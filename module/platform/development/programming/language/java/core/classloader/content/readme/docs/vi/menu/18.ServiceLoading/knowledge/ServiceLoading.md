<a id="back-to-top"></a>

# Nạp dịch vụ

## Menu
- [Service Provider Interface và vai trò của ServiceLoader](#service-loading-001)
- [Khai báo provider bằng META-INF/services trên classpath](#service-loading-002)
- [uses và provides trong module-info.java](#service-loading-003)
- [ServiceLoader.load với loader tường minh hoặc loader ngữ cảnh](#service-loading-004)
- [ServiceLoader.load với ModuleLayer](#service-loading-005)
- [Khám phá provider và khởi tạo lười](#service-loading-006)
- [Iterator, stream và ServiceLoader.Provider](#service-loading-007)
- [Cache provider, reload và giới hạn dùng đồng thời](#service-loading-008)
- [ServiceConfigurationError và provider không tương thích kiểu dịch vụ](#service-loading-009)
- [Provider constructor, provider method và khác biệt theo cách triển khai](#service-loading-010)

## <a id="service-loading-001">Service Provider Interface và vai trò của ServiceLoader</a>

<details>
<summary>Xem chi tiết</summary>

# Service Provider Interface (SPI) và vai trò của ServiceLoader trong Java

Khi xây ứng dụng có thể mở rộng, **host không nên phải sửa mã nguồn mỗi khi xuất hiện một implementation mới**. Java hỗ trợ mô hình **Service Provider Interface (SPI)**: một interface hoặc abstract class định nghĩa hợp đồng; nhiều **service providers** triển khai hợp đồng; code sử dụng dịch vụ (consumer) khám phá những provider ấy lúc runtime.

`java.util.ServiceLoader<S>` là API chuẩn để **tìm và tạo providers** mà không cần host hard-code tên class implementation. Cơ chế này liên quan trực tiếp tới **ClassLoader**, vì việc tìm provider và nạp bytecode còn phụ thuộc nơi đặt JAR, thread context class loader (TCCL), classpath hoặc JPMS module layers.

Ba ý cốt lõi:

1. **SPI là hợp đồng**, ví dụ `GreetingService`; provider là implementation, ví dụ `FriendlyGreeting`. `ServiceLoader` là công cụ khám phá/khởi tạo, **không phải chính SPI**.
2. **ServiceLoader không quét ngẫu nhiên mọi class trên classpath**. Trong môi trường classpath, nó đọc descriptor ở `META-INF/services/<tên nhị phân SPI>`; trong **named modules**, nó dùng `uses`/`provides` của JPMS.
3. **Có class trên đĩa chưa đủ**: provider và SPI phải có runtime type identity tương thích; loader chọn để khám phá phải nhìn thấy descriptor và provider. Trong chế độ mặc định, `ServiceLoader.load(SPI.class)` dùng **TCCL của thread hiện tại**.

Bài này trình bày mô hình, nguyên lý hoạt động, classloader boundary, lifecycle, lỗi thường gặp và ví dụ Java 17+ chạy được cả khi provider **không nằm trong application classpath của host**.

## 1. Mô hình SPI: consumer, service contract và provider

~~~text
                 Host / Consumer
                       |
                 sử dụng API SPI
                       |
                       v
                 GreetingService
                    /       \
                   /         \
       FriendlyGreeting    FormalGreeting
       (provider A)        (provider B)
                   \         /
                    \       /
                     ServiceLoader
                     discovery
~~~

Trong Java, một service thường được biểu diễn bởi `interface` hoặc `abstract class`. Provider là một kiểu cung cấp implementation cho service; việc provider được tạo như thế nào phụ thuộc cơ chế classpath hay named module.

~~~java
package spi;

public interface GreetingService {
    String greet(String name);
}
~~~

Đây là **hợp đồng SPI**. Consumer có thể gọi `greet` mà không cần biết service provider đến từ plugin JAR nào.

Provider ví dụ:

~~~java
package ext;

import spi.GreetingService;

public final class FriendlyGreeting implements GreetingService {
    public FriendlyGreeting() { }

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
~~~

Để provider được `ServiceLoader` khám phá, **không đủ chỉ `implements GreetingService`**: phải có cấu hình đăng ký provider hợp lệ.

### So sánh với Dependency Injection (DI)

| Tiêu chí | ServiceLoader/SPI | DI container/framework |
|---|---|---|
| Cách lựa chọn implementation | Dựa trên provider registration của Java | Theo component/configuration/annotations/container rules |
| Tìm provider ở runtime | Có, theo class loader/module metadata | Tùy framework |
| Constructor injection và scopes | Không phải nhiệm vụ chính | Thường hỗ trợ mạnh |
| API nằm trong JDK | `java.util.ServiceLoader` | Không bắt buộc |
| Độ phức tạp | Nhỏ, thuận tiện cho extension points | Có thể nhiều tính năng nhưng nhiều lifecycle hơn |

Không coi `ServiceLoader` là một DI container đầy đủ: nó **không tự tiêm mọi dependency**, cấu hình transaction, vòng đời HTTP request hoặc proxy cho provider. Nếu constructor của provider cần tham số cấu hình, cân nhắc dùng một SPI **factory** có constructor không tham số, rồi cho factory tạo object theo cấu hình.

## 2. ServiceLoader khám phá provider ở classpath thế nào?

Với các provider thuộc **unnamed module/classpath**, mỗi JAR hoặc thư mục chứa provider configuration file:

~~~text
META-INF/services/spi.GreetingService
~~~

**Tên file** sau `META-INF/services/` phải là **tên nhị phân đầy đủ của service**, ở đây `spi.GreetingService`. Bên trong là **tên nhị phân đầy đủ của từng provider**, một tên trên mỗi dòng:

~~~text
ext.FriendlyGreeting
~~~

Nếu có nhiều providers:

~~~text
ext.FriendlyGreeting
ext.FormalGreeting
~~~

Quy tắc:

- File provider configuration mã hóa **UTF-8**.
- Dùng **dấu chấm trong tên class** (`ext.FriendlyGreeting`), không dùng `ext/FriendlyGreeting.class`.
- Dòng trống và khoảng trắng/tab quanh tên được bỏ qua.
- Ký tự `#` bắt đầu comment đến hết dòng.
- Nếu cùng provider name xuất hiện nhiều lần trong các tệp cấu hình, các bản khai báo trùng được bỏ qua theo quy tắc ServiceLoader.
- Provider phải có thể nạp từ class loader được dùng để khám phá, đồng thời **assignable về đúng runtime service type**.
- Với provider trong unnamed module/classpath, provider class phải `public`, có **public constructor không tham số** phù hợp; provider phải là subtype của service.

`ServiceLoader` dùng `ClassLoader.getResources("META-INF/services/spi.GreetingService")` (và cơ chế liên quan) để tìm provider-configuration files trong vùng nhìn thấy của loader; nó không tự duyệt tất cả file `.class` hay lấy tất cả subclasses bằng reflection.

### Cấu trúc JAR phù hợp

~~~text
greeting-plugin.jar
  META-INF/
    MANIFEST.MF
    services/
      spi.GreetingService
  ext/
    FriendlyGreeting.class
~~~

Tệp `META-INF/services/spi.GreetingService` phải có mặt **trong artifact được chạy**, không chỉ ở thư mục `src/resources` của project. `javac` không tự copy resource config file ấy khi biên dịch nguồn Java.

Không cần `Main-Class`: plugin được `ServiceLoader` tìm, không cần chạy `java -jar greeting-plugin.jar`.

## 3. Các API `ServiceLoader` quan trọng

~~~java
import java.util.ServiceLoader;

// Lấy loader dựa trên TCCL của thread hiện tại.
ServiceLoader<GreetingService> a =
        ServiceLoader.load(GreetingService.class);

// Chỉ định loader tường minh để tìm provider.
ServiceLoader<GreetingService> b =
        ServiceLoader.load(GreetingService.class, pluginLoader);
~~~

Đây là **mảnh API minh họa**: cần `GreetingService`, `pluginLoader` tồn tại trong scope và import tương ứng.

| API | Vai trò |
|---|---|
| `ServiceLoader.load(Service.class)` | Tạo service loader, sử dụng **thread context class loader** |
| `ServiceLoader.load(Service.class, loader)` | Tạo service loader dùng class loader được chỉ định |
| `ServiceLoader.load(layer, Service.class)` | Tìm providers trong module layer và các parent layers phù hợp |
| `iterator()` / vòng `for-each` | Khám phá và instantiate providers một cách **lazy** |
| `stream()` | Truy cập `Provider<S>`, có thể xem `type()` trước khi yêu cầu `get()` |
| `findFirst()` | Tìm và lấy provider đầu tiên dưới dạng `Optional<S>` |
| `reload()` | Xóa cached providers để tìm lại vào lần iterate tiếp theo |

**Không nhầm:** `ServiceLoader.load(...)` tạo **đối tượng service loader**, không bảo đảm tất cả providers được tìm/tạo ngay tại lời gọi đó. Trong nhiều trường hợp, lỗi cấu hình chỉ xuất hiện lúc duyệt iterator/stream.

### Lazy discovery và cache

~~~text
ServiceLoader.load(GreetingService.class)
                |
                v
       ServiceLoader<GreetingService>
                |
             iterator()
                |
        +-------+---------+
        |                 |
   providers đã cache    Tìm provider kế tiếp
        |                 |
        v                 v
    trả instance       load + instantiate
                          |
                          v
                  thêm vào cache của
                  ServiceLoader instance
~~~

- Các provider đã được nạp/khởi tạo trong **ServiceLoader instance** được cache để vòng iterate kế tiếp có thể trả lại.
- `reload()` xóa cache provider đã khám phá/khởi tạo. Nó **không làm ClassLoader unload hoặc redefine class**.
- Sau `reload()` nên tạo iterator/stream mới; iterator đang sử dụng có thể không còn hợp lệ.
- ServiceLoader **không thread-safe**. Không sử dụng chung một instance và vừa `reload`, vừa iterate từ nhiều threads mà không có synchronization phù hợp.
- Đừng lưu một ServiceLoader instance **toàn JVM** cho tất cả ứng dụng/plugins; TCCL và provider visibility có thể khác nhau, đồng thời cached providers có thể giữ loader cũ sống lâu.

### `stream()` và Provider metadata

~~~java
ServiceLoader<GreetingService> services =
        ServiceLoader.load(GreetingService.class);

services.stream().forEach(provider -> {
    Class<? extends GreetingService> type = provider.type();
    System.out.println(type.getName());
    GreetingService service = provider.get();
    System.out.println(service.greet("Java"));
});
~~~

Đây là **mảnh minh họa**; package SPI và provider phải được cấu hình. `Provider.type()` cho kiểu mà `ServiceLoader` biết là provider; `get()` yêu cầu instance. Không tự giả định chỉ gọi `stream()` sẽ tạo mọi service object.

Với **JPMS provider factory method** (phần sau), provider class chứa factory method có thể khác kiểu service do `Provider.type()` đại diện; vì vậy đừng dựa vào metadata này để định danh class triển khai trong mọi mode mà không đọc hợp đồng JDK.

## 4. Vì sao TCCL quan trọng trong kiến trúc plugin?

Thread có một **context class loader**:

~~~java
ClassLoader tccl =
        Thread.currentThread().getContextClassLoader();
~~~

`ServiceLoader.load(GreetingService.class)` tương đương về việc chọn class loader với:

~~~java
ServiceLoader.load(
    GreetingService.class,
    Thread.currentThread().getContextClassLoader()
);
~~~

TCCL giải quyết trường hợp **thư viện API/host được nạp bởi parent** nhưng muốn khám phá provider do một child plugin loader thấy được. Nếu code API chỉ dùng defining loader của chính nó, nó có thể không nhìn thấy plugin bytecode/config ở child.

~~~text
                  Application class loader
                        |
                  spi.GreetingService
                        |
                  Host application
                        |
               tạo URLClassLoader plugin
                        |
                        +--> ext.FriendlyGreeting
                        +--> META-INF/services/spi.GreetingService

Thread Context ClassLoader = plugin URLClassLoader
                        |
                        v
     ServiceLoader.load(GreetingService.class)
                        |
                 tìm provider của plugin
~~~

Một ứng dụng có thể đặt TCCL tạm thời:

~~~java
Thread current = Thread.currentThread();
ClassLoader previous = current.getContextClassLoader();

try {
    current.setContextClassLoader(pluginLoader);
    for (GreetingService provider
            : ServiceLoader.load(GreetingService.class)) {
        // Gọi provider.
    }
} finally {
    current.setContextClassLoader(previous);
}
~~~

Đây là mảnh minh họa, `pluginLoader` phải có vòng đời phù hợp. **Luôn restore TCCL trong `finally`**: thread pools tái sử dụng thread, và quên restore có thể khiến request khác dùng sai plugin loader hoặc giữ loader đã dừng sống lâu.

Nếu đã có `pluginLoader` và không cần thay TCCL của framework khác, overload `ServiceLoader.load(SPI.class, pluginLoader)` thường rõ ràng và giới hạn tác động hơn.

## 5. Ví dụ Java 17+ hoàn chỉnh: SPI nằm ở host, provider nằm ở plugin directory

Thí nghiệm tạo một API contract, một provider class, một provider-configuration file và một host consumer. Điểm quan trọng là **classpath của lệnh chạy chỉ chứa host và SPI**, còn provider được đọc qua một `URLClassLoader` mới.

### 5.1. Cấu trúc project

~~~text
project/
  src/
    api/
      spi/
        GreetingService.java
    plugin/
      ext/
        FriendlyGreeting.java
    host/
      ServiceLoaderDemo.java
  resources/
    META-INF/
      services/
        spi.GreetingService
  out/
    api/
    plugin/
    host/
~~~

Output directories được tạo khi build. Tệp service configuration trong `resources` cần được **copy** vào `out/plugin/META-INF/services/spi.GreetingService`.

### 5.2. `src/api/spi/GreetingService.java`

~~~java
package spi;

public interface GreetingService {
    String greet(String name);
}
~~~

### 5.3. `src/plugin/ext/FriendlyGreeting.java`

~~~java
package ext;

import spi.GreetingService;

public final class FriendlyGreeting implements GreetingService {
    public FriendlyGreeting() {
    }

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
~~~

Constructor public không tham số phục vụ `ServiceLoader` theo cơ chế classpath. Provider class cần load/instantiate thành công và phải implement **đúng runtime interface `spi.GreetingService`**.

### 5.4. `resources/META-INF/services/spi.GreetingService`

~~~text
ext.FriendlyGreeting
~~~

Đây là **file văn bản**, không phải một Java class. File name **chính xác** là `spi.GreetingService`; nội dung **chính xác** là binary class name `ext.FriendlyGreeting`.

### 5.5. `src/host/ServiceLoaderDemo.java`

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ServiceLoader;
import spi.GreetingService;

public final class ServiceLoaderDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ServiceLoaderDemo <plugin-directory>");
        }

        ClassLoader parent = GreetingService.class.getClassLoader();
        URL source = Path.of(args[0]).toAbsolutePath()
                .toUri().toURL();

        // Parent không thấy config/provider của plugin directory.
        boolean viaParent = ServiceLoader
                .load(GreetingService.class, parent)
                .iterator().hasNext();
        System.out.println("provider through parent: " + viaParent);

        try (URLClassLoader pluginLoader =
                new URLClassLoader("greeting-plugin",
                        new URL[] {source}, parent)) {

            Thread current = Thread.currentThread();
            ClassLoader previous = current.getContextClassLoader();

            try {
                current.setContextClassLoader(pluginLoader);

                // Convenience overload lấy TCCL vừa được thiết lập.
                ServiceLoader<GreetingService> services =
                        ServiceLoader.load(GreetingService.class);

                int count = 0;
                for (GreetingService service : services) {
                    System.out.println("provider says: "
                            + service.greet("Java"));
                    System.out.println("same service API: "
                            + (service instanceof GreetingService));
                    System.out.println("provider defined by plugin: "
                            + (service.getClass().getClassLoader()
                               == pluginLoader));
                    count++;
                }

                System.out.println("providers: " + count);
            } finally {
                current.setContextClassLoader(previous);
            }
        }
    }
}
~~~

**Vì sao ví dụ dùng parent là loader của `GreetingService`?** Vì host và provider phải tham chiếu đến **cùng một interface runtime**, tức `(spi.GreetingService, parent)`. Plugin loader tự định nghĩa `ext.FriendlyGreeting`, nhưng delegate việc nạp `spi.GreetingService` về parent.

**Vì sao phải đặt TCCL?** Host program được nạp qua app loader không có plugin directory. Khi tạm đặt TCCL bằng URLClassLoader, `ServiceLoader.load(GreetingService.class)` tìm provider-configuration file và class implementation do plugin loader nhìn thấy.

### 5.6. Biên dịch và chạy (Bash / Linux / macOS)

Chạy từ thư mục `project`:

~~~bash
mkdir -p out/api out/plugin/META-INF/services out/host

javac -d out/api src/api/spi/GreetingService.java

javac -cp out/api -d out/plugin src/plugin/ext/FriendlyGreeting.java

cp resources/META-INF/services/spi.GreetingService \
   out/plugin/META-INF/services/spi.GreetingService

javac -cp out/api -d out/host src/host/ServiceLoaderDemo.java

java -cp "out/api:out/host" ServiceLoaderDemo out/plugin
~~~

Trên Windows, có thể tạo thư mục bằng `New-Item -ItemType Directory -Force`, copy file bằng `Copy-Item` và dùng dấu `;` để phân cách classpath:

~~~powershell
java -cp "out/api;out/host" ServiceLoaderDemo out/plugin
~~~

Chú ý **không đưa `out/plugin` vào `-cp` của host**. Nó chỉ là nguồn URL của custom loader. Nếu thêm `out/plugin` vào application classpath, lời gọi `ServiceLoader.load(SPI.class, parent)` có thể tìm provider trước khi đổi TCCL, khiến phần so sánh không còn đúng.

**Stdout xác định trong cấu hình được mô tả:**

~~~text
provider through parent: false
provider says: Hello, Java
same service API: true
provider defined by plugin: true
providers: 1
~~~

### 5.7. Giải thích output

- `provider through parent: false`: parent chỉ thấy host + API, không nhìn thấy descriptor/config `META-INF/services` trong `out/plugin`.
- `provider says: Hello, Java`: ServiceLoader đã đọc descriptor, load `ext.FriendlyGreeting` và instantiate provider.
- `same service API: true`: provider implement **đúng** runtime `spi.GreetingService` mà host sử dụng.
- `provider defined by plugin: true`: class `ext.FriendlyGreeting` do URLClassLoader tự tạo định nghĩa, không bị parent tìm thấy trước.
- `providers: 1`: chỉ một provider được đăng ký và tìm thấy trong cấu hình thí nghiệm.

### 5.8. Đóng plugin thành JAR

Từ output ở trên, đóng plugin class cùng descriptor:

~~~bash
mkdir -p dist

jar --create --file dist/greeting-plugin.jar -C out/plugin .

jar --list --file dist/greeting-plugin.jar
~~~

Các entry chính phải có:

~~~text
ext/FriendlyGreeting.class
META-INF/services/spi.GreetingService
~~~

Chạy lại bằng đường dẫn JAR thay vì thư mục:

~~~bash
java -cp "out/api:out/host" ServiceLoaderDemo dist/greeting-plugin.jar
~~~

Vì code chuyển đường dẫn sang `URL` bằng `Path.toUri().toURL()`, `URLClassLoader` hỗ trợ cả URL thư mục và URL JAR. Khi có đủ hai entries, **stdout giống hệt phần 5.6**.

Lưu ý: plugin JAR không cần `Main-Class`. ServiceLoader dùng descriptor `META-INF/services` và binary names.

## 6. ServiceLoader không được phép làm mờ ranh giới type identity

Một lỗi plugin phổ biến: plugin JAR đóng gói **cả một bản sao SPI** `spi.GreetingService` rồi được nạp child-first cho cả `spi.*`. Dù provider có khai báo `implements spi.GreetingService`, JVM có thể coi nó là **interface khác** với SPI của host:

~~~text
Host:    (spi.GreetingService, AppLoader)
Plugin:  (spi.GreetingService, PluginLoader)

Cùng binary name nhưng khác defining loader
=> hai runtime interfaces khác nhau
~~~

Khi ServiceLoader thử kiểm tra provider có assignable về service type hay không, provider sai ranh giới có thể gây `ServiceConfigurationError`; một thao tác cast thủ công có thể gặp `ClassCastException`.

**Đúng cách:** cho SPI, các public callback/DTO/shared exceptions sống ở loader cha chung. Plugin loader dùng parent delegation đối với API ấy, chỉ tự định nghĩa implementation/dependency riêng.

Không đủ để hai nơi có `GreetingService.class` cùng bytecode; JVM phân biệt chúng theo **defining loader object**.

### Lỗi do TCCL sai

Một framework chạy trong thread pool có thể dùng TCCL của thread được tạo trước khi plugin loader xuất hiện. Khi đó:

~~~java
ServiceLoader.load(GreetingService.class)
~~~

có thể trả kết quả trống dù `pluginLoader.loadClass("ext.FriendlyGreeting")` thành công. Đây không phải mâu thuẫn: ServiceLoader có thể **không tìm thấy provider configuration file** từ TCCL của thread hiện tại.

Chẩn đoán:

~~~java
System.out.println(Thread.currentThread()
        .getContextClassLoader());

System.out.println(GreetingService.class.getClassLoader());
~~~

Hai giá trị này không bắt buộc bằng nhau. Thử `ServiceLoader.load(GreetingService.class, pluginLoader)` để yêu cầu loader đích tường minh và so sánh.

## 7. Cơ chế SPI trong JPMS: `uses` và `provides`

Từ Java 9, **named modules** sử dụng module descriptor thay vì phải dựa hoàn toàn vào `META-INF/services`.

Ví dụ một module định nghĩa SPI:

**`greeting.api/module-info.java`**

~~~java
module greeting.api {
    exports spi;
}
~~~

Nội dung interface `spi.GreetingService` được biên dịch và đóng gói trong module `greeting.api`.

Module consumer:

**`greeting.consumer/module-info.java`**

~~~java
module greeting.consumer {
    requires greeting.api;
    uses spi.GreetingService;
}
~~~

Code trong module `greeting.consumer` có thể gọi `ServiceLoader.load(GreetingService.class)`. Khai báo **`uses`** cho service type là cần thiết đối với named module dùng ServiceLoader; `requires` cung cấp khả năng truy cập module export SPI.

Provider module:

**`greeting.provider/module-info.java`**

~~~java
module greeting.provider {
    requires greeting.api;
    provides spi.GreetingService
        with ext.FriendlyGreeting;
}
~~~

Khi provider module được **resolved và nằm trong module layer được tìm kiếm**, ServiceLoader có thể tìm provider qua `provides`. Cần cấu hình module path/resolution phù hợp, không phải chỉ copy `module-info.class` vào một JAR rồi mặc định provider sẽ hiện diện.

Một consumer class minh họa ở `greeting.consumer`:

~~~java
package app;

import java.util.ServiceLoader;
import spi.GreetingService;

public final class Main {
    public static void main(String[] args) {
        ServiceLoader.load(GreetingService.class)
                .forEach(service ->
                        System.out.println(service.greet("Java")));
    }
}
~~~

Đây là **các mảnh tệp minh họa riêng cho JPMS**, không chép cùng chỗ vào classpath demo mà không sắp xếp cấu trúc modular. Phải tạo module source tree, compile/resolve các module và bảo đảm provider module có trong module graph; số lượng providers còn phụ thuộc môi trường.

### Vì sao `uses` không tương đương `requires provider.module`?

- `requires greeting.api` cho phép consumer tham chiếu service interface exported bởi `greeting.api`.
- `uses spi.GreetingService` khai báo **mục đích tiêu thụ service** để JPMS/ServiceLoader thực hiện discovery theo module model.
- `provides spi.GreetingService with ext.FriendlyGreeting` ở provider module đăng ký implementation.
- Consumer **không cần** phụ thuộc trực tiếp vào implementation module để gọi service. Đó là mục tiêu loose coupling của SPI.

Tránh ép consumer viết `requires greeting.provider` chỉ để có tên provider class trong compile classpath; làm vậy phá ý tưởng thay đổi implementation độc lập.

### Provider constructor và provider factory method

Theo JDK API, provider trong **classpath/unnamed module** được instantiate qua **public constructor không tham số** phù hợp.

Trong **explicit named module**, provider có thể được tạo qua:

1. **Provider constructor** không tham số phù hợp; hoặc
2. **Public static no-arg factory method có tên `provider`**, trả một instance kiểu assignable về service.

Ví dụ provider factory method (minh họa):

~~~java
package ext;

import spi.GreetingService;

public final class GreetingFactories {
    private GreetingFactories() { }

    public static GreetingService provider() {
        return name -> "Hello from factory, " + name;
    }
}
~~~

Với explicit module có khai báo `provides spi.GreetingService with ext.GreetingFactories;`, API có thể sử dụng provider method để tạo service. **Không áp dụng cơ chế provider method này cho provider-configuration file trong unnamed module**: ở classpath, class được liệt kê phải trực tiếp là subtype của service và có constructor phù hợp.

Lợi ích của provider factory method: có thể che giấu implementation thật hoặc trả factory/service đã được cấu hình, thay vì buộc service object đồng nhất với provider class được khai báo trong module descriptor.

### Provider ở module layers

`ServiceLoader.load(ModuleLayer layer, SPI.class)` tìm providers từ layer đã chỉ định và các ancestor layers thích hợp. Cách này phù hợp plugin modules được tạo qua `ModuleLayer` riêng.

Cần lưu ý:

- Khám phá module providers liên quan **module graph/layer**, không chỉ nhìn một URL classpath.
- Một layer có thể dùng nhiều classloaders; không suy ra loader được truyền cho một overload nhất định chỉ nhìn thread context.
- Module readability/exports/opens và provider resolution phải được thiết kế đúng.
- `ServiceLoader.load(layer, service)` **không tìm provider của unnamed modules** theo hợp đồng API.

## 8. Đa provider: lựa chọn, thứ tự và xung đột

Khi có nhiều providers cùng SPI:

~~~java
ServiceLoader<GreetingService> all =
        ServiceLoader.load(GreetingService.class);

for (GreetingService g : all) {
    System.out.println(g.greet("Java"));
}
~~~

Đây là mảnh minh họa. **Thứ tự providers không nên được coi là nghiệp vụ ổn định xuyên mọi môi trường**. Với classpath descriptors, thứ tự phụ thuộc thứ tự tìm resource files của classloader và dòng cấu hình. Với named modules, có những quy tắc riêng và một số thứ tự không được bảo đảm.

Không nên dựa vào “provider xuất hiện đầu luôn là provider tốt nhất” khi có nhiều JAR. Tốt hơn:

- Thiết kế interface với metadata như `id()`, `priority()`, `supports(...)`.
- Thu thập tất cả providers rồi **chọn theo policy tường minh**.
- Xử lý provider lỗi theo hợp đồng sản phẩm; không âm thầm bỏ qua lỗi cấu hình nghiêm trọng.
- Đối với extension được deploy/redeploy, gắn provider list với plugin generation/lifecycle.

### Sự khác nhau giữa `findFirst()` và `stream().findFirst()`

`findFirst()` trả `Optional<S>` và có thể instantiate provider đầu tiên để nhận service instance.

`stream()` trả stream các `ServiceLoader.Provider<S>`, cho phép kiểm tra `type()` trước khi gọi `get()`. Đây là một khác biệt hữu ích nếu muốn chọn provider dựa trên metadata mà không phải tạo hết instances.

Tuy nhiên, `stream()` vẫn thực hiện khám phá lazy và có thể ném `ServiceConfigurationError` khi provider config/class không hợp lệ. Không xem nó như phép kiểm tra tĩnh luôn thành công.

## 9. Lỗi `ServiceConfigurationError` và cách chẩn đoán

`ServiceLoader` báo lỗi qua `java.util.ServiceConfigurationError` (một subtype của `Error`). Nó có thể xuất hiện trong `hasNext()`, `next()`, stream operations hoặc `Provider.get()` khi cần nạp/instantiate provider.

Các nguyên nhân thường gặp:

| Lỗi | Ví dụ | Hướng xử lý |
|---|---|---|
| Sai tên descriptor | `META-INF/services/GreetingService` thay vì `.../spi.GreetingService` | Dùng **binary name đầy đủ của SPI** |
| Sai tên provider | `ext/FriendlyGreeting.class` | Một dòng `ext.FriendlyGreeting` |
| Provider config không ở artifact runtime | `resources/` chưa được copy | Kiểm tra bằng `jar --list` |
| Provider class không thấy được | Plugin URL/TCCL sai hoặc dependency thiếu | Kiểm tra loader được chọn, classpath, parent chain |
| Không subtype cùng runtime SPI | Plugin định nghĩa lại `spi.GreetingService` | Chia sẻ SPI từ defining loader cha |
| Constructor không phù hợp ở classpath | Không có `public` no-arg constructor | Thêm constructor hợp lệ hoặc chuyển sang module provider method khi thiết kế phù hợp |
| Class abstract/không instantiate được | Provider không có implementation cụ thể | Sửa provider implementation |
| Constructor/provider method thất bại | Runtime exception khi instantiate | Kiểm tra stack trace/cause |
| Version/linkage không tương thích | `NoClassDefFoundError`, `NoSuchMethodError` | Kiểm tra JAR thực tế, defining loader, binary compatibility |
| File cấu hình UTF-8/syntax sai | Dòng tên class không hợp lệ | Sửa cấu trúc descriptor, encoding/comment |
| Module thiếu `uses` | Consumer ở explicit named module chưa khai báo service use | Thêm `uses SPI;` |
| Module provider chưa resolved | Chỉ có JAR trên module path nhưng không thuộc graph thực thi | Điều chỉnh module resolution/module layer |

Vì `ServiceConfigurationError` là `Error`, không nên quen tay bắt rồi lờ đi như thể không có provider. Nếu hệ thống thực sự cho phép plugin tùy chọn lỗi, cần policy cách ly, log nguyên nhân và đảm bảo không che lỗi của cả ứng dụng.

### Checklist chẩn đoán theo thứ tự

**Bước 1 — Xác nhận service type:**

~~~java
System.out.println(GreetingService.class.getName());
System.out.println(GreetingService.class.getClassLoader());
~~~

**Bước 2 — Xác nhận TCCL hoặc explicit loader:**

~~~java
ClassLoader loader =
        Thread.currentThread().getContextClassLoader();
System.out.println(loader);
~~~

**Bước 3 — Xác nhận resource provider descriptor:**

~~~java
var configs = loader.getResources(
        "META-INF/services/spi.GreetingService");

while (configs.hasMoreElements()) {
    System.out.println(configs.nextElement());
}
~~~

Các đoạn này là **mảnh minh họa**, cần exception handling `IOException` và biến loader khác `null`. Nhìn được descriptor không tự bảo đảm class/provider nạp hợp lệ nhưng giúp phân biệt lỗi discovery và lỗi instantiation.

**Bước 4 — Xác nhận class provider có thể load:**

~~~java
Class<?> impl = loader.loadClass("ext.FriendlyGreeting");
System.out.println(impl.getClassLoader());
System.out.println(GreetingService.class.isAssignableFrom(impl));
~~~

Nếu `isAssignableFrom` là `false`, hãy kiểm tra **hai bản SPI khác loader**, package/module access và binary incompatibility.

**Bước 5 — Kiểm tra artifact:**

~~~bash
jar --list --file dist/greeting-plugin.jar
~~~

Tìm chính xác `META-INF/services/spi.GreetingService` và `ext/FriendlyGreeting.class`, không chỉ kiểm tra Maven dependency declaration.

**Bước 6 — Đọc cause của `ServiceConfigurationError`:** khi lỗi do provider bytecode/constructor, nguyên nhân gốc có thể nằm bên trong `getCause()` hoặc chuỗi exception khác. Đừng khắc phục bằng việc thêm mọi JAR vào global classpath nếu mục tiêu là isolation.

## 10. ServiceLoader, ClassLoader và đời sống plugin

### 10.1. ServiceLoader giữ cache trong phạm vi instance

Sau khi một provider đã được khám phá/khởi tạo, ServiceLoader có thể giữ tham chiếu đến instance và class metadata. Nếu host giữ một `ServiceLoader` lâu hơn vòng đời plugin, plugin loader cũ có thể **không được thu gom/unload**.

Ví dụ khi redeploy:

~~~text
Generation A: pluginLoader-A -> providers-A
        |
        +-- Host còn giữ ServiceLoader<A> / provider instance
        |
        +-- Loader A chưa có cơ hội unload

Generation B: pluginLoader-B -> providers-B
        |
        +-- ServiceLoader instance mới theo B
~~~

Khi dừng plugin, nên:

1. Dừng các tasks/threads của plugin, hủy callback, đóng resources.
2. Dừng sử dụng provider objects cũ.
3. Xóa references tới ServiceLoader/provider instances và TCCL trỏ tới loader cũ.
4. Đóng URLClassLoader hoặc nguồn bytecode/JAR theo lifecycle.
5. Cho JVM/GC quyết định thời điểm class unloading thực tế.

`ServiceLoader.reload()` **không thay thế** toàn bộ các bước trên; nó chỉ xóa provider cache của instance loader, không tự tải lại class definitions.

### 10.2. Thread safety

ServiceLoader không phải một container concurrent-ready mặc định. Một instance bị iterate/reload đồng thời không có bảo đảm thread-safety. Khi cần đa luồng:

- Khám phá providers trong một phase có kiểm soát.
- Tạo snapshot danh sách service instances hoặc factories theo hợp đồng thread-safety của từng provider.
- Công bố snapshot một cách an toàn (ví dụ immutable list + volatile/atomic reference).
- Chỉ chia sẻ provider instance giữa threads nếu **implementation của provider** thread-safe.
- Với plugin reload, chuyển generation snapshot atomically thay vì sửa cùng list dùng chung giữa các threads.

ServiceLoader không tự bảo đảm rằng tất cả providers có trạng thái immutable hay reentrant.

### 10.3. Trust boundary

Đọc descriptor rồi instantiate provider có nghĩa ứng dụng thực thi code do nhà cung cấp plugin phát hành. `ServiceLoader` và `ClassLoader` **không tạo sandbox bảo mật**. Nếu plugin không tin cậy, cần quy trình xác minh chữ ký/nguồn artifact, quyền tiến trình, sandbox/process isolation thích hợp và chính sách update/deployment.

Không in file cấu hình chứa bí mật chỉ để debug service discovery; log tên provider, loader, version/hash và vị trí artifact khi cần.

## 11. Những hiểu lầm phổ biến

1. **“SPI và ServiceLoader là một.”** Sai: SPI là hợp đồng; ServiceLoader là cơ chế khám phá/khởi tạo provider.
2. **“Chỉ cần implement interface thì ServiceLoader tự tìm được.”** Sai: classpath cần `META-INF/services`; named module cần `provides`.
3. **“ServiceLoader tự quét tất cả `.class`.”** Sai: nó dựa trên cấu hình provider và module metadata.
4. **“`load(SPI.class)` luôn dùng system class loader.”** Sai: nó dùng **TCCL hiện tại**.
5. **“Tạo một URLClassLoader là TCCL tự đổi.”** Sai: phải truyền explicit loader hoặc đặt/restoring TCCL có kiểm soát.
6. **“Provider và SPI cùng tên class ở hai loader sẽ cast được.”** Sai: runtime type identity có defining loader.
7. **“Provider file phải tên provider class.”** Sai: tên file là **service binary name**; nội dung file liệt kê provider binary names.
8. **“Provider file chỉ cần ở source resources folder.”** Sai: nó phải có trong artifact/runtime search path.
9. **“`ServiceLoader.load` luôn instantiate ngay mọi provider.”** Sai: lazy discovery/instantiation.
10. **“`reload()` sẽ unload class và hot-replace plugin.”** Sai: chỉ reset provider cache; classloader lifecycle là chuyện khác.
11. **“`ServiceLoader` thread-safe.”** Sai: cần tự kiểm soát khi chia sẻ instance giữa nhiều threads.
12. **“Classpath provider method `public static provider()` thay cho constructor được.”** Sai: provider factory method thuộc quy tắc explicit module; classpath provider cần constructor đúng.
13. **“Module consumer phải `requires` provider module.”** Sai: `uses`/`provides` cho phép loose coupling, chỉ cần SPI type accessible.
14. **“Provider JAR phải có `Main-Class`.”** Sai: đó là quy tắc `java -jar`, không phải service discovery.
15. **“Provider đầu tiên luôn là implementation ưu tiên.”** Sai: thứ tự không phải hợp đồng selection nghiệp vụ trong mọi môi trường.
16. **“Một ServiceLoader global dùng cho mọi thread/plugin đều an toàn.”** Sai: TCCL visibility khác nhau và cached providers có thể giữ loader cũ.
17. **“`ServiceLoader.load(layer, service)` tự tìm cả unnamed-module classpath providers.”** Sai: overload layer tìm trong named modules ở layer/ancestors theo hợp đồng.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Phân biệt SPI, provider và `ServiceLoader`?

**Đáp án:** SPI định nghĩa hợp đồng, provider triển khai dịch vụ, ServiceLoader khám phá/khởi tạo providers đã đăng ký mà không cần consumer biết trước implementation name.

**Câu 2.** Service là `com.demo.Codec`; provider là `com.demo.impl.FastCodec` chạy trên classpath. Descriptor phải tên gì và nội dung gì?

**Đáp án:** File `META-INF/services/com.demo.Codec`, chứa dòng `com.demo.impl.FastCodec`. Provider class phải load được và có constructor no-arg public phù hợp.

**Câu 3.** Vì sao ServiceLoader từ host không tìm provider nằm trong plugin URLClassLoader mặc dù `pluginLoader.loadClass` thành công?

**Đáp án:** `ServiceLoader.load(SPI.class)` dùng TCCL; TCCL có thể là host loader không thấy descriptor/provider. Hãy truyền explicit pluginLoader hoặc tạm đặt TCCL rồi restore.

**Câu 4.** Plugin tự định nghĩa một bản `spi.GreetingService` riêng; host có interface cùng tên. Hậu quả?

**Đáp án:** Hai runtime interfaces khác defining loader; provider có thể không assignable về SPI của host, dẫn tới `ServiceConfigurationError` hoặc lỗi cast.

**Câu 5.** `reload()` có nạp lại bytecode provider mới trong cùng ClassLoader không?

**Đáp án:** Không. Nó chỉ reset cache provider của ServiceLoader; class definitions đã được JVM nạp vẫn tồn tại theo lifecycle riêng.

**Câu 6.** Trong named module, khai báo gì để consumer tìm provider không cần import class triển khai?

**Đáp án:** Consumer khai báo `uses spi.GreetingService;`, provider module khai báo `provides spi.GreetingService with ext.FriendlyGreeting;`, đồng thời module graph/readability cho service type được cấu hình đúng.

**Câu 7.** Có nên chia sẻ cùng ServiceLoader và provider instances giữa nhiều threads không?

**Đáp án:** ServiceLoader bản thân không thread-safe; hãy snapshot providers hoặc bảo vệ truy cập. Provider instance chỉ được chia sẻ nếu provider implementation đáp ứng thread-safety cần thiết.

## 13. Quy trình áp dụng trong một hệ thống plugin

~~~text
Định nghĩa SPI chung (host/shared API)
                 |
                 v
Build provider implementation
                 |
     +-----------+------------+
     |                        |
Classpath/unnamed         Named modules
     |                        |
META-INF/services/...    provides SPI with Impl
     |                        |
     +-----------+------------+
                 |
                 v
Chọn classloader/TCCL/module layer
                 |
                 v
ServiceLoader.load(...)
                 |
                 v
Iterator/stream: lazy discovery
                 |
                 v
Kiểm tra provider contract + lifecycle
                 |
                 v
Chọn provider theo policy ứng dụng
                 |
                 v
Sử dụng -> shutdown/reload có kiểm soát
~~~

**Ghi nhớ:** `ServiceLoader` là **cầu nối giữa service contract và nhiều provider runtime**, không phải công cụ tự thay đổi classloader. Provider discovery phải phù hợp **cấu hình `META-INF/services` hoặc JPMS `uses`/`provides`**, **loader visibility/TCCL** và **cùng runtime SPI type**. Nếu ba điều đó đúng, có thể thêm implementation mà không sửa consumer; nếu sai, lỗi thường nằm ở descriptor, module graph hoặc ranh giới defining loader.

### Tài liệu tham khảo chính thức

- [Java SE 17 — ServiceLoader API: service providers, classpath/JPMS, lazy loading](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)
- [Java SE 17 — ClassLoader API: getResources, delegation, defining loader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — URLClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html)
- [JLS 17 §7.7 — Module declarations, uses and provides](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-loading-002">Khai báo provider bằng META-INF/services trên classpath</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo provider bằng META-INF/services trên classpath trong Java

Trong Java, một thư viện có thể công bố **hợp đồng dịch vụ** dưới dạng interface/abstract class, còn ứng dụng hoặc một JAR khác cung cấp các **provider implementations** mà thư viện chưa biết trước khi biên dịch. Cơ chế **Service Provider Interface (SPI)** kết hợp với `java.util.ServiceLoader` giải quyết bài toán này bằng cách đọc một **tệp cấu hình văn bản có tên chính xác**:

```text
META-INF/services/<fully.qualified.ServiceInterface>
```

Ví dụ service interface `api.Greeting` có thể được triển khai bởi `provider.VietnameseGreeting`. Tệp `META-INF/services/api.Greeting` nằm **từ root của classpath entry hoặc JAR**, chứa một dòng `provider.VietnameseGreeting`. Khi application code duyệt `ServiceLoader.load(Greeting.class)` với loader phù hợp, JVM/ServiceLoader khám phá provider, kiểm tra nó thực sự triển khai interface chung, rồi tạo instance khi cần.

Bài thuộc nhóm **Nạp dịch vụ** của Java core – classloader. Trọng tâm: cách khai báo SPI trên **classpath/unnamed module**, cách xác định đúng tên và vị trí `META-INF/services`, cơ chế `ServiceLoader` và class loader, chương trình Java 17+ chạy được từ JAR, lỗi metadata/constructor/namespace, đóng gói nhiều provider, khác biệt với `provides ... with ...` của JPMS, cùng quy trình chẩn đoán.

## 1. Service, provider và discovery là ba vai trò khác nhau

Ta tách hệ thống thành ba thành phần:

1. **Service contract:** interface/abstract class cung cấp API ổn định. Ví dụ `api.Greeting` khai báo `String message()`.
2. **Provider:** class cụ thể đáp ứng contract, ví dụ `provider.VietnameseGreeting`. Provider có thể nằm trong JAR được ứng dụng thêm lúc triển khai thay vì có sẵn trong JAR API.
3. **Discovery/consumer:** code gọi `ServiceLoader` để tìm những provider đã được đăng ký, không hard-code `new VietnameseGreeting()` trong thư viện gốc.

Sơ đồ:

```text
host-classes/                   plugins/greeting-provider.jar
  api/Greeting.class               provider/VietnameseGreeting.class
  app/Main.class                   META-INF/services/api.Greeting
           |                                     |
           | ServiceLoader.load(Greeting.class)  |
           +-------------------------------------+
                               |
                               v
                ServiceLoader đọc service descriptor
                               |
                               v
                     provider.VietnameseGreeting
                               |
                               v
                      implements api.Greeting
                               |
                               v
                       ứng dụng gọi message()
```

**Lợi ích kiến trúc:** thư viện/framework phụ thuộc **interface** thay vì phụ thuộc implementation cụ thể. Nhà cung cấp có thể thêm plugin bằng một JAR được đóng gói đúng, không bắt consumer sửa source để import từng class provider.

Cơ chế này xuất hiện trong nhiều nền tảng: JDBC driver discovery, provider cho parser/transformer, logging, serialization và plugin architectures — nhưng mỗi framework còn có chính sách riêng, không được giả định mọi framework đều dùng `ServiceLoader`.

## 2. Cấu trúc bắt buộc của tệp META-INF/services

Với service interface:

```java
package api;

public interface Greeting {
    String message();
}
```

tệp service configuration cần nằm tại đường dẫn **tính từ root của classpath/JAR**:

```text
META-INF/services/api.Greeting
```

Chú ý từng ký tự:

- Thư mục đúng chính tả **`META-INF`** (viết hoa), rồi **`services`** (chữ thường).
- Tên tệp là **binary name đầy đủ của service**: `api.Greeting`, **không** phải tên provider, không có phần mở rộng `.txt`/`.properties`.
- Nội dung là **binary name đầy đủ của từng provider**, mỗi provider trên một dòng.
- Tên class dùng dấu chấm `.` cho package, không viết path `provider/VietnameseGreeting.class`.
- Tệp phải được giải mã theo **UTF-8** theo hợp đồng `ServiceLoader`.
- Dòng trống và comment bắt đầu bằng `#` được phép; phần từ ký tự `#` đến hết dòng là comment; khoảng trắng bao quanh được bỏ.
- Tên class provider phải hợp lệ, không chứa khoảng trắng/tabs bên trong tên binary.
- Một dòng không có cú pháp `new`, dấu ngoặc, dấu bằng hay tên method; chỉ có **tên class provider**.

Tệp mẫu:

```text
# Providers for api.Greeting
provider.VietnameseGreeting
provider.EnglishGreeting # enabled by default
```

Đoạn trên giả định **cả hai class đã được compile, đóng gói và đáp ứng SPI contract**. Nếu chỉ có `VietnameseGreeting.class`, hãy khai báo **một dòng** `provider.VietnameseGreeting`. Đừng sao chép ví dụ hai dòng vào artifact chỉ có một implementation: `ServiceLoader` có thể báo `ServiceConfigurationError` khi duyệt tới provider bị thiếu.

### Tên service khác tên provider

Giả sử có:

- Service: `com.acme.spi.Codec`.
- Implementation: `com.vendor.FastCodec`.

Tên file phải là `META-INF/services/com.acme.spi.Codec` và nội dung phải có `com.vendor.FastCodec`. Việc thay hai tên cho nhau là lỗi phổ biến khiến ServiceLoader không tìm provider hoặc báo cấu hình sai.

### Nested service hoặc provider

Nếu service là nested/member interface, ví dụ `com.acme.Api$Extension`, tên binary trong descriptor dùng ký tự `$`:

```text
META-INF/services/com.acme.Api$Extension
```

Tương tự provider nested member type có thể cần binary name có `$`. Chỉ nên dùng provider class đáp ứng quy tắc khởi tạo/access; static member class public với public no-arg constructor có thể đáp ứng, còn inner class yêu cầu enclosing instance thường không phù hợp cho kiểu provider classpath truyền thống.

## 3. ServiceLoader tìm provider thế nào trên classpath?

Trong tình huống classpath/unnamed module thông dụng:

1. Consumer gọi `ServiceLoader.load(Greeting.class)` hoặc overload nhận `ClassLoader` tường minh.
2. `ServiceLoader` chọn **loader dùng để discovery** theo contract của overload.
3. Khi bắt đầu **duyệt** service loader, runtime tìm những resource có tên `META-INF/services/api.Greeting` mà loader được chọn có thể nhìn thấy.
4. Service configuration files được đọc, parse tên provider và xác định provider classes.
5. Các provider được kiểm tra, ví dụ **có phải subtype của `api.Greeting` mà consumer đang dùng không**, constructor có hợp lệ không.
6. `ServiceLoader` khởi tạo provider **khi iteration/Provider.get yêu cầu**, sau đó consumer gọi API của service.
7. Những provider đã được instantiate/khám phá có thể được cache trong **đối tượng `ServiceLoader` tương ứng**; `reload()` reset cache đó theo contract.

Mô hình:

```text
ServiceLoader.load(service)
          |
          | chỉ tạo service loader, chưa phải instantiate hết
          v
Consumer bắt đầu for (Service s : loader)
          |
          v
ClassLoader.getResources("META-INF/services/<service-binary-name>")
          |
          v
Đọc resource UTF-8, parse tên provider
          |
          v
Load provider class -> kiểm tra subtype/constructor
          |
          v
Instantiate provider khi iterator.next()/Provider.get()
          |
          v
consumer gọi method của interface chung
```

Sơ đồ là cách hiểu khái quát của đường classpath provider lookup, không mô tả chi tiết mọi tối ưu, module provider hoặc cách caching nội bộ của từng JDK. **Hợp đồng quan trọng:** discovery/instantiation chủ yếu **lazy**, nên lỗi cấu hình có thể chỉ xuất hiện **khi thực sự duyệt** thay vì ngay tại lệnh `ServiceLoader.load(...)`.

### Quy tắc provider class trên classpath

Với provider nằm trong **unnamed module/classpath**, cách chuẩn là:

- Provider là **class public**, không abstract và có thể instantiate.
- Provider **implement/extend** service type được yêu cầu, trực tiếp hoặc gián tiếp.
- Provider có **public no-argument constructor** dùng để tạo instance.
- Constructor có thể thực thi logic nghiệp vụ, do vậy cần cân nhắc side effects.
- Các phụ thuộc mà provider cần cũng phải hiện diện theo loader/classpath phù hợp.

`ServiceLoader` có một hình thức provider factory qua **public static `provider()` method** đối với một số **explicit named module providers**. **Không dùng nó làm thay thế mặc định cho public no-arg constructor của provider trong unnamed module trên classpath**.

## 4. Ví dụ Java 17+ hoàn chỉnh: Provider nằm trong JAR khác ứng dụng

Ta tạo một host chứa API/consumer, một JAR chứa provider và descriptor. Có thể chạy hoàn toàn bằng JDK 17+ và không cần framework.

### 4.1 Cấu trúc thư mục

```text
spi-demo/
├── src/
│   ├── api/
│   │   └── Greeting.java
│   └── app/
│       └── Main.java
├── provider-src/
│   └── provider/
│       └── VietnameseGreeting.java
├── host-classes/                         # class output của host
├── provider-classes/
│   ├── provider/
│   │   └── VietnameseGreeting.class       # sau biên dịch
│   └── META-INF/
│       └── services/
│           └── api.Greeting               # tệp văn bản, KHÔNG có .txt
└── plugins/
    └── greeting-provider.jar              # sau bước jar
```

Tạo các directories `host-classes`, `provider-classes/META-INF/services` và `plugins` trước khi chạy các lệnh biên dịch. Nếu dùng IDE/build tool, có thể để nó tạo các output directories tương ứng.

### 4.2 Service contract

File `src/api/Greeting.java`:

```java
package api;

public interface Greeting {
    String message();
}
```

Đây là interface chung. Consumer và provider **phải resolve đúng cùng runtime `api.Greeting` class identity**; trong ví dụ, cả hai dùng API do application/system loader định nghĩa.

### 4.3 Consumer trong host

File `src/app/Main.java`:

```java
package app;

import api.Greeting;
import java.util.ServiceLoader;

public class Main {
    public static void main(String[] args) {
        System.out.println("before load");

        ServiceLoader<Greeting> services =
                ServiceLoader.load(Greeting.class);

        System.out.println("after load");

        int count = 0;
        for (Greeting g : services) {
            System.out.println(g.getClass().getName()
                    + ": " + g.message());
            count++;
        }

        System.out.println("providers=" + count);
    }
}
```

Consumer **không import `provider.VietnameseGreeting`**. Nó chỉ biết interface và đọc provider qua ServiceLoader. Trong chương trình command-line thông thường, main thread có TCCL phù hợp với application/system loader, nên `ServiceLoader.load(Greeting.class)` thấy provider JAR trên classpath.

### 4.4 Provider implementation

File `provider-src/provider/VietnameseGreeting.java`:

```java
package provider;

import api.Greeting;

public class VietnameseGreeting implements Greeting {
    public VietnameseGreeting() {
        System.out.println("provider constructor");
    }

    @Override
    public String message() {
        return "Xin chao!";
    }
}
```

Provider class và constructor đều **public**. Dòng `provider constructor` giúp chứng minh rằng lời gọi `ServiceLoader.load` chưa instantiate provider ngay; constructor chỉ in khi consumer bắt đầu iteration.

### 4.5 Tệp khai báo provider

File `provider-classes/META-INF/services/api.Greeting` (UTF-8, **nội dung chỉ một dòng**):

```text
provider.VietnameseGreeting
```

Lưu ý: đây là file trong output **của provider**, không phải file Java; `javac` **không tự tạo descriptor** và cũng không tự copy resource. Nó phải được thêm bằng tay, bằng Gradle/Maven resources processing hoặc qua tool đóng gói JAR.

### 4.6 Biên dịch host, provider và đóng gói JAR

Chạy từ thư mục `spi-demo`:

```bash
javac --release 17 -d host-classes src/api/Greeting.java src/app/Main.java
javac --release 17 -cp host-classes -d provider-classes provider-src/provider/VietnameseGreeting.java
jar --create --file plugins/greeting-provider.jar -C provider-classes .
```

Giải thích:

1. `javac -d host-classes` tạo `api/Greeting.class` và `app/Main.class` dưới root host.
2. `javac -cp host-classes` cho provider compile được vì nó implement `api.Greeting` từ host.
3. `-d provider-classes` xuất `provider/VietnameseGreeting.class` vào output riêng.
4. `jar -C provider-classes .` thêm **tất cả nội dung từ root provider-classes** vào JAR, bao gồm `META-INF/services/api.Greeting`. Đây là điểm then chốt; không được đóng gói thành `provider-classes/META-INF/services/...` bên trong JAR.

Kiểm tra JAR:

```bash
jar --list --file plugins/greeting-provider.jar
```

Ngoài manifest/directories, phải có ít nhất:

```text
provider/VietnameseGreeting.class
META-INF/services/api.Greeting
```

### 4.7 Chạy với provider JAR

**Windows (PowerShell/CMD):**

```bash
java -cp "host-classes;plugins/greeting-provider.jar" app.Main
```

**Linux/macOS:**

```bash
java -cp "host-classes:plugins/greeting-provider.jar" app.Main
```

Stdout đã được kiểm tra khi biên dịch với `javac --release 17` và thực thi Java:

```text
before load
after load
provider constructor
provider.VietnameseGreeting: Xin chao!
providers=1
```

Tại sao thứ tự quan trọng?

- `before load` in **trước** khi tạo `ServiceLoader`.
- `after load` xuất hiện **trước constructor**; chứng minh `ServiceLoader.load` không tự instantiate provider ngay.
- `provider constructor` chỉ xuất hiện khi vòng `for` bắt đầu nhận provider.
- `provider.VietnameseGreeting: Xin chao!` cho biết discovery đúng class và gọi API interface thành công.
- `providers=1` cho thấy trong classpath của ví dụ chỉ có một provider hợp lệ.

### 4.8 Thử bỏ provider khỏi classpath

Không cần sửa hoặc xóa file. Chạy lại **chỉ với host**:

```bash
java -cp host-classes app.Main
```

Stdout đã được kiểm tra:

```text
before load
after load
providers=0
```

**`ServiceLoader.load` không báo lỗi chỉ vì không có provider.** Nó trả một ServiceLoader mà khi duyệt không có phần tử. Đó là hành vi đúng và khác với việc descriptor có tên provider bị sai/hỏng, có thể phát `ServiceConfigurationError`.

Nếu đưa `plugins/greeting-provider.jar` lên classpath nhưng **không có descriptor**, class `provider.VietnameseGreeting` vẫn có thể tồn tại và gọi được qua reflection, nhưng **ServiceLoader không tự quét toàn bộ JAR để suy đoán lớp nào implements `Greeting`**. Khai báo provider là bước không thể bỏ qua trong cơ chế SPI classpath.

## 5. Vì sao đặt META-INF/services ở sai root sẽ thất bại?

Classpath là tập hợp **roots**. Đối với JAR, root là đỉnh bên trong archive; đối với directory, root là folder được thêm vào `-cp`.

**Đúng với directory:**

```text
provider-classes/
├── provider/VietnameseGreeting.class
└── META-INF/services/api.Greeting
```

Khi chạy:

```bash
java -cp "host-classes;provider-classes" app.Main
```

(Windows; Linux/macOS đổi `;` thành `:`.)

**Đúng với JAR:**

```text
greeting-provider.jar
├── provider/VietnameseGreeting.class
└── META-INF/services/api.Greeting
```

**Sai khi vô tình để thêm tầng thư mục:**

```text
greeting-provider.jar
└── provider-classes/
    ├── provider/VietnameseGreeting.class
    └── META-INF/services/api.Greeting
```

Nếu artifact như trường hợp sai được dùng làm classpath JAR, resource thực tế là `provider-classes/META-INF/services/api.Greeting` chứ không phải `META-INF/services/api.Greeting` ở root nên ServiceLoader không tìm thấy descriptor chuẩn.

Một lỗi khác là file thực tế `api.Greeting.txt` vì hệ điều hành ẩn extension `.txt`; `ServiceLoader` chỉ tìm tên **`api.Greeting` đúng nguyên văn**.

**Quy tắc chẩn đoán:** trước khi debug Java code, chạy `jar --list --file ...` và xác nhận đường dẫn descriptor ở root, sau đó xác nhận `provider/...class` và toàn bộ dependencies được đóng gói đúng.

## 6. ServiceLoader dùng class loader nào để tìm descriptor?

Đây là chủ đề gắn trực tiếp với **classloader**:

```java
ServiceLoader<Greeting> services =
        ServiceLoader.load(Greeting.class);
```

Theo hợp đồng Java SE 17, overload `load(Class<S>)` dùng **thread context class loader (TCCL)**:

```java
ClassLoader tccl =
        Thread.currentThread().getContextClassLoader();

ServiceLoader<Greeting> services =
        ServiceLoader.load(Greeting.class, tccl);
```

Đoạn trên là **mã minh họa**, giả định `Greeting` đã có. Hai cách tương đương về lựa chọn loader tại thời điểm tạo loader.

Có thể chọn loader một cách tường minh:

```java
ClassLoader pluginLoader = /* loader của plugin đã tạo */;

ServiceLoader<Greeting> services =
        ServiceLoader.load(Greeting.class, pluginLoader);
```

Đây là **mã minh họa**, cần khai báo giá trị thực cho `pluginLoader`. Đặc biệt hữu ích khi provider JAR được một child `URLClassLoader` quản lý, còn interface `Greeting` ở parent loader.

### Đồ thị nhìn thấy thường gặp

```text
Application/System loader
    ├── api.Greeting
    └── app.Main
          ^
          | parent-first delegation
Plugin URLClassLoader
    ├── provider.VietnameseGreeting
    └── META-INF/services/api.Greeting
```

Nếu consumer do parent loader định nghĩa, nó không tự thấy resource/class chỉ nằm ở loader con. Nhưng nếu dùng:

```java
ServiceLoader.load(Greeting.class, pluginLoader)
```

thì discovery được thực hiện theo plugin loader, có thể tìm descriptor và provider class của child. Plugin loader cần delegate **api.Greeting** tới parent để provider và consumer dùng **cùng interface runtime type**.

**Không nhầm TCCL với `Class.forName(String)`.** Lời gọi `Class.forName("provider.VietnameseGreeting")` mặc định dùng **defining loader của class chứa call site**, không tự chọn TCCL. Vì thế việc đổi TCCL có thể khiến `ServiceLoader.load` phát hiện provider, nhưng không nhất thiết khiến một helper ở parent gọi `Class.forName(String)` tìm được provider class.

### Khi nào cần set TCCL?

Framework/app server có thể thiết lập context loader riêng cho thread đang chạy service discovery:

```java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    for (Greeting g : ServiceLoader.load(Greeting.class)) {
        System.out.println(g.message());
    }
} finally {
    thread.setContextClassLoader(previous);
}
```

Đây là **đoạn minh họa**, giả định `pluginLoader` và `Greeting` tồn tại. `try/finally` bắt buộc quan trọng khi dùng thread pool: nếu để lại TCCL của plugin A trên worker thread, request B có thể khám phá provider sai hoặc giữ plugin loader A sống quá hạn.

**Không phải API nào cũng đọc TCCL.** Chỉ dùng hoặc set TCCL khi hợp đồng của API thực sự sử dụng nó; khi có loader cụ thể, overload `ServiceLoader.load(service, loader)` rõ ràng và dễ kiểm soát.

## 7. Cơ chế lazy và cache của ServiceLoader

Trong Java 17:

```java
ServiceLoader<Greeting> loader =
        ServiceLoader.load(Greeting.class);
```

dòng này chủ yếu thiết lập đối tượng discovery. Khi duyệt:

```java
for (Greeting provider : loader) {
    System.out.println(provider.message());
}
```

`ServiceLoader` khám phá và khởi tạo provider khi cần. Một số chi tiết:

- `iterator()` là **lazy**: không cần instantiate mọi provider từ đầu.
- `iterator.hasNext()` có thể cần tìm/read metadata hoặc load provider class để biết còn phần tử; đừng coi nó như phép kiểm tra hoàn toàn không side effect.
- `iterator.next()` trả provider instance và có thể gọi constructor; exception có thể chỉ xuất hiện tại đây.
- `stream()` trả `Stream<ServiceLoader.Provider<S>>`. `Provider.type()` cho biết class provider; `Provider.get()` mới yêu cầu instance (theo contract tương ứng).
- Một `ServiceLoader` có thể **cache** providers đã được khởi tạo; duyệt lần sau trên cùng object thường thấy cached instances.
- `reload()` xóa cache để các lần duyệt sau khám phá lại theo loader/source phù hợp; **không tải lại bytecode đã định nghĩa hoặc cập nhật JAR đã bị class loader giữ ở namespace cũ**.
- `ServiceLoader` không đảm bảo **thread-safe** khi chia sẻ cùng một đối tượng giữa nhiều thread. Cần đồng bộ hoặc tạo service loader riêng theo scope.

Ví dụ code minh họa:

```java
ServiceLoader<Greeting> services =
        ServiceLoader.load(Greeting.class);
for (Greeting g : services) {
    System.out.println(g.getClass().getName());
}
services.reload();
```

Đây là **đoạn minh họa**, không phải chương trình đầy đủ. Gọi `reload()` có ý nghĩa khi trạng thái discovery thay đổi theo cách loader thực sự có thể nhìn thấy; nó không “ép” một `URLClassLoader` đã closed tìm lại JAR mới.

### Có thể có 0, 1 hoặc nhiều providers

Hợp đồng `ServiceLoader` không buộc chỉ có một implementation. Nếu nhiều JAR trên classpath có cùng service descriptor, `ServiceLoader` có thể đọc nhiều configuration resources và trả về nhiều providers. Ứng dụng phải quyết định chính sách: dùng tất cả, chọn một theo cấu hình/priority riêng hay báo lỗi nếu không duy nhất.

Đừng mặc định thứ tự providers giữa các JAR là một hợp đồng business ổn định. Thứ tự tìm resources/classpath có thể phụ thuộc launcher, build tool, loader policy và cách artifact được đóng gói. Nếu ưu tiên quan trọng, ứng dụng nên có metadata chọn provider rõ ràng.

## 8. Khai báo nhiều providers

Giả sử có hai class thật:

- `provider.VietnameseGreeting`
- `provider.EnglishGreeting`

Một descriptor có thể chứa:

```text
# Greeting implementations
provider.VietnameseGreeting
provider.EnglishGreeting
```

Mỗi provider phải là class hợp lệ, public, triển khai `api.Greeting` và có public zero-argument constructor theo classpath SPI rules.

### Nhiều JAR cùng cung cấp service

```text
providers-a.jar:
    META-INF/services/api.Greeting
      -> provider.VietnameseGreeting

providers-b.jar:
    META-INF/services/api.Greeting
      -> other.EnglishGreeting
```

Nếu cả hai JAR trên classpath phù hợp, `ServiceLoader` có thể thấy cả hai. Nếu cùng **một provider class name** được lặp nhiều lần trong cùng một hoặc nhiều configuration files, ServiceLoader bỏ qua **duplicate provider names** theo đặc tả.

Tuy nhiên, **duplicate binary class name** trong hai JAR không phải lỗi “duplicate descriptor”: classpath loader có thể chọn một class definition theo lookup order. Khi có version conflict, cùng tên `provider.FastCodec` nhưng bytecode khác nhau có thể khiến `ServiceLoader` chạy nhầm bản hoặc lỗi linkage.

### Khi tạo fat/uber JAR

Maven Shade, Gradle Shadow hoặc một công cụ đóng gói khác có thể **hợp nhất nhiều provider JAR thành một JAR**. Nếu công cụ chỉ copy một file `META-INF/services/api.Greeting` và ghi đè file còn lại, **các provider bị mất khai báo** dù class files vẫn được giữ.

Cần dùng chức năng **merge/transform service descriptor files**, không chỉ copy class files. Nếu relocate package/class name, phải cập nhật **nội dung service descriptors và tên service descriptor** cho khớp binary names mới.

Kết quả cuối cần kiểm tra bằng `jar --list` và xem **nội dung** file `META-INF/services/...` trong artifact, không chỉ kiểm tra class files. Một JAR fat với nhiều class provider nhưng descriptor chỉ còn một dòng sẽ chỉ đăng ký một provider ở đường classpath SPI.

## 9. Những lỗi phổ biến và thông báo thường gặp

| Triệu chứng | Nguyên nhân phổ biến | Kiểm tra |
| --- | --- | --- |
| `ServiceLoader` duyệt ra **0 provider** | Không có descriptor trong classpath/JAR; resource path sai; loader không nhìn thấy provider JAR | `jar --list`, `getResources`, classpath/TCCL |
| `ServiceConfigurationError: Provider ... not found` | Descriptor nêu binary name nhưng class không có trong loader/dependency | Tên class, package, JAR entries, loader |
| `ServiceConfigurationError: ... not a subtype` | Provider không implement **đúng runtime service interface** hoặc có hai bản interface do hai loaders định nghĩa | `isAssignableFrom`, `getClassLoader()` |
| `ServiceConfigurationError: Unable to get public no-arg constructor` | Provider classpath thiếu constructor public không tham số hoặc class không instantiate được | Visibility, constructors, abstract class |
| `ServiceConfigurationError` khi tạo provider | Constructor ném exception, lỗi initialization/linking hoặc có dependency thiếu | Stack trace, cause, runtime classpath |
| Một provider xuất hiện nhưng provider khác không | Descriptor chỉ còn một entry do fat JAR merger, hoặc provider lỗi khi duyệt tới | Nội dung toàn bộ `META-INF/services` |
| IDE chạy được nhưng `java -cp` không | IDE đã add resource root / runtime JAR nhưng script không | So classpath, build output roots |
| Provider hoạt động với `-cp` nhưng không với `--module-path` | Chuyển semantics sang named/automatic modules, `uses`/`provides`, resolution và module layers | Định vị artifact và module graph |
| Provider mới không xuất hiện sau hot deploy | ClassLoader cũ/cached ServiceLoader cũ không thấy artifact mới | New loader, `reload()` chỉ khi nguồn mới nhìn thấy, plugin lifecycle |
| `ClassCastException` giữa API và provider | Hai classloader định nghĩa bản sao interface cùng tên | Shared API parent loader và class identity |

Quan trọng: `ServiceConfigurationError` là **`Error`**, không phải `Exception` checked như `ClassNotFoundException`. Lỗi provider discovery có thể phát từ `iterator.hasNext()`, `next()` hoặc stream operation. Đừng bọc toàn bộ logic `ServiceLoader` trong `catch (ClassNotFoundException)` rồi nghĩ sẽ bắt mọi lỗi SPI.

Cũng đừng dựa vào kiểu thông báo lỗi chính xác để xác định root cause; nhiều JDK/module/classloader configurations có thể tạo message khác nhau. Luôn đọc **cause** và đường tìm thực tế.

## 10. Chẩn đoán descriptor có được nhìn thấy không

Trong một classpath application, có thể kiểm tra tên resource **trực tiếp qua loader**:

```java
ClassLoader context =
        Thread.currentThread().getContextClassLoader();

java.util.Enumeration<java.net.URL> descriptors =
        context.getResources("META-INF/services/api.Greeting");

while (descriptors.hasMoreElements()) {
    System.out.println(descriptors.nextElement());
}
```

Đây là **đoạn minh họa** đặt trong method xử lý `java.io.IOException`, giả định `context` không null. URL có thể là `file:/...` hoặc `jar:file:/...!/META-INF/services/api.Greeting`. Nếu không có kết quả, hãy kiểm tra TCCL, classpath và việc đóng gói JAR.

Lưu ý:

- `ClassLoader.getResources(name)` tìm **resource**, không khẳng định class provider đã compile đúng hoặc constructor hoạt động.
- Với Java 9+ named modules, resource encapsulation/exports/opens và lookup rules có thể khác đường classpath thông thường.
- Khi context loader là `null`, cần chọn loader thay thế **theo contract API và kiến trúc** trước khi gọi method trên reference null; `ServiceLoader.load(service)` có xử lý loader semantics riêng của nó.
- Kết quả tìm được descriptor chỉ là **bước đầu**, cần duyệt `ServiceLoader` để kiểm tra provider class thực tế.

Nếu muốn debug class identity:

```java
Class<?> providerClass =
        Class.forName("provider.VietnameseGreeting",
                      false, pluginLoader);

System.out.println(Greeting.class.isAssignableFrom(providerClass));
System.out.println(Greeting.class.getClassLoader());
System.out.println(providerClass.getClassLoader());
```

Đây là **mã minh họa**, giả định `Greeting` và `pluginLoader` đã có, method xử lý `ClassNotFoundException`. Nếu `isAssignableFrom` trả `false` dù provider source có `implements Greeting`, nghi ngờ plugin tự định nghĩa lại một bản `api.Greeting` khác với host.

### Một quy trình gỡ lỗi ngắn gọn

1. **Xác minh service binary name.** `Greeting.class.getName()` phải đúng `api.Greeting`, không nhầm provider name.
2. **Xác minh descriptor path.** Trong JAR cần có đúng `META-INF/services/api.Greeting` tại root.
3. **Xác minh nội dung descriptor.** Một tên binary provider hợp lệ mỗi dòng, UTF-8, không thêm `.class` hoặc dấu slash.
4. **Xác minh bytecode provider.** `provider/VietnameseGreeting.class` phải nằm trong JAR hoặc classpath root loader nhìn thấy.
5. **Xác minh visibility/constructor.** Class public, concrete, constructor public no-arg, đúng service subtype.
6. **Xác minh runtime loader.** `ServiceLoader.load(service)` dùng TCCL; khi plugin ở child, truyền đúng loader explicit.
7. **Xác minh runtime dependencies.** Class provider có thể load được nhưng khởi tạo lỗi vì thư viện khác không có hoặc static initializer thất bại.
8. **Xác minh đóng gói fat JAR.** Service descriptor có bị ghi đè bởi merging/shading không?
9. **Xác minh JPMS mode.** Artifact ở classpath/unnamed hay module path/automatic/explicit? Cách khai báo provider khác nhau.
10. **Thử chương trình tối thiểu.** Chạy dạng `-cp` với interface, provider JAR và descriptor để tách lỗi framework khỏi lỗi SPI metadata.

## 11. Phân biệt SPI classpath với khai báo provider của JPMS

Trên **classpath/unnamed module**, provider thường đăng ký bằng tệp:

```text
META-INF/services/api.Greeting
```

và `ServiceLoader` đọc resource tương ứng.

Với **explicit named modules** từ Java 9, service consumption/provider registration thường được mô tả ngay trong `module-info.java`:

```java
module m.consumer {
    requires m.api;
    uses api.Greeting;
}
```

```java
module m.provider {
    requires m.api;
    provides api.Greeting with provider.VietnameseGreeting;
}
```

Đây là **hai tệp `module-info.java` minh họa riêng biệt**, không ghép hai `module` declarations vào một file. Module `m.api` phải công bố API package phù hợp. Với named module, `uses` thường bắt buộc cho code trực tiếp gọi `ServiceLoader` với service; `provides ... with ...` là khai báo provider trong descriptor module. Provider của explicit named module có thể dùng public constructor hoặc **provider method** theo yêu cầu API và ngữ cảnh.

**Automatic module** là named module suy ra từ non-modular JAR trên module path. Các service configuration files `META-INF/services` trong JAR này có thể được module finder dùng để tạo khai báo `provides` phù hợp; automatic module còn có các quy tắc readability/exports riêng. Không nói rằng automatic module giống hệt classpath unnamed module, dù JAR content ban đầu là legacy.

### So sánh ngắn

| Tiêu chí | Classpath/unnamed module | Explicit named module |
| --- | --- | --- |
| Đăng ký provider | `META-INF/services/<service>` | `provides Service with Provider;` |
| Consumer khai báo dùng service | Không có `module-info` | `uses Service;` |
| Provider constructor | Public zero-arg constructor theo classpath provider form | Có thêm provider factory method form theo hợp đồng |
| Tìm lớp | Theo classpath loader và delegation | Theo module resolution, module-to-loader mapping và service discovery |
| TCCL | `ServiceLoader.load(Class)` dùng TCCL | API overload cũng có semantics module/service resolution; chọn module/layer phù hợp |
| Đóng gói | JAR/classpath root phải có descriptor file | Modular JAR chứa `module-info.class`, `provides` metadata |

Đừng đồng thời để **bản trùng class** của cùng provider trên cả classpath và module path rồi mong chỉ có một runtime type. Đường nạp cụ thể, module graph và class loader có thể khiến namespace/identity khác với dự kiến.

## 12. ServiceLoader trong plugin có class loader riêng

Plugin frameworks thường có service interface do host định nghĩa và provider do plugin loader định nghĩa:

```text
Host/system loader
  ├── api.Greeting
  └── host.PluginManager
           ^
           |
           | parent delegation
           |
Plugin URLClassLoader
  ├── provider.VietnameseGreeting
  └── META-INF/services/api.Greeting
```

Nếu code `PluginManager` chỉ gọi:

```java
ServiceLoader.load(Greeting.class, Greeting.class.getClassLoader())
```

thì discovery bắt đầu từ **loader của interface**, thường là parent/system. Loader cha không mặc nhiên thấy descriptor chỉ ở child loader, nên có thể nhận **0 provider**.

Để chọn đúng vùng plugin, dùng:

```java
ServiceLoader<Greeting> services =
        ServiceLoader.load(Greeting.class, pluginLoader);
```

Code trên là **đoạn minh họa**, `pluginLoader` phải là loader đã tạo đúng cho deployment. API `Greeting` cần được parent/shared loader định nghĩa để host/provider cùng identity. Tránh copy interface vào child theo child-first policy khiến plugin tự define một `api.Greeting` khác.

### TCCL và thread pool

Một thread pool worker có thể mang TCCL khác thread gọi submit. Nếu `ServiceLoader.load(service)` được gọi trong worker, hãy kiểm tra TCCL **tại chính worker**. Khi đổi context tạm thời, restore trong `finally`:

```java
Thread worker = Thread.currentThread();
ClassLoader before = worker.getContextClassLoader();
try {
    worker.setContextClassLoader(pluginLoader);
    // ServiceLoader.load(Greeting.class) trong phạm vi này.
} finally {
    worker.setContextClassLoader(before);
}
```

Đây là **đoạn minh họa**, không phải file chạy độc lập. Nếu worker giữ TCCL trỏ vào plugin loader sau khi plugin đã “undeploy”, vừa có nguy cơ khám phá nhầm provider, vừa có thể giữ class loader sống và gây metaspace leak.

**`ServiceLoader` không phải cơ chế sandbox**. Đọc và khởi tạo provider có nghĩa chương trình có thể **chạy code từ constructor/static initializer** của provider. Chỉ sử dụng provider artifacts từ nguồn được tin cậy, kiểm tra version/signature/dependencies và áp dụng biện pháp cô lập phù hợp.

## 13. Cách kiểm thử descriptor tự động trong dự án thực

Dù dùng Maven, Gradle hay IDE, bạn nên có test xác nhận **artifact đã build** chứ không chỉ source tree.

### 13.1 Kiểm tra resource

```java
var resources = Thread.currentThread()
        .getContextClassLoader()
        .getResources("META-INF/services/api.Greeting");

if (!resources.hasMoreElements()) {
    throw new AssertionError("SPI descriptor missing");
}
```

Đây là **đoạn minh họa trong method xử lý IOException**, giả định TCCL phù hợp và non-null. Test này chỉ đảm bảo resource được tìm thấy, chưa khẳng định provider class hợp lệ.

### 13.2 Kiểm tra có ít nhất một provider

```java
boolean found = ServiceLoader.load(Greeting.class)
        .iterator()
        .hasNext();

if (!found) {
    throw new AssertionError("No Greeting provider discovered");
}
```

Đây là **đoạn minh họa** trong môi trường đã có `Greeting` và runtime loader phù hợp. Nếu metadata hỏng, thao tác `hasNext()` có thể phát `ServiceConfigurationError` thay vì chỉ trả `false`.

### 13.3 Kiểm tra class identity và gọi method

```java
for (Greeting greeting : ServiceLoader.load(Greeting.class)) {
    if (!Greeting.class.isInstance(greeting)) {
        throw new AssertionError("Provider type mismatch");
    }
    System.out.println(greeting.message());
}
```

Đây cũng là **đoạn minh họa**. Nếu ServiceLoader đã trả về `Greeting` instance hợp lệ, `isInstance` thường hiển nhiên true; đoạn code thể hiện tiêu chí contract trong test. Với provider phức tạp, thêm assert về hành vi thực của service (kết quả, configuration, lifecycle).

### 13.4 Kiểm tra JAR sau packaging

Chạy:

```bash
jar --list --file plugins/greeting-provider.jar
```

Sau đó nếu dùng Maven Shade/Gradle Shadow, kiểm tra nội dung `META-INF/services/api.Greeting` trong **final artifact**. Đối với provider multi-JAR, đảm bảo resource merge chính xác. Không suy từ việc `src/main/resources/META-INF/services/...` tồn tại rằng nó chắc chắn đã được copy vào JAR cuối.

## 14. Checklist ứng dụng đúng ngay lần đầu

1. **Service interface** có binary name nào? Trong ví dụ: `api.Greeting`.
2. **Provider implementation** có tên binary đầy đủ nào? `provider.VietnameseGreeting`.
3. Descriptor có ở đúng **`META-INF/services/<ServiceBinaryName>`** tại classpath/JAR root không?
4. Nội dung file UTF-8, một provider name mỗi dòng, không có extension/path/constructor syntax không?
5. Provider class public, concrete, implement đúng service runtime type và có public no-arg constructor chưa?
6. JAR chứa provider có mặt **trên runtime classpath hoặc loader thực sự được dùng để discovery** không?
7. Nếu dùng TCCL, context của **thread đang gọi ServiceLoader** có đúng loader không?
8. Parent/child loaders có dùng **cùng interface `Class<?>`** hay đang có bản sao API trong plugin?
9. Đã **duyệt ServiceLoader** chưa? Không có provider không nhất thiết gây lỗi khi chỉ gọi `load`.
10. Có `ServiceConfigurationError` xảy ra tại `iterator`/`stream` không? Đã log stack/cause?
11. Nếu là fat JAR, descriptor nhiều providers đã được merge chưa?
12. Nếu chuyển lên module path, đã xét automatic/explicit modules, `uses`/`provides`, resolution và access chưa?
13. Nếu cần hot reload, có loader/ServiceLoader mới và cleanup TCCL/cache/instances đúng scope chưa?
14. Đã có integration test chạy **artifact đã đóng gói** thay vì chỉ code trong IDE chưa?

## 15. Tự kiểm tra có đáp án

**Câu 1.** File nào đăng ký `provider.VietnameseGreeting` như một implementation của `api.Greeting`?

**Đáp án:** `META-INF/services/api.Greeting`, trong file chứa `provider.VietnameseGreeting` ở một dòng UTF-8.

**Câu 2.** Nếu file nằm ở `src/META-INF/services/api.Greeting` nhưng runtime chỉ có `-cp out` và không copy file vào `out`, ServiceLoader có tự tìm ở `src` không?

**Đáp án:** Không. Loader chỉ tìm resource từ những classpath entries nó thấy. Cần copy descriptor vào đúng output/resource root hoặc đóng gói trong JAR phù hợp.

**Câu 3.** `ServiceLoader.load(Greeting.class)` có luôn tạo instance ngay không?

**Đáp án:** Không. Service loading lazy; provider thường được instantiate trong lúc duyệt hoặc khi gọi `Provider.get()`. Ví dụ đã chứng minh dòng `after load` in trước `provider constructor`.

**Câu 4.** Provider class có private no-arg constructor dùng được cho SPI trên classpath không?

**Đáp án:** Không theo classpath provider constructor form thông thường. Provider cần public class với public zero-argument constructor và subtype phù hợp.

**Câu 5.** Nếu descriptor ghi `provider/VietnameseGreeting.class`, có hợp lệ không?

**Đáp án:** Không. Nội dung phải là **binary class name**: `provider.VietnameseGreeting`, không phải filesystem/JAR entry path.

**Câu 6.** Vì sao có provider class trên classpath nhưng `ServiceLoader` trả 0?

**Đáp án:** Có thể thiếu descriptor, descriptor path/name sai, runtime loader không thấy descriptor, hoặc class chỉ ở vị trí khác; `ServiceLoader` không tự quét mọi class để tìm subtype.

**Câu 7.** Nếu plugin provider chỉ có trong child loader, nhưng host gọi `ServiceLoader.load(Greeting.class, Greeting.class.getClassLoader())`, chuyện gì có thể xảy ra?

**Đáp án:** Nếu interface ở parent/system loader và descriptor chỉ ở child, parent không tự tìm xuống child, nên discovery có thể không thấy provider. Truyền explicit child loader hoặc dùng TCCL phù hợp.

**Câu 8.** Vì sao cùng tên interface `api.Greeting` vẫn có thể khiến provider bị báo không phải subtype?

**Đáp án:** Host và plugin có thể đã định nghĩa **hai runtime interfaces khác nhau** qua hai defining class loaders. JVM identity dựa binary name + defining loader; cần chia sẻ API ở parent/shared loader.

**Câu 9.** Nếu nhiều JAR đều có descriptor cho `api.Greeting`, có bắt buộc chỉ chọn provider đầu tiên không?

**Đáp án:** Không. `ServiceLoader` có thể phát hiện nhiều providers; consumer phải có chính sách chọn/sử dụng. Tránh dựa vào classpath order như một cơ chế priority business ổn định.

**Câu 10.** Chuyển non-modular provider JAR từ classpath sang module path nhưng không sửa code, có thể gọi nó là unnamed module tiếp không?

**Đáp án:** Không theo mặc định. JAR non-modular trên module path có thể trở thành automatic **named** module nếu được resolve; semantics JPMS/service discovery thay đổi so với classpath unnamed module.

**Câu 11.** Vì sao provider trong fat JAR bị mất dù `.class` vẫn tồn tại?

**Đáp án:** Build/shading tool có thể ghi đè `META-INF/services` thay vì merge descriptors. Khi ấy ServiceLoader chỉ biết những provider names còn lại trong final resource.

**Câu 12.** `reload()` có đổi class bytes của `provider.VietnameseGreeting` đã load sang version mới không?

**Đáp án:** Không. `reload()` xóa cache của `ServiceLoader`; class identity/bytecode còn do defining loader quyết định. Hot reload thực sự thường cần plugin loader mới, registry/TCCL cleanup và quản lý lifecycle.

## Kết luận

Đối với SPI trên **classpath**, cần đồng thời đúng **service interface**, **public provider implementation**, **tệp `META-INF/services/<service-binary-name>` ở classpath/JAR root** và **ClassLoader dùng cho discovery**. `ServiceLoader` dựa vào các file cấu hình này để tìm provider một cách lazy, chứ không tự quét mọi class trong JAR và cũng không tự bắc cầu từ loader cha xuống mọi plugin loader con.

Muốn hệ thống dễ mở rộng và dễ debug, hãy giữ shared API ở loader chung, đảm bảo descriptor được copy/merge vào artifact cuối, truyền loader/TCCL theo hợp đồng rõ ràng, và thử nghiệm ít nhất cả hai trường hợp **có JAR provider** (tìm được một implementation) và **không có JAR** (0 provider, không phải cấu hình hỏng).

**Tài liệu chính thức Java 17:**

- [`java.util.ServiceLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html) — provider configuration files, public no-arg constructor trên classpath, lazy discovery, caching, `load`/`loadInstalled`/`reload` và `ServiceConfigurationError`.
- [`java.lang.ClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html) — parent delegation và resource lookup.
- [`java.lang.Thread` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html) — context class loader.
- [Java SE 17 JAR File Specification](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html) — JAR entry/root/manifest layout.
- [`java.lang.Module` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html) — module boundaries cho SPI ở module path.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-loading-003">uses và provides trong module-info.java</a>

<details>
<summary>Xem chi tiết</summary>

# `uses` và `provides` trong `module-info.java`: khai báo và nạp dịch vụ Java

## 1. Vấn đề cần giải quyết

Một ứng dụng Java thường cần mở rộng chức năng bằng nhiều cách cài đặt của **cùng một hợp đồng**: bộ định dạng dữ liệu, hệ lưu trữ, driver, công cụ chuyển đổi hoặc plugin. Nếu ứng dụng biết cứng tên mọi lớp triển khai rồi gọi `new` trực tiếp, nó bị gắn chặt với những triển khai cụ thể. **Service Provider Interface (SPI)** cùng `java.util.ServiceLoader` cho phép ứng dụng khai báo **mình sử dụng dịch vụ nào**, còn các thư viện triển khai **công bố khả năng cung cấp dịch vụ**.

Khi dùng **Java Platform Module System (JPMS)** từ Java 9, hai khai báo trọng tâm nằm trong `module-info.java`:

```java
uses com.example.spi.GreetingService;
```

và:

```java
provides com.example.spi.GreetingService
        with com.example.provider.EnglishGreeting;
```

Đây là **hai mảnh khai báo** trong hai module descriptor khác nhau, chưa phải chương trình chạy hoàn chỉnh.

- **`uses ServiceType;`** đặt trong **module sử dụng** dịch vụ. Nó công bố rằng mã của module có thể tìm provider cho service type đó bằng `ServiceLoader`.
- **`provides ServiceType with ProviderType1, ProviderType2;`** đặt trong **module cung cấp**. Nó đăng ký provider cho service type. `with` liệt kê tên provider class; đây là khai báo module, không phải lệnh tự động chạy constructor.
- **`ServiceLoader.load(ServiceType.class)`** là lời gọi runtime **khám phá và lấy provider** theo quy tắc module/class loader tương ứng.
- **`requires` và `exports`** hỗ trợ khả năng đọc, thấy **kiểu service API**, nhưng **không thay cho `uses` hoặc `provides`**.

Mục tiêu bài học là giải thích ngữ nghĩa, cơ chế resolution và class loading, code Java 17+ chạy được, cách bố trí file, lỗi thường gặp và sự khác biệt với cơ chế `META-INF/services` của classpath truyền thống.

## 2. Bốn vai trò trong kiến trúc dịch vụ

Giả sử có hợp đồng `GreetingService` và hai triển khai tiếng Anh, tiếng Việt:

```text
learning.services.api
    └── learning.services.api.GreetingService  (service interface)
                    ^
                    |
           công bố/triển khai
               /        \
              /          \
learning.services.en   learning.services.vi
provides ... with ...  provides ... with ...
              \          /
               \        /
                v      v
        learning.services.app
        requires API; uses API
        ServiceLoader.load(...)
```

Có bốn khái niệm cần giữ riêng:

| Khái niệm | Vai trò | Ví dụ |
| --- | --- | --- |
| **Service** | Hợp đồng interface hoặc abstract class mà consumer muốn dùng | `GreetingService` |
| **Service consumer** | Module gọi `ServiceLoader` để lấy triển khai | `learning.services.app` |
| **Service provider** | Module đăng ký một/một số provider bằng `provides ... with ...` | `learning.services.en` |
| **Provider implementation** | Lớp thực hiện hợp đồng hoặc lớp cung cấp factory `provider()` hợp lệ | `EnglishGreeting` |

**Module provider** không đồng nghĩa **đối tượng provider**. Một module có thể đăng ký nhiều provider types, và một service có thể nhận nhiều provider từ các module khác nhau. Thứ tự phát hiện provider không nên được dùng làm chính sách ưu tiên ứng dụng.

### Phân biệt `requires`, `exports`, `uses`, `provides`

| Chỉ thị trong module-info.java | Ai khai báo? | Ý nghĩa |
| --- | --- | --- |
| `requires learning.services.api;` | Consumer và provider đều có thể cần | Cho module đọc module chứa kiểu API |
| `exports learning.services.api;` | Module API | Cho phép module khác truy cập các kiểu public trong package API |
| `uses learning.services.api.GreetingService;` | Module gọi `ServiceLoader` | Công bố quyền/hợp đồng sử dụng một loại service |
| `provides learning.services.api.GreetingService with ...;` | Module provider | Công bố một provider của service cho cơ chế khám phá |
| `opens package.name;` | Module cần reflective access theo quy tắc JPMS | Không tự động đăng ký service provider |
| `requires static ...;` | Module có dependency tùy chọn lúc runtime | Không thay cho đăng ký provider |

**Điểm cốt lõi:** `requires provider.module` không phải cơ chế thích hợp để “khám phá” provider khi consumer chỉ muốn biết service API. Consumer cần khai báo `uses` và duy trì hợp đồng SPI; provider được công bố qua `provides`, còn module resolution/service binding quyết định provider nào tham gia graph tại runtime.

## 3. Cú pháp và quy tắc cơ bản

### 3.1. `uses`

Trong module descriptor:

```java
module learning.services.app {
    requires learning.services.api;
    uses learning.services.api.GreetingService;
}
```

Module consumer đọc module API và khai báo mình dùng service type đó. Cú pháp `uses` nhận **tên kiểu** (*type name*), không nhận tên file, tên JAR hay object instance.

Nếu named module gọi cơ chế `ServiceLoader.load(...)` cho một service type mà module này **không khai báo `uses`**, quá trình tải có thể thất bại bằng `ServiceConfigurationError` nêu việc module caller không khai báo dùng service đó (theo API `ServiceLoader`). Không nên bỏ qua `uses` chỉ vì module đã `requires` module API.

### 3.2. `provides ... with ...`

Trong module cung cấp:

```java
module learning.services.en {
    requires learning.services.api;
    provides learning.services.api.GreetingService
        with learning.services.en.internal.EnglishGreeting;
}
```

Provider type cần đáp ứng điều kiện đăng ký hợp lệ. Trường hợp thông thường là một **public provider class** triển khai service interface và có **public no-argument constructor**.

Đối với **explicit named module**, `ServiceLoader` còn hỗ trợ **provider factory method** có tên `provider` theo quy tắc API, thay vì bắt buộc lớp provider được khai báo trực tiếp implements service. Ta sẽ phân biệt hai cách ở phần nâng cao.

### 3.3. Có bắt buộc `exports` package implementation không?

**Không.** Module `learning.services.en` có thể đặt `EnglishGreeting` trong package `learning.services.en.internal` và **không export package này**. ServiceLoader có cơ chế hỗ trợ nạp provider được module descriptor khai báo hợp lệ mà không cần consumer truy cập trực tiếp lớp implementation. Consumer chỉ lập trình theo interface public được export từ module API.

Nếu consumer cố import/call trực tiếp `learning.services.en.internal.EnglishGreeting`, trình biên dịch hoặc module access checks sẽ từ chối khi package không được export; đây không phải lỗi của `ServiceLoader`.

## 4. Demo Java 17 hoàn chỉnh: 1 service, 2 providers, 1 consumer

Ví dụ sử dụng bốn **explicit named modules**:

1. `learning.services.api` định nghĩa hợp đồng `GreetingService`.
2. `learning.services.en` cung cấp provider tiếng Anh.
3. `learning.services.vi` cung cấp provider tiếng Việt.
4. `learning.services.app` khai báo `uses` và gọi `ServiceLoader`.

Không cần dependency ngoài JDK, Maven hay Gradle. Dùng JDK 17+ có `javac` và `java`.

### 4.1. Cấu trúc thư mục

```text
src/
├── learning.services.api/
│   ├── module-info.java
│   └── learning/services/api/GreetingService.java
├── learning.services.en/
│   ├── module-info.java
│   └── learning/services/en/internal/EnglishGreeting.java
├── learning.services.vi/
│   ├── module-info.java
│   └── learning/services/vi/internal/VietnameseGreeting.java
└── learning.services.app/
    ├── module-info.java
    └── learning/services/app/Main.java
```

Tên thư mục dưới `src` tương ứng tên module; `javac --module-source-path src` sử dụng quy ước layout này.

### 4.2. Service API module

File `src/learning.services.api/module-info.java`:

```java
module learning.services.api {
    exports learning.services.api;
}
```

File `src/learning.services.api/learning/services/api/GreetingService.java`:

```java
package learning.services.api;

public interface GreetingService {
    String code();
    String greet(String name);
}
```

`exports` cho phép consumer và provider **sử dụng kiểu public** `GreetingService` ở package API. Nếu thiếu export (và không có cơ chế truy cập khác), module khác không thể viết `implements GreetingService` theo cách này.

### 4.3. Provider tiếng Anh

File `src/learning.services.en/module-info.java`:

```java
module learning.services.en {
    requires learning.services.api;
    provides learning.services.api.GreetingService
        with learning.services.en.internal.EnglishGreeting;
}
```

File `src/learning.services.en/learning/services/en/internal/EnglishGreeting.java`:

```java
package learning.services.en.internal;

import learning.services.api.GreetingService;

public final class EnglishGreeting implements GreetingService {
    public EnglishGreeting() {}

    @Override
    public String code() {
        return "en";
    }

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
```

Lớp provider public, implements interface, có public constructor không tham số. Package `internal` **không export** từ provider module, nhưng `provides ... with ...` vẫn hợp lệ.

### 4.4. Provider tiếng Việt

File `src/learning.services.vi/module-info.java`:

```java
module learning.services.vi {
    requires learning.services.api;
    provides learning.services.api.GreetingService
        with learning.services.vi.internal.VietnameseGreeting;
}
```

File `src/learning.services.vi/learning/services/vi/internal/VietnameseGreeting.java`:

```java
package learning.services.vi.internal;

import learning.services.api.GreetingService;

public final class VietnameseGreeting implements GreetingService {
    public VietnameseGreeting() {}

    @Override
    public String code() {
        return "vi";
    }

    @Override
    public String greet(String name) {
        return "Xin chào, " + name;
    }
}
```

Một service interface có thể có nhiều provider; mỗi provider được đăng ký trong module descriptor của module chứa provider đó.

### 4.5. Consumer module

File `src/learning.services.app/module-info.java`:

```java
module learning.services.app {
    requires learning.services.api;
    uses learning.services.api.GreetingService;
}
```

File `src/learning.services.app/learning/services/app/Main.java`:

```java
package learning.services.app;

import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;
import learning.services.api.GreetingService;

public final class Main {
    public static void main(String[] args) {
        SortedMap<String, String> results = new TreeMap<>();

        for (GreetingService provider :
                ServiceLoader.load(GreetingService.class)) {
            results.put(provider.code(), provider.greet("Java"));
        }

        System.out.println("providers=" + results.size());
        results.forEach((code, message) ->
                System.out.println(code + "=" + message));
    }
}
```

**Vì sao chọn `TreeMap`?** `ServiceLoader` có thể tìm nhiều provider và không nên coi thứ tự cung cấp của chúng là thứ tự ưu tiên nghiệp vụ. `TreeMap` sắp xếp theo `code()`, giúp **stdout có thứ tự ổn định** `en` trước `vi`, miễn mỗi provider trả một mã riêng.

**Vì sao không import EnglishGreeting trong Main?** Consumer chỉ phụ thuộc vào exported service interface; lớp implementation nằm trong package không export và có thể thay đổi độc lập.

### 4.6. Biên dịch theo module-source-path

Đặt terminal tại thư mục chứa `src`:

```bash
javac -encoding UTF-8 --release 17 --module-source-path src -d mods -m learning.services.api,learning.services.en,learning.services.vi,learning.services.app
```

Lệnh biên dịch các module cùng những source files theo layout `--module-source-path` vào `mods/<module-name>/...`. `-encoding UTF-8` giúp source tiếng Việt được giải mã nhất quán khi trình biên dịch chạy trên hệ điều hành có charset mặc định khác.

### 4.7. Chạy với các provider module được resolve

```bash
java --module-path mods --add-modules learning.services.en,learning.services.vi --module learning.services.app/learning.services.app.Main
```

Giải thích:

- `--module-path mods` chỉ vị trí các module được biên dịch.
- `--module learning.services.app/learning.services.app.Main` chỉ định module và main class.
- `--add-modules learning.services.en,learning.services.vi` yêu cầu hai provider module được đưa vào root set và giải quyết trong module graph, để demo không phụ thuộc vào suy đoán về service binding mặc định của môi trường launcher.
- Consumer vẫn **không import** bất kỳ provider implementation nào; `ServiceLoader` khám phá chúng qua descriptor.

**Stdout dự kiến khi chạy trong môi trường JDK 17+ bình thường:**

```text
providers=2
en=Hello, Java
vi=Xin chào, Java
```

Thứ tự ở đây đến từ `TreeMap`; không phải lời hứa về thứ tự nguyên bản của `ServiceLoader`.

### 4.8. Những gì đã xảy ra tại runtime

1. Module graph chứa `learning.services.api`, `learning.services.app` và hai provider modules.
2. Consumer đọc kiểu `GreetingService` từ API module nhờ `requires` + `exports`.
3. `uses` của consumer công bố service mà consumer có quyền khám phá.
4. `provides` trong các provider module đăng ký các lớp implement.
5. `ServiceLoader.load(GreetingService.class)` tạo công cụ khám phá; khi lặp provider, Java tìm rồi khởi tạo các provider thích hợp.
6. `GreetingService.code()/greet()` được gọi qua interface; không cần API truy cập trực tiếp các package `internal`.
7. Kết quả được sort và in ra; không có cam kết về ưu tiên của provider bên trong `ServiceLoader`.

**Lưu ý:** việc có provider module trên module path **chưa đủ** để mọi API khám phá nhìn thấy; module phải được đưa vào cấu hình module layer/graph thích hợp. `--add-modules` trong demo là cách làm rõ điều kiện này. Trong các launcher thực tế, dịch vụ cũng có thể được bind bởi module resolution hoặc application framework.

## 5. `ServiceLoader` có thực hiện việc gì ngay tại load()?

`ServiceLoader.load(ServiceType.class)` tạo một đối tượng `ServiceLoader<ServiceType>`; provider được tìm/khởi tạo **theo cơ chế lazy** khi iterator hoặc stream được sử dụng. Đây không phải cú pháp thay thế của `new EnglishGreeting()` và không bảo đảm ngay lúc gọi `load(...)` đã tạo tất cả provider objects.

Ví dụ snippet trong consumer có `uses GreetingService`:

```java
ServiceLoader<GreetingService> loader =
        ServiceLoader.load(GreetingService.class);

for (GreetingService service : loader) {
    System.out.println(service.getClass().getName());
}
```

Đây là snippet **không độc lập** (cần import, service module và provider phù hợp).

Một số API quan trọng:

| API | Công dụng |
| --- | --- |
| `ServiceLoader.load(Service.class)` | Dùng context class loader thích hợp theo API và quyền module caller |
| `ServiceLoader.load(Service.class, loader)` | Tra với class loader được chỉ định theo semantics API; vẫn có kiểm tra module caller |
| `ServiceLoader.load(layer, Service.class)` | Khám phá provider trong module layer được chỉ định và các layer cha theo hợp đồng |
| `iterator()` hoặc vòng `for` | Lần lượt lấy/khởi tạo provider instances |
| `stream()` | Truy cập `ServiceLoader.Provider<S>` để xem type và lấy instance thông qua `get()` |
| `reload()` | Xóa cache provider của ServiceLoader; lần lặp sau tìm lại theo cơ chế API |
| `ServiceConfigurationError` | Lỗi cấu hình service/provider, lớp không hợp lệ hoặc lỗi khi tìm/khởi tạo provider |

`reload()` **không** gỡ các lớp đã được JVM định nghĩa hoặc tự thay đổi mọi runtime class từ JAR cũ. Nếu plugin được cập nhật, có thể còn phải tạo module layer/class loader mới và quản lý reference vòng đời.

**TCCL:** `Thread.currentThread().getContextClassLoader()` có thể ảnh hưởng các overload của `ServiceLoader` sử dụng context loader, nhất là provider trên classpath hay framework tùy chỉnh. Nhưng thay TCCL không “cấp” `uses` cho named module caller, cũng không đổi defining loader của lớp đã được nạp.

## 6. Service provider có cần export package implementation?

Không. Một mục tiêu của JPMS service mechanism là **che implementation detail nhưng cho phép service discovery**.

Ví dụ:

```java
module learning.services.en {
    requires learning.services.api;
    provides learning.services.api.GreetingService
        with learning.services.en.internal.EnglishGreeting;
}
```

Chỉ thị trên **không export** `learning.services.en.internal`. Consumer gọi interface `GreetingService`, không cần nhìn thấy implementation package.

Cần tránh cách sửa sai:

```java
module learning.services.en {
    requires learning.services.api;
    exports learning.services.en.internal;
    provides learning.services.api.GreetingService
        with learning.services.en.internal.EnglishGreeting;
}
```

Đây là **đoạn cấu hình có thể biên dịch nhưng không cần thiết** nếu chỉ export package nhằm giúp ServiceLoader nhận provider. `exports` làm mở rộng public surface cho compile/runtime module access; không nên làm khi không có nhu cầu API trực tiếp.

### Public constructor và phạm vi truy cập

Với kiểu provider được **khởi tạo qua constructor**, thông thường provider class phải là `public`, không abstract và có **public no-args constructor**. Cơ chế dịch vụ có khả năng tạo instance theo mô hình đăng ký module; không yêu cầu consumer được truy cập trực tiếp package implementation qua `exports`.

Nếu constructor đòi hỏi đối số, ném exception hoặc không thể khởi tạo, discovery/iteration có thể thất bại bằng `ServiceConfigurationError`; cần xem `Caused by` để biết nguyên nhân gốc.

## 7. Provider factory method (nâng cao)

Đối với provider trong **explicit named module**, `ServiceLoader` hỗ trợ khai báo một **provider class có public static no-arg method tên `provider`** trả về kiểu có thể gán cho service. Điều này cho phép sử dụng factory và che constructor/implementation:

```java
package example.provider.internal;

import example.api.Compressor;

public final class CompressorFactory {
    private CompressorFactory() {}

    public static Compressor provider() {
        return new FastCompressor();
    }
}
```

Đây là **mã minh họa**, không phải ví dụ runnable độc lập: `Compressor` và `FastCompressor` phải được định nghĩa trong module thích hợp.

Module descriptor:

```java
module example.provider {
    requires example.api;
    provides example.api.Compressor
        with example.provider.internal.CompressorFactory;
}
```

- Trong phương án factory, lớp được liệt kê sau `with` có thể là class **cung cấp đối tượng dịch vụ**, không nhất thiết chính lớp đó implements service; method `provider()` trả về đối tượng thích hợp.
- Provider method phải đáp ứng điều kiện Java ServiceLoader API; nếu có nhiều overload/khai báo factory không hợp lệ, có thể gây `ServiceConfigurationError`.
- Cách factory provider method này áp dụng cho **explicit modules**, không phải cấu hình `META-INF/services` trên classpath theo kiểu cũ; với provider class trong unnamed module/automatic module, quy tắc constructor-based tương ứng vẫn quan trọng.
- Không nhầm factory method `provider()` của SPI với annotation injection framework hay Java method reflection phổ thông; nó có một hợp đồng riêng trong `ServiceLoader`.

### Vì sao factory method hữu ích?

Factory có thể quyết định implementation phù hợp dựa trên cấu hình hoặc tạo đối tượng service với dependencies mà không cần public no-arg constructor của implementation thật. Tuy nhiên, provider module chịu trách nhiệm factory không được tự ý tạo các class/plugin không tin cậy trong JVM chính mà không có cơ chế an toàn thích hợp.

## 8. JPMS service binding và module resolution

Từ khóa `uses`/`provides` nằm trong descriptor; nhưng **module graph** tại runtime mới quyết định những module provider nào sẵn sàng cho service discovery.

Trong Java launcher:

- Named module được chỉ định làm main module là root quan trọng.
- `requires` kéo các module được đọc vào graph theo module resolution.
- `--add-modules` cho phép thêm modules làm roots.
- Có cơ chế **service binding** trong quá trình resolution để liên kết consumer dùng service với provider modules được quan sát; hành vi còn phụ thuộc cách launcher/layer được xây dựng và các tập module có thể quan sát.
- Không nên suy luận “chỉ cần JAR provider nằm trên module path chắc chắn sẽ được load” ở mọi ứng dụng.

Bài demo dùng `--add-modules learning.services.en,learning.services.vi` nên provider modules chắc chắn hiện diện trong resolved graph khi các descriptor và dependency đúng. Khi triển khai, có thể thay thế bằng module resolution có service binding được cấu hình chủ đích.

### Lệnh chẩn đoán có ích

```bash
java --show-module-resolution --module-path mods --add-modules learning.services.en,learning.services.vi --module learning.services.app/learning.services.app.Main
```

Phần `--show-module-resolution` cho biết các module được resolve và nguồn/quan hệ tham gia, giúp phát hiện module provider vắng mặt hoặc chọn sai artifact. Output chẩn đoán thay đổi theo JDK và graph nên bài không gán cố định toàn bộ các dòng log.

Nếu chạy:

```bash
java --module-path mods --add-modules learning.services.en --module learning.services.app/learning.services.app.Main
```

thì **không nên khẳng định chắc chắn chỉ có một provider** trong mọi cơ chế service binding. Điều chắc chắn là module `learning.services.en` được thêm trực tiếp; các module khác có thể được resolve bởi service binding hoặc cấu hình launcher/layer. Khi cần kiểm thử loại trừ `learning.services.vi`, hãy dùng module path/graph chỉ cung cấp những module mong muốn hoặc cấu hình layer rõ ràng.

## 9. ClassLoader và module layer trong ServiceLoader

Trong classpath truyền thống, các plugin đôi khi được tải bằng `URLClassLoader` riêng. Trong JPMS, hệ thống có thể có **ModuleLayer** chứa các module, mỗi module được định nghĩa với một class loader phù hợp.

Những điều cần phân biệt:

1. **Module path** là đầu vào để resolver tìm module artifact; nó không tự là “parent của class loader”.
2. **Module graph** cho biết modules nào đã được resolve và quan hệ đọc/export tương ứng.
3. **Class loader** thực sự định nghĩa class provider và service interface; cùng binary name nhưng khác defining loader là hai runtime types khác nhau.
4. **ModuleLayer** là một cấu trúc runtime chứa resolved modules (có thể được tạo thêm cho plugin).
5. **ServiceLoader** có các overload tra provider theo class loader hoặc theo module layer.

Snippet nếu đã có layer và service type tương ứng:

```java
ServiceLoader<GreetingService> providers =
        ServiceLoader.load(pluginLayer, GreetingService.class);
```

Đây là **snippet**, không phải chương trình độc lập: `pluginLayer` cần được tạo đúng và caller named module cần có quyền sử dụng service theo hợp đồng. Overload theo `ModuleLayer` có thể tìm provider trong layer chỉ định và các layer cha theo API; nó không tự tạo layer từ tên module.

### Một ràng buộc dễ bỏ sót: SPI identity

Nếu host dùng `learning.services.api.GreetingService` định nghĩa ở loader H, nhưng một plugin loader khác **tự định nghĩa lại** class/interface cùng tên, provider thực hiện “bản SPI” của plugin sẽ không được xem là implementation của đúng service type host đang truyền cho `ServiceLoader`.

Vì vậy với kiến trúc plugin:

- Đưa API/SPI dùng chung vào một module/loader chung.
- Để provider modules đọc service API đó thay vì đóng gói bản sao.
- Kiểm tra module layer và defining loader của service interface/provider.
- Tránh cấu hình child-first nhân đôi SPI dùng chung.
- Khi thấy `ServiceConfigurationError: not a subtype` hoặc class-cast kỳ lạ, kiểm tra runtime type identity chứ không chỉ tên package.

## 10. So sánh `module-info.java` với `META-INF/services` trên classpath

Trước JPMS và hiện nay khi triển khai trên classpath, provider có thể được công bố bằng resource text:

```text
META-INF/services/learning.services.api.GreetingService
```

Nội dung file ví dụ:

```text
learning.services.en.internal.EnglishGreeting
learning.services.vi.internal.VietnameseGreeting
```

Trên **classpath**, đây là danh sách tên class provider. Người viết library phải đặt file resource trong JAR/classes đúng chỗ, public provider classes phải có constructor phù hợp theo cơ chế provider cũ, và loader phù hợp phải tìm được resource qua `getResources`.

Trong **explicit named modules**, mô hình chuẩn là **`provides ... with ...` trong module descriptor**. Việc lấy provider từ named module do thông tin module descriptor và module resolution quyết định; không cần đặt `META-INF/services` để đăng ký chính provider đó theo mẫu ví dụ.

| Chủ đề | Explicit named module | Unnamed module / classpath truyền thống |
| --- | --- | --- |
| Consumer declaration | `uses ServiceType;` trong `module-info.java` | Không có `module-info.java`; gọi API ServiceLoader phù hợp |
| Provider registration | `provides ServiceType with Provider;` | `META-INF/services/<fully.qualified.Service>` |
| API visibility | `requires` + `exports` khi thích hợp | Classpath/type visibility và defining loader |
| Provider package export | Không phải export chỉ để ServiceLoader instantiate provider | Không có export kiểu JPMS từ unnamed module |
| Provider factory `provider()` | Hỗ trợ theo quy tắc explicit named module | Không áp dụng như kiểu đăng ký file cũ |
| Discovery boundary | Resolved modules, module layers, class loaders | Resource lookup/class loaders |
| Khi trùng provider | Phụ thuộc định danh provider trong descriptor/resolution | Phụ thuộc cấu hình nhiều JAR/resource; ServiceLoader xử lý quy tắc duplicate hợp lệ |

Nếu chuyển library từ classpath lên module path, không nên giả định file descriptor cũ là đủ cho **explicit modular JAR**. Hãy kiểm tra descriptor, cách khai báo service và launcher/module graph thực tế.

### Automatic modules

Một JAR không có `module-info.class` có thể được xem là **automatic module** khi đặt trên module path. Automatic module có các quy tắc suy diễn metadata và provider service khác explicit named module; có thể lấy service-provider declarations từ `META-INF/services` theo JPMS. Đừng gọi mọi JAR trên module path là explicit module hoặc mặc nhiên áp dụng hỗ trợ provider factory method cho automatic module.

## 11. Nếu thiếu `uses` hoặc `provides` sẽ xảy ra gì?

### Trường hợp 1: Consumer quên `uses`

Module consumer vẫn có thể `requires` API và compile mã dùng `GreetingService`. Nhưng lúc gọi `ServiceLoader.load(GreetingService.class)` từ named module không khai báo `uses GreetingService`, Java ServiceLoader có thể ném `ServiceConfigurationError` vì caller module không khai báo nhu cầu dùng service này.

**Khắc phục:** thêm `uses learning.services.api.GreetingService;` vào descriptor consumer. Không chữa lỗi bằng cách export package provider implementation.

### Trường hợp 2: Provider quên `provides`

Module provider có một class implements `GreetingService` nhưng descriptor không công bố provider. Với cơ chế explicit named module, `ServiceLoader` **không tự quét mọi class** để đoán class nào implements interface.

**Khắc phục:** đăng ký `provides ... with ...` trong module provider, bảo đảm class/provider method đúng hợp đồng, module có mặt trong resolved graph.

### Trường hợp 3: Provider module không được resolve

JAR module nằm trong thư mục `mods` nhưng không tham gia module layer được truy vấn. Consumer không thấy provider ấy qua ServiceLoader dùng layer/class loader đó.

**Khắc phục:** xem module graph bằng `--show-module-resolution`; đưa provider vào resolution bằng `--add-modules`, service binding phù hợp hoặc tạo plugin layer có provider modules theo kiến trúc.

### Trường hợp 4: Interface API bị định nghĩa hai lần

Host và plugin đóng gói hai định nghĩa `GreetingService` có cùng binary name nhưng khác defining loader. Một provider implements SPI của plugin có thể không phải subtype của SPI host.

**Khắc phục:** thống nhất service API về module/loader chia sẻ, tránh shadowing API ở plugin.

### Trường hợp 5: Constructor/provider factory sai

Một provider class không đúng tính hợp lệ, thiếu public no-arg constructor (trong trường hợp constructor-based), provider() method sai chữ ký hoặc constructor ném lỗi.

**Khắc phục:** đọc `ServiceConfigurationError` cùng cause, kiểm tra chữ ký và module descriptor; không bắt hết mọi exception rồi âm thầm bỏ qua provider không đáng tin.

## 12. Các lỗi thường gặp và chẩn đoán

| Hiện tượng | Khả năng cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| `ServiceConfigurationError: module ... does not declare uses` | Consumer named module thiếu uses | Thêm uses vào module-info của module gọi |
| Không tìm thấy provider, vòng lặp rỗng | Provider module không resolved, chưa có provides hoặc tìm sai layer/loader | Kiểm tra module graph, provides, `--add-modules` |
| `ServiceConfigurationError: Provider ... not found` | Tên provider sai, provider class/dependency không load được | Binary name, JAR/module path, class loader |
| `ServiceConfigurationError: ... not a subtype` | Provider không thực hiện đúng service type, có thể do duplicate SPI | So sánh defining loader của SPI/provider |
| Lỗi constructor/provider method | Cấu hình provider class/factory không hợp lệ hoặc ném exception | Public constructor, factory method, cause |
| `IllegalAccessError` / `InaccessibleObjectException` | Module access, exports/opens hoặc reflection chưa hợp lệ | Xem module descriptors/caller module |
| `NoClassDefFoundError` | Provider class tham chiếu dependency vắng mặt hoặc initializer trước đó lỗi | Đọc cause và runtime dependencies |
| `NoSuchMethodError` | Compile/runtime API thư viện khác phiên bản | Kiểm tra defining loader, JAR, method descriptor |
| `ClassCastException` | Provider đưa qua ranh giới loại SPI bị nhân đôi | Thống nhất định nghĩa API interface |
| Provider thấy khi chạy IDE nhưng mất ở JAR | Module graph/packaging khác, quên descriptor hoặc services resource | Kiểm tra artifact thực tế và lệnh khởi động |
| Reload không phát hiện phiên bản class mới | ServiceLoader cache hay loader/module layer cũ giữ classes | Xem lifecycle, `reload()` và layer/class loader mới |

### Kiểm tra artifact thực tế

Nếu đã đóng gói module JAR (file JAR thực sự tồn tại):

```bash
jar --describe-module --file provider.jar
jar tf provider.jar
```

`jar --describe-module` giúp thấy descriptor với provides/exports/requires, còn `jar tf` liệt kê entry thực. Tên `provider.jar` ở đây là ví dụ, không phải file sinh bởi lệnh demo chưa có bước đóng gói.

Dùng:

```bash
java --show-module-resolution --module-path mods --add-modules learning.services.en,learning.services.vi --module learning.services.app/learning.services.app.Main
```

để kiểm tra modules nào tham gia resolution. Không dựa hoàn toàn vào log `ServiceLoader`: lỗi provider có thể nằm trong class loading/linking hoặc constructor lúc iteration.

### Quan sát type identity

Nếu có đối tượng `GreetingService provider`:

```java
System.out.println(GreetingService.class.getClassLoader());
System.out.println(provider.getClass().getClassLoader());
System.out.println(provider.getClass().getModule());
```

Đây là snippet, không phải chương trình độc lập. Service interface và provider **không bắt buộc cùng defining loader**, nhưng provider phải thực sự implements **định nghĩa SPI mà consumer đang dùng**. Khi phụ thuộc vào nhiều plugin loaders, kiểm tra interface identity theo `Class<?>` thực tế.

## 13. Hiệu năng, thread-safety và vòng đời

`ServiceLoader` có cache provider theo instance để tránh một số thao tác khám phá/khởi tạo lại. Tuy nhiên, **bản thân đối tượng `ServiceLoader` không an toàn khi được sử dụng đồng thời bởi nhiều thread nếu không có đồng bộ phù hợp**. Điều này khác với việc các provider implementations có thể hoặc không thể thread-safe tùy hợp đồng riêng.

Những lưu ý vận hành:

- **Không tự suy thứ tự provider:** nếu cần chọn một provider theo vùng/độ ưu tiên, dùng metadata hoặc policy riêng; trong ví dụ mã `code()` được sắp xếp để in kết quả.
- **Xử lý provider errors có chủ đích:** một provider lỗi có thể khiến iteration thất bại, không nên im lặng che giấu.
- **Đóng tài nguyên của provider:** ServiceLoader không thay thế trách nhiệm đóng kết nối, thread, stream của implementation. Nếu provider là AutoCloseable thì ứng dụng/framework phải quản lý lifecycle phù hợp.
- **Cẩn thận với TCCL trong thread pool:** nếu tạm đặt context loader để tra plugin, khôi phục bằng `finally` sau đó.
- **`reload()` không tự thay class identity:** cùng class loader/module layer cũ có thể vẫn dùng cùng runtime class đã định nghĩa.
- **Không coi modular service provider là sandbox:** code provider vẫn chạy với quyền của tiến trình theo mô hình bảo mật thực tế; phải xác minh nguồn plugin và cân nhắc cô lập tiến trình cho code không tin cậy.

### Một kịch bản lựa chọn provider có kiểm soát

Ví dụ khi ứng dụng cần ưu tiên service code `vi`:

```java
GreetingService chosen = null;
for (GreetingService item : ServiceLoader.load(GreetingService.class)) {
    if ("vi".equals(item.code())) {
        chosen = item;
        break;
    }
}
if (chosen == null) {
    throw new IllegalStateException("Vietnamese provider unavailable");
}
System.out.println(chosen.greet("Java"));
```

Đây là **snippet** bên trong consumer module đã khai báo `uses`. Output khi có provider tiếng Việt như demo:

```text
Xin chào, Java
```

Không dùng `iterator().next()` mà không kiểm tra khi thứ tự provider có thể thay đổi giữa môi trường triển khai.

## 14. Những hiểu nhầm phổ biến

**“`requires` module provider đã đủ để ServiceLoader nhận ra dịch vụ.”** Sai. Consumer phải khai báo `uses` phù hợp, provider phải công bố `provides` hoặc dùng cơ chế đăng ký khác theo loại module.

**“Module có `provides` tức provider constructor đã chạy lúc startup.”** Sai. Discovery/khởi tạo provider thường lazy khi API được sử dụng; descriptor chỉ công bố đăng ký.

**“Provider package phải `exports` để ServiceLoader gọi constructor.”** Không. Provider được khai báo đúng trong explicit module có thể ở package không export; consumer chỉ cần service API public phù hợp.

**“ServiceLoader tự quét mọi class implements interface trên module path.”** Không. Nó dùng cấu hình dịch vụ/module metadata/classpath descriptor đúng hợp đồng, không classpath-scan tùy ý.

**“Hai provider phải ở cùng module.”** Không. Một service có thể do nhiều module riêng biệt cung cấp.

**“ServiceLoader sẽ luôn ưu tiên provider đứng đầu theo alphabet.”** Không. Thứ tự discovery không phải chính sách ưu tiên; ứng dụng nên chọn có chủ đích.

**“Có provider JAR trên đĩa nghĩa là chắc chắn module đã được resolve.”** Không. Cần kiểm tra module layer/graph và cách loader được gọi.

**“`reload()` tự cập nhật class mới trong loader cũ.”** Không. Nó xử lý cache provider của ServiceLoader; không tự định nghĩa lại class cùng binary name trong cùng loader.

**“`META-INF/services` và `provides` phải luôn viết cả hai mới chạy trên module path.”** Không. Explicit named module có đăng ký `provides` chuẩn; classpath/automatic module có hình thức khác.

**“Dùng `ServiceLoader` thì provider luôn an toàn vì JVM kiểm tra bytecode.”** Sai. Bytecode hợp lệ vẫn có thể gây tác động theo quyền runtime của tiến trình.

## 15. Tự kiểm tra kiến thức

**Câu 1.** `uses` đặt ở module nào? Có bắt buộc đưa vào provider module không?

**Đáp án:** `uses ServiceType` thuộc module **gọi ServiceLoader để tiêu thụ service**. Provider module dùng `provides ServiceType with Provider;`. Một module có thể vừa dùng vừa cung cấp các dịch vụ khác nhau, nhưng không phải mọi provider tự nhiên phải có `uses`.

**Câu 2.** `exports learning.services.api;` có thay cho `uses GreetingService;` không?

**Đáp án:** Không. `exports` là khả năng truy cập kiểu ở package; `uses` công bố nhu cầu tìm provider theo hợp đồng ServiceLoader của named module.

**Câu 3.** Vì sao consumer demo không import `EnglishGreeting` nhưng vẫn nhận được instance?

**Đáp án:** Provider được đăng ký bằng `provides ... with ...`, module có mặt trong graph và ServiceLoader khám phá/khởi tạo theo service API. Consumer chỉ phụ thuộc SPI.

**Câu 4.** Nếu bỏ `provides` khỏi `learning.services.vi` mà vẫn giữ class `VietnameseGreeting` trong JAR, có chắc ServiceLoader nhận được provider này không?

**Đáp án:** Không theo cơ chế explicit named module đã trình bày; chỉ có class implements SPI chưa đủ để được tự động đăng ký.

**Câu 5.** `--add-modules learning.services.en,learning.services.vi` trong lệnh demo giải quyết điều gì?

**Đáp án:** Đưa cả hai provider modules vào root set của resolution; đó là cách kiểm soát chúng xuất hiện trong runtime module graph để ServiceLoader có thể tìm được.

**Câu 6.** Vì sao output demo dùng `TreeMap`?

**Đáp án:** Vì ServiceLoader không phải API đảm bảo sắp xếp theo mã provider; TreeMap giúp hiển thị theo khóa `en`, `vi` ổn định và tránh kết luận sai về thứ tự discovery.

**Câu 7.** Có thể đăng ký provider class trong package `internal` không export không?

**Đáp án:** Có, với explicit named module và khai báo/provider hợp lệ. ServiceLoader được thiết kế để khám phá provider không buộc consumer phụ thuộc implementation package.

**Câu 8.** `ServiceLoader.reload()` có thay bytecode class cũ bằng một bản mới trong cùng ClassLoader không?

**Đáp án:** Không. `reload()` xóa cache ServiceLoader, không thay thế định nghĩa runtime class đã được JVM nạp.

**Câu 9.** Một provider module ở module path nhưng ServiceLoader trả rỗng. Cần kiểm tra gì?

**Đáp án:** Provider descriptor có `provides` chưa, module có được resolve vào layer/graph đang dùng chưa, consumer có `uses` chưa, service API có cùng runtime identity không, và phương thức ServiceLoader được gọi dùng loader/layer nào.

**Câu 10.** Khi nào cân nhắc provider factory `provider()`?

**Đáp án:** Khi explicit named module cần một factory để tạo service instance theo hợp đồng mà không muốn provider class trực tiếp implements SPI với public no-arg constructor; phải tuân thủ chữ ký phương thức và kiểm tra bằng compiler/runtime.

## 16. Checklist triển khai

- [ ] Thiết kế service interface hoặc abstract class ổn định trong module API riêng.
- [ ] Module API `exports` package chứa service type cho module cần dùng.
- [ ] Consumer `requires` API khi cần và khai báo `uses ServiceType;`.
- [ ] Provider `requires` API và đăng ký đúng `provides ServiceType with Provider;`.
- [ ] Provider class có constructor hoặc factory method hợp lệ; các phụ thuộc runtime hiện diện.
- [ ] Không export implementation package chỉ vì muốn ServiceLoader truy cập.
- [ ] Module provider được resolve vào đúng runtime graph/layer.
- [ ] Không đóng gói trùng service API type trong nhiều loader.
- [ ] Dùng `ServiceLoader` thích hợp; xử lý provider errors và thứ tự có chủ đích.
- [ ] Nếu cần reload plugin, quản lý module layer/ClassLoader và vòng đời provider rõ ràng.
- [ ] Với classpath, kiểm tra `META-INF/services/<service binary name>` đúng đường và tên provider.
- [ ] Test bằng artifact, module path và lệnh khởi động sát môi trường triển khai.

## 17. Kết luận và nguồn tham khảo

Trong JPMS, `uses` và `provides` là **hai phía của một hợp đồng nạp dịch vụ**. Consumer khai báo loại service sẽ sử dụng; provider module công bố lớp cung cấp; `ServiceLoader` tìm provider từ các module/resource phù hợp theo graph và loader thực tế. `requires` và `exports` vẫn cần thiết cho type accessibility nhưng **không phải cơ chế đăng ký provider**.

Cách xây dựng bền vững là đặt SPI chung ở module/loader được chia sẻ, giữ triển khai trong provider module không cần export, kiểm tra provider được resolved, sử dụng ServiceLoader với chính sách chọn provider rõ ràng và không phụ thuộc vào thứ tự khám phá không được bảo đảm.

### Tài liệu chuẩn

- Java SE 17 `ServiceLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- JLS 17 §7.7.1, *Module Directives* (`requires`, `exports`, `uses`, `provides`): https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7.1
- Java SE 17 `java` launcher, module path/add-modules/show-module-resolution: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Java SE 17 `javac`, module-source-path: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html
- Java SE 17 `ModuleLayer` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html
- Java SE 17 `ClassLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-loading-004">ServiceLoader.load với loader tường minh hoặc loader ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# `ServiceLoader.load` với loader tường minh hoặc loader ngữ cảnh

> **Java Core → ClassLoader → Nạp dịch vụ.** Bài học giải thích sự khác nhau giữa `ServiceLoader.load(Service.class)` (dùng **thread context class loader – TCCL**) và `ServiceLoader.load(Service.class, chosenLoader)` (dùng **loader tường minh**), cách đóng gói providers trong JAR, tác động của class-loader delegation, cơ chế tìm providers trong unnamed/named modules, và quản lý cache/vòng đời an toàn. Thực hành chủ đạo dùng **Java 17+**; các API module/stream xuất hiện từ Java 9.

## 1. Câu trả lời trọng tâm

`java.util.ServiceLoader<S>` là API chuẩn để tìm **implementations (service providers)** của một interface/abstract service type `S`, mà consumer không cần hard-code tên từng class triển khai.

Có hai cách gọi thường gặp:

~~~java
// (1) Dựa vào TCCL của thread hiện tại.
ServiceLoader<MyService> viaContext =
        ServiceLoader.load(MyService.class);

// (2) Nêu rõ class loader dùng làm điểm bắt đầu tìm provider.
ClassLoader chosenLoader = obtainPluginLoader();
ServiceLoader<MyService> viaExplicit =
        ServiceLoader.load(MyService.class, chosenLoader);
~~~

Đây là **snippet minh họa**, chưa phải file Java độc lập; `MyService` và `obtainPluginLoader` do ứng dụng cung cấp.

**Quy tắc chính thức của Java SE:**

~~~java
ServiceLoader.load(service)
~~~

**tương đương với:**

~~~java
ServiceLoader.load(
        service,
        Thread.currentThread().getContextClassLoader())
~~~

Nghĩa là overload một tham số phụ thuộc **TCCL tại thời điểm tạo `ServiceLoader`**, trong khi overload hai tham số sử dụng **loader bạn truyền vào**. Đây không phải lựa chọn ngẫu nhiên theo package của service interface.

### Bảng phân biệt nhanh

| Cách gọi | Loader làm điểm bắt đầu khám phá providers | Thường dùng khi |
| --- | --- | --- |
| `ServiceLoader.load(S.class)` | TCCL của thread **đang gọi `load`** | Framework cần lựa chọn provider theo caller/application context |
| `ServiceLoader.load(S.class, pluginLoader)` | `pluginLoader` đã chọn | Plugin host/container muốn cách tìm có kiểm soát, không lệ thuộc thread hiện tại |
| `ServiceLoader.load(S.class, null)` | **System class loader**, hoặc bootstrap nếu không có system loader | Cần fallback công khai theo Java SE API; `null` không mang nghĩa “không tìm gì” |
| `ServiceLoader.loadInstalled(S.class)` | **Platform class loader** trên Java 9+ | Chỉ cần installed/platform providers; thường **không** thấy application classpath/module-path providers |
| `ServiceLoader.load(layer, S.class)` | **ModuleLayer** chỉ định cùng các parent layers | Plugin vận hành qua JPMS module layers; chỉ tìm named-module providers |

**TCCL khác defining loader.** Loader định nghĩa `S.class`, loader định nghĩa provider implementation và TCCL có thể là những đối tượng khác nhau. Kết quả chỉ hợp lệ khi provider **assignable** tới đúng runtime `S.class` mà consumer đưa vào.

## 2. Vì sao ServiceLoader cần context class loader?

Giả sử một thư viện nền tảng được classloader cha nạp, còn một ứng dụng/plugin có classloader con:

~~~text
               Platform / parent / host loader
                   |
                   +-- api.PaymentService
                   +-- framework.Discovery
                   |
                   v
                Plugin loader
                   |
                   +-- impl.CardPayment
                   +-- META-INF/services/api.PaymentService

Framework ở parent muốn tìm implementation ở child:
  Chỉ dùng defining loader của Framework => có thể không thấy plugin
  Chọn TCCL = plugin loader => có thể tìm provider
  Truyền plugin loader rõ ràng => không cần thay TCCL
~~~

Parent-first delegation thông thường chỉ cho **child hỏi parent**, chứ không làm parent tự động tìm mọi class/resource của child. ServiceLoader dùng TCCL trong convenience overload để framework do parent nạp vẫn có một **điểm nhìn** vào providers được triển khai trong child/application loader.

Điều đó **không** làm TCCL trở thành parent của mọi class hay thay JVM class resolution của bytecode:

- `Framework.class.getClassLoader()` vẫn là loader **định nghĩa** Framework.
- `PaymentService.class` phải là shared contract đúng runtime identity.
- TCCL là một **tham chiếu trên đối tượng Thread**, có thể thay qua `getContextClassLoader`/`setContextClassLoader`.
- Chỉ những API **chủ động dùng TCCL** (như overload `ServiceLoader.load(S.class)`) mới bị ảnh hưởng trực tiếp.
- Với explicit loader, người gọi có thể chọn plugin loader mà **không cần thay** TCCL của worker thread.

### 2.1. `ServiceLoader.load(S.class)` lấy TCCL vào lúc nào?

**Lúc bạn gọi `ServiceLoader.load(S.class)` để tạo đối tượng loader**, không phải liên tục hỏi lại TCCL trước mỗi `iterator().next()`. Provider discovery/instantiation lại diễn ra **lazily**, nên thời điểm **chọn loader** và thời điểm **tạo provider object** không nhất thiết trùng nhau.

Ví dụ ý niệm:

~~~java
Thread thread = Thread.currentThread();
ClassLoader saved = thread.getContextClassLoader();

ServiceLoader<MyService> serviceLoader;
try {
    thread.setContextClassLoader(pluginLoader);
    serviceLoader = ServiceLoader.load(MyService.class);
} finally {
    thread.setContextClassLoader(saved);
}

// Sau khi restore TCCL, serviceLoader vẫn dùng loader đã chọn
// ở lúc ServiceLoader.load(...) được gọi.
for (MyService provider : serviceLoader) {
    use(provider);
}
~~~

Đây là snippet trong ứng dụng có `MyService`, `pluginLoader` và `use` hợp lệ. Việc đổi TCCL của thread **sau** khi tạo đối tượng `ServiceLoader` không chuyển đối tượng đó sang loader khác.

**Không suy luận rằng loader đã chọn chắc nhìn thấy provider.** Classpath, parent delegation, `META-INF/services`, module graph và class identity vẫn phải phù hợp.

## 3. Nạp provider như thế nào? Classpath và module path

Một service có thể có **0, 1 hoặc nhiều providers**. Provider không nhất thiết được tạo ngay khi bạn gọi factory `load`; người gọi duyệt qua `iterator()`, `stream()` hoặc `findFirst()` để thực sự khám phá/khởi tạo theo API.

### 3.1. Provider ở classpath/unnamed module

Với interface:

~~~java
package demo.spi;

public interface GreetingService {
    String greet();
}
~~~

JAR cung cấp provider cần:

1. Có class triển khai, ví dụ `demo.plugin.EnglishGreeter implements demo.spi.GreetingService`.
2. Có file cấu hình đúng tên resource:

~~~text
META-INF/services/demo.spi.GreetingService
~~~

3. Nội dung file mã hóa UTF-8 có **binary name** của implementation:

~~~text
demo.plugin.EnglishGreeter
~~~

4. Resource và implementation đều **visible** qua loader mà `ServiceLoader` dùng để tìm. Provider class trong unnamed module thường cần **public class** có **public no-argument constructor** để ServiceLoader tạo instance.

File cấu hình có thể chứa nhiều tên provider, mỗi dòng một tên. Dòng rỗng và comment `#` có cách xử lý theo đặc tả; các tên phải đúng binary names. Một tên sai, một provider không implement interface đúng runtime identity, hoặc constructor sai có thể gây `ServiceConfigurationError`.

**Đừng nhầm** `META-INF/services` với một API kiểm tra folder hệ điều hành. ServiceLoader tìm **resources thông qua classloader** (thường là `getResources("META-INF/services/"+serviceName)` với providers trong unnamed modules), nên việc resource được đóng gói vào JAR đúng đường quan trọng hơn vị trí thư mục làm việc.

### 3.2. Provider ở named modules (JPMS)

Từ Java 9, provider có thể khai báo bằng `module-info.java`:

~~~java
module demo.provider {
    requires demo.api;
    provides demo.spi.GreetingService
        with demo.provider.EnglishGreeter;
}
~~~

Consumer module (ví dụ `demo.consumer`) phải khai báo:

~~~java
module demo.consumer {
    requires demo.api;
    uses demo.spi.GreetingService;
}
~~~

API `demo.api` export package `demo.spi`:

~~~java
module demo.api {
    exports demo.spi;
}
~~~

Đây là **ba module descriptors minh họa**, không phải một file Java duy nhất. Với caller nằm trong **explicit named module**, `uses` có ý nghĩa bắt buộc theo ServiceLoader contract; thiếu `uses` có thể gây `ServiceConfigurationError` khi gọi `load`. `requires` bảo đảm đọc module sở hữu service type nếu service nằm ở module khác.

**Không nhầm:**

- `requires`: module dependency/readability.
- `uses`: khai báo caller tiêu thụ service.
- `provides ... with ...`: provider module công bố implementation.
- `exports`: cho public package API được truy cập; provider implementation class không nhất thiết phải được export như một API công khai thông thường.
- Module phải nằm trong **resolved graph/layer** phù hợp; chỉ đặt JAR trên module path không đồng nghĩa module đó đang hiện diện trong graph.

Khi dùng overload `load(S.class, loader)` trên JDK 9+, ServiceLoader có thể tìm providers trong **named modules** liên quan đến loader/module layer trước khi đọc provider-configuration files trong **unnamed modules**. Vì thế explicit loader **không có nghĩa “chỉ quét JAR URLs của loader này”**.

## 4. Thực hành hoàn chỉnh: explicit loader và TCCL cho plugin JAR

Bài lab này cố ý tách **host API/application** khỏi **provider JAR**. Chúng ta sẽ chứng minh rằng:

- Host loader không nhìn thấy provider vì provider không nằm trên host classpath.
- Truyền `pluginLoader` tường minh thì thấy provider.
- Gọi `load(S.class)` khi TCCL là host thì không thấy.
- Tạo `ServiceLoader` khi TCCL là plugin, **sau đó khôi phục TCCL trước khi duyệt** thì vẫn thấy provider.

### 4.1. Cây thư mục

~~~text
service-loader-lab/
├── src/
│   ├── api/demo/spi/GreetingService.java
│   ├── plugin/demo/plugin/EnglishGreeter.java
│   └── host/demo/host/ServiceLookupDemo.java
├── config/
│   └── META-INF/services/demo.spi.GreetingService
└── build/                     (các lệnh bên dưới tạo)
    ├── api/
    ├── plugin/
    ├── host/
    └── provider.jar
~~~

Tệp **`src/api/demo/spi/GreetingService.java`**:

~~~java
package demo.spi;

public interface GreetingService {
    String greet();
}
~~~

Tệp **`src/plugin/demo/plugin/EnglishGreeter.java`**:

~~~java
package demo.plugin;

import demo.spi.GreetingService;

public final class EnglishGreeter implements GreetingService {
    public EnglishGreeter() { }

    @Override
    public String greet() {
        return "hello from plugin";
    }
}
~~~

Tệp cấu hình **`config/META-INF/services/demo.spi.GreetingService`** có **chính xác một dòng**:

~~~text
demo.plugin.EnglishGreeter
~~~

Tệp **`src/host/demo/host/ServiceLookupDemo.java`**:

~~~java
package demo.host;

import demo.spi.GreetingService;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class ServiceLookupDemo {
    private static String values(
            ServiceLoader<GreetingService> services) {
        List<String> result = new ArrayList<>();
        for (GreetingService service : services) {
            result.add(service.greet());
        }
        return result.toString();
    }

    public static void main(String[] args) throws Exception {
        ClassLoader hostLoader = GreetingService.class.getClassLoader();
        Thread caller = Thread.currentThread();
        ClassLoader originalTccl = caller.getContextClassLoader();
        URL jar = Path.of("build", "provider.jar")
                .toAbsolutePath().toUri().toURL();

        try (URLClassLoader pluginLoader = new URLClassLoader(
                new URL[] {jar}, hostLoader)) {

            // Kiểm soát TCCL của chương trình: không giả định môi trường.
            caller.setContextClassLoader(hostLoader);
            System.out.println("context-host="
                    + values(ServiceLoader.load(GreetingService.class)));
            System.out.println("explicit-host="
                    + values(ServiceLoader.load(
                            GreetingService.class, hostLoader)));

            System.out.println("explicit-plugin="
                    + values(ServiceLoader.load(
                            GreetingService.class, pluginLoader)));

            caller.setContextClassLoader(pluginLoader);
            ServiceLoader<GreetingService> captured =
                    ServiceLoader.load(GreetingService.class);

            // Restore TRƯỚC khi lặp qua ServiceLoader.
            caller.setContextClassLoader(hostLoader);
            System.out.println("captured-after-change="
                    + values(captured));
        } finally {
            caller.setContextClassLoader(originalTccl);
        }
    }
}
~~~

**Điểm an toàn của thiết kế:** `URLClassLoader` có parent là loader định nghĩa shared service interface, vì thế `EnglishGreeter` implement **chính cùng `demo.spi.GreetingService`** mà host truyền vào `ServiceLoader.load`. Plugin JAR **không được** chứa một bản `demo/spi/GreetingService.class` được child loader tự define lại.

### 4.2. Biên dịch và đóng JAR (Linux/macOS, POSIX shell)

Từ thư mục `service-loader-lab`:

~~~bash
mkdir -p build/api build/plugin build/host \
    build/plugin/META-INF/services

# 1. Service interface được biên dịch vào host/shared API.
javac --release 17 -d build/api \
    src/api/demo/spi/GreetingService.java

# 2. Plugin biên dịch với API của host.
javac --release 17 -cp build/api -d build/plugin \
    src/plugin/demo/plugin/EnglishGreeter.java

# 3. Copy provider configuration vào đúng vị trí trong JAR.
cp config/META-INF/services/demo.spi.GreetingService \
    build/plugin/META-INF/services/demo.spi.GreetingService

# 4. Gói CHỈ plugin implementation và metadata, không gói API.
jar --create --file build/provider.jar -C build/plugin .

# 5. Host main class dùng API, không có plugin JAR ở classpath.
javac --release 17 -cp build/api -d build/host \
    src/host/demo/host/ServiceLookupDemo.java

java -cp "build/api:build/host" demo.host.ServiceLookupDemo
~~~

Trên Windows, dùng lệnh tạo thư mục/copy phù hợp PowerShell và đổi dấu phân tách classpath thành **`;`**:

~~~powershell
java -cp "build/api;build/host" demo.host.ServiceLookupDemo
~~~

Khi tự lập thư mục/file bằng IDE, đảm bảo đường `META-INF/services/demo.spi.GreetingService` trong JAR đúng cả chữ hoa/thường; `jar --list --file build/provider.jar` có thể xác minh.

### 4.3. Stdout kỳ vọng (Java 17+ trong cấu hình lab)

~~~text
context-host=[]
explicit-host=[]
explicit-plugin=[hello from plugin]
captured-after-change=[hello from plugin]
~~~

**Giải thích chính xác:**

1. `context-host=[]`: TCCL bị đặt thành host loader, nên `ServiceLoader.load(S.class)` chỉ thấy nguồn provider host có thể tìm. Bài lab không triển khai một provider nào trên host classpath.
2. `explicit-host=[]`: dù TCCL có là gì, overload hai tham số chọn **hostLoader** một cách tường minh.
3. `explicit-plugin=[hello from plugin]`: loader con có URL tới `provider.jar`, tìm được `META-INF/services` và định nghĩa `EnglishGreeter`; interface được parent định nghĩa.
4. `captured-after-change=[hello from plugin]`: `ServiceLoader` được **tạo khi TCCL là plugin loader**, rồi caller khôi phục TCCL về host **trước khi iterator bắt đầu**. ServiceLoader vẫn giữ loader đã chọn ban đầu.
5. `try-with-resources` đóng `pluginLoader` sau khi đã duyệt xong providers; `finally` khôi phục TCCL bất kể việc khám phá providers thành công hay ném lỗi.

Đây là output của **thí nghiệm được cấu hình**, không phải một lời hứa cho mọi ứng dụng vốn có providers bổ sung trong host runtime. Nếu host có provider khác cùng service, kết quả sẽ khác theo deployment thực tế.

### 4.4. Thử các thay đổi để hiểu cơ chế

- **Bỏ file `META-INF/services/...` khỏi JAR:** ServiceLoader dạng classpath sẽ không tự dò toàn bộ classes để tìm implementation; hai dòng sử dụng plugin thường trả `[]`.
- **Ghi nhầm FQCN trong file cấu hình:** khi duyệt provider có thể ném `ServiceConfigurationError` với nguyên nhân “provider ... not found”.
- **Đưa provider JAR lên host classpath:** host loader có thể tìm provider, khiến ranh giới kiểm thử “host trống, plugin có provider” không còn đúng.
- **Đổi parent plugin loader thành platform loader mà không cung cấp shared API:** provider có thể không tìm thấy `demo.spi.GreetingService`, hoặc type identity không khớp nếu plugin tự mang bản API khác.
- **Đổi TCCL sau khi gọi `ServiceLoader.load(S.class)`:** không làm đối tượng `ServiceLoader` đang giữ chuyển sang loader mới.

## 5. Explicit loader: chủ động chọn context thay vì phụ thuộc thread

Trong plugin manager, lựa chọn tường minh thường an toàn và rõ ràng:

~~~java
ServiceLoader<PluginService> providers =
        ServiceLoader.load(PluginService.class, pluginLoader);

for (PluginService provider : providers) {
    register(provider);
}
~~~

Snippet này cần `PluginService`, `pluginLoader`, `register` trong project. Ưu điểm là API thể hiện **ý định lựa chọn loader** ngay tại call site; executor worker có TCCL host hay plugin khác không làm thay đổi loader được truyền vào.

Tuy nhiên explicit loader vẫn **không phải “bỏ qua delegation”**:

- Với `URLClassLoader` mặc định, tìm resources/classes thường phụ thuộc **parent-first behavior**.
- `ServiceLoader.load(S.class, pluginLoader)` trên JDK 9+ có thể khám phá **named-module providers** liên quan đến module layer, không chỉ đọc cấu hình trong `pluginLoader.getURLs()`.
- Service contract phải accessible và có đúng class identity; `S.class` do host đưa vào không được tự thay bởi bản `S` plugin định nghĩa.
- ServiceLoader mới cũng có **provider cache riêng**. Việc đổi một biến `ClassLoader` ở bên ngoài không biến đổi các ServiceLoader đã tạo trước đó.

Khi host có các plugins thuộc nhiều tenants, hãy gắn service discovery với **plugin context/loader cụ thể**, không dựa vào TCCL tình cờ của thread gọi.

### 5.1. Khi đối số `loader` là null

Theo Javadoc của `load(Class<S>, ClassLoader)`:

~~~java
ServiceLoader<MyService> viaSystem =
        ServiceLoader.load(MyService.class, null);
~~~

Nếu loader là null, API dùng **system class loader** (hoặc bootstrap nếu system loader không có). Nó **không** có nghĩa “dùng bootstrap ngay lập tức”, “không tìm providers” hoặc “dùng TCCL lúc đó”.

Phân biệt hai tình huống:

~~~java
ServiceLoader.load(MyService.class, null);
ServiceLoader.load(MyService.class);
~~~

Lệnh đầu chọn system/bootstrap fallback. Lệnh thứ hai đọc TCCL; nếu TCCL cũng null, fallback của overload hai tham số dẫn đến system/bootstrap như trên. Nếu TCCL là plugin loader, hai lệnh **có thể cho kết quả khác nhau**.

### 5.2. `loadInstalled` không thay cho plugin discovery

~~~java
ServiceLoader<MyService> installed =
        ServiceLoader.loadInstalled(MyService.class);
~~~

Trên Java 9+, API này dùng **platform class loader**, thường không thấy provider deployment trên application classpath/module path. Nó không phải lựa chọn chung để tìm plugin JAR do ứng dụng phát hành. Đừng lấy tên “installed” để suy ra Java vẫn có `lib/ext` extension mechanism như Java 8.

## 6. Khi nào nên chủ động thay TCCL?

Có frameworks hoặc libraries chỉ gọi overload một tham số mà **không cho bạn truyền loader**. Khi đó có thể đặt TCCL xung quanh một operation:

~~~java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    framework.discoverAndRun();
} finally {
    thread.setContextClassLoader(previous);
}
~~~

Đây là **snippet**, cần `framework` và `pluginLoader` trong scope. `try/finally` quan trọng vì:

- Phương thức framework có thể ném exception.
- Worker của một `ExecutorService` thường được **tái sử dụng**. Nếu không restore, task tiếp theo có thể tìm nhầm providers của plugin trước.
- TCCL là một **strong reference** tới loader. Một thread tồn tại lâu (pool worker, scheduler) giữ plugin loader đã stop có thể ngăn class unloading và khiến hot reload giữ JARs/Metaspace lâu hơn.
- Việc set TCCL không tự truyền sang một worker đã tồn tại; lúc `framework.discoverAndRun()` chạy trên thread khác, thread ấy phải có TCCL đúng **tại lúc thực tế gọi `ServiceLoader.load`**.

Nếu đã có overload hai tham số và có thể gọi trực tiếp, **ưu tiên explicit loader** để giảm thay đổi thread context ngoài ý muốn.

### 6.1. TCCL, `new Thread` và thread pool

Trong cấu hình Thread tạo thông thường, child thread **kế thừa TCCL tại thời điểm thread object được tạo**, không phải khi `start()`. Thread pool thường có workers sinh ra trước và tái sử dụng; `executor.submit(runnable)` **không bảo đảm** worker tự lấy TCCL của submitter.

Ví dụ:

~~~java
ExecutorService executor = Executors.newSingleThreadExecutor();

// Đây chỉ là ví dụ ý tưởng:
Thread.currentThread().setContextClassLoader(pluginLoader);
executor.submit(() -> ServiceLoader.load(PluginService.class));
// Không có bảo đảm worker nhìn pluginLoader nếu worker đã tồn tại.
~~~

Đây là snippet minh họa, **không** phải phương án propagation chuẩn. Ngoài imports và variables, nó thiếu restore TCCL của caller và chưa duyệt providers. Một thiết kế đúng phải capture loader từ plugin context và **đặt vào worker ngay trong task**, hoặc truyền tường minh vào `ServiceLoader.load`.

### 6.2. Wrapper propagation cho executor task (Java 17+)

Tệp **`ServiceContextTasks.java`**:

~~~java
import java.util.concurrent.Callable;

public final class ServiceContextTasks {
    private ServiceContextTasks() { }

    public static <T> Callable<T> withTccl(
            ClassLoader desired,
            Callable<T> operation) {
        return () -> {
            Thread current = Thread.currentThread();
            ClassLoader previous = current.getContextClassLoader();
            try {
                current.setContextClassLoader(desired);
                return operation.call();
            } finally {
                current.setContextClassLoader(previous);
            }
        };
    }
}
~~~

~~~bash
javac --release 17 ServiceContextTasks.java
~~~

Class helper hợp lệ nhưng không có `main`. Cách gọi từ code có `ExecutorService executor` và `pluginLoader`:

~~~java
var future = executor.submit(
    ServiceContextTasks.withTccl(pluginLoader, () -> {
        var services = ServiceLoader.load(PluginService.class);
        return services.findFirst().orElse(null);
    })
);
PluginService service = future.get();
~~~

Snippet yêu cầu imports/biến/context ngoại vi, đồng thời cần quản lý lifecycle provider trả về. Khi method `ServiceLoader.load` được gọi trong worker, TCCL đã là `pluginLoader`. `finally` restore khi operation thành công hay thất bại. Trong multi-tenant app, truyền `pluginLoader` từ context do host quản lý, không copy một giá trị TCCL có thể đã bị task khác làm bẩn.

**Lưu ý:** `withTccl` chỉ áp dụng cho **operation đồng bộ bên trong callback**. Nếu callback spawn async tasks khác mà không truyền loader tiếp, chúng không được đảm bảo cùng context.

## 7. Lazy discovery, caching và `reload()`

Nhiều hiểu nhầm phát sinh vì `ServiceLoader.load` chỉ **tạo đối tượng loader**. Providers được tìm/khởi tạo khi bạn thực sự duyệt:

~~~java
ServiceLoader<MyService> services =
        ServiceLoader.load(MyService.class, chosenLoader);

// Tại đây chưa thể kết luận mọi provider đã được instantiate.
for (MyService provider : services) {
    System.out.println(provider);
}
~~~

- `iterator()` tìm và **instantiate** providers lazily; `hasNext`/`next` có thể ném `ServiceConfigurationError`.
- Providers đã được load vào **cache của chính `ServiceLoader`**; lần duyệt sau thường nhận các providers đã cache trước rồi tiếp tục tìm các provider chưa dùng.
- `findFirst()` (Java 9+) trả `Optional<S>` với provider đầu tiên, nếu có; không bảo đảm provider đầu tiên trong toàn hệ thống là ưu tiên ổn định trừ khi deployment/module rules cụ thể cho biết.
- `stream()` (Java 9+) trả `Stream<ServiceLoader.Provider<S>>`; `provider.type()` cho biết provider type và `provider.get()` tạo/lấy instance theo contract. Nó cho phép inspect/filter types mà chưa bắt buộc instantiate tất cả providers.
- `reload()` **xóa provider cache** để những lần duyệt sau tìm từ đầu; nó **không** biến `ServiceLoader` sang một classloader khác và **không** unload các runtime classes đã nạp.

### 7.1. Đừng hiểu `reload` là nạp lại JAR/class

~~~java
ServiceLoader<MyService> services =
        ServiceLoader.load(MyService.class, pluginLoader);
services.reload();
~~~

Kết quả không bảo đảm plugin bytecode trên disk sẽ được VM định nghĩa lại. Cùng một defining classloader không thể define cùng binary name nhiều lần; muốn hot reload implementation mới thường phải tạo **plugin loader thế hệ mới**, chuyển service routing, dừng và close generation cũ, đồng thời bỏ references.

Sau `reload()`, các iterators/streams đã tạo trước đó **không nên sử dụng tiếp**; API có cơ chế fail-fast (`ConcurrentModificationException`) nếu cache bị xóa trong khi đang duyệt.

### 7.2. `ServiceLoader` không thread-safe

Javadoc nêu rõ **instances of `ServiceLoader` are not safe for use by multiple concurrent threads**. Đừng đưa một instance ServiceLoader dùng chung cho nhiều worker vừa `iterator()` vừa `reload()` mà không có orchestration phù hợp.

Cách quản lý thường tốt hơn:

- Tạo ServiceLoader **theo plugin/request/context** khi cần discovery.
- Duyệt và chọn provider **ở một thread/giai đoạn quản lý**, sau đó publication các service instances theo đồng bộ phù hợp.
- Tách provider instance thread-safety khỏi thread-safety của `ServiceLoader`; provider có thể không thread-safe ngay cả khi discovery đã xong.
- Không cache `ServiceLoader.load(S.class)` trong **static singleton toàn JVM** cho nhiều ứng dụng/TCCL khác nhau; Javadoc cảnh báo sai context và memory leak. Nếu cần cache, khóa theo đúng **service type + loader/layer + lifecycle** và có cleanup khi plugin stop.

## 8. Xử lý lỗi `ServiceConfigurationError` chính xác

`ServiceConfigurationError` **extends `Error`**, không phải `Exception`. Lỗi có thể xảy ra khi gọi `load` (ví dụ named caller thiếu `uses`) hoặc muộn hơn khi `iterator`/`stream` thực sự khám phá provider. Những nguyên nhân thường gặp:

| Biểu hiện | Lý do có thể | Cách kiểm tra |
| --- | --- | --- |
| Không tìm thấy provider, danh sách `[]` | Config file/provider module không visible qua loader | Kiểm tra `META-INF/services` trong JAR, loader, graph |
| “Provider ... not found” | Tên provider trong config sai, thiếu JAR, class unavailable | Kiểm tra exact binary name + classpath/loader |
| “Provider ... not a subtype” | Provider implement một bản `S` khác defining loader | So sánh `S.class` identity từ host và plugin |
| Không khởi tạo được provider | Không có public no-arg constructor hợp lệ với classpath deployment hoặc constructor ném lỗi | Xem cause, visibility, constructor |
| Error khi named caller gọi `load` | Module thiếu `uses` hoặc service không accessible | Thêm `uses S`/`requires`/`exports` phù hợp |
| Provider discovery sai tenant/plugin | TCCL là loader của worker cũ | Dùng explicit loader hoặc wrapper `try/finally` |
| Provider được giữ lâu sau shutdown | Cache, host registry, TCCL, threads giữ strong reference | Close instance nếu cần, clear references, shutdown workers |
| Bị lặp provider kỳ lạ | Duplicated descriptors/classpath/module paths | Kiểm tra JAR entries, module provides, parent resources |
| `ConcurrentModificationException` khi duyệt | `reload()` xảy ra sau khi có iterator/stream | Dùng iterator mới, không truy cập ServiceLoader concurrent |

Mẫu handling ở boundary tin cậy:

~~~java
try {
    for (MyService provider : ServiceLoader.load(
            MyService.class, pluginLoader)) {
        register(provider);
    }
} catch (java.util.ServiceConfigurationError problem) {
    // Log đầy đủ provider metadata/cause và đưa plugin vào FAILED.
    throw problem;
}
~~~

Đây là snippet. Không bắt toàn bộ `Error`/`Throwable` rồi bỏ qua “provider hỏng” vô điều kiện; `ServiceConfigurationError` thường chỉ ra cấu hình deployment sai nghiêm trọng. API có thể thử tiếp sau một số lỗi, nhưng Java SE không bảo đảm recovery hoàn toàn. Phần vận hành cần quyết định policy fail-fast, quarantine hoặc degrade một cách rõ ràng.

### 8.1. Gỡ lỗi bằng chính loader đã chọn

Khi chỉ có classpath SPI:

~~~java
String resourceName =
        "META-INF/services/" + MyService.class.getName();

java.util.Enumeration<java.net.URL> urls =
        pluginLoader.getResources(resourceName);

while (urls.hasMoreElements()) {
    System.out.println(urls.nextElement());
}
~~~

Đây là snippet yêu cầu `MyService`/`pluginLoader` và method xử lý `IOException`. Nó giúp kiểm tra provider configuration visible qua loader, nhưng **không chứng minh provider class sẽ được load, constructor sẽ thành công hay module access hợp lệ**. Với named modules, module descriptors cung cấp providers nên chỉ tìm `META-INF/services` **không đầy đủ**.

Nếu định vị resource đúng nhưng provider vẫn không hoạt động, kiểm tra lớp do loader nào định nghĩa:

~~~java
System.out.println(MyService.class.getClassLoader());
System.out.println(provider.getClass().getClassLoader());
System.out.println(MyService.class.isInstance(provider));
~~~

Đây cũng là snippet (đã có biến `provider`), mục tiêu là phân biệt **visibility**, **type identity** và **instantiation failure**.

## 9. JPMS và `ModuleLayer`: khác với TCCL

Với module plugin được resolve trong layer riêng, API:

~~~java
ServiceLoader<MyService> fromLayer =
        ServiceLoader.load(pluginLayer, MyService.class);
~~~

tìm named-module providers trong **`pluginLayer` và các parent layers**, **không tìm unnamed-module providers** qua `META-INF/services`. Đây là lựa chọn thường phù hợp khi plugin architecture dùng JPMS ModuleLayer thay vì `URLClassLoader` với JARs trên classpath.

Ngược lại:

~~~java
ServiceLoader<MyService> fromLoader =
        ServiceLoader.load(MyService.class, someLoader);
~~~

có thể tìm **cả named và unnamed-module providers** theo ServiceLoader algorithm. Kể cả một classloader thuộc một layer, việc lookup named modules có thể liên quan **các module khác trong cùng layer**, không giới hạn module do chính loader đó define. Cần hiểu đây là **service discovery**, không chỉ `someLoader.loadClass("...")`.

### 9.1. Service provider triển khai bằng named module

Ví dụ module declarations:

~~~java
module demo.api {
    exports demo.spi;
}
~~~

~~~java
module demo.provider {
    requires demo.api;
    provides demo.spi.GreetingService
        with demo.provider.EnglishGreeter;
}
~~~

~~~java
module demo.consumer {
    requires demo.api;
    uses demo.spi.GreetingService;
}
~~~

Nếu compiler/launcher yêu cầu:

~~~bash
java --module-path mods --add-modules demo.provider \
     --module demo.consumer/demo.consumer.Main
~~~

`mods` phải chứa các modules đã biên dịch và `Main` cần có code chạy phù hợp; lệnh này là **mẫu launch**, không phải script độc lập tạo ba modules. `--add-modules` trong ví dụ giúp đưa provider module vào graph; còn việc ServiceLoader khám phá chính xác còn phụ thuộc layer/module graph và service declarations. Với classpath demo ở phần 4, **không được tự viết `module-info.java` vào provider JAR** mà vẫn kỳ vọng kết quả/packaging không đổi.

### 9.2. `uses` không thay thế `exports`

Consumer module phải đọc module của service type và khai báo dùng service; service provider module phải công bố providers. Đừng giải quyết `ServiceConfigurationError` bằng `--add-opens` khi vấn đề thật là thiếu `uses` hoặc chưa resolve module. `opens` chủ yếu liên quan deep reflection, không phải “bật” SPI provider discovery.

## 10. Vòng đời provider, cache và hot reload

Khi provider là một plugin có state/resources, ServiceLoader **không quản lý shutdown** cho provider. API discovery/instantiation không tự gọi `close()` khi host không còn dùng provider.

Với plugin quản lý rõ ràng:

~~~text
1. Create plugin ClassLoader / ModuleLayer
2. Discover providers bằng explicit loader hoặc layer
3. Validate shared interface identity và constructor/module access
4. Start service và publish vào registry
5. Quiesce: ngừng request mới
6. Drain/cancel async tasks; unregister callbacks/listeners
7. Close providers/resources thuộc plugin (nếu có)
8. Drop ServiceLoader/provider caches và strong references
9. Close URLClassLoader sau khi không còn concurrent loads
10. JVM có thể unload classes khi đủ điều kiện; không bảo đảm ngay
~~~

Nếu bạn dùng `ServiceLoader.load(S.class)` trong một worker thread sống lâu, việc để worker giữ TCCL plugin loader **có thể giữ plugin cũ reachable**, ngay cả sau khi `URLClassLoader.close()`. Vì vậy cần restore TCCL và bỏ cache/registries.

**Không nhầm:** `ServiceLoader.reload()` chỉ reset cache của **một ServiceLoader instance**, không đóng providers cũ, không đổi TCCL đã capture, không close JAR, không tạo loader mới và không ép GC unload classes.

Với `ServiceLoader.Provider<S>` (Java 9+), `Provider.get()` có thể tạo instance trong quá trình sử dụng; lifecycle cũng thuộc trách nhiệm application framework sau khi nhận provider.

## 11. Quy tắc chọn overload trong ứng dụng thực

| Bài toán | API nên cân nhắc | Lý do |
| --- | --- | --- |
| Framework/library không biết plugin loader, chọn context theo request | `ServiceLoader.load(S.class)` | Dùng TCCL để parent-side code tìm child-side provider |
| Host biết loader nào sở hữu plugin | `ServiceLoader.load(S.class, pluginLoader)` | Tường minh, không phụ thuộc executor/TCCL |
| Code cần default system loader, không dựa TCCL | `ServiceLoader.load(S.class, ClassLoader.getSystemClassLoader())` hoặc `load(S.class, null)` | Fallback rõ ràng theo API |
| Chỉ muốn providers từ platform | `ServiceLoader.loadInstalled(S.class)` | Sử dụng platform loader, thường không dùng plugin application providers |
| Plugin modules ở một ModuleLayer riêng | `ServiceLoader.load(layer, S.class)` | Tìm named modules ở layer và ancestors |
| Cần lấy provider đầu tiên nếu có | `ServiceLoader.load(...).findFirst()` | Trả `Optional`; vẫn có thể ném `ServiceConfigurationError` |
| Cần inspect provider types trước khi instantiate | `ServiceLoader.load(...).stream()` | `Provider.type()` và `Provider.get()` tách biệt |
| Cần refresh danh sách providers của loader đang dùng | `serviceLoader.reload()` | Reset cache, **không hot-reload bytecode** |

**Khuyến nghị ứng dụng plugin:** Khi control plane đã biết loader/layer sở hữu provider, dùng **explicit loader/layer**. Chỉ dùng TCCL để tích hợp thư viện/framework yêu cầu ambient context, và khi đổi TCCL phải restore trong `finally`.

## 12. Những ngộ nhận thường gặp

1. “`ServiceLoader.load(S.class)` luôn dùng loader của `S.class`.” — **Sai**; nó dùng TCCL.
2. “`load(S.class, pluginLoader)` chỉ tìm classes từ đúng URL của pluginLoader, không bao giờ qua parent.” — **Sai**; phải tính delegation, `getResources`, named modules/layers.
3. “Sau khi tạo ServiceLoader, đổi TCCL sẽ thay loader nó sử dụng.” — **Sai**; loader được chọn tại factory call.
4. “Đưa JAR vào thư mục `plugins` là ServiceLoader tự quét.” — **Sai** nếu không có loader/module resolution cấu hình tìm đúng artifact.
5. “Provider class ở classpath nhưng không cần `META-INF/services`.” — **Sai** cho provider deployment dạng classpath truyền thống; cần cấu hình chuẩn.
6. “Trong named module vẫn phải có `META-INF/services` như classpath.” — **Không bắt buộc**; dùng `provides ... with`.
7. “`ServiceLoader.loadInstalled` là cách load plugin JAR.” — **Thường sai** trên Java 9+ vì dùng platform loader.
8. “`load(S.class, null)` luôn sử dụng bootstrap.” — **Sai**; ưu tiên system loader.
9. “`ServiceLoader.load` đã instantiate tất cả providers trước khi trả về.” — **Sai**; discovery/instantiation lazy.
10. “`reload()` tạo `Class<?>` mới cho plugin.” — **Sai**; chỉ xóa provider cache.
11. “ServiceLoader tự thread-safe vì nằm trong java.util.” — **Sai**; Javadoc cảnh báo không an toàn với truy cập đa luồng.
12. “Provider cùng tên interface nghĩa là compatible qua hai loaders.” — **Sai**; phải cùng runtime type identity.
13. “Set TCCL trên submitter là đủ cho thread pool worker đã tồn tại.” — **Không có bảo đảm**.
14. “Provider sau khi nhận từ ServiceLoader tự được close khi loader close.” — **Sai**; application quản lý lifecycle provider.
15. “Classloader nhìn thấy provider thì module caller chắc có `uses`/readability phù hợp.” — **Sai**; JPMS kiểm tra thêm module declarations.

## 13. Bài tập tự kiểm tra

Giả sử application API `demo.spi.GreetingService` được host loader định nghĩa, `pluginLoader` nhìn thấy `EnglishGreeter` và SPI file, host loader không thấy provider:

1. `ServiceLoader.load(GreetingService.class, hostLoader)` trả gì khi duyệt? **Không có provider trong lab**, thường danh sách trống.
2. `ServiceLoader.load(GreetingService.class, pluginLoader)` trả gì? **Provider `EnglishGreeter`**.
3. Nếu TCCL hiện tại là `pluginLoader`, `ServiceLoader.load(GreetingService.class)` giống lựa chọn nào? **Explicit plugin loader**.
4. Nếu đổi TCCL về host sau khi tạo ServiceLoader nhưng trước khi `iterator` chạy, loader có đổi không? **Không**.
5. Nếu dùng `load(S.class, null)` khi TCCL đang plugin? **System/bootstrap fallback, không lấy plugin TCCL**.
6. Nếu provider JAR không có file `META-INF/services/demo.spi.GreetingService` và không có named-module declaration, ServiceLoader có quét mọi `*.class` không? **Không**.
7. Provider có class cùng tên `demo.spi.GreetingService` nhưng tự định nghĩa lại interface thì kết quả gì? **Không subtype của host service type**, có thể `ServiceConfigurationError`.
8. Khi nào provider constructor chạy? **Khi provider được instantiated lazily trong iteration/Provider.get**, không nhất thiết khi gọi `load`.
9. `reload()` có bỏ provider objects đã được host lưu nơi khác không? **Không**.
10. Named caller quên `uses` thì xử lý ở đâu? **Sửa module descriptor/caller module, không phải đổi TCCL đơn thuần**.
11. Pool worker giữ TCCL plugin cũ là rủi ro gì? **Sai provider và có thể giữ classloader không unload**.
12. `ServiceLoader.load(layer,S.class)` có tìm provider trong unnamed module chỉ nhờ `META-INF/services` không? **Không**.

## 14. Tài liệu gốc nên tham khảo

1. [`ServiceLoader` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html): overload `load`, TCCL, explicit loader, null behavior, named/unnamed modules, cache, `reload`, concurrency và errors.
2. [`ServiceLoader.Provider` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.Provider.html): `type()`, `get()` và lazy provider creation.
3. [`ClassLoader` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass`, `getResources`, runtime loader behavior.
4. [`Thread` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html): `getContextClassLoader`/`setContextClassLoader` và inheritance.
5. [JPMS `java.lang.Module` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Module.html): module identity/readability.
6. [`ModuleLayer` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ModuleLayer.html): xây và dùng module layers.
7. [JEP 261 — Module System](https://openjdk.org/jeps/261): ServiceLoader với named modules.
8. [Java SPI Tutorial — Oracle (historical Java 8)](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html): khái niệm service interface/provider/ServiceLoader; với Java 9+ ưu tiên đối chiếu Javadoc hiện tại về JPMS.

**Kết luận:** `ServiceLoader.load(S.class)` chọn **TCCL tại lúc tạo ServiceLoader**; `ServiceLoader.load(S.class, chosenLoader)` chọn **loader bạn nêu rõ**. Provider discovery diễn ra lazily và còn phụ thuộc `META-INF/services` hoặc JPMS `provides/uses`, class identity và module graph. Trong host/plugin systems, explicit loader thường dễ kiểm soát hơn; nếu buộc dùng TCCL, phải truyền đúng context tại execution boundary và **luôn khôi phục trong `finally`**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-loading-005">ServiceLoader.load với ModuleLayer</a>

<details>
<summary>Xem chi tiết</summary>

# ServiceLoader.load với ModuleLayer trong Java

## 1. Vì sao cần overload nhận ModuleLayer?

Trong kiến trúc Java có host và plugin, host muốn làm việc với một **service interface** chung, nhưng implementation có thể nằm trong **named module** được nạp vào một `ModuleLayer` mới khi ứng dụng đã chạy. Java 9+ cung cấp overload:

~~~java
public static <S> ServiceLoader<S> load(
        ModuleLayer layer, Class<S> service)
~~~

Đây là chữ ký API, không phải chương trình Java hoàn chỉnh. Nó tạo `ServiceLoader<S>` để khám phá những provider đã đăng ký trong **named modules của layer được truyền vào và các ancestor layers**.

Khác với việc lấy một class bất kỳ qua `ClassLoader.loadClass("pkg.Type")`, ServiceLoader tìm những implementation đã đăng ký cho **một service contract**; consumer không phải import trực tiếp tên implementation.

~~~text
           ModuleLayer.boot()
      +-----------------------------+
      | com.acme.host               |
      | com.acme.spi                |
      | Greeter (service interface) |
      +-----------------------------+
                    ^
                    | parent layer
                    |
        Child ModuleLayer
      +-----------------------------+
      | com.acme.greeter            |
      | HelloGreeter (provider)     |
      +-----------------------------+

ServiceLoader.load(childLayer, Greeter.class)
          -> tìm child layer
          -> sau đó ancestor layers
          -> không tìm provider thuộc unnamed module
             chỉ vì có trên classpath
~~~

Phân biệt bốn khái niệm:

- **`ClassLoader`**: chịu trách nhiệm tìm và định nghĩa classes.
- **`ModuleLayer`**: tập resolved modules với configuration và ánh xạ tới class loaders.
- **`ServiceLoader`**: API khám phá/khởi tạo những provider của một service.
- **`ModuleFinder`**: phát hiện các module trên các đường dẫn chỉ định; bản thân nó **không** tạo layer hoặc tự nạp provider.

Đối với plugin chạy như **explicit named module**, có thể chủ động resolve một module mới vào child layer, rồi gọi `ServiceLoader.load(childLayer, Greeter.class)` mà không cần đặt module plugin lên boot module path từ đầu.

## 2. Hợp đồng giữa ba module

Ví dụ có:

| Module | Vai trò | Chỉ thị thiết yếu |
| --- | --- | --- |
| `com.acme.spi` | Chứa interface `Greeter` | `exports com.acme.spi;` |
| `com.acme.host` | Consumer tìm provider | `requires com.acme.spi;`, **`uses com.acme.spi.Greeter;`** |
| `com.acme.greeter` | Provider module | `requires com.acme.spi;`, **`provides com.acme.spi.Greeter with com.acme.greeter.HelloGreeter;`** |

Interface chung:

~~~java
package com.acme.spi;

public interface Greeter {
    String greet();
}
~~~

Một caller là explicit named module phải **khai báo `uses`** cho service mà nó yêu cầu qua ServiceLoader. Chỉ có `requires com.acme.spi;` là chưa đủ: requires cấp readability, còn uses là tuyên bố nhu cầu sử dụng service. Nếu không có uses phù hợp, API có thể ném `ServiceConfigurationError`.

Trong module provider:

~~~java
module com.acme.greeter {
    requires com.acme.spi;
    provides com.acme.spi.Greeter
            with com.acme.greeter.HelloGreeter;
}
~~~

Từ khóa `provides ... with ...` đăng ký provider theo descriptor. **Không cần `exports com.acme.greeter;` chỉ để ServiceLoader tạo provider**. Host chỉ cần type `Greeter` của SPI module, trong khi class implementation có thể tiếp tục được đóng gói nội bộ.

Một kiểu service/interface phải có **danh tính runtime nhất quán**. Nếu host có `com.acme.spi.Greeter` do loader A định nghĩa, còn plugin lại dùng bản copy Greeter do loader B định nghĩa, chúng có thể không phải cùng interface dù tên giống nhau, dẫn đến lỗi cast/linkage. Đặt SPI module ở parent layer và dùng cùng module là lựa chọn phổ biến.

## 3. Phạm vi của load(layer, service)

Theo Java SE API:

1. Tìm providers trong **layer được truyền vào**.
2. Tìm tiếp providers trong **ancestor layers** của layer đó.
3. **Không tìm providers thuộc unnamed modules** qua overload này.
4. Khi có nhiều parent layers, traversal là **depth-first**; mỗi ancestor layer được xét tối đa một lần.
5. **Không có thứ tự xác định giữa các modules trong cùng layer**. Nếu kết quả cần ưu tiên, ứng dụng phải chọn/sắp xếp rõ ràng.
6. Trong một module khai báo nhiều providers, thứ tự trong module descriptor có vai trò theo quy tắc API; providers thêm động do instrumentation có quy định riêng.
7. Chỉ có những modules thực sự **đã resolve vào layer graph** mới thuộc phạm vi. Có một JAR trên ổ đĩa không đồng nghĩa layer đang chứa module ấy.

Giả sử `L0` là boot, `L1` và `L2` có parent là `L0`, còn `L3` được tạo với hai parents `L1` và `L2`:

~~~text
         L0
       /    \
      L1     L2
        \   /
         L3
~~~

Lời gọi `load(L3, Service.class)` dò L3 trước, rồi ancestors với quy tắc depth-first và tránh xử lý một layer nhiều lần. Một **sibling layer** khác trong JVM không tự được dò nếu không phải ancestor của layer được truyền.

**Đặc biệt:** `ServiceLoader.load(ModuleLayer.boot(), Greeter.class)` không tự đi xuống mọi descendant layer. Nếu provider mới chỉ nằm trong child layer, hãy truyền child layer đó.

## 4. Quy trình tạo child layer cho plugin

Một ví dụ đơn giản chọn boot layer làm parent:

~~~java
ModuleLayer parent = ModuleLayer.boot();

ModuleFinder finder = ModuleFinder.of(
        Path.of("build/plugins"));

Configuration config = parent.configuration().resolve(
        finder,
        ModuleFinder.of(),
        Set.of("com.acme.greeter"));

ModuleLayer pluginLayer = parent.defineModulesWithOneLoader(
        config, ClassLoader.getSystemClassLoader());

ServiceLoader<Greeter> services =
        ServiceLoader.load(pluginLayer, Greeter.class);
~~~

Đoạn trên chỉ minh họa API cốt lõi; import và project chạy đầy đủ ở phần 5.

**Giải thích:**

1. `ModuleLayer.boot()` lấy boot layer chứa những named modules đã được launcher/JVM resolve.
2. `ModuleFinder.of(Path.of(...))` phát hiện exploded modules hoặc modular JAR được đặt đúng vị trí.
3. `Configuration.resolve` tạo configuration mới dựa vào parent configuration. Tập `Set.of("com.acme.greeter")` chỉ định module root; resolver xử lý thêm các required modules phù hợp.
4. `parent.defineModulesWithOneLoader(config, parentLoader)` định nghĩa child layer. Phương thức tạo **một class loader mới** cho modules thuộc configuration của layer con. Đối số `ClassLoader.getSystemClassLoader()` là **parent của loader mới**, không phải chính defining loader của các class plugin.
5. `ServiceLoader.load(pluginLayer, Greeter.class)` khám phá provider theo metadata của các named modules trong plugin layer và ancestors.

Nếu một plugin JAR nằm trong finder path nhưng **không nằm trong graph được resolve**, `ServiceLoader` không tự thêm module đó vào layer. Đối với bài toán auto-discovery có service-binding resolution, API `Configuration.resolveAndBind` là lựa chọn khác cần nghiên cứu; ví dụ này cố ý đặt provider làm root rõ ràng để hành vi có thể tái lập.

### One loader và many loaders khác nhau ra sao?

- `defineModulesWithOneLoader` tạo một class loader cho tất cả modules trong child configuration.
- `defineModulesWithManyLoaders` tạo class loader riêng cho từng module theo hợp đồng API.
- Cả hai đều tạo **named modules trong layer**; không phải đơn giản thêm JAR vào classpath.
- Quyết định cần xét số modules, split packages, mối quan hệ module/loader và quy tắc delegation. Với một module plugin, `oneLoader` đủ để học rõ ServiceLoader.

## 5. Ví dụ Java 17+ chạy được: 3 modules, 1 provider

Bài tập biên dịch ba modules, nhưng khi **chạy** sẽ tách:
- `com.acme.host` và `com.acme.spi` nằm trên module path của launcher, thuộc boot layer.
- `com.acme.greeter` nằm ở đường dẫn plugin riêng và chỉ được đưa vào **child layer** qua `ModuleFinder`/`Configuration`.

### Cấu trúc nguồn

~~~text
example/
└── src/
    ├── com.acme.spi/
    │   ├── module-info.java
    │   └── com/acme/spi/Greeter.java
    ├── com.acme.host/
    │   ├── module-info.java
    │   └── com/acme/host/Main.java
    └── com.acme.greeter/
        ├── module-info.java
        └── com/acme/greeter/HelloGreeter.java
~~~

### File src/com.acme.spi/module-info.java

~~~java
module com.acme.spi {
    exports com.acme.spi;
}
~~~

### File src/com.acme.spi/com/acme/spi/Greeter.java

~~~java
package com.acme.spi;

public interface Greeter {
    String greet();
}
~~~

### File src/com.acme.greeter/module-info.java

~~~java
module com.acme.greeter {
    requires com.acme.spi;
    provides com.acme.spi.Greeter
            with com.acme.greeter.HelloGreeter;
}
~~~

### File src/com.acme.greeter/com/acme/greeter/HelloGreeter.java

~~~java
package com.acme.greeter;

import com.acme.spi.Greeter;

public final class HelloGreeter implements Greeter {
    @Override
    public String greet() {
        return "Hello from child layer";
    }
}
~~~

Lớp provider public không có constructor tường minh, nên có implicit public default constructor phù hợp mô hình provider dùng constructor trong trường hợp này. Package implementation không cần exports cho host.

### File src/com.acme.host/module-info.java

~~~java
module com.acme.host {
    requires com.acme.spi;
    uses com.acme.spi.Greeter;
}
~~~

### File src/com.acme.host/com/acme/host/Main.java

~~~java
package com.acme.host;

import com.acme.spi.Greeter;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "plugin module directory required");
        }

        ModuleLayer parent = ModuleLayer.boot();
        ModuleFinder plugins = ModuleFinder.of(Path.of(args[0]));

        Configuration config = parent.configuration().resolve(
                plugins,
                ModuleFinder.of(),
                Set.of("com.acme.greeter"));

        ModuleLayer child = parent.defineModulesWithOneLoader(
                config, ClassLoader.getSystemClassLoader());

        System.out.println("child has provider: "
                + child.findModule("com.acme.greeter").isPresent());
        System.out.println("host in boot: "
                + (Main.class.getModule().getLayer() == parent));

        List<Greeter> services =
                ServiceLoader.load(child, Greeter.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();

        System.out.println("provider count: " + services.size());

        for (Greeter greeter : services) {
            Module module = greeter.getClass().getModule();

            System.out.println("provider module: "
                    + module.getName());
            System.out.println("provider in child: "
                    + (module.getLayer() == child));
            System.out.println("message: "
                    + greeter.greet());
        }
    }
}
~~~

Ý nghĩa các bước:
- Host khai báo `uses` trong module descriptor, không gọi implementation trực tiếp.
- Module `com.acme.spi` ở parent tạo contract chung.
- Finder tìm module plugin, config resolve root, layer mới được định nghĩa.
- `ServiceLoader.load(child, Greeter.class)` tìm provider trong child/ancestors.
- `stream().map(ServiceLoader.Provider::get)` yêu cầu instance provider; việc khởi tạo provider là lazy khi duyệt.
- Một provider duy nhất giúp stdout ổn định; nếu có nhiều modules, không dựa vào thứ tự iteration không được đảm bảo.

### Biên dịch và chạy: Linux/macOS

Đứng trong thư mục example đã có sáu tệp Java:

~~~bash
mkdir -p build/all build/base build/plugins

javac --release 17 --module-source-path src \
    -d build/all $(find src -name '*.java')

cp -R build/all/com.acme.spi build/base/
cp -R build/all/com.acme.host build/base/
cp -R build/all/com.acme.greeter build/plugins/

java --module-path build/base \
    --module com.acme.host/com.acme.host.Main build/plugins
~~~

Đảm bảo `build/base` **chỉ** chứa host và SPI, `build/plugins` **chỉ** chứa provider. Khi chạy, không đưa provider vào `--module-path build/base`. Plugin được phát hiện và resolve sau khi app đã vào phương thức `main`.

### Biên dịch và chạy: Windows PowerShell

Dùng lệnh một dòng để tránh nhầm ký tự nối dòng:

~~~powershell
New-Item -ItemType Directory -Force build\all, build\base, build\plugins | Out-Null
$sources = @(Get-ChildItem -Path src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
javac --release 17 --module-source-path src -d build\all $sources
Copy-Item build\all\com.acme.spi build\base\com.acme.spi -Recurse
Copy-Item build\all\com.acme.host build\base\com.acme.host -Recurse
Copy-Item build\all\com.acme.greeter build\plugins\com.acme.greeter -Recurse
java --module-path build\base --module com.acme.host/com.acme.host.Main build\plugins
~~~

Cần biên dịch thành công trước khi copy module output. Nếu chạy lại, hãy xóa output cũ thích hợp hoặc build trong thư mục mới để tránh module cũ gây nhiễu.

### Stdout thực tế đã kiểm chứng

~~~text
child has provider: true
host in boot: true
provider count: 1
provider module: com.acme.greeter
provider in child: true
message: Hello from child layer
~~~

Mã nguồn ở trên đã được biên dịch với `javac --release 17` và chạy thành công trên OpenJDK 21.

**Phân tích output:**
1. Child layer thực sự có module `com.acme.greeter`.
2. Host giữ nguyên trong boot layer.
3. Chỉ một provider được nạp do có một khai báo `provides` phù hợp.
4. Instance `HelloGreeter` thuộc module `com.acme.greeter`.
5. Module chứa class provider thuộc child layer chứ không phải boot.
6. Host gọi `greeter.greet()` qua interface chung, nhận chuỗi mong đợi.

## 6. Thay exploded module bằng modular JAR

`ModuleFinder` có thể đọc một **modular JAR** có `module-info.class`. Khi đã compile phần 5, đóng gói provider:

~~~bash
mkdir -p build/plugin-jars
jar --create --file build/plugin-jars/com.acme.greeter.jar \
    -C build/all/com.acme.greeter .
java --module-path build/base \
    --module com.acme.host/com.acme.host.Main build/plugin-jars
~~~

Trên PowerShell:

~~~powershell
New-Item -ItemType Directory -Force build\plugin-jars | Out-Null
jar --create --file build\plugin-jars\com.acme.greeter.jar -C build\all\com.acme.greeter .
java --module-path build\base --module com.acme.host/com.acme.host.Main build\plugin-jars
~~~

Lớp `Main` không cần sửa vì nó nhận plugin path qua `args[0]`. ModuleFinder phát hiện file JAR, resolver tạo child configuration, ServiceLoader tìm provider từ named module trong child layer.

Có thể kiểm tra JAR qua:

~~~bash
jar --describe-module --file build/plugin-jars/com.acme.greeter.jar
~~~

Biến thể modular JAR cũng đã được chạy thử trong một bài kiểm thử riêng trên OpenJDK, trả đúng provider. Lưu ý: thêm file JAR sau khi layer đã được tạo **không cập nhật layer đã tồn tại**; phải resolve/tạo layer mới nếu muốn module set mới.

## 7. So sánh các overload ServiceLoader

| API | Nguồn tìm provider | Khi nào phù hợp |
| --- | --- | --- |
| `ServiceLoader.load(ModuleLayer layer, Class<S> service)` | Named modules trong **layer và ancestors**, không unnamed modules. | Plugin/module layers JPMS. |
| `ServiceLoader.load(Class<S> service)` | Dùng **thread context class loader** của thread hiện tại. | Ứng dụng hoặc framework quản lý services theo TCCL. |
| `ServiceLoader.load(Class<S> service, ClassLoader loader)` | Sử dụng class loader được chỉ định theo quy tắc ServiceLoader. | Chủ động chọn loader để tìm services theo môi trường. |
| `ServiceLoader.loadInstalled(Class<S> service)` | Sử dụng platform class loader. | Phạm vi platform/installed providers theo API. |

**Điểm quan trọng:** Overload `load(layer, service)` không phải cú pháp viết khác của `load(service, classLoader)`. Layer xác định graph named modules, còn ClassLoader xác định cơ chế nạp và tìm resource/provider theo API tương ứng. Việc đổi TCCL không biến một ServiceLoader theo layer thành bộ dò mọi unnamed provider.

### META-INF/services khác provides

Với classpath truyền thống, nhà cung cấp thường đăng ký qua:

~~~text
META-INF/services/com.acme.spi.Greeter
~~~

Nội dung là binary name provider, ví dụ `com.acme.greeter.HelloGreeter`. Đó là cấu hình service cho classpath/unnamed module. Với explicit named module, khai báo `provides ... with ...` trong module descriptor mới là mô hình JPMS.

Đừng đặt một provider chỉ trong `META-INF/services` trên classpath rồi trông chờ `load(childLayer, Greeter.class)` tự khám phá nó: overload theo layer **không tìm provider trong unnamed modules**.

## 8. Quy tắc provider, constructor và factory method

Với named module:

- Provider được liệt kê trong chỉ thị `provides service with provider;` của **module chứa provider**.
- Provider class/declared provider hợp lệ theo quy tắc JLS.
- Nếu dùng **provider constructor**, class provider là subtype của service và có public no-arg constructor (tường minh hoặc ngầm định).
- Named-module provider có thể dùng một public static no-arg method tên chính xác **`provider()`** với return type phù hợp. Với cách này, provider class không bắt buộc trực tiếp implements service nếu method trả service instance thích hợp.
- Consumer module cần `uses` nếu thuộc explicit named module.
- Service API phải accessible và có danh tính class nhất quán giữa các module.

Ví dụ minh họa cho provider factory method:

~~~java
package com.acme.greeter;

import com.acme.spi.Greeter;

public final class GreeterFactory {
    public static Greeter provider() {
        return new HelloGreeter();
    }
}
~~~

Với code minh họa này, descriptor có thể viết:

~~~java
provides com.acme.spi.Greeter
        with com.acme.greeter.GreeterFactory;
~~~

Đây là **phương án thay thế**, không áp dụng đồng thời trong bài chạy phần 5. Factory method phải đáp ứng đúng đặc tả, không đổi tên tự do thành `create()` mà kỳ vọng ServiceLoader tự nhận diện.

## 9. Lazy discovery, cache, reload và ordering

Một `ServiceLoader` chỉ khởi tạo provider **khi cần** trong quá trình duyệt iterator hoặc truy cập `Provider.get()` trên stream:

~~~java
ServiceLoader<Greeter> services =
        ServiceLoader.load(layer, Greeter.class);

for (Greeter service : services) {
    System.out.println(service.greet());
}
~~~

Đoạn này giả định biến `layer` đã có và mã ở trong consumer hợp lệ. Nó giúp tránh khởi tạo mọi provider ngay khi gọi `ServiceLoader.load`.

Khi chỉ muốn xem provider type trước khi tạo instance, dùng `ServiceLoader.stream()` và `ServiceLoader.Provider::type`. Khi cần instance, dùng `Provider::get`. Đừng đánh đồng việc truy vấn type metadata với việc cố ý chạy constructor của provider.

**Cache:** ServiceLoader giữ cache của các provider đã được khám phá/khởi tạo theo hợp đồng. `reload()` xóa cache để lần duyệt sau bắt đầu lại. Tuy nhiên, `reload()` **không**:

- Biên dịch lại module.
- Resolve thêm JAR vừa copy.
- Tạo child `ModuleLayer` mới.
- Thay bytecode của class đã định nghĩa.
- Unload mọi object/class/provider đang được ứng dụng giữ.

Muốn bổ sung một plugin module mới vào graph, thường phải **tạo Configuration/ModuleLayer mới** với root/plugin paths thích hợp, rồi tạo ServiceLoader mới cho layer đó.

**Ordering:** API không đảm bảo thứ tự các module trong cùng layer. Không dùng provider đầu tiên làm “mặc định” nếu kết quả phải ổn định giữa nhiều plugin, trừ khi chính bạn thiết kế quy tắc chọn/sắp xếp sau khi duyệt.

## 10. Troubleshooting

| Triệu chứng | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| Không thấy provider | Plugin module chưa có trong layer hoặc thiếu `provides`. | Xem `child.modules()`, descriptor, root modules. |
| `ServiceConfigurationError` | Caller named module thiếu `uses`; provider sai hợp đồng/không tạo được; service không accessible. | Kiểm tra `uses`/`provides`, cause và module boundaries. |
| Resolver không tìm được module | Sai tên root/module path hoặc thiếu requires dependency. | Xem `ModuleFinder`, `Configuration.resolve`, exception. |
| `LayerInstantiationException` | Split packages hoặc mapping loader/module không hợp lệ. | Kiểm tra package ownership, lựa chọn loader strategy. |
| `ClassCastException` | SPI bị định nghĩa thành hai class runtime khác nhau. | So sánh `Greeter.class.getClassLoader()` và lớp service provider dùng. |
| Provider từ classpath không xuất hiện | Layer overload không quét unnamed module. | Dùng API classloader/TCCL hoặc chuyển provider sang named module. |
| `reload()` không thấy plugin JAR vừa thêm | Graph/layer cũ không được cập nhật. | Resolve và tạo layer mới. |
| Iteration order đổi | Có nhiều module cùng layer, không được đảm bảo thứ tự. | Chọn provider theo cấu hình/priority rõ ràng. |
| Load module thành công nhưng reflection vào implementation lỗi | Package provider không export/open cho caller. | Dùng SPI contract thay vì reflect trực tiếp vào implementation nội bộ. |

### Checklist

1. Consumer có `uses service;` trong descriptor chưa?
2. SPI module có `exports` package interface cho consumer và provider không?
3. Provider module có `provides service with impl;` và implementation hợp lệ chưa?
4. Module provider thực sự đã **resolved vào layer** chứ không chỉ tồn tại trên ổ đĩa chưa?
5. `ServiceLoader.load` được truyền **layer chứa provider hoặc descendant** chưa?
6. Có nhầm với unnamed modules hoặc file `META-INF/services` trên classpath không?
7. Có nhiều providers khiến thứ tự kết quả không xác định không?
8. `ServiceLoader.reload()` có bị hiểu lầm thành reload ModuleLayer không?
9. Exception xuất hiện tại resolution, layer creation hay khi iterator tạo provider?
10. Có giữ đúng một service interface class identity giữa host/plugin không?

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Provider nằm riêng trong child layer. Host gọi `ServiceLoader.load(ModuleLayer.boot(), Greeter.class)` có tìm được provider đó không?

**Đáp:** Không. API duyệt layer được đưa vào và ancestors, không tự duyệt descendants.

**Câu 2.** Consumer đã `requires com.acme.spi;` có còn cần `uses com.acme.spi.Greeter;`?

**Đáp:** Có đối với explicit named module gọi ServiceLoader. `requires` cung cấp readability, `uses` khai báo dịch vụ cần dùng.

**Câu 3.** Provider package phải `exports` cho host để ServiceLoader tạo object không?

**Đáp:** Không. `provides ... with ...` cho phép ServiceLoader tạo provider được khai báo hợp lệ; host giao tiếp qua SPI đã export.

**Câu 4.** Đặt một JAR chỉ có `META-INF/services` trên classpath. Overload nhận ModuleLayer có tìm nó không?

**Đáp:** Không, nó không tìm provider từ unnamed modules.

**Câu 5.** Sao chép JAR mới vào plugin folder sau khi tạo layer, gọi `reload()` là đủ?

**Đáp:** Không. Layer/configuration đã được định nghĩa. Tạo layer mới nếu muốn graph modules khác.

**Câu 6.** Khi nhiều named modules trong cùng layer cung cấp provider, thứ tự có luôn theo tên module không?

**Đáp:** Không. Thứ tự các module trong một layer không được bảo đảm.

## Tài liệu chính thức

- [ServiceLoader, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html): overload `load(ModuleLayer, Class)`, lazy providers, cache, uses/provides.
- [ModuleLayer, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html): tạo layer và loader mapping.
- [Configuration, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/Configuration.html): resolve/resolveAndBind.
- [ModuleFinder, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/ModuleFinder.html): tìm modular JAR/exploded modules.
- [JLS Java SE 21 §7.7.4](https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html#jls-7.7.4): rules của service provision.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-loading-006">Khám phá provider và khởi tạo lười</a>

<details>
<summary>Xem chi tiết</summary>

# Khám phá provider và khởi tạo lười với ServiceLoader trong Java

## 1. Service provider là gì và vì sao cần khám phá động?

Trong Java, một **service** thường là một interface hoặc abstract class công khai mô tả hợp đồng mà ứng dụng cần; **service provider** là một implementation có thể được cài đặt và phát hiện lúc chạy. Mã tiêu thụ chỉ phụ thuộc vào **service contract**, không phải tên lớp triển khai cụ thể.

Ví dụ, một ứng dụng hỗ trợ nhiều bộ giải mã:

```java
package example.spi;

public interface Codec {
    String decode(byte[] input);
}
```

Đây là **file interface minh họa**, không phải một ví dụ chương trình độc lập. Nhà cung cấp A, B có thể triển khai interface này và đăng ký qua metadata hoặc `module-info.java`. Ứng dụng dùng `ServiceLoader<Codec>` để khám phá các provider có sẵn.

`java.util.ServiceLoader<S>` là API chuẩn cho **service-provider loading (SPI)**, không phải container dependency injection tổng quát. Nó có thể tìm implementation từ:
- **Classpath/unnamed modules**: tệp metadata `META-INF/services/<binary-service-name>`.
- **Named modules (JPMS)**: khai báo `provides ... with ...` trong module descriptor; consumer khai báo `uses ...`.
- **ModuleLayer** tạo động: có overload để tìm providers trong layer và các layer cha.

Điều quan trọng của đề bài là **khám phá provider (discovery)** và **khởi tạo lười (lazy instantiation)** là **hai hoạt động có thể xảy ra ở thời điểm khác nhau**.

## 2. “Lazy” thực sự nghĩa là gì?

Lệnh:

```java
ServiceLoader<Codec> loader = ServiceLoader.load(Codec.class);
```

**Không có nghĩa** Java ngay lập tức đọc xong mọi service descriptors, nạp mọi provider class và gọi constructor của tất cả provider. Thay vào đó, `ServiceLoader` cung cấp cơ chế truy vấn **theo nhu cầu**:

1. Tạo đối tượng `ServiceLoader` cho một service type và một ngữ cảnh class loader/layer.
2. Khi bắt đầu duyệt, tìm các provider declarations phù hợp từ module descriptors hoặc `META-INF/services`.
3. Tải và kiểm tra provider type khi cần.
4. **Instantiate provider** khi consumer cần một provider instance (ví dụ `Iterator.next()`, `findFirst()` hoặc `Provider.get()`).
5. Giữ cache các provider đã tìm/nạp/khởi tạo theo ngữ nghĩa của phương thức duyệt, để những lần duyệt tiếp theo có thể dùng lại.
6. `reload()` xóa cache của chính đối tượng `ServiceLoader`; provider sẽ được khám phá/nạp/khởi tạo lại khi duyệt tiếp, nhưng class đã định nghĩa và static initializer đã chạy thành công **không tự được reset**.

Sơ đồ:

```text
ServiceLoader.load(Service.class)
             │
             ▼
       ServiceLoader object
             │
             ├── iterator()
             │      ├── hasNext() → khám phá tên/provider khi cần
             │      └── next()    → lấy/tạo provider instance
             │
             ├── findFirst()      → lấy/tạo provider đầu tiên nếu có
             │
             └── stream()
                    └── Provider<S>
                          ├── type() → Class<?> của provider type
                          └── get()  → provider instance
             │
             ▼
          Provider được dùng
```

**Lưu ý:** các phương thức tìm `hasNext()`/duyệt stream **có thể làm I/O, tìm tệp cấu hình, tải class và phát sinh lỗi**. “Lazy” không có nghĩa chúng luôn miễn phí. Điều được trì hoãn là **khám phá và instantiate theo nhu cầu**, thay vì tạo mọi provider ngay khi gọi `ServiceLoader.load`.

## 3. Hai khái niệm cần tách: loading và initialization

Trong JVM:
- **Loading**: ClassLoader/JVM tìm và định nghĩa runtime class.
- **Linking**: JVM verification, preparation, resolution theo quy tắc.
- **Initialization**: JVM chạy các biểu thức khởi tạo static field và `static { ... }` khi có active use thích hợp.
- **Instantiation**: tạo **object instance**, chạy constructor và các initializers liên quan.

Một provider class có thể được **load mà chưa initialized**. Khi `ServiceLoader.Provider.type()` trả về `Class<?>`, việc lấy kiểu provider thông thường không yêu cầu tạo instance. Ngược lại, khi `Provider.get()` tạo object bằng constructor hay provider factory method, class sẽ được khởi tạo theo quy tắc Java nếu chưa từng initialized.

Ví dụ:

```java
// Trích đoạn: providerClass đã tồn tại ở loader được chọn.
Class<?> providerClass =
        Class.forName("demo.impl.Alpha", false, providerLoader);
// Chưa chủ động yêu cầu chạy Alpha.<clinit>.

Object provider = providerClass.getDeclaredConstructor().newInstance();
// Có thể buộc khởi tạo Alpha và chạy constructor.
```

Đoạn này là trích đoạn reflection, **không phải quy trình thay thế đầy đủ cho ServiceLoader**. ServiceLoader còn kiểm tra service contract, metadata, module rules và provider constructor/factory.

**Không đánh đồng** “provider đã được phát hiện” với “provider instance đã được tạo”, và cũng không đánh đồng “object ServiceLoader đã được tạo” với “provider đã được tìm thấy”.

## 4. API chính của ServiceLoader

| API | Kết quả | Mức độ lazy cần nhớ |
|---|---|---|
| `ServiceLoader.load(S.class)` | Tạo service loader theo **TCCL** hiện tại | Chưa instantiate mọi providers |
| `ServiceLoader.load(S.class, loader)` | Dùng explicit `ClassLoader` | Cũng khám phá theo nhu cầu |
| `ServiceLoader.load(layer, S.class)` | Tìm providers từ `ModuleLayer` và các parent layers | Không tìm provider từ unnamed modules theo overload này |
| `serviceLoader.iterator()` | `Iterator<S>` | `hasNext` có thể khám phá; `next` lấy/tạo instance |
| `serviceLoader.findFirst()` | `Optional<S>` | Tương đương lấy provider đầu tiên từ iterator |
| `serviceLoader.stream()` | `Stream<ServiceLoader.Provider<S>>` | Xem provider descriptions/type trước khi tạo instance |
| `provider.type()` | `Class<? extends S>` | Xem kiểu mà không cần instantiate provider |
| `provider.get()` | `S` | Lấy hoặc tạo instance theo provider contract |
| `serviceLoader.reload()` | `void` | Xóa cache providers của chính ServiceLoader |

Các overload `load` **không có cùng ngữ cảnh tìm kiếm**. Với ứng dụng plugin, chọn đúng loader hoặc layer quan trọng không kém việc chọn interface service.

## 5. Ví dụ đầy đủ Java 17+: provider classpath và lazy instantiation

Ta sẽ tạo một service interface `demo.spi.Greeting`, hai provider classes và một host. Provider chứa static initializer và constructor in ra console, để thấy rõ **khi nào** Java thực sự tạo instance.

### 5.1. Cấu trúc thư mục

```text
spi-demo/
├── src/
│   ├── demo/
│   │   ├── spi/Greeting.java
│   │   ├── impl/AlphaGreeting.java
│   │   ├── impl/BetaGreeting.java
│   │   └── app/Host.java
│   └── META-INF/
│       └── services/
│           └── demo.spi.Greeting
└── out/
    ├── demo/...
    └── META-INF/services/demo.spi.Greeting
```

### 5.2. Service contract: src/demo/spi/Greeting.java

```java
package demo.spi;

public interface Greeting {
    String message();
}
```

### 5.3. Provider A: src/demo/impl/AlphaGreeting.java

```java
package demo.impl;

import demo.spi.Greeting;

public final class AlphaGreeting implements Greeting {
    static {
        System.out.println("Alpha initialized");
    }

    public AlphaGreeting() {
        System.out.println("Alpha constructed");
    }

    @Override
    public String message() {
        return "alpha";
    }
}
```

### 5.4. Provider B: src/demo/impl/BetaGreeting.java

```java
package demo.impl;

import demo.spi.Greeting;

public final class BetaGreeting implements Greeting {
    static {
        System.out.println("Beta initialized");
    }

    public BetaGreeting() {
        System.out.println("Beta constructed");
    }

    @Override
    public String message() {
        return "beta";
    }
}
```

### 5.5. Provider configuration file

Tạo tệp đúng đường dẫn:

```text
src/META-INF/services/demo.spi.Greeting
```

Nội dung **hai dòng theo thứ tự**:

```text
demo.impl.AlphaGreeting
demo.impl.BetaGreeting
```

Tên **tệp** phải trùng **binary name** của service type, không phải tên provider; nội dung chứa binary names của implementations. Không thêm `.class` vào tên tệp metadata.

### 5.6. Host: src/demo/app/Host.java

```java
package demo.app;

import demo.spi.Greeting;
import java.util.Iterator;
import java.util.ServiceLoader;

public final class Host {
    private static void iteratorDemo() {
        ServiceLoader<Greeting> services =
                ServiceLoader.load(
                        Greeting.class,
                        Host.class.getClassLoader()
                );

        System.out.println("loader created");
        Iterator<Greeting> items = services.iterator();
        System.out.println("iterator created");

        System.out.println("has first: " + items.hasNext());
        System.out.println("before first next");
        Greeting first = items.next();
        System.out.println("first message: " + first.message());

        System.out.println("has second: " + items.hasNext());
        System.out.println("before second next");
        Greeting second = items.next();
        System.out.println("second message: " + second.message());

        Greeting cached = services.iterator().next();
        System.out.println("first cached object: " + (cached == first));

        services.reload();
        System.out.println("after reload");
        Greeting recreated = services.iterator().next();
        System.out.println("new first instance: " + (recreated != first));
    }

    private static void streamDemo() {
        ServiceLoader<Greeting> services =
                ServiceLoader.load(
                        Greeting.class,
                        Host.class.getClassLoader()
                );

        System.out.println("loader created");
        ServiceLoader.Provider<Greeting> provider =
                services.stream().findFirst().orElseThrow();

        System.out.println("provider type: "
                + provider.type().getName());
        System.out.println("before provider get");

        Greeting instance = provider.get();
        System.out.println("message: " + instance.message());
    }

    public static void main(String[] args) {
        if (args.length == 1 && "stream".equals(args[0])) {
            streamDemo();
        } else {
            iteratorDemo();
        }
    }
}
```

Chương trình có hai chế độ chạy trong **hai JVM processes riêng** để static initializer của Alpha không bị gọi sẵn từ chế độ khác. Cả hai chế độ dùng cùng interface, implementations và metadata.

### 5.7. Biên dịch và chuẩn bị resource

Tại thư mục `spi-demo`, dùng JDK 17+.

Trên Bash:

```bash
mkdir -p out/META-INF/services
javac --release 17 -d out src/demo/spi/Greeting.java src/demo/impl/AlphaGreeting.java src/demo/impl/BetaGreeting.java src/demo/app/Host.java
cp src/META-INF/services/demo.spi.Greeting out/META-INF/services/demo.spi.Greeting
```

Trên Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force out/META-INF/services | Out-Null
javac --release 17 -d out src/demo/spi/Greeting.java src/demo/impl/AlphaGreeting.java src/demo/impl/BetaGreeting.java src/demo/app/Host.java
Copy-Item src/META-INF/services/demo.spi.Greeting out/META-INF/services/demo.spi.Greeting
```

File cấu hình provider **không được javac tự biên dịch/sao chép**; phải copy resource hoặc dùng build tool (Maven/Gradle) đưa nó vào artifact cuối cùng.

Kết quả nên có:

```text
out/
├── demo/
│   ├── spi/Greeting.class
│   ├── impl/AlphaGreeting.class
│   ├── impl/BetaGreeting.class
│   └── app/Host.class
└── META-INF/services/demo.spi.Greeting
```

### 5.8. Chạy chế độ iterator

```bash
java -cp out demo.app.Host
```

Stdout:

```text
loader created
iterator created
has first: true
before first next
Alpha initialized
Alpha constructed
first message: alpha
has second: true
before second next
Beta initialized
Beta constructed
second message: beta
first cached object: true
after reload
Alpha constructed
new first instance: true
```

Giải thích:

1. `load(Greeting.class, loader)` tạo đối tượng service loader: chưa in gì từ Alpha/Beta.
2. `iterator()` tạo iterator; `hasNext()` tìm metadata và cho biết có provider đầu tiên, nhưng chưa gọi constructor Alpha.
3. `next()` lấy instance đầu tiên, nên static initializer Alpha chạy lần đầu, rồi constructor Alpha chạy, rồi `message()` trả `alpha`.
4. Provider B chưa được khởi tạo ở thời điểm Alpha được dùng. Khi gọi `next()` lần hai, Beta mới được instantiate.
5. Một iterator mới từ **cùng ServiceLoader** sẽ trả lại provider objects đã cache trước khi tìm các provider còn lại. Vì thế `cached == first` in `true`.
6. `reload()` xóa provider cache. Lần duyệt tiếp theo tạo một **instance Alpha mới**, nhưng **không chạy lại** `Alpha.<clinit>`: static initialization thành công diễn ra một lần cho cùng runtime class.
7. `reload()` không định nghĩa lại Alpha bằng ClassLoader mới, không đổi binary name hoặc bytecode, và không tự “hot reload” JAR đã ghi đè.

### 5.9. Chạy chế độ stream trong JVM riêng

```bash
java -cp out demo.app.Host stream
```

Stdout:

```text
loader created
provider type: demo.impl.AlphaGreeting
before provider get
Alpha initialized
Alpha constructed
message: alpha
```

`stream().findFirst()` trả một `Provider<Greeting>` descriptor. `provider.type()` cho biết class provider mà **không tạo instance**, nên static initializer Alpha chưa chạy từ bước này. `provider.get()` mới lấy provider instance, kích hoạt initialization và constructor nếu đây là lần đầu.

**Không được suy ra** rằng `ServiceLoader.Provider.get()` luôn memoize một object cụ thể khi gọi nhiều lần. Đừng dùng `get() == get()` như hợp đồng singleton. Khi cần chia sẻ một instance theo vòng đời, ứng dụng nên quản lý nó, hoặc dùng provider factory/lifecycle có thiết kế rõ ràng.

## 6. Cơ chế tìm metadata trên classpath

Khi dùng provider configuration ở unnamed module/classpath, `ServiceLoader` tìm resources theo đường dẫn:

```text
META-INF/services/<binary-name-of-service>
```

Với ví dụ trên:

```text
META-INF/services/demo.spi.Greeting
```

Các tệp provider configuration có những quy tắc quan trọng:

- Phải được mã hóa **UTF-8**.
- Mỗi dòng hợp lệ chứa **binary name đầy đủ** của một provider class.
- Dòng trống và khoảng trắng/tab bao quanh tên được bỏ qua.
- Ký tự `#` mở đầu comment; phần còn lại của dòng sau `#` bị bỏ qua.
- Một provider class name lặp trong một hoặc nhiều configuration files sẽ được bỏ qua ở những lần trùng tên khi loader xử lý theo contract.
- Provider có thể nằm trong JAR khác tệp service configuration, miễn classloader được sử dụng **nhìn thấy** implementation.
- Thứ tự các provider trong **một file cấu hình** ảnh hưởng thứ tự duyệt; với nhiều resources, thứ tự phụ thuộc `ClassLoader.getResources` và những quy tắc phối hợp provider modules. Không nên xem “first provider” là thứ tự toàn cục ổn định khi triển khai phức tạp.

Ví dụ metadata hợp lệ:

```text
# Các provider dành cho Greeting
demo.impl.AlphaGreeting    # ưu tiên theo file này

demo.impl.BetaGreeting
```

### Sai vị trí và sai tên

Những cấu trúc sau **không tương đương**:

```text
META-INF/services/demo.spi.Greeting      [đúng tên service]
META-INF/services/demo.impl.AlphaGreeting [sai: tên provider]
META-INF/service/demo.spi.Greeting       [sai thư mục]
META-INF/services/demo.spi.Greeting.txt  [sai: thừa extension]
```

Nếu thiếu metadata hoặc metadata nằm sai path, `ServiceLoader` có thể không nhìn thấy provider dù `AlphaGreeting.class` có trên classpath. Ngược lại, metadata có tên provider không tồn tại, không assignable hoặc không có constructor phù hợp có thể gây `ServiceConfigurationError` trong lúc duyệt.

## 7. Khi nào provider được load, link, initialize và instantiate?

Ở classpath provider kiểu constructor, các sự kiện có thể xảy ra như sau:

```text
ServiceLoader.load(S.class, loader)
         │
         └── có object ServiceLoader

iterator.hasNext() / stream operation
         │
         ├── tìm provider configuration
         ├── phân tích provider names
         └── có thể tải/kiểm tra provider classes
                    │
                    ▼
iterator.next() / findFirst() / Provider.get()
         │
         ├── bảo đảm provider sẵn sàng tạo
         ├── initialization của provider class nếu cần
         ├── gọi public no-arg constructor
         └── trả object S
```

Đây là **sơ đồ ý nghĩa**, không phải cam kết `hasNext` tuyệt đối không làm bất kỳ class loading nào; các implementation/JVM có thể liên kết các kiểu theo thời điểm khác nhau.

Khi test, hãy phân biệt **dấu hiệu từ static initializer** và **constructor**:

```java
static {
    System.out.println("Provider class initialized");
}

public ExampleProvider() {
    System.out.println("Provider object constructed");
}
```

Đây là **đoạn trong class**, không phải tệp độc lập. Một static initializer thường chạy một lần cho **mỗi runtime class** khi init thành công. Constructor có thể chạy nhiều lần nếu ứng dụng tạo nhiều provider instances, kể cả sau `ServiceLoader.reload()` hoặc với những ServiceLoader objects khác nhau.

Nếu constructor ném exception, `ServiceLoader` thường trình bày lỗi dưới dạng `ServiceConfigurationError` khi consumer thực sự duyệt/instantiate provider.

## 8. Iterator, findFirst và stream: chọn đúng API

### 8.1. iterator() cho trường hợp muốn các instances

```java
ServiceLoader<Greeting> services = ServiceLoader.load(Greeting.class);
for (Greeting greeting : services) {
    System.out.println(greeting.message());
}
```

Đây là **trích đoạn**, giả định `Greeting` khả dụng và đã import `ServiceLoader`. Enhanced for-loop sử dụng `iterator()`, vì thế có thể instantiate các providers trong quá trình duyệt.

Lưu ý **duyệt không bắt buộc tìm mọi provider ngay**: nếu vòng lặp `break` sau provider đầu tiên, các providers sau có thể chưa được instantiate.

### 8.2. findFirst() cho trường hợp chỉ cần một

```java
Greeting selected = ServiceLoader
        .load(Greeting.class)
        .findFirst()
        .orElseThrow();
```

Đây là trích đoạn. `findFirst()` dùng lazy iteration để lấy provider đầu tiên nếu có; không cần instantiate toàn bộ providers. Nếu không có provider, `Optional` rỗng. Nếu metadata/provider hỏng, **không** bảo đảm trả `Optional.empty()`: vẫn có thể ném `ServiceConfigurationError`.

**Thứ tự provider** là phần của contract và môi trường triển khai. Đừng dùng `findFirst()` để thực thi thứ tự ưu tiên nghiệp vụ trong hệ có nhiều JAR/modules mà không định nghĩa thứ tự một cách rõ ràng.

### 8.3. stream() khi cần chọn theo metadata của provider type

```java
ServiceLoader<Greeting> services = ServiceLoader.load(Greeting.class);
ServiceLoader.Provider<Greeting> selected = services.stream()
        .filter(p -> p.type().getSimpleName().startsWith("Alpha"))
        .findFirst()
        .orElseThrow();

Greeting instance = selected.get();
```

Đây là trích đoạn. `Provider.type()` giúp xem kiểu provider **trước** khi instantiate. Điều này hữu ích nếu cần chọn provider theo annotation/class metadata hoặc theo tên class, tránh khởi tạo mọi candidates tốn kém.

Nhưng:
- Provider type có thể vẫn phải được **load/verified**.
- Đọc annotation qua reflection có những chi phí và ràng buộc riêng.
- `type()` không nhất thiết trả class có public no-arg constructor.
- Với **named-module provider factory method**, `type()` trả **return type của provider()**, không nhất thiết trả class factory khai báo trong `provides`.

**Lưu ý quan trọng:** Duyệt `stream()` và gọi `Provider.get()` không nên được coi là cùng hành vi cache instance với `iterator()`. Thiết kế rõ ràng nơi nào sở hữu provider object.

## 9. Cache của ServiceLoader và ý nghĩa reload()

`ServiceLoader` duy trì cache các providers đã được tải trong quá trình duyệt. Với `iterator()`, những lần duyệt sau của **cùng đối tượng** trước tiên trả lại **các instances đã cache** theo thứ tự khởi tạo, rồi tiếp tục khám phá những provider còn lại.

`reload()`:
- Xóa cache của đối tượng `ServiceLoader`, cho phép những lần duyệt sau khám phá lại provider declarations.
- Có thể tạo **instance mới** từ cùng provider class, nếu được yêu cầu.
- Không xóa object vẫn còn references ở nơi khác.
- Không “unload” class khỏi JVM.
- Không đảm bảo nạp lại class bytes đã được defining loader định nghĩa trước.
- Không tự làm provider đã stop/đã hủy tài nguyên trở lại trạng thái hoạt động.

### Sau reload, iterator cũ còn dùng được không?

Không nên tiếp tục dùng iterator hoặc stream đã lấy từ `ServiceLoader` trước khi gọi `reload()`. Với iterator, `hasNext()` và `next()` có thể ném `ConcurrentModificationException` khi cache đã bị xóa. Hãy tạo **iterator/stream mới** sau khi reload.

### Đọc lại resource thay đổi

Nếu bạn thay file `META-INF/services` trong một nguồn mà loader có thể nhìn thấy, `reload()` có thể khiến ServiceLoader thử khám phá danh sách providers mới. Nhưng việc phản ánh tệp mới còn phụ thuộc cách ClassLoader/resource cache hoạt động; nếu loader đã đóng, hoặc JAR còn nằm trong cache, đơn giản `reload()` có thể không đạt mục tiêu hot deployment.

**Muốn reload một phiên bản class mới thật sự**, thường cần một defining ClassLoader mới, quản lý stop/start plugin và những references cũ. `ServiceLoader.reload()` không thay thế kiến trúc hot-reload.

## 10. Thread Context ClassLoader và phạm vi nhìn thấy provider

Overload:

```java
ServiceLoader.load(Greeting.class)
```

sử dụng **Thread Context ClassLoader (TCCL)** của thread hiện tại theo contract `ServiceLoader`. Điều này hữu ích khi framework nằm ở loader cha nhưng provider implementation chỉ có trong plugin loader con.

Overload explicit:

```java
ServiceLoader.load(Greeting.class, pluginLoader)
```

chọn `pluginLoader` trực tiếp. Đây là trích đoạn cần có biến `pluginLoader` và interface service hợp lệ.

### Mô hình host/plugin

```text
Application ClassLoader
   ├── service API: demo.spi.Greeting
   └── host framework
             │
             ▼ sử dụng TCCL hoặc explicit loader
Plugin URLClassLoader
   ├── demo.impl.AlphaGreeting
   └── META-INF/services/demo.spi.Greeting
```

Nếu framework chỉ hỏi loader của chính nó, nó có thể **không thấy** provider ở child. Đổi TCCL đúng scope hoặc truyền loader cụ thể sẽ giúp khám phá provider.

TCCL là trạng thái ambient trên thread. Khi đổi tạm thời, phải restore:

```java
// Trích đoạn trong code host:
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    ServiceLoader<Greeting> services =
            ServiceLoader.load(Greeting.class);
    services.findFirst().ifPresent(
            greeting -> System.out.println(greeting.message())
    );
} finally {
    thread.setContextClassLoader(previous);
}
```

**Không** gọi `Class.forName("demo.impl.AlphaGreeting")` rồi mong nó tự lấy TCCL: overload `Class.forName(String)` dùng loader của caller, không phải TCCL.

### Trong thread pool

Thread pool có worker được tạo **từ trước**. Việc main thread đổi TCCL lúc submit task không đảm bảo worker dùng TCCL mới. Nếu task cần provider của plugin, truyền loader tường minh hoặc dùng wrapper set/restore ngay trong worker thread.

Không để TCCL plugin lưu trên thread pool sau khi request kết thúc: nó vừa có thể gây nhầm provider, vừa giữ strong reference đến plugin ClassLoader, cản trở unloading khi redeploy.

## 11. JPMS: uses/provides trong named modules

Trên module path, service provider được đăng ký bằng `module-info.java` chứ không cần `META-INF/services`.

Tạo service contract:

```java
package svc.api;

public interface Greeting {
    String message();
}
```

Module API:

```java
module svc.api {
    exports svc.api;
}
```

Module consumer:

```java
module svc.host {
    requires svc.api;
    uses svc.api.Greeting;
}
```

Module provider:

```java
module svc.provider {
    requires svc.api;
    provides svc.api.Greeting
            with svc.provider.HelloGreeting;
}
```

Với các descriptors trên:
- `requires svc.api`: consumer/provider đọc service contract.
- `uses svc.api.Greeting`: consumer khai báo nó sử dụng service này; đây là điều kiện quan trọng khi caller nằm trong explicit named module.
- `provides ... with ...`: module provider khai báo implementation.
- Package `svc.provider` **không cần export** chỉ để ServiceLoader instantiate provider theo quy tắc modules.

Không cần ép mọi implementation package thành public API của provider module.

### Named-module provider constructor so với factory method

Đối với **explicit named module** có `provides`, provider có hai cách:

1. Provider class có **public no-arg constructor**, tạo object trực tiếp; class provider phải assignable sang service.
2. Provider class có **public static provider()** không tham số, với return type assignable sang service; ServiceLoader gọi factory method. Lúc này class chứa factory **không nhất thiết implements service**.

Ví dụ provider factory trong module:

```java
// Trích đoạn class trong explicit named provider module:
package svc.provider;

import svc.api.Greeting;

public final class GreetingFactory {
    public static Greeting provider() {
        return new HelloGreeting();
    }
}
```

Đây là **trích đoạn có dependencies**, không phải một chương trình độc lập. Descriptor thích hợp có thể dùng `provides svc.api.Greeting with svc.provider.GreetingFactory;`. Với module factory, `Provider.type()` trả **return type** của `provider()`, không bắt buộc là class `GreetingFactory`.

**Đặc biệt:** providers được liệt kê trong `META-INF/services` trên classpath, hoặc provider của **automatic module**, cần public no-arg constructor theo contract tương ứng; **provider factory method không được hỗ trợ cho các hình thức này như explicit named module provides**.

## 12. Ví dụ JPMS đầy đủ: lazy provider từ module descriptor

Ví dụ tiếp theo bổ sung minh họa module path chạy độc lập với ví dụ classpath.

### 12.1. Cấu trúc

```text
mod-spi/
└── src/
    ├── svc.api/
    │   ├── module-info.java
    │   └── svc/api/Greeting.java
    ├── svc.provider/
    │   ├── module-info.java
    │   └── svc/provider/HelloGreeting.java
    └── svc.host/
        ├── module-info.java
        └── svc/host/Main.java
```

Tạo đúng các files:

`src/svc.api/module-info.java`

```java
module svc.api {
    exports svc.api;
}
```

`src/svc.api/svc/api/Greeting.java`

```java
package svc.api;

public interface Greeting {
    String message();
}
```

`src/svc.provider/module-info.java`

```java
module svc.provider {
    requires svc.api;
    provides svc.api.Greeting with svc.provider.HelloGreeting;
}
```

`src/svc.provider/svc/provider/HelloGreeting.java`

```java
package svc.provider;

import svc.api.Greeting;

public final class HelloGreeting implements Greeting {
    static {
        System.out.println("Module provider initialized");
    }

    public HelloGreeting() {
        System.out.println("Module provider constructed");
    }

    @Override
    public String message() {
        return "hello JPMS";
    }
}
```

`src/svc.host/module-info.java`

```java
module svc.host {
    requires svc.api;
    uses svc.api.Greeting;
}
```

`src/svc.host/svc/host/Main.java`

```java
package svc.host;

import svc.api.Greeting;
import java.util.ServiceLoader;

public final class Main {
    public static void main(String[] args) {
        ServiceLoader<Greeting> services =
                ServiceLoader.load(Greeting.class);

        System.out.println("ServiceLoader created");
        Greeting provider = services.findFirst().orElseThrow();
        System.out.println("Provider message: " + provider.message());
    }
}
```

### 12.2. Biên dịch và chạy

Trong thư mục `mod-spi` với JDK 17+:

```bash
javac --module-source-path src -d mods src/svc.api/module-info.java src/svc.api/svc/api/Greeting.java src/svc.provider/module-info.java src/svc.provider/svc/provider/HelloGreeting.java src/svc.host/module-info.java src/svc.host/svc/host/Main.java
java --module-path mods --add-modules svc.provider --module svc.host/svc.host.Main
```

Dùng `--add-modules svc.provider` để đảm bảo provider module được đưa vào module graph của lượt chạy này. Chỉ đặt artifact trên module path **không phải luôn đủ** để module được resolve.

Stdout:

```text
ServiceLoader created
Module provider initialized
Module provider constructed
Provider message: hello JPMS
```

Ở đây module provider **không export package implementation**, nhưng ServiceLoader vẫn instantiate provider nhờ khai báo `provides`. Consumer chỉ cần service API. Static block/constructor chạy khi `findFirst()` lấy instance, không phải chỉ do tạo đối tượng ServiceLoader.

### 12.3. Nếu tạo plugin ModuleLayer riêng

Với modules được nạp vào child `ModuleLayer`, dùng:

```java
// Trích đoạn: layer đã tạo và Greeting.class là service API chung.
ServiceLoader<Greeting> services =
        ServiceLoader.load(layer, Greeting.class);
```

Overload này tìm providers trong layer và ancestor layers theo contract; **không tìm providers thuộc unnamed modules**. Cần đảm bảo API service được chia sẻ đúng defining loader/module giữa host và providers, nếu không có thể xảy ra lỗi type identity hoặc service configuration.

## 13. ServiceConfigurationError xảy ra lúc nào?

`ServiceLoader` có thể trì hoãn **cả lỗi** đến khi thực sự khám phá hoặc instantiate provider. Không được suy ra `ServiceLoader.load(...)` thành công nghĩa là metadata/provider hoàn toàn đúng.

Một số lỗi điển hình:
- File `META-INF/services/...` sai cú pháp, encoding hoặc I/O khi đọc.
- Provider binary name sai hoặc class file thiếu.
- Provider không assignable sang service interface/class.
- Provider trên classpath không có **public no-arg constructor**, là abstract/interface không phù hợp hoặc constructor ném exception.
- Explicit named-module provider factory method có signature không hợp lệ, trả `null` hoặc ném lỗi.
- Provider code không thể được load/link vì dependency/class version vấn đề.
- Named-module caller không có `uses` phù hợp hoặc service type không accessible.

Khi gặp vấn đề, `ServiceLoader` dùng **`ServiceConfigurationError`**, một subclass của `Error`, thay vì trả “không có provider” trong mọi trường hợp.

### Bắt lỗi tại ranh giới discovery

```java
// Trích đoạn trong ứng dụng có Greeting service.
try {
    for (Greeting g : ServiceLoader.load(Greeting.class)) {
        System.out.println(g.message());
    }
} catch (java.util.ServiceConfigurationError error) {
    System.err.println("Broken service registration: " + error);
    Throwable cause = error.getCause();
    if (cause != null) {
        cause.printStackTrace();
    }
}
```

Đây là trích đoạn; cần interface Greeting hợp lệ và import ServiceLoader. Có thể phát sinh lỗi ở `Iterator.hasNext()`, `next()` hoặc xử lý `stream()`; vì vậy xử lý lỗi phải bao **đúng thao tác duyệt**, không chỉ bao `ServiceLoader.load()`.

Không catch `Throwable` rồi bỏ qua để giả vờ hệ thống có 0 providers. Nếu metadata khai báo provider nhưng sai, đó là **cấu hình lỗi**, khác việc không khai báo provider.

### Có nên tự động bỏ qua provider hỏng rồi lấy provider tiếp?

Tài liệu API nói iterator sau lỗi có thể **cố gắng** tìm provider tiếp theo nhưng **không đảm bảo phục hồi**. Tùy yêu cầu hệ thống, có thể fail fast và báo lỗi cấu hình, hoặc triển khai chính sách retry có kiểm soát. Không viết vòng `while(true)` catch mọi lỗi rồi tiếp tục vô hạn.

## 14. Thứ tự providers và cấu hình chọn implementation

`ServiceLoader.findFirst()` thuận tiện nhưng “first” phụ thuộc source provider declarations và thứ tự lookup, đặc biệt trong các tình huống:
- Nhiều file `META-INF/services` trên classpath.
- Nhiều named modules cùng cung cấp một service.
- Nhiều modules nằm trong cùng `ModuleLayer`.
- ClassLoader cha/con có providers riêng.

Trên classpath, theo contract của ServiceLoader, thứ tự dựa vào `ClassLoader.getResources` cùng thứ tự các tên provider trong từng file. Trong một named module, thứ tự các provider được khai báo trong descriptor được giữ, nhưng **thứ tự giữa modules trong cùng layer không được xác định**.

Vì vậy, nếu ứng dụng cần một provider có **ưu tiên nghiệp vụ** cao nhất:
- Đừng phụ thuộc mù quáng vào `findFirst()` trên một module graph thay đổi.
- Khám phá candidate types qua `stream()`.
- Đọc annotation/metadata priority đã xác định, kiểm tra suitability.
- Chọn theo quy tắc tường minh và chỉ instantiate provider được chọn nếu có thể.
- Nếu sự ưu tiên không thể được xác định mà không instantiate, định nghĩa vòng đời/tài nguyên phù hợp và không gọi constructor vô tội vạ.

Ví dụ chọn theo class name là demo, không phải chiến lược cấu hình ưu tiên đủ tốt cho sản phẩm lớn.

## 15. Provider không nhất thiết là singleton

`ServiceLoader` tạo providers theo nhu cầu; điều này **không tự cho một hợp đồng singleton toàn ứng dụng**.

- Một `ServiceLoader` instance có cache các providers đã được iterator instantiate.
- `ServiceLoader.load(...)` được gọi lại tạo **ServiceLoader object mới** với cache riêng.
- `reload()` có thể làm một ServiceLoader hiện có instantiate lại provider.
- `Provider.get()` phải được xem theo hợp đồng “returns an instance”, **không nên giả định identity ổn định**.
- Static initializer của class provider không nhất thiết chạy lại khi object provider mới được tạo.

Nếu provider mở connection pool, tạo thread, giữ socket hoặc đăng ký listener trong constructor, vòng đời phải được quản lý **ở cấp ứng dụng**. ServiceLoader không có `close()` tự đóng tất cả provider instances. Khi service interface cần cleanup, có thể thiết kế:

```java
// Trích đoạn thiết kế hợp đồng:
public interface ManagedService extends AutoCloseable {
    String name();

    @Override
    void close() throws Exception;
}
```

Đây là interface đầy đủ có thể biên dịch trong `ManagedService.java`, nhưng **không đủ tạo ứng dụng SPI độc lập**. Consumer phải gọi `close()` cho provider instances thực sự sở hữu, theo thứ tự và điều kiện phù hợp. Không tự close object dùng chung nếu provider được quản lý bởi container khác.

## 16. ServiceLoader có thread-safe không?

**Không.** Tài liệu `ServiceLoader` nêu các instance **không an toàn khi nhiều thread truy cập đồng thời**. Cache, iterator, stream và `reload()` của một ServiceLoader có trạng thái mutable.

Hệ quả:
- Không chia sẻ một đối tượng `ServiceLoader` cho nhiều thread duyệt và reload đồng thời mà không có phối hợp.
- Nếu nhiều task cần providers, cân nhắc tạo `ServiceLoader` riêng theo scope/thread hoặc dùng khóa ở tầng điều phối.
- Không gọi `reload()` trong lúc thread khác đang dùng iterator/stream của cùng đối tượng.
- Một provider instance có thread-safe hay không là **hợp đồng riêng của implementation**, không được suy ra từ ServiceLoader.
- ClassLoader có thể parallel-capable nhưng **không làm ServiceLoader instance trở thành thread-safe**.

Ví dụ đóng gói đồng bộ ở mức ứng dụng có thể serialize thao tác khám phá/reload, nhưng còn phải xem provider methods có gọi trở lại loader/callback và tránh deadlock. Chỉ dùng `synchronized` bừa trên toàn hệ có thể tạo điểm nghẽn mới.

## 17. Provider discovery có side effects không?

Có thể. Dù ServiceLoader được thiết kế lazy, thời điểm khám phá vẫn có thể thực hiện:
- Tìm/đọc provider config resource qua ClassLoader.
- Tải provider class và dependency khi cần.
- Liên kết/kiểm tra runtime types.
- Gọi constructor hoặc factory method khi instantiate.
- Chạy static initialization khi active use.
- Thực hiện I/O tùy custom ClassLoader (remote URLs, network JAR).
- Gây exception/error khi metadata/bytecode bất hợp lệ.

Do đó không nói “`ServiceLoader.load()` an toàn tuyệt đối và chẳng làm gì” theo nghĩa không thể có bất kỳ ngoại lệ hoặc chi phí nào. Nó **tạo service loader object**, còn phần lớn discovery/instantiate được trì hoãn. Kết quả cụ thể của các thao tác sau tùy môi trường module/loader.

Nếu muốn xây ứng dụng có startup latency thấp, dùng `stream` để lọc khi có thể và tránh instantiate tất cả providers chỉ để đọc tên. Nhưng việc lọc theo `Provider.type()` vẫn có thể load class, nên profiling thực tế là cần thiết.

## 18. Ví dụ lỗi điển hình: provider class có nhưng không tìm thấy

Trường hợp:
- `demo.impl.AlphaGreeting.class` tồn tại trong một thư mục.
- Host dùng `ServiceLoader.load(Greeting.class)`.
- Không có file `META-INF/services/demo.spi.Greeting` trong output hay module descriptor `provides`.

**Kết quả có thể là không tìm thấy provider**, dù bạn gọi `Class.forName("demo.impl.AlphaGreeting")` thành công. `ServiceLoader` không tự quét tất cả classes trong classpath để tìm implementations.

Cách chẩn đoán:

1. Kiểm tra interface service binary name **chính xác**.
2. Kiểm tra tệp `META-INF/services/...` đã được copy vào JAR/out directory chưa.
3. Kiểm tra contents có provider binary names không, encoding UTF-8, không thêm `.class`.
4. Kiểm tra provider class có public no-arg constructor nếu triển khai trên classpath.
5. Kiểm tra loader dùng để tìm configuration và class: explicit loader hay TCCL.
6. Nếu named modules, kiểm tra `uses`/`provides`, module có resolved không, đúng layer không.
7. Nếu cùng binary name service interface được định nghĩa ở hai loaders khác nhau, provider có thể không assignable với service type của consumer.
8. Phân biệt **không có provider** (Optional.empty) và **provider bị lỗi** (ServiceConfigurationError).

Với JAR provider:

```bash
jar --list --file provider.jar
```

Tìm entries tương ứng, ví dụ:

```text
META-INF/services/demo.spi.Greeting
demo/impl/AlphaGreeting.class
```

Đây là hai entries cần tìm, không phải toàn bộ stdout. Đừng kiểm tra chỉ source tree: xác minh **artifact cuối cùng mà JVM thực sự chạy**.

## 19. Lỗi trùng API qua nhiều ClassLoaders

Một lỗi hệ plugin thường gặp:

```text
Host ClassLoader
  └── demo.spi.Greeting       [runtime class A]

Plugin ClassLoader
  ├── demo.spi.Greeting       [runtime class B]
  ├── demo.impl.AlphaGreeting [implements B]
  └── META-INF/services/demo.spi.Greeting

Class A != Class B
```

Nếu provider implements interface B do plugin loader định nghĩa, nhưng host hỏi `ServiceLoader.load(ClassA, pluginLoader)`, provider có thể **không assignable** sang service class A. Đây là vấn đề **class identity**, không phải chỉ thiếu tên class trong configuration.

Giải pháp phổ biến:
- Đặt service API trong loader cha/shared module.
- Plugin loader dùng chính API đó, không tự định nghĩa lại interface.
- Provider implementation có thể nằm trong loader riêng.
- Kiểm tra `providerClass.getClassLoader()` và `serviceClass.getClassLoader()`; đừng so mỗi chuỗi tên.

Nếu custom loader child-first cho mọi package, cần whitelist service API dùng chung để parent-first, tránh định nghĩa trùng contract.

## 20. Reload và vòng đời ClassLoader khi đổi plugin

Có ba lớp vòng đời khác nhau:

| Thành phần | Ý nghĩa của thao tác |
|---|---|
| `ServiceLoader.reload()` | Xóa cache provider discovery/instances của đối tượng ServiceLoader |
| `URLClassLoader.close()` | Đóng tài nguyên tìm class/resource mới của URLClassLoader |
| JVM class unloading | Có thể thu gom runtime class/loader khi không còn reachable và GC thực hiện phù hợp |

Chúng không thay thế cho nhau.

Một plugin manager có thể thực hiện:

```text
stop requests
  ↓
finish/cancel in-flight provider operations
  ↓
unregister callbacks, close provider-owned resources
  ↓
restore TCCL, clear caches/references
  ↓
discard old ServiceLoader and provider instances
  ↓
close old URLClassLoader (if owned)
  ↓
build a new ClassLoader/ModuleLayer for new plugin generation
  ↓
create new ServiceLoader and discover providers again
```

Đừng dùng `ServiceLoader.reload()` như giải pháp hot deployment duy nhất khi bytecode đã được định nghĩa bởi loader cũ. Tương tự, `URLClassLoader.close()` không ép class cũ biến mất và không dừng thread do provider đã tạo.

## 21. Những hiểu nhầm phổ biến

| Nhận định sai | Điều đúng |
|---|---|
| `ServiceLoader.load` tạo tất cả providers ngay | API được thiết kế lazy: discovery/instantiation theo nhu cầu |
| `iterator()` tạo tất cả provider objects | Việc tạo xảy ra khi duyệt, nhất là `next()` |
| `hasNext()` luôn miễn phí, không thể lỗi | Nó có thể tìm config, tải/kiểm tra providers và ném ServiceConfigurationError |
| `stream()` trả Stream<S> | Nó trả `Stream<ServiceLoader.Provider<S>>` |
| `Provider.type()` và `Provider.get()` giống nhau | Một cái trả type, một cái trả instance |
| `Provider.get()` luôn là singleton cache | Không được giả định instance identity toàn cục |
| `findFirst()` luôn trả provider hoặc empty | Metadata hỏng có thể gây ServiceConfigurationError |
| Có implementation .class là đủ | Classpath cần `META-INF/services`, named module dùng `provides` |
| ServiceLoader luôn lấy loader của caller | `load(S.class)` dùng TCCL; có explicit overload |
| Provider class phải export package trong JPMS | `provides` có thể đăng ký implementation không export |
| Classpath provider dùng static provider() factory | Factory provider method chỉ theo contract explicit named module |
| `reload()` unload class mới thay bytes | Nó chỉ xóa provider cache |
| ServiceLoader tự đóng provider sau khi dùng | Ứng dụng/container phải quản lý lifecycle riêng |
| ServiceLoader thread-safe | ServiceLoader instances không an toàn để dùng concurrent không phối hợp |
| `findFirst()` là business priority ổn định mọi môi trường | Thứ tự phụ thuộc cấu hình JAR/module/loader |
| `ServiceConfigurationError` giống thiếu provider | Đây là lỗi cấu hình, không phải Optional.empty |

## 22. Câu hỏi tự kiểm tra

1. **Service và provider khác nhau thế nào?** Service là interface/class hợp đồng; provider là implementation được tìm lúc chạy.
2. **`ServiceLoader.load(...)` có khởi tạo toàn bộ providers ngay không?** Không; discovery và instantiation lazy.
3. **`iterator().hasNext()` có thể gây I/O hoặc lỗi không?** Có, khi tìm metadata/providers.
4. **`iterator().next()` trả gì?** Provider instance thuộc service type.
5. **`stream()` trả Stream của kiểu nào?** `Stream<ServiceLoader.Provider<S>>`.
6. **`Provider.type()` và `get()` khác nhau?** Type trả Class của provider type, get trả instance.
7. **`findFirst()` có đảm bảo không ném ServiceConfigurationError không?** Không.
8. **Đặt file class provider trong JAR mà thiếu service configuration thì sao?** Class có thể tồn tại nhưng ServiceLoader không tự phát hiện nó qua classpath SPI.
9. **Tên file trong META-INF/services là gì?** Binary name của service interface/class.
10. **Provider trên classpath thường cần constructor nào?** Public no-arg constructor.
11. **Named module consumer khai báo gì?** `uses service.Type;` cùng read dependency khi cần.
12. **Named module provider khai báo gì?** `provides service.Type with provider.Type;`.
13. **Module provider có cần export package implementation không?** Không nhất thiết cho ServiceLoader.
14. **`ServiceLoader.load(S.class)` dùng loader nào?** TCCL theo contract.
15. **`reload()` có chạy lại static initializer của class đã init thành công không?** Không.
16. **Sau reload, dùng iterator cũ có an toàn không?** Không; tạo iterator/stream mới.
17. **ServiceLoader có tự cleanup provider resources không?** Không.
18. **Hai class service cùng tên nhưng do hai loaders khác nhau định nghĩa có tương thích không?** Không tự động.
19. **Muốn hot reload bytecode mới, chỉ gọi reload() đủ không?** Không; thường cần loader generation mới.
20. **Lỗi metadata hỏng nên xử lý như gì?** `ServiceConfigurationError`, khác không có provider.

## Tài liệu chuẩn

- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): provider discovery, lazy timing, iterator/stream, provider cache, reload, ordering, module/classpath formats, lỗi ServiceConfigurationError.
- [Java SE 17 — ServiceLoader.Provider](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.Provider.html): `type()` và `get()`.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): binary names, parent delegation và loader identity.
- [Java SE 17 — ModuleLayer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html): plugin modules và module layer.
- [JLS 17 — §7.7 Module Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html): `uses`, `provides` và module access.
- [JLS 17 — §12.2–12.4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): loading, linking, initialization.

**Kết luận:** `ServiceLoader` tách **đăng ký/khám phá provider** khỏi **tạo provider instance**. `iterator()` instantiate khi duyệt, `findFirst()` chỉ lấy provider đầu tiên, `stream()` hỗ trợ xem type trước khi gọi `Provider.get()`. Khi làm SPI hoặc hệ plugin, phải chọn đúng ClassLoader/ModuleLayer, phân biệt “không có provider” với “provider cấu hình sai”, và quản lý cache, concurrency cùng vòng đời provider thay vì coi `reload()` là class unloading.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-loading-007">Iterator, stream và ServiceLoader.Provider</a>

<details>
<summary>Xem chi tiết</summary>

# Iterator, stream và ServiceLoader.Provider trong Java

`ServiceLoader<S>` là cơ chế **Service Provider Interface (SPI)** chuẩn của Java để ứng dụng tìm và sử dụng implementation của một service mà không cần viết cứng tên từng lớp triển khai. Có hai con đường quan trọng để duyệt kết quả tìm thấy:

- **`iterator()`** cung cấp `Iterator<S>`: mỗi phần tử là **instance dịch vụ `S`**. Khi duyệt, ServiceLoader nạp và **khởi tạo provider theo nhu cầu**.
- **`stream()`** (từ Java 9) cung cấp `Stream<ServiceLoader.Provider<S>>`: mỗi phần tử là **một mô tả provider**, có thể xem `type()` trước và chỉ tạo instance khi gọi `get()`.

Khác biệt này không chỉ là lựa chọn cú pháp `for` hay Stream API. Nó quyết định **thời điểm constructor/provider factory được gọi**, khả năng **lọc dựa trên metadata trước khi tạo object**, cách **cache và reload**, cách xử lý lỗi discovery/instantiation, và cách kết hợp SPI với class loader, thread context class loader (TCCL) và Java Platform Module System (JPMS).

Bài này tập trung chính xác vào `Iterator`, `stream` và `ServiceLoader.Provider`, có ví dụ **Java 17 biên dịch/chạy được và stdout đã kiểm tra**, cùng những giới hạn quan trọng khi dùng trong hệ plugin.

## 1. ServiceLoader duyệt cái gì?

Một **service** là interface hoặc abstract class biểu diễn hợp đồng. **Provider** là implementation có thể được framework hoặc ứng dụng tìm thấy theo metadata SPI.

~~~text
                         Service type
                         codec.api.Codec
                               |
                +--------------+---------------+
                |                              |
        Provider A                        Provider B
        codec.impl.Alpha                  codec.impl.Beta
                |                              |
                +--------------+---------------+
                               |
                               v
                     ServiceLoader<Codec>
                         /            \
                        v              v
               iterator()             stream()
               Iterator<Codec>        Stream<Provider<Codec>>
                        |              |
                        v              v
                  Codec instance    Provider descriptor
                                         |
                               +---------+--------+
                               |                  |
                             type()              get()
                               |                  |
                               v                  v
                         Class<? extends    Codec instance
                            Codec>
~~~

Trong code:

~~~java
ServiceLoader<Codec> loader = ServiceLoader.load(Codec.class);

Iterator<Codec> objects = loader.iterator();

Stream<ServiceLoader.Provider<Codec>> descriptions =
        loader.stream();
~~~

Đoạn trên chỉ là **fragment kiểu API**: để biên dịch cần khai báo `Codec` và các `import` tương ứng. Một chương trình hoàn chỉnh có ở mục 4.

**`ServiceLoader<S>` implements `Iterable<S>`**, nên có thể dùng enhanced-for:

~~~java
for (Codec codec : loader) {
    System.out.println(codec.id());
}
~~~

Nhưng chú ý: enhanced-for gọi `iterator()` và nhận **instance `Codec`**, không phải `ServiceLoader.Provider<Codec>`.

Ngược lại, phần tử của `loader.stream()` là **`Provider<Codec>`**, chưa phải object `Codec`. Vì vậy:

~~~java
loader.stream()
        .map(ServiceLoader.Provider::get)
        .forEach(codec -> System.out.println(codec.id()));
~~~

Lệnh `map(...::get)` chính là bước lấy hoặc tạo instance từ mỗi provider descriptor. Không thể gọi trực tiếp `codec.id()` trên biến kiểu `Provider<Codec>` trước khi `get()`.

## 2. So sánh iterator, stream, Provider.type và Provider.get

| API | Kiểu giá trị | Điều quan trọng |
| --- | --- | --- |
| `loader.iterator()` | `Iterator<S>` | Duyệt **service instances**; mỗi provider mới được định vị và khởi tạo lười khi cần. |
| `for (S s : loader)` | `S` | Cú pháp tiện của `Iterable`; về bản chất dựa trên `iterator()`. |
| `loader.findFirst()` | `Optional<S>` | Lấy và khởi tạo provider đầu tiên nếu có; hữu ích cho fallback, nhưng “đầu tiên” phụ thuộc thứ tự discovery. |
| `loader.stream()` | `Stream<ServiceLoader.Provider<S>>` | Duyệt **descriptors**, có thể lọc provider trước khi tạo instance. |
| `provider.type()` | `Class<? extends S>` | Trả type của provider theo hợp đồng API, không trả instance dịch vụ. |
| `provider.get()` | `S` | Trả instance của provider; có thể gọi constructor hoặc provider factory. |

Đừng viết code với giả định rằng `stream()` trả `Stream<S>`. Kiểu đúng là:

~~~java
Stream<ServiceLoader.Provider<MyService>> descriptors =
        ServiceLoader.load(MyService.class).stream();
~~~

Đây là fragment, cần khai báo `MyService`. Nếu muốn `Stream<MyService>`, có thể biến đổi bằng `map(Provider::get)`; song điều đó **kích hoạt instantiation** khi stream được tiêu thụ.

### Khác nhau ở thời điểm thực thi

Khi tạo một `ServiceLoader` hay chỉ gọi `stream()`, chưa cần tạo tất cả provider instances. Stream pipeline có tính **lazy**: thao tác trung gian `filter`, `map`, `sorted` thường không tự chạy cho tới khi có terminal operation như `findFirst()`, `toList()`, `count()` hoặc `forEach()`.

~~~text
ServiceLoader.load(...)                nhận một loader cho service
        |
        +-- iterator()                 tạo iterator
        |      |
        |      +-- next()              cần một S, có thể instantiate
        |
        +-- stream()                   tạo Stream<Provider<S>>
               |
               +-- filter(p -> p.type()...)
               |       (đánh giá khi duyệt)
               |
               +-- findFirst()
                       |
                       v
                  Provider<S>
                       |
                       +-- get()        tạo/lấy instance S
~~~

Lưu ý chính xác: **“lazy” không có nghĩa toàn bộ metadata/discovery không thể phát sinh lỗi cho tới khi gọi `get()`**. Bản thân việc định vị, đọc metadata SPI và load provider type trong lúc tiêu thụ stream cũng có thể ném `ServiceConfigurationError`. Điểm mạnh của `Provider.type()` là **có thể quyết định có cần tạo instance hay không trước khi gọi `get()`**.

## 3. ServiceLoader.Provider<S> là kiểu gì?

`ServiceLoader.Provider<S>` là interface lồng trong `ServiceLoader` và **extends `Supplier<S>`**. Nó biểu diễn provider do ServiceLoader đã định vị và cung cấp hai phương thức trung tâm:

~~~java
Class<? extends S> type();
S get();
~~~

Đây là **chữ ký khái quát**, không phải khai báo interface riêng để tạo trong project.

### Provider.type(): xem kiểu mà chưa instantiate

Một tình huống điển hình là lọc provider theo annotation, tên class hoặc đặc điểm type:

~~~java
Optional<ServiceLoader.Provider<Codec>> candidate =
        loader.stream()
              .filter(p -> p.type()
                            .isAnnotationPresent(FastCodec.class))
              .findFirst();
~~~

Đây là fragment: `Codec`, annotation `FastCodec` và `loader` cần được khai báo. Việc kiểm tra `p.type()` chỉ yêu cầu metadata type; **không yêu cầu gọi constructor của service object**. Tuy vậy, JVM vẫn có thể phải nạp class và truy vấn metadata/annotation, nên không thể nói “type() hoàn toàn không làm gì” hoặc không có side effects từ các lớp liên quan.

Nếu filter tìm được đúng provider:

~~~java
Codec codec = candidate.orElseThrow().get();
~~~

Lúc này `get()` mới lấy instance. Sử dụng filter trước `get()` giúp tránh tạo object của provider không phù hợp, nhất là khi constructor tốn tài nguyên hoặc plugin có nhiều implementation.

### Provider.get(): tạo instance theo cách provider khai báo

Với provider thông thường trên classpath hoặc một provider class được khai báo trong named module, `get()` có thể gọi constructor/provider creation mechanism. Đối với named module, Java hỗ trợ **provider factory method** là public static no-arg `provider()` theo các điều kiện của `ServiceLoader` API.

~~~java
// Fragment trong named module; class triển khai service ở bên ngoài.
public final class CodecProviderFactory {
    public static Codec provider() {
        return new AlphaCodec();
    }
}
~~~

Đây **không phải phần của ví dụ biên dịch ở mục 4**. Trong named module, descriptor có thể khai báo một provider factory class phù hợp bằng `provides ... with ...`. Điểm cần nhớ:

- Nếu dùng factory method, `Provider.type()` trả **return type của `provider()`**, không nhất thiết là `CodecProviderFactory.class`.
- `Provider.get()` thực thi cơ chế tạo instance theo provider tương ứng và có thể ném `ServiceConfigurationError` nếu không tạo được object hợp lệ.
- **Đừng mặc định gọi `get()` nhiều lần luôn trả cùng object.** `Provider<S>` là một supplier và API không nên bị hiểu như một singleton registry.
- Cơ chế factory provider trong named module không nên đánh đồng với việc ghi tên một class chỉ có factory (không có constructor public no-args) vào `META-INF/services` trên classpath. Classpath provider-configuration cần implementation có constructor provider phù hợp.

Do đó, nếu lọc dựa trên annotation đặt trên **factory class**, `p.type().isAnnotationPresent(...)` có thể không tìm thấy annotation ấy: `type()` có thể là **return type** của factory method, không phải class chứa method.

## 4. Chương trình Java 17 hoàn chỉnh: kiểm tra laziness, cache và reload

Ví dụ dùng một **named module** `codec.demo`, bên trong khai báo service `codec.api.Codec` và một provider `codec.impl.Alpha`. Để đo số lần khởi tạo, constructor provider tăng biến đếm `Probe.created`.

Cách làm có một ưu điểm: **chỉ một provider** nên không phụ thuộc thứ tự discovery giữa nhiều module hoặc nhiều JAR. Output do đó có thể kiểm tra chính xác.

Tạo **năm file** theo cấu trúc:

~~~text
spi-demo/
└── src/
    └── codec.demo/
        ├── module-info.java
        └── codec/
            ├── api/
            │   ├── Codec.java
            │   └── Probe.java
            ├── impl/
            │   └── Alpha.java
            └── app/
                └── Main.java
~~~

### File 1 — src/codec.demo/module-info.java

~~~java
module codec.demo {
    exports codec.api;

    uses codec.api.Codec;

    provides codec.api.Codec with codec.impl.Alpha;
}
~~~

Giải thích:

- `exports codec.api`: công khai package hợp đồng khi module khác cần dùng service type.
- `uses codec.api.Codec`: cho biết code trong named module này tiêu thụ service `Codec`.
- `provides codec.api.Codec with codec.impl.Alpha`: đăng ký một provider cho service.
- Package `codec.impl` **không cần export**: ServiceLoader có cơ chế instantiate provider theo hợp đồng JPMS, không đòi ta công khai implementation package cho mọi module.

### File 2 — src/codec.demo/codec/api/Codec.java

~~~java
package codec.api;

public interface Codec {
    String id();
}
~~~

`Codec` là API contract mà caller sử dụng. Trong hệ nhiều module, các provider phải implements **đúng định nghĩa interface này**, không chỉ một bản khác có cùng binary name.

### File 3 — src/codec.demo/codec/api/Probe.java

~~~java
package codec.api;

public final class Probe {
    private Probe() { }

    public static int created;
}
~~~

`Probe.created` dùng để đo lúc constructor provider được gọi. Đây là biến tĩnh phục vụ ví dụ; ứng dụng production không nên dùng một counter mutable không đồng bộ để ghi nhận khởi tạo provider qua nhiều thread.

### File 4 — src/codec.demo/codec/impl/Alpha.java

~~~java
package codec.impl;

import codec.api.Codec;
import codec.api.Probe;

public final class Alpha implements Codec {
    public Alpha() {
        Probe.created++;
    }

    @Override
    public String id() {
        return "alpha";
    }
}
~~~

Mỗi lần `new Alpha()` thành công, constructor tăng counter lên một. `ServiceLoader` sẽ tạo instance khi được yêu cầu theo đường iterator hoặc `Provider.get()`.

### File 5 — src/codec.demo/codec/app/Main.java

~~~java
package codec.app;

import codec.api.Codec;
import codec.api.Probe;
import java.util.Iterator;
import java.util.ServiceLoader;

public final class Main {
    public static void main(String[] args) {
        ServiceLoader<Codec> loader =
                ServiceLoader.load(Codec.class);

        System.out.println("Initial count: " + Probe.created);

        ServiceLoader.Provider<Codec> provider = loader.stream()
                .filter(p -> p.type().getSimpleName().equals("Alpha"))
                .findFirst()
                .orElseThrow();

        System.out.println("After type filter: " + Probe.created);

        Codec selected = provider.get();

        System.out.println("After Provider.get: " + Probe.created);
        System.out.println("Selected id: " + selected.id());

        // Dùng ServiceLoader KHÁC để quan sát cache của iterator riêng.
        ServiceLoader<Codec> other =
                ServiceLoader.load(Codec.class);

        Iterator<Codec> firstIterator = other.iterator();
        Codec first = firstIterator.next();

        System.out.println("After iterator.next: " + Probe.created);

        Codec again = other.iterator().next();
        System.out.println("Iterator reused instance: " + (first == again));

        other.reload();

        Codec fresh = other.iterator().next();
        System.out.println("After reload next: " + Probe.created);
        System.out.println("Fresh after reload: " + (first != fresh));
    }
}
~~~

Chạy từ thư mục `spi-demo` bằng **JDK 17 trở lên**:

~~~bash
javac --release 17 -d out --module-source-path src -m codec.demo
java --module-path out -m codec.demo/codec.app.Main
~~~

**Stdout chính xác** đã được đối chiếu bằng biên dịch và chạy với JDK hỗ trợ `--release 17`:

~~~text
Initial count: 0
After type filter: 0
After Provider.get: 1
Selected id: alpha
After iterator.next: 2
Iterator reused instance: true
After reload next: 3
Fresh after reload: true
~~~

### Giải thích từng dòng

1. `ServiceLoader.load(Codec.class)` tạo loader service; chưa có provider instance nào. `Probe.created` ban đầu là `0`.
2. `loader.stream()` tạo một stream của `Provider<Codec>`. Biểu thức `filter(p -> p.type()...)` được chạy khi `findFirst()` tiêu thụ pipeline, nhưng **không gọi `p.get()`**. Bởi thế counter vẫn bằng `0` sau bước lọc.
3. `provider.get()` trả một `Codec` thực tế, constructor của `Alpha` chạy và tăng `Probe.created` thành `1`.
4. Method `id()` trả `alpha`.
5. `other` là **một ServiceLoader khác**. Lệnh `other.iterator().next()` lần đầu cần một service instance và tạo thêm `Alpha`, counter thành `2`.
6. `other.iterator().next()` ở lần gọi kế tiếp trả lại provider instance đã được **cache trong chính `other`**, nên so tham chiếu `first == again` cho `true`. Không có constructor thứ ba trước lúc reload.
7. `other.reload()` xóa các provider cache của `other`. Lần `other.iterator().next()` tiếp theo tìm/nạp lại provider, tạo object mới; counter thành `3` và `first != fresh` là `true`.

**Giới hạn có chủ đích của ví dụ:** Chỉ một provider, nên demo không dựa vào thứ tự nạp nhiều provider. Việc `Provider.get()` tạo instance ở đây được quan sát cho **một lần gọi**; không từ đó suy rằng `Provider.get()` có contract cache giống iterator. Sau `reload`, các references `first`/`again` vẫn trỏ object cũ; reload chỉ xóa cache của ServiceLoader, **không hủy object đã được caller giữ**.

## 5. Caching: khác nhau giữa iterator và stream

ServiceLoader tìm provider **lazily** và duy trì những cache phục vụ các đường duyệt.

### Với iterator()

Theo API, mỗi `iterator()` trước hết trả những **provider instances đã được cache bởi những lần duyệt iterator trước** theo thứ tự đã tạo, sau đó mới tìm và tạo thêm provider còn lại khi cần. Vì vậy, gọi lại `loader.iterator()` không có nghĩa chương trình sẽ khởi tạo lại mọi provider từ đầu.

~~~java
ServiceLoader<Codec> services = ServiceLoader.load(Codec.class);

Codec a = services.iterator().next();
Codec b = services.iterator().next();

System.out.println(a == b);
~~~

Đây là **fragment phụ thuộc SPI hợp lệ**. Nếu chỉ có một provider như chương trình phần 4, dòng in là `true`; nếu có nhiều provider, vẫn là phần tử cached đầu tiên khi chưa gọi `reload`.

Nhưng **cache không làm `ServiceLoader` trở thành singleton factory toàn cục**: cache thuộc từng đối tượng `ServiceLoader`, không phải tất cả instances cho service ấy. Một `ServiceLoader.load(...)` khác có cache riêng.

### Với stream()

`stream()` duyệt các **`Provider<S>` descriptors**. Một lần xử lý stream có thể định vị provider và ghi nhận thông tin vào cache riêng của loader cho đường stream. `stream()` không yêu cầu gọi constructor chỉ để cung cấp `Provider.type()`.

Ví dụ:

~~~java
List<String> types = loader.stream()
        .map(p -> p.type().getName())
        .sorted()
        .toList();
~~~

Đây là fragment yêu cầu `List` và `loader` đã khai báo. Nó lấy **tên type**, không tự instantiate service object chỉ để thực thi `map(...type...)`. `sorted()` đòi tiêu thụ các descriptors cần thiết để sắp xếp, nên có thể buộc discovery của **toàn bộ** provider, mặc dù không gọi `get()`.

Nếu sau đó viết:

~~~java
List<Codec> instances = loader.stream()
        .map(ServiceLoader.Provider::get)
        .toList();
~~~

đây cũng là fragment. Bây giờ terminal operation tiêu thụ stream và **gọi `get()`** cho những phần tử được đi qua, nên có thể tạo provider objects.

### Không nhầm hai loại cache và identity

- **Iterator cache** gắn với những provider **instances** mà `iterator()` đã thực sự trả.
- **Stream cache** gắn với provider descriptions được định vị qua `stream()`; đừng đồng nhất nó với một singleton instance cache.
- **`Provider.get()` không hứa luôn trả đúng object như lần gọi `get()` trước**; không tự so sánh identity rồi dùng làm contract ứng dụng.
- Sau `reload()`, các cache được xóa và lần duyệt tiếp theo discovery lại; các object, streams hoặc descriptors mà **code bên ngoài còn giữ** không tự biến mất.
- `ServiceLoader` được tài liệu mô tả **không an toàn để dùng đồng thời tùy tiện trên nhiều thread**. Nếu hệ plugin nhiều thread cùng khám phá/reload, cần thiết kế đồng bộ/ownership rõ ràng hoặc dùng instance ServiceLoader theo context phù hợp.

## 6. reload(): dùng khi nào, tránh dùng sai khi nào?

`reload()` xóa các provider cache của **chính đối tượng ServiceLoader**:

~~~java
ServiceLoader<Codec> services = ServiceLoader.load(Codec.class);

// ... duyệt providers ...

services.reload();

// Sử dụng iterator() hoặc stream() MỚI để thấy cache đã xóa.
~~~

Đây là fragment. Những trường hợp có thể cần reload:

- Hệ thống phát hiện provider artifact mới mà **class loader được chọn đã có khả năng nhìn thấy**.
- Ứng dụng chủ động yêu cầu discovery lại metadata.
- Test có thay đổi nguồn provider được loader nhìn thấy và muốn reset cache giữa các lần duyệt.

**Điểm không được suy diễn từ `reload()`:**

1. Nó **không tự tạo class loader mới**, không thêm URL/JAR vào loader, không tạo module layer mới và không thay các quan hệ `requires`/`exports`/`opens`.
2. Nó **không unload** những Class hoặc service objects đã tạo trước đó.
3. Nó không bảo đảm sẽ tìm thấy provider mới nếu class loader/module layer hiện tại **không thể nhìn thấy** provider đó.
4. Nó không chuyển định nghĩa `Codec` đang được consumer dùng sang interface cùng tên của loader khác.
5. Những **iterator và stream đã tạo trước `reload()` nên bị bỏ**, không dùng tiếp. API có cơ chế fail-fast và có thể ném `ConcurrentModificationException` khi những iterator/stream cũ được tiêu thụ sau khi cache bị xóa.

Ví dụ xử lý lỗi đúng về vòng đời:

~~~java
Iterator<Codec> oldIterator = services.iterator();
services.reload();

// Đừng tiếp tục oldIterator: lấy iterator mới.
Iterator<Codec> newIterator = services.iterator();
~~~

Đây là fragment yêu cầu biến `services` tồn tại; lời gọi `newIterator` không bảo đảm có provider nếu metadata/source không phù hợp.

### findFirst() có liên quan gì?

`findFirst()` là tiện ích tương đương việc dùng iterator để lấy provider đầu tiên:

~~~java
Codec chosen = ServiceLoader.load(Codec.class)
        .findFirst()
        .orElseGet(DefaultCodec::new);
~~~

Đây là fragment giả định có `DefaultCodec implements Codec`; không phải chương trình độc lập. `findFirst()` **có thể instantiate** provider đầu tiên, không phải lựa chọn thay `stream().filter(p -> p.type()...)` khi ta muốn kiểm tra metadata trước.

## 7. Iterator hay stream nên chọn trong từng trường hợp?

| Nhu cầu | Cách dùng hợp lý | Lưu ý |
| --- | --- | --- |
| Chạy lần lượt mọi provider | `for (S s : loader)` / `iterator()` | Tạo instance khi duyệt; phù hợp xử lý từng service. |
| Chỉ cần một provider đầu tiên | `loader.findFirst()` | Thứ tự discovery không luôn ổn định với nhiều named modules. |
| Chỉ chọn provider mang annotation trên type | `loader.stream().filter(p -> p.type()...).map(Provider::get)` | Lọc trước khi tạo instance. |
| Liệt kê loại provider phục vụ diagnostics | `loader.stream().map(p -> p.type().getName())` | Không cần khởi tạo service object chỉ để in tên. |
| Sắp xếp providers theo type | `stream().sorted(comparing(p -> p.type().getName()))` | Sắp xếp buộc khám phá các phần tử cần thiết và không bảo đảm cùng lựa chọn khi provider set đổi. |
| Muốn tái khám phá sau thay đổi | `loader.reload()` rồi lấy iterator/stream mới | Đừng giữ stream/iterator cũ qua reload. |
| Cần lifecycle/singleton provider cụ thể | Tự quản lý instances qua registry của ứng dụng | Không coi `Provider.get()` là singleton contract. |

### Filter theo metadata: ví dụ an toàn về mặt ý tưởng

Giả sử các implementation được gắn annotation runtime `@CodecPriority` và service type có nhiều providers:

~~~java
List<Codec> selected = loader.stream()
        .filter(p -> p.type().isAnnotationPresent(CodecPriority.class))
        .map(ServiceLoader.Provider::get)
        .toList();
~~~

Đây là fragment chỉ minh họa lựa chọn; để chạy cần khai báo annotation `CodecPriority` với `@Retention(RUNTIME)`, service `Codec` và những providers phù hợp. Các provider không qua filter **không bị gọi `get()` từ pipeline này**. Nhưng việc định vị/kiểm tra class và metadata vẫn có thể phát sinh lỗi hoặc tốn chi phí.

**Factory caveat:** Nếu provider trong named module khai báo static factory, `p.type()` có thể là **return type của factory**. Annotation đặt trên factory class không nhất thiết được nhìn thấy bởi `p.type()`. Chọn metadata ở nơi mà ServiceLoader API thật sự cung cấp.

## 8. SPI discovery: classpath và named modules khác nhau

`ServiceLoader` không tự quét mọi class có `implements Codec`. Nó cần **đăng ký provider** theo quy tắc SPI.

### 8.1. Provider trên classpath / unnamed module

Một JAR/classpath provider thường có resource:

~~~text
META-INF/services/codec.api.Codec
~~~

Nội dung (UTF-8), ví dụ:

~~~text
codec.impl.Alpha
codec.impl.Beta
~~~

Đây là **minh họa file provider-configuration**. Muốn chạy một project classpath hoàn chỉnh phải có `Codec`, `Alpha`, `Beta` và resource nằm đúng vị trí trên classpath. Không phải file dùng trong demo named module ở mục 4.

Một provider line là **binary name**, không phải đường dẫn `.class`; có thể có comment bắt đầu bằng `#` và dòng trắng theo quy tắc API. Classpath provider phải có điều kiện tạo instance thích hợp, thông thường **public class với public no-arg constructor** và implement/extend service type. Không thể lấy class bất kỳ chỉ chứa một method tĩnh rồi xem như classpath provider hợp lệ theo cùng cách với provider factory của named module.

ServiceLoader sử dụng resource lookup (`ClassLoader.getResources(...)`) khi tìm provider-configuration trong unnamed modules. **Thứ tự các cấu hình** phụ thuộc thứ tự resource được loader tìm và thứ tự tên provider trong file; các tên duplicate được bỏ qua theo API. Không nên mong mọi runtime/classpath đều trả danh sách provider giống nhau nếu classpath hoặc class loader thay đổi.

### 8.2. Provider trong named module / module layer

Named module khai báo:

~~~java
module codec.demo {
    exports codec.api;
    uses codec.api.Codec;
    provides codec.api.Codec with codec.impl.Alpha;
}
~~~

Đây là metadata chính xác của **ví dụ chạy được**. Với consumer ở **named module**, khai báo `uses service.Type` là một điều kiện quan trọng khi sử dụng ServiceLoader từ module đó. Provider ở named module được định nghĩa bằng `provides ... with ...`; một module không nên khai báo provider thuộc một module khác.

Một provider trong named module có thể:

- Có provider constructor phù hợp, hoặc
- Có public static no-arg `provider()` factory method theo hợp đồng `ServiceLoader`.

Classpath provider-configuration là cơ chế dành cho unnamed modules và có các điều kiện khác; không mặc định dùng factory method đó cho provider trên classpath.

### 8.3. ServiceLoader.load(ModuleLayer, Class)

Khi chương trình tạo dynamic `ModuleLayer` cho plugin, API:

~~~java
ServiceLoader<MyService> services =
        ServiceLoader.load(pluginLayer, MyService.class);
~~~

là **fragment** yêu cầu `pluginLayer` và `MyService` đã được khai báo. Overload này khám phá provider trong **named modules** của layer được chọn và các parent layers theo quy tắc API; **không tìm provider ở unnamed modules** theo overload này.

Thứ tự quét là **layer được chỉ định trước, sau đó những parent layers** theo quy tắc duyệt của API. Thứ tự giữa các modules trong cùng layer không được định nghĩa; vì thế không chọn provider đầu tiên trong ứng dụng quan trọng mà không có tiêu chí ưu tiên rõ ràng.

### 8.4. Load bằng TCCL hay loader tường minh?

Hai overload sau có thể quan sát tập providers khác nhau:

~~~java
ServiceLoader<MyService> a =
        ServiceLoader.load(MyService.class);

ServiceLoader<MyService> b =
        ServiceLoader.load(MyService.class, pluginClassLoader);
~~~

Đây là fragment. Lời gọi `load(MyService.class)` dùng **thread context class loader (TCCL)**; lời gọi hai tham số dùng class loader đã chỉ định. Với thread pool, TCCL trên worker **không tự bằng TCCL của thread gửi công việc**, nên kết quả discovery có thể khác. Các hệ plugin có class loader riêng nên chọn cách cung cấp loader/mapping rõ ràng, tránh giữ ServiceLoader trong cache global qua nhiều deployment.

Dù chọn TCCL đúng, provider vẫn phải có **type identity tương thích** với interface `MyService` của consumer. Hai bản service interface cùng tên được định nghĩa bởi hai class loader khác nhau vẫn là hai kiểu JVM riêng; việc đảo thứ tự provider resource không sửa được split identity.

## 9. Lỗi xuất hiện ở iterator, stream và Provider.get

Không phải tất cả lỗi ServiceLoader nổ ngay khi gọi `ServiceLoader.load(...)`. Việc nạp/lọc/khởi tạo diễn ra lười nên exception thường xuất hiện **lúc bắt đầu tiêu thụ kết quả**.

| Thời điểm | Lỗi có thể phát sinh |
| --- | --- |
| `iterator().hasNext()` / `iterator().next()` | `ServiceConfigurationError` khi metadata/provider không hợp lệ, không nạp/instantiate được. |
| Terminal operation trên `stream()` | `ServiceConfigurationError` khi quá trình discovery, loading hoặc kiểm tra provider type gặp lỗi. |
| `Provider.get()` | `ServiceConfigurationError` nếu constructor/factory thất bại, trả null trong trường hợp factory, hoặc provider không thể instantiate. |
| Dùng iterator/stream cũ sau `reload()` | `ConcurrentModificationException` theo cơ chế fail-fast của API. |
| Provider class sử dụng API type khác defining loader | `ServiceConfigurationError` hoặc lỗi type/linkage liên quan, tùy nơi phát hiện bất nhất. |
| Provider được khai báo nhưng thiếu dependency | Có thể là `ServiceConfigurationError` với cause như `ClassNotFoundException`/`NoClassDefFoundError`, tùy tình huống. |

`ServiceConfigurationError` là một **Error**, không phải checked exception. Không nên bắt toàn bộ `Error` hoặc im lặng nuốt mọi lỗi để “bỏ qua provider hỏng”. Thường nên ghi **service name, provider name/type, class loader, module/layer, cause** rồi fail có kiểm soát. Theo API, một số lần duyệt có thể cố tiếp tục sau lỗi, nhưng **không bảo đảm luôn phục hồi được** khỏi cấu hình provider sai.

### Mẫu chẩn đoán tại boundary

Fragment sau giả định `loader` là `ServiceLoader<Codec>` đã tạo từ service tương thích:

~~~java
try {
    for (ServiceLoader.Provider<Codec> p :
            loader.stream().toList()) {

        Class<? extends Codec> type = p.type();

        System.out.println("Provider type: " + type.getName());
        System.out.println("Defining loader: " + type.getClassLoader());
        System.out.println("Module: " + type.getModule().getName());

        Codec instance = p.get();
        System.out.println("Instance: " + instance.id());
    }
} catch (java.util.ServiceConfigurationError error) {
    System.err.println("SPI discovery/creation failed: " + error);
}
~~~

Đây là **fragment** chứ không phải một chương trình độc lập. Ở project thực tế, nên phân biệt việc enumerate toàn bộ providers (qua `toList()`) với việc tạo instances (qua `get()`), và log cause chi tiết. `getModule().getName()` có thể trả `null` đối với **unnamed module**, không nên giả định luôn có tên.

Nếu class provider nạp thành công nhưng `serviceType.isAssignableFrom(providerType)` sai, cần kiểm tra **API JAR trùng** và defining loaders; sửa discovery một mình không làm hai interface cùng tên thành cùng type.

## 10. Performance và quản lý tài nguyên: lazy không đồng nghĩa miễn phí

Dù ServiceLoader lazy, những bước dưới đây vẫn có thể gây chi phí đáng kể:

- Tra cứu `META-INF/services` qua URLClassLoader có thể truy cập JAR, filesystem hoặc URL từ xa tùy cấu hình.
- Nạp metadata của nhiều provider types để làm filter/sort.
- Reflection/validation để xác nhận class implements service và được phép instantiate.
- Chạy static initialization hoặc constructor khi gọi `Provider.get()` / iterator, bao gồm I/O, tạo thread hoặc giữ cache do provider tự làm.
- `sorted(...)`, `toList()` hoặc phép duyệt toàn bộ có thể buộc discover tất cả providers, trong khi `findFirst()` có thể dừng sớm theo thứ tự discovery.

Khi chỉ cần chọn một implementation đủ điều kiện, một pipeline dựa trên `Provider.type()` rồi `findFirst()` sẽ tránh instantiate tất cả provider chỉ để đọc metadata. Nhưng **đừng phụ thuộc thứ tự first provider** nếu dùng nhiều named modules hoặc có yêu cầu ưu tiên nghiệp vụ: đặt tiêu chí rõ như annotation priority, tên hoặc config và cân nhắc xung đột.

ServiceLoader không sở hữu lifecycle của provider sau khi nó đã được chuyển cho caller. Nếu provider mở socket, thread pool hay native resource, hãy quản lý và đóng **theo hợp đồng service riêng**. `reload()` không gọi `close()` cho object cũ.

## 11. Những hiểu nhầm thường gặp

**“`ServiceLoader.stream()` trả luôn `Stream<Codec>`.”** Sai: nó trả `Stream<ServiceLoader.Provider<Codec>>`. Muốn instances cần `map(Provider::get)` hoặc đường iterator.

**“`Provider.type()` tạo object dịch vụ rồi lấy `getClass()`.”** Sai: nó trả provider type mà không cần instantiate service object. Tuy nhiên, việc định vị/load metadata vẫn có thể xảy ra.

**“`Provider.type()` luôn là class factory trong named module.”** Sai: nếu module dùng public static `provider()`, `type()` trả **return type của factory**, có thể khác class chứa factory.

**“Gọi `Provider.get()` hai lần chắc chắn cùng singleton.”** Không được bảo đảm. Nếu cần singleton hoặc lifecycle cụ thể, tự quản lý cache/object theo hợp đồng ứng dụng.

**“Iterator không có cache nên duyệt lại tạo provider mới.”** Sai: ServiceLoader cache những instances đã được trả bởi iterator; ví dụ Java 17 cho thấy lấy lại instance cached qua iterator mới của **cùng loader**.

**“`reload()` sẽ tự phát hiện JAR bất kỳ mới đặt vào ổ đĩa.”** Sai: nó chỉ xóa cache; class loader/module layer hiện tại phải nhìn thấy artifact, và điều kiện discovery vẫn phải đúng.

**“`findFirst()` luôn cho provider có ưu tiên cao nhất.”** Không. Thứ tự giữa các named modules có thể không xác định; dùng tiêu chí rõ nếu cần chọn một implementation tối ưu.

**“Mọi lỗi sẽ ném ngay từ `ServiceLoader.load`.”** Không. Discovery/instantiation lazy khiến lỗi thường phát sinh từ `hasNext`, `next`, terminal stream operation hoặc `Provider.get`.

**“ServiceLoader thread-safe, cứ chia sẻ một instance qua mọi worker.”** Không. Tài liệu API không bảo đảm dùng một ServiceLoader mutable đồng thời an toàn; phải đồng bộ hoặc có ownership rõ khi dùng/reload.

**“SPI classpath và JPMS module registration giống hệt nhau.”** Không. Unnamed modules chủ yếu dùng `META-INF/services`; named modules dùng `provides ... with ...`, cùng những điều kiện `uses`/factory/visibility tương ứng.

## 12. Checklist áp dụng trong project

1. Xác định **service type duy nhất** và nơi định nghĩa API contract.
2. Chọn cách đăng ký provider: `META-INF/services` cho classpath, `provides ... with ...` cho named module.
3. Nếu caller ở named module, khai báo `uses service.Type` theo yêu cầu.
4. Xác định **loader/layer dùng discovery** và TCCL khi API đọc context loader.
5. Dùng `iterator()` hoặc enhanced-for khi thật sự muốn **service instances**.
6. Dùng `stream()` với `Provider.type()` khi cần **lọc/kiểm tra metadata trước**.
7. Chỉ gọi `Provider.get()` sau khi đã quyết định lấy instance.
8. Đừng dựa vào identity của `Provider.get()` như một singleton guarantee.
9. Chỉ dùng `findFirst()` khi thứ tự provider phù hợp hoặc không quan trọng; nếu cần ưu tiên hãy sắp xếp/lọc bằng tiêu chí rõ.
10. Với `reload()`, bỏ iterator/stream cũ và hiểu rằng lifecycle của instances cũ vẫn do caller quản lý.
11. Bắt và báo đúng `ServiceConfigurationError` ở ranh giới khám phá/khởi tạo; không nuốt lỗi cấu hình hỏng.
12. Khi debug, in type, module, **defining class loader**, cause và vị trí provider metadata.
13. Tránh cache ServiceLoader global cho nhiều plugin/TCCL/deployments khác nhau.
14. Test một provider lỗi, provider thiếu dependency, provider constructor thất bại, filter metadata và discovery sau reload.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Kiểu phần tử của `ServiceLoader<Codec>.iterator()` là gì? Của `stream()` là gì?

**Đáp án:** Iterator trả `Codec` instances; stream trả `ServiceLoader.Provider<Codec>` descriptors.

**Câu 2.** Vì sao `stream().filter(p -> p.type()...).findFirst()` thường rẻ hơn duyệt iterator rồi kiểm tra `object.getClass()`?

**Đáp án:** Có thể lọc theo type metadata **trước khi khởi tạo object**, tránh constructor của provider bị loại. Discovery/type loading vẫn có chi phí.

**Câu 3.** `Provider.get()` có bảo đảm luôn trả cùng instance như một singleton không?

**Đáp án:** Không. Nó trả một instance hợp lệ, không có singleton identity guarantee cho mọi lần gọi.

**Câu 4.** Gọi lại `loader.iterator()` trên **cùng đối tượng ServiceLoader** có nhất thiết tạo mới tất cả provider instances không?

**Đáp án:** Không. Các instances đã load qua iterator được cache và được trả trước; những providers chưa load mới được tạo lười.

**Câu 5.** Vì sao gọi `reload()` rồi dùng iterator cũ là sai?

**Đáp án:** Reload xóa cache và làm stale iterator/stream; API có thể ném `ConcurrentModificationException` theo cơ chế fail-fast. Hãy tạo iterator/stream mới.

**Câu 6.** Một named module dùng provider factory có `public static Codec provider()`. `Provider.type()` trả type nào?

**Đáp án:** **Return type của method `provider()`**, theo ServiceLoader API, không nhất thiết là class chứa factory.

**Câu 7.** Một plugin có provider implements `api.Codec` nhưng host không chấp nhận subtype dù tên giống nhau. Điều gì cần kiểm tra?

**Đáp án:** **Defining class loader và identity** của interface `api.Codec` ở host/plugin; hai bản interface cùng tên do loader khác nhau định nghĩa không tương thích chỉ nhờ tên.

## Ghi nhớ

**`iterator()` làm việc với service instances, còn `stream()` làm việc với `ServiceLoader.Provider<S>` descriptors.** `Provider.type()` cho phép kiểm tra loại provider **trước khi instantiate**; `Provider.get()` mới trả instance. ServiceLoader hỗ trợ lazy discovery, cache và `reload`, nhưng cần tránh giả định về singleton, thứ tự giữa named modules hay khả năng đồng thời. Trong hệ plugin, lựa chọn **đúng service interface, class loader/module layer và đăng ký provider** quan trọng không kém lựa chọn iterator hay stream.

**Tài liệu chuẩn (Java SE 17):**

- `ServiceLoader` — iterator, stream, load, reload, findFirst, discovery, caching, errors: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- `ServiceLoader.Provider` — type và get: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.Provider.html
- `ModuleLayer` — dynamic layers và class loader mapping: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html
- `ClassLoader` — resource lookup và type loading: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-loading-008">Cache provider, reload và giới hạn dùng đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# ServiceLoader: Cache provider, reload và giới hạn dùng đồng thời

`java.util.ServiceLoader<S>` là công cụ Java để tìm các **service provider** triển khai một interface hoặc service class mà ứng dụng biết trước. Nó rất hữu ích trong kiến trúc SPI/plugin vì host có thể chỉ phụ thuộc vào một **hợp đồng dịch vụ**; các implementation được khai báo trong `META-INF/services` (classpath) hoặc `provides ... with ...` (JPMS).

Tuy nhiên, `ServiceLoader` không phải “mỗi lần gọi là quét lại mọi JAR rồi tạo object mới”, cũng không phải registry thread-safe hay hệ hot-reload tự động. Java 17 xác định ba đặc điểm quyết định:

1. **Provider được tìm và nạp theo nhu cầu; loader có cache.** Iterator mới sẽ đi qua các provider đã được cache trước khi tiếp tục khám phá provider còn lại. Trong đường iterator, những instance đã tạo được dùng lại.
2. **`reload()` xóa cache của chính `ServiceLoader`.** Việc duyệt lại có thể khám phá và khởi tạo provider mới; iterator/stream **đã lấy trước khi reload phải bỏ đi**. Nhưng `reload()` **không thay đổi những `Class<?>` đã được JVM định nghĩa**, không đóng class loader, không thay JAR trong tiến trình.
3. **Một `ServiceLoader` instance không an toàn khi nhiều thread cùng dùng.** Không đồng thời `iterator()`/`hasNext()`/`next()`/`stream()`/`reload()` trên cùng instance mà không có thiết kế đồng bộ bên ngoài. Thread safety của **provider object** là bài toán khác.

Bài học thuộc **Java core – classloader / Nạp dịch vụ**, tập trung chính xác vào **cache provider, reload và giới hạn dùng đồng thời**. Các ví dụ chạy được trên **JDK 17+**, giải thích rõ khi nào provider constructor chạy, thứ gì được cache, khi nào phải tạo loader mới và cách tổ chức lifecycle không giữ plugin cũ trong bộ nhớ.

## 1. Sơ đồ phân biệt những đối tượng dễ bị gọi chung là “loader”

Một kiến trúc plugin đơn giản:

```text
     HOST APPLICATION
  ┌─────────────────────────────────────┐
  │ Service API: spi.GreetingService    │
  │             │                       │
  │ ServiceLoader<GreetingService>      │
  │   ├─ provider discovery/cache       │
  │   └─ iterator()/stream()/reload()   │
  └─────────────┬───────────────────────┘
                │ dùng một ClassLoader để tra cứu
                ▼
  ┌─────────────────────────────────────┐
  │ ClassLoader (app/URLClassLoader)     │
  │   ├─ tìm META-INF/services/...      │
  │   ├─ load provider implementation   │
  │   └─ định danh runtime Class<?>     │
  └─────────────┬───────────────────────┘
                │
                ▼
  ┌─────────────────────────────────────┐
  │ Provider object(s)                  │
  │ impl.FriendlyService, ...           │
  │ Có state, constructor và lifecycle  │
  └─────────────────────────────────────┘
```

Ba thứ khác nhau:

- **`ServiceLoader<S>` object:** giữ ngữ cảnh tìm dịch vụ và cache provider; `reload` tác động lên **cache bên trong object này**.
- **`ClassLoader` object:** chịu trách nhiệm thấy resource/service descriptor và định nghĩa/nạp Java classes. `ServiceLoader.reload()` **không gọi cơ chế “unload/redefine” tùy ý** trên object này.
- **Provider instance:** object cụ thể do constructor hoặc provider factory method tạo. Nó có thể là stateless, mutable, giữ thread/socket/file handle, hoặc triển khai `AutoCloseable`. `ServiceLoader` không phải lifecycle manager tự gọi `close()` cho mọi provider.

Một interface API phải được host và provider resolve về **cùng runtime type**. Nếu loader plugin tự định nghĩa bản sao của `spi.GreetingService`, provider có thể không assignable với interface host dù binary name giống nhau.

## 2. Provider được tìm thế nào? Discovery khác instantiation

Có hai kiểu triển khai phổ biến.

**Classpath/unnamed module:** provider class được chỉ định trong một resource tên `META-INF/services/<binary-name-của-service>`. Ví dụ:

```text
META-INF/services/spi.GreetingService
```

Nội dung resource:

```text
impl.FriendlyService
```

Tên trong file là **binary name của implementation**, không phải tên file `.class`. File có thể có nhiều provider names, mỗi dòng một tên; Java 17 yêu cầu service-configuration file mã hóa **UTF-8**, hỗ trợ dòng trống và chú thích `#`.

**Named module (JPMS):** module khai báo:

```java
module com.example.plugin {
    requires com.example.api;
    provides spi.GreetingService with impl.FriendlyService;
}
```

Đây là **`module-info.java` minh họa**, chưa phải chương trình độc lập. Host module cần `uses spi.GreetingService;` khi gọi SPI discovery; các module export/readability và `ModuleLayer` phải được cấu hình hợp lệ. Named module có thể sử dụng một **public static provider() factory** trong những điều kiện Javadoc quy định, khác với classpath provider thường dùng public zero-argument constructor.

**Discovery không đồng nghĩa tạo instance:** khi có `ServiceLoader<GreetingService> loader = ServiceLoader.load(GreetingService.class)`, đừng mặc định mọi provider constructor đã chạy. Việc **iterator traversal**, `findFirst()` hoặc `Provider.get()` mới khiến cần đến những instance theo cơ chế tương ứng.

## 3. Cache nghĩa là gì đối với `iterator()`?

Java 17 Javadoc quy định provider được định vị/nạp/khởi tạo **lazily**. Khi dùng `iterator()`:

1. Iterator mới **trả các provider instances đã được cache** của loader đó trước, theo thứ tự chúng được nạp.
2. Sau phần cached, iterator tiếp tục **tìm những provider chưa được duyệt**, và khởi tạo chúng theo nhu cầu.
3. Những instances được tạo qua quá trình iterator traversal được **thêm vào cache**.
4. Một lần gọi `iterator()` khác trên **cùng `ServiceLoader`**, khi chưa `reload()`, có thể trả lại **chính các cached objects**.
5. Nếu đã `reload()`, cache cũ không được phát lại bởi lượt duyệt mới của loader ấy.

```text
ServiceLoader<GreetingService> L
  ├─ iterator #1
  │     ├─ provider A → tạo instance A1 → cache
  │     └─ provider B → chưa đụng đến → chưa cần tạo
  │
  ├─ iterator #2
  │     ├─ provider A → trả lại A1 từ cache
  │     └─ provider B → khi đến lượt, tạo B1 → cache
  │
  └─ reload()
        ├─ cache cleared
        └─ iterator mới → có thể tạo A2, B2 khi cần
```

**Cache không phải singleton toàn JVM.** Một `ServiceLoader` object khác có cache riêng. Ngay cả trên cùng class loader và cùng service type, việc gọi `ServiceLoader.load(...)` một lần nữa tạo **ServiceLoader instance mới** với cache riêng.

**Cache cũng không hứa provider thread-safe.** Nếu cùng một cached instance `A1` được đưa cho hai consumers, object ấy có thể vẫn có mutable state và phải được đồng bộ theo hợp đồng của implementation.

## 4. `findFirst()` và khả năng dùng lại instance

`findFirst()` (Java 9+) tương đương ý nghĩa “lấy provider đầu tiên qua iterator”. Trong trường hợp một service có đúng một provider được cấu hình và khởi tạo thành công:

```java
ServiceLoader<GreetingService> loader =
        ServiceLoader.load(GreetingService.class);

GreetingService first = loader.findFirst().orElseThrow();
GreetingService again = loader.findFirst().orElseThrow();
System.out.println(first == again);
```

Đây là **snippet minh họa** cần import `java.util.ServiceLoader` và service interface `GreetingService`. Kết quả là `true` khi hai lần tìm cùng cached provider qua iterator của **cùng loader**, chưa reload.

**Không lẫn cache với thứ tự lựa chọn:** nếu có nhiều providers, provider “đầu tiên” phụ thuộc discovery order theo classpath/service files hoặc module ordering. Một số thứ tự trong JPMS **không được đặc tả**. Nếu ứng dụng cần chọn provider theo độ ưu tiên, hãy thiết kế metadata/selection rules rõ ràng, đừng mặc định tên class đầu tiên theo bảng chữ cái.

`findFirst()` cũng có thể trả `Optional.empty()` khi không có provider; và có thể ném `ServiceConfigurationError` nếu cấu hình/provider bị lỗi. Nó không âm thầm chuyển mọi lỗi thành empty.

## 5. Thực hành A — cache trước reload, object mới sau reload

Ta dựng đúng **một service và một provider**, để output không phụ thuộc thứ tự khám phá nhiều JAR. Chương trình tạo một `ServiceLoader`, lấy provider hai lần, `reload` và lấy lại.

Cây tệp:

```text
service-cache-lab/
├── src/
│   ├── api/spi/GreetingService.java
│   ├── provider/impl/FriendlyService.java
│   └── host/demo/CacheDemo.java
├── api-out/       (javac tạo)
├── provider-out/  (javac và service descriptor)
└── host-out/      (javac tạo)
```

**Tệp `src/api/spi/GreetingService.java`:**

```java
package spi;

public interface GreetingService {
    String hello(String name);
}
```

**Tệp `src/provider/impl/FriendlyService.java`:**

```java
package impl;

import spi.GreetingService;

public final class FriendlyService implements GreetingService {
    private static int constructions;

    public FriendlyService() {
        int number = ++constructions;
        System.out.println("provider ctor #" + number);
    }

    @Override
    public String hello(String name) {
        return "Hello, " + name;
    }
}
```

Biến static `constructions` phục vụ **quan sát trong demo đơn luồng**, không phải counter thread-safe dùng trong production. Method `hello` không ghi mutable state nên có thể dùng như operation stateless trong bài tập.

**Tệp `src/host/demo/CacheDemo.java`:**

```java
package demo;

import spi.GreetingService;
import java.util.ServiceLoader;

public final class CacheDemo {
    public static void main(String[] args) {
        ServiceLoader<GreetingService> loader =
                ServiceLoader.load(GreetingService.class);

        GreetingService first =
                loader.findFirst().orElseThrow();
        GreetingService second =
                loader.findFirst().orElseThrow();

        System.out.println("before reload same object=" +
                (first == second));
        System.out.println("first result=" + first.hello("Java"));

        loader.reload();

        GreetingService after =
                loader.findFirst().orElseThrow();
        System.out.println("after reload same object=" +
                (first == after));
        System.out.println("after reload same class=" +
                (first.getClass() == after.getClass()));
        System.out.println("after result=" + after.hello("Java"));
    }
}
```

Biên dịch từ thư mục `service-cache-lab` bằng JDK 17+:

```sh
javac --release 17 -d api-out src/api/spi/GreetingService.java
javac --release 17 -cp api-out -d provider-out src/provider/impl/FriendlyService.java
javac --release 17 -cp api-out -d host-out src/host/demo/CacheDemo.java
```

**Bắt buộc tạo service descriptor** ở `provider-out/META-INF/services/spi.GreetingService`. Trên **Windows PowerShell**:

```powershell
New-Item -ItemType Directory -Force provider-out\META-INF\services | Out-Null
Set-Content -Path provider-out\META-INF\services\spi.GreetingService -Value "impl.FriendlyService" -Encoding Ascii
```

Trên **Linux/macOS**:

```sh
mkdir -p provider-out/META-INF/services
printf 'impl.FriendlyService\n' > provider-out/META-INF/services/spi.GreetingService
```

Ở đây tên provider và service chỉ có ASCII nên file tạo ở encoding ASCII cũng là UTF-8 hợp lệ, đáp ứng Javadoc.

Chạy trên **Windows** (phân tách classpath bằng `;`):

```powershell
java -cp "host-out;api-out;provider-out" demo.CacheDemo
```

Chạy trên **Linux/macOS** (phân tách bằng `:`):

```sh
java -cp "host-out:api-out:provider-out" demo.CacheDemo
```

**Stdout trong một tiến trình JVM mới:**

```text
provider ctor #1
before reload same object=true
first result=Hello, Java
provider ctor #2
after reload same object=false
after reload same class=true
after result=Hello, Java
```

### Giải thích chính xác từng kết quả

- Constructor in `#1` vì `findFirst()` đầu tiên buộc khởi tạo provider instance đầu tiên.
- `findFirst()` thứ hai từ **cùng `ServiceLoader`** nhận object cached; không gọi constructor lần nữa.
- `reload()` xóa cache provider của chính loader; `findFirst()` kế tiếp duyệt lại và tạo object mới, constructor in `#2`.
- `first == after` bằng `false` vì đây là hai instance.
- `first.getClass() == after.getClass()` bằng `true` vì ta **không thay class loader**, không định nghĩa lại class; chỉ tạo instance mới của **cùng runtime provider class**.
- `reload()` không gọi `close()` cho object `first`. Biến `first` vẫn giữ tham chiếu đến object cũ và hoàn toàn có thể gọi `first.hello(...)` sau reload nếu API/lifecycle của ứng dụng cho phép.

**Bài học:** reload **provider cache** khác với reload **class definition**. Nó không thay source JAR, không cập nhật bytecode của class đã loaded và không reset static field `constructions` về 0.

## 6. Những thứ `reload()` không thực hiện

Khi gọi:

```java
serviceLoader.reload();
```

Đây là snippet cần `serviceLoader` đã khai báo. Phương thức có tác dụng **xóa provider cache của `ServiceLoader` ấy** để các lần iterator/stream tiếp theo thực hiện discovery lazily từ đầu.

Nó **không**:

- Ép JVM **unload** provider classes đã được load.
- Định nghĩa lại bytecode của các classes đã được định nghĩa trong cùng defining loader.
- Thay `ClassLoader` object, `ModuleLayer` hoặc TCCL ngầm.
- Đổi đường dẫn `META-INF/services` nếu class loader không được cấu hình để thấy nguồn mới.
- Gọi `close()`/`stop()` trên provider objects đã phát cho host.
- Ngắt các tasks đang giữ provider instances cũ.
- Bảo đảm một JAR mới bị chép đè sẽ thay thế implementation của class đã loaded.
- Tạo một “atomic switchover” an toàn giữa consumers trên nhiều threads.
- Làm cho `ServiceLoader` thread-safe.

Nếu hệ thống cần **plugin hot reload**, phải xử lý **plugin lifecycle, class loader mới, discovery scope, dependency identity và references**. `reload()` chỉ là một bước có thể hữu ích trong trường hợp provider discovery có thay đổi mà classloader hiện tại thực sự nhìn thấy được.

### `reload` vẫn có ích khi nào?

Ví dụ, class loader hoặc module configuration được cập nhật theo cơ chế được hỗ trợ và **nguồn discovery của cùng ServiceLoader context** có thể nhìn thấy provider chưa từng được khám phá; `reload` buộc lần duyệt sau quét lại. Nhưng nếu class loader đã load `impl.Plugin` từ JAR phiên bản cũ, `reload` **không biến `impl.Plugin` thành runtime class khác**.

## 7. Iterator cũ sau `reload()`: phải bỏ, không tiếp tục dùng

Javadoc Java 17 nêu rõ: iterator được lấy từ `ServiceLoader.iterator()` **trước reload** không còn dùng được sau khi cache bị xóa. Các phương thức `hasNext()` và `next()` có thể ném **`ConcurrentModificationException`** theo cơ chế fail-fast.

```text
ServiceLoader L
   │
   ├─ Iterator old = L.iterator()
   │
   ├─ old.hasNext()          → hoạt động khi chưa reload
   │
   ├─ L.reload()             → provider cache cleared
   │
   ├─ old.hasNext()/next()   → ConcurrentModificationException
   │
   └─ Iterator fresh = L.iterator()
                              → discovery mới, từ cache mới
```

**Tên `ConcurrentModificationException` không đồng nghĩa nhất thiết có hai threads.** Ngay cả một thread gọi `reload()` rồi tiếp tục dùng iterator cũ cũng có thể gặp exception. Đó là **invalidation của iterator**, khác với race do nhiều threads thực thi.

Không nên bắt exception này rồi **tiếp tục dùng old iterator**. Hãy tạo **iterator mới** sau reload, và quy định vùng ownership rõ ràng.

## 8. Thực hành B — minh họa iterator fail-fast sau reload

Giữ lại service, provider, descriptor và các thư mục `api-out`, `provider-out` từ thực hành A. Thêm tệp **`src/host/demo/IteratorDemo.java`**:

```java
package demo;

import spi.GreetingService;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.ServiceLoader;

public final class IteratorDemo {
    public static void main(String[] args) {
        ServiceLoader<GreetingService> loader =
                ServiceLoader.load(GreetingService.class);

        Iterator<GreetingService> old = loader.iterator();
        System.out.println("old has provider=" + old.hasNext());

        loader.reload();

        try {
            old.hasNext();
            System.out.println("old iterator invalidated=false");
        } catch (ConcurrentModificationException expected) {
            System.out.println("old iterator invalidated=true");
        }

        GreetingService fresh =
                loader.findFirst().orElseThrow();
        System.out.println("fresh result=" + fresh.hello("B"));
    }
}
```

Biên dịch:

```sh
javac --release 17 -cp api-out -d host-out src/host/demo/IteratorDemo.java
```

Chạy trên Windows:

```powershell
java -cp "host-out;api-out;provider-out" demo.IteratorDemo
```

Hoặc Linux/macOS:

```sh
java -cp "host-out:api-out:provider-out" demo.IteratorDemo
```

**Stdout với đúng một provider:**

```text
old has provider=true
old iterator invalidated=true
provider ctor #1
fresh result=Hello, B
```

`old.hasNext()` trước reload xác định provider tồn tại nhưng **không cần tạo instance** của nó; constructor được gọi sau đó khi `findFirst()` lấy fresh provider. Vì chương trình chạy ở **một JVM mới**, bộ đếm constructor static bắt đầu lại và in `#1`.

**Đừng khái quát mọi `hasNext()` là vô hại:** quá trình lazy discovery có thể cần đọc descriptor và load provider metadata; lỗi `ServiceConfigurationError` có thể phát sinh ở `hasNext()` hoặc `next()` tùy tình huống. Chỉ nói rằng ví dụ này không instantiate provider bằng `old.hasNext()`.

## 9. `stream()` và `ServiceLoader.Provider<S>` khác iterator ra sao?

Java 9+ có:

```java
ServiceLoader<GreetingService> loader =
        ServiceLoader.load(GreetingService.class);

loader.stream()
      .filter(provider -> provider.type().getName()
              .startsWith("impl."))
      .map(ServiceLoader.Provider::get)
      .forEach(service ->
              System.out.println(service.hello("Java")));
```

Đây là **snippet**, cần imports cho `ServiceLoader` và `GreetingService`. `stream()` cung cấp các **`Provider<S>` descriptors**:

- `provider.type()` trả provider type để xét metadata/annotations mà chưa phải tạo service instance.
- `provider.get()` trả một instance của provider; với named modules, provider factory method có thể được dùng.
- `stream()` cũng **lazy**: terminal operation mới thực sự duyệt.
- `ServiceLoader` có cơ chế **cache providers đã được stream khám phá**; Javadoc mô tả xử lý các cached providers trước khi tìm những provider còn lại.
- **Không suy luận rằng gọi `Provider.get()` nhiều lần luôn trả cùng singleton instance.** `Provider.get()` là API tạo/nhận một instance theo provider implementation; Javadoc không hứa cached-instance identity tương tự iterator traversal.
- Với các service providers trong named modules, `Provider.type()` có thể phản ánh return type của provider factory method, không nhất thiết provider factory class itself.

### Cache của `stream` không nên bị đồng nhất với cache instance của `iterator`

Iterator dùng cache instance phục vụ việc duyệt `S`. Stream duyệt `Provider<S>` descriptors và chỉ khi gọi `Provider.get()` mới lấy instance. Hãy coi **caching behavior được Javadoc ghi theo từng API**; đừng phụ thuộc một chi tiết nội bộ rằng tất cả provider instances phát qua `stream` và `iterator` đều có identity nhất định.

Nếu mục tiêu là **một service object dùng lặp lại**, hãy lấy instance đúng một lần, đặt nó trong registry do ứng dụng sở hữu, sau đó quản lý concurrency/lifecycle của registry ấy.

### Stream cũ sau reload

Javadoc cũng yêu cầu **bỏ stream đã lấy trước reload**. `spliterator` nguồn của stream có hành vi fail-fast nếu cache đã được clear và có thể ném `ConcurrentModificationException`. Không gọi `reload()` giữa một stream pipeline đang được tiêu thụ hoặc giữa phần tạo stream và terminal operation; tạo stream mới sau reload.

## 10. `ServiceLoader` không thread-safe: hiểu đúng phạm vi cảnh báo

Java 17 Javadoc tuyên bố rõ: **instances of `ServiceLoader` are not safe for use by multiple concurrent threads**.

Điều này là cảnh báo về **chính `ServiceLoader<S>` object**, vì nó có cache và trạng thái duyệt lazy, có thể bị thay đổi khi các calls tương tác. Những cặp thao tác đáng tránh:

- Thread A duyệt `for (S s : loader)` trong khi thread B gọi `loader.reload()`.
- Hai threads cùng dùng **một `ServiceLoader`** và đồng thời mở/tiến hành hai iterators chưa được phối hợp.
- Một thread dùng `stream()` và thread khác làm thay đổi cache bằng iteration/reload.
- Lưu một `ServiceLoader` VM-wide rồi để các tenants/applications với TCCL khác nhau truy cập chung.
- Dùng `parallel()` trực tiếp trên stream của `ServiceLoader` để khám phá provider trong khi không xét thread-safety.

Ngay cả khi một lần chạy tình cờ không báo lỗi, điều đó **không biến `ServiceLoader` thành thread-safe**.

Nhưng có một ranh giới khác: **provider implementation có thread-safe hay không**. Sau khi host đã chọn một `GreetingService` instance, việc hai threads gọi `service.hello(...)` đồng thời có an toàn không phụ thuộc **implementation contract**, không phụ thuộc `ServiceLoader` cache.

Do đó phân biệt:

```text
ServiceLoader object  ── không thread-safe khi truy cập đồng thời
        │
        ▼
Provider instance    ── có thể thread-safe hoặc không; phải xem contract
        │
        ▼
Business operation   ── cần bảo vệ mutable state nếu có
```

## 11. Ba hướng xử lý concurrency hợp lý

### Cách A: Khởi tạo trên một thread, snapshot rồi mới phân phát

1. Tạo và duyệt `ServiceLoader` trong một giai đoạn discovery **tuần tự**.
2. Lưu các provider instances hoặc provider factories vào một danh sách/registry có ownership rõ ràng.
3. Công bố snapshot **bất biến về cấu trúc** cho worker threads.
4. Chỉ gọi provider instance đồng thời nếu chính provider **được thiết kế thread-safe**; nếu không, bảo vệ bằng lock hoặc tạo instance riêng cho mỗi worker.

Với service stateless, đây thường là cách đơn giản và hiệu quả.

### Cách B: Đồng bộ toàn bộ tương tác với cùng ServiceLoader

Ví dụ:

```java
final Object guard = new Object();
final ServiceLoader<GreetingService> loader =
        ServiceLoader.load(GreetingService.class);

synchronized (guard) {
    for (GreetingService service : loader) {
        // Đưa kết quả vào registry nội bộ.
    }
}
```

Đây là **snippet**, không phải chương trình độc lập. **Mọi đường gọi `iterator`, `stream`, `reload` đối với cùng loader phải tuân cùng chính sách bảo vệ**. Không được khóa riêng lúc lấy `Iterator` nhưng lại thả khóa rồi duyệt ở thread khác — trạng thái lazy còn tiếp tục bị thay đổi tại `hasNext/next`.

Đồng bộ cũng **không tự bảo vệ** những provider objects đã trao cho consumers; đó là lớp concurrency khác.

### Cách C: Mỗi công việc tạo ServiceLoader riêng

Nếu không cần chia sẻ cache, các tasks có thể dùng **ServiceLoader instances riêng** với cùng service type và loader được cấu hình phù hợp. Điều này tránh dùng chung trạng thái ServiceLoader, nhưng có thể tạo nhiều provider instances và tăng chi phí discovery. Không coi các providers là thread-safe chỉ vì ServiceLoader không bị dùng chung.

**Khuyến nghị theo ngữ cảnh:** đối với ứng dụng có provider lifecycle dài, thường nên làm **discovery một lần**, xây immutable registry và quản lý thay thế version bằng cơ chế rõ ràng thay vì gọi `ServiceLoader` đồng thời trên mỗi request.

## 12. Thực hành C — snapshot tuần tự, gọi provider trên hai worker threads

Dùng lại `GreetingService`, `FriendlyService` và descriptor từ thực hành A. Tệp mới **`src/host/demo/SnapshotDemo.java`**:

```java
package demo;

import spi.GreetingService;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class SnapshotDemo {
    public static void main(String[] args) throws Exception {
        ServiceLoader<GreetingService> loader =
                ServiceLoader.load(GreetingService.class);

        List<GreetingService> found = new ArrayList<>();
        for (GreetingService provider : loader) {
            found.add(provider);
        }
        List<GreetingService> snapshot = List.copyOf(found);

        // Chỉ sau khi discovery xong mới chạy tác vụ đồng thời.
        GreetingService service = snapshot.get(0);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a =
                    pool.submit(() -> service.hello("A"));
            Future<String> b =
                    pool.submit(() -> service.hello("B"));

            System.out.println("job A=" + a.get());
            System.out.println("job B=" + b.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch:

```sh
javac --release 17 -cp api-out -d host-out src/host/demo/SnapshotDemo.java
```

Chạy Windows:

```powershell
java -cp "host-out;api-out;provider-out" demo.SnapshotDemo
```

Chạy Linux/macOS:

```sh
java -cp "host-out:api-out:provider-out" demo.SnapshotDemo
```

**Stdout:**

```text
provider ctor #1
job A=Hello, A
job B=Hello, B
```

- Provider được khởi tạo trong **main thread** khi duyệt `loader`; một provider nên chỉ in một constructor marker.
- `List.copyOf` tạo danh sách bất biến về cấu trúc. Nó **không tự biến nội dung provider thành immutable hay thread-safe**.
- Hai workers gọi method `hello` của **cùng provider instance**. Bài tập này an toàn vì `FriendlyService.hello` không thay đổi shared state; constructor counter không bị thay đổi ở những calls ấy.
- Hai futures được chờ/in theo **thứ tự A rồi B**, nên stdout được xác định mặc dù hai tasks có thể thực sự chạy theo thứ tự bất kỳ.
- `ServiceLoader` **không bị gọi bởi worker threads**. Đây là điểm chính của chiến lược snapshot, không phải dùng `synchronized` bên trong provider một cách tình cờ.

Nếu service là stateful (ví dụ counter mutable hoặc dùng non-thread-safe parser), cần thiết kế **instance per task, pooling, synchronization**, hoặc serial executor ở lớp provider. Snapshot chỉ ngăn race trên provider discovery/cache.

## 13. `reload()` và concurrency không tạo atomic hot-swap

Giả sử host đang chạy request qua `oldProvider`. Một quản trị viên gọi `serviceLoader.reload()` rồi chọn `newProvider`. Điều gì xảy ra?

- `reload()` **không thu hồi** object `oldProvider` khỏi những threads đã giữ reference.
- Threads đang gọi method của `oldProvider` vẫn có thể tiếp tục dùng nó.
- Nếu provider sở hữu socket/file/thread, `reload()` không gọi cleanup.
- Nếu các consumers đọc trực tiếp một mutable list được thay đổi đồng thời, có thể thấy trạng thái không nhất quán.
- Nếu reload xảy ra trong khi iterator/stream cũ đang duyệt, có thể thấy `ConcurrentModificationException` hoặc race ngoài hợp đồng thread-safety.

Một cách thiết kế registry:

```text
  Giai đoạn discovery mới (tuần tự, loader riêng khi cần)
           │
           ▼
  Validate tất cả provider candidates
           │
           ▼
  Tạo snapshot registry mới đầy đủ
           │
           ▼
  Công bố snapshot mới bằng một phép gán atomic
           │
           ▼
  Dừng nhận request mới vào registry cũ
           │
           ▼
  Chờ/huỷ các tác vụ cũ theo lifecycle contract
           │
           ▼
  Close provider resources và loader cũ khi an toàn
```

Sơ đồ này là **pattern kiến trúc**, không phải guarantee mà `ServiceLoader` cung cấp. Có thể dùng `AtomicReference<Registry>` hoặc volatile reference cho một registry immutable và giao thức quiescence/active request tracking. **Atomic publish** giải quyết visibility của reference mới, còn việc hoàn tất công việc trên provider cũ vẫn cần lifecycle protocol.

## 14. ClassLoader mới khác hoàn toàn `ServiceLoader.reload()`

Hai tiến trình sau không tương đương:

**A. Cùng ClassLoader, gọi reload:**

```text
URLClassLoader L1 defines impl.FriendlyService → Class C1
ServiceLoader S1 (using L1)
  → first provider instance P1
  → S1.reload()
  → next provider instance P2

P1 != P2
P1.getClass() == P2.getClass()    (cùng C1)
```

**B. ClassLoader mới, discovery mới:**

```text
URLClassLoader L1 defines impl.FriendlyService → Class C1
URLClassLoader L2 defines impl.FriendlyService → Class C2

C1 != C2                        (khác defining loader)
```

Trường hợp B thường được dùng để nạp **phiên bản plugin khác** sau khi đã thay artifact và có loader mới. Nhưng cần shared API identity: `spi.GreetingService` ở host và hai plugins phải resolve về **cùng runtime interface Class<?>**, thường qua parent API loader.

Một lưu ý: nếu parent đã định nghĩa `impl.FriendlyService` và `URLClassLoader` dùng parent-first, cả L1 và L2 có thể trả **cùng class của parent** thay vì tự define C1/C2. Vì thế integration test phải **không đặt provider implementation trên host classpath** khi muốn kiểm thử per-plugin class loader.

## 15. Thực hành D — hai ClassLoader và reload cache trong một loader

Dùng **chính `provider-out`** từ thực hành A, nhưng lần này **không đặt nó trên application classpath**. Chỉ có host và shared API ở application loader; hai child `URLClassLoader` cùng được cấp URL tới `provider-out`.

Tệp **`src/host/demo/LoaderIdentityDemo.java`**:

```java
package demo;

import spi.GreetingService;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ServiceLoader;

public final class LoaderIdentityDemo {
    public static void main(String[] args) throws Exception {
        URL location = Path.of("provider-out")
                .toAbsolutePath().toUri().toURL();
        URL[] urls = { location };
        ClassLoader sharedApiLoader =
                GreetingService.class.getClassLoader();

        try (URLClassLoader firstLoader =
                     new URLClassLoader(urls, sharedApiLoader);
             URLClassLoader secondLoader =
                     new URLClassLoader(urls, sharedApiLoader)) {

            ServiceLoader<GreetingService> firstServices =
                    ServiceLoader.load(
                            GreetingService.class, firstLoader);
            ServiceLoader<GreetingService> secondServices =
                    ServiceLoader.load(
                            GreetingService.class, secondLoader);

            GreetingService a =
                    firstServices.findFirst().orElseThrow();
            GreetingService b =
                    secondServices.findFirst().orElseThrow();

            System.out.println("same provider Class across loaders=" +
                    (a.getClass() == b.getClass()));
            System.out.println("a defined by first=" +
                    (a.getClass().getClassLoader() == firstLoader));
            System.out.println("b defined by second=" +
                    (b.getClass().getClassLoader() == secondLoader));
            System.out.println("both implement host API=" +
                    (GreetingService.class.isInstance(a)
                            && GreetingService.class.isInstance(b)));

            firstServices.reload();
            GreetingService a2 =
                    firstServices.findFirst().orElseThrow();

            System.out.println("same object after reload=" +
                    (a == a2));
            System.out.println("same Class after reload=" +
                    (a.getClass() == a2.getClass()));
        }
    }
}
```

Biên dịch (đọc shared API từ `api-out`, không cần provider implementation tại compile time):

```sh
javac --release 17 -cp api-out -d host-out src/host/demo/LoaderIdentityDemo.java
```

**Windows:**

```powershell
java -cp "host-out;api-out" demo.LoaderIdentityDemo
```

**Linux/macOS:**

```sh
java -cp "host-out:api-out" demo.LoaderIdentityDemo
```

**Stdout trong cấu hình đã nêu:**

```text
provider ctor #1
provider ctor #1
same provider Class across loaders=false
a defined by first=true
b defined by second=true
both implement host API=true
provider ctor #2
same object after reload=false
same Class after reload=true
```

**Tại sao constructor cùng in `#1` hai lần?** Static field `constructions` thuộc runtime class `impl.FriendlyService` mà **mỗi defining loader riêng** đã định nghĩa. Vì hai lớp có class identity khác nhau, mỗi lớp bắt đầu static counter từ 0. Khi reload `firstServices`, class từ first loader **không đổi**, nên constructor lần tiếp theo của **lớp thứ nhất** in `#2`.

`ServiceLoader.load(GreetingService.class, firstLoader)` nhận loader tường minh để tìm descriptor/provider, nhưng provider tiếp tục delegate interface `spi.GreetingService` lên shared parent. Vì thế host cast/API assignability vẫn đúng.

**Đóng loader:** `try-with-resources` đóng `URLClassLoader` resources khi block kết thúc; không bảo đảm JVM unload ngay object/classes còn references. Khi ứng dụng thật có provider đang phục vụ request, **đừng close class loader trước khi lifecycle kết thúc**.

## 16. Thứ tự provider và cache: không thiết kế dựa trên thứ tự ngầm

Với classpath providers, thứ tự khám phá thường theo:

- thứ tự mà class loader `getResources("META-INF/services/...")` trả service configuration files;
- thứ tự các provider names trong mỗi descriptor;
- xử lý trùng provider names theo Javadoc.

Với named modules, `ServiceLoader` có quy tắc riêng theo module/layer và một số thứ tự **không được xác định**. Vì vậy không nên giả định `findFirst()` luôn chọn “provider tốt nhất” hoặc toàn bộ modules được duyệt theo tên.

Nếu ứng dụng có nhiều providers và cần ưu tiên:

1. Mỗi provider khai báo capability/priority dưới một **contract rõ ràng**.
2. Discovery tất cả candidates tuần tự.
3. Xác thực type/capability.
4. Sắp xếp/chọn theo tiêu chí của ứng dụng, có xử lý trường hợp trùng mức ưu tiên.
5. Cache selected instance/registry sau khi đã quyết định.

Đừng để thứ tự JAR files tình cờ quyết định provider dành cho giao dịch nhạy cảm.

## 17. `ServiceConfigurationError` trong quá trình lazy loading

Do ServiceLoader **lazy**, lỗi không nhất thiết phát sinh ngay khi gọi `ServiceLoader.load(...)`. Javadoc chỉ rõ:

- `Iterator.hasNext()` và `next()` có thể ném `ServiceConfigurationError` khi đọc descriptor, nạp provider hoặc tạo instance.
- Stream operations có thể ném lỗi tại **thời điểm thực sự duyệt**, và `Provider.get()` có thể lỗi lúc tạo provider.
- `findFirst()` cũng có thể báo lỗi khi provider đầu tiên không hợp lệ.
- Nếu provider-configuration file sai định dạng, tên class không tồn tại, provider không assignable vào đúng service type, hoặc constructor/factory thất bại, error có thể mang cause liên quan.

Một số lỗi hay gặp:

| Triệu chứng | Khả năng nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Không tìm thấy provider, `findFirst()` empty | Descriptor/module declaration không được runtime loader nhìn thấy | Kiểm tra `META-INF/services`, JAR entries, TCCL/explicit loader, JPMS |
| `ServiceConfigurationError: Provider ... not found` | Provider class không có ở classpath/module scope | Kiểm tra binary name, JAR, loader URL/delegation |
| Provider not a subtype | Host và plugin dùng hai runtime copies của service API hoặc provider khai báo sai | So `Class<?>`, defining loader, shared API graph |
| Constructor/factory lỗi | Provider init/constructor method ném exception hoặc cấu hình không hợp lệ | Xem cause, log provider identity, sửa lifecycle |
| Iterator `ConcurrentModificationException` sau reload | Đang dùng iterator cũ bị invalidated | Bỏ iterator cũ, tạo iterator mới sau reload |
| Concurrent iteration/reload thất thường | Một `ServiceLoader` bị truy cập từ nhiều threads | Serialize discovery/reload hoặc snapshot/ownership |
| Provider “không đổi” dù update JAR | ClassLoader đã định nghĩa class cũ; reload chỉ clear provider cache | Triển khai loader/version lifecycle, không gọi reload như redefine |
| Memory leak khi reload plugin liên tục | Host giữ provider instance, registry, ServiceLoader, ClassLoader, TCCL hoặc threads | Unregister callbacks, stop tasks, close resources, release references |

### Không nuốt lỗi cấu hình một cách mù quáng

Không nên:

```java
try {
    return loader.findFirst().orElse(null);
} catch (Throwable ignored) {
    return null;
}
```

Đây là **mẫu sai**, không phải method độc lập; nó nuốt cả lỗi class linking/JVM và đánh đồng “không có provider” với “provider broken”. `ServiceConfigurationError` là một `Error` có ý nghĩa cấu hình runtime nghiêm trọng. Nếu ứng dụng muốn optional plugin, có thể bắt riêng lỗi đó ở tầng **quản lý plugin**, giữ context/cause và chính sách fail-fast hoặc skip provider được xác định; đừng giả định tiếp tục iterator sau lỗi luôn phục hồi hoàn hảo.

## 18. TCCL, loader tường minh và giới hạn cache cross-application

`ServiceLoader.load(ServiceType.class)` dùng **thread context class loader (TCCL)** tại lúc tạo ServiceLoader:

```java
ServiceLoader<GreetingService> viaContext =
        ServiceLoader.load(GreetingService.class);

ServiceLoader<GreetingService> explicit =
        ServiceLoader.load(GreetingService.class, pluginLoader);
```

Đây là **snippets**, cần biến `pluginLoader` và interface đã khai báo. Trong container, hai threads có thể có TCCL khác nhau và nhìn thấy providers khác nhau.

Javadoc lưu ý **không nên cache `ServiceLoader.load(service)` VM-wide** ở môi trường nhiều ứng dụng: đối tượng ấy giữ **ngữ cảnh discovery** gắn với TCCL lúc tạo, provider caches có thể không phù hợp với ứng dụng khác; còn có nguy cơ giữ class loader sống.

**Không hiểu nhầm TCCL là defining loader.** TCCL chọn ngữ cảnh khám phá; provider class thực sự được định nghĩa bởi loader nào còn tùy delegation, module graph và URL sources. Nếu host truyền service interface `Class<?>` do host định nghĩa, loader plugin vẫn phải resolve về đúng shared API identity.

Các API liên quan:

- `ServiceLoader.load(Class<S> service)`: dùng TCCL.
- `ServiceLoader.load(Class<S> service, ClassLoader loader)`: chọn loader tường minh.
- `ServiceLoader.load(ModuleLayer layer, Class<S> service)`: khám phá named-module providers trong layer/ancestors; **không** lấy unnamed-module classpath providers như overload theo class loader.
- `ServiceLoader.loadInstalled(service)`: dùng platform loader; không thay thế cách load plugin trên application classpath.

**Không dùng `reload()` để “đổi TCCL” của một ServiceLoader đã tạo.** Nếu ngữ cảnh loader/layer cần thay, tạo một **ServiceLoader mới** với ngữ cảnh mới.

## 19. Provider lifecycle không do ServiceLoader quản lý thay bạn

Provider có thể chỉ là pure function, cũng có thể giữ tài nguyên:

```java
public final class ConnectionProvider
        implements SomeService, AutoCloseable {
    // Có thể sở hữu connection, executor hoặc subscription.

    @Override
    public void close() {
        // Giải phóng tài nguyên theo contract ứng dụng.
    }
}
```

Đây là **mã minh họa**, cần interface `SomeService` và implementation methods cụ thể nếu muốn biên dịch thành chương trình; không phải ví dụ chạy độc lập.

`ServiceLoader.reload()` **không tự gọi `close()`** trên những providers đã được trả cho host. Nếu caller giữ references, object có thể tiếp tục sống.

Một registry có lifecycle hợp lý nên:

- Có interface `start/stop/close` hoặc adapter lifecycle riêng.
- Ngăn tạo request mới trên provider đã bị thay.
- Chờ các requests đang chạy hoàn tất, hoặc hủy chúng có kiểm soát.
- Unregister listeners/callbacks, ngắt background tasks và dọn `ThreadLocal`/TCCL nếu framework đã thay chúng.
- Đóng file/socket/resources của provider; sau đó mới đóng `URLClassLoader` nếu loader thuộc quyền sở hữu của registry.
- Bỏ references tới instances/`Class<?>`/`Method`/`Provider<?>`/ServiceLoader/streams cũ khi muốn cho phép class unloading.

**GC/class unloading không tức thì:** ngay cả sau `URLClassLoader.close()`, một strong reference từ host registry hoặc thread cũng có thể giữ plugin loader và toàn bộ classes sống.

## 20. Chi phí caching và lựa chọn kiến trúc

Cache có lợi:

- Không khởi tạo lại provider instance qua mỗi lượt iterator.
- Tránh discovery/constructor lặp lại khi registry cần dùng dịch vụ nhiều lần.
- Giữ identity/states của provider object ổn định trong một `ServiceLoader` khi chưa reload.

Nhưng cache có trade-off:

- **Provider state được chia sẻ**: nếu provider mutable, những nơi dùng chung một cached instance có thể can thiệp lẫn nhau.
- **Lifetime kéo dài**: `ServiceLoader` có thể giữ provider instances và classes sống lâu hơn dự kiến.
- **Không tự thấy thay đổi**: thêm JAR/module, đổi config hoặc phiên bản implementation cần discovery plan cụ thể.
- **Nguy cơ concurrency** nếu nhiều threads duyệt cache và reload cùng lúc.
- **Khó quản lý phiên bản** nếu một đối tượng ServiceLoader bị giữ toàn JVM trong khi tenants có TCCL khác nhau.

Nếu chỉ cần **một provider chọn một lần khi startup**, việc lưu một immutable registry của providers đã xác thực thường đơn giản hơn. Nếu cần **thay provider lúc runtime**, thiết kế registry theo version/generation và class loader ownership, không chỉ bấm `reload()`.

### “Singleton provider” là quyết định của ai?

`ServiceLoader` giúp cache instance ở đường iterator, nhưng **không phải container quản lý singleton toàn chương trình**. Một provider constructor có thể tạo instance thứ hai khi:

- dùng `ServiceLoader` object khác;
- gọi `reload()` rồi iterate lại;
- dùng provider factory tạo instance theo contract riêng;
- có class loader khác định nghĩa provider type;
- ứng dụng tự gọi constructor/factory ngoài ServiceLoader.

Nếu service bắt buộc singleton theo nghiệp vụ, hãy thiết kế invariant/lifecycle tại provider/container layer; đừng suy nó chỉ từ cache behavior.

## 21. Checklist triển khai an toàn

Trước khi dùng `ServiceLoader` trong host/plugin framework:

1. **Chọn service API identity**: interface phải cùng `Class<?>` ở host và provider.
2. **Chọn discovery context**: explicit `ClassLoader`, TCCL hay `ModuleLayer`; không trộn khi thiếu lý do.
3. **Đóng gói descriptor đúng**: `META-INF/services/<service binary name>` cho classpath, `uses`/`provides` cho named modules.
4. **Biết lazy semantics**: `load` chưa nhất thiết tạo provider; iterator/stream/findFirst gây discovery khi được duyệt.
5. **Biết caching semantics**: iterator phát instances cached trước; `stream` cung cấp descriptors; không đoán `Provider.get()` là singleton.
6. **Quy định lúc `reload`**: clear cache, bỏ iterator/stream cũ, không sử dụng song song.
7. **Không chia sẻ `ServiceLoader` thiếu synchronization** giữa threads. Nếu cần throughput, snapshot providers sau discovery tuần tự.
8. **Xác nhận provider thread safety** độc lập; stateful service cần synchronization/factory/instance-per-call.
9. **Xác thực provider configuration errors** với context/cause, không nuốt lỗi.
10. **Không nhầm cache reload với class reload**; chọn class loader mới khi cần nạp definitions khác.
11. **Công bố registry atomic có lifecycle** nếu hot-swap, đảm bảo old consumers kết thúc.
12. **Dọn references và close tài nguyên** của provider/URLClassLoader đúng thời điểm.
13. **Test đúng runtime packaging**; IDE classpath dễ che thiếu descriptors/duplicate APIs.
14. **Test concurrency**: iterate/reload giao nhau phải được ngăn trong thiết kế; service object dùng đồng thời phải được kiểm chứng riêng.

## 22. Câu hỏi tự kiểm tra có đáp án

**1. `ServiceLoader.load(S.class)` có tạo ngay tất cả provider instances không?** Không. Provider discovery và instantiation có tính lazy; iterator/stream/findFirst mới thực hiện công việc theo nhu cầu.

**2. Hai lần `findFirst()` trên cùng ServiceLoader khi chưa reload có thể trả cùng object không?** Có. Khi provider được tạo/cached theo iterator, lượt sau nhận lại cached instance.

**3. `reload()` xóa những gì?** Provider caches của ServiceLoader instance ấy để lần duyệt mới khám phá lại.

**4. `reload()` có thay bytecode của class đã loaded không?** Không. Nó không redefine/unload runtime class.

**5. Tại sao ví dụ A in `provider ctor #2` sau reload nhưng `same class=true`?** Có object mới từ cùng `Class<?>` của cùng defining loader, nên static construction counter tiếp tục tăng.

**6. Sau `reload()` có được dùng iterator cũ không?** Không. Javadoc yêu cầu discard; `hasNext/next` có thể ném `ConcurrentModificationException`.

**7. `ConcurrentModificationException` sau reload có chứng minh hai thread chạy đồng thời không?** Không. Một thread cũng có thể làm iterator cũ invalid bằng `reload`.

**8. `stream()` trả trực tiếp service instance không?** Không. Nó trả `Stream<ServiceLoader.Provider<S>>`; gọi `Provider.get()` để lấy instance.

**9. Có thể gọi `Provider.get()` lặp lại rồi tin chắc cùng object không?** Không. API không hứa singleton identity như thế.

**10. Một `ServiceLoader` có thread-safe không?** Không. Java 17 Javadoc cảnh báo rõ instances không safe cho nhiều concurrent threads.

**11. Sau khi snapshot providers, mọi provider có tự thread-safe không?** Không; provider thread safety là hợp đồng riêng của implementation.

**12. Vì sao `ServiceLoader.load(service)` không nên cache VM-wide trong container?** Vì overload này dùng TCCL; các applications có discovery contexts khác nhau và việc cache có thể giữ class loader/provider sống.

**13. Plugin cập nhật JAR nhưng `reload()` vẫn chạy code cũ: vì sao?** ClassLoader có thể đã định nghĩa runtime class từ bytes cũ; reload chỉ đổi provider cache.

**14. Nếu hai URLClassLoader riêng định nghĩa provider cùng binary name, static counter có chia sẻ không?** Không. Hai runtime class identities có static states riêng.

**15. Host interface trùng tên với bản plugin nhưng cast fail, nguyên nhân có thể là gì?** Shared API bị define hai lần bởi defining loaders khác nhau; cần delegation về đúng API class.

**16. `ServiceLoader.reload()` có gọi `AutoCloseable.close()` trên provider objects cũ không?** Không. Ứng dụng phải tự quản lifecycle.

**17. Khi nào nên tạo ServiceLoader mới thay vì reload cái cũ?** Khi cần đổi discovery loader/TCCL/layer, xây registry theo generation hoặc không muốn chia sẻ caches.

**18. Một service-configuration file có thể bỏ qua những lỗi về provider class không?** Không nên. Cần phân biệt “không có provider” với `ServiceConfigurationError` do cấu hình hoặc class loading/instantiation thất bại.

## 23. Tài liệu chuẩn để đối chiếu

- [Java SE 17 — `ServiceLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): provider discovery, caching, `iterator`, `stream`, `findFirst`, `reload`, concurrency, TCCL và module layers.
- [Java SE 17 — `ServiceLoader.Provider` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.Provider.html): `type()`, `get()` và provider descriptors.
- [Java SE 17 — `ClassLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, runtime class visibility và resource lookup.
- [Java SE 17 — `URLClassLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): plugin class loaders từ URL, `close()` và phạm vi lookup.
- [Java SE 17 — `ModuleLayer` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html): named module layers khi triển khai providers.
- [Java Virtual Machine Specification, Java SE 17 — Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): defining loader, class identity, loading/linking và class lifecycle.

**Kết luận:** `ServiceLoader` **cache provider phục vụ lazy discovery/iteration**, `reload()` **xóa cache và làm iterator/stream cũ không còn hợp lệ**, nhưng **không nạp lại bytecode của provider class**. Một `ServiceLoader` **không dùng đồng thời an toàn**; hãy phát hiện provider ở giai đoạn được kiểm soát, xây registry/snapshot, đánh giá thread safety của từng provider và quản lý hot-swap bằng lifecycle cùng class loader ownership thật sự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-loading-009">ServiceConfigurationError và provider không tương thích kiểu dịch vụ</a>

<details>
<summary>Xem chi tiết</summary>

# ServiceConfigurationError và provider không tương thích kiểu dịch vụ trong Java

## 1. Vấn đề: tên interface đúng nhưng ServiceLoader vẫn từ chối provider

`java.util.ServiceLoader` là cơ chế Java Service Provider Interface (**SPI**) dùng để tìm và tạo các implementation của một service contract. Bên sử dụng chọn một **`Class<S>` cụ thể** làm service type, ví dụ `Greeter.class`. Bên cung cấp khai báo implementation `PluginGreeter` và đăng ký nó bằng file trong `META-INF/services/` đối với ứng dụng chạy qua classpath, hoặc bằng `provides ... with ...` trong named module.

Một lỗi đáng chú ý là **`java.util.ServiceConfigurationError`**, thường kèm mô tả như *provider not a subtype*. Điều này xảy ra khi ServiceLoader tìm được provider nhưng provider không thỏa **quan hệ kiểu runtime** mà service type yêu cầu.

~~~text
ServiceLoader.load(api.Greeter.class, pluginLoader)
                        |
                        v
Đọc META-INF/services/api.Greeter
                        |
                        v
Tìm plugin.PluginGreeter bằng pluginLoader
                        |
                        v
Kiểm tra provider có tương thích api.Greeter?
          /                              \
         có                              không
          |                                |
     khởi tạo provider            ServiceConfigurationError
          |
   trả instance api.Greeter
~~~

Nguyên nhân có thể đơn giản: provider class **thật sự không implements service interface**. Nhưng trong hệ thống nhiều `ClassLoader`, nó có thể tinh vi hơn: provider **có khai báo implements đúng tên interface**, song interface của provider được loader plugin tự định nghĩa **khác Class runtime** so với interface của host.

**Quy tắc quyết định:** JVM xác định danh tính của class/interface bằng **(binary name, defining class loader)**. Vì thế hai interface có cùng `getName()` chưa chắc là một kiểu. ServiceLoader không chấp nhận provider chỉ vì văn bản `implements api.Greeter` hay `providerClass.getName()` trông hợp lý.

Bài này tập trung vào các lỗi cấu hình SPI, type identity, parent delegation, API dùng chung, lazy discovery, cách phân biệt provider sai với loader sai và một thí nghiệm Java 17+ có thể chạy độc lập để tái hiện lỗi rồi sửa đúng.

## 2. Các thành phần của Service Provider Interface

Một SPI thường có bốn thành phần:

1. **Service contract**: interface hoặc abstract class công khai mà ứng dụng muốn gọi.
2. **Provider implementation**: class cung cấp hành vi cho service ấy.
3. **Provider configuration/registration**: metadata giúp Java biết tên implementation.
4. **Consumer**: ứng dụng dùng `ServiceLoader<S>` để khám phá, khởi tạo và gọi provider.

Với classpath, ví dụ:

~~~text
api.Greeter                           <- interface chung
plugin.EnglishGreeter                 <- implementation
META-INF/services/api.Greeter        <- descriptor chứa:
   plugin.EnglishGreeter
~~~

**Tên file descriptor là binary name của service type**, không phải tên tùy ý: `META-INF/services/api.Greeter`. Nội dung tệp là **binary name của provider class**, mỗi provider một dòng. Tên có dấu chấm, không đổi dấu chấm thành `/` trong phần tên file service.

Một ứng dụng consumer điển hình:

~~~java
ServiceLoader<Greeter> loader = ServiceLoader.load(Greeter.class);
for (Greeter provider : loader) {
    System.out.println(provider.greet());
}
~~~

**Đoạn trên chỉ minh họa**; giả sử `Greeter` tồn tại và khai báo `greet()`. ServiceLoader là một **iterable lazy discovery mechanism**: việc gọi `load` không chứng minh mọi provider đều đã nạp hoặc tạo instance thành công. Những lỗi cấu hình có thể chỉ xuất hiện khi bắt đầu duyệt `iterator()` / `stream()` hoặc lấy provider.

## 3. Khi nào ném ServiceConfigurationError?

`ServiceConfigurationError` là một **`Error`**, không phải subclass của `Exception`. Nó biểu thị vấn đề khi tìm, xác thực hoặc tạo service providers.

Các nhóm nguyên nhân thường gặp:

| Nhóm | Ví dụ | Giai đoạn có thể lỗi |
| --- | --- | --- |
| Không đọc được descriptor | `META-INF/services/api.Greeter` bị lỗi I/O | Đọc cấu hình |
| Nội dung descriptor sai | Tên provider không hợp lệ, cấu hình sai cú pháp | Parse descriptor |
| Provider không tồn tại | File liệt kê `plugin.MissingGreeter` nhưng class không có | Load provider class |
| Provider không phải subtype | Class không implement đúng service type runtime | Kiểm tra type |
| Constructor không phù hợp | Classpath provider không có public no-arg constructor | Chọn cách tạo instance |
| Khởi tạo provider thất bại | Constructor/factory ném exception | Tạo provider |
| Cấu hình named module sai | Service type, `uses`/`provides` hoặc provider factory không hợp lệ | Resolution/discovery/instantiation |
| Loader/version conflict | Service API bị định nghĩa trùng, dependency không tương thích | Load/link/type check |

**Không nên coi mọi lỗi `ServiceConfigurationError` đều do cấu hình tệp descriptor.** Khi dịch vụ đã tìm thấy provider class nhưng không tương thích với chính service Class mà consumer truyền vào, nguyên nhân là **type identity** hoặc implementation sai.

Một số vấn đề sâu hơn như `LinkageError`, `ExceptionInInitializerError` hoặc lỗi quyền module có thể xuất hiện riêng hoặc được ServiceLoader báo thông qua `ServiceConfigurationError` tùy code path và Java version. Muốn chẩn đoán đúng phải xem **cause, message, service Class, provider Class và defining loaders**.

## 4. Provider “not a subtype” thực sự có nghĩa gì?

Giả sử consumer có:

~~~java
Class<?> service = api.Greeter.class;
Class<?> provider = plugin.PluginGreeter.class;

boolean compatible = service.isAssignableFrom(provider);
~~~

Đây là **đoạn minh họa**, với các class có thật trong dự án. Đối với **classpath provider dùng public no-arg constructor thông thường**, ServiceLoader yêu cầu provider class tương thích với service type; nếu không, nó từ chối cấu hình.

Hai trường hợp:

### Trường hợp A: Provider thực sự không implements API

~~~java
package plugin;

public class WrongGreeter {
    public String greet() {
        return "hello";
    }
}
~~~

`WrongGreeter` có một method tên `greet` nhưng **không khai báo implements `api.Greeter`**. Java không áp dụng structural typing cho service provider kiểu classpath này. Chỉ trùng method signature chưa đủ.

Nếu descriptor đăng ký `plugin.WrongGreeter` cho `api.Greeter`, việc provider không phải subtype là đúng.

### Trường hợp B: Provider implements cùng tên API nhưng từ loader khác

~~~text
Host ClassLoader             Plugin ClassLoader
        |                            |
        v                            v
api.Greeter [H]              api.Greeter [P]
        ^                            ^
        |                            |
Consumer uses H            plugin.PluginGreeter implements P

H.getName() = P.getName() = "api.Greeter"
H == P => false
H.isAssignableFrom(plugin.PluginGreeter) => false
~~~

Provider source có thể viết đúng `implements api.Greeter`, nhưng JVM giải quyết `api.Greeter` trong provider thành **P**, còn consumer yêu cầu **H**. Không có quan hệ subtype với H, ServiceLoader vẫn từ chối.

Hai `ClassLoader` khác nhau **không mặc nhiên** có lỗi: chỉ khi API Class bị **định nghĩa riêng**, không cùng được ủy quyền cho parent, mới phát sinh sự khác biệt identity này.

## 5. Tại sao ClassLoader và ServiceLoader liên quan trực tiếp?

Overload:

~~~java
ServiceLoader<Greeter> services =
        ServiceLoader.load(Greeter.class, pluginLoader);
~~~

Nó có hai đối tượng với vai trò khác nhau:

- **`Greeter.class`** là **service Class mà consumer nhận làm hợp đồng**. Class ấy có defining loader riêng, thường là host/common parent loader.
- **`pluginLoader`** là **loader sử dụng để khám phá và tải providers** theo hợp đồng API. Nó có thể load class provider, nhưng việc cung cấp loader khác **không biến service Class của host thành service Class của plugin**.

Dù provider loader tìm được tệp `META-INF/services/api.Greeter` và class `plugin.PluginGreeter`, nếu implementation resolve interface `api.Greeter` từ một classloader khác với host, type check vẫn thất bại.

**Thread Context ClassLoader** thường được dùng bởi overload `ServiceLoader.load(Greeter.class)`. Trong container/plugin, TCCL giúp provider discovery nhìn thấy đúng nơi, nhưng **đổi TCCL không tự nhập hai Class runtime đã bị định nghĩa riêng thành một**. Cần có chính sách chia sẻ service API đúng.

Cách tổ chức thường tốt:

~~~text
            Common / Host ClassLoader
                     |
                 api.Greeter
                 api.Request
                 api.Result
                     ^
             parent delegation
                     |
             Plugin ClassLoader
                     |
             plugin.PluginGreeter
             plugin.privateLib.*
             META-INF/services/api.Greeter
~~~

Implementation/private dependencies có thể thuộc loader riêng; **service interface và các kiểu contract dùng qua boundary** phải thống nhất.

## 6. Ví dụ Java 17+ hoàn chỉnh: tạo lỗi rồi sửa đúng

Thí nghiệm dùng **cùng một plugin bytecode** nhưng hai chế độ loader:

- `isolated`: plugin loader cố tình **child-first cho `api.*`**, tự định nghĩa bản interface trùng host. ServiceLoader báo `ServiceConfigurationError`.
- `shared`: plugin loader vẫn tự định nghĩa `plugin.*` nhưng **delegate `api.*` về parent**. Provider tương thích và service hoạt động.

Ví dụ không cần thư viện ngoài JDK 17+.

### 6.1. Cấu trúc source

Tạo một thư mục `spi-loader-demo` với các file:

~~~text
spi-loader-demo/
├── src-api/
│   └── api/
│       └── Greeter.java
├── src-plugin/
│   └── plugin/
│       └── PluginGreeter.java
└── src-host/
    └── HostMain.java
~~~

Sau biên dịch sẽ có:

~~~text
host-classes/
├── HostMain.class
├── HostMain$PluginLoader.class
└── api/
    └── Greeter.class

plugin-classes/
├── api/
│   └── Greeter.class
├── plugin/
│   └── PluginGreeter.class
└── META-INF/
    └── services/
        └── api.Greeter
~~~

Việc `api/Greeter.class` xuất hiện trong **hai thư mục** là có chủ ý. Thí nghiệm chỉ đưa `host-classes` lên **application classpath**; plugin-classes được giao cho custom loader qua URL. Descriptor chỉ đặt dưới plugin-classes.

### 6.2. File src-api/api/Greeter.java

~~~java
package api;

public interface Greeter {
    String greet();
}
~~~

### 6.3. File src-plugin/plugin/PluginGreeter.java

~~~java
package plugin;

import api.Greeter;

public final class PluginGreeter implements Greeter {
    @Override
    public String greet() {
        return "hello from plugin";
    }
}
~~~

Class public có constructor mặc định public; đủ cho **classpath-based ServiceLoader provider** bình thường.

### 6.4. File src-host/HostMain.java

~~~java
import api.Greeter;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public class HostMain {
    static final class PluginLoader extends URLClassLoader {
        private final boolean duplicateApi;

        PluginLoader(Path pluginClasses, boolean duplicateApi)
                throws IOException {
            super(
                    new URL[] { pluginClasses.toUri().toURL() },
                    HostMain.class.getClassLoader()
            );
            this.duplicateApi = duplicateApi;
        }

        private boolean localFirst(String name) {
            return name.startsWith("plugin.")
                    || (duplicateApi && name.startsWith("api."));
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!localFirst(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    result = findClass(name);
                }
                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
        }
    }

    private static void runCase(
            String label, Path pluginDir, boolean duplicateApi)
            throws IOException, ClassNotFoundException {

        try (PluginLoader loader = new PluginLoader(
                pluginDir, duplicateApi)) {

            Class<?> pluginApi = loader.loadClass("api.Greeter");
            System.out.println(label + " same API Class: "
                    + (pluginApi == Greeter.class));

            ServiceLoader<Greeter> services =
                    ServiceLoader.load(Greeter.class, loader);

            try {
                Greeter provider = services.iterator().next();
                System.out.println(label + " provider: "
                        + provider.greet());
            } catch (ServiceConfigurationError failure) {
                System.out.println(label + " error: "
                        + failure.getClass().getSimpleName());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: HostMain <plugin-classes-directory>");
        }

        Path pluginDir = Path.of(args[0]).toAbsolutePath();
        runCase("isolated", pluginDir, true);
        runCase("shared", pluginDir, false);
    }
}
~~~

### 6.5. Descriptor META-INF/services/api.Greeter

File này **không có phần mở rộng** `.txt`. Tên tệp là `api.Greeter`, nằm trong `META-INF/services` tính từ **gốc classpath của plugin loader**.

Nội dung file, một dòng:

~~~text
plugin.PluginGreeter
~~~

Đặt đúng tại:

~~~text
plugin-classes/META-INF/services/api.Greeter
~~~

Đừng tạo `plugin-classes/META-INF/services/api/Greeter`. Descriptor name giữ **dấu chấm** trong binary service name.

### 6.6. Biên dịch bằng JDK 17+

Tại thư mục `spi-loader-demo`:

~~~bash
javac --release 17 -d host-classes src-api/api/Greeter.java src-host/HostMain.java
javac --release 17 -d plugin-classes src-api/api/Greeter.java src-plugin/plugin/PluginGreeter.java
~~~

Sau khi compile, **tạo descriptor theo mục 6.5**. Nếu dùng Linux/macOS/bash, có thể thực hiện:

~~~bash
mkdir -p plugin-classes/META-INF/services
printf 'plugin.PluginGreeter\n' > plugin-classes/META-INF/services/api.Greeter
java -cp host-classes HostMain plugin-classes
~~~

Nếu dùng Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force plugin-classes/META-INF/services | Out-Null
[System.IO.File]::WriteAllText(
    (Join-Path (Get-Location) 'plugin-classes/META-INF/services/api.Greeter'),
    "plugin.PluginGreeter" + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
java -cp host-classes HostMain plugin-classes
~~~

Lưu ý Java 17, UTF-8 là encoding của các provider-configuration files. PowerShell 5.1 không hỗ trợ cú pháp `::new(...)` trên mọi cấu hình? Nếu PowerShell cụ thể không hỗ trợ, có thể tạo file này thủ công bằng editor và lưu UTF-8; đây chỉ là bước chuẩn bị resource, không thay đổi Java code.

### 6.7. Stdout chính xác

Với các tệp như trên và application classpath chỉ có `host-classes`, kết quả:

~~~text
isolated same API Class: false
isolated error: ServiceConfigurationError
shared same API Class: true
shared provider: hello from plugin
~~~

### 6.8. Phân tích kết quả

**Ở chế độ isolated:**

1. `PluginLoader.loadClass("api.Greeter")` chọn bản `api.Greeter` dưới `plugin-classes` vì `duplicateApi=true`.
2. `HostMain` biên dịch với bản `api.Greeter` nằm dưới `host-classes`, thuộc host application loader.
3. Cả hai tên đều là `api.Greeter` nhưng `Class<?>` khác, vì defining loader khác.
4. `plugin.PluginGreeter` được plugin loader định nghĩa và resolve `implements api.Greeter` về bản API của plugin.
5. `ServiceLoader.load(Greeter.class, loader)` giữ **service Class của host**, không đổi sang service Class của plugin. Nó tìm descriptor và provider rồi phát hiện implementation **không phải subtype của host service Class**, nên báo `ServiceConfigurationError`.
6. Code in **tên Error type**, không in message vì nội dung thông báo có thể khác theo JDK.

**Ở chế độ shared:**

1. `PluginLoader` vẫn load `plugin.PluginGreeter` local-first.
2. Nhưng `api.Greeter` không bị chọn local-first; loader ủy quyền cho parent và nhận **đúng một Class của host**.
3. Provider implement host API, nên subtype check thành công.
4. ServiceLoader tạo provider qua public no-arg constructor và gọi `greet()`, in `hello from plugin`.

Sự thay đổi nằm ở **loader policy cho package `api.*`**, không phải sửa tên interface, constructor, descriptor hay plugin method. Đây là phương án sửa có hệ thống cho lỗi duplicated service API.

## 7. Tại sao phải kiểm tra “cùng Class” thay vì “cùng tên”?

Một chẩn đoán thường thấy:

~~~java
Class<?> expected = Greeter.class;
Class<?> actual = pluginLoader.loadClass("api.Greeter");

System.out.println(expected.getName());
System.out.println(actual.getName());
~~~

**Mã minh họa**: hai dòng có thể in `api.Greeter` như nhau và khiến người đọc tưởng loader không lỗi.

Cần kiểm tra thêm:

~~~java
System.out.println(expected == actual);
System.out.println(expected.getClassLoader());
System.out.println(actual.getClassLoader());
~~~

Nếu `expected == actual` là false, cần xem **parent chain, child-first rules, API JAR duplicates**. Một provider implements đúng interface **của nó** nhưng chưa chắc implement đúng interface **của consumer**.

Với provider class đã tải được:

~~~java
boolean assignable = Greeter.class.isAssignableFrom(providerClass);
~~~

Nếu false, thử hỏi `providerClass.getInterfaces()` và defining loaders của những interfaces ấy. Đừng chỉ so tên chuỗi của interfaces.

## 8. “Not a subtype” không phải lúc nào cũng do nạp trùng API

Hai nhóm nguyên nhân phổ biến:

### Provider code khai báo sai

Descriptor ghi `plugin.WrongGreeter`, nhưng class đó không implement `api.Greeter`, hoặc compile nhầm version interface, hoặc class là adapter chưa được khai báo implements. Cần sửa provider class đúng contract.

### API bị định nghĩa lại

`PluginGreeter implements api.Greeter` trong source, nhưng `api.Greeter` trong bytecode được plugin class loader giải quyết tới một runtime Class khác. Cần sửa **loader ownership** hoặc cách đóng gói, không phải thêm `implements` lần nữa.

Một quy trình phân loại:

| Quan sát | Kết luận gợi ý | Bước sửa |
| --- | --- | --- |
| Provider không khai báo implements service | Sai class/descriptor | Sửa class hoặc descriptor |
| Provider khai báo implements, service/interface Class khác | Duplicate API via loaders | Share API ở parent |
| `isAssignableFrom` đúng, tạo instance lỗi | Constructor/factory/access/init | Xem constructor và cause |
| Class không tìm thấy | Lookup/packaging | Xem classpath/JAR và loader |
| Descriptor không tìm được | Provider discovery path | Kiểm tra `META-INF/services` |
| Lỗi xảy ra khi module configuration tạo | JPMS descriptor/graph | Xem `uses`, `provides` và module readability |
| ServiceLoader liệt kê provider nhưng cast fail nơi khác | Boundary/API identity | Kiểm tra tất cả DTO/exception/annotation contract |

## 9. Provider-configuration file: những yêu cầu dễ sai

Trong classpath-based SPI, tệp `META-INF/services/<binary service name>` có các quy tắc:

- Tên tệp khớp **binary service name**, ví dụ `META-INF/services/api.Greeter`.
- Tên provider bên trong là **fully qualified binary class name**, không phải filesystem path.
- Mỗi dòng khai báo một provider; dấu `#` mở đầu comment, khoảng trắng được xử lý theo quy tắc định dạng.
- Cấu hình nên được lưu UTF-8.
- Provider class thông thường phải là public concrete class có **public zero-argument constructor** khi classpath registration được dùng.
- Một provider xuất hiện trùng tên trong các cấu hình thường không tạo ra hai provider riêng của cùng implementation theo quy tắc deduplication, nhưng **đừng dựa vào trật tự khai báo** để chọn provider quan trọng.
- Resource không đọc được, encoding/syntax sai, class không tìm thấy, constructor không hợp lệ hoặc provider không đúng subtype đều có thể gây `ServiceConfigurationError` khi duyệt.

Lệnh hữu ích:

~~~bash
jar --list --file plugin.jar
~~~

Khi `plugin.jar` tồn tại, kiểm tra có `META-INF/services/api.Greeter` và `plugin/PluginGreeter.class` hay không. Đây mới chỉ là **xác minh packaging**, chưa bảo đảm provider Class có cùng service type với host.

Một JAR có cả `api/Greeter.class` và implementation có thể hoạt động bình thường nếu loader luôn **parent-first cho API package**. Chính sách load mới quyết định bản API đó có thực sự bị define lần hai hay không.

## 10. Lazy discovery: tại sao gọi load() chưa báo lỗi?

Ví dụ:

~~~java
ServiceLoader<Greeter> services =
        ServiceLoader.load(Greeter.class, loader);

System.out.println("created ServiceLoader");

for (Greeter provider : services) {
    System.out.println(provider.greet());
}
~~~

Đây là **mã minh họa** (có service type và loader phù hợp). Câu lệnh khởi tạo `ServiceLoader` thường chưa tạo tất cả providers. Khi chương trình bắt đầu iterate, Java mới đọc cấu hình, xác thực provider class, khởi tạo instance theo yêu cầu.

Vì vậy:

- Test chỉ gọi `ServiceLoader.load(...)` rồi kết luận SPI hợp lệ là chưa đủ.
- Có thể provider thứ nhất hoạt động, provider thứ hai bị lỗi khi tiếp tục iterate.
- `ServiceLoader.stream()` cung cấp stream của `ServiceLoader.Provider<S>`; **lấy danh sách provider types** và **gọi `Provider.get()`** không hoàn toàn cùng điểm tạo instance.
- Đừng dựa vào thứ tự provider khám phá trong mọi classloader/module environment để báo “provider nào sẽ được load trước” nếu API không cam kết thứ tự cố định.
- `ServiceLoader.reload()` xóa cache nội bộ và khiến discovery lại trên những lần duyệt sau; nó **không đổi runtime Class đã được loader định nghĩa** hay tự sửa lỗi API duplication.

### Lưu ý khi catch ServiceConfigurationError

Vì `ServiceConfigurationError extends Error`, đoạn:

~~~java
try {
    for (Greeter g : services) {
        g.greet();
    }
} catch (Exception e) {
    System.out.println("caught");
}
~~~

**Không phải mẫu bắt lỗi cấu hình đúng**: `catch (Exception)` không bắt `ServiceConfigurationError`.

Nếu muốn chẩn đoán/disable một provider không hợp lệ tại boundary:

~~~java
try {
    Greeter provider = services.iterator().next();
} catch (java.util.ServiceConfigurationError error) {
    System.err.println("SPI discovery failed: "
            + error.getMessage());
}
~~~

Đây là snippet minh họa. Có thể bắt đúng loại Error này ở **ranh giới vận hành plugin** để ghi log/chuyển trạng thái, nhưng không nên bắt mọi `Error` hoặc mọi `Throwable` rồi tiếp tục như không có lỗi: những lỗi VM/linkage nghiêm trọng cần xử lý khác.

## 11. Named modules: khác biệt với classpath descriptor

Từ Java 9, provider đăng ký trong **named module** thường dùng `module-info.java` thay cho descriptor classpath.

Ví dụ module API:

~~~java
module greeter.api {
    exports api;
}
~~~

Module consumer:

~~~java
module greeter.app {
    requires greeter.api;
    uses api.Greeter;
}
~~~

Module implementation:

~~~java
module greeter.impl {
    requires greeter.api;
    provides api.Greeter with plugin.PluginGreeter;
}
~~~

**Ba descriptors này chỉ minh họa sơ đồ module**, chưa phải dự án runnable hoàn chỉnh; để chạy còn cần Java sources, module paths và module resolution hợp lệ.

Các điểm quan trọng:

- `uses api.Greeter` khai báo service dependency trong named consumer module.
- `provides api.Greeter with plugin.PluginGreeter` khai báo provider của service.
- Provider module phải dùng **cùng service contract** theo module resolution, không tự ship một class `api.Greeter` khác trong module implementation.
- Trong named modules, một provider có thể sử dụng **public static provider method** có tên `provider` (theo quy tắc `ServiceLoader`) trả service implementation; vì thế không được áp dụng máy móc điều kiện “provider class phải implements service” cho **mọi kiểu provider factory trong named module**.
- Khi dùng direct provider class thay vì provider method, provider phải phù hợp service type và hình thức construction được JDK chấp nhận.
- `ServiceLoader.load(ModuleLayer layer, Class<S> service)` tìm providers trong layer và các parent layers theo hợp đồng API, có semantics khác với chỉ quét các tệp descriptor từ một URLClassLoader.
- Module readability, module roots, package exports/opens và service binding có thể gây những lỗi **trước khi ServiceLoader tạo provider**, không nên gộp mọi lỗi thành “not a subtype”.

**Sự khác nhau quan trọng:** lớp provider chứa trong named module có thể hợp lệ nhờ một `provider()` factory trả service type, ngay cả nếu chính provider class không subtype trực tiếp. Ở ví dụ classpath bên trên, provider implementation thông thường phải subtype và có public no-arg constructor.

## 12. ModuleLayer, service Class identity và plugin architecture

Một hệ thống plugin có thể tự tạo `ModuleLayer` cho từng plugin. Khi làm vậy, cần đảm bảo module chứa shared service API thuộc **parent/common layer** và được các plugin implementations tham chiếu nhất quán.

Sơ đồ đề xuất:

~~~text
Boot / Parent ModuleLayer
  greeter.api
    api.Greeter
       ^
       |
Plugin ModuleLayer A
  greeter.impl.a
    provides api.Greeter

Plugin ModuleLayer B
  greeter.impl.b
    provides api.Greeter
~~~

Nếu mỗi layer tự định nghĩa `api.Greeter` từ bản copy API module bằng class loader khác, host không thể coi hai Class ấy là cùng contract chỉ vì module/binary names giống nhau.

Một số lỗi JPMS khác cần tách riêng:

| Lỗi | Nhóm điều tra |
| --- | --- |
| `FindException` | Không tìm thấy named module cần thiết |
| `ResolutionException` | Module graph/readability/export conflict |
| `LayerInstantiationException` | Mapping module tới loader, overlapping packages hoặc layer conditions |
| `IllegalAccessError` / `InaccessibleObjectException` | Module access/exports/opens |
| `ServiceConfigurationError` | Provider discovery/configuration/subtype/construction |
| `ClassCastException` | Typed cast qua runtime Class không tương thích |

Chuyển sang named modules **không tự sửa** một API class bị duplication giữa loaders. Cần thiết kế ownership của service Class tại boundary.

## 13. Thread Context ClassLoader (TCCL): cần nhưng không đủ

Nhiều thư viện gọi:

~~~java
ServiceLoader<Greeter> services =
        ServiceLoader.load(Greeter.class);
~~~

Overload này thường sử dụng `Thread.currentThread().getContextClassLoader()` để chọn phạm vi tìm providers. Khi host và plugin có nhiều loader, TCCL có thể đang trỏ về một loader không nhìn thấy descriptor/provider.

Một pattern ở host:

~~~java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    ServiceLoader<Greeter> services =
            ServiceLoader.load(Greeter.class);
    // Duyệt service providers trong phạm vi tác vụ plugin.
} finally {
    thread.setContextClassLoader(previous);
}
~~~

Đây là **đoạn minh họa**, `pluginLoader` và `Greeter` cần có thật. Phải restore TCCL để tránh ảnh hưởng công việc khác trên cùng thread.

TCCL giúp API tìm provider metadata/classes ở phạm vi loader muốn chọn, nhưng **không thay đổi defining loader của `Greeter.class` mà caller đang giữ**. Nếu plugin class implement bản `api.Greeter` khác runtime Class, ServiceLoader vẫn từ chối.

Với ứng dụng đa luồng, async tasks hoặc executors, TCCL có thể không được truyền qua thread boundary theo ý muốn; ưu tiên overload nhận loader tường minh khi bạn quản lý rõ service discovery context.

## 14. Những lỗi cấu hình/packaging phổ biến

### Descriptor bị đặt sai thư mục

Sai:

~~~text
src/META-INF/services/api.Greeter
app/META-INF/services/api/Greeter
META-INF/services/plugin.PluginGreeter
~~~

Đúng cho service `api.Greeter`:

~~~text
META-INF/services/api.Greeter
~~~

Resource phải nằm ở **classpath/JAR root** mà loader dùng khi tìm `META-INF/services/...`.

### Nội dung ghi class không tồn tại

~~~text
plugin.GreeterProviderV2
~~~

Nếu artifact chỉ chứa `plugin.PluginGreeter`, ServiceLoader có thể báo provider không được tìm thấy. Đây là lỗi lookup, không phải type identity.

### Provider không có constructor/factory phù hợp

Một class chỉ có constructor yêu cầu tham số mà không có no-arg constructor có thể không được ServiceLoader classpath tạo như một provider class bình thường. Với named modules có khả năng dùng provider factory theo hợp đồng riêng.

### API dependency bị đóng hai lần

Plugin fat JAR copy `api.Greeter`, child-first loader define bản plugin. Đây là nguồn của lỗi “not a subtype” dù code Java nhìn đúng.

### Sai version API

Provider được biên dịch với phiên bản `Greeter` có method signature khác, có thể gây `NoSuchMethodError`, `AbstractMethodError` hoặc những lỗi linkage khác, không nhất thiết chỉ `ServiceConfigurationError`. Kiểm tra dependency tree, module graph, classpath/JAR thực tế.

### Nhiều providers cùng service

Nếu có nhiều tệp descriptor trùng tên, ServiceLoader có thể phát hiện nhiều providers. Một provider sai làm iteration bị gián đoạn; không nên chỉ test provider đầu tiên. Khi cần thứ tự ưu tiên, hãy chọn theo metadata hoặc logic ứng dụng rõ ràng chứ không dựa mù quáng vào thứ tự JAR của môi trường.

## 15. Chẩn đoán ServiceConfigurationError có hệ thống

Khi thấy lỗi SPI:

1. **Đọc nguyên stack trace và cause**: ghi lại service name, provider name, class loaders và message; không chỉ giữ dòng `ServiceConfigurationError`.
2. **Xác nhận service Class của host**: `Greeter.class.getName()`, `getClassLoader()`, `getModule()`.
3. **Xác nhận provider descriptor**: đường `META-INF/services/api.Greeter` đúng? Nội dung có đúng binary name?
4. **Xác nhận provider class có được tìm không** bằng đúng loader mà ServiceLoader dùng.
5. **So service identity**: `hostService == pluginLoader.loadClass("api.Greeter")` trong tình huống API package tương ứng.
6. **Kiểm tra assignability**: `hostService.isAssignableFrom(providerClass)` với đúng Class của provider.
7. **Quan sát provider interfaces**: `providerClass.getInterfaces()` và defining loader của từng interface.
8. **Kiểm tra construction**: public concrete class/no-arg constructor đối với classpath provider thông thường, hoặc đúng provider factory trong named module.
9. **Kiểm tra dependency scope**: plugin có đóng kèm API copy trong fat JAR? Maven/Gradle đã cấu hình provided/compileOnly khi host cung cấp API chưa?
10. **Kiểm tra loader policy**: shared `api.*` có parent-first? Private `plugin.*` có đúng loader riêng?
11. **Kiểm tra TCCL và module layer**: API được gọi với loader nào, service đã được resolved trong module graph chưa?
12. **Sau sửa, duyệt toàn bộ providers** trong môi trường deploy thật, không chỉ gọi `ServiceLoader.load`.

Một helper:

~~~java
static void describe(String label, Class<?> type) {
    System.out.println(label + " name=" + type.getName());
    System.out.println(label + " loader=" + type.getClassLoader());
    System.out.println(label + " module=" + type.getModule());
}
~~~

Đây là **đoạn minh họa trong một Java class**, không phải file chạy độc lập.

Trong HotSpot/OpenJDK, có thể bật class loading logs:

~~~bash
java -Xlog:class+load=info -cp host-classes HostMain plugin-classes
~~~

Stdout/stderr logging của JVM khác nhau theo môi trường, nên không công bố một transcript cố định. Quan sát log chỉ hỗ trợ xác định classfile origin; **so trực tiếp Class runtime/defining loader** vẫn là bằng chứng tốt nhất về type identity.

## 16. Kiến trúc tránh duplication và bảo vệ boundary

Một thiết kế plugin đáng tin nên tách rõ:

| Thành phần | Nơi định nghĩa nên chọn |
| --- | --- |
| Service interface `api.Greeter` | Shared parent loader/module |
| Service DTO và callback types | Shared parent loader/module |
| Annotation host cần scan | Shared parent loader/module |
| Typed exception contract | Shared parent loader/module |
| Provider implementation | Plugin loader/module |
| Private third-party library | Plugin loader nếu cần cô lập |
| Java core types | Bootstrap/platform delegation |

Một số nguyên tắc:

- Không child-first bừa bãi cho mọi package có trong fat JAR.
- Không relocate/shade service API cần chia sẻ mà không có adapter contract rõ.
- Dùng Maven scope `provided` hoặc Gradle `compileOnly` khi host **thực sự cung cấp** shared API lúc chạy; cấu hình build cần phù hợp deployment, không phải quy tắc áp dụng cho mọi project.
- Kiểm tra chính artifact build: descriptor có được copy vào JAR? Provider class có được giữ sau shading/minification? Tên resource có bị đổi sai không?
- Tách lỗi discovery thành **missing provider**, **invalid descriptor**, **type mismatch**, **constructor/factory failure**, **module access/linkage** để log/hỗ trợ vận hành.
- Nếu provider do bên không tin cậy cung cấp, `ServiceLoader`/`ClassLoader` không phải sandbox quyền file/network. Cần bảo vệ ở process/OS/container boundary theo threat model.
- Một provider có thể có static initializer gây side effects hoặc lỗi khi khởi tạo; cần cân nhắc lifecycle, caching, cleanup và isolation khi plugin có thể reload.

## 17. Các tình huống biên cần hiểu rõ

### Không có provider nào được tìm

Một ServiceLoader hợp lệ có thể trả iterator rỗng khi không có provider. Gọi `iterator().next()` trên iterator rỗng dẫn tới `NoSuchElementException`, **không phải** `ServiceConfigurationError` chỉ vì không có provider.

Trong ví dụ runnable ở mục 6, file descriptor có sẵn provider nên tình huống này không xảy ra; code production nên kiểm tra `hasNext()` hoặc duyệt `for-each`.

### Descriptor liệt kê class hợp lệ nhưng không subtype

Đây là lỗi cấu hình provider đúng nghĩa. Đừng “sửa” bằng cách cast qua `Object` hoặc so tên interfaces. Cần sửa implementation hoặc loader ownership.

### Provider factory trong named module

Một public static `provider()` method theo quy tắc ServiceLoader có thể trả service type, nên class chứa factory **không nhất thiết** implement trực tiếp service interface. Đừng áp bảng subtype dành cho classpath constructor providers lên mọi named-module factory.

### Hot reload không tự thay service Class

`ServiceLoader.reload()` bỏ cache khám phá/instance của chính ServiceLoader, nhưng các Class đã được định nghĩa bởi loader cũ không bị viết lại. Để dùng plugin bytes mới với cùng tên, thường cần loader mới và thiết kế chuyển giao lifecycle; giữ API chung ở parent để tránh cast errors giữa phiên bản plugin.

### Exception từ provider implementation

Exception được ném từ `provider.greet()` là lỗi lúc **gọi nghiệp vụ sau khi provider được tạo**, không tự động trở thành `ServiceConfigurationError`. Ngược lại lỗi xảy ra trong constructor/provider factory có thể bị ServiceLoader wrap/báo là configuration error.

### ServiceLoader instance không thread-safe

Theo Javadoc, `ServiceLoader` không được bảo đảm an toàn cho các thao tác concurrent tùy ý. Nếu hệ thống muốn dùng chung instance trong nhiều threads, cần kiểm soát synchronization/caching theo thiết kế. Đừng đồng thời `reload()` và iterate trên cùng instance rồi kỳ vọng behavior ổn định.

## 18. Tự kiểm tra kiến thức

**Câu 1. `ServiceConfigurationError` có phải subtype của `Exception` không?**  
Không; nó kế thừa `Error`.

**Câu 2. Vì sao một class có method `greet()` không tự là provider của `Greeter`?**  
Java cần subtype compatibility đối với provider classpath thông thường, không dựa vào structural method matching.

**Câu 3. Plugin source viết `implements api.Greeter`. Host vẫn báo not a subtype. Cần kiểm tra gì?**  
So `Greeter.class` ở host với `api.Greeter` do plugin loader resolve; kiểm tra defining loaders và child-first policy.

**Câu 4. Hai ClassLoader khác nhau có nhất thiết tạo hai Class service khác nhau không?**  
Không. Parent delegation có thể dùng một Class do common parent định nghĩa.

**Câu 5. File provider descriptor cho service `api.Greeter` tên gì?**  
`META-INF/services/api.Greeter`.

**Câu 6. Nội dung descriptor ghi đường dẫn `plugin/PluginGreeter.class` có đúng không?**  
Không. Nó phải ghi **binary provider name**, ví dụ `plugin.PluginGreeter`.

**Câu 7. `ServiceLoader.load(...)` trả về mà không lỗi đã chứng minh provider hợp lệ chưa?**  
Chưa. Discovery và instantiation diễn ra lazy khi duyệt.

**Câu 8. `Class.isAssignableFrom` trả false khi service name giống nhau vì sao?**  
Provider có thể implement một runtime service Class khác loader hoặc thật sự không implement hợp đồng.

**Câu 9. `ServiceLoader.reload()` có tự làm hai Class cùng tên trở thành một không?**  
Không. Nó chỉ làm mới cache nội bộ của ServiceLoader.

**Câu 10. Named-module provider factory có luôn đòi class chứa factory implements service interface không?**  
Không. Provider method hợp lệ có thể trả service type; quy tắc khác classpath provider-constructor thông thường.

**Câu 11. Thay TCCL sang plugin loader có chữa API duplication không?**  
Không. TCCL giúp lookup, không đổi type identity đã được define.

**Câu 12. Nên đặt interface, DTO, annotation, exception dùng chung ở đâu?**  
Ở common parent loader/module mà cả host và plugin tham chiếu thống nhất.

## 19. Tài liệu chính thức

- **Java SE 17 — ServiceLoader API:** classpath providers, `META-INF/services`, provider factories, lazy discovery, `reload`, `ServiceConfigurationError` và module semantics: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- **Java SE 17 — ServiceConfigurationError:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceConfigurationError.html
- **Java SE 17 — ClassLoader:** delegation, `loadClass`, `findClass` và loader identity: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 — Class:** `isAssignableFrom`, `getClassLoader`, `getModule`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 — ModuleLayer:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html
- **JVMS Java 17 §5.3 — Creation and Loading:** runtime identity và defining loader: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JLS Java 17 §12.2 — Loading of Classes and Interfaces:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-loading-010">Provider constructor, provider method và khác biệt theo cách triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Provider constructor, provider method và khác biệt theo cách triển khai

## 1. Vì sao cùng là service provider nhưng có hai cách tạo?

Trong Java, **ServiceLoader<S>** là cơ chế phát hiện implementation của một **service type** — thường là interface hoặc abstract class — mà application không cần biết tên cụ thể của từng implementation lúc biên dịch.

Ví dụ application dùng service **Transform**, còn nhà cung cấp triển khai có thể đến từ module hoặc JAR khác. Java hỗ trợ hai cơ chế tạo service provider, nhưng **không phải hai cơ chế đều hoạt động trong mọi kiểu triển khai**:

- **Provider constructor:** ServiceLoader trực tiếp gọi **public constructor không tham số** của provider class; provider class phải là subtype/implementation của service.
- **Provider method:** Trong **explicit named module**, một provider class có thể công bố **public static method tên chính xác provider, không có tham số**, trả về đối tượng có kiểu trả về gán được cho service; class chứa provider method **không bắt buộc implements service**.

Khác biệt mấu chốt: một JAR đăng ký provider bằng **META-INF/services trên classpath** không được ServiceLoader xử lý provider method như named module explicit. Các **automatic modules** cũng yêu cầu provider constructor, không hỗ trợ provider method.

Bài này giải thích hợp đồng của JDK 17+, ví dụ runnable về cả hai đường, tầm quan trọng của module-info.java, cách đọc **ServiceLoader.Provider.type()/get()**, lỗi thường gặp và những giới hạn của class loading/lazy discovery.

## 2. Các khái niệm chính

### 2.1 Service type

Service type **S** thường là một public interface được consumer và các providers cùng nhìn thấy:

~~~java
package lesson.api;

public interface Transform {
    String apply(String input);
}
~~~

ClassLoader và module system phải bảo đảm consumer/provider dùng **cùng runtime service type**, không chỉ cùng binary name. Nếu host và plugin mỗi bên tự define một bản **lesson.api.Transform** riêng, ServiceLoader có thể từ chối provider do không assignable hoặc phát sinh lỗi type identity liên quan.

### 2.2 Provider class

Provider class là type được liệt kê trong **provides ... with ...** của module descriptor, hoặc trong cấu hình **META-INF/services/<service-name>** trên classpath.

Không phải mọi provider class đều là **instance được giao cho consumer**: với provider method, provider class có thể chỉ là **factory holder**. Đối tượng consumer nhận được là kết quả trả về từ method provider().

### 2.3 Provider constructor

Một **provider constructor** là public constructor không có tham số. Khi provider class không khai báo provider method theo quy tắc named-module, ServiceLoader cần class ấy assignable với service và khởi tạo trực tiếp.

~~~java
public final class DirectTransform implements Transform {
    public DirectTransform() {
    }

    @Override
    public String apply(String input) {
        return input;
    }
}
~~~

Đây là snippet nằm trong bối cảnh đã có interface Transform và imports/phần package thích hợp; không phải chương trình độc lập.

### 2.4 Provider method

Một provider method là **public static no-args method tên chính xác provider**. Với explicit named module:

~~~java
public final class FactoryProvider {
    private FactoryProvider() {
    }

    public static Transform provider() {
        return new SomeTransform();
    }
}
~~~

Đây là snippet cần các types Transform/SomeTransform tương ứng. **FactoryProvider** không cần implements Transform và không cần public no-args constructor. ServiceLoader gọi **FactoryProvider.provider()** để nhận đối tượng thực sự thực hiện service.

### 2.5 ServiceLoader.Provider<S>

Từ Java 9, **ServiceLoader.stream()** cung cấp các phần tử kiểu **ServiceLoader.Provider<S>**. Mỗi phần tử có:

- **type()**: Class của provider type có thể được lọc/kiểm tra trước khi tạo instance. Với provider method, đây là **kiểu trả về được khai báo của provider()**, không nhất thiết là class chứa factory method hoặc runtime class chính xác của object.
- **get()**: lấy/khởi tạo một instance của service; có thể gọi constructor hoặc provider method tùy cách triển khai.

Đừng nhầm **ServiceLoader.Provider.get()** với method tên **provider()** trên provider class: đây là hai API khác nhau.

## 3. Bảng so sánh theo cách triển khai

| Loại triển khai | Khai báo provider ở đâu? | Cách tạo | Điều kiện bắt buộc |
| --- | --- | --- | --- |
| **Explicit named module, direct provider** | **module-info.java**: provides S with Impl | Gọi provider constructor | Provider class public, implements/extends S, có public no-args constructor, nếu không có provider method. |
| **Explicit named module, factory provider** | **module-info.java**: provides S with Factory | Gọi public static provider() | Provider class public; provider() public static không tham số và kiểu trả về assignable với S; class factory không cần implements S. |
| **Classpath / unnamed module** | **META-INF/services/<fully-qualified-service-name>** | Gọi public no-args constructor | Class liệt kê phải implements/extends S; chỉ có provider() là **không đủ**. |
| **Automatic module trên module path** | Provider metadata suy từ **META-INF/services** trong JAR | Gọi public no-args constructor | Provider class phải assignable với S và có public no-args constructor; **provider method không được hỗ trợ**. |

**Cảnh báo:** “Đặt JAR lên module path” chưa bảo đảm hỗ trợ provider method. JAR phải là **explicit named module** có module descriptor **module-info.class** (tương ứng module-info.java khi biên dịch). Automatic module nhận tên tự động từ manifest/tên JAR là trường hợp khác.

## 4. Quy tắc cho explicit named module

### 4.1 requires, exports, uses và provides

Một hệ modular điển hình có:

~~~text
lesson.api
  exports lesson.api
  chứa interface Transform

lesson.providers
  requires lesson.api
  provides lesson.api.Transform with lesson.impl.CtorTransform,
      lesson.impl.FactoryProvider

lesson.app
  requires lesson.api
  uses lesson.api.Transform
  consumer gọi ServiceLoader.load(Transform.class)
~~~

- **requires** trong module provider/consumer cho phép tham chiếu đến API module.
- **exports** của module API cho phép các module khác truy cập service interface.
- **uses** trong explicit consumer module khai báo nhu cầu sử dụng service; ServiceLoader có thể báo ServiceConfigurationError nếu thiếu theo các overload và context liên quan.
- **provides ... with ...** khai báo provider classes thực sự thuộc chính module provider; không chỉ đến file resources.
- Module provider **không nhất thiết phải exports package implementation**. Javadoc còn khuyến nghị giữ package implementation đóng kín khi có thể.

### 4.2 Nếu class có cả constructor lẫn provider method?

Khi provider class trong explicit named module **khai báo provider method hợp lệ**, ServiceLoader sử dụng **method đó** để tạo instance, thay vì chọn constructor theo cảm tính.

Do đó provider method không chỉ là một tiện ích mà caller phải chủ động gọi bằng reflection. Nó là quy tắc mà **ServiceLoader** áp dụng khi module descriptor xác định provider class.

### 4.3 Factory provider không cần implements service

Ví dụ:

~~~java
public final class FactoryProvider {
    private FactoryProvider() {
        throw new AssertionError("not constructed");
    }

    public static Transform provider() {
        return new ActualTransform();
    }
}
~~~

Trong explicit module, điều này hợp lệ nếu **ActualTransform implements Transform** và method return type gán được cho Transform. ServiceLoader không cần gọi constructor private của FactoryProvider.

Nhưng nếu chính FactoryProvider được liệt kê trong **META-INF/services** trên classpath mà không implements Transform, lookup sẽ thất bại. Đó là khác biệt lớn nhất của chủ đề.

### 4.4 Đừng nhầm public method với public instance

**provider()** bắt buộc là public và static, không tham số. Nó có thể trả về một implementation class **không public** nếu kiểu trả về khai báo phù hợp với service và quy tắc accessibility của module cho phép.

**Provider.type()** không có nghĩa Class trả về luôn có public constructor không tham số hoặc có thể được consumer tự tạo. ServiceLoader.Provider.get() mới là con đường chuẩn để lấy instance.

## 5. Ví dụ runnable Java 17+ số 1: hai provider trong explicit named module

Ví dụ hoàn chỉnh này có ba modules: API, provider và consumer. Nó chứng minh:

- Provider **CtorTransform** được tạo qua public constructor.
- **FactoryProvider** không implements service và chỉ có private constructor, nhưng được chấp nhận vì có provider().
- **ServiceLoader.stream()** cho phép đọc type metadata trước khi gọi Provider.get().
- Với factory method, **Provider.type()** chính là **kiểu trả về khai báo** (ở đây FactoryResult), không phải FactoryProvider.

### 5.1 Cấu trúc file

~~~text
project/
└── src/
    ├── lesson.api/
    │   ├── module-info.java
    │   └── lesson/api/Transform.java
    ├── lesson.providers/
    │   ├── module-info.java
    │   └── lesson/impl/
    │       ├── CtorTransform.java
    │       └── FactoryProvider.java
    └── lesson.app/
        ├── module-info.java
        └── lesson/app/Main.java
~~~

**src/lesson.api/module-info.java**:

~~~java
module lesson.api {
    exports lesson.api;
}
~~~

**src/lesson.api/lesson/api/Transform.java**:

~~~java
package lesson.api;

public interface Transform {
    String apply(String input);
}
~~~

**src/lesson.providers/module-info.java**:

~~~java
module lesson.providers {
    requires lesson.api;

    provides lesson.api.Transform
            with lesson.impl.CtorTransform,
                 lesson.impl.FactoryProvider;
}
~~~

**src/lesson.providers/lesson/impl/CtorTransform.java**:

~~~java
package lesson.impl;

import lesson.api.Transform;

public final class CtorTransform implements Transform {
    public CtorTransform() {
        System.out.println("ctor invoked");
    }

    @Override
    public String apply(String input) {
        return "ctor:" + input;
    }
}
~~~

**src/lesson.providers/lesson/impl/FactoryProvider.java**:

~~~java
package lesson.impl;

import lesson.api.Transform;

public final class FactoryProvider {
    private FactoryProvider() {
        throw new AssertionError("not constructed");
    }

    public static FactoryResult provider() {
        System.out.println("factory method invoked");
        return new FactoryResult();
    }
}

final class FactoryResult implements Transform {
    @Override
    public String apply(String input) {
        return "factory:" + input;
    }
}
~~~

**src/lesson.app/module-info.java**:

~~~java
module lesson.app {
    requires lesson.api;
    uses lesson.api.Transform;
}
~~~

**src/lesson.app/lesson/app/Main.java**:

~~~java
package lesson.app;

import lesson.api.Transform;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

public class Main {
    public static void main(String[] args) {
        List<ServiceLoader.Provider<Transform>> providers =
                ServiceLoader.load(Transform.class).stream()
                        .sorted(Comparator.comparing(
                                p -> p.type().getName()))
                        .toList();

        System.out.println("provider count = " + providers.size());

        for (ServiceLoader.Provider<Transform> p : providers) {
            System.out.println("type = " + p.type().getName());
            Transform service = p.get();
            System.out.println("result = "
                    + service.apply("abc"));
        }
    }
}
~~~

### 5.2 Biên dịch và chạy

Đứng trong thư mục **project** có source tree như trên:

~~~bash
javac --module-source-path src -d mods -m lesson.api,lesson.providers,lesson.app
java --module-path mods --add-modules lesson.providers -m lesson.app/lesson.app.Main
~~~

**Lý do dùng --add-modules lesson.providers:** bảo đảm module provider có mặt trong graph của lần chạy này để lookup tìm được module chứa provider. Không nhầm một module JAR có mặt trên module path với một module chắc chắn đã được resolved vào module graph.

**Stdout đã kiểm chứng khi biên dịch/chạy JDK 21; code tương thích Java 17+:**

~~~text
provider count = 2
type = lesson.impl.CtorTransform
ctor invoked
result = ctor:abc
type = lesson.impl.FactoryResult
factory method invoked
result = factory:abc
~~~

### 5.3 Phân tích theo từng bước

1. **ServiceLoader.load(Transform.class)** tạo service loader cho service type. Module consumer đã có **uses lesson.api.Transform**.
2. **stream()** duyệt metadata providers. **sorted()** làm thứ tự demo ổn định theo class name thay vì dựa vào thứ tự discovery.
3. Trong output, **provider count = 2** chứng tỏ cả hai entries trong provides directive được phát hiện.
4. **type = lesson.impl.CtorTransform** là class provider trực tiếp, implements Transform.
5. Lúc **p.get()** tương ứng CtorTransform, constructor public không tham số chạy và in **ctor invoked**.
6. **type = lesson.impl.FactoryResult** là declared return type của FactoryProvider.provider(), không phải tên FactoryProvider.
7. **p.get()** tiếp theo gọi **FactoryProvider.provider()**, in **factory method invoked** và trả FactoryResult.
8. **FactoryProvider** có private constructor ném AssertionError. Không xuất hiện lỗi đó, vì ServiceLoader **không** tạo instance FactoryProvider bằng constructor.
9. Cả hai objects trả về thực sự implements Transform, nên consumer chỉ gọi **service.apply("abc")** mà không cần biết nguồn.

### 5.4 Vì sao FactoryResult có thể package-private?

FactoryResult là top-level class **không public** và thực thi Transform; provider method khai báo trả về FactoryResult. Nó nằm trong cùng module/package với FactoryProvider, nên code provider có thể truy cập nó.

Consumer **không trực tiếp new FactoryResult()** và không cần package implementation được exports. ServiceLoader hỗ trợ khởi tạo provider thông qua method đã công bố trong module descriptor. Nếu consumer cố phản chiếu/sử dụng class không public đó trực tiếp, vẫn phải tôn trọng Java/JPMS accessibility.

### 5.5 Những điểm làm kết quả sai

Nếu bỏ **uses** khỏi explicit consumer module, ServiceLoader có thể báo ServiceConfigurationError theo quy tắc của API.

Nếu bỏ **--add-modules lesson.providers** trong chính ví dụ, module provider không nhất thiết nằm trong resolved graph và có thể không được tìm thấy. Nếu thay **provider()** bằng tên khác như create(), ServiceLoader không nhận nó là provider method.

Nếu method chỉ là instance method hoặc có tham số, đó **không phải** provider method hợp lệ. Nếu FactoryProvider không implements Transform và không có provider method hợp lệ, module declaration có thể bị compiler từ chối hoặc runtime lookup thất bại tùy lỗi.

## 6. Ví dụ runnable Java 17+ số 2: classpath chỉ dùng constructor

Trường hợp truyền thống triển khai SPI trên **application classpath** không có module-info.java. ServiceLoader tìm providers qua **provider-configuration file**:

~~~text
META-INF/services/<fully-qualified-binary-name-of-service>
~~~

Ví dụ service **lesson.spi.Transform** cần file:

~~~text
META-INF/services/lesson.spi.Transform
~~~

với nội dung:

~~~text
lesson.impl.ClassicTransform
~~~

### 6.1 Cấu trúc

~~~text
classpath-project/
├── cp-src/
│   └── lesson/
│       ├── spi/Transform.java
│       ├── impl/ClassicTransform.java
│       └── app/ClasspathDemo.java
└── cp-out/
    └── META-INF/
        └── services/
            └── lesson.spi.Transform
~~~

**cp-src/lesson/spi/Transform.java**:

~~~java
package lesson.spi;

public interface Transform {
    String apply(String input);
}
~~~

**cp-src/lesson/impl/ClassicTransform.java**:

~~~java
package lesson.impl;

import lesson.spi.Transform;

public final class ClassicTransform implements Transform {
    public ClassicTransform() {
        System.out.println("classic constructor invoked");
    }

    @Override
    public String apply(String input) {
        return "classic:" + input;
    }
}
~~~

**cp-src/lesson/app/ClasspathDemo.java**:

~~~java
package lesson.app;

import lesson.spi.Transform;
import java.util.ServiceLoader;

public class ClasspathDemo {
    public static void main(String[] args) {
        for (Transform service : ServiceLoader.load(
                Transform.class)) {
            System.out.println("result = "
                    + service.apply("abc"));
        }
    }
}
~~~

**cp-out/META-INF/services/lesson.spi.Transform** là text file UTF-8:

~~~text
lesson.impl.ClassicTransform
~~~

### 6.2 Biên dịch và chạy

Đứng trong thư mục **classpath-project**. Đầu tiên tạo cây source và service file như mô tả. Bảo đảm provider configuration file nằm **trong cp-out/META-INF/services** chứ không chỉ ở cp-src.

~~~bash
javac -d cp-out   cp-src/lesson/spi/Transform.java   cp-src/lesson/impl/ClassicTransform.java   cp-src/lesson/app/ClasspathDemo.java
java -cp cp-out lesson.app.ClasspathDemo
~~~

Lệnh javac nhiều dòng dùng dấu nối **backslash** cho Linux/macOS shell. Nếu dùng Windows PowerShell, nhập trên một dòng:

~~~powershell
javac -d cp-out cp-src/lesson/spi/Transform.java cp-src/lesson/impl/ClassicTransform.java cp-src/lesson/app/ClasspathDemo.java
java -cp cp-out lesson.app.ClasspathDemo
~~~

Stdout thực tế đã kiểm chứng trên JDK 21:

~~~text
classic constructor invoked
result = classic:abc
~~~

ServiceLoader đọc text provider-configuration file, thấy **lesson.impl.ClassicTransform**, dùng class loader phù hợp để tìm class, kiểm tra assignable với service, rồi gọi **public no-args constructor**.

Không có module-info.java, không dùng provides directive và không sử dụng provider method. Để đóng gói JAR, bạn phải giữ nguyên path **META-INF/services/lesson.spi.Transform** trong archive; nếu thiếu entry này, ServiceLoader có thể không phát hiện provider dù class file vẫn tồn tại.

### 6.3 Cú pháp provider-configuration file

Theo Javadoc, provider configuration là **UTF-8 text**. Mỗi dòng là **fully qualified binary name** của provider, có thể có whitespace và comment bắt đầu bằng **#**:

~~~text
# Các provider của lesson.spi.Transform
lesson.impl.ClassicTransform
# lesson.impl.AnotherTransform
~~~

Các dòng trống và comments được bỏ qua. Provider names trùng trong file hoặc nhiều files được ServiceLoader khử trùng lặp theo quy tắc API. Nếu có nhiều JAR, loader có thể dùng **ClassLoader.getResources** để tìm nhiều files có cùng path META-INF/services/....

**File này không phải Java source và không chứa dòng provides lesson.spi.Transform with ...**. Dòng đó thuộc module-info.java của explicit named module.

## 7. Tình huống sai có chủ đích: classpath không dùng provider method

Hãy tưởng tượng có class:

~~~java
package lesson.impl;

import lesson.spi.Transform;

public final class FactoryOnly {
    private FactoryOnly() {
    }

    public static Transform provider() {
        return new ClassicTransform();
    }
}
~~~

Đây là một Java source class hợp lệ nếu ClassicTransform/Transform đã tồn tại, nhưng FactoryOnly **không implements Transform**. Khi provider-configuration file đổi thành:

~~~text
lesson.impl.FactoryOnly
~~~

ServiceLoader chạy theo **classpath rule**, đòi FactoryOnly assignable với lesson.spi.Transform và có public no-args constructor. Vì FactoryOnly không thỏa, loader thất bại **dù có public static provider()**.

Với cùng ClasspathDemo và file provider-configuration thay đổi như trên, JDK 21 báo lỗi bắt đầu bằng:

~~~text
Exception in thread "main" java.util.ServiceConfigurationError: lesson.spi.Transform: lesson.impl.FactoryOnly not a subtype
~~~

Phần stack trace và line numbers có thể khác theo JDK. Dòng trên đã quan sát khi chạy ví dụ thực tế với JDK 21.

**Đây là khác biệt có ý nghĩa nhất giữa explicit modules và classpath:** Classpath ServiceLoader không nhìn thấy một phương thức provider() rồi tự áp dụng quy tắc provider factory của module explicit.

### 7.1 Cách sửa tình huống classpath

Có hai hướng chính:

- **Giữ classpath:** đăng ký một **class public implements Transform có public constructor không tham số**, ví dụ ClassicTransform.
- **Chuyển sang explicit named module:** viết **module-info.java** có provides ... with FactoryOnly và cung cấp provider() hợp lệ theo module rules.

Không phải cứ thêm chữ **static** hoặc đổi tên file META-INF/services là chuyển sang factory provider. Mô hình đăng ký là yếu tố quyết định.

## 8. Automatic module: gần named module nhưng quy tắc constructor

Một **automatic module** là JAR không có explicit module descriptor nhưng được đặt trên **module path** và được module system xem như named automatic module theo những quy tắc riêng.

Đối với ServiceLoader, Javadoc nêu rõ **automatic module bắt buộc có provider constructor**; provider method **không được hỗ trợ**.

| Trường hợp | Descriptor | Provider method? | Constructor requirement |
| --- | --- | --- | --- |
| Explicit module | module-info.class thực sự | Có | Không cần nếu dùng provider method. |
| Automatic module | Sinh tự động từ JAR không có descriptor | **Không** | Có, public no-args và assignable với service. |
| Unnamed module/classpath | Không có descriptor | **Không** | Có, public no-args và assignable với service. |

Một JAR có **Automatic-Module-Name** trong manifest vẫn là **automatic module** nếu không có explicit module-info.class. Thuộc tính manifest đặt tên module ổn định hơn, **không tự cấp tính năng provider method**.

Có thể phân phối provider theo cách classpath trước, sau đó chuyển sang explicit module. Khi chuyển, bạn phải xác định rõ cách đăng ký:

~~~text
Classpath / automatic module:
  META-INF/services/lesson.spi.Transform
    lesson.impl.ClassicTransform

Explicit module:
  module lesson.providers {
    requires lesson.api;
    provides lesson.api.Transform with lesson.impl.CtorTransform,
        lesson.impl.FactoryProvider;
  }
~~~

Trong explicit module, không nên dựa vào việc provider method được ServiceLoader tìm ra chỉ từ service file nếu không có provides directive.

## 9. Provider constructor và provider method: quy tắc hợp lệ chi tiết

### 9.1 Provider constructor

Đối với đường trực tiếp:

- Provider class phải **public**.
- Provider class phải là concrete class có thể khởi tạo và **implements/extends service**.
- Phải có **public no-args constructor**; private/package-private no-args constructor không đạt hợp đồng.
- Constructor có thể thực hiện dependency setup, nhưng exception từ constructor thường bị ServiceLoader bọc thành **ServiceConfigurationError** khi được gọi.
- Một constructor có tham số không được ServiceLoader tự dependency-inject. Nếu bạn cần factory logic, explicit named module có thể sử dụng provider method.
- Nếu class là abstract hoặc không thể tạo instance, cấu hình constructor-path không hợp lệ.

### 9.2 Provider method

Đối với explicit named module:

- Tên method là chính xác **provider**, lowercase.
- **public static**, **không tham số**.
- Return type được khai báo phải **assignable với service type**.
- Method có thể trả object của một class implementation khác với chính factory holder.
- Method không được trả **null**; ServiceLoader.Provider.get() coi đó là lỗi cấu hình.
- Method có thể ném exception; ServiceLoader báo **ServiceConfigurationError**, thường lưu cause thích hợp.
- Nếu class file có nhiều public static no-args methods tên provider theo những descriptors khác nhau, ServiceLoader có thể coi đó là ambiguous/error.
- Class provider nằm trong **cùng module** với provides directive.
- Không cần public no-args constructor của factory holder khi provider method hợp lệ.

**Tránh nhầm:** class FactoryProvider với static method tên makeProvider hoặc newInstance không được nhận là provider method tự động. Chỉ đúng tên **provider()** theo hợp đồng.

### 9.3 Top-level hoặc static nested provider

JLS yêu cầu service provider được nêu trong module declaration là public class hoặc interface kiểu top-level hay nested static phù hợp. Non-static inner class không phù hợp làm provider type trong provides directive. Với path constructor, khả năng tạo bằng public no-args còn cần thỏa đặc tính constructor của Java.

Không nên dùng enum class làm provider trong provides directive; JLS quy định đó là compile-time error. Một abstract class hoặc interface có thể là provider holder nếu có provider method hợp lệ, nhưng không thể là direct provider cần constructor.

## 10. ServiceLoader.Provider.type() có thể đánh lừa người đọc

**Provider.type()** không đồng nghĩa “class chứa module descriptor entry” trong mọi trường hợp.

### 10.1 Provider constructor

Với provider class CtorTransform:

~~~text
module-info.java:
  provides Transform with CtorTransform

ServiceLoader.Provider.type():
  lesson.impl.CtorTransform
~~~

### 10.2 Provider method

Với FactoryProvider:

~~~text
module-info.java:
  provides Transform with FactoryProvider

FactoryProvider declares:
  public static FactoryResult provider()

ServiceLoader.Provider.type():
  lesson.impl.FactoryResult
~~~

Đây là **kiểu trả về khai báo**, không nhất thiết runtime class chính xác của object nếu method trả subtype cụ thể hơn. Nếu khai báo:

~~~java
public static Transform provider() {
    return new FactoryResult();
}
~~~

thì **Provider.type()** có thể là **lesson.api.Transform**, dù runtime object là FactoryResult.

Vì vậy:

- Dùng **Provider.type()** để quan sát/lọc theo contract của ServiceLoader, nhưng hiểu nó đại diện cho loại gì.
- Không giả định **type()** trả FactoryProvider.class.
- Không dùng **type().getConstructor().newInstance()** để tạo provider một cách tổng quát.
- Dùng **Provider.get()** nếu muốn đúng cơ chế khởi tạo do ServiceLoader hỗ trợ.

### 10.3 Annotation filtering cần tính đến return type

Một hệ thống có annotation trên provider factory holder sẽ không nhất thiết nhìn thấy annotation ấy qua Provider.type() nếu factory method trả về type khác. Nếu framework dùng **stream().filter(p -> p.type().isAnnotationPresent(...))**, hãy đặt metadata/annotations ở **declared return type** hoặc chọn một chiến lược lookup có chủ đích.

Điều này quan trọng trong plugin systems có nhiều implementations cần filter theo khả năng của provider.

## 11. Lazy discovery, instantiation và cache

### 11.1 load() không có nghĩa tạo mọi instance ngay

~~~java
ServiceLoader<Transform> loader =
        ServiceLoader.load(Transform.class);
~~~

Chỉ tạo ServiceLoader. Provider discovery/instantiation chủ yếu diễn ra **lazily** khi iterator hoặc stream thực sự được xử lý.

Với iterator:

~~~java
for (Transform transform : loader) {
    System.out.println(transform.apply("x"));
}
~~~

Provider instances được tạo khi duyệt, và ServiceLoader duy trì cache các providers đã được load.

Với stream:

~~~java
loader.stream()
        .filter(p -> p.type().getName().contains("Factory"))
        .map(ServiceLoader.Provider::get)
        .forEach(p -> System.out.println(p.apply("x")));
~~~

Lọc bằng **type()** có thể xảy ra trước **get()**. Không nên cho rằng chỉ tạo Stream là factory method/constructor đã chạy; tuy nhiên khi xử lý stream, class loading và provider discovery có thể đã có tác động runtime trước get().

### 11.2 Phân biệt hai cache

- **Iterator** cache các instantiated provider objects theo quy tắc ServiceLoader.
- **Stream** cache provider descriptors/Provider objects đã tìm; **Provider.get()** mới tạo hoặc lấy instance theo contract của method. Không tự giả định mỗi lần get() luôn trả cùng singleton.
- **reload()** xóa provider caches của chính ServiceLoader instance. Iterator/Stream đang dùng trước reload phải bỏ đi; chúng có thể ném **ConcurrentModificationException** nếu dùng sau khi reload.
- Một ServiceLoader object không được bảo đảm thread-safe để nhiều threads cùng dùng đồng thời.

Provider constructor và provider() nên được thiết kế với semantics tạo instance rõ ràng. Singleton, pooling hoặc dependency caching là trách nhiệm implementation, không phải điều ServiceLoader tự bảo đảm chỉ bởi việc dùng provider method.

## 12. Provider lifecycle và công việc thực tế

### 12.1 Constructor-path thích hợp khi

- Implementation trực tiếp đơn giản, có default constructor public.
- Không cần quyết định runtime phức tạp trước khi tạo object.
- Cần chạy trên classpath hoặc automatic modules.
- Muốn dễ đóng gói qua META-INF/services và tương thích rộng.

### 12.2 Provider method thích hợp khi

- Explicit named module muốn **ẩn implementation cụ thể** đằng sau factory holder.
- Object cần được tạo qua dependency graph, cache, singleton hoặc cấu hình nội bộ module.
- Provider class không muốn/không thể implements service trực tiếp.
- Muốn dùng internal implementation không có public constructor nhưng vẫn trả đối tượng service cho consumer.
- Muốn trì hoãn quyết định chọn subtype cho đến lúc ServiceLoader gọi provider().

**Giới hạn:** phương thức provider() không tự nhận context/arguments từ ServiceLoader; nó là **no-args**. Nếu cần nhận cấu hình từ caller, thiết kế service interface trả factory object hoặc cung cấp method nhận parameters sau khi ServiceLoader giao instance, thay vì tưởng rằng ServiceLoader tự đưa tham số vào provider().

### 12.3 Package encapsulation

Với explicit modules, nên **không export implementation package** nếu consumer chỉ cần public service API. Điều này cho phép implementation thay đổi mà không làm public module surface phình ra.

Ví dụ module provider:

~~~java
module lesson.providers {
    requires lesson.api;
    provides lesson.api.Transform with lesson.impl.FactoryProvider;
}
~~~

Không có exports lesson.impl. Consumer dùng Transform mà không phải tham chiếu trực tiếp đến FactoryProvider.

## 13. ServiceLoader.load: chọn context loader cho đúng

Các overload đáng chú ý:

~~~java
ServiceLoader.load(Transform.class);
ServiceLoader.load(Transform.class, explicitLoader);
ServiceLoader.load(layer, Transform.class);
~~~

Đây là snippet, cần biến explicitLoader/layer khi chạy.

- **load(service)**: chọn **Thread Context ClassLoader** của thread hiện tại theo hợp đồng API.
- **load(service, classLoader)**: xác định loader làm điểm xuất phát cho tìm providers và provider configuration resources.
- **load(ModuleLayer, service)**: tìm trong layer được chỉ định và parent layers theo quy tắc module layer, hữu ích với plugin runtime tạo layer động.

Không đánh đồng **TCCL** với **defining loader** của service interface. Nếu service type ở host bị plugin loader tự define bản trùng, provider có thể không assignable với host service type. Kiểm tra **serviceClass.getClassLoader()**, **provider.getClass().getClassLoader()** và module graph trong quá trình chẩn đoán.

**TCCL phải được khôi phục** nếu hệ thống thay nó tạm trong shared thread pool: worker sống lâu có thể giữ ClassLoader plugin cũ, làm memory retention sau unload.

## 14. Service provider trong ModuleLayer động

Khi plugin runtime tạo **ModuleLayer mới**, code host có thể dùng:

~~~java
ServiceLoader<Transform> services =
        ServiceLoader.load(pluginLayer, Transform.class);
~~~

Đây là snippet, cần biến pluginLayer và service Transform phù hợp, module graph đã có providers hợp lệ.

**ServiceLoader.load(layer, service)** xem providers trong layer và ancestors. Với one-loader/many-loaders modules, ServiceLoader còn có semantics tìm provider dựa trên module layer, không chỉ đọc một file META-INF/services ở current working directory.

Điều cần đảm bảo:

1. Module descriptor của provider có **provides S with Provider**.
2. Module chứa service interface có exports phù hợp.
3. Consumer/module context có quyền **uses** theo API.
4. Plugin layer đã được define và có các modules providers.
5. Runtime service Class mà caller truyền vào là **cùng service identity** provider được thiết kế để implement.
6. Vòng đời provider/loader được quản lý khi plugin dừng; cache ServiceLoader, futures và threads có thể giữ loaders cũ.

Provider method không thay đổi các quy tắc module access/identity. Nó chỉ thay cách tạo object từ provider class sau khi provider được tìm.

## 15. Lỗi thường gặp và thông điệp cần hiểu

### 15.1 ServiceConfigurationError: not a subtype

**Nguyên nhân điển hình:**

- Trên classpath/automatic module, provider class trong META-INF/services không implements service.
- Runtime có hai bản class service cùng binary name nhưng khác defining loaders.
- Classpath registration trỏ đến factory holder chỉ có static provider() thay vì implementation trực tiếp.

**Hành động:** kiểm tra deployment mode, service interface identity, file META-INF/services và public provider constructor. Không dùng provider method trên classpath để né yêu cầu subtype.

### 15.2 ServiceConfigurationError: Unable to get public no-arg constructor

Provider class dùng constructor-path nhưng không có **public** constructor không tham số. Có thể bạn khai báo constructor nhận dependencies:

~~~java
public DirectProvider(Config config) {
}
~~~

ServiceLoader không tự inject Config. Trong explicit named module, cân nhắc provider method; còn classpath/automatic module phải cung cấp constructor public no-args hoặc chuyển mô hình provider thành service factory với constructor no-args.

### 15.3 Provider method trả null

Trong explicit named module:

~~~java
public static Transform provider() {
    return null;
}
~~~

Đây là sai contract khi ServiceLoader gọi method. ServiceLoader.Provider.get() báo **ServiceConfigurationError**. Luôn trả một object assignable với service và đã ở trạng thái hợp lệ.

### 15.4 Provider method ném exception

~~~java
public static Transform provider() {
    throw new IllegalStateException("Configuration missing");
}
~~~

ServiceLoader báo ServiceConfigurationError, thường giữ nguyên nhân ở cause. Điều tra lỗi factory bên trong chứ không chỉ catch rồi âm thầm bỏ qua trong mọi trường hợp.

### 15.5 Thiếu uses ở explicit consumer module

Module có consumer code gọi ServiceLoader nhưng không khai báo **uses service.type** có thể gặp ServiceConfigurationError. Đừng nghĩ chỉ **requires service-api-module** là đủ cho quy tắc service use trong explicit named module.

### 15.6 Provider module nằm trên module path nhưng không resolved

Có module provider JAR không có nghĩa ServiceLoader luôn tìm thấy. Module graph khi khởi động có thể chưa chứa provider module. Kiểm tra **--add-modules**, root modules, service binding hoặc định nghĩa ModuleLayer phù hợp.

### 15.7 Không thấy provider vì quên META-INF/services

Nếu triển khai classpath mà thiếu file **META-INF/services/fully.qualified.Service**, ServiceLoader có thể trả zero providers dù implementation class compile bình thường. Kiểm tra output/JAR entries, không chỉ source tree.

### 15.8 Nhiều provider implementations không được tìm theo thứ tự mong đợi

Các modules khác nhau có thứ tự discovery không được bảo đảm như thứ tự bạn nhìn trên filesystem. Các provider entries trong **một provides directive** có thứ tự theo module descriptor, nhưng các modules trong một layer có thể không có thứ tự ổn định.

Nếu cần chọn provider dựa trên priority, định nghĩa metadata/config/ordering rõ ràng và sort, như ví dụ named module.

### 15.9 Nhầm ServiceLoader.Provider.type với provider class

Với provider method, type() là **declared return type** của provider(), nên khác factory holder. Không dùng điều này để truy cập constructor của factory holder hoặc suy ra annotation đặt ở factory holder.

### 15.10 Concurrent use hoặc reload khi đang iterate

ServiceLoader instances không thread-safe. Việc gọi reload trong khi đang duyệt iterator/stream cũ có thể ném ConcurrentModificationException. Thiết kế cache và synchronization tại cấp application/framework phù hợp thay vì dùng ServiceLoader object chia sẻ không khóa giữa nhiều threads.

## 16. Bảng quyết định khi thiết kế provider

| Câu hỏi | Cách tiếp cận |
| --- | --- |
| Có explicit module-info.java với provides? | Có thể dùng provider constructor **hoặc** provider method. |
| Provider class trực tiếp implements service và có public no-args constructor? | Constructor-path đơn giản, portable. |
| Class factory không implements service nhưng trả service object? | Dùng public static provider() trong **explicit named module**. |
| Provider nằm trong JAR trên classpath? | META-INF/services + subtype public + public no-args constructor. |
| Provider là automatic module trên module path? | Constructor-path; provider method không hỗ trợ. |
| Muốn ẩn implementation khỏi consumer modules? | Đóng kín implementation package, chỉ export service API. |
| Muốn inspect type trước khi tạo instance? | ServiceLoader.stream() và Provider.type(), nhớ factory return type. |
| Muốn tạo instance đúng cách không quan tâm cơ chế? | Dùng Provider.get() hoặc iterator(). |
| Muốn tìm providers trong ModuleLayer động? | ServiceLoader.load(layer, service). |
| Muốn hot reload providers? | ServiceLoader.reload() và tạo/lifecycle loaders/layers phù hợp; reload không tự thay class đã định nghĩa. |
| Muốn singleton/provider cache? | Thiết kế rõ trong implementation; đừng dựa vào việc gọi get() luôn trả cùng object. |

## 17. An toàn, độ tin cậy và tương thích

### 17.1 Không xem SPI discovery là cơ chế trust

ServiceLoader có thể nạp class và chạy constructor/provider method từ module/JAR trong runtime. Đó là **mã thực thi**, có thể gây side effects, network I/O hoặc truy cập dữ liệu. Chỉ nạp providers từ nguồn phù hợp với trust policy của ứng dụng; ClassLoader/module boundary không tự là security sandbox đầy đủ.

### 17.2 Tách errors của cấu hình với lỗi application

Nếu provider không tìm được, ServiceLoader có thể cho zero items. Nếu provider được khai báo nhưng **không hợp lệ**, ServiceLoader thường báo **ServiceConfigurationError** trong iteration, stream processing hoặc get(). Không nên catch tất cả Throwable để im lặng bỏ qua; lỗi cấu hình của deployment là tín hiệu cần sửa.

### 17.3 Thiết kế provider() không làm công việc quá bất ngờ

Provider method có thể phức tạp, nhưng nên document rõ chi phí khởi tạo, tạo thread, mở kết nối và cleanup. Một factory method thực hiện hoạt động tốn kém trong lần lookup đầu có thể tăng latency; side effects hoặc exception sẽ xuất hiện lúc consumer dùng ServiceLoader.

### 17.4 Class loader leaks khi redeploy

Nếu application giữ ServiceLoader, Provider descriptors, provider instances hoặc TCCL của plugin sau khi dừng, defining loader cũ có thể vẫn reachable. **reload()** chỉ xóa cache của ServiceLoader instance, không tự đóng plugin executor hoặc thu hồi module layer ngay. Plugin manager phải điều phối lifecycle riêng.

## 18. Bài tập tự kiểm tra

**Câu 1.** Provider class của explicit named module không implements service, có private constructor nhưng có public static no-args provider() trả service. Hợp lệ không?

**Đáp án:** Có, nếu provider method và module descriptor hợp lệ. ServiceLoader dùng provider() để lấy instance.

**Câu 2.** Classpath META-INF/services liệt kê class ở câu 1. ServiceLoader có tự gọi static provider() không?

**Đáp án:** Không. Classpath provider phải assignable với service và có public no-args constructor.

**Câu 3.** Một automatic module trên module path có hỗ trợ provider method không?

**Đáp án:** Không. Theo Javadoc, automatic module phải dùng provider constructor.

**Câu 4.** Trong explicit named module, provider class có public no-args constructor và public static provider() hợp lệ. ServiceLoader dùng con đường nào?

**Đáp án:** Provider method theo quy tắc khi provider method được khai báo.

**Câu 5.** ServiceLoader.Provider.type() cho FactoryProvider.provider() trả FactoryResult: type() là FactoryProvider.class hay FactoryResult.class?

**Đáp án:** FactoryResult.class, vì là declared return type của provider method.

**Câu 6.** Nếu provider() khai báo return type Transform nhưng thực tế trả FactoryResult, type() có tự đổi thành FactoryResult.class không?

**Đáp án:** Không. type() phản ánh **declared return type**, nên là Transform.class.

**Câu 7.** Thiếu module-info.java nhưng JAR có Automatic-Module-Name có được dùng factory-only provider() không?

**Đáp án:** Không. Automatic-Module-Name không biến automatic module thành explicit module có provider-method support.

**Câu 8.** Tại sao consumer explicit module nên có uses?

**Đáp án:** Để khai báo service dependency với module system và đáp ứng yêu cầu ServiceLoader khi gọi từ explicit module.

**Câu 9.** Provider method trả null thì sao?

**Đáp án:** ServiceLoader báo ServiceConfigurationError khi cố tạo service object.

**Câu 10.** ServiceLoader.load(Transform.class) có chắc tạo tất cả instances ngay không?

**Đáp án:** Không. Discovery/instantiation chủ yếu lazy thông qua iterator/stream/get().

**Câu 11.** Nếu muốn cùng một service type được chia sẻ giữa host và plugin, điều gì quan trọng ở ClassLoader?

**Đáp án:** Host và provider phải dùng cùng runtime Class identity của service, không chỉ cùng tên chuỗi.

**Câu 12.** Provider method có được nhận tham số Config do ServiceLoader tự inject không?

**Đáp án:** Không. Provider method theo hợp đồng là public static no-args. Cấu hình cần được xử lý qua thiết kế khác.

## 19. Tổng kết

~~~text
                     ServiceLoader<S>
                            |
              +-------------+-------------+
              |                           |
       Explicit named module     Classpath / automatic module
              |                           |
       module-info.java          META-INF/services / metadata
       provides S with P                  |
              |                           |
      Có public static provider()?        |
           |         |                    |
          Có        Không                 |
           |         |                    |
           v         v                    v
      gọi P.provider()      tạo P qua public no-args constructor
           |                       |
           v                       v
     S-compatible object     P phải implements/extends S
              |                           |
              +-------------+-------------+
                            |
                            v
                     consumer nhận S
~~~

**Ghi nhớ năm điểm:**

1. **Provider constructor** cần public no-args và provider class assignable với service.
2. **Provider method** chỉ được hỗ trợ cho **explicit named module** theo đường provides directive; factory holder không nhất thiết implements service.
3. **Classpath và automatic module** dùng constructor-path, không chỉ dựa vào static provider().
4. **ServiceLoader.Provider.type()** của method-path là **declared return type** của provider(), còn **get()** mới lấy instance.
5. Discovery/instantiation **lazy**, có cache, lỗi báo **ServiceConfigurationError** và vẫn phải tôn trọng class-loader identity, module boundaries và lifecycle.

### Tài liệu chuẩn

- Java SE 17 **ServiceLoader API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- Java SE 17 **ServiceLoader.Provider API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.Provider.html
- Java SE 17 **JLS §7.7.4 Service Provision**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7.4
- Java SE 17 **ModuleLayer API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html
- Dev.java **Decoupling Modules with Services**: https://dev.java/learn/organizing/modules/services/


</details>

- [Quay lại đầu trang](#back-to-top)
