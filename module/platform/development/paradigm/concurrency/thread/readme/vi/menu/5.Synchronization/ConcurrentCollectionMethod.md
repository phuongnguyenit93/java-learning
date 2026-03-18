<a id="back-to-top"></a>

# Các phương thức của Concurrent Collection

## Menu
- [ConcurrentHashMap: Sức mạnh của các thao tác Nguyên tử (Atomic)](#concurrent-hashmap)
- [ConcurrentSkipListMap & Set: Nghệ thuật sắp xếp trong Đa luồng](#concurrent-skip-list)
- [So sánh thực nghiệm: TreeMap vs. ConcurrentSkipListMap](#treemap-and-concurrent-skip-list-map)
- [Copy-On-Write (COW): Giải pháp cho hệ thống Read-Heavy](#copy-on-write-array)
- [ConcurrentLinkedQueue: "Phễu" dữ liệu tốc độ cao (Non-blocking)](#concurrent-linked-queue)


## <a id="concurrent-hashmap">ConcurrentHashMap: Sức mạnh của các thao tác Nguyên tử (Atomic)</a>
<details>
<summary>Click for details</summary>


Trong môi trường đa luồng, việc sử dụng các phương thức "siêu năng lực" của `ConcurrentHashMap` giúp loại bỏ hoàn toàn hiện tượng **Race Condition** mà không cần dùng đến từ khóa `synchronized` nặng nề.

## 1. Tại sao HashMap thường thất bại trong Đa luồng?

Nếu sử dụng `HashMap` thông thường cho bài toán đếm (Counter), hệ thống sẽ gặp 3 vấn đề:
1.  **Mất mát dữ liệu (Data Loss):** Hai luồng cùng đọc giá trị 10, cùng cộng 1 và ghi lại 11. Kết quả đúng phải là 12, nhưng thực tế chỉ là 11.
2.  **Kết quả không nhất quán:** Mỗi lần chạy cho ra một con số khác nhau (950, 980...).
3.  **Lỗi Runtime:** Có thể gây vòng lặp vô hạn khi `resize` Map đồng thời trên các phiên bản Java cũ.

## 2. Các "Siêu năng lực" (Atomic Methods) khuyên dùng

Thay vì thực hiện chuỗi hành động `get` -> `tính toán` -> `put` thủ công, hãy sử dụng các phương thức nguyên tử sau:

### A. `compute()` / `computeIfPresent()`
Đảm bảo quá trình thay đổi giá trị là nguyên tử.
```java
// Lấy giá trị cũ -> cộng 1 -> ghi giá trị mới (Atomic)
apiStats.compute(apiName, (key, val) -> (val == null) ? 1 : val + 1);
```

### B. `computeIfAbsent()`
Cực kỳ hiệu quả để khởi tạo Cache.
```java
    // Nếu chưa có trong map thì mới load từ DB, nếu có rồi thì lấy ra luôn
    apiStats.computeIfAbsent("config", key -> loadFromDB(key));
```

### C. `putIfAbsent()`
Chỉ thêm vào nếu Key chưa tồn tại. Trả về giá trị hiện tại nếu Key đã có.

### D. `replace(key, oldValue, newValue)`
Cơ chế **CAS (Compare-And-Swap)** cấp độ ứng dụng. Chỉ cập nhật nếu giá trị hiện tại đúng bằng kỳ vọng.
```java
    // Chỉ cập nhật từ 10 lên 11 nếu hiện tại vẫn là 10
    boolean success = apiStats.replace("counter", 10, 11);
```

## 3. So sánh hiệu năng: ConcurrentHashMap vs. SynchronizedMap

Mặc dù `Collections.synchronizedMap()` an toàn về dữ liệu, nhưng nó gặp vấn đề về **Scalability**:

| Tiêu chí | SynchronizedMap | ConcurrentHashMap |
| :--- | :--- | :--- |
| **Cơ chế khóa** | Khóa toàn bộ Map (Một ổ khóa lớn). | Khóa phân đoạn (**Lock Striping**) / CAS. |
| **Độ trễ** | Cao (Các luồng phải xếp hàng đợi nhau). | Thấp (Nhiều luồng làm việc song song). |
| **Hiệu năng** | Giảm mạnh khi số luồng tăng. | Duy trì ổn định ở tải cao. |
| **Loại khóa** | Pessimistic Locking (Khóa bi quan) | Optimistic / Fine-grained (Khóa nhỏ) |
| **Luồng đọc** | Bị chặn bởi luồng ghi | **Không bao giờ bị chặn** |
| **Iterator** | Fail-fast (Ném lỗi nếu bị sửa khi duyệt) | Weakly consistent (An toàn khi duyệt) |

## 4. Ví dụ thực tế: Request Counter System

Dưới đây là cách triển khai hệ thống thống kê API an toàn tuyệt đối:

```java
    ConcurrentHashMap<String, Integer> apiStats = new ConcurrentHashMap<>();
    ExecutorService executor = Executors.newFixedThreadPool(10);

    for (int i = 0; i < 1000; i++) {
        executor.execute(() -> {
            // Sử dụng compute để đảm bảo tính chính xác 100%
            apiStats.compute("getUserInfo", (k, v) -> (v == null) ? 1 : v + 1);
        });
    }
```
---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="concurrent-skip-list">ConcurrentSkipListMap & Set: Nghệ thuật sắp xếp trong Đa luồng</a>
<details>
<summary>Click for details</summary>


Trong hệ sinh thái Java, đây là phiên bản an toàn đa luồng của `TreeMap` và `TreeSet`. Điểm đặc trưng nhất là dữ liệu luôn được **sắp xếp theo thứ tự** (Sorted) nhưng vẫn đảm bảo hiệu năng cực cao khi có nhiều luồng truy cập đồng thời.

## 1. Tại sao lại dùng "Skip List" thay vì "Red-Black Tree"?

Đây là một quyết định kiến trúc quan trọng của các kỹ sư Java:

* **TreeMap (Red-Black Tree):** Việc "cân bằng lại cây" (rebalancing) khi có thay đổi là một thao tác toàn cục phức tạp. Rất khó để thực hiện việc này một cách an toàn mà không cần khóa (lock) toàn bộ cái cây, gây ra hiện tượng nghẽn cổ chai.
* **ConcurrentSkipListMap (Skip List):** Sử dụng cấu trúc danh sách nhiều tầng. Việc chèn một phần tử mới chỉ ảnh hưởng đến các liên kết cục bộ xung quanh nó. Điều này giúp Java áp dụng được cơ chế **CAS (Compare-And-Swap)** để ghi dữ liệu mà không cần dùng Lock (Lock-free).

## 2. So sánh Concurrent vs. Non-Concurrent

| Đặc điểm | TreeMap / TreeSet | ConcurrentSkipListMap / Set |
| :--- | :--- | :--- |
| **An toàn đa luồng** | Không. Dễ văng `ConcurrentModificationException`. | **Có**. An toàn tuyệt đối cho nhiều luồng. |
| **Cơ chế khóa** | Phải dùng `synchronized` bọc ngoài (Khóa cứng). | **Lock-free** (Dùng thuật toán CAS). |
| **Hiệu năng đa luồng** | Thấp do tranh chấp khóa (Contention). | **Rất cao** và ổn định khi tăng số lượng luồng. |
| **Duyệt dữ liệu** | Fail-fast (Lỗi nếu bị sửa khi đang duyệt). | **Weakly consistent** (An toàn khi vừa duyệt vừa sửa). |
| **Giá trị Null** | Không cho phép Null Key. | Không cho phép Null Key/Value. |
| **Thứ tự phần tử** | Có sắp xếp (Sorted). | Có sắp xếp (Sorted). |
| **Thuật toán** | Red-Black Tree (Cây đỏ đen). | Skip List (Danh sách nhảy). |

## 3. Khi nào dùng loại nào trong dự án?

Trong dự án `java-learning`, bạn hãy áp dụng theo các kịch bản sau:

1.  **Làm Leaderboard (Bảng xếp hạng):** Cập nhật điểm số và thứ hạng liên tục từ hàng nghìn luồng người chơi.
2.  **Job Scheduler (Lập lịch tác vụ):** Lưu danh sách các task kèm theo thời gian thực hiện. Luồng quản lý sẽ dùng `firstEntry()` để lấy ra task có thời gian gần nhất để chạy.
3.  **Range Search (Tìm kiếm theo khoảng):** Truy xuất các bản ghi log hoặc dải IP trong một khoảng nhất định bằng các hàm `subMap()`, `headMap()`, `tailMap()`.

## 4. Phân tích sự thắng thế về khả năng mở rộng (Scalability)

* **Tránh hiện tượng Nghẽn cổ chai:** Trong khi `Synchronized TreeMap` bắt các luồng phải xếp hàng đợi nhau, `ConcurrentSkipListMap` cho phép nhiều luồng chèn dữ liệu ở các vùng khác nhau trên danh sách cùng một lúc.
* **Độ ổn định:** Nếu bạn tăng từ 50 lên 500 luồng, `ConcurrentSkipListMap` giữ được tốc độ xử lý ổn định hơn nhiều vì xác suất hai luồng cùng can thiệp vào một vị trí cục bộ là rất thấp.

---
> **Quy tắc vàng:** Nếu bạn cần một cấu trúc dữ liệu vừa **An toàn đa luồng**, vừa **Luôn được sắp xếp**, vừa **Hỗ trợ tìm kiếm theo khoảng** $\rightarrow$ `ConcurrentSkipListMap` là ứng cử viên số 1.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="treemap-and-concurrent-skip-list-map">So sánh thực nghiệm: TreeMap vs. ConcurrentSkipListMap</a>
<details>
<summary>Click for details</summary>


Khi làm việc với các Map có sắp xếp (Sorted Map), việc chọn sai cấu trúc dữ liệu trong môi trường đa luồng không chỉ làm giảm hiệu năng mà còn trực tiếp phá hủy tính toàn vẹn của dữ liệu.

## 1. Kết quả thực nghiệm (Benchmarking)

Khi chạy cùng một khối lượng công việc (10 luồng, mỗi luồng 1000 phần tử), chúng ta thu được kết quả như sau:

| Loại Map | Độ chính xác | Trạng thái | Hiệu năng |
| :--- | :--- | :--- | :--- |
| **Unsafe TreeMap** | **Sai lệch** (Size < 10,000) | Dễ crash (Exception) | Không xác định |
| **Synchronized TreeMap** | Chính xác (Size = 10,000) | An toàn | Chậm (Do tranh chấp khóa) |
| **ConcurrentSkipListMap** | **Chính xác** (Size = 10,000) | An toàn | **Nhanh & Ổn định** |

## 2. Tại sao Unsafe TreeMap lại thất bại?

TreeMap sử dụng cấu trúc **Cây Đỏ-Đen (Red-Black Tree)**. Khi nhiều luồng cùng can thiệp, các vấn đề sau sẽ xảy ra:

* **Phá vỡ cấu trúc Cây:** Thao tác cân bằng lại cây (rebalancing) yêu cầu thay đổi hàng loạt liên kết (parent, left, right). Khi hai luồng cùng đảo cây, các con trỏ sẽ bị chỉ sai hướng, làm "gãy" cấu trúc dữ liệu.
* **Mất dữ liệu âm thầm:** Một luồng ghi đè lên nút mà luồng kia đang xử lý.
* **Lỗi Runtime:** Thường xuyên văng `NullPointerException` hoặc `ConcurrentModificationException` do luồng duyệt đụng phải các nút rỗng hoặc nút đang bị biến đổi.

## 3. Tại sao ConcurrentSkipListMap lại chiến thắng?

Thay vì cố gắng bảo vệ một cái cây phức tạp bằng "ổ khóa lớn", `ConcurrentSkipListMap` sử dụng cấu trúc danh sách nhiều tầng:

1.  **Cơ chế Nhảy (Skip):** Tương tự tìm kiếm nhị phân trên danh sách liên kết. Tốc độ tìm kiếm đạt $O(\log n)$.
2.  **Cập nhật cục bộ:** Việc chèn một nút chỉ ảnh hưởng đến các nút lân cận.
3.  **Thuật toán CAS (Lock-free):** Sử dụng lệnh của CPU để đổi con trỏ. Nếu có tranh chấp, luồng sẽ tự động thử lại (**Retry**) thay vì đứng đợi (Blocked). Điều này giúp hệ thống luôn có tiến triển (**Liveness**).

## 4. Bài học rút ra cho Software Engineer

* **Đừng tự xây Lock:** Việc dùng `Collections.synchronizedMap(new TreeMap<>())` tuy an toàn nhưng sẽ trở thành điểm nghẽn (Bottleneck) khi hệ thống mở rộng.
* **Ưu tiên cấu trúc Lock-free:** `ConcurrentSkipListMap` là lựa chọn tối ưu cho các bài toán cần sắp xếp trong môi trường High-concurrency như: Bảng xếp hạng, Lập lịch tác vụ (Scheduler), hoặc Hệ thống quản lý phiên làm việc (Session Management).


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="copy-on-write-array">Copy-On-Write (COW): Giải pháp cho hệ thống Read-Heavy</a>
<details>
<summary>Click for details</summary>


Nhóm cấu trúc dữ liệu `CopyOnWriteArrayList` và `CopyOnWriteArraySet` hoạt động dựa trên triết lý: Duyệt dữ liệu trên một **Snapshot** (ảnh chụp) cố định, giúp luồng Đọc không bao giờ phải chờ đợi luồng Ghi.

## 1. Cơ chế hoạt động: "Snapshot Iterator"

Điểm khác biệt cốt lõi của COW nằm ở cách nó xử lý vòng lặp (Iterator):

1.  **Khi bắt đầu duyệt:** Luồng Đọc sẽ giữ một tham chiếu đến mảng nội bộ hiện tại (Snapshot).
2.  **Khi có thay đổi (Add/Set/Remove):** Luồng Ghi không sửa trực tiếp. Nó sao chép toàn bộ mảng cũ sang một mảng mới, thực hiện thay đổi trên đó, rồi mới trỏ tham chiếu chính sang mảng mới này.
3.  **Kết quả:** Luồng Đọc hoàn thành công việc trên mảng cũ một cách an toàn, trong khi luồng Ghi đã chuẩn bị xong dữ liệu mới cho các luồng Đọc tiếp theo. Không bao giờ xảy ra `ConcurrentModificationException`.

## 2. So sánh CopyOnWrite vs. Đồng bộ hóa truyền thống

| Đặc điểm | ArrayList (Thường) | `Collections.synchronizedList` | CopyOnWriteArrayList |
| :--- | :--- | :--- | :--- |
| **An toàn luồng** | Không | Có (Dùng Lock cứng) | **Có (Lock-free cho luồng Đọc)** |
| **Hiệu năng Đọc** | Cực nhanh | Chậm (Bị chặn bởi luồng Ghi) | **Cực nhanh (Không tốn phí Lock)** |
| **Hiệu năng Ghi** | Nhanh | Trung bình | **Rất chậm** (Tốn phí copy mảng) |
| **Vòng lặp** | Fail-fast (Dễ văng lỗi) | Phải tự bọc `synchronized` | **Snapshot** (An toàn tuyệt đối) |

## 3. Khi nào nên và không nên dùng?

### ✅ Nên dùng khi:
* **Hệ thống Read-Heavy:** Số lượng luồng Đọc chiếm >95%.
* **Danh sách cấu hình:** Blacklist IP, danh sách Permissions, cấu hình Feature Flag.
* **Kiến trúc Sự kiện (Event Bus):** Lưu trữ danh sách các `Listeners` hoặc `Observers`.

### ❌ KHÔNG nên dùng khi:
* **Hệ thống Write-Heavy:** Thay đổi dữ liệu liên tục (VD: Log collector).
* **Dữ liệu khổng lồ:** Việc copy mảng hàng triệu phần tử mỗi khi `add()` sẽ gây áp lực cực lớn lên Garbage Collector (GC) và dễ dẫn đến lỗi `OutOfMemoryError`.

## 4. Bảng so sánh chiến lược thực tế

| Tình huống | Lựa chọn tối ưu | Tại sao? |
| :--- | :--- | :--- |
| **Danh sách User Online** (Thêm/xóa liên tục) | `ConcurrentHashMap.newKeySet()` | Hiệu năng ghi của cơ chế **Lock Striping** tốt hơn nhóm COW rất nhiều khi tần suất thay đổi cao. |
| **Danh sách chặn IP / Cấu hình** (Ít thay đổi) | `CopyOnWriteArrayList` | Ưu tiên **tốc độ đọc** cực nhanh và tuyệt đối an toàn (Snapshot) khi duyệt qua danh sách. |
| **Xử lý dữ liệu cục bộ** (Trong 1 hàm đơn lẻ) | `ArrayList` | Không có sự tranh chấp đa luồng, dùng `ArrayList` để tránh tốn chi phí đồng bộ hóa vô ích. |


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="concurrent-linked-queue">ConcurrentLinkedQueue: "Phễu" dữ liệu tốc độ cao (Non-blocking)</a>
<details>
<summary>Click for details</summary>


`ConcurrentLinkedQueue` là một hàng đợi phi chặn (non-blocking), được thiết kế cho các hệ thống yêu cầu tần suất đẩy và lấy dữ liệu cực cao mà không muốn các luồng bị treo.

## 1. Cơ chế hoạt động: Phép màu của CAS

Thay vì dùng ổ khóa (`Lock`), `ConcurrentLinkedQueue` sử dụng thuật toán **Wait-free** dựa trên lệnh **CAS (Compare-And-Swap)** của CPU.
* Khi một luồng muốn thêm hoặc lấy phần tử, nó sẽ thử thay đổi con trỏ của nút (node).
* Nếu có luồng khác đang làm việc đó, nó sẽ không đứng đợi (Blocked) mà sẽ tự động thử lại ngay lập tức. Điều này giúp loại bỏ hoàn toàn chi phí **Context Switch** (chuyển đổi ngữ cảnh luồng) vốn rất tốn kém.

## 2. So sánh: ConcurrentLinkedQueue vs. BlockingQueue

Đây là bảng giúp bạn quyết định chiến thuật điều phối dữ liệu:

| Đặc điểm | ConcurrentLinkedQueue | BlockingQueue (Array/Linked) |
| :--- |:---| :--- |
| **Cơ chế** | **Non-blocking**. Dùng thuật toán CAS.                                   | **Blocking**. Dùng Lock và Condition. |
| **Khi Queue Đầy** | Thường là Unbounded (tự nở ra).Luôn cho phép thêm vào (trừ khi tràn RAM) | Producer bị **Treo** (Wait) để điều tiết tải. |
| **Khi Queue Trống** | Consumer nhận về `null` ngay.   | Consumer bị **Treo** (Wait) chờ dữ liệu. |
| **Hiệu năng** | **Cực cao**, độ trễ (latency) thấp nhất.   | Thấp hơn do chi phí dừng/chạy luồng. |
| **Độ an toàn RAM** | Nguy cơ OOM nếu Producer quá nhanh.  | An toàn nhờ cơ chế **Backpressure**. |

## 3. Khi nào dùng cái nào?

### A. Bảng so sánh mục tiêu kiến trúc

| Đặc điểm | ConcurrentLinkedQueue | BlockingQueue |
| :--- | :--- | :--- |
| **Mục tiêu chính** | **Tốc độ cao**, độ trễ (latency) thấp nhất. | **Phối hợp nhịp nhàng** giữa các luồng. |
| **Cơ chế** | Thuật toán CAS (Lock-free). | Lock & Condition (Signalling). |
| **Sử dụng CPU** | Có thể tốn CPU nếu dùng vòng lặp `poll()`. | **Tiết kiệm CPU** vì luồng được nghỉ (Wait). |
| **Triết lý** | "Cứ đẩy đi, không cần đợi ai cả." | "Đợi một chút, hãy làm việc cùng nhau." |

### B. Khi nào dùng cái nào? (Dành cho java-learning)

#### 🚀 Dùng `ConcurrentLinkedQueue` (Trường phái Tốc độ)
* **Hệ thống thu thập (Collector):** Ghi Log, thu thập Metric, Event Tracking.
    * *Lý do:* Việc ghi log không được phép làm chậm quá trình thanh toán hay đăng nhập của khách hàng. Nếu mất một vài log do tràn RAM cũng có thể chấp nhận được so với việc làm treo toàn bộ App.
* **Low Latency Messaging:** Khi tốc độ đẩy dữ liệu giữa các module cần đạt mức mili giây.

#### 🛡️ Dùng `BlockingQueue` (Trường phái An toàn & Điều tiết)
* **Cơ chế điều tiết tải (Backpressure):** Khi bạn muốn bảo vệ RAM. Nếu Producer đẩy quá nhanh, nó buộc phải dừng lại cho đến khi Consumer xử lý bớt dữ liệu.
* **Xử lý đơn hàng / Giao dịch:** Đảm bảo không mất mát bất kỳ dữ liệu nào.
* **Thread Pool:** Quản lý các Task thực thi. Consumer sẽ "đi ngủ" để tiết kiệm tài nguyên khi không có việc và "tỉnh giấc" ngay khi có Task mới thông qua `take()`.

### 3. Tóm tắt kịch bản thực tế

1.  **Ghi Log cực nhanh:** Chọn `ConcurrentLinkedQueue`.
2.  **Xử lý đơn hàng/Thanh toán:** Chọn `LinkedBlockingQueue` hoặc `ArrayBlockingQueue`.
3.  **Gửi Email Marketing hàng loạt:** Chọn `PriorityBlockingQueue` (để ưu tiên khách VIP trước).
4.  **Trao đổi dữ liệu trực tiếp 1-1:** Chọn `SynchronousQueue` (không có bộ đệm, Producer phải đợi Consumer nhận thì mới đi tiếp).

## 4. Lưu ý "xương máu"
Với `ConcurrentLinkedQueue`, hàm `size()` không phải là hằng số $O(1)$ như các Collection khác. Nó phải duyệt qua toàn bộ danh sách để đếm, vì vậy **không nên gọi `.size() == 0`** trong vòng lặp, thay vào đó hãy dùng **`.isEmpty()`**.

</details>

- [Quay lại đầu trang](#back-to-top)