# 📂 README MODULE STRUCTURE (VI)

* **1.MentalModel**
    * [RuntimeDiagnostics](readme/vi/menu/1.MentalModel/RuntimeDiagnostics.md)
* **2.JdkDiagnosticTools**
    * [JdkDiagnosticTools](readme/vi/menu/2.JdkDiagnosticTools/JdkDiagnosticTools.md)
* **3.ThreadDiagnostics**
    * [ThreadDiagnostics](readme/vi/menu/3.ThreadDiagnostics/ThreadDiagnostics.md)
* **4.HeapGcDiagnostics**
    * [HeapGcDiagnostics](readme/vi/menu/4.HeapGcDiagnostics/HeapGcDiagnostics.md)
* **5.NativeMemoryTracking**
    * [NativeMemoryTracking](readme/vi/menu/5.NativeMemoryTracking/NativeMemoryTracking.md)
* **6.JFR**
    * [JFR](readme/vi/menu/6.JFR/JFR.md)
* **7.ManagementJMX**
    * [ManagementJMX](readme/vi/menu/7.ManagementJMX/ManagementJMX.md)
* **8.CrashPostmortemDiagnostics**
    * [CrashPostmortemDiagnostics](readme/vi/menu/8.CrashPostmortemDiagnostics/CrashPostmortemDiagnostics.md)
* **9.TroubleshootingWorkflow**
    * [TroubleshootingWorkflow](readme/vi/menu/9.TroubleshootingWorkflow/TroubleshootingWorkflow.md)

# Runtime Diagnostics

## Module này là gì?

Runtime Diagnostics tập trung vào cách quan sát một JVM đang chạy, thu thập bằng chứng có kiểm soát và dùng bằng chứng đó để kiểm tra giả thuyết khi hệ thống gặp vấn đề. Trọng tâm của module là **troubleshooting dựa trên bằng chứng**: đi từ triệu chứng đến dữ liệu, từ dữ liệu đến giả thuyết, rồi mới tiến tới kết luận.

Module này không dạy lại toàn bộ cơ chế nội bộ JVM, lý thuyết concurrency, Java Agent hay một nền tảng observability cụ thể. Những domain đó là kiến thức nền hoặc điểm chuyển khi bằng chứng chẩn đoán yêu cầu đi sâu hơn.

## Tại sao cần Runtime Diagnostics?

Một sự cố ở môi trường production thường không nói thẳng nguyên nhân. CPU cao, mức dùng bộ nhớ tăng, request bị treo, OutOfMemoryError hoặc JVM crash chỉ là triệu chứng. Nếu chỉ nhìn một chỉ số, một thread dump hoặc một heap dump rồi kết luận ngay, rất dễ nhầm tương quan với nguyên nhân.

Runtime Diagnostics cung cấp một quy trình để:

- chuẩn bị khả năng thu thập bằng chứng trước khi sự cố xảy ra;
- chọn công cụ phù hợp với câu hỏi cần trả lời;
- phân biệt ảnh chụp trạng thái với xu hướng theo thời gian;
- kết hợp dữ liệu thread, heap, native memory, JFR, JMX, logs và dữ liệu sự cố;
- đánh giá tác động của thao tác chẩn đoán lên JVM đang chạy;
- biết khi nào bằng chứng đã đủ và khi nào cần chuyển sang module chuyên trách khác.

## Kiến thức nền cần có

Người học nên đã có nền tảng về:

- JVM execution, memory, allocation, GC và JIT ở mức mô hình tư duy từ module `JAVA_JVM`;
- thread lifecycle, synchronization và Java Memory Model ở mức nền tảng từ `JAVA_CONCURRENCY_FUNDAMENTALS`;
- Java Core đủ để đọc stack trace, exception và resource behavior.

Không cần hiểu sâu cách JVM hiện thực nội bộ hoặc các hệ thống observability theo từng vendor trước khi bắt đầu module này.

## Lộ trình học

Module đi theo thứ tự sau:

1. **Tư duy chẩn đoán runtime** — hiểu triệu chứng, bằng chứng, giả thuyết, nguyên nhân gốc và chi phí của từng loại bằng chứng.
2. **Nguồn dữ liệu và công cụ chẩn đoán JDK** — xác định JVM mục tiêu, ưu tiên `jcmd` và chuẩn bị khả năng thu thập dữ liệu trước sự cố.
3. **Thread và liveness diagnostics** — đọc dữ liệu thread, deadlock/contention và khác biệt giữa platform thread với virtual thread.
4. **Heap và GC diagnostics** — phân tích mức dùng heap, histogram, heap dump, GC evidence và giả thuyết memory leak.
5. **Native Memory Tracking** — mở rộng điều tra ra ngoài Java heap bằng NMT.
6. **Java Flight Recorder** — dùng dòng sự kiện theo thời gian để điều tra vấn đề hiệu năng và hành vi khó tái hiện.
7. **MXBeans và JMX** — quan sát và quản lý runtime qua Java management APIs.
8. **Crash/postmortem diagnostics** — bảo toàn và diễn giải fatal error logs, core dumps, heap dumps và recordings.
9. **Troubleshooting từ đầu đến cuối** — ghép các nguồn bằng chứng thành một quy trình có điểm dừng và điểm chuyển rõ ràng.

## Ranh giới và điểm chuyển

Module này sở hữu **bằng chứng chẩn đoán và quy trình troubleshooting**, không sở hữu toàn bộ lý thuyết phía sau các bằng chứng đó.

- GC, JIT và cơ chế runtime memory sâu hơn → `JAVA_JVM`.
- Concurrency correctness, happens-before và synchronization semantics → `JAVA_CONCURRENCY_FUNDAMENTALS`.
- Java Agent, bytecode instrumentation và runtime transformation → `JAVA_INSTRUMENTATION`.
- JNI/native-library behavior khi dữ liệu sự cố chỉ ra native boundary → `JAVA_NATIVE_INTEROPERABILITY`.
- Arthas hoặc runtime analysis theo công cụ/vendor cụ thể → module/tool owner tương ứng trong observability.
- Metrics/logs/traces platform architecture → infrastructure observability.

Kết thúc module, người học nên có khả năng nhìn một triệu chứng runtime, xây dựng kế hoạch thu thập bằng chứng phù hợp, lấy dữ liệu với mức tác động hợp lý, kiểm tra giả thuyết bằng nhiều nguồn và biết lúc nào cần chuyển sang domain chuyên sâu khác.
