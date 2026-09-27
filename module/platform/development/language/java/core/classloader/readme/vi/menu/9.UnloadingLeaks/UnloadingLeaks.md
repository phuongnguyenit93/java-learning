# Class Unloading và ClassLoader Leak

Hệ thống plugin có một vòng đời mong muốn:

```text
load plugin
→ run plugin
→ stop plugin
→ bỏ mọi tham chiếu
→ plugin classes có thể được GC/unload
```

Nhưng “xóa plugin khỏi một `Map`” chưa chắc đủ. Một defining ClassLoader thường gắn với metadata và quan hệ của các class mà nó định nghĩa; ngược lại, `Class`, instance và nhiều cấu trúc runtime có thể dẫn tham chiếu trở lại loader.

Vì vậy chỉ một tham chiếu nhỏ từ vùng sống lâu sang plugin cũng có thể giữ cả đồ thị loader tồn tại sau khi redeploy.

## <a id="class-unloading">Class chỉ có thể unload khi defining loader có thể được thu hồi</a>

Đối với class do loader do ứng dụng tạo ra định nghĩa, điều kiện cốt lõi là **defining loader phải trở nên có thể được GC thu hồi**. JVM không unload riêng tùy ý một class trong khi defining loader của nó vẫn còn reachable.

Mô hình tư duy:

```text
reachable PluginClassLoader
→ classes do loader định nghĩa vẫn thuộc loader namespace đó
→ chưa có điều kiện để unload namespace

PluginClassLoader unreachable
→ loader có thể được GC
→ classes do loader định nghĩa có thể được unload
```

Các class do bootstrap loader định nghĩa không có vòng đời kiểu plugin loader và không phải đối tượng để ứng dụng unload.

### Vì sao application class thông thường gần như sống tới khi JVM kết thúc?

Trong một ứng dụng Java thông thường, Application/System ClassLoader là hạ tầng sống rất lâu, thường gần bằng vòng đời của tiến trình JVM. Vì loader này vẫn còn được tham chiếu, những class mà nó định nghĩa **thường không có vòng đời “load rồi unload riêng từng class”** giống plugin.

```text
JVM process đang chạy
→ Application/System ClassLoader vẫn sống
→ application classes do nó định nghĩa thường vẫn thuộc loader namespace đang sống
→ không mong đợi từng class tự biến mất chỉ vì không còn instance
```

Class unloading trở nên đặc biệt quan trọng khi ta cố tình tạo **ranh giới ClassLoader có thể bỏ đi sau một vòng đời**:

```text
plugin loader
application-server deployment loader
hot-reload/devtools loader
isolated scripting/tooling loader
```

Ở các mô hình đó, ta muốn bỏ cả một thế hệ loader để toàn bộ namespace cũ có cơ hội được thu hồi. Đây là lý do chương này tập trung vào plugin/redeploy thay vì coi unloading là vòng đời bình thường của mọi class trong ứng dụng.

### “Có thể unload” không phải “sẽ unload ngay”

GC và class unloading phụ thuộc cách triển khai JVM, garbage collector, áp lực heap và thời điểm chạy. Ứng dụng không có API:

```java
// không tồn tại
Class.unloadNow(PaymentPlugin.class);
```

Ngay cả `System.gc()` chỉ là một yêu cầu/gợi ý cho GC, không phải hợp đồng đảm bảo một loader sẽ được thu hồi tại dòng code kế tiếp.

## <a id="loader-retention">Những tham chiếu nào giữ một ClassLoader sống?</a>

Ta nên chẩn đoán dựa trên reachability (khả năng còn được tham chiếu):

**GC root** là một điểm mà garbage collector xem như còn sống chắc chắn khi bắt đầu lần theo object graph, ví dụ thread đang sống hoặc các runtime structure sống lâu dẫn tới static state. Một object còn đường tham chiếu từ GC root thì chưa thể được thu hồi.

```text
GC root / object sống lâu
→ ...
→ plugin object / plugin Class / plugin ClassLoader
```

Các nguồn giữ lại phổ biến:

- thread sống lâu có TCCL trỏ vào plugin loader;
- cache do parent/application sở hữu giữ `Class<?>`, `Method`, instance hoặc loader của plugin;
- registry/listener/event bus chưa gỡ đăng ký callback của plugin;
- executor/thread do plugin tạo chưa dừng;
- registry của JDK/thư viện giữ object được plugin đăng ký;
- `ThreadLocal` trên thread trong pool giữ object thuộc plugin;
- hook dọn dẹp hoặc callback bị giữ ngoài plugin lifecycle.

Ví dụ cache nguy hiểm:

```java
// Class này nằm ở application loader và sống suốt process.
final class GlobalCache {
    static final Map<String, Class<?>> TYPES = new HashMap<>();
}

GlobalCache.TYPES.put("payment", pluginClass);
```

Sau khi plugin stop, entry trên vẫn tạo chuỗi tham chiếu:

```text
application static GlobalCache
→ plugin Class
→ defining PluginClassLoader
```

### Static field trong plugin tự nó chưa đủ để gọi là leak

Đây là một điểm tinh tế nhưng quan trọng.

```text
PluginClassLoader
↔ plugin Class
↔ plugin static object graph
```

Nếu toàn bộ đồ thị chỉ tham chiếu lẫn nhau và **không còn đường từ GC root/object bên ngoài sống lâu vào graph**, GC có thể thu hồi chu trình đó.

Leak xảy ra khi có một root bên ngoài sống lâu giữ lại một phần đồ thị.

## <a id="static-threadlocal-leaks">Static, ThreadLocal, listener và các kiểu leak từ cache</a>

### Parent-owned static cache

Đây là mẫu điển hình:

```text
class ở application loader
→ static cache
→ plugin instance/Class
→ PluginClassLoader
```

Cách sửa phải nằm ở vòng đời: xóa entry khi plugin dừng, hoặc thiết kế cache với ngữ nghĩa tham chiếu yếu khi bài toán thực sự phù hợp.

### ThreadLocal trên thread trong pool

```java
threadLocal.set(pluginObject);
```

Nếu thread thuộc server/executor sống lâu hơn plugin, value có thể giữ plugin object và loader. Với `ThreadLocalMap`, key reference có weak semantics nhưng value vẫn có thể bị giữ cho tới khi entry được dọn; vì vậy “mất reference tới ThreadLocal key” không phải chiến lược dọn dẹp đáng tin cậy.

Mẫu xử lý:

```java
try {
    threadLocal.set(pluginContext);
    runPlugin();
} finally {
    threadLocal.remove();
}
```

### Listener/subscriber

```text
application event bus
→ listener implemented by plugin
→ plugin Class
→ loader
```

Khi plugin dừng phải gỡ đăng ký listener.

### TCCL

TCCL đã học ở chương trước:

```text
long-lived pooled Thread
→ contextClassLoader
→ PluginClassLoader
```

Vì vậy save/set/restore trong `finally` là cả quy tắc về tính đúng đắn lẫn quy tắc phòng tránh leak.

## <a id="redeploy-leak">Redeploy/plugin lifecycle leak xảy ra như thế nào?</a>

Giả sử phiên bản 1 được nạp:

```text
PluginLoaderV1
→ PaymentPlugin v1
```

Sau redeploy:

```text
PluginLoaderV2
→ PaymentPlugin v2
```

Nếu V1 còn bị giữ:

```text
application roots
├→ V1 listener/cache/thread/TCCL
│   ↓
│  PluginLoaderV1
│   ↓
│  toàn class graph v1
│
└→ PluginLoaderV2
    ↓
   class graph v2
```

Mỗi redeploy có thể tích lũy thêm thế hệ loader. Đây là lý do application server hoặc plugin host lâu ngày có thể tăng metaspace/heap dù object nghiệp vụ cũ tưởng như đã bị “xóa”.

Một trình tự shutdown thực tế nên dựa trên quyền sở hữu:

```text
1. ngừng nhận công việc mới cho plugin
2. dừng/join thread và executor do plugin sở hữu
3. gỡ đăng ký listener/provider/driver/callback đã đăng ký
4. xóa ThreadLocal và khôi phục TCCL
5. xóa các tham chiếu cache/registry do parent sở hữu
6. đóng resource/JAR handle của loader nếu loader hỗ trợ close
7. bỏ tham chiếu mạnh cuối cùng tới plugin instance/Class/loader
```

`URLClassLoader.close()` hữu ích để giải phóng resource JAR/file nhưng **không phải lệnh unload**. Nếu một cache toàn cục vẫn giữ `pluginClass`, loader vẫn reachable.

## <a id="unloading-observation">Quan sát unloading: phụ thuộc GC, không mang tính xác định</a>

Ta có thể dùng `WeakReference` để viết một thí nghiệm có giới hạn:

`WeakReference<T>` giữ một tham chiếu **không đủ mạnh để tự ngăn object được GC thu hồi**. Vì vậy nó hữu ích để quan sát “loader còn sống không?” mà không vô tình biến chính biến quan sát thành một strong reference giữ loader sống.

```java
ClassLoader loader = createPluginLoader();
Class<?> pluginClass =
        loader.loadClass("com.example.plugins.PaymentPlugin");

WeakReference<ClassLoader> loaderRef =
        new WeakReference<>(loader);

pluginClass = null;
loader = null;

System.gc(); // chỉ là yêu cầu, không phải đảm bảo

System.out.println(loaderRef.get());
```

Nếu `loaderRef.get()` vẫn khác `null`, chưa thể kết luận ngay là leak: GC có thể chưa chạy/thu hồi loader. Ngược lại, nếu sau áp lực GC có kiểm soát và công cụ chẩn đoán loader luôn còn reachable, cần tìm chuỗi tham chiếu.

> **Nâng cao — chẩn đoán runtime:** với HotSpot/JDK 21, unified logging có thể hỗ trợ việc quan sát:

```text
-Xlog:class+load=info,class+unload=info
```

Đây là cơ chế chẩn đoán của một cách triển khai JVM, không phải bảo đảm của ngôn ngữ Java. Khi chẩn đoán trong môi trường thực tế còn có thể dùng heap dump, JFR hoặc profiler phù hợp để tìm đường tham chiếu từ GC root tới loader bị giữ lại.

Checklist suy luận hữu ích hơn việc gọi `System.gc()` lặp lại:

```text
plugin đã dừng thật chưa?
→ thread/executor còn hoạt động?
→ TCCL đã được khôi phục?
→ ThreadLocal đã được xóa?
→ listener/provider đã được gỡ đăng ký?
→ parent/cache toàn cục còn giữ Class/instance?
→ loader/JAR handle đã được đóng khi cần?
→ còn tham chiếu mạnh tới loader?
```

Toàn bộ module giờ nối thành một câu chuyện:

```text
bytes
→ loader tìm và define
→ parent delegation quyết định namespace lookup
→ defining loader tham gia type identity
→ TCCL hỗ trợ discovery qua visibility boundary
→ loader cũng tìm resource
→ active use kích hoạt initialization
→ khi lifecycle kết thúc, chỉ graph không còn reachable mới có cơ hội unload
```

Nếu giữ mô hình tư duy này, các lỗi ClassLoader không còn là một nhóm exception rời rạc. Chúng thường quay về bốn câu hỏi: **bytes được tìm bởi loader nào, type được định nghĩa bởi loader nào, thao tác đang dùng ngữ cảnh tìm kiếm nào, và tham chiếu nào vẫn giữ loader sống**.
