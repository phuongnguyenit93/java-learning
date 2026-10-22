<a id="back-to-top"></a>

# Khởi động nhanh

## Menu
- [Phân tách chi phí tạo tiến trình, nạp lớp, khởi tạo và warm-up](#fast-startup-001)
- [Class Data Sharing (CDS) chia sẻ dữ liệu lớp như thế nào](#fast-startup-002)
- [AppCDS, archive tĩnh và archive động](#fast-startup-003)
- [Tạo và xác nhận sử dụng CDS archive bằng log nạp lớp](#fast-startup-004)
- [AOT cache của HotSpot: học từ lần chạy thử, tạo cache và dùng lại theo phiên bản hỗ trợ](#fast-startup-005)
- [Điều kiện tương thích của archive hoặc cache với JDK, ứng dụng và môi trường](#fast-startup-006)
- [Phân biệt AOT cache của JVM với biên dịch thành chương trình native](#fast-startup-007)
- [Đo thời gian sẵn sàng và dung lượng bộ nhớ để đánh giá lợi ích khởi động](#fast-startup-008)

## <a id="fast-startup-001">Phân tách chi phí tạo tiến trình, nạp lớp, khởi tạo và warm-up</a>

<details>
<summary>Xem chi tiết</summary>

# Phân tách chi phí tạo tiến trình, nạp lớp, khởi tạo và warm-up

## 1. Vấn đề: “Java khởi động chậm” không chỉ là một con số

Khi một ứng dụng Java được khởi chạy, nhiều công đoạn có thể góp vào thời gian trước khi nó phục vụ kết quả hữu ích: hệ điều hành tạo tiến trình, launcher/JVM khởi tạo runtime, class loading và linking, class initialization, framework/application bootstrap, kết nối đến dependencies, và sau đó **JIT warm-up** để đạt hiệu năng vận hành ổn định.

Một câu “startup 2 giây” chỉ hữu ích khi đã trả lời **tính từ đâu đến đâu**. Chẳng hạn:

- **Process creation:** thời gian OS bắt đầu một tiến trình thực thi Java; liên quan scheduling, executable, environment, dynamic libraries.
- **VM/launcher startup:** Java launcher đọc flags/module/classpath, JVM tạo các runtime structures, memory areas và threads cần thiết trước khi gọi `main`.
- **Loading/linking:** tìm bytecode, tạo class representation, verification, preparation và resolution theo chính sách runtime.
- **Class initialization:** chạy static field initializers và static initialization blocks tương ứng, được biên dịch thành `<clinit>`.
- **Application initialization:** tạo DI container, scan annotations, init serializers, mở pools, đọc config, migrations, nạp dữ liệu.
- **First useful response/readiness:** thời điểm business flow đầu tiên hoặc readiness endpoint thực sự hoạt động.
- **Warm-up:** profiling, tiered compilation C1/C2, method linking, lazy classes, JIT inlining/deoptimization, cache và GC trạng thái dần thay đổi.
- **Steady state:** khoảng hệ thống có throughput/latency/GC/CPU tương đối ổn định dưới workload đại diện.

**Không được cộng tất cả bằng cách lấy thời gian của một câu `System.nanoTime()` đặt trong `main`**. Đoạn Java đó đã bỏ lỡ toàn bộ OS process creation và phần JVM startup trước `main`, và thời điểm class initializer của main class cũng có thể xảy ra trước câu đầu tiên của `main`.

> **Mục tiêu của bài:** phân rã các mốc có thể đo, chỉ ra ranh giới đo đạc, minh họa **load khác init**, và thiết kế benchmark tách **cold-start / first response / warm-up / steady-state**, thay vì gộp thành “JVM launch mất bao nhiêu ms”.

## 2. Timeline đầy đủ: OS launch đến peak/steady throughput

~~~text
mốc ngoài tiến trình                                    bên trong JVM
t0: OS nhận yêu cầu tạo process
       |
       v
[OS process startup + java launcher + JVM bootstrap]
       |
       v
[JVM khởi tạo runtime, hệ thống classloader, GC/JIT runtime]
       |
       v
[Load/Link/Initialize main class] ─────> t_main: vào main()
       |
       v
[Application/bootstrap: config, DI, pools, external dependencies]
       |
       +──────────────> t_first: first useful result
       |
       +──────────────> t_ready: readiness đạt tiêu chí
       |
       v
[Realistic warm-up: JIT compilation, caches, lazy class init, GC]
       |
       v
t_steady: candidate steady-state windows
       |
       v
[Optional saturation / peak / long-soak measurements]
~~~

Sơ đồ chỉ là **các nhóm công việc**. Trong hệ thống thực, chúng có thể chồng lắp:

- Thread pools và background tasks có thể làm application init **song song**.
- JIT compilation có thể xảy ra **trong lúc** bootstrap, trước first response.
- Lazy initialization có thể dời đến **sau readiness**, gây cold-path latency.
- CDS có thể rút ngắn một số class-loading work nhưng không loại toàn bộ linker, `<clinit>` hoặc application bootstrap.
- Một service báo ready trước khi “nóng” hoàn toàn; app latency có thể tiếp tục cải thiện sau readiness.
- `t_first` và `t_ready` không buộc theo một thứ tự tuyệt đối; phụ thuộc protocol và workload.
- Hệ thống có dynamic request mix có thể thay đổi workload liên tục và **không có một steady-state toàn cục duy nhất**.

**Những time windows phải được gắn nhãn theo mục đích**, không gộp thành “JVM start” rồi kết luận GC/JIT là nguyên nhân.

## 3. Công đoạn A — chi phí tạo tiến trình OS và khởi động JVM

### 3.1. Trước `main()` đã có điều gì?

Khi người dùng gọi:

~~~bash
java -cp app.jar com.example.Main
~~~

Hệ điều hành và launcher thực hiện công việc khởi tạo process và nạp chương trình. Sau đó HotSpot phải chuẩn bị VM runtime: parsing options, runtime libraries, hệ thống class loading, memory management, thread/runtime structures, GC/JIT services và chuẩn bị thực thi main class.

Những phần cụ thể phụ thuộc OS, Java vendor, JDK build, flags, filesystem, classpath/module path và thiết kế launcher. Không viết một “trình tự nano giây” bất biến cho mọi JVM.

**Không nhầm `javac` với `java`:** `javac` biên dịch source thành bytecode `.class` trước khi ứng dụng chạy. Khi launch bình thường bằng `java Main`, JVM **không tự gọi lại `javac` cho tất cả files**. JIT có thể biên dịch **bytecode thành native code** trong lúc chạy, đó là hoạt động khác hẳn javac.

### 3.2. Tại sao `System.nanoTime()` đầu `main()` không đo process creation?

~~~java
public static void main(String[] args) {
    long start = System.nanoTime();
    // Đo từ đây, không phải từ lúc OS tạo process.
}
~~~

Lúc code đầu `main` chạy, **VM đã được tạo đủ để gọi main**, main class đã đạt điều kiện class initialization. Vì vậy lấy số thời gian từ `start` tới `READY` chỉ đo **một phần application init**, không đo được chi phí OS spawn và các bước VM xảy ra trước `main`.

Muốn đo end-to-end từ bên ngoài, dùng một tiến trình cha hoặc load generator:

~~~bash
time java -cp . StartupPhasesDemo
~~~

Ví dụ PowerShell (Windows):

~~~powershell
Measure-Command { java -cp . StartupPhasesDemo | Out-Null }
~~~

Lệnh `time` hoặc `Measure-Command` đo **toàn thời gian process chạy tới exit**, gồm process launch, VM boot, work trong `main`, shutdown và overhead wrapper. Đây **không riêng là startup latency** nếu ứng dụng tiếp tục làm việc lâu.

**Đối với server sống lâu:** không đợi process exit mới lấy số; công cụ bên ngoài nên đo từ **lúc spawn/start request** đến khi nhận tín hiệu **first useful response** hoặc **readiness** theo điều kiện được định nghĩa.

### 3.3. Tách OS, launcher và VM startup có tuyệt đối được không?

Công cụ Java đơn lẻ thường **không đủ** để chia tách hoàn toàn time trước `main` thành `OS spawn`, `dynamic loader`, `VM create`, `classloader init` mà không đụng nhau. Cần instrument theo đúng cấp:

| Muốn đo | Quan sát thích hợp |
| --- | --- |
| OS process start → process exit | Shell external timer |
| Process start → first useful result | Parent-process timestamp / external probe |
| Process start → ready | Orchestrator event + health/ready protocol, hoặc external poll |
| OS spawn/exec and library loading | OS profiler, process tracing phù hợp với platform |
| VM startup trước `main` | Runtime-specific startup diagnostics/JFR + external measurement |
| Code trong `main` | `System.nanoTime()` milestones |
| Class loading cụ thể | HotSpot Unified Logging, JFR nếu bật |
| Class initialization cụ thể | Logs class-init + marker trong `<clinit>` |
| JIT warm-up | Compilation events, throughput time windows, JFR |

Không cộng trực tiếp những duration từ **khác clocks, khác mốc hoặc phase chồng lắp**. Nếu `mainElapsedMs=20`, external `time java` đo 55 ms, phần chênh 35 ms **không được tự gắn nhãn hoàn toàn là JVM bootstrap**: nó còn shell/OS process handling, output, shutdown và những khoảng đo không tương đương.

## 4. Công đoạn B — nạp lớp, liên kết và khởi tạo là ba bước khác nhau

Java Language Specification (JLS 17, Chương 12) tách:

- **Loading:** tìm dạng nhị phân class/interface, dùng class loader phù hợp để tạo representation của type.
- **Linking:** gồm **verification**, **preparation**, và **resolution** có thể được làm sớm hoặc lazy theo giới hạn specification.
- **Initialization:** thực thi các class-variable initializers và static initializer blocks, tương ứng `<clinit>` khi compiler tạo ra.

Không được hiểu rằng “class vừa được load thì static initializer chắc đã chạy”. Không được đồng nhất **preparation** với **initialization**.

### 4.1. Loading: tìm bytecode và tạo class representation

Classpath/module path, resource lookup, đọc `.class`, classloader delegation, verification dependencies và built-in/custom class loaders có thể ảnh hưởng chi phí. Một framework scan hàng chục nghìn classes hoặc đọc annotation metadata theo nhiều chiến lược có thể tốn startup time, nhưng **không phải mọi lần metadata scan đều thực sự initialize toàn bộ classes**.

Loading cost còn liên quan disk/page cache và archive. Lần mở đầu classpath JAR trên OS cache lạnh khác hẳn khi OS đã cache file. Đó là lý do benchmark startup cần kiểm soát **cold/warm filesystem state** nếu nó ảnh hưởng workload thực.

### 4.2. Linking: verify, prepare, resolve

| Bước | Ý nghĩa |
| --- | --- |
| Verification | Xác minh bytecode/formats/type constraints đáp ứng yêu cầu JVM |
| Preparation | Cấp phát/đặt giá trị mặc định cho `static` fields và một số cấu trúc metadata |
| Resolution | Chuyển symbolic references trong constant pool sang thực thể runtime thích hợp |

**Resolution có thể lazy.** Class đã được load, verify và chuẩn bị nhưng một số symbolic references chỉ được resolve khi đường code tương ứng thực sự được sử dụng. Vì thế khi một route mới lần đầu được gọi, thêm linking work có thể xuất hiện **sau startup/ready**.

JLS/JVMS áp đặt hành vi và lỗi hợp lệ nhưng không định nghĩa một chi phí mili giây cố định cho tất cả classes.

### 4.3. Initialization: `<clinit>`

Các static field initializers không phải compile-time constants, và `static { ... }`, thường được compiler sắp vào method đặc biệt `<clinit>`.

~~~java
final class Expensive {
    static int[] table = buildTable();
    static {
        System.out.println("Expensive initialized");
    }
}
~~~

Đây là **class minh họa**, `buildTable()` do application định nghĩa. Khi lần sử dụng chủ động yêu cầu initialization, JVM chạy `<clinit>` theo quy tắc một lần trên mỗi **class identity** trong một JVM execution, với synchronization semantics được specification quy định.

**Một class identity** gồm binary name và defining class loader; hai classloaders khác nhau có thể tạo hai `Class` objects khác nhau với initialization state riêng. Không được phát biểu “static initializer chỉ chạy một lần trên toàn bộ mọi process/JVM và classloaders”.

### 4.4. Các trigger khởi tạo class quan trọng

Trong nhiều trường hợp, class initialization được kích hoạt trước:

- Tạo instance bằng `new` của class cần init.
- Truy cập/ghi `static` field không phải constant variable theo JLS.
- Gọi `static` method.
- Reflection hoặc method handles trong những thao tác có active-use semantics.
- Khởi tạo main class trước khi JVM gọi `main()`.

Một điểm kinh điển:

~~~java
static final int CONST = 7; // constant variable nếu khởi tạo bằng constant expression
static int counter = 7;     // không phải constant variable
~~~

Truy cập một **constant variable** có thể không buộc class khai báo được initialize, trong khi truy cập `counter` có thể kích hoạt initialization. Không dùng `static final` đơn thuần làm tiêu chí: `static final int X = parse(...)` không phải compile-time constant.

`SomeType.class`, `ClassLoader.loadClass(name)` hoặc `Class.forName(name, false, loader)` **có thể có class representation mà không bắt buộc chạy `<clinit>` ngay**. Nhưng một `Class.forName(name)` overload mặc định **thường yêu cầu initialization**.

## 5. Java 17 runnable: nạp lớp mà chưa khởi tạo

Tạo file **`StartupPhasesDemo.java`**:

~~~java
public final class StartupPhasesDemo {
    static class InitProbe {
        static {
            System.out.println("probe=<clinit>");
        }
        static int value = 41;
    }

    static class LazyService {
        static {
            System.out.println("lazy=<clinit>");
        }
        static String work() {
            return "READY";
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("main=entered");
        Class<?> type = Class.forName(
                "StartupPhasesDemo$InitProbe",
                false,
                StartupPhasesDemo.class.getClassLoader());
        System.out.println("loaded=" + type.getSimpleName());
        System.out.println("aboutToInitialize=true");
        System.out.println("probeValue=" + InitProbe.value);
        System.out.println("serviceResult=" + LazyService.work());
        System.out.println("end=true");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 StartupPhasesDemo.java
java StartupPhasesDemo
~~~

**Stdout chính xác đã biên dịch và chạy thực tế trên JDK 24 với `--release 17`:**

~~~text
main=entered
loaded=InitProbe
aboutToInitialize=true
probe=<clinit>
probeValue=41
lazy=<clinit>
serviceResult=READY
end=true
~~~

### Tại sao output theo thứ tự này?

1. Vào `main()` ⇒ main class đủ điều kiện đã initialize (nó không có static block in marker).
2. `Class.forName(binaryName, false, loader)` yêu cầu find/load/link type tương ứng **nhưng không yêu cầu initialize** nó; vì vậy chưa in `probe=<clinit>`.
3. `type.getSimpleName()` có thể đọc metadata để trả `InitProbe`, không kích hoạt static block của chính `InitProbe` trong ví dụ này.
4. Lần đầu đọc `InitProbe.value` là **active use** của static nonconstant field, nên JVM khởi tạo `InitProbe` trước ⇒ in `probe=<clinit>` rồi `probeValue=41`.
5. Lần đầu gọi `LazyService.work()` cũng kích hoạt initialization của `LazyService` ⇒ in `lazy=<clinit>`, rồi method trả `READY`.
6. Hai classes có thể đã được load/link vào những thời điểm khác nhau; **mốc in marker chỉ phản ánh initialization**, không phải toàn bộ chi phí trước đó.

**Không đem đoạn này kết luận mọi JVM sẽ đọc JAR tại thời điểm `Class.forName` được gọi.** Class có thể đã loaded, dữ liệu nằm trong CDS archive/OS page cache hoặc JVM đã resolve liên quan sớm. Ví dụ chứng minh **phân biệt hai hành vi API**, không phải đo cost ổ cứng.

### `Class.forName` nhiều overload khác nhau

~~~java
Class.forName("pkg.Type");                        // initialize theo API contract
Class.forName("pkg.Type", false, someClassLoader); // không yêu cầu initialize
~~~

Đây là **mảnh code minh họa**, `pkg.Type` và `someClassLoader` cần tồn tại thật. Không dùng overload có boolean sai rồi tin rằng mình đã đo riêng class load.

## 6. Instrument class loading và class initialization bằng HotSpot logs

Trên HotSpot Java 17+, **Unified Logging** có tags cho class load và class init. Ví dụ:

~~~bash
java '-Xlog:class+load=info,class+init=info' StartupPhasesDemo
~~~

Đây là mẫu cú pháp Bash; trên PowerShell, có thể đặt cùng option trong single quotes. JVM cụ thể nên được xác minh bằng:

~~~bash
java -Xlog:help
~~~

- `class+load=info` cho biết nhiều classes được load và nguồn/loader tương ứng, thường có timestamp decorations.
- `class+init=info` cho các initialization events khi VM phát log ở mức tương ứng.
- `class+resolve=debug` hoặc những tags khác có thể giúp xem resolution khi bản JVM hỗ trợ, nhưng log volume lớn.
- Dùng `file=...:uptime,level,tags` để ghi log và so mốc startup theo uptime.
- `PrintCompilation` giúp quan sát JIT events, nhưng **không phải class loading**.

**Lưu ý quan trọng:** log markers khác với timers phase hoàn chỉnh. Một dòng `class+load` thường chỉ là **event** ghi class, không cung cấp đầy đủ “load class này tốn N ms độc lập hoàn toàn”. Logger cũng có overhead, đặc biệt khi có hàng nghìn events.

Có thể chạy:

~~~bash
java '-Xlog:class+load=info:file=classes.log:uptime,level,tags' StartupPhasesDemo
~~~

Sau đó tìm binary names `StartupPhasesDemo$InitProbe` và `StartupPhasesDemo$LazyService`; trên lần kiểm chứng JDK 24, log thực tế ghi được cả hai classes. Nhưng class log **không tự chứng minh** initialization cost; cần init events hay markers khác.

### Dùng JFR trong các bài điều tra thật

Java Flight Recorder (JFR) có các class load, class define, compilation, GC, CPU và thread events tùy **recording settings, event thresholds và JVM build**. Ví dụ:

~~~bash
java '-XX:StartFlightRecording=filename=startup.jfr,settings=profile' StartupPhasesDemo
~~~

Sau khi file tồn tại:

~~~bash
jfr summary startup.jfr
jfr print --events jdk.ClassLoad,jdk.Compilation startup.jfr
~~~

Một recording rất ngắn có thể không chứa events mong muốn, hoặc profile settings không bật từng event; **không được suy luận rằng việc không thấy `jdk.ClassLoad` nghĩa JVM không nạp lớp**. Khi cần phân tích sâu, bật những events phù hợp trong `.jfc`, kiểm soát overhead và quan sát đủ thời gian.

## 7. Công đoạn C — application/framework initialization

Tách **class initialization** của JLS ra khỏi **application bootstrap**. Một Spring/Quarkus/microservice hoặc CLI tool có thể tự thực hiện:

- Parse configuration, environment, YAML, certificate/key stores.
- Khởi tạo dependency injection context và object graphs.
- Quét classpath, tìm annotations, load configuration classes.
- Khởi tạo logging, metrics registry, tracing/exporters.
- Khởi động thread pools và scheduler.
- Tạo JDBC/HTTP connection pools, handshake TLS.
- Chạy DB migrations, kiểm tra schema, lấy secrets hoặc config qua network.
- Preload caches, load dictionaries/models/templates/rules.
- Start server socket, đăng ký routes và health probes.
- Thực hiện readiness checks hoặc chờ dependencies sẵn sàng.

Những công việc này **không phải mặc nhiên là `<clinit>`**. Chúng có thể chạy trong constructors, methods, container lifecycle hooks, background tasks hoặc startup scripts.

### Tại sao cần phân tách rõ?

Hai ứng dụng dùng đúng một JDK và tập thư viện tương tự có thể có “startup” rất khác nhau vì **bootstrap logic**. Nếu 1,2 giây nằm trong connect DB timeout thì đổi GC collector hoặc tăng JIT compiler threads khó giải quyết nguyên nhân.

### Instrument semantic milestones

Có thể đặt application-defined markers:

~~~text
process_spawn_requested
main_entered
config_loaded
di_context_created
db_pool_ready
http_listener_bound
first_successful_business_request
readiness_accepted
~~~

Sau đó đo đồng hồ monotonic **giữa các mốc phát từ cùng một process**. Với milestones thuộc process bên ngoài, phải correlate qua wall timestamps/protocol spans một cách đúng clock.

Để các logs đáng dùng:

1. Ghi phase name ổn định và run ID riêng, tránh trộn nhiều processes.
2. Ghi cả duration/clock source, không chỉ `"finished"`.
3. Hạn chế instrumentation nặng trong hot startup.
4. Tách synchronous critical path và asynchronous work.
5. Đánh dấu khi readiness trả về **trước khi** background tasks hoàn tất.
6. Ghi failures/timeouts và retries, không chỉ successful startup.

**Không lấy tổng thời gian của các sub-tasks chạy song song rồi so với end-to-end elapsed**: tổng CPU/task durations có thể lớn hơn wall-clock critical path.

## 8. Công đoạn D — JIT warm-up và trạng thái ổn định

HotSpot thường bắt đầu với interpreter và dùng tiered compilation (C1/C2, tùy VM flags) khi profiling cho thấy một đoạn code đủ hot. Vùng mã chưa được gọi có thể chưa JIT compile. JVM còn có OSR cho loops dài và khả năng deoptimization/recompilation nếu giả định profiling thay đổi.

Vì vậy `main()` hoặc first request hoàn tất **không đồng nghĩa JIT warm-up hoàn tất**. Một app có 20 routes có thể làm nóng 2 routes phổ biến trước; 18 routes còn lại vẫn gặp cold-path loading/JIT khi traffic lần đầu chạm vào.

### Tác động không chỉ JIT

Bên cạnh JIT, throughput/latency có thể thay đổi vì:

- Lazy class initialization/late linking.
- Pool/cache warm state.
- GC allocation rate, heap occupancy và promotion.
- OS filesystem page cache.
- Database page cache, prepared statements, query plan.
- TLS handshake/session resumption.
- CPU frequency scaling, scheduler, resource contention.
- JIT background compiler threads tranh CPU quota với request threads.

**Một batch thứ hai nhanh hơn batch thứ nhất không tự chứng minh code đã được C2 compile**, vì còn vô số hiệu ứng khác. Muốn biết JIT activity, dùng `-XX:+PrintCompilation`, JFR compilation events và, nếu thật sự cần kiểm chứng native optimization, công cụ compiler IR/assembly phù hợp build.

### “Steady state” là khái niệm vận hành, không phải trạng thái do JVM hứa

Có thể gọi một measurement window là **candidate steady state** khi:

1. Same offered load và request mix đại diện.
2. Throughput, p99, CPU/GC và heap live set không còn trend chuyển tiếp rõ.
3. Không có compiler/classloading bursts đủ lớn làm biến dạng kết quả.
4. External dependencies/caches ổn định.
5. Các cửa sổ lặp lại cho kết quả tương đối nhất quán và có đủ samples.

Không có public API `Jvm.isSteadyState()` bảo đảm chương trình đã đạt “peak”. Vì vậy warm-up policy cần được xác định dựa trên dữ liệu đo, không chọn một số giây tùy ý cho mọi ứng dụng.

## 9. Java 17 runnable: đo class-loading marker, init marker và warm-up batches

Tạo file **`WarmupPhasesDemo.java`**:

~~~java
import java.util.Locale;

public final class WarmupPhasesDemo {
    static class Kernel {
        static final int[] values = new int[1024];

        static {
            for (int i = 0; i < values.length; i++) {
                values[i] = i * 7 + 1;
            }
            System.out.println("kernel=<clinit>");
        }

        static long compute(int seed) {
            long sum = seed;
            for (int value : values) {
                sum += value ^ seed;
            }
            return sum;
        }
    }

    public static void main(String[] args) throws Exception {
        long enteredMain = System.nanoTime();
        System.out.println("main=entered");

        long beforeLoad = System.nanoTime();
        Class.forName("WarmupPhasesDemo$Kernel", false,
                WarmupPhasesDemo.class.getClassLoader());
        long afterLoad = System.nanoTime();
        System.out.println("loadWithoutInit=done");

        long beforeInit = System.nanoTime();
        int size = Kernel.values.length;
        long afterInit = System.nanoTime();
        System.out.println("kernelSize=" + size);

        long checksum = 0;
        for (int batch = 0; batch < 3; batch++) {
            long beforeBatch = System.nanoTime();
            for (int i = 0; i < 3000; i++) {
                checksum += Kernel.compute(batch * 3000 + i);
            }
            long elapsed = System.nanoTime() - beforeBatch;
            System.out.printf(Locale.ROOT,
                    "batch%dMs=%.3f%n", batch, elapsed / 1_000_000.0);
        }
        System.out.println("checksum=" + checksum);
        System.out.printf(Locale.ROOT, "loadingMs=%.3f%n",
                (afterLoad - beforeLoad) / 1_000_000.0);
        System.out.printf(Locale.ROOT, "initMs=%.3f%n",
                (afterInit - beforeInit) / 1_000_000.0);
        System.out.printf(Locale.ROOT, "mainElapsedMs=%.3f%n",
                (System.nanoTime() - enteredMain) / 1_000_000.0);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 WarmupPhasesDemo.java
java WarmupPhasesDemo
~~~

**Các dòng bất biến đã kiểm chứng trên JDK 24 với target Java 17, theo thứ tự xuất hiện:**

~~~text
main=entered
loadWithoutInit=done
kernel=<clinit>
kernelSize=1024
~~~

Tiếp theo chương trình in:

~~~text
batch0Ms=<thời gian môi trường>
batch1Ms=<thời gian môi trường>
batch2Ms=<thời gian môi trường>
checksum=44137339276
loadingMs=<thời gian môi trường>
initMs=<thời gian môi trường>
mainElapsedMs=<thời gian môi trường>
~~~

Dấu `<thời gian môi trường>` mô tả những số **không cố định**; không phải stdout nguyên văn. Trong một lần chạy thử, `batch0Ms` lớn hơn `batch1Ms` và `batch2Ms`, nhưng kết quả này **không được dùng làm bằng chứng mỗi máy sẽ warm-up theo đúng tỷ lệ**.

### Giải thích các mốc

- `enteredMain` chỉ đo từ thời điểm đã ở trong main.
- `Class.forName(..., false, ...)` chỉ yêu cầu class được load/link theo API contract; không kích hoạt `<clinit>` của `Kernel`.
- `Kernel.values.length` là active use của nonconstant static array field: JVM initialize Kernel trước khi đọc giá trị, nên marker `kernel=<clinit>` xuất hiện trước `kernelSize=1024`.
- `initMs` đo cả overhead quanh static field access, việc chạy `<clinit>` và những chi phí runtime/JIT liên quan, **không phải “thời gian mọi static initialization trong app”**.
- `loadingMs` là elapsed của **một lời gọi Class.forName tại một lần chạy**; ngoài class loader work còn có call overhead, logging nếu bật và ảnh hưởng OS/JIT. Nó không phải toàn bộ class-loading cost của process.
- `batch0Ms`, `batch1Ms`, `batch2Ms` là các khoảng đo tính toán; không khẳng định batch thứ ba đạt steady state.
- `checksum` giữ kết quả observable để giảm nguy cơ dead-code elimination; vẫn không thay thế chuẩn benchmark.
- `mainElapsedMs` đo toàn bộ section sau `enteredMain`, **bao gồm** các khoảng đã đo con và output/formatting. **Không cộng `mainElapsedMs + initMs + loadingMs`** vì sẽ đếm lặp.

**Đọc code có chủ đích:** chương trình chèn `System.out.println` giữa các mốc để người học nhìn rõ thứ tự. Trong microbenchmark nghiêm túc, printing có thể ảnh hưởng timings; nên tách measurement khỏi output khi đo từng operation.

## 10. Quan sát JIT: warm-up khác class init

Trong Java 17 HotSpot, có thể chạy:

~~~bash
java -XX:+PrintCompilation WarmupPhasesDemo
~~~

Diagnostics có thể ghi phương thức `WarmupPhasesDemo$Kernel::compute` được compile ở một hoặc nhiều tiers. Có thể chạy đối chiếu:

~~~bash
java -Xint WarmupPhasesDemo
~~~

`-Xint` chạy interpreter-only: useful để hiểu vai trò JIT, nhưng **không phải mô phỏng startup của JVM có JIT mặc định**. Code cold trên JVM mặc định vẫn được quyền JIT compile khi đủ hot.

Không coi mỗi dòng `PrintCompilation` như một lời hứa compilation đã hoàn thành cho mọi hot path. HotSpot có C1/C2, OSR, deoptimization, inlining và tier policies; dấu hiệu compilation cần phân tích với current build.

### Các con số batch không thể thay một load test

Một app xử lý 9.000 lần `Kernel.compute` trong 3 batches không đo:

- Process spawn → first useful response.
- Sự sẵn sàng của DB, TLS, HTTP connections.
- Request queueing và p99/p99.9 trên arrival rate cố định.
- Dynamic route/class loading và memory retention theo thời gian.
- GC full/concurrent behavior trên workload dài.
- CPU quota/backpressure của dịch vụ trong container.

Vì vậy dùng ví dụ cho **phân tách khái niệm**, không dùng nó xếp hạng JDK hoặc framework.

## 11. Phân biệt cold start và lazy first hit

Đây là một bẫy rất thực tế trong framework:

~~~text
Process starts
       |
       +-- load Main, init DI context, bind HTTP port
       |
       +-- /health GET 200 ⇒ readiness OK
       |
       +-- /report request lần đầu
             +-- class ReportRenderer được load
             +-- static fields/serializer registry initialize
             +-- template compile, DB query plan/cache load
             +-- JIT/profile hot route bắt đầu
             +-- FIRST_HIT_LATENCY cao
       |
       +-- /report request lần sau ⇒ có thể nhanh hơn
~~~

Một service có thể đạt **readiness** sớm nhưng chưa đáp ứng **first-hit latency SLO** cho route quan trọng. Các lựa chọn:

1. Thêm warm-up bằng **đúng request mix** có ý nghĩa.
2. Preload những classes/static data thực sự quan trọng.
3. Dời công việc ít dùng sang lazy init để **giảm startup critical path**, chấp nhận cold first hit.
4. Tách “ready to accept basic traffic” và “ready for critical workload” nếu cần.
5. Bật tracing theo request route để tìm class/init/JIT/DB costs.

**Không phải cứ preload tất cả class là tốt.** Eager init có thể tăng startup duration, heap footprint và CPU burst; lazy init lại làm first-hit latency cao. Đây là trade-off theo SLO/lifecycle, không phải một tối ưu duy nhất cho mọi hệ thống.

## 12. CDS và AppCDS: tối ưu một phần của class loading

**Class Data Sharing (CDS)** cho phép JVM sử dụng một archive chứa metadata của những classes đã được chuẩn bị/chia sẻ, giảm một phần startup work và memory footprint. Oracle JDK 17 có **default CDS archive** với selected core classes; **Application CDS (AppCDS)** có thể mở rộng phạm vi cho classes từ ứng dụng.

Tác động tiềm năng:

- Một phần metadata có thể được memory-map từ archive.
- Nhiều JVM trên cùng host có thể chia sẻ read-only pages tương ứng.
- Giảm một số bytecode parsing/loading/preparation-related work.
- Có lợi nhất khi class loading là phần đáng kể của startup, tùy app/JDK/config.

**CDS không tự chạy hết `<clinit>` của ứng dụng, không thay business bootstrap, không mở DB connection sẵn, không tự làm nóng C2 compiled code của mọi route.** Không gọi CDS “giải pháp warm-up hoàn toàn”.

### Bật/tắt CDS trong thí nghiệm có kiểm soát

Ví dụ trên HotSpot Oracle/OpenJDK 17 phù hợp:

~~~bash
java -Xshare:auto StartupPhasesDemo
java -Xshare:off StartupPhasesDemo
~~~

`-Xshare:auto` cho JVM dùng archive khi phù hợp, `-Xshare:off` tắt CDS cho diagnostic comparison. Cần nhiều repetitions, cùng JDK/classpath/filesystem cache, process mới mỗi run. Không kết luận sự chênh lệch của một lần chạy là tác động riêng của CDS vì OS cache và scheduler noise.

`-Xshare:on` ép dùng archive và có thể khiến JVM fail khi archive không phù hợp; tài liệu Oracle cảnh báo **không dùng như default production option** trong môi trường archive/config không chắc chắn.

AppCDS có thể được tạo từ class list hoặc **dynamic archive** (HotSpot flag `-XX:ArchiveClassesAtExit=...`) phù hợp JVM version. Cần tuân thủ constraints archive và deployment, không đưa một đường dẫn archive thử nghiệm vào production mà không kiểm tra compatibility/version.

## 13. Những flags HotSpot thường hữu ích để phân tích startup

| Tùy chọn/công cụ | Mục đích | Hạn chế |
| --- | --- | --- |
| `-Xlog:class+load=info` | Ghi load class events | Không phải phase duration độc lập |
| `-Xlog:class+init=info` | Xem class init events khi được hỗ trợ | Logger có overhead, không tự bằng tổng `<clinit>` elapsed |
| `-XX:+PrintCompilation` | Biết JIT compile methods nào, tiers/timing | Không đo OS spawn/class loading |
| `-Xint` | Chạy interpreter-only để so cơ chế | Không phải baseline cold JVM mặc định |
| `-Xshare:auto/off` | So CDS effect | Phải cùng JDK, process forks và caching |
| `-Xlog:gc=info` | Ghi GC activity, pauses | Không đo full startup |
| `jfr`, JFR settings | Correlate CPU, class, compiler và allocation events | Phải bật đúng events, overhead |
| `jcmd PID VM.flags` | Xem JVM flags của process | Dùng PID thật, permissions phù hợp |
| `jcmd PID VM.command_line` | Xác minh lệnh launch | Process có thể đã exit khi đo startup ngắn |

### Ghi log ảnh hưởng kết quả

Startup của app vốn rất ngắn có thể bị log I/O chi phối nếu bật `class+load=trace` và in hàng chục nghìn classes. Vì thế nên có:

- **Diagnostic runs** bật logging để xác định nguyên nhân.
- **Benchmark runs** dùng cấu hình logging giống production hoặc giống giữa các candidates.
- Ghi rõ logger configuration trong báo cáo, tránh so run A `trace` với run B `off`.
- Nếu cần `file=...`, chọn file names riêng, rotation, quyền truy cập và không trộn processes.
- Khi xuất JFR/dumps/logs, lưu ý classnames/path/credentials có thể là dữ liệu nhạy cảm.

## 14. JVM process spawn đo một lần không đủ để kết luận

Mô tả một quy trình đo ngắn:

1. `N` lần gọi một **process Java mới** cho mỗi ứng viên, mỗi lần đo từ bên ngoài.
2. Randomize thứ tự ứng viên (A/B/B/A) để giảm bias của OS filesystem cache, thermal/CPU changes.
3. Ghi `java -version`, vendor/build, OS, CPU quota, heap flags, CDS settings, classpath, working directory.
4. Tách **cold filesystem/cache** và **warm OS cache** thành hai kịch bản nếu quan trọng; không mặc định mỗi JVM mới là OS cache lạnh.
5. Báo median/p95/p99 startup, min/max và variability, không chỉ fastest run.
6. Với server, dùng external first-response/readiness instrumentation thay `time java` đợi process exit.
7. Test startup failure/timeouts và retries, không chỉ instances thành công.
8. Thu class-load/JIT/JFR/OS metrics ở một số run diagnostic để giải thích từng phase.
9. Phân biệt main-internal elapsed với process external elapsed; không cộng durations đếm trùng.
10. Kiểm thử tiếp sau startup: first-hit latency và steady-state throughput, vì một tối ưu có thể chuyển chi phí sang nơi khác.

### OS caches và working set

Nếu lần đầu mở JAR mất lâu vì filesystem page faults nhưng lần thứ hai nhanh hơn nhờ page cache, **không được quy ngay tất cả delta cho JIT**. Một JVM process mới không có native code cache “nóng” từ process trước theo nghĩa ứng dụng, nhưng OS file cache và external DB cache có thể được tái sử dụng.

Nếu chạy trong container, thêm image start/cgroup scheduling, CPU throttling, memory pressure và network startup vào bối cảnh. `-XX:ActiveProcessorCount` **không tự cấp CPU quota**; chỉ ảnh hưởng JVM ergonomics, trong khi OS/container thực thi giới hạn tài nguyên.

## 15. Cold vs warm, startup vs throughput: chọn KPI nào?

| Workload | KPI startup | KPI sau startup | Điều cần nhấn mạnh |
| --- | --- | --- | --- |
| CLI chạy dưới một giây | Full process elapsed / time-to-result | Ít ý nghĩa nếu process exit sớm | VM boot, classloading, init quan trọng |
| Batch job chạy vài phút | Time-to-first-batch + total job time | Throughput khi batch steady | Startup có thể nhỏ so với toàn job |
| API server chạy dài | Ready time, first useful request | p99/p99.9, throughput, GC, memory | Lazy routes và JIT warm-up |
| Serverless/short-lived function | Cold invocation latency | Warm invocation latency | Startup lặp lại nhiều lần |
| Autoscaling pod | Process/deploy → ready; traffic ramp | SLO-constrained throughput | Cold pods cần ramp/backpressure |
| Desktop/interactive GUI | First interactive render | UI responsiveness | Không coi process-created là ready |

**Một phương án “startup nhanh” không tự là phương án “throughput cao”.** Ví dụ giảm compilation work ban đầu có thể cải thiện first response nhưng làm peak throughput kém; eager initialize có thể làm ready muộn nhưng giảm first-hit spikes. Đó là trade-off cần đo **theo vòng đời và SLO thực**.

### Tổng work trên vòng đời ngắn

Với function sống 2 giây, giảm 0,5 giây startup có thể rất có giá trị ngay cả khi steady-state CPU throughput không đổi. Với process sống 24 giờ, 0,5 giây startup có thể ít giá trị hơn nếu tăng CPU 10% trong suốt ngày.

**Không áp một mục tiêu “tối ưu startup bằng mọi giá” cho cả hai.**

## 16. Những sai lầm phổ biến cần tránh

1. **“Đặt `nanoTime()` đầu main thì đo được OS spawn.”** Sai; process/JVM đã init đủ để gọi main.
2. **“Class được load thì `<clinit>` lập tức chạy.”** Sai; load/link/init tách biệt.
3. **“Preparation chính là chạy static initializer.”** Sai; preparation khác initialization.
4. **“Mọi symbolic reference đều resolve tại startup.”** Sai; resolution có thể lazy.
5. **“Class.forName(..., false, ...) bắt buộc chạy static blocks.”** Sai.
6. **“`static final` nào cũng là compile-time constant.”** Sai; chỉ constant variables theo JLS.
7. **“Thời gian `Class.forName` bằng toàn bộ JVM classloading cost.”** Sai.
8. **“Có `JIT` thì phải compile tất cả classes trước khi ready.”** Sai.
9. **“Tốc độ batch hai nhanh chứng minh C2 compiled.”** Chưa đủ bằng chứng.
10. **“CDS có nghĩa không còn class initialization.”** Sai.
11. **“CDS archive làm nóng DB pool/JIT/HTTP routes.”** Sai.
12. **“Main in READY là server đã đáp ứng SLO.”** Không, trừ khi marker gắn đúng contract.
13. **“First successful response = steady state.”** Sai.
14. **“Một process mới luôn cold filesystem cache.”** Sai.
15. **“Tổng subphase durations bằng wall time nếu tasks song song.”** Sai, có thể đếm trùng.
16. **“`-Xint` mô phỏng y hệt cold JIT app.”** Sai.
17. **“GC log tắt là không có GC.”** Sai, chỉ không có log tương ứng.
18. **“JFR profile luôn bật mọi ClassLoad/Compilation event.”** Sai.
19. **“Fastest run đáng báo hơn median hay p99.”** Không có đủ cơ sở.
20. **“Gộp startup, warm-up và long-run throughput thành một số ms.”** Không có ý nghĩa đo lường.
21. **“Một tối ưu lazy init không có chi phí.”** Sai; chi phí có thể chuyển sang request đầu tiên.
22. **“Nhờ JIT mà OS process creation rẻ đi.”** Sai; hai lớp chi phí khác nhau.
23. **“So Java17 và Java24 mà không ghi phiên bản thì kết luận chung được.”** Sai, JVM đã thay đổi.
24. **“Readiness vừa thành công thì mọi route đã class-init và compiled.”** Sai.

## 17. Checklist để điều tra một ứng dụng Java startup chậm

- [ ] Định nghĩa **external start** và **response/readiness end** trước khi đo.
- [ ] Kiểm tra command line, Java vendor/build, OS, classpath, JAR size, CDS state.
- [ ] Phân biệt *process start → main* với *main → app ready* và *ready → warm throughput*.
- [ ] Chạy 5–10 lần độc lập, process mới, đo median/variance và ghi filesystem cache state.
- [ ] Bật `class+load` và `class+init` trong diagnostic runs để tìm lazy/eager classes.
- [ ] Với Java `<clinit>`, xác định trigger active use, không đánh đồng loading.
- [ ] Instrument phase durations của config/DI/DB/TLS/cache/HTTP bootstrap.
- [ ] Nếu có background init, đo critical path và readiness dependency thật.
- [ ] Kiểm tra JIT events nếu warm-up throughput/latency thay đổi.
- [ ] Kiểm tra GC/class loading/OS CPU và page faults, không quy tất cả cho JIT.
- [ ] Kiểm tra CDS/AppCDS trên cùng JDK/build, không nhầm với class init.
- [ ] Với server, đo first business request, first-hit từng route và p99 sau ready.
- [ ] Đo plateau bằng windows, không chỉ một batch nhanh.
- [ ] Với container, ghi CPU quota, memory limit, I/O/network startup.
- [ ] Phân biệt chi phí được loại bỏ với chi phí được **dời sang first request**.
- [ ] Không phá correctness: static init side effects, connection readiness và data consistency vẫn phải đúng.
- [ ] Ghi failure/timeout và các trường hợp process chết lúc bootstrap.
- [ ] Kết luận từ nhiều nguồn dữ liệu, không chỉ một log timestamp.

## 18. Tự kiểm tra: 18 câu và đáp án ngắn

1. Đặt timer ở câu đầu `main()` có đo OS process creation không? **Không**.
2. Loading, linking, initialization có thể diễn ra ở những thời điểm khác nhau không? **Có**.
3. Classloader load class xong `<clinit>` có bắt buộc đã chạy chưa? **Chưa**.
4. `Class.forName(name, false, loader)` có yêu cầu initialize class không? **Không**.
5. Đọc static nonconstant field có thể kích hoạt initialization không? **Có**.
6. Trong ví dụ `StartupPhasesDemo`, marker `probe=<clinit>` in trước hay sau `loaded=InitProbe`? **Sau**.
7. JLS preparation có phải chạy `static {}` không? **Không**.
8. Resolution luôn eager trước main? **Không**, có thể lazy.
9. App readiness có bằng time-to-C2-peak không? **Không**.
10. Static init trigger có thể nằm ở first request không? **Có**, nếu class đó được dùng lần đầu ở route.
11. CDN/CDS archive có làm sẵn TLS/DB session cho mọi app không? **Không**.
12. Default CDS archive trong JDK17 có thể tiết kiệm phần nào? **Một số class loading/metadata work và memory sharing**, tùy environment.
13. `time java Program` chạy tới exit có đo duy nhất JVM bootstrap không? **Không**, còn main work và shutdown.
14. Một process mới có nghĩa OS page cache chắc lạnh? **Không**.
15. Batch thứ hai nhanh hơn có chứng minh JIT level 4 không? **Không**.
16. Nếu first HTTP route chậm dù `/health` báo ready, nên xem gì? **Lazy class/init, pools, serializers, network, JIT và cache trên route đó**.
17. Chạy `java -Xint` có phải mô phỏng cold JIT startup chuẩn không? **Không**.
18. Với CLI 300 ms, tối ưu phần nào thường đáng xem trước? **Full process-to-result time**, không chỉ throughput hot path.

## 19. Nguồn chính thống Java 17

- **JLS 17, Chapter 12 — Execution**, loading/linking/initialization và VM startup: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html
- **JVMS 17, Chapter 5 — Loading, Linking and Initializing**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- **`Class.forName` Java SE 17**, overload initialize boolean: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)
- **`ClassLoader` Java SE 17**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Oracle JDK 17 java command** (`-Xlog`, `-Xint`, `-XX:+PrintCompilation`, CDS flags): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Oracle JDK 17 Class Data Sharing**, default CDS archive và AppCDS: https://docs.oracle.com/en/java/javase/17/vm/class-data-sharing.html
- **Oracle JDK 17 `jcmd`**, VM flags, JFR diagnostic tools: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **Oracle JDK 17 `jfr` command**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html
- **OpenJDK JMH project**, tránh sai lệch khi đo Java micro-workload: https://openjdk.org/projects/code-tools/jmh/

**Kết luận kỹ thuật:** Quá trình đưa một ứng dụng Java tới trạng thái hữu ích cần nhìn như **nhiều chi phí có ranh giới đo khác nhau**: OS process creation → launcher/JVM boot → class load/link → class init → application bootstrap/first response/readiness → JIT/cache/GC warm-up → steady state. Timer trong `main` bỏ sót mọi bước trước `main`; class được load **không đồng nghĩa đã init**; CDS có thể giảm một phần class-loading cost **không có nghĩa làm sẵn `<clinit>` hoặc JIT peak**. Hãy kết hợp **external milestones, class/JIT/GC logs, JFR và application tracing** trên cùng workload/JDK/resources, đo nhiều runs và báo riêng cold start, first-hit latency, readiness, warm-up ramp và steady-state SLO.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fast-startup-002">Class Data Sharing (CDS) chia sẻ dữ liệu lớp như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# Class Data Sharing (CDS) chia sẻ dữ liệu lớp như thế nào?

## 1. CDS là gì và giải quyết vấn đề nào?

**Class Data Sharing (CDS)** là một cơ chế của **HotSpot JVM** cho phép JVM chuẩn bị và lưu **một phần dữ liệu metadata của các lớp Java** vào một tệp archive, thường có phần mở rộng **`.jsa`**. Khi JVM khác được khởi chạy với điều kiện tương thích, nó có thể **memory-map** archive này vào không gian địa chỉ tiến trình và sử dụng biểu diễn lớp đã lưu thay vì phải làm lại toàn bộ một số bước tải, phân tích, kiểm tra và tạo metadata từ bytecode/JAR.

Mục tiêu chính:

- **Giảm startup time**, đặc biệt phần chi phí class loading và linking đối với lớp đã được archive.
- **Giảm memory footprint tổng** khi nhiều JVM processes sử dụng cùng archive: các trang nhớ chỉ đọc có thể cùng trỏ tới những physical pages do hệ điều hành quản lý.
- **Giảm công việc khởi tạo biểu diễn class**, đặc biệt với nhiều classes thường được dùng đi dùng lại.

CDS **không phải** cơ chế chia sẻ Java heap thông thường giữa hai chương trình Java; **không phải** làm sẵn mọi static initializer; **không phải** JIT/AOT compile toàn bộ chương trình thành machine code; cũng **không phải** bộ nhớ đệm tự động cho database, HTTP hay state nghiệp vụ.

### Một sơ đồ ý nghĩa

```text
                Khi chuẩn bị archive
.class/JAR/runtime classes
          |
          v
  HotSpot xác định các lớp được archive
          |
          v
     CDS archive (.jsa)
     metadata ở dạng dùng lại
          |
          +------------------------------+
          |                              |
          v                              v
     JVM process A                   JVM process B
  map cùng archive                 map cùng archive
     |                              |
     +-- shared read-only pages -----+
     |                              |
     +-- private / COW state         +-- private / COW state
     +-- Java heap A riêng           +-- Java heap B riêng
     +-- static state A riêng         +-- static state B riêng
     +-- JIT/code cache A riêng      +-- JIT/code cache B riêng
```

**Ý chính:** *Chia sẻ dữ liệu lớp* là chia sẻ các biểu diễn metadata phù hợp **qua các mapping của hệ điều hành**; không phải cho hai ứng dụng quyền sửa cùng một object tùy ý.

### Câu hỏi bài học trả lời

1. Archive chứa những loại dữ liệu gì và vì sao có thể đọc nhanh hơn JAR?
2. Dữ liệu nào thực sự được **share**, dữ liệu nào vẫn **private**?
3. Default CDS, AppCDS, static base archive và dynamic top archive khác nhau ra sao?
4. `-Xshare:auto`, `-Xshare:off`, `-Xshare:on` vận hành thế nào?
5. Làm sao tạo và dùng archive bằng JDK 17?
6. Làm sao chứng minh lớp ứng dụng được lấy từ archive chứ không phải JAR?
7. Vì sao CDS không bỏ `<clinit>`, JIT warm-up, class loaders và Java semantics?
8. Khi nào archive không dùng được và cần thiết kế fallback/thí nghiệm thế nào?

## 2. Class loading truyền thống tốn công việc gì?

Nếu một class chưa được nạp vào runtime, đường xử lý có thể liên quan:

1. Class loader định vị nguồn bytecode thích hợp.
2. Đọc các bytes của class từ runtime image, JAR, filesystem hoặc nguồn tùy biến.
3. Parse class file/constant pool và xây dựng những metadata cần thiết.
4. Verification các constraints, preparation static fields.
5. Resolution symbolic references khi cần.
6. Initialization ở lần active use thích hợp, theo JLS §12.4.
7. Trong quá trình thực thi, profiling/JIT compilation có thể phát sinh riêng.

```text
No suitable CDS archive:
   class path / module image / JAR bytes
                  |
           locate + read
                  |
             class parsing
                  |
          verify / metadata setup
                  |
         link + runtime class state
                  |
         initialize when required
```

Không có công thức “mỗi class tốn đúng X microseconds”. Chi phí thay đổi theo class size, loader behavior, file cache, JVM build, agents, classpath scanning, resolution và những hoạt động runtime khác.

### CDS tiết kiệm điều gì?

Archive lưu **dữ liệu lớp đã được chuẩn bị theo định dạng HotSpot có thể tái sử dụng**. Khi những điều kiện hợp lệ, JVM có thể **map và khai thác metadata này**, tránh lặp lại một phần việc tìm, đọc, phân tích và tổ chức class metadata từ JAR cho các lớp có trong archive.

Đây không phải lệnh “bỏ qua mọi bước verification/security checks trên mọi lớp”; HotSpot vẫn phải đảm bảo class identity, cấu hình loader, compatibility và Java semantics đúng.

**Lợi ích đến từ việc tái dùng phần việc đã được thực hiện**, không phải thay đổi kết quả của chương trình.

## 3. Archive CDS lưu những gì?

Trong HotSpot, **class metadata** đại diện cho thông tin mà VM cần để thực thi class/interface, có thể gồm:

- Thông tin cấu trúc class, superclass/interfaces.
- Method và field descriptors, constant-pool information.
- Metadata đã parse/link/preprocess phù hợp định dạng archive.
- Những runtime data structures đủ điều kiện để archive theo HotSpot/JDK release.
- Trong một số cấu hình, **archived heap objects** chuyên biệt như một số String objects/constant data đã được VM chuẩn bị phù hợp.

Không suy rằng “CDS copy nguyên toàn bộ object graph của ứng dụng vào archive”. Các shared contents và representation thay đổi theo JDK/collector/platform. **HotSpot implementation** quyết định chính xác dữ liệu nào được archive, phiên bản metadata nào phải tái tạo hoặc relocate.

### 3.1. Metadata khác Java object instance

```java
class Customer {
    long id;
    String name;
}
```

Đây là class snippet, chưa phải chương trình chạy độc lập.

Archive có thể hỗ trợ **metadata mô tả `Customer`** nếu lớp được archive. Khi mỗi tiến trình chạy `new Customer()`, chúng **không cùng nhận một object Java dùng chung**, không dùng chung value của `id` và không chia sẻ một biến `name` mutable giữa các JVM.

```text
Shared idea:
  class shape / metadata for Customer
Per JVM:
  new Customer() object allocations
  instance fields (id, name)
  application data and state
```

### 3.2. “Archive classes” không phải archive source code

CDS không lưu lại source Java (`.java`) để biên dịch. Nó lưu runtime-friendly representations dựa trên classes đã biết. JVM vẫn phải tuân theo class loading/security/initialization rules và có thể phải fallback sang class files thông thường khi archive không dùng được.

### 3.3. Archive có thể lớn hơn class files

Theo tài liệu **Oracle JDK 17 Application Class Data Sharing**, biểu diễn lớp trong archive có thể tốn nhiều dung lượng đĩa hơn class bytes trong JAR/runtime image (tài liệu nêu khoảng 2–5 lần tùy lớp). Lý do là định dạng được tối ưu cho tái dùng nhanh, không chỉ nén file tối đa.

Do vậy **đừng archive mọi class của mọi library theo mặc định**. Chỉ nên chứa các classes thực tế hay được ứng dụng sử dụng, trừ khi profile và chính sách triển khai cho thấy cần phạm vi khác.

## 4. “Sharing” diễn ra ở tầng hệ điều hành: memory mapping

Điểm cốt lõi không nằm ở một singleton Java toàn cục, mà ở **memory-mapped files** của OS.

Tại runtime:

1. JVM mở archive tương thích.
2. Các vùng của archive được map vào **virtual address space** của process.
3. Nhiều JVM processes có thể map cùng file/physical file-cache pages.
4. Những pages **read-only** có thể được dùng chung vật lý.
5. Những vùng cần ghi có thể dùng **copy-on-write (COW)** hoặc được cung cấp dưới dạng private data thích hợp.

```text
File on disk: cds-demo.jsa
        |
        v
Operating-system page cache / mapped file pages
        |
        +--------------------------+
        |                          |
       JVM A                      JVM B
 virtual address A             virtual address B
        |                          |
        +---- shared RO pages -----+
        |                          |
    private RW/COW pages       private RW/COW pages
        |                          |
     Heap A                       Heap B
```

### 4.1. Virtual address giống nhau không phải điều kiện bắt buộc

JVM có thể map archive tại những virtual addresses phù hợp và xử lý relocation/pointer adjustments theo HotSpot/JDK requirements. Không được suy các processes phải có cùng absolute pointer addresses để dùng chung file pages.

### 4.2. Copy-on-write nghĩa gì?

Một file-backed page có thể **được share khi chưa bị thay đổi**; khi một process cần ghi vào một mapped page theo cơ chế COW, OS cung cấp một private page cho thay đổi đó. JVM còn có các vùng private state riêng không đặt trong shared archive pages.

**Không suy “mọi vùng archive vĩnh viễn read-only”**, vì các logs HotSpot có thể cho thấy archive regions có labels `ReadOnly` và `ReadWrite`. Những trang được dùng chung thực tế tùy OS mapping, memory page modifications và implementation.

### 4.3. Shared physical memory giúp lúc nào?

Nếu nhiều Java processes cùng dùng cùng JDK/CDS archive và những pages được map/chia sẻ, tổng **physical memory consumption** có thể thấp hơn so với mỗi process tự lưu những bản metadata riêng.

Tuy nhiên:

- Mỗi JVM vẫn có private Java heap, threads, native allocations, code cache.
- Không phải mọi page đều có thể dùng chung.
- RSS của từng process có thể tính cả shared mapped pages, nên **cộng RSS đơn giản giữa processes có thể đếm trùng**. Trên Linux có thể cân nhắc PSS hoặc các metrics phù hợp khi đánh giá total shared-memory footprint.
- Một JVM đơn độc vẫn có thể giảm startup ngay cả khi chưa có tiến trình thứ hai để share page với nó.

### 4.4. Không phải Java-level shared mutable memory

CDS không cung cấp API giống shared-memory queue hay distributed cache. Không có điều kiện Java Memory Model nào cho phép thread ở process A tự truy cập `static int counter` của process B nhờ cả hai cùng map archive.

## 5. Điều gì vẫn riêng cho mỗi JVM?

Ngay cả khi class `CDSDemo` được load từ shared archive, mỗi JVM process có runtime state độc lập:

| Thành phần | Dùng chung nhờ CDS? | Ghi chú |
| --- | --- | --- |
| Những metadata pages read-only đã archive | **Có thể chia sẻ qua OS** | Tùy điều kiện archive/mapping |
| Những archive pages bị ghi/COW | **Không giữ sharing của phần đã copy** | Private modified pages |
| Java heap thông thường | **Không** | Mỗi JVM có heap riêng |
| Objects tạo bởi `new` trong app | **Không** | Mỗi process tạo object riêng |
| Giá trị static mutable của class | **Không** | Runtime class state mỗi JVM |
| Java threads và stack frames | **Không** | Tạo riêng từng process |
| Monitor locks và synchronization state | **Không theo nghĩa app dùng chung giữa processes** | JMM áp dụng trong mỗi JVM process |
| JIT profiles, C1/C2 compiled methods | **Không chỉ vì bật CDS** | Code cache và JIT state riêng |
| GC state, roots và heap generations | **Không** | GC quản lý heap từng process |
| Network connections / DB pools | **Không** | App cần khởi tạo riêng |
| CDS archive file | **Có thể cùng sử dụng** | File/page cache được OS chia sẻ |

**Cách nhớ:** *shared class metadata* ≠ *shared live application state*.

## 6. CDS không bỏ qua class initialization (`<clinit>`)

Đây là điểm dễ gây nhầm nhất.

```java
final class Settings {
    static final int LIMIT;

    static {
        LIMIT = 42;
        System.out.println("Settings initialized");
    }
}
```

Đây là class snippet. Khi một process đầu tiên active-use `Settings`, JVM phải thực hiện initialization phù hợp và in marker. Process thứ hai dù dùng cùng CDS archive **vẫn có trạng thái class initialization riêng** và có thể in marker khi active-use trong process ấy.

### Vì sao archive không thể “làm sẵn” tùy ý `<clinit>`?

Static initializer có thể:

- Đọc environment variables/config.
- Tạo objects gắn với process.
- Mở files/sockets.
- Gọi JNI/native code.
- Đăng ký shutdown hooks.
- Khởi tạo static fields phụ thuộc runtime.

Nếu JVM bỏ qua tất cả side effects này chỉ vì class metadata đã archive, semantics có thể sai. CDS vì thế **không phải cache kết quả tùy ý của application static initialization**.

### 6.1. Archived heap objects là một phạm vi hẹp hơn

Một số JVM versions/GC configurations hỗ trợ archive các đối tượng heap chuyên biệt đã được VM chuẩn bị một cách an toàn. Điều này **không đồng nghĩa** có thể archive mọi object sinh ra từ user-defined `<clinit>` và tái dùng nó như một mutable singleton giữa các JVM processes.

### 6.2. Class load != class init

CDS thường tập trung giảm work lúc **loading/linking**. JLS §12.4 vẫn quy định lúc nào initialize. Một class có thể được load từ archive mà static block vẫn chạy khi first active use.

## 7. CDS không thay thế JIT warm-up

Hai tối ưu giải quyết hai loại công việc khác nhau:

```text
CDS:    bytecode/class representation -> shared archived metadata
        Giảm chi phí load/link tương ứng

JIT:    executable hot bytecode -> machine code
        Cải thiện hiệu năng thực thi hot methods/loops
```

Một application chạy nhiều lần:

- Có thể dùng CDS giúp nạp metadata nhanh hơn.
- Nhưng C1/C2 vẫn có thể cần runtime profiles để tối ưu.
- Nếu code chưa hot, vẫn có thể chạy interpreter.
- Nếu code đã hot, JVM có compiled code trong **private code cache**, không tự lấy native code C2 của process trước từ `.jsa`.

Không viết “bật CDS là chương trình đã warm-up C2 trước khi `main`” hoặc “archive là Java executable”.

### 7.1. Tránh nhầm CDS với AOT code caching

Có những cơ chế AOT/native-image/code precompilation khác qua các JDK và JVM distributions. CDS `.jsa` trong phạm vi JDK 17 không phải một JIT-compiled method-cache nói chung. Nếu một release mới bổ sung các khả năng AOT liên quan, phải đọc **tài liệu đúng phiên bản** chứ không nhập lẫn semantics.

## 8. Default CDS archive — vì sao không cần tự tạo gì đã thấy hiệu ứng?

Từ JDK 12, nhiều JDK 64-bit distributions (gồm Oracle JDK) cung cấp **default CDS archive** được tạo sẵn, thường chứa những platform/core library classes hay dùng.

Theo tài liệu Oracle JDK 17, trên cấu hình phổ biến, đường dẫn default archive là:

```text
Linux/macOS: $JAVA_HOME/lib/server/classes.jsa
Windows:     %JAVA_HOME%\bin\server\classes.jsa
```

Lưu ý: vị trí và việc có archive phụ thuộc JDK vendor/distribution/install layout, không phải JLS guarantee.

### 8.1. Ba chế độ thường gặp

| Option | Ý nghĩa | Khuyến nghị |
| --- | --- | --- |
| **`-Xshare:auto`** | Dùng CDS khi có archive tương thích, có thể fallback | Mặc định phù hợp ở nhiều HotSpot builds |
| **`-Xshare:off`** | Tắt CDS để đối chứng | Phù hợp testing/diagnostics |
| **`-Xshare:on`** | Yêu cầu dùng CDS; không dùng được thì lỗi/exit | **Chỉ dùng testing**, không khuyến nghị production |

Lệnh thử:

```bash
java -Xshare:auto -version
java -Xshare:off -version
java -Xshare:on -version
```

Với `-Xshare:on`, JVM có thể **khởi động thất bại** khi archive tương thích không có hoặc cấu hình gây xung đột. Không nên dùng flag này trên production chỉ vì muốn “bắt buộc nhanh”.

### 8.2. Không có archive phù hợp thì sao?

Trong **auto mode**, JVM có thể không dùng archive và thực hiện class loading bình thường theo chính sách HotSpot. Khi **on mode**, việc không thể dùng CDS có thể khiến VM fail thay vì tiếp tục.

**Một process chạy thành công không đủ chứng minh CDS đã được dùng.** Hãy quan sát `-Xlog:cds=info` và nguồn class loading.

## 9. AppCDS — đưa lớp ứng dụng vào archive

**Application Class Data Sharing (AppCDS)** mở rộng ý tưởng từ core JDK classes sang application/library classes phù hợp.

Tình huống:

- 10 JVM instances đều chạy cùng application JAR.
- Những classes/framework libraries giống nhau được nạp rất nhiều lần.
- Nếu archive metadata đã chuẩn bị phù hợp, các processes có thể map lại một phần dữ liệu đó.

AppCDS **có thể** giúp startup và footprint. Nhưng nếu chương trình chủ yếu tốn thời gian kết nối DB, đọc config hoặc chạy expensive initialization, AppCDS không giải quyết toàn bộ.

### 9.1. Không có nghĩa archive “chứa tất cả classes của JAR”

Một archive thường chứa **tập classes đủ điều kiện và đã được quan sát/đưa vào class list** trong quá trình build/dump.

Những classes ở code paths chưa được chạy hoặc các classes không archive-eligible có thể vẫn được load bình thường sau này.

### 9.2. Runtime class loader và archive eligibility

HotSpot AppCDS hỗ trợ những trường hợp built-in loaders và một số custom-loader classes theo điều kiện phiên bản/nguồn class/compatibility phù hợp. **Không cam kết mọi custom bytecode generator, runtime proxy hay classloader tự viết đều được archive và reuse hoàn toàn**.

### 9.3. Vì sao archive phải gắn đúng artifact?

CDS metadata phản ánh những classes/JARs và VM configuration ở thời điểm tạo archive. Nếu code thay đổi nhưng ta cố dùng metadata cũ, kết quả có thể sai; vì vậy JVM có compatibility checks và có thể từ chối/dừng dùng archive.

**Không dùng một archive build từ artifact A để kỳ vọng nó đại diện chính xác cho artifact B**.

## 10. Base archive và dynamic top archive

HotSpot có thể sử dụng tối đa **hai tầng archive** trong cấu hình tài liệu JDK 17:

1. **Base (static) archive:** thường là default archive chứa core classes từ JDK.
2. **Dynamic (top) archive:** thêm những classes được xác định từ một lần chạy ứng dụng và không có trong base archive tương ứng.

```text
               Base archive
         $JAVA_HOME/.../classes.jsa
          (core / base classes)
                     |
                     v
          Dynamic top archive
                app.jsa
        (application/library classes)
                     |
                     v
         Java process startup:
    map base + dynamic when compatible
                     |
                     v
      load archived metadata on demand
```

Từ “static”/“dynamic” ở đây là **cách tạo/tầng archive**, **không phải** Java keyword `static`, không phải trạng thái initialized hay dữ liệu “tĩnh/động” trong heap.

### 10.1. Dynamic archive không thay thế base

Một dynamic top archive thường **phụ thuộc base archive** dùng khi tạo nó. Khi khởi chạy, JVM cần một base archive tương thích để map top archive đúng cách.

### 10.2. Đường dẫn `SharedArchiveFile`

Trong các trường hợp được tài liệu hỗ trợ:

```text
-XX:SharedArchiveFile=app.jsa
```

có thể dùng trực tiếp một dynamic archive, để JVM tìm base archive đã được ghi nhận.

Khi chỉ định tường minh cả hai:

```text
Linux/macOS: -XX:SharedArchiveFile=base.jsa:app.jsa
Windows:     -XX:SharedArchiveFile=base.jsa;app.jsa
```

Dấu phân tách **khác nhau giữa OS**. Trên command line Windows shell, dấu `;` cũng có thể có ý nghĩa riêng, nên quote toàn bộ JVM argument nếu cần.

## 11. Ví dụ Java 17+ độc lập: CDS không chia sẻ static state

Tạo tệp **`CDSDemo.java`**:

```java
public final class CDSDemo {
    private static volatile long sink;

    static final class Engine {
        static final int base;

        static {
            base = 7;
            System.out.println("engine initialized");
        }

        static int mix(int x) {
            return (x * 31 + (x >>> 3) + base) & 1023;
        }
    }

    static long compute() {
        long sum = 0;
        for (int i = 0; i < 10_000; i++) {
            sum += Engine.mix(i);
        }
        sink = sum;
        return sum;
    }

    public static void main(String[] args) {
        System.out.println("started");
        long result = compute();
        System.out.println("checksum=" + result);
        System.out.println("sinkMatches=" + (sink == result));
    }
}
```

Đặt file ở thư mục thực hành riêng, không cần module/package.

### 11.1. Biên dịch và đóng gói JAR

```bash
javac --release 17 CDSDemo.java
jar --create --file cds-demo.jar CDSDemo.class 'CDSDemo$Engine.class'
```

**Quan trọng về shell:** Lệnh `jar` trên là ví dụ cho Bash/POSIX shell với **single quotes**, dùng để tránh `$Engine` bị shell hiểu là một biến. Trên Windows PowerShell, có thể dùng:

```powershell
javac --release 17 CDSDemo.java
jar --create --file cds-demo.jar CDSDemo.class 'CDSDemo$Engine.class'
```

PowerShell cũng chấp nhận một chuỗi single-quoted literal như trên. Tùy layout, nếu đã tồn tại JAR cũ trong thư mục thì xóa/đổi tên JAR test theo cách an toàn trước khi tạo lại; không ghi đè artifacts sản xuất.

### 11.2. Chạy không dùng CDS

```bash
java -Xshare:off -cp cds-demo.jar CDSDemo
```

**Stdout chính xác khi chạy thành công:**

```text
started
engine initialized
checksum=5113344
sinkMatches=true
```

### 11.3. Chạy với CDS mặc định

```bash
java -Xshare:auto -cp cds-demo.jar CDSDemo
```

Chương trình có **cùng bốn dòng stdout** như trên. Default CDS có thể giúp một số core JDK classes, nhưng **chưa chắc** đã archive `CDSDemo` vì nó là application class chưa có trong default archive.

### 11.4. Tại sao static initializer vẫn in?

Mỗi lần khởi chạy Java process mới, class `CDSDemo$Engine` được active-use lần đầu, chạy static block theo JLS và in `engine initialized`.

Tệp `.jsa` có thể cung cấp class metadata đã chuẩn bị, **không dùng chung static variable value trong hai JVM processes** hoặc bỏ đi side effect của source initializer.

### 11.5. Kiểm thử được thực hiện

File đã được biên dịch bằng **`javac --release 17`**, sau đó chạy thành công bằng các options liên quan CDS trên **OpenJDK/HotSpot JDK 24.0.2** tại môi trường thử nghiệm.

**Phân biệt rõ:** Đây là code Java 17-compatible, **không phải lời khẳng định đã chạy trên một binary JDK 17 cụ thể**. Các lệnh sử dụng những tùy chọn được tài liệu JDK 17 mô tả. Số build/runtime phải được kiểm tra lại ở nơi bạn triển khai.

## 12. Dynamic AppCDS: top archive và ý nghĩa của lần chạy chuẩn bị

Trên **HotSpot JDK 17**, dùng application JAR đã tạo ở phần 11:

```bash
java -XX:ArchiveClassesAtExit=cds-demo.jsa -cp cds-demo.jar CDSDemo
```

Chương trình vẫn thực thi bình thường; JVM ghi **dynamic top archive** ở giai đoạn exit phù hợp. Archive chứa **metadata của các lớp đủ điều kiện đã được nạp** nhưng chưa thuộc base archive, **không phải ảnh chụp Java heap để process khác tiếp tục**. Nếu process bị kill đột ngột, base archive không phù hợp hoặc configuration lỗi, không được giả định file `.jsa` đã sẵn sàng. Cần xác minh file/exit code và log.

## 13. Dùng dynamic archive — kiểm chứng lớp được chia sẻ

Ở một **JVM process mới**, cùng JDK build và application JAR:

```bash
java -XX:SharedArchiveFile=cds-demo.jsa -Xlog:class+load=info -cp cds-demo.jar CDSDemo
```

Ứng dụng vẫn phải có cùng hành vi: `started`, `engine initialized`, `checksum=5113344`, `sinkMatches=true`. Trong diagnostic log HotSpot, lớp thực sự được tải từ dynamic top archive có thể có marker `source: shared objects file (top)`. Một class còn được đọc từ JAR có thể hiển thị `source: file:...`. **Chỉ có file `.jsa` trên đĩa hay thấy JVM chạy thành công chưa đủ để kết luận application class đã dùng CDS**.

CDS reuse metadata, không chia sẻ mutable `static` fields giữa JVM processes và không chạy trước các static initializers tùy ý. Bài về **AppCDS static/dynamic** ngay sau đây dạy đầy đủ workflow; bài về **class-loading logs** giúp xác định archive mapped, class source và fallback.

## 14. Quan sát memory mapping bằng `-Xlog:cds`

Một lệnh chẩn đoán:

```bash
java -XX:SharedArchiveFile=cds-demo.jsa \
     -Xlog:cds=info \
     -cp cds-demo.jar CDSDemo
```

Các sự kiện HotSpot có thể cho thấy:

```text
... Opened archive .../classes.jsa
... Opened archive cds-demo.jsa
... Mapped static  region #... (ReadOnly)
... Mapped dynamic region #... (ReadOnly)
... Mapped dynamic region #... (ReadWrite)
```

Đây là **log mẫu được rút gọn**, không phải thứ tự/timestamps/addresses bắt buộc. JVM có thể map archive tại những địa chỉ khác và log thêm xử lý pointer relocations/heap objects.

### 14.1. Vì sao có nhiều regions?

HotSpot có thể chia archive thành những regions được bảo vệ/định vị khác nhau theo cách triển khai. Các regions có thể chứa read-only metadata, writable/COW data, bitmap/relocation metadata v.v. **Không hứa mỗi archive chỉ một memory-mapped region**.

### 14.2. Vì sao có thể phải relocate?

Archive được tạo với một số layouts/pointer assumptions. Khi map vào process mới, address-space layout và runtime pointer encoding có thể khác; VM xử lý compatibility hoặc relocations nếu hỗ trợ.

Điều này không phá nguyên lý sharing; shared file pages vẫn có thể mang lại lợi ích nếu tương thích, nhưng đôi khi phải có private adjustments.

### 14.3. Không dùng `-Xlog:cds` như stopwatch chính xác

Logging có I/O overhead và nội dung event là diagnostic, không phải một phép tách thuần túy tất cả memory mapping/preparation CPU costs. Dùng log để **xác nhận và giải thích**, rồi đo startup benchmark **không logging**.

## 15. Static AppCDS — class list, dump và reuse

Static AppCDS tách **thu thập danh sách class** và **tạo archive** thành hai pha. Ví dụ tối giản trên HotSpot JDK 17:

```bash
java -Xshare:off -XX:DumpLoadedClassList=cds-demo.classlist -cp cds-demo.jar CDSDemo
java -Xshare:dump -XX:SharedClassListFile=cds-demo.classlist -XX:SharedArchiveFile=cds-static.jsa -cp cds-demo.jar
java -XX:SharedArchiveFile=cds-static.jsa -cp cds-demo.jar CDSDemo
```

Lệnh đầu **chạy ứng dụng**, lệnh thứ hai tạo archive **không gọi `main`**, lệnh cuối khởi chạy JVM mới. Điều kiện JDK/classpath/JAR phải tương thích; ứng dụng tiếp tục tuân theo Java class initialization semantics. Bài tập đầy đủ về classlist, dynamic top archive, flags, CI/CD và các trường hợp lỗi là trọng tâm riêng của bài **AppCDS, archive tĩnh và archive động**.

| So sánh | Static archive | Dynamic archive |
| --- | --- | --- |
| Class list riêng | Có trong workflow thông dụng | Không bắt buộc |
| Thời điểm tạo | Pha `-Xshare:dump` | Khi app exit với `ArchiveClassesAtExit` |
| Base/top | Custom static/base | Top layer phụ thuộc base |
| Java objects/state dùng chung giữa process | Không tự động | Không tự động |

Trên HotSpot JDK 17, static dump có ràng buộc classpath; vì vậy dùng JAR thay vì giả định non-empty class directory luôn hợp lệ. Xác minh archive bằng `-Xlog:cds` và **nguồn class** bằng `-Xlog:class+load`, không chỉ kiểm tra file tồn tại.

## 16. Dùng `jcmd VM.cds` khi phù hợp

Tài liệu Oracle JDK 17 `java` có nhắc công cụ `jcmd` có thể hỗ trợ tạo CDS archives từ JVM đang chạy, với những diagnostic commands như:

```text
jcmd <pid> VM.cds static_dump my_static_archive.jsa
jcmd <pid> VM.cds dynamic_dump my_dynamic_archive.jsa
```

**`<pid>` là placeholder**; dùng PID thực tế. Các commands/arguments có thể phụ thuộc build/permissions.

### 16.1. Dynamic dump cần cấu hình phù hợp

Theo tài liệu `java` của JDK 17, `VM.cds dynamic_dump` có thể cần JVM được start với:

```text
-XX:+RecordDynamicDumpInfo
```

Không giả định mọi process đã chạy sẵn đều cho phép dynamic dump. Kiểm tra:

```bash
jcmd <pid> help
```

### 16.2. Không đụng vào production process không kiểm soát

Diagnostic operations có thể làm tăng CPU/memory/I/O, tạo file và thay state profiling. Thử trên môi trường test/staging trước khi thêm dump operation vào production runbooks.

## 17. Tại sao archive đôi khi không sử dụng được?

Archive có thể không phù hợp vì:

- **JDK vendor/build/version** không trùng hoặc không tương thích với archive tạo ra.
- **Classpath/JAR changes**: file JAR chứa lớp đã archive được sửa sau khi dump.
- **Module/classpath restrictions** hoặc các option `--upgrade-module-path`, `--patch-module`, `--limit-modules` có tác động làm CDS không khả dụng trong những cấu hình JDK 17 được tài liệu ghi rõ.
- **Base archive** của dynamic top archive không phù hợp.
- **VM options/GC/pointer encoding** hoặc những runtime assumptions khác không khớp.
- **Custom classloader behavior** làm lớp không đủ điều kiện hoặc không thể được dùng từ archive.
- **Java agents/instrumentation/class transformation** thay hình dạng class khi load.
- Archive bị thiếu, không đọc được, bị hỏng hoặc permission không phù hợp.

### 17.1. Fallback vs fail-fast

- `-Xshare:auto`: cố dùng nếu tương thích, có thể fallback khi không dùng được.
- `-Xshare:on`: yêu cầu dùng, nên lỗi khi archive không khả dụng; chỉ dùng testing.
- `-Xshare:off`: không dùng archive, hữu ích tạo baseline.

**Không kết luận CDS đang hoạt động chỉ vì VM options có `-Xshare:auto`**. Cần bằng chứng từ `-Xlog:cds=info` và `-Xlog:class+load=info`.

### 17.2. Hãy rebuild khi deployment artifact thay đổi

Một CI/CD pipeline tạo application JAR mới thì phải xem archive cũ như một artifact **versioned và có dependency** lên JDK + app. Nếu classpath/JAR thay đổi, nên tạo lại archive dưới cùng cấu hình deployment tương ứng.

### 17.3. An toàn, đồng nhất và provenance

Không coi `.jsa` là một nguồn dữ liệu tùy ý có thể tải từ bên thứ ba rồi map vào production. Nó là **VM-specific binary artifact**, nên phải được tạo/kiểm soát qua trusted build pipeline và được bảo vệ bằng file permissions, image signatures và deployment policy thích hợp.

## 18. CDS và Class Data Sharing trong container

Một hình dung phổ biến:

```text
Container image:
  JDK runtime + classes.jsa
  application.jar
  cds-demo.jsa (optionally generated at image build)
          |
       deployment
          |
  +-------+--------+
  |                |
Pod/JVM A        Pod/JVM B
  |                |
Same host image layers + compatible mapping?
  -> OS có thể share file-backed physical pages
  -> mỗi JVM vẫn có private heap, threads, JIT state
```

### 18.1. Shared pages giữa containers có thể xảy ra

Khi containers trên **cùng OS kernel/host** map file-backed pages phù hợp, filesystem/page cache và memory mappings có thể đem lại sharing tương tự các processes thông thường. Nhưng không được suy **containers ở các hosts khác nhau có một RAM vật lý dùng chung**.

### 18.2. Image build có thể tạo archive trước deployment

Nếu build pipeline tạo archive bằng **đúng JDK build, app JAR và options** dự định sử dụng, runtime có thể map nó ở lần khởi động đầu. Điều này có thể hữu ích cho services restart/scale thường xuyên.

### 18.3. Application mutable state không chia sẻ

Pod A đổi `static int count` không thay `static int count` của Pod B. CDS chỉ giúp reuse những dữ liệu lớp đủ điều kiện; đồng bộ state giữa các pods vẫn cần network/database/messaging hoặc cơ chế phân tán thích hợp.

### 18.4. Đừng chỉ đo container boot event

Tách:

- Container image pull/unpack/start.
- OS/JVM process startup.
- Application readiness.
- First-request latency.
- Memory footprint/RSS/PSS.
- Resource pressure khi many JVMs cùng host.

CDS thường giúp **bên trong Java startup/class loading**, không giải quyết toàn bộ image pull/network initialization.

## 19. JAR packaging và classpath consistency

Archive tạo từ application JAR được kiểm tra với classpath/JAR metadata, do đó:

1. Giữ path/order classpath hợp lệ theo JDK rules.
2. Không sửa JAR sau khi tạo archive.
3. Không trộn classes từ phiên bản khác mà không rebuild archive.
4. Kiểm tra symlinks/mount paths trong deployment environment nếu phát sinh incompatibility.
5. Không cho rằng tương đương source code bảo đảm binary artifact giống nhau; timestamp/manifest/layout có thể ảnh hưởng checks.

### 19.1. Fat JAR, nested JAR và custom loaders

Một fat JAR hoặc framework launcher có thể dùng class loader/URL/resource semantics khác classpath JAR thông thường. Không giả định mọi framework nested-JAR structure đều được CDS xử lý giống lệnh `java -cp cds-demo.jar CDSDemo`.

Cách đúng là tạo archive bằng **chính phương thức launch production** được hỗ trợ rồi kiểm tra class origins trong logs.

### 19.2. Classpath thay đổi nhưng archive vẫn được dùng một phần?

Các JDK releases có quy tắc kiểm tra classpath khác nhau cho base/top archive. Oracle JDK 17 document mô tả constraints cụ thể như classpath lúc dump phải trùng hoặc là prefix của runtime classpath trong những workflow liên quan. **Không suy mọi loại thay đổi đều làm archive hoàn toàn vô dụng, cũng không hứa bất kỳ thay đổi nào an toàn**. Xem log và chính sách build/version.

## 20. Đánh giá startup bằng thí nghiệm có kiểm soát

Tối thiểu tạo các nhóm:

| Nhóm | JVM options | Ý nghĩa |
| --- | --- | --- |
| **A** | `-Xshare:off` | Baseline không CDS |
| **B** | `-Xshare:auto` | Default core-class CDS |
| **C** | `-XX:SharedArchiveFile=cds-demo.jsa` | Default base + AppCDS dynamic top |
| **D** | `-Xshare:on` (chỉ test phù hợp) | Confirm tính sẵn dùng, không là production tuning mặc định |

Cần giữ cùng:

- JDK vendor/version/build.
- App JAR và classpath.
- JVM heap/GC/cpu flags, container quotas.
- Workload đầu vào và correctness checksum.
- Host/OS và mức cạnh tranh CPU/I/O tương đương.
- Số repetitions, cách randomize/exchange order.
- Mốc đo (process wall time, time to ready, first request).

### 20.1. Thu đúng metrics

**External process elapsed time**: bao gồm launch/bootstrap/execution/shutdown. Sử dụng GNU `time`, PowerShell `Measure-Command` hoặc parent benchmark harness.

**Application readiness time**: dùng readiness marker/health probe, không chỉ khi `main` bắt đầu.

**First request latency**: đo riêng vì CDS có thể chuyển một phần chi phí startup, nhưng ứng dụng vẫn có lazy init/JIT/network work.

**Total memory footprint**: đo khi nhiều JVM instances chạy đồng thời, phân biệt RSS và shared pages/PSS khi OS hỗ trợ.

### 20.2. Không đo baseline khi in class-load logs

`-Xlog:class+load=info` có thể sinh hàng nghìn lines và tạo I/O overhead. **Chạy diagnostics riêng baseline** để kiểm tra archive use, sau đó đo performance không bật heavy logging.

### 20.3. Đừng lấy một sample làm tốc độ bảo đảm

Một JVM restart mới có thể dùng OS page cache đã ấm; C2 runtime state vẫn mới. CDS benefit thường lớn hơn trong workload class-heavy, trong khi application chủ yếu I/O có thể gần như không đổi end-to-end startup.

Đo **median/spread/p95** qua nhiều runs và nêu assumptions rõ ràng.

## 21. Ví dụ tập lệnh benchmark trên PowerShell

Sau khi có `cds-demo.jar` và `cds-demo.jsa`:

```powershell
foreach ($mode in @("off", "base", "app")) {
    Write-Output "MODE=$mode"
    foreach ($trial in 1..5) {
        if ($mode -eq "off") {
            Measure-Command { java -Xshare:off -cp cds-demo.jar CDSDemo }
        } elseif ($mode -eq "base") {
            Measure-Command { java -Xshare:auto -cp cds-demo.jar CDSDemo }
        } else {
            Measure-Command {
                java -XX:SharedArchiveFile=cds-demo.jsa -cp cds-demo.jar CDSDemo
            }
        }
    }
}
```

Đây là lệnh **minh họa đo lặp** chứ chưa phải báo cáo statistics tự động. Nếu cần tính median, hãy thu `TotalMilliseconds` từ `Measure-Command` vào CSV và đảm bảo Java process exit code/checksum hợp lệ.

### 21.1. Những lỗi đo dễ gặp

- Đo run thứ nhất với filesystem cache lạnh, run thứ hai với cache ấm.
- Dùng `-Xshare:off` cho baseline nhưng quên archive đã bị fallback trong mode khác.
- Dùng log verbose ở chỉ một mode.
- So một process kết thúc ngay với service tồn tại liên tục.
- Đưa `-Xcomp`, heap/GC flags khác vào một nhóm làm mất kiểm soát biến.
- Benchmark quá ngắn nên shell/process scheduling noise áp đảo CDS effects.

## 22. Các lợi ích CDS không được hứa tuyệt đối

**CDS có thể**:

- Giảm work khi load/link những classes nằm trong archive.
- Giảm startup time của class-heavy JVM applications.
- Giảm tổng memory footprint qua nhiều processes sharing archive pages.
- Giảm phần JVM startup trong service scale-out/container workloads.

**CDS không đảm bảo**:

- Mọi class được nạp từ archive.
- Mọi class initialize sớm hoặc không initialize.
- Mọi Java objects hoặc static fields được share.
- Các hot methods tự có C2 compiled code lúc startup.
- Runtime execution throughput tăng đúng một tỷ lệ.
- Tất cả Java process startup time là class loading, hoặc có thể giảm hết bằng CDS.
- Archive tương thích qua mọi JDK update, app version hoặc JVM flags.
- Tránh mọi class verification/resolution work có thể phát sinh.

**Không biến “up to X% faster startup” của một benchmark bên ngoài thành performance claim cho tất cả services.**

## 23. So sánh CDS với các khái niệm khác

| Khái niệm | Cơ chế | Khác CDS ở điểm nào? |
| --- | --- | --- |
| **JIT C1/C2** | Biên dịch hot bytecode thành machine code | Không chỉ chia sẻ class metadata |
| **Class initialization** | Chạy `<clinit>` khi có active use | CDS không bỏ init side effects |
| **Classpath scanning** | Framework tìm classes/annotations/resources | Scanning là work khác, có thể chưa định nghĩa classes |
| **JAR compression** | Nén classes/resources trên đĩa | CDS lưu runtime-ready data để tái dùng |
| **OS filesystem page cache** | Cache pages của tệp | CDS tận dụng mappings và runtime format, không chỉ caching JAR bytes |
| **Metaspace** | Vùng runtime metadata của HotSpot | Archived metadata có sharing/mapping rules riêng |
| **Shared mutable Java heap** | Cơ chế chia sẻ state đối tượng giữa processes | CDS không cung cấp loại API đó |
| **CRaC/checkpoint restore** | Lưu/khôi phục trạng thái process trong công nghệ tương ứng | Không đồng nghĩa CDS; lifecycle và side effects khác |
| **Native-image/AOT compilation** | Biên dịch một phần code trước runtime | Không phải cùng định dạng/hợp đồng với AppCDS JDK 17 |

## 24. Những hiểu lầm thường gặp

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| “CDS chia sẻ mọi Java objects giữa JVM processes.” | Chủ yếu chia sẻ metadata/archive pages đủ điều kiện; mỗi process có heap/state riêng. |
| “Hai JVM có cùng static field value vì cùng archive.” | Static mutable state được khởi tạo/quản lý riêng cho mỗi runtime. |
| “Có `.jsa` thì static initializer không chạy.” | `<clinit>` vẫn theo JLS initialization rules. |
| “CDS là cache của C2 compiled code.” | Không phải JIT code cache nói chung của HotSpot JDK17. |
| “Class loaded từ shared objects file là class có sẵn Java instance.” | Shared class representation khác object instance. |
| “CDS bật nghĩa mọi class của JAR archived.” | Chỉ các classes đủ điều kiện được tạo archive và dùng. |
| “Archive disk file luôn nhỏ hơn JAR.” | Runtime-ready archive data có thể lớn hơn class bytes. |
| “-Xshare:on là lựa chọn production tốt nhất.” | Oracle ghi rõ chỉ nên dùng testing vì có thể khiến VM fail startup. |
| “-Xshare:auto luôn bắt buộc dùng được archive.” | Có thể fallback khi archive không tương thích. |
| “Chỉ cần chạy mặc định và thấy kết quả đúng là CDS hoạt động.” | Cần log `cds` và `class+load` để xác nhận. |
| “CDS chia sẻ dữ liệu qua RAM giữa hai hosts khác nhau.” | OS page sharing là trên cùng host/kernel, không phải phân tán RAM. |
| “Không có RO pages share thì mọi CDS performance benefit biến mất.” | Ngay một process có thể hưởng startup benefit từ archive. |
| “RSS của N JVM cộng lại là tổng RAM không trùng lặp.” | RSS có thể đếm shared pages nhiều lần. |
| “Dynamic top archive có thể đứng độc lập không cần base.” | Theo mô hình JDK17 dynamic archive phụ thuộc base tương thích. |
| “Chỉnh JAR sau khi dump vẫn chắc dùng archive cũ.” | Runtime validation có thể từ chối và yêu cầu rebuild. |
| “-XX:ArchiveClassesAtExit lưu heap tại exit.” | Nó ghi class data archive, không snapshot tùy ý của app heap. |
| “CDS thay việc kết nối DB hoặc warm TLS.” | Không. Đây là application/environment initialization work khác. |
| “Class sharing xóa class identity theo loader.” | Loader/class identity semantics vẫn phải đúng. |
| “Có class ở archive thì runtime không cần class initialization lock.” | Class init synchronization/lifecycle vẫn được giữ. |
| “Default archive chứa hết application classes.” | Default thường nhắm vào các core JDK classes. |
| “Bật class-load logging không ảnh hưởng benchmark.” | Verbose logging có overhead và phải tách diagnostic runs. |
| “CDS chỉ hữu ích khi có hai JVM.” | Một JVM có thể tiết kiệm startup; nhiều JVM giúp thêm memory sharing. |

## 25. Tự kiểm tra kèm đáp án

**Câu 1.** CDS là viết tắt của gì?

**Đáp án:** Class Data Sharing — cơ chế HotSpot archive/reuse những dữ liệu lớp đủ điều kiện để cải thiện startup và memory footprint.

**Câu 2.** Archive thường có đuôi gì?

**Đáp án:** `.jsa`, ví dụ `classes.jsa` hoặc `cds-demo.jsa`.

**Câu 3.** Nhiều JVM share archive bằng cơ chế cấp OS nào?

**Đáp án:** Memory mapping của file, có thể share read-only physical pages và COW/private writable state.

**Câu 4.** Có thể làm thay đổi `static int counter` của JVM B bằng cách sửa counter của JVM A không?

**Đáp án:** Không. Application static/runtime state là riêng từng process.

**Câu 5.** CDS có tự bỏ `<clinit>` không?

**Đáp án:** Không. Initialization code/side effects vẫn theo Java semantics.

**Câu 6.** CDS có giúp class loading nhanh hơn JAR không?

**Đáp án:** Có thể. Archived runtime-ready metadata giúp tránh lặp một phần read/parse/link work với classes phù hợp.

**Câu 7.** `-Xshare:off` làm gì?

**Đáp án:** Tắt CDS, thường dùng làm baseline/diagnostics.

**Câu 8.** `-Xshare:auto` khác `-Xshare:on` thế nào?

**Đáp án:** Auto dùng nếu có archive phù hợp và có thể fallback; on yêu cầu dùng, lỗi nếu không thể và chỉ nên dùng testing.

**Câu 9.** Default CDS archive thường chứa lớp nào?

**Đáp án:** Những platform/core library classes hay dùng được build JDK chuẩn bị sẵn, tùy distribution.

**Câu 10.** AppCDS là gì?

**Đáp án:** Application Class Data Sharing, mở rộng archive tới các application/library classes đủ điều kiện.

**Câu 11.** Dynamic top archive được tạo bằng option nào?

**Đáp án:** `-XX:ArchiveClassesAtExit=app.jsa` trong workflow phù hợp với JDK17.

**Câu 12.** Top archive có cần base archive không?

**Đáp án:** Có, theo mô hình dynamic AppCDS JDK17 cần base archive tương thích.

**Câu 13.** Vì sao `CDSDemo$Engine` vẫn in marker khi chạy lần sau bằng archive?

**Đáp án:** Static initializer chạy theo class initialization của process mới; metadata sharing không giữ live static state của process cũ.

**Câu 14.** Log nào chứng minh app class đến từ archive?

**Đáp án:** `-Xlog:class+load=info` với source báo `shared objects file` hoặc `shared objects file (top)` trên HotSpot phù hợp.

**Câu 15.** `-Xlog:cds=info` cho biết gì?

**Đáp án:** Các thông tin mở/map archive, region handling, compatibility/relocation theo JVM build.

**Câu 16.** Vì sao không nên tải archive `.jsa` tùy tiện trên Internet?

**Đáp án:** Đây là binary VM-specific artifact; cần nguồn đáng tin và đúng build/configuration để tránh compatibility/security issues.

**Câu 17.** Static AppCDS khác dynamic ở bước đầu nào?

**Đáp án:** Workflow static thường cần capture class list và chạy `-Xshare:dump`; dynamic có thể archive tại process exit mà không cần class list riêng.

**Câu 18.** Vì sao không cộng RSS của ba JVM rồi gọi đó là RAM dùng riêng?

**Đáp án:** Shared mapped pages có thể được tính lặp trong RSS của mỗi process; PSS/OS metrics giúp đánh giá chia sẻ.

**Câu 19.** Có thể dùng archive tạo từ JDK 24 trên binary JDK 17 mà không kiểm tra không?

**Đáp án:** Không. CDS archives rất nhạy với JDK build/format compatibility.

**Câu 20.** CDS có làm methods `compute` C2 compiled trước lần chạy đầu không?

**Đáp án:** Không. JIT profiling/compilation là phần runtime khác CDS class metadata.

**Câu 21.** Vì sao app benchmark có thể không thấy startup cải thiện lớn dù CDS dùng đúng?

**Đáp án:** Class load/link chỉ là một phần tổng thời gian; framework initialization, I/O, networking, JIT, GC và OS launch có thể chiếm phần lớn.

**Câu 22.** Nếu archive không dùng được trong `-Xshare:auto`, cách kiểm tra?

**Đáp án:** Xem `-Xlog:cds=info`, `-Xlog:class+load=info` và compatibility conditions, sau đó rebuild archive nếu phù hợp.

**Câu 23.** Hai pods ở các hosts khác nhau có physical pages chung nhờ CDS không?

**Đáp án:** Không. Mỗi host có page cache/RAM riêng; CDS files có thể được triển khai đồng nhất nhưng không tạo shared physical memory liên host.

**Câu 24.** Khi nào nên tạo archive?

**Đáp án:** Trong CI/build/staging với JDK/application/config tương ứng production, rồi đo trước/sau; tránh archive cũ không cùng release.

## 26. Checklist áp dụng thực tế

1. Xác định **JDK vendor/build/version** của hệ đích.
2. Kiểm tra default CDS bằng `-Xlog:cds=info`.
3. Đối chiếu `-Xshare:off` và `-Xshare:auto` với cùng workload.
4. Nếu muốn archive application classes, đóng gói JAR theo workflow JDK17.
5. Chạy application qua `ArchiveClassesAtExit` để tạo dynamic top archive phù hợp.
6. Dùng `SharedArchiveFile` khi chạy lại.
7. Kiểm tra class origin qua `-Xlog:class+load=info`.
8. Kiểm tra static initializer vẫn chạy đúng trên mỗi process.
9. Ghi lại archive size và class coverage; không tối ưu chỉ vì có file.
10. Kiểm tra JAR unchanged, classpath/modulepath validity và base archive compatibility.
11. Không dựa vào `-Xshare:on` trong production.
12. Nếu custom loaders/agents có mặt, thử với đúng production launch command.
13. Với containers, phân biệt restart time và image pull/start overhead.
14. Với memory, so footprint khi nhiều JVMs chạy đồng thời; không cộng RSS thiếu ngữ cảnh.
15. Chạy diagnostic logging **tách** performance baseline.
16. Đo process wall time, application readiness, first request và memory footprint qua nhiều runs.
17. Không nhầm CDS với JIT warm-up, class init hoặc database connection setup.
18. Đưa archive thành artifact được quản lý/version cùng JDK và app, tái tạo khi thay build.

### Kết luận

```text
JAR/class bytes              CDS archive (.jsa)
     |                             |
  conventional                    runtime-ready
  loading path                    class metadata
     |                             |
     +---------- HotSpot ----------+
                       |
              map compatible archive
                       |
         +-------------+-------------+
         |                           |
    JVM process A               JVM process B
         |                           |
    shared file pages when supported
         |                           |
    private JVM heap             private JVM heap
    private statics              private statics
    private JIT state            private JIT state

CDS speeds eligible class data reuse.
CDS does NOT replace class initialization or JIT.
```

**Tóm lại:** CDS giúp HotSpot **không phải tạo lại toàn bộ metadata của những classes phổ biến** mỗi khi JVM process mới chạy. Archive được **memory-map**, cho phép chia sẻ read-only pages ở cấp OS và tái dùng dữ liệu runtime-ready. Khi mở rộng thành AppCDS, nhiều application/library classes cũng có thể tham gia. Nhưng mỗi JVM vẫn có **heap, static fields, threads, GC và JIT state riêng**; `<clinit>`, ứng dụng và business workflows vẫn phải vận hành đúng Java semantics. Đánh giá lợi ích qua **archive-use logs và startup/memory benchmark thực tế**, không chỉ qua sự tồn tại của tệp `.jsa`.

### Tài liệu chính thức

- [Oracle JDK 17 — Class Data Sharing](https://docs.oracle.com/en/java/javase/17/vm/class-data-sharing.html): default archives, memory footprint, dynamic AppCDS, các `-Xshare` options.
- [Oracle JDK 17 — `java` Command, Application Class Data Sharing](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): static/dynamic workflows, classpath/modulepath restrictions, `SharedArchiveFile`, `ArchiveClassesAtExit` và `jcmd VM.cds`.
- [dev.java — CDS and AppCDS in HotSpot](https://dev.java/learn/jvm/other-tools/cds-appcds/): cơ chế memory mapping, mặc định `-Xshare:auto` và hướng dẫn.
- [OpenJDK JEP 310 — Application Class-Data Sharing](https://openjdk.org/jeps/310): mở rộng tới application classes.
- [OpenJDK JEP 341 — Default CDS Archives](https://openjdk.org/jeps/341): default archive cùng JDK.
- [OpenJDK JEP 350 — Dynamic CDS Archives](https://openjdk.org/jeps/350): dynamic top archive và dump khi process exit.
- [JVMS §5 — Loading, Linking, Initializing (Java SE 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): semantics JVM class loading/initialization.
- [JLS §12.4 — Initialization of Classes and Interfaces (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4): active use và class initialization.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fast-startup-003">AppCDS, archive tĩnh và archive động</a>

<details>
<summary>Xem chi tiết</summary>

# AppCDS, archive tĩnh và archive động trong HotSpot Java 17

**AppCDS (Application Class-Data Sharing)** là một cơ chế của HotSpot JVM giúp **tái sử dụng dữ liệu lớp đã được chuẩn bị ở định dạng phù hợp để nạp nhanh** qua một file **CDS archive**. Nó mở rộng ý tưởng **CDS (Class Data Sharing)** từ các lớp thư viện nền tảng sang cả các lớp mà ứng dụng/thư viện dùng. Với Java 17, có hai cách chính để tạo archive riêng cho ứng dụng:

- **Archive tĩnh (static archive):** chạy thử ứng dụng để thu thập danh sách lớp, sau đó dùng **`-Xshare:dump`**, `-XX:SharedClassListFile` và `-XX:SharedArchiveFile` để sinh archive.
- **Archive động (dynamic archive):** chạy ứng dụng có **`-XX:ArchiveClassesAtExit=<file.jsa>`**; lúc thoát, VM tạo một archive bổ sung phía trên **base archive**. Lần chạy tiếp theo chỉ định `-XX:SharedArchiveFile=<dynamic.jsa>` để sử dụng lại.

Mục tiêu của AppCDS là cải thiện **startup time** và có thể giảm **memory footprint** khi nhiều JVM processes dùng cùng archive; nó **không phải** công cụ biên dịch toàn bộ Java source sang native executable, không phải cache kết quả tính toán nghiệp vụ và không bảo đảm mọi class trong ứng dụng đều được archive.

Bài nằm trong chủ đề **Khởi động nhanh**, tập trung HotSpot/JDK **17**. Bạn sẽ hiểu bản chất class metadata, sự khác biệt của static/dynamic archive, cách tạo JAR để chạy thử, quy trình dump/use/verify, các ràng buộc về class path, JDK build, base archive, hiệu năng thực tế và những lỗi triển khai thường gặp. Các chương trình ví dụ dùng Java 17 với stdout xác định; thông điệp từ CDS logging **không được trình bày như stdout cố định**.

## 1. Tại sao class loading ảnh hưởng startup?

Khi khởi chạy ứng dụng Java, JVM cần tìm và nạp các lớp cần thiết; tùy trường hợp nó còn thực hiện những bước như đọc class files, giải mã cấu trúc class, verification, linking và initialization. Trong ứng dụng có nhiều dependencies, các thao tác với JAR files và metadata của hàng nghìn lớp có thể đóng góp đáng kể vào thời gian trước khi chức năng đầu tiên sẵn sàng phục vụ.

```text
Thông thường:
  JAR / JDK runtime image
      │
      ▼
  Tìm bytes cho class
      │
      ▼
  Load / verify / link theo cơ chế của JVM
      │
      ▼
  Runtime class metadata và execution

Với CDS/AppCDS (mô hình khái niệm):
  CDS archive đã chuẩn bị
      │ map các vùng dữ liệu phù hợp
      ▼
  Một phần metadata có thể được tái sử dụng nhanh hơn
      │
      ▼
  Runtime class vẫn phải tuân thủ Java semantics
```

CDS giảm một phần công việc khởi động nhờ dữ liệu có định dạng HotSpot dễ dùng và cơ chế **memory mapping**. Nó **không bỏ qua mọi bước của Java class life cycle**; những kiểm tra và hoạt động phải xảy ra khi runtime thực sự sử dụng lớp vẫn phải đảm bảo tính đúng đắn.

### 1.1. CDS khác AppCDS thế nào?

| Khái niệm | Nội dung chính | Mục đích |
| --- | --- | --- |
| **CDS mặc định** | Một bộ lớp nền tảng được JDK đóng gói sẵn hoặc đã tạo archive | Tăng tốc các JVM process kể cả khi chưa cấu hình ứng dụng |
| **AppCDS** | Mở rộng việc archive sang lớp ứng dụng và thư viện phù hợp | Giảm phần startup work riêng của ứng dụng |
| **Static CDS archive** | File được xây trong một bước dump rõ ràng, có thể dùng class list | Kiểm soát tập lớp được chuẩn bị |
| **Dynamic CDS archive** | File bổ sung được ghi vào cuối một lần chạy hoặc bằng hỗ trợ runtime | Ít bước hơn, dựa trên các lớp đã thực sự được nạp |
| **Base archive** | Archive được map trước, thường là default CDS archive của JDK | Là nền để dynamic archive tham chiếu |
| **Top-layer dynamic archive** | Chứa lớp bổ sung chưa có ở base archive | Mở rộng base mà không cần ghi lại toàn bộ lớp nền |

Trong Java 17, các thuật ngữ **static** và **dynamic** chủ yếu nói tới **cách/lớp archive được tạo, sử dụng và xếp chồng**. “Dynamic” không có nghĩa file archive tự động cập nhật liên tục mỗi khi class mới được load trong tất cả các lần chạy.

### 1.2. CDS archive không phải heap dump

File `.jsa` là một file lưu **dữ liệu phù hợp với cơ chế class data sharing của HotSpot**, không phải:

- `.hprof` heap dump chứa các Java objects đang sống.
- `.class` do `javac` sinh trực tiếp.
- JAR/WAR đóng gói toàn bộ bytecode và resources.
- Native executable như kết quả của một native-image build.
- JIT profile dump hoặc bộ mã máy đã C2 compile để tái dùng một cách phổ quát.

**Archive không thay thế JAR gốc.** Khi chạy, ứng dụng vẫn cần đúng class path/module path, tài nguyên, cấu hình và bytecode có thể được kiểm tra phù hợp theo quy tắc CDS.

## 2. Cơ chế memory mapping và tiết kiệm bộ nhớ

Khi JVM khởi động và chấp nhận một CDS archive, nó có thể **memory-map** các vùng dữ liệu đã chuẩn bị. Các phần read-only có thể được hệ điều hành chia sẻ dưới dạng physical pages giữa nhiều JVM processes trên cùng host.

```text
             CDS archive trên đĩa
                    │
          ┌─────────┼─────────┐
          │         │         │
          ▼         ▼         ▼
      JVM proc A  JVM proc B  JVM proc C
          │         │         │
          └── mapped read-only pages ──┘
                 có thể chia sẻ vật lý
```

**Hai lợi ích tiềm năng:**

1. **Startup:** giảm chi phí phải tự đọc/chuẩn bị lại một phần class metadata trong từng process.
2. **Footprint tổng:** nhiều JVM có thể sử dụng chung một số trang memory-mapped, giảm memory riêng của từng process khi so với việc mỗi process phải tự tạo dữ liệu tương tự.

Tuy vậy:

- **Không phải toàn bộ archive đều là các trang read-only chia sẻ hoàn hảo.**
- `RSS` của từng process có thể tính shared pages, nên **cộng RSS của nhiều JVM dễ đếm trùng**; dùng PSS/USS và OS-specific tools khi đánh giá tổng memory vật lý.
- Archive cũng chiếm dung lượng disk và có metadata overhead.
- Không có công thức bảo đảm “có 10 JVM thì giảm RAM đúng 10 lần”.
- Phần tiết kiệm phụ thuộc số lớp thực được share, lượng metadata, JDK, OS và startup behavior.

### 2.1. Tại sao không archive mọi class có sẵn?

Một archive bao gồm class metadata trong định dạng đã chuẩn bị, có thể lớn hơn dữ liệu bytecode trong JAR hoặc runtime image. Oracle lưu ý lưu trữ CDS classes ở định dạng tối ưu có thể tốn dung lượng nhiều lần so với class files gốc. Archive cả các lớp ứng dụng không bao giờ cần có thể lãng phí disk và thời gian build.

Vì vậy cần phân biệt **classes hiện diện trên classpath** với **classes thực sự được tải ở những code paths cần tối ưu**. Nếu chỉ chạy startup tối thiểu để tạo archive, những classes được nạp muộn bởi request đầu tiên có thể chưa nằm trong archive.

## 3. Default CDS archive của JDK 17

Các bản phân phối JDK hiện đại thường cung cấp một **default CDS archive** cùng JDK, thường là file `classes.jsa` trong thư mục runtime của HotSpot; Oracle java command documentation nêu các vị trí tương ứng tùy hệ điều hành.

```text
Linux/macOS, ví dụ:
  $JAVA_HOME/lib/server/classes.jsa

Windows, ví dụ:
  %JAVA_HOME%\bin\server\classes.jsa
```

**Đừng dùng đường dẫn ví dụ làm giả định bắt buộc cho mọi vendor/build.** Có bản phân phối đóng gói khác, đường dẫn có thể thay đổi và archive mặc định có thể vắng mặt.

Từ JDK 12, default CDS archive được đóng gói trong nhiều bản JDK phổ biến. Ở JDK 17, cơ chế này thường **đã bật theo chế độ tự động** khi JVM có archive thích hợp; bạn không cần tự chạy `-Xshare:dump` chỉ để dùng một default archive đã sẵn có.

### 3.1. Kiểm tra với các chế độ `-Xshare`

| Tùy chọn | Ý nghĩa | Dùng khi nào? |
| --- | --- | --- |
| `-Xshare:auto` | Cố dùng CDS nếu có thể; không bắt buộc thành công | Phù hợp chạy ứng dụng thông thường |
| `-Xshare:off` | Tắt sử dụng shared archive | Baseline so sánh hoặc thu thập class list |
| `-Xshare:on` | Yêu cầu CDS phải sử dụng được; không được thì JVM có thể lỗi khởi động | Chỉ dùng trong kiểm thử xác nhận archive |
| `-Xshare:dump` | Chế độ tạo static archive | Bước dump, không chạy main ứng dụng theo nghĩa thông thường |

**Cảnh báo:** Oracle khuyến nghị tránh dùng `-Xshare:on` trong production vì nó có thể khiến JVM không khởi động khi archive không tương thích hoặc cấu hình đổi. `auto` là hành vi thân thiện hơn đối với môi trường có điều kiện runtime khác nhau.

## 4. Static archive: quy trình thử chạy → class list → dump → reuse

**Static AppCDS archive** là file được sinh trong một **bước dump riêng biệt** dựa trên class list và những yếu tố đầu vào đã chuẩn bị.

```text
            appcds-demo.jar
                   │
          1. Trial application run
          -Xshare:off
          -XX:DumpLoadedClassList=app.classlist
                   │
                   ▼
             app.classlist
                   │
          2. Explicit static dump
          -Xshare:dump
          -XX:SharedClassListFile=app.classlist
          -XX:SharedArchiveFile=app-static.jsa
                   │
                   ▼
             app-static.jsa
                   │
          3. App runtime
          -XX:SharedArchiveFile=app-static.jsa
                   │
                   ▼
           startup có thể nhanh hơn
```

**Lợi thế:** quy trình có thể được đặt thành build/release step có đầu vào xác định, thuận tiện theo dõi class list, archive và các chính sách deployment.

**Chi phí:** phải có bước trial run tạo class list và bước dump riêng; có ràng buộc chặt chẽ hơn với class path/JAR files. Những lớp nạp ngoài đường thử có thể không được hưởng lợi.

### 4.1. `DumpLoadedClassList` làm gì?

Khi chạy với:

```bash
java -Xshare:off -XX:DumpLoadedClassList=app.classlist \
  -cp appcds-demo.jar AppCdsDemo
```

JVM thực thi chương trình để thu thập **danh sách classes được nạp**. File `app.classlist` là input cho bước dump tiếp theo; nó **không phải archive chứa lớp đã tối ưu**.

Đừng thay ý nghĩa `-XX:DumpLoadedClassList` bằng “ghi mọi class file trong tất cả JAR” — danh sách phản ánh những classes được nạp trong lần trial run và điều kiện runtime, không phải toàn bộ classpath.

### 4.2. `SharedClassListFile` và `SharedArchiveFile`

Bước dump:

```bash
java -Xshare:dump \
  -XX:SharedClassListFile=app.classlist \
  -XX:SharedArchiveFile=app-static.jsa \
  -cp appcds-demo.jar
```

- `-Xshare:dump` yêu cầu VM tạo archive.
- `SharedClassListFile` đưa danh sách lớp đã quan sát vào bước dump.
- `SharedArchiveFile` đặt tên file archive đầu ra.
- `-cp appcds-demo.jar` xác định classpath phù hợp để dump.
- Đây **không** phải lệnh chạy `AppCdsDemo.main()` lần thứ hai; không cần main class ở cuối.

**Quan trọng Java 17:** Theo Oracle launcher documentation, khi làm static archive với application classes, **classpath tại giai đoạn minh họa này chỉ chứa JAR files**. Tránh dùng một folder `out/` hay `target/classes` có classes unpacked thay cho JAR; các lớp nằm trong non-empty directories trên classpath là nguồn lỗi/không được archive theo các ràng buộc CDS tương ứng.

### 4.3. `SharedArchiveFile` trong runtime

Để dùng static archive:

```bash
java -XX:SharedArchiveFile=app-static.jsa \
  -cp appcds-demo.jar AppCdsDemo
```

Ứng dụng vẫn chạy `AppCdsDemo.main()` bình thường. Nếu archive hợp lệ và đủ điều kiện được dùng, một số class metadata được nạp từ shared archive thay vì mọi thứ lại được chuẩn bị từ nguồn gốc theo đường không CDS.

Không suy “thành công với exit code 0” đồng nghĩa 100% ứng dụng classes đã được share; phải bật logging và kiểm tra class sources.

## 5. Dynamic archive: ghi lớp bổ sung khi ứng dụng thoát

**Dynamic CDS archive** được giới thiệu để làm AppCDS dễ dùng hơn: chạy ứng dụng **một lần** với `-XX:ArchiveClassesAtExit=<file.jsa>`, HotSpot thu thập đủ thông tin cho archive từ các classes được tải trong quá trình chạy, rồi ghi **top-layer archive** khi ứng dụng kết thúc và thực hiện dump.

```text
              default base CDS archive
                       │
                       │ JVM chạy AppCdsDemo
                       ▼
       java -XX:ArchiveClassesAtExit=app-dynamic.jsa
                 -cp appcds-demo.jar AppCdsDemo
                       │
                 Runtime class loading
                       │
                 JVM shutdown / archive dump
                       ▼
                 app-dynamic.jsa
            (lớp bổ sung so với base archive)
                       │
                       ▼
         lần chạy kế tiếp -XX:SharedArchiveFile
                       │
                       ▼
                   Reuse base + top
```

Lệnh tạo:

```bash
java -XX:ArchiveClassesAtExit=app-dynamic.jsa \
  -cp appcds-demo.jar AppCdsDemo
```

Lệnh dùng archive:

```bash
java -XX:SharedArchiveFile=app-dynamic.jsa \
  -cp appcds-demo.jar AppCdsDemo
```

Với default base archive, HotSpot có thể lấy thông tin nền từ header của dynamic archive để map lại base phù hợp, nên runtime không nhất thiết phải viết tường minh `base.jsa:app-dynamic.jsa`.

### 5.1. “Dynamic” có phải archive tự cập nhật trong lúc JVM đang chạy?

Không theo cách hiểu phổ thông đó. Với `ArchiveClassesAtExit`:

1. Trong lúc JVM chạy, nó nạp các classes theo workload.
2. Lúc kết thúc theo cơ chế dump được hỗ trợ, VM **tạo file archive**.
3. **Lần chạy sau** mới sử dụng file vừa tạo.

File không phải “một lớp caching được tự động rewrite sau mỗi lần nạp class” trong một tiến trình đang chạy. Nếu deployment thay classes/JARs, archive cũ có thể không còn dùng được và cần tái tạo.

### 5.2. Vì sao dynamic archive vẫn cần base?

JDK đã có nhiều lớp nền tảng ở default CDS archive. Dynamic archive **không cần sao chép lại toàn bộ các lớp ấy**, mà bổ sung lớp ứng dụng/thư viện chưa có trong base. Điều này tiết kiệm dung lượng và giúp quá trình tạo đơn giản hơn.

Nếu default base không có hoặc không tương thích với cấu hình muốn dump, lệnh dynamic dump có thể không thành công. Phải kiểm tra log chứ không coi mọi lỗi dynamic archive là “không có quyền ghi file”.

### 5.3. Lỗi dễ mắc: lệnh tạo archive và lệnh dùng archive khác nhau

| Tùy chọn | Pha | Kết quả |
| --- | --- | --- |
| `-XX:ArchiveClassesAtExit=app-dynamic.jsa` | Chạy mẫu/dump động | Yêu cầu **tạo** archive khi ứng dụng thoát |
| `-XX:SharedArchiveFile=app-dynamic.jsa` | Run tái sử dụng | Yêu cầu **dùng** archive đã có |
| `-XX:DumpLoadedClassList=app.classlist` | Trial run cho static | **Chỉ** tạo danh sách lớp |
| `-Xshare:dump` + `SharedClassListFile` | Static dump | Tạo **static archive** |
| `-Xshare:off` | Baseline | Tắt dùng archive |

Không dùng `ArchiveClassesAtExit` thay `SharedArchiveFile` rồi trông đợi startup nhanh ngay trong chính lần chạy đang tạo archive.

## 6. Hai lớp archive: static base và dynamic top

HotSpot Java 17 hỗ trợ một base archive và một top-layer dynamic archive khi cấu hình theo cơ chế ứng dụng. Oracle nêu dạng tổng quát:

```text
-XX:SharedArchiveFile=<static_archive>:<dynamic_archive>
```

**Trên Windows, dấu phân tách là chấm phẩy `;` thay vì dấu hai chấm `:`.** Đường dẫn trong option cần được quote phù hợp với shell nếu chứa space/ký tự đặc biệt.

Ví dụ POSIX, khi một base riêng tên `base.jsa` đã được xây:

```bash
# Tạo dynamic archive phía trên base tự tạo:
java -XX:SharedArchiveFile=base.jsa \
  -XX:ArchiveClassesAtExit=top.jsa \
  -cp appcds-demo.jar AppCdsDemo

# Sử dụng tường minh cả base và top:
java -XX:SharedArchiveFile=base.jsa:top.jsa \
  -cp appcds-demo.jar AppCdsDemo

# Nếu dynamic archive header lưu thông tin base thích hợp:
java -XX:SharedArchiveFile=top.jsa \
  -cp appcds-demo.jar AppCdsDemo
```

**Thứ tự có ý nghĩa:** base được map trước, top bổ sung sau. Nhưng cần phân biệt “base archive” và “top archive” với “archive tĩnh/dynamic được tạo ở thời điểm nào”; hai cái tên mô tả **vai trò trong cấu trúc archive**, không phải một hệ thống hai tầng cache tùy ý hỗ trợ ghép vô hạn files.

### 6.1. Điều gì xảy ra nếu class ở cả hai tầng?

Dynamic archive chủ yếu chứa những classes chưa có trong base, vì mục tiêu tránh lặp class metadata không cần thiết. Không nên hình dung user tự hợp nhất hai file `.jsa` theo byte concatenation; **định dạng archives và metadata quan hệ giữa hai tầng thuộc VM**.

Nếu base archive thay bằng file không phù hợp, top có thể không được map; không cố giải quyết bằng cách copy/move tự do rồi bỏ qua dependency checks.

### 6.2. Cần khớp phiên bản JDK và build

CDS archives là **HotSpot VM implementation artifacts**, không phải Java SE portable serialization format. Chúng phụ thuộc vào JDK build, VM settings, class metadata layout và các thành phần khác. Đừng xây trên JDK 17.0.x của vendor A rồi mặc định dùng được trên JDK 17.0.y của vendor B; thậm chí cập nhật patch/build có thể yêu cầu tạo lại archive.

**Quy tắc deployment an toàn:** sinh archive bằng đúng JDK image/VM build sẽ dùng để chạy ứng dụng trong cùng release artifact. Khi nâng JDK, classpath hoặc application JAR, **tái tạo archives và verify**.

## 7. Java 17 lab: chương trình độc lập và JAR đúng để archive

Lưu file **`AppCdsDemo.java`**:

```java
import java.util.List;
import java.util.Locale;

public final class AppCdsDemo {
    static final class Formatter {
        String upper(String input) {
            return input.toUpperCase(Locale.ROOT);
        }
    }

    static final class Counter {
        int lengthSum(List<String> inputs) {
            int sum = 0;
            for (String input : inputs) {
                sum += input.length();
            }
            return sum;
        }
    }

    public static void main(String[] args) {
        List<String> data = List.of("alpha", "beta", "gamma");
        Formatter formatter = new Formatter();
        Counter counter = new Counter();

        System.out.println("records=" + data.size());
        System.out.println("lengthSum=" + counter.lengthSum(data));
        System.out.println("first=" + formatter.upper(data.get(0)));
        System.out.println("done");
    }
}
```

**Compile và đóng gói trên JDK 17:**

```bash
mkdir -p out
javac --release 17 -d out AppCdsDemo.java
jar --create --file appcds-demo.jar --main-class AppCdsDemo -C out .
```

Lệnh trên dùng shell POSIX, tạo file JAR từ class files đã compile. `jar` là công cụ đi kèm JDK. Một file JAR có `Main-Class` manifest phù hợp có thể chạy bằng `java -jar appcds-demo.jar`, nhưng để đối chiếu cờ CDS ở bài này ta thống nhất **`-cp appcds-demo.jar AppCdsDemo`** ở mọi pha.

**Baseline không bật logging:**

```bash
java -cp appcds-demo.jar AppCdsDemo
```

**Stdout ứng dụng chính xác trên Java 17:**

```text
records=3
lengthSum=14
first=ALPHA
done
```

`"alpha"` dài 5, `"beta"` dài 4, `"gamma"` dài 5 nên `lengthSum=14`. `Locale.ROOT` giữ quy tắc biến đổi chữ hoa ổn định, tránh phụ thuộc locale của máy. `Formatter` và `Counter` là inner classes riêng, được đóng vào JAR cùng main class.

### 7.1. Tại sao phải tạo JAR thay vì chạy `java -cp out AppCdsDemo`?

Đối với **static AppCDS archive** trên JDK 17, Oracle java launcher documentation yêu cầu classpath trong ví dụ tạo archive chứa **chỉ JAR files**, không phải non-empty directories của application classes. Dùng một JAR thống nhất cho cả trial run, dump và runtime giúp:

- Khớp classpath nhất quán.
- Tránh sai lệch vị trí classes.
- Giảm rủi ro archive bị từ chối do classes nằm trong directory.
- Có artifact rõ ràng để đưa vào CI/CD.

Lệnh `java -cp out AppCdsDemo` vẫn là một cách chạy class file bình thường ngoài quy trình AppCDS lab, nhưng **không chọn làm đường dẫn chính trong bước dump** ở đây.

### 7.2. “Chương trình chạy ra đúng stdout” chưa chứng minh AppCDS hoạt động

Cả ba kiểu run sau có thể cho **cùng 4 dòng stdout ứng dụng**:

```bash
java -Xshare:off -cp appcds-demo.jar AppCdsDemo
java -cp appcds-demo.jar AppCdsDemo
java -XX:SharedArchiveFile=app-static.jsa -cp appcds-demo.jar AppCdsDemo
```

Dòng cuối yêu cầu `app-static.jsa` đã được tạo hợp lệ. Startup thời gian thực và class load sources có thể khác, nhưng **Java semantics không được thay đổi** vì archive. Phải bật CDS/class-load logging để xác minh archives.

## 8. Lab A — tạo và dùng static archive trong Java 17

**Bước 1 — Trial run thu thập class list:**

```bash
java -Xshare:off \
  -XX:DumpLoadedClassList=app.classlist \
  -cp appcds-demo.jar AppCdsDemo
```

Nếu thành công, ứng dụng in 4 dòng như baseline và VM tạo file `app.classlist`. Đừng kỳ vọng file class list là Java code hoặc binary `.jsa`.

**Bước 2 — Dump static archive:**

```bash
java -Xshare:dump \
  -XX:SharedClassListFile=app.classlist \
  -XX:SharedArchiveFile=app-static.jsa \
  -cp appcds-demo.jar
```

Đây là bước tạo `app-static.jsa`, thường in các log/diagnostics của HotSpot; **không in 4 dòng nghiệp vụ từ `AppCdsDemo.main()`** vì đây không phải một application execution bình thường.

**Bước 3 — Chạy lại với archive:**

```bash
java -XX:SharedArchiveFile=app-static.jsa \
  -cp appcds-demo.jar AppCdsDemo
```

Dòng nghiệp vụ vẫn:

```text
records=3
lengthSum=14
first=ALPHA
done
```

**Bước 4 — Xác thực class được nạp từ archive:**

```bash
java -XX:SharedArchiveFile=app-static.jsa \
  -Xlog:cds=info,class+load=info \
  -cp appcds-demo.jar AppCdsDemo
```

Trong log HotSpot phù hợp, một class được nạp từ shared archive có thể báo nguồn tương đương **`source: shared objects file`**. Đó là chỉ dấu class đó có thể dùng dữ liệu CDS. Ngược lại, một class báo `source: file:/.../appcds-demo.jar` thường được nạp từ JAR ở lần ấy, không chứng minh toàn bộ archive thất bại.

**Không dùng một dòng log giả định làm bằng chứng** đã thấy `AppCdsDemo` chính xác từ CDS trong mọi môi trường. Hãy lọc theo tên class/GC/VM build và quan sát log thật.

### 8.1. Nếu class list ghi quá ít lớp?

Trial run chỉ nạp các classes mà chương trình thực sự động đến. Nếu một framework có lazy initialization, plugins, reflection hoặc các request paths chưa được chạy trong trial, class list có thể không chứa chúng.

Cần xây **warm-up scenario đủ đại diện**: khởi chạy components muốn tối ưu, thực hiện representative requests, sau đó shutdown sạch để dùng cùng artifact. Không giả định “mở app vài giây thì mọi class trong JAR đã được archive”.

### 8.2. Nếu archive tĩnh tạo thành công nhưng app không dùng?

Kiểm tra:

- Runtime có thật sự chỉ định `-XX:SharedArchiveFile=app-static.jsa` không?
- Có đang dùng JDK build đã tạo archive không?
- JAR có bị rebuild/thay đổi bytecode/timestamps liên quan checks không?
- Runtime `-cp` có đúng path và thứ tự so với dump không?
- Có options làm disable CDS, ví dụ `-Xshare:off`?
- Log `-Xlog:cds=debug` báo vì sao archive bị từ chối?
- Nếu lỗi do application classes ở non-empty directories, dùng packaged JAR thay vì exploded folder.

## 9. Lab B — tạo và dùng dynamic archive trong Java 17

Dynamic dump đơn giản hơn vì **không cần tạo class list trước**.

**Bước 1 — Chạy ứng dụng đồng thời yêu cầu dump khi thoát:**

```bash
java -XX:ArchiveClassesAtExit=app-dynamic.jsa \
  -cp appcds-demo.jar AppCdsDemo
```

Program stdout:

```text
records=3
lengthSum=14
first=ALPHA
done
```

Sau khi JVM hoàn tất và dynamic dump thành công, file `app-dynamic.jsa` được tạo. Việc ghi archive ở cuối ứng dụng có thể làm **lần chạy tạo archive chậm hơn**, đặc biệt lúc shutdown; đây là công việc chuẩn bị cho những lần khởi động sau, **không phải một tối ưu làm chính lần dump chạy nhanh hơn**.

**Bước 2 — Chạy lại bằng dynamic archive:**

```bash
java -XX:SharedArchiveFile=app-dynamic.jsa \
  -cp appcds-demo.jar AppCdsDemo
```

Startup được phép dùng **default static base archive + dynamic top archive**. Ứng dụng phải có cùng semantics và stdout.

**Bước 3 — Kiểm chứng CDS/class loading:**

```bash
java -XX:SharedArchiveFile=app-dynamic.jsa \
  -Xlog:cds=info,class+load=info \
  -cp appcds-demo.jar AppCdsDemo
```

Đọc log để phân biệt lớp từ base/archive với những lớp còn được nạp trực tiếp từ JAR/runtime image. Một dynamic archive không nhất thiết chứa tất cả loaded classes; còn có điều kiện về loại class loader, archive eligibility và consistency checks.

### 9.1. Dynamic archive không nhất thiết ghi được khi ứng dụng chết bất thường

`ArchiveClassesAtExit` cần JVM thực hiện quá trình dump khi kết thúc phù hợp. Nếu process bị `SIGKILL`, crash native, container hard kill hoặc OS cắt tài nguyên, không bảo đảm archive được tạo đầy đủ.

Trong CI, **kiểm tra process exit code, file tồn tại, kích thước hợp lý và lần chạy lại có log chứng minh sử dụng được archive**. Đừng để pipeline chỉ làm `test -f app-dynamic.jsa` rồi tuyên bố đủ điều kiện production.

### 9.2. Dynamic archive không phải “không cần JAR nữa”

Dù archive đã có, runtime vẫn cần đúng JAR/classpath/resources. Archive không đóng gói arbitrary resources, service provider configuration, native libraries, network endpoints, TLS materials hoặc dữ liệu ứng dụng.

Nếu build lại `appcds-demo.jar` làm thay đổi binary, archive có thể bị từ chối; phải tái tạo archive khi packaging thay đổi.

## 10. Bảng so sánh static và dynamic archive

| Tiêu chí | Static archive | Dynamic archive |
| --- | --- | --- |
| Cách tạo chủ yếu | Trial run + tạo class list + `-Xshare:dump` | Một run với `-XX:ArchiveClassesAtExit` |
| File class list trung gian | **Có** theo quy trình phổ biến | **Không cần** |
| Có chạy main khi dump? | Bước trial run **có**, bước dump **không** | Run tạo archive **có** |
| Quan hệ với base | Có thể đóng vai trò archive chính/base | Thường là top-layer phía trên base |
| Số bước chuẩn bị | Nhiều hơn | Ít hơn |
| Nội dung classes | Dựa vào danh sách đã quan sát và eligibility | Những loaded classes eligible chưa có trong base |
| Dung lượng | Có thể lớn hơn vì nhiều dữ liệu nền | Thường gọn hơn vì chỉ ghi lớp bổ sung |
| Đối tượng dùng | Controlled release/build pipeline, cần custom base | Nhanh để enable AppCDS hoặc thu từ runtime |
| Khi runtime | `-XX:SharedArchiveFile=static.jsa` | `-XX:SharedArchiveFile=dynamic.jsa` hoặc `base:dynamic` |
| Hạn chế | Classpath/JDK/archive compatibility và các rules CDS | **Cũng** phụ thuộc base/JDK/classpath, exit/dump thành công |
| Có đảm bảo tất cả classes? | **Không** | **Không** |
| Có đảm bảo startup nhanh hơn? | **Không** | **Không** |

**Câu nhớ:** Static: **class list rồi dump**. Dynamic: **chạy app rồi dump lúc kết thúc**. Cả hai: **re-use qua `SharedArchiveFile`**, kết quả Java-level không đổi.

## 11. Lệnh `jcmd VM.cds` trong Java 17

Oracle JDK 17 launcher manual nêu có thể tạo CDS archive từ **JVM đang chạy** qua `jcmd <pid> VM.cds ...`, giảm việc phải thay startup script. Đây là kỹ thuật nâng cao hữu ích khi hệ thống phức tạp.

Mẫu lệnh, thay `<pid>` bằng PID thật:

```bash
# Tạo static CDS archive từ JVM đang chạy khi môi trường hỗ trợ:
jcmd <pid> VM.cds static_dump live-static.jsa

# Tạo dynamic archive từ JVM đã bật recording khi startup:
jcmd <pid> VM.cds dynamic_dump live-dynamic.jsa
```

Để hỗ trợ **dynamic_dump** theo Oracle Java 17 documentation, JVM đích cần được khởi động với:

```bash
java -XX:+RecordDynamicDumpInfo \
  -cp appcds-demo.jar AppCdsDemo
```

Lưu ý: `AppCdsDemo` kết thúc rất nhanh nên **không phù hợp để bạn kịp lấy PID và chạy jcmd**. Ví dụ `jcmd` ở đây nhằm giải thích cú pháp cho một **service sống đủ lâu**, không cam kết bạn có thể attach vào demo ngắn.

### 11.1. `jcmd` cần điều kiện vận hành

Việc attach cần đúng process, quyền OS/owner, JDK tooling tương ứng, hỗ trợ của HotSpot và môi trường cho phép attach. Container security policies, disable-attach flag hoặc process kết thúc sớm có thể khiến lệnh không chạy được.

Không dùng `jcmd VM.cds dynamic_dump` như một lệnh Java source hoặc một API Java SE chuẩn trong ứng dụng. Đây là diagnostic command của HotSpot/JDK tools.

### 11.2. Chọn giữa dump-at-exit và `jcmd`

- Nếu kiểm soát script và có một đường warm-up + shutdown tự nhiên, `ArchiveClassesAtExit` thường đơn giản.
- Nếu app/server đang chạy với trạng thái đã warm-up và có thể attach hợp lệ, `jcmd` có thể lấy class data sau khi traffic đại diện diễn ra.
- Với static archive muốn kiểm soát, class list + explicit dump dễ đưa vào pipeline dạng deterministic steps.

Đừng gọi `jcmd` ở production dưới tải cao mà không đo resource impact/overhead và có chiến lược rollback.

## 12. Giới hạn classpath và module path trên Java 17

### 12.1. Non-empty directories trên classpath có vấn đề

Oracle Java 17 CDS documentation quy định các ràng buộc với `-classpath`/`-Xbootclasspath/a` và module path. Trong các quy trình AppCDS archive, **non-empty directories** là nguồn không tương thích; để archive application classes đáng tin, hãy **đóng gói dưới dạng JAR**.

Ví dụ **không khuyến nghị** dùng làm cách tạo static archive của bài:

```bash
java -Xshare:off -XX:DumpLoadedClassList=app.classlist \
  -cp out AppCdsDemo
```

Lệnh này có thể chạy Java source đã biên dịch ở dạng exploded class files, nhưng không đáp ứng điều kiện “classpath chỉ chứa JARs” cho static AppCDS archive ở bước dump theo Oracle example.

**Đừng hiểu lệnh `java -cp out ...` là không bao giờ chạy:** nó chạy như một Java application bình thường. Hạn chế ở đây là **CDS archiving/sharing eligibility**, không phải Java launcher cấm thư mục.

### 12.2. Module path và modular JARs

Với `--module-path`, CDS hỗ trợ những trường hợp **modular JAR** phù hợp; exploded modules/non-empty directories trên module path có thể bị hạn chế theo Oracle docs.

Không suy việc ứng dụng chạy được ở module path đồng nghĩa lớp từ module đã chắc chắn được archive. Cần kiểm chứng thực tế trong logs và đảm bảo module configuration hợp lệ.

### 12.3. Phải nhất quán classpath lúc dump và lúc runtime

Theo Oracle JDK 17 launcher docs:

- **Classpath tại lúc tạo archive** phải **bằng hoặc là prefix của runtime classpath**, theo các quy tắc CDS tương ứng.
- Thứ tự các JAR có ý nghĩa trong việc phân giải classes.
- Nếu JAR files trên classpath hoặc module path bị **sửa đổi sau khi archive tạo**, archive không được sử dụng theo kiểm tra integrity/compatibility.
- Một số startup options như `--upgrade-module-path`, `--patch-module` và `--limit-modules` có thể khiến CDS bị disable.

Đừng chỉ so tổng số JAR hoặc thư mục: cần đúng nội dung, thứ tự/path và VM config phù hợp. Một đường dẫn symbolic link hoặc relative path thay đổi cũng có thể khiến deployment khó debug; dùng reproducible packaging/paths và verify.

### 12.4. Fat JAR, nested JAR và custom class loaders

Frameworks dùng executable/fat JAR và nested library JARs có **class loader behavior khác** với ví dụ `-cp appcds-demo.jar`. Việc hỗ trợ AppCDS cho classes do custom loaders nạp có các điều kiện/giới hạn riêng; không khẳng định “mọi lớp Spring Boot nested jar đều được archive tự động như classpath flat jar”.

Khi ứng dụng dùng custom class loader:

1. Kiểm tra JDK 17 và framework phiên bản thực tế.
2. Quan sát class loading sources bằng `-Xlog:class+load=info`.
3. Xác định classes nào đến từ archive, classes nào nạp từ loader path khác.
4. Chọn static/dynamic dump quy trình phù hợp với cách loader nhận bytecode và package artifacts.
5. Không dùng archive của một build rồi thay fat jar của build khác mà không tái tạo.

## 13. Archive compatibility: JDK, classpath, cấu hình JVM

CDS không phải một định dạng “write once, run anywhere” độc lập với JVM. Một archive có thể bị từ chối trong các trường hợp:

- JDK vendor/build/patch khác với nơi tạo archive.
- Classpath JARs được rebuild, sửa hoặc sắp xếp không tương thích.
- Application JAR đã đổi path/content sau dump.
- Dynamic top được tạo phía trên base archive không còn phù hợp.
- Một số VM options/GC/platform settings ảnh hưởng archive layout hoặc eligibility.
- Trình chạy đang dùng `-Xshare:off`.
- Runtime bị giới hạn filesystem permissions/memory mapping theo môi trường.
- Archive file bị thiếu, corrupt hoặc incomplete.
- Module configuration khác hoặc options disable CDS.

**Hệ quả thực tế:** tạo archive như một **artifact gắn với JDK image và application build**. Khi thay một trong hai, hãy regenerate + rerun validation, không tái dùng mù quáng file `.jsa` cũ.

### 13.1. Không nên tự sửa file `.jsa`

File archive có cấu trúc binary nội bộ của HotSpot, không phải text template. Không dùng text editor để đổi danh sách classes trong archive hoặc nối hai archives bằng `cat`. Muốn thay classes, hãy chạy lại dump với đầu vào đúng.

### 13.2. Nếu archive không hợp lệ thì JVM xử lý thế nào?

Hành vi phụ thuộc `-Xshare` mode, loại lỗi và HotSpot configuration:

- Với chế độ tự động, VM có thể bỏ qua archive không dùng được và tiếp tục chạy không CDS trong nhiều tình huống.
- Với `-Xshare:on`, việc không thể dùng archive thường được xem là lỗi khởi động.
- Nếu đang chạy chính bước dump tạo archive, lỗi có thể khiến lệnh dump thất bại và không tạo file đầy đủ.

Không khẳng định mọi lỗi CDS đều silently fallback; **đọc exit code và `-Xlog:cds` của run thực tế**.

## 14. Verify archive thay vì đoán từ tên file

Chạy:

```bash
java -XX:SharedArchiveFile=app-dynamic.jsa \
  -Xlog:cds=info,class+load=info \
  -cp appcds-demo.jar AppCdsDemo
```

Các dấu hiệu cần tìm (tùy bản HotSpot):

- Log cho thấy archive tương ứng được mở/map thành công.
- Thông tin một hoặc nhiều classes được load từ **shared objects file**.
- Không có diagnostic thông báo archive bị bỏ qua hoặc invalid.
- Java program in chính xác 4 dòng nghiệp vụ như baseline.
- Exit code 0 và không có `LinkageError`/lỗi truy cập resources liên quan.

### 14.1. Ba tình huống và cách diễn giải

| Log quan sát | Có thể kết luận | Chưa được kết luận |
| --- | --- | --- |
| `class+load` cho một JDK class `source: shared objects file` | Có một lớp nền đã được share | Toàn bộ application classes nằm trong AppCDS |
| `class+load` cho `AppCdsDemo` `source: shared objects file` | Lớp ứng dụng ấy thực sự nạp từ archive trong run này | Mọi lớp trong mọi dependencies đều đã được archive |
| Log báo archive không tương thích hoặc bỏ qua | Run không sử dụng được archive như kỳ vọng | Java program phải sai logic hoặc chậm bao nhiêu ms |
| `-Xshare:off` và class load từ JAR | Chế độ so sánh không dùng CDS | Chứng minh archive không có giá trị trên mode khác |

### 14.2. Đường đọc log theo `GC ID` có liên quan không?

GC logs như `-Xlog:gc*=info` **không phải công cụ xác minh CDS trực tiếp**. CDS tập trung vào **class loading/startup metadata**; muốn biết classes có dùng archive, xem tags `cds` và `class+load`. GC metrics có thể hữu ích để so memory/overall performance, nhưng không thay thông tin nguồn nạp lớp.

### 14.3. `-Xlog:cds=debug` khi cần chi tiết

```bash
java -XX:SharedArchiveFile=app-dynamic.jsa \
  -Xlog:cds=debug,class+load=info \
  -cp appcds-demo.jar AppCdsDemo
```

Debug logs có thể chứa thông tin layout/mapping/checks; output khác giữa JDK builds. Trên production, giới hạn logging volume. Không ghi nguyên một bộ debug log làm “kết quả tất định” của demo vì timestamps, addresses, số classes và VM choices khác nhau.

## 15. Đo hiệu năng AppCDS đúng cách

**AppCDS là tối ưu cho startup và footprint**, không phải bằng chứng chương trình nghiệp vụ chạy nhanh hơn mọi tác vụ.

### 15.1. Định nghĩa start-up metric

Có ít nhất ba cách thường dùng:

- **Process launch → first application log:** dễ đo nhưng có thể chưa bao gồm tất cả khởi tạo.
- **Process launch → HTTP server ready:** phù hợp service thực, cần readiness probe đáng tin.
- **Process launch → first successful business response:** bao gồm lần chạm đầu tới những lazy paths và upstream dependencies.

Phải dùng **cùng định nghĩa** khi so `-Xshare:off`, default CDS và AppCDS. Nếu chạy demo đơn giản, có thể đo wall time command process, nhưng application quá nhanh khiến OS/JIT/terminal noise lớn so với chênh lệch AppCDS.

### 15.2. Ba cấu hình so sánh

```bash
# Baseline hoàn toàn không dùng CDS:
java -Xshare:off -cp appcds-demo.jar AppCdsDemo

# Dùng default CDS khi có thể:
java -Xshare:auto -cp appcds-demo.jar AppCdsDemo

# Dùng custom dynamic archive:
java -Xshare:auto \
  -XX:SharedArchiveFile=app-dynamic.jsa \
  -cp appcds-demo.jar AppCdsDemo
```

**Chỉ so tốc độ sau khi xác minh archive thứ ba thực sự được dùng.** Nếu custom archive không được dùng và JVM fallback, bạn đang so hai cấu hình CDS mặc định, không phải AppCDS.

Có thể thêm static archive vào bài đo như cấu hình thứ tư. Cố định JDK image, classpath, heap/GC settings, workload, host và warm-up page cache tương đương.

### 15.3. Warm OS page cache có thể ảnh hưởng kết quả

Một lệnh chạy lần đầu có thể đọc JARs/archive từ storage chưa cached; những lần chạy sau có page cache ấm. Nếu luôn chạy “no CDS” trước và “AppCDS” sau, kết quả có thể thiên lệch vì hệ điều hành đã cache file.

Nên xen kẽ thứ tự test runs, chạy nhiều lần, báo **median/p95/min/max**, cùng tình trạng resource contention. Chỉ ghi nhận lợi ích có thể lặp lại trên phần cứng và deployment mục tiêu.

### 15.4. Phân biệt startup time với throughput

Một web app khởi động nhanh hơn 200 ms chưa chắc throughput req/s cao hơn sau warm-up. JIT compilation, database latency, lock contention và GC behavior quyết định steady-state throughput.

Không tuyên bố AppCDS giúp C2 bỏ profiling, inline tất cả methods hoặc thay toàn bộ JIT compilation. AppCDS chủ yếu tái sử dụng class-related metadata; JIT code/compilation vẫn vận hành theo các cơ chế riêng.

### 15.5. Đo footprint nhiều JVM processes

Để đo memory benefit khi triển khai nhiều JVM instances:

1. Chạy cùng một app/JDK image ở nhiều processes trên cùng host.
2. So các cấu hình no-CDS, default-CDS, AppCDS.
3. Thu thập **PSS/USS** hoặc memory counters phân biệt shared/private pages nếu OS hỗ trợ.
4. Đo cả CPU/startup và memory sau readiness, tránh so ở các thời điểm lifecycle khác nhau.
5. Không cộng RSS mù quáng và diễn giải thành tổng physical RAM used.
6. Đưa cả disk footprint của `.jsa` vào vận hành nếu có nhiều versions.

Ở container/kubernetes, mỗi pod có namespace/storage/mount khác nhau; khả năng cùng dùng shared pages phụ thuộc việc archive có được map từ cùng page-cache-backed file/bối cảnh host hay không. Không tự suy mọi pod đều chia physical pages giống nhau.

## 16. Tích hợp AppCDS vào build và triển khai

Một workflow thực tế:

```text
1. Chọn JDK 17 image/vm build chuẩn
           │
           ▼
2. Build immutable application JARs
           │
           ▼
3. Run representative startup/warm-up paths
           │
           ▼
4A. Static: class list → Xshare:dump
4B. Dynamic: ArchiveClassesAtExit / runtime dump
           │
           ▼
5. Verify archive under SAME JDK + jars + classpath
           │
           ▼
6. Package JDK + app jars + CDS archive together
           │
           ▼
7. Deployment smoke test with -Xlog:cds / class+load
           │
           ▼
8. Measure startup/readiness, PSS/USS, rollback behavior
           │
           ▼
9. Rebuild archive whenever JDK/app packaging changes
```

### 16.1. Khi chọn static archive?

Static phù hợp khi:

- Có bước build/release được kiểm soát chặt và có thể chạy trial app.
- Muốn dùng class list như một artifact điều tra classes được load.
- Muốn chủ động đưa archive vào image trước production.
- Cần custom static base archive để phục vụ một quy trình layer khác.
- Có đủ thời gian CI để dump và verify.

Không nên coi static là mặc định nhanh hơn dynamic trong mọi app; phải benchmark. Những optimization differences cụ thể phụ thuộc JDK/collector/build.

### 16.2. Khi chọn dynamic archive?

Dynamic phù hợp khi:

- Muốn thử AppCDS nhanh mà không cần class-list step.
- Ứng dụng có warm-up path chạy tự nhiên trước shutdown.
- Muốn lưu loaded-class set của một run đại diện.
- Muốn top-layer archive bổ sung lớp riêng trên default base.
- Có thể xác minh archive-at-exit được ghi hoàn chỉnh và runtime map lại thành công.

Nếu app luôn bị kill đột ngột ở cuối workload (ví dụ container forced SIGKILL), `ArchiveClassesAtExit` không phải phương pháp chắc chắn để nhận một archive hoàn chỉnh. Chạy dedicated **archive-generation job** với graceful JVM exit.

### 16.3. Thay JDK patch version thì sao?

Khi JDK image/VM build đổi, archive có thể không tương thích. Đặt pipeline rule: **đổi JDK ⇒ recreate archive ⇒ smoke test**. Không đem một archive build trên local Windows machine chạy trên Linux server rồi mặc định nó dùng được.

### 16.4. Không dùng archive do nguồn không tin cậy tạo

Archive là binary data mà JVM dùng trong startup. Dù JVM có compatibility checks, chỉ nên sử dụng archive được tạo và phân phối qua pipeline tin cậy; không tải ngẫu nhiên `.jsa` từ bên thứ ba và buộc `-Xshare:on`. Kiểm soát permissions, artifact digests, supply-chain provenance và reproducibility như các deployment binaries khác.

## 17. Những nhầm lẫn phổ biến

1. **“AppCDS là trình biên dịch source Java thành native binary.”** Sai; nó chia sẻ metadata lớp cho HotSpot.
2. **“JAR không còn cần thiết sau khi tạo archive.”** Sai; classpath/resources và bytecode phải khớp.
3. **“CDS và AppCDS là hai hệ hoàn toàn không liên quan.”** Sai; AppCDS mở rộng CDS sang app classes.
4. **“Dynamic archive có thể tự ghi lại mọi lần một class mới được load.”** Không phải ý nghĩa của `ArchiveClassesAtExit`.
5. **“Static archive là Java `static` field snapshot.”** Sai; tên static ở đây mô tả vai trò/tạo archive.
6. **“Dynamic archive ghi giá trị hiện tại của objects trên heap khi shutdown.”** Sai; không phải heap dump.
7. **“`DumpLoadedClassList` trực tiếp tạo file `.jsa`.”** Sai; tạo class-list input cho dump.
8. **“`-Xshare:dump` chạy `main()` như bình thường.”** Sai; đây là chế độ tạo archive.
9. **“`ArchiveClassesAtExit` tăng tốc chính lần chạy tạo archive.”** Không đảm bảo; chính lần dump thường tốn thêm việc.
10. **“`SharedArchiveFile` chỉ dùng được cho static archive.”** Sai; dynamic cũng được hỗ trợ.
11. **“Để dùng dynamic top bắt buộc luôn phải viết đường dẫn default base tường minh.”** Sai; dynamic header có thể tham chiếu base.
12. **“Windows và Linux đều phân tách `base:top` bằng dấu `:`.”** Sai; Windows dùng `;`.
13. **“Có thể ghép hàng chục `.jsa` thành nhiều tầng tùy ý trong Java 17.”** Sai; không phải hệ layer tự do.
14. **“AppCDS archive luôn portable giữa mọi JDK 17 vendors.”** Sai; build/VM compatibility quan trọng.
15. **“Cứ tên file giống nhau thì JAR vẫn phù hợp dù đã rebuild.”** Sai; content/path checks có thể loại archive.
16. **“Thấy startup stdout đúng nghĩa AppCDS được dùng.”** Sai; cần class-load/CDS logs.
17. **“Một lớp nền như `java.lang.String` đến từ shared archive chứng minh mọi app classes đều shared.”** Sai.
18. **“Ứng dụng dùng CDS sẽ bỏ qua JIT và GC.”** Sai; đây là cơ chế khác.
19. **“Tất cả classes trong mọi dependency được archive ngay cả khi không load.”** Sai; trial/profile được dùng để chọn classes.
20. **“`-Xshare:on` luôn nên bật trong production.”** Sai; mode strict có thể chặn startup khi archive invalid.
21. **“Thêm `-Xshare:off` làm AppCDS hiệu quả hơn.”** Sai; nó tắt sử dụng shared archive.
22. **“ClassPath có non-empty directories không bao giờ ảnh hưởng AppCDS.”** Sai; JDK 17 có hạn chế.
23. **“Java source `new` tạo class metadata mới cho mỗi object.”** Sai; class metadata liên quan lớp, object instances là khái niệm khác.
24. **“Giảm thời gian startup nghĩa RAM process giảm tỷ lệ bằng nhau.”** Sai; hai metrics cần đo riêng.
25. **“Mỗi JVM process có RSS 1 GiB thì 10 process bắt buộc dùng 10 GiB physical RAM.”** Sai; RSS có shared pages.
26. **“Dynamic archive chỉ hoạt động nếu ứng dụng chạy ít nhất 10 phút.”** Sai; phụ thuộc loaded-class set/exit dump, không có threshold phút cố định.
27. **“Bước static dump không cần ứng dụng JAR đúng.”** Sai; classpath là input quan trọng.
28. **“`jcmd VM.cds dynamic_dump` tự hoạt động cho mọi PID.”** Sai; cần VM build/flags/attach permissions.
29. **“AppCDS thay thế readiness/liveness probes.”** Sai; startup optimization không đảm bảo health.
30. **“Nếu không thấy lợi ích benchmark một lần, tính năng chắc chắn vô ích.”** Sai; phải đo nhiều runs và đúng startup metric.
31. **“`-Xlog:gc*` là cách chính xác nhất để biết class có load từ archive.”** Sai; `cds` và `class+load` thích hợp hơn.
32. **“Mọi custom class loaders được archive như system loader không có ràng buộc.”** Sai; eligibility phụ thuộc cơ chế loader và JDK.
33. **“Default CDS archive = full AppCDS archive của mọi Java app trên máy.”** Sai; nó chủ yếu chứa lớp nền phổ biến.
34. **“Build archive trên JDK 21 rồi sử dụng nguyên trên JDK 17.”** Sai; không portable.
35. **“Chỉ cần copy một file `.jsa` sang production, bỏ JAR và JDK version.”** Sai; cần runtime dependencies/compatibility.
36. **“Memory mapping nghĩa mỗi class dùng chung cả mutable static fields và application data giữa JVM processes.”** Sai; mutable states của từng process phải tách biệt về semantics.
37. **“CDS archive là chỗ lưu secret/config application.”** Sai; không phải mục đích, và không nên biến archive thành vật mang dữ liệu nhạy cảm.
38. **“Static archive luôn to gấp 2 và chậm hơn dynamic đúng 2 lần.”** Sai; kích thước và hiệu năng tùy tập classes/dữ liệu.

## 18. Câu hỏi tự kiểm tra (có đáp án)

**Câu 1.** AppCDS khác CDS mặc định chủ yếu ở đâu?

**Đáp án:** AppCDS có thể archive lớp ứng dụng/thư viện đủ điều kiện ngoài lớp nền tảng; đều nhằm giảm startup/memory footprint nhờ class data sharing.

**Câu 2.** Có phải file `.jsa` chứa toàn bộ object heap state tại lúc JVM thoát?

**Đáp án:** Không. Đó là CDS archive cho class-related data, không phải heap dump.

**Câu 3.** Quy trình static gồm mấy pha?

**Đáp án:** Thông thường **trial run tạo class list → explicit `-Xshare:dump` sinh static archive → runtime dùng archive**.

**Câu 4.** `-XX:DumpLoadedClassList=app.classlist` tạo ra loại file gì?

**Đáp án:** Danh sách classes được load trong trial run, **không phải `.jsa` archive**.

**Câu 5.** Dynamic archive tạo bằng flag nào trên Java 17?

**Đáp án:** `-XX:ArchiveClassesAtExit=app-dynamic.jsa` trong lần chạy ứng dụng, cần dump thành công khi JVM kết thúc.

**Câu 6.** Muốn dùng lại dynamic archive thì dùng flag nào?

**Đáp án:** `-XX:SharedArchiveFile=app-dynamic.jsa`, với JVM/base/classpath tương thích.

**Câu 7.** Vì sao dynamic archive có thể nhỏ hơn static archive?

**Đáp án:** Nó thường chỉ lưu classes bổ sung so với base archive, tránh lặp metadata đã có ở default static base.

**Câu 8.** `java -Xshare:on` có phù hợp production mặc định không?

**Đáp án:** Không khuyến nghị; mode strict có thể khiến JVM thất bại khởi động khi archive không thể dùng. `auto` thường linh hoạt hơn.

**Câu 9.** Lệnh `-XX:SharedArchiveFile=base.jsa:top.jsa` viết thế nào trên Windows?

**Đáp án:** Dùng `-XX:SharedArchiveFile=base.jsa;top.jsa`, đồng thời quote option/path nếu shell cần.

**Câu 10.** `AppCdsDemo` in `lengthSum` bao nhiêu?

**Đáp án:** **14**, vì `"alpha"` dài 5, `"beta"` dài 4, `"gamma"` dài 5.

**Câu 11.** Tại sao nên dùng JAR trong lab thay vì thư mục `out/`?

**Đáp án:** Các ràng buộc Java 17 AppCDS classpath, đặc biệt static archive creation, yêu cầu packages từ JARs và loại non-empty directories trong những tình huống tương ứng.

**Câu 12.** Nếu JVM stdout giống nhau với và không AppCDS, có nghĩa archive không giúp ích?

**Đáp án:** Không. Archive là tối ưu nội bộ, Java semantics/stdout **phải giống**. Hiệu năng cần đo và việc sử dụng cần kiểm chứng từ class loading/CDS logs.

**Câu 13.** Khi cập nhật Java 17.0.x patch, nên làm gì với archive?

**Đáp án:** Regenerate và verify bằng runtime JDK mới; archive phụ thuộc build/VM compatibility.

**Câu 14.** Class load log báo `source: shared objects file` cho một class. Điều đó nói gì?

**Đáp án:** Class ấy được nạp từ shared archive trong run đó; chưa chứng minh toàn bộ app classes đều shared.

**Câu 15.** Ứng dụng chạy lần đầu với `ArchiveClassesAtExit` nhưng process bị `SIGKILL`. Có chắc file archive đầy đủ không?

**Đáp án:** Không. Việc tạo archive ở cuối JVM cần đường kết thúc/dump phù hợp, phải check artifact và startup use.

**Câu 16.** Vì sao cần warm-up đại diện khi tạo AppCDS?

**Đáp án:** Vì archive từ loaded classes của một lần chạy; những lazy paths không được thực hiện có thể chưa nạp các classes cần tối ưu.

**Câu 17.** Có thể dùng một archive JDK của Linux amd64 trên Windows arm64 không?

**Đáp án:** Không nên giả định; HotSpot binary layout/VM build/platform khác, archive không portable.

**Câu 18.** `-Xlog:cds=info,class+load=info` để làm gì?

**Đáp án:** Xem archive mapping/acceptance và nguồn nạp từng class; không chỉ xem Java program exit status.

## 19. Checklist vận hành gợi ý

- [ ] Ghi rõ **JDK vendor/build, OS/architecture**, `java -version`.
- [ ] Đóng gói ứng dụng/dependencies dưới **JARs ổn định**, không thay file sau dump.
- [ ] Quyết định **static class-list/dump** hay **dynamic dump-at-exit** theo quy trình.
- [ ] Với static: tạo `app.classlist` từ **representative trial run**.
- [ ] Với static: tạo `app-static.jsa` bằng `-Xshare:dump` và `SharedClassListFile`.
- [ ] Với dynamic: xác nhận JVM **thoát và dump thành công**.
- [ ] Nếu có custom base: lưu đúng dependency của dynamic top vào packaging.
- [ ] Khi runtime: dùng `-XX:SharedArchiveFile` và `-Xshare:auto` phù hợp.
- [ ] Kiểm chứng một số **application classes** có log nguồn `shared objects file`.
- [ ] So output nghiệp vụ và readiness của app với baseline.
- [ ] Đo startup metric bằng nhiều runs, xen kẽ test order, kiểm soát OS page cache.
- [ ] Nếu đo memory: phân biệt **PSS/USS/shared pages** với RSS cộng thô.
- [ ] Regenerate archive khi **JDK/JAR/classpath/module settings** thay đổi.
- [ ] Chuẩn bị rollback khi archive bị từ chối, đặc biệt tránh `-Xshare:on` ngoài thử nghiệm.
- [ ] Bảo vệ `.jsa` như deployment artifact, không nhận binary từ nguồn không tin cậy.

## 20. Tổng kết nhanh

```text
                   JAVA 17 APP STARTUP
                         │
                default CDS archive
                         │
             ┌───────────┴───────────┐
             │                       │
        STATIC AppCDS            DYNAMIC AppCDS
             │                       │
     trial run: class list       app run với
             │                   ArchiveClassesAtExit
      Xshare:dump                  │
             │                  exit/dump
      app-static.jsa                │
             │                app-dynamic.jsa
             │                  (base + top)
             └───────────┬───────────┘
                         │
                  RUN với SharedArchiveFile
                         │
                 -Xlog:cds,class+load
                         │
              VERIFY classes đến từ archive
                         │
            MEASURE startup và footprint
                         │
         Không đổi Java semantics hay bỏ JAR/JDK.
```

**Ghi nhớ:** **AppCDS** mở rộng CDS sang classes ứng dụng để giảm startup work và có thể chia sẻ class metadata giữa nhiều JVMs. **Static archive** được tạo từ **class list + explicit dump**, còn **dynamic archive** được tạo **khi JVM thoát** sau một runtime run và dùng một **base archive**. Trong Java 17, các cờ chính là `DumpLoadedClassList`, `SharedClassListFile`, `-Xshare:dump`, `ArchiveClassesAtExit`, `SharedArchiveFile` và `-Xlog:cds,class+load`. Đúng quy trình phải **kiểm chứng archive được nạp**, giữ **JDK/JAR/classpath tương thích** và **benchmark có kiểm soát**. Archive không phải heap dump, không thay JAR, không cache JIT native code và không đảm bảo mọi lớp/ứng dụng đều khởi động nhanh hơn.

### Tài liệu tham khảo chính thức

- [Oracle JDK 17 — `java` command, Application Class Data Sharing](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): flags, static/dynamic archive workflows, two-archive layering, classpath restrictions, diagnostic logging.
- [Oracle Java SE 17 — Class Data Sharing, JVM Guide](https://docs.oracle.com/en/java/javase/17/vm/class-data-sharing.html): cơ chế CDS, AppCDS, dynamic archive, memory sharing và default behavior.
- [dev.java — CDS and AppCDS in HotSpot](https://dev.java/learn/jvm/other-tools/cds-appcds/): lab workflow, static/dynamic tradeoffs và giới hạn JDK versions.
- [JEP 310 — Application Class-Data Sharing](https://openjdk.org/jeps/310): kiến trúc mở rộng CDS đến application classes.
- [JEP 341 — Default CDS Archives](https://openjdk.org/jeps/341): default archive đóng gói với JDK 12+.
- [JEP 350 — Dynamic CDS Archives](https://openjdk.org/jeps/350): archive từ loaded classes khi JVM exit, dynamic top layer.
- [JDK 17 — `jcmd` manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): runtime diagnostic commands, trong đó có `VM.cds` nếu build hỗ trợ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fast-startup-004">Tạo và xác nhận sử dụng CDS archive bằng log nạp lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo và xác nhận sử dụng CDS archive bằng log nạp lớp

## 1. Kết quả cần đạt

**Class Data Sharing (CDS)** là tính năng của HotSpot giúp lưu dữ liệu các lớp Java đã được xử lý trước vào **archive** để JVM ở những lần chạy sau có thể **nạp một số lớp nhanh hơn** thay vì luôn đi lại toàn bộ đường đọc JAR và xử lý metadata như từ đầu. **Application CDS (AppCDS)** mở rộng khả năng đó tới những lớp ứng dụng/thư viện phù hợp.

Bài thực hành này hướng dẫn thực hiện trọn một chuỗi:

1. Tạo một Java application nhỏ và đóng thành **JAR** để đáp ứng các điều kiện CDS dễ kiểm chứng.
2. Chạy baseline không dùng CDS nhằm nhìn đường nạp lớp từ JAR.
3. Tạo **dynamic CDS archive** với `-XX:ArchiveClassesAtExit` rồi chạy lại.
4. Dùng **`-Xlog:class+load`** để xác minh **chính lớp của ứng dụng** được load từ **`shared objects file`**, không chỉ dựa vào sự tồn tại của file `.jsa`.
5. Tạo **static CDS archive** từ `DumpLoadedClassList` và `-Xshare:dump`, rồi xác minh tương tự.
6. Phân biệt chứng cứ **archive được map**, chứng cứ **class cụ thể được load từ archive**, và chứng cứ **startup thực sự nhanh hơn**.
7. Chẩn đoán archive không được dùng, không có lớp ứng dụng trong archive, classpath/JDK không khớp và các khác biệt ở JDK 25.

**Điểm quyết định:** File `cdslab.jsa` tồn tại **chưa chứng minh** HotSpot đã sử dụng nó. Muốn xác nhận một class cụ thể, phải đọc class-loading log trong **lần chạy sử dụng archive**, tìm tên class và nguồn `source: shared objects file` tương ứng.

### Phạm vi và phiên bản

Các ví dụ Java sử dụng **Java 17+** cùng HotSpot CDS truyền thống. Các lệnh static/dynamic CDS được tài liệu hóa trong **Oracle JDK 25 `java` manual** và có thể chạy trên nhiều bản OpenJDK HotSpot 17/21/25. Một ví dụ đã được kiểm tra với **OpenJDK 21**: chương trình in đúng hai dòng; dynamic archive và static archive đều làm `CdsHello`/`Greeting` được load từ `shared objects file`. Tên lớp, hai dòng output ứng dụng và loại đường load là những điều có thể kiểm tra; **thời gian startup, số class và kích thước archive không cố định**.

JDK 25 còn có **AOT cache** (`-XX:AOTCacheOutput`, `-XX:AOTCache`) theo JEP 483 và các cải tiến mới. AOT cache có thể làm được nhiều việc hơn CDS truyền thống. Bài này ưu tiên cách **tạo/xác nhận file `.jsa`** như đề bài, sau đó giới thiệu sự phân biệt với AOT.

## 2. Hiểu đúng CDS: chia sẻ dữ liệu lớp, không phải cache mọi thứ

Khi JVM load một class, runtime phải xử lý class-file bytes và metadata: loading, verification, linking và initialization khi được yêu cầu theo JVM semantics. Một số phần của class metadata phù hợp có thể được lưu dưới dạng **preprocessed** trong CDS archive rồi được mmap/map hiệu quả để các processes sử dụng.

~~~text
Không dùng CDS:
  JAR / runtime image
        |
        v
  Load class bytes + xử lý runtime metadata
        |
        v
  JVM tiếp tục chạy application

Dùng CDS khi tương thích:
  Archived class data (.jsa)
        |
        v
  Mapped shared archive regions
        |
        v
  Class loader lấy các archived classes phù hợp
        |
        v
  JVM tiếp tục chạy application
~~~

Những lợi ích tiềm năng:

- **Startup:** giảm công việc nạp một số classes.
- **Memory footprint:** một phần các pages archive được map có thể chia sẻ giữa nhiều JVM processes dùng cùng archive phù hợp.
- **Chi phí xử lý metadata:** giảm công việc lặp lại khi archive chứa đúng các classes app sử dụng.

**Không suy rằng:**

- CDS tự động **JIT compile sẵn mọi method**.
- Mọi class đều được load từ CDS.
- CDS bỏ toàn bộ class initialization, static initializers hoặc semantic checks.
- Có archive nghĩa startup chắc chắn nhanh hơn trong mọi workload.
- CDS sửa được chi phí network, database connection, dependency injection hay ứng dụng tự chạy trong `main`.
- Mỗi process dùng archive đều có identical startup time.

Oracle JDK 25 nêu archive chứa class data có thể chiếm **nhiều disk space hơn** class bytes trong JAR/JDK image vì được lưu ở dạng tối ưu để load nhanh. Chỉ nên archive những classes thực tế hữu ích với workload.

## 3. Default CDS, AppCDS, static và dynamic archive

| Khái niệm | Đối tượng | Cách tạo/dùng thường gặp |
| --- | --- | --- |
| **Default CDS archive** | Một tập classes nền tảng có sẵn trong JDK distribution | Thường tự động được dùng trên HotSpot hỗ trợ |
| **AppCDS** | Các classes của ứng dụng và libraries đủ điều kiện | Tạo archive từ một lượt training/demo thực tế |
| **Static archive** | Archive được tạo trước từ danh sách loaded classes | `DumpLoadedClassList` → `-Xshare:dump` |
| **Dynamic archive** | Archive bổ sung classes thu được trong lần chạy app | `-XX:ArchiveClassesAtExit=...` |
| **Base/top archive** | Dynamic archive bổ sung trên một base static archive | Default base + dynamic top; log có thể hiển thị `(top)` |
| **AOT cache JDK 25** | Container lưu AOT artifacts vượt quá phạm vi CDS truyền thống | `-XX:AOTCacheOutput=...` và `-XX:AOTCache=...` |

**Static/dynamic ở đây là cách tạo archive, không phải phân loại static/dynamic class trong Java.** Các files `.jsa` không chứa “đối tượng đang sống” để phục hồi mọi state của chương trình như một snapshot application.

Trong phần lớn HotSpot JDK builds, CDS default đã được bật nếu không dùng `-Xshare:off`. Nhưng **default archive chứa JDK/core classes không tự chứng minh class ứng dụng đã được lưu**. Để xác nhận AppCDS cần tìm nguồn của **`CdsHello` hoặc `Greeting`** — không chỉ `java.lang.Object` hay `java.lang.String`.

### Hai cấp bằng chứng

~~~text
Mức 1 — archive mapping:
  [info][cds] ... archive ... opened / mapped ...
  => archive được xét/mapped, nhưng class của app có thể vẫn load từ JAR

Mức 2 — app class source:
  [info][class,load] CdsHello source: shared objects file (top)
  => class CdsHello trong lần chạy này lấy từ shared archive
~~~

Mẫu log chỉ minh họa dạng chung; tiền tố timestamp, decorator và hậu tố `(top)` thay đổi theo VM/version/archive type.

## 4. Chuẩn bị: JDK, thư mục và cách đóng JAR

**Điều kiện đầu tiên:** có JDK 17+ trên PATH, dùng **cùng một JDK build** cho create và use archive khi muốn kết quả ổn định.

~~~bash
java -version
javac -version
jar --version
~~~

Tất cả lệnh trong phần thực hành chạy trong một **thư mục lab riêng**, không phải thư mục chứa artifacts production. Không cần cài Maven/Gradle hoặc tải third-party dependencies.

### Tạo file nguồn `CdsHello.java`

~~~java
public class CdsHello {
    public static void main(String[] args) {
        System.out.println("message=" + Greeting.message("CDS"));
        System.out.println("answer=" + Greeting.answer(40));
    }
}

class Greeting {
    static String message(String name) {
        return "Hello, " + name;
    }

    static int answer(int value) {
        return value + 2;
    }
}
~~~

Ở đây **một file Java** khai báo public class `CdsHello` và package-private class `Greeting`. `javac` tạo **hai class files**; khi chạy, class loader sẽ cần load cả hai.

**Stdout chính xác của application trong một lần chạy thành công không bật VM logs:**

~~~text
message=Hello, CDS
answer=42
~~~

Giải thích: `Greeting.message("CDS")` nối thành chuỗi `Hello, CDS`; `Greeting.answer(40)` trả 42. Cả hai thao tác không phụ thuộc timing/thread scheduling.

### Biên dịch, đóng JAR và chạy thử

**Linux/macOS shell:**

~~~bash
mkdir -p build
javac -d build CdsHello.java
jar --create --file cdslab.jar --main-class CdsHello -C build .
java -cp cdslab.jar CdsHello
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force build | Out-Null
javac -d build CdsHello.java
jar --create --file cdslab.jar --main-class CdsHello -C build .
java -cp cdslab.jar CdsHello
~~~

`-C build .` yêu cầu công cụ `jar` đặt nội dung thư mục `build` vào root của JAR (gồm `CdsHello.class` và `Greeting.class`). `--main-class` ghi manifest entry nhưng ở đây có thể chạy bằng `-cp cdslab.jar CdsHello` để các lệnh tạo/dùng CDS luôn có classpath giống nhau.

**Vì sao đóng JAR thay vì chạy từ class directory?** Oracle JDK 25 CDS manual nêu khi tạo/dùng AppCDS, classpath/module path có các **restrictions đối với non-empty directories**. Dùng **một JAR** và một command line nhất quán tránh rất nhiều trường hợp “JVM có log CDS nhưng app class không archived”.

### Cảnh báo về classpath

Không đổi `-cp cdslab.jar` thành `-cp build` trong bước use và vẫn khẳng định archive tương thích. Với CDS truyền thống, **classpath của lúc tạo phải trùng hoặc là tiền tố của runtime classpath** theo điều kiện Oracle mô tả; class JAR đã bị thay đổi sau dump có thể làm archive không dùng được.

Một JAR mới đóng gói lại **có thể khiến metadata fingerprint/validation khác**. Để test archive mới, tránh biên dịch/đóng JAR lại giữa thời điểm tạo và dùng.

## 5. Baseline: log nạp lớp khi TẮT CDS

Để biết khác biệt, trước tiên chạy app **không dùng bất kỳ CDS archive** nào.

~~~bash
java -Xshare:off -Xlog:class+load=info:file=baseline-load.log -cp cdslab.jar CdsHello
~~~

Lệnh này đặt JVM option **trước** main class và chuyển **class-loading logs vào file** `baseline-load.log`; stdout trên terminal vẫn là:

~~~text
message=Hello, CDS
answer=42
~~~

Trong `baseline-load.log`, tìm `CdsHello` và `Greeting`.

**Linux/macOS:**

~~~bash
grep -E "CdsHello source:|Greeting source:" baseline-load.log
~~~

**Windows PowerShell:**

~~~powershell
Select-String -Path baseline-load.log -Pattern "CdsHello source:|Greeting source:"
~~~

**Dạng log mong đợi, không bắt buộc y hệt đường dẫn:**

~~~text
[info][class,load] CdsHello source: file:/.../cdslab.jar
[info][class,load] Greeting source: file:/.../cdslab.jar
~~~

Nếu chạy baseline với `-Xshare:off`, hai classes ứng dụng từ JAR sẽ **không có nguồn `shared objects file`**. Đường dẫn chính xác có thể là `file:/...` hoặc dạng file path khác tùy JDK/OS.

**Vì sao log nguồn lớp quan trọng?** Đây là chứng cứ về **class được load từ đâu ở runtime**, hữu ích hơn việc chỉ quan sát file archive tồn tại hay số class mà VM startup đã biết.

## 6. Luồng A: tạo dynamic CDS archive (ít bước, dễ học)

### Bước A1. Chạy “training run” để tạo archive khi JVM thoát

~~~bash
java -XX:ArchiveClassesAtExit=cdslab-dynamic.jsa -cp cdslab.jar CdsHello
~~~

Output chương trình vẫn:

~~~text
message=Hello, CDS
answer=42
~~~

Khi process thoát bình thường, HotSpot có thể ghi **`cdslab-dynamic.jsa`** (dynamic/top archive) gồm các classes phù hợp mà ứng dụng đã load, trừ những classes đã nằm trong base archive. Dynamic archive cần một **base CDS archive** (mặc định JDK thường cung cấp). Nếu base không sẵn hoặc không load được, thao tác có thể thất bại; xem diagnostic logs.

**Không tự tạo archive bằng cách rename một file JAR thành `.jsa`.** Archive là format đặc thù của HotSpot/JDK build, không phải ZIP/JAR.

### Bước A2. Xác nhận file được tạo

**Linux/macOS:**

~~~bash
ls -lh cdslab-dynamic.jsa
~~~

**Windows PowerShell:**

~~~powershell
Get-Item .\cdslab-dynamic.jsa
~~~

File tồn tại và kích thước lớn hơn 0 nghĩa thao tác tạo có artifact, **nhưng vẫn chưa đủ chứng minh use**.

### Bước A3. Chạy lại và log class loading

~~~bash
java -XX:SharedArchiveFile=cdslab-dynamic.jsa -Xlog:class+load=info:file=dynamic-load.log -cp cdslab.jar CdsHello
~~~

Application stdout là hai dòng như trước. Mở log để tìm:

~~~bash
grep -E "CdsHello source:|Greeting source:" dynamic-load.log
~~~

Trong **PowerShell** dùng:

~~~powershell
Select-String -Path dynamic-load.log -Pattern "CdsHello source:|Greeting source:"
~~~

**Dạng log đã kiểm nghiệm trên OpenJDK 21:**

~~~text
[info][class,load] CdsHello source: shared objects file (top)
[info][class,load] Greeting source: shared objects file (top)
~~~

Phần timestamp/decorators đứng trước `[info]` tùy phiên bản. **`(top)`** thường chỉ top/dynamic archive; giá trị quan trọng nhất là cả **class ứng dụng** có `source: shared objects file`.

### Bước A4. Đọc cả CDS mapping log (tùy chọn)

~~~bash
java -XX:SharedArchiveFile=cdslab-dynamic.jsa -Xlog:cds=info:file=dynamic-cds.log -cp cdslab.jar CdsHello
~~~

Log CDS có thể nói file nào được mở, mapped/validated hay vì sao một archive bị từ chối. Tuy nhiên **chỉ mapping log không chứng minh `CdsHello` được archived**; phải đối chiếu `class+load` cho chính lớp cần kiểm tra.

### Bước A5. So sánh baseline và dynamic

| Bước chạy | Log nguồn lớp `CdsHello` | Kết luận |
| --- | --- | --- |
| `-Xshare:off` | Từ `cdslab.jar` | Không dùng CDS cho class ấy |
| `-XX:ArchiveClassesAtExit=...` | Training run tạo top archive ở exit | File được tạo, chưa xác nhận use |
| `-XX:SharedArchiveFile=cdslab-dynamic.jsa` | `shared objects file (top)` khi hợp lệ | **Đã xác nhận CDS dùng cho app class này** |

**Chú ý:** nếu app chạy đúng nhưng `CdsHello` vẫn có `source: file:/.../cdslab.jar`, không được nói “AppCDS đã hoạt động cho CdsHello”. Có thể chỉ default base CDS được dùng cho JDK classes, còn app class không được archived hoặc archive bị từ chối.

## 7. Luồng B: tạo static CDS archive (classlist + dump)

Static archive minh họa cách **xác định classes từ lần chạy thử**, rồi **tạo file `.jsa` ở bước riêng**.

### Bước B1. Ghi danh sách classes đã load

~~~bash
java -Xshare:off -XX:DumpLoadedClassList=cdslab.classlist -cp cdslab.jar CdsHello
~~~

Câu lệnh chạy chương trình, quan sát class loading trong VM và ghi **`cdslab.classlist`**. Output ứng dụng vẫn hai dòng chuẩn.

Kiểm tra danh sách:

**Linux/macOS:**

~~~bash
grep -E "CdsHello|Greeting" cdslab.classlist
~~~

**Windows PowerShell:**

~~~powershell
Select-String -Path cdslab.classlist -Pattern "CdsHello|Greeting"
~~~

File có thể chứa nhiều core/library classes và directives; **không nên tự xóa mọi dòng lạ**, bởi định dạng classlist do VM ghi. Tên lớp ở classlist thường dùng JVM internal slash form đối với packaged classes (ví dụ `example/Hello`); ví dụ lab không có package nên tên đơn giản.

### Bước B2. Dump static archive

~~~bash
java -Xshare:dump -XX:SharedArchiveFile=cdslab-static.jsa -XX:SharedClassListFile=cdslab.classlist -cp cdslab.jar
~~~

**Quan trọng:**

- Đây là lệnh **dump archive**, **không phải chạy `main`** của ứng dụng.
- `-cp cdslab.jar` phải phù hợp đường cấu hình lúc training.
- Oracle JDK 25 mô tả khi dùng `-Xshare:dump`, JVM thường chạy interpreter-only để tạo output archive deterministic. Với dump rất lớn, có thể điều chỉnh `-Xmixed` nếu phiên bản hỗ trợ và không cần dump bit-for-bit deterministic.
- Nếu dump thất bại, đọc errors/`-Xlog:cds` và sửa nguyên nhân chứ không khẳng định `cdslab-static.jsa` hợp lệ từ một file còn sót.

Kiểm tra file:

~~~bash
ls -lh cdslab-static.jsa
~~~

Trên Windows PowerShell, dùng `Get-Item .\cdslab-static.jsa`.

### Bước B3. Chạy application với static archive

~~~bash
java -XX:SharedArchiveFile=cdslab-static.jsa -Xlog:class+load=info:file=static-load.log -cp cdslab.jar CdsHello
~~~

Chương trình in:

~~~text
message=Hello, CDS
answer=42
~~~

Tìm class source:

~~~bash
grep -E "CdsHello source:|Greeting source:" static-load.log
~~~

**Dạng output đã kiểm nghiệm trên OpenJDK 21:**

~~~text
[info][class,load] CdsHello source: shared objects file
[info][class,load] Greeting source: shared objects file
~~~

Với static archive, log có thể không có `(top)`. Oracle JDK 25 `java` manual dùng marker tương đương cho class trong static AppCDS: `source: shared objects file`.

### Bước B4. Phân biệt lệnh tạo và lệnh sử dụng

| Tác vụ | Cờ chính | Đầu ra |
| --- | --- | --- |
| Liệt kê loaded classes | `-XX:DumpLoadedClassList=...` | `.classlist` |
| Tạo static archive | `-Xshare:dump` + `SharedClassListFile` + `SharedArchiveFile` | `.jsa` |
| Tạo dynamic archive | `-XX:ArchiveClassesAtExit=...` | `.jsa` khi exit |
| Dùng archive | `-XX:SharedArchiveFile=...` | Ứng dụng chạy, có thể load classes từ archive |
| Chứng minh nguồn class | `-Xlog:class+load` | `source: shared objects file` cho class cụ thể |

**Không nhầm `-XX:DumpLoadedClassList` với `-XX:SharedClassListFile`**: cái đầu **ghi classlist trong trial run**, cái sau **đọc danh sách để dump archive**.

## 8. Tùy chọn `-Xshare` và chính sách khi archive không dùng được

HotSpot có các chế độ:

| Tùy chọn | Ý nghĩa | Khi dùng |
| --- | --- | --- |
| `-Xshare:off` | Không dùng CDS | Baseline/troubleshooting |
| `-Xshare:auto` | Sử dụng archive khi khả dụng, có thể fallback | Hành vi thân thiện hơn trong production |
| `-Xshare:on` | Yêu cầu sử dụng CDS; thất bại có thể làm JVM exit | **Chẩn đoán/test**, không khuyến nghị production |
| `-Xshare:dump` | Tạo static archive | Bước build archive, không chạy ứng dụng |

Oracle JVM Guide nói rõ **`-Xshare:on` dành cho testing**: khi archive không khả dụng do JDK/options thay đổi, nó có thể làm JVM **thoát ở startup** thay vì fallback. Vì vậy không bật nó mù quáng trong production chỉ vì mong startup nhanh.

### Test fail-fast có chủ đích

Khi đã tạo đúng archive và muốn xác minh archive thực sự dùng được, trong lab có thể chạy:

~~~bash
java -Xshare:on -XX:SharedArchiveFile=cdslab-dynamic.jsa -Xlog:cds=info -cp cdslab.jar CdsHello
~~~

Nếu JDK/flags/archive không phù hợp, JVM có thể báo lỗi và exit. Lệnh này **không thay thế log `class+load`** để xác nhận **lớp nào** đã được load từ archive. Dùng `-Xshare:auto` (hoặc default tùy bản JDK) trong môi trường ứng dụng thật khi ưu tiên fallback an toàn.

## 9. Phân biệt “archive mapped” và “class X được share”

Một pattern lỗi thường gặp:

~~~text
[info][cds] Opened archive ... ...
[info][class,load] java.lang.Object source: shared objects file
[info][class,load] CdsHello source: file:/.../cdslab.jar
~~~

**Kết luận đúng:** JVM có dùng CDS cho ít nhất một **JDK class**, nhưng **chưa xác nhận AppCDS cho `CdsHello`**. Có thể dynamic archive không có class đó hoặc không dùng được, trong khi default/base archive vẫn hoạt động.

Đối chứng đủ mạnh cho bài:

~~~text
(1) cdslab-dynamic.jsa tồn tại
(2) runtime command thực sự chỉ định cdslab-dynamic.jsa
(3) class+load log có:
    CdsHello source: shared objects file (top)
    Greeting source: shared objects file (top)
(4) application stdout đúng hai dòng
~~~

Đủ để nói **archive này đang cung cấp những app classes đó trong lần chạy đã quan sát**. Vẫn **chưa** đủ chứng minh lợi ích startup bằng một số ms cụ thể.

### `class+load` xuất log lúc nào?

`-Xlog:class+load=info` là **Unified Logging** cho class loading. Nếu đưa `:file=...`, log được ghi vào file; nếu bỏ output spec, logs thường đi tới output mặc định JVM.

Ví dụ hiện trực tiếp trong terminal:

~~~bash
java -XX:SharedArchiveFile=cdslab-dynamic.jsa -Xlog:class+load=info -cp cdslab.jar CdsHello
~~~

**Ở lần chạy này terminal output gồm cả class logs và hai dòng app**. Không được ghi một “stdout chính xác chỉ hai dòng” cho toàn process khi bật class logs ra console.

Khi dùng output-to-file như các bước ở trên, hai dòng application stdout vẫn riêng, còn `.log` chứa logs. Nhưng `stderr` vẫn có thể chứa warnings/errors ngoài class logs tùy JVM.

## 10. Công cụ lọc log trên Linux/macOS và Windows

### Linux/macOS: lọc bằng grep

~~~bash
grep -E "CdsHello source:|Greeting source:" dynamic-load.log
grep -E "shared objects file|source: file:" dynamic-load.log
grep -E "CdsHello source:|Greeting source:" static-load.log
~~~

### Windows PowerShell: lọc bằng Select-String

~~~powershell
Select-String -Path .\dynamic-load.log -Pattern "CdsHello source:|Greeting source:"
Select-String -Path .\static-load.log -Pattern "CdsHello source:|Greeting source:"
~~~

Để tìm tất cả class-load sources, có thể mở file log bằng editor có search thay vì in toàn bộ log ra console.

**Không dùng regex quá rộng kiểu chỉ tìm chữ “shared” rồi kết luận:** JDK core classes trong default archive sẽ gây false positive. Phải tìm **đúng class tên `CdsHello`/`Greeting`** hoặc class mục tiêu của ứng dụng thực.

### Log markers theo JVM/JDK

Các dòng class logging có thể có:

- timestamp `[0.024s]`;
- tags `[info][class,load]`;
- tên lớp với dot notation;
- `source: shared objects file`, có hoặc không `(top)`;
- path đến JAR hoặc module image;
- các thông tin khác khi bật mức `debug`/`trace`.

**Không dùng exact timestamp hay vị trí ký tự làm assertion bền vững.** So marker nguồn class sau khi xác định đúng class/JVM phiên bản.

## 11. Log `-Xlog:cds` để chẩn đoán mapping và fallback

Một câu lệnh hữu ích:

~~~bash
java -XX:SharedArchiveFile=cdslab-dynamic.jsa -Xlog:cds=info:file=cds-check.log -Xlog:class+load=info:file=class-check.log -cp cdslab.jar CdsHello
~~~

Trong HotSpot Unified Logging, các cờ `-Xlog` tương ứng có thể dùng để chỉ các bộ tag/output khác nhau; xác nhận bằng `java -Xlog:help` trên JDK hiện tại. Nếu format hoặc tùy chọn log bị từ chối, ưu tiên chạy **mỗi log trong một process test riêng** với options đơn giản như những ví dụ các bước trước.

`cds-check.log` có thể giúp phát hiện:

- Lý do archive không mở được;
- Archive/base mismatch;
- Validation/JAR path issues;
- Layout/mapping memory issues;
- Vì sao archived class metadata bị vô hiệu hóa;
- Một số flags/module options làm CDS không tương thích.

`class-check.log` chỉ rõ **classes nào nạp từ nguồn nào**.

**Không đánh đồng `source: shared objects file` với `source: jrt:/...`**. JRT module image có thể là nguồn ordinary class loading khi CDS bị tắt; nó không phải marker dùng archived class data.

## 12. Điều kiện tương thích của AppCDS archive

Oracle JDK 25 `java` manual có restrictions đáng nhớ:

1. **Classpath và module path:** CDS có các giới hạn với **non-empty directories**; hướng lab dùng **JAR** và `-cp cdslab.jar` để tránh.
2. **Classpath consistency:** classpath lúc dump phải là chính nó hoặc một prefix của classpath runtime trong các trường hợp hỗ trợ.
3. **JAR modification:** Nếu JAR trong classpath/module path thay đổi sau khi dump, archive có thể không được load.
4. **JDK build:** archive là **build-specific**; tạo bằng JDK này rồi đổi JDK runtime khác có thể không tương thích.
5. **Modules:** những tùy chọn như `--module-path`, `--module`, `--add-modules` và `--enable-native-access` cần nhất quán theo documented conditions.
6. **Một số module flags:** `--upgrade-module-path`, `--patch-module` hoặc `--limit-modules` có thể vô hiệu hóa CDS theo Oracle JDK 25.
7. **Custom class loaders:** khả năng archive/reuse class còn phụ thuộc loader behavior và phần HotSpot support; không mặc định mọi generated/dynamic class đều share được.
8. **VM flags/diagnostics:** GC/compiler/JVMTI hoặc other runtime flags có thể ảnh hưởng archive mapping/compatibility tùy version.
9. **File access/security:** archive phải readable, có đường dẫn đúng và không bị thay đổi trái phép.

**Cảnh báo an toàn:** CDS archives chứa dữ liệu dùng bởi JVM runtime. Chỉ dùng archives từ build/training pipeline tin cậy, với file permissions ngăn sửa trái phép. Oracle nêu `-XX:+VerifySharedSpaces` để kiểm tra integrity bằng CRC32 chống corruption vô ý, **không phải giải pháp bảo mật toàn diện chống kẻ tấn công sửa file**.

### Ví dụ điều kiện bị phá

~~~text
Tạo:
  java -XX:ArchiveClassesAtExit=cdslab-dynamic.jsa -cp cdslab.jar CdsHello

Sau đó:
  javac -d build CdsHello.java
  jar --create --file cdslab.jar --main-class CdsHello -C build .
  (đóng lại JAR giữa hai lần)

Dùng:
  java -XX:SharedArchiveFile=cdslab-dynamic.jsa -cp cdslab.jar CdsHello

Khả năng:
  archive mismatch/rejected hoặc app class không loaded từ archive
~~~

**Không đảm bảo** mọi JAR repack đều luôn fail, nhưng coi archive phải **recreate** sau khi thay đổi binary/JAR là chính sách đúng cho deployment đáng tin.

## 13. Các lỗi thường gặp và cách xử lý

| Dấu hiệu | Khả năng nguyên nhân | Bước kiểm tra |
| --- | --- | --- |
| Không thấy file `.jsa` | Training process không exit thành công; flag/path sai | Kiểm tra exit code, stderr và `-Xlog:cds` |
| Archive tồn tại nhưng app class từ JAR | Không chứa app class hoặc archive không được dùng | `class+load` đúng tên app class và `-Xlog:cds` |
| Chỉ `java.lang.Object` có shared source | Default CDS đã dùng, chưa chắc AppCDS | Tìm `CdsHello`/`Greeting` |
| Lỗi `Unrecognized VM option` | JVM không hỗ trợ flag/JDK sai | `java -version`, docs theo bản, VM vendor |
| “base archive not loaded” khi dump dynamic | Default/base CDS không khả dụng | Thử default archive, kiểm tra `-Xshare:off` không được bật |
| Static dump không tạo output app | Bình thường: `-Xshare:dump` chỉ dump, không chạy main | Kiểm tra file archive/log và bước runtime sau |
| Không thấy class trong classlist | Trial run không load class đó | Điều chỉnh training workload, rồi dump lại |
| CDS không dùng khi classpath là directory | CDS restrictions đối với non-empty directories | Đóng JAR, giữ classpath nhất quán |
| Mới thay JAR/version JDK | Incompatible archive | Recreate archive trên đúng deployment build |
| Chỉ chạy với `-Xshare:on` và VM exit | Strict mode fail fast | Đọc log; dùng `auto` trong production phù hợp |
| Log ghi `jrt:/...` thay vì shared | Class load bình thường từ runtime image | Kiểm tra archive/source của đúng class |
| Startup không nhanh hơn rõ | Workload nhỏ, class loading không chi phối, system noise | Benchmark nhiều process starts, đo đúng metric |
| File `.jsa` shared giữa các pods nhưng lỗi ở vài nodes | JDK/OS/architecture/flags khác | Đối chiếu build/container image và runtime configuration |
| Có `source: shared objects file (top)` nhưng output sai | Lỗi app khác; CDS không thay semantic correctness | Kiểm tra program logic, classpath và logs |

### Dọn file cũ để tránh đọc nhầm

Khi làm lại thí nghiệm, **không ghi đè ngẫu nhiên archive đã được process khác dùng**. Hãy tạo lab directory riêng hoặc đổi tên archive/trace logs; xóa file cũ chỉ khi xác định chắc chúng thuộc lab.

Trong automation, một file archive tạo từ JDK build không đúng có thể còn trên disk; một đường dẫn `SharedArchiveFile` đã được dùng không có nghĩa nó đang mapped. **Luôn kiểm tra class source log ở runtime**.

## 14. Khi tạo dynamic archive từ runtime đang chạy

Oracle JDK 25 tài liệu hóa các lựa chọn với `jcmd VM.cds` cho JVM **đang chạy**:

~~~text
jcmd <pid> VM.cds static_dump my_static_archive.jsa
jcmd <pid> VM.cds dynamic_dump my_dynamic_archive.jsa
~~~

Đây là **các lệnh mẫu**, thay `<pid>` bằng PID của JVM được phép thao tác; không copy thẳng dấu angle brackets khi chạy shell.

Theo tài liệu JDK 25, muốn gọi `VM.cds dynamic_dump` thì target JVM phải được khởi động với `-XX:+RecordDynamicDumpInfo`. `jcmd` có thêm điều kiện permissions/attach và tác động vận hành, nên không dùng vào process production chưa có quyền/quy trình phù hợp.

### Khi nào dùng `jcmd VM.cds`?

- App có startup script phức tạp, khó thêm flag training sớm.
- Muốn dump một snapshot classes đã load sau một traffic/workflow có đại diện.
- Muốn tránh xây classlist/dump ngoài application lifecycle theo cách thủ công.

**Không thay bước xác nhận cuối:** dù archive được tạo qua `jcmd`, vẫn phải **restart/new JVM với `-XX:SharedArchiveFile`**, sau đó đọc `class+load` cho class mục tiêu.

## 15. `-XX:+AutoCreateSharedArchive` trên HotSpot hiện đại

Oracle JDK 25 còn mô tả cách tiện lợi:

~~~bash
java -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=cdslab-auto.jsa -cp cdslab.jar CdsHello
~~~

Trong những phiên bản có tính năng này, nếu file chưa tồn tại thì VM có thể tạo dynamic archive ở exit. Lần chạy sau với **cùng command line** có thể dùng archive đã tạo khi tương thích.

Ưu điểm: tránh phải nhớ riêng training run và runtime command. Nhưng:

- Chính sách **recreate/replace** archive phụ thuộc version của JDK đã tạo nó; Oracle JDK 25 phân biệt archives trước và sau JDK 19.
- File archive vẫn có build/version compatibility constraints.
- Cần kiểm tra `CdsHello source: shared objects file` trong **lần chạy tiếp theo**, không chỉ lần đầu tạo.
- Dùng `-Xlog:cds` nếu archive không dùng được.

Tính năng này không phải yêu cầu của Java language, không có trên mọi vendor/JDK; với người học, luồng dynamic 2-step ở phần 6 dễ lý giải hơn.

## 16. Static vs dynamic: chọn cách nào?

| Tiêu chí | Static (`-Xshare:dump`) | Dynamic (`ArchiveClassesAtExit`) |
| --- | --- | --- |
| Bước training | Ghi `.classlist` | Chạy app để record classes và dump ở exit |
| Bước dump | Tách riêng, có `-Xshare:dump` | Thường ngay khi JVM exit |
| Base archive | Có thể tạo static riêng | Cần base static archive phù hợp |
| Độ phức tạp lệnh | Nhiều bước hơn | Ít bước hơn |
| Kích thước | Có thể bao gồm nhiều classes hơn | Thường nhỏ hơn vì không lặp classes đã trong base |
| Optimizations | Có thể hỗ trợ một số tối ưu đầy đủ hơn | Một số optimizations ít hơn static |
| Xác minh use | `SharedArchiveFile` + `class+load` | Như static, có thể thấy `(top)` |
| Khi thuận tiện | Build pipeline/classlist có kiểm soát | Trial run của app rồi dùng lại |
| Đối chứng học tập | Rõ separation trial/dump/run | Rõ archive-at-exit/run |

Theo Oracle JDK 25, **dynamic archive tuy tiện lợi nhưng hỗ trợ ít tối ưu hơn static archive** trong một số tình huống; với những tối ưu đầy đủ hơn ở JDK 25, hãy cân nhắc **AOT cache**.

## 17. CDS so với AOT Cache ở JDK 25

JDK 24/25 hỗ trợ **Ahead-of-Time Cache** (`.aot`), được giới thiệu qua **JEP 483** và mở rộng thêm AOT method profiling trên JDK 25 (**JEP 515**). AOT cache có thể lưu artifacts **bên cạnh** class data để cải thiện startup và warmup.

Phân biệt:

- **`cdslab-dynamic.jsa` / `cdslab-static.jsa`:** CDS archive truyền thống.
- **`cdslab.aot`:** AOT cache từ JDK 24+ theo các tùy chọn riêng, có thể bao gồm additional artifacts.
- `-XX:SharedArchiveFile` dùng cho CDS; `-XX:AOTCache` dùng cho AOT cache.
- `-Xlog:class+load` vẫn hữu ích để biết nguồn class; `-Xlog:aot` cung cấp những thông tin riêng khi xử lý AOT cache.

### Bài thử bổ sung chỉ dành cho JDK 25 hỗ trợ

~~~bash
java -XX:AOTCacheOutput=cdslab.aot -cp cdslab.jar CdsHello
java -XX:AOTCache=cdslab.aot -Xlog:aot=info -Xlog:class+load=info:file=aot-load.log -cp cdslab.jar CdsHello
~~~

Theo Oracle JDK 25 `java` manual, `AOTCacheOutput` có thể thực hiện training và khởi chạy assembly VM bổ sung để tạo file cache. Chính sách và tính tương thích có các điều kiện **application/JDK/OS/CPU**; không dùng câu lệnh JDK 25 này như một ví dụ bảo đảm chạy được ở Java 17.

**Mục tiêu chính bài học vẫn là CDS `.jsa`.** Không trộn hai loại artifact rồi nói `.aot` và `.jsa` tương đương, hoặc áp danh sách flags AOT cho tất cả Java versions.

## 18. Vì sao classload log chưa chứng minh startup nhanh hơn?

**Chứng cứ correctness:** `CdsHello source: shared objects file` chứng minh class cụ thể lấy từ archive.

**Chứng cứ performance:** phải **đo thời gian startup** qua nhiều lần chạy/processes với cùng workload, JVM build, CPU load và memory conditions. Điều này độc lập với correctness.

Ví dụ thí nghiệm:

~~~bash
java -Xshare:off -cp cdslab.jar CdsHello
java -XX:SharedArchiveFile=cdslab-dynamic.jsa -cp cdslab.jar CdsHello
~~~

Không thể chỉ nhìn hai lần output rồi kết luận giảm bao nhiêu ms. Với app nhỏ này, process startup, JVM initialization, JIT/class loading, filesystem cache và scheduler noise có thể lớn hơn phần lợi ích của riêng AppCDS.

### Quy trình đo đáng tin hơn

1. Giữ **cùng JDK build, flags không liên quan và JAR**.
2. Chạy nhiều **process starts** độc lập ở hai modes: baseline không CDS và AppCDS.
3. Đo wall-clock/process-start-to-ready metric phù hợp với sản phẩm, không chỉ thời gian trong `main`.
4. Tránh bật verbose logs trong những lần đo chính, vì logging thêm overhead.
5. Với server, dùng readiness/first request latency trên workload đại diện; không chỉ đo `System.nanoTime` trong `main`.
6. Ghi median, P95, variation, CPU/load và memory footprint.
7. Xác minh trước bằng class-source logs rằng AppCDS thực sự hoạt động, sau đó mới benchmark.

**Một cải thiện nhỏ hoặc không rõ rệt trên `CdsHello` không phủ nhận CDS:** ví dụ cực nhỏ không đại diện cho ứng dụng có hàng nghìn classes. Ngược lại, archive mapped không bảo đảm startup production cải thiện nếu bottleneck là I/O/network hoặc application initialization.

## 19. Những hiểu nhầm thường gặp

| Phát biểu sai | Cách hiểu đúng |
| --- | --- |
| “Có file `.jsa` là JVM đã dùng.” | File tồn tại chỉ chứng minh có artifact; cần runtime `class+load`/`cds`. |
| “Thấy `java.lang.Object` từ shared là app archived.” | Chỉ có thể là default JDK CDS; kiểm tra app class. |
| “App class loaded từ `cdslab.jar` chứng minh AppCDS.” | Ngược lại, đó là dấu hiệu class này **không** load từ archive ở lần đó. |
| “Dynamic archive và static archive được tạo bằng cùng lệnh.” | Dynamic `ArchiveClassesAtExit`, static classlist + `Xshare:dump`. |
| “`DumpLoadedClassList` tạo `.jsa`.” | Nó tạo classlist, không phải archive. |
| “`Xshare:dump` chạy `main` của application.” | Nó dump archive, không phải chạy app. |
| “CDS luôn cache JIT machine code.” | CDS truyền thống tập trung class data; JDK 25 AOT cache có phạm vi khác. |
| “CDS làm bỏ mọi class initialization.” | Không, phải giữ Java semantics của init/linking. |
| “Mọi classpath directory đều được AppCDS hỗ trợ.” | Có restrictions với non-empty directories. |
| “Đổi JDK nhưng reuse `.jsa` chắc chắn ổn.” | Archives phụ thuộc bản/build; recreate khi thay deployment. |
| “Tạo rồi repack JAR không ảnh hưởng.” | Modified JAR có thể invalidate archive. |
| “Đổi classpath thứ tự tùy ý không ảnh hưởng.” | CDS yêu cầu path consistency. |
| “`-Xshare:on` an toàn hơn `auto` cho production.” | `on` là strict/testing, có thể exit nếu archive lỗi. |
| “`-Xshare:off` chỉ tắt AppCDS, vẫn giữ base CDS.” | `off` tắt class sharing trong lần chạy đó. |
| “`source: shared objects file (top)` là JAR path.” | Là marker dynamic top archive trong HotSpot class-load log. |
| “Log nào có chữ shared đều đủ.” | Phải xem đúng class được kiểm chứng. |
| “Archived class lúc nào cũng nhanh hơn mọi loading path.” | Lợi ích phụ thuộc workload và môi trường. |
| “CDS hỗ trợ bất kỳ custom loader/generated class.” | Eligibility theo HotSpot/loader/version. |
| “AOT cache của JDK 25 chính là file `.jsa`.” | AOT dùng artifact/options riêng, có optimizations bổ sung. |
| “Startup nhanh hơn một lần chạy đủ làm kết luận.” | Phải đo nhiều lần và cùng điều kiện. |

## 20. Câu hỏi tự kiểm tra (có đáp án)

**Câu 1.** CDS là gì?  
**Đáp án:** HotSpot chia sẻ dữ liệu classes đã được xử lý trước trong archive để giảm một phần chi phí class loading và có thể giảm memory footprint giữa JVM processes.

**Câu 2.** Vì sao đóng ứng dụng thành JAR trong thí nghiệm?  
**Đáp án:** Để dùng classpath phù hợp với AppCDS restrictions, dễ tạo archive và tái lập runtime path.

**Câu 3.** Lệnh tạo dynamic archive là gì?  
**Đáp án:** `java -XX:ArchiveClassesAtExit=cdslab-dynamic.jsa -cp cdslab.jar CdsHello`.

**Câu 4.** Lệnh nào xác nhận class load source?  
**Đáp án:** Run với `-XX:SharedArchiveFile=...` và `-Xlog:class+load=info`, tìm `CdsHello source: shared objects file`.

**Câu 5.** Vì sao `java.lang.Object source: shared objects file` chưa chứng minh AppCDS?  
**Đáp án:** Đó có thể là JDK default archive; phải kiểm tra chính app classes.

**Câu 6.** Static CDS cần hai bước nào trước khi dùng?  
**Đáp án:** `DumpLoadedClassList` để ghi danh sách classes rồi `-Xshare:dump` cùng `SharedClassListFile` để tạo archive.

**Câu 7.** `-Xshare:dump` có in `message=Hello, CDS` không?  
**Đáp án:** Không, đó là bước dump, không chạy `main`. Hai dòng output thuộc các app training/use runs.

**Câu 8.** Nếu file `.jsa` tồn tại nhưng `CdsHello` vẫn `source: file:/...jar` thì sao?  
**Đáp án:** Không được tuyên bố class này dùng AppCDS; kiểm tra CDS logs/compatibility/contents.

**Câu 9.** Vì sao thay JAR sau khi archive được tạo là nguy hiểm?  
**Đáp án:** Classpath/JAR consistency validation có thể làm archive invalid; cần recreate theo build mới.

**Câu 10.** Dùng `-Xshare:on` cho production có rủi ro gì?  
**Đáp án:** Strict mode có thể khiến JVM exit nếu archive không dùng được; Oracle hướng dẫn nó cho testing.

**Câu 11.** Khi nào dùng `jcmd VM.cds`?  
**Đáp án:** Khi muốn dump từ process JVM đang chạy, với quyền attach và điều kiện flags thích hợp.

**Câu 12.** `shared objects file (top)` thường gợi ý điều gì?  
**Đáp án:** Class được load từ top/dynamic archive trên base archive trong phiên bản HotSpot tương ứng.

**Câu 13.** JDK 25 `AOTCacheOutput` có giống `ArchiveClassesAtExit` không?  
**Đáp án:** Không. AOT cache là container cho thêm optimizations, dùng file/options riêng.

**Câu 14.** Log xác nhận archived class có đủ kết luận startup giảm 20% không?  
**Đáp án:** Không. Cần benchmark end-to-end startup với nhiều runs và conditions kiểm soát.

## 21. Checklist thực hành

- [ ] `java -version` dùng đúng JDK HotSpot 17+ trong tất cả bước.
- [ ] `CdsHello.java` biên dịch thành hai class files và đóng vào `cdslab.jar`.
- [ ] Baseline `-Xshare:off` load `CdsHello` từ JAR.
- [ ] Dynamic training run tạo `cdslab-dynamic.jsa`.
- [ ] Runtime run dùng `-XX:SharedArchiveFile=cdslab-dynamic.jsa`.
- [ ] `dynamic-load.log` có **`CdsHello source: shared objects file`** và **`Greeting source: shared objects file`**.
- [ ] Static trial run tạo `cdslab.classlist` có `CdsHello` và `Greeting`.
- [ ] `-Xshare:dump` tạo `cdslab-static.jsa`.
- [ ] `static-load.log` có shared source của đúng app classes.
- [ ] Tất cả lần chạy app thành công cho `message=Hello, CDS` / `answer=42`.
- [ ] JDK/JAR/classpath/options không đổi giữa create và use.
- [ ] Nếu cần chứng minh performance, đo startup nhiều lần **không bật verbose logs** trong measurement.

Checklist này chứng minh **quy trình tạo + sử dụng + quan sát**, tránh nhầm một file archive đã xuất hiện với một tối ưu thật đang được áp dụng.

## 22. Tài liệu chính thức

- [Oracle JDK 25 — `java` Command, Application Class Data Sharing](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `SharedArchiveFile`, `DumpLoadedClassList`, `SharedClassListFile`, `ArchiveClassesAtExit`, `Xshare:dump`, `AutoCreateSharedArchive`, `jcmd VM.cds`, classpath restrictions và ví dụ log `source: shared objects file`.
- [Oracle JDK 25 — Java Virtual Machine Guide](https://docs.oracle.com/en/java/javase/25/vm/index.html): **Class Data Sharing**; giải thích `-Xshare:off`, `-Xshare:on`, `-Xshare:auto`, khi strict mode dành cho test.
- [Oracle JDK 25 — `java` Unified Logging](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html#enable-logging-with-the-jvm-unified-logging-framework): `-Xlog:class+load` và `-Xlog:cds` để quan sát.
- [OpenJDK JEP 310 — Application Class-Data Sharing](https://openjdk.org/jeps/310): đưa archived application classes vào AppCDS.
- [OpenJDK JEP 350 — Dynamic CDS Archives](https://openjdk.org/jeps/350): archive được tạo khi application thoát, dựa trên base archive.
- [OpenJDK JEP 341 — Default CDS Archives](https://openjdk.org/jeps/341): bối cảnh default CDS archive trong JDK.
- [OpenJDK JEP 483 — Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483): AOT cache và cách training/assembly/use.
- [OpenJDK JEP 515 — Ahead-of-Time Method Profiling](https://openjdk.org/jeps/515): AOT method profiles trong JDK 25, mở rộng khái niệm warmup.
- [Oracle JDK 25 — `jcmd` Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html): `VM.cds` options để dump từ JVM process phù hợp.
- [OpenJDK — `java` Application Launcher Guide](https://dev.java/learn/jvm/tools/core/java/): giải thích dynamic CDS archive và cách sử dụng base/top files.

**Kết luận:** Để chứng minh đã tận dụng **CDS/AppCDS**, cần đủ **(1) file archive tạo thành công, (2) process thực sự khởi chạy với archive tương thích, (3) `-Xlog:class+load` xác nhận chính class ứng dụng lấy từ `shared objects file`**. Dynamic CDS thường đơn giản qua `ArchiveClassesAtExit`; static CDS đi qua `DumpLoadedClassList` và `-Xshare:dump`. Thời gian startup nhanh hơn là **một khẳng định cần benchmark riêng**, không phải điều tự động được chứng minh từ log nạp lớp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fast-startup-005">AOT cache của HotSpot: học từ lần chạy thử, tạo cache và dùng lại theo phiên bản hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# AOT cache của HotSpot: học từ lần chạy thử, tạo cache và dùng lại theo phiên bản hỗ trợ

**Ahead-of-Time (AOT) cache của HotSpot** là cơ chế lưu lại một phần công việc chuẩn bị mà JVM đã học được khi chạy ứng dụng, để những lần khởi động tiếp theo có thể **tái sử dụng thông tin phù hợp** và giảm thời gian startup/warm-up. Tư tưởng chính: thay vì mỗi tiến trình Java phải hoàn toàn tự khám phá, load/link và profile tất cả cùng một đường thực thi ngay lúc phục vụ người dùng, ta cho JVM quan sát **training run**, tạo **AOT cache**, rồi chạy ứng dụng cùng phiên bản/build phù hợp với cache đó.

**Điểm phải nhớ ngay:** đây là tính năng có mốc hỗ trợ cụ thể của **HotSpot**, không phải “mọi Java 17 đều có AOT cache mới”. **JDK 24** đưa AOT class loading/linking vào AOT cache (JEP 483). **JDK 25** bổ sung **Ahead-of-Time Method Profiling** (JEP 515) và rút gọn lệnh tạo cache bằng **JEP 514**. **JDK 26** mở rộng cách cache object dùng được với nhiều GC, bao gồm ZGC theo **JEP 516**. Phải đối chiếu JVM vendor, JDK build, OS/CPU và flags của phiên bản **thực sự chạy**.

AOT cache này **không phải** một quy trình chuyển `.class` thành native executable độc lập, **không phải** thư viện `native-image` của GraalVM và **không bảo đảm** mọi method của ứng dụng đã được biên dịch thành native code sẵn trước khi production start.

## 1. Bài toán: startup và warm-up của HotSpot

Một JVM mới khởi chạy thường phải hoàn thành nhiều công việc:

- Khởi tạo các bộ phận VM và runtime.
- Đọc classpath/modules, tìm và mở các classes cần dùng.
- Parse class file, kiểm tra bytecode, load và link classes.
- Thực hiện class initialization khi chương trình thật sự cần (`<clinit>`, theo Java semantics).
- Chuẩn bị một số runtime objects và metadata.
- Chạy interpreter/C1, thu thập invocation/branch/type profiles.
- Biên dịch các hot methods bằng JIT (C1/C2) khi có đủ căn cứ.
- Hoàn thành warm-up của ứng dụng (cache kết nối, object graph, libraries).

```text
KHÔNG SỬ DỤNG AOT CACHE
Process start
     │
     ├──► Load và link classes
     ├──► Chuẩn bị một số JVM structures
     ├──► Chạy và quan sát method profiles
     ├──► JIT compile hot methods
     └──► Phục vụ workload
               │
               ▼
           steady state (nếu đạt)
```

Nếu cùng ứng dụng được khởi động lại liên tục, một phần công việc có thể được làm **lặp đi lặp lại**. AOT cache cho phép chuyển **phần thích hợp** sang giai đoạn chuẩn bị và reuse ở các lần khởi động tương thích.

### Hai chỉ số không giống nhau

| Chỉ số | Ý nghĩa | Cách đo |
| --- | --- | --- |
| **Startup latency** | Từ lúc gọi launcher tới lúc ứng dụng đạt điểm ready/first useful response | Timestamp khách quan, ready probe, trace |
| **Warm-up time** | Thời gian tới throughput/latency tương đối ổn định sau startup | Nhiều cửa sổ workload, compiler/CPU/latency telemetry |
| **Steady-state throughput** | Năng lực xử lý khi đã warm-up đủ | Load test nhiều lần, công việc tương đương |
| **CPU during warm-up** | Công sức JVM/JIT/class loading và ứng dụng sử dụng | JFR/OS CPU/profile |
| **Memory footprint** | Heap, native memory và chi phí cache mapping | Heap/RSS/NMT khi khả dụng |

AOT cache nhằm **cải thiện startup/warm-up**; **không đảm bảo** steady-state throughput luôn tăng hay mọi application đều nhanh hơn cùng một tỷ lệ.

## 2. AOT cache thực sự chứa gì?

Nội dung cache **phụ thuộc JDK release và HotSpot build**:

| Thành phần | Vai trò | Lưu ý |
| --- | --- | --- |
| **Classes đã xử lý** | Cho phép tái dùng kết quả class loading/linking được cho phép bởi JEP 483 | Không bảo đảm mọi class của cả ứng dụng đều đã vào cache |
| **Linked metadata / resolved information** | Giảm công việc link/load lặp lại phù hợp | Có consistency restrictions; phụ thuộc cấu hình |
| **Một số heap objects JVM có thể cache** | Khởi tạo trước các object structures được chọn | Không phải snapshot của toàn bộ heap production |
| **Method execution profiles (JDK 25+)** | JIT được dùng profile học từ training trước khi có đủ profile mới | Không đồng nghĩa cache lưu sẵn mọi C2 native method |
| **Những artifacts tương lai (tùy release)** | AOT framework có thể mở rộng | Luôn xác minh release notes; đừng dự đoán nội dung cache chưa được triển khai |

**Đặc biệt:** trên JDK 25, “Ahead-of-Time Method Profiling” nghĩa là **lưu profile để JIT có thể bắt đầu tối ưu sớm hơn**, không nên diễn giải rằng JEP 515 đã đóng gói toàn bộ optimized C2 machine code của chương trình vào `.aot` như một binary native độc lập.

### AOT cache không phải một heap dump

Heap dump ghi một snapshot object graph phục vụ debugging; còn AOT cache chỉ lưu artifacts được JVM coi là hợp lệ để reuse, dưới quy tắc consistency. Không lấy một `.hprof` rồi đổi tên thành `.aot`. Không kỳ vọng AOT cache giữ runtime user sessions, trạng thái kết nối TCP, credentials của một process hoặc các tài nguyên không thể portable.

### AOT cache không bỏ qua yêu cầu đúng đắn của Java

Class loading, linking, initialization, class-loader identity, JVMTI/instrumentation, reflection và dynamic loading đều có semantics phải được bảo toàn. Nếu cache không tương thích, runtime có thể từ chối dùng cache hoặc từ chối khởi động trong mode strict; **không** được sử dụng cache cũ để chạy class definitions không khớp.

## 3. Ba pha cốt lõi: record → create → production

JEP 483 (JDK 24) dùng mô hình ba pha rõ ràng:

```text
Pha A: TRAINING / RECORD
  Chạy ứng dụng với workload đại diện
         │ JVM quan sát classes/paths
         ▼
  AOT Configuration (.aotconfig)
         │
         ▼
Pha B: ASSEMBLY / CREATE
  Đọc configuration, chuẩn bị artifacts
         │ Ứng dụng main KHÔNG chạy ở pha create
         ▼
  AOT Cache (.aot)
         │
         ▼
Pha C: PRODUCTION / ON
  Chạy ứng dụng với cache tương thích
         │
         └──► Reuse artifacts, vẫn thực thi Java bình thường
```

### Vì sao tách record và create?

Recording nhận biết **ứng dụng thật làm gì** trên đường chạy đặc trưng. Cache assembly có thể xử lý thêm classes và linked objects trong một VM process riêng, không cần thực thi business workload lại như một production request.

Cách tách pha còn giúp phân chia **ngân sách tài nguyên**: training có thể gần deployment environment, còn assembly chạy trong môi trường build/CI có tài nguyên phù hợp.

**Đừng nhầm:** “AOT” không có nghĩa mọi việc xảy ra tại `javac` compile time. Training có thể **thật sự chạy Java code** cùng inputs/systems giả lập, sau đó assembly xây artifacts cho lần chạy tới.

## 4. Những cờ AOT quan trọng theo bản HotSpot

Các cờ dưới đây thuộc **HotSpot JDK có hỗ trợ AOT cache (JDK 24+)**:

| Cờ | Tác dụng |
| --- | --- |
| `-XX:AOTMode=record` | Chạy application trong giai đoạn training và ghi configuration |
| `-XX:AOTConfiguration=file.aotconfig` | Đường dẫn file cấu hình AOT được tạo/đọc |
| `-XX:AOTMode=create` | Assembly phase đọc configuration và tạo cache |
| `-XX:AOTCache=file.aot` | Dùng/định vị cache, tùy mode; trong `create` là output |
| `-XX:AOTMode=on` | Production mode yêu cầu cache phù hợp, giúp phát hiện lỗi rõ |
| `-XX:AOTMode=off` | Không dùng AOT cache trong run đang xét |
| `-XX:AOTCacheOutput=file.aot` | Từ **JDK 25**: record + assembly thuận tiện trong một invocation |
| `-XX:AOTMode=auto` | JVM tự chọn hành vi phù hợp theo options/cache khả dụng trong các versions hỗ trợ |

**Không áp các cờ này cho JDK 17:** một bản HotSpot 17 thông dụng không có JEP 483. Thử `java -XX:AOTCacheOutput=app.aot ...` trên JDK 17 có thể gây `Unrecognized VM option`; đó là lỗi **version/feature availability**, không phải code Java sai.

### Không bật nhiều chế độ đối nghịch

Khi dùng cache đầu vào `-XX:AOTCache=app.aot`, không đồng thời định nghĩa cache đầu ra `-XX:AOTCacheOutput=...` trong cùng một lần gọi nếu version docs nói hai options xung đột.

Trước khi tạo cache, xem tài liệu cho **đúng JDK release** và chạy `java -version` từ **đúng java executable**. Không dùng `-XX:+IgnoreUnrecognizedVMOptions` để “giấu” AOT flags không được hỗ trợ: ứng dụng có thể chạy mà **không thật sự dùng cache**.

## 5. Chương trình Java 17+ dùng làm demo AOT

Mã sau **biên dịch được bằng Java 17+**, còn **quy trình cache AOT phải chạy trên HotSpot JDK 24/25/26 có hỗ trợ**. Tạo file **`AotCacheDemo.java`**:

```java
public final class AotCacheDemo {
    interface Rule {
        int apply(int value);
    }

    static final class AddOne implements Rule {
        @Override
        public int apply(int value) {
            return value + 1;
        }
    }

    static final class Twice implements Rule {
        @Override
        public int apply(int value) {
            return value * 2;
        }
    }

    private static volatile long resultSink;

    static long runScenario() {
        Rule first = new AddOne();
        Rule second = new Twice();
        long total = 0;

        for (int i = 0; i < 20_000; i++) {
            total += first.apply(i % 10);
        }
        for (int i = 0; i < 8_000; i++) {
            total += second.apply(i % 4);
        }
        resultSink = total;
        return total;
    }

    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("No arguments expected");
        }
        long answer = runScenario();
        System.out.println("application: AotCacheDemo");
        System.out.println("checksum: " + answer);
        System.out.println("ready: true");
    }
}
```

### Biên dịch và đóng gói thành JAR

```bash
javac -encoding UTF-8 AotCacheDemo.java
jar --create --file app.jar --main-class AotCacheDemo AotCacheDemo.class
```

Nếu JDK `jar` không hỗ trợ syntax này, kiểm tra `jar --help` đúng version. Hai **nested classes** cũng cần có trong JAR. Lệnh `jar` phía trên chỉ thêm `AotCacheDemo.class`, **chưa thêm** các file `AotCacheDemo$Rule.class`, `AotCacheDemo$AddOne.class` và `AotCacheDemo$Twice.class` được sinh khi compile. Vì vậy để có một **JAR chạy được đầy đủ**, dùng lệnh sau thay cho dòng `jar` vừa minh họa:

```bash
jar --create --file app.jar --main-class AotCacheDemo AotCacheDemo.class AotCacheDemo$Rule.class AotCacheDemo$AddOne.class AotCacheDemo$Twice.class
```

**Lưu ý shell:** ký tự `$` trong tên class files có thể được PowerShell/bash diễn giải khác nhau. Để tránh lỗi shell, thay cả hai lệnh `jar` kể trên bằng lệnh **portable hơn trên thư mục build sạch**:

```bash
jar --create --file app.jar --main-class AotCacheDemo -C . .
```

**Chỉ sử dụng lệnh `-C . .` khi thư mục làm việc sạch chỉ chứa source/classes cần đóng gói và không có JAR sẵn**; nếu không, command có thể tự gói nhiều files ngoài ý muốn. Với bài lab, cách ổn định là đặt toàn bộ files vào thư mục riêng, dùng một class không có nested classes (xem biến thể phần dưới), hoặc thêm tên class files theo đúng quoting của shell. Trong các lệnh AOT tiếp theo, giả sử bạn đã có **`app.jar` hợp lệ, chứa các classes cần thiết**; có thể kiểm tra bằng `jar --list --file app.jar`.

### Phiên bản đóng gói được khuyến nghị cho lab

Để không phụ thuộc shell glob/quoting, dùng riêng **PowerShell**:

```powershell
jar --create --file app.jar --main-class AotCacheDemo 'AotCacheDemo.class' 'AotCacheDemo$Rule.class' 'AotCacheDemo$AddOne.class' 'AotCacheDemo$Twice.class'
```

Trên **bash/sh**, dùng:

```bash
jar --create --file app.jar --main-class AotCacheDemo 'AotCacheDemo.class' 'AotCacheDemo$Rule.class' 'AotCacheDemo$AddOne.class' 'AotCacheDemo$Twice.class'
```

Hai lệnh trên thêm đúng bốn class files; hãy dùng **một** lệnh tùy shell, không chạy tất cả các biến thể liên tiếp để ghi đè JAR rồi làm thay đổi artifacts giữa training/production.

### Chạy Java thường trước khi làm AOT

```bash
java -cp app.jar AotCacheDemo
```

**Standard output chính xác do ứng dụng in:**

```text
application: AotCacheDemo
checksum: 134000
ready: true
```

**Kiểm tra phép tính:**

- Với `i % 10` chạy 0..9, `AddOne` cho tổng mỗi 10 iterations = **55**. Có 2.000 chu kỳ trong 20.000 iterations → **110.000**.
- Với `i % 4` chạy 0..3, `Twice` cho tổng mỗi 4 iterations = **12**. Có 2.000 chu kỳ trong 8.000 iterations → **24.000**.
- Tổng = **134.000**. Kết quả số học không phụ thuộc AOT/JIT, vì cache phải bảo toàn Java semantics.

Bài lab dùng `volatile resultSink` để giữ kết quả có tác dụng quan sát theo Java Memory Model; **điều đó không buộc JIT phải compile method với một cấp cụ thể**.

## 6. JDK 24: quy trình ba pha tường minh

**Yêu cầu:** HotSpot JDK 24 build hỗ trợ JEP 483, đúng OS/architecture. Đã biên dịch và tạo `app.jar` hợp lệ ở mục 5.

### 6.1. Record

```bash
java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconfig -cp app.jar AotCacheDemo
```

Trong pha này **application thực sự chạy**, vì vậy có stdout:

```text
application: AotCacheDemo
checksum: 134000
ready: true
```

JVM đồng thời ghi những thông tin training vào `app.aotconfig`. Có thể có thêm logs nếu bật các options chẩn đoán; **không dự đoán trước** thời gian và kích thước file.

### 6.2. Create

```bash
java -XX:AOTMode=create -XX:AOTConfiguration=app.aotconfig -XX:AOTCache=app.aot -cp app.jar
```

Pha **create** dùng AOT configuration để sinh `app.aot`. **Không gọi `main` ứng dụng** ở pha này; không mong thấy ba dòng stdout tính toán. Khi build tạo cache lỗi, phải sửa vấn đề tương thích/đầu vào, **không** sử dụng file output chưa được xác minh.

### 6.3. Run với cache

```bash
java -XX:AOTMode=on -XX:AOTCache=app.aot -cp app.jar AotCacheDemo
```

Application vẫn phải in:

```text
application: AotCacheDemo
checksum: 134000
ready: true
```

**Điều cần phân biệt:** output đúng chứng minh logic ứng dụng **không đổi**, **không tự chứng minh** cache đã được sử dụng. `AOTMode=on` hữu ích để buộc VM kiểm tra khả năng load cache; cần thêm startup logs để xác minh artifacts được dùng.

### 6.4. Không thay JAR giữa ba pha

Sau recording, **giữ nguyên `app.jar`** cho assembly và production test. Recompile, repackage hoặc đổi classpath dù source “giống nhau” có thể đổi file timestamps/bytes và phá những điều kiện cache consistency. Nếu ứng dụng đổi build, **xóa cache cũ và tạo lại** trên artifacts cuối cùng.

## 7. JDK 25+: cách ngắn gọn từ training tới cache

**JEP 514** cho phép dùng `-XX:AOTCacheOutput=...` để record và assemble trong một lời gọi thuận tiện:

```bash
java -XX:AOTCacheOutput=app.aot -cp app.jar AotCacheDemo
```

JVM chạy training workload của ứng dụng và có thể khởi chạy **một JVM thứ hai** để tạo cache khi pha record kết thúc. Vì thế việc “một lệnh shell” **không đồng nghĩa một JVM process duy nhất** hoặc không tốn bộ nhớ bổ sung. Kết quả `app.aot` chỉ được xem là hợp lệ khi quá trình hoàn tất thành công.

Lần chạy reuse:

```bash
java -XX:AOTMode=on -XX:AOTCache=app.aot -cp app.jar AotCacheDemo
```

Đây là quy trình hai command đơn giản thích hợp cho lab/training trong build pipeline khi runtime hỗ trợ.

### Khi vẫn nên dùng ba pha?

- Muốn tách **training workload** với assembly job.
- Training container có CPU/memory thấp, nhưng assembly cần resources cao hơn.
- Muốn lưu/configure `.aotconfig` độc lập để kiểm tra/trace.
- Cần kiểm soát rõ build vs production artifacts.
- Muốn giảm rủi ro memory pressure do JVM assembly được launch cùng command.

**Đặc biệt trong container:** một số trường hợp JVM assembly dùng memory options tương đương training. Đừng chọn `-Xmx` sát giới hạn container rồi ngạc nhiên khi training + assembly bị OOM. Tách ba pha hoặc cấp memory budget hợp lý.

### JEP 515: lưu method profiles từ training

Từ JDK 25, AOT cache có thể lưu **method execution profiles** để JIT không phải chờ profile phát triển từ 0 trong các subsequent runs. Kết quả có thể là:

- Hot methods được biết sớm hơn.
- C1/C2 có thông tin type/branch/call activity từ training.
- Warm-up ổn định nhanh hơn trên workload phù hợp.
- Một số extra startup/warm-up CPU work được giảm hoặc chuyển thời điểm.

**Không đảm bảo mọi method hot đã được compile sẵn**; JIT vẫn phải chạy và thích nghi nếu production traffic differs. Dữ liệu profile không “khóa chặt” runtime type choices; HotSpot phải bảo toàn Java semantics qua guards/fallback/deoptimization nếu profile khác thực tế.

## 8. JDK 26: AOT object cache và các GC

Một hạn chế đáng chú ý ở mốc đầu của AOT cache là hỗ trợ **GC-specific AOT object caching**. Theo **JEP 516 (JDK 26)**, HotSpot mở rộng AOT object caching để có thể dùng với **các garbage collectors được hỗ trợ**, trong đó có ZGC, thay vì những giới hạn đã có ở JDK 24–25.

**Điều cần đọc cẩn thận:**

- Tính năng chính là cải thiện portability của **AOT object cache theo collector** trong phạm vi implementation; **không có nghĩa `.aot` của JDK 25 tự nhiên dùng được với JDK 26**.
- Cache vẫn gắn với **một JDK release, application và OS/CPU**; nâng JDK thì **regenerate**.
- Không vì JEP 516 mà suy mọi collector trong mọi vendor build và architecture đều được hỗ trợ.
- Nếu startup báo incompatibility, **đọc nguyên nhân**, đừng bật `IgnoreUnrecognizedVMOptions`.

### Bảng mốc cần nhớ

| JDK | Điểm AOT cache liên quan | Điều cần tránh suy diễn |
| --- | --- | --- |
| 17 | **Không có quy trình JEP 483 mới** | Không dùng `AOTMode`/`AOTCacheOutput` của JDK 24–25 |
| 24 | JEP 483, class loading/linking cache; record/create/on rõ ràng | Không có cờ `AOTCacheOutput` tiện lợi của JEP 514 |
| 25 | JEP 514 và JEP 515, CLI gọn + method profiles | Method profiles không có nghĩa cache đã chứa mọi native methods |
| 26 | JEP 516, AOT object caching with any GC trong phạm vi hỗ trợ | Cache của JDK khác **không** tự hợp lệ |
| Mới hơn | Xem docs/version và release notes chính xác | Không áp bảng cũ như một danh mục feature vĩnh viễn |

Thông tin ở đây gắn **mốc upstream OpenJDK/HotSpot**. Một distribution có thể có backports/patches; xác minh **binary thật** thay vì chỉ so version major bằng suy đoán.

## 9. Cache có phụ thuộc application và môi trường không?

**Có.** Oracle JDK `java` docs ghi AOT cache phụ thuộc các chiều trọng yếu:

- **Application identity**: classpath/JAR/module path và artifacts phù hợp.
- **JDK release/build**: thay runtime cần tái tạo cache.
- **OS/CPU architecture**: không copy cache giữa những nền tảng khác rồi mặc định chạy.
- **Những JVM options liên quan**: collectors, instrumentation/JVMTI, class loading/linking flags hoặc những options khác có thể không tương thích.
- **Training context**: classpath, class loaders, class initialization/linking behavior và environment phải hợp lệ khi production reuse.

### Checklist consistency

- [ ] Cùng JDK release và binary/runtime thích hợp.
- [ ] Cùng OS/CPU architecture/ABI theo tài liệu version.
- [ ] JAR/classpath artifacts không bị rebuild sau recording.
- [ ] File timestamps quan trọng được bảo toàn.
- [ ] Classpath dùng paths/JARs theo hướng dẫn release, tránh tùy tiện wildcard/directory/nested JARs.
- [ ] Runtime classpath có quan hệ tương thích với training classpath theo docs.
- [ ] Những Java agents/JVMTI class-file transformations không làm phá constraints.
- [ ] JDK options khi train/create/run không có incompatibility.
- [ ] Training cover những startup paths và methods sẽ được dùng thật.
- [ ] AOT cache được phát hành cùng application artifact, không tái dùng chéo builds.

**Đừng đọc checklist như cách bảo đảm 100% cache luôn hoạt động:** mỗi release có các ràng buộc chi tiết hơn; hãy thử mode strict và đọc logs.

### Classpath JAR thay đổi rất dễ làm cache stale

Đổi một transitive dependency, thêm plugin, thay manifest, đổi fat JAR layout hoặc repackaging khiến application identity khác. Một build pipeline tốt nên coi `app.aot` là **derived artifact** của **chính app.jar + JDK + target platform**, không phải một file cấu hình vĩnh viễn.

## 10. Training workload phải đại diện nhưng không gây side effect nguy hiểm

AOT cache học từ những class/method paths được exercise trong **training run**. Nếu training chỉ gọi `--version` hoặc in banner, cache có thể không cover:

- HTTP server startup.
- Serialization/deserialization code paths.
- JSON validation và route matching.
- Dependency injection wiring.
- ORM initializations.
- Hot request processing methods.
- Các branches phụ thuộc config/feature flags.

Nhưng cho training toàn bộ integration tests không chọn lọc cũng có thể làm cache lưu **classes của test frameworks**, tạo artifacts không liên quan production.

### Thiết kế training tốt

1. Xác định **mốc ready/startup** và workload hot nhất.
2. Giữ **classpath/JAR production**.
3. Mock database/external services khi cần, nhưng vẫn exercise những code paths tương tự.
4. Không phát sinh giao dịch nghiệp vụ thật hoặc gửi email/charge thẻ trong training.
5. Chạy smoke scenarios tiêu biểu; tránh cả “chỉ hello world” lẫn “stress test tất cả”.
6. Thu thông tin classes/methods đã đi qua.
7. Đóng ứng dụng và cho JVM hoàn thành cache creation thành công.
8. Dùng đúng cache trên staging rồi đo startup/warm-up dưới tải đại diện.
9. Regenerate khi application dependencies hoặc JDK thay đổi.

### Training không phải “đoán” toàn bộ production

Một ứng dụng có feature flags, dynamic class loading hoặc plugin ecosystem có thể dùng classes hoàn toàn mới tại production. AOT cache không bắt mọi class/method tương lai phải nằm trong cache; JVM vẫn có đường xử lý runtime hợp lệ cho những artifacts không cached khi điều kiện được hỗ trợ.

**Không biến class loading lúc runtime thành sai logic** chỉ vì training chưa từng cover nó. Kết quả có thể là **không hưởng lợi AOT** ở đường đó; nếu dynamic loading/JVMTI options phá consistency, startup strict mode có thể từ chối cache.

## 11. Kiểm chứng thực sự đang dùng AOT cache

**Output ứng dụng giống nhau** là điều bắt buộc về correctness, nhưng chưa đủ chứng minh cache được nạp.

Trên HotSpot với tính năng AOT logging hỗ trợ, thử:

```bash
java -XX:AOTMode=on -XX:AOTCache=app.aot -Xlog:aot,class+path=info -cp app.jar AotCacheDemo
```

Một số bản có thể báo những messages liên quan loaded/linked classes được reuse. **Đọc log của JVM thật**; không hard-code một mẫu stdout log cố định cho mọi build.

### Ba phép đối chiếu

| Run | Lệnh | Câu hỏi |
| --- | --- | --- |
| **No cache** | `java -XX:AOTMode=off -cp app.jar AotCacheDemo` | Baseline chạy không dùng AOT artifacts |
| **Cache, fail-fast** | `java -XX:AOTMode=on -XX:AOTCache=app.aot -cp app.jar AotCacheDemo` | Cache hợp lệ và load được? |
| **Cache, logging** | `java -XX:AOTMode=on -XX:AOTCache=app.aot -Xlog:aot,class+path=info -cp app.jar AotCacheDemo` | Có evidence JVM reuse artifacts? |

`-XX:AOTMode=on` hữu ích khi cần strict startup failure nếu cache thiếu/incompatible, thay vì để default automatic behavior có thể chuyển sang chạy không cache. Log/option syntax phải được kiểm tra trên đúng release.

### Debug khi log không nói rõ

- Dùng `java -version` xác nhận đúng JDK, VM name.
- Dùng `java -Xlog:help` để xem tags của chính VM.
- Xem errors/warnings khi tạo cache.
- Kiểm tra tồn tại, quyền đọc và kích thước file `.aot`.
- Kiểm tra cache format/magic bằng công cụ được vendor hỗ trợ; **không chỉnh binary thủ công**.
- Đảm bảo training/create/production dùng đúng app JAR/dependencies.
- Không để build pipeline “tự bỏ AOT flags nếu lỗi” rồi gắn nhãn run là “cache-enabled”.

## 12. So sánh startup/warm-up cho công bằng

AOT thường được kỳ vọng giảm startup overhead, nhưng phép đo dễ nhiễu. Để so sánh:

- **Cùng** JDK vendor/build, CPU/memory quota, GC, container image.
- **Cùng** JAR/app arguments, classpath, config và external dependencies.
- Chọn **no-cache baseline** bằng `AOTMode=off` thay vì dùng JDK version khác.
- Chạy **cache-enabled** với `AOTMode=on` xác nhận hợp lệ.
- Định nghĩa metric: JVM process launch → app ready, hoặc first useful request.
- Đo **nhiều cold process launches**. Tránh so một lần warm file-system cache với một lần cold disk cache.
- Giữ JIT warm-up và startup work tương đương.
- Tách **elapsed startup**, **memory footprint**, **warm-up CPU** và **steady-state throughput**.
- Báo median/percentiles và độ phân tán, không chỉ lần tốt nhất.

### Không báo kết quả đo phỏng đoán

Một vài benchmark chính thức của các nhóm OpenJDK/Oracle có thể thể hiện mức cải thiện đáng kể trên workload cụ thể. **Không thể áp con số đó trực tiếp** cho AotCacheDemo hoặc một web service bất kỳ.

Ví dụ báo cáo chuẩn:

```text
Runtime: HotSpot JDK <version + build đã đo>
Application artifact: app.jar <digest đã xác minh>
No-cache command: ...
AOT-cache command: ...
Run count: <số runs thực>
Ready-time median A/B: <số liệu đo>
p95 startup A/B: <số liệu đo>
CPU / RSS / heap A/B: <số liệu đo>
Compiler/JFR differences: <observed evidence>
Conclusion: chỉ giới hạn trong môi trường/workload đã đo
```

Đây là **mẫu phải điền bằng số đo thực**, không phải kết quả cố định. Không đưa các trường mô tả này vào stdout kỳ vọng của chương trình Java.

## 13. AOT cache khác CDS, AppCDS và Dynamic CDS như thế nào?

**Class Data Sharing (CDS)** tồn tại trước AOT cache của JEP 483 và giúp reuse class metadata đã lưu qua archive. Ví dụ HotSpot có các flags lịch sử:

```text
-Xshare:on
-XX:SharedArchiveFile=archive.jsa
-XX:ArchiveClassesAtExit=archive.jsa
```

Đây là **ví dụ lựa chọn CDS của HotSpot theo phiên bản hỗ trợ**, không phải lệnh tạo JEP 483 AOT cache. File `.jsa` và `.aot` có workflow/semantics khác theo release.

| Cơ chế | Mục tiêu chính | Phân biệt quan trọng |
| --- | --- | --- |
| CDS/AppCDS | Chia sẻ một phần class metadata/archive để giảm startup/memory | Có thể dùng trên HotSpot trước JDK 24 |
| Dynamic CDS archive | Tạo archive classes trong những cách chạy hỗ trợ | Không đồng nhất `.jsa` với `.aot` |
| JEP 483 AOT cache | Framework mới reuse load/link classes/selected objects | Có training/config/create phases |
| JEP 515 method profiling | Tái dùng execution profiles từ training | Đưa dữ liệu cho JIT; không phải native executable |
| GraalVM Native Image | Build executable native với runtime đặc thù | Không phải `java -XX:AOTCache=...` của HotSpot |

**JDK 17:** có thể học CDS và performance tuning cổ điển, nhưng không đặt tên nhầm cơ chế đó thành **“JEP 483 AOT cache”**. Muốn thực nghiệm tính năng của mục này, chuẩn bị đúng HotSpot JDK 24+.

## 14. AOT cache không đồng nghĩa ahead-of-time native compilation cũ

Trước JEP 483, HotSpot có những thí nghiệm AOT compilation khác. **JEP 295** giới thiệu experimental ahead-of-time compilation (`jaotc`), rồi **JEP 410** loại bỏ AOT compiler experimental và `jaotc` khỏi JDK 17.

Những mốc này thường bị nhập nhằng:

- **JEP 295/410:** liên quan **experimental AOT compiler/jaotc** đã bị loại khỏi upstream JDK 17.
- **JEP 483:** AOT cache class loading/linking được giới thiệu ở JDK 24.
- **JEP 515:** bổ sung method profile caching từ JDK 25.
- **GraalVM Native Image:** hệ thống native compilation khác, có builder và runtime khác.

Vì vậy câu “JDK 17 đã bỏ AOT, nên Java 25 không còn AOT cache” là **sai do nhầm hai tính năng khác nhau**. Ngược lại, “Java 25 có AOT cache nên `jaotc` đã trở lại” cũng không đúng.

## 15. Startup optimizations không làm thay đổi luật class initialization

AOT class linking không được phép tự ý thay observable Java semantics về:

- Class initialization khi active use yêu cầu.
- Thứ tự `<clinit>` phù hợp.
- Exceptions từ static initializer.
- Runtime/linkage errors.
- Thread synchronization của class initialization.
- Reflection/dynamic loading theo những điều kiện được hỗ trợ.

Ví dụ:

```java
final class InitDemo {
    static {
        System.out.println("class initialization");
    }

    static void run() {
        System.out.println("run");
    }
}
```

Đây là class snippet, **chưa có `main`**. Tối ưu AOT không có quyền in chuỗi “class initialization” ở một thời điểm tùy ý làm thay đổi quan sát bắt buộc của Java code. Việc chuẩn bị artifact trước thời gian chạy chỉ được thực hiện trong những ranh giới compatibility mà HotSpot đảm bảo.

### Phân biệt load/link với initialize

Một class có thể được **load/link** trước khi thực sự **initialize**. AOT cache giúp chuẩn bị những phần công việc phù hợp nhưng không được tự đồng nhất “đã ở cache” với “static initializers đã chạy cho production process”.

**Đừng dùng AOT cache để né bugs trong static initializer.** Nếu một class khởi tạo sai/lỗi trong application, cache không làm lỗi logic trở nên hợp lệ.

## 16. Dynamic classes, agents và instrumentation

Một số ứng dụng load classes hoặc thay class bytes theo runtime:

- Java agents/bytecode instrumentation.
- Application server class loaders.
- Dependency injection proxies.
- Dynamic proxies / hidden classes.
- Hot-reload/redefinition tools.
- Custom class loaders.
- Plugin frameworks.

Điều đó không có nghĩa tất cả ứng dụng như trên không thể hưởng AOT. Nhưng **một số JVMTI operations**, đặc biệt agent thay bootstrap/system classloader search path, có thể không tương thích với cache đã link trước.

### Cần báo gì khi có agent?

1. Agent nào chạy trong training và production?
2. Có transform class bytes không?
3. Có add vào bootstrap/system class loader search path không?
4. Agent version/options có thay đổi?
5. `AOTMode=on` có chấp nhận config này không?
6. Những classes dynamic có được exercise và reuse không?
7. Nếu cache bị từ chối, có thể tắt optimization hoặc thay training/config theo docs mà vẫn giữ correctness không?

**Không** giải quyết bằng việc bắt buộc tắt monitoring/security agents ở production nếu chưa đánh giá yêu cầu vận hành. Hiệu năng tối ưu phải phù hợp instrumentation và bảo mật thực tế.

## 17. Triệu chứng và cách chẩn đoán

| Triệu chứng | Nguyên nhân có thể | Kiểm tra |
| --- | --- | --- |
| `Unrecognized VM option 'AOTMode'` | JDK quá cũ hoặc JVM khác HotSpot | `java -version`, release/build docs |
| `AOTCacheOutput` không nhận ở JDK 24 | JEP 514 là JDK 25 feature | Dùng record/create ở JDK 24 |
| `app.aot` không tồn tại | Training/assembly chưa hoàn tất, sai path/permission | Exit code, filenames, logs |
| Cache không load ở mode `on` | JDK/app/OS/CPU/flags không tương thích | Đọc error details, so artifacts |
| Chạy được nhưng không nhanh hơn | Training shallow, app quá nhỏ hoặc bottleneck không nằm ở load/link/profile | JFR/class logs và benchmark nhiều lần |
| Cùng tên JAR nhưng cache stale | JAR rebuilt, dependency đổi, timestamps/bytes khác | File digests và build pipeline |
| AOT assembly OOM | JVM thứ hai tiêu thụ memory, container quota thấp | Memory limit, tách pha |
| Chạy AOT trên ZGC JDK 25 gặp giới hạn | JEP 516 xuất hiện từ JDK 26 | Kiểm tra release và collector support |
| App output khác khi dùng cache | Vấn đề correctness/config hoặc ứng dụng có non-determinism | Reproduce, so flags/config, báo bug nếu cần |
| `-Xlog:aot` không tạo event mong đợi | Tag/level khác, cache không reuse, logging config | `-Xlog:help`, startup flags, strict mode |
| First request vẫn chậm | DB/network/framework init không cover, JIT còn warm-up | Request tracing/JFR, profile training |
| RAM/RSS tăng | Cache mapping/assembly overhead, config/bộ nhớ runtime | OS metrics, NMT, compare same windows |

### Lỗi không phải luôn “JVM không hỗ trợ”

Một flag **được nhận diện** nhưng VM từ chối vì **class path mismatch** khác với flag **không được build vào JVM**. Ghi đúng error, phân loại **availability vs compatibility vs effectiveness** trước khi chọn cách xử lý.

### Mode auto và sự im lặng nguy hiểm

`AOTMode=auto` có thể chạy ứng dụng mà **không dùng cache** nếu một cache không được nạp trong những trường hợp được phép. Trong CI/benchmark muốn chứng minh cache hoạt động, hãy dùng mode **on** và đọc logs. **Không kết luận “có `-XX:AOTCache=...` trên command line thì cache chắc chắn đã được dùng”.**

## 18. Lỗi vận hành khi đóng gói cache

### Không gắn `.aot` vào container image khác build

Một cache tạo với JDK build A rồi copy vào image B có JDK khác có thể không còn hợp lệ. Cách đúng: xây **application artifact + AOT cache** theo target release/platform cụ thể, lưu manifest metadata và phát hành chúng có version cùng nhau.

### Không sửa JAR sau training

Dù chỉ thay timestamp hoặc repackage, JVM có thể coi classpath artifacts không khớp. Trong release pipeline:

```text
Compile source
    ↓
Package immutable app.jar
    ↓
Train → assemble app.aot (same immutable app.jar)
    ↓
Validate strict AOT on staging
    ↓
Publish app.jar + app.aot + manifest
    ↓
Deploy both with matching JDK runtime image
```

**Mỗi lần build source/dependencies/JDK** nên regenerate cache. Nếu target có nhiều OS/CPU architectures, tạo/kiểm tra artifacts phù hợp từng target, thay vì suy `app.aot` là binary portable.

### AOT cache là artifact phải bảo vệ

File cache chứa thông tin về classes/metadata/runtime artifacts, không mặc nhiên là public. Cần file permissions, checksum/digest, provenance, artifact signing/policy khi tổ chức yêu cầu và kiểm soát người có quyền thay nó.

Không upload `.aot` hoặc training logs chứa nội dung nhạy cảm lên kho công khai chỉ để debug. Khi logging/cache config có user information, áp dụng retention/security policy tương ứng.

## 19. Training trong test vs production

### Training bằng integration/smoke tests

Ưu điểm:

- Có thể thực hiện trong CI không truy cập production database.
- Dễ lặp lại, ghi artifact và đặt quality gates.
- Có thể test một số startup/request paths đại diện.

Giới hạn:

- Test-only classes làm cache to mà không giúp production.
- Mocked paths không cover actual production hot methods.
- Synthetic traffic khác phân bố receiver types/requests.
- Một số integrations khó tái tạo đúng cách.

### Training dưới production-like workload

Ưu điểm:

- Có thể quan sát class/method paths sát thực tế.
- Phù hợp ứng dụng có dynamic initialization.

Giới hạn:

- Có nguy cơ business side effects và tài nguyên nhạy cảm.
- Khó cố định classpath/environment trong rolling deployment.
- Cache output phải được chuyển sang build/deploy flow an toàn.
- Time windows có thể bỏ sót hot paths của những thời điểm khác.

**Khuyến nghị:** ưu tiên isolated, repeatable training phản ánh production; nếu chỉ training ở production thì cần policy chặt chẽ về side effects, dữ liệu, bảo mật và artifact promotion. Không nên nghĩ training là “cứ chạy app một lần bất kỳ”.

## 20. Thí nghiệm A/B: cache là biến duy nhất

Một thí nghiệm đúng nên so:

**A — no cache:**

```bash
java -XX:AOTMode=off -cp app.jar AotCacheDemo
```

**B — strict AOT cache:**

```bash
java -XX:AOTMode=on -XX:AOTCache=app.aot -cp app.jar AotCacheDemo
```

Cả hai command dùng **cùng JDK**, app JAR, OS/CPU, GC/heap, input và môi trường. Trong ví dụ nhỏ này, **stdout phải giống** ở cả A và B nếu được hỗ trợ.

### Thu số liệu có ý nghĩa

- Process elapsed startup tới dòng `ready: true`, nếu dùng marker đó làm proxy.
- Thời gian tới first request ready trong ứng dụng server thực.
- CPU của compiler/class loading trong warm-up.
- Number of class loads, JFR events, compilation events.
- Heap committed/used, process RSS, memory maps của cache.
- Number of runs, median/p95 và variability.
- Error rates và correctness output.
- Cache artifact size vs savings.

**Không dùng một lần chạy** với `time java ...` để tuyên bố “AOT giúp giảm 40% startup”; filesystem cache, OS scheduler, JIT, entropy và process launch overhead đều thay đổi.

### Hiệu năng có thể tốt hơn ở metric này và xấu ở metric khác

Caching có thể cải thiện startup nhưng tăng storage footprint hoặc memory mapping overhead. Cache assembly cũng tiêu thụ CPU/RAM trong build stage. Nếu application chạy lâu liên tục, lợi ích startup có thể không phải KPI chính.

**Sự lựa chọn phụ thuộc use case:** batch jobs, CLI, serverless cold start và autoscaled services thường quan tâm startup; hệ thống uptime nhiều tháng có thể coi steady-state throughput/latency quan trọng hơn.

## 21. Kinh nghiệm gỡ lỗi từ output chương trình

Cần chia ít nhất ba loại output:

1. **Java application stdout:** các dòng `application`, `checksum`, `ready`; trong demo chúng có expected values cố định.
2. **JVM AOT/GC/class loading logs:** format/time/files thay đổi theo JDK và flags; **không viết expected stdout cố định** cho chúng.
3. **Timing/benchmark metrics:** thuộc môi trường đo; không phải hợp đồng ngôn ngữ Java.

Một báo cáo đúng có thể viết:

```text
Functional QA: PASS
  application=AotCacheDemo
  checksum=134000
  ready=true

AOT startup validation: PASS/FAIL theo strict mode + log thực tế
  JVM vendor/version/build: ghi từ máy thật
  AOT cache path/digest: ghi từ build thật
  Classpath/JAR digest: ghi từ build thật

Startup performance:
  Runs A/B: số đo thực
  Median, p95, variation: số đo thực
  Kết luận: giới hạn trong workload/target đo
```

**Đây là biểu mẫu ghi thực nghiệm, không phải một báo cáo kết quả thực tế đã đo**. Mỗi trạng thái cần có log/evidence cụ thể nếu đem dùng để quyết định rollout.

## 22. Những hiểu nhầm phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “AOT cache của HotSpot đã có trên Java 17.” | JEP 483 bắt đầu từ JDK 24 upstream |
| “Có `jaotc` trên JDK 25 vì có AOT cache.” | `jaotc` experimental đã removed từ JDK 17 |
| “AOT cache nghĩa toàn bộ app thành native executable.” | `.aot` là artifacts cho HotSpot JVM, không phải executable |
| “JEP 515 đóng gói tất cả compiled C2 methods.” | Method profiles được reuse để JIT tối ưu sớm |
| “Training chỉ cần `java -version`.” | Training phải chạy code paths ứng dụng thật |
| “Ứng dụng thay build vẫn dùng cache cũ.” | Regenerate theo application/JDK/platform constraints |
| “`AOTMode=auto` luôn đảm bảo cache được nạp.” | Có trường hợp VM chạy mà không sử dụng cache |
| “`AOTCacheOutput` dùng được trên JDK 24.” | Cờ tiện lợi này từ JDK 25 |
| “Cache của JDK 25 dùng trực tiếp trên JDK 26.” | Cache release-specific, phải tạo lại |
| “Dùng cache là bỏ qua static initialization.” | Java class-init semantics vẫn được bảo toàn |
| “Cache chứa mọi runtime object và secrets của ứng dụng.” | Chỉ có artifacts JVM cho phép cache |
| “JVM đổi GC nhưng cache luôn tương thích.” | Khả năng tùy JDK/GC và options; JEP 516 mở rộng JDK 26 |
| “JAR chỉ cần cùng filename.” | Artifact contents/timestamps/classpath consistency cần đối chiếu |
| “JVM in checksum đúng là chứng minh cache load.” | Phải dùng strict mode/logs |
| “Training càng nhiều test frameworks càng tốt.” | Thêm unused classes có thể làm cache phình to |
| “AOT chắc chắn tăng throughput sau warm-up.” | Chủ yếu hướng startup/warm-up, phải đo |
| “Một `.aot` dùng cho Windows/Linux/ARM như nhau.” | Không, cache platform-specific |
| “Lỗi không có AOT file nghĩa Java code sai.” | Có thể output path/permission/assembly failed |
| “Làm một lệnh output không tốn bộ nhớ phụ.” | JDK 25 có thể khởi chạy JVM assembly khác |
| “Cứ bật flags AOT trên OpenJ9 là được.” | HotSpot-specific; xác minh implementation |
| “Có Java agent đồng nghĩa luôn dùng AOT được.” | Một số JVMTI classloader changes không tương thích |
| “CDS `.jsa` và JEP 483 `.aot` là cùng file format.” | Khác mechanism/workflow theo release |

## 23. Bài tập tự kiểm tra

**Bài 1.** JDK mốc nào có AOT cache class loading/linking theo JEP 483?  
**Đáp án:** **JDK 24**.

**Bài 2.** JDK 25 bổ sung gì cho AOT?  
**Đáp án:** JEP 514 (ergonomic `AOTCacheOutput`) và JEP 515 (method profiling).

**Bài 3.** JDK 26 có cải thiện nào liên quan GC?  
**Đáp án:** JEP 516 mở rộng AOT object caching với những GCs hỗ trợ, trong đó có ZGC.

**Bài 4.** Vì sao JDK 17 `-XX:AOTMode=record` có thể lỗi?  
**Đáp án:** JEP 483 chưa có ở JDK 17 thông dụng; flags mới không được nhận diện.

**Bài 5.** Ba pha AOT gồm gì?  
**Đáp án:** Training/record → assembly/create → production/on.

**Bài 6.** Khi mode `create` chạy, có cần chạy lại `main` ứng dụng để tính checksum không?  
**Đáp án:** Không. Assembly đọc configuration và tạo cache; application main không được chạy trong pha này.

**Bài 7.** `AOTCacheOutput` khác record/create tường minh thế nào?  
**Đáp án:** Từ JDK 25, JVM làm record và assemble thuận tiện từ một invocation, có thể dùng JVM phụ để assemble.

**Bài 8.** Cache JDK 25 còn dùng được sau khi upgrade sang JDK 26?  
**Đáp án:** Không mặc định; phải regenerate cho JDK release/build và environment mới.

**Bài 9.** Training chỉ chạm một endpoint, production có mười endpoints. Có thể có giới hạn gì?  
**Đáp án:** Classes/method profiles chưa cover những paths còn lại, làm benefits AOT kém.

**Bài 10.** Cache có thay đổi `static` initializer observable Java semantics không?  
**Đáp án:** Không được; HotSpot phải giữ class loading/linking/initialization rules hợp lệ.

**Bài 11.** AOT cache là một native executable dùng thay `java`?  
**Đáp án:** Không. Đây là cache cho JVM, khác GraalVM Native Image.

**Bài 12.** JDK 25 có profile AOT thì JIT vẫn chạy sau startup không?  
**Đáp án:** Có. Cache profiles giúp JIT ra quyết định sớm, không đảm bảo có sẵn mọi native code.

**Bài 13.** AotCacheDemo in checksum bao nhiêu?  
**Đáp án:** **134000**, tổng 110000 từ AddOne và 24000 từ Twice.

**Bài 14.** Muốn fail-fast khi cache bị thiếu, dùng gì?  
**Đáp án:** `-XX:AOTMode=on` kết hợp đường dẫn `-XX:AOTCache=...` trên JVM hỗ trợ, rồi xem startup exit/log.

**Bài 15.** Cùng JAR filename nhưng rebuild dependency, có nên giữ `.aot`?  
**Đáp án:** Không; regenerate, đừng chỉ so filename.

**Bài 16.** Cache được tạo trên x86-64 Linux dùng trực tiếp trên AArch64 Windows?  
**Đáp án:** Không mặc định; OS/CPU khác khiến cache không tương thích.

**Bài 17.** Có Java agent thay classloading paths, phải làm gì?  
**Đáp án:** Xác minh JVMTI constraints và strict startup trên build/agent config thực.

**Bài 18.** Bật cache nhưng p99 không giảm; có lỗi không?  
**Đáp án:** Chưa kết luận. AOT hướng startup/warm-up; p99 có thể bị DB/I/O/locks/GC chi phối.

**Bài 19.** Chạy `java -Xlog:aot,class+path=info` có chứng minh toàn bộ cache đúng không?  
**Đáp án:** Chỉ giúp quan sát events; cần kết hợp strict mode, matching artifacts, successful startup và đo.

**Bài 20.** Vì sao không dùng `-XX:+IgnoreUnrecognizedVMOptions` để “bảo đảm deploy luôn chạy”?  
**Đáp án:** Có thể lặng lẽ bỏ AOT flag; service chạy nhưng benchmark/operational expectation sai.

**Bài 21.** Training load cả test framework có lợi không?  
**Đáp án:** Không nhất thiết; có thể tăng unused classes/cache size, giảm hiệu quả.

**Bài 22.** Có thể copy cache từ staging lên production?  
**Đáp án:** Chỉ khi application, JDK, OS/CPU và compatibility constraints khớp, artifact được quản lý an toàn và strict verification pass.

**Bài 23.** Cache cũ gặp lỗi, có nên tự sửa bytes `.aot` bằng hex editor?  
**Đáp án:** Không. Đây là VM-derived binary artifact; phải tìm nguyên nhân và regenerate đúng build.

**Bài 24.** Benchmark startup muốn quy lợi ích AOT, nên so điều gì?  
**Đáp án:** Cùng JDK/app/workload/heap/CPU nhưng `AOTMode=off` vs `AOTMode=on`, nhiều cold-process runs và log xác minh cache.

## 24. Checklist thực hành cuối bài

- [ ] Đã dùng **HotSpot JDK 24+** phù hợp, không nhầm JDK 17?
- [ ] Đã biết chính xác release hỗ trợ **JEP 483 / 514 / 515 / 516** nào?
- [ ] Đã compile Java code và tạo JAR chứa đầy đủ classes?
- [ ] Đã kiểm tra `jar --list --file app.jar` trước training?
- [ ] Đã lưu app JAR bất biến giữa record, create và run?
- [ ] Training đã exercise representative startup/hot paths?
- [ ] Không thực thi real business side effects trong training?
- [ ] `.aotconfig`/`.aot` được ghi hoàn tất, có quyền đọc/ghi và đủ disk?
- [ ] Khi dùng `AOTCacheOutput` đã dự trù memory cho assembly JVM?
- [ ] Production test dùng `AOTMode=on` để phát hiện cache mismatch rõ?
- [ ] Đã xem `-Xlog:aot,class+path=info` và `java -Xlog:help` đúng release?
- [ ] Đã xác minh application output giữ đúng nghĩa khi có/không cache?
- [ ] Đã không copy cache qua OS/CPU/JDK release khác?
- [ ] Đã kiểm tra agent/JVMTI/classpath/GC constraints?
- [ ] Đã regenerate cache nếu source/dependencies/JDK thay đổi?
- [ ] Đã đo startup/warm-up A/B nhiều lần với cùng CPU/heap/GC?
- [ ] Đã báo giới hạn và công việc chưa cover trong training?
- [ ] Đã quản lý provenance, digest và bảo mật file cache?
- [ ] Đã tách AOT cache của JEP 483 khỏi CDS `.jsa`, `jaotc` và GraalVM Native Image?

## 25. Kết luận

**AOT cache HotSpot là cơ chế học và reuse, không phải phép màu biên dịch Java toàn bộ thành native binary.** Từ JDK 24, JVM có thể quan sát một training run, xây các artifacts về loading/linking rồi dùng lại khi startup. JDK 25 làm quy trình thuận tiện hơn và thêm method profiles để JIT warm-up sớm. JDK 26 mở rộng sự hỗ trợ của object caching theo GC.

Muốn vận hành đúng:

```text
Immutable application artifact + đúng HotSpot release
              │
              ▼
Representative training run
              │
              ▼
AOT configuration / cache assembly
              │
              ▼
Strict cache validation trên target
              │
              ▼
Production startup sử dụng cache được xác minh
              │
              ▼
A/B benchmark startup + warm-up + memory
              │
              ▼
Regenerate cache ở mỗi build/JDK/OS/CPU thay đổi
```

**Tập trung vào điều có thể chứng minh:** đúng chức năng ứng dụng, file cache được tạo thành công, HotSpot thực sự nạp nó, và kết quả startup/warm-up được đo trong môi trường đại diện. Không hứa một mức tăng tốc cố định, không bỏ qua consistency constraints, và không dùng flags của JDK mới ở runtime chưa hỗ trợ.

### Tài liệu gốc

1. **OpenJDK JEP 483 — Ahead-of-Time Class Loading & Linking (JDK 24):** https://openjdk.org/jeps/483
2. **OpenJDK JEP 514 — Ahead-of-Time Command-Line Ergonomics (JDK 25):** https://openjdk.org/jeps/514
3. **OpenJDK JEP 515 — Ahead-of-Time Method Profiling (JDK 25):** https://openjdk.org/jeps/515
4. **OpenJDK JEP 516 — Ahead-of-Time Object Caching with Any GC (JDK 26):** https://openjdk.org/jeps/516
5. **Oracle JDK 24 — `java` command và AOT cache options:** https://docs.oracle.com/en/java/javase/24/docs/specs/man/java.html
6. **Oracle JDK 25 — `java` command, `AOTCacheOutput`, `AOTMode`:** https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html
7. **Oracle JDK 26 — `java` command, AOT cache mode/compatibility:** https://docs.oracle.com/en/java/javase/26/docs/specs/man/java.html
8. **Inside Java (Oracle, 2026) — Run Into the New Year with Java’s AOT Cache Optimizations:** https://inside.java/2026/01/09/run-aot-cache/
9. **OpenJDK JEP 410 — Remove the Experimental AOT and JIT Compiler:** https://openjdk.org/jeps/410
10. **OpenJDK Project Leyden:** https://openjdk.org/projects/leyden/

**Ghi nhớ:** cấu hình được đánh giá đúng khi ghi rõ **JDK version + application digest + OS/CPU + training coverage + strict AOT reuse evidence + A/B metrics**, chứ không chỉ vì có một file tên `app.aot` nằm trên đĩa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fast-startup-006">Điều kiện tương thích của archive hoặc cache với JDK, ứng dụng và môi trường</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện tương thích của archive hoặc cache với JDK, ứng dụng và môi trường

## Mục tiêu học tập

Những kỹ thuật **khởi động nhanh JVM** thường chuyển một phần công việc từ lúc chạy ứng dụng sang một bước chuẩn bị trước: tạo **CDS/AppCDS archive**, ghi một dạng cache, hoặc đóng gói dữ liệu đã phân tích để một lần chạy sau tái sử dụng. Điều đó làm nảy sinh câu hỏi quan trọng hơn việc “file cache có tồn tại hay không”:

> **File archive/cache được sinh cho JVM nào, ứng dụng nào và môi trường nào? Khi nào JVM thực sự được phép tái sử dụng nó, khi nào phải bỏ qua hoặc tạo lại?**

Trên **HotSpot Java 17**, ví dụ thực tế và có tài liệu chính thức rõ ràng nhất là **Class Data Sharing (CDS) / Application Class-Data Sharing (AppCDS)** với file `.jsa`. Archive này có thể tăng tốc class loading và giảm một phần memory footprint, nhưng **không phải một file Java chuẩn có thể chạy ở mọi JDK, mọi OS, mọi phiên bản ứng dụng**. Việc JVM từ chối archive không có nghĩa Java bytecode trở nên sai; trong nhiều cấu hình, JVM có thể khởi động theo đường không dùng archive, với hiệu năng khác.

Bài học giúp bạn:

1. Phân biệt **CDS archive**, **JIT Code Cache**, **cache của framework** và **snapshot/cache theo sản phẩm khác** — chúng không có cùng contract tương thích.
2. Nắm **những điều kiện JDK build, classpath/JAR, module path, class loader và cấu hình JVM** ảnh hưởng khả năng tái sử dụng archive Java 17.
3. Hiểu **base archive và dynamic top archive**: quan hệ phụ thuộc và vì sao không thể tùy tiện ghép file.
4. Biết **kiểm chứng bằng CDS/class-load logs**, kiểm thử trường hợp cấu hình thay đổi và chọn fallback/fail-fast hợp lý.
5. Tạo **lab Java 17 runnable** với JAR, static archive, dynamic archive và bộ kiểm tra tương thích cơ bản cho pipeline.
6. Không đánh đồng “ứng dụng in đúng kết quả” với “CDS đã hoạt động” hoặc “có archive” với “startup nhanh hơn”.

**Phạm vi:** JDK/HotSpot **17** và cách đánh giá các loại cache khởi động liên quan. Những tính năng AOT cache ở các JDK mới hơn hoặc snapshot của runtime/framework cụ thể có thể có quy tắc riêng; **không áp đặt option/cơ chế của phiên bản mới lên Java 17**.

---

## 1. Archive tương thích nghĩa là gì?

Một Java application có thể chạy bình thường mà không cần archive. Tương thích của một archive là chuyện khác: JVM cần tin rằng **dữ liệu đã được chuẩn bị trước vẫn mô tả đúng những classes và runtime assumptions ở lần chạy hiện tại**.

```text
                 BUILD / PREPARE
      JDK image + app JARs + JVM options + environment
                         │
                         ▼
               Create CDS archive
                    app.jsa
                         │
               deploy / restart
                         ▼
                  RUNTIME CHECKS
      JVM build, OS/arch, base archive, JAR identities,
       classpath/module configuration, relevant VM options
                         │
               ┌─────────┴─────────┐
               │                   │
           compatible         incompatible
               │                   │
               ▼                   ▼
       Map/reuse archive     Reject/skip/error
               │                   │
               ▼                   ▼
         Java semantics       Java semantics
         remain correct       must remain correct
```

Khi runtime xác nhận archive phù hợp, nó có thể ánh xạ dữ liệu class đã chuẩn bị vào địa chỉ bộ nhớ được phép và giảm công việc load/parse/verify/link tương ứng. Tính tương thích phụ thuộc **định dạng VM nội bộ** và quan hệ với đầu vào ban đầu.

Nếu archive không dùng được, phải tách hai câu hỏi:

- **Khả năng khởi động:** VM sẽ tự fallback hay dừng khởi động? Phụ thuộc `-Xshare` mode và loại lỗi.
- **Lợi ích hiệu năng:** có class dữ liệu ứng dụng nào thực sự được chia sẻ? Phải xem log, không suy từ file có sẵn.

### 1.1. Không phải mọi thay đổi môi trường đều có hậu quả giống nhau

| Thay đổi | Với CDS/AppCDS Java 17 | Tư duy kiểm tra |
| --- | --- | --- |
| Thay **JDK build/vendor/patch** | Archive có thể không tương thích | Tạo lại và xác minh |
| Đổi **JAR bytecode/nội dung** | Archive có thể bị từ chối | Gắn archive với artifact digest |
| Đổi **thứ tự classpath** | Có thể đổi class resolution, vi phạm điều kiện | Giữ classpath nhất quán |
| Runtime classpath thêm **JAR cuối danh sách** | Có điều kiện prefix được tài liệu cho phép | Xác minh lớp nào thực sự share |
| Runtime classpath bỏ/sắp xếp lại JAR | Có nguy cơ không dùng được | So chi tiết dump/runtime |
| Đổi module path | Có quy tắc riêng, không giống prefix classpath | Kiểm tra module JARs và logs |
| Thêm **`--patch-module`** | Oracle JDK 17 nói CDS bị vô hiệu | Đừng kỳ vọng dùng archive |
| Đổi OS/CPU architecture | Định dạng VM/memory mapping phụ thuộc nền tảng | Build archive trên đúng target runtime |
| Đổi hostname hoặc business config thuần dữ liệu | Không tự kết luận làm mất CDS | Kiểm tra liệu nó có đổi class-loading behavior không |
| Đổi heap size/GC option | Một số VM options ảnh hưởng compatibility; không phải mọi flag đều gây lỗi | Dùng logs/negative tests theo cấu hình thật |
| Đổi archive filename/path | Có thể ảnh hưởng tìm file và dependency của dynamic layer | Verify đường dẫn/base mapping |
| Chạy nhiều processes cùng file archive hợp lệ | Có khả năng chia sẻ read-only pages | So startup và PSS/USS, không cộng RSS mù |

**Bài học quan trọng:** archive không phải “dùng được hoặc không” dựa trên duy nhất `java -version`. Một bản Java 17 giống số phiên bản vẫn có thể khác vendor/build/VM flags/arch hoặc classpath.

## 2. Phân biệt các “archive” và “cache” có thể bị gọi chung

| Loại | Nội dung chủ yếu | Thời gian sống | Bảo đảm tái sử dụng giữa JVM runs? |
| --- | --- | --- | --- |
| **Default CDS archive** | Dữ liệu các lớp nền tảng đã được JDK chuẩn bị | File đi kèm JDK image | Có khi compatible với chính runtime image đó |
| **Static AppCDS archive** | Class metadata theo class list và build/runtime inputs | File `.jsa` do bước dump tạo | Có với JDK/classpath/VM settings phù hợp |
| **Dynamic CDS top archive** | Class metadata được ghi từ một lần chạy, trên một base archive | File `.jsa` bổ sung | Có khi top **và base** tương thích |
| **HotSpot JIT Code Cache** | Machine code do C1/C2 biên dịch lúc JVM chạy | Bộ nhớ nội bộ process | **Không mặc định** là persistent cache file tái sử dụng giữa Java 17 processes |
| **OS page cache** | Các pages file đã được OS đọc | Do kernel quản lý | Có thể giúp cold/warm startup, không do Java `SharedArchiveFile` định nghĩa |
| **Framework cache** | Metadata, indexes, generated config, templates, transformed data | Tùy framework | Phải theo contract/version/checksum của framework đó |
| **Native-image / snapshot của sản phẩm khác** | Native binary hoặc serialized runtime snapshot theo công cụ | Build/runtime artifact riêng | Không theo quy tắc CDS `.jsa`, có compatibility rules riêng |

**Không được đồng nhất:** nếu app startup nhanh hơn ở lần thứ hai, nguyên nhân có thể là OS page cache đã ấm, không phải AppCDS archive được dùng. Ngược lại, JIT Code Cache trong một process đã warm-up không phải file có thể copy sang process khác bằng `-XX:SharedArchiveFile`.

### 2.1. Dữ liệu về class khác dữ liệu của object

CDS/AppCDS không phải heap dump. Nó không “đóng băng” trạng thái mutable static fields, session data, in-memory caches, database connections hay business objects để process mới tiếp tục tại cùng state.

```text
CDS archive:
  reusable class-related metadata phù hợp

Không phải:
  Java object heap snapshot
  static field values tại lúc dump
  JIT code/CPU register snapshot
  open sockets và thread stacks
  token/session/application secrets
```

Nếu sản phẩm ngoài HotSpot hỗ trợ một cơ chế **checkpoint/restore**, nó cần tính tương thích riêng cho OS/kernel, filesystem, PID/network, clock, encryption secrets, JVM state và warm-up; **không dùng tài liệu CDS để kết luận nó cũng tương thích**.

## 3. Điều kiện JDK và VM build

CDS archive lưu cấu trúc dữ liệu VM được tối ưu cho **một cách biểu diễn nội bộ cụ thể**. Vì vậy nó không phải một định dạng portable như JAR hoặc class file.

### 3.1. Cùng “Java 17” chưa đủ

Xét các cách thay runtime:

```text
Temurin JDK 17 build A  → Oracle JDK 17 build B
OpenJDK 17.0.8         → OpenJDK 17.0.9
Linux x86_64           → Linux aarch64
Windows                → Linux
JDK 17                 → JDK 21
```

Không giả định archive `.jsa` giữ nguyên khả năng sử dụng trong những thay đổi trên. Compatibility check cụ thể thuộc HotSpot implementation; quá trình build JDK có thể đổi metadata layout. **Đóng gói archive cùng JDK image dùng để chạy và tạo lại archive sau thay đổi JDK** là lựa chọn vận hành an toàn.

Trong môi trường container, `image:tag` có thể trỏ tới image mới sau khi repull. Do đó nếu cần reproduce, pin **image digest** (và ghi `java -version`), không chỉ tên tag.

### 3.2. JDK home và default archive

Java 17 thường có **default CDS archive** đi kèm distribution, chẳng hạn `$JAVA_HOME/lib/server/classes.jsa` trên Linux/macOS theo một số cách đóng gói, hoặc đường dẫn tương đương dưới JDK home trên Windows. Đường dẫn chính xác phụ thuộc JDK image/vendor/platform.

**Không tự tải file `classes.jsa` từ JDK khác về chép vào JDK hiện tại**. Default archive thuộc bộ cài và tương thích với runtime đã tạo nó; thay thủ công bằng binary từ nguồn khác có thể bị từ chối hoặc tạo rủi ro supply-chain.

### 3.3. Class-file version không phải archive-file version

JVM có thể chạy class file `major version 61` của Java 17, nhưng một CDS archive đã tạo bằng một bản JDK khác vẫn có thể không compatible. Lý do:

- **Class file** có định dạng do JVMS quy định, có luật phiên bản và verification.
- **CDS archive** là dữ liệu triển khai nội bộ của HotSpot, không được đặc tả như một portable Java SE artifact.

`javac --release 17` bảo đảm đầu ra class/API target phù hợp Java 17 trong phạm vi compiler, **không đảm bảo một `.jsa` sinh từ JDK build A có thể map bằng JDK build B**.

### 3.4. Một số VM flags ảnh hưởng khả năng map archive

CDS có thể yêu cầu những giả định về data layout, runtime modules, compressed references, heap/memory reservation và VM features. Một số flags/điều kiện có thể khiến archive không dùng được hoặc chỉ một phần khả dụng.

Tránh lập một danh sách ngắn “5 flags chắc chắn disable CDS trong mọi HotSpot Java 17 build” nếu tài liệu không bảo đảm như vậy. Cách đúng:

1. Ghi **toàn bộ effective launcher JVM flags** ở bước tạo archive.
2. Chạy smoke test bằng **cấu hình production thật**, không chỉ cấu hình build.
3. Dùng `-Xlog:cds=info` / `-Xlog:cds=debug` để xem lý do skip/reject.
4. Nếu nghi ngờ option cụ thể, làm A/B test đổi một option rồi quan sát.

**Riêng Java 17 có tài liệu nêu rõ** `--upgrade-module-path`, `--patch-module` hoặc `--limit-modules` vô hiệu CDS; không suy điều này tự động áp dụng cho mọi module-related option khác.

## 4. Điều kiện ứng dụng: JAR content, classpath và thứ tự

**AppCDS static** và **dynamic CDS** đều có ràng buộc quan trọng với class-loading inputs. Archive chỉ có ích khi các classes được load ở runtime có định danh/code/source phù hợp với lúc archive được tạo.

### 4.1. Classpath dùng khi dump và khi chạy lại

Tài liệu Oracle JDK 17 quy định:

> **Classpath lúc tạo archive phải giống hoặc là prefix của classpath lúc runtime.**

Ví dụ:

```text
Tạo archive:
  -cp core.jar:service.jar

Runtime:
  -cp core.jar:service.jar
  → danh sách giống, điều kiện classpath đạt nếu các kiểm tra khác đều đạt

Runtime:
  -cp core.jar:service.jar:optional.jar
  → archive classpath là prefix, có thể đạt điều kiện này

Runtime:
  -cp service.jar:core.jar
  → đổi thứ tự, không còn cùng prefix

Runtime:
  -cp core.jar
  → thiếu thành phần từng dùng khi dump, không đạt điều kiện này
```

Trên **Windows**, phần tử classpath phân tách bởi `;`, còn trên Linux/macOS thường dùng `:`. Đừng nhầm dấu phân tách classpath với định dạng file JAR hoặc dấu phân tách **base/top archives**.

**Quan trọng:** “prefix được phép” không có nghĩa JAR cuối thêm vào luôn vô hại. Nếu JAR mới thay đổi class resolution/loader behavior, VM vẫn phải bảo đảm correctness và class-sharing eligibility. Chỉ khi verification/logs xác nhận thì mới coi archive hữu ích.

### 4.2. Sửa JAR sau khi dump

Oracle JDK 17 nêu rằng **CDS archive không thể được nạp nếu JAR trên class path hoặc module path bị sửa sau khi tạo archive** trong phạm vi kiểm tra liên quan.

Những thao tác có thể gây invalidation:

- Build lại application JAR với class bytes mới.
- Nâng version thư viện dependency.
- Thay JAR trong `lib/` nhưng giữ nguyên filename.
- Repackage JAR thay đổi manifest, entries hoặc metadata liên quan.
- Copy file mới lên đúng path sau khi archive được tạo.

**Đừng chỉ hash tên file.** Dùng content digest, release manifest hoặc artifact IDs để gắn archive với đúng binary inputs. Có trường hợp rebuild “cùng source code” vẫn sinh bytes/metadata khác nên không nên hứa archive sẽ hoạt động.

### 4.3. Đường dẫn JARs và reproducible deployment

Đường dẫn tuyệt đối, tương đối, symlink, current working directory và cách launcher mở JARs đều có thể gây khác biệt:

```text
Build:
  java -cp /app/lib/core.jar:/app/myapp.jar ...

Run:
  java -cp ./lib/core.jar:./myapp.jar ...
```

Đừng tự khẳng định hai cách viết tương đương cho mọi CDS compatibility check. JVM có các điều kiện xác minh source path/classpath; hãy **giữ startup commands, cwd và layout ổn định**, nhất là trong container images.

Nếu phải relocate ứng dụng, tái tạo archive ở layout mới hoặc ít nhất chạy xác minh `cds`/`class+load` trên target layout. Không giải quyết “file archive tồn tại mà không dùng” bằng cách tắt mọi kiểm tra.

### 4.4. Non-empty directories không giống JAR files

Theo Oracle Java 17 **CDS restriction**:

- Classpath (`-classpath` và `-Xbootclasspath/a`) không được chứa **non-empty directories** khi tạo/sử dụng CDS archive theo các ràng buộc được tài liệu mô tả.
- Module path không được chứa non-empty directories; chỉ **modular JARs** được hỗ trợ cho archiving trong điều kiện nói tới.
- Exploded modules không được hỗ trợ cho module path trong quy trình CDS tương ứng.

```bash
# Chạy ứng dụng Java bình thường có thể được:
java -cp out MyMain

# Nhưng để tạo AppCDS archive Java 17 tin cậy, dùng packaged JAR:
java -cp myapp.jar MyMain
```

Hai lệnh này minh họa **class-loading packaging**; class `MyMain` phải được cung cấp tương ứng. Không có nghĩa Java launcher cấm dùng thư mục `out` nói chung; nó chỉ **không đáp ứng một số điều kiện CDS archive**.

### 4.5. Wildcard và dependency resolution

Các launcher có thể cho `-cp "lib/*"`, nhưng thứ tự JARs thực tế và bộ dependencies cần được xác nhận thay vì coi wildcard là một danh sách ổn định theo ý developer. Nếu bộ JARs thay đổi, điều kiện AppCDS và khả năng class resolution thay đổi.

Khuyến nghị:

- Ghi effective classpath khi tạo archive.
- Giữ thứ tự/phạm vi JAR nhất quán giữa dump/run.
- Dùng immutable build artifacts trong image/container.
- Tránh cập nhật hot-swap JAR dưới đường dẫn đang dùng cho archive.
- Regenerate archive khi thay dependency graph.

## 5. Module path: quy tắc khác với classpath

Java 9+ có module system; ứng dụng modular có thể được khởi chạy bằng `--module-path` và `-m`. Với Java 17, CDS hỗ trợ các **modular JARs** phù hợp, nhưng không phải mọi kiểu module placement đều đủ điều kiện.

**Ba nguyên tắc:**

1. Classpath có quy tắc **same-or-prefix** khi dump vs runtime theo docs.
2. Oracle nói **không có cùng yêu cầu prefix cho module path**.
3. Tuy nhiên JARs trên module path vẫn phải còn phù hợp và **không bị sửa** sau khi archive tạo; những module options đặc biệt có thể vô hiệu CDS.

```bash
# Ví dụ launcher modular (package/module artifacts phải tồn tại):
java --module-path mods -m com.example.app/com.example.app.Main
```

Đây là **snippet cú pháp launcher**, không phải một runnable app độc lập vì không cung cấp module descriptor/JARs. Không suy `--module-path mods` đủ điều kiện archiving nếu `mods` là exploded directory thay vì chứa modular JARs đúng điều kiện.

### 5.1. Những module-related options vô hiệu CDS trong JDK 17

Oracle Java SE 17 launcher manual nêu rõ ba nhóm:

```bash
--upgrade-module-path
--patch-module
--limit-modules
```

Nếu các options này xuất hiện, CDS **bị vô hiệu**. Khi dùng chúng để tạo archive, VM có thể báo lỗi dump; khi dùng trong runtime, JVM chạy không load CDS archives theo mô tả của tài liệu.

Đừng dùng `-Xshare:on` để “ép CDS bất chấp module patching”: strict mode không làm không tương thích trở nên an toàn.

### 5.2. Custom class loaders, reflection và plugin systems

Frameworks có custom class loaders, proxies, generated classes, nested JARs hoặc runtime weaving có thêm điều kiện archive eligibility. Java 17 CDS có hỗ trợ một số class loading qua custom class loaders theo quy tắc nhất định, nhưng **không bảo đảm mọi dynamic proxy/generated class luôn được archive**.

Ví dụ những thay đổi cần xem:

- Plugin class loader loads JARs ở startup A khác startup B.
- Framework version mới tạo classes có tên/nội dung khác.
- Bytecode instrumentation agent thay classes trước/đang load.
- Application bật/tắt feature flags làm bộ classes được nạp khác.
- “Fat JAR” launcher mở nested JARs khác với flat classpath thông thường.

**Quy trình:** dùng đúng startup script của framework, warm-up representative code paths, bật `-Xlog:class+load=info` và kiểm tra **classes của chính ứng dụng** có `source: shared objects file` hay không. Thấy một core class được share không chứng minh cả framework đã share.

## 6. Archive tĩnh, archive động và điều kiện base–top

Trong HotSpot Java 17, có thể cấu hình **tối đa hai tầng CDS archive** theo Java launcher docs:

```text
           Static/base archive
                   │
                   ▼
           Dynamic/top archive
                   │
                   ▼
          Runtime maps base + top
```

Dạng tổng quát:

```text
-XX:SharedArchiveFile=<static_archive>:<dynamic_archive>
```

Ở Windows, dùng dấu phân tách `;` thay cho `:`. Nếu chỉ có một archive file, VM xác định dạng/tầng tương ứng qua metadata của file; dynamic archive lưu thông tin về base archive thích hợp, nên có thể chỉ cần `-XX:SharedArchiveFile=top.jsa` khi default/custom base khớp.

### 6.1. Static archive

Quy trình thông thường:

1. Chạy ứng dụng để ghi `app.classlist`.
2. Tạo `app-static.jsa` bằng `-Xshare:dump` + `SharedClassListFile`.
3. Chạy lại bằng `SharedArchiveFile`.

Tính tương thích cần xét cả JDK build, classpath/other options và file JARs. Có archive tĩnh không đồng nghĩa “không cần input JAR” khi runtime.

### 6.2. Dynamic archive

Quy trình thông thường:

1. Có base CDS archive phù hợp.
2. Chạy ứng dụng với `-XX:ArchiveClassesAtExit=app-dynamic.jsa`.
3. JVM ghi dynamic **top** archive khi exit phù hợp.
4. Lần chạy sau dùng `-XX:SharedArchiveFile=app-dynamic.jsa` hoặc chỉ định base/top.

**Dynamic archive không tồn tại độc lập với base compatibility assumptions**. Nếu thay default CDS archive hoặc đổi JDK build, dynamic top có thể bị từ chối. Không thể copy mỗi top sang image khác rồi cho rằng nó tự mang theo mọi dữ liệu lớp nền.

### 6.3. “Dynamic” không phải tự sửa cache trong mọi lần chạy

`ArchiveClassesAtExit` ghi ở **cuối một lần chạy**. Nó không có nghĩa:

- Khi một class mới được load, file `.jsa` được cập nhật ngay.
- File cũ luôn tự đồng bộ nếu JAR thay đổi.
- JVM tự xóa mọi archive không tương thích.
- Mọi bản HotSpot Java 17 đã có một `AutoCreateSharedArchive` behavior tương tự các JDK mới.

Nếu thay ứng dụng, cần **tái tạo archive** theo quy trình đúng, không đợi cache cũ tự “cập nhật”.

### 6.4. Trường hợp đổi base mà quên top

```text
Run A:
  JDK image A → base-A.jsa → app-top-A.jsa

Run B:
  JDK image B → base-B.jsa + app-top-A.jsa
                                 │
                                 ▼
                      compatibility không được đảm bảo
```

Một chiến lược release tốt là xuất bản **JDK image + ứng dụng JARs + custom/base/top archives** thành cùng một versioned unit. Đổi bất kỳ thành phần quan trọng nào → regenerate và test ở target.

## 7. AppCDS không dùng chung quy tắc với JIT Code Cache

HotSpot Java 17 còn có thuật ngữ **Code Cache**, là vùng memory giữ compiled machine code mà C1/C2 sinh trong runtime. Dù tên có “cache”, nó khác CDS:

- JIT compiled code thường phụ thuộc CPU instructions, receiver type profiles, compiler decisions, VM runtime addresses và các giả định về class loading.
- Nó được quản lý bởi JVM process; không có một cơ chế Java SE 17 chuẩn để dump toàn bộ JIT Code Cache thành `jit.jsa` rồi mang đi `SharedArchiveFile`.
- `-XX:ReservedCodeCacheSize` điều khiển capacity code cache (theo HotSpot), **không tự tạo persistent disk archive**.
- Code cache có thể bị thu dọn/reclaim trong quá trình runtime khi compiled code không còn dùng.
- Với warm-up trong cùng process, JIT đã compile methods và lần gọi sau chạy nhanh hơn; đó **không phải** bằng chứng về CDS compatibility giữa hai processes.

```text
CDS .jsa:          disk artifact → mapping class data across JVM processes
JIT Code Cache:    in-process machine code → JIT runtime optimization
OS page cache:     kernel-managed file pages → may affect disk I/O/startup
Framework cache:   provider-defined data → version/config validity rules
```

**Lưu ý:** Các cơ chế profile-guided optimization hoặc AOT compilation có ở những JDK/VM/products khác không biến mặc định HotSpot JDK 17 thành “có AOT cache portable”. Nếu dự án thực sự dùng một accelerator riêng, cần đọc documentation của **phiên bản chính xác** đó và định nghĩa compatibility riêng, không áp dụng máy móc bảng CDS.

## 8. Cache của framework: phải định nghĩa cache key và invalidation

Một framework có thể cache generated serializers, JSON schema, classpath scanning indexes, dependency injection metadata hoặc bytecode được sinh ra. Tính hợp lệ có thể phụ thuộc:

- Framework version, serializer/compiler version.
- Tập classes/JARs và content digests.
- Configuration flags, annotations, profiles và active modules.
- Data model/schema version, database mapping.
- Locale/timezone hoặc resource bundles nếu cache chứa dữ liệu phụ thuộc môi trường.
- OS/architecture nếu cache lưu native code hoặc addresses.
- Secrets/tenant-specific config nếu cache giữ dữ liệu nhạy cảm.

Không có quy tắc Java SE phổ quát “framework cache hợp lệ miễn JDK major version giống nhau”.

### 8.1. Định nghĩa compatibility contract cho cache do chính bạn xây

Ví dụ metadata manifest (chỉ là minh họa định dạng dữ liệu, không phải format mà HotSpot tự đọc):

```text
cacheFormatVersion=3
javaVendor=ExampleVendor
javaRuntimeVersion=17.0.x+build
osArch=amd64
appArtifactSha256=...
dependencyGraphSha256=...
configFingerprint=...
serializerVersion=...
generationEpoch=...
```

Một application cache nên có **versioned cache format**, hash hoặc signature của dữ liệu nền, kiểm tra integrity, atomic publication và fallback có thể đo được. Các trường nào quan trọng phụ thuộc chính cache đó; đừng giả định cần lưu hoặc gửi mọi secret ở dạng plaintext trong manifest.

**Đối với CDS archive**, manifest ngoài có thể dùng để quản lý release/CI, nhưng nó **không thay VM internal compatibility checks**.

## 9. Java 17 runnable lab: một JAR cho các phép thử archive

Đây là ứng dụng tối giản có thêm hai nested classes để quan sát class loading. Lưu **`ArchiveCompatibilityDemo.java`**:

```java
import java.util.List;
import java.util.Locale;

public final class ArchiveCompatibilityDemo {
    static final class Catalog {
        List<String> items() {
            return List.of("java", "jvm", "cache");
        }
    }

    static final class Formatter {
        String format(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    }

    public static void main(String[] args) {
        Catalog catalog = new Catalog();
        Formatter formatter = new Formatter();
        List<String> items = catalog.items();

        int count = 0;
        for (String item : items) {
            count += item.length();
        }

        System.out.println("items=" + items.size());
        System.out.println("totalChars=" + count);
        System.out.println("first=" + formatter.format(items.get(0)));
        System.out.println("version=" +
                Runtime.version().feature());
        System.out.println("done");
    }
}
```

**Chuẩn bị bằng JDK 17:**

```bash
mkdir -p out
javac --release 17 -d out ArchiveCompatibilityDemo.java
jar --create --file archive-demo.jar \
    --main-class ArchiveCompatibilityDemo -C out .
```

Lệnh là cú pháp POSIX shell; trên Windows có thể viết các câu lệnh `javac`/`jar` thành một dòng và dùng `mkdir out` theo shell phù hợp.

**Chạy bình thường trên JDK 17:**

```bash
java -cp archive-demo.jar ArchiveCompatibilityDemo
```

**Stdout ứng dụng chính xác trên JDK 17, không bật diagnostic logs:**

```text
items=3
totalChars=12
first=JAVA
version=17
done
```

Bởi vì `"java"` có 4 ký tự, `"jvm"` có 3 ký tự, `"cache"` có 5 ký tự, tổng **12**. `Runtime.version().feature()` bằng **17** trên runtime JDK 17. Nếu cố ý chạy JAR này bằng JDK mới hơn, dòng `version=...` sẽ thay đổi; vì thế ví dụ xác định stdout **trong phạm vi Java 17 đã chỉ định**.

### 9.1. Vì sao biên dịch xong phải đóng gói JAR?

Oracle Java 17 AppCDS restrictions không hỗ trợ non-empty classpath directories trong những trường hợp tạo/dùng archive. Chúng ta đóng gói `archive-demo.jar` để giữ cùng binary input xuyên trial/dump/runtime.

**Không sửa nội dung file JAR giữa bước dump và bước runtime**. Nếu mở `out` rồi chạy `javac` lại nhưng không rebuild JAR thì JAR vẫn cũ; nếu rebuild JAR thì archive cũ có thể không tương thích — cả hai tình huống cần được phân biệt rõ.

## 10. Lab static archive — tạo, chạy, kiểm tra

### 10.1. Trial run tạo class list

```bash
java -Xshare:off \
  -XX:DumpLoadedClassList=archive-demo.classlist \
  -cp archive-demo.jar ArchiveCompatibilityDemo
```

Lệnh chạy `main` và ghi **file class list**. Nội dung file phụ thuộc những classes runtime thực sự load. File classlist không phải archive; muốn tạo archive phải có bước tiếp theo.

### 10.2. Tạo static archive

```bash
java -Xshare:dump \
  -XX:SharedClassListFile=archive-demo.classlist \
  -XX:SharedArchiveFile=archive-static.jsa \
  -cp archive-demo.jar
```

Ở chế độ `dump`, JVM tạo file `archive-static.jsa`. Đây **không phải** lần thực thi `ArchiveCompatibilityDemo.main()`, nên không bắt buộc có stdout nghiệp vụ như phần 9.

### 10.3. Chạy lại và xác minh

```bash
java -Xshare:auto \
  -XX:SharedArchiveFile=archive-static.jsa \
  -Xlog:cds=info,class+load=info \
  -cp archive-demo.jar ArchiveCompatibilityDemo
```

Ứng dụng **vẫn phải cho kết quả nghiệp vụ tương đương** phần 9. Ngoài ra có các dòng VM logging, có thể bao gồm nguồn nạp lớp kiểu **`source: shared objects file`**. Thời điểm, số lines, addresses, class loading order tùy JDK build, vì vậy **không bịa một transcript logger cố định**.

### 10.4. Baseline không CDS

```bash
java -Xshare:off -cp archive-demo.jar ArchiveCompatibilityDemo
```

Đây là phép đối chiếu: ứng dụng chạy đúng Java semantics nhưng không sử dụng CDS. Nếu so thời gian startup, chạy nhiều lần, cùng JDK/cpu/heap/cwd/JARs và xem warm filesystem page cache.

### 10.5. Điều gì chứng minh tương thích?

- File archive tồn tại và không bị lỗi quyền đọc.
- Runtime log cho thấy CDS mapping thành công.
- Một số **application classes** trong log có `source: shared objects file`, không chỉ lớp `java.base`.
- Không có thông báo reject/skip archive liên quan.
- Output nghiệp vụ vẫn đúng.
- Kết quả có thể lặp lại ở môi trường mục tiêu.

Chỉ có exit code 0 và `done` **không đủ chứng minh** archive được dùng.

## 11. Lab dynamic archive — base/top và compatibility

### 11.1. Tạo dynamic top archive khi JVM thoát

```bash
java -Xshare:auto \
  -XX:ArchiveClassesAtExit=archive-dynamic.jsa \
  -cp archive-demo.jar ArchiveCompatibilityDemo
```

Nếu JVM có base archive phù hợp và shutdown/dump thành công, file `archive-dynamic.jsa` sẽ được tạo. Nó bổ sung classes eligible đã nạp nhưng chưa có ở base; nó **không** chứa một bản sao toàn bộ heap.

### 11.2. Sử dụng dynamic archive

```bash
java -Xshare:auto \
  -XX:SharedArchiveFile=archive-dynamic.jsa \
  -Xlog:cds=info,class+load=info \
  -cp archive-demo.jar ArchiveCompatibilityDemo
```

HotSpot sử dụng thông tin về base archive liên quan từ dynamic archive. Nếu base đổi/không còn map phù hợp, dynamic top có thể không được dùng. Không copy top sang JDK khác rồi suy rằng nó độc lập.

### 11.3. Tùy chọn ghi cả base và top

Nếu có một custom static base archive phù hợp, có thể thử:

```bash
# Khi đã có custom base và đã tạo top trên base này:
java -XX:SharedArchiveFile=base.jsa:top.jsa \
  -cp archive-demo.jar ArchiveCompatibilityDemo
```

Dấu `:` là delimiter trên POSIX; Windows dùng `;`. Đây là **snippet minh họa** vì `base.jsa`/`top.jsa` cần được tạo từ đúng JDK và ứng dụng trước đó.

**Không nối binary files bằng `cat base.jsa top.jsa > both.jsa`**. Cấu trúc header/tầng lớp do HotSpot quản lý; lựa chọn cấu hình đúng là `SharedArchiveFile`.

## 12. Thực hành kiểm tra tình huống không tương thích

Những thí nghiệm sau nên thực hiện trong **workspace lab cách ly**, không sửa JAR production để thử:

### Tình huống A — Thay app JAR sau dump

1. Tạo `archive-static.jsa` với JAR version A.
2. Cố ý sửa source rồi build JAR version B vào cùng path.
3. Chạy với archive A và bật `-Xlog:cds=debug,class+load=info`.
4. Ghi exit code, CDS reason, classes thực sự được load.
5. Tạo lại archive với version B, verify lần nữa.

**Kỳ vọng có thể kiểm chứng:** file archive cũ không được tin cậy nếu JAR nguồn đã đổi theo CDS checks. **Không khẳng định mọi VM build sẽ in chính xác cùng câu lỗi**, và không dùng strict mode trong production.

### Tình huống B — Đổi thứ tự JARs

```text
Archive creation classpath:
  lib/a.jar:lib/b.jar

Runtime classpath:
  lib/b.jar:lib/a.jar
```

Classpath lúc runtime không còn cùng thứ tự/prefix. Điều này có thể thay class resolution. Không ép JVM dùng archive bằng cách đổi tên file; chuẩn hóa thứ tự JARs và tạo lại archive nếu startup classpath của production khác.

### Tình huống C — Đổi JDK patch/vendor

- Archive trên build X.
- Khởi động với JDK build Y.
- Quan sát logs/exit code.
- Regenerate archive dưới build Y.
- Verify dưới chính build Y.

Không có quy tắc “major version 17 giống nhau thì file `.jsa` chắc chắn portable”. Dùng build-specific artifact metadata trong CI.

### Tình huống D — Thêm `--patch-module`

Khi một command line chứa `--patch-module` theo cách có hiệu lực, Oracle JDK 17 docs nói **CDS bị disable**. Đây là negative test rõ ràng để dạy sự khác nhau giữa JDK module configuration và binary archive:

```text
CDS enabled app run ≠ app run có --patch-module
```

Đừng thử nó bằng cách đưa một module path giả lập để rồi quy kết mọi lỗi launch là do CDS; chỉ test khi bạn có modular application đúng và patching cần thiết.

### Tình huống E — Default base bị thay

Dynamic top tạo trên default base của JDK X. Nếu base archive của JDK Y khác, dynamic top có thể không compatible. Tạo lại top với chính base của runtime Y, không tái sử dụng bằng niềm tin vào tên file giống nhau.

## 13. `-Xshare:off`, `auto` và `on` ảnh hưởng fallback

| Mode | Ý định | Khi archive không dùng được |
| --- | --- | --- |
| `-Xshare:off` | Không dùng CDS | JVM vẫn chạy theo đường không dùng archive nếu không có lỗi khác |
| `-Xshare:auto` | Dùng CDS khi đủ điều kiện | Thường có thể bỏ qua archive không phù hợp theo chính sách; phải xem loại lỗi và log |
| `-Xshare:on` | Yêu cầu CDS bằng strict mode | Nếu không dùng được archive có thể **dừng khởi động với lỗi** |
| `-Xshare:dump` | Tạo static archive | Lỗi dump khiến bước chuẩn bị thất bại; không phải application startup |

**Không xem `-Xshare:on` là “bật tối ưu mạnh hơn”.** Nó chủ yếu đặt kỳ vọng nghiêm ngặt rằng CDS phải khả dụng. Oracle Java 17 CDS docs nói `on` **dành cho thử nghiệm** và không nên dùng trong production nếu môi trường có thể đổi JDK/options.

### 13.1. Production fallback là một quyết định vận hành

Một service phải ưu tiên startup availability có thể dùng `-Xshare:auto` cùng metrics để phát hiện archive bị bỏ qua, sau đó rebuild ở release tiếp theo. Một CI preflight cần phát hiện lỗi sớm có thể chạy một **smoke test strict** trong môi trường kiểm soát.

Tránh hai cực đoan:

- **Silent ignore:** “App chạy được nên AppCDS chắc ổn”; có thể âm thầm mất lợi ích startup.
- **Strict everywhere:** “Mọi JVM process buộc phải dùng CDS bằng `on`”; có thể biến một archive mismatch thành outage.

Thiết kế tốt: **fail-fast trong CI, availability-conscious trong production, alert khi expected sharing không còn**.

## 14. Đọc logs để phân biệt mismatch với miss vì chưa có class

Các tùy chọn HotSpot Java 17 quan trọng:

```bash
-Xlog:cds=info
-Xlog:cds=debug
-Xlog:class+load=info
-Xlog:cds=debug,class+load=info
```

**`cds` logs** cho thấy các hoạt động open/map/validation của archive. **`class+load` logs** cho nguồn của từng class khi load.

Một ví dụ dòng **minh họa**, không phải transcript của lab:

```text
[info][class,load] ... source: shared objects file
[info][class,load] ... source: file:/app/archive-demo.jar
```

Cách đọc:

- `source: shared objects file` cho một class: class đó được load từ shared archive.
- `source: file:/...jar`: ở lần chạy ấy JVM đọc từ JAR, không chứng minh cả archive không được dùng.
- Log báo archive skipped/incompatible: phải xử lý lý do trước khi kỳ vọng startup benefit.
- Chỉ thấy JDK core classes từ shared archive: **default CDS có thể hoạt động**, còn AppCDS classes có thể chưa được share.
- Một class chưa được nạp trong workload thì không có evidence để kết luận nó được share, dù class file có trong JAR.

### 14.1. Kiểm tra lớp ứng dụng, không chỉ JVM classes

Ví dụ sau có nested classes:

```text
ArchiveCompatibilityDemo
ArchiveCompatibilityDemo$Catalog
ArchiveCompatibilityDemo$Formatter
```

Trong `class+load` log của lần chạy, tìm các class này và nguồn của chúng. Không suy từ các classes `java.lang.String`, `java.util.List` đã share rằng AppCDS custom archive đã làm việc.

### 14.2. Kiểm tra sau upgrade, không chỉ sau build

Một archive đã verify tại build machine **chưa chứng minh** nó dùng được trên target container/host vì environment có thể khác. Thêm một **post-deploy smoke test** hoặc startup log inspection có kiểm soát.

Khi runtime phải xử lý lỗi mismatch, ghi lại **JDK/vendor/build, archive ID/checksum, app artifact digest, classpath/module options và CDS log reason**. Những dữ liệu này giúp phân biệt “JDK đổi” và “JAR đổi” thay vì chỉ tìm file missing.

## 15. Cơ chế kiểm tra tương thích cho CI/CD

Một release pipeline có thể theo bước:

```text
    Pin JDK image digest và runtime architecture
                        │
                        ▼
    Build immutable JARs + dependency manifest
                        │
                        ▼
    Record effective classpath/module configuration
                        │
                        ▼
       Tạo static hoặc dynamic CDS archive
                        │
                        ▼
       Ghi metadata/digests của artifact inputs
                        │
                        ▼
      Smoke test trên SAME runtime image/flags
         -Xlog:cds,class+load
                        │
                 ┌──────┴──────┐
                 │             │
             PASS used?      NO / fallback?
                 │             │
                 ▼             ▼
      Package JDK+JAR+JSAs   Regenerate/diagnose
                 │
                 ▼
      Deploy immutable unit + post-deploy verify
                 │
                 ▼
       Monitor startup time + memory footprint
```

### 15.1. Metadata manifest — helper bên ngoài, không phải VM format

Ví dụ thông tin release nên ghi:

```text
runtime.version=17.0.x+build
runtime.vendor=<vendor>
runtime.imageDigest=<digest>
os.arch=<architecture>
archive.type=static-or-dynamic
archive.baseId=<base-id-if-any>
archive.sha256=<sha256>
application.jar.sha256=<sha256>
dependencies.lock.sha256=<sha256>
classpath.signature=<canonicalized-cp-hash>
modulepath.signature=<canonicalized-mp-hash>
required.vm.options=<recorded-keys>
```

Các placeholders chỉ thể hiện **schema minh họa** cho CI, không phải Java program cần compile. **CDS archive không tự đọc file manifest này**: VM vẫn tự kiểm tra những điều kiện nội bộ.

### 15.2. Atomic publication và quyền file

Nên tạo archive ở một **đường dẫn tạm** trong build/job, verify thành công, rồi đổi tên/publish **một lần** theo cơ chế filesystem phù hợp. Tránh để một process đọc file trong khi process khác đang ghi chưa xong.

- Chỉ mount archive có nguồn tin cậy.
- Run user có **quyền đọc archive** và **truy cập các thư mục cha**.
- Khi dump cần **quyền tạo/ghi file** ở destination.
- Đừng dùng cùng một tên `app.jsa` cho nhiều builds đồng thời.
- Có checksum/size sanity check khi đưa artifact qua hệ thống lưu trữ.
- Tránh tự chỉnh binary `.jsa`; format thuộc HotSpot.
- Nếu nhiều runtime processes cùng dùng một archive immutable, giữ file ổn định để OS có thể chia sẻ mapped pages theo điều kiện nền tảng.

## 16. Môi trường triển khai: containers, OS, CPU và storage

### 16.1. Docker/Kubernetes

Một container image có thể chứa JDK, application JARs và archive theo đúng layout:

```text
/app/
  jdk/
  lib/
    app.jar
    dep-a.jar
  cds/
    app.jsa
```

Đây là **sơ đồ deployment**. Khi xây image:

- Chốt **JDK image/build** và architecture trong build stage.
- Nếu build multi-stage, không tạo archive bằng JDK builder khác JDK runtime.
- Giữ JARs/dependencies immutable và paths tương thích.
- Nếu làm multi-arch image, tạo/verify archive cho **từng target architecture**.
- Phân biệt ephemeral writable layer với immutable archive dùng lúc startup.
- Nếu archive được tạo lúc pod shutdown bằng `ArchiveClassesAtExit`, filesystem ephemeral/pod deletion có thể khiến file không tồn tại cho pod tiếp theo; dùng dedicated archive-generation build job hoặc persistent artifact publishing.
- Không dựa vào `SIGKILL` shutdown để archive-at-exit hoàn tất.

### 16.2. CPU instruction set và OS

Machine code lưu trong JIT Code Cache có thể sử dụng CPU instructions riêng. CDS archive không phải JIT compiled code, nhưng vẫn là binary VM artifact và có các assumptions về platform/build; **không giả định archive có thể chuyển từ Linux x86_64 sang Windows/aarch64**.

File paths, separators và permissions cũng khác. Trên Windows, `-XX:SharedArchiveFile=base.jsa;top.jsa` sử dụng `;` làm delimiter. Trên POSIX, dùng `:` như tài liệu Oracle.

### 16.3. Storage, memory mapping và file permissions

CDS cần đọc/map file archive theo khả năng OS/VM. Các vấn đề có thể gồm file missing, permission denied, truncation, mapping constraints hoặc artifact corruption.

Một file có tên `app.jsa` nhưng kích thước 0 byte rõ ràng không phải một archive hữu dụng. Nhưng một file có kích thước lớn và checksum tồn tại **cũng chưa đủ**: JVM phải validate internal contents và khả năng tương thích.

### 16.4. Nhiều JVM processes và shared pages

Một lợi ích của CDS là OS có thể chia sẻ các read-only mapped pages giữa nhiều processes trên cùng host. Tuy vậy, **không bảo đảm** mỗi container/pod đều chia sẻ cùng physical pages nếu filesystem/mount/build khác nhau.

Khi đo memory:

- `RSS` của nhiều processes có thể chứa shared pages và bị đếm trùng khi cộng.
- `PSS` hoặc các metrics tách shared/private (nếu OS hỗ trợ) có ích hơn khi đánh giá lợi ích tổng.
- Heap objects/static mutable states của từng process không trở thành shared business data chỉ nhờ AppCDS.

## 17. Đo “tương thích” và “nhanh hơn” là hai bài kiểm tra khác nhau

**Compatibility test** hỏi:

> Archive có được JVM **chấp nhận và thực sự dùng** không?

**Performance test** hỏi:

> Startup/readiness/memory footprint của ứng dụng có **cải thiện đáng kể và lặp lại** so với baseline không?

Tương thích **không tự bảo đảm** lợi ích đo được. Nếu app rất nhỏ, phần thời gian liên quan class loading có thể không đáng kể so với OS process startup. Ngược lại, một app lớn có nhiều classes startup có thể hưởng lợi hơn.

### 17.1. Chọn chỉ số startup đúng

- JVM process launch → first Java log.
- Process launch → service listen port opened.
- Process launch → readiness check đáp ứng.
- Process launch → first successful business request.

Đo cùng định nghĩa ở mọi cấu hình. Nếu process có lazy initialization, “main đã in done” không nhất thiết là lúc service thực sự sẵn sàng.

### 17.2. So ba trạng thái có ý nghĩa

```bash
# Không dùng CDS:
java -Xshare:off -cp archive-demo.jar ArchiveCompatibilityDemo

# Có default CDS (nếu hợp lệ):
java -Xshare:auto -cp archive-demo.jar ArchiveCompatibilityDemo

# Có AppCDS archive đã được verify:
java -Xshare:auto -XX:SharedArchiveFile=archive-static.jsa \
  -cp archive-demo.jar ArchiveCompatibilityDemo
```

Chạy nhiều lần, đảo thứ tự để hạn chế bias do OS page cache, cố định host load/JDK/CPU quotas và phân tích median/p95 cùng counts. Đừng tuyên bố một cấu hình nhanh hơn X% chỉ từ một lần chạy của demo rất ngắn.

### 17.3. GC và JIT logs có thay cho CDS logs không?

Không. `-Xlog:gc*` tập trung vào GC events, còn `-XX:+PrintCompilation` về JIT compilation. Muốn kiểm tra class có dùng CDS, xem `-Xlog:cds` và `-Xlog:class+load`; sau đó dùng GC/JIT logs khi giải thích các khía cạnh performance khác.

Một câu nói chính xác: **“AppCDS có thể giảm class loading cost; JIT, GC và application initialization vẫn tồn tại.”**

## 18. Những kịch bản lỗi phổ biến và hành động đề xuất

| Triệu chứng | Nguyên nhân có thể | Hành động |
| --- | --- | --- |
| JVM báo archive bị từ chối sau nâng JDK | VM build/internal layout đổi | Regenerate trên exact JDK runtime |
| Run vẫn thành công nhưng không thấy class app shared | Default CDS được dùng, AppCDS không match hoặc class chưa eligible | Xem `cds`/`class+load` và classpath |
| Static dump báo liên quan non-empty directory | Archive classpath không dùng JARs phù hợp | Package classes vào JAR |
| Dynamic top không dùng sau đổi image | Base archive dependency mismatch | Tạo lại top trên base của runtime mới |
| Runtime classpath khác build/dump | JAR order/path/content đã thay | Đồng bộ classpath và rebuild archive |
| Module patching có hiệu lực | `--patch-module` vô hiệu CDS theo Java 17 docs | Chấp nhận chạy không CDS hoặc thay thiết kế |
| App chạy với `-Xshare:off` | Cấu hình tắt sharing | Sửa launcher nếu muốn sử dụng archive |
| Strict `-Xshare:on` làm fail startup | Archive không đáp ứng compatibility/availability | Không dùng strict trong production; sửa archive trong CI |
| File archive thiếu trong container | Không copy vào runtime image, mount/path khác | Verify image filesystem/digest |
| File archive không tạo khi shutdown | App bị kill/không dump hoàn tất | Dedicated generation run + graceful exit |
| Startup time không đổi | Classes chưa hưởng lợi hoặc bottleneck khác | Verify class sources và profile startup |
| Chỉ default classes shared | App-specific archive không được dùng/hits thấp | Warm-up đại diện, kiểm tra eligibility |
| Nhiều pod memory không giảm | Không chia thực shared pages, data chủ yếu heap | Đo PSS/USS và deployment file mapping |
| App logic sai sau upgrade | Bug classpath/class loader/deployment, không nên quy tội archive ngay | Chạy `-Xshare:off` đối chiếu + phân tích logs |
| Cache framework sai dữ liệu | Cache key thiếu dependency/config version | Sửa invalidation strategy riêng của framework |

### 18.1. `-Xshare:off` là diagnostic A/B, không phải chữa khỏi mọi lỗi

Nếu JVM khởi động được với `-Xshare:off` nhưng không chạy được với custom archive, đó là manh mối compatibility/option. Nhưng nếu ứng dụng vẫn ném `ClassNotFoundException` hoặc business data lỗi trong cả hai modes, không nên đổ lỗi cho archive.

Giữ log cả hai runs và so effective classpath, JVM version và exceptions.

## 19. Những hiểu lầm cần loại bỏ

1. **“Archive dùng được trên mọi máy có Java 17.”** Sai. VM build/arch/classpath và runtime assumptions có thể khác.
2. **“Java class file portable nên `.jsa` cũng portable.”** Sai. CDS archive là HotSpot-specific binary artifact.
3. **“AppCDS thay thế JAR file.”** Sai. JAR/classpath/resources vẫn cần tương thích.
4. **“Đổi tên JAR là đủ để cache tương thích lại.”** Sai. Tính hợp lệ phụ thuộc nội dung/đường phân giải được kiểm tra.
5. **“Đổi dependency nhưng tên JAR không đổi thì archive chắc còn dùng được.”** Sai.
6. **“Runtime classpath có thể đảo JAR order thoải mái.”** Sai. Order ảnh hưởng class resolution và prefix rule.
7. **“Một classpath directory hoạt động với `java -cp` thì luôn được AppCDS archive.”** Sai. Có ràng buộc non-empty directory.
8. **“Module path phải luôn là prefix giống classpath.”** Sai. Oracle docs phân biệt hai trường hợp.
9. **“`--patch-module` không liên quan CDS.”** Sai. Theo Java 17 docs, nó vô hiệu CDS.
10. **“Archive tĩnh và động đều tự chứa mọi classes của JDK.”** Sai. Dynamic top phụ thuộc base.
11. **“Có thể nối mọi file `.jsa` thành một file dùng chung.”** Sai. VM quản lý archive format/layering.
12. **“Một dynamic archive trên JDK A chắc dùng được với base của JDK B.”** Sai.
13. **“`ArchiveClassesAtExit` cập nhật file cache liên tục khi app đang chạy.”** Sai.
14. **“Ứng dụng exit 0 chứng minh AppCDS đã được dùng.”** Sai. Phải kiểm tra CDS/class-load logs.
15. **“Thấy `java.lang.String` shared là đủ chứng minh custom app archive hoạt động.”** Sai.
16. **“`-Xshare:on` là chế độ tăng tốc hơn `auto`.”** Sai. `on` là strict và có nguy cơ làm fail startup.
17. **“JIT Code Cache lưu mặc định vào `.jsa` để các JVM khác tái dùng.”** Sai.
18. **“OS page cache ấm là dấu hiệu CDS enabled.”** Sai. Hai cơ chế độc lập.
19. **“Một framework cache có cùng rules như CDS archive.”** Sai. Mỗi provider định nghĩa contract riêng.
20. **“Cứ thêm CPU là archive sẽ tương thích.”** Sai. CPU có thể cải thiện tốc độ, không làm thay đổi binary compatibility.
21. **“CDS chia sẻ toàn bộ Java heap và static fields giữa processes.”** Sai. Chia class data, không chia mutable business state.
22. **“Cùng major JDK 17 là đủ khi build multi-arch image.”** Sai.
23. **“CDS compatibility failures phải luôn làm process crash.”** Sai. `auto` có thể fallback, `on` thường fail strict.
24. **“Không thấy nhiều GC events chứng minh archive đã dùng.”** Sai. GC logs không kiểm tra nguồn class metadata.
25. **“Tăng `-Xmx` luôn giải quyết archive mapping failure.”** Sai.
26. **“Archive được JDK chấp nhận nghĩa startup chắc giảm 50%.”** Sai. Phải đo workload thực.
27. **“Có thể dump archive khi app bị SIGKILL mà chắc được file đầy đủ.”** Sai.
28. **“File `.jsa` có tên hợp lệ thì nội dung chắc cũng hợp lệ.”** Sai. Cần integrity và VM validation.
29. **“CDS cache mang theo application config và secrets.”** Sai. Đó không phải heap/config snapshot.
30. **“Một JDK vendor mới chắc tương thích archive vendor cũ nếu cùng build number.”** Không được bảo đảm.
31. **“Cứ `java -jar` là runtime classpath luôn giống `-cp` lúc dump.”** Không phải một quy tắc tổng quát; launcher/class-loader behavior có thể khác.
32. **“Khi archive mismatch, nên tắt VM internal validation.”** Sai. Phải tái tạo archive đúng input.
33. **“AppCDS tự động cache tất cả future plugin classes.”** Sai. Eligibility/loading/trial profile có giới hạn.
34. **“Cần dùng `-Xshare:on` để xác minh in production.”** Sai. Test strict ở CI, tránh làm service dễ outage.
35. **“Một tag Docker bất biến theo tên nên archive cũng bất biến.”** Không chắc; tag có thể được republish, cần image digest.
36. **“CDS archive tương thích nghĩa input source code không đổi.”** Chưa đủ; VM build, JAR bytes/path, options, module config cũng quan trọng.

## 20. Tự kiểm tra và đáp án

**Câu 1.** Archive được tạo từ JDK 17.0.8 vendor A, chạy dưới JDK 17.0.9 vendor B. Có thể tin chắc file `.jsa` hợp lệ không?

**Đáp án:** Không. CDS archive là VM-build-specific artifact; tái tạo trên runtime JDK đích và xác minh.

**Câu 2.** Dump classpath là `a.jar:b.jar`; runtime classpath là `a.jar:b.jar:c.jar`. Có nhất thiết vi phạm classpath prefix rule không?

**Đáp án:** Không. Dump classpath là prefix của runtime classpath; nhưng các điều kiện khác vẫn phải thỏa, và classes cần được xác minh từ logs.

**Câu 3.** Dump classpath là `a.jar:b.jar`; runtime thành `b.jar:a.jar`. Cách xử lý?

**Đáp án:** Khôi phục order và JARs đúng hoặc tạo lại archive với classpath mục tiêu; không chỉ đổi archive filename.

**Câu 4.** Vì sao có `.jsa` và chương trình in kết quả đúng chưa đủ chứng minh AppCDS?

**Đáp án:** JVM có thể chạy không dùng archive, vẫn đúng Java semantics. Cần `-Xlog:cds` và `-Xlog:class+load` kiểm tra mapping/class source.

**Câu 5.** Dynamic archive có thể chuyển sang JDK khác chỉ cần giữ file top không?

**Đáp án:** Không bảo đảm. Top phụ thuộc base và VM assumptions tương ứng.

**Câu 6.** `-XX:ReservedCodeCacheSize` có làm file archive `.jsa` chứa machine code sau JIT warm-up không?

**Đáp án:** Không. Nó điều khiển in-process JIT code cache size, không tạo persistent AppCDS artifact.

**Câu 7.** `--patch-module` ảnh hưởng CDS thế nào theo Java 17 docs?

**Đáp án:** Vô hiệu CDS trong phạm vi hướng dẫn Oracle launcher.

**Câu 8.** Muốn dùng archive nhưng không muốn service bị fail chỉ vì CDS unusable, nên chọn `-Xshare:off` hay `-Xshare:auto`?

**Đáp án:** Thường chọn `-Xshare:auto` cùng logging/monitoring; `off` không dùng CDS, còn `on` strict có thể fail startup.

**Câu 9.** Default CDS archive của JDK và AppCDS custom archive có giống nhau không?

**Đáp án:** Không. Default chủ yếu chứa classes nền; custom AppCDS có thể thêm class ứng dụng và dependencies theo workflow.

**Câu 10.** Thấy class `ArchiveCompatibilityDemo` từ shared objects file thì đã đủ kết luận toàn bộ application classes đều shared chưa?

**Đáp án:** Chưa. Chỉ kết luận class cụ thể đó shared ở run này; kiểm tra những classes khác nếu cần.

**Câu 11.** Trong lab Java 17, stdout `totalChars` là bao nhiêu?

**Đáp án:** **12** từ độ dài `java`(4), `jvm`(3), `cache`(5).

**Câu 12.** Trong lab, `Runtime.version().feature()` in giá trị nào khi chạy trên JDK 17?

**Đáp án:** **17**.

**Câu 13.** Tại sao update app JAR cùng đường dẫn vẫn làm archive bị từ chối?

**Đáp án:** Binary contents/metadata và class identity assumptions không còn khớp với dữ liệu lúc dump.

**Câu 14.** Nếu app dùng nhiều custom class loaders/nested JARs, có được coi mọi lớp đều archivable không?

**Đáp án:** Không. Eligibility và startup behavior phụ thuộc loader/VM/framework, phải kiểm tra bằng runtime logs.

**Câu 15.** Đo memory tiết kiệm của nhiều JVMs bằng tổng RSS có thể sai thế nào?

**Đáp án:** Shared mapped pages bị tính trong RSS của nhiều processes; tổng RSS có thể đếm trùng. Cân nhắc PSS/USS hoặc OS tools phù hợp.

**Câu 16.** Vì sao OS page cache có thể làm kết quả startup benchmark sai lệch?

**Đáp án:** Những runs sau có thể đọc JAR/archive từ page cache đã ấm, nhanh hơn dù chính sách CDS không đổi. Cần nhiều runs và đảo thứ tự.

**Câu 17.** Nếu CI dùng JDK 17 nhưng production image chứa JDK 21, phải làm gì?

**Đáp án:** Không mang archive JDK 17 sang dùng như portable artifact. Dùng đúng production JDK/image khi generate archive và verify.

**Câu 18.** `-XX:ArchiveClassesAtExit` nên ghi ra đâu trong môi trường ephemeral pod?

**Đáp án:** Nên dùng job tạo archive có filesystem/artifact publishing phù hợp và graceful exit; file sinh trong layer ephemeral của pod có thể mất khi pod bị xóa.

## 21. Checklist dùng trước mỗi lần triển khai

- [ ] **JDK:** kiểm tra vendor/version/build, VM variant, OS/CPU architecture.
- [ ] **Đầu vào:** giữ immutable application/dependency JARs, lưu digests.
- [ ] **Classpath:** giữ đúng các JAR và thứ tự; kiểm tra same-or-prefix rule.
- [ ] **Module path:** dùng modular JARs phù hợp, tránh exploded modules trong CDS flow.
- [ ] **Module flags:** xác minh không dùng những options vô hiệu CDS theo Java 17 guide.
- [ ] **Custom loaders:** verify classes ứng dụng có thực sự được archive/share.
- [ ] **Base–top:** top dynamic phải gắn đúng base static/default và cùng runtime.
- [ ] **Archive file:** đúng path, quyền đọc, không hỏng, không bị ghi dở.
- [ ] **Build pipeline:** tạo và verify archive bằng runtime image/cấu hình production.
- [ ] **CI:** thực hiện smoke test với CDS log và application stdout.
- [ ] **Production:** ưu tiên availability và logging, không ép strict mode thiếu chiến lược fallback.
- [ ] **Observability:** lưu lý do CDS reject/skip và class load sources.
- [ ] **Benchmark:** so no-CDS/default/custom, cùng môi trường và metric.
- [ ] **Multi-arch:** sinh/check artifact phù hợp từng architecture.
- [ ] **Rebuild trigger:** thay JDK/JAR/modules/classpath ⇒ regenerate và test.
- [ ] **Cache khác:** áp dụng version/config/integrity contract riêng theo provider.

## 22. Tổng kết

```text
                       CÓ ARCHIVE / CACHE
                              │
                              ▼
                 XÁC ĐỊNH ĐÚNG LOẠI
                ┌─────────────┼──────────────┐
                │             │              │
             CDS/AppCDS     JIT Code       Framework cache
               .jsa          Cache          / snapshot
                │             │              │
                ▼             ▼              ▼
       JVM build + jars   Runtime-only    Provider-specific
       paths + VM flags   compiled code   compatibility key
       + base/top + OS         │              │
                │             │              │
                └─────────────┴──────────────┘
                              │
                              ▼
                VALIDATE IN TARGET RUNTIME
                              │
             ┌────────────────┴───────────────┐
             │                                │
         COMPATIBLE                       INCOMPATIBLE
             │                                │
             ▼                                ▼
     Share archived class data         Fallback / fail / rebuild
             │                                │
             └────────────────┬───────────────┘
                              ▼
                  SAME JAVA SEMANTICS
                              │
                              ▼
       VERIFY logs và đo startup/footprint thật
```

**Kết luận:** Archive hoặc cache khởi động nhanh **chỉ có giá trị khi đầu vào và runtime vẫn phù hợp với giả định lúc tạo**. Với **CDS/AppCDS Java 17**, kiểm tra **JDK/HotSpot build, OS/architecture, application JARs, classpath prefix/order, module constraints, base–dynamic archive và các JVM flags liên quan**. **`-Xshare:auto`** phù hợp hơn cho một service cần availability; **`-Xshare:on`** hữu ích để fail-fast trong kiểm thử nhưng có thể làm production không khởi động. Luôn kiểm tra `cds`/`class+load` logs để chứng minh archive thực sự được dùng; sau đó mới đo startup/footprint. **Đừng áp các quy tắc của CDS lên JIT Code Cache, framework caches hay cơ chế AOT của JDK khác**.

### Tài liệu tham khảo chính thức

- [Oracle JDK 17 — `java` launcher, Application Class Data Sharing](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): classpath/module rules, static/dynamic archive, base/top syntax, `Xshare` modes, `jcmd`.
- [Oracle Java SE 17 — Class Data Sharing](https://docs.oracle.com/en/java/javase/17/vm/class-data-sharing.html): default archive, dynamic archive, memory/startup goals, availability notes.
- [JEP 310 — Application Class-Data Sharing](https://openjdk.org/jeps/310): cơ chế mở rộng CDS sang ứng dụng.
- [JEP 341 — Default CDS Archives](https://openjdk.org/jeps/341): default archive build cùng JDK.
- [JEP 350 — Dynamic CDS Archives](https://openjdk.org/jeps/350): dynamic top archive và nguyên tắc sử dụng.
- [Oracle JDK 17 — `jcmd` reference](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): diagnostic commands trong môi trường có attach.
- [Oracle Java SE 17 — JVMS Chapter 4](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): Java class-file format khác với binary CDS archive.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fast-startup-007">Phân biệt AOT cache của JVM với biên dịch thành chương trình native</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt AOT cache của JVM với biên dịch thành chương trình native

## 1. Vì sao có hai cách đều được gọi là “AOT”?

Trong Java, **Ahead-of-Time (AOT)** có nghĩa rộng là *chuẩn bị trước một số thông tin hoặc mã thực thi* thay vì đợi tới lúc ứng dụng cần chúng. Nhưng **AOT không phải tên của một định dạng file duy nhất**, và cụm “đã AOT” chưa đủ để biết ứng dụng **có cần JVM để chạy hay không**.

Hai cơ chế cần phân biệt trong bài này:

1. **JVM AOT cache (HotSpot / Project Leyden):** chạy ứng dụng Java trên **JVM bình thường**, nhưng dùng dữ liệu đã được ghi trước từ các lần chuẩn bị/training. Trên **JDK 25**, AOT cache có thể lưu class-loading/linking data, những đối tượng phù hợp và **method profiles** để cải thiện startup/warmup. Ứng dụng vẫn chạy qua `java`, sử dụng JVM runtime, garbage collector và có thể dùng **JIT compiler**.
2. **Native executable (ví dụ GraalVM Native Image):** dùng build tool để phân tích bytecode, xác định các thành phần cần dùng và **biên dịch chương trình thành tệp thực thi native** cho một OS/CPU target. Khi triển khai, thường chạy tệp `app-native` / `app-native.exe` **trực tiếp**, không khởi động HotSpot bằng lệnh `java -jar ...`. Executable vẫn chứa **những phần Java runtime cần thiết**, chứ không phải Java semantics biến mất.

**Một câu để nhớ:** AOT cache = **chuẩn bị cho JVM chạy ứng dụng hiệu quả hơn**; Native Image = **biên dịch và đóng gói ứng dụng thành executable native**. Cả hai hướng có thể cải thiện khởi động, nhưng khác nhau về **artifact, runtime, dynamic capabilities, build cost, portability và cơ chế tối ưu về sau**.

### Cảnh báo quan trọng về tên gọi

- File `.aot` của HotSpot JDK 25 **không phải** chương trình native chạy trực tiếp.
- File `.jsa` của CDS/AppCDS cũng **không phải** native executable.
- File `.class` và `.jar` thông thường chứa **bytecode Java**, không phải binary native.
- Tool `native-image` trong GraalVM **không phải** tùy chọn đồng nghĩa với `-XX:AOTCacheOutput` của HotSpot.
- Từ “AOT” của **Spring AOT processing** thường chỉ code generation/analysis phục vụ các đường triển khai, **không tự động** tạo native executable nếu chưa chạy Native Image build.

## 2. Ba con đường từ Java source tới chương trình chạy

### 2.1. Java/JVM thông thường

~~~text
StartupCompare.java
      |
      | javac
      v
StartupCompare.class   ->  startup-compare.jar
      |
      | java -jar startup-compare.jar
      v
HotSpot JVM
  - load/link/init classes theo Java rules
  - interpreter thực thi bytecode
  - profiling/JIT compile hot code khi phù hợp
  - GC và Java runtime hoạt động
      |
      v
Application result
~~~

Ở chế độ này, code có thể đi từ interpreter sang C1/C2 (hoặc compiler khác tùy cấu hình), rồi tới native machine code **tạo trong runtime**. JIT compilation không đổi định dạng `.jar` ban đầu.

### 2.2. JVM có AOT cache

~~~text
Build Java classes/JAR
      |
      v
JDK 25 training run (Java application thật)
      |
      | record classes, metadata, method profiles phù hợp
      v
AOT configuration/training information
      |
      | assembly
      v
startup-compare.aot   (AOT cache artifact)
      |
      | java -XX:AOTCache=startup-compare.aot -jar ...
      v
HotSpot JVM vẫn chạy:
  - đọc/map AOT cache tương thích
  - tái sử dụng dữ liệu classes/linking/profiles phù hợp
  - interpreter/JIT vẫn có vai trò trong runtime
  - GC và Java runtime vẫn có vai trò
      |
      v
Application result
~~~

**Cache không thay thế JVM**. Nó giúp VM có “bài chuẩn bị” từ trước; phần ứng dụng, JVM và JDK vẫn phải phù hợp với cache.

### 2.3. Native Image

~~~text
Build Java classes/JAR
      |
      | native-image tool
      v
Reachability analysis (closed-world model)
  - xác định reachable classes/methods/fields
  - xử lý metadata cho reflection/resources/JNI khi cần
  - các class initialization decisions
      |
      v
Ahead-of-time compilation + native linking
      |
      v
startup-compare-native[.exe]
  - application native code
  - những phần Java runtime cần thiết
  - GC/threading/safepoint support tương ứng
      |
      | chạy executable trực tiếp
      v
Application result
~~~

**Native Image không có nghĩa “không còn runtime”.** Native executable vẫn có logic/runtime hỗ trợ memory management, class metadata và threading theo cơ chế Native Image; nó chỉ **không cần khởi động HotSpot JVM truyền thống** để thực hiện chương trình theo cách `java -jar`.

## 3. Bảng so sánh trọng tâm

| Đặc điểm | HotSpot JVM + AOT cache | GraalVM Native Image |
| --- | --- | --- |
| Artifact tối ưu | `.aot` (và có thể `.aotconfig` trong workflow tách bước) | Native executable như `app-native`, `app-native.exe` |
| Cách chạy | `java -XX:AOTCache=... -jar app.jar` | `./app-native` hoặc `.\app-native.exe` |
| Có chạy HotSpot JVM? | **Có** | **Không**, dùng runtime nhúng/được link cho Native Image |
| Có cần JDK/JRE tương thích lúc runtime? | **Có**, JVM và classpath/module state phù hợp | Thường không cần JVM/JDK đầy đủ ở máy triển khai; vẫn có OS/native library requirements |
| Input trước build | Java bytecode và ứng dụng chạy thử | Java bytecode, dependency graph, reachability metadata |
| Cách AOT làm việc | Training và lưu artifacts giúp JVM startup/warmup | Whole-program reachability + ahead-of-time code generation/linking |
| Java JIT khi chạy? | **Vẫn có thể dùng** JIT; JDK 25 cache profiles giúp JIT tối ưu sớm hơn | Không dùng HotSpot tiered JIT thông thường cho ứng dụng đã biên dịch |
| Dynamic class loading | JVM semantics thông thường, chịu các điều kiện dùng cache | Theo **closed-world assumption**; code cần biết ở build time |
| Reflection/proxy/resources | Hành vi JVM linh hoạt như ứng dụng thường, với các ràng buộc cache | Có thể cần reachability metadata/configuration |
| Class initialization | Phần semantics runtime được bảo toàn theo JVM rules | Có phân biệt **build-time** và **run-time initialization** |
| Compatibility | App/JDK release/OS/CPU phải tương thích cache | Native binary phụ thuộc OS/CPU và runtime native libraries cần thiết |
| Build overhead | Training + assembly; có thể dùng một flag thuận tiện JDK 25 | Static analysis và native linking thường tốn CPU/RAM/build time |
| Lợi ích chính | Giảm startup/warmup của Java VM ít thay đổi mô hình ứng dụng | Rút ngắn startup, giảm footprint runtime và bỏ yêu cầu JVM truyền thống |
| Đổi dependency sau build | Cache thường phải tái tạo, app JAR vẫn là input runtime | Phải **rebuild native executable** |
| Tooling | `java`, `jcmd`, JFR, HotSpot `-Xlog` theo phiên bản | Native Image runtime/tooling và platform profiler phù hợp |
| Khả năng điều chỉnh sau deploy | Các JVM flags/GC/JIT tiếp tục phát huy, tùy compatibility | Options/native runtime có tập hỗ trợ và hành vi khác; không áp máy móc mọi HotSpot flags |

**Không khẳng định Native Image luôn nhanh hơn mọi JVM+AOT workload.** Native có thể vượt trội ở cold start; Java HotSpot JIT vẫn có thế mạnh tối ưu runtime cho một số workloads chạy lâu, đặc biệt khi nhận nhiều runtime profiles mới. Kết luận phải dựa trên metric thực tế.

## 4. AOT cache của JDK 25 lưu cái gì?

Theo Oracle JDK 25 `java` launcher manual và các JEP thuộc Project Leyden, AOT cache được phát triển để đưa một số công việc từ **production startup** sang **training/assembly**.

### Nền tảng từ CDS

**Class Data Sharing (CDS)** từng lưu class metadata đã xử lý trước trong archive, thường có đuôi `.jsa`. Các archived classes có thể được map vào JVM processes sau đó, giảm class-loading work và đôi khi giảm footprint nhờ memory-mapped shared pages.

AOT cache từ JDK 24 mở rộng hướng này. Oracle JDK 25 nêu AOT cache là container cho artifacts có khả năng giúp khởi động/warmup: lớp đã xử lý, class loading/linking data và những objects phù hợp. **Không nên coi AOT cache là heap snapshot đầy đủ** của toàn bộ app, cũng không phải một JAR mới.

### JEP 483: Ahead-of-Time Class Loading & Linking

**JEP 483 (JDK 24)** đưa workflow **training → assembly → production** để ghi nhận/lưu một phần trạng thái class loading/linking thích hợp. Production JVM dùng những dữ liệu đã chuẩn bị nếu compatibility checks cho phép.

**Không hiểu** đây là thực thi trước toàn bộ `main`, lưu tất cả sockets/database connections hoặc khôi phục tiến trình như sau một checkpoint toàn bộ OS process.

### JEP 515: Ahead-of-Time Method Profiling

**JEP 515 (JDK 25)** đưa method profiles đã thu trong training vào AOT cache. Khi JVM start, những profiles này giúp JIT bắt đầu tối ưu với thông tin tốt hơn **thay vì phải chờ thu tất cả profile mới từ cold runtime**.

**Điểm phải phân biệt:** Lưu **JIT profiles** vào `.aot` không tương đương lưu **native executable**. JIT vẫn có thể sinh machine code **trong production runtime**. Điều này khác với Native Image vốn AOT-compile reachable application code thành binary khi build.

Một số trang tài liệu lịch sử có thể còn mô tả “compiled methods và execution profiles sẽ được lưu trong cache ở tương lai”. Cần đối chiếu JDK release và **JEP 515**: JDK 25 đã có **AOT Method Profiling**, nhưng **không vì thế mà cache là một Native Image executable**.

### JEP 514: AOT Command-Line Ergonomics

JDK 25 đơn giản hóa thao tác tạo cache qua `-XX:AOTCacheOutput=...`, giúp một lệnh thực hiện **training** rồi khởi chạy assembly phase thích hợp để sinh cache. Tài liệu vẫn mô tả workflow tách **record/create/on** cho automation chi tiết.

## 5. Các artifacts của JVM AOT cache

| Artifact | Vai trò | Tự chạy được không? |
| --- | --- | --- |
| `.java` | Source Java | Không phải native executable |
| `.class` | JVM bytecode | Dùng Java launcher/JVM phù hợp |
| `.jar` | Đóng gói classes/resources | Thông thường dùng `java -jar` |
| `.classlist` | Class list phục vụ CDS workflow truyền thống | Không |
| `.jsa` | CDS/AppCDS archive | **Không**, dùng với JVM |
| `.aotconfig` | Dữ liệu configuration/training của JDK 25 AOT workflow | **Không** |
| `.aot` | Cache AOT artifacts tiêu chuẩn trên JDK 25 | **Không**, JVM đọc bằng `-XX:AOTCache` |
| `app-native` / `app-native.exe` | Native Image executable theo target OS/CPU | **Có**, chạy trực tiếp khi native runtime dependencies được thỏa |

Không đổi phần mở rộng từ `.aot` thành `.exe` để biến nó thành program. Cũng không nói `.aot` là file chứa những instruction CPU hoàn chỉnh để bỏ HotSpot.

## 6. Chương trình Java 17+ dùng để so sánh hai hướng

Tạo file **`StartupCompare.java`**:

~~~java
public final class StartupCompare {
    private StartupCompare() {
    }

    static int sumTo(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += i;
        }
        return sum;
    }

    static String message(String name) {
        return "Hello, " + name;
    }

    public static void main(String[] args) {
        System.out.println("message=" + message("AOT"));
        System.out.println("sum=" + sumTo(10));
    }
}
~~~

Chương trình **không cần library ngoài**, không có I/O file/network, không phụ thuộc thời gian, không dùng non-deterministic random hoặc thread scheduling.

Biên dịch bằng **Java 17+**:

~~~bash
javac -g StartupCompare.java
java StartupCompare
~~~

**Stdout xác định khi chạy thành công, không bật VM diagnostic logs:**

~~~text
message=Hello, AOT
sum=55
~~~

Bởi `sumTo(10)` cộng `1+2+...+10 = 55`. Giá trị này giữ nguyên khi chạy bằng HotSpot không cache, HotSpot có cache hoặc Native Image đã biên dịch thành công.

### Đóng JAR để thí nghiệm AOT cache

Tạo thư mục clean cho artifact, nếu bạn muốn không dùng class directory trong các command tiếp theo.

**Linux/macOS hoặc PowerShell (các lệnh Java/JAR này dùng được ở cả hai):**

~~~bash
javac -d classes StartupCompare.java
jar --create --file startup-compare.jar --main-class StartupCompare -C classes .
java -jar startup-compare.jar
~~~

**Trước đó cần tạo thư mục `classes`**:

- Linux/macOS: chạy `mkdir -p classes`.
- Windows PowerShell: chạy `New-Item -ItemType Directory -Force classes | Out-Null`.

Đầu ra chương trình vẫn hai dòng trên. Trong JAR có `StartupCompare.class` và manifest ghi `Main-Class`. **Giữ nguyên bytes của JAR** trong quá trình tạo/dùng cache để tránh mismatch.

**Lưu ý:** lệnh `javac -d classes` yêu cầu thư mục `classes` tồn tại; bước tạo thư mục là một phần bắt buộc của lab, không phải giả định tool tự tạo root directory.

## 7. Bài lab A — tạo AOT cache trên HotSpot JDK 25

Những lệnh ở phần này yêu cầu **HotSpot JDK 25** hoặc JVM/JDK khác tài liệu hóa đầy đủ cùng options. **Chúng không chạy trên JDK 17 chỉ vì mã nguồn Java tương thích Java 17.**

### Bước A1. Xác nhận JDK đang chạy

~~~bash
java -version
java -Xlog:help
~~~

Kiểm tra output cho thấy **JDK 25 HotSpot**. Nếu trong một hệ thống có nhiều `java`/`javac` trên PATH, phải làm rõ JDK dùng để tạo `.aot` và JVM sẽ dùng cache trong runtime.

**Không cố bật flags AOT trên Java 17 rồi kết luận tính năng lỗi** khi JVM báo `Unrecognized VM option`. JDK 17 có CDS/AppCDS nhưng chưa có workflow AOT Cache JDK 25 này.

### Bước A2. Chạy baseline không có AOT cache

~~~bash
java -XX:AOTMode=off -jar startup-compare.jar
~~~

**Stdout ứng dụng:**

~~~text
message=Hello, AOT
sum=55
~~~

`-XX:AOTMode=off` vô hiệu hóa sử dụng AOT cache theo semantics Oracle JDK 25. Để so sánh performance cần đo riêng nhiều process starts; ở bước này chỉ thiết lập baseline chức năng.

### Bước A3. Training + assembly qua một lệnh

~~~bash
java -XX:AOTCacheOutput=startup-compare.aot -jar startup-compare.jar
~~~

Ở JDK 25, chế độ mặc định `AOTMode=auto` kết hợp `AOTCacheOutput` làm một **training execution** và một **assembly JVM process** phù hợp để ghi artifacts. **Đừng diễn giải command này là “đã compile JAR thành native exe”.**

Đầu ra ứng dụng từ training run vẫn là:

~~~text
message=Hello, AOT
sum=55
~~~

Các diagnostic/build messages của JVM (nếu có) có thể xuất hiện ngoài hai dòng. Sau lệnh, kiểm tra artifact:

**Linux/macOS:**

~~~bash
ls -lh startup-compare.aot
~~~

**Windows PowerShell:**

~~~powershell
Get-Item .\startup-compare.aot
~~~

Nếu file không được tạo, xem exit code/log và tính tương thích của VM flags; không đi thẳng sang kết luận đã có cache hợp lệ.

### Bước A4. Chạy lại ứng dụng với cache

~~~bash
java -XX:AOTCache=startup-compare.aot -jar startup-compare.jar
~~~

**Stdout ứng dụng vẫn là:**

~~~text
message=Hello, AOT
sum=55
~~~

Đây là **production run của JVM**, không phải chạy file `startup-compare.aot` trực tiếp.

### Bước A5. Xác nhận JVM có sử dụng AOT cache

~~~bash
java -XX:AOTCache=startup-compare.aot -Xlog:aot=info -jar startup-compare.jar
~~~

Lúc này VM có thể in **AOT diagnostic logs** liên quan cache. Hãy kiểm tra những thông tin về **cache load / mapping / validation / rejection** mà JDK thực tế xuất ra; log format, exact text và timing thay đổi theo release/build. Để xem tag/level hợp lệ, dùng `java -Xlog:help`.

**Quan trọng:** một file `.aot` tồn tại trên disk **không chứng minh** runtime đã map/đọc thành công nó. Trong chế độ automatic, JVM có thể fallback hoặc không dùng vì cache không tương thích.

### Bước A6. Kiểm tra nghiêm ngặt trong môi trường test

~~~bash
java -XX:AOTMode=on -XX:AOTCache=startup-compare.aot -Xlog:aot=info -jar startup-compare.jar
~~~

`AOTMode=on` là chế độ yêu cầu dùng AOT cache theo options; nó hữu ích khi chẩn đoán lỗi compatibility. **Không dùng chế độ strict một cách mù quáng trong production**: nếu cache thiếu/không hợp lệ, JVM có thể exit thay vì fallback.

Nếu JVM từ chối cache, **không báo thí nghiệm thành công chỉ vì app vẫn chạy ở mode khác**. Đọc AOT logs, xác nhận đúng path/JDK/JAR/options.

## 8. Workflow ba giai đoạn tách riêng trên JDK 25

Tài liệu Oracle còn định nghĩa các modes **`record` → `create` → `on`** để kiểm soát rõ từng bước.

### Training / record

~~~bash
java -XX:AOTMode=record -XX:AOTConfiguration=startup-compare.aotconfig -jar startup-compare.jar
~~~

Process chạy chương trình để ghi thống kê/dữ liệu configuration thích hợp. File `startup-compare.aotconfig` **không phải** program và **chưa phải** final `.aot`.

### Assembly / create

~~~bash
java -XX:AOTMode=create -XX:AOTConfiguration=startup-compare.aotconfig -XX:AOTCache=startup-compare.aot -jar startup-compare.jar
~~~

Theo Oracle JDK 25, **create mode thực hiện assembly, không chạy ứng dụng `main`**. Nếu thao tác thành công, tạo `startup-compare.aot`.

### Production / on

~~~bash
java -XX:AOTMode=on -XX:AOTCache=startup-compare.aot -jar startup-compare.jar
~~~

Ứng dụng chạy trên **HotSpot JVM với cache**. Ba giai đoạn được tách phù hợp cho build pipeline, image build và deployment.

### Không trộn tùy tiện các AOT options

Oracle JDK 25 đặt constraints:

- `-XX:AOTCacheOutput=...` là lựa chọn output, không được dùng đồng thời với `-XX:AOTCache=...`.
- `-XX:AOTConfiguration=...` ghi ở `record`, đọc ở `create` theo mode.
- `-XX:AOTMode=off` bỏ qua cache; `auto` mặc định quyết định record/on/off theo options.
- `-XX:AOTMode=create` không đồng nghĩa gọi native-image; chỉ **assemble cache**.
- AOT cache cần **app/JDK/OS/CPU compatibility**.

**Nếu bước create báo lỗi, giữ nguyên dữ liệu training và đọc log**; đừng thay các flags cho tới khi “không còn lỗi” rồi khẳng định vẫn là cùng thí nghiệm.

## 9. Cache không tương thích: một giới hạn thực tế quan trọng

Theo Oracle JDK 25, AOT cache là artifact cho tổ hợp cụ thể:

1. **Ứng dụng** như biểu diễn qua `-classpath`, `-jar` hay `--module-path` và code/resources.
2. **Một JDK release/build phù hợp**.
3. **OS và CPU architecture**.

Nếu thay đổi thành phần này, phải **tạo lại AOT cache**. Một số JVM options, JVMTI agents hoặc điều kiện runtime khác có thể khiến cache không được dùng. Đừng giả định archive dùng được trên mọi node chỉ vì cùng tên file.

| Thay đổi sau training | Điều nên làm |
| --- | --- |
| Đổi JDK 25 build/release | Recreate cache |
| Rebuild JAR/dependency | Recreate cache |
| Đổi architecture x86-64 ↔ ARM64 | Recreate cache |
| Chuyển OS Windows ↔ Linux | Recreate cache |
| Thêm agent/VM options ảnh hưởng loading | Kiểm tra compatibility; có thể cần recreate |
| Đổi workload production mạnh so với training | Cache có thể thiếu profiles/classes hữu ích; retrain |
| Đổi JVM vendor | Kiểm tra hỗ trợ option, không mặc định portable |

**Không xem `.aot` như artifact độc lập tương đương source JAR.** Build/release pipeline nên quản lý **cache cùng JDK/container image và application version**, không chia sẻ rộng giữa các runtime khác nhau.

### Dữ liệu training phải đại diện

Nếu app production dùng một hot route nhưng training chỉ in `Hello` một lần, cache có thể không chứa những profiles quan trọng. Việc training không làm Java methods cố định vĩnh viễn; HotSpot production vẫn có thể profile và JIT theo workload runtime.

**Một cache hợp lệ nhưng chưa có profile tối ưu không phải cache lỗi.** Tính hữu ích phụ thuộc những code paths đã load/chạy trong training và khả năng áp dụng artifacts khi production.

## 10. Bài lab B — biên dịch cùng JAR thành native executable

Để làm phần này cần **GraalVM distribution có Native Image tool** cùng native compiler/linker toolchain phù hợp, ví dụ build tools trên Linux/macOS hoặc Visual Studio Build Tools trên Windows theo hướng dẫn của bản GraalVM bạn dùng.

**Không đòi `native-image` phải tồn tại trong một OpenJDK HotSpot JDK bình thường.** Đây là công cụ phụ thuộc distribution/installation.

### Bước B1. Kiểm tra công cụ

~~~bash
java -version
native-image --version
~~~

Nếu shell báo command not found, cài đúng GraalVM Native Image/toolchain theo tài liệu của bản đó. **Không tạo file `.exe` giả** hoặc rename `.jar` để “qua bước này”.

### Bước B2. Biên dịch native executable

Từ thư mục có `startup-compare.jar`:

~~~bash
native-image -jar startup-compare.jar startup-compare-native
~~~

Đây là **lệnh Native Image build**, không phải production execution. Build tool phân tích reachable code, compile/link native code và tạo executable cho OS/CPU build target.

- Trên Linux/macOS, output thường là `startup-compare-native` (dạng executable).
- Trên Windows, output thường là `startup-compare-native.exe`.
- Native Image build **có thể mất nhiều thời gian và cần nhiều CPU/RAM**, kể cả chương trình nhỏ so với `javac`.
- Những libraries phức tạp có reflection/proxy/JNI/resources có thể cần reachability metadata; chương trình lab đơn giản không cần cấu hình đặc biệt.

**Không cam kết mọi version của `native-image` cho cùng file size hay build log.** Kiểm tra `native-image --help` và docs đúng bản nếu command-line options thay đổi.

### Bước B3. Chạy output trực tiếp

**Linux/macOS:**

~~~bash
./startup-compare-native
~~~

**Windows PowerShell:**

~~~powershell
.\startup-compare-native.exe
~~~

**Stdout chương trình khi native build/run thành công:**

~~~text
message=Hello, AOT
sum=55
~~~

**Khác biệt nhìn thấy được:** không có `java -jar`, `-XX:AOTCache` hay JDK command để chạy program. Native binary vẫn phụ thuộc hệ điều hành/architecture và có thể phụ thuộc những system libraries; “standalone” không đồng nghĩa “chạy được mọi OS không cần gì khác”.

### Bước B4. Đừng dùng cùng tên “AOT artifact” cho hai file

~~~text
HotSpot:
  startup-compare.jar + startup-compare.aot + compatible JDK
                 |
                 v
        java -XX:AOTCache=... -jar ...

Native Image:
  startup-compare-native[.exe] + native OS/runtime prerequisites
                 |
                 v
          ./startup-compare-native
~~~

**Native executable không dùng HotSpot `.aot` như một “plugin cache” mặc định.** Hai build pipelines độc lập dù cùng bắt đầu từ Java bytecode.

## 11. Native Image và “closed-world assumption”

GraalVM Native Image dựa vào **static reachability analysis** từ entry points: compiler phải biết những classes/methods/fields cần thiết để đưa vào executable. Nếu ứng dụng dùng dynamic behavior mà static analysis không nhìn thấy đủ, phải bổ sung **reachability metadata** thích hợp.

Những trường hợp cần đặc biệt chú ý:

- `Class.forName(...)` với tên class tạo từ runtime config.
- Reflection tìm constructors/methods/fields theo chuỗi từ input.
- Dynamic proxies.
- JNI libraries và foreign/native integrations.
- Resources lấy từ classpath bằng tên runtime.
- Serialization frameworks hoặc libraries tạo adapters/reflection.
- Framework dependency injection, runtime scanning và generated classes.

**Không diễn giải closed-world là “Native Image không hỗ trợ reflection”.** Nó hỗ trợ theo những điều kiện khác Java HotSpot linh hoạt: phần class/method/field được dùng cần known/reachable/configured ở build time phù hợp. GraalVM có tooling và reachability metadata repository hỗ trợ nhiều libraries.

### Ví dụ minh họa reflection (không phải file chạy độc lập)

~~~java
String typeName = readNameFromConfig();
Class<?> type = Class.forName(typeName);
Object object = type.getDeclaredConstructor().newInstance();
~~~

Đoạn này là **pseudo-context Java**: `readNameFromConfig()` không được định nghĩa nên không thể biên dịch độc lập. Trên JVM, classpath ở runtime có thể chứa một implementation bổ sung phù hợp. Với Native Image, loại class sẽ được reflective loading phải được build tool biết hoặc có metadata đúng; việc thêm JAR mới sau khi native executable đã build không tự mở rộng closed-world code set.

**Giới hạn của ví dụ:** dynamic library loading/JNI trong Native Image có một số hỗ trợ riêng, nhưng không có quyền biến một binary đã đóng gói thành một JVM classloader cho mọi bytecode plugin tùy ý.

## 12. Class initialization: khác biệt tinh tế nhưng rất quan trọng

**JVM+AOT cache** vẫn chạy trên JVM và giữ class-loading/linking/initialization semantics theo các điều kiện tài liệu, nhằm không để training thực thi các side effects tùy ý rồi làm ứng dụng production sai.

**GraalVM Native Image** phân biệt:

- **Build-time initialization:** class static initializer có thể được thực thi khi tạo native image và state thích hợp được ghi vào **image heap** trong binary.
- **Run-time initialization:** class được initialization khi native executable chạy.

Một class chứa:

~~~java
final class Settings {
    static final String ENV = System.getenv("APP_ENV");
}
~~~

Đây là declaration minh họa; không có `main`.

Nếu một class được **build-time initialized**, việc đọc environment variable có thể bị “đóng băng” theo build environment. Điều đó **có thể làm production thấy giá trị của build-time env** nếu bạn cấu hình không đúng.

GraalVM Native Image cung cấp options như:

~~~bash
native-image --initialize-at-run-time=Settings -jar startup-compare.jar startup-compare-native
~~~

Đây là **lệnh ví dụ về option**, không phải yêu cầu chạy ngay với JAR lab vì lab **không có class `Settings`**. Nếu áp dụng trong ứng dụng thật, thay class name bằng tên đầy đủ của class có static initialization thích hợp và kiểm tra compiler/version docs.

**Bài học:** không tùy tiện cho crypto providers, random seeds, time-dependent config, system properties hoặc environment-derived state chạy ở **build time**. Với Native Image, class initialization policy là một phần rất quan trọng của correctness và reproducibility.

### Khác với AOT cache snapshot

Không coi HotSpot AOT cache là **Native Image image heap** hay ngược lại. Chúng khác dữ liệu được phép lưu, lifecycle và runtime compatibility. **Không có ngụ ý** một cache `.aot` lưu toàn bộ Java heap của training run rồi khôi phục object identities của user sessions production.

## 13. Runtime, GC, JIT và flags khác nhau thế nào?

| Vấn đề | HotSpot + AOT cache | Native Image |
| --- | --- | --- |
| Launcher | `java` | Executable native |
| JIT compiler | Có thể tiếp tục C1/C2/tiered compilation | Không có HotSpot tiered JIT thông dụng trong native runtime |
| Class data | JVM có thể map AOT cache | Nhiều class/runtime artifacts đã được linked vào binary |
| GC | HotSpot GC đã chọn (G1, v.v.) | Native Image GC implementation/configuration |
| Heap flags | JVM flags như `-Xmx` | Native Image options có thể khác, kiểm tra `--help`/docs |
| JVM diagnostic flags | `-Xlog:aot`, `-Xlog:class+load`, JFR/JVM tools | Native-image-specific runtime/diagnostics; không dùng nguyên bộ `-XX` HotSpot |
| Runtime reflection | JVM semantics đầy đủ theo classpath/module rules | Thường bị ràng buộc reachability metadata |
| Runtime code discovery | JVM classloading có thể load JARs theo APIs/cấu hình | Closed-world model, không tự nạp bytecode chưa có khi build |

**Native Image vẫn có garbage collection** trong phần runtime Java cần thiết. Câu “Native Image không chạy JVM nên không còn GC” là sai. Nó không sử dụng HotSpot GC/JIT theo cách của `java`, chứ không bỏ memory management.

### JIT trên HotSpot có thể thắng ở workload dài

JDK 25 AOT cache giúp HotSpot JIT khởi đầu thuận lợi hơn nhờ profiles training, **nhưng vẫn có thể tiếp tục quan sát runtime workload và recompilation**. Native Image thường đặt trọng tâm giảm runtime overhead/startup và đóng gói tối ưu theo build-time knowledge. Với một số throughput-heavy long-running workloads, JIT adaptive optimization có thể lợi thế; **không có một người thắng duy nhất**.

## 14. Cách so sánh startup và throughput đúng

**Hai dòng `message=...` và `sum=55` cho biết cùng semantics**, nhưng không chứng minh AOT cache/native image nhanh hơn bao nhiêu.

Các metric cần phân biệt:

| Metric | Ý nghĩa | Cách kiểm chứng |
| --- | --- | --- |
| **Build time** | Thời gian/công sức tạo JAR, cache hoặc native binary | Đo build pipeline |
| **Cold start** | Từ process launch đến ready/first useful output | Đo end-to-end nhiều process starts |
| **Warmup** | Thời gian tới steady throughput | Benchmark/chạy tải theo giai đoạn |
| **Steady-state throughput** | Số requests/operations hoàn thành mỗi đơn vị thời gian | Workload đủ lâu và lặp lại |
| **Tail latency** | P95/P99 response times | Load testing với concurrency thực |
| **RSS/native memory** | Tổng memory process theo OS | Process/container metrics |
| **Java heap/live-set** | Java objects reachable + heap sizing | JVM/native-runtime-specific diagnostics |
| **Binary/artifact size** | Tổng file deploy và dependency footprint | Filesystem/container image inspection |
| **Operational effort** | Rebuild/training metadata, toolchains, flags và portability | Theo build/release pipeline |

### Đối chứng JVM không cache và có cache

~~~bash
java -XX:AOTMode=off -jar startup-compare.jar
java -XX:AOTCache=startup-compare.aot -jar startup-compare.jar
~~~

Hai lệnh này cần **JDK 25**. Trong phép đo performance thật, xác minh trước cache được load bằng `-Xlog:aot`, rồi đo **không bật verbose logs** để tránh méo kết quả.

### Đối chứng native

~~~bash
./startup-compare-native
~~~

Lệnh trên dành cho POSIX OS với binary đã build thành công. Trên Windows dùng `.\startup-compare-native.exe`.

**Không thực hiện một lần chạy mỗi mode và tuyên bố “Native nhanh hơn 100 lần”.** OS file cache, antivirus, page faults, JVM ergonomics, CPU frequency, container startup, app dependencies và I/O có thể che hoặc khuếch đại khác biệt.

### Khuyến nghị đo

1. Dùng **cùng business logic và inputs**.
2. Tách **build time** khỏi runtime.
3. Nhiều process starts, có thống kê median/P95.
4. Workload đủ thật để xét JIT steady-state.
5. Đo CPU, memory, peak RSS và GC bên cạnh latency.
6. Ghi rõ JDK/GraalVM versions, target OS/CPU, JVM/native flags.
7. Tách I/O/database/network latency khỏi Java startup khi cần.
8. Ghi lại native compiler/toolchain/size của binary và deployment image.
9. Không ép flags JVM sang Native Image khi chúng không được hỗ trợ.
10. Tái benchmark mỗi khi thay version/runtime/CPU.

**Kết quả tùy application.** Một CLI/serverless function ưu tiên cold-start có thể được Native Image cải thiện nhiều; một server chạy hàng tuần, có dynamic plugins và JIT-beneficial workload có thể thích hợp HotSpot+AOT hơn. Đây là **giả thuyết kiến trúc**, không phải số đo cho ứng dụng cụ thể.

## 15. Triển khai trên container và máy khác

### HotSpot + cache

Ví dụ deployment cần:

~~~text
container/runtime:
  compatible JDK/JVM
  startup-compare.jar
  startup-compare.aot
  correct entry command:
    java -XX:AOTCache=startup-compare.aot -jar startup-compare.jar
~~~

Bạn **không nên** copy `.aot` tạo trên OS/CPU/JDK khác rồi mặc định compatible. Với image build, training và production workload phải đủ đại diện; nếu pipeline build trên x86-64 mà production chạy ARM64, cần tái tạo cache phù hợp thay vì rename.

### Native Image

~~~text
container/runtime:
  startup-compare-native
  native runtime libraries cần thiết cho target
  command:
    ./startup-compare-native
~~~

Binary có thể giúp giảm deployment size so với nguyên JDK, nhưng không phải lúc nào container image nhỏ nhất cũng là native: base image/system dependencies, debug symbols và static/dynamic linking choices ảnh hưởng nhiều.

Native executable tạo trên Linux **không chạy trực tiếp trên Windows** như một portable JAR. Thường phải cross-compile/có toolchain phù hợp hoặc build trên target OS/architecture. Java `.jar` là bytecode portable theo điều kiện version/dependencies; file `.aot` lại có compatibility constraints nghiêm ngặt riêng.

## 16. Quy trình chọn giải pháp

### Khi nên ưu tiên JVM + AOT cache

- Đang triển khai trên HotSpot JDK có hỗ trợ cache.
- Muốn cải thiện **startup/warmup** nhưng giữ Java runtime/JIT/classloading linh hoạt.
- Có plugin/reflection-heavy libraries và không muốn closed-world metadata phức tạp.
- Có những endpoints hot thay đổi theo tải production, nên JIT adaptive optimization có giá trị.
- Đã có stack toolchain của JDK/JFR/JMX/GC/tuning.
- Deployment luôn đi cùng JDK image và có pipeline training tái lập được.

### Khi nên thử Native Image

- Cold startup và low idle RSS cực kỳ quan trọng.
- Ứng dụng có dependency graph ổn định, khả năng kiểm soát reflection/resources.
- Sẵn sàng đầu tư build time và native toolchain.
- Có khả năng test build-time class init, locale/timezone/resources/certificates/JNI.
- Deployment theo OS/CPU target cụ thể, chấp nhận rebuild cho platform khác.
- Đã có framework/tooling hỗ trợ Native Image metadata tốt.

### Khi chưa nên kết luận

Nếu ứng dụng có classloading động rất mạnh, runtime-generated bytecode hoặc native libraries phức tạp, đừng mặc định Native Image không làm được, nhưng phải **kiểm tra compatibility metadata và build limitations** thật. Ngược lại nếu mục tiêu chỉ giảm JVM cold start, không mặc định phải đổi cả runtime sang Native Image khi HotSpot AOT cache có thể đủ.

## 17. Các câu hỏi quan trọng khi thiết kế pipeline

**17.1. Training AOT cache có cần dữ liệu production thật không?**  
Không nhất thiết. Nên dùng **representative workload** với dữ liệu giả phù hợp, đủ chạm những classes/methods quan trọng mà không rò rỉ secrets. Training sẽ chọn artifacts theo behavior, nên workload nghèo nàn làm cache ít hữu ích.

**17.2. Cache có tự cập nhật sau khi JVM chạy với tải mới không?**  
Không coi file `.aot` đã tạo là một file continuously refreshed chỉ vì JIT tiếp tục profile trong production. Nếu muốn cache cập nhật, cần quy trình **retrain/reassemble** theo công cụ/version. JVM có thể JIT code mới bình thường dù cache không ghi lại các profiles mới.

**17.3. Native binary có thể dùng file config khác lúc chạy không?**  
Có thể, nếu application/runtime thiết kế đọc config tại runtime. **Closed-world** ràng buộc **code/class reachability** chứ không nói mọi dữ liệu runtime bị đóng cứng; nhưng class initialization lúc build và resources được đóng gói có thể làm một số giá trị khác biệt.

**17.4. Dùng JVM+AOT có mất tính tương thích JPMS không?**  
Không. Ứng dụng vẫn có classpath/module path và JVM semantics; tuy nhiên cache có compatibility conditions với application/modules/options. Với Native Image, analysis/linking cũng phải xử lý module/library use theo toolchain.

**17.5. Native Image có thể dùng JFR/JMX như HotSpot không?**  
Không nên mặc định đầy đủ cùng một bộ công cụ/flags. GraalVM Native Image có những capabilities riêng (có thể bao gồm monitoring/profiling features tùy edition/version), nên kiểm tra **tài liệu đúng distribution**, không dùng HotSpot option list như hợp đồng.

**17.6. Cái nào “AOT compile machine code” rõ nghĩa hơn?**  
**Native Image** rõ ràng compile reachable application bytecode thành **native executable**. **JDK 25 AOT cache** tập trung preloading/linking/profiles, và JIT sinh optimized machine code trong runtime; đừng nói chỉ cần `AOTCacheOutput` là đã native compile toàn bộ ứng dụng.

## 18. Troubleshooting theo triệu chứng

| Triệu chứng | Loại | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- | --- |
| `Unrecognized VM option 'AOTCacheOutput'` | JVM cache | JDK 17/21 hoặc non-supporting VM | Kiểm tra `java -version`; dùng JDK 25 tương thích |
| `.aot` không xuất hiện | JVM cache | Training/assembly lỗi, permissions | Kiểm tra exit code và AOT logs |
| App chạy nhưng cache không dùng | JVM cache | Mismatch JDK/JAR/OS/CPU/options | `-Xlog:aot`; test strict `AOTMode=on` |
| Startup không nhanh hơn rõ | JVM cache | Workload quá nhỏ, profiles/training thiếu | Đo nhiều process starts và workload đại diện |
| Cùng cache có node chạy node lỗi | JVM cache | Different JDK/image/arch | Tạo cache cho đúng target |
| `native-image: command not found` | Native Image | Không có GraalVM tool trên PATH | Cài toolchain/distribution phù hợp |
| Native build thiếu native compiler | Native Image | GCC/Clang/MSVC dependencies | Cài OS-specific build tools theo docs |
| Native build thiếu reflection/proxy metadata | Native Image | Closed-world analysis không biết dynamic code | Bổ sung reachability metadata |
| Native binary có config sai | Native Image | Static initialization đã chạy ở build time | Chọn run-time init / đọc config runtime |
| Native app không chạy máy khác | Native Image | OS/CPU/libs mismatch | Build đúng target và kiểm tra system libraries |
| Native build quá lâu/RAM cao | Native Image | Whole-program analysis/codegen | Điều chỉnh build resources/pipeline |
| Native app OOME | Native Image | Native runtime heap/memory policy không phù hợp | Xem native runtime diagnostics, không dùng mù HotSpot flags |

**Chẩn đoán theo loại artifact trước.** Một lỗi `AOTCacheOutput` của JDK 17 không liên quan Native Image build; một lỗi reachability của Native Image không sửa bằng `-Xlog:aot` HotSpot.

## 19. Sai lầm và hiểu nhầm thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “AOT = mọi thứ luôn được compile thành machine code trước runtime.” | AOT có nhiều loại artifacts, từ class metadata tới profile/code. |
| “`.aot` là `.exe` của Java.” | `.aot` của HotSpot là **cache**, không chạy trực tiếp. |
| “CDS `.jsa` là native executable.” | CDS archive được JVM đọc, không tự execute. |
| “Có AOT cache thì Java không cần JVM.” | **Vẫn cần HotSpot/JVM**. |
| “JDK 25 `AOTCacheOutput` tạo Native Image.” | Nó tạo JVM AOT cache, không phải native executable. |
| “Native Image không có garbage collector.” | Có Java runtime support/GC trong native binary. |
| “Native Image vẫn chạy HotSpot C1/C2 JIT như `java`.” | Nó AOT compile thành binary, runtime model khác. |
| “AOT method profile là machine code lưu sẵn.” | Profiles giúp JVM JIT compile sớm hơn, không đồng nghĩa native binary. |
| “Native Image không hỗ trợ reflection.” | Hỗ trợ tùy analysis/reachability metadata và constraints. |
| “Native Image có dynamic plugin classloading tùy ý y hệt JVM.” | Closed-world model đặt giới hạn với code chỉ được biết lúc runtime. |
| “Cứ đổi đuôi `.jar` thành `.exe` là native.” | Phải thực sự chạy Native Image compiler/linker. |
| “JAR chạy được ở mọi máy không cần Java.” | Bytecode cần runtime Java hỗ trợ version/dependencies. |
| “AOT cache portable giữa mọi JDK/OS/CPU.” | Cache phụ thuộc combination application/JDK/OS/arch. |
| “Native binary tạo trên Linux chạy thẳng trên Windows.” | Binary target-specific. |
| “Native build không cần native compiler/linker.” | Thường cần toolchain native phù hợp. |
| “AOT cache chứa toàn bộ heap state của training.” | Không phải process snapshot, chỉ artifacts được hỗ trợ. |
| “Native Image build-time init luôn an toàn.” | Có thể đóng băng env/time/resources không mong muốn. |
| “JVM+AOT không còn JIT warmup.” | Có thể giảm, nhưng JIT/adaptive optimization vẫn tồn tại. |
| “Có cache là startup chắc chắn giảm 50%.” | Phải benchmark ứng dụng thực; lợi ích tùy workload. |
| “Native Image luôn có throughput cao nhất.” | JIT có thể hiệu quả hơn cho một số workloads dài. |
| “Runtime flags HotSpot đều dùng được trên native binary.” | Hai runtime có options/capabilities khác nhau. |
| “JEP 515 thay Java source thành binary.” | JEP 515 ghi nhận AOT method profiles, không thay định dạng ứng dụng. |
| “Classpath đổi sau AOT cache không sao.” | Có thể invalidate cache; cần recreate. |
| “Native executable không phụ thuộc libc/OS.” | Có thể có system library/native runtime prerequisites. |
| “Một lần chạy benchmark đủ kết luận.” | Nhiễu hệ thống và workload cần đo nhiều lần có kiểm soát. |

## 20. Câu hỏi ôn tập có đáp án

**1.** `startup-compare.aot` có chạy trực tiếp như program không?  
**Đáp án:** Không. Nó là AOT cache của JVM, được đọc khi dùng `java -XX:AOTCache=...`.

**2.** Native Image tạo đầu ra gì?  
**Đáp án:** Native executable/shared library cho OS/CPU target, chứa compiled application code và những phần Java runtime cần thiết.

**3.** JVM có AOT cache còn dùng JIT không?  
**Đáp án:** Có thể. JDK 25 hỗ trợ AOT method profiles để JIT có dữ liệu tối ưu ngay đầu runtime.

**4.** JDK version nào trong bài có workflow `AOTCacheOutput`?  
**Đáp án:** **JDK 25** theo tài liệu ví dụ. Java source vẫn có thể biên dịch với Java 17.

**5.** Vì sao `java -jar startup-compare.jar` và `./startup-compare-native` khác nhau?  
**Đáp án:** Một lệnh khởi động JVM thực thi bytecode, một lệnh khởi động binary native đã biên dịch.

**6.** JEP 483 lưu loại công việc gì?  
**Đáp án:** AOT class-loading/linking artifacts từ training/assembly.

**7.** JEP 515 bổ sung gì ở JDK 25?  
**Đáp án:** Ahead-of-Time Method Profiling — lưu method profiles đã ghi nhận cho JVM/JIT về sau.

**8.** Vì sao cache có thể không load sau khi đổi JAR?  
**Đáp án:** Cache gắn với application/JDK/OS/CPU configuration và consistency checks.

**9.** Native Image có thể cần cấu hình reflection vì sao?  
**Đáp án:** Static reachability analysis có thể không biết những classes/methods chỉ được gọi theo tên runtime.

**10.** Class initialization ở build time Native Image có rủi ro gì?  
**Đáp án:** Có thể lưu static state phụ thuộc env/config của máy build thay vì máy production nếu cấu hình sai.

**11.** Native Image có GC không?  
**Đáp án:** Có thể dùng GC tương ứng với runtime của native binary; không phải “không còn GC”.

**12.** `-XX:AOTMode=on` dùng khi nào?  
**Đáp án:** Để yêu cầu cache và chẩn đoán compatibility trong môi trường kiểm thử có kiểm soát.

**13.** Cache tồn tại chứng minh đã sử dụng không?  
**Đáp án:** Không. Cần runtime diagnostics như `-Xlog:aot` và kiểm tra validation/mapping.

**14.** Chạy một lần Native Image nhanh hơn HotSpot có đủ kết luận không?  
**Đáp án:** Không; cần đo cold start, warmup, throughput, memory nhiều lần trong điều kiện tương đương.

## 21. Checklist tự thực hành

- [ ] Đọc `java -version`, biết rõ build nào hỗ trợ JDK 25 AOT options.
- [ ] Chạy chương trình Java 17+ và xác nhận `message=Hello, AOT` / `sum=55`.
- [ ] Biên dịch và đóng `startup-compare.jar` có Main-Class đúng.
- [ ] Dùng HotSpot JDK 25 chạy baseline `AOTMode=off`.
- [ ] Tạo `startup-compare.aot` bằng `AOTCacheOutput` hoặc record/create.
- [ ] Chạy lại `java -XX:AOTCache=... -jar startup-compare.jar`.
- [ ] Đọc `-Xlog:aot` để xác nhận cache được chấp nhận; không suy từ file tồn tại.
- [ ] Không đổi JAR/JDK/OS/CPU giữa training và production test.
- [ ] Trên GraalVM có Native Image tool, build executable từ cùng JAR.
- [ ] Chạy executable trực tiếp, không dùng `java -jar`.
- [ ] So sánh outputs để chứng minh semantics của chương trình mẫu.
- [ ] Phân biệt cache data và compiled binary trong build artifacts.
- [ ] Chỉ benchmark sau khi bảo đảm cả hai paths hoạt động đúng.
- [ ] Nếu app thực dùng reflection, đánh giá Native Image reachability metadata.
- [ ] Kiểm tra config build-time/run-time initialization trước triển khai.
- [ ] Ghi lại những ràng buộc môi trường thay vì kết luận một giải pháp luôn thắng.

## 22. Nguồn chính thức

- [Oracle JDK 25 — java Command, Ahead-of-Time Cache](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `-XX:AOTCache`, `AOTCacheOutput`, `AOTConfiguration`, `AOTMode`; training/assembly/production và compatibility constraints.
- [OpenJDK JEP 483 — Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483): cơ chế record/training AOT class loading/linking data, là nền tảng JDK 24.
- [OpenJDK JEP 514 — Ahead-of-Time Command-Line Ergonomics](https://openjdk.org/jeps/514): cải thiện commands tạo AOT cache trong JDK 25.
- [OpenJDK JEP 515 — Ahead-of-Time Method Profiling](https://openjdk.org/jeps/515): lưu method profiles để cải thiện startup/warmup JIT.
- [Inside.java — Run Into the New Year with Java's AOT Cache Optimizations](https://inside.java/2026/01/09/run-aot-cache/): minh họa workflow AOT cache và những thay đổi JDK 25.
- [Oracle GraalVM 25 — Native Image Basics](https://docs.oracle.com/en/graalvm/jdk/25/docs/reference-manual/native-image/basics/): executable model, build-time/run-time initialization, image heap và closed-world analysis.
- [GraalVM Native Image Reference Manual](https://www.graalvm.org/reference-manual/native-image/): tạo native executable với `native-image`, prerequisites và build options.
- [GraalVM Native Image — Reachability Metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/): reflection, JNI, dynamic proxy và resource metadata.
- [GraalVM Native Image — Class Initialization](https://www.graalvm.org/latest/reference-manual/native-image/basics/#build-time-vs-run-time): phân biệt build-time và run-time class initialization.
- [JVMS Java SE 25](https://docs.oracle.com/javase/specs/jvms/se25/html/index.html): Java class-file/VM semantics; không bắt buộc compiler/AOT implementation cụ thể.
- [Oracle JDK 25 — Class Data Sharing](https://docs.oracle.com/en/java/javase/25/vm/class-data-sharing.html): kiến thức nền về CDS/archive trên HotSpot.

**Kết luận:** **JVM AOT cache** và **native compilation** đều “làm trước một phần việc” nhưng **không phải cùng giải pháp**. Trên JDK 25, `.aot` giúp HotSpot chuẩn bị class-loading/linking data và method profiles, **vẫn dùng `java` và vẫn có JIT/GC/runtime Java**. Với GraalVM Native Image, build tool phân tích reachable bytecode và sinh **native executable target-specific**, chứa runtime cần thiết nhưng không phụ thuộc việc khởi động HotSpot theo mô hình `java -jar`. Khi chọn công nghệ, hãy xem **startup/warmup, throughput, RSS, native build time, reflection/dynamic loading, initialization policy, platform compatibility và vận hành thực tế** — không chỉ so sánh tên gọi “AOT”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fast-startup-008">Đo thời gian sẵn sàng và dung lượng bộ nhớ để đánh giá lợi ích khởi động</a>

<details>
<summary>Xem chi tiết</summary>

# Đo thời gian sẵn sàng và dung lượng bộ nhớ để đánh giá lợi ích khởi động

## 1. Khởi động nhanh là cải thiện điều gì?

Tối ưu khởi động Java không đơn giản là làm cho dòng `public static void main` được thực hiện sớm hơn vài mili-giây. Một dịch vụ có thể in “Started” nhưng chưa mở cổng, đã mở cổng nhưng chưa nạp cấu hình, hoặc nhận kết nối mà chưa phục vụ thành công yêu cầu thực tế. Tương tự, giảm heap used không nhất thiết giảm **RAM thực** hoặc giới hạn bộ nhớ cần cấp cho container.

Để đánh giá lợi ích khởi động của một ứng dụng Java 17, cần trả lời ít nhất ba câu hỏi:

1. **Bao lâu ứng dụng thật sự sẵn sàng phục vụ?** Đo từ một mốc bên ngoài JVM đến **readiness condition** có ý nghĩa nghiệp vụ, không chỉ đến dòng log tùy ý.
2. **Ứng dụng sử dụng bao nhiêu bộ nhớ tại thời điểm đó và sau khi ổn định?** Phân biệt **heap used**, **heap committed**, **non-heap/metaspace**, **native memory**, **RSS**, giới hạn cgroup và peak.
3. **Đổi lấy gì?** Startup nhanh hơn có thể khiến steady-state throughput giảm, GC nhiều hơn, RSS tăng, container dễ bị OOMKilled hoặc first-request latency xấu đi.

Bài này tập trung **đo đạc và đánh giá bằng thực nghiệm** cho HotSpot/OpenJDK Java 17: định nghĩa điểm sẵn sàng, tạo baseline, chạy lặp nhiều process, thu thập chỉ số thời gian và bộ nhớ, so sánh có kiểm soát, tìm nguồn sai lệch và đưa ra quyết định dựa trên mục tiêu triển khai. Các ví dụ độc lập dùng **Java 17**, công cụ JDK `java`, `javac`, `jcmd`, `jstat` và công cụ hệ điều hành khi cần.

**Phân biệt:** Java SE bảo đảm hành vi Java ở cấp ngôn ngữ/JVM, không tiêu chuẩn hóa một chỉ số “startup time” thống nhất cho mọi ứng dụng. Những options như CDS, compiler tier, GC và Native Memory Tracking là **cơ chế phụ thuộc bản JVM/JDK**. Các con số thời gian/RSS giả định dùng trong ví dụ được dán nhãn rõ, không giả là dữ liệu đã đo.

## 2. Các mốc thời gian: process started khác application ready

Một timeline hữu ích:

```text
T0             T1                 T2                  T3                T4
|--------------|------------------|-------------------|-----------------|
Launcher       JVM initialized    Framework/app       Socket opened     Endpoint /ready
process begins                    initialization                        returns success
                                                                        + dependencies OK

T5: yêu cầu nghiệp vụ đầu tiên thật sự hoàn tất thành công
T6: throughput/latency sau warm-up đạt điều kiện steady state

T0→T4: startup to readiness (mục tiêu vận hành thường quan trọng)
T0→T5: first-successful-request latency
T4→T6: thời gian làm nóng sau ready, nếu có
```

Các mốc là mô hình; quá trình thật có thể khác thứ tự hoặc chạy song song một phần.

Những điểm kết thúc thường bị trộn:

- **Process created:** hệ điều hành tạo Java process; chưa chứng minh JVM/app sẵn sàng.
- **`main` entered:** JVM đã tải/khởi tạo các lớp cần để gọi `main`; nhiều framework/dependency chưa hoàn tất.
- **Application init complete:** một callback/framework event báo hoàn tất; có thể chưa bảo đảm dependencies hoặc endpoint thực tế đáp ứng.
- **Port bound/listening:** hệ điều hành nhận socket; handler nghiệp vụ vẫn có thể trả 503.
- **Readiness endpoint trả 200:** tốt hơn nếu endpoint kiểm tra đúng điều kiện cần nhận traffic.
- **First business request succeeds:** đo đường end-to-end mà người dùng thật thấy, có thể bao gồm lazy initialization, JIT warm-up, connection establishment.
- **Steady-state throughput/latency:** đo một tính chất khác hẳn startup, không được lấy thay cho readiness.

**Lựa chọn định nghĩa phải phục vụ yêu cầu:** với HTTP service, “sẵn sàng” thường là endpoint health/readiness trả success theo tiêu chí áp dụng trước khi load balancer bắt đầu chuyển traffic. Với CLI tool, mốc hợp lý có thể là **ra kết quả đúng và exit**. Với batch worker, mốc ready có thể là **đã kết nối queue và sẵn sàng nhận job**, không phải lệnh `main` return.

## 3. Cần một điểm bắt đầu đo ở ngoài JVM

Để tính **process-start-to-ready**, công cụ đo phải lấy timestamp gần lúc **khởi tạo process**. Nếu đặt:

```java
long started = System.nanoTime(); // đầu main
initialize();
long elapsed = System.nanoTime() - started;
```

thì bạn chỉ đo **main-to-init**, bỏ qua:

- OS process launch và Java launcher.
- JVM runtime setup và các native libraries cần trước `main`.
- Class loading/initialization xảy ra trước điểm đánh dấu.
- Một số startup hooks, agents và premain logic.
- Chênh lệch thời gian cho subprocess startup của môi trường.

Mốc trong `main` vẫn hữu ích để **chia nhỏ giai đoạn**, nhưng không nên đặt tên nó là **total process startup time**. Để đo tổng, cần một harness ngoài process gọi Java và nhận tín hiệu readiness.

### `System.nanoTime()` và `currentTimeMillis()`

- `System.nanoTime()` được thiết kế đo khoảng thời gian trôi qua bằng hiệu giữa hai lần gọi **trong phạm vi phù hợp**; không được dùng giá trị tuyệt đối làm Unix epoch.
- `System.currentTimeMillis()` phản ánh wall clock thời gian lịch, có thể bị chỉnh theo đồng hồ hệ thống; không phải lựa chọn tốt nhất để đo duration ngắn.
- **Không trừ hai giá trị `nanoTime` lấy từ hai Java processes độc lập** rồi giả định chúng có cùng điểm gốc; mỗi process/runtime/OS có thể có origin riêng.
- Harness nên tự dùng monotonic clock của mình cho T0→ready, ví dụ Python `time.monotonic_ns()` hoặc clock tương ứng của hệ điều hành.

Một **log timestamp** giúp phân tích timeline, nhưng một dòng có timestamp trễ do buffered logging chưa chắc phản ánh chính xác thời điểm endpoint có thể phục vụ.

## 4. Readiness contract phải được viết trước khi benchmark

Một readiness endpoint chỉ đúng khi nó phản ánh khả năng phục vụ traffic theo kiến trúc:

- Server đã bắt đầu nhận requests.
- Các cấu hình, certificates và resource pools cần thiết đã có.
- Những dependencies **bắt buộc để phục vụ phần lớn requests** đã được khởi tạo/kiểm tra theo timeout phù hợp.
- Nếu có database, queue hoặc remote APIs, xác định rõ **khi nào chúng cần thiết cho readiness** và chính sách degraded mode.
- Worker chưa bị đánh dấu shutting down.
- Nếu app sử dụng lazy initialization, định nghĩa ready có chấp nhận **first-request penalty** không; nếu không, phải warm đúng path trước ready.

**Liveness** và **readiness** khác nhau:

- **`/live`:** tiến trình còn khả năng hoạt động; một database outage không nhất thiết làm liveness thất bại hoặc buộc restart loop.
- **`/ready`:** hệ thống có nên gửi requests nghiệp vụ vào instance **ngay lúc này** hay không.
- **`/startup`:** một số nền tảng orchestration hỗ trợ startup probe để ứng dụng có thêm thời gian khởi động trước khi bị restart bởi liveness; contract phụ thuộc nền tảng.

Dùng một endpoint trả 200 ngay khi Java HTTP server accept requests là **tín hiệu transport**, không đủ đại diện cho một ứng dụng thực tế nếu database/cache/schema vẫn chưa sẵn sàng.

## 5. Java 17: một server có readiness endpoint để thực hành đo

Tạo file **`StartupReadyServer.java`**. Demo dùng module `jdk.httpserver` có sẵn trong nhiều phân phối **full JDK 17**, không cần thư viện HTTP ngoài:

```java
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class StartupReadyServer {
    private static volatile boolean ready;
    private static volatile long checksum;

    private static void reply(HttpExchange exchange, int code, String text)
            throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    public static void main(String[] args) throws Exception {
        long mainStart = System.nanoTime();
        int port = args.length == 0 ? 18080 : Integer.parseInt(args[0]);

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/live",
                e -> reply(e, 200, "alive\n"));
        server.createContext("/ready", e -> {
            if (ready) {
                reply(e, 200, "ready\n");
            } else {
                reply(e, 503, "not-ready\n");
            }
        });
        server.createContext("/work", e -> {
            if (!ready) {
                reply(e, 503, "not-ready\n");
            } else {
                reply(e, 200, "checksum=" + checksum + "\n");
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(2));

        long result = 0;
        for (int i = 0; i < 10_000; i++) {
            result += i % 10;
        }
        checksum = result;

        server.start();
        ready = true;
        long elapsedMs = (System.nanoTime() - mainStart) / 1_000_000;
        System.out.println("READY port=" + port);
        System.out.println("mainToReadyMs=" + elapsedMs);
    }
}
```

Biên dịch và khởi động bằng **JDK 17**:

```bash
javac --release 17 --add-modules jdk.httpserver StartupReadyServer.java
java --add-modules jdk.httpserver StartupReadyServer 18080
```

Trong **terminal khác** khi server còn chạy:

```bash
curl -i http://127.0.0.1:18080/live
curl -i http://127.0.0.1:18080/ready
curl -i http://127.0.0.1:18080/work
```

Sau khi đã sẵn sàng, phần **body** lần lượt là:

```text
alive
ready
checksum=45000
```

Còn dòng `mainToReadyMs=...` chứa **số đo động**, không có expected time cố định. Lệnh `curl -i` còn in status code và headers, cũng thay đổi theo HTTP server/runtime. Demo không có cơ chế tự shutdown: dùng **Ctrl+C** sau khi kết thúc đo. Cổng 18080 phải trống; nếu bận thì chọn cổng khác ở cả lệnh Java và lệnh curl.

### Vì sao readiness trong demo là tín hiệu tốt hơn “main đã chạy”?

- `/ready` trả **503** trước khi flag `ready` chuyển true.
- `server.start()` đã được gọi trước khi cho ready.
- Dữ liệu nghiệp vụ đơn giản (`checksum`) được chuẩn bị trước ready.
- Request `/work` trả result có thể kiểm tra được, không chỉ xác nhận socket mở.

Đây vẫn chỉ là **mô hình nhỏ**: một dịch vụ thật có thể cần DB migrations, external dependencies, certificate loading, JIT warm-up hoặc queue subscription trước khi readiness hợp lệ. Trong demo, `ready=true` có thể xảy ra trước khi client poll thực sự kết nối được; vì vậy mốc tốt để harness đo là **lần response 200 đầu tiên từ endpoint**.

## 6. Hai điểm thời gian đo trong demo có ý nghĩa khác nhau

| Metric | Bắt đầu | Kết thúc | Bao gồm |
| --- | --- | --- | --- |
| `mainToReadyMs` | Đầu `main` | Sau `server.start()` và `ready=true` | Chỉ initialization bên trong `main` từ mốc đánh dấu |
| `processToReadyMs` | Trước khi harness tạo subprocess | Poll HTTP `/ready` lần đầu 200 | Launcher, JVM startup, app init, HTTP accept, poll overhead |
| `processToWorkMs` | Trước subprocess | HTTP `/work` trả đúng result | Đường đầu tiên của một request nghiệp vụ |
| `readyToWarmMs` | Ready HTTP 200 | Đạt điều kiện throughput/latency warm đã định nghĩa | Lazy init, JIT, caches sau ready |
| `coldStartToFirstByte` | Trước subprocess hoặc lúc request kích hoạt start | Client nhận first response byte | Có thể gồm orchestration/router/network và request processing |

**Không cộng các durations tùy ý** nếu chúng đã chứa nhau. Ví dụ `processToWorkMs` có thể bao gồm `processToReadyMs` nếu harness kiểm tra readiness trước, nhưng không nhất thiết nếu request đi theo một đường khác.

### Poll interval làm sai số thời gian ready

Nếu harness kiểm tra mỗi 100 ms, app có thể thực sự ready giữa hai probes. Thời điểm ghi nhận 200 đầu tiên sẽ **muộn hơn** thời điểm sẵn sàng thật trong một khoảng phụ thuộc polling/scheduling/network. Để so các biến thể, dùng **cùng probe interval**, endpoint và timeout, rồi ghi độ phân giải/overhead của cách đo.

## 7. Ví dụ harness ngoài JVM: đo process-to-readiness nhiều lần

Ví dụ dưới đây dùng **Python 3 standard library** để launch Java 17 riêng cho mỗi trial và đo thời gian bằng monotonic clock. Python là **công cụ đo ngoài JVM**, không phải source Java SE. Tạo file **`measure_ready.py`** cùng thư mục chứa `StartupReadyServer.class`:

```python
import statistics
import subprocess
import time
import urllib.error
import urllib.request

JAVA_CMD = [
    "java",
    "--add-modules", "jdk.httpserver",
    "StartupReadyServer",
    "18080",
]
URL = "http://127.0.0.1:18080/ready"
RUNS = 5
TIMEOUT_S = 30

def one_run():
    started = time.monotonic_ns()
    proc = subprocess.Popen(
        JAVA_CMD,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    ready_ms = None
    try:
        deadline = time.monotonic() + TIMEOUT_S
        while time.monotonic() < deadline:
            if proc.poll() is not None:
                raise RuntimeError(
                    "JVM exited before readiness; inspect app logs"
                )
            try:
                with urllib.request.urlopen(URL, timeout=0.2) as response:
                    if response.status == 200 and response.read() == b"ready\n":
                        ready_ms = (time.monotonic_ns() - started) / 1e6
                        break
            except (urllib.error.URLError, TimeoutError, OSError):
                pass
            time.sleep(0.02)
        if ready_ms is None:
            raise TimeoutError("Application did not become ready")
        return ready_ms
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()

samples = [one_run() for _ in range(RUNS)]
print("runs=", RUNS)
print("medianReadyMs=", round(statistics.median(samples), 2))
print("minReadyMs=", round(min(samples), 2))
print("maxReadyMs=", round(max(samples), 2))
```

**Lệnh:**

```bash
javac --release 17 --add-modules jdk.httpserver StartupReadyServer.java
python3 measure_ready.py
```

Các số `medianReadyMs`/`minReadyMs`/`maxReadyMs` là **số đo phải lấy trên máy thực**, không có stdout số thời gian dự đoán. `RUNS=5` chỉ phù hợp **bài tập minh họa**; benchmark báo cáo đáng tin thường cần nhiều trials hơn, phân bổ thứ tự variants và có tiêu chí dừng/độ tin cậy.

**Chú ý giới hạn của harness mẫu:**

- Dùng cổng cố định 18080: không chạy song song với server khác trên cùng cổng.
- Mỗi trial giết process cũ trước khi launch trial tiếp; không thay port khi server chưa tắt.
- Poll interval ~20 ms cộng chi phí connection/read tạo độ phân giải và sai số.
- Không ghi stderr/stdout để giảm ảnh hưởng log, nên khi startup fail hãy chạy Java thủ công để xem lỗi.
- Không gồm network/orchestrator khi thực tế triển khai ở Kubernetes/cloud khác máy client.
- OS file page cache/JIT shared archive có thể **warm** sau trial đầu; kết quả không nhất thiết là **cold boot từ đĩa**.
- Script tạo process riêng mỗi trial nhưng không xóa OS cache, DNS cache, shared filesystem cache hay thay đổi CPU frequency.
- Nếu process cũ còn giữ cổng hoặc bị kill chưa sạch, trial có thể fail.
- `Popen` tương ứng thời điểm harness yêu cầu process, không phải timestamp chính xác nanosecond mà kernel đầu tiên thực hiện Java instructions.

Không dùng script này để tuyên bố benchmark production đủ chuẩn; nó là **bộ khung đo được, có đúng mốc readiness**, có thể mở rộng logging và statistics.

## 8. Định nghĩa cold start và warm start theo môi trường

Một ứng dụng Java có nhiều mức “lạnh”:

- **Fresh JVM process:** mỗi run là một process Java mới, nhưng OS page cache còn warm.
- **Cold class/JIT state:** process mới, chưa có runtime in-memory JIT profile/code cache; dữ liệu thư viện ở disk cache vẫn có thể warm.
- **Cold application caches:** ứng dụng chưa precompute caches/pools/connections.
- **Cold OS file/page cache:** trang của JAR/JDK files chưa nằm trong filesystem cache; tái lập khó và cần kiểm soát OS.
- **Cold container image/node:** node mới phải pull image, mount layers, start container, setup namespace/cgroup; không giống fresh Java process trên host warm.
- **Scale-to-zero request:** còn có load balancer/router, DNS/TLS/sidecar, scheduling và image distribution, có thể lớn hơn JVM init rất nhiều.

Mỗi report phải nói rõ **mốc bắt đầu** và cái gì đã warm. Không gọi vòng lặp launch process trên cùng laptop là “cold container startup” nếu image đã cached và JVM libs đã resident trong page cache.

### Test cold startup một cách an toàn

Đừng tự ý dùng lệnh drop kernel page caches trên production/shared machine chỉ để tạo “cold” condition. Nó thay đổi toàn host, ảnh hưởng dịch vụ khác và không phản ánh chắc môi trường thật. Với cloud/container, có thể đo bằng node/pod mới có kiểm soát hoặc reset môi trường thử nghiệm độc lập; ghi rõ chi phí image pull/scheduler thuộc phạm vi metric hay không.

## 9. Dung lượng bộ nhớ: sáu con số thường bị nhầm

| Chỉ số | Nó đo gì | Không có nghĩa |
| --- | --- | --- |
| **Heap used** | Bytes đang được JVM heap cấp cho objects/structures theo quản lý heap và GC telemetry | RAM process đang dùng |
| **Heap committed** | Heap memory JVM đã cam kết có thể dùng; thường khác used | Tất cả committed pages chắc resident vật lý |
| **Heap max** / `-Xmx` | Giới hạn heap mà JVM cố không vượt qua theo cấu hình/ergonomics | Giới hạn RSS toàn process |
| **Non-heap used/committed** | Metaspace, code cache và các pools được JMX phân loại non-heap | Toàn bộ native memory |
| **RSS** | Resident Set Size: số trang đang resident trong physical memory tính vào process theo OS | Dung lượng Java heap used; cũng không phải unique private bytes |
| **PSS** (Linux) | Proportional Set Size: phần resident shared pages được chia theo số process, hữu ích khi chia memory shared | Một API Java SE portable có ở mọi OS |

Còn các đại lượng:

- **Virtual Size / VSZ:** vùng địa chỉ ảo đã map/reserve; thường lớn hơn RSS, không bằng RAM thực.
- **USS/private resident:** phần resident mà process giữ riêng, hữu ích khi nhiều JVM chia sẻ libraries/CDS.
- **NMT reserved/committed:** view của HotSpot Native Memory Tracking về allocations/areas VM theo subsystem; khác RSS và có phạm vi giới hạn.
- **Container/cgroup `memory.current`:** tổng memory usage mà cgroup hạch toán, có thể gồm process memory, page cache và các phần khác; **không được đồng nhất với RSS của Java PID**.
- **Peak RSS / peak cgroup:** đỉnh memory trong một khoảng; readiness snapshot có thể bỏ lỡ peak.
- **Swap:** memory pages có thể được swap out, làm RSS thấp nhưng latency xấu; phải quan sát thêm nếu môi trường cho phép.

**Quan trọng nhất:** `-Xmx512m` **không** có nghĩa Java process chỉ dùng 512 MiB RAM. HotSpot còn cần native thread stacks, metaspace, code cache, GC metadata, direct buffers, JNI/native libraries và các regions runtime khác.

## 10. Heap used, committed, max: demo Java 17 độc lập

Tạo file **`HeapSnapshotDemo.java`**:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

public class HeapSnapshotDemo {
    static volatile byte[] retained;

    static void printMemory(String label) {
        MemoryUsage heap =
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        System.out.println(label
                + " usedMiB=" + heap.getUsed() / (1024 * 1024)
                + " committedMiB=" + heap.getCommitted() / (1024 * 1024)
                + " maxMiB=" + heap.getMax() / (1024 * 1024));
    }

    public static void main(String[] args) {
        printMemory("before");

        retained = new byte[8 * 1024 * 1024];
        retained[0] = 1;
        retained[retained.length - 1] = 2;

        printMemory("after");
        System.out.println("arrayBytes=" + retained.length);
        System.out.println("endpoints="
                + (retained[0] + retained[retained.length - 1]));
    }
}
```

```bash
javac --release 17 HeapSnapshotDemo.java
java -Xms32m -Xmx128m HeapSnapshotDemo
```

**Hai dòng cuối stdout xác định:**

```text
arrayBytes=8388608
endpoints=3
```

Hai dòng `before/after` ghi các số **động**, khác JDK build/GC/heap layout và thời điểm runtime; không bịa expected used/committed. Với G1 region size/heap constraints, allocation có thể được quản lý khác với collector khác. Array có object header/padding nên **heap allocation thực có thể lớn hơn `arrayBytes`** (payload length).

Đọc số đo đúng:

- `getUsed()` là heap used tại thời điểm snapshot, **không phải bytes đã allocate cộng dồn**.
- `getCommitted()` là dung lượng JVM cam kết cho heap, có thể khác `used`.
- `getMax()` có thể là `-1` với một số memory pool/usage contexts không xác định maximum; đối với tổng heap trên JVM phổ biến sẽ liên hệ giới hạn heap.
- Cả ba **không** cho biết resident physical pages của process.
- Biến `retained` cố giữ reference mạnh tới array để demo có dữ liệu còn sống tới cuối chương trình; một phép đo thực tế cần tránh code bị dead-code elimination.

### Một ví dụ mô hình về sự khác biệt

Giả sử (số giả định):

```text
heap max        = 512 MiB
heap committed  = 256 MiB
heap used       = 120 MiB

native + mapped resident + other categories = 95 MiB
RSS OS          = 280 MiB

Kết luận hợp lý:
  heap used ≠ committed ≠ heap max ≠ RSS.
```

Các số không phải số đo từ `HeapSnapshotDemo`; chúng minh họa lý do **không** tính RSS bằng cách cộng một phép ước lượng native vào heap used mà không kiểm tra actual mappings/pages.

## 11. Đo non-heap và metaspace qua JMX Java SE

`ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage()` có thể trả aggregate non-heap used/committed. Nếu cần biết từng pool, đọc `MemoryPoolMXBean`:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

public class NonHeapPoolsDemo {
    public static void main(String[] args) {
        for (MemoryPoolMXBean pool :
                ManagementFactory.getMemoryPoolMXBeans()) {
            System.out.println(pool.getName()
                    + " -> " + pool.getUsage());
        }
    }
}
```

```bash
javac --release 17 NonHeapPoolsDemo.java
java NonHeapPoolsDemo
```

**Không có stdout cố định:** tên memory pools, số liệu và cách phân chia non-heap phụ thuộc collector, JVM implementation và version; HotSpot 17 thường có các pools cho **Metaspace**, **Compressed Class Space** (khi dùng) và **CodeHeap** segments.

**Đừng đọc Metaspace pool rồi cho rằng đã biết toàn bộ native memory.** Direct buffers, thread stacks, JVM internal arenas và native allocations khác không nhất thiết nằm trong JMX non-heap pools.

## 12. Đo RSS trên Linux và macOS/Windows

### Linux

Khi Java PID đang chạy:

```bash
ps -o pid,rss,vsz,comm -p <PID>
cat /proc/<PID>/status
cat /proc/<PID>/smaps_rollup
```

`<PID>` là **placeholder**; thay bằng PID thực. Các fields thường thấy:

- `VmRSS` trong `/proc/<PID>/status`: RSS của process theo kernel.
- `VmHWM`: high-water mark RSS (đỉnh resident do kernel báo) trong đời process.
- `Rss` và `Pss` trong `smaps_rollup`: resident và proportional resident cho các mappings phù hợp.
- `ps rss` trên nhiều Linux distributions báo **KiB** (kiểm tra man page), không tự coi bytes.

`smaps_rollup` có thể cần permissions/kernel hỗ trợ; việc đọc sâu trên production có chi phí. RSS có thể gồm file-backed mapped libraries/JDK archive pages shared với các process khác, không chỉ private Java pages.

### GNU time: peak RSS lúc process kết thúc

Với GNU `/usr/bin/time`:

```bash
/usr/bin/time -v java -Xms64m -Xmx256m HeapSnapshotDemo
```

`Maximum resident set size (kbytes)` là **peak RSS** theo công cụ/OS trong thời gian process chạy, không phải RSS đúng vào readiness. Nếu ứng dụng server không kết thúc, phải stop server trong môi trường test rồi đọc kết quả; và cần thống nhất thời điểm stop giữa variants.

**Cẩn thận:** shell builtin `time`, GNU `/usr/bin/time` và các hệ Unix khác có output/options khác nhau; `-v` không phải option chung cho mọi hệ.

### Windows PowerShell

Có thể quan sát process Java:

```powershell
Get-Process -Name java |
    Select-Object Id, WorkingSet64, PeakWorkingSet64,
                  PrivateMemorySize64, VirtualMemorySize64
```

Các giá trị thường ở bytes. `WorkingSet64` tương tự khái niệm resident working set nhưng **không bằng Linux RSS/PSS một cách hoàn hảo**. `PrivateMemorySize64` khác Java heap used. Dùng `Get-Process -Id <PID>` nếu nhiều Java processes. PowerShell labels/behaviors có thể khác theo phiên bản OS.

### macOS

`ps -o rss` và `vmmap <PID>` có thể hỗ trợ điều tra footprint nhưng định nghĩa/công cụ khác Linux. Đừng trộn numbers từ Windows WorkingSet, Linux RSS và macOS footprint trong một báo cáo so sánh ngang hàng mà không giải thích.

## 13. Native Memory Tracking (NMT) để phân tích bộ nhớ HotSpot

HotSpot có **Native Memory Tracking (NMT)** nhằm ước lượng/ghi nhận những categories native memory do VM quản lý. Kích hoạt **trước khi khởi động JVM**:

```bash
java -XX:NativeMemoryTracking=summary \
  --add-modules jdk.httpserver StartupReadyServer 18080
```

Ở terminal khác với Java PID thích hợp:

```bash
jcmd <PID> VM.native_memory summary
jcmd <PID> GC.heap_info
jcmd <PID> VM.flags
```

`jcmd` cần JDK tools, cùng user/permissions thích hợp và process còn sống. NMT phải bật từ startup; không giả định có thể bật đầy đủ mọi cấp độ tracking giữa vòng đời process.

Có thể dùng baseline/diff khi runtime hỗ trợ:

```bash
jcmd <PID> VM.native_memory baseline
# Thực hiện một workload định nghĩa rõ
jcmd <PID> VM.native_memory summary.diff
```

**NMT reserved/committed không phải RSS:**

- **Reserved:** vùng địa chỉ ảo hoặc memory ranges đã dự trữ; không có nghĩa tất cả đã backed bằng physical pages.
- **Committed:** vùng đã cam kết theo allocator/VM; không nhất thiết bằng resident ngay tức thì.
- NMT chủ yếu theo dõi native allocations của **HotSpot/JVM**, **không bao trọn mọi native allocation từ third-party JNI libraries** hoặc memory phía OS.
- NMT có memory/CPU overhead và có thể làm biến động startup timing; muốn đo hiệu năng startup “clean” phải có lượt không NMT.
- `NativeMemoryTracking=detail` cho chi tiết hơn `summary` nhưng tăng overhead và volume dữ liệu; chỉ dùng khi cần.

### Tại sao so NMT với RSS có thể lệch?

RSS còn tính resident pages của mapped files/shared libraries, heap pages và native areas theo chính sách OS; NMT hạch toán ở categories reserved/committed trong runtime, có thể không theo dõi mọi native subsystem. Không yêu cầu tổng “NMT committed” bằng đúng “RSS”; đây là hai mô hình đo khác nhau.

## 14. Container memory: RSS chưa phải toàn bộ memory budget

Nếu Java chạy trong Linux container/cgroup v2, chỉ số thường gặp:

```bash
cat /sys/fs/cgroup/memory.current
cat /sys/fs/cgroup/memory.max
cat /sys/fs/cgroup/memory.peak
cat /sys/fs/cgroup/memory.events
```

Đường dẫn chỉ áp dụng khi process ở cgroup có mount/path tương ứng; trong môi trường có nested cgroups hoặc v1, phải xác định chính xác path controller. Một số files (như `memory.peak`) không tồn tại trên kernel/cgroup setup cũ.

Ý nghĩa:

- `memory.current`: usage theo cgroup accounting ở thời điểm đọc, có thể gồm nhiều processes và page cache theo kernel.
- `memory.max`: hard limit khi được cấu hình; giá trị `max` nghĩa không có limit cgroup đó theo file.
- `memory.peak`: high-water mark usage của cgroup phù hợp.
- `memory.events`: những sự kiện như OOM/OOM kill theo cgroup, cần đọc counter semantics.
- Container metric không nhất thiết bằng Java PID RSS; sidecar/native helpers/shared page cache có thể góp phần.

### Vì sao container bị OOMKilled khi heap used còn thấp?

Java heap chỉ là một phần memory footprint. Native thread stacks, metaspace, direct buffers, JIT/code cache, JVM internals, JNI, mapped files và page cache có thể làm cgroup chạm limit. Một process còn có thể bị kill bởi cgroup **mà không kịp ném `java.lang.OutOfMemoryError`** trong Java heap.

Nếu đo lợi ích startup trong container, báo cả **cgroup peak** và **process RSS** (khi thu được) thay vì chỉ dùng `MemoryMXBean.getHeapMemoryUsage().getUsed()`.

## 15. Peak, snapshot và steady-state: cần thu cả ba

Một service có thể:

1. Cần nhiều memory để parse cấu hình/truyền data vào các objects.
2. Giải phóng nhiều temporary objects sau startup.
3. JVM vẫn giữ heap committed.
4. RSS giảm chậm hoặc không giảm theo heap used vì page commitment/GC/OS policy.
5. Khi có traffic thật, thread stacks, caches và direct buffers tăng mạnh.

Vì vậy cần thu ít nhất:

- **Peak during startup:** đỉnh RSS/cgroup trong khoảng từ process start tới readiness.
- **At readiness:** RSS, heap used/committed, non-heap, NMT (nếu cần), process CPU time.
- **Steady state:** memory sau một workload/warm-up định nghĩa rõ (ví dụ sau 5 phút hoặc N requests) cùng throughput/p99.
- **After idle:** tùy mục tiêu serverless hoặc scale-to-zero; memory footprint sau thời gian nhàn rỗi.
- **Peak under traffic:** nếu ứng dụng có burst hoặc lazy initialization.

Một snapshot ở T4 **có thể bỏ lỡ peak xảy ra ở T2**. Một metric high-water mark ở process exit **không cho biết peak thuộc startup hay serving** nếu không có timeline.

## 16. Tại sao tăng `-Xms` có thể làm hiểu nhầm startup memory?

`-Xms` điều chỉnh kích thước heap ban đầu, còn `-Xmx` giới hạn heap tối đa. Thay đổi các flags có thể tác động:

- Memory committed từ đầu và khả năng heap mở rộng.
- GC ergonomics và điều kiện chọn cấu hình heap.
- RSS thực nếu các committed pages được chạm trong startup (không đồng nhất committed với resident).
- Startup timing vì page commitment/initialization và GC overhead.
- Throughput/tail latency sau ready.

Ví dụ thử nghiệm:

```bash
java -Xms64m -Xmx512m --add-modules jdk.httpserver StartupReadyServer 18080
```

So sánh với một cấu hình khác sau khi stop process cũ:

```bash
java -Xms256m -Xmx512m --add-modules jdk.httpserver StartupReadyServer 18080
```

**Không đưa ra kết luận trước khi đo**: `-Xms64m` có thể giảm committed heap tại ready nhưng làm heap growth/GC khác, không bảo đảm process-to-ready nhanh hơn hay RSS thấp hơn một tỷ lệ cố định. Đặc biệt `-XX:+AlwaysPreTouch` có thể khiến các trang committed được pre-touch, đổi mạnh chi phí startup và resident pages; nếu so sánh phải kiểm soát flag ấy.

**Không thay đồng thời `-Xms`, `-Xmx`, collector và concurrency** rồi đổ mọi thay đổi cho một biến. Chạy A/B có kỷ luật.

## 17. Class Data Sharing (CDS) và memory sharing

**Class Data Sharing (CDS)** của HotSpot có thể dùng một archive chứa metadata/class data đã xử lý để giảm một số chi phí class loading và cho phép chia sẻ mapped pages giữa nhiều Java processes. Điều này có thể liên quan tới cả **startup time** lẫn footprint, nhưng phải xem JVM/runtime thực tế.

Một cách **quan sát**, không ép kết quả:

```bash
java -Xshare:auto -Xlog:cds=info -version
```

Trong các HotSpot builds phổ biến, `-Xshare:auto` cho JVM dùng archive nếu khả dụng; khi không thể dùng archive có thể fallback theo semantics của flag. `-Xshare:on` có chính sách khắt khe hơn và có thể làm JVM exit nếu shared archive không dùng được. Không tự suy cứ có `-Xshare:auto` là archive chắc đã được sử dụng.

### CDS, AppCDS và shared pages

- **Default CDS archive:** có thể được JDK cung cấp/khởi tạo tùy distribution và cách cài.
- **Application CDS (AppCDS):** cần quy trình tạo/sử dụng archive theo đúng app/class path/module path/build; chỉ nên dùng khi đã có workload/class list thích hợp.
- **Shared mapped pages:** hai JVM processes dùng cùng CDS/JDK libraries có thể share resident pages, khiến tổng RSS của từng process **đếm lặp** shared pages.
- **PSS/USS** hữu ích hơn RSS đơn lẻ khi muốn biết incremental memory cost của nhiều replicas, nhưng mỗi metric có constraints.

**Không kết luận “CDS giảm RSS bằng X MiB” dựa trên một lần chạy**. Trên một process, mapped shared archive pages có thể vẫn làm RSS trông lớn, trong khi memory thực tăng thêm cho replica thứ hai lại thấp hơn. So **startup-to-ready** và **PSS/incremental cgroup footprint** theo mô hình deployment.

## 18. JIT và tiered compilation trade-off với startup

Đối với những ứng dụng chạy vài trăm mili-giây rồi exit, chi phí nạp class/JIT có thể chiếm đáng kể. Đối với server chạy hàng giờ, C2 optimized code có thể tiết kiệm CPU đáng kể sau warm-up.

Một flag HotSpot hay dùng trong **thử nghiệm riêng**:

```bash
java -XX:TieredStopAtLevel=1 \
  --add-modules jdk.httpserver StartupReadyServer 18080
```

Flag này giới hạn tiered compilation ở cấp C1 level 1 theo policy HotSpot phổ biến; nó **không đồng nghĩa Java chạy interpreter-only**. Nó có thể giảm một số overhead của profiling/C2 compilation và cải thiện vài chỉ số startup ở workloads phù hợp, nhưng có thể **giảm throughput hoặc tăng latency CPU-bound về lâu dài**.

Những phép đo cần so:

1. Process-to-readiness median/p95.
2. First business request duration.
3. Compilation CPU cost trong startup (diagnostic run).
4. RSS/committed/code cache ở ready.
5. 5–15 phút throughput/CPU/p99 sau ready, nếu app sống lâu.
6. Peak RAM khi nhiều replicas scale cùng lúc.

Không chọn cấu hình tối ưu startup mà chỉ dựa một microbenchmark method tính số: **điểm ready thực tế có thể nằm ở framework init/network/DB**, không phải JIT.

## 19. GC collector có ảnh hưởng startup không?

Collector khác nhau có thể thay đổi:

- JVM/GC initialization work.
- Metadata, native memory và background threads.
- Heap sizing/commit policies.
- Young/old GC pause behavior khi app khởi động.
- Memory footprint và throughput khi phục vụ traffic.

Ví dụ thử nghiệm **chỉ một biến mỗi lần**:

```bash
java -XX:+UseSerialGC -Xms64m -Xmx512m \
  --add-modules jdk.httpserver StartupReadyServer 18080
```

So với default collector **trên cùng JDK 17/container/heap**. `SerialGC` có thể phù hợp với process nhỏ, CPU-constrained hoặc short-lived workloads nhưng không phải “luôn nhanh nhất khi khởi động”; GC pauses và throughput dưới tải phải được đo.

**Lưu ý:** không chạy `UseSerialGC` và `UseG1GC` cùng lúc như thể có thể dùng hai collectors. JVM flag/ergonomics của mỗi JDK và container có thể chọn collector khác. Ghi `-XX:+PrintFlagsFinal` và `-Xlog:gc*=info` trong **diagnostic run** nếu cần biết collector thực đang dùng.

## 20. Giảm dependencies và lazy initialization: cẩn thận dời chi phí

Các kỹ thuật giúp giảm việc phải làm trước ready:

- Bỏ dependencies/framework modules không được dùng.
- Tránh classpath scanning quá rộng khi có cấu hình chính xác hơn.
- Bỏ eager initialization của caches không cần cho request đầu tiên.
- Khởi tạo client/database pools theo nhu cầu và cấu hình connection timeouts.
- Dùng lazy initialization cho feature ít dùng.
- Giảm số synchronous external calls bắt buộc ở startup.
- Dùng CDS/AppCDS trong những môi trường phù hợp.
- Giảm work logging/diagnostics không thiết yếu trước ready.

Nhưng rất dễ **dời chi phí sang lần request đầu**:

```text
Before:
  startup-to-ready = 800 ms
  first-request    = 30 ms

After lazy initialization (mô hình):
  startup-to-ready = 500 ms
  first-request    = 380 ms

Nếu traffic đến ngay sau ready, trải nghiệm có thể xấu hơn.
```

Đây là **số giả định để giải thích**, không phải kết quả benchmark. Một readiness contract tốt phải xác định những dependencies/cache paths nào cần được warm trước khi công bố 200.

### Lazy init và khả năng chịu lỗi

Nếu deferred initialization có thể fail ở first request, service đã đánh dấu ready dù chưa thể phục vụ một phần workload. Có thể chấp nhận với optional feature và graceful degradation, nhưng không chấp nhận nếu dependency ấy bắt buộc cho mọi request. **Định nghĩa “ready” là quyết định vận hành**, không chỉ optimization trick.

## 21. Ứng dụng CLI và batch: readiness có thể là completion

Đối với một CLI utility chạy Java, mốc ý nghĩa có thể là:

- Từ OS spawn tới stdout có **kết quả đúng**.
- Từ OS spawn tới **exit code 0** sau khi hoàn tất job.
- Từ dispatch task ở queue tới worker nhận job; hoặc thời gian xử lý xong job.
- Từ container create tới batch hoàn tất (bao gồm image pull nếu đó là mục tiêu).

Ví dụ không nên làm:

```bash
time java -version
```

Lệnh này đo launcher/JVM version-print path, **không đại diện startup của một ứng dụng nghiệp vụ có nhiều dependencies**. Có thể dùng làm một baseline thấp mang tính thông tin, nhưng không lấy nó thay performance của service thật.

Đối với CLI, kết quả giả nhanh do JVM không thực thi logic cần thiết sẽ đánh lừa hoàn toàn. Bảo đảm **output đúng, code path giống production** và dữ liệu input tương đương trước khi đọc timing.

## 22. So sánh baseline và candidate phải giống mọi yếu tố khác

Bảng thiết kế A/B:

| Dimension | Baseline A | Candidate B |
| --- | --- | --- |
| Source/application release | Cùng commit/build logic | Cùng commit nếu chỉ so JVM flag |
| JDK vendor/version/build | Java 17.0.x cụ thể | Giống A |
| CPU/cores, RAM, disk | Cùng machine/VM/container | Giống A |
| Cgroup memory/cpu limits | Cố định | Cố định |
| `-Xms/-Xmx` | Đã ghi rõ | Chỉ thay nếu là biến thử |
| CDS/GC/tiering | Ghi mọi flags | Chỉ thay chủ đích |
| Startup dependencies | DB/queue/network state kiểm soát | Tương đương A |
| Readiness definition | Cùng endpoint và response validation | Giống A |
| Probe interval/timeout | Cùng | Giống A |
| Run count/order | N trials, A/B xen kẽ | N trials, xen kẽ |
| Workload sau ready | Cùng input, concurrency, duration | Giống A |
| Output correctness | Required pass | Required pass |

Nếu mục đích so **hai JDKs**, đương nhiên JDK build là biến chủ đích; các yếu tố còn lại cố giữ tương đương. Nếu so **CDS**, archive/JAR/classpath phải được chuẩn bị đúng trên cả variants.

### Vì sao nên xen kẽ A/B?

Nếu chạy 20 lần A rồi 20 lần B, về cuối test OS page cache và CPU nhiệt độ có thể thay đổi, làm B có vẻ tốt hơn/xấu hơn do thứ tự. Xen kẽ hoặc randomize A/B giúp giảm **systematic drift**, nhưng không tự tạo tính độc lập tuyệt đối của samples. Ghi thời gian thứ tự, host load và các yếu tố môi trường.

## 23. Đọc thống kê: median, p95, variance, CI

Startup time thường có phân bố không chuẩn: đôi khi spike vì JIT/GC, CPU scheduling, host noise hoặc remote dependency. Một bảng “chỉ mean” có thể che biến động.

- **Median (p50):** startup điển hình của sample set.
- **p90/p95/p99:** tail startup; cần đủ số samples để percentile có ý nghĩa.
- **Min/max:** giới hạn quan sát được; min thường được lợi bởi page cache/lucky scheduling.
- **Std deviation / IQR:** độ phân tán; diễn giải với distribution shape.
- **Bootstrap CI hoặc phương pháp phù hợp:** thể hiện độ bất định của ước lượng.
- **Outliers:** không tự loại chỉ vì “làm số xấu”; tìm nguyên nhân hoặc báo kèm phương pháp lọc được xác định trước.

**Đừng lấy `p99` từ 5 trials làm kết luận đáng tin.** Với sample nhỏ, percentile cực cao phần lớn phản ánh một vài quan sát và bất định rất lớn. Nên chọn số trials dựa vào độ nhiễu, budget và yêu cầu độ tin cậy, không dùng một số cố định cho mọi workload.

### Warm-up cho server benchmark khác startup test

Trong **mỗi trial startup**, process cần mới nếu muốn đo init/JIT từ cold process. Nhưng để đánh giá **steady state**, cần cho server chạy và warm workload trước khi đo throughput.

**Không warm-up JVM trước rồi tính thời gian từ đó và gọi đó là cold process startup**. Đồng thời không đo throughput ngay sau process ready rồi báo là đã đạt steady state nếu code lazy-init/JIT còn đang warm.

## 24. Công thức đánh giá lợi ích và trade-off

Với A là baseline và B là candidate:

```text
timeSavedMs = readyMsA - readyMsB

timeImprovementPct = (readyMsA - readyMsB) / readyMsA * 100

rssChangeMiB = rssB - rssA

rssChangePct = (rssB - rssA) / rssA * 100
```

Nếu metric càng thấp càng tốt (startup, RSS, p99 latency), dấu **dương của timeImprovementPct** nghĩa B cải thiện thời gian; dấu dương của `rssChangePct` nghĩa B **tốn RSS hơn**.

Ví dụ **hoàn toàn giả định**:

| Metric | Baseline A | Candidate B | Diễn giải |
| --- | ---: | ---: | --- |
| Median process-to-ready | 900 ms | 660 ms | Giảm 240 ms (~26,7%) |
| p95 process-to-ready | 1.200 ms | 970 ms | Giảm 230 ms |
| RSS tại ready | 120 MiB | 135 MiB | Tăng 15 MiB (12,5%) |
| Peak cgroup startup | 175 MiB | 185 MiB | Tăng 10 MiB |
| First request p95 | 45 ms | 70 ms | Tệ hơn 25 ms |
| Throughput steady state | 5.000 req/s | 4.400 req/s | Giảm 12% |

**Kết luận ví dụ:** B khởi động nhanh hơn nhưng tăng RAM và giảm throughput. Nếu mục tiêu serverless scale-to-zero có mức giới hạn RAM đủ rộng, trade-off có thể chấp nhận **khi first-request penalty được xét kỹ**. Nếu service luôn chạy và cần throughput cao, B có thể kém lợi.

**Không có một “startup score” universal**. Định nghĩa trước ngưỡng vận hành/SLO: “ready p95 dưới N giây, RSS peak dưới M MiB, steady-state throughput không giảm quá K%, correctness pass”. SLO tùy sản phẩm, không bịa N/M/K chung cho mọi hệ thống.

## 25. Java 17: chương trình tính phần trăm không phụ thuộc JVM timing

Tạo file **`StartupBenefitMath.java`**:

```java
public class StartupBenefitMath {
    static double improvementPct(double oldValue, double newValue) {
        if (oldValue <= 0) {
            throw new IllegalArgumentException("baseline must be positive");
        }
        return (oldValue - newValue) * 100.0 / oldValue;
    }

    public static void main(String[] args) {
        double beforeMs = 900.0;
        double afterMs = 660.0;
        double beforeRss = 120.0;
        double afterRss = 135.0;

        System.out.printf(java.util.Locale.ROOT,
                "timeSavedMs=%.0f%n", beforeMs - afterMs);
        System.out.printf(java.util.Locale.ROOT,
                "timeImprovement=%.1f%%%n",
                improvementPct(beforeMs, afterMs));
        System.out.printf(java.util.Locale.ROOT,
                "rssChangeMiB=%.0f%n", afterRss - beforeRss);
        System.out.printf(java.util.Locale.ROOT,
                "rssChange=%.1f%%%n",
                (afterRss - beforeRss) / beforeRss * 100);
    }
}
```

```bash
javac --release 17 StartupBenefitMath.java
java StartupBenefitMath
```

**Stdout xác định:**

```text
timeSavedMs=240
timeImprovement=26.7%
rssChangeMiB=15
rssChange=12.5%
```

Những số 900/660/120/135 đều là **số giả định để dạy cách tính**. Chương trình **không tự đo** startup hay RSS; muốn đánh giá app phải lấy số từ harness và OS/container tools rồi tính cùng công thức.

## 26. Đo memory mà không làm lệch startup timing

Một sai lầm phổ biến là bật mọi diagnostics lúc baseline nhưng tắt khi đo candidate. NMT, JFR, log class loading và thread dumps đều có thể tác động startup/memory.

Thiết kế nên có **hai đường thí nghiệm**:

1. **Clean performance runs:** tối thiểu instrumentation, cùng metrics probe bên ngoài, cùng JVM flags ngoại trừ biến thử; thu time-to-ready và RSS/cgroup sampling ngoài process.
2. **Diagnostic runs:** bật NMT/JFR/GC logs/class-load logs để giải thích tại sao; chấp nhận overhead nhưng không trộn trực tiếp numbers vào clean A/B.

Ví dụ bật JFR **từ JVM startup**:

```bash
java -XX:StartFlightRecording=filename=startup.jfr,dumponexit=true,settings=profile \
  --add-modules jdk.httpserver StartupReadyServer 18080
```

Recording với `dumponexit=true` ghi lại khi JVM thoát phù hợp; phải stop demo bằng Ctrl+C/signal và kiểm tra file `startup.jfr` thật sự được tạo. JFR có overhead và event configuration phụ thuộc JDK build; không hứa chính xác một event sẽ luôn có.

**JFR giúp tìm class load, compilation, GC, thread/events, CPU samples theo event availability**, nhưng không thay được external readiness marker hoặc OS RSS. Khi cần memory chính xác lúc ready, thu snapshot trực tiếp hoặc sampler ngoài process.

## 27. Warm-up vẫn quan trọng sau khi ứng dụng đã ready

Một server có thể mở cổng và trả 200 khi compiler mới chạy C1, chưa JIT optimized hot routes. Nếu đưa traffic ngay lúc ấy:

- First requests có thêm lazy class loading, dependency resolution.
- JIT profiling, C2 compilation hoặc deoptimization có thể tăng CPU.
- TLS/JDBC caches chưa warm.
- Garbage collector chưa có đủ activity để ổn định GC ergonomics.
- Tail latency cao hơn sau khi warm.

Hai hướng design:

**A. Ready sớm:** chấp nhận một khoảng warm-up khi serving; phù hợp nếu first-request latency SLO cho phép, có prewarming từ load balancer hoặc traffic thấp.

**B. Ready sau khi warm một tập representative paths:** startup-to-ready dài hơn nhưng first-request latency ổn định hơn. Không chạy một vòng lặp artificial chỉ để kích JIT mà khác code path production; có thể làm profile lệch và không đại diện.

Nên báo hai metrics cùng nhau: **time to ready** và **time to first acceptable latency/throughput**, tránh tối ưu chỉ có một mốc.

## 28. Đo nhiều replicas: RSS cộng thẳng có thể đếm lặp

Nếu 10 JVM processes cùng map một CDS archive hoặc shared libraries, cộng `RSS(PID1)+...+RSS(PID10)` có thể **đếm lặp cùng physical pages**. Để ước lượng memory tổng của node/cgroup:

- Quan sát **cgroup memory.current/peak** cho nhóm processes (hiểu shared accounting).
- Quan sát **PSS** theo process trên Linux khi có quyền và kernel hỗ trợ.
- So **incremental node/cgroup memory** khi tăng replicas 1→2→N trên môi trường kiểm soát.
- Phân biệt app heap/private anonymous resident với file-backed/shared pages.
- Lưu ý memory cache, sidecars, GC threads và OS memory accounting.

**Ví dụ giả định:** RSS của mỗi JVM là 150 MiB, nhưng 30 MiB là shared pages của cùng JDK/lib archive; hai JVM không tự động tiêu 300 MiB physical private RAM. Tuy nhiên cũng không đơn giản lấy 2×(150−30)+30 nếu có những shared mappings khác, page cache và OS accounting khác; hãy đo thực.

## 29. Một hình mẫu benchmark sheet/CSV cần báo

Với mỗi trial, lưu các trường:

```text
experiment_id
variant
trial_index
jdk_vendor
jdk_version
jdk_build
jvm_flags
os_kernel
cpu_model
cpu_quota
memory_limit
container_image_digest
app_commit
process_start_monotonic_ns
readiness_http_status
ready_elapsed_ms
first_work_success_ms
rss_ready_kib
rss_startup_peak_kib
cgroup_memory_ready_bytes
cgroup_memory_peak_bytes
heap_used_ready_bytes
heap_committed_ready_bytes
native_memory_tracking_enabled
steady_state_rps
steady_state_p95_ms
steady_state_p99_ms
failure_reason
```

Đây là **định dạng cột đề xuất**, không phải một CSV kết quả thật. Chỉ những fields có thể thu một cách nhất quán mới nên dùng để so; điền missing khi không có số, **không tự thay bằng 0** vì 0 ngụ ý đã đo và thực sự không dùng memory/time.

Nếu startup fail (exit nonzero, readiness timeout, dependency unavailable), ghi **failure trial** và lý do. Không tùy ý bỏ trials fail rồi báo “startup nhanh và ổn định”.

## 30. Sai lệch do hệ điều hành và môi trường triển khai

Những nguồn nhiễu không phải bug Java:

- **OS page cache:** JAR/JDK classes có thể đã resident sau vài trials.
- **Filesystem/network storage:** container image layer hoặc network file mount làm thời gian đọc khác nhau.
- **CPU quota/throttling:** container bị giới hạn CPU có thể mất nhiều wall-clock time dù CPU work không đổi.
- **Host contention:** các processes khác chiếm CPU/cache/memory bandwidth.
- **CPU frequency/thermal:** turbo boost, power saving và nhiệt độ gây drift.
- **NUMA placement:** page faults/thread placement khác ở máy nhiều sockets.
- **DNS/TLS/database health:** external latency và timeouts có thể chi phối ready.
- **Logging/tracing/agents:** Java agents, JFR, NMT, class-load logging tăng overhead.
- **Image pull/scheduling:** tùy metric, có thể chiếm phần lớn total scale-to-zero latency.
- **Clock granularity/probe cadence:** polling quá thưa che cải thiện nhỏ.
- **GC timing and heap ergonomics:** initial heap/committed differences gây biến động.
- **Security/antivirus scanning:** có thể ảnh hưởng file loads trên desktop/Windows.
- **Stale processes/port collision:** poll nhầm endpoint của process cũ làm ready time gần 0 giả tạo.

### Đặc biệt: kiểm tra process identity khi poll

Một harness dùng cổng cố định có thể mắc lỗi: process cũ chưa tắt nhưng endpoint vẫn trả 200, khiến trial mới được ghi “ready” trước khi JVM mới thật sự startup. Bản mẫu ở trên **kết thúc process mỗi trial**, nhưng trong benchmark mạnh hơn nên:

- Dùng port mới cho mỗi trial hoặc process-specific token.
- Xác minh PID/process đang trả endpoint và port ownership.
- Đợi process cũ hoàn toàn exit, kiểm tra port được release.
- Ghi failure nếu subprocess mới thoát sớm.
- Không report success khi kết nối nhầm server khác.

Đây là loại lỗi thực tế có thể tạo “startup 0–20 ms” rất thuyết phục nhưng vô nghĩa.

## 31. Những kết luận sai về memory

| Kết luận sai | Điều đúng |
| --- | --- |
| “`-Xmx` bằng giới hạn RAM của JVM” | `-Xmx` chỉ giới hạn **Java heap**, không gồm native footprint |
| “Heap used thấp thì RSS cũng thấp” | Heap committed, metaspace, code cache, stacks, native allocations vẫn tốn |
| “Heap committed là toàn bộ RAM resident” | Committed khác resident physical pages |
| “VSZ 4 GiB nghĩa JVM ăn 4 GiB RAM” | Virtual reserved address space không bằng RSS |
| “RSS của 10 replicas cộng lại = unique physical RAM” | Shared mappings/CDS có thể bị đếm lặp |
| “NMT total committed phải bằng RSS” | NMT và OS RSS có coverage/accounting khác |
| “NMT theo dõi mọi JNI allocation” | NMT không bao trọn tất cả third-party native memory |
| “Metaspace bằng toàn bộ native memory” | Chỉ một vùng/cấu phần |
| “Container memory.current bằng một Java PID RSS” | cgroup có thể gồm nhiều processes/cache |
| “Readiness RSS đủ đại diện peak” | Startup có thể có temporary peak trước ready |
| “GC chạy thì RSS phải giảm ngay” | JVM/OS commit, page reclamation và GC policy quyết định |
| “Giảm -Xms luôn giảm startup time” | Chỉ là một trade-off, cần đo; GC/heap growth có thể đổi |
| “Đổi GC chỉ tác động pauses, không tác động footprint” | GC implementation có threads, metadata và sizing policies riêng |

## 32. Những kết luận sai về startup

| Kết luận sai | Điều đúng |
| --- | --- |
| “Java process tạo xong là app ready” | JVM và ứng dụng còn initialization work |
| “Đầu main là mốc total startup” | Bỏ qua launcher và phần JVM khởi tạo trước main |
| “Port listening nghĩa phục vụ được nghiệp vụ” | Endpoint có thể trả 503 hoặc dependencies chưa ready |
| “Log 'Started' là mốc tuyệt đối” | Log buffering/order không thay external readiness probe |
| “Latency first request chắc bằng ready time” | First request có lazy init, TLS, code warm-up khác |
| “Một trial nhanh hơn chứng minh tối ưu” | OS caches, scheduler, polling gây nhiễu |
| “JMH method benchmark đo process startup” | JMH đo microbenchmark hot/steady state theo harness |
| “Bật PrintCompilation không làm timing đổi” | Diagnostics có overhead |
| “CDS luôn giảm RSS của process” | Shared mappings có cách accounting khác, phải đo |
| “TieredStopAtLevel=1 luôn tốt hơn default” | Có thể giảm steady-state throughput |
| “SerialGC luôn là collector startup tốt nhất” | Phụ thuộc workload, heap, CPU và SLA |
| “Lazy init luôn cải thiện user experience” | Có thể chuyển chi phí sang first request và tăng failures |
| “Từ container start đến ready giống Java launch-to-ready” | Container setup/image pull/sidecar là phần khác |
| “p99 của năm trial đáng tin” | Sample size quá nhỏ để suy tail confidence |
| “Warm run trên host = cold startup” | OS page cache có thể còn warm |
| “Một flag thay đổi cùng lúc với JDK/app build vẫn phân tích được” | Confounding variables; phải kiểm soát design |

## 33. Một quy trình đánh giá có thể tái lập

**Bước 1 — Định nghĩa điều kiện thành công.** Viết rõ endpoint/operation trả gì, status code nào, dependencies nào cần sẵn sàng. Phân biệt liveness/ready/first request.

**Bước 2 — Định nghĩa mốc T0.** Java process created, container started, pod scheduled, hay request đến serverless platform? Không trộn các loại mốc.

**Bước 3 — Chốt môi trường.** Ghi JDK vendor/version, OS/CPU, cgroup limits, JAR/image digest, framework flags, dependencies state.

**Bước 4 — Chọn baseline A.** Lưu JVM flags đang dùng, heap initial/max, GC, CDS, tiered policy.

**Bước 5 — Chọn đúng một thay đổi B.** Ví dụ chỉ bật CDS, chỉ đổi `TieredStopAtLevel`, chỉ giảm eager initialization hoặc chỉ đổi `-Xms`.

**Bước 6 — Xây harness ngoài process.** Spawn JVM/process, poll readiness, có timeout, kiểm tra HTTP 200 + body/process identity, stop sạch và ghi lỗi.

**Bước 7 — Chạy nhiều trials độc lập.** Ghi cả những runs fail, xen kẽ thứ tự A/B, không coi một lần đo là benchmark.

**Bước 8 — Thu memory cùng mốc.** RSS tại ready, RSS peak lúc startup, heap used/committed (nếu cần), cgroup memory.current/peak. Không so sai đơn vị KiB/MiB.

**Bước 9 — Thu first-request metric.** Đảm bảo ứng dụng B không chỉ dời initialization sang request đầu.

**Bước 10 — Thu steady-state.** Với server dài hạn, đo throughput/CPU/p95/p99 sau representative warm-up.

**Bước 11 — Chạy diagnostics riêng.** Dùng JFR/NMT/GC/class load logs để giải thích nguyên nhân mà không làm lệch clean comparison.

**Bước 12 — Tính deltas và uncertainty.** Median/p95/time saved, RSS change/peak, failure rate và confidence; không chỉ báo “faster”.

**Bước 13 — Kiểm tra correctness.** Request thật/CLI output/batch result phải tương đương; readiness không được báo 200 quá sớm.

**Bước 14 — Quyết định theo SLO.** Startup nhanh hơn có đáng tăng RAM/giảm throughput không? Phù hợp scale-to-zero, batch hay service sống dài?

**Bước 15 — Kiểm tra regression.** Chạy trên môi trường gần production, với real image/build và constraints tương tự; giữ A/B logs để tái lập.

## 34. Câu hỏi tự kiểm tra

**Câu 1:** Vì sao `mainToReadyMs` không phải total startup?

Vì nó bắt đầu **sau** Java launcher/JVM initialization trước khi `main` được gọi.

**Câu 2:** Một server đã bind port, có chắc ready?

**Không.** Dependencies/handlers có thể chưa đáp ứng; cần readiness condition có nghĩa nghiệp vụ.

**Câu 3:** `-Xmx512m` có giới hạn process RSS ở 512 MiB không?

**Không.** Heap chỉ là một phần footprint.

**Câu 4:** Heap used = 100 MiB, committed = 300 MiB, RSS = 350 MiB có mâu thuẫn không?

**Không.** Ba metrics đo các khía cạnh khác nhau.

**Câu 5:** Vì sao cần peak RSS ngoài RSS tại ready?

Startup có thể cấp memory tạm thời rồi giải phóng trước ready, khiến snapshot bỏ sót peak.

**Câu 6:** NMT committed và OS RSS phải khớp từng byte?

**Không.** Có coverage và accounting khác nhau.

**Câu 7:** Vì sao `-XX:TieredStopAtLevel=1` có thể làm startup nhanh nhưng steady-state throughput xấu hơn?

Nó thay đổi JIT/tiering và không cho C2 tối ưu sâu theo policy thông thường, trade-off phụ thuộc workload.

**Câu 8:** CDS luôn giảm RSS trên một process?

**Không được bảo đảm.** Shared archive pages vẫn có thể tính trong RSS; PSS/incremental footprint có ý nghĩa khác.

**Câu 9:** Vì sao không dùng năm lần thử để khẳng định p99 startup?

Tail percentile cần đủ quan sát; năm trials có bất định quá lớn.

**Câu 10:** Tại sao `memory.current` không bằng `ps rss` của Java?

Cgroup có thể hạch toán nhiều processes/page cache và loại memory khác so với per-PID RSS.

**Câu 11:** Lazy initialization làm readiness nhanh hơn có chắc tốt?

**Không.** Nó có thể làm first request chậm hoặc fail, tăng peak và gây latency spikes.

**Câu 12:** Một harness poll mỗi 100 ms có đo được cải thiện 5 ms một cách chính xác không?

**Khó.** Poll quantization/scheduling có thể che cải thiện; cần thiết kế probe phù hợp.

**Câu 13:** Vì sao phải chạy clean và diagnostic runs riêng?

NMT/JFR/logging có overhead, có thể thay đổi chính startup timing/memory cần đo.

**Câu 14:** Muốn đo Java CLI tool, readiness endpoint có bắt buộc?

**Không.** Mốc thích hợp có thể là **kết quả nghiệp vụ đúng hoặc process exit thành công**.

**Câu 15:** Nếu B giảm ready 20% nhưng RSS +30% và first-request p95 xấu đi thì chọn B?

**Không thể quyết định chỉ từ % ready.** Phải xét SLO, RAM budget, traffic pattern, throughput và workload reality.

## 35. Tài liệu tham khảo Java 17 và công cụ liên quan

- [Oracle Java SE 17 — `java` launcher](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xms`, `-Xmx`, `-Xshare`, `-XX` flags, GC logging, JFR startup.
- [Oracle Java SE 17 — `jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `GC.heap_info`, `VM.native_memory`, `VM.flags`, `JFR.start`/`dump`.
- [Oracle Java SE 17 — Java HotSpot Virtual Machine Performance Enhancements](https://docs.oracle.com/en/java/javase/17/vm/java-hotspot-virtual-machine-performance-enhancements.html): CDS, tiered compilation và tối ưu HotSpot.
- [Oracle Java SE 17 — Class Data Sharing](https://docs.oracle.com/en/java/javase/17/vm/class-data-sharing.html): mục tiêu CDS và cơ chế shared archive ở version này.
- [Oracle Java SE 17 — Native Memory Tracking](https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html): `reserved`/`committed`, coverage và overhead.
- [Oracle Java SE 17 — Garbage Collection Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/index.html): GC/heap ergonomics, trade-offs của collectors.
- [Java SE 17 — `Runtime.totalMemory`/`freeMemory`/`maxMemory`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html): heap capacity-related API.
- [Java SE 17 — `MemoryMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html) và [`MemoryUsage`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryUsage.html): used/committed/max trong heap và non-heap.
- [Java SE 17 — `MemoryPoolMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html): các pools như Metaspace/CodeHeap (tùy JVM).
- [Java SE 17 — `System.nanoTime`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime()): đo duration, không diễn giải giá trị tuyệt đối.
- [Java SE 17 — `jdk.httpserver` module](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/module-summary.html): server demo và HTTP contexts.
- [OpenJDK JFR — Flight Recorder](https://openjdk.org/jeps/328): công cụ runtime profiling và startup recording.
- [Linux kernel cgroup v2 memory documentation](https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html#memory): `memory.current`, `memory.max`, `memory.peak` và counters.
- [Linux proc `proc_pid_status`](https://man7.org/linux/man-pages/man5/proc_pid_status.5.html) và [`proc_pid_smaps`](https://man7.org/linux/man-pages/man5/proc_pid_smaps.5.html): VmRSS/VmHWM/PSS và memory mapping accounting.
- [GNU time](https://www.gnu.org/software/time/): `/usr/bin/time -v` và peak RSS trên hệ hỗ trợ.

**Kết luận:** Một cải thiện khởi động chỉ đáng tin khi bạn **đo đúng thời gian từ process/container start tới readiness có nghĩa**, kiểm tra **request đầu tiên/steady-state trade-offs**, và báo **RSS hoặc cgroup peak** ngoài heap used/committed. Java 17 có đủ công cụ cho việc này: endpoint ready, harness monotonic ngoài JVM, `MemoryMXBean`, `jcmd` NMT/JFR và OS/container metrics. Hãy chạy nhiều trials trên điều kiện tương đương, giữ baseline, trình bày uncertainty, phân biệt số đo thật với ví dụ giả định và chỉ kết luận dựa trên **SLO cùng tổng cost của ứng dụng**.


</details>

- [Quay lại đầu trang](#back-to-top)
