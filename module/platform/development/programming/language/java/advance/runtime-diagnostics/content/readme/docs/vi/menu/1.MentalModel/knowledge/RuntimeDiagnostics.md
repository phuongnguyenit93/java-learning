<a id="back-to-top"></a>

# Tư duy chẩn đoán runtime

## Menu
- [Chẩn đoán runtime là gì và vì sao cần?](#runtime-diagnostics-purpose)
- [Từ triệu chứng đến bằng chứng, giả thuyết và nguyên nhân gốc](#diagnostic-reasoning-loop)
- [Chất lượng, chi phí và độ xâm lấn của bằng chứng](#evidence-quality-and-cost)
- [Chuẩn bị khả năng chẩn đoán trước sự cố](#diagnostic-readiness-principle)
- [Ranh giới với cơ chế nội bộ JVM, instrumentation và observability](#diagnostic-scope-boundaries)

## <a id="runtime-diagnostics-purpose">Chẩn đoán runtime là gì và vì sao cần?</a>

<details>
<summary>Click for details</summary>

Chẩn đoán runtime là quá trình quan sát một JVM đang chạy hoặc các dữ liệu chẩn đoán mà JVM để lại để trả lời một câu hỏi cụ thể về sự cố. Mục tiêu không phải là “chạy càng nhiều công cụ càng tốt”, mà là biến một triệu chứng bên ngoài thành bằng chứng đủ mạnh để kiểm chứng một giả thuyết.

Ví dụ, các biểu hiện sau mới chỉ là **triệu chứng**:

- API chậm bất thường;
- tiến trình dùng nhiều CPU;
- bộ nhớ của tiến trình tăng liên tục;
- request bị treo;
- xuất hiện `OutOfMemoryError`;
- JVM crash.

Mỗi triệu chứng có nhiều nguyên nhân khả dĩ. API chậm có thể do thread bị block, lock contention, GC pause, I/O, database hoặc đoạn mã đang chạy nóng. Vì vậy chẩn đoán runtime tập trung vào câu hỏi:

```text
Triệu chứng
    ↓
Điều gì đang xảy ra trong JVM?
    ↓
Bằng chứng nào có thể quan sát được?
    ↓
Giả thuyết nào phù hợp với bằng chứng?
    ↓
Cần thêm dữ liệu gì để xác nhận hoặc bác bỏ?
```

Trong module này, “bằng chứng” có thể là thread dump, class histogram, heap dump, GC log, JFR recording, đầu ra của Native Memory Tracking, dữ liệu MXBean/JMX hoặc dữ liệu crash. Mỗi loại chỉ nhìn thấy một phần runtime; không có nguồn dữ liệu đơn lẻ nào giải thích được mọi sự cố.

Điểm quan trọng là tách **cơ chế** khỏi **chẩn đoán**. Module JVM giải thích heap, GC, JIT và quá trình thực thi runtime hoạt động thế nào. Module Concurrency giải thích synchronization, happens-before và tính đúng đắn. Runtime Diagnostics dùng những mô hình đó để diễn giải dữ liệu quan sát được khi hệ thống có vấn đề.

Khi học xong module, bạn nên có khả năng bắt đầu từ một triệu chứng, chọn loại bằng chứng phù hợp, thu thập nó với chi phí hợp lý, rồi xây dựng kết luận có thể kiểm chứng thay vì đoán từ cảm giác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-reasoning-loop">Từ triệu chứng đến bằng chứng, giả thuyết và nguyên nhân gốc</a>

<details>
<summary>Click for details</summary>

Một quy trình chẩn đoán tốt luôn tách ba thứ thường bị trộn lẫn: **điều quan sát được**, **giả thuyết giải thích**, và **nguyên nhân gốc**.

Ví dụ:

```text
Quan sát:
  p95 latency tăng từ 100 ms lên 4 s

Giả thuyết A:
  nhiều request đang chờ cùng một lock

Giả thuyết B:
  GC pause kéo dài

Giả thuyết C:
  downstream I/O bị chậm
```

Chưa giả thuyết nào là kết luận. Bạn cần chọn bằng chứng có khả năng phân biệt chúng: nhiều thread dump theo thời gian cho A, GC log/JFR cho B, stack và I/O event cho C.

Một vòng lặp thực tế thường có dạng:

1. **Xác định triệu chứng và thời điểm** — lỗi xảy ra lúc nào, với request nào, kéo dài bao lâu, có lặp lại không.
2. **Khoanh vùng subsystem** — thread/liveness, Java heap, GC, native memory, CPU, I/O hay crash.
3. **Đặt giả thuyết có thể bác bỏ** — ví dụ “worker threads đang chờ cùng một monitor”, không phải “Java đang bị treo”.
4. **Chọn bằng chứng rẻ nhất có thể phân biệt giả thuyết**.
5. **Thu thập và đối chiếu theo thời gian** — tránh kết luận từ một ảnh chụp nếu vấn đề mang tính xu hướng.
6. **Cập nhật giả thuyết** — dữ liệu không phù hợp thì bỏ giả thuyết, không cố ép dữ liệu khớp với suy đoán ban đầu.
7. **Xác nhận nguyên nhân bằng hành vi có thể tái kiểm tra** — sau khi sửa, tín hiệu liên quan phải thay đổi theo dự đoán.

Một nguyên nhân gốc tốt phải giải thích được cả triệu chứng lẫn bằng chứng. Ví dụ “heap gần đầy” chưa phải nguyên nhân gốc; “cache không giới hạn giữ lại hàng triệu entry không còn cần thiết, khiến live set tăng qua mỗi chu kỳ tải” là giả thuyết cụ thể hơn và có thể kiểm tra bằng histogram, heap dump và xu hướng theo thời gian.

Thói quen quan trọng nhất là ghi lại **dòng thời gian**. Thread dump lúc 10:01, GC pause lúc 10:01:03 và latency spike lúc 10:01:03 có giá trị hơn ba nguồn dữ liệu không có quan hệ thời gian.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="evidence-quality-and-cost">Chất lượng, chi phí và độ xâm lấn của bằng chứng</a>

<details>
<summary>Click for details</summary>

Bằng chứng chẩn đoán không chỉ khác nhau về thông tin mà còn khác nhau về **chi phí thu thập** và mức độ chúng có thể làm thay đổi chính hệ thống đang quan sát.

Có thể hình dung theo một phổ:

```text
thông tin tiến trình / flags
        ↓
counter / log / lightweight event
        ↓
thread dump
        ↓
class histogram
        ↓
heap dump hoặc dữ liệu chẩn đoán rất lớn

thường nhẹ hơn -------------------- thường nặng hơn
```

Thứ tự này chỉ là mô hình tư duy; tác động thực tế phụ thuộc JVM, số thread, kích thước heap, collector, tải hệ thống và lệnh cụ thể. Chính `jcmd <pid> help <command>` cũng hiển thị mức tác động cho nhiều lệnh chẩn đoán.

Khi đánh giá một nguồn dữ liệu, hãy hỏi:

- **Nó trả lời câu hỏi gì?** Thread dump mạnh về execution/locks nhưng không cho biết object nào giữ phần lớn heap.
- **Ảnh chụp hay dòng thời gian?** Một histogram là ảnh chụp; GC log và JFR cung cấp chuỗi sự kiện theo thời gian.
- **Có thể làm JVM dừng hoặc tiêu tốn tài nguyên đáng kể không?** Một số thao tác heap có thể cần safepoint hoặc full GC, heap dump còn tạo I/O rất lớn.
- **Có đủ chỗ lưu dữ liệu chẩn đoán không?** Heap dump có thể gần bằng kích thước live heap hoặc lớn hơn kỳ vọng vận hành.
- **Quan sát có làm lệch hành vi không?** Khi hệ thống đang sát giới hạn CPU/I/O, một thao tác nặng có thể làm sự cố trầm trọng thêm.

Chất lượng bằng chứng cũng quan trọng. Một ảnh chụp đúng nhưng lấy sau khi restart có thể vô dụng vì trạng thái gây lỗi đã mất. Một log liên tục có thể giá trị hơn dữ liệu rất chi tiết nhưng được thu thập sai thời điểm.

Nguyên tắc thực tế là bắt đầu bằng dữ liệu ít xâm lấn có khả năng phân biệt các giả thuyết, sau đó tăng độ sâu khi cần. “Nhiều dữ liệu hơn” không tự động đồng nghĩa với “bằng chứng tốt hơn”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-readiness-principle">Chuẩn bị khả năng chẩn đoán trước sự cố</a>

<details>
<summary>Click for details</summary>

Nhiều bằng chứng quan trọng chỉ tồn tại trong thời gian ngắn. Restart có thể giải phóng heap, xóa trạng thái lock, kết thúc thread, làm mất ngữ cảnh của crash hoặc ghi đè log. Vì vậy khả năng chẩn đoán phải được chuẩn bị **trước** khi sự cố xảy ra.

Một baseline vận hành tốt thường bao gồm:

- biết chính xác JDK/JVM version và startup flags;
- có chính sách lưu và xoay vòng JVM/log ứng dụng;
- cân nhắc bật GC logging phù hợp với môi trường;
- cấu hình `-XX:+HeapDumpOnOutOfMemoryError` và vị trí lưu bằng `-XX:HeapDumpPath=...` nếu disk budget cho phép;
- cân nhắc JFR chạy liên tục khi cần dữ liệu sự kiện trước thời điểm sự cố;
- bảo đảm người vận hành có quyền chạy công cụ chẩn đoán trên đúng tiến trình;
- biết nơi dữ liệu chẩn đoán được ghi và còn đủ dung lượng;
- đồng bộ timestamp giữa log ứng dụng, JVM log và hệ thống monitoring.

Oracle khuyến nghị chuẩn bị heap dump cho `OutOfMemoryError`, GC logging và flight recording chạy liên tục như một phần của khả năng sẵn sàng troubleshooting. Điều này không có nghĩa mọi JVM trên production phải bật mọi thứ với cùng cấu hình; lựa chọn phải dựa trên chi phí, chính sách dữ liệu và khả năng lưu trữ của hệ thống.

Một checklist nhỏ trước sự cố có giá trị lớn:

```text
JVM nào?
PID/container nào?
JDK tools có sẵn ở đâu?
Ai có quyền attach?
Dữ liệu chẩn đoán ghi vào đâu?
Disk còn bao nhiêu?
Timestamp dùng timezone nào?
Restart sẽ làm mất dữ liệu gì?
```

Sự chuẩn bị trước biến troubleshooting từ phản ứng ngẫu nhiên thành quy trình lặp lại được. Khi sự cố xảy ra, bạn dành thời gian phân tích dữ liệu thay vì đi tìm PID, xin quyền hoặc phát hiện ổ đĩa không đủ để ghi heap dump.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-scope-boundaries">Ranh giới với cơ chế nội bộ JVM, instrumentation và observability</a>

<details>
<summary>Click for details</summary>

Runtime Diagnostics đứng tại ranh giới giữa nhiều lĩnh vực, nên cần biết **đi đến đâu thì dừng**.

**JVM internals** giải thích cơ chế phía dưới. Khi GC log cho thấy pause dài, diagnostics giúp xác định thời điểm, tần suất và liên hệ với triệu chứng. Giải thích sâu collector algorithm, allocation mechanics hay JIT compilation strategy thuộc module JVM.

**Concurrency** sở hữu ngữ nghĩa và tính đúng đắn. Thread dump có thể cho thấy một monitor đang bị giữ, nhiều thread đang `BLOCKED`, hoặc một deadlock cycle. Việc giải thích memory model, happens-before, lock design và cách sửa synchronization đúng về mặt concurrency thuộc module Java Concurrency.

**Instrumentation** chủ động thay đổi hoặc chèn quan sát vào runtime, ví dụ Java Agent và bytecode transformation. Diagnostics trong module này ưu tiên các khả năng quan sát sẵn có của JDK/JVM. Nếu bài toán yêu cầu transform class để chèn probe thì đã chuyển sang ranh giới của Instrumentation.

**Observability platform** như metrics/logs/traces backend, APM hoặc công cụ vendor cung cấp hệ thống thu thập và phân tích ở quy mô lớn. Module này tập trung vào JDK/JVM diagnostic primitives và cách suy luận từ bằng chứng; kiến trúc telemetry platform không phải phạm vi chính.

Mental model nên giữ là:

```text
JVM / Concurrency
→ giải thích cơ chế

Runtime Diagnostics
→ quan sát bằng chứng và troubleshooting

Instrumentation
→ chủ động can thiệp / chèn quan sát

Observability platform
→ thu thập, lưu trữ và phân tích telemetry ở cấp hệ thống
```

Các ranh giới này không tách biệt tuyệt đối trong thực tế. Một sự cố có thể bắt đầu từ metric, dùng `jcmd` để lấy thread dump, chuyển sang JVM để giải thích hành vi GC, rồi sang Concurrency để sửa locking. Điều quan trọng là biết module nào sở hữu phần kiến thức nào để không biến một bài troubleshooting thành một khóa JVM hoặc concurrency đầy đủ.

</details>

- [Quay lại đầu trang](#back-to-top)
