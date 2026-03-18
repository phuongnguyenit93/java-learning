<a id="back-to-top"></a>

# Những vấn đề nguy hiểm trong đồng bộ hoá

## Menu
- [Instruction Reordering: Con ma trong Đa luồng](#instruction-reordering)
- [ Happens-Before: Bản hợp đồng Nhất quán Bộ nhớ](#happen-before)
- [Tìm hiểu về Dữ liệu mờ ảo (Stale Data)](#stale-data)
- [Tại sao khó test lỗi Stale Data trên CPU i7?](#stale-data-advance)
- [Kỹ thuật Hoisting trong JIT](#hoisting)
- [Visibility (Tính hiển thị) trong Lập trình đa luồng](#visibility)
- [Race Condition: Cơn ác mộng của lập trình đa luồng](#race-condition)
- [Deadlock (Khóa chết) trong Lập trình đa luồng](#deadlock)
- [Java Concurrency: Hiểu về Livelock](#livelock)



## <a id="instruction-reordering">Instruction Reordering: Con ma trong Đa luồng</a>
<details>
<summary>Click for details</summary>


**Instruction Reordering** là kỹ thuật tối ưu hóa của Compiler và CPU nhằm tận dụng tối đa các chu kỳ trống của phần cứng. Tuy nhiên, trong môi trường đa luồng, nó có thể khiến chương trình chạy sai logic một cách cực kỳ khó hiểu.

---

## 1. Tại sao CPU lại "cầm đèn chạy trước ô tô"?

CPU không thực thi code theo từng dòng như chúng ta đọc sách. Nó nhìn vào một nhóm lệnh, nếu thấy **Lệnh 2** không phụ thuộc vào kết quả của **Lệnh 1**, nó có thể đảo thứ tự để chạy song song hoặc tận dụng Pipeline của vi xử lý.

---

## 2. Nguy hiểm: Kịch bản "Chưa kịp nấu đã ăn"

Hãy xem lỗi kinh điển khi không có `volatile` dưới đây:

```java
    int a = 0;
    boolean ready = false;

    // Thread 1: Chuẩn bị dữ liệu
    public void writer() {
        a = 42;          // Lệnh 1
        ready = true;    // Lệnh 2
    }

    // Thread 2: Sử dụng dữ liệu
    public void reader() {
        if (ready) {     // Lệnh 3
            System.out.println(a); // Lệnh 4
        }
    }
```

* **Thực tế phũ phàng:** CPU có thể đảo Lệnh 2 lên trước Lệnh 1.
* **Hệ quả:** Thread 2 thấy `ready == true` nhưng `a` vẫn đang bằng 0. Kết quả in ra số 0 sai lệch hoàn toàn so với logic nghiệp vụ.

---

## 3. Volatile và Hàng rào bộ nhớ (Memory Barrier)

Khi bạn khai báo `private volatile boolean ready = false;`, Java thiết lập một **Memory Barrier** (Ranh giới bất khả xâm phạm):

1.  **Ngăn chặn đảo lệnh lên trên (StoreStore Barrier):** Mọi lệnh ghi phía trước (như `a = 42`) bắt buộc phải hoàn tất trước khi lệnh ghi vào biến `volatile` (`ready = true`) được thực hiện.
2.  **Ngăn chặn đảo lệnh xuống dưới (LoadLoad Barrier):** Mọi lệnh đọc biến `volatile` phải xong trước khi các lệnh đọc phía sau nó bắt đầu.
3.  **Quy tắc Happens-Before:** Việc ghi vào biến `volatile` luôn xảy ra trước mọi hành động đọc biến đó sau này.

---

## 4. 🛡️ Ứng dụng: Singleton Double-Checked Locking

Đây là nơi `volatile` thể hiện vai trò "cứu mạng". Việc khởi tạo `new Object()` thực chất gồm 3 bước bytecode:

    1. Cấp phát bộ nhớ (Allocate).
    2. Gọi Constructor để khởi tạo thuộc tính (Init).
    3. Gán địa chỉ bộ nhớ cho biến (Publish).

**Nếu KHÔNG có volatile:** CPU có thể đảo bước 3 lên trước bước 2. Thread khác thấy `instance != null` nên lấy ra dùng ngay, nhưng thực tế Constructor chưa chạy xong $\rightarrow$ Gây lỗi dữ liệu rác hoặc `NullPointerException`.

```java
    public class DatabaseConnector {
        // PHẢI CÓ volatile để chặn reordering bước 2 và 3
        private static volatile DatabaseConnector instance;

        public static DatabaseConnector getInstance() {
            if (instance == null) {
                synchronized (DatabaseConnector.class) {
                    if (instance == null) {
                        instance = new DatabaseConnector(); 
                    }
                }
            }
            return instance;
        }
    }
```

---

## 🎯 Tổng kết về Volatile

| Tính năng | Tác dụng |
| :--- | :--- |
| **Visibility** | Đảm bảo Thread luôn đọc giá trị mới nhất từ RAM, không đọc từ Cache CPU. |
| **Ordering** | Thiết lập Memory Barrier, ngăn chặn CPU/Compiler sắp xếp lại thứ tự lệnh. |
| **Atomicity** | **KHÔNG CÓ.** (Vẫn cần `Atomic` hoặc `synchronized` cho các phép toán như `++`). |

---

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="happen-before"> Happens-Before: Bản hợp đồng Nhất quán Bộ nhớ</a>
<details>
<summary>Click for details</summary>


Trong Java, **Happens-Before** không đơn thuần là thứ tự thời gian. Đó là một **đảm bảo về sự hiển thị**: Nếu hành động A *happens-before* hành động B, thì mọi thay đổi dữ liệu của A chắc chắn phải được B nhìn thấy.

---

## 1. Tại sao Happens-Before là "vị cứu tinh"?

Nếu không có quy tắc này, CPU và Compiler có quyền đảo lệnh (Reordering) thoải mái để tăng tốc. Kết quả là Thread B có thể đọc được dữ liệu cũ hoặc dữ liệu rác từ Cache vì nó không có "căn cứ" nào để biết Thread A đã làm xong việc hay chưa.

**Happens-Before** thiết lập các ranh giới an toàn để dữ liệu luân chuyển giữa các Thread mà không bị sai lệch.

---

## 2. 5 Quy tắc "Sống còn" của Happens-Before

Để làm chủ đa luồng, bạn chỉ cần nắm vững 5 quy tắc nền tảng sau:

### A. Quy tắc Đơn luồng (Program Order Rule)
Trong cùng một Thread, các lệnh thực thi theo đúng thứ tự xuất hiện trong mã nguồn.
* **Ý nghĩa:** Kết quả cuối cùng phải giống như khi chạy tuần tự, dù CPU có tối ưu hóa bên trong.

### B. Quy tắc Volatile (Volatile Variable Rule)
Hành động **Ghi** vào biến `volatile` luôn *happens-before* mọi hành động **Đọc** biến đó sau này.
* **Ý nghĩa:** Khi Thread A ghi xong vào biến `volatile`, mọi thay đổi trước đó của A (kể cả biến thường) đều trở nên "trong suốt" với Thread B khi B đọc biến đó.

### C. Quy tắc Khóa (Lock Rule)
Việc **Giải phóng khóa** (Unlock) luôn *happens-before* mọi hành động **Chiếm giữ khóa** (Lock) trên cùng một Monitor sau này.
* **Ý nghĩa:** Người vào phòng sau sẽ thấy toàn bộ những gì người trước đã thực hiện trong phòng đó.

### D. Quy tắc Luồng (Thread Start/Join Rule)
* **Start:** `thread.start()` *happens-before* mọi hành động bên trong Thread con.
* **Join:** Mọi hành động trong Thread con *happens-before* lệnh tiếp theo của Thread cha sau khi `join()` kết thúc.

### E. Quy tắc Bắc cầu (Transitivity)
Nếu **A → B** và **B → C**, thì chắc chắn **A → C**.
* **Ý nghĩa:** Đây là "chìa khóa" để kết hợp các biến thường và biến `volatile` nhằm tạo ra sự đồng bộ dữ liệu phức tạp.

---

## 3. 🧪 Ví dụ: Kỹ thuật "Đi nhờ xe" (Piggybacking)

Đây là cách tận dụng quy tắc **Bắc cầu** để đồng bộ hóa cả một mảng dữ liệu mà không cần dùng khóa tốn kém:

```java
    int a = 0;
    volatile boolean flag = false;

    // --- Thread 1 ---
    a = 100;         // (1) Ghi biến thường
    flag = true;     // (2) Ghi biến volatile (Cổng đóng)

    // --- Thread 2 ---
    if (flag) {      // (3) Đọc biến volatile (Cổng mở)
        System.out.println(a); // (4) Chắc chắn thấy a = 100
    }
```

**Phân tích logic:**
1.  **(1) → (2)**: Quy tắc Đơn luồng.
2.  **(2) → (3)**: Quy tắc Volatile.
3.  **(3) → (4)**: Quy tắc Đơn luồng.
4.  **Kết luận:** Nhờ tính **Bắc cầu**, **(1) → (4)**. Dù `a` là biến thường, nó đã "đi nhờ xe" biến `flag` để hiển thị an toàn sang Thread 2.

---

## 🎯 Tổng kết

| Tình huống | Kết quả |
| :--- | :--- |
| **Có mối quan hệ Happens-Before** | Dữ liệu nhất quán, hiển thị rõ ràng giữa các Thread. |
| **KHÔNG có mối quan hệ Happens-Before** | Xảy ra **Data Race**. Kết quả phụ thuộc vào sự may rủi của CPU/OS. |

> **Lời khuyên:** Hãy coi Happens-Before là bản hợp đồng giữa bạn và JVM. Bạn sử dụng đúng công cụ (`volatile`, `synchronized`, `final`), JVM hứa sẽ giữ cho thế giới đa luồng của bạn luôn ổn định.

---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="stale-data">Tìm hiểu về Dữ liệu mờ ảo (Stale Data)</a>
<details>
<summary>Click for details</summary>


**Dữ liệu mờ ảo (Stale Data)** là hiện tượng một Thread đọc phải giá trị cũ của một biến, trong khi một Thread khác đã cập nhật giá trị mới cho biến đó rồi.

Nó giống như việc bạn xem bảng tỉ số bóng đá trên một trang web bị treo (caching), trong khi thực tế trận đấu đã có thêm bàn thắng mà bạn không hề hay biết.

---

### 1. Tại sao Stale Data xảy ra?
Trong kiến trúc máy tính hiện đại, để tăng tốc độ, CPU không đọc/ghi trực tiếp vào RAM mỗi lần vì RAM rất chậm. Thay vào đó:

* Mỗi lõi CPU có một bộ nhớ đệm riêng (L1, L2, L3 Cache).
* Khi Thread chạy trên **Core 1** sửa biến `count` từ 0 lên 1, giá trị này có thể chỉ nằm ở Cache của Core 1.
* Thread chạy trên **Core 2** vào đọc biến `count`, nó nhìn vào RAM (vẫn là 0) hoặc Cache của chính nó, dẫn đến việc lấy sai dữ liệu.

---

### 2. Ví dụ thực tế: Cỗ máy không bao giờ dừng
Hãy xem đoạn code đơn giản này. Bạn sẽ thấy "Stale Data" có thể làm chương trình chạy sai logic hoàn toàn:

**Java**

```java
	public class StaleDataDemo {
	    private boolean sayHello = false; // Biến này dễ bị stale

	    public void startThread() {
	        // Thread 1: Đợi biến sayHello thành true
	        new Thread(() -> {
	            while (!sayHello) {
	                // Đợi mãi mãi...
	            }
	            System.out.println("Hello world!");
	        }).start();

	        // Thread 2: Đổi biến sayHello sau 1 giây
	        new Thread(() -> {
	            try { Thread.sleep(1000); } catch (InterruptedException e) {}
	            sayHello = true;
	            System.out.println("Đã đổi sayHello thành true");
	        }).start();
	    }
	}
```

**Kết quả bất ngờ:** Trên nhiều dòng CPU, chương trình này không bao giờ in ra "Hello world!".
* Thread 2 đã đổi `sayHello = true`.
* Nhưng Thread 1 cứ nhìn vào bản copy cũ (false) trong Cache của nó và chạy vòng lặp `while` vô tận. Đó chính là **Stale Data**.

---

### 3. Cách "diệt tận gốc" Stale Data
Để đảm bảo không bao giờ đọc phải dữ liệu cũ, bạn phải thiết lập mối quan hệ *Happens-Before*. Có 2 cách phổ biến:

**Cách A: Dùng `volatile` (Giải pháp gọn nhẹ)**
Chỉ cần thêm từ khóa `volatile`, JVM sẽ ép các Thread không được dùng Cache cho biến này.

```java
	private volatile boolean sayHello = false;
```

**Cách B: Dùng `synchronized` (Giải pháp toàn diện)**

```java
	public synchronized void setHello(boolean val) { this.sayHello = val; }
	public synchronized boolean isHello() { return this.sayHello; }
```

---

### 4. Phân biệt Stale Data và Race Condition

| Khái niệm | Vấn đề chính | Giải pháp phổ biến |
| :--- | :--- | :--- |
| **Stale Data** | Tính hiển thị (Visibility) - Đọc sai do nhìn bản cũ. | `volatile` |
| **Race Condition** | Tính nguyên tử (Atomicity) - Hai Thread cùng sửa làm mất dữ liệu. | `AtomicInteger`, `synchronized` |

> **Mẹo ghi nhớ:** Stale Data là vấn đề về **"nhìn"**, còn Race Condition là vấn đề về **"làm"**.

### Tổng kết
Stale Data là lỗi cực kỳ nguy hiểm vì nó không báo lỗi, chương trình vẫn chạy nhưng kết quả sai hoặc bị treo. Luôn sử dụng `volatile` cho các biến cờ hiệu (flags) để tránh "nhìn lầm" dữ liệu cũ.

---

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="stale-data-advance">Tại sao khó test lỗi Stale Data trên CPU i7?</a>
<details>
<summary>Click for details</summary>


Việc bạn dùng chip **i7** (một dòng chip cực kỳ mạnh mẽ và hiện đại) chính là lý do khiến việc test lỗi Stale Data hay Reordering trở nên khó khăn hơn bao giờ hết. Có 3 lý do "oái oăm" khiến bạn test hoài không ra lỗi:

---

### 1. CPU i7 quá thông minh (Coherency Protocol)
Các dòng chip Intel Core i7 sử dụng một cơ chế gọi là **MESI Protocol** để quản lý bộ nhớ đệm (Cache Coherency).

* Khi Core 1 sửa một biến, phần cứng của i7 sẽ phát tín hiệu qua một con đường riêng (Bus) để báo cho Core 2 biết rằng bản copy trong Cache của nó đã cũ.
* Mặc dù về mặt lý thuyết Java không đảm bảo tính hiển thị nếu thiếu `volatile`, nhưng phần cứng i7 lại làm hộ việc đó quá tốt, khiến dữ liệu được cập nhật gần như tức thì giữa các Core.

---

### 2. JIT Compiler chưa kịp tối ưu (Hotspot)
JVM có một trình biên dịch tên là **JIT**. Ban đầu, nó chạy code ở chế độ "thông dịch" (interpreter). Ở chế độ này, nó rất ít khi đảo lệnh (Reordering).

Chỉ khi đoạn code được chạy đi chạy lại hàng chục nghìn lần, JIT mới nhận diện đó là **"Hot code"** và bắt đầu tung ra các đòn tối ưu hạng nặng. Nếu bạn chỉ chạy vài lần rồi tắt, lỗi chưa kịp xuất hiện.

---

### 3. Hiệu ứng "Quan sát viên" (Heisenbug)
Chỉ cần bạn thêm một lệnh `System.out.println()` vào vòng lặp để kiểm tra, lỗi sẽ biến mất.

**Tại sao?** Vì `System.out.println` là một hàm có dùng `synchronized` bên trong. Theo quy tắc *Happens-before*, `synchronized` ép CPU phải dọn dẹp Cache và làm mới dữ liệu từ RAM. Việc bạn in Log vô tình đã "chữa" luôn lỗi Stale Data mà bạn đang cố bắt.

---

### Cách "ép" i7 phải lộ lỗi (The Hard Way)
Để thắng được sự thông minh của i7, bạn cần một kịch bản "tra tấn" hơn. Hãy thử dùng đoạn code **"Vòng lặp trống"** này, tuyệt đối không thêm Log hay bất cứ thứ gì vào trong `while`:

**Java**

```java
	public class HardToCatchStale {
	    // KHÔNG dùng volatile ở đây để đợi lỗi
	    private boolean running = true;

	    public void test() throws InterruptedException {
	        Thread t1 = new Thread(() -> {
	            long count = 0;
	            while (running) {
	                // TUYỆT ĐỐI ĐỂ TRỐNG: Không Println, không Sleep
	                count++; 
	            }
	            System.out.println("Thread 1 dừng lại sau " + count + " vòng lặp.");
	        });

	        t1.start();
	        Thread.sleep(100); // Cho Thread 1 chạy vào vòng lặp trước

	        System.out.println("Thread chính chuẩn bị đổi running = false...");
	        running = false; // Đổi biến
	        System.out.println("Thread chính đã đổi xong. Đợi Thread 1 dừng...");

	        t1.join(5000); // Đợi tối đa 5 giây
	        if (t1.isAlive()) {
	            System.err.println("THẮNG RỒI! Thread 1 bị kẹt (Stale Data thành công)!");
	        }
	    }

	    public static void main(String[] args) throws InterruptedException {
	        new HardToCatchStale().test();
	    }
	}
```

#### Tại sao bản này dễ ra lỗi hơn?
1.  **Vòng lặp rỗng (Tight Loop):** CPU thấy vòng lặp quá đơn giản nên sẽ nhét biến `running` vào thẳng thanh ghi (**Register**) để chạy cho nhanh, không thèm ngó ngàng đến RAM nữa.
2.  **Không có rào cản (No Barriers):** Vì không có `println` hay `lock`, không có lý do gì để CPU phải "làm mới" biến.

---

### Nếu vẫn không ra lỗi?
Hãy thử chạy Java với tham số dòng lệnh sau để ép JIT làm việc mạnh hơn:
* `java -Xint HardToCatchStale`: Chế độ Interpreted (chậm nhưng dễ bộc lộ lỗi hiển thị).
* `java -server HardToCatchStale`: Ép dùng Server Compiler với tối ưu hóa cao nhất.

---

# 
</details>

- [Quay lại đầu trang](#back-to-top)
---
<a id="hoisting">Kỹ thuật Hoisting trong JIT</a>
<details>
<summary>Click for details</summary>


Hiện tượng bạn gặp phải — đã không bị thì thôi, mà đã bị là "hóa đá" luôn — giải thích chính xác cách thức JVM và CPU i7 của bạn "hợp tác" với nhau để tối ưu hóa code.

Tại sao lại có sự "độc ác" này? Thủ phạm chính là **JIT (Just-In-Time) Compiler** và một kỹ thuật tối ưu hóa gọi là **Hoisting**.

---

### 1. Phân tích hiện tượng: Tại sao bị là bị luôn?
Khi bạn chạy chương trình, JVM ban đầu sẽ chạy code ở chế độ thông dịch (Interpreted). Nhưng khi thấy vòng lặp `while(running)` chạy quá nhiều lần (**Hot Code**), JIT Compiler sẽ nhảy vào để biên dịch nó sang mã máy (Native Code) cực nhanh.

Nó nhìn vào code của bạn:
**Java**
```java
    while (running) {
        count++;
    }
```

Nó suy luận: *"Trong cái vòng lặp này, chả có lệnh nào thay đổi biến running cả. Vậy thì việc gì ta phải tốn công đọc từ RAM mỗi lần lặp cho mệt?"* Thế là nó thực hiện **Hoisting**, biến code của bạn thành:
**Java**
```java
    if (running) {
        while (true) { // Chúc mừng, bạn đã vào vòng lặp vô tận!
            count++;
        }
    }
```

**Kết quả:**
* Nếu JIT chưa kịp tối ưu, bạn sẽ thấy chương trình dừng bình thường.
* Nhưng nếu JIT đã ra tay và "nhốt" giá trị `true` vào thanh ghi (**Register**) của CPU, thì dù bạn có đổi `running = false` ở RAM bao nhiêu lần, Thread 1 cũng không bao giờ thèm ngó xuống RAM nữa.

---

### 2. Tại sao `t1.interrupt()` không cứu được nó?
Lệnh `interrupt()` chỉ đặt một cái "cờ" (flag) báo hiệu. Tuy nhiên:
* Các hàm như `Thread.sleep()`, `Object.wait()` mới thường xuyên kiểm tra cờ này.
* Vòng lặp `while(running) { count++; }` của bạn không có bất kỳ điểm kiểm tra nào (**Check point**). Nó cứ thế chạy như một con bò điên mà không quan tâm đến tín hiệu ngắt.

---

### 3. Tại sao Reset App mới được?
Vì khi bạn reset app, bạn khởi động một tiến trình JVM mới:
* Bộ nhớ được cấp phát lại từ đầu.
* JIT Compiler bắt đầu lại từ con số 0.
* Các Cache của CPU cho tiến trình cũ bị xóa sạch.

---

### 4. Giải pháp "Một phát ăn ngay"

Để tránh tình trạng "hên xui" này, bạn có 2 lựa chọn:

**Cách 1: Volatile (Đúng bài bản)**
```java
    private volatile boolean running = true;
```
Từ khóa này cấm tiệt JIT thực hiện trò Hoisting. Nó bắt buộc CPU mỗi lần lặp phải "nhìn" xuống RAM.

**Cách 2: Thêm "Rào cản" vào vòng lặp (Tà đạo)**
Nếu bạn thêm `Thread.yield()` hoặc bất kỳ hàm `synchronized` nào (như `System.out.println`):
**Java**
```java
    while (running) {
        count++;
        Thread.yield(); // Ép CPU làm mới bộ nhớ đệm
}
```
*Lưu ý: Đây là cách làm không nên dùng trong thực tế.*

---

### Tổng kết bài học về Stale Data
1. **Hiện tượng này là ngẫu nhiên:** Tùy vào lúc JIT hứng lên tối ưu hay không.
2. **Tốc độ i7 là kẻ thù:** CPU càng nhanh, vòng lặp càng "tight" (chặt), JIT càng dễ thực hiện Hoisting.
3. **Đừng tin vào cảm giác:** Trong đa luồng, không thấy lỗi không có nghĩa là code đúng. Chỉ có **Happens-Before** mới đảm bảo an toàn.

---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="visibility">Visibility (Tính hiển thị) trong Lập trình đa luồng</a>
<details>
<summary>Click for details</summary>


Trong lập trình đa luồng, **Visibility** là một khái niệm sống còn. Nó trả lời cho câu hỏi: *"Khi luồng A thay đổi giá trị của một biến, làm sao để luồng B chắc chắn nhìn thấy sự thay đổi đó ngay lập tức?"*

Như bạn đã trải nghiệm với con chip i7, nếu không có các quy tắc đảm bảo Visibility, luồng B có thể mãi mãi nhìn thấy giá trị cũ (**Stale Data**) do nó đang đọc từ bản copy trong bộ nhớ đệm (Cache) của CPU thay vì RAM.

---

### 1. Mô hình bộ nhớ Java (JMM) và vấn đề Visibility
Máy tính hiện đại có kiến trúc phân tầng bộ nhớ. RAM thì chậm, nhưng Cache (L1, L2, L3) bên trong CPU thì cực nhanh.

**Nguyên nhân gây lỗi:** Mỗi Thread thường chạy trên một lõi CPU riêng biệt. Mỗi lõi lại có Cache riêng. Khi **Thread 1** sửa biến `x = 5`, giá trị này có thể chỉ nằm ở Cache của Core 1. **Thread 2** chạy trên Core 2 vẫn nhìn thấy `x = 0` ở RAM hoặc Cache của chính nó.

---

### 2. Các giải pháp đảm bảo Visibility
Để "ép" các luồng phải nhìn thấy dữ liệu của nhau, Java cung cấp các **"hàng rào bộ nhớ" (Memory Barriers)**:

#### A. Từ khóa `volatile` (Giải pháp nhẹ nhất)
Khi một biến được khai báo là `volatile`:
* **Ghi:** Mọi thay đổi sẽ được đẩy thẳng xuống RAM ngay lập tức.
* **Đọc:** Thread buộc phải đọc từ RAM, không được dùng bản copy trong Cache.
* **Hiệu quả:** Đảm bảo mọi Thread luôn nhìn thấy giá trị mới nhất.

#### B. Từ khóa `synchronized` (Giải pháp toàn diện)
Không chỉ đảm bảo loại trừ tương hỗ (mutual exclusion), `synchronized` còn có hiệu ứng về bộ nhớ:
* **Khi vào (Lock):** Thread phải làm mới (refresh) toàn bộ dữ liệu từ RAM.
* **Khi ra (Unlock):** Mọi thay đổi bên trong khối `synchronized` phải được đẩy hết xuống RAM trước khi nhả khóa.

#### C. Các lớp Atomic (`AtomicInteger`, `AtomicBoolean`,...)
Các lớp này sử dụng biến `volatile` bên trong kết hợp với cơ chế phần cứng **CAS (Compare And Swap)**. Nó vừa đảm bảo Visibility, vừa đảm bảo Atomicity (tính nguyên tử).

---

### 3. Tại sao Visibility lại khó hiểu?
Lỗi Visibility thường được gọi là **"Heisenbug"** (lỗi nhút nhát) vì:
* **Tính ngẫu nhiên:** Nó phụ thuộc vào việc CPU hứng lên thì dọn Cache, hoặc JIT Compiler hứng lên thì tối ưu code.
* **Dễ biến mất khi Debug:** Nếu bạn thêm `System.out.println()` để xem giá trị, hàm này có dùng `synchronized` bên trong, nó vô tình "sửa" luôn lỗi Visibility, khiến bạn tưởng code mình đã đúng.

---

### 4. Quy tắc Happens-Before
Để Visibility hoạt động ổn định, Java đặt ra bản hợp đồng **Happens-Before**. Nếu hành động A (Ghi) happens-before hành động B (Đọc), thì B chắc chắn thấy kết quả của A.

* **Volatile Rule:** Ghi vào biến `volatile` happens-before mọi lần đọc biến đó sau này.
* **Thread Start Rule:** Lệnh `start()` của Thread cha happens-before mọi lệnh trong Thread con. (Thread con sẽ thấy hết những gì cha nó đã chuẩn bị trước khi tạo ra nó).

---

### Tóm tắt nhanh

| Đặc điểm | Không dùng gì | `volatile` | `synchronized` |
| :--- | :--- | :--- | :--- |
| **Visibility** | Hên xui (thường là lỗi) | Có | Có |
| **Atomicity** | Không | Không | Có |
| **Hiệu năng** | Nhanh nhất | Nhanh | Chậm hơn |

---

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="race-condition">Race Condition: Cơn ác mộng của lập trình đa luồng</a>
<details>
<summary>Click for details</summary>


Trong khi **Visibility** là lỗi về "nhìn" (nhìn thấy dữ liệu cũ), thì **Race Condition** là lỗi về "làm" (nhiều luồng cùng nhảy vào xâu xé một dữ liệu cùng lúc).

Đây là "cơn ác mộng" lớn nhất trong lập trình đa luồng vì nó làm dữ liệu của bạn bị sai lệch một cách âm thầm mà không hề có báo lỗi (Exception).

---

### 1. Bản chất của Race Condition
Race condition xảy ra khi kết quả cuối cùng của một chương trình phụ thuộc vào thứ tự thực thi (timing) của các luồng.

Ví dụ kinh điển nhất là phép toán `count++`. Trong Java, `count++` trông có vẻ là 1 bước, nhưng thực tế CPU phải làm 3 bước:
1.  **Read:** Đọc giá trị của `count` từ bộ nhớ (giả sử là 10).
2.  **Modify:** Tăng giá trị đó lên 1 (10 + 1 = 11).
3.  **Write:** Ghi giá trị 11 ngược lại vào bộ nhớ.

**Kịch bản lỗi:**
* Luồng A đọc `count = 10`.
* Ngay lúc đó, luồng B cũng nhảy vào đọc `count = 10`.
* Luồng A tăng lên 11 và ghi vào.
* Luồng B cũng tăng giá trị nó đang cầm (10) lên 11 và ghi đè vào.
* **Kết quả:** Lẽ ra phải là 12, nhưng cuối cùng chỉ là 11. Một lần tăng đã bị "bốc hơi"!

---

### 2. Demo Race Condition (Bạn có thể test thử ngay)
Hãy chạy đoạn code này với 100 luồng, mỗi luồng tăng 1000 lần. Lẽ ra kết quả phải là 100,000, nhưng bạn sẽ thấy nó luôn nhỏ hơn.

```java
	public class RaceConditionDemo {
	    private int count = 0;

	    public void doWork() throws InterruptedException {
	        Thread[] threads = new Thread[100];

	        for (int i = 0; i < 100; i++) {
	            threads[i] = new Thread(() -> {
	                for (int j = 0; j < 1000; j++) {
	                    count++; // Race condition xảy ra ở đây
	                }
	            });
	            threads[i].start();
	        }

	        for (Thread t : threads) t.join();

	        System.out.println("Kết quả cuối cùng: " + count);
	        // Chắc chắn sẽ nhỏ hơn 100.000
	    }
	}
```

---

### 3. Tại sao `volatile` không cứu được Race Condition?
Nhiều bạn nghĩ: *"Thì thêm volatile vào biến count là xong!"*. **Sai.**

`volatile` chỉ đảm bảo **Visibility** (luồng B thấy luồng A vừa ghi 11). Nhưng nó không đảm bảo tính **Atomicity** (Nguyên tử). Nếu luồng B đã lỡ đọc số 10 rồi, thì dù luồng A có ghi 11 nhanh đến mấy, luồng B vẫn sẽ cầm số 10 đó để tăng lên 11 và ghi đè lại.

---

### 4. Ba cách giải quyết Race Condition

#### Cách 1: Dùng `synchronized` (Locking)
Khóa cửa lại, mỗi lần chỉ cho 1 ông vào làm đủ 3 bước (Read-Modify-Write) rồi mới cho ông khác vào.

**Java**
```java
public synchronized void increment() {
    count++;
}
```
* **Ưu điểm:** Cực kỳ an toàn.
* **Nhược điểm:** Hiệu năng giảm vì các luồng phải đứng đợi nhau (Blocking).

#### Cách 2: Dùng Atomic classes (Non-blocking)
Đây là cách "xịn" nhất cho bài toán đếm số. Java cung cấp các lớp như `AtomicInteger`, `AtomicLong`.

**Java**
```java
    private AtomicInteger count = new AtomicInteger(0);

        public void increment() {
            count.incrementAndGet(); // Sử dụng cơ chế CAS (Compare-And-Swap) ở mức phần cứng
        }
```
* **Cơ chế CAS:** Nó sẽ thử ghi giá trị mới. Nếu lúc ghi mà thấy giá trị gốc đã bị đứa khác đổi mất rồi, nó sẽ đọc lại và thử lại từ đầu. Không cần "khóa cửa" nên cực nhanh.

#### Cách 3: Dùng Lock (`ReentrantLock`)
Tương tự `synchronized` nhưng linh hoạt hơn (có thể dùng `tryLock`).

---

### Tổng kết: Visibility vs Race Condition

| Lỗi | Tên gọi | Bản chất | Giải pháp |
| :--- | :--- | :--- | :--- |
| **Visibility** | Nhìn mờ | Đọc phải dữ liệu cũ từ Cache | `volatile`, `synchronized` |
| **Race Condition** | Đua nhau làm | Nhiều luồng cùng sửa đè lên nhau | `AtomicInteger`, `synchronized`, `Lock` |

> **Lời khuyên từ thực tế:** Trong dự án Java của bạn, nếu chỉ cần đếm số hoặc cộng dồn, hãy luôn ưu tiên `AtomicInteger` hoặc `LongAdder`. Chỉ dùng `synchronized` khi bạn cần bảo vệ một khối logic phức tạp gồm nhiều bước.
---
# Shared Resources (Tài nguyên dùng chung)

Trong lập trình đa luồng, **Shared Resources** là các vùng dữ liệu hoặc thực thể mà nhiều Thread cùng có quyền truy cập và chỉnh sửa. Đây chính là "chiến trường" nơi mọi vấn đề về đồng bộ hóa phát sinh.

---

### 1. Tài nguyên dùng chung là gì?
Tài nguyên dùng chung có thể là:
* **Biến (Variables):** Đặc biệt là các biến `static` hoặc biến `instance` (nằm trên vùng nhớ Heap).
* **Cấu trúc dữ liệu:** `List`, `Map`, `Set`.
* **File hoặc Database:** Khi nhiều Thread cùng ghi vào một file hoặc bản ghi.
* **Kết nối mạng (Network sockets).**

---

### 2. Vấn đề tranh chấp (Resource Contention)
Khi nhiều Thread cùng muốn thay đổi một tài nguyên tại cùng một thời điểm, chúng ta rơi vào tình trạng **Tranh chấp tài nguyên**. Nếu không được quản lý, nó sẽ dẫn đến 3 hậu quả nghiêm trọng:

* **A. Race Condition (Kết quả sai lệch):** Như chúng ta đã thảo luận, đây là khi các Thread "đua" nhau ghi đè lên kết quả của nhau.
* **B. Deadlock (Khóa chết):** Xảy ra khi Thread A giữ tài nguyên 1 và đợi tài nguyên 2, trong khi Thread B lại giữ tài nguyên 2 và đợi tài nguyên 1.
* **C. Starvation (Bỏ đói):** Một Thread không bao giờ có cơ hội tiếp cận tài nguyên vì các Thread khác có độ ưu tiên cao hơn luôn chiếm mất tài nguyên đó.

---

### 3. Cách quản lý Shared Resources an toàn
Để làm việc với tài nguyên dùng chung, bạn có 3 chiến thuật chính:

| Chiến thuật | Cách thực hiện | Đặc điểm |
| :--- | :--- | :--- |
| **Bất biến (Immutability)** | Dùng `final`, `String`, `LocalDate` | An toàn tuyệt đối. Tài nguyên không đổi nên không cần khóa. |
| **Cô lập (Isolation)** | Dùng `ThreadLocal` | Mỗi Thread có một bản copy riêng, không ai đụng chạm ai. |
| **Đồng bộ (Synchronization)** | `synchronized`, `Lock`, `Atomic` | Cho phép dùng chung nhưng phải có "cảnh sát" điều phối. |

---

### 4. Quy tắc "Vàng" khi dùng Shared Resources
Để tránh lỗi khi lập trình đa luồng, hãy luôn ghi nhớ:

1.  **Thu hẹp phạm vi (Minimize scope):** Chỉ khóa (lock) đúng đoạn code thực sự cần thay đổi tài nguyên chung. Đừng khóa cả một hàm lớn nếu không cần thiết.
2.  **Tránh chia sẻ (Avoid sharing):** Nếu có thể tính toán riêng biệt rồi mới gộp kết quả lại (như *Fork/Join Framework*), hãy làm thế.
3.  **Sử dụng Thread-Safe Collections:** Thay vì dùng `ArrayList` chung, hãy dùng `CopyOnWriteArrayList` hoặc `ConcurrentHashMap`.

> **Ví dụ về sự nguy hiểm:**
> Nếu bạn có một chiếc thẻ ngân hàng (Shared Resource) và hai người cùng rút tiền tại hai cây ATM khác nhau (hai Threads). Nếu hệ thống không xử lý tranh chấp, cả hai có thể cùng rút được 1 triệu dù trong tài khoản chỉ còn đúng 1 triệu.

---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="deadlock">Deadlock (Khóa chết) trong Lập trình đa luồng</a>
<details>
<summary>Click for details</summary>


**Deadlock (Khóa chết)** là một tình huống khi hai hoặc nhiều Thread bị đứng yên mãi mãi, vì mỗi Thread đều đang đợi tài nguyên mà Thread kia đang nắm giữ.

Nó giống như một ngã tư bị tắc nghẽn: Xe hướng Đông đợi xe hướng Bắc đi qua, nhưng xe hướng Bắc lại đang đợi xe hướng Đông nhường đường. Kết quả là không ai di chuyển được.

---

### 1. Kịch bản tạo ra Deadlock kinh điển
Hãy tưởng tượng bạn có hai tài nguyên là **Cái dĩa** và **Cái thìa**:

* **Thread 1:** Chiếm cái dĩa thành công -> Đang định lấy cái thìa thì... (CPU chuyển sang Thread 2).
* **Thread 2:** Chiếm cái thìa thành công -> Đang định lấy cái dĩa thì thấy Thread 1 đang cầm. Thread 2 dừng lại đợi Thread 1 nhả dĩa.
* **Quay lại Thread 1:** Nó vẫn đang đợi lấy cái thìa mà Thread 2 đang cầm để ăn.

Cả hai Thread sẽ rơi vào trạng thái "chờ đợi vô tận". Chương trình của bạn sẽ bị treo (freeze) mà không có bất kỳ báo lỗi nào.

---

### 2. Ví dụ bằng Code Java

```java
	public class DeadlockDemo {
	    public static void main(String[] args) {
	        Object lock1 = new Object();
	        Object lock2 = new Object();

	        Thread t1 = new Thread(() -> {
	            synchronized (lock1) {
	                System.out.println("Thread 1: Đang giữ lock 1...");
	                try { Thread.sleep(100); } catch (InterruptedException e) {}

	                System.out.println("Thread 1: Đang đợi lock 2...");
	                synchronized (lock2) {
	                    System.out.println("Thread 1: Đã lấy được cả 2 lock!");
	                }
	            }
	        });

	        Thread t2 = new Thread(() -> {
	            synchronized (lock2) {
	                System.out.println("Thread 2: Đang giữ lock 2...");
	                try { Thread.sleep(100); } catch (InterruptedException e) {}

	                System.out.println("Thread 2: Đang đợi lock 1...");
	                synchronized (lock1) {
	                    System.out.println("Thread 2: Đã lấy được cả 2 lock!");
	                }
	            }
	        });

	        t1.start();
	        t2.start();
	    }
	}
```

---

### 3. 4 điều kiện cần để Deadlock xảy ra (Coffman Conditions)
Deadlock chỉ xảy ra khi hội tụ đủ 4 yếu tố sau:

1.  **Mutual Exclusion:** Tài nguyên không thể dùng chung (tại một thời điểm chỉ 1 Thread được giữ).
2.  **Hold and Wait:** Thread đang giữ ít nhất một tài nguyên và đang đợi thêm tài nguyên khác.
3.  **No Preemption:** Không ai có quyền "giật" tài nguyên từ tay Thread đang giữ.
4.  **Circular Wait:** Có một chuỗi các Thread đợi nhau tạo thành một vòng tròn.

---

### 4. Cách phòng tránh Deadlock
Lập trình viên chuyên nghiệp thường dùng các mẹo sau để "diệt" Deadlock:

* **Thứ tự khóa nhất quán (Lock Ordering):** Đây là cách phổ biến nhất. Luôn ép các Thread phải lấy khóa theo một thứ tự cố định (ví dụ: luôn lấy `lock1` trước rồi mới đến `lock2`).
* **Sử dụng `tryLock` (Timeout):** Thay vì dùng `synchronized`, hãy dùng `ReentrantLock.tryLock(timeout)`. Nếu sau một khoảng thời gian không lấy được khóa, Thread sẽ từ bỏ và nhả các khóa đang giữ để luồng khác chạy.
* **Tránh giữ quá nhiều khóa:** Thiết kế code sao cho một Thread chỉ cần giữ tối thiểu số lượng khóa cần thiết.

---

### Tổng kết
Deadlock là lỗi logic cực kỳ khó debug vì nó không làm sập ứng dụng ngay mà chỉ làm nó "ngừng thở". Cách tốt nhất để xử lý Deadlock là phòng bệnh hơn chữa bệnh bằng cách thiết kế thứ tự chiếm hữu tài nguyên thật chặt chẽ.


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="livelock">Java Concurrency: Hiểu về Livelock</a>
<details>
<summary>Click for details</summary>


Trong thế giới đa luồng, nếu **Deadlock** là trạng thái "đứng hình" hoàn toàn, thì **Livelock** là trạng thái "nhảy múa nhưng không đi đến đâu".

Nói một cách dễ hiểu, Livelock giống như khi hai người đi đối diện nhau trong một hành lang hẹp:
1. Người A tránh sang trái để nhường đường.
2. Cùng lúc đó, người B cũng tránh sang phải để nhường.
3. Cả hai lại chạm mặt nhau ở vị trí mới và tiếp tục lặp lại hành động này mãi mãi.

Họ không bị "đứng yên" (không bị chặn/block), họ vẫn đang hoạt động (active), nhưng công việc thì không tiến triển được tí nào.

## 1. Đặc điểm của Livelock
* **Không bị Block:** Các luồng (threads) không ở trạng thái `WAITING` hay `BLOCKED`. Chúng vẫn tiêu tốn CPU.
* **Phản ứng liên tục:** Các luồng thay đổi trạng thái liên tục để đáp ứng lại hành động của luồng khác.
* **Không có tiến triển:** Mặc dù luồng đang chạy, nhưng mục tiêu cuối cùng của chương trình không bao giờ đạt được.

## 2. Ví dụ minh họa (Java)
Hãy tưởng tượng ví dụ về hai người lịch sự nhường nhau một cái thìa để ăn. Nếu cả hai đều quá "lịch sự", họ sẽ đẩy cái thìa qua lại mãi mãi.

```java
	class Spoon {
	    private Thread owner;
	    public Spoon(Thread owner) { this.owner = owner; }
	    public synchronized void use() { System.out.println(owner.getName() + " đang ăn!"); }
	    public synchronized void setOwner(Thread owner) { this.owner = owner; }
	    public Thread getOwner() { return owner; }
	}

	class Diner {
	    private String name;
	    private boolean isHungry = true;

	    public void eatWith(Spoon spoon, Diner spouse) {
	        while (isHungry) {
	            // Nếu mình không cầm thìa, đợi một chút rồi kiểm tra lại
	            if (spoon.getOwner() != Thread.currentThread()) {
	                try { Thread.sleep(1); } catch (InterruptedException e) {}
	                continue;
	            }

	            // Nếu mình cầm thìa nhưng người kia cũng đang đói, mình nhường thìa
	            if (spouse.isHungry) {
	                System.out.println(name + ": Anh/em ăn trước đi, em/anh nhường!");
	                spoon.setOwner(spouse.getThread());
	                continue;
	            }

	            // Chỉ ăn khi người kia không đói
	            spoon.use();
	            isHungry = false;
	            System.out.println(name + ": Đã ăn xong!");
	        }
	    }
	}
```

## 3. Cách khắc phục Livelock
Khác với Deadlock (giải quyết bằng cách sắp xếp thứ tự lock), Livelock thường được xử lý bằng các chiến thuật sau:

* **Introduce Randomness (Thêm tính ngẫu nhiên):** Tương tự giao thức Ethernet, mỗi luồng sẽ đợi một khoảng thời gian ngẫu nhiên trước khi thử lại. Điều này phá vỡ vòng lặp phản ứng đồng bộ.
* **Thứ tự ưu tiên (Priority):** Thiết lập một luồng có quyền ưu tiên cao hơn để nó hoàn thành việc trước thay vì nhường nhịn lẫn nhau liên tục.

## 4. So sánh nhanh: Deadlock vs Livelock

| Tiêu chí | Deadlock | Livelock |
| :--- | :--- | :--- |
| **Trạng thái luồng** | Bị kẹt (`Waiting`/`Blocked`) | Đang chạy (`Active`) |
| **Tài nguyên CPU** | Không tốn CPU | Tốn rất nhiều CPU |
| **Nguyên nhân** | Chờ đợi lẫn nhau | Phản ứng với nhau quá mức |
| **Hình ảnh trực quan** | Xe tắc cứng giữa ngã tư | Hai người né nhau mãi không xong |

---
*Lời khuyên: Livelock khó phát hiện hơn Deadlock vì luồng vẫn đang "chạy". Hãy sử dụng các công cụ giám sát CPU để phát hiện các luồng hoạt động bất thường mà không sinh ra kết quả.*

</details>

- [Quay lại đầu trang](#back-to-top)