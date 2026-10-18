<a id="back-to-top"></a>

# Heap được Quản lý, Cấp phát Object và Garbage Collection

## Menu
- [Managed heap tồn tại để giải quyết vấn đề gì?](#managed-heap-purpose)
- [Mô hình cấp phát object trên heap](#object-allocation-model)
- [Object layout - đặc tả và chi tiết implementation](#object-layout-boundary)
- [Reachability và object liveness](#reachability-and-liveness)
- [Automatic memory reclamation](#automatic-memory-reclamation)
- [Mô hình garbage collection](#garbage-collection-model)
- [Các chiến lược collector và cách chúng khác nhau](#collector-strategies)
- [Throughput, latency và memory-footprint trade-off](#gc-goals-and-tradeoffs)
- [Reference strength và tương tác với garbage collector](#reference-strengths)
- [Ranh giới giữa cơ chế GC và GC diagnostics](#gc-mechanism-vs-diagnostics)

## <a id="managed-heap-purpose">Managed heap tồn tại để giải quyết vấn đề gì?</a>

<details>
<summary>Click for details</summary>

Application liên tục tạo object có lifetime rất khác nhau. Nếu mỗi đoạn Java code phải tự quyết chính xác khi nào free từng object, chương trình sẽ dễ gặp use-after-free, double-free và memory leak do ownership phức tạp.

Managed heap đổi bài toán:

```text
ứng dụng
→ tạo và sử dụng object

runtime/GC
→ xác định object nào còn reachable
→ thu hồi vùng lưu trữ khi phù hợp
```

Lập trình viên Java vẫn phải quản lý **logical lifetime** bằng reference và resource lifecycle, nhưng không trực tiếp free heap object.

Đổi lại, runtime phải theo dõi reachability và thực hiện collection khi cần, tạo đánh đổi giữa pause, throughput và mức chiếm dụng bộ nhớ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-allocation-model">Mô hình cấp phát object trên heap</a>

<details>
<summary>Click for details</summary>

Ở specification level, object/array được cấp storage từ heap. Source:

```java
Order order = new Order();
```

cho ta mental model:

```text
new instruction
→ đảm bảo class sẵn sàng theo lifecycle rule
→ cấp storage cho một object chưa được khởi tạo
→ reference tới object đi vào operand stack
→ invokespecial gọi constructor <init>
→ object hoàn tất initialization theo constructor flow
```

Ở bytecode level, `new` và constructor invocation là **hai bước khác nhau**. `new` tạo một uninitialized object; compiler thường phát sinh `dup` rồi `invokespecial ... <init>` để gọi constructor. Distinction này quan trọng khi đọc bytecode hoặc verifier rules.

HotSpot có thể dùng các kỹ thuật cấp phát nhanh như thread-local allocation buffer hoặc bump-pointer tùy collector/layout, nhưng đó là chi tiết triển khai.

Do đó không nên dạy “Java object luôn được allocate bằng TLAB” như một hợp đồng. Hợp đồng là object có runtime storage và semantics đúng; chiến lược tối ưu có thể thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-layout-boundary">Object layout - đặc tả và chi tiết implementation</a>

<details>
<summary>Click for details</summary>

JVMS cố ý không quy định biểu diễn vật lý của object. Một implementation được tự do chọn header, alignment, field placement, pointer compression và nhiều chi tiết khác miễn semantics có thể quan sát vẫn đúng.

HotSpot thường có object header và alignment rules, nhưng các con số cụ thể phụ thuộc version, architecture và VM configuration.

Vì vậy các câu kiểu:

```text
mọi object Java = header X bytes + fields
```

chỉ đúng trong một ngữ cảnh implementation cụ thể.

Khi cần memory-sizing chính xác, hãy ghi rõ:

```text
HotSpot version
architecture
compressed-oops setting
alignment
```

Module JVM dạy ranh giới này để người học không biến output của một tool trên một máy thành quy luật chung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reachability-and-liveness">Reachability và object liveness</a>

<details>
<summary>Click for details</summary>

GC không thể chỉ hỏi “object được tạo bao lâu rồi?”. Câu hỏi quan trọng hơn là **object còn reachable từ runtime roots theo collector model hay không**.

Conceptually:

```text
GC roots
  ↓
reachable object graph
  ↓
objects còn cần giữ

unreachable objects
  ↓
có thể reclaim
```

Một memory leak trong Java thường không phải “GC quên free”, mà là ứng dụng vô tình giữ strong reference khiến object **vẫn reachable dù về mặt nghiệp vụ không còn cần**.

Reachability vì thế nối thiết kế chương trình với hành vi GC: reference graph của ứng dụng quyết định live set mà collector phải giữ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="automatic-memory-reclamation">Automatic memory reclamation</a>

<details>
<summary>Click for details</summary>

Automatic reclamation nghĩa là ứng dụng không gọi `free(object)` cho heap object. Collector xác định vùng lưu trữ nào có thể tái sử dụng khi object không còn thuộc live graph theo collector semantics.

“Automatic” không có nghĩa:

- GC chạy ngay khi reference cuối biến mất;
- object được reclaim tại thời điểm xác định trước;
- memory lập tức trả lại operating system;
- resource ngoài heap như file/socket tự đóng đúng lúc.

Đây là distinction rất quan trọng:

```text
heap object lifetime
≠ external resource lifetime
```

File, socket, database connection vẫn nên đóng deterministic bằng `try-with-resources` hoặc lifecycle API phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-model">Mô hình garbage collection</a>

<details>
<summary>Click for details</summary>

Garbage collector là storage-management subsystem tìm cách reclaim heap space mà không làm thay đổi Java-level semantics.

Một conceptual collector thường phải giải quyết:

1. xác định live/reachable data;
2. tìm/reclaim garbage;
3. có thể move/compact object;
4. cập nhật reference cần thiết;
5. phối hợp an toàn với các application thread.

Nhưng specification không bắt buộc collector cụ thể hay algorithm cụ thể. Collector có thể stop-the-world nhiều hơn, concurrent nhiều hơn, compact toàn phần hoặc dùng region.

Hãy học **vấn đề + đánh đổi** trước tên sản phẩm collector; như vậy kiến thức vẫn bền khi JDK thay đổi implementation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collector-strategies">Các chiến lược collector và cách chúng khác nhau</a>

<details>
<summary>Click for details</summary>

Collector khác nhau chủ yếu ở cách tổ chức heap và cách phân chia công việc giữa application threads với GC threads.

Các dimension thường gặp:

- **serial vs parallel**: collection work dùng một hay nhiều GC thread;
- **stop-the-world vs concurrent**: phần nào buộc ứng dụng pause;
- **generational vs non-generational**: có chia object theo age/lifetime assumption hay không;
- **region-based**: heap chia thành region để collection linh hoạt hơn;
- **compacting vs non-compacting**: cách xử lý fragmentation.

Ví dụ Java 21 có nhiều collector với goals khác nhau, và ZGC còn có generational/non-generational mode. Điều đó minh họa rằng “GC của Java” không phải một thuật toán duy nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gc-goals-and-tradeoffs">Throughput, latency và memory-footprint trade-off</a>

<details>
<summary>Click for details</summary>

Ba mục tiêu thường xung đột:

**Throughput**
Muốn phần lớn CPU time dành cho ứng dụng thay vì GC.

**Latency / pause time**
Muốn từng khoảng dừng ứng dụng ngắn để request/interaction ít bị gián đoạn.

**Memory footprint**
Muốn runtime giữ ít memory dư thừa.

Không có collector/configuration tối ưu tuyệt đối cả ba. Heap lớn có thể giảm frequency của collection nhưng tăng footprint; concurrent collector giảm pause nhưng có thể dùng thêm CPU/resources.

Do đó câu hỏi đúng không phải “GC nào tốt nhất?” mà là:

```text
workload ưu tiên throughput, latency hay footprint?
SLO và tài nguyên thực tế là gì?
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-strengths">Reference strength và tương tác với garbage collector</a>

<details>
<summary>Click for details</summary>

Ngoài strong reference thông thường, Java cung cấp `SoftReference`, `WeakReference` và `PhantomReference` để ứng dụng phối hợp với reachability/GC ở các use case chuyên biệt.

- **Strong**: giữ object strongly reachable theo graph thông thường.
- **Soft**: collector có thể clear khi memory pressure; thường được mô tả cho memory-sensitive cache nhưng không nên dùng như cache policy chính xác.
- **Weak**: không giữ referent sống chỉ vì weak reference tồn tại; hữu ích cho canonicalizing mapping/association có ownership yếu.
- **Phantom**: dùng để quan sát post-mortem cleanup/lifecycle; `get()` luôn trả `null`.

Các reference object được implementation phối hợp chặt với GC. Đây là công cụ nâng cao; nếu chỉ cần ownership rõ ràng, strong reference + lifecycle tường minh thường đơn giản hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gc-mechanism-vs-diagnostics">Ranh giới giữa cơ chế GC và GC diagnostics</a>

<details>
<summary>Click for details</summary>

Module JVM cần giúp người học hiểu **vì sao GC tồn tại và các đánh đổi của collector hoạt động thế nào**. Nhưng khi production có pause cao, allocation pressure hoặc nghi ngờ leak, ta cần bằng chứng.

Boundary:

```text
JVM
→ allocation / reachability / collector concepts / goals

Runtime Diagnostics
→ GC logging
→ heap histogram / heap dump
→ JFR allocation events
→ troubleshooting dựa trên bằng chứng
```

Không nên “tuning bằng cảm giác” chỉ từ theory. Cùng triệu chứng CPU cao có thể do allocation rate, collector choice, heap sizing hoặc hoàn toàn không liên quan GC.

Lý thuyết tạo hypothesis; diagnostics cung cấp bằng chứng để kiểm chứng.

</details>

- [Quay lại đầu trang](#back-to-top)
