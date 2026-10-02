<a id="back-to-top"></a>

# Chẩn đoán thread và liveness

## Menu
- [Chẩn đoán thread giải quyết vấn đề gì?](#thread-diagnostics-purpose)
- [Cấu trúc và ý nghĩa của thread dump](#thread-dump-structure)
- [Trạng thái thread, stack trace và bằng chứng thực thi](#thread-states-and-stack-evidence)
- [Locks, deadlocks và contention dưới góc nhìn chẩn đoán](#locks-deadlocks-and-contention)
- [Nhiều thread dump theo thời gian và phân tích liveness](#repeated-thread-dumps)
- [Platform thread và virtual thread trong chẩn đoán Java 21](#platform-vs-virtual-thread-diagnostics)
- [Các bẫy thường gặp khi kết luận từ dữ liệu thread](#thread-diagnostic-pitfalls)

## <a id="thread-diagnostics-purpose">Chẩn đoán thread giải quyết vấn đề gì?</a>

<details>
<summary>Click for details</summary>

Chẩn đoán thread trả lời câu hỏi **“các luồng đang làm gì và vì sao công việc không tiến triển?”**. Nó hữu ích khi ứng dụng treo, request chậm, CPU cao, thread pool cạn, nghi ngờ deadlock hoặc nhiều thread cùng chờ một tài nguyên.

Một thread dump không phải lịch sử thực thi. Nó là ảnh chụp tại một thời điểm của:

- định danh thread;
- trạng thái Java;
- stack trace;
- thông tin monitor/lock khi công cụ hỗ trợ;
- đôi khi có phần tóm tắt deadlock.

Vì vậy chẩn đoán thread mạnh nhất khi dùng để **hình thành và kiểm tra giả thuyết**, không phải kết luận từ một ảnh chụp trạng thái.

Ranh giới quan trọng: module này đọc bằng chứng về thread. Ngữ nghĩa sâu của `synchronized`, `Lock`, happens-before và tính đúng đắn concurrency thuộc Java Concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-dump-structure">Cấu trúc và ý nghĩa của thread dump</a>

<details>
<summary>Click for details</summary>

Thread dump truyền thống thường gồm một khối cho mỗi platform thread: tên, id, trạng thái, stack frame và các chú thích lock/monitor.

Ví dụ rút gọn:

~~~text
"worker-1" #31 ... RUNNABLE
   java.lang.Thread.State: RUNNABLE
        at com.example.OrderService.calculate(OrderService.java:84)
        at ...
~~~

Với monitor, có thể thấy annotation dạng conceptual:

~~~text
- waiting to lock <0x...>
- locked <0x...>
- waiting on <0x...>
~~~

Đọc theo thứ tự:

1. xác định tên/id của thread có liên quan workload không;
2. xem trạng thái;
3. đọc vài frame trên cùng — nơi thread đang ở;
4. tìm quan hệ lock/monitor;
5. so với các thread khác và các ảnh chụp trạng thái khác.

Một stack frame cho biết **nơi thread được quan sát**, không chứng minh frame đó là nguyên nhân gốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-states-and-stack-evidence">Trạng thái thread, stack trace và bằng chứng thực thi</a>

<details>
<summary>Click for details</summary>

`Thread.State` là nhóm thuật ngữ nền đầu tiên để đọc dump:

- **RUNNABLE**: đang thực thi hoặc runnable theo JVM; không đồng nghĩa chắc chắn đang dùng CPU;
- **BLOCKED**: chờ monitor để vào synchronized block/method;
- **WAITING**: chờ vô thời hạn qua cơ chế như Object.wait, Thread.join hoặc park;
- **TIMED_WAITING**: chờ có timeout;
- NEW/TERMINATED thường ít xuất hiện trong dump live thông thường.

Stack trace cho biết chuỗi lời gọi hiện tại. Khi nhiều thread có cùng stack, hãy hỏi đó là **điểm chờ mong đợi** hay nút thắt.

Ví dụ một consumer thread ở trạng thái WAITING trong thao tác lấy dữ liệu từ queue có thể hoàn toàn bình thường khi không có công việc. Ngược lại, hàng trăm thread xử lý request cùng BLOCKED trên một lock của ứng dụng qua nhiều ảnh chụp là bằng chứng mạnh hơn về contention.

Không gán trạng thái → vấn đề một cách máy móc. Trạng thái phải được đọc cùng workload, stack và yếu tố thời gian.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-deadlocks-and-contention">Locks, deadlocks và contention dưới góc nhìn chẩn đoán</a>

<details>
<summary>Click for details</summary>

**Contention** xảy ra khi nhiều thread cạnh tranh một tài nguyên/lock. **Deadlock** là trường hợp mạnh hơn: một tập thread tạo vòng chờ khiến không thread nào trong vòng có thể tiến triển.

Ví dụ deadlock:

~~~text
Thread A giữ Lock 1 → chờ Lock 2
Thread B giữ Lock 2 → chờ Lock 1
~~~

Thread dump có thể cho thấy quan hệ giữa thread giữ lock và thread đang chờ; một số lệnh chẩn đoán của JDK còn báo deadlock được phát hiện.

Điều tra contention:

- tìm nhiều thread BLOCKED trên cùng monitor;
- xác định thread đang giữ lock;
- xem thread đó đang chạy mã gì;
- lặp dump để xem mẫu có kéo dài;
- đối chiếu với các JFR event về monitor/park khi cần thời lượng và dòng thời gian.

Phát hiện deadlock không đồng nghĩa tìm được mọi lỗi liveness. Starvation, chờ I/O bên ngoài, executor bị bão hòa hoặc logical livelock có thể làm hệ thống không tiến triển mà không tạo vòng khóa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repeated-thread-dumps">Nhiều thread dump theo thời gian và phân tích liveness</a>

<details>
<summary>Click for details</summary>

Một dump chỉ cho một thời điểm. Để phân biệt trạng thái tạm thời với vấn đề liveness, hãy lấy **nhiều dump cách nhau một khoảng ngắn phù hợp với sự cố**.

Ví dụ:

~~~text
t0      thread A → same stack / same lock
t0+10s  thread A → same stack / same lock
t0+20s  thread A → same stack / same lock
~~~

Nếu nhiều thread xử lý request giữ nguyên quan hệ stack/lock qua nhiều ảnh chụp trong lúc lưu lượng vẫn đến, giả thuyết “stuck/blocking” mạnh hơn.

Ngược lại, stack thay đổi liên tục có thể cho thấy thread vẫn tiến triển dù mỗi ảnh chụp nhìn có vẻ “bận”.

Các lần dump lặp nên:

- dùng cùng PID;
- ghi timestamp;
- giữ khoảng cách nhất quán;
- tránh khoảng lấy mẫu quá dày gây nhiễu;
- đối chiếu với CPU/latency/log trong cùng khoảng thời gian.

Khi cần dòng thời gian rõ hơn thay vì ảnh chụp thủ công, JFR là bước tiếp theo tự nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="platform-vs-virtual-thread-diagnostics">Platform thread và virtual thread trong chẩn đoán Java 21</a>

<details>
<summary>Click for details</summary>

Java 21 có hai loại thread cần phân biệt về observability:

**Platform threads**  
Được `ThreadMXBean` quản lý/quan sát. Thread dump truyền thống và `jcmd Thread.print` phù hợp với số lượng platform thread thông thường.

**Virtual threads**  
Có thể tồn tại với số lượng rất lớn. Trong Java 21, `ThreadMXBean` **không** giám sát hoặc quản lý virtual thread.

Để dump cả platform và virtual threads:

~~~text
jcmd <pid> Thread.dump_to_file -format=text threads.txt
jcmd <pid> Thread.dump_to_file -format=json threads.json
~~~

Oracle lưu ý định dạng dump này không chứa đầy đủ mọi thông tin của thread dump truyền thống như địa chỉ object và một số thông tin lock/JNI/heap.

JFR bổ sung event quan trọng cho virtual threads, đặc biệt VirtualThreadPinned và VirtualThreadSubmitFailed. Vì vậy với ứng dụng dùng virtual thread nhiều, thread dump + JFR thường bổ sung cho nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-diagnostic-pitfalls">Các bẫy thường gặp khi kết luận từ dữ liệu thread</a>

<details>
<summary>Click for details</summary>

Các bẫy phổ biến:

**RUNNABLE = đang ăn CPU**  
Không luôn đúng. Cần CPU sample, JFR hoặc bằng chứng từ hệ điều hành.

**WAITING/BLOCKED = bug**  
Nhiều wait là hành vi thiết kế bình thường.

**Một dump đủ chứng minh stuck**  
Một ảnh chụp đơn lẻ thiếu yếu tố thời gian.

**Thread nhiều = vấn đề**  
Với virtual threads, số lượng lớn là bình thường; cần xét loại thread và workload.

**Chỉ đọc thread name**  
Tên có thể được tái sử dụng hoặc do framework đặt; stack và ngữ cảnh quan trọng hơn.

**Thấy deadlock-free rồi kết luận liveness ổn**  
Starvation, I/O stall, executor queue saturation vẫn có thể tồn tại.

**Dùng ThreadMXBean để đếm virtual threads**  
Java 21 ThreadMXBean chỉ bao phủ platform threads.

Khi câu hỏi chuyển từ “thread đang chờ ở đâu” sang “thiết kế đồng bộ có đúng không”, hãy chuyển sang module Concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)
