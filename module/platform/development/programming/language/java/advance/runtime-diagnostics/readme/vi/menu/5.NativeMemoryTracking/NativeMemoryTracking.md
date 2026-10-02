<a id="back-to-top"></a>

# Chẩn đoán native memory với Native Memory Tracking

## Menu
- [Khi nào cần nhìn ra ngoài Java heap?](#native-memory-diagnostics-purpose)
- [NMT theo dõi gì và không theo dõi gì?](#nmt-scope-and-limitations)
- [Bật NMT và chọn summary hoặc detail](#enabling-nmt-and-tracking-levels)
- [Reserved và committed memory](#reserved-vs-committed)
- [Baseline, summary.diff và detail.diff](#nmt-baseline-and-diff)
- [Phân tích xu hướng tăng bộ nhớ native](#native-memory-growth-analysis)
- [Chi phí, giới hạn và bẫy khi dùng NMT](#nmt-overhead-and-pitfalls)

## <a id="native-memory-diagnostics-purpose">Khi nào cần nhìn ra ngoài Java heap?</a>

<details>
<summary>Click for details</summary>

Java heap chỉ là một phần bộ nhớ của tiến trình JVM. Khi RSS/bộ nhớ tiến trình tăng nhưng heap sau GC vẫn ổn định, hoặc container bị OOMKill dù Java heap chưa chạm `-Xmx`, cần nhìn sang **native memory**.

Nguồn native memory có thể gồm:

- metaspace/class metadata;
- thread stacks;
- code cache/JIT;
- GC internal structures;
- direct/native buffers;
- JNI hoặc thư viện native;
- memory mapping và cấu trúc VM khác.

Native Memory Tracking (NMT) giúp quan sát phần bộ nhớ do HotSpot quản lý. Nó không thay thế số liệu từ hệ điều hành và cũng không nhìn thấy toàn bộ native allocation của ứng dụng/thư viện bên thứ ba.

Mental model:

~~~text
RSS tiến trình / virtual memory
        ├─ Java heap
        ├─ HotSpot-managed native memory  ← NMT quan sát phần lớn nhóm này
        └─ native memory của ứng dụng / bên thứ ba
~~~

NMT đặc biệt hữu ích khi câu hỏi là “JVM đang dùng native memory ở category nào và category nào tăng theo thời gian?”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nmt-scope-and-limitations">NMT theo dõi gì và không theo dõi gì?</a>

<details>
<summary>Click for details</summary>

NMT theo dõi internal memory usage của HotSpot theo các category như Java Heap, Class, Thread, Code, GC, Compiler, Internal, Symbol và nhiều nhóm khác tùy build/JDK.

Điểm quan trọng là phạm vi:

- NMT theo dõi allocation mà HotSpot instrumentation biết;
- NMT **không** theo dõi allocation của third-party native code;
- NMT cũng **không** theo dõi allocation của Oracle JDK class libraries;
- NMT không phải RSS monitor của OS.

Vì vậy:

~~~text
RSS tăng
NMT tăng tương ứng
→ có khả năng JVM-managed native category đang tăng

RSS tăng
NMT gần như không tăng
→ xem xét native code ngoài NMT, mmap/file cache, hành vi allocator hoặc bộ nhớ ở cấp hệ điều hành
~~~

Không dùng “NMT không thấy tăng” để kết luận “không có native leak”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enabling-nmt-and-tracking-levels">Bật NMT và chọn summary hoặc detail</a>

<details>
<summary>Click for details</summary>

NMT **tắt mặc định** và phải được bật khi JVM khởi động:

~~~text
-XX:NativeMemoryTracking=summary
~~~

hoặc:

~~~text
-XX:NativeMemoryTracking=detail
~~~

Hai mức:

- **summary**: tổng hợp theo subsystem/category, thường đủ để theo dõi xu hướng trên production;
- **detail**: thêm thông tin theo call site và bản đồ virtual memory, hữu ích khi cần đi sâu nhưng tốn thêm chi phí và dữ liệu.

Sau khi JVM chạy, dùng jcmd:

~~~text
jcmd <pid> VM.native_memory summary
jcmd <pid> VM.native_memory detail
~~~

NMT chỉ có thể được bật bằng tùy chọn JVM khi khởi động. `jcmd` có thể **dừng** NMT bằng `VM.native_memory shutdown`, nhưng không thể start hoặc restart NMT trong JVM đang chạy; sau khi đã shutdown, muốn bật lại phải khởi động lại JVM với NMT được cấu hình từ đầu.

Chọn detail chỉ khi câu hỏi cần mức chi tiết đó; summary thường là điểm bắt đầu thực dụng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reserved-vs-committed">Reserved và committed memory</a>

<details>
<summary>Click for details</summary>

NMT thường hiển thị hai khái niệm dễ nhầm:

**Reserved**  
Không gian địa chỉ ảo JVM đã dành trước cho một mục đích. Reserved không có nghĩa toàn bộ số đó đã được backed bởi physical memory.

**Committed**  
Phần trong vùng reserved đã được commit để JVM sử dụng; đây thường gần hơn với memory thực tế mà VM có thể đang tiêu thụ, nhưng vẫn không đồng nhất tuyệt đối với RSS.

Ví dụ ý tưởng:

~~~text
Java Heap (reserved=4096MB, committed=1024MB)
~~~

Nghĩa là JVM đã reserve address space cho heap tối đa lớn hơn, trong khi chỉ một phần hiện được commit.

Khi điều tra, tránh cộng reserved rồi so trực tiếp với RSS. Hãy so category committed theo thời gian và đối chiếu với bộ nhớ ở cấp OS.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nmt-baseline-and-diff">Baseline, summary.diff và detail.diff</a>

<details>
<summary>Click for details</summary>

Giá trị lớn nhất của NMT thường nằm ở **xu hướng**, không phải một summary đơn lẻ.

Tạo baseline tại điểm phù hợp với câu hỏi điều tra:

~~~text
jcmd <pid> VM.native_memory baseline
~~~

Sau đó:

~~~text
jcmd <pid> VM.native_memory summary.diff
jcmd <pid> VM.native_memory detail.diff
~~~

Diff cho biết category/call-site nào thay đổi so với baseline. Với bài toán tăng trưởng ở trạng thái ổn định, quy trình hợp lý là:

1. JVM warm-up đến trạng thái tương đối ổn định;
2. tạo baseline;
3. chờ một workload/khoảng thời gian đại diện;
4. lấy summary.diff;
5. nếu một category đáng ngờ, mới chuyển sang detail hoặc nguồn bằng chứng khác.

Baseline lấy trong giai đoạn startup có thể chủ yếu phản ánh class loading/JIT/heap expansion bình thường. Nếu mục tiêu là phát hiện rò rỉ ở trạng thái ổn định, hãy đặt baseline sau warm-up; nếu cần theo dõi tăng trưởng từ sớm, hãy tạo baseline sớm và diễn giải cả giai đoạn khởi động.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-memory-growth-analysis">Phân tích xu hướng tăng bộ nhớ native</a>

<details>
<summary>Click for details</summary>

Khi thấy native memory tăng, đừng chỉ nhìn tổng số. Hãy hỏi category nào tăng và nó có hợp với workload hay không.

Ví dụ:

- **Thread** tăng cùng số platform threads → kiểm tra thread creation/stack size;
- **Class** tăng cùng dynamic class loading → kiểm tra classloader lifecycle;
- **Code** tăng trong warm-up → có thể liên quan JIT/code cache;
- **GC/Internal** tăng → cần JVM-level interpretation;
- NMT ổn nhưng RSS tăng → nghi ngờ memory ngoài HotSpot tracking.

Quy trình:

~~~text
OS RSS trend
   + NMT summary.diff
        ↓
identify growing category
        ↓
đối chiếu với hành vi thread/class/JFR/ứng dụng
        ↓
quyết định chuyển sang JVM / native / ứng dụng
~~~

NMT giúp thu hẹp phạm vi tìm kiếm; nó hiếm khi tự chỉ ra object/hàm “gây leak” cuối cùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nmt-overhead-and-pitfalls">Chi phí, giới hạn và bẫy khi dùng NMT</a>

<details>
<summary>Click for details</summary>

NMT có chi phí. Oracle ghi nhận **mức giảm hiệu năng khoảng 5–10%** khi bật tracking; ngoài ra NMT còn thêm metadata vào các allocation mà nó theo dõi. Vì vậy cần cân nhắc trước khi bật rộng rãi trên môi trường production.

Các bẫy phổ biến:

- chờ đến khi sự cố xảy ra mới bật NMT — không thể;
- nhầm reserved với committed;
- so NMT total với RSS như hai số phải bằng nhau;
- kết luận third-party native leak từ dữ liệu NMT vốn không bao phủ nguồn allocation đó;
- lấy một ảnh chụp rồi gọi đó là leak;
- bật detail mặc định mọi nơi mà không cần.

NMT là tính năng của HotSpot, không phải abstraction của ngôn ngữ Java. Khi bằng chứng yêu cầu giải thích sâu hơn các VM category, chuyển sang module JVM; khi nghi ngờ JNI/FFM hoặc native allocation của thư viện bên thứ ba, chuyển sang Native Interoperability.

</details>

- [Quay lại đầu trang](#back-to-top)
