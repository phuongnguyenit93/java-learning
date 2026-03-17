# Atomic Variables: Sức mạnh của sự Nguyên tử và CAS

## <a id="atomic-basic">Khái niệm cơ bản của Atomic</a>

Trong Java, `Atomic Variables` (gói `java.util.concurrent.atomic`) cung cấp các thao tác an toàn đa luồng trên một biến đơn lẻ mà không cần dùng đến `synchronized` hay `Lock`. Đây là chìa khóa để đạt hiệu năng tối đa trong các bài toán về bộ đếm hoặc trạng thái.

## 1. Tại sao cần Atomic? (Vấn đề của thao tác `n++`)

Thao tác `count++` trông có vẻ đơn giản nhưng thực tế bao gồm 3 bước riêng biệt tại tầng CPU:
1. **Read:** Đọc giá trị từ RAM vào thanh ghi CPU.
2. **Modify:** Tăng giá trị thêm 1.
3. **Write:** Ghi giá trị mới ngược lại RAM.

Trong môi trường đa luồng, hiện tượng **Race Condition** xảy ra khi hai luồng cùng thực hiện bước 1 (đều đọc được số 5) và cùng ghi lại số 6, dẫn đến mất mát dữ liệu. `AtomicInteger` biến 3 bước này thành một thao tác **nguyên tử** (duy nhất và không thể bị chia cắt).

## 2. Nguyên lý CAS: "Lạc quan nhưng cẩn trọng"

Các biến Atomic hoạt động dựa trên thuật toán **CAS (Compare-And-Swap)** — một chiến thuật đồng bộ hóa không chặn (non-blocking).

* **V (Value):** Ô nhớ chứa giá trị hiện tại.
* **A (Expected):** Giá trị mà luồng tin rằng đang có ở ô nhớ đó.
* **B (New):** Giá trị mới mà luồng muốn ghi vào.

**Quy trình:** CPU chỉ ghi `B` vào `V` nếu giá trị tại `V` vẫn đúng bằng `A`. Nếu giá trị đã bị luồng khác đổi mất, luồng hiện tại sẽ thất bại, thực hiện đọc lại giá trị mới và **thử lại (retry)** trong một vòng lặp cực nhanh.

## 3. So sánh: Atomic vs. Synchronized

| Đặc điểm | Synchronized / Lock | Atomic Variables |
| :--- | :--- | :--- |
| **Triết lý** | **Pessimistic** (Khóa cửa cho chắc). | **Optimistic** (Làm trước, sai sửa sau). |
| **Hiệu năng** | Thấp hơn (do Context Switch luồng). | **Rất cao** (Thao tác trực tiếp trên CPU). |
| **Liveness** | Có nguy cơ bị Deadlock. | **Lock-free** (Không bao giờ Deadlock). |
| **Phạm vi** | Bảo vệ được cả một khối code phức tạp. | Chỉ dành cho **một biến đơn lẻ**. |

## 4. Kịch bản áp dụng thực tế (Dành cho java-learning)

* **Bộ đếm (Counters):** Đếm lượt truy cập API, đếm số lỗi hệ thống.
    * *Sử dụng:* `AtomicInteger`, `AtomicLong`.
* **ID Generator:** Tạo ID duy nhất cho Object/Transaction trong RAM mà không cần gọi Database.
* **Trạng thái hệ thống (Flags):** Đánh dấu trạng thái "đã sẵn sàng" hoặc "đang shutdown".
    * *Sử dụng:* `AtomicBoolean`.
* **Cập nhật tham chiếu đối tượng:** Thay đổi một cấu hình (Config object) một cách an toàn.
    * *Sử dụng:* `AtomicReference`.


## 5. Lưu ý quan trọng
Atomic Variables chỉ đảm bảo tính nguyên tử cho **một biến**. Nếu logic của bạn yêu cầu sự phối hợp giữa hai biến (Ví dụ: *"Nếu A = 1 thì đặt B = 2"*), bạn vẫn phải quay lại dùng `synchronized` hoặc `Lock` để đảm bảo toàn vẹn dữ liệu cho toàn bộ khối code đó.


## <a id="cas-trong-atomic">Giải mã CAS: Tại sao Atomic không cần Lock mà vẫn an toàn?</a>

Sự khác biệt lớn nhất giữa `synchronized` và `Atomic` là: Một bên chặn luồng bằng **Phần mềm (Software)**, một bên điều phối luồng bằng **Phần cứng (Hardware)**.

## 1. Bí mật: Lệnh đơn nguyên tử ở tầng CPU

Trong Java truyền thống, thao tác `n++` bị tách rời thành 3 bước. Nhưng với `Atomic`, bước quan trọng nhất — **Kiểm tra và Cập nhật** — được nén lại thành một lệnh duy nhất ở tầng vật lý của CPU (như lệnh `CMPXCHG` trên kiến trúc x86).

* **Cơ chế bảo vệ vật lý:** Khi CPU thực hiện lệnh CAS, nó sẽ kích hoạt cơ chế **Lock Cache Line** hoặc **Bus Locking**.
* **Tính bất khả xâm phạm:** Trong khoảnh khắc cực ngắn đó, không một nhân CPU nào khác có thể truy cập vào ô nhớ đó. Việc "Kiểm tra giá trị cũ" và "Ghi giá trị mới" diễn ra như một hành động duy nhất, không thể bị chia cắt.

## 2. Quy trình "Thử và Sai" (Optimistic Loop)

Thay vì nói "Dừng lại!", Atomic sử dụng chiến thuật "Tôi sẽ thử, nếu sai tôi làm lại".

1.  **Đọc (Read):** Luồng A đọc giá trị hiện tại (V=5).
2.  **Tính toán (Compute):** Luồng A tính giá trị mới (New=6).
3.  **Cập nhật (CAS):** Luồng A yêu cầu CPU: *"Đổi V thành 6 nếu V vẫn là 5"*.
4.  **Xử lý thất bại (Retry):** Nếu một luồng khác đã đổi V thành 10, CPU báo lỗi. Luồng A không bị treo, nó lập tức quay lại bước 1, đọc 10 và tính toán lại thành 11.

## 3. So sánh hình tượng: Cửa bảo vệ vs. Cửa xoay

| Đặc điểm | Synchronized (Lock) | Atomic (CAS) |
| :--- | :--- | :--- |
| **Hình tượng** | **Cửa có bảo vệ:** Chỉ 1 người qua, người khác đứng im xếp hàng. | **Cửa xoay tự động:** Bạn lao vào, vướng thì lùi lại rồi lao vào lại ngay. |
| **Trạng thái luồng** | **Blocked:** Luồng bị "ngủ đông", chờ được đánh thức. | **Active:** Luồng luôn vận động, liên tục thử lại. |
| **Chi phí** | **Cao**: Tốn tài nguyên để quản lý việc "ngủ/thức" (Context Switch). | **Thấp**: Không tốn chi phí quản lý luồng, chỉ tốn chu kỳ CPU để lặp. |


## 4. Khi nào "Phép màu" Atomic trở nên tốn kém?

Atomic không phải là "viên đạn bạc". Nó có một nhược điểm gọi là **High Contention**:
* Nếu có hàng nghìn luồng cùng tranh giành **một biến duy nhất**, các luồng sẽ rơi vào vòng lặp "thử lại" liên tục (Spinning).
* Lúc này, CPU sẽ bị đẩy lên rất cao chỉ để phục vụ việc lặp đi lặp lại.
* *Giải pháp:* Trong kịch bản này, Java 8 cung cấp `LongAdder` — một kỹ thuật chia nhỏ bộ đếm để giảm tranh chấp.

## 5. Tổng kết cho java-learning
* **Atomic an toàn vì:** Lệnh kiểm tra và ghi được CPU thực hiện nguyên tử.
* **Atomic nhanh vì:** Nó không bao giờ bắt luồng phải dừng lại và chờ đợi (Non-blocking).
* **Quy tắc:** Dùng Atomic cho các biến trạng thái hoặc bộ đếm đơn giản để đạt hiệu năng tối đa.

## <a id="software-lock-and-hardware-lock">Software Lock vs. Hardware Lock: Ai làm chủ cuộc chơi?</a>

Trong lập trình đa luồng, việc chọn "ai" là người quản lý sự đồng bộ sẽ quyết định trực tiếp đến độ trễ (latency) và khả năng chịu tải của hệ thống.

## 1. Khóa ở tầng Luồng (Software Lock - `synchronized`)

Đây là cơ chế quản lý bởi JVM và Hệ điều hành (OS).

* **Quy trình:** Kiểm tra khóa -> Chiếm khóa -> Thực thi -> Giải phóng.
* **Trạng thái Blocked:** Nếu khóa đã bị chiếm, luồng phải "ngủ đông". Hệ điều hành thực hiện **Context Switch** để nhường CPU cho luồng khác.
* **Chi phí:** Rất nặng nề vì việc "đánh thức" một luồng dậy tốn hàng nghìn chu kỳ CPU.
* **Hình tượng:** Giống như một cái cổng có bảo vệ. Bạn phải xuất trình thẻ, nếu không được vào thì phải ngồi ghế chờ cho đến khi bảo vệ gọi tên.



## 2. Khóa ở tầng CPU (Hardware Lock - `Atomic` / CAS)

Đây là cơ chế quản lý trực tiếp bởi Chip xử lý thông qua các tập lệnh nguyên tử.

* **Cơ chế:** Sử dụng tín hiệu điện tử để thực hiện **Bus Locking** hoặc **Cache Line Locking**.
* **Thời gian khóa:** Cực ngắn (vài nano giây), chỉ đủ để thực hiện một lệnh so sánh và ghi đè (`CMPXCHG`).
* **Trạng thái Active:** Luồng không bao giờ ngủ. Nếu thất bại, nó lập tức thử lại ngay (Spinning).
* **Hình tượng:** Giống như một cái cửa xoay tự động tốc độ cao. Bạn cứ lao vào, nếu vướng thì lùi lại một chút rồi lao vào lại ngay, không bao giờ đứng yên.



## 3. Bảng so sánh "Yết hầu" kỹ thuật

| Đặc điểm | Khóa ở Luồng (Java) | Khóa ở CPU (Atomic) |
| :--- | :--- | :--- |
| **Người quản lý** | JVM & OS (Phần mềm) | CPU (Phần cứng) |
| **Cơ chế chính** | Blocking (Chặn) | Non-blocking (Phi chặn) |
| **Chi phí tài nguyên** | Cao (Tốn tài nguyên quản lý luồng) | Rất thấp (Chỉ là một lệnh điện tử) |
| **Trạng thái luồng** | Luồng bị treo (Wait/Blocked) | Luồng luôn vận động (Active) |
| **Nguy cơ lỗi** | Có thể bị Deadlock | **Lock-free** (Không bao giờ Deadlock) |

## 4 Tại sao Atomic lại nhanh gấp 3-5 lần?

* Không có Context Switch: Với synchronized, khi một luồng không lấy được khóa, nó phải "đi ngủ" và chờ hệ điều hành đánh thức. Quá trình "ngủ-thức" này tốn rất nhiều tài nguyên.
* Lệnh CPU trực tiếp: Atomic dùng lệnh CAS. Nó không bao giờ bắt luồng phải dừng lại. Nếu thất bại, luồng chỉ đơn giản là lặp lại ngay lập tức (Spinning). Với các thao tác siêu nhanh như tăng số, việc lặp lại vẫn nhanh hơn rất nhiều so với việc đi ngủ.

## 5. Ví dụ thực tế trong `java-learning`

Giả sử có 2 luồng cùng muốn tăng `counter` từ **10** lên **11**:

1.  **Luồng A:** Gửi lệnh `CAS(ô_nhớ, 10, 11)`. CPU "chốt" đường truyền trong vài nano giây, thấy đúng là 10, đổi thành 11. **Thành công!**
2.  **Luồng B:** Gửi lệnh `CAS(ô_nhớ, 10, 11)` chậm hơn 1 phần tỷ giây. CPU kiểm tra thấy giá trị hiện tại đã là 11 (không phải 10). **Thất bại!**
3.  **Xử lý của Luồng B:** Không cần OS đánh thức, luồng B thấy thất bại sẽ tự động đọc lại giá trị mới (11) và gửi lệnh mới: `CAS(ô_nhớ, 11, 12)`.

## 6. Kết luận chiến thuật

* **Dùng `synchronized`:** Khi bạn cần bảo vệ một khối logic dài (ví dụ: trừ tiền, ghi log, gửi mail). Logic này quá dài để CPU có thể "chốt" giữ.
* **Dùng `Atomic`:** Khi bạn chỉ cần bảo vệ các biến số, bộ đếm, hoặc các cờ trạng thái (Flag) đơn giản. Ưu tiên tốc độ "bàn thờ".

## <a id="atomic-variable-list">Java Concurrency: Atomic Variables Summary </a>

## 1. Nhóm Cơ bản (Primitives)
Đây là những lớp hay dùng nhất, thay thế cho các kiểu dữ liệu nguyên thủy trong môi trường đa luồng.

| Tên lớp | Ý nghĩa & Ứng dụng |
| :--- | :--- |
| **AtomicInteger** | Quản lý số nguyên (int). Thường dùng làm bộ đếm (Counter), ID tự tăng. |
| **AtomicLong** | Quản lý số nguyên lớn (long). Dùng cho các giá trị tích lũy lớn hoặc thời gian (timestamp). |
| **AtomicBoolean** | Quản lý trạng thái true/false. Thường dùng làm cờ hiệu (Flag) để kiểm tra hệ thống. |

Ví dụ cách khai báo:
```java
private final AtomicInteger counter = new AtomicInteger(0);
```

## 2. Nhóm Đối tượng (References)
Dùng khi bạn muốn bảo vệ cả một Object hoặc giải quyết các vấn đề phức tạp về con trỏ.

| Tên lớp | Ý nghĩa & Ứng dụng |
| :--- | :--- |
| **AtomicReference<T>** | Bảo vệ một tham chiếu đối tượng. Dùng để cập nhật toàn bộ một bản ghi (State Object). |
| **AtomicStampedReference** | Kèm theo một số "version" (stamp). Giải quyết lỗi ABA trong thuật toán CAS. |
| **AtomicMarkableReference** | Kèm một biến boolean để đánh dấu (ví dụ: Node trong Linked List đã bị xóa hay chưa). |

## 3. Nhóm Mảng (Arrays)
Thay vì bảo vệ một biến, nhóm này bảo vệ từng phần tử bên trong một mảng.

| Tên lớp | Ý nghĩa & Ứng dụng |
| :--- | :--- |
| **AtomicIntegerArray** | Đảm bảo khi bạn cập nhật `array[i]`, nó sẽ an toàn mà không cần khóa cả mảng. |
| **AtomicLongArray** | Tương tự cho kiểu long. |
| **AtomicReferenceArray<E>** | Tương tự cho mảng các đối tượng. |

## 4. Nhóm Tốc độ cao (Adders & Accumulators)
Nhóm "chia để trị" (Striped64) dành cho môi trường cực nhiều luồng (High Contention).

| Tên lớp | Ý nghĩa & Ứng dụng |
| :--- | :--- |
| **LongAdder** | Tối ưu nhất cho việc cộng dồn số nguyên. Dùng cho thống kê (Metrics), đếm số Request. |
| **DoubleAdder** | Tối ưu cho cộng dồn số thực. Dùng cho tính tổng doanh thu, số dư. |
| **LongAccumulator** | Cho phép dùng hàm Lambda tùy biến (Max, Min, Nhân, GCD...) trên số nguyên. |
| **DoubleAccumulator** | Cho phép dùng hàm Lambda tùy biến trên số thực. |

## 5. Nhóm Cập nhật trường (Field Updaters)
Dùng Reflection để biến một biến bình thường (volatile) thành Atomic mà không cần thay đổi kiểu dữ liệu của biến đó.

| Tên lớp | Ý nghĩa & Ứng dụng |
| :--- | :--- |
| **AtomicIntegerFieldUpdater** | Cập nhật nguyên tử cho một trường int của một lớp. Tiết kiệm bộ nhớ. |
| **AtomicLongFieldUpdater** | Tương tự cho trường long. |
| **AtomicReferenceFieldUpdater** | Tương tự cho trường Object. |

---

### 💡 Lời khuyên
* Trong **90% trường hợp**, bạn sẽ chỉ cần dùng **Nhóm 1** và **Nhóm 4**.
* Nếu làm về **thuật toán cấu trúc dữ liệu** (như tự viết Lock-free Queue), bạn sẽ cần đến `AtomicStampedReference` để tránh lỗi ABA.
* Nếu cần **hiệu năng tối thượng** và tiết kiệm RAM (ví dụ viết Framework), hãy nghiên cứu **Nhóm 5**.

## <a id="long-adder">LongAdder: Phá vỡ giới hạn của AtomicLong</a>

Khi hệ thống đạt đến quy mô hàng nghìn luồng truy cập đồng thời vào một bộ đếm duy nhất, `AtomicLong` trở thành điểm nghẽn do hiện tượng tranh chấp cao (High Contention). `LongAdder` (Java 8+) ra đời để giải quyết bài toán này.

## 1. Vấn đề của AtomicLong: Hiện tượng "Xếp hàng lặp"

Trong môi trường cực nhiều luồng, `AtomicLong` gặp vấn đề:
* **Spinning:** 1000 luồng cùng CAS vào một ô nhớ. Chỉ 1 luồng thắng, 999 luồng còn lại phải chạy vòng lặp (Retry).
* **CPU Overhead:** CPU bị đẩy lên 100% chỉ để phục vụ việc "thử và sai" liên tục của các luồng thất bại.



## 2. LongAdder: Chiến thuật "Chia để trị" (Striped64)

`LongAdder` không bắt tất cả các luồng tranh giành một biến. Thay vào đó, nó sử dụng một cơ chế thông minh:

* **Mảng các Cells:** Khi phát hiện có tranh chấp, `LongAdder` tự động phân tách biến đếm thành một mảng các ô nhớ (Cells).
* **Điều hướng luồng:** Mỗi luồng sẽ được băm (hash) và điều hướng vào một Cell riêng biệt để thực hiện phép cộng.
* **Gom tụ (Summation):** Khi bạn gọi `sum()`, nó mới thực hiện phép cộng dồn tất cả các Cell lại với nhau để cho ra kết quả cuối cùng.



## 3. Bảng so sánh "Kèo đấu" Hiệu năng

| Đặc điểm | `AtomicLong` | `LongAdder` |
| :--- | :--- | :--- |
| **Cấu trúc** | 1 biến duy nhất (Single variable). | Mảng các biến chạy song song (**Striped 64**). |
| **Tranh chấp** | Cao khi nhiều luồng (High Contention). | **Cực thấp** (Mỗi luồng một "vòi bơm"). |
| **Hiệu năng** | Giảm mạnh khi số luồng tăng. | Giữ ổn định kể cả khi có hàng vạn luồng. |
| **Độ chính xác** | Chính xác tuyệt đối 100% tại mọi thời điểm. | `sum()` có thể hơi sai lệch nếu có luồng đang ghi lúc đang cộng. |
| **Sử dụng RAM** | Ít (chỉ 1 biến long). | Nhiều hơn (do phải duy trì mảng Cells). |

## 4. Khi nào dùng loại nào trong `java-learning`?

### ✅ Chọn `AtomicLong` khi:
* Cần độ chính xác tuyệt đối tại mọi thời điểm.
* Số lượng luồng tranh chấp không quá lớn.
* Dùng để tạo **Sequence ID** (Mã đơn hàng, mã giao dịch) vì ID không được phép trùng hay sai lệch dù chỉ 1 đơn vị.

### ✅ Chọn `LongAdder` khi:
* Cần bộ đếm với tần suất cập nhật cực cao (High-frequency update).
* Chấp nhận độ trễ nhỏ khi lấy tổng (sum).
* Dùng cho **Statistics / Metrics**: Đếm số lượng Request, đếm số lỗi hệ thống, đo băng thông (throughput).



## 5. Kết luận
`LongAdder` không thay thế `AtomicLong`. Nó là một công cụ chuyên dụng cho các bài toán thống kê ở quy mô lớn. Nếu dự án của bạn cần đo đạc Performance của hệ thống (Observability), `LongAdder` chính là "vũ khí" hạng nặng giúp bạn đạt được tốc độ bàn thờ.

## <a id="long-accumulator">Phân tích chuyên sâu: LongAdder và LongAccumulator</a>

Ngoài `LongAdder`, Java còn cung cấp một người anh em sinh đôi là `DoubleAdder` và hai phiên bản linh hoạt hơn là `LongAccumulator` và `DoubleAccumulator`.

## 1. LongAccumulator: "Bản nâng cấp" linh hoạt
Nếu `LongAdder` chỉ biết làm một việc duy nhất là "cộng thêm", thì nhóm **Accumulator** cho phép bạn thực hiện bất kỳ phép toán nào (nhân, chia, tìm số lớn nhất, nhỏ nhất...) một cách cực kỳ nhanh trong môi trường đa luồng.

`LongAccumulator` hoạt động giống hệt `LongAdder` (chia nhỏ dữ liệu ra các ô nhớ để tránh tranh chấp), nhưng nó cho phép bạn truyền vào một công thức (**Function**).

**Ví dụ: Tìm giá trị lớn nhất (Max) từ hàng ngàn luồng**
Nếu bạn dùng `AtomicLong` và lệnh `Math.max`, các luồng sẽ bị nghẽn (Spin) rất nhiều. `LongAccumulator` sẽ xử lý việc này hiệu quả hơn:

```java
	// Khởi tạo với công thức tìm Max và giá trị mặc định là 0
	LongAccumulator maxAccumulator = new LongAccumulator(Long::max, 0L);

	// Hàng ngàn luồng cùng đẩy giá trị vào
	maxAccumulator.accumulate(150);
	maxAccumulator.accumulate(300);

	// Lấy ra giá trị lớn nhất cuối cùng
	long maxValue = maxAccumulator.get(); 
```

## 2. DoubleAdder & DoubleAccumulator
Đây là phiên bản dành cho số thực (`double`). Trước Java 8, việc xử lý số thực nguyên tử rất khó khăn vì Java không có `AtomicDouble`.

* **DoubleAdder:** Dùng để cộng dồn doanh thu, số dư tài khoản (số thực) với tốc độ cao.
* **DoubleAccumulator:** Dùng để tính toán phức tạp hơn (nhân hệ số, tính thuế...) trên số thực một cách song song.

## 3. So sánh tổng quát
Dưới đây là bảng tra cứu nhanh để bạn chọn đúng "vũ khí" cho từng bài toán:

| Công cụ | Kiểu dữ liệu | Bài toán phù hợp |
| :--- | :--- | :--- |
| **AtomicInteger / Long** | Số nguyên | Cần độ chính xác tuyệt đối tại mọi thời điểm (vídụ: ID đơn hàng). |
| **LongAdder / DoubleAdder** | Số nguyên / thực | Chỉ cần kết quả tổng cuối cùng, ưu tiên tốc độ (ví dụ: Đếm lượt view). |
| **Long / DoubleAccumulator** | Số nguyên / thực | Cần thực hiện phép toán phức tạp (Max, Min, Nhân) trên dữ liệu khổng lồ. |

## 4. Bảng tóm tắt so sánh: LongAdder vs LongAccumulator

* Dưới đây là bảng tra cứu nhanh để bạn lưu trữ vào tài liệu hệ thống, giúp quyết định khi nào nên dùng công cụ nào trong môi trường đa luồng:

| Đặc điểm | LongAdder | LongAccumulator |
| :--- | :--- | :--- |
| **Phép toán** | Cố định: $x + y$ | Tùy biến thông qua Function: $f(x, y)$ |
| **Giá trị khởi tạo** | Mặc định là `0` | Tùy chọn theo nhu cầu (Identity value) |
| **Tốc độ** | Nhanh nhất cho các tác vụ cộng dồn | Chậm hơn một chút (do xử lý Lambda) nhưng vẫn rất nhanh |
| **Ứng dụng** | Thống kê, bộ đếm (Counter) đơn giản | Tìm Max/Min, tính tích, GCD, hoặc các công thức thuế phí |

## 5. Lưu ý về "Độ chính xác tức thời"
Trong dự án `java-learning`, bạn cần đặc biệt lưu ý sự đánh đổi (trade-off) này:

* **AtomicLong:** Khi gọi `.get()`, bạn luôn nhận được con số chính xác nhất ngay lúc đó.
* **LongAdder/Accumulator:** Khi gọi `.get()` hoặc `.sum()`, con số có thể **hơi chậm một chút** so với thực tế nếu các luồng khác vẫn đang miệt mài ghi dữ liệu vào các ô nhớ (cells) khác.

---
*Ghi chú: Nhóm này dựa trên cơ chế Striped64 để giảm thiểu tranh chấp (contention).*