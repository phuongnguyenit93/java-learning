# Java Concurrency: Fork/Join Framework

## <a id="fork-join"> Các khái niệm cơ bản của Fork Join</a>

## 1. Tư tưởng cốt lõi: Chia nhỏ và Trị (Fork & Join)
Framework này được thiết kế để tận dụng tối đa các CPU nhiều nhân (Multi-core). Nó hoạt động theo hai bước chính:

* **Fork (Chia):** Nếu bài toán quá lớn, nó sẽ chia đôi bài toán đó thành các bài toán con (sub-tasks) và chạy chúng song song. Quá trình này lặp lại cho đến khi bài toán con đủ nhỏ để xử lý trực tiếp.
* **Join (Gộp):** Sau khi các bài toán con tính xong, kết quả sẽ được gộp lại dần dần để cho ra kết quả cuối cùng.

## 2. Đặc điểm "vô đối": Thuật toán Work-Stealing
Đây là lý do khiến Fork/Join cực nhanh. Trong một Thread Pool thông thường, nếu một Thread làm xong việc, nó sẽ đứng chơi. Trong **ForkJoinPool**:

1. Mỗi Thread có một hàng đợi (**deque**) các nhiệm vụ riêng.
2. Nếu Thread A làm xong hết việc của mình, nó sẽ nhìn sang hàng đợi của Thread B (vẫn đang ngập đầu trong việc).
3. Thread A sẽ **"trộm" (steal)** một nhiệm vụ từ cuối hàng đợi của Thread B để làm hộ.
4. Tránh Overkill:** Nếu bạn dùng `new Thread()` cho mỗi bản ghi, hệ thống sẽ sập (**Out of Memory**). Fork/Join giới hạn số luồng bằng đúng số nhân CPU, giúp máy chạy mượt mà ở mức tải tối đa.

**Kết quả:** Toàn bộ các nhân CPU luôn bận rộn 100%, không có nhân nào bị lãng phí.

## 3. Cấu trúc của Fork/Join
Để sử dụng, bạn cần làm quen với 3 thành phần chính:
* **ForkJoinPool:** "Nhà máy" quản lý các luồng. Thường dùng `ForkJoinPool.commonPool()`.
* **RecursiveTask<V>:** Dùng khi bài toán có trả về kết quả (ví dụ: tính tổng mảng).
* **RecursiveAction:** Dùng khi bài toán không trả về kết quả (ví dụ: sắp xếp mảng tại chỗ).

## 4. Ví dụ: Tính tổng một mảng 10 triệu phần tử

```java
	import java.util.concurrent.RecursiveTask;
	import java.util.concurrent.ForkJoinPool;
	
	public class SumTask extends RecursiveTask<Long> {
	    private static final int THRESHOLD = 1000; // Ngưỡng dừng chia nhỏ
	    private int[] array;
	    private int start;
	    private int end;
	
	    public SumTask(int[] array, int start, int end) {
	        this.array = array;
	        this.start = start;
	        this.end = end;
	    }
	
	    @Override
	    protected Long compute() {
	        if (end - start <= THRESHOLD) {
	            // Nếu đủ nhỏ, tính trực tiếp
	            long sum = 0;
	            for (int i = start; i < end; i++) sum += array[i];
	            return sum;
	        } else {
	            // Nếu còn lớn, chia đôi (Fork)
	            int mid = (start + end) / 2;
	            SumTask leftTask = new SumTask(array, start, mid);
	            SumTask rightTask = new SumTask(array, mid, end);
	
	            leftTask.fork(); // Chạy luồng con 1 song song
	            long rightResult = rightTask.compute(); // Chạy luồng con 2
	            long leftResult = leftTask.join(); // Đợi luồng con 1 và lấy kết quả
	
	            return leftResult + rightResult; // Gộp kết quả (Join)
	        }
	    }
	
	    public static void main(String[] args) {
	        int[] data = new int[10_000_000];
	        // ... fill data ...
	
	        ForkJoinPool pool = ForkJoinPool.commonPool();
	        long result = pool.invoke(new SumTask(data, 0, data.length));
	        System.out.println("Tổng là: " + result);
	    }
```

## 5. So sánh nhanh với ExecutorService

| Đặc điểm | ExecutorService | Fork/Join Framework |
| :--- | :--- | :--- |
| **Loại công việc** | Các task độc lập (Email, Request). | Task lớn có thể chia nhỏ (Recursive). |
| **Tận dụng CPU** | Bình thường. | Tối đa nhờ **Work-stealing**. |
| **Độ phức tạp** | Dễ dùng. | Khó hơn (phải thiết kế đệ quy). |

## 6. Những sai lầm chết người khi xử lý Record bằng Fork/Join

Trong dự án `java-learning` của bạn, hãy cực kỳ lưu ý 2 điểm sau để tránh "phản tác dụng":

### Không gọi Database trong compute()
Nếu 1 triệu bản ghi mà mỗi bản ghi bạn lại `SELECT * FROM...`, Fork/Join sẽ chậm hơn cả 1 luồng vì nó phải đợi phản hồi mạng (I/O).

> **Giải pháp:** Hãy lấy 1 triệu bản ghi về bộ nhớ trước, rồi mới dùng Fork/Join để xử lý logic (CPU-bound).

### Cẩn thận với việc gộp kết quả (Join)
Trong các ví dụ đơn giản, ta thường dùng `leftResult.addAll(rightResult)`. Tuy nhiên, với 1 triệu bản ghi, việc tạo ra quá nhiều `ArrayList` nhỏ rồi gộp lại liên tục sẽ gây áp lực cực lớn lên **Garbage Collection (GC)**.

> **Giải pháp:** Trong thực tế, người ta thường dùng một **Concurrent Collection** (như `ConcurrentLinkedQueue`) để các luồng đẩy kết quả trực tiếp vào đó, tránh việc tạo object trung gian không cần thiết.

## 💡 Lưu ý quan trọng
* **Chỉ dùng cho CPU-bound:** Đừng dùng Fork/Join cho các việc có I/O (đọc file, gọi database, gọi API).
* **Nguy cơ tê liệt:** Nếu bạn block luồng trong ForkJoinPool bằng I/O, cơ chế Work-stealing sẽ bị tê liệt hoàn toàn.

---

## <a id="fork-join-method">Giải thích các từ khóa cốt lõi: Fork, Compute, Join, Invoke</a>

Để hiểu về 4 từ khóa này, hãy tưởng tượng **Fork/Join Framework** giống như một vị tướng quân đang điều hành một đội quân để xử lý một công việc khổng lồ (ví dụ: quét sạch 1 triệu quân địch).

## 1. Fork (Chia nhánh)
* **Mệnh lệnh:** *"Chia nhỏ việc ra và giao cho cấp dưới!"*
* **Ý nghĩa:** Khi một nhiệm vụ quá lớn, bạn dùng `fork()` để tách nó thành một nhiệm vụ con (sub-task) và đẩy vào hàng đợi (Work Queue).
* **Hành động:** Nó không chạy ngay lập tức tuần tự mà được gửi vào "kho việc". Các luồng rảnh tay khác sẽ "trộm" (steal) việc về làm.

## 2. Compute (Thực hiện/Tính toán)
* **Mệnh lệnh:** *"Bắt tay vào làm việc đi!"*
* **Ý nghĩa:** Phương thức chứa logic thực tế, là "trái tim" của mọi Task.
* **Hành động:** Kiểm tra điều kiện dừng (Threshold):
    * Nếu việc đủ nhỏ $\rightarrow$ Làm luôn (tính toán trực tiếp).
    * Nếu việc còn to $\rightarrow$ Tiếp tục gọi `fork()` để chia nhỏ thêm.

## 3. Join (Gộp kết quả)
* **Mệnh lệnh:** *"Báo cáo kết quả lại đây cho tôi!"*
* **Ý nghĩa:** Khi đã giao việc (fork), bạn cần kết quả của nó. `join()` khiến luồng hiện tại dừng lại và đợi cho đến khi nhiệm vụ con hoàn thành.
* **Hành động:** Trả về kết quả sau khi tính toán xong. Trong khi đợi, luồng này có thể đi "trộm" việc khác để tránh lãng phí.

## 4. Invoke (Kích hoạt/Triệu tập)
* **Mệnh lệnh:** *"Tổng tấn công! Bắt đầu toàn bộ quy trình!"*
* **Ý nghĩa:** Điểm khởi đầu (Entry Point) để gửi nhiệm vụ lớn nhất vào `ForkJoinPool`.
* **Hành động:** Thực hiện nhiệm vụ, đợi hoàn thành và trả về kết quả cuối cùng.

---

## Tóm tắt quy trình (Workflow)

Cách chúng phối hợp trong dự án `java-learning`:
1.  **invoke:** Bạn ném 1 triệu bản ghi vào và bảo "Làm đi!".
2.  **compute:** Nhiệm vụ bắt đầu xem xét 1 triệu bản ghi.
3.  **fork:** Thấy 1 triệu quá to, nó xẻ đôi, ném 500k bản ghi sang một nhánh bên cạnh.
4.  **compute (đệ quy):** Nhánh con lại xẻ tiếp cho đến khi đạt ngưỡng (ví dụ 10k bản ghi) thì tự tính toán.
5.  **join:** Các nhánh lớn thu thập kết quả từ các nhánh nhỏ và gộp lại thành danh sách tổng.

### Sự khác biệt quan trọng:
| Từ khóa | Hành động | Trạng thái |
| :--- | :--- | :--- |
| **fork()** | Gửi đi | Bất đồng bộ (Không đợi) |
| **join()** | Lấy về | Đồng bộ (Phải đợi) |
| **invoke()** | Khởi chạy | Chạy từ đầu đến cuối & lấy kết quả |

Ví dụ minh họa cấu trúc:
```java
    if (task.isSmall()) {
    return task.computeDirectly();
    } else {
    SubTask left = new SubTask(...);
    SubTask right = new SubTask(...);
    
            left.fork(); // Đẩy nhánh trái vào hàng đợi
            return right.compute() + left.join(); // Tính nhánh phải và gộp kết quả nhánh trái
        }
```

## <a id="fork-join-task-and-parallel-task">So sánh: ForkJoinTask (Thủ công) vs. Parallel Stream (Tự động)</a>

Đây là một bài so sánh cực kỳ thú vị vì nó sẽ cho bạn thấy sự khác biệt giữa việc "tự tay lái số sàn" (**ForkJoinTask**) và "đi xe số tự động" (**Parallel Stream**). Mặc dù cả hai đều chạy trên nền tảng `ForkJoinPool`, nhưng cách chúng quản lý bộ nhớ và chia việc lại khác nhau đáng kể.

## 1. So sánh Code: Thủ công vs. Hiện đại

Thực hiện cùng một bài toán: Xử lý 1 triệu bản ghi Transaction có phí ngẫu nhiên.

### Cách 1: ForkJoinTask (Thủ công)
Bạn phải tự định nghĩa lớp, chọn ngưỡng `THRESHOLD`, gọi `fork()` và `join()`.

	```java
	// Bạn phải viết cả một Class dài (sử dụng RecursiveTask)
	List<Long> result = pool.invoke(new TransactionProcessor(data, 0, data.size()));
	```

### Cách 2: Parallel Stream (Hiện đại)
Java tự động lo hết phần chia nhỏ (Splitting) và gộp (Merging).

	```java
	List<Long> result = data.parallelStream()
	    .filter(t -> !t.isValid()) // Logic kiểm tra
	    .map(t -> t.id)
	    .toList();
	```

## 2. Benchmark thực tế: Ai nhanh hơn?

| Tiêu chí | ForkJoinTask (Manual) | Parallel Stream |
| :--- | :--- | :--- |
| **Tốc độ thực thi** | Nhanh nhất (nếu ngưỡng `THRESHOLD` chuẩn). | Thấp hơn khoảng 5-10% so với code manual tối ưu. |
| **Độ sạch của code** | Dài dòng, khó bảo trì. | Cực sạch, dễ đọc, dễ bảo trì. |
| **Quản lý bộ nhớ** | Kiểm soát được số lượng Object con được tạo ra. | Tự quản lý, đôi khi tạo nhiều object trung gian hơn. |
| **Khả năng tùy biến** | Tùy chỉnh thuật toán chia (ví dụ chia 3 thay vì chia 2). | Phụ thuộc vào `Spliterator` mặc định của Java. |

## 3. Tại sao lại có sự chênh lệch này?

### Ngưỡng THRESHOLD (The "Sweet Spot")
* **ForkJoinTask:** Bạn tự đặt ngưỡng. Nếu chọn đúng số này, hiệu năng đạt đỉnh. Nếu chọn sai (quá nhỏ hoặc quá lớn), nó có thể chậm hơn cả chạy đơn luồng.
* **Parallel Stream:** Java dùng thuật toán thông minh để tự tính toán ngưỡng chia dựa trên số nhân CPU và kích thước mảng. Nó thường chọn một ngưỡng "an toàn" cho đa số trường hợp.

### Splitting logic
* `Parallel Stream` sử dụng `Spliterator`. Với **ArrayList**, việc chia đôi cực nhanh vì chỉ tính toán index mảng.
* Với **LinkedList**, `Parallel Stream` sẽ chạy cực chậm vì việc chia đôi một danh sách liên kết tốn chi phí $O(n)$.

Trong hầu hết các ứng dụng Java hiện đại, Parallel Stream thường là lựa chọn ưu tiên. Dưới đây là lý do và các quy tắc để bạn lựa chọn công cụ phù hợp.

## 4. Ưu thế của Parallel Stream

* **Tư duy khai báo (Declarative):** Bạn chỉ cần nói cho Java biết bạn muốn làm gì (`filter`, `map`, `sum`), thay vì phải hướng dẫn Java cách chia nhỏ và gộp lại như thế nào.
* **Spliterator thông minh:** Java có sẵn các bộ chia (**Spliterator**) cực kỳ tối ưu cho các Collection phổ biến như `ArrayList`, `HashMap`, hay `Range`. Nó tự biết cách chia mảng 1 triệu phần tử ra sao cho cân bằng nhất.
* **Bảo trì dễ dàng:** Một đoạn code Parallel Stream dài 3 dòng dễ đọc và ít lỗi hơn rất nhiều so với một Class `RecursiveTask` dài 50 dòng.

## 5. Khi nào thực sự cần dùng Fork/Join thủ công?

Chỉ khi bạn rơi vào những trường hợp "khó nhằn" mà Stream không giải quyết được:

* **Bài toán không phải là Collection:** Stream sinh ra để xử lý tập dữ liệu có sẵn. Nếu bài toán là tính toán đệ quy thuần túy (tìm đường đi, quét cây thư mục, thuật toán sắp xếp), Fork/Join thủ công sẽ linh hoạt hơn.
* **Cần kiểm soát Pool riêng biệt:** Parallel Stream mặc định dùng `ForkJoinPool.commonPool()`. Nếu bạn có tác vụ cực nặng và không muốn nó chiếm hết tài nguyên của hệ thống, bạn cần tự tạo một `ForkJoinPool` riêng.
* **Cấu trúc dữ liệu tùy chỉnh:** Nếu bạn tự viết một cấu trúc dữ liệu mới mà Java không hỗ trợ `Spliterator` sẵn, bạn sẽ phải tự viết logic chia việc bằng Fork/Join.

## 6. Quy tắc "Ngón tay cái" (Rule of Thumb) cho Backend Developer

Khi đối mặt với bài toán xử lý 1 triệu bản ghi trong dự án `java-learning`:

1.  **Bước 1:** Viết bằng Stream bình thường (tuần tự).
2.  **Bước 2:** Nếu thấy chậm, hãy thử đổi thành `.parallelStream()`.
3.  **Bước 3:** Nếu vẫn chưa đạt hiệu năng mong muốn HOẶC bài toán có tính chất đệ quy phức tạp $\rightarrow$ Lúc đó mới sử dụng Fork/Join thủ công để **tối ưu hóa chuyên sâu**.

## 💡 Lưu ý cực kỳ quan trọng:

Dù dùng cách nào, hãy nhớ: **Đừng bao giờ dùng đa luồng (cả Parallel Stream lẫn Fork/Join) cho các tác vụ I/O nặng** (như gọi API từ xa hay truy vấn Database liên tục).

> **Bản chất:** Fork/Join là để "nghiền nát" các con số (**CPU-bound**). Nếu luồng bị dừng lại để đợi phản hồi từ mạng, cơ chế **Work-stealing** sẽ bị vô hiệu hóa và hiệu năng sẽ tệ hơn cả khi chạy đơn luồng.

	```java
	// KHÔNG NÊN làm thế này trong Parallel Stream/ForkJoin
	list.parallelStream().forEach(id -> {
	    // Gọi API hoặc truy vấn DB tại đây sẽ làm nghẽn Common Pool
	    fetchDataFromRemoteAPI(id); 
	});
	```

## 7. Lời khuyên cho dự án java-learning

Trong thực tế phát triển Backend:

* **90% trường hợp:** Hãy dùng **Parallel Stream**. Code ngắn gọn giúp đồng nghiệp dễ hiểu và hiệu năng đã đủ tốt cho hầu hết bài toán xử lý record.
* **10% trường hợp đặc biệt:** Chỉ dùng **ForkJoinTask** thủ công khi:
  * Bài toán cực kỳ phức tạp, cần thuật toán đệ quy đặc thù (như xử lý ảnh, quét cây thư mục).
  * Cần tối ưu hóa hiệu năng đến từng mili giây (Micro-optimization).
  * Muốn tự kiểm soát `ForkJoinPool` riêng để không làm ảnh hưởng đến `commonPool`.

## 8. Bí mật về Parallel Stream (Cảnh báo I/O)
Nếu bạn dùng `parallelStream()` cho các tác vụ gọi API hoặc Database (**I/O Bound**), bạn có thể làm treo toàn bộ ứng dụng.

> **Lý do:** Tất cả `parallelStream()` trong một ứng dụng Java đều dùng chung một `ForkJoinPool.commonPool()`. Nếu một nơi bị kẹt do đợi phản hồi mạng, tất cả các luồng khác trong hệ thống cũng sẽ bị "vạ lây".