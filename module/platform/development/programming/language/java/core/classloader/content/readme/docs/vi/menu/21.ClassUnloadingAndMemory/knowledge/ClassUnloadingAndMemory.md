<a id="back-to-top"></a>

# Dỡ lớp và bộ nhớ

## Menu
- [Điều kiện để lớp và defining loader có thể được thu gom](#class-unloading-and-memory-001)
- [Vì sao không có thao tác unload tùy ý một lớp thông thường](#class-unloading-and-memory-002)
- [Phân biệt mất tham chiếu đối tượng, đóng loader và class unloading](#class-unloading-and-memory-003)
- [Quan hệ giữa Class, đối tượng instance và loader](#class-unloading-and-memory-004)
- [Metaspace trong HotSpot và giới hạn của mô hình method area](#class-unloading-and-memory-005)
- [Static field, cache reflection và tham chiếu từ ứng dụng chủ](#class-unloading-and-memory-006)
- [Thread, ThreadLocal, context class loader và tác vụ chưa dừng](#class-unloading-and-memory-007)
- [Listener, registry, ServiceLoader cache và nguyên nhân giữ plugin](#class-unloading-and-memory-008)
- [Giải phóng JAR handle và tránh giữ file sau reload](#class-unloading-and-memory-009)
- [Quan sát unloading và tránh kết luận từ một lần gọi System.gc](#class-unloading-and-memory-010)

## <a id="class-unloading-and-memory-001">Điều kiện để lớp và defining loader có thể được thu gom</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện để lớp và defining loader có thể được thu gom

## 1. Vì sao “object đã hết dùng” không đồng nghĩa “class đã được dỡ”?

Trong Java, vòng đời của **instance**, **runtime class** và **class loader object** là ba chuyện khác nhau. Garbage collector (GC) có thể thu hồi một instance khi nó không còn reachable, trong khi metadata của class tạo ra instance ấy vẫn tồn tại vì JVM còn cần class đó. Muốn một class do custom loader định nghĩa trở thành ứng viên **class unloading**, điều kiện liên quan đến **defining loader** của class.

Theo **Java Language Specification 17, `12.7`**:

- Một triển khai Java **có thể** unload class/interface.
- Một class/interface **chỉ có thể được unload nếu và chỉ nếu defining class loader của nó có thể được garbage collector thu hồi**.
- Class/interface do **bootstrap loader** nạp không được unload.
- Unloading là **tối ưu bộ nhớ**, không phải thao tác mà chương trình Java có thể yêu cầu rồi chờ một thời điểm chắc chắn.

Đây là điều kiện về **khả năng đủ điều kiện**, không phải lời hứa GC chắc chắn xảy ra. Ngay khi mọi tham chiếu tới custom loader đã hết, JVM vẫn có thể trì hoãn hoặc không thực hiện class unloading trong một khoảng chạy cụ thể.

Một mô hình tư duy:

```text
Ứng dụng còn giữ instance / Class<?> / loader / method handle
                         |
                         v
          Custom defining loader còn reachable
                         |
                         v
         Classes của loader chưa đủ điều kiện unload

Ứng dụng thả mọi strong roots tới plugin + loader
                         |
                         v
      Defining loader có thể trở nên unreachable
                         |
                         v
      Classes của loader có thể đủ điều kiện unload
                         |
                         v
           JVM/GC có thể thực hiện unloading
          (không bảo đảm thời điểm hay số lần)
```

Cần phân biệt **điều kiện cần/đủ theo khả năng** với **quyết định thực thi**: chỉ khi defining loader có thể reclaim thì class mới có thể unload, nhưng việc loader có thể reclaim **không bắt buộc** VM unload ngay.

## 2. Defining loader là gì, khác initiating loader thế nào?

JVM xác định runtime class bằng **binary name + defining class loader**. Ví dụ cùng `plugin.Task` có thể được custom loader A và B định nghĩa thành hai class khác nhau:

```text
("plugin.Task", LoaderA) ≠ ("plugin.Task", LoaderB)
```

Trong đó:

- **Defining loader**: loader trực tiếp định nghĩa runtime class (thường thông qua `defineClass`, hoặc cơ chế VM tương ứng).
- **Initiating loader**: loader được VM ghi nhận đã khởi xướng việc nạp class; có thể chỉ ủy quyền cho parent.
- **Parent loader**: loader mà một `ClassLoader` thường hỏi trước theo parent delegation.

Ví dụ `child.loadClass("java.lang.String")` có thể trả về chính `String.class` do bootstrap định nghĩa. Child là nơi code gọi lookup nhưng **không trở thành defining loader** của `String`. Class unloading liên quan **defining loader thực sự**, không đơn giản loader từng trả `Class<?>`.

Với ordinary class:

```java
ClassLoader owner = someClass.getClassLoader();
```

`someClass` phải là `Class<?>` được lấy hợp lệ. Phương thức trên giúp biết defining loader mà Java API cho phép quan sát; `null` biểu thị bootstrap đối với class thường do bootstrap định nghĩa. Với primitive/void/array có quy tắc riêng, nên không suy ra luôn có một class file mà bootstrap đã đọc.

### Vì sao loader còn sống thì không unload class của nó?

Giả sử loader L còn reachable. Sau đó code có thể yêu cầu L nạp một class D chưa được nạp, D lại tham chiếu class C từng tồn tại. Nếu JVM trước đó đã unload C nhưng vẫn giữ L, việc nạp lại C có thể tạo thay đổi không quan sát được một cách trong suốt: static state, initialization side effects, native state, hoặc `Class<?>` identity có thể khác. JLS `12.7` vì vậy gắn điều kiện unloading của class với khả năng reclaim **defining loader**.

Điểm suy ra quan trọng:

- **Không thể yêu cầu unload một class đơn lẻ tùy ý trong loader vẫn sống** bằng cách xóa một biến reference.
- **Một loader dùng lâu dài** thường giữ tập class đã định nghĩa cho vòng đời của loader đó.
- **Để nạp phiên bản mới** của cùng binary name, hệ plugin thường tạo **defining loader mới**, không cố định nghĩa lại class trong loader cũ.

## 3. Thế nào là “có thể được GC thu gom”?

Một Java object đủ điều kiện GC khi không còn reachable từ các **GC roots** theo cơ chế reachability của VM, có xét những quy tắc đặc biệt của references. Class loader cũng là một object. Nếu có **đường strong reachability** tới loader hoặc tới object/class khiến loader phải được giữ sống, loader chưa thể được thu gom.

Các nguồn giữ loader còn sống thường gặp:

| Nguồn giữ tham chiếu | Ví dụ | Vì sao ảnh hưởng class unloading? |
|---|---|---|
| Biến/cấu trúc dữ liệu ứng dụng sống lâu | `static Map<String, ClassLoader>` | Registry ở host giữ strong reference tới loader |
| Instance của class do plugin loader định nghĩa | Plugin instance còn nằm trong host cache | Object còn sống cần runtime class tương ứng |
| `Class<?>` token | `static Class<?> cached = pluginClass;` | Class metadata/token đang được code sống tham chiếu |
| Reflection handles | `Method`, `Constructor`, `Field`, method handle hoặc lookup gắn với plugin class | Có thể giữ declaring class/related types reachable |
| Thread context class loader (TCCL) | Worker thread `setContextClassLoader(pluginLoader)` rồi không restore | Thread còn sống giữ loader |
| Thread-local state | `ThreadLocal` value chứa plugin instance | Worker sống lâu tiếp tục giữ object |
| Thread do plugin tạo | `Runnable`/stack/callback hoặc thread state còn tham chiếu plugin | Code/object thuộc plugin còn được dùng |
| Executor/scheduled task | Job/callback lambda thuộc plugin còn trong queue | Scheduled reference giữ classes/instances/loader |
| Event listeners | Host listener registry chứa plugin listener | Host trở thành root tới plugin |
| JVM/native integrations | JNI global references, native handles còn lưu Java objects | Reference từ native side có thể giữ loader graph |
| Cache ở thư viện dùng chung | Singleton host giữ reflection metadata hoặc proxy của plugin | Parent/shared code giữ references tới class của child |

**Không phải mọi reference đều mạnh.** `WeakReference` tới loader/class **không tự** giữ referent sống. `SoftReference` và `PhantomReference` có semantics khác nhau; không nên coi chúng như một cơ chế xác nhận unload tức thì. Một `WeakReference` chỉ hỗ trợ **quan sát** GC, không ra lệnh GC.

### Đường reachability thường là gián tiếp

Không cần một biến có tên `pluginLoader` vẫn còn sống. Đường giữ loader có thể qua nhiều objects:

```text
GC root
   |
   v
Host static Registry
   |
   v
Plugin listener instance
   |
   v
Runtime Class của listener
   |
   v
Defining PluginClassLoader
```

Trong hệ thống thật, đường có thể phức tạp hơn do reflection, class mirrors, method handles, class-loader data và cấu trúc JVM. Mấu chốt là **toàn bộ graph**, không phải chỉ tên biến của loader.

## 4. Instance đã được thu gom nhưng class vẫn còn là chuyện bình thường

Giả sử:

```java
PluginTask task = new PluginTask();
task.run();
task = null;
```

Đây là đoạn minh họa, cần class `PluginTask` và xử lý `run()` phù hợp. Dòng `task = null` chỉ thay giá trị **một biến**, không chứng minh không còn reference khác tới object. Kể cả instance đó thực sự được GC, defining loader vẫn có thể được giữ bởi registry, code đang chạy, các `Class<?>` tokens khác, instance khác cùng loader, v.v.

Tương tự, một class được loader định nghĩa có thể không hề có instance nào mà vẫn không unload, vì defining loader còn reachable.

| Trạng thái | Suy luận có căn cứ |
|---|---|
| Object bị GC | Object đó hết reachable, **không** chứng minh class unload |
| Không còn instances quan sát được | **Không đủ**, còn `Class<?>`/loader/code/cache |
| `Class<?>` strong reference còn sống | Class chưa thể biến mất một cách gây sai semantics; thường giữ defining loader |
| Defining loader còn strong reachable | Các class do nó định nghĩa **không đủ điều kiện** unload theo JLS |
| Defining loader đã có thể reclaim | Classes do loader định nghĩa **có thể** đủ điều kiện; VM quyết định |
| `System.gc()` được gọi | Chỉ yêu cầu GC, **không bảo đảm** đã chạy, đã unload hoặc đã trả RAM cho OS |

Đừng dùng một biến `null` làm bằng chứng class unloading; hãy xác định **strong GC roots** tới loader và classes.

## 5. Static fields có luôn làm class loader bị leak không?

**Không.** Một static field thuộc class do plugin loader định nghĩa có thể giữ các object trong cùng vòng đời; nếu toàn bộ nhóm này không còn reachable từ GC roots thì reference cycle nội bộ không tự tạo leak. Trường hợp nguy hiểm là **host hoặc thư viện sống lâu** giữ class, instance, `Method` hoặc callback của plugin trong static registry/cache.

Ví dụ đường tham chiếu gây retention: `host static map → plugin instance → runtime class → defining loader`. Bỏ biến local `pluginLoader` không cắt đường này. Bài về static field và reflection cache phân tích thêm `WeakHashMap`, `ClassValue` và cache theo generation.

## 6. ClassLoader.close không phải class unloading

`URLClassLoader.close()` chủ yếu ngăn đọc những class/resource mới từ URL nguồn do loader quản lý và giải phóng file/JAR handles tương ứng. **Các class đã nạp và object hiện có vẫn có thể hoạt động**; việc JVM có unload class tùy thuộc khả năng reclaim defining loader và quyết định GC.

Cần phân biệt `close` ở mức I/O/resource với eligibility ở mức runtime class. Bài phân biệt mất tham chiếu, `close()` và unloading kiểm chứng ba hành động này bằng thí nghiệm chạy được.

## 7. Metaspace liên quan gì tới class unloading?

Trên **HotSpot JDK 17+**, class metadata phần lớn được quản lý trong Metaspace, khác Java heap chứa các object instance và `Class<?>` mirrors. Nếu mỗi lần reload tạo thêm defining loader mà những loader cũ vẫn reachable, class metadata có thể tích lũy và dẫn tới áp lực Metaspace.

Unloading, khi JVM thực hiện, có thể giải phóng/reuse metadata của generation cũ; không có bảo đảm mức `committed` hoặc RSS giảm ngay. **Method area** của JVMS là khái niệm logic, còn **Metaspace** là chi tiết triển khai HotSpot. Bài Metaspace riêng giải thích `used/committed/reserved`, NMT và điều tra OOME.

## 8. Java 17 demo: tạo custom defining loader, chỉ giữ WeakReference khi rời phạm vi

Demo sau nạp một class **chỉ tồn tại ở URL path của plugin**, lấy `WeakReference` tới loader và `Class<?>`, sau đó thoát khỏi method để bỏ các strong local references. Việc VM có collect/unload trong lần chạy cụ thể là **không xác định**.

### 8.1. Cấu trúc thư mục

```text
src/
  plugin/
    demo/
      Entry.java
  host/
    UnloadEligibilityDemo.java
out/
  plugin/
  host/
```

File `src/plugin/demo/Entry.java`:

```java
package demo;

public final class Entry {
    public static String value() {
        return "plugin-alive";
    }
}
```

File `src/host/UnloadEligibilityDemo.java`:

```java
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class UnloadEligibilityDemo {
    private record Watch(
            WeakReference<ClassLoader> loader,
            WeakReference<Class<?>> type) {
    }

    private static Watch loadOnce() throws Exception {
        URL directory = Path.of("out/plugin").toUri().toURL();
        ClassLoader parent =
                UnloadEligibilityDemo.class.getClassLoader();

        URLClassLoader pluginLoader = new URLClassLoader(
                "plugin-v1", new URL[]{directory}, parent);

        try (pluginLoader) {
            Class<?> type = Class.forName(
                    "demo.Entry", true, pluginLoader);

            WeakReference<ClassLoader> loaderRef =
                    new WeakReference<>(pluginLoader);
            WeakReference<Class<?>> classRef =
                    new WeakReference<>(type);

            System.out.println("Defined by plugin loader: "
                    + (type.getClassLoader() == pluginLoader));

            System.out.println("Class lookup reused: "
                    + (pluginLoader.loadClass("demo.Entry") == type));

            System.out.println("Method: "
                    + type.getMethod("value").invoke(null));

            System.out.println("Weak refs populated before return: "
                    + (loaderRef.get() != null
                       && classRef.get() != null));

            // Đảm bảo strong references còn live tới điểm này.
            Reference.reachabilityFence(type);
            Reference.reachabilityFence(pluginLoader);

            return new Watch(loaderRef, classRef);
        }
    }

    public static void main(String[] args) throws Exception {
        Watch watch = loadOnce();
        System.out.println("Returned from loader scope");

        // Đây CHỈ là một yêu cầu GC, không phải lệnh unload.
        System.gc();

        System.out.println("Loader weak ref cleared: "
                + (watch.loader().get() == null));
        System.out.println("Class weak ref cleared: "
                + (watch.type().get() == null));
    }
}
```

### 8.2. Biên dịch và chạy

Trên Linux/macOS, đứng tại thư mục chứa `src`:

```bash
mkdir -p out/plugin out/host
javac -d out/plugin src/plugin/demo/Entry.java
javac -d out/host src/host/UnloadEligibilityDemo.java
java -cp out/host UnloadEligibilityDemo
```

Trên Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force out/plugin,out/host
javac -d out/plugin src/plugin/demo/Entry.java
javac -d out/host src/host/UnloadEligibilityDemo.java
java -cp out/host UnloadEligibilityDemo
```

**Rất quan trọng:** chỉ `out/host` nằm trong application classpath khi chạy. Không thêm `out/plugin` vào `-cp`, vì parent loader có thể tìm `demo.Entry` trước custom `URLClassLoader`, làm ví dụ không còn chứng minh class do pluginLoader định nghĩa.

### 8.3. Kết quả nào xác định, kết quả nào không?

Bốn dòng đầu của stdout:

```text
Defined by plugin loader: true
Class lookup reused: true
Method: plugin-alive
Weak refs populated before return: true
Returned from loader scope
```

**Hai dòng cuối phụ thuộc GC/JVM/timing** và có dạng:

```text
Loader weak ref cleared: false
Class weak ref cleared: false
```

hoặc có thể in `true` cho một/both trường hợp nếu collector đã reclaim referent tương ứng trước lúc đọc. **Không có stdout duy nhất được Java specification đảm bảo cho hai boolean cuối**, kể cả có `System.gc()`. Đây là chủ đích của ví dụ: đo hiện tượng chứ không giả định GC đã thực hiện một lời hứa unload.

Phân tích:

1. `Class.forName("demo.Entry", true, pluginLoader)` khiến `pluginLoader` tìm và định nghĩa class `demo.Entry` từ `out/plugin` vì parent không thấy tên ấy.
2. `type.getClassLoader() == pluginLoader` true chứng minh **defining loader**.
3. Gọi `loadClass` lần sau trả cùng runtime `Class<?>`; không định nghĩa lặp.
4. `type.getMethod("value").invoke(null)` trả chuỗi đã khai báo trong plugin code.
5. Weak references được tạo trong lúc mạnh references còn sống; chúng không tự giữ class/loader.
6. Sau khi `loadOnce()` trả về, strong local references trong method đó hết phạm vi cần thiết; chỉ còn các weak references mà `Watch` đang giữ **trong thiết kế ví dụ**.
7. `try-with-resources` đã đóng URLClassLoader, nhưng nó không kích hoạt class unloading như một lệnh đồng bộ.
8. `System.gc()` gửi yêu cầu GC. Nếu loader weak reference chưa cleared, đó **chưa** là bằng chứng leak — VM có thể chưa reclaim tại thời điểm kiểm tra.

Với chương trình quá ngắn, VM có thể kết thúc trước khi việc class unloading quan sát được diễn ra. Không nên sửa code thành một vòng lặp vô hạn gọi `System.gc()` để “bắt buộc” xuất hiện `true`.

### Một điều cần phân biệt khi quan sát WeakReference

Nếu `watch.loader().get() == null`, weak referent loader đã được collector xử lý là không còn strongly reachable theo semantics của `WeakReference`. Điều đó là **quan sát về loader referent**, không tự chứng minh một sự kiện class-unload log cụ thể đã xuất hiện cùng thời điểm, và cũng không nói OS đã trả lại bao nhiêu RAM.

Nếu class token weak ref chưa cleared ở một lần quan sát, không được khẳng định VM chắc chắn có leak. Trạng thái GC và lifetime liveness không phải deterministically controllable từ Java user code.

## 9. Ví dụ Java 17: TCCL của thread có thể giữ loader sống

Một worker thread lâu sống có thể chứa `contextClassLoader` vẫn trỏ tới plugin loader sau khi plugin dừng. Đường `GC root → live Thread → TCCL → plugin loader` có thể giữ generation đó sống.

Trong tác vụ đặt TCCL tạm thời, khôi phục loader trước trong `finally`; dọn `ThreadLocal` trên **chính worker thread** đã lưu value và ngừng các task chưa kết thúc. Bài Thread/ThreadLocal/TCCL triển khai thí nghiệm độc lập trên executor để kiểm chứng retention này.

## 10. Class unloading không đồng nghĩa hot reload class trong cùng loader

Ví dụ plugin A đã load `plugin.Entry` version 1 bởi loader L1. Sau khi triển khai code mới ở cùng đường dẫn file, ứng dụng gọi:

```java
Class<?> next = L1.loadClass("plugin.Entry");
```

Trong loader được thiết kế đúng, lời gọi này thường trả **class đã ghi nhận** bởi L1, không nạp lại bytecode version 2. Một defining loader không được định nghĩa lặp class cùng binary name thành hai runtime classes độc lập trong chính loader đó; nỗ lực `defineClass` trùng có thể gây `LinkageError`.

Một chu kỳ reload đúng thường:

```text
Version 1:
  L1 -> plugin.Entry v1
  Host dùng plugin v1
  Host dừng plugin, bỏ references và close tài nguyên L1
  L1 có thể trở nên unreachable

Version 2:
  L2 (loader mới) -> plugin.Entry v2
  Host dùng plugin v2 qua shared API
  L2 có runtime class identity riêng

Class unloading của L1 (nếu có) không cần diễn ra
trước khi L2 load class cùng binary name.
```

**Không cần đợi L1 đã được GC mới có thể tạo L2.** Hai runtime classes cùng binary name có thể tồn tại song song nếu defining loaders khác nhau và các loading constraints/app contracts vẫn được tuân thủ.

Ngược lại, nếu host giữ `Class<?>` hoặc instance từ L1 sau khi chuyển sang L2, class metadata của cả hai thế hệ có thể tồn tại lâu dài. Đây là nguồn Metaspace growth trong hot deployment.

## 11. Những nguồn leak rất dễ bỏ sót

Để kiểm tra khả năng reclaim defining loader, hãy truy vết từ **GC roots** tới loader thay vì chỉ tìm một biến `loader`. Các đường tham chiếu thường gặp gồm host static `Map<String, Class<?>>` hoặc `Map<String, Method>`, event listener/callback, `ServiceLoader` cache, thread/TCCL/`ThreadLocal`, pending `CompletableFuture`/executor task, và JDBC/JMX/logging registries.

**Không có một lệnh cleanup chung cho mọi framework.** Gỡ chính xác registration handles, cache entries và thread state theo owner của mỗi resource. Các bài về static cache, threads và ServiceLoader giải thích riêng từng nhóm, cách đo và tình huống thất bại.

## 12. Parent–child loader: hướng tham chiếu quyết định khả năng thu gom

Một custom loader thường giữ parent:

```text
Child PluginLoader ---> Parent SharedApiLoader
```

Điều này **không bắt buộc** parent giữ child. Nếu chỉ có mũi tên từ child sang parent, parent còn sống **không tự** làm child còn sống.

Nhưng một registry/layer/framework có thể tạo đường ngược:

```text
Host root
   |
   v
PluginManager
   |
   v
Child PluginLoader
   |
   v
SharedApiLoader (parent)
```

Đường `Host → PluginManager → Child` là lý do child không GC, không phải riêng quan hệ `Child → Parent`.

Với JPMS `ModuleLayer`, các layer/loader objects có graph reference phụ thuộc cách tạo và giữ. Nếu host còn giữ layer object, modules, service providers hoặc objects của plugin, không nên kỳ vọng chỉ `pluginLoader = null` là đủ. Cần phân tích **thực tế references do implementation giữ**, không khẳng định mọi layer đều có cùng cấu trúc retention.

## 13. Những classes nào không nên kỳ vọng unload trong ứng dụng bình thường?

Theo JLS `12.7`, **class/interface do bootstrap loader định nghĩa không unload**. Các class như `java.lang.String` trong JDK tiêu chuẩn thuộc nhóm core runtime này.

Với platform/application loader mặc định của một JVM đang chạy, loader thường là một phần sống dài của runtime/application. Vì thế những classes do chúng định nghĩa **thường không phải mục tiêu để unload giữa chừng**, trừ khi các điều kiện reachability đặc biệt của triển khai cho phép.

Điều này giải thích vì sao chương trình:

```java
for (int i = 0; i < 1000; i++) {
    Object o = new SomeClass();
}
```

có thể GC hàng nghìn instances mà `SomeClass` vẫn được loaded. `SomeClass` trong một app loader sống cùng chương trình không phải một class được unload mỗi khi hết instances.

Những ứng dụng hưởng lợi nhiều từ class unloading:

- Application servers có nhiều lần deploy/redeploy.
- Plugin managers tạo riêng loader cho từng plugin/version.
- Runtime tạo/định nghĩa dynamic proxies hoặc generated classes theo loader có vòng đời ngắn.
- Code evaluation frameworks, scripting/runtime tích hợp khi chúng dùng dynamic defining loaders.

**Không đồng nghĩa mọi ứng dụng nạp nhiều class đều có unloading**: nếu tất cả classes được định nghĩa bởi một loader sống đến hết process, việc drop vài instances không làm loader eligible.

## 14. Cách quan sát class unloading trên HotSpot JDK 17

Trên HotSpot có thể khởi động JVM với `-Xlog:class+load=info,class+unload=info` để quan sát sự kiện. Log unload xuất hiện là bằng chứng JVM đã unload một lớp cụ thể; **thiếu log trong một lần chạy** không chứng minh loader bị leak. `System.gc()` là request, không là bằng chứng.

Khi điều tra, kết hợp event logs, class-loader counts và heap retaining paths; so sánh nhiều lần reload có cùng workload. Bài quan sát unloading phân tích quy trình thử nghiệm, cách đọc counter và các giới hạn.

## 15. Quy trình stop/reload plugin an toàn hơn

Một generation plugin chỉ có cơ hội retire đúng khi host **ngăn tác vụ mới → drain/cancel in-flight work → unregister listeners/registries → dọn thread, TCCL, `ThreadLocal`, caches → đóng resource và URL loader → bỏ strong references**.

Không dùng việc `close()` thành công hay số lần `System.gc()` đã gọi làm điều kiện xác nhận class unloading. Tùy kiến trúc, cần phối hợp thứ tự shutdown để tránh task khác đồng thời nạp class từ loader đang đóng.

## 16. Những hiểu lầm phổ biến

| Nhận định sai | Điều đúng |
|---|---|
| “Không còn instance thì class chắc chắn unload.” | Class unloading phụ thuộc **defining loader** có thể reclaim, không chỉ instance. |
| “Đặt `loader = null` là xong.” | Chỉ bỏ một reference; có thể còn registry, TCCL, Class<?>, listener, v.v. |
| “`URLClassLoader.close()` sẽ unload mọi class ngay.” | `close` quản lý resource/lookup; class unloading do GC/VM quyết định. |
| “Gọi `System.gc()` sẽ trả `true` ở WeakReference test.” | `System.gc()` là yêu cầu không có bảo đảm thời điểm/thực thi. |
| “Static field của plugin nào cũng gây leak.” | Cần strong path từ một root sống lâu; cycles nội bộ không tự pin graph. |
| “Class của bootstrap loader có thể unload nếu không có instances.” | JLS `12.7` loại trừ class/interface do bootstrap loader nạp. |
| “Tăng Metaspace là bằng chứng đủ của leak.” | Cần xét used/committed/reserved, workload và GC/loader stats. |
| “Class unload thì OS RSS giảm ngay từng byte tương ứng.” | VM allocator có thể giữ/reuse memory; RSS không phải thước đo class unload trực tiếp. |
| “Parent loader sống thì mọi child loaders sống.” | Child thường giữ parent; parent không mặc nhiên giữ child. |
| “TCCL không liên quan class unloading.” | Worker thread giữ loader qua TCCL là strong reference rất phổ biến. |
| “Cache Method không giữ class/plugin.” | Reflection metadata có thể giữ declaring class và loader graph reachable. |
| “Đặt tên loader giống nhau cho phép tái sử dụng class identity.” | Loader name không quyết định runtime identity. |
| “Unloading giống hot replacement trong cùng loader.” | Thường cần loader mới; class cũ vẫn có thể tồn tại cho đến khi GC. |
| “Không thấy unload log sau một lần chạy là leak.” | Unloading là tối ưu không có deadline, demo ngắn có thể kết thúc trước GC. |

## 17. Bài tập tự kiểm tra

**Câu 1.** Một `PluginTask` instance đã được GC, nhưng host vẫn giữ `Class<?>` của `PluginTask` trong static map. Class có đủ điều kiện unload chưa?

**Đáp án:** Chưa. `Class<?>` còn được giữ bởi host có thể tiếp tục giữ class/defining loader reachable. Mất instance riêng không đủ.

**Câu 2.** Defining loader của class có thể reclaim. JVM bắt buộc phải unload class ngay trong lần GC tiếp theo không?

**Đáp án:** Không. JLS `12.7` cho phép class unloading như tối ưu; **eligible** không bảo đảm thời điểm GC/unloading.

**Câu 3.** `URLClassLoader.close()` và `System.gc()` có phải hai cách tương đương để unload class không?

**Đáp án:** Không. `close()` đóng resources và ảnh hưởng lookup; `System.gc()` yêu cầu GC nhưng không bảo đảm thực thi; class unloading là cơ chế VM riêng dựa trên eligibility.

**Câu 4.** Nếu child loader giữ parent loader, parent còn sống có tự giữ child sống không?

**Đáp án:** Không mặc nhiên. Reference hướng từ child sang parent không tương đương từ parent sang child. Cần có đường strong reference từ root tới child.

**Câu 5.** Static field trong plugin giữ một object khác cũng thuộc plugin. Có tự ngăn GC mọi class của plugin không?

**Đáp án:** Không chỉ bởi quan hệ nội bộ ấy. Nếu toàn bộ graph không còn strong path từ roots bên ngoài, cycles nội bộ có thể được collector xử lý.

**Câu 6.** Một worker thread còn giữ `Thread.currentThread().getContextClassLoader() == oldPluginLoader` sau redeploy. Điều gì có thể xảy ra?

**Đáp án:** Thread còn sống giữ old loader reachable, khiến các classes của old loader chưa đủ điều kiện unload; nên restore TCCL đúng worker thread trong `finally`.

**Câu 7.** Trong demo `UnloadEligibilityDemo`, vì sao hai giá trị `Loader weak ref cleared` và `Class weak ref cleared` không có stdout cố định?

**Đáp án:** Chúng phụ thuộc GC/timing và trạng thái reachability tại thời điểm kiểm tra; `System.gc()` không cam kết collect/unload đồng bộ.

**Câu 8.** Plugin v1 và v2 có thể cùng tồn tại với cùng binary name `plugin.Entry` không?

**Đáp án:** Có, nếu hai defining loaders khác nhau và các shared API/loading constraints phù hợp. Không cần chờ class v1 unload mới được tạo loader v2.

**Câu 9.** `Class<?>` token, `Method` và `MethodHandle` của plugin nằm trong host cache có liên quan class unloading không?

**Đáp án:** Có. Chúng có thể giữ runtime classes/loader graph reachable, nên phải xem lifecycle của reflection/call-site caches.

**Câu 10.** Metaspace used giảm sau class unloading nhưng OS RSS giữ nguyên. Có mâu thuẫn không?

**Đáp án:** Không. VM allocator có thể giữ/commit memory để tái sử dụng; các metrics không đồng nhất.

**Câu 11.** Một chương trình không bật log unload nhưng thấy heap usage tăng theo từng lần reload. Cần kết luận gì?

**Đáp án:** Chưa kết luận được classloader leak. Phải xem GC roots, loader count, class metadata, heap cache, profiler và cấu hình logging/GC; heap tăng có nhiều nguyên nhân.

**Câu 12.** Vì sao JLS không cho unload class khi defining loader còn potentially reachable?

**Đáp án:** Loader còn sống có thể được yêu cầu resolve/nạp class về sau. Nếu một class bị unload rồi reload dưới cùng loader, sự khác nhau ở static state, initialization side effects, class identity hoặc native state có thể làm chương trình quan sát được việc unloading, trái yêu cầu transparency.

## 18. Tóm tắt và tài liệu chính thức

**Nắm chắc năm điều:**

1. **Class unloading chỉ có thể khi defining loader có thể reclaim**; đây là điều kiện về eligibility, không phải lời hứa JVM sẽ unload lập tức.
2. **Reachability phụ thuộc toàn bộ GC-root graph**: instances, `Class<?>`, reflection metadata, TCCL, threads, registries và caches đều đáng kiểm tra.
3. **Close resource ≠ unload class**: `URLClassLoader.close()` không giải phóng đồng bộ các runtime classes đã định nghĩa.
4. **Hot reload thường dùng loader mới** cho cùng binary name; loader cũ chỉ có cơ hội GC sau khi dọn mọi references còn sống.
5. **Quan sát nhiều tín hiệu, không đoán**: weak refs, class unload logs, loader stats và Metaspace numbers có vai trò khác nhau; không tín hiệu đơn lẻ nào thay thế phân tích GC roots.

**Tài liệu chuẩn:**

- [JLS 17 — `12.7` Unloading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7): điều kiện defining loader và giới hạn class unloading.
- [JVMS 17 — `5.3` Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): defining loader, initiating loader và class identity.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass`, `findLoadedClass`, `defineClass`.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): `close()`, class/resource lookup và tài nguyên URL/JAR.
- [Java SE 17 — WeakReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html): weak-reference reachability/clearing.
- [Java SE 17 — Reference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html): `reachabilityFence` và reference operations.
- [Java SE 17 — java launcher options](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xlog:class+unload` và `-Xnoclassgc` trên HotSpot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-unloading-and-memory-002">Vì sao không có thao tác unload tùy ý một lớp thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không có thao tác unload tùy ý một lớp thông thường trong Java?

Trong Java, ta có thể yêu cầu nạp một class bằng `ClassLoader.loadClass(...)`, `Class.forName(...)` hoặc cơ chế runtime khác. Nhưng Java **không cung cấp một API đối xứng kiểu `Class.unload()`, `ClassLoader.unloadClass("p.Name")` hoặc `System.unloadClass(...)` để ứng dụng tự xóa ngay một lớp thông thường đã được JVM định nghĩa**.

Đây không phải thiếu sót ngẫu nhiên. Một runtime class liên quan đến **định danh kiểu, các instance còn sống, method đang chạy, stack frames, field/method references đã liên kết, static state, reflection và class loader**. Xóa riêng một class bất kỳ theo ý ứng dụng có thể làm mất tính nhất quán của chương trình đang hoạt động.

Thay vào đó, **class unloading là cơ chế tùy JVM, có liên hệ với khả năng garbage collector (GC) thu hồi defining class loader**. Với class/interface thông thường, nguyên tắc của **Java Language Specification (JLS) `12.7`** là: class/interface **chỉ có thể được unload khi defining loader của nó có thể được GC thu hồi**. Nhưng đủ điều kiện thu hồi không phải lời hứa rằng JVM sẽ unload vào một thời điểm xác định. Class unloading là một tối ưu hóa quản lý bộ nhớ, và chương trình hợp lệ **không được phụ thuộc vào việc một class có được unload hay không**.

> Ghi nhớ trước: **ngừng sử dụng plugin → tháo references → loader có thể trở nên collectible → JVM có thể unload classes**. Không có mũi tên nào trong chuỗi này có nghĩa “ngay lập tức và chắc chắn”.

## 1. Phân biệt load, link, initialize, stop, close và unload

Các từ sau thường bị dùng lẫn:

| Khái niệm | Ý nghĩa thực tế | Có xóa runtime class không? |
|---|---|---|
| **Loading** | JVM tìm/tạo `Class<?>` theo binary name và loader | Không |
| **Linking** | Verification, preparation và có thể resolution theo JVM | Không |
| **Initialization** | Chạy logic `<clinit>`: static initializers/field initializers khi cần | Không |
| **Stop plugin** | Quy trình nghiệp vụ ngừng nhận request, đóng services, unregister handlers | Không tự động |
| **`URLClassLoader.close()`** | Giải phóng resource/JAR/file handles do loader này quản lý trong phạm vi API | **Không** unload classes đã tải |
| **`System.gc()`** | Gợi ý JVM thực hiện GC; không bảo đảm xử lý ngay, không chỉ định class | **Không** phải unload command |
| **Class unloading** | JVM giải phóng định nghĩa class/metadata theo điều kiện và chiến lược của nó | Có, khi JVM quyết định |
| **`Instrumentation.redefineClasses`** | Thay một số nội dung định nghĩa theo giới hạn instrumentation | **Không** phải xóa class |
| **`ClassLoader#defineClass` ở loader mới | Tạo runtime class thuộc một namespace loader khác | Không xóa class cũ |

**Điểm quan trọng:** `ClassLoader.close()` là thao tác lifecycle **nguồn tải**, còn **class unloading** là thao tác quản lý **định nghĩa runtime class**. Chúng có thể nằm trong cùng kế hoạch dỡ plugin nhưng **không đồng nghĩa**.

## 2. Tại sao không thể đơn giản “xóa tên class khỏi JVM”?

Một class trong JVM không chỉ là tệp `.class` hoặc tên chuỗi. Với class/interface thông thường, runtime identity được xác định bằng:

```text
(binary name, defining class loader)
```

Ví dụ:

```text
Loader-A defines app.Plugin
  -> runtime type (app.Plugin, Loader-A)

Loader-B defines app.Plugin
  -> runtime type (app.Plugin, Loader-B)
```

Hai runtime types này **khác nhau**, dù tên nhị phân trùng và bytecode có thể giống hệt. Một object của bản A không tự biến thành object bản B.

Một class đã định nghĩa có thể liên quan đến:

1. **Instance trên heap**: JVM phải biết kiểu, layout, virtual method dispatch và metadata tương ứng.
2. **Static fields**: một bản trạng thái tĩnh cho mỗi runtime class; giá trị có thể trỏ đến các objects, services và tài nguyên.
3. **Method đang thực thi**: call stack có thể đang chứa frame của class đó.
4. **References trong bytecode khác**: liên kết fields/methods/type descriptors, resolution và các loader constraints.
5. **Reflection**: các object `Class<?>`, `Method`, `Field`, `Constructor`, `MethodHandle` có thể gắn với kiểu.
6. **Kế thừa và interfaces**: subclasses/implementations có thể đã được nạp và dùng class đó.
7. **Runtime services**: proxy, serialization, JNI, exception handling hoặc framework caches có thể nắm metadata liên quan.

Nếu có API `unloadClass("app.Plugin")` trả về thành công ngay, JVM phải xử lý chuyện gì xảy ra với các object còn sống, frame đang chạy và code khác gọi method của class này sau đó. Java tránh cung cấp một hành động thiếu định nghĩa rõ về mặt semantics như thế.

### Ví dụ suy luận

```java
Object plugin = createPlugin();
Class<?> type = plugin.getClass();
Method method = type.getMethod("run");
```

Đây là **snippet minh họa**, chưa runnable độc lập vì `createPlugin()` và method `run` cần được định nghĩa.

Nếu ngay sau đó ta “xóa class theo tên”, `plugin` vẫn là một Java object, `type` vẫn đại diện runtime type, và `method` còn dùng để gọi code. Một JVM không thể giả định những references ấy tự biến mất chỉ vì ứng dụng muốn thay thế JAR.

**Không phải JVM hoàn toàn không thể quản lý lại metadata**, mà JVM phải đảm bảo class unloading diễn ra khi **các điều kiện thu hồi an toàn** được thỏa, thay vì ở một thời điểm tùy ý do một dòng lệnh ứng dụng quyết định.

## 3. Điều kiện class unloading theo JLS `12.7`

**JLS `12.7` — Unloading of Classes and Interfaces** quy định:

- Một implementation **có thể** unload classes/interfaces.
- Một class/interface **chỉ có thể** bị unload nếu **defining class loader của nó có thể được garbage collector thu hồi**.
- Classes/interfaces được **bootstrap loader** nạp **không thể bị unload** theo quy tắc JLS.
- Việc unload là **optimization**, và chương trình không được phụ thuộc vào quyết định unload.

Sơ đồ tư duy:

```text
Class p.A được loader L định nghĩa
           |
           v
Còn reference khiến L chưa collectible?
           |
    +------+------+
    |             |
   Có           Không
    |             |
    v             v
Không đủ      L có thể eligible
điều kiện     để GC thu hồi
để unload          |
                  v
         JVM có thể unload classes
         do L định nghĩa, khi phù hợp
```

Đây là **quan hệ điều kiện cần**, không phải thuật toán GC chính xác. Đừng hiểu “không còn biến địa phương tên `loader`” tự động chứng minh L đã không còn reachable: có thể còn references khác qua objects/classes/threads/registries.

### “Defining loader” khác “loader đã gọi loadClass”

```java
Class<?> type = child.loadClass("java.lang.String");
```

Một child loader có thể chỉ **ủy quyền** lên parent/bootstrap. Với `String`, defining loader trên JDK thông thường là bootstrap; `type.getClassLoader()` trả `null`. Hủy child loader **không làm `String` trở thành eligible for unloading**.

Nếu muốn quản lý vòng đời plugin, phải xác định **loader thực sự định nghĩa các private classes của plugin**, không chỉ loader mà plugin manager dùng để gọi `loadClass`.

### Unload là quyền của VM, không phải yêu cầu bắt buộc

Ngay cả khi một class loader đã trở nên unreachable và đủ điều kiện thu hồi, ứng dụng vẫn **không thể chỉ định thời điểm** GC chạy hoặc class metadata được giải phóng. Những hành vi có vẻ thành công trên một JDK/GC configuration có thể khác trên JDK/GC hoặc workload khác. Vì vậy logic nghiệp vụ như cập nhật plugin, xóa file, làm sạch registry phải dựa vào **lifecycle do ứng dụng điều khiển**, không dựa vào “GC đã unload lớp X rồi”.

## 4. Tại sao đóng URLClassLoader vẫn dùng được class đã nạp?

`URLClassLoader.close()` quản lý resource và khả năng tìm class/resource mới từ URL của loader. Class đã được định nghĩa **không bị xóa**, instance đã tạo vẫn mang runtime type cũ, và method đã liên kết có thể tiếp tục chạy khi còn tham chiếu hợp lệ.

Đó là lý do `close()` không tương đương với `unloadClass`. Ví dụ chạy được ở phần 5 kiểm tra điều này, đồng thời nạp phiên bản mới bằng một defining loader khác.

## 5. Demo Java 17+: close không unload, loader mới tạo kiểu mới

Demo có hai class nguồn, trong hai output directories tách biệt:

```text
class-unload-demo/
├── src/
│   ├── plugin/
│   │   └── Greeting.java
│   └── app/
│       └── UnloadDemo.java
└── out/
    ├── plugin/
    └── app/
```

**Mấu chốt:** Runtime classpath **chỉ chứa `out/app`**. Plugin class `plugin.Greeting` được hai đối tượng `URLClassLoader` khác nhau định nghĩa từ `out/plugin`.

### 5.1. Tệp src/plugin/Greeting.java

```java
package plugin;

public final class Greeting {
    private static int visits;

    static {
        System.out.println("Plugin initialized");
    }

    public static String greet() {
        return "hello " + (++visits);
    }
}
```

`visits` là **static field** thuộc runtime class. Mỗi defining loader sẽ có một bản `plugin.Greeting` độc lập và một giá trị `visits` độc lập.

### 5.2. Tệp src/app/UnloadDemo.java

```java
package app;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class UnloadDemo {
    public static void main(String[] args) throws Exception {
        URL url = Path.of(args[0]).toUri().toURL();
        ClassLoader parent = UnloadDemo.class.getClassLoader();

        try (URLClassLoader first =
                new URLClassLoader(new URL[] {url}, parent)) {

            Class<?> original =
                    first.loadClass("plugin.Greeting");

            System.out.println("Same class in one loader: "
                    + (original ==
                       first.loadClass("plugin.Greeting")));

            System.out.println("First call: "
                    + original.getMethod("greet").invoke(null));

            // Giải phóng nguồn loader; class hiện có vẫn sử dụng được.
            first.close();

            System.out.println("After close: "
                    + original.getMethod("greet").invoke(null));

            try (URLClassLoader second = new URLClassLoader(
                    new URL[] {url}, parent)) {

                Class<?> replacement =
                        second.loadClass("plugin.Greeting");

                System.out.println("Different classes: "
                        + (original != replacement));

                System.out.println("First loader identity: "
                        + (original.getClassLoader() == first));

                System.out.println("Second call: "
                        + replacement.getMethod("greet").invoke(null));
            }
        }
    }
}
```

### 5.3. Biên dịch/chạy

Trên Bash (Linux/macOS/Git Bash), từ thư mục `class-unload-demo`:

```bash
mkdir -p out/plugin out/app

javac --release 17 -d out/plugin src/plugin/Greeting.java
javac --release 17 -d out/app src/app/UnloadDemo.java

java -cp out/app app.UnloadDemo out/plugin
```

Trên Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path out/plugin, out/app | Out-Null
javac --release 17 -d out/plugin src/plugin/Greeting.java
javac --release 17 -d out/app src/app/UnloadDemo.java
java -cp out/app app.UnloadDemo out/plugin
```

**Đầu ra đã biên dịch/chạy và kiểm chứng** với `javac --release 17` trên OpenJDK 24:

```text
Same class in one loader: true
Plugin initialized
First call: hello 1
After close: hello 2
Different classes: true
First loader identity: true
Plugin initialized
Second call: hello 1
```

### 5.4. Giải thích từng dòng

1. **Same class in one loader: true** — `first.loadClass` gọi lại cùng binary name trả cùng `Class<?>` đã được loader đó nạp; không tự định nghĩa hai bản cùng tên trong một loader.
2. **Plugin initialized** — gọi static method `greet` yêu cầu initialization của runtime class do loader `first` định nghĩa.
3. **First call: hello 1** — static field `visits` của class ở loader `first` tăng từ 0 lên 1.
4. **After close: hello 2** — đã gọi `first.close()` nhưng `original` vẫn là một `Class<?>` có thể sử dụng, và method đã nạp vẫn chạy; static field tiếp tục từ 1.
5. **Different classes: true** — loader `second` định nghĩa một runtime class `plugin.Greeting` **khác** với bản `original` dù bytecode và binary name giống nhau.
6. **First loader identity: true** — `original.getClassLoader()` vẫn là `first`, không bị đổi sang `second` bởi `close`.
7. **Plugin initialized** lần nữa — class ở `second` là runtime class mới, có initialization riêng.
8. **Second call: hello 1** — static field của bản `second` bắt đầu từ 0, độc lập với bản cũ.

**Điều ví dụ không chứng minh:** `first` đã được garbage-collected hoặc `plugin.Greeting` V1 đã unload. Thực tế trong code, biến `original` vẫn giữ `Class<?>` V1, nên việc đòi JVM gỡ V1 ngay sau `close` là không hợp lý. Sau khi `main` kết thúc, tiến trình demo thoát; đó là kết thúc JVM process, **không phải một test thực nghiệm class unloading trong cùng JVM**.

### 5.5. Vì sao giữ `original` là quan trọng?

Thử nghiệm giữ một `Class<?>` object đang còn reachable, đồng thời cho thấy class/loader đã nạp không bị “xóa” bởi `close`. Nếu gọi `System.gc()` trong lúc còn giữ reference, cũng không biến object này thành không hợp lệ.

Không nên cố ép test bằng `original = null` rồi assert class chắc chắn unload; `null` cho một local variable **không chứng minh** mọi đường tham chiếu trong chương trình/JVM đã biến mất, và GC không bị buộc chạy đúng lúc bạn muốn.

## 6. Class unloading không giống garbage collection của object

**Instance GC** thu hồi object trên Java heap khi nó không còn reachable theo quy tắc của collector. **Class unloading** liên quan runtime class/metadata và defining loader; **đóng JAR handles** là vòng đời tài nguyên I/O.

Một instance có thể được thu hồi trong khi class vẫn dùng được; một loader có thể đóng JAR nhưng chưa đủ điều kiện reclaim. Không suy ra việc unload chỉ từ `instance = null`, `close()` hay một lần GC.

## 7. Những thứ hay giữ class loader còn sống

Host static cache, listener/event bus, reflection `Class<?>`/`Method` cache, thread pools, `ThreadLocal`, context class loader, scheduled jobs và provider cache là các nhóm tham chiếu điển hình.

Ví dụ: `host registry → plugin callback → plugin Class<?> → defining loader`. Cần gỡ reference tại đúng **owner** thay vì chỉ gọi `loader = null`. Các bài chuyên về cache, threads và ServiceLoader đào sâu từng cơ chế giữ sống và cách xử lý.

## 8. Vòng đời plugin đúng cách

Để ngừng sử dụng plugin có kiểm soát, ngăn request mới trước; sau đó hoàn tất/hủy công việc đang chạy, unregister callbacks, dọn state trên worker threads, close các resource được plugin sở hữu và cuối cùng đóng URL loader nếu có.

Hot reload tạo generation/defining loader mới cho cùng binary name khi cần code mới. JVM tự quyết định thời điểm GC/class unloading sau khi generation cũ không còn reachable. Đây là lifecycle ứng dụng, **không phải API buộc xóa một class đã định nghĩa**.

## 9. Reload class khác với unload class

Ba thao tác cần phân biệt:

### A. Tạo loader mới cho một phiên bản plugin mới

```text
Plugin V1: (com.acme.Widget, LoaderV1)
Plugin V2: (com.acme.Widget, LoaderV2)
```

Có thể coexist trong JVM. Host chọn thế hệ cho công việc mới và cho thế hệ cũ hoàn tất; sau đó xóa references V1. Đây là hướng thường dùng cho hot-reload dựa trên classloader isolation.

### B. Instrumentation redefine/retransform

`java.lang.instrument.Instrumentation` có APIs:

```java
redefineClasses(ClassDefinition... definitions)
retransformClasses(Class<?>... classes)
```

Đây là **tên phương thức API minh họa**, không phải đoạn chương trình chạy độc lập. Instrumentation có giới hạn nghiêm ngặt về redefinition/retransformation tùy JDK/VM và khả năng hỗ trợ của agent.

Các APIs này **không đồng nghĩa dỡ runtime class**. Class identity thường vẫn gắn với runtime class hiện có; các instances và static state không mặc nhiên bị xóa/reset chỉ vì bạn thay bytecode trong những giới hạn được phép. Chúng phù hợp với agents/profilers/hot code replace kiểu nhất định, **không** biến ứng dụng thành nơi có `Class.unload()`.

### C. Restart process/JVM

Kết thúc JVM process giải phóng toàn bộ địa chỉ và runtime state qua hệ điều hành. Đây là một giải pháp **deployment/lifecycle** khác với “unload đúng một class khi JVM vẫn hoạt động”.

Một ứng dụng yêu cầu bảo đảm toàn bộ code không đáng tin được gỡ và tài nguyên hệ thống được cô lập có thể dùng **plugin process riêng** rồi terminate/restart process. ClassLoader đơn lẻ không phải sandbox hay cơ chế bảo đảm xóa mọi side effects của code.

## 10. Vì sao cùng một loader không thể defineClass lần hai để “cập nhật”?

Với một defining loader `L` và binary name `p.Widget`, runtime class identity là `(p.Widget, L)`. Sau khi định nghĩa thành công, JVM không cho bạn `defineClass` lại **một runtime class khác** cùng name trong L chỉ bằng cách gọi lại với bytes mới.

Cố định nghĩa trùng tên có thể dẫn tới `LinkageError` (duplicate class definition). Việc đọc bytecode từ JAR mới không tự thay class cũ: loader có thể trả lại class đã ghi nhận qua `findLoadedClass`/`loadClass`.

Ví dụ giả mã:

```text
Loader L:
  defineClass("p.Widget", bytesV1) -> Class<p.Widget,V1>

  defineClass("p.Widget", bytesV2)
      -> lỗi duplicate definition, KHÔNG thay V1
```

Cách chuyển **thế hệ class** là loader khác (hoặc dùng Instrumentation theo contract nếu thực sự cần thay body/behavior trong giới hạn của nó). Không cố giải quyết bằng cách bỏ cache/`findLoadedClass`: JVM vẫn quản lý identity và liên kết types.

### Tại sao namespace plugin nên đặt dưới loader riêng?

Đặt mọi plugin vào một loader chung giúp chia sẻ classes dễ hơn, nhưng việc cập nhật riêng một implementation thường buộc quản lý một namespace mới cho **cả nhóm**. Với per-plugin loader, có thể thay thế định nghĩa private classes của một plugin mà không đổi class identity của các plugin còn lại, miễn API chung nằm ở parent và lifecycle references được thu dọn.

Đổi lại, những private types từ V1/V2 không cast lẫn nhau, nên host cần giao tiếp bằng **shared SPI/DTO**. Một object thuộc `LoaderV1` không tự trở thành instance kiểu `LoaderV2` dù cùng class name.

## 11. Quan sát class unloading bằng công cụ — không lấy log làm contract

Trên HotSpot, `-Xlog:class+unload=info` có thể ghi lại sự kiện JVM unload class. Event có giá trị như **bằng chứng thực nghiệm** của lần chạy tương ứng, còn không thấy event không chứng minh leak. Không dùng `System.gc()` làm bước kiểm thử có kết quả bắt buộc.

Bài cuối chương đưa ra quy trình quan sát class count, loader generations, log và retaining paths với đúng giới hạn của từng tín hiệu.

## 12. Không lẫn class unloading với các loại “unload” khác

| Thao tác thường bị nhầm | Nó làm gì? | Không làm gì? |
|---|---|---|
| Xóa `.class` hoặc plugin JAR trên disk | Xóa bytes khỏi nguồn cho việc load mới | Không xóa runtime class đã định nghĩa |
| `URLClassLoader.close()` | Đóng resource/JAR handles theo contract | Không hủy Class/instances đã load |
| `ClassLoader.loadClass` trên loader mới | Có thể tạo runtime class identity khác | Không dỡ class cũ |
| `System.gc()` | Yêu cầu/gợi ý xem xét GC | Không chỉ định class unload |
| `WeakReference.clear()` | Xóa reference được giữ trong object WeakReference | Không xóa actual class/loader |
| Clear application cache | Bỏ một đường strong reference | Không bảo đảm tất cả đường reference khác biến mất |
| `ServiceLoader.reload()` | Xóa cache provider của **ServiceLoader object đó** | Không redefine/unload provider classes |
| `Instrumentation.redefineClasses` | Thay định nghĩa được hỗ trợ trong giới hạn VM | Không biến object hiện tại thành class khác |
| Xóa một `ModuleLayer` reference | Bỏ một reference có thể giữ graph module/loader | Không ép các classes trong layer unload |
| Kết thúc JVM process | Chấm dứt toàn bộ runtime process | Không phải API chọn một class giữa runtime còn chạy |

Một file JAR có thể đã được xóa nhưng code vẫn tiếp tục chạy vì JVM đã định nghĩa classes trong bộ nhớ. Ngược lại, đóng JAR handle có thể cần thiết để cập nhật file trên một số hệ điều hành, nhưng đó là vấn đề **file-resource lifetime**, không phải bằng chứng class unloading.

## 13. Các tình huống gây hiểu nhầm thường gặp

### “Mình đã gán loader = null; sao class còn?”

`loader = null` chỉ xóa một reference. Một `Class<?>` hoặc plugin instance còn sống, một thread đang chạy, TCCL hoặc host registry có thể vẫn giữ loader/type liên quan. Cũng có thể JVM chưa thực hiện GC hoặc class unloading dù loader đã đủ điều kiện. Hãy kiểm tra retaining paths.

### “Mình đã close JAR rồi; sao gọi method vẫn được?”

Bởi `URLClassLoader.close()` không invalid classes đã nạp; JavaDoc nêu rõ existing classes/resources vẫn truy cập được. Demo phần 5 chứng minh điều này.

### “Đã deploy V2 mà người dùng vẫn chạy V1”

Host có thể còn reference tới plugin object V1 hoặc cache factory/`Class<?>` của V1. Loader V2 tồn tại không tự thay mọi reference ứng dụng. Cần versioned service registry/atomic handoff/stop old requests theo thiết kế.

### “Metaspace tăng mỗi lần reload plugin”

Mỗi loader generation định nghĩa classes mới và có metadata/caches riêng. Nếu loader cũ còn referenced, memory có thể tiếp tục tăng. Hãy khảo sát classloader statistics, JFR, heap retention, TCCL, threads, caches và native services. **Metaspace tăng không tự chứng minh chỉ có một loại leak**; hoạt động bình thường hoặc collector policy cũng ảnh hưởng quan sát.

### “Không còn instances nên chắc class unload được”

Không đủ: Java cho phép code vẫn giữ `Class<?>`, reflection handles hoặc các runtime references khác. Mặt khác, GC roots cần phân tích theo đồ thị; không phải class nào có static fields cũng tồn tại vĩnh viễn.

### “Có class unloading log thì plugin đã dừng sạch”

Không hẳn. Class unloading nói về runtime class metadata, không xác nhận mọi external side effects (file, network, DB state, process-level property, dữ liệu nghiệp vụ) đã được rollback. Lifecycle cleanup của host vẫn phải được thiết kế độc lập.

## 14. Khi nào class unloading thực sự hữu ích?

Class unloading giúp VM giải phóng metadata của các classes không còn cần thiết khi application thực hiện dynamic loading:

- **Application servers/containers** có nhiều deploy/undeploy cycles.
- **Plugin systems** tạo loader riêng cho các phiên bản plugin.
- **Dynamic code generation** (proxy classes, scripting engines, compilers) với scoped loaders.
- **Hot reload/devtools** tạo nhiều thế hệ class loader.
- **Long-running services** nơi tránh retaining class loaders cũ giúp kiểm soát memory.

Những hệ thống này thường không gọi “unload class X”; chúng **dừng phạm vi sử dụng** (scope) rồi loại bỏ references để GC có thể thu hồi một loader generation.

### Vì sao có những loại class khác như hidden class?

Java còn có **`MethodHandles.Lookup#defineHiddenClass`** dành cho code-generated classes với semantics khác ordinary named classes. Hidden classes có những quy tắc discoverability/lifetime riêng, ví dụ options như `NESTMATE` và `STRONG`.

Đây là cơ chế nâng cao phục vụ dynamic languages/frameworks, **không làm xuất hiện API unload tùy ý cho class thông thường**. Đừng lấy hành vi hidden class làm bằng chứng rằng `ClassLoader.unloadClass("p.Normal")` tồn tại.

## 15. Quy trình quyết định khi muốn “gỡ class” khỏi một hệ thống

Nếu yêu cầu là **ngừng chạy code**:
- Dừng dispatch, unregister services và chờ/cancel active tasks theo contract.
- Không cần chờ class unload mới coi nghiệp vụ đã dừng.

Nếu yêu cầu là **nạp phiên bản mới**:
- Sử dụng loader generation mới hoặc một cơ chế instrumentation phù hợp và có giới hạn rõ.
- Giữ API chung ổn định ở parent, chuyển registry sang V2 có kiểm soát.

Nếu yêu cầu là **giải phóng tài nguyên/JAR handle**:
- Gọi `close` trên loaders/resources có hỗ trợ và cleanup đúng lifecycle.
- Không khẳng định việc đó tự xóa classes.

Nếu yêu cầu là **giảm memory/metaspace**:
- Xác định loader generations cũ còn reachable từ đâu, dọn strong references hợp lệ.
- Dùng logs/JFR/heap analysis để xác minh, không cố “force unload” bằng GC loops.

Nếu yêu cầu là **cô lập code không tin cậy**:
- Class loader không đủ; cân nhắc process isolation, OS permissions, container sandbox và IPC contracts.

## 16. Checklist trước khi retire một plugin loader

- [ ] Host đã ngừng tạo **new work** liên quan plugin cũ.
- [ ] Active requests/tasks được hoàn tất hoặc cancel an toàn.
- [ ] Plugin `stop/close` đã được gọi đúng thứ tự.
- [ ] Schedulers/executors/threads do plugin quản lý đã ngừng.
- [ ] TCCL của threads phục hồi về loader thích hợp.
- [ ] `ThreadLocal`/event listeners/global registries được cleanup.
- [ ] JDBC/MBeans/logging/caches/ServiceLoader provider references được unregister/clear khi cần.
- [ ] Open files, sockets, JAR handles/resources được đóng.
- [ ] Host không giữ `Class<?>`/`Method`/`MethodHandle`/plugin object cũ ngoài scope.
- [ ] Loader mới dùng shared SPI/DTO contracts đúng runtime type identity.
- [ ] Có metrics/logging theo generation để tìm retention bất thường.
- [ ] Không dùng “unload đã xảy ra” làm điều kiện bắt buộc cho request routing.

## 17. Câu hỏi tự kiểm tra

**1. Java có `Class.unload()` hoặc `ClassLoader.unloadClass(String)` cho ordinary class không?** Không có API công khai như vậy.

**2. JLS quy định điều kiện cần nào cho class unloading?** Defining loader của class phải có thể được GC thu hồi.

**3. Nếu loader collectible thì JVM có bắt buộc unload class ngay không?** Không. Unloading là optimization phụ thuộc implementation/GC.

**4. Tại sao `URLClassLoader.close()` không dỡ class?** Vì `close()` đóng nguồn tải và file/resource handles; classes đã nạp vẫn tồn tại và dùng được khi còn hợp lệ.

**5. Tạo loader mới với cùng binary name có cập nhật class cũ không?** Không. Nó tạo runtime type mới với defining loader khác.

**6. Static field được chia sẻ giữa hai bản `plugin.Greeting` do hai loaders khác nhau định nghĩa không?** Không; đó là hai runtime classes có static state riêng.

**7. Không còn plugin instances thì chắc chắn loader được GC chưa?** Chưa; vẫn có thể có `Class<?>`, active threads, host registries hoặc references khác.

**8. Một static field bên trong plugin có tự khiến loader leak vĩnh viễn không?** Không phải luôn luôn; quan trọng là đồ thị có còn reachable từ GC roots bên ngoài không.

**9. `System.gc()` có bảo đảm class unload không?** Không. Nó không phải unload API và không cho chương trình chọn một class để dỡ.

**10. `ServiceLoader.reload()` có unload provider classes không?** Không. Nó xóa provider cache của đối tượng ServiceLoader; lifecycle class vẫn riêng.

**11. Có cần plugin classes unload xong mới cho người dùng chuyển sang V2?** Không. Host có thể chuyển request mới sang V2 sau khi xử lý handoff đúng contract; V1 có thể còn trong bộ nhớ trong thời gian cleanup/GC.

**12. Cách debug loader retention tốt hơn đoán mò là gì?** Quan sát classloader statistics/JFR/heap retaining paths, TCCL và registries; kiểm tra references thực tế.

## 18. Nguồn chính thức

- **Java Language Specification (Java SE 17), `12.7` — Unloading of Classes and Interfaces:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7
- **Java Virtual Machine Specification (Java SE 17), `5.5/`5.3 — Initialization, defining/initiating loaders và runtime class identity:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- **Java SE 17 API, `ClassLoader`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 API, `URLClassLoader.close()`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- **Java SE 17 API, `System.gc()`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html
- **Java SE 17 API, `Instrumentation`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html
- **Java SE 17, `java` command, `-Xlog:class+unload`:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html

**Ghi nhớ:** Không tồn tại thao tác **unload tùy ý một class thông thường** vì runtime class phải nhất quán với objects, code đang thực thi, class identity và loader graph. **Ứng dụng điều khiển vòng đời sử dụng; JVM/GC điều khiển unloading.** Muốn hot-reload hoặc giảm memory, hãy quản lý loader generations, cleanup references và quan sát class unloading như một optimization — không làm nó thành điều kiện nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-unloading-and-memory-003">Phân biệt mất tham chiếu đối tượng, đóng loader và class unloading</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt mất tham chiếu đối tượng, đóng loader và class unloading

Trong một ứng dụng Java có plugin, ba hiện tượng sau dễ bị nhầm thành một:

1. **Mất tham chiếu tới một object:** code không còn giữ đối tượng qua một đường tham chiếu mạnh (*strong reference*) nào đó. Nếu object không còn được giữ sống theo quy tắc reachability, garbage collector (GC) **có thể** thu hồi object đó.
2. **Đóng một URLClassLoader:** gọi `loader.close()` để ngừng việc tìm class/resource mới từ URL search path của loader và đóng các file/JAR handles mà loader đã mở. Việc đóng **không hủy object hoặc class đã load**.
3. **Class unloading:** JVM **có thể** dỡ metadata/trạng thái liên quan class nếu **defining class loader** của class đủ điều kiện được GC thu hồi. Đây là hoạt động tùy JVM, không có lệnh Java SE đảm bảo “unload ngay”.

**Không tồn tại suy luận tự động `object = null` ⇒ `loader.close()` ⇒ `class unloaded`.** Đây là ba vấn đề thuộc các tầng quản lý bộ nhớ, tài nguyên và kiểu runtime khác nhau.

Bài thuộc nhóm **Dỡ lớp và bộ nhớ** của Java core – classloader. Mục tiêu là hiểu điều kiện thật sự để class có thể unload, thấy tận mắt `close()` không xóa class, dùng `WeakReference` và công cụ JVM đúng cách, nhận diện rò rỉ class loader trong hệ thống plugin/hot reload và quản lý lifecycle theo thứ tự an toàn.

## 1. So sánh nhanh ba hành động

| Tiêu chí | Object mất tham chiếu | `URLClassLoader.close()` | Class unloading |
| --- | --- | --- | --- |
| Đối tượng chính | Instance trên heap, loader object hoặc object khác | Một `URLClassLoader` cụ thể | Runtime definition/metadata của class hoặc interface |
| Bản chất | Reachability thay đổi | Đóng những nguồn class/resource và file handles loader quản lý | Tối ưu thu hồi class do JVM quyết định |
| Có phải GC? | Không: mất reference khác với đã thu hồi; GC có thể chạy sau | Không | Gắn với khả năng thu hồi defining loader |
| Có xảy ra ngay? | Reference có thể bị bỏ ngay; thu hồi không được bảo đảm | Lời gọi diễn ra ngay, có thể ném I/O exception | Không có thời hạn bảo đảm |
| Có ngăn dùng object cũ không? | Object còn reference khác thì vẫn dùng được | Không tự vô hiệu hóa object/class đã nạp | JVM không được unload class khi loader còn potentially reachable |
| Có đóng resource riêng của plugin không? | Không | Chỉ những resource thuộc trách nhiệm URL loader | Không phải API cleanup tổng quát |
| Có làm class mới được nạp không? | Không trực tiếp | Sau close không tìm/define class mới từ URL search path của loader | Unloading không phải class loading |
| Bằng chứng phù hợp | GC roots, heap dump, WeakReference có giới hạn | Quan sát close, handles và lookup sau close | Class unloading logs, classloader stats, GC analysis |

Từ bảng này, hãy tách hai câu hỏi thường gặp: **“JAR đã được đóng chưa?”** và **“Các class của plugin đã được unload chưa?”**. Câu trả lời cho câu đầu không quyết định câu thứ hai.

## 2. Mất tham chiếu object: không đồng nghĩa object đã bị thu hồi

Ví dụ Java:

```java
Object a = new Object();
Object b = a;
a = null;
System.out.println(b != null);
```

Stdout:

```text
true
```

`a = null` chỉ xóa **một** tham chiếu. Biến `b` vẫn có reference tới object ban đầu nên ta vẫn có thể dùng object ấy.

Ngay cả khi không còn reference mạnh nào, GC cũng không có nghĩa vụ thu hồi object vào thời điểm bạn chỉ định. JVM có thể tối ưu liveness, phân loại references (strong/soft/weak/phantom) và quyết định lúc nào cần thực hiện GC. Đừng đánh đồng kết thúc scope của một biến với sự kiện giải phóng heap diễn ra ngay tức thì.

### Hiểu reachability qua GC roots

```text
GC roots
  |
  +-- Java thread stack -> local variable -> Object A
  |
  +-- Host static field -> registry -> Object B
  |
  +-- JNI global ref ---------------------> Object C

Object D ----> Object E
   ^                |
   +----------------+
   (không còn đường mạnh từ GC root)
```

- A, B, C có thể còn được giữ sống do đường reference xuất phát từ root.
- D và E có thể đủ điều kiện thu hồi nếu thực sự không còn đường giữ sống nào khác.
- Chu trình `D → E → D` tự nó **không** chứng minh memory leak: GC có thể thu hồi cả cụm không còn reachable.
- Một object bị bỏ khỏi biến local nhưng còn nằm trong collection/static registry vẫn có thể reachable.

Khi chẩn đoán, câu hỏi là **“còn đường từ root tới object không?”**, không chỉ “đã thấy dòng `variable = null` chưa?”.

### `System.gc()` có bắt buộc thu hồi không?

Không. `System.gc()` biểu thị yêu cầu GC; JVM có chính sách quyết định việc thực hiện và thời điểm. Đừng viết chương trình dựa trên giả định:

```java
plugin = null;
System.gc();
// Sai nếu tin rằng class plugin chắc chắn đã unload từ đây.
```

Đây là **đoạn minh họa**, giả định biến `plugin` đã được khai báo. Muốn dừng plugin đúng cách, phải quản lý tasks, callbacks, resources và loader trước; GC không phải lifecycle API.

## 3. `URLClassLoader.close()`: đóng URL source và handles, không xóa định nghĩa class

Trong Java 17, `java.net.URLClassLoader` là class loader tìm class/resource từ URL tới thư mục hoặc JAR. Nó triển khai `Closeable`, có method:

```java
public void close() throws IOException
```

Hợp đồng API Java SE 17 nói:

- Sau close, URL loader không còn được dùng để nạp class/resource **mới do chính loader đó định nghĩa từ nguồn URL**.
- Class/resource **đã nạp** còn có thể được truy cập.
- Các class/resource do **parent** định nghĩa vẫn có thể được truy cập qua delegation.
- Với các `jar:`/`file:` URL, loader cố đóng các file/JAR mà nó đã mở.
- Nếu một thread đang load class **đồng thời** với lúc thread khác gọi close, kết quả lần load đang chạy là **không được xác định** theo JavaDoc. Host phải điều phối stop/await trước khi đóng.
- Close có thể ném `IOException`; đóng lại một loader đã đóng không tạo thêm tác dụng.

Điều `close()` **không làm**: xóa `Class<?>`, xóa instance, reset static fields, ngừng execution của method đã nạp, dừng executor/threads của plugin, đóng mọi socket/DB connection, hoặc yêu cầu GC/class unloading ngay.

### Vì sao method của class cũ vẫn chạy?

Vì bytecode và runtime metadata của class đã nạp không nằm trong “stream còn mở của JAR” theo cách khiến đóng file đồng nghĩa vô hiệu hóa object. Có thể JVM đã load/link class, tạo object và resolve method cần gọi.

Tuy nhiên, **không phải mọi method của một class cũ đều chắc chắn thành công sau close**. JVM có thể chỉ resolve một class phụ thuộc khi method được gọi lần đầu. Nếu dependency ấy chưa nạp và chỉ có trong URL của loader đã đóng, chương trình có thể nhận `NoClassDefFoundError` hoặc lỗi liên kết phù hợp.

Phân biệt:

- **Class A đã load:** thường vẫn có thể truy cập sau close.
- **Class B chỉ có file trên đĩa nhưng chưa load:** loader đã close không thể tìm mới B từ URL đó.
- **Class từ parent:** có thể tiếp tục truy cập sau close nếu parent cung cấp.
- **Object vẫn được tham chiếu:** không tự biến thành `null` hoặc “đã destroy”.

## 4. Class unloading: điều kiện của JLS 17

Java Language Specification `12.7` quy định rằng một class/interface **có thể được unload nếu và chỉ nếu defining class loader của nó có thể được GC thu hồi**. Class/interface do **bootstrap loader** nạp không được unload theo quy tắc này.

Cụm “**có thể**” rất quan trọng:

- **Đủ điều kiện không phải bắt buộc.** JVM có thể chưa chạy GC, chưa thực hiện class unloading hoặc giữ memory committed để tái sử dụng.
- **Đóng loader không phải điều kiện đủ.** Một `URLClassLoader` đã closed vẫn có thể reachable từ host objects, thread pool hoặc cache.
- **Mất reference instance không phải điều kiện đủ.** Loader vẫn có thể định nghĩa nhiều class khác và vẫn được host giữ.
- **Không được unload khi defining loader còn potentially reachable.** Nếu VM unload class trong lúc loader còn sống, class có thể phải nạp lại, phá vỡ tính nhất quán của static state, object identity và native interactions.
- **Không có Java SE method `Class.unload()` hay `loader.unloadClass(name)`** cho ứng dụng gọi để buộc dỡ một class riêng trong loader còn sống.

### Loader nhận yêu cầu không nhất thiết là defining loader

```java
Class<?> stringType = pluginLoader.loadClass("java.lang.String");
System.out.println(stringType.getClassLoader() == null);
```

Đây là **đoạn minh họa**, giả định `pluginLoader` tồn tại và method xử lý checked exception. Trong Java SE thông thường, stdout là `true`: `String` được bootstrap định nghĩa dù lời yêu cầu khởi đầu ở plugin loader. Việc đóng plugin loader không làm `String` bị unload.

Với `plugin.Widget` thực sự do `PluginLoader` định nghĩa, VM mới xét điều kiện thu hồi **PluginLoader** để quyết định class có thể unload.

**Type identity** trong JVM phụ thuộc vào **binary class name + defining class loader**. Vì vậy hai loader độc lập có thể định nghĩa hai class cùng tên nhưng tạo hai runtime types khác nhau. Hot reload bằng loader mới có thể khiến bản v1 và v2 **cùng tồn tại**, nếu loader v1 chưa đủ điều kiện thu hồi.

## 5. Ví dụ Java 17+ có stdout đã kiểm chứng

Ví dụ này cố ý **giữ** các reference đến instance/class/loader ngay cả sau close, nhằm chứng minh close không xóa class cũ. Không dùng ví dụ này để tuyên bố đã đo class unloading.

### Cấu trúc project

```text
loader-lifetime-demo/
├── src/plugin/
│   ├── Alive.java
│   └── Later.java
├── LoaderLifetimeDemo.java
├── plugin-classes/       (output class của plugin)
└── host-classes/         (output class của host)
```

Tạo hai thư mục output nếu chưa có và chạy lệnh từ `loader-lifetime-demo`. Khi chạy host, **không đưa `plugin-classes` vào `-cp`**.

### Class plugin đã nạp trước close

`src/plugin/Alive.java`:

```java
package plugin;

public final class Alive {
    private final int value = 41;

    public int plusOne() {
        return value + 1;
    }
}
```

`src/plugin/Later.java`:

```java
package plugin;

public final class Later {
    public static String text() {
        return "late";
    }
}
```

`Alive` sẽ được tìm và tạo instance trước close. File `Later.class` tồn tại trên đĩa nhưng chưa được yêu cầu load.

### Host và URLClassLoader

`LoaderLifetimeDemo.java`:

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.lang.reflect.Method;

public class LoaderLifetimeDemo {
    public static void main(String[] args) throws Exception {
        URL directory = Path.of("plugin-classes").toUri().toURL();

        URLClassLoader loader = new URLClassLoader(
                new URL[]{directory},
                ClassLoader.getPlatformClassLoader());

        Class<?> type = loader.loadClass("plugin.Alive");
        Object instance = type.getConstructor().newInstance();
        Method method = type.getMethod("plusOne");

        System.out.println("plugin defining loader="
                + (type.getClassLoader() == loader));
        System.out.println("before close=" + method.invoke(instance));

        loader.close();

        System.out.println("after close=" + method.invoke(instance));
        System.out.println("cached class="
                + (loader.loadClass("plugin.Alive") == type));

        try {
            loader.loadClass("plugin.Later");
            System.out.println("unexpected");
        } catch (ClassNotFoundException expected) {
            System.out.println("unloaded? no; new class not found");
        }

        System.out.println("instance still alive="
                + (instance.getClass() == type));
    }
}
```

Parent ở đây là platform class loader: nó có thể giúp tìm core platform classes nhưng không có `plugin.Alive`/`plugin.Later`. Nhờ vậy `Alive` được chính child URL loader định nghĩa.

### Biên dịch/chạy (JDK 17+)

```bash
javac --release 17 -d plugin-classes src/plugin/Alive.java src/plugin/Later.java
javac --release 17 -d host-classes LoaderLifetimeDemo.java
java -cp host-classes LoaderLifetimeDemo
```

Stdout chính xác đã được biên dịch/chạy xác minh:

```text
plugin defining loader=true
before close=42
after close=42
cached class=true
unloaded? no; new class not found
instance still alive=true
```

### Giải thích dòng output

1. `plugin defining loader=true`: plugin class do URL loader tạo/define thật.
2. `before close=42`: instance hoạt động trước close.
3. `after close=42`: instance và method sẵn có vẫn chạy được sau close.
4. `cached class=true`: loader tra cứu lại class đã nạp; nó không phải nạp định nghĩa mới từ URL.
5. `unloaded? no; new class not found`: class `Later` chưa từng load, nên sau close không có nguồn URL để tìm class mới; parent cũng không có class ấy. Đây **không phải chứng cứ** `Alive` đã unload.
6. `instance still alive=true`: object và `Class<?>` vẫn hiện diện, đồng thời các biến `loader`, `type`, `instance`, `method` còn tham chiếu trong code.

**Kết luận của test giới hạn:** close không tương đương unload. Test **không hề buộc hoặc chứng minh** có sự kiện class unloading.

## 6. Object, Class<?> và ClassLoader liên kết tới nhau

Một object instance có runtime class riêng; `instance.getClass()` trả `Class<?>` mirror của định nghĩa đó; `Class.getClassLoader()` cho biết **defining loader** (có thể `null` cho bootstrap). Nếu host còn giữ instance hoặc mirror của plugin, JVM phải duy trì runtime type tương ứng.

Ví dụ đường retention: `host registry → instance → class → plugin defining loader`. Tuy nhiên **mất một reference** tới instance không đủ chứng minh không còn đường từ GC roots; bài về quan hệ `Class`–instance–loader đi sâu vào graph và type identity.

## 7. WeakReference: quan sát, không ép unload

`WeakReference<ClassLoader>` **không giữ loader sống bằng tham chiếu mạnh**. Nó có thể giúp quan sát loader còn được GC giữ hay referent đã bị clear; nhưng **không chứng minh** unloading qua một lần chạy.

Chương trình minh họa Java 17 có thể biên dịch nếu `plugin-classes/plugin/Alive.class` đã tạo ở demo trên:

```java
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class UnloadProbe {
    static WeakReference<ClassLoader> runAndRelease(URL[] urls)
            throws Exception {
        URLClassLoader loader = new URLClassLoader(
                urls, ClassLoader.getPlatformClassLoader());

        try {
            Class<?> type = loader.loadClass("plugin.Alive");
            Object instance = type.getConstructor().newInstance();
            type.getMethod("plusOne").invoke(instance);
        } finally {
            loader.close();
        }

        return new WeakReference<>(loader);
    }

    public static void main(String[] args) throws Exception {
        URL[] urls = {
            Path.of("plugin-classes").toUri().toURL()
        };

        WeakReference<ClassLoader> ref = runAndRelease(urls);
        System.gc(); // Chỉ là GC request/hint.

        System.out.println("loader cleared? " + (ref.get() == null));
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 -d host-classes UnloadProbe.java
java -cp host-classes UnloadProbe
```

Output **không được bảo đảm**: có thể in `loader cleared? true` hoặc `loader cleared? false`.

Tại sao?

- `System.gc()` không cam kết sẽ chạy và dọn loader vào thời điểm kiểm tra.
- Loader có thể còn được giữ bởi reference trong JVM, task/registry khác, hoặc chưa đủ điều kiện thu hồi.
- JIT và runtime liveness có thể ảnh hưởng thời điểm các local references được coi là cần thiết.
- Ngay cả khi weak referent đã clear, đó **không phải phép đo trực tiếp** mọi bytes class metadata/metaspace hoặc mọi OS handles đã trả về.

### Cách diễn giải đúng

| Quan sát | Điều kết luận được | Điều không thể khẳng định |
| --- | --- | --- |
| `ref.get() != null` | Loader chưa bị clear ở thời điểm đọc | Không chắc có leak; có thể GC chưa xử lý |
| `ref.get() == null` | Weak referent đã bị clear qua reference processing hoặc do chính code clear | Không khẳng định mọi metaspace/native memory đã trả OS |
| Không có class unload log | Chưa quan sát unload event tương ứng | Không chứng minh close sai |
| Heap giảm sau GC | Heap objects có thay đổi theo thống kê đang đo | Không chứng minh class loader/class metadata đã unload |

Để kiểm tra class unloading, phối hợp **loader liveness**, **heap retaining-path analysis** và **VM class unloading events/stats** thay vì chỉ WeakReference.

## 8. Heap, metaspace và JAR handles không cùng một tài nguyên

| Loại tài nguyên | Ví dụ | Cơ chế xử lý chính |
| --- | --- | --- |
| **Java heap** | Instances, arrays, `URLClassLoader` object, collection/cache | GC khi objects không còn được giữ sống |
| **Class metadata/metaspace** | Metadata của các class do loader định nghĩa, trên HotSpot | JVM class unloading và quản lý metaspace |
| **JAR/file handles** | `plugin.jar` do `URLClassLoader` mở | `URLClassLoader.close()` theo contract |
| **Network/database/file của plugin** | `Socket`, DB connection, `FileInputStream` plugin tự mở | Plugin phải close theo API của tài nguyên |
| **Threads/executors** | Worker, scheduler, task queue | Cancel/stop/shutdown/await theo lifecycle |
| **JNI/native resources** | Native allocations, JNI global refs, library state | JNI/native subsystem cleanup; không dựa vào class unload tự động |
| **JIT/code cache** | Compiled native instructions và VM caches | VM quản lý, không tương đương `Runtime.freeMemory()` |

Metaspace là thuật ngữ/cơ chế phổ biến của **HotSpot**; Java SE không bắt buộc mọi JVM phải sử dụng cách tổ chức bộ nhớ giống HotSpot.

Ngay cả nếu class metadata được unload, process RSS hoặc số committed/reserved memory có thể **không giảm ngay**, do VM còn giữ memory để dùng lại. Vì vậy:

**`Runtime.getRuntime().freeMemory()` tăng/giảm không phải phép đo trực tiếp class unloading.** Nó liên quan heap, không thay thế classloader statistics, GC logs và kiểm tra retaining paths.

## 9. Cách đo class unloading trên Java 17+

Với HotSpot/OpenJDK tương thích, có thể bật unified logging:

```bash
java -Xlog:class+load=info,class+unload=info -cp host-classes LoaderLifetimeDemo
```

Giải thích:

- `class+load` ghi class-loading events.
- `class+unload` ghi events khi JVM thực sự unload class.
- Demo ngắn có thể **không in event unload** vì references còn sống và GC chưa tiến hành.
- Không dùng “đã thấy class+load” làm bằng chứng “đã unload”.
- Các tag/logging capabilities và format cụ thể phụ thuộc bản JVM.

Nếu đang có một JVM server chạy lâu, công cụ `jcmd` có thể hỗ trợ:

```bash
jcmd <PID> VM.classloader_stats
```

Đây là **cú pháp minh họa**: thay `<PID>` bằng process ID thật; công cụ và lệnh chỉ có khi bản JDK/runtime hỗ trợ và bạn có quyền attach tương ứng.

Nên kết hợp:

1. **Classloader statistics:** số loaders/classes, metadata/metaspace usage trước và sau nhiều lần deploy.
2. **Heap dump:** tìm **retaining path từ GC root tới PluginClassLoader** hoặc instance/class.
3. **Thread inspection:** thread pool, scheduler, TCCL, ThreadLocal đang giữ loader nào.
4. **JFR (nếu phù hợp):** class-loading/unloading events với recording settings thích hợp.
5. **OS file handles:** xác minh file/JAR còn bị thành phần nào mở.
6. **GC logs/metrics:** hiểu thời điểm thu hồi và phân biệt class unloading với object collection.

Đừng để test quan sát tự tạo leak: một static debug list chứa `Class<?>` hay loader sẽ làm class cũ khó được thu hồi.

## 10. Mất references trong plugin host: các nguồn leak thường gặp

Những nguồn tham chiếu thường gặp là host static registries/caches, listener/callback, reflection metadata, worker threads (TCCL và `ThreadLocal`), pending jobs, `ServiceLoader` và framework registrations. Mỗi nguồn phải được gỡ tại owner thực sự; gọi `close()` không tự dọn chúng.

Hãy điều tra `path to GC roots` của **loader generation cũ** và đối chiếu vòng đời plugin. Những bài cache, thread và listener trong chương cung cấp cách kiểm tra chuyên sâu.

## 11. Lifecycle đúng để plugin có cơ hội được unload

**Quiesce → Stop/clean up → Close URL loader → Release references → Observe.** Chặn tác vụ mới, xử lý công việc đang chạy và unregister callback trước khi đóng loader; dọn `ThreadLocal` trên đúng thread, đóng resource riêng, sau đó bỏ registry/caches của generation cũ.

Trình tự này giúp loader **có thể đủ điều kiện reclaim** khi toàn bộ retaining graph được cắt. Không có lời hứa JVM sẽ unload trong một chu kỳ GC nhất định.

## 12. Hot reload: tạo loader mới, không “reset loader cũ”

Đổi file `.class` hoặc JAR trên đĩa không cập nhật định nghĩa runtime đã được loader cũ nạp. Để dùng bytecode mới cho cùng binary name, ứng dụng thường dùng **defining loader mới**, chuyển traffic sang generation mới rồi retire generation cũ.

Các instance cũ vẫn gắn với runtime class cũ; chúng không tự đổi kiểu chỉ vì loader hoặc file nguồn đã đóng/thay.

## 13. Bảng tình huống và sai lầm phổ biến

| Tình huống | Kết luận đúng |
| --- | --- |
| `object = null`, heap chưa giảm | Có thể vẫn còn references hoặc chưa GC; không đồng nghĩa loader leak |
| `loader.close()` thành công, method plugin vẫn chạy | Đúng contract; close không tự unload object/class |
| Sau close, `loadClass` cho class chưa nạp thất bại | Loader đã đóng nguồn URL cho class mới; **không** chứng minh class cũ unload |
| JAR vẫn bị khóa dù close | Có thể handles do thành phần khác giữ hoặc close gặp I/O error |
| `System.gc()` chạy, WeakReference vẫn non-null | Chưa đủ kết luận; tìm strong retaining paths và GC timing |
| WeakReference cleared nhưng RSS cao | JVM có thể giữ committed native/metaspace memory để tái sử dụng |
| Metaspace tăng qua hàng trăm lần reload | Nghi classloader leak hoặc unloading chưa theo kịp; đo loaders/classes/roots |
| Object plugin mất nhưng `Class<?>` ở host cache còn | `Class<?>` vẫn có thể giữ defining loader sống |
| Loader cũ và loader mới chứa class cùng tên | Khác runtime types nếu được hai loaders khác nhau define |
| Module/plugin interface duplicate gây `ClassCastException` | Chia sẻ API qua parent loader, không trông chờ close/GC sửa |
| Object Java được thu hồi nhưng socket không đóng đúng lúc | Quản lý external resources bằng explicit close/stop |
| Unloading không có log trong demo ngắn | Không bắt buộc xảy ra; không có event ≠ close thất bại |

## 14. Hỏi–đáp và bài tập tự kiểm tra

**Q1.** `Object a = new Object(); Object b = a; a = null;` Object ban đầu đã đủ điều kiện GC chưa?

*Trả lời:* Khi `b` còn là live strong reference, không thể nói object đã mất mọi reference. `a = null` chỉ xóa một đường tham chiếu.

**Q2.** Vì sao `method.invoke(instance)` sau `loader.close()` vẫn thành công trong demo?

*Trả lời:* Class, method và instance đã được nạp/tạo; close không hủy chúng hay xóa bytecode runtime.

**Q3.** Nếu `loader.loadClass("plugin.Later")` sau close ném `ClassNotFoundException`, có thể kết luận `plugin.Alive` đã unload?

*Trả lời:* Không. `Later` là class chưa từng load, còn `Alive` vẫn được giữ và sử dụng. Không tìm được class mới chỉ chứng tỏ URL lookup đã đóng.

**Q4.** `loader = null; System.gc();` có đảm bảo class unloading?

*Trả lời:* Không. Registry, `Class<?>`, instances, TCCL, ThreadLocal hoặc native references khác có thể còn giữ loader. GC/unloading cũng không có deadline.

**Q5.** Static map trong class do host system loader define đang giữ `plugin.Entry.class`. Làm sao sửa?

*Trả lời:* Xóa entry liên quan ở host static cache/registry khi dừng deployment, sau đó xác nhận không còn retaining references tới loader cũ.

**Q6.** Vì sao JVM không thể unload class khi defining loader vẫn potentially reachable?

*Trả lời:* Loader có thể được dùng để resolve/load class tham chiếu class cũ. Tự ý unload sẽ tạo nguy cơ class reloading làm thay đổi identity, static state và side effects, trái yêu cầu transparency của JLS.

**Q7.** `String.class` có bị unload do đóng một plugin loader con không?

*Trả lời:* Không. `String` thường do bootstrap loader định nghĩa; JLS không cho unload bootstrap-defined classes.

**Q8.** WeakReference tới loader đã được clear có khẳng định toàn bộ metaspace đã trả về OS?

*Trả lời:* Không. Đây chỉ là tín hiệu referent đã bị clear; class unloading/committed memory cần đo bằng JVM diagnostics riêng.

**Q9.** Vì sao `Runtime.freeMemory()` không nên dùng để kết luận class unloading?

*Trả lời:* Nó phản ánh trạng thái heap, không trực tiếp đo HotSpot metaspace hoặc events unload.

**Q10.** Plugin JAR muốn bị xóa/cập nhật trên Windows. Đã close URLClassLoader có đủ chưa?

*Trả lời:* Close là bước quan trọng giải phóng file/JAR handles do loader mở; nhưng các streams/handles khác cũng có thể đang giữ file. Stop work, close owned resources và điều tra OS locks nếu vẫn không xóa được.

**Q11.** Một thread pool worker giữ TCCL = oldPluginLoader sau undeploy. Hậu quả?

*Trả lời:* Nó có thể giữ old loader sống, gây classloader leak và làm service discovery của tác vụ khác dùng sai namespace. Phải restore trong worker bằng `finally`.

**Q12.** Sau reload, cùng `plugin.Widget` có hai phiên bản và xảy ra `ClassCastException`. Vì sao?

*Trả lời:* Class identity gồm binary name + defining loader. Hai loaders define hai types riêng. Dùng shared interfaces/DTO ở parent để host và plugin giao tiếp qua contract chung.

## 15. Checklist khi chuẩn bị dỡ plugin

Trước khi retire, xác định defining loader, bảo đảm API chung vẫn dùng một runtime type nhất quán; chặn new work, drain tasks, unregister listeners và caches; dọn TCCL/`ThreadLocal` tại đúng threads; đóng I/O handles và URL loader; bỏ strong roots giữ generation cũ.

Cuối cùng theo dõi log/heap retaining path qua nhiều chu kỳ để kiểm tra điều kiện unload. Không biến việc `System.gc()` trả về thành điều kiện pass.

## Kết luận

**Mất tham chiếu object** là câu chuyện **reachability**; **`URLClassLoader.close()`** là câu chuyện **đóng class/resource search path và file handles của loader**; **class unloading** là câu chuyện **JVM có thể thu hồi định nghĩa class khi defining loader đủ điều kiện GC**. Không có mũi tên suy diễn bắt buộc từ một sự kiện sang sự kiện tiếp theo.

Trong hệ thống plugin/hot reload, phương pháp bền vững là **stop → unregister và cleanup threads/resources → restore context → close loader → release strong references → quan sát GC/unloading nếu cần**. Đừng lấy một lần `System.gc()`, một biến local được gán `null`, hay một JAR đã được close thành bằng chứng class metadata lập tức biến mất.

**Tài liệu chuẩn Java SE 17:**

- [`URLClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): `close()`, class/resource đã nạp và file/JAR handles.
- [JLS 17 `12.7` — Unloading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7): quy tắc về defining loader có thể được GC thu hồi.
- [`ClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findLoadedClass` và parent delegation.
- [`WeakReference` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html): weak reachability và clearing.
- [`System` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html): `gc()` và giới hạn của việc yêu cầu GC.
- [Java 17 `jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): chẩn đoán các JVM đang chạy khi môi trường hỗ trợ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-unloading-and-memory-004">Quan hệ giữa Class, đối tượng instance và loader</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa Class, đối tượng instance và ClassLoader khi dỡ lớp trong Java

## 1. Câu hỏi trung tâm: điều gì còn sống khi plugin ngừng chạy?

Trong một hệ plugin, ứng dụng tạo `URLClassLoader` riêng, yêu cầu nó nạp `plugin.Widget`, tạo một instance, chạy một số phương thức rồi đóng loader. Khi đó có ba loại đối tượng/khái niệm cần phân biệt:

1. **`ClassLoader` instance:** đối tượng tổ chức việc tìm và định nghĩa class; xác định không gian tên runtime cho các class do nó định nghĩa.
2. **`Class<?>` object:** đại diện phản chiếu (*class mirror*) cho **một runtime class/interface cụ thể**. Có thể lấy qua `Widget.class`, `instance.getClass()` hoặc kết quả của `loadClass`.
3. **Instance của class:** một đối tượng bình thường được tạo từ class đó bằng constructor, reflection, factory hoặc cơ chế khác.

Ba thứ liên quan nhưng **không phải một đối tượng**, cũng không có cùng điều kiện vòng đời. Đóng tài nguyên của ClassLoader không làm các instance tự biến mất, còn xóa một biến tham chiếu instance không đồng nghĩa các class đã được unload.

Các câu hỏi quan trọng:

- Tại sao `object.getClass().getClassLoader()` cho biết loader định nghĩa kiểu của object?
- Vì sao giữ lại một instance, một `Class<?>` hay một thread đang thực thi mã plugin có thể ngăn class/loader đủ điều kiện thu hồi?
- Vì sao hai loader dùng cùng bytecode tạo hai kiểu Java khác nhau?
- `URLClassLoader.close()`, garbage collection, class unloading và Metaspace khác nhau thế nào?
- Làm thế nào xác định **GC root → object → class/loader** trong một sự cố memory leak?

Đây là trọng tâm của quan hệ **Class ↔ instance ↔ loader** trong ngữ cảnh **dỡ lớp và bộ nhớ**.

## 2. Hình dung ba thực thể và liên kết của chúng

Ví dụ mã nguồn:

```java
package plugin;

public final class Widget {
    public String message() {
        return "hello";
    }
}
```

Đây là source của một class mẫu, không phải chương trình có `main` độc lập.

Khi một custom loader `L` định nghĩa `plugin.Widget`, rồi tạo hai instance, có thể hình dung:

```text
Custom ClassLoader object L
        |
        | defining loader của runtime class
        v
Runtime class: ("plugin.Widget", L)
        |
        +-- Class<?> mirror C
        |       C.getName()          = "plugin.Widget"
        |       C.getClassLoader()   = L
        |
        +-- Instance A ---- A.getClass() == C
        |
        +-- Instance B ---- B.getClass() == C
```

Sơ đồ là **mô hình khái niệm**: JVM có thể tổ chức heap, mirrors và metadata khác nhau theo implementation. Không nên suy ra mọi mũi tên là một trường Java public có thể nhìn trực tiếp bằng reflection.

Những phép so sánh thường đúng khi cả hai đối tượng được tạo từ cùng runtime class:

```java
Class<?> aType = a.getClass();
Class<?> bType = b.getClass();

System.out.println(aType == bType); 
System.out.println(aType.getClassLoader() == definingLoader);
```

Đoạn này là **snippet** (cần biến `a`, `b`, `definingLoader`). `Class<?>` đại diện một định nghĩa runtime, không phải một descriptor chữ thuần và cũng không phải instance của `Widget`.

### Tại sao `Class<?>` là object, nhưng lại khác object đang học?

`java.lang.Class` là một class thuộc nền tảng Java. Bản thân `Widget.class` là một đối tượng Java thuộc kiểu `Class<Widget>` hoặc `Class<?>`. Còn `new Widget()` là một **instance của `Widget`**. Khác biệt:

- `Widget.class.getName()` hỏi tên định nghĩa kiểu.
- `widget.getClass()` trả class mirror của kiểu runtime của instance.
- `Widget.class.getClassLoader()` trả **defining loader** của `Widget`.
- `Widget.class.getClass()` mô tả class của **class mirror**, thường là `java.lang.Class`; nó không trả `Widget`.
- Class literal `Widget.class` trong code host yêu cầu kiểu Widget khả dụng với loader/linkage của host; trong hệ plugin động, host thường không thể import implementation cụ thể và sẽ làm việc qua interface chung cùng reflection.

**Không nên đồng nhất** câu “Class là object” với “tạo object class mới khi tạo mỗi instance”. Hai instance cùng runtime class thông thường trả **cùng đối tượng `Class<?>`** từ `getClass()`.

## 3. Danh tính runtime class: binary name cộng defining loader

JVM xác định danh tính của một runtime class/interface bằng cặp:

```text
(binary name, defining ClassLoader)
```

Ví dụ:

```text
("plugin.Widget", PluginLoader#1) !=
("plugin.Widget", PluginLoader#2)
```

Dù hai loader đọc **cùng bytecode byte-for-byte**, nếu mỗi loader tự định nghĩa `plugin.Widget` thì chúng là **hai runtime types khác nhau**.

| Đặc tính | Class ở Loader A | Class ở Loader B |
| --- | --- | --- |
| `getName()` | `plugin.Widget` | `plugin.Widget` |
| `getClassLoader()` | A | B |
| `Class<?> ==` | Không bằng bản ở B | Không bằng bản ở A |
| Instance có thể cast sang bản kia? | Không chỉ vì tên/bytes giống | Không chỉ vì tên/bytes giống |
| Static fields | Gắn với runtime class A | Gắn với runtime class B |
| Class initialization | Độc lập theo class A | Độc lập theo class B |

Nếu loader con gọi `loadClass("plugin.Widget")` nhưng parent đã có class ấy, kết quả có thể là class do parent **định nghĩa**. Trong trường hợp đó, loader con chỉ là loader **khởi xướng yêu cầu** (*initiating loader*), không phải defining loader. Nhiều code nhầm loader được gọi với loader xác lập type identity.

### Trường hợp `java.lang.String` và bootstrap loader

Các class lõi như `java.lang.String` được bootstrap loader định nghĩa trong môi trường Java tiêu chuẩn. Vì vậy:

```java
System.out.println(String.class.getClassLoader() == null);
```

thường in:

```text
true
```

Giá trị `null` từ `Class.getClassLoader()` là biểu diễn API cho **bootstrap loader**, không có nghĩa kiểu `String` “không có loader” theo mô hình JVM.

Với **primitive types** như `int.class` và `void.class`, `getClassLoader()` cũng trả `null` theo API nhưng không nên suy rằng đây là class file định nghĩa bởi một bootstrap ClassLoader bình thường. Với **array classes**, JVM tạo array class theo quy tắc riêng; defining loader được gắn theo kiểu thành phần tham chiếu hoặc biểu diễn bootstrap nếu thành phần là primitive.

## 4. Khi có instance còn reachable, điều gì được giữ?

Giả sử host giữ:

```java
Object pluginInstance = pluginType
        .getDeclaredConstructor().newInstance();
```

Đây là snippet, `pluginType` là Class mirror của implementation plugin và constructor được phép truy cập.

Khi `pluginInstance` còn reachable, JVM phải giữ kiểu runtime đủ để nhận biết và sử dụng đối tượng đó. Nếu defining loader cùng các runtime classes do nó định nghĩa được dỡ tùy tiện, VM sẽ không còn mô hình kiểu/method dispatch hợp lệ cho instance đang sống.

Đối với dỡ class theo JVMS, một class/interface **chỉ có thể** unload khi defining ClassLoader của nó có thể được garbage collector thu hồi. Đây là **điều kiện cần**, không phải lệnh yêu cầu JVM unload class ngay sau khi tham chiếu Java được gán `null`.

Những reference từ host thường khiến plugin chưa sẵn sàng được thu hồi:

```text
GC Root (thread / static field / registry)
   |
   v
Host service registry
   |
   v
Plugin instance
   |
   v
Runtime class / JVM metadata
   |
   v
Defining ClassLoader
```

Mũi tên liên quan runtime class/loader là **sơ đồ quan hệ reachability ở mức VM**, không giả định instance Java có trường `ClassLoader loader;`. Điểm chính là **không thể dỡ định nghĩa class khi các instance của nó vẫn còn cần dùng**.

### Còn nếu giữ lại một Class<?>?

Một reference từ host đến `Class<?>` của class plugin cũng có thể neo định nghĩa lớp và defining loader. Ví dụ:

```java
static final Map<String, Class<?>> PLUGIN_CLASS_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
```

Đây là snippet, cần import `Map` hoặc dùng tên đầy đủ; bản thân `Class<?>` từ plugin được lưu vào map lâu dài sẽ là điểm cần kiểm tra khi điều tra loader leak.

Tương tự, `Method`, `Constructor`, `MethodHandle`, proxy metadata, lambda, method reference và framework reflection caches có thể duy trì đường tham chiếu khiến code loader cũ còn reachable. Mức độ giữ object cụ thể phụ thuộc implementation/API, nhưng **đừng chỉ tìm tham chiếu trực tiếp tới chính ClassLoader**.

## 5. Ví dụ Java 17: hai loader, hai Class objects, instance vẫn dùng được sau close

Thí nghiệm tạo một class file lúc runtime bằng `JavaCompiler` (JDK 17+), rồi đọc cùng thư mục bytecode qua **hai `URLClassLoader` độc lập**. Ta đo những điều có thể khẳng định **không phụ thuộc GC**:

1. Cùng tên class và cùng bytecode không tạo cùng runtime `Class<?>` nếu defining loaders khác.
2. Mỗi instance trả về đúng class mirror do defining loader tương ứng định nghĩa.
3. Đóng URLClassLoader không làm instance đã tồn tại tự động mất khả năng chạy phương thức đã được định nghĩa.
4. Class nền tảng vẫn đến từ bootstrap/parent delegation.

### 5.1. Mã đầy đủ: `ClassInstanceLoaderDemo.java`

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class ClassInstanceLoaderDemo {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Use JDK 17+ with javac");
        }

        Path work = Files.createTempDirectory("class-instance-loader-");
        try {
            Path source = work.resolve("src/plugin/Widget.java");
            Path classes = work.resolve("classes");
            Files.createDirectories(source.getParent());
            Files.createDirectories(classes);

            Files.writeString(source, """
                    package plugin;
                    public final class Widget {
                        public String message() {
                            return "hello";
                        }
                    }
                    """);

            int exit = compiler.run(null, null, null,
                    "--release", "17",
                    "-d", classes.toString(), source.toString());
            if (exit != 0) {
                throw new IllegalStateException("Compilation failed");
            }

            URL classesUrl = classes.toUri().toURL();
            ClassLoader parent = ClassLoader.getPlatformClassLoader();

            Class<?> typeA;
            Object instanceA;

            try (URLClassLoader loaderA = new URLClassLoader(
                        new URL[]{classesUrl}, parent);
                 URLClassLoader loaderB = new URLClassLoader(
                        new URL[]{classesUrl}, parent)) {

                typeA = loaderA.loadClass("plugin.Widget");
                Class<?> typeB = loaderB.loadClass("plugin.Widget");

                instanceA = typeA.getDeclaredConstructor()
                        .newInstance();
                Object instanceB = typeB.getDeclaredConstructor()
                        .newInstance();

                System.out.println("sameBinaryName="
                        + typeA.getName().equals(typeB.getName()));
                System.out.println("sameClass=" + (typeA == typeB));
                System.out.println("aDefiningIsA="
                        + (typeA.getClassLoader() == loaderA));
                System.out.println("bDefiningIsB="
                        + (typeB.getClassLoader() == loaderB));
                System.out.println("instanceClassIsA="
                        + (instanceA.getClass() == typeA));
                System.out.println("crossLoaderCompatible="
                        + typeA.isInstance(instanceB));
                System.out.println("stringBootstrap="
                        + (loaderA.loadClass("java.lang.String")
                                .getClassLoader() == null));
            }

            // Cả hai loader đã close, nhưng instanceA/typeA
            // vẫn còn reference trong scope hiện tại.
            Object result = typeA.getMethod("message")
                    .invoke(instanceA);
            System.out.println("afterClose=" + result);
        } finally {
            try (Stream<Path> stream = Files.walk(work)) {
                for (Path path :
                        stream.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ClassInstanceLoaderDemo.java
java ClassInstanceLoaderDemo
```

Stdout mong đợi với JDK 17+ có compiler và quyền tạo file tạm:

```text
sameBinaryName=true
sameClass=false
aDefiningIsA=true
bDefiningIsB=true
instanceClassIsA=true
crossLoaderCompatible=false
stringBootstrap=true
afterClose=hello
```

### 5.2. Giải thích từng dòng

- **`sameBinaryName=true`:** cả hai type đều mang tên `plugin.Widget` theo `Class.getName()`.
- **`sameClass=false`:** vì hai `URLClassLoader` độc lập đã tự định nghĩa class đó; `Class<?>` mirror khác nhau.
- **`aDefiningIsA=true` và `bDefiningIsB=true`:** class A do loader A định nghĩa, class B do loader B định nghĩa. Cùng URL class directory không gộp chúng thành một kiểu.
- **`instanceClassIsA=true`:** instanceA thuộc runtime class A; `instanceA.getClass()` trả đúng class mirror A.
- **`crossLoaderCompatible=false`:** instanceB không được coi là instance của class A, dù code source và class name giống hệt.
- **`stringBootstrap=true`:** lớp `String` được bootstrap định nghĩa, không bị mỗi plugin loader tự định nghĩa.
- **`afterClose=hello`:** sau khi hai `URLClassLoader` được đóng bởi try-with-resources, `instanceA` và `typeA` vẫn còn reference và có thể dùng method đã nạp. Đây là minh chứng rằng **`close()` không phải `unload()`**.

Chương trình không dùng `System.gc()` và không đặt kỳ vọng thời điểm dỡ lớp, vì GC/class unloading không có stdout xác định theo thời gian.

### 5.3. Giới hạn của demo

- Class `Widget` không phụ thuộc class riêng nào khác được nạp muộn từ thư mục `classes` sau khi loader đóng. Nếu method cần nạp thêm một dependency chưa được định nghĩa trước `close()`, có thể xảy ra lỗi nạp sau close. Không suy rằng đóng URLClassLoader là vô hại trong mọi luồng thực thi.
- Ví dụ giữ `typeA` và `instanceA` bằng local references để **cố ý ngăn kết luận unload đã xảy ra**.
- Chỉ là ví dụ về class trên **classpath** qua `URLClassLoader`, không phải triển khai module layer hay plugin framework hoàn chỉnh.
- Việc xóa thư mục tạm chỉ xảy ra sau khi cả loader được đóng; có thể lỗi nếu môi trường từ chối xóa file, và đây là lỗi resource cleanup độc lập với class identity.
- Lệnh `--release 17` đảm bảo bytecode Java 17 cho class sinh ra; dự án demo chạy với Java 17+ có text block, stream `toList()`.

## 6. ClassLoader.close() không phải quy trình unload class

`URLClassLoader` triển khai `Closeable`, nên có thể dùng try-with-resources:

```java
try (URLClassLoader loader = new URLClassLoader(urls, parent)) {
    Class<?> plugin = loader.loadClass(binaryName);
    // Tạo instance và thực thi khi loader đang mở.
}
```

Đây là snippet, biến `urls`, `parent` và `binaryName` phải tồn tại ở ứng dụng.

**`close()` làm gì?** Theo API `URLClassLoader`, đóng những file JAR/resource loader đã mở và ngăn một số thao tác tìm thêm class/resource mới từ các URL đã được loader quản lý. Nó **không**:

- Tự gọi `Plugin.stop()` hay `AutoCloseable.close()` trên mọi plugin instance.
- Tự đóng worker executor, socket, JDBC connection hoặc event listener do plugin tạo.
- Tự xóa `Class<?>` đã định nghĩa, method/field đã liên kết hoặc các đối tượng Java còn reachable.
- Ép `System.gc()` hoặc bảo đảm JVM unload ngay khi close.
- Biến instance còn sống thành object “không hợp lệ”; các method đã nạp có thể tiếp tục chạy.
- Đảm bảo một loader cũ không còn được giữ bởi host static caches, TCCL hay framework metadata.

### Khi nào Class có cơ hội được unload?

Quy tắc quan trọng từ JVMS là một runtime class/interface có thể được unload **nếu và chỉ nếu** defining loader của nó có thể được GC thu hồi. Trong thực tế, để một custom loader đủ điều kiện thu hồi, ứng dụng cần bảo đảm không còn đường strong reference từ GC roots tới loader hoặc những class/instance và runtime structures khiến loader còn sống.

Điều này không có nghĩa:

```text
pluginLoader = null;
System.gc();
=> Class đã unload xong
```

Sơ đồ trên là **một kết luận sai**, không phải code có thể dựa vào. Gán một reference thành null chỉ loại bỏ **một cạnh**. GC là quá trình được JVM quyết định; tồn tại nhiều reference khác có thể khiến loader còn reachable.

### Vì sao JVM có thể giữ class metadata ở Metaspace?

Trên HotSpot JVM, class metadata thường được lưu trong **Metaspace** (bộ nhớ native), trong khi `Class<?>` mirrors là đối tượng Java theo mô hình runtime. Các đường reference từ heap/thread/static roots vẫn có thể giữ class/loader và metadata liên quan sống.

Do đó, một ứng dụng plugin có thể:

- Không tăng heap quá nhiều nhưng Metaspace tăng sau hàng trăm lần reload.
- Có heap dump cho thấy loader/plugin classes bị giữ bởi listener, cache hoặc thread.
- Giảm áp lực Metaspace khi dỡ được những classloader thực sự không còn cần thiết, nhưng mức bộ nhớ OS được trả lại và thời điểm unload phụ thuộc JVM/GC và allocator.

**Không đồng nhất** `OutOfMemoryError: Metaspace` với `OutOfMemoryError: Java heap space`. Đều là áp lực bộ nhớ nhưng vùng và nguyên nhân cần điều tra khác nhau.

## 7. Chu trình tham chiếu không phải lúc nào cũng gây leak

Một sơ đồ dễ khiến người mới hiểu sai:

```text
Plugin ClassLoader
   -> runtime class / metadata
   -> static field
   -> plugin instance
   -> object dùng class đó
   -> liên kết ngược về runtime class
```

Có thể tưởng “có vòng reference thì GC không thể thu hồi”. Thực tế **garbage collector dựa trên reachability từ GC roots**, không đơn giản đếm tham chiếu rồi từ chối mọi chu trình.

Nếu một nhóm object chỉ tham chiếu lẫn nhau mà **không còn reachable từ GC roots**, GC có thể thu hồi cả nhóm, với những điều kiện/cơ chế JVM thích hợp. Cùng một reasoning áp dụng khi loader và những class/instance của nó không còn được giữ bởi host, threads, registries hoặc những root khác.

Một vòng tham chiếu giữa plugin instance và callback là vấn đề nghiêm trọng **khi host event bus là GC root hoặc reachable từ root**:

```text
GC Root
   -> static HostEventBus
   -> callback
   -> plugin instance
   -> plugin runtime class
   -> loader
```

Chỉ xóa plugin khỏi một `PluginManager.map` **không** loại bỏ đường qua event bus nếu event bus vẫn giữ callback. Vì thế dỡ plugin cần **unregister tại nơi đã register**.

## 8. Các đường reference gây giữ ClassLoader phổ biến

Retaining paths phổ biến gồm **host cache → `Class<?>`/instance**, event bus → callback plugin, pool thread → TCCL/`ThreadLocal`, executor queue → pending task và service/framework registry → provider instance.

Cùng là reference tới plugin nhưng owner và cách cleanup khác nhau. Dùng heap dump `path to GC roots` để tìm nguồn sống lâu thực sự; bài static caches, threads và ServiceLoader giải thích các loại root này bằng ví dụ riêng.

## 9. WeakReference<ClassLoader>: quan sát được gì và không được gì?

`WeakReference<ClassLoader>` không giữ loader sống chỉ nhờ chính reference đó, nên thích hợp quan sát khả năng reclaim. Tuy nhiên `weak.get() != null` sau một lần thử không chứng minh leak; `weak.get() == null` chứng tỏ referent đã được clear, **không tự cung cấp log cho từng class unload**.

Tránh giữ `Class<?>`, instance, `Method` hoặc loader trong biến local khi muốn thử eligibility. Bài quan sát unloading phân biệt các tín hiệu này với sự kiện JVM.

## 10. Reload plugin là tạo defining loader mới, không phải đổi bytes trong loader cũ

Loader đã định nghĩa `plugin.Widget` thường trả chính `Class<?>` cũ khi gọi `loadClass("plugin.Widget")` lần nữa. Muốn dùng định nghĩa mới, tạo loader mới, chuyển lifecycle/traffic an toàn, rồi retire loader cũ.

Một instance của version cũ vẫn thuộc runtime type cũ; đóng URL loader không thay đổi type identity hay static state của class đang sống.

## 11. Lỗi thường gặp liên quan Class–instance–loader

`ClassCastException` giữa hai tên giống nhau thường chỉ hai defining loaders khác nhau. `LinkageError` dạng loading constraint violation liên quan chữ ký API bị liên kết với hai runtime types không tương thích. `IllegalAccessError` có thể phản ánh runtime package khác nhau dù source package trùng tên.

Để chẩn đoán, so sánh trực tiếp `Class<?>` và `getClassLoader()` ở hai phía. Những lỗi này liên quan **type/linkage/access**, không mặc nhiên chứng minh class unloading hay memory leak.

## 12. Công cụ và chiến lược chẩn đoán thực tế

### 12.1. Truy xuất defining loader từ instance và Class mirror

Một helper dùng trong ứng dụng:

```java
static void describe(Object instance) {
    Class<?> type = instance.getClass();
    System.out.println("type=" + type.getName());
    System.out.println("definingLoader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule());
}
```

Đây là snippet (cần đặt trong class và truyền instance phù hợp). Nếu class được tạo bởi custom loader với tên hiển thị không ổn định, log thêm ID loader do framework gán cho **phiên bản/plugin session**. `System.identityHashCode(loader)` chỉ hỗ trợ chẩn đoán, không phải ID toàn cục không va chạm.

### 12.2. Quan sát class loading/unloading

JDK/HotSpot có thể cung cấp:

```bash
java -Xlog:class+load=info,class+unload=info -cp app/classes com.example.Main
```

Lệnh dùng dấu cách/argument phù hợp cho Unix shell và Java launcher; thay app/classes và tên main cho dự án thật. Log khi JVM unload class **không** xuất hiện bắt buộc ngay sau close, vì unloading không đồng bộ với API close.

Một số lệnh attach/thống kê tham khảo:

```bash
jcmd <pid> VM.classloader_stats
jcmd <pid> GC.class_histogram
jcmd <pid> Thread.print
```

Đây là các ví dụ lệnh cần thay `<pid>`, phụ thuộc JDK/VM, quyền attach và chi phí vận hành. Classloader stats có thể giúp thấy số lượng class/loaders; thread dump giúp xem thread plugin còn chạy và TCCL/stack liên quan. Heap histogram giúp xem instance classes; không một lệnh nào tự chứng minh toàn bộ quy trình dispose đúng.

### 12.3. Phân tích GC roots và reference path

Khi nghi leak:

1. Xác định một loader cũ cụ thể đã lẽ ra phải rời lifecycle.
2. Kiểm tra những class mirror và instance do nó định nghĩa còn được giữ không.
3. Dò **path to GC root** của loader/instance qua heap dump/profiler.
4. Chú ý các static fields thuộc host, event bus, TCCL, ThreadLocal và cache của thư viện chung.
5. Kiểm tra thread còn sống đang thực thi class/plugin code.
6. Xác minh listener/route/metric/MBean/JDBC driver registrations đã được unregister.
7. Theo dõi xu hướng sau nhiều lần load–stop–dispose, thay vì kết luận dựa trên một snapshot.
8. Nếu loader unreachable mà class chưa unload, cân nhắc hoạt động GC/VM và điều kiện đánh giá, không vội gọi đó là bug của ClassLoader.

**Quan trọng:** một plugin có thể cleanup thread đúng nhưng vẫn leak do host cache `Class<?>`. Ngược lại, một cache không giữ class mirror vẫn có thể leak qua plugin instance. Cần xem toàn bộ đồ thị tham chiếu.

## 13. So sánh các trạng thái plugin với tình trạng memory

| Tình trạng | Instance | Class<?> | ClassLoader | Ý nghĩa |
| --- | --- | --- | --- | --- |
| Plugin ACTIVE | Thường reachable | Có định nghĩa runtime | Đang phục vụ | Bình thường |
| Plugin STOPPED nhưng registry chưa dọn | Có thể còn reachable | Có | Thường vẫn reachable | Có nguy cơ leak |
| Plugin DISPOSED, loader đã close | Có thể còn reference ở host/cache | Có thể còn | Có thể còn reachable | Close chưa chứng minh unload |
| Plugin không còn đường root strong reference | Có thể GC thu hồi nếu unreachable | Có thể được VM thu hồi cùng class metadata theo quy tắc | Có thể đủ điều kiện GC | **Có cơ hội** unload, không có deadline |
| GC đã thực sự thu hồi defining loader | Không thể còn instance cần runtime class đó trong heap reachable bình thường | Runtime class liên quan có thể unload theo cơ chế VM | Đã thu hồi | Kết quả quan sát sau GC, không phải do `close()` trực tiếp |

Đây là bảng khái niệm. Thời điểm, allocator, GC mode, class mirror layout và class unloading support phụ thuộc JVM; không áp dụng số liệu thời gian/bộ nhớ cụ thể khi chưa đo.

## 14. Những hiểu lầm phổ biến

**“Mỗi object giữ một bản Class<?> riêng.”** Sai. Instances của cùng runtime class thường trả cùng `Class<?>` mirror.

**“`instance.getClass().getClassLoader()` luôn là loader đã gọi `loadClass`.”** Sai. Nếu parent delegation đã trả class, defining loader có thể là parent/bootstrap.

**“Hai Class cùng tên do hai loader tạo là một kiểu.”** Sai. Runtime type identity gồm binary name và defining loader.

**“Đã gọi `URLClassLoader.close()`, toàn bộ instance do loader tạo phải không dùng được.”** Sai. Existing class/instance có thể tiếp tục dùng; chỉ những hoạt động cần tìm class/resource chưa có mới có thể thất bại.

**“Đặt biến local loader = null là chắc chắn unload.”** Sai. Class/instance/registry/thread có thể giữ loader, GC không có thời điểm bắt buộc.

**“Class<?> chỉ chứa tên nên cache bao nhiêu cũng không ảnh hưởng loader.”** Sai. Cache class mirrors là một dạng strong retention cần kiểm soát.

**“Có vòng reference là JVM không thu hồi được.”** Sai. Reachability từ GC roots mới là điểm quyết định của garbage collection thông thường.

**“System.gc() là cách đo unload chắc chắn.”** Sai. Là yêu cầu/gợi ý GC, không phải barrier đảm bảo class unloading.

**“Metaspace tăng thì chắc chỉ cần gọi close() thêm lần nữa.”** Sai. Cần tìm root paths giữ loader và xem lifecycle/GC thực tế; close không tự thu hồi metadata.

**“ServiceLoader.reload() thay toàn bộ class implementation.”** Sai. Nó chỉ ảnh hưởng cache provider của ServiceLoader instance, không thay defining loader.

## 15. Bài tập tự kiểm tra

**Câu 1.** Hai instance `a` và `b` được tạo từ cùng một runtime class. Phép `a.getClass() == b.getClass()` thường cho kết quả gì?

**Trả lời:** `true`. Chúng dùng cùng class mirror của một định nghĩa runtime.

**Câu 2.** Hai `URLClassLoader` độc lập cùng đọc `plugin.Widget.class`. Tại sao `typeA == typeB` là `false`?

**Trả lời:** Vì defining loaders khác nhau, nên hai runtime classes khác nhau dù tên và bytecode giống hệt.

**Câu 3.** Sau `loader.close()`, `instance.message()` đã được nạp vẫn có thể chạy không?

**Trả lời:** Có thể, nếu mã và những dependencies cần cho lời gọi đã khả dụng. Đóng loader không xóa instance hoặc class đã định nghĩa; không nên suy luận mọi lời gọi đều thành công sau close nếu cần nạp lớp mới.

**Câu 4.** Host giữ `Map<String, Class<?>>` cache type của plugin đã dừng. Đây có thể là nguyên nhân classloader leak không?

**Trả lời:** Có. Reference mạnh tới `Class<?>` có thể giữ định nghĩa class/loader và metadata của plugin sống.

**Câu 5.** WeakReference tới loader chưa được clear ngay sau khi gán local loader = null. Có chứng minh leak không?

**Trả lời:** Không. JVM có thể chưa GC, hoặc còn references khác; phải quan sát GC roots, lifecycle và nhiều chu kỳ.

**Câu 6.** Vì sao một vòng `loader → static object → plugin instance → runtime class` không tự động là leak?

**Trả lời:** Nếu toàn bộ nhóm không còn reachable từ GC roots, garbage collector có thể thu hồi chu trình. Chỉ khi có đường từ root giữ nhóm thì mới là retained data thực sự.

**Câu 7.** Tại sao một thread pool host có thể giữ loader dù manager đã remove plugin khỏi registry?

**Trả lời:** Worker thread có thể giữ TCCL/plugin-defined ThreadLocal value hoặc runnable/callback đang chờ trong queue; cần cleanup trên đúng thread và đóng task/registration.

**Câu 8.** Muốn reload `plugin.Widget` thành bytecode mới, nên tiếp tục gọi `loadClass` trên loader cũ không?

**Trả lời:** Không để thay một định nghĩa đã nạp. Thường cần defining loader mới, ranh giới API chung và lifecycle handoff rõ ràng.

**Câu 9.** Nếu `String.class.getClassLoader() == null`, có phải do `String` không có runtime class không?

**Trả lời:** Không. `null` đại diện bootstrap loader trong API; `String` vẫn là runtime class bình thường của Java.

**Câu 10.** Điều kiện cần của class unloading trong JVMS là gì?

**Trả lời:** Defining class loader của class ấy có thể được garbage collector thu hồi. Điều này không bảo đảm VM đã unload class ngay lúc ứng dụng gọi close/null.

## 16. Checklist thiết kế plugin tránh giữ class/loader ngoài ý muốn

Giữ interface API dùng chung ở loader ổn định; tách implementation theo generation và bảo đảm mỗi resource có owner cleanup. Gỡ plugin instances, callbacks, caches, TCCL/`ThreadLocal`, queued tasks và open resources khi retire.

Đọc `instance.getClass()` / `Class.getClassLoader()` để xác định đúng runtime class và defining loader. Muốn kiểm chứng reclaim, quan sát retaining graph và class unload events theo giới hạn JVM.

## 17. Kết luận và nguồn chính thức

**ClassLoader định nghĩa runtime class; `Class<?>` đại diện runtime class ấy; instance là đối tượng thuộc runtime class.** Chúng được liên kết về danh tính và vòng đời nhưng không bị hủy đồng loạt khi gọi `URLClassLoader.close()`.

Điều kiện để JVM có cơ hội unload class của plugin là **defining loader đủ điều kiện được GC thu hồi**. Class mirrors, instances, threads, TCCL, callbacks, reflection caches và các registry bên ngoài có thể làm loader tiếp tục reachable. Thiết kế plugin đúng phải **dừng hoạt động, hủy đăng ký, đóng resource và bỏ strong references** trước khi kỳ vọng GC/unloading xảy ra; ngay cả khi đó, không có deadline unload được API bảo đảm.

Tài liệu tham khảo:

- JVMS 17 §5.3, *Creation of Classes and Interfaces* (runtime class identity): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- JVMS 17 §5.5, *Initialization*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- JVMS 17 §5.5/§5.6/§5.7 (including *Unloading*): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- Java SE 17 `Class` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 `ClassLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `URLClassLoader` API, `close()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17 `WeakReference` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-unloading-and-memory-005">Metaspace trong HotSpot và giới hạn của mô hình method area</a>

<details>
<summary>Xem chi tiết</summary>

# Metaspace trong HotSpot và giới hạn của mô hình method area

Khi học Java ClassLoader, **method area** và **Metaspace** thường bị dùng như hai tên gọi cho cùng một thứ. Cách nói đó chỉ đúng ở mức diễn giải đơn giản và dễ gây hiểu lầm nghiêm trọng khi cần chẩn đoán **`OutOfMemoryError: Metaspace`**, class-loader leak, native memory hoặc khả năng unload class.

**Phân biệt nền tảng:**

- **Method area** là một **vùng dữ liệu runtime mang tính trừu tượng được Java Virtual Machine Specification (JVMS) yêu cầu**. Nó chứa các cấu trúc theo class như runtime constant pool, thông tin field/method và code của methods/constructors ở mức mô hình. Đặc tả **không bắt buộc** JVM phải triển khai vùng này ở một địa chỉ, một allocator hoặc một heap cụ thể.
- **Metaspace** là **cơ chế HotSpot sử dụng để quản lý một phần quan trọng của class metadata** trong các JDK hiện đại (JDK 8+). HotSpot thường cấp phát metadata này bằng **native memory**, khác với Java object heap chịu giới hạn `-Xmx`.
- **PermGen** là **chi tiết triển khai lịch sử của HotSpot** trước JDK 8. Metaspace thay thế mô hình class-metadata storage cũ của PermGen trong HotSpot, **không có nghĩa cả method area của JVMS đã “biến thành một byte array Metaspace duy nhất”**.
- **Class unloading** phụ thuộc vào runtime class và defining loader theo quy tắc JVM/GC; gọi `URLClassLoader.close()` không trực tiếp xóa class metadata. Metaspace có thể tái sử dụng hoặc trả về hệ điều hành một phần bộ nhớ sau khi JVM thu hồi các class-loader generations phù hợp.
- **Các số liệu memory là những góc nhìn khác nhau**: `used`, `committed`, `reserved`, heap usage, NMT `Class` category, OS RSS và compressed class space **không được cộng trừ hoặc xem là cùng một chỉ số một cách tùy tiện**.

Bài học dựa trên **Java 17+**, phân biệt rõ những yêu cầu **của đặc tả JVM** và những điều **chỉ đúng cho HotSpot/OpenJDK**. Ví dụ cuối bài dùng `java.lang.management`, JAR và hai `URLClassLoader` để quan sát các giới hạn thực tế.

## 1. Phân biệt “đặc tả” và “triển khai” trước khi nói về bộ nhớ

Java là ngôn ngữ và nền tảng có nhiều JVM implementations. Khi đọc **JVMS `2.5.4`**, ta đang đọc mô hình logic mà JVM tương thích phải thể hiện. Khi đọc **HotSpot Metaspace**, ta đang đọc cách một JVM cụ thể triển khai một phần của mô hình ấy.

### Method area theo JVMS

JVMS Java SE 17, `2.5.4` quy định một **method area chia sẻ giữa các JVM threads**, dùng để lưu những cấu trúc theo class, bao gồm:

- Runtime constant pool.
- Dữ liệu của fields và methods.
- Code của methods/constructors, kể cả các phương thức đặc biệt phục vụ initialization.
- Những cấu trúc mà implementation cần để quản lý runtime types.

**Đặc tả nói method area “logically part of the heap”**. Cụm này không có nghĩa HotSpot phải đặt toàn bộ metadata vào **Java heap do `-Xmx` giới hạn**. JVMS cũng nói rõ đặc tả **không ấn định vị trí bộ nhớ**, không yêu cầu một chính sách cụ thể về garbage collection/compaction trong method area, và không bắt mọi implementation lưu compiled code cùng một cách.

Vì vậy hai phát biểu sau **không mâu thuẫn**:

1. **Ở cấp JVMS:** method area được xem là một phần **logic** của heap trong mô hình máy ảo.
2. **Ở cấp HotSpot:** class metadata của Metaspace phần lớn dùng **native/non-heap memory** và được các bộ quản lý/công cụ báo cáo ngoài Java object heap.

Đừng dùng từ “heap” ở hai tầng trừu tượng khác nhau rồi kết luận tài liệu Java tự mâu thuẫn.

### Metaspace là gì?

Metaspace là subsystem quản lý native allocations chứa nhiều cấu trúc liên quan metadata cho **classes được nạp** trong HotSpot. Khi một class được định nghĩa, HotSpot cần metadata để hiểu cấu trúc của runtime type, methods, fields, constant-pool information và những thành phần triển khai khác.

`Metaspace` không phải một Java collection có các object `Class<?>` trực tiếp “nằm trong” theo nghĩa thông thường. Trên HotSpot, **`java.lang.Class` mirrors là các Java objects trên Java heap**, được liên kết với metadata nội bộ tương ứng. Một instance như `new Widget()` cũng ở Java heap; metadata cho runtime class `Widget` có vòng đời/cấp phát khác.

## 2. Sơ đồ dễ nhớ: nơi nào chứa gì?

```text
                   MỘT JVM PROCESS (HotSpot)
  +------------------------------------------------------+
  |                                                      |
  |  Java Object Heap (-Xmx, GC-managed)                |
  |  +-----------------------------------------------+   |
  |  | new Widget() instances                        |   |
  |  | String / Array / Map / Class<?> mirrors        |   |
  |  | objects được static fields tham chiếu tới     |   |
  |  +-----------------------------------------------+   |
  |                                                      |
  |  Native / VM-managed memory                         |
  |  +-----------------------------------------------+   |
  |  | Metaspace: nhiều cấu trúc class metadata      |   |
  |  |  + Non-class metadata allocations             |   |
  |  |  + (khi áp dụng) compressed class space       |   |
  |  +-----------------------------------------------+   |
  |  | Code Cache: machine code/JIT-compiled code    |   |
  |  | Thread stacks, GC data, symbols, VM internals  |   |
  |  +-----------------------------------------------+   |
  |                                                      |
  |  Filesystem / mapped artifacts                      |
  |  +-----------------------------------------------+   |
  |  | class files, JARs, JDK image, CDS archive      |   |
  |  +-----------------------------------------------+   |
  +------------------------------------------------------+
```

Đây là **sơ đồ khái niệm**, không phải memory map chính xác và cũng không diễn tả tất cả cấu trúc của mọi JDK. Một số vùng là virtual memory, một số bytes nằm trong mapped files, một số được dùng chung giữa processes, và cách phân loại NMT phụ thuộc HotSpot/JDK release.

### Bảng phân biệt nhanh

| Thành phần | Thuộc mô hình/triển khai nào? | Thường nằm ở đâu trong HotSpot hiện đại? |
|---|---|---|
| **Method area** | **JVMS logical region** | Không có cam kết vật lý duy nhất; các thành phần được HotSpot hiện thực qua nhiều cấu trúc |
| **Metaspace** | **HotSpot implementation** | Native memory cho class metadata |
| **Java heap** | Runtime area của JVM, HotSpot-managed | Java objects và arrays được garbage collector quản lý |
| **`Class<?>` object** | Java reflection API mirror | Java heap, tham chiếu đến runtime class metadata |
| **Instance `new Foo()`** | Java object | Java heap |
| **Runtime constant pool** | JVMS per-class logical structure | Metadata/VM structures tương ứng, không đồng nghĩa `String` objects nằm hết trong Metaspace |
| **`String` interned objects** | Java objects | Trên các HotSpot hiện đại, `String` instances ở Java heap |
| **JIT compiled machine code** | JIT implementation | HotSpot Code Cache và các vùng VM tương ứng, không nói “toàn bộ trong Metaspace” |
| **Bytecode .class trong JAR** | Artifact của ứng dụng | File/JAR/CDS image; không đồng nghĩa mỗi file phải được giữ nguyên bản copy trong Metaspace |
| **Native thread stack** | OS/JVM execution resource | Ngoài Java heap; không phải Metaspace |

Một method có thể có **bytecode metadata** gắn với class và **machine code đã JIT compile** ở code cache. Đừng gộp bytecode, compiled code và method metadata thành một con số “kích thước method area” mà Java API buộc phải công bố.

## 3. Từ PermGen tới Metaspace

### PermGen của HotSpot lịch sử

Ở các phiên bản HotSpot trước JDK 8, class metadata và một số thành phần liên quan được quản lý thông qua cơ chế **Permanent Generation (PermGen)**. Người vận hành thường gặp:

```text
java.lang.OutOfMemoryError: PermGen space
```

và tùy chọn lịch sử `-XX:MaxPermSize`.

### JDK 8 trở đi

HotSpot chuyển class metadata sang **Metaspace**, sử dụng native memory và các thuật toán cấp phát khác. Vì thế lỗi thiếu không gian metadata thường biểu hiện:

```text
java.lang.OutOfMemoryError: Metaspace
```

**Tuy nhiên, không nên nói “PermGen và Metaspace là hai JVM memory areas được JVMS quy định”.** Cả hai là implementation details của HotSpot ở những thời kỳ khác nhau; JVMS vẫn mô tả **method area** trừu tượng.

Tùy chọn `-XX:MaxPermSize` không phải cách giới hạn Metaspace trên JDK 17. Muốn đặt ngưỡng class metadata của HotSpot dùng **`-XX:MaxMetaspaceSize`** với ý nghĩa và giới hạn riêng.

### JDK 16+: Elastic Metaspace

**JEP 387 (Elastic Metaspace)** được đưa vào **JDK 16** nhằm giảm footprint metadata và giúp HotSpot **trả lại bộ nhớ không dùng về hệ điều hành** kịp thời hơn sau class unloading. Nó dùng cơ chế quản lý virtual-memory chunks/commit granules và các chính sách reclamation.

Bài học quan trọng:

- “Metaspace có khả năng co lại” **không đồng nghĩa** JVM luôn lập tức giảm RSS sau khi đóng một plugin loader.
- Trả bộ nhớ về OS phụ thuộc class unloading, allocator state, kích cỡ/granularity, chính sách reclaim và môi trường đo.
- Bộ nhớ được **free/reuse trong allocator** khác với **uncommit** native pages; và uncommit khác với OS RSS lập tức giảm đúng bằng một con số.
- Một phần metadata còn sống vẫn cần memory giữ lại, kể cả khi đã dọn nhiều class-loader generations.

## 4. ClassLoader liên quan thế nào tới Metaspace?

Mỗi **runtime class** được xác định bởi:

```text
(binary class name, defining class loader)
```

Ví dụ hai loader cùng định nghĩa `example.Widget`:

```text
URLClassLoader A
   └── ("example.Widget", A) -> class metadata của A

URLClassLoader B
   └── ("example.Widget", B) -> class metadata của B
```

Hai runtime classes khác nhau, nên JVM có thể cần **metadata riêng** cho từng class. Không có quy tắc “cùng tên + cùng bytecode thì JVM chỉ tốn một bản class metadata” trong mọi trường hợp.

### Vì sao plugin reload dễ làm Metaspace tăng?

Một plugin manager có thể tạo loader mới ở mỗi lần cập nhật:

```text
V1: Loader-1 -> plugin classes v1 -> metadata generation 1
V2: Loader-2 -> plugin classes v2 -> metadata generation 2
V3: Loader-3 -> plugin classes v3 -> metadata generation 3
...
```

Nếu loader generations cũ **vẫn còn reachable** qua registrations, threads hoặc caches, JVM chưa thể thu hồi chúng theo class-unloading rules; lượng class metadata có thể tiếp tục tăng.

Các **nguồn giữ loader** phổ biến:

- Plugin instance bị host giữ trong `Map`, registry hoặc listener list.
- `Class<?>`, `Method`, `Constructor`, `MethodHandle`, generated proxy classes và reflection caches bị lưu giữ.
- Thread vẫn chạy code plugin hoặc **thread context class loader (TCCL)** trỏ tới plugin loader.
- `ThreadLocal` value ở executor thread giữ plugin objects.
- ServiceLoader/provider cache, JDBC driver, MBeans, logging handlers hoặc scheduled tasks chưa unregister.
- Loader graph có strong references từ host hoặc custom loader khác.
- Active call frames, native/JNI global references hay resource lifecycle không được dọn.

**Không phải mọi tăng trưởng Metaspace đều là leak.** Một JVM đang warm-up có thể nạp nhiều classes cần dùng và đạt plateau bình thường. Cần quan sát **số loaded classes, loader generations, used/committed metadata và retaining paths theo thời gian**.

## 5. Class unloading gắn với defining loader

**JLS 12.7** cho phép JVM unload một class/interface khi **defining class loader đủ điều kiện để GC thu hồi**; thời điểm unload do JVM quyết định. Nếu host còn giữ một instance, `Class<?>`, reflection cache hoặc TCCL của plugin, generation cũ có thể vẫn reachable và Metaspace không được giải phóng cho generation đó.

Đối với ứng dụng reload lặp lại, kiểm tra retaining paths và class-loader generations **trước khi tăng `MaxMetaspaceSize`**; cấu hình bộ nhớ chỉ giảm áp lực tạm thời nếu nguyên nhân là retention.

## 6. Metaspace và Java heap có những OOME riêng

| Lỗi/triệu chứng | Nguyên nhân thường liên quan | Cách điều tra |
|---|---|---|
| `OutOfMemoryError: Java heap space` | Heap objects/arrays, retention, allocation pressure | Heap usage, allocation profiler, heap dump, `-Xmx` |
| `OutOfMemoryError: Metaspace` | HotSpot không thể cấp phát thêm metadata do giới hạn hoặc native memory pressure | Loader/class counts, metaspace pools, `MaxMetaspaceSize`, NMT |
| `OutOfMemoryError: Compressed class space` | Vùng dành cho compressed class pointers/class metadata chạm giới hạn phù hợp | `CompressedClassSpaceSize`, NMT `Class` category, số/loại classes |
| `OutOfMemoryError: unable to create native thread` | Native memory, thread/process limits, OS resources | Thread count/stacks, OS/container limits |
| JVM/process bị OS kill (OOM killer) | Tổng resident/native + heap + stack + JIT/CDS/... vượt hạn ngạch | OS/container RSS, `memory.events`, NMT và GC logs |
| Metaspace committed cao nhưng used thấp | Allocator overhead/free chunks, fragmentation/reclaim policy, sampling | Theo dõi used vs committed/reserved và class unloading theo thời gian |

**Không đánh đồng `OutOfMemoryError: Metaspace` với Java heap đã đầy.** Nâng `-Xmx` không mặc nhiên chữa lỗi Metaspace; tăng heap quá lớn trong container memory-limited còn có thể giảm native headroom.

Ngược lại, đặt `-XX:MaxMetaspaceSize` cao hơn **không chữa classloader leak**; nó có thể trì hoãn triệu chứng trong khi loader generations cũ vẫn còn reachable.

## 7. Ba từ then chốt: used, committed và reserved

Khi quan sát Metaspace/NMT, hãy tách ba mức:

### Used

`used`: bộ nhớ mà allocator/pool báo đã dùng cho metadata hoặc phần sử dụng theo cách thống kê của công cụ. Không phải số lượng objects trên heap, cũng không tự tương đương với OS resident bytes.

### Committed

`committed`: lượng virtual-memory backing đã được JVM **commit** cho vùng liên quan và có thể dùng theo cơ chế quản lý đó. Có thể lớn hơn `used` vì allocations được tổ chức theo chunks/granules, có slack/fragmentation hoặc memory sẵn cho lần cấp phát tiếp theo.

### Reserved

`reserved`: **không gian địa chỉ ảo** JVM dành trước để có thể commit khi cần. `reserved` lớn **không có nghĩa** hệ điều hành đã cấp RAM vật lý cho tất cả bytes ấy.

```text
Virtual address space reserved (có thể rất lớn)
+------------------------------------------------------+
| Committed virtual memory backing                     |
| +----------------------------------+                  |
| | Used metadata | free/slack/      |                  |
| |               | allocator space  |                  |
| +----------------------------------+                  |
|              vùng chưa committed                      |
+------------------------------------------------------+
```

Các chỉ số thay đổi động. Một báo cáo NMT `Class reserved=... committed=...` không phải phép đo “RAM còn trống” hoặc “Java heap đã dùng”. Đặc biệt **Compressed Class Space có thể reserve một vùng địa chỉ ảo lớn** nhưng committed ban đầu nhỏ hơn đáng kể.

### OS RSS có bằng Metaspace committed không?

Không nhất thiết. **Resident Set Size (RSS)** là góc nhìn pages cư trú của toàn process theo OS; nó gồm nhiều subsystem ngoài Metaspace. Shared/mapped pages, demand paging, accounting và memory reclamation làm việc đối chiếu RSS ↔ committed không phải phép cộng đơn giản.

Khi tối ưu theo RAM của container, hãy phối hợp **heap, GC native allocations, metaspace, code cache, thread stacks, direct buffers, libraries/mapped regions và OS reports**. NMT giúp nhìn HotSpot internals nhưng không thống kê toàn bộ native allocations của các thư viện bên thứ ba.

## 8. Giới hạn cấu hình trong HotSpot Java 17+

### `-XX:MaxMetaspaceSize`

Đặt ngưỡng tối đa cho native memory HotSpot dành cho class metadata theo semantics của JVM, ví dụ:

```bash
java -XX:MaxMetaspaceSize=256m -jar app.jar
```

Đây là **lệnh minh họa** cần artifact `app.jar` phù hợp. Nếu các metadata allocations cần vượt giới hạn, JVM có thể thử GC/class unloading rồi cuối cùng ném `OutOfMemoryError: Metaspace`.

Không nên chọn `256m` tùy tiện làm mức production. Phải đo workload có số classes, plugin generations, class metadata, compressed class space và safety headroom phù hợp.

### `-XX:MetaspaceSize`

Đây chủ yếu là **ngưỡng liên quan metadata allocation và kích hoạt một số GC hoạt động**, không phải “Metaspace bắt đầu được pre-allocate chính xác X MB” cũng không phải giới hạn tối đa như `MaxMetaspaceSize`.

Một cấu hình minh họa:

```bash
java -XX:MetaspaceSize=64m -XX:MaxMetaspaceSize=256m -jar app.jar
```

Đừng ngầm hiểu JVM ngay lập tức chiếm 64 MB physical RAM chỉ vì `MetaspaceSize=64m`.

### `-XX:CompressedClassSpaceSize`

Khi HotSpot dùng **compressed class pointers**, metadata có thể chia thành non-class metadata và class metadata trong **compressed class space**. Tùy chọn:

```bash
java -XX:CompressedClassSpaceSize=256m -jar app.jar
```

có thể điều chỉnh kích cỡ reservation/giới hạn tương ứng trong những cấu hình hỗ trợ. Không sử dụng tùy chọn này nếu chưa xác nhận JVM/flags và vấn đề thực sự nằm ở compressed class space.

Một vùng reserved lớn ở NMT `Class` **không phải bằng chứng rò rỉ RAM vật lý**, vì phần lớn virtual space có thể chưa committed.

### `-XX:MetaspaceReclaimPolicy`

HotSpot sau **JEP 387** hỗ trợ các lựa chọn như:

```text
balanced
aggressive
none
```

Với tùy chọn:

```bash
java -XX:MetaspaceReclaimPolicy=balanced -jar app.jar
```

`balanced` là chính sách mặc định thường dùng trong các HotSpot versions áp dụng Elastic Metaspace; `aggressive` có thể tăng độ tích cực khi uncommit/reclaim, đổi lại một số chi phí bookkeeping; `none` giảm hành động trả bộ nhớ theo chính sách đó.

**Không xem `aggressive` là “luôn ít RAM nhất và nhanh nhất”.** Workload có class loading/unloading thường xuyên cần đo throughput, pause behavior và footprint, không tối ưu một con số tại một thời điểm.

### `-Xmx` là chuyện khác

`-Xmx` giới hạn Java heap. Đặt:

```bash
java -Xmx512m -XX:MaxMetaspaceSize=128m -jar app.jar
```

không có nghĩa JVM process bị giới hạn ở 640 MB. Vẫn còn thread stacks, code cache, GC native metadata, direct buffers, JIT, mapped libraries, CDS, JVM internals và OS-level overhead. Nếu chạy trong container, cần dự trù headroom thực thay vì cộng hai flags thành memory limit.

## 9. Demo Java 17+: quan sát Metaspace và hai loader cùng tên class

Demo dưới đây là một **chương trình runnable thực tế**, dùng `java.lang.management.MemoryPoolMXBean` để quan sát **HotSpot pool tên `Metaspace`**, rồi tạo **hai `URLClassLoader`** nạp một JAR có class `example.Widget`.

**Không đưa ra số used/committed cố định:** memory snapshots thay đổi theo JDK, GC, CDS, flags và thời điểm quan sát. Thay vào đó, ví dụ xác nhận những invariant và ngữ nghĩa ổn định đã được kiểm thử.

### 9.1. Cấu trúc thư mục

```text
metaspace-demo/
├── src/
│   ├── plugin/
│   │   └── example/
│   │       └── Widget.java
│   └── host/
│       └── host/
│           └── MetaspaceDemo.java
├── out/
│   ├── plugin/
│   └── host/
└── plugin.jar
```

### 9.2. Class plugin

Tệp **`src/plugin/example/Widget.java`**:

```java
package example;

public final class Widget {
    private static int calls;

    public String hello() {
        calls++;
        return "hello " + calls;
    }
}
```

`calls` là static state theo **runtime class**, không phải biến toàn JVM theo string tên class.

### 9.3. Chương trình host

Tệp **`src/host/host/MetaspaceDemo.java`**:

```java
package host;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class MetaspaceDemo {
    public static void main(String[] args) throws Exception {
        MemoryPoolMXBean metaspace = ManagementFactory
                .getMemoryPoolMXBeans().stream()
                .filter(p -> p.getName().equals("Metaspace"))
                .findFirst().orElseThrow();

        System.out.println("Pool name: " + metaspace.getName());
        System.out.println("Pool is non-heap: "
                + (metaspace.getType() == MemoryType.NON_HEAP));

        MemoryUsage sample = metaspace.getUsage();
        System.out.println("Metadata snapshot valid: "
                + (sample != null && sample.getUsed() >= 0
                   && sample.getCommitted() >= sample.getUsed()));

        URL code = Path.of(args[0]).toUri().toURL();
        ClassLoader parent = MetaspaceDemo.class.getClassLoader();

        try (URLClassLoader a =
                     new URLClassLoader(new URL[] {code}, parent);
             URLClassLoader b =
                     new URLClassLoader(new URL[] {code}, parent)) {

            Class<?> ca = a.loadClass("example.Widget");
            Class<?> cb = b.loadClass("example.Widget");

            System.out.println("Same binary name: "
                    + ca.getName().equals(cb.getName()));
            System.out.println("Different runtime classes: "
                    + (ca != cb));
            System.out.println("Each child defines its class: "
                    + (ca.getClassLoader() == a
                       && cb.getClassLoader() == b));

            Object objectA =
                    ca.getDeclaredConstructor().newInstance();
            System.out.println("Instance created: "
                    + (objectA != null));

            System.out.println("Method: "
                    + ca.getMethod("hello").invoke(objectA));

            a.close();

            System.out.println("Method after close: "
                    + ca.getMethod("hello").invoke(objectA));

            MemoryUsage after = metaspace.getUsage();
            System.out.println("After snapshot valid: "
                    + (after != null && after.getUsed() >= 0));
        }
    }
}
```

### 9.4. Biên dịch và chạy — Bash

Từ thư mục `metaspace-demo` trên Linux/macOS/Git Bash:

```bash
mkdir -p out/plugin out/host

javac --release 17 -d out/plugin \
  src/plugin/example/Widget.java

javac --release 17 -d out/host \
  src/host/host/MetaspaceDemo.java

jar --create --file plugin.jar -C out/plugin .

# Chỉ host classes nằm trên classpath hệ thống.
java -cp out/host host.MetaspaceDemo plugin.jar

# Thử giới hạn Metaspace và bật NMT để thực hành.
java -XX:MaxMetaspaceSize=64m \
  -XX:NativeMemoryTracking=summary \
  -cp out/host host.MetaspaceDemo plugin.jar
```

**Lưu ý:** Đường `plugin.jar` đi vào **program argument**, không vào `-cp`. Nếu đưa plugin JAR vào parent classpath, hai `URLClassLoader` parent-first có thể trả cùng class từ parent thay vì tự định nghĩa.

### 9.5. Lệnh tương đương PowerShell

```powershell
New-Item -ItemType Directory -Force -Path out/plugin, out/host | Out-Null

javac --release 17 -d out/plugin src/plugin/example/Widget.java
javac --release 17 -d out/host src/host/host/MetaspaceDemo.java

jar --create --file plugin.jar -C out/plugin .

java -cp out/host host.MetaspaceDemo plugin.jar

java -XX:MaxMetaspaceSize=64m -XX:NativeMemoryTracking=summary -cp out/host host.MetaspaceDemo plugin.jar
```

### 9.6. Kết quả đã biên dịch và kiểm thử

Đã biên dịch với **`javac --release 17`** và chạy trên **OpenJDK 24.0.2**. Cả bản baseline lẫn bản có `-XX:MaxMetaspaceSize=64m` và `-XX:NativeMemoryTracking=summary` in chính xác:

```text
Pool name: Metaspace
Pool is non-heap: true
Metadata snapshot valid: true
Same binary name: true
Different runtime classes: true
Each child defines its class: true
Instance created: true
Method: hello 1
Method after close: hello 2
After snapshot valid: true
```

**Giải thích từng nhóm kết quả:**

1. **Pool name: Metaspace** — ứng dụng đang chạy trên HotSpot/OpenJDK có `MemoryPoolMXBean` pool tên `Metaspace`. Không nên giả định tên pool này tồn tại trên mọi JVM implementation khác.
2. **Pool is non-heap: true** — API quản lý bộ nhớ của HotSpot phân loại pool này là **NON_HEAP**. Điều đó không bác bỏ phát biểu “method area logically part of the heap” trong JVMS vì hai câu thuộc **hai cấp độ trừu tượng**.
3. **Metadata snapshot valid: true** — có thể lấy một sample hợp lệ và `committed >= used`. Số bytes tuyệt đối không được kiểm thử cố định.
4. **Same binary name: true** và **Different runtime classes: true** — hai `Class<?>` có cùng tên nhưng khác defining loaders.
5. **Each child defines its class: true** — `ca.getClassLoader() == a` và `cb.getClassLoader() == b`, xác nhận isolation.
6. **Method: hello 1** — plugin object đã tạo và method hoạt động.
7. **Method after close: hello 2** — class đã nạp tiếp tục hoạt động dù loader A gọi `close()`; điều này **không phải class unloading**.
8. **After snapshot valid: true** — bean vẫn cho snapshot metadata; không khẳng định size đã giảm hoặc loader cũ đã collectible.

**Giới hạn của demo:** Ứng dụng vẫn giữ `ca`, `cb`, `objectA` trong scope, nên **không** được kỳ vọng JVM unload/reclaim các classes ngay ở đây. `URLClassLoader.close()` xử lý tài nguyên loader, không phải trình điều khiển garbage collector.

## 10. Đọc số liệu MemoryPoolMXBean đúng cách

Từ `java.lang.management`:

```java
MemoryPoolMXBean pool = ManagementFactory
        .getMemoryPoolMXBeans().stream()
        .filter(p -> p.getName().equals("Metaspace"))
        .findFirst().orElseThrow();

MemoryUsage usage = pool.getUsage();

if (usage != null) {
    System.out.println("used=" + usage.getUsed());
    System.out.println("committed=" + usage.getCommitted());
    System.out.println("max=" + usage.getMax());
}
```

Đây là **snippet minh họa bên trong method** với imports `java.lang.management.*`. Nó sẽ in **giá trị động**, không nên dùng một con số stdout cố định làm đáp án.

**Ý nghĩa:**

- `getUsed()`: lượng memory mà pool thống kê đang sử dụng.
- `getCommitted()`: lượng memory backing được commit theo Java Management API cho pool.
- `getMax()`: có thể là **`-1`** nếu không có maximum xác định cho pool theo implementation/configuration. **Không** tự thay `-1` bằng tổng RAM máy.
- Một measurement có thể thay đổi giữa các lần gọi; nếu cần tính delta phải chọn snapshot boundaries/thời điểm rõ ràng.

Nếu chạy trên non-HotSpot JVM, pool có thể có tên khác hoặc không cung cấp cách phân loại tương tự. Nếu logic ứng dụng phụ thuộc memory-pool naming, hãy coi đó là **monitoring integration theo JVM vendor**, không phải JVM portability contract.

### “Used bytes tăng sau khi load class” có chứng minh gì?

Chỉ ra có thay đổi ở snapshot; chưa chứng minh toàn bộ delta do đúng một class. Ngay lúc đó có thể:

- JDK lazily load management/reflection classes.
- Reflection tạo metadata hoặc cache.
- Loader và ZIP/JAR handling tạo nhiều allocations.
- GC/class unloading xảy ra xen kẽ.
- JIT compilation/caches/thread scheduling ảnh hưởng hành vi đo.

Để kết luận về nguyên nhân, đo nhiều lần, dùng JFR/profiler/NMT/classloader stats và phân biệt chi phí ổn định với warmup. Không assert “mỗi class = X bytes Metaspace”.

## 11. Native Memory Tracking (NMT): quan sát toàn VM theo categories

**Native Memory Tracking** là tính năng HotSpot giúp theo dõi những nhóm native allocations của **HotSpot VM**. Trên Java 17, bật lúc **khởi động process**:

```bash
java -XX:NativeMemoryTracking=summary -jar app.jar
```

Hoặc cần phân tích chi tiết hơn:

```bash
java -XX:NativeMemoryTracking=detail -jar app.jar
```

**Không thể bật NMT từ trạng thái off thành on cho process đang chạy chỉ bằng `jcmd`**; thiết kế công cụ cần bật từ startup nếu dự kiến phải điều tra. `detail` có thể tăng overhead và tạo lượng thông tin lớn.

### 11.1. Sử dụng jcmd

Sau khi ứng dụng đang chạy có PID, ví dụ `12345`:

```bash
jcmd 12345 VM.native_memory summary
jcmd 12345 VM.native_memory summary scale=MB
```

Muốn so sánh snapshots cùng process:

```bash
jcmd 12345 VM.native_memory baseline
# Chạy workload: khởi tạo plugins, reload một nhóm.
jcmd 12345 VM.native_memory summary.diff scale=MB
```

Dấu `#` là comment Bash. Trong PowerShell, cũng dùng `#` cho comment; `jcmd` syntax tương tự.

**Đừng ghi nguyên con số PID `12345` vào runbook như giá trị cố định.** Tìm PID của Java process thực tế bằng `jcmd -l` hoặc công cụ OS phù hợp, rồi thay vào lệnh.

### 11.2. NMT báo gì?

Tùy JDK, output tóm tắt có thể có:

```text
Total: reserved=..., committed=...
  Java Heap       (reserved=..., committed=...)
  Class           (reserved=..., committed=...)
  Thread          (reserved=..., committed=...)
  Code            (reserved=..., committed=...)
  GC              (reserved=..., committed=...)
  Internal        (reserved=..., committed=...)
  ...
```

Đây là **minh họa bố cục**, không phải log chạy cố định; tên categories và cách chi tiết hóa có thể thay đổi giữa HotSpot releases.

Với `Class`, một phần số liệu có thể mô tả metadata/non-class memory, compressed class space, số classes hoặc loader data. **Không kết luận rằng `Class reserved` là toàn bộ RSS**, và **không cộng số `reserved` vào số `committed`** như hai khoản RAM khác nhau.

### 11.3. Giới hạn quan trọng của NMT

NMT **không theo dõi tất cả allocations của bên thứ ba/native libraries** và không nhất thiết đo đầy đủ mọi mapped/CDS memory theo cách người đọc mong muốn. NMT bật theo subsystem có overhead vận hành; nó là công cụ chẩn đoán HotSpot, không thay cho OS metrics.

Do đó:

- NMT cho thấy **VM internal native allocations** tương ứng category nào tăng.
- Heap dump cho thấy **retaining paths của Java objects**.
- JVM classloader stats cho thấy **loader/class populations**.
- OS/container metrics cho thấy **RSS/cgroup usage** và áp lực process thực.
- Kết hợp các nguồn mới chẩn đoán hợp lý.

## 12. VM.classloader_stats và class-unload logging

Trên HotSpot hỗ trợ diagnostic command:

```bash
jcmd 12345 VM.classloader_stats
```

Lệnh này giúp quan sát class loaders và class metadata/class counts theo khả năng JVM. **Không có nghĩa** cứ số loader lớn là leak: application servers và plugin frameworks có thể hợp lệ khi cần nhiều loader.

Để quan sát khi JVM thực hiện class unloading:

```bash
java '-Xlog:class+load=info,class+unload=info' -jar app.jar
```

PowerShell:

```powershell
java "-Xlog:class+load=info,class+unload=info" -jar app.jar
```

Có log `class+unload` khi VM unload một số classes **không chứng minh tất cả plugin resources được cleanup**; ngược lại **không có log unload trong một test ngắn** không chứng minh leak. GC không cam kết unload xảy ra ngay sau `close()` hoặc `System.gc()`.

`java -Xlog:help` liệt kê tags/mức logging được runtime hỗ trợ. Dùng công cụ theo JDK/vendor tương ứng.

## 13. Điều tra một classloader leak làm Metaspace tăng

Giả sử một server cập nhật plugin mỗi phút, mỗi lần tạo `URLClassLoader` mới. Ban đầu mọi thứ ổn, sau vài giờ thấy:

```text
Metaspace used         tăng dần sau mỗi reload
Class count            tăng dần
Classloader generations tăng dần
OS/container memory    tăng dần hoặc dao động cao
GC cycles              có nhưng ít/không unload loader cũ
```

Đây là **mẫu nghi vấn** chứ không phải bằng chứng; metric phải đo thực và phân tích retaining graph.

### Quy trình 7 bước

1. **Chốt baseline:** runtime version, container memory limit, `-Xmx`, `MaxMetaspaceSize`, GC, CDS, flags và loader count trước reload.
2. **Tái hiện có kiểm soát:** lặp plugin load/stop/reload N lần trong môi trường thử nghiệm; log generation IDs.
3. **Theo dõi heap và non-heap độc lập:** lấy Metaspace pool used/committed, NMT `Class` category và heap object population.
4. **Đếm loader/class populations:** `jcmd VM.classloader_stats`, JFR class loading/unloading events khi được bật.
5. **Phân tích heap retaining paths:** xác định những GC roots còn trỏ tới loader cũ hoặc object/class/method của nó.
6. **Dọn lifecycle:** threads, executors, TCCL, listener registries, JDBC/MBeans, reflection/ServiceLoader caches, buffers và resource handles.
7. **Đo lại sau cleanup:** không yêu cầu số liệu tụt ngay nhưng cần xu hướng ổn định hơn; nếu vẫn tăng, quay lại retaining paths/VM-level profiling.

### Ví dụ đường giữ loader

```text
GC root / long-lived host object
    |
    +--> static Map<String, Handler> in host
            |
            +--> PluginHandler instance
                    |
                    +--> Class<plugin.Handler>
                            |
                            +--> defining URLClassLoader V1
                                    |
                                    +--> plugin classes/metadata
```

Dù `pluginManager.currentLoader = loaderV2`, một reference còn trong static map có thể khiến loader V1 vẫn reachable. Thay loader reference chính **không tự xóa mọi references khác**.

**Không phải cứ static field trong plugin là leak vĩnh viễn.** Những object cycles chỉ tự trỏ vào nhau có thể được GC thu hồi khi cả graph không còn reachable từ GC roots. Vấn đề nằm ở **reference roots thật sự** và runtime lifecycle.

## 14. Khi nào Metaspace được tái sử dụng hoặc trả lại OS?

Trên HotSpot hiện đại, metadata allocations thường được quản lý theo **class-loader-associated arenas/chunks**. Từ góc nhìn đơn giản:

```text
Define classes -> class metadata allocations -> Metaspace chunks
          |
          v
Retire class loader generation
          |
          v
Bỏ được các strong references tới loader/classes/instances?
          |
         Có
          |
          v
JVM/GC có thể unload class loader và các classes
          |
          v
Allocator có free chunks hoặc vùng có thể reuse
          |
          v
Elastic Metaspace có thể uncommit một phần unused pages
          |
          v
OS resident memory có thể giảm (không hứa đúng thời điểm/số bytes)
```

### Lý do used giảm mà committed chưa giảm

- Chunk có free space nhưng vẫn được giữ để **tái sử dụng nhanh**.
- Các commit granules còn metadata sống nên chưa thể uncommit hết.
- Reclaim policy hướng tới cân bằng footprint và CPU overhead.
- Thời điểm GC/class unloading chưa xảy ra hoặc chưa đủ điều kiện.
- Công cụ lấy snapshot ở thời điểm khác nhau.

### Lý do committed giảm nhưng process RSS chưa giảm tương ứng

- OS accounting/mapped pages hoạt động khác expected.
- Subsystems khác đồng thời tăng native memory hoặc heap committed/resident.
- Những pages đã trả/đánh dấu nhưng số liệu RSS chưa phản ánh tức thời.
- Cgroup/process measures và NMT dùng phạm vi khác nhau.

Vì vậy KPI nên gồm **Metaspace used/committed, classloader count, native total, RSS và thời gian**, không chỉ một thanh biểu đồ.

## 15. Compressed Class Space không phải mọi Metaspace

Trong HotSpot 64-bit với **compressed class pointers**, metadata class pointers có thể được biểu diễn qua một vùng địa chỉ được reserve thích hợp gọi **Compressed Class Space**. Một phần non-class metadata dùng allocator khác.

Điều này dẫn tới các báo cáo như:

```text
Class (reserved=..., committed=...)
  Metadata: ...
  Class space: ...
```

Tùy HotSpot/JDK, presentation chính xác có thể khác.

**Phân biệt:**

- `CompressedClassSpaceSize`: giới hạn/reservation liên quan compressed class space trong cấu hình áp dụng.
- `MaxMetaspaceSize`: giới hạn tổng class-metadata allocations theo HotSpot option.
- `-Xmx`: giới hạn Java heap, **không** giới hạn trực tiếp metaspace.
- `reserved`: virtual address space, không bằng resident RAM.
- `committed`: memory backing, không có nghĩa tất cả là objects đang dùng.
- `used`: snapshot theo allocator accounting, không phải số bytes tương đương một class-file JAR.

Nếu `OutOfMemoryError: Compressed class space` xuất hiện, tăng `MaxMetaspaceSize` đơn lẻ không phải lúc nào cũng giải quyết được giới hạn khác; cần xem compressed class space configuration, số lượng classes/loader generations và native headroom.

## 16. CDS, code cache và metadata sharing

**Class Data Sharing (CDS)** trên HotSpot giúp chia sẻ một số class data đã chuẩn bị trước giữa Java processes hoặc cải thiện startup/memory footprint khi archive được sử dụng.

Nhưng:

- CDS không đồng nghĩa “mọi custom-loader class metadata đều được chia sẻ”.
- Một class loader mới vẫn có runtime class identity/lifecycle của mình.
- CDS không biến objects/static mutable fields của plugin A thành state chung có kiểm soát.
- CDS không loại bỏ mọi chi phí class initialization, dynamic linking hoặc JIT.
- Memory-mapped CDS archives có accounting khác với Metaspace committed và Java heap.

Tương tự, **JIT code cache** lưu native compiled code phục vụ thực thi methods. Nó có memory lifecycle và flags riêng; **không nên gộp code cache vào Metaspace chỉ vì method thuộc một class đã load**.

Khi profile native memory, so sánh các nhóm **`Class`, `Code`, `Thread`, `GC` và `Java Heap`** theo công cụ hỗ trợ; tránh đổ lỗi Metaspace cho mọi native memory growth.

## 17. Khác biệt giữa “metadata tăng” và “heap leak”

| Dấu hiệu quan sát | Hướng nghi vấn | Xác minh tiếp |
|---|---|---|
| Heap objects tăng mạnh, loader/class counts ổn định | Heap cache/leak thông thường | Heap histograms, heap dump, allocation profile |
| Loader/class counts tăng theo số plugin reload | Loader generations giữ lại | Retaining path từ GC roots, stop/unregister |
| Metaspace used tăng, heap ổn định tương đối | Nhiều class definitions/loader retention | NMT, classloader stats, JFR |
| Metaspace used plateau, committed cao hơn | Allocator slack/reclaim timing | Elastic Metaspace policy, trends |
| RSS tăng nhưng Metaspace stable | Native subsystem khác, direct buffers, threads, JNI, GC, JIT | NMT subsystem diff và OS tools |
| `Class reserved` rất lớn nhưng committed thấp | Virtual address reservation cho compressed class pointers | Không quy trực tiếp thành physical memory leak |

Trong server containerized, memory leak còn có thể đến từ **direct/mapped buffers**, JNI libraries hoặc native agents mà NMT không thống kê đầy đủ. Đừng chỉ điều chỉnh `MaxMetaspaceSize` nếu OS đang kill process vì RSS tăng từ thành phần khác.

## 18. Những ngộ nhận cần loại bỏ

### “Method area chính là Metaspace theo đặc tả Java”

**Không chính xác.** Method area là abstract runtime region của JVMS; Metaspace là HotSpot implementation detail cho class metadata. Một JVM khác có thể dùng cơ chế phân bổ khác mà vẫn phù hợp đặc tả.

### “Method area logically part of heap, vậy `-Xmx` phải giới hạn Metaspace”

**Không.** `-Xmx` giới hạn Java object heap của HotSpot, còn nhiều HotSpot metadata allocations nằm trong native/non-heap memory. Cụm “logically part” không phải lời cam kết placement vật lý.

### “Tất cả static variables nằm hoàn toàn trong Metaspace”

**Sai về mức độ tuyệt đối.** Class metadata, static field storage, `Class<?>` mirrors và object mà static reference trỏ tới có cách biểu diễn/placement khác nhau theo HotSpot implementation. Đặc biệt **object được static field tham chiếu tới nằm ở Java heap**. Không vẽ mọi static value thành các bytes luôn nằm nguyên trong Metaspace.

### “JAR có 30 MB class files thì Metaspace phải dùng 30 MB”

**Không.** Kích thước JAR đã nén, số classes thực load, class metadata structures, shared archives, JIT code, resources và allocator overhead là các đại lượng khác nhau.

### “Mỗi class loader luôn có một Metaspace riêng”

**Không theo nghĩa một pool riêng có size cố định.** HotSpot có subsystem Metaspace chung và cấp chunks/arenas gắn với loader data structures. Những loaders khác nhau có metadata ownership khác nhau, nhưng không phải mỗi loader sở hữu một OS memory pool độc lập với một `MaxMetaspaceSize` riêng.

### “Đóng loader thì Metaspace sẽ lập tức giảm”

**Không.** `URLClassLoader.close()` giải phóng tài nguyên loader, không buộc GC/class unloading. Loader có thể còn reachable; cũng có allocator reclaim delay.

### “Metaspace reserved 1 GB nghĩa là JVM đã ăn 1 GB RAM”

**Không.** Reserved virtual address space khác committed physical backing và khác resident memory. Đặc biệt compressed class space có thể reserve một khoảng địa chỉ lớn.

### “Tăng `MaxMetaspaceSize` là sửa được Metaspace leak”

**Không.** Nó thay giới hạn, không xóa references giữ classloader generations. Cần tìm root cause bằng profiling/heap graph.

### “Method area không bao giờ GC được”

JVMS không bắt buộc mọi implementation áp dụng policy GC cụ thể cho method area; **không cấm JVM thực hiện class unloading**. HotSpot có class unloading và reclaim metadata theo các điều kiện nhất định. JLS giới hạn khi class có thể unload.

## 19. Checklist vận hành Metaspace cho ứng dụng plugin

- [ ] Ghi rõ **JVM implementation/vendor/version** đang chạy.
- [ ] Tách **JVMS method area** khỏi **HotSpot Metaspace** khi giải thích.
- [ ] Không cộng `-Xmx` với `MaxMetaspaceSize` rồi gọi đó là tổng giới hạn RAM process.
- [ ] Quan sát `Metaspace` pool `used/committed` và biết `max` có thể là `-1`.
- [ ] Phân biệt `reserved`, `committed`, `used` và **RSS**.
- [ ] Theo dõi **loaded-class count** và **classloader generations** theo thời gian.
- [ ] Nếu nghi leak, lấy **heap retaining paths** đến loader/instance/reflection handles.
- [ ] Kiểm tra TCCL, `ThreadLocal`, executors, listeners, `ServiceLoader` và global registries.
- [ ] Khi plugin retire, dừng work rồi cleanup và `close` loader đúng thứ tự.
- [ ] Không dùng `System.gc()` làm điều kiện bắt buộc xác nhận unload.
- [ ] Nếu bật NMT, bật từ startup và chấp nhận profiling overhead.
- [ ] Khi điều chỉnh `MetaspaceReclaimPolicy`, benchmark memory lẫn CPU/latency.
- [ ] Kiểm tra CDS, compressed class pointers và allocator differences trước khi diễn giải NMT.
- [ ] Kiểm tra container/cgroup limits, native headroom và các memory pools khác.
- [ ] Không sử dụng classloader như sandbox an ninh chỉ vì nó cách ly metadata.

## 20. Câu hỏi tự kiểm tra

**1. Method area có phải tên riêng của HotSpot Metaspace không?** Không. Method area là cấu trúc trừu tượng của JVMS; Metaspace là cách HotSpot quản lý class metadata.

**2. Vì sao “method area logically part of heap” không mâu thuẫn với HotSpot `MemoryType.NON_HEAP`?** Vì một câu nói về **logic model**, câu còn lại nói về **implementation/management classification**.

**3. `Class<?>` mirror và `new Widget()` thường nằm ở đâu trên HotSpot hiện đại?** Là Java objects trên Java heap; class metadata riêng có thể ở Metaspace.

**4. Hai loaders định nghĩa cùng binary name có cùng runtime class không?** Không. Defining loader là một phần của runtime class identity.

**5. Gọi `URLClassLoader.close()` có trực tiếp xóa metadata của loaded classes không?** Không. Class unloading phụ thuộc JVM/GC và điều kiện của defining loader.

**6. `used` và `committed` có giống nhau không?** Không; committed có thể cao hơn used do allocator chunks, slack và trạng thái reclaim.

**7. `reserved` có bằng lượng RAM vật lý đã dùng không?** Không. Đây là không gian địa chỉ ảo dành trước.

**8. Có thể dùng `-Xmx` để giới hạn chính xác Metaspace không?** Không. `-Xmx` giới hạn Java heap, không phải HotSpot metadata memory.

**9. Tại sao JEP 387 có thể giúp ứng dụng reload plugins?** Elastic Metaspace cải thiện khả năng hoàn trả native memory không dùng sau class unloading, nhưng không thay được lifecycle cleanup.

**10. NMT có bao quát mọi malloc của native code bên thứ ba không?** Không. NMT chủ yếu theo dõi HotSpot VM internals và có giới hạn phạm vi.

**11. Nếu Metaspace tăng liên tục, tăng limit hay tìm references trước?** Xác định tình trạng trước: load thường xuyên hay loader generations không được thu hồi; tìm retaining paths và lifecycle root cause.

**12. Nếu không thấy class-unload log ngay sau `close` thì chắc chắn leak không?** Không. GC/class unloading không có cam kết thời gian cụ thể.

## 21. Tài liệu chính thức để đối chiếu

- **JVMS Java SE 17, `2.5.4` – Method Area**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.4
- **JLS Java SE 17, `12.7` – Unloading of Classes and Interfaces**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7
- **Java SE 17 `MemoryPoolMXBean` / `MemoryUsage`**: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html và https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryUsage.html
- **Oracle Java SE 17, GC Tuning – Other Considerations, Metaspace**: https://docs.oracle.com/en/java/javase/17/gctuning/other-considerations.html
- **Oracle Java SE 17, Native Memory Tracking**: https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
- **Oracle Java SE 17, `java` launcher options**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **OpenJDK JEP 387 – Elastic Metaspace (JDK 16)**: https://openjdk.org/jeps/387
- **Java SE 17 `URLClassLoader`**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html

**Ghi nhớ:** **Method area là đặc tả logic; Metaspace là chi tiết HotSpot**. Một runtime class có metadata được quản lý gắn với defining loader; loader bị giữ sống có thể khiến metadata tiếp tục chiếm bộ nhớ. Muốn chẩn đoán chính xác hãy kết hợp **Metaspace pool, NMT, classloader stats, heap retaining paths và OS/container metrics**, luôn phân biệt **used, committed, reserved và RSS**. Việc `close` loader hoặc tăng giới hạn bộ nhớ không thể thay thế việc quản lý đúng vòng đời và references.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-unloading-and-memory-006">Static field, cache reflection và tham chiếu từ ứng dụng chủ</a>

<details>
<summary>Xem chi tiết</summary>

# Static field, cache reflection và tham chiếu từ ứng dụng chủ

> **Java Core → ClassLoader → Dỡ lớp và bộ nhớ.** Trong plugin architecture, không phải cứ gọi `URLClassLoader.close()` là Java classes, static state và Metaspace của phiên bản cũ biến mất. Điều quyết định là **đường tham chiếu mạnh từ GC roots đến loader/classes/plugin objects** còn tồn tại hay không. Bài này phân biệt static field trong plugin với static cache ở ứng dụng chủ, phân tích reflection metadata, `WeakHashMap`, `ClassValue`, TCCL, ThreadLocal và cách cleanup theo vòng đời.

## 1. Tại sao static/cache có thể ngăn class unloading?

Một runtime Java class được nhận diện bằng **binary name + defining class loader**. Plugin V1 và V2 có thể cùng khai báo `plugin.impl.Handler` nhưng là **hai runtime types** nếu được định nghĩa bởi hai plugin loaders riêng. Mỗi type có `Class<?>` mirror, static fields và state riêng.

JLS `12.7` nêu JVM **có thể** unload một class khi và chỉ khi **defining class loader có thể được garbage collector reclaim**. Đây là điều kiện cần cho class unloading, **không phải cam kết GC sẽ xảy ra ngay**.

Có hai đồ thị rất khác nhau:

~~~text
A. CHU TRÌNH BÊN TRONG PLUGIN, KHÔNG CÓ ROOT BÊN NGOÀI

  PluginLoader -> Class<Plugin> -> static INSTANCE
        ^                                |
        +-------- instance/class --------+

  Nếu toàn bộ cụm không reachable từ GC roots:
  tracing GC có thể thu hồi cả cụm; static không tự tạo root.

B. THAM CHIẾU TỪ HOST SỐNG LÂU

  GC root -> Host Class -> static Map<String, Method>
                                  |
                                  +-> Method
                                        |
                                        +-> declaring Class<Plugin>
                                                |
                                                +-> defining PluginLoader

  Root host còn giữ Method => loader V1 còn reachable.
~~~

**Nguyên tắc:** “Có static field” không phải kết luận đủ để gọi memory leak. Cần biết **static field thuộc lớp do loader nào định nghĩa**, giá trị của nó giữ object nào, và **có một GC root bên ngoài generation đã stop trỏ vào cụm này không**.

### 1.1. Thuật ngữ cần phân biệt

| Khái niệm | Ý nghĩa |
| --- | --- |
| GC root | Điểm khởi đầu phân tích reachability của JVM/collector, như live thread frames, runtime roots và reachable static state |
| Strong reference | Tham chiếu khiến referent tiếp tục reachable qua tracing GC |
| Weak reference | Không tự giữ referent sống; được clear khi GC xử lý đối tượng yếu-reachable |
| Defining loader | Loader thực sự định nghĩa runtime Class; ảnh hưởng type identity và unload eligibility |
| Class mirror | Đối tượng `Class<?>` biểu diễn runtime type |
| Metaspace | Bộ nhớ native dùng cho nhiều class metadata trong HotSpot, khác với Java heap |
| Class unloading | VM loại bỏ metadata của runtime classes khi loader eligible và collector thực hiện |
| Resource closing | Đóng JAR/stream/thread/connection theo API lifecycle, **khác** class unloading |

Một live loader không phải leak nếu plugin đang ACTIVE và phục vụ công việc. Leak là loader **đáng lẽ đã được retire** nhưng vẫn bị giữ bởi một root không còn hợp lệ theo lifecycle.

## 2. Static field ở plugin không tự động gây leak

Ví dụ plugin:

~~~java
package plugin.impl;

public final class InternalCache {
    public static final InternalCache INSTANCE =
            new InternalCache();

    private final byte[] data = new byte[4096];

    private InternalCache() {}

    public int bytes() {
        return data.length;
    }
}
~~~

Có một vòng class/static singleton/instance. Nếu không còn bất cứ root ngoài plugin giữ loader, class hay objects của generation, tracing GC có thể thu hồi cả vòng. **Không cần set mọi static field về null chỉ để phá một chu trình**.

Nhưng ngay khi plugin còn active, static map lớn vẫn có thể gây **heap pressure**. Tài nguyên như stream, thread, native handle, DB connection nên close rõ ràng trước khi chấm dứt plugin; không dựa GC để tự đóng.

### 2.1. Khi nào static field ở plugin trở thành vấn đề?

- Một host registry giữ `InternalCache.INSTANCE` trong `Map<String,Object>`.
- Một worker thread do plugin tạo vẫn chạy và giữ plugin `Runnable`/frame/TCCL.
- Plugin đăng ký listener vào **host singleton EventBus**, host nắm callback plugin.
- Một host serializer/DI framework cache `InternalCache.class` hoặc reflection metadata.
- Một global `ThreadLocal`/TCCL của pool worker giữ plugin object/classloader.
- Plugin giữ native resources mà lifecycle `stop` không close.

Không nên thay hết static fields bằng instance fields như một “fix” chung. Nếu instance vẫn được host giữ, classloader vẫn bị pin.

## 3. Static field ở host: nguồn pin loader dễ thấy nhất

Xem một host registry:

~~~java
public final class HostRegistry {
    public static final java.util.Map<String, Object> SERVICES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private HostRegistry() {}
}
~~~

Host class sống xuyên nhiều lần reload V1→V2. Nếu host gọi:

~~~java
HostRegistry.SERVICES.put("plugin-old", pluginInstance);
~~~

thì có chuỗi `host static map → plugin instance → runtime class → defining plugin loader`. Gọi `URLClassLoader.close()` **không loại bỏ** entry này.

Cách xử lý đúng là host xác định ownership: mỗi entry thuộc generation nào, được remove khi nào, ai bảo đảm callback/request cũ kết thúc và có thể thêm lại entry hay không.

**Rủi ro với `static final`:** `final` chỉ khiến reference của `Map` không được gán lại bình thường; **nội dung Map vẫn mutable**. Mặc định Java không tự expire entries theo plugin lifecycle.

### 3.1. Host cache không chứa plugin instance vẫn có thể leak

Ví dụ host chỉ lưu Java reflection:

~~~java
static final java.util.Map<String, java.lang.reflect.Method> METHODS =
        new java.util.concurrent.ConcurrentHashMap<>();
~~~

Value `Method` biểu diễn member thuộc một **declaring Class** cụ thể. Có thể đi từ:

~~~java
java.lang.reflect.Method method = METHODS.get("plugin.label");
Class<?> declaring = method.getDeclaringClass();
ClassLoader loader = declaring.getClassLoader();
~~~

Đây là snippet phụ thuộc cache đã có key, không phải Java main độc lập. `Method`, `Field` và `Constructor<?>` có API `getDeclaringClass()`. Do đó host giữ reflection metadata của plugin có thể giữ **Class<?>** và defining loader ngay cả khi host đã bỏ instance plugin chính.

Tương tự những caches quan trọng:

| Value được host giữ | Khả năng liên hệ plugin generation |
| --- | --- |
| `Class<?>` | Định danh runtime type/defining loader |
| `Method` | Declaring class và method signature |
| `Field` | Declaring class và field type |
| `Constructor<?>` | Declaring class và parameter/exception types |
| `MethodHandle` | Target, method type hoặc runtime linkage tới plugin |
| `VarHandle` | Field/access semantics của runtime type |
| `MethodType` | Các `Class<?>` của parameter/return types |
| `MethodHandles.Lookup` | Lookup class/context |
| `Proxy`/generated accessor | Generated classes, interfaces, handler instance |
| `ServiceLoader`/`Provider<?>` | Discovered provider types/instances/cache |
| Captured lambda / `Runnable` | Captured plugin service/handler hoặc code của plugin |

Cần phân tích graph **thực tế** trên JVM khi nghi leak; không khẳng định mọi MethodHandle hoặc every framework cache đều giữ loader vĩnh viễn.

### 3.2. Cache dùng key String có an toàn?

~~~java
Map<String, Class<?>> types;
Map<String, Method> methods;
Map<String, Object> instances;
~~~

**Không tự động an toàn.** Key `String` là value độc lập với plugin class, nhưng **strong value** vẫn có thể trỏ đến plugin. Đổi cache key từ `Class<?>` sang tên class `String` không giải quyết nếu value vẫn giữ reflection object.

Nếu host chỉ cần hiển thị tên và phiên bản plugin, lưu lightweight descriptor:

~~~java
record PluginDescriptor(String id, String version,
                        String implementationName) {}
~~~

Đây là record Java 17 do host định nghĩa. Descriptor chứa strings thuần và không cần giữ `Class<?>` của plugin. Khi thật sự cần execute, resolve đúng instance của **active generation**; không dùng `Class.forName` vô ý trên system loader.

## 4. Thí nghiệm Java 17+: static host cache giữ `Method` sau close

Bài lab độc lập dưới đây dùng một plugin JAR và host program. Code chứng minh những điều có thể dự đoán chắc chắn **không cần `System.gc()`**: cache host vẫn giữ reflection Method, Method dẫn về plugin loader, method đã nạp còn gọi được sau `URLClassLoader.close()`, và entry được xóa khi host remove.

### 4.1. Source plugin

Cây thư mục:

~~~text
reflection-cache-lab/
├── plugin-src/demo/plugin/Feature.java
├── host-src/HostCacheDemo.java
└── build/                 (tạo bởi lệnh bên dưới)
    ├── plugin/
    ├── host/
    └── plugin.jar
~~~

Tệp **`plugin-src/demo/plugin/Feature.java`**:

~~~java
package demo.plugin;

public final class Feature {
    public static final byte[] LOCAL_BUFFER = new byte[1024];

    private Feature() {}

    public static String label() {
        return "hello-from-plugin";
    }
}
~~~

Static `LOCAL_BUFFER` minh họa state của runtime class plugin. Nó không tự biến loader thành GC root thuộc host.

### 4.2. Source ứng dụng chủ

Tệp **`host-src/HostCacheDemo.java`**:

~~~java
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HostCacheDemo {
    private static final Map<String, Method> HOST_METHODS =
            new ConcurrentHashMap<>();

    private static WeakReference<ClassLoader> loadAndClose()
            throws Exception {
        URL pluginJar = Path.of("build", "plugin.jar")
                .toAbsolutePath().toUri().toURL();

        URLClassLoader loader = new URLClassLoader(
                new URL[] {pluginJar},
                ClassLoader.getPlatformClassLoader());

        Class<?> type = Class.forName(
                "demo.plugin.Feature", true, loader);
        Method method = type.getMethod("label");

        HOST_METHODS.put("feature.label", method);

        WeakReference<ClassLoader> observer =
                new WeakReference<>(loader);

        // Đóng JAR handles của loader; không xóa loaded Class.
        loader.close();
        return observer;
    }

    public static void main(String[] args) throws Exception {
        WeakReference<ClassLoader> observer = loadAndClose();

        Method cached = HOST_METHODS.get("feature.label");
        ClassLoader owner =
                cached.getDeclaringClass().getClassLoader();

        System.out.println("cache size=" + HOST_METHODS.size());
        System.out.println("method owner matches="
                + (owner == observer.get()));
        System.out.println("invoke after close="
                + cached.invoke(null));

        HOST_METHODS.remove("feature.label");
        System.out.println("cache empty=" + HOST_METHODS.isEmpty());

        // Các biến 'cached' và 'owner' vẫn còn trong scope.
        // Không thể assert loader đã GC chỉ vì map được xóa.
    }
}
~~~

### 4.3. Biên dịch và chạy

Từ root `reflection-cache-lab`, trong POSIX shell (Linux/macOS, Git Bash), JDK 17 trở lên:

~~~bash
mkdir -p build/plugin build/host
javac --release 17 -d build/plugin \
    plugin-src/demo/plugin/Feature.java
jar --create --file build/plugin.jar -C build/plugin .

javac --release 17 -d build/host \
    host-src/HostCacheDemo.java

# Cố ý không đặt plugin.jar vào host classpath.
java -cp build/host HostCacheDemo
~~~

**Stdout theo cấu hình bài lab**:

~~~text
cache size=1
method owner matches=true
invoke after close=hello-from-plugin
cache empty=true
~~~

Giải thích:

1. `URLClassLoader` có parent là platform loader, nên plugin implementation trong JAR được **plugin loader** định nghĩa, không bị application loader lấy hộ do cùng classpath.
2. `HOST_METHODS` là static map thuộc host class. `Method` cached có `getDeclaringClass()` là `demo.plugin.Feature` của plugin loader.
3. `observer` là **weak reference**, không tự giữ loader. Nhưng `Method` còn trong map và biến local, nên loader vẫn reachable.
4. `Method.invoke(null)` gọi một static method đã được load; `URLClassLoader.close()` không xóa class hiện hữu.
5. `remove` dọn **một root ở host map**, nhưng `cached`, `owner` và local stack vẫn còn references. Không khẳng định unloading đã xảy ra.
6. Nếu static method lần đầu yêu cầu nạp class/resource khác từ JAR sau khi loader close, lời gọi có thể thất bại. Ví dụ này cố ý không phụ thuộc các classes chưa nạp.

Trên Windows, các lệnh tạo thư mục có thể thay bằng PowerShell; không cần đổi classpath separator vì lệnh chạy chỉ có **một entry `build/host`**.

### 4.4. Vì sao không đưa kết quả `WeakReference.get()` sau `System.gc()` vào stdout?

Một `WeakReference<ClassLoader>` chỉ chứng minh tham chiếu đó **không tự ngăn reclaim**. Nếu còn strong root khác, referent có thể tiếp tục sống. Nếu bạn đã xóa mọi root nhưng GC chưa chạy/không chọn unload, `get()` vẫn có thể khác null. Ngược lại, một thí nghiệm bị compiler/JIT tối ưu lifetime của local variables có thể cho kết quả khác bạn dự kiến.

Do đó không có stdout GC-unload cố định cho mọi JVM. Khi kiểm thử, ghi log observer/`ReferenceQueue` trong cửa sổ quan sát, kiểm tra heap roots và chú thích rằng class unloading phụ thuộc collector/runtime.

## 5. `WeakHashMap`: key yếu nhưng value mạnh

Một cách sửa cache quen thuộc:

~~~java
static final java.util.WeakHashMap<Class<?>,
                                  java.lang.reflect.Method> CACHE =
        new java.util.WeakHashMap<>();
~~~

Có thể vẫn giữ loader vì `WeakHashMap` giữ **value bằng strong reference**:

~~~text
Host -> WeakHashMap
        (key) --weak--> PluginClass
        (value) --strong--> Method -> declaring PluginClass
                                     |
                                     +-> PluginLoader
~~~

Value tham chiếu ngược key, nên key vẫn có đường strong reachability qua value. Đây là lỗi được Javadoc `WeakHashMap` cảnh báo: values **không nên giữ keys trực tiếp hoặc gián tiếp** nếu mong entry được tự thu hồi.

Một value chỉ chứa strings/primitives độc lập với plugin classes có thể phù hợp với weak key, nhưng còn phải xử lý việc entry bị GC xóa **không theo thời điểm cố định**. `WeakHashMap` không thread-safe mặc định.

### 5.1. Weak key không phải cơ chế lifecycle

Không thay quy trình stop/unregister bằng niềm tin rằng GC sẽ dọn. Khi đang có request sử dụng provider, việc entry bị xóa ngẫu nhiên có thể ảnh hưởng cache hit rate; khi plugin retire, host cần **deterministic release** các references do mình sở hữu.

Nếu muốn dùng `WeakReference<Method>` làm value, bạn phải xử lý `ref.get()==null` khi truy cập, tính lại metadata và quản lý concurrency. Weak cache là **tối ưu bộ nhớ**, không phải thay toàn bộ cleanup protocol.

## 6. `ClassValue`: per-class metadata cache

Java `ClassValue<T>` sinh ra cho trường hợp cần lưu metadata **theo từng `Class<?>`** mà không tự xây global strong-key map.

Ví dụ Java 17 helper:

~~~java
import java.lang.reflect.Method;

public final class AccessorCache {
    private AccessorCache() {}

    private static final ClassValue<Method> LABEL =
            new ClassValue<>() {
                @Override
                protected Method computeValue(Class<?> type) {
                    try {
                        return type.getMethod("label");
                    } catch (NoSuchMethodException problem) {
                        throw new IllegalArgumentException(problem);
                    }
                }
            };

    public static Method lookup(Class<?> type) {
        return LABEL.get(type);
    }

    public static void evict(Class<?> type) {
        LABEL.remove(type);
    }
}
~~~

Tệp **`AccessorCache.java`** có thể biên dịch với `javac --release 17`; đây là **library class**, không có `main`. Mục tiêu là liên kết computed metadata với class cụ thể và dùng `remove(type)` để xóa association khi cần.

Javadoc `ClassValue` cho biết `computeValue` có thể chạy **nhiều lần** dưới tranh chấp đồng thời, nhưng cơ chế sẽ chọn một value liên kết với `Class` theo contract. Không viết `computeValue` có side effects tài nguyên không idempotent mà không kiểm soát.

**Giới hạn quan trọng:** `ClassValue` không xóa roots khác (host registry, TCCL, event listeners) và không phải `unloadClass`. Nếu value chứa `AutoCloseable`, việc `remove` cũng không đồng nghĩa tự gọi `close()` cho value. Cần owner/lifecycle riêng.

## 7. Một cache có scope theo generation

Nếu host cần lookup method nhiều lần cho một plugin version, thường nên đặt cache **trong đối tượng generation**, thay vì static global:

~~~java
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class GenerationMethods {
    private final Map<String, Method> methods =
            new ConcurrentHashMap<>();

    void put(String name, Method value) {
        methods.put(name, value);
    }

    Method get(String name) {
        return methods.get(name);
    }

    void clear() {
        methods.clear();
    }
}
~~~

Code biên dịch được nhưng chỉ minh họa cache object, **không chứa lifecycle coordination**. Để dùng trong production cần trạng thái `ACTIVE → DRAINING → CLOSED` và không cho một request V1 thêm Method cũ **sau khi** admin `clear()`.

Sơ đồ race sai:

~~~text
Request V1                           Admin
  giữ Method V1
                                      CACHE.clear()
  CACHE.put("key", Method V1)         close(loader V1)
  -> V1 được cache lại sau cleanup
~~~

`ConcurrentHashMap` chỉ bảo vệ các thao tác map ở mức thread safety, không làm hai hoạt động “clear old generation” và “không cho writer V1 thêm lại” thành một transaction.

Cách sửa: ngăn request mới trên V1, đợi in-flight work hoàn tất (read/write lock hoặc **generation lease/reference counting**), cleanup cache V1, đóng objects/resources, đóng loader, rồi bỏ references cuối.

### 7.1. Dùng generation id để tránh lẫn V1/V2

Nếu bắt buộc host giữ registry global, key nên chứa identity thế hệ **và** scope tenant/plugin, kèm eviction chính xác:

~~~java
record CacheKey(String pluginId, long generation,
                String operation) {}
~~~

Một key tốt **không tự giải phóng value**. Khi generation 42 stop, xóa tất cả entries thuộc generation 42 sau draining; không xóa nhầm 43 vừa publish. Với ứng dụng nhiều threads, đảm bảo iteration/removal và lookup không chạy ngoài lifecycle contract.

## 8. `MethodHandle`, `VarHandle`, proxy và framework cache

Reflection truyền thống không phải nguồn duy nhất:

- `MethodHandle` có thể giữ target hoặc runtime type liên quan tới implementation plugin.
- `VarHandle` có thể gắn với field type và declaring class của plugin.
- `MethodType` có `Class<?>` parameter/return types; một host cache MethodType chứa plugin type có thể pin loader.
- `MethodHandles.Lookup` chứa lookup class/context; giữ nó lâu hơn plugin generation có thể gây retention.
- Generated proxies/bytecode accessors/captured lambdas chứa interfaces, handler hoặc target object thuộc plugin loader.
- Serializer/ORM/DI frameworks cache annotation metadata, property descriptors, builders hoặc supplier của plugin type.
- `java.beans.Introspector` và các framework-specific introspection caches cần được xử lý theo API chính thức khi plugin unload.

Không khẳng định mọi cache do JVM/framework cung cấp đều leak; nhiều cache dùng weak keys hoặc lifecycle scopes phù hợp. **Chỉ khi có root path giữ loader đã retire** mới gọi là leak thực tế. Heap dump/dominator tree có ích hơn đoán dựa trên tên lớp.

## 9. TCCL và ThreadLocal: roots ẩn trong thread pool

Worker thread ở host có thể giữ plugin loader cũ qua `Thread.contextClassLoader` hoặc value của `ThreadLocal` sau khi task plugin kết thúc. Đây là đường `live Thread → plugin state/loader` mà dọn host static cache không xử lý hết.

Khôi phục TCCL bằng `finally`, gọi `ThreadLocal.remove()` tại **đúng worker thread**, và cancel/drain asynchronous tasks trước retire. Bài Thread/ThreadLocal/TCCL có thí nghiệm thread pool riêng.

## 10. Close resources không đồng nghĩa unload class

`URLClassLoader.close()` đóng nguồn class/resource và file handles do nó quản lý; các class, `Method` và instance đã được load vẫn có thể tiếp tục được host giữ. Một host cache còn giữ reflection `Method` sau close là ví dụ điển hình.

Muốn JVM **có cơ hội** unload, cắt các retaining references tới class/defining loader; GC timing là quyết định của VM.

## 11. Quy trình retirement để không để lại roots của V1

**STOP new work → DRAIN current work → UNREGISTER callbacks → CLEAR host caches and generation records → CLEAN TCCL/ThreadLocal → CLOSE owned resources and URLClassLoader → RELEASE strong references**.

Đối với bài này, kiểm tra trọng tâm là cache host không còn entries `Class<?>`, `Method`, `MethodHandle`, proxy hoặc instance thuộc V1, kể cả cache ẩn của framework. `WeakHashMap` cần xem cả value có tham chiếu ngược đến key không; `ClassValue` hoặc per-generation cache có thể phù hợp hơn.

## 12. Chẩn đoán chính xác bằng heap roots và Metaspace

Một sự kiện class unloading tùy thuộc JVM/GC. JLS `12.7` không yêu cầu class unload ngay khi loader đủ điều kiện. Java SE cũng không cung cấp lệnh `ClassLoader.unloadClass("name")`.

Khi nghi retention sau nhiều lần V1→V2:

1. Ghi **generation id/artifact hash/loader identity** ở startup và stop.
2. Đo số active plugins, in-flight calls, host cache entries, TCCL của long-lived workers.
3. Quan sát số classloaders, class count, Metaspace và heap qua nhiều vòng reload; không lấy một thời điểm tăng memory làm bằng chứng.
4. Thu heap dump khi phù hợp vận hành và dùng dominator tree / path to GC root để tìm giữ loader.
5. Ưu tiên đường `Host static Map → Method → Class → Loader`, `Thread → contextClassLoader`, `ThreadLocalMap → value`, `Host EventBus → listener`, `ServiceLoader cache → provider`.
6. Sửa **ownership/eviction/lifetime protocol**, không dùng GC loop như workaround.
7. Chạy lại nhiều vòng, kiểm tra nếu tổng loader count hoặc Metaspace không còn tăng không kiểm soát.

### 12.1. Lệnh hỗ trợ trên HotSpot/JDK tương ứng

~~~bash
java -version

# HotSpot unified logging khi chạy ví dụ:
java -Xlog:class+load=info,class+unload=info \
    -cp build/host HostCacheDemo

# Với process sống lâu, thay PID thật vào vị trí <pid>:
jcmd <pid> help
jcmd <pid> VM.classloader_stats
jcmd <pid> VM.classloaders
jcmd <pid> VM.metaspace basic
jcmd <pid> Thread.print
~~~

`jcmd` và options phụ thuộc JVM/tool availability; xác nhận thông qua `jcmd PID help`. Heap dump/`GC.class_histogram` có thể gây chi phí stop-the-world, I/O lớn hoặc chứa dữ liệu nhạy cảm; chỉ thực hiện theo chính sách chẩn đoán phù hợp.

### 12.2. Giải thích dấu hiệu cẩn thận

| Dấu hiệu | Điều đã biết | Điều chưa thể kết luận |
| --- | --- | --- |
| Static host map có Method của V1 | Có root đến metadata V1 | Không còn bất kỳ root khác |
| `URLClassLoader.close()` thành công | Loader-owned file handles được cố đóng | Class V1 đã unload |
| `WeakReference.get()!=null` sau `System.gc()` | Loader chưa bị clear lúc quan sát | Chắc chắn leak |
| `WeakReference.get()==null` | Referent bị GC clear theo weak semantics | Tất cả plugin resources đã close |
| Có class unload log | JVM đã unload classes theo implementation | Mọi native/file/socket handle đã giải phóng |
| Metaspace tăng sau một số lượt reload | Có metadata/commit/reservation activity | Nguyên nhân chắc chắn là cache reflection |
| Host Method/Class cache liên tục tăng qua generations | Có retained metadata nghi vấn | Tự biết cache owner mà không kiểm tra heap path |

## 13. Các ngộ nhận thường gặp

1. “Static field của plugin tự nó gây leak.” — **Không nhất thiết** nếu toàn bộ loader graph không có root bên ngoài.
2. “Static final Map ở host không thể giữ thêm entries.” — **Sai**, final chỉ cố định Map reference.
3. “Cache `Method` không giữ plugin instance nên an toàn.” — **Sai**, Method liên quan declaring class.
4. “Dùng String làm cache key sẽ tránh classloader leak.” — **Sai** nếu value giữ `Class<?>`/Method/plugin object.
5. “`WeakHashMap<Class<?>, Method>` tự giải phóng mọi entry.” — **Sai**, value có thể giữ key.
6. “Chuyển sang `ClassValue` là xong mọi memory leak.” — **Sai**, còn host listeners/TCCL/registries.
7. “`URLClassLoader.close()` lập tức unload class.” — **Sai**.
8. “Static state V1 bị reset sau close.” — **Sai**, loaded class vẫn sống khi reachable.
9. “Worker daemon không bao giờ pin plugin loader.” — **Sai**, active thread có thể giữ roots.
10. “`System.gc()` chắc chắn làm loader biến mất.” — **Sai**, không có deadline/guarantee.
11. “Nếu hai classes cùng binary name thì host cache đang trỏ version mới.” — **Sai**, cần so defining loader identity.
12. “`ConcurrentHashMap.clear()` đã atomic với mọi request.” — **Sai**, writer có thể repopulate sau clear.
13. “ServiceLoader.reload() hot-reload bytecode.” — **Sai**, nó reset provider cache của instance hiện tại.
14. “Heap giảm thì Metaspace lập tức giảm.” — **Không chắc**, hai miền memory/GC policies khác nhau.
15. “Một lần class count chưa giảm là JVM vi phạm spec.” — **Sai**, unloading là tối ưu hóa tùy runtime.

## 14. Câu hỏi tự kiểm tra

1. Static `PluginCache.INSTANCE` chỉ tham chiếu nội bộ loader V1 đã retire, không có root ngoài. Nó có **bắt buộc** pin V1 không? **Không**.
2. Một host static `Map<String, Method>` có thể pin plugin loader không? **Có**, qua Method→Class→loader.
3. `WeakHashMap<Class<?>, Method>` có chắc an toàn không? **Không**, value có thể giữ key mạnh.
4. `URLClassLoader.close()` có xóa những `Class<?>` đã loaded không? **Không**.
5. Vì sao phải dừng V1 tasks trước khi clear cache? **Chặn repopulation và tránh dùng resource đã close**.
6. Dùng `WeakReference<ClassLoader>` quan sát, một lần GC không clear có chứng minh leak? **Không**.
7. `ClassValue` dùng để làm gì? **Cache computed metadata theo Class**, không ép class unload.
8. `ThreadLocal.remove()` giúp phòng rò trên pool worker thế nào? **Bỏ value plugin sau scope**.
9. Cache key `String` và value `Class<?>` có an toàn cho hot reload không? **Không khi value giữ V1**.
10. Reflection `Method.getDeclaringClass()` có ý nghĩa gì? **Xác định runtime Class nơi khai báo method**.
11. Heap dump/dominator tree tìm điều gì? **Strong path tới GC root giữ loader**.
12. Khi nào V1 loader còn sống không bị coi là leak? **Khi có request/service V1 active theo lifecycle**.
13. Dùng một classloader mới cho V2 đã loại bỏ mọi root của V1 chưa? **Chưa**, host cache/threads có thể còn giữ V1.
14. `System.gc()` là yêu cầu hay cam kết? **Lời gợi ý/request**, không có guarantee unload tức thì.
15. Static byte array thuộc miền memory nào chủ yếu? **Heap**; class metadata của HotSpot ở Metaspace.

## 15. Tài liệu chuẩn

1. [JLS `12.7` — Unloading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.7): lớp chỉ có thể unload khi defining loader có thể reclaim, và thời điểm do implementation quyết định.
2. [JVMS `5.3` — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): runtime type identity, defining/initiating loader.
3. [ClassLoader — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): delegation, define/load, findLoadedClass.
4. [URLClassLoader — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/URLClassLoader.html): close, đã-load classes và JAR/file handles.
5. [Method — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/Method.html): getDeclaringClass, invoke.
6. [WeakHashMap — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/WeakHashMap.html): weak keys/strong values có thể tham chiếu ngược key.
7. [ClassValue — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassValue.html): computed per-Class cache, get/remove.
8. [WeakReference — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ref/WeakReference.html): weak references và GC semantics.
9. [jcmd — JDK 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html): VM.classloader_stats, VM.classloaders, VM.metaspace.
10. [Thread — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html): TCCL và thread lifetime.
11. [ServiceLoader — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html): provider cache/reload.

**Kết luận:** Static fields không đương nhiên gây leak, nhưng **strong reference từ host/GC root tới plugin Class/Method/instance/loader** có thể giữ generation đã retire. Quản lý caches **theo owner/generation**, dừng in-flight work, unregister listeners, restore TCCL, remove ThreadLocal values, close đúng resources và quan sát retained paths. `URLClassLoader.close()` là bước cleanup I/O, **không phải class-unloading API**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-unloading-and-memory-007">Thread, ThreadLocal, context class loader và tác vụ chưa dừng</a>

<details>
<summary>Xem chi tiết</summary>

# Thread, ThreadLocal, context class loader và tác vụ chưa dừng khi dỡ plugin

> **Java Core → ClassLoader → Dỡ lớp và bộ nhớ.** Tạo một `ClassLoader` mới để nạp phiên bản plugin khác chỉ là phần đầu của vòng đời. Một **thread vẫn sống**, một giá trị `ThreadLocal` chưa dọn, một `Thread.contextClassLoader` (TCCL) còn trỏ vào plugin hoặc một task chưa kết thúc có thể giữ **tham chiếu mạnh** đến class loader cũ. Khi đó, dù host đã bỏ plugin khỏi registry và gọi `URLClassLoader.close()`, loader cùng các lớp nó định nghĩa có thể chưa đủ điều kiện để garbage collector thu hồi. Bài này trình bày đồ thị tham chiếu, cách tái hiện lỗi, phân biệt “task kết thúc” với “thread kết thúc”, quy trình cleanup đúng và cách chẩn đoán trên Java 17+.

## 1. Câu hỏi thực tế: tại sao plugin đã dừng mà heap vẫn tăng?

Một plugin manager hỗ trợ hot reload thường làm:

```text
1. Tải plugin-v1.jar bằng PluginClassLoader V1
2. Tạo đối tượng plugin và chạy tasks
3. Người dùng nâng cấp plugin
4. Gọi plugin.stop(), close(V1)
5. Tải plugin-v2.jar bằng PluginClassLoader V2
6. Mong V1 và các class metadata cũ được GC thu hồi
```

Nhưng sau nhiều vòng, heap/metaspace có thể tăng và nhiều loader thế hệ cũ vẫn xuất hiện trong heap dump. Một lý do phổ biến là **host đã quên một root reference**:

```text
Live thread / global executor / registry
      |
      +--> Thread.contextClassLoader --> PluginClassLoader V1
      |
      +--> ThreadLocalMap (của thread)
      |         '--> plugin object --> Class<?> --> PluginClassLoader V1
      |
      +--> Runnable / Future / callback còn sống
                '--> plugin object hoặc plugin Class<?> --> V1
```

Đây là sơ đồ hướng đi phổ biến, không phải kết luận rằng **mọi** đường tham chiếu đều tồn tại ở mọi JVM. Garbage collector quyết định theo reachability thực tế: nếu một loader còn được chạm tới từ GC roots, loader không thể được thu hồi chỉ vì ứng dụng gọi phương thức tên là `stop` hoặc `close`.

**Định nghĩa quan trọng:**

- **Class unloading**: JVM có thể loại bỏ class metadata khi đáp ứng điều kiện reachability/lifecycle thích hợp; không có lời hứa thời điểm cụ thể.
- **Class loader leak**: code không còn cần một loader nhưng các references vô tình giữ nó reachable.
- **Resource leak**: file descriptor, socket, thread, memory buffer hoặc task không được đóng/dừng. Nó có thể liên quan hoặc độc lập với loader leak.
- **Plugin unload đúng nghĩa**: host đã ngừng nhận công việc mới, dừng/cô lập công việc cũ, xóa references trong host và các threads, đóng nguồn, và **cho GC cơ hội** thu hồi khi còn không có references khác.

Đừng dùng “đã đóng JAR” như bằng chứng “đã dỡ class”.

## 2. Thread sống là một phần quan trọng của đồ thị tham chiếu

Một `Thread` đang chạy có thể giữ nhiều loại dữ liệu liên quan plugin:

1. **TCCL**: `thread.getContextClassLoader()` giữ một reference đến một `ClassLoader`.
2. **ThreadLocal values**: dữ liệu riêng của thread, có thể là object do plugin loader định nghĩa.
3. **Stack frames/local variables**: phương thức plugin đang thực thi có locals, `this`, `Class<?>` hoặc callbacks.
4. **Runnable/Callable**: target task giữ các fields hoặc lambda captures dẫn về plugin.
5. **`UncaughtExceptionHandler` và các handler/listener tùy chỉnh**: có thể giữ object hoặc class của plugin.
6. **Inherited state**: `InheritableThreadLocal` và TCCL được truyền tại thời điểm tạo thread theo quy tắc `Thread`.
7. **Thread pool bookkeeping**: queued `Runnable`, `FutureTask`, `ScheduledFuture` hoặc executor giữ dữ liệu liên quan plugin.

Một thread nền của plugin nếu tiếp tục vòng lặp sẽ giữ execution stack và có thể giữ loader gián tiếp qua class/method đang chạy. Một thread ở trạng thái `WAITING` cũng **không hẳn đã kết thúc**: nó có thể đang chờ queue, latch, socket hoặc timer trong khi vẫn giữ các references.

**Không phải mọi object giữ `Class<?>` đều là bug:** khi host còn chủ động cần chạy plugin, giữ references là đúng. “Leak” chỉ hình thành sau khi lifecycle đã tuyên bố plugin không còn được sử dụng nhưng references không được giải phóng.

## 3. Context Class Loader (TCCL) là gì và tại sao dễ giữ loader cũ?

`Thread` có hai phương thức:

```java
ClassLoader cl = Thread.currentThread().getContextClassLoader();
Thread.currentThread().setContextClassLoader(cl);
```

Đây là snippet trong method. TCCL cho phép framework chạy trong class/library của host hỏi một loader thích hợp để tìm provider classes/resources của plugin. Ví dụ `ServiceLoader` và một số công nghệ SPI/framework có thể sử dụng context loader phù hợp để khám phá implementation.

TCCL **không nhất thiết là defining loader** của code đang chạy:

```text
Thread đang chạy method của host class
          |
          | getContextClassLoader()
          v
PluginClassLoader A

host method vẫn do host loader định nghĩa
TCCL chỉ là ngữ cảnh được chọn cho lookup tương ứng
```

Vì `Thread` lưu TCCL thành reference, khi một thread pool sống lâu hơn plugin và TCCL còn trỏ vào `PluginClassLoader V1`:

```text
GC root -> pooled Thread -> contextClassLoader -> PluginLoader V1
```

Loader V1 tiếp tục reachable. Ngay cả khi chưa có `ThreadLocal`, **một mình TCCL** có thể đủ để giữ loader.

### TCCL được kế thừa vào thời điểm tạo thread

Theo Java SE 21 `Thread`, context loader mặc định thường **được kế thừa từ parent thread khi child Thread được tạo**. Điều đó có nghĩa:

- Nếu host đặt TCCL thành plugin loader khi tạo một thread pool, pool workers có thể **kế thừa nhầm** loader của plugin.
- Sau khi host khôi phục TCCL ở thread tạo pool, TCCL trong các workers đã được tạo **không tự thay đổi theo**.
- Một thread pool có thể tái sử dụng cùng platform thread qua nhiều tasks. TCCL do task trước để lại có thể trở thành context của task kế tiếp.
- Không nên dựa vào việc “main thread đã set lại loader” để kết luận pool threads đã được dọn.

Để tránh tạo workers với context loader sai, cấu hình `ThreadFactory` thích hợp và luôn phục hồi TCCL trong **chính worker thread** sau khi chạy task.

### Mẫu đổi TCCL có try/finally

```java
Thread current = Thread.currentThread();
ClassLoader old = current.getContextClassLoader();

try {
    current.setContextClassLoader(pluginLoader);
    // Gọi plugin / ServiceLoader / framework API cần ngữ cảnh này.
} finally {
    current.setContextClassLoader(old);
}
```

Đây là snippet cần biến `pluginLoader` và đoạn gọi plugin thực tế. TCCL restore phải nằm trong `finally` vì plugin có thể ném exception. Nếu chỉ `current.setContextClassLoader(pluginLoader)` mà quên khôi phục, một pooled worker sau khi task kết thúc vẫn có thể giữ loader.

**Đừng tự khôi phục về `null` trừ khi `null` thực sự là giá trị trước đó.** `null` có semantics “dùng system class loader (hoặc bootstrap nếu cần)” theo API và có thể không tương đương context ban đầu của framework.

## 4. ThreadLocal giữ dữ liệu ra sao?

`ThreadLocal<T>` là một cách lưu **một giá trị riêng trên mỗi thread**. Dù biến khai báo ở class của host, chính worker thread giữ value riêng:

```java
private static final ThreadLocal<Object> CURRENT_PLUGIN =
        new ThreadLocal<>();
```

`CURRENT_PLUGIN.set(value)` lưu một value cho **thread đang chạy**. `CURRENT_PLUGIN.get()` trong thread khác không trả cùng value chỉ vì dùng cùng `ThreadLocal` key.

Nếu value là object có class do plugin loader định nghĩa:

```text
Host static ThreadLocal key
      |
Live pooled thread
      '--> ThreadLocal value: plugin.Instance
                      |
                      v
                Class<?> of plugin.Instance
                      |
                      v
              PluginClassLoader V1
```

Một key `ThreadLocal` đặt trong class host sống lâu và value do plugin tạo có thể giữ loader cũ cho đến khi worker gọi `remove()`, thay value, hoặc thread chết (và không còn references khác).

API chính:

- `set(value)`: ghi value của **thread hiện tại**.
- `get()`: lấy value của **thread hiện tại**; có thể dùng `initialValue()` tạo value.
- `remove()`: xóa value thuộc **thread hiện tại**.

Đặc biệt: **gọi `CURRENT_PLUGIN.remove()` ở main thread không xóa bản value mà một pool worker đang giữ**. Cleanup cần chạy trên đúng worker từng set nó, hoặc có một chính sách thay thế/hủy thread tương ứng.

### “ThreadLocal có weak key nên chắc không leak” là nhầm

Trong các triển khai HotSpot/OpenJDK thường gặp, `ThreadLocalMap` có keys là weak references tới `ThreadLocal` instances nhưng **giá trị có thể được giữ mạnh**. Nếu key bị GC, entry có thể trở thành stale trong map, và value không nhất thiết được thu hồi ngay cho đến khi map cleanup thực hiện.

Vì thế:

- Key là weak không bảo đảm value plugin được GC ngay.
- Mất reference tới biến `ThreadLocal` không thay thế `remove()`.
- Đừng tạo `new ThreadLocal<>()` cho mỗi request mà không dọn trên long-lived worker.
- Đừng dựa vào việc thread pool “sẽ làm sạch mọi ThreadLocal sau mỗi task” trừ khi **executor/framework cụ thể có hợp đồng làm điều đó**.

Cấu trúc nội bộ `ThreadLocalMap` là implementation detail, không phải API để ứng dụng truy cập hoặc chỉnh sửa bằng reflection. **Biện pháp đúng** là thiết kế ownership, dùng `try/finally`/`remove()`, và để worker kết thúc khi plugin được gỡ nếu còn state không kiểm soát.

## 5. Kết thúc task khác kết thúc thread

`ExecutorService.submit(Runnable)` trả về `Future` có thể `isDone() == true`, nhưng điều này **không** đồng nghĩa executor đã dừng hoặc worker thread đã chết.

Ví dụ:

```text
t0: Thread pool tạo worker-1
t1: worker-1 chạy Task A của plugin-v1
t2: Task A hoàn thành, Future A = done
t3: worker-1 quay về đợi trong task queue
t4: Task B chạy trên cùng worker-1
t5: pool shutdown, workers mới có thể kết thúc theo quy tắc executor
```

Nếu Task A để lại TCCL hoặc `ThreadLocal` của plugin-v1, worker-1 có thể mang nó từ t2 sang t4, thậm chí Task B thuộc một plugin khác.

Có ba trạng thái phải hỏi riêng:

| Trạng thái | Ý nghĩa | Điều không thể suy ra |
| --- | --- | --- |
| `future.isDone()` | Task đã hoàn thành/cancelled theo `Future` contract. | Thread pool đã chết hoặc TCCL/TL đã được dọn. |
| `executor.isShutdown()` | Executor không tiếp nhận tasks mới. | Mọi task đang chạy đã kết thúc. |
| `executor.isTerminated()` | Sau shutdown, mọi tasks hoàn tất theo contract executor. | Không còn **bất kỳ** reference plugin trong host caches/TCCL threads ngoài pool. |
| `thread.isAlive()` | Thread đã start và chưa kết thúc. | Nó đang chạy tác vụ hữu ích; có thể đang chờ queue. |
| `loader.close()` | URLClassLoader đóng các resources/nguồn tải của nó theo API. | Class đã unload, thread/task tự ngừng, hoặc mọi references bị xóa. |

**`shutdownNow()` không phải “kill tất cả code”.** Nó thường cố dừng bằng interrupt và trả tasks chưa chạy; code có thể bỏ qua interrupt, mắc I/O không interruptible hoặc giữ references khác. Với plugin code tùy ý không tin cậy, chỉ cố interrupt thread trong cùng JVM không phải giới hạn an toàn tương đương kill một process.

## 6. Ví dụ Java 17+ kiểm chứng TCCL và ThreadLocal retention

Ví dụ này giữ nguyên một **worker thread trong pool** sau khi task ban đầu đã hoàn thành. Worker chứa hai references tới loader plugin: TCCL và một `ThreadLocal<Object>` có value class do plugin loader định nghĩa.

Sau đó, host **gửi cleanup task vào đúng worker**, gọi `ThreadLocal.remove()` và restore TCCL về baseline đã biết. Cuối cùng mới shutdown pool và close URL loader.

Demo **không gọi `System.gc()`**, không yêu cầu suy ra class unloading xảy ra ngay. Nó kiểm tra được **tham chiếu đang tồn tại/được gỡ** và lifecycle, điều này có kết quả xác định.

### Cấu trúc source

```text
src/
  plugin/
    sample/
      plugin/
        Payload.java
  app/
    RetainedLoaderDemo.java
plugin-classes/  (javac tạo)
app-classes/     (javac tạo)
```

**File `src/plugin/sample/plugin/Payload.java`:**

```java
package sample.plugin;

public final class Payload {
    public String label() {
        return "plugin-value";
    }
}
```

**File `src/app/RetainedLoaderDemo.java`:**

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RetainedLoaderDemo {
    private static final ThreadLocal<Object> HOLDER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        URL source = Path.of("plugin-classes").toAbsolutePath()
                .toUri().toURL();
        Class<?> loaded;

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{source}, ClassLoader.getPlatformClassLoader())) {
            loaded = Class.forName(
                    "sample.plugin.Payload", true, loader);
            Object value = loaded.getDeclaredConstructor().newInstance();

            AtomicReference<Thread> worker = new AtomicReference<>();
            ExecutorService pool =
                    Executors.newSingleThreadExecutor(action -> {
                        Thread thread =
                                new Thread(action, "reusable-worker");
                        thread.setContextClassLoader(
                                ClassLoader.getPlatformClassLoader());
                        return thread;
                    });

            try {
                pool.submit(() -> {
                    worker.set(Thread.currentThread());
                    Thread.currentThread()
                            .setContextClassLoader(loader);
                    HOLDER.set(value);
                }).get();

                System.out.println("defined by plugin loader: "
                        + (loaded.getClassLoader() == loader));
                System.out.println("worker stays alive: "
                        + worker.get().isAlive());
                System.out.println("TCCL retains loader: "
                        + pool.submit(
                                () -> Thread.currentThread()
                                        .getContextClassLoader()
                                        == loader).get());
                System.out.println("ThreadLocal retains plugin value: "
                        + pool.submit(
                                () -> HOLDER.get() != null
                                        && HOLDER.get().getClass()
                                           .getClassLoader() == loader).get());
            } finally {
                pool.submit(() -> {
                    HOLDER.remove();
                    Thread.currentThread().setContextClassLoader(
                            ClassLoader.getPlatformClassLoader());
                }).get();

                System.out.println("TCCL restored: "
                        + pool.submit(
                                () -> Thread.currentThread()
                                        .getContextClassLoader()
                                        == ClassLoader.getPlatformClassLoader()).get());
                System.out.println("ThreadLocal cleared: "
                        + pool.submit(() -> HOLDER.get() == null).get());

                pool.shutdown();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            }
        }

        System.out.println("class usable after close: "
                + loaded.getName().equals("sample.plugin.Payload"));
    }
}
```

### Biên dịch và chạy

Đứng trong thư mục gốc chứa `src`:

```bash
javac --release 17 -d plugin-classes src/plugin/sample/plugin/Payload.java
javac --release 17 -d app-classes src/app/RetainedLoaderDemo.java
java -cp app-classes RetainedLoaderDemo
```

Code tương thích Java 17; `ClassLoader.getPlatformClassLoader()` có từ Java 9 và `URLClassLoader` hỗ trợ try-with-resources. Không đưa `plugin-classes` vào application classpath: class `sample.plugin.Payload` phải do URL loader trong demo định nghĩa.

**Stdout chính xác đã được kiểm chứng trên OpenJDK 24 sau khi biên dịch với `javac --release 17`:**

```text
defined by plugin loader: true
worker stays alive: true
TCCL retains loader: true
ThreadLocal retains plugin value: true
TCCL restored: true
ThreadLocal cleared: true
class usable after close: true
```

### Phân tích kết quả

1. `defined by plugin loader: true`: `Payload` được loader URL mới định nghĩa thay vì application/platform loader.
2. `worker stays alive: true`: task đầu tiên đã kết thúc và `Future.get()` trả về, nhưng `newSingleThreadExecutor` vẫn giữ worker để dùng cho task kế tiếp.
3. `TCCL retains loader: true`: chính worker thread có `contextClassLoader == loader` **sau** task trước. Đây là một reference mạnh.
4. `ThreadLocal retains plugin value: true`: bản value `HOLDER` thuộc worker thread là object của plugin. Không gọi `remove` thì nó có thể đi theo worker qua các tasks tiếp theo.
5. `TCCL restored: true`: cleanup task chạy trên cùng worker và reset loader về platform baseline do `ThreadFactory` đã thiết lập. Trong ứng dụng thật, nên lưu `old = thread.getContextClassLoader()` rồi restore chính giá trị ấy.
6. `ThreadLocal cleared: true`: worker không còn value do task đầu đặt. Host **không** gọi `HOLDER.remove()` trong main rồi hy vọng dọn hộ worker.
7. `class usable after close: true`: `URLClassLoader.close()` không “xóa” Class object đã được định nghĩa; biến `loaded` ở host vẫn giữ reference. Đây **không phải test GC**. Class chỉ có thể được thu hồi sau khi không còn các references ngăn thu hồi và GC thực sự xử lý.

**Phạm vi kiểm thử:** Ví dụ dùng single-thread executor, nên các tasks inspection/cleanup được thực thi trên **cùng một worker**. Với fixed pool có nhiều workers, gửi một task cleanup không đảm bảo nó chạy trên tất cả workers đã từng xử lý plugin. Cần quản lý state theo từng thread/task, hoặc shutdown và thay hẳn pool thuộc plugin.

**Cẩn trọng production:** Ví dụ tập trung quan sát nên cleanup task đơn giản và `get()` có thể block theo contract. Trong hệ thống thực phải bảo đảm cleanup/shutdown diễn ra kể cả khi một task khác ném exception, bị hủy, executor từ chối submit hoặc worker kẹt; không xem một `finally` chứa các thao tác có thể lỗi như giải pháp tuyệt đối cho mọi tình huống.

## 7. Mẫu phục hồi TCCL + ThreadLocal đúng trong từng task

Với một worker pool **do host sở hữu**, nên bọc mọi lần thực thi plugin:

```java
// Snippet trong Runnable có các biến pluginLoader,
// pluginContext và HOST_CONTEXT do ứng dụng cung cấp:
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    HOST_CONTEXT.set(pluginContext);

    // invokePlugin();
} finally {
    HOST_CONTEXT.remove();
    thread.setContextClassLoader(previous);
}
```

`HOST_CONTEXT` nên có kiểu `ThreadLocal<PluginContext>` hoặc kiểu phù hợp; đây là **snippet**, không phải chương trình chạy độc lập. Điều quan trọng nhất là `remove()` và TCCL restore nằm **trong task, trên chính worker**.

Nếu plugin hoặc framework có **nhiều ThreadLocal khác** do nó tự tạo, host chỉ dọn `HOST_CONTEXT` là **chưa đủ**. Plugin/framework phải có lifecycle cleanup riêng hoặc được chạy trong pool chuyên dụng mà bạn có thể shutdown, chờ hoàn tất và loại bỏ toàn bộ threads.

### Tác vụ lồng nhau / TCCL stack

Một task có thể thay TCCL nhiều lần:

```text
Host TCCL H
   -> plugin A TCCL A
      -> thư viện tải provider B, TCCL B
      <- restore A
   <- restore H
```

Mỗi tầng phải **restore giá trị cũ của nó**, không hardcode toàn hệ thống về system loader. Nếu một tầng quên restore, code phía ngoài có thể hoạt động sai hoặc giữ B lâu hơn mong muốn.

Khi framework dùng `ServiceLoader.load(SomeService.class)` (overload sử dụng context loader phù hợp theo API), TCCL sai có thể khiến tìm nhầm provider version hoặc giữ provider loader cũ; không chỉ là vấn đề bộ nhớ.

## 8. ThreadLocal trong thread pool: hai dạng retention phổ biến

### Dạng A — ThreadLocal key đặt ở host, value do plugin tạo

```text
GC root -> pooled Thread
                 |
                 '--> ThreadLocal value (plugin instance)
                                |
                                '--> plugin Class<?> / loader
```

Đây là dạng dễ xảy ra khi host lưu `currentTenant`, `pluginContext` hoặc transaction context bằng `ThreadLocal` và vô tình ghi object thuộc plugin. Key trong host có thể sống suốt JVM, nên value còn trong worker có thể giữ plugin rất lâu.

### Dạng B — ThreadLocal key do plugin tạo

Plugin khai báo:

```java
package sample.plugin;

public final class PluginContext {
    static final ThreadLocal<Object> CURRENT = new ThreadLocal<>();
}
```

Đây là một class Java hợp lệ nếu đặt đúng file `PluginContext.java`. Nếu plugin code gọi `CURRENT.set(...)` trong host pool worker nhưng không gọi `CURRENT.remove()` trước khi trả thread:

- Worker giữ per-thread entry có thể chứa object/plugin metadata.
- Static field và bản `ThreadLocal` key thuộc class được plugin loader định nghĩa.
- Kể cả khi static reference/key có thể trở nên unreachable, một **stale ThreadLocalMap entry** trong các JDK implementations vẫn có thể giữ strong value tới khi cleanup.

Chỉ đặt `CURRENT = null` hoặc xóa reference tới plugin không đảm bảo dọn value đã lưu trong mọi worker threads. Không nên dùng reflection mở `java.lang.Thread.threadLocals` để xóa “thủ công”: internal layout không phải public API, JPMS có encapsulation, và truy cập/sửa xuyên thread dễ gây race hoặc phá framework state.

### InheritableThreadLocal và threads sinh từ plugin

`InheritableThreadLocal` có thể truyền giá trị từ parent sang child **khi tạo thread**. TCCL cũng có tính kế thừa theo Java `Thread` API. Nếu plugin tạo thread con khi TCCL/value đang trỏ vào plugin, child có thể giữ chúng sau khi parent hoàn thành.

**Executor reuse khác inheritance:** InheritableThreadLocal không được thiết kế như “per task context propagation” an toàn cho thread pool. Workers thường được tạo một lần rồi nhận nhiều tasks; value được thừa hưởng lúc tạo không tự cập nhật cho từng request. Nếu framework có context propagation chuyên biệt, đọc hợp đồng và cleanup của chính framework.

## 9. Tác vụ chưa dừng: không chỉ là ThreadLocal

Các đường retention khác thường thấy:

| Nguồn giữ tham chiếu | Đường tới loader | Cách dọn phù hợp |
| --- | --- | --- |
| `new Thread(pluginRunnable).start()` còn chạy | Thread → Runnable/stack → plugin code hoặc object. | Yêu cầu dừng hợp tác, cancel I/O, `join`, kiểm tra còn sống. |
| `ScheduledExecutorService.scheduleAtFixedRate` | Executor → recurring `ScheduledFuture`/Runnable → plugin closure. | `cancel` scheduled task, shutdown executor đúng ownership. |
| `ExecutorService` queue | Queue → `Runnable/FutureTask` → plugin captures. | Ngừng tiếp nhận, cancel/purge tasks, drain/terminate theo API. |
| `CompletableFuture` chưa hoàn tất | Completion chain/lambda → plugin values/functions. | Complete/cancel theo contract, xóa references chain, xử lý work nguồn. |
| Event bus/listener registry của host | Host singleton → listener object do plugin tạo. | `unsubscribe/unregister` listeners/callbacks. |
| Shutdown hook đã đăng ký | JVM hook registry → plugin Thread/Runnable. | `removeShutdownHook` theo lifecycle khi hợp lệ. |
| `Timer` / background scheduler | Timer thread và tasks giữ callbacks. | `Timer.cancel()`, xóa task refs, dùng scheduler chuẩn khi có thể. |
| Framework cache / SPI provider | Cache hoặc service registry → plugin `Class<?>`/instance. | Evict đúng cache, unregister providers, clear TCCL. |
| Static field của host | Host class → plugin instance / `Class<?>`. | Bỏ reference/callback, quản lý ownership. |
| Open sockets/streams | Resource và async callbacks vẫn hoạt động. | Close/cancel operations, unregister handlers. |

Một plugin có thể giữ thread chạy mã trong plugin package, nhưng host chưa bao giờ lưu plugin loader vào `ThreadLocal`. Nó vẫn có thể giữ class metadata qua stack và runnable. Vì thế **chỉ quét TCCL/ThreadLocal là chưa đủ** nếu plugin tạo tasks và callbacks khác.

### Interrupted không đồng nghĩa terminated

`Future.cancel(true)` hoặc `ExecutorService.shutdownNow()` thường chỉ cố **interrupt** thread chạy; Java không đảm bảo task hợp tác. Một task có thể:

- Bắt `InterruptedException` rồi tiếp tục vòng lặp.
- Bị kẹt I/O hoặc lời gọi bên thứ ba không có deadline.
- Giữ monitor trong deadlock.
- Tạo thread phụ và bỏ quên nó.
- Đăng ký callback/lambda vào global host singleton.

Vì vậy shutdown cần có **deadline, xác minh hoàn tất và chính sách xử lý thất bại**. Không dùng `Thread.stop()` như cách kill class loader; nó đã bị deprecated/không an toàn vì có thể phá invariants và monitors.

## 10. Close URLClassLoader không ép JVM unload Class

`URLClassLoader.close()` xử lý resource lookup và JAR handles thuộc loader, **không dừng worker threads**, xóa `ThreadLocal`, phục hồi TCCL hay hủy pending tasks. Class đã load vẫn có thể dùng sau close khi còn references.

Muốn retiring thread-related state, thực hiện cleanup tại đúng thread/executor owner rồi mới release generation; log unload chỉ là bước quan sát tùy JVM.

## 11. Một quy trình unload plugin có trách nhiệm

Plugin manager nên có trạng thái rõ ràng, ví dụ:

```text
ACTIVE -> QUIESCING -> STOPPING -> CLEANING -> CLOSED -> RETIRED
```

Trình tự tham khảo:

1. **Chặn việc gọi mới**: gỡ plugin khỏi route/dispatcher và từ chối tasks mới, tránh concurrent unload.
2. **Ngừng scheduled/background work**: cancel periodic tasks, ngừng subscription, dừng task producer.
3. **Chờ hoặc cancel tasks đang chạy**: dùng deadlines hợp lý, ghi nhận tasks chưa kết thúc, không tuyên bố thành công khi chúng còn sống.
4. **Chạy cleanup trong worker**: restore TCCL và gọi `ThreadLocal.remove` trong chính thread đã set; cleanup framework-specific request contexts.
5. **Đóng tài nguyên plugin**: streams, sockets, watchers, database handles, files, timers, JAR handles.
6. **Dừng plugin-owned executors/threads**: `shutdown`, `awaitTermination`; khi thất bại dùng `shutdownNow` theo contract, tiếp tục xác minh và phân loại blockers.
7. **Unregister references từ host**: listeners, MBeans, caches, service registries, logging appenders, callbacks, static maps, shutdown hooks.
8. **Close class loader** nếu API hỗ trợ; tránh thực hiện khi class loading khác vẫn đang diễn ra.
9. **Bỏ host references**: manager state không còn giữ plugin instance, `Class<?>`, loader, lambda hoặc `Future` của generation cũ.
10. **Kiểm tra bằng công cụ**: kiểm tra thread dump/heap dump/classloader statistics theo thời gian; không khẳng định GC tức thì.

Có thể chia cleanup thành các bước idempotent, ghi log lỗi theo từng bước và vẫn cố dọn các phần còn lại. Với platform thread/task không hợp tác, thực tế có thể cần **restart worker process hoặc host process** nếu không thể dừng an toàn.

### Shutdown executor chuẩn (minh họa)

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

// Snippet trong method có executor đã biết:
executor.shutdown();
try {
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            System.err.println("Tasks remain active");
        }
    }
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Đây là code minh họa xử lý lifecycle; `executor` do caller quản lý. Nó không tự giải phóng mọi `ThreadLocal` nếu tasks đã ghi chúng mà không remove, và `shutdownNow` không đảm bảo mọi task dừng. Với executor dùng chung của host, **không gọi `shutdown`** chỉ để unload một plugin; bạn cần cơ chế hủy/cleanup **các tasks thuộc plugin ấy** mà không làm hỏng các plugin khác.

## 12. ThreadFactory và việc ngăn kế thừa loader ngoài ý muốn

Khi tạo thread pool chung, một `ThreadFactory` đặt TCCL baseline rõ ràng giúp tránh kế thừa nhầm loader tại thời điểm workers được tạo:

```java
import java.util.concurrent.ThreadFactory;

ThreadFactory factory = task -> {
    Thread thread = new Thread(task, "host-shared-worker");
    thread.setContextClassLoader(
            ClassLoader.getPlatformClassLoader());
    return thread;
};
```

Đây là snippet cần dùng `factory` khi tạo executor; không phải class có `main`. Chọn loader baseline theo kiến trúc: `ClassLoader.getPlatformClassLoader()` chỉ phù hợp nếu công việc không cần host application classes từ TCCL; nhiều frameworks cần system/application loader. Một factory hardcode platform loader có thể khiến plugin providers của host không tìm thấy — **đây không phải lựa chọn mặc định cho mọi ứng dụng**.

Quan trọng hơn, dù factory đã đặt baseline, **mỗi task vẫn phải restore TCCL** sau khi tạm đổi sang plugin loader. Một task có thể làm bẩn state của pooled thread bất kể initial context là gì.

Với thread pools được framework quản lý, đừng tự tạo thread hoặc thay ThreadFactory không theo contract; xem cơ chế context propagation/lifecycle của container.

## 13. Chẩn đoán ClassLoader leak và retention graph

Đừng dùng `System.gc()` trong test rồi in “GC đã thu hồi loader” như một khẳng định có thể lặp lại. `System.gc()` chỉ là **request**, JVM có thể không thực hiện GC ngay hoặc có references khác không được nhìn thấy.

### Các công cụ nên dùng

- **Thread dump** (`jcmd <pid> Thread.print` hay công cụ tương đương): xem tên threads, stacks, WAITING/BLOCKED, plugin method frames, pool workers chưa dừng. Cần phân biệt một thread idle sống lâu hợp lệ với leak giữ TCCL/value cũ.
- **JFR (Java Flight Recorder):** xem hoạt động threads, allocations, class loading/unloading và các sự kiện liên quan theo cấu hình JDK/JFR; diễn giải cùng heap evidence.
- **Heap dump + Eclipse MAT/VisualVM:** tìm instance class loader cũ, retained size, **path to GC roots**, `Thread.contextClassLoader`, `ThreadLocalMap` entries, `FutureTask`, listener registry.
- **`jcmd <pid> VM.classloader_stats`**: kiểm tra thống kê loader/classes nếu lệnh được JVM hỗ trợ; thống kê không trực tiếp là graph root-cause.
- **Native Memory Tracking/metaspace metrics** nếu phù hợp: quan sát memory theo vùng, không mặc định growth chỉ do class loaders.

Lệnh tham khảo (đổi PID thành process thực):

```bash
jcmd <pid> Thread.print
jcmd <pid> VM.classloader_stats
jcmd <pid> GC.heap_dump /tmp/plugin-heap.hprof
```

Các dòng trên là **mẫu cú pháp công cụ**, không phải script chạy nguyên văn: `<pid>` là placeholder, tùy quyền attach của JVM và dung lượng disk. Heap dump có thể chứa **dữ liệu nhạy cảm**, phải xử lý như artifact bảo mật và không đưa công khai lên mạng.

### Đọc “path to GC roots” đúng cách

Ví dụ một đường giữ loader:

```text
GC Root
  -> Thread "host-executor-3"
  -> contextClassLoader
  -> PluginClassLoader#v17
```

Một đường khác:

```text
GC Root
  -> Thread "host-executor-3"
  -> threadLocals / ThreadLocalMap entry
  -> value: PluginContext
  -> class sample.plugin.PluginContext
  -> defining PluginClassLoader#v17
```

Nếu path dẫn qua `CompletableFuture`, `ScheduledThreadPoolExecutor` queue, `ServiceLoader` cache hoặc host static map, phải sửa **chủ sở hữu reference đó**, không chỉ TCCL.

**Đừng coi số lượng class loaders tăng một vài chu kỳ là chứng cứ tự động của leak.** GC và class unloading không ngay lập tức, JIT/runtime có lifecycle phức tạp. Cần đo sau những vòng load/unload có kiểm soát và xem references còn tồn tại theo heap snapshots.

### WeakReference kiểm tra thế nào?

Có thể giữ `WeakReference<ClassLoader>` trong bài test để quan sát loader có biến mất sau khi host bỏ strong references không. Nhưng:

- `ref.get() != null` tại một thời điểm **không tự chứng minh leak**.
- `ref.get() == null` cũng không khẳng định mọi resources/tasks được dọn đúng.
- Không giữ loader trong biến local, lambda hoặc debugger inspection khi test reachability.
- Không đòi kết quả `null` sau một số vòng `System.gc()` cố định, vì đây là kiểm tra có thể flaky.

Cách đáng tin hơn để tìm nguyên nhân là heap dump, GC roots và kiểm tra lifecycle được xây thành unit/integration tests cho từng registration/unregistration.

## 14. Trường hợp Java 21+ virtual threads có khác không?

Java 21 giới thiệu virtual threads dạng production, nhưng bài này ưu tiên chạy được trên Java 17. Cần phân biệt:

- Virtual thread cũng có `ThreadLocal` và context class loader semantics tương ứng, nhưng lifecycle thường **ngắn theo task** hơn pooled platform threads truyền thống.
- Nếu mỗi task dùng **một virtual thread riêng** rồi thread đó thực sự kết thúc, giá trị thread-local thường không bị giữ lâu bởi **chính thread đã kết thúc**.
- Tuy vậy, plugin object vẫn có thể bị giữ bởi **task chưa xong**, scheduler/framework references, `Future`, callback hoặc thread khác.
- Không xem “dùng virtual threads” là phép chữa mọi loader leak. Context propagation/inheritance và plugin tasks không ngừng vẫn cần phân tích.

Đừng trộn semantics của `Executors.newVirtualThreadPerTaskExecutor()` (Java 21) vào ví dụ `javac --release 17`; đó là API không có trong Java 17.

## 15. Các lỗi thường gặp

| Sai lầm | Hậu quả | Giải pháp |
| --- | --- | --- |
| Task plugin kết thúc nên pool worker chắc đã chết | Worker còn sống và giữ TCCL/ThreadLocal cũ. | Phân biệt task lifecycle và pool/thread lifecycle. |
| `ThreadLocal.remove()` trong main dọn value của worker | Không có tác dụng trên value thuộc worker. | Remove trong chính worker/task. |
| `System.setProperty("java.class.path", ...)` để “thay plugin” | Không cập nhật tự động loader/runtime classes. | Quản lý loader generation và dependency graph. |
| Set TCCL plugin mà không restore | Loader cũ tiếp tục được thread tham chiếu. | `try/finally` restore TCCL cũ. |
| Tạo pool khi TCCL là plugin loader | Worker mới có thể kế thừa loader plugin. | Dùng ThreadFactory/context baseline phù hợp. |
| Tin rằng `ThreadLocal` weak key không giữ value | Value có thể được giữ mạnh trong map. | Explicit `remove()` và worker lifecycle. |
| Gọi `URLClassLoader.close()` trước khi dừng tasks | Loader đóng nhưng task vẫn chạy hoặc nạp class/resource mới lỗi. | Quiesce, stop, cleanup, rồi close. |
| `Future.cancel(true)` chắc chắn giết task | Interrupt cần task hợp tác. | Deadlines, monitoring, process isolation nếu code không tin cậy. |
| Chỉ cleanup một task trong fixed pool nhiều workers | Các worker khác vẫn có TL/TCCL value. | Per-task cleanup hoặc shutdown pool thế hệ plugin. |
| Giữ `Class<?>`/plugin instance trong host cache | Defining loader vẫn reachable. | Evict caches và unregister listeners. |
| Dùng `System.gc()` như bằng chứng unload ngay | Không có timing guarantee. | Heap dump/path-to-roots và thống kê theo thời gian. |
| Không giới hạn `ScheduledExecutorService` recurring tasks | Scheduled callbacks có thể giữ plugin rất lâu. | Cancel futures, shutdown đúng ownership. |
| Cố dùng `Thread.stop()` để dừng plugin | Có thể làm hỏng invariant/monitor; API không an toàn. | Cooperative cancellation hoặc worker process riêng. |

## 16. Bài tập tự kiểm tra

1. **Một task có `Future.isDone() == true`, pool worker có thể còn sống không?** Có. Executor có thể giữ worker chờ task mới.
2. **Vì sao một TCCL vẫn trỏ vào plugin loader cũ có thể ngăn thu hồi loader?** Thread sống giữ reference mạnh tới loader.
3. **Một host static `ThreadLocal<Object>` giữ value plugin trên worker, main gọi `remove()` có xóa value ấy không?** Không; `remove` chỉ tác động current thread.
4. **`ThreadLocalMap` có weak keys đồng nghĩa mọi values được GC ngay khi key mất?** Không; value có thể còn giữ mạnh cho đến cleanup.
5. **`URLClassLoader.close()` có dừng background thread hoặc hủy static host registry không?** Không.
6. **Một plugin vẫn đang thực thi trên stack có đủ điều kiện bị unload chắc chắn không?** Không thể coi như vậy; stack frames/objects/class metadata của tác vụ có thể giữ loader reachable.
7. **Phải restore TCCL về system loader mọi lúc?** Không. Restore **giá trị trước khi tạm đổi**, không hardcode.
8. **ThreadFactory đã đặt baseline TCCL thì các task có cần finally restore không?** Có; worker có thể được tái sử dụng.
9. **Một cleanup task có dọn tất cả TL values trong fixed pool 10 threads không?** Không có bảo đảm; nó chỉ chạy trên một worker cụ thể.
10. **Vì sao `shutdownNow()` chưa đủ để xác nhận plugin đã dừng?** Vì interrupt có thể không được task hợp tác, và queued/callback references còn tồn tại.
11. **Hai kết quả của demo có chứng minh GC đã unload class không?** Không. Demo chỉ kiểm tra references TCCL/TL và class tiếp tục dùng sau close.
12. **Cần làm gì khi plugin không đáng tin và không thể dừng thread hợp tác?** Đặt plugin ở process có quota/timeout và quyền OS hạn chế, có thể terminate process nếu không hợp tác.

## 17. Tài liệu chính thức

- [Java SE 21 — Thread](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): `getContextClassLoader`, `setContextClassLoader`, context inheritance, thread lifecycle.
- [Java SE 21 — ThreadLocal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html): per-thread values, `set`, `get`, `remove`.
- [Java SE 21 — InheritableThreadLocal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html): inheritance tại thời điểm tạo thread.
- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): semantics của `close` và khả năng dùng lớp đã load.
- [Java SE 21 — ExecutorService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html): `shutdown`, `shutdownNow` và `awaitTermination`.
- [Java SE 21 — Future](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html): `isDone` và `cancel`.
- [Java SE 21 — ServiceLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html): chọn class loader cho provider lookup.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): defining loader/class identity và delegation.
- [Java SE 21 — jcmd](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html): thread dumps, VM classloader stats và heap dump tùy command/JVM.
- [Oracle Java SE 21 — Core Libraries, Thread-Local Variables](https://docs.oracle.com/en/java/javase/21/core/java-core-libraries-developer-guide.pdf): cơ chế ThreadLocal.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-unloading-and-memory-008">Listener, registry, ServiceLoader cache và nguyên nhân giữ plugin</a>

<details>
<summary>Xem chi tiết</summary>

# Listener, registry, ServiceLoader cache và nguyên nhân giữ plugin trong bộ nhớ

## 1. Vấn đề: plugin đã dừng nhưng ClassLoader vẫn còn sống

Một hệ Java plugin thường tạo một **ClassLoader riêng** để nạp implementation và dependencies. Khi plugin bị gỡ (unload/undeploy/redeploy), host có thể ngừng phục vụ request và gọi `URLClassLoader.close()`, nhưng bộ nhớ dành cho plugin **không nhất thiết được giải phóng**.

Nguyên nhân thường không phải JVM “quên unload class”, mà là **một hoặc nhiều tham chiếu còn giữ object/plugin class/ClassLoader ở trạng thái reachable**:

- Listener của plugin vẫn được đăng ký trong registry sống lâu của host.
- Plugin instance còn nằm trong list/map singleton, event bus, callback table.
- Một `ServiceLoader` được lưu trong cache host vẫn giữ loader/provider metadata và các instance đã được duyệt.
- Thread pool worker giữ `Thread Context ClassLoader (TCCL)` trỏ vào plugin loader.
- Executor, timer, thread, shutdown hook hoặc `ThreadLocal` vẫn giữ object/class thuộc plugin.
- Cache chứa `Class<?>`, reflection `Method`, `Constructor`, `MethodHandle`, lambda/proxy hoặc object được định nghĩa bởi plugin loader.
- Một resource khác (JDBC driver, MBean, logger/handler, scheduled job...) còn được đăng ký với một thành phần sống lâu.

**Trọng tâm bài học:** hiểu **đường tham chiếu mạnh** (strong reference path) từ GC roots đến plugin, phân biệt **stop**, **close**, **dereference** và **class unloading**, rồi thiết kế quy trình cleanup có thể xác minh.

Bài viết phù hợp Java 17+, có ví dụ chạy được về **listener registry + ServiceLoader cache**, ví dụ giới hạn của `reload()`, hướng nhận diện leak bằng JVM tooling và checklist redeploy an toàn.

## 2. Bốn khái niệm khác nhau của vòng đời

**Stop plugin** ngừng xử lý và hủy đăng ký nghiệp vụ; **`URLClassLoader.close()`** đóng class/resource source và JAR handles do loader quản lý; **GC eligibility** phụ thuộc loader generation còn reachable từ roots hay không; **class unloading** là quyết định của JVM khi điều kiện phù hợp.

Đối với `ServiceLoader`, gọi `reload()` làm sạch provider cache của chính `ServiceLoader` đó nhưng không xóa các provider instances được giữ ở host registry khác. Bài này tập trung vào ownership của listener/registry/provider caches.

## 3. ClassLoader, Class và object giữ nhau như thế nào?

Plugin instance, `Class<?>`, listener object hoặc ServiceLoader provider vẫn có thể dẫn về runtime type và **defining loader** của plugin. Ví dụ `host event bus → listener instance → runtime class → plugin loader`.

Vì JVM định danh class thông thường bằng `(binary name, defining loader)`, hai plugin versions có thể giữ hai runtime classes cùng tên khác nhau. Chỉ khi các registrations và references còn được host giữ bị xóa, defining loader cũ mới có cơ hội được reclaim.

## 4. Listener và callback registry là nguồn giữ plugin điển hình

Một event bus thường có API:

```java
public interface Listener {
    void onEvent(String message);
}
```

Đây là interface mẫu có thể đặt trong `Listener.java`. Host có thể giữ:

```java
// Trích đoạn: event bus thuộc một class do loader host định nghĩa.
private static final java.util.List<Listener> LISTENERS =
        new java.util.concurrent.CopyOnWriteArrayList<>();

public static void register(Listener l) {
    LISTENERS.add(l);
}

public static void unregister(Listener l) {
    LISTENERS.remove(l);
}
```

Nếu plugin gọi `register(new PluginListener())` mà không bao giờ gọi `unregister`, `LISTENERS` giữ object plugin. Khi host `ClassLoader` sống suốt JVM, registry thường vẫn reachable qua class/field của host.

**Cơ chế leak không phụ thuộc listener có đang nhận event hay không.** Chỉ cần reference vẫn nằm trong collection.

### Lambda và method references cũng là callbacks

Ví dụ:

```java
// Trích đoạn: eventBus là đối tượng host sống lâu, plugin là plugin instance.
eventBus.subscribe(event -> plugin.handle(event));
```

Lambda có thể capture `plugin` trong object callback. Host chỉ thấy một `Consumer<Event>`/interface chung, nhưng callback giữ object plugin phía sau. Tên class runtime của lambda có thể không giống `PluginListener`, khiến heap dump khó đọc hơn.

Tương tự, `plugin::handle` là method reference có thể giữ receiver `plugin`. Một callback chỉ tồn tại trong host registry **vẫn có thể giữ cả loader của plugin**.

**Thiết kế API tốt:** `subscribe` trả một đối tượng `AutoCloseable`/`Subscription` để plugin manager có thể hủy đăng ký dứt khoát:

```java
// Trích đoạn thiết kế API:
interface Subscription extends AutoCloseable {
    @Override
    void close();
}

Subscription subscription = eventBus.subscribe(listener);
try {
    // plugin hoạt động
} finally {
    subscription.close();
}
```

Đây là **trích đoạn** vì `eventBus`, `listener` và overload `subscribe` do ứng dụng định nghĩa. Trong hệ bất đồng bộ, `unsubscribe` chỉ loại bỏ callback khỏi danh sách tương lai; những event đã được dispatch hoặc task in-flight vẫn cần được chờ hoàn tất trước khi đóng loader.

## 5. Registry và singleton cache ở loader cha

Một host thường có:

```java
// Trích đoạn trong host class:
private static final java.util.Map<String, Object> PLUGINS =
        new java.util.concurrent.ConcurrentHashMap<>();
```

Nếu `PLUGINS.put("alpha", pluginInstance)`, sau `plugin.stop()` nhưng không `remove("alpha")`, loader plugin có thể còn sống.

Các biến thể dễ bị bỏ sót:

- `Map<String, Class<?>>` lưu provider class để reflection tạo object sau.
- `Map<String, Method>` lưu method reflective trên class plugin.
- `List<Runnable>` lưu callbacks capture plugin.
- Cache framework lưu metadata về annotation, generated proxy, method handles, serializer, mapper, DI providers.
- `ThreadLocal` của một thread pool host lưu object thuộc class plugin.
- Cache giữ `ServiceLoader<?>` trỏ tới explicit plugin ClassLoader.
- Registry JDBC driver hoặc MBean server thuộc phần runtime sống lâu giữ implementation do plugin định nghĩa.

### Cẩn trọng với các reference gián tiếp

Không chỉ xem collection có chứa **ClassLoader** trực tiếp hay không. Một entry có kiểu `Object`, `Runnable`, `Listener` hay `Supplier<?>` cũng có thể giữ **object được định nghĩa bởi plugin loader**, rồi từ đó giữ runtime class/loader.

Một `static Map` nằm **trong class host/common loader** có thể giữ plugin qua nhiều lượt deploy. Một `static Map` chỉ nằm trong plugin class không nhất thiết sống lâu hơn loader nếu không có reference path từ bên ngoài.

Khi phân tích heap dump, đi từ **GC roots** tới loader bị nghi ngờ, xem **retaining/dominator path**, không chỉ tìm “class nào có static field”.

## 6. ServiceLoader cache giữ provider như thế nào?

`java.util.ServiceLoader<S>` tìm các implementations của service interface hoặc abstract class từ:
- `META-INF/services/<service-binary-name>` trong classpath/unnamed modules;
- `provides ... with ...` trong module descriptors;
- một module layer được chọn khi dùng `ServiceLoader.load(layer, service)`.

**ServiceLoader dùng lazy discovery/instantiation và cache theo instance ServiceLoader.** Với `iterator()` hoặc `findFirst()`, những providers đã instantiate có thể được giữ và trả lại khi duyệt tiếp. Một object `ServiceLoader` còn sống có thể giữ:
1. loader/ngữ cảnh discovery được cấu hình;
2. provider descriptors/types và objects đã cache sau khi duyệt;
3. các đối tượng phụ trợ phục vụ việc phát hiện providers.

Nếu host giữ `ServiceLoader<HostService>` trong static field qua nhiều thế hệ plugin, nó có thể giữ **plugin loader và provider instances của thế hệ cũ** dù host không còn gọi chúng.

```text
Host static field
     │
     ▼
ServiceLoader<HostService>
     ├── discovery ClassLoader = PluginLoader-v1
     └── provider instance cache
              │
              ▼
         PluginProvider-v1
              │
              ▼
         PluginLoader-v1

Dù đã deploy v2, v1 vẫn có thể reachable.
```

**Không có `ServiceLoader.close()` để tự đóng provider objects hoặc class loaders.** Host phải quản lý lifecycle của chính providers, service loader holder, callback resources và plugin loaders.

### reload() giải quyết gì và không giải quyết gì?

`serviceLoader.reload()` xóa cache providers của **chính ServiceLoader instance đó**, để lần duyệt sau thử khám phá lại. Nó **không**:
- xóa providers đang có references ở listener registries khác;
- tự bỏ tham chiếu đến **discovery ClassLoader** gắn với chính ServiceLoader;
- unload class đã được JVM định nghĩa;
- reset static initializer của class đã initialized;
- stop threads/connections do provider tạo;
- đóng `URLClassLoader`;
- thay thế architecture hot deploy bằng loader mới.

Vì vậy, nếu host định gỡ toàn bộ plugin loader, **chỉ gọi `reload()` thường không đủ**: host còn phải bỏ tham chiếu đến ServiceLoader cũ, providers cũ và các owners khác.

## 7. Ví dụ Java 17+ chạy được: Listener registry + ServiceLoader cache giữ plugin sau close()

Đây là ví dụ hoàn chỉnh, có stdout xác định và **không cần đoán thời điểm GC**. Ta tạo:
- Host API `api.Hook`;
- implementation `impl.PluginHook` trong thư mục plugin riêng;
- file `META-INF/services/api.Hook` để `ServiceLoader` khám phá implementation;
- host static registry lưu provider instance và cache `ServiceLoader`;
- `URLClassLoader` bị `close()` nhưng host vẫn gọi được provider đã nạp;
- cuối cùng host làm sạch registry và cache.

### 7.1. Cấu trúc source

```text
plugin-leak-demo/
├── src/
│   ├── api/
│   │   └── Hook.java
│   ├── impl/
│   │   └── PluginHook.java
│   └── host/
│       └── RegistryLeakDemo.java
├── host-out/
└── plugin-out/
    └── META-INF/
        └── services/
            └── api.Hook
```

### 7.2. File src/api/Hook.java

```java
package api;

public interface Hook {
    String message();
}
```

`Hook` là **shared API** của host. Plugin implementation sẽ implements chính `api.Hook` do application/host loader định nghĩa.

### 7.3. File src/impl/PluginHook.java

```java
package impl;

import api.Hook;

public final class PluginHook implements Hook {
    public PluginHook() {
        System.out.println("PluginHook constructed");
    }

    @Override
    public String message() {
        return "plugin active";
    }
}
```

### 7.4. Service provider configuration

Tạo file:

```text
plugin-out/META-INF/services/api.Hook
```

Nội dung một dòng:

```text
impl.PluginHook
```

Tên file metadata là binary name của **service**, không phải của provider. Provider class có public constructor không tham số, đáp ứng cơ chế classpath SPI trong ví dụ.

### 7.5. File src/host/RegistryLeakDemo.java

```java
package host;

import api.Hook;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class RegistryLeakDemo {
    // Các fields nằm ở host class, không phải plugin class.
    private static final List<Hook> LISTENERS = new ArrayList<>();
    private static ServiceLoader<Hook> cachedServices;
    private static ClassLoader pluginLoaderForComparison;

    private static void deploy() throws Exception {
        URL url = Path.of("plugin-out")
                .toAbsolutePath().toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {url},
                RegistryLeakDemo.class.getClassLoader()
        )) {
            ServiceLoader<Hook> services =
                    ServiceLoader.load(Hook.class, loader);
            Hook provider = services.findFirst().orElseThrow();

            LISTENERS.add(provider);
            cachedServices = services;
            pluginLoaderForComparison = loader;

            System.out.println("provider uses plugin loader: "
                    + (provider.getClass().getClassLoader() == loader));
        }
        // Tại đây loader đã được close(), nhưng host vẫn giữ references.
    }

    public static void main(String[] args) throws Exception {
        deploy();

        System.out.println("after close listeners: "
                + LISTENERS.size());
        System.out.println("provider still callable: "
                + LISTENERS.get(0).message());
        System.out.println("cache keeps same provider: "
                + (cachedServices.iterator().next()
                   == LISTENERS.get(0)));
        System.out.println("registry points to loader: "
                + (LISTENERS.get(0).getClass().getClassLoader()
                   == pluginLoaderForComparison));

        LISTENERS.clear();
        cachedServices = null;
        pluginLoaderForComparison = null;

        System.out.println("after cleanup listeners: "
                + LISTENERS.size());
        System.out.println("after cleanup cached: "
                + (cachedServices != null));
    }
}
```

Trường `pluginLoaderForComparison` chỉ là **dấu mốc kiểm chứng trong demo**, không phải cơ chế cần dùng ở host thật. Bản thân trường này cũng giữ một strong reference đến loader; trong hệ thực tế không nên lưu nó ở static field lâu hơn vòng đời plugin. Các dòng `LISTENERS` và `cachedServices` đã đủ minh họa hai loại reference path chính.

### 7.6. Biên dịch và chạy

Mở terminal trong `plugin-leak-demo`. Với Bash:

```bash
mkdir -p host-out plugin-out/META-INF/services
```

Với Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force host-out,plugin-out/META-INF/services | Out-Null
```

Tạo file metadata như mục 7.4, rồi biên dịch bằng **JDK 17+**:

```bash
javac --release 17 -d host-out src/api/Hook.java src/host/RegistryLeakDemo.java
javac --release 17 -cp host-out -d plugin-out src/impl/PluginHook.java
java -cp host-out host.RegistryLeakDemo
```

**Chú ý:** command `java` chỉ đưa `host-out` vào application classpath. Không thêm `plugin-out` vào `-cp` của host: nếu parent loader nhìn thấy implementation trước, class sẽ không còn do plugin loader riêng định nghĩa và demo mất ý nghĩa cô lập.

### 7.7. Stdout chính xác

```text
PluginHook constructed
provider uses plugin loader: true
after close listeners: 1
provider still callable: plugin active
cache keeps same provider: true
registry points to loader: true
after cleanup listeners: 0
after cleanup cached: false
```

Kết quả đã được thử với `javac --release 17` và Java runtime mới hơn theo API Java 17.

### 7.8. Phân tích từng quan hệ giữ

**Bước 1:** Host tạo `URLClassLoader` mới, parent là host loader. Plugin loader có thể đọc `plugin-out` và tìm `impl.PluginHook`, còn API `api.Hook` do parent cung cấp. Provider assignable sang service interface host vì dùng chung một runtime type.

**Bước 2:** `ServiceLoader.load(Hook.class, loader)` tạo discovery object; `findFirst()` duyệt lazy, instantiate `PluginHook` và constructor in `PluginHook constructed`.

**Bước 3:** Host lưu provider trong `LISTENERS` và giữ ServiceLoader trong `cachedServices`. Chúng là các strong references ở class host sống lâu.

**Bước 4:** Kết thúc `try (URLClassLoader loader = ...)`, `close()` chạy. Nhưng đã được định nghĩa, provider class/object vẫn hiện hữu. Vì method `message()` không đòi tìm class/resource mới, lời gọi tiếp theo vẫn thành công. **Không được kết luận plugin sau close luôn hoạt động đầy đủ**: một method khác cần dependency chưa load có thể thất bại.

**Bước 5:** `cachedServices.iterator().next()` trả lại instance được cache từ lượt `findFirst()`. Điều này chứng minh cache của ServiceLoader có thể là một **đường giữ provider instance**.

**Bước 6:** `LISTENERS.get(0).getClass().getClassLoader()` bằng loader đã tạo. Host còn tham chiếu đến implementation thuộc loader đó.

**Bước 7:** `LISTENERS.clear()`, `cachedServices=null` và `pluginLoaderForComparison=null` xóa ba tham chiếu host của demo. Đây là **cleanup cấu trúc có thể xác nhận ngay**; nó **không chứng minh loader đã được garbage collected đúng lúc in dòng cuối**.

### Điều gì sẽ xảy ra nếu chỉ xóa một trong hai cache?

- Chỉ `LISTENERS.clear()`: `cachedServices` vẫn có thể giữ provider instances và discovery loader.
- Chỉ `cachedServices=null`: `LISTENERS` vẫn giữ provider instance.
- Chỉ `cachedServices.reload()`: cache providers được xóa, nhưng ServiceLoader vẫn là một object sống có loader/ngữ cảnh gắn với nó; ngoài ra listener registry vẫn giữ instance.
- Chỉ `loader.close()`: không tự bỏ bất kỳ reference nào trong host.
- Chỉ `System.gc()`: không phải cleanup, không giải quyết strong references.

Đó là lý do việc xác minh bằng cấu trúc registry/ownership quan trọng hơn một lần gọi GC trong test.

## 8. Gỡ từng listener phải chính xác: token hay remove theo identity?

Trong registry đơn giản, API `remove(listener)` có thể phù hợp. Nhưng thực tế phát sinh:
- một listener được đăng ký **nhiều lần**;
- callback được wrap/decorate nên object truyền vào lúc remove không giống object nằm trong registry;
- `equals()` của listener có logic riêng khiến remove nhầm object;
- dispatch bất đồng bộ đã copy callback sang queue trước khi unregister;
- event bus cần ngừng chấp nhận callbacks mới trong lúc cleanup.

Vì vậy token registration thường rõ ràng hơn:

```java
// Trích đoạn thiết kế:
AutoCloseable registration = eventBus.register(pluginListener);

try {
    plugin.run();
} finally {
    registration.close(); // unregister một registration cụ thể
}
```

Đây là **pseudocode thiết kế**, `eventBus` và `plugin` do ứng dụng cung cấp. Khi người dùng đóng `registration`, event bus nên loại bỏ đúng registration tương ứng (thường idempotent) và có chính sách rõ về callbacks đang chạy.

Nếu listener ở registry ứng dụng **mà plugin không sở hữu API unsubscribe**, manager vẫn phải có cơ chế cleanup khi undeploy. Một registry chỉ có `add` mà không có `remove`/`close`/`reset` phù hợp là dấu hiệu rủi ro leak.

### Registration theo scope

Một kiến trúc an toàn hơn đặt mọi subscriptions trong một đối tượng lifecycle:

```java
// Trích đoạn: registrations là List<AutoCloseable>.
void stopSubscriptions(List<AutoCloseable> registrations)
        throws Exception {
    Exception first = null;
    for (int i = registrations.size() - 1; i >= 0; i--) {
        try {
            registrations.get(i).close();
        } catch (Exception ex) {
            if (first == null) {
                first = ex;
            } else {
                first.addSuppressed(ex);
            }
        }
    }
    registrations.clear();
    if (first != null) {
        throw first;
    }
}
```

Đây là **phương thức minh họa** có thể đặt trong một class; không cần import ngoài `java.util.List`. Nó cố đóng **mọi** registrations theo thứ tự ngược và ghi nhận lỗi suppressed thay vì dừng sau thất bại đầu tiên. Trong production còn cần thread-safety và tránh ghi đè registrations lúc cleanup.

## 9. Thread Context ClassLoader giữ plugin trên thread pool

`Thread.currentThread().getContextClassLoader()` có thể giữ plugin loader khi framework/`ServiceLoader` dùng TCCL để khám phá providers. Nếu host worker thread sống qua nhiều lần reload và không reset context loader, nó có thể pin generation cũ.

Restore TCCL trong `finally`, dọn provider cache/registrations riêng theo owner. Bài Thread/ThreadLocal/TCCL giải thích đầy đủ tình huống pooled thread.

## 10. ThreadLocal, CompletableFuture và các công việc bất đồng bộ

`ThreadLocal` value, scheduled callback, `CompletableFuture` chain, executor queue và task đang chạy có thể giữ plugin object dù `ServiceLoader` hoặc event bus đã được clear.

Để retire, cancel/drain futures/tasks phù hợp, `remove()` ThreadLocal trên chính thread đã gán và tránh để callback mới được đăng ký khi đang shutdown. Bài threads chuyên sâu kiểm tra lỗi này.

## 11. JNI, JDBC, JMX, logging và registry do framework quản lý

Bên cạnh listener do bạn tự viết, nhiều thành phần JDK/framework cũng là **registries**:

- **JDBC DriverManager**: plugin đăng ký `java.sql.Driver` do loader plugin định nghĩa. Tùy cách driver được khởi tạo/đăng ký và runtime, cần **deregister** driver plugin sở hữu khi unload bằng API thích hợp. Không deregister driver của ứng dụng khác.
- **JMX MBeanServer**: MBeans đăng ký với server sống lâu có thể giữ object/class/loader plugin; cần unregister đúng `ObjectName`.
- **Logging handlers/appenders**: plugin logger/handler có thể được host logger tree, async queue hoặc logging context giữ; cần detach/stop/close theo API framework.
- **Shutdown hooks**: `Runtime.addShutdownHook(Thread)` giữ hook thread được đăng ký cho tới khi chạy hoặc xóa; plugin phải remove hook do nó sở hữu khi vẫn còn được phép.
- **Timer/Executor/Scheduler**: các task objects có thể capture plugin và tồn tại lâu hơn plugin.
- **Class/serializer/reflection caches** của framework dùng chung: có thể giữ `Class<?>`/`Method`/codegen proxy của plugin.
- **JNDI, service registries, DI containers**: bản chất tương tự khi chúng giữ provider object hoặc class metadata.

Đây là **nhóm nguồn cần điều tra**, không có nghĩa mọi thư viện/JVM implementation đều leak. Nhiều framework đã dùng weak references hoặc cleanup hooks. Phải đo và xác định reference path trên phiên bản đang dùng.

### Không dọn nhầm tài nguyên dùng chung

Nếu host sở hữu thread pool và nhiều plugins cùng sử dụng, plugin A không được `shutdown()` cả pool khi A undeploy. A chỉ phải cancel các tasks/callbacks của mình và trả quyền điều khiển về host.

Tương tự, driver/mbean/listener/resource chỉ nên được unregister bởi owner hoặc lifecycle manager theo registration token/ID của nó. Cleanup bừa có thể phá plugin B.

## 12. Quan hệ giữa ServiceLoader, ClassLoader và module layer

Trong ứng dụng modular (JPMS), ServiceLoader có thể khám phá providers qua module descriptors:

```java
module app.api {
    exports app.api;
}
```

```java
module plugin.impl {
    requires app.api;
    provides app.api.Plugin with plugin.impl.DefaultPlugin;
}
```

Đây là **hai descriptor minh họa**, giả định các types thực sự có. `ServiceLoader.load(layer, Plugin.class)` tìm provider trong `ModuleLayer` phù hợp theo API Java.

Một host tạo **child ModuleLayer** cho plugin có thể giữ:
- đối tượng `ModuleLayer`;
- `Module` objects;
- `ClassLoader` do layer sử dụng;
- `ServiceLoader`/provider descriptors;
- providers/instances và callbacks.

Dù không dùng `URLClassLoader` trực tiếp, reference path từ host đến child layer/plugin loaders/classes vẫn cần quản lý. Không xem `ModuleLayer` là một “vỏ” tự unload khi gọi method nào đó; bản thân `ModuleLayer` không có `close()`.

**Không tự đồng nhất JPMS export/open với memory isolation.** Module `requires`/`exports`/`opens` kiểm soát readability/access, không tự quản lý GC hoặc ngừng threads của plugin.

## 13. Vì sao WeakReference/WeakHashMap không phải giải pháp tự động?

Java có:
- `WeakReference<T>`: tham chiếu yếu, không giữ referent sống chỉ nhờ reference đó.
- `WeakHashMap<K,V>`: khóa yếu; entry có thể bị xóa khi key không còn strong reachable phù hợp.
- `ReferenceQueue<T>`: có thể quan sát tham chiếu yếu/phantom được enqueue theo tiến trình GC.
- `Cleaner` và phantom references: cơ chế cleanup dựa trên reachability, không có thời điểm thực thi bảo đảm.

Chúng hữu ích trong một số cache, nhưng **không thay thế explicit lifecycle cleanup**.

### Ví dụ bẫy WeakHashMap

```java
// Trích đoạn:
Map<ClassLoader, Holder> cache = new WeakHashMap<>();
cache.put(pluginLoader, new Holder(pluginLoader));
```

Ở đây `Holder` giả định có strong reference đến loader. Dù key của `WeakHashMap` là weak, **value Holder có thể giữ key loader một cách mạnh**, tạo đường:

```text
WeakHashMap (host)
     └── strongly-held value Holder
              └── strongly-held pluginLoader
                       └── key chưa collectible
```

Vì thế “đã dùng WeakHashMap” không tự bảo đảm entry biến mất. Cũng tương tự với cache chứa `Class<?>` hoặc provider instances có liên hệ đến loader.

### Weak listener có đủ không?

Một event bus chỉ dùng weak references đến listeners có thể tránh giữ listeners lâu ngoài ý muốn, nhưng làm phát sinh một vấn đề khác: listener có thể bị GC **khi vẫn cần nhận event** nếu owner không giữ reference. Trong nhiều hệ thống, **strong registration + explicit unsubscribe** đáng tin hơn, rõ ràng về thời gian và quyền sở hữu.

**Chỉ dùng weak references khi semantics của cache phù hợp**, không dùng để che việc thiếu API unregister/stop.

## 14. Dùng WeakReference để quan sát khả năng collectible

Có thể ghi nhận:

```java
// Trích đoạn:
WeakReference<ClassLoader> weak =
        new WeakReference<>(pluginLoader);

// Sau khi ngừng plugin, bỏ strong references ở đúng owner:
// pluginLoader = null;  // chỉ nếu biến có thể gán lại
//
// Sau đó tại thời điểm GC thực hiện:
ClassLoader stillThere = weak.get();
```

Đây là **trích đoạn**, cần import `java.lang.ref.WeakReference` và biến `pluginLoader` phù hợp. **`weak.get() != null` ngay sau cleanup không chứng minh leak**: GC có thể chưa chạy hoặc vẫn còn transient stack references. **`weak.get() == null`** cho thấy referent đã được GC thu hồi ở thời điểm quan sát đó, nhưng không thay thế việc kiểm tra resource cleanup.

Một số test harness thử lặp `System.gc()`, chờ `ReferenceQueue` và giới hạn timeout. Các bài test này dễ bất ổn trên CI/JVM/GC khác nhau, vì `System.gc()` là request, không phải bảo đảm. Nên:
- Dùng **structural assertions** để chứng minh registries empty, subscriptions closed, TCCL restored, executors stopped.
- Dùng **integration soak/redeploy tests** để theo dõi xu hướng loader count/metaspace.
- Dùng weak references/queue như **tín hiệu hỗ trợ**, không biến “GC phải xảy ra trong 100 ms” thành điều kiện đúng sai của application logic.

## 15. Cách quan sát class unloading bằng HotSpot

Với JDK HotSpot hỗ trợ unified logging, có thể bật:

```bash
java -Xlog:class+load=info,class+unload=info -cp host-out host.RegistryLeakDemo
```

Câu lệnh dùng demo ở mục 7. **Dòng log thực tế tùy JVM/JDK** và có thể rất dài. Không có nghĩa JVM bắt buộc unload plugin class trong phiên chạy ngắn này.

Với process đang hoạt động, có thể dùng:

```bash
jcmd <pid> VM.classloader_stats
jcmd <pid> Thread.print -l
```

`<pid>` là placeholder cho process ID thật. Các lệnh và thông tin khả dụng tùy JVM/JDK. Trong HotSpot:
- `VM.classloader_stats` hỗ trợ xem ClassLoader statistics (nếu command có trong JVM tương ứng).
- Thread dump giúp tìm thread vẫn chạy code plugin hoặc TCCL liên quan.
- `GC.class_histogram`/heap dump có thể hỗ trợ phân tích instances và retention paths, nhưng cần cân nhắc overhead khi chạy production.
- Các công cụ heap analysis như Eclipse Memory Analyzer có thể giúp xem **path to GC roots**, dominator tree và retained heap.

### Đừng chỉ nhìn metaspace một lần

Metaspace có thể tăng do class loading hợp lệ, JVM caching hoặc hoạt động GC; mức sử dụng chưa giảm ngay không đủ kết luận leak. Tín hiệu mạnh hơn là **nhiều thế hệ plugin loader cũ tiếp tục reachable và tăng theo mỗi redeploy**, cùng reference paths cụ thể từ những GC roots sống lâu.

Phân biệt:
- **Bytes JAR bị mở/khóa**: vấn đề I/O/file handles; `URLClassLoader.close()` hỗ trợ giải phóng.
- **Object plugin còn trong listener registry**: vấn đề strong references; cần unregister.
- **ServiceLoader/provider caches chưa bỏ**: vấn đề discovery object/cache lifecycle.
- **Worker TCCL còn plugin loader**: vấn đề thread context/lifecycle.
- **Class metadata không unload ngay**: có thể là GC timing; cần đo qua thời gian/roots.

## 16. Heap dump: đọc “path to GC roots” đúng cách

Giả sử phát hiện `PluginClassLoader-v1` đáng lẽ đã bị dỡ mà còn sống. Một retaining path có thể là:

```text
GC Root: System ClassLoader's class / host static field
    └── host.EventBus.REGISTRATIONS
          └── ArrayList.elementData[5]
                └── impl.PluginHook instance
                      └── runtime Class / loader
                            └── PluginClassLoader-v1
```

Hoặc:

```text
GC Root: live executor Thread
    └── contextClassLoader
          └── PluginClassLoader-v1
```

Hoặc:

```text
GC Root: host static service registry
    └── cached ServiceLoader<Hook>
          ├── explicit discovery ClassLoader
          └── provider instance cache
                 └── PluginHook instance
```

Những sơ đồ này **mô tả nguyên nhân điển hình**, không phải tên field nội bộ chắc chắn giống nhau trên mọi JVM.

Quy trình đọc heap dump:
1. Chọn **loader instance thuộc thế hệ plugin cũ**, phân biệt với loader hiện tại.
2. Kiểm tra objects của plugin classes do loader cũ định nghĩa.
3. Dùng **path to GC roots, excluding weak/soft/phantom references** khi công cụ hỗ trợ để ưu tiên strong retention.
4. Nhận diện **owner bên ngoài plugin** (registry host, thread, module layer, singleton).
5. Tìm registration/cleanup path tương ứng và xác nhận nó có chạy ở normal + error shutdown.
6. Đo lại sau nhiều vòng redeploy và GC phù hợp.

Không “sửa” leak bằng cách giữ những objects nghi vấn trong debugger/test code rồi trông đợi GC unload: chính tool/test cũng có thể làm referent reachable.

## 17. Thiết kế PluginContext có quyền sở hữu rõ ràng

Một kiến trúc plugin bền vững có **một owner chịu trách nhiệm cho mỗi resource**. Host có thể tạo một context để plugin đăng ký mọi handles cần cleanup:

```text
PluginManager
   └── PluginSession
         ├── Plugin ClassLoader
         ├── ServiceLoader / provider instances
         ├── Event subscriptions
         ├── Scheduled tasks / Executor-owned handles
         ├── MBean / JDBC / logging registrations
         └── Closeable resources
```

Khi dừng:
- Ngừng nhận công việc plugin mới.
- Cho công việc đã bắt đầu hoàn thành hoặc cancel theo policy.
- Gọi stop hook của plugin khi loader và dependencies vẫn khả dụng.
- Đóng các subscriptions/registrations, hủy scheduled tasks.
- Cleanup ThreadLocals/TCCL theo đúng thread/scope.
- Đóng streams/sockets/connections do session sở hữu.
- Xóa các caches thuộc host trỏ vào instance/class/loader plugin.
- Bỏ giữ ServiceLoader cũ; nếu sẽ reload providers, tạo discovery context của thế hệ mới.
- Đóng URLClassLoader nếu host sở hữu và dùng loader loại đó.
- Loại bỏ session khỏi registry của PluginManager.

Nếu một cleanup bước thất bại, nên **vẫn thử các bước còn lại**, thu thập/suppress exceptions rồi báo cáo tổng hợp. Không dừng giữa chừng, để lại registrations khác chỉ vì một listener ném exception.

### Ví dụ resource stack đơn giản

```java
import java.util.ArrayDeque;
import java.util.Deque;

public final class CleanupStack implements AutoCloseable {
    private final Deque<AutoCloseable> resources =
            new ArrayDeque<>();

    public void own(AutoCloseable resource) {
        resources.push(resource);
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;

        while (!resources.isEmpty()) {
            AutoCloseable resource = resources.pop();
            try {
                resource.close();
            } catch (Exception ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
    }
}
```

Đây là một **file `CleanupStack.java` biên dịch được** trên Java 17. Đăng ký các resource theo thứ tự sở hữu; `push` và `pop` khiến cleanup chạy **ngược thứ tự đăng ký**.

Ví dụ sử dụng (trích đoạn):

```java
try (CleanupStack scope = new CleanupStack()) {
    scope.own(registrationA);
    scope.own(registrationB);
    // Thực thi plugin với các registrations còn hiệu lực.
}
```

`registrationA`/`registrationB` ở đây là placeholders cho `AutoCloseable` thực. Không nên dùng cùng một `CleanupStack` từ nhiều threads nếu chưa bổ sung đồng bộ; class minh họa quản lý một scope tuần tự. Đồng thời, thứ tự stop tác vụ trước cleanup callbacks/loader phải được quy định ở tầng PluginManager; không đẩy toàn bộ trách nhiệm lifecycle vào stack một cách mù quáng.

### Vì sao có ích?

- Resource owner rõ, tránh quên unsubscribe.
- Scope kết thúc vì `return`/exception vẫn gọi `close()`.
- Cleanup errors được giữ dạng suppressed.
- Dễ kiểm thử `close()` idempotent ở các wrappers riêng.
- Không biến `ServiceLoader.reload()` thành phương tiện stop plugin.

Đây là mẫu trợ giúp, không thay thế chính sách concurrency và lifecycle đầy đủ.

## 18. Thứ tự shutdown và trường hợp race

Cần tránh:

```text
Thread A: close PluginClassLoader
Thread B: đang load một class mới của plugin
Thread C: event bus đang gọi listener plugin
```

Với `URLClassLoader.close()`, Java API cảnh báo kết quả load class đồng thời với close không xác định. Về nghiệp vụ, dù loader đã đóng, event đã dispatch vẫn có thể tiếp tục gọi callback; nếu callback tải class/resource muộn, có thể lỗi.

Thứ tự an toàn thường là:

```text
ACTIVE
  ↓ 1. set state STOPPING (không nhận work mới)
STOPPING
  ↓ 2. cancel/drain, await in-flight work
QUIESCENT
  ↓ 3. stop providers, unregister callbacks/listeners
CLEANING
  ↓ 4. close owned resources, drop ServiceLoader/registry refs
  ↓ 5. restore per-thread context, shutdown owned executors
  ↓ 6. close loader và release session refs
CLOSED
  ↓ 7. GC có thể unload về sau
```

Các bước có thể thay đổi thứ tự chi tiết theo dependency. Ví dụ các threads phải restore TCCL **trước khi hoàn toàn dừng** nếu TCCL do chúng đã tự đặt; shutdown executor không tự cho phép host truy cập ThreadLocals của worker khác để remove đúng context. Thiết kế cleanup hooks chạy **ngay trong task/thread đã dùng context**, thay vì đợi đến cuối rồi cố “xóa thread local từ thread khác”.

### Dùng trạng thái rõ ràng

`PluginManager` nên có trạng thái như `NEW / STARTING / ACTIVE / STOPPING / CLOSED`, chặn callback/task mới sau `STOPPING`, và làm `stop()` **idempotent** khi có thể. Điều này giúp tránh hai threads cùng undeploy rồi remove/close lặp không đúng thứ tự.

Một listener registration token `close()` idempotent càng dễ bảo đảm cleanup toàn diện khi application shutdown gặp nhiều loại failure.

## 19. Vì sao static caches thuộc plugin không luôn là vấn đề?

Một cache static tự tham chiếu bên trong plugin generation **không tự tạo leak** nếu toàn bộ graph của generation không còn reachable từ GC roots. Ngược lại, **host static registry** giữ provider/listener/callback của plugin sẽ duy trì đường reference ngoài generation.

Phân biệt chủ sở hữu cache và chiều reference. Bài static field/cache reflection phân tích cụ thể những trường hợp nên dùng per-generation cache hoặc `ClassValue`.

## 20. Test các điều kiện cleanup mà không phụ thuộc GC

Một số assertions xác định ngay sau undeploy:
- `registry.size() == 0` đối với registrations của plugin cũ.
- `pluginSession.isClosed()` hoặc state `CLOSED`.
- Số subscriptions/handlers/timers do plugin đăng ký trở về mức nền.
- Worker tasks thuộc plugin không còn in-flight; executor owned đã shutdown.
- TCCL của workers được restore hoặc các workers thuộc plugin đã kết thúc.
- `ServiceLoader` và provider instances của **thế hệ cũ** không còn được host giữ trong registry/cache.
- Một loader mới có thể nạp implementation phiên bản mới qua shared API mà không xung đột runtime class identity.

Một tình huống test nên mô phỏng:

```text
Deploy v1
  → register listener, create provider/cache
  → assert plugin active and callable

Stop v1
  → unregister listener, clear caches, close loader
  → assert no old callbacks/owners
  → do NOT assert immediate class unloading

Deploy v2 with fresh loader
  → assert new implementation selected
  → assert shared host API still assignable

Repeat N times
  → check loader count/metaspace trend under controlled GC
```

Ở step soak, sử dụng heap dump/reference paths nếu trend tăng bất thường. Không biến bộ test thành vòng lặp `System.gc()` vô hạn, vì JVM có thể không thu gom class ngay và thời điểm GC không phải API đồng bộ.

## 21. Tình huống đặc biệt: ServiceLoader trong host dùng TCCL

Giả sử framework ở host loader gọi:

```java
ServiceLoader<MyService> providers =
        ServiceLoader.load(MyService.class);
```

Theo contract, overload này chọn TCCL của thread đang gọi. Nếu thread pool được tái sử dụng qua plugins:
- TCCL còn `PluginLoader-v1` → service discovery có thể tìm provider v1.
- Đổi TCCL sang `PluginLoader-v2` sau đó không có nghĩa `providers` cũ tự đổi discovery loader; nó đã được tạo trong ngữ cảnh cũ.
- Một host static cache `providers` còn giữ object ServiceLoader và các references liên quan đến loader cũ.

Giải pháp:
1. Scope TCCL theo task và restore `finally`.
2. Nếu host biết loader đúng, ưu tiên `ServiceLoader.load(service, pluginLoader)` cho rõ ràng.
3. Không cache `ServiceLoader` theo service type đơn thuần nếu thực tế provider set **phụ thuộc từng plugin generation/ClassLoader/ModuleLayer**.
4. Khi unload, drop cache entry của generation cũ, ngừng provider tasks và cleanup callback registrations.
5. Tạo ServiceLoader mới cho loader/layer thế hệ mới.

Đây là tình huống thường gây nhầm giữa **class loading**, **service discovery**, **thread context** và **memory retention**.

## 22. Dấu hiệu quan trọng trong log, heap dump và monitoring

| Dấu hiệu | Có thể liên quan | Kiểm tra tiếp |
|---|---|---|
| Listener tiếp tục nhận event sau plugin stop | Subscription chưa gỡ, queue in-flight | Registry snapshot, owner registration token |
| Plugin class loader cũ có nhiều instances sau redeploy | Registry/cache/threads giữ reference | Path to GC roots |
| `ServiceLoader` trả provider cũ sau cập nhật | Cache + loader cũ, chưa tạo context mới | ServiceLoader object, loader/layer, `reload`/replace |
| Metaspace tăng theo số lần redeploy | Classloader generations chưa được thu gom | Class loader stats, GC roots, trend |
| JAR không xóa được trên Windows | Open file handles | URLClassLoader.close, JarFile, streams khác |
| NoClassDefFoundError sau close | Code/plugin còn hoạt động, cần dependency mới | Shutdown order, lazy loading, in-flight tasks |
| Request B dùng provider của plugin A | TCCL của worker bị giữ từ request trước | TCCL set/restore trong task |
| Thread pool không dừng khi plugin stop | Executor/scheduled job còn sống | Threads, queue, owner shutdown |
| `WeakReference.get()` vẫn khác null sau stop | GC chưa chạy hoặc còn strong refs | Đừng kết luận ngay; xem registry/roots |
| Leak chỉ xảy ra khi error trong start | Cleanup không chạy ở failure path | try/finally, cleanup stack, partial registration |

Nếu có thể, logging nên gắn **plugin generation ID** với loader object identity (không chỉ tên class), và record số registrations/providers/tasks do generation đó còn sở hữu.

## 23. Một số anti-pattern phổ biến

### 23.1. “Close là đủ”

```java
// Anti-pattern ở cấp kiến trúc, trích đoạn:
pluginLoader.close();
// hostRegistry vẫn giữ plugin instances
```

**Sai** vì close không xóa registry entry. Phải unsubscribe/drop references trước hoặc trong shutdown protocol.

### 23.2. “Reload ServiceLoader sẽ xóa provider cũ ở mọi nơi”

```java
// Trích đoạn:
services.reload();
```

**Sai** nếu listener registry vẫn chứa provider, hoặc ServiceLoader còn giữ explicit plugin loader. `reload` không gọi `close()` cho provider.

### 23.3. “Dùng WeakHashMap là hết leak”

Sai khi map value hoặc reference khác vẫn giữ loader/key, và khi data cần strong lifecycle ownership.

### 23.4. “Dùng System.gc() để unload plugin”

```java
System.gc();
```

API chỉ request GC, không có bảo đảm thu gom một loader đang reachable. Đặc biệt, nếu host static field còn giữ callback/provider, GC không được phép thu hồi object đó.

### 23.5. “Dùng loader mới nhưng cache host vẫn giữ bản cũ”

Loader mới có thể định nghĩa cùng binary name thành runtime class mới, nhưng những `Class<?>`/provider objects của loader cũ trong host registry vẫn sống. Khi cast implementation qua hai generations có thể nhận `ClassCastException`.

### 23.6. “Xóa mọi static cache trong plugin bằng reflection”

Không cần thiết nếu không còn strong refs từ host/GC roots; còn có thể gây access issue trong JPMS. Đúng hơn là dọn các **references xuyên ranh giới loader** và resources owned có lifecycle cụ thể.

## 24. Checklist code review cho hệ plugin

### Trước khi triển khai

- Shared service API/interface nằm ở **host/common loader** hoặc shared module ổn định.
- Plugin implementation/dependencies private nằm ở loader riêng theo architecture.
- Có mô hình ownership cho loader, ServiceLoader, listeners, async tasks, ThreadLocals và external resources.
- Registration APIs trả token hoặc hỗ trợ unregister theo đúng owner.
- Quy định rõ có dùng TCCL hay explicit loader/module layer khi discovery.

### Trong vòng đời ACTIVE

- Theo dõi list subscriptions/providers/tasks thuộc từng plugin generation.
- TCCL được set/restore theo scope, đặc biệt ở executor.
- Không dùng global static cache keyed chỉ bằng class name cho các plugin có nhiều generations.
- Không để provider tự mở background thread vô hạn không có stop hook.
- Không đóng loader sớm nếu plugin có thể lazy-load class/resource.

### Khi STOPPING

- Chặn work mới, chờ in-flight tasks.
- Gọi stop hooks khi plugin dependencies còn hoạt động.
- Unregister listeners, MBeans, drivers, shutdown hooks do plugin sở hữu.
- Cancel timers/tasks và shutdown các executors do plugin sở hữu.
- Remove ThreadLocals, restore TCCL đúng thread.
- Đóng streams/sockets/connections, xử lý errors nhưng vẫn cleanup phần còn lại.
- Xóa host registry/cache refs tới provider instances, `Class<?>`, reflection objects, ServiceLoader, layer, loader.
- Gọi `URLClassLoader.close()` khi applicable và bỏ session references.

### Sau cleanup

- Assert các registry counts/owners về mức mong đợi.
- Không yêu cầu GC/unload đồng bộ.
- Chạy integration test reload với loader thế hệ mới.
- Theo dõi metaspace/loader generations trên nhiều vòng deploy; nếu tăng, phân tích GC roots.
- Không đụng vào resources của plugin khác khi teardown.

## 25. Câu hỏi tự kiểm tra

1. **`URLClassLoader.close()` có tự unload mọi class không?** Không, nó đóng nguồn tìm và tài nguyên; classes đã nạp có thể còn sống.
2. **Một listener nằm trong host static registry có thể giữ plugin loader không?** Có, qua object implementation/class/loader.
3. **Event bus không phát event nữa thì listener đã hết leak chưa?** Không; phải remove reference.
4. **`ServiceLoader.findFirst()` có thể làm cache giữ provider instance không?** Có, ServiceLoader instance có thể cache provider đã instantiate.
5. **`ServiceLoader.reload()` có clear listeners do host đăng ký không?** Không.
6. **ServiceLoader cũ còn giữ loader đã dùng khám phá sau `reload()` không?** Có thể, vì nó vẫn là discovery object với loader/ngữ cảnh đã cấu hình.
7. **Vì sao host static `Map<String, Class<?>>` nguy hiểm?** Nó có thể giữ plugin runtime Class và defining loader.
8. **Static field bên trong plugin tự trỏ vòng có luôn gây leak không?** Không; nếu cả nhóm không còn reachable từ GC roots, GC có thể thu gom.
9. **TCCL của thread pool còn trỏ plugin cũ thì sao?** Giữ strong reference và có thể chọn nhầm provider.
10. **Dùng `setContextClassLoader(null)` có luôn đúng khi cleanup không?** Không; restore đúng giá trị trước đó.
11. **ThreadLocal remove có tự stop background thread không?** Không; cần lifecycle task/executor riêng.
12. **Dùng `WeakHashMap` với value giữ mạnh key có tự làm entry biến mất không?** Không.
13. **`WeakReference.get() != null` ngay sau stop có chứng minh leak không?** Không; GC có thể chưa chạy.
14. **Nên test cleanup thế nào để ổn định?** Assert unregister, empty registries, dropped caches, completed tasks, restored TCCL; GC observation là bổ sung.
15. **Classloader count tăng qua nhiều redeploy nên điều tra gì?** Loader generations còn sống và path to GC roots.
16. **Có thể vừa thay provider thế hệ mới mà không dọn provider cũ không?** Có thể về mặt kỹ thuật, nhưng dễ gây leak, nhầm provider và type identity conflicts.
17. **Mục đích subscription token là gì?** Bảo đảm host có handle hủy đúng registration thuộc plugin.
18. **`ModuleLayer` có close()/unload tự động không?** Không.
19. **ServiceLoader có thread-safe không?** Không; phải phối hợp khi duyệt/reload từ nhiều threads.
20. **Mục tiêu thực tế của undeploy là gì?** Dừng plugin, cleanup resources và references; GC có thể unload classes sau đó.

## Tài liệu tham khảo chính thức

- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): discovery, cache, `reload()`, threading và provider lifecycle.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): close behavior và các class/resource đã nạp.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): defining loader, parent delegation và runtime class identity.
- [Java SE 17 — Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): TCCL và thread lifecycle.
- [Java SE 17 — WeakReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html): reachability và tham chiếu yếu.
- [Java SE 17 — WeakHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html): weak keys và cảnh báo values có thể giữ keys.
- [JVMS 17 — `5.3` Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): defining loaders, runtime identity, class lifecycle.
- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): dữ liệu riêng theo thread và `remove()`.
- [Java SE 17 — ModuleLayer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html): module layer runtime và ClassLoader mapping.

**Kết luận:** Khi plugin đã stop nhưng bộ nhớ không giảm, cần kiểm tra **ai còn giữ plugin**: listener registry, singleton/cache của host, `ServiceLoader` và cached provider instances, TCCL/ThreadLocal, background tasks hoặc các registries thuộc framework. `close()` và `reload()` chỉ xử lý **một phần vòng đời**; chúng không xóa strong references từ hệ thống. Cleanup đúng là **stop work → unregister/close owned resources → bỏ cache và references → close loader → để GC quyết định unloading**, với kiểm thử cấu trúc và phân tích GC roots thay vì đặt kỳ vọng vào `System.gc()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-unloading-and-memory-009">Giải phóng JAR handle và tránh giữ file sau reload</a>

<details>
<summary>Xem chi tiết</summary>

# Giải phóng JAR handle và tránh giữ file sau reload

## 1. Vì sao reload plugin rồi vẫn không thay hoặc xóa được JAR?

Trong ứng dụng Java nạp plugin từ `plugin.jar`, các thành phần như `URLClassLoader`, `JarFile` hoặc luồng đọc resource có thể giữ **file handle** tới JAR. Khi triển khai lại (reload), ứng dụng thường muốn thay JAR v1 bằng JAR v2; nếu chưa đóng các handles, thao tác ghi đè, rename hoặc xóa có thể gặp lỗi — đặc biệt trên Windows.

Đôi khi JAR thay được nhưng chương trình vẫn chạy code cũ. Đó lại là vấn đề khác: JVM đã định nghĩa class theo **binary name và defining class loader**, không tự cập nhật bytecode của một class đã nạp chỉ vì file JAR ngoài đĩa thay đổi.

Phải phân biệt ba mục tiêu:

1. **Giải phóng file handle:** đóng loader/stream/archive/connection sở hữu handle để hệ điều hành có thể giải phóng tài nguyên I/O.
2. **Nạp phiên bản mới:** tạo **loader mới** cho JAR phiên bản mới thay vì tái sử dụng loader đã định nghĩa class v1.
3. **Dỡ class khỏi bộ nhớ:** không còn strong references tới class, class loader, instances và những cấu trúc liên quan; khi đó JVM mới *có thể* thu gom/unload nếu phù hợp.

Ba mục tiêu liên quan, nhưng **không tương đương**. `URLClassLoader.close()` không tự unload class, còn `System.gc()` không phải API để đóng JAR handle.

Các triệu chứng:

| Triệu chứng | Nhóm vấn đề có thể xảy ra |
| --- | --- |
| Windows báo file đang được process sử dụng | Handle từ loader, JarFile, cache, stream, process khác |
| Linux xóa được file nhưng bộ nhớ/FD tăng | Unlink không bảo đảm descriptor đã đóng |
| Reload nhưng gọi method vẫn trả giá trị v1 | Class v1 vẫn được dùng; không tạo/định tuyến sang loader v2 |
| Sau nhiều lần reload, metaspace tăng | Loader/classes cũ còn được tham chiếu; cần phân tích heap/GC |
| Reload chỉ lỗi khi có request chạy đồng thời | Đóng loader trong lúc thread khác vẫn load class/resource |
| Đóng loader xong mà plugin vẫn có thread chạy | `close()` không tự gọi stop/shutdown plugin |

## 2. Hợp đồng chính xác của URLClassLoader.close()

`java.net.URLClassLoader` triển khai `java.io.Closeable` và có thể dùng trong *try-with-resources*. Theo Javadoc Java SE 21:

- `close()` đóng loader để loader không tiếp tục nạp **class và resource mới do loader ấy tự định nghĩa**.
- Các **class đã được nạp** vẫn có thể truy cập; các class/resource do parent định nghĩa vẫn có thể tiếp cận qua delegation.
- Với `file:` và `jar:` URL, nó cố gắng **đóng các file đã được chính loader ấy mở**.
- Nếu một thread còn thực hiện class loading khi `close()` được gọi, **kết quả của thao tác đang chạy không xác định** theo hợp đồng API.
- `close()` nỗ lực đóng mọi file, giữ lỗi `IOException` và các ngoại lệ suppressed khi có nhiều lỗi.
- Gọi close lại trên loader đã đóng là no-op.

Những điều `close()` **không hứa hẹn**:

- Không hủy các instances đã tạo từ class của loader.
- Không bảo đảm Class objects hoặc metaspace được thu gom ngay.
- Không tự dừng threads, executors, scheduled tasks hoặc callbacks của plugin.
- Không tự đóng mọi `new JarFile(...)` hay mọi `URL.openStream()` của thư viện ngoài.
- Không đóng parent system/application loader hoặc sibling loader.
- Không làm định nghĩa class cũ trở thành bytecode mới.
- Không bảo đảm file JAR đã được giải phóng nếu **owner khác** còn giữ handle.

Do đó, sau khi `close()` thành công bạn chỉ có thể khẳng định **đã thực hiện thao tác đóng của URLClassLoader ấy**, không thể suy ra toàn bộ tiến trình đã thôi giữ JAR.

## 3. Quản lý vòng đời loader bằng try-with-resources

Ví dụ cơ bản:

~~~java
Path jar = Path.of("plugins", "plugin-v1.jar")
        .toAbsolutePath().normalize();
URL url = jar.toUri().toURL();

try (URLClassLoader loader = new URLClassLoader(
        new URL[]{url}, Host.class.getClassLoader())) {

    Class<?> impl = loader.loadClass("demo.Greeter");
    // Làm việc với impl trong vòng đời loader còn mở.
}
// loader.close() được gọi ở đây.
~~~

Đây là đoạn minh họa **không độc lập**, giả định `Host` và imports có sẵn. `try-with-resources` giúp đóng loader khi block kết thúc kể cả khi có exception. Nếu `close()` ném `IOException`, caller phải xử lý/log; nếu block cũng ném ngoại lệ thì lỗi close có thể nằm trong `getSuppressed()` của lỗi chính.

**Quan trọng với server/plugin lâu dài:** không đặt try-with-resources quanh **riêng lệnh loadClass** rồi trả plugin instance cho nhiều request phía sau. Sau khi block kết thúc, loader đã đóng, nên nếu instance về sau cần load thêm dependency hoặc resource, việc nạp mới có thể không thực hiện được.

Hãy để đối tượng quản lý plugin (plugin manager) sở hữu loader trong **suốt một phiên phục vụ**, rồi đóng nó **sau khi** plugin dừng hoạt động.

### Parent không thuộc quyền sở hữu của child

Không đóng nhầm application/system loader hoặc một loader cha được chia sẻ giữa nhiều plugin. `URLClassLoader.close()` trên loader con **không** đóng parent; đó là hành vi đúng. Nếu parent classpath cũng đã chứa cùng plugin JAR, việc chỉ đóng child loader không đủ để giải phóng handle do parent sở hữu — và có thể khiến parent-first trả class phiên bản cũ.

## 4. Luồng resource và handle độc lập

Plugin thường đọc cấu hình/resource ngay trong JAR:

~~~java
try (URLClassLoader loader = new URLClassLoader(
        new URL[]{jarUrl}, parentLoader)) {

    try (InputStream in =
                 loader.getResourceAsStream("demo/config.txt")) {
        if (in == null) {
            throw new IllegalStateException("Missing demo/config.txt");
        }
        String config = new String(in.readAllBytes(),
                StandardCharsets.UTF_8);
        // Sử dụng config.
    }
}
~~~

Đây là code minh họa với `jarUrl`, `parentLoader` và imports giả định. Thứ tự đúng: **stream đóng trước, loader đóng sau**.

`URLClassLoader.getResourceAsStream` có hợp đồng: nếu loader đã đóng, các resource mà phương thức này mở sẽ được đóng. Tuy vậy, luôn nên đóng chính stream ngay khi dùng xong bằng *try-with-resources*, thay vì để nó sống suốt vòng đời loader.

Một stream lấy từ **API khác** có thể có owner khác:

| Cách mở | Đối tượng chủ quản | Cách quản lý phù hợp |
| --- | --- | --- |
| `loader.getResourceAsStream(name)` | Loader và stream do nó mở | Đóng stream khi dùng xong, đóng loader khi plugin stop |
| `url.openStream()` | URLConnection/stream; có thể dùng `jar:` handler cache | Đóng stream, chú ý connection/cache |
| `new JarFile(path)` | JarFile riêng | Đóng JarFile tường minh |
| `new ZipFile(path)` | ZipFile riêng | Đóng ZipFile tường minh |
| Framework-specific nested JAR reader | Framework/resource subsystem riêng | Tuân theo lifecycle của framework |

Không tin rằng `loader.close()` sẽ quét mọi object `JarFile` trên heap và đóng tất cả.

### Khi trực tiếp đọc JarFile

~~~java
try (JarFile jar = new JarFile(jarPath.toFile())) {
    JarEntry entry = jar.getJarEntry("demo/config.txt");
    if (entry == null) {
        throw new IllegalStateException("JAR entry missing");
    }

    try (InputStream in = jar.getInputStream(entry)) {
        String content = new String(in.readAllBytes(),
                StandardCharsets.UTF_8);
        // Xử lý content.
    }
}
~~~

Đoạn này cần `JarFile`, `JarEntry`, `InputStream`, `StandardCharsets` và biến `jarPath` hợp lệ. Stream được đóng trước JarFile. Nếu một dependency tự mở JAR bằng cách này, đóng URLClassLoader của host không thay trách nhiệm đóng JarFile đó.

## 5. Close không phải class unloading

Giả sử loader L1 định nghĩa class `demo.Greeter` từ v1:

~~~text
URLClassLoader L1
    |
    +--- mở file plugin-v1.jar
    |
    +--- định nghĩa Class<demo.Greeter>
             |
             +--- instance v1, static state, references
~~~

Sau `L1.close()`:

- File mà L1 sở hữu đã được đưa vào quy trình đóng theo hợp đồng.
- `Class<demo.Greeter>` v1 **vẫn là class runtime hiện hữu** nếu còn được tham chiếu.
- Những method đã liên kết và object đã tạo có thể vẫn dùng được trong các giới hạn tương ứng; nhưng không bảo đảm lazy class loading từ L1 hoạt động sau close.
- `Class<?>`, instance, method/constructor reflection, callback, thread hoặc registry của host có thể giữ L1 sống.

Để dùng class v2 cùng binary name, tạo **L2**, vì JVM phân biệt class bằng **binary name + defining loader**:

~~~text
demo.Greeter @ L1 = định nghĩa v1
demo.Greeter @ L2 = định nghĩa v2

cùng tên nhưng hai runtime Class khác nhau
~~~

Không thể bắt L1 định nghĩa lại `demo.Greeter` một lần nữa bằng cách sửa JAR tại chỗ rồi gọi `L1.loadClass(...)`. JVM không thay bytecode đã định nghĩa chỉ vì filesystem thay đổi.

`System.gc()` không phải câu lệnh unload class tức thời. Class unloading phụ thuộc điều kiện GC và việc loader/class có còn bị giữ hay không; không dùng vòng lặp gọi GC vô hạn để chờ xóa file.

## 6. Quy trình hot reload an toàn

Khi reload plugin được phục vụ liên tục, nên có một **lifecycle coordinator**:

~~~text
RUNNING (plugin v1)
    |
    | Bắt đầu reload
    v
QUIESCING: chặn request/task mới vào v1
    |
    | Đợi request in-flight hoàn thành
    v
STOPPING: plugin.stop(), cancel tasks, unregister handlers
    |
    | Đóng streams, connections, JarFile/ZipFile khác
    v
CLOSING: URLClassLoader.close(), xử lý IOException
    |
    | Không thread nào còn loading từ loader v1
    v
RETIRED: bỏ references tới v1, dọn JAR cũ khi không còn handle
    |
    v
START v2: loader mới, instance mới, health check, nhận request
~~~

Nếu muốn không downtime, nên **nạp v2 trước trong file tên riêng**, health-check rồi chuyển routing; sau đó mới đợi v1 ngừng hẳn và đóng loader cũ:

~~~text
plugins/
    greeter-1.0.0.jar  <-- L1; request cũ
    greeter-2.0.0.jar  <-- L2; request mới
~~~

Đây là **blue/green deployment ở mức plugin**, tránh thay thế một file đang được loader cũ mở:

1. Triển khai JAR v2 dưới tên versioned bất biến.
2. Tạo L2 độc lập, khởi tạo plugin mới và kiểm tra sức khỏe.
3. Chuyển request mới sang L2 bằng pointer/registry có đồng bộ.
4. Đợi v1 kết thúc các tác vụ đang chạy, dừng tất cả resources v1.
5. Đóng L1, dọn references, rồi mới xóa file JAR v1 khi không còn handles.

Với thiết kế overwrite cùng một file `live.jar`, việc replace nên xảy ra **sau khi đã đóng** toàn bộ owners của JAR cũ. Trên các hệ thống production, đặt bản mới theo tên version thường dễ rollback và ít phụ thuộc hành vi locking của OS hơn.

## 7. Bài thực hành Java 17+: đóng loader v1 rồi thay live.jar

Bài có **hai class source cùng binary name** nhưng nội dung khác nhau, được compile thành **hai JAR riêng**. Host chạy với classpath chỉ chứa code host, nạp JAR v1 rồi JAR v2 qua hai instances URLClassLoader, đóng stream và loader giữa hai lượt.

### Cấu trúc source

~~~text
example/
├── src/
│   ├── v1/demo/Greeter.java
│   ├── v2/demo/Greeter.java
│   └── host/ReloadDemo.java
├── out/
│   ├── v1/demo/...
│   ├── v2/demo/...
│   └── host/...
└── dist/
    ├── v1.jar
    ├── v2.jar
    └── live.jar
~~~

Hai file `Greeter.java` cố ý trùng tên class, nên **không compile chúng cùng một output root**.

### File src/v1/demo/Greeter.java

~~~java
package demo;

public final class Greeter {
    public static String version() {
        return "v1";
    }
}
~~~

### File src/v2/demo/Greeter.java

~~~java
package demo;

public final class Greeter {
    public static String version() {
        return "v2";
    }
}
~~~

### File src/host/ReloadDemo.java

~~~java
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ReloadDemo {
    private static Class<?> usePlugin(Path jar, String label)
            throws Exception {
        URL url = jar.toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{url},
                ReloadDemo.class.getClassLoader())) {

            Class<?> type = loader.loadClass("demo.Greeter");

            System.out.println(label + " defined by child: "
                    + (type.getClassLoader() == loader));

            System.out.println(label + " version: "
                    + type.getMethod("version").invoke(null));

            try (InputStream in =
                         loader.getResourceAsStream("demo/config.txt")) {
                if (in == null) {
                    throw new IllegalStateException("Missing config");
                }
                String value = new String(in.readAllBytes(),
                        StandardCharsets.UTF_8).trim();
                System.out.println(label + " config: " + value);
            }

            return type;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("live.jar next.jar");
        }

        Path live = Path.of(args[0])
                .toAbsolutePath().normalize();
        Path next = Path.of(args[1])
                .toAbsolutePath().normalize();

        Class<?> first = usePlugin(live, "before");

        Files.copy(next, live, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("jar replaced");

        Class<?> second = usePlugin(live, "after");

        System.out.println("different runtime classes: "
                + (first != second));
    }
}
~~~

Chú ý:

- Mỗi lượt `usePlugin` tạo loader riêng và đóng bằng try-with-resources.
- Stream tài nguyên được đóng **trước** khi đóng loader.
- Bước copy/replace xảy ra **giữa** hai lượt, lúc loader v1 đã đóng.
- Cả hai `Class<?>` được giữ để so sánh identity: bài này **không thử chứng minh class unloading**.
- Host classpath **không** được chứa `demo.Greeter`; nếu parent nhìn thấy class đó, parent-first có thể trả class parent.

### Compile và chạy Linux/macOS

Từ thư mục `example`:

~~~bash
mkdir -p out/v1/demo out/v2/demo out/host dist

javac --release 17 -d out/v1 src/v1/demo/Greeter.java
javac --release 17 -d out/v2 src/v2/demo/Greeter.java
javac --release 17 -d out/host src/host/ReloadDemo.java

printf 'config one\n' > out/v1/demo/config.txt
printf 'config two\n' > out/v2/demo/config.txt

jar --create --file dist/v1.jar -C out/v1 .
jar --create --file dist/v2.jar -C out/v2 .

cp dist/v1.jar dist/live.jar

java -cp out/host ReloadDemo dist/live.jar dist/v2.jar
~~~

### Compile và chạy Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force out\v1\demo, out\v2\demo, out\host, dist | Out-Null

javac --release 17 -d out\v1 src\v1\demo\Greeter.java
javac --release 17 -d out\v2 src\v2\demo\Greeter.java
javac --release 17 -d out\host src\host\ReloadDemo.java

Set-Content -Path out\v1\demo\config.txt -Value 'config one' -Encoding Ascii
Set-Content -Path out\v2\demo\config.txt -Value 'config two' -Encoding Ascii

jar --create --file dist\v1.jar -C out\v1 .
jar --create --file dist\v2.jar -C out\v2 .

Copy-Item dist\v1.jar dist\live.jar
java -cp out\host ReloadDemo dist\live.jar dist\v2.jar
~~~

Chạy trong thư mục `example` với JDK 17 trở lên. Các resource config chứa text ASCII nên việc đọc UTF-8 trả nội dung giống nhau trên hai hệ điều hành.

### Stdout chính xác đã kiểm chứng

~~~text
before defined by child: true
before version: v1
before config: config one
jar replaced
after defined by child: true
after version: v2
after config: config two
different runtime classes: true
~~~

Bài đã được biên dịch bằng `javac --release 17` và chạy thực tế trên OpenJDK 21 (Linux) với cùng cấu trúc file. Điều này chứng minh loader mới thấy JAR v2 và JAR resource v2. **Nó không chứng minh trên mọi Windows install mọi file lock đều được giải phóng**, vì file có thể còn do process, scanner hoặc thư viện khác sử dụng.

### Giải thích đầu ra

- `before defined by child: true`: L1 thực sự định nghĩa class v1.
- `before version: v1` và `before config: config one`: class/resource đầu tiên đến từ file live chứa v1.
- `jar replaced`: thao tác `Files.copy` hoàn thành trong môi trường test sau khi loader đầu tiên đóng.
- `after defined by child: true`: L2 định nghĩa class mới, không nhận class từ parent.
- `after version: v2` và `after config: config two`: class/resource đã chuyển sang JAR mới.
- `different runtime classes: true`: L1 và L2 là hai defining loaders khác nhau nên `Class<?>` không trùng.

Ví dụ cố ý trả `Class<?>` v1 từ `usePlugin` để so sánh. Trong hệ thống thật muốn unload, **không giữ reference v1 vô thời hạn**.

## 8. JarURLConnection và cache: một nguồn giữ JAR khác

Nếu code lấy URL rồi trực tiếp mở stream:

~~~java
URL resourceUrl = loader.getResource("demo/config.txt");
if (resourceUrl == null) {
    throw new IllegalStateException("Missing resource");
}
try (InputStream in = resourceUrl.openStream()) {
    // Đọc resource.
}
~~~

Đây là đoạn code minh họa với loader sẵn có. URL entry từ JAR có thể dạng:

~~~text
jar:file:/path/to/plugin.jar!/demo/config.txt
~~~

Giao thức `jar:` có `JarURLConnection`, một subclass của `URLConnection`. Thuộc tính `useCaches` của URLConnection cho phép cache khi `true`, và default thường bật. Một kết nối/caching độc lập có thể giữ tài nguyên ngay cả khi bạn đã gọi `close()` trên loader riêng.

Nếu **tự quản lý một JarURLConnection** và không muốn dùng cache của kết nối đó:

~~~java
URL jarEntry = new URL(
        "jar:file:/path/to/plugin.jar!/demo/config.txt");
JarURLConnection connection =
        (JarURLConnection) jarEntry.openConnection();
connection.setUseCaches(false);

try (InputStream in = connection.getInputStream()) {
    // Đọc bytes.
}
~~~

Đây là **minh họa**, URL string phải được tạo hợp lệ theo đường dẫn hệ thống thực. Gọi `setUseCaches(false)` **trước** khi bắt đầu sử dụng connection. Nó ảnh hưởng tới chính connection đó, **không** hứa đóng mọi cache/handle khác trên toàn JVM. Không lạm dụng thay đổi default cache toàn hệ thống để “sửa” một JAR lock nếu không hiểu tác động.

Nếu code mở `JarFile` bằng constructor, quản lý owner rõ ràng bằng try-with-resources; nếu connection hoặc thư viện dùng cache chung, cần tuân đúng contract của API và tránh đóng bừa một JarFile đang được chia sẻ.

## 9. Những gì giữ loader và class v1 trong bộ nhớ

Ngay cả khi đã giải phóng JAR handle, loader cũ vẫn có thể sống vì các **strong references**. Những nguồn phổ biến:

| Nguồn tham chiếu | Dấu hiệu | Cách xử lý |
| --- | --- | --- |
| Host static map / registry | `Map<String,Object>` còn giữ plugin instance | Unregister/remove theo version |
| `Class<?>` hoặc reflection Method/Constructor cache | Host giữ metadata class plugin lâu dài | Xóa cache theo lifecycle |
| Runnable/executor | Tác vụ của plugin vẫn nằm trong queue hoặc đang chạy | Cancel/shutdown và chờ kết thúc |
| Thread do plugin tạo | Thread sống giữ Runnable/context loader | Shutdown/join theo contract |
| TCCL trên thread pool | Context loader vẫn là child đã retire | Khôi phục TCCL trong `finally` |
| ThreadLocal | Thread lâu sống giữ plugin object/class | Remove ThreadLocal từ đúng thread |
| Listener/event bus | Plugin đã đăng ký callback vào singleton host | Unregister khi stop |
| MBean/JMX registry | MBean trỏ tới implementation class | Unregister có chủ đích |
| Service/driver registry | Provider/driver/handler được giữ trong registry | Dọn qua API đăng ký tương ứng |
| Resource/stream/archive | InputStream/JarFile/ZipFile còn mở | Đóng đúng owner |
| Shutdown hook | Hook giữ object plugin hoặc loader | Remove khi được phép và hợp lệ |

### TCCL nên được phục hồi

~~~java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi code/framework cần tìm resources hoặc SPI qua TCCL.
} finally {
    thread.setContextClassLoader(previous);
}
~~~

Đây là đoạn minh họa. Với thread pool, một thread có thể tồn tại lâu hơn plugin rất nhiều; quên khôi phục context loader sẽ giữ tham chiếu tới phiên plugin cũ. Tuy vậy, phục hồi TCCL thôi chưa giải quyết mọi reference từ thread/task/registry.

Class unloading phụ thuộc JVM/GC và ranh giới các tham chiếu. `WeakReference<ClassLoader>` có thể giúp trong test quan sát loader có thể được thu gom không, nhưng **không đặt assertion thời gian cứng** rằng GC phải unload trong vài mili-giây. Heap dump và class unloading logs giúp phân tích khi có leak.

## 10. Khi nào dùng versioned JAR thay vì ghi đè live.jar?

Nếu có quyền thiết kế lại hệ thống, tên file versioned thường là lựa chọn tốt:

~~~text
plugins/
    service-1.0.0.jar
    service-1.1.0.jar
    service-1.2.0.jar
~~~

Lợi ích:

- Không cần ghi đè file mà loader cũ đang sử dụng.
- Có thể chạy song song loader version cũ và mới trong thời gian chuyển traffic.
- Rollback dễ vì file v1 chưa bị sửa.
- Dễ ghi log chính xác version nào đang phục vụ.
- Cleanup có thể thực hiện sau khi những loader liên quan đóng và không còn handle.

Nếu cần **atomic deployment**, tải/copy bản mới vào file tạm cùng filesystem, xác minh checksum/version, rồi rename tới tên **versioned final** trước khi tạo loader mới. Không coi một thao tác `Files.copy(..., REPLACE_EXISTING)` trực tiếp lên live file là một quy trình rollout đầy đủ có rollback/atomicity. Cơ chế rename/atomic move và quyền file phụ thuộc OS/filesystem.

Khi ứng dụng có nhiều request, cần dùng routing/registry có đồng bộ để chuyển sang loader v2. Không tự đóng L1 ngay khi cập nhật registry nếu request v1 còn hoạt động.

## 11. Chẩn đoán khi file vẫn bị giữ sau close

Một checklist thực hành:

1. **Lệnh chạy host có đặt plugin JAR trên application classpath không?** Nếu có, parent có thể đã mở JAR; đóng child không đóng parent.
2. **Có hai URLClassLoader trỏ cùng JAR không?** Đóng một loader chưa giải phóng handle của loader khác.
3. **Có reader độc lập nào không?** Tìm `new JarFile`, `ZipFile`, `getResourceAsStream`, `openStream`, framework scanner.
4. **Có JarURLConnection caching không?** Kiểm tra ownership và `useCaches`; không coi mọi URL stream là do URLClassLoader quản lý.
5. **Có IOException bị bỏ qua khi close?** Log cause và suppressed exceptions.
6. **Có request/thread đang load class/resource lúc close?** Tổ chức quiescing trước, không dựa vào hành vi concurrent close không xác định.
7. **Có file watcher, antivirus, IDE, scanner hoặc process khác chạm file không?** Kiểm tra bằng công cụ quản lý handles OS.
8. **OS hiện tại là Windows hay Linux?** Khác nhau về cách rename/unlink khi file mở; “xóa được” chưa chắc FD đã đóng.
9. **JAR thuộc custom/framework class loader?** URLClassLoader API không đại diện cho mọi nested-JAR loader.
10. **Tăng memory mà file handle không tăng?** Tìm strong references giữ ClassLoader; không coi đó là cùng loại leak.

Các công cụ như Resource Monitor/Process Explorer trên Windows hoặc `lsof` trên Linux hỗ trợ xác định process đang giữ handles thật. Chúng không thay cho Java API, nhưng giúp phân biệt lỗi của loader và lỗi của một process khác. Ở chiều ngược lại, việc class unload logs không báo class v1 được thu gom **không** tự chứng minh JAR handle vẫn mở.

### Những hiểu lầm điển hình

**“Đóng URLClassLoader đồng nghĩa plugin đã bị unload.”** Không: loader close không hủy objects/classes; JVM chỉ unload khi điều kiện reachability/GC thỏa.

**“Chỉ cần gọi System.gc() rồi file sẽ không bị lock.”** Không: GC không phải API giải phóng handle; đóng đúng owner.

**“Sửa JAR tại chỗ sẽ sửa bytecode của class đã load.”** Không: tạo class loader mới để có định nghĩa class runtime mới.

**“Stream do loader mở có thể để đó vì close loader sẽ đóng.”** Dù URLClassLoader có contract về getResourceAsStream, thiết kế tốt vẫn đóng stream ngay sau khi đọc xong.

**“Linux xóa file thành công chứng minh không còn leak.”** Không: descriptor tới nội dung cũ có thể còn sống sau khi unlink.

**“Cứ đóng loader rồi thay JAR là chắc chắn chạy được trên mọi Windows.”** Không: file có thể còn được process/connection khác giữ, hoặc thiếu quyền.

**“Cứ tạo loader mới cho mỗi request là reload đơn giản.”** Không: mở thêm handles, nhân class namespaces và tăng memory/cpu. Nên dùng một loader cho mỗi **plugin version/lifecycle** đã được điều phối.

## 12. Checklist production và câu hỏi ôn tập

Checklist trước khi triển khai:

- Plugin JAR cần reload không thuộc application classpath sống lâu.
- Loader per version có owner và lifecycle rõ ràng.
- Có stop hook ngừng request, executor, threads, callbacks và registrations.
- Không concurrent class loading khi gọi `close()`.
- Mọi stream/JarFile/ZipFile độc lập có try-with-resources hoặc cleanup tương đương.
- `URLClassLoader.close()` được gọi, IOException không bị bỏ qua.
- File version cũ được dọn sau khi không còn owner giữ handle.
- Phiên bản mới dùng **loader mới**, không reuse loader đã đóng.
- Shared API/interface được định nghĩa bởi loader cha nhất quán.
- Các caches, TCCL, ThreadLocals và registries được dọn khi plugin retire.
- Có logging/monitoring file handles, class loaders và class unloading khi cần.
- Có chiến lược versioned JAR, health check và rollback.
- Quy trình được thử trên đúng OS/filesystem triển khai.

**Câu 1.** `URLClassLoader.close()` bảo đảm unload mọi class đã định nghĩa không?

**Đáp:** Không. Nó đóng loader phục vụ việc nạp class/resource mới và đóng các file do nó mở theo hợp đồng; class unloading phụ thuộc JVM/GC và tham chiếu.

**Câu 2.** Vì sao JAR vẫn bị Windows giữ sau khi đóng một URLClassLoader?

**Đáp:** Có thể parent/sibling loader, JarFile/ZipFile, JarURLConnection cache hoặc process khác vẫn giữ file handle; cần tìm đúng owner.

**Câu 3.** Vì sao sau khi thay JAR, phải tạo URLClassLoader mới?

**Đáp:** Class đã định nghĩa bởi loader cũ không tự thay bytecode; loader mới cho phép định nghĩa cùng binary name trong namespace khác.

**Câu 4.** Có nên dùng `System.gc()` để bảo đảm xóa file không?

**Đáp:** Không. Đóng stream/JAR/loader bằng API quản lý tài nguyên; GC không cung cấp timing bảo đảm và không thay thế close.

**Câu 5.** Tại sao nên triển khai JAR theo tên version riêng?

**Đáp:** Tránh ghi đè file đang được phiên cũ sử dụng, dễ chuyển traffic và rollback, chỉ xóa bản cũ khi lifecycle kết thúc.

**Câu 6.** Nếu URLClassLoader đã đóng nhưng host vẫn giữ `Class<?>` của plugin v1 thì sao?

**Đáp:** JAR handle có thể đã được đóng, nhưng class v1 vẫn được tham chiếu, vì vậy không thể kết luận nó đã được unload. Hai trạng thái phải được kiểm tra riêng.

## Tài liệu tham khảo chính thức

- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): `close()`, `getResourceAsStream` và concurrency behavior.
- [Java SE 21 — JarURLConnection](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/JarURLConnection.html): giao thức `jar:` và archive connection.
- [Java SE 21 — URLConnection](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLConnection.html): `setUseCaches(false)` và cache policy.
- [Java SE 21 — JarFile](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/jar/JarFile.html): đọc và đóng JAR tường minh.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): class loading lifecycle.
- [JVMS Java SE 21 §5.3](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): định nghĩa class, binary name và defining loader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-unloading-and-memory-010">Quan sát unloading và tránh kết luận từ một lần gọi System.gc</a>

<details>
<summary>Xem chi tiết</summary>

# Quan sát class unloading và tránh kết luận từ một lần gọi `System.gc()`

## 1. Mục tiêu và bản chất của class unloading

**Class unloading** (dỡ lớp) là việc JVM có thể loại bỏ định nghĩa lớp/giao diện đã nạp cùng metadata liên quan khi chúng không còn cần thiết. Đây là một hoạt động quản lý vòng đời lớp và bộ nhớ, khác với **class loading** (nạp lớp), **linking** (liên kết), **initialization** (khởi tạo tĩnh) và việc thu gom các object thông thường trên heap.

Trong Java, danh tính của một lớp không chỉ là tên nhị phân như `lesson.UnloadTarget`, mà còn gắn với **defining class loader** đã định nghĩa lớp đó. Hai class loader độc lập có thể định nghĩa hai lớp cùng tên nhưng chúng vẫn là hai kiểu khác nhau ở runtime. Khi làm ứng dụng có plugin, hot reload, redeploy hoặc tạo nhiều class loader động, việc loader cũ có được thu hồi hay không quyết định liệu các lớp do nó định nghĩa có thể được dỡ hay không.

Các điều kiện và giới hạn cần nhớ:

- **Một object của lớp đã nạp còn sống** có thể giữ lớp và defining loader còn reachable. Một tham chiếu mạnh trực tiếp tới `Class<?>`, `Method`, `Constructor`, instance, loader, hoặc cấu trúc runtime liên quan cũng có thể ngăn loader được thu hồi.
- Với lớp được định nghĩa bởi **class loader do ứng dụng tạo**, JVM chỉ có thể dỡ lớp khi điều kiện về khả năng thu hồi defining loader thỏa mãn. Chỉ gọi `close()` trên `URLClassLoader` là đóng tài nguyên như JAR/file handle; **không phải lệnh unload lớp**.
- Lớp do bootstrap loader định nghĩa thường tồn tại theo vòng đời JVM; không nên dùng ví dụ `java.lang.String` để kiểm chứng unload của một plugin.
- Trạng thái “đã hết tham chiếu mạnh từ ứng dụng” chỉ làm loader **có thể** được thu hồi. Thời điểm GC, thời điểm unloading và việc trả lại bộ nhớ vật lý cho hệ điều hành phụ thuộc JVM/collector/cấu hình.

Một đường giữ tham chiếu thường gặp:

```text
GC root
  └── static cache thuộc ứng dụng/loader sống lâu
        └── instance của plugin
              └── lớp plugin / defining class loader
```

Nếu cache, thread đang chạy hoặc thread context class loader vẫn giữ plugin, vòng đời của plugin **chưa kết thúc về mặt reachability** dù mã ứng dụng đã “undeploy” nó.

## 2. Vì sao một lần `System.gc()` không chứng minh được gì?

`System.gc()` là **lời đề nghị JVM nỗ lực thực hiện garbage collection**, không phải cam kết “GC xong, mọi lớp đủ điều kiện đã unload”. Đặc tả API không bảo đảm thu hồi một số object nhất định, không bảo đảm một `Reference` cụ thể được clear, cũng không bảo đảm hoàn tất ở thời điểm trả về. HotSpot có thể được cấu hình bỏ qua explicit GC (ví dụ `-XX:+DisableExplicitGC`). Cơ chế và thời điểm xử lý class unloading còn phụ thuộc garbage collector và chu kỳ thu gom tương ứng.

Phải phân biệt bốn câu:

1. **“Đã gọi `System.gc()`”**: chỉ biết code thực thi lời đề nghị.
2. **“Đã có GC”**: cần GC log/JFR hoặc bằng chứng quan sát thực sự; vẫn **không đồng nghĩa** một class loader đã chết.
3. **“Class loader không còn strong reachability”**: là tiền đề để loader có thể bị GC; không đồng nghĩa JVM đã dỡ lớp ngay lúc đó.
4. **“Đã có class unload”**: cần sự kiện unloading, hoặc tín hiệu trực tiếp khác có phạm vi diễn giải rõ ràng.

Do đó hai kết luận sau đều sai:

- “Gọi `System.gc()` một lần mà không thấy unload, vậy chắc chắn bị leak.”
- “Gọi `System.gc()` xong heap/metaspace giảm, vậy lớp plugin X chắc chắn đã được dỡ.”

Heap object collection, class unloading và giảm `metaspace committed` là các hiện tượng liên quan nhưng **không phải ba phép đo thay thế cho nhau**. Sau unloading, bộ cấp phát metaspace còn có thể giữ phần bộ nhớ để tái sử dụng; mức committed/reserved không nhất thiết giảm ngay.

## 3. Quan sát bằng những tín hiệu nào?

Trên **HotSpot JDK 17+**, sử dụng phối hợp log sự kiện, thống kê JVM và kiểm tra tham chiếu. Không áp dụng nguyên xi cú pháp cờ cho mọi bản triển khai JVM.

| Tín hiệu | Nó xác nhận được gì? | Giới hạn cần ghi trong kết luận |
|---|---|---|
| Log `-Xlog:class+unload=info` xuất hiện tên lớp plugin | Bằng chứng trực tiếp JVM đã ghi nhận sự kiện unload lớp đó | Phải bật log trước thời điểm sự kiện; thiếu dòng log chưa tự động chứng minh leak |
| `WeakReference<ClassLoader>.get() == null` | Tham chiếu yếu tới loader đã được GC clear; hữu ích để kiểm tra loader không còn được giữ sống | Không thay cho timestamp/sự kiện unload của từng lớp; một kết quả chưa clear không chứng minh leak |
| `ClassLoadingMXBean.getUnloadedClassCount()` tăng | Có các lớp được JVM ghi nhận là đã unload kể từ lúc khởi động | Đếm trên **toàn JVM**, không khẳng định chính lớp đang khảo sát |
| `jcmd <pid> VM.classloader_stats` ở nhiều thời điểm | Thống kê class loader/lớp tại từng thời điểm khảo sát | Snapshot; khác biệt có thể do đồng thời nạp thêm loader/lớp |
| `jcmd <pid> VM.metaspace` và GC log | Xu hướng vùng metadata, thời điểm GC và áp lực bộ nhớ | Dung lượng không đổi hoặc giảm không đủ để kết luận một lớp cụ thể đã unload |

Để ghi log từ lúc khởi chạy:

```bash
java -Xlog:class+unload=info,gc*=info -cp . UnloadingProbe
```

Với ứng dụng thật có nhiều log, có thể chuyển sang file:

```bash
java -Xlog:class+unload=info,gc*=info:file=class-gc.log:time,uptime,level,tags -jar app.jar
```

Giá trị của log nằm ở **sự kiện và mốc thời gian thực tế**, không phải suy đoán “sau khi chạy câu lệnh GC”. Các lệnh `jcmd` yêu cầu JDK và quyền attach phù hợp; có thể kiểm tra lệnh JVM hỗ trợ bằng `jcmd <pid> help`.

## 4. Thí nghiệm Java 17+: nạp lớp qua loader riêng rồi quan sát

Thí nghiệm dùng hai file Java và thư mục chứa plugin được biên dịch riêng. Mục tiêu là tạo điều kiện để loader có thể được thu hồi, sau đó đọc bằng chứng. Kết quả thu hồi/unload **không được ép phải xảy ra trong mọi lần chạy**.

### Bước 1: tạo lớp plugin

File **`plugin-src/lesson/UnloadTarget.java`**:

```java
package lesson;

public class UnloadTarget {
    public static String answer() {
        return "ready";
    }
}
```

### Bước 2: tạo chương trình điều khiển

File **`UnloadingProbe.java`**, nằm tại thư mục làm việc gốc, **không** nằm trong `plugin-classes`:

```java
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class UnloadingProbe {
    private static WeakReference<ClassLoader> loadOnce() throws Exception {
        URL pluginDirectory =
                Path.of("plugin-classes").toAbsolutePath().toUri().toURL();

        // Parent platform loader không thấy class plugin trong plugin-classes.
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { pluginDirectory },
                ClassLoader.getPlatformClassLoader())) {
            Class<?> pluginClass =
                    Class.forName("lesson.UnloadTarget", true, loader);
            Object result = pluginClass.getMethod("answer").invoke(null);
            System.out.println("Plugin: " + result);

            // Chỉ trả về weak reference: không giữ Class, instance,
            // Method hay loader bằng strong reference ở phương thức gọi.
            return new WeakReference<>(loader);
        } // close() giải phóng tài nguyên; không tự unload class.
    }

    public static void main(String[] args) throws Exception {
        ClassLoadingMXBean counter =
                ManagementFactory.getClassLoadingMXBean();
        long unloadedBefore = counter.getUnloadedClassCount();

        WeakReference<ClassLoader> loaderRef = loadOnce();

        for (int attempt = 0;
             attempt < 20 && loaderRef.get() != null;
             attempt++) {
            System.gc(); // Đề nghị, KHÔNG phải cam kết.
            byte[] temporary = new byte[256 * 1024];
            temporary[0] = (byte) attempt;
            Thread.sleep(100);
        }

        System.out.println(
                "Loader weak reference cleared: " + (loaderRef.get() == null));
        System.out.println(
                "JVM-wide unloaded classes delta: "
                + (counter.getUnloadedClassCount() - unloadedBefore));
    }
}
```

**Giải thích quyết định thiết kế:**

- `plugin-classes` phải nằm ngoài classpath của chương trình điều khiển, để `lesson.UnloadTarget` thực sự được định nghĩa bởi `URLClassLoader` riêng. Nếu lớp được parent loader nạp trước, thí nghiệm không còn đo đúng loader mong muốn.
- `loadOnce()` tách thành một phương thức riêng để các biến cục bộ `pluginClass`, reflection result và loader hết phạm vi; JVM/JIT có quyền giữ một số tham chiếu tạm thời lâu hơn dự đoán, nên vẫn phải quan sát thực nghiệm.
- `WeakReference` không giữ loader sống bằng strong reference. `URLClassLoader.close()` chỉ đóng resource. Vòng lặp cho JVM nhiều **cơ hội** GC hơn một lần gọi, nhưng số lần lặp cũng không biến unloading thành việc bảo đảm.
- Bộ đếm từ `ClassLoadingMXBean` là toàn JVM. Ngoài plugin, JVM có thể load/unload lớp khác; vì vậy delta lớn hơn 0 là dấu hiệu hỗ trợ, **không nhận dạng lớp cụ thể**.
- Không gọi `System.exit()` để “xác minh” vì khi process kết thúc mọi bộ nhớ process được OS thu hồi; đó không phải class unloading **trong JVM đang chạy**.

### Bước 3: biên dịch và chạy

Tạo trước các thư mục `plugin-src/lesson` và `plugin-classes`. Từ thư mục chứa hai file nêu trên, chạy trên JDK 17+:

```bash
javac -d plugin-classes plugin-src/lesson/UnloadTarget.java
javac UnloadingProbe.java
java -Xlog:class+unload=info -cp . UnloadingProbe
```

**Lưu ý:** Không thêm `plugin-classes` vào `-cp` của `UnloadingProbe`; chương trình chủ chỉ cần `.`. Trên Windows, dấu phân tách classpath có thể khác khi dùng nhiều entry, nhưng ví dụ chỉ dùng một entry nên `-cp .` dùng được trên cả Windows và Unix.

Output ứng dụng **chắc chắn bắt đầu bằng**:

```text
Plugin: ready
```

Hai dòng ứng dụng cuối có **giá trị phụ thuộc lần chạy**:

```text
Loader weak reference cleared: true
JVM-wide unloaded classes delta: 1
```

Đây là **một ví dụ output có thể xảy ra**, **không phải output được bảo đảm**. Giá trị đầu có thể là `false`; số delta có thể bằng 0 hoặc lớn hơn 1. Nếu có sự kiện dỡ lớp, log HotSpot có thể chứa dòng có tag `[class,unload]` và tên `lesson.UnloadTarget`; prefix/timestamp/địa chỉ nội bộ là động. Log này là bằng chứng sát với câu hỏi “lớp plugin có unload không?” hơn việc thấy `System.gc()` đã được gọi.

Chạy lại với cấu hình HotSpot bỏ qua explicit GC để quan sát khác biệt về hành vi; đây là **thử nghiệm so sánh**, không phải cách buộc JVM unload hay chứng minh leak:

```bash
java -XX:+DisableExplicitGC -Xlog:class+unload=info -cp . UnloadingProbe
```

Ngay cả khi explicit GC bị bỏ qua, **GC tự nhiên do áp lực bộ nhớ vẫn có thể xảy ra**. Không dùng việc không thấy một dòng unload ở lần chạy này làm kết luận cuối.

## 5. Cách diễn giải kết quả theo tình huống

**Tình huống A — Có log unload cho `lesson.UnloadTarget`.** Có thể kết luận JVM đã ghi nhận unload lớp thử nghiệm. Nên lưu thời điểm, cờ JVM, log tương ứng và loader đã nạp lớp để có thể lặp lại/khoanh vùng.

**Tình huống B — `loaderRef.get() == null` nhưng chưa thấy dòng unload.** Đây là tín hiệu loader đã được GC clear qua weak reference. Kiểm tra log có bật đủ sớm/đúng tag hay không, thời gian quan sát và JVM đang dùng. Đừng suy ra “đã có log unload” nếu thực tế không có.

**Tình huống C — Loader reference vẫn chưa clear sau vòng lặp.** Chưa đủ cơ sở để gắn nhãn leak. Có thể chưa có chu kỳ GC phù hợp, explicit GC bị bỏ qua, JIT còn giữ tham chiếu tạm thời, hoặc có strong reference thật sự. Tăng cửa sổ quan sát và xác minh đồ thị tham chiếu trước khi chẩn đoán.

**Tình huống D — Delta bộ đếm dương nhưng không có log lớp cần quan sát.** Chỉ có thể khẳng định có **một số lớp nào đó** trong process unload. Không được gán nhầm kết quả toàn JVM cho plugin X.

**Tình huống E — Metaspace committed chưa giảm.** Không đủ chứng minh classes không unload. Bộ cấp phát có thể giữ chunk để tái sử dụng, và các loader khác có thể đang nạp class mới đồng thời.

## 6. Quy trình theo dõi ở ứng dụng plugin/redeploy thực tế

1. **Đặt giả thuyết chính xác.** Ví dụ: “Sau khi plugin phiên bản A dừng và rời cache, loader A không còn root giữ sống; lớp `com.example.plugin.Handler` dự kiến có thể unload sau một số chu kỳ GC”.
2. **Ghi baseline trước hành động:** JVM/JDK version, collector, cờ GC, thời điểm, log class unload và snapshot loader/metaspace; đặt tên hoặc ghi lại danh tính loader thay vì chỉ dựa vào class name.
3. **Thực hiện thao tác vòng đời:** ngừng task/timer, unregister listener/MBean/driver, giải phóng cache, đóng tài nguyên loader rồi bỏ strong reference. Kiểm tra xem phần mềm có thực sự giải phóng loader cũ không.
4. **Quan sát theo khoảng thời gian và nhiều chu kỳ GC thực tế** trong điều kiện tải có kiểm soát; thu log unload và snapshot tại ít nhất các mốc trước tải plugin, sau unload logic, sau giai đoạn quan sát. Không dùng riêng một lần `System.gc()` làm ranh giới.
5. **Đối chiếu ít nhất hai nguồn bằng chứng độc lập:** log unload có class/loader mục tiêu; weak reference loader hoặc phân tích GC roots; thống kê toàn JVM chỉ là dữ liệu bối cảnh.
6. **Nếu có dấu hiệu giữ loader lâu bất thường**, truy ngược từ GC roots tới loader/plugin. So sánh các chu kỳ load→use→dispose lặp lại để phân biệt một vòng đời dài hợp lệ với tăng trưởng tích lũy.

Các lệnh chẩn đoán HotSpot thường dùng khi ứng dụng còn chạy:

```bash
jcmd
jcmd <pid> VM.classloader_stats
jcmd <pid> VM.metaspace
jcmd <pid> help
```

Lệnh `jcmd` xem snapshot, không “kích hoạt class unloading”. Trong container, attach có thể cần quyền cùng user/JVM namespace và công cụ JDK hiện diện; hãy lưu context cùng kết quả.

Với chương trình chạy lâu, có thể lấy thông tin tải lớp thông qua `ManagementFactory.getClassLoadingMXBean()`:

- `getLoadedClassCount()`: số lớp hiện được load (snapshot).
- `getTotalLoadedClassCount()`: tổng lớp từng được load kể từ lúc JVM khởi động.
- `getUnloadedClassCount()`: tổng lớp từng được unload kể từ lúc JVM khởi động.

Không trừ `totalLoaded - unloaded` để “xác định loader X” và không coi xu hướng metadata tăng là leak nếu không kiểm soát lưu lượng nạp lớp, số plugin và chu kỳ GC.

## 7. Vì sao plugin đã đóng vẫn không unload?

Đóng loader không xóa **host references**. Khi không thấy class unload event, kiểm tra `path to GC roots` qua static caches/`Class<?>`, callbacks/listeners, `ServiceLoader` providers, thread TCCL/`ThreadLocal`, pending executor tasks và registries của JMX/JDBC/logging.

Nhưng **không thấy sự kiện unload không chứng minh leak**: VM có thể chưa chọn thực hiện unloading. Phải đối chiếu loader generations, event logs và heap retaining paths theo nhiều chu kỳ hoạt động.

## 8. Những kết luận cần tránh và cách nói đúng

| Suy luận vội | Kết luận có cơ sở hơn |
|---|---|
| “`System.gc()` trả về nên lớp đã unload.” | “Đã yêu cầu GC; cần sự kiện class unloading hoặc bằng chứng reachability/loader riêng.” |
| “Một lần `System.gc()` không có event, chắc chắn memory leak.” | “Trong cửa sổ quan sát đó chưa ghi nhận event; cần khảo sát GC/roots và lặp lại.” |
| “`URLClassLoader.close()` dỡ lớp ngay.” | “`close()` đóng resource của loader; tham chiếu và GC quyết định khả năng thu hồi.” |
| “Weak reference chưa null nghĩa là JVM không unload được.” | “Loader còn được quan sát là reachable/chưa bị clear ở thời điểm đọc; chưa biết nguyên nhân.” |
| “`unloadedClassCount` tăng nên plugin vừa được dỡ.” | “Số lớp unload toàn JVM tăng; cần nhận dạng chính lớp/loader qua log hoặc bằng chứng bổ sung.” |
| “Metaspace không giảm tức là classes còn sống.” | “Metaspace sử dụng/commit có thể không giảm tức thời hoặc bị lớp mới bù vào.” |

Một báo cáo quan sát tốt nên ghi rõ: **JDK/JVM, collector và flags; tên lớp + defining loader; thao tác release; mốc thời gian; số lần/chu kỳ GC thực tế; log `class+unload`; trạng thái weak reference; snapshot stats; phần chưa thể kết luận**.

## 9. Tự kiểm tra

1. Tại sao cùng binary name có thể được hai loader nạp thành hai runtime type khác nhau?
2. Tại sao việc ngắt sử dụng plugin và gọi `close()` chưa đủ để khẳng định unload?
3. Nếu counter unload tăng từ 100 lên 105 nhưng không thấy event plugin, có thể nói gì?
4. Nếu sau lần gọi `System.gc()` đầu tiên `WeakReference` vẫn chưa clear, cần kiểm tra những giả thuyết nào trước khi nói “leak”?
5. Bằng chứng nào đủ cụ thể hơn cho câu hỏi “`lesson.UnloadTarget` có unload hay chưa”: GC log chung, thay đổi metaspace, hay log `class+unload` có tên lớp?

**Đáp án định hướng:** (1) Kiểu runtime được xác định bởi tên và defining loader; (2) strong references còn có thể giữ loader và `close` không unload; (3) chỉ biết toàn JVM có 5 lớp unload; (4) thời điểm/kiểu GC, JVM flags, tham chiếu mạnh, stack/JIT/debugger; (5) event `class+unload` mang tên lớp quan tâm, kết hợp nhận dạng loader khi cần.

## Tài liệu chuẩn để đối chiếu

- [Java SE 17 — `System.gc()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html): tài liệu nêu rõ giới hạn bảo đảm của yêu cầu GC.
- [Java SE 17 — `ClassLoadingMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ClassLoadingMXBean.html): bộ đếm loaded/unloaded toàn JVM.
- [JDK 17 — `jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `VM.classloader_stats`, `VM.metaspace` và lệnh chẩn đoán HotSpot.
- [Java Language Specification `12.7`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7): unloading của class và interface.


</details>

- [Quay lại đầu trang](#back-to-top)
