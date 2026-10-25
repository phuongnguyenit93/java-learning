<a id="back-to-top"></a>

# Quy trình troubleshooting từ đầu đến cuối

## Menu
- [Vì sao chẩn đoán cần quy trình thay vì chạy công cụ ngẫu nhiên?](#troubleshooting-workflow-purpose)
- [Phân loại triệu chứng trước khi chọn bằng chứng](#classify-symptom)
- [Xây dựng kế hoạch thu thập bằng chứng](#build-evidence-plan)
- [Ưu tiên bằng chứng có chi phí thấp trước](#collect-low-impact-first)
- [Liên hệ nhiều nguồn dữ liệu để kiểm tra giả thuyết](#correlate-multiple-signals)
- [Bảo toàn trạng thái quan trọng trước khi restart](#preserve-before-restart)
- [Các sai lầm chẩn đoán thường gặp](#common-diagnostic-pitfalls)
- [Điểm dừng và chuyển sang module chuyên trách](#escalation-and-handoff)

## <a id="troubleshooting-workflow-purpose">Vì sao chẩn đoán cần quy trình thay vì chạy công cụ ngẫu nhiên?</a>

<details>
<summary>Click for details</summary>

Một sự cố runtime không được giải quyết tốt bằng cách “chạy mọi công cụ rồi xem có gì lạ”. Cách đó tạo rất nhiều dữ liệu nhưng ít bằng chứng có giá trị. Một quy trình tốt buộc người điều tra trả lời tuần tự:

~~~text
Triệu chứng là gì?
        ↓
Câu hỏi nào cần được trả lời tiếp?
        ↓
Bằng chứng rẻ nhất nào có thể trả lời?
        ↓
Kết quả ủng hộ hay bác bỏ giả thuyết nào?
        ↓
Cần thu thêm gì hay đã đủ để chuyển sang bước sửa/chuyên môn khác?
~~~

Quy trình giúp ba việc:

- tránh thao tác chẩn đoán nặng không cần thiết;
- giảm thiên kiến xác nhận (confirmation bias);
- tạo dòng thời gian và chuỗi suy luận mà người khác có thể xem xét lại.

Công cụ chỉ là phương tiện. Giá trị thật nằm ở việc mỗi lần thu thập đều có một câu hỏi cụ thể phía sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classify-symptom">Phân loại triệu chứng trước khi chọn bằng chứng</a>

<details>
<summary>Click for details</summary>

Trước khi chọn công cụ, hãy phân loại triệu chứng ở mức đủ rộng:

| Triệu chứng | Hướng bằng chứng đầu tiên |
| --- | --- |
| request treo / không tiến triển | thread dump, JFR thread/lock events |
| CPU cao | thread dump lặp, JFR CPU samples |
| Java heap tăng | GC log, histogram, bằng chứng heap/JFR |
| RSS tăng nhưng heap ổn | NMT, góc nhìn bộ nhớ từ OS, chuyển sang native nếu cần |
| latency spike ngắn | dòng thời gian JFR, đối chiếu log ứng dụng |
| JVM crash | hs_err, core, JFR/log trước crash |
| cần quan sát trạng thái từ xa | MXBeans/JMX |

Đây không phải bảng tra cứu tuyệt đối. Một triệu chứng có thể có nhiều nguyên nhân. Mục tiêu của bước phân loại chỉ là chọn **nhóm bằng chứng đầu tiên** có khả năng phân biệt nguyên nhân tốt với chi phí thấp.

Luôn ghi lại khoảng thời gian, host/container, PID, phiên bản runtime và thay đổi gần nhất. Bằng chứng chẩn đoán từ sai tiến trình hoặc sai thời điểm gần như vô giá trị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="build-evidence-plan">Xây dựng kế hoạch thu thập bằng chứng</a>

<details>
<summary>Click for details</summary>

Một kế hoạch thu thập bằng chứng nên viết được dưới dạng câu hỏi:

~~~text
Giả thuyết:
Request treo do thread contention.

Cần biết:
Các thread có lặp lại trạng thái BLOCKED trên cùng lock qua nhiều ảnh chụp không?

Bằng chứng:
3 thread dumps cách nhau 10 giây + JFR lock events.

Quyết định:
Nếu thread giữ lock và các stack bị block lặp lại → đi sâu concurrency.
Nếu không → kiểm tra I/O hoặc downstream latency.
~~~

Kế hoạch tốt có:

- giả thuyết hiện tại;
- dữ liệu cần thu;
- tác động dự kiến;
- thời điểm/số lần lấy;
- tiêu chí diễn giải;
- bước tiếp theo cho cả trường hợp giả thuyết đúng và sai.

Điều này biến troubleshooting thành quá trình có thể kiểm tra, thay vì chuỗi thao tác ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collect-low-impact-first">Ưu tiên bằng chứng có chi phí thấp trước</a>

<details>
<summary>Click for details</summary>

Ưu tiên bằng chứng theo nguyên tắc **tác động thấp → tập trung → xâm lấn cao**.

Ví dụ:

~~~text
logs / metrics sẵn có
        ↓
thông tin jcmd / ảnh chụp thread / JMX
        ↓
dữ liệu JFR mặc định/chạy liên tục
        ↓
histogram / focused recording
        ↓
heap dump / core dump / invasive attach
~~~

“Rẻ” không chỉ là chi phí CPU. Cần tính cả:

- pause time;
- lượng disk/network;
- nguy cơ làm tiến trình thiếu bộ nhớ hơn;
- độ nhạy cảm dữ liệu;
- thời gian của người vận hành;
- khả năng bằng chứng biến mất nếu chờ quá lâu.

Đôi khi dữ liệu chẩn đoán nặng phải được lấy ngay, ví dụ tiến trình sắp bị restart hoặc OOME đang diễn ra. Nguyên tắc tác động thấp không có nghĩa là trì hoãn bằng chứng không thể tái tạo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="correlate-multiple-signals">Liên hệ nhiều nguồn dữ liệu để kiểm tra giả thuyết</a>

<details>
<summary>Click for details</summary>

Một tín hiệu hiếm khi đủ để kết luận nguyên nhân gốc. Hãy đối chiếu theo **cùng PID, cùng khoảng thời gian và cùng giả thuyết**.

Ví dụ CPU cao:

- metric OS/container xác nhận CPU thật sự cao;
- repeated thread dumps cho thấy stack nào lặp lại;
- JFR CPU samples cho biết code path chiếm sample;
- GC events loại trừ việc CPU chủ yếu đến từ GC;
- dòng thời gian deploy/log chỉ ra thay đổi nào xảy ra trước spike.

Ví dụ bộ nhớ:

- RSS của tiến trình tăng;
- heap usage/GC xác định heap có phải nguồn tăng không;
- histogram/heap dump xem object population;
- NMT kiểm tra JVM-native categories;
- bằng chứng allocation/old-object từ JFR hỗ trợ xác định nguồn.

Đối chiếu dữ liệu không phải ghép mọi thứ vào một dashboard. Mục tiêu là kiểm tra xem nhiều nguồn độc lập có phù hợp với cùng một chuỗi nguyên nhân hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="preserve-before-restart">Bảo toàn trạng thái quan trọng trước khi restart</a>

<details>
<summary>Click for details</summary>

Restart là một thao tác phục hồi, không phải kết luận chẩn đoán. Nếu hệ thống cho phép, trước restart hãy hỏi:

1. Có trạng thái thread/JFR nào sẽ mất?
2. Có cần heap dump hoặc NMT diff không?
3. Fatal/core dump đã được sao chép khỏi vùng lưu trữ tạm thời chưa?
4. Log rotation có sắp xóa time window cần điều tra không?
5. PID/container cũ có metadata nào phải ghi lại?

Một checklist ngắn thường hữu ích:

~~~text
[ ] timestamp + PID/container
[ ] bằng chứng thread
[ ] JFR dump
[ ] bằng chứng bộ nhớ nếu liên quan
[ ] logs + JVM flags/version
[ ] crash artifacts nếu có
[ ] copy ra persistent storage
~~~

Không cố “thu đủ mọi thứ” khi service đang critical. Hãy cân bằng SLA với tính độc nhất và giá trị của bằng chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-diagnostic-pitfalls">Các sai lầm chẩn đoán thường gặp</a>

<details>
<summary>Click for details</summary>

Các lỗi suy luận phổ biến:

**Một ảnh chụp = một kết luận**  
Một thread đang RUNNABLE hoặc một object type lớn chưa chứng minh nguyên nhân gốc. Cần xu hướng và đối chiếu nhiều nguồn.

**Metric thay cho cơ chế**  
CPU 90% cho biết mức sử dụng, không cho biết code path. Heap 80% không tự nói có leak.

**Chỉ tìm bằng chứng ủng hộ giả thuyết**  
Mỗi kế hoạch phải có điều kiện bác bỏ.

**Dùng công cụ nặng quá sớm**  
Heap/core dump có thể làm sự cố tệ hơn và tạo file rất lớn.

**Không ghi time/PID/version**  
Dữ liệu đúng loại nhưng sai tiến trình/khoảng thời gian dẫn tới phân tích sai.

**Restart rồi mới nghĩ đến bằng chứng**  
Nhiều trạng thái runtime không thể phục dựng sau restart.

**Nhầm ranh giới**  
Diagnostics quan sát và khoanh vùng; không cần dạy lại GC algorithm, JMM, JNI hoặc instrumentation internals.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="escalation-and-handoff">Điểm dừng và chuyển sang module chuyên trách</a>

<details>
<summary>Click for details</summary>

Một cuộc điều tra chẩn đoán nên dừng hoặc chuyển sang chuyên môn khác khi:

- giả thuyết đã được bằng chứng đủ mạnh xác nhận và phần sửa nằm ở lĩnh vực khác;
- cần ngữ nghĩa chuyên sâu mà Runtime Diagnostics không sở hữu;
- bằng chứng hiện tại không đủ nhưng bước tiếp theo thuộc công cụ/platform chuyên biệt;
- chi phí thu thêm dữ liệu vượt lợi ích hoặc vượt mức rủi ro chấp nhận được trên môi trường production.

Các điểm chuyển chính:

| Bằng chứng chỉ ra | Chuyển sang |
| --- | --- |
| GC/JIT/runtime-memory mechanics | JAVA_JVM |
| lock/happens-before/concurrency correctness | JAVA_CONCURRENCY_FUNDAMENTALS |
| cần active bytecode instrumentation | JAVA_INSTRUMENTATION |
| JNI/FFM/native library/memory | JAVA_NATIVE_INTEROPERABILITY |
| Arthas hoặc phân tích runtime theo công cụ cụ thể | nhóm phụ trách công cụ observability |

Một lần chuyển giao tốt phải mang theo bằng chứng và giả thuyết hiện tại, không chỉ câu “có vẻ là JVM”. Điều đó giúp nhóm phụ trách lĩnh vực chuyên trách tiếp tục điều tra thay vì bắt đầu lại từ đầu.

</details>

- [Quay lại đầu trang](#back-to-top)
