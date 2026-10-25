<a id="back-to-top"></a>

# Loader tùy chỉnh

## Menu
- [Khi nào cần mở rộng ClassLoader](#custom-class-loader-001)
- [Chọn constructor, parent và tên loader](#custom-class-loader-002)
- [Ưu tiên ghi đè findClass khi giữ cơ chế ủy quyền mặc định](#custom-class-loader-003)
- [Đọc và kiểm tra bytecode từ thư mục, bộ nhớ hoặc nguồn riêng](#custom-class-loader-004)
- [Ánh xạ tên lớp tới nguồn dữ liệu có giới hạn rõ ràng](#custom-class-loader-005)
- [Truyền tên, offset và length chính xác cho defineClass](#custom-class-loader-006)
- [Định nghĩa package và nguồn mã khi cần](#custom-class-loader-007)
- [Phân loại lỗi không tìm thấy với bytecode không hợp lệ](#custom-class-loader-008)
- [Không định nghĩa lại cùng một tên trong cùng loader](#custom-class-loader-009)
- [Bổ sung khả năng tìm tài nguyên tương ứng với lớp](#custom-class-loader-010)
- [Giữ bất biến định danh kiểu và đóng tài nguyên do loader sở hữu](#custom-class-loader-011)
- [findLibrary và trách nhiệm tra cứu thư viện native](#custom-class-loader-012)

## <a id="custom-class-loader-001">Khi nào cần mở rộng ClassLoader</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào cần mở rộng ClassLoader trong Java?

**Mở rộng `java.lang.ClassLoader`** nghĩa là viết một lớp con để kiểm soát **cách JVM tìm dữ liệu class và định nghĩa runtime class**. Đây là công cụ chuyên biệt, không phải giải pháp mặc định cho mọi lỗi `ClassNotFoundException` hoặc mọi nhu cầu nạp JAR động.

**Nguyên tắc chọn giải pháp:** Hãy ưu tiên classpath/module path, `URLClassLoader`, `ServiceLoader` hoặc module layers nếu chúng đã giải quyết được vấn đề. Chỉ viết custom `ClassLoader` khi bạn thực sự cần **một nguồn class bytecode mới, namespace/isolation riêng hoặc chính sách lựa chọn/biến đổi bytecode có chủ đích** mà các công cụ sẵn có không đáp ứng.

Trong Java, danh tính runtime class được xác định bởi cặp:

```text
(binary name, defining class loader)
```

Vì vậy custom loader không chỉ là một “đường dẫn khác để tìm file”. Nó có thể làm thay đổi **định danh kiểu, khả năng ép kiểu, package-private access, static state, việc giải quyết dependency và vòng đời unload/reload**.

## 1. Trước tiên: ClassLoader mặc định làm gì?

Các JVM hiện đại có bootstrap/platform/application loaders để tìm lớp nền tảng và lớp ứng dụng. `ClassLoader` chuẩn dùng mô hình **parent delegation**:

```text
child.loadClass("plugin.Example")
         |
         v
findLoadedClass(name)
         |
         +-- đã biết class? -> trả về
         |
         v
parent.loadClass(name) (hoặc bootstrap lookup)
         |
         +-- tìm thấy? -> trả về class do parent/ancestor cung cấp
         |
         v
child.findClass(name)
         |
         +-- tìm bytecode riêng, rồi defineClass(...)
         |
         v
Class<?> kết quả
```

`loadClass(String, boolean)` mặc định thực hiện kiểm tra đã nạp, ủy quyền parent, rồi mới gọi `findClass`. Nếu tham số `resolve` là `true`, nó còn gọi `resolveClass` theo hợp đồng API. **`loadClass` không đồng nghĩa với chạy static initializer**: class có thể được load/link nhưng chỉ initialized khi thao tác của chương trình/JVM yêu cầu.

Đối với custom loader **vẫn muốn parent-first**, cách mở rộng được JavaDoc khuyến nghị là override **`findClass(String)`**, không phải ngay lập tức override `loadClass`. Nhờ đó ta giữ lại thuật toán delegation, kiểm tra class đã nạp, locking và resolution của lớp cơ sở.

Các phương thức có vai trò khác nhau:

| API | Mục đích | Thường cần override? |
|---|---|---|
| `loadClass(String)` | Điểm vào nạp lớp theo binary name | **Không** nếu chấp nhận parent-first |
| `loadClass(String, boolean)` | Điều phối cache, parent, findClass, resolve | Chỉ khi cần chính sách delegation khác và đã hiểu hệ quả |
| `findClass(String)` | Tìm/định nghĩa class **do loader này** cung cấp | **Có** với custom nguồn bytecode theo parent-first |
| `defineClass(String, byte[], int, int)` | Chuyển bytes class file hợp lệ thành runtime class | **Gọi**, không override (phương thức protected final) |
| `findLoadedClass(String)` | Truy vấn class đã gắn với initiating loader | Hữu ích nếu tự override loadClass |
| `resolveClass(Class<?>)` | Hoàn tất linking thích hợp | Gọi theo quy tắc `loadClass`; không phải initialization |
| `findResource(String)` / `findResources(String)` | Tìm resource do loader cung cấp | Khi loader cũng phục vụ resource |
| `getClassLoadingLock(String)` | Lock phối hợp nạp theo tên lớp | Cần biết khi tự thay `loadClass`/dùng parallel loading |

**Đừng gọi `defineClass` mỗi lần được yêu cầu một tên đã nạp.** Một loader không thể định nghĩa lại cùng binary name thành runtime class thứ hai của chính loader đó; cố làm vậy có thể dẫn tới `LinkageError` liên quan duplicate definition. Muốn thế hệ class mới thì phải thiết kế defining loader mới và quản lý lifetime của nó.

## 2. Khi nào thực sự nên tạo custom ClassLoader?

Một số nhu cầu chính đáng:

### A. Dữ liệu class không nằm trên URL/JAR/thư mục mà loader có sẵn hiểu được

Ví dụ một môi trường nhận **bytecode từ bộ nhớ**, một kho dữ liệu nội bộ, nội dung tạo bởi compiler trong chương trình, hoặc một định dạng lưu trữ tùy biến. Khi đã có `byte[]` của class file, custom loader có thể gọi `defineClass` để đưa class vào JVM.

`URLClassLoader` làm tốt với URLs tới directory/JAR. Nếu nguồn của bạn là `Map<String,byte[]>` hoặc bytecode được cấp qua API nội bộ, override `findClass` có thể đơn giản hơn ép dữ liệu vào file JAR trên disk.

**Bảo mật:** Việc bytecode nằm trong bộ nhớ hoặc đã mã hóa lúc lưu trữ **không biến nó thành code an toàn**. Xác minh nguồn tin cậy, tính toàn vẹn, kích thước, phụ thuộc và quyền thực thi trước khi định nghĩa. Không nên viết loader tải và chạy bytecode bất kỳ từ mạng chỉ vì có thể.

### B. Tạo namespace cho plugin hoặc phần ứng dụng có vòng đời riêng

Hai plugin có thể dùng hai phiên bản thư viện trùng binary name. Với các **defining loaders khác nhau**, chúng có thể tồn tại như các runtime classes riêng:

```text
Parent/AppLoader:
    shared.api.Plugin          (một contract dùng chung)
       ^                 ^
       |                 |
PluginLoaderA       PluginLoaderB:
    impl.A              impl.B
    lib.VersionX        lib.VersionY
```

Loader riêng giúp cô lập implementation/dependencies, nhưng **shared API cần được ủy quyền cho loader chung**; nếu interface `shared.api.Plugin` cũng bị định nghĩa riêng ở từng child, cast và service exchange có thể thất bại.

Có thể triển khai plugin loading bằng `URLClassLoader` với parent phù hợp; không cần custom subclass trừ khi bạn phải điều chỉnh delegation, resource lookup hoặc nguồn code.

### C. Cần chính sách delegation khác parent-first cho phạm vi xác định

Một số ứng dụng plugin/server muốn **child-first** với dependency riêng để không bị phiên bản cùng tên từ parent chi phối. Khi đó custom `loadClass` có thể được cân nhắc, nhưng phải có luật **namespace cụ thể**, chẳng hạn ưu tiên parent cho `java.*`, `javax.*` phù hợp runtime, `shared.api.*`, và chỉ tự nạp trước cho `plugin.internal.*`.

Đây là phần **khó và nhiều rủi ro hơn** so với override `findClass`. Thiếu đồng bộ, bỏ `findLoadedClass`, quản lý sai `resolveClass` hoặc tự định nghĩa lại shared API có thể gây `LinkageError`, `ClassCastException` hay `IllegalAccessError`. Nên thử loader framework/container đã tồn tại trước.

### D. Sinh/biến đổi bytecode theo quy trình ứng dụng

Custom loader có thể là một bước trong hệ thống compiler, bytecode generator hoặc transformation pipeline: lấy class bytes, kiểm tra/biến đổi, gọi `defineClass`. Nhưng nếu mục tiêu là **instrument những class JVM đã hoặc đang được nạp**, hãy cân nhắc **`java.lang.instrument.Instrumentation`**, Java agent, bytecode library, `MethodHandles.Lookup#defineClass` hoặc hidden classes tùy trường hợp; không phải mọi thay đổi cần thay lớp loader gốc.

### E. Phân tách vòng đời các thế hệ plugin

JVM thường không hỗ trợ “thay class bytes rồi khởi tạo lại cùng runtime class”. Muốn đưa phiên bản mới với cùng binary name vào sử dụng, một phương án là **tạo defining loader mới**, xây lại instances/services trên namespace mới, sau đó loại bỏ references đến loader và objects cũ.

Đây là một kiến trúc hot-reload, **không phải bảo đảm rằng cứ tạo class loader mới là JVM đã unload loader cũ**. Thread, static references, caches, context class loaders và registry có thể giữ class loader cũ sống lâu.

## 3. Những tình huống KHÔNG nên vội mở rộng ClassLoader

| Vấn đề thực tế | Cách ưu tiên | Khi nào mới nghĩ tới custom loader? |
|---|---|---|
| Ứng dụng không thấy thư viện JAR | Sửa `--class-path`, manifest `Class-Path` hoặc module path | Khi phải tìm lớp từ nguồn phi chuẩn |
| Muốn thêm một URL JAR/thư mục trong phạm vi riêng | `URLClassLoader` với parent đã chọn | Khi cần chính sách delegation hoặc nguồn dữ liệu khác |
| Muốn tìm implementation theo interface | `ServiceLoader` + provider declarations | Khi cần tự tổ chức nhiều namespace/provider loaders |
| Muốn gọi class bằng tên chuỗi | `Class.forName(name, initialize, loader)` hoặc `loader.loadClass(name)` | Khi không loader sẵn nào có thể cung cấp class |
| Muốn giảm thời gian khởi tạo của class | Hoãn sử dụng, dùng `Class.forName(..., false, ...)` khi hợp lý | Thường **không** cần custom loader |
| Muốn nạp thêm module | JPMS module path / `ModuleLayer` | Khi kiến trúc module cũng đòi policy loader riêng |
| Muốn đổi method/bytecode class đang chạy | Java agent/Instrumentation, công cụ bytecode phù hợp | Loader chỉ là một phần nếu pipeline định nghĩa class mới |
| Muốn nạp một class mới trong cùng package/nest context | `MethodHandles.Lookup#defineClass` hoặc hidden-class APIs khi đáp ứng điều kiện | Khi cần namespace/lifetime lớn hơn một lookup |
| Muốn chạy plugin từ bên thứ ba **không đáng tin** | Thiết kế thực thi cách ly ở process/container, xác thực code, kiểm soát quyền | **ClassLoader một mình không phải sandbox bảo mật** |
| Muốn có “một singleton static cho mọi plugin” | Đặt contract/state tại loader chung có chủ đích | Custom loader riêng thường tạo **nhiều bản static state**, trái mục tiêu |

Một số điểm thường bị hiểu sai:

- `ClassLoader` **không** phải công cụ quản lý dependency tự động như Maven/Gradle.
- `ClassLoader` **không** thay thế quyền truy cập `module-info.java`, exports/opens, package access hay JVM verification.
- `ClassLoader` **không** buộc mọi resource URL trở thành `File`; resource có thể đến từ JAR, JRT, mạng hoặc bộ nhớ.
- Cố tạo loader mới để “sửa” `NoClassDefFoundError` mà không chẩn đoán nguyên nhân là phản tác dụng: lỗi có thể do thiếu dependency, failed initialization, bytecode sai, version conflict hoặc loader constraints.

## 4. Ví dụ Java 17+: định nghĩa plugin class từ byte[] bằng custom loader

Demo này diễn tả một nhu cầu thực sự: ứng dụng **chỉ có service API trên application classpath**, còn implementation nằm trong bộ nhớ mà custom loader được cấp. Để thực hành dễ lặp lại, chúng ta đọc bytes từ một file `.class` riêng, nhưng **không thêm thư mục plugin vào classpath lúc chạy**. Trong hệ thống thật, `byte[]` có thể đến từ compiler trong bộ nhớ hoặc một nguồn nội bộ tin cậy.

Thư mục nguồn:

```text
loader-demo/
├── src/
│   ├── api/
│   │   └── Greeter.java
│   ├── plugin/
│   │   └── HelloGreeter.java
│   └── app/
│       └── LoaderDemo.java
└── out/
    ├── app/
    └── plugin/
```

### 4.1. Contract chung

Tệp **`src/api/Greeter.java`**:

```java
package api;

public interface Greeter {
    String greet();
}
```

### 4.2. Plugin implementation

Tệp **`src/plugin/HelloGreeter.java`**:

```java
package plugin;

import api.Greeter;

public final class HelloGreeter implements Greeter {
    static {
        System.out.println("Plugin initialized");
    }

    @Override
    public String greet() {
        return "hello-from-bytes";
    }
}
```

### 4.3. Application và custom loader

Tệp **`src/app/LoaderDemo.java`**:

```java
package app;

import api.Greeter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class LoaderDemo {
    static final class MemoryLoader extends ClassLoader {
        private final Map<String, byte[]> definitions;

        MemoryLoader(ClassLoader parent, Map<String, byte[]> definitions) {
            super(parent);
            this.definitions = Map.copyOf(definitions);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            byte[] bytes = definitions.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }

            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader appLoader = LoaderDemo.class.getClassLoader();

        try {
            Class.forName("plugin.HelloGreeter", false, appLoader);
            System.out.println("Parent sees plugin: true");
        } catch (ClassNotFoundException expected) {
            System.out.println("Parent sees plugin: false");
        }

        byte[] classBytes = Files.readAllBytes(Path.of(args[0]));
        MemoryLoader custom = new MemoryLoader(appLoader,
                Map.of("plugin.HelloGreeter", classBytes));

        Class<?> type = custom.loadClass("plugin.HelloGreeter");
        System.out.println("Defined by custom: "
                + (type.getClassLoader() == custom));
        System.out.println("Class loaded, initialization pending");

        Greeter greeter =
                (Greeter) type.getDeclaredConstructor().newInstance();

        System.out.println("Message: " + greeter.greet());
        System.out.println("Shared Greeter: "
                + (custom.loadClass("api.Greeter") == Greeter.class));
        System.out.println("Same Class on repeat: "
                + (custom.loadClass("plugin.HelloGreeter") == type));
    }
}
```

**Vì sao chỉ override `findClass`?** Vì `MemoryLoader` kế thừa `loadClass` mặc định, nên JVM kiểm tra class đã nạp và ủy quyền parent trước khi gọi `findClass`. Khi plugin implementation không được parent tìm thấy, `findClass` nhận bytes, dùng `defineClass` để định nghĩa runtime class mới. Khi tìm `api.Greeter`, parent định nghĩa contract, nên app và plugin dùng **cùng interface**.

### 4.4. Biên dịch và chạy

Trên Linux/macOS/Git Bash, từ thư mục `loader-demo`:

```bash
mkdir -p out/app out/plugin

javac --release 17 -d out/app \
  src/api/Greeter.java src/app/LoaderDemo.java

javac --release 17 -cp out/app -d out/plugin \
  src/plugin/HelloGreeter.java

java -cp out/app app.LoaderDemo out/plugin/plugin/HelloGreeter.class
```

Trên Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path out/app, out/plugin | Out-Null
javac --release 17 -d out/app src/api/Greeter.java src/app/LoaderDemo.java
javac --release 17 -cp out/app -d out/plugin src/plugin/HelloGreeter.java
java -cp out/app app.LoaderDemo out/plugin/plugin/HelloGreeter.class
```

Đầu ra đã kiểm thử bằng `javac --release 17` và JDK 21 tương thích Java 17:

```text
Parent sees plugin: false
Defined by custom: true
Class loaded, initialization pending
Plugin initialized
Message: hello-from-bytes
Shared Greeter: true
Same Class on repeat: true
```

**Giải thích chính xác các bước:**

1. Runtime classpath chỉ có `out/app`. Parent/application loader tìm được `api.Greeter` và `app.LoaderDemo`, **không tìm được** `plugin.HelloGreeter`. Do đó dòng đầu là `false`.
2. Bytecode plugin đã được biên dịch vào `out/plugin`, nhưng thư mục đó **không nằm trên runtime classpath**. Chương trình đọc đúng file `HelloGreeter.class` thành `byte[]`.
3. `MemoryLoader.findClass("plugin.HelloGreeter")` được gọi sau khi parent không tìm được; `defineClass` tạo runtime class với defining loader là đối tượng `MemoryLoader`.
4. `custom.loadClass(...)` chỉ tải class, không bắt buộc chạy static initializer. Vì vậy dòng `Class loaded, initialization pending` xuất hiện trước `Plugin initialized`.
5. Lời gọi constructor bằng reflection kích hoạt initialization của plugin, rồi constructor và `greet()` chạy.
6. `custom.loadClass("api.Greeter")` ủy quyền parent, nên trả **đúng đối tượng `Class`** mà mã ứng dụng đang dùng. Cast sang `Greeter` thành công.
7. `custom.loadClass("plugin.HelloGreeter")` lần tiếp theo nhận cùng runtime `Class<?>` từ loader đó, không định nghĩa lại class.

**Giới hạn của ví dụ:** Loader này giữ `Map` chứa mutable `byte[]` chỉ để minh họa. `Map.copyOf` sao chép cấu trúc map, **không sao chép các mảng byte bên trong**; nếu dữ liệu đến từ caller không tin cậy/có thể thay đổi đồng thời, phải clone/kiểm soát ownership và xác thực bytecode. Ví dụ cũng không triển khai resource lookup, package metadata, CodeSource hoặc lifecycle đóng nguồn. Một hệ thống production cần các bước này theo yêu cầu thực tế.

## 5. Các quyết định quan trọng khi tự thiết kế loader

### 5.1. Nguồn bytecode và đặt tên

`ClassLoader.findClass(String name)` nhận **binary name** như `com.example.Widget`, không nhận `com/example/Widget.class`. Nếu nguồn là thư mục/JAR, đường tài nguyên có thể được suy ra khi cần:

```java
String entry = name.replace('.', '/') + ".class";
```

Đây là dòng minh họa không độc lập, phù hợp với binary names của class thông thường. Khi xây loader thực tế, phải kiểm tra package/name hợp lệ, tránh traversal, giới hạn đầu vào và chỉ đọc từ nguồn tin cậy. Không nhận một chuỗi tùy ý rồi ghép vào filesystem path mà không kiểm soát đường dẫn và thư mục gốc.

`defineClass(name, bytes, 0, bytes.length)` cần **class-file bytes** hợp lệ. Nếu binary name truyền vào không khớp tên class bên trong bytes, JVM có thể ném `NoClassDefFoundError`. Nếu bytecode sai định dạng hoặc sai quy tắc type verification, có thể gặp `ClassFormatError` hoặc `VerifyError`. Custom loader **không bỏ qua** JVM verification.

### 5.2. Chọn parent cho đúng

Chọn parent phù hợp giúp chia sẻ các class API và JDK platform classes:

```java
ClassLoader parent = SharedService.class.getClassLoader();
ClassLoader custom = new MemoryLoader(parent, definitions);
```

Đây là snippet minh họa; cần `SharedService` và `definitions` được khai báo. Mục tiêu là loader con và application dùng cùng runtime `SharedService` qua delegation.

**Không chọn `null` làm parent chỉ vì muốn “cô lập tuyệt đối”** nếu plugin vẫn cần type/library do application loader định nghĩa. Parent `null` biểu diễn đường bootstrap trong `ClassLoader`, nên có thể không nhìn thấy shared API từ application loader.

Chọn loader parent khác nhau dẫn tới kết quả khác nhau khi cùng tên class đã có mặt ở parent. Parent-first cũng có thể vô tình “thắng” bản class riêng của plugin nếu parent chứa class cùng tên.

### 5.3. Có cần override loadClass để child-first không?

Hầu hết trường hợp không cần. Nhưng nếu có yêu cầu namespace isolation nghiêm túc, một chiến lược minh họa là:

```text
loadClass(name, resolve):
    lock theo name
    nếu đã load -> trả đã load
    nếu name thuộc shared/JDK APIs -> thử parent trước
    nếu name thuộc private plugin namespace:
         thử findClass của child trước
         nếu không tìm được -> fallback parent theo chính sách
    các tên khác -> parent-first
    nếu resolve -> resolveClass(result)
    trả result
```

Đây là **giả mã**, không phải implementation đầy đủ. Cần kiểm soát các điểm khó:

- `getClassLoadingLock(name)` và `findLoadedClass(name)` để không tạo duplicate class trong tình huống đồng thời.
- `ClassNotFoundException` được dùng cho trường hợp không tìm thấy; **không được tùy tiện bắt rồi bỏ qua `VerifyError`, `LinkageError` hoặc `SecurityException` để dùng bản class khác**.
- Không tự định nghĩa `java.*`; `ClassLoader.defineClass` có hạn chế rõ ràng, và nhiều nền tảng còn có policy package/module riêng.
- `shared.api.*` thường phải parent-first để class/interface vượt qua boundary giữa plugin và host.
- Nếu code trong plugin chứa method signatures tham chiếu type do loader khác định nghĩa không tương thích, **loading constraints** của JVM có thể gây `LinkageError`.
- Nếu đảo delegation cho class nhưng không xử lý resource lookup hoặc service discovery tương ứng, class và resource có thể đến từ hai nguồn khác nhau không mong muốn.

Nếu bạn đang có container/plugin framework sẵn, tốt hơn nên hiểu và cấu hình loader policy của framework trước khi viết loader riêng.

### 5.4. Có cần findResource/findResources?

Nếu cùng một nguồn cung cấp `.class` và tài nguyên như configuration, service descriptors, image, schema, loader có thể cần triển khai:

```java
protected URL findResource(String name)
protected Enumeration<URL> findResources(String name)
```

`ClassLoader.getResource` và `getResources` có delegation/search rules riêng. Resource name có dạng `"plugin/config.json"` (dấu `/`), không phải binary name `plugin.config`. Một custom loader trả `Class<?>` đúng nhưng không tìm được `META-INF/services/...` vẫn có thể làm `ServiceLoader` không nhận diện provider.

Nếu resource nằm trong memory/custom scheme, cung cấp URL stream handler phù hợp hoặc API truy xuất trực tiếp bằng stream; không kỳ vọng mọi resource có thể chuyển thành `File`. Tạo custom protocol URL cần cân nhắc chính sách, lifetime và độ phức tạp của connection.

### 5.5. Package, sealing, protection domain và nguồn code

`defineClass` có ràng buộc về class name, package và tập chứng chỉ ký. Khi làm việc với signed JARs, package sealing, `CodeSource` hay cần metadata/protection domain cụ thể, **không nên chỉ đọc bytes rồi gọi overload `defineClass` mặc định và cho rằng mọi metadata được bảo toàn**.

`SecureClassLoader`, `URLClassLoader`, `definePackage` và overload `defineClass(..., ProtectionDomain)` cung cấp công cụ phục vụ các trường hợp liên quan. Với custom loader production, phải tuân thủ quy tắc package/certificates/signers phù hợp khi đưa classes từ nhiều nguồn vào cùng loader.

Hãy đọc JavaDoc của `defineClass`: định nghĩa tên thuộc `java.*` bị hạn chế; tên tham số phải khớp binary name trong class-file bytes khi được cung cấp.

### 5.6. Đồng thời: parallel capable loaders

`ClassLoader` hỗ trợ mô hình **parallel capable**. Các subclass có thể đăng ký bằng `registerAsParallelCapable()` từ static initializer của **chính subclass**, với điều kiện các superclass thích hợp đã được đăng ký và chưa tạo instance; API trả boolean thông báo kết quả.

Điều này cho phép locking theo class name thay vì một lock chung cho toàn loader trong các trường hợp phù hợp. **Không tự bật nếu implementation chưa bảo đảm thread-safety** của map, caches, I/O, transformations và namespace. Nếu tự override `loadClass` mà bỏ lock, việc hai thread cùng yêu cầu class có thể gây duplicate definition hoặc race. Parent-first implementation kế thừa từ `ClassLoader` thường ít rủi ro hơn cho người mới.

### 5.7. Vòng đời: đóng nguồn không có nghĩa unload class ngay

`URLClassLoader` triển khai `Closeable`; đóng nó giải phóng JAR/file handles trong khả năng của loader và ngăn nạp thêm classes/resources qua đường nó quản lý. Nhưng những classes đã load vẫn có thể được dùng, và `close()` **không ép garbage collector unload ngay**.

Custom `MemoryLoader` trong ví dụ không có tài nguyên native/file mở dài hạn, nhưng vẫn có thể giữ mảng bytes/metadata. Một loader production có nguồn network, file handles hoặc caches dài hạn nên có API lifecycle thích hợp, đồng thời quản lý references đến classes, objects, threads và TCCL.

## 6. Nhận diện rủi ro trước khi mở rộng loader

Một loader tùy chỉnh can thiệp vào nơi JVM lấy class bytes và nơi **định nghĩa kiểu**. Vì vậy một tính năng chỉ định làm mới nguồn nạp lớp vẫn có thể thay đổi các ranh giới quan trọng:

| Dấu hiệu | Điều cần kiểm tra |
| --- | --- |
| `ClassCastException` dù class cùng tên | Hai bên có nhận **cùng `Class<?>` của API chia sẻ** hay mỗi loader tự định nghĩa một bản? |
| `ClassNotFoundException` / `NoClassDefFoundError` | Tên/nguồn có vắng mặt, dependency không nhìn thấy, hay class từng khởi tạo lỗi? |
| `ClassFormatError`, `VerifyError`, `UnsupportedClassVersionError` | Bytecode đã đọc có đúng cấu trúc, verification và target JVM? |
| `LinkageError` | Có duplicate definition, binary incompatibility hoặc loading constraint giữa các loader? |
| Resource/provider không được tìm thấy | Loader có hỗ trợ resource lookup và cùng định danh SPI không? |
| Plugin cũ không được thu hồi | Thread, TCCL, registry, cache, handle JAR hoặc object nào đang giữ loader? |

**Đừng xử lý mọi lỗi bằng cách đổi sang child-first hoặc nuốt `LinkageError` để thử bytes khác.** Nên xác định defining loader, binary name, nguồn bytes và nguyên nhân đầu tiên trước. Phần còn lại của chương đi sâu vào chính các giới hạn này, từ lựa chọn parent đến error handling, type identity và vòng đời. Class loader tự nó không phải sandbox cho mã không tin cậy.

## 7. So sánh lựa chọn: URLClassLoader, ServiceLoader, ModuleLayer

### URLClassLoader

Khi có thư mục/JAR ở URL phù hợp và muốn load classes/resources từ đường dẫn đó:

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

Path jar = Path.of("plugins/sample.jar");
URL[] urls = { jar.toUri().toURL() };

try (URLClassLoader loader =
        new URLClassLoader(urls, MyApp.class.getClassLoader())) {
    Class<?> type = loader.loadClass("plugins.Sample");
    // Dùng type trong phạm vi cần thiết.
}
```

Đây là **snippet minh họa**, chưa là file `main` độc lập vì cần `MyApp` và JAR phù hợp. `URLClassLoader` đã cài đặt việc tìm class/resource từ directories/JARs, kiểm soát tài nguyên mở và nhiều hành vi cần thiết; **không cần viết lại `findClass` cho use case này**.

Lưu ý `URLClassLoader` mặc định kế thừa parent delegation. Nếu parent đã có class cùng tên, parent có thể thắng và loader chỉ trả lại class của parent. Muốn dependency isolation có chủ đích cần giải pháp lớp cao hơn, không đơn thuần thêm URL.

### ServiceLoader

Khi ứng dụng chỉ cần implementation của service interface, không cần quản lý class bytes:

```java
ServiceLoader<MyService> services =
        ServiceLoader.load(MyService.class, pluginLoader);
```

Đây là snippet minh họa cần `MyService` và `pluginLoader` có thật. `ServiceLoader` quản lý discovery dựa trên `META-INF/services` hoặc named-module `provides`, còn class loader quản lý visibility/runtime class identity. Có thể phối hợp **`ServiceLoader` với `URLClassLoader` sẵn có**, không buộc custom subclass.

### JPMS ModuleLayer

Với named modules, module path, dependency graph, readability và module access có thể được giải quyết bằng `java.lang.module.Configuration` / `ModuleLayer`. Framework có thể thiết lập layer mới và gắn loader phù hợp. Đây là cơ chế khác với đọc tùy ý `.class` bằng `findClass`.

Nếu bài toán là “plugin có `module-info.java` và cần isolation theo module”, đánh giá module layer trước, vì tự viết loader không tự cung cấp module readability/exports/opens.

### MethodHandles.Lookup / hidden classes

Nếu một framework sinh một lớp **liên quan chặt với lookup class** (như helper class trong runtime package phù hợp), `MethodHandles.Lookup#defineClass` có thể định nghĩa class trong loader/package của lookup class theo những ràng buộc tương ứng. `defineHiddenClass` phục vụ những trường hợp bytecode-generated classes không cần được tìm lại bằng binary name thông thường.

Không nên mặc định đây là thay thế hoàn toàn cho loader khi cần namespace plugin độc lập; chúng giải quyết loại bài toán khác.

### Instrumentation / Java agent

Nếu muốn quan sát/sửa bytecode tại sự kiện class load hoặc retransform/redefine trong những giới hạn của Instrumentation API, Java agent là lựa chọn chuyên dụng. Tạo custom loader không cho phép tùy ý thay tất cả những classes đã được parent/JVM định nghĩa.

## 8. Một quy trình quyết định thực tế

Hỏi lần lượt:

1. **Nguồn class có phải JAR/thư mục thông thường không?** Có → thử classpath/module path hoặc `URLClassLoader`.
2. **Mục tiêu chỉ tìm service implementations không?** Có → thử `ServiceLoader`/JPMS `provides`.
3. **Cần nạp đúng class name với loader đã biết?** Có → dùng `Class.forName(name, initialize, loader)` hoặc `loader.loadClass(name)`.
4. **Bytecode đến từ nguồn không thể biểu diễn tự nhiên bằng URLClassLoader?** Có → cân nhắc subclass override `findClass`.
5. **Cần hai bản cùng binary name cùng tồn tại?** Có → thiết kế defining loaders riêng và shared contracts; cân nhắc container/module framework.
6. **Cần child-first thật không?** Chỉ khi bạn có namespace isolation rules và kiểm thử lỗi type identity/delegation; không phải mặc định.
7. **Cần thay thế class đã định nghĩa trong cùng loader không?** Đánh giá Instrumentation hoặc loader generation mới; một `defineClass` thứ hai cùng tên trong cùng loader không giải quyết được.
8. **Có yêu cầu chạy mã không đáng tin?** Đưa isolation/security về thiết kế process, OS sandbox/container và kiểm soát code provenance; loader một mình không đủ.
9. **Có kế hoạch đóng, dọn, unload?** Xem strong references, thread/TCCL, caches, opened JARs, registration và lifecycle trước khi triển khai.
10. **Có thể viết test chạy trên runtime triển khai thật?** Phải kiểm tra class identity, classpath, resource loading, module access, concurrency và lỗi root cause.

Câu hỏi “Khi nào cần mở rộng `ClassLoader`?” thường có câu trả lời tốt nhất là **“khi chúng ta sở hữu một cơ chế định nghĩa class đặc thù, và đã chứng minh các API có sẵn không đủ”**. Không bắt đầu từ việc subclass chỉ vì muốn gọi `loadClass`.

## 9. Checklist chất lượng cho một ClassLoader tự viết

- [ ] `findClass` trả `Class<?>` nếu tìm/định nghĩa thành công; ném `ClassNotFoundException` đúng khi **không tìm được**.
- [ ] Input là **binary name**; nếu chuyển sang resource path, dùng tên `pkg/Type.class` phù hợp và kiểm soát nguồn.
- [ ] `defineClass` được truyền đúng bytes và binary name; không định nghĩa trùng tên trong cùng loader.
- [ ] Parent có shared API/JDK types cần thiết; không định nghĩa lại API chung trong child một cách vô ý.
- [ ] Nếu dùng custom `loadClass`, có locking, `findLoadedClass`, fallback/resolve và exception propagation đúng.
- [ ] Resource discovery/`ServiceLoader` tìm đúng descriptors nếu provider source dùng cùng loader.
- [ ] Có kiểm soát kích thước, nguồn tin cậy và integrity của bytes; không coi loader là sandbox.
- [ ] Có chính sách module, package-private, sealing, code source/signer, native libraries nếu hệ thống cần.
- [ ] Không giữ vô hạn các references đến plugin loaders/instances khi có yêu cầu unload.
- [ ] Test không chỉ happy path: thiếu class, thiếu dependency, sai bytecode, version mismatch, duplicate class, concurrency và đổi phiên bản.

## 10. Bài tập tự kiểm tra

**1. Khi có một JAR trên filesystem cần load động, phải subclass `ClassLoader` không?** Không nhất thiết; thường thử `URLClassLoader` với parent phù hợp trước.

**2. Vì sao override `findClass` thay vì `loadClass` trong loader parent-first?** Vì lớp cơ sở đã xử lý kiểm tra class đã nạp, parent delegation, synchronization và resolution; `findClass` chỉ bổ sung cách tìm nguồn class riêng.

**3. Tại sao classloader trong ví dụ phải dùng application loader làm parent?** Vì plugin triển khai `api.Greeter` và app cần cast sang **cùng runtime interface**; parent cung cấp contract dùng chung.

**4. `loadClass` có chạy `static { ... }` ngay không?** Không phải theo hợp đồng mặc định. Initialization diễn ra khi chương trình/JVM yêu cầu, chẳng hạn tạo instance.

**5. Hai loaders cùng `defineClass("p.Widget", sameBytes, ...)` có tạo cùng runtime class không?** Không. Defining loader khác làm class identity khác.

**6. `ClassNotFoundException` có nên được dùng để che `VerifyError` không?** Không. Lỗi không tìm thấy khác lỗi bytecode sai; cần báo đúng nguyên nhân.

**7. Muốn provider implementation được chọn theo loader của plugin, chọn API nào?** Thường `ServiceLoader.load(serviceType, pluginLoader)` hoặc một phương án module-layer thích hợp; không cần tự hiện thực SPI discovery.

**8. Custom ClassLoader có bảo đảm plugin đáng tin và không truy cập tài nguyên hệ thống không?** Không. Đó không phải cơ chế sandbox bảo mật độc lập.

**9. `URLClassLoader.close()` có ép unload tất cả classes do nó định nghĩa không?** Không. Nó đóng tài nguyên loader quản lý nhưng class unloading phụ thuộc JVM/garbage collection và không còn references.

**10. Thời điểm nào nên đầu tư custom delegation policy?** Khi có yêu cầu namespace isolation đã xác định, đã chọn shared API boundary, biết danh sách packages cần ưu tiên parent/child, và có test cho type identity, linkage, concurrency.

## 11. Tài liệu chuẩn

- Java SE 17, `ClassLoader`: thuật toán `loadClass`, `findClass`, `defineClass`, `getClassLoadingLock` và `registerAsParallelCapable`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17, `URLClassLoader`: nạp classes/resources từ URL, `close()` và `findClass` sẵn có: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17, `ServiceLoader`: SPI providers trên classpath hoặc trong named modules: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- Java SE 17, `MethodHandles.Lookup#defineClass` và hidden-class APIs: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html
- Java SE 17, `Instrumentation`: https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html
- JVMS `5.3, *Creation of Classes and Interfaces*, về defining/initiating loaders và class identity: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3

**Ghi nhớ:** Chỉ mở rộng `ClassLoader` khi cần thay đổi **cách class bytes được cung cấp hoặc namespace/chính sách load**. Đối với parent-first custom loader, bắt đầu bằng `findClass` và `defineClass`, giữ shared API ở parent, và không quên **class identity, resources, modules, concurrency, security và lifecycle**. Với JAR thông thường và SPI, hãy tận dụng `URLClassLoader`/`ServiceLoader` trước.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-002">Chọn constructor, parent và tên loader</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn constructor, parent và tên loader khi xây dựng ClassLoader tùy chỉnh

## 1. Ba quyết định khi tạo một custom class loader

Một class loader tùy chỉnh (*custom class loader*) có thể đọc class bytes từ JAR, thư mục riêng, tài nguyên plugin, mạng hoặc nguồn do chương trình quản lý. Trước khi viết cách tìm class, hãy xác định **ba điều khác nhau**:

1. **Constructor nào dùng để khởi tạo class loader object?** Trong Java 17, `ClassLoader` có constructor mặc định, constructor nhận parent, và constructor nhận tên cộng parent.
2. **Loader nào là parent trong quá trình delegation?** Điều này ảnh hưởng class và resource mà custom loader nhìn thấy, ranh giới shared API, khả năng cách ly plugin và nguy cơ trùng runtime type.
3. **Tên loader nào sẽ xuất hiện khi chẩn đoán?** Tên giúp con người phân biệt loader nhưng không thay cho object identity và không tự thay đổi parent hoặc bytecode lookup.

Cần phân biệt: **tạo một ClassLoader object không có nghĩa đã tạo một Java class mới**. Nếu subclass không cài đặt logic tìm/định nghĩa class mới, nó có thể chỉ ủy quyền cho parent mà không tự định nghĩa bất cứ class ứng dụng nào.

Một lựa chọn hợp lý thường thấy:

```java
public final class PluginClassLoader extends ClassLoader {
    public PluginClassLoader(String pluginId, ClassLoader apiParent) {
        super("plugin:" + pluginId, apiParent);
    }
}
```

Đây là subclass tối giản minh họa cách chọn **name** và **parent**. Nó chưa đọc plugin JAR; muốn nạp bytecode riêng còn cần cơ chế phù hợp, chẳng hạn override `findClass` hoặc sử dụng một loader có khả năng đọc nguồn class được chỉ định.

## 2. Các constructor của ClassLoader trên Java 17

`java.lang.ClassLoader` cung cấp ba constructor `protected` sau:

| Constructor | Parent | Tên của loader | Tình huống |
|---|---|---|---|
| `ClassLoader()` | `ClassLoader.getSystemClassLoader()` | Không đặt tên (`getName() == null`) | Loader đơn giản muốn mặc định delegating qua system loader |
| `ClassLoader(ClassLoader parent)` | Chính `parent` được truyền | Không đặt tên (`getName() == null`) | Cần chọn parent rõ ràng nhưng không cần nhãn |
| `ClassLoader(String name, ClassLoader parent)` | Chính `parent` được truyền | `name` hoặc `null` nếu không đặt tên | Plugin/module loader muốn cả parent lẫn tên dễ truy vết |

Điểm cần nhớ:

- `protected` có nghĩa các constructor được gọi từ subclass như `super(...)`, không phải `new ClassLoader()` trực tiếp trong mã ứng dụng ngoài phạm vi phù hợp.
- Constructor không tham số thực sự dùng **system class loader** làm parent theo contract JDK 17; nó không chọn “loader của class gọi” hay TCCL của thread hiện tại.
- `parent == null` trong constructor hai/ba tham số biểu thị parent là **bootstrap class loader** ở cơ chế delegation mặc định. Nó **không** tạo một bootstrap loader object mới.
- Với constructor có `name`, `null` được chấp nhận để tạo loader không tên; **chuỗi rỗng `""` không được phép** và gây `IllegalArgumentException`.
- Constructor có tên được giới thiệu từ **Java 9**, vì vậy dùng được trên Java 17 nhưng không có trên Java 8.

### Mẫu subclass hợp lệ

```java
public final class DemoLoader extends ClassLoader {
    public DemoLoader() {
        super();
    }

    public DemoLoader(ClassLoader parent) {
        super(parent);
    }

    public DemoLoader(String name, ClassLoader parent) {
        super(name, parent);
    }
}
```

Đây là class độc lập hợp lệ nếu lưu trong file `DemoLoader.java`. Trong Java 17, lời gọi constructor của superclass bằng `super(...)` cần xuất hiện đầu tiên trong constructor tương ứng. Nếu bỏ `super()` ở constructor không tham số, Java compiler chèn lời gọi `super()` ngầm.

**Constructor chỉ cấu hình loader object.** Class `DemoLoader` phía trên không override `findClass`, nên chưa có cơ chế riêng để đọc một JAR mới.

## 3. Parent là gì trong class loading?

`ClassLoader` thường dùng **parent delegation** khi được yêu cầu nạp một class. Với `ClassLoader.loadClass(String, boolean)` mặc định:

1. Gọi `findLoadedClass(name)` xem class đã được JVM ghi nhận đối với loader này chưa.
2. Nếu chưa có, thử nhờ `parent.loadClass(name)`. Nếu parent `null`, thử dùng **bootstrap loader**.
3. Nếu parent/bootstrap không cung cấp class, gọi `findClass(name)` của loader hiện tại.
4. Nếu yêu cầu resolve, gọi `resolveClass` theo contract rồi trả `Class<?>`.

Mô hình khái niệm:

```text
custom.loadClass("com.example.Service")
              |
              v
     Đã nạp qua loader này?
        | yes                 | no
        v                     v
  Dùng Class<?> cũ       Parent delegation
                               |
                  +------------+-----------+
                  | tìm được               | không tìm được
                  v                        v
             Dùng Class<?>            custom.findClass
              của parent                    |
                                      Tự tìm/định nghĩa
                                      hoặc thất bại
```

**Parent không đồng nghĩa superclass Java.** Một class loader có thể có `parent` là loader object của class khác bất kể quan hệ kế thừa Java giữa hai class loader implementations.

**Parent không đồng nghĩa defining loader của chính custom loader class.** Ví dụ `PluginClassLoader.class` có thể được application loader định nghĩa; trong khi object `new PluginClassLoader(..., platformLoader)` do app tạo có `getParent() == platformLoader`. Hai thuộc tính nói về hai chuyện khác nhau.

### Khi parent tìm được class

Nếu parent trả `Class<?>`, custom loader dùng class đó; **không tự tạo thêm class có cùng tên**.

```java
ClassLoader app = MainApp.class.getClassLoader();
ClassLoader child = new ClassLoader(app) { };

Class<?> c = child.loadClass("MainApp");
```

Đây là đoạn minh họa cần class `MainApp` đã có. Nếu `MainApp` được app loader định nghĩa, `c == MainApp.class` là true; còn `c.getClassLoader() == child` là false. **Initiating loader và defining loader có thể khác nhau**.

### Khi parent không tìm được class

Loader hiện tại được phép dùng `findClass` để tìm class từ nguồn riêng. Nhưng nếu subclass không override `findClass` thì implementation mặc định ném `ClassNotFoundException`.

Vì vậy:

```java
ClassLoader isolated = new ClassLoader(null) { };
isolated.loadClass("java.lang.String"); // thường thành công qua bootstrap
isolated.loadClass("MyApplication");    // thường thất bại nếu không tự tìm được
```

Đoạn trên minh họa hành vi trên JDK 17 chạy class path thông thường, với `MyApplication` chỉ là class ứng dụng. Lời gọi thứ hai có thể ném checked exception nên cần xử lý trong chương trình thật.

## 4. Chọn parent nào cho từng nhu cầu?

Trên JDK 17, các tầng loader thường gặp là bootstrap, platform và application/system. Nhưng custom loaders có thể được tổ chức theo nhiều mô hình khác nhau.

| Parent được chọn | Khả năng thường có | Điểm cần thận trọng |
|---|---|---|
| `null` | Delegation tới bootstrap trong thuật toán mặc định | Không bảo đảm nhìn thấy mọi platform class và class của app; phải tự cung cấp logic `findClass` cho phần riêng |
| `ClassLoader.getPlatformClassLoader()` | Có thể truy cập các lớp platform nhìn thấy được | Không mặc nhiên thấy mọi class nằm ở application class path |
| `ClassLoader.getSystemClassLoader()` | Trong app class path điển hình, nhìn thấy API/class của ứng dụng | Không mặc nhiên thấy các class/plugin được nạp bởi custom loader khác |
| `SharedApi.class.getClassLoader()` | Dùng cùng loader có thể nhìn thấy shared API type | Cần xác nhận implementation/plugin classes được cung cấp bởi finder riêng; tránh duplicate shared API |
| Loader plugin khác | Có thể ủy quyền sang loader chuyên biệt nếu thiết kế yêu cầu | Cân nhắc lifecycle, dependency graph, visibility và nguy cơ deadlock |

Chọn `parent` đúng là chọn **ràng buộc lookup và chia sẻ kiểu**. Không có một lựa chọn cố định luôn phù hợp với mọi ứng dụng.

### Parent null không có nghĩa “không thấy Java core”

Khi `parent == null`, `ClassLoader.loadClass` mặc định sử dụng bootstrap loader ở bước delegation. Vì vậy, `java.lang.Object` và `java.lang.String` thông thường vẫn có thể được nạp.

Tuy nhiên trên JDK 17, **không phải mọi Java platform class đều do bootstrap định nghĩa**. Các module và built-in loaders được phân chia phù hợp với runtime. Tài liệu constructor của Java SE 17 cảnh báo rõ: khi parent là `null`, **không có đảm bảo mọi platform class đều visible**.

Không nên chọn parent null chỉ vì muốn “cách ly tuyệt đối” hoặc “an toàn tuyệt đối”: loader đó vẫn sử dụng core runtime, và class loading không phải một sandbox bảo mật hoàn chỉnh.

### Parent là system loader có phù hợp plugin?

Có khi phù hợp, nếu plugin cần share interfaces/DTO do app loader định nghĩa. Nhưng với application server, module layer, OSGi-like container hoặc plugin framework, system loader chưa chắc là loader chứa shared API cần dùng.

Một cách ổn định hơn: tìm loader **định nghĩa shared contract**, chẳng hạn `PluginApi.class.getClassLoader()`, rồi dùng nó làm parent nếu architecture mong muốn. Trường hợp API class được bootstrap định nghĩa thì `getClassLoader()` có thể trả `null`, cần hiểu semantics của constructor đích.

## 5. Tên loader dùng để làm gì?

Từ Java 9, constructor:

```java
super("plugin:billing", parent);
```

cho phép gắn tên cho loader. Có thể đọc qua:

```java
String name = loader.getName();
```

Tên được dùng để **nhận biết** loader trong công cụ, log, diagnostic hoặc khi in thông tin cấu hình.

Những điều tên **không làm được**:

- Không quyết định binary name hoặc runtime identity của class.
- Không thay đổi parent delegation hoặc thứ tự tìm class.
- Không chỉ định đường dẫn JAR, thư mục hoặc module.
- Không bảo đảm uniqueness; hai loader objects có thể cùng tên.
- Không làm một loader trở thành bootstrap, platform hay system loader.
- Không tự đặt tên cho classes hoặc threads.

Ví dụ:

```java
ClassLoader app = MyApp.class.getClassLoader();
DemoLoader a = new DemoLoader("plugins", app);
DemoLoader b = new DemoLoader("plugins", app);

boolean sameName = a.getName().equals(b.getName());
boolean sameObject = a == b;
```

Đây là đoạn minh họa cần hai class `MyApp`/`DemoLoader`. `sameName` là true, `sameObject` là false. **Tên giống nhau không có nghĩa hai loader là một**.

### Quy tắc chọn tên thực tế

Tên hữu ích thường gồm chức năng và một định danh **không nhạy cảm**:

- `plugin:invoice`
- `tenant-loader:west`
- `module:reporting`
- `reload:inventory:v2`

Không nên ghi mật khẩu, token, đường dẫn riêng tư hoặc secret vào loader name vì các công cụ chẩn đoán có thể hiển thị tên đó. Tên dễ đọc nhưng **không phải security boundary**.

Khi cần phân biệt các lần hot reload, có thể dùng nhãn version hoặc run ID nội bộ không nhạy cảm, song đừng dựa vào label để xác định runtime class identity; hãy so sánh loader objects hoặc `Class<?>`.

### Tên null, tên rỗng và getName

- `new DemoLoader(null, app)` với constructor tên cho một loader **không tên**.
- `new DemoLoader("", app)` ném **`IllegalArgumentException`** theo Java SE 17.
- `loader.getName()` trả `null` nếu loader không tên.
- `getName()` là public và không `final` vì tương thích API; nếu subclass override, contract yêu cầu trả đúng tên được đặt tại thời điểm tạo.

Tên loader không được dùng thay vì kiểm tra `Class.getClassLoader()` khi muốn biết **defining loader**.

## 6. Ví dụ Java 17: so sánh ba constructor, parent và tên

Tạo file **`LoaderConstructorDemo.java`**:

```java
public class LoaderConstructorDemo {
    static class DemoLoader extends ClassLoader {
        DemoLoader() {
            super();
        }

        DemoLoader(ClassLoader parent) {
            super(parent);
        }

        DemoLoader(String name, ClassLoader parent) {
            super(name, parent);
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        ClassLoader app = LoaderConstructorDemo.class.getClassLoader();
        ClassLoader system = ClassLoader.getSystemClassLoader();

        DemoLoader defaultLoader = new DemoLoader();
        DemoLoader parentLoader = new DemoLoader(app);
        DemoLoader namedA = new DemoLoader("plugin:orders", app);
        DemoLoader namedB = new DemoLoader("plugin:orders", app);
        DemoLoader bootstrapChild = new DemoLoader("isolated", null);

        System.out.println("Default parent is system: "
                + (defaultLoader.getParent() == system));

        System.out.println("Explicit parent is app: "
                + (parentLoader.getParent() == app));

        System.out.println("No-arg name is null: "
                + (defaultLoader.getName() == null));

        System.out.println("Parent-only name is null: "
                + (parentLoader.getName() == null));

        System.out.println("Named loader: " + namedA.getName());

        System.out.println("Same name: "
                + namedA.getName().equals(namedB.getName()));

        System.out.println("Different loader objects: "
                + (namedA != namedB));

        System.out.println("Null parent: "
                + (bootstrapChild.getParent() == null));

        System.out.println("Bootstrap supplies String: "
                + (bootstrapChild.loadClass("java.lang.String")
                        == String.class));

        System.out.println("App parent supplies main: "
                + (parentLoader.loadClass("LoaderConstructorDemo")
                        == LoaderConstructorDemo.class));

        try {
            bootstrapChild.loadClass("LoaderConstructorDemo");
            System.out.println("Unexpected app lookup success");
        } catch (ClassNotFoundException expected) {
            System.out.println("Bootstrap-only cannot find main");
        }

        boolean rejectedEmptyName = false;
        try {
            new DemoLoader("", app);
        } catch (IllegalArgumentException expected) {
            rejectedEmptyName = true;
        }
        System.out.println("Empty name rejected: "
                + rejectedEmptyName);
    }
}
```

Chạy trên JDK 17 ở thư mục chứa file:

```bash
javac LoaderConstructorDemo.java
java LoaderConstructorDemo
```

Stdout trong cấu hình JDK 17 class path thông thường:

```text
Default parent is system: true
Explicit parent is app: true
No-arg name is null: true
Parent-only name is null: true
Named loader: plugin:orders
Same name: true
Different loader objects: true
Null parent: true
Bootstrap supplies String: true
App parent supplies main: true
Bootstrap-only cannot find main
Empty name rejected: true
```

Đọc kết quả:

1. Constructor không tham số của `ClassLoader` chọn system class loader làm parent; constructor nhận `app` đặt chính app loader.
2. Không truyền tên khiến `getName()` trả `null`; constructor `(name,parent)` cho tên `plugin:orders`.
3. Hai loader objects có thể dùng cùng tên nhưng vẫn là hai objects khác nhau.
4. `bootstrapChild` có parent `null` và thông qua default `loadClass` vẫn tìm được `String` từ bootstrap; nó không tự đọc `LoaderConstructorDemo.class` từ application class path.
5. `parentLoader.loadClass("LoaderConstructorDemo")` ủy quyền để trả class được **app loader định nghĩa**, không tự tạo một bản.
6. `""` là tên loader không hợp lệ, vì thế `IllegalArgumentException` được bắt và kết quả cuối là `true`.

**Điều kiện của ví dụ:** chạy chương trình ở class path thông thường và không thay đổi các loader classes/bytecode trong quá trình chạy. Nếu cấu hình một system loader đặc biệt, quy tắc constructor không tham số vẫn là dùng loader mà `getSystemClassLoader()` trả về; nhưng chi tiết visibility của app có thể khác.

## 7. Parent được chọn ảnh hưởng lúc nào tới findClass?

Nhiều người viết subclass:

```java
public class FileBackedLoader extends ClassLoader {
    FileBackedLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        // Đọc bytes của name từ nguồn class riêng.
        // Sau khi kiểm tra hợp lệ, defineClass(name, bytes, 0, bytes.length).
        throw new ClassNotFoundException(name);
    }
}
```

Đây là **khung cấu trúc để giải thích**; chưa phải loader đọc file thật và luôn ném `ClassNotFoundException`. Điều quan trọng là `findClass` thường được gọi **sau khi parent thất bại**, trong thuật toán `loadClass` mặc định.

Vì vậy, nếu parent **đã thấy class cùng tên**, code `findClass` của bạn có thể không bao giờ chạy với tên đó. Muốn một implementation có quyền tự định nghĩa namespace riêng, cần thiết kế **đường lookup** cẩn thận, không phải chỉ đổi tên loader.

### Ví dụ Java 17: parent-first khiến findClass không chạy khi parent tìm được

Tạo file **`ParentFirstDemo.java`**:

```java
public class ParentFirstDemo {
    static class ProbeLoader extends ClassLoader {
        private int findCalls;

        ProbeLoader(String name, ClassLoader parent) {
            super(name, parent);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            findCalls++;
            throw new ClassNotFoundException(name);
        }

        int findCalls() {
            return findCalls;
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        ClassLoader app = ParentFirstDemo.class.getClassLoader();
        ProbeLoader child = new ProbeLoader("probe", app);

        Class<?> loaded = child.loadClass("ParentFirstDemo");
        System.out.println("Parent supplied main: "
                + (loaded == ParentFirstDemo.class));
        System.out.println("findClass calls after hit: "
                + child.findCalls());

        boolean missing = false;
        try {
            child.loadClass("example.never.Exists123456");
        } catch (ClassNotFoundException expected) {
            missing = true;
        }

        System.out.println("Missing class reported: " + missing);
        System.out.println("findClass calls after miss: "
                + child.findCalls());
    }
}
```

Chạy trong một class path sạch không chứa class `example.never.Exists123456`:

```bash
javac ParentFirstDemo.java
java ParentFirstDemo
```

Stdout:

```text
Parent supplied main: true
findClass calls after hit: 0
Missing class reported: true
findClass calls after miss: 1
```

`ParentFirstDemo` đã được app loader định nghĩa, nên loader con nhận lại class đó và **không cần gọi `findClass`**. Class `example.never.Exists123456` không có ở parent; `findClass` của loader con được thử và ném `ClassNotFoundException`. Việc gắn tên `probe` giúp nhận diện loader, **không làm thay thuật toán parent-first**.

Trong hệ thống thật, nên ưu tiên override `findClass` khi chỉ muốn cung cấp cách tìm bytes riêng. Override `loadClass` để đổi parent-first thành child-first chỉ khi có kiến trúc rõ, kiểm soát namespace chia sẻ và xử lý đồng bộ hóa đúng cách.

## 8. Chọn parent và shared API trong ứng dụng plugin

Giả sử ứng dụng host có:

```text
Host / shared API loader
    api.Plugin
    api.Request
    |
    +-- Plugin loader A
    |      plugin.AImplementation
    |
    +-- Plugin loader B
           plugin.BImplementation
```

Nếu cả hai plugin cần implement `api.Plugin` và nhận `api.Request`, một thiết kế thông thường là cho plugin loader ủy quyền các shared types `api.*` tới **cùng loader sở hữu API**.

Nếu plugin loader tự định nghĩa một bản `api.Plugin` riêng, dù binary name trùng, đó có thể là **runtime class khác**, dẫn đến lỗi ép kiểu hoặc loading constraint khi host và plugin trao đổi types.

Chọn parent đúng giúp:

- Shared API/interface/DTO có cùng runtime identity.
- Code của plugin vẫn tự định nghĩa implementation và dependency riêng nếu lookup policy cho phép.
- Giảm nguy cơ `ClassCastException`, `LinkageError` hoặc provider không assignable.
- Dễ kiểm soát lifecycle: loader riêng có thể được bỏ tham chiếu khi plugin dừng, trong khi shared API loader sống cùng host.

**Nhưng parent được chọn không tự áp đặt toàn bộ chính sách isolation.** Nếu cần child-first cho một số packages và parent-first cho `java.*`/`api.*`, phải tự thiết kế chính sách lookup phù hợp và đảm bảo các ràng buộc Java/JVM.

### Parent null trong plugin có phải tốt hơn?

Không nhất thiết. Plugin parent-null có thể nhìn thấy bootstrap classes nhưng **không tự thấy API host**. Nếu plugin bytecode implement `api.Plugin` do host sở hữu, JVM cần resolve kiểu đó. Không có shared lookup path thì quá trình nạp/liên kết có thể thất bại.

Nếu cần plugin cô lập khỏi app nhưng vẫn chia sẻ contract, có thể dùng một loader chung chuyên biệt làm parent hoặc cơ chế delegate có kiểm soát, thay vì đặt parent null mà không có chiến lược tìm API.

## 9. Cách xác minh loader đã chọn

Khi debug, dùng các thuộc tính tách biệt:

```java
ClassLoader parent = myLoader.getParent();
String label = myLoader.getName();

Class<?> found = myLoader.loadClass("com.example.SomeClass");
ClassLoader actualOwner = found.getClassLoader();
```

Đây là đoạn **minh họa**, cần các biến/class thực tế và xử lý `ClassNotFoundException`. Ý nghĩa:

- `myLoader.getParent()`: parent cho delegation.
- `myLoader.getName()`: tên để chẩn đoán, có thể null.
- `found.getClassLoader()`: **defining loader của runtime class đã trả về**; có thể khác `myLoader` nếu parent cung cấp class.

Một bảng chẩn đoán nhanh:

| Quan sát | Không nên kết luận | Cách hiểu đúng |
|---|---|---|
| `myLoader.getName().equals(other.getName())` | Hai loader là một | Tên có thể trùng; so sánh object identity |
| `myLoader.getParent() == null` | Không delegation | Với default `loadClass`, ủy quyền tới bootstrap |
| `loaded.getClassLoader() != myLoader` | Lookup thất bại | Class có thể được parent định nghĩa và child trả về |
| `ClassNotFoundException` từ loader parent-null | JVM thiếu core classes | Loader có thể không biết class app và không override `findClass` |
| Loader có tên khác nhau | Classes của chúng chắc chắn khác | Nếu cùng ủy quyền cho parent, có thể nhận **cùng Class<?>** |
| `findClass` không được gọi | Override bị lỗi | Parent có thể đã trả kết quả trước |

### Class loader name có xuất hiện trong lỗi và log không?

Các công cụ JVM như class loading logs, stack traces hoặc diagnostics có thể hiển thị thông tin loader và tên. Trên HotSpot JDK 17, có thể bật:

```bash
java -Xlog:class+load=info LoaderConstructorDemo
```

Output có thể chứa nhiều lớp thư viện được nạp; định dạng log phụ thuộc JVM/version. Tên có ích để đọc, nhưng không dùng chỉ tên làm bằng chứng loader object nào đã định nghĩa class. Khi cần, xem thêm `Class.getClassLoader()` và các công cụ thống kê loader tương ứng.

## 10. Constructor và vòng đời của loader object

Tạo `new DemoLoader(...)` chỉ tạo một **loader object**, chưa nhất thiết định nghĩa class riêng. Trong JVM chạy lâu:

- Loader có thể sống rất lâu nếu nằm trong static registry, thread context class loader, thread-local, listener, cache hoặc executor.
- Các class do loader định nghĩa có runtime identities gắn với loader đó.
- Muốn hot reload version mới của cùng binary name, thường tạo **loader mới** thay vì cố định nghĩa lại class đã có ở loader cũ.
- Một loader mới có thể có **cùng name** với loader cũ nhưng là object khác và có thể định nghĩa class runtime khác.
- Việc bỏ tham chiếu tới loader/plugin không đảm bảo class unloading xảy ra ngay; garbage collection và class unloading phụ thuộc reachability cùng JVM implementation.

Do đó tên loader nên biểu thị logical owner và optional version để debug, nhưng **lifecycle phải quản lý qua object references và trách nhiệm giải phóng resources**, không qua tên.

### Constructor chưa đủ để đọc JAR

`new DemoLoader("plugin:A", appLoader)` không làm loader hiểu đường dẫn JAR plugin. Nếu dùng `URLClassLoader`, cần cung cấp URLs/parent đúng constructor mà loại loader đó hỗ trợ. Với subclass `ClassLoader`, thường cài `findClass` đọc bytes và `defineClass` theo API; cần xử lý package identity, access controls, resources và lỗi.

Nếu loader sở hữu tài nguyên có thể đóng (ví dụ `URLClassLoader`), thiết kế lifecycle để gọi `close()` khi thích hợp và sau khi không còn tác vụ sử dụng. `close()` không có nghĩa JVM tức thì unload mọi class do loader đã định nghĩa.

## 11. Những lựa chọn thường sai

### “Để super() tự chọn parent vì loader hiện tại là plugin loader”

Không đúng. `ClassLoader()` lấy **system class loader**, không tự động dùng TCCL hoặc loader của class plugin. Trong môi trường phức tạp, parent mặc định có thể không thấy shared API cần dùng.

**Sửa:** nếu biết loader sở hữu API, truyền tường minh vào `super(apiLoader)` hoặc `super(name, apiLoader)`.

### “Truyền parent null để có loader hoàn toàn độc lập với Java core”

Không đúng. Parent null được `loadClass` mặc định diễn giải thành bootstrap delegation. Đồng thời không đảm bảo mọi platform class visible.

**Sửa:** thiết kế isolation có chủ đích, đảm bảo shared runtime/API types được resolve đúng.

### “Tên loader là khóa duy nhất để xác định các class”

Không đúng. Hai loader objects có thể trùng tên. JVM class identity dựa vào binary name cộng **defining loader**, không dùng `getName()`.

**Sửa:** lưu loader object/reference, khi cần so sánh runtime class identity thì so sánh `Class<?>` bằng `==`.

### “Đặt name là plugin-v2 thì JVM tự nạp JAR v2”

Không đúng. `name` là label; không quyết định classpath, URL hay finder.

**Sửa:** quản lý nguồn class bytes/resource URLs trong finder riêng, không nhồi đường dẫn vào tên như một phương thức cấu hình.

### “Dùng super(null) tương đương super()”

Không đúng. `super()` dùng system loader làm parent; `super((ClassLoader) null)` chọn bootstrap lookup trong default delegation.

**Sửa:** chọn rõ behavior mong muốn; trong source code, `super(null)` là constructor một tham số khi không có constructor một tham số kiểu khác gây nhập nhằng, còn `super(name,null)` rõ parent hơn.

### “Cứ override findClass thì class trong plugin luôn được định nghĩa bởi custom loader”

Không đúng. Parent-first sẽ ưu tiên class tìm được ở parent; `findClass` chỉ được gọi khi cần.

**Sửa:** thiết kế namespace: shared API parent-first, implementation có thể cần đường tìm riêng. Chỉ override `loadClass` khi thật sự cần, đồng bộ hóa qua `getClassLoadingLock(name)` và `findLoadedClass(name)`.

### “Tên loader rỗng giúp tiết kiệm log”

Không đúng. Constructor có tên từ Java 9 từ chối `""`.

**Sửa:** truyền `null` để không đặt tên hoặc truyền tên **không rỗng**.

### “Tên loader sẽ tự bảo đảm class unloading”

Không đúng. Tên là metadata cho chẩn đoán; garbage collection/unloading phụ thuộc reachability và JVM.

**Sửa:** dọn các tham chiếu dài hạn như thread pools, listener, callback, registries, context class loader và caches khi plugin đóng.

## 12. Một checklist lựa chọn nhanh

Trước khi triển khai, trả lời theo thứ tự:

1. **Loader này có cần thấy shared API của host không?** Nếu có, chọn một parent nhìn thấy đúng API classes; kiểm tra runtime identity.
2. **Nó cần nạp class riêng từ đâu?** JAR, directory, generated bytecode hay nguồn khác — phải có finder phù hợp; constructor không tự giải quyết.
3. **Ứng dụng muốn parent-first hay một chiến lược có chọn lọc?** Đừng child-first cho mọi package nếu có shared contracts.
4. **Có cần tên loader để debug không?** Nếu có, dùng `ClassLoader(String, ClassLoader)` và nhãn không nhạy cảm, không rỗng.
5. **Parent null có thực sự cần không?** Nhớ bootstrap delegation và giới hạn platform visibility.
6. **Có tình huống hot reload không?** Mỗi loader object sở hữu class identities; quản lý vòng đời, đóng tài nguyên và xóa references phù hợp.
7. **Có concurrency không?** Nếu override `loadClass`, giữ các thao tác check/delegate/find/resolve đồng bộ phù hợp `getClassLoadingLock` và parallel-capable contract.
8. **Có dùng named modules không?** Parent và `ClassLoader` không bỏ qua JPMS readability, exports/opens hoặc module-layer relationships.

## 13. Bài tập tự kiểm tra

**Câu 1.** Trong Java 17, `new DemoLoader()` và `new DemoLoader(null)` có cùng parent không nếu cả hai constructor tương ứng gọi `super()` và `super(parent)`?

**Đáp án:** Không. `super()` chọn system loader; `super(null)` chọn parent null, tức bootstrap delegation trong default `loadClass`.

**Câu 2.** `new DemoLoader("", app)` và `new DemoLoader(null, app)` khác nhau chỗ nào?

**Đáp án:** Chuỗi rỗng gây `IllegalArgumentException`, còn tên `null` hợp lệ và tạo loader không tên.

**Câu 3.** Hai loaders đều tên `plugin:orders`. Chúng có phải cùng một loader object không?

**Đáp án:** Không thể suy ra từ tên. Name không nhất thiết duy nhất, class identity cũng không dựa vào name.

**Câu 4.** Loader con có parent `app`. Vì sao `child.loadClass("MyApp")` có thể trả class do app loader định nghĩa?

**Đáp án:** Vì thuật toán mặc định ưu tiên parent lookup. `Class<?>` trả về giữ nguyên defining loader là app, không trở thành class do child định nghĩa.

**Câu 5.** Vì sao truyền `null` làm parent không luôn giúp tìm mọi Java SE/JDK platform class?

**Đáp án:** Bootstrap không định nghĩa mọi platform class trên JDK 17. API của constructor cũng cảnh báo không có bảo đảm toàn bộ platform classes visible khi parent `null`.

**Câu 6.** Nếu `findClass` đã được override, tại sao khi nạp một class parent thấy được, bộ đếm lần `findClass` vẫn là 0?

**Đáp án:** `loadClass` mặc định kiểm tra lớp đã nạp rồi ủy quyền parent; chỉ khi parent không cung cấp class thì mới thử `findClass`.

**Câu 7.** Một plugin cần dùng interface `api.Plugin` của host. Nên chọn parent theo điều gì?

**Đáp án:** Loader phải resolve interface qua **đúng shared API runtime class**; thường chọn parent có thể thấy/định nghĩa `api.Plugin` thay vì đơn giản chọn null hoặc system loader theo thói quen.

**Câu 8.** Loader mới mang cùng tên với loader của phiên bản plugin cũ có tự thay thế các class runtime cũ không?

**Đáp án:** Không. Loader objects khác nhau có thể định nghĩa các runtime classes khác nhau, dù cùng tên hoặc cùng class binary name; class unloading phụ thuộc reachability, không phụ thuộc tên.

## 14. Ghi nhớ và tài liệu tham khảo

- **`ClassLoader()`** chọn **system class loader** làm parent.
- **`ClassLoader(parent)`** chọn parent tường minh; **`parent == null`** biểu thị bootstrap trong thuật toán mặc định.
- **`ClassLoader(name,parent)`** (Java 9+) đặt tên loader; `name == null` hợp lệ, `name == ""` bị từ chối.
- **Tên loader không tạo runtime class identity**; binary name cộng defining loader mới xác định class.
- **Parent quyết định đường delegation**, còn `findClass`/nguồn bytes quyết định loader có thể tự định nghĩa những class nào.
- **Khi thiết kế plugin**, ưu tiên một loader chung cho shared API types, xác định lookup policy và lifecycle trước khi chọn constructor.

Nguồn chính thức:

1. [Java SE 17 — ClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): constructors, `getName`, `getParent`, `loadClass`, `findClass`, `getClassLoadingLock` và runtime built-in loaders.
2. [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): ví dụ loader có URLs, parent, tên và lifecycle `close`.
3. [JVMS 17 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): defining/initiating loaders, type identity và constraints.
4. [JEP 261 — Module System](https://openjdk.org/jeps/261): cách built-in loaders và Java modules hoạt động từ Java 9.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-003">Ưu tiên ghi đè findClass khi giữ cơ chế ủy quyền mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Ưu tiên ghi đè findClass khi giữ cơ chế ủy quyền mặc định

## 1. Quy tắc cốt lõi

Khi xây dựng **custom class loader** trong Java, nếu mục tiêu chỉ là tìm và định nghĩa lớp từ một nguồn mới (thư mục, kho bytecode riêng, JAR/nguồn plugin đặc biệt) nhưng **vẫn muốn giữ cơ chế parent-first delegation**, cách thiết kế được tài liệu `ClassLoader` khuyến nghị là:

> **Kế thừa `ClassLoader`, ghi đè `findClass(String name)`, và giữ nguyên `loadClass(String, boolean)` của superclass.**

Lý do: lớp cơ sở đã cung cấp quy trình tìm lớp có cache, ủy quyền parent, khóa đồng bộ và bước link tùy chọn. Bạn chỉ cần viết phần **“khi parent không tìm được, loader của tôi lấy bytecode ở đâu và định nghĩa lớp thế nào?”**. Phần đó chính là hợp đồng của `findClass`.

Sự phân chia trách nhiệm:

| Thành phần | Vai trò chính | Khi viết loader parent-first |
| --- | --- | --- |
| `ClassLoader.loadClass(String)` | API bên ngoài dùng để yêu cầu lớp theo binary name; tương đương `loadClass(name, false)`. | **Giữ nguyên.** |
| `ClassLoader.loadClass(String, boolean)` | Điều phối cache, parent delegation, `findClass`, có thể gọi `resolveClass`. | **Thường giữ nguyên.** |
| `findLoadedClass(String)` | Kiểm tra loader này đã được JVM ghi nhận là initiating loader của lớp cùng tên chưa. | Đã được `loadClass` mặc định gọi. |
| `findClass(String)` | Tìm/định nghĩa lớp từ **nguồn riêng của loader hiện tại** sau khi parent không tìm thấy. | **Đây là chỗ nên override.** |
| `defineClass(...)` | Biến bytecode hợp lệ thành đối tượng `Class<?>`, ràng buộc với defining loader. | Gọi từ `findClass` sau khi đọc đúng bytes. |
| `resolveClass(Class<?>)` | Yêu cầu link class nếu cần. | Để `loadClass` mặc định gọi khi `resolve=true`. |

**Không nhầm:** `findClass` không phải API thông thường để ứng dụng gọi thẳng nhằm bỏ qua parent. Code bên ngoài nên gọi `loader.loadClass("pkg.Type")` (hoặc một API cấp cao phù hợp), để toàn bộ quy trình chuẩn được áp dụng.

## 2. Parent-first delegation: vì sao chỉ override findClass là đủ?

Thuật toán mặc định của `ClassLoader.loadClass(String, boolean)` được mô tả trong Java SE 21:

1. Gọi `findLoadedClass(name)` để tránh tìm/định nghĩa lại lớp đã biết với cùng tên.
2. Nếu chưa có, gọi `loadClass` của parent. Nếu parent là `null`, dùng loader tích hợp bootstrap của JVM.
3. Nếu parent không tìm được, gọi `findClass(name)` của loader hiện tại.
4. Khi tìm được và `resolve == true`, gọi `resolveClass(c)`.
5. Trả đối tượng `Class<?>`; hoặc ném `ClassNotFoundException` khi không có nơi nào tìm thấy.

~~~text
                loader.loadClass(name)
                         |
                         v
              findLoadedClass(name)?
                    /         \
                 có            chưa
                 |               |
                 |         parent.loadClass(name)
                 |            /      \
                 |         có        không
                 |         |           |
                 |         |     findClass(name)
                 |         |       (override)
                 |         |           |
                 +---------+-----------+
                           |
                   trả Class<?> đã tìm
                           |
             resolve=true? -> resolveClass(c)
~~~

Đây là **mô hình logic**; cơ chế thực còn có khóa từ `getClassLoadingLock(name)`, xử lý ngoại lệ và những quy tắc JVM/JDK khác. Điểm quan trọng là `findClass` **được gọi ở bước fallback**, nên bạn không phải tự viết lại toàn bộ thuật toán.

Ví dụ parent là application loader và child là loader của thư mục plugin:

~~~text
Application/System Loader (parent)
  ├── FolderLoaderDemo
  └── java APIs do parent/bootstrap cung cấp
              ^
              |
       ủy quyền trước
              |
Custom FolderClassLoader (child)
  └── source root:
      out/plugin/
         plugin/Greeter.class
~~~

- `child.loadClass("java.lang.String")` nhận lớp cốt lõi qua delegation; không cần `findClass` của child định nghĩa lại.
- `child.loadClass("plugin.Greeter")` hỏi parent trước; nếu parent không có plugin, child mới đọc `out/plugin/plugin/Greeter.class` trong `findClass`.
- Nếu parent **đã** có `plugin.Greeter`, child sẽ nhận bản parent. Việc override `findClass` không đảo ưu tiên sang child.
- Nếu child từng nhận lớp đó rồi, lần gọi sau có thể trả `Class<?>` đã ghi nhận mà không đọc class file thêm lần nữa.

**Defining loader** là loader thực sự định nghĩa class bằng `defineClass`; **initiating loader** có thể chỉ là loader khởi đầu yêu cầu được ủy quyền cho parent. Hai khái niệm không phải lúc nào cũng là cùng một đối tượng.

## 3. Hợp đồng findClass(String): nhận tên gì, trả kết quả gì?

Chữ ký phổ biến:

~~~java
@Override
protected Class<?> findClass(String name)
        throws ClassNotFoundException {
    // Tự tìm bytecode theo binary name, sau đó defineClass.
}
~~~

Đây là khung giải thích, chưa phải phương thức hoàn chỉnh. Những quy tắc quan trọng:

- `name` là **binary name**, ví dụ `plugin.Greeter`, không phải `"plugin/Greeter.class"` hay một đường dẫn file.
- Nếu đọc từ cây file class, ánh xạ tên theo package: `plugin.Greeter` → `plugin/Greeter.class`; đường dẫn được resolve **bên dưới root** mà loader sở hữu.
- Nếu tên **không có trong nguồn riêng**, ném `ClassNotFoundException(name)`, có thể giữ `cause` như `NoSuchFileException`.
- Nếu đọc file gặp `IOException`, có thể bọc vào `ClassNotFoundException` với nguyên nhân gốc để người gọi chẩn đoán; đừng lặng lẽ trả `null` từ overload một tham số này.
- Nếu bytes tìm được, dùng `defineClass(name, bytes, 0, bytes.length)` với **đúng tên và bytes tương ứng**. Không truyền tên của lớp A nhưng bytecode khai báo lớp B.
- Không tự đổi `ClassFormatError`, `VerifyError` hoặc `LinkageError` thành `ClassNotFoundException` bằng một `catch(Throwable)` chung. Những lỗi ấy không phải “không tìm thấy file”.
- `findClass` mặc định của `ClassLoader` chỉ ném `ClassNotFoundException`; override là nơi thực hiện logic tìm nguồn riêng.

### Vì sao gọi defineClass chứ không chỉ trả bytes?

`defineClass` tạo đối tượng `Class<?>` theo hợp đồng JVM. Khi class được định nghĩa thành công, loader này trở thành **defining loader** của class thông thường đó. Danh tính runtime của class/interface phụ thuộc cặp **binary name và defining loader**, không chỉ tên package.

Vì vậy, đừng tự duy trì cache độc lập kiểu `Map<String, Class<?>>` rồi trả nhầm class được định nghĩa bởi loader khác nếu không có lý do rõ ràng. `findLoadedClass` và thuật toán chuẩn vốn đã phối hợp với JVM để tránh nhiều định nghĩa trong cùng loader.

## 4. Ví dụ Java 17+ độc lập: loader đọc bytecode từ thư mục

Ví dụ này có **hai tệp nguồn Java** được biên dịch vào **hai thư mục output riêng**. Class plugin **không nằm trên classpath của host** khi chạy, để chứng minh child thực sự dùng `findClass`.

### Cấu trúc project

~~~text
example/
├── source/
│   ├── plugin/
│   │   └── Greeter.java
│   └── host/
│       └── FolderLoaderDemo.java
└── output/
    ├── plugin/          <-- sau biên dịch
    │   └── plugin/
    │       └── Greeter.class
    └── host/            <-- sau biên dịch
        ├── FolderLoaderDemo.class
        └── FolderLoaderDemo$FolderClassLoader.class
~~~

### File `source/plugin/Greeter.java`

~~~java
package plugin;

public class Greeter {
    static {
        System.out.println("Greeter initialized");
    }

    public static String greet() {
        return "Hello from plugin";
    }
}
~~~

### File `source/host/FolderLoaderDemo.java`

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class FolderLoaderDemo {
    static final class FolderClassLoader extends ClassLoader {
        private final Path root;

        FolderClassLoader(Path root, ClassLoader parent) {
            super(parent);
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        protected Class<?> findClass(String binaryName)
                throws ClassNotFoundException {
            Path classFile = root.resolve(
                    binaryName.replace('.', '/') + ".class").normalize();

            if (!classFile.startsWith(root)) {
                throw new ClassNotFoundException(binaryName);
            }

            final byte[] bytes;
            try {
                bytes = Files.readAllBytes(classFile);
            } catch (NoSuchFileException e) {
                throw new ClassNotFoundException(binaryName, e);
            } catch (IOException e) {
                throw new ClassNotFoundException(
                        "Cannot read class: " + binaryName, e);
            }

            System.out.println("findClass: " + binaryName);
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("plugin root");
        }

        ClassLoader parent = FolderLoaderDemo.class.getClassLoader();
        FolderClassLoader loader =
                new FolderClassLoader(Path.of(args[0]), parent);

        Class<?> jdk = loader.loadClass("java.lang.String");
        System.out.println("parent class: " + (jdk == String.class));

        Class<?> plugin = loader.loadClass("plugin.Greeter");
        System.out.println("plugin defined by child: "
                + (plugin.getClassLoader() == loader));

        System.out.println("cached Class: "
                + (plugin == loader.loadClass("plugin.Greeter")));

        System.out.println("method: "
                + plugin.getMethod("greet").invoke(null));

        try {
            loader.loadClass("plugin.Missing");
        } catch (ClassNotFoundException expected) {
            System.out.println("missing: ClassNotFoundException");
        }
    }
}
~~~

Điểm quyết định: **`FolderClassLoader` chỉ override `findClass`**; không override `loadClass`. Mọi đường gọi `loader.loadClass(...)` đều sử dụng cache, parent-first và cơ chế đồng bộ chuẩn có sẵn của `ClassLoader`.

`Path.toAbsolutePath().normalize()` dùng thư mục hiện tại tại lúc tạo loader để xác định root của plugin. `Files.readAllBytes` tiện cho file `.class` nhỏ trong thí nghiệm; với code thực tế phải có giới hạn kích thước và xác thực dữ liệu đầu vào nếu nguồn không tin cậy. Kiểm tra `classFile.startsWith(root)` giúp không đi ra ngoài root sau bước chuẩn hóa đường dẫn cú pháp, nhưng không phải **biện pháp chống symlink/path traversal hoàn chỉnh** cho nguồn không tin cậy; production cần kiểm soát filesystem và security model riêng.

### Chạy trên Linux/macOS

Đứng ở thư mục `example`:

~~~bash
mkdir -p output/plugin output/host

javac --release 17 -d output/plugin source/plugin/Greeter.java
javac --release 17 -d output/host source/host/FolderLoaderDemo.java

java -cp output/host FolderLoaderDemo output/plugin
~~~

Không thêm `output/plugin` vào tham số `-cp` của lệnh `java` cuối. Nếu thêm, parent có thể tự tìm `plugin.Greeter`, khiến `findClass` của child không được gọi.

### Chạy trên Windows PowerShell

Tại thư mục `example`:

~~~powershell
New-Item -ItemType Directory -Force output\plugin, output\host | Out-Null

javac --release 17 -d output\plugin source\plugin\Greeter.java
javac --release 17 -d output\host source\host\FolderLoaderDemo.java

java -cp output\host FolderLoaderDemo output\plugin
~~~

### Stdout chính xác

~~~text
parent class: true
findClass: plugin.Greeter
plugin defined by child: true
cached Class: true
Greeter initialized
method: Hello from plugin
missing: ClassNotFoundException
~~~

Mã đã được biên dịch với `javac --release 17` và chạy bằng Java thực tế; stdout khớp nội dung trên.

### Phân tích đầu ra

1. **`parent class: true`** — lời gọi nạp `java.lang.String` qua child được parent/bootstrap đáp ứng. Không có dòng `findClass: java.lang.String`, nên child không tự định nghĩa bản sao JDK.
2. **`findClass: plugin.Greeter`** — parent không tìm được plugin, nên `loadClass` mặc định mới gọi override `findClass` của child để đọc bytecode từ `output/plugin/plugin/Greeter.class`.
3. **`plugin defined by child: true`** — class được tạo qua `defineClass` của `FolderClassLoader`.
4. **`cached Class: true`** — gọi `loader.loadClass("plugin.Greeter")` lần hai nhận cùng đối tượng `Class<?>`; dòng log `findClass` không xuất hiện thêm lần nữa.
5. **`Greeter initialized`** — `loadClass` không tự chạy static initializer; việc gọi `greet` qua reflection là một active use của phương thức `static`, vì thế JVM khởi tạo class trước lần gọi.
6. **`method: Hello from plugin`** — kết quả thân phương thức `greet`.
7. **`missing: ClassNotFoundException`** — không có `plugin.Missing` ở parent lẫn thư mục child, nên `findClass` ném lỗi tìm lớp và `loadClass` chuyển lỗi ấy cho caller.

Ví dụ dùng reflection để không cần host compile trực tiếp dựa trên kiểu `plugin.Greeter`. Nếu host có `import plugin.Greeter;` rồi biên dịch với plugin trong compile classpath, bạn phải đặc biệt kiểm soát runtime classpath và loader resolution; đó là chủ đề dependency phức tạp hơn. Ở đây trọng tâm là **vị trí đúng để override khi giữ parent-first**.

## 5. Vì sao không nên tự override loadClass khi không cần?

Nếu override `loadClass(String, boolean)`, bạn phải tự đảm bảo nhiều bất biến mà superclass đã xử lý:

| Trách nhiệm dễ bị quên | Nếu override `loadClass` không cẩn thận |
| --- | --- |
| Kiểm tra `findLoadedClass(name)` | Có thể định nghĩa lại cùng binary name → `LinkageError` hoặc các lỗi khác. |
| Hỏi parent trước | Phá parent-first, có thể tạo bản sao API/JDK class không tương thích. |
| Fallback sang `findClass` | Mất cơ chế tìm từ nguồn riêng hoặc nuốt `ClassNotFoundException` sai cách. |
| Sử dụng `getClassLoadingLock(name)` | Hai luồng cùng cố `defineClass`; rủi ro race, lỗi và deadlock. |
| Xử lý `resolve` | Quên `resolveClass(c)` khi `resolve == true`, đổi hợp đồng với caller. |
| Bảo toàn lỗi gốc | Nuốt `LinkageError`/`SecurityException` và báo sai là “không tìm thấy lớp”. |
| Tôn trọng loader cha | Kết quả class được định nghĩa ở loader không như phần còn lại của hệ thống kỳ vọng. |

**Tình huống lỗi minh họa** (không dùng trong chương trình thật):

~~~java
@Override
public Class<?> loadClass(String name) throws ClassNotFoundException {
    byte[] bytes = readBytes(name); // hàm minh họa, chưa tồn tại
    return defineClass(name, bytes, 0, bytes.length);
}
~~~

Mã này **có chủ ý sai và không độc lập chạy được**. Nó bỏ qua cache, parent delegation và khóa class loading; có thể tìm/định nghĩa lặp lớp đã nạp. Nó còn có nguy cơ cố định nghĩa các class cốt lõi hoặc shared interface thay vì nhận từ parent.

**Sửa đúng khi vẫn cần parent-first:** bỏ override `loadClass` không cần thiết, chuyển logic `readBytes(name)` + `defineClass(...)` vào `findClass(name)` giống ví dụ phần 4.

### Parent-first đặc biệt hữu ích với API dùng chung

Giả sử host định nghĩa `api.Plugin` và child định nghĩa `plugins.GreetingPlugin implements api.Plugin`. Khi child hỏi parent trước, nó dùng **cùng một `Class<?>` của `api.Plugin`** mà host đã thấy. Nếu loader con tự định nghĩa một bản `api.Plugin` khác cùng tên, ép đối tượng plugin sang API bên host có thể gặp `ClassCastException` hoặc lỗi liên kết kiểu.

Parent-first giúp thống nhất nền tảng và interface dùng chung; đồng thời vẫn cho child đọc những class riêng từ `findClass`. Tuy nhiên, nếu host parent đã có một thư viện cùng binary name với plugin, parent-first sẽ ưu tiên bản parent. **Nếu thật sự cần cô lập hai phiên bản, phải thiết kế loader policy riêng**, không đơn giản ép mọi trường hợp dùng `findClass`.

## 6. Kiến thức quan trọng về concurrency và cache

`loadClass(String, boolean)` mặc định đồng bộ trong giai đoạn nạp bằng monitor trả từ `getClassLoadingLock(name)`. Class loader đăng ký **parallel capable** có thể có khóa riêng theo tên, nhưng các subclass có yêu cầu đăng ký riêng phù hợp; đừng suy luận rằng tất cả loader con mặc nhiên parallel capable chỉ vì superclass `ClassLoader` đã được đăng ký.

Trong ví dụ `FolderClassLoader`, không override `loadClass` nên logic khóa cơ sở vẫn hoạt động. `findClass` vẫn cần **không tự tạo những thread/race không kiểm soát** hoặc ghi bytecode đồng thời vào thư mục một cách nguy hiểm.

Một lần `defineClass` thành công ràng buộc lớp với defining loader. Định nghĩa lại **cùng tên trong cùng loader** có thể gây `LinkageError`, kể cả bạn đã sửa bytes trên ổ đĩa. Muốn tải version mới trong một class namespace độc lập, thường tạo **loader mới** và quản lý vòng đời các đối tượng/class cũ. Việc sửa file `.class` tại chỗ không khiến class đã được nạp tự cập nhật.

Cần phân biệt:

- `findLoadedClass` là truy vấn JVM về class loader này đã được ghi nhận nạp class theo tên hay chưa; không phải một `Map` bạn được phép sửa trực tiếp.
- `defineClass` đưa bytecode thành class runtime và có thể ném `LinkageError`/`ClassFormatError` nếu không hợp lệ.
- `resolveClass` là bước linking theo yêu cầu của overload có `resolve=true`; không phải lệnh chạy static initializer.
- `loadClass(name)` tương đương overload `resolve=false`, nhưng JVM vẫn có quyền link các class vào thời điểm hợp lệ theo đặc tả.

## 7. Exception handling trong findClass

Với loader đọc file, câu hỏi khó là “khi không tìm được hoặc bytecode lỗi, nên ném gì?”:

| Tình huống | Kết quả hợp lý | Tại sao? |
| --- | --- | --- |
| Không có `plugin/Missing.class` | `ClassNotFoundException(name)` | Loader không thể tìm lớp theo binary name. |
| Đọc file gặp `NoSuchFileException` | Bọc thành `ClassNotFoundException(name, cause)` | Giữ nguyên nhân filesystem. |
| File tồn tại nhưng không đọc được vì lỗi I/O | `ClassNotFoundException(..., cause)` trong loader file minh họa | Caller biết lỗi tra cứu kèm nguyên nhân gốc; production có thể cần chính sách rõ hơn. |
| Bytecode hỏng | `ClassFormatError` | Không biến nhầm thành thiếu file. |
| Tên được truyền không khớp tên định nghĩa trong bytes | `NoClassDefFoundError` có thể phát sinh từ `defineClass` | “Wrong name” là lỗi khác với tìm không thấy file. |
| Cố define lại tên đã có trong cùng loader | `LinkageError` | Trùng định nghĩa class. |
| Cố định nghĩa tên bắt đầu `java.` từ loader ứng dụng | `SecurityException` | JVM bảo vệ namespace nền tảng theo hợp đồng `defineClass`. |
| Lớp có dependency khác bị thiếu khi được resolve | Có thể `NoClassDefFoundError` tại thời điểm JVM cần lớp đó | Không nhất thiết xảy ra ngay khi `findClass` trả về. |
| Static initializer của plugin ném lỗi | Có thể `ExceptionInInitializerError` khi class được initialize | Đây không phải nhiệm vụ `findClass`. |

Đừng ghi đè tất cả lỗi bằng `catch (Throwable e) { throw new ClassNotFoundException(name); }`. Cách đó làm mất cause và che giấu lỗi runtime quan trọng. Trong ví dụ, chỉ các lỗi **I/O** khi đọc bytes được bọc; lỗi Java linkage/format do JVM xác định được giữ nguyên.

## 8. Nếu muốn nạp resource thì sao?

`findClass(String)` **chỉ xử lý class**. Nó không tự khiến loader tìm được resource khác như `plugin/config.yml`:

~~~java
Class<?> pluginType = loader.loadClass("plugin.Greeter");
URL resource = loader.getResource("plugin/config.yml");
~~~

Đoạn trên là minh họa, giả định có loader và resource. `getResource` mặc định cũng có cơ chế parent-first riêng; để tìm resource từ thư mục plugin, bạn có thể cần override `findResource(String name)` hoặc chọn loader có sẵn như `URLClassLoader`. Phân biệt rõ:

- `findClass("plugin.Greeter")`: binary name bằng dấu `.`, nhận `Class<?>`.
- `findResource("plugin/config.yml")`: resource name bằng dấu `/`, thường trả `URL` hoặc `null`.
- `findResources(String name)`: có thể trả nhiều URL phù hợp khi cần nhiều resource cùng tên, tùy implementation.

Chỉ override `findClass` không tạo cơ chế resource search tùy biến tự động; tuy nhiên lớp Java được nạp vẫn có thể lấy những resource do parent cung cấp theo khả năng của loader. Khi nhiệm vụ thực sự là tải cả class lẫn resource từ một **JAR hoặc thư mục**, `URLClassLoader` thường ít code và ít rủi ro hơn việc tự viết loader file.

## 9. Khi nào thực sự cần override loadClass?

Lý do hợp lý là **muốn thay đổi chính sách tìm lớp**, chứ không chỉ thay nguồn bytecode. Ví dụ:

- **Child-first** cho một namespace plugin nhất định: tìm plugin version trước parent, nhưng tiếp tục dùng parent cho JDK/shared API.
- **Reverse delegation hoặc multi-parent** theo thiết kế container/module phức tạp, nơi parent-first đơn giản không đủ.
- **Chặn/lọc các tên binary name** theo quy tắc hệ thống, nhưng vẫn phải xem xét hậu quả với liên kết kiểu và các API nền tảng.
- **Tùy biến nạp song song/đồng bộ** khi có kiến trúc loader thực sự đặc biệt và đội ngũ hiểu sâu hợp đồng của `ClassLoader`.

Khi đó, phải thiết kế đủ thuật toán: `synchronized(getClassLoadingLock(name))` hoặc chiến lược khóa tương đương, `findLoadedClass(name)`, thứ tự lookup, fallback parent, `resolveClass` nếu cần, class identity, shared interfaces, failure handling và deadlock avoidance. Class loader có quan hệ ủy quyền **không phân cấp thuần túy** cần đặc biệt cân nhắc **parallel capable** theo tài liệu.

Một chính sách child-first **không có nghĩa tự động** mọi parent nhìn được dependency của child. Dùng loader tường minh hoặc SPI/interface chung để giao tiếp giữa các tầng. Đừng vô tình override `loadClass` chỉ vì bạn muốn đọc bytes từ một thư mục mới: đó chính xác là công việc của `findClass`.

## 10. Các lỗi dễ mắc và cách khắc phục

**Lỗi 1: Override cả loadClass lẫn findClass dù chỉ thay thư mục nguồn.** Không cần. Đưa logic đọc bytes vào `findClass`; giữ delegation/caching đồng bộ mặc định.

**Lỗi 2: Trong findClass gọi super.findClass(name) và kỳ vọng parent search.** `super.findClass` mặc định ném `ClassNotFoundException`, không phải thuật toán parent lookup. Parent đã được thử bởi `loadClass` trước khi override `findClass` được gọi.

**Lỗi 3: Truyền đường dẫn `plugin/Greeter.class` cho loadClass.** API cần binary name `plugin.Greeter`. Đổi tên thành đường dẫn file **bên trong findClass**, không ở API caller.

**Lỗi 4: Class plugin vô tình nằm trên application classpath.** Parent-first sẽ tìm được plugin trước; `findClass` của child không chạy. Test custom loader nên để plugin trong thư mục riêng và chỉ truyền classpath host khi chạy.

**Lỗi 5: Đọc class bytes rồi gọi defineClass với sai tên.** Có thể gây `NoClassDefFoundError` dạng wrong name. Tên binary phải khớp `this_class` bên trong class file.

**Lỗi 6: Đọc toàn bộ filesystem không giới hạn.** `Files.readAllBytes` đơn giản nhưng không phù hợp với nguồn file quá lớn hoặc không tin cậy. Giới hạn kích thước, kiểm soát symlink và chỉ dùng bytecode từ nguồn đáng tin cậy.

**Lỗi 7: Tưởng đóng file hoặc xóa tệp `.class` làm JVM unload lớp.** Một class đã được định nghĩa có vòng đời riêng; khi cần cập nhật, tạo loader mới và quản lý tham chiếu cũ.

**Lỗi 8: Kỳ vọng override findClass tự xử lý tài nguyên và native library.** Các hook liên quan là `findResource/findResources` và `findLibrary`; chọn API đúng nhu cầu.

**Lỗi 9: Biên dịch host dựa trên class plugin rồi bỏ classpath runtime mà không nghĩ về resolution.** Lớp do parent định nghĩa có thể không tìm thấy dependency chỉ thuộc loader con. Dùng reflection hoặc interface chung được parent định nghĩa để thiết kế ranh giới plugin.

**Lỗi 10: Bắt `Exception` hay `Throwable` rồi báo mọi lỗi là ClassNotFoundException.** Hãy giữ các lỗi linkage/format/security đúng loại và cause để chẩn đoán.

## 11. Checklist ra quyết định

Trước khi code custom loader, hãy trả lời tuần tự:

1. Tôi chỉ cần **đổi nguồn tìm bytes**, hay phải **đổi thứ tự ủy quyền**? Nếu chỉ đổi nguồn, override `findClass`.
2. Root class file đã đúng chưa? `plugin.Greeter` nằm ở `root/plugin/Greeter.class`, không phải `root/Greeter.class`.
3. Parent nào cần chia sẻ JDK và API chung? Có lớp cùng tên trên classpath parent không?
4. Caller gọi `loadClass(binaryName)`, không gọi thẳng `findClass` để bỏ delegation?
5. `findClass` có ánh xạ tên sang path đúng, kiểm soát file nguồn và trả `Class<?>` từ `defineClass`?
6. Lỗi file vắng mặt/I/O có giữ `ClassNotFoundException` cùng nguyên nhân? Linkage/format có được giữ đúng loại?
7. Các lời gọi nhiều luồng có dùng nguyên thuật toán đồng bộ của superclass? Có nguy cơ tự định nghĩa trùng?
8. Loader có cần resource/JAR riêng không? Có nên dùng `URLClassLoader` thay vì tự viết `findClass`?
9. Có cần class mới ở loader mới cho hot reload không? Class đã load không thể đơn giản “define lại” vào cùng loader.
10. Đã kiểm tra `getClassLoader()`, stdout, classpath compile/runtime và lỗi `ClassNotFoundException` của một class không tồn tại chưa?

### Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `findClass("plugin.Greeter")` không in log khi parent đã có `plugin.Greeter`?

**Đáp:** Vì `loadClass` theo parent-first hỏi parent trước. Nếu parent trả một lớp, nó không gọi `findClass` child.

**Câu 2.** Tại sao sau hai lần `loader.loadClass("plugin.Greeter")` chỉ thấy một log `findClass`?

**Đáp:** Loader/JVM đã ghi nhận cùng lớp; `findLoadedClass` giúp lời gọi sau trả `Class<?>` có sẵn, không define lại.

**Câu 3.** Muốn tải hai version có cùng binary name trong hai plugin độc lập, có nên define cả hai trong cùng loader?

**Đáp:** Không. Cần **hai defining loader riêng** và thiết kế API giao tiếp phù hợp, tránh cast nhầm qua hai namespace.

**Câu 4.** Muốn thêm class từ JAR nhưng không cần policy đặc biệt, bắt buộc tự viết findClass từ `JarFile` không?

**Đáp:** Không. `URLClassLoader` đã hỗ trợ thư mục và JAR; custom `findClass` thích hợp khi cần kiểm soát nguồn bytecode đặc biệt, còn parent-first vẫn được giữ.

## Tài liệu chuẩn

- [Java SE 21: ClassLoader.loadClass(String, boolean)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#loadClass(java.lang.String,boolean)): thuật toán mặc định và khuyến nghị override `findClass`.
- [Java SE 21: ClassLoader.findClass(String)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#findClass(java.lang.String)): contract tìm class khi parent không tìm được.
- [Java SE 21: ClassLoader.defineClass](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#defineClass(java.lang.String,byte%5B%5D,int,int)): định nghĩa lớp từ bytes, tên nhị phân và lỗi.
- [Java SE 21: URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): giải pháp có sẵn cho thư mục/JAR.
- [JVMS Java SE 21 §5.3: Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): defining loader, initiating loader và danh tính lớp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-004">Đọc và kiểm tra bytecode từ thư mục, bộ nhớ hoặc nguồn riêng</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc và kiểm tra bytecode từ thư mục, bộ nhớ hoặc nguồn riêng trong Java ClassLoader

## 1. Mục tiêu và bài toán

Java có thể nạp lớp không chỉ từ JAR và classpath thông thường mà còn từ thư mục cấu hình riêng, một mảng byte trong bộ nhớ hoặc một kho bytecode do ứng dụng quản lý. Khả năng này cho phép triển khai plugin, sinh mã động, cô lập phiên bản thư viện hoặc các cơ chế triển khai tùy chỉnh.

Điểm mở rộng quen thuộc là `ClassLoader.findClass(String name)`. Ứng dụng con override phương thức này để **tìm bytecode**, kiểm tra đầu vào cần thiết, rồi dùng `defineClass(...)` để tạo định nghĩa runtime của lớp.

Nhưng thao tác “đọc được mảng byte và gọi `defineClass`” chưa đủ để thiết kế một loader an toàn. Cần quan tâm đến:

- **Tên lớp (binary name):** phải trùng định nghĩa thực sự trong class file.
- **Nguồn byte:** thư mục đúng root, kho trong bộ nhớ hoặc nguồn riêng được kiểm soát.
- **Dữ liệu hợp lệ:** định dạng class file, phiên bản bytecode, cấu trúc, mức độ tin cậy và giới hạn kích thước.
- **Delegation:** loader cha có thể đã cung cấp class trước khi `findClass` được gọi.
- **Danh tính kiểu:** hai loader định nghĩa cùng binary name tạo thành hai runtime types khác nhau.
- **Lỗi và concurrency:** phải báo lỗi tìm lớp đúng nghĩa, tránh định nghĩa trùng khi nhiều thread yêu cầu cùng class.
- **Vòng đời:** resource lookup, rò rỉ loader, cache bytecode và tải lại plugin phải được thiết kế riêng.

Bài này phân biệt rõ **đọc bytecode từ nguồn riêng** với **kiểm chứng bytecode** và **định nghĩa một class trong JVM**.

## 2. Ba bước riêng biệt: tìm, kiểm tra, định nghĩa

Với tên `lesson.plugin.Greeting`, một loader tùy chỉnh thường thực hiện:

```text
request: loader.loadClass("lesson.plugin.Greeting")
       |
       v
ClassLoader.loadClass:
  (1) đã được ghi nhận nạp? -> trả Class cũ
  (2) parent có lớp?        -> trả Class parent
  (3) chưa có               -> findClass(binaryName)
                                   |
                            lấy bytecode từ nguồn
                                   |
                            kiểm tra sơ bộ/policy
                                   |
                            defineClass(name, bytes, ...)
                                   |
                            JVM kiểm tra, tạo định nghĩa
                                   |
                            trả Class<?> runtime
```

**Vai trò API:**

| API | Trách nhiệm | Điều dễ nhầm |
| --- | --- | --- |
| `loadClass(String)` | Đầu vào tìm/nạp lớp, mặc định theo parent-first delegation | Không có nghĩa loader được gọi là defining loader |
| `findLoadedClass(String)` | Kiểm tra lớp JVM đã ghi nhận nạp với initiating loader này | Không phải một cache bytecode tùy ý do ứng dụng quản lý |
| `findClass(String)` | Điểm mở rộng tìm/định nghĩa lớp cục bộ | Không tự động đọc thư mục/JAR nếu không có implementation |
| `defineClass(String, byte[], int, int)` | Tạo định nghĩa runtime bằng bytecode do loader cung cấp | Không chỉ đơn giản “parse header”; JVM còn áp dụng các quy tắc class/linking |
| `resolveClass(Class<?>)` | Liên quan linking/resolution của lớp đã định nghĩa | Không đồng nghĩa chạy `static initializer` |
| `Class.forName(name, initialize, loader)` | Tra kiểu và chọn có chủ động khởi tạo hay không | Có thể bị parent delegation ảnh hưởng như `loadClass` |

Khi chỉ cần thay **nguồn bytecode**, thường nên override `findClass` và giữ thuật toán `loadClass` mặc định. Nếu override `loadClass` để áp dụng child-first, bạn phải tự giữ quy tắc đồng bộ, lớp đã nạp, tương thích class loader và namespace bắt buộc.

## 3. Tên nhị phân và ánh xạ tới thư mục

Giả sử bytecode được biên dịch từ:

```java
package lesson.plugin;

public final class Greeting {
    public String message() {
        return "hello";
    }
}
```

Đây là mã nguồn của một lớp ví dụ, không phải một chương trình có `main` độc lập. Tên nhị phân của nó là `lesson.plugin.Greeting`. Với một **classpath root** là thư mục `plugins/classes`, class file thường nằm tại:

```text
plugins/classes/lesson/plugin/Greeting.class
```

Do đó cách ánh xạ quen thuộc là:

```java
Path classFile = root.resolve(
        binaryName.replace('.', '/') + ".class");
```

Đây là snippet, giả định `root` và `binaryName` tồn tại.

**Không được nhầm:**

- `lesson.plugin.Greeting` là **binary name**, còn `lesson/plugin/Greeting.class` là đường dẫn tương đối trong một root.
- Class lồng nhau có dấu `$` trong tên nhị phân, ví dụ `Outer$Nested`; không đổi thành `Outer.Nested` khi truyền cho `loadClass`.
- Một JAR dùng dấu `/` trong entry names, bất kể dấu phân tách đường dẫn của hệ điều hành.
- Parent có thể đã định nghĩa `lesson.plugin.Greeting`; việc file riêng tồn tại không bảo đảm `findClass` của child sẽ chạy.

### Thư mục là nguồn dữ liệu, không phải lớp bảo mật

Nếu tên class có thể đến từ bên ngoài, không nên nối thẳng nó vào đường dẫn mà bỏ kiểm tra. Cần giới hạn namespace, chuẩn hóa đường dẫn, kiểm tra root, xử lý symbolic link và quyền truy cập phù hợp. Đường dẫn hợp lệ về mặt cú pháp vẫn có thể dẫn tới nội dung do đối tượng không tin cậy kiểm soát.

Nếu ứng dụng chỉ tải class trong `lesson.plugin.*`, chính sách allowlist package giúp tránh vô ý đọc bytecode của vùng API khác. Tuy nhiên, allowlist tên **không** chứng minh bytecode an toàn.

## 4. Bytecode từ bộ nhớ: không cần file JAR lúc định nghĩa

Một nguồn khác là `Map<String, byte[]>` chứa class file bytes:

```text
"lesson.plugin.Greeting" -> [0xCA, 0xFE, 0xBA, 0xBE, ...]
"lesson.plugin.Helper"   -> [0xCA, 0xFE, 0xBA, 0xBE, ...]
```

Đây là mô tả cấu trúc dữ liệu. Một `MemoryClassLoader` có thể lấy `byte[]` theo binary name rồi gọi `defineClass`; JVM **không yêu cầu** class phải xuất phát từ file trên ổ đĩa.

Những lựa chọn triển khai đáng chú ý:

- **Copy mảng byte:** nếu bên ngoài còn giữ mảng và thay đổi nó trong lúc loader sử dụng, kết quả có thể khó dự đoán. Copy mảng khi nhận vào loader và tránh cho bên ngoài sửa bản đang sử dụng.
- **Cache định nghĩa, không định nghĩa lại:** `findLoadedClass` và thuật toán `loadClass` mặc định giúp tái sử dụng `Class<?>` đã được ghi nhận.
- **Bản đồ đầy đủ phụ thuộc:** bytecode của `Greeting` có thể yêu cầu `Helper` hoặc interface cha; nguồn loader phải có cách tìm chúng hoặc nhờ parent.
- **Giới hạn bộ nhớ:** lưu nhiều bản class bytes, plus runtime metadata, có thể làm tăng bộ nhớ; sau khi lớp được định nghĩa, không nhất thiết cần giữ mọi `byte[]` gốc mãi mãi.
- **Danh tính lớp:** bytecode giống hệt nhưng nạp bằng hai defining loader khác nhau tạo hai loại runtime khác nhau.

Bytecode từ network/database không tự nhiên “đáng tin” hơn bytecode từ bộ nhớ. Với nguồn riêng, phải thiết kế thêm xác thực, quản trị phiên bản, cache, timeout, checksum/signature từ nguồn đáng tin cậy và rollback khi nguồn không khả dụng.

## 5. Những mức “kiểm tra bytecode” cần phân biệt

Có ba tầng không được đánh đồng.

### 5.1. Kiểm tra trước khi gọi defineClass

Ứng dụng có thể kiểm tra:

1. **Tên/namespace được cho phép:** ví dụ chỉ chấp nhận `lesson.plugin.*`; không cố định nghĩa `java.*`.
2. **Nguồn:** root đã cấu hình, kho bytecode nội bộ, hoặc artifact ID nằm trong allowlist.
3. **Kích thước:** không đọc file quá lớn vô điều kiện; cân nhắc đọc stream có giới hạn thay vì chỉ dựa vào kích thước file tại một thời điểm.
4. **Class-file magic:** bốn byte đầu của class file là `CA FE BA BE`. Điều này **chỉ là kiểm tra sơ bộ**, không chứng minh class hợp lệ.
5. **Phiên bản class file:** major version phải được runtime hỗ trợ; không chỉ dựa trên phần mở rộng `.class`.
6. **Integrity/authenticity:** kiểm tra SHA-256 so với digest được phát hành qua kênh đáng tin hoặc xác minh chữ ký artifact thích hợp. Không có ý nghĩa bảo mật nếu “expected hash” được lấy từ chính nguồn không tin cậy cùng lúc với bytecode.
7. **Policy bổ sung:** package sealing, signer/certificate, protection domain và cách cho phép plugin kết nối API host.

Kiểm tra tên **bên ngoài** không thay thế việc JVM đối chiếu tên nhị phân thực trong class file.

### 5.2. JVM định dạng và verification

Khi gọi `defineClass(name, bytes, offset, length)`, JVM áp dụng những kiểm tra cần thiết cho định nghĩa class. Tùy lỗi, thời điểm và quy tắc JVM, có thể gặp:

| Lỗi | Ý nghĩa điển hình |
| --- | --- |
| `ClassFormatError` | Cấu trúc class file không hợp lệ |
| `UnsupportedClassVersionError` | Class file dùng phiên bản bytecode JVM hiện tại không hỗ trợ |
| `NoClassDefFoundError` do wrong name | Tên yêu cầu không trùng với tên định nghĩa trong class file |
| `VerifyError` | Bytecode không đáp ứng điều kiện verifier |
| `SecurityException` | Vi phạm hạn chế định nghĩa, namespace, package hoặc nguồn/certificate |
| `LinkageError` do duplicate definition | Cố định nghĩa lại cùng binary name trong một defining loader |
| `NoClassDefFoundError` do thiếu phụ thuộc | Superclass/superinterface hoặc phụ thuộc cần thiết không thể nạp khi JVM cần |
| `ExceptionInInitializerError` | Lỗi **initialization** khi thực thi mã tĩnh, không đồng nghĩa `defineClass` hỏng |

Một class có thể **định nghĩa thành công** mà sau đó mới gặp lỗi resolution của class phụ thuộc hoặc lỗi initialization khi active use. Đừng ép kết luận “defineClass hoàn tất = toàn bộ graph bytecode đều đã được kiểm chứng, liên kết và khởi tạo”.

### 5.3. Không coi JVM verifier là sandbox cho code không tin cậy

JVM bytecode verifier đảm bảo nhiều ràng buộc an toàn kiểu/máy ảo, nhưng **không** ngăn một class hợp lệ gọi API có quyền của tiến trình. Loader tùy chỉnh tự nó không cô lập network, filesystem, process execution hoặc native code. Với plugin không tin cậy, cần mô hình cách ly mạnh hơn (ví dụ một tiến trình/container được quản lý với quyền hạn được giảm), chứ không dựa vào `ClassLoader` như hàng rào bảo mật.

## 6. Ví dụ Java 17 chạy được: cùng bytecode từ thư mục và Map trong bộ nhớ

Thí nghiệm này tự sinh và biên dịch một class `lesson.plugin.Greeting` bằng `JavaCompiler` của JDK, rồi tải **cùng bytecode** theo hai cách:

1. `DirectoryLoader` đọc file `.class` từ thư mục gốc.
2. `MemoryLoader` đọc `byte[]` từ một `Map`.
3. Cả hai loader có parent là platform loader, để parent không nạp class plugin từ application classpath.
4. JVM tạo hai định nghĩa runtime riêng dù cùng binary name.
5. Chương trình kiểm tra tìm class đã được ghi nhận, kiểm tra bootstrap class và trường hợp class không tồn tại.

**Lưu toàn bộ chương trình** vào `BytecodeSourceDemo.java`:

```java
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class BytecodeSourceDemo {
    private static final String TYPE = "lesson.plugin.Greeting";
    private static final int MAX_BYTES = 1_000_000;

    private static void checkBytes(String name, byte[] bytes)
            throws ClassNotFoundException {
        if (!TYPE.equals(name) || bytes == null
                || bytes.length < 8 || bytes.length > MAX_BYTES) {
            throw new ClassNotFoundException(name);
        }
        if ((bytes[0] & 0xFF) != 0xCA
                || (bytes[1] & 0xFF) != 0xFE
                || (bytes[2] & 0xFF) != 0xBA
                || (bytes[3] & 0xFF) != 0xBE) {
            throw new ClassNotFoundException(
                    "Not a class-file header: " + name);
        }
    }

    static final class DirectoryLoader extends ClassLoader {
        private final Path root;

        DirectoryLoader(Path root, ClassLoader parent) {
            super(parent);
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!TYPE.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            Path file = root.resolve(
                    name.replace('.', '/') + ".class").normalize();
            if (!file.startsWith(root)) {
                throw new ClassNotFoundException(name);
            }

            byte[] bytes;
            try {
                if (!Files.isRegularFile(file)
                        || Files.size(file) > MAX_BYTES) {
                    throw new ClassNotFoundException(name);
                }
                bytes = Files.readAllBytes(file);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }

            checkBytes(name, bytes);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    static final class MemoryLoader extends ClassLoader {
        private final Map<String, byte[]> source;

        MemoryLoader(Map<String, byte[]> source, ClassLoader parent) {
            super(parent);
            this.source = new HashMap<>();
            source.forEach((name, bytes) ->
                    this.source.put(name, bytes.clone()));
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            byte[] stored = source.get(name);
            if (stored == null) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = stored.clone();
            checkBytes(name, bytes);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static String invokeGreeting(Class<?> type)
            throws Exception {
        Object instance = type.getDeclaredConstructor().newInstance();
        Method method = type.getMethod("message");
        return (String) method.invoke(instance);
    }

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a full JDK");
        }

        Path temp = Files.createTempDirectory("bytecode-source-");
        Path source = temp.resolve(
                "src/lesson/plugin/Greeting.java");
        Path classes = temp.resolve("classes");
        Path bytecode = classes.resolve(
                "lesson/plugin/Greeting.class");

        try {
            Files.createDirectories(source.getParent());
            Files.createDirectories(classes);
            Files.writeString(source, """
                    package lesson.plugin;
                    public final class Greeting {
                        public String message() {
                            return "hello";
                        }
                    }
                    """);

            int exit = compiler.run(null, null, null,
                    "--release", "17",
                    "-d", classes.toString(), source.toString());
            if (exit != 0) {
                throw new IllegalStateException("javac failed");
            }

            byte[] originalBytes = Files.readAllBytes(bytecode);
            ClassLoader parent = ClassLoader.getPlatformClassLoader();

            DirectoryLoader directory =
                    new DirectoryLoader(classes, parent);
            MemoryLoader memory = new MemoryLoader(
                    Map.of(TYPE, originalBytes), parent);

            Class<?> fromDirectory = directory.loadClass(TYPE);
            Class<?> fromMemory = memory.loadClass(TYPE);

            System.out.println("directory="
                    + invokeGreeting(fromDirectory));
            System.out.println("memory="
                    + invokeGreeting(fromMemory));
            System.out.println("sameName="
                    + fromDirectory.getName().equals(
                            fromMemory.getName()));
            System.out.println("sameClass="
                    + (fromDirectory == fromMemory));
            System.out.println("directoryDefining="
                    + (fromDirectory.getClassLoader() == directory));
            System.out.println("memoryDefining="
                    + (fromMemory.getClassLoader() == memory));
            System.out.println("directoryCached="
                    + (fromDirectory == directory.loadClass(TYPE)));
            System.out.println("stringBootstrap="
                    + (memory.loadClass("java.lang.String")
                            .getClassLoader() == null));

            try {
                memory.loadClass("lesson.plugin.Missing");
            } catch (ClassNotFoundException exception) {
                System.out.println("missing=ClassNotFoundException");
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(bytecode);
            Files.deleteIfExists(source.getParent());
            Files.deleteIfExists(
                    source.getParent().getParent());
            Files.deleteIfExists(temp.resolve("src"));
            Files.deleteIfExists(bytecode.getParent());
            Files.deleteIfExists(
                    bytecode.getParent().getParent());
            Files.deleteIfExists(classes);
            Files.deleteIfExists(temp);
        }
    }
}
```

Biên dịch và chạy với **JDK 17 trở lên** (không chỉ một runtime image không có compiler):

```bash
javac BytecodeSourceDemo.java
java BytecodeSourceDemo
```

**Stdout mong đợi khi compiler và hệ thống file hoạt động bình thường:**

```text
directory=hello
memory=hello
sameName=true
sameClass=false
directoryDefining=true
memoryDefining=true
directoryCached=true
stringBootstrap=true
missing=ClassNotFoundException
```

### Giải thích kết quả

- Hai loader tìm được **cùng bytecode** do `javac` tạo, nên gọi `message()` đều trả `hello`.
- `sameName=true` vì `Class.getName()` của cả hai là `lesson.plugin.Greeting`.
- `sameClass=false` vì hai loader là **hai defining loaders khác nhau**; JVM không xem chúng là một class runtime.
- `directoryDefining=true` và `memoryDefining=true` chứng tỏ class được định nghĩa bởi loader nhận trách nhiệm tìm bytecode, không phải platform parent.
- `directoryCached=true`: với cùng một loader, lần yêu cầu thứ hai nhận lại định nghĩa đã nạp, không gọi `defineClass` thêm lần nữa.
- `stringBootstrap=true`: `java.lang.String` vẫn được bootstrap loader định nghĩa theo delegation.
- Khi `MemoryLoader` không có byte array cho `lesson.plugin.Missing`, `findClass` ném `ClassNotFoundException`. Đây là hợp đồng hợp lý khi nguồn loader không chứa class được yêu cầu.

### Giới hạn chủ đích của thí nghiệm

- Chỉ cho phép **một binary name** `lesson.plugin.Greeting`: giúp tránh vô tình đọc/định nghĩa class ngoài phạm vi bài học. Loader thực tế phải có policy rộng hơn và hợp lệ với các lớp phụ thuộc.
- Kiểm tra `CA FE BA BE` là **sơ bộ**; JVM mới là bên kiểm tra class file theo đặc tả. Không tuyên bố chương trình này đã xác minh chữ ký artifact hay sandbox code.
- `Files.size` trước `Files.readAllBytes` là một bước giới hạn thuận tiện; nếu file có thể bị thay đồng thời, sẽ có rủi ro *time-of-check/time-of-use*. Nguồn không tin cậy cần đọc stream có giới hạn và chính sách filesystem mạnh hơn.
- Các đường dẫn chỉ được tạo từ tên cố định, vì vậy ví dụ không phô diễn đầy đủ cách chống path traversal, symlink escape hoặc arbitrary namespace.
- Ví dụ dùng JavaCompiler của JDK để tránh cần JAR bên ngoài; khi nạp bytecode từ database hoặc HTTP, chỉ thay **nguồn bytes** và giữ quy tắc validate/define tương ứng.

## 7. Kiểm tra dữ liệu sai: làm gì với bytecode hỏng?

Phân biệt hai nhóm lỗi:

**Nhóm 1 — Loader không tìm được nguồn hợp lệ:** tên nằm ngoài namespace cho phép, file không tồn tại, không thể đọc, byte array không có trong kho. `findClass` nên báo `ClassNotFoundException`, có thể kèm `IOException` làm cause.

**Nhóm 2 — Đã lấy được bytes, nhưng bytes không phải class file hợp lệ:** `defineClass`/JVM có thể ném `ClassFormatError`, `UnsupportedClassVersionError`, `VerifyError` hoặc các `LinkageError` phù hợp. Không nên bắt hết mọi `Throwable` rồi biến thành `ClassNotFoundException` vì sẽ che giấu nguyên nhân kỹ thuật.

Ví dụ một mảng không phải class file:

```java
byte[] invalid = new byte[]{1, 2, 3, 4};
```

Đây là đoạn minh họa. Nếu đưa mảng này vào loader trên với key hợp lệ, kiểm tra `checkBytes` sẽ loại bỏ trước do độ dài nhỏ hơn 8. Nếu vượt qua kiểm tra magic nhưng vẫn có nội dung class file sai, JVM có thể từ chối tại `defineClass` hoặc giai đoạn linking tiếp theo. **Magic bytes không đủ để khẳng định bytecode an toàn hoặc đúng cấu trúc.**

### Nếu tên được yêu cầu khác tên trong class file

Giả sử bạn yêu cầu `lesson.plugin.Other` nhưng đưa vào bytes thực sự định nghĩa `lesson.plugin.Greeting`. Khi gọi `defineClass("lesson.plugin.Other", bytes, ...)`, JVM phải áp dụng ràng buộc tên; kết quả không phải việc âm thầm “đổi tên class” sang Other. Trong những cấu hình thông thường có thể báo `NoClassDefFoundError` với thông tin wrong name.

**Không nên cố sửa tên nhị phân bằng cách thay một vài byte trong class file.** Class file chứa constant pool, descriptors, signatures, bootstrap methods và nhiều metadata ràng buộc khác; nếu cần đổi tên thực sự phải dùng công cụ biến đổi bytecode phù hợp và kiểm tra lại tính nhất quán.

## 8. Nạp từ nguồn riêng: database, kho artifact, HTTP hoặc code sinh động

Có thể trừu tượng nguồn bytecode để `findClass` không phụ thuộc vào filesystem:

```java
@FunctionalInterface
interface BytecodeRepository {
    byte[] read(String binaryName) throws java.io.IOException;
}
```

Đây là snippet về interface nguồn dữ liệu, không phải một ứng dụng chạy riêng. Tùy triển khai, phương thức `read` có thể nhận byte array từ:

- Một `Map<String,byte[]>` được compiler/runtime sinh ra.
- Object storage hoặc database có ánh xạ chính xác từ binary name sang blob bytes/version.
- Một artifact registry có metadata checksum/signature/nguồn phát hành.
- Bộ biên dịch động dùng `javax.tools.JavaCompiler` và `JavaFileManager` để thu bytes trực tiếp vào bộ nhớ.
- Một tầng cache nội bộ chứa lớp plugin đã được xác minh/cho phép.

**Tránh tải trực tiếp bytecode từ URL không được xác thực rồi execute trong JVM ứng dụng.** Với nguồn có độ tin cậy thấp, nên ưu tiên cấu trúc artifact ký số từ pipeline build tin cậy, xác minh nguồn, giới hạn truy cập và cân nhắc cô lập thực thi qua tiến trình riêng. SHA-256 chỉ chứng minh dữ liệu trùng với digest được **tin cậy từ trước**, không cho biết “code này không độc hại”.

### Cache là cache bytes, không phải “reload lớp” trong cùng loader

Kho nguồn có thể cập nhật bản `Greeting.class` theo version. Nhưng một loader đã định nghĩa `lesson.plugin.Greeting` thì không thể tự ý thay definition bằng cách đọc bytes bản mới và gọi `defineClass` lần nữa cùng tên. Lần nạp lặp với `loadClass` mặc định thường lấy class đã ghi nhận.

Nếu cần hot reload, thường phải **tạo loader mới** cho version mới, đổi đường routing của ứng dụng và giải phóng tham chiếu tới loader cũ. Bài toán migration state và đối tượng xuyên giữa các loader cần xử lý riêng; hai bản `Greeting` có thể không ép kiểu qua nhau.

## 9. Bytecode hợp lệ vẫn cần dependency và API chung

Một class mới được `defineClass` thành công vẫn có thể cần superclass, interface hay phương thức từ các thư viện **chưa được phân giải**. Loader phải tìm được các dependency ấy, trực tiếp hoặc qua parent. Chẳng hạn một implementation trong `lesson.plugin` khai báo `implements lesson.api.Plugin`: host và plugin chỉ trao đổi object dưới kiểu `Plugin` an toàn khi cả hai dùng **cùng định nghĩa `Class<?>`** của interface chia sẻ.

Trong hệ plugin, thường để API/SPI và những DTO dùng chung ở loader cha, còn class implementation ở loader plugin. Các thư viện cần cô lập phiên bản phải có phạm vi lookup riêng; parent-first mặc định sẽ chọn bản parent nếu parent đã tìm thấy cùng binary name. JVM vẫn áp đặt loading constraints khi các method signatures đi qua ranh giới loader.

**Cần phân biệt hai kiểm tra:** integrity/authenticity của **bytes nguồn** trước `defineClass`, và tính khả dụng/đồng nhất của **các kiểu liên quan** khi linking. Một checksum đúng không chứng minh graph dependency hợp lệ hay plugin có thể cast qua interface host.

## 10. Đồng thời nhiều luồng và định nghĩa lớp trùng

Khi nhiều thread đồng thời yêu cầu `loader.loadClass("lesson.plugin.Greeting")`, cơ chế `ClassLoader.loadClass` mặc định gồm đồng bộ trên khóa nạp phù hợp và kiểm tra `findLoadedClass` trước khi chọn `findClass`. Vì vậy, nếu chỉ override `findClass`, bạn thường tận dụng được quy tắc chống nạp trùng của phần lõi.

Nếu tự override `loadClass`, cần kiểm soát:

1. Chính sách parent/child rõ ràng.
2. Kiểm tra class đã được ghi nhận nạp trước khi định nghĩa mới.
3. Khóa nạp lớp phù hợp với `getClassLoadingLock(name)` và trạng thái parallel-capable nếu có.
4. Tránh tự gọi `loadClass(name)` từ `findClass(name)` cho cùng tên dẫn đến đệ quy.
5. Không giữ khóa ứng dụng dài qua network fetch hoặc gọi ngược sang các loader khác mà tạo vòng chờ.
6. Chính sách cache + timeout để nguồn remote không làm mọi thread chờ vô hạn.

Cùng một defining loader không được định nghĩa hai lần cùng binary name như hai lớp độc lập. Nếu có xung đột, JVM có thể báo `LinkageError`. **Không được catch lỗi này rồi trả một đối tượng Class tùy ý không đúng tên/loader** chỉ để “qua” lượt nạp.

### Loading khác initialization

`defineClass` và `loadClass` không phải API “thực thi code trong static block”. JVM có giai đoạn loading/linking/initialization riêng. Tạo instance hoặc gọi static method có thể gây initialization sau đó; loader do ứng dụng viết không nên dùng log trong `static {}` để suy ra mọi lần đọc bytecode.

## 11. Lỗi thường gặp và hướng chẩn đoán

| Hiện tượng | Câu hỏi cần kiểm tra |
| --- | --- |
| `ClassNotFoundException` | Loader được yêu cầu có đúng binary name/namespace? Root, repository key, quyền đọc và source có đúng? |
| `NoClassDefFoundError` | Thiếu superclass/interface, dependency khác, wrong name hoặc class từng initialization lỗi? |
| `ClassFormatError` | Bytes có phải class file cấu trúc hợp lệ? Nguồn có trả HTML/JSON thay vì `.class`? |
| `UnsupportedClassVersionError` | Java runtime có đủ mới để đọc major version của class file? |
| `VerifyError` | Bytecode có vi phạm các ràng buộc verifier? Đã bị biến đổi/sinh không hợp lệ? |
| `LinkageError` duplicate definition | Có hai thread/đường code gọi `defineClass` lại cùng tên trong cùng loader? |
| `ClassCastException` giữa lớp cùng tên | Hai defining loader có tạo hai runtime types khác nhau? |
| `SecurityException` | Có cố định nghĩa `java.*` hoặc vi phạm package certificate/protection constraint? |
| `NoSuchMethodError` | Runtime dependency có khác API mà bytecode plugin được compile với không? |
| `ExceptionInInitializerError` | Mã khởi tạo tĩnh lỗi sau active use, không phải lỗi đọc bytes? |
| Không tìm được resource `.properties` | Custom loader có override/thiết lập `findResource(s)` hoặc package/module resource lookup phù hợp? |
| Plugin cũ còn chiếm bộ nhớ | ClassLoader cũ có bị static registry, thread, TCCL, callback hoặc cache giữ lại không? |

**Lưu ý:** `ClassNotFoundException` (checked) và `NoClassDefFoundError` (Error) không được hoán đổi tùy tiện. Khi `findClass` không tìm thấy bytes, dùng `ClassNotFoundException`; khi JVM phát hiện class phụ thuộc không khả dụng, thông điệp runtime có thể là `NoClassDefFoundError`. Phải xem toàn bộ cause.

### Công cụ hữu ích

Với file `.class` hoặc JAR có thể truy cập:

```bash
javap -verbose -classpath plugins/classes lesson.plugin.Greeting
jar tf plugins/plugin.jar
java -Xlog:class+load=info -cp app/classes app.Main
```

Đây là các **lệnh minh họa**, phụ thuộc cách bố trí file và class runtime của dự án. `javap` cho biết thông tin constant pool, major version và cấu trúc class; `jar tf` liệt kê các entry của JAR; log class-load hỗ trợ xác định nhiều class được load từ nguồn nào. Với loader trong bộ nhớ, `CodeSource` có thể không thể hiện như URL file/JAR; điều đó không đồng nghĩa định nghĩa lớp sai.

Để biết defining loader của một class đã nạp:

```java
static void inspect(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("definingLoader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule());
}
```

Đây là phương thức hỗ trợ, cần được đặt vào một class ứng dụng rồi gọi; không phải chương trình có `main` độc lập. `getClassLoader() == null` là trường hợp lớp do bootstrap loader định nghĩa.

## 12. Resource lookup và giới hạn khi nạp từ memory/database

Một loader chỉ override `findClass` có thể nạp `Greeting.class` từ database, nhưng:

```java
loader.getResource("lesson/plugin/config.properties");
```

có thể trả `null`. Điều này không mâu thuẫn, bởi class loading và resource lookup là hai đường API khác nhau.

Nếu repository chứa cả resource, phải cân nhắc `findResource` và `findResources`, hoặc cung cấp một abstraction riêng cho plugin. Đặc biệt, `getResource` thường ưu tiên parent theo mặc định, còn `getResources` có thể trả nhiều URL. Việc trả URL không mở stream được sẽ khiến API tiêu thụ thất bại dù lookup tưởng như thành công.

Nếu class nằm trong named module, `ClassLoader.getResource` và `Module.getResourceAsStream` còn chịu những quy tắc về module visibility và package openness. Không tự giả định nguồn bytes trong bộ nhớ biến class thành named module nếu không có cấu hình module tương ứng.

## 13. Các hiểu lầm cần tránh

**“Đọc được bốn byte CA FE BA BE là class hợp lệ.”** Sai; đó chỉ là magic header. JVM verifier, class format rules và các constraint khác mới đánh giá sâu hơn.

**“Có byte[] thì tự nhiên class sẽ tồn tại.”** Sai; cần `defineClass` hoặc cơ chế tạo class phù hợp và phân giải đúng tên.

**“`defineClass` thành công tức bytecode an toàn để chạy.”** Sai; bytecode hợp lệ vẫn có thể thực thi hành vi mà tiến trình được phép, gồm truy cập dữ liệu và network.

**“Hai loader nạp cùng bytes sẽ trả cùng đối tượng Class.”** Sai; defining loader khác nhau tạo hai type runtime riêng.

**“Map bytecode thay đổi thì loader cũ tự thay class.”** Sai; một binary name đã được định nghĩa không tự bị thay trong cùng defining loader.

**“Khi không tìm thấy class cứ trả null từ findClass.”** Sai; `findClass` cần báo `ClassNotFoundException` khi không có class tương ứng, theo hợp đồng API.

**“Nếu class đã có trong parent thì findClass chắc chắn được gọi rồi.”** Sai; `loadClass` parent-first có thể trả từ parent trước khi tìm local.

**“Lọc dấu ../ là đủ ngăn mọi path traversal.”** Sai; cần xét symlink, canonical/real path, filesystem race và quyền truy cập nếu tên/path không tin cậy.

**“ClassLoader tùy chỉnh là một security sandbox.”** Sai; cần isolation/process policy phù hợp khi phải chạy code không tin cậy.

## 14. Bài tập tự kiểm tra

**Câu 1.** `DirectoryLoader` được gọi với `lesson.plugin.Greeting`. Loader cần ánh xạ tên này tới file nào nếu root là `plugins/classes`?

**Trả lời:** `plugins/classes/lesson/plugin/Greeting.class`, đồng thời phải xác minh path nằm trong root được phép.

**Câu 2.** Một mảng byte bắt đầu bằng `CA FE BA BE`. Đã đủ để khẳng định class hợp lệ và an toàn chưa?

**Trả lời:** Chưa. Magic chỉ là điều kiện sơ bộ; còn phiên bản, cấu trúc, verification, linkage, nguồn tin cậy và quyền thực thi.

**Câu 3.** Hai loader độc lập nhận cùng bytes với cùng binary name. Có thể cast instance của bản loader A sang class do loader B định nghĩa không?

**Trả lời:** Không chỉ nhờ cùng tên/bytes. Danh tính runtime phụ thuộc defining loader nên hai class khác nhau.

**Câu 4.** Đã định nghĩa `Greeting` trong một loader, repository cập nhật bytes mới. Gọi `loadClass` cùng tên có reload không?

**Trả lời:** Không. Loader tái sử dụng class đã nạp; cần một defining loader mới cho định nghĩa độc lập.

**Câu 5.** Bytecode được tải qua HTTP không có chữ ký nhưng magic header hợp lệ. Có nên chạy trong JVM chính không?

**Trả lời:** Không thể kết luận an toàn. Cần nguồn tin cậy, xác minh artifact theo kênh đáng tin cậy, giới hạn đặc quyền và có thể cô lập tiến trình.

**Câu 6.** `findClass` có bytecode nhưng class sau đó báo `NoSuchMethodError` khi gọi một thư viện. Vì sao?

**Trả lời:** Bytecode class được định nghĩa không bảo đảm các API runtime khác phiên bản compile-time ABI đều có method được tham chiếu.

**Câu 7.** Hai thread cùng yêu cầu một tên class trong cùng loader. Vì sao override chỉ `findClass` thường dễ an toàn hơn override cả `loadClass`?

**Trả lời:** Thuật toán `loadClass` mặc định đã phối hợp khóa nạp lớp, parent delegation và `findLoadedClass` để tránh định nghĩa trùng.

## 15. Checklist cho loader sử dụng trong sản phẩm

- [ ] Xác định namespace/package được phép và nguồn dữ liệu nào được tin cậy.
- [ ] Ánh xạ binary name sang path/key an toàn; xử lý symlink, path traversal và quyền đọc.
- [ ] Giới hạn kích thước và thời gian đọc; kiểm soát bộ nhớ khi nguồn trả `byte[]` lớn.
- [ ] Kiểm tra tính toàn vẹn/xác thực bằng expected digest hoặc chữ ký từ nguồn tin cậy.
- [ ] Giữ phần lỗi I/O trong `ClassNotFoundException` với cause, không che lỗi class format/linkage.
- [ ] Đảm bảo superclass/superinterface, API/SPI và phụ thuộc được cung cấp đúng loader.
- [ ] Tận dụng `loadClass` mặc định nếu chỉ cần thay cách tìm bytecode.
- [ ] Xử lý concurrency và tránh `defineClass` trùng binary name.
- [ ] Phân biệt caching bytes với lifecycle của runtime `Class`.
- [ ] Thiết kế resource lookup và cleanup cho plugin/redeploy.
- [ ] Không coi custom ClassLoader là sandbox code không tin cậy.
- [ ] Kiểm thử thiếu file, sai tên, bytes hỏng, sai JDK version, hai loader khác nhau và dependency conflict.

## 16. Kết luận và tài liệu chính thức

Một custom `ClassLoader` có thể đọc bytecode từ **thư mục, memory map hoặc nguồn riêng**. Nhưng thiết kế đúng cần tách **tìm bytecode** (`findClass`) khỏi **định nghĩa lớp** (`defineClass`), kiểm tra tên/source/kích thước/integrity trước khi giao cho JVM, để JVM thực hiện kiểm tra định dạng và các ràng buộc liên kết. Sau đó phải quản lý delegation, identity, phụ thuộc, lỗi và vòng đời loader một cách có chủ đích.

Tài liệu chuẩn:

- Java SE 17 `ClassLoader` API, đặc biệt `findClass`, `defineClass`, `loadClass` và `findLoadedClass`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- JVMS 17 §4, *The class File Format*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- JVMS 17 §5, *Loading, Linking, and Initializing*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- Java SE 17 `JavaCompiler` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/tools/JavaCompiler.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-005">Ánh xạ tên lớp tới nguồn dữ liệu có giới hạn rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Ánh xạ tên lớp tới nguồn dữ liệu có giới hạn rõ ràng trong custom ClassLoader

## 1. Bài toán cần giải quyết

Một custom `ClassLoader` có thể định nghĩa các lớp Java từ dữ liệu lấy ở thư mục, JAR, cơ sở dữ liệu, mạng hoặc một kho bytecode riêng. Tuy nhiên, từ **binary class name** như `demo.plugins.Greeting` đến **nguồn dữ liệu bytes** là một quyết định thiết kế quan trọng. Nếu biến mọi chuỗi tên lớp thành một đường dẫn hoặc URL tùy ý mà không giới hạn, loader dễ rơi vào tình huống nạp nhầm bytecode, truy cập ngoài vùng cho phép, phụ thuộc vào thứ tự tìm kiếm không rõ ràng hoặc tiêu thụ quá nhiều tài nguyên.

Mục tiêu của bài này là thiết kế **ánh xạ rõ ràng, có phạm vi (bounded mapping)**:

1. Xác định **những binary class names nào được chấp nhận**.
2. Xác định **nguồn bytes tương ứng với từng tên**.
3. Giới hạn **thư mục hoặc kho được phép đọc** và kiểm tra đường dẫn sau chuẩn hóa.
4. Giới hạn **kích thước dữ liệu, số lớp hoặc lượng công việc** theo yêu cầu vận hành.
5. Phân biệt **parent delegation** với **nguồn bytecode riêng** do loader này kiểm soát.
6. Giải thích cách trả `ClassNotFoundException` hoặc lỗi phù hợp thay vì tự đoán một class khác khi tên/nguồn không khớp.
7. Không lẫn lộn tính hợp lệ của bytecode, khả năng định nghĩa lớp và việc tin cậy/truy cập an toàn đối với mã được thực thi.

Ví dụ nguyên tắc:

~~~text
Binary name                Approved source
----------------------------------------------------------
demo.plugins.Greeting   ->  plugin-classes/demo/plugins/Greeting.class
demo.plugins.Parser     ->  [không có trong allowlist: từ chối]
java.lang.String        ->  bootstrap/platform qua delegation

Không bao giờ dùng một tên người dùng nhập để tự ý
chọn file ngoài trusted root hoặc URL bất kỳ.
~~~

**Phạm vi bảo vệ rất quan trọng:** ánh xạ được giới hạn ở đây bảo vệ **nguồn dữ liệu do `findClass` của custom loader tự đọc và định nghĩa**. Nó **không phải sandbox bảo mật để ngăn code plugin đọc file, mở kết nối mạng hay dùng mọi Java API**. Trong Java 17+, không nên dựa vào `SecurityManager` như biện pháp sandbox đáng tin cậy; Java Security Manager đã bị deprecated for removal trong Java 17 và không phải cơ chế cách ly code tùy ý cho thiết kế mới.

## 2. Tên lớp dùng ở API: binary name chứ không phải tên đường dẫn

`ClassLoader.loadClass(String name)` và `findClass(String name)` làm việc với **binary name** của một kiểu, chẳng hạn:

| Loại | Ví dụ |
| --- | --- |
| Lớp trong package | `com.example.Greeting` |
| Lớp ở unnamed package | `Greeting` |
| Lớp thành viên nested | `com.example.Outer$Inner` |
| Tên class file entry tương ứng | `com/example/Greeting.class` |
| Resource path tương ứng | `com/example/Greeting.class` |
| JVM internal name trong class file | `com/example/Greeting` |

Không được thay chuỗi binary name bằng một URL, `../secret` hoặc một path tùy ý. Tên nested class trong class file thường có dấu `$`, không phải dấu `.` của source syntax. Ví dụ `Outer.Inner` có thể là tên canonical/source, còn binary name là `Outer$Inner`.

Một quy ước **có thể** dùng là:

~~~java
String resource = binaryName.replace('.', '/') + ".class";
~~~

Đây là **đoạn minh họa**, không phải chính sách đủ an toàn khi dùng với đầu vào chưa được kiểm soát. Nó chuyển tên thành path, nhưng **chưa trả lời** tên nào hợp lệ, thư mục nào được phép, người gọi có quyền yêu cầu tên ấy không, file có nằm trong root không, bytes có đúng class file và giới hạn kích thước không.

Nếu yêu cầu rất hẹp, **explicit allowlist map** thường rõ ràng hơn việc chuyển mọi tên thành đường dẫn.

## 3. Hai cách ánh xạ phổ biến và khi chọn

### 3.1. Ánh xạ tường minh từng binary name

Ví dụ:

~~~java
Map<String, Path> approved = Map.of(
        "demo.plugins.Greeting",
        Path.of("demo", "plugins", "Greeting.class")
);
~~~

Điều này thể hiện rõ:

- Chỉ `demo.plugins.Greeting` được custom loader tìm trong kho riêng.
- Đường dẫn là **relative path** bên dưới trusted root, không phải đường dẫn tuyệt đối do người gọi quyết định.
- Một tên khác như `demo.plugins.Admin` sẽ không có ánh xạ.
- Việc tên nằm trong `demo.plugins` vẫn **chưa đủ** để được load nếu nó không có trong allowlist.

Ưu điểm: dễ review, audit, hash/checksum, version pinning. Nhược điểm: với nhiều lớp cần quản lý manifest lớn; nên sinh manifest từ quá trình build/đăng ký plugin có kiểm soát thay vì nhập thủ công hàng nghìn mục.

### 3.2. Ánh xạ theo prefix/package đã kiểm tra

Với kho lớn hơn, có thể chấp nhận tất cả tên khớp một **package boundary chính xác**, rồi chuyển theo quy ước đường dẫn, ví dụ `demo.plugins.*`. Nhưng cần kiểm tra cả:

- Binary name có cú pháp được chính sách cho phép.
- Tiền tố là `demo.plugins.` **bao gồm dấu chấm kết thúc**, tránh vô tình cho phép `demo.pluginsEvil.*`.
- Không chứa `/`, dấu gạch ngược, chuỗi path traversal hoặc tên giả mạo.
- Chuỗi sau prefix có phù hợp những dạng binary name cần hỗ trợ (including nested names `$`) không.
- Đường dẫn cuối cùng phải nằm trong root đã xác thực, sau khi xét symlinks và cách canonicalize của filesystem.

**Không đặt một regex đơn giản làm tiêu chuẩn toàn bộ tên Java hợp lệ** nếu công cụ cần hỗ trợ Unicode identifiers, generated class names, các dạng nested/anonymous hoặc định nghĩa đặc biệt. Chính sách chỉ cho phép một tập con là hợp lệ nếu được công bố rõ; đừng tuyên bố nó kiểm tra mọi binary name hợp lệ theo JVM spec.

Bài thực hành dưới đây dùng **explicit allowlist** để bài toán mapping rõ nhất.

## 4. Ba ranh giới cần định nghĩa tường minh

### 4.1. Ranh giới tên (namespace)

Cho phép những binary names hoặc package scopes đã đăng ký. Không nên cho custom loader tự định nghĩa các package nền tảng như `java.*`; JVM hạn chế việc định nghĩa `java.*` trong application class loaders, đồng thời việc cố nạp bản riêng của core API có thể gây lỗi nghiêm trọng về type identity.

Danh sách tên được phép có thể do config đáng tin cậy, build-generated manifest hoặc registry có xác thực cung cấp. **Đầu vào người dùng cuối không nên tự tạo quyền load một class bất kỳ**.

### 4.2. Ranh giới dữ liệu (source boundary)

Gốc thư mục hay kho dữ liệu phải được chọn từ cấu hình đáng tin cậy, không phải ghép tự do từ input. Với filesystem:

~~~text
trustedRoot = /srv/app/plugins/v1/classes/
requested = demo.plugins.Greeting
relative = demo/plugins/Greeting.class
candidate = trustedRoot/relative
~~~

Kiểm tra:

1. Relative path thực sự **không tuyệt đối**.
2. Candidate sau canonicalization/real-path resolution vẫn **ở trong** trusted root.
3. Nguồn là regular file được phép và kích thước không vượt giới hạn.
4. Nếu symlink được chấp nhận, **đích cuối cùng** của symlink vẫn ở trong root; nếu không cho phép symlink thì phải kiểm soát từng thành phần đường dẫn.
5. Không xem phép `String.startsWith("/srv/app/plugins")` là ranh giới path: `/srv/app/plugins-evil/...` có cùng tiền tố chuỗi nhưng nằm ngoài root. Dùng `Path.startsWith(otherPath)` sau khi chuẩn hóa phù hợp.

Trong môi trường filesystem do đối tượng không đáng tin có thể thay đổi đồng thời, việc gọi `toRealPath` rồi mới đọc file có thể gặp **TOCTOU race**. Kiểm tra path trước khi mở không phải bảo đảm chống kẻ tấn công có khả năng thay symlink/thư mục giữa hai bước. Với threat model như vậy, cần thiết kế kho immutable/permission-controlled hoặc dùng các primitive truy cập qua directory handle/`SecureDirectoryStream` khi provider hỗ trợ, cộng các kiểm soát ở cấp hệ điều hành.

### 4.3. Ranh giới tài nguyên (resource limits)

Một class file có thể quá lớn hoặc kho plugin có hàng triệu entry. Giới hạn cần gắn với nhu cầu:

- **Bytes tối đa mỗi class file**, có cap ở bước đọc, không chỉ `Files.size` trước đó.
- **Số lớp được phép** hoặc số class definitions theo manifest.
- **Thời gian I/O hoặc request timeout** nếu bytes đến từ mạng.
- **Số request đồng thời/caches** để tránh bùng nổ bộ nhớ.
- **Nguồn tin cậy/hash/chữ ký** khi plugin supply chain là thành phần quan trọng.

Một giới hạn byte không chứng minh bytecode an toàn; JVM verification cũng không phải sandbox quyền file/network. Một allowlist giới hạn **cái gì loader tự tìm**, chứ không thay thế cách ly process/container đối với code không tin cậy.

## 5. Ví dụ Java 17+ độc lập: loader chỉ đọc một class từ root cố định

Chúng ta tạo một host app và một plugin class tách biệt. Custom loader sau đây:

- Chỉ cho phép **binary name `demo.plugins.Greeting`** trong kho riêng.
- Đọc file tương ứng từ một root được truyền **một lần ở lúc tạo loader**.
- Dùng `toRealPath` và `Path.startsWith` để từ chối đích thoát ra ngoài root.
- Kiểm tra regular file, cap đọc **1 MiB**.
- Gọi `defineClass` với **chính binary name đã yêu cầu**, để JVM kiểm tra tên trong class file.
- Dùng platform loader làm parent, cho phép các Java runtime classes cần thiết được tìm qua delegation.
- Không tự tải class khi tên khác allowlist, dù có file mang tên khác trong root.

### 5.1. Cây thư mục

~~~text
bounded-loader-demo/
├── plugin-src/
│   └── demo/
│       └── plugins/
│           └── Greeting.java
└── host-src/
    └── HostDemo.java
~~~

Sau biên dịch, chúng ta có hai thư mục độc lập:

~~~text
bounded-loader-demo/
├── plugin-classes/
│   └── demo/
│       └── plugins/
│           └── Greeting.class
└── host-classes/
    ├── HostDemo.class
    └── HostDemo$BoundedLoader.class
~~~

**Quan trọng:** không đưa `plugin-classes` vào host application classpath. Nếu parent cũng thấy plugin class, parent-first delegation có thể trả về class do parent định nghĩa, khiến demo không còn chứng minh loader tự nạp bytecode riêng.

### 5.2. File plugin: plugin-src/demo/plugins/Greeting.java

~~~java
package demo.plugins;

public class Greeting {
    public String message() {
        return "hello from mapped class";
    }
}
~~~

Đây là file Java độc lập cho plugin. Constructor mặc định do javac cung cấp có quyền truy cập phù hợp với public class. Nó không tham chiếu host class nào.

### 5.3. File host: host-src/HostDemo.java

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class HostDemo {
    static final class BoundedLoader extends ClassLoader {
        private static final int MAX_BYTES = 1_048_576;

        private final Path trustedRoot;
        private final Map<String, Path> approved = Map.of(
                "demo.plugins.Greeting",
                Path.of("demo", "plugins", "Greeting.class")
        );

        BoundedLoader(Path root) throws IOException {
            super(ClassLoader.getPlatformClassLoader());
            this.trustedRoot = root.toRealPath();

            if (!Files.isDirectory(trustedRoot)) {
                throw new IOException("Not a directory: " + trustedRoot);
            }
        }

        @Override
        protected Class<?> findClass(String binaryName)
                throws ClassNotFoundException {
            Path relative = approved.get(binaryName);

            if (relative == null) {
                throw new ClassNotFoundException(
                        "Not allowlisted: " + binaryName);
            }

            try {
                if (relative.isAbsolute()) {
                    throw new IOException("Expected relative path");
                }

                Path candidate = trustedRoot.resolve(relative).toRealPath();
                if (!candidate.startsWith(trustedRoot)) {
                    throw new IOException("Outside trusted root");
                }
                if (!Files.isRegularFile(candidate)) {
                    throw new IOException("Not a regular class file");
                }

                byte[] bytes;
                try (InputStream in = Files.newInputStream(candidate)) {
                    bytes = in.readNBytes(MAX_BYTES + 1);
                }

                if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                    throw new IOException("Invalid class-file size");
                }

                return defineClass(
                        binaryName, bytes, 0, bytes.length);
            } catch (IOException error) {
                throw new ClassNotFoundException(
                        "Cannot read approved class: " + binaryName,
                        error);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: HostDemo <plugin-classes-directory>");
        }

        BoundedLoader loader = new BoundedLoader(Path.of(args[0]));

        Class<?> plugin = loader.loadClass("demo.plugins.Greeting");
        Object instance = plugin.getDeclaredConstructor().newInstance();
        String message = (String) plugin.getMethod("message")
                .invoke(instance);

        System.out.println("defined by bounded loader: "
                + (plugin.getClassLoader() == loader));
        System.out.println("message: " + message);

        Class<?> stringType = loader.loadClass("java.lang.String");
        System.out.println("standard class uses bootstrap: "
                + (stringType.getClassLoader() == null));

        try {
            loader.loadClass("demo.plugins.Unknown");
            System.out.println("unknown plugin class rejected: false");
        } catch (ClassNotFoundException expected) {
            System.out.println("unknown plugin class rejected: true");
        }
    }
}
~~~

Điểm kỹ thuật của code:

- `Map.of` là immutable map về cấu trúc (không có API chỉnh entries tại runtime); cấu hình mapping không thay đổi giữa những lần tra cứu.
- `toRealPath()` của root và candidate giải quyết đường dẫn thực tồn tại, bao gồm symlink theo hành vi mặc định của provider. `Path.startsWith` kiểm tra **path components**, tránh nhầm tiền tố chuỗi.
- `readNBytes(MAX_BYTES + 1)` giới hạn bytes đọc/đưa vào mảng; nếu file lớn hơn 1 MiB thì array sẽ chứa thêm một byte rồi bị từ chối. Không dùng `Files.readAllBytes` vô hạn trên nguồn có thể rất lớn.
- `defineClass(binaryName, bytes, 0, bytes.length)` yêu cầu bytes thể hiện class phù hợp tên đang được load. Nếu file bytes sai định dạng hoặc sai internal class name, JVM báo lỗi tương ứng, không tự đổi tên class theo tên file.
- Parent loader là **platform loader**, nên những class cốt lõi như `java.lang.String` vẫn được bootstrap/runtime loaders xử lý.
- `findClass` chỉ quản lý nguồn riêng; `loadClass` mặc định vẫn thực hiện delegation cho parent. **Không được coi đây là allowlist tuyệt đối của mọi class mà loader có thể trả về.**

### 5.4. Biên dịch và chạy

Mở terminal tại thư mục `bounded-loader-demo`. Những lệnh sau chạy bằng JDK 17 trở lên trên Windows, Linux và macOS khi thư mục được tạo đúng:

~~~bash
javac --release 17 -d plugin-classes plugin-src/demo/plugins/Greeting.java
javac --release 17 -d host-classes host-src/HostDemo.java
java -cp host-classes HostDemo plugin-classes
~~~

**Stdout chính xác khi chạy với các file trên và cấu hình thông thường:**

~~~text
defined by bounded loader: true
message: hello from mapped class
standard class uses bootstrap: true
unknown plugin class rejected: true
~~~

Diễn giải:

1. Vì chỉ `host-classes` được đưa vào `-cp`, system/app loader của host không được cung cấp bytes của `demo.plugins.Greeting` từ thư mục plugin.
2. `BoundedLoader` gọi parent trước; platform loader không có `demo.plugins.Greeting` trong JDK chuẩn, nên chuyển đến `findClass` của loader và đọc bytes từ trusted root.
3. `getClassLoader() == loader` đúng vì **loader này là defining loader** của plugin class.
4. Reflection tạo instance và gọi phương thức public `message()`. String trả về là Java core type, được chia sẻ phù hợp qua bootstrap/platform delegation.
5. `java.lang.String` được tìm qua parent/bootstrap; plugin loader không tự định nghĩa bản sao String.
6. Tên `demo.plugins.Unknown` không có trong allowlist. Parent không tìm được tên đó trong tình huống demo; `findClass` ném `ClassNotFoundException` nên ứng dụng in kết quả từ chối.

## 6. Giới hạn của ví dụ: bảo vệ mapping chứ không sandbox plugin

Ví dụ ở mục 5 là một **mẫu bounded filesystem mapping để học API**, không phải runtime an toàn cho mã không tin cậy. Những giới hạn được chủ động chỉ rõ:

**Thứ nhất, parent vẫn có quyền tìm class.** Nếu gọi `loader.loadClass("java.util.List")`, parent/bootstrap có thể trả về type Java runtime. Điều này cần thiết để plugin thực thi Java code. Nếu người viết kỳ vọng chỉ duy nhất `Greeting` và hoàn toàn không có class nào khác được trả về, thì `findClass` allowlist **không đáp ứng** kỳ vọng đó. Một số cơ chế plugin cố override `loadClass` để áp chính sách khác, nhưng chặn các core types tùy tiện sẽ làm việc liên kết của plugin thất bại. Không cố biến class loading boundary thành quyền bảo mật hệ thống.

**Thứ hai, file root phải đáng tin và có kiểm soát quyền ghi.** `toRealPath()` + `startsWith` chặn việc đường dẫn hiện tại trỏ ra ngoài root tại thời điểm kiểm tra; chúng không cung cấp giao dịch nguyên tử giữa kiểm tra và mở file. Nếu kẻ tấn công có quyền đổi symlink hoặc thay file trong kho cùng lúc, cần kho immutable, file ownership/permissions ở OS, ký bytecode hoặc cách mở file qua directory-handle phù hợp.

**Thứ ba, giới hạn byte không kiểm tra độ tin cậy của bytecode.** Một class file nhỏ hơn 1 MiB có thể làm nhiều việc khi được khởi tạo/thực thi. Verification của JVM bảo vệ tính an toàn kiểu bytecode, nhưng không hạn chế mọi hành vi I/O, CPU, mạng hay tác dụng phụ của static initializer.

**Thứ tư, custom loader không tự làm unload/reload an toàn.** Một `Class<?>` đã được định nghĩa bởi một loader không thể được “đổi bytes” tùy ý chỉ bằng cách sửa file rồi gọi `loadClass` lần nữa. Để dùng một định nghĩa mới của cùng binary name, thường phải tạo loader mới, quản lý lifecycle và giải phóng tham chiếu tới loader cũ nếu muốn GC có thể unload.

**Thứ năm, byte cap và allowlist không phải resource quota toàn tiến trình.** JVM có thể nạp dependency khác, plugin có thể cấp phát heap hoặc spawn thread; application cần những giới hạn và cô lập bổ sung nếu threat model đòi hỏi.

### 7. Vì sao không dùng className.replace để đọc bất kỳ file nào?

Một đoạn mã dễ gặp:

~~~java
@Override
protected Class<?> findClass(String name)
        throws ClassNotFoundException {
    String file = name.replace('.', '/') + ".class";
    byte[] bytes = Files.readAllBytes(Path.of("/some/root", file));
    return defineClass(name, bytes, 0, bytes.length);
}
~~~

**Đây là anti-pattern mang tính minh họa; không phải file runnable độc lập.** Ngoài việc thiếu xử lý `IOException`, đoạn code còn có vấn đề:

- Không xác định package hoặc tên lớp nào thực sự được cho phép.
- Không có kiểm tra candidate path thực nằm trong trusted root.
- Không xử lý symlink đưa candidate sang file ngoài root.
- Không giới hạn kích thước file trước khi cấp bộ nhớ đọc toàn bộ.
- Không ghi rõ dependency được ủy quyền cho parent hay do loader tự nạp.
- Không phân biệt “không tìm thấy class” và “đường dẫn bị từ chối bởi policy”.
- Không đảm bảo bytes đến từ artifact đã được phê duyệt.

Việc ghép một path chỉ là **cơ chế chuyển đổi hình thức tên**, không phải đầy đủ chính sách “bản đồ tên lớp → nguồn dữ liệu tin cậy”.

Với một thư viện có rất nhiều class hợp lệ, có thể lập manifest từ thời điểm build để ánh xạ tên sang các JAR entries, thêm hash và version, rồi dùng cùng triết lý kiểm soát phạm vi. Không nên mở rộng quyền đơn giản bằng cách cho tất cả tên binary và tất cả URLs.

## 8. Nếu nguồn là JAR thay vì thư mục

Một thiết kế có thể ánh xạ:

~~~text
com.example.spi.PluginA -> plugins/plugin-a-v3.jar!/com/example/spi/PluginA.class
com.example.spi.PluginB -> plugins/plugin-b-v1.jar!/com/example/spi/PluginB.class
~~~

Đây là **bản đồ khái niệm**, không phải cú pháp đường dẫn file mà `Path.of` hiểu nguyên văn. `!/...` thường gợi nhớ cú pháp resource URL dạng `jar:file:...!/entry`.

Các giới hạn cần có:

1. Chọn JAR đã được phê duyệt bằng manifest/registry, không lấy arbitrary URL từ tên lớp.
2. Giới hạn kích thước và nguồn của JAR; chống nén quá mức/zip bomb nếu nguồn không đáng tin.
3. Chỉ cho phép entry đã đăng ký; kiểm tra tên entry theo chuẩn và không chấp nhận path traversal khi giải nén.
4. Giới hạn bytes class mà loader đọc từ từng entry; tránh đọc không giới hạn.
5. Có chiến lược parent delegation rõ đối với API host/JDK.
6. Chọn `URLClassLoader`/module APIs sẵn có khi phù hợp, thay vì tự viết trình phân tích ZIP/class file.
7. Đóng `URLClassLoader` sau khi không dùng để giải phóng resources mở; việc close không làm mọi class đã được định nghĩa lập tức unload.

Ví dụ API Java 17:

~~~java
try (java.net.URLClassLoader loader =
         new java.net.URLClassLoader(
                 new java.net.URL[] { approvedJar.toUri().toURL() },
                 ClassLoader.getPlatformClassLoader())) {
    Class<?> type = loader.loadClass("com.example.spi.PluginA");
}
~~~

**Đoạn minh họa**, giả sử `approvedJar` là một `Path` đã được kiểm tra và thực sự chứa class. Mặc dù JAR URL đã giới hạn ở một file, bản thân `URLClassLoader` **không tạo allowlist tên class** như ví dụ `BoundedLoader`; nếu cần allowlist nghiêm ngặt, kiểm tra tên ở API wrapper/loader policy thích hợp. Parent delegation cũng có thể trả về class từ parent.

## 9. Nếu nguồn là mạng, database hoặc object storage

Có thể ánh xạ tên binary sang một **định danh nội bộ ổn định**, ví dụ:

~~~text
demo.plugins.Greeting
    -> artifact-id: plugin-greeting@sha256:...
    -> trusted object storage entry
    -> byte[] sau xác thực / giới hạn
    -> defineClass(binaryName, ...)
~~~

Điểm mấu chốt: **binary name không phải URL do người gọi tự chọn**. Loader không nên làm:

~~~java
// KHÔNG phải mẫu an toàn: người gọi điều khiển URL nguồn
URL url = new URL(userInput);
byte[] bytes = url.openStream().readAllBytes();
~~~

Đây là anti-pattern minh họa; không phải chương trình hoàn chỉnh. Ngoài vấn đề unbounded bytes, cách này có nguy cơ truy cập những network endpoints ngoài dự kiến (SSRF trong ngữ cảnh service), thiếu xác thực integrity và khó điều phối cache/version.

Thiết kế hợp lý hơn:

- Service registry/manifest ánh xạ **allowed binary name** sang artifact identifier được xác thực.
- Storage client truy cập **chỉ các buckets/endpoints đã cấu hình**.
- TLS, authn/authz và hash/signature đảm bảo dữ liệu đến từ nguồn được phép.
- Timeout, retry giới hạn, byte limit và circuit breaker tránh treo loader thread.
- Các lỗi fetch/validation được bọc phù hợp trong `ClassNotFoundException` khi tên không có định nghĩa; lỗi bytecode/linkage vẫn để JVM báo nguyên nhân tương ứng.
- Có cơ chế quan sát (logs/metrics) cho tên requested, artifact ID và thời gian fetch mà không tiết lộ bí mật.
- Không kích hoạt network fetch bất ngờ trên mọi `loadClass`; cân nhắc tải trước dữ liệu đã approved hoặc cache có giới hạn.

## 10. Parent-first delegation và allowlist: ranh giới nào đang áp dụng?

Cách triển khai mặc định của `ClassLoader.loadClass(String, boolean)` thường:

~~~text
loadClass(name)
      |
      v
findLoadedClass(name)
      |
      v
parent.loadClass(name)
      |
  không tìm được
      |
      v
findClass(name)
      |
      v
defineClass(name, bytes, ...)
~~~

Vì vậy, nếu parent đã biết `demo.plugins.Greeting` thì child có thể **không đọc** file mapping của mình, dù allowlist có tên ấy.

Hệ quả thiết kế:

- Trong plugin isolation, đặt APIs chia sẻ ở parent và implementation độc lập ở loader riêng.
- Đừng coi allowlist ở `findClass` là chặn mọi thứ parent có thể trả về.
- Nếu cần **chặn tuyệt đối một binary name** khỏi việc được trả về, phải thiết kế policy ở lớp API/loader routing phù hợp, xét cả parent delegation và những ràng buộc JVM về core classes.
- Nếu cần **child-first** cho package plugin, cần override `loadClass` cẩn thận, tránh tự định nghĩa các core/shared API classes gây `ClassCastException`/`LinkageError`. Child-first không tự nhiên an toàn hơn.
- Parent loader có thể là platform loader, application loader hoặc một loader chung; lựa chọn phụ thuộc package sharing và dependencies của plugin.

Phân biệt **initiating loader** (loader nhận/tham gia yêu cầu nạp) và **defining loader** (loader thực sự định nghĩa kiểu). Khi parent trả về một Class, `type.getClassLoader()` báo defining loader, có thể không phải child.

## 11. Khi nào trả ClassNotFoundException, khi nào để LinkageError nổi lên?

Đối với `findClass(String)`, hợp đồng chính là ném `ClassNotFoundException` nếu loader không tìm thấy một định nghĩa cho tên được yêu cầu.

Các tình huống:

| Tình huống | Hướng xử lý |
| --- | --- |
| Tên không nằm trong allowlist | `ClassNotFoundException` với thông điệp dễ debug |
| File mapped chưa tồn tại | `ClassNotFoundException` có cause `IOException` thích hợp |
| File nằm ngoài trusted root sau resolve symlink | Từ chối, không đọc; ví dụ dùng `ClassNotFoundException` với cause policy/IO |
| File không đọc được hoặc vượt byte cap | Từ chối theo policy; cause I/O và mô tả rõ |
| Bytes không phải class file hợp lệ | `defineClass` có thể ném `ClassFormatError` |
| Bytes mang binary name khác tên yêu cầu | JVM có thể ném `NoClassDefFoundError` cho wrong name |
| Class name trùng định nghĩa trái phép trong cùng loader | `LinkageError` |
| Vi phạm loader constraints khi liên kết | `LinkageError` phù hợp |

**Không nên bắt tất cả `Throwable` rồi chuyển hết thành `ClassNotFoundException`.** Làm vậy che `OutOfMemoryError`, `LinkageError` và vấn đề lập trình nghiêm trọng. Ví dụ chỉ catch `IOException` quanh phần đọc bytes; các errors do JVM ném từ `defineClass` tiếp tục mang ngữ nghĩa gốc.

Trong môi trường production, nếu một policy reject do file ngoài trusted root, có thể dùng loại lỗi policy nội bộ rõ hơn ở API phía trên loader; vì giao diện `findClass` yêu cầu `ClassNotFoundException` cho tình huống không cung cấp định nghĩa, việc gói cause có ý nghĩa trong cách triển khai mẫu.

## 12. Kiểm tra tên bên trong bytecode và type identity

`defineClass(name, bytes, 0, bytes.length)` không chỉ “đặt nhãn” tùy ý cho một mảng bytes. Class file chứa định danh kiểu nội bộ. JVM kiểm tra sự nhất quán giữa tên yêu cầu và định nghĩa; nếu mismatch hoặc bytes sai định dạng, việc define thất bại.

Ngoài ra, **runtime type identity** là sự kết hợp của:

~~~text
(binary class name, defining ClassLoader)
~~~

Hai loader khác nhau có thể định nghĩa cùng binary name từ cùng mảng bytes, thành hai kiểu runtime riêng. Vì vậy:

- `Class<?>` của hai loader có thể cùng `getName()` nhưng `a == b` là `false`.
- Cast object từ phiên bản A sang cùng-named class B có thể ném `ClassCastException`.
- Mỗi định nghĩa có static fields/initialization riêng.
- Một shared interface/API nên được định nghĩa bởi parent chung, không đóng nhiều bản API vào plugin loaders khác nhau.
- Mapping thay đổi nguồn bytes không “cập nhật” định nghĩa Class cũ đã nạp. Muốn hot reload thông thường cần tạo defining loader mới.

Class loader mapping và class identity liên hệ chặt chẽ: **nguồn bytes được chọn quyết định nội dung class, còn defining loader quyết định namespace runtime của class đó**.

## 13. Các kiểm tra an toàn tối thiểu và giới hạn thực tế

| Rủi ro | Kiểm tra tối thiểu | Giới hạn còn lại |
| --- | --- | --- |
| Arbitrary class name | Exact allowlist/manifest đã ký | Parent vẫn có thể nạp những tên khác |
| Path traversal | Relative path + canonical root + `Path.startsWith` | Concurrent filesystem changes cần mô hình mạnh hơn |
| Symlink ra ngoài root | Kiểm tra target `toRealPath` nằm trong root | Có TOCTOU race nếu kho cho phép sửa đồng thời |
| File size quá lớn | `readNBytes(MAX+1)` và từ chối nếu vượt | Nhiều class nhỏ vẫn tiêu thụ nhiều tổng memory |
| Bytecode giả mạo | Allowlisted artifact, integrity/hash/signature | Bytecode hợp lệ vẫn có thể thực thi tác dụng phụ |
| Dependency conflict | Parent API sharing, version policy | Cần testing, module/loader constraints |
| Network source độc hại | Fixed endpoint, auth, timeout, byte cap | Cần bảo vệ credential, retry và SSRF |
| Hot reload memory leak | Loader lifecycle và giải phóng references | Static caches, TCCL, threads có thể giữ loader |

**Nguyên tắc sản phẩm:** nếu plugin do bên không tin cậy cung cấp và cần ngăn đọc file/network/process APIs, hãy dùng **cách ly ở process/container/VM cùng OS permissions và policy** phù hợp. Java class loader chủ yếu cung cấp **cơ chế nạp và cô lập danh tính kiểu**, không phải thay thế security sandbox.

## 14. Cách viết test cho bounded mapping

Một bộ test tốt phải xác minh cả happy path lẫn policy rejects:

1. **Đúng tên, đúng nguồn:** `demo.plugins.Greeting` được define thành công từ file tương ứng.
2. **Tên ngoài allowlist:** `demo.plugins.Unknown` bị từ chối (trừ khi parent cung cấp theo chính sách đã chọn).
3. **Không có file:** cấu hình root thiếu file approved dẫn tới error hợp lý, không tự tìm file lân cận.
4. **Mismatched class bytes:** file được đặt tên Greeting.class nhưng bên trong là class khác; JVM không được im lặng chấp nhận tên yêu cầu.
5. **Quá byte cap:** nguồn vượt giới hạn bị từ chối mà không đọc vô hạn.
6. **Symlink ra ngoài root:** với filesystem cho phép symlinks, canonical target ngoài root bị từ chối.
7. **Parent class:** `java.lang.String` vẫn được tìm qua bootstrap, không bị self-define.
8. **Cùng tên ở parent:** biết chắc parent-first sẽ ưu tiên parent hay cần thay chính sách routing.
9. **Hai loader độc lập:** nếu mỗi loader define cùng bytes, xác nhận `classA != classB` và static state được cô lập.
10. **Nhiều thread yêu cầu cùng class:** dùng synchronization đúng `ClassLoader.loadClass` để không tự define trùng; tránh cache tự viết không an toàn.
11. **Root bị sửa khi loader hoạt động:** xác định policy chống race hoặc yêu cầu immutable data source.
12. **Log:** rejected names và causes đủ chẩn đoán, nhưng không tiết lộ đường dẫn nội bộ/bí mật cho user cuối nếu không cần.

Không cần dùng một framework cụ thể để kiểm thử, nhưng hãy đảm bảo test phân biệt **lỗi tìm class**, **policy reject**, **bytecode verification/linkage** và **lỗi static initializer**.

## 15. Những lỗi phổ biến khi tự thiết kế ánh xạ

**Lỗi 1: Ghép trực tiếp chuỗi input với path.** Dẫn tới path traversal, tên không đúng package, và quyền nạp class quá rộng.

**Lỗi 2: Chỉ kiểm tra string prefix.** `/data/plugins-evil` cũng bắt đầu bằng `/data/plugins` theo nghĩa chuỗi; dùng `Path.startsWith` trên canonical/real paths.

**Lỗi 3: Tin rằng normalize là đủ để ngăn symlink.** `normalize()` xử lý `.`/`..` ở mức cú pháp, **không tự giải quyết symbolic links**. `toRealPath` cung cấp thông tin thực tế khi file tồn tại; với kho có hostile concurrent writes vẫn cần bảo vệ khác.

**Lỗi 4: Dùng `Files.readAllBytes` không cap từ nguồn tùy ý.** Có nguy cơ cấp phát lớn hoặc cạn tài nguyên.

**Lỗi 5: Giả sử findClass allowlist kiểm soát cả parent.** `loadClass` mặc định hỏi parent trước; tên khác có thể được trả về từ parent.

**Lỗi 6: Định nghĩa lại `java.lang.*` hoặc shared API class.** Dễ gây vi phạm JVM constraints và xung đột danh tính kiểu.

**Lỗi 7: Nuốt mọi Throwable và báo ClassNotFoundException.** Che thông tin cấu trúc class file hỏng, lỗi liên kết, OOM hoặc lỗi chạy của static initializer.

**Lỗi 8: Tự gọi `resolveClass` và nghĩ đã chạy mã plugin.** Linking khác class initialization; cần hiểu thời điểm `<clinit>`.

**Lỗi 9: Đổi file trên đĩa rồi kỳ vọng `loadClass` trả Class cập nhật.** Class đã define thuộc loader cũ không tự biến đổi; có thể cần loader mới.

**Lỗi 10: Xem bytecode verification là sandbox.** Class hợp lệ có thể truy cập file, mạng hoặc API nguy hiểm tùy quyền của process.

**Lỗi 11: Bỏ qua package metadata/sealing/signers.** Khi chuyển sang loader đóng gói từ JAR, có thể cần bảo toàn quy tắc package sealing, code source, certificates và `ProtectionDomain` phù hợp; việc chỉ đọc bytes rồi gọi `defineClass` không tự tái hiện tất cả thông tin JAR packaging.

**Lỗi 12: Không ghi lại quyết định mapping.** Khi debug nhiều plugin cùng binary name, cần biết defining loader, artifact version và source identifier thực tế, không chỉ log một chuỗi tên class.

## 16. Khi nào dùng URLClassLoader hoặc API sẵn có?

Tự viết `ClassLoader` có ích khi cần kiểm soát đặc biệt về source mapping, verification policy và delegation. Nhưng nếu cần tải class từ một tập JAR URLs cố định, `URLClassLoader` (Java 17) có thể phù hợp và giảm nhu cầu tự đọc class bytes.

Trong JPMS, nếu plugin là named module thực sự, nên tìm hiểu `ModuleFinder`, `Configuration` và `ModuleLayer` để thiết kế module resolution/module loaders đúng ngữ nghĩa thay vì giả định một directory mapping đủ thay thế JPMS.

Một lựa chọn thực tế:

| Nhu cầu | Hướng thiết kế |
| --- | --- |
| Một số class cụ thể từ một thư mục được kiểm soát | Explicit allowlist + custom `findClass` |
| Nhiều JAR được phê duyệt, classpath-like loading | `URLClassLoader` và source/URL allowlist |
| Plugin có named modules, requires/exports | JPMS `ModuleFinder`/`ModuleLayer` |
| Bytecode được sinh động | `defineClass`/hidden-class APIs theo hợp đồng phù hợp |
| Code từ bên không tin cậy cần cách ly quyền hệ thống | Cách ly process/container, không dùng ClassLoader làm sandbox |

## 17. Tự kiểm tra

**1. Vì sao `name.replace('.', '/') + ".class"` chưa phải bounded mapping?**  
Vì nó chỉ chuyển tên sang đường dẫn, chưa giới hạn tên, nguồn dữ liệu, kích thước hay quyền truy cập.

**2. Exact allowlist tốt hơn package prefix trong trường hợp nào?**  
Khi chỉ có một tập nhỏ class được approved hoặc cần gắn từng class với artifact/hash/version cụ thể.

**3. Vì sao phải kiểm tra `candidate.toRealPath().startsWith(root.toRealPath())`?**  
Để nhận diện đích canonical ngoài root, kể cả khi path đi qua symlink; vẫn cần xét TOCTOU với kho có writer không tin cậy.

**4. Tại sao `findClass` từ chối tên Unknown nhưng `loader.loadClass("java.lang.String")` vẫn được?**  
Vì parent/bootstrap delegation diễn ra trước `findClass` trong default loader.

**5. Nếu file tên Greeting.class chứa bytecode của WrongName, JVM có coi nó là Greeting không?**  
Không; `defineClass` kiểm tra tính nhất quán của tên class file và có thể báo linkage error.

**6. Giới hạn 1 MiB bảo đảm code plugin không thể làm hại app không?**  
Không. Đây chỉ là byte cap cho việc đọc một class, không phải kiểm soát quyền hành vi của Java code.

**7. Vì sao không catch `Throwable` quanh `defineClass` để luôn trả `ClassNotFoundException`?**  
Vì phải giữ nguyên nghĩa lỗi class file/linkage và các lỗi nghiêm trọng như OOM để chẩn đoán đúng.

**8. Khi một parent loader đã có cùng binary name, child sẽ define bản của mình trong default `loadClass` không?**  
Thông thường không; parent-first delegation sẽ trả Class của parent.

**9. Một class đã load từ file, sửa file rồi gọi `loadClass` lần nữa sẽ có bytes mới không?**  
Không mặc nhiên. Class runtime đã define vẫn thuộc loader cũ; hot reload thường cần loader mới và quản lý lifecycle.

**10. Nếu cần ngăn plugin đọc filesystem thật sự, bounded ClassLoader có đủ không?**  
Không. Cần isolation/permissions ở mức process, OS, container hoặc VM theo threat model.

## 18. Tài liệu tham chiếu

- **Java SE 17 — ClassLoader**: `findClass`, `loadClass`, `defineClass`, `getClassLoadingLock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 — Path**: `resolve`, `normalize`, `startsWith`, `toRealPath`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html
- **Java SE 17 — Files**: `newInputStream`, `isRegularFile`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html
- **Java SE 17 — InputStream**: `readNBytes`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html
- **Java SE 17 — URLClassLoader**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- **JVMS 17 §5.3 — Creation and Loading** (defining/initiating loaders): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JLS 17 §12.2 — Loading of Classes and Interfaces**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-006">Truyền tên, offset và length chính xác cho defineClass</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền tên, offset và length chính xác cho defineClass

Khi tự viết `ClassLoader` để đọc bytecode từ tệp, JAR, mạng, bộ nhớ hoặc một container riêng, bước quyết định là gọi đúng phương thức **`defineClass(String name, byte[] b, int off, int len)`**. Ba giá trị **`name`, `off`, `len`** phải mô tả chính xác **định nghĩa lớp** mà vùng byte mang theo: tên nhị phân của lớp bên trong ClassFile và vị trí bắt đầu/độ dài phần ClassFile hợp lệ trong mảng.

Một lỗi nhỏ có thể tạo ra `ClassFormatError`, `NoClassDefFoundError` hoặc lỗi chỉ xuất hiện khi code nạp lớp ở môi trường production. Việc byte array đủ lớn hay có chứa một đoạn ClassFile hợp lệ **không đủ** nếu ta cắt sai ranh giới, truyền nhầm kích thước vùng đệm hoặc khai báo một tên lớp không khớp.

**Quy tắc chính:**

~~~java
Class<?> type = defineClass(binaryName, bytes, offset, classFileLength);
~~~

Đây là **fragment** phải chạy từ một subclass của `ClassLoader` hoặc ngữ cảnh có quyền gọi phương thức `protected` đó; không phải một lệnh gọi công khai từ bất kỳ class nào.

- `binaryName`: tên nhị phân JVM/Java của **đúng class nằm trong ClassFile**, ví dụ `com.example.Widget`, không phải đường dẫn `com/example/Widget.class`.
- `bytes`: mảng có chứa dữ liệu ClassFile.
- `offset`: **chỉ số byte đầu** của ClassFile trong mảng `bytes`, đếm từ **0**.
- `classFileLength`: **số byte của ClassFile bắt đầu tại offset**, không phải chỉ số cuối mảng.

Nếu toàn bộ mảng là đúng một ClassFile (không có header/footer thừa), cách gọi điển hình nhất là:

~~~java
defineClass("com.example.Widget", classBytes, 0, classBytes.length);
~~~

## 1. Vai trò của defineClass trong một class loader tùy chỉnh

`ClassLoader` chịu trách nhiệm tìm dữ liệu của lớp và phối hợp với JVM định nghĩa class. Với subclass truyền thống, các bước thường là:

~~~text
Yêu cầu nạp "com.example.Widget"
       |
       v
loadClass(...) kiểm tra đã nạp / ủy quyền parent
       |
       v
findClass(...) đọc hoặc lấy byte[] ClassFile
       |
       v
xác định binary name + đoạn byte hợp lệ
       |
       v
defineClass(name, bytes, offset, length)
       |
       v
JVM tạo định nghĩa Class<?>
       |
       v
resolve / initialize vào thời điểm thích hợp
~~~

`defineClass` là bước **định nghĩa class từ biểu diễn ClassFile**. Nó không có nghĩa mọi lúc đều chạy static initializer ngay tại dòng gọi. Liên kết, resolution và khởi tạo là các hoạt động riêng của JVM. `resolveClass(type)` thường được gọi bởi mã trong `loadClass` khi yêu cầu resolve; initialization chỉ xảy ra theo các điều kiện của Java/JVM.

Subclass đơn giản thường ghi đè **`findClass(String name)`** để lấy bytes, còn giữ cơ chế `loadClass` parent-first mặc định. Không tự ghi đè `loadClass` nếu chưa cần child-first hoặc chính sách đặc biệt, vì rất dễ phá delegation, tạo hai kiểu cùng tên bởi loader khác nhau hoặc chặn lớp nền tảng.

### Đặc tả tham số quan trọng

| Tham số | Ý nghĩa | Sai sót thường gặp |
| --- | --- | --- |
| `name` | Binary name tương ứng với lớp trong ClassFile; API cũng hỗ trợ `null` ở một số overload, nhưng thông thường nên truyền tên đúng. | Dùng đường dẫn `pkg/MyClass.class` hoặc tên khác `this_class` bên trong bytecode. |
| `b` | Mảng byte chứa ClassFile. | Truyền một buffer chưa ghi đầy dữ liệu, buffer bị sửa, hoặc dữ liệu đã nén/mã hóa chưa giải mã. |
| `off` | Byte đầu tiên cần đọc, chỉ số zero-based. | Đọc nhầm header container hoặc gửi offset 0 dù có prefix. |
| `len` | Số byte ClassFile trong mảng tính từ `off`. | Truyền `b.length` thay vì độ dài payload, nhầm `endIndex` với `length`, hoặc quên trừ prefix. |

**Tên `name` không phải tên file:** Nếu ta đọc `plugins/lab/generated/Echo.class` từ ổ đĩa, binary name của kiểu là `lab.generated.Echo`, không phải `plugins.lab.generated.Echo`, `lab/generated/Echo` hay chuỗi có đuôi `.class`.

## 2. Binary name phải khớp tên khai báo trong bytecode

Trong ClassFile có cấu trúc `this_class` tham chiếu đến metadata định danh kiểu. Với Java:

~~~java
package lab.generated;
public class Echo { }
~~~

ClassFile mô tả tên nội bộ tương ứng `lab/generated/Echo`, trong khi tham số `name` của `ClassLoader.defineClass` được cung cấp bằng **binary name**:

~~~text
Cần truyền:    lab.generated.Echo
Không truyền:  lab/generated/Echo
Không truyền:  lab/generated/Echo.class
Không truyền:  Echo
Không truyền:  lab.generated.Other
~~~

Tên class lồng còn dùng dấu `$` trong binary name, ví dụ `lab.generated.Outer$Inner` cho một member class được biên dịch thông thường. Tên file có thể chứa `$`, nhưng phải lấy theo **tên thực tế trong ClassFile**, không xây bằng cách thay dấu gạch chéo dựa trên đường dẫn container một cách tùy tiện.

Nếu cung cấp `name` **khác** tên mà ClassFile khai báo, JVM có thể ném **`NoClassDefFoundError`** (thường kèm thông tin *wrong name*). Không có cơ chế đổi tên kiểu bằng cách chỉ đổi tham số `name`: muốn class thật sự mang tên khác cần biến đổi ClassFile hợp lệ và tất cả metadata/tham chiếu liên quan theo quy tắc JVM.

**Có được truyền `null` không?** Một số `defineClass` overload cho phép `name == null` và JVM suy từ ClassFile, nhưng tài liệu API cảnh báo hạn chế kiểm tra gắn với tên/package; do đó **nên truyền binary name không null và đã xác thực** trong loader tùy chỉnh. Đặc biệt, package `java.*` có ràng buộc bảo vệ, và tên bytecode/loại module có thể ảnh hưởng kiểm tra bảo mật.

## 3. off và len là khoảng byte [off, off + len)

Thứ tự byte của một ClassFile chuẩn bắt đầu bằng **`CA FE BA BE`**. Có thể xuất hiện header/footer của định dạng lưu trữ riêng bên ngoài payload thực:

~~~text
byte[] container:

Index:  0   1   2   3   4   5   6   7   8   9  ...      N
        +-------------------+------------------------+----------+
Data:   | 5 byte HEADER     | CA FE BA BE ... .class | TRAILER  |
        +-------------------+------------------------+----------+
                            ^
                            off = 5
                             <---- len bytes ---->
~~~

Với ví dụ, ClassFile chiếm vùng **từ `off` (bao gồm) đến `off + len` (không bao gồm)**. Do đó:

- `off = 5`, `len = payload.length` là đúng nếu `payload` là toàn bộ byte ClassFile.
- `off = 0`, `len = payload.length` sẽ truyền cả header và cắt thiếu phần cuối ClassFile.
- `off = 5`, `len = container.length` dễ vượt quá giới hạn mảng; kể cả không vượt, truyền cả trailer là sai ý nghĩa.
- `off = 5`, `len = payload.length - 1` cắt mất byte cuối ClassFile, thường dẫn tới `ClassFormatError`.
- `off = 5`, `len = off + payload.length` là nhầm **chỉ số kết thúc** với **độ dài**.

Cần phân biệt bốn đại lượng:

~~~text
offset                 vị trí bắt đầu payload trong mảng
endExclusive           vị trí sau byte cuối = offset + length
length                 số byte payload = endExclusive - offset
container.length       độ dài cả mảng, có thể lớn hơn payload
~~~

Lỗi thường gặp trong xử lý stream, buffer pool và container tự thiết kế là dùng **capacity** thay vì số byte thực sự nhận được hoặc số byte payload đã được decode. Một buffer có capacity 65.536 không có nghĩa 65.536 byte đều thuộc ClassFile.

### Kiểm tra khoảng trước khi defineClass

Một điều kiện an toàn trước khi dùng một mảng không đáng tin là:

~~~java
if (offset < 0 || length < 0 || length > data.length
        || offset > data.length - length) {
    throw new IndexOutOfBoundsException("Invalid ClassFile byte range");
}
~~~

Đây là **fragment**, giả sử `data` là mảng khác `null` và `offset`/`length` là số nguyên. Điều kiện dùng `offset > data.length - length` giúp tránh overflow của biểu thức cộng `offset + length`. Thư viện/JVM vẫn kiểm tra tham số và định dạng bytecode, nhưng validation ở boundary giúp báo lỗi đầu vào rõ ràng hơn.

**Không coi validation độ dài là validation ClassFile.** Kiểm tra offset/length hợp lệ chỉ nói khoảng nằm trong mảng; bytes vẫn có thể sai magic, sai constant pool, sai phiên bản hoặc bị làm hỏng.

## 4. Ví dụ Java 17: cùng một file, buffer có prefix/suffix

Ví dụ này dùng compiler của JDK để tạo đúng một ClassFile `lab.generated.Echo` tại thời điểm chạy, rồi đặt payload vào một mảng lớn hơn có **5 byte prefix và 7 byte suffix**. Một subclass của `ClassLoader` gọi trực tiếp `defineClass` để thử cả trường hợp đúng và sai.

Tạo tệp độc lập **`DefineClassRangeDemo.java`**:

~~~java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class DefineClassRangeDemo {
    private static final String NAME = "lab.generated.Echo";
    private static final int PREFIX = 5;
    private static final int SUFFIX = 7;

    static class ByteArrayLoader extends ClassLoader {
        ByteArrayLoader() {
            super(ClassLoader.getSystemClassLoader());
        }

        Class<?> define(String binaryName, byte[] container,
                        int offset, int length) {
            return defineClass(binaryName, container, offset, length);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("defineclass-range-");
        Path source = root.resolve("src/lab/generated/Echo.java");
        Path classes = root.resolve("classes");

        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);

        Files.writeString(source,
                "package lab.generated;\n"
                + "public class Echo {\n"
                + "  public static String message() { return \"OK\"; }\n"
                + "}\n",
                StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a full JDK");
        }

        int result = compiler.run(null, null, null,
                "--release", "17",
                "-d", classes.toString(), source.toString());

        if (result != 0) {
            throw new IllegalStateException("Compilation failed");
        }

        byte[] payload = Files.readAllBytes(
                classes.resolve("lab/generated/Echo.class"));

        byte[] container = new byte[PREFIX + payload.length + SUFFIX];
        Arrays.fill(container, (byte) 0x11);
        System.arraycopy(payload, 0, container, PREFIX, payload.length);

        Class<?> type = new ByteArrayLoader().define(
                NAME, container, PREFIX, payload.length);

        System.out.println("Loaded: " + type.getName());
        System.out.println("Message: "
                + type.getMethod("message").invoke(null));
        System.out.println("Container larger: "
                + (container.length > payload.length));

        try {
            new ByteArrayLoader().define(NAME, container, 0, payload.length);
        } catch (ClassFormatError e) {
            System.out.println("Wrong offset: ClassFormatError");
        }

        try {
            new ByteArrayLoader().define(
                    NAME, container, PREFIX, payload.length - 1);
        } catch (ClassFormatError e) {
            System.out.println("Short length: ClassFormatError");
        }

        try {
            new ByteArrayLoader().define(
                    "lab.generated.Other", container, PREFIX, payload.length);
        } catch (NoClassDefFoundError e) {
            System.out.println("Wrong name: NoClassDefFoundError");
        }

        try {
            new ByteArrayLoader().define(
                    NAME, container, container.length - 2, 8);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("Invalid range: IndexOutOfBoundsException");
        }
    }
}
~~~

Dùng **JDK 17 hoặc mới hơn**, chạy trong thư mục chứa file:

~~~bash
javac --release 17 DefineClassRangeDemo.java
java DefineClassRangeDemo
~~~

Với JDK chuẩn có `jdk.compiler`, chương trình in:

~~~text
Loaded: lab.generated.Echo
Message: OK
Container larger: true
Wrong offset: ClassFormatError
Short length: ClassFormatError
Wrong name: NoClassDefFoundError
Invalid range: IndexOutOfBoundsException
~~~

**Giải thích:**

1. `JavaCompiler` biên dịch file nguồn được sinh ra thành ClassFile của `lab.generated.Echo` với target Java 17. Bytecode được đọc **đúng** từ `classes/lab/generated/Echo.class`.
2. `Arrays.fill` tạo các byte bao ngoài không thuộc ClassFile. `System.arraycopy` đặt toàn bộ payload tại chỉ số `PREFIX = 5`.
3. Lời gọi `define(NAME, container, PREFIX, payload.length)` đưa JVM đúng vùng byte và đúng binary name, nên tạo class hợp lệ.
4. Phản chiếu gọi `message()` chứng minh class đã được định nghĩa và sử dụng. Kết quả là chuỗi `OK`.
5. Khi truyền `off = 0`, dữ liệu bắt đầu bằng 0x11 thay vì magic `CAFEBABE`. JVM bác bỏ định dạng bằng `ClassFormatError`.
6. Khi cắt `len` ngắn hơn payload một byte, ClassFile bị thiếu phần cuối và bị bác bỏ bằng `ClassFormatError`.
7. Khi truyền `name = lab.generated.Other` nhưng bytecode tự khai báo `lab.generated.Echo`, lỗi là `NoClassDefFoundError` do tên không khớp.
8. Với offset nằm sát cuối buffer và `len = 8` vượt quá mảng, JVM phát hiện khoảng bất hợp lệ và ném `IndexOutOfBoundsException`.
9. Mỗi phép thử lỗi dùng **một `ByteArrayLoader` mới**. Nếu thử định nghĩa lần nữa cùng tên trong loader đã định nghĩa thành công, sẽ cần xét thêm lỗi duplicate-definition thay vì chỉ quan sát input bytes.

Ví dụ tạo thư mục tạm để chứa source/class sinh động; bạn có thể dọn sau khi chạy. Nó không đòi kết nối mạng hay thư viện ngoài JDK. `Class.forName` hoặc `resolveClass` không cần cho demo này vì ta gọi trực tiếp protected `defineClass` rồi dùng reflection. Trong ứng dụng thật, cần thêm chính sách parent delegation, kiểm tra nguồn bytecode, bảo vệ package và quản lý lifecycle.

### Vì sao không dùng container.length?

`container.length` ở ví dụ là `5 + payload.length + 7`. Nếu lấy `len = container.length` bắt đầu tại offset 5, khoảng vượt cuối mảng. Nếu lấy offset 0 và length container.length, ta truyền cả prefix/trailer; JVM không thể hiểu đó là một ClassFile bắt đầu tại byte 0.

Khi container chứa **nhiều ClassFile**, không truyền toàn bộ container cho `defineClass`; phải trích chính xác một ClassFile cần nạp và tên tương ứng. Việc tìm ranh giới ClassFile trong định dạng container là trách nhiệm parser/decoder của container, không phải `defineClass`.

## 5. Ví dụ triển khai findClass đúng quy tắc

Đây là **fragment rút gọn** của một subclass `ClassLoader` có `byte[] data` là toàn bộ ClassFile đã được đọc từ một nguồn đáng tin:

~~~java
@Override
protected Class<?> findClass(String binaryName)
        throws ClassNotFoundException {
    byte[] data = readClassFileBytes(binaryName);
    if (data == null) {
        throw new ClassNotFoundException(binaryName);
    }
    return defineClass(binaryName, data, 0, data.length);
}
~~~

`readClassFileBytes(...)` trong fragment là method **chưa được định nghĩa**, minh họa một lớp loader sẽ đọc file/JAR/resource/memory; đây **không phải code độc lập để compile**. Những điểm cần giữ:

- Chuyển binary name sang đường dẫn một cách có kiểm soát, ví dụ `binaryName.replace('.', '/') + ".class"` khi đọc JAR/classpath riêng. Đường dẫn này là **tên entry/đường dẫn**, không phải tham số `name` cho `defineClass`.
- Nếu không tìm thấy bytecode, ném `ClassNotFoundException` với binary name và lưu nguyên nhân I/O nếu có, không nuốt lỗi rồi trả `null`.
- Nếu `byte[] data` là toàn bộ payload, `off = 0` và `len = data.length`.
- Không dùng `InputStream.available()` làm độ dài toàn bộ ClassFile; nó chỉ phản ánh số byte có thể đọc không chặn theo hợp đồng của stream, **không bảo đảm kích thước nội dung**. Với Java 17, `readAllBytes()` phù hợp cho file có kích thước kiểm soát và chính sách giới hạn hợp lý.
- Không dùng độ dài string sau khi decode bytecode thành text. ClassFile là **nhị phân**, không phải chuỗi UTF-8 chứa source Java.
- Với dữ liệu container có prefix/suffix, truyền đúng vùng hoặc tạo `Arrays.copyOfRange(container, off, off + len)` rồi `defineClass(name, slice, 0, slice.length)`. Cách copy dễ tránh sai offset nhưng dùng thêm bộ nhớ; cần kiểm tra chỉ số đầu/cuối trước khi copy.

**Lưu ý lớp trùng:** `findClass` chỉ nên định nghĩa class khi chưa có định nghĩa theo chính sách `loadClass`. Nếu tự bỏ qua `findLoadedClass` và định nghĩa lại cùng binary name trong một loader, JVM có thể ném `LinkageError`. Không cố dùng `defineClass` để ghi đè lớp đã nạp.

## 6. Những lỗi và exception cần phân biệt

| Biểu hiện | Nguyên nhân điển hình | Hướng kiểm tra |
| --- | --- | --- |
| `IndexOutOfBoundsException` | `off`/`len` âm hoặc khoảng vượt mảng. | Xem `off`, `len`, `b.length`; kiểm tra tính toán không overflow. |
| `ClassFormatError` | Dãy byte không tạo thành ClassFile hợp lệ: sai magic, hỏng constant pool, bị cắt ngắn, dữ liệu rác. | So lại 4 byte đầu `CAFEBABE` và đúng ranh giới payload. |
| `NoClassDefFoundError` kèm *wrong name* | `name` yêu cầu khác tên `this_class` trong ClassFile. | So binary name được yêu cầu với tên thực trong bytecode, dùng `javap -v`. |
| `UnsupportedClassVersionError` | Bytecode target phiên bản JVM chưa hỗ trợ. | Kiểm tra `major_version` trong ClassFile và bản Java chạy. |
| `SecurityException` | Có thể vi phạm quy tắc định nghĩa package bị cấm hoặc ràng buộc certificate/package. | Xem package, signing, ProtectionDomain và thiết kế nguồn code. |
| `LinkageError` | Ví dụ định nghĩa trùng trong cùng loader hoặc xung đột liên kết giữa các định nghĩa kiểu. | Kiểm tra `findLoadedClass`, đồng bộ loader, namespace và delegation. |
| `ClassNotFoundException` | `findClass` không tìm được dữ liệu cần thiết; thường ở đường tìm lớp, không phải phép parse ClassFile. | Xem chuyển binary name thành đường dẫn, nguồn bytes và cause I/O. |

Một số lỗi như `UnsupportedClassVersionError` và `NoClassDefFoundError` cũng thuộc họ `LinkageError`, nhưng có ý nghĩa chẩn đoán cụ thể hơn. Đừng mặc định mọi lỗi `NoClassDefFoundError` đều do sai tên: nó còn có thể do dependency bị thiếu hoặc lớp ở trạng thái khởi tạo lỗi trước đó. Hãy đọc **toàn bộ thông báo và cause**.

### Kiểm tra header nhanh

Một ClassFile hợp lệ có bốn byte đầu `CA FE BA BE`:

~~~java
boolean plausibleMagic = length >= 4
        && (data[offset] & 0xFF) == 0xCA
        && (data[offset + 1] & 0xFF) == 0xFE
        && (data[offset + 2] & 0xFF) == 0xBA
        && (data[offset + 3] & 0xFF) == 0xBE;
~~~

Đây là **fragment**, giả sử offset/length đã được kiểm tra hợp lệ trong data. `plausibleMagic` chỉ là phép **sanity check**; nó không thay JVM verifier và không bảo đảm phần còn lại của ClassFile hợp lệ hay đáng tin.

Với bytecode thiếu/không khớp phiên bản, có thể dùng:

~~~bash
javap -v lab.generated.Echo
~~~

khi `lab.generated.Echo` đã được biên dịch vào đúng classpath và không có xung đột tên. `javap -v` hiển thị major version, this_class, constant pool và cấu trúc giúp đối chiếu tên/phiên bản. Với custom container, có thể trích đúng ClassFile ra tệp trước khi kiểm tra.

## 7. ByteBuffer overload có giải quyết được lỗi offset không?

Ngoài overload dùng `byte[]`, `ClassLoader` còn có phương thức `defineClass(String, ByteBuffer, ProtectionDomain)` và các overload liên quan. Với `ByteBuffer`, cần hiểu chính xác vùng dữ liệu dùng để định nghĩa class theo **position/limit/remaining** của buffer và hợp đồng API thay vì đoán từ `capacity`.

Nếu dữ liệu nhận từ network/NIO, pattern thường là chuẩn bị buffer chỉ chứa payload hoặc tạo một **slice** với vị trí/giới hạn đúng, sau đó gọi overload phù hợp. Nhưng dùng ByteBuffer **không tự sửa** lỗi sai binary name, dữ liệu hỏng, sai ranh giới, hoặc không nhất quán về ProtectionDomain.

Đối với `byte[]`, điều quan trọng vẫn là tính toán:

~~~text
0 <= off
0 <= len
off + len <= b.length  (kiểm tra sao cho không overflow)
payload = b[off ... off+len)
~~~

Với input không tin cậy, nên đặt giới hạn kích thước payload, bảo đảm nguồn bytes đã được giải nén/xác thực đầy đủ, và không dùng dữ liệu mutable chung mà thread khác có thể sửa trong lúc định nghĩa lớp. Có thể copy sang mảng sở hữu riêng trước khi gọi `defineClass` nếu nguồn bytecode có thể bị thay đổi đồng thời.

## 8. Tên, package và quyền định nghĩa lớp

`defineClass` không chỉ kiểm tra cú pháp bốn tham số. JVM và ClassLoader còn áp dụng các quy tắc về:

- **Package:** một class thuộc package theo tên nhị phân thực tế. Một số package như `java.*` không được class loader tùy chỉnh định nghĩa thông thường.
- **ProtectionDomain:** overload đơn giản sử dụng miền bảo vệ mặc định gắn với loader theo hợp đồng API; khi tải code với nguồn/quyền truy cập khác nhau có thể cần overload với `ProtectionDomain` rõ ràng.
- **Certificate/signing consistency:** các class trong cùng package của một defining loader có thể phải tuân thủ điều kiện nhất quán về certificate theo `ClassLoader` API.
- **Type identity:** cùng binary name nhưng khác defining loader là hai kiểu JVM khác nhau. Không dùng `defineClass` hai lần ở hai loader để giả vờ tạo bản thứ hai của interface API dùng chung.
- **Class initialization:** `defineClass` không đồng nghĩa với việc chạy static initializer của kiểu vừa định nghĩa. Thời điểm khởi tạo tuân theo quy tắc JLS/JVMS.

Nếu cần xây hệ plugin với child-first, nên phân biệt package API chung (ủy quyền parent) và package implementation riêng (loader plugin sở hữu). Việc truyền đúng tên/byte range là điều kiện cần, nhưng **chưa đủ** để làm custom loader an toàn và tương thích mọi dependency.

## 9. Những anti-pattern dễ mắc

**Dùng `buffer.length` làm `len` dù có prefix:**

~~~java
// Sai nếu có header/trailer bên ngoài ClassFile:
defineClass(name, container, prefixSize, container.length);
~~~

`container.length` bao gồm bytes không thuộc payload, dễ vượt giới hạn mảng hoặc đi sai ClassFile. Dùng đúng `payloadLength`.

**Dùng chỉ số cuối làm độ dài:**

~~~java
// Sai nếu endExclusive là chỉ số kết thúc:
defineClass(name, container, start, endExclusive);
~~~

Đúng là `endExclusive - start` khi chỉ số kết thúc là exclusive.

**Dùng offset đúng nhưng name sai:**

~~~java
// Sai nếu ClassFile khai báo pkg.Widget, không phải pkg.Other:
defineClass("pkg.Other", bytes, 0, bytes.length);
~~~

Không thể đổi tên class bằng cách sửa chuỗi `name`.

**Đọc file nhị phân thành String rồi quay lại byte[]:**

~~~java
// Sai ý tưởng: ClassFile không phải văn bản UTF-8 của source code.
String source = new String(classBytes, java.nio.charset.StandardCharsets.UTF_8);
byte[] converted = source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
~~~

Làm vậy có thể hỏng byte không hợp lệ cho UTF-8, thay đổi nội dung và gây `ClassFormatError`. Giữ dữ liệu nhị phân nguyên vẹn.

**Tự gộp nhiều ClassFile thành một mảng rồi define cả mảng:** JVM chỉ mong **một ClassFile hợp lệ** cho mỗi lần `defineClass`. Phải bóc đúng entry trước khi gọi.

**Giả định defineClass luôn trả về class có thể sử dụng được:** ClassFile có thể định nghĩa thành công nhưng lỗi linking/resolution/dependency hoặc initialization chỉ phát sinh lúc dùng về sau. Cần phân biệt lỗi ở từng pha và log nguyên nhân.

## 10. Checklist kiểm tra trước khi gọi defineClass

1. **Binary name:** có đúng `pkg.Type` theo tên trong ClassFile không? Không có `.class` hoặc dấu `/` của internal name.
2. **Dữ liệu đầu vào:** byte array có chứa **đầy đủ một** ClassFile chưa bị đổi, nén hoặc cắt mất dữ liệu?
3. **Ranh giới:** `off` là chỉ số bắt đầu; `len` là số byte payload; `off + len` không vượt `b.length`.
4. **Header:** nếu cần debug, bốn byte bắt đầu vùng payload có phải `CA FE BA BE`?
5. **Version:** ClassFile target có được JVM hiện hành hỗ trợ không?
6. **Class loader:** đã có cùng binary name được định nghĩa trong loader này chưa? Parent delegation và chính sách `findLoadedClass` có phù hợp?
7. **Package/ProtectionDomain:** có vi phạm package/certificate/security rule không?
8. **Đồng thời:** bytes có thể bị thread khác sửa trong lúc define không? Nếu có, copy thành buffer riêng.
9. **Quan sát lỗi:** phân biệt `ClassFormatError`, `NoClassDefFoundError`, `IndexOutOfBoundsException`, `UnsupportedClassVersionError` và các vấn đề linking/initialization.

## 11. Tự kiểm tra kiến thức

**Câu 1.** Mảng dài 1024 byte, ClassFile bắt đầu tại offset 32 và dài 800 byte. Phải gọi thế nào?

**Trả lời:** `defineClass(name, b, 32, 800)`. Khoảng được đọc là `[32, 832)`, không phải `[32, 800)`.

**Câu 2.** Nếu truyền `len = 832` thay vì 800 thì có đúng vì 832 là `endExclusive`?

**Trả lời:** Không. `len` là **độ dài**, không phải chỉ số kết thúc. Nếu tính từ off 32, đoạn đó sẽ dài 832 byte và kết thúc tại 864.

**Câu 3.** Bytecode tự khai báo `com.demo.Service`; gọi `defineClass("com/demo/Service", bytes, 0, bytes.length)` có chuẩn không?

**Trả lời:** Không. Tham số `name` cần dùng **binary name** `com.demo.Service`, không phải internal name `com/demo/Service`.

**Câu 4.** ClassFile đúng hoàn toàn nhưng loader khai báo tên khác. Lỗi thường là gì?

**Trả lời:** `NoClassDefFoundError` với thông điệp *wrong name*, vì tên yêu cầu không khớp với tên bên trong bytecode.

**Câu 5.** `byte[]` có đúng bốn byte magic nhưng `len` thiếu một byte cuối. Có đủ để define thành công không?

**Trả lời:** Không. Magic chỉ là kiểm tra sơ bộ, còn định dạng ClassFile cần đầy đủ nội dung. JVM có thể ném `ClassFormatError`.

**Câu 6.** Mỗi loader định nghĩa một `com.example.Api` từ bytecode giống nhau. Hai kiểu có tự hợp nhất thành một không?

**Trả lời:** Không. Type identity bao gồm defining loader; cần dùng chung API contract qua loader phù hợp nếu hai bên muốn cast trực tiếp.

## Ghi nhớ

`defineClass(name, b, off, len)` đòi hỏi **đúng tên nhị phân** và **đúng vùng bytes của một ClassFile**. `off` là byte bắt đầu (zero-based), `len` là số byte cần đọc, không phải offset kết thúc hay capacity. Với mảng chỉ có một ClassFile, dùng `off=0` và `len=b.length`; với buffer chứa prefix/suffix, phải dùng ranh giới payload thực tế. Sau đó còn cần lưu ý package, version, ProtectionDomain, parent delegation và vòng đời class loader.

**Tài liệu chính thức:**

- Java SE 17 API, `ClassLoader#defineClass`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java Virtual Machine Specification, Java SE 17, §4.1 — *The ClassFile Structure*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.1
- Java Virtual Machine Specification, Java SE 17, §5.3 — *Creation of Classes and Interfaces*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- Java SE 17 API, `JavaCompiler`: https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/tools/JavaCompiler.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-007">Định nghĩa package và nguồn mã khi cần</a>

<details>
<summary>Xem chi tiết</summary>

# Định nghĩa package và nguồn mã khi cần trong ClassLoader tùy chỉnh

> **Bối cảnh:** Java Core → ClassLoader → Loader tùy chỉnh. Khi tự đọc bytecode từ thư mục, JAR, kho plugin hoặc bộ sinh mã, bạn không chỉ quyết định **class nào được nạp** mà đôi khi còn cần gắn **thông tin package** (phiên bản, vendor, package sealing) và **nguồn mã — CodeSource** cho các class được định nghĩa. Bài học giải thích khi nào cần `definePackage`, cách phối hợp `SecureClassLoader`/`ProtectionDomain`, sự khác biệt giữa metadata và cơ chế bảo vệ thật, và các quy tắc không được phá vỡ. Ví dụ dùng Java 17+.

## 1. Vấn đề: defineClass thành công nhưng metadata ở đâu?

Một custom loader đơn giản thường trông như sau:

```java
// Snippet bên trong một subclass của ClassLoader:
byte[] bytes = readClassBytes(binaryName);
return defineClass(binaryName, bytes, 0, bytes.length);
```

Ở đây `readClassBytes` là helper do ứng dụng tự xây; snippet **không độc lập**. Nó có thể đủ nếu chỉ muốn nạp class thường. Nhưng sau khi định nghĩa `sample.plugin.Task`, những thông tin sau có thể quan trọng:

- `Task.class.getPackage().getSpecificationVersion()` báo phiên bản đặc tả package nào?
- `Task.class.getPackage().getImplementationVersion()` phản ánh bản release của plugin nào?
- Package có được **sealed** để tránh lẫn class từ nguồn khác không?
- `Task.class.getProtectionDomain().getCodeSource()` có thể cho biết class do nguồn nào cung cấp không?
- Nếu JAR có chữ ký, các class trong **cùng package của cùng loader** có tuân thủ quy tắc certificates nhất quán không?

**Câu trả lời:** `defineClass` tự tạo `Package` mặc định cho tên package nếu loader chưa có, nhưng metadata đặc thù **không tự phát sinh từ một tệp JAR manifest** chỉ nhờ bạn đọc bytes rồi gọi `defineClass`. Nếu ứng dụng muốn các thuộc tính cụ thể, hãy cấu hình chúng **trước lần định nghĩa class đầu tiên trong package**; nếu cần nguồn mã, hãy dùng overload `defineClass` nhận `ProtectionDomain` hoặc `SecureClassLoader.defineClass` nhận `CodeSource`.

Không phải custom loader nào cũng cần làm mọi việc này: chỉ thêm thông tin/cơ chế khi có use case rõ ràng và có thể duy trì tính nhất quán.

## 2. Phân biệt ba khái niệm không nên trộn lẫn

| Khái niệm | Được đại diện bằng | Trả lời câu hỏi |
| --- | --- | --- |
| **Java package metadata** | `java.lang.Package` | Tên package, specification/implementation title/version/vendor, tình trạng sealed, annotations nếu có. |
| **Code source** | `java.security.CodeSource` | Location URL và tập certificates/signers liên quan đến nguồn code (nếu được cung cấp/xác thực). |
| **Protection domain** | `java.security.ProtectionDomain` | Nhóm metadata security domain gồm code source, class loader, permission collection/principals theo cấu hình/constructor. |

Một `Class<?>` có thể biết package của nó trong khi `ProtectionDomain.getCodeSource() == null`. Ngược lại, một class có `CodeSource` nhưng package metadata phiên bản trống. Hai nhóm thông tin **không tự thay thế nhau**.

Thứ tự khuyến nghị của custom loader:

```text
1. Nhận yêu cầu loadClass(binaryName)
2. Kiểm tra đã load và thử parent theo policy
3. Trong findClass:
      - tìm đúng bytes của một class file
      - xác định package name, manifest metadata, source URL
      - kiểm tra/định nghĩa Package nếu cần
      - tạo CodeSource hoặc ProtectionDomain phù hợp
      - defineClass(binaryName, bytes, domain/source)
4. JVM liên kết/khởi tạo khi quy tắc tương ứng yêu cầu
```

`definePackage`, `defineClass` và `resolveClass` là những bước **khác nhau**; việc gọi `definePackage` không hề “load” tất cả lớp trong package.

## 3. Package là gì trong runtime đa ClassLoader?

Ở source, các lớp có thể khai báo:

```java
package sample.plugin;
```

Nhưng tại JVM, **runtime package** được phân biệt bằng **tên package + defining class loader**. Hai loader khác nhau đều có thể định nghĩa một package tên `sample.plugin` và mỗi loader quản lý `Package` của riêng mình.

Do đó:

```text
Loader A defines sample.plugin.Task
    -> (binary name sample.plugin.Task, defining loader A)
    -> package sample.plugin của loader A

Loader B defines sample.plugin.Task
    -> (binary name sample.plugin.Task, defining loader B)
    -> package sample.plugin của loader B

A != B: hai runtime types khác nhau, hai runtime packages khác nhau
```

Hệ quả quan trọng:

- `package-private` không tự động truy cập được giữa hai lớp chỉ vì string `package` giống nhau.
- Cùng tên class do hai loader định nghĩa là **hai loại runtime** khác nhau; `ClassCastException` có thể xảy ra khi ép object giữa chúng.
- `getDefinedPackage("sample.plugin")` chỉ hỏi các package được **loader nhận câu hỏi trực tiếp định nghĩa**, không quét ngược parent.
- `getPackage(String)` của `ClassLoader` đã **deprecated từ Java 9**, bởi nó có thể tìm cả parent và cho metadata package không đúng lớp định nghĩa; ưu tiên `getDefinedPackage`.
- Để hỏi `Package` gắn với một class cụ thể, dùng `clazz.getPackage()` thay vì cố suy luận qua system loader.

### Package object không phải directory hay classpath root

`Package` là metadata runtime, **không phải** một thư mục `sample/plugin` được tạo trên disk. `definePackage` không thêm đường tìm JAR; và `getDefinedPackage` không cho biết JAR nào chứa mọi class package ấy.

## 4. Khi nào JVM tự tạo Package?

API `ClassLoader.defineClass(String, byte[], int, int, ProtectionDomain)` và các overload liên quan có cơ chế bảo đảm package phù hợp tên class được định nghĩa. Nếu loader chưa có `Package` cho tên đó, việc định nghĩa class có thể tạo **package mặc định** với các thuộc tính metadata theo quy tắc `Package`.

Vì vậy loader tối giản **không bắt buộc** gọi `definePackage` trước mọi `defineClass`.

Tuy nhiên, nếu bạn muốn specification title, version, implementation vendor hoặc `sealBase` được thiết lập theo một manifest, **phải định nghĩa tường minh package trước khi `defineClass` lần đầu ở package đó**. Đừng dựa vào việc “sau khi nạp vài class rồi mới cập nhật”.

Ví dụ:

```text
defineClass("sample.plugin.First", bytesFirst, ...)
       -> nếu package sample.plugin chưa tồn tại:
          package metadata mặc định được tạo

definePackage("sample.plugin", ... metadata cần có ...)
       -> quá muộn: không thể định nghĩa lại cùng package của loader
```

Một package name chỉ được định nghĩa **một lần trong một loader** và không thể thay đổi các thuộc tính bằng `definePackage` về sau. Vì thế quyết định metadata lúc load class đầu tiên là một phần thiết kế loader, không phải việc “vá nhãn” sau khi đã nạp.

## 5. definePackage: phiên bản, vendor và sealing

Overload được dùng phổ biến:

```java
protected Package definePackage(
        String name,
        String specTitle,
        String specVersion,
        String specVendor,
        String implTitle,
        String implVersion,
        String implVendor,
        java.net.URL sealBase);
```

`definePackage` là method `protected` trên `ClassLoader`: subclass có thể gọi từ logic nội bộ; mã ở ngoài không được tự do gọi nếu không có quyền `protected` thích hợp.

Các tham số:

| Tham số | Ý nghĩa |
| --- | --- |
| `name` | Tên package kiểu dấu chấm, ví dụ `sample.plugin`. |
| `specTitle` | Tên specification. |
| `specVersion` | Phiên bản specification; `Package.isCompatibleWith` phân tích theo quy tắc số dot-separated. |
| `specVendor` | Nhà cung cấp specification. |
| `implTitle` | Tên implementation. |
| `implVersion` | Chuỗi phiên bản implementation, do bên đóng gói định nghĩa. |
| `implVendor` | Nhà cung cấp implementation. |
| `sealBase` | URL nguồn mà package được đóng dấu **sealed**; `null` nghĩa package không sealed. |

Một ví dụ trong custom loader:

```java
definePackage(
    "sample.plugin",
    "Demo API", "1.0", "Demo",
    "Plugin Impl", "2.0", "Demo",
    null);
```

Sau đó, với lớp `sample.plugin.Task` do loader ấy định nghĩa:

```java
Package p = taskClass.getPackage();
System.out.println(p.getSpecificationTitle()); // Demo API
System.out.println(p.getImplementationVersion()); // 2.0
System.out.println(p.isSealed()); // false
```

Đây là **hai snippet cần nằm trong subclass và caller tương ứng**; `taskClass` phải được định nghĩa trước. Mục 7 có một chương trình độc lập có thể chạy thật.

### Giới hạn của phiên bản

`getImplementationVersion()` chỉ trả **chuỗi metadata** do loader đặt; JVM không đảm bảo đó là phiên bản Maven/SemVer hoặc trùng mã đang chạy. Với `getSpecificationVersion()`, `Package.isCompatibleWith(String)` so sánh các thành phần phiên bản dạng số và có thể ném `NumberFormatException` nếu format không phù hợp. Đừng dùng `Package` metadata như công cụ dependency resolver hay làm điều kiện an toàn để chấp nhận code không tin cậy.

### Không được redefine cùng package

Nếu gọi `definePackage` lần nữa cho cùng `name` trên cùng loader, method có thể ném `IllegalArgumentException` do package đã được định nghĩa. Với loader song song, hai thread cùng chuẩn bị metadata package cần đồng bộ thích hợp, tránh race giữa `getDefinedPackage` và `definePackage`.

## 6. Package sealing: nó bảo đảm gì, và custom loader cần làm gì?

Một `Package` được **sealed** khi `sealBase` khác `null`. Về ý nghĩa đóng gói JAR, các class trong package sealed phải thuộc đúng nguồn archive được đánh dấu. Kiểm tra metadata:

```java
Package p = someClass.getPackage();
boolean sealed = p.isSealed();
boolean fromExpectedBase = p.isSealed(expectedJarUrl);
```

`someClass` và `expectedJarUrl` là biến trong chương trình có thật; snippet minh họa API.

**Điểm thường bị hiểu sai:** gán `sealBase` vào `definePackage` chỉ tạo thuộc tính metadata sealed cho `Package`. Nếu **bạn tự viết loader** và tự gọi `defineClass` từ raw bytes, bạn phải tự quản lý việc xác định nguồn, kiểm tra package sealing và consistency theo thiết kế loader; không nên giả định mọi logic so khớp nguồn JAR/manifest của `URLClassLoader` sẽ tự được tái hiện cho loader của bạn.

Quy tắc kiểm tra một cách **minh họa** trước khi define thêm class từ `sourceUrl`:

```java
// Đặt trong loader, trước defineClass, với:
// packageName, sourceUrl và shouldBeSealed đã được xác định từ nguồn đáng tin.
Package pkg = getDefinedPackage(packageName);
if (pkg == null) {
    definePackage(packageName,
            null, null, null, null, null, null,
            shouldBeSealed ? sourceUrl : null);
} else if (pkg.isSealed()) {
    if (!pkg.isSealed(sourceUrl)) {
        throw new SecurityException("Package sealing violation");
    }
} else if (shouldBeSealed) {
    throw new SecurityException("Cannot seal already-unsealed package");
}
```

Đây là **snippet đặt trong subclass**, với ba biến `packageName`, `sourceUrl`, `shouldBeSealed` từ policy của ứng dụng. Trong sản phẩm, cần cùng khóa đồng bộ khi kiểm tra và khi nạp class, xác định package metadata một lần, và tránh lẫn certificate sets/source policy giữa các JAR.

Khi dùng `URLClassLoader`, nó có triển khai xử lý manifest/package metadata và package sealing khi nạp từ JAR; không nên xây lại nếu chỉ cần loader chuẩn. **Sealing không thay thế quyền truy cập module, chữ ký JAR hoặc isolation giữa các defining loaders.**

## 7. Nguồn mã: CodeSource và ProtectionDomain

### CodeSource gồm những gì?

`java.security.CodeSource` chứa:

- **Location URL** mô tả nguồn gắn với class, thường là JAR URL hoặc directory URL.
- **Certificates/signers** gắn với code, khi có và được xác thực phù hợp.

Ví dụ:

```java
java.net.URL origin = Path.of("build/plugin")
        .toAbsolutePath().toUri().toURL();

java.security.CodeSource source = new java.security.CodeSource(
        origin, (java.security.cert.Certificate[]) null);
```

Đây là snippet cần `import java.nio.file.Path;` (hoặc tên đầy đủ như trong ví dụ), không phải class có `main`. `origin` ở đây mô tả **nơi ứng dụng cho rằng bytes đến từ**. **Việc tự đặt CodeSource URL không chứng minh bytes thực sự được tải từ nguồn ấy**; bạn phải đọc/kiểm tra chúng đúng quy trình.

### Gắn nguồn qua SecureClassLoader

`java.security.SecureClassLoader` mở rộng `ClassLoader` và có overload:

```java
protected final Class<?> defineClass(
        String name, byte[] b, int off, int len, CodeSource cs);

protected final Class<?> defineClass(
        String name, java.nio.ByteBuffer b, CodeSource cs);
```

Nếu truyền `CodeSource` khác `null`, `SecureClassLoader` tạo `ProtectionDomain` thích hợp cho class được định nghĩa. Bạn có thể kiểm tra:

```java
ProtectionDomain pd = clazz.getProtectionDomain();
CodeSource cs = pd.getCodeSource();
URL url = cs == null ? null : cs.getLocation();
```

Đây là snippet, `clazz` là `Class<?>` được loader định nghĩa. Trường hợp không có nguồn mã rõ ràng, `cs` có thể `null`. Không bao giờ mặc định `clazz.getProtectionDomain().getCodeSource().getLocation()` luôn có giá trị — tránh `NullPointerException`.

### Gắn bằng ClassLoader với ProtectionDomain

Nếu extends `ClassLoader` thay vì `SecureClassLoader`, có thể dùng:

```java
defineClass(
        binaryName, bytes, 0, bytes.length, protectionDomain);
```

`protectionDomain` là `java.security.ProtectionDomain` do ứng dụng tạo hoặc quản lý, có thể chứa `CodeSource` và các thông tin liên quan. Nếu `null`, `defineClass` dùng default domain theo Javadoc. **Không phải mọi custom loader phải tự chế tạo ProtectionDomain**; chỉ làm khi metadata và quyền hạn thực sự cần kiểm soát.

### CodeSource không phải sandbox bảo mật tự động

Trong Java 17, `SecurityManager` đã bị deprecated for removal; từ JDK 24, Security Manager đã bị vô hiệu hóa vĩnh viễn theo JEP 486. Không dùng `ProtectionDomain` hay `CodeSource` như một lời hứa rằng bytecode plugin không tin cậy đã bị sandbox theo security policy cũ. Chúng vẫn là API metadata và các ràng buộc liên quan, nhưng isolation an toàn cho code không tin cậy có thể cần **process boundary**, container hoặc giải pháp có mô hình threat phù hợp.

## 8. Thực hành đầy đủ: metadata Package + CodeSource

Ví dụ sau tạo hai custom loader riêng, cùng đọc bytecode `sample.plugin.Task`:

- Loader thứ nhất **không sealed**, metadata specification/implementation được gắn trước khi định nghĩa class.
- Loader thứ hai **sealed** tại cùng URL nguồn.
- Mỗi loader tạo **runtime class và Package riêng**.
- Mỗi class lưu CodeSource URL đã khai báo thông qua `SecureClassLoader`.

Không dùng thư viện ngoài JDK và không cần file JAR vật lý; `origin` được gắn là URL của thư mục `build/plugin`, đúng với nơi ví dụ đọc class bytes.

### Cấu trúc source

```text
src/
  sample/
    plugin/
      Task.java
  app/
    PackageSourceDemo.java
build/
  plugin/        (javac tạo)
  app/           (javac tạo)
```

**File `src/sample/plugin/Task.java`:**

```java
package sample.plugin;

public class Task {
    public String message() {
        return "hello";
    }
}
```

**File `src/app/PackageSourceDemo.java`:**

```java
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;

public class PackageSourceDemo {
    static final class PluginLoader extends SecureClassLoader {
        private final Path root;
        private final URL origin;
        private final boolean sealed;

        PluginLoader(Path root, URL origin, boolean sealed) {
            super(ClassLoader.getPlatformClassLoader());
            this.root = root;
            this.origin = origin;
            this.sealed = sealed;
        }

        @Override
        protected Class<?> findClass(String binaryName)
                throws ClassNotFoundException {
            Path path = root.resolve(
                    binaryName.replace('.', '/') + ".class");

            final byte[] bytes;
            try {
                bytes = Files.readAllBytes(path);
            } catch (IOException ex) {
                throw new ClassNotFoundException(binaryName, ex);
            }

            int dot = binaryName.lastIndexOf('.');
            if (dot >= 0) {
                String pkgName = binaryName.substring(0, dot);
                if (getDefinedPackage(pkgName) == null) {
                    definePackage(
                            pkgName, "Demo API", "1.0", "Demo",
                            "Plugin Impl", "2.0", "Demo",
                            sealed ? origin : null);
                }
            }

            CodeSource codeSource = new CodeSource(
                    origin, (Certificate[]) null);

            return defineClass(
                    binaryName, bytes, 0, bytes.length, codeSource);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("build/plugin").toAbsolutePath();
        URL origin = root.toUri().toURL();

        PluginLoader open = new PluginLoader(root, origin, false);
        Class<?> first = Class.forName(
                "sample.plugin.Task", false, open);
        Package pkg = first.getPackage();

        System.out.println("class defined by custom loader: "
                + (first.getClassLoader() == open));
        System.out.println("package title: "
                + pkg.getSpecificationTitle());
        System.out.println("package version: "
                + pkg.getImplementationVersion());
        System.out.println("unsealed: " + !pkg.isSealed());
        System.out.println("code source matches: "
                + origin.equals(first.getProtectionDomain()
                        .getCodeSource().getLocation()));
        System.out.println("repeated lookup same class: "
                + (Class.forName("sample.plugin.Task", false, open)
                        == first));

        PluginLoader sealed = new PluginLoader(root, origin, true);
        Class<?> second = Class.forName(
                "sample.plugin.Task", false, sealed);

        System.out.println("sealed: "
                + second.getPackage().isSealed());
        System.out.println("sealed at origin: "
                + second.getPackage().isSealed(origin));
        System.out.println("different runtime classes: "
                + (first != second));
        System.out.println("different Package objects: "
                + (first.getPackage() != second.getPackage()));
    }
}
```

### Cách chạy

Đứng trong thư mục gốc có `src`, chạy:

```bash
javac -d build/plugin src/sample/plugin/Task.java
javac -d build/app src/app/PackageSourceDemo.java
java -cp build/app PackageSourceDemo
```

**Stdout đã được biên dịch và đối chiếu trên OpenJDK 24; code chỉ dùng API hiện diện từ Java 17 trở về trước:**

```text
class defined by custom loader: true
package title: Demo API
package version: 2.0
unsealed: true
code source matches: true
repeated lookup same class: true
sealed: true
sealed at origin: true
different runtime classes: true
different Package objects: true
```

### Giải thích kết quả

1. `build/plugin` **không nằm trên `-cp build/app`**, vì vậy parent platform loader không định nghĩa `sample.plugin.Task` từ đó. `PluginLoader.findClass` nhận tên từ `Class.forName(name, false, loader)` và tự đọc bytes.
2. `getDefinedPackage(pkgName) == null` cho phép package metadata được khai báo trước khi `defineClass`. Hai giá trị `Demo API`, `2.0` xuất hiện đúng ở `clazz.getPackage()`.
3. `CodeSource(origin, null)` gắn một URL nguồn mà ví dụ cũng thật sự dùng để đọc bytes. `ProtectionDomain.getCodeSource().getLocation()` trả URL phù hợp. Ví dụ **không kiểm tra chữ ký** vì nguồn class test không có certificates.
4. `Class.forName(..., false, open)` lần tiếp theo trả **cùng `Class<?>`** trên cùng loader.
5. Loader thứ hai gắn `sealBase = origin`, nên `Package.isSealed()` và `isSealed(origin)` trả `true`. Thí nghiệm chứng minh metadata package; nó **không kiểm tra package sealing across multiple source JARs**.
6. Hai loader độc lập cho phép cùng binary name nhưng hai kiểu runtime khác nhau, cùng hai đối tượng `Package` riêng. **Không** suy ra chỉ vì chúng có CodeSource URL giống nhau thì type identity cũng giống nhau.

### Những giới hạn của ví dụ khi triển khai thực tế

`findClass` này chỉ là phiên bản đơn giản một lớp/package: nó chưa có cache/manifest parser, certificate verification, kiểm tra package sealing khi nạp nhiều nguồn, giới hạn kích thước file, chính sách nguồn cho phép hoặc đăng ký parallel-capable. Với nhiều class cùng package và concurrent loading, `getDefinedPackage` + `definePackage` phải được bảo vệ trong đúng cơ chế khóa và tránh `IllegalArgumentException` do race.

Không dùng nguyên loader này để chạy bytecode không tin cậy; `CodeSource` không biến nó thành sandbox.

## 9. Metadata từ JAR manifest: nên làm đúng thế nào?

Khi class đến từ JAR, những thuộc tính tương ứng có thể khai báo trong `META-INF/MANIFEST.MF`:

```text
Manifest-Version: 1.0
Specification-Title: Demo API
Specification-Version: 1.0
Specification-Vendor: Demo
Implementation-Title: Plugin Impl
Implementation-Version: 2.0
Implementation-Vendor: Demo
Sealed: false
```

Đây là **ví dụ nội dung manifest**, không phải output JVM. Manifest còn cho phép **package-specific sections**, thường có dạng:

```text
Name: sample/plugin/
Specification-Title: Plugin API
Implementation-Version: 2.1
Sealed: true
```

Khi loader tùy chỉnh phân tích manifest, cần xác định chính sách các attributes theo package section hoặc main section của manifest theo JAR Specification. Tên section dùng **dấu slash** như entry name, trong khi `definePackage` dùng **dấu chấm** trong tên package. `Sealed: true` yêu cầu seal base phù hợp URL **nguồn archive**, không phải tùy tiện dùng URL tới `.class` entry.

Một loader quản lý JAR nên:

1. Mở JAR một cách có kiểm soát, xác định chính xác nguồn class bytes.
2. Đọc manifest/metadata cho package liên quan, xét scope của main/section attributes.
3. Trước lần define đầu tiên, `definePackage` với metadata thích hợp.
4. Với package sealed, kiểm tra nguồn của các class tiếp theo trong **cùng loader/package** khớp seal base.
5. Đưa `CodeSource` gắn với **nguồn thật** của bytes; nếu có chữ ký, chỉ công nhận certificates đã được kiểm tra theo cơ chế JarFile/JarVerifier thích hợp.

Nếu chỉ cần nạp từ URL/JAR thông thường và muốn manifest metadata/sealing hoạt động theo cơ chế chuẩn, **ưu tiên `URLClassLoader`** — nó đã được xây cho đường tìm URL/JAR cùng metadata packaging. Tự xử lý `JarFile` và gọi `defineClass` trên bytes từ entry nghĩa là **bạn sở hữu phần trách nhiệm còn lại**, không phải đã nhận miễn phí toàn bộ behavior của `URLClassLoader`.

### Không nhầm CodeSource với manifest title/version

Manifest metadata như `Implementation-Version` là **thông tin package**. `CodeSource` chủ yếu liên quan **location và certificates**. Bạn có thể có package version `2.0` và `CodeSource` URL `file:/.../plugin.jar` cùng lúc; chúng không phải cùng một thuộc tính, và không tự suy được cái này từ cái kia qua API `defineClass`.

## 10. Certificates, signing và kiểm tra nhất quán package

Javadoc `ClassLoader#defineClass(..., ProtectionDomain)` quy định **class đầu tiên được định nghĩa trong một package** xác lập **tập certificates** mà những class tiếp theo **trong package do cùng loader định nghĩa** phải có. Tập certificates lấy từ `CodeSource` của `ProtectionDomain`; nếu khác sẽ gây `SecurityException`. Overload `SecureClassLoader.defineClass(..., CodeSource)` phục vụ việc tạo domain cho trường hợp này.

Đặc biệt:

- `CodeSource(origin, null)` biểu thị **không cung cấp certificates**, không có nghĩa JAR đã được ký hoặc xác minh.
- Tự tạo đối tượng `Certificate` và đưa vào `CodeSource` **không tương đương** với xác minh chữ ký archive và đảm bảo bytes là dữ liệu đã được ký.
- Nếu có nhiều JAR cung cấp class trong một package, loader phải giữ **certificate sets thống nhất**. Chỉ thay `URL` source không nhất thiết gây certificate mismatch nếu certificate sets tương thích; package sealing là kiểm tra nguồn riêng.
- Nếu cần xác minh signed JAR, hãy sử dụng cơ chế JAR verification và kiểm soát quá trình đọc toàn bộ entry trước khi tin tưởng certificate metadata phù hợp. Hãy phân biệt signing assurance, loader identity, package sealing và permissions.
- Không truyền `null` làm tên binary class chỉ để tránh một số kiểm tra namespace/certificates; Javadoc khuyến nghị **luôn cung cấp binary name** để xác nhận class vừa được định nghĩa đúng như dự kiến.

Các lỗi có thể xuất hiện:

| Lỗi | Nguyên nhân điển hình |
| --- | --- |
| `IllegalArgumentException` tại `definePackage` | Package đã được định nghĩa trong cùng loader. |
| `SecurityException` tại `defineClass` | Package certificate set không nhất quán, hoặc định nghĩa tên `java.*` từ loader không được phép. |
| `NoClassDefFoundError` dạng wrong name | `binaryName` truyền vào không khớp tên thật trong bytecode. |
| `ClassFormatError` | Dữ liệu class file không hợp lệ/cắt hỏng. |
| `LinkageError` | Cố định nghĩa trùng tên class trong cùng loader, hoặc một linkage constraint khác bị vi phạm. |
| `ClassNotFoundException` | `findClass` không tìm được/read được class bytes cần tìm. |
| `NullPointerException` khi đọc CodeSource | Giả định `getProtectionDomain().getCodeSource()` luôn khác `null`. |

Không coi mọi `SecurityException` là “JAR chưa ký”; phải đọc message, cause, ngữ cảnh class/package và chính sách được loader áp dụng.

## 11. Package metadata không thay thế module system hoặc các luật Java khác

Java 9+ có JPMS với named modules, `module-info.java`, readability, `exports` và `opens`. Các thuộc tính `Package` như `Specification-Version`, `Sealed` không phải quyền truy cập module. Một class được định nghĩa trong **unnamed module** của custom loader không tự trở thành class của một named module chỉ nhờ gọi `definePackage`.

Ngoài ra:

- **Package-private access** vẫn dựa trên runtime package gồm tên package và defining loader.
- **Exports/opens** của named module kiểm soát mức truy cập/reflective access theo module rules, không được cấp bởi manifest `Sealed`.
- **Class identity** vẫn là binary name + defining loader với class/interface thông thường; hai loader đọc cùng JAR không tạo cùng runtime type.
- **ProtectionDomain/CodeSource** không phải công cụ tự động giải quyết `IllegalAccessError` do module/package boundaries hoặc lỗi thiếu class.
- Nếu plugin muốn giao tiếp qua interface của host, đặt interface chung dưới loader cha phù hợp; nếu plugin tự define bản sao interface đó, cast sẽ có thể lỗi.

Khi package-info annotations quan trọng, cần đảm bảo `package-info.class` đến từ định nghĩa loader chính xác; `ClassLoader.getPackage(String)` deprecated có thể trả package metadata từ parent khiến bạn nhìn nhầm.

## 12. Thiết kế loader thực tế: trình tự và concurrency

Một quy trình an toàn hơn cho loader có nhiều plugin JARs:

```text
Yêu cầu loadClass("sample.plugin.Task")
    |
    v
findLoadedClass / parent delegation / class loading lock
    |
    v
Tìm JAR entry thật và xác minh bytes/nguồn
    |
    v
Xác định package từ binary name
    |
    +-- package chưa có:
    |      đọc manifest và definePackage trước class đầu tiên
    |
    '-- package đã có:
           kiểm tra metadata/sealBase/certificates policy
    |
    v
Xây CodeSource / ProtectionDomain nhất quán với source
    |
    v
defineClass(name, bytes, domain/source)
    |
    v
Trả Class<?>; JVM sẽ link/init theo semantics tương ứng
```

Các điểm cần cân nhắc:

**1. Tương tranh:** `getDefinedPackage` rồi `definePackage` không phải một transaction atomic cho toàn bộ việc đọc JAR và define class. Nếu custom loader đăng ký `registerAsParallelCapable` và nhiều thread nạp **hai class khác tên trong cùng package**, class-loading lock theo tên có thể **không ngăn race ở cấp package**. Cần đồng bộ trên package state riêng; đừng đơn giản `catch (IllegalArgumentException)` rồi nuốt mà không kiểm chứng metadata/seal base có nhất quán.

**2. Không define lại:** một loader không thể cập nhật `sample.plugin.Task` chỉ bằng `defineClass` lần hai hoặc thay `CodeSource` sau khi class đã được định nghĩa. Muốn hot reload thường tạo loader mới, quản lý object cũ và interface chung.

**3. Nguyên tắc nguồn dữ liệu:** URL của `CodeSource` phải tương ứng nguồn bytes loader đã đọc nếu bạn định dùng để chẩn đoán/chính sách. Không lấy URL từ tên người dùng rồi gắn mà không kiểm tra; metadata có thể sai hoặc bị giả mạo.

**4. Sealed packages:** một package sealed phải có một source base nhất quán; nếu loader tự thực hiện kiểm tra, cần làm **trước khi define thêm class**. Một URL khác nhưng dẫn tới cùng file theo hệ thống file cũng có thể cần chính sách canonicalization/thẩm định riêng.

**5. Loader ownership:** ai chịu trách nhiệm đóng JAR/URLClassLoader/stream? `definePackage` không quản lý archive handles. Dùng try-with-resources cho `JarFile` và entry streams khi bạn tự mở.

**6. Lỗi theo giai đoạn:** define thành công không bảo đảm resolution mọi dependency hay initialization thành công. Đừng đánh đồng lỗi metadata package với `NoClassDefFoundError` do dependency thiếu hoặc static initializer thất bại.

## 13. Những sai lầm và cách sửa

| Sai lầm | Hậu quả | Hướng sửa |
| --- | --- | --- |
| Luôn gọi `definePackage` trước mỗi `defineClass` mà không kiểm tra trạng thái | `IllegalArgumentException` khi nạp class thứ hai trong package. | Quản lý trạng thái package; khai báo một lần. |
| Định nghĩa class đầu tiên rồi mới đọc manifest metadata | Package mặc định đã tồn tại; metadata không cập nhật bằng definePackage. | Đọc metadata trước khi defineClass đầu tiên. |
| Dùng `getPackage` trên loader để xác định đã define package chưa | Có thể lấy package từ parent, sai namespace loader. | Dùng `getDefinedPackage` của loader hoặc `clazz.getPackage()`. |
| Nghĩ `Package.isSealed()` tự bảo đảm custom loader đọc từ đúng JAR | Raw-bytes loader có thể không thực hiện đầy đủ policy kiểm tra nguồn. | Thiết kế sealing checks cho mọi class nguồn hoặc dùng URLClassLoader. |
| Gắn `CodeSource` URL tùy ý rồi coi đó là bằng chứng xuất xứ | Metadata sai/không được xác minh. | Lấy từ nguồn bytes đã kiểm chứng và quản lý trust. |
| Trộn signed/unsigned classes trong cùng runtime package | Certificate-set mismatch có thể ném `SecurityException`. | Giữ provenance/certificates nhất quán; tách package/loader nếu cần. |
| Dùng ProtectionDomain như sandbox đầy đủ trên Java hiện đại | Không bảo vệ trước code không tin cậy theo cách kỳ vọng. | Dùng isolation boundary phù hợp, không dựa vào SecurityManager cũ. |
| Đưa bản copy API host vào loader plugin khác | Cùng binary name nhưng khác defining loader, dẫn tới ClassCastException. | Chia sẻ API từ parent; kiểm soát parent delegation. |
| Mở JAR rồi quên đóng | Rò file descriptors/locks. | Đóng `JarFile`/streams theo lifecycle bằng try-with-resources. |
| Lẫn package version với module version | Nhầm chính sách dependency/resolution. | Phân biệt JAR manifest, `Package` metadata và module descriptors. |
| Tự đọc bytes nhưng quên kiểm tra binary name | `NoClassDefFoundError: wrong name` hoặc class loading sai mục tiêu. | Truyền expected binary name, xác nhận class-file đúng. |
| Hai thread đồng thời `definePackage` cùng name | Race/exception và nguy cơ chọn nhầm metadata. | Đồng bộ theo package, kiểm chứng policy trước khi cho class được define. |

## 14. Câu hỏi tự kiểm tra

1. **Custom loader chỉ gọi `defineClass` có bắt buộc phải gọi `definePackage` trước không?**  
   Không. `defineClass` có thể tạo package mặc định. Chỉ gọi `definePackage` trước khi cần metadata/sealing cụ thể.
2. **Tại sao gọi `definePackage("sample.plugin", ...)` sau khi class đầu tiên đã nạp thường thất bại?**  
   Vì package trong loader đã được định nghĩa, không thể định nghĩa lại để thay metadata.
3. **`getDefinedPackage` khác `getPackage` thế nào?**  
   `getDefinedPackage` trả package được loader đó định nghĩa; `getPackage` deprecated có thể tìm parent và dẫn tới metadata không đúng runtime package.
4. **Hai loader có cùng `Package.getName()` có chung một runtime package?**  
   Không. Runtime package khác nhau khi defining loader khác nhau.
5. **`sealBase == null` có nghĩa gì?**  
   Package không sealed; có URL nghĩa package metadata sealed tại base đó.
6. **`CodeSource` có trực tiếp lưu `Implementation-Version` không?**  
   Không. Nó chủ yếu gắn location và certificates; version/title thuộc `Package`.
7. **Nếu `CodeSource.location` trả URL mà ứng dụng đặt, điều đó có chứng minh class file được ký và hợp lệ?**  
   Không. Tự khai báo metadata không xác minh bytes/signature.
8. **Vì sao certificate set của class đầu tiên trong package quan trọng?**  
   Các class sau trong cùng package/loader cần bộ certificates tương thích, nếu không có thể gây `SecurityException`.
9. **Sealed package có tự làm bytecode an toàn như sandbox?**  
   Không. Nó liên quan nguồn package; không thay process isolation hay runtime permission system.
10. **Tại sao loader song song cần khóa theo package?**  
    Hai class có binary name khác nhau nhưng cùng package có thể đồng thời cố `definePackage` với thông tin khác nhau.
11. **Trong JDK 9+, muốn package metadata từ custom loader nào phải hỏi gì?**  
    `thatLoader.getDefinedPackage(name)` hoặc `clazz.getPackage()` của class do loader đó định nghĩa.
12. **Nên tự triển khai manifest/sealing cho mọi ứng dụng chỉ đọc JAR không?**  
    Không. Nếu `URLClassLoader` đáp ứng yêu cầu, ưu tiên dùng API chuẩn và tránh nhân đôi quy tắc dễ sai.

## 15. Tài liệu chính thức

- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `definePackage`, `getDefinedPackage`, `defineClass`, metadata package và certificate rules.
- [Java SE 21 — Package](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Package.html): specification/implementation metadata, `isSealed`, `isCompatibleWith`.
- [Java SE 21 — SecureClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureClassLoader.html): `defineClass(String,byte[],int,int,CodeSource)` và `getPermissions`.
- [Java SE 21 — CodeSource](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/CodeSource.html): location URL, certificate/signers metadata.
- [Java SE 21 — ProtectionDomain](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/ProtectionDomain.html): security domain fields/constructors.
- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): JAR search, class definition và lifecycle chuẩn.
- [Java SE 21 — JAR File Specification](https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html): manifest attributes, package sections, sealing và signed JARs.
- [JVM Specification §5.3](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): runtime type identity, defining loader và runtime package.
- [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486): tình trạng của security manager trong JDK 24+.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-008">Phân loại lỗi không tìm thấy với bytecode không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân loại lỗi không tìm thấy và bytecode không hợp lệ trong custom ClassLoader

## 1. Vấn đề cốt lõi: “không tải được lớp” không phải chỉ có một nguyên nhân

Khi viết custom `ClassLoader`, bạn có thể thấy nhiều lỗi với kết quả giống nhau ở tầng nghiệp vụ: ứng dụng không sử dụng được class được yêu cầu. Nhưng **bản chất và hướng xử lý khác nhau hoàn toàn**:

- **`ClassNotFoundException`**: loader **không tìm được lớp được yêu cầu** qua đường tìm kiếm đang dùng. Đây là **checked exception** thường được khai báo ở `loadClass` và `findClass`.
- **`ClassFormatError`**: JVM đã nhận dữ liệu để định nghĩa class nhưng class file **sai định dạng hoặc không thể diễn giải như class file hợp lệ**.
- **`UnsupportedClassVersionError`**: class file có **major/minor version JVM hiện tại không hỗ trợ**. Nó là **subclass của `ClassFormatError`**.
- **`VerifyError`**: class file đủ cấu trúc để JVM đi vào verification, nhưng bytecode **vi phạm các ràng buộc verifier**, ví dụ tính đúng đắn của stack/kiểu ở các đường điều khiển.
- **`NoClassDefFoundError`**: trong khi JVM cần định nghĩa, liên kết hoặc khởi tạo class, một định nghĩa cần thiết **không khả dụng** hoặc một class trước đó **đã khởi tạo thất bại**; riêng `defineClass` còn có thể ném lỗi này khi tên yêu cầu **khác binary name chứa trong bytes**.
- **Các `LinkageError` khác** như `NoSuchMethodError`, `NoSuchFieldError`, `IllegalAccessError` hoặc `IncompatibleClassChangeError`: JVM tìm thấy bytecode nhưng việc liên kết các ký hiệu/API runtime không phù hợp.

**Nguyên tắc:** Đừng biến tất cả thành `ClassNotFoundException`. Nếu bytecode tồn tại nhưng **không hợp lệ**, thông tin chẩn đoán quan trọng thường nằm ở một **Error** thuộc nhánh `LinkageError`, không phải ở tình trạng “không tìm thấy file”.

## 2. Đặt lỗi vào đúng giai đoạn của vòng đời class

```text
loader.loadClass("demo.Plugin")
          │
          ▼
1. findLoadedClass(name)
          │
          ▼
2. parent.loadClass(name)
          │ cha không tìm thấy?
          ▼
3. findClass(name) [custom loader]
          │
          ├── Không có bytecode
          │     └── ClassNotFoundException
          │
          ├── Đọc được bytecode
          │     └── defineClass(name, bytes, ...)
          │            ├── File sai định dạng → ClassFormatError
          │            ├── Version không hỗ trợ
          │            │      → UnsupportedClassVersionError
          │            ├── Tên không khớp
          │            │      → NoClassDefFoundError
          │            └── Class<?> đã định nghĩa
          │
          ▼
4. Verification / preparation / resolution
          │
          ├── Vi phạm quy tắc verifier → VerifyError
          ├── Thiếu dependency → có thể NoClassDefFoundError
          └── Sai liên kết binary API → các LinkageError khác
          │
          ▼
5. Initialization khi có active use
          │
          ├── Static initializer thành công
          └── Static initializer lỗi
                 ├── lần đầu: ExceptionInInitializerError
                 └── lần sau: thường NoClassDefFoundError
```

**Đây là mô hình để chẩn đoán, không phải lịch trình cứng cho mọi JVM.** Việc verification, resolution và sử dụng tham chiếu ký hiệu có thể được JVM thực hiện vào thời điểm khác nhau trong những giới hạn đặc tả. Vì vậy `loadClass(name)` thành công **chưa chứng minh** mọi dependency, mọi method reference và static initializer của lớp sẽ hoạt động.

### Hai ranh giới phải nhớ

1. **File có tồn tại không?** Đây mới là một bước trong `findClass`. File tồn tại không đồng nghĩa đó là class hợp lệ.
2. **Class đã được định nghĩa chưa?** Tìm được bytes hoặc nhận `Class<?>` không đồng nghĩa class đã khởi tạo hay các tham chiếu đều resolution thành công.

Nếu custom loader tự dùng `Files.readAllBytes(path)`, bước đọc là **I/O do ứng dụng viết**. Các lỗi sau khi gọi `defineClass` hoặc khi JVM liên kết là **lỗi hợp đồng JVM**. Cần giữ ranh giới này rõ trong code và logs.

## 3. Sơ đồ phân loại exception/error

| Dạng lỗi | Hệ phân cấp | Ý nghĩa thường gặp | Hướng xử lý |
|---|---|---|---|
| `ClassNotFoundException` | `Exception`, checked | Loader không tìm được class qua nguồn đã cấu hình | Kiểm tra binary name, URL/classpath, JAR entry, delegation |
| `ClassFormatError` | `LinkageError` | Bytes không có cấu trúc class file hợp lệ | Kiểm tra magic, constant pool, transformer, file hỏng |
| `UnsupportedClassVersionError` | `ClassFormatError` | Major/minor không được runtime hỗ trợ | So JDK chạy và target `--release` |
| `VerifyError` | `LinkageError` | Bytecode vi phạm ràng buộc verifier | Kiểm tra bytecode generator/transformer, stack map, control flow |
| `NoClassDefFoundError` | `LinkageError` | JVM cần class/definition nhưng không có; hoặc init từng thất bại; hoặc wrong name | Đọc toàn bộ message và cause; xét thiếu dependency/init/wrong name |
| `NoSuchMethodError` | `IncompatibleClassChangeError` → `LinkageError` | Method từng được compile tham chiếu không tồn tại đúng signature lúc chạy | So JAR/API bản compile và runtime |
| `NoSuchFieldError` | `IncompatibleClassChangeError` → `LinkageError` | Field không còn đúng tên/descriptor | So versions của dependency |
| `IllegalAccessError` | `IncompatibleClassChangeError` → `LinkageError` | JVM tìm được thành viên nhưng runtime không có quyền truy cập | Kiểm tra access modifiers, runtime package, module, loader |
| `ExceptionInInitializerError` | `LinkageError` | Logic khởi tạo static thất bại | Đọc root cause trong `<clinit>` |
| `SecurityException` | `RuntimeException` | Vi phạm kiểm tra bảo vệ khi định nghĩa/truy cập class | Kiểm tra package như `java.*`, certificates, security context |

**Lưu ý kế thừa:** `UnsupportedClassVersionError extends ClassFormatError`. Khi viết các `catch` riêng, phải bắt lỗi version **trước** `ClassFormatError`; nếu không, nhánh version sẽ bị nhánh cha giữ lại. `VerifyError` là một nhánh khác dưới `LinkageError`, không phải subclass của `ClassFormatError`.

## 4. ClassNotFoundException: “không tìm được tên class”

API chuẩn:

```java
protected Class<?> findClass(String name)
        throws ClassNotFoundException
```

Trong loader tuân theo mô hình ủy quyền mặc định, `findClass` được gọi sau khi parent không tìm được lớp được yêu cầu. Triển khai mặc định của `ClassLoader.findClass(String)` ném `ClassNotFoundException`; subclass override để biết tìm bytecode từ đâu.

Với một custom loader tìm class trên ổ đĩa:

```java
// Trích đoạn bên trong override findClass:
Path classFile = root.resolve(
        name.replace('.', '/') + ".class"
);

byte[] bytes;
try {
    bytes = Files.readAllBytes(classFile);
} catch (IOException cause) {
    throw new ClassNotFoundException(name, cause);
}
return defineClass(name, bytes, 0, bytes.length);
```

Đây là **trích đoạn**, cần có `root`, imports và một subclass `ClassLoader`. Cách bọc `IOException` giữ lại nguyên nhân I/O; ví dụ `NoSuchFileException` cho thấy đường dẫn không tồn tại.

Trong loader production, nên phân biệt thêm:
- **Không có class trong nguồn này**: `ClassNotFoundException` là tín hiệu hợp lý để caller biết lớp không tìm được.
- **Nguồn tồn tại nhưng I/O hỏng**: hợp đồng `findClass` vẫn có thể trình bày dưới dạng `ClassNotFoundException` với **cause gốc**; không được mất thông tin đường dẫn, quyền đọc, timeout hay lỗi storage.
- **Bytes đã đọc nhưng hỏng**: **không** catch `ClassFormatError` rồi thay thành `ClassNotFoundException`, vì như vậy sẽ che mất nguyên nhân “bytecode có nhưng không hợp lệ”.

### Sai lầm thường gặp

```java
// ANTI-PATTERN — không sử dụng nguyên văn:
try {
    byte[] bytes = loadBytes(name);
    return defineClass(name, bytes, 0, bytes.length);
} catch (Throwable anything) {
    throw new ClassNotFoundException(name, anything);
}
```

Đây là **mã minh họa anti-pattern**, các phương thức và biến chưa được định nghĩa. Bắt `Throwable` nuốt cả `LinkageError`, `OutOfMemoryError`, `ThreadDeath` và những lỗi nghiêm trọng khác. Caller tưởng class chỉ vắng mặt và có thể retry/fallback vô nghĩa. **Chỉ bắt đúng những lỗi thực sự có thể xử lý.**

## 5. ClassFormatError: “đã có bytes nhưng không phải class file hợp lệ”

Một class file Java có cấu trúc nhị phân quy định bởi JVMS:

```text
u4 magic          // 0xCAFEBABE
u2 minor_version
u2 major_version
u2 constant_pool_count
...
```

**Ví dụ nguyên nhân:**

- Dữ liệu đầu file không có magic `CAFEBABE`; đọc nhầm một tệp text/HTML/JAR/ảnh như `.class`.
- File bị cắt/truncated trong quá trình tải, giải nén hoặc deploy.
- Cấu trúc constant pool, indices, attributes hoặc phần khác không tuân theo class-file format.
- Một công cụ bytecode transformer sinh ra bytes sai cấu trúc.
- Loader đọc nhầm tệp do cách map binary name sang path bị lỗi.

`ClassFormatError` là **`Error`**, không phải checked `Exception`. `defineClass` có thể ném lỗi này khi nhận bytes không hợp lệ. Cần tìm **nguồn bytes** và **nguyên nhân làm hỏng dữ liệu**, không chỉ thử lại tên class.

### Phân biệt file tồn tại với dữ liệu hợp lệ

```java
// Trích đoạn:
byte[] bytes = Files.readAllBytes(classFile);
System.out.println("bytes length=" + bytes.length);
return defineClass(name, bytes, 0, bytes.length);
```

Nếu `bytes.length > 0`, ta chỉ biết đã đọc được dữ liệu; chưa biết đó là Java class file. Ngay cả việc bốn byte đầu là `CAFEBABE` cũng **chưa chứng minh** constant pool, nội dung hoặc bytecode hợp lệ.

## 6. UnsupportedClassVersionError: “bytecode mới hơn/không được JVM hỗ trợ”

Đây là **subclass của `ClassFormatError`**, dành cho trường hợp `major_version`/`minor_version` trong class file không được JVM nhận diện theo quy tắc phiên bản.

Một số mốc:

| Java release | Class file major thường dùng |
|---|---:|
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |
| Java 25 | 69 |
| Java 26 | 70 |

Ví dụ ứng dụng chạy **Java 17** nhưng custom loader đọc class đã biên dịch cho **Java 21** (major 65) thì JVM Java 17 không thể nạp bytecode đó. Dù class file tồn tại và magic hợp lệ, vẫn phát sinh `UnsupportedClassVersionError`.

**Không giải quyết bằng cách đổi phần mở rộng, đổi thư mục hoặc đổi vài byte major trong class file**: opcode, constant pool, language feature và API có thể cần runtime mới thật sự.

Giải pháp đúng:

1. Kiểm tra **JVM runtime đang chạy** bằng `java -version`, không chỉ `javac -version`.
2. Kiểm tra **major/minor của file .class thực tế** bằng `javap -verbose` (hoặc công cụ đọc header đáng tin).
3. Biên dịch lại với `javac --release <supported-version>` nếu mã và các API nó dùng cho phép.
4. Nếu tính năng không thể hạ target, **nâng Java runtime** cho phù hợp.
5. Nếu dùng multi-release JAR, kiểm tra đúng entry versioned mà runtime/loader thật sự chọn.

Một ngoại lệ tinh tế là **preview-feature class files** có `minor=65535` từ các release hỗ trợ quy tắc preview. Chúng thường chỉ dùng được trên runtime **cùng release** với tùy chọn bật preview tương ứng; không suy ra major thấp hơn có nghĩa mọi JDK mới đều nhận bytecode preview.

## 7. VerifyError: bytecode qua bước định dạng nhưng không qua verifier

`VerifyError` biểu thị JVM phát hiện class file **không vượt qua các điều kiện verification**, ví dụ:

- Instruction đòi operand stack có kiểu/độ sâu không tương thích.
- Branch nhảy đến vị trí hoặc trạng thái stack không phù hợp.
- Return type/stack-map information không phù hợp với đường điều khiển.
- Các phép thao tác local variables/receiver vi phạm quy tắc an toàn kiểu.

Đây là nhóm vấn đề thường xuất hiện khi:

- Tự sinh bytecode bằng ASM/Byte Buddy hoặc viết class-file generator.
- Instrumentation/rewrite bytecode nhưng không cập nhật stack map frames hay max stack/local variables đúng cách.
- Một công cụ obfuscator/shader/agent tạo bytecode không phù hợp runtime target.

**Phân biệt với `ClassFormatError`:** Một class file có thể có header/constant pool hợp lệ nhưng chứa code vi phạm verifier. JVM có thể chỉ phát hiện `VerifyError` khi verification thực sự xảy ra; đừng gán một lịch trình cố định cho mọi JVM.

`javac` bình thường sinh bytecode hợp lệ cho target được hỗ trợ; vì vậy không cần cố xây một ví dụ `VerifyError` bằng source Java hợp lệ đơn thuần. Để tạo lỗi xác định, phải cố tình sinh/mutate bytecode theo cách kiểm soát. Trong production, **không vô hiệu hóa verifier** làm cách “sửa” file bytecode hỏng.

## 8. Demo Java 17+: phân biệt bốn tình huống bằng một custom loader

Ví dụ sau chủ động tạo bốn đường:

1. `MISSING`: yêu cầu một binary name không có file → `ClassNotFoundException`.
2. `MALFORMED`: sửa magic đầu file của class hợp lệ → `ClassFormatError`.
3. `NEWER`: sửa major thành giá trị cao hơn khả năng của Java 17 → `UnsupportedClassVersionError`.
4. `WRONG_NAME`: yêu cầu một binary name khác tên được khai trong bytes → `NoClassDefFoundError`.
5. `VALID`: sử dụng bytes hợp lệ → nạp được class, sau đó reflection gọi phương thức và khởi tạo lớp.

Các tình huống dùng **loader riêng cho mỗi lần thử** để không bị `findLoadedClass` lấy lại class đã nạp từ lượt trước. Demo cố ý **không tạo `VerifyError`** vì cần một bytecode transformer phù hợp, và cũng không giả định mọi JVM chọn đúng cùng thời điểm verification.

### 8.1. File Sample.java

Tạo file `Sample.java`:

```java
package demo;

public final class Sample {
    static {
        System.out.println("Sample initialized");
    }

    public static String greet() {
        return "hello";
    }
}
```

### 8.2. File FailureKindsDemo.java

Tạo file `FailureKindsDemo.java`:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FailureKindsDemo {
    enum Mode {
        MISSING, MALFORMED, NEWER, WRONG_NAME, VALID
    }

    static final class TestLoader extends ClassLoader {
        private final Path root;
        private final Mode mode;

        TestLoader(Path root, ClassLoader parent, Mode mode) {
            super(parent);
            this.root = root;
            this.mode = mode;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            String relativePath = mode == Mode.WRONG_NAME
                    ? "demo/Sample.class"
                    : name.replace('.', '/') + ".class";

            byte[] bytes;
            try {
                bytes = Files.readAllBytes(root.resolve(relativePath));
            } catch (IOException cause) {
                throw new ClassNotFoundException(name, cause);
            }

            if (mode == Mode.MALFORMED) {
                bytes[0] = 0; // Phá magic CAFEBABE.
            } else if (mode == Mode.NEWER) {
                bytes[6] = (byte) 0x7F; // Major rất cao.
                bytes[7] = (byte) 0x7F;
            }

            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("plugin-classes");
        ClassLoader parent = FailureKindsDemo.class.getClassLoader();

        for (Mode mode : Mode.values()) {
            TestLoader loader = new TestLoader(root, parent, mode);

            String name = switch (mode) {
                case MISSING -> "demo.Absent";
                case WRONG_NAME -> "demo.WrongName";
                default -> "demo.Sample";
            };

            try {
                Class<?> type = loader.loadClass(name);
                System.out.println(mode + " -> loaded: " + type.getName());

                if (mode == Mode.VALID) {
                    Object greeting =
                            type.getMethod("greet").invoke(null);
                    System.out.println("invoke: " + greeting);
                }
            } catch (ClassNotFoundException | LinkageError error) {
                System.out.println(
                        mode + " -> " + error.getClass().getSimpleName()
                );
            }
        }
    }
}
```

**Mục đích từng bước:**

- `TestLoader.findClass` tìm file khi parent không có class. Nếu `Files.readAllBytes` ném `IOException`, nó chuyển thành `ClassNotFoundException(name, cause)`, giữ lại nguyên nhân gốc.
- `MALFORMED` phá magic của bytes: dữ liệu không còn là class file hợp lệ.
- `NEWER` thay **major** ở offset 6–7 của header class file bằng `0x7F7F` (32767): Java 17 không hỗ trợ version đó, nhưng các bytes khác vẫn xuất phát từ một class file hợp lệ.
- `WRONG_NAME` yêu cầu `demo.WrongName` nhưng tải bytes tự khai tên `demo.Sample`. `defineClass(name,...)` phát hiện tên binary không khớp.
- `VALID` định nghĩa và trả về `demo.Sample` đúng tên; static initializer chỉ chạy khi reflection thực sự gọi `greet()`.

### 8.3. Biên dịch và chạy

Tạo thư mục plugin riêng. Trên Bash:

```bash
mkdir -p plugin-classes
```

Trên PowerShell:

```powershell
New-Item -ItemType Directory -Force plugin-classes | Out-Null
```

Biên dịch **bằng JDK 17**:

```bash
javac --release 17 -d plugin-classes Sample.java
javac --release 17 FailureKindsDemo.java
java -cp . FailureKindsDemo
```

Kết quả file:

```text
working-directory/
├── Sample.java
├── FailureKindsDemo.java
├── FailureKindsDemo.class
├── FailureKindsDemo$Mode.class
├── FailureKindsDemo$TestLoader.class
└── plugin-classes/
    └── demo/
        └── Sample.class
```

**Điều kiện thử nghiệm:** `plugin-classes` **không nằm trên `-cp` của host**. Nếu thêm `plugin-classes` vào application classpath, parent loader có thể tìm `demo.Sample` trước và nhánh `findClass` tùy chỉnh không được gọi cho tên đó.

### 8.4. Stdout trên JDK 17

```text
MISSING -> ClassNotFoundException
MALFORMED -> ClassFormatError
NEWER -> UnsupportedClassVersionError
WRONG_NAME -> NoClassDefFoundError
VALID -> loaded: demo.Sample
Sample initialized
invoke: hello
```

**Phân tích thứ tự:**

1. `MISSING`: parent không có `demo.Absent`; child tìm `plugin-classes/demo/Absent.class` nhưng không có, nên ném `ClassNotFoundException`.
2. `MALFORMED`: parent không có `demo.Sample`; child đọc file có thật nhưng sửa magic. JVM nhận bytes không hợp lệ và ném `ClassFormatError`.
3. `NEWER`: đọc file có thật và tạo major không hỗ trợ. JVM phân loại là `UnsupportedClassVersionError`, **một loại `ClassFormatError` cụ thể**.
4. `WRONG_NAME`: content class file thực sự là `demo.Sample` nhưng `defineClass` được yêu cầu tên `demo.WrongName`; lỗi là `NoClassDefFoundError`, thường chứa thông tin “wrong name” trong message thực.
5. `VALID`: class được định nghĩa thành công và in `loaded: demo.Sample`. `loadClass` chưa yêu cầu chạy static initializer; khi gọi `greet` bằng reflection, in `Sample initialized` rồi `invoke: hello`.

Ví dụ in **chỉ tên lớp lỗi** qua `getClass().getSimpleName()` để có stdout ổn định. Message chi tiết của JVM có thể khác theo bản JDK và không nên dùng làm chuỗi kiểm thử cứng. Trong hệ thống thật, cần giữ **message + cause + stack trace** để biết nguồn lỗi và đường nạp.

### 8.5. Nếu chạy bằng Java mới hơn thì sao?

Nhánh `NEWER` dùng major `32767`, cao vượt xa class-file major của các Java release hiện có. Nó được chọn nhằm mô phỏng một version không được JVM hỗ trợ trong demo, **không phải** khuyến nghị sửa bytes để “nâng phiên bản class”.

Đối với dữ liệu sản xuất, đừng sửa thủ công header; hãy dùng compiler với target phù hợp hoặc kiểm tra bằng `javap -verbose`. Thử nghiệm này chỉ giúp quan sát **phân loại lỗi**.

## 9. Vì sao NoClassDefFoundError khác ClassNotFoundException?

Tên hai lỗi tương tự nhưng quan hệ không giống nhau:

- `ClassNotFoundException` là **checked Exception**, thường xuất hiện khi **code chủ động yêu cầu loader tìm một binary name mà loader không tìm thấy**.
- `NoClassDefFoundError` là **LinkageError**, thường xuất hiện khi JVM cần một định nghĩa để **liên kết hoặc thực thi class đã tham chiếu** nhưng không thể đáp ứng; ngoài ra có đường riêng khi binary name trong bytes không khớp `defineClass`, hoặc khi class đã initialization thất bại.

Ví dụ ứng dụng được compile cùng thư viện `lib.Helper`, nhưng runtime chỉ còn `App.class` mà không có `lib.Helper.class`. Lời gọi đến `Helper` trong code App có thể gây `NoClassDefFoundError` khi JVM cần tải lớp phụ thuộc đó. Đừng diễn giải là caller của `App` đã trực tiếp gọi `ClassLoader.loadClass("lib.Helper")` và bắt `ClassNotFoundException`.

Trong JDK, một `ClassNotFoundException` cũng có thể nằm sâu bên trong **cause** của `NoClassDefFoundError`, tùy đường nạp. Nếu gặp `NoClassDefFoundError`, phải xem message và cause, không chỉ tên exception.

### Trường hợp class đã lỗi initialization

Lần đầu code chạm vào một class có static initializer ném một exception không thuộc `Error`, JVM thường bọc thành `ExceptionInInitializerError`. Class chuyển sang trạng thái erroneous; lần yêu cầu init tiếp theo của cùng runtime class thường ném `NoClassDefFoundError` với thông báo “Could not initialize class ...”.

Đây **không phải** bằng chứng thiếu file class. Thay classpath mà không kiểm tra static initializer có thể sửa sai vấn đề.

## 10. Ví dụ riêng: nạp được class nhưng initializer thất bại

Tạo `InitFailureDemo.java`:

```java
public final class InitFailureDemo {
    static final class Broken {
        static int value = initialize();

        static int initialize() {
            throw new IllegalStateException("invalid configuration");
        }
    }

    public static void main(String[] args) throws Exception {
        String name = "InitFailureDemo$Broken";
        ClassLoader loader = InitFailureDemo.class.getClassLoader();

        System.out.println("loaded: "
                + (Class.forName(name, false, loader) != null));

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                Class.forName(name, true, loader);
            } catch (LinkageError error) {
                System.out.println(
                        attempt + ": " + error.getClass().getSimpleName()
                );
            }
        }
    }
}
```

Chạy:

```bash
javac --release 17 InitFailureDemo.java
java InitFailureDemo
```

Stdout:

```text
loaded: true
1: ExceptionInInitializerError
2: NoClassDefFoundError
```

Lời gọi `forName(..., false, ...)` thành công cho thấy JVM **tìm được class** và không yêu cầu chạy initializer. Lần `initialize=true` đầu thất bại vì code trong `Broken`, không phải vì thiếu class file. Lần tiếp theo class vẫn erroneous và không chạy initializer lại.

**Quy tắc debug:** tìm **lỗi xuất hiện đầu tiên**, vì sau đó một chuỗi lỗi “Could not initialize class” có thể chỉ là hậu quả thứ cấp.

## 11. Không che LinkageError bằng fallback sang loader khác

Một custom ClassLoader tuân parent-first thường làm:

```text
findLoadedClass(name)
       ↓ chưa có
parent.loadClass(name)
       ↓ parent báo ClassNotFoundException
findClass(name)
```

Điều kiện fallback thông thường là parent **không tìm thấy class**, chứ không phải parent báo bất kỳ lỗi nào. Nếu parent tìm thấy bytes nhưng ném `ClassFormatError` hoặc `VerifyError`, thuật toán mặc định **không âm thầm đổi sang một bản class khác** ở child.

Đây là nguyên tắc quan trọng cho plugin loader:

- **Không tìm thấy**: có thể thử nguồn class tiếp theo theo chính sách lookup được thiết kế.
- **Tìm thấy nhưng bị hỏng**: cần báo lỗi; không giả vờ rằng lớp không tồn tại.
- **Class có version mới hơn runtime**: đổi sang bytes cũ hơn một cách tùy tiện có thể che lỗi build/deploy. Nếu muốn fallback có kiểm soát, hãy thiết kế rõ các artifacts/versions hoặc **multi-release JAR** theo spec, không catch `UnsupportedClassVersionError` rồi âm thầm nạp class khác không kiểm chứng.
- **Lỗi security hoặc package definition**: không nuốt lỗi để vượt chính sách truy cập.

Đặc biệt, trong code override `loadClass`, đừng dùng:

```java
// ANTI-PATTERN — không chạy trực tiếp:
try {
    return parent.loadClass(name);
} catch (Throwable ignored) {
    return findClass(name);
}
```

Vì như vậy lỗi bytecode hỏng của parent có thể bị biến thành việc nạp một bản class khác. Điều này tạo kết quả khó tái hiện và nguy cơ xung đột danh tính class/runtime package.

## 12. Những lỗi link khác không thuộc “không tìm thấy class file”

### 12.1. NoSuchMethodError / NoSuchFieldError

Chúng thường liên quan **binary incompatibility**: class caller được compile với API version A, còn runtime nạp dependency version B thiếu method/field tương ứng.

Ví dụ về source API:

```java
// Library v1:
public class Api {
    public static String version() {
        return "v1";
    }
}
```

Caller compile với v1 và gọi `Api.version()`. Nếu runtime thay bằng library v2 đã xóa method đó, JVM có thể ném `NoSuchMethodError` khi resolve lời gọi.

Đoạn trên chỉ là **minh họa hai bản thư viện**, không phải một file runnable chứng minh lỗi tự thân. Cách sửa là làm rõ dependency runtime và giữ binary compatibility, không bắt `ClassNotFoundException`.

### 12.2. IllegalAccessError

`IllegalAccessError` xảy ra khi JVM tìm được một field/method/class nhưng điều kiện truy cập runtime không thỏa. Với custom loaders, hai class có cùng tên package về mặt chữ nhưng do **different defining loaders** định nghĩa **không tự thuộc cùng runtime package**. Vì vậy code từng truy cập package-private thành công khi compile hoặc chạy cùng loader có thể thất bại trong một bố cục loader khác.

Không giải quyết lỗi access bằng cách thay `ClassNotFoundException`, vì class **đã được tìm thấy**.

### 12.3. IncompatibleClassChangeError và duplicate definition

`IncompatibleClassChangeError` bao gồm nhiều thay đổi binary contract, ví dụ static/instance mismatch. Tự gọi `defineClass` nhiều lần cho cùng binary name trong cùng defining loader có thể gây `LinkageError` do duplicate class definition. Triển khai `ClassLoader.loadClass` mặc định kiểm tra `findLoadedClass` và dùng locking thích hợp nhằm tránh một phần các vấn đề này.

Đừng gọi trực tiếp `findClass(name)` từ client nếu định làm hoạt động lookup bình thường: bạn có thể bỏ qua caching/delegation/synchronization của `loadClass` và tạo lỗi khó kiểm soát.

## 13. Cách bắt lỗi đúng tầng ứng dụng

Ở **ranh giới plugin/module loading**, có thể phân loại để ghi log và quyết định:

```java
// Trích đoạn trong method có biến:
// ClassLoader loader; String binaryName;

try {
    Class<?> type = loader.loadClass(binaryName);
    useType(type);
} catch (ClassNotFoundException ex) {
    report("Class not found in configured sources", ex);
} catch (UnsupportedClassVersionError ex) {
    report("Class version not supported by this JVM", ex);
} catch (ClassFormatError ex) {
    report("Malformed or incompatible class-file format", ex);
} catch (VerifyError ex) {
    report("Bytecode verification failed", ex);
} catch (NoClassDefFoundError ex) {
    report("Required definition unavailable or class init failed", ex);
} catch (LinkageError ex) {
    report("Other JVM linkage failure", ex);
}
```

Đây là **trích đoạn**, `useType` và `report` là các phương thức do ứng dụng định nghĩa, không phải API Java chuẩn. Thứ tự `catch` quan trọng: `UnsupportedClassVersionError` đứng trước `ClassFormatError`; các trường hợp chuyên biệt đứng trước `LinkageError`.

**Cẩn thận:** nếu plugin API được dùng ở framework, không nhất thiết **mọi nơi** đều nên catch `LinkageError`. Thường chỉ tầng biên có chính sách quản lý plugin/fail-fast rõ ràng mới nên làm thế. Đừng bắt Error rồi “tiếp tục chạy như không có gì xảy ra”. Hệ thống có thể đã ở trạng thái không nhất quán.

### Giữ thông tin chẩn đoán

Một log tối thiểu nên gồm:

- **Binary name yêu cầu** (ví dụ `demo.Sample`).
- **ClassLoader và parent chain**; nếu có, defining loader của class thực sự được trả về.
- **Nguồn class file**: URL, JAR entry, path, version artifact, checksum nếu hữu ích.
- **JVM runtime** (feature version, vendor).
- **Loại lỗi, message, cause chain, suppressed exceptions**.
- **Thời điểm xảy ra**: find bytes, defineClass, resolve/verify, initialize hay lúc gọi method.
- **Thread thực thi và TCCL** nếu framework dùng service/provider discovery.

Không đưa dữ liệu nhạy cảm như credential, token trong URL nguồn vào logs sản xuất. Khi xem URL từ nguồn mạng, cần redact query string hay thông tin xác thực phù hợp.

## 14. Chẩn đoán bằng javap, file header và JVM log

### 14.1. Kiểm tra class file được deploy

```bash
javap -verbose -classpath plugin-classes demo.Sample
```

Tìm những thông tin có ích:

- `major version` và `minor version`.
- `this_class` tương ứng `demo/Sample`.
- Constant pool, method descriptors, bytecode và các attributes.

Với lệnh biên dịch `javac --release 17` trong ví dụ, `major version` của `demo.Sample` là **61** và minor thông thường là **0**.

Nếu `javap` không đọc được, hãy kiểm tra liệu tệp thực sự là `.class`, có bị cắt/hỏng hay không; điều này không có nghĩa JVM chắc chắn sẽ ném đúng cùng lỗi và message như `javap`.

### 14.2. Kiểm tra đúng JDK runtime

```bash
java -version
javac -version
```

`java` và `javac` có thể đến từ hai JDK installations khác nhau qua PATH, IDE, Maven/Gradle toolchain, container image hoặc CI. `javac` mới không làm JVM runtime cũ hiểu bytecode mới hơn.

### 14.3. Log class load

Với HotSpot hỗ trợ unified logging:

```bash
java -Xlog:class+load=info -cp . FailureKindsDemo
```

Log giúp quan sát class load và một phần nguồn class, nhưng không nên coi log class load là **log mọi bước của findClass, verification, resolution hoặc initialization**. Để biết chính xác mã trong custom loader làm gì, thêm log trước `Files.readAllBytes` và trước `defineClass`, tránh log những dữ liệu có thể nhạy cảm.

### 14.4. Bytecode transformer và agent

Nếu code chỉ lỗi khi chạy với Java agent, AOP/ORM proxy hoặc bytecode enhancer, so sánh:

1. Bytes class file **gốc** trước biến đổi.
2. Bytes **sau biến đổi** ngay trước `defineClass`.
3. JVM version và phiên bản thư viện transformer.
4. Các attributes như `StackMapTable` có được tạo/cập nhật đúng không.

Không kiểm tra duy nhất source code Java: source hợp lệ nhưng transformer vẫn có thể phá bytecode.

## 15. Cẩn trọng với lớp đã nạp và vấn đề tái hiện lỗi

Khi custom loader đã định nghĩa thành công `demo.Sample`:

```java
Class<?> first = loader.loadClass("demo.Sample");
Class<?> second = loader.loadClass("demo.Sample");
System.out.println(first == second); // true, với cùng loader mặc định
```

Đây là **trích đoạn**. Nếu bạn thay file `Sample.class` thành file hỏng **sau lần nạp thành công**, lần gọi tiếp theo theo `loadClass` mặc định thường dùng `findLoadedClass` và **không đọc lại file mới**. Điều đó có thể khiến một thí nghiệm “bytecode hỏng nhưng vẫn load được” nếu quên rằng class đã cached.

Muốn kiểm tra file mới:

- Chạy **JVM mới**, hoặc
- Tạo **defining ClassLoader mới** với bố cục parent phù hợp, bảo đảm nó không ủy quyền cho parent vốn đã có class cùng tên.

Tương tự, nếu một class initializer đã thất bại và class trở thành erroneous, JVM không tự thử lại initialization cho **cùng runtime class** mỗi lần gọi. Tạo file class khác trên ổ đĩa không xóa trạng thái erroneous của runtime class đó.

## 16. Quy trình quyết định khi một plugin load thất bại

```text
Không sử dụng được plugin class
          │
          ▼
Có ClassNotFoundException?
    ├─ Có → binary name / path / URL / JAR / parent / permissions
    └─ Không
          │
          ▼
Có UnsupportedClassVersionError?
    ├─ Có → so major/minor với Java runtime, --release
    └─ Không
          │
          ▼
Có ClassFormatError?
    ├─ Có → bytes bị hỏng / sai cấu trúc / đọc nhầm file
    └─ Không
          │
          ▼
Có VerifyError?
    ├─ Có → bytecode generator, StackMapTable, kiểu/stack
    └─ Không
          │
          ▼
Có NoClassDefFoundError?
    ├─ Có → dependency, wrong name, failed initialization
    └─ Không
          │
          ▼
Có lỗi LinkageError khác?
    ├─ Có → binary API, access, class identity
    └─ Không → xem các exception khác của ứng dụng/framework
```

**Đây là quy trình triage**, không phải danh sách exhaustive của mọi lỗi JVM. Một plugin có thể đồng thời tồn tại nhiều vấn đề; sửa lỗi đầu tiên rồi chạy lại trong **môi trường sạch** để xác định lỗi kế tiếp.

## 17. Lỗi phổ biến khi tự viết ClassLoader

| Anti-pattern | Vì sao sai? | Hướng sửa |
|---|---|---|
| Catch `Throwable` trong `findClass` rồi ném CNFE | Che lỗi bytecode, linkage, OOME và vấn đề bảo mật | Chỉ chuyển IOException phù hợp thành CNFE; để Error đi qua |
| Nếu file tồn tại thì kết luận class hợp lệ | Bytes có thể sai magic/constant pool/major/verification | Gọi `defineClass` để JVM xác minh, kiểm tra log |
| Dùng `UnsupportedClassVersionError` như lỗi “thiếu file” | JVM đã đọc được class nhưng không hỗ trợ version | Kiểm tra runtime, target, recompile |
| Bắt `ClassFormatError` trước `UnsupportedClassVersionError` | Branch version không bao giờ tới được | Bắt subclass trước |
| Chặn `LinkageError` và luôn fallback sang child | Có thể thay một bản class sai cách, phá identity | Fallback khi không tìm thấy theo chính sách đã định |
| Viết custom loader nhưng quên binary name | `defineClass` có thể ném `NoClassDefFoundError: wrong name` | Map name/path chính xác, không tự đổi package |
| Nạp từ JAR mới bằng loader cũ | `findLoadedClass` trả class cũ | Loader mới hoặc process mới |
| Dùng một JVM cho mọi biến thể thử nghiệm | Trạng thái đã load/init làm kết quả không độc lập | Loader riêng hoặc process riêng |
| Thấy `NoClassDefFoundError` rồi chỉ sửa classpath | Có thể do init thất bại hoặc wrong name | Đọc message/cause và lỗi đầu tiên |
| Bỏ qua `VerifyError` vì source compile được | Transformer có thể tạo bytecode sai | Kiểm tra bytes sau transform |
| Tắt verifier để “fix” lỗi | Che bytecode sai và phá bảo đảm an toàn JVM | Sửa generator/transformer |
| Không ghi loader identity khi debug | Hai class cùng name có thể khác runtime type | Log defining loader và path nguồn |

## 18. Bài tập tự kiểm tra

1. **Lớp không có trên classpath và `findClass` không tìm được file, lỗi nào phù hợp?** `ClassNotFoundException`.
2. **Một loader đọc được bốn byte ngẫu nhiên rồi gọi `defineClass`, có chắc là CNFE?** Không; nếu JVM thấy bytes không phải class file, đó là `ClassFormatError`.
3. **Java 17 tìm thấy bytecode Java 21 major 65 thì sao?** Có thể ném `UnsupportedClassVersionError`, không phải “không tìm thấy class”.
4. **`UnsupportedClassVersionError` có phải subclass của `ClassFormatError`?** Có.
5. **Class file có cấu trúc hợp lệ nhưng phép thao tác operand stack vi phạm type safety thì thường lỗi gì?** `VerifyError`.
6. **`defineClass("p.Other", bytesOfPActual, ...)` có thể ném gì?** `NoClassDefFoundError` do binary name không khớp.
7. **Class đã load được nhưng static initializer ném `IllegalStateException`, lần đầu và lần sau có thể thấy gì?** `ExceptionInInitializerError` rồi `NoClassDefFoundError`.
8. **Có nên catch mọi LinkageError và chuyển thành CNFE?** Không; làm mất nguyên nhân thực và cản trở triage.
9. **Nếu parent có class file sai cấu trúc, loader con có mặc nhiên thử bản cùng tên ở thư mục riêng không?** Không; parent-first mặc định chỉ fallback khi parent không tìm thấy theo hợp đồng, không nuốt các Error.
10. **Hai lỗi `ClassNotFoundException` và `NoClassDefFoundError` có cùng superclass trực tiếp không?** Không; một nhánh là checked Exception, nhánh kia là LinkageError.
11. **Tại sao trong demo dùng loader mới cho mỗi mode?** Để class đã định nghĩa ở mode khác không được reuse qua `findLoadedClass`.
12. **Một file class đúng header và đúng major có chắc thành công khi JVM sử dụng không?** Không; còn verification, binary dependencies, access và initialization.
13. **Muốn chắc runtime đang dùng bytecode nào thì làm gì?** Xác định JVM, loader, JAR entry/đường dẫn thực, kiểm tra bytes, `javap` và logs.
14. **Nếu class file version hỗ trợ nhưng method thiếu lúc runtime, lỗi nào có thể xảy ra?** `NoSuchMethodError` do binary incompatibility.

## Tài liệu chính thức

- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findClass`, `defineClass`, tên binary, xử lý CNFE và các lỗi khi định nghĩa class.
- [Java SE 21 — ClassFormatError](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassFormatError.html): file class hỏng hoặc không diễn giải được.
- [Java SE 21 — UnsupportedClassVersionError](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/UnsupportedClassVersionError.html): lỗi major/minor version và quan hệ kế thừa với ClassFormatError.
- [Java SE 21 — VerifyError](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/VerifyError.html): lỗi verification của JVM.
- [Java SE 21 — NoClassDefFoundError](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/NoClassDefFoundError.html): lớp không còn định nghĩa khả dụng theo đường JVM yêu cầu.
- [JVM Specification — §4 The Class File Format](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html): định dạng class file, major/minor, constant pool, bytecode attributes.
- [JVM Specification — §5 Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html): loading, resolution, verification và class initialization.

**Kết luận:** Khi custom loader không sử dụng được một class, hãy phân biệt **“không tìm được bytes”** (`ClassNotFoundException`) với **“bytes có nhưng không phải class hợp lệ”** (`ClassFormatError`), **“class version không hỗ trợ”** (`UnsupportedClassVersionError`), **“bytecode không qua verifier”** (`VerifyError`) và **“liên kết/khởi tạo không thành công”** (các `LinkageError`). Giữ nguyên loại lỗi và nguyên nhân gốc là chìa khóa sửa đúng classpath, JDK target, bytecode generator hoặc cấu trúc dependency.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-009">Không định nghĩa lại cùng một tên trong cùng loader</a>

<details>
<summary>Xem chi tiết</summary>

# Không định nghĩa lại cùng một tên trong cùng ClassLoader

## 1. Quy tắc phải nhớ

Trong Java, một **ClassLoader cụ thể không được tạo lại một định nghĩa class mới với cùng binary name** sau khi loader đó đã là *initiating loader* của class có tên ấy. Nếu cố gọi `defineClass` một lần nữa cho cùng tên trong cùng loader, JVM từ chối, thường bằng **`LinkageError`** (thông báo điển hình chứa `attempted duplicate class definition`).

Đây không phải do file `.class` đã biến mất và cũng không phải do bytecode mới có nội dung giống bytecode cũ. Đó là quy tắc về **định danh kiểu ở runtime** của JVM.

~~~text
ClassLoader L1
  ├── defineClass("demo.plugin.Entry", bytes-v1) -> Class Entry#1
  └── defineClass("demo.plugin.Entry", bytes-v2) -> LinkageError
      (kể cả bytes-v2 giống hệt bytes-v1)

ClassLoader L2 (đối tượng loader mới)
  └── defineClass("demo.plugin.Entry", bytes-v2) -> Class Entry#2

Entry#1 != Entry#2 vì defining loaders khác nhau.
~~~

**Hai nguyên tắc tưởng đối lập nhưng đều đúng:**

1. **Cùng binary name + cùng defining loader** là cùng một danh tính runtime class; không thể tạo hai định nghĩa khác nhau bằng cách gọi `defineClass` lặp lại.
2. **Cùng binary name + hai defining loaders khác nhau** có thể đại diện **hai runtime types khác nhau**. Những class ấy có thể không ép kiểu qua lại và có thể bị ràng buộc thêm bởi **loading constraints** khi chúng tương tác qua API.

Bài này thuộc chủ đề **Loader tùy chỉnh**. Trọng tâm là hiểu vì sao `loadClass` nên trả về class đã được nạp, vì sao gọi `defineClass` trực tiếp lần thứ hai sai, và cách tổ chức loader cho cơ chế plugin/reload.

### Kết quả học tập

Sau bài, bạn có thể:

- Phân biệt **binary name**, **defining loader**, **initiating loader** và **`Class<?>` object**.
- Giải thích nhiệm vụ của `findLoadedClass` trong thuật toán `loadClass` mặc định.
- Dự đoán khi nào có cùng `Class<?>`, khi nào phát sinh `LinkageError`.
- Tự chạy ví dụ Java 17+ tạo một custom loader, gọi `loadClass` hai lần, thử `defineClass` lại và so sánh với một loader mới.
- Hiểu rủi ro nếu override `loadClass` sai, nhất là với thread concurrency.
- Phân biệt **nạp lại bằng loader khác** với **class redefinition/retransformation** thông qua instrumentation.

## 2. Định danh runtime type: tên không đứng một mình

### 2.1. Class name là một phần của định danh

Một class file chứa tên nhị phân như:

~~~text
demo.plugin.Entry
~~~

Khi JVM tạo class từ định nghĩa do một loader cung cấp, **defining loader** của class là thành phần làm nên runtime identity. Có thể mô tả:

~~~text
RuntimeClassIdentity = (binaryName, definingClassLoader)
~~~

Hai class cùng tên nhưng loader khác nhau có thể là hai loại khác nhau:

~~~java
Class<?> a = loaderA.loadClass("demo.plugin.Entry");
Class<?> b = loaderB.loadClass("demo.plugin.Entry");

System.out.println(a.getName().equals(b.getName())); // có thể true
System.out.println(a == b);                           // có thể false
~~~

Đây là **snippet minh họa**, cần hai loader được cấu hình tìm được class đúng nguồn. `a == b` mới trực tiếp kiểm tra class identity, còn `getName()` chỉ kiểm tra chuỗi binary name.

### 2.2. Hai vai trò loader khác nhau

- **Defining loader:** loader đã thực sự định nghĩa một class, thể hiện qua `Class.getClassLoader()` (với bootstrap-defined classes, API có thể trả `null`).
- **Initiating loader:** loader đã yêu cầu JVM load một class theo tên và được JVM ghi nhận là khởi xướng việc tìm lớp đó; loader có thể nhận class do parent định nghĩa theo delegation.

Ví dụ:

~~~text
L_child.loadClass("java.lang.String")
    -> ủy quyền lên bootstrap
    -> trả về java.lang.String.class

Defining loader của String: bootstrap
L_child: có thể được JVM ghi nhận là initiating loader cho tên đó.
~~~

Điểm tinh tế này giải thích vì sao quy tắc trong [JVMS §5.3.5](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.5) **không chỉ là** “nếu loader đã từng gọi trực tiếp `defineClass` thì cấm lần thứ hai”. JVM kiểm tra trạng thái loading theo **initiating loader và binary name**. Nếu loader đã khởi xướng và được ghi nhận một class cùng tên qua parent, cố tự định nghĩa lại tên ấy trong cùng loader cũng là một hướng vi phạm.

### 2.3. Từ khóa “cùng loader” nghĩa là cùng đối tượng

Hai instance khác nhau của **cùng một subclass ClassLoader** không phải là một loader:

~~~java
ByteLoader loaderOne = new ByteLoader(path);
ByteLoader loaderTwo = new ByteLoader(path);
~~~

Dù cả hai dùng cùng đường `path` và cùng bytes, `loaderOne != loaderTwo`. Nếu mỗi loader tự định nghĩa `demo.plugin.Entry`, JVM có thể tạo hai `Class<?>` riêng. Đừng dùng tên class của loader (`getClass().getName()`), hash string hay `toString()` để thay thế so sánh identity `==`.

## 3. `loadClass` và `defineClass` không có cùng nhiệm vụ

### 3.1. `loadClass(String)` là cổng tìm class theo tên

Theo [Java SE 21 `ClassLoader` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html), triển khai mặc định của `loadClass(name, resolve)` làm việc theo thứ tự cơ bản:

1. Gọi **`findLoadedClass(name)`** để xem loader đã được JVM ghi nhận là initiating loader cho class cùng tên chưa. Nếu đã có, **trả lại class đó**.
2. Nếu chưa có, thử **parent delegation** (hoặc bootstrap loader khi parent là `null`).
3. Nếu parent không tìm ra, gọi **`findClass(name)`** của loader.
4. Nếu yêu cầu `resolve == true`, gọi `resolveClass` với kết quả theo hợp đồng.

Giả mã để hình dung (không phải mã nguồn nguyên văn của JDK):

~~~java
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        Class<?> result = findLoadedClass(name);

        if (result == null) {
            try {
                result = (getParent() == null)
                        ? findBootstrapClassSomehow(name)
                        : getParent().loadClass(name);
            } catch (ClassNotFoundException notFound) {
                result = findClass(name);
            }
        }

        if (resolve) {
            resolveClass(result);
        }
        return result;
    }
}
~~~

**Lưu ý:** `findBootstrapClassSomehow` là **tên giả để mô tả bước ủy quyền**, không phải method Java có sẵn. Đoạn mã này chỉ nhằm giải thích thuật toán; bạn **không được copy để biên dịch**. Trong thực hành hãy giữ triển khai `loadClass` chuẩn rồi override `findClass`.

Đặc biệt, **`findLoadedClass` có thể trả class do parent định nghĩa** nếu loader hiện tại là *initiating loader* của class đó. Tên phương thức dễ gây hiểu nhầm rằng chỉ trả những class đã được loader này trực tiếp `defineClass`.

### 3.2. `defineClass` tạo định nghĩa mới từ bytes

Họ phương thức protected như:

~~~java
protected final Class<?> defineClass(
        String name, byte[] bytes, int offset, int length)
~~~

nhận một biểu diễn class file hợp lệ để JVM tạo class. `defineClass` **không được thiết kế thành API idempotent kiểu “nếu có rồi thì trả class cũ”**.

Khác biệt:

| Hoạt động | Ý định | Khi class cùng tên đã được loader khởi xướng |
|---|---|---|
| `loader.loadClass(name)` theo triển khai mặc định | Tìm class theo tên, tái sử dụng kết quả nếu đã nạp | Thường trả lại cùng `Class<?>` |
| `findLoadedClass(name)` trong subclass | Kiểm tra trạng thái JVM đã ghi nhận đối với initiating loader | Trả class tương ứng hoặc `null` |
| `defineClass(name, bytes, ...)` | **Định nghĩa** class mới từ bytecode | Cố định nghĩa trùng → **`LinkageError`** |
| `findClass(name)` do bạn override | Tìm bytecode nguồn riêng rồi thường gọi `defineClass` khi cần | Nên chỉ được gọi trong đường có kiểm tra/cache phù hợp |
| `new AnotherLoader(...).loadClass(name)` | Loader **khác** có thể định nghĩa type mới cùng binary name | Có thể có `Class<?>` khác (theo nguồn, delegation và constraints) |

**Điểm cốt lõi:** nếu mục đích là lấy class đã tồn tại, gọi `loadClass`; **không gọi lại `defineClass` để “refresh” class cũ**.

## 4. Điều gì thực sự gây `LinkageError`?

### 4.1. Quy tắc của JVM

Theo JVMS `5.3.5, khi derive một class từ class-file representation cho tên `N` theo loader `L`, JVM kiểm tra xem `L` đã là initiating loader của một class/interface tên `N` hay chưa. Nếu đã ghi nhận, phép derive mới là **không hợp lệ** và ném **`LinkageError`**.

Ví dụ:

~~~text
L = ByteLoader@1
N = demo.plugin.Entry

L.loadClass(N)        -> Class<?> C1
L.loadClass(N)        -> chính C1
L.defineClass(N, ...) -> LinkageError
~~~

Thông báo cụ thể của `LinkageError` phụ thuộc JVM; bài chỉ dựa vào **loại lỗi và quy tắc**, không phụ thuộc vào nguyên văn message có thể khác theo runtime/vendor.

### 4.2. Nếu bytes mới y hệt thì sao?

Vẫn **không** được định nghĩa lại cùng tên trong cùng initiating loader. Quy tắc dựa trên runtime loading identity, **không phải so hash của file**.

- Bytes giống hệt → không trở thành “định nghĩa trùng nhưng hợp lệ”.
- Bytes đã đổi → không được tự động thay definition cũ.
- Đổi một file `.class` hoặc thay nội dung JAR trên disk → một `Class<?>` đã load trong JVM không bị viết đè.
- Gọi `ClassLoader.loadClass(name)` lần nữa sau khi thay file → mặc định thường vẫn nhận `Class<?>` cũ vì cache theo initiating loader.

Điều này giải thích tại sao cơ chế hot reload không thể đơn giản là “ghi đè JAR rồi gọi `loadClass`”.

### 4.3. Khác `ClassNotFoundException`

Nếu tìm bytecode theo tên nhưng **không có file**, một custom `findClass` hợp lý sẽ ném `ClassNotFoundException`.

Nếu có bytes nhưng **cố định nghĩa trùng**, lỗi là **`LinkageError`**; JVM không nói rằng class không tồn tại, mà ngược lại **một class tương ứng đã được ghi nhận**.

Các lỗi khác có thể xảy ra khi define class: `ClassFormatError` (class file không hợp lệ), `UnsupportedClassVersionError` (classfile version không được JVM hỗ trợ), `NoClassDefFoundError` nếu tên truyền không tương thích với tên mà bytes khai báo, `SecurityException` trong những trường hợp package/security constraints. Không nên gom tất cả vào “duplicate class definition”.

## 5. Thực hành Java 17+: lặp `loadClass`, cố `defineClass` lại và dùng loader mới

Chương trình sau chỉ cần Java 17 trở lên, không cần Gradle/Maven, không tải network. Nó tạo:

- **Một class `demo.plugin.Entry`** được biên dịch thành file `.class` trong thư mục plugin, tách khỏi app classpath.
- **Một `ByteLoader`** custom subclass chỉ override `findClass` để đọc class bytes và gọi `defineClass`.
- **Một method `forceDefineAgain`** chỉ dùng trong phòng thí nghiệm để thử cố ý định nghĩa trùng và quan sát `LinkageError`.
- **Hai instance loader độc lập** để đối chiếu identity.

### 5.1. Cây thư mục

~~~text
demo/
  src/
    plugin/
      demo/
        plugin/
          Entry.java
    app/
      DuplicateDemo.java
  build/
    plugin/                 # output riêng của Entry
    app/                    # output riêng của DuplicateDemo
~~~

**Quan trọng:** khi chạy, chỉ để `build/app` trong `-cp`. Nếu thêm `build/plugin` vào system classpath, parent-first delegation có thể tìm `Entry` trước; như vậy thí nghiệm custom `ByteLoader` không còn đo đúng việc nó tự định nghĩa class.

### 5.2. Tệp `src/plugin/demo/plugin/Entry.java`

~~~java
package demo.plugin;

public class Entry {
    public String id() {
        return "entry";
    }
}
~~~

`Entry` là một class bình thường. Không có logic static đặc biệt vì chủ đề bài là **class definition** chứ không phải class initialization.

### 5.3. Tệp `src/app/DuplicateDemo.java`

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DuplicateDemo {
    static final String NAME = "demo.plugin.Entry";

    static class ByteLoader extends ClassLoader {
        private final Path root;

        ByteLoader(Path root) {
            super(DuplicateDemo.class.getClassLoader());
            this.root = root;
        }

        private byte[] bytes(String name) throws IOException {
            return Files.readAllBytes(
                    root.resolve(name.replace('.', '/') + ".class"));
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            try {
                byte[] data = bytes(name);
                return defineClass(name, data, 0, data.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }

        Class<?> forceDefineAgain(String name) throws IOException {
            byte[] data = bytes(name);
            return defineClass(name, data, 0, data.length);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("build/plugin");
        ByteLoader firstLoader = new ByteLoader(root);

        Class<?> first = firstLoader.loadClass(NAME);
        Class<?> again = firstLoader.loadClass(NAME);
        System.out.println("repeatSameClass=" + (first == again));
        System.out.println("firstLoaderOwnsClass="
                + (first.getClassLoader() == firstLoader));

        try {
            firstLoader.forceDefineAgain(NAME);
            System.out.println("duplicateDefinition=UNEXPECTED");
        } catch (LinkageError expected) {
            System.out.println("duplicateDefinition="
                    + expected.getClass().getSimpleName());
        }

        ByteLoader secondLoader = new ByteLoader(root);
        Class<?> other = secondLoader.loadClass(NAME);
        System.out.println("sameBinaryName="
                + first.getName().equals(other.getName()));
        System.out.println("differentClassObjects=" + (first != other));
        System.out.println("secondLoaderOwnsClass="
                + (other.getClassLoader() == secondLoader));
    }
}
~~~

**Giới hạn của ví dụ:** `forceDefineAgain` **cố ý vi phạm điều kiện JVM** để minh họa lỗi. Không sao chép method đó vào production loader với mục tiêu “cập nhật class”. Trong loader thật, `findClass` thường phụ trách tra bytecode, còn `loadClass` thực hiện cached/delegation lookup.

### 5.4. Biên dịch/chạy

Đứng ở thư mục `demo` trên Linux/macOS:

~~~sh
mkdir -p build/plugin build/app
javac --release 17 -d build/plugin src/plugin/demo/plugin/Entry.java
javac --release 17 -d build/app src/app/DuplicateDemo.java
java -cp build/app DuplicateDemo
~~~

Trên Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force build/plugin, build/app | Out-Null
javac --release 17 -d build/plugin src/plugin/demo/plugin/Entry.java
javac --release 17 -d build/app src/app/DuplicateDemo.java
java -cp build/app DuplicateDemo
~~~

**Stdout chính xác**, đã biên dịch với `javac --release 17` và kiểm chứng bằng Java:

~~~text
repeatSameClass=true
firstLoaderOwnsClass=true
duplicateDefinition=LinkageError
sameBinaryName=true
differentClassObjects=true
secondLoaderOwnsClass=true
~~~

### 5.5. Giải thích từng dòng

| Output | Ý nghĩa |
|---|---|
| `repeatSameClass=true` | Hai lần `firstLoader.loadClass(NAME)` trả **cùng object `Class<?>`**, không tạo definition mới |
| `firstLoaderOwnsClass=true` | `Entry` lần đầu do instance `firstLoader` thực sự định nghĩa |
| `duplicateDefinition=LinkageError` | `forceDefineAgain` cố gọi `defineClass` lần hai trên **chính loader cũ**, JVM từ chối |
| `sameBinaryName=true` | Hai runtime class đều mang tên `demo.plugin.Entry` |
| `differentClassObjects=true` | Hai instance loader khác nhau có thể tạo hai runtime classes khác identity |
| `secondLoaderOwnsClass=true` | Class thứ hai thực sự do `secondLoader` định nghĩa, không chỉ được parent trả về |

**Điều cần học, không phải chỉ nhớ output:** loader cache làm `loadClass` có tính ổn định; `defineClass` không phải phép thay đổi class; muốn một definition mới cùng tên, phải có điều kiện cho loader **khác** định nghĩa, hoặc dùng cơ chế instrumentation đặc biệt theo hợp đồng riêng.

### 5.6. Tự thử các biến thể

**Thử A — Gọi `loadClass` lần thứ ba:** sau khi bắt `LinkageError`, gọi lại `firstLoader.loadClass(NAME)` và so với `first`. Kết quả vẫn là cùng `Class<?>`, vì phép thử định nghĩa trùng thất bại không tạo được định nghĩa mới.

**Thử B — Thay bytes sau khi đã load:** nếu bạn đổi `Entry.id()` trong `Entry.java` rồi biên dịch lại trong một tiến trình chạy khác, class được loader cũ trong JVM đang chạy **không được cập nhật tự động**. Với JVM mới và loader mới, class mới có thể được định nghĩa từ bytes mới. Để thử trong cùng JVM phải tổ chức bước cập nhật file và loader sao cho có thể quan sát được thời điểm đọc bytes; đừng gán cho class đã load một hành vi tự làm mới.

**Thử C — Gọi `findClass` trực tiếp liên tục:** `findClass` thường đọc bytes rồi gọi `defineClass`; nếu tự cho phép code bên ngoài gọi đi gọi lại, bạn có thể lại gặp duplicate definition. Thiết kế tốt là cung cấp API `loadClass` có kiểm tra `findLoadedClass`/delegation của JDK, không expose `findClass` như “reloader”.

**Thử D — Đưa `build/plugin` vào application classpath:** parent có thể định nghĩa `Entry`, làm `firstLoader.loadClass(NAME)` trả class parent thay vì tạo class bằng `ByteLoader.findClass`. `firstLoader.getClass().getName()` vẫn như cũ, nhưng `first.getClassLoader()` đổi. Khi đã có initiated record với tên đó, cố tự define class trùng trong child vẫn không phải cơ chế hợp lệ.

## 6. Đồng thời (concurrency): vì sao phải khóa quá trình load?

Một custom loader có thể bị nhiều threads gọi `loadClass("demo.plugin.Entry")` cùng lúc. Nếu cả hai cùng thấy “chưa được load” rồi cùng định nghĩa, một thread có thể thành công còn thread kia chạm `LinkageError`. Đây là rủi ro nếu override `loadClass` một cách bất cẩn, không giữ cơ chế đồng bộ đúng.

### 6.1. Thiết kế khuyến nghị

**Ưu tiên override `findClass`, không override `loadClass`** nếu không có nhu cầu đặc biệt. Phương thức `loadClass(String, boolean)` mặc định có cơ chế đồng bộ theo **`getClassLoadingLock(name)`**, đồng thời kiểm tra `findLoadedClass` trước khi ủy quyền/tìm bytes.

~~~text
Thread T1: loadClass(N)
  -> lock dành cho N
  -> findLoadedClass(N) == null
  -> parent delegation thất bại
  -> findClass(N) -> defineClass(N) thành công
  -> release lock

Thread T2: loadClass(N)
  -> lock dành cho N
  -> findLoadedClass(N) trả C1
  -> return C1; KHÔNG define lại
~~~

Sơ đồ mô tả đường load có kết quả do loader này định nghĩa; khi parent có class, bước `findClass` có thể không được gọi.

### 6.2. Parallel-capable ClassLoader

Nếu subclass được đăng ký parallel-capable bằng `ClassLoader.registerAsParallelCapable()` theo đúng quy tắc (các superclasses liên quan cũng phải đáp ứng điều kiện), `getClassLoadingLock(name)` có thể dùng **khóa riêng theo tên class** để nhiều tên class được xử lý đồng thời.

Điều này **không cho phép cùng loader định nghĩa hai class trùng binary name**. Nó chỉ thay chiến lược đồng bộ và throughput. Nếu override `loadClass`, cần kiểm tra cache, delegation, locking và loading constraints; nếu làm sai, `LinkageError` có thể chỉ xuất hiện trong tình huống nhiều threads.

Không nên bỏ qua khóa rồi cố catch `LinkageError` như cách sửa race. Đó là che dấu nguyên nhân, dễ khiến các threads có kết quả class resolution không ổn định.

## 7. Kế thừa, delegation và “định nghĩa trùng”

### 7.1. Parent đã định nghĩa class: child thường không tự định nghĩa lại

Trong mô hình parent-first thông thường:

~~~text
Parent loader P:
  defines demo.api.Service

Child loader C:
  C.loadClass("demo.api.Service")
     -> P.loadClass(...), trả Service của P
     -> C không gọi findClass/defineClass cho tên này
~~~

Đây là cách phổ biến để chia sẻ **cùng API type** giữa host và plugin. Nếu sau đó child cố gọi `defineClass("demo.api.Service", bytes)` để tạo class cùng tên trong chính child đã initiated load đó, nó có thể vi phạm quy tắc JVMS `5.3.5.

### 7.2. Child-first có thể hợp lệ với tên chưa khởi xướng

Một số containers dùng child-first cho namespace được cô lập. Nếu `C` **chưa** là initiating loader của `com.plugin.Widget`, và parent chưa được C dùng để ghi nhận class này, C có thể tự define class tên ấy theo chính sách child-first. Nhưng:

- Cần hiểu rõ các protected packages và package sealing/certificates khi define class.
- Một class tên giống class parent ở **hai defining loaders** là hai runtime types khác nhau.
- Type identity khác nhau có thể gây `ClassCastException` và loading-constraint `LinkageError` khi signature xuyên qua loader boundaries.
- Các lớp platform lõi như `java.lang.*` không được tùy tiện định nghĩa bởi custom loader.

Vì vậy, **“định nghĩa cùng binary name ở hai loaders khác nhau có thể được” không đồng nghĩa “mọi trường hợp shadow class đều an toàn”**.

### 7.3. Loading constraints giữa loaders

JVM đặt các **loading constraints** khi liên kết tham chiếu qua các type signature, bảo đảm một tên kiểu dùng ở những vị trí nhất định phải biểu diễn cùng runtime class khi có tương tác tương ứng. Nếu hai loaders đã định nghĩa hai classes khác nhau cho tên cần thống nhất, JVM có thể ném `LinkageError`.

Đừng trộn hai tình huống:

1. **Duplicate definition trong cùng initiating loader:** JVM từ chối cố derive/define tên trùng theo `5.3.5`.
2. **Loading constraint violation giữa các loaders:** mỗi loader có thể đã có class riêng, nhưng việc liên kết giữa chúng đòi cùng identity và phát hiện mâu thuẫn theo `5.3.4`.

Cả hai liên quan `LinkageError`, nhưng **điểm sửa khác nhau**. Tình huống đầu thường sửa bằng cache/delegation đúng; tình huống hai phải thiết kế classpath, shared API và ranh giới loaders.

## 8. Nếu cần hot reload/hot deploy thì làm thế nào?

### 8.1. Cách phổ biến: tạo ClassLoader mới

Một hệ plugin có thể dùng:

~~~text
Version 1:
  PluginLoader_v1 -> defines plugin.Feature
  application giữ Feature_v1 và instance_v1

Version 2:
  tạo PluginLoader_v2 (instance loader mới)
  PluginLoader_v2 -> defines plugin.Feature từ bytes phiên bản 2
  application đổi tham chiếu hoạt động sang instance_v2
  giải phóng tham chiếu cũ khi an toàn
~~~

Lúc này `plugin.Feature` từ loader v1 và từ loader v2 là **hai runtime types** khác nhau. Framework thường cần một interface API chung do parent loader định nghĩa để trao đổi:

~~~java
// Mã minh họa: interface API được host và plugin nhìn qua loader chung
package demo.api;

public interface FeatureService {
    String run();
}
~~~

Plugin implementations ở những loader khác nhau đều có thể triển khai `FeatureService` nếu cùng dùng đúng interface runtime của parent. Host nên giữ object theo **type API chung** thay vì ép kiểu giữa implementation class của hai loader versions.

Đây là cách đổi **định nghĩa class thông qua loader isolation**, không phải `defineClass` đè lên một `Class<?>` đã tồn tại.

### 8.2. Điều kiện để class loader cũ có thể được thu gom

Tạo loader v2 **không tự unload v1**. Loader v1 và các class của nó có thể còn sống vì:

- Instance cũ được singleton/static cache giữ.
- `Thread Context ClassLoader` của một worker thread còn trỏ tới v1.
- Thread do plugin tạo vẫn đang chạy.
- `ServiceLoader`/reflection/proxy/callback caches còn tham chiếu class v1.
- Resource/JAR file handles chưa được đóng.

Muốn retire plugin đúng cách cần dừng hoặc chuyển tác vụ, xóa tham chiếu/caches, khôi phục TCCL, đóng URLClassLoader nếu thích hợp và để GC làm việc theo điều kiện JVM. **Không có lời hứa rằng class unload ngay khi `close()` được gọi**.

### 8.3. Instrumentation là một cơ chế khác

Java có `java.lang.instrument.Instrumentation.redefineClasses(...)` và `retransformClasses(...)` khi JVM hỗ trợ và agent có capability cần thiết. Những API này có thể **thay đổi định nghĩa của class đã nạp theo giới hạn JVM**, nhưng:

- **Không tạo một `Class<?>` mới bằng `defineClass` lần hai trong cùng loader**.
- Phụ thuộc công cụ/JVM/agent và thường có giới hạn về thay đổi cấu trúc class/schema.
- Cần thiết kế, kiểm thử compatibility và vòng đời instrumentation riêng.

Do đó, **Java agent hot swap** và **plugin reload bằng ClassLoader mới** là hai hướng khác nhau; không dùng một lệnh `defineClass` lặp lại để thay thế cả hai.

## 9. Bảng lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Khả năng nguyên nhân | Cách kiểm tra |
|---|---|---|
| `LinkageError: attempted duplicate class definition` | Cùng loader đang bị gọi `defineClass` lại cho tên đã initiated | Trace code override `loadClass`/`findClass` và số lần `defineClass` |
| Thay file `.class` xong gọi `loadClass` vẫn thấy hành vi cũ | Loader đã có class, trả lại `Class<?>` cũ | So `first == second`, source bytes, lifecycle loader |
| Hai class cùng tên nhưng ép kiểu lỗi | Defining loaders khác nhau | So `a.getClassLoader() == b.getClassLoader()` và `a == b` |
| `LinkageError: loader constraint violation` | Hai loaders không thống nhất identity của type trong signature dùng chung | Xem call signatures, shared API, loader hierarchy |
| `ClassNotFoundException` | Loader không tìm được bytes/class theo tên | Kiểm tra binary name, đường JAR/directory và delegation |
| `NoClassDefFoundError` | JVM không có class cần thiết ở runtime hoặc class từng lỗi initialization | Xem `Caused by`, classpath/JAR, lỗi ban đầu |
| `ClassFormatError` | Bytes đầu vào không hợp lệ theo classfile format | Kiểm tra bytes đọc được và compiler/JDK |
| `UnsupportedClassVersionError` | Class file version vượt JVM đang chạy | So `javac --release` và `java -version` |
| `SecurityException` khi define class ở package hạn chế | Package/signer/security condition bị vi phạm | Không tự define `java.*`, kiểm tra protection domain |
| Chỉ lỗi khi chạy đồng thời | Override `loadClass` sai cache/lock dẫn đến hai luồng cùng define | Giữ `getClassLoadingLock`/`findLoadedClass` hoặc dùng loadClass mặc định |

### Checklist trước khi sửa custom ClassLoader

1. Có đúng **binary name** của class chưa?
2. Một class đã được loader này initiated/load chưa? Trong subclass, `findLoadedClass(name)` là công cụ kiểm tra hữu ích.
3. Code có đang **gọi `defineClass` trực tiếp mỗi lần** thay vì đi qua `loadClass` không?
4. Parent delegation có định nghĩa class cùng tên trước không?
5. Loader có được dùng từ nhiều threads? Locking/cache có đúng?
6. Loader và class có phải của đúng plugin/version không?
7. Có vô tình đưa plugin classes vào parent/system classpath không?
8. Có đang nhầm `ClassNotFoundException` với `LinkageError`?
9. Nếu mục tiêu là reload, nên tạo loader mới và hợp đồng API chung thay vì redefine trùng?
10. Có chỗ giữ reference tới loader cũ làm class unloading chậm không?

## 10. Những nhận định dễ sai

1. **“`defineClass` lần hai sẽ cập nhật implementation cũ.”** Sai; JVM không coi đó là update.
2. **“Bytes giống nhau thì định nghĩa trùng được.”** Sai; JVM xét trạng thái class theo name/loader.
3. **“`loadClass` lần hai cũng gọi `defineClass` lần hai.”** Sai đối với thuật toán mặc định khi class đã có.
4. **“`findLoadedClass` chỉ thấy những class do loader này tự định nghĩa.”** Chưa đủ; method dựa trên trạng thái *initiating loader* được JVM ghi nhận.
5. **“Nếu tạo hai đối tượng loader cùng subclass, chúng phải dùng chung `Class<?>`.”** Sai nếu mỗi loader tự định nghĩa class; phải xét delegation.
6. **“Cùng binary name là cùng Java type.”** Sai khi defining loader khác nhau.
7. **“Đổi JAR trên disk là JVM tự load lại class cùng tên.”** Sai với class đã định nghĩa.
8. **“Catch `LinkageError` rồi gọi `defineClass` lại sẽ sửa được.”** Sai; lặp lại thao tác vi phạm không giải quyết identity.
9. **“Child-first luôn giải quyết được mọi trùng class.”** Sai; có thể sinh loading constraint violations hoặc khác API type.
10. **“Có loader mới thì plugin cũ tự unload ngay.”** Sai khi object/thread/cache vẫn giữ loader cũ.
11. **“Java instrumentation đơn giản chỉ gọi `defineClass` lần hai.”** Sai; đó là cơ chế cập nhật theo hợp đồng khác.
12. **“Một class loader có thể làm mới mọi method bằng cách gọi `loadClass` lại.”** Sai; phải phân biệt class lookup với thay đổi definition.

## 11. Câu hỏi tự kiểm tra

1. Hai yếu tố nào định danh một runtime class thông thường của JVM?
2. `findLoadedClass(name)` có chức năng gì trong thuật toán `loadClass` mặc định?
3. Vì sao gọi `firstLoader.loadClass(NAME)` hai lần trong demo trả cùng `Class<?>`?
4. Vì sao `forceDefineAgain(NAME)` ném `LinkageError` dù bytes không đổi?
5. Phân biệt **defining** và **initiating** ClassLoader; khi child được parent trả class, ai định nghĩa class đó?
6. Khi nào hai loaders khác nhau có thể tạo hai `Class<?>` cùng binary name?
7. Vì sao `first.getName().equals(other.getName())` có thể là `true` nhưng `first == other` là `false`?
8. Tại sao không đưa `build/plugin` lên system classpath khi muốn chứng minh `ByteLoader` tự định nghĩa `Entry`?
9. Nếu custom loader dùng hai threads cùng tải `Entry`, cần giữ cơ chế nào để không define trùng?
10. `registerAsParallelCapable` có cho phép định nghĩa hai class cùng binary name trong cùng loader không?
11. Loader constraint violation khác duplicate definition trong cùng loader như thế nào?
12. Đổi file `Entry.class` trên disk rồi gọi `loadClass` lại có bảo đảm code mới được dùng không?
13. Khi muốn cập nhật plugin mà vẫn cùng tên class, vì sao tạo loader instance mới là một lựa chọn phổ biến?
14. Nếu host và plugin v1/v2 trao đổi object, vì sao một interface chung do parent định nghĩa có giá trị?
15. `URLClassLoader.close()` có bảo đảm class cũ bị unload ngay không?
16. Instrumentation `redefineClasses` khác phép gọi lại `defineClass` ở điểm căn bản nào?
17. Nếu một loader đã initiated class cùng tên nhờ parent delegation, vì sao cố define lại class đó trong cùng loader là một sai lầm?
18. Khi gặp `LinkageError`, những thông tin nào trong log nên ghi để phân biệt định nghĩa trùng với loading-constraint violation?

**Tiêu chí hoàn thành:** bạn tự chạy đúng demo, giải thích đủ sáu dòng stdout, phân biệt `loadClass` và `defineClass`, nêu được điều kiện làm nên class identity, hiểu nguyên nhân `LinkageError` và chọn được cách reload an toàn qua loader mới hoặc cơ chế instrumentation riêng khi phù hợp.

## 12. Tài liệu đặc tả và API chính thức

- [JVMS Java SE 21 §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): class identity, defining/initiating loaders và quy trình tạo class.
- [JVMS Java SE 21 §5.3.5 — Deriving a Class from a Class File Representation](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.5): điều kiện JVM từ chối derive class trùng tên đã initiated và các lỗi liên quan.
- [JVMS Java SE 21 §5.3.4 — Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.4): vì sao có ràng buộc type identity giữa các loaders khi liên kết.
- [Java SE 21 `ClassLoader` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findLoadedClass`, `findClass`, `defineClass`, `getClassLoadingLock` và parallel capable.
- [Java SE 21 `Instrumentation` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/Instrumentation.html): `redefineClasses`/`retransformClasses`, cách cập nhật class có giới hạn riêng.

Bài áp dụng cho Java 17+; chương trình minh họa đã biên dịch bằng `javac --release 17` và chạy độc lập, không cần build tool hoặc framework. Hãy dựa vào **Class object identity** và defining loader thực tế khi chẩn đoán, không chỉ dựa vào chuỗi tên class.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-010">Bổ sung khả năng tìm tài nguyên tương ứng với lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Bổ sung khả năng tìm tài nguyên tương ứng với lớp trong ClassLoader tùy chỉnh

## 1. Vấn đề: tải được lớp nhưng không tìm được tài nguyên

Một ClassLoader tùy chỉnh có thể đọc bytecode từ thư mục, cơ sở dữ liệu, JAR hoặc kho lưu trữ riêng rồi dùng `defineClass(...)` để tạo runtime class. Nhưng nếu nó **chỉ override `findClass(...)`**, các tài nguyên đi kèm lớp — như `config.properties`, `messages.txt`, biểu mẫu hoặc descriptor dịch vụ — chưa chắc được tìm thấy bởi:

~~~java
Class<?> pluginType = customLoader.loadClass("demo.plugin.Widget");
URL url = pluginType.getResource("message.txt");
~~~

Hai thao tác đó đi qua **hai đường tra cứu khác nhau**:

- **Class lookup:** `loadClass` (thường ủy quyền parent trước) rồi `findClass` của custom loader.
- **Resource lookup:** `getResource`/`getResources` (thường hỏi parent trước) rồi `findResource`/`findResources` của custom loader.

Vì vậy, **nạp class thành công không tự động làm cho file dữ liệu bên cạnh `.class` có thể được tra cứu**. Khi viết custom ClassLoader, cần thiết kế đường tra cứu tài nguyên tương ứng với nơi lưu bytecode.

Mục tiêu bài này: từ một **thư mục gốc chứa cả `.class` và resource**, triển khai đủ `findClass`, `findResource`, `findResources`; kiểm tra resource tương đối với package, resource trùng tên với parent, và tránh hiểu lầm về thứ tự lookup. Ví dụ dùng **Java 17+** và chạy độc lập bằng `javac`/`java`.

## 2. Cặp phương thức class và resource

| API | Vai trò | Thường do ai gọi? |
|---|---|---|
| `ClassLoader.loadClass(name)` | Entry point tìm class, mặc định parent-first | Host, reflection, JVM/framework |
| `ClassLoader.findClass(name)` | Hook để loader tùy chỉnh **định nghĩa class** khi parent không tìm thấy | `loadClass` |
| `ClassLoader.getResource(name)` | Entry point tìm **một URL** của resource, mặc định parent-first | Host, `Class.getResource` qua defining loader |
| `ClassLoader.findResource(name)` | Hook tìm resource trong **nguồn riêng** của loader | `getResource` |
| `ClassLoader.getResources(name)` | Liệt kê **nhiều URL** theo cơ chế parent rồi resource riêng | Framework và ứng dụng |
| `ClassLoader.findResources(name)` | Hook liệt kê các resource trong **nguồn riêng** của loader | `getResources` |
| `ClassLoader.resources(name)` | Biểu diễn nhiều URL bằng `Stream<URL>` (Java 9+) | Ứng dụng dùng Stream API |
| `ClassLoader.getResourceAsStream(name)` | Mở `InputStream` cho resource đầu tiên | Ứng dụng đọc dữ liệu |

**Nguyên tắc thiết kế:** Khi loader của bạn có một kho lưu trữ chung cho bytecode và resource, `findClass` và `findResource(s)` nên dựa trên **cùng một root, cùng quy tắc ánh xạ đường dẫn và cùng vòng đời nguồn dữ liệu**.

Các hook `findResource`/`findResources` không định nghĩa class và không tự khởi tạo class. Chúng chỉ trả URL mà caller có thể mở để lấy dữ liệu. Ngược lại, `findClass` không tự quảng bá mọi file không phải class trong thư mục nguồn cho resource API.

## 3. Mapping từ tên class và tên resource

Giả sử tên binary class là `demo.plugin.Widget` và package là `demo.plugin`:

~~~text
Binary class name:     demo.plugin.Widget
Class file:            demo/plugin/Widget.class

Class-relative name:   message.txt
Resolved resource:     demo/plugin/message.txt

Root-relative name:    demo/plugin/message.txt
~~~

Quy tắc chủ yếu:

- `findClass("demo.plugin.Widget")` cần tìm `demo/plugin/Widget.class` trong class root.
- `ClassLoader.getResource("demo/plugin/message.txt")` tìm theo **tên resource từ root**. Thông thường **không** có dấu `/` ở đầu.
- `Widget.class.getResource("message.txt")` đổi tên tương đối thành `demo/plugin/message.txt` trước khi tìm qua loader của `Widget`.
- `Widget.class.getResource("/demo/plugin/message.txt")` xử lý dấu `/` đầu như **đường dẫn tuyệt đối theo resource root**, rồi bỏ slash này khi tra cứu loader.
- `Widget.class.getResourceAsStream("message.txt")` cũng tuân quy tắc đặt tên tương đối với package, nhưng trả stream thay vì URL.

Điểm quan trọng: `Class.getResource(...)` **không mặc định dùng Thread Context ClassLoader (TCCL)**. Nó gắn với class được gọi và loader/module của class đó. Vì thế nếu `Widget` được định nghĩa bởi loader tùy chỉnh, lời gọi `Widget.class.getResource("message.txt")` có thể tìm qua loader ấy — **miễn là loader đó triển khai lookup resource**.

## 4. Sơ đồ parent-first và phạm vi hook

Giả sử host sử dụng application loader (parent), plugin dùng `DirectoryLoader` (child):

~~~text
Caller gọi child.loadClass("demo.plugin.Widget")
    ├─ Parent thử tìm Widget
    └─ Không có → child.findClass("demo.plugin.Widget")
                       └─ đọc plugin-root/demo/plugin/Widget.class

Caller gọi child.getResource("demo/plugin/message.txt")
    ├─ Parent thử tìm message.txt
    └─ Không có → child.findResource("demo/plugin/message.txt")
                       └─ trả URL tới plugin-root/demo/plugin/message.txt

Caller gọi child.getResources("demo/plugin/shared.txt")
    ├─ Parent tìm shared.txt    → HOST_SHARED
    └─ child.findResources     → CHILD_SHARED
                                 |
                                 v
                     [URL(parent), URL(child)]
~~~

Với `ClassLoader` mặc định, bạn thường **không cần override `loadClass` hoặc `getResource`** để có parent-first. Override các hook `findClass`, `findResource` và `findResources` là đủ để cung cấp đường tìm riêng khi parent không tìm được hoặc khi cần liệt kê.

Nếu override `getResource` bằng một implementation chỉ tìm tài nguyên riêng, bạn có thể vô tình **bỏ qua tài nguyên của parent**. Nếu override `getResources` bằng một implementation chỉ trả tài nguyên riêng, bạn có thể làm mất các descriptor service/config của parent trong kết quả. Chỉ thay cơ chế delegation khi có lý do rõ ràng và thiết kế tương thích với các API khác.

## 5. Ví dụ Java 17+: DirectoryLoader hỗ trợ cả class và resource

Ta sẽ viết custom loader đọc từ một thư mục plugin trên đĩa, thay vì dùng `URLClassLoader` sẵn có, nhằm học cách bổ sung resource hooks một cách có kiểm soát.

Chương trình thể hiện hai trường hợp:

1. Resource `message.txt` **chỉ nằm trong plugin root** nên có thể được tìm bằng `Widget.class.getResource(...)` hoặc `DirectoryLoader.getResource(...)`.
2. Resource `shared.txt` xuất hiện **cả trong host root và plugin root**. Vì parent-first, lookup mặc định trả phiên bản của host; còn `findResource` trả phiên bản của child khi chủ động truy cập hook nguồn riêng.

### 5.1. Cấu trúc file

~~~text
directory-loader-demo/
├── src/
│   └── DirectoryLoaderDemo.java
├── plugin-src/
│   └── demo/plugin/Widget.java
├── host-resources/
│   └── demo/plugin/shared.txt
└── plugin-resources/
    └── demo/plugin/
        ├── message.txt
        └── shared.txt
~~~

Các file text là UTF-8 không BOM, có nội dung lần lượt:

| Đường dẫn | Nội dung |
|---|---|
| `host-resources/demo/plugin/shared.txt` | `HOST_SHARED` |
| `plugin-resources/demo/plugin/message.txt` | `PLUGIN_MESSAGE` |
| `plugin-resources/demo/plugin/shared.txt` | `CHILD_SHARED` |

Sau khi biên dịch, `build/host` chứa host class + host resource; `build/plugin` chứa bytecode plugin + resource plugin. **Không đưa `build/plugin` vào application classpath** lúc chạy.

### 5.2. Class plugin

File **`plugin-src/demo/plugin/Widget.java`**:

~~~java
package demo.plugin;

public final class Widget {
    public Widget() {
    }

    public String message() {
        return "Widget ready";
    }
}
~~~

Đây là class bình thường với `public` constructor không tham số. Host **không import** `Widget` vì phải để custom loader là nơi định nghĩa class ấy.

### 5.3. Host và custom loader hoàn chỉnh

File **`src/DirectoryLoaderDemo.java`**:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DirectoryLoaderDemo {
    private static final String MESSAGE = "demo/plugin/message.txt";
    private static final String SHARED = "demo/plugin/shared.txt";

    private DirectoryLoaderDemo() {
    }

    static final class DirectoryLoader extends ClassLoader {
        private final Path root;

        DirectoryLoader(Path root, ClassLoader parent) {
            super(parent);
            this.root = root.toAbsolutePath().normalize();
        }

        private Path existingFile(String relativeName) {
            if (relativeName == null
                    || relativeName.isEmpty()
                    || relativeName.startsWith("/")
                    || relativeName.indexOf('\\') >= 0) {
                return null;
            }

            final Path relative;
            try {
                relative = Path.of(relativeName);
            } catch (InvalidPathException ex) {
                return null;
            }

            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                return null;
            }
            return file;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            Path file = existingFile(
                    name.replace('.', '/') + ".class");

            if (file == null) {
                throw new ClassNotFoundException(name);
            }

            try {
                byte[] bytes = Files.readAllBytes(file);
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }

        @Override
        protected URL findResource(String name) {
            Path file = existingFile(name);
            if (file == null) {
                return null;
            }

            try {
                return file.toUri().toURL();
            } catch (MalformedURLException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        protected Enumeration<URL> findResources(String name)
                throws IOException {
            URL found = findResource(name);
            return Collections.enumeration(
                    found == null ? List.<URL>of() : List.of(found));
        }

        // Chỉ để minh họa resource riêng của child,
        // bỏ qua cơ chế parent-first của getResource.
        URL ownResource(String name) {
            return findResource(name);
        }
    }

    private static String text(URL url) throws IOException {
        if (url == null) {
            return "(missing)";
        }
        try (InputStream in = url.openStream()) {
            return new String(
                    in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    private static String text(InputStream input) throws IOException {
        if (input == null) {
            return "(missing)";
        }
        try (InputStream in = input) {
            return new String(
                    in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    private static String all(Enumeration<URL> urls)
            throws IOException {
        List<String> labels = new ArrayList<>();
        while (urls.hasMoreElements()) {
            labels.add(text(urls.nextElement()));
        }
        return String.join(" > ", labels);
    }

    private static String all(Stream<URL> urls) {
        try (urls) {
            return urls.map(url -> {
                try {
                    return text(url);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }).collect(Collectors.joining(" > "));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: DirectoryLoaderDemo <plugin-root>");
        }

        Path pluginRoot = Path.of(args[0]).toAbsolutePath();
        if (!Files.isDirectory(pluginRoot)) {
            throw new IllegalArgumentException(
                    "Not a plugin directory: " + pluginRoot);
        }

        ClassLoader parent =
                DirectoryLoaderDemo.class.getClassLoader();
        DirectoryLoader child =
                new DirectoryLoader(pluginRoot, parent);

        Class<?> widget = child.loadClass("demo.plugin.Widget");
        Object instance = widget.getConstructor().newInstance();

        System.out.println("Widget says: "
                + widget.getMethod("message").invoke(instance));
        System.out.println("Widget defined by child: "
                + (widget.getClassLoader() == child));

        System.out.println("Class relative message: "
                + text(widget.getResource("message.txt")));
        System.out.println("Class absolute message: "
                + text(widget.getResource("/" + MESSAGE)));
        System.out.println("Loader message: "
                + text(child.getResource(MESSAGE)));
        System.out.println("Loader message stream: "
                + text(child.getResourceAsStream(MESSAGE)));
        System.out.println("Parent sees plugin message: "
                + (parent.getResource(MESSAGE) != null));

        System.out.println("Parent-first shared: "
                + text(child.getResource(SHARED)));
        System.out.println("Child's own shared: "
                + text(child.ownResource(SHARED)));
        System.out.println("All shared: "
                + all(child.getResources(SHARED)));
        System.out.println("Shared stream: "
                + all(child.resources(SHARED)));
        System.out.println("Class relative shared: "
                + text(widget.getResource("shared.txt")));

        System.out.println("Leading slash on loader fails: "
                + (child.getResource("/" + MESSAGE) == null));

        boolean parentCannotLoad;
        try {
            parent.loadClass("demo.plugin.Widget");
            parentCannotLoad = false;
        } catch (ClassNotFoundException expected) {
            parentCannotLoad = true;
        }
        System.out.println("Parent cannot load Widget: "
                + parentCannotLoad);
    }
}
~~~

**Vì sao `DirectoryLoader` chỉ override ba hook?** Cách này giữ nguyên hợp đồng parent-first của `loadClass`, `getResource` và `getResources` trong `ClassLoader`. Loader mới chỉ cần cho JVM biết cách lấy bytecode và URL tài nguyên từ `root`.

**Vì sao `ownResource` được khai báo riêng?** Nó chỉ là phương thức *quan sát* kết quả `findResource` của child. Không nên cho code ứng dụng dùng hook nguồn riêng như API mặc định, vì hook bỏ qua các tài nguyên do parent cung cấp. Nếu requirement của một hệ plugin đòi child-first thật sự, cần định nghĩa rõ hành vi đó trên toàn bộ hệ thống thay vì dựa vào một method tiện lợi.

## 6. Lệnh biên dịch và chạy

Yêu cầu JDK **17+**, gồm `javac` và `java`. Mở terminal tại thư mục `directory-loader-demo/` chứa đủ file nêu trên.

**Bash (Linux, macOS hoặc Git Bash):**

~~~bash
mkdir -p build/host build/plugin

javac -encoding UTF-8 -d build/host src/DirectoryLoaderDemo.java
javac -encoding UTF-8 -d build/plugin \
  plugin-src/demo/plugin/Widget.java

cp -R host-resources/. build/host/
cp -R plugin-resources/. build/plugin/

java -cp build/host DirectoryLoaderDemo build/plugin
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force build/host,build/plugin | Out-Null

javac -encoding UTF-8 -d build/host src/DirectoryLoaderDemo.java

javac -encoding UTF-8 -d build/plugin plugin-src/demo/plugin/Widget.java

Copy-Item -Path host-resources/* -Destination build/host -Recurse -Force
Copy-Item -Path plugin-resources/* -Destination build/plugin -Recurse -Force

java -cp build/host DirectoryLoaderDemo build/plugin
~~~

Sau khi build, kiểm tra cấu trúc:

~~~text
build/
├── host/
│   ├── DirectoryLoaderDemo.class
│   ├── DirectoryLoaderDemo$DirectoryLoader.class
│   └── demo/plugin/shared.txt            (HOST_SHARED)
└── plugin/
    └── demo/plugin/
        ├── Widget.class
        ├── message.txt                   (PLUGIN_MESSAGE)
        └── shared.txt                    (CHILD_SHARED)
~~~

Không thêm `build/plugin` vào `java -cp` của host. Nếu parent đã nạp `Widget` từ đó, `child.loadClass(...)` có thể trả class của parent thay vì dùng `findClass` và kết quả kiểm tra sẽ khác.

### Stdout dự kiến

Với đúng các file, classpath và runtime Java 17 thông thường:

~~~text
Widget says: Widget ready
Widget defined by child: true
Class relative message: PLUGIN_MESSAGE
Class absolute message: PLUGIN_MESSAGE
Loader message: PLUGIN_MESSAGE
Loader message stream: PLUGIN_MESSAGE
Parent sees plugin message: false
Parent-first shared: HOST_SHARED
Child's own shared: CHILD_SHARED
All shared: HOST_SHARED > CHILD_SHARED
Shared stream: HOST_SHARED > CHILD_SHARED
Class relative shared: HOST_SHARED
Leading slash on loader fails: true
Parent cannot load Widget: true
~~~

**Phân tích stdout:**

1. `Widget defined by child: true` xác nhận parent không có class và `DirectoryLoader.findClass` đã đọc bytecode tại `build/plugin/demo/plugin/Widget.class`.
2. Hai phép gọi `widget.getResource("message.txt")` và `widget.getResource("/demo/plugin/message.txt")` cùng quy về `demo/plugin/message.txt`, được tìm bởi defining loader của `Widget`.
3. `child.getResource(MESSAGE)` và `child.getResourceAsStream(MESSAGE)` tìm được resource thuộc plugin; host parent không có resource đó.
4. Với `shared.txt`, parent chứa `HOST_SHARED` nên `child.getResource(SHARED)` trả bản của parent, **không** phải bản đứng cạnh `Widget.class`. Đây là ý nghĩa của parent-first.
5. `child.ownResource(SHARED)` truy cập nguồn của child nên thấy `CHILD_SHARED`.
6. `getResources` và `resources` bao gồm cả URL parent rồi URL child, cho kết quả `HOST_SHARED > CHILD_SHARED`.
7. `widget.getResource("shared.txt")` dù bắt đầu từ class do child định nghĩa, vẫn đi qua parent-first của ClassLoader. Vì thế resource được chọn là `HOST_SHARED`.
8. Với `ClassLoader.getResource`, tên bắt đầu bằng `/` không được xử lý như `Class.getResource` và không khớp tên resource được lưu; parent cũng không có tên tương ứng.
9. Parent không tự tìm xuống child nên `parent.loadClass("demo.plugin.Widget")` ném `ClassNotFoundException`.

## 7. `findClass`: đường bytecode dùng chung quy tắc root

Trong `findClass`:

~~~java
// Trích logic cốt lõi từ ví dụ.
String relative = name.replace('.', '/') + ".class";
Path file = existingFile(relative);
byte[] bytes = Files.readAllBytes(file);
return defineClass(name, bytes, 0, bytes.length);
~~~

Đoạn trích ở trên chỉ minh họa nhánh thành công; code runnable đầy đủ đã kiểm tra null và bọc `IOException` bằng `ClassNotFoundException`.

Việc dùng cùng `root` cho `findClass` và `findResource` tạo một nguyên tắc dễ hiểu:

~~~text
root/demo/plugin/Widget.class  ← dữ liệu để defineClass
root/demo/plugin/message.txt   ← dữ liệu để findResource
root/demo/plugin/shared.txt    ← resource khác cùng package
~~~

Nhưng phải phân biệt:

- `defineClass` chỉ nhận bytecode Java class hợp lệ; không dùng nó để nạp file văn bản.
- `findResource` trả `URL` trỏ đến dữ liệu; nó không định nghĩa class hoặc kích hoạt static initializer.
- `getResource` có thể trả tài nguyên của parent ngay cả khi `findResource` của child cũng tìm thấy cùng tên.
- Có thể có nhiều source roots hoặc JARs; khi đó `findClass` và `findResources` phải sử dụng danh sách nguồn rõ ràng, không mặc định một Path duy nhất như ví dụ.

Nếu class nằm trong **JAR** thay vì thư mục, nên cân nhắc `URLClassLoader` sẵn có. Nó đã hỗ trợ class lookup, URL resource lookup, parent delegation và lifecycle `close()`. Không cần viết lại parser ZIP/JAR nếu mục tiêu chỉ là nạp một vài JAR thông thường.

## 8. `findResource` và `findResources`: vì sao cần cả hai?

Một lỗi rất phổ biến là chỉ override `findResource`:

~~~java
@Override
protected URL findResource(String name) {
    // Tìm resource đầu tiên trong kho riêng.
    ...
}
~~~

Khi đó `getResource(name)` có thể hoạt động, **nhưng `getResources(name)` chưa chắc liệt kê resource thuộc kho riêng**, vì implementation mặc định của `findResources` không tự tổng hợp từ `findResource` của subclass. Đây là điều khác biệt giữa **API tìm một** và **API liệt kê nhiều**.

Để hỗ trợ cả hai, cần cài:

~~~java
@Override
protected Enumeration<URL> findResources(String name)
        throws IOException {
    URL found = findResource(name);
    return Collections.enumeration(
            found == null ? List.<URL>of() : List.of(found));
}
~~~

Đây là cách phù hợp **khi mỗi tên chỉ có nhiều nhất một file trong root của child**. Khi loader tìm trên **nhiều JAR/root**, phải liệt kê mọi URL trong thứ tự mong muốn, ví dụ:

~~~java
// Mã minh họa, không chạy độc lập:
List<URL> found = new ArrayList<>();
for (Repository repository : repositories) {
    URL url = repository.find(name);
    if (url != null) {
        found.add(url);
    }
}
return Collections.enumeration(found);
~~~

Không thực hiện return ngay ở kết quả đầu tiên nếu contract của `findResources` là liệt kê tất cả URL có tên đó.

**Điều kiện nhất quán hữu ích:** URL đầu tiên do `getResources(name)` liệt kê nên phù hợp với `getResource(name)` khi hệ thống tra cứu không bị thay đổi đồng thời. Nếu subclass override phương thức entry-point một cách khác nhau, các framework dùng API một resource và nhiều resource có thể nhìn thấy các bộ dữ liệu khác nhau không chủ ý.

## 9. Class-relative resource: cùng package không bảo đảm cùng nguồn

Một nhận định dễ nhầm:

> “`Widget.class.getResource("shared.txt")` chắc chắn đọc file từ đúng thư mục chứa `Widget.class`.”

Điều đó **không đúng một cách tổng quát**. `Class.getResource` xử lý **tên resource tương đối với package** rồi ủy quyền tra cứu tới ClassLoader/module phù hợp. Nếu ClassLoader dùng parent-first và parent có resource trùng tên, **resource của parent có thể được ưu tiên**, dù class `Widget` do child định nghĩa.

Trong ví dụ:

~~~text
Widget.class        → child định nghĩa, nằm trong build/plugin
shared.txt (host)   → parent cung cấp, HOST_SHARED
shared.txt (plugin) → child cung cấp, CHILD_SHARED
~~~

Lệnh:

~~~java
URL chosen = widget.getResource("shared.txt");
~~~

nhận `HOST_SHARED`. Muốn truy vấn riêng root plugin trong một hệ thống có yêu cầu “resource phải đi cùng code plugin”, cần một API mà framework kiểm soát, ví dụ `ownResource`, hoặc thiết kế namespace/path tránh xung đột.

**Chiến lược thực tế tốt hơn:** đặt resource dưới đường dẫn định danh riêng, ví dụ `META-INF/my-plugin-id/config.json`, thay vì tên chung `config.properties` ở root của nhiều JAR. Nếu code cố ý muốn hỗ trợ nhiều resource trùng tên, dùng `getResources` và quy tắc merge/priority rõ ràng.

## 10. Tên resource và quy tắc slash

| Caller | Input | Ý nghĩa |
|---|---|---|
| `loader.getResource("demo/plugin/message.txt")` | Không có slash đầu | Path từ root loader |
| `loader.getResource("/demo/plugin/message.txt")` | Có slash đầu | Thông thường không khớp tên resource, đừng dùng |
| `Widget.class.getResource("message.txt")` | Tên tương đối | Từ package `demo.plugin` |
| `Widget.class.getResource("/demo/plugin/message.txt")` | Slash đầu | Tên tuyệt đối từ resource root |
| `Widget.class.getResourceAsStream("message.txt")` | Tên tương đối | InputStream của resource được chọn, cần đóng |

Với resource trong **named module**, còn có các quy tắc module encapsulation và `opens`. `Class.getResource` không đơn giản là thao tác cộng chuỗi rồi gọi bất kỳ loader nào; lookup có thể có các kiểm tra quyền của module. Mẫu này dùng **classpath/unnamed modules** để hành vi có thể kiểm chứng dễ dàng.

## 11. An toàn đường dẫn và tính đúng đắn của custom loader

Code ví dụ có hàm `existingFile` để giới hạn lookup trong root:

~~~java
Path file = root.resolve(relative).normalize();
if (!file.startsWith(root) || !Files.isRegularFile(file)) {
    return null;
}
~~~

Việc `normalize()` và kiểm tra `startsWith(root)` giúp tránh **một số** kiểu truy cập `../` đơn giản hoặc đường dẫn tuyệt đối ngoài root. Nhưng đây **không phải sandbox hay giải pháp chống traversal đầy đủ**:

- **Symbolic link** nằm bên trong root có thể trỏ tới file bên ngoài; so sánh path sau normalize **không** giải quyết được trường hợp đó. Nếu nguồn resource không tin cậy, cần chính sách symlink, kiểm tra real path và phòng tránh race thay đổi filesystem (TOCTOU).
- Path do caller cung cấp có thể khác quy tắc giữa Windows/Unix (separator, drive, case sensitivity). Thư viện production cần kiểm tra/chuẩn hóa đầu vào rõ ràng.
- Không nên cho untrusted caller tùy tiện yêu cầu mọi file trong plugin root nếu root có chứa secret/config nhạy cảm.
- `defineClass` không tạo ranh giới bảo mật: plugin bytecode chạy trong cùng JVM/process. Với mã không tin cậy, cách ly quyền bằng tiến trình/container phù hợp.
- Loader production còn phải cân nhắc `ProtectionDomain`, package metadata/sealing, chữ ký JAR, thread safety, lifecycle và nạp đồng thời; ví dụ tập trung vào **resource lookup**, không trình bày đây là loader JAR production hoàn chỉnh.

Nếu file biến mất giữa `Files.isRegularFile` và `Files.readAllBytes`, nhánh `IOException` trong `findClass` chuyển lỗi sang `ClassNotFoundException` có cause; nhánh URL resource cũng có thể gặp lỗi khi caller mở URL. Tìm được `URL` **không bảo đảm** file vẫn đọc được sau đó.

## 12. Resource lifecycle: URL và InputStream

`findResource` trả một `URL` trỏ tới file. Khi caller gọi `URL.openStream()` hoặc `getResourceAsStream`, một `InputStream` được mở. Caller phải **đóng stream**:

~~~java
// Mã minh họa nếu loader đã tồn tại.
try (InputStream in =
             loader.getResourceAsStream("demo/plugin/message.txt")) {
    if (in == null) {
        throw new IOException("Resource missing");
    }
    String text = new String(
            in.readAllBytes(), StandardCharsets.UTF_8);
    // Xử lý text...
}
~~~

Đừng coi URL là nội dung tài nguyên. Một URL là **vị trí**; tệp có thể thay đổi hoặc bị xóa sau lúc URL được trả về. Nếu resource phải bất biến trong suốt phiên bản plugin, hãy quản lý snapshot/version rõ ràng.

Lưu ý `DirectoryLoader` minh họa ở đây **không triển khai `AutoCloseable`**, cũng không giữ `JarFile`/stream handles để đóng. Không có `close()` tự động giống `URLClassLoader`. Nếu tự xây loader đọc ZIP, JAR hoặc kết nối mạng, phải thiết kế cơ chế đóng nguồn tài nguyên do loader sở hữu, ngừng task và dọn object/thread/callback trước khi unload plugin.

Việc làm `DirectoryLoader` không còn được tham chiếu có thể giúp JVM thu gom class/loader sau khi mọi object/class liên quan không còn reachable, nhưng không có lời hứa unload ngay; TCCL, `ThreadLocal` và cache framework có thể giữ loader lâu hơn dự kiến.

## 13. `getResources`, `resources` và metadata nhiều nguồn

Với root đơn, `findResources` của ví dụ chỉ trả một URL hoặc Enumeration rỗng. Nhưng `getResources` của ClassLoader **vẫn có thể trả nhiều URL**, vì nó ghép kết quả của parent và child.

Với `shared.txt`:

~~~text
child.getResources("demo/plugin/shared.txt")
    → [HOST_SHARED, CHILD_SHARED]

child.resources("demo/plugin/shared.txt")
    → Stream<URL> theo cùng lookup
~~~

`getResources` trả `Enumeration<URL>`. `resources` (Java 9+) trả `Stream<URL>`, cho phép lọc/map/collect. **Hai API trả URL, không trả nội dung file**. Khi đọc từng URL, mở và đóng các `InputStream` riêng như phương thức `text(url)` trong ví dụ.

Một trường hợp phổ biến là `META-INF/services/com.example.Service`:

- Nhiều JAR có thể cung cấp các descriptor cùng tên.
- `ServiceLoader` dùng lookup resource/provider theo quy tắc riêng.
- Nếu custom loader chỉ cài `findResource` nhưng không cài `findResources`, một framework cần enumerate nhiều descriptor có thể không thấy provider nằm trong nguồn riêng.
- Chỉ định nghĩa cùng tên file `META-INF/services/...` chưa đủ; provider và interface service cần thuộc đúng runtime type để `ServiceLoader` chấp nhận.

Khi có thể, dùng `ServiceLoader` thay vì tự parser descriptor. Nếu API cho phép truyền loader, dùng overload nhận `ClassLoader` tường minh; TCCL chỉ là lựa chọn tra cứu ngữ cảnh, không thay defining loader của class.

## 14. ClassLoader tùy chỉnh với JAR: khi nào không tự viết?

Nếu mục đích chỉ là “nạp lớp và resource từ danh sách JAR”, lựa chọn đơn giản hơn thường là `URLClassLoader`:

~~~java
// Mã minh họa, cần urls và parent.
try (URLClassLoader loader = new URLClassLoader(urls, parent)) {
    Class<?> type = loader.loadClass("demo.plugin.Widget");
    URL resource = loader.getResource("demo/plugin/message.txt");
    // Dùng class và đọc resource khi loader vẫn còn mở.
}
~~~

`URLClassLoader` đã hỗ trợ `findClass`, `findResource`, `findResources`, thứ tự URL, parent delegation và `close()` giải phóng các file/JAR do loader mở. Điều đó giảm rất nhiều công việc và lỗi thường gặp so với một subclass tự đọc ZIP/JAR.

Chỉ cân nhắc custom ClassLoader khi nguồn dữ liệu thật sự khác biệt (ví dụ bytecode/resources từ encrypted bundle, database, network repository, nội dung sinh động) hoặc cần kiểm soát xác thực/version rõ ràng. Khi viết, phải xử lý **đồng thời class và tài nguyên**; chỉ nạp được `.class` nhưng bỏ `findResource(s)` là thiết kế thiếu.

## 15. Những lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `child.loadClass("demo.plugin.Widget")` thành công nhưng `Widget.class.getResource("message.txt")` trả null | Có `findClass` nhưng không có `findResource` | Cài resource lookup từ cùng root và đúng đường dẫn |
| `getResource` tìm được nhưng `getResources` không liệt kê file child | Có `findResource` nhưng thiếu `findResources` | Override cả hai hook; Enumerate mọi match của child |
| Resource của host “thắng” file bên cạnh class plugin | `getResource` parent-first và parent có tên trùng | Dùng namespace riêng hoặc API truy vấn nguồn plugin có chủ đích |
| `ClassLoader.getResource("/demo/plugin/message.txt")` null | Tên có slash đầu không đúng quy tắc `ClassLoader` | Dùng `"demo/plugin/message.txt"` |
| `Widget.class.getResource("message.txt")` không thấy file ở root | Class-relative lookup thêm package path | Di chuyển resource vào `demo/plugin/` hoặc dùng tên absolute |
| Parent không tìm được class của child | Parent không tự duyệt nguồn riêng của child | Host gọi child loader và giao tiếp qua API chung |
| `ClassCastException` khi class trùng tên | Hai loader đã định nghĩa hai runtime class không tương thích | Chia sẻ interface/DTO ở loader chung |
| `getResource` trả URL nhưng đọc sau đó gặp lỗi | File bị xóa/đổi hoặc I/O thất bại | Mở và đọc trong lifecycle được quản lý, xử lý `IOException` |
| Path chuẩn hóa nhưng vẫn ra ngoài root | Symlink hoặc filesystem race | Kiểm soát real path và chính sách nguồn tin cậy |
| Plugin unload khó dù không còn dùng | Thread/TCCL/cache giữ classloader | Dọn callback, thread, resource và references |
| `ServiceLoader` không phát hiện provider | Sai descriptor, sai lookup loader hoặc thiếu `findResources` | Kiểm tra `META-INF/services`, type identity và enumeration |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Một custom ClassLoader chỉ override `findClass`. Nó nạp được `Widget.class` từ thư mục riêng. `Widget.class.getResource("message.txt")` có chắc tìm được file nằm cạnh `Widget.class` không?

**Đáp án:** Không. Class lookup và resource lookup là hai đường khác nhau; loader cần cung cấp `findResource` (và `findResources` nếu muốn liệt kê).

**Câu 2.** Vì sao `Widget.class.getResource("shared.txt")` có thể trả resource của parent dù `Widget.class.getClassLoader()` là child?

**Đáp án:** `Class.getResource` xác định tên tương đối với package rồi dùng cơ chế resource lookup. Với ClassLoader parent-first, parent có thể cung cấp resource trùng tên trước child.

**Câu 3.** Vì sao nên override `findResources` nếu đã có `findResource`?

**Đáp án:** Vì API `getResources` dành để liệt kê **nhiều URL**, thường gọi hook `findResources`; hook mặc định không tự liệt kê nội dung của `findResource` tùy biến.

**Câu 4.** `getResources("demo/plugin/shared.txt")` trong ví dụ trả bao nhiêu resource?

**Đáp án:** Hai URL: resource của host parent trước, sau đó resource riêng của child. `resources(...)` cung cấp chúng qua Stream API.

**Câu 5.** Khi custom loader đọc class từ database nhưng resource được lưu trong database cùng key prefix, nên thiết kế thế nào?

**Đáp án:** `findClass` ánh xạ binary name thành key bytecode, `findResource(s)` ánh xạ resource name thành URL/đường truy cập thích hợp (hoặc cung cấp protocol handler có vòng đời được quản lý). Cần bảo đảm URI/URL được trả thực sự có thể mở và kiểm soát lỗi/đóng tài nguyên.

**Câu 6.** Có cần override `getResource` để sử dụng parent-first với resource của loader tùy chỉnh không?

**Đáp án:** Thông thường không. Dùng `findResource`/`findResources`; `ClassLoader.getResource(s)` mặc định thực hiện parent delegation.

## 17. Tài liệu tham khảo

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `findClass`, `findResource`, `findResources`, `getResource`, `getResources` và `resources`.
- [Java SE 17 — Class.getResource](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getResource(java.lang.String)): quy tắc tên tương đối với package và tên tuyệt đối.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): triển khai loader có đường tìm JAR/resource.
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): liên hệ giữa nhiều resource descriptor và provider discovery.
- [Java SE 17 — Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html): `resolve`, `normalize`, `startsWith` khi ánh xạ root.

**Ghi nhớ:** Một custom ClassLoader cần thiết kế **cặp tra cứu class và tài nguyên**. Override `findClass` để định nghĩa lớp, `findResource` để tìm một resource riêng và `findResources` để liệt kê các resource riêng; để entry point `loadClass`/`getResource(s)` quản lý parent-first nếu không có lý do thay đổi. Resource có cùng package với class **không bảo đảm** được lấy từ cùng nguồn khi parent có tên trùng; hãy kiểm tra namespace, đường dẫn và thứ tự delegation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-011">Giữ bất biến định danh kiểu và đóng tài nguyên do loader sở hữu</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ bất biến định danh kiểu và đóng tài nguyên do loader sở hữu

> **Java Core → ClassLoader → Loader tùy chỉnh.** Khi tự viết hoặc vận hành class loader cho plugin, bạn phải bảo vệ **định danh của mỗi runtime type**, để những API mà host/plugin trao đổi luôn thuộc cùng một type, và phải có **vòng đời đóng tài nguyên** rõ ràng. Nạp class thành công mới là bước đầu: lỗi cast giữa hai lớp cùng tên, JAR bị giữ file handle, thread/context class loader làm plugin không unload, hoặc cố dùng loader sau khi close đều là lỗi thiết kế dễ gặp.

## 1. Hai mục tiêu độc lập nhưng liên quan

**Bất biến định danh kiểu (type identity invariant):** Với class/interface thông thường, JVM phân biệt runtime class theo **binary name và defining class loader**. Hai loader khác nhau cùng định nghĩa `example.Plugin` tạo ra **hai runtime types** khác nhau, dù bytecode hoàn toàn giống. Một loader cũng không được định nghĩa một tên class hai lần trong cùng namespace. Khi host và plugin cần trao đổi object qua interface, cả hai phải nhìn thấy **cùng một đối tượng `Class`** cho interface đó.

**Bất biến sở hữu tài nguyên (resource ownership/lifecycle):** Loader hoặc plugin có thể mở JAR, file, URL stream, socket, native library, worker thread, executor, watcher, cache hoặc registry. Hệ thống cần biết **ai tạo**, **ai có quyền đóng**, **khi nào ngăn công việc mới**, **khi nào đợi công việc đang chạy** và **cách giải phóng**. Với `URLClassLoader`, `close()` có hợp đồng cụ thể về nguồn JAR/file nó đã mở; với custom `ClassLoader` thuần túy, Java API **không cung cấp `close()` chung** cho mọi loader.

Tóm tắt:

~~~text
HOST (vòng đời dài)
  |
  +-- Shared API/DTO, nạp bởi host parent loader
  |       |
  |       +------ Contract.class: chỉ MỘT runtime identity
  |
  +-- Plugin loader A -----> PluginImpl.class (identity A)
  |       + resources: JAR, streams, tasks, listeners
  |
  +-- Plugin loader B -----> PluginImpl.class (identity B)
          + resources: JAR, streams, tasks, listeners

Dừng plugin A:
quiesce tasks -> unregister/cancel -> close owned resources/loader
-> bỏ strong references -> GC có thể unload classes sau đó
~~~

Điểm quan trọng: `close()` không phải `unloadClass()`. Tài nguyên I/O có thể đã được đóng nhưng các `Class` đã nạp, object, method hoặc static state **vẫn tồn tại** nếu còn tham chiếu.

## 2. Danh tính kiểu phải nhất quán qua vòng đời plugin

Đối với class/interface thông thường, JVM xác định một kiểu runtime bằng cặp **(binary name, defining loader)**. Hai loader có thể trả cùng tên qua `getName()` nhưng `Class<?> a == Class<?> b` là `false` nếu chúng thực sự định nghĩa hai bản khác nhau. Ngược lại, hai loader con ủy quyền cho cùng cha có thể nhận lại **cùng kiểu**.

- **Defining loader** là chủ sở hữu định nghĩa class, xem qua `type.getClassLoader()`; **initiating loader** chỉ có thể đã khởi phát một yêu cầu rồi ủy quyền cho cha.
- Hai implementation cùng tên do hai loader độc lập định nghĩa có static state khác nhau và không tự cast qua lại. API/SPI chia sẻ phải được host và plugin nhìn qua cùng defining loader.
- Với một loader đã khởi xướng tên lớp, `loadClass` mặc định tìm/tái sử dụng kết quả; `defineClass` lần hai để cập nhật cùng tên có thể gây `LinkageError`.
- Muốn nạp generation mới của plugin, thường cần loader mới và chuyển các object thông qua một API ổn định của parent; không coi việc thay JAR trên disk là cập nhật `Class<?>` cũ.

Bài này dùng nguyên tắc ấy để quyết định **ai giữ references và ai giải phóng tài nguyên** khi một plugin dừng hoặc được thay thế. Phần tiếp theo chỉ rõ ranh giới loader; các bước đóng và thu gom được kiểm chứng riêng ở các mục về lifecycle.

## 3. Xác định ranh giới API và ownership của loader

Trong hệ plugin, hãy đặt interface, DTO, exception hay annotation trao đổi với host ở **shared API loader**, để plugin ủy quyền khi cần. Phần triển khai và dependency private có thể thuộc loader riêng. Nếu child-first cần cho một package triển khai, hãy giới hạn chính sách theo namespace; không tự định nghĩa `java.*` hoặc API contract do host sở hữu.

Đối với parent-first custom loader chỉ thay nguồn bytecode, ưu tiên override `findClass`: `loadClass` mặc định đã xử lý locking, `findLoadedClass`, delegation và optional resolution. Khi custom `loadClass` thật sự cần thiết, phải bảo toàn những bước đó và phân biệt `ClassNotFoundException` với các lỗi linkage/security. Một interface cùng tên nhưng do child tự định nghĩa có thể làm `isAssignableFrom` thất bại tại host.

Ngoài danh tính `Class<?>`, loader còn phải giữ **runtime package, module access, certificate/signing và package sealing** đúng quy tắc. Nếu nạp từ JAR, `URLClassLoader` giúp xử lý nhiều metadata và I/O sẵn có. Không coi `ProtectionDomain` hay parent isolation là sandbox; điều quan trọng cho vòng đời là xác định **loader nào sở hữu mỗi tài nguyên** và khả năng dừng sử dụng trước khi đóng.

## 4. Ví dụ chạy được: cùng tên, hai loader, hai định danh

Mục đích là tự chứng minh **cùng một JAR nhưng hai loader riêng** tạo hai runtime classes. Không đặt plugin JAR trên host classpath.

Cấu trúc:

~~~text
identity-demo/
├── plugin-src/plugin/entry/Widget.java
├── host-src/IdentityDemo.java
├── plugins/               (sẽ tạo)
└── out/                   (sẽ tạo)
~~~

Tệp **`plugin-src/plugin/entry/Widget.java`**:

~~~java
package plugin.entry;

public class Widget {
    public Widget() { }
}
~~~

Tệp **`host-src/IdentityDemo.java`**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class IdentityDemo {
    public static void main(String[] args) throws Exception {
        URL jar = Path.of("plugins", "widget.jar").toUri().toURL();
        Class<?> leftType;
        Object leftObject;

        try (URLClassLoader left = new URLClassLoader(
                    new URL[] {jar},
                    ClassLoader.getPlatformClassLoader());
             URLClassLoader right = new URLClassLoader(
                    new URL[] {jar},
                    ClassLoader.getPlatformClassLoader())) {

            leftType = left.loadClass("plugin.entry.Widget");
            Class<?> rightType = right.loadClass("plugin.entry.Widget");
            leftObject = leftType.getDeclaredConstructor().newInstance();
            Object rightObject =
                    rightType.getDeclaredConstructor().newInstance();

            System.out.println("same name="
                    + leftType.getName().equals(rightType.getName()));
            System.out.println("same class=" + (leftType == rightType));
            System.out.println("same loader="
                    + (leftType.getClassLoader()
                       == rightType.getClassLoader()));
            System.out.println("left accepts right="
                    + leftType.isInstance(rightObject));
            System.out.println("right accepts left="
                    + rightType.isInstance(leftObject));
        }

        // close() đã chạy; Class<?> đã nạp không bị "xóa".
        System.out.println("old instance still valid="
                + leftType.isInstance(leftObject));
    }
}
~~~

Biên dịch, đóng JAR, chạy trong thư mục `identity-demo` bằng Java 17+ (POSIX shell):

~~~bash
mkdir -p out/plugin out/host plugins
javac --release 17 -d out/plugin \
    plugin-src/plugin/entry/Widget.java
jar --create --file plugins/widget.jar -C out/plugin .
javac --release 17 -d out/host host-src/IdentityDemo.java
java -cp out/host IdentityDemo
~~~

Stdout:

~~~text
same name=true
same class=false
same loader=false
left accepts right=false
right accepts left=false
old instance still valid=true
~~~

**Giải thích từng dòng:** Hai `URLClassLoader` dùng cùng URL, nhưng vì parent là platform loader và `plugin.entry.Widget` không nằm trong parent, mỗi loader **tự define** một bản class. Chúng có cùng binary name nhưng khác defining loader. `isInstance` chéo trả false. Sau khối `try-with-resources`, `URLClassLoader.close()` đã được gọi; các object/class đã có vẫn hợp lệ nếu còn tham chiếu.

**Ràng buộc của demo:** Không đặt `plugins/widget.jar` vào classpath của host nếu bạn muốn thử đúng ranh giới loader. Không dùng `ClassLoader.getSystemClassLoader()` làm parent chỉ vì thuận tiện khi host cũng vô tình chứa cùng class. Nếu plugin cần một interface do host định nghĩa, cần dùng parent có thể tìm đúng shared interface; giải thích ở mục kế tiếp.

## 5. Chia sẻ contract mà không nhân đôi `Class`

Một kiến trúc plugin thực tế thường cần host nhận một service object. Ví dụ shared contract:

~~~java
package api;

public interface PluginService extends AutoCloseable {
    String name();

    @Override
    void close();
}
~~~

Đây là tệp **`api/PluginService.java`**, một phần của project, không phải chương trình có `main`. Plugin có thể implement:

~~~java
package plugin.impl;

import api.PluginService;

public final class PluginServiceImpl implements PluginService {
    @Override
    public String name() {
        return "plugin-A";
    }

    @Override
    public void close() {
        // Đóng những tài nguyên do instance sở hữu.
    }
}
~~~

Đây là tệp **`plugin/impl/PluginServiceImpl.java`**, cũng không có `main`. Để cast an toàn ở host:

1. Biên dịch và đặt `api.PluginService` ở **host/shared API loader**.
2. Biên dịch plugin theo contract ấy, nhưng không bundle một bản API sẽ được child-first loader define lại.
3. Tạo plugin loader với **parent là host/shared loader**, và giữ parent-first cho package `api`.
4. Host lấy class triển khai từ plugin loader, gọi constructor theo contract/module access; cast sang `api.PluginService` do host định nghĩa.
5. Host gọi lifecycle `service.close()` trước khi đóng plugin loader, sau đó bỏ object/loader references.

Nếu plugin và host **mỗi bên định nghĩa lại `api.PluginService`** với cùng tên, bạn có thể gặp `ClassCastException` hoặc `LinkageError`, kể cả source chữ giống hệt. Việc “cùng JAR tên api.jar” không đủ; điều kiện là **cùng runtime `Class<?>`**.

### 5.1. Kiểm tra khi nhận plugin

~~~java
Class<?> impl = pluginLoader.loadClass("plugin.impl.PluginServiceImpl");
if (!PluginService.class.isAssignableFrom(impl)) {
    throw new IllegalArgumentException("Wrong shared API loader");
}
PluginService service = PluginService.class.cast(
        impl.getDeclaredConstructor().newInstance());
~~~

Đây là **snippet yêu cầu class `PluginService`, biến `pluginLoader` và điều kiện access hợp lệ**. Chỉ kiểm tra `isAssignableFrom` không bảo đảm constructor public, module opens, artifact trusted, dependency resolution hay vòng đời plugin. Đừng dùng `getName().equals(...)` thay kiểm tra type để “sửa” lỗi hai loaders.

## 6. `URLClassLoader.close()` thực sự bảo đảm điều gì?

`URLClassLoader` implements `Closeable`/`AutoCloseable` nên có thể dùng `try-with-resources`. Theo Javadoc:

- `close()` làm loader **không thể nạp mới classes/resources mà chính nó định nghĩa/tìm** qua URL path.
- Classes/resources đã nạp trước đó **vẫn có thể dùng**; classes/resources từ parent delegation **vẫn có thể truy cập**.
- Với các URL dạng `jar:`/`file:`, loader **đóng các file mà nó đã mở**, ở mức best effort, bao gồm JAR handles tương ứng.
- `getResourceAsStream` được `URLClassLoader` override: các streams do method này mở được quản lý/đóng khi loader close theo Javadoc.
- `close()` có thể ném `IOException` khi có lỗi đóng file; nếu nhiều IOException, các lỗi sau có thể là suppressed exceptions.
- Gọi `close()` lần nữa trên loader đã close không có tác dụng.
- Nếu **một thread đang load class đồng thời với close**, Javadoc nói kết quả thao tác đang load **không xác định**. Vì vậy lifecycle phải ngăn concurrency trước khi close.

**Không bảo đảm:**

- `Class` và instances lập tức biến mất.
- `static` singleton bị reset.
- JVM ngay lập tức unload classes hoặc reclaim Metaspace.
- Threads/executors, sockets, JDBC connections, files mở **bên ngoài loader**, logger appenders, HTTP clients, timers, listeners và caches tự đóng.
- URL/InputStream do application tự mở từ `URL.openStream()` hoặc `Files.newInputStream()` tự được dọn sạch chỉ vì loader đã close.
- Native/JNI libraries do plugin nạp được unload ngay; có thể còn ràng buộc VM/OS và class loader.

**Không có `close()` chung cho mọi `ClassLoader`**. Nếu bạn subclass trực tiếp `ClassLoader` và tự mở JAR/socket/channel, hãy thiết kế `AutoCloseable`/`Closeable` riêng, hoặc giữ owner khác quản lý tài nguyên đó. `URLClassLoader` có sẵn lifecycle I/O vì đặc tính URL search path, không vì mọi loader đều giống nhau.

## 7. Mẫu quản lý resource ownership

Với mỗi resource, hãy trả lời ba câu: **Ai tạo? Ai đóng? Đóng khi nào?** Ví dụ:

| Resource | Owner hợp lý | Bước đóng/giải phóng |
| --- | --- | --- |
| `URLClassLoader` và JAR handles của nó | Plugin runtime / host plugin manager | `loader.close()` sau khi không còn class-loading tasks |
| File streams/JarFile do plugin code tự mở | Thành phần trực tiếp mở | `try-with-resources` hoặc `close()` trong lifecycle riêng |
| Plugin executor/thread/timer | Plugin instance hoặc host manager quản lý pool | `shutdown`, cancel, `awaitTermination`; tránh bỏ tasks chạy |
| WatchService, sockets, DB pool | Thành phần plugin tạo | Close theo API tương ứng; xử lý lỗi best effort |
| ServiceLoader và provider instance cache | Host/plugin container | Bỏ references, clear registry, không giữ provider sau unload |
| `Thread.currentThread().getContextClassLoader()` | Từng task/worker đặt loader | `finally` restore loader trước đó |
| ThreadLocal value có chứa class/plugin | Code thiết lập value | `remove()` theo quy tắc lifecycle, đặc biệt trên thread pool |
| Static registry/listener ở host giữ plugin object | Host registry | Unregister listener/callback/caches trước unload |
| JNI/native library | Plugin/native runtime với contract riêng | Giải phóng handles qua API native; đừng kỳ vọng `close(loader)` là đủ |

**Nguyên tắc sở hữu:** Nếu loader là child của một parent dùng chung, **không bao giờ gọi `close()` vào parent chỉ vì plugin kết thúc**, trừ khi chính plugin thực sự sở hữu parent ấy. Một parent loader bị đóng khi còn nhiều plugin phụ thuộc có thể làm hỏng hệ thống.

### 7.1. Dùng try-with-resources cho những vòng đời ngắn

~~~java
URL location = Path.of("plugins", "task.jar").toUri().toURL();

try (URLClassLoader pluginLoader =
         new URLClassLoader(new URL[] {location},
                            ClassLoader.getSystemClassLoader())) {
    Class<?> pluginType = pluginLoader.loadClass("example.Task");
    // Chạy công việc plugin và đóng resources do plugin tạo.
}
~~~

Đây là snippet cần imports `java.net.URL`, `java.net.URLClassLoader`, `java.nio.file.Path` và exception handling. Class `example.Task` và JAR phải tồn tại, nên không coi đây là chương trình chạy được độc lập. Khối `try` chỉ tự close **pluginLoader**; các đối tượng được plugin tạo có thể cần lifecycle close riêng **trước** khi ra khỏi khối.

### 7.2. Vòng đời plugin dài: `stop → drain → close → release`

~~~text
ACTIVE
  -> STOPPING: từ chối công việc mới, rút khỏi discovery/routing
  -> DRAINING: cancel hoặc chờ tasks đang chạy; stop timers/threads
  -> CLEANUP: unregister callbacks/listeners; close plugin instances
  -> CLOSE LOADER: đóng JAR/file handles do loader sở hữu
  -> RELEASE: bỏ references đến Class, Method, objects, caches, TCCL
  -> ELIGIBLE FOR GC: VM có thể unload classes khi đủ điều kiện
~~~

Đây là **mô hình quản trị vòng đời**, không phải state machine do Java tự động cung cấp. Cần định nghĩa khi nào một request được xem là đã hoàn thành, timeout/cancellation policy, cách tiếp tục cleanup nếu một bước ném lỗi, và cách tránh actor khác tiếp tục sử dụng plugin cũ.

Một thứ tự cleanup thực dụng:

1. Ngừng publish plugin và nhận work mới; khóa registration theo giao ước.
2. Cancel/chờ background tasks và các request đang gọi code plugin; không `close()` loader trong lúc chúng đang tìm thêm class/resource.
3. Gỡ callback, listener, scheduler, MBean, metrics hook, shutdown hook/registries mà plugin đã đăng ký vào host.
4. Gọi `close()` của plugin service, release file/socket/DB pools, dọn ThreadLocal và restore TCCL ở workers.
5. Close những resources loader-owned, bao gồm `URLClassLoader.close()`.
6. Bỏ tất cả references host còn giữ tới plugin objects/classes/loader và các captured lambdas.
7. Quan sát GC/class unloading **không đưa ra deadline cứng**; verify bằng log/profiler và lặp vòng start/stop khi test.

Nếu một bước cleanup ném exception, hãy ghi nhận và tiếp tục dọn **các resources độc lập còn lại**, sau đó tổng hợp lỗi. Dùng `try/finally`, `try-with-resources` hoặc cơ chế suppressed exceptions thích hợp; tránh nuốt lỗi khiến vận hành tưởng plugin đã đóng an toàn.

## 8. TCCL, worker threads và classloader leaks

Trong hệ plugin, một thread có thể giữ **strong reference** tới plugin loader qua `Thread.contextClassLoader`. Điều này đặc biệt dễ xảy ra với **executor threads sống lâu**.

~~~java
Thread worker = Thread.currentThread();
ClassLoader previous = worker.getContextClassLoader();

try {
    worker.setContextClassLoader(pluginLoader);
    // Gọi ServiceLoader, resource lookup hoặc plugin callback.
} finally {
    worker.setContextClassLoader(previous);
}
~~~

Đây là snippet cần biến `pluginLoader` và phần callback thực tế. **Restore trong `finally`** để exception không làm worker giữ loader cũ. Không được giả định task nộp vào `ExecutorService` sẽ tự kế thừa TCCL từ submitter: worker đã được tạo từ trước có thể dùng TCCL khác hoặc từ task trước.

Một utility có thể wrap mỗi task, capture desired loader tại boundary và restore previous loader sau task. Nhưng thiết kế plugin phải xem **ai thực sự muốn dùng loader nào**, đừng capture một TCCL đã bị task trước làm bẩn.

Còn những roots khác:

- `ThreadLocal` key/value trên thread pool chứa plugin object/Class/Method.
- Host `static` maps, service registry, listeners, caches và timers giữ instances/classes.
- `ServiceLoader` hoặc framework registry giữ cached providers qua nhiều generations.
- Running threads đang execute method của class plugin, stack frames và callbacks.
- Java native handles hoặc JNI references trong phạm vi lifetime thích hợp.
- Một `Class<?>` hoặc `Method` được host giữ có thể giữ loader gián tiếp.

**Không nhầm:** Việc một plugin có instance trong heap chưa chắc là memory leak nếu plugin vẫn active. “Leak” là khi plugin **đã stop** theo thiết kế nhưng objects/loader vẫn bị giữ không mong muốn; cần heap dump/dominator path và lifecycle evidence để kết luận.

## 9. Class unloading: làm gì và không được hứa gì?

VM **có thể** unload một class khi điều kiện của VM/GC cho phép, thường gắn với việc defining loader và các class/object liên quan không còn reachable. Nhưng:

- Java API không cung cấp `ClassLoader.unloadClass(name)` chuẩn.
- `URLClassLoader.close()` không buộc unload class; class đã nạp vẫn sống nếu `Class<?>` hoặc objects còn referenced.
- `System.gc()` chỉ là đề nghị GC, không bảo đảm GC sẽ chạy hoặc class sẽ unload ngay.
- Không đánh giá “unload thất bại” chỉ vì một lần GC chưa thấy sự kiện; có thể còn references, thời điểm GC/chính sách runtime khác, hoặc class không unload trong đợt quan sát.

Trong HotSpot hỗ trợ unified logging, có thể xem:

~~~bash
java -Xlog:class+load=info,class+unload=info -cp out/host IdentityDemo
~~~

Lệnh này là **diagnostic HotSpot**, không phải contract chung mọi JVM. Tùy JDK, có thể dùng `jcmd <pid> VM.classloader_stats` hoặc heap dump/Java Flight Recorder để khảo sát loader còn sống. Trước khi dùng `jcmd`, kiểm tra `jcmd <pid> help` để biết command được hỗ trợ. Trong bài lab ngắn, chỉ đóng loader mà còn giữ `leftType`/`leftObject` sẽ **không** chứng minh class phải được unload.

### 9.1. Thí nghiệm kiểm tra leak nghiêm túc

Thiết kế test loop:

1. Tạo loader mới cho plugin JAR, load/khởi tạo plugin.
2. Gọi method plugin, đăng ký và sau đó unregister các hooks cần thiết.
3. Dừng tasks và close resources; bỏ references mạnh trong host.
4. Dùng `WeakReference<ClassLoader>`/`ReferenceQueue` hoặc profilers **để quan sát**, không coi kết quả ngay lập tức là chứng cứ chắc chắn.
5. Chạy nhiều lần, kiểm tra số classes/loaders, Metaspace, threads, JAR file handles; so sánh xu hướng.
6. Nếu loader vẫn reachable, tìm đường strong reference như TCCL, thread-local, static cache, listener hoặc provider.

Tránh dùng `finalize()` hoặc finalizers làm cleanup contract: không có thời điểm chắc chắn, và finalization không phù hợp để giải quyết vòng đời resource rõ ràng. Ưu tiên `AutoCloseable` và quản lý lifecycle tường minh.

## 10. Đóng tài nguyên tự quản lý trong `ClassLoader` tùy biến

Nếu custom loader đọc JAR thủ công, bạn có hai cách thiết kế:

**A. Đọc class bytes trong phạm vi ngắn**, đóng `InputStream`/`JarFile` bằng try-with-resources ngay trong `findClass`. Loader không cần giữ open JAR giữa các lần tìm (đổi lại có thể chi phí I/O). Bảo đảm `defineClass` được gọi sau khi bytes đọc đầy đủ.

**B. Giữ lâu `JarFile`/file channel/cache** để tải nhanh. Khi ấy loader/container phải implement `AutoCloseable` và có trạng thái lifecycle `OPEN → CLOSING → CLOSED`, ngăn loads mới và khóa việc đóng với loads đang chạy.

Pseudocode tài nguyên:

~~~java
final class ResourceOwner implements AutoCloseable {
    private final java.util.jar.JarFile jar;
    private boolean closed;

    ResourceOwner(java.nio.file.Path path) throws java.io.IOException {
        jar = new java.util.jar.JarFile(path.toFile());
    }

    synchronized byte[] entryBytes(String name) throws java.io.IOException {
        if (closed) {
            throw new IllegalStateException("owner closed");
        }
        var entry = jar.getJarEntry(name);
        if (entry == null) {
            throw new java.io.FileNotFoundException(name);
        }
        try (var input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    @Override
    public synchronized void close() throws java.io.IOException {
        if (!closed) {
            closed = true;
            jar.close();
        }
    }
}
~~~

Đây là **class Java độc lập biên dịch trên Java 17+** nhưng không có `main`; nó chỉ minh họa owner của `JarFile` chứ không phải `ClassLoader` đầy đủ. `synchronized` tuần tự hóa read/close trong ví dụ này; một implementation khác có thể dùng read/write lock, reference counting hoặc state machine để tránh race. Cần đảm bảo các reads đang diễn ra không gặp file bị close giữa chừng.

**Không dùng `finalize` hoặc chỉ tin GC đóng file.** Khi resource còn ở trạng thái OPEN dù business plugin đã stop, file handles có thể giữ khóa JAR (đặc biệt hữu hình trên Windows), gây lỗi upgrade/remove artifact.

## 11. Tránh race giữa `close()` và class loading

Với `URLClassLoader`, tài liệu API nói thẳng: nếu một thread đang nạp class trong khi thread khác gọi `close()`, **kết quả load không xác định**. Vì thế cần tách trách nhiệm:

- Chặn việc cấp plugin handle / nhận request mới trước khi shutdown.
- Chờ các request/background jobs có khả năng nạp class/resource kết thúc.
- Không phát lệnh `close()` từ một cleanup thread trong khi còn plugin scanner đang `loadClass`.
- Nếu có lỗi stop/timeout, đưa trạng thái thành FAILED/QUARANTINED theo chính sách container, không claim resources đã được giải phóng sạch sẽ.
- Đồng bộ state (lifecycle lock/counter/phaser) theo quy tắc concurrency của hệ thống; một `AtomicBoolean closed` riêng **không đủ** để bảo đảm mọi load đã kết thúc trước close.

Có thể coi plugin runtime là một resource owner, thay vì đưa `URLClassLoader` ra nhiều modules rồi để ai cũng có thể `close()`. Trong API host, expose interface operations và lifecycle quản lý tập trung.

## 12. Bảo vệ binary/source compatibility khi reload

Giữa hai plugin generations, version phụ thuộc có thể khác. Kiểm tra:

1. **Shared API types không bị plugin shade/relocate nhầm** thành một bản riêng tại runtime.
2. Mã host không giữ `Class<?>`/method handles của generation cũ khi chuyển route sang generation mới.
3. Serialized objects, reflection, service interfaces và callbacks phải xử lý version boundaries; không cast object gen A sang class gen B chỉ dựa trên tên.
4. Có contract khả năng đóng: provider implement `AutoCloseable`, hoặc plugin manager quản lý close hooks theo thứ tự.
5. Các JAR/resources của generation cũ được close trước khi xóa/thay file, nhưng vẫn phải quản lý references tránh unload leak.
6. Không tự ý chuyển `parent-first` sang `child-first` trên toàn bộ packages để sửa dependency version; xác định packages sở hữu rõ ràng.

JPMS thêm khái niệm module identity/access/layer; việc nạp đúng class file vẫn có thể không đủ vì `requires`/`exports`/`opens` và module layer rules. Việc close loader không phải hành động “gỡ module” tự động khỏi layer đang được tham chiếu.

## 13. Lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân khả dĩ | Hướng sửa |
| --- | --- | --- |
| `Plugin cannot be cast to Plugin` | Hai class cùng name nhưng defining loaders khác | Chia sẻ contract qua parent; kiểm tra loader identity |
| `LinkageError: attempted duplicate class definition` | Gọi `defineClass` lặp cùng binary name trong loader | `findLoadedClass`, locking, không redefine |
| `NoClassDefFoundError` sau khi loader đã close | Một thành phần vẫn cần class mới sau shutdown | Ngăn activity, wait/drain tasks, chỉ close khi an toàn |
| `IOException` từ `URLClassLoader.close()` | Lỗi đóng file handles | Xử lý/log, suppressed errors, retry theo policy nhưng không giả định close sạch |
| JAR không xóa/đổi tên được trên Windows | `JarFile`/stream/loader còn mở | Đóng owner và URLClassLoader, tìm handles thực |
| Plugin cũ không unload sau GC | TCCL, threads, ThreadLocal, host registry/caches giữ loader | Shutdown/unregister/remove, heap dominator path |
| Parent API khác bản giữa host/plugin | Dependency packaging/shading sai | Shared API loader, contract test |
| `IllegalAccessError` ở module | JPMS readability/exports/opens | Sửa module graph/access, không bypass bằng class name |
| Child-first loader deadlock | Delegate vòng, khóa thô, concurrency sai | getClassLoadingLock/parallel capable policy, giản lược graph |
| ServiceLoader không nhận provider | TCCL sai, SPI metadata thiếu, provider type khác loader | Shared interface, loader đúng, cache/lifecycle đúng |
| Native library bị giữ | JNI/OS/native references và lifecycle khác classloading | Đóng native resources theo API, không tin `close(loader)` tự unload |

## 14. Checklist triển khai và review

- [ ] Định nghĩa **shared API packages** và **plugin-private packages**; test `sharedContractFromHost == sharedContractFromPlugin`.
- [ ] Không định nghĩa trùng binary name trong cùng loader; dùng delegation, `findLoadedClass` và locking thích hợp.
- [ ] Kiểm soát parent-first/child-first theo package, không để child define lại `java.*` hoặc API của host.
- [ ] Xác định rõ owner của loader, `JarFile`, InputStreams, executor, sockets, watchers, native handles và listeners.
- [ ] Các resource ngắn hạn có `try-with-resources`; owner dài hạn có `close()` rõ ràng và kiểm tra idempotence.
- [ ] Plugin shutdown có **quiesce → drain → cleanup → close → release**, xử lý lỗi từng bước.
- [ ] Mọi task đổi TCCL đều khôi phục trong `finally`; dọn ThreadLocal/registries.
- [ ] Không dùng loader sau `close` để tìm **class/resource mới do loader tự định nghĩa**.
- [ ] Không kỳ vọng `close()` làm các `Class` đã nạp, objects hoặc native resources biến mất.
- [ ] Test reload nhiều lần, quan sát loader retention/file handles, phân biệt hiện tượng GC tạm thời và leak thật.
- [ ] Review module access và class compatibility xuyên versions; không dùng `getName()` như bằng chứng cast được.

## 15. Bài tập tự kiểm tra

1. Hai `URLClassLoader` có URL giống nhau, cùng parent platform loader, mỗi loader tự định nghĩa `plugin.Widget`. Hai `Class` có `==` không? **Không**.
2. Hai child loaders đều delegate `api.Contract` về parent. Hai `Class` do `loadClass` trả có `==` không? **Có**, nếu parent thực sự define cùng runtime type.
3. `URLClassLoader.close()` có làm các object của class đã nạp invalid? **Không**.
4. Có thể gọi `close()` trong lúc thread khác đang load mà mong behavior xác định? **Không**, cần quiesce/drain.
5. Có `ClassLoader.close()` chuẩn cho mọi custom loader không? **Không**.
6. Nếu plugin manager bỏ reference loader nhưng executor thread còn TCCL trỏ tới loader, class unloading có được bảo đảm? **Không**.
7. Có nên set `System.gc()` để “bắt buộc unload” không? **Không**, chỉ là đề nghị.
8. Cách nào tránh class duplicate trong custom loader? **findLoadedClass + class loading lock + định nghĩa một lần**.
9. `ServiceLoader` đã cache provider plugin cũ nhưng bạn chỉ close loader: provider object có tự mất? **Không**, phải dọn cache/reference.
10. `parent-first` cho shared API có ý nghĩa gì? **Giữ contract ở cùng defining loader mà host/plugin cùng thấy**.
11. JNI native library có chắc unload khi URLClassLoader close không? **Không**, vòng đời native tách biệt.
12. Cách xác minh memory leak? **Kiểm tra strong reference roots và thử vòng stop/reload lặp, không chỉ nhìn một lần GC**.

## 16. Tài liệu đặc tả và API

- [JVMS §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): runtime class identity, defining/initiating loaders, loading constraints.
- [ClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findClass`, `findLoadedClass`, `defineClass`, `getClassLoadingLock` và `registerAsParallelCapable`.
- [URLClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/URLClassLoader.html): URL search path, `close`, JAR/file handles và concurrent close behavior.
- [AutoCloseable — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/AutoCloseable.html): scoped resource cleanup.
- [JarFile — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/jar/JarFile.html): native JAR file owner và lifecycle I/O.
- [Thread — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html): context class loader references.
- [ServiceLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html): provider discovery/cache và loader selection.

**Kết luận:** Một loader tùy chỉnh đúng không chỉ nạp được bytecode; nó phải **giữ cùng runtime identity cho shared contracts** và **đóng đúng tài nguyên do mình sở hữu** theo vòng đời được quản trị. `URLClassLoader.close()` giúp release file/JAR handles nhưng **không phải lệnh unload class**, không dừng plugin threads và không xóa mọi strong reference. Thiết kế cả type boundary, concurrency và cleanup ngay từ đầu để plugin reload không trở thành nguồn lỗi khó tái hiện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-class-loader-012">findLibrary và trách nhiệm tra cứu thư viện native</a>

<details>
<summary>Xem chi tiết</summary>

# `findLibrary` và trách nhiệm tra cứu thư viện native trong Custom ClassLoader

Khi ứng dụng Java sử dụng JNI (*Java Native Interface*), một lớp Java có thể yêu cầu nạp thư viện native qua `System.loadLibrary("codec")`. JVM lúc đó phải tìm **tệp thư viện của hệ điều hành** tương ứng, ví dụ `codec.dll` trên Windows hoặc `libcodec.so` trên Linux. Nhưng thư viện native không phải `.class` hay resource Java thông thường: nó được hệ điều hành/JVM nạp vào không gian địa chỉ của tiến trình, có vòng đời và lỗi liên kết riêng.

Với **custom class loader**, Java cung cấp điểm mở rộng:

```java
protected String findLibrary(String libname)
```

**`findLibrary` cho loader chỉ ra vị trí tuyệt đối của một native library mà những class do loader ấy nạp yêu cầu.** Nó không thực hiện việc nạp DLL/SO/DYLIB vào bộ nhớ, không định nghĩa Java class và không tự tìm thư viện trong JAR. Khi trả `null`, JVM có thể tìm tiếp theo `java.library.path`. Khi trả một đường dẫn, JVM sử dụng kết quả đó để thử nạp library qua cơ chế native.

Bài thuộc nhóm **Loader tùy chỉnh** của Java core – classloader. Mục tiêu: hiểu đúng lời gọi `findLibrary`, phân biệt `findClass`/`findResource`/`findLibrary`, chọn phương thức nạp native phù hợp, tự xây loader Java 17+ có override, phân tích lỗi JNI và quản lý an toàn thư viện cho plugin.

## 1. Vì sao class loader phải quan tâm thư viện native?

Một framework/plugin có thể được đóng gói thành hai phần:

- **Java classes:** bytecode trong `plugin.jar` hoặc thư mục classes, ví dụ `plugin.NativeEngine`.
- **Native library:** mã máy theo hệ điều hành và kiến trúc CPU, ví dụ `libengine.so`, `engine.dll` hoặc `libengine.dylib`.

Java có thể định nghĩa class plugin qua `URLClassLoader` hoặc custom loader. Nhưng lệnh `System.loadLibrary` phải định vị **bản native tương thích**. Nếu chỉ cấu hình đường tới Java class files, JVM **không mặc nhiên biết** thư viện native nằm ở đâu.

Sơ đồ:

```text
Host application
      |
      v
PluginLoader  ---- loadClass("plugin.NativeEngine") ---> bytecode .class
      |
      | class plugin thực thi System.loadLibrary("engine")
      v
JVM native-library loader
      |
      +-- hỏi PluginLoader.findLibrary("engine")
      |        |
      |        +-- trả absolute path ----> thử mở DLL/SO/DYLIB
      |        |
      |        +-- trả null ------------> thử java.library.path
      |
      v
Liên kết thư viện native vào tiến trình
      |
      v
JNI method được đăng ký/tìm theo quy tắc liên kết JNI
```

Đó là lý do API `ClassLoader` có hai trách nhiệm phân biệt: **định nghĩa/tìm class Java** và **hỗ trợ JVM tìm thư viện native gắn với class do loader nạp**.

## 2. Hợp đồng Java 17 của `ClassLoader.findLibrary`

Chữ ký:

```java
protected String findLibrary(String libname)
```

Theo API Java SE 17:

1. **JVM gọi method này** khi cần định vị thư viện native cho những class đã được nạp bằng loader đó.
2. Tham số `libname` là **tên logic của thư viện** mà lời gọi `loadLibrary` yêu cầu, không phải path tuyệt đối.
3. Kết quả hợp lệ khi tìm được là **chuỗi đường dẫn tuyệt đối tới thư viện native**.
4. Nếu kết quả **`null`**, VM tìm thư viện theo đường tìm thông thường được biểu diễn qua thuộc tính `java.library.path`.
5. **Triển khai mặc định trả `null`**. Subclass muốn quản lý native libraries ở nơi riêng cần override phương thức.
6. `findLibrary` là **`protected`**: không phải API public cho application tự gọi trực tiếp như `ClassLoader.loadClass`.

Điều quan trọng: `findLibrary` là **hook tìm đường dẫn**, không phải factory khởi tạo một class, và cũng không phải nơi nên gọi lại `System.loadLibrary` cho chính `libname`. Gọi như vậy có thể tạo vòng lặp tìm library. JVM đã có trách nhiệm thực hiện bước nạp thật sự sau khi nhận được path.

### So sánh các hook thường thấy trong custom loader

| Hook/API | Đối tượng được tìm | Kiểu kết quả | Ai dùng? |
| --- | --- | --- | --- |
| `findClass(String)` | Java binary name, ví dụ `plugin.Engine` | `Class<?>` | `loadClass`/JVM trong quy trình class loading |
| `findResource(String)` | Resource path, ví dụ `config/default.json` | `URL` hoặc `null` | `getResource`/`getResources` theo quy tắc delegation |
| `findLibrary(String)` | Native library logical name, ví dụ `engine` | **Absolute file path** hoặc `null` | JVM khi xử lý native library loading |
| `System.mapLibraryName(String)` | Logical native library name | Tên file theo nền tảng | Ứng dụng cần ánh xạ `engine` → tên file OS |
| `System.loadLibrary(String)` | Logical native library name | `void` hoặc lỗi | Code Java yêu cầu JVM nạp native library |
| `System.load(String)` | **Absolute native library path** | `void` hoặc lỗi | Code Java biết sẵn file đích, không yêu cầu hook tìm tên logic theo cách `loadLibrary` |

**Không thay `findClass` bằng `findLibrary`**: JVM không coi đường dẫn `.dll` là một Java class. Và `findLibrary` **không được gọi chỉ vì** code đọc một `.so` bằng `ClassLoader.getResourceAsStream`.

## 3. Tên logic, tên file hệ điều hành và đường dẫn tuyệt đối

Khi code viết:

```java
System.loadLibrary("engine");
```

tham số phải là tên thư viện **không kèm** directory path, prefix hoặc extension nền tảng. JVM sẽ xử lý tên logic theo quy tắc nền tảng. Ví dụ thường gặp:

| Hệ điều hành | Logical name | File name thường gặp |
| --- | --- | --- |
| Windows | `engine` | `engine.dll` |
| Linux | `engine` | `libengine.so` |
| macOS | `engine` | `libengine.dylib` hoặc dạng tương thích do runtime hỗ trợ |

Không tự ghép `".dll"` hoặc `"lib" + name + ".so"` cho mọi nền tảng. Sử dụng API:

```java
String nativeFileName = System.mapLibraryName("engine");
System.out.println(nativeFileName);
```

Đây là **đoạn minh họa đặt trong method**; kết quả phụ thuộc OS/JVM, vì vậy không có một stdout cố định dùng chung mọi máy.

Ví dụ cách tạo đường dẫn:

```java
Path nativeDir = Path.of("plugins", "engine", "native");
Path fullPath = nativeDir
        .toAbsolutePath()
        .resolve(System.mapLibraryName("engine"))
        .normalize();

System.out.println(fullPath.isAbsolute());
```

Đây là **đoạn minh họa** (cần `import java.nio.file.Path;`). Trên filesystem bình thường, dòng cuối in `true`. Chỉ mới tạo chuỗi path **không chứng minh** file tồn tại hoặc tải được.

Trong `findLibrary`, thường cần kiểm tra file có thực sự tồn tại là regular file hay không, path có bị thoát khỏi root cho phép không, và OS/CPU architecture có khớp không. Nếu không có bản tương ứng, **trả `null` nếu chính sách cho phép fallback**; hoặc khi muốn fail closed, có thể trả path tới artifact kỳ vọng và để JVM báo lỗi, nhưng phải làm vậy có chủ đích. Không trả chuỗi tương đối như `"native/libengine.so"`.

## 4. Luồng thực thi và loader nào được hỏi?

Phần tinh tế là **loader của class thực hiện lời gọi native loading**.

Giả sử:

```text
                  System/Application loader
                       |  host.NativeHelper
                       |
                       v parent
                 PluginClassLoader
                       |  plugin.NativeEngine
                       |
                       +-- native/engine.dll, native/libengine.so ...
```

Nếu **`plugin.NativeEngine`** (được plugin loader định nghĩa) gọi `System.loadLibrary("engine")` từ static initializer, native lookup được liên hệ với loader của class gọi ấy; JVM có thể gọi **`PluginClassLoader.findLibrary("engine")`**.

Nhưng nếu một method **`host.NativeHelper.loadIt()`** do system loader định nghĩa mới là nơi trực tiếp thực hiện `System.loadLibrary("engine")`, runtime dùng ngữ cảnh của class caller là helper, **không tự suy ra plugin loader** từ object plugin ở stack trên. Do đó override `findLibrary` ở plugin loader có thể **không được gọi** trong trường hợp helper của host thực hiện native load.

Điểm khác biệt này thường gây câu hỏi “tại sao đã override `findLibrary` nhưng không thấy log?”. Hãy kiểm tra **class chứa câu lệnh `System.loadLibrary` đang được defining loader nào nạp**.

Cũng đừng nhầm với **thread context class loader (TCCL)**: `Thread.currentThread().getContextClassLoader()` được một số SPI framework chủ động sử dụng, nhưng JVM không vì thế mà **mặc định** thay loader tra native libraries theo TCCL ở mọi `System.loadLibrary`. Nếu ứng dụng muốn dùng plugin context, hãy thiết kế method load trong class do plugin loader định nghĩa hoặc dùng chiến lược `System.load(absolutePath)` với quyền sở hữu rõ ràng.

### Loader được yêu cầu và defining loader

Một class `plugin.Entry` được gọi qua `customLoader.loadClass(...)` chưa chắc **do** custom loader định nghĩa nếu parent đã có class cùng tên. Lệnh native của `Entry` sẽ hoạt động theo **defining loader thực sự** của class. Luôn kiểm tra:

```java
Class<?> entry = customLoader.loadClass("plugin.Entry");
System.out.println(entry.getClassLoader() == customLoader);
```

Đây là **đoạn minh họa**, với `customLoader` đã được tạo và method xử lý `ClassNotFoundException`. Nếu in `false`, phải điều tra parent delegation/classpath trùng trước khi kết luận `findLibrary` ở custom loader có hiệu lực.

## 5. Ví dụ Java 17+ chạy được mà không cần cài native compiler

Ví dụ cố ý **không cung cấp native library hợp lệ**. Mục đích là chứng minh JVM thực sự gọi `findLibrary` của loader tùy chỉnh, đường dẫn được chọn là absolute path, và lỗi nạp file không tồn tại được báo đúng dạng. Ta sẽ **bắt lỗi được dự đoán**, nên cả chương trình kết thúc bình thường.

### 5.1 Cấu trúc

```text
native-demo/
├── src/
│   └── plugin/
│       └── Entry.java
├── NativeLookupDemo.java
├── plugin-classes/        (output plugin)
└── host-classes/          (output host)
```

Tạo hai thư mục output trước khi compile. Chạy toàn bộ lệnh từ `native-demo`. **Không** đưa `plugin-classes` vào classpath của host.

### 5.2 Class plugin là nơi gọi `System.loadLibrary`

File `src/plugin/Entry.java`:

```java
package plugin;

public final class Entry {
    public static void attemptLoad() {
        System.loadLibrary("step4_missing_native_demo_159");
    }
}
```

Tên thư viện dài và đặc biệt ở đây được cố ý chọn để **không trùng** với library thật trên máy. Không có JNI method để thực thi: mục đích của test là riêng **native lookup**.

### 5.3 Custom loader override `findLibrary`

File `NativeLookupDemo.java`:

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;

public class NativeLookupDemo {
    static class PluginLoader extends URLClassLoader {
        private final Path nativeDirectory;

        PluginLoader(URL[] urls, ClassLoader parent, Path nativeDirectory) {
            super(urls, parent);
            this.nativeDirectory = nativeDirectory;
        }

        @Override
        protected String findLibrary(String name) {
            System.out.println("findLibrary: " + name);

            if (!name.equals("step4_missing_native_demo_159")) {
                return null;
            }

            return nativeDirectory
                    .resolve(System.mapLibraryName(name))
                    .toAbsolutePath()
                    .toString();
        }
    }

    public static void main(String[] args) throws Exception {
        Path classes = Path.of("plugin-classes");
        Path nativeDir = Path.of("intentionally-empty-native-dir");

        try (PluginLoader loader = new PluginLoader(
                new URL[] {classes.toUri().toURL()},
                ClassLoader.getPlatformClassLoader(),
                nativeDir)) {

            Class<?> entry = loader.loadClass("plugin.Entry");
            System.out.println("custom defining loader: "
                    + (entry.getClassLoader() == loader));

            try {
                entry.getMethod("attemptLoad").invoke(null);
                System.out.println("unexpected success");
            } catch (InvocationTargetException e) {
                System.out.println("native load error: "
                        + e.getCause().getClass().getSimpleName());
            }
        }
    }
}
```

Chú ý:

- `PluginLoader` extends `URLClassLoader` để tìm `plugin.Entry.class` từ `plugin-classes`.
- Parent = `ClassLoader.getPlatformClassLoader()`, trong ví dụ này không chứa `plugin.Entry`, giúp bảo đảm class plugin do loader tùy chỉnh **định nghĩa thật**.
- `findLibrary` nhận tên logic `step4_missing_native_demo_159` và trả đường dẫn tuyệt đối tới file native **không tồn tại**. Không gọi lại `System.loadLibrary` trong override.
- `getMethod(...).invoke(null)` kích hoạt **static method do class plugin định nghĩa**. Vì lệnh `System.loadLibrary` nằm bên trong `plugin.Entry`, JVM chọn ngữ cảnh loader plugin.
- Method reflection bọc lỗi phát từ target method trong `InvocationTargetException`; test chỉ in tên loại `getCause()`. Nó không coi lỗi native giả lập là thành công.

### 5.4 Biên dịch và chạy

```bash
javac --release 17 -d plugin-classes src/plugin/Entry.java
javac --release 17 -d host-classes NativeLookupDemo.java
java -cp host-classes NativeLookupDemo
```

Kết quả application stdout đã được kiểm tra khi biên dịch bằng `javac --release 17` và thực thi:

```text
custom defining loader: true
findLibrary: step4_missing_native_demo_159
native load error: UnsatisfiedLinkError
```

JDK mới hơn có thể **in thêm cảnh báo native access trên stderr** khi gọi `System.loadLibrary`, trong khi ba dòng application stdout phía trên vẫn đúng. Cảnh báo quyền native access và `UnsatisfiedLinkError` là **hai vấn đề khác nhau**; xem phần Java 17+/JPMS ở cuối bài.

**Không được kết luận việc override thành công chỉ vì `UnsatisfiedLinkError` xuất hiện:** phải nhìn thấy dòng `findLibrary: ...` và xác nhận class plugin thật sự do custom loader định nghĩa. Nếu loader không bao giờ được hỏi, lỗi có thể đến từ `java.library.path` hoặc helper loader khác.

## 6. Tại sao trả `null` lại có ý nghĩa?

Triển khai mặc định của `ClassLoader.findLibrary` trả `null`. JavaDoc quy định khi method này trả `null`, VM tìm library trong đường dẫn do thuộc tính `java.library.path` mô tả.

Triển khai tùy chỉnh thường muốn ưu tiên một thư mục native riêng nhưng giữ fallback:

```java
@Override
protected String findLibrary(String name) {
    Path file = nativeDir.resolve(System.mapLibraryName(name));

    if (java.nio.file.Files.isRegularFile(file)) {
        return file.toAbsolutePath().toString();
    }
    return null; // JVM dùng cơ chế lookup thông thường.
}
```

Đây là **đoạn mã minh họa trong subclass `ClassLoader`**, giả định `nativeDir` có sẵn. Nên chuẩn hóa/kiểm tra path và nguồn thư viện đáng tin trước khi dùng trong ứng dụng thật.

Ba kết quả cần phân biệt:

| `findLibrary` trả | Diễn giải | Kết quả có thể xảy ra |
| --- | --- | --- |
| Absolute path tồn tại, native đúng kiến trúc và dependency đầy đủ | JVM thử load thư viện đích | Thành công hoặc lỗi liên kết/native khác |
| Absolute path không tồn tại, sai bitness hoặc hỏng | JVM đã có ứng viên rõ ràng nhưng không thể nạp | `UnsatisfiedLinkError`, không suy ra sẽ tìm tiếp mọi thư mục |
| `null` | Loader không cung cấp path riêng | JVM thực hiện lookup mặc định theo quy tắc `java.library.path` |

**Không nên dùng return `null` như cơ chế báo “hãy bỏ qua native support hoàn toàn”.** Nó chỉ có nghĩa custom loader không đề xuất đường dẫn; JVM có thể vẫn tìm được và nạp phiên bản khác trong system library path. Nếu plugin cần một phiên bản native **cụ thể và bắt buộc**, thiết kế không được cho fallback vô tình lấy file khác tên trùng từ môi trường.

### `java.library.path` khác `java.class.path`

| Thuộc tính / tùy chọn | Vai trò |
| --- | --- |
| `java.class.path`, `-cp` | Java classes và resources của application classpath |
| `java.library.path`, thường cấu hình khởi động bằng `-Djava.library.path=...` | Đường tìm native libraries khi cơ chế native lookup dùng |
| `System.mapLibraryName` | Tên file native theo OS cho tên logic |
| `PATH` trên Windows, `LD_LIBRARY_PATH` trên Linux, `DYLD_*` trên macOS | Các biến môi trường thuộc cơ chế tìm shared libraries/dependencies của hệ điều hành; hành vi tùy nền tảng/launcher và chính sách bảo vệ |
| `java.home` | Đường tới cài đặt runtime Java, không phải plugin native directory |

Ví dụ một đường tìm native ở startup:

```bash
java -Djava.library.path=./native -cp host-classes NativeLookupDemo
```

Lệnh này chỉ minh họa **cú pháp cấu hình native search path**. Vì test `NativeLookupDemo` chủ động trả absolute path tới file giả không tồn tại, không nên trông chờ `-Djava.library.path` giải cứu lần nạp được override đó.

Trong ứng dụng Java 17, nên quyết định `java.library.path` từ **startup configuration**; đừng viết code thay `System.setProperty("java.library.path", ...)` lúc đang chạy rồi mặc định JVM sẽ lập tức dùng đường mới. JVM có thể cache native-library search configuration và hành vi phụ thuộc runtime.

## 7. `System.loadLibrary` khác `System.load` ở điểm nào?

Hai API có trách nhiệm khác nhau:

```java
System.loadLibrary("engine");
```

- Đầu vào là **tên logic** không kèm path/prefix/extension.
- Thư viện được tìm theo cơ chế native-library lookup phù hợp, bao gồm hook `findLibrary` của loader tương ứng khi JVM yêu cầu.
- Dễ viết portable nếu thư viện được đặt đúng đường tra.

```java
System.load("/opt/myapp/native/libengine.so");
```

- Đầu vào là **đường dẫn tuyệt đối** tới thư viện (ví dụ trên là Linux).
- Không yêu cầu tên logic để `findLibrary` quyết định vị trí.
- Phù hợp khi ứng dụng đã kiểm chứng exact file và muốn chỉ định bản native cụ thể; path không portable nếu hard-code.

Đoạn trên là **minh họa API**, không thể chạy thành công nếu file native chưa tồn tại và chưa được build phù hợp. Đừng dùng `System.load("engine")` rồi kỳ vọng Java tự tìm file từ `java.library.path` như `loadLibrary`; nếu đường dẫn không tuyệt đối, API có thể phát `UnsatisfiedLinkError`.

### Vì sao không gọi `System.load` ngay trong `findLibrary`?

`findLibrary` được JVM gọi **trong lúc** đang cố load native library. Nó chỉ phải trả **path string**. Nếu tự gọi thêm `System.load` hay `System.loadLibrary` bên trong hook, bạn tạo thêm thao tác load khác trong lúc lookup hiện tại chưa xong. Điều này làm tăng nguy cơ reentrancy, lỗi trùng load, deadlock hoặc exception khó chẩn đoán.

Thiết kế đúng:

```text
Java class yêu cầu System.loadLibrary("engine")
                    |
                    v
JVM -> customLoader.findLibrary("engine")
                    |
                    v
customLoader trả "/trusted/native/.../libengine.so"
                    |
                    v
JVM thực hiện load và JNI linking
```

## 8. JNI liên quan tới định danh class loader như thế nào?

Native code **không chạy trong sandbox bytecode Java**. Các hàm JNI dùng pointers/handles tới JVM và liên kết tên symbol hoặc đăng ký native methods. Do đó JNI phải bảo toàn ranh giới giữa các class loaders.

JNI Specification quy định: **mỗi class loader quản lý tập native libraries liên quan tới nó**; cùng một native library JNI **không được load vào hơn một class loader đồng thời**. Nếu cố làm vậy, `System.loadLibrary` có thể phát `UnsatisfiedLinkError`.

Ví dụ tình huống:

```text
PluginLoader A ----loadLibrary("engine")----> /opt/plugin/native/libengine.so
PluginLoader B ----loadLibrary("engine")----> /opt/plugin/native/libengine.so
                                                   |
                                                   v
                                  Có thể lỗi: library already loaded
                                  in another classloader
```

Đây là một **tình huống minh họa**, không phải output chính xác cố định. Quy tắc đặc tả là không dùng đồng thời cùng JNI library trong hai namespace loader khác nhau. Trên thực tế thông báo chi tiết của `UnsatisfiedLinkError` và hành vi path/canonicalization phụ thuộc JVM/OS.

Điều này ảnh hưởng mạnh tới plugin reload:

- Class Java mới có thể được định nghĩa trong loader mới, nhưng bản JNI library ở path cũ có thể vẫn gắn với loader cũ.
- Gọi `URLClassLoader.close()` chỉ đóng các nguồn URL classpath; **không phải lệnh JNI unload**.
- Để native library có thể được unload, loader cũ và các class/object liên quan cần đủ điều kiện GC thu hồi theo các quy tắc JVM.
- JNI `JNI_OnUnload` là hook vòng đời có thể được JVM gọi khi loader chứa native library bị thu hồi; không dùng nó như một callback xảy ra ngay khi gọi `close()`.
- Với native state còn giữ global references, threads hoặc OS resources, việc shutdown phải có hợp đồng rõ, độc lập với JVM GC.

**Không giải quyết xung đột JNI class loader bằng cách tùy tiện copy DLL/SO thành hàng chục đường dẫn mới** nếu native library không hỗ trợ nhiều instance/cùng process. Cách này có thể dẫn đến state duplication, crash và data corruption. Hãy xem thư viện native có hỗ trợ multi-loader/multi-instance không; nếu không, cân nhắc một layer JNI chung ổn định do loader chung sở hữu.

## 9. Lỗi `UnsatisfiedLinkError` không phải chỉ có một nguyên nhân

| Dạng vấn đề | Ví dụ nguyên nhân | Cách kiểm tra |
| --- | --- | --- |
| **Library chưa được tìm thấy** | `findLibrary` trả `null`, `java.library.path` không có file | In tên logic, mapped filename, startup native path |
| **Path được trả không hợp lệ** | `findLibrary` trả relative path hoặc absolute path không tồn tại | `Path.isAbsolute()`, `Files.isRegularFile`, normalized/canonical path |
| **Sai kiến trúc** | Chạy 64-bit JVM nhưng chỉ có 32-bit native DLL/SO | Xác minh `os.arch`, file architecture và ABI |
| **Thiếu dependency native** | Main `engine.dll` tồn tại nhưng thư viện C/C++ mà nó phụ thuộc không có | Công cụ loader OS, lệnh dependency inspection phù hợp |
| **Sai JNI symbol/signature** | Library load thành công nhưng lời gọi `native` method không tìm được entry phù hợp | `javac -h`, JNI naming hoặc `RegisterNatives` |
| **Library đã gắn loader khác** | Hai custom loaders thử nạp cùng JNI library | Xác định defining loader của caller và lifecycle plugin |
| **Native access restrictions** | Runtime mới yêu cầu cấp quyền native access cho module caller | Kiểm tra phiên bản JDK, flags và cảnh báo/lỗi về native access |
| **Static linked library khác dynamic** | Runtime tích hợp thư viện native và có `JNI_OnLoad_<L>` | Xem JNI Invocation API; không suy từ việc file vật lý không tồn tại |
| **Bị chặn quyền OS** | File không được phép load, bị quarantine/policy/antivirus | Kiểm tra OS permissions và security logs |

`UnsatisfiedLinkError` là subclass của `LinkageError`. **Đừng bắt rồi im lặng** trong hệ thống production: cần log tên library, đường dự kiến, caller defining loader, OS/architecture và nguyên nhân root. Nếu native setup được gọi trong `static` initializer, lỗi có thể làm class rơi vào trạng thái erroneous; lần dùng sau có thể thấy `NoClassDefFoundError`.

### Library có file trên đĩa vẫn có thể lỗi

Giả sử `findLibrary("engine")` trả `C:\app\native\engine.dll`. Điều đó chỉ giải quyết *vị trí của library chính*. Windows loader vẫn có thể không tìm được dependency DLL thứ hai, hoặc library không phải DLL hợp lệ cho kiến trúc CPU đang dùng.

Tương tự, một `libengine.so` có tồn tại trên Linux vẫn có thể báo lỗi do thiếu `libstdc++.so` tương thích, sai ELF class, symbol bị thiếu hoặc dynamic linker configuration. **Đừng đọc mỗi câu “file exists” rồi kết luận `findLibrary` đã làm xong mọi trách nhiệm end-to-end.**

## 10. Thiết kế `findLibrary` cho plugin an toàn

Một loader plugin có thể có đường native root riêng:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;

public final class NativePluginLoader extends URLClassLoader {
    private final Path nativeRoot;

    public NativePluginLoader(
            URL[] classUrls, ClassLoader parent, Path nativeRoot) {
        super(classUrls, parent);
        this.nativeRoot = nativeRoot.toAbsolutePath().normalize();
    }

    @Override
    protected String findLibrary(String logicalName) {
        // Chỉ cho phép những library đã biết của plugin.
        if (!logicalName.equals("engine")
                && !logicalName.equals("codec")) {
            return null;
        }

        String fileName = System.mapLibraryName(logicalName);
        Path candidate = nativeRoot.resolve(fileName).normalize();

        if (!candidate.startsWith(nativeRoot)) {
            return null;
        }
        if (!Files.isRegularFile(candidate)) {
            return null;
        }
        return candidate.toString();
    }
}
```

Đây là **class Java độc lập có thể biên dịch với JDK 17+**; chưa thể kiểm thử native loading thực sự nếu `engine`/`codec` native files chưa được cung cấp. Danh sách allowlist, path normalization và kiểm tra regular file giúp giảm lỗi đặt sai file hoặc path traversal. Nhưng trong môi trường không tin cậy, **không đủ để ngăn symlink race**, thay file sau khi kiểm tra hay binary độc hại. Cần xác minh nguồn artifact, quyền filesystem và tính toàn vẹn trước khi JVM nạp library.

**Trách nhiệm thiết kế quan trọng:**

1. **Đặt owner rõ ràng:** plugin nào được quyền sở hữu/đòi native lib nào? Shared JNI library có nên để loader chung nạp một lần?
2. **Xác định platform artifact:** OS, CPU architecture, ABI, runtime C/C++ dependencies tương thích.
3. **Ánh xạ tên portable:** `System.mapLibraryName` thay vì hard-code extension.
4. **Chỉ trả absolute path:** `findLibrary` theo hợp đồng API. Dùng path canonical/real path sau khi đã có artifact, tránh nhầm những location tương đương.
5. **Fail closed khi cần:** nếu không cho phép fallback sang system library cùng tên, cần một chính sách rõ ràng; `return null` không phải fail closed.
6. **Không giữ locks nặng trong hook:** native library loading có thể diễn ra trong initialization và có tương tác bất đồng bộ, tránh recursive load/deadlock.
7. **Log vừa đủ:** ghi logical name và mapping, không rò rỉ path bí mật vào log public.
8. **Quản lý lifecycle:** không unload/reload vội khi vẫn còn native methods, global references hoặc threads chạy.

Nếu thư viện native được **đóng gói bên trong JAR** dưới `native/windows-x64/engine.dll` hay `native/linux-x64/libengine.so`, JVM không thể chỉ nhận một `jar:file:...!/...` URL qua `findLibrary` rồi coi đó là file native trên đĩa. Thông thường plugin manager cần **giải nén tới một thư mục an toàn**, kiểm tra tên/kiến trúc/tính toàn vẹn/quyền truy cập, rồi cho `findLibrary` trả đường dẫn tuyệt đối. Cần quản lý xóa file sau khi không còn library/OS handles đang dùng; có hệ điều hành không cho xóa/thay file đang được load.

## 11. `findLibrary` và phương thức nạp native nằm ở đâu trong kiến trúc?

Một thiết kế dễ bảo trì:

```text
Application host
  |
  +-- Shared Java API (interface/DTO) ở parent loader
  |
  +-- PluginManager
        |
        +-- kiểm tra platform + chữ ký/native artifacts
        +-- chuẩn bị trusted native directory
        +-- tạo PluginClassLoader(class URLs, parent, native directory)
        +-- nạp plugin.Entry do PluginClassLoader định nghĩa
        |
        v
     plugin.Entry (Java code của plugin)
        |
        +-- static { System.loadLibrary("engine"); }
        |             |
        |             v
        |        PluginClassLoader.findLibrary("engine")
        |             |
        |             v
        |        return absolute path
        |             |
        |             v
        |        JVM/OS native loader
        |
        +-- native method / JNI entry
```

**Chủ đích:** logic Java của plugin nên có một điểm rõ ràng chịu trách nhiệm gọi `System.loadLibrary`. Plugin manager quản lý artifact và loader, còn hook `findLibrary` chỉ ánh xạ tên → file hợp lệ. Có thể tách thêm lớp kiểm tra chữ ký, phiên bản và platform compatibility.

Nếu một shared Java host class chứa lời gọi `System.loadLibrary`, hãy cân nhắc cấu trúc JNI bridge ở **loader chung** tương ứng, hoặc explicit absolute load với chính sách sở hữu. Đừng ngẫu nhiên thay TCCL chỉ để mong làm native loader chọn đường của plugin: đó không phải hợp đồng của `findLibrary`.

## 12. Tại sao thêm `-Djava.library.path` vẫn chưa sửa được lỗi?

Một chuỗi tình huống minh họa:

```text
1. plugin.Entry gọi System.loadLibrary("engine")
2. PluginClassLoader.findLibrary("engine") trả C:\plugins\engine-v2.dll
3. File trên không tồn tại hoặc sai kiến trúc
4. UnsatisfiedLinkError xảy ra
5. Đổi -Djava.library.path tới thư mục khác
6. Vẫn có thể lỗi vì findLibrary tiếp tục trả absolute path cũ
```

Nếu `findLibrary` trả một candidate cụ thể, debugger cần kiểm tra chính path ấy. Nếu nó trả `null`, mới xem kỹ `java.library.path` và quy tắc native lookup mặc định.

Một nhầm lẫn khác: người dùng chỉ thêm thư mục DLL vào **Java classpath `-cp`**. Điều đó khiến `ClassLoader` có thể tìm **class/resource Java**, **không bảo đảm** JVM native linker tìm được DLL/SO qua chính classpath. Native library path là cơ chế riêng.

### Khi chạy trong môi trường container

Trong container/Docker:

- File JNI phải được copy/mount vào đúng container filesystem, không phải đường của host OS.
- Linux container cần thư viện theo **Linux ABI/CPU**; Windows DLL không hoạt động chỉ vì tên logic giống.
- Dynamic linker còn cần các dependency native của file chính.
- Native file có thể là resource từ image layer không có quyền ghi hoặc execute; cân nhắc extraction directory phù hợp.
- Nếu dùng custom loader, `findLibrary` nên trả path **bên trong container** đã tồn tại và được xác minh.

## 13. Quản lý native library khi reload/unload custom loader

Native lifecycle khác vòng đời Java classes:

| Sự kiện | Kết quả |
| --- | --- |
| `ClassLoader.loadClass` | Tìm/định nghĩa Java class; không tự nạp native library nếu class chưa thực hiện thao tác yêu cầu |
| `System.loadLibrary` thành công | JVM ghi nhận native library đã được nạp cho ngữ cảnh loader thích hợp |
| `URLClassLoader.close()` | Đóng các nguồn classpath/JAR do URL loader sở hữu; **không là JNI unload API** |
| Loader không còn reachable và GC thu hồi | JVM **có thể** unload các class/native libraries gắn với loader theo quy tắc runtime |
| `JNI_OnUnload` nếu được cung cấp | Hook có thể được gọi khi runtime unload native lib; không phải callback bắt buộc ngay sau close |
| Tạo loader mới cho phiên bản plugin mới | Có namespace Java mới, nhưng vẫn phải xử lý xung đột JNI native library đang gắn loader cũ |

Plugin reload an toàn thường cần:

1. Ngừng yêu cầu mới và đợi mọi Java/native task đang chạy hoàn tất.
2. Ngắt callbacks, shutdown native services theo API của thư viện native và giải phóng JNI global refs nếu có.
3. Unregister listeners, caches, thread locals và context loader references còn giữ plugin.
4. Close nguồn URL của loader khi không còn cần load class/resource mới.
5. Bỏ strong references tới class/loader cũ; chấp nhận rằng GC/class-unloading không có thời điểm bảo đảm.
6. Chỉ mở phiên bản mới sau khi đã giải quyết quy tắc reuse/exclusivity của native lib; nếu cần hard isolation, có thể phải dùng **process riêng**.

Không làm điều này:

```java
oldLoader.close();
System.gc();          // Không bảo đảm unload native lib.
newLoader.loadClass("plugin.Entry"); // Không bảo đảm có thể load lại same JNI.
```

Đoạn trên là **mã minh họa**, không biên dịch độc lập nếu chưa khai báo `oldLoader` và `newLoader`. `System.gc()` là hint, không phải JNI unload operation. Xung đột `already loaded in another classloader` cần thiết kế ownership/lifecycle, không chữa bằng việc ép GC giả định.

## 14. Kiểm thử `findLibrary` mà không làm hỏng môi trường máy

Có ba mức kiểm thử, nên phân biệt:

**Mức A – unit test chỉ ánh xạ path.** Cho loader một `nativeRoot` tạm và tên logic đã biết; kiểm tra hook trả absolute path, không thoát root, và `null` cho tên ngoài allowlist. Vì method là `protected`, test có thể đặt trong subclass cùng package/test helper để gọi bằng wrapper test-only.

**Mức B – integration test thiếu library có chủ đích.** Ví dụ `NativeLookupDemo` ở mục 5 xác nhận JVM **thực sự đi qua** `findLibrary` và báo `UnsatisfiedLinkError` khi không có file. Đây là thử nghiệm JVM lookup, không phải thử nghiệm JNI symbol thực.

**Mức C – integration test với JNI library thật.** Biên dịch Java native declaration, dùng `javac -h` để sinh C header; build DLL/SO/DYLIB tương thích, đặt vào thư mục được map; test native method end-to-end, kiểm tra ABI, dependency và unload/reload semantics. Nên chạy riêng theo OS/architecture trong CI, không dự đoán stdout tuyệt đối khi native code và platform khác nhau.

### Ví dụ khai báo một native method

```java
package plugin;

public final class NativeMath {
    static {
        System.loadLibrary("native_math");
    }

    public static native int add(int a, int b);
}
```

Đây là **class Java biên dịch được**, nhưng gọi `NativeMath.add(...)` sẽ cần native library thực có JNI implementation đúng tên/signature. Có thể sinh header:

```bash
javac --release 17 -h headers -d plugin-classes src/plugin/NativeMath.java
```

Nếu thực hiện, cần tạo đúng file `src/plugin/NativeMath.java`, thư mục output và native implementation trước khi dùng method; đoạn này minh họa cơ chế, **không tuyên bố đã kiểm thử thư viện C thật**.

Sử dụng `javac -h` giúp tránh đoán JNI mangled names cho overloaded methods. Khi module/native access restrictions của JDK tương lai áp dụng, còn cần khởi chạy JVM với quyền native access theo tài liệu phiên bản JDK.

## 15. Những hiểu lầm cần tránh

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “`findLibrary` tự nạp và liên kết DLL.” | Nó **trả đường dẫn**; JVM/OS thực hiện native load và linking. |
| “Mọi subclass `ClassLoader` bắt buộc override `findLibrary`.” | Không; mặc định trả `null`, JVM dùng native lookup path thông thường. |
| “`findLibrary` phải trả `URL` của resource trong JAR.” | Hợp đồng trả **absolute file path string** tới native library. |
| “`System.loadLibrary("engine.dll")` là portable.” | `loadLibrary` nhận tên logic như `engine`, không kèm extension/path. |
| “`System.load` và `System.loadLibrary` giống nhau hoàn toàn.” | `load` nhận **absolute path**; `loadLibrary` nhận **logical name**. |
| “Classpath `-cp` chứa JAR thì JVM sẽ tự tìm DLL trong JAR ấy.” | Không. Native library cần cơ chế file/path/load riêng. |
| “Đổi TCCL là JVM dùng plugin `findLibrary`.” | Không mặc định; phải xem defining loader của class thực hiện native loading. |
| “`findLibrary` trả `null` nghĩa là fail ngay.” | Nó cho phép JVM dùng lookup theo `java.library.path`. |
| “File native tồn tại tức sẽ nạp thành công.” | ABI, dependencies, symbol exports, quyền OS hoặc trùng loader có thể vẫn lỗi. |
| “Hai class loaders cùng load một JNI library mà không liên quan nhau.” | JNI spec hạn chế cùng library nạp vào nhiều class loaders; có thể `UnsatisfiedLinkError`. |
| “`URLClassLoader.close()` giải phóng native lib ngay.” | Close classpath sources khác JNI/native unloading; GC/lifecycle mới liên quan. |
| “`System.gc()` bảo đảm dỡ JNI và reload version mới.” | Không có bảo đảm đó. |

## 16. Tự kiểm tra có đáp án

**1.** `findLibrary("codec")` cần trả gì khi thư viện nằm ở `C:\app\native\codec.dll` trên Windows?

**Đáp án:** Chuỗi **đường dẫn tuyệt đối** đến file đó nếu file là ứng viên đúng. Không trả tên logic `codec` hay đường tương đối `native/codec.dll`.

**2.** Nếu `findLibrary` trả `null`, JVM lập tức phát `UnsatisfiedLinkError` đúng không?

**Đáp án:** Không. Theo Java 17 API, JVM tìm library theo `java.library.path`; chỉ khi không thể tìm/nạp library thì việc load mới có thể thất bại.

**3.** `System.mapLibraryName("engine")` trả gì?

**Đáp án:** **Tên file phụ thuộc nền tảng**; ví dụ Windows thường là `engine.dll`, Linux là `libengine.so`. Không hard-code một output cho mọi OS.

**4.** Vì sao override `findLibrary` trong plugin loader không chạy nếu `host.NativeHelper` gọi `System.loadLibrary`?

**Đáp án:** Loader dùng khi native lookup gắn với class caller trực tiếp yêu cầu load. Nếu `NativeHelper` do system loader định nghĩa, đó không phải plugin loader; TCCL không tự sửa sự lựa chọn ấy.

**5.** Vì sao native library đã được tìm thấy vẫn `UnsatisfiedLinkError`?

**Đáp án:** Có thể sai CPU bitness/ABI, thiếu native dependency, symbol JNI không phù hợp, library đã được loader khác nạp hoặc bị hạn chế bởi OS/JVM. Cần đọc thông báo và kiểm tra nguyên nhân cụ thể.

**6.** Plugin muốn load JNI từ JAR resource. Có thể return `"jar:file:/...!/libengine.so"` từ `findLibrary` không?

**Đáp án:** Không đúng hợp đồng absolute native file path. Thông thường cần extract đến file thật ở trusted directory, xác minh nó và trả đường dẫn tuyệt đối.

**7.** `URLClassLoader.close()` có ngay lập tức giải quyết lỗi library already loaded in another classloader không?

**Đáp án:** Không. Nó đóng nguồn classpath/JAR do loader phụ trách, không tương đương native library unloading. Class loader cũ còn sống/GC chưa thu hồi vẫn có thể gắn native library.

**8.** Mục đích của `System.load("/abs/path/libengine.so")` là gì?

**Đáp án:** Chỉ định file native bằng **absolute pathname**, không cần `findLibrary` map từ tên logic như `System.loadLibrary`. Đường path thực tế vẫn phải hợp OS/ABI và nạp được.

**9.** Nếu `findLibrary` trả path đúng file nhưng ở thư mục plugin không đáng tin, có an toàn không?

**Đáp án:** Không. Native code có quyền tác động trực tiếp vào tiến trình. Cần kiểm tra nguồn artifact, quyền ghi/thay file, symlink/path traversal, chữ ký/hash và isolation nếu code không tin cậy.

**10.** Khi nào nên cân nhắc đưa JNI bridge vào loader chung thay vì mỗi plugin tự nạp?

**Đáp án:** Khi native library không hỗ trợ nhiều class-loader instances hoặc cần chia sẻ native state trong một JVM. Bridge chung cần API contract ổn định, ownership rõ và xử lý concurrent access/native lifecycle.

## 17. Checklist trước khi vận hành custom loader có JNI

1. **Caller đúng:** Class chứa lệnh `System.loadLibrary` được defining loader nào định nghĩa?
2. **Hook đúng:** `findLibrary` có được override tại loader ấy và trả absolute path/`null` đúng hợp đồng?
3. **Mapping đúng:** Logical name, `System.mapLibraryName` và file name theo OS đã khớp?
4. **Root an toàn:** Directory đã normalize/canonicalize, artifact có nguồn đáng tin, không bị thay sau xác minh?
5. **Artifact phù hợp:** OS, architecture, ABI, phụ thuộc native, exports JNI đã kiểm tra?
6. **Module/runtime:** Quyền native access và chính sách runtime có cho phép JNI theo JDK đang chạy?
7. **Không trùng loader:** JNI library có đang được nạp bởi defining loader khác hay không?
8. **Error report:** Ghi loại lỗi và cause, không che mất `UnsatisfiedLinkError` bằng thông báo chung “class not found”?
9. **Lifecycle:** Plugin có stop tasks/native resources, release references, close URL loader đúng lúc không?
10. **Reload:** Cần loader/process mới hay JNI bridge chung để quản lý native library version?
11. **Tests:** Unit mapping + integration thiếu file + test native thật trên từng nền tảng có trong CI.
12. **Fallback rõ:** `return null` có được phép dẫn đến một system library cùng tên nhưng khác version không?

## Kết luận

**`ClassLoader.findLibrary` chịu trách nhiệm trả lời “native library có tên logic này nằm ở absolute path nào đối với lớp do loader này định nghĩa?”**. JVM chịu trách nhiệm **thực hiện load, liên kết và quản lý native library**. Hook trả **absolute path** khi biết file hợp lệ, trả **`null`** khi muốn dùng cơ chế tra mặc định, và không thay thế `findClass`, `findResource`, `System.load` hay `System.loadLibrary`.

Trong ứng dụng plugin, thành công không chỉ là “tìm được file”: còn phải đúng caller loader, tránh trùng JNI giữa loader, đúng OS/ABI, quản lý native file/threads/global refs và có chính sách reload/shutdown. Đây là ranh giới giữa **class loader Java** và **native linker/OS** mà người xây custom loader cần nắm vững.

**Tài liệu chính thức (Java SE 17 / JNI):**

- [`ClassLoader.findLibrary` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#findLibrary(java.lang.String)).
- [`System.load`, `System.loadLibrary` và `System.mapLibraryName` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html).
- [JNI Specification — Design Overview](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/design.html).
- [JNI Specification — Invocation API, Library and Version Management](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/invocation.html).


</details>

- [Quay lại đầu trang](#back-to-top)
