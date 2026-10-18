# Gỡ nạp class và rò rỉ ClassLoader

Hệ thống plugin có một vòng đời mong muốn:

```text
load plugin
→ run plugin
→ stop plugin
→ bỏ mọi tham chiếu
→ plugin classes có thể được GC/unload
```

Nhưng “xóa plugin khỏi một `Map`” chưa chắc đủ. Một **ClassLoader định nghĩa (defining ClassLoader)** thường gắn với metadata và quan hệ của các class mà nó định nghĩa; ngược lại, `Class`, instance và nhiều cấu trúc khi chạy có thể dẫn tham chiếu trở lại ClassLoader.

Vì vậy chỉ một tham chiếu nhỏ từ vùng sống lâu sang plugin cũng có thể giữ cả đồ thị ClassLoader tồn tại sau khi triển khai lại (redeploy).

## <a id="class-unloading">Class chỉ có thể được gỡ nạp khi ClassLoader định nghĩa có thể được thu hồi</a>

Đối với class do ClassLoader do ứng dụng tạo ra định nghĩa, điều kiện cốt lõi là **ClassLoader định nghĩa phải trở nên có thể được GC thu hồi**. Một class như vậy không thể trở thành đối tượng để gỡ nạp khi ClassLoader định nghĩa của nó vẫn còn được tham chiếu và chưa thể được thu hồi.

Mô hình tư duy:

```text
reachable PluginClassLoader
→ classes do loader định nghĩa vẫn thuộc loader namespace đó
→ chưa có điều kiện để unload namespace

PluginClassLoader unreachable
→ loader có thể được GC
→ classes do loader định nghĩa có thể được unload
```

Các class do Bootstrap ClassLoader định nghĩa không có vòng đời kiểu ClassLoader plugin và không phải đối tượng để ứng dụng chủ động gỡ nạp.

### Vì sao class của ứng dụng thông thường gần như sống tới khi JVM kết thúc?

Trong một ứng dụng Java thông thường, Application/System ClassLoader là hạ tầng sống rất lâu, thường gần bằng vòng đời của tiến trình JVM. Vì ClassLoader này vẫn còn được tham chiếu, những class mà nó định nghĩa **thường không có vòng đời “nạp rồi gỡ nạp riêng từng class”** giống plugin.

```text
JVM process đang chạy
→ Application/System ClassLoader vẫn sống
→ application classes do nó định nghĩa thường vẫn thuộc loader namespace đang sống
→ không mong đợi từng class tự biến mất chỉ vì không còn instance
```

Việc gỡ nạp class trở nên đặc biệt quan trọng khi ta cố tình tạo **ranh giới ClassLoader có thể bỏ đi sau một vòng đời**:

```text
plugin loader
application-server deployment loader
hot-reload/devtools loader
isolated scripting/tooling loader
```

Ở các mô hình đó, ta muốn bỏ cả một thế hệ ClassLoader để toàn bộ không gian tên cũ có cơ hội được thu hồi. Đây là lý do chương này tập trung vào plugin/triển khai lại thay vì coi việc gỡ nạp là vòng đời bình thường của mọi class trong ứng dụng.

### “Có thể gỡ nạp” không có nghĩa “sẽ gỡ nạp ngay”

GC và việc gỡ nạp class phụ thuộc cách triển khai JVM, bộ thu gom rác, áp lực heap và thời điểm chạy. Ứng dụng không có API:

```java
// không tồn tại
Class.unloadNow(PaymentPlugin.class);
```

Ngay cả `System.gc()` chỉ là một yêu cầu/gợi ý cho GC, không phải hợp đồng đảm bảo một ClassLoader sẽ được thu hồi tại dòng mã kế tiếp.

## <a id="loader-retention">Những tham chiếu nào giữ một ClassLoader sống?</a>

Ta nên chẩn đoán dựa trên **khả năng còn được tham chiếu (reachability)**:

**GC root** là một điểm mà bộ thu gom rác xem như còn sống chắc chắn khi bắt đầu lần theo đồ thị đối tượng, ví dụ luồng đang sống hoặc các cấu trúc JVM sống lâu dẫn tới trạng thái `static`. Một đối tượng còn đường tham chiếu từ GC root thì chưa thể được thu hồi.

```text
GC root / đối tượng sống lâu
→ ...
→ đối tượng plugin / Class của plugin / ClassLoader của plugin
```

Các nguồn giữ lại phổ biến:

- luồng sống lâu có TCCL trỏ vào ClassLoader của plugin;
- bộ nhớ đệm do tầng cha/ứng dụng sở hữu giữ `Class<?>`, `Method`, instance hoặc ClassLoader của plugin;
- registry/bộ lắng nghe/event bus chưa gỡ đăng ký hàm gọi lại (callback) của plugin;
- executor/luồng do plugin tạo chưa dừng;
- registry của JDK/thư viện giữ đối tượng được plugin đăng ký;
- `ThreadLocal` trên luồng trong pool giữ đối tượng thuộc plugin;
- hook dọn dẹp hoặc hàm gọi lại bị giữ ngoài vòng đời plugin.

Ví dụ bộ nhớ đệm nguy hiểm:

```java
// Class này nằm ở Application ClassLoader và sống suốt tiến trình.
final class GlobalCache {
    static final Map<String, Class<?>> TYPES = new HashMap<>();
}

GlobalCache.TYPES.put("payment", pluginClass);
```

Sau khi plugin dừng, mục trên vẫn tạo chuỗi tham chiếu:

```text
GlobalCache static của ứng dụng
→ plugin Class
→ PluginClassLoader ở vai trò ClassLoader định nghĩa
```

### Trường static của plugin tự nó chưa đủ để gọi là rò rỉ

Đây là một điểm tinh tế nhưng quan trọng.

```text
PluginClassLoader
↔ Class của plugin
↔ đồ thị đối tượng static của plugin
```

Nếu toàn bộ đồ thị chỉ tham chiếu lẫn nhau và **không còn đường từ GC root hoặc đối tượng bên ngoài sống lâu đi vào đồ thị**, GC có thể thu hồi chu trình đó.

Rò rỉ xảy ra khi một đối tượng bên ngoài sống lâu vẫn còn đường tham chiếu tới một phần đồ thị của plugin.

## <a id="static-threadlocal-leaks">Static, ThreadLocal, bộ lắng nghe và các kiểu rò rỉ từ bộ nhớ đệm</a>

### Bộ nhớ đệm static do ClassLoader cha sở hữu

Đây là mẫu điển hình:

```text
class ở Application ClassLoader
→ bộ nhớ đệm static
→ instance/Class của plugin
→ PluginClassLoader
```

Cách sửa phải nằm ở vòng đời: xóa phần tử khi plugin dừng, hoặc thiết kế bộ nhớ đệm với ngữ nghĩa tham chiếu yếu khi bài toán thực sự phù hợp.

### ThreadLocal trên luồng trong nhóm luồng (thread pool)

```java
threadLocal.set(pluginObject);
```

Nếu luồng thuộc máy chủ hoặc executor sống lâu hơn plugin, giá trị có thể giữ đối tượng của plugin và ClassLoader. Với `ThreadLocalMap`, tham chiếu tới key có ngữ nghĩa tham chiếu yếu, nhưng value vẫn có thể bị giữ cho tới khi phần tử được dọn; vì vậy “mất tham chiếu tới key của ThreadLocal” không phải chiến lược dọn dẹp đáng tin cậy.

Mẫu xử lý:

```java
try {
    threadLocal.set(pluginContext);
    runPlugin();
} finally {
    threadLocal.remove();
}
```

### Bộ lắng nghe (listener/subscriber)

```text
event bus của ứng dụng
→ bộ lắng nghe do plugin triển khai
→ plugin Class
→ ClassLoader
```

Khi plugin dừng phải gỡ đăng ký bộ lắng nghe.

### TCCL

TCCL đã học ở chương trước:

```text
luồng trong pool sống lâu
→ contextClassLoader
→ PluginClassLoader
```

Vì vậy việc lưu, gán rồi khôi phục TCCL trong `finally` vừa là quy tắc về tính đúng đắn vừa giúp phòng tránh rò rỉ.

## <a id="redeploy-leak">Rò rỉ khi triển khai lại (redeploy) plugin xảy ra như thế nào?</a>

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

## <a id="unloading-observation">Quan sát việc gỡ nạp: phụ thuộc GC, không mang tính xác định</a>

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

## <a id="end-to-end-synthesis">Mô hình tổng hợp: từ bytecode đến gỡ nạp ClassLoader</a>

Toàn bộ module giờ nối thành một câu chuyện:

```text
trục vòng đời chính
bytecode
→ yêu cầu nạp đi qua quy tắc ưu tiên cha/ủy quyền và cách tìm kiếm của ClassLoader tùy chỉnh
→ một ClassLoader cuối cùng định nghĩa class có tên thông thường
→ tên nhị phân + ClassLoader định nghĩa xác lập định danh kiểu khi chạy
→ liên kết (linking) chuẩn bị kiểu cho việc sử dụng
→ sử dụng chủ động (active use) kích hoạt khởi tạo
→ đối tượng/class tồn tại khi vẫn còn được tham chiếu
→ class của ClassLoader tùy chỉnh chỉ có cơ hội được gỡ nạp khi đồ thị ClassLoader định nghĩa không còn được GC root giữ lại

cơ chế tìm kiếm/phát hiện chạy song song với trục vòng đời
TCCL
→ có thể cung cấp ngữ cảnh tìm kiếm khác để khung phần mềm thấy provider của ứng dụng/ClassLoader con

API tài nguyên của Class / ClassLoader
→ tìm tài nguyên trong không gian tên mà ClassLoader nhìn thấy
→ việc tìm tài nguyên không tự nó định nghĩa một class
```

Nếu giữ mô hình tư duy này, các lỗi ClassLoader không còn là một nhóm exception rời rạc. Chúng thường quay về bốn câu hỏi: **bytes được tìm bởi loader nào, type được định nghĩa bởi loader nào, thao tác đang dùng ngữ cảnh tìm kiếm nào, và tham chiếu nào vẫn giữ loader sống**.
