<a id="back-to-top"></a>

# Chẩn đoán heap và Garbage Collection

## Menu
- [Chẩn đoán heap và GC giải quyết vấn đề gì?](#heap-gc-diagnostics-purpose)
- [Mức sử dụng heap, áp lực cấp phát và áp lực bộ nhớ](#heap-usage-vs-allocation-pressure)
- [Class histogram và cách dùng đúng mục đích](#class-histogram)
- [Phân tích heap dump khi điều tra OutOfMemoryError và memory growth](#heap-dump-and-oome)
- [GC logs, GC events và bằng chứng runtime](#gc-logs-and-events)
- [Ảnh chụp trạng thái và xu hướng bộ nhớ theo thời gian](#snapshot-vs-trend)
- [Từ nghi ngờ memory leak đến bằng chứng có thể kiểm chứng](#memory-leak-evidence)
- [Các bẫy thường gặp khi phân tích heap và GC](#heap-gc-diagnostic-pitfalls)

## <a id="heap-gc-diagnostics-purpose">Chẩn đoán heap và GC giải quyết vấn đề gì?</a>

<details>
<summary>Click for details</summary>

Chẩn đoán heap/GC trả lời các câu hỏi như:

- vì sao heap liên tục tăng?
- OutOfMemoryError đến từ áp lực heap hay nguyên nhân khác?
- tốc độ cấp phát object có quá cao không?
- GC có đang chạy quá thường xuyên?
- có dấu hiệu object bị giữ lại lâu hơn mong đợi không?

Điểm quan trọng là tách **cơ chế JVM** khỏi **bằng chứng chẩn đoán**. Module JVM sở hữu heap layout, collector và cơ chế cấp phát; chapter này tập trung cách dùng histogram, heap dump, GC logs và JFR để kiểm tra giả thuyết.

Mental model:

~~~text
memory symptom
   ↓
mức dùng heap + hành vi GC
   ↓
histogram / heap dump / JFR
   ↓
giả thuyết giữ object hoặc cấp phát
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-usage-vs-allocation-pressure">Mức sử dụng heap, áp lực cấp phát và áp lực bộ nhớ</a>

<details>
<summary>Click for details</summary>

Ba khái niệm thường bị trộn lẫn:

**Heap usage**  
Lượng Java heap đang được sử dụng tại một thời điểm.

**Allocation pressure**  
Tốc độ tạo object mới. Allocation cao có thể làm GC chạy thường xuyên nhưng không đồng nghĩa leak.

**Memory pressure**  
Tình trạng ứng dụng/JVM thiếu dư địa bộ nhớ do heap, native memory hoặc giới hạn container/OS.

Ví dụ:

~~~text
allocation rate cao
GC thu hồi tốt
post-GC heap ổn định
→ chưa có bằng chứng leak

post-GC heap tăng dần qua thời gian
→ cần điều tra khả năng object bị giữ lại
~~~

Vì vậy hãy nhìn usage **sau GC** và trend theo thời gian, không chỉ một peak.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-histogram">Class histogram và cách dùng đúng mục đích</a>

<details>
<summary>Click for details</summary>

Class histogram cho biết số instance và tổng size theo class trong heap tại thời điểm lấy.

Với jcmd:

~~~text
jcmd <pid> GC.class_histogram
~~~

Đừng hiểu “histogram” là một thao tác mặc định nhẹ. Trên Java 21, `jcmd` xếp `GC.class_histogram` là command có **High impact**, với chi phí phụ thuộc kích thước và nội dung heap. Trước khi lấy histogram — đặc biệt khi định lấy nhiều lần để so sánh — cần kiểm tra đúng JVM đích, thời điểm xảy ra triệu chứng và impact budget có thể chấp nhận.

Histogram hữu ích để:

- phát hiện class nào chiếm nhiều instance/bytes;
- so sánh nhiều ảnh chụp để xem class nào tăng;
- quyết định có cần heap dump sâu hơn không.

Nhưng histogram không cho đầy đủ object graph và đường dẫn giữ object. Một class lớn không có nghĩa nó bị leak; đó có thể là cache hợp lệ hoặc tập dữ liệu hiện tại.

Quy trình hợp lý:

~~~text
histogram t1
   ↓
histogram t2
   ↓
class nào tăng bất thường?
   ↓
heap dump / JFR / ngữ cảnh ứng dụng
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-dump-and-oome">Phân tích heap dump khi điều tra OutOfMemoryError và memory growth</a>

<details>
<summary>Click for details</summary>

Heap dump là ảnh chụp object graph của Java heap. Nó hữu ích khi cần hiểu **object nào đang tồn tại và vì sao chưa được thu gom**.

Tạo heap dump từ JVM đang chạy bằng jcmd:

~~~text
jcmd <pid> GC.heap_dump heap.hprof
~~~

Khi phân tích, thường quan tâm:

- retained size;
- dominator;
- GC roots;
- reference chain;
- classloader retention;
- cache/collection lớn bất thường.

`OutOfMemoryError` không tự động đồng nghĩa với memory leak. Heap có thể đơn giản là quá nhỏ, workload tăng, có đợt cấp phát đột biến, object graph hợp lệ quá lớn hoặc thực sự có leak.

Heap dump thường nặng và có thể tạo pause/disk pressure. Chapter Postmortem sở hữu việc tự động capture bằng HeapDumpOnOutOfMemoryError; chapter này sở hữu **phân tích** heap dump.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gc-logs-and-events">GC logs, GC events và bằng chứng runtime</a>

<details>
<summary>Click for details</summary>

GC logs cung cấp dòng thời gian về hoạt động thu gom. Với unified logging:

~~~text
-Xlog:gc*
~~~

Bằng chứng GC giúp trả lời:

- collection xảy ra bao nhiêu lần?
- pause time có tăng không?
- heap trước/sau GC thay đổi thế nào?
- collector có reclaim được memory không?

Ví dụ suy luận:

~~~text
GC chạy liên tục
post-GC heap vẫn tăng
→ giả thuyết object bị giữ lại mạnh hơn

GC chạy thường xuyên
post-GC heap ổn định
→ áp lực cấp phát / cấu hình kích thước heap có thể đáng xem hơn
~~~

Các GC event trong JFR bổ sung dòng thời gian để đối chiếu với CPU/thread/I/O.

Không dùng GC log để tự suy ra cơ chế nội bộ của collector vượt phạm vi; khi cần giải thích sâu hành vi collector, chuyển sang module JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="snapshot-vs-trend">Ảnh chụp trạng thái và xu hướng bộ nhớ theo thời gian</a>

<details>
<summary>Click for details</summary>

Một ảnh chụp lớn chưa chứng minh leak. Leak là vấn đề **object bị giữ lại theo thời gian**.

So sánh:

~~~text
t1 post-GC heap = 500 MB
t2 post-GC heap = 700 MB
t3 post-GC heap = 950 MB
~~~

Nếu workload tương đương mà live set tăng đều, cần điều tra thêm.

Xu hướng có thể lấy từ:

- GC logs;
- JMX/MXBeans;
- JFR;
- repeated histograms;
- monitoring metrics.

Các nguồn trên không có cùng chi phí thu thập. Đặc biệt, lặp lại `GC.class_histogram` vẫn là lặp lại một command được xếp **High impact** tùy heap size/content. Chỉ dùng repeated histogram khi giá trị chẩn đoán xứng đáng với chi phí đó; nếu không, ưu tiên dữ liệu theo thời gian ít xâm lấn hơn như GC log đã có, JFR, management data hoặc monitoring metrics.

Heap dump cho object graph chi tiết nhưng chỉ là một thời điểm. Hai dump cách nhau có thể mạnh hơn một dump đơn lẻ, nhưng chi phí cũng lớn hơn.

Luôn đối chiếu xu hướng với workload, lần deploy và traffic. Dữ liệu tăng hợp lệ không phải leak.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="memory-leak-evidence">Từ nghi ngờ memory leak đến bằng chứng có thể kiểm chứng</a>

<details>
<summary>Click for details</summary>

Giả thuyết memory leak nên được xây theo chuỗi:

~~~text
symptom: memory tăng
   ↓
post-GC live set có tăng?
   ↓
class histogram nào tăng?
   ↓
heap dump cho thấy object bị giữ bởi ai?
   ↓
Bằng chứng allocation/old-object từ JFR chỉ về chuỗi lời gọi nào?
~~~

Bằng chứng mạnh thường gồm:

- object population tăng qua thời gian;
- retained graph dẫn về long-lived root;
- workload không giải thích được mức tăng;
- sau full/major collection live set không giảm như mong đợi.

Tránh kết luận “class X leak” chỉ vì X có nhiều instance. Leak thực tế có thể do một cache hoặc listener sống lâu giữ reference đến X.

Nếu mức tăng nằm ngoài Java heap, chuyển sang chẩn đoán NMT/native memory.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-gc-diagnostic-pitfalls">Các bẫy thường gặp khi phân tích heap và GC</a>

<details>
<summary>Click for details</summary>

Các bẫy phổ biến:

**Heap usage cao = leak**  
Heap được phép dùng gần giới hạn nếu GC reclaim tốt.

**Allocation rate cao = leak**  
Cấp phát và việc giữ object sống là hai chuyện khác nhau.

**Một heap dump = nguyên nhân gốc**  
Dump cần ngữ cảnh, xu hướng và workload.

**Histogram top class = culprit**  
Class đứng đầu có thể chỉ là dữ liệu hợp lệ.

**Chỉ nhìn used trước GC**  
Post-GC live set thường hữu ích hơn cho retention trend.

**Bỏ qua native memory**  
RSS của tiến trình tăng không nhất thiết do Java heap.

**Heap dump không có rủi ro**  
Dump có thể rất lớn, tạo pause và chứa dữ liệu nhạy cảm.

Chẩn đoán heap/GC tốt luôn kết hợp ảnh chụp + xu hướng + ngữ cảnh runtime.

</details>

- [Quay lại đầu trang](#back-to-top)
