<a id="back-to-top"></a>

# Quan sát và lỗi JVM

## Menu
- [Chọn bằng chứng phù hợp: bytecode, log, chỉ số, thread dump và heap dump](#jvm-observability-and-errors-001)
- [Unified Logging với -Xlog cho GC, nạp lớp và safepoint](#jvm-observability-and-errors-002)
- [Đọc log GC: nguyên nhân, bộ nhớ trước/sau, dung lượng heap và thời gian dừng](#jvm-observability-and-errors-003)
- [jcmd và java.lang.management để xem phiên bản, cờ và mức dùng tài nguyên](#jvm-observability-and-errors-004)
- [JFR ở mức nhập môn để đối chiếu cấp phát, GC và hoạt động luồng](#jvm-observability-and-errors-005)
- [Native Memory Tracking: nhóm bộ nhớ, baseline, so sánh thay đổi và phạm vi theo dõi](#jvm-observability-and-errors-006)
- [StackOverflowError và các dạng OutOfMemoryError cần phân biệt](#jvm-observability-and-errors-007)
- [ClassNotFoundException, NoClassDefFoundError và lỗi khởi tạo lớp](#jvm-observability-and-errors-008)
- [UnsupportedClassVersionError, VerifyError và ClassFormatError](#jvm-observability-and-errors-009)
- [NoSuchMethodError, IllegalAccessError và các lỗi không tương thích nhị phân](#jvm-observability-and-errors-010)
- [Phân biệt ngoại lệ Java với JVM crash; nhận diện báo cáo hs_err_pid](#jvm-observability-and-errors-011)
- [Chọn thao tác chẩn đoán theo tác động dừng, CPU và dung lượng dữ liệu tạo ra](#jvm-observability-and-errors-012)

## <a id="jvm-observability-and-errors-001">Chọn bằng chứng phù hợp: bytecode, log, chỉ số, thread dump và heap dump</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn bằng chứng phù hợp: bytecode, log, chỉ số, thread dump và heap dump

## Mục tiêu và nguyên tắc điều tra

Khi một ứng dụng Java chậm, treo, tiêu thụ nhiều bộ nhớ hoặc phát sinh exception, việc đầu tiên **không phải** mặc định lấy heap dump hay tăng `-Xmx`. Việc đầu tiên là **phát biểu giả thuyết kiểm chứng được** rồi chọn loại bằng chứng phù hợp.

Trong HotSpot **Java 17**, năm nhóm bằng chứng chủ đạo của bài này là:

1. **Bytecode:** class đã biên dịch chứa những chỉ thị, descriptor, exception handlers, attributes và thông tin version gì?
2. **Log:** sự kiện nào được ứng dụng/JVM ghi nhận, tại thời điểm nào, theo thread/trace nào và theo cấu hình logging nào?
3. **Chỉ số (metrics):** mức sử dụng heap, CPU, threads, allocation rate, GC counts/pause durations, queue depth và request latency biến đổi như thế nào theo thời gian?
4. **Thread dump:** tại một thời điểm, Java threads đang ở trạng thái gì, stack frames nào, sở hữu/chờ monitor hoặc ownable synchronizer nào?
5. **Heap dump:** tại một thời điểm, heap chứa những **objects/refs** nào, chúng còn được giữ bởi các đường reference nào và giữ lại bao nhiêu bộ nhớ?

Mỗi bằng chứng trả lời một **loại câu hỏi khác nhau**. Bytecode có `new` không chứng minh C2 phải thực hiện một allocation tại runtime. Log không có dòng lỗi không chứng minh lỗi không xảy ra. Một giá trị heap-used cao không chứng minh memory leak. Một thread dump có thread ở `WAITING` không đồng nghĩa deadlock. Heap dump có nhiều `byte[]` chưa chỉ ra ai đang giữ chúng.

> **Công thức điều tra:** Triệu chứng → giả thuyết → bằng chứng tối thiểu có thể bác bỏ/xác nhận giả thuyết → đối chiếu thời gian và môi trường → kết luận có giới hạn → kiểm nghiệm biện pháp sửa.

Bài nằm trong nhóm **Quan sát và lỗi JVM**, không chỉ liệt kê tên công cụ. Mục tiêu là **chọn đúng evidence với chi phí/rủi ro phù hợp**, biết giới hạn của từng snapshot và xây một hồ sơ sự cố có thể tái lập.

## 1. Bảng quyết định nhanh

| Câu hỏi cụ thể | Bằng chứng ưu tiên | Công cụ Java 17 thường dùng | Không thể kết luận chỉ từ đó |
| --- | --- | --- | --- |
| Lỗi `UnsupportedClassVersionError` do class version nào? | **Bytecode/class metadata** + runtime identity | `javap -verbose`, `java -version` | JVM thực sự đang dùng JIT compiler nào chỉ từ bytecode |
| Tại sao nhánh/lệnh trong source khác khi deploy? | **Bytecode** + artifact digest | `javap -c -p`, `jar tf`, checksum | Source file đang mở chắc trùng class deploy |
| Exception xảy ra ở đâu? | **Log** với stack trace, trace ID | Logger, unified logging khi phù hợp | Lỗi chưa từng xảy ra nếu không có log |
| JVM mất thời gian vì GC? | **Metrics theo thời gian + GC log/JFR** | `-Xlog:gc*`, `jstat`, `jcmd`, JFR | Mọi request chậm đều do GC |
| Thread pool bão hòa hay deadlock? | **Nhiều thread dumps + executor metrics** | `jcmd PID Thread.print -l` | Một thread `WAITING` là lỗi |
| Heap usage tăng mãi sau traffic? | **Time series sau GC** rồi **heap histogram/dump** khi cần | Heap metrics, `GC.class_histogram`, `GC.heap_dump` | Chỉ số heap-used cao = leak |
| Class nào giữ nhiều byte arrays? | **Heap dump + dominator/retained-size analysis** | `jcmd PID GC.heap_dump`, MAT | Loại object lớn nhất là nguyên nhân gốc |
| Allocation rate tăng nhưng retained heap ổn định? | **Allocation metrics/JFR** | JFR/profile events | Heap dump một lần chỉ ra toàn bộ objects đã cấp phát rồi chết |
| Native RSS tăng nhưng Java heap ổn định? | **OS metrics + NMT (nếu bật)** | `jcmd PID VM.native_memory`, cgroup/OS | Heap dump giải thích toàn bộ native memory |
| JIT warm-up, code cache và method compilation? | **JFR/compilation diagnostics** | `-XX:+PrintCompilation`, JFR, `Compiler.codecache` | `javap -c` hiển thị machine code C2 |
| Application p99 tăng? | **Latency histograms + tracing/logs**, bổ trợ JFR/GC/thread dumps | Metrics/traces/JFR | Một snapshot giải thích mọi tail-latency spike |
| Crash JVM/native error? | **Fatal error file `hs_err_pid...log`** + OS logs/core nếu có | `-XX:ErrorFile`, vendor debugging | Heap dump thay thế fatal error report |

Bảng mang tính **ưu tiên bằng chứng đầu tiên**, không phải một công cụ duy nhất đủ chứng minh mọi giả thuyết. Trong điều tra thực tế thường cần kết hợp hai hoặc ba loại.

## 2. Ba mức bằng chứng: mô tả, tương quan và nguyên nhân

Điều tra chặt chẽ cần phân biệt:

- **Mô tả (observation):** “heap used ở 2.5 GiB”, “thread T ở `BLOCKED`”, “GC pause lúc 10:15:03”.
- **Tương quan (correlation):** “khi p99 request tăng thì GC pause cũng tăng trong cùng cửa sổ thời gian”.
- **Nguyên nhân (causality):** “pause stop-the-world này thực sự làm request R bị chậm X ms” hoặc “object được giữ bởi cache key path gây tăng retained heap”.

Một log dòng `GC(31) Pause Young 50ms` không tự chứng minh toàn bộ 50ms là phần duy nhất của một request latency 200ms. Một thread stack chứa `SocketInputStream.socketRead0` có thể chỉ là I/O blocking bình thường. Một `HashMap` giữ nhiều objects trong heap dump có thể là cache được thiết kế, không phải leak.

**Mẫu kết luận tốt:**

> “Tại các thời điểm t1, t2 và t3 sau full workload, old-gen occupancy tăng từ A đến B; heap dumps cho thấy retained-size của một nhóm objects tăng và chúng có GC-root path qua static map M. Điều này hỗ trợ giả thuyết retention trong M; cần đối chiếu cache policy và chạy lại bài kiểm tra giải phóng.”

**Mẫu kết luận yếu:** “Java tốn RAM vì có quá nhiều objects.”

## 3. Bằng chứng phải có bối cảnh thời gian

Mỗi evidence nên kèm:

- **Thời điểm tuyệt đối** (timestamp, timezone, đồng bộ clock).
- **Thời gian JVM chạy** (uptime) khi công cụ có thể cung cấp.
- **Process identity** (PID, service/pod/container, JDK vendor/VM version).
- **Workload window** (traffic, batch id, scenario, phase warm-up/steady-state).
- **Cấu hình liên quan** (heap/GC flags, CPU quota, logging/JFR settings).
- **Độ hạt/sampling** (metric 10s hay 1s, JFR events nào bật, có drop/sampling không).
- **Chi phí thu thập** (safepoint, heap traversal, disk I/O, performance impact).
- **Nguồn và tính toàn vẹn** (file path, digests/labels nếu chuyển artifact).

```text
Incident window:
  12:00:00  traffic tăng
  12:00:20  p99 latency spike
  12:00:21  GC log event + CPU metric
  12:00:24  thread dump #1
  12:00:29  thread dump #2
  12:00:34  thread dump #3
  12:01:00  heap usage sau traffic vẫn cao

Không có timestamp và process ID, những artifacts
dễ bị gộp nhầm giữa pods hoặc các lần deploy.
```

**Đừng so timestamps khác clock domain một cách mù quáng.** `System.nanoTime()` chỉ thích hợp đo elapsed duration trong một JVM/process, không phải timestamp wall-clock để ghép trực tiếp với server logs của máy khác.

## 4. Bytecode: chọn khi cần biết compiler đã tạo gì

**`javap`** trong JDK 17 là công cụ xem class-file format và instructions. Nó hữu ích khi:

- Trình biên dịch/công cụ sinh bytecode không giống source bạn nghĩ.
- Cần kiểm tra class major version lúc triển khai.
- Muốn thấy `invokevirtual`, `invokeinterface`, `invokestatic`, `invokedynamic`.
- Muốn hiểu bytecode `tableswitch`/`lookupswitch`, `monitorenter`/`monitorexit`, `athrow`.
- Debug exception table, annotations, stack map frames, method descriptors và constant pool.
- Xác nhận JAR deploy thực sự chứa class cần phân tích.

Lệnh:

```bash
javac --release 17 BytecodeEvidenceDemo.java
javap -c -p BytecodeEvidenceDemo
javap -verbose BytecodeEvidenceDemo
```

**Lưu ý:** Chỉ chạy lệnh khi có file Java trong mục 5 bên dưới. `-c` disassemble Java bytecode; `-p` gồm cả members private; `-verbose` có constant pool, class version và attributes.

### 4.1. Bytecode không phải optimized machine code

```text
Source .java
   │ javac
   ▼
Class file bytecode       ← javap cho thấy
   │
   ▼
Interpreter / C1 / C2
   │
   ▼
Optimized machine code    ← javap KHÔNG cho thấy
```

Nếu bytecode có `new`, HotSpot C2 có thể loại allocation nhờ escape analysis khi giữ đúng semantics. Nếu bytecode có `invokeinterface`, JIT có thể devirtualize/inline. Nếu bytecode có `synchronized` bytecodes, compiler có thể loại lock khi chứng minh an toàn.

**Không dùng `javap -c` để tuyên bố chắc chắn đã có hoặc không có allocation/GC/deoptimization lúc runtime.** Muốn quan sát JIT behavior phải có compiler diagnostics, JFR hoặc native disassembly thích hợp theo đúng JVM build.

### 4.2. So artifact deploy, không so class ngẫu nhiên

```bash
jar tf service.jar
javap -classpath service.jar -c -p com.example.Service
javap -classpath service.jar -verbose com.example.Service
```

Đây là cú pháp minh họa; `service.jar` và class phải thực sự tồn tại. Đảm bảo:

- Đường dẫn đúng artifact đã deploy.
- Classloader không lấy class cùng tên từ JAR khác.
- Với multi-release JAR, class được chọn còn phụ thuộc Java runtime version và layout `META-INF/versions`.
- Generated proxies/lambda/bytecode weaving có thể tạo artifacts runtime khác bytecode đã đóng gói.
- Class file format không tự cung cấp loaded-class source hoặc runtime execution count.

Nếu nghi “chạy source cũ”, so hash JAR, effective classpath và bytecode/class-file version của artifact sản xuất.

## 5. Java 17 runnable demo 1 — bytecode và kết quả Java-level

Tạo file **`BytecodeEvidenceDemo.java`**:

```java
public final class BytecodeEvidenceDemo {
    private static int sign(int value) {
        if (value < 0) {
            return -1;
        }
        if (value == 0) {
            return 0;
        }
        return 1;
    }

    private static int sum(int[] values) {
        int result = 0;
        for (int value : values) {
            result += value;
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("sign=" + sign(-7));
        System.out.println("sum=" + sum(new int[]{2, 3, 5}));
        System.out.println("version=" + Runtime.version().feature());
    }
}
```

Biên dịch/chạy trong **Java 17 runtime**:

```bash
javac --release 17 BytecodeEvidenceDemo.java
java BytecodeEvidenceDemo
```

**Stdout chính xác khi chạy bằng Java 17:**

```text
sign=-1
sum=10
version=17
```

Sau đó:

```bash
javap -c -p BytecodeEvidenceDemo
javap -verbose BytecodeEvidenceDemo
```

Quan sát có thể kiểm tra:

1. `sign` chứa các conditional branches tương ứng.
2. `sum` có loop và thao tác array load/increment theo bytecode.
3. `main` tạo `int[]` và gọi methods tương ứng.
4. Class file do `javac --release 17` tạo có **major version 61** trong class-file metadata.
5. Nếu HotSpot JIT inline `sign`/`sum`, output `javap` **vẫn là bytecode** đã lưu trên disk.

Thứ tự/bytecode offsets/constant-pool indices có thể khác theo compiler build hoặc biến đổi source, nên **không đưa một transcript `javap` cố định không được đo từ môi trường thật**.

### 5.1. Từ bytecode có thể suy được gì?

Có thể kiểm tra **instruction contract** và đường control-flow bytecode, ví dụ branch/call/checks. Không thể biết class đã được load hay hotness thực tế chỉ từ class bytes. Đó là lý do phải bổ sung **logs/JFR/metrics** nếu câu hỏi là runtime performance.

## 6. Log: dùng để trả lời “điều gì đã được ghi nhận khi nào?”

**Application logs** thường mô tả business requests, exceptions, retries, timeouts, DB queries và lifecycle transitions. **JVM unified logging** mô tả runtime events như GC, safepoints, class loading và một số compiler activities. Chúng có phạm vi khác nhau.

Một bản ghi hữu ích tối thiểu nên có:

```text
timestamp | service | instance/pod | PID | thread | level
traceId/spanId | event | outcome | duration | normalized error
```

Ví dụ **minh họa**, không phải output của chương trình thật:

```text
2026-10-22T12:00:01Z checkout pod-a pool-4 INFO  trace=abc REQUEST_START
2026-10-22T12:00:04Z checkout pod-a pool-4 ERROR trace=abc DB_TIMEOUT
```

Để điều tra:

- Dùng **correlation IDs** để ghép cùng request qua nhiều services.
- Thống nhất timestamp timezone hoặc UTC, rồi ghép với JVM uptime của GC log nếu cần.
- Ghi full exception stack trace ở nơi thích hợp; một message “timeout” thường không đủ.
- Phân biệt log được ghi trước hay sau thay đổi state.
- Đừng giả định thứ tự hai dòng từ hai threads là một happens-before relation.
- Sampling/rate-limits/log level có thể khiến một số sự kiện không được ghi lại.
- Logging bất đồng bộ có queue/buffer; khi process crash, dòng log mới nhất có thể chưa được flush.
- Không in passwords, tokens, request body chứa dữ liệu nhạy cảm hoặc hàng triệu object values.

### 6.1. HotSpot unified logging JDK 17

Ví dụ ghi GC events vào file:

```bash
java -Xlog:gc*:file=gc.log:time,uptime,level,tags \
  BytecodeEvidenceDemo
```

**Lưu ý:** Lệnh chạy file class `BytecodeEvidenceDemo.class` đã được compile theo mục 5. GC có thể **không xảy ra** vì ứng dụng quá nhỏ; file log có thể chủ yếu có initialization/heap summaries tùy bộ lọc, không được diễn giải là “không bao giờ có GC trong chương trình”.

Ví dụ class loading:

```bash
java -Xlog:class+load=info BytecodeEvidenceDemo
```

Ví dụ safepoint:

```bash
java -Xlog:safepoint=info BytecodeEvidenceDemo
```

**`-Xlog` là cơ chế HotSpot/JDK unified logging**; tags, available levels, decorations và file outputs phụ thuộc JVM implementation/build. Không giả định một JVM khác HotSpot hỗ trợ toàn bộ tags y hệt.

### 6.2. Đọc log với ngữ cảnh GC

Một dòng GC thường chứa GC ID, pause type, trước–sau heap usage và elapsed pause time theo flags/tag selection. Khi ghép với application p99:

- Check **thời điểm GC pause thật sự** so với request latency spike.
- Check **pause duration**, không đồng nhất với tổng thời gian cycle concurrent marking.
- Xem **throughput allocation/CPU pressure** để tránh quy kết mọi chậm trễ cho GC.
- Xem **GC frequency**, old-gen occupancy và promotion nếu triệu chứng liên quan heap.
- Cân nhắc **safepoint synchronization delay** và các nguồn JVM pause khác.

**Không suy “log không có Full GC ⇒ heap không leak”.** Một retained-heap leak có thể tăng dần và chưa chạm ngưỡng trigger trong cửa sổ quan sát.

### 6.3. Không bật debug/trace vô tội vạ trong production

Logging quá mức có thể tiêu thụ CPU, disk I/O, heap allocations và che mất symptom gốc. Khi cần tăng log:

1. Chọn đúng component/tag/level.
2. Giới hạn duration và dung lượng file/rotation.
3. Xác định quyền truy cập logs.
4. Thử trên staging/reproduction nếu chi phí cao.
5. Thu nhỏ hoặc tắt lại khi đủ evidence.

Nếu muốn quan sát historical events mà log chưa bật, log không thể tự tạo lại quá khứ. Dùng các sources có khả năng lưu **rolling/continuous recording** được chuẩn bị trước như JFR hoặc metrics.

## 7. Metrics: dùng cho xu hướng, quy mô và cảnh báo

**Metrics** là số đo theo thời gian, ví dụ:

- Heap used/committed/max; young/old gen occupancy.
- GC collections count/duration và pause latency histogram.
- JVM process CPU usage, GC CPU, native RSS, container memory pressure.
- Live thread count, daemon threads, peak thread count.
- Class loading/unloading counts, code cache/metaspace usage.
- Application request rate, failure rate và **p50/p95/p99 latency**.
- Executor active workers, queue length, rejected tasks.
- Pool connections, downstream requests và timeout rate.

**Một điểm đo** chỉ cho biết số ở một thời điểm. **Một chuỗi điểm đo** mới cho thấy tốc độ thay đổi, trend, correlation và hiện tượng tích lũy.

### 7.1. Gauges, counters và histograms khác nhau

| Kiểu | Ví dụ | Cách diễn giải |
| --- | --- | --- |
| **Gauge** | JVM heap used bytes tại thời điểm đo | Có thể lên xuống sau GC; không lấy số cuối làm allocation rate |
| **Counter** | Tổng GC collections kể từ process start | Tính rate/delta theo khoảng thời gian; xử lý restart/reset |
| **Histogram** | Request latency buckets | Tính quantiles phù hợp; chú ý window/sample size |
| **Timer** | Time spent trong operation | Định nghĩa rõ start/end, tránh lẫn wall vs CPU |
| **Rate** | Allocated bytes/s, requests/s | Phải có denominator thời gian; xử lý missing samples |

Counter reset sau restart. Histogram aggregation có thể khác giữa systems. p99 từ 20 requests không có ý nghĩa thống kê như p99 từ hàng triệu requests; đừng trộn quantiles từ nhiều pods bằng cách lấy trung bình p99 nếu hệ thống metrics không hỗ trợ một cách có ý nghĩa.

### 7.2. Heap used không phải RSS

```text
Java process memory (khái niệm):
  Java heap
  Metaspace
  Thread stacks
  JIT Code Cache
  Direct buffers/native allocations
  GC structures / JNI / libc allocations
  Memory-mapped regions, ...
```

Vì vậy:

- JVM **heap used** thấp không chứng minh process **RSS** thấp.
- `-Xmx` là giới hạn heap, không bao trọn native memory.
- Container OOM kill có thể xảy ra dù heap chưa đạt `-Xmx`.
- Heap dump tập trung Java heap object graph; không tự giải thích mọi native allocations.

### 7.3. `jstat` và `jcmd`

Các lệnh HotSpot JDK 17 phổ biến:

```bash
jstat -gc <pid> 1000 5
jcmd <pid> GC.heap_info
jcmd <pid> VM.flags
jcmd <pid> VM.version
```

`<pid>` là PID **thực**, thay bằng số; đây không phải các câu lệnh có thể chạy nguyên dạng với dấu ngoặc nhọn. `jstat -gc` cung cấp các counters/capacities cụ thể theo JVM; output headers/đơn vị/collector behavior cần được hiểu theo JDK. **Không nên poll mọi vài milliseconds trong production** nếu không có mục tiêu và kiểm thử overhead.

### 7.4. JVM management APIs

Java SE 17 có management beans để đọc một phần runtime metrics ngay từ ứng dụng:

- `MemoryMXBean`: heap/non-heap memory usage.
- `GarbageCollectorMXBean`: GC counts và aggregate collection times khi implementation cung cấp.
- `ThreadMXBean`: thread counts, deadlock detection và thread CPU time trong phạm vi hỗ trợ.
- `RuntimeMXBean`: process JVM uptime, input args và VM name/version.
- `MemoryPoolMXBean`: memory pools (tên phụ thuộc collector/JVM).

Những APIs cho **measurement theo thời điểm/khả năng VM**, không tự tạo forensic explanation cho mọi sự cố. Một `getCollectionTime()` không phải histogram độ trễ theo GC pause ID.

## 8. Java 17 runnable demo 2 — đo các invariant từ MXBeans

Lưu **`JvmMetricsEvidenceDemo.java`**:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

public final class JvmMetricsEvidenceDemo {
    public static void main(String[] args) {
        MemoryUsage heap =
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        System.out.println("heapUsedNonNegative=" +
                (heap.getUsed() >= 0L));
        System.out.println("threadCountPositive=" +
                (threads.getThreadCount() > 0));
        System.out.println("uptimeNonNegative=" +
                (runtime.getUptime() >= 0L));
        System.out.println("processorsPositive=" +
                (Runtime.getRuntime().availableProcessors() > 0));
    }
}
```

Biên dịch/chạy Java 17:

```bash
javac --release 17 JvmMetricsEvidenceDemo.java
java JvmMetricsEvidenceDemo
```

**Stdout dự kiến, độc lập với chính xác bao nhiêu bytes/threads trong môi trường chạy hợp lệ:**

```text
heapUsedNonNegative=true
threadCountPositive=true
uptimeNonNegative=true
processorsPositive=true
```

**Vì sao chỉ in boolean invariants?** Heap used, thread count và uptime là giá trị thay đổi theo môi trường và thời điểm. Nếu ví dụ in `heapBytes=123456` như một “stdout chính xác cho mọi máy”, đó sẽ là bịa. Với real monitoring, hãy export **giá trị thô kèm timestamp** thay vì chỉ những boolean kiểm tra ở trên.

### 8.1. Một mẫu số liệu cần cảnh giác

Giả sử metrics hiển thị:

```text
heap_used=2.0GiB trước GC
heap_used=0.4GiB sau GC
heap_used=2.2GiB trước GC tiếp theo
heap_used=0.4GiB sau GC tiếp theo
```

Đây là **dữ liệu mô phỏng**, không phải số đo thật. Heap dao động mạnh trước GC nhưng sau GC về cùng baseline có thể chỉ là **allocation churn** bình thường. Nếu **post-GC baseline** tăng đều trong cùng workload và ổn định điều kiện, khi đó mới cần điều tra retention/leak sâu hơn.

## 9. JFR — cầu nối giữa logs, metrics, stack samples và sự kiện JVM

**Java Flight Recorder (JFR)** ghi các events có timestamps, durations và có thể có stack traces. Nó hữu ích khi bạn cần timeline chung:

- GC pauses, allocation samples, CPU load và thread scheduling.
- Thread park/monitor contention hoặc blocked duration (theo event settings).
- Socket/file I/O và exceptions (phụ thuộc recording configuration).
- Compilation/class loading và những runtime events mà JDK implementation cung cấp.
- Các application custom events nếu code ghi bằng `jdk.jfr.Event`.

JFR không thay toàn bộ raw application logs hoặc full heap dump. Nó giúp **correlate theo thời gian**, trong khi các dashboards metrics thường phù hợp cho giám sát dài hạn.

### 9.1. Hai cấu hình JFR phổ biến trong JDK 17

JDK 17 cung cấp các settings files:

- **`default.jfc`:** chọn tập events với overhead thấp, thích hợp recordings dài hạn khi được vận hành đúng.
- **`profile.jfc`:** thu thêm thông tin phục vụ profiling nhưng overhead thường cao hơn; cân nhắc dùng trong khoảng ngắn.

Các event types, thresholds, stack traces và recording options phụ thuộc **.jfc đang dùng**. **Không khẳng định JFR tự ghi mọi allocation, mọi exception, mọi object root path trong mọi recording.** Một số events là sampling hoặc disabled.

### 9.2. Bật JFR từ startup

```bash
java -XX:StartFlightRecording=filename=app.jfr,dumponexit=true,settings=default \
  BytecodeEvidenceDemo
```

File recording được ghi theo cơ chế được cấu hình. Với chương trình quá ngắn, tập events hữu ích có thể ít; ví dụ nhằm minh họa cú pháp, **không cam kết có đủ dữ liệu để kết luận bottleneck**.

### 9.3. Bật JFR cho process đang chạy

```bash
jcmd <pid> JFR.start name=incident settings=profile duration=60s \
  filename=incident.jfr
jcmd <pid> JFR.check
jcmd <pid> JFR.dump name=incident filename=incident-now.jfr
```

Trong HotSpot Java 17, `JFR.start` bắt đầu recording, `JFR.check` cho trạng thái và `JFR.dump` ghi dữ liệu của recording đang chạy vào file. `JFR.dump` không nhất thiết dừng recording. Các paths liên quan JVM đích và permission/filesystem trong môi trường ấy; nếu process đã exit hoặc attach bị chặn, lệnh không hoạt động.

Có thể dùng công cụ dòng lệnh:

```bash
jfr summary incident-now.jfr
jfr print incident-now.jfr
```

**Không in toàn bộ recording trong issue công khai**: JFR có thể chứa thread names, class names, stack traces, I/O paths hoặc custom event payload nhạy cảm.

### 9.4. JFR thu thập gì, không thu thập gì?

| Điều cần điều tra | JFR hỗ trợ như thế nào | Giới hạn |
| --- | --- | --- |
| CPU hotspot | Sampling và stack traces nếu config bật | Không phải trace của mọi CPU instruction |
| Allocation hotspot | Sample events/allocation telemetry phù hợp | Không phải per-object exact ledger mặc định |
| GC pause | Events có duration/timestamp | Phải match với request latency và collector logs |
| Thread blocking | Monitor/park events theo thresholds | Có thể không thấy những waits ngắn hơn threshold |
| File/network I/O | Events theo config/threshold | Không thay distributed tracing |
| Memory leak | Object sampling, root-path mechanisms nếu cấu hình thích hợp | Thường cần heap dump/dominator analysis khi nghi retention |
| Class loading/JIT | Runtime events nếu implementation/config hỗ trợ | Không chứng minh compiler đã phát sinh một machine instruction cụ thể |

**Ghi nhớ:** Evidence vắng mặt trong JFR chỉ có ý nghĩa nếu bạn biết **event được bật, threshold phù hợp, cửa sổ quan sát bao phủ sự cố và không bị mất dữ liệu**.

## 10. Thread dump — dùng khi câu hỏi liên quan threads và locks

**Thread dump** là ảnh chụp trạng thái Java threads ở một thời điểm. Nó thường hiển thị:

- Thread names, identities và `Thread.State`.
- Java stack frames và vị trí method đang chạy/đợi.
- Một số monitors/ownable synchronizers đang giữ hoặc đang chờ.
- Thông tin về deadlock khi JVM phát hiện cycle phù hợp.
- Native frames hoặc thông tin nâng cao tùy JVM/tooling.

Lệnh HotSpot JDK 17:

```bash
jcmd <pid> Thread.print -l
```

Trong đó `-l` yêu cầu thêm thông tin `java.util.concurrent` locks theo JDK 17 `jcmd` manual. Tác động được Oracle phân loại **Medium**, phụ thuộc số lượng threads. Đây **không phải lệnh không chi phí**; một JVM có hàng nghìn threads có thể tốn thời gian thu thập và file output lớn.

### 10.1. Một snapshot chỉ nói thời điểm đó

Nếu thread T đang ở `WAITING` trong `LinkedBlockingQueue.take()`, đó có thể là worker pool idle bình thường. Nếu tất cả workers đang `BLOCKED` chờ cùng monitor và queue backlog tăng, đó là tín hiệu contention đáng điều tra.

Phương pháp tốt:

1. Thu **vài snapshots cách nhau có chủ đích** trong lúc triệu chứng diễn ra.
2. Dùng **cùng PID/service** và kèm timestamps.
3. So các stacks/lock owners và xem threads có tiến triển hay không.
4. Correlate với request rate, queue depth và CPU.
5. Phân biệt legitimate idle, long I/O, lock contention, deadlock và starvation.

### 10.2. Thread states thường bị hiểu nhầm

| State | Ý nghĩa khái quát | Có phải lỗi không? |
| --- | --- | --- |
| `RUNNABLE` | Thread đủ điều kiện chạy/đang thực thi, có thể đang trong native I/O path | Không tự động |
| `BLOCKED` | Đợi vào intrinsic monitor đang do thread khác giữ | Có thể chỉ contention bình thường |
| `WAITING` | Đợi vô hạn qua `Object.wait`/`Thread.join`/`LockSupport.park` | Nhiều worker pools idle là bình thường |
| `TIMED_WAITING` | Đợi có timeout như `sleep`/timed `wait` | Không tự động |
| `TERMINATED` | Thread kết thúc | Có thể là lifecycle bình thường |
| `NEW` | Thread chưa start | Không nhất thiết là lỗi |

**`RUNNABLE` không chắc thread thực sự sử dụng CPU ở khoảnh khắc ấy.** OS scheduling/native operations có thể khiến biểu hiện khác. Để chứng minh CPU busy loop, bổ sung CPU metrics, thread CPU time sampling/JFR/profiler.

### 10.3. Deadlock vs starvation vs quên notify

- **Deadlock:** các threads giữ/chờ resources tạo vòng phụ thuộc, không tiến triển.
- **Starvation:** một thread không có cơ hội tiến triển dù tài nguyên vẫn được dùng bởi threads khác, có thể do unfair scheduling/locks.
- **Missed notification:** thread chờ một condition mà bên kia đã gửi notify trước thời điểm wait và không có guarded predicate đúng.
- **I/O latency:** thread chờ network/disk; không phải monitor cycle.
- **Executor saturation:** workers bận, tasks tích tụ; có thể không có deadlock.

Thread dump giúp nhận ra patterns nhưng không thay điều tra **protocol, lock owners và metrics**.

### 10.4. Không thể từ thread dump suy được mọi dữ liệu heap

Một thread stack có reference tới object nhưng dump văn bản không cung cấp **complete object graph/retained size**. Để biết vì sao hàng triệu objects bị giữ, cần heap dump + GC-root analysis. Ngược lại, heap dump không phải công cụ chính để biết lịch scheduling của thread trong vài giây trước.

## 11. Java 17 runnable demo 3 — thread có thể chờ trên monitor hợp lệ

Tạo file **`ThreadEvidenceDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;

public final class ThreadEvidenceDemo {
    private static final Object LOCK = new Object();
    private static final CountDownLatch STARTED = new CountDownLatch(1);
    private static boolean released;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            synchronized (LOCK) {
                STARTED.countDown();
                while (!released) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            System.out.println("workerFinished=true");
        }, "diagnostic-waiter");

        worker.start();
        STARTED.await();

        long holdMillis = Long.getLong("holdMillis", 0L);
        if (holdMillis > 0L) {
            Thread.sleep(holdMillis);
        }

        synchronized (LOCK) {
            released = true;
            LOCK.notifyAll();
        }

        worker.join();
        System.out.println("mainFinished=true");
    }
}
```

Biên dịch/chạy thông thường:

```bash
javac --release 17 ThreadEvidenceDemo.java
java ThreadEvidenceDemo
```

**Stdout xác định nếu không bị interrupt bất thường:**

```text
workerFinished=true
mainFinished=true
```

Giải thích:

1. Worker lấy `LOCK` và báo `STARTED`.
2. Khi `released=false`, worker gọi `LOCK.wait()` trong vòng `while` và nhường monitor ấy.
3. Main đợi `STARTED` rồi đặt `released=true`, gọi `notifyAll` dưới đúng monitor.
4. Worker phải giành lại monitor, thấy predicate đúng và kết thúc.
5. `join()` đảm bảo dòng `mainFinished=true` chỉ in sau dòng worker.

**Để có cửa sổ thu thread dump trong lab**, chạy một phiên có khoảng giữ 20 giây:

```bash
java -DholdMillis=20000 ThreadEvidenceDemo
```

Trong lúc process còn sống, tìm đúng PID bằng OS tools rồi:

```bash
jcmd <pid> Thread.print -l
```

Thường có thể quan sát `diagnostic-waiter` trong `Object.wait` tại thời điểm đó. **Không hứa** mọi snapshot luôn bắt đúng cùng state; OS scheduling và thời điểm attach khác nhau. Đây là lab về thread waiting **hợp lệ**, không phải ứng dụng deadlock.

### 11.1. Tại sao `CountDownLatch` cần thiết trong demo?

Chỉ gọi `Thread.sleep(100)` ở main rồi giả định worker chắc đã vào wait set là không chính xác. `STARTED.await()` thiết lập một handshake để main biết worker đã vào critical section; vòng `while` giữ chương trình đúng ngay cả khi main đổi predicate trước khi worker thực sự bắt đầu `wait`.

## 12. Heap dump — dùng khi cần object graph và retention

**Heap dump** trong JDK 17 thường ở định dạng HPROF, gồm Java heap objects và references tại thời điểm thu thập, tùy cách dump và VM. Nó hữu ích để phân tích:

- Class nào có nhiều instances.
- Shallow size, retained size và dominator relationships.
- Đường giữ object từ **GC roots**.
- Collection/cache/map nào giữ lại nhiều values.
- Lớp objects nào tăng retained counts qua hai lần chụp dưới workload tương đồng.
- References từ threads/static fields/classloaders tới object graphs.

Một điểm quan trọng: **heap dump là snapshot retained/live heap state tại thời điểm chụp**, không phải nhật ký đầy đủ mọi allocation từng diễn ra. Những objects cấp phát và đã GC trước thời điểm chụp sẽ không còn nằm trong live heap dump.

### 12.1. Heap dump tốn chi phí và có thể gây pause lớn

HotSpot JDK 17:

```bash
jcmd <pid> GC.heap_dump /path/to/incident.hprof
```

Oracle `jcmd` manual đánh giá `GC.heap_dump` có **High impact**, phụ thuộc kích thước heap và lượng nội dung. Mặc định lệnh có thể **yêu cầu full GC trước khi dump**; `-all` cho phép giữ cả objects unreachable nhưng có implications khác.

Không chạy không kiểm soát trên một JVM heap hàng chục GiB ở thời điểm peak traffic chỉ vì “muốn xem RAM”. Các vấn đề:

- **Pause/impact** có thể đáng kể.
- File `.hprof` có thể rất lớn; cần disk quota/space.
- Có thể làm tăng I/O và memory pressure.
- Full GC trước dump có thể thay trạng thái heap bạn muốn quan sát.
- HPROF có thể chứa **PII, tokens, session attributes, request bodies, credentials trong String/byte[]**.
- Dump file cần quyền truy cập và retention policy như dữ liệu nhạy cảm.

### 12.2. `GC.class_histogram` là bước trước heap dump khi phù hợp

```bash
jcmd <pid> GC.class_histogram
```

Lệnh in thống kê số instances/bytes theo class; cũng được Oracle phân loại **High impact**. Nó trả lời “loại object nào đang nổi bật”, nhưng **không chỉ ra reference paths hoặc dominators**.

Nếu app chỉ cần biết class counts và bạn không cần object graph, histogram có thể là bằng chứng đủ cho bước khảo sát. Nhưng nó cũng có overhead và không phải thay thế an toàn tuyệt đối cho việc phân tích retention.

### 12.3. `-all` thay đổi tập object được xem

Theo Oracle JDK 17:

- `GC.class_histogram -all`: thống kê **mọi objects bao gồm unreachable objects** trong phạm vi VM có thể quan sát.
- `GC.heap_dump -all /path/file.hprof`: dump **mọi objects**, không mặc định yêu cầu full GC như chế độ không `-all`.

**Không coi `-all` là “càng chính xác hơn” cho mọi memory leak investigation.** Nếu mục tiêu là tìm objects đang bị giữ sống, cần phân biệt reachable với unreachable. Một histogram có nhiều unreachable objects sau allocation burst không chứng minh chúng là retained leak.

### 12.4. Shallow size vs retained size

Giả sử:

```text
CacheManager
    │
    └── HashMap
          │
          ├─ SessionObject A
          │     └─ byte[1 MiB]
          └─ SessionObject B
                └─ byte[1 MiB]
```

**Shallow size** của `HashMap` hoặc `SessionObject` nhỏ, nhưng **retained size** của một dominator trong object graph có thể lớn vì nó giữ các byte arrays sống. Không nhìn top ten class histogram rồi xóa “lớp có shallow bytes lớn nhất” khi chưa biết GC root/reference path.

Trong tools như Eclipse Memory Analyzer (MAT):

1. Mở heap dump trong môi trường đáng tin cậy.
2. Xem histograms để xác định nhóm lớn.
3. Xem **Dominator Tree** và retained sizes.
4. Xem **Paths to GC Roots**, phân biệt strong/weak/soft references đúng ngữ cảnh.
5. So sánh với baseline dump sau cùng workload/GC policy.
6. Gắn kết quả với code giữ references (static caches, listeners, thread-local, classloader).

**Không coi mọi cache lớn là leak.** Cache có dung lượng được thiết kế và còn được truy cập có thể chiếm nhiều RAM hợp lệ; vấn đề là policy, bound/eviction, retention không mong muốn và áp lực tài nguyên.

### 12.5. Heap dump không bao phủ mọi native memory

Nếu RSS tăng nhưng heap occupancy sau GC ổn định, heap dump có thể không giải thích:

- Thread stacks.
- Direct/native buffers ở mức native backing memory.
- Metaspace/class metadata.
- JIT code cache.
- JNI/native library allocations.
- Allocator fragmentation.
- Memory-mapped files.

Khi đó cần **OS/cgroup metrics + Native Memory Tracking** nếu được bật + app-specific native instrumentation. Không chẩn đoán “heap leak” chỉ từ tổng RSS của container.

## 13. Java 17 runnable demo 4 — retained object graph để lấy heap dump

Tạo file **`HeapEvidenceDemo.java`**:

```java
public final class HeapEvidenceDemo {
    private static volatile byte[][] retained;

    public static void main(String[] args) throws InterruptedException {
        byte[][] arrays = new byte[128][];
        for (int i = 0; i < arrays.length; i++) {
            arrays[i] = new byte[4096];
        }

        retained = arrays;

        System.out.println("retainedArrays=" + retained.length);
        System.out.println("payloadBytes=" + (128 * 4096));

        long holdMillis = Long.getLong("holdMillis", 0L);
        if (holdMillis > 0L) {
            Thread.sleep(holdMillis);
        }

        System.out.println("done=true");
    }
}
```

Biên dịch/chạy bình thường:

```bash
javac --release 17 HeapEvidenceDemo.java
java HeapEvidenceDemo
```

**Stdout chính xác khi không bị interrupt:**

```text
retainedArrays=128
payloadBytes=524288
done=true
```

**Giải thích:**

- Mỗi vòng lặp tạo một `byte[4096]` và lưu reference trong `byte[][]`.
- Có 128 arrays với **payload 524.288 bytes**, chưa cộng object headers/alignment hoặc references trong outer array.
- `retained` là static field, giữ outer array/reference graph khi process còn sống.
- Heap dump/histogram sẽ có **nhiều objects khác của JDK/runtime**; không kỳ vọng 128 là tổng số `[B` instances trong cả JVM.
- Đối tượng cấp phát từ compiler/JVM internals, Strings và framework có thể khiến histogram lớn hơn rất nhiều.

### 13.1. Thu snapshot trong cửa sổ quan sát

```bash
java -DholdMillis=20000 HeapEvidenceDemo
```

Trong lúc app đang ngủ theo cấu hình lab, xác định đúng PID và chạy:

```bash
jcmd <pid> GC.class_histogram
jcmd <pid> GC.heap_dump /tmp/heap-evidence.hprof
```

Đây là ví dụ POSIX; chọn directory thực sự tồn tại và đủ quota. File được tạo bởi **target JVM với quyền của process ấy**, không phải ngầm trên máy terminal khác. Khi môi trường là container, `/tmp` ở namespace của process đích.

**Không lấy file heap dump của production chứa dữ liệu thật để upload vào dịch vụ công cộng.** Trong lab này chỉ có dữ liệu giả; dù vậy heap file vẫn cần được xử lý theo security policy.

### 13.2. Chụp hai heap dumps có đủ kết luận leak chưa?

Chưa chắc. Nếu dump 1 được lấy lúc warm-up và dump 2 sau peak traffic, khác biệt là bình thường. Muốn kết luận leak:

- Giữ workload/test scenario tương đương.
- Ghi GC behavior và điều kiện capture.
- Tập trung **retained objects sau collection** thay vì chỉ tổng before-GC occupancy.
- Tìm GC-root paths/owners và growth qua nhiều thời điểm.
- Kiểm tra cache bound/lifecycle, đặc biệt không giữ references vô tình.

## 14. Native Memory Tracking (NMT): khi heap dump không đủ

HotSpot **Native Memory Tracking** theo dõi các nhóm native memory mà VM quản lý trong phạm vi hỗ trợ. Với JDK 17, thông thường phải **bật từ lúc khởi động**:

```bash
java -XX:NativeMemoryTracking=summary \
  -DholdMillis=20000 HeapEvidenceDemo
```

Khi process còn sống:

```bash
jcmd <pid> VM.native_memory summary
jcmd <pid> VM.native_memory baseline
jcmd <pid> VM.native_memory summary.diff
```

`VM.native_memory` có các dạng `summary`/`detail` và hỗ trợ baseline/diff khi NMT đang bật, tùy build. NMT có overhead, thường không bật mặc định, và **không phải công cụ bao quát mọi allocation của mọi native third-party library**. Nó cũng không thay RSS/PSS/cgroup metrics.

Đọc NMT:

- **Reserved** không đồng nghĩa committed RSS.
- **Committed** không đồng nghĩa bytes đang resident trong physical RAM từng thời điểm.
- Một số allocator/native components ngoài vùng được NMT instrument vẫn có thể không được tính đủ.
- “Heap reserved 4 GiB” không có nghĩa heap used đã là 4 GiB.

Nếu process bắt đầu khi NMT chưa bật, không khẳng định `jcmd VM.native_memory summary` sẽ tự tạo được lịch sử đầy đủ của các native allocations từ đầu.

## 15. Log, metrics, thread dump, heap dump: mức độ chi phí vận hành

| Bằng chứng | Tính chất | Chi phí thường gặp | Cảnh giác |
| --- | --- | --- | --- |
| `javap` trên JAR offline | Phân tích static artifact | Thấp, ngoài process | Bảo đảm đúng class/JAR đã deploy |
| Application logs có sẵn | Event history | Thấp đến cao tùy volume | I/O, PII, async loss, missing context |
| Metrics đã instrument | Time-series sampling | Thường thấp nếu cardinality có kiểm soát | Reset/sample gaps, misaggregation |
| GC logging `info` có chọn lọc | Runtime event history | Thường thấp đến vừa | Disk quota, timestamp và GC tags |
| JFR `default` | Event recording | Thường thấp, tùy settings | Sampling/threshold, retention, PII |
| JFR `profile` | Profiling events | Cao hơn default tùy config | Đo overhead trên workload thật |
| `jcmd Thread.print -l` | Thread snapshot | Medium theo Oracle | Số threads lớn/monitor contention |
| `jcmd GC.class_histogram` | Heap class statistics | **High** theo Oracle | Heap traversal/safepoints/impact |
| `jcmd GC.heap_dump` | Object graph snapshot | **High** theo Oracle | Pause, full GC, disk space, secrets |
| NMT `summary/detail` | Native allocation accounting | Có overhead khi được bật | Không thu mọi native allocations |
| Fatal `hs_err` / core dump | Crash evidence | Có thể rất nhạy cảm và dung lượng lớn | Tài liệu điều tra, không phải routine capture |

Các mức này là **định tính**, không phải benchmark overhead phổ quát. Nếu app có heap 200 GiB, một heap dump là quyết định production nghiêm trọng. Nếu app lab có heap nhỏ, tác động có thể chấp nhận được. **Tối ưu evidence theo giả thuyết và mức rủi ro hiện tại**.

### 15.1. Lộ trình thu thập ít xâm lấn trước

Một trình tự hợp lý:

```text
1. Xác nhận correct JVM PID/version/container
       │
       ▼
2. Triệu chứng + workload window + metrics/logs hiện có
       │
       ▼
3. Chọn hypothesis cụ thể
       │
       ├─ bytecode/runtime mismatch → javap/classpath
       ├─ GC/tail latency          → GC logs + JFR
       ├─ lock/thread hang         → thread dumps nhiều mốc
       ├─ retained heap            → histogram → heap dump nếu cần
       └─ RSS ngoài heap           → OS metrics + NMT
       │
       ▼
4. Correlate timestamps và process identity
       │
       ▼
5. Sửa đúng nguyên nhân, chạy lại đo cùng conditions
```

Đừng “thu tất cả trước rồi mới tìm vấn đề” trên production nếu chi phí capture có thể làm hệ thống trầm trọng hơn.

## 16. Sơ đồ suy luận theo symptom

### 16.1. CPU 100% nhưng latency tăng

Không vội heap dump. Bắt đầu:

- Process CPU / thread CPU / JVM vs container throttling.
- Application throughput và request queues.
- JFR CPU sampling/profiler.
- Thread dump nhiều thời điểm để xem hot stacks; `RUNNABLE` riêng lẻ không chứng minh CPU usage.
- GC CPU share và JIT compilation nếu thời điểm startup.

Nếu phát hiện 80% CPU trong một method, xem source/bytecode/algorithm sau, không suy ngay GC.

### 16.2. JVM process treo nhưng CPU gần 0%

Ưu tiên:

- Thread dumps 2–3 lần trong cửa sổ triệu chứng.
- Xác định Java monitor lock owner, ownable synchronizers, blocking I/O.
- Executor metrics: worker count, queue depth, rejections, downstream timeouts.
- Application logs/traces theo correlation ID.
- Nếu cần, JFR monitor/park events để nhìn **lịch sử**, không chỉ snapshot.

Nhận diện **WAITING bình thường** khác “worker bị mất notification” nhờ predicate/queue state và nhiều evidence points.

### 16.3. JVM “OutOfMemoryError: Java heap space”

Ưu tiên:

- Heap occupancy theo thời gian, GC logs.
- `OutOfMemoryError` message và stack trace.
- Heap histogram/dump nếu chính sách capture cho phép.
- Retained sizes/GC roots, growth qua comparable workload.
- So heap `-Xmx` với live set và allocation rate.

Chỉ tăng `-Xmx` có thể trì hoãn lỗi mà không giải quyết leak. Ngược lại, một workload hợp lệ cần heap lớn hơn cũng có thể hết OOM sau capacity sizing; không nên gọi mọi OOM là leak.

### 16.4. Container OOMKilled nhưng không có Java heap OOM

Ưu tiên:

- Container exit reason/cgroup memory events.
- RSS/native memory, thread stacks, direct buffers, metaspace.
- Heap used/max và GC logs để loại trừ nhầm heap.
- NMT nếu trước đó được bật.
- Native/profiler evidence nếu có JNI.
- CPU/memory limits và overcommit/OS allocator behavior.

Heap dump không phải câu trả lời duy nhất khi **cgroup memory limit** bị vượt bởi nhiều loại allocations.

### 16.5. “Một request bị chậm 5 giây”

Ưu tiên:

- Distributed trace span timings.
- Application logs có trace ID.
- HTTP/server queue metrics, DB connection pool waits.
- GC/JFR events trùng thời gian.
- Thread dumps nếu symptom kéo dài đủ lâu.

Một 5s latency chưa chắc có bất cứ Java thread nào bị `BLOCKED` bởi monitor; có thể là network timeout/retry/external service.

### 16.6. Exception “NoSuchMethodError” hoặc “ClassNotFoundException”

Ưu tiên:

- Runtime exception stack trace, classpath/module/classloader logs.
- Class/JAR sources thực tế, artifact hashes, `javap -verbose` descriptors.
- JDK runtime vs compile JDK version.
- Nếu dùng custom loaders, classloader graph/hierarchy khi phù hợp.

Heap dump thường không phải công cụ đầu tiên cho linkage problems.

## 17. Tình huống ghép bằng chứng: memory leak giả và memory leak thật

Giả sử metrics (dữ liệu **giả lập**) như sau:

| Mốc | Heap trước GC | Heap ngay sau GC | Request rate |
| --- | ---: | ---: | ---: |
| T1 | 2.0 GiB | 0.5 GiB | 100 rps |
| T2 | 2.2 GiB | 0.5 GiB | 100 rps |
| T3 | 2.1 GiB | 0.5 GiB | 100 rps |

Bức tranh này phù hợp với **allocation churn**: objects được cấp phát nhiều nhưng có thể được thu hồi. Không có bằng chứng mạnh retained heap đang tăng.

Kịch bản thứ hai (cũng **giả lập**):

| Mốc | Heap trước GC | Heap ngay sau GC | Request rate |
| --- | ---: | ---: | ---: |
| T1 | 2.0 GiB | 0.5 GiB | 100 rps |
| T2 | 2.4 GiB | 1.1 GiB | 100 rps |
| T3 | 2.9 GiB | 1.8 GiB | 100 rps |

Sau-GC baseline tăng mạnh ở cùng workload, **đáng nghi retention**. Lúc này histogram/dump có ích để tìm object graph. Nhưng vẫn cần kiểm tra cache warming, workload mix, concurrent GC accounting, memory pools, và phase của application trước khi tuyên bố leak.

### 17.1. Ba câu hỏi trước khi mở MAT

1. **Đang giữ objects nào?** Counts/shallow bytes theo classes từ histogram.
2. **Ai giữ chúng?** Dominators/paths to GC roots từ heap dump.
3. **Vì sao thời gian sống vượt kỳ vọng?** Cache/ThreadLocal/listener/classloader lifecycle từ source và business behavior.

Một dump không tự trả lời “business policy cần objects sống bao lâu”; kỹ sư phải bổ sung domain knowledge.

## 18. Những vấn đề bằng chứng dễ bị lệch

### 18.1. Sampling bias

- JFR allocation samples không ghi mọi allocation.
- Metrics scrape interval 30s có thể bỏ lỡ GC spikes 20ms nếu chỉ dựa vào gauges.
- Thread dump bắt được thread ở `WAITING` nhưng bỏ lỡ 500ms lock contention ngay trước đó.
- Heap dump sau GC không còn objects đã chết, dù chúng tạo rất nhiều allocation traffic.

Phải xem **sampling/threshold và capture timing** trước khi kết luận “không thấy evidence là không có hiện tượng”.

### 18.2. Observer effect

Việc thu evidence có thể thay hành vi:

- Logging rất chi tiết làm I/O tăng.
- Heap dump yêu cầu heap traversal/GC và chiếm disk/IO.
- CPU profiler sampling có chi phí.
- Debugger/JVMTI instrumentation có thể thay JIT behavior hoặc timing.
- Thread dump có thể tạo safepoint-related perturbations tùy VM.

**Bằng chứng tốt có ghi cả việc bạn đã bật công cụ gì**, để sau đó phân biệt spike do hệ thống với spike do thu thập.

### 18.3. Survivorship bias trong heap dump

Heap dump sau khi app dừng load/đã GC chỉ còn những objects survive. Nó có thể bỏ sót millions temporary objects đã tạo và chết. Nếu câu hỏi là “tại sao allocation rate cao?”, dùng **allocation profiling/metrics/JFR** thay vì chỉ đếm objects còn sống.

### 18.4. Snapshot khác recording theo thời gian

`javap`: static artifact. `thread dump` và `heap dump`: timepoint snapshots. `logs` và `JFR`: event history trong cửa sổ đã được cấu hình. `metrics`: time series theo sampling intervals. Một dấu hiệu quá khứ đã biến mất có thể không có trong snapshot hiện tại.

### 18.5. Dữ liệu từ nhiều pods bị ghép nhầm

Trong Kubernetes, PID có thể là 1 ở nhiều containers. Không ghi `PID=1` rồi xem là unique global identity. Tối thiểu cần **cluster/namespace/pod/container + image digest + timestamp** và đồng hồ phù hợp. `jcmd` attach trong đúng namespace/process; paths của dump file là paths của target JVM environment.

## 19. Kiểm soát bảo mật và lưu trữ artifacts

Các đối tượng cần coi là có thể **nhạy cảm**:

- **Heap dump:** String/byte arrays có thể chứa credentials, session tokens, payloads, PII.
- **Thread dump:** endpoint/file paths, stack arguments trong các context/tooling nhất định, thread names.
- **JFR:** paths, thread names, custom event payloads và một phần environment metadata.
- **Application logs:** request identifiers, user attributes, structured fields.
- **VM.system_properties/command_line:** option `-D...` hoặc arguments có secrets.
- **Fatal error logs:** native libraries, process arguments, paths, memory maps, environment data.

**Quy trình an toàn:**

1. Chỉ thu những artifacts cần thiết cho giả thuyết.
2. Dùng environment thử nghiệm hoặc dữ liệu tổng hợp khi có thể.
3. Trước khi dump production, có quyền phê duyệt/maintenance window nếu impact lớn.
4. Ghi nơi lưu, owner, retention period và access policy.
5. Encrypt artifact at rest/in transit khi cần.
6. Không upload raw production heap dump/JFR vào public issue hoặc third-party tool không được phép.
7. Redact logs/metadata trước khi chia sẻ; chú ý redact plaintext dump không dễ.
8. Hủy bản sao sau khi hết nhu cầu điều tra theo retention policy.

### 19.1. Disk quota là điều kiện tiên quyết trước heap dump

Một JVM heap 8 GiB có thể tạo file dump rất lớn. Kiểm tra free disk, writable path, container filesystem và quota trước; nếu `/tmp` nằm trong ephemeral container layer, artifact có thể mất khi pod bị xóa. Nếu chọn một mount persistent/encrypted, bảo vệ quyền truy cập.

## 20. Lab điều tra có kiểm soát — lựa chọn bằng chứng theo bước

**Lab A: Lỗi bytecode/runtime mismatch.**

1. Compile bằng một JDK newer nhưng target Java 17 (hoặc cố ý bỏ `--release` trong môi trường lab).
2. Dùng `javap -verbose` so major version.
3. Đối chiếu `java -version` của runtime.
4. Quan sát `UnsupportedClassVersionError` khi cố chạy class quá mới trên runtime cũ.
5. Sửa bằng target release và test trên runtime mục tiêu.

Không tạo lỗi này trên production, và không khẳng định một thông điệp exception chính xác cho mọi JDK build.

**Lab B: Thread waiting hợp lệ.**

1. Chạy `ThreadEvidenceDemo` với `-DholdMillis=20000`.
2. Thu `jcmd PID Thread.print -l` đúng lúc app đang chờ.
3. Nhận diện `diagnostic-waiter` và method `Object.wait`.
4. So state với application predicate `released=false`.
5. Cho app kết thúc tự nhiên và xác nhận stdout không đổi.

**Lab C: Heap retention.**

1. Chạy `HeapEvidenceDemo` với cửa sổ hold.
2. Thu `GC.class_histogram` và `GC.heap_dump` (lab nhỏ, có đủ disk).
3. Mở dump trong MAT, tìm `HeapEvidenceDemo.retained` làm owner.
4. So shallow/retained size; chú ý `byte[][]` và `byte[]` khác class types.
5. Đừng xem tất cả `[B` instances trong heap là do chương trình demo.

**Lab D: JVM performance timeline.**

1. Dùng một app workload đủ lâu (không phải demo thoát tức thì).
2. Bật GC log có tags/rotation phù hợp và JFR `default`.
3. Export request latency metric + CPU/heap/GC counters.
4. Khi thấy p99 spike, mở cùng time window trong JFR và application logs.
5. Nếu có lock contention, lấy thread dump để đối chiếu; nếu heap retention tăng, cân nhắc dump sau khi đánh giá impact.

**Tiêu chí hoàn thành lab:** Bạn nêu được rõ “bằng chứng nào hỗ trợ giả thuyết X”, “bằng chứng nào phản bác nó”, “phạm vi quan sát bị thiếu gì”, và “quyết định tiếp theo ít xâm lấn nhất”.

## 21. Runbook tối giản cho incident production

### 21.1. Bước 1 — định danh đúng process

```bash
jcmd -l
jcmd <pid> VM.version
jcmd <pid> VM.command_line
```

Oracle JDK 17 ghi rõ `jcmd` cần quyền attach phù hợp. `jcmd -l` có thể không liệt kê Java processes trong một số Docker contexts; khi đó dùng process list/container tools để xác định PID, không đoán.

### 21.2. Bước 2 — giữ evidence đã có

- Bảo toàn application errors/logs/trace IDs.
- Chụp relevant dashboards với metric names, units, timestamps.
- Lưu deployment/config/flag changes gần thời điểm lỗi.
- Ghi JDK vendor, build và GC choice.

### 21.3. Bước 3 — chọn công cụ theo triệu chứng

| Triệu chứng | Ưu tiên đầu | Bằng chứng bổ sung |
| --- | --- | --- |
| Class linkage error | Stack trace + deployed JAR/`javap` | Class loading logs |
| High GC pause | GC log + pause metrics | JFR, allocation profiler |
| Hung requests | Thread dumps + request traces | Executor/lock metrics, JFR |
| Heap retained growth | Heap time series/post-GC baseline | Class histogram/dump, MAT |
| RSS native growth | OS/cgroup memory + NMT | Native library info, direct buffers |
| High CPU | CPU metrics + JFR sampling | Thread dumps, compiler/GC logs |
| Unexpected startup overhead | Startup trace/class-load/GC logs | JFR/metrics and archive settings |

### 21.4. Bước 4 — ghi điều chưa chắc

Ví dụ:

```text
Hypothesis: request timeout do monitor contention.
Evidence: 3 thread dumps trong 10s có 100 threads BLOCKED
          cùng monitor, owner giữ monitor trong JDBC call.
Unknown:  đây có phải nguyên nhân duy nhất của p99 spike?
Next:     correlate request trace IDs và lock wait durations.
```

Đó là **báo cáo điều tra mẫu**, không phải kết quả đo thực. Có bằng chứng mạnh nhưng vẫn cần tìm causal link và validation sau fix.

### 21.5. Bước 5 — kiểm nghiệm sửa lỗi

Sau thay đổi code/config:

- Cùng load profile/traffic window đại diện.
- So p99/error rate/throughput/GC/rejected tasks.
- Xác nhận thread states hoặc retained heap giảm theo kỳ vọng.
- Đảm bảo kết quả nghiệp vụ, concurrency và memory safety không bị ảnh hưởng.
- Lưu lại before/after evidence và các điều kiện so sánh.

## 22. Những nhầm lẫn thường gặp

1. **“`javap -c` là machine code mà C2 đã tạo.”** Sai: đây là Java bytecode.
2. **“Bytecode có `new` là runtime chắc đã tạo heap object.”** Sai: JIT có thể loại allocation.
3. **“Thấy `invokeinterface` thì ứng dụng chắc không được inline.”** Sai: JIT có thể devirtualize.
4. **“Application log không báo exception nghĩa không có exception.”** Sai: log level/sampling/async failure có thể bỏ lỡ.
5. **“Một dòng GC log đồng nghĩa nguyên nhân duy nhất của p99.”** Sai: cần correlation/cause evidence.
6. **“Heap used cao luôn là memory leak.”** Sai: live set/cache hoặc churn có thể hợp lệ.
7. **“Một lần heap dump cho biết tất cả allocations từng diễn ra.”** Sai: chỉ các objects phù hợp snapshot.
8. **“Đếm `byte[]` trong histogram là biết ai giữ chúng.”** Sai: cần paths to GC roots/dominator.
9. **“Shallow size bằng retained size.”** Sai.
10. **“GC.class_histogram là free/zero-impact command.”** Sai: Oracle phân loại High impact.
11. **“GC.heap_dump là zero-pause diagnostic.”** Sai: có thể High impact và yêu cầu GC.
12. **“Heap dump giải thích toàn bộ process RSS.”** Sai: native/off-heap memory ở ngoài Java heap.
13. **“`VM.native_memory` đo mọi native allocation của mọi JNI library.”** Không bảo đảm.
14. **“NMT bật mặc định và tự hồi cứu từ khi process start.”** Sai: cần cấu hình từ startup theo HotSpot.
15. **“`Thread.State.WAITING` chứng minh deadlock.”** Sai: có thể idle.
16. **“`Thread.State.RUNNABLE` chắc thread đang tiêu thụ CPU 100%.”** Sai.
17. **“Một thread dump đủ chứng minh starvation kéo dài 10 phút.”** Sai: cần nhiều thời điểm.
18. **“`Thread.print -l` liệt kê đầy đủ mọi tài nguyên ngoài JVM như DB lock.”** Sai.
19. **“`jcmd -l` chắc thấy mọi container JVM trên host.”** Sai.
20. **“Có thể dùng PID 1 của container làm PID host ở mọi nơi.”** Sai.
21. **“`jfr summary` cung cấp full heap object graph.”** Sai.
22. **“JFR default ghi mọi allocation và mọi I/O call.”** Sai: settings/threshold/sampling.
23. **“`JFR.dump` luôn dừng recording.”** Sai: dump có thể ghi mà recording vẫn chạy.
24. **“Không có JFR event nghĩa không có sự kiện.”** Sai nếu event disabled/threshold/window không khớp.
25. **“Cộng p99 latency của các pods rồi chia số pods là cluster p99.”** Sai: quantiles không tổng hợp kiểu arithmetic average.
26. **“`Runtime.maxMemory()` bằng container memory limit.”** Sai: nó liên quan heap tối đa.
27. **“GC logs luôn có thể tạo lại quá khứ khi mới bật logging.”** Sai.
28. **“High CPU thì heap dump là công cụ đầu tiên.”** Thường sai: metrics/JFR sampling phù hợp hơn.
29. **“NoSuchMethodError thì heap dump là công cụ đầu tiên.”** Thường sai: classpath/bytecode/runtime.
30. **“Heap histogram thấy class lớn nhất là thủ phạm leak.”** Chưa đủ: owner, design/lifecycle và GC roots quan trọng.
31. **“Hai heap dumps khác kích thước chứng minh leak.”** Sai: khác workload/GC state có thể gây khác.
32. **“Không có `OutOfMemoryError` nghĩa container không bị OOM kill.”** Sai: cgroup có thể kill process.
33. **“Bật trace log vô hạn trong production là vô hại.”** Sai: CPU/disk/PII risks.
34. **“JFR và heap dumps an toàn để upload vào public tracker.”** Sai: có thể chứa bí mật.
35. **“Timestamp của `System.nanoTime()` giống UTC wall clock.”** Sai.
36. **“Metrics từ pod cũ và thread dump pod mới có thể tự ghép chỉ vì cùng service name.”** Sai.
37. **“Một fix làm heap used giảm chứng minh root cause được giải quyết.”** Chưa đủ: cần workload/correctness và post-GC metrics.
38. **“Có log thì không cần metrics/traces.”** Sai: mỗi loại evidence có độ hạt và câu hỏi khác.
39. **“Có metrics thì không cần application-level logs khi debug request cụ thể.”** Sai: metrics aggregated không biết request identity.
40. **“Thread dump có chứa complete retained object graph.”** Sai.
41. **“Bytecode inspection cần chạy chính JVM production đang tải cao.”** Không; có thể làm offline từ exact JAR.
42. **“Mọi JVM/vendor đều nhận giống hệt HotSpot flags và jcmd commands.”** Sai: kiểm tra implementation/build.

## 23. Câu hỏi tự kiểm tra kèm đáp án

**Câu 1.** Ứng dụng báo `UnsupportedClassVersionError`. Nên lấy heap dump hay bytecode trước?

**Đáp án:** Bytecode/class major version từ deployed artifact qua `javap -verbose` và runtime `java -version`; heap dump không phù hợp giả thuyết.

**Câu 2.** Một JVM có heap usage cao nhưng post-GC baseline ổn định. Có thể kết luận memory leak chưa?

**Đáp án:** Chưa; có thể là allocation churn hoặc cache hợp lệ. Cần trend và object retention analysis nếu có dấu hiệu.

**Câu 3.** Thấy thread ở `BLOCKED`. Cần làm gì trước khi nói deadlock?

**Đáp án:** Xem lock owner, chu trình chờ, nhiều thread dumps và executor/queue metrics; contention ngắn không nhất thiết deadlock.

**Câu 4.** Cần biết tại sao `byte[]` sống lâu. `GC.class_histogram` có đủ không?

**Đáp án:** Chưa đủ; histogram đếm theo class, cần heap dump + GC-root/dominator paths để xác định owners.

**Câu 5.** JVM RSS tăng nhưng heap sau-GC không tăng. Chọn evidence nào?

**Đáp án:** OS/cgroup memory metrics, NMT nếu enabled, native allocations/direct buffers/threads/metaspace; heap dump chỉ bổ trợ.

**Câu 6.** JFR không có allocation event trong một recording. Có chứng minh app không allocate?

**Đáp án:** Không; xem event enablement, threshold/sampling và window.

**Câu 7.** Vì sao cần timestamp/uptime cho GC logs và request traces?

**Đáp án:** Để correlates cùng incident window và tránh ghép nhầm pod/clock domains; đó vẫn chỉ chứng minh tương quan, không tự chứng minh nhân quả.

**Câu 8.** `-XX:StartFlightRecording` cùng `jcmd JFR.start` khác nhau ra sao?

**Đáp án:** Một cái bật từ JVM startup, cái kia yêu cầu runtime đang chạy qua attach; đều cần settings/permission và xem cost.

**Câu 9.** Thread dump có thể thấy `Object.wait` nhưng không thấy predicate readiness. Vì sao?

**Đáp án:** Stack snapshot cho vị trí code, không tự hiển thị toàn bộ business state; phải đọc code/logs/metrics liên quan.

**Câu 10.** `GC.heap_dump` trên heap lớn có chi phí gì?

**Đáp án:** High impact, thường request full GC trừ khi option thích hợp, file có thể rất lớn và chứa dữ liệu nhạy cảm.

**Câu 11.** `HeapEvidenceDemo` tạo payload bao nhiêu bytes?

**Đáp án:** **524.288 bytes** từ 128 × 4.096; không bao gồm headers/alignment/outer references.

**Câu 12.** `BytecodeEvidenceDemo` in sum bằng bao nhiêu?

**Đáp án:** **10** từ 2 + 3 + 5.

**Câu 13.** `ThreadEvidenceDemo` in `mainFinished` trước `workerFinished` không?

**Đáp án:** Không, nếu không bị interrupt bất thường; `worker.join()` làm main đợi worker hoàn tất rồi mới in `mainFinished`.

**Câu 14.** Vì sao `javap` có `invokeinterface` chưa chứng minh runtime dispatch chậm?

**Đáp án:** Optimizing JIT có thể devirtualize/inline dựa trên profile và giữ Java semantics.

**Câu 15.** Một metrics counter giảm về 0 sau deployment có nghĩa hệ thống tự phục hồi?

**Đáp án:** Không; có thể counter reset do process restart, cần phân biệt reset và rate.

**Câu 16.** Vì sao không nên chụp nhiều raw heap dumps production tùy tiện?

**Đáp án:** Impact, disk/resource pressure và privacy/security concerns cao; cần hypothesis, quyền/phương án lưu trữ.

**Câu 17.** Một trace thấy 5s chờ DB, còn GC pause 20ms cùng khoảng. Nguyên nhân chính có thể là gì?

**Đáp án:** Trace gợi ý DB/downstream latency chiếm phần lớn; 20ms GC pause có thể chỉ đóng góp nhỏ, phải xác nhận bằng timeline, không quy mọi chậm cho GC.

**Câu 18.** Cần đo CPU hotspot và monitor contention theo timeline, chọn gì?

**Đáp án:** CPU metrics + JFR/profile events, bổ sung thread dumps và stack samples nếu symptom kéo dài.

**Câu 19.** Khi `jcmd -l` không thấy JVM trong Docker, làm gì?

**Đáp án:** Xác định PID đúng bằng process/container tools trong đúng namespace; kiểm tra attach quyền, không đoán process đã tắt.

**Câu 20.** Nếu sửa code xong, cần evidence gì để xác nhận fix?

**Đáp án:** Before/after cùng workload, error rates/p99/throughput/GC/heap metrics và chỉ số liên quan giả thuyết, cùng kiểm thử correctness.

## 24. Checklist trước khi quyết định thu bằng chứng

- [ ] Đã ghi **triệu chứng cụ thể** và mốc thời gian xảy ra.
- [ ] Đã xác định **đúng JVM process**, JDK build/vendor, container/pod, deployment.
- [ ] Đã có **giả thuyết có thể bác bỏ** chứ không chỉ “JVM chậm”.
- [ ] Đã phân biệt **static bytecode** với **runtime JIT machine code**.
- [ ] Đã tận dụng **logs/traces/metrics có sẵn** trước khi thu evidence tốn tài nguyên.
- [ ] Đã so **time-series** thay vì một gauge/snapshot rời rạc.
- [ ] Đã chọn số lượng và thời điểm thread dumps cần thiết.
- [ ] Đã phân biệt live heap, allocations và native memory.
- [ ] Đã đánh giá overhead của `GC.class_histogram`/`GC.heap_dump`.
- [ ] Đã xác minh quyền attach, disk capacity và dump storage location.
- [ ] Đã biết JFR event settings/default vs profile và sampling thresholds.
- [ ] Đã ghi GC logger tags, time decorators và log retention.
- [ ] Đã chuẩn bị **redaction/access control** cho HPROF/JFR/logs.
- [ ] Đã có người chịu trách nhiệm xử lý artifacts và thời hạn lưu.
- [ ] Đã ghi những kết luận chưa được chứng minh sau vòng evidence đầu.
- [ ] Đã xác định bài kiểm tra hậu sửa để xác nhận tác động thực.

## 25. Bản đồ lựa chọn công cụ

```text
                TRIỆU CHỨNG JVM / JAVA
                         │
                         ▼
             Đúng JVM? PID? JDK? Time?
                         │
                         ▼
               GIẢ THUYẾT CỤ THỂ
                         │
     ┌───────────────────┼───────────────────────┐
     │                   │                       │
 class/bytecode      runtime event            memory/thread
  mismatch            hoặc latency              symptoms
     │                   │                       │
     ▼                   ▼                       ▼
 javap/JAR         logs + metrics       ┌────────┴──────────┐
 classpath         time series          │                   │
     │                   │            hang                retained heap
     │                   │              │                   │
     │                   ▼              ▼                   ▼
     │               JFR/traces     thread dumps         histogram
     │                + GC logs      nhiều mốc            │
     │                   │              │                   ▼
     │                   │              │                heap dump
     │                   │              │               + GC roots
     └───────────────────┴──────────────┴───────────────────┘
                         │
                         ▼
         CORRELATE process/time/workload/config
                         │
                         ▼
              EVIDENCE vs INFERENCE
                         │
                         ▼
              FIX + VERIFY UNDER LOAD
```

**Điểm nhớ cuối cùng:** Công cụ “mạnh” nhất không phải thứ thu được nhiều bytes nhất, mà là thứ **giảm bất định của giả thuyết với chi phí và rủi ro thấp nhất**. Bytecode giải thích *class artifact có gì*; log ghi *events đã phát ra*; metrics cho biết *mức độ và xu hướng*; thread dump cho thấy *trạng thái threads và locks tại một thời điểm*; heap dump cho thấy *object graph và retention tại một thời điểm*. **JFR** liên kết thêm nhiều sự kiện JVM trên một timeline, còn OS/NMT hỗ trợ phần native memory không nằm trong heap. Luôn đối chiếu các nguồn với **đúng PID, đúng thời gian, đúng workload và cùng build** trước khi tuyên bố nguyên nhân.

### Tài liệu tham khảo Java 17 và công cụ phân tích

- [Oracle JDK 17 — `jcmd` Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `Thread.print`, `GC.class_histogram`, `GC.heap_dump`, `GC.heap_info`, `JFR.start`/`JFR.dump`, `VM.native_memory`.
- [Oracle JDK 17 — `javap` Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): `-c`, `-p`, `-verbose` và class disassembly.
- [Oracle JDK 17 — `jstat` Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html): `-gc` và VM performance counters.
- [Oracle JDK 17 — `java` Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xlog`, `StartFlightRecording` và HotSpot options.
- [Oracle JDK 17 — `jfr` Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html): mở/in/thống kê Flight Recorder files.
- [Oracle JDK 17 — JFR Configurations](https://docs.oracle.com/en/java/javase/17/jfapi/flight-recorder-configurations.html): `default.jfc` vs `profile.jfc` và thresholds.
- [Java SE 17 — `java.lang.management`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/package-summary.html): `MemoryMXBean`, `ThreadMXBean`, `GarbageCollectorMXBean`, `RuntimeMXBean`.
- [Java SE 17 — JFR module API](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/package-summary.html): events, custom recordings và event configurations.
- [Java SE 17 — JVM Class File Format](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): phân biệt class file bytecode và runtime machine code.
- [Eclipse Memory Analyzer](https://eclipse.dev/mat/): heap dump histogram, dominator tree và paths to GC roots.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-002">Unified Logging với -Xlog cho GC, nạp lớp và safepoint</a>

<details>
<summary>Xem chi tiết</summary>

# Unified Logging với `-Xlog` cho GC, nạp lớp và safepoint

## 1. Vì sao cần Unified Logging thay vì bật thật nhiều cờ debug rời rạc?

Khi ứng dụng Java đột nhiên chậm, pause p99 tăng hoặc bị nghi ngờ nạp lớp động quá nhiều, ba câu hỏi thường xuất hiện:

1. **Garbage Collection (GC)** có xảy ra, collector đang làm gì và pause được JVM báo là bao lâu?
2. **Class loading / class initialization** có xảy ra ở đúng giai đoạn request chậm không? Lớp đến từ classpath, JAR, loader hoặc archive nào?
3. **Safepoint** nào đã yêu cầu phối hợp các Java threads, mất bao lâu để đạt safepoint và thực hiện VM operation?

Trên **HotSpot JDK 17**, **Unified JVM Logging** cho phép cấu hình logging theo một mô hình thống nhất: **tag sets, levels, outputs, decorators và rotation** bằng tùy chọn `-Xlog`. Nó thay thế hoặc chuẩn hóa nhiều cờ logging JVM cũ của các JDK trước.

**Giới hạn quan trọng:** Unified Logging phản ánh **những event mà JVM có hỗ trợ và đã bật ở mức log tương ứng**. Log không phải một profiler sampling đầy đủ, không thay thế **JFR**, không bảo đảm biết every application method call, và bản thân logging có **CPU/I/O/volume overhead**. Đặt mức log vừa đủ để trả lời một giả thuyết kỹ thuật.

> **Cốt lõi:** `gc`, `class+load`, `class+init` và `safepoint` là những **nhóm thông điệp khác nhau**. Một event class load **không chứng minh `<clinit>` đã chạy**; một event safepoint **không chứng minh GC đã chạy**; một GC pause **không mô tả toàn bộ application latency**.

## 2. Phạm vi phiên bản: JDK 8, 9 và Java 17

- **JDK 8** thường dùng các flags cũ như `-verbose:gc`, `-XX:+PrintGCDetails`, `-XX:+PrintGCDateStamps`, `-XX:+TraceClassLoading`.
- **JDK 9+** đưa **JEP 158 Unified JVM Logging**, và GC logging theo Unified Logging được chuẩn hóa thêm trong **JEP 271**.
- **Java 17 HotSpot** hỗ trợ `-Xlog` với những selectors/tags thông dụng: `gc`, `class+load`, `class+init`, `safepoint`, cùng rotation và `jcmd VM.log` runtime reconfiguration.
- **JDK 21/24+** có thể thêm hoặc thay đổi chi tiết của tags/events, collectors, log messages và defaults. **Không sao chép một log JDK 24 rồi tuyên bố stdout Java 17 bắt buộc giống hệt**.
- Unified Logging trong bài là **cơ chế HotSpot**, không phải phần syntax/output buộc mọi JVM implementation ngoài HotSpot phải trùng.

Để biết những tag/level/decorator/output options **JVM đang chạy thực sự hỗ trợ**, hãy hỏi chính nó:

~~~bash
java -Xlog:help
~~~

**Dùng `java -Xlog:help` trước khi thử một selector lạ**. Nếu thiếu tag combination, JVM có thể từ chối khởi chạy hoặc cảnh báo rằng selector không hợp lệ; không đánh đồng lỗi syntax với một ứng dụng bị lỗi business logic.

## 3. Cú pháp đầy đủ của `-Xlog`

Oracle JDK 17 mô tả dạng:

~~~text
-Xlog[:[what][:[output][:[decorators][:output-options]]]]
-Xlog:help
-Xlog:disable
-Xlog:async
~~~

Các trường:

| Thành phần | Mục đích | Ví dụ |
| --- | --- | --- |
| `what` | Một hay nhiều log selectors theo tags và level | `gc*=info,class+load=info,safepoint=debug` |
| `output` | Gửi log ra stdout, stderr hoặc file | `stdout`, `stderr`, `file=vm.log` |
| `decorators` | Metadata thêm trước mỗi message | `uptimemillis,level,tags,pid,tid` |
| `output-options` | Chính sách file rotation | `filecount=5,filesize=10M` |
| `help` | In tags/levels/options | `java -Xlog:help` |
| `disable` | Tắt cấu hình logging hiện tại, kể cả warnings mặc định | Chỉ dùng khi có lý do |
| `async` | Logging bất đồng bộ qua buffer hữu hạn | Có thể **mất message khi buffer đầy** |

**Phân biệt dấu phân cách:**

- `:` ngăn cách **what → output → decorators → output-options**.
- `,` phân tách **nhiều selectors**, **nhiều decorators** hoặc **nhiều output-options**.
- `+` ghép **những tags cùng nằm trong một tag set**, không phải OR.
- `*` sau tag set yêu cầu **match superset** chứa các tags ấy.
- `=` đặt **log level** cho selector.

Ví dụ:

~~~bash
java '-Xlog:gc*=info,class+load=info,safepoint=debug:file=vm.log:uptime,level,tags:filecount=5,filesize=10M' AppMain
~~~

`AppMain` là **tên class minh họa**, phải thay bằng class thật của ứng dụng. Lệnh trên minh họa syntax, không phải stdout của lab bên dưới.

**Không nhầm `-Xlog` với `-Xmx` hay application logger** như SLF4J/Logback: JVM Unified Logging ghi **JVM internals**; Logback ghi những messages mà application/framework chủ động phát. Hai loại cần correlated qua timestamp, process ID hoặc trace ID phù hợp.

### 3.1. Mức độ log

Các levels thông dụng:

~~~text
off | error | warning | info | debug | trace
~~~

Thứ tự độ chi tiết thông thường từ ít đến nhiều là `error → warning → info → debug → trace`. Khi chọn `debug`, những messages cùng tag selector ở mức debug và các mức nghiêm trọng hơn có thể xuất hiện; không chỉ đúng messages được gắn nhãn `debug`.

- `warning`, `error`: thông tin bất thường, thường đã được bật theo default warning policy.
- `info`: sự kiện phổ biến hữu ích để theo dõi GC/pause/class load.
- `debug`: thêm chi tiết để điều tra nguyên nhân.
- `trace`: cực nhiều, thường chỉ dùng một khoảng ngắn trong môi trường kiểm soát.
- `off`: tắt matching messages.

**Mức chi tiết không đồng nhất trên mọi tag**. `gc+heap=debug` có thể mở ra heap-region details, còn `class+load=debug` có loại messages khác. Không có cam kết mọi subsystem đều phát message tại mọi level.

### 3.2. Tags: một tag set không phải nhiều tùy chọn độc lập

Ví dụ:

~~~bash
java -Xlog:gc AppMain
java '-Xlog:gc+heap=debug' AppMain
java '-Xlog:gc*=info' AppMain
java '-Xlog:gc,safepoint' AppMain
~~~

- `gc` **không có wildcard**: khớp **exact tag set** `gc` trong việc chọn message theo quy tắc selector, không lấy mọi `gc,heap` hay `gc,start`.
- `gc+heap=debug`: khớp tag set chứa đúng `gc` **và** `heap` ở mức yêu cầu; không có nghĩa “log GC hoặc log heap”.
- `gc*=info`: khớp tag sets có **ít nhất tag gc**, gồm `gc`, `gc,heap`, `gc,cpu`, `gc,start`...; có thể in nhiều hơn rất nhiều.
- `gc,safepoint`: hai **selectors độc lập** (OR giữa các selectors), không phải tag set `gc+safepoint`. Có thể không chọn message có tag set chính xác `gc,safepoint` nếu không có wildcard.

Một trong những lỗi phổ biến nhất là bật `-Xlog:gc` rồi bảo JVM “không ghi heap detail”. **Cần `gc*` hoặc tag set chính xác tương ứng** để yêu cầu thông điệp chuyên sâu hơn.

### 3.3. Các shorthand cơ bản

~~~bash
java -Xlog:gc AppMain
java '-Xlog:gc=debug' AppMain
java '-Xlog:class+load=info' AppMain
java '-Xlog:safepoint=info' AppMain
~~~

Nếu chỉ cần stdout cho một trường hợp lab, không cần cung cấp `output` hay `decorators`. Nhưng trên service thực, file output với rotation và PID rõ ràng sẽ dễ quản lý hơn.

## 4. Output: stdout, stderr, file và log rotation

### 4.1. Ghi file với path rõ

~~~bash
java '-Xlog:gc*=info:file=gc.log:uptime,level,tags:filecount=5,filesize=20M' AppMain
~~~

`file=gc.log` tạo file ở **working directory** của JVM. Nếu service chạy qua systemd, container hoặc IDE, working directory có thể không phải nơi bạn nghĩ. Nên xác minh đường dẫn có quyền ghi, đủ disk và đúng log shipping policy.

Để tránh nhiều JVM cùng ghi vào một file, JVM cho phép mở rộng:

- `%p`: **PID**.
- `%t`: timestamp khi JVM startup theo format launcher hỗ trợ.

~~~bash
java '-Xlog:gc*:file=gc-%p-%t.log:time,level,tags:filecount=5,filesize=10M' AppMain
~~~

Với Kubernetes, log ra stdout/stderr có thể phù hợp hơn file logging nếu agent thu container stdout. Nhưng `-Xlog` file có thể hữu ích khi cần rotation riêng và bảo tồn GC/safepoint evidence.

### 4.2. `filecount`, `filesize`

~~~text
filecount=3,filesize=1M
~~~

- `filesize`: mục tiêu kích thước **ước lượng** trước rotation, không hứa số bytes chính xác.
- `filecount`: số file xoay vòng được giữ **ngoài active file** theo tài liệu JDK 17.
- `filecount=0`: tắt rotation, có nguy cơ file tăng không giới hạn hoặc bị ghi đè khi một process khác trùng tên.
- Với file logging mặc định, JVM cũng có chính sách rotation; **không mặc định log vô hạn** nhưng phải xác minh config thật.
- Rotation file theo JVM **không thay retention** của Docker/Kubernetes/cloud log agent.

**Không bật `gc*=trace,class+load*=trace,safepoint*=trace` vô thời hạn trên production.** Nó có thể chiếm disk, CPU, làm tăng noise và tác động chính độ trễ bạn định đo.

### 4.3. Decorators: chọn clock và tag phù hợp

Các decorators của Java 17 có thể gồm:

| Decorator | Ý nghĩa |
| --- | --- |
| `time` | ISO-8601 wall-clock time |
| `utctime` | UTC wall time |
| `uptime` | Seconds từ lúc JVM start |
| `uptimemillis` | Milliseconds từ lúc JVM start |
| `uptimenanos` | Nanoseconds từ JVM start |
| `pid` | Process identifier |
| `tid` | JVM/native thread identifier theo formatter |
| `level` | `info`, `debug`... |
| `tags` | Log tag set |
| `hostname` | Host name |
| `none` | Không thêm decorators |

Ví dụ:

~~~bash
java '-Xlog:gc,safepoint:file=runtime.log:utctime,uptimemillis,pid,tid,level,tags:filecount=3,filesize=10M' AppMain
~~~

Các tags có thể được JVM in trong một cặp `[...]` như `[gc,heap]`, mức như `[info]`, uptime như `[38ms]`. Không cố định order tùy ý nếu công cụ mô tả thứ tự formatter riêng; chỉ định các fields cần thiết.

**Mốc thời gian rất quan trọng:** `uptimemillis` gắn với lifecycle của **một JVM process**, dễ align những events của chính tiến trình ấy. `utctime` phù hợp hơn để correlate với nginx/application/OS/container logs nếu đồng hồ máy đồng bộ. Không lấy uptime 3,5 giây của JVM A so với uptime 3,5 giây của JVM B mà bảo chúng xảy ra đồng thời.

## 5. Logging GC: chọn mức nào để trả lời câu hỏi nào?

GC logs là công cụ quan sát công việc collector, heap occupancy và pause events. Với Java 17 HotSpot, các recipes:

| Mục tiêu | Selector | Chi tiết |
| --- | --- | --- |
| Tóm tắt GC pause | `-Xlog:gc=info` | Thường in events với collector/GC ID, trước/sau heap, pause |
| GC sub-phases/tags liên quan | `-Xlog:gc*=info` | Gồm `gc`, `gc,start`, `gc,heap`, `gc,cpu`... |
| Phân tích heap cụ thể | `-Xlog:gc+heap=debug` | Một tag combination chuyên biệt |
| Phân tích các phases | `-Xlog:gc+phases=debug` | Tùy collector/JDK hỗ trợ |
| Safepoint cùng GC | `-Xlog:gc*,safepoint*=info` | Correlate STW events và VM-operation timing |
| Chỉ ghi file tối giản | `-Xlog:gc:file=gc.log:uptime,level,tags` | Volume nhỏ hơn `gc*` |

`gc*=info` **không có nghĩa** mọi biến cố đều ở `info` hoặc có cùng cấu trúc giữa G1, Serial, Parallel, ZGC, Shenandoah. GC names/phase types và log volume khác nhau. Một concurrent collector có thể làm **GC work bên ngoài stop-the-world windows**.

### 5.1. Đọc một GC pause mẫu

Một dòng đã được thực sự quan sát trong lab với **HotSpot JDK 24, Serial GC, heap max 32 MiB**:

~~~text
[38ms][info][gc] GC(4) Pause Young (Allocation Failure) 5M->1M(15M) 0.091ms
~~~

Giải thích:

- `[38ms]`: uptime kể từ JVM startup theo decorator, **không phải duration của event**.
- `[info][gc]`: mức log và tag.
- `GC(4)`: identifier của GC cycle/event để correlate các dòng liên quan trong cùng process.
- `Pause Young (Allocation Failure)`: kiểu GC được collector này ghi nhận, reason string **phụ thuộc collector**.
- `5M->1M(15M)`: số heap used trước/sau (đơn vị hiển thị của log) và heap capacity được báo cho event trong trường hợp này.
- `0.091ms`: duration pause được collector log, **không phải thời gian toàn request**, cũng không phải tổng thời gian thread chờ network.

**Cảnh báo dữ liệu:** con số 0.091 ms chỉ là một dòng từ một lần chạy ngắn. Không dùng nó suy ra p99 GC hoặc mức pause của ứng dụng production.

### 5.2. GC causes và full vs young

- `Allocation Failure`: một nhãn nguyên nhân collector chọn báo, không tự nghĩa JVM `OutOfMemoryError`.
- `Pause Young`: hoạt động thu gom vùng trẻ tương ứng collector đó.
- `Pause Full`: phải quan sát event thật và collector/JDK trước khi gọi là Full GC; một dòng concurrent-phase không tương đương STW Full GC.
- “Không có `Pause Full` trong 30 giây” **không chứng minh** app không bao giờ có Full GC ở workload dài.

Nếu service có 99th percentile latency bất thường, hãy đặt GC pause timestamps trên cùng timeline với request latency. Một GC pause 10 ms có thể góp vào request 20 ms, nhưng không thể tự giải thích request mất 2 giây nếu còn DB/network/lock/CPU starvation.

### 5.3. GC heap occupancy không giống object leak proof

`5M->1M(15M)` là heap summary ở một event. Nó không cho biết “object nào sống”, allocation call-site hoặc retained references. Nếu hậu-GC live set tăng qua nhiều cycles, điều tra thêm heap histogram, allocation profiling, JFR và heap dump (khi an toàn), không chỉ log GC.

GC logs cũng **không** đo mọi native/off-heap memory. JVM có thể bị container OOMKilled khi `heap used` thấp nếu direct buffers, metaspace, thread stacks và cgroup charges cao.

## 6. Class loading log: điều gì đã được nạp?

Các selectors thường hữu ích:

~~~bash
java '-Xlog:class+load=info' AppMain
java '-Xlog:class+unload=info' AppMain
java '-Xlog:class+init=info' AppMain
java '-Xlog:class+resolve=debug' AppMain
~~~

`class+load` có thể in **binary name** và nguồn class bytes/classpath/module/CDS archive. Ví dụ trên lab JDK24:

~~~text
[29ms][info][class,load] UnifiedLoggingLab$Deferred source: file:/.../worker7-jvm-208-unified-log/
~~~

Đây là dạng log được **lược bớt đường dẫn**, không phải câu lệnh. `source: shared objects file` có thể xuất hiện ở bootstrap classes được lấy từ CDS archive trên HotSpot; nó không có nghĩa ứng dụng đang dùng một file `.java` thật ngoài filesystem.

### 6.1. Class load khác class initialize

JLS/JVMS phân biệt:

1. **Loading:** tạo runtime representation của class/interface từ nguồn binary.
2. **Linking:** verification, preparation và symbolic resolution có thể eager/lazy theo constraints.
3. **Initialization:** chạy static initializers, thường là `<clinit>`, khi có active use phù hợp.

Một class đã được **load** nhưng chưa chắc `<clinit>` được thực thi. Ví dụ Java:

~~~java
Class.forName("pkg.SomeType", false, loader);
~~~

Đây là **mảnh mã minh họa**, `pkg.SomeType` và biến `loader` cần có thật. Overload `false` không yêu cầu class initialization. Ngược lại, truy cập `SomeType.nonConstantStaticField` hoặc gọi static method thường kích hoạt init theo quy tắc JLS.

Để quan sát **initialization**, dùng tag `class+init`:

~~~text
[29ms][info][class,init] ... Initializing 'UnifiedLoggingLab$Deferred' ... by thread "main"
~~~

Lưu ý `class+init` trên JVM hiện tại có thể còn ghi **verification** trước dòng init. Phân biệt chính xác loại message, **không coi mọi dòng gắn tag `class,init` là marker hoàn tất `<clinit>`**. Một dòng “Initializing” cho biết VM bước vào khởi tạo; nếu cần duration chính xác của `<clinit>`, phải instrument/trace chuyên sâu hơn.

### 6.2. Class unload không phải GC liên tục tự động xóa mọi class

Với HotSpot, class unloading gắn với điều kiện lifetime của defining classloader, reachability và collector capability. Một class có thể không unload khi classloader còn reachable. Bật:

~~~bash
java '-Xlog:class+unload=info' AppMain
~~~

“Không thấy unload” không phải classloader leak proof; có thể workload chưa tạo loaders tạm, collector chưa có điều kiện, hoặc class unloading không xảy ra trong thời gian đo. Nếu nghi leak, correlate số lượng classes, metaspace committed, classloader instances, JFR và heap analysis.

### 6.3. Vấn đề volume và dữ liệu nhạy cảm

Một JVM mới có thể nạp hàng trăm hoặc hàng nghìn JDK/framework classes, nên `class+load=info` dễ tạo log lớn **ngay lúc khởi động**. Đường dẫn JAR/classpath trong log có thể tiết lộ đường dẫn deployment, tên class, component names hoặc cấu trúc ứng dụng. Phải kiểm soát quyền file, rotation và log retention.

Không dùng log class-loading làm thay `ClassLoader` API hay artifact dependency graph. Log chỉ cho thấy **các classes VM báo đã load trong khoảng được theo dõi**; class chưa load không nhất thiết không tồn tại trong JAR.

## 7. Safepoint logging: VM dừng các Java threads để làm gì?

**Safepoint** là cơ chế JVM phối hợp execution của những threads ở các điểm phù hợp để thực hiện một số operations yêu cầu trạng thái VM ổn định. Stop-the-world phases của GC là ví dụ quan trọng, nhưng **không phải mọi safepoint đều là GC**. JVM có thể yêu cầu safepoint cho những VM operations khác.

Thêm log:

~~~bash
java '-Xlog:safepoint=info' AppMain
java '-Xlog:safepoint*=info' AppMain
java '-Xlog:safepoint=debug' AppMain
~~~

`-Xlog:safepoint=info` thường cho thông tin **VM operation name**, thời gian đến safepoint, thời gian tại safepoint và tổng độ dài theo phiên bản JVM. `safepoint*` có thể lấy thêm stats/tag sets liên quan. Tùy version và mức, log format khác nhau.

### 7.1. Safepoint time có nhiều thành phần

Một dòng thực tế từ lab JDK24/Serial GC:

~~~text
[38ms][info][safepoint] Safepoint "SerialCollectForAllocation", Time since last: 100500 ns, Reaching safepoint: 1300 ns, At safepoint: 109700 ns, Leaving safepoint: 300 ns, Total: 111300 ns
~~~

Diễn giải:

- `Time since last`: khoảng thời gian từ lần safepoint liên quan trước theo formatter, **không** phải pause của safepoint hiện tại.
- `Reaching safepoint`: thời gian đến khi các threads cần thiết phối hợp tại safepoint, tùy cơ chế JVM.
- `At safepoint`: thời gian thực hiện operation khi VM tại safepoint.
- `Leaving safepoint`: thời gian thoát/tiếp tục theo formatter.
- `Total`: tổng thời gian safepoint log báo; trong ví dụ này `1300+109700+300 = 111300 ns`.
- `SerialCollectForAllocation`: tên VM operation/collector path của **thí nghiệm**.

**Không thay thế `0.091ms GC pause` bằng `111300ns` rồi tuyên bố một nguồn sai**. Số safepoint và số GC có thể khác boundaries, clocks, reporting overhead và scopes; từng event phải correlate theo timestamp/operation.

### 7.2. Safepoint và “time to safepoint” / TTSP

Khi một safepoint request cần phối hợp threads, VM có thể phải **đợi** trước khi tất cả những threads liên quan đạt trạng thái phù hợp. Nếu phần **reaching safepoint** lớn, không nên quy toàn bộ latency cho GC collector work. Hãy xem application threads, native transitions, long-running VM-sensitive operations, OS scheduling và JDK-specific details.

Lưu ý thuật ngữ **TTSP** trong bài đọc/tài liệu có thể diễn giải theo tên metrics/phiên bản khác; luôn đọc **đúng column names** của log JDK đang chạy.

### 7.3. Safepoint khác thread park/lock contention

Một thread chờ `Object.wait()`, socket I/O hay `LockSupport.park()` không đồng nghĩa JVM đã thực hiện một **global safepoint VM operation**. Thread blocking có thể gây latency dù safepoint logs im lặng. Kết hợp **thread dumps, JFR thread events, CPU profiler, dependency timings** để phân biệt.

### 7.4. Concurrent GC và safepoint

GC hiện đại có các pha concurrent và các pha stop-the-world. `-Xlog:gc*=info` có thể ghi nhận work concurrent kéo dài ngoài safepoint event. **Đừng hiểu mọi GC message đều ứng với một lần global application pause**, và không coi một safepoint log đơn lẻ là toàn bộ collector cycle.

## 8. Java 17 runnable: tạo cả class load/init, GC và safepoint

Tạo file **`UnifiedLoggingLab.java`**:

~~~java
public final class UnifiedLoggingLab {
    private static volatile byte[] sink;

    static class Deferred {
        static {
            System.out.println("lazyInit=true");
        }
        static int value = 42;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("main=true");
        Class.forName("UnifiedLoggingLab$Deferred", false,
                UnifiedLoggingLab.class.getClassLoader());
        System.out.println("loadWithoutInit=true");
        System.out.println("value=" + Deferred.value);

        for (int i = 0; i < 96; i++) {
            byte[] block = new byte[256 * 1024];
            block[0] = 1;
            sink = block;
        }
        System.out.println("allocatedArrays=96");
        System.out.println("sinkLength=" + sink.length);
        System.out.println("done=true");
    }
}
~~~

Đây là **toàn bộ chương trình Java 17**, không phải pseudocode; filename/class name khớp. `sink` là `volatile` field để lần ghi mới có khả năng quan sát, giảm nguy cơ JIT loại bỏ toàn bộ work như dead code. Tuy nhiên lab này **không phải benchmark throughput**.

### 8.1. Biên dịch và chạy không bật logging

~~~bash
javac --release 17 UnifiedLoggingLab.java
java -Xms16m -Xmx32m -XX:+UseSerialGC UnifiedLoggingLab
~~~

**Stdout chính xác đã biên dịch/chạy trên OpenJDK 24 với `--release 17`:**

~~~text
main=true
loadWithoutInit=true
lazyInit=true
value=42
allocatedArrays=96
sinkLength=262144
done=true
~~~

Giải thích:

1. `Class.forName(..., false, loader)` có thể load/link nested `Deferred`, nhưng không yêu cầu static initialization.
2. Chương trình in `loadWithoutInit=true` **trước** `lazyInit=true`.
3. Đọc `Deferred.value` (nonconstant static field) kích hoạt initialization, in `lazyInit=true` rồi `value=42`.
4. Mỗi lượt tạo `256 KiB` array, tổng requests cho allocation khoảng `96 × 256 KiB = 24 MiB`; heap rất nhỏ nên collector có cơ hội GC trong khi allocations cũ trở nên unreachable.
5. `sinkLength` chỉ phản ánh array cuối có length `262144` bytes.
6. **Không hứa** mọi JDK/GC sẽ chạy chính xác sáu GC events; số collections phụ thuộc heap layout, allocations khác và policies.

`-XX:+UseSerialGC` được chọn **chỉ để làm lab dễ đọc**, không phải khuyến nghị dùng Serial trong production; chạy ứng dụng thực với collector tương ứng workload và SLO.

### 8.2. Bật ba nhóm logging trong một lệnh

~~~bash
java '-Xlog:gc*=info,class+load=info,class+init=info,safepoint*=info:file=vm-%p.log:uptimemillis,level,tags:filecount=3,filesize=1M' -Xms16m -Xmx32m -XX:+UseSerialGC UnifiedLoggingLab
~~~

Lệnh trên đã được **thực thi thành công** trên JDK24 Windows/PowerShell sau khi compile target 17. Single quotes thích hợp cho Bash/PowerShell; nếu dùng CMD Windows có thể bỏ single quotes hoặc dùng double quotes theo shell. `%p` được **Java launcher** thay bằng PID, không phải biến shell.

**stdout của application vẫn như mục 8.1**; Unified Logging được ghi vào file `vm-<pid>.log` trong working directory.

Với cùng sample JVM24, file log có:

- 621 dòng có tag chứa `class,load`.
- 479 dòng có tag chứa `class,init`.
- 65 dòng có tag bắt đầu `gc`.
- 17 dòng có tag bắt đầu `safepoint`.
- Tổng khoảng 1.182 dòng log.

**Đây là quan sát một lần chạy, không phải kỳ vọng cố định khi chạy Java 17 hoặc collector khác**. Các counts cũng **không** có nghĩa 621 classes application, 479 static initializer invocations hay 65 GC pauses: có events của JDK/bootstrap, nhiều tag sets và multiple lines per GC.

### 8.3. Các dấu vết được nhìn thấy

Trong log lần chạy đó, các marker được tìm thấy theo logic:

~~~text
[class,load] UnifiedLoggingLab$Deferred source: file:/.../
[class,init] ... Initializing 'UnifiedLoggingLab$Deferred' ... by thread "main"
[gc] GC(0) Pause Young (Allocation Failure) 5M->1M(15M) 0.452ms
[safepoint] Safepoint "SerialCollectForAllocation", ... Total: 486900 ns
~~~

Đây là **trích đoạn rút gọn**, có `...` tượng trưng fields/path không cố định. Để xem output nguyên vẹn, đọc file `vm-<pid>.log` của lần chạy thật. Application stdout chứng minh ordering `loadWithoutInit` trước `<clinit>`; log class load và init cung cấp bằng chứng runtime khác, từ VM.

**Không tính chênh lệch `[29ms]` và `[38ms]` thành thời gian `<clinit>` hay GC pause**: timestamps chỉ gắn với các sự kiện được ghi, còn duration cần field duration phù hợp.

## 9. Tách từng nguồn log để phân tích cho đúng

Đôi khi một file chứa cả nghìn dòng khó truy vết. Có thể chạy **nhiều diagnostic runs** với cấu hình khác nhau, nhưng chú ý một run mới là một JVM mới và GC/JIT/classload behavior có thể khác.

### 9.1. Chỉ GC

~~~bash
java '-Xlog:gc=info:file=gc-only.log:uptime,level,tags:filecount=2,filesize=5M' -Xms16m -Xmx32m -XX:+UseSerialGC UnifiedLoggingLab
~~~

Câu hỏi trả lời được: event summaries, pauses được collector log, heap used trước/sau và total capacity liên quan.

**Không** trả lời được: class nào init đầu tiên, stack trace nào nạp class, Java thread nào bị lock-blocked, retained heap objects.

### 9.2. Chỉ class loading và class init

~~~bash
java '-Xlog:class+load=info,class+init=info:file=classes.log:uptime,level,tags:filecount=2,filesize=5M' -Xms16m -Xmx32m UnifiedLoggingLab
~~~

Kiểm tra class nào load từ đâu, class nào bắt đầu init. Cẩn thận `class+init` có thể ghi thêm messages liên quan verification.

**Không** dùng dòng `class+load` làm bằng chứng một class vừa được initialize.

### 9.3. Chỉ safepoint

~~~bash
java '-Xlog:safepoint*=info:file=safepoints.log:uptimemillis,level,tags:filecount=2,filesize=5M' -Xms16m -Xmx32m -XX:+UseSerialGC UnifiedLoggingLab
~~~

Quan sát VM operations, reaching/at/leaving time. Đừng suy luận một dòng safepoint là một Java thread application tự gọi `sleep`.

### 9.4. Correlate trong cùng process

Nếu mục tiêu là biết class loading và safepoint **có trùng request chậm không**, tốt nhất bật **đồng thời** selectors cần thiết trong cùng process, với cùng decorators/time source và PID, thay vì ghép timeline từ hai runs độc lập.

## 10. `jcmd VM.log`: đổi logging ở JVM đang chạy

Với HotSpot JDK 17 và quyền attach thích hợp:

~~~text
jcmd
jcmd PID help VM.log
jcmd PID VM.log list
jcmd PID VM.log output=stdout what=gc=debug
jcmd PID VM.log list
~~~

`PID` trong hướng dẫn là **số PID thực**, phải thay trước khi chạy. `jcmd` cần cùng máy/PID namespace, quyền user phù hợp và JVM còn sống. `VM.log` hỗ trợ các arguments như:

- `list`: liệt kê cấu hình hiện tại.
- `output`: tên hoặc output index (`stdout`, `stderr`, `file=...` tùy hỗ trợ).
- `what`: selectors/levels.
- `decorators`: decorators để in.
- `output_options`: file rotation options.
- `rotate`: rotate configured log files.
- `disable`: tắt logging configuration theo command, **cần hết sức thận trọng**.

**Đã kiểm chứng trên JDK 24:** khi đang chạy một chương trình Java thử ngủ 6 giây, lệnh `jcmd PID VM.log list` liệt kê `#0: stdout all=warning`; tiếp đó `jcmd PID VM.log output=stdout what=gc=debug` trả `Command executed successfully`, và log configuration mới có `stdout all=warning,gc=debug`. Không giả định mọi JDK/VM cho phép thay đổi logging khi thiếu permissions.

### Bật log trong một khoảng chẩn đoán ngắn

Quy trình tốt:

1. Xác minh PID và thời gian maintenance/chẩn đoán.
2. Lưu `jcmd PID VM.log list` để biết cấu hình ban đầu.
3. Chọn output file riêng có giới hạn, selectors hẹp.
4. Tăng verbosity (`debug`) trong khoảng cần đo.
5. Đọc/correlate events với application metrics.
6. Khôi phục logging configuration ban đầu; xác minh lại `VM.log list`.

**Không tắt tất cả warnings/errors mặc định** chỉ vì muốn bớt GC logs. `-Xlog:disable` ở startup hoặc `VM.log disable` làm thay đổi rất nhiều logging, có thể che tín hiệu sự cố quan trọng.

### Có nên dùng `-Xlog:async`?

Java 17 có asynchronous logging mode, tránh synchronous log writes ở nhiều sites bằng cách enqueue vào một buffer hữu hạn. Trên overflow, messages **có thể bị drop**. Vì vậy `-Xlog:async` có thể hữu ích để giảm logging-induced latency nhưng **không phù hợp nếu cần log đầy đủ tuyệt đối cho forensic trace**. Ghi rõ async mode và dropped messages diagnostics khi diễn giải.

## 11. Phương pháp phân tích một latency spike: GC hay safepoint?

Giả sử monitoring báo request p99 500 ms ở 13:10:15 UTC; JVM GC dashboard hiển thị pause trung bình 2 ms. Không được kết luận ngay GC là thủ phạm, cũng không được loại GC chỉ dựa average.

Các bước:

1. Xác minh **đúng JVM PID/container**, JDK, collector và timestamps.
2. Bật hoặc thu logs GC **cùng** `safepoint` trong khoảng nghi vấn.
3. Correlate theo UTC/time + JVM uptime và GC IDs; kiểm tra clock skew.
4. Xem có **STW GC** event khoảng request p99 hay không, pause cụ thể dài bao nhiêu.
5. Xem safepoint **Reaching / At / Leaving / Total**; safepoint khác GC có xuất hiện không.
6. Xem application CPU, thread scheduling, lock/block, DB latency, network timeouts và queue depth.
7. Nếu phần time-to-safepoint cao, không quy hết vào collector work.
8. Nếu toàn bộ GC pauses đều rất ngắn nhưng request chậm 500 ms, tìm **other contributors** thay vì ép giải thích từ GC.
9. Nếu thấy Java heap memory sau GC tăng qua nhiều cycles, mở nhánh điều tra heap retention riêng.
10. Ghi kết luận bằng **evidence**: tag, event timestamp, duration, route/trace/request IDs, không chỉ một ảnh dashboard.

**Lưu ý:** một request latency 500 ms có thể chứa nhiều 1–2 ms pauses cộng thêm queueing/CPU starvation. Một GC event cũng có thể ảnh hưởng nhiều requests, không chỉ request đang đứng đầu.

## 12. Phương pháp phân tích startup chậm: class load hay `<clinit>`?

Một framework service đôi khi báo ready sau 4 giây nhưng logs class load rất nhiều. Không được kết luận “4 giây đều tốn cho loading class” chỉ vì có 10.000 dòng class logs.

Cần chia:

~~~text
OS/process start
  |
  +-- VM bootstrap (trước Java main)
  +-- class load/link/verification
  +-- static initialization <clinit>
  +-- DI/container bootstrap, config, network/dependencies
  +-- readiness / first useful response
  +-- lazy route init và JIT warm-up sau ready
~~~

Quy trình:

1. Đo **process start → readiness** bằng external clock, không chỉ timer đầu `main`.
2. Ghi `class+load` events và đường dẫn nguồn class.
3. Ghi `class+init` và application explicit markers cho từng phần `<clinit>` quan trọng.
4. Tránh bật verbosity quá cao khiến class-load logging tự làm startup chậm.
5. Phân biệt initialization của **main class** trước `main` và các classes lazy init.
6. Correlate DI/context init, DB pool/handshake, config/caches và background tasks.
7. Xem `class+unload`/metaspace nếu nghi loader churn, không chỉ load count.
8. Nếu first request sau ready vẫn chậm, warm-up route đại diện và measure first-hit riêng.

**CDS/AppCDS:** nếu log `source: shared objects file`, có thể liên quan Class Data Sharing. CDS giúp giảm **một phần class-loading/metadata work** trong cấu hình phù hợp; nó **không tự chạy business static initializers hay C2 warm-up** cho mọi path.

## 13. Mức độ log phù hợp: production, staging và lab

| Môi trường / mục tiêu | Gợi ý | Rủi ro |
| --- | --- | --- |
| Production baseline | `gc=info` với rotation/retention và event volume kiểm soát | Có thể chưa đủ phase details |
| Production latency investigation ngắn | `gc*,safepoint*=info` vào file riêng | Thêm log volume, cần điều kiện rollback |
| Staging startup investigation | `class+load=info,class+init=info` | Log hàng nghìn classes, đường dẫn nhạy cảm |
| Focused GC analysis | `gc+heap=debug` hoặc `gc+phases=debug` | Tags/messages phụ thuộc collector |
| Debug class resolution | `class+resolve=debug` trong lab | Rất nhiều output, không phải dấu class init |
| Deep safepoint diagnostic | `safepoint*=debug` và JFR | Có overhead và format JDK-specific |
| Mọi tags `all=trace` | Tránh trừ khi thật sự cô lập vấn đề | Log quá lớn, ảnh hưởng tài nguyên/độ trễ |

**Đặt ngân sách log cùng ngân sách máy chủ:** dung lượng file, filecount, log collector agent, I/O bandwidth, retention và quyền đọc. Logging `class+load=info` trên startup có thể làm file lớn dù app không có lỗi.

Không bật debug logging **vĩnh viễn** trên mọi pod nếu chỉ một instance/route có vấn đề. Có thể dùng run sampling có kiểm soát hoặc bật động một PID bằng `jcmd VM.log`.

## 14. Cách đọc log mà không bị lạc vào hàng nghìn dòng

### Tập trung vào một JVM process

Sử dụng file path gắn PID/time:

~~~text
vm-<pid>.log
gc-<pid>-<startup-time>.log
~~~

Dấu `<...>` trong đây chỉ là notation minh họa tên file, không phải tên bắt buộc. Với file thật, JVM thay `%p`, `%t` khi config file output.

### Tập trung vào loại message

Linux/macOS (đường dẫn phải là file thật):

~~~bash
grep '\[gc' vm-123.log
grep '\[safepoint' vm-123.log
grep 'UnifiedLoggingLab' vm-123.log
~~~

PowerShell:

~~~powershell
Select-String -Path vm-123.log -Pattern '\[gc','\[safepoint','UnifiedLoggingLab'
~~~

`vm-123.log` là **tên ví dụ**; phải thay bằng file generated thực. `[gc]` và `[gc,heap]` đều có thể được lọc theo pattern rộng; nhưng nếu dùng regex tìm tag quá chung có thể match những chuỗi khác. Nên parse decorators/tag sets thay vì tự suy luận kiểu event từ substring bất kỳ.

### Không cộng số dòng như số events

Một **GC event** có thể có `gc,start`, `gc,heap`, `gc,cpu`, `gc` summary, safepoint info và multiple phases. Nếu log có 65 dòng bắt đầu GC tag không có nghĩa đã có 65 GC pauses.

Class loading log nhiều dòng cũng không có nghĩa class count chính xác bằng số lines nếu có source variants, verification events, reload/classloader identities hoặc tag combination khác. Tốt hơn là correlate theo **event IDs/operation name/class identity**, nếu cần dùng parser đúng grammar từng JDK/collector.

### Không tính duration bằng cách trừ hai line timestamps một cách mù quáng

- `[38ms]` có độ phân giải milliseconds, sự kiện dài microseconds.
- Dòng đầu và cuối ở các phases có overhead/ordering khác nhau.
- Nhiều threads có thể ghi events xen kẽ.
- Dùng **field duration của chính event** nếu có; không coi timestamp delta là pause.
- Nếu cần analytics thống kê nhiều GC pauses, dùng parser phù hợp collector/JDK hoặc JFR events đúng configuration.

## 15. Các lỗi cấu hình thường gặp và cách sửa

### `Invalid -Xlog option`

Kiểm tra:

~~~bash
java -Xlog:help
~~~

Xem đúng tags và level. Một dấu `:` sai có thể khiến `file=` bị parse như stdout/other fields. Trong PowerShell, ghép string arguments trước khi gọi nếu path có ký tự đặc biệt; **đừng nối các fragments shell thành nhiều argv khác nhau**.

### `No matching tag set` / selector vô hiệu

Thông điệp cho biết tag combination không tồn tại trên JVM đang chạy hoặc tag không được hỗ trợ. Dùng `-Xlog:help`, lựa chọn tags hợp lệ; không ép dựa vào bài viết về một JDK khác.

### Không thấy GC event

Có thể chương trình chưa allocation đủ, GC không xảy ra trong cửa sổ đo, selector không bao phủ tag sets bạn cần, logger ghi vào file khác hoặc verbosity quá thấp. Không dùng một run trống làm chứng cứ JVM **không có GC**.

### Không thấy class initializer trong `class+load`

`class+load` chỉ ghi load events. Dùng `class+init=info` và kiểm tra active-use của class; `Class.forName(name, false, loader)` không yêu cầu init. **Đừng đổi `false` thành `true` chỉ để tạo log nếu điều đó làm lệch semantics test**.

### Safepoint log trống

Workload có thể chưa thực hiện event cần quan sát trong lúc logging bật, selector/mức chưa phù hợp hoặc JVM dùng cơ chế khác. Đừng tự suy ra “không có bất kỳ threads bị chặn”. Dùng JFR/threaddump và app traces khi điều tra lock/network.

### File không xuất hiện

Kiểm tra working directory, quyền ghi, path quoting, log file name `%p`/`%t`, và stdout/stderr. Với container, file trong filesystem ephemeral có thể biến mất khi pod bị thay thế; cần shipping nếu muốn lưu bằng chứng.

### Log rotation xóa mất khoảng cần phân tích

`filesize` thấp + `filecount` nhỏ, nhất là `class+load*=trace`, có thể quay vòng nhanh. Đặt retention đủ cho cửa sổ quan sát; nhớ rotation size là approximate, không chính xác từng byte.

### Debug logging gây performance regression

Logging có overhead. A/B benchmark performance chỉ có ý nghĩa khi **logger settings giống nhau**; chạy `gc*=debug` trên ứng viên A và `gc=info` trên ứng viên B có thể đo chính logging overhead.

### Nhầm GC pause với global safepoint time

So event IDs/timestamps, đọc phạm vi thời gian `Reaching`, `At`, `Leaving`; không coi hai log lines khác nhau phải báo **cùng số ns**.

## 16. JFR, thread dumps và application tracing bổ sung gì?

Unified Logging rất mạnh cho low-level JVM events, nhưng nhiều câu hỏi đòi công cụ khác:

| Câu hỏi | `-Xlog` giúp gì? | Công cụ bổ sung |
| --- | --- | --- |
| Pause GC ở lúc nào? | `gc`, `gc*`, `safepoint` logs | JFR GC pauses, request tracing |
| Đâu là hot allocation sites? | GC logs cho pressure tổng thể | JFR allocation events, profiler |
| Class load đến từ đâu? | `class+load` | Classloader diagnostics, build deps |
| Vì sao `<clinit>` chậm? | Init event/timestamps | Instrument method, JFR CPU profiling |
| Thread nào đang chờ DB? | Safepoint không đủ | Thread dump, tracing, async profiler |
| Application p99 latency do đâu? | Một phần từ JVM pauses | HTTP spans, queue metrics, DB/network |
| Heap object leak? | Heap occupancy/GC clues | Heap histogram/dump, allocation profiler |
| Native memory leak? | GC/class logs không đủ | NMT, OS/native allocator tooling |

**JFR** có các events về GC, class loading, allocation, compilation, safepoints hoặc thread activity tùy JDK/settings; không phải mọi event luôn bật mặc định. Nếu cần phân tích một cơn latency spike phức tạp, kết hợp `-Xlog` với JFR, CPU profiling và application-level trace spans.

**Thread dumps** có thể chứa tên package, method, code location và thông tin nội bộ. **JFR/heap dumps/logs** có thể chứa dữ liệu nhạy cảm. Lưu và chia sẻ theo chính sách bảo mật.

## 17. Bảng hướng dẫn chọn recipe

| Tình huống | Recipe nên bắt đầu | Bổ sung nếu cần |
| --- | --- | --- |
| Theo dõi GC baseline | `-Xlog:gc=info` | `gc*=info` |
| GC pauses khó giải thích | `-Xlog:gc*=info,safepoint*=info` | `gc+phases=debug`, JFR |
| JVM startup chậm | `-Xlog:class+load=info,class+init=info` | App init spans, CDS, external timer |
| Route đầu tiên chậm | class load/init logs trong traffic thật | JFR/JIT/compiler logs, DB tracing |
| Classloader churn/metaspace | `class+load=info,class+unload=info` | NMT, JFR, heap analysis |
| TTSP hoặc safepoint lạ | `-Xlog:safepoint*=info` | JFR, thread dumps, OS profiler |
| Muốn chẩn đoán một PID | `jcmd PID VM.log list` | Bật động `what=` với rollback |
| Log file quá lớn | Hạ level, giảm tags, chỉnh rotation | Tối ưu sampling/retention |
| Cần startup vs warm comparison | Cùng logging settings/clock | Nhiều process forks, workload giống nhau |

**Không coi bảng là cấu hình global bắt buộc.** Production có throughput, disk, compliance, CPU quota và uptime khác nhau; chọn mức log đủ trả lời câu hỏi, không phải nhiều nhất.

## 18. Checklist đọc log đúng và không làm sai phép đo

- [ ] Ghi JVM vendor/build/JDK version, collector, flags và PID.
- [ ] Chạy `java -Xlog:help` trên chính JVM đang dùng.
- [ ] Phân biệt `gc`, `gc*`, `gc+heap` và selectors cách nhau bởi dấu phẩy.
- [ ] Chọn level `info/debug/trace` có chủ đích; xác minh volume.
- [ ] Đặt decorators chứa uptime/timestamp, level, tags và PID khi cần correlate.
- [ ] Dùng `%p` hoặc `%t` trong file name nếu có nhiều JVM processes.
- [ ] Đặt `filecount/filesize` và quyền log files/retention hợp lý.
- [ ] Đừng bật `-Xlog:disable` chỉ để ẩn những warnings phiền phức.
- [ ] Không dùng log load làm log `<clinit>`; bật `class+init` riêng.
- [ ] Phân biệt class loading, linking/resolution, initialization, unloading.
- [ ] Đọc GC pause duration từ field duration, không trừ timestamps tùy tiện.
- [ ] Phân biệt safepoint reach time, at-safepoint time và total.
- [ ] Không quy mọi safepoint cho GC hoặc mọi GC phase cho safepoint.
- [ ] Không lấy số dòng `gc` thành số collections nếu một GC có nhiều sub-lines.
- [ ] Correlate application request slow windows với VM uptime và wall time đúng clock.
- [ ] Nếu debug/JFR bật gây overhead, ghi lại trong kết quả đo và so settings công bằng.
- [ ] Bật động với `jcmd VM.log` thì lưu config trước và khôi phục sau.
- [ ] Bảo vệ classpath paths, internal identifiers, heap dumps và log artifacts.
- [ ] Với issue liên quan DB/lock/network, kết hợp JFR/thread dump/tracing.
- [ ] Dùng nhiều runs/time windows, không kết luận từ một event duy nhất.

## 19. Tự kiểm tra: 22 câu ngắn và đáp án

1. **`-Xlog` dùng để làm gì?** Cấu hình JVM Unified Logging theo tag/level/output/decorator/rotation.
2. **`gc=info` và `gc*=info` giống nhau không?** Không; `gc*` khớp cả tag sets chứa tag `gc`.
3. **`gc+heap` có nghĩa “gc OR heap” không?** Không; đó là một tag set có cả hai tags.
4. **`gc,safepoint` có cùng nghĩa `gc+safepoint` không?** Không; dấu phẩy tách selectors, dấu cộng kết hợp tags.
5. **`info` có ít chi tiết hơn `debug` không?** Thường có.
6. **`trace` có nên bật lâu trên production không?** Không nếu không có kế hoạch volume/overhead/retention rõ.
7. **Làm sao xác minh tags mà JVM hỗ trợ?** `java -Xlog:help`.
8. **`class+load` chứng minh `<clinit>` đã chạy?** Không.
9. **`class+init` chỉ có message hoàn tất initialization?** Không; có thể ghi các messages liên quan khác, cần đọc nội dung.
10. **`Class.forName(name, false, loader)` có buộc init không?** Không.
11. **`class+unload` trống có tự chứng minh classloader leak?** Không.
12. **`[38ms]` đầu một GC line có phải GC pause 38 ms không?** Không, đó là JVM uptime decorator.
13. **`GC(4)` thường có ích cho việc gì?** Correlate lines cùng GC ID trong một JVM process.
14. **`5M->1M(15M)` có nghĩa mọi Java objects chỉ còn 1 MB trên toàn hệ thống?** Không; đó là heap occupancy/capacity của collector event đó.
15. **Mọi safepoint đều là GC?** Không.
16. **Mọi GC phase đều STW?** Không; nhiều collectors có concurrent phases.
17. **`Reaching safepoint` là GC collection work?** Không nhất thiết; là phần thời gian VM phối hợp tới safepoint.
18. **`filecount=0` có giới hạn tăng trưởng file theo rotation không?** Không; rotation bị tắt.
19. **`%p` trong file name được ai thay?** Java logging runtime dùng PID, không phải một phép cộng của shell.
20. **Có đổi `-Xlog` khi JVM đang chạy không?** Có thể dùng `jcmd PID VM.log` trên HotSpot với quyền thích hợp.
21. **`-Xlog:async` có cam kết không mất message không?** Không; buffer đầy có thể drop messages.
22. **Một dòng GC pause 0.091ms có chứng minh production p99 luôn thấp hơn 1ms không?** Không; chỉ là một event trong một run cụ thể.

## 20. Tham khảo chính thống

- **Oracle JDK 17 `java` command — Enable Logging with the JVM Unified Logging Framework** (cú pháp, tags, levels, decorators, file rotation, async): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Oracle JDK 17 `jcmd`** (`VM.log list`, `output`, `what`, `decorators`, `disable`, `rotate`): https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **OpenJDK JEP 158 — Unified JVM Logging** (thiết kế log theo tags/levels): https://openjdk.org/jeps/158
- **OpenJDK JEP 271 — Unified GC Logging** (chuyển GC logging sang UL): https://openjdk.org/jeps/271
- **Oracle JDK 17 Garbage Collection Tuning Guide** (GC pauses, throughput/footprint và collectors): https://docs.oracle.com/en/java/javase/17/gctuning/
- **JLS 17, Chapter 12 — Loading, Linking and Initialization**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html
- **JVMS 17, Chapter 5 — Loading, Linking and Initializing**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- **Oracle JDK 17 `jfr` command** (bổ sung low-level VM logs với event profiling): https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html

**Kết luận:** Hãy dùng `-Xlog` như một công cụ **đặt câu hỏi có phạm vi rõ**: GC `gc/gc*`, nạp lớp `class+load`, khởi tạo `class+init`, safepoint `safepoint/safepoint*`. Hiểu syntax **exact tag set vs wildcard**, levels, decorators, file rotation, và dùng `jcmd VM.log` để bật/tắt có kiểm soát. Đọc duration từ event fields, phân biệt GC pause với safepoint reaching/at/leaving, và không đánh đồng class load với `<clinit>`. Kết hợp log JVM với **JFR, thread dumps và application tracing**, trên đúng process/timestamp/workload, để xác định nguyên nhân thay vì suy đoán từ số dòng log.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-003">Đọc log GC: nguyên nhân, bộ nhớ trước/sau, dung lượng heap và thời gian dừng</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc log GC: nguyên nhân, bộ nhớ trước/sau, dung lượng heap và thời gian dừng

## 1. Mục đích của việc đọc GC log

Garbage Collection (GC) log là một trong những nguồn bằng chứng quan trọng nhất để phân tích **tình trạng Java heap**, số lần thu gom, hành vi của collector và các khoảng **stop-the-world (STW)** có liên quan tới độ trễ ứng dụng. Nhưng chỉ nhìn thấy dòng có chữ `GC` hoặc số mili-giây chưa đủ để kết luận “ứng dụng chậm do GC” hay “bị memory leak”.

Khi đọc **một sự kiện GC**, hãy trả lời tối thiểu năm câu hỏi:

1. **Sự kiện nào?** Young collection, mixed collection, full collection, concurrent phase, safepoint hay một sự kiện khác?
2. **Vì sao được kích hoạt?** Trong log có nhãn cause/trigger nào, ví dụ `G1 Evacuation Pause`, `Allocation Failure`, `Metadata GC Threshold`, `System.gc()`, `G1 Humongous Allocation`?
3. **Bộ nhớ heap trước và sau?** Trong mẫu thường gặp, `A->B(C)` mô tả used trước, used sau và heap capacity tại sự kiện ấy.
4. **Thời gian nào?** Dòng có `Pause` bao nhiêu mili-giây, phase concurrent bao lâu, và thời gian các thread ứng dụng bị dừng có thực sự khớp với độ trễ người dùng quan sát không?
5. **Xu hướng toàn bộ log ra sao?** Sau nhiều lần GC, occupancy đáy có tăng liên tục, khoảng cách các GC có rút ngắn, có promotions/evacuation failures hay Full GC không?

**Chủ đề của bài:** quan sát và lỗi JVM trên **HotSpot/OpenJDK Java 17**, đặc biệt đọc log của **G1** vì G1 thường là collector mặc định trên cấu hình server-class HotSpot 17 phù hợp. Các collector khác như Serial, Parallel, ZGC, Shenandoah có log, cơ chế và ý nghĩa pause khác. **Không áp một format G1 duy nhất cho mọi JVM hoặc mọi collector.**

Các log mẫu bên dưới được ghi rõ là **mô phỏng có chủ đích**, dùng để luyện đọc từng trường; chúng không được giới thiệu là kết quả đã chạy trên máy của bạn. Thời điểm, số liệu, tag và hình thức các dòng thực tế có thể thay đổi theo vendor/build, các tùy chọn `-Xlog` và thuật toán GC.

## 2. Bật Unified JVM Logging ở Java 17

Từ Java 9, HotSpot sử dụng **Unified Logging** với option `-Xlog`. Java 17 nên dùng cú pháp này thay vì copy nguyên bộ flags `PrintGCDetails`/`PrintGCTimeStamps` đời Java 8.

Bắt đầu đơn giản:

```bash
java -Xlog:gc -jar app.jar
```

Tìm thêm các dòng chi tiết có tag GC:

```bash
java -Xlog:gc* -jar app.jar
```

Lưu vào tệp, có timestamp, uptime, level, tags và rotation:

```bash
java '-Xlog:gc*:file=gc-%p-%t.log:time,uptime,level,tags:filecount=5,filesize=20M' -jar app.jar
```

Trên một số shell Windows/công cụ launch khác, cú pháp quoting cần điều chỉnh; dấu `*` trong Bash được đặt trong **nháy đơn** để tránh shell filename expansion. `%p` là process ID, `%t` là startup timestamp trong file name theo HotSpot logging syntax.

**Các cấp độ log:**

- `-Xlog:gc`: có dòng tóm tắt chính cho nhiều GC events.
- `-Xlog:gc*`: chọn các tag sets có chứa `gc`, thường cho cả breakdown như `gc,heap`, `gc,phases`, `gc,cpu` ở mức `info`.
- `-Xlog:gc*=debug`: có thể tạo nhiều output hơn và overhead cao hơn; dùng có mục đích.
- `-Xlog:gc+heap=debug`: tập trung một nhóm heap-related messages; một số nội dung chi tiết còn ở `trace` tùy build.
- `-Xlog:gc+age=trace`: xem age/tenuring-related logs nếu collector và build có tag/event phù hợp.
- `-Xlog:gc+ergo*=debug`: khám phá các quyết định GC ergonomics khi cần, chấp nhận log nhiều.

Kiểm tra capabilities thực tế:

```bash
java -Xlog:help
java -version
```

**Phân biệt dấu `+` với `*`:** `gc+heap` chọn một tag set chứa cả hai tag theo selector; `gc*` match các tag sets có chứa `gc` ở mức lựa chọn tương ứng. `-Xlog` có hệ thống decorators, levels, outputs và tag combinations; không nên đọc nó như tên file đơn giản.

### Lưu ý an toàn vận hành

- GC logging có overhead CPU/I/O/disk; file rotation giúp hạn chế không gian lưu.
- Log level `debug`/`trace` có thể phát sinh khối lượng lớn; không bật rộng vô hạn trên production.
- Xác nhận **file thực sự được tạo và chứa GC events**; nếu workload chưa trigger GC thì không có dòng `Pause Young` không phải lỗi.
- Khi cấu hình ghi file, kiểm tra permissions, đường dẫn và cơ chế container log shipping.
- Nếu dùng `-Xlog:async`, log có buffer giới hạn; khi quá tải có thể **mất log messages**; đừng dựa vào log async như nhật ký đầy đủ tuyệt đối.
- Không mở rộng logging có nhãn sensitive trong môi trường cần bảo vệ dữ liệu nếu không có quyền.

## 3. Anatomy của một dòng GC log

Ví dụ **mô phỏng, theo kiểu HotSpot 17 G1**:

```text
[2026-10-22T09:30:00.120+0700][2.345s][info][gc] GC(17) Pause Young (Normal) (G1 Evacuation Pause) 160M->52M(256M) 8.250ms
```

Tách từng phần:

| Đoạn | Ý nghĩa |
| --- | --- |
| `2026-10-22T09:30:00.120+0700` | Wall-clock timestamp do decorator `time` cung cấp; minh họa |
| `2.345s` | JVM uptime ở thời điểm ghi log; không phải thời lượng GC |
| `info` | Mức log |
| `gc` | Tag set của message |
| `GC(17)` | GC ID, giúp gom các dòng liên quan cùng sự kiện |
| `Pause Young` | Sự kiện có pha dừng ứng dụng để thu gom young generation |
| `(Normal)` | Nhãn loại young pause trong ví dụ G1 |
| `(G1 Evacuation Pause)` | Tên event/cause kiểu G1, không tự là kết luận lỗi |
| `160M->52M(256M)` | Heap used trước → sau; heap capacity của sự kiện |
| `8.250ms` | Elapsed duration cho GC pause/event summary được ghi |

**Quan trọng:** Timestamp `2.345s` là **uptime của JVM** tại lúc message được ghi (thường gần lúc kết thúc pause), còn `8.250ms` là **duration**. Không đọc `2.345s` thành “GC kéo dài 2,345 giây”.

Thông thường `M` trong GC logging đại diện MiB (2²⁰ bytes), `K` thường gần KiB (2¹⁰ bytes) trong hệ thống format HotSpot, nhưng đừng làm tròn hoặc trộn đơn vị một cách mù quáng khi so với RSS, JMX bytes hay dữ liệu do vendor khác xuất.

### Dòng log thực có thể thiếu/trùng trường

Cấu hình `-Xlog:gc` thông thường có decorators mặc định **uptime, level, tags**, không bắt buộc có date-time. Ngược lại `-Xlog:gc*:file=...:time,uptime,level,tags` sẽ hiện cả time/uptime. Collector khác và các thay đổi build có thể in tên cause và capacity theo dạng khác.

Không viết parser dựa trên một vị trí cột cố định mà chưa chốt collector, JVM version và danh sách decorators.

## 4. Cách đọc `before->after(capacity)`

Cấu trúc thường gặp:

```text
160M->52M(256M)
  |    |    |
  |    |    +-- dung lượng Java heap được dùng làm capacity cho sự kiện
  |    +------- heap used ngay sau sự kiện
  +------------ heap used ngay trước sự kiện
```

Trong ví dụ:

- Heap used **trước**: 160 MiB.
- Heap used **sau**: 52 MiB.
- **Capacity**: 256 MiB.
- Chênh lệch used: 160 − 52 = **108 MiB**.
- Tỷ lệ giảm used từ trước: 108 / 160 = **67,5%**.
- Tỷ lệ occupancy sau pause so với capacity: 52 / 256 ≈ **20,3%**.

**Cực kỳ quan trọng:** `(256M)` không nhất thiết là giới hạn `-Xmx` của process. Heap có thể mở rộng/co lại, do đó capacity/committed tại các GC events có thể đổi. `-Xmx` là heap max, còn `before/after` thường là heap occupancy ở hai thời điểm.

### “Giảm 108 MiB” có nghĩa JVM đã trả 108 MiB RAM cho OS?

**Không.** Đây là biến động **used heap**, không phải con số RAM được hoàn trả. Những vùng heap vẫn có thể **committed**, có thể vẫn resident trong RSS, và collector có thể đưa free regions về danh sách có thể tái sử dụng thay vì uncommit pages.

Cũng không nhất thiết “GC đã thu hồi chính xác 108 MiB objects chết” nếu các events/metadata, allocation/promotion trong lúc concurrent phases, region accounting hoặc cách biểu diễn collector khác nhau. Nó là **net decrease của reported heap occupancy** cho thời điểm đó.

### Dung lượng heap có thể thay đổi sau Full GC

Ví dụ mô phỏng:

```text
GC(20) Pause Full (G1 Compaction Pause) 300M->120M(512M) 160.000ms
GC(26) Pause Full (G1 Compaction Pause) 280M->104M(384M) 140.000ms
```

Nhìn hai dòng riêng không đủ nói collector đã shrink heap **trong chính sự kiện thứ hai** hay khi nào capacity đổi, vì các GC khác có thể nằm giữa `GC(20)` và `GC(26)`. Để xác định chuỗi resize, xem **toàn timeline** cùng `gc+heap` và heap/ergonomics metrics liên quan.

## 5. GC ID và cách gom một event nhiều dòng

Với `-Xlog:gc*`, một GC có thể sinh nhiều messages. Ví dụ **mô phỏng**:

```text
[2.120s][info][gc,start] GC(12) Pause Young (Normal) (G1 Evacuation Pause)
[2.120s][info][gc,task ] GC(12) Using 4 workers of 4 for evacuation
[2.129s][info][gc,phases] GC(12) Pre Evacuate Collection Set: 0.4ms
[2.129s][info][gc,phases] GC(12) Evacuate Collection Set: 5.7ms
[2.129s][info][gc,phases] GC(12) Post Evacuate Collection Set: 2.0ms
[2.129s][info][gc,phases] GC(12) Other: 0.6ms
[2.129s][info][gc,heap] GC(12) Eden regions: 80->0(73)
[2.129s][info][gc,heap] GC(12) Survivor regions: 4->8(12)
[2.129s][info][gc,heap] GC(12) Old regions: 32->34
[2.129s][info][gc ] GC(12) Pause Young (Normal) (G1 Evacuation Pause) 132M->58M(256M) 8.700ms
[2.129s][info][gc,cpu] GC(12) User=0.03s Sys=0.00s Real=0.01s
```

Để đọc:

1. Gom các messages có **`GC(12)`** với nhau.
2. `gc,start` giúp hiểu lúc pause bắt đầu; `gc` summary báo heap change và elapsed pause.
3. `gc,phases` chia nhỏ thời gian của **cùng sự kiện** để tìm phase đắt.
4. `gc,heap` cho thấy các **G1 regions** thay đổi; region counts không phải MiB nếu chưa biết region size.
5. `gc,cpu` báo user/system/real CPU-time statistics; không dùng `User=...` làm thời gian STW.

Ở log thực, tổng rounded phase durations có thể khác summary một ít do rounding, overlap, phase definitions hoặc những chi tiết không được ghi. Đừng đòi cộng chính xác mọi cột ở mức 0,001ms.

### Vì sao GC ID hữu ích khi logs bị trộn?

GC threads và các JVM subsystems có thể ghi xen kẽ những messages. Cùng một event có nhiều tags và timestamps. **GC ID** là khóa tương quan tốt hơn chỉ dùng khoảng thời gian kế cận, đặc biệt khi log có concurrent phases hoặc debug messages.

GC IDs thường tăng trong một JVM process; **đừng ghép `GC(7)` giữa hai process** như một event nếu file logs/replicas được gom vào hệ thống chung. Dùng PID, startup timestamp hoặc instance identity cùng GC ID.

## 6. Tên pause, loại collector và mục đích của GC

| Dòng/từ khóa hay gặp | Cách hiểu đúng ở mức khái quát | Cần xem tiếp |
| --- | --- | --- |
| `Pause Young` | Thu gom vùng young, thường STW | Frequency, duration, Eden/Survivor/Old |
| `Pause Young (Mixed)` / `Pause Young (Mixed) ...` | Với G1, một pause có thể thu gom young cùng một số old regions | Old-region reclamation và after-GC floor |
| `Pause Full` | STW full-heap collection/compaction theo collector phù hợp | Trigger, failure, capacity, pause, trend |
| `Concurrent Mark Cycle` | Có công việc marking chạy concurrent với application | Các STW subphases và tổng CPU pressure |
| `Pause Remark` | Một STW phase trong concurrent marking | Remark duration/tail latency |
| `Pause Cleanup` | Phase cleanup (collector/version specific) | Có stop hay concurrent, duration |
| `Allocation Failure` | Collector không cấp phát theo cơ chế hiện tại mà phải thu gom | Có phải young/old pressure, promotion |
| `G1 Evacuation Pause` | G1 pause di chuyển/thu gom selected regions | CSet, evacuation work, post-GC used |
| `G1 Humongous Allocation` | Allocation liên quan humongous object kích hoạt GC/cycle thích hợp | Humongous regions, fragmentation, live set |
| `Metadata GC Threshold` | Metadata pressure/Metaspace threshold trigger | Class loading, unloading, metaspace |
| `System.gc()` | Yêu cầu explicit GC từ application/library/tool | Call sites, flags, full vs concurrent behavior |

Tên hiển thị và dấu ngoặc thay đổi theo JVM/collector. **Nhãn cause là trigger gần của một event**, không tự chứng minh **nguyên nhân gốc**. Ví dụ `G1 Evacuation Pause` có thể là hoạt động bảo trì bình thường; nhiều young GCs không tự là lỗi.

### “Normal”, “Concurrent Start”, “Mixed” khác nhau thế nào?

Trong G1, young pause phục vụ young collections; khi bắt đầu concurrent marking có thể có young pause mang nhãn `Concurrent Start`, còn `Mixed` cho thấy collection set gồm young và **các old regions được chọn** sau marking. Không phải mọi old region đều được thu ở một mixed GC và không phải mixed GC là Full GC.

G1 đặt **pause-time goal** chứ không hứa mọi pause luôn ngắn hơn ngưỡng. Ví dụ `-XX:MaxGCPauseMillis=200` là **soft goal/hint cho G1 ergonomics**, không phải timeout tuyệt đối của GC hay SLA hard real-time.

## 7. Pause time khác tổng thời gian GC concurrent

Phải phân biệt:

- **Stop-the-world pause:** các Java application threads bị dừng tại safepoint phù hợp; trực tiếp đóng góp vào latency không phục vụ công việc Java thông thường trong khoảng dừng.
- **Concurrent marking/cleanup:** GC làm việc song song với application; tiêu CPU, băng thông bộ nhớ và đôi khi tác động throughput, nhưng **không đồng nghĩa application bị dừng suốt toàn khoảng ấy**.
- **GC elapsed duration:** trong log của một event phải đọc đúng label; mỗi line có thể báo thời gian của từng phase khác nhau.
- **User/Sys/Real CPU:** user CPU cộng từ các worker threads có thể lớn hơn real wall time khi GC dùng nhiều lõi.
- **Safepoint timing:** ngoài collection work còn có pha đưa threads tới safepoint; nếu quan sát latency spike phải xem logs `safepoint` và application tracing tương ứng.

Ví dụ mô phỏng:

```text
[4.100s][info][gc] GC(20) Concurrent Mark Cycle
[4.101s][info][gc] GC(20) Concurrent Mark From Roots 42.0ms
[4.144s][info][gc] GC(20) Concurrent Mark Cycle 44.0ms
```

Các từ/giờ ở đây chỉ làm **mô hình** phân biệt pha concurrent; **không** thể lấy 44ms gán là “ứng dụng STW 44ms”. Muốn xác định thời gian dừng của một concurrent cycle, tìm các sub-events thực mang `Pause` và bản ghi safepoint, không cộng toàn bộ elapsed concurrent wall time như STW.

### `gc,cpu` vì sao User lớn hơn Real?

Ví dụ:

```text
GC(12) User=0.12s Sys=0.01s Real=0.03s
```

Nghĩa khái quát: GC workers có thể tiêu tổng **CPU user time 0,12 giây** trong **real elapsed khoảng 0,03 giây** nhờ chạy song song trên nhiều lõi. Không phải một “GC pause kéo dài 120ms” chỉ vì `User=0.12s`.

Trong môi trường container bị **CPU throttling**, `Real` có thể kéo dài và GC throughput giảm; cần đọc thêm CPU quota/throttling metrics chứ không chỉ heap used.

## 8. Young GC: vì sao diễn ra thường xuyên?

Young GC trong generational collector thường xảy ra khi vùng cấp phát cho đối tượng mới (Eden/young) cần được thu gom để tiếp tục allocations, nhưng trigger và policy phụ thuộc collector.

Ví dụ G1:

```text
GC(31) Pause Young (Normal) (G1 Evacuation Pause) 210M->68M(384M) 7.5ms
GC(32) Pause Young (Normal) (G1 Evacuation Pause) 216M->74M(384M) 8.1ms
GC(33) Pause Young (Normal) (G1 Evacuation Pause) 221M->78M(384M) 7.8ms
```

Các số **giả định** này gợi ý:

- Sau mỗi young GC, used còn **68→74→78 MiB**, tăng dần.
- Có thể do long-lived objects/live set tăng, promotions, caches đang warm hoặc allocation/collector region accounting; **chưa đủ** kết luận memory leak.
- Trước GC là **210→216→221 MiB**; khoảng cách về thời gian **chưa có** trong ba dòng nên không biết GC frequency.
- Pause duration ~7–8ms trong mô hình, nhưng cần tính p95/p99/trends trên **nhiều** events.

**Young GC nhiều không nhất thiết xấu.** Nếu application có allocation rate cao, thu gom short-lived objects nhiều lần là điều bình thường; hãy xem GC CPU overhead, pause tails và request latency dưới workload mục tiêu.

### Eden, Survivor, Old theo regions

Với `-Xlog:gc*` G1 thường có dạng:

```text
GC(31) Eden regions: 140->0(132)
GC(31) Survivor regions: 8->12(18)
GC(31) Old regions: 25->28
GC(31) Humongous regions: 1->1
```

Các số là **region counts**, không phải MiB trực tiếp. G1 chia heap thành regions có kích cỡ được chọn bởi ergonomics hoặc `-XX:G1HeapRegionSize`. Để quy đổi cần biết **region size** và hiểu các loại occupancy. **Eden regions sau young pause về 0** không có nghĩa toàn heap used sau GC về 0, vì survivor/old/humongous vẫn giữ dữ liệu sống.

Một số young survivors có thể được copy/promote sang old nếu đáp ứng tenuring/space policy. Vì vậy old-region count tăng **không đủ chứng minh leak**, nhất là lúc caches/classes được làm nóng.

## 9. Mixed GC trong G1: old không đợi đến Full GC mới được xử lý

Một G1 concurrent mark cycle xác định old regions có lượng live data/thời gian thu gom phù hợp; sau đó có thể có **mixed collections** gồm young cùng các old regions được chọn.

```text
GC(40) Pause Young (Mixed) (G1 Evacuation Pause) 450M->330M(640M) 24.0ms
GC(43) Pause Young (Mixed) (G1 Evacuation Pause) 426M->290M(640M) 27.0ms
```

Ví dụ giả định:

- GC thứ 40 hạ occupancy xuống 330MiB.
- GC thứ 43 hạ occupancy xuống 290MiB.
- Không thể lấy khoảng `GC(40)` đến `GC(43)` suy có đúng 3 mixed GCs; cần xem các GC IDs/loại events xen giữa.
- Mixed pause có thể tốn hơn young-only, vì collection set chứa cả old regions.

Nếu mixed cycle hoàn tất mà **after-GC occupancy gần heap capacity**, có thể là live set cao, promotion pressure hoặc fragmentation; cần xem detailed logs, allocation rate và hệ thống thực trước khi tăng heap/tuning.

## 10. Full GC: mức độ nghiêm trọng phụ thuộc nguyên nhân và tần suất

Một Full GC thường là tín hiệu phải kiểm tra vì có thể gây **STW pause dài**, nhưng nó không tự là kết luận thảm họa.

Ví dụ mô phỏng:

```text
GC(67) Pause Full (G1 Compaction Pause) 980M->890M(1024M) 1250.0ms
```

Diễn giải:

- Heap used trước gần capacity: **980/1024 ≈ 95,7%**.
- Sau Full GC vẫn **890/1024 ≈ 86,9%**.
- Net decrease chỉ **90 MiB**.
- Duration **1250ms = 1,25s**, có thể góp đáng kể vào tail latency khi xảy ra trong request-serving interval.

Những câu hỏi kế tiếp:

- Full GC do `System.gc()`, `Allocation Failure`, `G1 Humongous Allocation`, evacuation failure/to-space exhaustion, `Metadata GC Threshold` hay sự kiện khác?
- Có đủ memory để các live objects, promotions và allocation bursts không?
- Nhiều Full GCs liên tiếp mà after-GC occupancy ít giảm có phải do live set lớn, object retention hoặc leak?
- Cgroup memory limit có nhỏ hơn footprint thực khi gồm native memory?
- Có thể dùng heap dump/JFR allocation/retention profiling xác định retaining references không?

**Đừng kết luận leak chỉ từ một dòng.** Một cache hợp lệ, batch xử lý dữ liệu lớn hay workload vừa tăng có thể khiến live set lớn. Cần **xu hướng after-GC occupancy đã xét cùng workload** và heap/object retention analysis.

### Full GC không nhất thiết luôn xảy ra khi `System.gc()`

`System.gc()` là request, và HotSpot có flags như `-XX:+DisableExplicitGC` hoặc `-XX:+ExplicitGCInvokesConcurrent` làm behavior khác. Dấu `(System.gc())` nếu xuất hiện là manh mối truy nguồn, **không** phải chứng minh JVM luôn đã compact toàn heap.

## 11. Humongous objects và evacuation failure

Trong G1, object có kích thước **từ một nửa G1 region trở lên** thường được xem là **humongous** và sử dụng contiguous old regions; logic cấp phát/thu hồi của chúng khác object nhỏ.

Tín hiệu cần tìm:

- `(G1 Humongous Allocation)`: nhãn liên quan việc cấp phát object humongous kích hoạt chu kỳ/pause phù hợp.
- `Humongous regions: A->B`: số regions gắn với humongous objects thay đổi.
- `To-space exhausted` hoặc thông điệp tương ứng: quá trình evacuation thiếu chỗ trống phù hợp.
- `Evacuation Failure`: collector không copy được một số objects từ collection set như dự kiến.
- `Pause Full`: có thể là fallback sau khi G1 không tiến triển đủ hoặc bởi một trigger khác.

**Không đồng nhất humongous allocation với memory leak.** Một array lớn còn sống hợp lệ có thể chiếm nhiều regions; fragmentation và cách dùng contiguous regions mới là manh mối cần đánh giá thêm.

```text
GC(80) Pause Young (Concurrent Start) (G1 Humongous Allocation) ...
GC(81) Pause Young (Normal) (G1 Evacuation Pause) ...
GC(82) To-space exhausted
GC(82) Pause Young (Normal) (G1 Evacuation Pause) ...
GC(83) Pause Full (G1 Compaction Pause) ...
```

Đây là **pattern diễn giải có chủ đích**, không phải trace thực nghiệm đầy đủ hay lời hứa thứ tự IDs cố định. Nếu quan sát dấu failure:

1. Xác minh heap max/capacity và region size.
2. Xem humongous regions tăng, old occupancy và free regions.
3. Xem tốc độ phân bổ large arrays/buffers.
4. Xác minh workload peak và container/cgroup memory budget.
5. Chỉ điều chỉnh `-XX:G1HeapRegionSize` hoặc flags GC sau khi có số liệu và cân nhắc tác dụng phụ.

## 12. `Allocation Failure` không phải lúc nào là `OutOfMemoryError`

Trong log của một số collectors (ví dụ Serial/Parallel), cause `Allocation Failure` thường chỉ tình huống JVM cần GC vì allocation không thể tiếp tục với vùng/budget hiện có theo current policy.

Đây **không tự là Java exception** `java.lang.OutOfMemoryError`. Một young GC được trigger bởi allocation pressure có thể thu hồi đủ bộ nhớ và chương trình tiếp tục bình thường.

Ngược lại, nếu các collections không giải phóng đủ và không thể cấp phát tiếp, ứng dụng có thể gặp OOME hoặc process bị cgroup OOM kill. Phân biệt:

- **GC cause:** lý do bắt đầu một lần collection.
- **GC result:** occupancy/regions sau collection, duration.
- **Application exception:** OOME có stack trace/hs_err/log ứng dụng hoặc không.
- **OS kill:** cgroup `memory.events` tăng `oom_kill` và process kết thúc đột ngột, có thể không có Java heap OOME.
- **Native allocation failure:** process RSS/cgroup có thể vượt giới hạn dù Java heap chưa đầy.

Nên dùng GC logs **cùng** error logs, container logs và heap/native diagnostics khi điều tra.

## 13. Metaspace trong GC logs không phải Java heap used

Với `-Xlog:gc*`, HotSpot có thể ghi:

```text
GC(35) Metaspace: 18240K->18240K(1064960K)
```

Đây là **log mẫu**. Metaspace phục vụ class metadata ở vùng native/VM-managed memory, không thuộc Java object heap used. Các dấu ngoặc/capacity trong metaspace reporting phải được đọc theo **ngữ nghĩa collector và build**, không tự gán chúng bằng `-XX:MaxMetaspaceSize` hay process RSS.

Tình huống cần chú ý:

- Dynamic class loading, proxies, generated classes hoặc nhiều class loaders.
- Class-loader retention khiến classes không unload được.
- `Metadata GC Threshold` hoặc full/concurrent cycles liên quan metaspace pressure.
- Việc tăng threshold không sửa object/classloader leak logic.

Để điều tra chi tiết hơn:

```bash
jcmd <PID> VM.classloader_stats
jcmd <PID> GC.class_histogram
jcmd <PID> VM.native_memory summary
```

**Lưu ý:** `GC.class_histogram` có thể yêu cầu safepoint/đáng kể overhead, `VM.native_memory` cần NMT bật từ startup và một số commands khác nhau theo build. `<PID>` là placeholder, thay bằng Java process ID thực.

## 14. Log `gc,cpu` và thời gian pause: đọc đủ ba đại lượng

Một dòng thường gặp:

```text
GC(17) User=0.10s Sys=0.01s Real=0.02s
```

| Trường | Ý nghĩa khái quát |
| --- | --- |
| `User` | CPU time tiêu trong user mode do GC-related worker activity |
| `Sys` | CPU time trong kernel/system mode |
| `Real` | Thời gian thực trôi qua cho khoảng đo tương ứng |
| Summary cuối dòng `... 20.0ms` | Duration của event/pause do collector ghi |

Do parallelism, `User+Sys` **có thể lớn hơn** `Real`. Nếu `Real` cao bất thường so với CPU usage, xem CPU quota/throttling, thread scheduling hoặc stalls. Không mặc định mọi chênh lệch nghĩa “GC bug”.

Số `Real=0.02s` thường được làm tròn đến hai chữ số thập phân giây, nên không đối chiếu như thể chính xác đến microsecond với dòng `20.312ms`. Đọc mức precision của từng field.

## 15. Safepoint log: tìm thời gian ứng dụng dừng ngoài GC

Một request p99 tăng vọt không nhất thiết được giải thích chỉ bằng `Pause Young`. JVM có thể dừng threads cho những safepoint operations khác, và còn có **thời gian đến safepoint**.

Khi cần:

```bash
java '-Xlog:gc*,safepoint=info:file=vm-events.log:time,uptime,level,tags' -jar app.jar
```

Sau đó đối chiếu:

- Timestamps của request latency/traces với GC/safepoint events.
- Thời gian **reaching safepoint** và **at safepoint** trong dòng safepoint theo build.
- Các STW phases được đánh dấu trong collector logs.
- Các JVM operations khác, như class redefinition/JVMTI activity, cleanup hoặc deoptimization, nếu có.
- Các khoảng latency do I/O, locks, network, disk, CPU throttling mà GC logs không đủ bao quát.

**Không đồng nhất pause line của GC với tổng wall-clock latency một request.** Một request có thể chờ socket, thread pool, lock hoặc database trong thời gian GC diễn ra; cần correlating traces/JFR/system metrics.

## 16. Thực hành Java 17: tạo áp lực heap có giới hạn để sinh log

Tạo file **`GcLoggingDemo.java`**:

```java
public final class GcLoggingDemo {
    private static volatile byte[] retained;

    public static void main(String[] args) {
        int attempts = 1_024;
        int arrayLength = 128 * 1024;

        for (int i = 0; i < attempts; i++) {
            byte[] buffer = new byte[arrayLength];
            buffer[0] = (byte) i;

            // Volatile publication giữ reference observable:
            // không dựa vào vòng lặp hoàn toàn vô tác dụng.
            retained = buffer;
        }

        System.out.println("attempts=" + attempts);
        System.out.println("arrayLength=" + retained.length);
        System.out.println("completed=true");
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 GcLoggingDemo.java
java -Xms32m -Xmx64m -Xlog:gc GcLoggingDemo
```

**Ba dòng stdout do `System.out` tạo ra khi chạy thành công:**

```text
attempts=1024
arrayLength=131072
completed=true
```

Vì có `-Xlog:gc` trên default stdout, xen giữa có thể có các **dòng GC diagnostic bổ sung** không xác định. Nếu muốn tách riêng log:

```bash
java -Xms32m -Xmx64m \
  '-Xlog:gc*:file=gc-demo.log:time,uptime,level,tags' \
  GcLoggingDemo
```

Sau chạy, `gc-demo.log` có thể chứa một số GC events tùy JVM/collector và OS; **không cam kết số lần GC hoặc loại pause**. Vòng lặp yêu cầu khoảng **128 MiB payload allocations** theo nguồn lệnh Java (1024 × 128 KiB); chỉ giữ reference của array mới nhất, nên heap live set nhỏ và một HotSpot heap 64 MiB thường thực hiện GC để tái sử dụng không gian.

**Đây là demo tạo pressure được kiểm soát, không phải benchmark GC.** Nếu tăng `attempts`/array size không kiểm soát, JVM có thể OOME hoặc làm môi trường chậm. Không chạy vòng lặp vô hạn hay cố làm cạn RAM host.

### Vì sao thêm volatile reference?

Nếu code chỉ tạo array và không dùng nó, optimizer có thể dead-code eliminate một phần allocations; khi đó bạn tưởng tạo GC pressure nhưng thực tế compiled code đã xóa nhiều công việc. `retained = buffer` là một side effect có semantics, giúp ví dụ không phụ thuộc vào hoàn toàn dead allocations. Không đồng nghĩa mọi allocation phải xảy ra cùng số bytes thực, vì JIT vẫn được tối ưu các phần hợp lệ khác.

## 17. Đọc log tạo được: trình tự năm phút đầu tiên

Giả sử đã có file `gc-demo.log`:

1. Đọc **header/runtime startup** để biết JVM uptime, heap sizing, collector, region size.
2. Lọc các dòng `GC(...) Pause` và nhận diện collector/type.
3. Chọn một GC ID rồi gom mọi `gc,start`, `gc,heap`, `gc,phases`, `gc,cpu` liên quan.
4. Với summary, đọc **cause → before → after → capacity → duration**.
5. Vẽ theo timeline các values **after-GC used**, **capacity**, **pause duration**.
6. Xem frequency: khoảng giữa timestamps của các successive pauses và tương quan allocation rate.
7. Nếu Full GC/failed evacuation xuất hiện, mở chi tiết theo nguyên nhân thay vì tune vô tội vạ.
8. So request latency/safepoint/container metrics cùng timestamps.
9. Kiểm tra những GC events có bị thiếu do rotation hoặc async logging không.
10. Nếu chưa đủ thông tin, tăng đúng nhóm log tags ở lượt diagnostic riêng; không mở toàn bộ `trace` ngay.

Không có thể dùng một câu “GC đã thu hồi nhiều nên tốt”: nếu mỗi vài mili-giây lại pause, throughput có thể xấu; nếu thu hồi ít nhưng chỉ diễn ra hiếm khi và latency SLO vẫn đạt, không nhất thiết phải tune.

## 18. Java 17: parser đơn giản để học đọc `A->B(C)` và duration

Đoạn sau cố tình **chỉ hỗ trợ một dòng log mô phỏng có đơn vị `M`**, không phải parser production cho mọi collectors/decorators. Tạo file **`GcLineReader.java`**:

```java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GcLineReader {
    public static void main(String[] args) {
        String line = "[2.345s][info][gc] GC(17) "
                + "Pause Young (Normal) (G1 Evacuation Pause) "
                + "160M->52M(256M) 8.250ms";

        Pattern pattern = Pattern.compile(
                "GC\\((\\d+)\\).*?(\\d+)M->(\\d+)M\\((\\d+)M\\) "
                + "(\\d+(?:\\.\\d+)?)ms");
        Matcher match = pattern.matcher(line);

        if (!match.find()) {
            throw new IllegalArgumentException("No matching GC summary");
        }

        int id = Integer.parseInt(match.group(1));
        int before = Integer.parseInt(match.group(2));
        int after = Integer.parseInt(match.group(3));
        int capacity = Integer.parseInt(match.group(4));
        String elapsed = match.group(5);

        System.out.println("gcId=" + id);
        System.out.println("beforeMiB=" + before);
        System.out.println("afterMiB=" + after);
        System.out.println("netDecreaseMiB=" + (before - after));
        System.out.println("capacityMiB=" + capacity);
        System.out.println("pauseMs=" + elapsed);
    }
}
```

```bash
javac --release 17 GcLineReader.java
java GcLineReader
```

**Stdout xác định:**

```text
gcId=17
beforeMiB=160
afterMiB=52
netDecreaseMiB=108
capacityMiB=256
pauseMs=8.250
```

### Vì sao regex demo không dùng được nguyên xi trong production?

- HotSpot có thể ghi `K`, `M`, `G`, `B` hoặc decimals/spacing khác.
- Có lines không chứa **heap occupancy** hoặc **pause duration**.
- Một event có nhiều phase lines và CPU lines cần link bằng GC ID.
- Tên collector/format có thể khác; concurrent events không có cùng dạng `A->B(C)`.
- Tệp bị cắt do log rotation hoặc thiếu records; parser phải xử lý incomplete events.
- Timestamps có decorators khác nhau, có thể cần parse uptime/time chính xác.
- Dòng `Pause` đơn giản không bao trùm toàn bộ safepoint/latency.
- Trên nhiều JVM processes phải ghép PID/startup identity, tránh trùng GC ID.

Nếu cần phân tích hàng GB logs, dùng GC log analyzer hiểu collector/JDK build, hoặc viết parser có **unit normalization, event model, test fixtures và version awareness**. Đừng dùng regex trên làm bộ đếm tỷ lệ memory leak.

## 19. Phân biệt capacity với `-Xmx` và heap committed

Nếu `-Xms128m -Xmx1024m`, heap có thể được JVM committed từ ~128 MiB và tăng khi cần lên tới khoảng `-Xmx` theo collector/ergonomics và cấu hình thực.

Vì vậy:

```text
GC(7) Pause Young (...) 110M->40M(128M) 5.0ms
GC(20) Pause Young (...) 480M->160M(640M) 18.0ms

- Xmx có thể vẫn là 1024 MiB.
- Capacity reported tăng từ 128 tới 640 MiB.
- Heap used sau GC tăng từ 40 tới 160 MiB.
```

Những số trên là **minh họa**, không phải trace từ chương trình thật. `-Xmx` không hiện nhất thiết trong summary parentheses; kiểm tra với `jcmd VM.flags` và `GC.heap_info` trên process đang chạy.

Một số collectors có reserved/committed bookkeeping phức tạp; chỉ số `capacity` trong summary thông dụng giúp phân tích heap ở event ấy, không phải tuyên bố mỗi byte capacity đều resident vật lý.

### Heap used khác process RSS

- `used`: occupancy của Java heap.
- `committed/capacity`: JVM quản lý heap memory đã cam kết/có thể dùng.
- `RSS`: resident physical pages của process theo OS.
- `PSS`: phân bổ tỷ lệ shared resident pages cho process trên Linux.
- `VSZ/reserved`: phạm vi địa chỉ ảo, không tương đương resident RAM.

Một ứng dụng có `after GC=200MiB` vẫn có thể dùng RSS trên 500MiB vì **thread stacks, metaspace, code cache, direct buffers, native allocations và mapped libraries**. GC log không thay được `ps`, `/proc/PID/smaps_rollup` hoặc cgroup metrics.

## 20. Những đồ thị nên xây dựng từ GC logs

Một dashboard GC hữu ích thường gồm:

| Trục thời gian | Metric | Tín hiệu cần tìm |
| --- | --- | --- |
| Uptime/wall-clock | `before-GC heap used` | Allocation bursts, mức áp sát capacity |
| Uptime/wall-clock | `after-GC heap used` | Live-set/old occupancy floor và trend |
| Uptime/wall-clock | Heap capacity | Tăng/co heap, GC ergonomics |
| Uptime/wall-clock | Pause duration theo loại | Young/mixed/full pause distributions |
| Windowed | GC events/second | Frequency, burst patterns |
| Windowed | STW time / elapsed window | GC pause fraction, ứng dụng bị dừng bao nhiêu |
| Windowed | Allocation bytes/s (nếu có nguồn đo riêng) | Tốc độ allocations, pressure |
| Uptime/wall-clock | Old/humongous regions | Retained data/fragmentation indicators |
| Uptime/wall-clock | CPU utilization / cgroup throttling | CPU-driven long pauses |
| Uptime/wall-clock | Request latency/traces | Có tương quan p95/p99 với GC không |

**Không cộng durations của cả concurrent và pause phases** để tính total STW. Tính pause fraction từ các **STW pauses** tương ứng và chỉ số window phù hợp; khi phases lồng nhau phải tránh double-counting.

### Hai xu hướng giả định để luyện đọc

```text
Trường hợp A (ổn định):
  after-GC used: 120, 118, 125, 121, 119 MiB
  young pauses: 8, 9, 8, 10, 8 ms
  => Có thể là live-set ổn định dưới cùng workload.

Trường hợp B (cần điều tra):
  after-GC used: 120, 165, 210, 265, 310 MiB
  full pauses:   120, 200, 450 ms (không cùng 5 events)
  => Có thể do retention/caches/workload tăng; chưa khẳng định leak.
```

Các số là **mô hình**, không phải dữ liệu quan trắc. Để so hợp lý phải biết **workload, collector, heap max, elapsed time**, vì một ứng dụng tăng load sẽ giữ nhiều in-flight objects hơn mà không có bug.

## 21. Cách đánh giá pause fraction và throughput

Giả sử trong **cửa sổ 60 giây** có 200 STW pauses, mỗi pause được ghi duration 5ms (ví dụ giả định). Tổng STW thời gian **không chồng lấn** ≈ 200 × 5ms = 1000ms = 1s.

```text
STW pause fraction = 1 s / 60 s * 100 = 1.67%
application available-time fraction (chỉ xét STW GC) ~= 98.33%
```

**Không kết luận ứng dụng có throughput 98,33% của khả năng tối đa**: còn GC concurrent CPU, Java allocation barriers, lock contention, I/O, scheduler và overhead khác.

Trong thực tế:

- Chọn cửa sổ phù hợp với SLO (1 phút/5 phút/giờ) và tải thực.
- Không cộng cùng một pause summary và các phase durations của chính nó.
- Với collectors **concurrent**, nhiều GC activities không dừng toàn ứng dụng; phải phân loại đúng.
- GC pause fraction nhỏ vẫn có thể gây **p99 latency spike** nếu một pause riêng rất dài.
- GC pause fraction cao nhưng pauses ngắn đều có thể tác động throughput rõ rệt.
- Cần phối hợp **wall-clock events** với tracing/JFR và resource metrics để giải thích impact.

## 22. Dấu hiệu retention hoặc memory leak từ GC logs

Một chuỗi **after-GC used** tăng có thể là một manh mối leak khi:

1. **Workload** và số request in-flight tương đối ổn định.
2. Caches đã qua giai đoạn warm-up và không còn tăng hợp lệ.
3. Có nhiều **major/full/mixed GC events** đủ để phản ánh live-set, không chỉ vài young collections.
4. After-GC occupancy tăng kéo dài theo thời gian, tiến gần heap max.
5. Full/mixed GC dần thu hồi ít, pause tăng, OOME/cgroup pressure có thể xuất hiện.
6. Heap dump/retained-size analysis chỉ ra đường giữ reference không mong muốn.

Ngược lại, dấu hiệu có thể **không phải leak**:

- Cache được phép tăng tới max rồi ổn định.
- Dữ liệu batch phải giữ trong memory đến cuối job.
- Request concurrency/in-flight payload tăng.
- Collector thay đổi young/old sizing và promotion behavior.
- Chỉ đo young GC before/after thay vì post-full/mixed floor.
- Process có nhiều direct buffers/native memory mà heap used vẫn ổn định.

**Phân tích heap dump khi cần**, nhưng nhớ heap dump live/snapshot có thể đòi stop/pause và tiêu disk/RAM; xử lý production theo quy trình, bảo vệ dữ liệu nhạy cảm. Dump có thể chứa secrets/PII của ứng dụng.

## 23. Kết hợp với `jstat` và `jcmd`

Nếu JVM còn chạy, quan sát:

```bash
jcmd <PID> VM.flags
jcmd <PID> GC.heap_info
jstat -gcutil <PID> 1000 10
jstat -gccause <PID> 1000 10
```

`<PID>` là số PID thực của Java process, không copy dấu `<...>` vào shell. Trên Windows, `jstat`/`jcmd` availability/permissions và option sets tùy JDK installation.

- `VM.flags`: JVM flags hữu ích để biết heap/GC settings đã bật.
- `GC.heap_info`: snapshot heap state theo collector.
- `jstat -gcutil`: tỷ lệ sử dụng các memory spaces/pools và thông tin số lần/thời gian GC theo khả năng thống kê HotSpot.
- `jstat -gccause`: cause gần nhất/current cause theo counters nếu có.
- `jcmd GC.class_histogram`: đối tượng theo class, có thể làm gián đoạn hoặc tốn tài nguyên, không phải bước đầu tiên nhẹ.
- `jcmd GC.heap_dump`: tạo dump, thường rất tốn dung lượng/time; không chạy tùy tiện trên production.

**Không trộn loại dữ liệu:** `jstat -gcutil` thường ghi **percent** của các pools, `gc logs` có MiB và ms, `JMX MemoryMXBean` trả **bytes**, `/proc` thường KiB. Một biểu đồ không normalize units sẽ đánh lừa.

### JFR giúp gì?

**Java Flight Recorder** có thể ghi GC pause events, allocations, CPU samples và thread-related events theo settings. Dùng timeline JFR/JMC để đối chiếu những GC events với **latency windows**. Nhưng JFR event configuration/sampling và log lines không có quan hệ một-một bắt buộc ở mọi build.

## 24. Kích hoạt, thay đổi và xác nhận GC logging lúc runtime

Để khám phá commands hỗ trợ:

```bash
jcmd <PID> help
jcmd <PID> VM.log help
```

Một HotSpot Java 17 có thể cho phép đổi Unified Logging configuration qua `VM.log`. Ví dụ **chỉ mang tính định hướng**: sau khi đọc help của runtime, chọn selectors/output phù hợp thay vì tự đoán cú pháp option trên mọi vendor.

Nếu muốn luôn ghi log từ startup, **ưu tiên cấu hình `-Xlog` trong command line** vì logging bật sau có thể bỏ lỡ GC events đầu tiên. Dynamic logging hữu ích khi cần điều tra một khoảng ngắn mà không restart, nhưng nhớ:

- Startup events đã qua không xuất hiện trở lại.
- Đổi debug/trace làm overhead runtime tăng.
- Log rotation và permissions cần kiểm tra.
- Đổi cùng lúc nhiều settings làm khó so baseline.

Lưu cả `java -version`, thời điểm start, PID, collector và option flags khi thu log. Không nên chỉ nhận một tệp `gc.log` rồi đoán đó là output của JVM nào.

## 25. Một quy trình điều tra p99 latency khi nghi GC

**Bước 1 — Xác nhận triệu chứng:** request p95/p99, throughput, CPU và thời điểm spike. Đừng bắt đầu bằng “cứ tăng Xmx”.

**Bước 2 — Xác nhận JVM:** `java -version`, collector, `-Xms/-Xmx`, container memory limit/cpu quota, ứng dụng build/commit.

**Bước 3 — Thu log đủ thông tin:** `-Xlog:gc*,safepoint=info` với time/uptime decorators, rotation và instance identity.

**Bước 4 — Xem timeline:** đánh dấu GC ID, loại pause, pause duration, after-GC heap used, heap capacity.

**Bước 5 — So timestamp:** spike latency có thực sự overlap với STW pause hoặc safepoint? Nếu không, xem network/DB/locks/CPU.

**Bước 6 — Phân nhóm:** Young, mixed, full, concurrent. Không cộng concurrent wall time vào STW.

**Bước 7 — Đọc cause:** là allocation pressure bình thường, humongous allocation, metadata threshold, explicit GC hay failure?

**Bước 8 — Phân tích pressure:** after-GC floor, promotion/old regions, allocation rate, humongous regions, free capacity.

**Bước 9 — Kiểm tra OS/container:** RSS/PSS/cgroup usage, `memory.events`, CPU throttling; native OOM khác Java heap OOME.

**Bước 10 — Chạy diagnostics riêng:** JFR allocations/heap histogram/dump khi được phép, không làm gián đoạn production một cách mù quáng.

**Bước 11 — Thay một yếu tố:** heap sizing, giảm allocation/retention, cache policy, collector/tuning theo giả thuyết được kiểm chứng.

**Bước 12 — Đo lại:** giữ workload, JVM version và CPU/memory limits tương đương; so pause p95/p99, GC fraction, throughput, heap occupancy và application latency.

**Bước 13 — Kiểm tra regression:** không chỉ GC tốt hơn; CPU tổng, RSS, OOM risk và tính đúng chương trình cũng phải không xấu đi.

### GC log có thể không nói hết

Một pause 100ms trùng p99 spike **là manh mối mạnh**, nhưng request đã chờ database 2s có thể có GC 100ms nằm giữa. Chỉ log GC không xác định chắc root cause của toàn latency; cần tracing/sampling stack và hệ thống phân tán khi có.

## 26. Các kiểu sự cố và chiến lược xử lý đúng

| Triệu chứng | Giả thuyết cần xem | Dữ liệu xác minh | Hướng xử lý thường gặp |
| --- | --- | --- | --- |
| Young GC quá thường xuyên, pauses ngắn | Allocation rate cao, Eden nhỏ | Events/s, Eden regions, allocation sampling | Giảm allocations nóng, xem heap/young sizing |
| Young pauses lâu | Copy/promotion/root scanning lớn | `gc,phases`, survivor/old, CPU quota | Giảm live data/roots, CPU pressure, tune khi có số |
| Mixed cycles kéo dài | Old live set/marking pressure | Old regions, after-GC floor, marking timeline | Giảm retention, cân nhắc heap/GC tuning |
| Full GC dài và lặp | Heap gần đầy, allocation/evacuation failures | Full causes, after occupancy, failure logs | Xác định retention/leak, memory budget, region pressure |
| Humongous regions tăng | Arrays/large payloads và contiguous-region fragmentation | `gc,heap`, allocation profiler | Giảm/stream large objects, tune region size có kiểm chứng |
| `Metadata GC Threshold` lặp | Class loading/classloader retention | Metaspace logs, classloader stats | Xử lý classloader retention/cache/proxies |
| Heap ổn định nhưng container OOM | Native/direct/stacks/metaspace/cgroup accounting | RSS, NMT, cgroup `memory.events` | Native profiling, adjust total footprint/budget |
| `System.gc()` quá thường xuyên | Explicit GC calls từ library/tool | Cause, callsite tracing, flags | Tìm caller, đánh giá chính sách explicit GC |
| Pause real cao, user CPU thấp | Throttling/scheduling/OS pressure | `gc,cpu`, CPU quota, host metrics | Giảm host contention/tăng CPU quota hợp lý |
| GC ổn nhưng p99 cao | I/O, locks, DB, thread pool queue | JFR, thread dumps, tracing | Điều tra ngoài GC trước |

**Không thay GC flags cùng lúc hàng loạt** chỉ vì thấy nhiều young collections. Với GC ergonomics, thay vùng young/region/threads có thể ảnh hưởng memory và latency theo hướng ngược mong muốn.

## 27. Một ví dụ đọc log theo từng bước

Giả sử có tóm tắt sau, **hoàn toàn mô phỏng**:

```text
[1.000s][info][gc] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 240M->90M(512M) 12.000ms
[2.000s][info][gc] GC(2) Pause Young (Normal) (G1 Evacuation Pause) 300M->110M(512M) 14.000ms
[3.000s][info][gc] GC(3) Pause Young (Concurrent Start) (G1 Humongous Allocation) 390M->170M(512M) 25.000ms
[4.500s][info][gc] GC(4) Pause Young (Mixed) (G1 Evacuation Pause) 420M->210M(512M) 30.000ms
[5.300s][info][gc] GC(5) Pause Full (G1 Compaction Pause) 490M->455M(512M) 980.000ms
```

**Bước A — Phân nhóm cause/type.** 1 và 2 là young normal, 3 là concurrent-start với humongous trigger, 4 là mixed, 5 là full. Mỗi dòng ở đây có cùng format summary giả định.

**Bước B — Xem occupancy.** After-GC: 90, 110, 170, 210, 455 MiB. Tăng liên tục nhưng **5 events chưa đủ** kết luận leak, vì có thay đổi chu kỳ GC, promotions và workload chưa biết.

**Bước C — Xem capacity.** 512 MiB theo từng dòng; có thể heap max lớn hơn hoặc bằng 512MiB, cần runtime flags để xác nhận.

**Bước D — Xem duration.** Pauses 12, 14, 25, 30, 980 ms; Full GC 980ms rõ ràng là tail event cần điều tra.

**Bước E — Đánh giá hiệu quả Full GC.** 490→455 MiB: net decrease 35 MiB (≈7,1% trước). Heap used sau vẫn 455/512 ≈88,9%. Nếu Full GC liên tiếp cho kết quả tương tự, ứng dụng có thể đối mặt allocation failure/OOME.

**Bước F — Câu hỏi tiếp:** Humongous regions? To-space exhausted? Classloader/native pressure? User requests liên quan lớn hay không? Có logs GC chi tiết và heap dump được phép không?

**Bước G — Chỉ kết luận có căn cứ.** “Có một Full GC 980ms và occupancy cao sau GC, cần điều tra high live set/pressure” là kết luận vững; “GC do leak ở class X” là **quá mức** nếu chưa có object retention evidence.

## 28. Những hiểu lầm cần tránh

| Nhận định sai | Cách đọc đúng |
| --- | --- |
| “`160M->52M(256M)` là before/after RAM process” | Đây thường là **Java heap used**, không phải RSS |
| “`(256M)` chắc là `-Xmx`” | Đây là heap capacity tại event; max có thể khác |
| “Used giảm 108M nghĩa OS trả 108M RAM” | Net heap occupancy change khác RSS/uncommit |
| “`[2.345s]` nghĩa GC mất 2.345 giây” | Đây là JVM uptime decorator |
| “Một dòng `gc,cpu User=0.12s` là STW 120ms” | User CPU có thể chạy song song; đọc pause `Real`/summary |
| “Concurrent Mark Cycle 100ms nghĩa STW 100ms” | Concurrent wall time không đồng nghĩa pause |
| “Young GC nhiều tức JVM lỗi” | Short-lived allocations có thể trigger young GC bình thường |
| “`Allocation Failure` = OutOfMemoryError” | Thường chỉ trigger GC; OOME là tình huống khác |
| “`G1 Evacuation Pause` là evacuation failure” | Đây là tên hoạt động thu gom G1 bình thường |
| “Old regions tăng = leak” | Promotions/cache/warm-up cũng làm tăng |
| “Một Full GC nghĩa leak” | Cần cause, nhiều events và retained-object evidence |
| “After-Full vẫn cao chắc class loader leak” | Có thể live set hợp lệ, caches, traffic peak |
| “Mixed GC giống Full GC” | Mixed thu young và **old regions được chọn**, không phải toàn heap |
| “Humongous allocation chắc là memory leak” | Có thể là payload lớn hợp lệ, nhưng fragmentation cần xem |
| “Metaspace nằm trong heap `-Xmx`” | Metaspace là native memory category riêng |
| “NMT committed phải bằng RSS” | Accounting/covers khác nhau |
| “Log không có GC nghĩa JVM không dùng bộ nhớ” | Workload có thể chưa cần GC hoặc logging chưa bật |
| “Mọi collector log cùng format” | Collector/build khác về causes, tags và phases |
| “Đã xem `gc*` là biết toàn bộ app latency” | Cần safepoint, tracing, CPU, locks, network |
| “Một pause 10ms luôn tốt với mọi SLA” | p99 SLO và workload quyết định |
| “GC CPU User > Real nghĩa dữ liệu sai” | Parallel collector workers làm tổng CPU > wall |
| “Mọi thay đổi GC flags đều tăng performance” | Tuning phải kiểm soát trade-offs và đo lại |
| “Heap max lớn thì container không thể OOM” | Native overhead+cgroup limit vẫn có thể kill |
| “GC ID của hai replicas là một event” | Phải dùng PID/process startup identity |
| “Một regex đọc một sample là parser sản xuất hoàn chỉnh” | Cần version awareness, tag/units/rotation support |

## 29. Tự kiểm tra kiến thức

**Câu 1:** Trong `GC(17) Pause Young ... 160M->52M(256M) 8.250ms`, ba số bộ nhớ nghĩa gì?

**Đáp:** Heap used trước=160MiB, used sau=52MiB và heap capacity tại event=256MiB; pause duration 8,250ms.

**Câu 2:** Giảm 108MiB used có chứng minh RSS giảm 108MiB không?

**Không.** Heap used khác committed/resident; collector có thể giữ pages để tái sử dụng.

**Câu 3:** GC ID dùng làm gì?

Gom lines `gc,start`, `gc,heap`, `gc,phases`, `gc,cpu` của một event trong **cùng JVM process**.

**Câu 4:** Vì sao `[8.456s]` trong prefix không phải pause duration?

Nó là uptime decorator; duration thường là field cuối phù hợp event.

**Câu 5:** `Pause Young` khác `Concurrent Mark Cycle` thế nào?

Young pause có pha STW; concurrent cycle có nhiều công việc chạy song song với ứng dụng và có thể gồm các pause nhỏ riêng.

**Câu 6:** `Real=0.02s` và `User=0.10s` có mâu thuẫn?

Không; nhiều GC workers có thể tiêu tổng 100ms CPU trong ~20ms wall time trên nhiều lõi.

**Câu 7:** `Allocation Failure` có phải Java heap OOME?

Không nhất thiết; JVM có thể thu gom và tiếp tục cấp phát thành công.

**Câu 8:** Một G1 mixed GC có thu hết toàn bộ old generation không?

Không; nó chọn một số old regions cùng young collection set.

**Câu 9:** Nếu after-GC used tăng 5 lần liên tiếp có chắc leak?

Chưa. Cần so workload, warm-up, old/full occupancy trends và heap/object retention.

**Câu 10:** Tại sao cần `-Xlog:gc*` thay vì chỉ `-Xlog:gc` trong chẩn đoán?

`gc*` thường cho các phase/heap/CPU breakdowns để giải thích nguyên nhân và duration, đổi lại log volume cao hơn.

**Câu 11:** Tại sao dùng thêm `-Xlog:safepoint`?

Để phân tích time-to-safepoint/STW-related activity ngoài GC summary khi ứng dụng có latency spikes.

**Câu 12:** `Humongous regions` tăng là dấu hiệu gì?

Có nhiều large-object regions đang được sử dụng; cần xem allocation/lifetime/fragmentation, không tự kết luận leak.

**Câu 13:** Có thể giải thích OOMKilled khi log heap chỉ báo used thấp không?

Có, vì native memory/direct buffers/stacks/cgroup accounting có thể vượt limit dù heap chưa đầy.

**Câu 14:** Vì sao không so hai GC logs nếu một file có debug logging, file còn lại chỉ info mà không ghi settings?

Thông tin/overhead không tương đương; có thể bỏ sót events/fields hoặc làm timing khác, dẫn tới kết luận sai.

**Câu 15:** Khi Full GC 980ms trùng request latency 2s, có thể kết luận GC gây toàn bộ 2s không?

Không; GC có thể đóng góp khoảng 980ms nhưng phần còn lại có thể do I/O/locks/CPU; cần traces và safepoint correlation.

## 30. Tài liệu chuẩn để đối chiếu Java 17

- [Oracle Java SE 17 — `java` launcher, Unified Logging](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): cú pháp `-Xlog`, tag selectors, decorators, file rotation, chuyển flags GC cũ sang logging mới.
- [Oracle Java SE 17 — G1 Garbage Collector Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): young/mixed/concurrent behavior, pause-time goals và cách tìm causes liên quan G1.
- [Oracle Java SE 17 — GC Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/index.html): mục tiêu throughput/latency/footprint và khác biệt collector.
- [Oracle Java SE 17 — `jstat`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html): runtime statistics và GC cause/space usage.
- [Oracle Java SE 17 — `jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `GC.heap_info`, `VM.flags`, `VM.log` và các commands khác.
- [Oracle Java SE 17 — `MemoryMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html): đọc used/committed/max của heap và non-heap theo JMX.
- [Oracle Java SE 17 — `GarbageCollectorMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/GarbageCollectorMXBean.html): collection count/time ở cấp MXBean, không thay thế detailed log.
- [Oracle Java SE 17 — `jdk.jfr` module](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html): JFR events và runtime monitoring.
- [OpenJDK JEP 158 — Unified JVM Logging](https://openjdk.org/jeps/158): logging framework, selectors/tags/outputs/decorators.
- [OpenJDK JEP 271 — Unified GC Logging](https://openjdk.org/jeps/271): GC events trong Unified Logging.
- [OpenJDK JEP 307 — Parallel Full GC for G1](https://openjdk.org/jeps/307): bối cảnh G1 Full GC/compaction.
- [Linux proc `smaps` và cgroup documentation](https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html): đọc pressure ngoài Java heap.

**Kết luận:** Để đọc đúng GC log, luôn bắt đầu từ **loại collection và cause**, sau đó đọc **`before->after(capacity)`** như **Java heap used trước/sau và capacity**, không nhầm với `-Xmx` hay RSS; tiếp theo phân biệt **`Pause ... ms`** với **concurrent wall time** và **GC CPU User/Sys/Real**. Cuối cùng phải xem **xu hướng qua nhiều GC IDs**, G1 regions/Full GC/humongous/failure hints, và tương quan với **request latency, safepoints, cgroup/OS metrics**. Một GC event có thể bình thường; **nguyên nhân gốc của sự cố chỉ được kết luận khi có bằng chứng hệ thống nhất quán**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-004">jcmd và java.lang.management để xem phiên bản, cờ và mức dùng tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# `jcmd` và `java.lang.management` để xem phiên bản, cờ và mức dùng tài nguyên JVM

Khi một ứng dụng Java chạy chậm, khởi động bất thường, chiếm nhiều bộ nhớ hoặc hoạt động khác với cấu hình dự kiến, bước đầu tiên thường không phải đổi cờ GC/JIT. Hãy **xác minh chính JVM đang chạy là JVM nào, nó nhận những tham số gì và các loại tài nguyên đang ở trạng thái nào**. Trong JDK có hai đường quan sát bổ trợ nhau:

- **`jcmd`**: công cụ dòng lệnh gửi **diagnostic commands tới một JVM đang chạy**, không nhất thiết phải sửa source code ứng dụng. Trên **HotSpot**, có những lệnh như `VM.version`, `VM.command_line`, `VM.flags`, `GC.heap_info`, `Thread.print`, `Compiler.codecache` và `VM.native_memory`.
- **`java.lang.management`**: các **MXBeans** trong module `java.management` để chính chương trình (hoặc công cụ JMX được cấp quyền) đọc thông tin runtime, bộ nhớ, threads, class loading, GC, compilation, OS và buffer pools. Dữ liệu lấy được phụ thuộc VM implementation và thời điểm đọc.

Chúng **không phải hai công cụ có tính năng giống hệt**. `jcmd` có các diagnostic commands rất cụ thể của HotSpot; Java Management API tạo một giao diện lập trình chuẩn cho nhiều thông tin runtime. Để phân tích sự cố chính xác, kết hợp **phiên bản + cờ hiệu lực + metric đúng phạm vi + mốc thời gian + workload + giới hạn môi trường**.

Bài dùng **Java 17+**; các lệnh và cờ đặc thù HotSpot được ghi rõ. Không tự áp `jcmd` commands của HotSpot cho OpenJ9/Native Image nếu không có tài liệu tương ứng.

## 1. Bốn câu hỏi quan trọng nhất khi bắt đầu điều tra

Trước khi tối ưu hoặc chẩn đoán một sự cố JVM:

1. **Phiên bản nào đang thực thi?** Java major/update, vendor, VM implementation, build và architecture.
2. **JVM nhận cờ gì?** Command line, flags hiệu lực, ergonomic defaults, môi trường/container được JVM nhận diện.
3. **Tài nguyên nào đang dùng?** Heap used/committed/max, non-heap MXBean, GC events/count/time, thread count, loaded class count, direct buffers, code cache, native memory khi có tracking.
4. **Giới hạn và nguồn chứng cứ?** CPU quota, cgroup memory limit, process RSS, permissions, timestamp, monitoring interval và overhead của diagnostic.

Một số câu dễ sai:

- “Đã khai `-Xmx4g` trong deployment YAML nên process chắc chắn có heap 4 GiB.” **Chưa đủ:** cần xác minh process command line/effective flags.
- “`MemoryMXBean` báo non-heap 150 MiB, vậy toàn bộ native memory chỉ 150 MiB.” **Sai:** non-heap management view không bao phủ mọi native allocations.
- “`jcmd -l` không thấy PID, vậy không có JVM.” **Sai:** container PID namespace, permissions hoặc attach restrictions có thể làm JVM không được liệt kê.
- “`VM.flags` in các options, vậy mọi option đều do người dùng chủ động đặt.” **Sai:** có defaults và ergonomically derived values.
- “`getSystemLoadAverage()` âm nghĩa CPU đang rảnh.” **Sai:** giá trị âm có thể là **không hỗ trợ/không có dữ liệu**.

## 2. Sơ đồ nguồn dữ liệu

```text
                           JVM PROCESS
                    ┌───────────────────────┐
                    │ Class loader / JIT     │
                    │ Java heap / GC         │
                    │ Native / code cache    │
                    │ Threads / OS interface │
                    └─────────┬─────────────┘
                              │
             ┌────────────────┴─────────────────────┐
             │                                      │
    jcmd attach/diagnostics                java.lang.management
             │                                      │
    VM.version / VM.flags             RuntimeMXBean / MemoryMXBean
    VM.command_line                   ThreadMXBean / GC MXBeans
    GC.heap_info                      ClassLoadingMXBean
    Compiler.codecache                OperatingSystemMXBean
    Thread.print                      CompilationMXBean / Buffer pools
    VM.native_memory                  MemoryPoolMXBeans
             │                                      │
             └─────────────┬────────────────────────┘
                           ▼
                Quan sát có timestamp
                           │
                           ▼
                 So sánh với OS/cgroup
                  và workload thực tế
```

**Không có “một số tổng hợp duy nhất” để suy ra toàn bộ hoạt động của JVM**. Một số metrics phản ánh tình trạng **trong VM**, một số phản ánh **cả process/container**; phạm vi đo khác nhau phải được nêu rõ.

## 3. Chuẩn bị môi trường JDK 17+

Để dùng `jcmd`, hãy dùng **JDK** có công cụ phù hợp thay vì môi trường chỉ chứa một JRE tối giản không có công cụ chẩn đoán. Trên máy có nhiều JDK, `java` và `jcmd` trong `PATH` có thể thuộc **hai installations khác nhau**.

Kiểm tra:

```bash
java -version
javac -version
jcmd -h
```

Các lệnh này hữu ích để xác nhận công cụ hiện có. `java -version` trả về implementation/version của **launcher được gọi**, **không chứng minh** process đích được khởi động bằng chính executable ấy.

Ví dụ trên Linux:

```bash
which java
which javac
which jcmd
```

Ví dụ trên PowerShell:

```powershell
Get-Command java
Get-Command javac
Get-Command jcmd
```

Nếu có nhiều JVM containers, thực hiện `jcmd` trong **đúng host/container/PID namespace** với quyền truy cập thích hợp. `jcmd` thường cần cùng **effective user/group** với JVM đích và quyền attach; một số môi trường có thể hạn chế attach hoàn toàn.

### Cách đọc một mã phiên bản

`java.lang.Runtime.version()` trả một `Runtime.Version` cho Java runtime đang chạy; `RuntimeMXBean.getVmName/getVmVendor/getVmVersion` cho identity của **VM implementation**. Java specification version và VM implementation version là hai trường khác nhau.

Ví dụ cần ghi:

```text
java.version       : 17.0.x (từ process thật)
java.vm.name       : tên VM thực tế
java.vm.vendor     : nhà cung cấp thực tế
java.vm.version    : build/version thực tế
os.name / os.arch  : OS/architecture
runtime PID        : định danh tiến trình
```

Các giá trị ở đây chỉ là **nhãn cần thu thập**, không phải stdout cố định. Ghi số thật từ chính runtime trong báo cáo.

## 4. `jcmd` là gì và giới hạn hoạt động

Cú pháp nền:

```bash
jcmd -l
jcmd PID help
jcmd PID help VM.flags
jcmd PID VM.version
```

Thay `PID` bằng một process ID JVM **đang chạy và có quyền truy cập**. `jcmd -l` liệt kê các JVM processes tìm thấy ở môi trường/namespace hiện tại. Lệnh `help` cho biết **commands mà JVM đích hỗ trợ**, không nên tự đoán từ tài liệu release khác.

Đọc kết quả có giới hạn:

- `jcmd -l` có thể không liệt kê JVM trong **Docker PID namespaces** tách biệt.
- Kết nối attach có thể bị chặn nếu runtime bật `-XX:+DisableAttachMechanism` hoặc có policy/security restrictions.
- JVM đã chết không thể trả lời diagnostic command.
- Một số lệnh rất nhẹ, một số có thể dừng JVM đáng kể hoặc tạo dump lớn.
- Command availability, syntax và impact thay đổi theo VM vendor/version.
- `jcmd PID VM.command_line` hoặc `VM.system_properties` có thể chứa **sensitive paths, system properties, arguments hoặc tokens**. Không đẩy raw logs lên kênh công khai.

**Không sử dụng `jcmd 0 COMMAND` trong một môi trường đông JVM mà chưa xét phạm vi:** nó có thể gửi lệnh tới **tất cả JVMs mà công cụ nhìn thấy**, gây thu thập hoặc tác động ngoài dự định.

## 5. Bộ lệnh đọc-only ban đầu với `jcmd`

Với một HotSpot process đang chạy:

```bash
jcmd PID VM.version
jcmd PID VM.command_line
jcmd PID VM.flags
jcmd PID VM.uptime
jcmd PID GC.heap_info
jcmd PID Compiler.codecache
```

Đây là các lệnh thường dùng cho **triage**; tuy nhiên mỗi command có mức **impact** riêng. Ví dụ `GC.heap_info` được tài liệu JDK 17 đánh dấu impact **Medium**, không mặc định là một phép đọc không có chi phí.

| Lệnh | Trả lời câu hỏi | Giới hạn |
| --- | --- | --- |
| `VM.version` | VM implementation/version nào đang chạy? | Không cho app git revision |
| `VM.command_line` | VM được khởi động với command/options gì? | Có thể lộ thông tin nhạy cảm |
| `VM.flags` | Các VM flags hiệu lực/được in theo mode nào? | Output không đơn giản là danh sách user-explicit flags |
| `VM.uptime` | JVM đã chạy bao lâu? | Không phản ánh tổng thời gian service từng hoạt động trước restart |
| `GC.heap_info` | Trạng thái heap/collector snapshot ra sao? | Snapshot và layout collector-specific |
| `Compiler.codecache` | JIT code cache đang ở đâu/mức nào? | Không đo toàn bộ native memory |
| `Thread.print` | Các threads/stacks/locks đang ở trạng thái nào? | Có thể tạo output lớn và ảnh hưởng runtime |
| `VM.native_memory` | Các nhóm native memory do HotSpot track? | Chỉ hữu ích khi NMT đã bật từ startup |

### In toàn bộ flags của HotSpot

```bash
jcmd PID VM.flags -all
```

Output có thể **rất dài**; lưu vào file bảo mật phù hợp khi cần, nhưng không paste nguyên output nếu có data nhạy cảm. `VM.flags` không phải một giao diện quản lý mọi user options giống hệt trên tất cả Java distributions.

### `VM.command_line` khác `VM.flags`

- **Command line**: mô tả cách VM được khởi chạy, gồm options/launcher context.
- **Flags**: các VM flag settings hiện hành, có cả defaults và ergonomically determined values tùy mode.
- **`JDK_JAVA_OPTIONS` / `JAVA_TOOL_OPTIONS` và launcher wrappers** có thể thay effective setup.
- Một số flags được **manageable** và có thể đổi sau startup; so thời điểm ghi nhận.

**Không kết luận** vì `-Xmx` không xuất hiện trong list of explicit JVM arguments thì max heap không được xác định: JVM có ergonomic defaults.

## 6. Runtime phiên bản và flags qua `java.lang.management`

Java API chuẩn có `RuntimeMXBean`:

```java
java.lang.management.RuntimeMXBean runtime =
        java.lang.management.ManagementFactory.getRuntimeMXBean();
```

Đây là snippet hợp lệ trong method, không phải chương trình độc lập.

Các phương thức quan trọng:

| API | Điều trả về |
| --- | --- |
| `getVmName()` | Tên VM implementation |
| `getVmVendor()` | Vendor của VM |
| `getVmVersion()` | Version/build của VM implementation |
| `getSpecVersion()` | Phiên bản specification của JVM |
| `getPid()` | PID của JVM process (có từ Java 10) |
| `getStartTime()` | Mốc thời gian JVM start gần đúng, milliseconds |
| `getUptime()` | Uptime JVM tính milliseconds |
| `getInputArguments()` | JVM input arguments do implementation trả về |
| `getSystemProperties()` | Properties map (có thể chứa thông tin nhạy cảm) |
| `getClassPath()` | Java class path của application class loader trong phạm vi contract |

**Cảnh báo rất quan trọng:** `RuntimeMXBean.getInputArguments()` **không bảo đảm liệt kê tất cả launcher options** và không gồm arguments của `main(String[] args)`. VM có thể nhận arguments từ launcher, environment/configuration và runtime interfaces khác nhau.

Nếu muốn biết **effective max heap**, đo `MemoryMXBean.getHeapMemoryUsage().getMax()` và đối chiếu `VM.flags` thay vì chỉ dựa vào `getInputArguments()`.

### System properties không giống toàn bộ flags

`System.getProperty("java.version")`, `java.vm.name`, `os.arch` và `user.dir` là **system properties**. Các HotSpot `-XX` flags không phải tự xuất hiện thành một system property có cùng tên. Có thể đọc flagged settings bằng `jcmd VM.flags`, hoặc với HotSpot diagnostic MXBean như bài nói sau.

## 7. Heap và non-heap qua `MemoryMXBean`

API chuẩn:

```java
java.lang.management.MemoryMXBean memory =
        java.lang.management.ManagementFactory.getMemoryMXBean();

java.lang.management.MemoryUsage heap = memory.getHeapMemoryUsage();
java.lang.management.MemoryUsage nonHeap =
        memory.getNonHeapMemoryUsage();
```

`MemoryUsage` có bốn giá trị:

| Field | Diễn giải |
| --- | --- |
| `getInit()` | Initial size trong VM management view, có thể là -1 nếu undefined |
| `getUsed()` | Usage ở thời điểm đọc |
| `getCommitted()` | Dung lượng VM đã commit/đảm bảo dùng được |
| `getMax()` | Maximum theo VM management view, có thể -1 |

Đơn vị là **bytes**. Các giá trị có thể khác chính xác `-Xms/-Xmx` bởi alignment/heap regions. `getNonHeapMemoryUsage()` **không phải** một OS-native-memory profiler, không báo toàn bộ memory của JNI/native malloc/direct buffers, cũng không bằng RSS.

### Quan hệ thường gặp

```text
Java heap:     used ≤ committed ≤ max (nếu max được xác định)
Non-heap:      used/committed/max do VM management view báo
Process RSS:   Java heap + native + stacks + mappings + ...
Cgroup memory: OS-charged memory của cả cgroup
```

**Không trừ heap used khỏi cgroup memory để đặt tên phần còn lại là “native leak”**; committed-but-unused heap, file cache, page accounting, shared memory và sidecars có thể chen vào.

### Vì sao một snapshot không đủ?

GC có thể chạy giữa hai reads hoặc trong thời gian lấy dữ liệu. Heap used có thể tăng vì allocation churn rồi giảm sau GC, hoàn toàn không nhất thiết memory leak. Muốn đánh giá retained objects, xem post-GC live-set trend, allocation rate, heap dump/retention paths nếu cần và được phép.

## 8. `MemoryPoolMXBean`: nhìn theo từng heap/non-heap pool

`ManagementFactory.getMemoryPoolMXBeans()` trả các memory pools do JVM expose. Tên có thể khác theo GC/JDK. Một HotSpot G1 thường có các pools heap tương ứng young/survivor/old và non-heap như metaspace/code cache, nhưng **đừng hard-code pool names** cho tất cả collectors.

Các phương thức hữu ích:

```java
for (java.lang.management.MemoryPoolMXBean pool :
        java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
    java.lang.management.MemoryUsage usage = pool.getUsage();
    if (usage != null) {
        System.out.println(pool.getName() + ": " + usage.getUsed());
    }
}
```

Đây là snippet, không phải chương trình độc lập. Nó in số dynamic. Lưu ý:

- `getType()` cho biết `HEAP` hoặc `NON_HEAP`.
- `getUsage()` có thể trả `null` khi pool không valid.
- `getPeakUsage()` cho peak kể từ startup hoặc lần reset trước đó.
- `getCollectionUsage()` có thể `null` nếu pool không hỗ trợ hoặc không áp dụng.
- Threshold support tùy pool/collector (`isUsageThresholdSupported()`).
- Pool tên “CodeHeap ...” không bằng toàn bộ JIT/native code footprint ngoài management view.

**Không lấy các pool used values cộng vào heap used thêm lần nữa** nếu đã có heap total: đó là dữ liệu có thể chồng nhau.

## 9. GC metrics qua `GarbageCollectorMXBean`

`ManagementFactory.getGarbageCollectorMXBeans()` cung cấp các collector management beans.

```java
for (java.lang.management.GarbageCollectorMXBean gc :
        java.lang.management.ManagementFactory
                .getGarbageCollectorMXBeans()) {
    System.out.println(gc.getName()
            + " count=" + gc.getCollectionCount()
            + " timeMs=" + gc.getCollectionTime());
}
```

Đây là snippet trong method. `getCollectionCount()` và `getCollectionTime()` có thể trả **-1 nếu undefined**, và names/collection semantics tùy collector. Count/time là aggregate từ runtime management view, **không phải** từng pause event timestamp và không đủ tự suy p99 GC pause.

### Dùng để làm gì?

- So count delta qua một khoảng sampling.
- Phát hiện tăng đáng kể GC activity khi workload tương tự.
- Cùng GC logging/JFR đánh giá memory pressure.
- So ứng dụng đã warm-up, số lượng collections, thời gian collection.

### Chưa đủ cho điều gì?

- Không chứng minh memory leak chỉ từ count tăng.
- Không xác định mỗi request chậm bao nhiêu vì một pause cụ thể.
- Không thay đầy đủ GC unified log.
- Không đảm bảo tất cả collectors báo time với định nghĩa giống nhau.
- Không tự đo Native Memory Tracking.

Nếu cần events/timeline, dùng `-Xlog:gc*` và JFR trên JVM hỗ trợ, kết hợp timestamps của application/request tracing.

## 10. Thread metrics qua `ThreadMXBean`

`ThreadMXBean` theo dõi số threads, peak threads, total started threads và có các APIs đọc thread information/detect deadlocks tùy support.

```java
java.lang.management.ThreadMXBean threads =
        java.lang.management.ManagementFactory.getThreadMXBean();
int live = threads.getThreadCount();
int peak = threads.getPeakThreadCount();
long totalStarted = threads.getTotalStartedThreadCount();
```

Các chỉ số có nghĩa:

- **Current live thread count:** hiện có bao nhiêu live Java threads theo bean.
- **Peak thread count:** cực đại kể từ startup hoặc reset.
- **Total started count:** tổng số threads đã được tạo/khởi chạy theo bean kể từ JVM start.
- **Thread IDs/infos:** có thể dùng `getAllThreadIds()` / `getThreadInfo(...)` với context phù hợp.
- **Thread CPU time:** hỗ trợ và enablement có điều kiện; không mặc định luôn có số hợp lệ.

**Không kết luận “thread leak” từ một lần live count cao:** thread pool chủ động giữ nhiều threads vẫn có thể là healthy. Cần biết expected ownership/lifecycle và trend.

### `jcmd Thread.print` để xem stacks

```bash
jcmd PID Thread.print -l
```

Có thể thấy các states `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING` và monitor/lock information. Đây là **snapshot**; một thread `WAITING` có thể bình thường trong pool. Lệnh có thể in nhiều dữ liệu và impact thay theo số lượng threads.

### Không nhầm với hệ điều hành

`ThreadMXBean.getThreadCount()` là số live Java threads theo management API, **không** luôn bằng OS total threads/tasks trong mọi VM/platform vì có các VM/native threads khác. Linux `/proc/PID/status` (Threads) và cgroup `pids.current` có scope khác.

## 11. Class loading và compilation MXBeans

### ClassLoadingMXBean

```java
java.lang.management.ClassLoadingMXBean classes =
        java.lang.management.ManagementFactory.getClassLoadingMXBean();

int currentlyLoaded = classes.getLoadedClassCount();
long totalLoaded = classes.getTotalLoadedClassCount();
long unloaded = classes.getUnloadedClassCount();
```

Có thể dùng xu hướng class count/class-loader activity để **đặt giả thuyết** metaspace hoặc plugin/class-loader retention. Nhưng số loaded classes tăng không tự chứng minh leak: ứng dụng có thể vừa đi vào một code path mới và load classes lần đầu.

### CompilationMXBean

```java
java.lang.management.CompilationMXBean compiler =
        java.lang.management.ManagementFactory.getCompilationMXBean();

if (compiler != null && compiler.isCompilationTimeMonitoringSupported()) {
    long time = compiler.getTotalCompilationTime();
    System.out.println("compiler timeMs=" + time);
}
```

Compile time là **cumulative** theo bean, không tương đương chính xác wall-clock service slowdown. `getCompilationMXBean()` có thể trả `null` nếu implementation không có compilation system; `getTotalCompilationTime()` có thể không available theo VM support.

### Cùng lúc kiểm tra HotSpot compiler/code cache

```bash
jcmd PID Compiler.queue
jcmd PID Compiler.codecache
```

Các lệnh giúp xem compiler queue/code cache snapshots; không cung cấp phân tích tự động “call site A đã devirtualized” hay chứng minh ứng dụng CPU chậm vì JIT. Nếu cần compilation events, HotSpot có `-XX:+PrintCompilation`/JFR với trade-off về instrumentation.

## 12. OperatingSystemMXBean: biết giới hạn của CPU/load metrics

Giao diện Java chuẩn:

```java
java.lang.management.OperatingSystemMXBean os =
        java.lang.management.ManagementFactory.getOperatingSystemMXBean();

System.out.println(os.getName());
System.out.println(os.getArch());
System.out.println(os.getAvailableProcessors());
System.out.println(os.getSystemLoadAverage());
```

Đây là snippet có output phụ thuộc máy và thời điểm.

- `getAvailableProcessors()`: số processors VM coi là available; **có thể thay đổi trong thời gian JVM hoạt động**.
- `getSystemLoadAverage()`: average load trong khoảng thời gian gần nhất theo OS; có thể trả **âm nếu unavailable**.
- `getArch()`: architecture tương ứng JVM/system property.
- `getName()/getVersion()`: OS metadata.

**Không đọc system load average 4.0 thành “CPU đang dùng 400%”**. Load average có thể tính cả runnable queues, running entities và implementation-specific semantics; đối chiếu CPU quota/usage nếu đánh giá CPU pressure.

### Mở rộng HotSpot-specific

Trên các JDK có module `jdk.management`, `com.sun.management.OperatingSystemMXBean` có nhiều chỉ số bổ sung như process CPU time/memory-related OS data. Các methods có thể thay qua release và có những semantics container-specific. **Không coi `com.sun.management` là `java.lang.management` thuần chuẩn xuyên mọi Java runtime.**

Để xem OS/container constraints từ launcher HotSpot hỗ trợ:

```bash
java -XshowSettings:system -version
```

Kết quả là thông tin của launcher/process mới được chạy; khi cần biết **process đích**, lấy metrics từ process đích và cgroup/OS tương ứng.

## 13. Direct và mapped buffers qua `BufferPoolMXBean`

`ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)` trả các buffer pools mà VM expose.

```java
for (java.lang.management.BufferPoolMXBean pool :
        java.lang.management.ManagementFactory
                .getPlatformMXBeans(
                    java.lang.management.BufferPoolMXBean.class)) {
    System.out.println(pool.getName()
            + " count=" + pool.getCount()
            + " capacity=" + pool.getTotalCapacity()
            + " memoryUsed=" + pool.getMemoryUsed());
}
```

Đây là snippet, không có output cố định. Lưu ý:

- `getCount()` là estimate của số buffers.
- `getTotalCapacity()` đếm tổng capacity.
- `getMemoryUsed()` có thể khác capacity vì alignment, allocators và internal implementation.
- `getMemoryUsed()` có thể trả **-1** nếu không có estimate.
- Bean này **không đo mọi native/JNI malloc allocations**.

**Điểm phân tích hữu ích:** nếu Java heap không tăng nhưng direct buffer pool tăng cùng RSS, có cơ sở đặt giả thuyết direct-buffer retention. Vẫn phải kiểm tra lifespan/pooling, native allocations khác và OS memory accounting trước khi gọi đó là root cause.

## 14. HotSpotDiagnosticMXBean: đọc VM options trong Java

Ngoài các beans chuẩn, HotSpot cung cấp `com.sun.management.HotSpotDiagnosticMXBean` trong module `jdk.management`. Trên JVM hỗ trợ, nó cho phép đọc một option được hỗ trợ qua `getVMOption(String)`.

Ví dụ **Java 17+ và HotSpot có `jdk.management`**:

```java
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import java.lang.management.ManagementFactory;

public class HotSpotFlagsDemo {
    public static void main(String[] args) {
        HotSpotDiagnosticMXBean bean =
                ManagementFactory.getPlatformMXBean(
                    HotSpotDiagnosticMXBean.class);

        if (bean == null) {
            System.out.println("HotSpot diagnostics unavailable");
            return;
        }

        for (String name : new String[] {
                "InitialHeapSize", "MaxHeapSize", "UseG1GC"
        }) {
            try {
                VMOption option = bean.getVMOption(name);
                System.out.println(option.getName()
                        + "=" + option.getValue()
                        + ", origin=" + option.getOrigin()
                        + ", writable=" + option.isWriteable());
            } catch (IllegalArgumentException unsupported) {
                System.out.println(name + ": unavailable");
            }
        }
    }
}
```

Biên dịch/chạy trên HotSpot JDK 17 có module `jdk.management`:

```bash
javac -encoding UTF-8 HotSpotFlagsDemo.java
java HotSpotFlagsDemo
```

**Output không cố định**: giá trị cờ, origin, writable và compiler/runtime options tùy JVM, OS, flags và build. `getVMOption` chỉ hoạt động khi option được VM expose; exceptions phải được xử lý. Trên modular application dùng `module-info.java`, cần khai báo `requires jdk.management;` nếu dùng `com.sun.management`.

### Managed flag khác command-line-only flags

`getDiagnosticOptions()` chỉ liệt kê những **diagnostic options có thể ghi** theo contract của bean, **không nhất thiết là mọi JVM flag**. `getVMOption(name)` cho một tên flag mà VM hỗ trợ. `setVMOption` là thao tác **mutation** và có yêu cầu writable/security; bài này **chỉ đọc**, không thử thay flags trên JVM production.

Dù bean hỗ trợ mutable flags, không được coi tất cả `-XX` flags đều có thể đổi lúc runtime.

## 15. Java 17+ runnable: ảnh chụp MXBeans và bài thử số học xác định

Để tránh nhầm dữ liệu thay đổi theo thời điểm với kết quả kiểm thử, ví dụ sau tách:

- **Standard output cố định**: kiểm tra số phần tử và phép tính của chương trình.
- **Standard error động**: version, PID, uptime, args count, heap/non-heap, threads, classes, compiler/GC, buffer pools và OS metrics.

Tạo file độc lập **`JvmManagementSnapshot.java`**:

```java
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

public final class JvmManagementSnapshot {
    private static final int COUNT = 16;
    private static final int BLOCK_BYTES = 64 * 1024;

    private static void reportRuntime() {
        RuntimeMXBean vm = ManagementFactory.getRuntimeMXBean();
        System.err.println("vm name: " + vm.getVmName());
        System.err.println("vm vendor: " + vm.getVmVendor());
        System.err.println("vm version: " + vm.getVmVersion());
        System.err.println("runtime pid: " + vm.getPid());
        System.err.println("runtime uptime ms: " + vm.getUptime());
        System.err.println("jvm input argument count: "
                + vm.getInputArguments().size());

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        System.err.println("heap used bytes: " + heap.getUsed());
        System.err.println("heap committed bytes: "
                + heap.getCommitted());
        System.err.println("heap max bytes: " + heap.getMax());
        System.err.println("nonheap used bytes: "
                + nonHeap.getUsed());

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classes =
                ManagementFactory.getClassLoadingMXBean();
        OperatingSystemMXBean os =
                ManagementFactory.getOperatingSystemMXBean();

        System.err.println("threads live: " + threads.getThreadCount());
        System.err.println("threads peak: "
                + threads.getPeakThreadCount());
        System.err.println("classes loaded: "
                + classes.getLoadedClassCount());
        System.err.println("processors available: "
                + os.getAvailableProcessors());
        System.err.println("system load average: "
                + os.getSystemLoadAverage());

        CompilationMXBean compiler =
                ManagementFactory.getCompilationMXBean();
        if (compiler != null
                && compiler.isCompilationTimeMonitoringSupported()) {
            System.err.println("compiler time ms: "
                    + compiler.getTotalCompilationTime());
        } else {
            System.err.println("compiler time: unavailable");
        }

        for (GarbageCollectorMXBean gc :
                ManagementFactory.getGarbageCollectorMXBeans()) {
            System.err.println("gc: " + gc.getName()
                    + ", count=" + gc.getCollectionCount()
                    + ", timeMs=" + gc.getCollectionTime());
        }

        for (BufferPoolMXBean pool :
                ManagementFactory.getPlatformMXBeans(
                    BufferPoolMXBean.class)) {
            System.err.println("buffer pool: " + pool.getName()
                    + ", count=" + pool.getCount()
                    + ", capacity=" + pool.getTotalCapacity()
                    + ", memoryUsed=" + pool.getMemoryUsed());
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        boolean observe = args.length == 1
                && args[0].equals("observe");
        if (!(args.length == 0 || observe)) {
            throw new IllegalArgumentException(
                    "Use no arguments or the word observe");
        }

        List<byte[]> retained = new ArrayList<>();
        int checksum = 0;
        for (int i = 0; i < COUNT; i++) {
            byte[] data = new byte[BLOCK_BYTES];
            data[0] = (byte) (i + 1);
            retained.add(data);
            checksum += Byte.toUnsignedInt(data[0]);
        }

        System.out.println("buffers: " + retained.size());
        System.out.println("payload bytes: "
                + (retained.size() * BLOCK_BYTES));
        System.out.println("checksum: " + checksum);
        System.out.println("first byte: "
                + Byte.toUnsignedInt(retained.get(0)[0]));
        System.out.println("last byte: "
                + Byte.toUnsignedInt(retained.get(15)[0]));
        System.out.println("ready: true");

        reportRuntime();

        // observe: duy trì process sống để công cụ jcmd
        // có cơ hội attach từ một terminal khác.
        if (observe) {
            Thread.sleep(30_000);
        }
        System.out.println("done");
    }
}
```

### Compile và chạy

```bash
javac -encoding UTF-8 JvmManagementSnapshot.java
java -Xms32m -Xmx128m JvmManagementSnapshot
```

**Standard output chính xác, khi chương trình chạy hoàn tất bình thường và không bật thêm logging:**

```text
buffers: 16
payload bytes: 1048576
checksum: 136
first byte: 1
last byte: 16
ready: true
done
```

### Giải thích số học

- `BLOCK_BYTES = 64 × 1024 = 65536`.
- `COUNT = 16`, tổng array payload = **16 × 65536 = 1.048.576 bytes**.
- Các byte đầu lần lượt `1..16`, tổng **16 × 17 / 2 = 136**.
- Array đầu mang `1`, cuối mang `16`.
- `retained` được dùng sau khi tạo, giúp chương trình có phần data observable phù hợp.
- Các số về runtime do `reportRuntime()` in ra `System.err` nên có **giá trị biến động**, không ghi cứng vào expected stdout.

### Các metric in stderr là động

Ví dụ chương trình có thể in những trường sau:

```text
vm name: (tên VM đang chạy)
vm vendor: (vendor thực tế)
vm version: (version/build)
runtime pid: (PID)
runtime uptime ms: (milliseconds)
jvm input argument count: (số arguments)
heap used bytes: (bytes)
heap committed bytes: (bytes)
heap max bytes: (bytes)
nonheap used bytes: (bytes)
threads live: (số)
classes loaded: (số)
processors available: (số)
system load average: (số, có thể âm)
compiler time ms: (số) / compiler time: unavailable
gc: (tên, số collection và thời gian)
buffer pool: (tên, count, capacity và memoryUsed)
```

Đây là **mẫu giải thích trường**, không phải literal stderr. Một số metric có thể `-1` khi không được định nghĩa; số lượng/tên GC và buffer pools tùy runtime.

## 16. Lab kết hợp: attach `jcmd` vào chương trình vừa chạy

### Terminal A — chạy ứng dụng đủ lâu để quan sát

```bash
java -Xms32m -Xmx128m JvmManagementSnapshot observe
```

Ứng dụng in sáu dòng chức năng, sau đó report runtime lên stderr, chờ một khoảng thời gian ngắn trước khi in `done`. Đây là **thí nghiệm**, không phải mẫu production sleep/monitoring. Thời gian chờ và khả năng scheduler làm việc không phải guarantee attach thành công.

### Terminal B — tìm đúng JVM và đọc trạng thái

Khi Terminal A vẫn chạy:

```bash
jcmd -l
jcmd PID help
jcmd PID VM.version
jcmd PID VM.command_line
jcmd PID VM.flags
jcmd PID GC.heap_info
jcmd PID Compiler.codecache
```

Chọn **PID từ cùng process JVM trong Terminal A**, không chọn bừa một PID khác trong máy. Có thể đối chiếu với trường `runtime pid` ở stderr của chương trình. Trên Windows, chạy từ đúng quyền và context; trên Linux/container kiểm tra namespace và user.

### Đối chiếu có ý nghĩa

| `java.lang.management` | `jcmd` | Cách hiểu |
| --- | --- | --- |
| `RuntimeMXBean.getPid()` | PID đầu `jcmd PID ...` | Hai dữ liệu phải trỏ cùng process |
| `getVmName/Vendor/Version()` | `VM.version` | VM identity tương ứng, format có thể khác |
| `getInputArguments()` | `VM.command_line` | Hai phạm vi thông tin khác nhau; không đòi identical |
| `MemoryUsage.getMax()` | `VM.flags` → MaxHeapSize | Có thể đối chiếu heap maximum sau alignment |
| `MemoryMXBean.getHeapMemoryUsage()` | `GC.heap_info` | Hai snapshots có thể lệch vì thời gian/GC |
| `CompilationMXBean` | `Compiler.queue/codecache` | Compilation time khác queue/code cache metrics |
| `ThreadMXBean` | `Thread.print` | Count khác thread dump scope/timing |
| `BufferPoolMXBean` | `VM.native_memory` nếu bật | Hai accounting scopes khác, không cộng trừ máy móc |

**Quan trọng:** output của hai công cụ không nhất thiết **trùng từng byte**, vì nguồn, thời điểm đo, tính đại diện và metric definitions khác nhau. Chỉ đưa ra nhận xét khi xác định đúng metric.

## 17. JVM flags: effective, origin và thay đổi runtime

Một JVM có thể nhận cờ từ nhiều nguồn:

- User-supplied VM args như `-Xms`, `-Xmx`, `-XX:...`.
- JVM ergonomics theo memory/CPU/container.
- Default values của JDK vendor/build.
- `JAVA_TOOL_OPTIONS` / `JDK_JAVA_OPTIONS` hoặc deployment wrappers.
- Runtime-manageable flags thay đổi bởi authorized management commands.

Để quan sát options từ một JVM mới mà không chạy app:

```bash
java -Xms32m -Xmx128m -XX:+PrintFlagsFinal -version
```

Để đọc JVM đã chạy:

```bash
jcmd PID VM.flags -all
```

Một số HotSpot diagnostic views còn hiển thị **origin** của flags, ví dụ default, ergonomic, command line hoặc management. **Đừng cố đoán toàn bộ flag origins chỉ từ output launcher**, hãy đọc cái VM thực sự expose.

### Ví dụ phân biệt

Nếu command line không có `-XX:+UseG1GC` nhưng JVM chọn G1 theo ergonomics, `VM.flags` có thể cho thấy `UseG1GC=true`. Điều đó không đồng nghĩa developer đã chủ động đưa cờ vào deployment configuration.

### Chỉ đọc hay có thể đổi?

`jcmd VM.set_flag` và `HotSpotDiagnosticMXBean.setVMOption` có thể thay **một số manageable options**. Đây là **thao tác thay đổi runtime state**, có thể tác động production; **không** dùng như một phép quan sát. Không phải mọi VM flag đều writable. Nên có quy trình change management/rollback thay vì tự dùng nó để sửa `Xmx` đang chạy.

## 18. Native Memory Tracking qua `jcmd`

HotSpot **Native Memory Tracking (NMT)** giúp theo dõi nhiều native allocations nội bộ JVM. Ví dụ bật từ startup:

```bash
java -XX:NativeMemoryTracking=summary -Xms128m -Xmx512m YourApp
```

`YourApp` là class/entrypoint giả định bạn đã có, **không phải class định nghĩa trong ví dụ**. Nếu chạy trong một test service có NMT enabled:

```bash
jcmd PID VM.native_memory summary
jcmd PID VM.native_memory baseline
jcmd PID VM.native_memory summary.diff
```

### Điều NMT làm và không làm

- Báo các categories tracked như `Java Heap`, `Class`, `Thread`, `Code`, `GC` và others tùy VM.
- Phân biệt `reserved` và `committed` trong nội bộ tracking.
- `baseline` và `summary.diff` giúp quan sát biến đổi ở **cùng process**.
- **Phải bật NMT từ khi JVM khởi động**; nếu off, không lấy được retroactive allocation history bằng một `jcmd`.
- Không bao phủ **mọi malloc từ JNI/native third-party code** hoặc toàn bộ process RSS.
- Bật NMT gây overhead; nên cân nhắc và công bố khi đo performance.
- `summary.diff` không thể ghép với baseline của một process đã bị restart.

**Không dùng NMT total = RSS** hay `nonHeapUsage = NMT total`: chúng có phạm vi và semantics khác.

## 19. Những diagnostic commands cần cân nhắc tác động

Không phải mọi `jcmd` đều read-only, và ngay cả lệnh đọc cũng có thể ảnh hưởng workload.

| Command | Loại công việc | Cảnh báo |
| --- | --- | --- |
| `VM.version` / `VM.uptime` | Identity/uptime | Thường impact thấp |
| `VM.flags` / `VM.command_line` | Config/effective flags | Output có thể nhạy cảm/lớn |
| `GC.heap_info` | Heap snapshot | Tài liệu đánh dấu medium impact |
| `Thread.print` | Thread stacks/locks snapshot | Nhiều threads → output lớn, observer effect |
| `GC.class_histogram` | Đếm objects theo classes | High impact theo Oracle JDK 17 |
| `GC.heap_dump` | Tạo HPROF heap dump | High impact, nhiều disk, PII/secrets |
| `GC.run` | Yêu cầu `System.gc()` | Có thể ảnh hưởng GC/latency đáng kể |
| `JFR.start` / `JFR.dump` | Bật/ghi flight recording | Thêm data collection, disk và overhead |
| `VM.set_flag` | **Thay** manageable JVM flag | Không phải thao tác read-only |
| `VM.native_memory` | NMT report nếu enabled | Overhead/tracking scope và output lớn |

Không tự chạy `GC.heap_dump` hoặc `GC.class_histogram` trên production chỉ để “biết RAM đang dùng”, nhất là khi không có dung lượng disk/quyền/bảo vệ dữ liệu phù hợp.

### Dữ liệu nhạy cảm có thể nằm ở đâu?

- `VM.command_line` / `VM.system_properties`: credentials hoặc config values nếu ứng dụng truyền nhạy cảm trên command line/properties.
- `Thread.print`: class names, stack traces, endpoint/context names.
- Heap dump: live object values, sessions, tokens, user data.
- JFR: stack traces/events/paths, custom event payload.
- Native maps: process/library paths.

Cần áp dụng data minimization, quyền truy cập hạn chế và retention policy cho diagnostic outputs.

## 20. Truy cập management từ xa: bảo mật và giới hạn

`java.lang.management` có thể tích hợp với **JMX**, nhưng không có nghĩa công khai một JMX port trên Internet là an toàn. Nếu dùng remote monitoring:

- Ưu tiên công cụ giám sát đã được tổ chức cho phép và có authentication/TLS.
- Giới hạn network reachability; không expose JMX service công khai không xác thực.
- Quản lý credentials/certificates, rotate secrets và quyền role-based phù hợp.
- Không cho phép management write operations từ dashboard chỉ để đọc metrics.
- Kiểm tra overhead khi sampling intervals quá dày.
- Nếu process chạy trong container, mapping JMX/RMI network ports có constraints phức tạp cần cấu hình đúng.

**Không lấy `System.getProperties()` rồi expose toàn bộ qua HTTP monitoring endpoint.** Có thể làm rò rỉ tokens/paths/passwords. Với `RuntimeMXBean`, ưu tiên xuất các fields cần thiết và redact trước khi chia sẻ.

## 21. Chẩn đoán theo tình huống: triệu chứng → bộ lệnh phù hợp

### Tình huống A — Process có dùng đúng JDK/version chưa?

```bash
jcmd PID VM.version
jcmd PID VM.command_line
jcmd PID VM.flags
```

Đối chiếu `RuntimeMXBean.getVmVersion()`, `java.version`, application artifact/build ID. **Không chỉ chạy `java -version` trong shell**, bởi shell Java có thể không phải JVM process đích.

### Tình huống B — “Heap còn chỗ nhưng container OOMKilled”

Kiểm tra:

- `MemoryMXBean`: heap used/committed/max và non-heap view.
- `GC.heap_info`: collector/heap snapshot.
- `BufferPoolMXBean`: direct/mapped metrics.
- `VM.native_memory summary`: nếu process bật NMT.
- OS RSS, cgroup memory limit/current/events và nhiều processes trong cgroup.
- Metaspace, code cache, threads và native libraries.

**Không kết luận native leak** bằng một phép trừ giữa RSS và heap used; cần trend, scope và competing hypotheses.

### Tình huống C — Nghi thread leak/deadlock

```bash
jcmd PID Thread.print -l
```

Và trong code:

```java
java.lang.management.ThreadMXBean tm =
        java.lang.management.ManagementFactory.getThreadMXBean();
System.out.println(tm.getThreadCount());
System.out.println(tm.getPeakThreadCount());
```

Đọc nhiều mẫu, tìm những thread names/stacks tồn tại ngoài vòng đời mong đợi. `getThreadCount()` tăng một lúc do load không tự là leak.

### Tình huống D — Nghi class loading/metaspace tăng

Xem `ClassLoadingMXBean.getLoadedClassCount()`, `getTotalLoadedClassCount()`, `getUnloadedClassCount()`, `MemoryPoolMXBean` cho metaspace nếu có và NMT `Class` category nếu bật. Xác minh class-loader retained paths thay vì chỉ tăng `MaxMetaspaceSize`.

### Tình huống E — Nghi JIT/code cache giới hạn

```bash
jcmd PID Compiler.codecache
jcmd PID Compiler.queue
```

So với `CompilationMXBean` và runtime uptime. Một service cold vừa deploy sẽ có compiler activity; không tự kết luận “có JIT queue tức là performance bug”.

## 22. Tạo báo cáo đo lường nhất quán

Báo cáo tối thiểu nên ghi:

| Trường | Ví dụ cách điền |
| --- | --- |
| **Process identity** | PID, start time, process/container identifier |
| **VM identity** | JVM name/vendor/version/build |
| **Effective config** | Xms, Xmx, collector, RAM percentage, JVM input args |
| **Timebase** | UTC timestamp + uptime nếu có |
| **Workload** | Requests/s, concurrency, payload size |
| **Heap** | used, committed, max, GC count/time |
| **Non-heap** | MXBean pools, code cache, metaspace nếu có |
| **Buffers** | direct/mapped count/capacity/memory used |
| **Threads** | count, peak, blocked/waiting samples |
| **Container/OS** | process RSS/PSS, cgroup current/max, CPU quota |
| **Evidence** | Commands + outputs đã redact + JFR/log nếu hợp lệ |
| **Limitations** | Sampling gaps, permissions, NMT off, versions, overhead |
| **Action** | Hypothesis, next evidence, mitigation/rollback conditions |

**Mẫu dưới đây minh họa cấu trúc, không phải dữ liệu thực đo:**

```text
Observation time: 2026-10-22T10:00:00Z
Target process: PID <điền giá trị thật>
VM identity: <tên/vendor/build của JVM đích>
Command: jcmd PID VM.version
Heap used/committed/max: <số MXBean, bytes>
Thread count/peak: <số MXBean>
Direct pool: <capacity/memoryUsed hoặc unavailable>
Native memory: <NMT summary hoặc unavailable>
Container memory: <current/max từ đúng cgroup>
Hypothesis: <cơ chế giải thích triệu chứng>
Evidence: <nguồn và timestamp từng phép đo>
Limitations: <điều chưa quan sát/không có quyền>
Conclusion: <kết luận không mạnh hơn dữ liệu>
```

Không đưa placeholders này ra làm **stdout kỳ vọng** của chương trình Java. Một báo cáo hữu ích phải phân biệt **đã đọc trực tiếp** và **đang suy luận**.

## 23. Các sai lầm phổ biến

| Sai lầm | Cách sửa |
| --- | --- |
| “`java -version` của terminal là version process đang chạy.” | Dùng `jcmd PID VM.version` hoặc RuntimeMXBean của process đích |
| “`jcmd -l` không thấy JVM tức là JVM không tồn tại.” | Xem PID namespace/container, attach rights |
| “`VM.command_line` chứa tất cả effective defaults.” | Dùng thêm `VM.flags`, MXBeans |
| “`RuntimeMXBean.getInputArguments()` là toàn bộ launcher command.” | Nó là VM input arguments, có thể thiếu launcher/options |
| “System properties và `-XX` flags là một.” | Hai loại config khác nhau |
| “`getNonHeapMemoryUsage()` là mọi native memory.” | Chỉ non-heap management view |
| “`heap max − used` là RAM còn trống của máy.” | Đó là heap capacity headroom, không là free physical RAM |
| “`getSystemLoadAverage() = -1` nghĩa không có tải.” | Có thể API không có dữ liệu |
| “`ThreadMXBean` đếm đúng tất cả OS native threads.” | Scope khác với OS task count |
| “`GarbageCollectorMXBean` có mọi GC pause timestamps.” | Count/time aggregates, không phải event timeline |
| “`BufferPoolMXBean` đo mọi JNI malloc.” | Không |
| “`CompilationMXBean` luôn có trên mọi JVM.” | Có thể null/unsupported |
| “`MemoryPoolMXBean.getUsage()` luôn non-null.” | Pool invalid có thể trả null |
| “NMT có thể bật hồi tố đầy đủ qua jcmd.” | Phải bật từ startup trên HotSpot |
| “NMT total bằng RSS.” | Khác scope và accounting |
| “`jcmd VM.flags` là mutation.” | Đọc flags; `VM.set_flag` mới là mutation |
| “Thấy GC snapshot đủ để chứng minh leak.” | Cần nhiều mốc và retained data evidence |
| “Mọi `jcmd` đều chi phí thấp.” | Heap dump/histogram có impact cao |
| “Dump/logs an toàn để paste nguyên vào issue.” | Có thể chứa secrets/PII |
| “JMX remote mặc định an toàn.” | Cần auth/TLS/network restrictions |
| “Một lần sampling đủ xác định nguyên nhân.” | Cần trend, workload correlation và competing hypotheses |

## 24. Bài tập có đáp án

**Bài 1.** Cần biết VM implementation/version **của PID thực**, lệnh nào?  
**Đáp án:** `jcmd PID VM.version`; thay PID bằng process đích.

**Bài 2.** Một JVM đã khởi động nhận những effective flags nào?  
**Đáp án:** `jcmd PID VM.flags` hoặc `VM.flags -all`, tùy mức chi tiết và support.

**Bài 3.** Muốn biết CLI/launcher flags VM đã dùng, lệnh nào?  
**Đáp án:** `jcmd PID VM.command_line`; chú ý có dữ liệu nhạy cảm.

**Bài 4.** `RuntimeMXBean.getInputArguments()` có gồm `main(String[] args)` không?  
**Đáp án:** Không; không đồng nhất VM args với application args.

**Bài 5.** API lấy PID Java 17 là gì?  
**Đáp án:** `RuntimeMXBean.getPid()`.

**Bài 6.** `MemoryUsage` trả mấy trường chính?  
**Đáp án:** `init`, `used`, `committed`, `max`, đơn vị bytes; `init/max` có thể -1.

**Bài 7.** Non-heap used có bằng toàn native/JNI memory không?  
**Đáp án:** Không. Scope của management API không bao trùm mọi native allocations.

**Bài 8.** Muốn xem từng memory pool theo GC, API nào?  
**Đáp án:** `ManagementFactory.getMemoryPoolMXBeans()`, kiểm tra `getUsage()` có thể null.

**Bài 9.** Muốn đọc count/time GC tổng hợp, API nào?  
**Đáp án:** `GarbageCollectorMXBean` qua `ManagementFactory.getGarbageCollectorMXBeans()`.

**Bài 10.** GC count/time có chứng minh request p99 chậm do GC không?  
**Đáp án:** Không. Cần timeline/log/JFR/request traces được align.

**Bài 11.** `ThreadMXBean.getThreadCount()` có bằng OS thread/task count không?  
**Đáp án:** Không bắt buộc; scopes khác.

**Bài 12.** Dùng `jcmd` nào để xem thread stacks?  
**Đáp án:** `jcmd PID Thread.print -l`, cần cân nhắc impact.

**Bài 13.** `getAvailableProcessors()` có phải số core vật lý toàn host bất biến?  
**Đáp án:** Không. Đây là processors VM available, có thể ảnh hưởng bởi container/affinity và thay đổi.

**Bài 14.** `getSystemLoadAverage()` âm nghĩa là gì?  
**Đáp án:** Có thể không available/unsupported; không suy CPU idle.

**Bài 15.** Muốn xem direct buffers, API nào?  
**Đáp án:** `BufferPoolMXBean`, với `count/capacity/memoryUsed`; không thay native profiler.

**Bài 16.** `getMemoryUsed()` của buffer pool có luôn bằng capacity?  
**Đáp án:** Không; có thể khác hoặc trả -1 nếu không ước tính được.

**Bài 17.** `CompilationMXBean` lúc nào cũng có?  
**Đáp án:** Không; kiểm tra null và monitoring support.

**Bài 18.** HotSpot get một flag theo tên qua API nào?  
**Đáp án:** `HotSpotDiagnosticMXBean.getVMOption(name)` nếu option được expose.

**Bài 19.** `getDiagnosticOptions()` có chứa mọi VM flags không?  
**Đáp án:** Không; đó là tập diagnostic options writable theo bean contract.

**Bài 20.** Vì sao không chạy `jcmd 0 GC.heap_dump` bừa bãi?  
**Đáp án:** Có thể tác động nhiều JVM processes, gây pause/disk/secrets leak ngoài mục đích.

**Bài 21.** NMT off lúc start, `summary.diff` có dùng để tái dựng native allocation trước đó không?  
**Đáp án:** Không. Phải bật NMT từ startup và đặt baseline trong cùng process.

**Bài 22.** Java heap metrics khác process RSS ra sao?  
**Đáp án:** Heap là vùng JVM quản lý; RSS gồm resident pages của nhiều vùng process.

**Bài 23.** Trong `JvmManagementSnapshot`, `payload bytes` bằng bao nhiêu?  
**Đáp án:** **1.048.576**, vì 16 × 65.536.

**Bài 24.** Checksum của chương trình là gì?  
**Đáp án:** **136**, tổng số 1 đến 16.

**Bài 25.** Khi chạy `observe`, dùng phương pháp nào ghép kết quả `jcmd` với đúng Java process?  
**Đáp án:** So `RuntimeMXBean.getPid()` in stderr với PID đã chọn từ `jcmd -l`, kiểm tra process vẫn alive.

**Bài 26.** Có được hard-code giá trị `heap used bytes` làm expected unit test trong ví dụ không?  
**Đáp án:** Không. Đây là runtime-dependent metric, có thể thay theo GC/JDK/timing.

**Bài 27.** `VM.command_line` và `VM.system_properties` có cần redact trước khi chia sẻ không?  
**Đáp án:** Có, vì có thể chứa credentials, paths hoặc configuration nhạy cảm.

**Bài 28.** Đâu là bước tốt đầu tiên khi gặp OOMKilled dù heap còn chỗ?  
**Đáp án:** Phân loại termination, lấy heap/native/process/cgroup metrics có cùng timestamp, rồi mới đặt giả thuyết.

## 25. Checklist trước khi phát hành báo cáo giám sát JVM

- [ ] Đúng JVM process/PID, đúng host/container/PID namespace?
- [ ] Có VM version/vendor/build từ **target**, không chỉ từ shell?
- [ ] Có startup time/uptime để phân biệt process sau restart?
- [ ] Có effective `VM.flags` và command line được redact?
- [ ] Có xác minh `-Xms/-Xmx` qua effective flags/MemoryMXBean?
- [ ] Có hiểu heap `used/committed/max` và non-heap view đúng scope?
- [ ] Có thu GC count/time và logs/events nếu cần?
- [ ] Có thread counts, peaks và thread-state samples khi chẩn đoán contention?
- [ ] Có loaded class trends và metaspace nếu class-loader issues?
- [ ] Có direct/mapped buffer pool metrics khi nghi off-heap pressure?
- [ ] Có code cache/compilation observations nếu nghi warm-up/JIT?
- [ ] Có NMT summary/diff chỉ khi bật từ startup?
- [ ] Có OS RSS/PSS/cgroup/CPU quotas để đối chiếu JVM metrics?
- [ ] Có timestamp, sampling interval và workload tương ứng?
- [ ] Có gắn nhãn missing/unsupported metrics (null, -1)?
- [ ] Có tránh cộng trùng heap/non-heap/pool/NMT measurements?
- [ ] Có phân biệt metric snapshots với longitudinal trends?
- [ ] Có phân loại tác động/perf overhead của diagnostic commands?
- [ ] Có giữ secrets ngoài raw outputs được chia sẻ?
- [ ] Có nêu rõ những conclusions nào vẫn là giả thuyết?
- [ ] Có thể tái thực hiện lệnh và đọc được bằng chứng gốc?

## 26. Tóm tắt thực hành

```text
jcmd:
  VM.version       → JVM implementation/version
  VM.command_line  → arguments/config launch
  VM.flags         → effective HotSpot flags
  GC.heap_info     → heap/collector snapshot
  Thread.print     → threads/stacks/locks
  Compiler.codecache → JIT code cache
  VM.native_memory → NMT nếu đã bật

java.lang.management:
  RuntimeMXBean    → VM name/vendor/version, PID, uptime, args
  MemoryMXBean     → heap/non-heap init/used/committed/max
  MemoryPoolMXBean → memory pools & peak/collection usage
  GarbageCollectorMXBean → collection count/time
  ThreadMXBean     → live/peak threads
  ClassLoadingMXBean → class counts
  CompilationMXBean → compilation totals khi supported
  OperatingSystemMXBean → available processors & OS load
  BufferPoolMXBean → direct/mapped pool estimates

Điều tra:
  version → flags → resources → OS/cgroup limits
       → workload timeline → giả thuyết → bằng chứng
```

**Quy tắc cuối:** `jcmd` cho phép đặt câu hỏi trực tiếp với một JVM đang chạy, còn `java.lang.management` giúp ứng dụng/công cụ monitoring lấy trạng thái qua API. **Đúng process, đúng metric, đúng thời điểm và đúng phạm vi** quan trọng hơn số lượng lệnh đã chạy. Không biến non-heap API thành toàn native memory, không dùng snapshot làm bằng chứng leak, không thay JVM flags khi chỉ được phép quan sát.

### Tài liệu tham khảo chính thức

1. **Oracle JDK 17 — `jcmd` Command** (syntax, commands, permissions và impact): https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
2. **Java SE 17 — `RuntimeMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/RuntimeMXBean.html
3. **Java SE 17 — `MemoryMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html
4. **Java SE 17 — `MemoryUsage`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryUsage.html
5. **Java SE 17 — `MemoryPoolMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html
6. **Java SE 17 — `GarbageCollectorMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/GarbageCollectorMXBean.html
7. **Java SE 17 — `ThreadMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html
8. **Java SE 17 — `ClassLoadingMXBean` và `CompilationMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ClassLoadingMXBean.html ; https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/CompilationMXBean.html
9. **Java SE 17 — `OperatingSystemMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/OperatingSystemMXBean.html
10. **Java SE 17 — `BufferPoolMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/BufferPoolMXBean.html
11. **Java SE 17 — HotSpotDiagnosticMXBean:** https://docs.oracle.com/en/java/javase/17/docs/api/jdk.management/com/sun/management/HotSpotDiagnosticMXBean.html
12. **Oracle JDK 17 — Native Memory Tracking:** https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
13. **Oracle JDK 17 — `java` launcher/flags:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html

**Ghi nhớ:** đối chiếu **`jcmd PID VM.version/VM.flags`** với **`RuntimeMXBean` và `MemoryMXBean`**, rồi bổ sung thread/GC/buffer/native/OS evidence khi cần. Luôn lưu ý API support, metric scope, overhead, permissions và bảo mật dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-005">JFR ở mức nhập môn để đối chiếu cấp phát, GC và hoạt động luồng</a>

<details>
<summary>Xem chi tiết</summary>

# JFR ở mức nhập môn để đối chiếu cấp phát, GC và hoạt động luồng

## 1. JFR giúp trả lời những câu hỏi gì?

**Java Flight Recorder (JFR)** là hệ thống ghi nhận sự kiện chẩn đoán tích hợp trong JDK/HotSpot. JFR thu thập các sự kiện có **thời điểm**, đôi khi có **thời lượng**, **thread**, **stack trace**, thông tin JVM và các thuộc tính cụ thể của sự kiện. Tệp kết quả có đuôi `.jfr` và có thể phân tích bằng **JDK Mission Control (JMC)**, công cụ dòng lệnh `jfr` hoặc API `jdk.jfr.consumer`.

Trong bài nhập môn này, mục tiêu không phải tìm ngay một flag tối ưu JVM, mà là **đặt ba loại tín hiệu lên cùng trục thời gian**:

1. **Allocation** — object nào được cấp phát nhiều/ở call sites nào? Tốc độ cấp phát và allocation samples có dấu hiệu tăng vào lúc nào?
2. **Garbage Collection** — có collection nào, lúc nào, pause bao lâu, heap occupancy trước/sau ra sao, có phase STW hay concurrent?
3. **Hoạt động luồng** — thread nào đang tiêu CPU, chờ monitor, ngủ, park hoặc I/O? Việc chờ có trùng với GC pause/CPU pressure hay không?

Ví dụ một request mất 900 ms trong khi GC pause gần đó chỉ 12 ms: GC có thể góp phần, nhưng không thể giải thích cả 900 ms từ một sự kiện duy nhất. JFR giúp **tương quan thời điểm và call stack**, sau đó kiểm chứng bằng logs, metrics và traces độc lập.

**Phạm vi:** Java 17, JFR của JDK và HotSpot. Tên event, cấu hình `default.jfc`/`profile.jfc`, ngưỡng và cách thu thập có thể khác nhau giữa JDK vendors/updates. **JFR là công cụ quan sát, không phải API cam kết ghi tất cả mọi allocation, method call hay context switch.** Một event không xuất hiện có thể do sampling, threshold, setting disabled, recording window hoặc data loss.

## 2. JFR khác GC log, thread dump và CPU profiler ở đâu?

| Nguồn dữ liệu | Thấy gì | Giới hạn |
| --- | --- | --- |
| **GC log `-Xlog:gc*`** | Collector phases, causes, heap before/after, pauses theo collector | Không tự cho toàn bộ allocation call stacks và blocked-thread timeline |
| **JFR recording** | Events theo thời gian: allocations, GC, threads, CPU, I/O, environment | Một số sự kiện sampled/thresholded, không có đủ mọi hoạt động |
| **Thread dump `jcmd Thread.print`** | Trạng thái nhiều threads ở một thời điểm, locks và stacks | Là snapshot; có thể bỏ lỡ contention ngắn |
| **CPU sampling profiler** | Stacks tiêu CPU qua samples | Không phải công cụ đếm chính xác object allocations |
| **Heap dump** | Object graph/retaining paths tại thời điểm dump | Không cho thời điểm mỗi object được allocate; có thể rất tốn tài nguyên |
| **Application tracing/metrics** | Request IDs, business phases, p95/p99, error rate | Không tự giải thích native JVM/GC internals |
| **OS/cgroup metrics** | CPU quota/throttling, RSS, memory pressure, disk/network | Không biết trực tiếp caller Java tạo object nào |

JFR đặc biệt hữu ích khi cần **ghép nhiều tầng chẩn đoán trong một timeline**. JFR không thay thế tất cả các nguồn trên: nếu JFR cho thấy GC đỉnh vào 12:00:00 nhưng request trace mất 2 giây ở database, nguyên nhân chính có thể nằm ngoài JVM.

### Ba câu hỏi cần hỏi ngay khi mở recording

- Recording bao phủ **khoảng thời gian có triệu chứng** chưa?
- Những **event types và stack traces cần thiết đã bật** chưa?
- Timestamp/clock của application tracing, GC logs và JFR có đủ thống nhất để đối chiếu không?

Một tệp chứa event timestamps nhưng không bao gồm thời điểm lỗi hoặc thiếu event settings quan trọng chỉ có thể cung cấp **bằng chứng giới hạn**.

## 3. Kiểu sự kiện trong JFR: duration, instant, sampled

Có thể nhìn event theo các nhóm ý niệm:

| Nhóm | Minh họa | Cách hiểu |
| --- | --- | --- |
| **Instant event** | Một sự kiện tại một thời điểm | Không mặc định có duration đáng kể |
| **Duration event** | `jdk.ThreadSleep`, `jdk.JavaMonitorEnter` khi có nội dung đo phù hợp | Có start time và duration; có thể có threshold lọc sự kiện ngắn |
| **Periodic event** | JVM/heap/CPU statistics theo chu kỳ | Khoảng cách do settings quy định, không phải live continuous measurement |
| **Sampled event** | `jdk.ExecutionSample`, `jdk.ObjectAllocationSample` | Mẫu từ hoạt động thật, **không đếm toàn bộ actions** |
| **Custom application event** | `demo.Phase` do code tự phát ra | Mốc nghiệp vụ do application xác định |

Không nên nhìn event count rồi kết luận “chỉ có N allocations trong toàn JVM” khi nó là **sampled allocation event**. Cũng không thể lấy 4 samples từ một method và nói method ấy chiếm chính xác 4/10 CPU time nếu chưa hiểu sampling window, stack availability và statistical uncertainty.

### Những thuộc tính thường cần kiểm

- `startTime` và có thể `endTime` / `duration`.
- `eventThread`: thread liên quan; có thể khác nghĩa tùy event type.
- `stackTrace`: location/event stack nếu được bật/thu được.
- `gcId`, `name`, `cause` hoặc `heapUsed`: chỉ khi event type có fields tương ứng.
- `objectClass`, `allocationSize` hoặc các trường tương đương ở allocation records tùy event type.
- `event settings`: enabled, threshold, period, stackTrace.

**Không tự đoán tên field:** dùng `jfr metadata recording.jfr` hoặc APIs `RecordedEvent.getFields()` và đúng JDK 17 để xác minh metadata của file.

## 4. Chuẩn bị đúng JDK 17

Kiểm tra các công cụ đi cùng **full JDK 17**:

```bash
java -version
javac -version
jcmd -l
jfr help
```

Nếu thiếu `jcmd` hoặc `jfr`, có thể đang chạy một image runtime tối giản (`jlink`), PATH trỏ tới JRE/toolchain khác hoặc vendor cung cấp công cụ không đầy đủ. Chạy công cụ cùng phiên bản JVM khi có thể.

Một JFR recording là **tệp nhị phân**, không dùng `cat recording.jfr` để đọc như UTF-8 log. Dùng `jfr summary`, `jfr print` hoặc mở bằng JMC. Không cần Java agent thứ ba để ghi JFR cơ bản trên các full OpenJDK/HotSpot JDK 17 builds có JFR.

### Cấu hình đi kèm JDK

JDK 17 thường kèm:

```text
<JAVA_HOME>/lib/jfr/default.jfc
<JAVA_HOME>/lib/jfr/profile.jfc
```

- `default`: tập event được chọn hướng tới overhead thấp và recording kéo dài.
- `profile`: thu thập nhiều thông tin hơn; hữu ích khi cần điều tra có mục tiêu, nhưng có overhead lớn hơn.
- `none`: khởi đầu không dùng configuration file dựng sẵn rồi bật event settings cụ thể (nếu tool/build cho phép).

Không nói `profile` = “ghi tất cả mọi method call và allocation”. Một số events vẫn là samples, nhiều event types có thresholds hoặc không enabled; phải xem event settings.

## 5. Cách 1 — bật recording từ khi Java process bắt đầu

Trên HotSpot Java 17:

```bash
java -XX:StartFlightRecording=filename=startup.jfr,settings=profile,dumponexit=true -jar app.jar
```

Lệnh này dùng ví dụ tên `app.jar`; thay bằng ứng dụng Java thật. Tệp được ghi theo chính sách recording và VM shutdown; khi ứng dụng chạy lâu hoặc muốn lấy file **ngay lúc xảy ra lỗi**, dùng `jcmd JFR.dump` thay vì chỉ trông chờ process exit.

Với recording ngắn có duration và output path:

```bash
java -XX:StartFlightRecording=filename=short.jfr,settings=profile,duration=60s -jar app.jar
```

Định dạng option cần dùng là `-XX:StartFlightRecording=...`. Nếu chạy môi trường đặc thù, kiểm tra `java --help-extra` và lệnh chính thức của JDK tương ứng.

### Lúc nào dùng startup recording?

- Lỗi chỉ xảy ra trong **khởi động/lần request đầu**.
- Class loading, CDS, JIT, GC hoặc thread contention xảy ra **trước khi có thể attach `jcmd`**.
- Process sống ngắn, attach sau khi ứng dụng bắt đầu là quá muộn.

Recording có overhead, có thể ảnh hưởng startup đo được. Khi so startup performance tuyệt đối, chạy một lượt **clean** để đo, lượt khác có JFR để giải thích.

## 6. Cách 2 — attach JFR vào process đang chạy bằng `jcmd`

Tìm JVM processes:

```bash
jcmd -l
```

Khởi động recording trên đúng **PID**, ví dụ ghi 60 giây:

```bash
jcmd <PID> JFR.start name=triage settings=profile duration=60s filename=triage.jfr
```

Kiểm tra recording đang hoạt động:

```bash
jcmd <PID> JFR.check
jcmd <PID> JFR.check name=triage verbose=true
```

Dump snapshot trong khi recording vẫn tiếp tục:

```bash
jcmd <PID> JFR.dump name=triage filename=triage-snapshot.jfr
```

Stop và ghi file (khi recording còn chạy):

```bash
jcmd <PID> JFR.stop name=triage filename=triage-final.jfr
```

`<PID>` là placeholder **không copy nguyên dấu `<...>` vào shell**; thay bằng PID của JVM thật. Bạn cần quyền attach/đường dẫn JDK phù hợp. Một số containers giới hạn attach, PID namespaces và filesystem permissions, nên `jcmd` có thể không nhìn thấy process dù ứng dụng vẫn chạy.

**Quan trọng:** `JFR.dump` **không mặc định stop recording**; `JFR.stop` kết thúc recording. Nếu đã dùng `duration=60s` và recording tự stop trước lúc bạn gọi lệnh stop, phải đọc kết quả `JFR.check` thay vì lặp stop rồi giả đó là lỗi JVM.

### Chi phí recording và kích thước tệp

- `settings=default` thích hợp hơn cho quan sát dài hạn, ít overhead.
- `settings=profile` phù hợp một khoảng điều tra hữu hạn với nhiều event hơn.
- `disk`, `maxage` và `maxsize` ảnh hưởng retention/quota khi được hỗ trợ.
- Dump nhiều lần hoặc bật stack traces/sampling chi tiết tạo thêm I/O và file size.
- Dữ liệu trong `.jfr` có thể chứa tên class, package, method, thread, paths, system properties hoặc ứng dụng custom fields. Phải kiểm soát permissions và retention.

## 7. Đọc recording bằng `jfr` tool có sẵn trong JDK 17

Sau khi có `triage.jfr`:

```bash
jfr summary triage.jfr
jfr metadata triage.jfr
jfr print --events jdk.GarbageCollection triage.jfr
jfr print --events jdk.GCHeapSummary triage.jfr
jfr print --events jdk.ObjectAllocationSample triage.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.ThreadPark,jdk.ThreadSleep triage.jfr
```

Đọc:

- **`jfr summary`**: kích thước, khoảng thời gian, số event records theo loại; dùng để thấy loại event nào **có mặt trong file**.
- **`jfr metadata`**: metadata các event types/fields trong file.
- **`jfr print`**: hiển thị records cụ thể theo event types hoặc categories.
- **`--events`**: tên event type/filters; không phải `--event`.
- **`--stack-depth`**: điều chỉnh số frame in ra khi event đã có stack trace.
- **`--json`**: xuất định dạng có cấu trúc để xử lý bằng script, nếu thích hợp.

Trên **JDK 17**, dựa vào `summary`, `metadata` và `print`. **Không mặc nhiên dùng `jfr view` hoặc `jfr configure` từ tài liệu JDK mới hơn**: các subcommands đó được mở rộng trong các release sau. Nếu muốn dùng một lệnh ở môi trường cụ thể, luôn kiểm tra `jfr help`.

### Event absence không chứng minh sự kiện không xảy ra

Nếu `jfr print --events jdk.ObjectAllocationSample` không in gì, có thể do:

- Recording template không bật type ấy hoặc chỉ bật với setting khác.
- Không có sample trong cửa sổ quá ngắn.
- Stack capture hoặc allocation sampling thresholds được thiết lập khác.
- Không có allocations đủ điều kiện sampling.
- Sự kiện bị mất/recording bị cắt, `maxage`/`maxsize` giữ chưa đủ window.
- Event naming/schema khác trong VM build (xác minh `jfr metadata`).

Không kết luận “JVM không cấp phát object” chỉ từ một tệp không có `ObjectAllocationSample`.

## 8. Mở trong Java Mission Control (JMC)

**JDK Mission Control** là công cụ giao diện đồ họa giúp đọc JFR. Tùy cách phát hành, JMC được **cài riêng**, không nhất thiết nằm trong full JDK 17 distribution.

Một luồng đọc cơ bản:

1. Mở tệp `.jfr` trong JMC; xác minh duration/time range và JVM/application identity.
2. Xem **Overview** để biết tổng quan CPU, allocations, GC và những cảnh báo hiển thị.
3. Chuyển sang các trang **Memory/Allocations**, **Garbage Collections**, **Threads/Locks** (tên tab phụ thuộc JMC version).
4. Chọn khoảng thời gian **trước, trong, sau** latency spike; giữ cùng time window khi đối chiếu các charts.
5. Dùng filter theo thread, class, event type, call stack nếu dữ liệu có.
6. Kiểm tra các event fields và timestamps thực, không chỉ màu sắc/summary của JMC.
7. Đối chiếu request metrics/tracing và `-Xlog:gc*` nếu nghi độ trễ GC.
8. Nếu thiếu allocation/thread stack, thu một recording khác với đúng settings, không gán nguyên nhân khi chưa có dữ liệu.

JMC có views như Flame Graph/Stack Trace/Event Browser tùy version/plugins; **không cần GUI** để học đọc data vì JDK 17 có `jfr print`. Việc một tab của JMC hiển thị trống có thể do không có data hoặc bị lọc/ngưỡng; không có nghĩa hoạt động tương ứng “bằng 0”.

## 9. Sự kiện về cấp phát bộ nhớ: cần đọc cẩn thận

Các event liên quan:

| Event hoặc nhóm | Dùng để hỏi gì? | Giới hạn quan trọng |
| --- | --- | --- |
| `jdk.ObjectAllocationSample` | Những allocations được **sample** với class/call site, ước lượng hotspot | Không phải một record cho mọi object |
| `jdk.ObjectAllocationInNewTLAB` | Các allocations/chu kỳ TLAB-related events khi enabled | Không phải số từng object allocation trong TLAB |
| `jdk.ObjectAllocationOutsideTLAB` | Allocation đi ngoài TLAB khi enabled | Không đầy đủ toàn bộ allocation graph |
| `jdk.ThreadAllocationStatistics` | Một số counters/statistics theo thread | Có chu kỳ đo, không phải mỗi object |
| `jdk.GCHeapSummary` | Heap occupancy ở các điểm GC, có quan hệ gcId/timing | Không trực tiếp là cumulative allocation bytes |
| `jdk.OldObjectSample` | Có thể hỗ trợ retained-object/leak investigation khi setup phù hợp | Sampling và path-to-gc-roots tốn kém, không chắc event được bật |

Đây là **các loại event thường thấy trên HotSpot**, nhưng setting của mỗi recording quyết định event nào được ghi, stack trace có tồn tại hay không và áp dụng threshold/sampling nào.

### Phân biệt allocated bytes, live bytes và retained bytes

- **Allocated bytes:** tổng bytes objects được cấp phát trong khoảng đo; có thể lớn hơn heap capacity do GC tái sử dụng vùng.
- **Heap used:** occupancy snapshot ở một thời điểm/pha; không phải tổng bytes đã cấp phát từ lúc JVM start.
- **Live bytes sau GC:** những objects còn reachable theo collector; có thể chịu ảnh hưởng collector/cấu hình snapshot.
- **Retained size:** lượng memory mà một object có thể giữ sống qua reference graph; thường cần heap dump/object graph analysis, **không phải field cộng dồn có sẵn ở mọi JFR allocation sample**.

Một dòng có `allocationSize=256 KiB` trong một sample **không** bảo đảm chỉ có duy nhất một allocation 256 KiB trong cả JVM. Nếu một chart JMC hiển thị **ước lượng allocation rate**, ghi rõ sampling model và interval của chart.

## 10. Khi nào sampling làm sai trực giác?

Giả sử một module tạo hàng triệu objects nhỏ, còn một module khác tạo ít arrays lớn:

```text
Workload giả định:
  Module A: 2.000.000 đối tượng nhỏ
  Module B: 200 arrays lớn

JFR samples:
  A: số events nào đó
  B: số events nào đó

Không được kết luận count events A/B == count objects A/B.
```

Tùy sampling policy, class có allocation bytes lớn có thể được thể hiện nổi bật dù số objects ít. Mô hình phụ thuộc JFR event settings và type. Nếu cần **đếm chính xác business objects** hoặc **đo allocations bytes/op trong một benchmark**, cân nhắc JMH GC profiler, `com.sun.management.ThreadMXBean` khi supported, allocation instrumentation hoặc workload-specific counters phù hợp; chúng cũng có overhead và scope riêng.

### Stack trace không tự nói tất cả nơi cấp phát

Một stack trong event cho bạn **context của sample**, không bắt buộc chứa toàn bộ inline frames/JIT transformations ở cùng mức Java source. Compiler có thể eliminate allocations qua escape analysis, nên source có `new` không bảo đảm tương ứng heap allocation thật. Ngược lại, JIT/lib code có thể tạo objects bạn không thấy trực tiếp trong source tại hot method.

Không dùng “không có object X trong JFR” làm bằng chứng object X được stack-allocated.

## 11. GC events: điều gì tương quan được với allocations?

Các tên event cần nhận biết:

- `jdk.GarbageCollection`: thông tin collection, thường có gcId, cause/name và duration phù hợp.
- `jdk.GCHeapSummary`: heap status ở GC-related points; khi available có thể ghép với GC id và thời điểm.
- `jdk.GCPhasePause` và các phase-level events: các khoảng GC pause/phases, giúp tìm STW components.
- Các collector-specific events: G1 young/mixed/old-region details, concurrent phases và statistics; availability tùy build.
- `jdk.ObjectAllocationSample`: samples cấp phát trong khoảng trước/giữa các GC events.

Một timeline:

```text
JVM time ------------------------------------------------------->

Allocation samples   • • • • • • • •     • • •    • • • • •
Heap used          _/^^^^^^^^^^^^^\____/^^^^^\__/^^^^^^^^^
GC events                          [Young]    [Mixed]
JavaMonitorEnter        [blocked]             [blocked]
ThreadPark         [park------]        [park--]
App request        [-------------------- slow ----------------]

Các vị trí chỉ là minh họa; không có dữ liệu thực trong sơ đồ.
```

### Điều có thể suy từ sự trùng thời điểm

Nếu allocations tăng mạnh ngay trước một Young GC, đó là một **giả thuyết hợp lý** về allocation pressure kích hoạt GC. Nếu GC pause xảy ra bên trong request latency window, pause có thể góp một phần độ trễ. Nhưng **coincidence không chứng minh cause**, nhất là khi nhiều threads và requests chạy đồng thời.

Nếu heap used tăng rồi giảm, đó có thể là chu kỳ GC bình thường. Một “đường răng cưa” không tự chứng minh leak. Muốn điều tra retention, xem **after-GC floor trends** qua nhiều collections và object retaining paths.

## 12. Phân biệt STW pause và concurrent GC work

Không được lấy **toàn bộ GC cycle elapsed time** làm thời gian Java application bị dừng. Generational/concurrent collectors có thể có:

- **Stop-the-world pauses:** application threads bị dừng theo safepoint/collector semantics.
- **Concurrent marking/cleanup:** GC work thực hiện đồng thời với application, tiêu CPU/cache/memory bandwidth nhưng không dừng app suốt interval.
- **Concurrent cycle completion event:** biểu diễn tổng khoảng chạy, có thể bao gồm những pha concurrent và STW subphases.
- **CPU user/system time:** tổng CPU time nhiều GC workers có thể lớn hơn elapsed wall-clock thời gian.

Ví dụ **số giả định**:

```text
GC cycle wall time: 200 ms
  initial-mark pause:  8 ms
  concurrent work:   170 ms (không có nghĩa STW 170 ms)
  remark pause:      14 ms
  cleanup/other:      8 ms

Không cộng 200 + 8 + 14 để ra tổng STW.
```

Để tính STW pause fraction, chọn đúng các **pause events** không đếm trùng phases; đối chiếu với `-Xlog:gc*,safepoint=info` khi cần hiểu time to safepoint. Đừng suy rằng mọi duration trong JFR của nhóm GC là thời gian request bị ngưng.

## 13. Luồng: các event dễ nhầm nhau

| Event | Quan sát mô hình | Không đồng nghĩa |
| --- | --- | --- |
| `jdk.ThreadStart` / `jdk.ThreadEnd` | Khởi/kết thúc Java thread | CPU có chạy liên tục từ start đến end |
| `jdk.ThreadSleep` | Thread gọi sleep với duration/threshold được ghi | Đang đợi monitor hoặc nhả monitor |
| `jdk.ThreadPark` | Parking thông qua cơ chế park phù hợp | Mọi parked thread bị deadlock |
| `jdk.JavaMonitorEnter` | Monitor-entry contention event khi bật/đạt ngưỡng | Mỗi lệnh synchronized đều bị contention |
| `jdk.JavaMonitorWait` | Monitor wait khi gọi `Object.wait` | Giống intrinsic monitor-entry BLOCKED |
| `jdk.ExecutionSample` | Một stack sample khi thread được lấy mẫu | Số lần method được gọi, exact CPU time |
| `jdk.ThreadCPULoad` | Tỷ lệ CPU theo thread ở các thời điểm/chu kỳ | CPU usage chính xác từng request |
| `jdk.JavaThreadStatistics` | Các thống kê/số lượng thread | Toàn bộ thread state transitions |

**`ThreadSleep`**, **`ThreadPark`** và **`JavaMonitorEnter`** là **ba cơ chế khác nhau**:

- `Thread.sleep` trì hoãn thread nhưng **không tự thả monitor** mà thread đang sở hữu.
- `LockSupport.park/parkNanos` dùng permit-based parking, thường gặp trong thread pools/executors/futures.
- Contention khi enter `synchronized` phát sinh từ monitor đang do thread khác sở hữu.
- `Object.wait` có quy tắc thả và tái acquire monitor của object được wait, khác sleep/park.
- Trạng thái `WAITING` có thể hoàn toàn bình thường khi worker idle, không tự là deadlock.

## 14. Thread timeline: đọc như một câu chuyện, không chỉ đếm events

Giả sử service Java có thread pool:

```text
12:00:00.000 Request starts on worker-3
12:00:00.015 ObjectAllocationSample around JSON parsing
12:00:00.040 JavaMonitorEnter: worker-3 waits on a shared lock
12:00:00.155 JavaMonitorEnter ends; worker-3 acquired lock
12:00:00.170 GC pause 10 ms
12:00:00.230 Request ends

(Số và event names chỉ là kịch bản giả định.)
```

Cách đọc có trách nhiệm:

1. Request dài 230 ms, theo tracing/app marker.
2. Monitor waiting interval ~115 ms **có thể** là một contributor.
3. GC pause ~10 ms nằm trong interval; có thể đóng góp, nhưng phải xem thread và global STW semantics.
4. Allocation sample gần parse path gợi ý caller tạo objects, chưa nói 100% object count.
5. CPU sample nếu có giúp tách thời gian chạy CPU với thời gian chờ.
6. Chưa kết luận lock owner là method/class nào nếu event metadata/call stacks không có đủ bằng chứng.
7. Sau khi có giả thuyết, thu thêm thread dumps/lock owner info, request traces hoặc stress test để xác nhận.

**Không lấy tổng durations của các events chồng nhau rồi so với request elapsed** — có thể double-count và một event chỉ biểu diễn phần hoạt động gián tiếp của request.

## 15. Kết hợp JFR với application markers

Tự định nghĩa một **custom JFR event** cho một pha nghiệp vụ, ví dụ `checkout.loadCart`, `checkout.calculate` hay `batch.parse`, giúp biết vùng thời gian nào thuộc request/job. Khi phân tích allocations/GC/locks, đặt markers cùng timeline để tránh đoán “cái GC này chắc liên quan request X”.

Dùng Java API trong module **`jdk.jfr`**:

```java
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("demo.BusinessPhase")
@Label("Business Phase")
class BusinessPhase extends Event {
    @Label("Phase name")
    String name;
}
```

Đây là **fragment**, chưa có `main`. Khi application tạo, gọi `begin()` rồi `commit()` trong đường thực thi cần đo, event có start/duration phù hợp và có thể chứa custom fields.

**Đừng nhét secrets, access tokens, email cá nhân, request payload raw** vào custom event fields. Một `.jfr` có thể được chia sẻ cho đội performance/support; dữ liệu nhạy cảm cần được hạn chế, bảo vệ và xóa theo chính sách.

## 16. Java 17 chạy được: ghi allocation, thread contention và custom markers

Tạo file **`JfrTriadDemo.java`**:

```java
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;

public class JfrTriadDemo {
    @Name("demo.Phase")
    @Label("Demo phase")
    static final class Phase extends Event {
        @Label("Phase name")
        String phase;
    }

    private static volatile byte[] lastArray;
    private static volatile long checksum;

    private static void mark(String label) {
        Phase event = new Phase();
        event.phase = label;
        event.commit();
    }

    private static void allocations() {
        long total = 0;
        for (int i = 0; i < 240; i++) {
            byte[] bytes = new byte[256 * 1024];
            bytes[0] = (byte) i;
            bytes[bytes.length - 1] = (byte) (i / 2);
            total += bytes[0] & 0xff;
            lastArray = bytes;
        }
        checksum = total;
    }

    private static void contention() throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch acquired = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (monitor) {
                acquired.countDown();
                try {
                    Thread.sleep(180);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "jfr-holder");

        Thread contender = new Thread(() -> {
            try {
                acquired.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (monitor) {
                checksum += 1;
            }
        }, "jfr-contender");

        holder.start();
        contender.start();
        holder.join();
        contender.join();
    }

    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording(
                Configuration.getConfiguration("profile"))) {
            recording.setToDisk(true);
            recording.enable("demo.Phase")
                    .withThreshold(Duration.ZERO);
            recording.enable("jdk.JavaMonitorEnter")
                    .withThreshold(Duration.ZERO);
            recording.enable("jdk.ThreadSleep")
                    .withThreshold(Duration.ZERO);
            recording.enable("jdk.ThreadPark")
                    .withThreshold(Duration.ZERO);
            recording.enable("jdk.GarbageCollection");
            recording.enable("jdk.ObjectAllocationSample");

            recording.start();
            mark("allocation");
            allocations();
            mark("threads");
            contention();
            LockSupport.parkNanos(Duration.ofMillis(60).toNanos());
            mark("end");

            recording.stop();
            recording.dump(Path.of("jfr-triad.jfr"));
        }
        System.out.println("markersCommitted=3");
        System.out.println("recording=jfr-triad.jfr");
    }
}
```

Chạy bằng **full JDK 17 có `jdk.jfr`**:

```bash
javac --release 17 JfrTriadDemo.java
java -Xms32m -Xmx64m JfrTriadDemo
jfr summary jfr-triad.jfr
jfr print --events demo.Phase jfr-triad.jfr
```

**Hai dòng `System.out` do chính Java main in ra khi hoàn tất bình thường:**

```text
markersCommitted=3
recording=jfr-triad.jfr
```

Các thông điệp runtime JFR/chẩn đoán khác, số lượng events và định dạng `jfr summary`/**`jfr print` không có stdout cố định**. Tệp `jfr-triad.jfr` được tạo trong working directory khi quyền ghi cho phép; một số environments có thể không cung cấp recording permission/disk space/JFR capabilities.

### Những gì chương trình thật sự làm

1. Tạo một `Recording` với cấu hình `profile` rồi bật rõ custom marker và một số event types.
2. Commit **3 custom marker events** với nhãn `allocation`, `threads`, `end`.
3. Tạo 240 arrays kích thước payload 256 KiB (tổng khoảng **60 MiB payload allocations yêu cầu**; object layout có header/padding). Biến `lastArray` giữ reference tới array cuối, không giữ cả 240 arrays sống.
4. Hai threads dùng **cùng monitor**. Holder lấy monitor trước và `sleep` bên trong; contender chờ latch rồi thử enter monitor, tạo khả năng thấy `jdk.JavaMonitorEnter`.
5. Main `parkNanos` sau khi hai threads join, tạo tình huống có thể sinh `jdk.ThreadPark`.
6. Stop và dump recording xuống `jfr-triad.jfr`.

**Không bảo đảm** có chính xác một GC hay một JavaMonitorEnter event, bởi runtime scheduling, collector, allocation optimization, JFR event thresholds và compiler behavior thay đổi. `jdk.ObjectAllocationSample` không chứa đúng 240 records như số arrays của source. `markersCommitted=3` chứng minh code gọi commit 3 lần, không phải lời hứa mọi dữ liệu recording không thể bị mất trong mọi điều kiện.

### Vì sao không đưa `System.gc()` vào demo?

`System.gc()` là yêu cầu explicit collection, có thể bị bỏ qua hoặc được triển khai theo chính sách GC khác, và không đại diện allocation-triggered young GC. Demo cố tạo **allocated data** và để collector hoạt động theo chính sách bình thường; nếu thực tế không có GC event, hãy tăng thời gian/cường độ **có kiểm soát** trong môi trường test, không ép OOME hoặc chạy stress vô hạn.

## 17. Đọc custom events và đối chiếu với GC/thread events

Sau demo:

```bash
jfr summary jfr-triad.jfr
jfr print --events demo.Phase jfr-triad.jfr
jfr print --events jdk.ObjectAllocationSample jfr-triad.jfr
jfr print --events jdk.GarbageCollection,jdk.GCHeapSummary jfr-triad.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.ThreadPark,jdk.ThreadSleep jfr-triad.jfr
```

Hãy ghi lại:

- Timestamp/ordering của markers `allocation`, `threads`, `end`.
- Các allocation samples nào nằm giữa `allocation` và `threads`?
- GC nào overlap giai đoạn allocation?
- MonitorEnter event ghi caller/thread nào, duration bao lâu?
- `ThreadSleep` của `jfr-holder` có được ghi, duration có gần 180ms không?
- `ThreadPark` của main có được ghi, duration có gần 60ms không?
- Có các events không liên quan đến demo (JDK threads, compiler/GC startup) hay không?
- JFR đã bật event settings, thresholds và stackTrace phù hợp chưa?

**Không mặc định mọi event startTime đều có cùng origin semantic** (một event có duration, một event là sample). Khi đối chiếu nên dùng timeline dựa trên **thời điểm sự kiện**, chú ý timezone/time sources và timestamp rendering của công cụ.

## 18. Java 17: đọc lại số lượng marker bằng API `RecordingFile`

Tạo file **`JfrMarkerReader.java`** để đọc `.jfr` bằng API chuẩn của module `jdk.jfr.consumer`:

```java
import java.nio.file.Path;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

public class JfrMarkerReader {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("usage=JfrMarkerReader <file.jfr>");
            return;
        }

        int markerCount = 0;
        int gcCount = 0;
        int monitorEvents = 0;

        try (RecordingFile input =
                new RecordingFile(Path.of(args[0]))) {
            while (input.hasMoreEvents()) {
                RecordedEvent event = input.readEvent();
                String type = event.getEventType().getName();

                if (type.equals("demo.Phase")) {
                    markerCount++;
                    System.out.println("phase="
                            + event.getString("phase"));
                } else if (type.equals("jdk.GarbageCollection")) {
                    gcCount++;
                } else if (type.equals("jdk.JavaMonitorEnter")) {
                    monitorEvents++;
                }
            }
        }

        System.out.println("markerEvents=" + markerCount);
        System.out.println("gcEvents=" + gcCount);
        System.out.println("monitorEvents=" + monitorEvents);
    }
}
```

```bash
javac --release 17 JfrMarkerReader.java
java JfrMarkerReader jfr-triad.jfr
```

Nếu cả ba custom marker events được ghi/giữ trong file, output sẽ chứa:

```text
phase=allocation
phase=threads
phase=end
markerEvents=3
gcEvents=<số động>
monitorEvents=<số động>
```

**Hai dòng `<số động>` ở đây chỉ là mô tả template**, không phải literal stdout hay kết quả cố định. Event ordering/traces và GC/monitor event counts phụ thuộc recording thực tế. Trên hệ thống không ghi đủ marker hoặc file không đúng, `markerEvents` có thể khác 3; đó là lý do kiểm tra thay vì mặc định file luôn hoàn chỉnh.

### API đọc event không yêu cầu JMC

`RecordingFile` có thể dùng trong một Java CLI analysis tool mà không cần mở GUI. Với large files, đọc stream từng event như ví dụ thay vì `readAllEvents` để tránh giữ tất cả records trong memory. Nếu cần nhiều aggregation theo time windows, cân nhắc timestamp/field availability và event-specific schema.

**Không được tự gọi `getString("phase")` với event type khác**: field này do custom `demo.Phase` định nghĩa; event built-in có field khác và cần tra metadata.

## 19. Correlation workflow: allocation → GC → luồng → request

Một workflow nhập môn đáng tin:

**Bước 1 — Chọn khoảng sự cố.** Dùng request traces hoặc timestamps từ monitoring để xác định 30–120 giây khi throughput giảm/p99 tăng.

**Bước 2 — Thu recording cùng cửa sổ.** Nếu tình huống dễ tái lập, dùng `JFR.start duration=... settings=profile`. Nếu hiếm, dùng recording `default` lâu hơn, retention có giới hạn và dump khi có trigger.

**Bước 3 — Kiểm tra file.** `jfr summary` cho duration và event counts; `jfr metadata` cho field schemas và event types thực tế.

**Bước 4 — Xác định CPU/allocations.** CPU samples và allocation samples ở methods/classes nào? Stack traces có hay không, sampling interval có đủ không?

**Bước 5 — Tìm GC gần đó.** `jdk.GarbageCollection`/`GCHeapSummary`/`GCPhasePause`, phân biệt pause với concurrent work.

**Bước 6 — Tìm blocked/waiting threads.** `JavaMonitorEnter`, `ThreadPark`, `JavaMonitorWait`, `ThreadSleep` có duration overlaps với request không?

**Bước 7 — Ghép business phase.** Application tracing/custom marker với request IDs không nhạy cảm và mốc thời gian giúp xác định event nào thuộc hoặc gần request.

**Bước 8 — Kiểm tra nguyên nhân thay thế.** GC pause 8ms không thể giải thích request 2s; tìm I/O, DB query, lock queue, CPU throttling hoặc network.

**Bước 9 — Thu thêm đúng công cụ.** Nếu nghi memory retention, GC logs/heap dump; nếu nghi deadlock, thread dumps; nếu nghi throughput CPU, CPU profiler; nếu nghi DB, tracing.

**Bước 10 — Chỉ kết luận khi đủ evidence.** Có thể ghi “allocation samples tăng trước young GC và request latency cùng lúc tăng, nghi allocation pressure” trước khi khẳng định caller gây lỗi.

**Bước 11 — Đổi một yếu tố.** Giảm allocation/cải thiện lock granularity/đổi GC flag riêng, đo lại trên workload tương đương.

**Bước 12 — So với baseline.** p95/p99, request rate, GC pause fraction, allocation bytes/op hoặc per-second, CPU, RSS/cgroup pressure; không chỉ event count.

## 20. Ba mẫu đọc kết quả thường gặp

### A. Allocation pressure và nhiều Young GC

Giả định:

```text
12:00:00–12:00:30
  Allocation samples tăng trên JsonDecoder.decode
  Nhiều G1 Young GC trong cùng cửa sổ
  P99 latency tăng từ 60ms -> 140ms
  GC pauses mỗi event khoảng 4–15ms
```

Nhận định **có cơ sở nhưng chưa phải kết luận cuối**: allocation-intensive path có thể gây pressure và nhiều young pauses, nhưng cần biết total STW fraction, request concurrency, CPU/GC background costs. Một vài pauses 15ms không tự giải thích toàn bộ latency 140ms. Kiểm tra payload sizes, temporary objects, caller/allocations và baseline dưới cùng workload.

### B. Thread contention, ít GC

Giả định:

```text
12:05:00–12:05:30
  JavaMonitorEnter durations cao cho lock X
  Thread pool workers BLOCKED nhiều
  GC activity thấp, pause p99 nhỏ
  CPU usage thấp nhưng request queue tăng
```

Có thể có **lock bottleneck**, nhưng phải biết actual monitor owner, thread stacks, critical section dài vì I/O hay computations nào. Không tăng heap thay cho việc tìm lock contention.

### C. Parked threads và request queue

Giả định:

```text
12:10:00–12:10:30
  Nhiều ThreadPark events của worker pool
  Một số workers idle
  User requests vẫn chờ trong queue khác
  GC ổn định
```

`park` có thể là **hành vi thread pool idle bình thường**. Queue backlog có thể do thiếu worker ở một pool khác, slow I/O, backpressure hoặc lỗi routing. Không vội kết luận “park làm chậm ứng dụng” từ số `ThreadPark` records; cần event threads, duration, queue metrics và request tracing.

## 21. `jdk.JavaMonitorEnter` không phải toàn bộ lock contention

Một ứng dụng có thể dùng:

- Intrinsic `synchronized`: liên quan JVM monitors, có thể thấy `JavaMonitorEnter` khi contention/threshold đạt.
- `ReentrantLock`/`Condition`: thường dựa trên AQS/`LockSupport.park`, có thể hiện ở `ThreadPark` hoặc execution stacks; không phải mọi explicit lock contention thành monitor event.
- Concurrent queues/semaphores/futures: có thể park/spin/cas theo API implementation.
- Native locks hoặc JNI: không nhất thiết hiện thành các Java monitor events đơn giản.

Nếu recording thấy ít `JavaMonitorEnter`, không thể tuyên bố “ứng dụng không có contention”. Có thể contention ở explicit locks hoặc events dưới threshold. Dùng JFR cùng thread dumps, `LockSupport`/AQS stacks, pool metrics và application tracing.

### Waiting không nhất thiết là bottleneck

Thread pool giữ nhiều workers trong `WAITING` hoặc `TIMED_WAITING` khi không có jobs có thể là bình thường. Câu hỏi quan trọng là **khi có request**, những thread cần thiết có tiến triển theo latency SLO không, hay đang đợi tài nguyên/cơ chế gây backlog?

## 22. `jdk.ExecutionSample` và CPU hot methods

Execution sampling giúp biết stacks nào **thường xuất hiện khi JVM lấy mẫu**. Tuy nhiên:

- Không phải mỗi method invocation có một event.
- Không phải duration của một sample là thời gian thực method chạy.
- Java stack traces có thể phản ánh inlining/deoptimization tùy profiler/implementation.
- Native CPU work, GC threads, OS CPU throttle và thread scheduling cần thêm dữ liệu.
- Sampling period/settings ảnh hưởng số observations và statistical confidence.
- Warm-up/interpreter/C1/C2 transitions có thể thay đổi code paths trong recording.

Khi một method hiện nhiều trong execution samples, đó là **gợi ý hotspot** chứ không phải bằng chứng 100% CPU time. Nếu cần định lượng CPU cycles/tổng OS CPU sát hơn, kết hợp OS profiler và CPU usage metric.

## 23. Đọc stack trace và biết nơi event được sinh

Tại một event có stack trace:

```text
java.example.Parser.parse(...)
java.example.Service.process(...)
java.example.RequestHandler.handle(...)
...
```

Đây là **ví dụ stack giả định**. Đọc:

1. Frames gần event có thể giúp xác định method đang allocate, sleep, park hoặc enter monitor.
2. Caller chain cho biết vì sao method ấy được gọi.
3. Có thể có Java/JDK framework internals; không phải mọi frame đều do application tạo.
4. Stack bị cắt do `--stack-depth` hoặc recording event settings; top/bottom frames có thể không đầy đủ.
5. Một event có thể không kèm stack dù chính activity xảy ra; configuration quyết định.
6. Stack trace cho thấy **where** tại một thời điểm, không tự chứng minh business causality.

Một class thư viện như `java.util.HashMap` xuất hiện gần stack top **không đủ** để kết luận bug HashMap. Call sites của ứng dụng và input shapes là điều quan trọng hơn.

## 24. `default` vs `profile`: chọn cấu hình theo mục đích

| Tình huống | Gợi ý cấu hình | Rủi ro/giới hạn |
| --- | --- | --- |
| Theo dõi service lâu dài, overhead thấp | `settings=default` và bounded retention | Có thể thiếu allocation/lock details cần để chẩn đoán sâu |
| Tái lập latency spike vài phút | `settings=profile` | Nhiều events và overhead hơn |
| Nghi một specific lock | Bật đúng `jdk.JavaMonitorEnter`/`ThreadPark` thresholds | Tập dữ liệu hẹp dễ bỏ sót bottleneck khác |
| Nghi heavy allocations | Allocation sampling và cần stackTrace phù hợp | Sampling không phải exact allocation count |
| Nghi memory leak retention | JFR liên quan old object/GC roots nếu build hỗ trợ | Path-to-gc-roots rất tốn kém, không bật bừa |
| Nghi cold startup | `-XX:StartFlightRecording` | JFR có thể làm startup time thay đổi |
| Nghi CPU-only hot path | Execution samples/CPU profiling | CPU samples khác blocked-time events |

**Đọc file `default.jfc`/`profile.jfc` của đúng JDK** thay vì tuyên bố tuyệt đối những event types cụ thể “chắc chắn bật trong profile” trên mọi update/vendor.

Cấu hình threshold 0 chỉ nên dùng **trong bài test ngắn có kiểm soát**; trong production, ghi mọi micro-block/sleep có thể gây overhead/log volume khó chấp nhận.

## 25. Data loss, retention và lệch kết luận

JFR dùng buffers/chunks, nên có những tình huống:

- Recording được start **sau** latency spike.
- Recording hết hạn hoặc bị truncate vì `maxage`/`maxsize`.
- Runtime bị crash trước lúc dump/flush đủ.
- Buffers quá tải, xuất hiện dấu hiệu `jdk.DataLoss` nếu được ghi.
- Một số events bị suppress bởi thresholds hoặc sampling period.
- Event stacks disabled để giảm overhead.
- JFR file đến từ **JVM replica khác** so với request tracing.
- Timestamps/timezone không được chuẩn hóa khi ghép logs.

Trước khi đưa ra kết luận về absence, kiểm tra recording start/end times, JVM identity, PID/container identity, event settings, số lượng records và DataLoss indicators. **“Không được ghi” khác “không xảy ra”.**

## 26. Bảo mật tệp `.jfr`

Recording có thể chứa:

- Tên packages/classes/methods, thread names và call stacks.
- JVM system properties, OS/CPU/runtime metadata.
- File/Socket I/O context, paths và identifiers.
- Custom JFR event fields do application tự thêm.
- Một phần dữ liệu có thể cho biết cấu trúc nội bộ ứng dụng hoặc thông tin nhạy cảm.

Quy tắc:

1. Chỉ thu event types cần thiết; tránh payload/PII/raw tokens trong custom events.
2. Lưu tệp trong path có ACL/permissions thích hợp; không public share mặc định.
3. Có retention, encryption và cơ chế xóa file sau xử lý.
4. Trước khi đưa cho bên thứ ba, review loại metadata có thể tiết lộ.
5. Đừng bật `path-to-gc-roots=true` trên production chỉ vì tò mò: overhead và dữ liệu object graph có thể nhạy cảm.
6. Không lưu recording vào thư mục Git repository nếu chính sách không cho phép.
7. Trên container, chú ý volume mounts và output file size/budget.

JFR là công cụ kỹ thuật mạnh nhưng không phải tệp log vô hại để gửi công khai.

## 27. Chi phí quan sát: tránh tự tạo bottleneck

JFR được thiết kế có overhead tương đối thấp trong các chế độ phù hợp, nhưng **không zero-cost**:

- Recording profile có nhiều stack traces/samples hơn default.
- Event threshold thấp làm tăng số records và ghi file.
- Recording trên service CPU-bound có thể tạo thêm CPU/memory/I/O.
- Dump file lớn cần thời gian/disk bandwidth.
- Bật diagnostic GC logs + JFR + tracing đồng thời có thể thay đổi latency đang điều tra.
- Java custom events rất nhiều fields hoặc gọi `commit` ở vòng lặp nóng có thể gây overhead.

Nếu cần so hiệu năng ứng dụng trước/sau optimization, chạy một lần **clean baseline** và một lần có instrumentation để tìm nguyên nhân. Không so A không bật JFR với B bật `profile` rồi gán chênh lệch latency cho bản code B.

### `path-to-gc-roots` có phải mặc định luôn bật?

**Không.** Tùy chọn thu đường đến GC roots cho một số tình huống leak có overhead và có thể dừng ứng dụng đáng kể. Chỉ bật khi thật sự điều tra retention có kiểm soát và chấp nhận chi phí; không là bước nhập môn cần thiết cho mọi recording.

## 28. Một báo cáo JFR nên có những trường gì?

| Trường | Ví dụ cách ghi | Lý do |
| --- | --- | --- |
| JDK | `OpenJDK 17.0.x vendor/build` | Event schema/implementation khác version |
| JVM instance | PID/start time/container ID | Tránh nhầm records giữa replicas |
| Recording | `profile, 60s, start/end timestamps` | Biết coverage/thresholds |
| Event settings | `JavaMonitorEnter threshold 10ms` | Giải thích events bị lọc |
| Workload | `200 req/s, payload distribution X` | Reproduce triệu chứng |
| GC | Young/mixed/full count, p95 pause | Chẩn đoán memory pressure |
| Allocations | Call stacks sampled nhiều, rate estimation | Tìm vị trí phân bổ có thể quan trọng |
| Threads | Block/park/sleep top stacks + durations | Tách CPU-bound và waiting |
| Request tracing | p95/p99, correlation IDs | Liên hệ sự kiện JVM với người dùng |
| Resources | CPU, RSS/cgroup, throttling | Tránh đổ nhầm vấn đề cho GC |
| Validity | DataLoss, sampling caveats, missing stacks | Không overclaim |
| Action | Thay đổi đề xuất và kế hoạch A/B | Dựa kết luận trên bằng chứng |

Không đưa một “điểm JFR score” chung cho mọi dự án: mục tiêu là trả lời câu hỏi cụ thể bằng các event types đủ tin cậy.

## 29. Những hiểu lầm thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “JFR là GC log với giao diện đẹp” | JFR chứa đa dạng CPU/allocations/threads/I/O/VM events |
| “JFR đếm mọi object allocation” | Nhiều allocation events là **sampled/thresholded** |
| “jfr summary không có Event X nên X không xảy ra” | Có thể event disabled/bị lọc/không sample/data loss |
| “Một allocation sample = một object duy nhất” | Sampling không phải per-object complete trace |
| “Heap used giảm nghĩa allocation rate giảm” | GC occupancy snapshot khác allocated bytes/time |
| “Mọi GC duration là STW” | Concurrent phases có duration nhưng không stop toàn ứng dụng |
| “GC pause 10ms giải thích request chậm 2s” | Phải đối chiếu tổng timeline và các contributors |
| “`ThreadPark` nghĩa deadlock” | Park thường là thread pool idle/await |
| “Sleep thả monitor” | `Thread.sleep` không tự release intrinsic locks |
| “`JavaMonitorEnter` bao phủ ReentrantLock contention” | AQS/LockSupport thường đi qua park, không monitorenter |
| “`ExecutionSample` = method invocation count” | Là stack samples, không phải số lần gọi |
| “Profile recording có đúng toàn bộ event names” | Phải kiểm tra settings và version |
| “Tệp JFR text, dùng cat đọc là đủ” | Binary format; dùng `jfr`/JMC/API |
| “JMC luôn đi kèm JDK 17” | JMC thường cài riêng |
| “JDK 17 có `jfr view` giống JDK mới nhất” | Dùng `jfr print/summary/metadata` của đúng JDK 17 |
| “JFR.start và JFR.dump là một thao tác” | Start ghi recording; dump xuất snapshot; stop kết thúc |
| “Dump sẽ tự stop mọi recording” | `JFR.dump` không mặc định stop recording |
| “JFR đọc được root cause mọi latency” | Cần traces, GC logs, thread dumps, DB/OS metrics |
| “Bật profile 24/7 không overhead” | Profile có overhead và data retention cost |
| “Không có JFR stack tức method không tồn tại” | Stack traces có thể tắt/cắt/không sample |
| “Cứ thu path-to-gc-roots là leak được chữa” | Đây là dữ liệu chẩn đoán tốn kém, không sửa retention |
| “JFR file chia sẻ công khai vô hại” | Có thể chứa metadata và custom fields nhạy cảm |

## 30. Bài tập và câu hỏi tự kiểm tra

**Câu 1.** Một `.jfr` không có `jdk.ObjectAllocationSample`. Có thể nói Java không allocate?

**Không.** Event có thể disabled, không được sample hoặc không có trong cửa sổ recording.

**Câu 2.** `jfr summary` và `jfr print` khác nhau thế nào?

`summary` tổng hợp event counts/file metadata; `print` hiển thị từng event records và fields.

**Câu 3.** `JFR.dump` có stop recording?

**Không mặc định.** Dump ghi snapshot; stop là thao tác riêng.

**Câu 4.** `profile` luôn tốt hơn `default` cho service chạy liên tục?

**Không.** Profile có overhead/event volume cao hơn; cần chọn theo mục tiêu và budget.

**Câu 5.** GC cycle kéo dài 200ms nghĩa request dừng 200ms?

**Không.** Phải phân biệt STW pause với concurrent phases.

**Câu 6.** Thread park lâu có chắc là bottleneck?

**Không.** Có thể worker idle hợp lệ; cần xem request queues và activity có liên quan.

**Câu 7.** Khi nào `jdk.JavaMonitorEnter` hữu ích?

Khi nghi **intrinsic monitor contention**, biết event được bật và đủ threshold/stack data.

**Câu 8.** `ReentrantLock.lock()` bị chặn có chắc có monitor event?

**Không.** Contention của explicit locks thường quan sát qua `ThreadPark`/AQS stacks và metrics.

**Câu 9.** Custom `demo.Phase` giúp gì?

Đặt mốc nghiệp vụ trong cùng recording để correlation allocations/GC/thread activity.

**Câu 10.** Một CPU sample cho method A có nghĩa A dùng toàn bộ CPU thời điểm đó?

**Không.** Chỉ là một stack sample; cần nhiều samples và context.

**Câu 11.** Khi khởi động app chỉ sống 2 giây, nên attach `jcmd` sau 5 giây không?

**Không.** Bật startup recording bằng `-XX:StartFlightRecording` để không bỏ lỡ window.

**Câu 12.** Muốn chứng minh memory leak từ JFR thì nên làm gì?

Theo dõi after-GC occupancy/retention trends, nếu cần dùng heap dump/GC roots path với quy trình kiểm soát; một allocation sample đơn lẻ không đủ.

**Câu 13.** Vì sao output `markerEvents=3` trong demo không phải một cam kết mọi recording đều đủ?

Event settings/data loss/runtime/file errors có thể ảnh hưởng; chương trình commit ba markers, nhưng file cần kiểm tra thực tế.

**Câu 14.** Muốn xem fields có trong event `jdk.GarbageCollection` của recording, dùng gì?

`jfr metadata file.jfr` hoặc `jdk.jfr.consumer.RecordedEvent`, không tự đoán field schema.

**Câu 15.** Trước khi gửi file `.jfr` cho đồng nghiệp bên ngoài, cần làm gì?

Review sensitive metadata/custom fields, giới hạn quyền truy cập và retention policy.

## 31. Nguồn tham khảo chính thức dành cho Java 17

- [Oracle Java 17 — `jcmd` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `JFR.start`, `JFR.check`, `JFR.dump`, `JFR.stop` và parameters.
- [Oracle Java 17 — `java` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-XX:StartFlightRecording`, JFR startup settings.
- [Oracle Java 17 — `jfr` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html): `print`, `summary`, `metadata`, event filters và `--json`.
- [Oracle Java 17 — Flight Recorder API Programmer's Guide](https://docs.oracle.com/en/java/javase/17/jfapi/creating-and-recording-your-first-event.html): tạo/commit custom JFR event trong Java.
- [Java SE 17 — `jdk.jfr` module](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html): `Recording`, `Event`, `Configuration` và JFR APIs.
- [Java SE 17 — `Recording` API](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/Recording.html): start/stop/dump, settings, destination, disk policy.
- [Java SE 17 — `jdk.jfr.consumer` package](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/package-summary.html): `RecordingFile`, `RecordedEvent`, streaming records.
- [Oracle Java 17 — JFR troubleshooting guide](https://docs.oracle.com/en/java/javase/17/troubleshoot/index.html): dùng JFR để điều tra CPU, allocations, garbage collection và thread contention.
- [Dev.java — Configuring JDK Flight Recorder](https://dev.java/learn/jvm/monitoring-troubleshooting/jfr/configure/): recording profiles, event thresholds/sampling, JFR configuration.
- [OpenJDK JEP 328 — Flight Recorder](https://openjdk.org/jeps/328): JFR tích hợp trong OpenJDK, kiến trúc và mục tiêu monitoring.
- [JDK Mission Control (JMC)](https://www.oracle.com/java/technologies/jdk-mission-control.html): công cụ phân tích recordings với timeline/threads/GC/allocations.
- [Oracle Java 17 — Garbage Collection Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/index.html): hiểu GC pause và các collector, bổ sung cho JFR.
- [Oracle Java 17 — `Thread`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html), [`LockSupport`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/LockSupport.html): phân biệt sleep, park, monitor wait.
- [OpenJDK JEP 158 — Unified JVM Logging](https://openjdk.org/jeps/158): cấu trúc `-Xlog` bổ sung khi đối chiếu GC logs với JFR.

**Ghi nhớ:** Với JFR ở mức nhập môn, đừng cố tối ưu ứng dụng ngay từ một biểu đồ. **Thu recording đúng thời gian**, kiểm tra **events/settings/thresholds**, phân biệt **allocation samples với tổng allocations**, **GC pause với concurrent GC**, và **monitor wait/park/sleep với CPU work**. Dùng **custom application markers và request tracing** để ghép các dữ liệu trên cùng timeline. Sau đó mới đặt giả thuyết, đối chiếu GC logs/thread dumps/OS metrics và đo lại dưới workload kiểm soát. Một event không được ghi **không chứng minh hoạt động không xảy ra**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-006">Native Memory Tracking: nhóm bộ nhớ, baseline, so sánh thay đổi và phạm vi theo dõi</a>

<details>
<summary>Xem chi tiết</summary>

# Native Memory Tracking: nhóm bộ nhớ, baseline, so sánh thay đổi và phạm vi theo dõi

## 1. Tổng quan: Vì sao Java heap không giải thích được toàn bộ RAM?

Một tiến trình Java có thể bị container **OOMKilled** hoặc có **resident set size (RSS)** tăng liên tục, trong khi biểu đồ Java heap chỉ ở mức ổn định. Đây không nhất thiết là lỗi của Garbage Collector (GC): ngoài managed heap, HotSpot JVM còn sử dụng bộ nhớ cho **class metadata, thread stacks, JIT code cache, compiler arenas, GC data structures, VM internals**, và ứng dụng có thể cấp phát bộ nhớ native qua JNI hoặc thư viện bên ngoài.

**Native Memory Tracking (NMT)** là cơ chế chẩn đoán của **HotSpot JVM** để quan sát những vùng bộ nhớ mà HotSpot quản lý/theo dõi. Công cụ **jcmd** lấy báo cáo tổng hợp theo subsystem hoặc mức chi tiết theo call site; nó còn hỗ trợ đặt **baseline** và xem mức thay đổi **summary.diff/detail.diff** qua thời gian.

Điều tối quan trọng: **NMT không phải trình đo toàn bộ RAM của process.** Theo tài liệu Oracle Java SE 17, nó tập trung vào **HotSpot internal memory allocations**, không theo dõi đầy đủ **third-party native code/JNI allocations**, và không bao quát toàn bộ cấp phát của **JDK class libraries** hoặc CDS archive. NMT có thể báo reserved/committed cho **Java Heap**, nhưng **không phải Java heap profiler**, không liệt kê object graph và không thay thế OS RSS/PSS measurements.

### Mục tiêu bài học

Sau bài này, bạn sẽ có khả năng:

1. Trình bày NMT là gì, điều kiện kích hoạt và ba chế độ **off/summary/detail**.
2. Đọc những nhóm báo cáo thường gặp: **Java Heap, Class, Thread, Code, GC, Compiler, Internal, Symbol, Native Memory Tracking, Arena Chunk** và các nhóm phụ thuộc JVM.
3. Phân biệt **reserved, committed, used, malloc, mmap, arena, tracking overhead** và physical RSS.
4. Thiết lập NMT trước khi process bắt đầu, xác minh bằng **jcmd VM.native_memory**.
5. Tạo **baseline** tại một giai đoạn thích hợp, sau đó dùng **summary.diff** hay **detail.diff** để so với chính baseline.
6. Hiểu trường hợp tăng trưởng tạm thời, retention hữu ích, fragmentation, virtual address reservation hoặc native leak đáng điều tra.
7. Biết NMT **nhìn thấy gì và không nhìn thấy gì**, đặc biệt JNI/native libraries, direct buffers, memory-mapped files và OS page accounting.
8. Ghép NMT với **GC.heap_info, VM.metaspace, thread dumps, code cache, process RSS, cgroup memory** và class-loader diagnostics.
9. Thực hành một thí nghiệm Java 17+ chạy đủ lâu để dùng jcmd, hiểu sự khác biệt giữa **Java stdout xác định** và **NMT output thay đổi theo môi trường**.
10. Lưu baseline/time-series/cấu hình để kết luận có thể tái lập mà không vội coi mọi committed increase là memory leak.

**Phạm vi:** Java 17+ với trọng tâm **HotSpot JDK 17**. NMT là chức năng HotSpot implementation, không được Java Language Specification hay JVM Specification yêu cầu ở mọi JVM. Những categories, counters, subcategories, CLI option và cách trình bày có thể thay đổi theo phiên bản/vendor/build/OS; luôn xác minh từ JVM đang chạy.

## 2. Bản đồ bộ nhớ của một Java process

~~~text
                    Java process virtual address space
 +------------------------------------------------------------------+
 | Java Heap                                                        |
 |   - managed Java objects, arrays                                 |
 |   - virtual reservation / committed heap pages                   |
 |                                                                  |
 | HotSpot VM native subsystems                                     |
 |   - Metaspace, compressed class space (Class)                    |
 |   - per-thread stacks / thread structures (Thread)              |
 |   - JIT compiled code and stubs (Code)                           |
 |   - collector structures and buffers (GC)                       |
 |   - JIT compiler temporary arenas (Compiler)                    |
 |   - symbols, internals, native metadata, tracking bookkeeping    |
 |                                                                  |
 | Native memory not fully covered by NMT                            |
 |   - user JNI/native libraries, other allocators                  |
 |   - JDK library allocations that NMT doesn't instrument          |
 |   - native direct/mapped files depending on allocation pathway   |
 |                                                                  |
 | Mappings, shared libraries, file-backed pages, OS accounting     |
 +------------------------------------------------------------------+
                  |
                  v
          Operating system / cgroup
          RSS, PSS, working set, mapped pages
~~~

**NMT không tương đương /proc/<PID>/smaps, ps RSS hay container memory.current**: chúng đo những góc nhìn khác nhau.

### 2.1. Phân biệt bốn câu hỏi

| Câu hỏi | Công cụ phù hợp |
| --- | --- |
| Java objects nào đang giữ heap? | Heap dump, JFR allocations, heap profiler, GC.heap_info |
| HotSpot đã reserve/commit bao nhiêu cho subsystem nào? | **NMT summary/detail** |
| Một process hiện dùng bao nhiêu resident memory theo OS? | ps/top, /proc/<PID>/status, smaps/smaps_rollup, container metrics |
| JNI/native library đang malloc/mmap ở đâu? | Native profiler/allocator stats/OS mapping tools; NMT chỉ giúp loại trừ một phần VM |

Một kết luận đúng thường kết hợp **ít nhất hai nguồn**. Ví dụ NMT cho thấy HotSpot committed ổn định còn RSS tăng, có thể phải xem native code ngoài phạm vi NMT, mapped files, allocator behavior hoặc OS accounting; chưa thể khẳng định “JVM leak”.

## 3. Chế độ NMT và thời điểm kích hoạt

NMT mặc định **off** trong HotSpot JDK 17. Bạn chọn chế độ ngay ở JVM startup bằng **-XX:NativeMemoryTracking=...**:

| Chế độ | JVM flag | Dữ liệu chính | Chi phí và công dụng |
| --- | --- | --- | --- |
| **off** | -XX:NativeMemoryTracking=off | Không theo dõi qua NMT | Mặc định; không thu NMT reports |
| **summary** | -XX:NativeMemoryTracking=summary | Tổng hợp theo subsystem/category | Đủ cho bước đầu xác định category nào tăng |
| **detail** | -XX:NativeMemoryTracking=detail | Category + virtual memory map/call sites khi được hỗ trợ | Hữu ích khi summary chưa đủ, có overhead lớn hơn/tốn thêm memory |

Ví dụ:

~~~bash
java -XX:NativeMemoryTracking=summary -Xms128m -Xmx512m MyMain
~~~

Hoặc:

~~~bash
java -XX:NativeMemoryTracking=detail -Xms128m -Xmx512m MyMain
~~~

**MyMain** phải là một Java main class thực trên classpath. Nếu chạy JAR dùng **-jar app.jar** thay vì tên class.

### 3.1. Không thể bật NMT cho JVM đã khởi động mà trước đó tắt

Theo Oracle JDK 17:

- NMT được kích hoạt qua launch flag.
- **jcmd VM.native_memory shutdown** có thể **tắt tracking** trong process hiện tại.
- Sau shutdown, **không thể bật lại hay khởi động lại NMT bằng jcmd** trong cùng JVM.
- Nếu JVM chạy từ đầu với NMT off, việc dùng jcmd không tự tạo được lịch sử cấp phát mà NMT chưa thu thập.

Không nói “chỉ cần jcmd VM.native_memory baseline là bật NMT”: **baseline chỉ lấy mốc so sánh của tracking đã được bật**.

Nếu production cần điều tra lâu dài, lập kế hoạch khởi động với summary/detail phù hợp và xác định thời gian/overhead chấp nhận được; không bật bừa trên mọi node.

### 3.2. Overhead là một phần của thí nghiệm

Tài liệu Oracle Java 17 cảnh báo bật NMT có thể gây **khoảng 5–10% performance overhead**, tùy workload/mode/VM. Đây là mức cảnh báo tham khảo, **không phải con số cố định cho mọi JVM**.

NMT còn tự dùng native memory để lưu metadata theo dõi (**tracking overhead**). So sánh A/B performance mà baseline không bật NMT, candidate bật detail NMT là một thí nghiệm bị nhiễu.

Khuyến nghị đo:

- Thử NMT summary trước nếu chỉ cần attribution cấp subsystem.
- Chọn detail nếu cần call-site breakdown.
- Dùng cùng NMT mode cho hai nhánh của một benchmark công bằng.
- Ghi rõ trạng thái NMT vào experiment manifest.
- Tắt NMT với shutdown chỉ khi đã hoàn tất quan sát; biết rằng không thể bật lại trong process ấy.

## 4. Cú pháp jcmd VM.native_memory

Dùng JDK tooling tương thích với tiến trình:

~~~bash
jcmd
jcmd <PID> help
jcmd <PID> help VM.native_memory
~~~

Thay **<PID>** bằng process ID JVM đang sống. Trong containers, chú ý user permissions/PID namespace/attach restrictions. Trên HotSpot Java 17, các lệnh cơ bản:

~~~bash
jcmd <PID> VM.native_memory summary
jcmd <PID> VM.native_memory detail
jcmd <PID> VM.native_memory baseline
jcmd <PID> VM.native_memory summary.diff
jcmd <PID> VM.native_memory detail.diff
jcmd <PID> VM.native_memory summary scale=MB
jcmd <PID> VM.native_memory shutdown
~~~

Ý nghĩa:

- **summary:** bản chụp cấp category tại thời điểm gọi.
- **detail:** bổ sung breakdown theo call sites và virtual memory mapping, cần tracking level detail.
- **baseline:** lưu một snapshot/mốc hiện tại cho so sánh tương đối.
- **summary.diff:** so tổng category hiện tại với baseline gần nhất đã được đặt.
- **detail.diff:** so call-site/detail report với baseline khi chạy detail mode.
- **scale=KB|MB|GB:** điều chỉnh cách hiển thị units, không làm JVM cấp phát ít hơn.
- **shutdown:** dừng tracking, không phải tắt process JVM.

Không gọi **jcmd VM.native_memory shutdown** để “free toàn bộ Java heap/native memory” — đó là tắt NMT subsystem, **không phải một thao tác GC hay cleanup tổng quát**.

### 4.1. Dùng option scale

~~~bash
jcmd <PID> VM.native_memory summary scale=MB
~~~

Dễ đọc báo cáo lớn nhưng việc đổi đơn vị sang MB có thể làm những thay đổi nhỏ bị làm tròn. Khi theo dõi leaks nhỏ, dùng **scale=KB** hoặc giữ full units.

### 4.2. Khi JVM nói NMT chưa bật

Nếu process khởi động không có NativeMemoryTracking=summary/detail, jcmd sẽ không cung cấp NMT category data đúng như mong muốn. Không cố “sửa” bằng baseline hoặc tạo ra giả định. Thay đổi launch config rồi **restart process** theo vận hành phù hợp.

### 4.3. Khác nhau giữa jcmd output và file log

jcmd in textual report ra standard output của terminal đang attach. Nếu cần lịch sử, người vận hành có thể redirect output sang file bằng shell. Ví dụ Bash:

~~~bash
jcmd <PID> VM.native_memory summary scale=KB > nmt-01.txt
~~~

Lệnh trên tạo report riêng chứ **không tự tạo time series trong JVM**. Hãy gắn thời gian/tên run ID ngoài tool và xử lý thông tin nhạy cảm/metadata khi chia sẻ.

## 5. Đọc NMT summary: top-level

Một report HotSpot NMT thường có cấu trúc gần như:

~~~text
Native Memory Tracking:

Total: reserved=...KB, committed=...KB

- Java Heap (reserved=...KB, committed=...KB)
- Class (reserved=...KB, committed=...KB)
- Thread (reserved=...KB, committed=...KB)
- Code (reserved=...KB, committed=...KB)
- GC (reserved=...KB, committed=...KB)
- Compiler (reserved=...KB, committed=...KB)
- Internal (reserved=...KB, committed=...KB)
- Symbol (reserved=...KB, committed=...KB)
- Native Memory Tracking (reserved=...KB, committed=...KB)
...
~~~

Đây là **sơ đồ minh họa**, không phải output cố định của JDK 17; tên/số categories thay đổi theo GC/JDK/platform và những subsystems đã khởi tạo.

### 5.1. Reserved: không gian địa chỉ được dành

**Reserved** thường có ý nghĩa vùng virtual address space dành trước. Ví dụ JVM dự trữ heap 2 GiB không có nghĩa OS đang cấp đủ 2 GiB RAM vật lý. Reserved memory có thể lớn hơn committed và lớn hơn mức RAM trong container, tùy memory layout/OS overcommit.

### 5.2. Committed: pages/resources đã commit ở mức VM/OS

**Committed** ghi nhận dung lượng được HotSpot commit/allocate theo allocator/mapping accounting. Nó gần hơn với memory obligation nhưng **không bằng RSS**: committed pages có thể chưa touched, có thể được swap, shared mappings có accounting khác, malloc arenas có fragmentation.

Không suy total committed NMT là exact memory.current của cgroup hoặc một hard cap bằng RSS.

### 5.3. Used: không phải luôn có trên mọi category

Một số subcategories như **Metaspace** có thể in **used**, **free**, **waste**. Used theo allocator/VM meaning khác với:

- Java heap occupancy.
- NMT committed.
- OS resident bytes.
- Application object graph reachability.

Không tự suy từ “Class committed 50 MiB” rằng có đúng 50 MiB class metadata thực sự có ý nghĩa đang được dùng.

## 6. Những nhóm bộ nhớ thường gặp

### 6.1. Java Heap

NMT cho thấy **heap reservation/commit** do HotSpot quản lý, ví dụ:

~~~text
- Java Heap (reserved=1048576KB, committed=524288KB)
    (mmap: reserved=1048576KB, committed=524288KB)
~~~

**Số liệu hoàn toàn minh họa.** Đó không phải “Java objects đang dùng 524288 KB”; actual heap **used** cần GC.heap_info, GC logs, memory MXBeans, heap dump/profilers.

Nếu ứng dụng giữ nguyên Xmx=1GiB, **reserved có thể cố định**, trong khi **committed tăng** khi JVM mở rộng heap — đây có thể là behavior hợp lệ. Một leak trong Java heap không nhất thiết phản ánh thành một reserved increase vì heap đã reserve từ đầu.

### 6.2. Class

Có thể bao gồm:

- Metaspace metadata allocations.
- Compressed class space reservation/commit trong cấu hình phù hợp.
- Class-related VM accounting.
- Class counts ở một số report formats.

NMT Class increase có thể do **class loading/proxy generation/lambda generated classes**, hoặc classloader retention. Nhưng Class reserved lớn (như compressed class space virtual reservation) không có nghĩa physical RAM tương ứng đã được dùng.

Điều tra chi tiết hơn:

~~~bash
jcmd <PID> VM.metaspace
jcmd <PID> VM.classloader_stats
~~~

Các options/commands phụ thuộc HotSpot build. **Class-loader leak** đòi phân tích loaders và reachability/retention, không chỉ một dòng Class committed tăng.

### 6.3. Thread

NMT Thread có thể gồm thread structures, native stacks và một số allocations liên quan. Nhiều báo cáo in thread count, stack reservation/commit.

Những điều cần hiểu:

- Thêm threads có thể làm **reserved stacks** tăng đáng kể.
- **-Xss** ảnh hưởng stack sizing/budget theo JVM/OS.
- Thread stack reserved thường không đồng nghĩa full stack size đã bị touch vào RSS.
- Java platform thread và JVM service/compiler/GC threads đều góp phần tài nguyên; chi tiết category phụ thuộc implementation.
- Khi threads kết thúc, reservations có thể được giải phóng theo lifecycle/allocator; committed không nhất thiết giảm đúng một lượng như bạn cộng thủ công.

Kết hợp:

~~~bash
jcmd <PID> Thread.print
~~~

với thread count/application executor metrics. **Thread state WAITING** không có nghĩa stack memory leak; nhiều idle pool threads vẫn giữ native stacks/structures.

### 6.4. Code

Code category liên quan HotSpot **JIT code cache**, compiled native instructions, stubs và bookkeeping phù hợp. Để xem occupancy riêng code cache:

~~~bash
jcmd <PID> Compiler.codecache
~~~

Không nhầm:

- **Code reserved** có thể là virtual address budget.
- **Code committed** là trang đã commit theo VM.
- **Compiled code used** theo code-cache allocator có thể nhỏ hơn Code committed.
- Code heap full có thể giới hạn JIT compilation dù Java heap chưa đầy.

### 6.5. GC

GC category có thể gồm card tables, remembered sets, marking bitmaps, region tables, forwarding/collector-specific structures và allocations được HotSpot đánh dấu thuộc collector. Nó phụ thuộc mạnh vào GC algorithm, heap size và CPU/platform.

Nếu đổi **G1 ↔ ZGC**, sự thay đổi GC category không tự chứng minh “leak”: **đó là đổi thuật toán**, không phải cùng một process cùng setup.

Hãy kiểm tra collector, Java heap reserved/committed, GC logs và từng phase/memory policy.

### 6.6. Compiler

Compiler category bao gồm những allocation nội bộ cho JIT compilation (C1/C2 compiler arenas/temp structures theo build), **không đồng nhất với Code category** chứa generated executable code.

JIT có thể tạo **temporary native memory spikes** trong warm-up và giảm về sau. Nếu Compiler committed tăng trong lúc compilation queue lớn, kiểm tra:

~~~bash
jcmd <PID> Compiler.queue
jcmd <PID> Compiler.codecache
~~~

Không kết luận leak chỉ từ một Compiler diff dương trong đoạn workload đang warm-up.

### 6.7. Internal, Symbol và các nhóm VM khác

Các category có thể chứa:

- **Internal:** allocations nội bộ HotSpot không được gom vào subsystem riêng hơn theo build.
- **Symbol:** symbol tables, interned names và structures tương ứng.
- **Arena Chunk:** memory allocator/arena chunks được quản lý, đôi khi tái sử dụng.
- **Native Memory Tracking:** tracking metadata/overhead của chính NMT.
- Những nhóm như **Shared class space**, **Other**, **Serviceability**, **String Deduplication**, **Module**, **Safepoint**, **Synchronization** có thể xuất hiện tùy JDK/build/feature use.

**Đừng tạo danh sách bắt buộc** rằng mọi JDK 17 phải có đúng 10 categories và cùng thứ tự. Đọc report thực tế, giải nghĩa trong ngữ cảnh version, và phân tích groups có thay đổi đáng kể.

### 6.8. Bảng diễn giải nhanh

| Category | Tín hiệu tăng có thể là | Cần kiểm chứng thêm |
| --- | --- | --- |
| **Java Heap** | Heap commit/resizing | Heap used/live set, GC logs |
| **Class** | Class loading, Metaspace commit | Class-loader stats, VM.metaspace |
| **Thread** | Nhiều platform/VM threads, stack reservation | Thread dumps, thread lifecycle, -Xss |
| **Code** | JIT code cache commit | Compiler.codecache, compilation logs |
| **GC** | Collector data structures/buffers | Collector, heap size, GC logs |
| **Compiler** | JIT temporary memory | Compiler.queue, JIT activity |
| **Internal** | VM-specific structures | Detail callsites, source/version |
| **Native Memory Tracking** | NMT bookkeeping | Tracking mode/overhead |
| **Arena Chunk** | Arena pools/chunks | Lifecycle/peak vs retained, detail report |

## 7. malloc, mmap, arena và accounting

NMT summary thường có breakdown phụ như:

~~~text
- Example Group (reserved=...KB, committed=...KB)
    (malloc=...KB #...)
    (mmap: reserved=...KB, committed=...KB)
    (arena=...KB #...)
~~~

Đây là **pseudo-output** để giải thích labels, không phải một category tên "Example Group" của HotSpot.

### 7.1. malloc

**malloc** biểu thị memory allocations được HotSpot tracking qua malloc-related pathways. Con số **#** thường là allocation count theo format, không phải số Java objects.

Allocator có thể reserve memory từ OS theo chunks, phân mảnh, cache hoặc giữ slabs; giá trị malloc tracked và process RSS không có một phép ánh xạ 1:1 đơn giản.

### 7.2. mmap

**mmap** thể hiện virtual memory reservations/commits được tracker biết. Đây là vùng có thể lớn hơn nhiều so với số pages resident. Nếu mmap reserved tăng nhưng committed không tăng, đó có thể chỉ là address-space reservation.

### 7.3. arena

**Arena** là cơ chế cấp phát nhóm của HotSpot; allocations có thể được giải phóng/tái dùng theo lifecycle của arena, không nhất thiết theo từng object như malloc riêng lẻ.

Một temporary compiler arena spike có thể đúng với workload compilation, rồi giảm khi compilation kết thúc. Để kết luận leak cần một khoảng đo đủ dài sau warm-up/steady state.

### 7.4. NMT tracking overhead tự cộng vào footprint

NMT cần metadata ghi lại allocations và virtual memory operations. Một report có thể có dòng **Native Memory Tracking** cho chính overhead này. Bật **detail** có thể tạo chi phí nhiều hơn so với summary, nhất là workload rất nhiều allocations.

Không so memory giữa process NMT off và detail NMT on rồi quy toàn bộ delta cho ứng dụng.

## 8. Thiết lập baseline: chọn đúng mốc thời gian

**Baseline** là snapshot memory khi chạy:

~~~bash
jcmd <PID> VM.native_memory baseline
~~~

Sau đó:

~~~bash
jcmd <PID> VM.native_memory summary.diff
~~~

Hiển thị **current status so với baseline gần nhất**, không phải tổng số bytes từng được allocate từ đầu process.

### 8.1. Tại sao baseline nên đặt sau warm-up khi điều tra leak?

Trong startup, JVM sẽ tự nhiên:

- Load/verify/initialize nhiều classes.
- Commit thêm Java heap pages.
- Tạo compiler/GC/service threads.
- Compile hot methods vào code cache.
- Khởi tạo runtime metadata và các caches hợp lệ.

NMT summary/diff tăng mạnh trong giai đoạn này **không tự chứng minh leak**. Nếu mục tiêu điều tra tăng trưởng **steady-state**, nên chờ warm-up tương đối ổn định rồi baseline.

Ngược lại, nếu nghiên cứu **startup/native memory peak**, đặt baseline sớm hoặc thu snapshots xuyên startup theo protocol riêng. **Không có quy tắc baseline sau warm-up cho mọi câu hỏi.**

### 8.2. Mỗi lần baseline tạo một mốc mới

Ví dụ:

~~~text
T0: JVM start
T1: warm-up complete
T2: jcmd PID VM.native_memory baseline    <- mốc A
T3: jcmd PID VM.native_memory summary.diff  <- so với A
T4: jcmd PID VM.native_memory summary.diff  <- vẫn so với A
T5: jcmd PID VM.native_memory baseline     <- mốc B mới
T6: jcmd PID VM.native_memory summary.diff  <- so với B, không còn là A
~~~

Nếu vô ý gọi baseline thêm lần nữa, các subsequent diffs **đổi điểm tham chiếu**. Hãy lưu timestamp, reason và snapshot report trước khi rebasing.

### 8.3. Baseline không phải một file tự động

jcmd baseline lưu snapshot trong JVM để sau đó diff. Muốn archive phục vụ review, tự lưu:

- Current summary/detail trước baseline (nếu cần).
- Timestamp/tên milestone.
- Baseline command và phản hồi.
- Các summary.diff/detail.diff tiếp theo.
- Raw reports kèm cùng JDK/vendor/flags/workload.
- OS RSS/cgroup metrics cùng timestamps.

Không dùng một file text cũ như “baseline bên trong JVM mới” — **NMT baseline là cơ chế trong tiến trình**, không có API jcmd chuẩn để nạp snapshot của process khác vào baseline.

## 9. So sánh thay đổi: summary.diff và detail.diff

### 9.1. Summary diff

~~~bash
jcmd <PID> VM.native_memory summary.diff scale=KB
~~~

Giúp trả lời:

- Category nào đang giữ thêm committed/reserved memory so với baseline?
- Java Heap committed tăng hay ổn định?
- Class/Thread/Code/GC/Compiler tăng theo phase nào?
- NMT total committed delta có đi cùng RSS trend?
- Có groups âm (reclaimed/decommitted) không?

**Không** đủ trả lời:

- Java class instance nào giữ heap.
- Cụ thể native call site nào nếu chỉ chạy summary.
- JNI allocations bên ngoài HotSpot tăng bao nhiêu.
- Tăng trưởng tương lai có chắc chắn tiếp tục không.
- Mỗi byte allocated trong thời gian từ baseline tới hiện tại.

### 9.2. Detail diff

Nếu JVM được khởi động với **-XX:NativeMemoryTracking=detail**:

~~~bash
jcmd <PID> VM.native_memory baseline
jcmd <PID> VM.native_memory detail.diff scale=KB
~~~

Có thể cung cấp attribution theo call site và virtual memory mapping. Theo mô tả jcmd JDK17, detail report về call-site allocations theo granularity/reporting của tool (ví dụ allocations ≥1K cho phần call-site thống kê).

**Detail không phải native stacktrace của mọi malloc đã từng xảy ra**, không thay heap profiler, không bảo đảm symbol names rõ ràng nếu build stripped symbols, và không đo runtime CPU cost của allocator.

### 9.3. Dấu âm trong diff

Nếu committed delta là âm, có thể do VM decommit/free resources (ví dụ threads kết thúc, heap shrinking, compilation arenas freed), hoặc do allocator categories thay đổi. Không suy “âm thì chắc memory leak đã được chữa hết” vì còn allocations ngoài NMT hoặc objects còn giữ.

### 9.4. Net delta khác cumulative allocation rate

Giả sử từ baseline có:

~~~text
Allocate 100 MiB
Free     90 MiB
Current net growth = +10 MiB
~~~

summary.diff chỉ thể hiện **net memory state change** cho categories tương ứng, không báo lượng cumulative 100 MiB hoặc allocator churn trong cả interval. Muốn đo allocation rate/churn, cần profiler/JFR/instrumentation phù hợp.

## 10. Ví dụ đọc một diff giả định

Giả sử NMT ghi:

~~~text
Total committed: 500000KB +40000KB

- Java Heap: committed=350000KB +24576KB
- Class:     committed=22000KB  +4096KB
- Thread:    committed=12000KB  +2048KB
- Code:      committed=30000KB  +2048KB
- GC:        committed=30000KB  +5120KB
- Other categories: remaining difference
~~~

**Đây là dữ liệu giả định để học diễn giải**, không phải actual jcmd output và không bảo đảm numbers/subtotal matches exact generated report.

Điều có thể nói:

1. Tổng HotSpot tracked committed tăng khoảng 40.000 KB so với baseline.
2. Một phần đáng kể liên quan Java Heap commit thêm pages.
3. Class/Thread/Code/GC cũng tăng ở các mức khác.
4. Cần xem actual timestamps, workload, GC class loading/threads and OS RSS.

Điều **không** được nói:

- Tất cả 40.000 KB là memory leak.
- RSS chắc chắn tăng đúng 40.000 KB.
- Có 24.576 KB Java objects mới còn live.
- Thread stack tăng do đúng một số thread cố định nếu chưa đọc thread count.
- Third-party native code không cấp phát thêm vì NMT không ghi.
- G1 là nguyên nhân chỉ vì GC group tăng; có thể tăng theo heap sizing.

### 10.1. Đánh giá tỷ lệ thay đổi có đơn vị

Có thể dùng:

~~~text
baseline committed = 40,960 KiB
current committed  = 53,248 KiB

delta = 53,248 - 40,960 = 12,288 KiB
delta percent = 12,288 / 40,960 * 100 = 30.00%
~~~

Bài toán này đo **tăng trưởng tương đối của một chỉ số cụ thể**; nếu đó là Code committed hay Thread committed, cách chẩn đoán root cause khác nhau.

## 11. Thí nghiệm Java 17+ số 1: giữ JVM sống để quan sát NMT

Tạo **NmtPhasesDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class NmtPhasesDemo {
    private static final int WORKERS = 12;
    private static final long BASELINE_WINDOW_MS = 15_000L;
    private static final long WORKER_WINDOW_MS = 25_000L;

    public static void main(String[] args)
            throws InterruptedException {
        System.out.println("phase=baseline-window");
        Thread.sleep(BASELINE_WINDOW_MS);

        CountDownLatch ready = new CountDownLatch(WORKERS);
        CountDownLatch release = new CountDownLatch(1);
        Thread[] threads = new Thread[WORKERS];

        for (int i = 0; i < WORKERS; i++) {
            threads[i] = new Thread(() -> {
                ready.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }, "demo-worker-" + i);
            threads[i].start();
        }

        ready.await();
        System.out.println("phase=workers-active");
        Thread.sleep(WORKER_WINDOW_MS);

        release.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("phase=workers-released");
        System.out.println("workers=" + WORKERS);
    }
}
~~~

Biên dịch bằng Java 17:

~~~bash
javac --release 17 -encoding UTF-8 NmtPhasesDemo.java
~~~

Chạy với summary:

~~~bash
java -XX:NativeMemoryTracking=summary NmtPhasesDemo
~~~

**Stdout của chính chương trình, nếu không bị interrupt và hoàn thành:**

~~~text
phase=baseline-window
phase=workers-active
phase=workers-released
workers=12
~~~

Không có output NMT trong khối stdout trên vì jcmd được chạy ở terminal khác. Chương trình có cửa sổ khoảng 15 giây trước khi tạo 12 threads và khoảng 25 giây khi các threads vẫn đang chờ latch. **Elapsed time thực không chính xác tuyệt đối** vì OS/JVM scheduling.

### 11.1. Các lệnh jcmd tại terminal thứ hai

Trong 15 giây khi thấy **phase=baseline-window**:

~~~bash
jcmd
jcmd <PID> VM.native_memory summary scale=KB
jcmd <PID> VM.native_memory baseline
~~~

Sau khi thấy **phase=workers-active**:

~~~bash
jcmd <PID> VM.native_memory summary.diff scale=KB
jcmd <PID> Thread.print
~~~

Ở thời điểm threads active, category **Thread** có thể cho thấy thêm thread structures/stacks/reservation so với baseline. Mức tăng **không có stdout số học cố định**; thay đổi phụ thuộc -Xss, VM/OS, số HotSpot threads nền, timing và việc các stack pages thực sự được touch.

**Quan trọng:** Worker threads dùng CountDownLatch.await nên đang WAITING hợp lệ; chúng vẫn giữ các native thread resources trong khi còn sống. NMT tracking cho thấy accountings cấp VM, không phải chứng minh đang “rò rỉ” 12 stacks.

### 11.2. Sau khi workers kết thúc

Program prints **phase=workers-released** rồi hoàn tất. Muốn chụp snapshot sau khi worker threads đã kết thúc trong cùng process, có thể thêm thời gian chờ cuối trong code khi làm thí nghiệm riêng. Đừng coi một diff sau khi process đã kết thúc là có thể attach được.

### 11.3. Vì sao không dùng System.gc() thay cho NMT?

GC tác động Java objects trong heap theo JVM policies, nhưng không trực tiếp trả lời stack native reservations, compiler arenas hay JIT code cache. **System.gc() không là lệnh in NMT snapshot**; có thể tác động timing/heap behavior và làm phép thử khác đi, nên không thêm bừa.

### 11.4. Nếu trễ cửa sổ baseline

Nếu không kịp đặt baseline trước khi workers được tạo, **không tự sửa timestamps rồi ghi “baseline before workers”**. Chạy lại process với cùng command hoặc thay đổi protocol thí nghiệm có ghi nhận, bảo đảm phép so đúng giai đoạn.

## 12. Thí nghiệm Java 17+ số 2: tính delta trên dữ liệu giả định

Tạo **NmtDiffMathDemo.java**:

~~~java
import java.util.Locale;

public class NmtDiffMathDemo {
    public static void main(String[] args) {
        long baselineCommittedKiB = 40_960L;
        long currentCommittedKiB = 53_248L;

        long deltaKiB =
                currentCommittedKiB - baselineCommittedKiB;
        double changePercent =
                100.0 * deltaKiB / baselineCommittedKiB;

        System.out.println("baselineKiB="
                + baselineCommittedKiB);
        System.out.println("currentKiB="
                + currentCommittedKiB);
        System.out.println("deltaKiB=" + deltaKiB);
        System.out.printf(Locale.ROOT,
                "relativeChangePercent=%.2f%n",
                changePercent);
    }
}
~~~

~~~bash
javac --release 17 -encoding UTF-8 NmtDiffMathDemo.java
java NmtDiffMathDemo
~~~

**Stdout chính xác:**

~~~text
baselineKiB=40960
currentKiB=53248
deltaKiB=12288
relativeChangePercent=30.00
~~~

Chương trình cố tình dùng **hardcoded synthetic numbers** để giải thích phép tính. Nó **không gọi NMT**, không parse jcmd output và không phải một profiler. Với production reports, hãy ghi rõ chính xác category, metric (reserved/committed/used), đơn vị và baseline time trước khi tính delta.

### 12.1. Tại sao không tính % khi baseline=0?

Nếu baseline committed=0, phép chia % không có nghĩa hoặc dẫn tới Infinity/NaN; khi ấy báo absolute delta và mô tả “category xuất hiện sau baseline” thay vì gán một phần trăm tăng vô hạn.

### 12.2. Tổng delta và group delta

Một report có nhiều categories. **Không cộng cùng một category lần hai**: Class có sub-lines như Metadata/Class space, Java Heap có mmap sub-line. Nếu cộng top-level total + every subline, bạn double-count. Dùng top-level categories cho breakdown và những sublines chỉ để giải thích composition.

## 13. Quy trình baseline và nhiều snapshot trong một production investigation

Một quy trình có kiểm soát:

### Giai đoạn A — Xác nhận cấu hình

~~~bash
jcmd <PID> VM.version
jcmd <PID> VM.command_line
jcmd <PID> VM.flags -all
jcmd <PID> VM.native_memory summary scale=KB
~~~

Lưu runtime build và effective NativeMemoryTracking flag. Nếu off, đừng giả tạo NMT evidence; lên kế hoạch restart/canary có bật summary/detail.

### Giai đoạn B — Chọn baseline phù hợp

- Nếu điều tra **steady-state leak**: chờ ổn định startup/class loading/JIT/GC.
- Nếu điều tra **startup peak**: baseline càng sớm theo protocol càng tốt, kết hợp snapshots xuyên startup.
- Nếu điều tra **load spike**: baseline ngay trước load phase và ghi workload/CPU quota.

~~~bash
jcmd <PID> VM.native_memory baseline
~~~

Ghi timestamp ở bên ngoài và lưu sample summary tại mốc đó.

### Giai đoạn C — Quan sát biến thiên

~~~bash
jcmd <PID> VM.native_memory summary.diff scale=KB
~~~

Lặp ở nhiều mốc (ví dụ 1 phút, 5 phút, 30 phút) thích hợp với tốc độ thay đổi và operational costs. Giữ baseline nếu mục tiêu là growth since T0.

### Giai đoạn D — Phân loại

- Java Heap: còn used/leak thật hay chỉ commit?
- Class: class loaders mới, cached proxies, metaspace trend?
- Thread: số native threads có tăng, pool growth?
- Code: JIT warm-up/code cache?
- GC: collector data structures, heap resize?
- Compiler: temporary compile arenas?
- NMT tracking overhead: detail mode overhead?
- Other/native: RSS tăng nhưng NMT không giải thích?

### Giai đoạn E — Tìm nguồn

Dùng NMT detail nếu đã bật detail. Nếu đang summary, không thể kỳ vọng **detail.diff** trở lại call-site history đầy đủ mà chưa kích hoạt detail từ startup; cần chọn restart test khi cần.

Kết hợp chuyên dụng:

~~~bash
jcmd <PID> GC.heap_info
jcmd <PID> VM.metaspace
jcmd <PID> Thread.print
jcmd <PID> Compiler.codecache
~~~

**Chỉ chạy các lệnh supported bởi VM đích.** Không có một command nào thay tất cả.

### Giai đoạn F — Sửa và đo lại

Đưa ra một giả thuyết cụ thể: “thread pool tạo workers không giới hạn” hoặc “classloader leak làm Class committed tăng”. Thay một biến có kiểm soát; giữ launch NMT mode, workload và JVM build tương đương. So lại multiple time points.

### Giai đoạn G — Lưu và phục hồi

Archive:

- JVM build/vendor, process launch flags và environment.
- NMT level off/summary/detail.
- Baseline timestamp, snapshot summary/detail, diffs.
- OS RSS/smaps/cgroup memory cùng time range.
- Request/traffic/load and class/thread counts.
- GC/JIT evidence, nếu liên quan.
- Kết luận/độ tin cậy và cách rollback.

## 14. Phân biệt Java heap live set với NMT Java Heap

Giả sử:

~~~text
JVM flags: -Xms256m -Xmx1024m

NMT Java Heap:
  reserved=1048576KB
  committed=786432KB

GC.heap_info:
  used=200MiB (ví dụ)

OS RSS:
  process RSS=... (đo riêng)
~~~

**Các số là ví dụ giả định.** NMT reserved 1GiB và committed 768MiB không nói 768MiB objects còn reachable. Java heap used được collector/management API tính theo semantics riêng.

### 14.1. Java heap memory leak

Một Java collection static giữ references không giải phóng có thể làm **live set** tăng. Nếu heap đã committed tới max từ trước, NMT Java Heap committed có thể **không tăng nữa**, dù application chắc chắn đang tăng object retention.

Dùng heap dump, GC logs, JFR allocation profiling, MAT/histogram, class references. **NMT không thay các công cụ Java heap object analysis**.

### 14.2. Heap committed cao nhưng used thấp

HotSpot có thể giữ committed pages để tái sử dụng, tùy collector/flags và uncommit policy. Không tự coi đây là leak. Có thể cần xem memory pressure, collector uncommit behavior, heap minimum/maximum và quá trình warm-up.

### 14.3. Heap reserved lớn hơn RAM

Virtual address space reservation không tự tạo physical RAM tương ứng. Trên 64-bit JVM, khả năng reserve address range rất lớn là bình thường. Tác động thực cần đo **committed, touched pages, RSS và cgroup**.

## 15. NMT Class: Metaspace và compressed class space

Phần Class có thể hiển thị:

~~~text
Class:
  reserved=...KB, committed=...KB
  classes #...
  Metadata:
    reserved=...KB
    committed=...KB
    used=...KB
  Class space:
    reserved=...KB
    committed=...KB
    used=...KB
~~~

Tên/format là minh họa. **Class space reserved** có thể lớn do address reservation, không có nghĩa cùng lượng committed.

### 15.1. Điều tra Class growth

1. Kiểm tra class count, loaded/unloaded classes theo JFR/management metrics.
2. Kiểm tra class-loader count và retention.
3. Kiểm tra dynamic proxies, runtime bytecode generation, reflection/framework caching.
4. Đọc **VM.metaspace** để phân biệt committed, used, free, waste.
5. Nếu needed, classloader stats/heap dump để xác định references giữ class loaders.
6. Đo sau warm-up; class loading ban đầu là bình thường.

**Đừng hạ MaxMetaspaceSize để “chữa leak”** mà chưa hiểu số lượng class loaders và business behavior. Giới hạn quá thấp có thể gây **OutOfMemoryError: Metaspace**.

### 15.2. Một class-loader leak có thể biểu hiện thế nào?

Class metadata/Metaspace committed tăng qua nhiều redeploys, class count không giảm, GC logs/classloading events cho thấy loaders cũ còn live. NMT giúp phát hiện **subsystem tăng**, nhưng **không chỉ đích danh object tham chiếu đang giữ classloader**. Heap profiler/dump và classloader diagnostics mới cần cho root cause.

## 16. NMT Thread: stacks và nền tảng đo

Thread category có thể tăng khi:

- Executor pool khởi tạo thêm workers.
- Application tạo native/platform threads liên tục.
- GC/compiler/service threads được khởi tạo.
- Thread stacks reserve/commit thêm memory.
- Thread-related VM bookkeeping tăng.

Nếu thấy:

~~~text
Thread (reserved=...KB, committed=...KB)
    (thread #72)
    (stack: reserved=...KB, committed=...KB)
~~~

Số **#72** nếu xuất hiện thường là thread count theo report, **không phải số Java Threads đang RUNNABLE hoặc số requests**. Một số JVM service threads có thể hiện trong tổng.

### 16.1. Không suy stack committed = Xss × threadCount

**-Xss** thường liên quan stack reserve sizing, nhưng committed pages phụ thuộc stack growth/OS, guards và JVM implementation. Với 72 threads và -Xss1m, không kết luận chắc có chính xác 72MiB resident stack memory.

### 16.2. Nhiều WAITING threads có thể vẫn giữ stacks

Một pool worker ở WAITING tại BlockingQueue.take() có thể hoàn toàn idle đúng chức năng nhưng vẫn có thread structures/native stack memory. Tùy pool design, giảm số idle threads có thể giảm native footprint, nhưng nếu pool quá nhỏ có thể tăng queue latency.

Tuning cần theo SLO/throughput và memory budget, không đặt “ít threads nhất” bằng mọi giá.

### 16.3. Thread leak và legitimate pool growth

Phân biệt:

- Thread count tăng rồi đạt plateau theo pool maximum: có thể expected.
- Thread count tăng mãi theo requests/tasks: nghi thread creation leak.
- Thread count giảm sau load: có cơ chế idle timeout/shutdown.
- Virtual threads ở JDK 21+ có model khác platform thread native stacks; không áp con số JDK17 platform thread x Xss lên mọi virtual thread.

## 17. NMT Code và Compiler: chi phí JIT khác nhau

**Code category** thường liên quan native code cache chứa compiled method nmethods/stubs; **Compiler category** thường liên quan compiler temporary arenas/structures.

Ví dụ:

~~~text
Code reserved: stable near code cache maximum budget
Code committed: grows during JIT warm-up
Compiler committed: fluctuates during compilations
~~~

Hình là diễn giải, không phải report thật.

### 17.1. JIT code cache

JVM có thể reserve code-cache virtual address space từ startup và commit dần khi methods được JIT compile. Nếu app thêm methods hot do traffic/load thay đổi, Code committed có thể tăng. Điều này không tự chứng minh leak.

Dùng:

~~~bash
jcmd <PID> Compiler.codecache
jcmd <PID> Compiler.codelist
~~~

để xem actual compiled code occupancy.

### 17.2. Compiler temporary memory

Compiler CPU/temporary memory có thể tăng trong warm-up, rồi giảm khi tasks hoàn thành. Một Compiler diff tạm dương có thể expected. Nếu tăng kéo dài, cần xem compiler queue, C1/C2 activity, dynamically generated methods hoặc application class churn.

**Không dùng NMT Compiler committed để đo trực tiếp CompilerThread CPU utilization.** Dùng JFR/CPU profiling và thread CPU metrics.

### 17.3. Code cache full không phải Java heap OOME

Nếu Code category/codecache gần capacity, JVM có thể tạm dừng/giới hạn compilation, khiến throughput giảm dù Java heap vẫn khỏe. Điều tra JIT flags/codecache; tăng -Xmx không trực tiếp chữa thiếu code-cache space.

## 18. NMT GC: collector-specific allocations

GC category phụ thuộc collector:

- G1 có region/remembered-set/card-table/marking data structures.
- ZGC có metadata/buffers tùy implementation.
- Parallel/Serial collectors có data structures khác.
- Heap size, number of regions/threads và phase có thể ảnh hưởng footprint.

Không thể so NMT GC category của G1 và ZGC rồi kết luận khác biệt là “memory leak” nếu không nhận thức rằng collector khác.

### 18.1. GC pauses và native memory khác nhau

NMT GC committed tăng chưa chắc GC pauses tăng. NMT không trực tiếp cho:

- GC pause duration distribution.
- Allocation rate of Java objects.
- Full GC frequency.
- Mutator utilization.
- Application p99 latency.

Dùng **-Xlog:gc*** và JFR để bổ sung. Nếu muốn chỉ ra NMT GC growth do particular collector subcomponent, NMT detail có thể cung cấp call-site clues nhưng cần đọc source/version.

### 18.2. Xmx thay đổi có thể kéo theo NMT GC changes

Khi tăng Xmx, collector có thể reserve/commit structures tương ứng heap capacity. Vì vậy thí nghiệm “tăng heap làm GC category tăng” có thể là kết quả hợp lệ do ergonomics, không chứng minh leak.

## 19. RSS tăng, NMT không tăng: phải làm gì?

Đây là failure mode quan trọng nhất khi hiểu **scope**.

Một process có thể chứa memory ngoài NMT do:

- User JNI library gọi **malloc** trực tiếp.
- Third-party native allocators.
- JDK class library native allocations không được NMT instrument đủ.
- OS file mappings/memory mapped files.
- Shared libraries và their resident pages.
- Allocator caches/arenas có khác biệt giữa VM accounting và RSS.
- Kernel/cgroup accounting, page faults, shared/private pages, page cache và NUMA behavior.

Nếu **RSS tăng rõ** nhưng **NMT total committed ổn định**:

1. Xác nhận đang nhìn cùng PID/container và timestamps.
2. Kiểm tra NMT mode/tracking enabled và report scale.
3. Lấy Linux **/proc/<PID>/status**, **smaps_rollup**, smaps/pmap khi có quyền.
4. Xem anonymous vs file-backed/private/shared mappings.
5. Kiểm tra JNI/native libraries và direct buffer/mapped file behavior.
6. Dùng native profiling hoặc allocator instrumentation khi cần.
7. Kiểm tra container **memory.current/working set** khác process RSS như thế nào.
8. Không gán “NMT không tăng ⇒ không có native memory leak”.

### 19.1. DirectByteBuffer và memory-mapped files

Java API có **ByteBuffer.allocateDirect**, **FileChannel.map**. Những vùng native/mapped của thư viện JDK có lifecycle, cleaning và implementation accounting riêng.

**NMT không được tài liệu JDK17 bảo đảm theo dõi đầy đủ JDK class-library allocations**, nên không hứa “mọi DirectByteBuffer sẽ tăng Other category chính xác bằng capacity”. Dùng BufferPoolMXBean/JFR/native profiler/smaps phù hợp để kiểm chứng trường hợp thực tế.

### 19.2. JNI allocations

Nếu code C/C++ được load qua JNI gọi malloc mà không giải phóng, NMT HotSpot có thể **không nhìn thấy allocation đó**. Các lựa chọn:

- Native allocator tracing/profilers cho OS/toolchain.
- Kiểm tra native library lifecycle.
- Native heap profiling có kiểm soát trong staging.
- /proc/PID/smaps/heap mappings.
- AddressSanitizer/Valgrind phù hợp khi xây/test native component.
- Check native library version/configuration.

Không dùng NMT detail như bằng chứng “JNI hoàn toàn không leak”.

### 19.3. OS virtual memory mapping tools

Linux:

~~~bash
cat /proc/<PID>/status
cat /proc/<PID>/smaps_rollup
pmap -x <PID>
~~~

Cần quyền đọc tương ứng, các lệnh có thể không có trong container minimal image. Trên Windows/macOS, dùng tools tương ứng hệ điều hành. **/proc** là Linux-specific, không portable Java API.

Các chỉ số quan trọng:

- **VmRSS:** resident memory theo process accounting.
- **VmSize:** virtual address space, có thể lớn vì reservations.
- **PSS:** proportional set size trong smaps, chia sẻ pages theo tỷ lệ.
- **Anonymous/file-backed/shared/private:** giúp phân loại memory sources.
- **cgroup memory.current:** accounting ở container/cgroup, không luôn bằng tổng RSS process.

## 20. NMT tăng, RSS không tăng tương ứng: phải hiểu đúng

Tình huống ngược lại:

~~~text
NMT committed +100 MiB
OS RSS +20 MiB
~~~

Điều này không tự là mâu thuẫn hoặc bug báo cáo:

- Committed pages có thể chưa được touched thành resident.
- Pages có thể shared/mapped/OS accounted khác.
- Process memory accounting có thể có paging/rounding.
- NMT aggregated allocation counters có semantics khác RSS.
- Counters được chụp khác thời điểm.
- Allocator có thể dùng lại/reserve pages.

**Không xây công thức RSS = sum(NMT committed)**. Dùng NMT để attribution các allocations HotSpot tracked, và OS tools để physical footprint.

### 20.1. Cẩn thận với RSS và container working set

RSS đo resident memory mappings của process, còn cgroup memory.current có thể tính cả những page-cache/kernel charges không thuộc RSS cùng nghĩa. Trên container nhiều processes, thêm sidecar/helpers. Khi điều tra OOMKill cần kiểm tra **memory limit** và **cgroup-level charges** đúng scope.

## 21. Những nghi vấn không được kết luận từ một snapshot

Các câu sau **không đủ căn cứ** nếu chỉ có NMT summary tại một thời điểm:

- “Java Heap reserved 8GiB, vậy JVM dùng 8GiB RAM thực”.
- “Class committed 500MiB, chắc chắn classloader leak”.
- “Compiler đang chiếm 50MiB nên C2 có bug”.
- “Code committed 100MiB nghĩa code cache full”.
- “Thread reserved 1GiB nên 1GiB physical stack RSS”.
- “NMT total committed ổn định nên JNI không leak”.
- “NMT diff +20MiB là 20MiB malloc vẫn chưa freed”.
- “NMT Java Heap committed tăng nghĩa live Java objects tăng đúng mức ấy”.
- “NMT detail cho phép xác định Java object giữ native pointer leak”.
- “Summary.diff dương có nghĩa metric sẽ tiếp tục tăng tuyến tính mãi”.

**Tối thiểu cần:** nhiều mốc thời gian, baseline hợp lý, app phase/workload, JVM version/flags và metrics từ các nguồn bổ sung.

## 22. Memory leak thực vs tăng trưởng hợp lệ

Mô hình diễn biến:

~~~text
Case A: expected warm-up
Committed
 ^              ________ plateau
 |           __/
 |        __/
 |_______/
 +--------------------------> time

Case B: suspect sustained growth
Committed
 ^                    /
 |                 __/
 |              __/
 |           __/
 |__________/
 +--------------------------> time

Case C: cyclic work/reclaim
Committed
 ^          /\     /\
 |     /\  /  \   /  \
 |____/  \/    \_/    \____
 +--------------------------> time
~~~

Đây là **các sơ đồ khái niệm**. Ngay cả đường tăng đều dài cũng chưa đủ chứng minh leak: có thể workload/traffic tăng, cache warm-up dài, new modules/classloading hợp lệ. Ngược lại leak có thể xuất hiện ở native allocator **không nằm trong NMT**, khiến NMT phẳng.

### 22.1. Điều kiện kiểm tra leak mạnh hơn

- Cùng workload, same JVM build/config/resource budget.
- Baseline sau phase phù hợp.
- Multiple snapshots với time/order rõ.
- Category growth không plateau khi workload ổn định.
- Native RSS/cgroup memory tăng liên quan.
- Có subsystem lifecycle/class/thread metrics hỗ trợ.
- Sau GC/reclaim/idle, memory không giải thích được bởi live useful structures.
- Tìm được retention mechanism hoặc allocation/free mismatch.

**Không tự ép GC để mong tất cả native allocations biến mất**; heaps, code cache, native arenas có policies khác nhau.

### 22.2. Chọn intervention theo category

| Growth ở đâu? | Giả thuyết tiếp theo |
| --- | --- |
| Java Heap used thực tăng | Object retention/caches; heap dump và GC logs |
| Class committed + classloaders tăng | Loader retention/dynamic code generation |
| Thread count/stack reserved tăng | Pool growth/unbounded thread creation |
| Code compiled usage tăng | JIT warm-up/dynamic methods |
| GC committed tăng theo heap config | Collector footprint/region metadata |
| Compiler intermittent peaks | JIT work in progress |
| NMT flat, RSS tăng | Third-party JNI, OS mappings/allocator |
| NMT total reserved tăng mạnh, committed không tăng | Virtual reservation/allocator policy |

## 23. Mẫu nhật ký quan sát có thể tái lập

~~~text
Incident ID:
Timestamp and timezone:
Process PID / container ID:
JDK vendor/version/build:
OS / architecture:
JVM flags, including NativeMemoryTracking level:
Xms/Xmx / MaxMetaspaceSize / Xss:
GC collector / CPU quota / memory limit:
App commit/image digest:
Workload phase / RPS / concurrency:
Baseline timestamp:
NMT summary at baseline:
NMT summary.diff at T+5m:
NMT summary.diff at T+15m:
NMT summary.diff at T+30m:
Largest committed deltas by category:
RSS/PSS/memory.current at same timestamps:
Thread/class count:
GC/codecache/metaspace evidence:
Hypothesis:
Alternative explanations:
Action:
Validation result:
Rollback plan:
~~~

Cần dùng actual measurements để điền. Không lưu secrets trong JVM command lines, system properties hoặc environment; redact khi chia sẻ.

### 23.1. Vì sao ghi PID/container ID?

Một redeploy có thể tạo JVM process mới cùng tên main class. NMT baseline của PID cũ **không chuyển** sang PID mới. So diffs giữa hai processes cần biết tương ứng workloads/builds, không thể dùng jcmd baseline của process A để so NMT process B theo cùng nghĩa.

### 23.2. Mẫu experiment: NMT summary → detail

- Lượt 1: NMT summary để xác định category đang tăng.
- Lượt 2: Nếu cần call site attribution, khởi động lại hoặc tái hiện sự cố trên process mới bật NMT detail từ đầu.
- Lượt 3: Dùng detail.diff để xác định nguồn allocations HotSpot-tracked cụ thể.
- Lượt 4: Xác thực bằng specialized subsystem diagnostics và OS RSS/cgroup.

Điều này có nghĩa **detail từ process mới không tự giữ snapshot/baseline của process summary cũ**; phải chạy lại experiment protocol và xem điều kiện tương đương hay không.

## 24. Kiểm tra NMT khi tắt và khi JVM kết thúc

### 24.1. NMT off

Nếu NMT off:

~~~bash
java -XX:NativeMemoryTracking=off NmtPhasesDemo
~~~

Ứng dụng demo vẫn chạy bình thường, nhưng **jcmd VM.native_memory** không có category tracking reports như khi summary/detail bật. Muốn NMT, restart với launch flag.

### 24.2. NMT shutdown

~~~bash
jcmd <PID> VM.native_memory shutdown
~~~

Đây là thao tác dừng tracking. Nếu vẫn cần theo dõi sau đó, **không sử dụng shutdown** giữa cuộc điều tra; không restart NMT bằng jcmd.

### 24.3. PrintNMTStatistics tại JVM exit

Theo Oracle JDK17, khi tracking enabled có thể yêu cầu NMT report tại VM exit bằng diagnostic options:

~~~bash
java -XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics NmtDiffMathDemo
~~~

Ứng dụng vẫn in stdout ví dụ NmtDiffMathDemo, **nhưng sau đó JVM có thể in thêm NMT diagnostic report**. Không ghi toàn bộ combined console output như một expected stdout cố định; các NMT numbers và VM output không deterministic.

**PrintNMTStatistics** cần các diagnostic flags phù hợp; kiểm tra JVM build. Đây là công cụ hỗ trợ startup/shutdown snapshots, không thay multi-phase jcmd baseline/diff.

## 25. Những nhầm lẫn phổ biến

1. **“NMT theo dõi mọi cấp phát native trong process.”** Sai: chủ yếu internal HotSpot, không bao phủ mọi JNI/JDK library native allocations.
2. **“NMT = Java heap profiler.”** Sai: thấy heap reservation/commit, không object graphs.
3. **“NMT Java Heap committed chính là heap used.”** Sai.
4. **“NMT tổng committed = process RSS.”** Sai: accounting khác nhau.
5. **“Reserved là RAM vật lý đã dùng.”** Sai: thường là virtual address reservation.
6. **“Class reserved lớn chắc là leak.”** Không suy được.
7. **“Thread reserved = số threads × RSS per stack.”** Không chính xác.
8. **“JIT code cache và compiler arena cùng một category.”** Sai: Code/Compiler thường khác.
9. **“Dùng jcmd baseline để bật NMT runtime.”** Sai.
10. **“jcmd shutdown tắt JVM process.”** Sai: tắt tracking.
11. **“Sau shutdown có thể gọi summary để bật lại NMT.”** Sai trên JDK17.
12. **“Có thể nâng từ summary sang detail runtime không restart.”** Không theo NMT startup levels JDK17.
13. **“summary.diff bằng số bytes từng allocated trong interval.”** Sai: net memory state delta.
14. **“Mỗi summary.diff tự đặt baseline mới.”** Sai: vẫn so baseline hiện tại cho đến khi gọi baseline lại.
15. **“Đặt baseline 2 lần vẫn giữ mốc đầu.”** Sai: latest baseline có hiệu lực.
16. **“Diff âm luôn là bug.”** Sai: có thể reclaim/decommit.
17. **“Diff dương luôn là leak.”** Sai: warm-up/working set growth hợp lệ.
18. **“Không có NMT tăng thì không có JNI leak.”** Sai: outside scope.
19. **“NMT detail có stacktrace Java của mọi object.”** Sai.
20. **“NMT detail đo CPU cost của native allocators.”** Sai.
21. **“Bật NMT không có overhead.”** Sai: Oracle cảnh báo khoảng 5–10%.
22. **“Bật NMT detail riêng candidate nhưng so p99 với baseline off vẫn công bằng.”** Sai: instrumentation confounder.
23. **“Code reserved 240MiB nghĩa 240MiB physical JIT code.”** Sai.
24. **“GC category tăng chứng minh collector lỗi.”** Không đủ dữ liệu.
25. **“Compiler category tăng chứng minh C2 bug.”** Không đủ dữ liệu.
26. **“Một NMT snapshot xác định leak.”** Sai.
27. **“NMT summary tự chỉ ra classloader/object leak root cause.”** Không.
28. **“Mọi DirectByteBuffer memory được NMT đếm đầy đủ theo một category.”** Không được JDK17 docs bảo đảm.
29. **“NMT Native Memory Tracking group có thể bỏ qua mọi khi.”** Sai: overhead có thể ảnh hưởng footprint.
30. **“Mọi NMT categories phải giống nhau qua JDK releases.”** Sai: version-dependent.
31. **“Mọi reserved/committed sizes phải cộng đúng RSS nếu đơn vị bằng nhau.”** Sai.
32. **“Thread.State.WAITING nghĩa thread đã freed native stack.”** Sai.
33. **“Khi Java heap GC, thread stacks/code cache phải tự giảm ngay.”** Sai.
34. **“Chỉ tăng Xmx có thể chữa mọi native OOMKill.”** Sai: còn tăng pressure.
35. **“Một JVM PID có thể dùng NMT baseline của JVM PID khác.”** Sai.
36. **“NMT output là cấu hình ứng dụng có thể copy y nguyên qua mọi OS.”** Sai.
37. **“NMT detail có thể khôi phục tracking quá khứ từ khi summary-only startup.”** Sai.
38. **“NMT summary nhìn thấy external database memory.”** Không liên quan, DB ngoài process.
39. **“NMT code heap full đồng nghĩa Java heap OOME.”** Sai.
40. **“JFR/GC logs và NMT đo cùng một thứ nên chỉ cần một tool.”** Sai: công cụ bổ sung.
41. **“Tăng số threads là miễn phí nếu Java heap còn chỗ.”** Sai: native stacks và scheduler.
42. **“NMT số không đổi nghĩa GC/JIT không làm gì.”** Sai: net memory state có thể ổn định khi work đang diễn ra.

## 26. Bài tập tự kiểm tra có lời giải

**Câu 1.** NMT viết tắt gì? Theo dõi loại memory nào?

**Đáp án:** Native Memory Tracking. HotSpot tracking tập trung VM internal allocations, không phải toàn process native allocations.

**Câu 2.** Muốn summary mode từ startup, dùng flag gì?

**Đáp án:** -XX:NativeMemoryTracking=summary.

**Câu 3.** Muốn call-site detail từ startup, dùng flag gì?

**Đáp án:** -XX:NativeMemoryTracking=detail.

**Câu 4.** JVM đã start NMT=off có thể dùng jcmd VM.native_memory baseline để kích hoạt không?

**Đáp án:** Không. Cần launch/restart với tracking mode thích hợp.

**Câu 5.** Dùng command nào xem NMT summary hiện tại?

**Đáp án:** jcmd <PID> VM.native_memory summary.

**Câu 6.** Command nào đặt baseline?

**Đáp án:** jcmd <PID> VM.native_memory baseline.

**Câu 7.** Command nào xem diff theo category?

**Đáp án:** jcmd <PID> VM.native_memory summary.diff.

**Câu 8.** Command nào xem diff theo call sites khi detail enabled?

**Đáp án:** jcmd <PID> VM.native_memory detail.diff.

**Câu 9.** Gọi baseline lần nữa có giữ nguyên baseline cũ không?

**Đáp án:** Không, baseline hiện hành được thay bằng snapshot mới.

**Câu 10.** NMT summary.diff là cumulative bytes allocated trong thời gian qua?

**Đáp án:** Không. Nó cho current vs baseline net-state difference.

**Câu 11.** Reserved 2GiB, committed 256MiB. Có phải process dùng 2GiB RSS?

**Đáp án:** Không. Reserved virtual address space khác committed/physical RSS.

**Câu 12.** NMT Java Heap committed 512MiB có nghĩa 512MiB live Java objects?

**Đáp án:** Không. Cần heap used/live-set diagnostics riêng.

**Câu 13.** Class category tăng sau khi framework load proxies mới có thể là expected không?

**Đáp án:** Có. Cần classloader/Metaspace trend để xác minh leak hay working set.

**Câu 14.** Thread reserved tăng vì pool tạo 20 threads mới có nghĩa RAM tăng đúng 20 × Xss?

**Đáp án:** Không. Stack reservation khác committed/resident pages.

**Câu 15.** Code tăng còn Compiler tăng trong warm-up nên kết luận gì?

**Đáp án:** Có thể là JIT code cache và compilation temporary arenas tăng hợp lệ; cần metrics thời gian và compiled code diagnostics.

**Câu 16.** GC category tăng khi đổi từ G1 sang ZGC có phải leak?

**Đáp án:** Không đủ căn cứ; collector architectures/memory layout khác nhau.

**Câu 17.** Nếu NMT total committed ổn định còn RSS tăng, nghi gì?

**Đáp án:** Memory ngoài phạm vi NMT như JNI/native libraries, JDK library allocations, mapped pages hoặc OS/cgroup accounting; cần native/OS tools.

**Câu 18.** Nếu RSS nhỏ hơn NMT total committed có chứng minh NMT báo sai?

**Đáp án:** Không. Committed và physical RSS có accounting khác nhau.

**Câu 19.** Vì sao bật NMT có thể làm benchmark chậm?

**Đáp án:** NMT thêm allocation tracking overhead và native metadata; Oracle JDK17 cảnh báo khoảng 5–10% performance overhead có thể xảy ra.

**Câu 20.** Bật NMT detail ở candidate, off ở baseline có công bằng không?

**Đáp án:** Không; instrumentation itself là một biến khác.

**Câu 21.** Một NMT summary snapshot đủ kết luận native leak không?

**Đáp án:** Không. Cần baseline/diffs nhiều thời điểm, workload control và subsystem/native evidence.

**Câu 22.** Điều gì xảy ra khi chạy jcmd VM.native_memory shutdown?

**Đáp án:** Tracking bị tắt; không phải JVM shutdown; không restart NMT trong cùng JVM bằng jcmd.

**Câu 23.** Nếu NMT báo Thread count #72, có 72 RUNNABLE threads không?

**Đáp án:** Không. Đây là thread count accounting, không đồng nghĩa Thread.State.

**Câu 24.** Lệnh nào hữu ích để xem code cache thay vì chỉ NMT Code?

**Đáp án:** jcmd <PID> Compiler.codecache.

**Câu 25.** Baseline khi nào là phù hợp cho leak steady-state?

**Đáp án:** Thường sau warm-up/class loading/JIT ổn định, đồng thời ghi workload/timestamp, vì startup growth có thể hợp lệ.

**Câu 26.** 40,960 KiB → 53,248 KiB tăng bao nhiêu?

**Đáp án:** +12,288 KiB, tương đối +30.00%.

**Câu 27.** Có thể dùng NMT detail để xác định Java object nào giữ references tới classloader không?

**Đáp án:** Không. Dùng heap dump, classloader diagnostics/GC tools.

**Câu 28.** Khi so baseline và candidate JVM khác processes, có thể dùng internal NMT baseline của process A cho process B không?

**Đáp án:** Không. So các independent reports ngoài tool theo cùng workload/configuration, không tái sử dụng baseline internal giữa JVM.

**Câu 29.** NMT Native Memory Tracking category tăng có nghĩa gì?

**Đáp án:** Có thể là overhead lưu tracking metadata; cần xem mode/total footprint.

**Câu 30.** DirectByteBuffer và JNI allocations có được NMT bảo đảm bao phủ đầy đủ không?

**Đáp án:** Không. NMT chỉ theo dõi allocations đi qua tracked HotSpot internals, tài liệu JDK17 nêu giới hạn với JDK libraries/third-party native allocations.

## 27. Checklist thao tác an toàn

### Khi chuẩn bị

- [ ] Xác nhận JVM thực sự là HotSpot và version được hỗ trợ.
- [ ] Chọn NMT summary hoặc detail trước startup.
- [ ] Ghi nhận 5–10% overhead cảnh báo và policy production.
- [ ] Chuẩn bị jcmd có thể attach đúng PID/quyền/container namespace.
- [ ] Quy định workload/phase và baseline timestamp.
- [ ] Ghi flags, Xmx, GC, app build và CPU/memory limits.

### Khi thu thập

- [ ] Lấy summary/detail ở phase tương ứng.
- [ ] Đặt baseline đúng mốc trước khi so.
- [ ] Không vô ý gọi baseline lại giữa các snapshots.
- [ ] Thu nhiều summary.diff/detail.diff cùng baseline.
- [ ] Ghi scale=KB hoặc units không mất thay đổi nhỏ.
- [ ] Không gộp top-level và sub-lines gây double-count.
- [ ] Đối chiếu Thread/Class/Code/GC changes với specialized jcmd.
- [ ] Đo RSS/PSS/cgroup separately và cùng timestamp.

### Khi phân tích

- [ ] Phân biệt reserved/committed/used/RSS.
- [ ] Phân biệt heap commit và Java live set.
- [ ] Phân biệt VM internal allocations và JNI/native external allocations.
- [ ] Giải thích startup/JIT/classloading/GC warm-up.
- [ ] Kiểm tra NMT mode overhead và baseline validity.
- [ ] Tìm sustained growth thay vì một snapshot.
- [ ] Chỉ chọn flags/code fixes khi có giả thuyết kiểm chứng được.

### Khi kết luận

- [ ] Có command lines, raw reports và decision record.
- [ ] Không xuất JVM args/secrets công khai.
- [ ] Có bước rollback/configuration preservation.
- [ ] Nếu NMT không giải thích RSS, mở rộng native/OS profiling thay vì phủ nhận vấn đề.
- [ ] Ghi rõ “leak confirmed”, “suspected” hoặc “inconclusive” đúng mức bằng chứng.
- [ ] Recheck kết quả sau fix trên cùng workload và JDK build.

## 28. Tài liệu tham khảo chính thức

- **Oracle JDK 17 — Native Memory Tracking (tổng quan, giới hạn, overhead, startup, baseline, shutdown):** https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
- **Oracle JDK 17 — jcmd command: VM.native_memory summary/detail/baseline/diff:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **Oracle JDK 17 — Java Virtual Machine Guide:** https://docs.oracle.com/en/java/javase/17/vm/
- **Oracle JDK 17 — Troubleshooting Guide (native memory leaks):** https://docs.oracle.com/en/java/javase/17/troubleshoot/
- **Oracle JDK 17 — java command JVM options:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Oracle JDK 17 — Garbage Collection Tuning Guide:** https://docs.oracle.com/en/java/javase/17/gctuning/
- **Oracle JDK 17 — Thread class and thread lifecycle:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- **Oracle JDK 17 — Runtime Memory APIs:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html
- **Oracle JDK 17 — ManagementFactory and MemoryMXBean:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ManagementFactory.html
- **Oracle JDK 17 — BufferPoolMXBean:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/BufferPoolMXBean.html
- **OpenJDK — HotSpot Native Memory Tracking source:** https://github.com/openjdk/jdk17u/tree/master/src/hotspot/share/nmt
- **OpenJDK — NMT implementation notes (source root):** https://github.com/openjdk/jdk/tree/master/src/hotspot/share/nmt

## 29. Kết luận

Native Memory Tracking là công cụ hữu ích để trả lời **“HotSpot nội bộ đang reserve/commit memory cho subsystem nào và tình hình thay đổi ra sao so với baseline?”**. Nó báo **Java Heap, Class/Metaspace, Thread/stacks, Code/JIT cache, GC, Compiler và những nhóm VM khác** theo mode summary/detail; có thể đặt **baseline** rồi theo dõi **summary.diff/detail.diff**.

Tuy nhiên:

- **Reserved ≠ committed ≠ used ≠ RSS.**
- **NMT Java Heap không phải live Java object heap profiler.**
- **NMT không bao quát toàn bộ native allocations của JNI/third-party libraries/JDK class libraries.**
- **NMT off không thể bật lại bằng jcmd trong JVM đang chạy; shutdown tracking không khởi động lại được.**
- **Một diff dương không đủ kết luận leak; baseline nhiều mốc và workload context mới quan trọng.**

Cách điều tra đúng là **bật NMT với mức phù hợp ngay từ startup → đặt baseline ở đúng phase → thu nhiều diffs → phân loại group tăng → dùng các công cụ GC/Metaspace/Thread/Code/native/OS để xác minh → sửa giả thuyết có bằng chứng → đo lại dưới cấu hình có thể tái lập**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-007">StackOverflowError và các dạng OutOfMemoryError cần phân biệt</a>

<details>
<summary>Xem chi tiết</summary>

# StackOverflowError và các dạng OutOfMemoryError cần phân biệt

## 1. Vì sao thấy `Error` chưa đủ để kết luận JVM hết Java heap?

Một tiến trình Java có thể gặp:

- `java.lang.StackOverflowError` khi không còn không gian stack cho call frames.
- `java.lang.OutOfMemoryError: Java heap space` khi không cấp phát được Java object/array trong heap.
- `java.lang.OutOfMemoryError: GC overhead limit exceeded` khi collector dành quá nhiều thời gian GC mà thu hồi rất ít heap theo chính sách cụ thể.
- `java.lang.OutOfMemoryError: Metaspace` hoặc `Compressed class space` khi hết ngân sách class metadata.
- `java.lang.OutOfMemoryError: unable to create native thread` khi JVM/OS không tạo được platform thread mới.
- `OutOfMemoryError` liên quan **direct buffer memory**, native allocations hoặc một array vượt giới hạn implementation.
- **Tiến trình bị kernel/container OOM kill** hoặc VM fatal error/crash mà **không hề có Java `OutOfMemoryError` được ném**.

Đây là các **cơ chế hết tài nguyên khác nhau**. Bật `-Xmx` lớn hơn có thể giúp một tình huống thiếu heap, nhưng không chữa lỗi recursion vô hạn, giới hạn `pids.max` hay một thư viện JNI giữ 2 GiB native memory ngoài heap.

**Mục tiêu:** đọc đúng loại lỗi, hiểu vùng bộ nhớ và tài nguyên cạn kiệt, biết chọn log/dump/counter đúng, tái hiện an toàn và **không nhầm phục hồi tạm thời với giải quyết nguyên nhân**.

### Sơ đồ phân loại ngắn

~~~text
Java process chậm, OOM hoặc kết thúc
    |
    +-- Có "StackOverflowError"?
    |     +-- kiểm tra call depth, recursion, -Xss, thread stack
    |
    +-- Có "OutOfMemoryError" được ném?
    |     +-- Java heap space / GC overhead / array size
    |     +-- Metaspace / Compressed class space
    |     +-- direct buffer / native thread / native malloc
    |     +-- đọc nguyên văn detail message và runtime evidence
    |
    +-- Không có Java Error, process bị kill/crash?
          +-- cgroup memory.events, OS logs, process exit/signal
          +-- hs_err_pid*.log / native crash dump (nếu có)
          +-- không suy đoán từ heap used đơn lẻ
~~~

Không phải mọi case đều có message chính xác như sơ đồ. **Thông điệp detail là manh mối của VM/allocator cụ thể**, không phải tập enum chuẩn hóa bởi Java Language Specification. Một library cũng có thể tự tạo `OutOfMemoryError`, nên phải xem call stack, JVM build và nguồn cấp phát.

## 2. Vị trí trong hệ phân cấp và ngữ nghĩa Java

Trong Java SE 17:

~~~text
java.lang.Object
 └── Throwable
      └── Error
           └── VirtualMachineError
                ├── StackOverflowError
                └── OutOfMemoryError
~~~

Cả hai là **`Error`**, không phải `Exception` thông thường. Theo Javadoc, `VirtualMachineError` cho biết Java Virtual Machine bị hỏng hoặc cạn tài nguyên cần để tiếp tục vận hành. Dù JVM có thể ném Error và chương trình về mặt cú pháp **có thể catch**, **không có bảo đảm** hệ thống đang ở trạng thái đủ an toàn để tiếp tục phục vụ request bình thường.

**Phân biệt type và detail message:**

- `StackOverflowError` là **một class type** riêng.
- `OutOfMemoryError` là **một class type**; các chuỗi `Java heap space`, `Metaspace`, `GC overhead limit exceeded`, `Direct buffer memory` thường là **detail messages**, không phải những subclasses khác nhau.
- `catch (OutOfMemoryError e)` bắt nhiều dạng OOME khác nhau; nếu dùng catch này trong công cụ lab, phải hiểu nó **không phân biệt root cause**.
- `catch (Exception e)` **không bắt** trực tiếp `StackOverflowError` hoặc `OutOfMemoryError`.
- `catch (Throwable t)` có thể bắt cả `Error`, nhưng thường là sai lầm nếu chỉ muốn retry request vô hạn.

**Điều không được khẳng định:** Một `OutOfMemoryError` nhất thiết chứng minh memory leak. Nó cũng có thể là sizing sai, spike load, payload lớn bất thường, process/cgroup quota, classloader explosion, direct-buffer retention hoặc lỗi trong native allocator.

## 3. `StackOverflowError`: vùng stack bị cạn, không phải heap đầy

### 3.1. Stack frame là gì?

Khi một Java thread thực thi:

~~~text
main()
  └─ controller()
       └─ service()
            └─ parse()
                 └─ parse() → parse() → ...
~~~

Mỗi lần gọi method đang active cần một Java frame logic với locals, operand stack ở mức JVM và return state. Với HotSpot **platform thread**, stack thực thi liên quan đến **native/OS stack** của thread đó; JVM quản lý stack overflow checks/guard areas.

Nếu recursion không có điểm dừng hoặc call chain hợp lệ sâu hơn stack budget, VM có thể ném `StackOverflowError`.

### 3.2. Những nguyên nhân phổ biến

- Recursion vô hạn: `f()` gọi `f()` mà không có base case.
- Recursion có base case nhưng **depth hợp lệ quá lớn**, như DFS trên graph/tree lệch sâu, parser lồng hàng vạn levels.
- Mutual recursion: `a()` gọi `b()`, `b()` gọi lại `a()`.
- `toString()`, `equals()`, `hashCode()` giữa objects tham chiếu vòng và implementation không bảo vệ chu trình.
- Mapper/serializer tự duyệt object graph cyclic; Jackson/generic reflection/custom visitor theo depth không giới hạn.
- Call chain kết hợp nhiều framework layers, native/Java transitions, runtime instrumentation dẫn đến stack frames lớn hơn dự kiến.
- `-Xss` quá nhỏ cho call depth hợp lệ trên OS/JDK hiện tại.

**Ví dụ bug minh họa:**

~~~java
static int countNodes(Node n) {
    if (n == null) return 0;
    return 1 + countNodes(n.next);
}
~~~

Đây là **mảnh Java không độc lập**, cần định nghĩa `Node`. Với list liên kết rất dài, recursion depth tuyến tính theo số nodes. Để duyệt list có thể dùng iteration và kiểm tra chu trình khi hợp đồng dữ liệu đòi hỏi.

### 3.3. `-Xss` tác động thế nào?

`-Xss` điều chỉnh thread stack size theo JVM/OS, ví dụ:

~~~bash
java -Xss256k StackDepthDemo
java -Xss1m StackDepthDemo
~~~

`-Xss` là **ngân sách stack cho platform thread**, không phải kích thước Java heap. Mức minimum/rounding khác theo build, architecture và OS. Một số cấu hình quá thấp có thể bị VM từ chối ngay ở startup.

Không có phép đổi cố định từ `-Xss` sang “số method calls tối đa” vì frame size phụ thuộc:
- Interpreter và JIT compiled code/inlining.
- Locals, method complexity, native transitions.
- Architecture, shadow/guard pages và JVM implementation.
- Debug/profiling/agent instrumentation, compiler optimization.

**Tăng `-Xss` không sửa bug recursion vô hạn.** Nó có thể trì hoãn lỗi và làm tăng native stack reservations của mỗi platform thread, khiến ngân sách cho nhiều OS threads tệ hơn.

### 3.4. Java 21+ virtual threads là ngoại lệ về cách cất giữ stack

Virtual threads có call stack logic và vẫn có thể gặp `StackOverflowError`. Nhưng mỗi virtual thread không sở hữu một **native stack riêng** trong suốt lifetime; HotSpot có thể lưu continuation/stack chunks trên heap khi unmount. Vì vậy không dùng `N_virtual_threads × -Xss` để dự đoán native stack reservations.

Bài lab phía dưới là **Java 17 platform threads**, không cần tính năng preview hay Java 21.

### 3.5. Có nên catch `StackOverflowError`?

Trong production, mục tiêu là **sửa thuật toán, giới hạn độ sâu input, phá chu trình hoặc dùng iterative traversal**. Khi stack đã cạn, error handler phức tạp cũng có thể thất bại khi cần thêm frames.

Tại ranh giới process/task có thể có chính sách fail-fast/log minimal, nhưng **đừng viết `catch (StackOverflowError) { return default; }` như logic bình thường**. Nó có thể che mất dữ liệu lỗi hoặc kết quả bị xử lý dở dang.

## 4. `OutOfMemoryError: Java heap space`

Đây là lỗi thường gặp khi JVM không thể cấp phát đối tượng trong Java heap (sau những nỗ lực quản lý heap/GC phù hợp) và không thể mở rộng thêm theo giới hạn cấu hình.

### 4.1. Nguyên nhân chính

- `-Xmx` nhỏ hơn working set hợp lệ của ứng dụng.
- Unbounded collection/cache/queue giữ references không cần thiết.
- Một request đọc toàn bộ file/body/report lớn vào RAM.
- Memory leak: references đến objects đáng lẽ không còn cần nhưng vẫn reachable qua static fields, singleton caches, listeners, ThreadLocals, request registries...
- Traffic burst làm concurrent live data/payloads tăng, kể cả khi không có leak.
- Mức memory fragmentation, object size và collector/allocation behavior trong trường hợp cụ thể.

**Không nhất thiết là leak:** nếu application thật sự cần một `byte[64MiB]` nhưng heap max 32 MiB, allocation không thể thành công, kể cả trước đó không có dữ liệu nào bị giữ lại bất thường.

### 4.2. `used`, `committed`, `max` khác nhau

- **Heap used**: JVM báo lượng heap đang sử dụng.
- **Heap committed**: heap space VM cam kết dành cho heap management; không phải physical RSS chính xác.
- **Heap max / `-Xmx`**: giới hạn tối đa heap; **không** phải giới hạn toàn process.
- **RSS/container usage**: phần memory hệ điều hành hạch toán cho pages/process/cgroup, còn có native stacks, metaspace, direct buffers, file cache...

Không lấy phép trừ `RSS - heapUsed` gọi ngay là native memory leak; không cộng `NMT Total committed + heapCommitted`, vì **NMT Total đã bao gồm Java Heap**.

### 4.3. Chứng cứ nên thu

1. Log GC/heap trước và trong thời điểm lỗi.
2. Heap dump hoặc class histogram **nếu an toàn và được phép**.
3. Allocation rate, cache/queue size, concurrent requests, heap live set qua các lần GC.
4. `java -version`, heap flags (`-Xms`, `-Xmx`), effective container limit và GC.
5. JFR allocations, object counts và dominator/retained graph nếu có dump phù hợp.

**Không chỉ tăng `-Xmx` trước khi xem leak**: memory leak tuyến tính chỉ khiến lỗi xuất hiện muộn hơn. Nhưng nếu sizing không đáp ứng live working set hợp lệ, điều chỉnh heap/budget là cần thiết.

## 5. `OutOfMemoryError: GC overhead limit exceeded`

Đây là **cùng type** `OutOfMemoryError`, với message khác. Trong HotSpot/Oracle JDK 17 documentation, tình huống classic là JVM dành gần như toàn bộ thời gian vào GC nhưng thu hồi quá ít heap nên application **không tiến triển đủ**.

Oracle mô tả heuristic cổ điển xấp xỉ:

- GC chiếm hơn **98% thời gian**.
- Thu hồi ít hơn khoảng **2% heap**.
- Điều kiện kéo dài theo số collections liên tiếp mà implementation yêu cầu (tài liệu nêu 5 cycles).

Các ngưỡng và cách áp dụng phụ thuộc **collector/JDK implementation**; không coi đây là quy tắc ngôn ngữ Java hoặc cam kết mọi collector đều ném đúng message này.

### Cách giải thích

Nếu **live set gần đầy heap**, GC phải chạy liên tục nhưng không tạo đủ free space cho application. Message có thể khác với `Java heap space`, song cả hai thường liên quan **heap pressure nghiêm trọng**.

**Chẩn đoán**: xem GC logs (`-Xlog:gc*`), after-GC occupancy, allocation rate và retained objects. Nếu application có leak, cần sửa retention; nếu live set hợp lệ > heap budget, cân nhắc tăng heap trong giới hạn process/container.

Không khuyến nghị dùng `-XX:-UseGCOverheadLimit` để “sửa lỗi”. Flag ấy (khi JVM hỗ trợ) có thể tắt heuristic báo lỗi nhưng **không giải phóng heap** hoặc sửa nguyên nhân; ứng dụng có thể tiếp tục GC thrashing, cuối cùng gặp OOME khác.

### Vì sao lab không cố tạo message này?

Để tái tạo `GC overhead limit exceeded` một cách chắc chắn thường cần một workload/live set tương ứng collector/JDK, có thể tốn CPU, memory và làm benchmark sai. Bài dùng **log và phân tích** thay vì ép một tiến trình dùng nhiều tài nguyên để lấy một dòng message không portable.

## 6. `OutOfMemoryError: Requested array size exceeds VM limit`

Một Java array cần độ dài hợp lệ (`int`), nhưng JVM còn có **implementation-specific maximum array length** do header/alignment/VM layout và constraints khác. Request mảng quá giới hạn ấy có thể ném:

~~~text
java.lang.OutOfMemoryError: Requested array size exceeds VM limit
~~~

Điểm khác `Java heap space`: đây có thể là **giới hạn kích thước một object/array** trước cả việc xem heap còn bao nhiêu bytes. Có thể do:

- Input length tính sai, file length vượt giới hạn array index.
- Gộp payloads thành một array cực lớn.
- Overflow khi tính `int` length từ `long` size.
- Thư viện cố build một `String`/array khổng lồ.

**Không tin rằng mọi array length bằng `Integer.MAX_VALUE` sẽ có cùng message trên mọi JVM/architecture**. Có thể nhận các exceptions khác tùy size/VM và trạng thái tài nguyên.

### Cách xử lý

- Validate size bằng `long` trước khi cast sang `int`.
- Chia dữ liệu thành chunks/streaming thay vì một array duy nhất.
- Sử dụng bounded buffers, page/chunk processing hoặc file-backed techniques khi business flow cho phép.
- Kiểm tra logic nhân/cộng lengths để tránh overflow.

Đây là khác biệt quan trọng: **tăng `-Xmx` vô hạn không vượt được giới hạn array object của VM**.

## 7. `OutOfMemoryError: Metaspace`

Từ Java 8, HotSpot dùng **Metaspace** trong native memory để quản lý nhiều loại class metadata thay cho PermGen cũ. Class loading, dynamic proxies, generated classes, bytecode frameworks và classloader lifetime ảnh hưởng footprint.

### Khi nào thấy `Metaspace`?

- `-XX:MaxMetaspaceSize` đặt quá thấp so với lượng class metadata hợp lệ.
- Nhiều classloaders sống lâu giữ lại toàn bộ classes mà chúng define.
- Dynamic class generation tăng không giới hạn, ví dụ tạo class mới cho mỗi request.
- Redeploy/plugin/container classloader leak giữ old instances.
- Metadata cần native memory nhưng OS/process không đủ tài nguyên.

### Cách phân tích

- `jcmd PID VM.classloader_stats` (xác minh `jcmd PID help` nếu khác build).
- `jcmd PID VM.native_memory summary` khi **đã bật NMT từ startup**.
- Class load/unload logs (`-Xlog:class+load=info,class+unload=info`).
- JFR class loading, heap dump để tìm **classloader object retention**, framework caches.
- Metaspace used/committed theo JMX memory pool và GC/class unloading behavior.

**Đừng chỉ nhìn Java heap used thấp rồi kết luận không có vấn đề memory:** Metaspace nằm ngoài ordinary Java object heap, dù defining classloader objects/references nằm trong heap.

### `MaxMetaspaceSize` khác `MetaspaceSize`

- `-XX:MaxMetaspaceSize=...` giới hạn phần Metaspace theo cơ chế JVM, có thể khiến OOME nếu đặt quá thấp.
- `-XX:MetaspaceSize=...` liên quan threshold ban đầu kích hoạt GC vì metadata pressure, **không phải hard upper bound tương đương `MaxMetaspaceSize`**.

Nếu gặp classloader leak, tăng `MaxMetaspaceSize` chỉ trì hoãn failure, không sửa nguồn retention.

## 8. `OutOfMemoryError: Compressed class space`

Ở nhiều cấu hình HotSpot 64-bit có **compressed class pointers**, JVM reserve vùng địa chỉ riêng cho metadata của class (Klass structures) gọi **Compressed Class Space**.

- `-XX:CompressedClassSpaceSize` chi phối giới hạn/reservation của vùng này khi feature được bật.
- `-XX:+UseCompressedClassPointers` là một HotSpot flag liên quan (giá trị default/khả dụng tùy runtime).
- `Compressed class space` và `Metaspace` đều liên quan **class metadata** nhưng có **các không gian/quy tắc sizing khác nhau**.

Một NMT output có:

~~~text
Class (reserved=1024MB, committed=...)
~~~

**không có nghĩa** có một GiB class metadata đã chạm RAM vật lý; reserved address space khác committed/resident. Trong NMT, `Class` có thể gồm cả compressed class space và non-class metadata subcategories, tùy JDK build.

**Không làm theo phản xạ** tắt compressed class pointers trên production. Thay đổi representation, compatibility, footprint và hiệu năng; cần đo và có lý do kỹ thuật.

### Phân biệt `Metaspace` và `Compressed class space`

| Dấu hiệu | Điều cần điều tra |
| --- | --- |
| `Metaspace` | Tổng metadata footprint, classloaders, `MaxMetaspaceSize`, class unloading |
| `Compressed class space` | Metadata trong compressed class pointer space, `CompressedClassSpaceSize`, VM layout |
| `Java heap space` | Java object live set, `Xmx`, allocation/retained objects |

Cả ba có thể được ảnh hưởng gián tiếp bởi framework/class generation, nhưng không cùng một memory budget.

## 9. OOME liên quan DirectByteBuffer và off-heap

`ByteBuffer.allocateDirect(size)` tạo Java wrapper cho direct buffer và yêu cầu **bộ nhớ ngoài heap** phù hợp. Với HotSpot/Java NIO, direct buffer reservation có thể chịu giới hạn cấu hình:

~~~bash
-XX:MaxDirectMemorySize=8m
~~~

Một dạng detail message của JDK hiện đại có thể chứa `direct buffer memory` hoặc `Cannot reserve ... bytes of direct buffer memory` (chuỗi cụ thể không phải API chuẩn Java).

### Vì sao heap còn rảnh nhưng direct buffer lỗi?

- Direct buffer native memory đã đạt configured limit.
- Buffers vẫn được giữ reachable qua pools, caches, futures hoặc requests; cleanup qua Cleaner/GC không kịp nhịp allocation.
- Thư viện có direct buffer hoặc mmap lifecycle dài, hoặc allocator/native memory pressure.
- Heap thấp **không** có nghĩa off-heap thấp.

### Công cụ đo

Java 17 có `BufferPoolMXBean`, có thể quan sát direct pool:

~~~java
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;

for (BufferPoolMXBean pool :
        ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
    if ("direct".equals(pool.getName())) {
        System.out.println("count=" + pool.getCount());
        System.out.println("capacity=" + pool.getTotalCapacity());
        System.out.println("memoryUsed=" + pool.getMemoryUsed());
    }
}
~~~

Đây là **mảnh source để đặt bên trong method**, không phải chương trình tự chạy độc lập (vì có câu lệnh top-level import/loop). Các values là estimates của direct buffer pool mà VM phơi ra, **không đo tất cả JNI/native allocations**.

Đừng cộng `getTotalCapacity()` và `getMemoryUsed()` như hai vùng disjoint; chúng biểu diễn các khía cạnh của **cùng buffers**.

### `-XX:MaxDirectMemorySize` không cap mọi native malloc

Một thư viện JNI gọi `malloc`, một native cache hoặc thư viện C++ có thể không tuân theo direct-buffer reservation limit của NIO. Do đó **tăng `MaxDirectMemorySize` không chữa mọi native-memory OOME**; và **tăng nó khi container limit nhỏ** có thể làm OOMKilled dễ hơn.

## 10. `OutOfMemoryError: unable to create native thread`

Java 17 `new Thread(...).start()` với platform threads đòi OS/JVM cấp phát native thread resources. Khi OS/JVM từ chối, một dạng message có thể là:

~~~text
java.lang.OutOfMemoryError: unable to create native thread
~~~

**Không suy luận ngay JVM hết Java heap**. Những giới hạn có thể gây lỗi:

1. Native stack reservations/commit hoặc native memory không đủ.
2. OS per-user/per-process thread limits, global thread limits.
3. Linux `RLIMIT_NPROC`/`ulimit -u` trong các trường hợp áp dụng.
4. Cgroup **pids controller** `pids.current`, `pids.max`, `pids.events`.
5. Thread pool không giới hạn, thread leak, tạo một platform thread cho mỗi request blocking.
6. Native library hoặc JVM service threads/GC/compiler threads cạnh tranh cùng tài nguyên.
7. OS-specific constraints/permissions.

### Điều tra đúng

Linux:

~~~bash
ps -o pid,nlwp,rss,vsz,comm -p PID
ps -L -p PID -o pid,lwp,stat,comm
cat /proc/PID/status
cat /sys/fs/cgroup/pids.current
cat /sys/fs/cgroup/pids.max
cat /sys/fs/cgroup/pids.events
cat /sys/fs/cgroup/memory.current
cat /sys/fs/cgroup/memory.events
~~~

`PID` là ký hiệu phải thay bằng process ID thực; các đường cgroup là **ví dụ cgroup v2** và có thể khác tùy namespace/mount/kernel. `pids.max` có thể là `max`; thiếu file không chứng minh JVM không dùng native threads.

Trên Windows PowerShell:

~~~powershell
Get-Process -Id 12345 |
  Select-Object Id,@{Name='Threads';Expression={$_.Threads.Count}},
                WorkingSet64,PrivateMemorySize64,VirtualMemorySize64
~~~

`12345` chỉ là PID ví dụ. `WorkingSet64` khác reserved/virtual address space và Private Bytes.

JVM:

~~~text
jcmd PID Thread.print
jcmd PID VM.native_memory summary scale=MB
jcmd PID VM.flags
~~~

NMT phải bật từ JVM startup; thread dump và NMT snapshot giúp biết **số thread/stack**, không tự chứng minh limit nằm ở RAM hay pids quota.

### Vì sao không đưa ví dụ tạo hàng nghìn threads cho tới khi lỗi?

Thí nghiệm exhaust native threads có thể làm **cả máy/shared container không tạo được threads**, phá những processes khác hoặc không khôi phục được. Không cần làm như vậy để hiểu lỗi. Thay vào đó, kiểm tra `pids.max`, thread pool metrics, NMT, thread dumps trên một workload được giới hạn.

**Hướng sửa** thường là giới hạn thread pools, backpressure, giảm blocking concurrency hoặc dùng asynchronous I/O; với Java 21+ virtual threads có thể giúp I/O-heavy concurrency nhưng **không làm downstream capacity hay heap vô hạn**.

## 11. Native allocation failure, `Out of swap space` và JVM crash

Một số errors từ native heap/system malloc/JNI có thể đi cùng message kiểu:

~~~text
Out of swap space?
Native memory allocation (malloc) failed to allocate ...
Failed to map memory ...
~~~

Trên một số HotSpot paths, JVM có thể ghi **fatal error log** và **terminate process** thay vì ném một Java OOME có thể catch. Detail `request size bytes for reason. Out of swap space?` được Oracle JDK Troubleshooting Guide mô tả như một kiểu memory failure native dẫn đến fatal handling.

Các nguyên nhân gồm:

- JVM/cgroup/process chạm giới hạn native memory/address space.
- OS không cấp được contiguous mapping hoặc commit.
- Third-party native allocator/fragmentation/cache retention.
- Memory pressure hoặc swap/overcommit settings của OS.
- Direct/native buffers, mapped files, JNI, compression/TLS/native DB clients.

**NMT không đo hoàn toàn third-party C malloc**. Nếu NMT committed gần như đứng yên nhưng RSS/private memory tăng, cần OS memory mappings (`/proc/PID/smaps_rollup`, `pmap`, Process Explorer, native allocator profiler) và xem các JNI libraries, không kết luận NMT đã chứng minh không có native leak.

### Crash log và heap dump khác nhau

- `hs_err_pid*.log`: fatal VM error report, có thể chứa thread/native stack, registers, mappings và VM flags; tạo trong crash path khi JVM có khả năng ghi.
- `.hprof` heap dump: snapshot Java heap objects/references, phục vụ điều tra Java object retention.
- GC log/JFR: runtime events/trends, không phải full Java heap graph.
- Container OOMKilled: kernel có thể kill mà JVM **không kịp ghi** `.hprof` hoặc `hs_err_pid`.

Không bắt buộc sẽ có đủ cả ba. Quyết định công cụ phải theo loại failure.

## 12. Container/Linux OOMKilled: không phải Java `OutOfMemoryError`

Một JVM có `-Xmx512m` chạy trong container với limit 650 MiB vẫn có thể bị kill dù `heapUsed` chỉ 300 MiB: ngoài heap còn metaspace, code cache, GC structures, thread stacks, direct/native allocations, file cache, kernel charges hoặc processes cùng cgroup.

~~~text
Cgroup memory.max = 650 MiB
JVM Java heap max = 512 MiB
Other memory/charges peak = 200 MiB
Potential total charge > 650 MiB
                 |
                 v
Kernel/cgroup may kill process (OOM killer)
                 |
                 v
No guarantee of Java OutOfMemoryError or heap dump
~~~

**Đây là sơ đồ giả định về ngân sách**, không là phép cộng chi tiết các counters có overlap. `-Xmx` chỉ là phần heap budget; `memory.current` còn gồm những thành phần khác theo cgroup accounting.

### Dấu vết điều tra

~~~bash
cat /sys/fs/cgroup/memory.max
cat /sys/fs/cgroup/memory.current
cat /sys/fs/cgroup/memory.peak
cat /sys/fs/cgroup/memory.events
cat /sys/fs/cgroup/memory.stat
~~~

Đường dẫn cgroup v2 có thể khác. `memory.events` có những counters như `oom`, `oom_kill` (kernel dependent). Trong Kubernetes, container status có thể ghi reason `OOMKilled`.

Exit code `137` trên Linux thường là `128 + SIGKILL(9)`, nhưng **không chứng minh duy nhất do cgroup OOM**: process cũng có thể bị kill vì lý do khác. Cần kernel/cgroup/container events để xác nhận.

### Java OOME khác OOMKill

| Dấu hiệu | Java OOME | OOMKilled |
| --- | --- | --- |
| Ai phát tín hiệu? | JVM/Java allocator có thể ném `OutOfMemoryError` | Kernel/cgroup/OOM killer |
| Java stack trace? | Thường có nếu stderr được ghi và đủ điều kiện | Không bảo đảm |
| `catch (OutOfMemoryError)`? | Có thể bắt nếu VM đã ném | Không thể bắt một SIGKILL bằng Java |
| Heap dump on OOME? | Có thể nếu HotSpot flag bật, đủ disk/resources | Không bảo đảm process có cơ hội dump |
| Có thể xảy ra khi heap used thấp? | Có với Metaspace/direct/native cases | Có |
| Điều tra nguồn? | Message, heap/JFR/NMT/GC/VM flags | Cgroup/kernel/container events, RSS, native charges |

Đừng chữa mọi OOMKill bằng tăng `-Xmx`: điều đó có thể làm **memory headroom ngoài heap giảm thêm**.

## 13. Java 17 runnable: ba thí nghiệm có giới hạn, không phá tài nguyên hệ thống

Các ví dụ dưới đây đã được **biên dịch với `javac --release 17` và chạy trên OpenJDK/HotSpot 24 Windows**, lần lượt dùng VM flags hạn chế riêng. Chúng không cần library ngoài.

**Chỉ chạy trong môi trường thử nghiệm**, không bật các giới hạn nhỏ lên service production. Mỗi test chạy trong **JVM process mới**, tránh để một lỗi ảnh hưởng hai tình huống kế tiếp.

Tạo file **`JvmErrorKindsDemo.java`**:

~~~java
import java.nio.ByteBuffer;

public final class JvmErrorKindsDemo {
    private static volatile int depth;
    private static volatile Object retained;

    static void dive(int n) {
        depth = n;
        dive(n + 1);
    }

    static void stackCase() {
        try {
            dive(1);
        } catch (StackOverflowError expected) {
            System.out.println("stackOverflowCaught=true");
            System.out.println("depthPositive=" + (depth > 0));
        }
    }

    static void heapCase() {
        try {
            retained = new byte[64 * 1024 * 1024];
            System.out.println("heapAllocationSucceeded=true");
        } catch (OutOfMemoryError expected) {
            System.out.println("outOfMemoryCaught=true");
        }
    }

    static void directCase() {
        try {
            retained = ByteBuffer.allocateDirect(16 * 1024 * 1024);
            System.out.println("directAllocationSucceeded=true");
        } catch (OutOfMemoryError expected) {
            System.out.println("outOfMemoryCaught=true");
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("usage=stack|heap|direct");
            return;
        }
        System.out.println("case=" + args[0]);
        switch (args[0]) {
            case "stack": stackCase(); break;
            case "heap": heapCase(); break;
            case "direct": directCase(); break;
            default: System.out.println("usage=stack|heap|direct");
        }
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 JvmErrorKindsDemo.java
~~~

### 13.1. Case A: recursion → `StackOverflowError`

~~~bash
java -Xss256k -Xmx32m JvmErrorKindsDemo stack
~~~

**Stdout đã kiểm chứng:**

~~~text
case=stack
stackOverflowCaught=true
depthPositive=true
~~~

Cơ chế: `dive()` gọi chính nó vô hạn; với stack budget nhỏ, một lần gọi mới không được thỏa mãn và `StackOverflowError` phát sinh. `depthPositive` chỉ kiểm tra đã đi vào recursion, không phải độ sâu tối đa chính xác.

**Tại sao không in `depthReached=1234`?** Độ sâu phụ thuộc JDK/JIT/OS/stack frame và flags. Giá trị số cụ thể không portable.

### 13.2. Case B: allocation Java heap quá lớn

~~~bash
java -Xmx32m JvmErrorKindsDemo heap
~~~

**Stdout đã kiểm chứng:**

~~~text
case=heap
outOfMemoryCaught=true
~~~

Cơ chế: yêu cầu `byte[64 MiB]` trong heap giới hạn `32 MiB`. Trong chạy này, allocation thất bại và `OutOfMemoryError` được catch. **Không in detail message** vì nó có thể thay đổi giữa VM/version. Không đồng nhất với tình huống heap leak — đây là **một allocation vượt ngân sách cố ý**.

### 13.3. Case C: vượt direct buffer reservation limit

~~~bash
java -Xmx32m -XX:MaxDirectMemorySize=8m JvmErrorKindsDemo direct
~~~

**Stdout đã kiểm chứng:**

~~~text
case=direct
outOfMemoryCaught=true
~~~

Cơ chế: yêu cầu direct buffer 16 MiB khi direct-buffer limit được đặt 8 MiB trên HotSpot. Heap max vẫn 32 MiB, nhưng **nguồn thất bại là direct reservation**, không phải allocation một Java `byte[]` 16 MiB.

**Điểm then chốt:** B và C cùng in `outOfMemoryCaught=true` vì cả hai là **cùng Java exception type `OutOfMemoryError`**. Nếu muốn phân loại thật, cần full error message, stack, flags, direct pool and native/process counters, không chỉ `catch` type.

### 13.4. Chương trình demo không phải chiến lược xử lý production

- Không catch rồi tiếp tục vận hành như thể tài nguyên đã hồi phục.
- Không để test thiếu memory vô tình chạy trên shared production host.
- Sau OOME native/memory, VM và application state có thể không còn đủ resources để log/trả response.
- Với stack case, error handler có thể thiếu stack để thực thi phức tạp.
- Vì lab sử dụng limit nhỏ và failed allocations đã được kiểm soát, in hai dòng là chấp nhận được cho mục tiêu giáo dục; **không suy ra một app OOME có thể tự chữa bằng cách catch**.

## 14. Công cụ chẩn đoán: chọn theo error class/detail

### 14.1. Unified Logging cho GC, class và safepoint

Java 17 HotSpot hỗ trợ:

~~~bash
java '-Xlog:gc*=info:file=gc-%p.log:uptime,level,tags:filecount=3,filesize=10M' -Xmx512m AppMain
~~~

`AppMain` chỉ là class minh họa cần thay bằng application thực, không phải một class đã định nghĩa ở phần lab. `gc*=info` có thể ghi GC sub-tags và pause events. Xem:

- GC ID và pause duration.
- Heap occupancy trước/sau GC.
- Những GC cycles thu rất ít memory.
- Concurrent vs stop-the-world phases.
- Allocation pressure và thời gian ứng dụng không tiến triển.

Đừng coi `GC overhead limit exceeded` có thể khẳng định chỉ từ một GC pause dài; cần mức GC overhead và diễn biến live set.

### 14.2. Heap dump on OutOfMemoryError

~~~bash
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/diagnostics \
     -Xmx512m AppMain
~~~

**Các điều kiện vận hành:**

- Thư mục phải tồn tại, writable và đủ disk.
- `.hprof` có thể rất lớn, chứa secrets, personal data, request payloads, access tokens.
- Dump tạo overhead, có thể thất bại nếu process/disk không đủ tài nguyên.
- Nhiều replicas có thể đồng thời tạo nhiều dumps, làm đầy filesystem.
- `HeapDumpOnOutOfMemoryError` là HotSpot diagnostic facility, **không phải đảm bảo mọi OS OOMKilled sẽ có dump**.
- Heap dump giúp phân tích **Java heap retention**; nó không thể hiện đầy đủ JNI malloc/direct native pages như native profiler.

### 14.3. `jcmd` và JMX khi JVM còn sống

~~~text
jcmd
jcmd PID GC.heap_info
jcmd PID GC.class_histogram
jcmd PID VM.native_memory summary scale=MB
jcmd PID VM.flags
jcmd PID Thread.print
~~~

Thay `PID` bằng process ID thật. `GC.class_histogram` và heap dump commands có thể làm workload pause/tốn memory, nên chỉ dùng khi có quyền và kế hoạch impact.

`MemoryMXBean` và `MemoryPoolMXBean` theo dõi heap/non-heap pool usage; `BufferPoolMXBean` theo dõi NIO direct/mapped buffers được VM phơi ra; `ThreadMXBean` giúp đếm Java threads (có giới hạn với virtual threads Java21+); OS tools giúp biết **process/cgroup consumption**.

### 14.4. Native Memory Tracking (NMT)

Khởi động HotSpot với:

~~~bash
java -XX:NativeMemoryTracking=summary -Xmx512m AppMain
~~~

Với process sống:

~~~text
jcmd PID VM.native_memory baseline
jcmd PID VM.native_memory summary.diff scale=MB
~~~

NMT chia các categories như `Java Heap`, `Class`, `Thread`, `Code`, `GC`, `Compiler`, `Other` tùy build. **Reserved ≠ committed ≠ resident**; `Total committed` đã gồm Java heap, không cộng lại.

**NMT không thể bật lại sau khi đã khởi động với `off`**, và **không theo dõi hoàn toàn native allocations từ third-party libraries**. Nếu nghi JNI leak mà NMT flat, dùng OS-native memory tools.

### 14.5. JFR và application metrics

JFR có thể giúp tương quan:
- Allocation pressure, GC cycles, pause times.
- Class loading/classloader activity.
- Threads, CPU samples và exception-related events nếu bật đúng settings.
- Timeline so với application latency/requests.

Một exception detail hiếm không phải bằng chứng đủ. Kết hợp:
- Peak memory/current memory.
- Post-GC live set.
- Request concurrency, cache/queue lengths.
- Native thread count, direct buffer pool.
- Container OOM events và restart counters.

Không bật tất cả trace/logging liên tục mà không giới hạn I/O overhead.

## 15. Ma trận chọn cách xử lý

| Dấu hiệu | Nguồn cạn | Sửa sai thường gặp | Hướng có căn cứ |
| --- | --- | --- | --- |
| `StackOverflowError` | Call stack | Chỉ tăng `Xmx` | Sửa recursion/cycle/depth, cân nhắc `Xss` sau đo |
| `Java heap space` | Java heap allocation | Catch/retry vô hạn | Retained heap graph, queue/cache bounds, sizing |
| `GC overhead limit exceeded` | GC thrashing / heap pressure | Chỉ tắt overhead limit | Xem live set, GC logs, fix leak/working set |
| `Requested array size exceeds VM limit` | Array/object implementation limit | Chỉ tăng heap | Streaming/chunking, kiểm tra lengths |
| `Metaspace` | Class metadata | Chỉ tăng `Xmx` | Classloader retention, metadata cap |
| `Compressed class space` | Compressed Klass metadata space | Tắt class pointers không đo | Kiểm tra class count/loaders/cap/VM build |
| Direct buffer OOME | Direct/native memory budget | Chỉ tăng `Xmx` | Buffer pool lifecycle, direct cap, native pressure |
| `unable to create native thread` | Native thread/PID/stack | Chỉ tăng `Xmx` | Bounded pools, OS pids quota, NMT/OS thread count |
| Native malloc / fatal log | Native/OS memory | Chỉ phân tích Java heap dump | Native allocators, NMT + OS mappings, hs_err |
| Container `OOMKilled` | Cgroup overall memory | Chỉ đọc Java heap used | memory.events/stat/peak, process/native/headroom |

**Tại sao không có một “JVM memory parameter” duy nhất?** Vì JVM tiêu thụ nhiều nguồn tài nguyên có các giới hạn độc lập: heap max, class space, native stacks, direct buffers, process address space, pids quotas, filesystem và các khoản cgroup charge.

## 16. Bài toán ngân sách bộ nhớ trong container

Giả sử container memory limit **1 GiB = 1024 MiB**, ứng dụng cấu hình:

~~~text
-Xmx768m
Platform stacks, Metaspace, JIT Code, GC, JNI,
direct buffers, mapped files, kernel/cache/process overhead
--> còn dưới 256 MiB theo phần limit chưa dành cho heap max
~~~

Đây là **kế hoạch budget sơ bộ**, không phải số RSS đo được. Nếu components ngoài heap đạt 400 MiB tại peak, container có thể vượt giới hạn ngay cả khi Java heap used chưa tới `-Xmx`.

Dự trù bằng nhóm không chồng lắp **ở cấp kế hoạch**:

| Nhóm kế hoạch | MiB giả định |
| --- | ---: |
| Java heap allowance | 512 |
| Direct-buffer allowance | 128 |
| Class metadata/Metaspace allowance | 96 |
| JIT code cache allowance | 64 |
| Native stacks, GC structures, JNI/native libs | 128 |
| OS/cgroup/peak extra headroom | 96 |
| Tổng planned limit | **1024** |

**Không đưa bảng này vào cấu hình production như một phép tính đủ chắc chắn**: các nhóm bao gồm maximums/ước lượng khác nhau, actual RSS/cgroup charges chịu page residency, mappings, allocator overhead, GC behavior và sidecar processes.

Một headroom **bằng 0 trong bảng** cho thấy kế hoạch quá sát giới hạn; cần đo peak, giảm concurrency/footprint hoặc tăng container limit. Cần phân biệt:
- `-Xmx` là upper bound của heap, không phải heap used hiện tại.
- `MaxMetaspaceSize` có thể là một hard cap nội bộ, nhưng GC/internal allocations khác vẫn tồn tại.
- Direct buffer limit không cap third-party JNI allocations.
- Stack reserved không bằng stack resident.
- Page cache/kernel charges có thể tính vào cgroup.

### Câu hỏi capacity quan trọng

1. Có bao nhiêu requests **đồng thời** giữ payloads trong memory?
2. Mỗi request giữ mấy buffers hoặc Java objects qua I/O wait?
3. Thread pool có giới hạn hay cứ mỗi request một platform thread?
4. Java heap live set sau GC tại steady/peak là bao nhiêu?
5. NMT committed và process RSS/PSS/memory.current tại peak thế nào?
6. OOMKill xảy ra ở thời điểm startup, traffic burst, rolling deploy hay steady?
7. Có thể queue/backpressure thay vì giữ mọi request trong memory không?

## 17. Lỗi phổ biến khi vận hành và cách tránh

### 17.1. `catch (Throwable)` rồi trả HTTP 200

Sai: tiếp tục hoạt động sau VM resource exhaustion có thể làm data integrity/logging/synchronization không an toàn. Ưu tiên fail-fast/fail-request theo policy, health/readiness hợp lệ và phục hồi bằng orchestration khi thực sự cần.

### 17.2. Gọi `System.gc()` để “chữa” mọi OOME

Không đủ: `System.gc()` chỉ là request với JVM và có thể bị bỏ qua/tùy collector/flags. Nó không sửa classloader leak, OS pids limit hay memory JNI native cạn. Có thể gây pause và làm tình trạng tệ hơn.

### 17.3. `-Xmx` luôn càng lớn càng tốt

Không trong container nhỏ. Tăng heap maximum làm giảm headroom cho native memory và có thể khiến cgroup OOMKill xuất hiện sớm hơn.

### 17.4. `StackOverflowError` do thiếu heap

Không: nó gắn với stack capacity/call depth, không phải Java heap allocated objects. Object allocations trong recursive method vẫn có thể gây OOME khác, nhưng hai lỗi không đồng nghĩa.

### 17.5. Heap dump luôn giải quyết Metaspace/native leak

Heap dump có thể giúp tìm reachable classloader objects hoặc Java wrappers, nhưng không ghi đầy đủ native allocations. Cần NMT, classloader stats, OS tools và native profilers.

### 17.6. `unable to create native thread` luôn do hết physical RAM

Không: `pids.max`, per-user limits, OS quotas và available process address space đều là candidates.

### 17.7. DirectBuffer OOME thì tăng `Xmx`

Không đúng hướng nếu giới hạn nằm ở `MaxDirectMemorySize` hoặc native allocations. Ngược lại tăng direct cap mà container limit không đổi có thể làm OOMKill.

### 17.8. Không thấy Java stack trace nên chưa có OOM

Sai. Kernel OOM kill hoặc fatal native allocation failure có thể kết thúc tiến trình mà không chạy bất kỳ Java error handler nào.

### 17.9. NMT `reserved` bằng RAM đã tiêu thụ

Sai: reserved virtual address space có thể lớn hơn committed hoặc resident rất nhiều.

### 17.10. Một lỗi `Java heap space` chứng minh leak

Sai: một single object quá lớn hoặc burst hợp lệ vượt heap cap cũng có thể gây lỗi. Cần kiểm tra retained graph và load profile.

### 17.11. Hạ `-Xss` về mức tối thiểu để tạo vô hạn threads

Sai: giảm per-thread stack budget làm tăng nguy cơ stack overflow và không bỏ OS pids/thread scheduling/other native memory constraints.

### 17.12. Chạy lab Native OOME bằng cách tạo hàng trăm nghìn threads

Có thể làm shared host mất khả năng tạo threads cho processes khác. Không dùng thử nghiệm exhaust resources ngoài môi trường cô lập có quotas cứng.

## 18. Quy trình phân loại sự cố trong 15 phút đầu

1. **Xác nhận nguồn phát hiện:** Java stack trace/OOME message, process exit, Kubernetes reason, OS event hay fatal VM log?
2. **Ghi nguyên văn lỗi** gồm class name và detail message; không chỉ screenshot “Out of memory”.
3. **Ghi JDK vendor/version/build, GC, -Xms/-Xmx/-Xss, metaspace/direct flags** và container/pids quotas.
4. **Định vị PID/run/timestamp**; tránh nối dữ liệu từ JVM cũ và container mới sau restart.
5. Nếu StackOverflowError: lấy stack trace rút gọn, tìm recursion/cycle/pathologically deep input, so `-Xss`.
6. Nếu `Java heap space`: GC logs + heap used/max + heap dump (nếu đủ resources/disk), nhìn retained objects.
7. Nếu GC overhead: xem GC time ratios, after-GC occupancy, allocation rate.
8. Nếu Metaspace/Compressed class space: xem class counts, dynamic classloaders, metadata pool usage.
9. Nếu Direct OOME: xem BufferPoolMXBean, native buffers, direct cap, JNI allocations.
10. Nếu native thread OOME: xem thread pool count, OS thread count, pids controller, NMT Thread and stack budgets.
11. Nếu process bị SIGKILL/OOMKilled: xem cgroup `memory.events`, kernel/container log, RSS/peak cgroup usage; **đừng chờ Java exception**.
12. Nếu fatal VM crash: đọc `hs_err_pid` file/OS crash dump và native memory mappings.
13. Đánh giá liệu đây là **single large request**, burst, configuration cap hay leak tăng theo thời gian.
14. Chọn mitigation an toàn: backpressure, giảm load, scale, restart khi process không healthy, lưu bằng chứng.
15. Sau mitigation, **điều tra nguyên nhân và xác nhận bằng regression tests**; không dừng ở “đổi một flag thấy hết lỗi”.

### Khi nào cần cảnh báo mạnh?

- Cgroup memory.current/peak sát limit hoặc `memory.events` có `oom_kill` mới.
- Repeated OOME/restarts, request failures tăng hoặc DB retries làm load tăng.
- Heap live set sau GC tăng không giới hạn qua nhiều windows có workload ổn định.
- Thread count tăng không giới hạn hoặc `pids.current` sát `pids.max`.
- Native RSS tăng trong khi heap/NMT VM-tracked memory không tăng tương ứng.
- GC pauses/overhead cao và service không đáp ứng readiness/SLO.

**Đừng đợi OOME để thu metrics**. Các nguồn memory/threads/events trước lỗi thường quyết định khả năng chẩn đoán.

## 19. Tự kiểm tra: câu hỏi và đáp án

1. **`StackOverflowError` có phải subclass của `OutOfMemoryError` không?** Không; là hai subclasses của `VirtualMachineError`.
2. **`OutOfMemoryError: Metaspace` và `: Java heap space` là hai classes khác nhau?** Không; cùng type OOME, detail messages khác.
3. **`catch (Exception)` có bắt SOE/OOME không?** Không trực tiếp.
4. **`-Xmx` có giới hạn OS RSS không?** Không; chỉ Java heap max.
5. **Stack overflow do recursion vô hạn nên tăng gì đầu tiên?** Sửa recursion/cycle/base case, không chỉ tăng stack size.
6. **`-Xss` quyết định số recursion frames chính xác không?** Không, phụ thuộc frames/JIT/OS.
7. **VT Java 21 có native stack riêng suốt lifetime không?** Không.
8. **Heap used thấp có loại trừ Metaspace OOME không?** Không.
9. **`MaxMetaspaceSize` và `MetaspaceSize` là một không?** Không; hard cap khác initial metadata threshold.
10. **`Compressed class space` tương đương Java heap thiếu?** Không.
11. **`GC overhead limit exceeded` có thể xuất hiện khi GC tốn nhiều thời gian và thu hồi rất ít?** Có, theo heuristic JVM/collector.
12. **Tắt `UseGCOverheadLimit` có sửa memory leak không?** Không.
13. **`Requested array size exceeds VM limit` có thể xảy ra dù heap max lớn?** Có, array implementation limit riêng.
14. **Tăng `Xmx` có tự tăng `MaxDirectMemorySize` theo cách có thể dự đoán cho mọi JVM?** Không dùng quan hệ mặc định thay việc kiểm tra direct cap thực.
15. **BufferPoolMXBean direct đo toàn bộ JNI malloc không?** Không.
16. **OS thiếu `pids.max` headroom có thể gây native thread OOME dù RAM còn?** Có.
17. **Cgroup OOMKilled có thể xảy ra không có Java stack trace?** Có.
18. **Exit 137 có tuyệt đối chứng minh kernel OOM kill?** Không; còn SIGKILL do nguyên nhân khác.
19. **NMT `Total committed` có chứa Java Heap committed không?** Có.
20. **NMT bao phủ mọi third-party native allocation không?** Không.
21. **Heap dump có phải hoàn chỉnh native memory snapshot không?** Không.
22. **`CrashOnOutOfMemoryError`/`HeapDumpOnOutOfMemoryError` có bảo đảm file xuất hiện nếu kernel SIGKILL?** Không.
23. **Trong lab `JvmErrorKindsDemo`, heap và direct case cùng in `outOfMemoryCaught=true`; có chứng minh root cause như nhau?** Không.
24. **Có nên bắt OOME rồi retry cấp phát giống hệt vô hạn?** Không.
25. **Một lần OOME chứng minh leak?** Không.
26. **Muốn điều tra đúng, cần gì ngoài message?** JVM flags, GC/JFR/NMT/OS/cgroup metrics, workload/timeline, dump thích hợp.
27. **Một platform thread `WAITING` có tự giải phóng native stack như thread đã kết thúc không?** Không theo mô hình platform threads.
28. **Đúng hơn: tăng mọi limits hay giảm retained work/backpressure theo bottleneck?** Xác định nguồn cạn thật, rồi chỉnh budget và hành vi workload có kiểm chứng.

## 20. Tài liệu tham khảo (Java SE 17 / HotSpot)

- **Oracle JDK 17 Troubleshooting Guide — Troubleshoot Memory Leaks**, các detail messages của OOME (`Java heap space`, `GC overhead limit exceeded`, `Requested array size exceeds VM limit`, `Metaspace`, `Compressed class space`, native failures): https://docs.oracle.com/en/java/javase/17/troubleshoot/troubleshooting-memory-leaks.html
- **Java SE 17 `StackOverflowError` API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackOverflowError.html
- **Java SE 17 `OutOfMemoryError` API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/OutOfMemoryError.html
- **Java SE 17 `VirtualMachineError` API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/VirtualMachineError.html
- **Oracle JDK 17 `java` command**, `-Xss`, `-Xmx`, `HeapDumpOnOutOfMemoryError`, `ExitOnOutOfMemoryError`, diagnostics: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Oracle JDK 17 `jcmd`**, `GC.heap_info`, `GC.class_histogram`, `VM.native_memory`, `VM.flags`, thread diagnostics: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **Oracle JDK 17 Native Memory Tracking**, scope và giới hạn của NMT: https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
- **Oracle Java SE 17 Garbage Collection Tuning Guide:** https://docs.oracle.com/en/java/javase/17/gctuning/
- **Java SE 17 `BufferPoolMXBean`**, direct/mapped buffers: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/BufferPoolMXBean.html
- **Linux cgroup v2 memory controller**, `memory.current`, `memory.max`, `memory.events`: https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html#memory
- **Linux cgroup v2 pids controller**, `pids.current`, `pids.max`: https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html#pid
- **OpenJDK JEP 444 Virtual Threads (Java 21)**, phân biệt virtual thread logical stacks và native carrier stacks: https://openjdk.org/jeps/444

**Kết luận:** `StackOverflowError` chủ yếu gắn với **call stack và độ sâu lời gọi**, trong khi `OutOfMemoryError` là một type báo **nhiều loại allocation/resource exhaustion** khác nhau: heap, GC overhead, array object size, Metaspace, compressed class space, direct/native memory và native threads. Kernel/container **OOMKill còn có thể xảy ra không ném Java Error**. Bắt đầu từ **tên type + nguyên văn detail message + process/OS evidence**; sau đó dùng đúng công cụ — GC logs/heap dump, JMX/JFR, NMT, thread/pids metrics, cgroup events, native crash diagnostics — để chọn cách sửa. Không biến `-Xmx` thành thuốc chữa mọi memory issue và không catch `Error` như exception nghiệp vụ thường lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-008">ClassNotFoundException, NoClassDefFoundError và lỗi khởi tạo lớp</a>

<details>
<summary>Xem chi tiết</summary>

# ClassNotFoundException, NoClassDefFoundError và lỗi khởi tạo lớp trong Java 17

## 1. Vì sao ba lỗi “không thấy class” không phải một lỗi?

Ứng dụng có thể build thành công, nhưng lúc chạy lại báo `ClassNotFoundException`, `NoClassDefFoundError` hoặc `ExceptionInInitializerError`. Nếu chỉ nhìn một dòng `Could not initialize class` và vội thêm JAR vào classpath, ta có thể bỏ lỡ nguyên nhân thực: **lớp đã được tìm thấy nhưng chạy static initialization thất bại**, hoặc một **dependency gián tiếp** không tồn tại khi JVM cố liên kết lớp.

Muốn điều tra chính xác, cần phân biệt bốn giai đoạn khái niệm trong JVM:

1. **Loading** — tìm định nghĩa bytecode lớp và tạo biểu diễn `Class` cùng class-loader identity.
2. **Linking** — verification, preparation và resolution theo những thời điểm được phép.
3. **Initialization** — chạy các static field initializers và static initializer blocks khi lớp được chủ động sử dụng theo JLS.
4. **Execution** — phương thức/đường sử dụng sau khi lớp có thể được dùng; trong quá trình này có thể xảy ra resolution muộn của references.

```text
Code/reflective name
        │
        ▼
   TÌM ĐỊNH NGHĨA
        │
        ├── Không tìm thấy khi yêu cầu bằng Class.forName/loadClass
        │           ▼
        │     ClassNotFoundException (checked)
        │
        ├── JVM cần định nghĩa một lớp/phụ thuộc nhưng không có
        │           ▼
        │     NoClassDefFoundError (LinkageError)
        │
        ▼
  LINKING: verify / prepare / resolve
        │
        ├── VerifyError, NoSuchMethodError, IllegalAccessError...
        │
        ▼
  INITIALIZATION: <clinit> / static initializers
        │
        ├── Throwable không phải Error
        │           ▼
        │     ExceptionInInitializerError (lần thất bại đầu)
        │
        ├── Error trực tiếp (tùy trường hợp)
        │           ▼
        │     Có thể được ném nguyên Error
        │
        └── Lớp đã bị đánh dấu erroneous sau lần lỗi
                    ▼
          Các lần cố khởi tạo tiếp theo thường có
          NoClassDefFoundError: Could not initialize class ...
```

**Không được coi sơ đồ là thứ tự mọi VM bắt buộc dùng để resolve mọi reference.** Java Virtual Machine Specification (JVMS) cho phép eager/lazy resolution trong những giới hạn rõ ràng. Thời điểm exception nổi lên có thể khác nhau tùy việc JVM cần resolve dependency lúc nào.

**Mục tiêu bài học:** phân biệt *checked exception* và *linkage errors*, nhận diện nguyên nhân gốc trong `Caused by`, tái hiện lỗi bằng Java 17, hiểu trạng thái **erroneous** của lớp sau static initialization thất bại, kiểm tra đúng runtime/JAR/classloader/module path và xử lý production mà không chữa sai bệnh.

## 2. Tóm tắt so sánh nhanh

| Vấn đề | `ClassNotFoundException` | `NoClassDefFoundError` | `ExceptionInInitializerError` |
| --- | --- | --- | --- |
| Nhóm kế thừa | `Exception` (checked) | `LinkageError` → `Error` | `LinkageError` → `Error` |
| Thường xuất hiện | Yêu cầu **tìm lớp theo tên** nhưng loader không tìm được | JVM/loader cần định nghĩa lớp không tìm thấy, **hoặc lớp đã không thể khởi tạo** | Static initializer/field initializer ném throwable không phải `Error` |
| Ví dụ | `Class.forName("pkg.Plugin")`, `loader.loadClass(name)` | Missing transitive superclass/JAR; subsequent use của class erroneous | `static final Config C = parseInvalidConfig();` |
| Có thể xảy ra dù `.class` của lớp đang gọi tồn tại? | Có | Có | Có |
| Có thể do classpath/dependency? | Có | Có | Có thể gián tiếp, nhưng nguyên nhân trực tiếp là initializer |
| Có thể khắc phục bằng chỉ thêm missing JAR? | Nếu thực sự thiếu lớp và đúng loader | Nếu thiếu dependency; không chắc khi class erroneous | Chỉ khi initializer thất bại vì dependency và khởi động lại hợp lệ |
| Bằng chứng trước tiên | Requested name, loader, classpath/module path | **Full stack + cause + thời điểm lỗi đầu** | **Full cause chain từ lỗi đầu tiên**, static initialization code |
| Sau một lần khởi tạo thất bại | Không áp dụng quy luật erroneous riêng cho lớp không tìm thấy | Có thể xuất hiện trên lần dùng sau của lớp erroneous | Thường xuất hiện ở lần đầu nếu cause là non-Error |
| Nguyên tắc sửa | Sửa tên/loader/classpath/deploy | Sửa missing class hoặc root cause init, redeploy/restart thích hợp | Sửa initializer/config/dependency gây lỗi, restart hoặc thay loader identity |

**Điểm phân biệt quan trọng nhất:** `NoClassDefFoundError` **không luôn có nghĩa một file `.class` không tồn tại**. Nó còn là kết quả thông dụng khi một lớp đã được JVM tìm thấy nhưng **không thể khởi tạo** trong lần trước.

## 3. Vì sao cần đọc nguyên cả stack trace, không chỉ loại exception?

Hãy đối chiếu hai thông điệp giả lập:

```text
java.lang.NoClassDefFoundError: com/example/codec/Codec
    ...
Caused by: java.lang.ClassNotFoundException: com.example.codec.Codec
    ...
```

Đây là ví dụ **thiếu định nghĩa**: class loader/JVM không tìm được bytecode cần thiết, thường là runtime dependency hoặc loader context không chứa JAR.

Trường hợp thứ hai:

```text
java.lang.NoClassDefFoundError: Could not initialize class com.example.Config
    ...
Caused by: java.lang.ExceptionInInitializerError: ...
    ...
Caused by: java.lang.IllegalStateException: missing environment variable
    ...
```

Đây là ví dụ **khởi tạo thất bại**: lớp `Config` từng được tìm thấy, nhưng một code path trong static initializer ném exception, JVM đã ghi nhận class ở trạng thái erroneous trong class-loader identity đó.

Cả hai block trên là **minh họa hình thức lỗi**, không phải transcript được đo của một chương trình cụ thể: message/line number/cause wrapping có thể khác JDK/build và thời điểm.

### 3.1. Cách đọc `Caused by`

- **Top-level throwable**: lỗi người gọi hiện tại nhìn thấy.
- **`Caused by` chain**: các exceptions/errors làm một thao tác con thất bại.
- **Frame đầu tiên thuộc mã ứng dụng**: điểm bắt đầu phân tích phù hợp, không nhất thiết là root cause.
- **Lần lỗi sớm nhất theo log/timestamp**: đặc biệt quan trọng cho `ExceptionInInitializerError`. Nếu log hiện tại chỉ còn `NoClassDefFoundError: Could not initialize class`, lần lỗi đầu có thể đã xảy ra trước đó.
- **Class-loader và runtime process identity**: một JVM có nhiều loaders có thể có nhiều định nghĩa của cùng binary name, nên chuỗi tên lớp đơn độc chưa định danh đầy đủ.

**Không tự động chọn throwable ở dòng cuối cùng rồi tuyên bố nó là root cause duy nhất.** Cần kiểm tra cả lỗi kinh doanh, cấu hình, mạng, file và class-loading graph.

## 4. Class loading, linking và initialization: khung chuẩn cần nhớ

### 4.1. Loading

Một JVM class được nhận diện bởi **binary name + defining class loader**, không chỉ bởi tên `com.example.Widget`. Hai loaders khác nhau có thể định nghĩa hai classes cùng binary name nhưng không phải cùng một runtime class.

Sources mà loader có thể sử dụng tùy loại:

- Boot classes trong runtime image/modules.
- Application class path.
- Module path.
- Custom class loader của application server/plugin system.
- Nested JAR loader của framework.
- In-memory generated bytecode/class instrumentation theo cơ chế riêng.

`ClassNotFoundException` là một **checked exception** mà API loading theo tên như `Class.forName` công bố. Java code thường cần catch hoặc khai báo `throws`. Việc tên lớp được gửi bằng string làm compiler không thể kiểm tra chắc lớp ấy có mặt lúc chạy.

### 4.2. Linking

Theo JVMS `5.4`, linking bao gồm:

- **Verification:** kiểm tra class file constraints/type safety; lỗi tương ứng có thể là `VerifyError`.
- **Preparation:** tạo/chuẩn bị static fields với **default values**; chưa chạy explicit static initializer.
- **Resolution:** chuyển symbolic references từ constant pool thành references thực đối với class, field, method, method handle và các cấu trúc liên quan.

Resolution có thể **lazy** hoặc **eager** theo implementation, miễn tuân thủ chuẩn. Một method có thể chạy tới một instruction lần đầu mới lộ `NoSuchMethodError` hoặc `NoClassDefFoundError` liên quan dependency.

### 4.3. Initialization

Initialization chạy các static field initializers và static blocks theo quy tắc JLS `12.4`, ví dụ:

```java
final class Settings {
    static int count = calculate(); // Gọi khi class được initialize.
    static {
        validate();                  // Static initializer.
    }
    // calculate() và validate() cần được khai báo để snippet compile.
}
```

Đây là snippet **chưa phải file độc lập**. Nếu initializer ném một `RuntimeException` hoặc exception khác không phải `Error`, JVM thường gói thành `ExceptionInInitializerError` khi thất bại lần đầu.

Class initialization là một quá trình được phối hợp giữa threads. Nếu khởi tạo thất bại, JVM ghi nhận lớp ở trạng thái **erroneous**, không chỉ “đã xảy ra một exception trong constructor”. Các lần khởi tạo khác của **cùng runtime class** không được chạy lại static initializer như một lần đầu mới.

### 4.4. `<clinit>` là gì?

Trong class file, compiler có thể tạo class initialization method `<clinit>` chứa code từ static field initializers và static blocks theo thứ tự phù hợp. `<clinit>` do JVM quản lý; Java source không định nghĩa trực tiếp một method tên `<clinit>`.

Dùng:

```bash
javap -c -p InitializationFailureDemo
javap -c -p 'InitializationFailureDemo$Broken'
```

Dạng quote single-quoted name ở câu lệnh trên áp dụng POSIX shells; trong Windows PowerShell cũng có thể quote tên chứa `$` để tránh expansion. Ví dụ runnable đầy đủ ở phần sau.

**Quan trọng:** không phải mọi static field đều gây `<clinit>` có hành vi thực thi tùy cách Java compiler xử lý constants. Và `javap` cho thấy bytecode lưu trong class file, **không phải machine code HotSpot C2/JIT đã tối ưu**.

## 5. Khi nào `ClassNotFoundException` xuất hiện?

Java SE 17 API `Class.forName(String)` và `Class.forName(String, boolean, ClassLoader)` có `throws ClassNotFoundException` khi lớp có binary name yêu cầu không thể được tìm thấy trong loader context.

Ví dụ cần catch:

```java
try {
    Class<?> type = Class.forName("com.example.optional.Plugin");
    // type có thể dùng nếu lớp hiện diện và khởi tạo thành công.
} catch (ClassNotFoundException exception) {
    // Optional plugin không được tìm thấy ở loader context.
}
```

Đây là snippet, không độc lập. `Class.forName(String)` theo Javadoc tương đương form có `initialize=true` và loader context caller, **nên việc tìm thấy class chưa đảm bảo initializer thành công**. Bên cạnh `ClassNotFoundException`, API còn có thể ném `LinkageError` và `ExceptionInInitializerError` theo tình huống.

### 5.1. Trường hợp thường gặp

- Sai **fully qualified binary name**: `com.company.Drive` khác `com.company.Driver`.
- Đặt class trong package khác hoặc đổi namespace lúc refactor.
- Dependency chỉ tồn tại trên **compile classpath** nhưng bị loại khỏi runtime artifact.
- Custom class loader không nhìn thấy JAR hoặc module mong đợi.
- Runtime classpath thiếu dependency runtime-scoped.
- Code reflection đi tìm lớp tùy chọn vốn không được deploy.
- Module/bundle/plugin có visibility không như giả định.
- Lớp tồn tại nhưng được tìm bằng loader khác với loader đã define nó.

**Không suy** mọi `ClassNotFoundException` đều là bug nghiêm trọng: một framework có thể chủ động thử tìm một optional provider, catch exception và fallback bình thường. Cần xem code caller và error policy.

### 5.2. `ClassLoader.loadClass` và `Class.forName` có khác nhau?

| API | Mục tiêu | Initialization mặc định |
| --- | --- | --- |
| `Class.forName(name)` | Tìm class theo tên với caller loader context | **Có**, nếu chưa initialized |
| `Class.forName(name, true, loader)` | Tìm qua loader chỉ định | **Có** |
| `Class.forName(name, false, loader)` | Tìm/nạp class nhưng không yêu cầu initialize ngay | **Không** trong chính yêu cầu |
| `loader.loadClass(name)` | Yêu cầu loader nạp lớp | **Không yêu cầu** chạy static initializer ngay |
| `Class.forName(module, name)` | Tìm trong module chỉ định | Trả `null` nếu không thấy theo contract; semantics khác overload `String` |

**Đừng trộn API contract:** `Class.forName(Module,String)` trả `null` khi không tìm được, không phải một dạng `throws ClassNotFoundException` giống overload theo string.

`Class.forName(name,false,loader)` hữu ích để tách **“loader có tìm được lớp?”** và **“class có initialize thành công?”**, dù linking/resolution vẫn có thể phát sinh `LinkageError`. Nó không nên được coi là “kiểm tra an toàn mà không bao giờ có side effect”: classloader tùy biến vẫn có thể làm I/O, load dependencies hoặc thực thi custom code.

### 5.3. Java 17 demo 1 — checked `ClassNotFoundException`

Tạo file **`FindClassDemo.java`**:

```java
public final class FindClassDemo {
    public static void main(String[] args) {
        String missing = "lab.missing.NotIncludedInThisLab";

        try {
            Class.forName(missing);
            System.out.println("classFound=true");
        } catch (ClassNotFoundException ex) {
            System.out.println("classNotFound=true");
            System.out.println("requested=" + missing);
        }
    }
}
```

Biên dịch/chạy với JDK 17:

```bash
javac --release 17 FindClassDemo.java
java FindClassDemo
```

**Stdout xác định trong lab không cài thêm lớp cố ý được đặt tên `lab.missing.NotIncludedInThisLab`:**

```text
classNotFound=true
requested=lab.missing.NotIncludedInThisLab
```

Giải thích:

1. Compiler không cần class `lab.missing.NotIncludedInThisLab` vì nó chỉ là **chuỗi**.
2. Runtime `Class.forName` đi tìm lớp ở caller loader context.
3. Khi không thấy, method ném `ClassNotFoundException`; code catch checked exception và xử lý.
4. Không có nghĩa lớp đã từng được JVM khởi tạo rồi trở nên erroneous.
5. Nếu ai đó thêm đúng lớp mang tên ấy vào runtime path, kết quả có thể thay đổi; đây là test có điều kiện đầu vào rõ ràng.

**Lưu ý:** Không nhầm với `FileNotFoundException` — classpath/classloader không đơn thuần là một path tới một file trên disk, nhất là trong modular/runtime image hoặc custom loader.

## 6. Khi nào `NoClassDefFoundError` xuất hiện?

Javadoc Java 17: `NoClassDefFoundError` là `LinkageError` báo JVM hoặc một `ClassLoader` cố load định nghĩa một lớp để thực thi một thao tác và **không thể tìm định nghĩa ấy**. Điển hình: khi mã được compile, lớp cần thiết tồn tại; tới runtime, bytecode/JAR/phụ thuộc không còn ở loader context thích hợp.

Nhưng trong thực tế còn có trường hợp cực kỳ quan trọng: **class được tìm thấy nhưng initialization đã thất bại**, sau đó VM ném `NoClassDefFoundError` với thông điệp dạng `Could not initialize class ...` cho những lần thử tiếp theo.

Không thể đưa ra chẩn đoán chỉ từ tên throwable. Cần phân biệt:

- `NoClassDefFoundError: com/acme/Service` — có thể thiếu định nghĩa class hoặc dependency.
- `NoClassDefFoundError: Could not initialize class com.acme.Service` — có thể class đang erroneous do lỗi static initializer đã xảy ra.
- `NoClassDefFoundError` có `Caused by: ClassNotFoundException` — loader không tìm được lớp cần thiết.
- `NoClassDefFoundError` có cause liên quan initialization — xem **lỗi đầu tiên** và trạng thái erroneous.

### 6.1. Tại sao compile được rồi runtime lại lỗi?

Xét ba files:

```text
Main.class
Plugin.class         (extends MissingBase)
MissingBase.class    (có lúc compile, mất lúc runtime)
```

Compiler kiểm tra `Plugin extends MissingBase` hợp lệ vì đủ classes. Sau deploy, `MissingBase.class` bị bỏ quên. Khi JVM phải load `Plugin`, nó còn phải có định nghĩa superclass và có thể ném `NoClassDefFoundError` vì thiếu `MissingBase`.

**Không chỉ tìm JAR chứa `Plugin`.** Trong nhiều errors, lớp hiển thị sau dấu `/` là một **phụ thuộc gián tiếp** cần được nạp để hoàn tất linkage.

### 6.2. Java 17 demo 2 — thiếu superclass khi runtime

Tạo ba files cùng thư mục làm việc, không khai báo package.

**`MissingBase.java`:**

```java
public class MissingBase {
}
```

**`Plugin.java`:**

```java
public class Plugin extends MissingBase {
}
```

**`MissingDependencyDemo.java`:**

```java
public final class MissingDependencyDemo {
    public static void main(String[] args) {
        try {
            Class.forName("Plugin");
            System.out.println("pluginLoaded=true");
        } catch (ClassNotFoundException ex) {
            System.out.println("pluginNotFound=true");
        } catch (NoClassDefFoundError ex) {
            System.out.println("missingDependency=true");
        }
    }
}
```

**Bước 1 — Compile đủ ba classes:**

```bash
javac --release 17 MissingBase.java Plugin.java MissingDependencyDemo.java
java MissingDependencyDemo
```

**Stdout khi tất cả classes hiện diện và không có lỗi khác:**

```text
pluginLoaded=true
```

**Bước 2 — Trong thư mục LAB, cố ý bỏ đúng dependency** (lệnh POSIX; không áp dụng trên production):

```bash
rm MissingBase.class
java MissingDependencyDemo
```

**Stdout dự kiến trên JVM Java 17 chuẩn khi `Plugin.class` còn nhưng `MissingBase.class` không tồn tại:**

```text
missingDependency=true
```

Giải thích:

1. `MissingDependencyDemo` chỉ tham chiếu tên `Plugin` bằng string trong `Class.forName`, để mã gọi vẫn tự load được.
2. JVM tìm thấy `Plugin.class`, nhưng định nghĩa của superclass `MissingBase` không còn.
3. Lúc load/link `Plugin`, JVM cần superclass và ném `NoClassDefFoundError`.
4. Nếu đặt lại đúng `MissingBase.class` và chạy **một JVM process mới**, lab trở lại kết quả `pluginLoaded=true`.
5. Demo dùng thư mục classes để dễ xóa file; **production nên điều tra JAR artifact và build/deployment**, không xóa class files của service thật.

**Giới hạn:** Message/stack frames cụ thể có thể khác vendor/JVM build; ta chỉ khẳng định nhánh catch khi các đầu vào của lab đúng. Các JVM có quyền chọn thời điểm resolution trong nhiều trường hợp, nhưng superclass là quan hệ thiết yếu khi định nghĩa subclass.

### 6.3. Tại sao không catch `ClassNotFoundException` là đủ?

`ClassNotFoundException` là checked exception, còn `NoClassDefFoundError` là **Error**. Chúng không cùng một nhánh kế thừa để một catch CNFE tự bao hết.

```java
try {
    Class.forName("Plugin");
} catch (ClassNotFoundException e) {
    // Không bắt NoClassDefFoundError.
}
```

Snippet này compile nếu lớp có thể được tìm theo string; nhưng khi thiếu superclass nó không bắt `NoClassDefFoundError`. **Không khuyên catch `Throwable` chung cho mọi lỗi ở production**; Errors thường biểu thị vấn đề ở tầng VM/linkage cần fail-fast hoặc xử lý boundary cực kỳ có chủ đích.

### 6.4. Khi class bị thiếu ở một JAR dependency “transitive”

Giả sử app phụ thuộc vào `engine.jar` và `engine.jar` cần `codec.jar`. Compile/dev environment có cả hai, nhưng deployment chỉ có `engine.jar`. JVM có thể ném NCDFE đối với một class trong `codec.jar` khi code path lần đầu thực hiện.

Điều tra:

- Xác định binary name sau dấu `NoClassDefFoundError`, đổi `/` sang `.` để tìm JAR.
- Xem **dependency graph** và runtime scopes/exclusions.
- Kiểm tra JAR thực sự được ship, không chỉ dependency lockfile.
- Check `Class-Path` trong manifest nếu launcher dựa vào nó.
- Kiểm tra classloader delegation và module visibility khi framework dùng nhiều loaders.

## 7. `ExceptionInInitializerError` và lớp erroneous

Khi một class lần đầu được **initialize**, JVM thực hiện static initialization theo thứ tự. Nếu initialization kết thúc abruptly bởi một throwable:

- Nếu throwable **không phải** instance của `Error`, JVM có thể tạo `ExceptionInInitializerError` để bọc throwable và báo lần thất bại đó.
- Nếu throwable vốn là `Error`, nguyên tắc JVMS/JLS cho phép ném trực tiếp error đó trong đường khởi tạo, không nhất thiết bọc lại.
- JVM đánh dấu class là **erroneous** trong bản định nghĩa/runtime identity ấy và thông báo threads khác đang đợi initialization.
- Những lần cố initialize lại class đó **không chạy lại** static initializer trong cùng runtime class identity; thường ném `NoClassDefFoundError`.

**Đây là vấn đề state**, không phải việc file class biến mất. Có thể thấy `Config.class` và `ConfigLoader.class` vẫn nằm đúng JAR nhưng lỗi `Could not initialize class` vẫn tái diễn trong process.

### 7.1. Ví dụ initializer thất bại do config

```java
final class Configuration {
    static final String ENDPOINT = readRequiredEndpoint();

    private static String readRequiredEndpoint() {
        String value = System.getenv("SERVICE_ENDPOINT");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("SERVICE_ENDPOINT is missing");
        }
        return value;
    }
}
```

Đoạn trên là snippet class; có thể compile nếu đặt trong file phù hợp. **Chỉ lúc code chủ động sử dụng class theo quy tắc initialization**, `ENDPOINT` mới được tính. Nếu env thiếu, throwable gốc là `IllegalStateException`, JVM có thể bọc thành `ExceptionInInitializerError`.

**Lưu ý:** Giá trị env thường được đọc trong process; set env sau khi process đã bắt đầu không chắc giúp code này vì process environment và class-state đã cố định. Nếu class trở thành erroneous, phải sửa config và tạo **process/class definition mới phù hợp**, không mong đơn thuần gọi lại getter là nó tự hồi phục.

### 7.2. Java 17 demo 3 — lần đầu EIIE, lần hai NCDFE

Lưu file **`InitializationFailureDemo.java`**:

```java
public final class InitializationFailureDemo {
    static final class Broken {
        static final int VALUE = initialize();

        private static int initialize() {
            throw new IllegalStateException("configuration missing");
        }
    }

    public static void main(String[] args) {
        try {
            int ignored = Broken.VALUE;
            System.out.println("unexpected=" + ignored);
        } catch (ExceptionInInitializerError ex) {
            System.out.println("firstError=" +
                    ex.getClass().getSimpleName());
            System.out.println("cause=" +
                    ex.getCause().getClass().getSimpleName());
        }

        try {
            int ignored = Broken.VALUE;
            System.out.println("unexpectedAgain=" + ignored);
        } catch (NoClassDefFoundError ex) {
            System.out.println("secondError=" +
                    ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy **một JVM process mới** dùng Java 17:

```bash
javac --release 17 InitializationFailureDemo.java
java InitializationFailureDemo
```

**Stdout chính xác khi chạy đúng file/VM Java 17 thông thường:**

```text
firstError=ExceptionInInitializerError
cause=IllegalStateException
secondError=NoClassDefFoundError
```

Tại sao?

1. `Broken.VALUE` không phải compile-time constant (`initialize()` là method call).
2. Lần đầu đọc `Broken.VALUE` là một active use buộc initialization của `Broken`.
3. `initialize()` ném `IllegalStateException` nên VM gói vào `ExceptionInInitializerError`; `getCause()` là exception gốc.
4. Class `Broken` bị đánh dấu erroneous cho defining loader của nó.
5. Lần dùng tiếp theo trong **cùng process và cùng class identity** ném `NoClassDefFoundError`, dù `Broken.class` vẫn tồn tại.
6. Một **process Java mới** chạy lại thí nghiệm sẽ tái lập trạng thái ban đầu, vì class initialization state không tồn tại xuyên process.

**Đừng xóa JAR rồi copy lại để cố “chữa” lỗi thứ hai trong process cũ.** Cần sửa nguyên nhân đầu (ở ví dụ là method cố ý ném exception) và dùng lifecycle/loader strategy thích hợp.

### 7.3. Dòng quan trọng là lần lỗi đầu tiên

Nếu lần đầu lỗi được logging system ghi trước giờ cao điểm, còn lần thứ hai chỉ in `NoClassDefFoundError: Could not initialize class Broken`, người trực production có thể nhầm đây là lỗi thiếu dependency. Hãy tìm:

- Throwable đầu tiên trong JVM process lifetime.
- `Caused by` của `ExceptionInInitializerError`.
- Static initializer/source file tương ứng.
- Config files/env vars/secrets/permission cần thiết lúc khởi tạo.
- Điều kiện mạng/downstream có thể thay đổi giữa deploy và startup.

## 8. Cái gì thực sự kích hoạt initialization?

Theo JLS `12.4.1`, initialization thường diễn ra trước các hành vi như:

- Tạo một instance bằng `new SomeClass()`.
- Gọi một `static` method được khai báo trong class.
- Gán một `static` field của class.
- Đọc một `static` field **không phải constant variable**.
- Một số hành vi reflective theo API liên quan.
- Initialization của superclass/superinterfaces liên quan theo quy tắc Java.

**Không phải mọi lần đề cập tên class đều initialize.** Đặc biệt, đọc một **compile-time constant variable** có thể không kích hoạt initialization của declaring class.

### 8.1. Java 17 demo 4 — compile-time constant vs nonconstant static

Lưu file **`InitializationTriggerDemo.java`**:

```java
public final class InitializationTriggerDemo {
    static final class Example {
        static final String LABEL = "ready";
        static final Integer NUMBER = Integer.valueOf(42);

        static {
            System.out.println("initialized=true");
        }
    }

    public static void main(String[] args) {
        System.out.println("constant=" + Example.LABEL);
        System.out.println("nonconstant=" + Example.NUMBER);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 InitializationTriggerDemo.java
java InitializationTriggerDemo
```

**Stdout chính xác với Java 17 compilation:**

```text
constant=ready
initialized=true
nonconstant=42
```

Giải thích:

1. `LABEL` là `static final String` với compile-time constant expression `"ready"`.
2. Compiler có thể đưa giá trị này trực tiếp vào bytecode của caller mà không cần initialize `Example`.
3. `NUMBER` là `Integer`, không phải constant variable theo JLS; truy cập field buộc `Example` initialization.
4. Static initializer in `initialized=true` đúng **một lần** trước khi `NUMBER` được đọc và in.
5. Nếu bỏ dòng đọc `NUMBER`, chương trình có thể không chạy static initializer của `Example` chỉ vì dùng `LABEL`.

**Bài học điều tra:** Một application có thể đọc vài hằng số thành công, nhưng khi sau đó lần đầu gọi method/field khác mới gặp `ExceptionInInitializerError`. Đừng kết luận class “đã khởi tạo OK” chỉ vì một constant có thể in ra.

### 8.2. `Class.forName` có thể ép initialization

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();
Class<?> c = Class.forName("com.example.Config", false, loader);
// Request load, không ép initialize ở đây.

Class.forName("com.example.Config", true, loader);
// Yêu cầu initialize (nếu chưa từng initialized và class phù hợp).
```

Đoạn này là snippet; cần class và loader có thể tìm được. Nếu class không hiện diện, cả hai đường có thể ném `ClassNotFoundException`. Nếu initializer thất bại, lời gọi `true` có thể ném `ExceptionInInitializerError`. Nếu static initialization đã thất bại trước đó, lời gọi `true` có thể ném `NoClassDefFoundError`.

**Cảnh báo:** Context class loader có thể là `null` hoặc khác loader định nghĩa caller; chọn loader phải theo protocol framework thay vì tự mặc định. Nếu `loader==null` ở overload này, class được nạp bằng bootstrap loader theo API, không phải fallback tùy ý tới application loader.

## 9. Lỗi initialization có được JVM tự thử lại không?

Với **cùng runtime class identity** (binary name + defining class loader), sau khi initialization kết thúc thất bại, class ở trạng thái erroneous. Các lần gọi tiếp theo không tự “retry từ đầu” static block chỉ vì:

- File config đã được sửa.
- System property đã đổi.
- Class path có thêm JAR về sau.
- Method được gọi lại sau vài giây.
- Một thread khác chạm tới class.
- Garbage collector vừa chạy.

Trong một số kiến trúc plugin hot reload, một **defining class loader mới** có thể tạo runtime class identity mới, và class ấy có thể initialize lại. Nhưng đó không phải sửa lỗi tự động: đổi loader còn liên quan các instances, API boundaries, caches, static holders, threads và class unloading.

**Với đa số dịch vụ:** sửa root cause và **restart/redeploy đúng runtime process** là cách ít nhập nhằng hơn cố làm sống lại class erroneous trong cùng process.

### 9.1. Nhiều threads cùng khởi tạo class

JLS `12.4.2` mô tả synchronization của initialization:

- Nếu một thread đang initialize class, thread khác cần initialize cùng class có thể phải đợi.
- Nếu khởi tạo thành công, chúng nhìn class ở trạng thái initialized theo ngữ nghĩa memory/synchronization chuẩn.
- Nếu khởi tạo thất bại, class erroneous được ghi nhận; threads bị ảnh hưởng không nhận một cuộc retry độc lập thành công.
- Thứ tự log giữa threads không bảo đảm thread nào in `ExceptionInInitializerError` trước trong mọi application, nếu nhiều đường lỗi lẫn lộn.

Vì thế khi điều tra production cần **timestamp + thread ID + full first failure**, không chỉ số đếm loại throwable trong metrics.

## 10. Lỗi static initializer do environment, config và side effects

Các static initializer quá nhiều việc thường làm ứng dụng mong manh:

```java
final class ExpensiveBootstrap {
    static final RemoteClient CLIENT = connectToRemoteService();
    static final Configuration CONFIG = loadConfigFromDisk();
}
```

Đây là snippet **chưa độc lập**; types/methods cần định nghĩa. Nó minh họa cách startup có thể thất bại chỉ vì network tạm thời unavailable, file permissions không đúng, secrets chưa mount, DNS chưa sẵn sàng hoặc parsing exception.

Nếu constructor/method bên trong static init ném unchecked exception, caller có thể nhìn `ExceptionInInitializerError`; và lớp có thể trở thành erroneous.

### 10.1. Vì sao static initialization nên đơn giản?

Hướng dẫn:

- Ưu tiên static fields cho **constants, immutable deterministic values** có thể tính an toàn.
- Di chuyển I/O, credentials, network connect, database migrations sang **explicit initialization lifecycle** có errors/retry/timeout rõ ràng.
- Khi đọc required config, validate sớm ở startup với thông điệp chính xác, không log secrets.
- Đặt class initialization error trong một application startup boundary có logging đầy đủ.
- Tránh giữ state mutable quá rộng trong static singletons khó reset/test.
- Nếu cần lazy initialize với retry, dùng một thành phần quản lý lifecycle và concurrency protocol phù hợp thay vì mong JVM tự retry `<clinit>`.
- Thiết kế fallback policy rõ ràng: config bắt buộc mà thiếu thì fail startup; optional subsystem có thể disable hợp lệ.

### 10.2. Không chữa `ExceptionInInitializerError` bằng cách bắt rồi chạy tiếp bừa

```java
try {
    ServiceBootstrap.touch();
} catch (ExceptionInInitializerError ignored) {
    // Rất nguy hiểm nếu các code path khác còn cần class này.
}
```

Snippet này chỉ minh họa anti-pattern. Nếu class đã erroneous, những code paths khác vẫn có thể gặp NCDFE. Hệ thống có thể khởi động ở trạng thái **partial initialization** và lỗi xảy ra xa hơn về sau.

Nếu thật sự cô lập một optional plugin, xử lý errors ở boundary rõ ràng, không để code chung chạm vào class đó, ghi sự kiện đầy đủ, giải phóng resources và thông báo degraded mode.

## 11. Classpath/Runtime packaging: compile được không có nghĩa chạy được

Một module/JAR chỉ được đảm bảo có **compile-time inputs** tại lúc build. Lúc chạy thật, launcher, classpath, modulepath và custom loaders quyết định classes có thể được tìm thấy hay không.

### 11.1. Những sai lệch phổ biến

- Một dependency dùng Maven/Gradle scope `provided`/`compileOnly` nên build OK nhưng runtime artifact không chứa nó.
- Fat/shaded JAR bỏ thiếu class hoặc đè một dependency version khác.
- Một `jar-with-dependencies` thay package tên nhưng reflective class name trong config chưa đổi.
- Plugin JAR nằm trong folder không được custom loader scan.
- Class không có trong custom `jlink` runtime image/module graph.
- Docker builder stage có đủ JARs nhưng runtime stage bỏ sót.
- Container layer khác path/case sensitivity với developer OS.
- Cùng tên `package.Class` có hai JAR versions, classpath order chọn lớp không mong đợi.
- Application server dùng child-first loader, trong khi code giả định parent-first.
- Native/agent instrumentation thay bytecode lúc load.
- Multi-release JAR chọn class khác theo JDK runtime feature.
- Dependency bị exclude gián tiếp; transitive dependency vẫn được compiler/tool resolve lúc build nhưng mất lúc deploy.

### 11.2. Classpath order và binary compatibility

Nếu application được compile với `codec-2.0.jar` nhưng runtime chọn `codec-1.0.jar`, có thể:

- `NoSuchMethodError` hoặc `NoSuchFieldError` nếu API binary không còn đúng.
- `IllegalAccessError` nếu visibility không còn tương thích.
- `IncompatibleClassChangeError` nếu class/interface/field/method contract khác.
- `NoClassDefFoundError` nếu cần một lớp chỉ tồn tại trong version 2.0.
- `ClassCastException` khi class identity/loader boundaries sai, dù tên class trông giống nhau.

**Không quy mọi LinkageError thành NCDFE.** Các subclasses thông báo nhiều loại mismatch khác nhau; phải xử lý đúng subtype/cause.

### 11.3. Build/runtime JDK version mismatch

Ví dụ một class compile bằng JDK 21 mặc định có class file major version 65; runtime Java 17 chỉ hỗ trợ major 61. Khi gặp class version mới hơn runtime, lỗi thích hợp thường là `UnsupportedClassVersionError` — cũng là một `ClassFormatError`/`LinkageError` family, **không phải** `ClassNotFoundException`.

Dùng:

```bash
javac --release 17 Main.java
javap -verbose Main | grep 'major version'
```

Trên POSIX shell, `grep` lọc output. Trên Windows, dùng cách lọc tương đương. `Main.java`/class cần tồn tại. `--release 17` áp dụng khi compiler hỗ trợ target 17. Nếu class version sai, **chạy lại đúng build artifact** sau điều chỉnh toolchain, không đổi tên exception.

## 12. Class loader identity: same name chưa chắc cùng lớp

JVM định danh lớp bởi **binary name + defining loader**. Một application server có hai plugin loaders L1 và L2 có thể cùng define `com.example.Api`:

```text
             Parent/shared loader
                  │
            ┌─────┴─────┐
            │           │
           L1           L2
            │           │
    com.example.Api  com.example.Api
    (identity A)     (identity B)
```

Mặc dù tên giống nhau, Java runtime types khác và ép kiểu qua boundary có thể gây `ClassCastException`; reflection qua loader không phù hợp có thể gây `ClassNotFoundException`. Trong trường hợp runtime cần nạp một phụ thuộc từ loader không nhìn thấy, NCDFE cũng có thể xuất hiện.

### 12.1. Những thông tin cần thu khi nghi loader mismatch

- Requested class **binary name**.
- Caller class và defining loader.
- `Thread.currentThread().getContextClassLoader()` (context loader).
- Loader hierarchy/delegation policy (parent-first/child-first/custom).
- URL/JAR/resources mà mỗi loader thực sự quét được.
- Module layer và exports/readability khi module system liên quan.
- Runtime class identity/JDK vendor nếu framework có custom instrumentation.

Trong Java 17, có thể in thông tin loader khi debug:

```java
Class<?> clazz = java.util.ArrayList.class;
System.out.println("loader=" + clazz.getClassLoader());
```

Đây là snippet, không file độc lập. `java.util.ArrayList` thường được platform/bootstrap classes load, nên `getClassLoader()` có thể là `null`. **`null` không có nghĩa class không tồn tại**; nó thường biểu thị bootstrap-defined class.

### 12.2. `Class.forName` của caller vs context loader

Một framework dùng thread context class loader cho SPI/plugins, còn business code dùng `Class.forName(name)` với caller loader. Nếu hai loaders nhìn thấy những JAR khác nhau, cùng một tên có thể tìm được bằng API này nhưng không tìm được bằng API kia.

**Không sửa bằng cách đặt context loader tùy tiện cho cả process**: loader visibility có thể thay đổi và gây class identity conflicts. Xác định API/framework contract và loader của boundary cụ thể.

## 13. Module system từ Java 9: “có JAR” chưa đủ

Ở Java 17, module path và named modules bổ sung các điều kiện:

- Module cần được resolve vào module graph của runtime.
- Readability/exports/opens quyết định các loại truy cập hợp lệ.
- Custom runtime image có thể thiếu module chứa class cần thiết.
- Reflection có thể thất bại do strong encapsulation dù bytecode class có mặt.

Không kết luận `ClassNotFoundException` luôn do “không có file trên disk” trong modular deployment. Đồng thời cần phân biệt những lỗi truy cập:

| Tình huống | Throwable điển hình | Hướng kiểm tra |
| --- | --- | --- |
| Không tìm được class theo string/loader | `ClassNotFoundException` | Loader/module/classpath |
| JVM không tìm được định nghĩa dependency | `NoClassDefFoundError` | Binary name, JAR/module/loader |
| Runtime thiếu class version/API yêu cầu | `UnsupportedClassVersionError` hoặc linkage subtype | JDK feature, `javap` |
| Java-level access không hợp lệ ở linkage | `IllegalAccessError` | Exports/visibility/compiler-vs-runtime |
| Reflective deep access bị chặn bởi module openness | `InaccessibleObjectException` | `opens`/`--add-opens`, framework version |
| Class đã tìm được nhưng initializer lỗi | `ExceptionInInitializerError` / NCDFE tiếp theo | Static code/config/cause |

Lỗi cụ thể phụ thuộc path/API. **Đừng dùng `--add-opens` như một “thuốc chữa” cho lớp thực sự bị thiếu**. `--add-opens` điều chỉnh reflective access, không tạo ra missing class bytes.

## 14. Chẩn đoán bằng chứng thay vì đoán

### 14.1. Bước 1 — giữ nguyên stack trace đầu tiên

Trong incident production, lưu:

- Timestamp và service/pod/container/process PID.
- Class name, message, **entire cause chain**.
- Frame đầu tiên thuộc application code.
- JVM version/vendor/build và startup command.
- Build artifact ID/digest.
- Classpath/module path/loader context phù hợp.
- Trạng thái config/feature flag có liên quan (được redact).

Nếu có một đợt log `ExceptionInInitializerError` lúc startup rồi rất nhiều `NoClassDefFoundError` sau đó, **lỗi đầu tiên thường giá trị chẩn đoán cao hơn**.

### 14.2. Bước 2 — xác nhận định nghĩa class trong artifact

Trên file JAR *được deploy thực tế*:

```bash
jar tf application.jar
jar tf dependency.jar
javap -classpath dependency.jar -verbose com.example.Dependent
```

Các tên file/class ở trên là placeholder cần thay bằng artifact thật. Không inspect bản JAR ở laptop rồi kết luận bản trong production giống hệt; cần digest/manifest để đối chiếu.

**Các bẫy:**

- Class `com.example.Foo` có path JAR entry `com/example/Foo.class`.
- Tên inner class có `$`, ví dụ `Outer$Inner`, shell cần quote.
- Trên hệ thống case-sensitive, `foo/Bar.class` không phải `Foo/Bar.class`.
- `Multi-Release: true` JAR có thể cung cấp versioned classes.
- `jar tf` cho thấy entry tồn tại, nhưng loader khác có thể không load từ JAR ấy.
- Source dependency graph khác assembled runtime classpath.

### 14.3. Bước 3 — xác định runtime class source

Trên HotSpot Java 17:

```bash
java -Xlog:class+load=info -cp application.jar com.example.Main
```

Output logging có thể rất lớn, cần filter. Tìm class báo lỗi và những classes trong parent/superclass/interfaces/dependencies. Class loading source thường giúp biết class được nạp từ JAR nào hoặc shared archive.

**Giới hạn:** Logger chỉ hiển thị các events đã bật trong **cửa sổ chạy**. Nó không phải một API liệt kê tuyệt đối mọi loader có thể từng tìm class. Một class không xuất hiện trong log có thể chưa được yêu cầu.

### 14.4. `-verbose:class`, `jdeps`, `jcmd`

Các công cụ bổ trợ:

```bash
# JVM class-loading diagnostic (cách cũ, nhiều JVM vẫn hỗ trợ):
java -verbose:class -cp application.jar com.example.Main

# Static dependency analysis:
jdeps --missing-deps application.jar

# Inspect running HotSpot process khi có attach permissions:
jcmd <pid> VM.version
jcmd <pid> VM.command_line
```

**Chú ý:**

- `jdeps` phân tích **static class-file dependencies**; nó có thể không thấy class tên ghép bằng reflection/string, generated classes hoặc runtime plugin configuration.
- `VM.command_line` có thể chứa các `-D...` giá trị nhạy cảm; chỉ chia sẻ bản đã redact.
- `jcmd` cần đúng PID/namespace/permissions và tooling compatible.
- `-verbose:class`/`-Xlog:class+load` có thể tạo nhiều I/O nếu bật trên production lâu.
- Command để chạy Java app đầy đủ cần có classpath/main class đúng; các lệnh là mẫu cần thay artifact.
- Lỗi khởi tạo lớp có thể xảy ra khi class đã load thành công; xem `class+load` thôi không phân tích được `<clinit>` root cause.

### 14.5. ClassLoader resource check

Đôi lúc inspect class resource giúp định vị JAR:

```java
String resource = "com/example/Config.class";
ClassLoader loader = Thread.currentThread().getContextClassLoader();
java.net.URL found = loader == null ? null : loader.getResource(resource);
System.out.println(found);
```

Đây là snippet cần đặt trong một method/class phù hợp. `getResource` có thể trả `null` nếu loader không tìm thấy resource; tuy nhiên **resource visibility không hoàn toàn đồng nhất với class definition/linkage/initialization** trong mọi custom loaders hoặc module setups.

### 14.6. Đừng nhìn `-cp` của một lệnh shell rồi suy của service

Một service thực tế có thể được launch bằng:

- `java -jar app.jar`, manifest `Main-Class` và loader riêng.
- `java -cp "lib/*:app.jar" ...`.
- Module launcher `java -m module.name/...`.
- Spring Boot nested-JAR launcher hoặc application server.
- Docker/Kubernetes entrypoint/script.
- IDE/test runner với classpath riêng.
- Java agents chỉnh class loading.

Vì vậy `ClassNotFoundException` xảy ra trên server cần điều tra **effective deployment path và custom class loader**, không chỉ `java.class.path` property trong một shell bất kỳ.

## 15. Vì sao lỗi có thể chỉ xảy ra ở một code path?

Linking/resolution không bắt buộc resolve mọi symbolic reference ngay khi app startup. JVM có thể trì hoãn tới khi instruction cần dùng reference.

Ví dụ:

```java
public void handleRequest(Request request) {
    if (request.needsOptionalPlugin()) {
        new OptionalCodec().encode(request);
    }
}
```

Snippet chưa độc lập (types/method chưa khai báo). Application có thể chạy hàng giờ với một branch chưa từng được thực thi. Khi request đặc biệt xuất hiện, classloader mới phải load/link `OptionalCodec` hoặc một dependency của nó. Lúc đó:

- `ClassNotFoundException` có thể xuất hiện nếu framework dùng reflection load by name.
- `NoClassDefFoundError` có thể xuất hiện nếu JVM cần class definition cho bytecode/reference.
- `NoSuchMethodError` có thể xuất hiện nếu runtime JAR API khác compile JAR.
- `ExceptionInInitializerError` có thể xuất hiện nếu static initializer lần đầu chạy và lỗi.

**Bài học:** “ứng dụng startup thành công” chưa chứng minh toàn bộ optional code paths và dependencies có thể chạy.

### 15.1. Startup health và lazy paths

Một readiness check chỉ gọi `GET /health` có thể không nạp các classes của payment adapter, serialization provider hoặc message consumer. Cần **representative integration tests** qua những paths quan trọng sau khi build/deploy, không ép preload mọi class bừa bãi chỉ để tránh sự cố.

## 16. Vì sao `ClassNotFoundException`/NCDFE có thể bị catch rồi biến thành lỗi khác?

Framework thường bọc lỗi từ SPI/DI/reflection vào:

- `BeanCreationException` hoặc exception lifecycle của framework.
- `ServiceConfigurationError` từ `ServiceLoader`.
- `InvocationTargetException` nếu lỗi xảy ra trong method/constructor được gọi bằng reflection và được API bọc.
- Exception tùy biến từ module/plugin manager.

Ví dụ một logger có thể chỉ in:

```text
Failed to instantiate provider
```

trong khi nguyên nhân sâu là `ExceptionInInitializerError` bên dưới. Xem full nested causes và suppressed exceptions khi phù hợp.

**Không tự mặc định** `ServiceLoader` luôn ném `ClassNotFoundException` trực tiếp. Service discovery có hợp đồng lỗi riêng, chẳng hạn `ServiceConfigurationError`, và providers có thể lỗi trong lúc initialize/instantiate.

## 17. Static initializer exception có được bọc bao giờ cũng giống nhau không?

Không. Các nhánh cần phân biệt:

| Throwable gốc từ init | Kết quả lần đầu khi initialization thất bại | Kết quả lần tiếp theo |
| --- | --- | --- |
| `IllegalStateException` | `ExceptionInInitializerError` bọc cause | Thường `NoClassDefFoundError` |
| `NullPointerException` | `ExceptionInInitializerError` bọc cause | Thường `NoClassDefFoundError` |
| `OutOfMemoryError` | Có thể ném `Error` trực tiếp | Thường `NoClassDefFoundError` nếu class đã bị đánh dấu erroneous |
| `AssertionError` | Có thể ném `Error` trực tiếp | Thường `NoClassDefFoundError` |
| Exception được caller catch **ngay trong initializer** và xử lý ổn | Có thể hoàn tất initialization bình thường | Lớp vẫn dùng được |
| Lỗi ở **constructor của instance**, không phải class init | Tùy constructor | Không mặc định làm class erroneous |

**Điểm mấu chốt:** JLS `12.4.2` có quy tắc bọc non-Error throwables trong `ExceptionInInitializerError`; Error có đường xử lý khác. Vì vậy một lần class init fail **không phải lúc nào lần đầu cũng có EIIE**.

### 17.1. `ExceptionInInitializerError` có thể giữ cause thế nào?

`ExceptionInInitializerError.getCause()` là cách chuẩn hiện đại để lấy nguyên nhân gốc, còn `getException()` là API lịch sử.

```java
catch (ExceptionInInitializerError e) {
    Throwable cause = e.getCause();
    // Ghi log loại lỗi và stack trace đã redact;
    // không in secret/config values vô điều kiện.
}
```

Snippet cần đặt quanh operation có thể initialize class. Nếu cause null do object được tạo thủ công theo constructor khác, cần xử lý null thay vì giả định luôn có cause. Trong `InitializationFailureDemo`, cause chắc là `IllegalStateException` theo code đã viết.

## 18. `Error` không nên dùng như flow control thông thường

`ClassNotFoundException` là checked exception, phù hợp để xử lý một optional class không có. `NoClassDefFoundError` và `ExceptionInInitializerError` là `Error` family báo tình huống nghiêm trọng ở linkage/initialization.

Không nên viết:

```java
try {
    startServer();
} catch (Throwable t) {
    // Bỏ qua mọi Error rồi báo healthy.
}
```

Đây là snippet phản ví dụ; method `startServer()` cần định nghĩa. Bắt mọi `Throwable` mà nuốt lỗi có thể che:

- `OutOfMemoryError`.
- `StackOverflowError`.
- `LinkageError`.
- Các thất bại khởi tạo khiến class thành erroneous.
- Các vấn đề nghiêm trọng không thể recovery an toàn.

Một plugin isolation boundary có thể chủ đích catch một vài `LinkageError` để từ chối plugin và tiếp tục hệ thống, **nhưng cần** chứng minh không leak partial state, báo lỗi rõ, và không cho codepath khác vô tình tham chiếu class đã hỏng.

## 19. Nhận diện root cause bằng timeline

Giả sử một JVM process có log (đều là ví dụ giả lập):

```text
10:00:01  INFO  application startup started
10:00:02  ERROR ExceptionInInitializerError in Settings
10:00:02    caused by IllegalStateException: missing key
10:05:33  ERROR NoClassDefFoundError: Could not initialize class Settings
10:06:01  ERROR NoClassDefFoundError: Could not initialize class Settings
```

Cách suy luận:

1. `10:00:02` là thời điểm **first initialization failure**.
2. `10:05:33`/`10:06:01` nhiều khả năng chỉ là **hệ quả** do class erroneous.
3. Không cần tìm một JAR Settings khác ngay nếu stack đầu cho thấy Settings đã có mặt và `<clinit>` chạy.
4. Root cause có thể là config missing, not “class vanished”.
5. Fix cần validate config trước startup và restart/redeploy process đã được repair.

Nếu chỉ thu log từ 10:05 trở đi, sẽ thiếu bằng chứng quan trọng. Trong production, chuẩn bị log retention/JFR/structured exceptions để **không mất first failure**.

## 20. Bytecode, classfile version và source-of-truth

`javap` có ích để xác định:

- Class file thực có major version nào (Java 17 là `61`).
- Class có superclass/interfaces nào.
- Method descriptor và field descriptors.
- Constant-pool symbolic references tới dependency nào.
- Static initialization `<clinit>` gọi những methods nào.
- Runtime artifact có phù hợp compile target không.

```bash
javap -verbose Plugin
javap -c -p InitializationFailureDemo
javap -c -p 'InitializationFailureDemo$Broken'
```

Chạy trong thư mục đã compile demos. Nếu cần kiểm tra JAR:

```bash
javap -classpath application.jar -verbose com.example.Plugin
jdeps --missing-deps application.jar
```

Lệnh là mẫu; các file JAR/classes phải tồn tại. **`javap` không bảo đảm runtime loader đã chọn đúng JAR ấy.** Phải đối chiếu class-load logs/artifact hashes để xác minh loaded class source.

### 20.1. `java.class.path` có phải lời giải đầy đủ?

Không trong mọi môi trường. Nó là thông tin hữu ích cho application classpath nhưng:

- Named modules dùng module path/graph riêng.
- Nested JAR frameworks dùng loader behavior riêng.
- Custom plugin loaders có URLs/sources không phản ánh trong `java.class.path`.
- Có thể có JARs/resources được load động hoặc generated classes.

Bắt đầu bằng runtime launcher/`jcmd VM.command_line`, sau đó đến classloader-specific diagnostics.

## 21. Build và triển khai sao để giảm lỗi?

### 21.1. Cố định dependency graph

- Khóa các dependency versions cần thiết cho release.
- Kiểm tra runtime dependency scopes/exclusions.
- Kiểm tra transitive dependency tree, không chỉ các dependencies trực tiếp.
- Tránh dùng `SNAPSHOT`/floating dynamic versions trong deployment cần reproduce.
- Xác minh fat/shaded JAR sau packaging, không chỉ trước packaging.
- Chạy integration tests bằng **final runtime artifact**.
- Giữ manifest/build metadata và checksum cho các JARs.

### 21.2. Kiểm tra bằng Java 17 target runtime

Nếu build bằng JDK mới, dùng `javac --release 17` hoặc toolchain target phù hợp; nhưng vẫn phải chạy integration tests trên **đúng JDK 17 runtime** và classpath mà production sử dụng.

Một ứng dụng build trên JDK 21, chạy tests trên JDK 21 nhưng deploy lên JDK 17 có thể gặp version/linkage errors khác, dù source-level syntax không có Java 21 features.

### 21.3. Test các optional/plugin code paths

- Dynamic reflective class names lấy từ config.
- `ServiceLoader` service provider registration.
- Native/agent instrumentation class loading.
- Modules được resolve theo runtime graph.
- Các features chỉ bật bởi tenant/feature flags.
- Startup với config invalid và partial deployment để đảm bảo fail-fast.
- Restart behavior sau một class initialization failure.

**Test cho negative scenarios** thường quan trọng tương đương happy path, nhất là nếu class loading diễn ra lazy.

### 21.4. Khi nâng dependency

Thay một JAR có thể làm hệ thống chuyển từ CNFE sang `NoSuchMethodError` hoặc ngược lại tùy APIs/classes. Sau nâng:

1. Rebuild **toàn bộ deployment artifact**.
2. Kiểm tra versions/digests không bị duplicate trên classpath.
3. Chạy targeted regression tests.
4. Đọc root cause đúng throwable subtype mới.
5. Không áp “copy thêm JAR” vô nguyên tắc vì có thể sinh class conflicts hoặc API mismatches.

## 22. Những câu hỏi “gỡ rối nhanh” trước khi sửa

1. Tên lớp trong exception là **đúng class đang tìm** hay **một superclass/dependency** của nó?
2. Stack trace bắt đầu khi **Class.forName/loadClass** hay khi JVM chạy bytecode gọi class?
3. Lỗi là **checked ClassNotFoundException** hay **LinkageError subtype**?
4. Message có `Could not initialize class` không?
5. Có **ExceptionInInitializerError đầu tiên** trước các NCDFE sau đó không?
6. `Caused by` có `IllegalStateException`/`NullPointerException`/`ExceptionInInitializerError` không?
7. Lớp có thực sự có trong **runtime JAR** và loader context không?
8. Có **module path/readability/exports** ảnh hưởng không?
9. Class file major version có phù hợp JVM target không?
10. Runtime classpath có dependency được đánh dấu `provided`/`compileOnly` nhưng chưa ship không?
11. Có duplicate JAR/classes khác version dẫn đến name shadowing không?
12. Có classloader khác (plugin/OSGi/application server/nested JAR) không?
13. Có static initializer gọi network/file/database/config không?
14. Lỗi xảy ra ở **lần đầu dùng class trong JVM** hay lần sau?
15. Có JVM process/pod khác cùng deployment chạy tốt với môi trường khác không?
16. Lỗi chỉ xảy ra sau khi feature flag bật hoặc request ít gặp đến không?
17. Có mất log first failure do rotation/sampling/async logging không?
18. Nếu restart process sau sửa config, lỗi có biến mất một cách lặp lại không?

## 23. Những hiểu nhầm phổ biến

1. **“ClassNotFoundException và NoClassDefFoundError giống hệt nhau.”** Sai: khác inheritance/API/đường gây lỗi.
2. **“NoClassDefFoundError luôn nghĩa file `.class` bị xóa.”** Sai: còn có class erroneous sau init failure.
3. **“ExceptionInInitializerError chỉ do static block có dấu `static { }`.”** Sai: static field initializer cũng có thể lỗi.
4. **“Compiler đã tìm được dependency thì runtime chắc chắn tìm được.”** Sai: paths/artifact khác.
5. **“Bất cứ khi nào đọc static constant đều kích hoạt init.”** Sai: compile-time constant variable có quy tắc riêng.
6. **“Class.forName(String) chỉ load chứ không init.”** Sai: overload đó yêu cầu initialize.
7. **“Class.forName(name,false,loader) chắc chắn không bao giờ gây lỗi.”** Sai: loading/linking vẫn có thể lỗi.
8. **“ClassLoader.loadClass luôn initialize static fields ngay.”** Sai: không mặc định.
9. **“Class.forName(Module,String) cũng ném CNFE y như String overload.”** Sai: khi không tìm thấy thường trả null.
10. **“Một static initializer fail có thể tự retry mỗi lần method được gọi.”** Sai: cùng class identity trở thành erroneous.
11. **“Chỉ cần sửa config file tại chỗ là class erroneous sẽ tự khởi tạo lại.”** Sai.
12. **“Lần đầu class init fail chắc luôn ném ExceptionInInitializerError.”** Sai: Error có thể truyền ra nguyên dạng.
13. **“Lần thứ hai NCDFE chứng minh dependency vừa biến mất.”** Sai: có thể do init failure trước đó.
14. **“getCause() của EIIE chắc luôn khác null trong mọi trường hợp.”** Không bảo đảm nếu object được tạo thủ công.
15. **“Class identity là binary name duy nhất.”** Sai: defining classloader cũng tham gia.
16. **“Tên lớp trùng nghĩa casts luôn an toàn giữa custom loaders.”** Sai.
17. **“Class loader resource tìm được `.class` chứng minh class đã link/init OK.”** Sai.
18. **“Mọi lỗi reflective load đều ném ClassNotFoundException trực tiếp.”** Sai: errors/wrappers khác.
19. **“Không có log class load nghĩa class không có.”** Chưa đủ: log window/level và lazy loading.
20. **“Đưa `--add-opens` vào mọi lỗi ClassNotFoundException sẽ chữa được.”** Sai: openness không tạo missing bytecode.
21. **“Class not found ở production thì chỉ cần lấy JAR từ máy dev copy qua.”** Rủi ro version/conflict; phải sửa release artifact.
22. **“Cùng tên JAR nghĩa nội dung class chắc giống.”** Sai.
23. **“`NoSuchMethodError` là subtype của CNFE.”** Sai: thuộc `LinkageError`.
24. **“Static init failure đồng nghĩa Java object constructor fail.”** Sai: constructor instance khác class init.
25. **“Catch `Throwable` rồi ignore giúp server khỏe hơn.”** Sai: có thể che Errors và corrupted lifecycle.
26. **“App startup OK chứng minh tất cả lazy plugins usable.”** Sai.
27. **“Chỉ xem `java.class.path` là biết mọi loader/plugin sources.”** Sai.
28. **“jdeps thấy đủ dependency nghĩa reflection chắc cũng thấy đủ.”** Sai: jdeps không resolve mọi string-based dynamic loading.
29. **“JVM 17 chắc chạy mọi class do javac 21 biên dịch mặc định.”** Sai: class major version mismatch.
30. **“`jar tf` thấy `Plugin.class` thì Plugin khởi tạo chắc thành công.”** Sai.
31. **“Đổi JDK build không thể ảnh hưởng class loading.”** Không bảo đảm, modules/VM behavior/options có thể khác.
32. **“Mọi NoClassDefFoundError đều bắt đầu từ application classpath.”** Sai: modules/loaders/initialization.
33. **“NNCDFE có cause ClassNotFoundException và EIIE nghĩa cùng một thứ.”** Sai: một bên loading dependency, một bên init behavior.
34. **“Nếu app dùng constant field thành công, lớp đã initialize.”** Sai.
35. **“Quá nhiều threads lỗi NCDFE nghĩa JVM thiếu nhiều bản sao JAR.”** Chưa đủ: có thể một class erroneous chung.
36. **“Thêm logging sau khi lỗi xảy ra là xem được cả first failure history.”** Sai.
37. **“Dùng `Class.forName` để kiểm tra optional class luôn vô hại.”** Sai: default initialize và có thể side effects.
38. **“Một plugin loader mới tự động sửa lỗi config từ plugin cũ.”** Sai: class identity mới nhưng root cause vẫn cần sửa.
39. **“NoClassDefFoundError là checked exception nên compiler bắt buộc catch.”** Sai: là `Error`.
40. **“Có thể kết luận chính xác từ một dòng message không cần full stack.”** Sai.

## 24. Bài tập tự kiểm tra (có đáp án)

**Câu 1.** `Class.forName("x.y.Missing")` trong code Java 17 có thể ném checked exception nào?

**Đáp án:** `ClassNotFoundException` nếu loader context không tìm được lớp.

**Câu 2.** `Class.forName(name)` có tự yêu cầu initialization không?

**Đáp án:** Có, theo overload một string. Để không yêu cầu initialization ngay, dùng overload `Class.forName(name,false,loader)` nếu phù hợp.

**Câu 3.** `ClassLoader.loadClass(name)` có mặc định chạy `<clinit>` không?

**Đáp án:** Không yêu cầu khởi tạo ngay theo contract loadClass; class có thể initialize khi active use sau đó.

**Câu 4.** Code compile khi có superclass `Base`, runtime thiếu `Base.class`, có thể lỗi nào?

**Đáp án:** `NoClassDefFoundError` khi JVM cần định nghĩa subclass/superclass.

**Câu 5.** `ExceptionInInitializerError` thuộc checked exception hay Error?

**Đáp án:** `Error` qua `LinkageError`; không phải checked exception.

**Câu 6.** `static int X = parse("bad")` ném `IllegalArgumentException` thì lần đầu active use có thể thấy gì?

**Đáp án:** `ExceptionInInitializerError` với `IllegalArgumentException` trong `getCause()`, nếu exception xảy ra trong static initialization và không được xử lý.

**Câu 7.** Lần hai cùng class erroneous thường thấy gì?

**Đáp án:** `NoClassDefFoundError` cho class đã không thể initialize; JVM không retry static initialization.

**Câu 8.** Nếu static initializer ném `OutOfMemoryError`, nó có bắt buộc được bọc trong EIIE không?

**Đáp án:** Không. `Error` có thể được ném trực tiếp theo quy tắc initialization.

**Câu 9.** `static final String LABEL="ready"` có bắt buộc initialize declaring class khi caller in nó không?

**Đáp án:** Không; compile-time constant variable có thể được inline ở caller.

**Câu 10.** Vì sao `InitializationTriggerDemo` in `initialized=true` trước `nonconstant=42`?

**Đáp án:** Truy cập `Example.NUMBER` không phải constant variable, nên cần initialize class trước khi lấy giá trị.

**Câu 11.** Nếu hai loaders khác nhau define cùng binary name, chúng có phải cùng class không?

**Đáp án:** Không; JVM class identity bao gồm binary name và defining loader.

**Câu 12.** `jar tf app.jar` tìm thấy lớp, nhưng `Class.forName` vẫn CNFE; có thể vì sao?

**Đáp án:** Caller/custom loader không nhìn thấy JAR, module graph không phù hợp, runtime path khác hoặc tên binary không khớp.

**Câu 13.** Khi thấy `Could not initialize class Config`, bằng chứng đầu tiên cần xem là gì?

**Đáp án:** Tìm **lần static initialization failure đầu tiên** và full `Caused by` chain trước các NCDFE lặp lại.

**Câu 14.** Nếu `NoSuchMethodError` xảy ra sau khi đổi JAR, có chắc thiếu class không?

**Đáp án:** Không. Có thể class vẫn tìm được nhưng method descriptor/API binary không tương thích.

**Câu 15.** `jdeps --missing-deps` kiểm tra được mọi class name sinh từ string vào runtime không?

**Đáp án:** Không; static analysis không bao phủ đầy đủ reflective/generated loading.

**Câu 16.** Static initializer đọc remote config sai rồi lỗi, vì sao tăng `-Xmx` không phải giải pháp mặc định?

**Đáp án:** Lỗi là config/IO/logic initialization; heap size chỉ liên quan nếu có bằng chứng memory pressure cụ thể.

**Câu 17.** Có nên `catch(Throwable)` và bỏ qua để server luôn healthy?

**Đáp án:** Không; có thể che unrecoverable VM/linkage/class initialization errors.

**Câu 18.** Dùng `--add-opens` có làm xuất hiện JAR bị thiếu không?

**Đáp án:** Không; nó thay reflective access rules, không cung cấp bytecode.

**Câu 19.** Vì sao test final runtime image quan trọng hơn chỉ chạy tests từ IDE?

**Đáp án:** IDE có classpath/toolchain phụ thuộc khác; deployment thực có thể thiếu libs, khác loader và version.

**Câu 20.** Bản sửa tốt cho `Config.<clinit>` thiếu env var là gì?

**Đáp án:** Validate required config có thông điệp rõ trước khi phục vụ, sửa env/deployment, và restart JVM/class identity sau khi root cause đã xử lý; không dựa vào retry tự động của static initializer.

## 25. Checklist xử lý incident thực tế

- [ ] Đã xác định throwable subtype: CNFE, NCDFE, EIIE hay linkage error khác.
- [ ] Đã lưu **nguyên văn first failure stack trace + complete cause chain**.
- [ ] Đã kiểm tra message `Could not initialize class` nếu là NCDFE.
- [ ] Đã phân biệt thiếu class definition với class **erroneous** sau initialization.
- [ ] Đã ghi JVM vendor/version/build, PID, container/pod và deployment image.
- [ ] Đã kiểm tra missing class **trực tiếp hoặc transitive superclass/interface**.
- [ ] Đã kiểm tra classpath/modulepath và runtime dependency scopes.
- [ ] Đã đối chiếu JAR thực sự deploy bằng file digest.
- [ ] Đã kiểm tra duplicate classes/JAR version order.
- [ ] Đã xác định custom defining loader/context loader nếu có.
- [ ] Đã kiểm tra reflective class names/config/SPI/ServiceLoader.
- [ ] Đã dùng `jar tf`/`javap`/`jdeps` khi phù hợp.
- [ ] Đã ghi `-Xlog:class+load` có giới hạn và chú ý output volume.
- [ ] Đã kiểm tra class file major version và target JDK.
- [ ] Đã kiểm tra static initializers đọc config/file/network/secrets.
- [ ] Đã phân biệt non-Error throwable và `Error` trong class init.
- [ ] Đã biết class erroneous **không tự retry** trong cùng loader identity.
- [ ] Đã chuẩn bị restart/redeploy sau khi fix nếu initialization thất bại.
- [ ] Đã test optional/lazy code paths của final deployment artifact.
- [ ] Đã redacted secrets trước khi chia sẻ logs/VM.command_line.
- [ ] Đã chạy negative tests và canary/smoke tests trong môi trường mục tiêu.

## 26. Tổng kết và sơ đồ quyết định

```text
       JVM gặp lỗi liên quan class?
                    │
                    ▼
        XÁC ĐỊNH LOẠI THROWABLE
                    │
        ┌───────────┼────────────────┐
        │           │                │
 ClassNotFound    NoClassDef      ExceptionIn
 Exception        FoundError      InitializerError
        │           │                │
        ▼           ▼                ▼
 loader/name?  Có "Could not      Static code
 classpath?    initialize class"?  throwing?
        │           │                │
        │      ┌────┴────┐           │
        │      │         │           │
        │     Có        Không        │
        │      │         │           │
        │      ▼         ▼           ▼
        │   Tìm lỗi     Missing    getCause()
        │   <clinit>    binary /   config/init
        │   đầu tiên    dependency  root cause
        │      │         │           │
        └──────┴─────────┴───────────┘
                    │
                    ▼
         XÁC MINH DEPLOYMENT THỰC:
         JAR / classpath / modules
         loaders / versions / config
                    │
                    ▼
          SỬA ĐÚNG ROOT CAUSE
                    │
                    ▼
         TEST TRÊN RUNTIME ARTIFACT
                    │
                    ▼
        RESTART/REDEPLOY NẾU CẦN
```

**Kết luận:** `ClassNotFoundException` trả lời “loader được hỏi bằng tên nhưng không tìm được định nghĩa”. `NoClassDefFoundError` trả lời một bài toán cấp JVM/linkage: lớp cần thiết không tìm được **hoặc** một class đã không thể initialize trước đó. `ExceptionInInitializerError` thường là tín hiệu đầu tiên static initializer ném exception không phải `Error`; **lần lỗi sau** có thể chỉ còn `Could not initialize class`. Muốn chẩn đoán đúng, hãy xem **full cause chain và earliest failure**, phân biệt **loading/linking/initialization**, xác minh **runtime JAR, module graph, defining loader và configuration**, rồi thử lại trên đúng JVM/deployment artifact.

### Tài liệu tham khảo chính thức (Java SE 17)

- [Java SE 17 — `ClassNotFoundException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassNotFoundException.html): checked exception khi loader không tìm được class yêu cầu.
- [Java SE 17 — `NoClassDefFoundError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html): JVM/loader không tìm được định nghĩa class cần dùng.
- [Java SE 17 — `ExceptionInInitializerError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html): static initializer failure và cause.
- [Java SE 17 — `Class.forName`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String)): mặc định yêu cầu initialization; overload có `boolean initialize`.
- [Java SE 17 — `ClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): loader identity và `loadClass` semantics.
- [Java Language Specification 17 §12.2 — Loading](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2).
- [Java Language Specification 17 §12.3 — Linking](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.3).
- [Java Language Specification 17 §12.4.1 — Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1).
- [Java Language Specification 17 §12.4.2 — Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2).
- [Java Virtual Machine Specification 17 §5.3–§5.5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html).
- [Oracle JDK 17 — `javap`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): class-file disassembly.
- [Oracle JDK 17 — `jdeps`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jdeps.html): static dependencies.
- [Oracle JDK 17 — `java` launcher and `-Xlog:class+load`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-009">UnsupportedClassVersionError, VerifyError và ClassFormatError</a>

<details>
<summary>Xem chi tiết</summary>

# UnsupportedClassVersionError, VerifyError và ClassFormatError

## 1. Ba lỗi cùng liên quan bytecode nhưng chỉ ra ba kiểu thất bại khác nhau

Khi ứng dụng Java tải một file `.class`, JVM không đơn giản “đọc xong là chạy”. VM phải hiểu **định dạng class file**, kiểm tra **phiên bản major/minor** được hỗ trợ và **xác minh tính hợp lệ của bytecode** theo quy tắc JVM. Nếu một trong những điều kiện không đáp ứng, những lỗi trong họ `LinkageError` có thể xuất hiện **trước khi application thực thi method mong muốn**.

Ba lỗi của bài có ba câu hỏi phân biệt rất thực tế:

- **`UnsupportedClassVersionError`**: JVM **không hỗ trợ phiên bản class file** hiện có (major/minor). Ví dụ compile bằng Java mới nhưng chạy trên runtime cũ, hoặc preview class file không đúng phiên bản/flag.
- **`ClassFormatError`**: JVM đọc dữ liệu `.class` và phát hiện **cấu trúc file không đúng định dạng**, nên không thể diễn giải như một class file hợp lệ.
- **`VerifyError`**: class file nhìn chung **đúng định dạng**, nhưng verifier phát hiện **code/frames/type state không nhất quán hoặc vi phạm ràng buộc an toàn** của JVM.

Đừng gọi cả ba là “sai phiên bản Java”. Trong một hệ thống có transformer, bytecode instrumentation, shading, plugin classloader và build artifacts, việc xác định **đúng tầng lỗi** giúp tránh sửa nhầm `JAVA_HOME`, đổi GC hoặc vô hiệu hóa verifier.

### Quan hệ kế thừa cực kỳ quan trọng

~~~text
java.lang.Throwable
  └── java.lang.Error
       └── java.lang.LinkageError
            ├── java.lang.ClassFormatError
            │    └── java.lang.UnsupportedClassVersionError
            ├── java.lang.VerifyError
            ├── java.lang.NoClassDefFoundError
            ├── java.lang.IncompatibleClassChangeError
            └── ... các dạng linking errors khác
~~~

**Chi tiết dễ nhầm:** `UnsupportedClassVersionError` **extends `ClassFormatError`** theo Javadoc Java 17. `VerifyError` **extends trực tiếp `LinkageError`**, chứ không extends `ClassFormatError`.

Do đó:

~~~java
catch (ClassFormatError e) {
    // Bắt được cả UnsupportedClassVersionError.
}
~~~

Đây là mảnh code minh họa (đặt trong một `try` hợp lệ). Nếu cần phân biệt subtype, phải bắt `UnsupportedClassVersionError` **trước** `ClassFormatError`, vì bắt superclass trước sẽ che subtype và compiler có thể báo unreachable catch.

Các class là **`Error`**, không phải `Exception` thông thường. Trong production nên **sửa classpath/bytecode/build/runtime** thay vì coi `catch (LinkageError)` rồi tiếp tục như không có vấn đề.

## 2. Đặt ba lỗi vào đúng vòng đời loading và linking

Java Language Specification §12 và JVM Specification §5 phân biệt các pha:

~~~text
Tìm và đọc binary form
  │
  ├── Đọc header/classfile structures
  │     ├── version không được hỗ trợ → UnsupportedClassVersionError
  │     └── format malformed → ClassFormatError
  │
  ├── Loading / defining Class representation
  │
  └── Linking
        ├── Verification (type/operand stack/stack map rules)
        │     └── internal inconsistency → VerifyError
        ├── Preparation (giá trị static defaults / structures)
        └── Resolution (symbolic references, có thể lazy)
               └── các LinkageError khác tùy sự cố
  │
  └── Initialization (chạy static initializers / <clinit>)
        └── ExceptionInInitializerError và các lỗi khác
~~~

Đây là **sơ đồ khái niệm**, không phải cam kết VM luôn thực hiện từng operation theo một trình tự eager cứng. JVM được phép tổ chức loading/linking/resolution ở những thời điểm khác nhau theo specification; vì vậy:

- Một class có thể **chưa bị verify toàn bộ** cho tới khi có nhu cầu linking/active use tương ứng.
- Một lỗi có thể xuất hiện lúc `Class.forName`, `getDeclaredMethod`, gọi method lần đầu hoặc framework scan, tùy VM/classloader và đường sử dụng.
- Stack trace chỉ nơi **lỗi được quan sát**, không nhất thiết là nơi file `.class` bị tạo sai.
- Không dùng mỗi thông báo “Could not initialize class” để kết luận đó là `VerifyError`; class initialization failure là một nhánh chẩn đoán riêng.

### Class loader không được phép tạo bytecode tùy ý phá contract

Custom classloader, JVM agent, bytecode weaver, ORM proxy, coverage agent, AOP framework và library chuyển đổi bytecode đều có thể cung cấp/transformed bytes. VM **vẫn kiểm tra** class files và type safety; “generated bytecode” không được miễn format/version/verification rules.

Nếu class `A` được tạo bằng javac bình thường nhưng lớp `B` bị transformer sửa bytecode rồi hỏng, lỗi có thể chỉ xảy ra trong **production environment** nơi agent ấy được bật, dù source repository compile thành công.

## 3. Class file header: tại sao major/minor quan trọng?

Một file `.class` chuẩn bắt đầu bằng header:

~~~text
Offset   Bytes            Nội dung
0..3     CA FE BA BE      magic
4..5     00 00            minor_version (ví dụ)
6..7     00 3D            major_version = 61 (Java 17)
8..      ...              constant pool, class/access/fields/methods/attributes
~~~

`ClassFile` dùng **big-endian** cho các thành phần multi-byte; `major_version` và `minor_version` là các giá trị unsigned 16-bit.

Các bước đọc:

1. Đọc **magic** `0xCAFEBABE`: file có nhận diện định dạng class file không?
2. Đọc **minor version** ở bytes offset 4–5.
3. Đọc **major version** ở bytes offset 6–7.
4. Đối chiếu với JVM Specification và runtime đang dùng.
5. Chỉ khi header/formats đủ hợp lệ mới có thể kiểm tra nội dung sâu hơn.

### Bảng major versions thông dụng

| Java SE | Major class file |
| --- | ---: |
| Java 8 | 52 |
| Java 9 | 53 |
| Java 11 | 55 |
| Java 12 | 56 |
| Java 15 | 59 |
| Java 16 | 60 |
| **Java 17** | **61** |
| Java 18 | 62 |
| Java 19 | 63 |
| Java 20 | 64 |
| **Java 21** | **65** |
| Java 22 | 66 |
| Java 23 | 67 |
| Java 24 | 68 |
| Java 25 | 69 |
| Java 26 | 70 |

Không hiểu bảng như phép đo version của mã nguồn: **major version của `.class`** phản ánh target classfile format được dùng khi biên dịch/chỉnh sửa, không nhất thiết là phiên bản `javac` binary đã chạy. Ví dụ `javac` JDK 21 với `--release 17` xuất class major **61**, dù compiler thực thi trên JDK 21.

**Một Java 17 JVM chuẩn** hỗ trợ tối đa class file **61** theo JVMS 17. Nếu nó gặp major **65**, có thể báo kiểu:

~~~text
UnsupportedClassVersionError: ... has been compiled by a more recent version
of the Java Runtime (class file version 65.0),
this version ... recognizes class file versions up to 61.0
~~~

Đây là **ví dụ cấu trúc thông điệp**, không phải stdout từ lab, và từng HotSpot build có thể diễn đạt khác. Không cố định câu chữ message rồi parse production error bằng string literal.

### Phiên bản minor 65535 và preview features

Từ Java SE 12 trở đi, class file `major >= 56` có quy tắc:

- `minor = 0`: class file bình thường.
- `minor = 65535`: class file dùng preview features của **cùng** Java SE release tương ứng major.
- Java runtime **cùng release** cần được bật preview features (`--enable-preview`) để load class preview tương thích.
- Runtime release mới **không có nghĩa** được phép chạy class preview của release cũ bằng cách bật preview trên release mới.

Với Java 17 preview: class file dạng **61.65535** có nghĩa preview của Java 17; không nên chạy class đó trên JDK21 rồi hy vọng `--enable-preview` làm ngược phiên bản. Phải dùng runtime đúng release hoặc **biên dịch lại** sang API/feature đã ổn định.

**Không kết luận chỉ từ major version** rằng chương trình dùng preview; phải xem cả minor và các flags biên dịch.

## 4. `UnsupportedClassVersionError`: mismatch giữa bytecode và JVM

### 4.1. Điều kiện phát sinh

Javadoc Java 17: lỗi được ném khi JVM đọc class file và thấy **major/minor version numbers không được hỗ trợ**. Những nguyên nhân thường gặp:

- Compile `.class` bằng default `javac` của JDK 21 rồi deploy vào JRE/JDK 17.
- Transitive dependency/library jar mới chứa classes target Java 21, còn application runtime là Java 17.
- Một submodule/service/plugin đã nâng Java target nhưng deploy runtime chưa nâng.
- CI dùng JDK mới hơn local và artifact target không cố định bằng `--release`.
- Container base image vẫn dùng JDK 17 nhưng build runner compile JDK 21.
- Shadowing: classpath có **hai phiên bản cùng class** và classloader thực sự tìm phải bản compiled mới.
- JAR multi-release chọn entry không như bạn nghĩ, hoặc packaging làm mất/đổi manifest/resources cần thiết.
- Java preview class dùng sai JDK release hoặc thiếu `--enable-preview`.
- Bytecode manipulator sửa header version sai hoặc thư viện instrumentation sinh class major vượt khả năng runtime.

### 4.2. Kế hoạch sửa chính xác

1. **Xác minh runtime**, không chỉ version người phát triển đang dùng:

~~~bash
java -version
javac -version
which java
~~~

Trên Windows PowerShell:

~~~powershell
Get-Command java
java -version
javac -version
~~~

Chạy các lệnh **trên đúng container/process environment**. Trong CI/CD, hãy in phiên bản JDK trong build logs và deployment base image digest; một máy có nhiều JDK không thể suy từ `JAVA_HOME` đơn lẻ nếu `PATH` trỏ sang binary khác.

2. Xem classfile version thực:

~~~bash
javap -verbose -classpath . DemoBytecode
~~~

Ví dụ `.class` compile với `--release 17` cho:

~~~text
minor version: 0
major version: 61
~~~

`javap` phải đọc được file/class đó trên JDK dùng để inspect; `javap` cũ có thể không hiểu classfile rất mới. Khi command thất bại, dùng tool phù hợp hơn hoặc đọc header `CAFEBABE + minor/major` như kiến thức trên.

3. Chọn **một** chiến lược:
   - **Nâng runtime** đến release hỗ trợ bytecode, sau khi test dependencies/GC/agents.
   - **Hạ target biên dịch** bằng `javac --release 17` khi source và API dùng thực sự tương thích Java 17.
   - **Thay dependency** bằng phiên bản vẫn hỗ trợ Java 17.
   - **Tách module** yêu cầu Java mới hoặc điều chỉnh deploy topology.

4. Xác minh **tất cả** JARs/modules được load thật, không chỉ application entrypoint; một dependency bị compile major cao có thể fail dù `Main.class` major thấp.

### 4.3. `--release` tốt hơn chỉ `-target` trong phần lớn builds Java 9+

~~~bash
javac --release 17 DemoBytecode.java
~~~

`--release` hướng compiler dùng **language rules, target classfile và public platform APIs của release được chọn**. Nếu chỉ dùng `-target 17` trên compiler mới, có nguy cơ compile code tham chiếu APIs JDK mới (tùy options/modulepath) nhưng file class lại target version cũ, gây `NoSuchMethodError` hoặc `NoClassDefFoundError` khi chạy trên JDK 17.

Không hiểu `--release 17` là “mọi dependency được compile lại về Java17”: **dependency class files vẫn có phiên bản của chính chúng**, cần verify transitive artifacts và plugin bytecode output.

### 4.4. Không sửa class file major bằng hex editor để “downgrade”

Một class file major 65 có thể chứa bytecode instructions, attributes, type rules hoặc API references mà Java 17 không hỗ trợ. Chỉ thay bytes version thành 61 có thể làm JVM ném **ClassFormatError/VerifyError hoặc LinkageError khác**, hoặc che vấn đề cho đến khi method được load.

**Cách đúng là biên dịch lại với target phù hợp**, sửa code/dependencies hoặc nâng runtime, không “patch version header” của artifact production.

## 5. `ClassFormatError`: bytes không thỏa cấu trúc class file

`ClassFormatError` được ném khi JVM cố đọc class file nhưng nhận thấy file **malformed hoặc không thể diễn giải như class file**.

### 5.1. Các nguyên nhân điển hình

- Magic header không phải `0xCAFEBABE`.
- File `.class` rỗng/truncated/corrupted do transfer, disk, packaging hoặc class loader bytes lỗi.
- Constant-pool entries malformed: tag không hợp lệ, index ngoài phạm vi, length sai, UTF8 data không đúng rules.
- Các counts/offsets/attribute lengths không khớp hoặc trỏ qua vùng byte không tồn tại.
- Class structures không phù hợp format constraints (field/method names, descriptors/flags/access, `Code` attribute shape), tùy rule cụ thể.
- Bytecode transformer serialize lỗi, ASM/Javassist/Byte Buddy/agent version không tương thích với format mới.
- Đưa **tệp khác** vào đường dẫn `.class` (một số lỗi packaging/proxy trả HTML, JSON hoặc ZIP bytes thay vì class bytes).

### 5.2. Phân biệt lỗi format và lỗi verifier

- **Format check:** VM **không parse được** cấu trúc class file một cách hợp lệ.
- **Verifier:** VM đã có classfile format đủ hợp lệ, nhưng **các bytecode/type/stack transitions không nhất quán**.

Ví dụ minh họa:

~~~text
00 FE BA BE ...        (magic sai)       → ClassFormatError
CA FE BA BE ...        (header hợp lệ,
                        iconst_1; ireturn) → hợp lệ cho int return
CA FE BA BE ...        (header hợp lệ,
                        aconst_null; ireturn) → VerifyError
~~~

**Boundary thực tế có thể tinh tế:** một constraint về `Code` attribute/branch range/stack maps có thể được VM kiểm tra ở bước format hoặc verification tùy ràng buộc JVMS và implementation. Không tự khẳng định mọi bytecode lỗi đều VerifyError hay mọi classfile bất thường đều ClassFormatError.

### 5.3. Những thứ cần kiểm tra khi production báo `ClassFormatError`

- Lấy **tên class và defining classloader** đang load class, đường dẫn JAR/source URLs thực.
- Kiểm tra JAR/class sha256 và kích thước, so với artifact CI đã xuất bản.
- `jar tf` xem entries, `jar xf` giải nén **trong môi trường thử nghiệm**, không ghi đè production.
- `javap -verbose` để xem structural header nếu vẫn parse được.
- Tắt/đổi JVM agent theo thí nghiệm có kiểm soát; so artifact **trước và sau** transformer.
- Kiểm tra generated classes (proxies, lambda metafactories, AOP, coverage, shading).
- Không cố ép JVM chạy class malformed bằng cách tắt verifier; cần **khôi phục file hợp lệ**.

### 5.4. Khi nào `UnsupportedClassVersionError` được bắt như `ClassFormatError`?

Vì `UnsupportedClassVersionError extends ClassFormatError`, đoạn:

~~~java
try {
    Class.forName("pkg.Target");
} catch (ClassFormatError error) {
    System.err.println(error.getClass().getSimpleName());
}
~~~

là ví dụ **cấu trúc điều khiển**, cần `pkg.Target` tồn tại trong runtime thích hợp. Nó có thể in `UnsupportedClassVersionError` chứ không nhất thiết `ClassFormatError`. Nếu code telemetry chỉ phân loại theo `instanceof ClassFormatError`, phải phân nhánh subtype trước.

**Đừng gộp trong báo cáo vận hành** mọi `ClassFormatError` thành corrupt JAR: cùng supertype ấy còn chứa version mismatch subtype.

## 6. `VerifyError`: class đúng format nhưng code không type-safe

Javadoc Java 17 định nghĩa: verifier phát hiện class file, dù **well formed**, nhưng có **internal inconsistency hoặc security problem**.

### 6.1. JVM verifier kiểm tra gì?

Verifier không “chạy thử toàn bộ business methods”. Nó áp các static/type constraints của JVMS:

- Type of values trên **operand stack** phải phù hợp từng instruction.
- Local variables phải có types phù hợp paths và được khởi tạo đúng trước khi dùng.
- Trước `ireturn`, operand stack phải có **int-compatible value**, không phải object reference.
- Branch targets/control-flow merges phải có consistent stack frame states và hợp lệ.
- Constructor rules: uninitialized `this` và new object chưa chạy `<init>` không được dùng như fully initialized object trong mọi ngữ cảnh.
- `StackMapTable` frames và computed data-flow types phải nhất quán với instruction sequence.
- Access to values/instructions phải phù hợp class hierarchy/descriptors theo rules.

**Đừng nhầm với source type error:** `javac` thường ngăn những lỗi type này khi build Java source bình thường. `VerifyError` hay gặp hơn khi bytecode bị **generate/transform** ngoài `javac`, metadata/stack map frames không được cập nhật hoặc artifact/caching không nhất quán.

### 6.2. Bytecode ví dụ: đúng và sai

Một method:

~~~java
public static int number() {
    return 1;
}
~~~

được `javac --release 17` biên dịch thành:

~~~text
public static int number();
    Code:
       0: iconst_1
       1: ireturn
~~~

`iconst_1` đặt giá trị `int 1` lên operand stack. `ireturn` lấy `int` ấy để trả về — hợp lệ.

Nếu một transformer thay `iconst_1` bằng `aconst_null` **mà vẫn giữ `ireturn`**, trạng thái trở thành:

~~~text
public static int number();
    Code:
       0: aconst_null     # đẩy null reference lên stack
       1: ireturn        # lại yêu cầu int
~~~

Classfile có thể vẫn parse bình thường, nhưng verifier bắt **type mismatch**. Trên OpenJDK 21 ở thí nghiệm, lỗi là `VerifyError`. Không sửa bằng casting ở source (source đã hợp lệ), mà phải sửa **bytecode generator/transformer**.

### 6.3. StackMapTable là nguồn lỗi phổ biến

Kể từ Java 6/7 trở đi, type checking verifier dùng thông tin **stack map frames** trong nhiều trường hợp để xác minh control-flow merges. Khi một library instrumentation chèn instruction/branch nhưng không cập nhật frames/max locals/max stack đúng:

- `Expecting a stackmap frame at branch target ...`
- `Inconsistent stackmap frames at branch target ...`
- `Bad type on operand stack`

Các messages là **ví dụ cách mô tả thường gặp**, không phải contract string giữa JDKs. Kiểm tra thật bằng `javap -v` và `StackMapTable` attribute, cùng bytecode tool (ASM `ClassWriter.COMPUTE_FRAMES` khi phù hợp), với common superclass resolution đúng classloader.

**Không phải mọi transformation đều cần thêm frames thủ công**: các thư viện bytecode có cơ chế tái tính frames khác nhau. Quan trọng là **output cuối hợp lệ theo JVMS và target runtime**.

### 6.4. Không nên dùng `-noverify` làm “fix”

Cờ `-Xverify:none` / `-noverify` đã deprecated/không còn được hỗ trợ nhất quán qua các JDK mới. Bỏ verification có thể mở rủi ro an toàn kiểu nhớ/type, không chữa class format/version mismatch, và lỗi vẫn có thể xuất hiện ở resolution/initialization.

**Cách đúng:** tìm class bytes/transformer gây mismatch, cập nhật bộ tạo bytecode, recompute metadata/frames, verify với runtime hỗ trợ. Không sử dụng disabling verifier trong production để che lỗi `VerifyError`.

## 7. Bảng so sánh ba loại lỗi

| Tiêu chí | UnsupportedClassVersionError | ClassFormatError (khác subtype version) | VerifyError |
| --- | --- | --- | --- |
| Vị trí phân loại | Subclass của `ClassFormatError` | Subclass của `LinkageError` | Subclass của `LinkageError` |
| Câu hỏi đầu tiên | JVM có hỗ trợ **major/minor** không? | `.class` **có format hợp lệ** không? | Bytecode có **type/flow consistency** không? |
| Ví dụ gây lỗi | Java 21 class (65) chạy JVM17 (<=61) | `CAFEBABE` bị sửa thành header khác | `aconst_null` rồi `ireturn` |
| Sửa ưu tiên | Đúng runtime/`--release`/dependency | Khôi phục bytes đúng, sửa packaging/transformer | Sửa bytecode generation/stack maps |
| `-Xmx` có sửa trực tiếp? | Không | Không | Không |
| Dùng `javap -v`? | Xem major/minor nếu tool đọc được | File hỏng có thể không disassemble được | Xem instructions/StackMapTable nếu parse được |
| Có thể từ agent? | Có, nếu emit version sai | Có, nếu serialize bytes sai | Có, nếu emit type/frames sai |
| Có nhất thiết ở application startup? | Không | Không | Không |
| Có catch `Exception` như lỗi thường? | Không | Không | Không |

**Về superclass:** không nên trình bày thành ba `Error` hoàn toàn ngang hàng. `UnsupportedClassVersionError` là một loại cụ thể **nằm trong** `ClassFormatError`, còn `VerifyError` tách nhánh.

## 8. Thí nghiệm Java 17 runnable: tạo cả ba lỗi một cách có kiểm soát

Mục tiêu là minh họa **chính những lỗi ở runtime**, không giả lập bằng cách `throw new VerifyError()` bằng tay. Chúng ta biên dịch một classfile Java 17 hoàn toàn hợp lệ, rồi tạo **ba bản bytes riêng biệt trong memory** và đưa chúng qua `ClassLoader.defineClass`.

**Yêu cầu:**

- JDK **21** hoặc một HotSpot JVM tương đương còn chưa hỗ trợ class major 32767; code **được compile với API Java 17**. Chương trình đã được chạy và kiểm chứng trên OpenJDK 21.
- Không dùng thư viện ngoài.
- Chạy từ thư mục chỉ chứa hai source/classes của lab; không sửa bất kỳ JAR hay `.class` production nào.
- Verifier được bật bằng `-Xverify:all` khi chạy lab.
- `DemoBytecode` đơn giản được compile bằng `javac --release 17`, để pattern opcode có cấu trúc dự đoán được.

### 8.1. File `DemoBytecode.java`

~~~java
public final class DemoBytecode {
    public static int number() {
        return 1;
    }
}
~~~

Đây là **đích thử nghiệm**; không có main method. Khi compile bằng `--release 17`, bytecode của method `number` là `iconst_1; ireturn`.

### 8.2. File `LinkageErrorKindsDemo.java`

~~~java
import java.nio.file.Files;
import java.nio.file.Path;

public final class LinkageErrorKindsDemo {
    static final class BytesLoader extends ClassLoader {
        BytesLoader() {
            super(null);
        }

        Class<?> define(byte[] bytes) {
            return defineClass("DemoBytecode", bytes, 0, bytes.length);
        }
    }

    static void runCase(String label, byte[] bytes) {
        try {
            Class<?> target = new BytesLoader().define(bytes);
            target.getDeclaredMethod("number").invoke(null);
            System.out.println(label + "=NO_ERROR");
        } catch (LinkageError problem) {
            System.out.println(label + "=" +
                    problem.getClass().getSimpleName());
        } catch (ReflectiveOperationException problem) {
            throw new AssertionError(
                    "Unexpected reflection failure", problem);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("usage=path-to-DemoBytecode.class");
            return;
        }

        byte[] baseline = Files.readAllBytes(Path.of(args[0]));
        int major = ((baseline[6] & 0xff) << 8)
                  | (baseline[7] & 0xff);
        System.out.println("originalMajor=" + major);
        runCase("normal", baseline.clone());

        byte[] unsupported = baseline.clone();
        unsupported[6] = 0x7f;
        unsupported[7] = (byte) 0xff;
        runCase("unsupported", unsupported);

        byte[] malformed = baseline.clone();
        malformed[0] = 0;
        runCase("format", malformed);

        byte[] invalidCode = baseline.clone();
        int marker = -1;
        for (int i = 0; i + 1 < invalidCode.length; i++) {
            if ((invalidCode[i] & 0xff) == 0x04
                    && (invalidCode[i + 1] & 0xff) == 0xac) {
                marker = i;
            }
        }
        if (marker < 0) {
            throw new AssertionError(
                    "Could not locate iconst_1 followed by ireturn");
        }
        invalidCode[marker] = 0x01;
        runCase("verify", invalidCode);
    }
}
~~~

### 8.3. Biên dịch và chạy

Trong thư mục của hai files:

~~~bash
javac --release 17 DemoBytecode.java LinkageErrorKindsDemo.java
javap -c DemoBytecode
java -Xverify:all LinkageErrorKindsDemo DemoBytecode.class
~~~

**Stdout chính xác từ lần chạy đã kiểm chứng trên OpenJDK 21:**

~~~text
originalMajor=61
normal=NO_ERROR
unsupported=UnsupportedClassVersionError
format=ClassFormatError
verify=VerifyError
~~~

Ở dòng `normal`, class được loader riêng `BytesLoader` định nghĩa từ bytes **bản gốc**. Nó không phải class copy đã sửa. Với từng case, `new BytesLoader()` tạo một **defining classloader khác**; JVM cho phép có cùng binary name dưới những loader khác nhau, tránh xung đột “duplicate class definition” của chính lab.

### 8.4. Case `unsupported`: sửa major version

~~~java
unsupported[6] = 0x7f;
unsupported[7] = (byte) 0xff;
~~~

Hai bytes tạo **major = 32767** (unsigned 16-bit), cao hơn các major classfile được Java 21 hỗ trợ. VM từ chối header version và ném `UnsupportedClassVersionError` trong lab.

Đây là **classfile invalid do phiên bản unsupported một cách chủ động**, không phải build artifact Java bình thường. Không dùng kỹ thuật sửa header như cách nâng/hạ Java version trong production.

### 8.5. Case `format`: phá magic

~~~java
malformed[0] = 0;
~~~

Byte đầu của magic `CA FE BA BE` đổi thành `00 FE BA BE`. VM không coi đây là classfile có magic hợp lệ, dẫn đến `ClassFormatError`.

**Không phải version mismatch**: lỗi nảy sinh vì header format, không vì major/minor cần nâng runtime.

### 8.6. Case `verify`: thay opcode nhưng giữ file structurally parseable

~~~java
invalidCode[marker] = 0x01;
~~~

Method được javac tạo với bytes `0x04 0xac`:

- `0x04` = `iconst_1`, đẩy `int`.
- `0xac` = `ireturn`, cần `int`.
- `0x01` = `aconst_null`, đẩy object reference `null`.

Thay `0x04` bằng `0x01` nhưng giữ `ireturn`: type stack tại lệnh return không khớp declared return type `int`, nên **verifier** từ chối bằng `VerifyError`.

**Chú ý độ tin cậy của demo:**

- Vòng lặp scan chuỗi `04 AC` trong toàn file là **mẹo tối giản cho class có một method `number()` rất ngắn**, đã được chạy thử trên output cụ thể của `javac --release 17`. Đây **không phải classfile parser tổng quát**. Trong class phức tạp hơn, pattern trùng trong constant pool/attributes có thể làm patch sai byte, gây lỗi khác.
- `javap -c DemoBytecode` phải hiển thị `iconst_1; ireturn`; nếu compiler/toolchain khác làm bytecode khác, dừng lab hoặc dùng parser đúng `Code` attribute.
- `getDeclaredMethod(...).invoke(...)` thúc VM đi qua đường liên kết/xác minh cần thiết; **thời điểm error chính xác** có thể khác tùy JVM.
- Bản lab **không viết** ba file lỗi ra disk; các byte arrays chỉ tồn tại trong JVM process của lab.
- Do `LinkageError` được bắt, stdout không in stack trace dài. Trong production **nên lưu stack trace/cause đúng cách**, không chỉ tên simple class.

### 8.7. Vì sao không tự `throw new VerifyError()`?

~~~java
throw new VerifyError("demo");
~~~

Một dòng Java tự ném `VerifyError` chỉ chứng minh loại `Error` có constructor, **không chứng minh verifier phát hiện bytecode sai**. Ở đây ba lỗi đều phát sinh từ `ClassLoader.defineClass`/runtime validation trên bytes tương ứng, nên bài kiểm chứng nguyên lý.

## 9. Những error gần giống nhưng không được nhầm

Ba lỗi là một phần của họ **linkage-related failures**; production stack trace thường xuất hiện những loại khác:

| Lỗi | Thường hàm ý | Khác ba lỗi bài học thế nào |
| --- | --- | --- |
| `ClassNotFoundException` | Class loader/API tìm class theo tên nhưng không tìm thấy | Checked exception, không phải `LinkageError` |
| `NoClassDefFoundError` | Class definition cần thiết không khả dụng tại runtime hoặc class đã fail init trước | Là `LinkageError`, có thể do missing dependency/lifecycle, không mặc định format hỏng |
| `NoSuchMethodError` | Runtime resolution không tìm thấy method signature mong đợi | ABI/binary compatibility, không nhất thiết bytecode malformed |
| `NoSuchFieldError` | Runtime không tìm thấy field descriptor mong đợi | Binary mismatch, không phải format/header |
| `IllegalAccessError` | Bytecode cố truy cập class/member không được phép khi linking | Access control mismatch |
| `IncompatibleClassChangeError` | Class/interface/method relationship thay đổi không tương thích | ABI/type hierarchy issue |
| `AbstractMethodError` | Lời gọi tới abstract method không có implementation hợp lệ | Runtime binary compatibility |
| `ExceptionInInitializerError` | Static initializer `<clinit>` ném non-Error throwable | Class initialization failure, thường sau loading/linking |
| `UnsupportedClassVersionError` | Major/minor không được runtime hỗ trợ | **Subtype `ClassFormatError`** |
| `ClassFormatError` | Classfile malformed/unparseable | Format structures |
| `VerifyError` | Bytecode well-formed nhưng verifier thấy unsafe/inconsistent | Verification constraints |

**Không gộp `NoClassDefFoundError` với `ClassNotFoundException`**: một cái là `Error`, một cái là checked `Exception`, và cơ chế/stack trace khác. Khi classloader error xảy ra, **cause chain** và tên class/loader giúp tách biệt.

### `ExceptionInInitializerError` không phải `VerifyError`

Ví dụ:

~~~java
final class FailedInit {
    static {
        if (true) throw new RuntimeException("boom");
    }
}
~~~

Đây là **mảnh code minh họa**; class initializer cố tình lỗi. Khi VM initialize class, nó có thể ném `ExceptionInInitializerError`; lần sử dụng sau trong cùng defining loader có thể thấy `NoClassDefFoundError: Could not initialize class ...`. Không có nghĩa class header hay verifier đã thất bại.

## 10. Diagnostic workflow: xác minh từ đâu ra đúng bytes mà JVM nạp

Một quy trình thực tế:

1. Ghi đầy đủ **exception class**, message, cause và suppressed chain; không chỉ chụp dòng đầu.
2. Ghi **tên offending class**, classloader identity và source JAR/deployment module.
3. Xác minh đúng runtime Java đang chạy (`java -version`, executable path, container image).
4. Dùng `javap -verbose` với **artifact thực** để xem major/minor, constant pool/Code/StackMapTable.
5. Nếu `UnsupportedClassVersionError`: so major/minor với runtime; kiểm tra transitive deps, build release target, preview flags.
6. Nếu `ClassFormatError`: kiểm tra magic/length, JAR integrity, hashing, generated classes và transformation chain.
7. Nếu `VerifyError`: xem **method/BCI**, offending instruction, operand stack locals, branch target, StackMapTable, bytecode tool.
8. Kiểm tra classes có tên giống nhưng đến từ JAR/loader khác; classpath precedence, module path, shading, fat JAR, nested jar.
9. So build artifact trước và sau agent/instrumentation; isolate một transformation thay vì sửa ngẫu nhiên nhiều flags.
10. Test cùng JDK/vendor/version và exact deployment runtime; xây regression test cho class bytes đã gây lỗi.
11. Sửa **nguồn bytes** (build/transformer/dependency), không catch/disable verifier để production đi tiếp.
12. Nếu có nhiều deployed instances, thu dữ liệu version/bytes từ **instance đang lỗi**; không dùng bản JAR trên laptop làm đại diện chắc chắn.

### Các lệnh hữu ích

~~~bash
java -version
javac -version
javap -verbose -classpath app.jar com.example.Target
jar tf app.jar
jdeps --multi-release 17 --recursive app.jar
java '-Xlog:class+load=info' -jar app.jar
~~~

`app.jar` và `com.example.Target` là **tên minh họa**, phải thay bằng artifact và class thực. `jdeps` giúp xem dependencies/static module/class references, **không thay kiểm thử runtime loading/verifier**. `javap -v` có thể thất bại với corrupt/higher-major class nếu tool không hỗ trợ.

### HotSpot `-Xlog` để tìm nguồn class

Trên HotSpot JDK 17:

~~~bash
java '-Xlog:class+load=info:file=classload-%p.log:uptime,level,tags' -jar app.jar
~~~

Log class load giúp nhìn classes thực sự được VM báo đã load và nguồn class/JAR trong những dòng có thông tin ấy. **Không bảo đảm** mọi class đã load sẽ hoàn tất verification/initialization, và không tự cho biết cách transformer sửa mỗi byte.

Tag `verification` tồn tại trong `java -Xlog:help` trên một số JDK builds; có thể thử mức diagnostic phù hợp:

~~~bash
java '-Xlog:verification=debug' -cp . LinkageErrorKindsDemo DemoBytecode.class
~~~

**Hãy chạy `java -Xlog:help` trên build đang dùng trước khi bật selector**. Log volume/nội dung có thể khác JDK; không lấy việc thiếu message verification làm chứng minh class đã hợp lệ.

## 11. Build pipeline: phòng lỗi trước production

### 11.1. Pin target Java ở build

Với javac:

~~~bash
javac --release 17 DemoBytecode.java
~~~

Trong Maven `maven-compiler-plugin`, chọn release phù hợp:

~~~xml
<properties>
    <maven.compiler.release>17</maven.compiler.release>
</properties>
~~~

Đây là **cấu hình minh họa**, Maven project cần POM/hệ plugin thật. Gradle Java toolchains tương tự giúp build chọn compiler release mong muốn; riêng toolchain JDK version **không tự chứng minh `--release` của mọi compiler task đã đúng** nếu có tùy chỉnh.

### 11.2. Kiểm thử với runtime nhỏ nhất được hỗ trợ

Nếu ứng dụng công bố hỗ trợ Java 17:

- CI phải **chạy smoke/integration tests trên Java 17 runtime thật**.
- Inspect major versions của generated classes và dependencies, không chỉ app classes.
- Test cả plugin/agent/bytecode instrumentation paths được production bật.
- Không chỉ compile bằng JDK21 `--release 17` rồi run tests toàn bộ trên JDK21; test đó chưa chứng minh mọi dependency APIs tương thích JDK17.

### 11.3. Verify dependency graph chứ không chỉ `Main.class`

Một service có thể có `Main.class` major61 nhưng thư viện báo cáo chứa `ReportRenderer.class` major65, chỉ lỗi khi route/report chạy lần đầu. Mọi classfile **có thể được load ở những thời điểm khác nhau**, và lỗi có thể lộ ra sau readiness.

**Khuyến nghị:** kiểm tra nội dung JARs (kể cả shaded/nested JARs, generated classes), kiểm thử những code paths có lazy class loading, cùng runtime min version.

### 11.4. Multi-release JAR không nhất thiết sai

Multi-release JAR có manifest `Multi-Release: true` và entries trong `META-INF/versions/N/`. Trên runtime mới, classloader theo quy tắc multi-release có thể chọn class version-specific được thiết kế cho runtime ấy.

Không kết luận JAR hỏng chỉ vì **có một entry major65 nằm trong `META-INF/versions/21/`** nếu Java17 runtime không chọn entry đó và base entries hỗ trợ Java17. Nhưng nếu file bị **shading/repackaging** sai manifest/paths, runtime có thể chọn không như mong muốn. Phải inspect lựa chọn actual runtime/classloader.

### 11.5. ASM/Byte Buddy/Javassist/coverage plugins

Các thư viện tạo/chỉnh bytecode phải hiểu target classfile version, descriptors, StackMapTable và Java feature attributes tương ứng. Khi JDK/toolchain nâng:

- Update bytecode tool/agent version có hỗ trợ target.
- Test frame recomputation đúng context (hierarchy resolution qua defining classloader).
- Tránh cache transformed bytes của JDK này rồi sử dụng bừa trong JVM khác.
- Test đúng instrumentation pipeline theo thứ tự thực.
- Ghi checksum, class name, loader và build ID của **input/output bytes** để truy vết.
- Không trộn bytecode từ nhiều artifacts cũ/mới trong hot reload mà thiếu compatibility checks.

**Compiler/tool compatibility không tự bảo đảm correctness**: ngay cả phiên bản mới nhất của một transformer vẫn có thể generate bytecode sai do bug/configuration.

## 12. Case study A: build Java 21, deploy Java 17

~~~text
CI javac JDK21 (mặc định target21) → feature.jar with major65
                                     |
                                     v
runtime JDK17 loads feature.class (maximum major61)
                                     |
                                     v
UnsupportedClassVersionError
~~~

**Không khắc phục bằng:** tăng `-Xmx`, đổi GC, thêm RAM, `-noverify` hoặc vá 65 thành 61 bằng hex editor.

**Khắc phục hợp lý:** nâng runtime lên version hỗ trợ **hoặc** rebuild library/app `--release 17` sau khi bảo đảm code/API compatible Java17. Sau đó run smoke test trên **actual Java17 runtime**.

## 13. Case study B: JAR bị hỏng khi publish

~~~text
Valid bytecode (.class)
       |
       +-- upload/build artifact corruption / incorrect packaging
       |
       v
Unexpected bytes/truncated header/invalid constant-pool
       |
       v
ClassFormatError
~~~

**Kiểm tra:** hash SHA-256 giữa published JAR/CI build/deployed JAR, `jar tf`, archive integrity, image layer cache, artifact registry, bytecode source entry. Nếu HTML error page bị lưu nhầm thành `.class`, hex header sẽ không phải `CAFEBABE`.

**Khắc phục:** rebuild, republish, rollback artifact hoặc sửa classloader/resource handling. Không sửa class format bằng cách tăng `--release` nếu dữ liệu bị corrupt.

## 14. Case study C: instrumentation làm sai StackMapTable

~~~text
Valid javac bytecode
       |
       +-- agent chèn branch hoặc code ở giữa method
       |     nhưng không cập nhật frames/locals/cfg đúng
       |
       v
Class structurally parseable
       |
       v
VerifyError: bad type / frame mismatch
~~~

**Kiểm tra:** JVM agent flags, output transformed bytes (nếu công cụ hỗ trợ), `javap -v`, stack map frames/branch bytecode offsets, exact instrumenter version. So chạy không agent và có agent trong **môi trường thử nghiệm**.

**Khắc phục:** sửa rule transformation, cập nhật bytecode tool, tính frames đúng và có regression test trên real target runtime. **Không** để production tắt verification vì root cause vẫn còn.

## 15. Khi nào lỗi xuất hiện trễ dù application đã READY?

Không phải toàn bộ application classes bắt buộc được load/linked/verified ngay lúc JVM start:

- Một HTTP route ít dùng nạp controller/serializer lần đầu.
- Một plugin được kích hoạt bởi feature flag.
- Reflection gọi class chưa có trong startup path.
- Classloader theo tenant tạo generated proxies lúc nhận tenant mới.
- Bytecode agent chỉ instrument method khi hot/retransform.
- Multi-release dependency chỉ đến lúc service gọi path Java-specific.

Do đó **pass startup smoke test không chứng minh bytecode cả ứng dụng hợp lệ**. Muốn bảo đảm cần test critical/lazy paths trên **min supported runtime và exact artifacts**.

### Đồng thời: JVM có thể báo lỗi không tại dòng `new ClassLoader`

Ví dụ class chưa được resolve/verify toàn bộ trong một lần load. Sau đó `getDeclaredMethod()` hoặc invocation mới làm VM xử lý phần cần thiết, nên lỗi lộ ra ở stack trace lúc reflection. **Không nhầm** rằng reflection API tạo bytecode hỏng; lỗi có thể nằm trong artifact được load trước đó.

## 16. Những ngộ nhận phổ biến

1. **“UnsupportedClassVersionError, ClassFormatError, VerifyError là ba siblings.”** Sai: UnsupportedClassVersionError extends ClassFormatError.
2. **“ClassFormatError luôn do JDK quá cũ.”** Sai: có thể do class bytes malformed.
3. **“VerifyError là javac source type error.”** Sai: JVM verifier kiểm tra bytecode ở runtime; source javac thường đã type-check.
4. **“`--release 17` làm runtime thành Java17.”** Sai: chỉ đặt compile target/API rules, runtime thật vẫn là JVM đang chạy.
5. **“Chỉ đổi major65 thành major61 là downgrade Java21 code.”** Sai.
6. **“Java17 chạy được mọi dependency nếu Main.class major61.”** Sai: mỗi dependency class có target riêng.
7. **“Verifier có thể bị tắt an toàn để chữa lỗi.”** Không; không khuyến nghị, có rủi ro security/correctness.
8. **“Một class well-formed chắc chắn pass verifier.”** Sai: internal inconsistency vẫn có thể gây VerifyError.
9. **“Một class file không có CAFEBABE sẽ luôn là VerifyError.”** Sai: malformed magic → ClassFormatError trong lab.
10. **“Class version chỉ một số major, minor không quan trọng.”** Sai với preview.
11. **“Bật `--enable-preview` trên JDK mới chạy mọi class preview cũ.”** Sai.
12. **“`ClassFormatError` và `NoClassDefFoundError` có cùng nghĩa.”** Sai.
13. **“`ClassNotFoundException` là LinkageError.”** Sai: nó là checked Exception.
14. **“`ExceptionInInitializerError` chứng minh classfile format hỏng.”** Sai.
15. **“`java -version` trên laptop đủ xác nhận runtime production.”** Sai.
16. **“Một build pipeline dùng JDK21 nhưng `--release17` thì mọi JARs chắc target17.”** Sai: dependencies/generated classes vẫn phải kiểm tra.
17. **“Multi-release JAR chứa Java21 class nghĩa Java17 deploy chắc fail.”** Không nhất thiết: runtime chọn đúng versioned entries khi JAR/loader chuẩn.
18. **“`javap` đọc không được nghĩa class chắc corrupt.”** Có thể tool quá cũ/unsupported major.
19. **“Bất kỳ thao tác đụng class đều phải initialize `<clinit>`.”** Sai; load/link và initialization tách biệt.
20. **“Class loader parent=null giúp production class luôn an toàn.”** Sai; chỉ cách cô lập class name trong lab.
21. **“Demo VerifyError chỉ là throw new VerifyError.”** Sai; lab kích verifier bằng modified opcode.
22. **“Tăng heap sẽ giải được lỗi format/version.”** Sai.
23. **“Một lần `NoClassDefFoundError` luôn vì file không tồn tại.”** Sai; còn prior init failure và dependency/linking conditions.
24. **“`-Xlog:class+load` tự chứa đầy đủ bytecode transformer history.”** Sai.
25. **“Một transformer thay opcode mà không sửa frames vẫn luôn chạy.”** Sai; verifier có thể phát hiện type inconsistency.
26. **“Verification là optional security decoration.”** Sai; là phần bảo đảm VM type safety.
27. **“Dòng stack trace nơi VerifyError bật là nơi code hỏng được sinh.”** Không nhất thiết.
28. **“Một JAR có class major mới hơn runtime sẽ lỗi ngay dù class ấy không bao giờ được load.”** Không bắt buộc; lỗi chỉ phát sinh khi VM cần xử lý class không hỗ trợ.
29. **“Đổi `JAVA_HOME` mà không đổi actual `java` binary sẽ sửa runtime.”** Không chắc: PATH/container entrypoint có thể override.
30. **“Lỗi `UnsupportedClassVersionError` là lỗi compilation.”** Nó là runtime class-reading failure; compilation có thể đã thành công trên JDK khác.

## 17. Quy trình xử lý sự cố thực tế (runbook)

1. Lưu **type/message/stack trace/cause** chính xác; phân biệt `UnsupportedClassVersionError` với `ClassFormatError` và `VerifyError`.
2. Ghi **class binary name**, method/BCI nếu message verification có thông tin ấy, và classloader identity.
3. Đối chiếu **actual Java runtime** của instance đang lỗi với target classfile version.
4. Đọc `javap -v`/header cho offending `.class`, không chỉ `Main.class`.
5. Kiểm tra artifact SHA256 và `jar tf`; có nhiều JARs/class duplicates hay nested JAR không?
6. Với version issue, xác minh `--release` và min supported runtime; runtime version và preview flags.
7. Với format issue, kiểm tra magic, constant-pool/attribute parsing và corrupted byte streams.
8. Với verifier issue, kiểm tra operand stack/local types, branch targets, StackMapTable, agent transformation.
9. Xác minh JDK vendor/build, class loader order, `-javaagent` flags và bytecode libraries.
10. Tái hiện trong **isolated test environment** với đúng JDK/runtime, plugins và deployment artifact.
11. Tránh sửa class bytes production thủ công; tạo artifact mới có version/hash và tests.
12. Không tắt verifier hoặc nuốt `LinkageError` để giữ một service có behavior không xác định.
13. Rollback/nâng runtime/rebuild dependency/transformer theo root cause đã tìm.
14. Thử lại **critical lazy-loaded code paths**, startup, config flags, plugins và hot reload nếu có.
15. Ghi regression test và CI gate đọc class major/minor/corrupt bytes để ngăn tái phát.

### Câu hỏi “ai tạo ra offending bytes?”

Hãy trả lời cụ thể:
- Javac version/`--release` nào?
- Build tool/plugin version nào?
- Artifact/JAR checksum ở CI/registry/production?
- Có shading/repackaging/minification không?
- Có classloader custom/agent/transformer không?
- Runtime/JDK nào đã thực sự load class?

Câu hỏi ấy thường giá trị hơn việc thử random JVM flags.

## 18. Bài tập kiểm tra và đáp án

1. **`UnsupportedClassVersionError` extends class nào?** `ClassFormatError`.
2. **`VerifyError` extends class nào?** `LinkageError`.
3. **Java17 class major bằng?** 61.
4. **Java21 class major bằng?** 65.
5. **Class major 65 load trên JDK17 thành công không?** Không theo runtime chuẩn; thường `UnsupportedClassVersionError`.
6. **Java17 preview class có minor nào?** 65535.
7. **JDK21 bật preview có tự chạy preview Java17 class không?** Không.
8. **Byte đầu của `.class` magic theo chuẩn?** `CA FE BA BE`.
9. **Magic sai thường cho lỗi nào?** `ClassFormatError`.
10. **`aconst_null` rồi `ireturn` với declared method trả int gây lỗi gì?** `VerifyError` trong lab.
11. **Vì sao `javac` bình thường hiếm sinh VerifyError?** Compiler type-check và emit valid bytecode/frames theo rules.
12. **`--release 17` khác `-target 17` ở điểm nào?** Có compile-time platform API restrictions, không chỉ classfile target.
13. **Chỉ vá class major để chạy JDK17 có an toàn không?** Không.
14. **ClassFormatError bắt được UnsupportedClassVersionError không?** Có, vì quan hệ extends.
15. **ClassNotFoundException là một LinkageError không?** Không, checked exception.
16. **ExceptionInInitializerError có nghĩa class bytes corrupted?** Không; static initialization code ném lỗi.
17. **Main.class major61 có chứng minh tất cả deps major61 không?** Không.
18. **`javap -v` giúp xem gì?** Version, constant pool, methods, Code, StackMapTable (nếu tool đọc được).
19. **`-Xlog:class+load` có tự chứng minh class đã init?** Không.
20. **Nếu agent sinh VerifyError sau khi nâng JDK, ưu tiên điều tra gì?** Compatibility/tool version và transformed frames/bytecode.
21. **Với corrupt JAR, nâng `Xmx` có ích không?** Không.
22. **`catch (Exception)` có bắt LinkageError không?** Không.
23. **JVM có thể phát hiện lỗi ở lần dùng class sau startup?** Có.
24. **Multi-release JAR chứa `/versions/21` có luôn sai trên JDK17 không?** Không nếu loader xử lý manifest/entries đúng.
25. **Có cần JVM classfile verifier khi dùng generated code?** Có.
26. **`-noverify` là cách sửa lâu dài?** Không.
27. **Lab cần sửa artifact production để tái hiện lỗi không?** Không; dùng bytes clone trong memory.
28. **Để phân loại đúng phải lưu gì ngoài error name?** Class name, bytes/source JAR, classloader, runtime, toolchain và cause chain.

## 19. Nguồn chính thống và tài liệu chuyên sâu

- **Javadoc Java SE 17 — `UnsupportedClassVersionError`** (extends `ClassFormatError`; major/minor unsupported): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/UnsupportedClassVersionError.html
- **Javadoc Java SE 17 — `ClassFormatError`** (malformed/uninterpretable class file): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassFormatError.html
- **Javadoc Java SE 17 — `VerifyError`** (class well-formed, verifier inconsistency): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/VerifyError.html
- **Javadoc Java SE 17 — `LinkageError`**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/LinkageError.html
- **JVMS 17 — Chapter 4 `ClassFile` structure/major-minor/format verification**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- **JVMS 17 — Chapter 5 Loading, Linking and Initializing**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- **JLS 17 — Chapter 12 Execution**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html
- **Oracle Java SE 17 `javac` command**, `--release` và target: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html
- **Oracle Java SE 17 `java` command** (`--enable-preview`, classloading logs): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Oracle Java SE 17 `javap`** (`-c`, `-verbose`): https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html
- **OpenJDK ASM project**, bytecode reading/writing/verification practices: https://asm.ow2.io/

**Kết luận kỹ thuật:** Ba lỗi đều xuất hiện khi JVM cố xử lý class bytes/linkage, nhưng cần phân biệt **phiên bản classfile không hỗ trợ** (`UnsupportedClassVersionError`), **định dạng không parse được** (`ClassFormatError`) và **bytecode type/stack/flow không nhất quán** (`VerifyError`). Quan hệ kế thừa đúng là `UnsupportedClassVersionError extends ClassFormatError extends LinkageError`, còn `VerifyError extends LinkageError`. Không dùng `-Xmx`, `-noverify` hay đổi major header làm “fix”. Hãy xác minh **JDK đang chạy, major/minor, artifact/loader thật và transformer output**, tái biên dịch/nâng runtime/sửa format/frames theo nguyên nhân, rồi thêm CI regression tests trên minimum supported runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-010">NoSuchMethodError, IllegalAccessError và các lỗi không tương thích nhị phân</a>

<details>
<summary>Xem chi tiết</summary>

# NoSuchMethodError, IllegalAccessError và các lỗi không tương thích nhị phân

## 1. Vì sao chương trình biên dịch được nhưng runtime lại lỗi?

Một ứng dụng Java có thể đã biên dịch thành công nhưng ở production bất ngờ ném:

~~~text
java.lang.NoSuchMethodError: ...
java.lang.IllegalAccessError: ...
java.lang.IncompatibleClassChangeError: ...
java.lang.NoSuchFieldError: ...
java.lang.AbstractMethodError: ...
~~~

Nguyên nhân thường không phải cú pháp Java source hiện tại mà là **không tương thích nhị phân (binary incompatibility)**: bytecode của client được biên dịch với một phiên bản thư viện, nhưng JVM runtime nạp một class định nghĩa khác. Những lỗi như vậy có thể do:

- Thư viện thay đổi tên, parameters, return type hoặc xóa method/constructor.
- API member thay `public` thành `private`/package-private/protected.
- Method hoặc field đổi giữa `static` và instance.
- Dependency transitively kéo phiên bản không khớp.
- Fat JAR, application server hoặc custom class loader chứa một `.class` khác với class được build.
- Build cache/container layer/IDE output vẫn giữ client cũ khi runtime library đã cập nhật.
- Module exports hoặc hierarchy của class/interface thay đổi.

**Không phải mọi runtime failure là linkage error.** Phải nhận diện **chính xác loại error, symbolic reference mà caller cần và class implementation đã được nạp**.

### Kết quả học tập

Sau bài này, bạn sẽ:

1. Phân biệt **source compatibility**, **binary compatibility** và **behavioral compatibility**.
2. Hiểu JLS §13 và JVMS §5.4 quy định gì về linking, resolution và access control.
3. Dùng `javap` xem **method descriptors, call opcodes và access flags**.
4. Thực hành chạy **client cũ với library v1/v2** để tái hiện `NoSuchMethodError` và `IllegalAccessError`.
5. Đọc runtime class-loading log, xác định chính JAR/class directory được JVM nạp.
6. Chẩn đoán các lỗi liên quan, dependency conflicts, JPMS/class loaders, rồi sửa theo quy trình có kiểm chứng.

Bài dùng **Java 17+**, không cần Maven/Gradle cho hai thí nghiệm chính; các commands build-tool chỉ dùng khi điều tra ứng dụng có build tool thật.

## 2. Source, binary và behavioral compatibility không giống nhau

| Lớp tương thích | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Source compatibility** | Source cũ có compile với API mới không? | `Api.message()` compile thất bại khi API chỉ có `message(int)` |
| **Binary compatibility** | Client `.class` cũ còn link với library `.class` mới không? | Có thể ném `NoSuchMethodError` do descriptor cũ mất |
| **Behavioral compatibility** | Kết quả/side effects còn đáp ứng hợp đồng không? | Method vẫn tồn tại nhưng cách tính thay đổi |

**JLS §13.2** định nghĩa binary compatibility dựa vào việc pre-existing binaries từng link được vẫn tiếp tục link được sau khi type thay đổi. Điều này không hứa source vẫn compile hoặc ứng dụng vẫn đúng nghiệp vụ.

Ví dụ đổi method body:

~~~java
// v1: fragment method
public static int price() {
    return 10;
}

// v2: fragment method
public static int price() {
    return 20;
}
~~~

Hai fragments minh họa là **hai phiên bản khác nhau**, không phải hai methods được đặt chung một class. Method name, parameters, return type và access không đổi; client binary thông thường vẫn link được nhưng output nghiệp vụ đổi.

**Bài học:** không thể dùng “unit test source v2 pass” để chứng minh **client v1 binary** vẫn chạy được với library v2.

## 3. Cây phân loại những lỗi quan trọng

~~~text
java.lang.Throwable
 └─ java.lang.Error
     └─ java.lang.LinkageError
         ├─ IncompatibleClassChangeError
         │   ├─ NoSuchMethodError
         │   ├─ NoSuchFieldError
         │   ├─ IllegalAccessError
         │   └─ AbstractMethodError
         ├─ NoClassDefFoundError
         ├─ ClassFormatError
         │   └─ UnsupportedClassVersionError
         └─ VerifyError
~~~

Sơ đồ không liệt kê mọi subclass của `LinkageError`. Hai lỗi chính **`NoSuchMethodError`** và **`IllegalAccessError`** đều thuộc **`IncompatibleClassChangeError`** trong Java SE API.

| Lỗi | Điều đáng nghi trước tiên |
| --- | --- |
| **NoSuchMethodError** | Method name + JVM descriptor không còn ở runtime owner/hierarchy |
| **IllegalAccessError** | Class/member có nhưng caller không được truy cập |
| **NoSuchFieldError** | Field name/type descriptor bị mất/đổi |
| **IncompatibleClassChangeError** | Invocation kind/field kind/class-interface không phù hợp |
| **AbstractMethodError** | Không có concrete implementation cần thiết khi invoke |
| **NoClassDefFoundError** | Runtime không tìm/tạo class definition được khi cần, hoặc initialization từng thất bại |
| **UnsupportedClassVersionError** | `.class` có major version cao hơn JVM hỗ trợ |
| **VerifyError** | Verifier không chấp nhận bytecode hoặc constraints |

**Không suy mọi thứ từ một từ “method” trong thông báo.** Phải xem class owner và descriptor chính xác.

## 4. Bytecode tham chiếu method bằng tên và descriptor

Trong Java source:

~~~java
String value = Library.message();
~~~

đây là **fragment**, chưa chạy độc lập. Sau khi compile, client bytecode thường chứa symbolic reference tương tự:

~~~text
owner:      lib/Library
name:       message
descriptor: ()Ljava/lang/String;
opcode:     invokestatic
~~~

Các thành phần:

- **Owner class** là class/interface được client tham chiếu.
- **Method name** là tên ở class-file constant pool.
- **Descriptor** chứa parameter types **và return type**.
- **Invocation opcode** thể hiện static, virtual, interface hoặc special invocation.
- **Defining loader/module context** ảnh hưởng identity và accessibility.

| Java method | JVM descriptor |
| --- | --- |
| `void run()` | `()V` |
| `String message()` | `()Ljava/lang/String;` |
| `String message(int x)` | `(I)Ljava/lang/String;` |
| `long sum(int[] values)` | `([I)J` |
| `void set(String s)` | `(Ljava/lang/String;)V` |

`I` là int, `J` là long, `V` là void; `Lpkg/Type;` là reference, `[` là array.

**Thay return type cũng có thể đổi binary descriptor.** Ví dụ `String get()` có descriptor `()Ljava/lang/String;` còn `CharSequence get()` là `()Ljava/lang/CharSequence;`. Theo JLS `13.4.15`, đổi result type tương đương xóa method cũ và thêm method mới xét về binary compatibility (một số compiler-generated bridges có thể giúp trong tình huống đặc biệt).

### Overload không được chọn lại tùy ý ở runtime

Giả sử client compile với `message()` rồi library mới chỉ có `message(int)`. JVM **không tự thêm `0` làm argument**, cũng không chạy overload resolution của Java source lại. Bytecode vẫn chỉ rõ `message:()Ljava/lang/String;`.

## 5. Loading, linking, resolution và initialization

JVMS `5` mô tả:

1. **Loading:** một classloader tìm class bytes, tạo class/interface representation.
2. **Linking:** verification, preparation, resolution symbolic references.
3. **Initialization:** thực thi `<clinit>` của class/interface theo điều kiện đặc tả.

Một method reference có thể chỉ được resolve khi đường gọi tương ứng được sử dụng; implementation cũng có thể thực hiện eager resolution trong những giới hạn của JVMS. Vì thế **không phải mọi lỗi binary đều xuất hiện lúc startup**.

~~~text
Client.class: invokestatic lib.Library.message:()String
                          |
                          v
                 Load owner class
                          |
                          v
                  Resolve member
                     /         \
         Method lookup fails    Found matching method
                  |                     |
         NoSuchMethodError       Access check
                                    /       \
                              Denied       Allowed
                                |             |
                         IllegalAccessError  Invocation checks
                                                    |
                                                    v
                                            Execute selected method
~~~

**Sơ đồ chỉ mô tả các bước khái niệm**, không thay thuật toán đầy đủ ở JVMS §5.4.3 và §5.4.4. Method inheritance, maximally-specific superinterface methods, loader constraints và invoke-kind checks có rules chi tiết.

### Ví dụ lazy failure

~~~java
if (featureEnabled) {
    Library.message();
}
~~~

Đoạn này là fragment minh họa. Nếu feature never runs, lỗi missing method có thể chưa bộc lộ. Vì vậy **smoke test khởi động thành công chưa chứng minh mọi đường gọi của binary đã tương thích**.

## 6. Thực nghiệm A: NoSuchMethodError giữa library v1 và v2

**Quy tắc:** compile **client với library v1**, không compile client lại, rồi run **cùng Client.class với library v2** thiếu method cũ. Đây là điểm mấu chốt; nếu biên dịch client với v2, compiler bắt mismatch và ta không tái hiện được **runtime binary linkage**.

Thư mục:

~~~text
nsm/
  v1-src/lib/Library.java
  v2-src/lib/Library.java
  client-src/demo/Client.java
  out/
    v1/
    v2/
    client/
~~~

### 6.1. File thư viện v1

Lưu **`nsm/v1-src/lib/Library.java`**:

~~~java
package lib;

public final class Library {
    private Library() { }

    public static String message() {
        return "library-v1";
    }
}
~~~

Định nghĩa API cho client: `public static String message()`, descriptor `()Ljava/lang/String;`.

### 6.2. Client dùng API cũ

Lưu **`nsm/client-src/demo/Client.java`**:

~~~java
package demo;

import lib.Library;

public final class Client {
    public static void main(String[] args) {
        System.out.println("before-call");
        System.out.println("message=" + Library.message());
        System.out.println("after-call");
    }
}
~~~

### 6.3. Library v2 thiếu method cũ

Lưu **`nsm/v2-src/lib/Library.java`**:

~~~java
package lib;

public final class Library {
    private Library() { }

    // Không còn message():String của v1.
    public static String message(int code) {
        return "library-v2-" + code;
    }
}
~~~

Method mới có descriptor `(I)Ljava/lang/String;`, không khớp với reference đã compile của client.

### 6.4. Biên dịch — Linux/macOS Bash

Từ thư mục cha chứa `nsm` và sau khi tạo ba files:

~~~bash
mkdir -p nsm/out/v1 nsm/out/v2 nsm/out/client

javac -d nsm/out/v1 nsm/v1-src/lib/Library.java

javac -cp nsm/out/v1 -d nsm/out/client \
  nsm/client-src/demo/Client.java

javac -d nsm/out/v2 nsm/v2-src/lib/Library.java
~~~

**Không chạy lại `javac` với client khi thay v1 thành v2.**

### 6.5. Đối chứng với thư viện v1

~~~bash
java -cp nsm/out/client:nsm/out/v1 demo.Client
~~~

**Stdout chính xác:**

~~~text
before-call
message=library-v1
after-call
~~~

### 6.6. Gây lỗi với thư viện v2

~~~bash
java -cp nsm/out/client:nsm/out/v2 demo.Client
~~~

**Stdout trước lỗi:**

~~~text
before-call
~~~

**Dạng stderr dự kiến, không cố định exact wording:**

~~~text
Exception in thread "main" java.lang.NoSuchMethodError: ...
    at demo.Client.main(Client.java:...)
~~~

Không có `message=library-v1` và `after-call`. Process thường exit với status khác 0. Error detail có thể chứa tên class/method/return type tùy JVM version.

**Kết luận:** Client cần `lib.Library.message:()Ljava/lang/String;` nhưng runtime v2 chỉ có `message:(I)Ljava/lang/String;`. Đây là lỗi lookup method ở thời điểm JVM resolve.

### 6.7. Windows PowerShell

Tạo files với nội dung y hệt và chạy:

~~~powershell
New-Item -ItemType Directory -Force nsm/out/v1,nsm/out/v2,nsm/out/client | Out-Null
javac -d nsm/out/v1 nsm/v1-src/lib/Library.java
javac -cp nsm/out/v1 -d nsm/out/client nsm/client-src/demo/Client.java
javac -d nsm/out/v2 nsm/v2-src/lib/Library.java

java -cp "nsm/out/client;nsm/out/v1" demo.Client
java -cp "nsm/out/client;nsm/out/v2" demo.Client
~~~

**Khác biệt hệ điều hành:** Java classpath separator là `:` trên Linux/macOS và `;` trên Windows. Dùng nhầm có thể khiến classpath bị parse sai, gây main-class-not-found thay vì `NoSuchMethodError`.

## 7. Xác minh NoSuchMethodError bằng javap

~~~bash
javap -classpath nsm/out/client -c -p demo.Client
javap -classpath nsm/out/client -v demo.Client
javap -classpath nsm/out/v1 -p -s lib.Library
javap -classpath nsm/out/v2 -p -s lib.Library
~~~

Điểm cần tìm trong caller:

~~~text
invokestatic ... // Method lib/Library.message:()Ljava/lang/String;
~~~

Đây là **mẫu dòng liên quan**, không hứa instruction offsets hay constant pool indexes giống nhau. Java compiler có thể dùng `invokedynamic` cho string concatenation quanh method call, nhưng method reference tới `Library.message` vẫn có.

Trong v1:

~~~text
public static java.lang.String message();
    descriptor: ()Ljava/lang/String;
~~~

Trong v2:

~~~text
public static java.lang.String message(int);
    descriptor: (I)Ljava/lang/String;
~~~

**Đừng so chỉ method name**: JVM cần match cả descriptor. Các tools:

- `javap -c`: disassemble opcodes.
- `javap -s`: show JVM descriptors.
- `javap -p`: include private members.
- `javap -v`: detailed constant pool, flags, attributes.

Đây là cách chứng minh vấn đề chính xác hơn nhìn hai file Java trong IDE.

## 8. Thực nghiệm B: IllegalAccessError do thu hẹp quyền truy cập

Khác demo A, giờ library v2 **giữ nguyên tên và descriptor** nhưng chuyển method từ public sang private. JVM tìm thấy method nhưng caller không có quyền truy cập.

Thư mục độc lập:

~~~text
access/
  v1-src/lib/Gate.java
  v2-src/lib/Gate.java
  client-src/demo/AccessClient.java
  out/v1/
  out/v2/
  out/client/
~~~

### 8.1. Library v1 có public method

File **`access/v1-src/lib/Gate.java`**:

~~~java
package lib;

public final class Gate {
    private Gate() { }

    public static String open() {
        return "gate-v1";
    }
}
~~~

### 8.2. Client compile against v1

File **`access/client-src/demo/AccessClient.java`**:

~~~java
package demo;

import lib.Gate;

public final class AccessClient {
    public static void main(String[] args) {
        System.out.println("before-call");
        System.out.println("value=" + Gate.open());
        System.out.println("after-call");
    }
}
~~~

### 8.3. Library v2 có private method cùng descriptor

File **`access/v2-src/lib/Gate.java`**:

~~~java
package lib;

public final class Gate {
    private Gate() { }

    // API v1 đã mất quyền truy cập public.
    private static String open() {
        return "gate-v2";
    }
}
~~~

`Gate.open:()Ljava/lang/String;` vẫn tồn tại nhưng có `ACC_PRIVATE`. `demo.AccessClient` là class khác package, không được access member này theo JVM rules.

### 8.4. Build và chạy — Linux/macOS

~~~bash
mkdir -p access/out/v1 access/out/v2 access/out/client

javac -d access/out/v1 access/v1-src/lib/Gate.java

javac -cp access/out/v1 -d access/out/client \
  access/client-src/demo/AccessClient.java

javac -d access/out/v2 access/v2-src/lib/Gate.java

java -cp access/out/client:access/out/v1 demo.AccessClient
~~~

**Stdout v1:**

~~~text
before-call
value=gate-v1
after-call
~~~

Giữ nguyên client đã compile, thay classpath library thành v2:

~~~bash
java -cp access/out/client:access/out/v2 demo.AccessClient
~~~

**Stdout trước khi lỗi:**

~~~text
before-call
~~~

**Stderr kiểu điển hình:**

~~~text
Exception in thread "main" java.lang.IllegalAccessError: ...
    at demo.AccessClient.main(AccessClient.java:...)
~~~

Thông báo chi tiết có thể nêu tên method private, class/module loaders. Không có `value=gate-v2` hay `after-call`.

### 8.5. Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force access/out/v1,access/out/v2,access/out/client | Out-Null
javac -d access/out/v1 access/v1-src/lib/Gate.java
javac -cp access/out/v1 -d access/out/client access/client-src/demo/AccessClient.java
javac -d access/out/v2 access/v2-src/lib/Gate.java

java -cp "access/out/client;access/out/v1" demo.AccessClient
java -cp "access/out/client;access/out/v2" demo.AccessClient
~~~

### 8.6. Xác minh quyền truy cập bằng javap

~~~bash
javap -classpath access/out/client -c demo.AccessClient
javap -classpath access/out/v1 -p -v lib.Gate
javap -classpath access/out/v2 -p -v lib.Gate
~~~

Client cũ vẫn có:

~~~text
invokestatic ... // Method lib/Gate.open:()Ljava/lang/String;
~~~

Trong v1, method flags có `ACC_PUBLIC`, `ACC_STATIC`. Trong v2, flags có `ACC_PRIVATE`, `ACC_STATIC`. **Descriptor không đổi** nên nguyên nhân nổi bật là **access control**, khác với demo A.

## 9. Tại sao compiler không ngăn được các runtime errors?

~~~text
Compile phase:
  Client.java + Library v1
       |
       v
  javac validates API accessibility/signatures
       |
       v
  Client.class (symbolic refs tới v1)

Runtime phase:
  Client.class + Library v2
       |
       v
  JVM resolves refs against v2
       |
       +-- Method missing -> NoSuchMethodError
       |
       +-- Method inaccessible -> IllegalAccessError
~~~

Nếu **recompile client source với v2**, Java compiler có thể báo lỗi ngay; đó là **source incompatibility**. Nhưng client binary cũ **không được `javac` tự động rebuild** chỉ vì server đã cài library mới. Vì vậy binary compatibility là trách nhiệm riêng khi nâng dependencies.

**Đừng coi clean rebuild là chữa trị phổ quát**. Nếu application cần API không còn trên v2, một clean build có thể đơn giản làm lỗi xuất hiện sớm ở compile time, chứ chưa giải quyết dependency incompatibility.

## 10. Các thay đổi khác gây binary linkage failures

| Library change | Rủi ro cho old binary | Lưu ý |
| --- | --- | --- |
| Xóa method | `NoSuchMethodError` | Nếu không có matching inherited method |
| Đổi số/type parameters | `NoSuchMethodError` | Descriptor khác |
| Đổi return type | `NoSuchMethodError` | JVM descriptor chứa return type |
| Public → private/package | `IllegalAccessError` | Access check khi resolve |
| Static → instance method | `IncompatibleClassChangeError` | Invocation opcode sai kind |
| Xóa/đổi type field | `NoSuchFieldError` | Field descriptor khác |
| Static ↔ instance field | `IncompatibleClassChangeError` | `getstatic`/`getfield` không khớp |
| Concrete → abstract method | `AbstractMethodError` | Không có concrete implementation |
| Đổi superclass/interface graph | Nhiều linkage failures | Tùy lookup/selection/loader constraints |
| Đổi module exports | `IllegalAccessError` có thể xảy ra | JPMS boundaries |
| Thêm method overload | Thường giữ client old references | Client cũ không tự chọn overload mới |

**Không khẳng định mỗi change luôn sinh đúng một error**; kết quả cuối cùng phụ thuộc JVMS resolution, hierarchy, invoke kind và context.

### Method return type

~~~java
// v1 fragment
public static String load() { return "ok"; }

// v2 fragment
public static CharSequence load() { return "ok"; }
~~~

Descriptor thay `()Ljava/lang/String;` thành `()Ljava/lang/CharSequence;`. JLS `13.4.15` xem việc này như remove old method/add new method cho mục đích binary compatibility. Compiler-generated bridge methods có thể giúp ở một số quan hệ inheritance/covariant returns, nhưng không phải giải pháp tự động cho mọi API change.

### Interface evolution và AbstractMethodError

Việc thêm abstract method vào interface không nhất thiết tự phá **binaries cũ** theo JLS §13.5. Nhưng nếu client mới gọi method đó trên object thuộc **implementation cũ không triển khai method**, `AbstractMethodError` có thể xuất hiện. Phân biệt:

1. Old client gọi old method trên new interface.
2. New client gọi new method trên old implementation.

Compatibility không phải một mũi tên hai chiều.

## 11. Trường hợp ngược lại: không ném lỗi nhưng dùng constant cũ

Với **compile-time constants**:

~~~java
public final class Settings {
    public static final String LABEL = "v1";
}
~~~

và client fragment:

~~~java
System.out.println(Settings.LABEL);
~~~

Compiler có thể **inline `"v1"` vào Client.class**. Nếu library mới sửa constant thành `"v2"` nhưng client không rebuild, client có thể vẫn in `v1`, **không ném NoSuchFieldError**. JLS `13.4.9` đề cập vấn đề này.

Vì vậy “không có linkage error” **không bảo đảm client binary đã hấp thụ thay đổi API/value**. Với library quan trọng, cần binary compatibility tests **và behavioral tests**.

## 12. NoSuchMethodError khác NoSuchMethodException

Hai tên gần giống nhưng thuộc cơ chế khác:

| Loại | Bản chất | Ví dụ |
| --- | --- | --- |
| `NoSuchMethodError` | JVM linkage error, subclass `IncompatibleClassChangeError` | `invokestatic` resolve missing descriptor |
| `NoSuchMethodException` | Checked reflection exception | `Class.getMethod(...)` tìm không thấy |
| `IllegalAccessError` | JVM bytecode access-check failure | Old binary gọi private method |
| `IllegalAccessException` | Reflection access exception | `Method.invoke(...)` không được phép |
| `InaccessibleObjectException` | Runtime exception ở reflective access | JPMS strong encapsulation chặn `setAccessible` |

**Không dùng `--add-opens` để sửa một private API thành public.** `--add-opens` chủ yếu mở quyền reflective access; normal bytecode linking còn chịu Java/JVM accessibility và module exports rules.

## 13. NoClassDefFoundError, ClassNotFoundException và UnsupportedClassVersionError

- **`NoClassDefFoundError`**: JVM cần class definition nhưng không thể có class tại resolution/init time. Ngoài missing JAR, lỗi này có thể xảy ra khi class initialization đã thất bại trước đó.
- **`ClassNotFoundException`**: checked exception ở class-loading API như `Class.forName`, không tự đồng nghĩa binary method mismatch.
- **`UnsupportedClassVersionError`**: runtime JVM không hỗ trợ `.class` version đã biên dịch.

Cách kiểm tra:

~~~bash
java -version
javap -verbose SomeClass
~~~

`SomeClass` là placeholder thay bằng class thực. `javap -verbose` cho major class-file version; **không dùng method-error troubleshooting nếu vấn đề là runtime Java version quá cũ**.

## 14. Classpath: phiên bản thực sự được load quan trọng hơn pom.xml

Dependency tree chỉ là **resolved dependency graph theo build tool**. Application server, fat JAR, plugins, classloaders hoặc container layers có thể khiến runtime lấy `lib.Library` từ nơi khác.

### HotSpot class loading logs

Với lab A:

~~~bash
java -Xlog:class+load=info:file=nsm-load.log -cp nsm/out/client:nsm/out/v2 demo.Client
~~~

Tìm đúng class:

~~~bash
grep -E "demo.Client source:|lib.Library source:" nsm-load.log
~~~

Dạng log minh họa (không phải exact paths/timestamps):

~~~text
[info][class,load] demo.Client source: file:/.../nsm/out/client/
[info][class,load] lib.Library source: file:/.../nsm/out/v2/
~~~

**Tìm đúng library owner**, không chỉ `java.lang.Object`. Nếu CDS/AppCDS được bật, một số classes có thể log `source: shared objects file`. Cần hiểu classload log của đúng JVM build.

PowerShell một dòng:

~~~powershell
java -Xlog:class+load=info:file=nsm-load.log -cp "nsm/out/client;nsm/out/v2" demo.Client
Select-String -Path .\nsm-load.log -Pattern "demo.Client source:|lib.Library source:"
~~~

**Không suy** theo tên `lib-v2.jar` trong YAML nếu class runtime log lại tới `lib-v1.jar`. Classpath order hoặc parent-first delegation có thể chọn definition khác.

### Nếu có hai versions trên classpath

~~~bash
java -cp nsm/out/client:nsm/out/v1:nsm/out/v2 demo.Client
java -cp nsm/out/client:nsm/out/v2:nsm/out/v1 demo.Client
~~~

Với simple application classloader của lab:

- Đặt v1 trước thường nạp v1, chạy thành công.
- Đặt v2 trước thường nạp v2, gây `NoSuchMethodError`.

**Không áp máy móc cho mọi classloader**: application servers, OSGi, custom child-first loaders có policies riêng. Kiểm nguồn thực qua log.

## 15. Java class identity và module boundaries

Theo JVMS `5.3`, class identity gắn **binary name + defining class loader**. Hai loaders khác nhau có thể định nghĩa classes trùng tên nhưng khác runtime identity. Hệ plugin như vậy có thể xuất hiện `ClassCastException`, `LinkageError` hoặc loader constraints errors mà dependency tree không thể giải thích hết.

Với JPMS:

- `module-info.java` của library quyết định package exports/opens.
- Nếu package từng được export cho caller rồi bị bỏ export, old binaries có thể gặp `IllegalAccessError` khi linking.
- Một `--add-opens` cho reflection không giống `exports` của normal accessibility.
- Ngay cả khi bytecode member vẫn là `public`, class/module/package có thể không accessible cho caller.

**Điều tra chính xác caller module, owner module, package exports, classloaders và access flags.**

## 16. Maven/Gradle debugging (khi dự án thực sử dụng)

Với Maven project:

~~~bash
mvn dependency:tree
mvn dependency:tree -Dverbose
~~~

Với Gradle project:

~~~bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency target-library --configuration runtimeClasspath
~~~

`target-library` là placeholder cần thay; trên Windows dùng `gradlew.bat`. Configurations có thể khác theo plugins. Những commands này **không nằm trong lab Java thuần**; chúng chỉ áp dụng khi có `pom.xml`/Gradle build.

Hãy kiểm:

- Phiên bản direct và transitive dependencies.
- Maven scopes `provided`/`runtime`; Gradle runtime configuration.
- BOM/dependency management/conflict resolution.
- Duplicated class names trong fat JAR, application server libs và container image.
- Java agents và custom launchers.
- Artifact thực tế deploy, không chỉ source dependencies trên IDE.

### Tạo và inspect JAR của lab

~~~bash
jar --create --file nsm/lib-v1.jar -C nsm/out/v1 .
jar --create --file nsm/lib-v2.jar -C nsm/out/v2 .

jar tf nsm/lib-v1.jar
jar tf nsm/lib-v2.jar

javap -classpath nsm/lib-v1.jar -p -s lib.Library
javap -classpath nsm/lib-v2.jar -p -s lib.Library
~~~

Cả hai JAR đều có `lib/Library.class`; **cùng tên không có nghĩa cùng API**. `jdeps` hữu ích cho package/module dependency graph nhưng **không thay được javap descriptor check** khi cần xác định `NoSuchMethodError`.

## 17. Checklist chẩn đoán lỗi theo đúng thứ tự

1. **Ghi nguyên exception/error class** và stack trace, không chỉ copy một dòng.
2. **Xác định caller** đang invoke, method/field owner được tham chiếu.
3. **Lấy bytecode của caller binary thực tế**: `javap -c -v`, xem name/descriptor/opcode.
4. **Bật class-load log** (trong test/canary) để biết runtime owner class đến từ JAR/directory nào.
5. **Inspect runtime owner API** bằng `javap -p -s -v`, đối chiếu descriptors/flags.
6. **Đọc release notes**: member bị remove/rename/visibility change hay compiler bridge thay đổi?
7. **Kiểm runtime classpath/module graph** và build dependency convergence.
8. **Tái hiện với client cũ + v1/v2** để xác nhận nguyên nhân, tránh thay nhiều biến cùng lúc.
9. **Sửa version/API contract** hoặc rebuild/redeploy **toàn bộ consumers cần thiết**.
10. **Thêm binary API compatibility test** vào CI, test code paths có thể lazy resolve.

Một report nhỏ đủ hữu ích:

~~~text
ERROR = java.lang.NoSuchMethodError
CALLER = demo.Client
EXPECTED = lib.Library.message:()Ljava/lang/String;
RUNTIME_OWNER = nsm/out/v2/lib/Library.class
AVAILABLE = lib.Library.message:(I)Ljava/lang/String;
ROOT_CAUSE = old client binary + incompatible library v2
ACTION = select compatible library or adapt/recompile clients
~~~

Dữ liệu ví dụ là minh họa từ lab, không phải kết quả thu từ ứng dụng thật.

## 18. Sửa library mà không phá client cũ

Nếu muốn hỗ trợ **backward binary compatibility**, library v2 có thể giữ old method rồi thêm overload:

File **`Library.java`** thuộc package `lib`:

~~~java
package lib;

public final class Library {
    private Library() { }

    // Giữ API client cũ đã được compile.
    public static String message() {
        return "library-v2-compatible";
    }

    // Thêm API mới.
    public static String message(int code) {
        return "library-v2-" + code;
    }
}
~~~

Nếu compile library mới và dùng `Client.class` cũ của demo A, **stdout kỳ vọng**:

~~~text
before-call
message=library-v2-compatible
after-call
~~~

Với access modifier, có thể giữ public entry point và đưa implementation vào helper private:

File **`Gate.java`** thuộc package `lib`:

~~~java
package lib;

public final class Gate {
    private Gate() { }

    @Deprecated
    public static String open() {
        return implementation();
    }

    private static String implementation() {
        return "gate-v2-compatible";
    }
}
~~~

Client `AccessClient.class` cũ lại link được:

~~~text
before-call
value=gate-v2-compatible
after-call
~~~

**Caveat:** Giữ hai methods tương thích không chứng minh **toàn library** tương thích. Còn constructors, fields, interface hierarchies, type erasure, bridge methods, module exports và behavior cần xem xét.

### Khi bắt buộc breaking change

Nếu không giữ API cũ được, hãy:

- Tăng major version theo policy của thư viện.
- Cập nhật consumers và artifacts **đồng bộ**.
- Đảm bảo không còn old client classes trong deployment.
- Kiểm tra dependency mediation và classpath duplicates.
- Test full lifecycle/call paths, không chỉ compilation.
- Nếu hỗ trợ rolling deploy, thiết kế giai đoạn **expand-and-contract** hoặc adapter layer để old/new versions cùng hoạt động trong thời gian chuyển đổi.

## 19. Không chữa LinkageError bằng catch(Throwable)

`NoSuchMethodError` và `IllegalAccessError` là **Error**, không phải business exceptions. Bắt và bỏ qua có thể:

- Che lấp dependency incompatibility thật.
- Để những code paths khác tiếp tục lỗi.
- Khiến hiện tượng chỉ xảy ra theo traffic/feature flag.
- Phá observability của ứng dụng.

Chỉ catch ở boundary được thiết kế rõ để **isolate một plugin không tương thích và fallback an toàn**. Thông thường giải pháp là sửa runtime artifact và API compatibility.

**Không ép `--add-opens` hoặc `--illegal-access` để “chữa nhanh” một private Java method**. Hãy tìm nguyên nhân ở đúng member access rules.

## 20. CI: kiểm thử nhị phân xuyên phiên bản

Ma trận:

| Client binary | Runtime library | Ý nghĩa |
| --- | --- | --- |
| v1 | v1 | Baseline |
| **v1** | **v2** | **Backward binary compatibility** |
| v2 | v2 | Current release |
| v2 | v1 | Forward compatibility nếu có cam kết |
| Plugin cũ | Host mới | Kiểm tra classloader/API boundaries |
| Host cũ | Plugin mới | Khi hỗ trợ nâng cấp từng phần |

Có thể dùng **japicmp** hoặc **Revapi** để so API giữa JARs trong build pipeline. Chúng hỗ trợ phát hiện nhiều changes nhưng không thay thế integration tests về classloaders, JPMS và behavior.

Một CI gate tốt nên:

- So API surface public/protected/constructors.
- Ghi method descriptors/access changes.
- Kiểm tra duplicate class entries trong build artifacts.
- Chạy precompiled client binaries với new library.
- Test resolved runtime dependency graph và packaged JAR.
- Chạy application canary với đúng module/loader environment.

**Không giả định backward compatibility đồng nghĩa forward compatibility**; chúng là hai chiều riêng.

## 21. Các hiểu nhầm điển hình

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Compile pass ⇒ runtime link pass.” | Runtime class/JAR có thể khác compile classpath. |
| “NoSuchMethodError nghĩa toàn bộ class không tìm thấy.” | Class có thể tồn tại, chỉ method descriptor thiếu. |
| “Chỉ tên method là đủ.” | JVM cần name **và descriptor**. |
| “Đổi return type không ảnh hưởng.” | Return type nằm trong JVM method descriptor. |
| “Thêm overload sẽ cứu client cũ.” | Cần giữ old descriptor nếu cần backward compatibility. |
| “Java runtime chọn overload lại.” | Compiler đã chọn symbolic reference. |
| “IllegalAccessError giống IllegalAccessException.” | Linkage vs reflection exception. |
| “Giữ tên method nhưng đổi public→private vẫn an toàn.” | Access checks có thể gây IllegalAccessError. |
| “Static↔instance sẽ báo NoSuchMethodError.” | Có thể là IncompatibleClassChangeError. |
| “Maven dependency:tree là đủ để biết JAR thực chạy.” | Phải inspect runtime loader/source. |
| “Hai classes cùng tên chắc cùng runtime class.” | Defining class loader cũng tham gia identity. |
| “Một main method chạy xong là đã test mọi linkage.” | Resolution có thể xảy ra ở call path chưa chạm. |
| “Public static final constant đổi thì old client tự thấy.” | Compile-time constant có thể đã inline. |
| “Một clean build luôn sửa lỗi.” | Nếu dependencies thực sự không tương thích thì cần sửa API/version. |
| “Có thể fix private member bằng --add-opens.” | Reflection opens khác normal Java access. |
| “Error messages phải y hệt giữa JDK 17 và 25.” | JVM có thể format khác dù error class giống. |
| “Thêm abstract interface method luôn làm mọi old client crash.” | Cần phân biệt old client vs new invocation trên old implementation. |
| “Catch LinkageError là xong.” | Không sửa binary/API mismatch. |

## 22. Câu hỏi ôn tập và đáp án

**1.** Binary compatibility nghĩa gì?  
Theo JLS §13.2, pre-existing binaries vẫn link được sau thay đổi type/library.

**2.** Tại sao `NoSuchMethodError` thường chỉ xảy ra lúc chạy?  
Client đã compile với API cũ, nhưng method reference được runtime resolve với library khác.

**3.** Descriptor `(I)Ljava/lang/String;` biểu thị gì?  
Nhận một int và trả về String.

**4.** Demo A giữ nguyên binary nào khi đổi library?  
`nsm/out/client/demo/Client.class`.

**5.** Tại sao `Library.message(int)` không thay thế được `Library.message()`?  
Tên giống nhưng parameter descriptor khác.

**6.** Điều gì gây `IllegalAccessError` trong demo B?  
`Gate.open()` còn method nhưng đổi public thành private.

**7.** Lệnh nào xem descriptors và method flags?  
`javap -p -s -v -classpath <runtime-library-path> lib.Gate` (thay placeholder bằng path thật).

**8.** Dùng gì xác nhận JAR được load thực?  
HotSpot `-Xlog:class+load=info` và class source logs.

**9.** Đổi static method thành instance có thể gây gì?  
`IncompatibleClassChangeError` do invocation opcode mismatch.

**10.** NoSuchMethodException thuộc cơ chế nào?  
Reflection API lookup, khác `NoSuchMethodError` bytecode linking.

**11.** Một `public static final String LABEL` thay value nhưng old client vẫn in v1 là vì sao?  
Compiler có thể inline compile-time constant.

**12.** Vì sao dependency tree chưa đủ?  
Runtime có thể dùng fat JAR/server classloader/plugin JAR khác.

**13.** Sửa tối thiểu cho library v2 để giữ client A chạy được?  
Giữ public static `message():String` và thêm overload mới thay vì xóa old API.

**14.** Kiểm thử quan trọng khi publish library v2?  
Chạy precompiled client v1 binaries với library v2.

**15.** Vì sao một feature flag ít dùng có thể che mismatch?  
Lazy resolution có thể chỉ xảy ra khi code path của feature chạy.

## 23. Tài liệu chính thức

- [JLS SE 25 — Chapter 13: Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html): `13.2` khái niệm; `13.3` modules; `13.4.7` access, `13.4.12–13.4.15` methods/signatures, `13.4.16` abstract, `13.4.19` static, `13.5` interfaces.
- [JVMS SE 25 — Chapter 5: Loading, Linking, Initializing](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html): `5.3` loading; `5.4.3` resolution; `5.4.4` access control, loading constraints.
- [JVMS SE 25 — Chapter 4: Class File Format](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html): method descriptors, run-time constant pool và `ACC_*` flags.
- [Java SE 25 — NoSuchMethodError](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/NoSuchMethodError.html): runtime class incompatibly changed.
- [Java SE 25 — IllegalAccessError](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/IllegalAccessError.html): method/field không accessible.
- [Java SE 25 — IncompatibleClassChangeError](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/IncompatibleClassChangeError.html): binary class changes.
- [Java SE 25 — AbstractMethodError](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/AbstractMethodError.html).
- [Java SE 25 — NoSuchFieldError](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/NoSuchFieldError.html).
- [Java SE 25 — NoClassDefFoundError](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/NoClassDefFoundError.html).
- [Oracle JDK 25 — javap](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html): xem descriptors/bytecode/flags.
- [Oracle JDK 25 — java Unified Logging](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `-Xlog:class+load` class-loading diagnostics.
- [Oracle JDK 25 — jdeps](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeps.html): inspect dependencies, không thay exact descriptor validation.

**Kết luận:** `NoSuchMethodError` và `IllegalAccessError` thường báo rằng **client bytecode không còn tương thích với class/API thực sự được JVM nạp**. Với `NoSuchMethodError` hãy so **method name + descriptor**; với `IllegalAccessError` hãy so **accessibility, package/module và loader context**. Đọc `javap` trên **binary caller và runtime owner**, xác minh nguồn class bằng `-Xlog:class+load`, xử lý version conflicts và giữ backward API compatibility khi đã cam kết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-011">Phân biệt ngoại lệ Java với JVM crash; nhận diện báo cáo hs_err_pid</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ngoại lệ Java với JVM crash; nhận diện báo cáo hs_err_pid

## Mục tiêu bài học

Trong vận hành ứng dụng Java, câu “JVM bị lỗi” có thể chỉ các tình huống hoàn toàn khác nhau: một method ném `NullPointerException` và request thất bại; một thread gặp uncaught exception; main kết thúc với exit code khác 0; Java heap cạn và ném `OutOfMemoryError`; kernel giết container vì vượt memory limit; hoặc HotSpot phát hiện **fatal error** như một tín hiệu truy cập bộ nhớ không hợp lệ trong native/JIT/VM code rồi tạo file `hs_err_pid12345.log`.

Những tình huống ấy có **cơ chế, bằng chứng, mức độ ảnh hưởng và cách khắc phục khác nhau**. Nếu mọi sự cố đều bị gắn nhãn “JVM crash” hoặc mọi file hs_err đều được kết luận “bug JVM”, đội vận hành dễ xử lý sai: tăng heap khi native library phá memory, sửa NPE khi thực chất container bị SIGKILL, hoặc bỏ qua một lỗi JIT có khả năng tái diễn.

Sau bài, bạn có thể:

1. Phân biệt **Java exception**, **Java Error**, **uncaught exception**, **application-level exit**, **JVM fatal error/crash** và **external process kill**.
2. Nhận diện thông điệp HotSpot fatal-error, các mẫu `SIGSEGV`, `SIGBUS`, `SIGILL`, `SIGFPE`, `SIGABRT` và Windows `EXCEPTION_ACCESS_VIOLATION` theo ngữ cảnh.
3. Hiểu mục đích, vị trí, giới hạn và mức độ nhạy cảm của báo cáo `hs_err_pid<PID>.log`; cấu hình `-XX:ErrorFile=...`.
4. Phân tích **header**, `Problematic frame`, `Current thread`, native/Java stacks, registers, heap, command-line, loaded libraries và system section.
5. Đọc ký hiệu frames `C`, `V`/`v`, `J`/`j` và hiểu rằng một frame gây ra fault **chưa đủ để kết luận root cause**.
6. Phân biệt **heap OOME có Java stacktrace** với **native allocation fatal error** và **cgroup OOMKilled** vốn có thể không tạo hs_err.
7. Tạo/chạy ví dụ Java 17+ về handled và uncaught exceptions **mà không cố tình làm crash máy**.
8. Phân tích một header mô phỏng và tách trường thông tin có giá trị bằng chương trình Java 17+ runnable.
9. Xây dựng quy trình incident triage, thu bằng chứng, tái hiện có kiểm soát, xác định JNI/JIT/GC/native library và báo bug cho đúng bên.
10. Bảo vệ dữ liệu nhạy cảm trong fatal-error log, core dump và command-line arguments.

## 1. Bản đồ phân loại sự cố

~~~text
                     Java process gặp sự cố
                               |
        +----------------------+----------------------+
        |                      |                      |
   Java Throwable        HotSpot fatal error     External OS / runtime
        |                      |                      |
  Exception hoặc Error   Signal/assertion/...    SIGKILL, OOMKilled,
        |                      |                 node eviction, timeout
  catch/uncaught          hs_err_pid*.log         Có thể KHÔNG hs_err
        |                      |
 request fail / thread     whole VM process
 exit / app policy         usually terminates
        |                      |
 Java stack trace          native+Java stack,
 may be available          problematic frame
                               |
                        Core dump (optional)
~~~

**Phân loại theo quan sát thực tế, không theo tên exception duy nhất.** Một `OutOfMemoryError` có thể được Java runtime ném như một `Error`; trong điều kiện native memory exhaustion nghiêm trọng, HotSpot có thể gặp fatal error khác và đi vào crash handler. Khi kernel phát SIGKILL, JVM có thể hoàn toàn không chạy crash handler.

### Năm trường hợp cần tách

| Hiện tượng | Phản ứng điển hình | Dấu vết chính | `hs_err_pid`? |
| --- | --- | --- | --- |
| Exception được catch | Thread tiếp tục theo catch/finally | Application logs/trace | Thường **không** |
| Uncaught Exception/Error của một Java thread | UncaughtExceptionHandler xử lý; **thread** kết thúc | Java throwable/stack trace | Thường **không** |
| Main/app tự exit | JVM tiến tới shutdown hoặc halt tùy API | Exit code, application logs | **Không mặc định** |
| HotSpot fatal crash | Process bị kết thúc sau fatal-error handling | Fatal header, problematic frame, core | **Thường có**, nếu có thể ghi |
| OS/container kill | Process bị hệ điều hành dừng cưỡng bức | Pod status, OOM events, signal/exit | **Không bảo đảm**, thường không |

**Chú ý:** một uncaught exception trong *một worker thread* không mặc nhiên dừng toàn bộ JVM; các non-daemon threads khác vẫn có thể sống. Ngược lại, exception uncaught trong main có thể dẫn tới JVM exit nếu không còn non-daemon threads; đó vẫn **không phải HotSpot fatal crash**.

## 2. Java Throwable: lỗi ở tầng ngôn ngữ

Java phân cấp:

~~~text
java.lang.Throwable
   |
   +-- java.lang.Exception
   |      |
   |      +-- IOException
   |      +-- SQLException
   |      +-- RuntimeException
   |             +-- NullPointerException
   |             +-- IllegalArgumentException
   |             +-- ArithmeticException
   |
   +-- java.lang.Error
          |
          +-- OutOfMemoryError
          +-- StackOverflowError
          +-- LinkageError
                 +-- NoClassDefFoundError
                 +-- UnsatisfiedLinkError
~~~

Một `Throwable` là **Java object**. Khi ném, Java runtime tìm handler tương ứng theo call stack; nếu không có, thread chấm dứt và uncaught exception handler được gọi theo contract. Một `NullPointerException` có thể gây HTTP 500 nhưng không phải native memory access violation; **HotSpot có thể ném NPE một cách có kiểm soát**, kể cả khi tận dụng OS faults để thực hiện implicit null checks nội bộ.

### Checked vs unchecked không phải crash vs non-crash

- Checked Exception được Java compiler buộc xử lý/khai báo theo quy tắc.
- Unchecked exceptions như RuntimeException không cần khai báo throws.
- `Error` thường biểu thị tình trạng nghiêm trọng và thường **không nên catch tràn lan**.
- Nhưng cả hai loại `Exception`/`Error` đều có thể tồn tại dưới dạng Java Throwable, không tự tạo `hs_err_pid`.

Nếu chương trình tự gọi `System.exit(1)` sau khi catch lỗi, hệ thống thấy exit code 1. **Exit code khác 0 chưa đủ chứng minh HotSpot crashed.**

## 3. Java 17+ demo 1 — handled exception và uncaught worker

Tạo **`JavaFailureKindsDemo.java`**:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class JavaFailureKindsDemo {
    public static void main(String[] args)
            throws InterruptedException {
        try {
            int result = 10 / 0;
            System.out.println(result);
        } catch (ArithmeticException ex) {
            System.out.println("caught="
                    + ex.getClass().getSimpleName());
        } finally {
            System.out.println("finally=true");
        }

        AtomicReference<String> workerFailure =
                new AtomicReference<>("none");

        Thread worker = new Thread(() -> {
            throw new IllegalStateException("worker failed");
        }, "example-worker");

        worker.setUncaughtExceptionHandler((thread, cause) ->
                workerFailure.set(
                        cause.getClass().getSimpleName()));

        worker.start();
        worker.join();

        System.out.println("workerFailure="
                + workerFailure.get());
        System.out.println("mainContinues=true");
    }
}
~~~

Chạy trên JDK 17+:

~~~bash
javac --release 17 JavaFailureKindsDemo.java
java JavaFailureKindsDemo
~~~

**Stdout chính xác:**

~~~text
caught=ArithmeticException
finally=true
workerFailure=IllegalStateException
mainContinues=true
~~~

Giải thích:
- `10 / 0` ném `ArithmeticException`; catch và finally chạy đúng.
- Worker ném uncaught `IllegalStateException`; handler gán tên class vào `AtomicReference` thay vì để default handler in stack trace không ổn định.
- `join()` đợi worker kết thúc, tạo happens-before trước khi main đọc kết quả.
- Main vẫn hoàn thành và có thể exit status 0 trong chạy bình thường.
- **Không có lý do từ chương trình này để HotSpot tạo `hs_err_pid`**.

Đừng coi việc “thread chết” là “JVM crash”. Thread lifecycle và process lifecycle không giống nhau. Một process server có thread pool/other non-daemon threads vẫn có thể tiếp tục chạy sau khi một worker chết; tuy nhiên nghiệp vụ có thể hỏng và cần giám sát.

## 4. Uncaught exception ở main: output thường gặp, không phải fatal error

Tạo **`UncaughtMainDemo.java`**:

~~~java
public class UncaughtMainDemo {
    public static void main(String[] args) {
        throw new IllegalArgumentException("invalid input");
    }
}
~~~

Chạy:

~~~bash
javac --release 17 UncaughtMainDemo.java
java UncaughtMainDemo
~~~

Một stderr điển hình (số dòng/stack có thể khác do source layout, JDK):

~~~text
Exception in thread "main" java.lang.IllegalArgumentException: invalid input
    at UncaughtMainDemo.main(UncaughtMainDemo.java:3)
~~~

Đây là **dạng minh họa**, không phải stdout tuyệt đối được chuẩn hóa cho mọi launcher/uncaught exception handler. Trên Java launcher bình thường process thường trả exit status khác 0 khi main kết thúc uncaught và không còn non-daemon threads, nhưng **không có fatal-error header "Problematic frame" mặc định**. Không tìm hs_err như bằng chứng đầu tiên; điều tra exception type, stack trace, input và application logging.

### Stack trace Java gồm gì?

Một dòng `at com.example.Service.process(Service.java:42)` mô tả Java-level call site theo stack trace, có thể bị ảnh hưởng bởi inlining, missing debug line tables, generated/proxy code hoặc stacktrace filtering. Nó **khác** CPU instruction pointer `pc` và native library offset trong fatal-error log.

## 5. JVM fatal error: HotSpot không thể tiếp tục an toàn

HotSpot có error-reporting mechanism cho những lỗi đủ nghiêm trọng, chẳng hạn:
- Unhandled segmentation fault hoặc bus error trong native/VM/compiled code.
- Một số internal fatal assertions/guarantees và VM consistency failures.
- Lỗi VM/native allocation nghiêm trọng đến mức không thể tiếp tục.
- SIGILL/illegal instruction hoặc một số signal/platform exceptions không thể khôi phục.
- Lỗi trong JNI/JVMTI agents hoặc native libraries gây memory corruption.

Tùy bản JDK và nguyên nhân, HotSpot in header đại loại:

~~~text
#
# A fatal error has been detected by the Java Runtime Environment:
#
#  SIGSEGV (0xb) at pc=0x00007f0000000100, pid=4242, tid=4243
#
# JRE version: OpenJDK Runtime Environment (...)
# Java VM: OpenJDK 64-Bit Server VM (...)
# Problematic frame:
# C  [libexample.so+0x2a]
#
# Core dump will be written. Default location: ...
# An error report file with more information is saved as:
# /var/log/java/hs_err_pid4242.log
~~~

**Đây là ví dụ tổng hợp**, các tên library/PC/PID/OS và văn bản không phải log thực. Khi fatal error đã được JVM nhận diện, HotSpot cố ghi **fatal-error log** và có thể core dump tùy OS/configuration. **Không bảo đảm ghi được file hoàn chỉnh**, vì trạng thái VM có thể đã corrupt, disk đầy, không đủ quyền hoặc crash tiếp khi error handler làm việc.

### SIGSEGV không phải lúc nào cũng là JVM crash

HotSpot còn có thể dùng một số OS signals làm cơ chế nội bộ cho null checks/safepoint polling và xử lý chúng bình thường. Khi thấy một signal ở low-level debugger, **không vội kết luận fatal crash**; kiểm tra JVM đã thực sự in fatal header, process terminated và error log hay không.

## 6. Vì sao tên `hs_err_pid`?

Trên HotSpot, default filename thường là:

~~~text
hs_err_pid<PID>.log
~~~

Ví dụ PID của process là 4242:

~~~text
hs_err_pid4242.log
~~~

Báo cáo này còn được gọi **fatal error log** hoặc **HotSpot error report**. Nó là **text file** chứa state tại thời điểm crash nếu error handler thu thập được.

### Vị trí mặc định

Theo Oracle Java 21 Troubleshooting Guide:
1. JVM thử ghi vào **current working directory** của Java process.
2. Nếu không ghi được do quyền/dung lượng/đường dẫn, HotSpot thử **OS temporary directory** (Linux thường là `/tmp`; Windows dựa trên `TMP`/`TEMP`).
3. Nếu môi trường container có writable filesystem khác hoặc process chạy dưới service user, vị trí bạn kỳ vọng từ laptop shell có thể khác current working directory thật.

**Không tìm thấy file ở folder chứa JAR** không đủ kết luận không tồn tại; `user.dir`/working directory của service có thể khác JAR path.

### Điều kiện không có hs_err

- Process bị `SIGKILL` hoặc kernel/container OOM kill — không cho JVM chạy user-level fatal error handler.
- Process bị kill/exit theo chính sách ứng dụng như `System.exit` hoặc graceful `SIGTERM`.
- Disk full, permission denied, read-only filesystem, log directory không tồn tại.
- Native corruption quá nặng, fault xảy ra ngay trong error-reporting code hoặc crash ở giai đoạn rất sớm.
- JVM implementation không phải HotSpot hoặc vendor có crash-report format khác.

**Vì vậy “không có hs_err” không phải một chẩn đoán.** Tìm thêm exit code, container status, journald/event logs, OS crash reports và application logs.

## 7. Chỉ định vị trí log bằng -XX:ErrorFile

Ví dụ trên Linux/HotSpot:

~~~bash
java -XX:ErrorFile=/var/log/myapp/hs_err_pid%p.log -jar app.jar
~~~

Trong HotSpot, `%p` trong file pattern được thay bằng process ID; `%%` biểu thị ký tự percent literal trong context này. Đường dẫn là ví dụ; phải đảm bảo folder **đã tồn tại, writable, còn dung lượng, mount persistence phù hợp và permissions an toàn**.

Trên Windows PowerShell, một ví dụ được quote cẩn thận:

~~~powershell
java "-XX:ErrorFile=C:\java-logs\hs_err_pid%p.log" -jar app.jar
~~~

Dùng Windows path thực và kiểm tra directory writable. Dấu phần trăm được diễn giải khác nhau tùy Windows shell/batch script; xem chính command line sau expansion nếu wrapper can thiệp.

### Nếu chạy trong Kubernetes

Một container có writable layer bị xóa khi pod được recreate có thể làm log biến mất khỏi vị trí cũ. Có thể:
- Dùng volume phù hợp cho fatal-error logs, với securityContext/ownership/space limits rõ ràng.
- Có log-collection sidecar/agent được cấu hình thu file.
- Ghi crash metadata/event và attach file artifact vào incident theo policy.
- Tránh gửi nguyên core dumps qua stdout hoặc kho chia sẻ rộng.

**Không được tùy tiện đổi cấu hình logging của production** nếu chưa đánh giá dữ liệu nhạy cảm và chi phí storage. Chỉ thị `ErrorFile` là công cụ bảo đảm vị trí *nếu fatal error handler chạy được*, không bảo đảm file xuất hiện.

## 8. Header của hs_err: đọc từ trên xuống

Một báo cáo có thể chia thành:

~~~text
HEADER
  fatal error / signal / PC / pid / tid
  JVM version/build, problematic frame
  core dump and error-file notes

THREAD SECTION
  Current thread
  siginfo/exception info
  registers and stack boundaries
  Native frames / Java frames

PROCESS SECTION
  other JVM threads / VM state
  heap and collector information
  command line and loaded libraries

SYSTEM SECTION
  OS, CPU, memory, mappings, environment
~~~

Format chi tiết có thể thay đổi theo **JDK release và update**. Một số bản có thêm `Dynamic libraries`, memory maps, compressed oops mode, CDS info, container detection, internal exceptions, VM operations, native allocation metadata hoặc JIT compilation events.

### Ý nghĩa các trường header

| Trường | Cách đọc |
| --- | --- |
| `SIGSEGV` / `SIGBUS` / `EXCEPTION_ACCESS_VIOLATION` | Loại signal/exception hệ điều hành; không tự nói root cause |
| `pc=0x...` | Program counter/instruction address nơi fault được báo |
| `pid=...` | Process ID vào thời điểm crash |
| `tid=...` | Native thread ID/OS thread context, không nhất thiết bằng Java Thread.getId |
| `JRE version` | Runtime distribution/version |
| `Java VM` | VM type/build, có thể có GC/mode flags |
| `Problematic frame` | Fault-site frame mà VM báo cáo; rất hữu ích để định hướng |
| `Core dump...` | Hints về OS core dump, **không bảo đảm core thực sự được ghi** |
| `Error report file...` | Nơi JVM đã cố/sẽ lưu hs_err report |

Giữ lại **đúng JDK vendor/build/update** vì crash có thể đã được sửa ở một update release nhưng vẫn xảy ra trên runtime cũ.

## 9. Nhận diện Problematic frame: C, V, v, J, j

Trong stack/frame notation của HotSpot (theo Oracle guide), bạn có thể gặp:

| Ký hiệu | Loại frame | Hướng điều tra |
| --- | --- | --- |
| `C` | Native C/C++ code bên ngoài VM hoặc native library frame | JNI/JVMTI agent, library, libc, graphics/SSL/compression... |
| `V` | VM C/C++ code của HotSpot | VM subsystem, GC/JIT/synchronization, possibly underlying corruption |
| `v` | VM-generated stub code | VM runtime stubs/adapters |
| `J` | Compiled Java code (JIT) | JIT/native machine code, possible compiler bug hoặc dữ liệu corrupted |
| `j` | Interpreted Java code | Java interpreter frames |
| `A` trên một số builds | AOT-compiled Java code | Version/build-specific AOT context |

Đây là **conventions của HotSpot diagnostics**, không phải một Java Language Specification guarantee. JIT inlining, deoptimization, symbols stripped và stack corruption có thể khiến stacks không đầy đủ.

### Ví dụ: native library

~~~text
# Problematic frame:
# C  [libthirdparty.so+0x1af3]
~~~

Điều này cho thấy fault PC nằm trong `libthirdparty.so` hoặc cách unwind quy về library đó, **không chứng minh 100% chính thư viện này là tác nhân đầu tiên**. Nó có thể đang dùng một pointer bị ghi hỏng trước đó từ code/agent khác. Dù vậy đây là nơi điều tra **rất ưu tiên**: library version, native symbols, JNI safety, ABI, reproduce.

### Ví dụ: VM code

~~~text
# Problematic frame:
# V  [libjvm.so+0x58fab]
~~~

Fault nằm trong HotSpot VM code, nhưng nguyên nhân có thể do JNI/native corruption từ trước, bug HotSpot, unsupported JVM flags, hardware, agent hoặc memory fault. Không kết luận “JDK bug” chỉ từ `libjvm.so` ở top frame.

### Ví dụ: compiled code

~~~text
# Problematic frame:
# J  org.example.Parser.parse([B)I
~~~

Có thể gợi ý JIT compiled code hoặc invalid state; cần thêm compilation events, VM flags, crash reproducibility, JDK build, core dump. **Không kết luận Java source sai chỉ vì method của app xuất hiện**. Java code đã đi qua verifier/JMM semantics thường không được phép gây arbitrary native segfault nếu không có VM/native corruption.

## 10. Current thread: bối cảnh thread đang fault

Ví dụ mô phỏng:

~~~text
Current thread (0x00007f01abc00000): JavaThread "worker-12"
    [_thread_in_native, id=1208, stack(0x...,0x...)]
~~~

Các gợi ý:
- `JavaThread "worker-12"`: Java-thread representation do VM mô tả.
- `_thread_in_native`: thread đang thực thi native code tại thời điểm được VM chụp.
- `id=1208`: native thread ID.
- `stack(...)`: vùng địa chỉ stack, không phải Java Throwable stack trace.

Một số cases:
- **Thread named C2 CompilerThread**: lỗi trong JIT compiler thread đáng được điều tra về JIT, VM build, compiler replay/compilation inputs.
- **GC worker hoặc VM Thread**: GC/VM operations có thể liên quan; vẫn phải xét native memory corruption hoặc hardware.
- **JavaThread in native**: xem JNI/FFM/agent/native code ở stack đầu tiên.
- **JavaThread in Java/compiled**: xem compiled method, code cache/JIT, VM state và fault PC.

**Thread name có thể do application đặt**, không được coi là xác thực chức năng chỉ từ tên. Thread types/diagnostics thay đổi theo JDK.

### Đừng nhầm Java virtual thread với OS thread

Trên JDK 21+, Java virtual threads được lập lịch trên carrier platform threads. Fatal fault xảy ra ở **native OS execution context**; các `pid/tid`/native frames không nhất thiết phản ánh một virtual thread identity kiểu bạn kỳ vọng. Khi phân tích, kết hợp Java-level contexts, carrier information khi có, JFR/tracing và đúng JDK. Không suy từ `tid` rằng đó là `Thread.getId()`.

## 11. Native frames và Java frames: thông tin khác nhau

Một excerpt:

~~~text
Native frames: (J=compiled Java code, j=interpreted,
               Vv=VM code, C=native code)
C  [libthirdparty.so+0x1af3]
j  org.example.NativeAdapter.read()I+0
j  org.example.Service.run()V+9
V  [libjvm.so+0x839eea]

Java frames: (J=compiled Java code, j=interpreted, Vv=VM code)
j  org.example.NativeAdapter.read()I+0
j  org.example.Service.run()V+9
~~~

**Native frames** thường có instruction/library offsets, kể cả native functions. **Java frames** cho biết Java methods ở execution stack, có thể bổ sung inlined frames mà native unwind không thể hiện trực tiếp.

Một stack trace giúp trả lời:
1. Crash xảy ra khi xử lý request/task nào?
2. Lệnh Java nào gọi sang JNI/native boundary?
3. Library nào đang chạy?
4. Có agent/instrumentation/profiler/native FFM call trong path không?
5. Có dấu hiệu stack corruption hoặc recursion quá sâu?

**Không phải mọi crash đều có hai stack frames đầy đủ.** Khi stack corrupt hoặc process sắp chết, HotSpot error handler có thể bị lỗi tiếp. Thông báo kiểu `error occurred during error reporting` không có nghĩa nguyên nhân ban đầu chính là reporting code.

## 12. Những signal thường gặp và cách hiểu thận trọng

| Signal/error | Khả năng gợi ý | Không được tự suy |
| --- | --- | --- |
| Linux `SIGSEGV` | Fault memory access, illegal pointer/native memory corruption | Luôn là NullPointerException |
| Linux `SIGBUS` | Bus/memory-mapped file issue, alignment/storage/mapping | Luôn lỗi JVM implementation |
| Linux `SIGILL` | Illegal instruction, CPU feature/incompatible code/binary | Luôn phần cứng hỏng |
| Linux `SIGFPE` | Arithmetic/hardware exception ở native context | Luôn Java ArithmeticException |
| Linux `SIGABRT` | Abort/assertion/native/runtime termination | Luôn HotSpot JIT bug |
| Windows `EXCEPTION_ACCESS_VIOLATION` (0xc0000005) | Native invalid read/write/execute | Luôn nguyên nhân do DLL trong top frame |
| `SIGKILL` | Kernel/OS forcibly ends process; uncatchable | Luôn kernel cgroup OOM |
| `SIGTERM` | Termination request, JVM có thể chạy shutdown hooks | Luôn crash |

Phân biệt **signal bị handled** với **signal làm VM fatal**. Ví dụ HotSpot implicit null check có thể dùng cơ chế OS page protection/signals nội bộ nhưng ném NPE đúng Java semantics, không đồng nghĩa fatal `SIGSEGV`.

### SIGSEGV và Java NullPointerException

Java dereference của null có thể ném `NullPointerException` ở mức ngôn ngữ. JVM có quyền dùng tín hiệu từ CPU/OS làm phần implementation để phát hiện và chuyển thành Java exception. **Không thấy hs_err cho mỗi Java NPE** là hoàn toàn bình thường.

Ngược lại, một JNI library dùng invalid pointer có thể phát SIGSEGV **ngoài đường xử lý null check hợp lệ** và dẫn tới fatal error.

## 13. Java heap OutOfMemoryError khác native-memory fatal error

### Heap OOME ở mức Java

Một throwable thông dụng:

~~~text
java.lang.OutOfMemoryError: Java heap space
~~~

HotSpot không thể cấp phát một Java object theo heap conditions. Lúc đó:
- Exception là `java.lang.OutOfMemoryError`.
- Có thể có Java stack trace.
- Tùy lỗi và flags, JVM/process có thể tiếp tục, dừng hoặc bị chính sách giám sát restart.
- **Không mặc định sinh `hs_err_pid` chỉ vì có heap OOME.**

Các flags thường gặp:

~~~bash
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dumps -jar app.jar
~~~

Đây là **ví dụ**, cần directory writable/available space và phù hợp confidentiality policy. Heap dump có thể rất lớn và chứa dữ liệu người dùng, tokens, credentials.

### Native allocation failure

Nếu VM không reserve/commit native memory cần thiết, một số tình huống có thể sinh fatal-error log với câu kiểu:

~~~text
Native memory allocation (mmap) failed to map ... bytes
~~~

Câu chữ và action tùy error path/JDK/OS. Điều này không tự tương đương Java heap used chạm Xmx; cần xem:
- Container/cgroup memory limits.
- Native Memory Tracking (NMT) và process RSS.
- Thread stacks/direct buffers/code cache/metaspace.
- OS virtual memory settings, address space fragmentation, memory map limits.
- JNI/third-party native memory allocations.

**Đừng mặc định “tăng -Xmx” để chữa native-memory failure**; tăng heap có thể làm ít headroom hơn.

### CrashOnOutOfMemoryError

Một số HotSpot flags có thể yêu cầu fail-fast/crash khi OOME; khi đó Java OOME có thể bị policy biến thành VM fatal termination. Nếu điều tra môi trường có `-XX:+CrashOnOutOfMemoryError`, cần đọc actual VM flags trước khi kết luận từ loại exception. Không bật flag này chỉ để tạo thử hs_err trên máy dùng chung.

## 14. SIGKILL / OOMKilled: tại sao thường không có hs_err?

Trên Linux, `SIGKILL` không thể bị process bắt/ignore; JVM không có cơ hội thực hiện HotSpot error handler hoặc Java shutdown hooks khi bị SIGKILL. Ví dụ Kubernetes báo:

~~~text
lastState:
  terminated:
    reason: OOMKilled
    exitCode: 137
~~~

Đây là **đoạn YAML/log minh họa**, không phải mẫu khẳng định tất cả runtimes hiển thị giống nhau. Exit code 137 thường biểu thị termination do signal 9 (128+9). Nhưng **137 không chứng minh OOM**: process cũng có thể bị SIGKILL bởi operator/runtime vì lý do khác.

Để chẩn đoán:
- Kubernetes `kubectl describe pod ...` và Pod/container status/events.
- Linux cgroup v2 `memory.events` với `oom`/`oom_kill`.
- Cgroup `memory.current`/`memory.max`/`memory.peak` (nếu có).
- Process memory metrics, Java heap/GC/NMT/native telemetry.
- Node-level memory pressure, eviction events, cgroup ancestry.

**Không có hs_err với OOMKilled là điều có thể xảy ra**, không phải dấu hiệu “JVM không hỗ trợ crash reporting”.

## 15. System.exit, Runtime.halt và process termination

Java có những API chủ động kết thúc process:
- `System.exit(status)` khởi động shutdown sequence thích hợp, thường chạy registered shutdown hooks theo contract.
- `Runtime.getRuntime().halt(status)` kết thúc VM mạnh hơn và không chạy hooks theo shutdown protocol thông thường.
- Shell/OS SIGTERM có thể kích hoạt graceful shutdown trong JVM khi runtime/signal handlers hỗ trợ.
- OS SIGKILL kết thúc cưỡng bức, không cho JVM cleanup.

Một process exit **không luôn là crash**. Trong incident triage, hãy hỏi **exit reason**:
- uncaught main exception;
- explicit System.exit;
- SIGTERM từ redeploy/rolling restart;
- operator kill;
- cgroup OOM;
- native SIGSEGV/fatal VM assertion.

Cùng exit code có thể có nhiều nguyên nhân; không dùng exit code làm chứng cứ duy nhất.

## 16. Process section và system section của hs_err

Sau header/thread stack, HotSpot thường có information về:
- Danh sách Java/VM/GC/compiler threads.
- VM state và operations đang diễn ra.
- Heap configurations, GC name/generation/regions.
- Loaded native libraries và mappings.
- VM arguments, Java command, launcher properties.
- Environment variables nếu report có thể thu thập.
- OS version, CPU model/features, process memory, swap limits.
- Các crash-related events/compilation events gần thời điểm lỗi.

### Các câu hỏi quan trọng

| Dữ liệu | Câu hỏi phục vụ |
| --- | --- |
| JRE version/build | Runtime có update/bug-fix mới không? |
| Command line / VM arguments | Có agent, illegal flags, large heaps, Xcomp/Xint không? |
| Dynamic libraries | Native/JNI/agent nào được load? Version/path hợp lệ? |
| Current thread | Crash trong app worker, compiler, GC hay VM thread? |
| Heap / native memory | Có dấu resource exhaustion, memory pressure, GC activity không? |
| Loaded code / compiler events | Có JIT-compiled method liên quan? |
| OS/CPU | Có CPU instruction mismatch, kernel/storage/native library incompatibility? |
| Recent events | Có safepoint, deopt, compilation, exception trends không? |

**Không chỉ đọc 20 dòng đầu rồi bỏ qua phần còn lại.** Problematic frame là điểm xuất phát; nếu top frame là `libjvm.so`, native libraries list và command line có thể tiết lộ agent/driver gây corruption trước đó.

## 17. Không nhầm địa chỉ PC với source line

Một header có:

~~~text
SIGSEGV at pc=0x00007f0000abc123
Problematic frame:
C  [libcustom.so+0x12af]
~~~

Đây là **instruction address và offset trong native binary**, không phải `SomeClass.java:123`.

Để symbolicate/điều tra native code cần:
- Exact binary/library build (hash, build ID, symbol/debug file).
- Core dump nếu được phép; `gdb`/`lldb`/WinDbg tùy OS.
- `addr2line`/native symbols phù hợp với address mapping/ASLR.
- Native library maintainer hoặc JDK vendor khi cần.

**Không lấy offset từ một phiên bản native library rồi áp qua phiên bản khác.** Nếu binary được strip, symbolication hạn chế. Có thể dùng `libname+offset` để tìm cùng bug trên exact build, nhưng đây không phải root-cause proof.

## 18. JNI/JVMTI và thư viện bên thứ ba

Các nguồn native code phổ biến:
- JNI bridges tới C/C++ libraries.
- Java agents sử dụng JVMTI hoặc native instrumentations.
- Compression/SSL/database driver native components.
- Graphics/AWT/Swing native libraries.
- Memory-mapped/native I/O code.
- Foreign Function & Memory API ở JDK hỗ trợ.

Nếu `Problematic frame: C [libthirdparty.so+...]`, ưu tiên:
1. Xác định exact library build/version và nơi nó được load.
2. Kiểm tra JDK/OS/architecture/ABI compatibility.
3. Xem recent library upgrades, native agent/profiler changes.
4. Reproduce trong staging với input tương tự.
5. Sử dụng `-Xcheck:jni` trên test environment nếu JNI bug khả nghi.
6. Thu native core/debug symbols theo policy.
7. Báo bug cho owner của library với sanitized evidence.

**`-Xcheck:jni`** giúp phát hiện một số lỗi sử dụng JNI APIs, **không tìm ra mọi memory corruption**, có overhead và không nên bật không kiểm soát trên production.

### Fault ở libjvm không loại trừ JNI bug

Một JNI library có thể ghi hỏng heap/native pointer và return; sau đó VM crash ở một chỗ không liên quan. Fault-site frame phản ánh **nơi hỏng được phát hiện**, không nhất thiết nơi nó được tạo ra.

## 19. Lỗi trong JIT compiled code và compiler thread

Một `J` frame hoặc current thread `C2 CompilerThread` có thể gợi ý vấn đề liên quan JIT, nhưng cần bằng chứng bổ sung.

Hướng điều tra:
- Exact JDK vendor/build; kiểm tra bug đã được sửa trong update mới hơn.
- Method/class đang được compile hoặc đang chạy khi crash.
- VM arguments có flags experimental/compiler tuning không.
- Reproducibility với cùng workload/data.
- Thử Java invocation trong **staging** với `-Xint` (interpreter-only) để xem crash còn hay không; đây là **triage experiment** chứ không phải bằng chứng JIT chắc chắn là thủ phạm.
- Nếu vendor cần, thu compilation logs/replay inputs theo tài liệu và privacy constraints.

**Thử -Xint có thể làm chương trình cực chậm**, thay đổi timing, GC/lifecycle và khiến race/native bug biến mất. “Không crash ở -Xint” chỉ là gợi ý về dependency/timing/compiled path, không kết luận duy nhất về JIT bug.

### Tránh workaround mù quáng

Flags như `-XX:CompileCommand=exclude,...` có thể thay behavior của một method, nhưng syntax/method selectors/version phải được xác minh và benchmark. Không triển khai hàng loạt một workaround compiler chỉ vì gặp `J` frame một lần.

## 20. Lỗi khi GC hoặc VM Thread thực thi

Nếu current thread là GC worker hoặc VM Thread:
- Kiểm tra collector type và runtime build.
- Có cgroup/native memory pressure, corrupted references hoặc unsafe/native code?
- Có recent change của GC flags, JNI agent, driver hoặc JDK?
- Error location có được liên hệ với một collector-specific source file/function không?
- Reproduce với cùng load/heap/collector và quan sát GC logs.

Lỗi trong GC có thể là HotSpot bug thật, nhưng cũng có thể chỉ là nơi memory corruption trước đó phát lộ. Hãy **thu thập chứng cứ rồi triage theo vendor**, không mặc định “đổi GC là sửa được”.

Nếu ứng dụng đã deploy JDK update cũ nhiều năm, kiểm tra patch releases hỗ trợ mới nhất là một nhánh xử lý thực tế; vẫn cần confirm compatibility/performance.

## 21. Java 17+ demo 2 — phân tích một header hs_err mô phỏng an toàn

**Không cố tạo SIGSEGV để thực hành.** Bạn có thể học cấu trúc hs_err bằng một đoạn text mô phỏng mà không làm hỏng JVM.

Tạo file **`HsErrHeaderInspector.java`**:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;

public class HsErrHeaderInspector {
    private static final String SAMPLE = String.join("\n",
            "# A fatal error has been detected by the Java Runtime Environment:",
            "#",
            "# SIGSEGV (0xb) at pc=0x00000001, pid=4242, tid=4243",
            "#",
            "# Problematic frame:",
            "# C  [libexample.so+0x2a]",
            "#");

    public static void main(String[] args) throws Exception {
        String report = args.length == 0
                ? SAMPLE
                : Files.readString(Path.of(args[0]));

        String signal = "unknown";
        String frame = "unknown";
        boolean nextIsFrame = false;

        for (String line : report.split("\\R")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("# SIGSEGV")) {
                signal = "SIGSEGV";
            } else if (trimmed.startsWith("# SIGBUS")) {
                signal = "SIGBUS";
            } else if (trimmed.startsWith("# EXCEPTION_ACCESS_VIOLATION")) {
                signal = "EXCEPTION_ACCESS_VIOLATION";
            }

            if (nextIsFrame && trimmed.startsWith("#")) {
                String candidate = trimmed.substring(1).trim();
                if (!candidate.isEmpty()) {
                    frame = candidate;
                    nextIsFrame = false;
                }
            }

            if (trimmed.equals("# Problematic frame:")) {
                nextIsFrame = true;
            }
        }

        System.out.println("signal=" + signal);
        System.out.println("frame=" + frame);
    }
}
~~~

Chạy JDK 17+:

~~~bash
javac --release 17 HsErrHeaderInspector.java
java HsErrHeaderInspector
~~~

**Stdout chính xác cho SAMPLE nội bộ:**

~~~text
signal=SIGSEGV
frame=C  [libexample.so+0x2a]
~~~

Giải thích:
- SAMPLE chỉ là string, không làm JVM crash.
- Chương trình nhận diện một vài signal patterns và dòng sau `# Problematic frame:`.
- `split("\\R")` dùng regex Java cho Unicode line breaks.
- Khi không truyền file, output cố định; nếu truyền path tới hs_err thật, input/format có thể khác và kết quả không được hứa giống SAMPLE.
- Đây là **demo học đọc header**, không phải parser tổng quát an toàn cho mọi release/format. Real files có thể truncated, multi-line frame hoặc các error paths khác.

Thử với file do bạn **được phép đọc**:

~~~bash
java HsErrHeaderInspector /path/to/hs_err_pid4242.log
~~~

Path là placeholder; không bắt buộc phải có crash file để hoàn thành bài.

## 22. Quy trình triage 10 phút đầu khi production JVM dừng

**Bước 1 — Khôi phục và giữ bằng chứng.** Theo runbook, khôi phục service hoặc failover an toàn. Không xóa pod/log artifacts trước khi thu mốc thời gian, image digest, JDK version, last exit reason, signal và mọi hs_err.

**Bước 2 — Phân loại termination.** Có Java stack trace/uncaught Throwable? Có fatal header? Kubernetes ghi OOMKilled? Exit 137/143? Có normal shutdown? Dùng nhiều nguồn để xác thực.

**Bước 3 — Xác định scope.** Một request, một thread, một process, một Pod, nhiều replicas hay toàn node? Có restart storm không? Nếu chỉ một worker exception mà app vẫn sống, incident category khác process crash.

**Bước 4 — Tìm hs_err đúng process/time.** Xem working directory, configured ErrorFile path, OS temp directory, attached log volume. Nếu không có, ghi rõ **not found**, đừng suy crash source.

**Bước 5 — Đọc header.** Signal, pid/tid, PC, JDK vendor/build, problematic frame, core dump status.

**Bước 6 — Đọc current thread/native frames.** Native library/JNI? Compiler thread? GC/VM? Java method đang active? Có error-during-reporting?

**Bước 7 — Correlate.** So mốc crash với deploy, driver/native library upgrade, load spike, GC pressure, cgroup OOM/throttling, node failures, OS kernel logs.

**Bước 8 — Snapshot config.** JVM args, agents, GC, heap, thread/FD limits, container image digest, OS/CPU architecture. Cẩn trọng secrets.

**Bước 9 — Reproduce trong staging.** Cùng JDK/lib versions, input/workload phù hợp. Chỉ dùng flags debugging/experimental có overhead trong môi trường được phép.

**Bước 10 — Routing và mitigation.** Native component owner, JDK vendor, platform team hay application team? Document evidence, severity, rollback/patch, regression tests và follow-up.

Một incident có thể cần nhiều vòng phân tích hơn 10 phút; đây là **thứ tự ưu tiên ban đầu**, không phải SLA giải quyết root cause.

## 23. Linux command-line checklist

Với quyền phù hợp, khi process còn chạy:

~~~bash
java -version
jcmd
jcmd PID VM.version
jcmd PID VM.command_line
jcmd PID VM.flags
jcmd PID Thread.print
~~~

`PID` phải thay bằng PID thật; `jcmd` không thể attach vào process đã bị kill/crashed. Sau sự cố, tập trung vào saved logs, external monitoring, process supervisor, core dumps và container status.

Tìm fatal logs ở folder đã cấu hình (mẫu Linux):

~~~bash
ls -lah /var/log/myapp/hs_err_pid*.log
ls -lah /tmp/hs_err_pid*.log
~~~

Không có file là kết quả hợp lệ; hãy kiểm tra ErrorFile path/current working directory/service permissions. Tránh dùng recursive filesystem search trên toàn host nếu không được phép; có thể gây I/O overhead và lộ files ngoài phạm vi.

Một số lệnh kiểm tra Pod (tên giả):

~~~bash
kubectl describe pod my-pod -n my-namespace
kubectl get pod my-pod -n my-namespace -o yaml
kubectl logs my-pod -n my-namespace --previous
~~~

Các lệnh yêu cầu quyền cluster, Pod/container names phù hợp. Nếu có nhiều containers, `--previous` và `-c container-name` cần được chọn đúng. Không có previous logs không đồng nghĩa không có lỗi; container runtime retention có giới hạn.

## 24. Windows crash diagnostics

Trên Windows, báo cáo có thể dùng:

~~~text
EXCEPTION_ACCESS_VIOLATION (0xc0000005)
Problematic frame:
C  [thirdparty.dll+0x1abc]
~~~

Windows Error Reporting (WER), Event Viewer và crash dumps có thể cung cấp thêm evidence. Symbolication trên Windows có thể dùng WinDbg và đúng PDB/symbol server nếu được cho phép.

Đừng áp nguyên Linux `SIGSEGV`/`/tmp` paths cho Windows. `-XX:ErrorFile=...` vẫn có vai trò trên HotSpot Windows, nhưng đường dẫn, shell escaping, file permissions, crash dump policies và code-signing/antivirus interactions khác.

**Không upload nguyên crash dumps lên nơi công khai**: process dump có thể chứa toàn bộ memory pages, credentials, session tokens và dữ liệu khách hàng.

## 25. Core dump khác hs_err text log

| Tập tin | Định dạng | Có thể dùng làm gì? | Rủi ro |
| --- | --- | --- | --- |
| `hs_err_pid*.log` | Text fatal-error report | Signal, frames, VM/thread/process/system context | Chứa paths, CLI/env, memory snippets và identifiers |
| OS core dump | Binary process memory/register snapshot | Native postmortem debugging, symbolication | Có thể rất lớn, chứa dữ liệu nhạy cảm |
| Java heap dump `.hprof` | Java heap object graph | Heap OOME/leak/retention analysis | Chứa strings, objects, secrets, PII |
| Java thread dump | Stack/state snapshot | Deadlocks, blocked threads, workload stalls | Có thể chứa class/method names và runtime context |
| GC log/JFR | Runtime events/timing | Memory/GC/JIT/thread behavior | Overhead và potential sensitive context |
| Container events/logs | Supervisor/kernel state | OOMKilled, restart, scheduling causes | Retention và access policy |

Một **heap dump** không thay native core dump khi cần unwind C/C++ frames. Một **core dump** không đảm bảo có đủ Java object diagnostics nếu VM state corrupt; một hs_err text file có thể đủ để định tuyến nhưng không luôn đủ để xác định precise root cause.

### Khi nào tạo heap dump?

`-XX:+HeapDumpOnOutOfMemoryError` được dùng khi muốn điều tra Java heap OOME. Nó không phải global “dùng khi JVM crash” switch và không bảo đảm heap dump khi OS SIGKILL. Hãy quản lý dump path, disk capacity và quyền truy cập.

## 26. Bảo mật: hs_err có thể chứa dữ liệu nhạy cảm

Fatal-error log có thể chứa:
- JVM command-line arguments, ví dụ `-Dpassword=...` hoặc đường dẫn riêng tư.
- Environment variables như tokens, service endpoints, cloud credentials.
- Native library names, filesystem/user paths.
- Memory addresses, process/thread metadata.
- Trong một số phần, bytes/values từ stack/register/heap hoặc tình trạng system.

Trước khi chia sẻ với vendor, issue tracker hay team khác:
1. Xác định data classification và đối tượng được phép xem.
2. Tạo **bản sao** phục vụ incident, không chỉnh raw evidence duy nhất.
3. Redact credentials, tokens, personal data, internal hostnames theo policy.
4. Ghi rõ bản redacted có thể thiếu thông tin cần cho symbolication.
5. Cấp quyền tối thiểu cho log/volume/storage.
6. Đặt TTL và quy trình xóa dữ liệu sau điều tra.
7. Không paste toàn bộ hs_err/core dump vào ticket công khai.
8. Nếu file chứa secrets thực, thực hiện rotation/incident handling phù hợp.

**Đừng hardcode secrets trong JVM -D flags** khi chúng có thể xuất hiện trong process listings, crash logs hoặc diagnostics. Sử dụng secret-delivery mechanism phù hợp với hạ tầng.

## 27. Phân tích một tình huống giả định

Giả sử một service ngừng hoạt động:
- Java logs không có NPE/IllegalStateException mới.
- OS/Kubernetes báo exit 134 ở Linux và application output có fatal header.
- Tồn tại `/var/log/java/hs_err_pid4242.log`.
- Header ghi `SIGSEGV` và `Problematic frame: C [libcompression.so+0x2ad]`.
- Java frames cho thấy `CompressionAdapter.compress(...)`.
- Tuần trước vừa cập nhật native compression library.

Đây là dữ liệu giả định. Hướng triage hợp lý:
1. Xác thực crash type/native frame, không gọi đây là “Java NPE”.
2. Lấy exact native library build/hashes.
3. Kiểm tra JVM/lib ABI và khác biệt deployment, Java agents.
4. Reproduce với controlled workload trong staging.
5. Kiểm tra `-Xcheck:jni` nếu JNI path dùng sai contract.
6. Nếu native library có bug, cập nhật/rollback/test bản được hỗ trợ.
7. Nếu vẫn tái diễn ở JVM code, thu thêm core/logs và báo JDK vendor.

**Không khẳng định libcompression là root cause chỉ vì top frame**: memory corruption từ nơi khác có thể lộ tại điểm compress.

### Tình huống ngược lại: không có hs_err

Nếu Pod báo `OOMKilled`, exit 137, cgroup `oom_kill` tăng và container memory usage sát limit:
- Ưu tiên bài toán **total-process/native memory budget**.
- So heap metrics, NMT, direct buffers, thread stacks, sidecars.
- Xem memory limit/requests và workload spike.
- Đừng trì hoãn khắc phục vì “không thấy hs_err”.

## 28. Những sai lầm thường gặp

| Ngộ nhận | Thực tế |
| --- | --- |
| “NullPointerException nghĩa JVM segfault.” | NPE là Java exception theo semantics; không mặc định native crash. |
| “Một worker ném uncaught exception là cả VM crashed.” | Worker thường kết thúc, process có thể tiếp tục. |
| “Main exit code 1 là HotSpot fatal error.” | Có thể là uncaught exception hoặc System.exit(1). |
| “OutOfMemoryError luôn tạo hs_err.” | Heap OOME thường là Java Error; fatal handling phụ thuộc loại lỗi/flags. |
| “Exit 137 chắc chắn do OOMKilled.” | 137 thường là SIGKILL convention; cần status/events xác nhận. |
| “OOMKilled phải có Java stack trace.” | Kernel kill không chạy Java exception handler. |
| “Không có hs_err thì không có JVM crash.” | Có thể log không ghi được hoặc OS killed process. |
| “Có hs_err tức bug JVM chắc chắn.” | Có thể do JNI/agent/native/OS/hardware/resource exhaustion. |
| “Problematic frame libjvm.so chứng minh VM bug.” | Memory corruption trước đó có thể gây fault sau trong libjvm. |
| “Frame C chứng minh native library là nơi ghi sai pointer.” | C frame là nơi phát hiện fault, chưa chắc nơi corruption phát sinh. |
| “Java frame J chứng minh source line Java sai.” | Compiled frame có thể chỉ điểm thực thi, JIT/native corruption cần xét. |
| “PC address là Java source line.” | PC là native instruction address. |
| “tid bằng Thread.getId().” | Native thread ID khác Java thread identity. |
| “SIGSEGV thấy trong debugger luôn fatal.” | HotSpot có thể xử lý signals nội bộ bình thường. |
| “-Xcheck:jni bắt được mọi memory corruption.” | Nó chỉ kiểm một tập JNI usage bugs, có overhead. |
| “Dùng -Xint mà không còn crash chắc chắn JIT bug.” | Thay execution/timing, không đủ chứng minh root cause. |
| “Heap dump và core dump cùng một thứ.” | Khác scope, format và mục đích. |
| “hs_err luôn nằm cạnh JAR.” | Thường thử process working directory hoặc configured ErrorFile/temp. |
| “hs_err sẽ luôn ghi xong và đúng format.” | Crash handler có thể fail, format thay theo JDK. |
| “Có ErrorFile thì OOMKilled sẽ tạo hs_err.” | SIGKILL không cho JVM xử lý fatal error. |
| “SIGABRT giống mọi Java Exception.” | SIGABRT là OS/native context; không tự chuyển thành Java Throwable. |
| “Core dump chia sẻ an toàn vì chỉ là debug data.” | Có thể chứa secrets/PII/process memory. |

## 29. Câu hỏi tự kiểm tra

**1. Java `NullPointerException` có phải HotSpot fatal error không?**  
Không. Thông thường nó là một Java RuntimeException theo Java semantics.

**2. Worker thread ném uncaught exception có làm JVM luôn exit không?**  
Không. Thread có thể kết thúc trong khi các non-daemon threads khác tiếp tục.

**3. Vì sao `hs_err_pid4242.log` có số 4242?**  
Theo convention default của HotSpot đó là PID của JVM process lúc fatal error.

**4. Có thể đặt vị trí hs_err bằng flag nào?**  
`-XX:ErrorFile=/writable/path/hs_err_pid%p.log` trên HotSpot hỗ trợ.

**5. Nếu không thấy hs_err cạnh JAR, tìm ở đâu?**  
Configured ErrorFile path, process working directory, OS temp directory và artifact volumes, lưu ý file có thể không được tạo.

**6. `Problematic frame: C [libfoo.so+0x123]` nói gì?**  
Fault-site được quy về một native-library offset, chưa đủ khẳng định root cause.

**7. `Problematic frame: V [libjvm.so+...]` là bằng chứng JVM bug chắc chắn?**  
Không. Memory corruption/agents/native dependencies có thể gây fault bên trong VM.

**8. Ký hiệu `J` có nghĩa gì?**  
Compiled Java code frame trong notation HotSpot diagnostics.

**9. Native frame và Java frame khác nhau thế nào?**  
Native stack chứa các native/VM frames, Java stack mô tả Java method context phù hợp.

**10. `pid` và `tid` có cùng ý nghĩa?**  
Không. pid là process ID, tid là native thread ID theo OS context.

**11. Exit 137 có tự chứng minh OOMKilled?**  
Không. Cần Pod status/cgroup events/OS evidence.

**12. Kernel SIGKILL có chạy JVM shutdown hooks không?**  
Không.

**13. Java heap OOME có nhất thiết tạo hs_err?**  
Không. Thông thường có Java Error/trace; fatal-error log phụ thuộc mechanism/flags.

**14. `-XX:+HeapDumpOnOutOfMemoryError` có bảo đảm heap dump khi OS OOMKilled?**  
Không; JVM không có cơ hội thực thi handler khi kernel kill.

**15. Khi gặp frame C từ JNI, công cụ kiểm tra nào có thể hỗ trợ?**  
`-Xcheck:jni` trong staging, cộng native symbols/core dump và library-specific diagnostics.

**16. Vì sao nên biết exact JDK build?**  
Một crash có thể phụ thuộc JIT/GC/native ABI và đã được fix ở patch release khác.

**17. Có được gửi hs_err lên issue tracker công khai không?**  
Không nên gửi raw file khi chưa kiểm tra/redact secrets, PII và internal information.

**18. Một hs_err có `error occurred during error reporting` nghĩa là gì?**  
Crash handler gặp lỗi khi thu thập report; file có thể không đầy đủ, không nhất thiết reporting code là root cause ban đầu.

**19. Không crash khi chạy -Xint thì kết luận gì?**  
Đây là gợi ý về execution/timing/JIT path, không là chứng minh độc nhất JIT bug.

**20. Bằng chứng đầu tiên cần giữ khi incident?**  
Exit status/reason, timestamps, application/fatal logs, JDK build, VM flags, container/OS events và crash artifacts nếu có.

## 30. Quy trình quyết định nhanh

~~~text
            JVM / APPLICATION STOPPED?
                        |
                        v
            Is there Java Throwable trace?
                     /          \
                   YES           NO / UNKNOWN
                    |                 |
             Exception/Error      Exit reason?
                    |                 |
        Uncaught? main? worker?        +-- OOMKilled/137?
                    |                 |      cgroup/Pod evidence
        Application/Thread triage       |
                    |                   +-- SIGTERM/System.exit?
                    |                   |      lifecycle investigation
                    |                   |
                    |                   +-- Fatal header/hs_err?
                    |                          |
                    |                     READ HEADER
                    |                          |
                    |                   signal / problematic frame
                    |                          |
                    |                C native? V/v VM? J/JIT?
                    |                          |
                    |                 current thread / libraries
                    |                          |
                    +-------------+------------+
                                  |
                      COLLECT SAFE EVIDENCE
                      JDK build + args + events
                                  |
                          REPRODUCE IN STAGING
                                  |
                     APPLICATION / NATIVE / JDK /
                        OS / RESOURCE OWNER
                                  |
                      MITIGATION + REGRESSION
~~~

**Kết luận:** **Java Throwable** là sự kiện ở tầng ngôn ngữ có thể bị catch hoặc làm một thread dừng; **JVM fatal crash** là lỗi khiến HotSpot không thể tiếp tục an toàn và thường sinh **hs_err_pid**; **OS/container kill** có thể dừng tiến trình mà không để JVM ghi bất kỳ fatal-error log nào. Phân biệt các đường này trước khi sửa code hay tuning heap. Khi có hs_err, hãy đọc **signal + problematic frame + current thread + native/Java frames + runtime/libraries/system context**, tìm nguyên nhân với bằng chứng nhiều nguồn, không gán root cause chỉ từ một dòng. Bảo vệ dữ liệu nhạy cảm trong logs/dumps suốt quá trình điều tra.

## Tài liệu chính thức

- [Oracle Java 21 — Fatal Error Log: location, header, thread/process/system sections](https://docs.oracle.com/en/java/javase/21/troubleshoot/location-fatal-error-log.html).
- [Oracle Java 21 — Troubleshoot System Crashes](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshoot-system-crashes.html).
- [Oracle Java 25 — Troubleshooting Guide, description of fatal error log](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshooting-guide.pdf).
- [Oracle Java 21 — Introduction to Client Issues (hs_err and native libraries)](https://docs.oracle.com/en/java/javase/21/troubleshoot/introduction-client-issues.html).
- [Oracle Java 21 — Troubleshoot Memory Leaks and native allocation failures](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html).
- [Oracle Java 21 — java command JVM diagnostic flags](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).
- [Oracle Java 21 — jcmd diagnostic tool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html).
- [Oracle Java 17 — Throwable API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html).
- [Oracle Java 17 — Thread.UncaughtExceptionHandler API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.UncaughtExceptionHandler.html).
- [Oracle Java 17 — OutOfMemoryError API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/OutOfMemoryError.html).
- [Oracle Java 17 — Runtime.halt API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html#halt(int)).
- [OpenJDK — HotSpot sources and runtime fatal error handler](https://github.com/openjdk/jdk/tree/master/src/hotspot/share/utilities).
- [Linux kernel documentation — Control Group v2 memory events](https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html).
- [Kubernetes — Resource Management for Pods and Containers](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-observability-and-errors-012">Chọn thao tác chẩn đoán theo tác động dừng, CPU và dung lượng dữ liệu tạo ra</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn thao tác chẩn đoán theo tác động dừng, CPU và dung lượng dữ liệu tạo ra

## 1. Nguyên tắc nền tảng: chẩn đoán cũng là một workload

Khi JVM có dấu hiệu **CPU cao, nhiều threads bị chờ, độ trễ tăng, tăng heap/native memory hoặc chuẩn bị hết tài nguyên**, kỹ sư cần thu được bằng chứng đủ tốt để tìm nguyên nhân. Tuy nhiên một công cụ chẩn đoán có thể **làm ứng dụng dừng, dùng thêm CPU, tăng I/O, sinh file lớn hoặc thay đổi trạng thái heap**. Vì vậy “cứ chạy heap dump để xem” không phải trình tự an toàn cho mọi incident.

**Ba trục bắt buộc cân nhắc cho mỗi thao tác:**

1. **Tác động dừng / pause:** lệnh có yêu cầu safepoint/stop-the-world hoặc chặn một phần application threads không? Nếu có, thời gian có thể tăng theo lượng dữ liệu và tải hiện tại.
2. **CPU, bộ nhớ và I/O:** lệnh có quét objects/stack, sampling, encode events, ghi file, nén dữ liệu hay gây GC không?
3. **Dung lượng đầu ra:** stdout, log files, `.jfr`, heap dump `.hprof`, core dump hay metadata files có thể lớn tới mức nào? Đĩa có đủ chỗ, tốc độ ghi có đáp ứng, dữ liệu có nhạy cảm không?

Không đánh đồng các trục: một lệnh được HotSpot phân loại **Impact: Low** vẫn có thể sinh file lớn nếu bạn ghi rất lâu; một heap histogram không sinh file `.hprof` lớn bằng heap dump nhưng có **tác động xử lý/scan heap cao**.

### Quy tắc mặc định trong production

**Ưu tiên thu thập ít xâm lấn → đủ bằng chứng thì dừng → chỉ nâng cấp mức xâm lấn khi có giả thuyết, quyền vận hành và ngân sách tài nguyên.**

```text
Triệu chứng JVM
      |
      v
Metrics/logs hiện có, CPU/GC/thread trends
      |
      v
jcmd các lệnh Low, JFR default nếu hợp lý
      |
      +------ đủ chứng cứ? ---- Có ---> xác định nguyên nhân/giải pháp
      |
     Không
      |
      v
Thread dump / targeted JFR profile / NMT
      |
      +------ đủ chứng cứ? ---- Có ---> xác định nguyên nhân/giải pháp
      |
     Không
      |
      v
Đánh giá tác động + phê duyệt + đĩa + dữ liệu nhạy cảm
      |
      v
GC.class_histogram / GC.heap_dump hoặc chuyên sâu
      |
      v
Phân tích offline, kiểm soát file, dọn dẹp an toàn
```

Không có câu “High luôn cấm”. Nếu cần xác định retention path của memory leak nghiêm trọng, heap dump có thể có giá trị rất lớn. Nhưng phải biết **giá phải trả**, chọn thời điểm và giới hạn blast radius phù hợp.

## 2. Khái niệm impact trong tài liệu `jcmd` của JDK 17

Tài liệu chính thức Oracle JDK 17 liệt kê nhiều lệnh chẩn đoán và **Impact: Low / Medium / High**:

- **Low:** tác động thường thấp trong điều kiện thông thường, không phải cam kết zero pause/zero CPU.
- **Medium:** có overhead đáng chú ý, thường phụ thuộc số threads, số classes, native memory hoặc nội dung JVM.
- **High:** có thể tác động lớn theo kích thước và nội dung heap; không nên chạy tùy tiện khi production latency nhạy cảm.

Ví dụ từ **Oracle JDK 17 `jcmd`**:

| Lệnh | Impact do tài liệu JDK 17 nêu | Khía cạnh chính cần cân nhắc |
| --- | --- | --- |
| `VM.uptime` | **Low** | Ít dữ liệu, thuận tiện nhận diện JVM còn sống |
| `VM.version` | **Low** | Thông tin build/version |
| `VM.flags` | **Low** | Flags hiện thời, output phụ thuộc số cờ |
| `VM.command_line` | **Low** | Có thể lộ args nhạy cảm; hạn chế chia sẻ |
| `Compiler.codecache` | **Low** | Báo code cache layout/occupancy |
| `Compiler.queue` | **Low** | Số methods chờ JIT compilation |
| `VM.classloader_stats` | **Low** | Class loader statistics |
| `JFR.check` | **Low** | Danh sách recordings đang chạy |
| `JFR.start` | **Low** | Tác động còn phụ thuộc template/events của recording |
| `JFR.dump` | **Low** | Có thể ghi lượng dữ liệu lớn ra file |
| `Thread.print` | **Medium** | Phụ thuộc số threads và stack depth |
| `VM.native_memory` | **Medium** | NMT phải được bật từ startup, thêm overhead |
| `GC.heap_info` | **Medium** | Thông tin heap tổng quát |
| `GC.class_histogram` | **High** | Heap traversal, có thể tác động pause và CPU |
| `GC.heap_dump` | **High** | Quét heap, có thể Full GC, sinh HPROF rất lớn |
| `GC.run` | **Medium** | Yêu cầu `System.gc()`; không nên dùng làm query |
| `VM.cds` | **Medium** | Khi tạo archive, pause phụ thuộc số classes |

**Lưu ý:** Đây là **nhãn tác động theo tài liệu JDK 17**, không phải SLA định lượng: “Low = dưới 5 ms” hay “Medium = dưới 1 giây” đều là khẳng định vô căn cứ. Thời gian và độ lớn phải đo trên workload/JVM thật.

### 2.1. Impact còn phụ thuộc JDK version

HotSpot JDK 21/24/26 có thể thêm lệnh/options (ví dụ thread dump sang file, compression options cho heap dump hoặc virtual thread diagnostics). Bài học này lấy **JDK 17** làm mốc; luôn chạy:

```bash
jcmd <pid> help
jcmd <pid> help GC.heap_dump
jcmd <pid> help Thread.print
```

`<pid>` là ký hiệu thay bằng process ID thực tế. Không copy ký tự `<` và `>` vào shell vì có thể là toán tử redirect.

### 2.2. Không suy command tồn tại nếu JDK không liệt kê

Ví dụ `Thread.dump_to_file` xuất hiện ở những JDK mới, **không coi là lệnh chuẩn có sẵn trên mọi JDK 17**. Với JDK 17 dùng `Thread.print` và redirect output ở phía shell nếu cần.

## 3. Ba loại “dừng” dễ bị nhầm lẫn

**Stop-the-world (STW):** các application Java threads bị tạm dừng cho một pha VM operation nào đó, chẳng hạn một số GC pauses hoặc heap inspection. Độ dài tùy collector/heap/workload.

**Safepoint / handshake:** JVM phối hợp một hoặc nhiều threads để chạy các tác vụ cần trạng thái runtime nhất quán. Không phải mọi safepoint đều là một Full GC; cũng không phải mọi lệnh jcmd đều luôn có global STW dài.

**Blocking do I/O / contention:** lệnh có thể chờ ghi file hoặc giành CPU/locks, làm application latency tăng ngay cả khi không có một STW pause lớn.

### 3.1. “Không Full GC” không có nghĩa “zero pause”

Ví dụ `GC.heap_dump -all` trong JDK 17 **không yêu cầu Full GC theo cơ chế mặc định của heap dump**, nhưng vẫn có **Impact: High** vì phải kiểm tra/ghi dữ liệu heap lớn.

### 3.2. “Chạy async từ shell” không làm JVM work biến mất

Một parent shell có thể chạy command background, nhưng phần việc attach/heap traversal/output trong JVM/OS vẫn tiêu tốn tài nguyên. `&` hoặc PowerShell Start-Process chỉ đổi cách shell chờ lệnh, không khiến tác động production biến mất.

### 3.3. Stop-time phải xem p99/p999, không chỉ trung bình

Một lệnh gây vài seconds pause trong giờ thấp điểm vẫn có thể phá SLO của một giao dịch latency-sensitive. Trước khi chạy operation High, nên xem **latency budget**, traffic và khả năng divert traffic của replica.

## 4. Ba nguồn chi phí CPU và memory khi chẩn đoán

Một operation có thể tác động tới:

- **CPU time trong JVM:** quét heap, thu thread stacks, giải mã metadata, JFR event generation, GC.
- **CPU bên ngoài JVM:** file compression, profiler/analysis tool, OS filesystem activities.
- **I/O/disk:** ghi `hprof`/`jfr`/logs, page cache pressure, filesystem throughput.
- **Memory overhead:** recording buffers, JVM internal data structures, native tracking overhead, allocations của tool xử lý output.

### 4.1. CPU quota của container là một ràng buộc quan trọng

Khi container chỉ có 1 vCPU quota, thêm hoạt động profiler hoặc heap inspection có thể cạnh tranh mạnh với application work. Một command có vẻ “nhanh” trên workstation 32 cores không nhất thiết nhanh trên pod hạn chế CPU.

### 4.2. Output size có thể là nguyên nhân incident mới

Ghi một tệp heap dump vào volume gần đầy có thể làm hệ thống mất log, ứng dụng khác thất bại I/O hoặc khiến pod bị eviction. Chọn storage tách biệt với critical application writes nếu có thể.

### 4.3. CPU sampling khác full instrumentation

JFR default liên tục thường overhead thấp; profile settings thu thêm events và stack sampling, làm output/CPU tăng. Bật **heap statistics/GC-root paths** có thể gây tác động cao hơn nhiều so với chỉ thu basic events.

## 5. Phân loại thao tác theo câu hỏi chẩn đoán

| Câu hỏi cần trả lời | Mức thu thập nên bắt đầu | Không vội thực hiện |
| --- | --- | --- |
| JVM nào đang chạy, version/flags nào? | `jcmd -l`, `VM.version`, `VM.flags` | Heap dump |
| Heap sắp đầy hay GC nhiều? | GC metrics/logs hiện có, `GC.heap_info` | Ép `GC.run` |
| Có threads chờ hoặc deadlock? | Thread metrics, `Thread.print -l` khi cần | Heap dump trước khi đọc stacks |
| Code cache/JIT bị nghẽn? | `Compiler.codecache`, `Compiler.queue` | Tracing tất cả compiler events vô hạn |
| CPU spike không rõ hot method? | JFR default/profile có thời hạn, OS CPU metrics | Heap dump như bước đầu |
| Native memory có tăng? | OS RSS/cgroup metrics, NMT summary nếu đã bật | Tuyên bố NMT bằng toàn bộ RSS |
| Nghi class loader retention? | Loaded class counts, `VM.classloader_stats`, JFR/NMT | Chụp heap dump ngay không có giả thuyết |
| Nghi heap objects bị giữ? | Heap trend, histogram có phê duyệt, dump cuối cùng | Chạy heap dump nhiều lần liên tiếp |
| Quá tải output/log disk? | Metrics, nhỏ hóa duration/events/output | `JFR.start settings=profile` liên tục không giới hạn |

**Một triệu chứng có thể có nhiều nguyên nhân.** CPU cao không tự đồng nghĩa GC, Memory leak không tự đồng nghĩa heap. Chọn thao tác chẩn đoán nên dựa trên câu hỏi cụ thể và bằng chứng sơ bộ.

## 6. Bước 0 — xác định đúng JVM và quyền truy cập

Trên host có JDK:

```bash
jcmd -l
```

Để xem commands mà **JVM đích thực sự hỗ trợ**:

```bash
jcmd <pid> help
```

Cần thay `<pid>` bằng PID chính xác. `jcmd` phải chạy trên cùng host với JVM và theo quy tắc quyền người dùng/nhóm, attach settings và container PID namespaces.

### 6.1. Tránh lệnh phát tán đến nhiều JVM

Theo tài liệu `jcmd`, PID `0` hoặc chọn tên main class có thể gửi command đến **nhiều JVM processes**. Trong production, dùng **PID chính xác** và xác minh `VM.command_line`, `VM.version` trước thao tác High.

### 6.2. PID trong container có thể khác host

PID nhìn từ Kubernetes/container namespace có thể khác host PID. Lệnh `jcmd -l` cũng không nhất thiết liệt kê các Java processes trong separate Docker process namespace. Dùng công cụ container/OS để xác định PID phù hợp.

### 6.3. Không bỏ qua quyền và bảo mật

Đối với những JVM của người dùng khác, JVM tools có thể cần quyền phù hợp; **không nên nâng đặc quyền tùy tiện**. Thu thập cần theo quy trình vận hành, audit log và least privilege.

## 7. Nhóm nhẹ — version, flags, uptime và code cache

Một bộ lệnh ban đầu:

```bash
jcmd <pid> VM.version
jcmd <pid> VM.uptime
jcmd <pid> VM.flags
jcmd <pid> Compiler.codecache
jcmd <pid> Compiler.queue
```

Những lệnh này thường thuộc nhóm **Impact: Low** trong Oracle JDK 17 `jcmd`, giúp xác định JVM build, current flags và JIT/code cache state.

### 7.1. Kiểm tra thông tin trước khi giải thích kết quả

Nếu không biết collector, heap limits, JDK build và workload context, rất dễ giải sai dấu hiệu. Ví dụ code cache used tăng trong JIT warm-up có thể bình thường; dùng một `Compiler.codecache` snapshot không chứng minh memory leak.

### 7.2. `VM.command_line` có thể chứa bí mật

Command line đôi khi chứa system properties như token, credentials, paths nội bộ, endpoints hoặc profile names. Không đăng nguyên văn lên ticket công khai. Redact các trường nhạy cảm và chỉ chia sẻ phần cần thiết.

### 7.3. `VM.flags -all` không luôn cần

In mọi cờ có thể tạo output rất dài. Nếu chỉ hỏi `-Xmx`/GC, xem các chỉ số hiệu lực liên quan thay vì thu tất cả 1000+ cờ nhiều lần.

## 8. Thread dump — chọn khi nghi deadlock, blocking hoặc contention

Trên JDK 17:

```bash
jcmd <pid> Thread.print
```

Có thể kèm thông tin về `java.util.concurrent` locks:

```bash
jcmd <pid> Thread.print -l
```

Theo tài liệu JDK 17, **Impact: Medium — phụ thuộc số threads**. Thread dump lấy stack traces của các threads ở một thời điểm, có thể có safepoint/handshake/stack walking overhead và lượng output lớn nếu JVM có nhiều threads.

### 8.1. Kích thước output tăng theo gì?

Một ước lượng sơ bộ:

```text
output_bytes ≈ thread_count × average_frames_per_thread
               × average_bytes_per_frame
               + headers + monitor/lock details
```

Giả sử **1.000 threads × 40 stack frames × 120 bytes/frame**, phần frames riêng đã là khoảng **4,8 MB** trước headers và metadata khác. Đây là **mô hình tính dung lượng**, không phải hứa output chính xác của HotSpot.

### 8.2. Một dump không chứng minh deadlock vĩnh viễn

Một thread có trạng thái `BLOCKED` tại snapshot có thể hết chờ ngay sau đó. Với tình huống treo/nghẽn nghiêm trọng, nên thu **một vài dumps cách nhau có chủ đích** để xem stack có tiến triển không, nhưng không tự động lấy hàng trăm dumps mỗi giây.

### 8.3. Giữ dữ liệu khỏi tràn stdout

Trên Linux/Bash, có thể redirect `jcmd` output:

```bash
jcmd <pid> Thread.print -l > thread-dump.txt
```

Trên PowerShell:

```powershell
jcmd <pid> Thread.print -l | Out-File -FilePath thread-dump.txt -Encoding utf8
```

`<pid>` vẫn là ký hiệu. Nếu cần tránh ghi vào directory hiện tại, chọn **đường dẫn đủ dung lượng**. Redirect chỉ thay nơi ghi đầu ra, không bỏ cost lấy stack traces trong JVM.

### 8.4. JDK 17 và virtual threads

Virtual threads được chuẩn hóa sau JDK 17; đừng mặc nhiên áp commands của JDK mới như `Thread.dump_to_file -format=json` cho môi trường JDK 17. Hãy đọc `jcmd <pid> help` của process mục tiêu.

## 9. JFR default — bước trung gian hữu ích cho CPU/latency

**Java Flight Recorder (JFR)** có thể thu sự kiện về CPU execution sampling, thread blocking, allocation, GC và JVM activity tùy recording settings.

Bắt đầu một recording ngắn trên JDK 17:

```bash
jcmd <pid> JFR.start name=DiagSample settings=default duration=30s filename=diag-sample.jfr
```

**Impact: Low** là phân loại của **lệnh `JFR.start`**, không bảo đảm mọi cấu hình event và workload đều có overhead thấp tuyệt đối. `settings=default` thường phù hợp khảo sát vận hành trước khi bật profiling events nhiều hơn.

### 9.1. Tác động phụ thuộc event set

Một recording với nhiều stack traces/sampling events có thể tăng CPU/buffer/disk. Bật **path-to-gc-root=true** hoặc heap statistics có thể tạo thêm latency/pause đáng kể; không bật chỉ vì muốn “đủ hết thông tin”.

### 9.2. Điều khiển thời lượng và dung lượng

`JFR.start` cho phép `duration`, `maxage`, `maxsize` và những options khác. Với JFR liên tục:

```bash
jcmd <pid> JFR.start name=RollingDiag settings=default disk=true maxage=1h maxsize=256m
```

Các values trên chỉ là **minh họa một budget**; điều kiện storage và chunk size phải thỏa các constraints của JDK. `maxage`/`maxsize` giới hạn dữ liệu recording được giữ theo semantics JFR, không nhất thiết giới hạn toàn bộ filesystem usage của hệ thống ở đúng 256 MiB khi tính mọi recording/log khác.

### 9.3. Kiểm tra/ghi/stop

```bash
jcmd <pid> JFR.check
jcmd <pid> JFR.dump name=RollingDiag filename=rolling-diag.jfr
jcmd <pid> JFR.stop name=RollingDiag
```

Theo tài liệu JDK 17, `JFR.dump` **ghi dữ liệu ra file nhưng recording vẫn tiếp tục chạy**; `JFR.stop` mới kết thúc recording tương ứng. Không nhầm “dump” với “stop”.

### 9.4. Rủi ro khi ghi file

Nếu recording quá dài hoặc events nhiều, file `.jfr` có thể rất lớn và cạnh tranh disk I/O. Chọn directory có quota, giới hạn thời lượng và kiểm tra free space. JFR files còn có stack traces, class names, arguments/events có thể nhạy cảm.

## 10. JFR default vs profile vs heap statistics

| Kiểu | Phạm vi dữ liệu | Chi phí thường gặp | Khi nào |
| --- | --- | --- | --- |
| `settings=default` | Một tập runtime events mặc định | Thường low overhead | Tổng quan trước tiên |
| `settings=profile` | Nhiều profiling/stack-sampling events hơn | CPU/data overhead cao hơn default | Cần tìm hotspots |
| Heap statistics events | Thông tin heap có thể kích hoạt extra GC | Có thể tăng pauses đáng kể | Nghi memory leak/live set |
| `path-to-gc-root=true` | Truy vết đường giữ object từ GC roots | Có thể tốn thời gian/pause | Điều tra retention cụ thể |

Không có một phần trăm overhead chuẩn cho mọi service. Cùng `settings=profile` trên app có 20 threads và 20.000 threads là những tình huống rất khác.

### 10.1. Cấu hình thử profile có thời hạn

```bash
jcmd <pid> JFR.start name=CpuProfile settings=profile duration=30s filename=cpu-profile.jfr
```

Nên đo CPU quota/latency trước và trong thời gian ghi, và có ngưỡng dừng nếu ảnh hưởng SLO.

### 10.2. Vì sao không bật mọi event một lúc?

Nhiều event loại stacktrace/heap thống kê có thể làm lượng dữ liệu tăng vọt, ghi đĩa liên tục và gây pause. Một trace quá lớn cũng khó phân tích, tải vào JDK Mission Control tốn thời gian/RAM.

### 10.3. Không có JFR events không chứng minh không có lỗi

Event set và thresholds có thể bỏ qua sự kiện hiếm hoặc không ghi một loại dữ liệu. Cần kiểm tra recording settings, sampling interval và thời điểm issue xảy ra trước khi kết luận.

## 11. Native Memory Tracking — đúng công cụ nhưng cần bật trước

**Native Memory Tracking (NMT)** trong HotSpot theo dõi những native allocations của **VM-managed subsystems**, chẳng hạn Class, Code, Thread và GC. Dùng khi RSS/container usage tăng mà Java heap không giải thích được.

Muốn thu:

```bash
java -XX:NativeMemoryTracking=summary -jar service.jar
```

Sau đó trên process mục tiêu:

```bash
jcmd <pid> VM.native_memory summary scale=MB
```

**Theo tài liệu Oracle JDK 17, `VM.native_memory` Impact: Medium.** Việc bật NMT từ JVM startup cũng có overhead; `summary` thường ít dữ liệu hơn `detail`.

### 11.1. Baseline/diff

```bash
jcmd <pid> VM.native_memory baseline
jcmd <pid> VM.native_memory summary.diff scale=MB
```

Các lệnh giúp so allocated/reserved/committed memory theo subsystem qua một khoảng, không phải phép chứng minh mọi +MB là memory leak.

### 11.2. NMT không phải profiler của mọi native allocation

NMT **không bảo đảm phản ánh mọi allocation từ thư viện C/C++/JNI của bên thứ ba**, cũng không nhất thiết bằng process RSS. Khi JVM heap ổn định nhưng RSS tăng, kết hợp NMT, OS memory maps và native library diagnostics.

### 11.3. Không thử bật NMT retroactively trên process không hỗ trợ

NMT thường cần bật từ startup. Nếu lệnh báo disabled, chọn một JVM tái khởi động có cấu hình NMT trong môi trường kiểm soát; không cố kết luận có NMT history bằng cách gọi `jcmd` lại.

### 11.4. Có thể nhạy cảm với kích thước báo cáo

`VM.native_memory detail` sinh nhiều call-site/virtual-memory-region details hơn summary, làm tăng CPU/output. Ưu tiên summary trước, detail khi đã xác định khoảng/subsystem cần đào sâu.

## 12. `GC.heap_info` trước khi quét objects

Lệnh:

```bash
jcmd <pid> GC.heap_info
```

cho một số thông tin heap tổng quát và **Impact: Medium** trong Oracle JDK 17. Dùng để định hướng heap capacity/usage cùng GC logs/JMX metrics.

**Không nhầm `GC.heap_info` với heap dump**: nó không xuất toàn bộ object graph/retention paths. Nếu câu hỏi chỉ là “heap đang committed/used bao nhiêu?”, đừng yêu cầu `GC.heap_dump`.

### 12.1. Heap occupancy và live set khác nhau

Heap used đang tăng có thể do short-lived objects trước GC, không phải leak. Kết hợp GC cycles, old-generation trend và sustained usage sau collections để nhận diện vấn đề.

### 12.2. Không ép `GC.run` như thao tác đọc

`GC.run` yêu cầu `System.gc()`; đây là **thao tác làm thay đổi hoạt động runtime**, có thể gây GC work/pauses tùy collector và `ExplicitGCInvokesConcurrent`/`DisableExplicitGC` policy. Nó không phải lệnh chỉ quan sát.

## 13. `GC.class_histogram` — vì sao high impact?

Lệnh:

```bash
jcmd <pid> GC.class_histogram
```

báo **số lượng instances và tổng bytes theo class** trong Java heap, hữu ích khi nghi một loại object hoặc array phát triển bất thường.

Oracle JDK 17 xếp lệnh này **Impact: High, phụ thuộc heap size/content**.

### 13.1. Chi phí

- Quét/đếm nhiều objects theo class.
- Có thể đòi runtime coordination/safepoint hoặc GC behavior liên quan.
- CPU tăng cùng heap/object count.
- Output có thể gồm hàng nghìn classes, cần xử lý/redirect.
- Tác động lên latency production có thể đáng kể dù không tạo file HPROF lớn.

### 13.2. `-all` có ý nghĩa gì?

```bash
jcmd <pid> GC.class_histogram -all
```

`-all` yêu cầu bao gồm cả unreachable objects theo semantics JDK 17. Vì vậy kết quả **không cùng phạm vi** với histogram của live objects sau GC; không so hai báo cáo `-all` và mặc định rồi gọi chênh lệch là leak.

Tùy JVM/collector, các cơ chế lấy histogram có thể khác nhau. **`-all` không làm lệnh trở thành Low impact**, cũng không tạo cam kết zero safepoint.

### 13.3. Khi nào chấp nhận?

Khi có evidence heap pressure, cần xác định loại object chiếm nhiều bộ nhớ, JFR/heap usage metrics không đủ, và có khoảng chịu được potential pause. Nếu cần biết **ai giữ** object, histogram chưa đủ; lúc đó mới xem heap dump với approval.

## 14. `GC.heap_dump` — tác động dừng, CPU và dữ liệu lớn

Trong HotSpot JDK 17:

```bash
jcmd <pid> GC.heap_dump /diagnostics/heap-incident.hprof
```

Lệnh tạo heap dump **HPROF** và Oracle xếp **Impact: High**, phụ thuộc heap size/content. Tài liệu JDK 17 ghi rõ lệnh **yêu cầu Full GC mặc định**, trừ khi đặt `-all`.

### 14.1. `-all` không phải “safe mode”

```bash
jcmd <pid> GC.heap_dump -all /diagnostics/heap-incident-all.hprof
```

`-all` bao gồm cả unreachable objects, không thực hiện default Full GC request của heap dump. Tuy nhiên heap traversal, object serialization và file I/O vẫn lớn; kết quả có thể **to hơn** vì có thêm garbage chưa được thu hồi.

**Không được tuyên bố thêm `-all` làm heap dump không ngừng ứng dụng hoặc tiêu thụ rất ít CPU.**

### 14.2. Dung lượng file

Một HPROF có thể đạt **hàng trăm MiB tới nhiều GiB**, tùy live/heap objects, references, class metadata và output format. Không có công thức file size = `-Xmx` đúng tuyệt đối: heap reserved chưa chắc đã chứa objects; HPROF metadata và object representation có overhead.

Giả sử dự kiến HPROF 1,5 GiB:

- Trống disk hiện tại chỉ 1 GiB → **không chạy tại đường dẫn đó**.
- Storage còn 4 GiB nhưng bị chia sẻ với database/logs → cần đánh giá I/O và quota riêng.
- JVM có latency SLO rất chặt → cân nhắc drain replica/maintenance window.
- Có dữ liệu cá nhân/keys trong heap → cần xử lý file như **dữ liệu nhạy cảm**.

### 14.3. Nội dung nhạy cảm

Heap dump có thể chứa strings, user payloads, secrets, tokens, credential fragments, personal data và internal service configuration. Hạn chế quyền đọc, mã hóa khi truyền/lưu, đặt retention deadline, audit mọi lần tạo/chia sẻ/xóa.

**Đừng gửi HPROF tùy tiện cho dịch vụ bên ngoài hoặc đính vào ticket công khai.**

### 14.4. Đường dẫn file thuộc JVM đích

Nếu command tạo file trong container, **file xuất hiện trong filesystem/namespace của JVM/container đích**; không chắc nằm trên laptop nơi người điều tra gõ lệnh. Dùng đường dẫn tuyệt đối/quyền truy cập đã xác minh, tránh volume critical và kiểm tra dung lượng trước.

## 15. Heap dump lớn nhưng CPU không phải vấn đề duy nhất

Trước thao tác High phải ước lượng:

- **Pauses:** ứng dụng có thể ngưng hoặc bị ảnh hưởng đủ lâu để timeout.
- **CPU:** duyệt objects/GC/serialization.
- **I/O:** một lượng bytes lớn ghi ra storage.
- **Filesystem capacity:** nếu file đầy đĩa, cả service khác có thể bị ảnh hưởng.
- **Memory pressure:** VM/internal buffers và page cache effects.
- **Incident blast radius:** một replica vs cả cluster.
- **Security:** HPROF có dữ liệu nhạy cảm.

### 15.1. Ước lượng thời gian I/O tối thiểu — ví dụ đơn giản

Nếu một file được ước lượng **2 GiB** và tốc độ ghi thực tế chỉ **100 MiB/s**, riêng việc ghi tuần tự lý tưởng đã cần khoảng:

```text
2 GiB = 2048 MiB
2048 MiB / 100 MiB/s = 20.48 seconds
```

Đây **không phải thời gian ngừng JVM được tiên đoán**: còn heap traversal/GC, buffering, compression và contention. Thực tế có thể chậm hơn hoặc một phần xử lý/chờ xen kẽ; hãy dùng số này để thấy mức độ rủi ro của output lớn, không cam kết pause=20,48 giây.

### 15.2. Compression cũng tiêu tốn CPU

Một số JDK releases mới cung cấp options compress heap dump; đừng mặc định JDK 17 có cùng options hay semantics. Trong mọi trường hợp, giảm file bytes thường đổi lấy CPU time và có thể tăng pause. Đối chiếu `jcmd <pid> help GC.heap_dump` đúng VM.

### 15.3. Có thể chụp một replica thay vì toàn cluster

Với dịch vụ có nhiều replicas và rollout phù hợp, **drain hoặc tách một replica** để lấy heap dump có thể giảm ảnh hưởng người dùng. Cần đảm bảo replica đại diện cho vấn đề (có cùng leak/traffic/state), không chụp một pod rảnh rồi kết luận toàn service không lỗi.

## 16. Khi nào chỉ cần metrics/logs có sẵn?

Trước attach diagnostics, kiểm tra những nguồn đã được bật từ trước:

- GC logs: pause durations/frequency, heap occupancy, collection reasons.
- JMX/Micrometer: heap/non-heap, thread count, loaded classes, compilation time, buffer pools.
- OS/cgroup metrics: CPU throttling, RSS, memory pressure, disk availability, network.
- Application tracing: request latency, pool/queue backlog, error rate.
- Logging: OutOfMemoryError, Full GC, blocked requests, deadlock warnings.

Nếu câu hỏi “CPU usage có tăng trước latency không?” đã có metrics giải đáp, một heap dump có thể chỉ tăng rủi ro. **Không tạo dữ liệu mới khi dữ liệu hiện có đủ độ phân giải và độ tin cậy.**

### 16.1. Chu kỳ lấy metrics cũng có overhead

Sampling quá dày, tags/cardinality quá lớn và logs quá verbose có thể tự tạo CPU/I/O pressure. Mức “Low” không nghĩa có thể poll mỗi 1 ms trên toàn cluster mà không đánh giá chi phí.

## 17. Chọn mức độ theo SLO và mức độ khẩn cấp

Một ma trận đơn giản:

| Production state | Tác động có thể chấp nhận | Nhóm thao tác |
| --- | --- | --- |
| Bình thường, cần baseline | Gần không gián đoạn | Existing metrics, `VM.uptime`/`VM.flags`, JFR default có giới hạn |
| Latency tăng, nhiều threads chờ | Có thể chấp nhận snapshot ngắn | `Thread.print`, targeted JFR |
| Native memory tăng không giải thích được | Cho phép thêm CPU/metadata collection | NMT summary nếu đã bật, `VM.metaspace`/OS |
| Heap sắp OOME, cần top object classes | Chấp nhận scan heap có rủi ro | `GC.class_histogram` có approval |
| OOME lặp, cần retention path/ownership | Chấp nhận lớn pause/disk | `GC.heap_dump` trên replica đã chuẩn bị |
| Disk gần đầy, traffic cực nhạy | Gần zero extra I/O | Tránh heap dump/JFR unlimited; dùng metrics và plan chuyển dữ liệu |

**SLO là ràng buộc, không chỉ là độ ưu tiên của người điều tra.** Nếu service không chịu được 5 giây gián đoạn, đừng chọn dump chỉ vì operator có quyền chạy lệnh.

### 17.1. Phê duyệt operation High

Nên có thông tin trước khi chạy: symptom/hypothesis, JVM PID, JDK/collector, operation, expected pause class, output path/quota, data sensitivity, affected replicas, rollback/fallback, người chịu trách nhiệm và thời gian giám sát.

### 17.2. Nếu incident quá khẩn

Khi ứng dụng sắp crash và bằng chứng sắp mất, có thể chấp nhận thao tác High nhanh hơn. Nhưng đó vẫn là quyết định **có ghi nhận rủi ro**, không phải mặc định luôn có thể dump không ảnh hưởng.

## 18. Quy trình thực hành A — CPU spike

**Mục tiêu:** tìm method/stack thường chiếm CPU.

1. Xác định JVM PID/JDK và current CPU/latency.
2. Xem OS CPU per-thread nếu có; đối chiếu thread IDs với tools.
3. Kiểm tra GC logs để xem CPU do GC hay application.
4. Nếu chưa rõ, ghi **JFR default** 30–60 giây giới hạn output.
5. Khi cần sâu hơn, dùng `settings=profile` trong một window có giám sát.
6. Phân tích offline trong JDK Mission Control theo execution samples, threads, locks và GC events.
7. Nếu dump cần thiết cho blocking riêng lẻ, thu một số `Thread.print` có khoảng cách, không spam.
8. Xác minh hot code bằng profiling/tracing, không kết luận chỉ từ một stack snapshot.

**Không bắt đầu bằng heap dump cho CPU spike** trừ khi đã có bằng chứng heap pressure/GC là nguyên nhân chính.

## 19. Quy trình thực hành B — Threads bị treo hoặc deadlock

**Mục tiêu:** xác định blocked/waiting states, monitors, ownable synchronizers và stack progress.

```bash
jcmd <pid> Thread.print -l > threads-1.txt
```

Sau một khoảng được chọn theo SLO:

```bash
jcmd <pid> Thread.print -l > threads-2.txt
```

So sánh:

- Threads nào vẫn giữ cùng stack và lock information?
- Có chain monitor owner/waiter?
- Có ngoại vi I/O đang đợi (socket/read/DB)?
- Thread pool có backlog hay starvation?
- JVM có báo deadlock detection không?

**Thread dump Medium không có nghĩa zero safepoint**, và số threads nhiều có thể tạo file đáng kể. Điều tra cần cân bằng với latency impact; không tạo hàng nghìn snapshots theo vòng lặp tự động.

## 20. Quy trình thực hành C — Nghi memory leak

**Mục tiêu:** phân biệt heap vs native, transient allocations vs retained live objects.

1. Kiểm tra heap occupancy sau các GC cycles, Metaspace, direct buffer pools và process RSS.
2. Nếu native memory tăng, kiểm tra NMT summary (phải được bật từ startup).
3. Nếu heap live set tăng bền vững, dùng allocation/JFR observations khi đủ.
4. Nếu cần top object classes, cân nhắc histogram **High** với window đã chuẩn bị.
5. Nếu cần paths to GC roots, cân nhắc **heap dump High** với disk/security/SLO controls.
6. Dùng MAT/JDK Mission Control hoặc công cụ phù hợp để phân tích offline.
7. Đưa ra giả thuyết object ownership/class loader retention và kiểm chứng sau fix.

### 20.1. Histogram khác heap dump

Histogram cho biết **class counts/bytes**, không cho object graph đầy đủ để tìm strong reference chains. Heap dump có retention graph nhưng đắt hơn và chứa nhiều dữ liệu nhạy cảm.

### 20.2. Heap pressure chưa đủ chứng minh leak

GC chưa chạy, cache có chủ ý, tăng load hoặc lazy init có thể làm usage tăng. Phải đánh giá sau chu kỳ hoạt động đại diện; tránh chạy Full GC ép chỉ để cho đẹp biểu đồ nếu nó làm mất bằng chứng.

## 21. Quy trình thực hành D — Native RSS tăng

**Mục tiêu:** tìm native memory subsystem thay vì chỉ nhìn Java heap.

Nguồn có thể bao gồm:

- Metaspace/class metadata.
- Code cache.
- NIO direct buffers.
- Java thread stacks.
- GC/Compiler/Internal arenas.
- JNI/third-party native allocators.
- File-backed memory mappings/shared libraries.

Nếu NMT enabled:

```bash
jcmd <pid> VM.native_memory summary scale=MB
```

Nếu chưa NMT:

- Xem OS RSS/PSS/maps và container metrics.
- Đọc BufferPoolMXBean direct/mapped.
- Đọc Metaspace/code cache/threads metrics.
- Cân nhắc kế hoạch bật NMT ở restart tiếp theo.

**Không cộng tất cả reserved addresses như thể chúng là committed RAM** và không đòi NMT total bằng OS RSS.

## 22. Dự toán dung lượng dữ liệu: bảng giúp chuẩn bị storage

Các con số dưới đây là **ví dụ ước lượng cách tính**, không phải size fixed theo JDK:

| Loại | Thành phần quyết định size | Cách chuẩn bị |
| --- | --- | --- |
| `VM.flags`/`VM.uptime` | Số flags, bytes text | Thường rất nhỏ, redaction args |
| Thread dump | Number of threads × frames × chars | Ở JVM nhiều threads có thể vài MB hoặc hơn |
| JFR default | Event rate × duration × payload | Giới hạn duration/maxsize/maxage, volume |
| JFR profile | Stack/sample event rate × duration | Ngân sách lớn hơn default, theo dõi CPU |
| NMT summary | Number of native subsystems | Nhỏ hơn detail trong nhiều trường hợp |
| NMT detail | Call-site/region counts | Có thể dài, thêm tracking overhead |
| Class histogram | Number of classes + rows | Text từ KB tới MB+, scan vẫn High |
| HPROF heap dump | Object graph + metadata/headers | Có thể hàng trăm MiB tới nhiều GiB |
| Core dump (OS) | Process mappings / OS policy | Rất lớn, dữ liệu cực nhạy cảm |

### 22.1. Ước lượng JFR bằng tốc độ sinh dữ liệu

Nếu một JFR test 2 phút tạo 24 MiB, trung bình thô là **0,2 MiB/s**. Nếu giữ nguyên event configuration và workload, 10 phút có thể cỡ **120 MiB**. Nhưng event rate có thể tăng mạnh dưới spike; **đừng dùng phép nhân làm guarantee**.

### 22.2. Đừng chỉ dựa vào free disk của host

Nếu JVM ở container có filesystem quota/ephemeral storage limit, dù host còn nhiều free space, pod vẫn có thể eviction/out-of-disk. Cần xác nhận **đường đích, PVC/quota, access controls và retention**.

### 22.3. Thời gian chuyển dữ liệu cũng là chi phí

Chuyển HPROF 5 GiB sang nơi phân tích có thể tốn network bandwidth và kéo dài incident. Có thể dùng encrypted transfer/approved storage, không upload vào kênh không kiểm soát.

## 23. Demo Java 17+ độc lập: JVM mục tiêu cho các lệnh nhẹ

Tạo file **`DiagnosticsDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;

public final class DiagnosticsDemo {
    private static final CountDownLatch ready =
            new CountDownLatch(2);
    private static final CountDownLatch release =
            new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        Thread one = new Thread(
                DiagnosticsDemo::waitForRelease,
                "diagnostic-demo-A");
        Thread two = new Thread(
                DiagnosticsDemo::waitForRelease,
                "diagnostic-demo-B");
        one.start();
        two.start();
        ready.await();

        System.out.println("demo=ready");
        System.out.println("workers=2");

        if (args.length > 0 && args[0].equals("hold")) {
            System.out.println(
                    "pid=" + ProcessHandle.current().pid());
            Thread.sleep(13000);
        }

        release.countDown();
        one.join();
        two.join();
        System.out.println("demo=finished");
    }

    private static void waitForRelease() {
        ready.countDown();
        try {
            release.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 23.1. Biên dịch và chạy không giữ process

```bash
javac --release 17 DiagnosticsDemo.java
java DiagnosticsDemo
```

**Stdout chính xác** khi hoàn tất bình thường:

```text
demo=ready
workers=2
demo=finished
```

### 23.2. Chạy chế độ giữ process để attach

```bash
java -XX:NativeMemoryTracking=summary DiagnosticsDemo hold
```

Đầu ra của process:

```text
demo=ready
workers=2
pid=<actual JVM process id>
```

`<actual JVM process id>` là ký hiệu cho PID thật, không phải dòng cố định. Khi 13 giây sleep kết thúc, chương trình thả hai worker threads và in:

```text
demo=finished
```

### 23.3. Lệnh nhẹ để kiểm chứng

Trong terminal khác, trước khi 13 giây kết thúc:

```bash
jcmd <pid> VM.uptime
jcmd <pid> Compiler.codecache
jcmd <pid> VM.native_memory summary scale=MB
jcmd <pid> Thread.print
```

Trong `Thread.print` tìm:

```text
"diagnostic-demo-A"
"diagnostic-demo-B"
```

Thực tế có thêm IDs, OS thread identifiers, states và stack frames; **không kỳ vọng exact full output**.

### 23.4. JFR recording ngắn

```bash
jcmd <pid> JFR.start name=ShortDiag settings=default duration=4s filename=short-diag.jfr
```

Sau khoảng recording duration, file `short-diag.jfr` được tạo ở nơi JVM ghi được. Trong một lần chạy kiểm chứng trên **HotSpot OpenJDK 24.0.2**, file này có **248.438 bytes**. **Con số chỉ là sample cục bộ**, không phải dung lượng chuẩn của JFR 4 giây.

### 23.5. Điều đã được kiểm chứng

- `javac --release 17 DiagnosticsDemo.java` **biên dịch thành công**.
- Chạy không `hold` cho đúng ba dòng stdout.
- Chạy `hold` với NMT summary bật từ startup.
- `jcmd VM.uptime`, `Compiler.codecache`, `VM.native_memory summary` chạy thành công.
- `Thread.print` cho thấy hai threads `diagnostic-demo-A/B`.
- `JFR.start settings=default duration=4s` tạo recording file thành công.
- **Không chạy heap dump/class histogram/GC.run** trong thử nghiệm an toàn này.

Đây là **mã Java 17-compatible** được chạy thực tế trên **JDK 24.0.2**, không khẳng định các diagnostics đã được thử trên một binary JDK 17 cụ thể. Tài liệu JDK 17 được dùng để xác minh cú pháp và impact categories.

## 24. Những giới hạn của thí nghiệm nhỏ

Một JVM mới, hai worker threads và heap nhỏ **không phản ánh** rủi ro của service có:

- Hàng chục GiB heap.
- Hàng nghìn threads và nhiều stack frames.
- JFR profile events mật độ cao.
- Limited CPU quota.
- Slow/shared disks.
- Heap chứa thông tin khách hàng.
- Latency SLO vài milliseconds.

Một lệnh nhanh trên demo **không phải bằng chứng lệnh đó an toàn tuyệt đối trên production**.

### 24.1. Không thử Full GC trên production để học công cụ

Nếu cần tìm hiểu tác động, thực hiện trên môi trường test có workload mô phỏng. Một forced full GC có thể che đi leak symptom, ảnh hưởng latency và làm ứng dụng mất khả năng phục vụ request trong window không mong muốn.

### 24.2. Dùng dry run để kiểm tra đường dẫn

Trước heap dump, thử ghi **một file nhỏ không chứa dữ liệu nhạy cảm** vào target volume trong sandbox/staging phù hợp, kiểm tra permission/quota. Đừng tạo HPROF thật chỉ để kiểm tra đường dẫn có ghi được.

## 25. Bảo mật và vòng đời artifacts

Mỗi đầu ra có mức độ nhạy cảm khác nhau:

| Artifact | Có thể chứa | Cách kiểm soát |
| --- | --- | --- |
| JVM flags/command line | Args, paths, có thể secrets | Redact trước khi chia sẻ |
| Thread dumps | Stack frames, class names, request contexts | Giới hạn quyền xem và thời gian lưu |
| JFR | Stacktraces, event contexts, môi trường | Mã hóa/lưu theo policy |
| Class histogram | Tên classes, số lượng objects | Không public nội bộ service structure |
| Heap dump | Object graphs, credentials/tokens/payloads | Mức bảo vệ cao nhất, approval/audit/retention |
| Core dump | Native/heap/process memory | Cực nhạy cảm, được xử lý như secret-bearing artifact |

### 25.1. Hạn chế bản sao

Tránh copy dump qua nhiều laptops/buckets/tickets vì khó xóa hết. Ưu tiên một approved artifact repository có access controls, expiry/retention và audit trail.

### 25.2. Dọn dẹp cần an toàn

Đảm bảo artifact đã được phân tích/lưu đúng policy trước khi xóa; chỉ xóa **đúng file do incident tạo**. Không chạy lệnh xóa wildcard toàn thư mục shared diagnostics.

## 26. Checklist trước thao tác tác động cao

1. Đã xác định đúng service, replica, PID và JDK version?
2. Đã lấy metrics/logs ít xâm lấn và nêu giả thuyết chưa?
3. Command có **Impact Low/Medium/High** gì theo JDK đích?
4. Lệnh có thể Full GC, STW, safepoint hoặc nhiều CPU work không?
5. Tải hiện tại và p95/p99 latency budget cho phép không?
6. Heap/object/thread count có thể khiến thời gian tăng mạnh không?
7. Output file có thể lớn nhất cỡ nào? Dùng loại artifact gì?
8. Target volume còn đủ dung lượng và throughput?
9. Container/PVC/ephemeral storage quota có cho phép?
10. Có replica/traffic drain hoặc maintenance window?
11. File có chứa secrets/PII/credentials hay không?
12. Người phê duyệt và người theo dõi đã được xác định?
13. Có stop/escalation criteria nếu disk/latency/CPU xấu đi?
14. Có rollback/phương án chuyển traffic?
15. Đã xác minh permission/attach path đúng?
16. Có cần kiểm tra `help <command>` cho đúng JDK build?
17. Đã đặt duration/maxsize/maxage nếu sử dụng JFR?
18. Có nơi phân tích offline đủ RAM/disk?
19. Có chính sách retention, transfer, deletion và audit?
20. Lệnh có thật sự cần thiết để trả lời câu hỏi incident không?

## 27. Những sai lầm thường gặp

| Nhận định sai | Điều cần hiểu |
| --- | --- |
| “jcmd luôn không dừng JVM.” | Một số commands High/Medium có thể gây pause/CPU work. |
| “Impact Low nghĩa zero impact.” | Không, đây là nhãn phân loại, không phải SLA. |
| “Thread dump không bao giờ ảnh hưởng latency.” | Tác động Medium tùy thread count và stack walk. |
| “Một thread dump chứng minh deadlock mãi mãi.” | Chỉ một snapshot; cần đối chiếu context/tiến triển. |
| “Heap histogram không ảnh hưởng app vì chỉ in text.” | High-impact heap scanning dù output text. |
| “Heap dump chỉ tốn dung lượng đĩa, không tốn CPU.” | Quét heap/GC/serialization cũng tốn CPU/pause. |
| “Heap dump mặc định không GC.” | JDK17 `GC.heap_dump` yêu cầu Full GC trừ khi `-all`. |
| “-all làm heap dump an toàn tuyệt đối.” | Vẫn High, có thể ghi file còn lớn hơn. |
| “JFR.start Low nghĩa mọi JFR configuration Low.” | Recording settings/heap stats/GC roots có thể tăng overhead. |
| “JFR.dump sẽ stop recording.” | JDK17 `JFR.dump` ghi ra file, recording tiếp tục. |
| “NMT summary đo cả JNI/native third-party allocations.” | NMT scope có giới hạn, không bằng RSS. |
| “NMT có thể bật để lấy lịch sử process đã chạy mà chưa cấu hình.” | Thông thường phải bật từ startup. |
| “GC.run chỉ đọc thông tin GC.” | Nó yêu cầu `System.gc()`, thay runtime behavior. |
| “Số dòng class histogram nhỏ nghĩa CPU scan thấp.” | CPU scan phụ thuộc heap objects, không chỉ rows output. |
| “-Xmx=2g nghĩa HPROF luôn đúng 2 GiB.” | HPROF size khác heap maximum/used. |
| “JFR 4 giây luôn ~248 KB.” | File size phụ thuộc events/workload/config. |
| “Đưa dump lên public ticket không sao.” | Có thể chứa credentials/PII/business payloads. |
| “Không có file ở local thì dump thất bại.” | File có thể được JVM tạo trong container/host namespace khác. |
| “Một lệnh an toàn trên laptop thì an toàn trên pod 1 CPU.” | Resource quotas/tải/heap size khác. |
| “Thread.dump_to_file dùng được trên mọi JDK17.” | Kiểm tra `jcmd help` theo đúng JDK release. |
| “Mỗi lệnh chỉ cần chạy một lần rồi không cần QA.” | Phải kiểm tra file và tính đầy đủ sau thu thập. |
| “Ghi HPROF ở ổ gần đầy không ảnh hưởng application.” | Có thể gây I/O contention, disk exhaustion, eviction. |

## 28. Câu hỏi tự kiểm tra và đáp án

**1.** Ba trục chi phí khi chọn JVM diagnostic operation là gì?

**Đáp án:** Tác động dừng/pause, CPU/memory/I/O, và kích thước artifact/output sinh ra.

**2.** `VM.uptime` thuộc impact gì theo Oracle JDK17?

**Đáp án:** Low.

**3.** `Thread.print` thuộc impact gì?

**Đáp án:** Medium, phụ thuộc thread count.

**4.** `GC.class_histogram` thuộc impact gì?

**Đáp án:** High, phụ thuộc heap size/content.

**5.** `GC.heap_dump` mặc định có yêu cầu Full GC không?

**Đáp án:** Có theo mô tả Oracle JDK17, trừ khi đặt `-all`.

**6.** `GC.heap_dump -all` có tác động Low không?

**Đáp án:** Không. Vẫn High và xuất cả unreachable objects.

**7.** `GC.heap_info` có xuất object graph đầy đủ không?

**Đáp án:** Không, chỉ thông tin tổng quát; impact Medium.

**8.** `JFR.start` có impact nhãn gì?

**Đáp án:** Low trong tài liệu lệnh, nhưng workload/template/events quyết định overhead thực tế.

**9.** `JFR.dump` có chấm dứt recording không?

**Đáp án:** Không, theo JDK17 recording tiếp tục sau khi ghi file; dùng `JFR.stop` để kết thúc.

**10.** Vì sao không bật path-to-gc-root mặc định?

**Đáp án:** Thu thập paths có thể tốn thời gian, gây pause; phù hợp khi nghi memory leak cụ thể.

**11.** NMT phải được cấu hình khi nào?

**Đáp án:** Thường từ JVM startup bằng `-XX:NativeMemoryTracking=summary` hoặc `detail`.

**12.** NMT có hoàn toàn bằng OS RSS không?

**Đáp án:** Không; NMT không theo dõi mọi native allocations hay OS resident/shared pages.

**13.** Khi CPU spike, nên chụp heap dump ngay không?

**Đáp án:** Không, ưu tiên CPU/GC metrics và JFR profile có giới hạn.

**14.** Khi nghi deadlock, nên bắt đầu từ đâu?

**Đáp án:** `Thread.print -l`/thread metrics, xem lock chains và stack progress, cân nhắc vài snapshots.

**15.** Heap histogram khác heap dump ở đâu?

**Đáp án:** Histogram đếm instances/bytes theo class; heap dump có object graph/retention information, thường tốn disk/time hơn.

**16.** Dung lượng thread dump tăng theo gì?

**Đáp án:** Chủ yếu theo số threads, stack frames, độ dài text và lock details.

**17.** Dùng số thread dumps càng nhiều càng tốt không?

**Đáp án:** Không; cần khoảng cách theo SLO, tránh tạo overhead và output thừa.

**18.** Có thể coi `-Xmx` bằng dung lượng HPROF không?

**Đáp án:** Không; file size còn phụ thuộc live/total objects, headers, metadata.

**19.** Tại sao một heap dump có thể gây thêm incident?

**Đáp án:** STW/GC/CPU spikes, disk exhaustion, I/O contention và rò rỉ dữ liệu nhạy cảm.

**20.** Nếu JVM chạy trong container, cần quan tâm đường dẫn file ở đâu?

**Đáp án:** Filesystem/volume namespace của JVM đích, không chắc thư mục local operator.

**21.** Vì sao JFR default thường được ưu tiên so với profile chạy lâu?

**Đáp án:** Ít event overhead hơn, dễ giới hạn duration/size và thường đủ định hướng ban đầu.

**22.** `jcmd <pid> help` giúp gì?

**Đáp án:** Xem commands và options thực sự có trên JDK/JVM đích, tránh dùng syntax của release khác.

**23.** Impact Medium có quy đổi thành chính xác dưới 1 giây không?

**Đáp án:** Không, không có thời gian tuyệt đối portable từ nhãn impact.

**24.** `GC.run` có phải lệnh đọc trạng thái GC không?

**Đáp án:** Không; nó yêu cầu `System.gc()`, có thể thay đổi runtime state.

**25.** Khi cần HPROF của production, nên chọn replica thế nào?

**Đáp án:** Ưu tiên một replica đại diện được drain/cô lập nếu architecture cho phép, chuẩn bị disk/security/SLO/approval.

**26.** Tại sao output file nhỏ không chứng minh thao tác nhẹ?

**Đáp án:** Heap histogram có thể quét lượng lớn objects nhưng chỉ in số liệu tổng hợp theo classes.

## 29. Tóm tắt quyết định

```text
BẮT ĐẦU:
  Triệu chứng + SLO + JVM PID/JDK
       |
       v
  Metrics / GC logs / VM.flags / VM.uptime
       |                   (usually low)
       v
  JFR default (bounded) / Thread.print (medium)
       |
       v
  Native? -> NMT summary if already enabled
  CPU?    -> JFR profile bounded and monitored
  Thread? -> 2-3 thread snapshots if needed
  Heap?   -> GC.heap_info / histogram with approval
       |
       v
  Still need retained object graph?
       |
       v
  Explicit HIGH-impact approval
  Heap dump -> secure volume -> offline analysis
       |
       v
  Remove artifacts safely according to retention

Không đánh giá chỉ một trục:
  * Safepoint / STW
  * CPU / GC / I/O
  * File bytes / capacity / sensitivity
```

**Kết luận:** Chọn thao tác chẩn đoán JVM theo **câu hỏi cần trả lời và ngân sách rủi ro**, không theo thói quen “càng nhiều dữ liệu càng tốt”. Bắt đầu bằng metrics/logs và lệnh Low, dùng thread dumps/JFR/NMT khi cần, chỉ nâng lên histogram/heap dump High sau khi xác định phạm vi pause/CPU/disk và bảo mật. Luôn đọc **impact label của JDK đang chạy**, nhưng kiểm chứng impact thực tế trên workload của chính ứng dụng.

### Tài liệu chính thức

- [Oracle JDK 17 — jcmd command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): lệnh, options, phân loại Impact Low/Medium/High, `GC.heap_dump`, `GC.class_histogram`, `Thread.print`, `JFR.start`/`JFR.dump`, `VM.native_memory`.
- [Oracle JDK 17 — Troubleshooting Guide](https://docs.oracle.com/en/java/javase/17/troubleshoot/): xác định triệu chứng, JFR, memory leaks và diagnostic tooling.
- [Oracle JDK 17 — JFR](https://docs.oracle.com/en/java/javase/17/jfapi/flight-recorder.html): Java Flight Recorder, các lựa chọn profiling/recording.
- [Oracle JDK 17 — Native Memory Tracking](https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html): phạm vi NMT, summary/detail và overhead.
- [Oracle JDK 17 — java command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): options liên quan JFR, GC logs, heap dumps khi OOME.
- [OpenJDK JFR (JEP 328)](https://openjdk.org/jeps/328): kiến trúc low-overhead JFR tích hợp trong JDK.
- [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html): phân tích JFR recordings.


</details>

- [Quay lại đầu trang](#back-to-top)
