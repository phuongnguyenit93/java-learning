<a id="back-to-top"></a>

# Concurrent Collections: Xa lộ đa làn cho dữ liệu

## Menu
- [Khái quát về Concurrent Collection](#concurrent-collection-basic)
- [Concurrent Collections và Nghệ thuật Đồng bộ hóa Thông minh](#synchronization-with-concurrent-collection)
- [Phân biệt Manual Locks vs. Concurrent Collections](#manual-lock-and-concurrent-collection)
- [Các phương thức trong Concurrent Collections](#concurrent-collection-method)



## <a id="concurrent-collection-basic">Khái quát về Concurrent Collection</a>
<details>
<summary>Click for details</summary>


Trong Java, `Concurrent Collections` (thuộc gói `java.util.concurrent`) là các cấu trúc dữ liệu được tối ưu hóa đặc biệt để hoạt động an toàn và hiệu quả khi nhiều luồng cùng truy cập đồng thời.

## 1. Tại sao cần Concurrent Collections?

Trong Java truyền thống, việc dùng `synchronized` hoặc `Collections.synchronizedMap` gây ra các nhược điểm:
* **Khóa toàn bộ (Coarse-grained locking):** Chỉ một luồng được truy cập tại một thời điểm, gây nghẽn cổ chai.
* **Lỗi `ConcurrentModificationException`:** Xảy ra khi một luồng đang duyệt (iterate) còn luồng khác lại sửa đổi dữ liệu.

Concurrent Collections giải quyết vấn đề này bằng kỹ thuật **Lock Striping** (chia nhỏ khóa) và **CAS (Compare-And-Swap)**.

## 2. Các nhóm Concurrent Collections chính

Dựa trên các bài học trước, chúng ta phân loại chúng như sau:

### A. Nhóm Map (Key-Value)
* **`ConcurrentHashMap`:** Phổ biến nhất. Chỉ khóa phân đoạn đang bị ghi, cho phép nhiều luồng đọc/ghi song song ở các vị trí khác nhau.
* **`ConcurrentSkipListMap`:** Map được sắp xếp theo thứ tự, an toàn cho đa luồng.

### B. Nhóm List & Set
* **`CopyOnWriteArrayList`:** Mỗi khi ghi, nó tạo một bản sao mới. Cực nhanh cho việc **Đọc** nhưng chậm khi **Ghi**.
* **`CopyOnWriteArraySet`:** Tương tự List nhưng đảm bảo không trùng lặp phần tử.

### C. Nhóm Queue (Hàng đợi điều phối)
* **`BlockingQueue`:** (VD: `LinkedBlockingQueue`, `ArrayBlockingQueue`) Có khả năng chặn luồng khi đầy/trống. Đây là "linh hồn" của các Thread Pool.
* **`ConcurrentLinkedQueue`:** Hàng đợi không chặn (Non-blocking), dùng thuật toán **Lock-free** để đạt tốc độ cực cao.

## 3. Bảng so sánh ưu điểm vượt trội

| Đặc điểm | Collections truyền thống | Concurrent Collections |
| :--- | :--- | :--- |
| **An toàn luồng** | Không (hoặc dùng khóa cứng) | Có (dùng khóa mềm/tối ưu) |
| **Hiệu năng** | Thấp khi có nhiều luồng | Rất cao (Scalability tốt) |
| **Trạng thái duyệt** | Dễ gây lỗi (Fail-fast) | An toàn (Fail-safe) |
| **Cơ chế** | Pessimistic Locking (Khóa bi quan) | Optimistic / Fine-grained Locking |

## 4. Cách áp dụng vào dự án `java-learning`

| Collection thường | Old School (Chậm/Khóa cứng) | Concurrent (Hiện đại/Nhanh) |
| :--- | :--- | :--- |
| **ArrayList** | `Vector` / `SynchronizedList` | `CopyOnWriteArrayList` |
| **HashMap** | `Hashtable` / `SynchronizedMap` | `ConcurrentHashMap` |
| **TreeMap** | `SynchronizedSortedMap` | `ConcurrentSkipListMap` |
| **HashSet** | `SynchronizedSet` | `CopyOnWriteArraySet` |
| **LinkedList** | `Collections.synchronizedList` | `ConcurrentLinkedQueue` / `Deque` |

### 4.1. Tại sao nên bỏ qua "Old School"?

Các bản an toàn kiểu cũ (`Vector`, `Hashtable`) hoặc các Wrapper (`SynchronizedList`) hoạt động dựa trên cơ chế **Pessimistic Locking**:
* **Cơ chế:** Khóa toàn bộ cấu trúc dữ liệu cho mỗi thao tác (ngay cả khi chỉ đọc).
* **Nhược điểm:** Hiệu năng giảm theo hàm mũ khi số lượng luồng tăng lên.
* **Lỗi tiềm ẩn:** Vẫn có thể gây ra `ConcurrentModificationException` khi vừa duyệt vừa sửa.

### 4.2. Quy tắc "Vàng" khi chuyển đổi

Dựa trên kinh nghiệm xử lý backend trong `java-learning`, hãy áp dụng quy tắc sau:

1.  **Nếu dữ liệu thay đổi liên tục (High-frequency Write):** Hãy chọn `ConcurrentHashMap` hoặc `ConcurrentLinkedQueue`. Tránh dùng nhóm `CopyOnWrite` vì chi phí nhân bản mảng rất lớn.
2.  **Nếu dữ liệu chủ yếu là đọc (Read-heavy):** `CopyOnWriteArrayList` là sự lựa chọn số 1 vì nó cho phép các luồng đọc không bao giờ phải đợi.
3.  **Nếu cần dữ liệu có thứ tự (Sorted):** Đừng cố dùng `SynchronizedTreeMap`, hãy dùng `ConcurrentSkipListMap` vì nó sử dụng thuật toán **SkipList** cho hiệu năng cực tốt trong môi trường đa luồng.

---

> **Tóm lại:** Concurrent Collections là sự kết hợp giữa Cấu trúc dữ liệu và Thuật toán đồng bộ hóa tối ưu. Nó giúp mã nguồn đa luồng sạch hơn, nhanh hơn và hạn chế lỗi Deadlock.

---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="synchronization-with-concurrent-collection">Concurrent Collections và Nghệ thuật Đồng bộ hóa Thông minh</a>
<details>
<summary>Click for details</summary>


Nhiều người lầm tưởng `Concurrent Collections` không dùng `Synchronization`, nhưng thực tế chúng dùng các kỹ thuật đồng bộ hóa hiện đại hơn nhiều so với từ khóa `synchronized` truyền thống.

## 1. Ba chiến thuật Đồng bộ hóa Hiện đại

Thay vì "khóa toàn bộ cửa", các class này sử dụng các chiến thuật điều phối tinh vi:

### A. Lock Striping (Phân đoạn khóa)
* **Cơ chế:** Chia nhỏ cấu trúc dữ liệu thành nhiều phần (segment). Mỗi phần có một khóa riêng.
* **Đại diện:** `ConcurrentHashMap` (Java 7 về trước).
* **Lợi ích:** Luồng A ghi vào Segment 1, luồng B vẫn có thể ghi vào Segment 2 cùng lúc mà không phải đợi nhau.

### B. Copy-On-Write (Sao chép khi ghi)
* **Cơ chế:** Dữ liệu hiện tại được coi là bất biến (**Immutability**). Luồng Đọc không cần khóa. Luồng Ghi sẽ tạo ra một bản sao mới, sửa đổi trên đó rồi tráo đổi bản sao này với bản gốc.
* **Đại diện:** `CopyOnWriteArrayList`, `CopyOnWriteArraySet`.
* **Lợi ích:** Cực nhanh cho các hệ thống "đọc nhiều, ghi ít".

### C. Non-blocking & CAS (Compare-And-Swap)
* **Cơ chế:** Đây là kỹ thuật **Lock-free**. Nó sử dụng các lệnh nguyên tử (atomic) của CPU để so sánh: *"Nếu giá trị hiện tại là X, hãy đổi nó thành Y"*. Nếu giá trị đã bị luồng khác đổi, nó sẽ thử lại (retry) thay vì ngủ đông.
* **Đại diện:** `ConcurrentLinkedQueue`, `AtomicInteger`.

## 2. So sánh: Đồng bộ hóa Truyền thống vs. Hiện đại

| Đặc điểm | `synchronized` (Truyền thống) | Concurrent Collections (Hiện đại) |
| :--- | :--- | :--- |
| **Cơ chế** | **Pessimistic Locking** (Khóa bi quan) | **Optimistic / Fine-grained** (Khóa lạc quan/nhỏ) |
| **Hiệu năng** | Thấp (Các luồng phải xếp hàng đợi) | Cao (Nhiều luồng làm việc song song) |
| **Nguy cơ** | Dễ gây lỗi Deadlock | Hạn chế tối đa Deadlock |
| **Tác động CPU** | Luồng bị treo (Context switch tốn phí) | Luồng vẫn chạy (CAS dùng vòng lặp hiệu quả) |

## 3. Tại sao chọn Concurrent Collections?

Trong dự án `java-learning`, việc chuyển từ `synchronized` sang `Concurrent Collections` giúp hệ thống của bạn:
1.  **Scalability:** Tận dụng tối đa sức mạnh của CPU đa nhân.
2.  **Safety:** Loại bỏ các lỗi phổ biến như `ConcurrentModificationException`.
3.  **Liveness:** Đảm bảo hệ thống luôn có tiến triển (không bị treo cứng vì một luồng giữ khóa quá lâu).

---

> **Tóm tắt tư duy:** > `synchronized` là một cái cổng kiểm soát duy nhất.
> `Concurrent Collections` là một hệ thống làn đường tự động hóa.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="manual-lock-and-concurrent-collection">Phân biệt Manual Locks vs. Concurrent Collections</a>
<details>
<summary>Click for details</summary>


Trong hệ sinh thái Java Concurrency, cả hai đều thuộc gói `java.util.concurrent` nhưng phục vụ những mục đích khác nhau ở các cấp độ khác nhau.

## 1. Mối quan hệ: Nguyên liệu vs. Thành phẩm

Hãy tưởng tượng về việc xây dựng một khu vực an toàn:
* **Locks (ReentrantLock, ReadWriteLock, StampedLock):** Là gạch, xi măng và những chiếc ổ khóa rời. Bạn phải tự tay thiết kế và xây dựng vùng an toàn (Critical Section) bằng cách gọi `lock()` và `unlock()`.
* **Concurrent Collections (ConcurrentHashMap, BlockingQueue):** Là những "căn phòng an toàn" đã được xây sẵn. Các kỹ sư Java đã sử dụng Locks và cơ chế CAS để bảo vệ dữ liệu bên trong cho bạn.

## 2. So sánh vị trí trong "Bản đồ Concurrency"

| Tiêu chí | Nhóm Locks (Manual) | Nhóm Concurrent Collections (Managed) |
| :--- | :--- | :--- |
| **Đại diện** | `ReentrantLock`, `ReadWriteLock`, `StampedLock` | `ConcurrentHashMap`, `CopyOnWriteArrayList` |
| **Cách dùng** | Phải tự gọi `lock()` / `try-finally` / `unlock()`. | Chỉ việc gọi `put()`, `get()`. Việc khóa được xử lý ngầm. |
| **Độ linh hoạt** | **Rất cao**: Có thể khóa bất cứ logic nghiệp vụ nào. | **Thấp**: Chỉ bảo vệ cấu trúc dữ liệu bên trong nó. |
| **Độ an toàn** | Dễ sai sót (Quên `unlock` gây Deadlock). | Rất an toàn, khó gây lỗi logic đồng bộ. |

## 3. Khi nào dùng cái nào? (Chiến thuật lựa chọn)

Trong dự án `java-learning`, việc lựa chọn phụ thuộc vào **phạm vi bảo vệ**:

* **Dùng Concurrent Collections khi:** Bạn chỉ cần lưu trữ và truy cập dữ liệu an toàn.
    * *Ví dụ:* Lưu danh sách User đang online. Bạn chỉ cần đẩy vào `ConcurrentHashMap`.
* **Dùng Locks khi:** Bạn cần bảo vệ một **chuỗi hành động (Atomicity)** phức tạp.
    * *Ví dụ:* Rút tiền ngân hàng. Quy trình (1) Kiểm tra số dư $\rightarrow$ (2) Trừ tiền $\rightarrow$ (3) Ghi Log phải được bọc trong một `Lock` để đảm bảo không có luồng nào xen vào giữa, ngay cả khi bạn dùng Map an toàn.

## 4. Tầm quan trọng của ReadWriteLock và StampedLock

Hai loại này có tư duy rất gần với cách các Concurrent Collections tối ưu hiệu năng:
1. **ReadWriteLock:** Tách biệt khóa Đọc (nhiều luồng) và khóa Ghi (độc quyền).
2. **StampedLock (Java 8+):** Cung cấp cơ chế **Optimistic Reading** (Đọc lạc quan). Nó giả định không có ai ghi để cho phép đọc cực nhanh, sau đó mới kiểm tra lại tính đúng đắn. Đây là kỹ thuật giúp đạt hiệu năng tương đương với các Concurrent Collections hiện đại.

---

> **Tóm tắt tư duy:**
> * Cần chứa dữ liệu an toàn? $\rightarrow$ **Concurrent Collections**.
> * Cần bảo vệ logic nghiệp vụ (nhiều bước)? $\rightarrow$ **Manual Locks**.

---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="concurrent-collection-method">Các phương thức trong Concurrent Collections</a>
<details>
<summary>Click for details</summary>


Toàn bộ các lớp trong gói `java.util.concurrent` được thiết kế để giải phóng lập trình viên khỏi việc sử dụng `synchronized` thủ công, đồng thời tối ưu hóa hiệu năng thông qua các cơ chế đồng bộ hóa hiện đại.

## 1. Nhóm Map & Set (Dựa trên Hash & SkipList)

### ConcurrentHashMap
* **Định nghĩa:** Bảng băm hỗ trợ truy cập song song hoàn toàn cho việc đọc và hiệu suất cao cho việc ghi nhờ kỹ thuật **Lock Striping**.
* **Kịch bản:** Lựa chọn mặc định để làm **Local Cache** hoặc lưu trữ trạng thái dùng chung (Shared State) trong các Service.

### ConcurrentSkipListMap & Set
* **Định nghĩa:** Phiên bản an toàn luồng của `TreeMap` và `TreeSet`, dựa trên cấu trúc dữ liệu **Skip List**.
* **Kịch bản:** Khi bạn cần dữ liệu vừa an toàn đa luồng, vừa phải được **sắp xếp theo thứ tự** (ví dụ: Danh sách tác vụ theo thời gian hết hạn).

## 2. Nhóm Copy-On-Write (Sao chép khi ghi)

**Cơ chế:** Khi có thay đổi, hệ thống tạo bản sao mới của mảng. Các luồng Đọc luôn làm việc trên bản gốc bất biến, không bao giờ bị chặn.

* **CopyOnWriteArrayList:** Dùng cho danh sách các **Observer**, **EventListener** hoặc các cấu hình hệ thống (ít thay đổi, đọc liên tục).
* **CopyOnWriteArraySet:** Dùng cho danh sách các quyền hạn (**Permissions**) hoặc danh sách các Server khả dụng trong Cluster.

## 3. Nhóm Queue (Hàng đợi tốc độ cao)

### ConcurrentLinkedQueue
* **Định nghĩa:** Hàng đợi **không chặn** (Non-blocking) sử dụng thuật toán **Lock-Free** dựa trên lệnh CAS (Compare-And-Swap) của CPU.
* **Kịch bản:** Khi cần tốc độ cực cao để trao đổi dữ liệu mà không muốn luồng bị "ngủ" (Blocked). Thích hợp cho hệ thống thu thập Log hoặc xử lý Event thời gian thực.

---

## Tổng kết chiến thuật chọn lựa (Cheat Sheet)

| Nếu bạn cần... | Hãy chọn... |
| :--- | :--- |
| **Map an toàn, hiệu năng cao nhất** | `ConcurrentHashMap` |
| **Map/Set được sắp xếp thứ tự** | `ConcurrentSkipListMap` / `Set` |
| **List/Set cực ít thay đổi, đọc cực nhiều** | `CopyOnWriteArrayList` / `Set` |
| **Queue tốc độ cao, không chặn (Non-blocking)** | `ConcurrentLinkedQueue` |
| **Queue có khả năng chờ (Blocking)** | `LinkedBlockingQueue` / `ArrayBlockingQueue` |

## Tầm quan trọng với Synchronization

Tất cả các lớp trên đều thực hiện **Managed Synchronization**. Thay vì bạn phải dùng `ReentrantLock` bao bọc bên ngoài, các lớp này tự thực hiện việc đó bên trong bằng cách sử dụng:
1.  **Biến `volatile`**: Đảm bảo tính hiển thị của dữ liệu giữa các luồng.
2.  **Lệnh CAS**: Đảm bảo tính nguyên tử (Atomicity) mà không làm luồng bị treo.

</details>

- [Quay lại đầu trang](#back-to-top)