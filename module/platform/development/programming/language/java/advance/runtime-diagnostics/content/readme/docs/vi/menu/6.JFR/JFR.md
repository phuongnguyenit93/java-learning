<a id="back-to-top"></a>

# Chẩn đoán sự kiện và hiệu năng với Java Flight Recorder

## Menu
- [JFR là gì và vì sao chẩn đoán cần dòng sự kiện theo thời gian?](#jfr-diagnostics-purpose)
- [Vòng đời của một flight recording](#recording-lifecycle)
- [Cấu hình recording, thời lượng và overhead](#recording-settings-and-overhead)
- [Mô hình event và cách chọn dữ liệu cần quan sát](#jfr-event-model)
- [Phân tích CPU samples, threads và locks](#cpu-thread-lock-analysis)
- [Phân tích allocation, GC và I/O events](#memory-gc-io-analysis)
- [Continuous recording và điều tra sau sự cố](#continuous-recording-and-postmortem)
- [jfr CLI, JDK Mission Control và ranh giới công cụ](#jfr-cli-and-jmc-boundary)

## <a id="jfr-diagnostics-purpose">JFR là gì và vì sao chẩn đoán cần dòng sự kiện theo thời gian?</a>

<details>
<summary>Click for details</summary>

Thread dump, heap histogram hay NMT summary đều là những ảnh chụp tại một thời điểm. Chúng rất hữu ích nhưng dễ bỏ lỡ vấn đề chỉ xuất hiện trong vài giây hoặc cần hiểu **chuỗi sự kiện trước–trong–sau**.

Java Flight Recorder (JFR) là cơ chế ghi sự kiện tích hợp trong JDK/HotSpot. Nó thu thập dữ liệu runtime và ứng dụng theo dòng thời gian với chi phí được thiết kế đủ thấp cho nhiều kịch bản trên môi trường production.

JFR phù hợp khi cần trả lời:

- CPU spike đến từ chuỗi lời gọi nào?
- lock contention xảy ra trước latency spike ra sao?
- allocation tăng vào lúc nào?
- GC, I/O và hoạt động thread có liên quan trong cùng khoảng thời gian không?
- virtual thread có bị pinned hoặc submit failed không?

Mental model:

~~~text
công cụ ảnh chụp
→ trạng thái tại một thời điểm

JFR
→ chuỗi event có timestamp + thời lượng + ngữ cảnh + stack (khi event bật)
~~~

JFR không tự “chẩn đoán nguyên nhân gốc”; nó cung cấp dòng thời gian giàu ngữ cảnh để kiểm tra giả thuyết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recording-lifecycle">Vòng đời của một flight recording</a>

<details>
<summary>Click for details</summary>

Một recording có vòng đời cơ bản:

~~~text
configure
   ↓
start
   ↓
record events
   ↓
dump / stop
   ↓
analyze
~~~

Có thể bắt đầu từ startup:

~~~text
java -XX:StartFlightRecording=filename=app.jfr,settings=default -jar app.jar
~~~

hoặc trên JVM đang chạy:

~~~text
jcmd <pid> JFR.start name=incident settings=default
jcmd <pid> JFR.check
jcmd <pid> JFR.dump name=incident filename=incident.jfr
jcmd <pid> JFR.stop name=incident filename=incident-final.jfr
~~~

Dump không nhất thiết phải dừng recording; điều này hữu ích khi muốn lấy một cửa sổ bằng chứng nhưng vẫn giữ continuous recording chạy tiếp.

Tên recording rất quan trọng nếu một JVM có nhiều recording. Ghi lại tên/ID ngay khi start để tránh dump nhầm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="recording-settings-and-overhead">Cấu hình recording, thời lượng và overhead</a>

<details>
<summary>Click for details</summary>

JDK cung cấp các cấu hình JFR phổ biến:

- **default.jfc**: tập event có chi phí thấp, phù hợp continuous recording;
- **profile.jfc**: thu nhiều dữ liệu hơn, phù hợp thời gian ngắn khi cần profiling sâu hơn.

`default.jfc` được thiết kế cho mức chi phí thấp và phù hợp với recording chạy liên tục. Với `JFR.start`, đừng phụ thuộc vào một impact label cố định giữa các JDK 21 update: Oracle Java 21 man page mô tả command là Low impact, trong khi một số JDK 21 update hiện tại (ví dụ 21.0.9) báo Medium vì cấu hình recording có thể dao động từ nhẹ đến nặng. Hãy dùng `jcmd <pid> help JFR.start` trên **JVM đích** làm contract thực tế. Chi phí cuối cùng vẫn phụ thuộc event, threshold, stack trace và workload.

Những lựa chọn cần cân nhắc:

- thời lượng: recording ngắn cho sự cố hay chạy liên tục;
- disk=true/false;
- `maxage` và `maxsize` để giới hạn dữ liệu giữ lại;
- threshold của event để tránh ghi mọi event quá nhỏ;
- có cần stack trace cho event đó không;
- chỉ bật việc thu thập đường tới GC roots khi thật sự điều tra leak vì thao tác này có thể gây pause/chi phí đáng kể. Với tên option, cần phân biệt version/tool surface: Oracle Java 21 `jcmd` man page ghi `path-to-gc-root` (singular), trong khi một số JDK 21 update hiện tại như 21.0.9 expose `path-to-gc-roots` (plural). Luôn kiểm tra `jcmd <pid> help JFR.start` trên JVM đích trước khi chạy.

Nguyên tắc: bắt đầu bằng bằng chứng có chi phí thấp từ cấu hình mặc định, sau đó mở rộng event hoặc dùng profile trong một khoảng ngắn nếu câu hỏi chưa được trả lời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jfr-event-model">Mô hình event và cách chọn dữ liệu cần quan sát</a>

<details>
<summary>Click for details</summary>

JFR ghi **event**, không phải một “log text” thống nhất. Mỗi loại event mô tả một dạng hành vi: CPU sample, allocation, GC phase, file/socket I/O, monitor enter, thread park, exception, virtual-thread event...

Event thường có:

- timestamp;
- thời lượng hoặc thời điểm tức thời;
- thread liên quan;
- fields chuyên biệt;
- stack trace nếu cấu hình bật.

Không nên bật mọi event chỉ vì “có thể cần”. Hãy xuất phát từ giả thuyết:

~~~text
high CPU
→ execution / CPU sample events

lock contention
→ monitor/park/thread events

memory growth
→ bằng chứng allocation / GC / old-object

I/O latency
→ socket/file events
~~~

Với virtual threads trong Java 21:

- `jdk.VirtualThreadPinned` mặc định được bật với threshold 20 ms;
- `jdk.VirtualThreadSubmitFailed` mặc định được bật;
- `jdk.VirtualThreadStart` và `jdk.VirtualThreadEnd` mặc định tắt và cần được bật khi thực sự cần quan sát vòng đời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cpu-thread-lock-analysis">Phân tích CPU samples, threads và locks</a>

<details>
<summary>Click for details</summary>

Đối với CPU, thread và lock, JFR giúp tránh phụ thuộc vào một thread dump đơn lẻ.

**CPU samples** cho biết stack nào xuất hiện thường xuyên trong quá trình lấy mẫu CPU. Đây là bằng chứng thống kê, không phải phép đo chính xác đến từng nanosecond.

**Các event về thread/lock** giúp thấy:

- thời lượng thread bị park/block;
- monitor contention;
- thread giữ lock và ngữ cảnh tùy event;
- virtual-thread pinning;
- tương quan giữa latency window và synchronization activity.

Quy trình:

~~~text
CPU/latency spike time
        ↓
lọc JFR đúng time range
        ↓
top execution stacks
        +
lock/park events
        ↓
đối chiếu với request/log
~~~

Nếu bằng chứng chỉ ra vấn đề về ngữ nghĩa lock, happens-before hoặc tính đúng đắn concurrency, chuyển sang module Concurrency; JFR chỉ cung cấp quan sát runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-gc-io-analysis">Phân tích allocation, GC và I/O events</a>

<details>
<summary>Click for details</summary>

JFR cũng kết nối ba câu hỏi thường tách rời: **allocation → GC → I/O**.

Với memory:

- allocation events cho biết chuỗi lời gọi tạo object;
- GC events cho biết hành vi pause/collection theo dòng thời gian;
- old-object sampling có thể hỗ trợ leak investigation khi được cấu hình phù hợp.

Với I/O:

- file/socket read/write events có thể cho thời lượng và ngữ cảnh;
- đối chiếu I/O event với thread park hoặc request latency để xem thời gian bị tiêu ở đâu.

Đừng nhầm tốc độ cấp phát cao với memory leak. Ứng dụng có thể cấp phát nhanh nhưng GC thu hồi tốt. Giả thuyết leak cần bằng chứng về việc giữ object và xu hướng theo thời gian.

Tương tự, GC event dày đặc là tín hiệu cần phân tích JVM/heap thêm; JFR không thay thế hiểu biết về cơ chế collector.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="continuous-recording-and-postmortem">Continuous recording và điều tra sau sự cố</a>

<details>
<summary>Click for details</summary>

Continuous recording biến JFR thành một “hộp đen” cho sự cố khó tái hiện.

Cách điển hình:

~~~text
JVM start
→ JFR chạy liên tục với cấu hình mặc định
→ giới hạn maxage/maxsize
→ sự cố xảy ra
→ dump recording trước khi restart
→ phân tích khoảng thời gian ngay trước sự cố
~~~

Lợi ích là không cần biết trước chính xác khi nào lỗi xảy ra. Recording giữ lại cửa sổ lịch sử gần nhất.

Ví dụ startup:

~~~text
-XX:StartFlightRecording=name=continuous,settings=default,disk=true,maxage=2h,maxsize=512m
~~~

Cần kiểm tra dung lượng lưu trữ và chính sách giữ dữ liệu. Recording có thể chứa tên class/method, đường dẫn, ngữ cảnh liên quan request hoặc custom event, nên vẫn phải coi là dữ liệu chẩn đoán trên môi trường production.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jfr-cli-and-jmc-boundary">jfr CLI, JDK Mission Control và ranh giới công cụ</a>

<details>
<summary>Click for details</summary>

JDK cung cấp **jfr CLI** để thao tác file recording mà không cần GUI:

~~~text
jfr summary recording.jfr
jfr print recording.jfr
jfr print --events <event-list> recording.jfr
jfr metadata recording.jfr
~~~

CLI phù hợp cho tự động hóa, kiểm tra nhanh và lọc dữ liệu lặp lại trong terminal.

**JDK Mission Control (JMC)** cung cấp UI mạnh hơn để xem dòng thời gian, phân tích tự động, hot methods, memory và đối chiếu event. JMC là công cụ đồng hành trong hệ sinh thái JDK; không nên giả định nó luôn được đóng gói cùng runtime.

Ranh giới:

- JFR = cơ chế ghi recording/event;
- jcmd / StartFlightRecording = điều khiển recording;
- `jfr` CLI = kiểm tra/in nội dung file;
- JMC = UI trực quan hóa/phân tích.

Học JFR không đồng nghĩa phải học mọi tính năng JMC. Module này tập trung cách dùng recording làm bằng chứng chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)
