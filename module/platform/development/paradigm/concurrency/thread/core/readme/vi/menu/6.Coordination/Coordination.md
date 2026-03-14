<a id="back-to-top"></a>

# Coordination - Điều phối

## Menu
- [1. Phân biệt Synchronization (Đồng bộ) và Coordination (Điều phối)](#coordination)
- [2. Điều phối luồng cơ bản: join(), wait(), notify() và notifyAll()](#coordination-classic)
- [3. Điều phối hiện đại: Condition và LockSupport](#modern-coordination)
- [4. Exchanger: "Điểm hẹn" trao đổi dữ liệu giữa hai luồng](#exchanger)
- [5. Mẫu thiết kế Producer–Consumer (Người sản xuất – Người tiêu dùng)](#producer-consumer-pattern)

## <a id="coordination">1. Phân biệt Synchronization (Đồng bộ) và Coordination (Điều phối)</a>
<details>
<summary>Click for details</summary>


Trong lập trình đa luồng, hai khái niệm này thường đi đôi nhưng mục đích của chúng hoàn toàn khác nhau. Việc hiểu rõ sự khác biệt này sẽ giúp bạn tổ chức code trong dự án `java-learning` một cách logic và chuyên nghiệp.

Để dễ hiểu nhất, hãy tưởng tượng một **Đội cứu hỏa**:
* **Synchronization (Tranh chấp):** Đảm bảo tại một thời điểm, chỉ có một người được cầm vòi phun nước. Nó tập trung vào việc **bảo vệ tài nguyên**.
* **Coordination (Hợp tác):** Đảm bảo rằng người cầm vòi chỉ phun nước *sau khi* người ở xe cứu hỏa đã bật van bơm. Nó tập trung vào việc **phối hợp thứ tự**.

---

### 1. Synchronization (Bảo vệ tài nguyên)
Mục tiêu chính là **Mutual Exclusion** (Loại trừ lẫn nhau). Nó giải quyết vấn đề: *"Làm sao để các luồng không dẫm chân lên nhau khi cùng sửa một biến?"*

* **Trạng thái:** Các luồng đối đầu nhau (Competitive).
* **Công cụ:** `synchronized`, `ReentrantLock`, `ReadWriteLock`, `StampedLock`.
* **Vấn đề giải quyết:** Race Condition, Data Inconsistency.

---

### 2. Coordination (Phối hợp thứ tự)
Mục tiêu chính là **Signaling** (Báo hiệu). Nó giải quyết vấn đề: *"Làm sao để luồng A đợi luồng B làm xong rồi mới chạy tiếp?"*

* **Trạng thái:** Các luồng hợp tác với nhau (Cooperative).
* **Công cụ:** `wait()` / `notify()`, `CountDownLatch`, `CyclicBarrier`, `Semaphore`, `Phaser`.
* **Vấn đề giải quyết:** Thread Dependency, Execution Order (Thứ tự thực thi).

---

### 3. Bảng so sánh chi tiết

| Đặc điểm | Synchronization | Coordination |
| :--- | :--- | :--- |
| **Câu hỏi chính** | Ai được quyền truy cập bây giờ? | Khi nào tôi có thể bắt đầu làm? |
| **Mối quan hệ** | Luồng này chặn luồng kia. | Luồng này đợi luồng kia báo hiệu. |
| **Từ khóa chính** | Khóa (Locking), Độc quyền (Exclusive). | Chờ (Waiting), Tín hiệu (Signaling). |
| **Ví dụ thực tế** | Hai người cùng rút tiền từ một ATM. | Bếp nấu xong thì phục vụ mới bưng món. |

---

### 4. Tại sao chúng thường đi cùng nhau?
Trong thực tế, **Coordination** thường dựa trên nền tảng của **Synchronization** để hoạt động an toàn.

**Ví dụ điển hình:** Để dùng `wait()` và `notify()`, bạn bắt buộc phải nằm trong khối `synchronized`.
1.  Bạn dùng `synchronized` để lấy quyền kiểm soát "bộ đàm" (**Synchronization**).
2.  Sau đó bạn dùng `wait()` để thông báo: *"Tôi đang đợi cơm, ai nấu xong thì gọi nhé"* (**Coordination**).

---

### Tổng kết cho dự án `java-learning`
* Nếu bạn muốn bảo vệ một biến `count` hoặc một `ArrayList`: Hãy tìm đến **Synchronization**.
* Nếu bạn muốn luồng chính đợi 10 luồng con xử lý xong dữ liệu rồi mới tổng hợp kết quả: Hãy tìm đến **Coordination** (ví dụ: `CountDownLatch`).

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="coordination-classic">2. Điều phối luồng cơ bản: join(), wait(), notify() và notifyAll()</a>
<details>
<summary>Click for details</summary>


Trong hệ thống **Coordination (Điều phối)**, các phương thức này đóng vai trò là những "tín hiệu giao thông" cơ bản nhất để thiết lập thứ tự thực thi giữa các luồng. Việc nắm vững chúng là nền tảng để hiểu các bộ điều phối cao cấp hơn trong Java.

---

### 1. join(): Vai trò là "Rào chắn kết thúc" (Termination Barrier)
`join()` là cách đơn giản nhất để một luồng phối hợp với vòng đời của một luồng khác.

* **Vai trò:** Cho phép luồng hiện tại (thường là luồng cha) tạm dừng và chờ cho đến khi một luồng cụ thể khác (luồng con) kết thúc hoàn toàn công việc của nó.
* **Ứng dụng:** Dùng khi kết quả của bước tiếp theo phụ thuộc hoàn toàn vào việc bước trước đó phải chạy xong.
* **Hình ảnh minh họa:** Bạn đợi shipper giao hàng xong rồi mới có thể khui quà. Bạn không thể khui quà khi shipper vẫn đang trên đường đi.

---

### 2. wait(): Vai trò là "Trạng thái chờ điều kiện" (Condition Waiting)
`wait()` cho phép một luồng tự đưa mình vào trạng thái "ngủ đông" để nhường tài nguyên cho luồng khác.

* **Vai trò:** Tạm thời giải phóng khóa (**Monitor Lock**) mà luồng đang giữ và đứng vào danh sách chờ (**Wait-set**). Luồng sẽ không làm gì cả cho đến khi điều kiện logic mà nó mong đợi được thỏa mãn.
* **Ứng dụng:** Dùng trong mô hình Producer-Consumer. Khi kho hàng trống, luồng Consumer gọi `wait()` để không chiếm dụng CPU vô ích.
* **Hình ảnh minh họa:** Bạn vào quán ăn nhưng hết bàn, bạn đứng sang một bên đợi (nhường lối đi cho khách khác ra về) cho đến khi nhân viên gọi tên bạn.

---

### 3. notify() và notifyAll(): Vai trò là "Bộ phát tín hiệu" (Signaling)
Đây là những lệnh "đánh thức" các luồng đang nằm trong trạng thái `wait()`.

* **`notify()`:** Đánh thức một luồng ngẫu nhiên đang đợi trên đối tượng đó. Nó giống như việc nhân viên quán ăn chỉ gọi duy nhất một người đang đợi vào bàn.
* **`notifyAll()`:** Đánh thức tất cả các luồng đang đợi. Đây là cách điều phối an toàn hơn, tránh việc đánh thức nhầm hoặc bỏ sót luồng. Sau khi thức dậy, các luồng sẽ tranh chấp khóa để kiểm tra lại điều kiện.
* **Vai trò:** Thông báo rằng: *"Tình trạng tài nguyên đã thay đổi, các anh hãy tỉnh dậy và kiểm tra lại đi!"*.
* **Hình ảnh minh họa:** Khi có bàn trống, nhân viên hét lớn: *"Có bàn rồi!"* để tất cả những người đang đứng đợi biết và tiến vào.

---

### Tóm tắt mối quan hệ phối hợp

| Phương thức | Luồng gọi nó sẽ làm gì? | Mục đích phối hợp |
| :--- | :--- | :--- |
| **`join()`** | Bị chặn (Blocked) | Đợi một luồng khác kết thúc hoàn toàn. |
| **`wait()`** | Giải phóng khóa & Ngủ | Đợi một tín hiệu/điều kiện từ luồng khác. |
| **`notify(All)`** | Tiếp tục chạy | Gửi tín hiệu để đánh thức luồng khác dậy. |

---

### Ví dụ phối hợp thực tế (The Sequence)

1.  **Main** tạo ra một luồng **Worker**.
2.  **Worker** bắt đầu chạy nhưng ngay lập tức gọi `wait()` vì chưa có dữ liệu đầu vào từ Main.
3.  **Main** chuẩn bị dữ liệu xong, gọi `notify()` để "đánh thức" **Worker**.
4.  **Main** tiếp tục gọi `Worker.join()` để đảm bảo **Worker** xử lý xong xuôi dữ liệu đó thì **Main** mới in báo cáo cuối cùng ra màn hình.

> **Lưu ý quan trọng:** Bạn chỉ có thể gọi `wait()` và `notify()` khi đang giữ khóa của đối tượng đó (nằm trong khối `synchronized`), nếu không Java sẽ ném ra `IllegalMonitorStateException`.

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="modern-coordination">3. Điều phối hiện đại: Condition và LockSupport</a>
<details>
<summary>Click for details</summary>


Trong hệ sinh thái đa luồng của Java, nếu `wait`/`notify` là những công cụ cổ điển, thì **Condition** và **LockSupport** là những công cụ hiện đại, linh hoạt hơn, nằm trong gói `java.util.concurrent`. Chúng giúp tối ưu hóa hiệu năng và độ chính xác khi điều phối các luồng trên chip đa nhân i7.

---

### 1. Condition (Điều kiện)
`Condition` là một giao diện đi kèm với `Lock` (thường là `ReentrantLock`). Nó thay thế cho các phương thức của `Object` (`wait`, `notify`).

* **Điểm mạnh vượt trội:** Một `Lock` có thể có **nhiều `Condition`**.
* **Vấn đề của `wait`/`notify`:** Tất cả luồng (cả người mua và người bán) đều ngủ chung một danh sách (`Wait-set`). Khi gọi `notifyAll`, tất cả cùng dậy, gây ra hiện tượng tranh chấp lãng phí tài nguyên.
* **Giải pháp với `Condition`:** Bạn có thể tách ra thành `conditionNguoiMua` và `conditionNguoiBan`. Khi có hàng, bạn chỉ cần gọi `conditionNguoiMua.signal()`, những người bán vẫn tiếp tục ngủ ngon, giúp hệ thống hoạt động cực kỳ chính xác.

**Các hàm tương ứng:**

| Legacy (Object) | Modern (Condition) |
| :--- | :--- |
| `wait()` | `await()` |
| `notify()` | `signal()` |
| `notifyAll()` | `signalAll()` |

---

### 2. LockSupport
`LockSupport` là một lớp tiện ích cung cấp các phương thức tĩnh để tạm dừng (`park`) và đánh thức (`unpark`) một luồng. Đây là nền tảng cực thấp (low-level) mà Java dùng để xây dựng nên các loại Lock hiện đại.

* **Cơ chế "Giấy phép" (Permit):** `LockSupport` hoạt động dựa trên một loại giấy phép nhị phân (0 hoặc 1).
    * `park()`: Nếu có giấy phép (1), nó tiêu thụ giấy phép và đi tiếp. Nếu không (0), nó đứng lại đợi.
    * `unpark(thread)`: Cấp 1 giấy phép cho luồng chỉ định.
* **Điểm khác biệt cực lớn:**
    * **Không cần `synchronized`:** Có thể gọi ở bất cứ đâu.
    * **Thứ tự gọi không quan trọng:** Nếu bạn gọi `unpark` trước khi luồng đó kịp `park`, luồng đó sẽ **không bao giờ bị dừng lại** (vì đã có sẵn giấy phép). Với `wait`, nếu bạn `notify` trước, tín hiệu sẽ bị mất và luồng sẽ ngủ mãi mãi.
    * **Đánh thức chính xác:** Yêu cầu truyền vào chính xác đối tượng `Thread` cần đánh thức.

---

### 3. So sánh tổng hợp

| Đặc điểm | wait / notify | Condition | LockSupport |
| :--- | :--- | :--- | :--- |
| **Gắn liền với** | Object & `synchronized` | `ReentrantLock` | Đối tượng `Thread` cụ thể |
| **Số lượng Wait-set** | Duy nhất 1 | Nhiều (tùy biến) | Không có (dùng permit) |
| **Độ linh hoạt** | Thấp | Cao | Rất cao (Low-level) |
| **Độ chính xác (Ai thức dậy?)** | **Thấp nhất.** `notify()` chọn ngẫu nhiên. `notifyAll()` gọi cả "làng" dậy rồi tranh giành. | **Trung bình - Cao.** Đánh thức đúng nhóm luồng đang đợi (VD: Nhóm đợi Xăng, nhóm đợi Tiền). | **Cao nhất.** Đánh thức đích danh 01 Thread cụ thể qua tham chiếu đối tượng luồng. |
| **Thứ tự gọi (Notify trước Wait)** | **Thất bại.** Tín hiệu biến mất nếu không có ai đang đợi. Luồng sẽ ngủ mãi (Deadlock). | **Thất bại.** Tín hiệu `signal()` phát ra mà không có ai nghe sẽ bị trôi mất. | **Thành công.** Nhờ cơ chế **Permit**, `unpark` trước thì khi gọi `park` luồng sẽ đi tiếp luôn. |
| **Độ linh hoạt** | Thấp | Cao | Rất cao (Low-level) |
| **Dùng khi nào?** | Học cơ bản, App đơn giản | Logic phức tạp, hiệu năng | Tự viết Custom Lock/Framework |

---

### 4. Ví dụ Code thực tế

#### Với Condition (Phối hợp chính xác)

```java
    Lock lock = new ReentrantLock();
    Condition xangDay = lock.newCondition();

	// Luồng xe:
	lock.lock();
	try {
	    xangDay.await(); // Đợi đúng khi nào có tín hiệu "xăng đầy" mới dậy
	} finally { lock.unlock(); }

	// Luồng nhân viên:
	lock.lock();
	try {
	    xangDay.signal(); // Chỉ đánh thức những xe đang đợi xăng
	} finally { lock.unlock(); }
```

#### Với LockSupport (An toàn về thứ tự)

```java
    Thread t1 = new Thread(() -> {
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        System.out.println("T1 chuẩn bị gọi park...");
        LockSupport.park(); // Sẽ KHÔNG bị dừng vì Main đã cấp permit từ trước
        System.out.println("T1 chạy tiếp ngay lập tức!");
    });

        t1.start();
        LockSupport.unpark(t1); // Cấp giấy phép trước khi T1 kịp park
```

---

**Tổng kết:** Sử dụng `Condition` khi bạn cần logic nghiệp vụ phức tạp và `LockSupport` khi bạn cần can thiệp sâu vào vòng đời của Thread.

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="exchanger">4. Exchanger: "Điểm hẹn" trao đổi dữ liệu giữa hai luồng</a>
<details>
<summary>Click for details</summary>


Trong hệ sinh thái **Coordination**, `Exchanger` là một công cụ cực kỳ thú vị và có mục đích rất đặc thù. Nó không dùng để "báo hiệu" đơn thuần như `wait`/`notify` hay "chặn" như `LockSupport`, mà dùng để **trao đổi dữ liệu một cách đồng bộ** giữa đúng hai luồng.

Bạn có thể coi nó là một **điểm hẹn (Rendezvous)** mà tại đó, hai luồng gặp nhau, trao cho nhau món đồ mình đang cầm và sau đó cả hai cùng tiếp tục công việc.

---

### 1. Cơ chế hoạt động: "Tiền trao cháo múc"
Exchanger hoạt động theo nguyên tắc đồng bộ hóa hai chiều:
1.  **Luồng A** gọi `exchange(objectA)`: Nó sẽ bị chặn (block) lại và đứng đợi.
2.  **Luồng B** gọi `exchange(objectB)`: Xuất hiện tại điểm hẹn.
3.  **Trao đổi:** Ngay lập tức, Luồng A nhận được `objectB`, và Luồng B nhận được `objectA`.
4.  **Tiếp tục:** Cả hai cùng "tỉnh dậy" và đi tiếp.

---

### 2. Ví dụ thực tế: Hệ thống Đổi tiền (Currency Exchange)
Hãy tưởng tượng kịch bản: Một luồng đại diện cho **Khách hàng** (cầm USD) và một luồng đại diện cho **Quầy giao dịch** (cầm VND).

```java
import java.util.concurrent.Exchanger;

	public class ExchangeDemo {
	    public static void main(String[] args) {
	        Exchanger<String> exchanger = new Exchanger<>();

	        // Luồng Khách hàng
	        new Thread(() -> {
	            try {
	                String moneyInHand = "100 USD";
	                System.out.println("Khách: Tôi có " + moneyInHand + ", đang đợi đổi...");

	                // Đổi 100 USD lấy tiền từ Quầy
	                String moneyReceived = exchanger.exchange(moneyInHand);

	                System.out.println("Khách: Đã nhận được " + moneyReceived);
	            } catch (InterruptedException e) { e.printStackTrace(); }
	        }, "Customer").start();

	        // Luồng Quầy giao dịch
	        new Thread(() -> {
	            try {
	                Thread.sleep(2000); // Giả lập nhân viên đang đếm tiền VND
	                String moneyInHand = "2.500.000 VND";
	                System.out.println("Quầy: Chúng tôi đã chuẩn bị xong " + moneyInHand);

	                // Đổi VND lấy USD từ Khách
	                String moneyReceived = exchanger.exchange(moneyInHand);

	                System.out.println("Quầy: Đã nhận được " + moneyReceived + " từ khách.");
	            } catch (InterruptedException e) { e.printStackTrace(); }
	        }, "Bank").start();
	    }
	}
```

---

### 3. Khi nào thì dùng Exchanger?
Đây là công cụ rất hẹp về công dụng, thường dùng trong các bài toán tối ưu hóa trên chip đa nhân i7:

* **Buffer Swapping (Hoán đổi vùng đệm):** Một luồng chuyên đổ dữ liệu vào Buffer A, một luồng chuyên đọc từ Buffer B. Khi cả hai xong việc, chúng hoán đổi Buffer cho nhau. Điều này cực kỳ hiệu quả vì không mất chi phí copy dữ liệu.
* **Genetic Algorithms (Giải thuật di truyền):** Khi hai cá thể gặp nhau tại một thời điểm để trao đổi gen (Crossover).
* **Pipeline 2 giai đoạn:** Phối hợp dữ liệu giữa hai bước xử lý song song cần tính nhất quán cao.

---

### 4. Đặc điểm quan trọng cần lưu ý

| Đặc điểm | Chi tiết |
| :--- | :--- |
| **Số lượng luồng** | Chỉ hoạt động theo **cặp (02 luồng)**. Nếu luồng thứ 3 gọi `exchange`, nó sẽ đợi cho đến khi có luồng thứ 4 xuất hiện. |
| **Tính đồng bộ** | Đây là cơ chế **Blocking**. Luồng đến trước bắt buộc phải đợi luồng đến sau. |
| **Hướng dữ liệu** | **Hai chiều (Bidirectional)**. Luồng gửi đi và đồng thời nhận lại. |
| **An toàn** | Luồng dữ liệu đi qua Exchanger được đảm bảo an toàn đa luồng (**Thread-safe**). |

---

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="producer-consumer-pattern">5. Mẫu thiết kế Producer–Consumer (Người sản xuất – Người tiêu dùng)</a>
<details>
<summary>Click for details</summary>


**Producer–Consumer** là một mẫu thiết kế (design pattern) kinh điển giải quyết bài toán: Làm sao để hai nhóm luồng có tốc độ làm việc khác nhau có thể phối hợp nhịp nhàng mà không gây tắc nghẽn hoặc lãng phí tài nguyên CPU i7?

Hãy tưởng tượng một **Tiệm Bánh Mì**:
* **Producer (Thợ làm bánh):** Làm ra bánh và để lên kệ.
* **Buffer (Kệ bánh):** Có giới hạn (ví dụ chỉ chứa được 10 ổ).
* **Consumer (Khách hàng):** Đến lấy bánh từ kệ.

---

### 1. Tại sao cần Pattern này?

Nếu không có cái "kệ bánh" (Buffer) ở giữa làm vùng đệm:
* **Lãng phí:** Thợ làm bánh xong phải đợi khách đến mới đưa tận tay $\rightarrow$ Thợ bị rảnh rỗi vô ích.
* **Tắc nghẽn:** Khách đến mà thợ chưa làm xong phải đứng nhìn thợ làm $\rightarrow$ Khách bị chặn (Blocked).

**Producer–Consumer** giúp **tách biệt (decouple)** hai quá trình này: Thợ cứ làm khi kệ còn chỗ, khách cứ lấy khi kệ còn bánh. Cả hai chỉ tương tác thông qua cái kệ chung.

---

### 2. Ba bài toán điều phối cốt lõi

Để hệ thống hoạt động trơn tru, bạn phải xử lý 3 trạng thái của Buffer:

1.  **Chặn Producer (Buffer Full):** Khi kệ đã đầy, thợ phải "ngủ đông" (`wait` hoặc `await`). Nếu làm tiếp sẽ gây tràn bộ nhớ (StackOverflow/OOM).
2.  **Chặn Consumer (Buffer Empty):** Khi kệ trống, khách phải "ngủ đông" và đợi. Không thể tiêu thụ thứ không tồn tại.
3.  **Đánh thức (Signaling):** * Khi thợ bỏ bánh vào kệ đang trống $\rightarrow$ Phải gọi khách dậy (`notify` hoặc `signal`).
  * Khi khách lấy bánh từ kệ đang đầy $\rightarrow$ Phải gọi thợ dậy để làm thêm.

---

### 3. Cách triển khai trong Java

Trong project `java-learning`, bạn nên triển khai theo 3 cấp độ để thấy sự tiến hóa của ngôn ngữ:

* **Cấp độ 1: Cổ điển (wait/notify)**
  Bạn tự quản lý việc ngủ/thức bên trong khối `synchronized`. Sử dụng `while(size == limit)` để kiểm tra điều kiện trước khi `wait`.
* **Cấp độ 2: Hiện đại (Condition)**
  Sử dụng `ReentrantLock` với hai điều kiện `notFull` và `notEmpty`. Cách này tối ưu vì bạn đánh thức chính xác nhóm cần thiết (như đã học ở bài trước).
* **Cấp độ 3: Chuyên nghiệp (BlockingQueue)**
  Java cung cấp sẵn `ArrayBlockingQueue` hoặc `LinkedBlockingQueue`. Bạn chỉ cần gọi `put()` và `take()`, mọi việc điều phối bên dưới đã được Java lo liệu cực kỳ an toàn.

---

### 4. Bảng tóm tắt vai trò

| Thành phần | Trách nhiệm | Hành động khi "kịch trần" |
| :--- | :--- | :--- |
| **Producer** | Tạo dữ liệu, đưa vào Buffer. | Đợi cho đến khi Buffer có chỗ trống. |
| **Buffer** | Lưu trữ tạm thời dữ liệu. | Giới hạn dung lượng để bảo vệ bộ nhớ. |
| **Consumer** | Lấy dữ liệu từ Buffer xử lý. | Đợi cho đến khi Buffer có dữ liệu. |

---

</details>

- [Quay lại đầu trang](#back-to-top)