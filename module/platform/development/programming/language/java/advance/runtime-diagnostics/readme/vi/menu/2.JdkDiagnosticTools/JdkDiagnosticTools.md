<a id="back-to-top"></a>

# Nguồn dữ liệu và công cụ chẩn đoán JDK

## Menu
- [Xác định đúng JVM và tiến trình cần chẩn đoán](#target-jvm-identification)
- [Ưu tiên jcmd trong chẩn đoán JDK](#jcmd-first-diagnostics)
- [jps, jstack, jmap, jinfo và jstat nằm ở đâu trong bộ công cụ?](#specialized-jdk-utilities)
- [Chọn công cụ theo câu hỏi chẩn đoán](#diagnostic-tool-selection)
- [Chẩn đoán trực tiếp, thu thập artifact và phân tích hậu kiểm](#live-vs-offline-evidence)
- [Chuẩn bị logs, dumps, recordings và dữ liệu sự cố](#diagnostic-artifact-readiness)
- [Quyền truy cập, tác động runtime và thời điểm thu thập](#diagnostic-permissions-and-impact)

## <a id="target-jvm-identification">Xác định đúng JVM và tiến trình cần chẩn đoán</a>

<details>
<summary>Click for details</summary>

Bước đầu tiên của mọi thao tác chẩn đoán là chắc chắn bạn đang nhìn **đúng JVM**. Một host có thể chạy nhiều tiến trình Java, một container có thể có PID namespace riêng, và tên main class/JAR đôi khi giống nhau giữa nhiều instance.

Có thể bắt đầu bằng:

```bash
jcmd -l
```

hoặc:

```bash
jps -lv
```

`jcmd -l` liệt kê các JVM mà công cụ có thể nhìn thấy cùng main class và thông tin command line. `jps` cung cấp cách liệt kê tương tự cho HotSpot JVM. Tuy nhiên, không nên xem danh sách này là nguồn nhận dạng duy nhất. Oracle lưu ý rằng JVM nằm trong một Docker process/namespace riêng có thể không xuất hiện theo cách bạn mong đợi; khi đó cần kết hợp công cụ hệ điều hành/container để tìm đúng PID.

Sau khi có PID nghi ngờ, hãy kiểm tra định danh của JVM đích:

```bash
jcmd 12345 VM.version
jcmd 12345 VM.command_line
jcmd 12345 VM.flags
jcmd 12345 VM.system_properties
```

Các câu hỏi cần trả lời trước khi thu thập dữ liệu nặng:

- đây có đúng instance/service không;
- JDK version/vendor nào đang chạy;
- startup command và JVM flags có đúng với deployment bạn đang điều tra không;
- PID đang dùng là PID của host hay PID trong container;
- tiến trình đã restart chưa, vì restart có thể đổi PID và làm mất trạng thái sự cố.

Trong môi trường orchestrated, nên liên kết PID với **service instance + container/pod + host + thời điểm**. Một file tên `thread-dump-12345.txt` thiếu timestamp và định danh instance rất dễ bị phân tích nhầm sau sự cố.

Ví dụ naming tốt hơn:

```text
orders-api_pod-7f9c_pid-12345_2026-10-02T08-15-30Z_threads.txt
```

Nhận diện đúng JVM đích là bước có chi phí gần như bằng không nhưng có giá trị rất lớn: toàn bộ phân tích sau đó đều vô nghĩa nếu dữ liệu đến từ sai JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jcmd-first-diagnostics">Ưu tiên jcmd trong chẩn đoán JDK</a>

<details>
<summary>Click for details</summary>

`jcmd` là command-line interface chung để gửi **diagnostic commands** tới một JVM đang chạy. Với JDK hiện đại, Oracle khuyến nghị ưu tiên `jcmd` thay cho các utility cũ như `jstack`, `jmap` và `jinfo` khi `jcmd` có command tương đương.

Quy trình cơ bản:

```bash
# Liệt kê JVM
jcmd -l

# Xem command nào JVM mục tiêu hỗ trợ
jcmd 12345 help

# Xem cú pháp/tác động của một lệnh cụ thể
jcmd 12345 help Thread.print

# Một số câu hỏi phổ biến
jcmd 12345 VM.version
jcmd 12345 VM.command_line
jcmd 12345 VM.flags
jcmd 12345 VM.system_properties
jcmd 12345 GC.heap_info
jcmd 12345 Thread.print -l
```

Danh sách lệnh có thể phụ thuộc JVM/build/version, vì vậy `jcmd <pid> help` trên **target thật** đáng tin cậy hơn việc nhớ một danh sách cố định.

`jcmd` cũng là điểm vào cho nhiều dữ liệu chẩn đoán quan trọng:

```bash
# Class histogram
jcmd 12345 GC.class_histogram

# Heap dump HPROF
jcmd 12345 GC.heap_dump /diagnostics/heap.hprof

# Thread dump truyền thống cho platform threads
jcmd 12345 Thread.print -l

# Thread dump có hỗ trợ virtual threads trong Java 21
jcmd 12345 Thread.dump_to_file -format=json /diagnostics/threads.json
```

Điểm cần lưu ý là “một công cụ” không có nghĩa “mọi lệnh đều nhẹ”. Oracle đánh dấu `VM.flags` là low impact, `Thread.print` là medium impact phụ thuộc số thread, còn `GC.class_histogram` và `GC.heap_dump` có thể high impact phụ thuộc kích thước/nội dung heap.

Vì vậy cách dùng đúng là:

```text
câu hỏi chẩn đoán
    ↓
jcmd <pid> help
    ↓
chọn command có đủ bằng chứng
    ↓
đọc mức tác động + chuẩn bị đầu ra
    ↓
thu thập
```

`jcmd` là giao diện điều khiển thuận tiện; nó không thay thế việc hiểu dữ liệu mà mỗi lệnh tạo ra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="specialized-jdk-utilities">jps, jstack, jmap, jinfo và jstat nằm ở đâu trong bộ công cụ?</a>

<details>
<summary>Click for details</summary>

Các utility chuyên biệt vẫn hữu ích vì bạn sẽ gặp chúng trong runbook, tài liệu cũ hoặc những tình huống mà đầu ra của chúng thuận tiện. Điều quan trọng là biết **mỗi công cụ trả lời loại câu hỏi nào**.

| Công cụ | Vai trò chính | Ví dụ |
| --- | --- | --- |
| `jps` | Liệt kê HotSpot JVM có thể quan sát trên máy/local VM namespace | `jps -lv` |
| `jstack` | In Java stack trace của thread; `-l` thêm thông tin lock | `jstack -l 12345` |
| `jmap` | Heap histogram, heap dump và một số heap/class-loader statistics | `jmap -histo 12345` |
| `jinfo` | Xem system properties và VM flags; một số flag có thể điều chỉnh tùy JVM | `jinfo -flags 12345` |
| `jstat` | Lấy mẫu HotSpot performance counters theo thời gian | `jstat -gcutil 12345 1000 10` |

Oracle đánh dấu `jstack`, `jmap` và `jinfo` là experimental/unsupported trong JDK 21 tool documentation và khuyến nghị `jcmd` cho các thao tác tương đương. Vì vậy, ví dụ:

```text
jstack -l <pid>
≈ khi phù hợp, ưu tiên jcmd <pid> Thread.print -l

jmap -histo <pid>
≈ khi phù hợp, ưu tiên jcmd <pid> GC.class_histogram

jmap -dump:format=b,file=heap.hprof <pid>
≈ khi phù hợp, ưu tiên jcmd <pid> GC.heap_dump heap.hprof
```

`jstat` có vai trò hơi khác. Nó đọc HotSpot instrumentation counters và rất hữu ích để quan sát **xu hướng**:

```bash
jstat -gcutil 12345 1000 10
```

Lệnh trên lấy 10 mẫu, mỗi mẫu cách 1 giây. Các cột như số GC, thời gian GC và mức sử dụng các vùng heap giúp bạn thấy hành vi thay đổi theo thời gian. Tuy nhiên các counter mang tính triển khai HotSpot; không nên biến một cột `jstat` thành kết luận nguyên nhân gốc nếu chưa liên hệ với collector, log và triệu chứng.

`jps` cũng cần dùng thận trọng: LVMID thường trùng OS PID nhưng không nên xây tự động hóa dựa trên giả định rằng mọi môi trường/container đều có cùng khả năng nhìn thấy tiến trình.

Hãy xem các utility này như những cửa sổ chuyên biệt vào JVM, với `jcmd` là giao diện tổng quát được ưu tiên cho chẩn đoán HotSpot hiện đại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-tool-selection">Chọn công cụ theo câu hỏi chẩn đoán</a>

<details>
<summary>Click for details</summary>

Chọn công cụ nên bắt đầu từ **câu hỏi**, không bắt đầu từ tên lệnh.

| Câu hỏi | Bằng chứng đầu tiên nên cân nhắc |
| --- | --- |
| JVM nào đang chạy và chạy với option gì? | `jcmd -l`, `VM.version`, `VM.command_line`, `VM.flags` |
| Ứng dụng treo hoặc nhiều request không tiến triển? | `Thread.print` nhiều lần; virtual-thread-aware dump khi cần |
| Có dấu hiệu deadlock/lock contention? | thread dump với thông tin lock, sau đó đối chiếu nhiều ảnh chụp |
| Heap đang chứa loại object nào nhiều? | `GC.class_histogram` |
| Cần xem object-retention graph chi tiết? | heap dump |
| Hành vi Heap/GC thay đổi theo thời gian? | GC logs, `jstat`, JFR |
| Bộ nhớ tiến trình tăng nhưng heap không giải thích được? | NMT nếu đã bật; chuyển sang chương Native Memory |
| Cần dòng thời gian CPU/allocation/lock/I/O event? | JFR |

Cùng một triệu chứng có thể cần nhiều nguồn. Ví dụ “memory tăng” nên được tách:

```text
RSS/bộ nhớ tiến trình tăng
        ↓
Java heap tăng?
  ├─ có → histogram / heap dump / bằng chứng GC
  └─ không → bằng chứng native memory / bằng chứng OS
```

Một lỗi phổ biến là lấy heap dump chỉ vì thấy bộ nhớ tiến trình cao. Heap dump chỉ mô tả Java heap; nếu mức tăng đến từ cấu trúc native của JVM hoặc thư viện native, dữ liệu đó có thể không trả lời câu hỏi.

Tương tự, một thread dump đơn lẻ không phải profiler. Nó có thể chỉ ra stack đang chạy tại một thời điểm, nhưng nếu câu hỏi là “method nào tiêu tốn CPU trong 20 phút”, sampling/event data như JFR phù hợp hơn.

Quy tắc chọn công cụ:

1. Viết câu hỏi thành một câu có thể trả lời bằng dữ liệu.
2. Xác định cần ảnh chụp hay dòng thời gian.
3. Chọn dữ liệu chẩn đoán nhỏ nhất có đủ thông tin.
4. Kiểm tra tác động và quyền truy cập.
5. Chỉ tăng độ nặng khi bằng chứng hiện tại chưa phân biệt được các giả thuyết.

Đây là lý do module bắt đầu bằng tư duy chẩn đoán trước khi học từng công cụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="live-vs-offline-evidence">Chẩn đoán trực tiếp, thu thập artifact và phân tích hậu kiểm</a>

<details>
<summary>Click for details</summary>

Không phải mọi dữ liệu chẩn đoán đều được phân tích ngay trên JVM đang gặp sự cố. Có ba cách làm thường gặp:

**1. Live diagnostics**

Bạn hỏi trực tiếp JVM đang chạy và nhận đầu ra tức thời:

```bash
jcmd 12345 VM.flags
jcmd 12345 Thread.print -l
jcmd 12345 GC.heap_info
jstat -gcutil 12345 1000 10
```

Ưu điểm là phản ánh trạng thái hiện tại. Nhược điểm là trạng thái có thể đổi nhanh và một số thao tác có tác động đáng kể.

**2. Thu thập dữ liệu để phân tích sau**

Bạn yêu cầu JVM ghi dữ liệu ra file:

```bash
jcmd 12345 GC.heap_dump /diagnostics/heap.hprof
jcmd 12345 Thread.dump_to_file -format=json /diagnostics/threads.json
jcmd 12345 JFR.dump filename=/diagnostics/incident.jfr
```

Dữ liệu có thể được chuyển sang máy phân tích, lưu cùng ticket sự cố hoặc so sánh về sau. Cần bảo vệ vì heap dump/recording có thể chứa dữ liệu ứng dụng nhạy cảm.

**3. Bằng chứng postmortem**

Khi JVM đã dừng, các lệnh chẩn đoán trực tiếp không còn dùng được. Khi đó phải dựa vào dữ liệu đã tồn tại: fatal error log, core dump, heap dump, GC/application logs và JFR recording. Quy trình này được đi sâu ở chương Crash/Postmortem Diagnostics.

Điểm kết nối giữa ba cách làm là **thời gian**. Nếu sự cố chỉ kéo dài 30 giây, một heap dump lấy 10 phút sau có thể mô tả trạng thái khác hoàn toàn. Hãy ghi timestamp, PID, định danh host/container và khoảng thời gian xảy ra triệu chứng cho mỗi dữ liệu.

Trước khi restart một JVM, hãy hỏi:

```text
Dữ liệu nào chỉ còn tồn tại trong tiến trình hiện tại?
Dữ liệu nào đã được ghi bền vững?
Restart sẽ xóa bằng chứng nào?
```

Trong sự cố thực tế, khả năng bảo toàn bằng chứng thường quan trọng không kém khả năng đọc đầu ra của công cụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-artifact-readiness">Chuẩn bị logs, dumps, recordings và dữ liệu sự cố</a>

<details>
<summary>Click for details</summary>

Mức sẵn sàng chẩn đoán ở cấp công cụ nghĩa là **dữ liệu chẩn đoán có thể được tạo đúng lúc, đúng nơi và còn dùng được sau sự cố**.

Một cấu hình thực tế cần quyết định trước:

- thư mục dành riêng cho diagnostic artifacts;
- dung lượng tối đa và cơ chế dọn dẹp/giữ dữ liệu;
- quyền ghi của tiến trình JVM;
- quy ước filename có service, instance, PID và timestamp;
- GC log rotation;
- nơi lưu heap dump khi `OutOfMemoryError`;
- continuous recording policy nếu sử dụng JFR;
- cách chuyển dữ liệu lớn khỏi host mà không làm nghẽn disk/network trong sự cố.

Ví dụ startup options:

```text
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/myapp/heapdump.hprof
-Xlog:gc*:file=/var/log/myapp/gc.log:time,uptime,level,tags:filecount=5,filesize=20M
```

Đây chỉ là ví dụ hình dạng cấu hình; đường dẫn và chính sách giữ dữ liệu phải phù hợp môi trường. Nếu nhiều JVM cùng ghi vào một tên file cố định, bạn còn phải tránh collision.

Heap dump đặc biệt cần được lên kế hoạch. `GC.heap_dump` được Oracle đánh dấu high impact và file có thể rất lớn. Trước khi chạy, kiểm tra:

```text
disk free
→ đường dẫn đầu ra
→ quyền truy cập
→ có chấp nhận tác động pause/I/O không
→ dữ liệu sẽ được bảo vệ/chuyển đi thế nào
```

GC logs và JFR có giá trị vì chúng tạo **history trước sự cố**, trong khi thread dump/histogram thường được chụp sau khi symptom đã xuất hiện.

Sẵn sàng chẩn đoán không phải bật mọi tùy chọn. Mục tiêu là có tối thiểu bộ bằng chứng phù hợp với dạng lỗi quan trọng của hệ thống và biết chính xác cách thu thập thêm khi cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-permissions-and-impact">Quyền truy cập, tác động runtime và thời điểm thu thập</a>

<details>
<summary>Click for details</summary>

Lệnh chẩn đoán chạy với quyền rất gần tiến trình mục tiêu, nên hai câu hỏi luôn đi cùng nhau: **có được phép attach không** và **attach có an toàn tại thời điểm này không**.

Theo tài liệu JDK 21, `jcmd` phải chạy trên cùng máy với JVM đích và thường cần cùng effective user/group identity với tiến trình đã khởi động JVM. Container, PID namespace, security policy và cách đóng gói JRE/JDK có thể làm thay đổi khả năng nhìn thấy hoặc attach vào process.

Trước khi chạy command:

1. Xác nhận đúng PID/instance.
2. Chạy `jcmd <pid> help <command>` để xem lệnh có được hỗ trợ và mức tác động của nó.
3. Kiểm tra đường dẫn đầu ra và quyền ghi nếu lệnh tạo file.
4. Kiểm tra disk/I/O headroom.
5. Xác định symptom có đang ở thời điểm cần bảo toàn trạng thái không.

Ví dụ về mức tác động trong HotSpot JDK 21:

- `VM.flags`: low impact;
- `Thread.print`: medium, phụ thuộc số thread;
- `GC.class_histogram`: high, phụ thuộc heap size/content;
- `GC.heap_dump`: high, phụ thuộc heap size/content và có thể yêu cầu full GC trừ khi dùng option phù hợp.

Nhãn tác động không phải SLA. “Medium” trên JVM vài chục thread khác với JVM có hàng trăm nghìn thread/virtual thread; “high” trên heap 512 MB khác với heap hàng chục GB.

Không nên gửi command rộng tới nhiều JVM chỉ để tiện. `jcmd 0 <command>` có thể gửi command đến tất cả JVM khả dụng; với thao tác nặng, đây là lựa chọn nguy hiểm về vận hành. Hãy nhắm đúng PID.

Cuối cùng, dữ liệu chẩn đoán có thể chứa dữ liệu nhạy cảm: heap dump có object contents, system properties có thể chứa cấu hình, thread stack có thể chứa tên request/task. Quyền đọc, lưu trữ và chia sẻ dữ liệu chẩn đoán phải tuân theo chính sách của hệ thống như bất kỳ dữ liệu production nào khác.

</details>

- [Quay lại đầu trang](#back-to-top)
