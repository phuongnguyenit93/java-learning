<a id="back-to-top"></a>

# Loader ngữ cảnh

## Menu
- [Thread context class loader là gì](#context-class-loader-001)
- [Vì sao API nền tảng cần nhìn thấy cách triển khai của ứng dụng](#context-class-loader-002)
- [getContextClassLoader và setContextClassLoader](#context-class-loader-003)
- [Lựa chọn loader tường minh thay vì phụ thuộc ngầm vào luồng](#context-class-loader-004)
- [Truyền và kế thừa context class loader khi tạo luồng](#context-class-loader-005)
- [Thread pool và ngữ cảnh có thể khác luồng gửi công việc](#context-class-loader-006)
- [Đổi context class loader trong phạm vi hẹp và khôi phục bằng finally](#context-class-loader-007)
- [Rò rỉ bộ nhớ khi luồng sống lâu giữ loader của plugin](#context-class-loader-008)
- [ServiceLoader và các API dùng loader ngữ cảnh](#context-class-loader-009)

## <a id="context-class-loader-001">Thread context class loader là gì</a>

<details>
<summary>Xem chi tiết</summary>

# Thread Context ClassLoader (TCCL) là gì?

## 1. Khái niệm và mục đích

**Thread Context ClassLoader (TCCL)** là một tham chiếu `ClassLoader` **được gắn với từng đối tượng `Thread`**, để code đang chạy trong thread đó có thể hỏi: “Trong ngữ cảnh công việc hiện tại, nên dùng class loader nào để tìm class và resource động?”

API trọng tâm:

```java
ClassLoader loader =
        Thread.currentThread().getContextClassLoader();

Thread.currentThread().setContextClassLoader(loader);
```

TCCL **không phải** một loại `ClassLoader` mới, cũng không phải trường static chung cho mọi thread. Nó là **một tham chiếu có thể đọc/thay đổi trên mỗi Thread**. ClassLoader được gắn vào TCCL vẫn có chính sách nạp của chính nó, chẳng hạn parent-first hoặc child-first tùy implementation.

TCCL ra đời để giải quyết một tình huống phổ biến trong ứng dụng lớn:

- **Framework hoặc thư viện lõi** được định nghĩa bởi loader cha (hoặc loader nền tảng).
- **Plugin, JDBC driver, service provider, thư viện của ứng dụng con** nằm trong loader con.
- Theo **ủy quyền parent-first**, loader cha không thể tự động nhìn xuống toàn bộ class mà loader con định nghĩa.
- Framework cần tìm implementation thuộc **ngữ cảnh ứng dụng/thread gọi nó**, thay vì chỉ tìm trong classpath của chính framework.

TCCL tạo ra một **đường truyền ngữ cảnh từ code phía ứng dụng đến framework**, giúp framework chọn một loader thích hợp khi cần tìm plugin.

Ví dụ khái niệm:

```text
Application / plugin code
   └── đang chạy trên Thread T
       └── TCCL = PluginLoader
                  │
                  ▼
Framework code / ServiceLoader
   └── đọc Thread.currentThread().getContextClassLoader()
       └── dùng PluginLoader tìm provider, resource
           └── PluginLoader có thể thấy các class riêng của plugin
```

**Điểm phải nhớ:** TCCL **chỉ có tác dụng khi API/framework chủ động sử dụng nó**. Java không tự động thay defining loader của mọi class, hay chuyển mọi lời gọi `Class.forName(...)` sang TCCL chỉ vì thread đã có TCCL mới.

## 2. Phân biệt ba loader thường bị nhầm

Cùng trong một dòng code, ba nguồn `ClassLoader` có thể khác nhau:

```java
ClassLoader defining =
        SomeClass.class.getClassLoader();

ClassLoader context =
        Thread.currentThread().getContextClassLoader();

ClassLoader system =
        ClassLoader.getSystemClassLoader();
```

Đây là **trích đoạn API**; `SomeClass` phải là class cụ thể trong chương trình, không phải một định danh có sẵn trong JDK.

| Khái niệm | Đến từ đâu? | Vai trò |
|---|---|---|
| Defining loader của `SomeClass` | `SomeClass.class.getClassLoader()` | Đã **định nghĩa runtime class** SomeClass |
| TCCL của thread hiện tại | `Thread.currentThread().getContextClassLoader()` | Loader mà **ngữ cảnh thực thi hiện tại muốn API chọn**, khi API hỗ trợ |
| System class loader | `ClassLoader.getSystemClassLoader()` | Loader hệ thống của ứng dụng theo cấu hình runtime |

Điểm tinh tế:

1. TCCL **không cần bằng** defining loader của class đang thực thi.
2. Thay TCCL **không đổi** defining loader của các class đã nạp; class identity vẫn phụ thuộc binary name và defining loader.
3. TCCL có thể trỏ tới custom loader, `URLClassLoader`, một loader ứng dụng, hoặc `null`. Theo hợp đồng `Thread`, giá trị `null` biểu diễn **system class loader (hoặc bootstrap nếu không có system loader)** cho ngữ cảnh thread. **Đừng đánh đồng** giá trị `null` ở TCCL với ý nghĩa `loader=null` trong **`Class.forName(name, initialize, loader)`**, vì overload đó chỉ định **bootstrap** khi `loader=null`.
4. System class loader không bắt buộc có cùng phạm vi nhìn thấy class với loader plugin, đặc biệt trong container có nhiều ứng dụng con.

Ví dụ một thư viện `engine.Framework` do loader cha định nghĩa có thể gọi:

```java
// Trong code framework:
ClassLoader appLoader =
        Thread.currentThread().getContextClassLoader();

Class<?> implementation =
        Class.forName("my.plugin.Provider", false, appLoader);
```

Đây là trích đoạn, cần khai báo `throws ClassNotFoundException` hoặc bắt exception. Framework **chủ động truyền TCCL** vào overload ba tham số. Nếu chỉ gọi `Class.forName("my.plugin.Provider")`, overload một tham số sẽ dùng loader của class **chứa lời gọi** (`engine.Framework`), không tự dùng TCCL.

## 3. Vì sao mô hình ủy quyền bình thường chưa đủ?

Với parent-first delegation, một loader con thường thử:

```text
findLoadedClass
     ↓ nếu chưa có
parent.loadClass
     ↓ nếu parent không tìm thấy
findClass của chính loader con
```

Quan hệ parent-child chủ yếu theo hướng **con ủy quyền lên cha**. Đó là thiết kế hữu ích để class API chung có danh tính nhất quán. Nhưng parent **không tự động ủy quyền ngược xuống mọi child**.

Ví dụ container chạy hai ứng dụng:

```text
                  Bootstrap / Platform
                         │
                 SharedFrameworkLoader
                   /             \
          ApplicationLoader-A   ApplicationLoader-B
             plugin.A             plugin.B
```

Nếu `SharedFrameworkLoader` có class `FrameworkRegistry` và class đó tự tìm `plugin.A` bằng loader của chính nó, có thể nhận `ClassNotFoundException`: loader cha **không thấy class chỉ nằm ở loader con A**.

Nếu một request của ứng dụng A được xử lý trên thread có TCCL = `ApplicationLoader-A`, `FrameworkRegistry` có thể đọc TCCL và yêu cầu A loader tìm provider thích hợp. Với request của B, TCCL có thể là `ApplicationLoader-B`.

Đây là mô hình **context-dependent discovery**. Nó hữu ích trong Java SPI, service provider, JDBC, logging/provider ecosystems, application server, test runner và một số hệ plugin.

**Nhưng TCCL không bảo đảm isolation hoặc bảo mật:** loader được chọn vẫn có quyền nhìn thấy những class/resource theo chính sách và quyền hiện có của nó. Việc lấy code từ plugin không tin cậy cần thêm kiểm soát nguồn bytecode, module, permission, API boundary và vòng đời loader.

## 4. Đọc và gán TCCL trong Thread

`Thread` cung cấp hai instance methods:

```java
public ClassLoader getContextClassLoader();
public void setContextClassLoader(ClassLoader cl);
```

Cách dùng:

```java
Thread thread = Thread.currentThread();
ClassLoader before = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(chosenLoader);
    // Gọi framework/service discovery cần loader của ngữ cảnh này.
} finally {
    thread.setContextClassLoader(before);
}
```

Đây là **mẫu code trích đoạn**: `chosenLoader` do chương trình cung cấp. Dùng `try/finally` rất quan trọng vì thread có thể được dùng lại trong thread pool. Nếu để TCCL plugin A còn trên một worker rồi worker xử lý request B, request B có thể vô tình tìm class/resource của A.

### Tính chất phạm vi

- **Theo Thread:** thay TCCL của thread hiện tại không tự đổi TCCL của tất cả thread khác.
- **Có thể đọc/ghi:** các thành phần cùng sử dụng một thread có thể đọc và thay đổi TCCL; nó không phải trường immutable.
- **Không tự “scope” khi thoát method:** Java không tự restore TCCL sau khi method return. Chính code/framework đổi TCCL phải phục hồi đúng ngữ cảnh.
- **Có thể kế thừa:** thread mới thường nhận TCCL ban đầu từ thread tạo nó, với các quy tắc của `Thread`.
- **Không tự truyền qua mọi cơ chế bất đồng bộ:** thread pool có worker từ trước, nên TCCL ở lúc submit task không tự trở thành TCCL của worker chạy task đó.

### Chú ý về `null`

Tài liệu `Thread.getContextClassLoader()` cho phép trả `null`, có nghĩa ngữ cảnh dùng system class loader (hoặc bootstrap khi không có system). Tài liệu `setContextClassLoader(null)` cũng dùng quy ước tương ứng.

Tuy nhiên, nếu **mã của bạn** lấy TCCL và truyền thẳng vào API khác, cần đọc hợp đồng của **API nhận tham số loader**. Ví dụ `Class.forName(name, false, null)` sử dụng bootstrap loader. Vì vậy một framework muốn có fallback về system loader khi TCCL là `null` có thể cần triển khai rõ:

```java
// Trích đoạn: chủ động định nghĩa fallback phù hợp yêu cầu ứng dụng.
ClassLoader cl = Thread.currentThread().getContextClassLoader();
if (cl == null) {
    cl = ClassLoader.getSystemClassLoader();
}
Class<?> type = Class.forName("demo.Provider", false, cl);
```

Đừng dùng một giả định “`null` ở đâu cũng cùng nghĩa” khi ghép nhiều API.

## 5. Ví dụ chạy được 1: TCCL được kế thừa khi tạo thread

Tạo file `ThreadContextDemo.java`, chạy bằng Java 17+:

```java
public class ThreadContextDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread parent = Thread.currentThread();
        ClassLoader original = parent.getContextClassLoader();

        ClassLoader marker = new ClassLoader(original) {
        };

        parent.setContextClassLoader(marker);
        System.out.println("main uses marker: "
                + (parent.getContextClassLoader() == marker));

        // Tạo thread con khi TCCL của main đang là marker.
        Thread child = new Thread(() -> {
            ClassLoader inherited =
                    Thread.currentThread().getContextClassLoader();
            System.out.println("child inherited marker: "
                    + (inherited == marker));
        }, "child");

        // Phục hồi TCCL của main TRƯỚC KHI start thread con.
        parent.setContextClassLoader(original);
        System.out.println("main restored: "
                + (parent.getContextClassLoader() == original));

        child.start();
        child.join();
    }
}
```

Biên dịch và chạy:

```bash
javac ThreadContextDemo.java
java ThreadContextDemo
```

Stdout:

```text
main uses marker: true
main restored: true
child inherited marker: true
```

**Phân tích:**

1. `marker` là một object `ClassLoader` mới có `original` làm parent. Chưa có class plugin nào được định nghĩa; object loader chỉ dùng làm dấu mốc.
2. `main` đổi TCCL sang `marker`.
3. Khi thực hiện `new Thread(...)`, thread con **nhận giá trị TCCL ban đầu** từ thread tạo theo quy tắc kế thừa mặc định. **Thời điểm tạo Thread** quan trọng; không cần đợi đến `start()` để chụp lại TCCL.
4. `main` trở về `original` trước `child.start()`. Thread con vẫn giữ tham chiếu `marker` mà nó nhận trước đó.
5. `child.join()` giúp thứ tự in stdout ổn định trong ví dụ này.

Trong ứng dụng sản xuất, nếu việc tạo thread hoặc chạy code có thể ném exception, hãy bảo đảm restore TCCL trong `finally` chứ không chỉ đặt restore sau một chuỗi lệnh bình thường.

### Có áp dụng với virtual thread không?

JDK 21+ giới thiệu virtual threads ở trạng thái chuẩn. Các API xây dựng thread có tùy chọn liên quan thừa kế inheritable thread-local/context state; không nên kết luận tất cả loại thread, thread builder, executor hay framework đều sao chép TCCL theo cùng một cách bất kể cấu hình. Bài này chọn `new Thread(...)` để minh họa hành vi rõ ràng và chạy được trên **Java 17**.

## 6. ServiceLoader dùng TCCL để tìm provider
Khi service API được định nghĩa ở host nhưng provider chỉ có trong plugin loader, `ServiceLoader.load(Greeting.class)` có thể dùng **TCCL hiện tại** để tìm provider. Đây là điểm nối giữa mô hình parent–child và cơ chế SPI. Ví dụ sau là đoạn minh họa trong một method, giả định host đã có interface `Greeting`, classpath plugin đã có `META-INF/services` tương ứng và biến `pluginLoader`:

```java
Thread current = Thread.currentThread();
ClassLoader before = current.getContextClassLoader();
try {
    current.setContextClassLoader(pluginLoader);
    for (Greeting greeting : ServiceLoader.load(Greeting.class)) {
        System.out.println(greeting.message());
    }
} finally {
    current.setContextClassLoader(before);
}
```

Provider phải triển khai **đúng runtime interface** mà host dùng; hai bản interface cùng binary name nhưng do loader khác nhau định nghĩa có thể không tương thích. File descriptor và provider code phải nằm trong vùng mà `pluginLoader` nhìn thấy. `ServiceLoader.load(Greeting.class, pluginLoader)` là lựa chọn tường minh khi không cần thay TCCL. Việc khám phá/khởi tạo provider có thể diễn ra lười khi duyệt, và provider khai báo sai có thể ném `ServiceConfigurationError`; không coi mọi lỗi là “không có provider”. Bài chuyên sâu ở mục cuối chương giải thích đầy đủ cấu trúc project, lệnh biên dịch, output, cache và JPMS.
## 7. ServiceLoader và TCCL: các overload không giống nhau

`ServiceLoader` có nhiều factory methods với phạm vi tìm khác nhau:

| API | Loader/ngữ cảnh tìm |
|---|---|
| `ServiceLoader.load(Service.class)` | **TCCL của thread hiện tại** |
| `ServiceLoader.load(Service.class, loader)` | **ClassLoader truyền tường minh** |
| `ServiceLoader.load(ModuleLayer layer, Service.class)` | Tìm provider trong module layer và các layer cha theo contract |
| `ServiceLoader.loadInstalled(Service.class)` | Provider được cài đặt qua platform loader và tổ tiên phù hợp, không phải loader plugin tùy ý |

TCCL không được suy rộng sang tất cả Java SPI. Mỗi thư viện/provider discovery API phải được kiểm tra riêng: API nào thật sự lấy `Thread.currentThread().getContextClassLoader()`, API nào dùng loader của caller, API nào dùng cấu hình service module/layer?

**Lưu ý về lazy loading:** `ServiceLoader.load(...)` tạo một service loader, nhưng provider có thể chỉ được tìm/nạp/khởi tạo khi iterator, `findFirst()` hoặc stream tiêu thụ chúng. Với một `ServiceLoader` đã tạo, thay TCCL về sau **không có nghĩa** đối tượng ServiceLoader cũ tự động chuyển sang loader mới. Hãy tạo service loader mới theo ngữ cảnh, hoặc dùng overload truyền loader tường minh nếu muốn tránh phụ thuộc TCCL.

Nếu tìm được service configuration nhưng provider class không thể nạp hoặc không implement đúng API runtime class, `ServiceLoader` có thể ném **`ServiceConfigurationError`** thay vì chỉ trả collection trống. Cần phân biệt “không có provider” với “provider được khai báo nhưng không hợp lệ”.

## 8. Quan hệ với ClassLoader.loadClass và Class.forName

Bảng đối chiếu:

| Biểu thức | Chọn loader thế nào? | Có tự yêu cầu khởi tạo class đích? |
|---|---|---|
| `Thread.currentThread().getContextClassLoader()` | Đọc ngữ cảnh của thread | Không nạp class đích |
| `loader.loadClass("p.X")` | Dùng chính `loader` gọi method | Không tự yêu cầu chạy `X.<clinit>` |
| `Class.forName("p.X")` | Defining loader của class **chứa lời gọi** | Có, nếu X chưa khởi tạo |
| `Class.forName("p.X", false, loader)` | Loader truyền tường minh | Không yêu cầu khởi tạo |
| `Class.forName("p.X", true, loader)` | Loader truyền tường minh | Có, nếu X chưa khởi tạo |
| `ServiceLoader.load(S.class)` | TCCL hiện tại | Provider loading/instantiation diễn ra theo việc duyệt service loader |

Đây là khác biệt lớn giữa **class-loading context** và **class initialization**. TCCL không chủ động chạy `<clinit>`; nó có thể **chọn loader** sẽ dùng nếu một API cần tìm class. Lệnh sau mới quyết định việc nạp/link/init theo hợp đồng của nó.

Đặc biệt, `Class.forName(name, boolean, loader)` khi `loader=null` chọn **bootstrap loader**, trong khi `Thread.getContextClassLoader()==null` mang quy ước **system-or-bootstrap context** của Thread API. Luôn xem hợp đồng từng API thay vì truyền `null` xuyên suốt mà không kiểm tra.

## 9. TCCL khi tạo thread và gửi việc vào executor
Với `new Thread(task)` thông thường, TCCL ban đầu của thread con được kế thừa **khi tạo đối tượng Thread**, không đợi `start()`. Đổi TCCL của thread cha sau khi đã tạo child không tự đổi giá trị của child (ví dụ độc lập ở phần 5).

Ngược lại, `executor.submit(task)` thường chạy công việc trên worker **đã tồn tại**; API không hứa chép TCCL của submitter sang worker ở mỗi lần submit. Nếu API bên trong task cần plugin loader, truyền loader như một dependency hoặc bọc `Runnable`/`Callable` để worker lưu previous → set desired → thực hiện → restore trong `finally`. Chính thread chạy task mới là nơi cần đọc và đổi TCCL.

Điều này áp dụng cả khi callback hoặc `CompletableFuture` chuyển execution sang một thread khác. Với virtual threads (Java 21+), cần xét cấu hình kế thừa của thread builder/factory và không nhầm TCCL của virtual thread với carrier thread. Bài “Truyền và kế thừa...” và “Thread pool...” phân tích thời điểm chụp ngữ cảnh, ví dụ pool và wrapper chạy được.
## 10. Vòng đời loader và rủi ro giữ plugin lâu hơn dự kiến
`Thread.contextClassLoader` là **tham chiếu mạnh**. Nếu worker sống lâu giữ TCCL của loader plugin đã ngừng hoạt động, loader ấy vẫn reachable; các class do nó định nghĩa chưa đủ điều kiện unload. Đây là một **đường giữ tham chiếu**, có thể xảy ra đồng thời với cache, `ThreadLocal`, callback hay task queue.

```text
host worker còn sống
  └── TCCL -> loader plugin cũ -> class / resource plugin
```

Cần khôi phục TCCL cũ trong `finally`, ngừng/hủy công việc của plugin và dọn các reference do ứng dụng sở hữu. `URLClassLoader.close()` chỉ đóng tài nguyên liên quan, không ép GC unload class. Đừng reset bừa về `null` hoặc system loader: hãy restore đúng giá trị previous. Bài “Rò rỉ bộ nhớ...” chỉ ra quy trình unload, kiểm tra GC roots và các giới hạn của `System.gc()`.
## 11. An toàn và tính đúng đắn khi dùng TCCL

### 11.1. TCCL là lựa chọn lookup, không phải sandbox

`setContextClassLoader(pluginLoader)` không làm class của plugin tự an toàn hơn, không chặn reflection và không cấp quyền module/package. Nó chỉ làm một số API biết **nên hỏi loader nào**.

Không dùng TCCL làm bằng chứng đáng tin rằng một class được nạp từ nguồn hợp pháp. Provider class được trả về vẫn cần kiểm tra:

- Có triển khai đúng interface/API dùng chung và đúng runtime type không?
- Có được nạp từ JAR/nguồn mong muốn không?
- Có bị parent-first chọn một class khác cùng tên trước không?
- Có tương thích với module access và Java runtime version không?
- Có kiểm soát được constructor/provider initialization side effects không?

### 11.2. Trùng tên API giữa các loader

Giả sử `HostApi` được định nghĩa ở parent, nhưng một plugin loader child-first định nghĩa một bản `HostApi` khác cùng binary name. Provider implement bản API ở child có thể **không assignable** sang `HostApi.class` của host, dù hai class cùng tên trên màn hình. `ServiceLoader` có thể báo provider không phải subtype của service, hoặc code cast thất bại.

Thiết kế thông thường giữ **API giao tiếp chung ở parent**, plugin implementation ở child; chỉ override child-first cho các namespace plugin/private thật sự cần isolation.

### 11.3. Lỗi và cách báo cáo

| Triệu chứng | Nguyên nhân khả dĩ | Cách kiểm tra |
|---|---|---|
| `ServiceLoader` không thấy provider | TCCL chưa trỏ đúng plugin loader, thiếu `META-INF/services` hoặc module `provides` | In TCCL, kiểm tra resource và module layer |
| `ClassNotFoundException` từ `Class.forName(name)` | API một tham số dùng loader của caller, không dùng TCCL | Chuyển sang overload truyền loader tường minh nếu phù hợp |
| `ServiceConfigurationError` | Provider class/constructor/service config sai, provider không assignable | Xem cause, provider binary name và class identity |
| `ClassCastException` | Host/plugin định nghĩa trùng API cùng tên qua loaders khác nhau | So `Class<?>` và `getClassLoader()` hai phía |
| Provider sai sau request trước | TCCL bị rò rỉ trên thread pool hoặc có service loader/cache cũ | Restore TCCL, làm rõ vòng đời cache |
| Không giải phóng được plugin khi redeploy | Thread sống lâu giữ TCCL plugin loader hoặc reference khác | Thread dumps, reference paths, close/unregister |
| `SecurityException` khi get/set TCCL | Môi trường legacy có SecurityManager kiểm tra quyền | Xem policy/runtime, không mặc định mọi JVM đều có manager hoạt động |

Đừng bắt `ServiceConfigurationError` rồi trả “không có provider” mà bỏ qua lỗi. Khi provider được khai báo nhưng không thể instantiate/load, đây là **cấu hình hỏng**, khác với không có provider được cung cấp.

## 12. Khi nào nên dùng TCCL, khi nào nên truyền ClassLoader tường minh?

| Thiết kế | Lựa chọn phù hợp |
|---|---|
| Framework chung gọi SPI trong ngữ cảnh request/application hiện tại | TCCL nếu đó là contract được framework/API thiết kế |
| Library tự quản lý plugin và biết chính xác loader | Truyền `ClassLoader` tường minh thường rõ hơn |
| Code có type biết lúc compile, không cần discovery động | Dùng trực tiếp `SomeType.class` hoặc new/type-safe API |
| Task qua executor có context riêng | Wrapper set/restore TCCL ở worker hoặc truyền context tường minh |
| Cần plugin isolation và unload/redeploy | Kiểm soát loader lifecycle, scope TCCL, tránh strong references |
| Ứng dụng JPMS với named modules | Xem thêm `ModuleLayer` và `ServiceLoader.load(layer, service)` |
| Không muốn thay đổi global state theo Thread | Ưu tiên overload nhận loader trực tiếp nếu API cung cấp |

TCCL đặc biệt hữu ích khi một API cấp thấp không nhận tham số loader nhưng cần khám phá implementation theo ứng dụng đang được xử lý. Nhưng vì là trạng thái **ambient** trên thread, nó làm hành vi khó đoán nếu context bị thay đổi ở nơi khác. Khi đã biết loader đúng và API có overload tường minh, truyền thẳng loader thường dễ test và dễ bảo trì hơn.

## 13. Phân biệt TCCL và các khái niệm liên quan

### 13.1. TCCL khác ThreadLocal

TCCL là một thuộc tính chuyên biệt của `Thread` và có API `getContextClassLoader`/`setContextClassLoader`. `ThreadLocal<T>` là cơ chế riêng cho dữ liệu thread-local bất kỳ. TCCL có một số hành vi kế thừa tương ứng với inheritable thread state, nhưng **không nên coi việc đặt TCCL giống hệt tự đặt một ThreadLocal bất kỳ**.

Hãy xem docs của `Thread` và `InheritableThreadLocal` khi thiết kế lan truyền context giữa platform threads, virtual threads hoặc executor.

### 13.2. TCCL khác defining loader của caller

TCCL phục vụ lựa chọn class/resource lookup theo ngữ cảnh thread. Defining loader của caller được cố định khi runtime class được định nghĩa. Đổi TCCL không đổi kết quả `Host.class.getClassLoader()` hay cho phép cast hai runtime classes trùng tên do loaders khác nhau định nghĩa.

### 13.3. TCCL khác module layer

Trong JPMS, module graph, readability, exports/opens và service declarations quyết định một phần khả năng tìm/truy cập provider. Một loader có thể định nghĩa nhiều modules. Chỉ đặt TCCL không bảo đảm service thuộc một module layer độc lập sẽ được tìm trong mọi trường hợp. Nếu kiến trúc tạo module layers riêng, hãy xét overload `ServiceLoader.load(layer, service)` và contract module `uses`/`provides`.

### 13.4. TCCL không tự khởi tạo lớp

Đọc, gán hoặc kế thừa TCCL **không chạy static initializer** của provider. Khi API thực sự nạp, resolve, instantiate hoặc gọi static method, Java mới áp dụng các điều kiện loading/linking/initialization theo đặc tả. Vì vậy nếu thấy `<clinit>` chạy sau `ServiceLoader.findFirst()`, nguyên nhân là **khám phá và instantiate provider**, không phải bản thân lời gọi `setContextClassLoader`.

## 14. Quy trình debug ngắn khi plugin không được tìm thấy
1. Xác định **thread thực sự thực thi** lời gọi tìm provider (đặc biệt khi qua executor).
2. Ghi nhận TCCL ngay tại nơi lookup, cùng defining loader của service interface.
3. Xác minh API sử dụng **TCCL**, loader do caller xác định, tham số loader tường minh hay `ModuleLayer`. `Class.forName(String)` không tự đọc TCCL.
4. Kiểm tra provider descriptor `META-INF/services/...` hoặc `provides`, đường dẫn JAR, shared type identity và chính sách parent delegation.
5. Phân biệt provider không tồn tại với `ServiceConfigurationError`, `ClassNotFoundException`, lỗi module access hoặc cast thất bại.
6. Kiểm tra `finally`, cache/service loader cũ và references còn lại khi redeploy.

Thử `ServiceLoader.load(service, pluginLoader)` để phân biệt lỗi chọn TCCL với lỗi visibility thực tế của plugin loader.
## 15. Câu hỏi tự kiểm tra

1. **TCCL là một subclass `ClassLoader` mới do JVM tạo cho mỗi thread?** Không; đó là tham chiếu `ClassLoader` trên `Thread`.
2. **`Thread.currentThread().setContextClassLoader(pluginLoader)` có đổi defining loader của `Host.class` không?** Không.
3. **Framework class do parent loader định nghĩa cần tìm provider ở child loader; TCCL hữu ích chỗ nào?** Framework có thể chủ động hỏi loader từ ngữ cảnh thread để tìm provider ở child.
4. **`ServiceLoader.load(MyService.class)` chọn loader theo cách nào?** Dùng TCCL của thread hiện tại.
5. **`Class.forName("p.Provider")` có tự dùng TCCL mới set không?** Không; dùng defining loader của caller theo contract overload một tham số.
6. **`Class.forName("p.Provider", false, tccl)` có tự chạy `Provider.<clinit>` không?** Không yêu cầu; `initialize=false`.
7. **Thread con nhận TCCL từ thời điểm `new Thread` hay `start`?** Với constructor mặc định, nó được kế thừa khi thread được tạo, không đợi đến `start`.
8. **Có thể đổi TCCL ở thread main rồi submit task vào thread pool và chắc worker dùng TCCL mới không?** Không; worker có thể đã tồn tại và có context riêng.
9. **Tại sao phải restore TCCL trong `finally`?** Tránh nhiễm ngữ cảnh giữa các task và giữ tham chiếu làm rò rỉ plugin loader.
10. **Nếu `getContextClassLoader() == null`, có thể truyền nguyên `null` vào `Class.forName(name,false,loader)` mà vẫn chắc dùng system loader không?** Không; `forName` dùng bootstrap khi loader null.
11. **Hai plugin loaders định nghĩa hai class API cùng binary name thì ServiceLoader/cast có thể gặp gì?** Type identity khác nhau, provider có thể không assignable, `ServiceConfigurationError` hoặc `ClassCastException`.
12. **Khi đã biết plugin loader và muốn tránh trạng thái ambient, nên dùng API gì?** Overload truyền loader tường minh nếu API có, ví dụ `ServiceLoader.load(service, pluginLoader)`.

## Tài liệu tham khảo chính thức

- [Java SE 21 — Thread.getContextClassLoader / setContextClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): ý nghĩa context ClassLoader, kế thừa từ thread tạo, cách lấy/đặt và `null`.
- [Java SE 21 — ServiceLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html): overload `load(service)` dùng TCCL, service-provider configuration, lazy discovery và `ServiceConfigurationError`.
- [Java SE 21 — Class.forName](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): phân biệt defining loader của caller với overload truyền explicit loader.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): parent-first delegation, `loadClass`, `findClass` và identity.
- [JLS — `12.2 Loading, `12.3 Linking, `12.4 Initialization](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html): quan hệ nạp, liên kết và khởi tạo lớp.

**Tóm lại:** TCCL là **loader do ngữ cảnh Thread chỉ định** để API/framework có thể tìm class hoặc provider phù hợp với ứng dụng đang xử lý, đặc biệt khi implementation nằm trong loader con. Nó **không thay defining loader**, **không tự đổi chính sách nạp** và **không tự khởi tạo class**. Khi dùng, phải phân biệt hợp đồng từng API, truyền loader rõ ràng khi cần và luôn khôi phục TCCL sau một scope để tránh sai provider và rò rỉ ClassLoader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-class-loader-002">Vì sao API nền tảng cần nhìn thấy cách triển khai của ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao API nền tảng cần nhìn thấy cách triển khai của ứng dụng?

Một framework hoặc API nền tảng thường **định nghĩa hợp đồng (interface/SPI)**, trong khi ứng dụng, plugin hoặc nhà cung cấp thư viện **cung cấp cách triển khai (provider)**. Nếu thư viện nằm trong loader cha nhưng provider nằm ở loader con, cơ chế **parent-first class loading** thông thường không tự cho loader cha nhìn xuống class của loader con.

Ví dụ: thư viện định nghĩa `Greeting`, ứng dụng triển khai `HelloGreeting`. Thư viện cần khám phá và gọi implementation mà bản thân nó không biết lúc biên dịch. Với cơ chế chỉ hỏi parent, lớp ở cha có thể tìm các class của chính nó, nhưng **không tự động tìm được** những provider chỉ nằm trong loader ứng dụng/plugin.

Đó là một lý do tồn tại của **thread context class loader (TCCL)**: mã thư viện/khung chạy trên một thread có thể chủ động hỏi “loader ứng dụng muốn dùng để khám phá implementation trong tác vụ này là loader nào?”. Những API như `ServiceLoader.load(Service.class)` có hợp đồng sử dụng TCCL để giải quyết nhu cầu đó.

Bài thuộc nhóm **Loader ngữ cảnh** của Java core – classloader. Trọng tâm là vì sao API nền tảng cần nhìn thấy implementation của ứng dụng, đường đi của lời gọi, điểm khác nhau giữa **defining loader**, **parent loader** và **TCCL**, ví dụ Java 17+ chạy được, giới hạn trong môi trường module/plugin, cùng các lỗi thường gặp.

## 1. Bài toán thiết kế: API ở “trên”, implementation ở “dưới”

Một hệ thống mở rộng được thường tách hai vai trò:

- **API/service contract:** interface, abstract class hoặc bộ quy tắc chung mà framework công bố, ví dụ `java.sql.Driver`, `java.util.spi.*` hoặc một interface do host ứng dụng định nghĩa.
- **Provider/implementation:** class cụ thể triển khai hợp đồng ấy, ví dụ driver của hãng cơ sở dữ liệu, provider mã hóa, serializer, logging implementation, plugin.
- **Discovery/factory:** thành phần cần tìm hoặc khởi tạo provider theo cấu hình/module metadata mà không phụ thuộc cứng vào tên class cụ thể ngay lúc biên dịch API.

Cây delegation điển hình:

```text
                     [Bootstrap loader]
                              ^
                              |
                       [Platform loader]
                              ^
                              |
                  [Application/System loader]
                              ^
                              |
                       [Plugin loader]
                       plugin.HelloGreeting
```

Mũi tên trên biểu thị **child có thể nhờ parent tìm class**, không có nghĩa parent tự động có thể truy cập mọi class do child định nghĩa.

Nếu `host.LibraryDiscovery` do system loader định nghĩa, còn `plugin.HelloGreeting` chỉ nằm trong plugin loader con, lời gọi:

```java
Class.forName("plugin.HelloGreeting")
```

nếu viết *ngay trong `host.LibraryDiscovery`* sẽ mặc định bắt đầu từ **defining loader của `LibraryDiscovery`**. Nó không tự suy luận rằng một plugin đang gọi vào method ấy và cần đổi loader theo call stack. Điều này có thể khiến lớp provider không tìm được dù plugin loader thực sự có bytecode của nó.

**Mâu thuẫn cần giải quyết:** mã nền tảng/host cần khám phá implementation do ứng dụng cung cấp, nhưng parent-first loader không có cơ chế tổng quát “hỏi ngược mọi loader con”.

## 2. TCCL giải quyết bài toán theo cách nào?

Mỗi `Thread` trong Java có một **context class loader** có thể đọc hoặc thay đổi:

```java
ClassLoader current = Thread.currentThread().getContextClassLoader();
Thread.currentThread().setContextClassLoader(someLoader);
```

Đây là **đoạn minh họa**, giả định `someLoader` đã tồn tại. TCCL là **trạng thái của thread**, không phải thuộc tính toàn cục cho JVM và không phải một cạnh mới trong `ClassLoader.getParent()`.

Đường gọi của framework:

```text
Host/framework định nghĩa interface Service
               |
               | application khởi động request / plugin
               v
Thread context loader = PluginLoader
               |
               | Host API gọi ServiceLoader.load(Service.class)
               v
ServiceLoader hỏi TCCL để tìm provider metadata
               |
               v
PluginLoader tìm META-INF/services/... và provider class
               |
               v
Provider class do PluginLoader định nghĩa
               |
               +-- provider implements Service do parent định nghĩa
               |
               v
Framework nhận Service và gọi method của interface chung
```

**Điểm cốt lõi:** TCCL cho phép *code nằm ở loader cha chủ động yêu cầu loader con tìm implementation*. Nó không làm mọi code trong loader cha tự nhiên nhìn thấy class của loader con, không sửa parent chain, không chuyển defining loader của những class đã nạp.

Một điều đặc biệt cần biết từ Java 17 API:

```java
ServiceLoader<Greeting> services =
        ServiceLoader.load(Greeting.class);
```

Overload này tương đương về lựa chọn loader với:

```java
ServiceLoader<Greeting> services =
        ServiceLoader.load(
                Greeting.class,
                Thread.currentThread().getContextClassLoader());
```

Đây là **đoạn minh họa**, giả định interface `Greeting` đã khai báo. Cơ chế lựa chọn TCCL là hợp đồng cụ thể của `ServiceLoader.load(Class)`, **không phải quy tắc tự động của mọi phương thức Java**.

## 3. Vì sao parent loader không chủ động tìm xuống child?

Trong cơ chế mặc định `ClassLoader.loadClass`, loader lần lượt kiểm tra class đã load, hỏi parent, rồi mới thử `findClass` của chính mình. Parent không có danh sách ổn định tất cả child loader vì:

1. Ứng dụng có thể tạo nhiều plugin loaders một cách động.
2. Hai plugin có thể định nghĩa **cùng tên class** nhưng muốn giữ namespace/version độc lập.
3. Framework có thể chạy nhiều tenant hoặc ứng dụng trên cùng JVM với context khác nhau.
4. Parent có thể được nạp sớm hơn nhiều so với các plugin và không biết các implementation sẽ xuất hiện sau đó.
5. Nếu parent tự tra mọi child mà không có hợp đồng, nó có thể chọn nhầm provider hoặc lộ ranh giới class không mong muốn.

Ví dụ:

```text
                   [App/system loader]
                     /           \
                    v             v
              [PluginLoader A] [PluginLoader B]
               demo.Provider   demo.Provider
```

Hai `demo.Provider` có thể là hai class **khác nhau**, vì danh tính runtime của class là **binary name + defining loader**. Chọn provider B cho một request của A sẽ sai, dù class có cùng tên.

TCCL cho phép chính **ngữ cảnh thực thi** chọn một loader cụ thể khi API discovery có chủ đích sử dụng nó. Cơ chế này hữu ích cho request-scoped, plugin-scoped hoặc container-scoped discovery.

Tuy nhiên, **TCCL không phải sandbox bảo mật**: nó là một tham chiếu tới loader được chọn. API dùng loader đó vẫn phải tuân theo Java/module access và ranh giới trust.

## 4. Ví dụ chạy được Java 17+: framework cha khám phá provider của plugin con

Ta xây dựng một ví dụ có thể biên dịch/chạy bằng JDK 17 trở lên. Gồm:

- `api.Greeting`: interface chung do **system loader** định nghĩa.
- `lib.Discovery`: code thư viện **chỉ ở system loader**, khám phá provider qua `ServiceLoader`.
- `plugin.HelloGreeting`: implementation **chỉ được plugin loader định nghĩa**.
- `main.Launcher`: ứng dụng chủ động đặt TCCL = plugin loader cho khoảng thời gian discovery.
- `META-INF/services/api.Greeting`: file khai báo provider theo **Service Provider Interface (SPI)** trên classpath.

### 4.1 Cấu trúc thư mục

Tạo cây sau trong `tccl-demo`:

```text
tccl-demo/
├── src/
│   ├── api/Greeting.java
│   ├── lib/Discovery.java
│   └── main/Launcher.java
├── plugin-src/
│   └── plugin/HelloGreeting.java
├── host-out/                           (output của host)
└── plugin-out/
    └── META-INF/
        └── services/
            └── api.Greeting           (file text chứa tên provider)
```

**Tạo đủ thư mục trước** khi biên dịch. File `plugin-out/META-INF/services/api.Greeting` phải có đúng tên binary của service interface; nội dung là tên binary của provider, không phải tên file `.class`.

### 4.2 Interface chung

File `src/api/Greeting.java`:

```java
package api;

public interface Greeting {
    String greet();
}
```

Interface này sẽ được nạp bởi **system loader**; cả library và plugin đều tham chiếu đúng một `Class<api.Greeting>`.

### 4.3 Code thư viện/framework

File `src/lib/Discovery.java`:

```java
package lib;

import api.Greeting;
import java.util.ServiceLoader;

public final class Discovery {
    private Discovery() { }

    public static void inspect() {
        long fromParent = ServiceLoader.load(
                Greeting.class, Greeting.class.getClassLoader())
                .stream().count();

        long fromContext = ServiceLoader.load(Greeting.class)
                .stream().count();

        System.out.println("parent provider count=" + fromParent);
        System.out.println("context provider count=" + fromContext);

        for (Greeting greeting : ServiceLoader.load(Greeting.class)) {
            System.out.println(greeting.greet());
        }
    }
}
```

Giải thích:

- `Greeting.class.getClassLoader()` là system loader trong chương trình này. Khi truyền rõ loader đó, discovery **không thấy** provider chỉ ở plugin child loader.
- `ServiceLoader.load(Greeting.class)` chọn **TCCL của thread hiện tại**.
- `stream().count()` đếm provider types phát hiện được, **không yêu cầu gọi `greet()` hoặc tạo instance để đếm**. Vòng `for` sau đó thực sự instantiate provider khi cần.

### 4.4 Provider trong plugin

File `plugin-src/plugin/HelloGreeting.java`:

```java
package plugin;

import api.Greeting;

public final class HelloGreeting implements Greeting {
    public String greet() {
        return "Hello from plugin";
    }
}
```

File văn bản `plugin-out/META-INF/services/api.Greeting`:

```text
plugin.HelloGreeting
```

Đường dẫn và nội dung phải khớp **chính xác**. Nếu file metadata thiếu, `ServiceLoader` sẽ không phát hiện provider từ classpath theo cơ chế SPI này. Nếu provider khai báo sai tên hoặc không implement interface chung, có thể gặp `ServiceConfigurationError` khi duyệt provider.

### 4.5 Chương trình khởi động và đặt TCCL

File `src/main/Launcher.java`:

```java
package main;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import lib.Discovery;

public final class Launcher {
    public static void main(String[] args) throws Exception {
        URL pluginUrl = Path.of("plugin-out").toUri().toURL();

        try (URLClassLoader pluginLoader = new URLClassLoader(
                new URL[]{pluginUrl}, ClassLoader.getSystemClassLoader())) {

            ClassLoader original =
                    Thread.currentThread().getContextClassLoader();

            System.out.println("provider visible to system=" +
                    isVisible("plugin.HelloGreeting",
                            ClassLoader.getSystemClassLoader()));

            Thread.currentThread().setContextClassLoader(pluginLoader);
            try {
                Discovery.inspect();
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    private static boolean isVisible(String name, ClassLoader loader) {
        try {
            loader.loadClass(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

Chú ý:

- `pluginLoader` có parent là system loader để **dùng chung interface `api.Greeting`**, còn `plugin.HelloGreeting` chỉ có trong `plugin-out`.
- `isVisible(...)` kiểm tra system loader có tự load provider không, trước khi TCCL được thay đổi.
- `setContextClassLoader` chỉ thay context của thread, **không tạo lại `Discovery` hoặc thay đổi defining loader** của class ấy.
- `finally` khôi phục loader cũ; `try-with-resources` đóng `URLClassLoader` sau khi tác vụ xong. Đóng loader không tự giải phóng class ngay lập tức nếu nơi khác vẫn giữ tham chiếu đến loader/class/provider.

### 4.6 Biên dịch và chạy

Từ **thư mục `tccl-demo`**:

```bash
javac --release 17 -d host-out src/api/Greeting.java src/lib/Discovery.java src/main/Launcher.java
javac --release 17 -cp host-out -d plugin-out plugin-src/plugin/HelloGreeting.java
java -cp host-out main.Launcher
```

Các lệnh `javac` và `java` trên hoạt động theo cùng cách ở Windows/Linux/macOS vì mỗi lần chỉ cần một root. Cần tạo `host-out`, `plugin-out` và file `META-INF/services/api.Greeting` như hướng dẫn trước đó.

Stdout đã được kiểm tra bằng biên dịch `javac --release 17` và chạy chương trình:

```text
provider visible to system=false
parent provider count=0
context provider count=1
Hello from plugin
```

Phân tích:

1. `provider visible to system=false`: host/system loader không tìm provider của child; đó là **chiều nhìn thấy bất đối xứng** trong parent delegation.
2. `parent provider count=0`: thư viện chỉ dùng loader của service interface thì không tìm thấy metadata/provider trong `plugin-out`.
3. `context provider count=1`: TCCL đã trỏ vào `pluginLoader`, nên `ServiceLoader` tìm được `META-INF/services/api.Greeting` rồi xác định provider.
4. `Hello from plugin`: provider được instantiate và gọi qua interface `api.Greeting`, một kiểu chung do parent định nghĩa.

Đây là phiên bản thu nhỏ của nhu cầu thật trong plugin engine, service-provider mechanism và ứng dụng có nhiều class loader.

**Nếu bỏ dòng `setContextClassLoader(pluginLoader)`**, trong cách chạy này `ServiceLoader.load(Greeting.class)` sẽ thường dùng system loader mặc định của main thread; kết quả `context provider count=0` và không có dòng chào plugin. Không cần giả định mọi thread/runtime có TCCL mặc định giống nhau để hiểu quy tắc: quan trọng là **loader nào đang là TCCL lúc gọi**.

## 5. Đặc tả quan trọng: không phải API nào cũng đọc TCCL

So sánh:

| API / thao tác | Loader được chọn | Có tự yêu cầu khởi tạo class đích? |
| --- | --- | --- |
| `ServiceLoader.load(Service.class)` | **TCCL hiện tại** | Discovery có thể lazy; instantiate provider khi duyệt/`get()` |
| `ServiceLoader.load(Service.class, loader)` | Loader được truyền rõ | Provider được instantiate theo nhu cầu của lần duyệt |
| `ServiceLoader.loadInstalled(Service.class)` | **Platform loader** | Chỉ tìm những provider “installed” phù hợp, không tự tìm provider application classpath |
| `ServiceLoader.load(layer, Service.class)` | **ModuleLayer** truyền vào | Discovery trong layer và parent layers theo quy tắc module |
| `Class.forName("pkg.Impl")` | **Defining loader của class gọi `forName`** | **Có**, nếu class đích chưa initialize |
| `Class.forName("pkg.Impl", false, loader)` | Loader được truyền | Không yêu cầu initialize class đích ngay |
| `someLoader.loadClass("pkg.Impl")` | Loader được gọi và delegation của nó | Không tự yêu cầu initialize |
| `Class.getResourceAsStream(...)` | Theo class và module/resource rules | Không tự chọn TCCL chỉ vì method được gọi |

Hai mẫu quan trọng:

```java
Class<?> type = Class.forName("plugin.HelloGreeting");
// Không tự dùng TCCL chỉ vì plugin context đang tồn tại.

ClassLoader context = Thread.currentThread().getContextClassLoader();
Class<?> selected = Class.forName(
        "plugin.HelloGreeting", true, context);
// Dùng loader được chọn rõ ràng.
```

Đây là đoạn minh họa, không phải file chạy độc lập. `Class.forName(String)` là API **caller-sensitive**: loader mặc định của nó là defining loader của **class chứa lệnh gọi**, không phải lớp gọi framework ở xa trong stack và không phải TCCL.

**TCCL chỉ có tác dụng với API chủ động đọc nó**. Không được kết luận “đổi TCCL sẽ sửa mọi `ClassNotFoundException`” hoặc “mọi API nền tảng đều truy vấn TCCL”.

## 6. SPI giúp tách hợp đồng khỏi implementation

Mục tiêu thiết kế là **dependency inversion** ở tầng triển khai:

```text
Framework/API -----------------> Service interface
                                      ^
                                      |
                              Provider implements
                                      |
                              Application/plugin
```

Mã nền tảng phụ thuộc vào **hợp đồng ổn định**. Ứng dụng cung cấp provider mà framework không cần biết tên implementation khi compile. **Discovery metadata** giúp runtime biết nhà cung cấp nào đã triển khai interface đó.

Một SPI classpath theo Java SE thông thường dùng:

```text
META-INF/services/<fully.qualified.ServiceInterface>
```

và nội dung chứa mỗi tên provider trên một dòng. Trong ví dụ, đúng file:

```text
META-INF/services/api.Greeting
```

chứa:

```text
plugin.HelloGreeting
```

Trên Java 17, `ServiceLoader` hỗ trợ cả **unnamed modules / classpath provider-configuration files** và **named modules có `provides`**. TCCL giúp chọn miền loader trong nhiều trường hợp, nhưng module system còn bổ sung quy tắc tìm provider theo layer/readability.

### ServiceLoader thực sự làm gì?

- Chọn loader khi tạo `ServiceLoader`, sau đó duyệt provider từ thông tin module/SPI.
- Thường **lazy**: việc gọi `ServiceLoader.load` chưa có nghĩa đã instantiate mọi implementation.
- Tìm provider classes và kiểm tra hợp đồng service phù hợp.
- Tạo instance provider khi iteration hoặc `Provider.get()` yêu cầu.
- Có cache cho các provider đã được nạp/thực thể hóa trong từng đối tượng `ServiceLoader`; `reload()` xóa cache của đối tượng.
- **Không thread-safe** nếu chia sẻ cùng đối tượng `ServiceLoader` cho nhiều thread mà không đồng bộ.

Sự khác biệt giữa “tìm thấy provider metadata”, “nạp class provider” và “khởi tạo instance provider” là quan trọng để hiểu thời điểm side effect xảy ra.

## 7. Thực tế: API nền tảng nào có nhu cầu nhìn thấy implementation của ứng dụng?

Một số mẫu thường gặp:

**Service Provider Interface (SPI):** `java.util.ServiceLoader` là ví dụ rõ nhất với hợp đồng `load(Class)` dùng TCCL. Service interface có thể do Java SE, một thư viện hoặc host ứng dụng định nghĩa; provider ở JAR/plugin ứng dụng.

**JDBC driver:** API JDBC và `DriverManager` là phần nền tảng; JDBC driver là thư viện của nhà cung cấp. Các driver hiện đại có thể được phát hiện thông qua cơ chế service-provider `java.sql.Driver`. Tuy nhiên, `DriverManager` còn áp dụng quy tắc class loader/access và cơ chế khởi tạo riêng; không được suy từ ví dụ SPI rằng **mọi code gọi JDBC luôn đơn giản dùng TCCL trực tiếp**. Khi debug phải kiểm tra tài liệu Java/JDBC và cấu hình runtime.

**JAXP XML parser/transformer:** API factory nền tảng có thể cần chọn provider do ứng dụng cung cấp; factory lookup có nhiều nguồn (system properties, service configuration, default provider, v.v.). Hãy xem hợp đồng cụ thể của từng factory, đừng mặc định tất cả lookup luôn dùng một loader.

**Application servers, dependency injection, plugin frameworks:** một container có thể chạy mã framework từ shared parent loader nhưng cần nạp component triển khai trong loader ứng dụng. Container thường quản lý TCCL ở ranh giới request/deployment/lifecycle để các API discovery có thể thấy đúng vùng ứng dụng.

**Logging bindings và serializers:** các thư viện chọn provider dựa trên service metadata hoặc cơ chế riêng. Với từng thư viện, phải kiểm tra nó **có dùng TCCL hay không**, vì không có quy tắc JVM bắt buộc mọi factory phải tuân theo cùng cách discovery.

Điểm chung không phải “API cấp cao phải trực tiếp phụ thuộc các lớp thấp hơn”, mà là **API cần cơ chế tìm plugin/provider đúng phạm vi** dù nơi định nghĩa API và nơi cung cấp implementation có thể dùng các loader khác nhau.

## 8. Thread context không tự theo công việc bất đồng bộ
Thread mới thông thường thừa hưởng TCCL ở **thời điểm tạo**, trong khi executor có thể tái sử dụng worker được tạo từ trước. Vì thế một lời gọi discovery trong worker có thể nhìn provider khác với lời gọi trên submitter, dù dùng cùng `ServiceLoader.load(service)`. Nếu cần ngữ cảnh plugin cụ thể, truyền `ClassLoader` tường minh hoặc gán/khôi phục ngay bên trong task với `try/finally`. Đừng giữ `ServiceLoader` của plugin A trong một static cache dùng cho nhiều ứng dụng; cache và worker đều có thể kéo dài vòng đời loader. Các bài về thread inheritance và pool cung cấp code trace riêng.
## 9. Vì sao loader cha và plugin cần dùng chung interface?

Xét hai phiên bản:

**Đúng thiết kế:**

```text
[Host/system loader]:  api.Greeting
        ^
        |
[Plugin child loader]: plugin.HelloGreeting implements api.Greeting
```

Plugin loader nhờ parent nạp `api.Greeting`. Cả host và plugin thấy **cùng một đối tượng `Class<api.Greeting>`**. `ServiceLoader<Greeting>` có thể cast provider về `Greeting`.

**Sai thiết kế:**

```text
[Host/system loader]: api.Greeting (bản A)
[Plugin child loader]: api.Greeting (bản B), plugin.HelloGreeting
```

Nếu plugin định nghĩa **một bản `api.Greeting` khác** bằng child-first loader hoặc package shading không phù hợp, `plugin.HelloGreeting` không nhất thiết assignable tới interface của host dù cả hai interface có cùng tên. Khi discovery, có thể nhận `ServiceConfigurationError: provider ... not a subtype`, `ClassCastException` hoặc lỗi linkage/access tương ứng.

Java identity của type là **binary name + defining loader**, không phải chỉ tên và nội dung file. Khi thiết kế SPI:

1. Đặt **service interface và shared DTO/API contract** vào loader chung, thường parent của plugin loaders.
2. Chỉ để các **implementation/dependencies riêng** ở plugin loaders.
3. Không đóng gói bản sao API chung vào mỗi plugin theo cách khiến plugin tự define lại class ấy.
4. Kiểm tra module exports/opens và API version khi runtime là named modules.

**TCCL không sửa được lỗi identity giữa hai bản service interface.** Nó chỉ quyết định loader bắt đầu discovery của những API dùng TCCL.

## 10. Với JPMS, TCCL không phải câu trả lời cho mọi module

Từ Java 9, service providers có thể khai báo trong `module-info.java`:

```java
// Module m.spi
module m.spi {
    exports api;
}
```

```java
// Module m.plugin
module m.plugin {
    requires m.spi;
    provides api.Greeting with plugin.HelloGreeting;
}
```

```java
// Module m.app
module m.app {
    requires m.spi;
    uses api.Greeting;
}
```

Đây là **ba tệp minh họa độc lập**, mỗi block thuộc `module-info.java` trong module tương ứng; không ghép ba block vào một file. Một module muốn dùng `ServiceLoader` trong named-module code thường cần khai báo `uses` tương ứng; provider có `provides ... with ...` và phù hợp constructor/provider method theo contract API.

Khi custom `ModuleLayer` chứa provider modules, lựa chọn thuận tiện thường là:

```java
ServiceLoader<Greeting> providers =
        ServiceLoader.load(pluginLayer, Greeting.class);
```

Đây là đoạn minh họa với `pluginLayer` là đối tượng `ModuleLayer` đã tạo. Overload này tìm provider ở layer và ancestor layers, không dùng classpath provider trong unnamed modules như một cơ chế tương đương hoàn toàn.

Đồng thời, `ServiceLoader.load(service, loader)` có thể tìm provider trong named modules gắn với loader và một số module layers liên quan theo quy tắc đặc tả. Vì vậy “TCCL = một loader” **không luôn đồng nghĩa chỉ tìm đúng một named module**. Trong JPMS, phải xét **module layer, uses/provides, package/module mapping** chứ không chỉ sơ đồ parent loader.

Các class đã load được cũng chưa chắc có thể truy cập mọi thành viên: module readability, exports, opens và access modifiers vẫn có hiệu lực. Service provider discovery không bỏ qua module access.

## 11. Các giới hạn thiết kế quan trọng

| Vấn đề | Giải thích đúng | Cách xử lý |
| --- | --- | --- |
| `Class.forName(name)` không thấy plugin | Nó dùng loader của **class caller**, không tự đọc TCCL | Dùng overload truyền loader hoặc SPI hợp đồng hóa |
| Parent loader không thấy class của child | Parent-first delegation theo chiều child → parent, không tự tìm xuống | Cho API discovery chủ động dùng TCCL/loader/module layer |
| `ServiceLoader` không tìm provider | Thiếu `META-INF/services`, sai loader, sai module declarations hoặc không có provider | Kiểm tra cấu hình SPI/module và loader khi iteration |
| Provider bị `ServiceConfigurationError` | Sai tên class, không assignable, constructor/provider method không hợp lệ hoặc lỗi instantiate | Đọc cause, so Class identity và metadata SPI |
| “Provider visible” nhưng cast lỗi | Plugin tự define bản API interface trùng tên | Đặt API ở loader chung, dùng đúng parent delegation |
| Quên restore TCCL sau request | Thread pool giữ loader cũ, định tuyến sai provider/leak | `try/finally`, quản lý lifecycle |
| Muốn retry provider sau cài plugin mới | `ServiceLoader` có cache theo instance, module/classloader lifecycle ảnh hưởng phát hiện | Tạo loader/ServiceLoader phù hợp hoặc gọi `reload()` khi có ý nghĩa |
| Có provider trong named module nhưng app không thấy | `ModuleLayer`, `uses`, `provides` hoặc module accessibility chưa đúng | Dùng overload layer/loader phù hợp, kiểm tra module graph |
| Đổi TCCL để sửa mọi lỗi loader | Chỉ hiệu lực với API dùng TCCL; không đổi bytecode/linking đã thực hiện | Xác định chính xác API lookup và caller |
| Dùng plugin loader để chạy mã không đáng tin | TCCL/loader không phải sandbox toàn diện | Áp dụng biện pháp isolation và trust phù hợp |

### Vì sao không nên cache `ServiceLoader` toàn JVM?

Theo Java 17 ServiceLoader API, `ServiceLoader.load(service)` dùng TCCL lúc tạo loader. Nếu server có nhiều ứng dụng cùng JVM nhưng khác TCCL, một `ServiceLoader` được cache toàn cục có thể giữ providers của ứng dụng A trong khi phục vụ B, hoặc giữ tham chiếu khiến class loader của A không được giải phóng. Tùy use case, nên scope cache theo deployment/request/module layer, quản lý lifecycle và tránh static references sống lâu hơn plugin.

Khi reload code động, chỉ `ServiceLoader.reload()` không thay thế các class đã được JVM định nghĩa trong một loader. Để có namespace phiên bản mới thực sự, framework có thể cần loader/plugin layer mới và giải phóng tham chiếu cũ.

## 12. Debug khi API ở parent không thấy implementation ở child
Phân tích theo ba trục độc lập: **visibility** (class/resource có thật sự nằm trong namespace của loader dùng tìm?), **identity** (provider và host có chia sẻ cùng runtime interface?) và **discovery** (descriptor/module declaration/overload `ServiceLoader` có đúng không?). Ghi các giá trị `Greeting.class.getClassLoader()`, `Thread.currentThread().getContextClassLoader()` và `provider.getClass().getClassLoader()` ngay ở code gọi, kiểm tra classpath của host **không vô tình chứa provider**. Với JPMS, xem `uses`/`provides` và `ModuleLayer`. Nếu lời gọi chạy qua executor, lấy thông tin từ worker thực tế. `ServiceConfigurationError` chỉ ra lỗi cấu hình/provider, khác với kết quả discovery rỗng.
## 13. Các ngộ nhận thường gặp

| Ngộ nhận | Sự thật |
| --- | --- |
| “Loader cha luôn thấy mọi class của con.” | Parent-first cho child hỏi parent; chiều ngược không mặc định tồn tại. |
| “API nền tảng biết tất cả plugin vì chúng cùng JVM.” | Nó cần cơ chế discovery và lựa chọn loader/module phù hợp. |
| “Đổi TCCL làm mọi `Class.forName(String)` dùng plugin loader.” | Không; `Class.forName(String)` dùng defining loader của caller. |
| “TCCL là parent mới của loader ứng dụng.” | Không; nó chỉ là tham chiếu loader trên thread. |
| “TCCL được lưu trong `Class<?>` của provider.” | Không; class có **defining loader** riêng, không bị thay bởi TCCL. |
| “Gọi `ServiceLoader.load` nghĩa là tất cả provider đã được instantiate.” | Discovery có thể lazy; iteration/`Provider.get()` mới thực hiện nhiều bước. |
| “Provider trùng tên class với interface bên host là đủ.” | Không; class identity cần đúng defining loader, nhất là API interface chung. |
| “Một static cache `ServiceLoader` dùng tốt cho mọi plugin.” | Có thể sai provider scope hoặc rò rỉ loader khi undeploy. |
| “TCCL cung cấp security isolation.” | Không; cần kiểm soát quyền/cô lập ở kiến trúc thích hợp. |
| “Java 17 có JPMS thì không cần TCCL nữa.” | TCCL vẫn dùng trong các SPI API; `ModuleLayer` cũng cung cấp discovery theo layer khi phù hợp. |

## 14. Câu hỏi tự kiểm tra

**1.** Vì sao system loader thường không load được class mà plugin loader con tự định nghĩa?

**Đáp án:** Parent delegation mặc định theo chiều child → parent. Parent không tự có danh sách class của child và cũng không tự search toàn bộ child loaders.

**2.** `ServiceLoader.load(Greeting.class)` chọn loader nào trong Java 17?

**Đáp án:** Theo API, chọn **TCCL của thread gọi**. Nó tương đương `ServiceLoader.load(Greeting.class, Thread.currentThread().getContextClassLoader())` về chọn loader.

**3.** Vì sao `Discovery.inspect()` trong ví dụ vẫn tìm được plugin, dù `Discovery` được system loader định nghĩa?

**Đáp án:** `Discovery` gọi `ServiceLoader.load(Greeting.class)`, API này chọn TCCL. `Launcher` đã đặt TCCL thành plugin loader nên metadata và provider của plugin được tìm thấy, dù defining loader của `Discovery` không đổi.

**4.** Có thể dùng `Class.forName("plugin.HelloGreeting")` thay `ServiceLoader.load` mà kỳ vọng cùng kết quả khi đổi TCCL không?

**Đáp án:** Không mặc định. `Class.forName(String)` chọn defining loader của class chứa call site. Nếu muốn chỉ định plugin loader phải dùng overload `Class.forName(name, initialize, pluginLoader)`.

**5.** Vì sao cần đặt `api.Greeting` ở parent/shared loader?

**Đáp án:** Host và plugin phải dùng **cùng định nghĩa interface**. Nếu mỗi loader tự define một bản, cùng tên binary vẫn có thể là hai kiểu JVM khác nhau; provider không assignable tới service type phía host.

**6.** Có nên giữ TCCL của plugin A trên một thread pool worker khi request A kết thúc không?

**Đáp án:** Không. Request B có thể dùng nhầm provider của A và giữ tham chiếu tới plugin A. Khôi phục context cũ bằng `finally`.

**7.** Java module `m.app` dùng `ServiceLoader` thì khi nào cần khai báo `uses api.Greeting;`?

**Đáp án:** Trong named-module code, nếu module dùng service type đó để khám phá providers theo `ServiceLoader`, module declaration cần khai báo `uses` tương ứng. Provider module khai báo `provides ... with ...` phù hợp.

**8.** Nếu TCCL là `null`, các API `ServiceLoader.load(service)` và `Class.forName(name, true, null)` có cùng nghĩa loader không?

**Đáp án:** Không. Theo `ServiceLoader`, loader `null` biểu thị system loader (nếu có, fallback bootstrap). Với overload `Class.forName` có tham số `ClassLoader`, `null` là bootstrap loader.

**9.** Trong ví dụ nếu bỏ SPI descriptor nhưng giữ class provider, `ServiceLoader` sẽ thấy provider không?

**Đáp án:** Không qua cơ chế classpath `META-INF/services` của ví dụ. Class có thể tồn tại nhưng ServiceLoader không biết provider nào cần xét. Nếu dùng named modules, declaration `provides` là cơ chế khác.

**10.** API nền tảng có nên chủ động quét mọi plugin loader trong JVM không?

**Đáp án:** Không nên. Namespace/version, isolation và lifecycle khác nhau. Cần cơ chế discovery theo **context/loader/module layer** cụ thể; TCCL là một cách diễn đạt context đó khi API hỗ trợ.

## 15. Tóm tắt quy trình suy luận

Khi API nền tảng không thấy implementation ứng dụng, hãy hỏi:

1. **Interface/service** nằm ở loader/module nào, và implementation nằm ở đâu?
2. **Mã discovery** dùng `Class.forName`, `ServiceLoader` hay factory mechanism nào? API đó đọc **caller loader**, **TCCL**, **loader truyền vào**, hay **ModuleLayer**?
3. **TCCL tại thread đang chạy thật sự** là gì? Có executor/thread pool đổi ngữ cảnh không?
4. **Provider metadata** đã được đóng gói đúng (`META-INF/services` hoặc `provides`) chưa?
5. Host và provider có dùng **cùng class identity** cho service interface không?
6. Có **module access**, lifecycle/cache hoặc initialization error che khuất nguyên nhân không?

Tóm lại: **API nền tảng cần thấy cách triển khai của ứng dụng vì một hợp đồng mở chỉ hữu ích khi runtime tìm và gọi được provider do ứng dụng cung cấp.** Parent-first bảo vệ thứ tự delegation nhưng không tự tạo chiều nhìn xuống. **TCCL cung cấp lựa chọn loader theo ngữ cảnh của thread** cho các API như `ServiceLoader`, trong khi các hệ module/loader khác có thể dùng những cơ chế tường minh khác.

Nguồn chuẩn:

- [`java.util.ServiceLoader` – Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): overload dùng TCCL, cách khai báo provider, khám phá ở module/classpath, lazy loading, cache/lifecycle.
- [`java.lang.Thread` – Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `getContextClassLoader`, `setContextClassLoader` và kế thừa context.
- [`java.lang.ClassLoader` – Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation và defining loader.
- [`java.lang.Class` – Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `Class.forName` và loader được chọn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-class-loader-003">getContextClassLoader và setContextClassLoader</a>

<details>
<summary>Xem chi tiết</summary>

# getContextClassLoader và setContextClassLoader trong Java

## 1. Mục tiêu và vấn đề thực tế

Trong Java, một lớp thường được nạp thông qua ClassLoader liên quan đến lớp đang yêu cầu nạp, hoặc thông qua loader truyền tường minh cho API. Tuy nhiên, nhiều framework/API cần nạp **implementation của ứng dụng/plugin** từ đoạn code thư viện vốn được định nghĩa bởi loader cấp trên. Chỉ dùng loader định nghĩa thư viện có thể không nhìn thấy các lớp/plugin mà ứng dụng đưa vào.

Java giải quyết một phần nhu cầu này bằng **Thread Context ClassLoader (TCCL)** — một tham chiếu `ClassLoader` gắn với **mỗi đối tượng `Thread`**. Hai API trọng tâm:

~~~java
ClassLoader loader = Thread.currentThread().getContextClassLoader();
Thread.currentThread().setContextClassLoader(otherLoader);
~~~

- `getContextClassLoader()`: đọc ClassLoader ngữ cảnh hiện được gắn với thread.
- `setContextClassLoader(ClassLoader)`: đặt/thay thế ClassLoader ngữ cảnh của thread.
- Cả hai là **instance methods trên `Thread`**, không phải static methods của `ClassLoader`. `Thread.currentThread()` chỉ chọn thread đang thực thi.
- TCCL là **gợi ý/ngữ cảnh cho những API chủ động sử dụng nó**. Gán TCCL **không tự nạp một class**, **không đổi defining loader** của class đã được định nghĩa và **không tự thêm JAR vào JVM classpath**.

**Mục tiêu hoàn thành bài:** phân biệt TCCL với defining loader và parent delegation; đọc và thay đổi TCCL an toàn; biết lúc nào API thật sự dùng TCCL; hiểu thread inheritance và thread pools; biên dịch/chạy ví dụ Java 17+ với `URLClassLoader` và khôi phục loader bằng `finally`.

## 2. TCCL khác ClassLoader định nghĩa lớp ở đâu?

### 2.1. Ba loại thông tin phải tách biệt

| Khái niệm | Câu hỏi trả lời | Cách quan sát |
|---|---|---|
| **Defining loader** của `SomeType` | Loader nào thực sự định nghĩa runtime class `SomeType`? | `SomeType.class.getClassLoader()` |
| **Parent** của loader | Loader này ủy quyền cho loader nào theo mô hình delegation? | `loader.getParent()` |
| **Thread context loader** | Code đang chạy trong thread này được cung cấp loader ngữ cảnh nào? | `Thread.currentThread().getContextClassLoader()` |

Một ứng dụng có thể có:

~~~text
Application ClassLoader  (L_app)
  |  định nghĩa demo.app.Launcher
  |  định nghĩa interface demo.api.Plugin
  |
  +-- Child URLClassLoader (L_plugin)
         định nghĩa demo.plugin.Implementation

main thread:
  context ClassLoader = L_plugin  (được set tạm thời)

demo.app.Launcher.class.getClassLoader() = L_app (không đổi)
Implementation.class.getClassLoader()   = L_plugin
main.getContextClassLoader()            = L_plugin (trong phạm vi tạm thời)
~~~

Ba phép so sánh có thể cùng đúng mà không mâu thuẫn. **TCCL thuộc thread, không phải thuộc `Class<?>`**. Tên gọi “context loader” cũng không có nghĩa đây là loader **cha** của các loader khác.

### 2.2. Tại sao cần TCCL?

Ví dụ, một thư viện SPI chạy trong một class do platform/application loader định nghĩa muốn tìm implementations do plugin loader nạp. Nếu thư viện chỉ sử dụng defining loader của chính nó, nó có thể không thấy plugin. Thư viện có thể **hỏi thread đang gọi** xem loader ngữ cảnh nào được ứng dụng thiết lập, rồi dùng loader đó tìm provider.

Sơ đồ điều khiển:

~~~text
App gọi thư viện SPI
  -> thư viện đọc Thread.currentThread().getContextClassLoader()
  -> thư viện dùng ClassLoader được cung cấp để tra provider
  -> ứng dụng/plugin chỉ định loader chứa provider tương ứng

TCCL = cầu nối ngữ cảnh khám phá lớp, không phải lệnh đổi định danh lớp.
~~~

Một ví dụ có hợp đồng rõ ràng là `ServiceLoader.load(ServiceType.class)`: overload tiện ích này sử dụng TCCL của thread hiện tại. Nhưng **không phải mọi API nạp lớp của Java đều đọc TCCL**; hãy kiểm tra đúng overload đang gọi.

## 3. `Thread.getContextClassLoader()`

Chữ ký:

~~~java
public ClassLoader getContextClassLoader()
~~~

API trả về ClassLoader ngữ cảnh của **đối tượng Thread được gọi**. Trong code đang chạy, ta thường dùng:

~~~java
Thread current = Thread.currentThread();
ClassLoader tccl = current.getContextClassLoader();
~~~

Các điểm cần nhớ:

1. **Không mặc định luôn bằng application/system loader.** Ở thread ứng dụng chính khi chạy Java thông thường, TCCL ban đầu thường là loader dùng nạp ứng dụng. Nhưng framework, server, thread pool, chương trình nhúng hoặc code khác có thể đã thay giá trị.
2. **Không mặc định bằng defining loader của lớp gọi.** `Library.class.getClassLoader()` và `Thread.currentThread().getContextClassLoader()` có thể khác nhau.
3. **Giá trị `null` là hợp lệ.** Theo `Thread` API, `null` ở TCCL diễn đạt ý nghĩa dùng system class loader (hoặc bootstrap nếu không có system loader) đối với API theo hợp đồng tương ứng. Không được mặc định dùng `tccl.loadClass(...)` khi `tccl == null`.
4. **Đọc TCCL không nạp lớp.** Đây là thao tác lấy tham chiếu, không phải `loadClass`.
5. **Các chính sách bảo mật lịch sử có thể tác động.** Trong môi trường có SecurityManager, `getContextClassLoader()` có thể ném `SecurityException` nếu caller không có quyền phù hợp. SecurityManager bị deprecate trong Java 17; không nên dùng nó làm trọng tâm cho ứng dụng mới.

Ví dụ kiểm tra an toàn:

~~~java
ClassLoader tccl = Thread.currentThread().getContextClassLoader();
if (tccl == null) {
    System.out.println("Thread đang dùng context loader mặc định theo hợp đồng API");
} else {
    System.out.println("TCCL hiện tại là: " + tccl.getClass().getName());
}
~~~

Đoạn trên **chỉ là mã minh họa trong một phương thức**, không phải tệp độc lập. Không nên dùng chuỗi `toString()` hoặc tên class loader để xác lập danh tính runtime; dùng so sánh tham chiếu `==` khi phù hợp.

## 4. `Thread.setContextClassLoader(ClassLoader)`

Chữ ký:

~~~java
public void setContextClassLoader(ClassLoader cl)
~~~

Hàm này **gán** context ClassLoader cho **Thread nhận lời gọi**. Gọi trên thread hiện tại:

~~~java
Thread current = Thread.currentThread();
current.setContextClassLoader(pluginLoader);
~~~

Không có thao tác kiểm tra class/JAR trong `setContextClassLoader`. Chỉ khi code sau đó gọi API **sử dụng TCCL**, chẳng hạn `ServiceLoader.load(MyService.class)` hoặc `Class.forName(name, false, current.getContextClassLoader())`, loader mới có thể được dùng để tìm lớp.

### 4.1. Có thể đặt trên Thread khác không?

Về mặt API, `someThread.setContextClassLoader(cl)` thao tác trên chính đối tượng `someThread`, không bắt buộc phải là current thread. Nhưng thay đổi TCCL của thread đang chạy nơi khác làm tăng nguy cơ race condition, hiểu sai ngữ cảnh và lỗi bảo mật. Hướng phổ biến là **thread tự thiết lập ngữ cảnh của nó** khi thực hiện tác vụ, hoặc cấu hình đúng `ThreadFactory`/executor tại biên tạo thread.

### 4.2. Truyền `null` có ý nghĩa gì?

`current.setContextClassLoader(null)` là hợp lệ theo Java `Thread` API; `null` thể hiện system loader (hoặc bootstrap dự phòng) theo hợp đồng của context loader. Nhưng nếu code riêng của bạn làm:

~~~java
Thread.currentThread().getContextClassLoader().loadClass("demo.Widget");
~~~

thì TCCL `null` khiến expression trên ném `NullPointerException`. Đừng tự diễn giải `null` như một loader object. Với API cho phép loader `null`, hãy đọc hợp đồng chính xác; ví dụ `Class.forName(name, initialize, null)` lại có nghĩa **bootstrap loader**, không giống cách `Thread` diễn đạt giá trị context mặc định.

### 4.3. Quyền truy cập

Ở môi trường Java 17 có SecurityManager, `setContextClassLoader` có thể kiểm tra quyền `RuntimePermission("setContextClassLoader")` và ném `SecurityException`. Điều này không có nghĩa Java mới luôn kiểm tra permission bằng SecurityManager; không nên coi cơ chế này là cơ chế sandbox đáng tin cậy cho code không tin cậy.

### 4.4. Mẫu thiết lập và khôi phục đúng

~~~java
Thread thread = Thread.currentThread();
ClassLoader oldLoader = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi những API thực sự dùng TCCL trong phạm vi này.
} finally {
    thread.setContextClassLoader(oldLoader);
}
~~~

Tại sao **phải có `finally`**?

- Nếu trong phạm vi xảy ra `RuntimeException`, loader ngữ cảnh vẫn phải được khôi phục.
- Thread pool thường dùng lại một worker thread cho nhiều request/tác vụ khác nhau. Loader cũ còn sót lại có thể làm tác vụ sau nhìn thấy provider/JAR của tác vụ trước.
- Một thread sống lâu giữ tham chiếu tới custom loader thông qua TCCL có thể cản trở garbage collection của plugin, deployment hoặc application cũ khi chúng cần được unload.

Nếu `setContextClassLoader(pluginLoader)` có thể ném lỗi **trước khi vào phạm vi thực thi**, đặt lệnh này trong khối `try` như ví dụ là một cách an toàn cho logic khôi phục; khi xử lý `SecurityException` hãy giữ nguyên nguyên nhân. Đừng “cố khôi phục” bằng cách ép mọi exception vào catch rỗng.

## 5. Khi nào TCCL thực sự được sử dụng?

### 5.1. API dùng TCCL: `ServiceLoader.load(service)`

Theo Javadoc Java 17, lời gọi:

~~~java
ServiceLoader<MyService> providers =
        ServiceLoader.load(MyService.class);
~~~

tương đương về lựa chọn loader với:

~~~java
ServiceLoader<MyService> providers = ServiceLoader.load(
        MyService.class,
        Thread.currentThread().getContextClassLoader());
~~~

Đây là lý do cốt lõi của TCCL trong môi trường SPI/plugin: provider có thể do loader khác định nghĩa, còn **service interface** cần có type identity mà cả thư viện và plugin đều thống nhất.

**Mã trên là snippet minh họa, cần interface `MyService`**; không phải chương trình độc lập. Dữ liệu provider qua `META-INF/services/<binary-service-name>` dành cho classpath, hoặc qua `provides ... with ...` trong named module (tùy cách deploy/resolve).

Ba overload không giống nhau:

| API | Nguồn/loader dùng tìm provider |
|---|---|
| `ServiceLoader.load(S.class)` | **Context ClassLoader của current thread** |
| `ServiceLoader.load(S.class, explicitLoader)` | **Loader được truyền tường minh** |
| `ServiceLoader.load(layer, S.class)` | **ModuleLayer và các layer tổ tiên** theo hợp đồng JPMS |

Trong named modules, caller cũng phải tuân thủ yêu cầu `uses` với service khi API đòi hỏi; provider phải được khai báo và module resolve phù hợp. Thay đổi TCCL **không tự tạo module layer, không tự thêm `requires`, `uses` hoặc `provides`**.

### 5.2. API không tự đọc TCCL: `Class.forName(String)`

~~~java
Class<?> type = Class.forName("demo.plugin.Widget");
~~~

Theo `Class` Javadoc, overload một tham số dùng **defining loader của lớp đang gọi** (và yêu cầu initialization nếu cần), **không tự lấy TCCL**.

Nếu muốn chủ động dùng loader context:

~~~java
ClassLoader tccl = Thread.currentThread().getContextClassLoader();
Class<?> type = Class.forName("demo.plugin.Widget", false, tccl);
~~~

Ở đây **chính bạn đã truyền TCCL vào overload nhận loader**. Lưu ý: nếu `tccl == null` thì overload `Class.forName(String, boolean, ClassLoader)` coi đó là yêu cầu nạp bằng **bootstrap loader**; cần chọn chiến lược null phù hợp cho ứng dụng, không suy rằng nó luôn dùng system loader.

Với ClassLoader API:

~~~java
ClassLoader tccl = Thread.currentThread().getContextClassLoader();
if (tccl != null) {
    Class<?> type = tccl.loadClass("demo.plugin.Widget");
}
~~~

`loadClass` được gọi trên loader được bạn chọn; `ClassLoader.loadClass` không tự đổi “sang TCCL” nếu gọi trên loader khác. Đừng nhầm **API dùng loader truyền tường minh** với **API mặc định đọc context loader**.

### 5.3. Resource lookup cũng phụ thuộc API

Một framework đôi khi dùng:

~~~java
ClassLoader tccl = Thread.currentThread().getContextClassLoader();
java.net.URL resource = tccl == null ? null
        : tccl.getResource("config/plugin.properties");
~~~

Điều này tìm resource bằng loader được chọn. Không nên suy từ `Thread.setContextClassLoader` rằng mọi lời gọi `SomeClass.class.getResource(...)` hoặc `ClassLoader.getSystemResource(...)` sẽ đổi cách tìm: mỗi API có quy tắc chọn loader và lookup riêng.

## 6. Ví dụ Java 17+ độc lập: plugin loader và TCCL

Ví dụ này kiểm chứng đồng thời:

1. Loader ứng dụng **không** tìm được plugin class bằng `Class.forName(String)`.
2. `URLClassLoader` con tìm/định nghĩa được plugin class.
3. `setContextClassLoader` đổi TCCL, cho phép code **chủ động truyền** TCCL vào `Class.forName(name, false, loader)` để tìm plugin.
4. Thread con được tạo trong phạm vi có TCCL mới **kế thừa** ngữ cảnh.
5. `finally` khôi phục TCCL; defining loader của lớp ứng dụng **không đổi**.

### 6.1. Cấu trúc thư mục

~~~text
demo/
  src/
    app/
      TcclDemo.java
    plugin/
      demo/
        plugin/
          Widget.java
  build/
    app/                  # classpath runtime của launcher
    plugin/               # class files chỉ cho URLClassLoader con
~~~

Chạy từ đúng thư mục `demo`. Không thêm `build/plugin` vào classpath system khi chạy; nếu không, minh họa cô lập loader sẽ không còn ý nghĩa.

### 6.2. Tệp `src/plugin/demo/plugin/Widget.java`

~~~java
package demo.plugin;

public final class Widget {
    public static String label() {
        return "plugin-ready";
    }
}
~~~

Plugin class không được compile cùng app. Trong ví dụ, app chỉ biết tên nhị phân `demo.plugin.Widget` tại runtime.

### 6.3. Tệp `src/app/TcclDemo.java`

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class TcclDemo {
    public static void main(String[] args) throws Exception {
        Thread current = Thread.currentThread();
        ClassLoader original = current.getContextClassLoader();
        ClassLoader defining = TcclDemo.class.getClassLoader();

        try (URLClassLoader pluginLoader = new URLClassLoader(
                new URL[] { Path.of("build/plugin").toUri().toURL() },
                defining)) {

            System.out.println("parentIsApp="
                    + (pluginLoader.getParent() == defining));

            try {
                Class.forName("demo.plugin.Widget");
                System.out.println("defaultLookupMissing=false");
            } catch (ClassNotFoundException expected) {
                System.out.println("defaultLookupMissing=true");
            }

            Class<?> pluginClass = Class.forName(
                    "demo.plugin.Widget", false, pluginLoader);
            System.out.println("pluginDefinedByChild="
                    + (pluginClass.getClassLoader() == pluginLoader));

            try {
                current.setContextClassLoader(pluginLoader);
                System.out.println("contextInstalled="
                        + (current.getContextClassLoader() == pluginLoader));

                Class<?> selected = Class.forName(
                        "demo.plugin.Widget",
                        false,
                        current.getContextClassLoader());
                System.out.println("contextLookupMatches="
                        + (selected == pluginClass));

                Thread child = new Thread(() -> System.out.println(
                        "childInherited="
                        + (Thread.currentThread().getContextClassLoader()
                                == pluginLoader)));
                child.start();
                child.join();
            } finally {
                current.setContextClassLoader(original);
            }

            System.out.println("restored="
                    + (current.getContextClassLoader() == original));
            System.out.println("definingLoaderUnchanged="
                    + (TcclDemo.class.getClassLoader() == defining));
        }
    }
}
~~~

Chú ý:

- `defining` được lấy từ `TcclDemo.class` để làm parent cho loader mới. Parent-first delegation vẫn giữ khả năng tìm JDK và shared API classes.
- Plugin chỉ có trong `build/plugin` nên system loader không tìm ra `demo.plugin.Widget` khi gọi overload một tham số từ `TcclDemo`.
- `Class.forName("demo.plugin.Widget", false, pluginLoader)` dùng đúng loader con và không yêu cầu khởi tạo class.
- `Class.forName(..., false, current.getContextClassLoader())` **không tự phát hiện TCCL**; code đã truyền loader một cách tường minh.
- Thread con được **tạo sau khi context của thread chính được đặt**. Nó kế thừa reference đến cùng loader. Nếu thread con tiếp tục sống sau khi parent khôi phục TCCL, reference mà thread con giữ **không tự được khôi phục theo**.

### 6.4. Biên dịch và chạy

Trên Linux/macOS, từ thư mục `demo`:

~~~sh
mkdir -p build/app build/plugin
javac --release 17 -d build/plugin src/plugin/demo/plugin/Widget.java
javac --release 17 -d build/app src/app/TcclDemo.java
java -cp build/app TcclDemo
~~~

Trên Windows PowerShell, tạo thư mục output:

~~~powershell
New-Item -ItemType Directory -Force build/app, build/plugin | Out-Null
javac --release 17 -d build/plugin src/plugin/demo/plugin/Widget.java
javac --release 17 -d build/app src/app/TcclDemo.java
java -cp build/app TcclDemo
~~~

**Stdout chính xác**, đã biên dịch với `javac --release 17` và kiểm tra bằng JDK:

~~~text
parentIsApp=true
defaultLookupMissing=true
pluginDefinedByChild=true
contextInstalled=true
contextLookupMatches=true
childInherited=true
restored=true
definingLoaderUnchanged=true
~~~

### 6.5. Phân tích kết quả

| Output | Điều được chứng minh |
|---|---|
| `parentIsApp=true` | Loader con có `TcclDemo` defining loader làm parent |
| `defaultLookupMissing=true` | `Class.forName(String)` từ app **không tự dùng** TCCL/loader plugin và không tìm được plugin trong app loader |
| `pluginDefinedByChild=true` | Plugin class được loader con định nghĩa |
| `contextInstalled=true` | TCCL của current thread đã thay đổi |
| `contextLookupMatches=true` | Đọc TCCL và truyền vào overload nhận loader tìm đúng cùng `Class<?>` |
| `childInherited=true` | Thread mới kế thừa context loader của thread tạo ra nó |
| `restored=true` | `finally` khôi phục reference ban đầu |
| `definingLoaderUnchanged=true` | Thay TCCL **không đổi** loader đã định nghĩa `TcclDemo` |

**Điểm dễ nhầm:** thành công của `contextLookupMatches` không có nghĩa mọi class lookup giờ đều dùng loader con. Chỉ phép tìm đã **chọn/truyền loader con** mới dùng nó.

### 6.6. Ba biến thể kiểm tra

**Biến thể A — Không set TCCL:** bỏ `current.setContextClassLoader(pluginLoader);`. `Class.forName(..., current.getContextClassLoader())` sẽ dùng loader cũ; trong môi trường demo chuẩn không tìm được plugin và ném `ClassNotFoundException`. Đây là lỗi có chủ đích trong biến thể; không phải output của bản gốc.

**Biến thể B — Truyền loader tường minh:** vẫn giữ nguyên context ban đầu, nhưng gọi `Class.forName("demo.plugin.Widget", false, pluginLoader)`. Phép tìm **vẫn thành công** vì caller truyền đúng loader, cho thấy TCCL chỉ cần khi API/convention của bạn chọn sử dụng nó.

**Biến thể C — Tạo child thread trước khi set:** tạo `new Thread(...)` khi TCCL còn là `original`, sau đó mới `setContextClassLoader(pluginLoader)` và `start()` child. Theo quy tắc kế thừa mặc định của `Thread`, child đã nhận context lúc **được tạo**, không phải lúc bắt đầu `start()`. TCCL của child sẽ không tự nhảy theo giá trị mới của parent.

## 7. Thread mới và executor thay đổi ngữ cảnh như thế nào?
Thread mới thông thường thừa hưởng TCCL tại thời điểm **construction**. Do đó, nếu tạo child trong lúc TCCL tạm là plugin loader, child vẫn có thể giữ reference ấy sau khi parent đã khôi phục. Thread pool lại tái sử dụng worker, nên set TCCL ở submitter không bảo đảm tác vụ chạy với loader đó. Khi cần, gói task và thực hiện lấy previous → set plugin loader → chạy → restore trong `finally` trên **worker**; hoặc truyền explicit loader cho API phù hợp. Không đóng plugin loader khi task/provider còn sử dụng nó. Hai bài dành riêng cho inheritance và thread pool cung cấp ví dụ độc lập và các chính sách `ThreadFactory`.
## 8. Giới hạn của hai API và lỗi cần phòng
`setContextClassLoader` không tạo sandbox bảo mật, không bổ sung JAR vào classpath, không đổi module readability/exports/opens và không làm `Class.forName(String)` tự chọn TCCL. Code dùng `getContextClassLoader()` phải xét kết quả `null` trước khi gọi instance method; ý nghĩa `null` khi truyền cho `Class.forName(String, boolean, ClassLoader)` khác quy ước của `Thread`. Nếu code đọc/đặt TCCL bị `SecurityException` trong runtime có cơ chế kiểm soát quyền tương ứng, cần giữ nguyên nguyên nhân và không coi context đã thay đổi thành công. Tránh cache một TCCL của tenant/plugin trong singleton lâu sống.
## 9. So sánh các tình huống

| Biểu thức/tình huống | Có dùng TCCL tự động? | Kết luận |
|---|---|---|
| `Thread.currentThread().getContextClassLoader()` | Đây là thao tác **đọc TCCL** | Trả loader context hoặc `null` |
| `Thread.currentThread().setContextClassLoader(loader)` | Đây là thao tác **gán TCCL** | Không tự load class |
| `SomeType.class.getClassLoader()` | **Không** | Trả defining loader của class |
| `Class.forName("p.Type")` | **Không** | Dựa vào defining loader của caller theo Javadoc |
| `Class.forName("p.Type", false, tccl)` | **Có**, vì **caller truyền** nó | Dùng loader explicit, không yêu cầu khởi tạo |
| `tccl.loadClass("p.Type")` | **Có**, vì gọi trên loader đó | Nếu tccl null cần xử lý trước |
| `ServiceLoader.load(Service.class)` | **Có** | Overload tiện ích lấy TCCL hiện tại |
| `ServiceLoader.load(Service.class, loader)` | **Không tự lấy** | Dùng loader argument |
| `ServiceLoader.load(layer, Service.class)` | **Không theo TCCL mặc định** | Tra provider trong module layer theo API |
| `new Thread(task)` | **Có kế thừa** theo mặc định | Nhận context từ thread tạo lúc construction |
| `executor.submit(task)` | **Không bảo đảm inherit** context thread gửi | Task có thể dùng worker thread được tạo từ trước |

Tại những API dùng TCCL, nếu đã set loader trên thread chính nhưng lại gọi API trên **thread khác**, nó sẽ đọc TCCL **của thread khác**, không đọc “biến global context” của process.

## 10. Chẩn đoán các lỗi thường gặp

| Triệu chứng | Nguyên nhân đáng kiểm tra | Hướng xử lý |
|---|---|---|
| `ServiceLoader` không tìm thấy provider | TCCL hiện tại không nhìn thấy provider config/class, hoặc service metadata/module configuration thiếu | Kiểm tra provider path, `META-INF/services`/`provides`, loader thực tế, `uses` |
| `Class.forName("plugin.X")` thất bại dù set TCCL | Overload một tham số **không tự đọc TCCL** | Dùng overload nhận loader tường minh nếu phù hợp |
| Plugin A chạy được, plugin B gọi sau lại dùng A | Thread pool quên restore context hoặc cache ServiceLoader dùng loader cũ | Bọc task bằng `try/finally`, giới hạn cache theo scope |
| Child thread không thấy loader mới | Thread đã được **tạo trước** khi parent set TCCL, hoặc framework set context riêng | Kiểm tra thời điểm tạo, `ThreadFactory` và worker logic |
| Có `NullPointerException` tại `getContextClassLoader().loadClass(...)` | TCCL có thể null | Kiểm tra null, chọn loader fallback theo contract |
| `ClassCastException` dù class names giống nhau | Hai defining loaders tạo hai runtime types khác nhau | Dùng shared API types từ loader thống nhất |
| `IllegalAccessError` hoặc module access error | Không phải chỉ lỗi loader; có thể do runtime package/JPMS access | Kiểm tra defining loaders, module readability, `exports`/`opens` |
| Plugin không unload sau redeploy | Thread/caches còn giữ loader cũ | Restore TCCL, stop tasks, dọn caches/resource |
| `SecurityException` khi get/set TCCL | Quyền theo môi trường SecurityManager cũ hoặc chính sách framework | Ghi nguyên cause, theo quyền và API hiện hành |

### Checklist tối thiểu trước khi thay TCCL

1. Có thực sự cần TCCL hay API đã nhận loader tường minh?
2. Plugin loader có chứa class/resource/provider cần tìm, và có đúng parent không?
3. Thiết lập sẽ ảnh hưởng **thread nào**, có thread pool hay thread con nào kế thừa không?
4. Đã chụp context cũ của **chính thread đang chạy** chưa?
5. Đã khôi phục trong `finally` kể cả khi code ném exception chưa?
6. Có cache/static field hoặc worker lâu sống giữ reference tới loader/plugin không?
7. Có điều kiện access/module nào cần xử lý độc lập không?

## 11. Bài tập và câu hỏi kiểm tra

1. So sánh `SomeType.class.getClassLoader()` và `Thread.currentThread().getContextClassLoader()`. Khi nào chúng có thể khác nhau?
2. `Thread.setContextClassLoader` có đổi loader đã định nghĩa `SomeType` không?
3. Tại sao `Class.forName(String)` vẫn không tìm được `Widget` ở ví dụ dù loader con có bytecode?
4. Câu lệnh nào trong ví dụ đã chủ động **truyền TCCL** tới API nạp lớp?
5. Vì sao `ServiceLoader.load(MyService.class)` thường được dùng trong bài toán SPI/plugin?
6. `ServiceLoader.load(MyService.class, explicitLoader)` có phải tự động đọc TCCL không?
7. Nếu thread cha đổi TCCL **sau khi** đã tạo thread con nhưng **trước khi** gọi `child.start()`, thread con mặc định giữ loader nào?
8. Vì sao set TCCL ở thread gửi `executor.submit(...)` không bảo đảm task thấy context mới?
9. Điều gì có thể sai nếu plugin loader bị để lại trong TCCL của worker thread sau khi task kết thúc?
10. `getContextClassLoader() == null` có hợp lệ không? Vì sao không được lập tức gọi `.loadClass(...)` trên giá trị đó?
11. Nêu tình huống mà loader được truyền tường minh an toàn và dễ hiểu hơn chỉnh TCCL.
12. Một `ClassCastException` khi giao tiếp giữa host và plugin có thể liên quan defining loaders như thế nào?
13. Giải thích tại sao khối `finally` vẫn cần thiết khi API nạp class hoặc provider có thể ném exception.
14. Muốn unload plugin, chỉ gọi `URLClassLoader.close()` có bảo đảm tất cả class cũ được GC ngay không?
15. Nếu API nạp lớp không dùng TCCL, chỉ thay TCCL có tác dụng gì đối với API đó?

**Tiêu chí hoàn thành:** tự chạy ví dụ và giải thích đủ tám dòng output; phân biệt defining loader, parent và TCCL; nêu đúng API có/không đọc context; sử dụng `try/finally` để tránh rò ngữ cảnh trong thread pool và hiểu quy tắc kế thừa của `new Thread`.

## 12. Tài liệu chuẩn

- [Java SE 17 — `Thread.getContextClassLoader` và `Thread.setContextClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): khai báo phương thức, cơ chế kế thừa và `null`, quyền truy cập.
- [Java SE 17 — `ServiceLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): overload `load(service)` dùng TCCL, overload tường minh/layer, lưu ý cache/provider.
- [Java SE 17 — `Class.forName`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): cách chọn loader của overload một tham số và ba tham số.
- [Java SE 17 — `ClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): defining loader, delegation và `loadClass`.
- [Java SE 17 — `URLClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): loader theo URL, đóng resource và vòng đời sử dụng.

Bài áp dụng cho Java 17+; ví dụ đã chạy với compiler `--release 17`. Với phiên bản JDK và framework mới, hãy tham khảo hợp đồng API tương ứng, đặc biệt quanh SecurityManager, module layers và các loại threads mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-class-loader-004">Lựa chọn loader tường minh thay vì phụ thuộc ngầm vào luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Lựa chọn loader tường minh thay vì phụ thuộc ngầm vào luồng

## 1. Tại sao phải chọn ClassLoader?

Trong JVM, một class được định danh bởi **binary name và defining class loader**. Hai class cùng tên `com.example.Plugin` có thể là hai runtime types khác nhau nếu do hai defining loaders khác nhau định nghĩa. Vì vậy, câu hỏi “nạp class nào?” luôn có thêm phần “nạp **qua loader nào**?”.

Java cung cấp **thread context class loader (TCCL)** qua `Thread.currentThread().getContextClassLoader()`. TCCL giúp code thư viện/framework tìm implementation và resources theo ngữ cảnh thread đang chạy, nhất là khi implementation nằm ở loader con mà thư viện ở loader cha không tự nhìn thấy. Tuy nhiên, giá trị đó **thay đổi được**, được gắn với thread và có thể khác khi code chạy trong executor, callback, application server hoặc plugin runtime.

Khi biết rõ class, resource hoặc provider thuộc artifact/module/plugin nào, nên cân nhắc **chọn loader tường minh** và truyền nó cho API:

```java
ClassLoader pluginLoader = /* loader do plugin manager cung cấp */;
Class<?> implementation =
        Class.forName("com.example.PluginImpl", false, pluginLoader);
```

Đây là **mã minh họa**, cần thay `pluginLoader` bằng một biến đã được khai báo thực tế. `false` không yêu cầu initialization; loader phải thấy được class được hỏi. Tư tưởng là đưa dependency về loader vào input của component thay vì suy đoán từ TCCL hiện tại.

> TCCL không sai. Nó thích hợp khi framework **cố ý định nghĩa lookup theo context của thread**. Vấn đề là dùng nó như một mặc định vô thức khi thực chất đã có loader sở hữu đúng định nghĩa cần dùng.

## 2. TCCL khác defining loader như thế nào?

| Khái niệm | Lấy từ đâu? | Ý nghĩa |
|---|---|---|
| **Defining loader** | `SomeType.class.getClassLoader()` | Loader định nghĩa runtime class `SomeType`; đã định nghĩa thì identity không đổi |
| **TCCL** | `Thread.currentThread().getContextClassLoader()` | Loader được gắn với ngữ cảnh thread, có thể được thay đổi |
| **System loader** | `ClassLoader.getSystemClassLoader()` | Loader hệ thống theo cấu hình JVM; không tự động chứa mọi plugin |
| **Loader truyền tường minh** | Biến `loader` do caller/factory cung cấp | Quyết định đường tra cứu riêng cho yêu cầu |
| **Bootstrap loader** | Thường biểu diễn bằng `null` tại các API dành cho nó | Cơ chế nạp class nền tảng của JVM |

Đổi TCCL không làm `SomeType.class.getClassLoader()` thay đổi, không định nghĩa lại class đã có và không biến object thuộc loader A thành object thuộc loader B.

Ví dụ:

```java
Thread thread = Thread.currentThread();
ClassLoader old = thread.getContextClassLoader();
ClassLoader custom = new ClassLoader(old) { };

try {
    thread.setContextClassLoader(custom);
    // TCCL của thread là custom;
    // class chứa đoạn code vẫn có defining loader như trước.
} finally {
    thread.setContextClassLoader(old);
}
```

Đây là đoạn minh họa dùng Java 17; mỗi thread giữ TCCL của chính nó. TCCL của thread mới mặc định kế thừa từ thread tạo ra nó nếu không có thiết lập riêng; nó không tự đổi theo parent khi parent đã tạo thread con.

### Vì sao TCCL tồn tại?

Trong **parent delegation** thường gặp, loader con có thể hỏi parent nạp lớp; code do parent định nghĩa không tự nhìn xuống tất cả các class riêng của loader con. Một thư viện nền tảng có thể cần nạp provider do ứng dụng cung cấp mà không có tham chiếu trực tiếp tới loader ứng dụng. TCCL cho phép code ấy lấy loader theo ngữ cảnh.

Đây là ý nghĩa của `ServiceLoader.load(Service.class)`: overload tiện lợi này dùng TCCL. Nó không có nghĩa mọi API reflection/class loading đều tự động dùng TCCL.

## 3. API nào dùng loader nào?

| API | Nguồn quyết định lookup |
|---|---|
| `Class.forName("com.example.Foo")` | **Defining loader của class gọi**, đồng thời yêu cầu initialization |
| `Class.forName("com.example.Foo", false, chosen)` | Loader `chosen` được truyền tường minh |
| `chosen.loadClass("com.example.Foo")` | Loader `chosen` và chiến lược delegation của nó |
| `Foo.class` | Class literal được compiler/JVM liên kết theo ngữ cảnh class hiện tại |
| `Foo.class.getClassLoader()` | Chỉ **đọc defining loader** của runtime type `Foo` |
| `ServiceLoader.load(FooService.class)` | **TCCL** của thread hiện tại |
| `ServiceLoader.load(FooService.class, chosen)` | Loader `chosen` dùng cho việc tìm provider |
| `ServiceLoader.load(layer, FooService.class)` | `ModuleLayer` được chỉ định (và các layer cha theo API) |
| `chosen.getResourceAsStream("config/a.yml")` | Resource lookup qua loader `chosen` |

**Hai điểm hay nhầm:**

1. `Class.forName(String)` không mặc nhiên đọc `Thread.currentThread().getContextClassLoader()`. Theo Java SE 17, overload một tham số tương đương `Class.forName(name, true, currentLoader)`, trong đó `currentLoader` là defining loader của **class gọi**.
2. `ServiceLoader.load(Class<S>)` **có dùng TCCL**; nó tương đương `ServiceLoader.load(service, Thread.currentThread().getContextClassLoader())`.

Vì vậy, chỉ thấy method tên `load` hoặc `forName` là chưa đủ; phải kiểm tra overload thật và contract của API.

### Không tự suy diễn ý nghĩa của null

`null` có ngữ nghĩa **khác nhau** ở các API:

- `Class.forName(name, false, null)`: tìm qua **bootstrap loader**.
- `ClassLoader.getParent() == null`: trong class loader delegation phổ biến, parent API biểu diễn bootstrap.
- `SomeClass.class.getClassLoader() == null`: với ordinary class như `java.lang.String`, defining loader là bootstrap trên JDK thông thường; primitive/void/array có quy tắc riêng.
- `Thread.getContextClassLoader() == null`: tài liệu `Thread` quy định `null` biểu thị **system loader** hoặc bootstrap nếu không có system loader.
- `ServiceLoader.load(service, null)`: tài liệu `ServiceLoader` quy định sử dụng **system loader**, hoặc bootstrap nếu system loader không khả dụng.

Không truyền một loader nullable qua nhiều API với giả định tất cả đều dùng bootstrap. Nếu một anchor class được bootstrap định nghĩa và `getClassLoader()` trả `null`, phải xem lại contract cụ thể của API nhận giá trị ấy.

## 4. Khi dùng TCCL ngầm gây lỗi gì?

Giả sử một library có hàm:

```java
static Class<?> locate(String name)
        throws ClassNotFoundException {
    ClassLoader loader =
            Thread.currentThread().getContextClassLoader();
    return Class.forName(name, false, loader);
}
```

Đây là snippet minh họa phải đặt trong class. Đầu vào `name` giống nhau nhưng kết quả có thể khác:

- Thread chính thường có application loader và tìm thấy class của app.
- Thread được container quản lý có thể mang loader khác; class không tìm thấy.
- Worker thread của executor được tạo từ trước có thể giữ TCCL không liên quan đến request hiện tại.
- Một plugin đổi TCCL trước khi gọi library, làm cùng binary name resolve theo plugin.
- Unit test runner hoặc agent có thể tạo thread với TCCL khác môi trường production.
- Nếu TCCL `null` và code chuyển thẳng sang `Class.forName(name,false,null)`, lookup bị giới hạn theo quy tắc bootstrap của API đó.

Phụ thuộc vào TCCL là **dependency ẩn gắn với thread state**. Logic khó tái hiện và kiểm thử khi chuyển execution sang thread khác.

### TCCL không tự sửa type identity

Nếu loader A và B cùng định nghĩa `api.Message`, kết quả của `A.loadClass("api.Message")` và `B.loadClass("api.Message")` có thể là hai `Class<?>` khác nhau. Hai runtime types đó không tự tương thích chỉ vì cùng binary name.

Đặt TCCL theo A hoặc B có thể ảnh hưởng lookup, nhưng không biến hai class thành một. Với host/plugin chia sẻ interface hoặc DTO, nên định nghĩa **shared API** ở một loader chung và để các plugin resolve về cùng runtime class identity.

## 5. Quy tắc chọn explicit loader theo chủ sở hữu

### 5.1. Class thuộc cùng thư viện với API anchor

Nếu thư viện chứa class `com.example.lib.Helper`, có thể chọn loader của một class đã biết thuộc thư viện:

```java
ClassLoader libraryLoader = LibraryAnchor.class.getClassLoader();
Class<?> helper = Class.forName(
        "com.example.lib.Helper", false, libraryLoader);
```

Đây là mã minh họa: cần `LibraryAnchor` và `Helper` tồn tại. Cách này giảm phụ thuộc vào TCCL của worker hoặc HTTP request.

### 5.2. Implementation do plugin manager nạp

Nếu implementation thuộc plugin, **loader do plugin manager quản lý** mới là lựa chọn rõ ràng:

```java
public final class PluginFactory {
    private final ClassLoader implementationLoader;

    public PluginFactory(ClassLoader implementationLoader) {
        this.implementationLoader =
                java.util.Objects.requireNonNull(implementationLoader);
    }

    public Class<?> loadType(String binaryName)
            throws ClassNotFoundException {
        return Class.forName(binaryName, false, implementationLoader);
    }
}
```

Class này có thể lưu ở `PluginFactory.java`. Constructor thể hiện rõ dependency. Test có thể truyền loader thử nghiệm; khi plugin đóng, registry/factory có thể được giải phóng theo lifecycle.

Nếu plugin implementation sử dụng shared interface, cấu hình parent/delegation để implementation và host nhìn cùng runtime class của interface. Explicit loader chỉ đảm bảo đường lookup **được xác định**, không bỏ qua Java access checks, JPMS hoặc loading constraints.

### 5.3. Resource đi cùng một library

Nếu resource do cùng artifact với `LibraryAnchor` sở hữu, có thể dùng:

```java
try (java.io.InputStream in =
             LibraryAnchor.class.getResourceAsStream(
                     "/config/defaults.properties")) {
    if (in == null) {
        throw new java.io.FileNotFoundException("Resource missing");
    }
    // Đọc dữ liệu cấu hình.
}
```

Đoạn này cần đặt trong method khai báo `throws IOException` hoặc xử lý exception. Nếu resource do plugin sở hữu thì hãy dùng đúng `pluginLoader.getResourceAsStream(...)` thay vì anchor của host. Với named modules, resource encapsulation và quy tắc mở package vẫn được áp dụng.

### 5.4. Service provider trong hệ module

Nếu provider sống trong `ModuleLayer` được ứng dụng tạo và quản lý, có thể dùng:

```java
java.util.ServiceLoader<MyService> providers =
        java.util.ServiceLoader.load(layer, MyService.class);
```

Snippet minh họa cần biến `layer` và interface `MyService`. Trong named modules, cần cấu hình `uses` và `provides` cùng các quan hệ module thích hợp; explicit loader không bỏ qua quy tắc JPMS.

## 6. Java 17 chạy độc lập: TCCL bị đổi nhưng explicit lookup vẫn đúng

Tạo file `ExplicitLoaderDemo.java`:

```java
public class ExplicitLoaderDemo {
    public static void main(String[] args)
            throws ClassNotFoundException {

        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        ClassLoader app = ExplicitLoaderDemo.class.getClassLoader();
        ClassLoader isolated = new ClassLoader(null) { };

        try {
            thread.setContextClassLoader(isolated);
            System.out.println("TCCL is isolated: "
                    + (thread.getContextClassLoader() == isolated));

            boolean notFound = false;
            try {
                Class.forName("ExplicitLoaderDemo", false,
                        thread.getContextClassLoader());
            } catch (ClassNotFoundException expected) {
                notFound = true;
            }
            System.out.println("TCCL cannot find main: " + notFound);

            Class<?> viaExplicit =
                    Class.forName("ExplicitLoaderDemo", false, app);
            System.out.println("Explicit finds main: "
                    + (viaExplicit == ExplicitLoaderDemo.class));

            Class<?> viaCaller = Class.forName("ExplicitLoaderDemo");
            System.out.println("Caller forName finds main: "
                    + (viaCaller == ExplicitLoaderDemo.class));

            Class<?> viaBootstrap =
                    Class.forName("java.lang.String", false, null);
            System.out.println("Bootstrap finds String: "
                    + (viaBootstrap == String.class));

            System.out.println("Defining loader unchanged: "
                    + (viaExplicit.getClassLoader() == app));
        } finally {
            thread.setContextClassLoader(previous);
        }

        System.out.println("TCCL restored: "
                + (thread.getContextClassLoader() == previous));
    }
}
```

Chạy với JDK 17+, cấu hình class path thông thường:

```bash
javac ExplicitLoaderDemo.java
java ExplicitLoaderDemo
```

Stdout:

```text
TCCL is isolated: true
TCCL cannot find main: true
Explicit finds main: true
Caller forName finds main: true
Bootstrap finds String: true
Defining loader unchanged: true
TCCL restored: true
```

Giải thích:

1. `isolated` là custom loader có parent bằng `null`. Theo thuật toán mặc định, nó có thể ủy quyền tới bootstrap nhưng không tự đọc class ứng dụng trên application class path.
2. Truyền TCCL `isolated` cho `Class.forName` nên `ExplicitLoaderDemo` không được tìm thấy và `notFound` trở thành `true`.
3. Khi truyền `app` rõ ràng, lookup tìm cùng runtime class với `ExplicitLoaderDemo.class`.
4. Overload `Class.forName(String)` dùng loader của **class gọi**, nên không bị việc đổi TCCL làm lỗi.
5. `Class.forName(..., false, null)` minh họa bootstrap lookup của `String`.
6. `finally` phục hồi TCCL; đổi ngữ cảnh không đổi defining loader của `ExplicitLoaderDemo`.

**Điểm đáng nhớ:** cùng thread và cùng tên class nhưng chọn loader khác có thể tạo ra kết quả khác nhau. API nhận loader tường minh giúp caller kiểm soát được điều đó.

## 7. ServiceLoader: so sánh lookup theo TCCL và loader tường minh
`ServiceLoader` là ngoại lệ quan trọng: overload không nhận loader **cố ý** đọc TCCL. Khi biết plugin owner, ta có thể loại bỏ phụ thuộc vào trạng thái ambient của thread:

```java
// Snippet trong method; MyService và pluginLoader đã được khai báo.
ServiceLoader<MyService> ambient = ServiceLoader.load(MyService.class);
ServiceLoader<MyService> explicit =
        ServiceLoader.load(MyService.class, pluginLoader);
```

`ambient` phụ thuộc TCCL của **thread gọi**, còn `explicit` phụ thuộc chính loader truyền vào. Nếu TCCL của executor worker bị đổi, hai lời gọi có thể nhìn tập provider khác nhau. Descriptor `META-INF/services/<binary-service-name>` phải có thể được loader chỉ định tìm thấy; provider phải implement **cùng runtime service API** mà host dùng. Trong named modules có thêm `uses`/`provides` và overload nhận `ModuleLayer`. Đây là cách áp dụng nguyên tắc chọn loader ở phần 5 vào SPI; bài ServiceLoader cuối chương có project mẫu chạy đầy đủ.
## 8. Chọn loader tại ranh giới thread và executor
`new Thread` thường kế thừa TCCL tại lúc tạo. Một executor worker có thể sống lâu trước khi request được submit, nên TCCL của submitter **không** tự được truyền cho từng task. Ví dụ, nếu một plugin factory đã biết `pluginLoader`, nó có thể đưa loader đó vào constructor/parameter của task và gọi `Class.forName(name, false, pluginLoader)` thay vì dựa vào worker TCCL.

Nếu library bắt buộc sử dụng TCCL, đổi nó **bên trong worker đang thực thi** rồi restore `previous` trong `finally`. Không đổi TCCL của submitter rồi mặc định worker sẽ theo. Bài về thread inheritance, pool và finally trình bày wrapper/exception paths chi tiết.
## 9. Cách chuyển TCCL có trách nhiệm

Có những framework cũ chỉ đọc `Thread.currentThread().getContextClassLoader()` mà không có overload nhận loader. Khi đó nên dùng phạm vi hẹp:

```java
Thread current = Thread.currentThread();
ClassLoader previous = current.getContextClassLoader();

try {
    current.setContextClassLoader(pluginLoader);
    // Gọi framework cố ý dùng TCCL ngay trong phạm vi này.
} finally {
    current.setContextClassLoader(previous);
}
```

Đoạn trên minh họa với `pluginLoader` đã được chọn. Các nguyên tắc:

- Lưu và phục hồi TCCL trên **cùng thread**.
- Không giữ TCCL của plugin sau khi hoàn thành công việc.
- Với nested calls, mỗi cấp thay đổi nên lưu giá trị **ngay trước** khi đổi, không tự mặc định context cũ là system loader.
- Nếu tác vụ chuyển thread hoặc asynchronous callback, scope này **không tự lan** sang thread khác.
- Trong server chạy lâu, thread pool, listener, cache hoặc TCCL còn reference tới plugin loader có thể cản garbage collection/class unloading sau redeploy.

Không nên “reset” TCCL bằng `setContextClassLoader(null)` sau mọi callback: đó không nhất thiết là giá trị trước đó, và `null` có semantics theo API `Thread`. Hãy khôi phục chính xác `previous`.

## 10. Plugin factory và dependency injection của loader

Thay vì để mỗi method đọc TCCL, có thể đưa loader vào constructor:

```java
public final class ProviderRegistry {
    private final ClassLoader providerLoader;

    public ProviderRegistry(ClassLoader providerLoader) {
        this.providerLoader =
                java.util.Objects.requireNonNull(providerLoader);
    }

    public <S> java.util.ServiceLoader<S> providers(Class<S> service) {
        return java.util.ServiceLoader.load(service, providerLoader);
    }
}
```

Code này có thể lưu thành `ProviderRegistry.java`. Registry biết loader nào quản lý providers; thread gọi không làm thay lựa chọn.

Lợi ích:

- **Dễ kiểm thử:** test truyền test loader hoặc loader riêng cho fixture.
- **Có thể tái hiện lỗi:** biết chính xác loader đã dùng trong log và stack trace.
- **Hợp với cấu trúc plugin:** mỗi plugin có owner/lifecycle riêng.
- **Tránh nhiễm chéo thread:** không cần đổi TCCL cho những API có explicit overload.
- **Hạn chế leak:** registry có thể được giải phóng cùng plugin thay vì bị lưu vô hạn trong static cache của host.

Nhưng explicit loader **không tự bảo đảm** loader đó nhìn thấy mọi class cần thiết. Cần thiết kế delegation đúng với shared API và các providers.

### Không chọn system loader một cách máy móc

`ClassLoader.getSystemClassLoader()` thường phù hợp với app đơn giản chạy `java -cp`. Trong application server, plugin manager, test runner hoặc runtime modules, implementation có thể nằm ngoài visibility của system loader.

Hãy dựa vào **artifact owner**: API anchor, loader do plugin manager giao, loader của module/layer, hoặc provider loader của registry. Đừng chỉ vì code chạy trong app mà thay mọi TCCL bằng system loader.

## 11. Các lỗi phổ biến và cách sửa

| Sai lầm | Vì sao sai | Hướng xử lý |
|---|---|---|
| Tin `Class.forName(name)` dùng TCCL | Overload một tham số dùng loader của class gọi | Dùng overload ba tham số để chọn loader |
| Gọi `ServiceLoader.load(service)` trong worker mà không xét context | Provider lookup phụ thuộc TCCL worker | Truyền loader tường minh khi có owner |
| Đổi TCCL ở thread submit và cho rằng worker thấy ngay | Worker có TCCL riêng, thường được tái sử dụng | Đặt/restore trong worker hoặc bỏ phụ thuộc TCCL |
| Không restore TCCL | Tác vụ sau có thể dùng context sai, giữ class loader cũ | Dùng `try/finally` |
| Khôi phục bằng `null` thay vì giá trị cũ | Null có contract riêng và có thể thay đổi behavior | Lưu `previous` và restore chính nó |
| Dùng `getSystemClassLoader()` cho mọi provider | Provider có thể do loader khác định nghĩa | Nhận loader từ plugin/module owner |
| Dùng loader của library API để tìm implementation ở child plugin | Parent không mặc định nhìn thấy child classes | Dùng plugin loader được truyền tường minh |
| Có hai bản shared interface cùng binary name | Runtime identities khác nhau | Định nghĩa shared API một lần ở common loader |
| Nghĩ explicit loader bỏ qua module access | JPMS và Java access vẫn áp dụng | Khai báo `uses`, `provides` và kiểm tra exports/opens |
| Giữ `Class<?>` hoặc `ServiceLoader` plugin trong singleton mãi | Reference có thể cản class unloading | Quản lý cache và registry theo plugin lifecycle |
| Coi `null` ở mọi API là bootstrap | `Class.forName`, `ServiceLoader`, `Thread` xử lý khác nhau | Đọc tài liệu từng overload |

### Khi nào vẫn nên dùng TCCL?

TCCL là lựa chọn hợp lệ khi:

- Framework công khai hợp đồng “tìm implementation theo thread context”.
- Caller biết rõ thread nào chạy lookup và đặt đúng context.
- Có cơ chế **restore trong finally** và quản lý lifecycle.
- Không lấy một ServiceLoader theo TCCL của tenant này rồi cache dùng chung vô hạn cho tenant khác.
- Shared API có class identity nhất quán với provider loader.

Trong trường hợp đã biết loader cụ thể ngay khi xây dựng component, explicit loader thường dễ bảo trì hơn.

## 12. Bài tập tự kiểm tra

**Câu 1.** `Class.forName("example.Task")` có tự dùng TCCL không?

**Đáp án:** Không. Overload một tham số dùng loader của class gọi. Muốn truyền loader cụ thể, dùng `Class.forName(name, initialize, loader)`.

**Câu 2.** `ServiceLoader.load(MyService.class)` dùng loader nào?

**Đáp án:** TCCL của thread hiện tại theo contract Java 17. Overload `ServiceLoader.load(MyService.class, chosenLoader)` dùng loader chỉ định.

**Câu 3.** Vì sao cùng method có thể tìm thấy class ở main thread nhưng không thấy ở executor worker?

**Đáp án:** Nếu method chọn TCCL, mỗi thread có context khác nhau; worker có thể được tạo từ trước hoặc do container quản lý.

**Câu 4.** Khi dùng thư viện chỉ hỗ trợ TCCL, phải đổi context ở thread nào?

**Đáp án:** Thread thực sự gọi thư viện và thực hiện lookup, lưu giá trị cũ và restore trong `finally`.

**Câu 5.** Nếu `SomeClass.class.getClassLoader()` và TCCL khác nhau, điều gì xảy ra với defining loader của `SomeClass`?

**Đáp án:** Không thay đổi. Defining loader thuộc runtime class identity; TCCL chỉ là trạng thái lookup theo thread.

**Câu 6.** `Class.forName(name,false,null)` và `ServiceLoader.load(service,null)` có luôn cùng ý nghĩa không?

**Đáp án:** Không. Overload của `Class.forName` dùng bootstrap khi loader null; `ServiceLoader.load(service,null)` dùng system loader, hoặc bootstrap khi không có system loader.

**Câu 7.** Một plugin provider implements interface `api.Plugin` trùng binary name nhưng do loader riêng định nghĩa. Host có chắc cast sang `api.Plugin` của host được không?

**Đáp án:** Không. Cần cùng runtime interface identity, thường bằng cách các plugin ủy quyền shared API type cho common loader.

**Câu 8.** TCCL từ một plugin tồn tại trong worker thread pool sau khi plugin đã dừng có thể gây gì?

**Đáp án:** Có thể giữ loader và class metadata của plugin reachable, làm hot reload/unloading không giải phóng tài nguyên như kỳ vọng.

## 13. Ghi nhớ và tài liệu chính thức

- **TCCL** cung cấp loader theo ngữ cảnh thread; hữu ích cho SPI nhưng dễ thành dependency ẩn trong executor/server/plugin.
- **Explicit loader** là tham số/cấu hình thể hiện chính xác owner của class hoặc provider, hỗ trợ debug và kiểm thử.
- **Class.forName một tham số** dùng loader của class gọi; **ServiceLoader.load một tham số** dùng TCCL.
- **Đổi TCCL phải có phạm vi và restore**, đặc biệt với thread pool tái sử dụng worker.
- **Loader visibility khác runtime type identity**: provider phải dùng cùng shared API runtime types với host.
- **Null không đồng nghĩa nhau ở mọi API**; đọc contract của overload sử dụng.

Nguồn chính thức:

1. [Java SE 17 — Thread.getContextClassLoader và setContextClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html).
2. [Java SE 17 — Class.forName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html).
3. [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html).
4. [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html).
5. [JVMS 17 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-class-loader-005">Truyền và kế thừa context class loader khi tạo luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền và kế thừa context class loader khi tạo luồng

> **Java Core → ClassLoader → Loader ngữ cảnh.** Bài học giải thích cách **thread context class loader (TCCL)** được thừa hưởng khi tạo thread, cách đặt và khôi phục loader, sự khác biệt giữa tạo thread mới và chạy task trong thread pool, và những lưu ý với virtual threads. Các ví dụ độc lập dùng Java 17+; phần virtual threads yêu cầu Java 21+.

## 1. Context class loader là gì?

Mỗi đối tượng `Thread` có một **context class loader** có thể đọc và cập nhật:

~~~java
Thread thread = Thread.currentThread();
ClassLoader context = thread.getContextClassLoader();
thread.setContextClassLoader(context);
~~~

Mục đích thường gặp: mã thư viện/framework nạp bởi loader tầng cha có thể dùng loader ngữ cảnh để tìm classes, resources hoặc service providers thuộc ứng dụng/plugin đang gọi nó. Đây là cách **đảo chiều hướng tìm kiếm** so với việc luôn chỉ dựa vào defining loader của chính thư viện.

Phân biệt ba khái niệm:

| Thuật ngữ | Nghĩa | Có tự động giống TCCL? |
| --- | --- | --- |
| **Defining class loader** | Loader trực tiếp định nghĩa runtime class; một thành phần của class identity | Không |
| **Initiating class loader** | Loader mà JVM ghi nhận đã khởi xướng load thành công class, có thể qua delegation | Không |
| **Thread context class loader (TCCL)** | Một tham chiếu `ClassLoader` gắn với từng `Thread` để code lựa chọn dùng khi tìm class/resource | Không |

`Thread.currentThread().getContextClassLoader()` không nhất thiết bằng `SomeLibrary.class.getClassLoader()`, `ClassLoader.getSystemClassLoader()` hoặc loader đã tạo `Runnable`. Giá trị `null` ở API này có ý nghĩa fallback tới system class loader (hoặc bootstrap loader nếu không có system loader) theo tài liệu `Thread`; đừng tùy tiện suy ra `null` nghĩa là lỗi.

**Quan trọng:** TCCL **không chuyển** class sang defining loader mới. Nó chỉ là tham chiếu mà những API chủ động dùng TCCL có thể truy vấn. `SomeClass.class` và method bytecode resolution không đổi loader chỉ vì bạn set TCCL.

## 2. Quy tắc mặc định: kế thừa tại thời điểm **tạo Thread**

Với các public `Thread` constructors thông thường, **thread mới kế thừa context class loader từ thread đang tạo nó**. Đây là một trường hợp đặc biệt của cơ chế thừa hưởng initial values của inheritable-thread-locals được tài liệu `Thread` mô tả.

~~~text
Thread A đang chạy
    TCCL = loaderA
          |
          | new Thread(task)  <-- lấy trạng thái tại thời điểm tạo
          v
Thread B được tạo
    TCCL ban đầu = loaderA
          |
          | B.start()          <-- chỉ lập lịch thực thi; không lấy lại TCCL
          v
Thread B chạy task dưới TCCL ban đầu
~~~

Từ khóa chính là **khi `new Thread(...)` xảy ra**, không phải khi `start()` được gọi hoặc khi scheduler thật sự cho thread chạy.

Nếu thread A đổi TCCL **sau** khi tạo B nhưng **trước** khi start B, B không tự thay đổi theo A. Nếu sau này A đổi nữa thì B cũng không tự cập nhật. Các thread lưu tham chiếu TCCL riêng, không chia sẻ một biến TCCL duy nhất.

### 2.1. Ví dụ kiểm chứng creation-time inheritance, Java 17+

Tệp **TcclAtCreation.java**:

~~~java
public class TcclAtCreation {
    public static void main(String[] args)
            throws InterruptedException {
        Thread parent = Thread.currentThread();
        ClassLoader original = parent.getContextClassLoader();
        ClassLoader marker = new ClassLoader(original) { };

        try {
            parent.setContextClassLoader(marker);
            Thread child = new Thread(() -> {
                System.out.println("child inherited marker="
                    + (Thread.currentThread()
                        .getContextClassLoader() == marker));
            });

            // Thay đổi ở thread cha SAU khi child đã được tạo.
            parent.setContextClassLoader(original);
            System.out.println("parent restored="
                + (parent.getContextClassLoader() == original));

            child.start();
            child.join();
        } finally {
            parent.setContextClassLoader(original);
        }
    }
}
~~~

~~~bash
javac --release 17 TcclAtCreation.java
java -cp . TcclAtCreation
~~~

Stdout:

~~~text
parent restored=true
child inherited marker=true
~~~

Giải thích: việc tạo `child` dưới TCCL `marker` đã chụp giá trị này vào initial context của child. Khôi phục loader của parent trước `start()` không đổi TCCL đã có của child. `join()` làm chương trình chờ child hoàn tất nên output có thứ tự ổn định.

`new ClassLoader(original) { }` chỉ tạo loader **làm marker** để so sánh identity; nó không thực sự nạp class từ plugin JAR. Không nên nhầm minh họa truyền loader reference với một thử nghiệm định nghĩa class mới.

### 2.2. Gán TCCL tường minh cho thread đích

Thay vì dựa vào thời điểm tạo, bạn có thể set trên đối tượng thread mới trước khi start:

~~~java
Thread worker = new Thread(task);
worker.setContextClassLoader(pluginLoader);
worker.start();
~~~

Đây là snippet cần biến `task` và `pluginLoader` ở scope phù hợp. Nó mô tả một **policy rõ ràng hơn** cho ứng dụng cần loader nhất quán. Nếu worker đã chạy, thay TCCL giữa chừng có thể gây race về lúc thư viện bên trong gọi `getContextClassLoader()`; cấu hình trước `start()` thường dễ kiểm soát hơn.

### 2.3. Khác với `ThreadLocal` thường

- `ThreadLocal<T>` thông thường giữ giá trị riêng mỗi thread và **không mặc định truyền** sang child.
- `InheritableThreadLocal<T>` sao chép/tạo initial child values tại **thread construction**, không truyền động mọi cập nhật về sau.
- **TCCL** được `Thread` xử lý như một special inheritable thread-local value; hiểu nguyên tắc thời điểm kế thừa giúp tránh nhầm với ThreadLocal bình thường.
- Kế thừa TCCL **không đồng nghĩa sao chép toàn bộ trạng thái của classloader**. Hai threads có thể giữ cùng một tham chiếu loader, dùng chung classes đã được loader định nghĩa.

**Chú ý:** ClassLoader là object reference; “truyền” TCCL không clone bytecode, không tạo loader mới và không thay quyền truy cập Java module.

## 3. Trong trường hợp nào TCCL được sử dụng?

Nhiều thư viện sử dụng TCCL có chủ đích để tìm resources/SPI/providers. Ví dụ:

~~~java
ClassLoader loader =
        Thread.currentThread().getContextClassLoader();

Class<?> providerClass =
        loader.loadClass("example.plugin.Provider");
~~~

Đây là **snippet**, sẽ ném `NullPointerException` nếu `loader` thực sự null và gọi instance method trực tiếp; ứng dụng cần quyết định fallback trước khi sử dụng. Binary name là tên minh họa, không có sẵn class trong Java SE.

### 3.1. `ServiceLoader`

API `ServiceLoader.load(Service.class)` trong ngữ cảnh thông thường sử dụng **TCCL của thread hiện tại** làm class loader tìm providers. API overload như `ServiceLoader.load(Service.class, explicitLoader)` cho phép chủ động chọn loader, và JPMS còn có `ServiceLoader.load(layer, Service.class)` khi làm việc với module layer.

Nếu host và plugin có hai loaders:

- Contract/interface `Service` nên được chia sẻ bằng **cùng runtime class identity**; nếu mỗi loader tự define tên interface giống nhau, provider có thể bị từ chối vì type mismatch.
- TCCL có thể giúp thư viện tầng cha truy tìm plugin implementation theo caller context.
- Việc đưa provider vào JAR hoặc cấu hình `META-INF/services`/module `provides` phải đúng; TCCL không làm provider thiếu xuất hiện.
- Nếu một thread trong pool giữ TCCL của plugin A khi xử lý request plugin B, provider discovery có thể nhìn nhầm plugin.

### 3.2. Resource lookup

~~~java
ClassLoader tccl =
        Thread.currentThread().getContextClassLoader();
var url = tccl == null
        ? ClassLoader.getSystemResource("config/app.properties")
        : tccl.getResource("config/app.properties");
~~~

Đây là snippet minh họa, không phải tệp Java độc lập. `getResource` dùng logical resource name thường có dấu phân cách `/`. Resource trong named modules còn chịu module encapsulation; việc TCCL có thể nhìn class không đảm bảo mọi resource đều accessible. Một resource trả về `null` có thể do không tồn tại hoặc quyền module/loader, không nhất thiết do sai path vật lý.

### 3.3. Không nhầm TCCL với Java module access

Đổi TCCL **không tự cấp** `requires`/`exports`/`opens` cho named module. Class có thể được tìm bởi một loader nhưng caller vẫn bị giới hạn bởi accessibility rules hoặc class identity constraints. Với plugin isolation, phải thiết kế đồng thời loader boundary, module graph và shared API.

## 4. Thread pool: task mới không đồng nghĩa thread mới
Executor thường tái sử dụng worker có từ trước. Nếu caller đổi TCCL sang plugin A rồi submit task, worker có thể vẫn giữ context cũ (base hoặc plugin B). `submit()` không có hợp đồng chụp lại TCCL ở mỗi tác vụ; dù worker được tạo khi caller giữ plugin A, nó cũng có thể tiếp tục giữ A ở các tác vụ đến sau.

```text
creator TCCL=A → new Thread(worker) → worker ban đầu có TCCL=A
creator đổi TCCL=B → executor.submit(job) → worker vẫn có thể giữ A
```

Thay TCCL của worker mà quên restore còn gây nhiễm context giữa các tasks và kéo dài reference tới plugin. Muốn thực sự truyền context, capture loader ở boundary submit theo ownership của tác vụ, rồi áp dụng bên trong worker với wrapper ở phần 5; hoặc dùng API nhận `ClassLoader` tường minh. Bài “Thread pool và ngữ cảnh có thể khác luồng gửi công việc” có chương trình Java 17 kiểm chứng plain/wrapped/restored tasks.
## 5. Cách truyền TCCL vào task và khôi phục an toàn

Khi công việc phải chạy qua executor có worker được tái sử dụng, hãy **capture desired loader lúc submit** và thực thi task dưới loader đó với wrapper. Ví dụ một helper chuẩn cho Java 17+:

~~~java
import java.util.concurrent.Callable;

public final class ContextTasks {
    private ContextTasks() {}

    public static <T> Callable<T> withLoader(
            ClassLoader desired, Callable<T> operation) {
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

Tệp `ContextTasks.java` độc lập biên dịch được. Sử dụng từ code ứng dụng đã có `ExecutorService executor`:

~~~java
ClassLoader desired = Thread.currentThread().getContextClassLoader();

var result = executor.submit(ContextTasks.withLoader(desired, () -> {
    ClassLoader actual = Thread.currentThread().getContextClassLoader();
    return actual == desired;
}));
System.out.println(result.get());
~~~

Đây là **snippet caller**, yêu cầu biến `executor` và handling `Exception` phù hợp. Khi task được thực thi, wrapper set TCCL rồi luôn restore loader cũ trong `finally`. Kể cả `operation.call()` ném exception, worker không bị bỏ lại với desired context.

### 5.1. Biến thể `Runnable`

~~~java
static Runnable withLoader(ClassLoader desired, Runnable operation) {
    return () -> {
        Thread worker = Thread.currentThread();
        ClassLoader old = worker.getContextClassLoader();
        try {
            worker.setContextClassLoader(desired);
            operation.run();
        } finally {
            worker.setContextClassLoader(old);
        }
    };
}
~~~

Đây là **method minh họa** cần đặt trong class phù hợp. Tạo wrapper tại nơi nhận task/boundary của framework để policy nhất quán. Nếu hệ thống có context propagation API đã được framework hỗ trợ, ưu tiên dùng cơ chế chung ấy để tránh bỏ sót nested tasks và context khác như tracing, tenant, security.

**Tránh capture nhầm loader:** `getContextClassLoader()` tại submitter có thể đã bị một tác vụ khác làm bẩn. Trong hệ plugin, xác định loader từ **chủ sở hữu plugin/request** rõ ràng và restore cả caller nếu chính caller cũng đã thay đổi.

### 5.2. ThreadFactory cố định TCCL cho workers

Nếu mọi task trong một pool đều thuộc **một plugin**, có thể chọn TCCL của worker ngay trong ThreadFactory:

~~~java
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

class PluginFactoryExample {
    static ThreadFactory factory(ClassLoader pluginLoader) {
        ThreadFactory base = Executors.defaultThreadFactory();
        return command -> {
            Thread thread = base.newThread(command);
            thread.setContextClassLoader(pluginLoader);
            return thread;
        };
    }
}
~~~

Snippet là class Java độc lập để tạo factory; không có `main`. Có thể dùng `Executors.newFixedThreadPool(2, factory(pluginLoader))` khi loader sống cùng pool. Nhưng với pool dùng chung nhiều plugins, **không gắn cố định loader của một plugin cho tất cả tasks**; dùng wrapper có restore per-task hoặc thiết kế pool phân vùng rõ.

Khi dừng plugin cần shutdown pool/worker, hủy callbacks và clear references; một active worker có TCCL trỏ tới pluginLoader có thể giữ plugin cũ trong bộ nhớ.

## 6. Virtual threads (JDK 21+) và inheritance

Từ Java 21, virtual threads được hỗ trợ chính thức. `Thread.ofVirtual()`/`Thread.ofPlatform()` builder có method:

~~~java
inheritInheritableThreadLocals(boolean inherit)
~~~

Theo API, default thường là **inherit=true**, và TCCL là một trong các inherited context values ở **thread creation time**. Có thể tắt inheritance rõ ràng:

~~~java
Thread child = Thread.ofVirtual()
    .inheritInheritableThreadLocals(false)
    .unstarted(task);
~~~

Đây là snippet cần biến `task`. Khi chọn false, đừng dựa vào việc TCCL sẽ tự bằng loader của parent; nếu cần policy xác định, set TCCL hoặc bọc task một cách rõ ràng.

### 6.1. Ví dụ virtual thread kế thừa TCCL (Java 21+)

Tệp **VirtualTccl.java**:

~~~java
public class VirtualTccl {
    public static void main(String[] args) throws InterruptedException {
        Thread creator = Thread.currentThread();
        ClassLoader saved = creator.getContextClassLoader();
        ClassLoader marker = new ClassLoader(saved) { };

        try {
            creator.setContextClassLoader(marker);
            Thread child = Thread.ofVirtual().unstarted(() ->
                System.out.println("virtualInherited=" +
                    (Thread.currentThread()
                        .getContextClassLoader() == marker)));

            creator.setContextClassLoader(saved);
            child.start();
            child.join();
        } finally {
            creator.setContextClassLoader(saved);
        }
    }
}
~~~

~~~bash
javac --release 21 VirtualTccl.java
java -cp . VirtualTccl
~~~

Stdout:

~~~text
virtualInherited=true
~~~

Tương tự platform thread, virtual thread **không lấy TCCL từ carrier thread** mỗi khi được scheduler gắn lên một carrier. `Thread.currentThread()` trong task trả về virtual thread, nên TCCL thuộc virtual thread. Nếu carrier đổi/virtual thread unmount rồi remount, context class loader của virtual thread không vì thế biến thành TCCL của carrier.

Với task chạy qua `Executors.newVirtualThreadPerTaskExecutor()`, mỗi task có một virtual thread mới, nhưng vẫn nên rõ chính sách inheritance/capture. Virtual threads giúp tránh reuse cùng thread giữa các tasks, không loại bỏ toàn bộ rủi ro giữ loader khi task sống quá lâu hoặc khi các async boundaries khác dùng pools.

## 7. Khi nào không nên mặc định kế thừa?

Trong một số hệ thống multi-tenant, plugin sandbox, extension runtime hoặc long-lived background jobs, kế thừa TCCL từ creator có thể không phù hợp vì:

- Creator có thể đang xử lý một plugin cụ thể trong khi background thread dự kiến phục vụ toàn hệ thống.
- Không muốn worker giữ strong reference đến plugin loader sau plugin unload.
- Muốn worker chỉ thấy host/shared APIs hoặc module layer cụ thể.
- Cần tránh “context tràn” giữa requests ở pools và async pipelines.

Cách xử lý tùy thiết kế:

1. Khi tạo worker, set TCCL tường minh trong ThreadFactory; dùng **host loader** nếu worker thuộc host.
2. Khi chạy callback/plugin operation, bọc theo desired loader và restore trong `finally`.
3. Với Thread.Builder của JDK phù hợp, cân nhắc `inheritInheritableThreadLocals(false)` rồi set TCCL theo policy đã định.
4. Đừng truyền `ClassLoader` chỉ để dùng như cơ chế phân quyền bảo mật: TCCL không thay Java access checks, module rules hoặc isolation được kiểm chứng.
5. Khi unload plugin, shutdown workers, gỡ callbacks/listeners, release caches và tham chiếu class/loader; theo dõi thread dumps nếu loader còn bị giữ.

**Đặc biệt:** `setContextClassLoader(null)` có ý nghĩa API rõ ràng (system loader hoặc fallback), không bảo đảm mọi thư viện dùng null đúng như nhau khi nó tự gọi `getContextClassLoader().loadClass(...)`; thư viện cần xử lý null một cách có chủ đích.

## 8. Lỗi thường gặp và cách chẩn đoán

| Biểu hiện | Nguyên nhân có thể | Cách kiểm tra/sửa |
| --- | --- | --- |
| Child thread thấy loader cũ dù parent đổi trước start | Inheritance xảy ra ở construction | Set TCCL trước `new Thread` hoặc set trên child |
| Task executor không thấy loader của submitter | Worker được tạo trước hoặc được tái sử dụng | Wrap task hoặc ThreadFactory policy |
| Task B tìm provider của plugin A | Task A bỏ quên restore | `try/finally` trong mọi context switch |
| Plugin hot reload không unload class metadata | Worker/ThreadLocal/TCCL giữ loader cũ | Shutdown worker và dọn references |
| `ClassNotFoundException` ở ServiceLoader/resource lookup | TCCL sai hoặc service config thiếu | Xác nhận TCCL, SPI metadata, module layer |
| Cast lỗi dù class cùng tên | Hai defining loaders khác nhau | Kiểm tra class identity; chia sẻ host interface |
| Set TCCL xong named module vẫn bị chặn access | TCCL không sửa readability/exports/opens | Kiểm tra JPMS graph |
| Virtual thread có TCCL khác dự kiến | Builder inheritance setting hoặc capture timing | Kiểm tra builder/factory và set explicit |

### Cách log tối thiểu

~~~java
Thread current = Thread.currentThread();
ClassLoader tccl = current.getContextClassLoader();

System.out.println("thread=" + current.getName());
System.out.println("TCCL=" + tccl);
System.out.println("definingLoader="
    + getClass().getClassLoader());
~~~

Đây là **snippet trong instance method**; khi dùng trong static context thay `getClass()` bằng một class literal phù hợp. `ClassLoader.toString()` là diagnostic, không phải stable ID hay bằng chứng hai loaders nạp cùng bytecode. Khi cần, kiểm tra thêm `clazz.getModule()`, runtime jar/version và context propagation boundaries.

## 9. Câu hỏi tự kiểm tra

1. TCCL của child `new Thread(...)` được lấy lúc nào? **Khi tạo Thread object**, trước `start()`.
2. Parent đổi TCCL sau khi tạo child, child có tự đổi không? **Không**.
3. TCCL và defining loader của class luôn giống nhau? **Không**.
4. Executor tái sử dụng worker có tự nhận TCCL của caller ở mỗi `submit`? **Không**.
5. Vì sao `finally` bắt buộc khi wrapper task đổi TCCL? **Để worker không giữ context của task trước khi có exception**.
6. TCCL được kế thừa giống mọi ThreadLocal bình thường? **Không**, nó được mô tả như special inheritable thread-local.
7. Một thread không có TCCL và trả về null có nghĩa classloading bất khả thi? **Không**, API có fallback rule.
8. Set TCCL có cho module A đọc package chưa export của B không? **Không**.
9. Virtual thread lấy TCCL theo carrier mỗi lần schedule? **Không**, TCCL thuộc virtual Thread object.
10. Muốn thread không kế thừa inherited context theo JDK 21 builder, dùng gì? **`inheritInheritableThreadLocals(false)`**.
11. Hot-reload plugin không unload dù đã đóng URLClassLoader: vì sao? **Có thể thread/pool giữ strong reference qua TCCL**.
12. Nên lưu plugin interface ở loader nào khi dùng ServiceLoader xuyên host/plugin? **Ở defining loader chung để tránh hai runtime types cùng tên**.

## 10. Nguồn tham khảo chuẩn

1. [Thread — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html): `getContextClassLoader`, `setContextClassLoader` và quy tắc inheritance at thread creation.
2. [Thread.Builder — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.Builder.html): `inheritInheritableThreadLocals`, platform/virtual thread builders.
3. [InheritableThreadLocal — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/InheritableThreadLocal.html): giá trị initial được kế thừa.
4. [Executors — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/Executors.html): thread pools, ThreadFactory, virtual thread-per-task.
5. [ServiceLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html): lựa chọn loader/provider discovery.
6. [ClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): defining loader, resource lookup, delegation và module encapsulation.

**Ghi nhớ:** `new Thread` thường kế thừa TCCL **tại thời điểm tạo**, thread pools tái sử dụng context của **worker**, và virtual threads tuân theo chính sách inheritance của builder/factory. Với async code và plugin systems, xác định desired loader, truyền vào execution boundary rồi luôn **restore trong `finally`**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-class-loader-006">Thread pool và ngữ cảnh có thể khác luồng gửi công việc</a>

<details>
<summary>Xem chi tiết</summary>

# Thread pool và ngữ cảnh có thể khác luồng gửi công việc

Khi ứng dụng Java đưa công việc từ một thread sang `ExecutorService` hoặc thread pool, **thread thực sự thực thi công việc có thể có context class loader (TCCL) khác thread gửi công việc**. Lệnh `submit()` không có hợp đồng tự động chuyển `Thread.currentThread().getContextClassLoader()` từ bên gửi sang worker trong mỗi lần gọi. Worker có thể được tạo từ trước, tồn tại lâu và thực hiện nhiều tác vụ của những thành phần khác nhau.

Điểm này ảnh hưởng các thư viện tìm class, resource hoặc provider dựa trên TCCL: chương trình có thể hoạt động khi gọi trực tiếp nhưng mất provider khi chạy nền; một worker giữ TCCL của plugin cũ cũng có thể gây rò rỉ class loader sau hot redeploy. Bài học giải thích cơ chế, trình bày ví dụ Java 17 chạy độc lập và cách **capture → set → execute → restore** an toàn.

## 1. TCCL là gì?

Mỗi `Thread` có một **thread context class loader**. Hai API quan trọng:

~~~java
ClassLoader current =
        Thread.currentThread().getContextClassLoader();

Thread.currentThread().setContextClassLoader(current);
~~~

Đây là **fragment minh họa**; nó đọc rồi đặt lại cùng giá trị nên không làm thay đổi hành vi. TCCL có thể bằng `null` và không phải mọi API đều sử dụng nó.

TCCL thường giúp code thư viện tìm implementation do ứng dụng hoặc plugin cung cấp. Một số tình huống:

- `ServiceLoader.load(MyService.class)` thường dùng TCCL hiện tại để khám phá service provider theo quy tắc API.
- Framework đọc `META-INF/services/...`, resource hoặc cấu hình thông qua `Thread.currentThread().getContextClassLoader().getResource(...)`.
- Application server đặt TCCL phù hợp trước khi gọi code của web application hoặc plugin.

**Phân biệt TCCL với defining loader.** `SomeClass.class.getClassLoader()` cho biết class loader **đã định nghĩa** `SomeClass`; thay TCCL không làm class này đổi defining loader hay sửa `ClassCastException` giữa hai định nghĩa kiểu cùng tên.

| Tình huống | Class loader nào được sử dụng? |
| --- | --- |
| `ServiceLoader.load(Service.class)` | Mặc định dựa vào TCCL của thread gọi. |
| `ServiceLoader.load(Service.class, chosenLoader)` | Dùng loader truyền tường minh, trong khuôn khổ quy tắc module/provider. |
| `Class.forName(name, true, chosenLoader)` | Dùng loader được truyền vào. |
| `Class.forName(name)` | Dùng cơ chế của caller, không mặc định chuyển sang TCCL. |
| `MyClass.class` | Tham chiếu kiểu được JVM liên kết, không tự đổi theo TCCL. |
| `ClassLoader.getSystemClassLoader()` | System/application class loader, không nhất thiết là TCCL. |

Muốn hiểu vì sao thread pool làm khác kết quả, cần xác định code bên trong task **thực sự đang dùng API tra cứu nào**.

## 2. Thời điểm kế thừa context loader

Khi tạo một `Thread` mới trong điều kiện thông thường, TCCL ban đầu được **kế thừa từ thread tạo nó**, trừ khi ứng dụng, `ThreadFactory` hoặc môi trường thay đổi tường minh. Việc kế thừa diễn ra khi **tạo thread**, không phải khi chạy từng `Runnable` hoặc nhận từng `Callable`.

~~~text
Submitter S                    Worker W trong thread pool
TCCL = PluginLoader              đã tạo trước đó
      |                           TCCL = BaseLoader
      |                                    |
      +-- executor.submit(job) ----------> |
                                           v
                                        job chạy
                                           |
                                           v
                                   TCCL = BaseLoader
                                     (khác submitter)
~~~

Tại thời điểm `submit`:

1. Executor nhận và lên lịch công việc. Nó có thể đặt task vào queue thay vì tạo thread.
2. Worker được chọn có thể đã chạy những task khác, hoặc được tạo bởi `ThreadFactory` trên một thread hoàn toàn khác.
3. Nếu task gọi `Thread.currentThread().getContextClassLoader()` thì giá trị là **TCCL hiện tại của worker**, không tự sao chép của submitter.
4. Nếu task thay TCCL của worker mà quên khôi phục, **task tiếp theo** có thể thấy loader không liên quan.
5. `Future.get()` chỉ đợi kết quả hoặc exception; nó không truyền TCCL từ thread gọi sang worker.

### Default thread factory có giải quyết không?

`new Thread(...)` và default `ThreadFactory` thường kế thừa TCCL **tại lúc tạo worker**. Pool có thể tạo worker khi nhận task đầu hoặc khi cần mở rộng, nhưng **không đảm bảo worker mới cho mỗi lần submit**. Nếu pool có một worker tạo lúc Thread A đang giữ PluginA, task của Thread B gửi sau này có thể tiếp tục thấy PluginA thay vì PluginB. Không nên dựa vào submitter đầu tiên, bởi pool có thể khởi tạo worker sớm, tái tạo worker hoặc dùng factory riêng.

## 3. Ví dụ Java 17 chạy được và có output xác định

Ví dụ tạo một `URLClassLoader` tượng trưng cho plugin và một executor một worker. `ThreadFactory` thiết lập TCCL **cố định là system class loader** cho worker, giúp chứng minh sự khác biệt mà không phụ thuộc thời điểm tạo thread.

Tạo tệp **`ThreadPoolContextDemo.java`**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ThreadPoolContextDemo {
    private static <T> Callable<T> withContextLoader(
            ClassLoader desired, Callable<T> action) {

        return () -> {
            Thread worker = Thread.currentThread();
            ClassLoader previous = worker.getContextClassLoader();
            worker.setContextClassLoader(desired);

            try {
                return action.call();
            } finally {
                worker.setContextClassLoader(previous);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ClassLoader baseLoader = ClassLoader.getSystemClassLoader();

        try (URLClassLoader pluginLoader =
                     new URLClassLoader(new URL[0], baseLoader)) {

            ThreadFactory factory = job -> {
                Thread worker = new Thread(job, "context-worker");
                worker.setContextClassLoader(baseLoader);
                return worker;
            };

            ExecutorService pool =
                    Executors.newSingleThreadExecutor(factory);

            Thread submitter = Thread.currentThread();
            ClassLoader previousSubmitterLoader =
                    submitter.getContextClassLoader();

            try {
                submitter.setContextClassLoader(pluginLoader);

                System.out.println("Submitter uses plugin: "
                        + (submitter.getContextClassLoader() == pluginLoader));

                boolean plain = pool.submit(() ->
                        Thread.currentThread().getContextClassLoader()
                                == pluginLoader).get();
                System.out.println("Plain task uses plugin: " + plain);

                boolean wrapped = pool.submit(
                        withContextLoader(pluginLoader, () ->
                                Thread.currentThread().getContextClassLoader()
                                        == pluginLoader)).get();
                System.out.println("Wrapped task uses plugin: " + wrapped);

                boolean restored = pool.submit(() ->
                        Thread.currentThread().getContextClassLoader()
                                == baseLoader).get();
                System.out.println("Worker restored base: " + restored);

            } finally {
                submitter.setContextClassLoader(previousSubmitterLoader);
                pool.shutdown();

                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            }
        }
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 ThreadPoolContextDemo.java
java ThreadPoolContextDemo
~~~

**Stdout chính xác:**

~~~text
Submitter uses plugin: true
Plain task uses plugin: false
Wrapped task uses plugin: true
Worker restored base: true
~~~

Giải thích từng kết quả:

1. `pluginLoader` và `baseLoader` là hai object khác nhau. `new URL[0]` có nghĩa ví dụ **không thực sự tải lớp của plugin**, mà chỉ kiểm tra TCCL identity một cách độc lập, dễ tái hiện.
2. Submitter set TCCL thành `pluginLoader`. Vì thế dòng đầu bằng `true`.
3. `ThreadFactory` tạo worker với TCCL cố định `baseLoader`; task gửi theo `submit` thông thường vẫn thấy base, nên dòng thứ hai bằng `false`.
4. `withContextLoader` lưu ngữ cảnh cũ của worker, tạm đặt ngữ cảnh plugin rồi chạy `action.call()`. Dòng thứ ba bằng `true`.
5. `finally` của wrapper khôi phục đúng TCCL cũ ngay cả khi action ném exception. Task tiếp theo thấy base và dòng cuối bằng `true`.
6. `finally` ở `main` khôi phục TCCL submitter, shutdown pool và đợi worker dừng. `try-with-resources` đóng tài nguyên `URLClassLoader`. Việc đóng loader **không ép JVM unload mọi class tức thì**.

**Ý nghĩa thực tế:** TCCL của worker có thể khác submitter ngay cả khi task do submitter đó gửi. Nếu một library gọi `ServiceLoader` hay tìm resource bằng TCCL bên trong worker, nó có thể nhận kết quả khác.

## 4. Pattern capture → set → execute → restore

Khi một task **cần TCCL của bên submit** theo thiết kế, capture ở boundary gửi việc:

~~~text
SUBMITTER
    captured = currentThread.getContextClassLoader()
    executor.submit(wrap(captured, task))

WORKER
    previous = currentThread.getContextClassLoader()
    currentThread.setContextClassLoader(captured)
    try:
        task.call()
    finally:
        currentThread.setContextClassLoader(previous)
~~~

Ở Java, có thể dùng chính hàm `withContextLoader` của ví dụ trước. Ví dụ fragment sau giả định đã có `ExecutorService executor` và `Callable<T> task` bên trong một method generic:

~~~java
ClassLoader captured =
        Thread.currentThread().getContextClassLoader();

Future<T> result = executor.submit(
        withContextLoader(captured, task));
~~~

Đây là **fragment**, cần khai báo/import `Future`, `T`, `task`, `executor` phù hợp. Điều quan trọng:

- **Capture tại thời điểm submit** nếu đó là ngữ cảnh cần chuyển, chứ không capture một lần khi tạo singleton pool.
- **Set trên worker khi task bắt đầu**, không chỉ set tại submitter trước khi `submit`.
- **Khôi phục đúng giá trị trước đó**, kể cả `null`, dùng `finally`. Không tự thay mọi loader cũ thành system loader.
- Dùng wrapper riêng cho `Runnable` nếu task không trả kết quả, giữ xử lý exception của executor.
- Không truyền TCCL bừa cho mọi task; tác vụ hệ thống chung đôi khi nên dùng base loader.
- Chuyển loader bằng đối số tường minh khi API hỗ trợ (`Class.forName(name, true, loader)`, `ServiceLoader.load(service, loader)`) thường ít trạng thái ẩn hơn.
- Captured loader là một **strong reference** trong task/queue. Khi plugin kết thúc vòng đời, cần hủy task tồn đọng và release các reference này.

Nếu wrapper chỉ restore TCCL nhưng task còn thay đổi `ThreadLocal`, logging MDC, security context hoặc transaction context, các trạng thái đó **không tự được dọn**. Chúng có cơ chế và lifecycle riêng.

## 5. Vì sao ServiceLoader và resource có thể cho kết quả khác?

Giả sử provider class và metadata `META-INF/services/com.acme.Service` chỉ nằm trong đường tìm kiếm của plugin loader. Khi gọi trực tiếp trên submitter (TCCL=plugin), discovery có thể tìm thấy provider; khi gọi trong worker (TCCL=base), provider có thể không được tìm thấy.

~~~text
Submitter TCCL=PluginLoader
  ServiceLoader.load(Service.class)
      └─ nhìn thấy provider của plugin

Worker TCCL=BaseLoader
  ServiceLoader.load(Service.class)
      └─ có thể không thấy provider plugin
~~~

Ví dụ Java dạng **fragment**, cần interface `MyService` và khai báo provider hợp lệ nên **chưa phải chương trình độc lập**:

~~~java
ServiceLoader<MyService> providers =
        ServiceLoader.load(MyService.class);

for (MyService provider : providers) {
    provider.execute();
}
~~~

Lời gọi dạng một tham số ở trên sử dụng TCCL theo quy tắc API. Có thể chọn overload rõ ràng để tránh phụ thuộc trạng thái worker:

~~~java
ServiceLoader<MyService> providers =
        ServiceLoader.load(MyService.class, pluginLoader);
~~~

Fragment này cần `pluginLoader` và các loại/import tương ứng. Dùng loader tường minh không bảo đảm provider hợp lệ nếu interface `MyService` đã bị host và plugin định nghĩa riêng: provider vẫn phải triển khai **đúng cùng định nghĩa interface** mà consumer yêu cầu. Module graph, visibility, `uses`/`provides` cũng có thể ảnh hưởng discovery trong JPMS.

Tương tự, các API có thể khác nhau:

| API trong worker | Có dùng TCCL không? |
| --- | --- |
| `Thread.currentThread().getContextClassLoader().getResource(name)` | Có, dùng loader của worker. |
| `ServiceLoader.load(Service.class)` | Thông thường dùng TCCL theo API. |
| `SomeClass.class.getResource("config.properties")` | Dựa trên class/package/module liên quan, **không mặc định chuyển qua TCCL**. |
| `Class.forName(name, true, loader)` | Dùng loader được truyền tường minh. |

Vì vậy, **resource trả `null` chưa chắc do thread pool**: có thể sai tên đường dẫn, module encapsulation, loader không nhìn thấy JAR, thiếu provider metadata hoặc code đang tra cứu bằng một API khác.

## 6. ThreadFactory, pool dùng chung và plugin-specific pool

Một `ThreadFactory` có thể tạo worker với TCCL mong muốn ngay từ đầu:

~~~java
ThreadFactory factory = job -> {
    Thread worker = new Thread(job, "plugin-worker");
    worker.setContextClassLoader(pluginLoader);
    return worker;
};
~~~

Đây là **fragment**, cần `pluginLoader` được khai báo ở nơi dùng. Nó hợp lý khi **pool chỉ thuộc một plugin** và có vòng đời tương ứng. Nhưng với pool **chia sẻ nhiều plugin**, một TCCL cố định là không đủ: task của plugin B có thể được xử lý bằng loader A. Khi đó wrapper per-task hoặc truyền class loader tường minh thường phù hợp hơn.

Còn một vấn đề: task có thể thay TCCL ngay trong lúc chạy. Nếu quên restore, chính worker vốn được tạo đúng bởi factory vẫn bị “bẩn” cho các task tiếp theo. Không nên cố thay TCCL của một worker đang chạy **từ thread khác**: việc thay đổi đồng thời có thể gây lỗi ngữ cảnh khó tái hiện. Đặt/khôi phục trong code được thực thi trên chính worker.

## 7. CompletableFuture và callback không nhất thiết chạy trên submitter

`CompletableFuture.supplyAsync(...)` hoặc `runAsync(...)` không truyền executor thường sử dụng `ForkJoinPool.commonPool()` khi thích hợp (có fallback phụ thuộc điều kiện), nên stage chạy trên thread do framework/pool quản lý.

~~~java
CompletableFuture<Result> future =
        CompletableFuture.supplyAsync(() -> queryProvider());
~~~

Đây là **fragment minh họa**: `Result` và `queryProvider()` chưa được khai báo. Không thể suy ra TCCL của stage từ thread tạo future.

Điểm cần lưu ý:

- `thenApply(...)` không phải async: action có thể chạy ở thread hoàn thành stage trước hoặc khi đăng ký vào stage đã hoàn tất.
- `thenApplyAsync(...)` lên lịch theo executor liên quan; TCCL là của thread thực hiện stage đó.
- Nhiều stage có thể chạy ở **nhiều thread khác nhau**. Nếu từng stage cần TCCL của plugin, phải đảm bảo context tại **mỗi boundary thực sự cần**.
- Scheduled executor, event loop hoặc background callback có cùng nguyên tắc: xác định **ai sở hữu thread**, thay vì giả định là thread đã gửi công việc.

Framework đôi khi có task decorator/context propagation. Cần đọc rõ tính năng đó có truyền **TCCL** hay chỉ MDC, security context, tracing context. Không được coi các ngữ cảnh trên là đồng nhất.

## 8. Rò rỉ class loader sau undeploy
Nếu worker sống lâu quên restore TCCL, nó giữ strong reference tới loader plugin cũ sau khi plugin dừng. Tương tự, task chờ trong queue hoặc `Future`/callback cũng có thể giữ loader qua closure. Điều này vừa gây provider lookup sai vừa làm class unloading bị trì hoãn.

```text
Host executor → live worker → contextClassLoader → old PluginLoader
```

Khi unload, chặn task mới, cho task hiện tại hoàn thành/hủy theo policy, restore TCCL trong `finally`, dọn queue/callback/`ThreadLocal`, và shutdown pool **nếu thuộc sở hữu plugin**. `URLClassLoader.close()` không tự giải phóng mọi class ngay. Bài “Rò rỉ bộ nhớ...” đi sâu GC roots, metaspace và cách đo qua nhiều vòng redeploy.
## 9. Những lỗi lập trình thường gặp

**Chỉ set TCCL tại submitter:**

~~~java
Thread.currentThread().setContextClassLoader(pluginLoader);
executor.submit(task);
~~~

Đây là **anti-pattern fragment**, chưa khôi phục TCCL và cũng không bảo đảm worker thấy plugin loader. Submitter có thể tự mang ngữ cảnh sai sau khi gọi.

**Set TCCL trong worker nhưng không restore:**

~~~java
executor.submit(() -> {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    runPluginJob();
});
~~~

Đây cũng chỉ là **fragment minh họa lỗi**. Khi `runPluginJob()` trả về hoặc ném exception, worker có thể giữ loader ấy cho task sau.

**Nhầm `InheritableThreadLocal` với propagation theo task:** `InheritableThreadLocal` cũng chủ yếu truyền dữ liệu lúc tạo thread con. Nó không cập nhật giá trị mỗi lần thread pool nhận task. TCCL có cơ chế kế thừa riêng của `Thread` nhưng cũng không tự cập nhật khi submit.

**Nhầm lỗi SPI với lỗi loader duy nhất:** `ServiceConfigurationError` có thể do thiếu metadata, class/provider không nạp được, module access hoặc provider không triển khai đúng interface. TCCL đúng chưa chắc đủ.

**Quên dọn trạng thái ngoài TCCL:** `ThreadLocal`, MDC, transaction context hoặc những object ứng dụng không tự được restore bằng wrapper chỉ xử lý `ClassLoader`.

## 10. Quy trình debug

1. Ngay tại submitter, log `Thread.currentThread().getName()`, TCCL và class loader của kiểu dịch vụ cần dùng.
2. Ngay trong worker khi task chạy, log **chính các thông tin ấy** và so sánh; tránh log trước khi enqueue rồi suy luận thread trong pool tương tự.
3. Xem worker được tạo ở đâu và `ThreadFactory` có đặt TCCL không.
4. Kiểm tra task trước đó có `setContextClassLoader` mà thiếu `finally` không.
5. Với `ServiceLoader`, in provider thực tế hoặc lỗi discovery và xác minh interface do loader nào định nghĩa.
6. Với resource, kiểm tra URL từ `getResource()` qua đúng loader và đường dẫn, kể cả module access.
7. Dùng wrapper capture/set/restore hoặc truyền loader tường minh rồi xác minh **task sau wrapper đã thấy loader gốc**.
8. Với hot redeploy, kiểm tra queue và thread còn giữ class loader deployment cũ.

## 11. Tự kiểm tra kiến thức

**Câu 1.** Thread S có TCCL=PluginA, pool worker W đã có TCCL=Base từ trước. Sau `submit`, task thấy loader nào nếu không có propagation?

**Đáp án:** Nó thấy **Base**, tức TCCL hiện tại của W.

**Câu 2.** Worker được tạo lúc thread A giữ PluginA; hôm sau thread B giữ PluginB và submit task vào chính worker ấy. TCCL worker có tự thành PluginB?

**Đáp án:** Không. Kế thừa lúc tạo thread không phải cơ chế truyền dữ liệu ở mỗi submit.

**Câu 3.** Tại sao wrapper cần `finally`?

**Đáp án:** Để khôi phục TCCL trước đó khi task hoàn tất hoặc ném exception, tránh ảnh hưởng task khác và giữ loader cũ.

**Câu 4.** `ServiceLoader.load(Service.class)` có thể khác kết quả khi gọi trên submitter và worker?

**Đáp án:** Có, vì TCCL khác nhau có thể làm thay đổi provider được nhìn thấy.

**Câu 5.** `Class.forName(name, true, loader)` dùng TCCL ngầm không?

**Đáp án:** Không; overload này dùng `loader` truyền tường minh.

**Câu 6.** TCCL đúng nhưng vẫn gặp `ClassCastException` ở provider là vì sao?

**Đáp án:** Có thể do provider và consumer dùng hai định nghĩa interface giống tên nhưng khác defining loader; TCCL không tự hợp nhất kiểu.

## Ghi nhớ

**TCCL thuộc về thread thực thi, không tự động gắn với tác vụ khi `submit`.** Worker được tạo và tái sử dụng ở thời điểm khác submitter, nên có thể tìm class, resource và provider khác. Nếu thật sự cần ngữ cảnh của submitter, hãy **capture → set trên worker → execute → restore trong finally**, hoặc truyền `ClassLoader` tường minh khi API hỗ trợ. Cần quản lý queue/worker khi plugin dừng để tránh dùng sai loader và giữ deployment cũ.

**Tài liệu chính thức:**

- `Thread` (Java SE 17): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- `ClassLoader` (Java SE 17): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- `ExecutorService`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- `Executors` và `ThreadFactory`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html
- `ServiceLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- `CompletableFuture`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-class-loader-007">Đổi context class loader trong phạm vi hẹp và khôi phục bằng finally</a>

<details>
<summary>Xem chi tiết</summary>

# Đổi context class loader trong phạm vi hẹp và khôi phục bằng finally

## 1. Vì sao cần đổi Thread Context ClassLoader?

Trong Java, mỗi thread có thể giữ một tham chiếu tới **Thread Context ClassLoader (TCCL)**. Ta lấy nó bằng:

~~~java
ClassLoader current =
        Thread.currentThread().getContextClassLoader();
~~~

Đây là **loader gắn với ngữ cảnh của thread**, không nhất thiết là loader định nghĩa class đang chạy. Một thư viện có thể được nạp bởi loader của host nhưng vẫn cần tìm provider, class hoặc resource thuộc plugin. Thay vì buộc thư viện biết một loader plugin cụ thể, framework có thể đặt **TCCL của thread** thành loader plugin trong thời gian gọi thư viện.

Ví dụ thực tế: overload một tham số của `ServiceLoader.load(MyService.class)` sử dụng **TCCL của thread hiện tại** để tìm provider trong ngữ cảnh caller. Một số framework và API khám phá plugin/resource khác cũng đọc TCCL để chọn loader phù hợp.

Tuy nhiên, TCCL là **trạng thái có thể thay đổi của thread**, vì vậy việc đổi nó phải có phạm vi và cơ chế phục hồi rõ ràng:

> **Quy tắc cốt lõi:** Lưu loader cũ, đặt loader tạm thời, thực hiện công việc trong `try` và **luôn khôi phục loader cũ trong `finally`**.

Nếu quên khôi phục, một thao tác sau trên **cùng thread** có thể dùng nhầm loader plugin, tìm nhầm resource/provider hoặc giữ loader cũ còn sống sau khi plugin đã bị gỡ.

## 2. Phân biệt ba loại “ClassLoader hiện tại”

Ba cách lấy loader sau giải quyết các câu hỏi khác nhau:

~~~java
ClassLoader defining =
        MyClass.class.getClassLoader();

ClassLoader system =
        ClassLoader.getSystemClassLoader();

ClassLoader context =
        Thread.currentThread().getContextClassLoader();
~~~

| Tham chiếu | Câu hỏi nó trả lời | Có đổi khi `setContextClassLoader` không? |
|---|---|---|
| `MyClass.class.getClassLoader()` | Ai **định nghĩa runtime class** `MyClass`? | **Không** |
| `ClassLoader.getSystemClassLoader()` | System/application loader được JVM chọn là gì? | **Không** |
| `Thread.currentThread().getContextClassLoader()` | Loader nào đang được thread dùng làm ngữ cảnh tra cứu? | **Có**, với thread đó |

Việc gọi `thread.setContextClassLoader(pluginLoader)` **không di chuyển class** từ parent sang child, không thay đổi loader định nghĩa các class đã nạp và không sửa quan hệ ủy quyền parent–child. Nó chỉ thay giá trị của **một trường ngữ cảnh trên đối tượng `Thread`**.

Nếu một API tự gọi `SomeClass.class.getClassLoader()` thay vì lấy TCCL, việc đổi TCCL có thể **không ảnh hưởng** tới API đó. TCCL là một **quy ước tra cứu được một số API/framework sử dụng**, không phải công tắc thay toàn bộ class loading của JVM.

## 3. Mẫu an toàn tối thiểu: `try/finally`

Mẫu nên thuộc nằm lòng:

~~~java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi thư viện hoặc framework cần tìm tài nguyên từ pluginLoader.
} finally {
    thread.setContextClassLoader(previous);
}
~~~

Đây là **đoạn mã minh họa**: `pluginLoader` phải được tạo hoặc truyền từ bên ngoài. Những điểm không được bỏ qua:

1. **Lấy đúng thread hiện tại.** Một lệnh gọi `setContextClassLoader` thay TCCL của thread mà nó được gọi trên; trong ví dụ là `Thread.currentThread()`.
2. **Giữ nguyên tham chiếu loader cũ**, kể cả khi nó là `null`. Đừng suy luận “loader cũ chắc chắn là system loader”.
3. **Giới hạn vùng `try`** vừa đủ cho lời gọi cần plugin loader. Không giữ TCCL của plugin cho toàn bộ request nếu chỉ một bước discovery cần dùng.
4. **Dùng `finally` chứ không đặt restore sau `try`**. `finally` vẫn được chạy trên luồng điều khiển thông thường khi code trong `try` thành công, return hoặc ném exception.
5. **Không nuốt exception chính.** `finally` dùng để khôi phục trạng thái; exception từ thao tác chính thường cần truyền lên caller hoặc được xử lý theo chính sách rõ ràng.

Mã sau **không an toàn**:

~~~java
// Mã minh họa sai.
Thread.currentThread().setContextClassLoader(pluginLoader);
service.perform();
Thread.currentThread().setContextClassLoader(previous);
~~~

Nếu `service.perform()` ném exception, dòng restore cuối **không chạy**. Trên thread pool, một task sau có thể vô tình nhìn thấy loader plugin của task trước.

Lưu ý kỹ thuật: `setContextClassLoader` có thể bị từ chối theo cơ chế kiểm soát quyền trong các runtime có cấu hình kiểm soát phù hợp, chẳng hạn `SecurityManager` của các JDK hỗ trợ tính năng này. `finally` bảo đảm **thử khôi phục**, không bảo đảm một lệnh khôi phục bị chặn luôn thành công. Đừng âm thầm bỏ qua lỗi restore.

## 4. Vì sao mẫu này quan trọng với nested calls và exception?

Giả sử một thao tác A cần loader A, nhưng trong khi chạy A lại gọi thao tác B cần loader B.

~~~text
Ban đầu:               TCCL = system
│
├─ Vào phạm vi A:       TCCL = loader A
│  │
│  ├─ Vào phạm vi B:    TCCL = loader B
│  └─ finally của B:    TCCL = loader A
│
└─ finally của A:       TCCL = system
~~~

Mỗi phạm vi lưu **loader mà nó thấy khi bắt đầu** và khôi phục **chính giá trị đó**. Vì thế nested scopes hoạt động theo cấu trúc *stack* tự nhiên.

Nếu B ném exception, `finally` của B vẫn khôi phục A; sau đó nếu exception tiếp tục đi ra phạm vi A, `finally` của A khôi phục system. Không cần giả định loader cũ luôn là system loader.

Nếu đổi TCCL nhiều lần trong cùng một vùng `try` mà không thiết kế rõ các mức lồng nhau, dễ khôi phục sai loader. Tốt nhất mỗi lần thay tạm thời nên được bọc trong **một vùng `try/finally` độc lập**.

## 5. Ví dụ Java 17+ hoàn chỉnh: hai plugin resource và khôi phục TCCL

Ví dụ sau minh họa bằng **resource lookup qua TCCL**. Đây là cách thấy rõ thay đổi ngữ cảnh mà không cần phụ thuộc framework.

Ta tạo hai JAR chỉ chứa cùng tên resource:

~~~text
tccl-demo/
├── src/
│   └── ContextScopeDemo.java
└── resources/
    ├── alpha/
    │   └── demo/context/marker.txt
    └── beta/
        └── demo/context/marker.txt
~~~

Nội dung chính xác của hai file UTF-8:

- `resources/alpha/demo/context/marker.txt`: `ALPHA`
- `resources/beta/demo/context/marker.txt`: `BETA`

Các resource này **không** được thêm vào application classpath. Host chỉ chứa class `ContextScopeDemo`. Chúng được nạp qua hai `URLClassLoader` khác nhau.

### 5.1. File nguồn Java độc lập

Tạo **`src/ContextScopeDemo.java`**:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public final class ContextScopeDemo {
    private static final String RESOURCE = "demo/context/marker.txt";

    private ContextScopeDemo() {
    }

    private static String readMarker() throws IOException {
        ClassLoader loader =
                Thread.currentThread().getContextClassLoader();

        if (loader == null) {
            return "(no context loader)";
        }

        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return "(missing)";
            }
            return new String(
                    in.readAllBytes(),
                    StandardCharsets.UTF_8).strip();
        }
    }

    private static <T> T withContext(
            ClassLoader temporary, Callable<T> action) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(temporary);
            return action.call();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: ContextScopeDemo <alpha.jar> <beta.jar>");
        }

        Path alphaJar = Path.of(args[0]).toAbsolutePath().normalize();
        Path betaJar = Path.of(args[1]).toAbsolutePath().normalize();

        if (!Files.isRegularFile(alphaJar)
                || !Files.isRegularFile(betaJar)) {
            throw new IllegalArgumentException("Both JARs must exist");
        }

        URL alphaUrl = alphaJar.toUri().toURL();
        URL betaUrl = betaJar.toUri().toURL();

        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();

        try (URLClassLoader alpha =
                     new URLClassLoader(new URL[] {alphaUrl}, original);
             URLClassLoader beta =
                     new URLClassLoader(new URL[] {betaUrl}, original)) {

            System.out.println("Before: " + readMarker());

            withContext(alpha, () -> {
                System.out.println("Inside alpha: " + readMarker());

                String nestedValue =
                        withContext(beta, ContextScopeDemo::readMarker);
                System.out.println("Inside beta: " + nestedValue);

                System.out.println("Back in alpha: " + readMarker());
                return null;
            });

            System.out.println("After alpha: " + readMarker());

            try {
                withContext(beta, () -> {
                    throw new IllegalStateException("boom");
                });
            } catch (IllegalStateException expected) {
                System.out.println("Caught: " + expected.getMessage());
            }

            System.out.println("After exception: " + readMarker());
            System.out.println("Original restored: "
                    + (thread.getContextClassLoader() == original));
        }
    }
}
~~~

**Mẫu trọng tâm** là phương thức `withContext`. Nó cho phép truyền một đoạn công việc dưới dạng `Callable<T>`, lưu loader cũ, đặt loader mới, chạy `action.call()`, rồi khôi phục trong `finally`.

Cách này đơn giản hóa nested calls vì mỗi lần gọi `withContext` quản lý chính phạm vi của mình. Đây không phải cách duy nhất để xây một context scope; với thao tác đơn lẻ, đoạn `try/finally` trực tiếp ở phần 3 thường dễ đọc hơn.

### 5.2. Biên dịch, đóng JAR và chạy

Yêu cầu **JDK 17 trở lên**. Đứng tại thư mục `tccl-demo/` có cấu trúc đã mô tả.

**Bash (Linux/macOS hoặc Git Bash):**

~~~bash
mkdir -p build/classes dist

javac -encoding UTF-8 \
  -d build/classes src/ContextScopeDemo.java

jar --create --file dist/alpha.jar -C resources/alpha .
jar --create --file dist/beta.jar -C resources/beta .

java -cp build/classes \
  ContextScopeDemo dist/alpha.jar dist/beta.jar
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force build/classes,dist | Out-Null

javac -encoding UTF-8 -d build/classes src/ContextScopeDemo.java

jar --create --file dist/alpha.jar -C resources/alpha .
jar --create --file dist/beta.jar -C resources/beta .

java -cp build/classes ContextScopeDemo dist/alpha.jar dist/beta.jar
~~~

Lệnh `jar` đóng resource vào JAR ở đường dẫn `demo/context/marker.txt`. Có thể xác minh:

~~~bash
jar --list --file dist/alpha.jar
jar --list --file dist/beta.jar
~~~

Cả hai JAR đều phải có `demo/context/marker.txt`. Chương trình chạy trên classpath `build/classes` và **không** thêm hai JAR vào `-cp`: như vậy loader mặc định của host không tìm thấy resource plugin, còn TCCL `alpha` hoặc `beta` có thể tìm thấy.

### 5.3. Stdout trong môi trường Java 17 tiêu chuẩn

Với Java launcher thông thường (TCCL ban đầu của luồng `main` là application/system loader), không có resource trùng tên trong application/JDK classpath:

~~~text
Before: (missing)
Inside alpha: ALPHA
Inside beta: BETA
Back in alpha: ALPHA
After alpha: (missing)
Caught: boom
After exception: (missing)
Original restored: true
~~~

**Giải thích:**

1. **Before:** System/context loader ban đầu không nhìn thấy `marker.txt` chỉ nằm trong plugin JAR → `(missing)`.
2. **Inside alpha:** `withContext(alpha, ...)` tạm đặt TCCL thành loader alpha; đọc resource từ `alpha.jar` → `ALPHA`.
3. **Inside beta:** Nested `withContext(beta, ...)` đặt loader beta → `BETA`. Khi lời gọi lồng nhau kết thúc, `finally` của beta khôi phục TCCL về **alpha**, không phải system.
4. **Back in alpha:** Resource lại trả `ALPHA`, chứng minh nested scope đã khôi phục chính xác.
5. **After alpha:** `finally` của alpha khôi phục TCCL về original → không còn thấy plugin resource từ ngữ cảnh host.
6. **Caught:** Một action dùng beta cố ý ném `IllegalStateException("boom")`. `finally` vẫn khôi phục TCCL, sau đó caller bắt exception.
7. **After exception** và **Original restored:** TCCL không bị rò khỏi vùng tạm và chính là **cùng object loader ban đầu**; phép so sánh dùng `==`, không dựa trên tên hoặc `toString()`.

Kết quả sẽ khác nếu môi trường chạy chủ động thiết lập TCCL ban đầu đặc biệt hoặc thêm các resource tương tự vào parent classpath. Chương trình vẫn thể hiện nguyên tắc khôi phục, nhưng các phép lookup có thể chịu ảnh hưởng của parent-first resource delegation.

## 6. Vì sao phải lưu loader cũ thay vì ép về system?

Cách sai thường thấy:

~~~java
// Mã minh họa sai về ngữ nghĩa scope.
try {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    usePlugin();
} finally {
    Thread.currentThread().setContextClassLoader(
            ClassLoader.getSystemClassLoader());
}
~~~

Nếu caller ban đầu chạy trong application server, loader của request, loader của plugin A hoặc một custom context loader, đoạn này **thay ngữ cảnh ban đầu bằng system loader**, không phải khôi phục. Nó có thể làm hỏng lời gọi bên ngoài trong nested scope.

Cách đúng:

~~~java
// Mã minh họa; pluginLoader/usePlugin do ứng dụng cung cấp.
Thread current = Thread.currentThread();
ClassLoader old = current.getContextClassLoader();

try {
    current.setContextClassLoader(pluginLoader);
    usePlugin();
} finally {
    current.setContextClassLoader(old);
}
~~~

TCCL cũ có thể là `null`; nếu thật sự là `null` thì `finally` cũng cần trả về `null`, chứ không tự thay bằng system loader.

Không nên xác định loader cũ bằng `getClass().getClassLoader()` trong thư viện vì loader định nghĩa class thư viện thường **khác** context loader mà caller đã lựa chọn.

## 7. Khi nào dùng TCCL với ServiceLoader?

Giả sử host có interface `com.example.api.Plugin` do loader cha định nghĩa và plugin JAR có service descriptor `META-INF/services/com.example.api.Plugin`.

Trong thư viện sử dụng overload:

~~~java
// Mã minh họa; Plugin đã được định nghĩa bởi API dùng chung.
ServiceLoader<Plugin> providers = ServiceLoader.load(Plugin.class);
~~~

`ServiceLoader.load(Plugin.class)` chọn loader qua **TCCL của thread**. Vì vậy host có thể gọi nó trong một phạm vi tạm:

~~~java
// Mã minh họa; Plugin và pluginLoader được cung cấp bên ngoài.
Thread thread = Thread.currentThread();
ClassLoader old = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    for (Plugin provider : ServiceLoader.load(Plugin.class)) {
        // Sử dụng provider trong phạm vi plugin loader thích hợp.
    }
} finally {
    thread.setContextClassLoader(old);
}
~~~

Cách trực tiếp hơn, khi API cho phép truyền loader rõ ràng:

~~~java
// Mã minh họa; không cần đổi TCCL.
ServiceLoader<Plugin> providers =
        ServiceLoader.load(Plugin.class, pluginLoader);
~~~

**Ưu tiên truyền loader tường minh** nếu API hỗ trợ, vì tránh một thay đổi trạng thái trên thread. Chỉ đổi TCCL khi library/framework bên trong chủ động phụ thuộc vào nó và không có cách truyền loader phù hợp khác.

Dù dùng cách nào, provider class cần liên kết tới **cùng runtime interface** mà host đưa vào `ServiceLoader`. TCCL không chữa được lỗi `ClassCastException` do host/plugin tự định nghĩa hai bản interface cùng tên.

Với **named modules**, `ServiceLoader` và module layers còn có cơ chế `uses`, `provides ... with ...`, readability và các cách nạp provider khác; ví dụ trên chỉ mô tả trường hợp classpath/plugin thông dụng.

## 8. Scope TCCL không tự lan sang công việc bất đồng bộ
`try/finally` chỉ quản lý **thread đang chạy scope**. Nếu một method trong scope gọi `executor.submit(...)` rồi trả về, `finally` của thread gửi sẽ chạy trước khi task hoàn tất, và worker không mặc nhiên nhận TCCL tạm. Với `CompletableFuture`, callback có thể chạy trên một thread khác hoặc tại thread hoàn thành stage, tùy API.

Muốn tác vụ dùng TCCL plugin, chuyển desired loader như dependency, đặt/khôi phục trên chính worker trong wrapper. Khi một thread mới được **tạo** trong scope tạm, nó có thể thừa hưởng plugin loader và giữ reference sau khi scope cha đã phục hồi; giới hạn vòng đời thread con hoặc đặt context rõ ràng qua factory. Không đóng `URLClassLoader` khi tác vụ async vẫn cần tải resource/class từ nó. Bài về thread creation/pool trình bày ví dụ chi tiết.
## 9. Phạm vi hẹp và quan hệ với vòng đời URLClassLoader

Đổi TCCL **không tự đóng** resource loader và **không tự unload** class. Nếu plugin loader là `URLClassLoader`:

~~~java
// Mã minh họa (không phải chương trình độc lập):
try (URLClassLoader pluginLoader =
             new URLClassLoader(urls, parentLoader)) {
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    try {
        thread.setContextClassLoader(pluginLoader);
        usePlugin();
    } finally {
        thread.setContextClassLoader(previous);
    }
}
~~~

Thứ tự có chủ đích:

1. Tạo/open loader.
2. Chuyển TCCL tạm thời.
3. Gọi code cần dùng loader.
4. **Restore TCCL trong `finally`**.
5. Đóng `URLClassLoader` khi công việc dùng loader đã kết thúc.

`URLClassLoader.close()` đóng nguồn JAR/file mà loader quản lý, nhưng không bảo đảm các class đã nạp được giải phóng ngay. Một thread pool, thread sống lâu, object plugin, callback hoặc cache còn giữ loader có thể chặn class unloading.

Nếu công việc đã gửi sang thread khác chưa hoàn thành thì **không đóng plugin loader quá sớm**. Scope cho TCCL và vòng đời plugin phải được đồng bộ: đóng loader chỉ khi không còn task cần dùng nguồn class/resource của nó.

## 10. Mẫu helper và chi phí của abstraction

Có thể đóng gói mã lặp lại như trong ví dụ:

~~~java
// Phương thức minh họa — cần import java.util.concurrent.Callable.
static <T> T withContext(
        ClassLoader temporary, Callable<T> action) throws Exception {
    Thread thread = Thread.currentThread();
    ClassLoader original = thread.getContextClassLoader();
    try {
        thread.setContextClassLoader(temporary);
        return action.call();
    } finally {
        thread.setContextClassLoader(original);
    }
}
~~~

Ưu điểm: nơi gọi ít khả năng quên restore, nested scope được quản lý đúng, và helper dùng được với task trả giá trị.

Nhưng helper không loại bỏ mọi trách nhiệm:

- `Callable.call()` cho phép ném `Exception`; caller cần chính sách xử lý phù hợp.
- Với lỗi restore (ví dụ bị từ chối bởi cấu hình security), exception ở `finally` có thể làm che exception ban đầu. Nếu hệ thống cần đảm bảo cả hai lỗi được báo đầy đủ, cần thiết kế cleanup/error reporting riêng.
- Nếu action trả về `CompletableFuture` rồi kết thúc ngay, `finally` khôi phục TCCL **trước khi** công việc bất đồng bộ chạy xong. Không coi helper đồng bộ này là phạm vi bao trùm tương lai bất đồng bộ.
- Helper chỉ thay TCCL trên **thread gọi nó**, không lan sang các executor thread khác.
- Khi không cần trả kết quả, có thể dùng `Runnable` và vẫn cần xử lý exception theo contract, nhưng đừng phát triển nhiều helper làm che khuất loader được chọn.

Một `AutoCloseable` context scope cũng có thể được thiết kế, nhưng phải bảo đảm capture đúng thread ban đầu, khôi phục đúng thứ tự lồng nhau và tránh close scope từ thread khác. Đối với bài học cơ bản và Java 17, `try/finally` trực tiếp thường minh bạch nhất.

## 11. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách giải quyết |
|---|---|---|
| Sau khi gọi plugin, lời gọi kế tiếp thấy provider của plugin | Quên restore TCCL | Bao lời gọi trong `try/finally` |
| Scoped call lồng nhau trả về system thay vì loader A | Trong scope B restore về system, không restore `previous` | Lưu TCCL tại lúc bắt đầu **mỗi** scope |
| Sau exception, thread pool tiếp tục dùng loader plugin | Đặt restore sau dòng có thể ném exception | Chuyển restore vào `finally` |
| Parent thread đã restore nhưng worker vẫn thấy plugin | Thread mới kế thừa TCCL hoặc worker có trạng thái riêng | Quản lý TCCL trong worker/thread factory |
| `executor.submit` không thấy resource plugin dù caller đã đổi TCCL | Task chạy trên thread khác | Đặt scope trong task thực thi |
| `Class.forName(...)` vẫn không tìm class plugin | API đang dùng loader của caller, không lấy TCCL | Dùng overload có `ClassLoader` tường minh hoặc API hỗ trợ TCCL |
| `ServiceLoader` không thấy provider | TCCL sai hoặc metadata `META-INF/services` thiếu | Truyền loader rõ ràng, kiểm tra provider descriptor |
| Sau plugin reload, loader cũ chưa unload | Thread/callback/cache giữ tham chiếu loader | Restore TCCL, gỡ task/thread/cache, quản lý lifecycle |
| `ClassCastException` giữa host và plugin | Shared interface/DTO bị nhiều loader tự định nghĩa | Dùng một bản API định nghĩa bởi loader chung |
| Đổi TCCL không thay loader của class hiện tại | TCCL không phải defining loader | Kiểm tra `SomeClass.class.getClassLoader()` |
| Resource lookup đổi kết quả giữa môi trường | Parent delegation/classpath/module visibility khác nhau | Kiểm tra đúng loader, URL, module và quyền truy cập |
| `finally` cũng ném exception | Restore thất bại hoặc cleanup không an toàn | Đừng nuốt lỗi; phân tích lỗi chính và lỗi cleanup |

## 12. TCCL và module/security: phạm vi thật sự của cơ chế

**TCCL không tạo sandbox.** Đặt plugin loader làm ngữ cảnh cho thread không giới hạn quyền hệ điều hành của code plugin và không phân lập tiến trình. Với plugin không tin cậy, cần mô hình cách ly thực sự như tiến trình/container và chính sách kiểm soát phù hợp.

**JPMS không biến mất.** ClassLoader có thể tìm thấy bytecode, nhưng named modules vẫn chịu các quy tắc readability, `exports`, `opens` và `ModuleLayer`. Thay TCCL không mặc nhiên cho phép truy cập package đã bị đóng.

**Bootstrap loader và giá trị `null`.** `Thread.getContextClassLoader()` có thể trả `null`. Trường hợp này không nhất thiết là lỗi; cách một thư viện diễn giải TCCL null phụ thuộc API đó. Không tự thay null bằng system loader trong logic restore.

**Class initialization độc lập.** Thay TCCL không chạy `static initializer` của mọi class trong plugin; nó chỉ thay loader được thư viện lựa chọn nếu thư viện chủ động đọc TCCL. Việc `loadClass`, `Class.forName` và initialization hoạt động theo những quy tắc riêng.

## 13. Checklist khi phải đổi context class loader

1. **API thực sự đọc TCCL không?** Nếu hỗ trợ tham số ClassLoader, ưu tiên truyền tham số tường minh.
2. **Loader ban đầu là gì?** Lấy `Thread.currentThread().getContextClassLoader()` và giữ nguyên object reference, kể cả `null`.
3. **Phạm vi đủ hẹp chưa?** Bao đúng lời gọi thư viện/plugin cần loader tạm.
4. **Restore nằm trong `finally` chưa?** Bao gồm cả khi return, exception và nested scopes.
5. **Có thread khác không?** Nếu dùng executor hoặc async, cần scope ở thread thực thi.
6. **Có tạo thread sống lâu không?** Cân nhắc TCCL kế thừa, ThreadFactory và classloader leak.
7. **Loader còn sống trong suốt công việc không?** Không đóng `URLClassLoader` trước khi task dùng nó kết thúc.
8. **Có shared API đúng identity không?** TCCL không giải quyết hai bản interface riêng biệt.
9. **Có test đường lỗi chưa?** Cố ý ném exception rồi xác minh context loader được restore bằng phép `==`.
10. **Có test nested calls chưa?** B khôi phục A, rồi A khôi phục caller.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao cần lưu TCCL cũ trước khi gọi `setContextClassLoader`?

**Đáp án:** Để khôi phục **đúng context ban đầu** của caller, có thể là loader riêng hoặc `null`; không mặc định là system loader.

**Câu 2.** Nếu một phương thức trong scope ném exception, đặt restore sau lời gọi có đủ không?

**Đáp án:** Không. Dòng restore sau lời gọi có thể bị bỏ qua. Phải đặt trong `finally`.

**Câu 3.** Với nested scope A → B, cuối scope B phải restore về loader nào?

**Đáp án:** Về **loader A**, tức TCCL được lưu khi scope B bắt đầu. Khi A kết thúc, restore về TCCL của caller ban đầu.

**Câu 4.** Một task trong thread pool có tự dùng TCCL mà thread gửi task vừa đặt không?

**Đáp án:** Không có đảm bảo tổng quát như vậy. Worker có TCCL riêng; đổi TCCL trong task nếu API bên trong cần context đó.

**Câu 5.** Tại sao `ServiceLoader.load(Service.class, pluginLoader)` thường phù hợp hơn việc đổi TCCL chỉ để nạp provider?

**Đáp án:** Nó chọn loader tường minh, không cần thay trạng thái thread và giảm nguy cơ rò TCCL. Chỉ dùng scope khi API bên dưới không hỗ trợ truyền loader.

**Câu 6.** Scope khôi phục đúng TCCL có đồng nghĩa loader plugin được unload ngay sau đó không?

**Đáp án:** Không. Thread khác, object, cache, listener hoặc tài nguyên còn giữ loader vẫn có thể ngăn unloading.

## 15. Nguồn tài liệu đối chiếu

- [Java SE 17: Thread.getContextClassLoader và setContextClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): hành vi context loader của thread và quyền thay đổi.
- [Java SE 17: ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): overload nạp service bằng TCCL hoặc loader tường minh.
- [Java SE 17: ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): class/resource lookup, delegation và class identity.
- [Java SE 17: URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): nạp resource từ JAR và đóng loader.
- [Java SE 17: Callable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Callable.html): cách truyền task có giá trị trả về và có thể ném checked exception.

**Ghi nhớ:** TCCL là **ngữ cảnh tạm của thread**, không phải defining loader của class. Khi cần chuyển sang plugin loader, hãy **lưu old → set temporary → chạy trong try → restore old trong finally**; scopes lồng nhau khôi phục từng tầng, và executor/async cần được xử lý ngay trên thread thực thi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-class-loader-008">Rò rỉ bộ nhớ khi luồng sống lâu giữ loader của plugin</a>

<details>
<summary>Xem chi tiết</summary>

# Rò rỉ bộ nhớ khi luồng sống lâu giữ loader của plugin

## 1. Hiện tượng

Một ứng dụng Java chạy lâu có thể nạp plugin bằng ClassLoader riêng, chạy code của plugin, sau đó unload/redeploy. Nếu hoàn tất cleanup, ClassLoader cũ và các class do nó định nghĩa có thể đủ điều kiện được thu hồi.

Nhưng một thread sống lâu của host có thể đang giữ **Thread Context ClassLoader (TCCL)** trỏ tới loader cũ. Đây là một strong reference:

~~~text
GC root / live thread
       |
       v
Host worker thread (vẫn sống)
       |
       v
Thread.contextClassLoader
       |
       v
PluginClassLoader phiên bản cũ
       |
       +-- class metadata và đối tượng liên quan

=> Loader vẫn reachable dù plugin đã stop
=> Các class của plugin có thể không unload được
~~~

Sau nhiều lần deploy, nhiều phiên bản PluginClassLoader bị giữ có thể gây tăng heap, metaspace và đôi khi OutOfMemoryError: Metaspace. Không phải cứ tăng bộ nhớ là do TCCL; bài này chỉ rõ cơ chế này và cách chẩn đoán những đường giữ khác.

**Mục tiêu:** phân biệt TCCL với defining loader; hiểu reachability; tái hiện strong reference bằng Java 17+; khôi phục loader trong finally; kiểm soát việc kế thừa context ở thread con; quản lý executor, ThreadLocal và vòng đời plugin; tìm GC roots trong heap dump.

## 2. TCCL không phải defining loader

API của java.lang.Thread:

~~~java
Thread t = Thread.currentThread();
ClassLoader previous = t.getContextClassLoader();
t.setContextClassLoader(pluginLoader);
~~~

Đây là code minh họa đặt trong method có biến pluginLoader, chưa là chương trình độc lập.

TCCL là **một thuộc tính có thể đổi của Thread**. Một số framework dùng nó để tìm class, resources hoặc providers. Theo JDK API, khi tạo thread mới mà không cấu hình riêng, context loader thường được kế thừa từ thread tạo ra nó.

| API / khái niệm | Ý nghĩa |
| --- | --- |
| Thread.currentThread().getContextClassLoader() | Loader ngữ cảnh hiện tại của thread. |
| Thread.setContextClassLoader(loader) | Đổi ngữ cảnh cho thread này; không định nghĩa lại class. |
| SomeClass.class.getClassLoader() | Defining loader của class; không đổi vì TCCL đổi. |
| ClassLoader.getSystemClassLoader() | System/application loader, không mặc nhiên trùng TCCL của mọi thread. |
| ServiceLoader.load(service) | Một ví dụ API sử dụng context loader theo hợp đồng của overload này. |
| ServiceLoader.load(service, loader) | Một overload cho phép truyền loader tường minh. |

**TCCL không làm thay đổi danh tính của class.** Danh tính kiểu runtime thông thường liên quan binary name cùng defining loader. Đổi TCCL không chuyển một Class hiện hữu từ plugin sang host hoặc ngược lại.

## 3. Vì sao TCCL có thể trở thành một memory leak?

### 3.1 GC nhìn tham chiếu, không nhìn trạng thái nghiệp vụ

Khi host đánh dấu plugin STOPPED, object ClassLoader chưa chắc có thể bị thu hồi. Nếu worker còn sống, JVM vẫn có thể giữ Thread; field contextClassLoader trong Thread giữ reference đến PluginLoader.

~~~text
Host ExecutorService
    |
    +-- worker (chờ nhận task tiếp theo)
           |
           +-- contextClassLoader = oldPluginLoader

Plugin Registry đã xóa plugin cũ
JAR handle đã đóng
Nhưng oldPluginLoader vẫn có đường tham chiếu mạnh
~~~

Trong server chạy liên tục, một worker có thể tồn tại lâu hơn mọi phiên bản plugin được nó từng phục vụ.

### 3.2 Điều kiện class unloading

Theo JLS §12.7, một class/interface có thể bị unload chỉ khi **defining ClassLoader** của nó có thể được garbage collector thu hồi. Nếu long-lived thread giữ loader, điều kiện này chưa thỏa.

Tuy nhiên, **xóa đường tham chiếu không bảo đảm JVM unload ngay**. GC và class unloading được thực hiện theo nhu cầu, cấu hình và implementation. Cần phân biệt:

1. Plugin đã ngừng nhận công việc.
2. Mọi task, callback, thread và registry đã được cleanup.
3. ClassLoader không còn strongly reachable.
4. JVM đã thực sự thu hồi/unload class (có thể sau đó).

**System.gc() không phải phép đo quyết định:** đó là yêu cầu/hint, không phải lời cam kết loader sẽ biến mất ngay.

### 3.3 Heap và Metaspace

ClassLoader và object giữ tham chiếu ở heap. JVM HotSpot sử dụng metaspace để quản lý nhiều loại class metadata. Nếu loader cũ không collectible, class metadata cũng có thể tiếp tục tồn tại; redeploy liên tục có thể gây áp lực metaspace. Triệu chứng còn phụ thuộc số class, GC, giới hạn bộ nhớ và các reference khác.

## 4. Kịch bản phổ biến: task plugin đổi TCCL trên host pool

Một ứng dụng tạo pool dùng chung:

~~~text
Host starts
    |
    +-- ExecutorService hostPool
           |
           +-- host-worker-1 (sống hàng giờ)
~~~

Plugin v1 chạy trong worker:

~~~java
Thread.currentThread().setContextClassLoader(pluginLoader);
invokePlugin();
// Quên restore
~~~

Nếu task return thành công, worker vẫn có thể được pool tái sử dụng với **contextClassLoader = PluginLoaderV1**. Host unload v1 rồi nạp v2 bằng loader mới, nhưng loader v1 vẫn nằm trên worker.

Hậu quả kép:

- **Retention:** loader cũ còn reachable.
- **Sai context:** task chạy sau có thể dùng nhầm loader của plugin cũ để tìm provider, class hoặc resource.

Sự cố thường lộ ra khi application redeploy nhiều lần chứ không phải ở lần khởi động đầu.

## 5. Ví dụ Java 17+ số 1: quan sát strong reference ở thread sống lâu

Tạo file **ContextRetentionDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class ContextRetentionDemo {
    static final class PluginLoader extends ClassLoader {
        PluginLoader(ClassLoader parent) {
            super(parent);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader host = ContextRetentionDemo.class.getClassLoader();
        PluginLoader plugin = new PluginLoader(host);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "host-worker");

        worker.setContextClassLoader(plugin);
        worker.start();
        started.await();

        System.out.println("worker alive = " + worker.isAlive());
        System.out.println("worker holds plugin = "
                + (worker.getContextClassLoader() == plugin));

        worker.setContextClassLoader(host);
        System.out.println("worker restored host = "
                + (worker.getContextClassLoader() == host));

        release.countDown();
        worker.join();
        System.out.println("worker stopped = " + !worker.isAlive());
    }
}
~~~

Chạy:

~~~bash
javac ContextRetentionDemo.java
java ContextRetentionDemo
~~~

Stdout xác định trong ví dụ:

~~~text
worker alive = true
worker holds plugin = true
worker restored host = true
worker stopped = true
~~~

**Phân tích:** worker đang đợi latch và vẫn sống. Trước khi reset, field TCCL của worker giữ chính object plugin. Thay bằng host cắt **đường giữ qua TCCL**, rồi chương trình giải phóng latch và join để kết thúc thread.

**Giới hạn:** ví dụ không chứng minh GC đã xảy ra. Biến local plugin trong main vẫn còn giữ loader, và loader trong ví dụ chưa được dùng để định nghĩa class plugin. Nó chỉ xác nhận cơ chế strong reference, không đo lượng metaspace. Trong production, thông thường để **worker tự phục hồi context trong finally** thay vì thread khác thay context khi nó đang chạy công việc.

## 6. Cách sửa cốt lõi: lưu và khôi phục previous TCCL

Mẫu cần ưu tiên:

~~~java
Thread current = Thread.currentThread();
ClassLoader previous = current.getContextClassLoader();

current.setContextClassLoader(pluginLoader);
try {
    invokePlugin();
} finally {
    current.setContextClassLoader(previous);
}
~~~

Đây là snippet đặt trong method có pluginLoader và invokePlugin() phù hợp.

Vì sao cần **finally**?

- Task có thể return sớm hoặc ném exception.
- Thread pool giữ worker sống sau khi task hoàn thành.
- Việc restore phải xảy ra trước khi worker nhận task tiếp theo.
- **previous** có thể là loader của host, loader của framework khác hoặc null. Không đoán rằng system loader luôn đúng.

**setContextClassLoader(null) không tương đương restore previous.** Null có ngữ nghĩa fallback của API, nhưng có thể không khôi phục đúng context của thread trước khi plugin chạy.

Nếu thao tác setContextClassLoader thất bại trước khi vào try (ví dụ vì ràng buộc bảo mật của runtime), đừng kết luận scope đã dùng loader mới. Cần xử lý lỗi theo hợp đồng framework.

**Nếu không cần TCCL, đừng đổi nó.** Khi API hỗ trợ truyền ClassLoader tường minh, sử dụng tham số đó có thể tránh sửa shared thread state.

## 7. Ví dụ Java 17+ số 2: executor phục hồi TCCL kể cả khi Callable lỗi

Tạo **ScopedContextDemo.java**:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ScopedContextDemo {
    static final class PluginLoader extends ClassLoader {
        PluginLoader(ClassLoader parent) {
            super(parent);
        }
    }

    static <T> Callable<T> withContext(
            ClassLoader pluginLoader,
            Callable<T> action) {

        return () -> {
            Thread current = Thread.currentThread();
            ClassLoader previous = current.getContextClassLoader();

            current.setContextClassLoader(pluginLoader);
            try {
                return action.call();
            } finally {
                current.setContextClassLoader(previous);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ClassLoader host = ScopedContextDemo.class.getClassLoader();
        PluginLoader plugin = new PluginLoader(host);

        ExecutorService pool = Executors.newSingleThreadExecutor(task -> {
            Thread worker = new Thread(task, "host-pool-1");
            worker.setContextClassLoader(host);
            return worker;
        });

        try {
            System.out.println("before = " + pool.submit(
                    () -> Thread.currentThread()
                            .getContextClassLoader() == host
            ).get());

            System.out.println("during = " + pool.submit(
                    withContext(plugin, () ->
                            Thread.currentThread()
                                    .getContextClassLoader() == plugin)
            ).get());

            System.out.println("after = " + pool.submit(
                    () -> Thread.currentThread()
                            .getContextClassLoader() == host
            ).get());

            try {
                pool.submit(withContext(plugin, () -> {
                    throw new IllegalStateException("boom");
                })).get();
            } catch (ExecutionException expected) {
                System.out.println("failure = "
                        + expected.getCause()
                                .getClass().getSimpleName());
            }

            System.out.println("after failure = " + pool.submit(
                    () -> Thread.currentThread()
                            .getContextClassLoader() == host
            ).get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac ScopedContextDemo.java
java ScopedContextDemo
~~~

Stdout:

~~~text
before = true
during = true
after = true
failure = IllegalStateException
after failure = true
~~~

Giải thích:

1. ThreadFactory host tạo worker với TCCL là host loader, không phụ thuộc context của thread đang submit.
2. Wrapper **withContext** đổi sang plugin loader trong phạm vi công việc.
3. Sau khi job kết thúc, **finally** phục hồi previous.
4. Khi job ném **IllegalStateException**, Future.get bọc nó vào **ExecutionException**, nhưng cleanup vẫn chạy.
5. Task cuối nhìn thấy host TCCL, cho thấy không còn plugin context trên worker đó.

**Đây chưa phải giải pháp cho tất cả reference leaks.** Callable có thể capture plugin loader và vẫn nằm trong task queue, Future hoặc callback registry do host giữ. Phải hủy/xóa các reference đó ở lifecycle unload.

## 8. Nguy cơ thứ hai: thread con kế thừa TCCL ở thời điểm tạo

JDK Thread API quy định context loader mặc định của thread mới được kế thừa từ thread tạo ra nó, nếu không bị cấu hình riêng.

~~~text
Thread A (host): TCCL = PluginLoader
        |
        | new Thread(...)
        v
Thread B: TCCL kế thừa = PluginLoader
        |
Thread A restore HostLoader
        |
Thread B vẫn có TCCL riêng trỏ PluginLoader
~~~

Chỉ restore A không khiến B tự restore. Nếu B sống hàng giờ, nó có thể giữ loader cũ.

### Ví dụ Java 17+ số 3

Tạo file **InheritedContextDemo.java**:

~~~java
public class InheritedContextDemo {
    static final class PluginLoader extends ClassLoader {
        PluginLoader(ClassLoader parent) {
            super(parent);
        }
    }

    public static void main(String[] args) throws Exception {
        Thread parent = Thread.currentThread();
        ClassLoader previous = parent.getContextClassLoader();
        PluginLoader plugin = new PluginLoader(previous);
        Thread child;

        try {
            parent.setContextClassLoader(plugin);

            child = new Thread(() -> {
                System.out.println("child inherited plugin = "
                        + (Thread.currentThread()
                                .getContextClassLoader()
                                instanceof PluginLoader));
            }, "background-child");

            System.out.println("before start = "
                    + (child.getContextClassLoader() == plugin));
        } finally {
            parent.setContextClassLoader(previous);
        }

        child.start();
        child.join();

        System.out.println("parent restored = "
                + (parent.getContextClassLoader() == previous));
    }
}
~~~

Chạy:

~~~bash
javac InheritedContextDemo.java
java InheritedContextDemo
~~~

Stdout:

~~~text
before start = true
child inherited plugin = true
parent restored = true
~~~

**before start = true** chứng minh child đã nhận context trước khi start. Trong ví dụ child kết thúc nên không khẳng định leak lâu dài; trong thực tế nếu child chạy mãi, nó có thể giữ PluginLoader.

### Vì sao thread pool có thể kế thừa ngoài ý muốn?

**Executors.newSingleThreadExecutor** không bắt buộc tạo worker ngay khi construct executor; worker có thể được tạo khi submit task đầu tiên. Nếu thread submitter lúc đó mang plugin TCCL, ThreadFactory mặc định có thể cho worker kế thừa loader plugin. ThreadFactory do host sở hữu nên **đặt baseline context loader rõ ràng** cho mọi worker.

## 9. Đúng ownership khi quản lý executor

| Thành phần | Chủ sở hữu | Khi unload plugin |
| --- | --- | --- |
| Host ExecutorService shared | Host | Không shutdown toàn bộ; dừng/cancel task thuộc plugin, chờ cleanup và reset TCCL trên worker. |
| Plugin-owned ExecutorService | Plugin/container | Ngừng nhận task, shutdown, awaitTermination, cancel nếu cần. |
| Plugin ScheduledExecutorService | Plugin | Hủy scheduled jobs, shutdown và bỏ references tới Runnable/Callable plugin. |
| Thread riêng do plugin tạo | Plugin | Dừng hợp tác bằng interrupt/cờ dừng, unblock, join phù hợp. |
| Host ThreadFactory | Host | Cài đặt TCCL baseline an toàn cho workers. |
| Host registry của listeners/callbacks | Host + plugin | Unregister callbacks và references trước khi kết thúc unload. |
| ThreadLocal trên shared worker | Scope sở hữu key/value | Remove trong finally khi scope kết thúc. |

**Không dùng Thread.stop** để cưỡng bức dừng worker: nó nguy hiểm và có thể phá invariants. Interrupt chỉ là tín hiệu, không chứng minh thread đã chết. Một task không phản hồi interrupt cần timeout và giao thức stop phù hợp, không nên được báo là teardown thành công vô điều kiện.

**Đừng đổi TCCL của worker đang bận từ một thread khác** trong production nếu chưa điều phối; điều đó có thể ảnh hưởng lookup đang diễn ra. Trình tự an toàn là ngừng nhận task mới, chờ/cancel task, cho cleanup trên chính thread chạy task thực hiện, rồi quản lý references.

## 10. ThreadLocal là một đường retention riêng

TCCL là một field trên Thread. ThreadLocal dùng cấu trúc **ThreadLocalMap**, nơi value có thể là instance do plugin loader định nghĩa.

~~~text
Live Thread
  |
  +-- contextClassLoader -> PluginLoader
  |
  +-- threadLocals / ThreadLocalMap
         |
         +-- Entry.value -> PluginObject
                                 |
                                 +-- defining PluginLoader
~~~

Key trong ThreadLocalMap có tính chất weak, nhưng **value vẫn có thể bị giữ** trên long-lived worker nếu scope không remove. Việc quên TCCL và quên ThreadLocal có thể đồng thời tạo hai đường giữ mạnh.

Mẫu cleanup cho ThreadLocal do scope sở hữu:

~~~java
ThreadLocal<Object> state = new ThreadLocal<>();

try {
    state.set(pluginObject);
    runPluginTask();
} finally {
    state.remove();
}
~~~

Đây là snippet minh họa, không phải chương trình hoàn chỉnh; cần pluginObject và runPluginTask phù hợp.

**Restore TCCL không tự gọi state.remove().** Ngược lại, remove ThreadLocal không tự reset context loader. Khi dùng cả hai, cleanup cả hai trong finally (và xử lý thứ tự/error nếu cleanup có thể ném exception).

Không dùng reflection vào nội bộ Thread để “quét sạch tất cả ThreadLocal” của framework mà không có quyền sở hữu rõ.

## 11. Quy trình unload/redeploy an toàn

~~~text
1. Đưa plugin vào trạng thái STOPPING
         |
         v
2. Chặn task/callback mới thuộc plugin
         |
         v
3. Hủy/drain queue và running tasks theo timeout
         |
         v
4. Trong mọi task/plugin callback:
   - restore TCCL trong finally
   - remove ThreadLocal value do scope sở hữu
   - đóng streams/sessions/resources
         |
         v
5. Shutdown và chờ threads/executors do plugin sở hữu
         |
         v
6. Unregister listener, scheduler, driver, MBean,
   logging/service hooks và cache/callback còn giữ plugin
         |
         v
7. Xóa host references tới plugin objects và loader
         |
         v
8. Quan sát class-loader retention qua nhiều lần redeploy
~~~

**Race conditions cần chú ý:**

- Task mới lọt vào queue ngay sau khi bắt đầu stop.
- Task đang chạy tạo thêm Thread kế thừa plugin TCCL.
- Pool thay worker bằng thread mới khi context không đúng.
- Future/cache vẫn giữ Callable capture plugin loader.
- Callback khác còn đang chạy khi registry đã đánh dấu plugin stopped.
- Exception khiến nhánh cleanup không thực thi.

Vì vậy unload có thể cần một trạng thái **quiescing/draining** rõ ràng, thay vì chỉ xóa object plugin khỏi map rồi hy vọng GC xử lý.

## 12. URLClassLoader.close không phải lệnh unload mọi class

Một số loader như **URLClassLoader** có **close()** để đóng tài nguyên và file handles. Nhưng nếu một Thread vẫn có TCCL trỏ tới URLClassLoader đã close, loader đó vẫn reachable.

Phải phân biệt:

- **Đóng resource handles**.
- **Dừng background work**.
- **Xóa references** tới ClassLoader và các object của plugin.
- **JVM thu hồi khi đủ điều kiện**.

Đừng tự ý close loader chia sẻ giữa nhiều plugin đang hoạt động.

## 13. Chẩn đoán bằng heap dump, JFR và jcmd

Nếu nghi leak sau redeploy, cần tìm **đường tham chiếu giữ loader cũ**, không chỉ quan sát một biểu đồ RAM.

### 13.1 Công cụ

Với JVM HotSpot có những lệnh phù hợp:

~~~bash
jcmd
jcmd <PID> help
jcmd <PID> VM.classloader_stats
jcmd <PID> GC.class_histogram
jcmd <PID> GC.heap_dump /path/to/heap.hprof
~~~

Cú pháp và tính khả dụng phụ thuộc JDK/JVM; chạy **jcmd <PID> help** để xác nhận. Heap dump có thể lớn, tốn thời gian và chứa dữ liệu nhạy cảm; không tạo trên production khi chưa có quy trình vận hành/cho phép.

- **JFR (Java Flight Recorder)**: theo dõi class load/unload events, GC và tài nguyên khi bật cấu hình phù hợp.
- **Eclipse Memory Analyzer (MAT)**: mở heap dump, dùng **Path to GC Roots**, incoming references và retained size.
- **Thread dump**: tìm các worker còn sống, stuck hoặc đang giữ callback cũ.
- **Metaspace/classloader metrics**: so sánh nhiều chu kỳ deploy với baseline.

### 13.2 Mẫu Path to GC Roots

Nếu nguyên nhân là TCCL, heap analyzer có thể cho đường:

~~~text
GC Root: Java Thread
   |
   -> java.lang.Thread
        |
        -> contextClassLoader
             |
             -> PluginClassLoader (old deployment)
~~~

Nếu nguyên nhân khác:

~~~text
GC Root
   |
   -> host ExecutorService / listener registry / cache
        |
        -> Future / Runnable / callback / PluginObject
             |
             -> plugin class / PluginClassLoader
~~~

Khi có cả hai, sửa một đường vẫn có thể không đủ. **Đường mạnh đến GC root** mới là bằng chứng giá trị; chỉ thấy object trong heap không tự chứng minh leak nếu GC chưa xảy ra.

### 13.3 Tránh kiểm thử phụ thuộc System.gc

Có thể dùng WeakReference<ClassLoader> trong thí nghiệm chẩn đoán, nhưng không kết luận chắc chắn loader phải biến mất sau **System.gc()**. GC có thể bị trì hoãn/tắt theo tùy chọn, class unloading không nhất thiết xảy ra ngay, stack local variables/debugger cũng có thể giữ reference.

Ba ví dụ Java ở trên cố ý kiểm tra các giá trị **TCCL==plugin**, **restore==host**, **thread inheritance**, không đòi GC xảy ra đúng thời điểm.

### 13.4 Đo qua nhiều vòng redeploy

1. Ghi số loader/classes/metaspace sau warm-up.
2. Deploy plugin và chạy workload đại diện.
3. Unload bằng cùng quy trình, không bỏ qua timeout.
4. Lặp nhiều vòng, so sánh các plugin loader cũ còn reachable.
5. Khi số lượng loader cũ tăng bất thường, lấy heap dump và tìm Path to GC Roots.
6. Đối chiếu source task/callback để xác định chỗ đặt TCCL mà không restore hoặc chỗ giữ strong reference.

Không kết luận có leak chỉ vì sau deploy lượng metaspace cao hơn ngay trước khi GC có cơ hội hoạt động.

## 14. Những hiểu nhầm thường gặp

- **“Plugin stop rồi, class chắc chắn unload.”** Sai: class unloading phụ thuộc reachability của defining loader và quyết định của JVM.
- **“TCCL chỉ là tên loader dạng String.”** Sai: nó là tham chiếu tới đối tượng ClassLoader.
- **“Thread pool tự bỏ worker sau mỗi task.”** Sai: worker thường được tái sử dụng.
- **“Restore context về null luôn đúng.”** Sai: phải phục hồi previous context, kể cả previous có thể null.
- **“Restore parent TCCL cũng restore tất cả child.”** Sai: child có context riêng sau khi được tạo.
- **“Pool tạo sớm thì không thể kế thừa plugin loader.”** Không bảo đảm worker cũng được tạo sớm.
- **“Cleanup TCCL cũng cleanup ThreadLocal.”** Sai: hai cơ chế khác nhau.
- **“Thread daemon không giữ objects.”** Sai: khi JVM còn sống, daemon thread vẫn có thể giữ references.
- **“Đã interrupt worker thì nó chắc chắn kết thúc.”** Sai: task phải hợp tác.
- **“URLClassLoader.close tự giải phóng tất cả class.”** Sai: nó không xóa references từ host.
- **“System.gc chưa unload = chắc chắn leak.”** Không đủ chứng cứ; cần heap path và đo theo thời gian.

## 15. Tự kiểm tra kiến thức

**Câu 1.** Host worker sống lâu giữ TCCL = PluginLoaderV1 sau plugin stop. Vì sao loader có thể chưa collectible?

**Đáp án:** Thread.contextClassLoader còn strong reference tới PluginLoaderV1 từ một live thread.

**Câu 2.** Vì sao dùng try/finally thay vì setContextClassLoader(plugin), gọi task rồi reset sau đó?

**Đáp án:** Task có thể ném exception/return sớm; finally phục hồi previous trong các đường thực thi phù hợp.

**Câu 3.** Main có plugin TCCL lúc new Thread, sau đó main restore trước child.start. Child còn có thể mang plugin loader không?

**Đáp án:** Có, vì Thread mới thường kế thừa context tại lúc tạo Thread.

**Câu 4.** Host Future giữ Runnable capture plugin object, nhưng TCCL đã restore. Leak vẫn có thể xảy ra?

**Đáp án:** Có, Runnable/Future là đường giữ riêng; cần dọn queue/registrations/host references.

**Câu 5.** ThreadLocalMap dùng weak key thì value plugin tự biến mất khi key không được giữ?

**Đáp án:** Không có bảo đảm lập tức; value có thể còn trong long-lived thread, cần remove trong finally.

**Câu 6.** Vì sao classloader.close không đủ để chứng minh class unloaded?

**Đáp án:** Close dọn resources/handles chứ không xóa mọi reference và không ép GC.

**Câu 7.** Tool nào cho biết ai đang giữ plugin loader cũ?

**Đáp án:** Heap dump với Path to GC Roots/dominator tree; JFR, jcmd, thread dumps và metrics hỗ trợ chẩn đoán.

**Câu 8.** Plugin owns ScheduledExecutorService. Bước cleanup cần gì?

**Đáp án:** Hủy scheduled tasks, shutdown và chờ kết thúc theo timeout, dọn callbacks, TCCL/ThreadLocal và references.

**Câu 9.** Nếu chỉ cần load provider và API nhận loader tường minh, có phải luôn đổi TCCL không?

**Đáp án:** Không. Truyền loader tường minh thường tránh thay state dùng chung khi phù hợp.

**Câu 10.** Sau khi dọn mọi strong references, JVM phải unload ngay không?

**Đáp án:** Không. Đủ điều kiện để thu hồi không đồng nghĩa chắc chắn thu hồi tức thời.

## 16. Ghi nhớ

~~~text
TCCL leak:
Long-lived host Thread
      -> contextClassLoader
      -> PluginLoader phiên bản cũ
      -> class metadata/objects được giữ

Phòng tránh:
Save previous TCCL
      -> set plugin TCCL trong scope ngắn
      -> invoke plugin
      -> finally restore previous
      -> cleanup ThreadLocal, callbacks, queued tasks,
         plugin threads/executors và host references
~~~

**Một thread sống lâu có thể giữ toàn bộ vòng đời ClassLoader của plugin cũ chỉ vì quên phục hồi TCCL.** Nhưng sửa TCCL chỉ giải quyết một đường retention; unload đáng tin cần quản lý đầy đủ workers, tasks, registries, resources và kiểm chứng GC roots qua nhiều vòng redeploy.

### Tài liệu tham khảo

- Thread API Java SE 17: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- ClassLoader API Java SE 17: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- ExecutorService API Java SE 17: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- ServiceLoader API Java SE 17: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- JLS §12.7 Unloading of Classes and Interfaces: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7
- IBM — Class loader memory leaks in enterprise Java: https://www.ibm.com/docs/en/was-nd/8.5.5?topic=cmlp-memory-leaks-in-java-platform-enterprise-edition-applications


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-class-loader-009">ServiceLoader và các API dùng loader ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# ServiceLoader và các API dùng thread context class loader (TCCL)

Trong Java, **`ServiceLoader`** là cơ chế *service provider interface* (SPI): ứng dụng định nghĩa một **service contract** (thường là interface hoặc abstract class), còn các thư viện/plugin đóng góp những **provider implementations** có thể được phát hiện lúc chạy. Điểm đặc biệt trong chủ đề class loader là overload:

```java
ServiceLoader<MyService> services = ServiceLoader.load(MyService.class);
```

Theo Java SE 17 API, `ServiceLoader.load(service)` sử dụng **thread context class loader** (TCCL) của thread hiện tại để tìm providers. Nhờ đó code thuộc một thư viện hoặc framework được nạp bởi loader cha vẫn có thể khám phá provider nằm trong loader con của ứng dụng/plugin — khi thread được gán đúng context loader.

**Không nên hiểu rằng tất cả API Java tự động dùng TCCL.** `ServiceLoader.load(service)` là trường hợp có hợp đồng rõ ràng; các API khác có chiến lược loader riêng và phải đọc đúng đặc tả của từng API.

## 1. Tại sao cần loader ngữ cảnh?

Hãy tưởng tượng hệ thống plugin:

```text
ApplicationClassLoader
    ├── spi.Greeting               (interface dùng chung)
    ├── app.Application            (code framework)
    └── java.util.ServiceLoader    (API từ JDK)
               ^
               | parent delegation
               |
PluginClassLoader
    ├── plugins.HelloGreeting      (implementation)
    └── META-INF/services/
        └── spi.Greeting           (provider declaration)
```

Mặc định, parent loader **không tự đi xuống toàn bộ child loaders** để tìm `plugins.HelloGreeting`. Nếu một thư viện chỉ dùng loader định nghĩa chính nó, thư viện có thể không nhìn thấy class của plugin.

TCCL mở ra một lựa chọn khác: framework hỏi thread “trong ngữ cảnh xử lý lần này, nên dùng loader nào để khám phá provider?” Khi ứng dụng thiết lập context loader thành `PluginClassLoader`, `ServiceLoader.load(Greeting.class)` có thể tìm provider qua child loader mà không đòi parent biết trước từng plugin.

Những khái niệm cần tách:

- **Defining loader:** loader thực sự định nghĩa một class. Danh tính runtime class dựa trên cặp `(binary name, defining loader)`.
- **Initiating loader:** loader tham gia yêu cầu nạp class, có thể ủy quyền cho parent và nhận class do parent định nghĩa.
- **Thread context class loader:** một tham chiếu `ClassLoader` gắn với `Thread`; thư viện có thể chủ động sử dụng nó để tra cứu providers/resources/classes.
- **Parent delegation:** chính sách mặc định của `ClassLoader.loadClass` ưu tiên parent trước khi gọi `findClass` ở child.

**TCCL không thay đổi defining loader của các class đã được nạp**, cũng không “bẻ ngược” parent delegation cho mọi lời gọi JVM. Nó chỉ quyết định loader nào được truyền/được API dùng ở những chỗ có chủ đích.

## 2. Lấy và đặt TCCL

API của `java.lang.Thread`:

```java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi code/framework có contract dùng TCCL.
} finally {
    thread.setContextClassLoader(previous);
}
```

Đây là **đoạn mã minh họa**, yêu cầu `pluginLoader` đã được cung cấp. Một số quy tắc:

1. `Thread.currentThread().getContextClassLoader()` trả context loader hiện hành, **có thể là `null`**.
2. `setContextClassLoader(...)` điều chỉnh context loader của thread; cách code khác phản ứng với thay đổi tùy API có đọc TCCL hay không.
3. Luôn khôi phục `previous` trong `finally` nếu thay đổi tạm, nhất là trên **thread pool**: thread được tái sử dụng cho request/plugin khác.
4. Thread mới thường **kế thừa TCCL của thread tạo ra nó** theo quy tắc API; đừng coi đó là cấu hình độc lập bất biến. Với executor, thread factory, framework hoặc môi trường tùy biến, hãy xác minh loader thực tế.
5. Đừng giữ strong references đến plugin loader trong TCCL của thread tồn tại lâu khi muốn unload/reload plugin; điều này có thể khiến loader và các lớp của nó không được thu gom.

Lưu ý: `null` trong một số API nạp lớp mang nghĩa bootstrap loader; trong API khác có thể có fallback khác. Không thay thế `null` bằng “system loader” nếu không có tài liệu API cho phép suy luận đó.

## 3. ServiceLoader là gì?

`java.util.ServiceLoader<S>` là một công cụ **phát hiện và tải provider** cho loại service `S`.

Ví dụ:

```java
package spi;

public interface Greeting {
    String message();
}
```

Một triển khai:

```java
package plugins;

import spi.Greeting;

public final class HelloGreeting implements Greeting {
    public String message() {
        return "hello-from-plugin";
    }
}
```

Để `ServiceLoader` phát hiện provider khi dùng **classpath/unnamed module**, JAR hoặc thư mục classpath của plugin chứa tệp cấu hình:

```text
META-INF/services/spi.Greeting
```

Nội dung tệp:

```text
plugins.HelloGreeting
```

Tên tệp là **binary name của service type** (`spi.Greeting`), không phải tên provider và không có hậu tố `.class`. Mỗi dòng có tên nhị phân của một provider implementation, có thể có chú thích với `#`. Theo đặc tả, provider-configuration files có mã hóa **UTF-8**; các bản ghi trùng provider name được bỏ qua theo quy tắc của `ServiceLoader`.

Trong classpath SPI thông thường, provider phải là một class cụ thể, có thể gán cho service type, và có **public constructor không tham số**. `ServiceLoader` khởi tạo nó khi quá trình duyệt yêu cầu provider instance. Đối với named modules, hợp đồng có thêm lựa chọn **provider factory method**, được trình bày riêng bên dưới.

### Ba thành phần của SPI

| Thành phần | Vị trí thường gặp | Vai trò |
|---|---|---|
| **Service API** | Dependency chia sẻ ở parent/application loader | Hợp đồng mà cả application và plugin dùng chung |
| **Provider implementation** | Plugin JAR hoặc module | Class thực hiện service |
| **Provider registration** | `META-INF/services/<binary-name>` hoặc `provides ... with ...` trong `module-info.java` | Cho runtime biết provider nào được quảng bá |

**Đặt API contract ở loader chung** là yêu cầu quan trọng khi giao tiếp qua ranh giới plugin. Nếu ứng dụng và plugin tự định nghĩa hai bản `spi.Greeting` khác nhau, cùng binary name chưa đủ: chúng là hai runtime types khác nhau và việc kiểm tra khả năng gán/ép kiểu có thể thất bại.

## 4. Các overload ServiceLoader và lựa chọn loader

Những API cần phân biệt:

| API | Cách chọn namespace/provider source | Thường dùng khi |
|---|---|---|
| `ServiceLoader.load(MyService.class)` | **TCCL của thread hiện tại** | Framework/SPI muốn tìm provider theo ngữ cảnh ứng dụng |
| `ServiceLoader.load(MyService.class, loader)` | **ClassLoader tường minh** | Ứng dụng/plugin manager biết loader cần dùng |
| `ServiceLoader.loadInstalled(MyService.class)` | **Platform class loader** (Java 17) | Tìm provider trong phạm vi platform-installed; không thay thế plugin lookup |
| `ServiceLoader.load(layer, MyService.class)` | **ModuleLayer** được chỉ định theo quy tắc module | Provider trong các named modules và layer liên quan |

Tên `loadInstalled` được giữ để tương thích với lịch sử Java; trong Java 9+ nó dùng **platform class loader**, không có nghĩa “tìm mọi JAR mà app vừa cài vào bất kỳ thư mục nào”.

Một chú ý quan trọng: `ServiceLoader.load(service, loader)` không có nghĩa “chỉ định nghĩa provider bằng loader này”. Loader có thể **ủy quyền parent**, và service type bản thân nó có thể đã được parent định nghĩa. Điều quan trọng là provider được tìm thấy trong namespace/dải nguồn có thể truy cập từ loader đang dùng.

### Không nhầm với Class.forName(String)

```java
Class<?> type = Class.forName("plugins.HelloGreeting");
```

Overload **một tham số** của `Class.forName` dùng **defining loader của class đang gọi**, và yêu cầu initialization. Nó **không mặc định dùng TCCL**. Vì thế hai câu lệnh nằm trong cùng method có thể nhìn thấy tập class khác nhau:

```java
ServiceLoader.load(Greeting.class);                  // TCCL
Class.forName("plugins.HelloGreeting");              // caller defining loader
Class.forName("plugins.HelloGreeting", false, loader); // explicit loader
```

Đây là các dòng **minh họa**, cần có `Greeting`, `loader` và provider tương ứng. Nếu framework đang chạy trong parent loader nhưng provider nằm ở plugin child loader, lời gọi `ServiceLoader.load(...)` theo TCCL có thể thành công trong khi `Class.forName(name)` từ mã parent thất bại.

## 5. Ví dụ Java 17+ chạy được: khám phá provider theo TCCL

Bài thử cố ý tách service API/app và plugin provider vào **hai thư mục classpath khác nhau**. Ứng dụng chạy với chỉ `out/app` trên classpath. Provider nằm trong `out/plugin`, được một `URLClassLoader` con đưa vào namespace tại runtime.

Cấu trúc:

```text
spi-demo/
├── src/
│   ├── spi/
│   │   └── Greeting.java
│   ├── plugins/
│   │   └── HelloGreeting.java
│   └── app/
│       └── SpiDemo.java
└── out/
    ├── app/
    └── plugin/
        └── META-INF/
            └── services/
                └── spi.Greeting
```

### 5.1. Interface ở lớp API chia sẻ

Tạo tệp **`src/spi/Greeting.java`**:

```java
package spi;

public interface Greeting {
    String message();
}
```

### 5.2. Provider ở plugin

Tạo tệp **`src/plugins/HelloGreeting.java`**:

```java
package plugins;

import spi.Greeting;

public final class HelloGreeting implements Greeting {
    public String message() {
        return "hello-from-plugin";
    }
}
```

Lớp `HelloGreeting` là `public`. Khi không khai báo constructor, Java tự cung cấp default no-argument constructor với quyền truy cập tương ứng lớp `public`, phù hợp với classpath SPI.

### 5.3. Ứng dụng dùng ServiceLoader

Tạo tệp **`src/app/SpiDemo.java`**:

```java
package app;

import spi.Greeting;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ServiceLoader;

public final class SpiDemo {
    static String discover() {
        var names = new ArrayList<String>();
        for (Greeting service : ServiceLoader.load(Greeting.class)) {
            names.add(service.message());
        }
        return names.isEmpty() ? "(none)" : String.join(",", names);
    }

    static String discover(ClassLoader loader) {
        var names = new ArrayList<String>();
        for (Greeting service :
                ServiceLoader.load(Greeting.class, loader)) {
            names.add(service.message());
        }
        return names.isEmpty() ? "(none)" : String.join(",", names);
    }

    public static void main(String[] args) throws Exception {
        ClassLoader appLoader = SpiDemo.class.getClassLoader();
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();

        try {
            current.setContextClassLoader(appLoader);
            System.out.println("App TCCL: " + discover());

            try (URLClassLoader plugin = new URLClassLoader(
                    new java.net.URL[] {
                        Path.of(args[0]).toUri().toURL()
                    },
                    appLoader)) {
                current.setContextClassLoader(plugin);

                System.out.println("Plugin TCCL: " + discover());
                System.out.println("Explicit app: "
                        + discover(appLoader));
                System.out.println("Explicit plugin: "
                        + discover(plugin));
                System.out.println("Same service API: "
                        + (Greeting.class
                                == plugin.loadClass("spi.Greeting")));

                current.setContextClassLoader(appLoader);
                System.out.println("Restored app TCCL: " + discover());
            }
        } finally {
            current.setContextClassLoader(previous);
        }
    }
}
```

Đây là **chương trình hoàn chỉnh có `main`**, giả định `args[0]` là thư mục chứa plugin classes/descriptor. Lệnh chạy bên dưới cung cấp chính xác đối số đó.

### 5.4. Tạo provider configuration

Bên trong `out/plugin`, tạo chính xác:

```text
META-INF/services/spi.Greeting
```

với nội dung một dòng:

```text
plugins.HelloGreeting
```

Đây là **tệp UTF-8 text**, không phải Java source và không mang hậu tố `.java`.

### 5.5. Biên dịch/chạy trên Linux/macOS/Git Bash

Từ thư mục `spi-demo`:

```bash
mkdir -p out/app out/plugin/META-INF/services

javac --release 17 -d out/app \
  src/spi/Greeting.java src/app/SpiDemo.java

javac --release 17 -cp out/app -d out/plugin \
  src/plugins/HelloGreeting.java

printf 'plugins.HelloGreeting\n' \
  > out/plugin/META-INF/services/spi.Greeting

java -cp out/app app.SpiDemo out/plugin
```

Lưu ý dấu `\` ở cuối các dòng lệnh `javac` là cách Bash tiếp tục lệnh sang dòng sau. Trên Windows PowerShell có thể viết mỗi lệnh `javac` trên một dòng thay vì dùng `\`.

Tạo thư mục và descriptor tương đương trong Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path out/app, out/plugin/META-INF/services | Out-Null
javac --release 17 -d out/app src/spi/Greeting.java src/app/SpiDemo.java
javac --release 17 -cp out/app -d out/plugin src/plugins/HelloGreeting.java
[System.IO.File]::WriteAllText(
    "out/plugin/META-INF/services/spi.Greeting",
    "plugins.HelloGreeting" + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
java -cp out/app app.SpiDemo out/plugin
```

Đầu ra được kiểm chứng khi biên dịch với `javac --release 17` và chạy trên JDK 21 (đúng APIs của Java 17):

```text
App TCCL: (none)
Plugin TCCL: hello-from-plugin
Explicit app: (none)
Explicit plugin: hello-from-plugin
Same service API: true
Restored app TCCL: (none)
```

### 5.6. Giải thích đầu ra từng dòng

1. **App TCCL: (none):** chỉ có `out/app` trong classpath chính, nên loader của app không thấy descriptor `META-INF/services/spi.Greeting` nằm riêng tại `out/plugin`.
2. **Plugin TCCL:** ứng dụng tạo `URLClassLoader` có URL tới `out/plugin`. Sau khi gán loader này làm TCCL, `ServiceLoader.load(Greeting.class)` khám phá provider.
3. **Explicit app:** dù thread đang có plugin TCCL, overload hai tham số được truyền `appLoader` vẫn chỉ tìm theo app loader, nên không thấy provider plugin.
4. **Explicit plugin:** khi truyền `plugin` tường minh, provider được phát hiện mà không cần dựa vào TCCL.
5. **Same service API: true:** plugin loader ủy quyền class `spi.Greeting` cho parent; provider và app đều dùng một runtime interface. Đây là điều kiện giúp vòng lặp `for (Greeting service : ...)` nhận implementation thành công.
6. **Restored app TCCL:** thay TCCL về app loader khiến lần khám phá mới không nhìn thấy provider plugin. `finally` ngoài cùng còn khôi phục TCCL ban đầu khi method kết thúc.

Chương trình không chỉnh sửa `java.class.path` để “thêm JAR động”; nó sử dụng **một loader có URL tới thư mục plugin**. Trong thực tế, URL đó thường chỉ đến **plugin JAR** thay vì thư mục. `URLClassLoader` là `AutoCloseable`, nên sử dụng `try-with-resources` để giải phóng các tài nguyên có thể đóng của loader; tuy nhiên đóng loader không tự unload mọi class ngay lập tức nếu còn tham chiếu sống.

## 6. ServiceLoader duyệt providers theo cách nào?

`ServiceLoader` hỗ trợ truy cập provider **lười (lazy)**: tạo một đối tượng `ServiceLoader` không đồng nghĩa khởi tạo ngay mọi provider instance.

Ví dụ:

```java
ServiceLoader<Greeting> loader =
        ServiceLoader.load(Greeting.class);

for (Greeting service : loader) {
    System.out.println(service.message());
}
```

- Đối tượng `loader` đại diện cho cơ chế khám phá/caching providers.
- Khi `iterator()` được duyệt, `ServiceLoader` có thể tải provider class và tạo provider instance khi cần.
- Các provider instances đã nạp được `ServiceLoader` giữ trong **cache của chính đối tượng ServiceLoader** theo hợp đồng API. Vì vậy việc duyệt lại cùng loader có thể trả lại instance đã cached thay vì tạo bản mới.
- Gọi `loader.reload()` xóa cache để việc khám phá và khởi tạo provider được thực hiện lại theo điều kiện hiện tại của nguồn/provider; **không tự thay defining class loader của các class đã nạp**.
- `ServiceLoader` **không an toàn để dùng đồng thời giữa nhiều thread** khi truy cập/mutate như cùng một object mà không có đồng bộ bên ngoài.

Một dạng sử dụng khác trên Java 17:

```java
ServiceLoader<Greeting> services =
        ServiceLoader.load(Greeting.class);

services.stream().forEach(provider -> {
    Class<? extends Greeting> type = provider.type();
    System.out.println(type.getName());

    Greeting instance = provider.get();
    System.out.println(instance.message());
});
```

`ServiceLoader.Provider<S>` cung cấp `type()` để xem type của provider và `get()` để lấy instance. Chú ý với named modules sử dụng **provider factory method**, `type()` có thể phản ánh return type của factory theo hợp đồng API; đừng suy diễn lúc nào cũng là tên class khai báo trong cấu hình giống classpath.

### reload() không thay thế việc quản lý plugin lifecycle

Nếu một plugin JAR thay đổi trên disk nhưng vẫn dùng **cùng loader** và cùng `ServiceLoader`, chỉ gọi `reload()` không bảo đảm JVM sẽ tự định nghĩa lại các class đã được nạp, cũng không tự đóng provider instances cũ. Hệ thống hot reload cần một quy trình rõ ràng: ngừng sử dụng instance cũ, giải phóng tài nguyên/thread, tạo defining loader mới khi thích hợp, tái khám phá providers và tránh trộn kiểu giữa các thế hệ loader.

## 7. Khi có nhiều providers

Một service interface có thể có nhiều implementations. Ví dụ provider descriptor:

```text
plugins.EnglishGreeting
plugins.VietnameseGreeting
plugins.FrenchGreeting
```

Mỗi dòng phải tham chiếu đúng một provider class có thể được loader tìm thấy và gán cho service interface. Đoạn trên chỉ là **mẫu metadata**; các lớp minh họa chưa được định nghĩa trong chương trình chạy phần 5.

Việc iterator duyệt providers theo thứ tự chịu những quy tắc cụ thể của `ServiceLoader` và class loader/module source. Với nhiều JAR/modules, **không nên thiết kế nghiệp vụ phụ thuộc tuyệt đối vào thứ tự provider đầu tiên** nếu không có contract đảm bảo. Nếu có nhiều implementation, hãy dùng một thuộc tính cấu hình, priority được thiết kế riêng, hoặc bước lọc/định danh provider thay vì tự coi “provider đầu tiên” là phiên bản mong muốn.

Cũng không nên cho rằng service discovery sẽ lọc providers theo tình trạng runtime: một provider có thể được tìm thấy nhưng **constructor** hoặc **provider() factory** báo lỗi khi thực sự được khởi tạo.

## 8. Cơ chế đăng ký provider trong named modules (JPMS)

Java 9+ hỗ trợ SPI trong **named modules** mà không nhất thiết phải dùng `META-INF/services` cho providers nằm trong module.

Ví dụ module chứa API:

```java
module demo.api {
    exports spi;
}
```

Module của consumer:

```java
module demo.app {
    requires demo.api;
    uses spi.Greeting;
}
```

Module của provider:

```java
module demo.provider {
    requires demo.api;
    provides spi.Greeting with plugins.HelloGreeting;
}
```

Ba đoạn là các tệp **`module-info.java` minh họa riêng biệt**; để chạy thực tế cần xây dựng source tree module, class definitions và module path phù hợp. Không ghép cả ba `module` declarations vào một file.

Ý nghĩa:

- `uses spi.Greeting;` cho biết module consumer dùng service contract đó.
- `provides spi.Greeting with plugins.HelloGreeting;` đăng ký provider cho service.
- `requires demo.api;` giúp consumer/provider đọc API module.
- `exports spi;` cho phép module khác dùng kiểu public `spi.Greeting`.

Với provider module, `ServiceLoader` có thể phát hiện providers thông qua module metadata; không nhất thiết cần `META-INF/services/...` nếu provider đã đăng ký trong module descriptor. Named-module provider có thể có **public no-args constructor** hoặc **public static provider() method** với các ràng buộc cụ thể được `ServiceLoader` kiểm tra. Phương thức factory `provider()` là cơ chế của named-module provider, **không phải** cách thay thế tự do cho constructor khi provider chỉ được khai báo bằng classpath configuration file.

Nếu cần khám phá providers trong một tập module được tổ chức theo **ModuleLayer**, cân nhắc:

```java
ServiceLoader<Greeting> providers =
        ServiceLoader.load(layer, Greeting.class);
```

Đây là snippet minh họa cần biến `layer` kiểu `ModuleLayer` đã được dựng; không phải chương trình chạy độc lập. Dùng module layer có ích khi framework tạo nhiều module layers riêng thay vì chỉ dựa vào loader ngữ cảnh của thread.

### Không nhầm module với loader

Module và class loader là hai khái niệm khác nhau. Một loader có thể định nghĩa classes của nhiều modules theo mapping JVM/JDK; class có thể thuộc named module hoặc unnamed module. Điều kiện module readability, exports và service binding là tầng bổ sung, không thể được giải quyết chỉ bằng cách gán TCCL.

## 9. Những API khác có thể liên quan tới loader ngữ cảnh

`ServiceLoader` là API Java SE tiêu biểu để nhìn thấy TCCL rõ ràng. Những hệ sinh thái khác cũng có **provider discovery** nhưng không được đánh đồng tất cả:

| API/hệ sinh thái | Quan hệ với loader ngữ cảnh | Điều cần kiểm tra |
|---|---|---|
| `ServiceLoader.load(service)` | Dùng **TCCL** theo contract | TCCL có thấy provider declaration/class không? |
| `ServiceLoader.load(service, loader)` | Dùng **loader tường minh** | Đã truyền đúng plugin loader chưa? |
| `Class.forName(name)` | Dùng **caller defining loader**, không tự dùng TCCL | Code gọi đang được loader nào định nghĩa? |
| `ClassLoader.getSystemClassLoader()` | Trả system loader, không phải TCCL | System loader có thấy plugin không? |
| `javax.xml.parsers.DocumentBuilderFactory.newInstance()` (JAXP) | Quy trình tìm provider có thể dùng context class loader theo tài liệu JAXP, cùng các nguồn cấu hình/fallback khác | Provider factory, system property, service declarations, module context |
| `javax.xml.transform.TransformerFactory.newInstance()` (JAXP) | Cơ chế provider lookup và TCCL có thể liên quan | Provider discovery và version của JDK |
| `java.sql.DriverManager` / JDBC driver SPI | Driver discovery dùng service-provider mechanism cùng các quy tắc đăng ký/truy cập riêng | Loader của driver, module, driver visibility, lifecycle |
| JNDI và thư viện container/plugin | Có thể sử dụng TCCL tùy API/provider/framework | Contract và cấu hình provider cụ thể |

**Không biến bảng thành khẳng định rằng mọi API luôn đọc TCCL trong mọi phiên bản.** Chẳng hạn `ResourceBundle` có overload gắn với caller/module hoặc loader được cung cấp tường minh; chỉ nhìn tên “resource loading” không đủ để kết luận mặc định lấy TCCL. `java.util.logging`, JDBC và các framework còn có quy tắc cache, singleton, module và classloader checks riêng. Hãy đọc JavaDoc chính xác của overload đang dùng.

### Ví dụ về JAXP

`DocumentBuilderFactory.newInstance()` và `TransformerFactory.newInstance()` là những factory APIs có cơ chế tìm implementation thay thế dựa trên cấu hình/provider discovery. Chúng không tương đương trực tiếp với gọi `ServiceLoader.load` bằng tay: quy trình có thể kiểm tra system properties, cấu hình JDK, service providers và default implementation. TCCL có thể quyết định provider nào được thấy trong một số nhánh tra cứu.

Ví dụ minh họa:

```java
import javax.xml.parsers.DocumentBuilderFactory;

DocumentBuilderFactory factory =
        DocumentBuilderFactory.newInstance();
System.out.println(factory.getClass().getName());
```

Đây là đoạn **minh họa bên trong method**, không phải ứng dụng độc lập. Tên implementation in ra **không nên hardcode** vì thay đổi theo JDK, provider configuration và class loader. Nếu cần chọn provider cụ thể, tham khảo overload factory nhận class name/loader hoặc cơ chế configuration được API đó công bố.

## 10. Những lỗi thường gặp khi dùng TCCL và ServiceLoader

### 10.1. Descriptor không nằm đúng vị trí

Sai:

```text
META-INF/service/spi.Greeting
META-INF/services/spi/Greeting
META-INF/services/plugins.HelloGreeting
```

Đúng:

```text
META-INF/services/spi.Greeting
```

Tên entry là service **binary name**, và nội dung là provider implementation **binary name**. Với archive, cần đảm bảo descriptor thực sự nằm trong JAR có thể đọc từ loader được chọn, không chỉ tồn tại ở source directory.

### 10.2. Provider code tồn tại nhưng ServiceLoader không thấy

Có thể do:

- Descriptor không được copy vào build output/JAR.
- TCCL trỏ tới **parent/application loader** trong khi plugin provider chỉ thuộc child loader.
- Đã gọi overload nhận loader tường minh nhưng truyền **loader sai**.
- Named module khai báo `provides`/`uses` hoặc module layer không phù hợp.
- Provider class nằm trong nguồn mà loader không thể thấy, hoặc dependency của provider không giải quyết được.
- Một container/framework override parent-first/child-first theo namespace, khiến API contract và implementation không dùng chung runtime type.

**Chẩn đoán tối thiểu:**

```java
ClassLoader context =
        Thread.currentThread().getContextClassLoader();

System.out.println("TCCL = " + context);
System.out.println("API defining loader = "
        + Greeting.class.getClassLoader());
```

Đây là snippet cần interface `Greeting` trong project. Đừng so sánh loader bằng chuỗi `toString()`: mỗi custom loader có thể ghi log khác nhau. Nếu cần so sánh runtime type, dùng `==` trên `Class<?>` hoặc `ClassLoader` references.

### 10.3. ServiceConfigurationError

`ServiceLoader` có thể ném **`java.util.ServiceConfigurationError`** khi thông tin cấu hình/provider không hợp lệ, ví dụ provider class không thể tìm thấy, không tương thích service type, thiếu constructor/provider factory phù hợp, lỗi khi khởi tạo provider, hoặc không instantiate được theo hợp đồng.

Ví dụ nếu descriptor viết:

```text
plugins.DoesNotExist
```

`ServiceLoader` có thể phát hiện lỗi **khi thực sự iterate/stream providers**, không nhất thiết ngay tại `ServiceLoader.load(...)`. `ServiceConfigurationError` là `Error` (không phải checked exception), nên đừng dựa vào `catch (Exception ex)` để che mọi vấn đề provider discovery. Trong startup hoặc plugin manager, hãy ghi lại service/provider/loader và cause; có thể fail fast hoặc cô lập plugin lỗi tùy policy đã thiết kế.

### 10.4. TCCL chưa khôi phục

Một tác vụ dùng thread pool đặt TCCL sang `PluginLoaderA` rồi quên khôi phục. Tác vụ sau dành cho plugin B chạy trên cùng thread có thể tìm provider của A hoặc giữ tham chiếu lâu dài đến A. Hãy dùng `try/finally` như ví dụ ở phần 2 và đặt loader theo scope nhỏ nhất.

### 10.5. Tự thêm implementation nhưng vẫn dùng cache cũ

`ServiceLoader` cache providers đã khám phá. Việc thêm một JAR hoặc đổi TCCL sau khi tạo một `ServiceLoader` **không nên xem là cập nhật tự động toàn bộ providers trong object cũ**. Tạo `ServiceLoader` mới với đúng loader hoặc gọi `reload()` theo nhu cầu và hiểu rằng các class đã nạp có identity/lifecycle riêng.

### 10.6. Duplicate service API theo child-first

Nếu plugin tự đóng gói và child-first tải bản riêng của `spi.Greeting`, implementation có thể không được coi là subtype của `spi.Greeting` ở application loader. Kết quả có thể là `ServiceConfigurationError`, `ClassCastException` hoặc `LinkageError` tùy đường nạp/gọi. Bố trí service API trong parent/shared loader và bảo đảm plugin delegate namespace API.

## 11. Checklist thiết kế SPI/plugin đúng loader

1. **Xác định service contract** là lớp/interface nào, được defining loader nào định nghĩa.
2. **Đặt shared API ở parent/shared class loader** nếu caller và provider cần trao đổi object theo cùng Java type.
3. **Chọn nguồn đăng ký**: classpath descriptor `META-INF/services/<service-binary-name>` hoặc named module `provides ... with ...`.
4. **Xác nhận package/class entry đúng** trong JAR output, không chỉ ở source.
5. **Chọn loader cho discovery có chủ đích**: TCCL (`ServiceLoader.load(service)`), explicit loader, hoặc `ModuleLayer`.
6. **Không bỏ quên các dependencies** của provider trong namespace phù hợp.
7. **Thiết lập và phục hồi TCCL** quanh lời gọi framework nếu cần; để ý thread pools và asynchronous tasks.
8. **Duyệt provider và xử lý lỗi cấu hình rõ ràng**, thay vì giả định constructor không bao giờ lỗi.
9. **Quản lý cache, lifecycle và unload** của provider instances/class loaders; `reload()` không tự đóng resource.
10. **Tránh phụ thuộc thứ tự providers** nếu contract không yêu cầu; dùng config/metadata để chọn implementation mong muốn.

### Một lưu ý về bảo mật

TCCL và `ServiceLoader` là cơ chế **mở rộng ứng dụng**, không phải cơ chế bảo mật. Mã provider thực thi trong tiến trình với khả năng của code đã triển khai; đừng tự động tải provider không tin cậy chỉ vì có entry `META-INF/services`. Kiểm soát nguồn JAR, chữ ký/quy trình deploy, module/access policy và các quyền bên ngoài JVM theo nhu cầu hệ thống.

## 12. Câu hỏi tự kiểm tra

**1. `ServiceLoader.load(MyService.class)` dùng loader nào theo mặc định?** TCCL của thread gọi.

**2. `Class.forName("my.Provider")` có tự dùng TCCL không?** Không. Overload một tham số dùng defining loader của **calling class**; muốn loader cụ thể thì dùng overload ba tham số.

**3. Vì sao framework parent không thấy provider ở child khi không dùng context loader?** Parent delegation là từ child lên parent, không phải tìm ngược từ parent xuống tất cả children.

**4. Descriptor classpath cho `spi.Greeting` có tên gì?** `META-INF/services/spi.Greeting`. Nội dung là tên nhị phân của provider class.

**5. `ServiceLoader.load(...)` có chắc khởi tạo mọi provider ngay lập tức không?** Không. Cơ chế discovery/instantiation có tính lazy khi duyệt provider.

**6. `ServiceLoader.reload()` có bắt JVM định nghĩa lại một class đã load không?** Không. Nó xóa cache trong đối tượng ServiceLoader; class loading/loader lifecycle vẫn riêng.

**7. TCCL đã đổi mà `ServiceLoader.load(service, appLoader)` vẫn không thấy plugin là lỗi không?** Không, vì overload có tham số loader dùng loader **được truyền**, không dùng TCCL.

**8. Service API bị định nghĩa ở cả parent và child, hai bản có cùng tên. Có đảm bảo tương thích không?** Không. Class identity còn phụ thuộc defining loader.

**9. Provider trong named module bắt buộc luôn đăng ký bằng `META-INF/services` không?** Không. Có thể đăng ký trong `module-info.java` bằng `provides ... with ...`.

**10. API Java nào có thể có cơ chế tra provider theo TCCL khác ServiceLoader?** Một số JAXP factory APIs và hệ sinh thái plugin/container; phải xem JavaDoc của API và overload cụ thể, không áp dụng máy móc cùng một quy tắc.

## 13. Nguồn chính thức

- Java SE 17 API `ServiceLoader` — provider configuration, lazy loading, caching, overloads và module usage: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- Java SE 17 API `Thread.getContextClassLoader / setContextClassLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- Java SE 17 API `ClassLoader` — parent delegation, defining/initiating loaders: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API `DocumentBuilderFactory` — factory discovery: https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/parsers/DocumentBuilderFactory.html
- Java Language Specification `7.7.2, `uses` và `provides` trong module declarations: https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7.2

**Ghi nhớ:** `ServiceLoader.load(service)` lấy **thread context class loader** để khám phá provider, còn `Class.forName(String)` dùng **caller defining loader**. Muốn plugin được nhận diện đúng, phải bảo đảm provider descriptor/module declaration có thể tìm thấy, service interface được chia sẻ bằng cùng runtime type, và context loader được đặt/khôi phục theo vòng đời tác vụ. Chọn loader chủ động tốt hơn việc hy vọng runtime luôn thấy mọi plugin.


</details>

- [Quay lại đầu trang](#back-to-top)
