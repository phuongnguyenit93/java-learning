<a id="back-to-top"></a>

# Dữ liệu sự cố và chẩn đoán hậu kiểm

## Menu
- [Khi nào cần chuyển từ chẩn đoán trực tiếp sang phân tích hậu kiểm?](#postmortem-diagnostics-purpose)
- [Fatal error log và thông tin crash của HotSpot](#fatal-error-log)
- [Core dump, Serviceability Agent và jhsdb](#core-dump-and-jhsdb)
- [Tự động tạo heap dump khi OutOfMemoryError](#oome-heap-dump)
- [Bảo toàn logs, recordings và dữ liệu sự cố trước khi restart](#preserve-logs-and-recordings)
- [Kết hợp nhiều dữ liệu sự cố để kiểm tra giả thuyết](#correlate-crash-artifacts)
- [Khi nào chuyển sang Native Interoperability?](#native-crash-handoff)

## <a id="postmortem-diagnostics-purpose">Khi nào cần chuyển từ chẩn đoán trực tiếp sang phân tích hậu kiểm?</a>

<details>
<summary>Click for details</summary>

Chẩn đoán trực tiếp (live diagnostics) phù hợp khi JVM vẫn đang chạy đủ ổn định để trả lời lệnh chẩn đoán. Khi tiến trình đã crash, bị kill, treo đến mức không thể attach an toàn hoặc phải restart để khôi phục dịch vụ, trọng tâm chuyển sang **postmortem diagnostics**: phân tích những dữ liệu chẩn đoán đã được bảo toàn tại thời điểm sự cố.

Khác biệt cốt lõi:

- chẩn đoán trực tiếp có thể hỏi JVM thêm thông tin ngay lúc điều tra;
- postmortem chỉ có thể suy luận từ trạng thái đã được ghi lại;
- vì vậy giá trị của postmortem phụ thuộc rất lớn vào việc chuẩn bị trước: core dump, fatal error log, heap dump khi OOME, GC log và JFR chạy liên tục.

Một lần restart có thể làm mất bằng chứng quan trọng. Trước khi restart một JVM đang có vấn đề, hãy cân nhắc thu thập thread dump, JFR dump, bằng chứng heap/native memory và log hiện tại nếu thao tác đó không làm tình trạng tệ hơn.

Postmortem không thay thế chẩn đoán trực tiếp; nó là bước tiếp theo khi trạng thái sống không còn hoặc không thể tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fatal-error-log">Fatal error log và thông tin crash của HotSpot</a>

<details>
<summary>Click for details</summary>

Khi HotSpot gặp lỗi nghiêm trọng ở mức VM hoặc native code, nó thường tạo **fatal error log**. Tên mặc định có dạng hs_err_pid<PID>.log; vị trí có thể cấu hình bằng tùy chọn -XX:ErrorFile.

Fatal error log thường chứa nhiều lớp bằng chứng trong cùng một file:

- loại signal/exception đã làm JVM dừng;
- thread đang thực thi tại thời điểm lỗi;
- native và Java stack liên quan;
- thông tin VM, GC, heap và command line;
- danh sách dynamic library đã được load;
- thông tin hệ điều hành và CPU;
- đôi khi có ngữ cảnh register/memory phụ thuộc nền tảng.

Điều cần tránh là đọc một dòng “problematic frame” rồi kết luận đó chắc chắn là nguyên nhân gốc. Frame đó cho biết nơi crash được quan sát, nhưng nguyên nhân có thể nằm ở memory corruption xảy ra trước đó, JNI contract bị vi phạm, lỗi thư viện native hoặc lỗi VM.

Quy trình thực dụng:

1. xác nhận timestamp/PID đúng với sự cố;
2. đọc phần header và problematic frame;
3. xác định lỗi nằm trong JDK/VM, thư viện native của ứng dụng hay thư viện của hệ điều hành;
4. đối chiếu current thread + stack;
5. kiểm tra command-line flags và môi trường;
6. sau đó mới quyết định có cần core dump, symbol hoặc phân tích sâu hơn trong Native Interoperability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="core-dump-and-jhsdb">Core dump, Serviceability Agent và jhsdb</a>

<details>
<summary>Click for details</summary>

**Core dump** là ảnh chụp bộ nhớ tiến trình do hệ điều hành tạo ra tại thời điểm crash hoặc khi được yêu cầu. Nó lớn hơn fatal error log nhưng giữ nhiều trạng thái hơn, nên đặc biệt hữu ích khi cần postmortem ở mức native/VM.

Trong JDK, nhóm công cụ **jhsdb** dùng Serviceability Agent để đọc trạng thái HotSpot từ core dump hoặc tiến trình. Các subcommand như jhsdb jstack, jhsdb jmap và jhsdb jinfo cung cấp các góc nhìn khác nhau trên dữ liệu đó.

Ví dụ ý tưởng:

~~~text
jhsdb jstack --exe <java-executable> --core <core-file>
jhsdb jmap   --histo --exe <java-executable> --core <core-file>
~~~

Core dump chỉ thực sự hữu ích khi đi kèm đúng executable/JDK build và, với native code, symbol phù hợp. `jhsdb` là công cụ **experimental và unsupported**. Theo tài liệu Java 21, attach `jhsdb` vào một tiến trình đang chạy **sẽ làm tiến trình hang** và tiến trình **có khả năng crash khi debugger detach**. Vì vậy không dùng Serviceability Agent cho chẩn đoán live thông thường; hãy ưu tiên `jcmd`, JFR hoặc JMX khi JVM còn sống.

Một core dump cũng có thể chứa dữ liệu nhạy cảm vì nó phản ánh bộ nhớ của tiến trình. Việc lưu trữ, truyền và chia sẻ phải tuân theo chính sách bảo mật của hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="oome-heap-dump">Tự động tạo heap dump khi OutOfMemoryError</a>

<details>
<summary>Click for details</summary>

Đối với lỗi thiếu Java heap, cách chuẩn bị quan trọng là bật:

~~~text
-XX:+HeapDumpOnOutOfMemoryError
~~~

Khi JVM gặp `OutOfMemoryError` thuộc trường hợp có thể tạo heap dump, HotSpot sẽ ghi file HPROF mà không cần người vận hành kịp attach vào tiến trình. Có thể kiểm soát vị trí bằng `-XX:HeapDumpPath`.

Ví dụ trên shell Linux/macOS:

~~~text
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/log/java/app-%p.hprof \
     -jar app.jar
~~~

Điểm quan trọng là **thu thập** và **phân tích** là hai trách nhiệm khác nhau:

- chapter Postmortem sở hữu việc chuẩn bị và bảo toàn heap dump khi OOME;
- chapter Heap/GC sở hữu cách đọc histogram/heap dump để kiểm tra xu hướng tăng bộ nhớ hoặc giả thuyết leak.

Heap dump có thể rất lớn và việc ghi file có thể tạo pause đáng kể. Cần đảm bảo đủ dung lượng đĩa, đường dẫn có quyền ghi và quy trình bảo vệ dữ liệu nhạy cảm trong heap.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="preserve-logs-and-recordings">Bảo toàn logs, recordings và dữ liệu sự cố trước khi restart</a>

<details>
<summary>Click for details</summary>

Một sự cố hiếm khi được giải thích bởi một nguồn dữ liệu duy nhất. Trước khi restart, hãy bảo toàn các nguồn dữ liệu có thể biến mất:

- log ứng dụng và JVM/GC log trong đúng khoảng thời gian;
- continuous JFR hoặc một JFR dump vừa thu;
- thread dump nếu tiến trình còn phản hồi;
- heap dump hoặc histogram khi bài toán liên quan bộ nhớ;
- NMT summary/diff nếu NMT đã được bật;
- fatal error log và core dump nếu có crash;
- command line, environment/version và thời điểm deploy gần nhất.

Oracle khuyến nghị chuẩn bị Java cho troubleshooting từ trước: bật core file khi phù hợp, HeapDumpOnOutOfMemoryError, continuous Flight Recording, GC logging và remote management theo policy.

Nguyên tắc thực tế là **bảo toàn bằng chứng trước, restart sau** nếu SLA cho phép. Restart có thể phục hồi dịch vụ nhưng đồng thời xóa trạng thái thread, JFR buffer, bộ nhớ tiến trình và nhiều dấu hiệu tạm thời.

Dữ liệu chẩn đoán cũng là dữ liệu trên production cần được bảo vệ. Heap/core dump có thể chứa credentials, request payload, PII hoặc key material; quyền truy cập và chính sách giữ dữ liệu phải chặt hơn log thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="correlate-crash-artifacts">Kết hợp nhiều dữ liệu sự cố để kiểm tra giả thuyết</a>

<details>
<summary>Click for details</summary>

Phân tích postmortem mạnh nhất khi nhiều nguồn dữ liệu xác nhận lẫn nhau.

Ví dụ một sự cố bộ nhớ:

~~~text
OOME / bộ nhớ tiến trình tăng
        ↓
GC log cho thấy heap liên tục đầy?
        ↓
heap dump cho thấy retained object tăng?
        ↓
JFR cho thấy allocation source nào?
        ↓
NMT cho thấy tăng nằm trong JVM native category hay không?
~~~

Ví dụ một crash native:

~~~text
hs_err problematic frame
        ↓
native stack trong core dump
        ↓
JFR/log ngay trước crash
        ↓
thay đổi thư viện/JNI gần đây
~~~

Không phải mọi nguồn phải cùng “nói một điều”. Mâu thuẫn giữa chúng đôi khi là tín hiệu quan trọng: RSS tăng nhưng heap/NMT không tăng gợi ý allocation bên ngoài phạm vi đang quan sát; CPU cao nhưng ảnh chụp thread không lặp lại hot stack có thể là spike ngắn hoặc sampling sai thời điểm.

Mục tiêu là xây một giả thuyết có thể bị bác bỏ, không phải chọn dữ liệu phù hợp với kết luận có sẵn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-crash-handoff">Khi nào chuyển sang Native Interoperability?</a>

<details>
<summary>Click for details</summary>

Runtime Diagnostics chỉ đi đủ sâu để xác định rằng failure liên quan ranh giới Java ↔ native. Khi bằng chứng bắt đầu yêu cầu kiến thức về JNI contract, native library loading, ABI, vòng đời pointer/memory hoặc FFM, hãy chuyển sang module **Native Interoperability**.

Dấu hiệu nên chuyển module thường gặp:

- fatal error log chỉ vào frame trong thư viện native của ứng dụng;
- crash xảy ra trong JNI call hoặc callback;
- core dump cho thấy native stack hoặc memory corruption;
- RSS tăng nhưng Java heap và JVM-internal NMT không giải thích được;
- lỗi phụ thuộc architecture, native symbol hoặc phiên bản thư viện.

Runtime Diagnostics vẫn cung cấp “bằng chứng đầu vào”: PID, timestamp, hs_err, core, stack, JFR và danh sách thư viện. Native Interoperability chịu trách nhiệm giải thích ngữ nghĩa của JNI/FFM/native memory.

Không chuyển module chỉ vì stack có một native frame. Nhiều thao tác JDK hợp lệ đi qua native code; cần đối chiếu với ngữ cảnh failure trước.

</details>

- [Quay lại đầu trang](#back-to-top)
