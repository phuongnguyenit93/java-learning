<a id="back-to-top"></a>

# Java Memory Model

## Menu
- [Atomicity, visibility và ordering trong Java](#java-memory-model-001)
- [Happens-before và vai trò trong tính đúng đắn](#java-memory-model-002)
- [Program order và các quan hệ happens-before](#java-memory-model-003)
- [Quy tắc lock/unlock của monitor](#java-memory-model-004)
- [Quy tắc write/read của volatile](#java-memory-model-005)
- [Thread.start(), Thread.join() và quan hệ đồng bộ](#java-memory-model-006)
- [Safe publication và nguy cơ đọc đối tượng khởi tạo dở](#java-memory-model-007)
- [Trường final và bảo đảm khởi tạo đúng](#java-memory-model-008)
- [Data race và hành vi quan sát khó dự đoán](#java-memory-model-009)
- [Reordering của compiler, CPU và bộ nhớ](#java-memory-model-010)
- [Giới hạn của volatile với compound actions](#java-memory-model-011)
- [Double-checked locking và điều kiện dùng volatile](#java-memory-model-012)
- [Phân biệt tính hiển thị và loại trừ tương hỗ](#java-memory-model-013)

## <a id="java-memory-model-001">Atomicity, visibility và ordering trong Java</a>

<details>
<summary>Xem chi tiết</summary>

# Atomicity, visibility và ordering trong Java

## 1. Ba bảo đảm khác nhau của Java Memory Model

Khi hai thread cùng sử dụng một trạng thái có thể thay đổi, lập trình viên cần trả lời ba câu hỏi:

- **Atomicity (tính nguyên tử):** một thao tác có được thực hiện như một đơn vị không thể xen vào giữa không? Thread khác có thể quan sát trạng thái cập nhật dở hay không?
- **Visibility (khả năng nhìn thấy):** khi thread A ghi một giá trị, điều gì bảo đảm thread B đọc được giá trị đã công bố?
- **Ordering (thứ tự):** các hành động có bắt buộc được quan sát theo quan hệ thứ tự nhất định không, dù compiler/JIT và CPU có thể tối ưu việc thực thi?

**Java Memory Model (JMM)** xác định kết quả đọc/ghi liên luồng nào hợp lệ theo ngôn ngữ Java, cùng những bảo đảm khi dùng `volatile`, `synchronized` và các API concurrency. Đây là mô hình ngôn ngữ; không nên giản lược mọi vấn đề thành “dữ liệu nằm ở CPU cache” vì JMM áp dụng trên các kiến trúc máy khác nhau.

Ba tính chất liên quan nhưng không thay thế nhau. Một biến `volatile` có thể bảo đảm visibility và ordering cho giao thức công bố dữ liệu, nhưng `counter++` trên chính biến đó vẫn không atomic. Một khóa bảo vệ việc cập nhật hai trường có thể giúp giữ bất biến, trong khi hai biến `AtomicInteger` độc lập không tự tạo một giao dịch chung.

### Bản đồ tư duy

| Thuộc tính | Câu hỏi cần kiểm tra | Cơ chế Java thường dùng |
| --- | --- | --- |
| Atomicity | Ai có thể chen vào giữa các bước cập nhật? | `synchronized`, `Lock`, `AtomicInteger.incrementAndGet()` |
| Visibility | Ghi từ thread này khi nào được thread kia nhìn thấy theo bảo đảm JMM? | `volatile`, monitor acquire/release, `join()`, `Future.get()` |
| Ordering | Tác động của các hành động được ràng buộc quan sát theo thứ tự nào? | Quan hệ happens-before, program order, volatile, lock/unlock |

Bài này chỉ xét concurrency và JMM của **Java nói chung**. Các cơ chế đồng bộ ở cấp framework không thuộc phạm vi ở đây.

## 2. Atomicity: “một câu lệnh” không nhất thiết là “một thao tác”

### 2.1. Vì sao `count++` có thể làm mất lần tăng?

~~~java
// Đoạn minh họa trong một đối tượng dùng chung; không phải chương trình độc lập.
int count = 0;

// Hai thread cùng thực thi:
count++;
~~~

Về logic, `count++` đọc giá trị cũ, cộng một, rồi ghi kết quả. Nếu A và B cùng đọc 0, cả hai có thể ghi 1. Hai lần tăng dự kiến cho kết quả 2 lại chỉ còn 1. Đây là **lost update**.

Một lần đọc hoặc ghi `int` là nguyên tử ở mức một thao tác, nhưng chuỗi **đọc–sửa–ghi** không mặc nhiên nguyên tử. `volatile int count` vẫn không làm `count++` thành atomic.

Với `long` và `double` *không khai báo volatile*, đặc tả ngôn ngữ còn cho phép một lần đọc/ghi 64 bit bị chia thành hai nửa 32 bit trên một số triển khai. Ngược lại, mọi lần đọc/ghi `volatile long` hay `volatile double` được bảo đảm nguyên tử. Không nên suy ra từ hành vi của một JVM hiện đại rằng mọi triển khai đều mạnh hơn quy định ngôn ngữ.

### 2.2. Ví dụ Java 17+: mô phỏng lost update có kiểm soát

Race condition ngẫu nhiên có thể không xuất hiện trong nhiều lượt chạy. Để nhìn thấy lỗi rõ ràng, ví dụ này dùng `CountDownLatch` để buộc cả hai worker **đọc giá trị cũ trước khi bất kỳ worker nào ghi**. Đây là thí nghiệm cố ý dựng một thứ tự xen kẽ; không phải cách cập nhật nên dùng trong ứng dụng.

Tạo tệp `AtomicityDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicityDemo {
    private static int ordinary = 0;

    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch bothRead = new CountDownLatch(2);
        AtomicInteger safe = new AtomicInteger(0);

        Runnable job = () -> {
            int snapshot = ordinary;
            bothRead.countDown();

            try {
                bothRead.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }

            ordinary = snapshot + 1;
            safe.incrementAndGet();
        };

        Thread first = new Thread(job, "writer-1");
        Thread second = new Thread(job, "writer-2");
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("ordinary = " + ordinary);
        System.out.println("atomic = " + safe.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AtomicityDemo.java
java AtomicityDemo
~~~

Stdout khi các worker hoàn tất:

~~~text
ordinary = 1
atomic = 2
~~~

Cả hai đọc `ordinary == 0` rồi mới được phép ghi 1, nên mất một lần tăng. Trong khi đó, `AtomicInteger.incrementAndGet()` thực hiện thao tác đọc–sửa–ghi nguyên tử, nên giá trị cuối là 2. `join()` bảo đảm thread `main` chờ cả hai worker hoàn tất trước khi in kết quả.

Ví dụ cố tình tách thao tác thành hai pha. Trong chương trình thực, nếu việc cập nhật một biến không có khóa hoặc atomic, kết quả sai có thể chỉ xuất hiện với một số lịch chạy nhất định.

### 2.3. Chọn atomic classes khi nào?

Các loại `AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `AtomicReference<T>` cho phép các phép toán nguyên tử trên một trạng thái tương ứng. Ví dụ:

~~~java
// Đoạn minh họa, không phải chương trình độc lập.
AtomicInteger requests = new AtomicInteger();
int current = requests.incrementAndGet();
boolean changed = requests.compareAndSet(1, 2);
~~~

`compareAndSet(expected, update)` (CAS) chỉ cập nhật nếu giá trị hiện tại khớp kỳ vọng. Một thuật toán CAS có thể phải thử lại khi nhiều thread tranh chấp; với `updateAndGet(...)`, hàm cập nhật cũng có thể bị gọi lại. Hàm đó nên không tạo side effect như gửi email, ghi tệp hoặc giao dịch mạng.

Atomic cho **một biến** không bảo vệ một bất biến trải trên **nhiều biến**: trừ số dư tài khoản A rồi cộng tài khoản B bằng hai thao tác atomic riêng vẫn để thread khác có thể quan sát trạng thái ở giữa. Khi cần một đơn vị cập nhật xuyên nhiều trường, hãy dùng một khóa chung hoặc thay thế nguyên tử toàn bộ một snapshot bất biến theo thiết kế phù hợp.

CAS còn có thể gặp **ABA**: một giá trị đi A → B → A và phép so sánh chỉ nhìn thấy A, mặc dù trạng thái đã thay đổi ở giữa. Nếu lịch sử thay đổi là một phần của bất biến, phải chọn giao thức khác hoặc phiên bản hóa trạng thái.

## 3. Visibility: ghi rồi chưa chắc thread khác đọc thấy đúng

### 3.1. Tình huống cờ dừng không an toàn

~~~java
// Minh họa không an toàn: running là biến thường được dùng bởi hai thread.
boolean running = true;

// Worker:
while (running) {
    processWork();
}

// Thread điều khiển:
running = false;
~~~

Nếu không có đồng bộ giữa hai bên, JMM không bảo đảm worker sẽ quan sát lần ghi `false` của thread điều khiển. Compiler/JIT và CPU có thể tối ưu trong phạm vi JMM, và việc quan sát thấy code “chạy đúng” trên một máy không chứng minh chương trình đúng theo đặc tả.

Với giao thức cờ đơn giản, có thể dùng:

~~~java
// Đoạn minh họa trong class chia sẻ giữa hai thread:
private volatile boolean running = true;
~~~

`volatile` thiết lập bảo đảm visibility và ordering khi hai thread ghi/đọc biến này theo đúng giao thức. Nhưng **visibility không phải liveness**: nếu worker đang block trên I/O không kiểm tra cờ hoặc không được lập lịch, chỉ đổi cờ không khiến nó dừng ngay. Đối với thao tác chờ dài, nên sử dụng primitive điều phối hoặc interrupt phù hợp thay vì spin vô thời hạn.

### 3.2. Happens-before: cách diễn đạt chính xác

**Happens-before (HB)** là quan hệ thứ tự logic của JMM. Nó cho phép xác định liệu một hành động được bảo đảm diễn ra trước hành động khác trong mô hình quan sát bộ nhớ hay không. Không nên xem HB chỉ là thứ tự trên đồng hồ hoặc thứ tự các dòng log.

Những cạnh HB quan trọng:

| Quy tắc | Bảo đảm |
| --- | --- |
| **Program order** | Trong một thread, các hành động trước trong thứ tự chương trình HB hành động sau |
| **Monitor** | Unlock trên một monitor HB lần lock tiếp theo của thread khác trên cùng monitor |
| **Volatile** | Ghi volatile synchronizes-with lần đọc volatile tương ứng sau đó theo thứ tự đồng bộ |
| **Start** | Lời gọi `Thread.start()` HB các hành động của thread được khởi chạy |
| **Join** | Các hành động của worker HB hành động của thread khác sau khi `join()` hoàn thành thành công |
| **CountDownLatch** | Hành động trước `countDown()` HB hành động sau `await()` trả về thành công |
| **Executor/Future** | Theo hợp đồng memory consistency của API, hành động trước submit HB task; hành động của task HB hành động tiếp theo sau `Future.get()` |

HB có tính **bắc cầu**: nếu A HB B và B HB C thì A HB C. Điều này đặc biệt quan trọng trong cơ chế **safe publication**: ghi dữ liệu trước rồi công bố một cờ volatile, thread khác đọc cờ đó và sau đó đọc dữ liệu.

Thấy một giá trị mới mà không có giao thức HB đáng tin cậy không chứng minh mọi lần chạy tiếp theo đều đúng. Ngược lại, `Thread.sleep(...)` không tự tạo quan hệ HB; việc chờ một khoảng thời gian chỉ là ảnh hưởng đến lịch chạy.

### 3.3. Ví dụ Java 17+: công bố dữ liệu qua volatile

Tạo tệp `VolatilePublicationDemo.java`:

~~~java
public class VolatilePublicationDemo {
    private static int payload;
    private static volatile boolean ready;

    public static void main(String[] args)
            throws InterruptedException {
        Thread reader = new Thread(() -> {
            while (!ready) {
                Thread.onSpinWait();
            }
            System.out.println("payload = " + payload);
        }, "reader");

        reader.start();

        payload = 42;
        ready = true;

        reader.join();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac VolatilePublicationDemo.java
java VolatilePublicationDemo
~~~

Khi reader được lập lịch thực thi và đọc thấy `ready == true`, stdout là:

~~~text
payload = 42
~~~

Luồng `main` ghi `payload = 42` trước khi ghi `ready = true`. Thread reader chỉ đọc `payload` sau khi quan sát lần ghi volatile đó. Các quan hệ program order, synchronizes-with và tính bắc cầu tạo HB từ ghi `payload` tới đọc `payload`. Vì vậy dữ liệu được công bố an toàn mặc dù `payload` không khai báo volatile.

`Thread.onSpinWait()` chỉ là một gợi ý CPU/JVM cho vòng chờ ngắn; **không tự tạo đồng bộ hoặc HB**. Ví dụ nhằm minh họa giao thức công bố một lần. Trong ứng dụng thực, dùng `CountDownLatch`, `BlockingQueue`, `Future` hoặc điều kiện có khả năng chờ/hủy thích hợp, thay vì để vòng spin vô hạn. Không tự tái sử dụng bằng cách liên tục đặt `ready = false` rồi bật lại mà không thiết kế thêm giao thức trạng thái.

## 4. Ordering: thứ tự mã nguồn và quan sát liên luồng

### 4.1. Optimizer có thể đổi cách thực thi trong phạm vi JMM

Trong một thread, Java giữ ngữ nghĩa phù hợp với **as-if-serial**: tối ưu không được làm sai kết quả mà thread đơn lẻ được phép quan sát. Điều đó không có nghĩa mọi câu lệnh phải được CPU thực hiện vật lý theo đúng trình tự nguồn.

Khi các thread truy cập shared mutable state mà thiếu đồng bộ, những kết quả quan sát liên luồng có thể rộng hơn tập kết quả mà người đọc mã tuần tự dự đoán. Không nên suy luận rằng “dòng 1 xuất hiện trước dòng 2” đương nhiên tạo quan hệ visibility cho thread khác.

### 4.2. Bài toán hai biến và kết quả khó trực giác

Giả sử `x = 0` và `y = 0` ban đầu. Hai thread thực hiện:

~~~java
// Minh họa tư duy, không phải chương trình độc lập.
// Thread A:               // Thread B:
// x = 1;                  y = 1;
// r1 = y;                 r2 = x;
~~~

Nếu `x` và `y` là biến thường, không có HB giữa hai luồng, JMM cho phép execution trong đó `r1 == 0` **và** `r2 == 0`. Nếu mọi hành động phải xen kẽ hoàn toàn theo một thứ tự tuần tự tổng thể mà vẫn giữ program order của mỗi thread, kết quả đó sẽ không xảy ra. Đây là ví dụ giúp nhận ra hệ quả của data race và thiếu ràng buộc ordering.

Nếu cả `x` và `y` đều khai báo `volatile`, các truy cập volatile tham gia một thứ tự đồng bộ tổng nhất quán với program order; kết quả cả hai đọc 0 không còn hợp lệ với trình tự nêu trên.

**Lưu ý quan trọng:** “Được JMM cho phép” không có nghĩa một lần chạy trên máy cụ thể chắc chắn sẽ hiển thị kết quả đó. Đừng viết test kỳ vọng nhất định thấy hiệu ứng reordering; bài học phải dựa trên hợp đồng của mô hình bộ nhớ.

### 4.3. Data race và sequential consistency

Hai hành động truy cập **cùng một biến**, ít nhất một là ghi, và không được sắp thứ tự bằng HB, tạo nên một **data race**. Ví dụ hai thread cùng thực hiện `count++` trên một biến thường có thể tạo data race.

Với chương trình **được đồng bộ đúng** (không có data race theo tiêu chuẩn JMM), Java cung cấp bảo đảm mạnh về **sequential consistency**: có thể hiểu execution như một sự xen kẽ các hành động theo một thứ tự tổng thể, giữ thứ tự chương trình của mỗi thread.

Sequential consistency không nói thread nào chạy trước, không loại bỏ deadlock, starvation hoặc lỗi logic nghiệp vụ. Nó chỉ thu hẹp tập hành vi bộ nhớ hợp lệ để lập trình viên suy luận một cách nhất quán.

## 5. `volatile` giải quyết và không giải quyết điều gì?

`volatile` thường phù hợp cho cờ trạng thái một chiều hoặc công bố dữ liệu theo một giao thức được thiết kế. Nó bảo đảm các lần đọc/ghi riêng lẻ của biến volatile có ngữ nghĩa visibility và ordering tương ứng; đọc/ghi `volatile long/double` cũng luôn nguyên tử.

Nhưng các câu sau đều **không được `volatile` tự bảo đảm**:

- `volatileCounter++` là một read–modify–write atomic.
- Cập nhật hai trường có thể được quan sát như một transaction.
- `volatile List` biến `ArrayList` được tham chiếu thành thread-safe.
- Thread khác đang block sẽ tự thức dậy ngay sau khi cờ đổi.
- Mọi dữ liệu mutable đằng sau một reference volatile trở thành bất biến hoặc tự bảo vệ khỏi concurrent modification.
- Side effect lên database, file hoặc mạng được rollback khi thread gặp lỗi.

~~~java
// Minh họa sai: dù volatile nhưng phép tăng không nguyên tử.
private volatile int counter = 0;

void increment() {
    counter++;
}
~~~

Muốn đếm chính xác, hãy dùng `AtomicInteger.incrementAndGet()` hoặc synchronized/lock. Với thuật toán cần đọc tình trạng rồi quyết định cập nhật, cần bảo vệ toàn bộ **check-then-act**. Không thể chỉ đặt volatile vào một biến rồi mong chuỗi điều kiện và cập nhật trở thành atomic.

### Safe publication và final fields

Một object nên được khởi tạo hoàn toàn trước khi thread khác dùng nó. Những mẫu công bố an toàn thường là gán tham chiếu qua volatile/atomic, đưa object qua concurrent queue, truy cập dưới khóa hoặc tạo dữ liệu trước khi gọi `Thread.start()`.

Các trường `final` được JMM trao bảo đảm khởi tạo đặc biệt khi constructor thực thi đúng và tham chiếu `this` không thoát trong quá trình xây dựng. Tuy nhiên, `final List<String> items` chỉ khiến **tham chiếu** `items` không thể gán lại, không làm danh sách bên trong bất biến. Nếu một object mutable vẫn có nhiều writer, cần phối hợp những writer đó.

Tránh đăng ký callback, đưa `this` vào cấu trúc dùng chung hoặc khởi chạy thread trong constructor trước khi object được khởi tạo xong.

## 6. `synchronized`: loại trừ lẫn nhau và bảo đảm HB

Một khối `synchronized` dùng **monitor lock**. Những thread tuân thủ cùng khóa nhận được:

1. **Mutual exclusion:** một thread giữ khóa, thread khác không thể đồng thời vào vùng khóa dùng cùng monitor.
2. **Visibility:** unlock từ thread trước có quan hệ HB với lock sau trên cùng monitor.
3. **Ordering:** quan hệ acquire/release giúp những hành động trong critical section được công bố theo giao thức khóa.

Cần bảo đảm mọi đường đọc/ghi cùng một bất biến đều tuân thủ **cùng khóa**. Nếu writer khóa nhưng reader đọc trực tiếp một trường từ bên ngoài, việc reader có được snapshot đúng không còn do khóa đó bảo đảm.

### Ví dụ Java 17+: bảo vệ bất biến hai biến

Tạo tệp `SynchronizedInvariantDemo.java`:

~~~java
public class SynchronizedInvariantDemo {
    static final class Balances {
        private int left = 100;
        private int right = 0;

        synchronized void move(int amount) {
            if (amount < 0 || amount > left) {
                throw new IllegalArgumentException("Invalid amount");
            }
            left -= amount;
            right += amount;
        }

        synchronized String snapshot() {
            return "left=" + left
                + ", right=" + right
                + ", total=" + (left + right);
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Balances balances = new Balances();
        Thread first = new Thread(() -> balances.move(40));
        Thread second = new Thread(() -> balances.move(60));

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println(balances.snapshot());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SynchronizedInvariantDemo.java
java SynchronizedInvariantDemo
~~~

Stdout khi cả hai worker hoàn tất:

~~~text
left=0, right=100, total=100
~~~

Vì mỗi `move` giữ khóa trong cả kiểm tra và hai thao tác cập nhật, mọi thứ tự thực hiện của hai thread đều hợp lệ: cả 40 và 60 đều chuyển được, tổng luôn 100. `snapshot()` cũng có cùng monitor, nên khi được gọi, nó không quan sát một lần chuyển đang cập nhật dở. `join()` giúp `main` in kết quả sau khi worker hoàn tất.

Ví dụ này chỉ mô tả dữ liệu **trong bộ nhớ một JVM**. `synchronized` không phải transaction database và không tự bảo vệ trạng thái nằm ở tiến trình khác.

### `Lock` và giới hạn của vùng khóa

`ReentrantLock` là một lựa chọn khác khi cần `tryLock()`, tính năng interruptible lock hoặc điều kiện chờ linh hoạt. Luôn giải phóng khóa trong `finally`:

~~~java
// Minh họa bên trong một phương thức; lock đã khai báo trước.
lock.lock();
try {
    updateSharedState();
} finally {
    lock.unlock();
}
~~~

Đừng giữ khóa khi thực hiện I/O chặn lâu nếu có thể tránh. Một vùng khóa quá rộng tăng contention; nhiều khóa lấy theo thứ tự khác nhau có thể gây deadlock. Tính đúng đắn của bất biến cần được xác lập trước, sau đó mới đo và tinh chỉnh hiệu năng.

## 7. Các API khác cũng có hợp đồng memory consistency

### 7.1. `Thread.start()` và `Thread.join()`

Các hành động trong caller trước khi gọi `thread.start()` HB những hành động của worker. Sau khi worker hoàn tất, caller đợi `thread.join()` thành công nhận bảo đảm HB đối với các hành động của worker.

~~~java
// Minh họa, không phải chương trình độc lập.
int[] result = new int[1];
Thread worker = new Thread(() -> result[0] = 99);
worker.start();
worker.join();  // Cần xử lý hoặc khai báo InterruptedException.
System.out.println(result[0]);  // 99 sau join thành công.
~~~

Không thay `join()` bằng `Thread.sleep(100)` chỉ vì bạn đoán worker đã có đủ thời gian chạy; sleep không phải cạnh HB giữa hai thread.

### 7.2. Executor và Future

Theo hợp đồng của `ExecutorService`, các hành động trước khi gửi task HB các hành động của task; các hành động của task HB hành động sau khi kết quả được lấy qua `Future.get()`. Dù vậy, **hai task độc lập** cùng chạy trong executor vẫn cần cơ chế riêng nếu chúng cập nhật chung một biến.

`Future.get()` có thể chặn và có thể ném `ExecutionException` khi task lỗi. `get(timeout, unit)` giới hạn thời gian caller chờ, không tự hủy một task đang chạy. Cần quản lý cancellation, timeout và exception rõ ràng.

### 7.3. CountDownLatch và BlockingQueue

`CountDownLatch` thiết lập quan hệ memory consistency từ hành động trước `countDown()` đến hành động sau `await()` thành công. `BlockingQueue` cung cấp cơ chế truyền phần tử và hợp đồng công bố dữ liệu phù hợp giữa producer và consumer. Các primitive này thường rõ ràng hơn việc tự chia sẻ nhiều cờ boolean bình thường rồi đoán trạng thái của nhau.

## 8. Thiết kế theo yêu cầu, không theo thói quen thêm volatile

| Trường hợp | Công cụ phù hợp | Cần kiểm tra |
| --- | --- | --- |
| Cờ dừng đơn giản, worker chủ động kiểm tra | `volatile boolean` | Thread có thực sự quay lại đọc cờ hoặc được đánh thức không? |
| Đếm số event độc lập | `AtomicInteger` hoặc `LongAdder` khi phù hợp | Có cần snapshot chính xác trong khi cập nhật không? |
| Chuyển giá trị giữa hai trường, giữ invariant | `synchronized` hoặc `Lock` | Tất cả đường đọc/ghi có theo cùng giao thức không? |
| Công bố dữ liệu đã khởi tạo xong | Volatile reference, immutable snapshot, queue, start/join | Có để reference thoát trước khi khởi tạo xong không? |
| Nhiều thread sửa collection | Concurrent collection phù hợp | Một chuỗi thao tác “kiểm tra rồi ghi” có cần atomic không? |
| Không cần trạng thái chia sẻ | Thread confinement, immutable values, message passing | Có thể thiết kế tránh lock ngay từ đầu không? |

`LongAdder` giúp giảm tranh chấp cho nhiều phép cộng trong những trường hợp thống kê. Tuy nhiên, `sum()` không phải snapshot nguyên tử khi các update vẫn đang diễn ra; không dùng nó như bộ đếm transaction cần kiểm tra điều kiện chính xác tức thời.

Với dữ liệu chia sẻ, điều quan trọng là thiết kế **giao thức sở hữu và công bố** trước khi chọn công cụ; code có thể không có data race nhưng vẫn có deadlock, starvation hoặc lỗi nghiệp vụ.

## 9. Sai lầm phổ biến

**“Một câu lệnh Java thì atomic.”** Không đúng với `count++`, `map.get(k)` rồi `map.put(k, v)` hoặc các chuỗi check-then-act.

**“Có volatile là đủ thread-safe.”** Không đúng với nhiều biến phải nhất quán hoặc một object mutable được sửa đồng thời.

**“Một lần đọc đã thấy giá trị mới chứng minh visibility được bảo đảm.”** Chạy thử đúng không thay thế được phân tích HB; tối ưu JIT và lịch chạy có thể khác ở lần khác.

**“Thread.sleep tạo ordering.”** Sleep ảnh hưởng lịch chạy chứ không tạo quan hệ đồng bộ HB cần thiết.

**“Lúc ghi dùng synchronized, lúc đọc không cần.”** Reader có thể quan sát trạng thái không nhất quán nếu đọc ngoài giao thức khóa.

**“Dùng hai AtomicInteger là một transaction.”** Mỗi lần cập nhật một biến là atomic; hai cập nhật chưa chắc được người khác quan sát cùng lúc.

**“Không thấy race trong stress test nghĩa là đã chứng minh đúng.”** Test có thể phát hiện lỗi nhưng không kiểm tra được mọi execution hợp lệ. Cần lập luận theo JMM và rà soát mọi đường truy cập.

**“Data-race-free nghĩa là ứng dụng không còn lỗi concurrency.”** Không đúng: deadlock, logic thứ tự nghiệp vụ, timeout và liveness vẫn có thể sai dù mỗi truy cập bộ nhớ được đồng bộ.

## 10. Kiểm thử và quan sát hành vi

Khi kiểm tra các tính chất của JMM, hãy đo **bất biến**, không chỉ đo chương trình có ném exception hay không:

1. Với **atomicity**, tổ chức nhiều writer cùng cập nhật, so kết quả với tổng dự kiến. Có thể dùng latch/barrier để chủ động tạo điều kiện tranh chấp, như `AtomicityDemo`.
2. Với **visibility**, xác định cạnh HB nào công bố dữ liệu. Test không nên dùng sleep làm tín hiệu duy nhất; cần timeout và cơ chế phối hợp đúng.
3. Với **ordering**, phân tích những execution được/không được JMM cho phép. Kết quả “có thể xảy ra” không nhất thiết xuất hiện trên mọi JVM.
4. Với **invariant nhiều trường**, thử reader quan sát trong lúc writer cập nhật, rồi kiểm tra toàn bộ snapshot có hợp lệ không.
5. Với hiệu năng, theo dõi lock contention, CPU, throughput, latency, thời gian chờ queue và thread states bằng profiler, thread dump hoặc Java Flight Recorder khi phù hợp.

Stress test và công cụ chuyên biệt như JCStress có thể giúp phát hiện một số hành vi liên luồng. Chúng hỗ trợ lập luận đúng đắn, không thay thế chứng minh theo các quy tắc JMM. Cũng cần phân biệt phép đo hiệu năng với kiểm thử an toàn bộ nhớ.

## 11. Câu hỏi tự kiểm tra

**1.** Vì sao `volatile int counter` vẫn có thể mất cập nhật khi hai thread cùng chạy `counter++`?

Vì `volatile` không biến tổ hợp đọc–cộng–ghi thành một phép atomic; hai thread có thể cùng đọc giá trị cũ. Sử dụng `AtomicInteger.incrementAndGet()` hoặc khóa phù hợp.

**2.** Một thread ghi `payload = 42` rồi `ready = true` với `ready` là volatile; thread khác đọc `ready == true` rồi đọc `payload`. Bảo đảm nào làm phép công bố an toàn?

Program order, quy tắc synchronizes-with của volatile và tính bắc cầu của happens-before tạo quan hệ HB từ ghi payload đến đọc payload.

**3.** Hai biến `AtomicInteger` quản lý hai số dư có tự bảo đảm tổng luôn đúng khi chuyển một lượng tiền giữa chúng không?

Không. Một phép update của từng biến nguyên tử, nhưng hai update không phải một transaction chung. Cần khóa hoặc biểu diễn snapshot bất biến có cập nhật nguyên tử phù hợp.

**4.** Vì sao chạy code thành công nhiều lần vẫn chưa chứng minh code thiếu đồng bộ là thread-safe?

Vì những lần chạy chỉ khám phá một phần lịch thực thi. Data race có thể chưa bộc lộ; tính đúng đắn phải được lập luận bằng các bảo đảm JMM và bất biến của chương trình.

**5.** Một thread chờ `Thread.sleep(200)` sau khi khởi động worker rồi đọc kết quả có chắc chắn thấy mọi cập nhật của worker không?

Không. Sleep không đợi worker hoàn tất và không tự tạo cạnh happens-before. Dùng `join()`, `Future.get()` hoặc primitive phù hợp với bài toán.

**Kết luận:** **Atomicity** bảo vệ tính toàn vẹn của một thao tác; **visibility** bảo đảm công bố dữ liệu; **ordering** ràng buộc thứ tự quan sát. Thiết kế concurrency đúng trong Java cần tạo đủ quan hệ happens-before và bảo vệ toàn bộ bất biến dùng chung, thay vì chỉ thêm `volatile` hoặc `synchronized` mà không hiểu phạm vi của chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-002">Happens-before và vai trò trong tính đúng đắn</a>

<details>
<summary>Xem chi tiết</summary>

# Happens-before và vai trò trong tính đúng đắn của Java Concurrency

Khi hai thread truy cập chung dữ liệu, không thể chỉ nhìn thứ tự câu lệnh trong mã nguồn để kết luận thread đọc sẽ thấy kết quả mới nhất. Java Memory Model (JMM) mô tả những cách quan sát bộ nhớ nào được phép khi chương trình chạy đồng thời. **Happens-before (HB)** là quan hệ thứ tự để lập luận rằng một nhóm thao tác ghi đã được công bố và có thể được quan sát đúng bởi các thao tác đọc về sau.

HB giúp trả lời các câu hỏi: Khi worker ghi dữ liệu rồi kết thúc, main đọc được gì? Ghi một cờ `volatile` có đủ để công bố dữ liệu trước đó không? Vì sao `synchronized` vừa tránh chen ngang vừa mang hiệu ứng bộ nhớ? Vì sao `Thread.sleep()` không giải quyết data race? Từ các câu hỏi đó, chúng ta có thể phân biệt **visibility**, **ordering**, **atomicity** và chứng minh chương trình đúng theo hợp đồng Java.

Đây là bài **Java Memory Model trong Java Concurrency nói chung**. Các cơ chế riêng của Spring nằm ngoài phạm vi.

## 1. Vấn đề: thứ tự trong source chưa đủ cho đa luồng

Xét trạng thái chia sẻ:

~~~java
final class Message {
    int data;
    boolean ready;
}
~~~

Writer:

~~~java
message.data = 42;
message.ready = true;
~~~

Reader:

~~~java
while (!message.ready) {
    // Đợi writer.
}
System.out.println(message.data);
~~~

Đây là **các mảnh minh họa lỗi**, chưa phải chương trình độc lập. Nếu `message` được truy cập bởi hai thread mà không đồng bộ, reader không có bảo đảm cần thiết rằng nó sẽ thấy `ready == true`, hoặc rằng khi thấy ready thì cũng nhìn được `data == 42`. Compiler và phần cứng có thể tối ưu một cách hợp lệ theo JMM, và các thao tác đọc/ghi thường không tự thiết lập việc công bố dữ liệu qua thread khác.

Vòng `while` còn là busy waiting: nó có thể chạy liên tục, lãng phí CPU. Thêm `Thread.sleep(10)`, `Thread.yield()` hoặc `Thread.onSpinWait()` có thể thay đổi tốc độ/lịch polling, nhưng **không tạo quan hệ HB** cần thiết.

Ta cần một giao thức đồng bộ thực sự: `volatile`, monitor `synchronized`, `Lock`, `CountDownLatch`, `Future`, `Thread.join()`, queue đồng thời hoặc API có memory-consistency contract rõ ràng.

### 1.1 Ba thuộc tính không đồng nghĩa

| Thuộc tính | Ý nghĩa | Lỗi thường gặp |
| --- | --- | --- |
| **Visibility** | Đảm bảo đọc quan sát cập nhật theo các ràng buộc JMM. | Thread đọc cờ hoặc dữ liệu cũ khi chưa được publish. |
| **Ordering** | Đảm bảo thứ tự quan sát cần thiết giữa các hành động. | Reader thấy tín hiệu nhưng chưa có bảo đảm payload đã publish. |
| **Atomicity** | Một thao tác/cụm thao tác quan trọng diễn ra không bị chen giữa theo giao thức. | Hai thread cùng thực hiện `count++` gây mất cập nhật. |

**HB là nền tảng visibility và ordering**. HB không tự biến phép tăng ghép thành atomic và không thay thế việc bảo vệ invariant nghiệp vụ.

## 2. Định nghĩa và cách đọc quan hệ happens-before

Ký hiệu `A →HB B` nghĩa là hành động A *happens-before* hành động B. Đây là **quan hệ thứ tự một phần** theo Java Memory Model, không phải cách đo A xảy ra trước B bao nhiêu mili giây theo đồng hồ. HB được hình thành bởi:

- **Program order:** trong cùng thread, những hành động đứng trước theo thứ tự chương trình tạo các cạnh HB đến những hành động sau.
- **Synchronizes-with:** những quan hệ đồng bộ giữa thread được JMM định nghĩa, ví dụ unlock→lock trên cùng monitor hoặc volatile write→subsequent volatile read.
- **Transitivity (bắc cầu):** nếu A →HB B và B →HB C thì A →HB C.

Hãy hình dung:

~~~text
Writer                              Reader
---------------------------         ----------------------------
ghi payload = 42                    ...
      |
      v
ghi volatile ready = true --------> đọc volatile ready == true
                                          |
                                          v
                                     đọc payload
~~~

Mũi tên ngang là cạnh đồng bộ của volatile khi reader đọc tín hiệu do writer công bố. Hai mũi tên dọc là program order. Theo tính bắc cầu, `ghi payload = 42 →HB đọc payload`. Nếu không có một writer khác thay đổi payload cạnh tranh, reader có cơ sở để thấy 42.

**Không được đồng nhất HB với lịch chạy vật lý.** JVM/CPU vẫn có thể tái sắp xếp instruction miễn giữ những kết quả quan sát hợp lệ theo JMM. Hai hành động có thể xảy ra cách nhau rất lâu mà vẫn thiếu cạnh HB nếu không có một cơ chế đồng bộ tương ứng.

**HB cũng không phải điều kiện đủ duy nhất cho mọi kết quả đọc:** nếu có nhiều lần ghi cùng biến và các quan hệ thứ tự khác, phải xét tất cả phép ghi cạnh tranh và quy tắc đọc giá trị của JMM. Khi kết luận reader thấy một giá trị cụ thể, luôn nói rõ những giả định như “chỉ một writer ghi biến này trước tín hiệu”.

## 3. Những quy tắc HB cơ bản của JMM

| Nguồn quan hệ | Cạnh happens-before quan trọng | Điều kiện |
| --- | --- | --- |
| Thứ tự trong thread | Hành động trước →HB hành động sau trong program order. | Cùng thread; không tự đồng bộ với thread khác. |
| Monitor (`synchronized`) | Unlock một monitor →HB mọi lock tiếp theo trên chính monitor ấy. | Cùng monitor; mọi đường truy cập phải dùng protocol phù hợp. |
| Biến `volatile` | Ghi volatile →HB những lần đọc volatile về sau của **cùng biến** trong synchronization order. | Không có tác dụng atomic cho phép `++` nhiều bước. |
| `Thread.start()` | Lời gọi `start()` →HB mọi hành động trong thread được khởi chạy. | Các phép ghi của caller trước start được công bố theo quy tắc. |
| Kết thúc thread | Mọi hành động trong worker →HB thread khác khi nó phát hiện worker kết thúc, chẳng hạn qua `join()` thành công. | Timed join hết hạn lúc worker còn sống không bảo đảm completion. |
| Interruption | `interrupt()` →HB điểm thread bị interrupt phát hiện tín hiệu đó. | Interruption là yêu cầu hợp tác, không phải ép dừng. |
| Khởi tạo mặc định | Default initialization của object →HB các hành động khác trên object. | Không chứng minh mọi field đã được constructor cấu hình đúng cách khi object publish không an toàn. |
| Tính bắc cầu | A →HB B và B →HB C ⇒ A →HB C. | Nối các cạnh program order và synchronization. |

Quan hệ *synchronizes-with* chỉ những cạnh đồng bộ cơ bản. *Happens-before* còn gồm thứ tự chương trình và chuỗi cạnh bắc cầu; bởi vậy có thể có HB từ một lần ghi thông thường trước volatile đến một lần đọc thông thường sau volatile mà không phải biến nào cũng mang từ khóa `volatile`.

Java còn định nghĩa *synchronization order*, một thứ tự tổng thể cho các synchronization actions phù hợp program order của mỗi thread. Không cần dự đoán toàn bộ thứ tự này khi viết ứng dụng, nhưng cần hiểu rằng không thể ghép tùy tiện một volatile write với bất kỳ volatile read nào để chứng minh điều mình muốn.

## 4. Dùng volatile để publish dữ liệu

Sửa class ban đầu:

~~~java
final class Message {
    int data;
    volatile boolean ready;
}
~~~

Writer thực hiện:

~~~java
message.data = 42;     // Ghi dữ liệu trước.
message.ready = true;  // Phát tín hiệu bằng volatile write.
~~~

Reader:

~~~java
if (message.ready) {   // Volatile read thấy true của writer.
    System.out.println(message.data);
}
~~~

Các mảnh trên giả định `message` đã được khai báo. Khi reader thấy tín hiệu thích hợp, cạnh volatile write→read cùng program order cho phép suy ra lần ghi `data = 42` xảy ra trước lần đọc `data` theo HB. Với một writer duy nhất và không có lần sửa cạnh tranh về `data`, reader có thể thấy giá trị 42.

**Thứ tự hai lệnh rất quan trọng.** Nếu writer đặt `ready = true` rồi mới ghi `data = 42`, reader có thể nhận tín hiệu trước khi dữ liệu được chuẩn bị. Nếu reader đọc `data` trước khi đọc `ready`, phép đọc ấy không nhận được bảo đảm publish từ tín hiệu phía sau.

**Giới hạn của volatile:**

- Một phép đọc/ghi volatile đơn lẻ có visibility và ordering theo JMM; nó không biến nhiều câu lệnh thành một giao dịch.
- `volatile int count; count++` vẫn có thể mất cập nhật vì `++` là read–modify–write.
- Một `volatile Message reference` không khiến mọi field bên trong Message tự được bảo vệ khi object tiếp tục bị sửa không đồng bộ sau khi publish.
- Cờ volatile không phải cơ chế chờ hiệu quả cho thời gian dài. Với giao thức thông báo/hoàn tất, ưu tiên blocking primitive như latch hoặc future.

### 4.1 Ví dụ runnable: công bố và quan sát bằng volatile

Ví dụ bên dưới dùng một volatile flag và vòng spin ngắn **chỉ để quan sát rõ cơ chế**; trong sản phẩm nên dùng blocking primitive. Worker cố ý được đợi bằng `join()` để chương trình đóng đúng vòng đời.

Tạo `VolatileHappensBeforeDemo.java`:

~~~java
public final class VolatileHappensBeforeDemo {
    private static int payload;
    private static volatile boolean ready;

    public static void main(String[] args) throws InterruptedException {
        Thread reader = new Thread(() -> {
            while (!ready) {
                Thread.onSpinWait();
            }
            System.out.println("Payload: " + payload);
        }, "reader");

        Thread writer = new Thread(() -> {
            payload = 42;
            ready = true;
        }, "writer");

        reader.start();
        writer.start();
        writer.join();
        reader.join();
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac VolatileHappensBeforeDemo.java
java VolatileHappensBeforeDemo
~~~

Kết quả khi thread được scheduler cấp cơ hội chạy bình thường:

~~~text
Payload: 42
~~~

`Thread.onSpinWait()` chỉ là CPU hint, **không** tạo HB. Chính write/read của biến `ready` là cạnh đồng bộ. Mặc dù có visibility đúng, vòng spin vẫn có thể chiếm CPU và bị starvation nếu scheduler không cho một bên chạy. Trong phần ứng dụng thực tế bên dưới, chúng ta dùng `CountDownLatch.await()` thay kiểu chờ này.

## 5. Synchronized: vừa loại trừ chen ngang vừa publish bộ nhớ

Một monitor lock đem lại hai tác dụng khác nhau:

1. **Mutual exclusion:** chỉ một thread giữ cùng monitor tại một thời điểm. Các phép ghi/đọc trong critical section được bảo vệ khỏi chen ngang bởi thread khác cũng dùng monitor ấy.
2. **Happens-before:** unlock một monitor xảy ra trước lần lock về sau trên **cùng monitor**. Trạng thái đã ghi dưới protocol được công bố theo JMM.

Ví dụ Java 17+ tạo `SynchronizedHappensBeforeDemo.java`:

~~~java
public final class SynchronizedHappensBeforeDemo {
    private static final Object LOCK = new Object();
    private static int total;

    public static void main(String[] args) throws InterruptedException {
        Runnable add = () -> {
            for (int i = 0; i < 1_000; i++) {
                synchronized (LOCK) {
                    total++;
                }
            }
        };

        Thread first = new Thread(add, "first");
        Thread second = new Thread(add, "second");
        first.start();
        second.start();
        first.join();
        second.join();

        synchronized (LOCK) {
            System.out.println("Total: " + total);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SynchronizedHappensBeforeDemo.java
java SynchronizedHappensBeforeDemo
~~~

Stdout:

~~~text
Total: 2000
~~~

Mọi phép `total++` được thực hiện trong cùng monitor `LOCK`, nên không có hai critical section chồng lên nhau. `join()` bảo đảm main đợi cả hai thread kết thúc và công bố những hành động của worker cho main; lần lấy lock cuối cũng tuân thủ monitor protocol.

Nếu chỉ giữ hai lệnh `join()` nhưng bỏ `synchronized` trong worker, kết quả có thể bị mất cập nhật. Join làm kết quả **được nhìn thấy sau khi thread đã chạy xong**, không sửa những race đã xảy ra **trong quá trình chạy**.

Nếu hai thread khóa hai object khác nhau, chúng không cùng một monitor và không có mutual exclusion chung. Đừng kết luận “cả hai nơi đều có synchronized” mà bỏ qua **khóa cụ thể** và **mọi đường truy cập** đến invariant.

## 6. start() và join(): hai chiều công bố theo vòng đời thread

Trong đa số bài toán worker đơn giản, `Thread.start()` và `Thread.join()` đã cung cấp các cạnh HB cần thiết.

Tạo `StartJoinVisibilityDemo.java`:

~~~java
public final class StartJoinVisibilityDemo {
    private static final class Job {
        int input;
        int output;
    }

    public static void main(String[] args) throws InterruptedException {
        Job job = new Job();
        job.input = 21;

        Thread worker = new Thread(() -> {
            job.output = job.input * 2;
        }, "calculator");

        worker.start();
        worker.join();

        System.out.println("Result: " + job.output);
    }
}
~~~

Chạy:

~~~bash
javac StartJoinVisibilityDemo.java
java StartJoinVisibilityDemo
~~~

Stdout:

~~~text
Result: 42
~~~

Đồ thị lập luận:

~~~text
main: job.input = 21
       |
       v  program order
main: worker.start()
       |
       v  start happens-before worker actions
worker: đọc input, ghi output = 42
       |
       v  termination detected by join
main: worker.join() kết thúc
       |
       v  program order
main: đọc output = 42
~~~

Mặc dù `input` và `output` chỉ là `int` thường, các lần truy cập theo đúng luồng start/join này có một quan hệ HB phù hợp. Không cần đánh dấu mọi trường là volatile nếu **không có thread khác truy cập cạnh tranh trái protocol**.

Lưu ý: `Thread.sleep(100)` của main sau `worker.start()` không thay thế `join()`. Nó chỉ chờ một khoảng thời gian, không bảo đảm worker đã kết thúc và không cung cấp cạnh completion. `join(timeout)` trả về mà worker còn sống cũng chưa bảo đảm toàn bộ kết quả đã publish.

Với API vòng đời, `Thread.isAlive()` trả `false` sau khi thread đã start và kết thúc có thể là một cách phát hiện termination theo JMM, nhưng việc liên tục polling `isAlive()` thường lãng phí CPU. Chọn `join()` để chờ trực tiếp.

## 7. Data race là gì?

Hai access ở các thread khác nhau **xung đột** nếu chúng truy cập cùng một biến và ít nhất một access là write. Khi chúng không được sắp thứ tự bởi HB, chương trình có **data race**.

Ví dụ:

~~~java
int counter; // Trường chia sẻ.

// Thread A:
counter++;

// Thread B:
counter++;
~~~

Đây là mảnh minh họa. Hai lần tăng mỗi lần gồm đọc giá trị cũ, tính giá trị mới, ghi lại; chúng có thể cùng đọc cùng giá trị ban đầu và ghi đè, dẫn đến mất cập nhật.

**JMM có ràng buộc cả chương trình có data race**, nên nói “data race khiến Java được phép làm bất kỳ điều gì” là không chính xác. Nhưng khi thiếu các cạnh HB cần thiết, trực giác “các lệnh source đã chạy theo đúng thứ tự nên chắc chắn đúng” không đủ để chứng minh kết quả.

Một chương trình **correctly synchronized** theo định nghĩa của JMM không có data race trong các execution sequentially consistent của nó. Chương trình như vậy nhận được bảo đảm hành vi có thể giải thích theo *sequential consistency*: các thao tác như thể xen kẽ trong một thứ tự tổng thể tôn trọng thứ tự của từng thread.

Đây là bảo đảm về **hành vi quan sát bộ nhớ**, không có nghĩa các thread thực sự chạy tuần tự, không có tranh chấp CPU hay không có deadlock. Một chương trình hoàn toàn dùng lock đúng vẫn có thể có lỗi logic nghiệp vụ, hoặc chờ vô hạn.

### 7.1 HB không phải atomicity

Giả sử hai thread cùng chạy:

~~~java
if (balance > 0) {
    balance--;
    withdrawals++;
}
~~~

Muốn giữ invariant “số dư không âm, số lượt rút thành công phù hợp”, cần bảo vệ cả thao tác kiểm tra và cập nhật như một critical section. Việc đánh dấu `balance` và `withdrawals` là volatile không biến cụm lệnh thành atomic. Có thể dùng cùng một lock cho tất cả đường đọc/ghi hoặc một atomic state transition được thiết kế cho toàn bộ invariant.

HB chứng minh công bố dữ liệu theo quan hệ thứ tự; atomicity bảo đảm không có thread khác chen ngang một cập nhật cần toàn vẹn. Trong thiết kế concurrent, **phải kiểm tra cả hai**.

## 8. Safe publication của object và final fields

*Safe publication* là chuyển một reference sang thread khác qua cơ chế đồng bộ sao cho trạng thái object đã khởi tạo được nhìn thấy đúng.

Ví dụ rủi ro:

~~~java
final class Settings {
    String host;
    int port;
}

// Thread A, giả định shared là field thường:
Settings value = new Settings();
value.host = "localhost";
value.port = 8080;
shared = value;

// Thread B:
Settings snapshot = shared;
if (snapshot != null) {
    // Không đủ căn cứ để coi host/port đã publish an toàn.
}
~~~

Mảnh trên chưa có khai báo `shared`, là **ví dụ lỗi**, không phải chương trình độc lập. Do writer/reader truy cập reference không đồng bộ, việc reader thấy một reference khác null chưa đủ để kết luận mọi trạng thái khởi tạo đã được quan sát theo protocol an toàn.

Các cách publish phổ biến:

- Ghi reference vào trường `volatile` rồi đọc lại qua cùng trường volatile.
- Sử dụng cùng monitor `synchronized` hoặc `Lock`.
- Truyền object qua `BlockingQueue` hay collection concurrent có hợp đồng memory-consistency tương ứng.
- Lấy object là kết quả từ `Future.get()` sau khi task hoàn thành.
- Công bố qua class initialization hợp lệ của JVM.
- Thiết kế immutable object với final fields và không để `this` escape trong constructor.

### 8.1 Cẩn thận với final

~~~java
final class Endpoint {
    final String host;
    final int port;

    Endpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }
}
~~~

JMM có những bảo đảm khởi tạo đặc biệt cho `final` fields khi constructor hoàn tất đúng và reference không thoát ra ngoài trong lúc xây dựng. Các trường này hữu ích cho immutable object. Nhưng:

- `final` cố định **reference của trường**, không tự làm object mà reference đó trỏ tới trở thành immutable; `final List<String>` vẫn có thể trỏ đến danh sách mutable.
- Những lần sửa trạng thái mutable **sau constructor** vẫn cần đồng bộ.
- Không cho `this` escape bằng cách đăng ký listener hoặc start thread sử dụng object đang xây dựng trước khi constructor hoàn tất.

Ví dụ snapshot publish:

~~~java
final class Config {
    final String host;
    final int port;

    Config(String host, int port) {
        this.host = host;
        this.port = port;
    }
}

final class ConfigHolder {
    private volatile Config current = new Config("localhost", 8080);

    Config readSnapshot() {
        return current;
    }

    void replace(Config next) {
        current = next;
    }
}
~~~

Đây là mảnh minh họa. Writer tạo một `Config` hoàn chỉnh rồi thay reference volatile; reader lấy một instance để dùng trong một thao tác. Nếu gọi `readSnapshot()` nhiều lần, các lần gọi có thể nhận các phiên bản khác nhau. Khi cần snapshot nhất quán xuyên suốt xử lý, hãy lưu reference vào biến local một lần.

## 9. java.util.concurrent cung cấp thêm memory-consistency effects

Ngoài các quan hệ JMM nền tảng, nhiều class concurrent có hợp đồng HB cụ thể:

| API | Quan hệ chính | Giới hạn cần hiểu |
| --- | --- | --- |
| `CountDownLatch` | Hành động trước `countDown()` →HB hành động sau `await()` thành công khi count về 0. | Latch không tự thu thập exception của task; countDown phải đúng mốc completion. |
| `ExecutorService` | Hành động trước submit task →HB hành động trong task; hành động trong task →HB hành động sau khi lấy kết quả qua `Future.get()`. | Submit không đồng nghĩa task đã chạy xong. |
| `Future.get()` | Hành động của task →HB hành động sau khi lấy kết quả qua get. | Cần phân biệt completion, failure, cancellation và timeout. |
| `BlockingQueue` | Hành động trước khi put object →HB hành động sau khi thread khác take/access phần tử theo hợp đồng. | Không tự bảo vệ sửa đổi object sau khi put. |
| `Semaphore` | Hành động trước `release()` →HB hành động sau `acquire()` thành công tương ứng. | Giữ invariant nhiều trường vẫn cần protocol riêng. |
| `Lock` | Các thao tác lock/unlock thành công có memory effects như monitor tương ứng. | Phải lock/unlock đúng cùng cơ chế và nhả trong `finally`. |
| Atomic classes | Các phép đọc/ghi/CAS có memory effects được API mô tả. | Các bước `get()` rồi `set()` không tự gộp thành một atomic transaction. |

Cần đọc hợp đồng **của đúng primitive**. Không suy rằng mỗi lần chạm bất kỳ concurrent collection nào đều công bố mọi cập nhật trên toàn bộ object graph. Các hiệu ứng HB thường áp dụng cho dữ liệu **trước** hành động chuyển giao và việc nhận/truy cập phần tử theo API, không bảo vệ những cập nhật phát sinh sau đó.

### 9.1 Ví dụ Java 17+: không cần volatile khi đã có latch

Tạo tệp `LatchHappensBeforeDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;

public final class LatchHappensBeforeDemo {
    private static int result;
    private static final CountDownLatch READY = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        Thread producer = new Thread(() -> {
            result = 42;
            READY.countDown();
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                READY.await();
                System.out.println("Result: " + result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        consumer.start();
        producer.start();
        producer.join();
        consumer.join();
    }
}
~~~

Chạy:

~~~bash
javac LatchHappensBeforeDemo.java
java LatchHappensBeforeDemo
~~~

Stdout khi không có interruption ngoài:

~~~text
Result: 42
~~~

`result` là một `int` thường. Nhưng `result = 42` xảy ra trước `READY.countDown()`, và reader chỉ đọc result sau `READY.await()` thành công: có cạnh HB do latch. Không cần thêm `volatile` cho result chỉ để publish lần ghi đó.

Lưu ý rằng `CountDownLatch.await()` có thể trả về khi số tín hiệu đạt 0 dù thread producer vẫn chưa hoàn toàn `TERMINATED` ở thời điểm đó; muốn đợi chính vòng đời Thread kết thúc cần `join()`. Ở đây hai lời gọi join còn giúp main kết thúc sau các worker.

## 10. Double-checked locking: thiếu volatile có thể publish sai

Mẫu lazy initialization có hai lượt kiểm tra thường được gọi là *double-checked locking* (DCL). Nếu reference chia sẻ là field thường, nhánh đọc ngoài lock có thể không có HB với nhánh ghi:

~~~java
final class LazyService {
    private static Service instance; // Sai: thiếu volatile.

    static Service getInstance() {
        if (instance == null) {
            synchronized (LazyService.class) {
                if (instance == null) {
                    instance = new Service();
                }
            }
        }
        return instance;
    }
}
~~~

Đây là mảnh lỗi, `Service` chưa được định nghĩa. Chỉ phần kiểm tra bên trong lock được đồng bộ qua monitor. Reader ở nhánh nhanh ngoài lock không có quan hệ publish đầy đủ đối với một lần ghi thường vào `instance`. Sửa field thành:

~~~java
private static volatile Service instance;
~~~

Với volatile reference và protocol đúng, lần khởi tạo, ghi reference và lần đọc reference tương ứng được ordered. Tuy nhiên DCL có nhiều chi tiết dễ sai; với singleton lazy đơn giản, holder idiom có thể dễ đọc hơn:

~~~java
final class Service {
    private Service() {}

    private static final class Holder {
        static final Service INSTANCE = new Service();
    }

    static Service getInstance() {
        return Holder.INSTANCE;
    }
}
~~~

Đây là mảnh minh họa class. Class initialization của JVM có cơ chế đồng bộ tương ứng; khi Holder được khởi tạo lần đầu, singleton được publish theo hợp đồng initialization. Vẫn phải tránh leak `this` trước khi constructor hoàn tất.

## 11. Happens-before không tự bảo đảm liveness

Một chương trình có thể dùng synchronization đúng để đảm bảo state nhất quán nhưng vẫn **không tiến triển**:

- Thread A giữ lock L1 rồi đợi L2; thread B giữ L2 rồi đợi L1: deadlock.
- Một task pool nhỏ chờ kết quả task khác trong chính queue của pool: thread-pool starvation.
- Reader chờ một latch không ai countDown: có thể block mãi.
- Một vòng CAS liên tục retry: có thể có progress toàn hệ thống nhưng một thread riêng lẻ bị starvation.

HB và JMM bàn về **các quan sát bộ nhớ hợp lệ**. Liveness/progress bàn về **điều kiện công việc tiếp tục và kết thúc**. Muốn chương trình thực tế đúng, cần thiết kế cả việc công bố dữ liệu lẫn thời hạn chờ, cancellation, interruption và thứ tự lấy lock.

Đừng dùng việc nhiều thread vẫn chạy CPU làm bằng chứng rằng mọi thao tác sẽ kết thúc. Và không nên dùng `Thread.sleep` để “sửa” race: nó chỉ làm lỗi ít xuất hiện hơn hoặc thay đổi interleaving.

## 12. Các lỗi diễn giải HB thường gặp

| Ngộ nhận | Giải thích đúng |
| --- | --- |
| “A chạy trước B theo đồng hồ nên A →HB B.” | HB là quan hệ JMM dựa trên program order/synchronization, không dựa vào khoảng thời gian. |
| “Có `volatile` là mọi thao tác đều atomic.” | Volatile không biến `count++` thành atomic. |
| “`Thread.join()` sửa mất cập nhật giữa worker.” | Join công bố kết quả khi worker đã kết thúc; race trong lúc worker chạy vẫn có thể xảy ra. |
| “Hai khối `synchronized` bất kỳ tạo HB.” | Cần dùng **cùng monitor** trong protocol tương ứng. |
| “Final reference làm toàn bộ object graph immutable.” | Chỉ reference final; object mutable được trỏ tới cần kiểm soát riêng. |
| “Đã có `ready = true` thì mọi phép ghi sau đó cũng được publish.” | HB phát tín hiệu áp dụng cho các hành động đã đứng trước write đồng bộ; các ghi sau cần protocol riêng. |
| “`sleep()`, `yield()`, `onSpinWait()` giúp visibility.” | Chúng không tạo cạnh HB cho dữ liệu chia sẻ. |
| “Log cho ra kết quả đúng chứng minh không có data race.” | Logging và scheduler có thể che giấu race; test hữu hạn không thay chứng minh. |
| “Sequential consistency có nghĩa runtime chạy tuần tự.” | Đây là mô hình kết quả bộ nhớ, không phải yêu cầu scheduler chạy từng thread một. |
| “Dùng AtomicInteger cho một trường bảo vệ mọi invariant.” | Cần phân tích toàn bộ chuyển trạng thái và các field phụ thuộc. |

## 13. Quy trình chứng minh correctness bằng HB graph

Đối với một đoạn code concurrent, thực hiện tuần tự:

1. **Ghi rõ invariant.** Ví dụ “số dư không âm”, “config được đọc theo snapshot nhất quán”, “task chỉ trả kết quả sau khi đã khởi tạo xong”.
2. **Liệt kê tất cả access xung đột.** Thread nào đọc/ghi cùng trường? Có access ngoài lock hay ngoài protocol không?
3. **Đánh dấu program order mỗi thread.** Lần ghi payload nằm trước hay sau tín hiệu? Phép đọc nằm trước hay sau khi nhận tín hiệu?
4. **Tìm cạnh đồng bộ thực sự.** Cùng monitor lock, volatile write/read tương ứng, start/join, latch, future, queue transfer?
5. **Nối cạnh bằng tính bắc cầu.** Có đường A →HB B từ phép ghi cần publish đến phép đọc cần bảo đảm không?
6. **Tính đến writer khác.** Có phép ghi cạnh tranh làm mất kết luận một giá trị cụ thể hoặc tạo data race không?
7. **Kiểm tra atomicity.** Một thao tác gồm nhiều bước có thể bị chen giữa, phá invariant không?
8. **Kiểm tra liveness.** Lock có thể bị giữ mãi, tín hiệu có thể không đến, timeout/interrupt có được xử lý không?
9. **Viết test theo invariant.** Dùng barrier/latch để tái hiện interleaving, test stress có giới hạn; không dùng chỉ `sleep()` làm điều kiện.
10. **Code review lại sau khi sửa.** Thêm một access bỏ qua lock hoặc một writer sau publish có thể phá toàn bộ lập luận cũ.

Ví dụ một giao thức được publish đúng:

~~~text
writer                             reader
------                             ------
payload = 42
   |
   v
volatile ready = true ---------> if (ready)  // volatile read
                                    |
                                    v
                               use(payload)
~~~

Nếu chuyển `use(payload)` lên **trước** `if (ready)` hoặc chuyển `payload = 42` xuống **sau** ghi ready, đường HB bảo đảm dữ liệu không còn giống sơ đồ.

## 14. Test concurrency và giới hạn của việc chạy thử

Không thể dùng “chạy 10.000 lần thấy đúng” để chứng minh một giao thức có HB. Data race phụ thuộc lịch chạy, optimizer, mức tải, runtime và CPU.

- Dùng `CountDownLatch` hoặc `CyclicBarrier` để bắt đầu tác vụ đồng thời và tạo các điều kiện interleaving cụ thể; đừng dùng sleep để phỏng đoán.
- Assert **invariant chính xác**, ví dụ hai thread mỗi thread tăng 1.000 lần phải cho kết quả 2.000, thay vì assert kết quả lớn hơn 0.
- Stress test nhiều lần với nhiều thread để tìm lỗi nhưng phải chấp nhận rằng không quan sát thấy lỗi chưa chứng minh chương trình an toàn.
- Dùng công cụ nghiên cứu concurrency như **OpenJDK jcstress** khi cần khảo sát những kết quả quan sát được của các đoạn mã nhỏ; đọc đúng các outcome được JMM cho phép.
- Review HB graph cùng với protocol atomicity: đây là phần kiểm chứng mà test hữu hạn không thể thay thế.
- Log/println trong đoạn bị race có thể vô tình thêm đồng bộ hoặc làm thay đổi scheduling, che dấu lỗi; tránh dựa vào hiện tượng đó.

Thread dump hỗ trợ xem thread `BLOCKED` hoặc `WAITING` khi chương trình kẹt nhưng không tự chứng minh một field đã publish an toàn. Giữ mục tiêu quan sát đúng: HB cho memory consistency, thread dump cho điều tra runtime/liveness.

## 15. Tự kiểm tra kiến thức

**1.** Writer ghi `data=42` rồi `volatile ready=true`; reader thấy `ready==true` rồi mới đọc data. Vì sao có thể kết luận đọc 42 nếu không có writer khác?  
**Đáp:** Program order của writer, cạnh volatile write→read và program order của reader tạo đường HB từ ghi data đến đọc data.

**2.** Nếu bỏ volatile ở ready nhưng giữ nguyên thứ tự code thì có bảo đảm tương tự không?  
**Đáp:** Không. Thiếu synchronization; thread đọc và ghi có thể bị data race.

**3.** `Thread.sleep(100)` sau khi start worker có thay `join()` được không?  
**Đáp:** Không. Sleep không có cạnh HB với việc worker kết thúc; join nhận biết completion.

**4.** Hai thread cùng chạy `volatile int counter; counter++`, có chắc đủ 2.000 lượt tăng không?  
**Đáp:** Không. Phép tăng ghép không atomic; cần lock hoặc atomic increment.

**5.** Tại sao hai `synchronized` dùng hai object khác nhau chưa bảo vệ được một biến chia sẻ?  
**Đáp:** Mutual exclusion và cạnh unlock→lock chỉ áp dụng cho cùng monitor phù hợp.

**6.** Sau `worker.join()`, main thấy output đã ghi trong worker, nhưng hai worker trước đó cùng cập nhật sai. Join có sửa lỗi không?  
**Đáp:** Không. Join tạo visibility sau termination, không ngăn race và lost update đã xảy ra.

**7.** Một object có `final List<String> items`. Có thể cập nhật list từ nhiều thread mà không có synchronization không?  
**Đáp:** Không. Final chỉ cố định reference; object được trỏ tới còn mutable.

**8.** `CountDownLatch.await()` trả về khi count bằng 0. Reader có nhìn thấy trạng thái producer ghi trước `countDown()` không?  
**Đáp:** Có theo hợp đồng memory-consistency của latch khi await trả về thành công.

**9.** Chương trình không có data race có tự động không deadlock không?  
**Đáp:** Không. Safety/visibility và liveness là hai chiều correctness khác nhau.

**10.** Cần những gì để chứng minh một invariant concurrent?  
**Đáp:** Xác định access, lập HB graph, tìm writer cạnh tranh, kiểm tra atomicity của thao tác ghép và xét liveness. Test chỉ bổ trợ lập luận.

**Ghi nhớ:** *Happens-before* giúp chứng minh việc **công bố và quan sát dữ liệu đúng thứ tự** giữa các thread. Để chương trình đúng toàn diện, còn phải bảo vệ **atomicity và invariant** cũng như giải quyết **liveness**. Hãy dựa vào hợp đồng rõ ràng của `volatile`, `synchronized`, `start()/join()` và `java.util.concurrent` thay vì độ trễ, log hay tình cờ scheduler.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-003">Program order và các quan hệ happens-before</a>

<details>
<summary>Xem chi tiết</summary>

# Program order và các quan hệ happens-before trong Java Memory Model

Khi hai thread cùng đọc và ghi dữ liệu, thứ tự nhìn thấy ở mã nguồn không đủ để khẳng định thread này sẽ thấy thay đổi của thread kia. Java Memory Model (JMM) xác định **những giá trị mà thao tác đọc được phép nhìn thấy** và các **bảo đảm về thứ tự, tính hiển thị** giữa những hành động đồng thời. Hai khái niệm cốt lõi là **program order** (thứ tự chương trình của một thread) và **happens-before** (quan hệ bảo đảm thứ tự ở mức bộ nhớ).

Bài học áp dụng cho Java concurrency nói chung (Java 17+), không phụ thuộc vào Spring.

Sau bài này, bạn sẽ biết:
- Hiểu “thứ tự trong cùng thread” khác “thứ tự được bảo đảm giữa các thread” như thế nào.
- Xây dựng một chuỗi happens-before bằng **program order**, **synchronizes-with** và tính **bắc cầu**.
- Nhận diện các cạnh happens-before đến từ `Thread.start()`, `join()`, monitor `synchronized`, `volatile` và API đồng thời trong JDK.
- Phân biệt **visibility**, **ordering**, **atomicity** và vấn đề **data race**.
- Kiểm tra một mẫu chia sẻ dữ liệu xem có thể lập luận được về kết quả hay không, thay vì dựa vào `sleep()` hoặc kết quả chạy thử.

## 1. JMM cần quy tắc thứ tự để làm gì?

CPU có cache, compiler/JIT tối ưu, các lõi xử lý thực thi song song và thread được lập lịch độc lập. JVM được phép thực hiện nhiều tối ưu miễn là vẫn tuân thủ JMM. Kết quả là:

- Trong **một thread**, hành vi phải tương thích với ngữ nghĩa chạy tuần tự của chính thread đó (*intra-thread semantics*).
- Giữa **hai thread**, việc thread A thực hiện lệnh ghi trước trên đồng hồ thực tế **không đủ** để kết luận thread B sẽ đọc được giá trị ghi mới hoặc nhìn thấy mọi thao tác trước đó của A.
- Cần một quan hệ đồng bộ được đặc tả để xác định ràng buộc bộ nhớ có thể dựa vào khi chứng minh tính đúng.

Ví dụ ý tưởng:

~~~java
// Biến chia sẻ giữa 2 thread:
int payload = 0;
boolean ready = false;

// Thread A:
payload = 42;
ready = true;

// Thread B:
if (ready) {
    System.out.println(payload);
}
~~~

Đây là **các đoạn minh họa, không phải một chương trình Java độc lập**. Khi `payload` và `ready` là biến thường, không có đồng bộ, không có bảo đảm rằng B sẽ luôn thấy `42` sau khi thấy `ready == true`. B cũng có thể không quan sát được trạng thái `ready` mà A đã ghi. Đoạn mã bị **data race** trên dữ liệu chia sẻ; thứ tự nguồn trong A chưa đủ tạo quan hệ happens-before từ A sang B.

Câu hỏi cần trả lời không phải chỉ là “dòng nào chạy trước?”, mà là: **Hành động ghi của A có happens-before hành động đọc tương ứng của B không?**

## 2. Program order: thứ tự hành động bên trong một thread

### 2.1. Định nghĩa

**Program order** là thứ tự giữa các hành động của **cùng một thread** như thể chúng thực thi theo ngữ nghĩa của chương trình trong thread đó. Theo quy tắc JMM, nếu hành động `x` đứng trước hành động `y` theo program order trong một thread, thì:

**`x happens-before y`.**

Ví dụ:

~~~java
int a = 10;       // A1
int b = a + 5;    // A2
System.out.println(b); // A3
~~~

Trong cùng thread, `A1 →po A2 →po A3`; vì vậy `A1 →hb A2 →hb A3`. Kết quả trong ngữ cảnh ví dụ là `15`, dù JVM có thể áp dụng tối ưu nội bộ. JMM yêu cầu hành vi được giữ đúng theo ngữ nghĩa trong thread; nó **không hứa CPU sẽ phát từng thao tác máy theo đúng thứ tự dòng mã nguồn**.

### 2.2. Program order không tự nối hai thread

Hai chuỗi:

~~~text
Thread A: A1 ->po A2 ->po A3
Thread B: B1 ->po B2 ->po B3
~~~

Cho ta các cạnh happens-before **trong từng thread**, nhưng không mặc nhiên có `A3 →hb B1` hay `B2 →hb A1`. Muốn nối hai chuỗi cần một quy tắc đồng bộ xuyên thread, thường gọi là **synchronizes-with**.

Các điểm thường nhầm:
- `A1` viết trước `A2` trong mã nguồn không có nghĩa mọi thread khác nhất thiết quan sát thấy hai giá trị theo cách mong muốn.
- “Thread A đã chạy xong dòng ghi” trên đồng hồ ngoài chương trình không phải một quy tắc JMM có thể dùng thay đồng bộ.
- Mỗi thread có program order riêng; program order không cung cấp một *total order* chung cho toàn bộ hành động thường trên mọi thread.

## 3. Happens-before: quan hệ bảo đảm về bộ nhớ

### 3.1. Định nghĩa và ba cách suy luận chính

Viết `A →hb B` khi hành động A **happens-before** hành động B.

Trong JMM, happens-before hình thành từ program order, các cạnh synchronizes-with và tính bắc cầu của chúng. Để lập luận thực hành, thường áp dụng ba quy tắc:

1. **Program order:** nếu A đứng trước B trong cùng thread theo program order, `A →hb B`.
2. **Synchronizes-with:** một số thao tác đồng bộ xác định tạo cạnh từ thread này sang thread khác, ví dụ monitor unlock đến một lần lock tiếp sau trên cùng monitor, hay ghi volatile đến đọc volatile tiếp sau theo synchronization order.
3. **Bắc cầu (transitivity):** nếu `A →hb B` và `B →hb C`, suy ra `A →hb C`.

Ví dụ:

~~~text
Thread A:
  data = 42       (A1)
  ready = true    (A2: volatile write)
                       |
                       | synchronizes-with
                       v
Thread B:
  read ready      (B1: volatile read thấy true)
  read data       (B2)

A1 ->po A2 ->sw B1 ->po B2
Suy ra A1 ->hb B2
~~~

Dấu `→po` là program order; `→sw` là synchronizes-with; `→hb` là happens-before. Nếu không có các ghi cạnh tranh khác vào `data`, B có thể dựa vào bảo đảm thấy `42` sau khi đọc `ready == true`.

Happens-before **không chỉ nói về “đã diễn ra trước trên đồng hồ”**; đây là quan hệ do JMM xác định để ràng buộc những kết quả quan sát được. Bất kỳ lời giải thích nào dựa riêng vào thời gian thực hoặc số mili giây đã chờ đều thiếu cơ sở đồng bộ.

### 3.2. Happens-before không đồng nghĩa “mỗi bước là nguyên tử”

Ví dụ `counter++` bao gồm đọc, tính toán và ghi lại. Hai thread đều dùng `volatile int counter` có visibility và quy tắc thứ tự của volatile, nhưng `counter++` **vẫn không phải** một phép tăng nguyên tử. Hai thread có thể đọc cùng giá trị rồi cùng ghi giá trị mới, làm mất một lần cập nhật.

Đối với phép cập nhật nguyên tử, hãy dùng:
- `AtomicInteger.incrementAndGet()`/`getAndIncrement()` cho bộ đếm;
- `synchronized` hoặc `Lock` khi cần bảo vệ toàn bộ bất biến gồm nhiều bước;
- Cấu trúc dữ liệu đồng thời và các phương thức nguyên tử được API cam kết khi thích hợp.

Ba thuộc tính liên quan nhưng khác nhau:

| Thuộc tính | Câu hỏi |
| --- | --- |
| **Visibility** | Khi thread B đọc, JMM có bảo đảm B thấy ghi của A không? |
| **Ordering** | Những thao tác nào được yêu cầu có trật tự có thể dựa vào giữa các thread? |
| **Atomicity** | Một nhóm bước có được quan sát/xử lý như một thao tác không thể bị xen kẽ không? |

Một chương trình cần xem xét cả ba khi bất biến đòi hỏi.

## 4. Synchronizes-with: những cạnh xuyên thread quan trọng

### 4.1. Monitor: synchronized unlock → subsequent lock

Một lần **unlock** trên monitor `m` synchronizes-with **mọi lần lock sau đó** trên cùng monitor `m` (theo synchronization order). Điều này cho phép thao tác ghi trong khối khóa trước được nhìn thấy khi thread khác vào khối khóa sau, nếu truy cập đúng cùng monitor.

~~~java
// Đoạn minh họa trong một class có 2 thread cùng tham chiếu object lock:
final Object lock = new Object();
int value = 0;

// Thread A:
synchronized (lock) {
    value = 42;
} // unlock(lock)

// Thread B, đi vào khối này sau khi A đã unlock:
synchronized (lock) { // lock(lock)
    System.out.println(value);
}
~~~

Đoạn mã không độc lập; ý nghĩa là cả hai thread **dùng cùng object `lock`**. Nếu B thực sự lấy khóa sau khi A nhả khóa và không có người ghi khác, in `42`. Nếu B vào trước A, kết quả có thể là `0`. `synchronized` tạo ràng buộc chính xác khi biết thứ tự lấy khóa; nó **không tự buộc thread A phải lấy khóa trước thread B**.

Đổi sang hai object lock khác nhau sẽ mất cạnh đồng bộ mong muốn, dù tên biến hoặc nội dung hai khối giống nhau.

Với `java.util.concurrent.locks.Lock` như `ReentrantLock`, việc lock/unlock được đặc tả có hiệu ứng bộ nhớ tương đương monitor để xây dựng lập luận tương ứng. Vẫn phải `unlock()` trong `finally` sau khi lấy khóa thành công.

### 4.2. Volatile write → subsequent volatile read

Một lần ghi vào biến `volatile` synchronizes-with các lần đọc biến đó **tiếp sau theo synchronization order**. Đối với ví dụ kiểu *publication flag*, thread đọc thấy `true` có thể dùng thứ tự này để truy cập dữ liệu mà writer đã chuẩn bị trước khi công bố flag.

Ví dụ độc lập (Java 17+):

~~~java
// VolatilePublicationDemo.java
public class VolatilePublicationDemo {
    private static int payload;
    private static volatile boolean ready;

    public static void main(String[] args) throws InterruptedException {
        Thread reader = new Thread(() -> {
            while (!ready) {
                Thread.onSpinWait();
            }
            System.out.println("Payload: " + payload);
        });

        reader.start();
        payload = 42;
        ready = true;
        reader.join();
    }
}
~~~

~~~bash
javac VolatilePublicationDemo.java
java VolatilePublicationDemo
~~~

Khi hai thread được lập lịch và chương trình hoàn thành bình thường, stdout:

~~~text
Payload: 42
~~~

Lập luận:
1. Trong main: `payload = 42 →po ready = true`.
2. Trong reader: lần đọc volatile quan sát `true` diễn ra sau lần ghi `ready = true` trong synchronization order; lần ghi này `→sw` lần đọc đó.
3. Trong reader: lần đọc thấy `true` `→po` lần đọc `payload`.
4. Theo bắc cầu, việc main ghi `payload = 42 →hb` việc reader đọc `payload`. Chỉ có một writer vào `payload` trong chương trình này.

`Thread.onSpinWait()` là gợi ý cho JVM/CPU trong vòng spin; **nó không phải thao tác đồng bộ**. Dùng spin chỉ để minh họa quan hệ volatile: trong ứng dụng thực tế, ưu tiên công cụ chờ phù hợp như `CountDownLatch`, `BlockingQueue` hoặc `Future` để tránh tốn CPU và có chính sách timeout/cancel.

Chú ý: tính liveness không suy ra từ riêng quan hệ happens-before. Nếu hệ điều hành không cấp thời gian chạy cho writer, reader có thể chưa thoát vòng chờ; JMM không cam kết lịch chạy công bằng.

### 4.3. Thread.start(): start → action đầu tiên của thread mới

Một lời gọi `thread.start()` synchronizes-with bất kỳ hành động nào trong thread vừa được start. Do đó dữ liệu được chuẩn bị **trước `start()`** có thể được thread mới nhìn thấy theo happens-before.

~~~java
// StartVisibilityDemo.java
public class StartVisibilityDemo {
    public static void main(String[] args) throws InterruptedException {
        int[] values = {0};
        values[0] = 42;

        Thread worker = new Thread(() ->
            System.out.println("Observed: " + values[0]));

        worker.start();
        worker.join();
    }
}
~~~

~~~bash
javac StartVisibilityDemo.java
java StartVisibilityDemo
~~~

Stdout:

~~~text
Observed: 42
~~~

`values[0] = 42 →po worker.start() →sw` các hành động của worker. Đây là cách chuyển thông tin khởi tạo từ thread tạo sang thread con. Nếu main **sửa tiếp `values[0]` sau `start()`** trong khi worker đọc mà không có đồng bộ bổ sung, thì cạnh `start()` không bảo vệ việc sửa sau đó.

### 4.4. Thread termination: hành động của worker → join thành công

Mọi hành động trong thread hoàn tất happens-before thread khác **phát hiện thread đó đã kết thúc**, chẳng hạn khi `join()` trả về bình thường hoặc `isAlive()` trả `false` do phát hiện kết thúc.

~~~java
// JoinVisibilityDemo.java
public class JoinVisibilityDemo {
    private static int result;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> result = 42);
        worker.start();
        worker.join();
        System.out.println("Result: " + result);
    }
}
~~~

~~~bash
javac JoinVisibilityDemo.java
java JoinVisibilityDemo
~~~

Stdout:

~~~text
Result: 42
~~~

Lập luận: `result = 42` xảy ra trong worker; worker kết thúc, main trở về từ `join()`; main đọc `result` sau `join()`. Cạnh kết thúc thread cùng program order và bắc cầu đủ để main thấy cập nhật. Không cần biến `result` là volatile **trong tình huống chỉ một worker ghi và main chỉ đọc sau khi join thành công**. Nếu main đọc trước khi `join()`, không được tự suy ra bảo đảm đó.

`join()` có thể ném `InterruptedException` khi caller đang chờ bị interrupt; code phải xử lý trường hợp chờ chưa hoàn tất theo chính sách ứng dụng.

### 4.5. Interrupt và việc phát hiện interrupt

Một lời gọi `interrupt()` lên thread T synchronizes-with hành động **T phát hiện interrupt**, ví dụ kiểm tra `isInterrupted()`/`Thread.interrupted()` hoặc thao tác chặn ném `InterruptedException` do interrupt.

Cạnh này giúp truyền tín hiệu điều khiển, nhưng **interrupt không dừng cưỡng bức thread**, không buộc một tác vụ CPU-bound tự kiểm tra, và không thay thế giao thức chuyển dữ liệu giữa các thread. Đừng dùng interrupt như thông điệp “kết quả đã sẵn sàng” khi mục đích thật là công bố một giá trị.

### 4.6. Default initialization và final field

JMM còn có quy tắc đồng bộ cho **default initialization** của object trước các thao tác thông thường khác lên object đó. Với `final`, JMM định nghĩa các bảo đảm riêng khi object được xây dựng đúng cách, đặc biệt **không để `this` escape trong constructor**. Nhờ vậy các trường final có khả năng hiển thị khởi tạo đặc biệt, nhưng không nên suy diễn rằng mọi field mutable khác trong object tự nhiên an toàn khi chia sẻ.

Với các object có nhiều trạng thái mutable, hãy công bố chúng bằng các cơ chế happens-before rõ ràng như khóa, volatile reference hoặc handoff của concurrency API.

## 5. Transitivity: ghép các cạnh thành một bằng chứng

Giả sử:

~~~text
Thread A:       write(data=42) ─po─> volatile write(ready=true)
                                        |
                                        | sw
                                        v
Thread B:                              volatile read(ready)
                                        |
                                        | po
                                        v
                                      read(data)
~~~

Suy ra:

~~~text
write(data=42) →hb read(data)
~~~

Phải kiểm tra **đúng cặp thao tác** và **đúng đối tượng đồng bộ**. Một câu như “có dùng volatile ở đâu đó” không cung cấp bằng chứng rằng một ghi vào `data` được bảo đảm hiển thị ở nơi đọc.

### 5.1. Nhiều chặng chuyển giao

Quan hệ happens-before có thể đi qua nhiều thread. Ví dụ nếu:

~~~text
A: write X →po release(lock1)
                →sw B: acquire(lock1) →po release(lock2)
                                            →sw C: acquire(lock2) →po read X
~~~

thì từ bắc cầu ta có `A:write X →hb C:read X`. Các edge giữa A–B và B–C không cần dùng cùng monitor, miễn là từng cặp dùng đúng monitor của nó, có thứ tự unlock–lock phù hợp, và code thật sự nối được hai bước bằng program order.

### 5.2. Happens-before có hướng; không thể đảo chiều

Nếu `A →hb B`, không thể kết luận `B →hb A`. Nếu hai hành động nằm trong hai thread mà không có quan hệ happens-before theo cả hai hướng, chúng **không được sắp thứ tự bởi happens-before**. Đối với truy cập xung đột vào dữ liệu chia sẻ, cần xem xét data race và kết quả được JMM cho phép.

Không nên tự thêm edge chỉ vì:
- Cùng gọi một phương thức bình thường trên hai thread.
- Cùng nhìn vào một object nhưng mỗi thread khóa một monitor khác nhau.
- Đã gọi `Thread.sleep(100)` trước khi đọc.
- Khi debug, log thường hiển thị tác vụ ghi “có vẻ hoàn thành trước”.

## 6. Các API java.util.concurrent tạo bảo đảm bộ nhớ

Các công cụ chuẩn của JDK có **memory consistency effects** được công bố trong tài liệu API. Những bảo đảm này thường ngắn gọn và dễ dùng hơn việc tự dựng giao thức với nhiều biến thường.

| Cơ chế | Cạnh quan trọng để suy luận |
| --- | --- |
| `ExecutorService` | Hành động trước khi submit `Runnable`/`Callable` →hb hành động trong task; hành động task →hb việc caller lấy kết quả bằng `Future.get()` |
| `Future.get()` | Hành động trong phép tính bất đồng bộ →hb hành động tiếp theo sau khi `get()` trả kết quả thành công cho thread đợi |
| `CountDownLatch` | Hành động trước `countDown()` →hb hành động sau khi `await()` tương ứng trở về thành công |
| `BlockingQueue` | Hành động trước khi đặt phần tử vào queue →hb hành động sau khi thread khác lấy/nhận phần tử |
| `ConcurrentMap` và collection đồng thời | Dùng đúng hợp đồng *memory consistency* của API cho giao tiếp thông qua phần tử/entry và các thao tác được đặc tả |
| `Lock` (ví dụ `ReentrantLock`) | Thành công lock/unlock có hiệu ứng bộ nhớ của monitor tương ứng |

**Ví dụ dùng CountDownLatch**, stdout xác định mà không cần spin:

~~~java
// LatchHappensBeforeDemo.java
import java.util.concurrent.CountDownLatch;

public class LatchHappensBeforeDemo {
    private static int payload;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try {
                ready.await();
                System.out.println("Payload: " + payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        reader.start();
        payload = 42;
        ready.countDown();
        reader.join();
    }
}
~~~

~~~bash
javac LatchHappensBeforeDemo.java
java LatchHappensBeforeDemo
~~~

Stdout:

~~~text
Payload: 42
~~~

Trong main: `payload = 42 →po ready.countDown()`. Theo hợp đồng `CountDownLatch`, những hành động trước `countDown()` happens-before những hành động sau khi `await()` trở về thành công ở reader. Kết hợp program order, reader thấy `42`. Việc `reader.join()` trong main chỉ nhằm chờ đọc/xuất hoàn tất trước khi kết thúc chương trình; **bảo đảm visibility từ main sang reader ở đây đến từ latch**.

**Ví dụ với ExecutorService + Future**, phân biệt hai chiều chuyển giao:

~~~java
// FutureHappensBeforeDemo.java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureHappensBeforeDemo {
    private static int base;
    private static int outcome;

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            base = 40;
            Future<Integer> task = executor.submit(() -> {
                outcome = base + 2;
                return outcome;
            });

            int received = task.get();
            System.out.println("Future: " + received);
            System.out.println("Shared: " + outcome);
        } finally {
            executor.shutdown();
        }
    }
}
~~~

~~~bash
javac FutureHappensBeforeDemo.java
java FutureHappensBeforeDemo
~~~

Stdout:

~~~text
Future: 42
Shared: 42
~~~

Giải thích hai chiều:

1. Main ghi `base = 40` **trước khi `submit`**; hành động này happens-before hành động của task, nên task thấy `base = 40`.
2. Task ghi `outcome = 42` và trả kết quả; các hành động trong task happens-before hành động của main **sau `task.get()` thành công**. Main có thể đọc `outcome` theo bảo đảm đó.
3. `ExecutorService.shutdown()` dừng nhận task mới; **không tự chờ** các task chạy xong. Trong ví dụ, main đã `get()` trước khi shutdown.

Bảo đảm này không có nghĩa mọi shared variable trong task tự động thread-safe nếu các thread khác vẫn sửa cùng biến mà không đồng bộ.

## 7. Data race, correctly synchronized và giới hạn lập luận

### 7.1. Data race là gì?

Ở mức căn bản, khi hai thread truy cập **cùng biến**, có ít nhất một lần **ghi**, và hai truy cập xung đột đó **không được sắp thứ tự bởi happens-before**, ta có tình huống data race theo định nghĩa của JMM. Vấn đề thường gặp nhất là các biến thường chia sẻ giữa thread nhưng không có bất kỳ giao thức đồng bộ nhất quán nào.

~~~java
// Đoạn minh họa, KHÔNG phải một chương trình hoàn chỉnh:
int counter = 0;

// T1 và T2 cùng thực hiện, không khóa:
counter++;
~~~

Phép tăng là chuỗi đọc, tính, ghi; có thể mất cập nhật. Data race và lost update có liên quan nhưng không nên xem mọi race condition là chỉ do visibility. Một phép tăng cần **atomicity**, do đó việc đổi `counter` sang `volatile` **không giải quyết** lost update.

Khi chương trình được **đồng bộ đúng** (các truy cập chia sẻ xung đột được điều phối phù hợp), JMM bảo đảm những hành vi nhất quán với mô hình tuần tự cho các chương trình được đồng bộ đúng (thường gọi là thuộc tính *sequential consistency for data-race-free programs*). Điều kiện tiên quyết là các thành phần đều tuân thủ giao thức đồng bộ.

### 7.2. Không có happens-before thì được phép đọc gì?

Không có cạnh happens-before giữa ghi và đọc **không đồng nghĩa** đọc chắc chắn thấy giá trị cũ; có lần chạy thấy mới và có lần không. JMM ràng buộc những kết quả được phép của một execution, chứ không tuyên bố “không đồng bộ thì luôn sai”.

Cũng không thể tự do giả định một thứ tự toàn cục cho mọi phép đọc/ghi thường. Một quy tắc kiểm tra thực tế là: xác định các ghi có thể tác động đến một lần đọc, kiểm tra những quan hệ happens-before bắt buộc, rồi xem API đồng bộ có đưa tới bảo đảm mong muốn không.

Lưu ý JMM có thêm các quy tắc về tính hợp lệ của phép đọc và thực thi; không nên biến ví dụ thành tuyên bố “đọc được bất kỳ giá trị nào tùy ý”. Trong thiết kế ứng dụng, mục tiêu thường là tạo **cạnh happens-before rõ ràng** để tránh phụ thuộc vào những khả năng quan sát khó đoán.

### 7.3. Happens-before chưa đủ cho thao tác ghép nhiều bước

~~~java
// Mã minh họa, không biên dịch độc lập:
volatile int counter = 0;

// Hai thread cùng gọi:
counter = counter + 1;
~~~

Mỗi lần đọc/ghi volatile có thứ tự/bảo đảm riêng, nhưng thao tác ghép vẫn có thể bị xen kẽ. Nếu bất biến là “mỗi yêu cầu phải tăng bộ đếm một lần”, sử dụng `AtomicInteger.incrementAndGet()` hoặc khóa bao toàn bộ cập nhật.

Ngược lại, khi thông tin chỉ **được công bố một lần** rồi **đọc sau ranh giới đồng bộ**, như các ví dụ `start()`, `join()` và latch, có thể không cần biến payload là volatile. Bắt buộc phân tích ai viết, khi nào viết, có ghi cạnh tranh hay không và điểm đồng bộ nào được sử dụng.

## 8. Cách kiểm tra một đoạn code theo đồ thị happens-before

Đọc code đồng thời bằng một sơ đồ hành động:

1. **Liệt kê biến chia sẻ:** ví dụ `payload`, `ready`, `counter`; chỉ ra thread nào đọc/ghi từng biến.
2. **Đánh dấu hành động:** `W(payload=42)`, `Wvolatile(ready=true)`, `Rvolatile(ready)`, `R(payload)`.
3. **Vẽ program order bên trong từng thread** (các cạnh `→po`).
4. **Tìm các cạnh synchronizes-with thực sự**: cùng monitor, cùng volatile field, `start`, phát hiện kết thúc bằng `join`, giao tiếp qua concurrency API.
5. **Lấy bắc cầu** để biết truy cập ghi mong muốn có happens-before lần đọc hay không.
6. **Kiểm tra writer cạnh tranh và thao tác phức hợp**: dù visibility được bảo đảm, phép cập nhật có đủ atomicity? Nếu nhiều writer, thứ tự tương quan đã được kiểm soát chưa?
7. **Đối chiếu với yêu cầu nghiệp vụ**: ràng buộc kết quả, xử lý timeout/interrupt, đóng executor, và khả năng tiến triển.

Ví dụ so sánh:

| Tình huống | Có đủ HB để công bố `payload`? | Lưu ý |
| --- | --- | --- |
| A ghi `payload` rồi `start()` worker B; B đọc | Có | Chỉ bảo đảm những thao tác trước `start()` |
| Worker A ghi `payload`; B là thread đã `join()` A thành công, rồi đọc | Có | B phải đợi thành công rồi mới đọc |
| A ghi `payload` trước volatile write flag; B đọc flag thấy true rồi đọc payload | Có | Không có ghi cạnh tranh khác vào payload |
| A ghi `payload` rồi `countDown()`; B `await()` thành công rồi đọc | Có | Dựa trên memory consistency của latch |
| A ghi `payload` rồi `sleep(100)`; B đọc | Không có HB chỉ từ sleep | Delay không tạo giao thức đồng bộ |
| A khóa `lockA`; B khóa object `lockB` khác | Không có HB từ cặp khóa đó | Tên biến giống nhau không làm hai monitor giống nhau |
| Hai thread cùng `volatileCounter++` | Có quy tắc volatile, nhưng **không đủ atomicity** | Dùng atomic increment/khóa |

### Những sai lầm thường gặp

- **“Mã trong cùng thread chạy theo thứ tự, vậy thread khác chắc chắn thấy đúng.”** Program order là cạnh nội bộ; cần nối bằng synchronizes-with.
- **“Volatile làm mọi biểu thức trên biến trở thành nguyên tử.”** Sai: volatile không gộp chuỗi đọc–sửa–ghi.
- **“`join()` làm cho mọi đọc trước `join()` thấy dữ liệu mới.”** Sai: chỉ có thể dùng bảo đảm cho hành động của caller **sau khi join thành công**.
- **“Bất kỳ hai khối synchronized nào cũng đồng bộ với nhau.”** Sai: phải cùng monitor và đúng thứ tự unlock/lock.
- **“`Thread.sleep` giúp JMM flush cache.”** Không có quy tắc happens-before như vậy; sleep chỉ ảnh hưởng lập lịch.
- **“Đọc `isDone() == true` là đã lấy đủ mọi dữ liệu tùy ý.”** Dựa vào hợp đồng memory consistency cụ thể của API, chẳng hạn `Future.get()`, thay vì phỏng đoán một phương thức kiểm tra trạng thái tương đương mọi điểm đồng bộ.
- **“Nếu test 10.000 lần đúng thì không có data race.”** Stress test có ích để phát hiện lỗi, nhưng không chứng minh đầy đủ các cạnh JMM cần thiết.
- **“Happens-before là đúng thứ tự thời gian CPU thực thi.”** Quan hệ này là một ràng buộc ngữ nghĩa; cách compiler/JIT/CPU hiện thực có thể khác, miễn tuân thủ JMM.

## 9. Tự kiểm tra kiến thức

**Câu 1.** Thread A ghi `int data = 99` rồi gọi `t.start()`, B là thread `t` và chỉ đọc `data` sau khi bắt đầu. Nếu không có ghi cạnh tranh khác, có cần `volatile` chỉ để thấy giá trị 99 không?

**Đáp án:** Không. Cạnh program order trước `start()` và cạnh `start()` đến hành động trong B cho phép suy ra happens-before.

**Câu 2.** Worker ghi `result`, caller gọi `worker.join()` trả về thành công rồi mới đọc. Bảo đảm gì?

**Đáp án:** Hành động của worker happens-before các hành động tiếp sau việc caller phát hiện worker đã kết thúc; caller thấy giá trị của worker nếu không có writer cạnh tranh khác làm thay đổi nó.

**Câu 3.** A dùng `synchronized(lock1)`, B dùng `synchronized(lock2)`, trong đó `lock1 != lock2`. Có thể suy luận unlock–lock từ hai khối này không?

**Đáp án:** Không; phải khóa **cùng monitor**.

**Câu 4.** Một `volatile boolean ready` được ghi sau khi writer chuẩn bị payload. Reader thấy `ready == true` rồi đọc payload. Kể tên chuỗi cạnh.

**Đáp án:** `write(payload) →po writeVolatile(ready) →sw readVolatile(ready) →po read(payload)`; suy ra `write(payload) →hb read(payload)`.

**Câu 5.** Hai thread cùng tăng một `volatile int` bằng `++`. Có thể thiếu lần tăng không?

**Đáp án:** Có. Volatile không làm phép tăng nguyên tử; dùng `AtomicInteger` hoặc đoạn tới hạn phù hợp.

**Câu 6.** Người gọi submit `Callable<Integer>` rồi gọi `future.get()` thành công. Làm sao chứng minh có thể thấy các thay đổi trước khi task hoàn tất?

**Đáp án:** Các hành động trong task happens-before hành động của caller sau khi `Future.get()` trả kết quả thành công, theo memory consistency của executor/future.

**Câu 7.** Tại sao một lần ngủ `sleep(500)` sau ghi dữ liệu không đủ để cho reader dựa vào kết quả?

**Đáp án:** Thời gian chờ không tạo synchronizes-with. Phải dùng volatile, lock, latch, join, future hoặc cơ chế được đặc tả phù hợp với giao thức trao đổi dữ liệu.

**Ghi nhớ:** Program order cho quan hệ happens-before **bên trong thread**. Synchronizes-with nối các thread. Tính bắc cầu ghép những cạnh đó thành chứng minh visibility/ordering. Sau khi có happens-before, vẫn phải kiểm tra **atomicity**, các writer cạnh tranh và việc tuân thủ giao thức đồng bộ để khẳng định tính đúng của chương trình.

### Tài liệu tham khảo chuẩn

- Java Language Specification, `17.4 “Memory Model”, đặc biệt `17.4.4 “Synchronization Order” và `17.4.5 “Happens-before Order”: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
- JDK 17 `java.util.concurrent` package documentation (memory consistency properties): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html
- JDK 17 `Thread` và `CountDownLatch` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html và https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-004">Quy tắc lock/unlock của monitor</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc lock/unlock của monitor trong Java Memory Model

> **Phạm vi:** quy tắc sở hữu, vào/ra intrinsic monitor, tính loại trừ lẫn nhau (*mutual exclusion*) và bảo đảm *happens-before* khi lock/unlock trong **Java SE/JVM**. Bài tập trung vào Java Memory Model (JMM), `synchronized` và `Object.wait/notify`. Những cơ chế riêng của Spring được trình bày ở phần khác.

## 1. Mục tiêu bài học

Sau khi học, bạn cần trả lời được:

- Một **monitor** gắn với object nào? Các cách `synchronized` lấy và nhả monitor hoạt động ra sao?
- **Một thread cùng lúc giữ monitor**, reentrancy, thời điểm nhả khóa bình thường/khi exception, và trường hợp thread khác phải chờ được hiểu thế nào?
- Quy tắc chính thức **unlock trên monitor m *synchronizes-with* mọi lock sau đó trên chính monitor m** có ý nghĩa gì?
- Vì sao quy tắc đó tạo **visibility/ordering** thông qua quan hệ *happens-before*, ngoài việc ngăn hai thread cùng vào critical section?
- Dùng **cùng một monitor** thế nào để bảo vệ các trường dữ liệu, invariant, một phép read-modify-write và giao thức chờ/thông báo?
- Những giới hạn của `synchronized`: thứ tự cấp khóa không cố định, không tự bảo vệ thao tác ngoài khóa, không tự làm một chuỗi nhiều lần gọi trở thành atomic.

**Điểm cốt lõi:** `synchronized` đem lại **hai loại bảo đảm** khi được dùng nhất quán: (1) mutual exclusion trong critical section trên cùng monitor và (2) quan hệ bộ nhớ giữa **unlock** và **lock tiếp theo của cùng monitor**. Một chức năng đúng cần cả hai khi các thread trao đổi dữ liệu có thể thay đổi.

## 2. Intrinsic monitor là gì?

Mỗi object Java có một monitor dùng cho đồng bộ hóa nội tại (intrinsic lock). Để vào một khối `synchronized (monitor) { ... }`, thread phải giành quyền **sở hữu monitor** của **đúng object tham chiếu bởi biểu thức `monitor`** tại thời điểm đó.

- Nếu monitor chưa do thread khác giữ, thread có thể vào critical section.
- Nếu thread khác đang giữ, thread đang tranh monitor phải đợi; trạng thái Java có thể quan sát là `Thread.State.BLOCKED`.
- Tại một thời điểm, chỉ **một thread** được sở hữu mỗi monitor, nhưng chính thread đó có thể vào lại monitor nhiều lần (*reentrant*).
- Khi rời vùng đồng bộ, số lần sở hữu được giảm tương ứng; monitor chỉ thật sự khả dụng cho thread khác khi mức sở hữu đã trở về 0.
- Monitor là thuộc tính của **object identity**, không phải tên biến, tên method hoặc văn bản giống nhau của biểu thức khóa.

Ví dụ khóa tại cấp instance:

```java
final class Inventory {
    private final Object guard = new Object();
    private int stock = 10;

    void sellOne() {
        synchronized (guard) {
            if (stock > 0) {
                stock--;
            }
        }
    }

    int remaining() {
        synchronized (guard) {
            return stock;
        }
    }
}
```

Đây là class độc lập có thể biên dịch khi đặt trong file `Inventory.java` (class không public). Nếu tất cả đường truy cập `stock` đi qua `guard`, một lần bán và việc đọc snapshot không thể xen kẽ tùy ý *bên trong* critical section. Với nhiều thread gọi `sellOne()`, số lượng không bị bán âm do hai thread cùng quan sát `stock > 0` trước khi trừ. Mỗi lần gọi vẫn có thể đến theo bất kỳ lịch nào; `synchronized` không hứa thứ tự khách hàng được phục vụ.

### Ba dạng synchronized thường dùng

| Dạng Java | Monitor thực sự được dùng | Phạm vi cạnh tranh |
|---|---|---|
| `synchronized (lockObject) { ... }` | Object do `lockObject` trỏ tới | Mọi vùng đồng bộ dùng **cùng object** |
| `synchronized void method() { ... }` trong instance | `this` | Các instance method synchronized khác trên **cùng instance**, hoặc `synchronized(this)` |
| `static synchronized void method() { ... }` | Object `SomeClass.class` tương ứng | Các static synchronized methods cùng Class object |

Một instance synchronized method trên object A **không dùng chung monitor** với cùng method trên object B. Instance synchronized method cũng **không dùng monitor của class** trừ khi lập trình viên chủ động dùng `synchronized (SomeClass.class)`.

`synchronized` là cấu trúc ngôn ngữ: khi vào khối có thể tương ứng với `monitorenter`, khi ra với `monitorexit` trong bytecode; các instance/static synchronized method có thể được biểu diễn bằng cờ `ACC_SYNCHRONIZED` và JVM thực thi semantics tương ứng. Không cần và không thể gọi thủ công một hàm `unlock()` dành cho intrinsic monitor. Khối `synchronized` tự giải phóng khi luồng điều khiển rời khối, kể cả do exception; điều này khác với explicit lock cần `finally { lock.unlock(); }`.

## 3. Mutual exclusion và reentrancy

### Mutual exclusion ngăn interleaving bên trong cùng critical section

Giả sử hai thread cùng gọi:

```java
synchronized (guard) {
    balance = balance + 100;
}
```

Đoạn trên chỉ là **minh họa trong một class**, nơi `guard` và `balance` đã được khai báo. Phép đọc–tính–ghi của một thread không thể bị thread khác cũng đang cố vào **cùng `guard`** chen vào giữa vùng đồng bộ. Trong điều kiện mọi cập nhật/đọc cần bảo vệ đều tuân thủ monitor đó, một phép cộng logic sẽ không bị mất cập nhật như `balance += 100` thiếu khóa.

Tuy nhiên, monitor **không ngăn** code không sử dụng `guard` truy cập biến trực tiếp. Nếu một method khác đọc/ghi `balance` bên ngoài khóa và không có cơ chế HB độc lập phù hợp, vẫn có thể xảy ra data race hoặc vi phạm invariant.

### Cùng thread có thể vào lại monitor (reentrant)

`synchronized` cho phép một thread đã giữ monitor gọi tiếp method cũng cần **chính monitor ấy** mà không tự deadlock:

```java
final class ReentrantExample {
    private int count;

    synchronized void outer() {
        inner(); // outer đã giữ monitor this
    }

    synchronized void inner() {
        count++;
    }

    synchronized int getCount() {
        return count;
    }
}
```

Đây là class độc lập có thể lưu dưới tên `ReentrantExample.java`. Thread gọi `outer()` lấy monitor `this`, `inner()` vào lại đúng monitor đó, rồi hai scope kết thúc và quyền sở hữu được nhả tương ứng. **Reentrancy không có nghĩa nhiều thread được đồng thời sở hữu monitor**: đó vẫn là một thread với mức reentrant depth lớn hơn một.

Khi dùng monitor lồng nhau, cần đặc biệt chú ý **thứ tự lấy các monitor khác nhau**. Reentrancy trên cùng monitor không giải quyết được deadlock do A giữ monitor X đợi Y, còn B giữ Y đợi X.

## 4. Quy tắc lock/unlock trong JMM

Đây là quy tắc **chính thức tại JLS `17.4.4**:

> Một hành động **unlock** trên monitor `m` *synchronizes-with* tất cả hành động **lock** sau đó trên cùng `m`; “sau đó” được xác định trong **synchronization order**.

Chú ý ba thành phần của câu này:

1. **`m` phải là cùng monitor/object**. Unlock `leftLock` không tự tạo cạnh *synchronizes-with* cho lần lock `rightLock`.
2. Quy tắc áp dụng giữa hành động **nhả và lấy monitor**, không phải tự động giữa mọi câu lệnh xảy ra ở hai thread bất kỳ.
3. **Synchronization order** là thứ tự tổng của các synchronization actions trong một execution và nhất quán với program order trong mỗi thread. Nó là khái niệm hình thức của JMM, không phải lời hứa về FIFO, fairness hay thread nào chạy trước trong thực tế.

Trong JMM:

- **Program order** đưa hành động trước–sau trong cùng thread vào quan hệ *happens-before*.
- **Synchronizes-with** tạo các cạnh liên thread, ví dụ unlock→subsequent lock của cùng monitor.
- **Happens-before (HB)** bao gồm program order và synchronizes-with, rồi lấy tính **bắc cầu**.

Vì vậy ta có sơ đồ:

```text
Thread A                          Thread B
--------------------------------  --------------------------------
synchronized (m) {                 synchronized (m) {
    sharedData = 42;                  int seen = sharedData;
} // unlock(m)                    } // unlock(m)
   |                                   ^
   +--- synchronizes-with ------------+
        lock(m) sau unlock(m)

Ghi sharedData = 42  --program order--> unlock(m)
unlock(m)            --synchronizes-with--> lock(m) của B
lock(m) của B        --program order--> đọc sharedData
=> Ghi của A happens-before đọc của B (nếu B lấy khóa sau A nhả).
```

Ở ví dụ trên, không thể suy ra B luôn đọc 42 từ việc hai thread được tạo ra. **Nếu B vào critical section trước A**, B có thể đọc giá trị cũ theo thứ tự thực thi hợp lệ. Điều được bảo đảm là: khi lần lấy monitor của B xảy ra **sau** lần unlock của A theo synchronization order, các ghi trước unlock của A được công bố theo HB cho các thao tác sau lock B, nếu không có các cập nhật hợp lệ khác thay đổi kết quả.

### Ví dụ cụ thể: truyền dữ liệu dưới cùng monitor

```java
final class SharedMessage {
    private final Object monitor = new Object();
    private String message;

    void publish(String value) {
        synchronized (monitor) {
            message = value;
        } // release monitor
    }

    String snapshot() {
        synchronized (monitor) { // acquire same monitor
            return message;
        }
    }
}
```

Đây là class độc lập, có thể đặt trong `SharedMessage.java`. Nếu `snapshot()` lấy monitor sau khi `publish()` đã nhả nó, thay đổi `message` trước unlock có quan hệ HB với lần đọc trong snapshot. **Tuy vậy**, một caller gọi `snapshot()` trước khi có publish có thể nhận `null`, và nhiều publish có thể tạo giá trị khác nhau theo thứ tự được thực thi. Nếu yêu cầu là “consumer phải đợi có thông điệp”, cần thêm predicate và `wait/notify` (phần sau), hoặc một công cụ phù hợp như `BlockingQueue`.

### “Release/acquire” không phải lệnh flush cache thủ công

Mô tả bằng “unlock flush hết cache, lock tải hết RAM” là cách giải thích **sai hoặc quá thô**. JMM là **hợp đồng quan sát/ordering** cho execution; JVM/JIT/CPU có thể dùng barriers hoặc cơ chế khác để thực hiện hợp đồng trên mỗi nền tảng. Người viết Java cần dựa vào HB và atomicity, không dựa vào một sơ đồ cache vật lý giả định.

Điểm đáng nhớ: **mutual exclusion** khiến những thread cùng dùng monitor không thực hiện phần protected code đồng thời; **HB** khiến ghi trước unlock được quan sát đúng bởi các lần lock theo sau. Hai tác dụng bổ sung cho nhau. Trong trường hợp một thread chỉ đọc mà không có cùng khóa, monitor alone không tạo ra cạnh HB từ lần unlock tới lần đọc đó.

## 5. Khi nào lock và unlock thực sự diễn ra?

### Vào/ra khối synchronized

```java
synchronized (m) {        // cố gắng acquire monitor m
    doCriticalWork();
}                         // release monitor m khi ra khối
```

Đây là code minh họa với `m` và `doCriticalWork()` được định nghĩa trong class chứa đoạn. Nếu `doCriticalWork()` ném exception, Java vẫn bảo đảm giải phóng phần sở hữu monitor tương ứng của khối. Trường hợp `m == null`, việc cố vào khối gây `NullPointerException`: chưa có monitor hợp lệ được lấy.

### synchronized method

```java
public synchronized void update() {
    state++;
}
```

Đây là method minh họa trên một instance có trường `state`. Acquire monitor `this` trước khi thực hiện body; unlock khi method kết thúc. Với `static synchronized`, monitor là object `Class`. Thực hiện một cuộc gọi `this.update()` trong khi đã ở `synchronized(this)` là ví dụ **reentrance**.

### Ngoại lệ, return, break

Không phải viết `finally` để nhả intrinsic monitor khi `return` hoặc exception làm rời `synchronized`. Nhưng **logic rollback nghiệp vụ không tự xảy ra**: nếu code thay đổi hai trường rồi exception ở giữa, monitor được nhả, nhưng dữ liệu có thể còn ở trạng thái trung gian. Phải tự thiết kế invariant, exception safety hoặc commit/rollback logic nếu cần.

### Khi đang chờ lấy monitor

Thread khác giữ `m` → contender đi vào `synchronized(m)` → có thể ở `Thread.State.BLOCKED`. Không có hợp đồng rằng contender được lấy khóa theo thứ tự nó bắt đầu chờ. `Thread.interrupt()` **không làm lời gọi monitor entry ném `InterruptedException`**, và không tự biến thao tác vào `synchronized` thành interruptible lock acquisition.

**Không tự đồng nhất các kiểu chờ**: một thread đợi `ReentrantLock.lock()` thường sử dụng cơ chế park và có thể quan sát `WAITING`; đó không phải monitor `BLOCKED` của `Thread.State`. Explicit lock có hợp đồng memory effects tương đương cho acquire/release theo `Lock` API, nhưng đó là một primitive khác với monitor của `synchronized`.

## 6. Một object khóa ổn định cho cùng invariant

Một nguyên tắc thiết kế: **mọi truy cập trạng thái chia sẻ cấu thành cùng invariant phải được điều phối bởi cùng giao thức đồng bộ.**

Ví dụ hai trường cần nhất quán:

```java
final class Account {
    private final Object guard = new Object();
    private long balance;
    private long version;

    Account(long initial) {
        balance = initial;
    }

    void deposit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException();
        synchronized (guard) {
            balance += amount;
            version++;
        }
    }

    String snapshot() {
        synchronized (guard) {
            return "balance=" + balance + ", version=" + version;
        }
    }
}
```

Đây là class độc lập có thể biên dịch trong `Account.java`. Ở ví dụ, `balance` và `version` cập nhật trong cùng critical section, `snapshot()` đọc cả hai dưới cùng monitor nên không quan sát một nửa bước cập nhật đang tiến hành. Mã chỉ minh họa khóa/invariant, **không** bao gồm xử lý tràn số `long`; chương trình tài chính thực tế phải thêm kiểm tra overflow và quy tắc chính xác số tiền.

### Các lựa chọn monitor cần tránh nếu thiếu lý do

- `synchronized (new Object())` bên trong mỗi lần gọi tạo **monitor mới** cho từng lần, không tạo sự loại trừ giữa hai lần gọi.
- Dùng khóa khác nhau cho writer và reader không tạo cùng một quy tắc HB từ unlock tới lock của reader.
- Dùng một tham chiếu lock có thể thay đổi (`lock = anotherObject`) làm các thread có thể đồng bộ trên các object khác nhau.
- `synchronized (this)` trong API công khai cho phép bên ngoài chủ động giữ `this` và làm nghẽn nội bộ; monitor `private final Object guard` thường dễ kiểm soát hơn.
- Đồng bộ trên string literal/interned string, wrapper bị boxing/caching hoặc một object có thể bị chia sẻ ngoài ý muốn tạo contention và rủi ro tương tác với code khác.
- Một khóa toàn cục bảo vệ trạng thái không liên quan có thể làm giảm concurrency; ngược lại, dùng quá nhiều khóa cho một invariant dễ phá tính atomic.

**Lưu ý:** `final` trên biến tham chiếu `guard` chỉ bảo đảm nó không được gán lại theo luồng code bình thường; `final` không làm những trường mutable khác tự thread-safe. Chính việc sử dụng cùng monitor một cách nhất quán và safe publication của object chứa nó tạo nền tảng cho thiết kế đúng.

## 7. Thử nghiệm Java 17+ có kết quả xác định

Chương trình dưới đây minh họa **mutual exclusion** khi hai worker tăng cùng một bộ đếm và **visibility/ordering** khi một producer thông báo cho consumer qua monitor. Đầu ra không phụ thuộc việc worker nào chạy trước.

Tạo file **`MonitorRulesDemo.java`**:

```java
public final class MonitorRulesDemo {
    private static final class Counter {
        private int count;

        synchronized void increment() {
            count++;
        }

        synchronized int get() {
            return count;
        }
    }

    private static final class Mailbox {
        private final Object monitor = new Object();
        private boolean ready;
        private String message;

        void publish(String text) {
            synchronized (monitor) {
                message = text;
                ready = true;
                monitor.notifyAll();
            }
        }

        String awaitMessage() throws InterruptedException {
            synchronized (monitor) {
                while (!ready) {
                    monitor.wait();
                }
                return message;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();

        Runnable work = () -> {
            for (int i = 0; i < 10_000; i++) {
                counter.increment();
            }
        };

        Thread first = new Thread(work, "first");
        Thread second = new Thread(work, "second");
        first.start();
        second.start();
        first.join();
        second.join();
        System.out.println("count=" + counter.get());

        Mailbox mailbox = new Mailbox();
        Thread receiver = new Thread(() -> {
            try {
                System.out.println("message=" + mailbox.awaitMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "receiver");

        receiver.start();
        mailbox.publish("ready");
        receiver.join();
        System.out.println("receiverFinished=" + !receiver.isAlive());
    }
}
```

Từ thư mục chứa `MonitorRulesDemo.java`, biên dịch/chạy:

```bash
javac --release 17 MonitorRulesDemo.java
java MonitorRulesDemo
```

**Stdout đã xác minh:**

```text
count=20000
message=ready
receiverFinished=true
```

**Phân tích tại sao không bị lost update:**

1. Hai worker mỗi thread gọi `increment()` 10.000 lần; method là instance `synchronized` nên **đều lấy monitor của cùng object `counter`**.
2. `count++` bên trong monitor được thực hiện trọn vẹn cho mỗi lần gọi, không có worker nào khác lọt vào method đồng bộ cùng monitor giữa lúc đó.
3. `first.join()` và `second.join()` chờ hai worker hoàn tất. Lúc main gọi `get()` sau đó, không còn tác vụ tăng đang chạy; kết quả chính xác là 20.000. `join()` cũng có quan hệ HB riêng về hoàn tất thread, nhưng **join không thay thế việc lock bảo vệ các phép tăng**.
4. Nếu bỏ synchronized trên `increment()` và giữ `join()`, kết quả có thể mất cập nhật vì hai worker đã chạy đua trước khi main chờ xong.

**Phân tích tại sao thông điệp luôn là “ready”:**

1. `publish()` giữ `mailbox.monitor`, ghi `message`, rồi đặt `ready = true`.
2. `notifyAll()` chỉ thông báo; monitor vẫn do producer giữ đến khi thoát `synchronized`.
3. `awaitMessage()` giữ **cùng monitor** và kiểm tra `ready` trong vòng `while`. Nếu chưa có dữ liệu, `wait()` nhả monitor và đợi. Khi thức dậy, nó **phải lấy lại monitor** trước khi rời `wait()` rồi kiểm tra lại `ready`.
4. Trường hợp producer publish **trước** khi receiver vào `wait()` cũng đúng: receiver đọc được `ready == true` dưới monitor, không đợi nữa. Nhờ predicate, không bị “mất thông báo” theo cách dùng `notify` mà không có trạng thái.
5. Các ghi `message/ready` của producer trước unlock được bảo đảm HB với lần lock/reacquire sau đó của consumer; consumer đọc dữ liệu trong critical section có visibility phù hợp.
6. `receiver.join()` bảo đảm dòng `message=ready` đã thực hiện trước dòng cuối cùng và `receiverFinished=true`.

Mã này cố ý dùng **platform threads** chạy tốt từ Java 17. Scheduler có thể chọn thứ tự nội bộ bất kỳ, nhưng output trên được bảo đảm khi chương trình hoàn thành bình thường. Với production, cấu trúc truyền dữ liệu như `BlockingQueue` thường tiện hơn tự quản lý wait set, nhưng bài này dùng monitor để giải thích semantics của Java.

## 8. `wait()` và `notify()` thay đổi ownership thế nào?

Phần dễ hiểu sai: `Object.wait()` **nhả monitor**, còn `Object.notify()/notifyAll()` **không tự nhả monitor**.

### Quy trình chờ đúng

Khi thread đang giữ monitor của object `m` và gọi `m.wait()`:

1. Thread **phải sở hữu** monitor `m`; nếu không, Java ném `IllegalMonitorStateException`.
2. Thread được đưa vào wait set của `m`, **nhả toàn bộ mức sở hữu trên chính monitor `m`** và chờ được đánh thức.
3. Một sự kiện như `notify`/`notifyAll`, interrupt, timeout (ở overload có timeout) hoặc spurious wakeup có thể kết thúc pha đợi.
4. Thread phải **lấy lại monitor `m`** trước khi `wait()` thực sự trả về bình thường hoặc ném `InterruptedException`; nếu monitor vẫn bận, nó có thể ở `BLOCKED` khi reacquire.
5. Bởi vì điều kiện có thể đổi, **kiểm tra predicate bằng vòng `while`** mỗi khi tiếp tục.

`wait()` **không nhả bất kỳ monitor khác** nào thread cũng đang sở hữu. Do đó lồng nhiều `synchronized` rồi `wait` trên một object mà vẫn giữ object khác có thể gây deadlock.

### Sơ đồ về thứ tự sở hữu

```text
Thread consumer                          Thread producer
---------------------------------------  -----------------------------
lock(m)
while (!ready):
    wait(m): release(m)
            [đang đợi]                 lock(m)
                                       message = "ready"
                                       ready = true
                                       notifyAll(m)
                                       ... vẫn giữ m ...
                                       unlock(m)
    [reacquire(m) thành công]
    đọc message
unlock(m)
```

Việc consumer đọc sau reacquire nhận các ghi được công bố của producer là hệ quả của HB qua monitor (cùng các quy tắc đồng bộ liên quan). **`notifyAll()` tự nó không chuyển ngay quyền sở hữu**. Nếu producer gọi `notifyAll()` rồi xử lý lâu bên trong monitor, waiter đã được đánh thức vẫn phải chờ.

Ví dụ lỗi phổ biến:

```java
// MINH HỌA SAI: không được gọi wait/notify khi chưa sở hữu monitor.
Object m = new Object();
m.notifyAll(); // IllegalMonitorStateException
m.wait();      // IllegalMonitorStateException
```

Đây là các lệnh minh họa không nằm trong chương trình chạy đầy đủ; dòng `m.wait()` còn phải được đặt trong ngữ cảnh method khai báo/xử lý `InterruptedException`. Để dùng đúng: gọi `m.wait()` và `m.notifyAll()` **trong `synchronized(m)`**, và chỉ wait khi predicate chưa thỏa.

### Sự khác nhau với sleep

`Thread.sleep(...)` không nhả monitor mà thread đang nắm giữ; `sleep` cũng không tạo ra cạnh *synchronizes-with* chỉ vì trôi qua thời gian. Nếu muốn đợi điều kiện gắn với monitor, dùng `wait` với predicate hoặc primitive concurrency cấp cao phù hợp; đừng thay bằng `sleep`/`yield` để “đợi thread khác xong”.

## 9. Vì sao locking đơn lẻ vẫn có thể không đủ?

### Check-then-act phải nằm trong **một** critical section

Sai về atomicity nghiệp vụ:

```java
// Giả sử getBalance()/withdraw() mỗi method đều synchronized
if (account.getBalance() >= 100) {
    account.withdraw(100);
}
```

Đây là đoạn mã minh họa và `account` có API giả định. Dù từng method tự khóa đúng, **giữa lần `getBalance()` và `withdraw()` monitor đã được nhả**. Hai thread có thể cùng thấy đủ tiền rồi cùng rút. Cách đúng là một method trên `account` thực hiện **kiểm tra + cập nhật** dưới cùng monitor:

```java
synchronized boolean withdrawIfEnough(long amount) {
    if (amount < 0 || balance < amount) return false;
    balance -= amount;
    return true;
}
```

Đoạn trên là method của một class có trường `long balance`. Lệnh `return` tự rời synchronized method và nhả monitor. Trong hệ thống thực tế còn phải định nghĩa xử lý overflow, transaction, persistence và quyền truy cập, nhưng về Java monitor, kiểm tra và trừ ở đây thuộc cùng một critical section.

### Hai khóa khác nhau không phối hợp qua monitor rule

```java
// Ví dụ minh họa SAI: hai monitor khác nhau cho cùng sharedValue.
private final Object writerLock = new Object();
private final Object readerLock = new Object();
private int sharedValue;

void set(int v) {
    synchronized (writerLock) { sharedValue = v; }
}

int get() {
    synchronized (readerLock) { return sharedValue; }
}
```

Đây là thành viên minh họa của một class. `writerLock` và `readerLock` khác object, nên lần unlock thứ nhất **không** synchronizes-with lần lock thứ hai qua monitor rule. Hơn nữa writer và reader có thể vào vùng “bảo vệ” đồng thời. Sửa bằng **một monitor duy nhất** cho mọi truy cập gây xung đột, hoặc một giao thức khác có memory semantics tương đương được chứng minh.

### volatile khác monitor

`volatile` có quy tắc *synchronizes-with* cho **ghi → đọc** cùng volatile variable theo JMM. Nhưng `volatile int count` vẫn không làm `count++` atomic; nó là đọc–tính–ghi. `synchronized` tạo exclusion qua monitor và HB qua unlock→lock; `AtomicInteger.incrementAndGet()` là thao tác atomic riêng. Chọn theo **invariant**:

| Yêu cầu | Công cụ thường phù hợp |
|---|---|
| Nhiều bước đọc/ghi cần atomic cùng nhau | `synchronized` hoặc `Lock` bảo vệ cả nhóm |
| Cập nhật độc lập một số nguyên bằng phép atomic | `AtomicInteger` / `AtomicLong` |
| Công bố một cờ và dữ liệu có đúng giao thức visibility | `volatile` khi đủ, hoặc monitor/queue |
| Consumer chờ producer bàn giao item | `BlockingQueue`, `Condition` hoặc `wait/notify` đúng predicate |
| Nhiều trường phải nhất quán trong một snapshot | Cùng monitor/lock hoặc thiết kế dữ liệu bất biến |

`ReentrantLock` theo `java.util.concurrent.locks.Lock` quy định hành vi bộ nhớ của lock/unlock tương đương tác dụng của monitor lock/unlock; nó có API khác như timeout và interruptible acquisition. **Không coi `Lock` và `synchronized` là một object monitor giống nhau**. Đổi sang primitive khác cần thay cả acquire/release protocol nhất quán.

## 10. Visibility, liveness và fairness không phải một thứ

Quy tắc HB trả lời: **khi một truy cập đã được ordering tương ứng, các ghi nào phải được thấy đúng?** Nó **không trả lời** thread có chắc chắn được CPU chạy hay được monitor trước đối thủ hay không.

- **Safety:** dữ liệu và invariant không bị phá, chẳng hạn bộ đếm không mất cập nhật.
- **Visibility:** các cập nhật đã được công bố qua cùng monitor được quan sát theo HB.
- **Liveness:** một thread có khả năng tiếp tục hoàn thành, không mắc deadlock/starvation vô hạn.
- **Fairness:** chính sách phân phối quyền tiến triển có thiên vị hay không.

`synchronized` bảo đảm mutex và ordering; **không có cam kết FIFO/fairness** giữa các contender. Giữ monitor khi thực hiện I/O chậm có thể làm throughput giảm và các thread khác chờ lâu. Lấy monitor A rồi B ở một thread, nhưng B rồi A ở thread khác có thể tạo **deadlock**. Các lỗi liveness này cần thiết kế acquisition order, giảm critical section, timeout khi khả thi và đo tải thực tế; HB không tự chữa chúng.

Một pattern tránh deadlock cơ bản khi phải lấy hai monitor là **quy ước thứ tự toàn cục** cho mọi caller. Tuy nhiên, nếu thiết kế không cần giữ hai monitor cùng lúc, chuyển sang một critical section ngắn hoặc truyền message thường dễ kiểm chứng hơn.

## 11. Tại sao lock/unlock còn có ý nghĩa với dữ liệu không volatile?

Ví dụ counter trong `MonitorRulesDemo` là `private int count` bình thường, **không volatile**. Cách này hợp lệ nếu mọi truy cập từ nhiều thread tuân thủ khóa `this` của chính counter:

- Writer gọi `increment()` và giành monitor, thay đổi `count`, nhả monitor.
- Reader gọi `get()` và giành cùng monitor sau unlock đó.
- Thứ tự “ghi count → unlock → lock → đọc count” được nối bằng HB, nên không cần khai báo count là volatile chỉ để cập nhật/đọc theo giao thức ấy.
- Nếu reader bỏ khóa và chỉ `return count;`, không thể lấy sự bảo đảm memory visibility **từ monitor rule** cho lần đọc đó nữa. Các cạnh HB khác đôi khi vẫn tồn tại (chẳng hạn join trong ví dụ), nhưng code thư viện không nên ngầm phụ thuộc vào tình huống gọi rất riêng này.

Tương tự, dùng `private final Object guard` không biến dữ liệu bên trong thành immutable. “final lock” và “lock-protected mutable data” là hai vai trò riêng: final giúp giữ ổn định identity của object monitor; còn mutual exclusion/HB đến từ các lần acquire/release thực tế.

Cũng cần phân biệt **atomicity của truy cập đơn lẻ** với **atomicity của hành vi nhiều bước**. Một phép đọc/ghi reference hay int có tính nguyên tử của access riêng lẻ theo JMM, nhưng không có nghĩa check-then-act được bảo vệ. Kể cả khi đọc một giá trị “không bị xé”, đọc có thể cũ hoặc xen với cập nhật khác nếu thiếu HB.

## 12. Quan sát quy tắc monitor trong thực tế

### Thread dump

Trong khi ứng dụng Java đang chạy, có thể dùng:

```bash
jcmd <PID> Thread.print -l
```

Thay `<PID>` bằng process ID thật. Xem stack trace và thông tin monitor để phân biệt:

- Thread `BLOCKED (on object monitor)` đang chờ giành intrinsic monitor (thường là `synchronized`).
- Thread `WAITING (on object monitor)` tại `Object.wait()` đang nằm trong wait set, **đã nhả monitor được wait**.
- Thread `WAITING (parking)` tại `LockSupport.park()` có thể chờ `ReentrantLock`, queue hay synchronizer khác; không nhất thiết chờ `synchronized`.
- Thread `RUNNABLE` chưa chắc đang thực sự dùng CPU; có thể chờ OS scheduling.

Một thread dump chỉ là **ảnh tại một thời điểm**. Không thể suy độ dài contention, tần suất chuyển state, hay logic invariant chỉ từ một dòng `Thread.State`.

### Java Flight Recorder (JFR)

Với workload đủ dài để tạo sự kiện:

```bash
jcmd <PID> JFR.start name=monitor-profile settings=profile duration=60s filename=monitor.jfr
jfr summary monitor.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.JavaMonitorWait monitor.jfr
```

`jdk.JavaMonitorEnter` giúp khảo sát thời gian tranh chấp monitor; `jdk.JavaMonitorWait` giúp xem các hoạt động đợi monitor. Chú ý threshold và event settings: **không thấy event không chứng minh không có lock contention**. Dùng kết hợp thread dump, CPU profiling và application metrics; chạy ví dụ rất ngắn thường không tạo recording hữu ích.

### ThreadMXBean

`java.lang.management.ThreadMXBean` có thể cung cấp thông tin blocked/waited count và time. Muốn đo thời gian contention, kiểm tra `isThreadContentionMonitoringSupported()` trước rồi mới cân nhắc bật `setThreadContentionMonitoringEnabled(true)`, vì đo lường có overhead và có thể bị giới hạn bởi JVM. `getBlockedCount()/getBlockedTime()` gắn với **monitor contention**, không phải tổng thời gian đợi mọi explicit lock hay số lần context switch OS. Dùng số liệu theo thời gian thay vì kết luận từ một snapshot.

### Phương pháp tìm lỗi monitor theo trình tự

1. Xác định **state/invariant** đang sai (lost update, stale read, snapshot không nhất quán, treo).
2. Ghi lại **mọi vị trí đọc/ghi** state và object cụ thể được dùng để khóa.
3. Vẽ đường HB cần có: writer có unlock trên monitor nào? Reader có acquire **cùng monitor** sau đó không? Có primitive khác tạo HB hợp lệ không?
4. Phân biệt **bug atomicity** (hai thao tác xen kẽ) với **visibility bug** (quan sát thiếu HB), và **liveness bug** (không tiến triển).
5. Tái hiện bằng `CountDownLatch`/barrier trong test khi cần; dùng phép kiểm tra invariants, không dựa vào `Thread.sleep()`/`yield()` để “chữa” race.
6. Sau khi sửa, kiểm tra correctness trên nhiều lịch/tải, rồi mới đo throughput/latency/lock contention để quyết định granularity của khóa.

## 13. Các hiểu lầm cần tránh

1. **“`synchronized` chỉ là mutex, không liên quan visibility.”** Sai: unlock và subsequent lock cùng monitor có cạnh *synchronizes-with*, tạo HB.
2. **“Nhả một monitor làm mọi thread đọc thấy dữ liệu ngay, dù không lấy khóa.”** Không có bảo đảm ấy **chỉ từ monitor rule**; cần đúng HB path.
3. **“Hai method đều synchronized là đủ.”** Chưa đủ nếu hai method thuộc **hai instance khác nhau** hoặc các bước check-then-act cần nguyên tử qua nhiều lần gọi.
4. **“`static synchronized` khóa từng instance.”** Sai: dùng monitor `Class` của class đó.
5. **“`synchronized(lock)` và `synchronized(new Object())` giống nhau.”** Sai: object identity quyết định các thread có cạnh tranh cùng monitor hay không.
6. **“Ngoại lệ khiến monitor bị giữ mãi.”** Với khối/method `synchronized`, JVM nhả khi scope rời; nhưng dữ liệu không tự rollback.
7. **“`notifyAll` nhả khóa cho waiter lập tức.”** Sai: caller còn giữ monitor tới khi unlock; waiter phải reacquire.
8. **“`wait` nhả mọi lock thread đang giữ.”** Sai: chỉ nhả monitor của chính object được wait, không phải các monitor khác.
9. **“`Thread.sleep` nhả monitor.”** Sai: ngủ không nhả khóa.
10. **“Có HB thì B luôn chạy sau A theo thời gian.”** HB là ràng buộc ordering/visibility của các action, không cam kết scheduler cho B CPU sau một khoảng thời gian cụ thể.
11. **“`volatile++` tương đương tăng dưới monitor.”** Sai: volatile read/write riêng lẻ không biến read-modify-write thành atomic.
12. **“Monitor cấp quyền công bằng theo FIFO.”** Không có cam kết FIFO/fairness của `synchronized`.
13. **“Nếu synchronized gây overhead, bỏ nó đi rồi dùng join để đọc sau.”** `join` có HB đối với thread hoàn tất, nhưng không ngăn các worker chạy đua khi cùng cập nhật trong thời gian thực thi.
14. **“Java lock luôn thực hiện một lệnh flush toàn bộ cache.”** Không phải mô tả đúng về hợp đồng JMM; dùng quan hệ formal HB để suy luận.

## 14. Bài tập tự kiểm tra có giải đáp

**Câu 1.** A viết `x=17` trong `synchronized(m)` rồi ra khối. B sau đó vào `synchronized(m)` và đọc `x`, không có ghi cạnh tranh khác. B có thể thấy giá trị cũ vì `x` không volatile không?  
**Đáp án:** Không, nếu lần lock của B đứng sau unlock của A trên cùng `m` theo synchronization order. Chuỗi program-order + unlock→lock tạo HB.

**Câu 2.** A nhả monitor `m1`; B lấy monitor `m2` rồi đọc cùng `x`. Có thể viện dẫn quy tắc monitor nói trên để bảo đảm visibility không?  
**Đáp án:** Không. Cạnh unlock→lock này đòi hỏi cùng monitor. Có thể có các HB edges **khác**, nhưng không được suy ra chỉ từ hai monitor khác nhau.

**Câu 3.** A gọi instance synchronized method trên `obj1`, B gọi cùng method trên `obj2`. Hai call có loại trừ lẫn nhau không?  
**Đáp án:** Không, vì monitor lần lượt là `obj1` và `obj2`, trừ khi bên trong còn phối hợp trên shared monitor khác.

**Câu 4.** Vì sao `count++` nằm hoàn toàn trong `synchronized(guard)` cùng mọi access liên quan thì đúng, nhưng hai synchronized method `get()` và `set()` gọi liên tiếp có thể không đủ cho một transaction?  
**Đáp án:** Phép tăng được bảo vệ như một critical section; cặp get-then-set gồm hai lần acquire/release nên thread khác có thể chen vào ở giữa.

**Câu 5.** Một thread đang `synchronized(m)` gọi `m.wait()`. Nó có còn giữ `m` trong lúc chờ không? Khi `wait` trả về thì sao?  
**Đáp án:** Nó tạm nhả monitor `m` khi vào wait set, và phải reacquire `m` trước khi `wait` hoàn tất. Các monitor khác nó giữ không được nhả tự động.

**Câu 6.** A giữ monitor rồi gọi `notifyAll()`. B đang đợi điều kiện có được chạy ngay trong critical section không?  
**Đáp án:** Không. B còn phải được scheduler chạy và giành lại monitor sau khi A nhả. Dùng `while` kiểm tra lại điều kiện.

**Câu 7.** Thread A có thể vào `synchronized(this)` khi nó đang giữ monitor `this` bởi một synchronized method khác không?  
**Đáp án:** Có, vì intrinsic monitor reentrant. Thread B đồng thời sẽ phải đợi đến khi A nhả hết mức sở hữu.

**Câu 8.** Việc gọi `Thread.join()` sau hai worker cùng làm `counter++` thiếu đồng bộ có xóa lost update không?  
**Đáp án:** Không. `join` xác nhận chúng đã kết thúc và tạo HB cho việc main đọc sau đó; lỗi lost update đã có thể xảy ra trước đó giữa hai worker.

**Câu 9.** Vì sao không nên lấy một string literal làm private monitor?  
**Đáp án:** Literal có thể được intern và chia sẻ giữa những phần code không liên quan; vô tình khóa chung gây contention hoặc deadlock ngoài dự kiến. Thường nên dùng `private final Object guard = new Object()`.

**Câu 10.** Một thread giữ monitor quá lâu, nhiều thread khác `BLOCKED`. Java Memory Model có bị vi phạm không?  
**Đáp án:** Chưa chắc. Mutex/HB vẫn có thể đúng, nhưng thiết kế có **vấn đề tiến triển/hiệu năng**. Cần tìm thời gian giữ lock, I/O bên trong critical section và thứ tự lấy khóa.

## Tài liệu tham khảo chính thức

- [JLS Java SE 17 `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): đặc biệt `17.1` về synchronization, `17.4.4` về synchronization order, `17.4.5` về happens-before.
- [JLS Java SE 17 `14.19 — The synchronized Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.19): vào/rời synchronized statement và xử lý abrupt completion.
- [JLS Java SE 17 `8.4.3.6 — synchronized Methods](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.3.6): instance/static method và monitor liên quan.
- [Java SE 17 Object API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): `wait()`, `notify()`, `notifyAll()` và quy tắc sở hữu monitor.
- [Java SE 17 Thread API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): start, join, interrupt và thread states.
- [Java SE 17 Lock API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Lock.html): memory synchronization semantics của explicit locks.
- [Java SE 17 ThreadMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html): số liệu contention khi được hỗ trợ.

**Tổng kết kỹ thuật:** lần **unlock(m)** tạo cạnh *synchronizes-with* tới các lần **lock(m)** sau đó. Khi kết hợp với program order, nó thiết lập *happens-before* để bảo đảm visibility/ordering; còn việc chỉ một thread sở hữu `m` tại một thời điểm bảo vệ atomicity của critical section. Đúng **cùng monitor**, đúng **phạm vi invariant** và đúng **giao thức chờ** mới biến bảo đảm JMM thành correctness cho chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-005">Quy tắc write/read của volatile</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc write/read của `volatile` trong Java Memory Model

## 1. Mục tiêu và phạm vi

Trong Java concurrency, một thread có thể ghi vào biến chung trong khi thread khác đọc. Nếu thiếu cơ chế đồng bộ, việc một thread **đã thực hiện phép gán** không tự bảo đảm thread kia sẽ quan sát đúng giá trị đó hoặc các thay đổi dữ liệu liên quan. Từ khóa `volatile` cung cấp những quy tắc đặc biệt cho thao tác **ghi (write)** và **đọc (read)** một trường: bảo đảm visibility, tham gia vào synchronization order và tạo quan hệ **happens-before**.

Sau bài này, bạn có thể:
- Phát biểu chính xác quy tắc `volatile write` **synchronizes-with** những `volatile read` tiếp theo trên **cùng một biến**, theo synchronization order.
- Giải thích bằng chuỗi happens-before vì sao các ghi thông thường **trước** khi publish một cờ `volatile` có thể được nhìn thấy sau khi thread khác đọc cờ.
- Phân biệt volatile visibility/ordering với atomicity và mutual exclusion; nhận diện vì sao `volatile count++` hoặc “kiểm tra rồi cập nhật” vẫn sai.
- Sử dụng `volatile boolean` cho cờ thông báo một lần, `volatile` reference để publish một immutable snapshot, và biết khi nào phải dùng `synchronized`, atomic API hoặc công cụ phối hợp thread.
- Đọc được ví dụ Java 17+ chạy độc lập, biết điều kiện để output đúng và các giới hạn về liveness, CPU và cập nhật đồng thời.

Phạm vi chỉ gồm **Java SE, Java Memory Model (JMM) và thư viện JDK**. Các diễn giải và ví dụ dưới đây không phụ thuộc framework.

## 2. Phát biểu quy tắc chính thức

Theo Java Language Specification, §17.4.4–§17.4.5:

> Một thao tác **ghi vào biến `volatile v`** *synchronizes-with* mọi thao tác **đọc `v` tiếp theo**, trong đó “tiếp theo” được xác định theo **synchronization order**.

Từ quy tắc đó, có hệ quả: volatile write **happens-before** các volatile read tiếp theo trên cùng biến. `synchronizes-with` là một trong các quan hệ tạo thành `happens-before`; `happens-before` còn bao gồm **program order** trong mỗi thread và tính **bắc cầu**.

Điều kiện cần đọc chính xác:
1. Phải là truy cập **volatile** và cùng một **field/biến `v`**. Hai biến khác nhau không tự có một `synchronizes-with` edge trực tiếp giữa chúng.
2. “Sau” được xét trong **synchronization order** của mô hình bộ nhớ, không đơn thuần dựa vào một đồng hồ tường hoặc timestamp từ log.
3. Một volatile read quan sát giá trị theo các quy tắc hợp lệ của JMM; không cần và không nên mô tả nó như lệnh “đọc trực tiếp RAM”.
4. Một thread có thể vẫn chưa thực hiện volatile read, chưa được scheduler chạy hoặc đang bị chặn vì lý do khác. Quy tắc **visibility/ordering không bảo đảm thời gian hoàn tất** hay đánh thức thread.
5. Nếu sau khi publish, dữ liệu thông thường tiếp tục bị thay đổi đồng thời mà không có giao thức bảo vệ mới, lần read volatile trước đó **không tự biến mọi truy cập dữ liệu tương lai thành an toàn**.

Có thể dùng cách nói “**volatile write tương tự release; volatile read tương tự acquire**” để nhớ hướng truyền thông tin giữa threads. Tuy nhiên đây là cách giải thích thực hành, còn hợp đồng chính xác của Java vẫn là các quy tắc synchronization order và happens-before của JMM.

## 3. Vì sao thread khác thấy được dữ liệu thông thường trước volatile write?

Giả sử có các trường dùng chung:

~~~java
int payload;               // không volatile
volatile boolean ready;    // cờ đồng bộ
~~~

Thread producer:

~~~java
payload = 42; // A: normal write
ready = true; // B: volatile write
~~~

Thread consumer:

~~~java
if (ready) {             // C: volatile read quan sát true
    int answer = payload; // D: normal read
}
~~~

Giả sử **producer là bên duy nhất ghi `payload`**, giá trị `ready` bắt đầu `false`, producer gán `payload` trước khi publish `ready=true`, và không có ghi cạnh tranh vào `payload` sau đó. Nếu consumer đi vào nhánh `if (ready)` nhờ đọc giá trị `true` từ bản publish này thì **`answer` phải là 42**. Điều này không đòi hỏi `payload` cũng phải khai báo volatile.

Chuỗi quan hệ:

~~~text
Producer:   A: payload = 42
                     |
               program order
                     v
            B: ready = true     (volatile WRITE)
                     |
               synchronizes-with
                     v
Consumer:   C: read ready=true  (volatile READ)
                     |
               program order
                     v
            D: read payload
~~~

Kết hợp:
- `A happens-before B` vì cùng producer và A đứng trước B theo program order.
- `B synchronizes-with C` vì C là một volatile read phù hợp trên chính biến `ready`, sau B trong synchronization order.
- `C happens-before D` vì cùng consumer và C đứng trước D theo program order.
- Do tính bắc cầu, **`A happens-before D`**.

Đây chính là **mẫu publication bằng volatile flag**: ghi dữ liệu trước, rồi **publish** thông qua volatile write; bên nhận **đọc cờ trước**, rồi mới đọc dữ liệu. Đặt thứ tự ngược lại sẽ thay đổi hoàn toàn lập luận.

## 4. Ví dụ độc lập Java 17+: publication một lần

Lưu tệp **VolatileWriteReadDemo.java**:

~~~java
public class VolatileWriteReadDemo {
    private static int payload;
    private static volatile boolean ready;

    public static void main(String[] args) throws InterruptedException {
        Thread consumer = new Thread(() -> {
            while (!ready) {
                Thread.onSpinWait();
            }
            System.out.println("payload=" + payload);
        }, "consumer");

        Thread producer = new Thread(() -> {
            payload = 42;
            ready = true;
        }, "producer");

        consumer.start();
        producer.start();

        producer.join();
        consumer.join();
        System.out.println("completed=true");
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac VolatileWriteReadDemo.java
java VolatileWriteReadDemo
~~~

Kết quả khi các threads được scheduler chạy bình thường và không có sự can thiệp khác:

~~~text
payload=42
completed=true
~~~

**Vì sao có thể tin kết quả?**
- Biến tĩnh `ready` được khởi tạo mặc định là `false` và `payload` là 0; producer duy nhất ghi `payload=42`, sau đó volatile write `ready=true`.
- Consumer chỉ đi qua vòng chờ khi volatile read quan sát `true`. Read đó thiết lập quan hệ synchronization cần thiết với lần publish.
- Sau khi read `ready=true`, các hành động trước khi producer publish (trong đó có `payload=42`) happens-before phép đọc payload của consumer.
- `join()` ở main đợi worker kết thúc, giúp main chỉ in `completed=true` sau khi tất cả công việc hoàn tất. **Join không phải lý do payload có giá trị 42 trong consumer**; lý do là volatile publication.
- `Thread.onSpinWait()` chỉ là gợi ý tối ưu cho vòng đợi ngắn, **không tự tạo happens-before**; volatile read trong điều kiện while mới là điểm quan trọng.

**Cảnh báo thực hành:** Vòng `while (!ready)` là **busy-wait**, có thể dùng CPU và không có timeout hoặc hủy. Chỉ dùng để minh họa ngữ nghĩa bộ nhớ hoặc đợi cực ngắn khi có cân nhắc. Nếu cần thread ngủ/được đánh thức, dùng `CountDownLatch`, `BlockingQueue`, `Condition`, `Future` hoặc cơ chế chặn phù hợp. Volatile không thay thế các cơ chế này.

## 5. Volatile write và volatile read bảo đảm những gì?

| Khía cạnh | Ý nghĩa chính xác | Không nên hiểu thành |
|---|---|---|
| **Visibility** | Các ghi thích hợp trước volatile write được sắp xếp happens-before các truy cập sau volatile read tương ứng. | Mọi thread luôn thấy trạng thái mới ngay tức khắc, dù không thực hiện read. |
| **Ordering** | JMM không cho phép các reorder tạo hành vi vi phạm happens-before đã thiết lập. | Máy luôn phát lệnh theo đúng thứ tự nguồn hoặc không bao giờ tối ưu. |
| **Atomicity của truy cập volatile** | Một lần đọc/ghi volatile của field là thao tác nguyên tử theo quy định JMM, kể cả `volatile long` và `volatile double`. | Biểu thức gồm nhiều lần đọc/tính/ghi như `count++` cũng atomic. |
| **Synchronization** | Volatile accesses là synchronization actions; write-read trên cùng field có quan hệ synchronizes-with. | Volatile lock được tài nguyên, đảm bảo mutual exclusion hoặc fairness. |
| **Happens-before** | Program order + synchronizes-with + bắc cầu cho phép chứng minh thứ tự quan sát. | Cùng lệnh gọi, cùng tên field hoặc hai timestamp gần nhau tự tạo happens-before. |
| **Liveness** | Volatile không ngăn compiler giữ mãi một bản cũ của flag trong ví dụ có truy cập volatile đúng cách. | Thread chắc chắn sẽ có CPU, được đánh thức, hay nhận kết quả trong N ms. |

### 5.1. Volatile read nào “thấy” giá trị nào?

Các volatile reads/writes nằm trong một **synchronization order** nhất quán với program order của mỗi thread. Một volatile read phải tuân theo các quy tắc của đồng bộ hóa đối với những volatile writes của **chính field đó**. Nếu có nhiều writer viết `true`, `false` hoặc giá trị số khác nhau, consumer có thể gặp các trạng thái khác nhau tùy interleaving hợp lệ. Không có bảo đảm mọi lần gán được biến thành một “sự kiện” mà mọi consumer phải nhận đủ.

Ví dụ `ready=true` rồi `ready=false` rất nhanh có thể khiến một consumer chưa được chạy **không bao giờ quan sát thấy true**, dù cả hai phép gán đều volatile. Đây là lý do một cờ volatile không phù hợp để biểu diễn **hàng đợi các sự kiện** hoặc đếm số thông báo.

### 5.2. Visibility không có nghĩa “flush nguyên cache về RAM”

Đôi khi volatile được mô tả sơ lược là “writer flush cache, reader đọc main memory”. Cách nói này thiếu chính xác và dễ khiến bạn suy diễn sai trên các CPU/JVM. JMM là **hợp đồng hành vi quan sát được**; JVM/JIT/CPU có quyền chọn instruction và memory barrier phù hợp trên từng kiến trúc, miễn giữ hợp đồng đó. Khi giải thích code, hãy lập luận bằng `happens-before`, không dựa vào một mô hình cache cố định.

### 5.3. Một lần đọc/ghi long và double

JLS cho phép một số thao tác đọc/ghi **non-volatile long/double** bị xử lý theo hai nửa 32 bit trong những triển khai nhất định; đánh dấu `volatile` loại bỏ nguy cơ đọc/ghi “rách” (tearing) đối với **một lần truy cập**. Tuy vậy `volatile long counter; counter++;` vẫn là compound operation gồm read–modify–write, và **có thể lost update** khi có nhiều writer.

## 6. Sai thứ tự publication: đặt ready trước dữ liệu

Xét mã lỗi:

~~~java
// PRODUCER — SAI thứ tự publish:
ready = true;  // volatile WRITE
payload = 42;  // normal WRITE thực hiện sau
~~~

Consumer vẫn:

~~~java
if (ready) {
    System.out.println(payload);
}
~~~

Consumer có thể đọc `ready=true` rồi đọc `payload=0`, vì producer chưa chắc đã gán 42. Không thể áp dụng lập luận “mọi ghi trước volatile write” cho một thao tác **xảy ra sau** volatile write.

~~~text
Producer: ready=true ─────────────► payload=42
              │
              └─ Consumer quan sát ready=true
                                  └─ có thể đọc payload=0
~~~

Chỉ sửa khai báo `payload` thành volatile **không làm thứ tự nghiệp vụ đúng lên**: vẫn có thể đọc trước khi producer ghi 42. Hãy sắp xếp khởi tạo dữ liệu hoàn tất rồi mới publish một tín hiệu được định nghĩa rõ, hoặc dùng một immutable snapshot được publish nguyên tử.

## 7. Safe publication bằng volatile reference

Thay vì dùng nhiều trường mutable cùng cờ, một thiết kế thường rõ ràng hơn là tạo **object không thay đổi**, rồi công bố **tham chiếu volatile** tới object đó.

Ví dụ:

~~~java
public final class ConfigStore {
    public record Config(String endpoint, int retries) {}

    private volatile Config current = new Config("primary", 2);

    public void update(String endpoint, int retries) {
        Config next = new Config(endpoint, retries);
        current = next; // volatile WRITE: publish snapshot mới
    }

    public Config snapshot() {
        return current; // volatile READ: lấy cùng một snapshot
    }
}
~~~

Đây là class Java 17+ độc lập nhưng chưa có `main`; có thể đưa vào dự án và sử dụng như một component lưu cấu hình. Bản thân record giữ hai thành phần `String` và `int`; `String` bất biến. Bên đọc phải **đọc tham chiếu một lần** rồi sử dụng object đó:

~~~java
ConfigStore.Config c = store.snapshot();
use(c.endpoint(), c.retries());
~~~

Đây là đoạn mã minh họa nằm trong một method nơi `store` và `use(...)` đã được khai báo.

Lợi ích:
- Mỗi snapshot chứa các thuộc tính đã được xác lập trước khi `current` được cập nhật.
- Volatile write của reference publish các thay đổi trước đó; volatile read lấy reference và nhận được dữ liệu đã khởi tạo an toàn.
- Reader có thể sử dụng các trường của **cùng snapshot**, tránh đọc `endpoint` của phiên bản A và `retries` của phiên bản B do đọc nhiều field riêng lẻ.

**Ranh giới của lời hứa:** `volatile Config current` chỉ làm reference được đọc/ghi với volatile semantics. Nó **không** khiến nội dung mọi object mutable mà reference trỏ tới trở thành bất biến hay tự động thread-safe. Nếu object chứa `List` mutable và thread khác tiếp tục sửa list không đồng bộ, vẫn có nguy cơ data race. Dùng immutable/deeply immutable state hoặc giao thức đồng bộ cho mutation tiếp theo.

### 7.1. Cẩn thận khi đọc hai lần

~~~java
String endpoint = store.snapshot().endpoint();
int retries = store.snapshot().retries();
~~~

Nếu writer publish snapshot mới giữa hai lần gọi `snapshot()`, hai giá trị có thể đến từ **hai phiên bản khác nhau**. Từng phép đọc volatile đều đúng, nhưng nhóm hai lần đọc không phải một transaction. Muốn các trường nhất quán, đọc **một tham chiếu một lần**, giữ local variable và dùng tất cả thuộc tính từ cùng object.

## 8. Volatile không bảo vệ count++ hoặc check-then-act

Ví dụ sai:

~~~java
class BrokenCounter {
    volatile int count = 0;

    void increment() {
        count++; // vẫn có thể lost update
    }
}
~~~

Hai thread có thể:
1. Cùng volatile read `count = 5`.
2. Cùng tính `5 + 1 = 6`.
3. Cùng volatile write `count = 6`.

Kết quả là 6 thay vì 7. Mỗi đọc/ghi riêng có volatile semantics; **toàn bộ phép tăng** vẫn không atomic. Volatile không nắm lock và không ngăn một thread khác đi vào phần tính toán.

Nếu cần một counter nguyên tử:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

class SafeCounter {
    private final AtomicInteger count = new AtomicInteger();

    void increment() {
        count.incrementAndGet();
    }

    int get() {
        return count.get();
    }
}
~~~

Một biến chỉ để quan sát “công việc đã kết thúc chưa” có thể dùng `volatile boolean done`; một biến “đã xử lý được bao nhiêu yêu cầu” cần `AtomicInteger`, `AtomicLong`, `LongAdder` (với điều kiện đọc phù hợp) hoặc lock tùy mô hình cập nhật.

### 8.1. Check-then-act trên volatile cũng không atomic

~~~java
volatile int tickets = 1;

// Hai thread cùng chạy:
if (tickets > 0) {
    tickets--; // có thể cả hai đều tin rằng mình mua được vé
}
~~~

Ngay cả khi phép `tickets--` tình cờ chỉ giảm một đơn vị tổng cộng do lost update, **hai luồng đều có thể đi vào nhánh**. Việc cần bảo vệ ở đây là **cả kiểm tra điều kiện lẫn thay đổi trạng thái**, không chỉ phép giảm. Có thể dùng một monitor/lock chung hoặc CAS loop trên `AtomicInteger`.

### 8.2. Volatile array khác với volatile elements

~~~java
volatile int[] numbers = {0};

// Thao tác này cập nhật phần tử mảng bình thường:
numbers[0]++;
~~~

Khai báo `volatile int[] numbers` chỉ đặt volatile semantics lên **trường tham chiếu `numbers`**, không khiến mọi truy cập tới `numbers[i]` là volatile hay atomic. Mọi truy cập phần tử cạnh tranh vẫn cần giao thức an toàn. Có thể chọn `AtomicIntegerArray`, lock hoặc immutable array snapshot tùy trường hợp.

Cũng tương tự, `volatile Map<K,V>` không làm các thao tác trên một mutable Map trở nên thread-safe.

## 9. Một volatile flag không phải message queue

Đối với một thay đổi trạng thái duy nhất (từ `false` sang `true`), volatile flag có thể biểu thị “đã sẵn sàng”. Nhưng khi cần gửi nhiều thông báo liên tiếp:

~~~java
volatile boolean changed;

// Producer:
changed = true;
changed = false;
changed = true;
~~~

Consumer đọc không nhất thiết phải quan sát đủ hai lần `true`. Tương tự, `volatile int eventId` có thể bị “nhảy số” khi writer cập nhật nhanh hơn reader xử lý. Volatile giúp nhìn thấy trạng thái phù hợp với ordering, **không lưu mọi chuyển đổi** và không xác nhận mỗi event đã được xử lý.

Nếu yêu cầu nhận đúng từng sự kiện, dùng `BlockingQueue`, `Channel` phù hợp (nếu có thư viện), semaphore đếm hoặc message/queue abstraction có hợp đồng giữ thông báo. Nếu chỉ cần báo hiệu hoàn thành một lần, `CountDownLatch` hoặc `CompletableFuture` thường tránh busy-wait và truyền kết quả/lỗi rõ ràng hơn.

## 10. Quy tắc write/read khi có nhiều volatile fields

Không nên khẳng định “write `volatile a` synchronizes-with read `volatile b`” nếu `a` và `b` là **hai field khác nhau**. Tuy nhiên, thông qua **program order** và các cạnh happens-before khác, volatile field có thể đóng vai trò *cầu nối* để thấy các thao tác trên những field thông thường khác.

Ví dụ:

~~~java
int x;
int y;
volatile boolean published;

// Producer:
x = 10;
y = 20;
published = true;

// Consumer:
if (published) {
    int a = x;
    int b = y;
}
~~~

Khi consumer đã đọc bản publish `true` và không có writer cạnh tranh khác, các ghi `x=10`, `y=20` happens-before các đọc tương ứng. Chúng **không phải volatile** vẫn được bảo vệ theo đường truyền của happens-before.

Ngược lại, ví dụ có hai trường volatile độc lập `volatile boolean a` và `volatile boolean b` không tự tạo “giao dịch” cập nhật hai biến như một cặp. Nếu bất biến yêu cầu `a` và `b` luôn thuộc cùng phiên bản, hãy publish một object chứa cả hai hoặc đồng bộ trên lock chung.

### Không giả định đọc volatile thấy mọi “giá trị mới nhất” của các field khác

Sau khi consumer đọc `published=true`, các write sau thời điểm producer publish (ví dụ writer tiếp tục sửa `x`) không được bảo vệ bởi lần volatile read đó. Nếu nhiều writer cập nhật `x` và `y`, việc thiếu giao thức đồng bộ trên các cập nhật tiếp theo vẫn có thể tạo data race hoặc trạng thái kết hợp sai. Một chiều publication một lần khác với stream cập nhật liên tục.

## 11. Volatile và vòng lặp chờ

Một ứng dụng đôi khi có worker chạy tới khi nhận lệnh dừng:

~~~java
class Worker implements Runnable {
    private volatile boolean stopRequested;

    public void requestStop() {
        stopRequested = true;
    }

    @Override
    public void run() {
        while (!stopRequested) {
            doOneUnitOfWork();
        }
    }

    private void doOneUnitOfWork() {
        // Một đơn vị công việc hữu hạn, tôn trọng lifecycle.
    }
}
~~~

Đây là class minh họa, **không phải chương trình độc lập có main**. Nếu `doOneUnitOfWork()` luôn kết thúc hữu hạn và vòng lặp tiếp tục đọc flag volatile, worker có thể quan sát yêu cầu dừng theo các quy tắc visibility. Nhưng `volatile` **không thể cưỡng bức** một lệnh blocking I/O hoặc wait vô thời hạn hoàn tất, cũng không bảo đảm scheduler cấp CPU trong khoảng thời gian xác định.

Đối với worker đang chờ trên API có thể interrupt:
- Kết hợp trạng thái dừng với `Thread.interrupt()`, timeout hoặc cơ chế hủy được API hỗ trợ.
- Bắt `InterruptedException` đúng hợp đồng: thường thoát hoặc khôi phục interrupt flag qua `Thread.currentThread().interrupt()` khi không ném tiếp.
- Khi shutdown, chờ `join()`, `Future.get()` hoặc đóng executor có kiểm soát để biết công việc thật sự kết thúc.

Không dùng `Thread.sleep()` hay `Thread.yield()` để thay thế quan hệ happens-before; chúng không cung cấp sự đồng bộ cần thiết cho một trường không volatile.

## 12. Trường hợp kinh điển: double-checked locking cần volatile

Một ví dụ sử dụng **safe publication** khác là lazy initialization, khi nhiều thread cần truy cập cùng một instance:

~~~java
public final class LazyService {
    private static volatile LazyService instance;

    private final String label;

    private LazyService() {
        label = "ready";
    }

    public static LazyService getInstance() {
        LazyService result = instance;
        if (result == null) {
            synchronized (LazyService.class) {
                result = instance;
                if (result == null) {
                    result = new LazyService();
                    instance = result;
                }
            }
        }
        return result;
    }

    public String label() {
        return label;
    }
}
~~~

**Phân tích:**
- `instance` là volatile reference; xây dựng object hoàn chỉnh trước lần publish vào trường volatile.
- Đường vào đầu tiên đọc volatile vào biến local. Nếu khác null, caller có thể dùng instance đã được publish an toàn mà không cần lock.
- Nếu null, khóa trên `LazyService.class` chỉ cho một thread vào vùng khởi tạo tại một thời điểm. Kiểm tra lần thứ hai **bên trong lock** ngăn thread đến sau tạo lại instance.
- Sau lần publish, các lần đọc instance non-null phù hợp nhận được các ghi trước volatile write. Constructor ở đây không làm `this` thoát ra nơi khác trước khi xây dựng xong.
- Cấu hình này an toàn theo JMM hiện đại; code phức tạp hơn nhiều so với các lựa chọn đơn giản như **initialization-on-demand holder idiom** hoặc eager singleton. Không chọn double-checked locking chỉ để “thể hiện volatile” nếu không có nhu cầu lazy initialization và fast path thật.

**Lưu ý:** Nếu class này có trạng thái mutable về sau, volatile reference không làm các thao tác trên trạng thái đó tự thread-safe. Cần một hợp đồng concurrency riêng cho trạng thái thay đổi sau khởi tạo.

## 13. Ví dụ biên dịch thứ hai: đọc một snapshot được publish

Chương trình này tạo snapshot bất biến trước khi volatile write và đọc nó sau volatile read. Nó minh họa trường tham chiếu volatile, không yêu cầu mọi field bên trong cũng volatile.

Lưu tệp **VolatileSnapshotDemo.java**:

~~~java
public class VolatileSnapshotDemo {
    record Snapshot(String status, int code) {}

    private static volatile Snapshot latest;

    public static void main(String[] args) throws InterruptedException {
        Thread consumer = new Thread(() -> {
            Snapshot observed;
            do {
                observed = latest; // volatile read
                if (observed == null) {
                    Thread.onSpinWait();
                }
            } while (observed == null);

            System.out.println("status=" + observed.status());
            System.out.println("code=" + observed.code());
        }, "consumer");

        Thread producer = new Thread(() -> {
            Snapshot prepared = new Snapshot("READY", 200);
            latest = prepared; // volatile write
        }, "producer");

        consumer.start();
        producer.start();
        producer.join();
        consumer.join();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac VolatileSnapshotDemo.java
java VolatileSnapshotDemo
~~~

Output xác định với các thread được chạy bình thường:

~~~text
status=READY
code=200
~~~

Mỗi vòng chỉ đọc `latest` một lần vào biến local. Sau khi nhận được tham chiếu non-null, consumer chỉ đọc cùng một snapshot. Việc producer tạo `Snapshot` trước khi publish và consumer đọc `latest` sau khi publish thiết lập happens-before cho quá trình khởi tạo. Cả hai ví dụ runnable dùng spin để làm rõ nguyên lý và **không nên copy nguyên vòng chờ vào production** khi thời gian đợi có thể dài.

## 14. Phân biệt volatile, synchronized và atomic classes

| Nhu cầu | Giải pháp thường phù hợp | Tại sao |
|---|---|---|
| Flag trạng thái được một thread publish, nhiều thread đọc | `volatile boolean` | Read/write và visibility/ordering đủ cho trạng thái đơn giản. |
| Publish một snapshot bất biến hoàn chỉnh | `volatile Snapshot` | Có điểm cập nhật tham chiếu nguyên tử, thấy khởi tạo trước publish. |
| Nhiều writer cùng tăng biến đếm chính xác | `AtomicInteger.incrementAndGet()` hoặc lock | Cần atomic **read–modify–write**; volatile count++ không đủ. |
| Check-then-act và nhiều trường phải tuân theo bất biến | `synchronized`, `Lock` hoặc CAS phù hợp | Cần bảo vệ cả chuỗi hành động logic. |
| Đợi một sự kiện không dùng CPU polling | `CountDownLatch`, `Future`, `BlockingQueue`, `Condition` | Có cơ chế chặn, đánh thức/đợi và thường có cách xử lý lỗi/hủy. |
| Tổng metric có nhiều writer, đọc tổng quan sát | `LongAdder` | Tối ưu contention nhưng `sum()` không phải snapshot nguyên tử khi còn writer. |

Không nên coi `volatile` là “khóa nhẹ”. Một lock cung cấp **mutual exclusion** để cô lập critical section; volatile không bảo vệ chuỗi nhiều hành động khỏi interleaving. Trái lại, khi chỉ cần publish flag/snapshot đúng nghĩa, đưa mọi thứ vào synchronized có thể tăng mức phối hợp không cần thiết. Chọn công cụ theo bất biến và hành vi cần bảo đảm, sau đó đo trong workload thật nếu hiệu năng quan trọng.

## 15. Các lỗi thường gặp khi review code

**Lỗi A — Chỉ publisher dùng volatile nhưng consumer đọc từ đường khác.** Các lời đọc phải truy cập đúng field volatile hoặc dùng một cơ chế đồng bộ tương thích. Một cờ khác cùng tên hoặc bản copy trong biến không volatile không tạo cạnh synchronizes-with mong muốn.

**Lỗi B — Ghi flag trước payload.** Consumer có thể thấy cờ đã bật khi payload còn chưa được ghi. Publish dữ liệu trước, flag sau, và không sửa payload thiếu đồng bộ sau publish.

**Lỗi C — Đặt volatile trên reference rồi sửa object mutable không kiểm soát.** Volatile reference không biến các phần tử `ArrayList`, `HashMap` hoặc field trong object thành atomic/thread-safe.

**Lỗi D — Dùng `volatile count++`.** Bảo đảm đọc/ghi riêng lẻ không kéo theo atomic compound update. Dùng atomic increment hoặc lock.

**Lỗi E — Dùng `volatile` làm notification queue.** Cờ có thể bị ghi đè, và reader có thể bỏ lỡ các chuyển đổi nhanh. Dùng queue hoặc cơ chế đếm/đồng bộ có hợp đồng lưu event khi cần.

**Lỗi F — Đồng nhất happens-before với thời gian thật.** Một volatile write không làm thread khác được scheduler chạy ngay, và một thread không đọc cờ thì không thể nhận tín hiệu chỉ vì biến đã đổi.

**Lỗi G — Cho rằng volatile read đảm bảo mọi dữ liệu mãi nhất quán.** Publication đưa các ghi *trước* tín hiệu tới bên đọc. Những cập nhật *sau* tín hiệu vẫn cần một protocol riêng hoặc snapshot mới.

**Lỗi H — Đọc snapshot nhiều lần và trộn phiên bản.** Với state có nhiều trường, giữ một tham chiếu snapshot local rồi đọc đủ trường từ đó.

**Lỗi I — Dùng sleep để “đợi volatile có hiệu lực”.** `sleep()` không tạo synchronization action cho những biến thông thường; nó chỉ làm thay đổi lịch chạy và không sửa data race.

**Lỗi J — Dựa vào một bài stress test để chứng minh không có lỗi.** JIT, runtime, OS và interleaving đa dạng; không xuất hiện sai số trong một lần chạy không biến code thành đúng theo JMM.

## 16. Cách tự chứng minh một đoạn mã dùng volatile có đúng

Với mỗi trường hợp, ghi rõ bốn mốc:

1. **W(data):** writer tạo và ghi dữ liệu gì?
2. **W(v):** writer có ghi vào **chính volatile field v** sau dữ liệu hay không?
3. **R(v):** reader có thực hiện volatile read từ cùng field và quan sát trạng thái publish phù hợp không?
4. **R(data):** reader chỉ đọc dữ liệu sau R(v), và có writer nào tiếp tục sửa dữ liệu không được đồng bộ không?

Nếu xác lập được `W(data) →program-order W(v) →synchronizes-with R(v) →program-order R(data)` thì kết luận `W(data) happens-before R(data)` là hợp lý. Nếu một mũi tên thiếu, hãy tìm một cơ chế đồng bộ khác **thực sự tồn tại**, đừng tự suy đoán dựa trên thời gian.

### Bài tập nhanh

**Câu 1.** Producer viết `data=10`, rồi `ready=true` (ready volatile). Consumer đọc ready là true rồi đọc data; không có writer nào khác. Data có thể là 0 không?

**Đáp án:** Không, trong điều kiện đã mô tả và quan hệ read/write phù hợp. Ghi data happens-before lần đọc data sau read ready.

**Câu 2.** Đảo thứ tự producer thành `ready=true`; `data=10`. Consumer vẫn đọc ready rồi data. Có thể gặp 0 không?

**Đáp án:** Có. Ghi data xảy ra sau volatile write nên không được bảo đảm bởi lần read flag đó.

**Câu 3.** `volatile int n`; hai thread cùng gọi `n++`. Hai lần tăng có chắc tăng tổng cộng 2 không?

**Đáp án:** Không. Có thể lost update. Phải dùng atomic read–modify–write hoặc lock.

**Câu 4.** Một thread ghi `volatile boolean a=true`; thread kia đọc `volatile boolean b`, với a và b không liên quan. Đọc b có trực tiếp synchronizes-with ghi a không?

**Đáp án:** Không. Quy tắc write/read trực tiếp yêu cầu **cùng field v**; muốn có happens-before cho dữ liệu liên quan phải tìm các cạnh hợp lệ khác.

**Câu 5.** `volatile List<String> list`; sau khi publish list, một writer tiếp tục `list.add(...)` và reader gọi `list.size()` không lock. Reference volatile đã làm việc này an toàn chưa?

**Đáp án:** Chưa. Các mutation đồng thời trên mutable list vẫn phải dùng đúng thiết kế thread-safe/immutable snapshot hoặc synchronization thích hợp.

## 17. Tài liệu đối chiếu

- **Java Language Specification §17.4.4 — Synchronization Order:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.4
- **Java Language Specification §17.4.5 — Happens-before Order:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5
- **Java Language Specification §8.3.1.4 — volatile Fields:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.3.1.4
- **AtomicInteger API:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html
- **CountDownLatch API:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- **Thread.onSpinWait API:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#onSpinWait()

**Ghi nhớ:** *Ghi dữ liệu trước volatile write; reader thực hiện volatile read cùng field trước khi đọc dữ liệu.* Chuỗi program order → synchronizes-with → program order tạo happens-before, giúp publication có visibility/ordering đúng. Nhưng volatile **không làm compound update atomic**, không khóa thread, không lưu đủ mọi sự kiện và không bảo vệ những mutation xảy ra thiếu đồng bộ sau publication.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-006">Thread.start(), Thread.join() và quan hệ đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Thread.start(), Thread.join() và quan hệ đồng bộ trong Java Memory Model

Khi lập trình đa luồng, việc một luồng **thực hiện một phép ghi trước về thời gian** không tự động có nghĩa luồng khác chắc chắn nhìn thấy phép ghi đó. Java Memory Model (JMM) quy định những quan hệ bộ nhớ cho phép ta lập luận về **thứ tự** và **tính hiển thị** giữa các luồng. Hai thao tác căn bản của `Thread` — `start()` và `join()` — đồng thời là những điểm đồng bộ quan trọng trong JMM.

Bài học tập trung vào hai bảo đảm:

1. **Trước `start()` → bên trong luồng được khởi động:** mọi hành động của luồng A trước khi gọi `threadB.start()` có quan hệ *happens-before* với mọi hành động trong luồng B được khởi động.
2. **Bên trong luồng đã kết thúc → sau khi `join()` thành công:** mọi hành động của luồng B có quan hệ *happens-before* với các hành động của luồng A sau khi A phát hiện B đã kết thúc thành công qua `threadB.join()`.

Đây là quan hệ **có hướng**, không phải lời hứa về thứ tự lên lịch CPU. Hiểu đúng hai quy tắc giúp truyền dữ liệu đầu vào sang worker và lấy kết quả sau khi worker hoàn tất, ngay cả khi các biến dữ liệu ví dụ không dùng `volatile` hay `synchronized`.

## 1. Happens-before có nghĩa gì?

Theo JMM, nếu hành động A *happens-before* hành động B, các hiệu ứng bộ nhớ của A được ràng buộc để B quan sát đúng theo quy tắc mô hình bộ nhớ; một phép đọc tại B vẫn phải xét **các phép ghi khác** có thể xen vào, chứ không mặc nhiên nhận đúng giá trị của “phép ghi A” trong mọi trường hợp.

Những điểm then chốt:

- Trong **cùng một thread**, các hành động trước trong thứ tự chương trình (*program order*) happens-before các hành động sau, dù trình biên dịch/JIT/CPU được phép tối ưu trong giới hạn giữ đúng ngữ nghĩa.
- Quan hệ happens-before có tính **bắc cầu**: nếu A → B và B → C thì A → C.
- Một quan hệ *synchronizes-with* (ví dụ `start()` hoặc phát hiện thread kết thúc) tạo ra cạnh happens-before giữa các thread.
- Happens-before **không đồng nghĩa** hai thread chạy lần lượt hoàn toàn. Những phần hành động không được ràng buộc vẫn có thể xen kẽ.
- *Không có* happens-before phù hợp khi hai thread truy cập cùng trạng thái thay đổi có thể tạo **data race** và kết quả không có bảo đảm hiển thị mong đợi.

Vì vậy, `start()` và `join()` nên được hiểu vừa là API vòng đời, vừa là **mốc đồng bộ bộ nhớ**.

## 2. Thread.start(): công bố dữ liệu trước khi khởi động

Quy tắc cốt lõi: **lời gọi `t.start()` trên một thread `t` synchronizes-with các hành động trong thread `t`**. Kết hợp thứ tự chương trình, mọi thao tác được thực hiện trước lời gọi `start()` trong thread khởi tạo happens-before các thao tác trong thread mới.

Sơ đồ:

```text
Thread main                           Thread worker
-----------                           -------------
data.input = 21
data.label = "ready"
      |
      | program order
      v
worker.start()
      |  synchronizes-with
      +--------------------------------> đọc data.input, data.label
                                        tính kết quả
```

Điều này rất hữu ích để **chuyển đầu vào đã chuẩn bị sẵn** cho luồng mới: đối tượng được khởi tạo/điền dữ liệu trước `start()` có thể được worker đọc theo quan hệ happens-before đó. Tuy nhiên, nếu luồng main tiếp tục **ghi cùng biến sau khi `start()`** trong khi worker đang đọc, hai hành động sau `start()` có thể không được đồng bộ với nhau. `start()` không biến mọi chia sẻ dữ liệu tương lai thành an toàn.

Ví dụ đơn giản:

```java
// Trích đoạn minh họa, giả định Data data và Thread worker phù hợp.
data.input = 21;         // Có happens-before với hành động trong worker.
worker.start();
data.input = 99;         // KHÔNG được start() tự bảo vệ đối với worker.
```

Nếu cả main và worker cùng truy cập không đồng bộ đến `data.input` sau `start()`, cần cơ chế phối hợp riêng. Không được suy từ việc đã gọi `start()` rằng worker sẽ nhất định đọc 21 hay 99.

**Điểm dễ nhầm:** `start()` không đợi worker chạy xong, thậm chí không bảo đảm worker đã thực hiện câu lệnh đầu tiên khi `start()` trả về. Nó chỉ khởi động luồng mới và thiết lập cạnh đồng bộ đúng hướng từ các hành động trước `start()`.

## 3. Thread.join(): phát hiện kết thúc và lấy kết quả đúng cách

`thread.join()` khiến **luồng đang gọi** chờ thread đích hoàn tất; nếu bị interrupt trong lúc chờ, phương thức có thể ném `InterruptedException`. Khi phát hiện thread đích đã thực sự kết thúc qua `join()`, các hành động đã thực hiện trong thread đích happens-before các hành động tiếp theo trên luồng gọi.

Sơ đồ:

```text
Thread worker                          Thread main
-------------                          -----------
đọc dữ liệu đầu vào
result.answer = 42
thoát run()
      |
      | thread termination detected by join
      +--------------------------------> worker.join() hoàn tất
                                         |
                                         | program order
                                         v
                                       đọc result.answer
```

Không bắt buộc khai báo `result.answer` là `volatile` **chỉ để chuyển kết quả một chiều** nếu không có truy cập xung đột và main chỉ đọc sau khi `join()` hoàn tất thành công. `join()` tạo điểm chờ vòng đời và bảo đảm hiển thị bộ nhớ cần thiết.

Cần phân biệt:

- `join()` **không** bắt đầu thread. Một `Thread` chưa `start()` không có tác vụ để chờ; gọi `join()` trên nó không khiến `run()` được gọi.
- `join()` **không** tự yêu cầu worker ngừng chạy. Nếu worker bị treo mãi, `join()` không thời hạn có thể chờ mãi.
- `join()` **không** tự lan truyền ngoại lệ xảy ra trong worker sang thread gọi. Phải thiết kế cơ chế thu lỗi/kết quả riêng.
- Chỉ việc **phát hiện thread đã kết thúc** mới tạo quan hệ thích hợp; một lần `join(timeout)` trả về khi worker vẫn sống không phải bằng chứng worker đã hoàn thành.
- Quan hệ bộ nhớ này áp dụng cho *thread cụ thể* được đợi. Nếu worker lại tạo thread con hoặc gửi nhiệm vụ sang executor rồi tự kết thúc, `join()` của worker **không tự động chờ** các tác vụ độc lập còn đang chạy ở nơi khác.

JMM cũng cho phép một thread khác phát hiện termination qua `isAlive()` trả về `false` đối với thread đã thực sự kết thúc; nhưng với mục tiêu chờ hoàn thành, `join()` truyền đạt chủ đích rõ ràng hơn và không buộc phải thăm dò trạng thái liên tục.

## 4. Ví dụ 1 — Truyền input trước start, đọc output sau join

Ví dụ đầy đủ này cố ý dùng trường `int` bình thường, không thêm `volatile` hay `synchronized`, để thấy sự kết hợp của hai cạnh happens-before.

Tạo file **`StartJoinVisibility.java`**:

```java
public class StartJoinVisibility {
    static final class Data {
        int input;
        int output;
    }

    public static void main(String[] args) throws InterruptedException {
        Data data = new Data();
        data.input = 21;

        Thread worker = new Thread(() -> {
            data.output = data.input * 2;
        }, "calculator");

        worker.start();
        worker.join();

        System.out.println("Input: " + data.input);
        System.out.println("Output: " + data.output);
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac StartJoinVisibility.java
java StartJoinVisibility
```

Kết quả:

```text
Input: 21
Output: 42
```

Diễn giải theo JMM:

1. Main ghi `data.input = 21` **trước** `worker.start()`; phép ghi này happens-before phép đọc `data.input` trong worker.
2. Worker đọc 21, tính 42 rồi ghi `data.output = 42` và kết thúc `run()`.
3. Main chờ `worker.join()` hoàn tất; hành động ghi output của worker happens-before phép đọc output ở main sau `join()`.
4. Không thread nào viết chồng lên hai trường theo cách cạnh tranh trong cửa sổ đó, nên output được bảo đảm như trên.

Có thể biểu diễn chuỗi bắc cầu:

```text
main: input = 21
       |
       | program order + start()
       v
worker: đọc input 21 -> output = 42 -> kết thúc
       |
       | termination detected via join()
       v
main: đọc output 42
```

**Ý nghĩa thực tế:** phân công cho thread khác tính kết quả rồi `join()` là một cách chuyển giao trạng thái hợp lệ. Điều này không có nghĩa mọi lần chia sẻ trường bình thường giữa các thread đều an toàn: ví dụ được thiết kế sao cho input được ghi trước start và output chỉ được main đọc sau join.

## 5. Không có quan hệ ngược từ worker về main nếu chỉ gọi start()

Xét đoạn mã (trích đoạn minh họa; `data` đã khai báo):

```java
Thread worker = new Thread(() -> data.output = 42);
worker.start();
System.out.println(data.output); // Không có kết quả cố định.
```

Phép ghi `data.output` của worker **không tự happens-before** phép đọc ở main chỉ nhờ `start()`. Main có thể đọc giá trị cũ hoặc mới, và việc chạy thử nhiều lần cho một kết quả giống nhau vẫn không chứng minh mã đúng. Nếu `data.output` là một trường `int` không đồng bộ, phép ghi/đọc đối nghịch này có thể cấu thành data race.

Cách sửa rõ ràng khi cần kết quả **sau khi hoàn thành**:

```java
worker.start();
worker.join();
System.out.println(data.output);
```

Đoạn sửa là trích đoạn minh họa cần nằm trong phương thức cho phép xử lý `InterruptedException`. Nếu phải nhận **kết quả trong khi worker vẫn đang chạy**, `join()` không đáp ứng vì nó đợi kết thúc; cần `volatile`, `Atomic*`, khóa, `BlockingQueue`, `CountDownLatch`, `Future` hoặc cấu trúc đồng bộ khác phù hợp với giao thức trao đổi dữ liệu.

**Cũng không được suy ra** gọi `first.start(); second.start();` thì toàn bộ công việc của first happens-before công việc của second. Main có thứ tự gọi start cho hai thread, nhưng không có cạnh trực tiếp giữa hành động chạy trong first và second chỉ từ hai lần gọi đó.

## 6. Ví dụ 2 — Bắc cầu khi chuyển dữ liệu giữa hai worker

Nếu main thật sự chờ thread thứ nhất kết thúc rồi mới khởi động thread thứ hai, các cạnh happens-before liên kết thành một chuỗi.

Tạo file **`ThreadHandoff.java`**:

```java
public class ThreadHandoff {
    static final class Box {
        int value;
    }

    public static void main(String[] args) throws InterruptedException {
        Box box = new Box();

        Thread producer = new Thread(() -> {
            box.value = 7;
        }, "producer");

        producer.start();
        producer.join();

        Thread consumer = new Thread(() -> {
            System.out.println("Received: " + box.value);
        }, "consumer");

        consumer.start();
        consumer.join();
        System.out.println("Done");
    }
}
```

```bash
javac ThreadHandoff.java
java ThreadHandoff
```

Kết quả:

```text
Received: 7
Done
```

Chuỗi quan hệ:

```text
producer: box.value = 7
             |
             | producer kết thúc -> main join() phát hiện
             v
main: producer.join() xong -> consumer.start()
                                   |
                                   | start() synchronizes-with consumer
                                   v
consumer: đọc box.value == 7
```

Vì happens-before có tính bắc cầu, phép ghi của producer xảy ra trước phép đọc của consumer theo JMM. Không cần một `volatile` riêng cho `box.value` trong đúng giao thức **hoàn tất producer → main join → khởi động consumer** này.

Nếu bỏ `producer.join()`, main có thể khởi động consumer trước khi producer ghi dữ liệu; consumer không được bảo đảm đọc 7. Trong ứng dụng thật, việc xếp hai thread thành từng pha như trên có thể giảm concurrency. Nếu cần chạy đồng thời và chuyển dữ liệu theo sự kiện, dùng cơ chế phối hợp như queue, latch hoặc future.

## 7. join(timeout): kết thúc chờ không có nghĩa worker đã kết thúc

`join(long millis)` chờ **tối đa một khoảng thời gian** cho worker kết thúc; nó có thể trả về trong khi worker vẫn đang chạy. Vì vậy phải **kiểm tra điều kiện hoàn thành** sau lời gọi nếu cần quyết định có thể đọc trạng thái cuối một cách an toàn hay chưa.

Ví dụ sau cố tình giữ worker ở trạng thái chờ bằng latch, nhờ đó nhánh “timeout nhưng thread vẫn sống” dễ quan sát và không phụ thuộc lịch chạy nhanh/chậm.

Tạo file **`TimedJoinDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class TimedJoinDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "waiting-worker");

        worker.start();
        worker.join(20);

        System.out.println("Alive after timed join: " + worker.isAlive());

        release.countDown();
        worker.join();

        System.out.println("Alive after full join: " + worker.isAlive());
    }
}
```

```bash
javac TimedJoinDemo.java
java TimedJoinDemo
```

Kết quả trong lần chạy bình thường:

```text
Alive after timed join: true
Alive after full join: false
```

Worker không thể thoát khỏi `release.await()` trước khi main gọi `release.countDown()`, vì vậy sau `join(20)` nó vẫn sống. Khi release được phát, worker kết thúc, `join()` không thời hạn trả về và main xác nhận thread đã xong. Latch bản thân nó cũng có quy tắc đồng bộ bộ nhớ riêng; ở ví dụ này nó chủ yếu giúp điều khiển thời điểm worker kết thúc.

**Quan trọng:** lời gọi `join(timeout)` trả về đơn thuần chưa đủ để suy ra bảo đảm *worker kết thúc → main*. Khi `worker.isAlive()` cho thấy nó thực sự đã kết thúc thì đây mới là trường hợp phát hiện termination. Với một giá trị timeout, thread có thể hoàn tất trước thời hạn hoặc vẫn chạy sau thời hạn; mã không được mặc định theo một phía.

Đừng dùng `join(0)` để đặt timeout bằng không: với overload `join(long)`, **0 nghĩa là chờ vô thời hạn**. Nếu muốn thời hạn thực tế hoặc khả năng hủy, cần thiết kế riêng đường xử lý timeout/interruption.

## 8. join() bị interrupt và xử lý vòng đời đúng cách

Chỉ thread **đang gọi `join()`** bị chặn trong thao tác chờ. Nếu thread chờ nhận interrupt, `join()` ném `InterruptedException` và việc chờ đã bị ngắt; không được tự động kết luận worker đích đã hoàn thành. Worker đích có thể vẫn đang chạy.

Trong phương thức có trách nhiệm xử lý và không ném tiếp `InterruptedException`, thường khôi phục cờ interrupt để tầng gọi nhận biết:

```java
try {
    worker.join();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Quyết định hủy công việc/chuyển trách nhiệm dọn dẹp.
}
```

Đây là trích đoạn minh họa, giả định biến `worker` đã khai báo. **Interrupt luồng đang chờ** khác **interrupt worker đích**. `worker.interrupt()` gửi tín hiệu hợp tác đến worker; nó không ép worker kết thúc ngay, không tự chuyển worker sang TERMINATED, và không thay thế `join()`.

Nếu worker có thể chờ vô hạn, phương án thực tế là có **giao thức yêu cầu dừng**, xử lý interrupt đúng chỗ và thời hạn chờ hợp lý. `Thread.stop()` không phải giải pháp: phương thức này đã lỗi thời và không an toàn.

## 9. join() không mang kết quả và ngoại lệ về cho bạn

`Thread.join()` trả về `void`, chỉ phục vụ chờ/vòng đời. Nó **không trả về giá trị từ Runnable**, cũng không tự ném lại ngoại lệ chưa được xử lý trong worker. Có thể dùng một nơi lưu kết quả/lỗi chia sẻ và chỉ đọc sau `join()`, hoặc chọn API có khả năng mang kết quả như `Future`.

Tạo file **`JoinAndFailure.java`**:

```java
import java.util.concurrent.atomic.AtomicReference;

public class JoinAndFailure {
    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            throw new IllegalStateException("bad input");
        }, "failing-worker");

        worker.setUncaughtExceptionHandler((thread, error) -> {
            failure.set(error);
        });

        worker.start();
        worker.join();

        Throwable error = failure.get();
        System.out.println("Worker terminated: " + !worker.isAlive());
        System.out.println("Failure: "
                + (error == null ? "none" : error.getMessage()));
    }
}
```

```bash
javac JoinAndFailure.java
java JoinAndFailure
```

Kết quả:

```text
Worker terminated: true
Failure: bad input
```

`UncaughtExceptionHandler` nhận lỗi khi luồng sắp kết thúc vì ngoại lệ không được bắt. Sau `join()`, main quan sát được thông tin do handler đã ghi và quyết định xử lý lỗi. `AtomicReference` ở đây làm nơi chứa kết quả có giao diện truy cập rõ ràng; trong một giao thức chỉ đọc sau `join()` thành công, việc chờ kết thúc đã cung cấp quan hệ bộ nhớ cho dữ liệu do worker ghi trước khi chấm dứt.

Cần nhớ rằng **worker kết thúc không đồng nghĩa worker thành công**. Nếu muốn API trả kết quả/ngoại lệ theo giao thức rõ hơn, `ExecutorService.submit(...)` và `Future.get()` cung cấp cách tiếp cận khác, đồng thời có thêm trách nhiệm đóng executor.

## 10. Thread.start()/join() khác volatile, synchronized và các công cụ khác

| Công cụ | Quan hệ/bảo đảm chính | Dùng khi nào? |
|---|---|---|
| `Thread.start()` | Hành động trước start của luồng khởi tạo happens-before hành động trong thread mới. | Công bố dữ liệu đã chuẩn bị trước khi worker bắt đầu. |
| `Thread.join()` | Hành động của thread đã kết thúc happens-before hành động sau khi luồng khác phát hiện kết thúc. | Đợi worker và đọc trạng thái cuối/kết quả. |
| `volatile` | Một phép ghi volatile synchronizes-with các phép đọc sau đó của cùng biến theo thứ tự đồng bộ hóa. | Cờ/tín hiệu hoặc giá trị đơn giản được trao đổi **trong khi các thread còn chạy**. |
| `synchronized` | Mở khóa monitor synchronizes-with lần khóa tiếp theo trên cùng monitor, cùng bảo vệ vùng găng. | Check-then-act hoặc bất biến liên quan nhiều bước/trường. |
| `AtomicInteger` và họ `Atomic*` | Các phép đọc/ghi/cập nhật với bảo đảm nguyên tử và hiệu ứng bộ nhớ theo API. | Bộ đếm, CAS và trạng thái nguyên tử đơn. |
| `CountDownLatch` | Hành động trước `countDown()` happens-before hành động sau `await()` thành công ở thread khác. | Điều phối giữa các thread chưa kết thúc. |
| `Future.get()` | Có hợp đồng chờ kết quả, và hành động của task happens-before hành động sau `get()` tương ứng. | Nhận kết quả hoặc lỗi từ task bất đồng bộ. |

Ví dụ `start()` và `join()` không cần dùng `volatile` cho dữ liệu chuyển giao một chiều đúng cách; nhưng **không thể bỏ mọi đồng bộ hóa** nếu hai thread cùng đọc/ghi trạng thái trong lúc chạy. Một phép `volatile` đọc/ghi cũng không tự làm nguyên tử đoạn mã check-then-act nhiều bước.

### Dễ nhầm giữa “đã xảy ra theo thời gian” và “happens-before”

JMM tạo quan hệ hình thức. Việc nhật ký cho thấy A in ra trước B chưa chắc làm các thao tác dữ liệu khác giữa hai thread có happens-before tương ứng; log dùng đồng bộ hóa nội bộ còn có thể ảnh hưởng lịch chạy. Cần chứng minh bằng các cạnh cụ thể như start, join, lock/unlock, volatile hoặc primitive đồng bộ.

### Dễ nhầm giữa thứ tự start và thứ tự hoàn thành

`a.start(); b.start();` không bảo đảm A hoàn thành trước B. Cũng không bảo đảm A in một dòng trước B chỉ vì A được start trước. Muốn B chỉ thực thi công việc sau khi A xong, hãy chờ `a.join()` trước khi bắt đầu B hoặc dùng một giao thức đồng bộ giữa hai worker.

### Dễ nhầm giữa dữ liệu trong bộ nhớ và tác dụng phụ bên ngoài

Happens-before bảo đảm các quan hệ bộ nhớ trong Java, **không** cam kết file đã được flush/bền trên đĩa, yêu cầu mạng đã được hệ thống khác xử lý hoặc giao dịch ngoài tiến trình đã commit. Muốn có những bảo đảm đó phải dùng API và giao thức riêng của tài nguyên bên ngoài.

## 11. Những câu hỏi để kiểm tra tính đúng đắn

Khi quyết định có thể trao đổi dữ liệu bằng `start()`/`join()` hay không, hãy lần lượt kiểm tra:

1. **Ai ghi input và ghi khi nào?** Nếu main ghi xong trước khi `start()`, worker có thể dựa vào cạnh start.
2. **Input có bị thread khác sửa sau khi start không?** Nếu có, cạnh start không bảo vệ những thao tác cạnh tranh về sau.
3. **Ai ghi output và main đọc khi nào?** Nếu chỉ đọc sau khi `join()` hoàn tất do worker kết thúc, có cạnh termination.
4. **Có phải đang dùng `join(timeout)`?** Nếu worker còn sống sau timeout, chưa thể coi dữ liệu cuối đã ổn định; cần kiểm tra termination hoặc dùng primitive phù hợp.
5. **Worker có tạo thêm thread/task khác không?** `join()` chỉ đợi worker được chỉ định, không tự đợi toàn bộ tác vụ được nó giao đi.
6. **Nếu worker thất bại hoặc không bao giờ kết thúc thì sao?** Có đường báo lỗi và hủy hợp tác, timeout, cleanup phù hợp chưa?
7. **Bất biến có nhiều bước hay nhiều trường không?** Nếu có, start/join có thể phù hợp cho các *pha* tách biệt; còn truy cập đồng thời trong pha cần khóa/atomic/primitive tương ứng.

Lưu ý các quy tắc happens-before này áp dụng cho quan hệ thread Java; với **virtual threads** được giới thiệu chính thức trong Java 21, bản chất quy tắc start/join vẫn có ý nghĩa. Ví dụ bài học sử dụng API Java 17 phổ biến để có thể biên dịch và chạy trực tiếp.

## 12. Tự kiểm tra

1. **`start()` bảo đảm hướng dữ liệu nào?** Các hành động đã xảy ra trước start trên luồng khởi tạo happens-before hành động của thread được start. Không có bảo đảm ngược lại từ worker về main chỉ bằng start.
2. **Vì sao `data.output` không cần `volatile` ở Ví dụ 1?** Vì main chỉ đọc sau `join()` hoàn tất, tạo quan hệ happens-before từ phép ghi của worker tới phép đọc của main; các truy cập không bị tranh chấp ngoài giao thức.
3. **`a.start(); b.start();` có làm hành động của a happens-before b không?** Không, trừ khi có thêm một cạnh đồng bộ nối hai thread.
4. **`join(20)` trả về có chắc worker đã kết thúc không?** Không; phải xác minh worker thực sự đã kết thúc hoặc dùng cách chờ hoàn thành khác.
5. **`join()` có tự truyền ngoại lệ worker sang main không?** Không; cần nơi lưu lỗi, uncaught exception handler, Future hoặc giao thức kết quả riêng.
6. **Nếu main bị interrupt lúc chờ join thì điều gì xảy ra?** Lời gọi có thể ném `InterruptedException`; không được tự coi worker đã hoàn thành.
7. **Vì sao hai thread đồng thời ghi/đọc một biến sau `start()` vẫn có thể race?** Vì cạnh start chỉ bao trùm hành động trước start đến thread mới; không tự sắp thứ tự các truy cập xung đột về sau.
8. **Nếu producer hoàn thành, main join producer rồi start consumer, điều gì bảo đảm consumer nhìn thấy kết quả?** Cạnh termination → join, thứ tự chương trình trên main và cạnh start → consumer ghép thành quan hệ happens-before bắc cầu.

**Ghi nhớ:** `Thread.start()` là cạnh **công bố dữ liệu vào worker**; `Thread.join()` khi thực sự quan sát kết thúc là cạnh **nhận kết quả từ worker**. Ghép đúng hai cạnh với *program order* và tính bắc cầu tạo một giao thức chuyển giao dữ liệu đơn giản, rõ ràng theo Java Memory Model. Với các truy cập đồng thời trong lúc worker còn chạy, vẫn cần cơ chế đồng bộ hóa thích hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-007">Safe publication và nguy cơ đọc đối tượng khởi tạo dở</a>

<details>
<summary>Xem chi tiết</summary>

# Safe publication và nguy cơ đọc đối tượng khởi tạo dở trong Java

> **Trọng tâm:** Tạo được một object đúng trong một thread **chưa đủ** để thread khác quan sát object đó đúng. Cần **safe publication**: đưa tham chiếu đến object cho thread khác qua một cơ chế đồng bộ có hợp đồng *happens-before*. Nếu tham chiếu được chia sẻ quá sớm, thread khác còn có thể nhìn thấy một object **đang khởi tạo dở**. `final` giúp bảo vệ trạng thái bất biến khi object được dựng đúng cách, nhưng không sửa việc `this` thoát khỏi constructor quá sớm.

Bài thuộc nhóm **Java Memory Model** của Java concurrency nói chung, dùng các cơ chế Java SE (`volatile`, `synchronized`, class initialization, `java.util.concurrent`). Không bàn tới vòng đời bean, dependency injection, proxy hoặc cơ chế concurrency riêng của Spring.

## 1. Ba công việc cần phân biệt

Giả sử ta muốn một thread tạo cấu hình và các thread khác sử dụng nó:

~~~text
Thread A (producer)                     Thread B (consumer)
-------------------                     -------------------
1. Cấp phát object
2. Khởi tạo các field
3. Công bố tham chiếu ----------------> 4. Đọc tham chiếu
                                        5. Đọc các field
~~~

Một thiết kế đúng cần trả lời ba câu hỏi:

1. **Construction — object đã khởi tạo xong chưa?** Mọi invariant (trạng thái phải nhất quán) của object đã được thiết lập chưa? Constructor hoặc factory đã hoàn thành chưa?
2. **Publication — consumer nhận tham chiếu bằng cách nào?** Có quan hệ đồng bộ bảo đảm các ghi trong quá trình khởi tạo được quan sát trước các lần đọc của consumer không?
3. **Subsequent mutation — object có bị sửa sau khi công bố không?** Nếu có, các sửa đổi về sau phải được bảo vệ riêng; safe publication của một snapshot ban đầu không biến mọi hoạt động tương lai thành thread-safe.

**Đừng đánh đồng object tồn tại với object đã được công bố an toàn.** Tham chiếu `ref != null` không mặc nhiên chứng minh các field đã được consumer nhìn thấy theo đúng trạng thái cuối của constructor.

### Khởi tạo dở có nghĩa gì?

Java cấp giá trị mặc định cho field của object trước khi các bước khởi tạo chạy: `int` mặc định `0`, `boolean` là `false`, tham chiếu là `null`. Constructor có thể thay đổi các field sang giá trị hợp lệ, ví dụ `port = 8080` và `name = "primary"`.

Nếu thread B lấy được tham chiếu khi constructor chưa hoàn thành, B có thể đọc một số field ở trạng thái mặc định hoặc thấy invariant chưa thiết lập. Nếu object đã dựng xong nhưng được trao tham chiếu qua một **data race**, B cũng có thể không được JMM bảo đảm quan sát các ghi tới field thông thường theo cách A dự định. Hai nguyên nhân cần phân biệt:

- **Early escape:** object được công bố **trước khi hoàn tất construction**; việc đọc dở có thể xảy ra thực sự vì field chưa được ghi.
- **Unsafe publication:** object có thể đã được xây xong trong A, nhưng không có quan hệ *happens-before* thích hợp giữa các ghi xây dựng object và những lần đọc trong B.

## 2. Java Memory Model và `happens-before`

Java Memory Model (JMM) quy định những giá trị một thread được phép quan sát và các giới hạn của việc tái sắp xếp đọc/ghi. Nó không buộc mọi thread luôn nhìn thấy ngay lập tức các ghi thông thường của nhau khi chương trình thiếu đồng bộ.

Quan hệ **happens-before** (HB) là công cụ suy luận chính. Nếu ghi dữ liệu của A *happens-before* lần đọc tương ứng của B, quan hệ này cung cấp ràng buộc visibility và ordering cần thiết, với điều kiện không có ghi cạnh tranh khác làm thay đổi kết quả. HB **không phải chỉ là** “A xảy ra trước B theo đồng hồ”.

Một số nguồn HB phổ biến:

| Cơ chế | Quan hệ quan trọng |
| --- | --- |
| **Program order** | Hành động trước trong cùng thread HB hành động sau của thread đó. |
| **`volatile`** | Ghi vào biến volatile HB lần đọc volatile tiếp theo của chính biến đó theo thứ tự đồng bộ. |
| **Monitor (`synchronized`)** | Unlock trên một monitor HB lần lock tiếp theo trên cùng monitor. |
| **`Thread.start()`** | Các hành động trước `start()` HB những hành động của thread được start. |
| **`Thread.join()` thành công** | Hành động của thread đã kết thúc HB những hành động phía sau `join()` trong thread đợi. |
| **Class initialization** | JVM điều phối việc khởi tạo class một lần theo các quy tắc khởi tạo class; static state được công bố phù hợp qua cơ chế đó. |
| **Concurrent utilities** | Các API như `BlockingQueue`, `Future.get()`, executor có các bảo đảm memory consistency cụ thể theo tài liệu của chúng. |

HB có tính **bắc cầu**: nếu `A HB B` và `B HB C` thì `A HB C`. Vì thế khi producer ghi các field, sau đó thực hiện thao tác release và consumer thực hiện thao tác acquire tương ứng, ta có thể suy ra việc quan sát các ghi đã xảy ra trước release.

Ví dụ đơn giản:

~~~text
Thread A:  config = new Config(...);   // Khởi tạo field
           READY = config;             // Ghi volatile (release)

Thread B:  local = READY;              // Đọc volatile thấy tham chiếu (acquire)
           local.port();               // Đọc dữ liệu đã khởi tạo
~~~

Nếu `READY` là field `volatile` và B thấy tham chiếu A đã ghi, các hành động chuẩn bị object trước volatile write sẽ được công bố thích hợp cho B. B phải đọc qua cơ chế đó; không được bypass bằng đường tham chiếu khác chưa được đồng bộ.

### Safe publication khác atomicity

Safe publication bảo đảm **trạng thái lúc công bố** được nhìn thấy đúng theo quan hệ đồng bộ. Nó **không có nghĩa** mọi thay đổi về sau của các field đều atomic. Nếu các thread cùng tăng một biến đếm mutable bằng `count++`, vẫn cần `synchronized`, atomic RMW hoặc cơ chế phù hợp. **`volatile` của biến chứa tham chiếu cũng không làm các field bên trong object tự động volatile.**

## 3. Sai lầm 1 — công bố qua field thông thường

Xem đoạn minh họa sau (cố tình không đồng bộ, không phải chương trình độc lập):

~~~java
final class MutableConfig {
    int port;
    String host;

    MutableConfig(int port, String host) {
        this.port = port;
        this.host = host;
    }
}

class Registry {
    static MutableConfig shared; // Không volatile; không có lock.

    static void publish() {
        shared = new MutableConfig(8080, "primary");
    }

    static void consume() {
        MutableConfig local = shared;
        if (local != null) {
            System.out.println(local.port + " / " + local.host);
        }
    }
}
~~~

Nếu `publish()` và `consume()` chạy trên các thread khác nhau mà **không** có một cơ chế HB khác, việc ghi/đọc `shared` tạo data race. JMM không cung cấp bảo đảm rằng consumer sẽ quan sát object đúng như producer vừa xây, hoặc thậm chí luôn nhìn thấy tham chiếu mới trong một vòng polling không đồng bộ.

Không nên ép một stdout cụ thể cho ví dụ sai này: trên máy quen thuộc nó có thể in `8080 / primary` hàng triệu lần và **vẫn sai về mặt hợp đồng**. Trong một execution không đồng bộ, việc thread đọc default/stale field hoặc không nhận được cập nhật có thể xảy ra tùy JMM và tối ưu hóa, nhưng ta không được coi “thường chạy đúng” là proof.

Điểm đáng lưu ý: producer viết `new MutableConfig(...)` **trước** câu gán `shared` trong source không tự tạo HB với consumer. Program order chỉ tạo quan hệ nội bộ trong producer; để ràng buộc consumer cần một synchronization edge liên thread.

## 4. Sai lầm 2 — `this` thoát khỏi constructor quá sớm

Một object không nên được thread khác truy cập khi các invariant còn đang được dựng. Các ví dụ gây **constructor escape**:

- Gán `this` vào một biến static/global mà thread khác đọc.
- Gọi callback hoặc đăng ký listener để có thể được gọi ngay và truyền `this` ra ngoài.
- Gửi lambda capture `this` vào executor trước khi constructor xong.
- Khởi động thread trong constructor với `new Thread(() -> use(this)).start()` rồi tiếp tục thiết lập field.
- Gọi phương thức có thể bị override trong constructor; mã override ở subclass có thể thấy subclass chưa khởi tạo xong.

Đặc biệt, `Thread.start()` chỉ bảo đảm các ghi **trước lời gọi start** HB hành động của thread mới; các field được gán **sau start** không tự có bảo đảm tương tự.

### Ví dụ 1 — nhìn thấy object chưa dựng xong một cách xác định

Để thấy đây là vấn đề thứ tự khởi tạo thật sự, chương trình dưới **chủ động cho phép consumer đọc giữa constructor**. Tạo `EscapingThisDemo.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;

public class EscapingThisDemo {
    private static volatile Leaky shared;
    private static final CountDownLatch exposed = new CountDownLatch(1);
    private static final CountDownLatch resume = new CountDownLatch(1);

    static final class Leaky {
        int answer; // Ban đầu là 0.

        Leaky() throws InterruptedException {
            shared = this;      // Sai: công bố trước khi hoàn thành.
            exposed.countDown();
            resume.await();     // Cố ý giữ constructor ở giữa chừng.
            answer = 42;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread maker = new Thread(() -> {
            try {
                new Leaky();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        maker.start();
        exposed.await();
        System.out.println("while constructing = " + shared.answer);

        resume.countDown();
        maker.join();
        System.out.println("after construction = " + shared.answer);
    }
}
~~~

Chạy:

~~~bash
javac EscapingThisDemo.java
java EscapingThisDemo
~~~

Kết quả trong trường hợp không có interruption bên ngoài:

~~~text
while constructing = 0
after construction = 42
~~~

Vì `shared = this` xảy ra **trước** `answer = 42` và latch giữ constructor tại điểm đó, consumer chắc chắn đọc `answer` lúc field vẫn đang ở default `0`. Sau `resume.countDown()`, constructor tiếp tục. `maker.join()` bảo đảm main đợi maker hoàn tất trước khi in `42`.

Ví dụ này **không cần** dựa vào một hiệu ứng reordering mơ hồ: nó cho thấy một object có thể thực sự bị công bố trước khi sẵn sàng. Quan trọng hơn, `shared` ở đây **đã là volatile**: dùng volatile không thể “đẩy” những phép ghi ở tương lai trong constructor quay ngược về trước thời điểm publish. Safe publication phải xảy ra **sau khi** object hoàn tất khởi tạo.

### Sửa cách tổ chức

Không đăng ký callback, khởi động worker hoặc gắn `this` vào registry từ trong constructor khi consumer có thể chạy ngay. Hãy:

1. Xây dựng object trong constructor **không cho tham chiếu thoát**.
2. Kiểm tra invariant và hoàn thành construction.
3. Công bố tham chiếu qua một cơ chế HB; hoặc gọi một phương thức `start()`/`register()` **sau khi** factory đã trả về object hoàn chỉnh.

Nếu một callback có thể chạy đồng thời, chính callback cũng cần dùng giao thức đồng bộ thích hợp cho trạng thái mà nó truy cập.

## 5. Cách 1 — public snapshot bất biến qua `volatile`

Đối với cấu hình được thay thế nguyên khối, cách đơn giản là tạo một object **bất biến** hoàn chỉnh rồi gán tham chiếu của nó vào một field `volatile`.

Tạo `VolatilePublication.java` (Java 17+):

~~~java
public class VolatilePublication {
    record Config(String host, int retries) {}

    private static volatile Config current;

    public static void main(String[] args) throws InterruptedException {
        Thread consumer = new Thread(() -> {
            Config snapshot;
            while ((snapshot = current) == null) {
                Thread.onSpinWait(); // Chỉ dành cho demo ngắn.
            }
            System.out.println(snapshot.host() + " / " + snapshot.retries());
        });

        consumer.start();                // Consumer đã được khởi động.
        Config built = new Config("primary", 3);
        current = built;                 // Publish sau construction.
        consumer.join();
    }
}
~~~

Chạy:

~~~bash
javac VolatilePublication.java
java VolatilePublication
~~~

Đầu ra:

~~~text
primary / 3
~~~

Điều cốt lõi không phải bản thân `record`, mà là chuỗi:

~~~text
Producer: khởi tạo Config -> ghi volatile current
                                 |
                                 | synchronizes-with
                                 v
Consumer:                 đọc volatile current -> đọc field Config
~~~

Consumer chỉ đọc những field của **snapshot nó vừa lấy vào biến local**, không đọc tham chiếu từ registry nhiều lần rồi trộn field của các phiên bản khác nhau. `record` tạo các component final; ở đây `String` và `int` không chứa trạng thái mutable có thể bị sửa sau khi công bố.

Vòng `Thread.onSpinWait()` là **busy-wait chỉ để dạy ngữ nghĩa volatile**, không phải khuyến nghị production. Với công việc thật, dùng `BlockingQueue`, `CountDownLatch`, `Future` hoặc cơ chế notification để chờ hiệu quả.

### Thay thế snapshot an toàn, nhưng cập nhật hợp thành chưa chắc an toàn

~~~java
// Minh họa: mỗi lần cập nhật được publish nguyên một snapshot.
current = new Config("backup", 5);
~~~

Mỗi consumer có thể lấy một trong các phiên bản đã được công bố; snapshot đó nhất quán về hai trường của nó. Tuy nhiên, nếu **nhiều producer** đồng thời đọc `current`, tính bản mới, rồi ghi lại, chuỗi read-modify-write vẫn có thể lost update. Khi yêu cầu điều phối cập nhật, cần lock hoặc `AtomicReference.updateAndGet()`/CAS và hàm cập nhật thuần.

Ngoài ra, một field `volatile` chỉ bảo đảm publication qua chính field ấy; nếu publish object rồi **tiếp tục sửa các field ordinary** bên trong mà không đồng bộ, các thay đổi về sau có thể tạo data race.

## 6. Cách 2 — `synchronized` và cùng một monitor

`synchronized` có hai vai trò liên quan: **mutual exclusion** (loại trừ đồng thời trong critical section) và **visibility/ordering** nhờ unlock/lock trên cùng monitor.

Tạo `MonitorPublication.java` (Java 17+):

~~~java
public class MonitorPublication {
    static final class Config {
        final String host;
        final int retries;

        Config(String host, int retries) {
            this.host = host;
            this.retries = retries;
        }
    }

    private static final Object LOCK = new Object();
    private static Config shared; // Không cần volatile khi luôn dùng LOCK.

    private static void publish(Config config) {
        synchronized (LOCK) {
            shared = config;
            LOCK.notifyAll();
        }
    }

    private static Config awaitConfig() throws InterruptedException {
        synchronized (LOCK) {
            while (shared == null) {
                LOCK.wait();
            }
            return shared;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread consumer = new Thread(() -> {
            try {
                Config snapshot = awaitConfig();
                System.out.println(snapshot.host + " / " + snapshot.retries);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        publish(new Config("primary", 3));
        consumer.join();
    }
}
~~~

Chạy:

~~~bash
javac MonitorPublication.java
java MonitorPublication
~~~

Kết quả bình thường:

~~~text
primary / 3
~~~

`publish()` dựng `Config` **trước khi** đặt tham chiếu vào `shared` dưới monitor `LOCK`. `awaitConfig()` đọc `shared` dưới **chính monitor đó**. Unlock của producer HB lần lock tiếp theo ở consumer. `notifyAll()` giúp đánh thức thread đang đợi; việc kiểm tra điều kiện trong vòng `while` là cần thiết vì Java cho phép *spurious wakeup*.

**Lỗi hay gặp:** producer dùng `synchronized(lockA)` nhưng consumer đọc `shared` bên ngoài lock hoặc dùng `lockB`. Không có bảo đảm HB từ `lockA` sang `lockB` chỉ vì chúng đều là lock.

Nếu reader phải trả về snapshot để xử lý lâu dài, nên để trạng thái snapshot bất biến. Nếu object mutable được sửa sau khi lấy ra khỏi lock, công việc vẫn cần bảo vệ riêng.

## 7. Cách 3 — chuyển quyền sở hữu qua `BlockingQueue`

Một queue thread-safe vừa chuyên chở tham chiếu, vừa là giao thức giao việc có quan hệ memory consistency. Các hành động trước khi producer đặt phần tử vào `BlockingQueue` HB những hành động sau khi consumer lấy phần tử đó ra theo API.

Tạo `QueuePublication.java` (Java 17+):

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class QueuePublication {
    record Message(String text, int code) {}

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Message> queue = new ArrayBlockingQueue<>(1);

        Thread consumer = new Thread(() -> {
            try {
                Message message = queue.take();
                System.out.println(message.text() + " / " + message.code());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        Message built = new Message("ready", 200);
        queue.put(built);
        consumer.join();
    }
}
~~~

Chạy:

~~~bash
javac QueuePublication.java
java QueuePublication
~~~

Kết quả:

~~~text
ready / 200
~~~

Ưu điểm: consumer có thể **block** chờ dữ liệu, không cần vòng polling volatile; queue còn mô hình hóa thứ tự và backpressure theo capacity. Cần nắm quy tắc ownership: nếu producer `queue.put(mutableObject)` rồi tiếp tục mutate object trong khi consumer đọc, queue chỉ chuyển giao an toàn **những hành động trước put**; các sửa đổi đồng thời sau đó cần bảo vệ riêng.

Giao tiếp bằng snapshot bất biến hoặc ownership transfer rõ ràng thường làm bài toán ít shared mutable state hơn.

## 8. Cách 4 — Java class initialization và initialization-on-demand holder

JVM có quy tắc đồng bộ hóa quá trình khởi tạo class. Khi một static field được gán trong quá trình class initialization, mã khác sử dụng class đã khởi tạo đúng theo hợp đồng sẽ quan sát trạng thái static đã hoàn tất.

Ví dụ lazy singleton bằng *initialization-on-demand holder*. Tạo `LazySingleton.java`:

~~~java
public class LazySingleton {
    static final class Service {
        final int answer;

        Service(int answer) {
            this.answer = answer;
        }
    }

    private static final class Holder {
        static final Service INSTANCE = new Service(42);
    }

    static Service getService() {
        return Holder.INSTANCE;
    }

    public static void main(String[] args) {
        Service first = getService();
        Service second = getService();

        System.out.println("same instance = " + (first == second));
        System.out.println("answer = " + first.answer);
    }
}
~~~

Chạy:

~~~bash
javac LazySingleton.java
java LazySingleton
~~~

Đầu ra:

~~~text
same instance = true
answer = 42
~~~

`Holder` được khởi tạo khi cần dùng static field của nó; JVM bảo đảm khởi tạo class một lần đúng quy tắc concurrency. Các thread gọi `getService()` nhận cùng instance đã được constructor hoàn tất, miễn constructor không để `this` thoát sớm. Cách này tránh phải tự viết double-checked locking cho một singleton khởi tạo lười đơn giản.

### Static field `final` khác “object hoàn toàn bất biến”

~~~java
static final java.util.List<String> NAMES =
        new java.util.ArrayList<>();
~~~

`final` ở đây **chỉ giữ cố định tham chiếu** tới list; bản thân list vẫn mutable và không thread-safe. Nếu nhiều thread đồng thời thêm/xóa phần tử cần thiết kế synchronization/ownership hoặc chọn cấu trúc dữ liệu phù hợp. Với cấu hình read-only, ưu tiên immutable snapshots như `List.copyOf(source)`. Lưu ý immutable collection không tự làm các **phần tử bên trong** bất biến nếu phần tử chứa trạng thái mutable.

## 9. `final` field semantics: mạnh, nhưng có điều kiện

JMM dành các quy tắc đặc biệt cho `final` fields. Khi object được **constructed correctly** — tức tham chiếu `this` không thoát trong quá trình construction — và constructor kết thúc, cơ chế *freeze* trên final fields giúp thread khác quan sát giá trị final đã gán trong constructor, ngay cả trong một số tình huống tham chiếu được truyền bằng data race.

Ví dụ:

~~~java
// Ví dụ dạng type declaration, không phải chương trình độc lập.
final class Point {
    final int x;
    final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
~~~

Khi một `Point` được xây dựng đúng cách, final-field semantics cung cấp bảo đảm đặc biệt cho `x` và `y`. Tuy nhiên **không nên diễn dịch** thành “cứ dùng final là mọi publication đều an toàn”. Phân biệt:

- `final` trên một field được gán ở constructor và **không có constructor escape** có bảo đảm riêng về giá trị được khởi tạo.
- Field thông thường (non-final) không tự nhận được bảo đảm final-field semantics; cho object có dữ liệu ordinary mutable, hãy publish qua HB.
- `final SomeObject reference` chỉ ngăn **gán lại tham chiếu** sau construction; nếu object được tham chiếu có thể bị sửa, các sửa đổi sau này vẫn cần synchronization.
- Quy tắc cho object mà reference được truy cập qua final field có những bảo đảm visibility đối với trạng thái trước freeze, nhưng **không phải phép bảo đảm mọi mutation về sau là an toàn**.
- Nếu `this` thoát sớm, thread khác có thể đọc field **trước khi gán** hoặc trước lúc constructor kết thúc; không có quyền dựa vào freeze để sửa hành vi đó.
- Dùng reflection, unsafe mechanisms hoặc serialization đặc biệt để sửa final fields về sau có ngữ nghĩa phức tạp; đừng dựa vào pattern như vậy cho state chia sẻ.

**Thực hành an toàn:** thiết kế object bất biến thật sự và publish qua một cơ chế HB rõ ràng. Final fields giúp tính đúng đắn của dữ liệu bất biến, còn cơ chế publication giúp kiểm soát luồng giao nhận và mở rộng dễ hiểu khi kiểu dữ liệu thay đổi.

### Record có làm mọi object thread-safe?

`record` thường thuận tiện để tạo một immutable snapshot về mặt component references:

~~~java
record Settings(String endpoint, int timeoutSeconds) {}
~~~

Các component fields là final. Nhưng một record có component là `List<MutableItem>` vẫn có thể mang mutable objects nếu không defensive copy và không bảo vệ các item. Các constructor cũng phải tránh cho `this` thoát sớm, và các method không nên phơi nội bộ mutable state một cách không kiểm soát.

## 10. Safe publication bằng `Thread.start()` và `Thread.join()`

Không phải lúc nào cũng cần một field volatile mới. Khi **producer xây object rồi mới gọi `start()`** cho consumer, `start()` chính nó là một synchronization event:

~~~java
// StartPublication.java — Java 17+
public class StartPublication {
    static final class Data {
        int value;

        Data(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Data built = new Data(42); // Hoàn tất trước start.

        Thread consumer = new Thread(
                () -> System.out.println("value = " + built.value));
        consumer.start();
        consumer.join();
    }
}
~~~

Chạy `javac StartPublication.java` và `java StartPublication`:

~~~text
value = 42
~~~

Các hành động xây dựng object ở main trước `consumer.start()` HB các hành động của consumer. Không cần `volatile` ở `built` cho tình huống một chiều này.

`Thread.join()` tạo hướng **ngược lại**: nếu main đợi consumer kết thúc, các hành động consumer trước khi kết thúc HB hành động main sau `join()`. Ví dụ một worker ghi kết quả vào ordinary field và main chỉ đọc **sau join thành công** có cơ sở visibility. Nhưng join chỉ giúp **thread đã đợi**; không có nghĩa một thread thứ ba đang đọc shared state sẽ tự nhiên được đồng bộ.

**Chú ý:** khởi động thread **trong constructor** rồi tiếp tục gán các field của `this` có thứ tự đảo ngược; những ghi sau `start()` không được bao phủ tự động bởi HB của `start()`. Đây là một nguyên nhân constructor escape phổ biến.

## 11. Future, executor và concurrent collections

Các tiện ích chuẩn Java thường có quy tắc memory consistency đã được API tài liệu hóa. Ví dụ với `ExecutorService`, hành động của thread gửi task **trước khi submit** HB hành động của task; hành động trong task HB hành động sau khi thread khác lấy kết quả bằng `Future.get()`. Khi dùng đúng, cặp `submit/get` là một kênh an toàn để truyền state và lấy kết quả.

Tạo `FuturePublication.java` (Java 17+):

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FuturePublication {
    static final class Result {
        final int sum;
        Result(int sum) {
            this.sum = sum;
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Result> future = executor.submit(
                    () -> new Result(20 + 22));

            Result result = future.get();
            System.out.println("sum = " + result.sum);
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac FuturePublication.java
java FuturePublication
~~~

Đầu ra:

~~~text
sum = 42
~~~

`Future.get()` đợi computation hoàn thành và đem kết quả qua một quan hệ HB được API quy định. Nếu task bị lỗi, `get()` có thể ném `ExecutionException`; nếu bị hủy, có thể ném `CancellationException`. An toàn publication không thay thế quy tắc xử lý lỗi/hủy.

Tương tự, `ConcurrentHashMap`, các blocking queue và những synchronization primitives khác có các cam kết riêng cho thao tác được hỗ trợ. **Không suy ra từ tên “concurrent” rằng một chuỗi get → compute → put nhiều bước hoặc một transaction nhiều key tự động atomic.** Safe publication và compound-action atomicity là hai yêu cầu tách biệt.

## 12. Double-checked locking và vai trò của `volatile`

Một lỗi cổ điển của lazy singleton là dùng **double-checked locking (DCL)** nhưng quên `volatile` ở reference:

~~~java
// Minh họa lỗi: instance không volatile.
private static Service instance;

static Service get() {
    if (instance == null) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new Service();
            }
        }
    }
    return instance;
}
~~~

Lần kiểm tra đầu tiên ở ngoài lock là lần đọc không đồng bộ với writer trong lock. Vì thiếu HB thích hợp, thiết kế không an toàn: thread khác có thể thấy tham chiếu mà không có bảo đảm về trạng thái object cần quan sát. Câu chuyện lịch sử hay diễn giải thành “có thể thấy đối tượng chưa được khởi tạo đầy đủ”; thực tế cần phân tích theo hợp đồng visibility và các field cụ thể, tránh khẳng định mọi JVM chắc chắn sẽ tái hiện hiện tượng.

DCL đúng cho Java 5+ cần field `volatile`, constructor không escape và cùng monitor cho phép khởi tạo:

~~~java
// Dạng phương thức minh họa, các field nằm trong class chứa singleton.
private static volatile Service instance;
private static final Object LOCK = new Object();

static Service get() {
    Service local = instance;
    if (local == null) {
        synchronized (LOCK) {
            local = instance;
            if (local == null) {
                local = new Service();
                instance = local; // Volatile write sau construction.
            }
        }
    }
    return local;
}
~~~

`volatile` bảo đảm lần đọc nhanh ở ngoài lock vẫn tham gia cơ chế publication. Dùng biến local giảm số lần đọc volatile ở đường fast path. Nếu use case chỉ là static singleton đơn giản, **holder idiom** ở phần 8 thường ít phức tạp hơn.

DCL không giải quyết trường hợp constructor của `Service` tự đăng ký `this` vào registry khác, hoặc không chịu trách nhiệm đảm bảo thread-safe cho các method mutable của `Service` về sau.

## 13. Vì sao “volatile reference” chưa đủ nếu object còn mutable?

Giả sử `volatile Config shared` được publish đúng sau construction, rồi thread A thay đổi một field ordinary:

~~~java
// Minh họa lỗi logic, không phải chương trình độc lập.
Config snapshot = new Config();
snapshot.port = 8080;
shared = snapshot;  // Publish an toàn trạng thái đã xây tới đây.

// Một lúc khác, thread A:
snapshot.port = 9090; // Mutation ordinary không đồng bộ.

// Trong khi đó, thread B:
int observed = shared.port;
~~~

Lần gán `shared` ban đầu có thể publish an toàn giá trị `8080`. Nhưng lần ghi `snapshot.port = 9090` ở **sau publication** không tự trở thành thread-safe chỉ vì field giữ reference là volatile. Nếu B đọc dữ liệu đồng thời với A sửa mà không có HB thích hợp, còn data race. B cũng có thể đọc state cũ hoặc state thay đổi từng phần khi có nhiều field.

Ba cách thiết kế thường tốt hơn:

1. **Immutable snapshots:** xây `new Config(...)` đầy đủ và gán reference volatile mới; consumer làm việc với snapshot local.
2. **Shared lock:** các thao tác đọc/ghi trạng thái mutable cùng khóa bảo vệ toàn bộ invariant.
3. **Atomic whole-state replacement:** `AtomicReference<State>` giữ immutable state; muốn tính state mới từ state cũ dưới cạnh tranh thì dùng CAS/`updateAndGet()`. Hàm update nên thuần vì có thể chạy lại khi retry.

Nếu cần giữ một collection mutable lâu dài, chọn cấu trúc concurrent đúng ngữ nghĩa và tránh rò rỉ tham chiếu tới trạng thái nội bộ chưa được bảo vệ.

## 14. Chọn chiến lược publication theo nhu cầu

| Nhu cầu | Cơ chế nên dùng | Lưu ý |
| --- | --- | --- |
| Một producer cập nhật snapshot cấu hình, nhiều reader lấy bản hiện tại | `volatile` reference tới immutable object | Publish **sau** construction; mutation sau publish phải tránh hoặc bảo vệ |
| Đọc/ghi nhiều field mutable như một invariant | `synchronized` hoặc `Lock` chung | Cả writer và reader phải tham gia cùng protocol |
| Chuyển thông điệp/công việc giữa producer–consumer | `BlockingQueue` | Trạng thái của object sau `put` cần quy tắc ownership rõ |
| Singleton static khởi tạo lười | Initialization-on-demand holder | Constructor không escape; state bên trong phải có policy concurrency riêng |
| Worker tạo dữ liệu và main cần kết quả | `Future.get()`, `Thread.join()` trong trường hợp phù hợp | Chờ đúng đối tượng và xử lý failure/interruption |
| Thay nguyên trạng thái nhiều trường dưới cạnh tranh | `AtomicReference<ImmutableState>` + CAS | Atomicity áp dụng cho **một reference**, không cho side effect bên ngoài |
| Tham chiếu được đưa tới thread **sau** khi khởi tạo trong cùng thread | `Thread.start()` | Những ghi **sau start** không tự nằm trong quan hệ HB trước start |
| Bất kỳ object nào cần chia sẻ an toàn | Không để `this` escape từ constructor | Không gọi code ngoài kiểm soát có thể publish `this` quá sớm |

Không dùng việc thử chạy thấy đúng, `Thread.sleep`, thứ tự timestamp hay giả định “CPU tự đồng bộ cache” làm tiêu chí chứng minh safe publication.

## 15. Quy trình rà soát một lỗi publication

**Bước 1 — Tìm nơi tạo object.** Constructor có khởi tạo đầy đủ invariant không? Có gọi method overridable, callback, listener registration hay khởi động thread trong constructor không?

**Bước 2 — Tìm đường tham chiếu đi đến consumer.** Qua static field, instance field, global registry, queue, future, local variable capture hay một callback? Tham chiếu có bị chia sẻ trước khi constructor kết thúc không?

**Bước 3 — Vẽ cạnh HB.** Trước đường consumer đọc có volatile write/read, unlock/lock cùng monitor, `start`, `join`, `Future.get`, queue put/take, static class init hoặc một protocol khác đã được tài liệu hóa không? Nếu không chỉ ra được cạnh, đừng kết luận visibility là an toàn.

**Bước 4 — Phân biệt field.** Trường nào là `final` đúng cách, trường nào ordinary mutable, trường nào volatile? Final-field semantics có điều kiện gì? Có nested mutable object cần bảo vệ không?

**Bước 5 — Tìm mutation sau publication.** Consumer đang dùng snapshot cố định hay đọc reference lại nhiều lần? Có nhiều producer làm RMW và làm mất cập nhật không? Object có bị sửa không đồng bộ sau khi consumer giữ tham chiếu không?

**Bước 6 — Chọn sửa ở đúng ranh giới.** Chuyển publish ra sau constructor, dùng immutable snapshot và volatile, hoặc đồng bộ hóa cả producer/consumer, hoặc đưa dữ liệu qua concurrency API có HB. Chỉ thêm `volatile` vào reference không sửa vấn đề publish quá sớm.

**Bước 7 — QA đúng tính chất.** Ví dụ lỗi racy có thể chạy “đúng” nhiều lần; không xem đó là test chứng minh. Dùng một ví dụ có latch điều khiển early escape để cho thấy thứ tự khởi tạo, và dùng lập luận HB/kiểm tra code cho tính đúng của sửa lỗi. Stress test có thể giúp phát hiện lỗi, nhưng không thay thế lập luận về JMM.

### Những lỗi thường gặp

- **“Reference khác `null` thì object chắc chắn dựng xong.”** Không: `this` có thể bị đưa ra ngoài khi constructor chưa xong.
- **“Producer đã kết thúc constructor nên mọi reader luôn thấy toàn bộ field.”** Không đủ với ordinary fields khi thiếu safe publication; `final` có bảo đảm đặc biệt chỉ khi được dựng đúng cách.
- **“Dùng `volatile` cho reference thì các field bên trong mãi mãi thread-safe.”** Không: mutation sau publish cần cơ chế riêng.
- **“`final` nghĩa là mọi object được tham chiếu đều bất biến sâu.”** Không: final khóa tham chiếu, không đóng băng object mutable được tham chiếu.
- **“Chạy `new Thread(...).start()` trong constructor luôn an toàn.”** Không: child có thể quan sát `this` trước những field được gán sau start.
- **“Producer và consumer đều có synchronized, khóa nào cũng được.”** Không: cần cùng monitor/protocol để có cạnh HB liên quan.
- **“`Future.isDone()` thôi đã cho mọi bảo đảm.”** Đối với kết quả cụ thể, dùng `Future.get()`/quy tắc completion được tài liệu hóa và xử lý lỗi; không suy luận tùy ý về mọi state ngoài phép tính.
- **“Đã publish an toàn thì có thể làm compound action không khóa.”** Không: safe publication khác atomic read-modify-write hoặc giao dịch nhiều field.

## 16. Tự kiểm tra

**Câu 1.** Thread A khởi tạo một `Config` hoàn chỉnh rồi gán vào field ordinary; thread B đọc field ordinary không khóa. A đã chạy trước theo đồng hồ. Có chắc B thấy đúng mọi ordinary field không?  
**Đáp án:** Không có bảo đảm từ **thời gian thực** đơn thuần. Cần edge HB hoặc cơ chế phù hợp; final fields dựng đúng có quy tắc riêng.

**Câu 2.** Object gán vào một static `volatile` reference ở đầu constructor, rồi mới đặt các field. Consumer có thể đọc default value không?  
**Đáp án:** Có. Volatile publish **những ghi xảy ra trước nó**, không bảo đảm các field sẽ được ghi sau này trước khi reader chạy.

**Câu 3.** Producer tạo snapshot immutable xong rồi volatile-write; consumer volatile-read được tham chiếu đó. Có cần lock thêm chỉ để đọc các field bất biến đã tạo không?  
**Đáp án:** Không, nếu object được constructed correctly và đúng protocol, volatile write/read tạo HB cho các giá trị đã khởi tạo.

**Câu 4.** Thread A xây object rồi `consumer.start()`, consumer chỉ đọc object không bị sửa tiếp. Có cơ sở HB không?  
**Đáp án:** Có: các hành động trong A trước `start()` HB các hành động của thread được start.

**Câu 5.** `final List<MutableItem> items` có chứng minh list và item không thể bị sửa đồng thời không?  
**Đáp án:** Không. `final` giữ reference; collection và các item có thể còn mutable.

**Câu 6.** Một `BlockingQueue` giao message bất biến từ producer tới consumer. Vì sao consumer không cần dùng một volatile field riêng chỉ để lấy message?  
**Đáp án:** Queue đã có protocol đồng bộ và bảo đảm memory consistency cho hành động trước put so với hành động sau take của chính phần tử đó.

**Câu 7.** DCL dùng đọc reference ngoài lock nhưng field không volatile. Tại sao đáng ngại?  
**Đáp án:** Read ngoài lock thiếu HB với writer khởi tạo; reader không được bảo đảm quan sát trạng thái phù hợp. Cần `volatile` hoặc chọn holder idiom.

## Nguồn tham khảo chính thức

- [Java Language Specification, Java SE 17 — `17.4 Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4): synchronization order, happens-before, data races.
- [Java Language Specification, Java SE 17 — `17.5 final Field Semantics](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5): constructor freeze, correctly constructed objects và giới hạn của final.
- [Java Language Specification, Java SE 17 — `12.4 Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4): quy tắc khởi tạo class.
- [`Thread` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): start, join và memory consistency.
- [`BlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html), [`ExecutorService` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html), [`Future` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): bảo đảm giao nhận công việc/dữ liệu.
- [`AtomicReference` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): cập nhật nguyên tử một reference.

**Ghi nhớ:** **Construct first → publish safely → protect later mutation.** Để một consumer thấy object đáng tin cậy, hãy tránh constructor escape và xác lập cơ chế *happens-before* rõ ràng giữa các thread.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-008">Trường final và bảo đảm khởi tạo đúng</a>

<details>
<summary>Xem chi tiết</summary>

# Trường `final` và bảo đảm khởi tạo đúng trong Java Memory Model

## 1. Vì sao `final` liên quan đến lập trình đa luồng?

Khi nhiều thread truy cập cùng một đối tượng, câu hỏi đầu tiên là: **thread đọc có thấy trạng thái đối tượng đã được khởi tạo đầy đủ không?** Trình biên dịch, bộ xử lý và bộ nhớ đệm có thể thực hiện tối ưu hóa trong giới hạn Java Memory Model (JMM). Nếu một thread tạo đối tượng rồi chuyển tham chiếu cho thread khác mà không có cơ chế công bố đúng, thread đọc có thể thiếu bảo đảm nhìn thấy một số lần ghi trong constructor.

`final` có hai ý nghĩa cần phân biệt:

1. **Quy tắc của ngôn ngữ:** trường instance `final` chỉ được gán trong quá trình khởi tạo hợp lệ; sau đó mã Java thông thường không thể gán lại trường đó. Với kiểu nguyên thủy, giá trị trường không bị gán lại; với kiểu tham chiếu, chính *tham chiếu* không bị gán lại.
2. **Bảo đảm của Java Memory Model:** khi đối tượng được **khởi tạo đúng cách (properly constructed)**, các giá trị trường `final` đã được thiết lập trong constructor có những bảo đảm đặc biệt về khả năng nhìn thấy đối với thread khác, kể cả trong một số trường hợp không có quan hệ `happens-before` thông thường cho việc công bố tham chiếu.

Điều kiện quan trọng nhất là **không để tham chiếu `this` thoát ra cho thread khác sử dụng trước khi constructor hoàn thành**. Nếu vi phạm điều này, không thể viện dẫn bảo đảm của `final` để khẳng định thread khác luôn thấy trạng thái đã khởi tạo.

Các khái niệm gốc:

- **Initialization (khởi tạo):** constructor và các bước khởi tạo thiết lập trạng thái ban đầu của đối tượng.
- **Publication (công bố):** làm cho tham chiếu tới đối tượng trở nên truy cập được từ thread khác.
- **Visibility (khả năng nhìn thấy):** thread đọc có bảo đảm quan sát các lần ghi tạo trạng thái hay không.
- **Safe publication (công bố an toàn):** sử dụng quan hệ đồng bộ thích hợp khi chuyển đối tượng cho thread khác, ví dụ trường `volatile`, `synchronized`, `AtomicReference`, `BlockingQueue`, hoặc khởi tạo lớp (class initialization) theo hợp đồng của Java.
- **Immutability (bất biến):** trạng thái quan sát được của đối tượng không đổi sau khi khởi tạo; chỉ thêm `final` vào một tham chiếu chưa đủ để làm mọi đối tượng được tham chiếu trở nên bất biến.

Bài này tập trung vào **Java concurrency nói chung** và chính các bảo đảm của JMM; không phụ thuộc framework.

## 2. Một ví dụ dễ hình dung

Xét đối tượng cấu hình:

```java
final class RetrySettings {
    private final String serviceName;
    private final int maxRetries;

    RetrySettings(String serviceName, int maxRetries) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries");
        }
        this.serviceName = serviceName;
        this.maxRetries = maxRetries;
    }

    String serviceName() {
        return serviceName;
    }

    int maxRetries() {
        return maxRetries;
    }
}
```

Đây là **định nghĩa lớp minh họa** (chưa có `main`). Hai trường được gán khi constructor chạy và không thể gán lại qua mã Java bình thường. `String` cũng bất biến, nên kiểu dữ liệu này có thể được dùng như một value object bất biến đơn giản.

Nếu đối tượng được tạo bằng `new RetrySettings("catalog", 3)`, không làm lộ `this` trong quá trình khởi tạo và thread khác nhận được tham chiếu đó, bảo đảm đặc biệt của `final` giúp thread đọc thấy `serviceName = "catalog"` và `maxRetries = 3` theo ngữ nghĩa trường final. Đây là khác biệt đáng kể với một đối tượng có các trường thường (non-final) được công bố qua data race.

**Không suy rộng:** bảo đảm giá trị trường `final` không tự bảo đảm thread khác sẽ *nhận được tham chiếu* kịp thời, không bảo vệ các trường non-final, và không tạo an toàn cho các thao tác cập nhật trạng thái tiếp theo trên đối tượng khác.

## 3. Những quy tắc ngôn ngữ của trường `final`

### 3.1. Gán một lần trong quá trình khởi tạo

Một **blank final instance field** là trường `final` chưa được gán ở chỗ khai báo. Java yêu cầu trường đó được gán đúng cách trên mọi đường hoàn thành constructor bình thường, trong constructor hoặc bộ khởi tạo instance hợp lệ. Trường `final` đã có giá trị từ initializer thì không được gán lại trong constructor.

```java
class Point {
    final int x;
    final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
```

Đây là đoạn định nghĩa lớp minh họa. Sau khi tạo `Point p = new Point(2, 5)`, mã `p.x = 7` bị từ chối khi biên dịch. Constructor được quyền chọn giá trị từ tham số, và các đường khởi tạo hợp lệ phải hoàn tất việc gán trường.

Với trường `static final`, việc gán thường nằm ở khai báo hoặc bộ khởi tạo `static`. JMM cung cấp **cơ chế class initialization** riêng để điều phối việc khởi tạo lớp; không nên nhầm cơ chế đó với bảo đảm đặc biệt của **instance final fields** trong constructor.

### 3.2. `final` không đồng nghĩa với “object sâu bên trong bất biến”

```java
class Bag {
    final int[] values = {1, 2};
}
```

Mã `bag.values = new int[] {3, 4}` là không hợp lệ sau khởi tạo. Nhưng `bag.values[0] = 99` vẫn hợp lệ: **mảng** là đối tượng có thể sửa, còn `final` chỉ khóa việc gán lại **tham chiếu tới mảng**.

Tương tự:

```java
private final java.util.List<String> items = new java.util.ArrayList<>();
```

Danh sách vẫn cho phép `items.add(...)`. Truy cập/sửa danh sách từ nhiều thread có thể gây race condition hoặc lỗi cấu trúc. Trường `final` không biến `ArrayList` thành collection an toàn đa luồng.

Muốn thiết kế bất biến hơn, có thể sao chép phòng vệ (`List.copyOf`) và không để lộ các đối tượng phần tử có thể bị sửa. `List.copyOf` tạo danh sách không hỗ trợ thao tác sửa cấu trúc qua tham chiếu nhận được, nhưng **không tự sao chép sâu** mọi phần tử; phần tử mutable vẫn cần xét riêng.

## 4. Bảo đảm đặc biệt của JMM: constructor kết thúc và freeze action

JMM định nghĩa **freeze action** gắn với trường `final` tại thời điểm constructor ghi trường đó kết thúc theo quy tắc của JLS. Hiểu ở mức ứng dụng: khi một đối tượng hoàn tất quá trình khởi tạo một cách hợp lệ, JMM thiết lập quy tắc đặc biệt giúp các thread khác đọc những trường `final` với giá trị đã được thiết lập trong quá trình khởi tạo.

Đây là lý do Java thường khuyến khích tạo **immutable value object** bằng trường `final`. Dù một tham chiếu được công bố bằng cách thiếu đồng bộ, trường `final` của đối tượng **properly constructed** vẫn có bảo đảm riêng về giá trị khởi tạo, với điều kiện thread khác thực sự lấy được tham chiếu đó và thao tác đọc đáp ứng ngữ nghĩa của JMM.

Tuy nhiên, có giới hạn quan trọng:

- Đây **không phải** là bảo đảm rằng mọi trường non-final được ghi trong constructor cũng nhìn thấy đúng qua một lần công bố không an toàn.
- Nó **không tạo ra** một quan hệ `happens-before` tổng quát như khi dùng `synchronized` hay `volatile` để công bố.
- Nó **không làm đối tượng mutable trở thành thread-safe** sau khi constructor xong.
- Nó **không thay thế** giao thức đồng bộ cho nhiều phép đọc/ghi tạo thành một thao tác logic.
- Nó phụ thuộc vào quá trình **khởi tạo đúng**: không để thread khác quan sát `this` trước khi các trường được khởi tạo và constructor cần thiết kết thúc.

Các trường `final` tham chiếu đến object/array còn có một số quy tắc visibility bổ sung đối với trạng thái của những object được tham chiếu khi khởi tạo. Không nên diễn giải những quy tắc đó thành “mọi thay đổi về sau trên object con tự an toàn”; các thay đổi về sau vẫn cần đồng bộ phù hợp.

Đối với code thực tế, **safe publication của cả đối tượng vẫn là chính sách dễ kiểm chứng hơn**, vì nó bảo vệ cả dữ liệu không phải final và tạo ra quan hệ chia sẻ rõ ràng.

## 5. Properly constructed: không làm lộ `this` trong constructor

**This escape** là tình huống tham chiếu tới đối tượng đang được tạo thoát ra cho code có thể sử dụng nó trước khi constructor hoàn thành. Những cách xảy ra thường gặp:

- Gán `this` vào một biến `static` hoặc một holder dùng chung ngay trong constructor.
- Đăng ký `this` vào listener/callback mà thread khác có thể gọi lập tức.
- Bắt đầu thread ngay trong constructor và truyền lambda có thể truy cập các trường của `this`.
- Gửi `this` cho một dịch vụ hoặc bộ điều phối khác thực thi đồng thời.
- Gọi phương thức có thể bị ghi đè từ constructor của lớp cha; phương thức ghi đè trong lớp con có thể nhìn thấy trạng thái của lớp con **trước khi lớp con khởi tạo xong**, hoặc làm lộ tham chiếu cho thread khác.

Trong các trường hợp đó, thread khác có thể đọc trường khi nó vẫn mang **giá trị mặc định** (`0` đối với `int`, `false` đối với `boolean`, `null` đối với tham chiếu), vì constructor chưa chạy đến phép gán. Không có cơ chế JMM nào cho phép một thread đọc trước phép gán đã xảy ra mà vẫn đòi hỏi giá trị tương lai phải xuất hiện.

### 5.1. Minh chứng có thể chạy: công bố `this` quá sớm

Ví dụ dùng `volatile` và hai `CountDownLatch` để **chủ động bắt gặp trạng thái chưa khởi tạo**. Cách tổ chức này có kết quả xác định và thể hiện rằng ngay cả việc công bố tham chiếu qua `volatile` cũng không thể “sửa” việc công bố **trước khi constructor hoàn thành**.

Tạo tệp `ThisEscapeDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class ThisEscapeDemo {
    private static volatile Leaky published;
    private static final CountDownLatch referenceVisible =
            new CountDownLatch(1);
    private static final CountDownLatch finishConstructor =
            new CountDownLatch(1);

    private static final class Leaky {
        final int answer;

        Leaky() {
            published = this; // Công bố khi answer chưa được gán.
            referenceVisible.countDown();

            try {
                finishConstructor.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }

            answer = 42;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread builder = new Thread(Leaky::new, "builder");
        builder.start();

        referenceVisible.await();
        Leaky leaked = published;
        System.out.println("Khi constructor chưa xong: " + leaked.answer);

        finishConstructor.countDown();
        builder.join();
        System.out.println("Sau khi constructor xong: " + leaked.answer);
    }
}
```

Biên dịch và chạy trên Java 17+:

```bash
javac ThisEscapeDemo.java
java ThisEscapeDemo
```

Kết quả khi không có yêu cầu interrupt bên ngoài:

```text
Khi constructor chưa xong: 0
Sau khi constructor xong: 42
```

**Tại sao không phụ thuộc vào may rủi lập lịch?**

1. Worker gán `published = this` qua trường `volatile` **trước khi** gán `answer = 42`.
2. Worker báo `referenceVisible` rồi đứng chờ `finishConstructor`, nên main biết tham chiếu đã xuất hiện nhưng phép gán `answer` chưa thể chạy.
3. Main đọc `leaked.answer`: vì chưa có phép gán, trường `int` đang mang giá trị mặc định `0`.
4. Main mở latch, cho phép constructor thực hiện `answer = 42` và kết thúc.
5. `builder.join()` hoàn thành tạo quan hệ *happens-before* từ các hành động của builder đến đoạn đọc cuối ở main; giá trị `42` được nhìn thấy sau khi hoàn thành.

Ví dụ chỉ dùng để **giải thích nguy cơ và điều kiện khởi tạo**. Code ứng dụng không nên lưu một đối tượng chưa hoàn tất constructor như thế. Có `volatile` trong ví dụ không khiến trạng thái chưa được gán tự trở nên hợp lệ.

### 5.2. Sửa bằng cách khởi tạo xong rồi mới công bố

Tách hẳn hai hành động:

```java
LeakyReplacement object = new LeakyReplacement(42);
// Constructor đã trả về thành công.
registry.publish(object);
```

Đây là **mã minh họa** với `LeakyReplacement` và `registry` là API do ứng dụng định nghĩa. Điều quan trọng là constructor không đăng ký `this` ra bên ngoài; bên gọi nhận object hoàn chỉnh rồi mới công bố thông qua cơ chế thread-safe của registry. Không nên tin rằng chỉ đổi tên biến hoặc thêm `final` vào tham số constructor sẽ ngăn được this escape.

## 6. Safe publication: những con đường chuyển object cho thread khác

Đối tượng đã được khởi tạo đúng nên được chuyển qua một cơ chế có quan hệ bộ nhớ rõ ràng. Một số cách phổ biến:

| Cơ chế | Bảo đảm cần nhớ | Phù hợp khi |
| --- | --- | --- |
| Gán vào trường `volatile` sau khi constructor kết thúc | Ghi volatile và đọc volatile tương ứng tạo quan hệ visibility; một tham chiếu được công bố hoàn chỉnh | Một cấu hình hiện hành được thay thế theo phiên bản |
| `AtomicReference.set/get` | Công bố/đọc tham chiếu có ngữ nghĩa bộ nhớ phù hợp; có CAS để cạnh tranh cập nhật | Nhiều reader và trạng thái nguyên khối bất biến |
| Chuyển object qua `BlockingQueue.put/take` | API concurrent collection cung cấp bảo đảm truyền dữ liệu giữa producer và consumer | Bàn giao task hoặc thông điệp |
| Ghi/đọc dưới cùng `synchronized` hoặc lock có hợp đồng bộ nhớ | Hành động nhả khóa xảy ra trước hành động lấy khóa tiếp theo trên cùng cơ chế | Dữ liệu mutable cần bảo vệ nhất quán |
| Đưa tham chiếu vào một trường `static` được khởi tạo an toàn cùng lớp | Class initialization có quy tắc đồng bộ riêng | Singleton/constant khởi tạo khi lớp được sử dụng |
| Công bố trước `Thread.start()` tới thread vừa tạo | Hành động của caller trước `start()` happens-before các hành động của thread được khởi động | Bàn giao dữ liệu lúc tạo worker |

Chỉ đặt một tham chiếu vào một trường thường rồi cho thread khác đọc lại **không mặc nhiên là safe publication**. Bảo đảm đặc biệt của `final` có thể giúp một số dữ liệu khởi tạo, nhưng cách công bố thiếu đồng bộ còn gây nhiều vấn đề khác: reader có thể không thấy tham chiếu mới đúng lúc, đọc các trường non-final không đáng tin, hoặc không có thứ tự rõ với các cập nhật sau đó.

**Không hiểu sai:** `volatile` trên trường tham chiếu không làm **nội dung đối tượng** mutable tự an toàn đa luồng. Mô hình tốt là tạo toàn bộ object bất biến, rồi thay **một tham chiếu** bằng cơ chế công bố phù hợp. Nếu nhiều writer cùng tạo giá trị mới dựa trên giá trị cũ, phải giải quyết cạnh tranh cập nhật bằng CAS hoặc khóa, vì phép `current = next` chỉ đơn lẻ có thể làm mất cập nhật logic.

## 7. Ví dụ hoàn chỉnh: tạo đối tượng bất biến rồi công bố an toàn

Tạo tệp `FinalSettingsDemo.java`:

```java
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class FinalSettingsDemo {
    private static final class Settings {
        private final String service;
        private final int maxAttempts;

        Settings(String service, int maxAttempts) {
            this.service = Objects.requireNonNull(service);
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("maxAttempts");
            }
            this.maxAttempts = maxAttempts;
        }

        String service() {
            return service;
        }

        int maxAttempts() {
            return maxAttempts;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Settings> current = new AtomicReference<>();
        CountDownLatch published = new CountDownLatch(1);

        Thread builder = new Thread(() -> {
            Settings complete = new Settings("catalog", 3);
            current.set(complete); // Công bố sau constructor.
            published.countDown();
        }, "builder");

        builder.start();
        published.await();

        Settings snapshot = current.get();
        System.out.println("service=" + snapshot.service());
        System.out.println("maxAttempts=" + snapshot.maxAttempts());
        builder.join();
    }
}
```

```bash
javac FinalSettingsDemo.java
java FinalSettingsDemo
```

Kết quả:

```text
service=catalog
maxAttempts=3
```

Phân tích:

1. `new Settings(...)` thiết lập các trường `final` và constructor trả về trước khi object được công bố.
2. `AtomicReference.set` chuyển tham chiếu hoàn chỉnh theo hợp đồng bộ nhớ của atomic reference. `CountDownLatch` cũng tạo quan hệ phối hợp để main không đọc `null`.
3. Main lấy một tham chiếu `snapshot` và đọc hai trường từ cùng đối tượng bất biến.
4. Trường `service` là `String` bất biến, trường `maxAttempts` là `int`, nên không có trạng thái con mutable bị chia sẻ qua hai trường này.
5. `builder.join()` kết thúc vòng đời worker. Trong ví dụ, cả `AtomicReference` và latch đã có bảo đảm chuyển giao dữ liệu; vì thế **kết quả này không phải phép thử tách riêng bảo đảm JMM của `final` khi tham chiếu được công bố thiếu đồng bộ**. Nó minh họa mẫu thiết kế an toàn được khuyến khích.

Nếu cần thay đổi cấu hình, tạo **Settings mới** và công bố tham chiếu mới thay vì thử sửa các trường `final`. Reader đang dùng snapshot cũ vẫn thấy snapshot đó không đổi; reader mới có thể nhận snapshot mới tùy thời điểm đọc.

## 8. `final` trên tham chiếu mutable: lỗi vẫn có thể xảy ra

Tạo tệp `FinalReferenceDemo.java`:

```java
public class FinalReferenceDemo {
    private static final class Box {
        final int[] values = {10, 20};
    }

    public static void main(String[] args) {
        Box box = new Box();

        System.out.println("before=" + box.values[0]);
        box.values[0] = 99;
        System.out.println("after=" + box.values[0]);
    }
}
```

```bash
javac FinalReferenceDemo.java
java FinalReferenceDemo
```

Kết quả:

```text
before=10
after=99
```

`box.values` không được phép gán sang mảng mới, nhưng phần tử bên trong mảng vẫn có thể đổi. Nếu nhiều thread cùng đọc/ghi `values[0]` mà không có cơ chế đồng bộ phù hợp, `final` trên `values` không tránh được data race.

Một số cách cải thiện thiết kế:

- Dùng kiểu bất biến thực sự, ví dụ một record chứa các trường nguyên thủy hoặc `String` bất biến.
- Với mảng mutable, sao chép phòng vệ khi đưa vào constructor và sao chép khi trả ra nếu cần tránh caller giữ tham chiếu và sửa sau lưng.
- Với collection, dùng các bản sao không sửa được và kiểm tra xem phần tử bên trong có mutable hay không.
- Nếu object thật sự phải thay đổi trong quá trình chạy, dùng cơ chế đồng bộ thích hợp quanh **mọi đường đọc/ghi** trạng thái đó, thay vì dựa vào `final` để thay cho khóa.

Đây là sự khác biệt giữa **immutable object**, **effectively immutable object** (được tạo xong rồi không sửa thêm trong thực tế), và **final reference to mutable object**. Chỉ trường hợp đầu tiên trực tiếp tạo mô hình bất biến rõ từ hợp đồng của kiểu dữ liệu. “Effectively immutable” có thể rất hữu ích, nhưng cần bảo đảm không còn đường cập nhật nào xảy ra sau công bố.

## 9. Trường `final` khác `volatile` ở đâu?

| Vấn đề | `final` instance field | `volatile` field |
| --- | --- | --- |
| Có được gán lại theo Java thông thường sau khi khởi tạo? | Không | Có |
| Dùng chủ yếu cho | Trạng thái khởi tạo ổn định và thiết kế bất biến | Giá trị được cập nhật/công bố giữa các thread |
| Bảo đảm JMM nổi bật | Quy tắc khởi tạo trường final và freeze khi đối tượng được xây dựng đúng | Visibility và thứ tự bộ nhớ của các thao tác volatile |
| Có khiến nhiều trường thành một transaction? | Không | Không |
| Có làm object được tham chiếu trở nên immutable? | Không | Không |
| Thay thế monitor khi cập nhật nhiều trường liên quan? | Không | Không |

Một trường có thể vừa là `static final` và chứa **tham chiếu tới một đối tượng mutable**, ví dụ `static final AtomicInteger COUNT`. Tham chiếu `COUNT` không đổi, nhưng trạng thái bên trong `AtomicInteger` thay đổi được qua các phương thức thread-safe của nó. Cần phân biệt thuộc tính của **biến chứa tham chiếu** với thuộc tính của **đối tượng được tham chiếu**.

Tương tự, `final int[] values` không biến phần tử mảng thành `final`; `volatile int[] values` không biến từng phần tử mảng thành volatile. Đây là hai ví dụ tiêu biểu về việc bảo đảm ở cấp **tham chiếu** không tự lan tới mọi trường/phần tử bên trong.

## 10. Khi nào bảo đảm final-field initialization không đủ?

### 10.1. Các trường khác không phải `final`

```java
class MixedState {
    final int id;
    int status;

    MixedState(int id) {
        this.id = id;
        this.status = 1;
    }
}
```

Với một object được khởi tạo đúng nhưng tham chiếu **công bố không an toàn**, quy tắc của `final` bảo vệ trường `id` theo phạm vi đặc biệt của nó; không thể dùng quy tắc đó để khẳng định `status` non-final luôn nhìn thấy giá trị constructor gán. Công bố an toàn sẽ đơn giản hóa hợp đồng cho **cả hai trường**.

### 10.2. Có cập nhật sau constructor

Giả sử `Settings` giữ một `final List<String> endpoints` rồi nhiều thread gọi `endpoints.add(...)`. Bảo đảm trạng thái khởi tạo của final field không cho biết các thread đọc danh sách có nhìn thấy lần thêm mới hay không, cũng không ngăn danh sách bị cập nhật đồng thời gây lỗi.

Nếu muốn trạng thái thay đổi có kiểm soát, hoặc tạo snapshot mới và thay tham chiếu nguyên tử, hoặc bảo vệ đối tượng mutable bằng đồng bộ.

### 10.3. Có this escape

Nếu đăng ký `this` trước khi hoàn thành constructor, thread khác có thể đọc giá trị mặc định khi trường final chưa được gán, như ví dụ `ThisEscapeDemo`. Không thể “cứu” hành vi đó chỉ bằng cách đổi trường thành `volatile` hoặc tạo `Thread.sleep(...)` để mong constructor chạy trước. Cần sửa **thời điểm công bố**.

### 10.4. Cập nhật nhóm nhiều biến liên quan

Hai trường `final` không thể thay đổi sau constructor, phù hợp khi cả hai cùng là một **trạng thái bất biến**. Nhưng nếu dữ liệu nghiệp vụ luôn cần cập nhật cặp giá trị, việc đổi hai trường thành `volatile` rồi cập nhật riêng sẽ không bảo đảm invariant. Chọn một immutable snapshot được công bố nguyên tử hoặc khóa cùng monitor cho cả thao tác cập nhật và đọc.

### 10.5. Reflection, serialization và các cơ chế đặc biệt

Reflection, cơ chế deserialization, native/unsafe hoặc bytecode instrumentation có thể can thiệp vào quá trình khởi tạo theo các đường không giống constructor Java thông thường. Các thay đổi trường final qua cơ chế đặc biệt có quy tắc và giới hạn phức tạp, có thể phụ thuộc JDK và runtime. **Không thiết kế giao thức concurrent dựa trên việc sửa trường final sau constructor.** Với ứng dụng Java bình thường, hãy tạo đối tượng mới rồi công bố tham chiếu mới.

## 11. Thiết kế constructor để object khó bị quan sát nửa chừng

Một số thói quen thiết kế giúp bảo đảm `final` thực sự có giá trị:

1. **Validate đầu vào trước khi công bố:** kiểm tra `null`, phạm vi giá trị, sự phù hợp giữa các tham số ngay trong constructor hoặc factory.
2. **Không đưa `this` cho thread khác trong constructor:** tránh ghi vào registry, gửi event, đăng ký callback có thể chạy ngay, hoặc `new Thread(() -> use(this)).start()`.
3. **Không gọi phương thức có thể override từ constructor** khi phương thức đó có thể dùng trạng thái lớp con hoặc công bố `this`. Dùng phương thức private/final cho logic khởi tạo nội bộ khi phù hợp.
4. **Chú ý các trường mutable được tham chiếu bởi final:** sao chép phòng vệ, tránh trả thẳng mảng hoặc collection mutable ra ngoài, hoặc có hợp đồng đồng bộ minh bạch.
5. **Ưu tiên object nhỏ, hoàn chỉnh, bất biến:** constructor thiết lập toàn bộ các giá trị liên quan; các hoạt động sau đó chỉ đọc.
6. **Công bố sau khi constructor trả về:** sử dụng `volatile`, atomic reference, concurrent collection hoặc monitor. Với một factory, tạo xong rồi mới đưa object vào cache/registry.
7. **Giữ nguyên tính nhất quán khi cập nhật:** thay nguyên snapshot bằng một hành động atomic hoặc dùng lock cho trạng thái mutable; final field không bảo vệ các tài nguyên khác chỉ vì chúng thuộc cùng object.

### Ví dụ factory thay vì đăng ký `this` ngay khi tạo

Thay cho một constructor tự đăng ký:

```java
// Ví dụ minh họa cách tổ chức, không phải chương trình độc lập.
final class Listener {
    private final String name;

    private Listener(String name) {
        this.name = name;
    }

    static Listener createAndRegister(String name, Registry registry) {
        Listener complete = new Listener(name);
        registry.register(complete);
        return complete;
    }
}
```

`Registry` là giao diện do ứng dụng định nghĩa; `register` phải có cơ chế thread-safe nếu cho thread khác truy cập. Điều thay đổi về kiến trúc là **constructor chỉ làm nhiệm vụ khởi tạo**, còn factory chỉ đăng ký sau khi object hoàn chỉnh.

Việc tạo constructor `private` không phải điều kiện bắt buộc cho final-field semantics, nhưng có thể giúp API ngăn việc gọi constructor rồi làm lộ `this` theo đường không chủ định.

## 12. Phân biệt bảo đảm và quan sát trong thí nghiệm concurrent

Khi thử một ví dụ JMM, không nên viết một vòng lặp đợi trên một trường non-volatile rồi nhận xét: “chạy một triệu lần vẫn luôn thấy đúng, vậy publication an toàn”. JMM cho phép nhiều hành vi mà một máy và một phiên bản JVM cụ thể có thể **không thể hiện dễ dàng**. Một chương trình có data race có thể trông đúng trong nhiều lần chạy và sai khi đổi tối ưu, CPU hoặc tải.

Một số cách lập luận đúng:

- Nếu thiết kế yêu cầu thread reader luôn nhận object hoàn chỉnh, **chứng minh đường công bố có happens-before**, thay vì chỉ kiểm tra output.
- Nếu thiết kế yêu cầu object không đổi sau khởi tạo, **kiểm tra khả năng rò rỉ mutable state**: có mảng, collection, builder, listener hoặc tham chiếu tới object thay đổi được không?
- Nếu constructor có thể bị truy cập giữa chừng, dùng điều phối bằng latch trong bài học để **chứng minh trạng thái thật sự chưa gán**, như `ThisEscapeDemo`. Điều đó không chứng minh rằng lỗi luôn xuất hiện theo cùng tần suất trong chương trình không điều phối.
- Nếu muốn kiểm tra tính đúng đắn của cập nhật nhiều field, tạo snapshot nhất quán dưới khóa hoặc dùng immutable snapshot, và diễn giải rõ điểm công bố.
- Dùng Java Flight Recorder, thread dump hoặc stress test để tìm vấn đề thực tế khi cần, nhưng không coi một lịch chạy cụ thể là định nghĩa của Java Memory Model.

## 13. Các hiểu nhầm thường gặp

**“Trường final giúp mọi trường của object an toàn đa luồng.”** Sai. Nó có bảo đảm riêng cho dữ liệu khởi tạo final; các trường mutable/non-final vẫn cần cơ chế đồng bộ thích hợp.

**“Chỉ cần khai báo `final List<?>` là có danh sách bất biến.”** Sai. Final giữ tham chiếu, không cấm sửa nội dung collection.

**“Có thể đăng ký `this` trong constructor vì mọi trường đều final.”** Sai. Thread khác có thể đọc object trước khi các phép gán thực sự xảy ra, và final-field guarantees yêu cầu proper construction.

**“Volatile khi công bố tham chiếu sẽ che giấu việc constructor chưa xong.”** Sai. Nếu tham chiếu được công bố quá sớm, thread khác có thể thấy giá trị mặc định. Volatile chỉ áp dụng các bảo đảm bộ nhớ theo thứ tự thực tế của những thao tác đã xảy ra.

**“Một object có trường final thì không cần công bố an toàn.”** Kết luận quá rộng. Final có bảo đảm đặc biệt cho những trường đó nếu đối tượng properly constructed, nhưng không giải quyết toàn bộ đời sống và các dữ liệu liên quan. Safe publication vẫn là chính sách được khuyến khích khi chuyển object giữa thread.

**“Dùng `static final` là giống hệt instance final.”** Không hoàn toàn. `static final` gắn với quá trình khởi tạo lớp; JVM bảo đảm đồng bộ class initialization theo cơ chế riêng. Nếu trường static final giữ mutable object, trạng thái bên trong object đó vẫn cần cơ chế điều phối khi thay đổi.

**“Record luôn immutable sâu.”** Không đúng. Các component của record được lưu trong trường final, nhưng nếu component là mảng hoặc object mutable thì vẫn có thể thay đổi nội dung. Record là lựa chọn tốt cho value object khi các thành phần của nó cũng được thiết kế bất biến hoặc sao chép phòng vệ phù hợp.

## 14. Tự kiểm tra kiến thức

**Câu 1.** Một lớp có `final int value` được gán trong constructor, đối tượng properly constructed, tham chiếu tới nó cuối cùng được một thread khác đọc qua đường công bố thiếu đồng bộ. Bảo đảm đặc biệt của `final` là gì?

**Trả lời:** Khi thread khác lấy được tham chiếu và đọc trường final theo ngữ nghĩa JMM, các giá trị final đã được gán trong quá trình khởi tạo đúng có bảo đảm visibility đặc biệt. Điều đó không bảo đảm việc thread đọc sẽ nhận được tham chiếu đúng hạn, cũng không mở rộng mọi bảo đảm sang trường non-final.

**Câu 2.** Vì sao `final int[] values` không đảm bảo `values[0]` an toàn khi nhiều thread sửa?

**Trả lời:** `final` chỉ cấm gán lại tham chiếu `values`; nội dung mảng vẫn mutable. Các phép đọc/ghi phần tử đồng thời vẫn có thể data race.

**Câu 3.** Trong constructor, object gán `shared = this` trước khi gán `final int answer = 42` (hoặc trước khi gán blank final trong constructor). Reader có thể thấy `answer = 0` không?

**Trả lời:** Có. Reader có thể chạy khi giá trị final chưa được gán. Việc công bố sớm vi phạm điều kiện proper construction.

**Câu 4.** Có nên dùng `volatile` cho mọi trường vốn chỉ thiết lập một lần và không thay đổi sau constructor?

**Trả lời:** Không cần làm vậy mặc định. Trường final thể hiện hợp đồng bất biến và có bảo đảm khởi tạo riêng. Khi chuyển object giữa các thread, chọn một cơ chế công bố an toàn cho toàn object và dùng volatile/lock nếu trạng thái thực sự cần cập nhật.

**Câu 5.** `AtomicReference<Config>` chứa một immutable `Config`. Nhiều writer cập nhật bản mới dựa trên bản cũ bằng `set` đơn giản. Có thể mất cập nhật không?

**Trả lời:** Có. Từng `set` nguyên tử nhưng hai writer có thể cùng đọc bản cũ và ghi đè lẫn nhau. Nếu mỗi cập nhật phụ thuộc trạng thái cũ, dùng `updateAndGet`/CAS với hàm chuyển đổi không có side effect hoặc dùng khóa phù hợp.

**Câu 6.** Một `record Config(int limit, List<String> hosts)` đã được khởi tạo. Nó có chắc chắn bất biến sâu không?

**Trả lời:** Không. Trường tham chiếu `hosts` là final nhưng bản thân `List` có thể mutable. Constructor nên tạo bản sao không sửa được (`List.copyOf`) khi hợp đồng cần tính bất biến và cân nhắc kiểu phần tử bên trong.

**Câu 7.** Tại sao gọi phương thức có thể bị override từ constructor của lớp cha là nguy hiểm ngay cả khi không tạo thread mới?

**Trả lời:** Phương thức override của lớp con có thể chạy trước khi các trường của lớp con khởi tạo xong, khiến nó quan sát trạng thái mặc định. Nếu phương thức còn công bố `this`, thread khác cũng có thể nhìn thấy object chưa hoàn chỉnh.

## 15. Tổng hợp

**`final` là lời cam kết về việc khởi tạo và không gán lại một trường**, đồng thời cung cấp bảo đảm đặc biệt trong Java Memory Model để giá trị khởi tạo của trường final được quan sát đúng khi object được xây dựng hợp lệ. Điều kiện nền tảng là **constructor hoàn tất mà không làm lộ `this` cho thread khác sử dụng trước đó**.

Một thiết kế dễ giữ đúng là tạo object bất biến với các trường final, kiểm tra đầy đủ invariant trong constructor, chỉ công bố sau khi hoàn thành qua cơ chế thread-safe, rồi đọc object đó như một snapshot. Nếu đối tượng chứa state mutable, final reference không thay thế khóa hoặc các cấu trúc concurrent. Phân biệt **khởi tạo đúng**, **công bố an toàn** và **cập nhật đồng thời an toàn** là chìa khóa để áp dụng JMM chính xác, tránh cả lỗi nhìn thấy dữ liệu khởi tạo lẫn race condition phát sinh về sau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-009">Data race và hành vi quan sát khó dự đoán</a>

<details>
<summary>Xem chi tiết</summary>

# Data race và hành vi quan sát khó dự đoán trong Java Memory Model

## 1. Vấn đề: vì sao kết quả đa luồng không giống cách đọc mã tuần tự?

Trong chương trình Java đa luồng, hai thread có thể chạy xen kẽ hoặc song song và truy cập cùng dữ liệu tại các thời điểm không đoán trước. Một thread cũng không nhất thiết quan sát ngay các ghi từ thread khác. Nếu thiếu **quan hệ đồng bộ phù hợp**, chương trình có thể mất cập nhật, nhìn thấy giá trị cũ, quan sát trạng thái không nhất quán hoặc chờ mãi một tín hiệu đã được phát ra.

Cần xét hai khía cạnh khác nhau:

- **Scheduling:** khi nào các thread có cơ hội thực thi; thứ tự này phụ thuộc JVM, OS và tình trạng máy.
- **Java Memory Model (JMM):** quy tắc cho biết những giá trị nào một phép đọc được phép quan sát; quan hệ **happens-before** giúp bảo đảm visibility và ordering giữa các thread.

Không phải thứ tự log thay đổi là một lỗi. Nếu hai nhiệm vụ độc lập in hai dòng, cả hai thứ tự có thể đúng. Vấn đề xuất hiện khi tính đúng đắn dựa vào một thứ tự hoặc một quan sát mà JMM **không bảo đảm**.

## 2. Data race theo định nghĩa JMM

Hai hành động truy cập một biến được gọi là **conflicting** khi cùng động đến một biến và ít nhất một hành động là ghi. Một chương trình có **data race** nếu tồn tại hai hành động conflicting mà **không có quan hệ happens-before sắp thứ tự giữa chúng**.

Ví dụ ở mức minh họa, với một trường `int value` dùng chung:

```java
// Thread A:
shared.value = 1;

// Thread B:
int observed = shared.value;
```

Đoạn trên chỉ minh họa hai đoạn mã của hai thread, **không phải file Java độc lập**. Nếu không có synchronized, volatile, thread start/join hoặc đồng bộ khác thiết lập happens-before thích hợp, không được dựa vào việc A “thường chạy trước” để kết luận B sẽ quan sát 1.

### Phân biệt các khái niệm dễ lẫn

| Khái niệm | Vấn đề | Ví dụ |
| --- | --- | --- |
| **Data race** | Truy cập xung đột cùng biến không được sắp thứ tự bằng happens-before | Một thread ghi trường thường, thread khác đọc nó không đồng bộ |
| **Race condition** | Tính đúng đắn phụ thuộc vào timing hoặc thứ tự xen kẽ | Hai yêu cầu cùng thấy còn một đơn vị hàng và đều bán |
| **Atomicity violation** | Một chuỗi thao tác cần xem như một bước logic nhưng bị chen ngang | Hai thread chạy `counter++` và mất cập nhật |
| **Visibility problem** | Không có bảo đảm thread khác thấy thay đổi | Vòng chờ cờ `done` thường không kết thúc |
| **Ordering problem** | Các hành động được quan sát không như suy luận dựa trên mã nguồn | Thread thấy cờ sẵn sàng nhưng chưa thấy dữ liệu tương ứng |

**Data race và race condition không đồng nhất.** Hai thread cùng thực hiện `volatileCounter++` trên trường `volatile int` có thể mất cập nhật dù các truy cập volatile riêng lẻ là synchronization actions. Đây là **race condition về nguyên tử tính**, không cần có data race theo định nghĩa JMM.

Ngược lại, data race không đồng nghĩa mọi lần chạy đều tạo ra kết quả sai; nó cho phép những quan sát mà chương trình không nên dựa vào.

## 3. Ba nhóm hành vi quan sát khó đoán

### 3.1. Lost update: một phép tăng không phải một bước nguyên tử

Giả sử hai thread cùng chạy `count++`. Ý nghĩa logic của phép tăng là:

```text
old = count
newValue = old + 1
count = newValue
```

Nếu cả hai đọc `old = 0` trước khi ghi, cả hai cùng ghi 1 và mất một lần tăng. Với trường `int` thường, các truy cập này có thể tạo data race; với trường `volatile int`, phép đọc và ghi riêng vẫn không hợp thành thao tác tăng nguyên tử.

### 3.2. Visibility: cờ hoàn tất không phải lúc nào cũng được nhìn thấy

```java
// Minh họa: finished là trường boolean thường được chia sẻ.
while (!finished) {
    // spin
}
```

Writer có thể đã đặt `finished = true`, nhưng nếu không có happens-before, JMM không cam kết reader sẽ thấy thay đổi. JVM có thể tối ưu hóa vòng lặp và thread có thể tiếp tục đọc giá trị cũ. `Thread.sleep()` hoặc `Thread.yield()` không bổ sung bảo đảm công bố dữ liệu.

`boolean` được đọc/ghi nguyên tử theo kiểu dữ liệu vẫn không có nghĩa **visibility** được bảo đảm giữa các thread.

### 3.3. Ordering: thấy tín hiệu chưa chắc thấy nội dung

```java
// Writer, chỉ là đoạn minh họa:
payload = 42;
ready = true;

// Reader, chỉ là đoạn minh họa:
if (ready) {
    System.out.println(payload);
}
```

Nếu cả `payload` và `ready` là trường thường, truy cập không được đồng bộ và hai đoạn chạy trên hai thread, không thể kết luận “đọc ready là true ⇒ chắc chắn đọc payload là 42”. Thứ tự chương trình của writer **không tự tạo** quan hệ happens-before từ writer sang reader.

Có thể sửa bằng giao thức xuất bản: writer ghi payload trước, sau đó ghi **volatile** ready; reader đọc ready volatile rồi mới dùng payload. Với giao thức nhiều producer hoặc nhiều lần cập nhật, cần xem xét thêm khả năng thay đổi sau xuất bản và tính nguyên tử của cả trạng thái.

## 4. Java 17+: ví dụ lost update tái hiện có kiểm soát

Tạo file `ForcedLostUpdate.java`:

```java
import java.util.concurrent.CountDownLatch;

public class ForcedLostUpdate {
    private static final class Box {
        int count;
    }

    public static void main(String[] args) throws InterruptedException {
        Box box = new Box();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch mayWrite = new CountDownLatch(1);

        Runnable increment = () -> {
            int old = box.count;
            bothRead.countDown();
            try {
                mayWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            box.count = old + 1;
        };

        Thread a = new Thread(increment, "A");
        Thread b = new Thread(increment, "B");
        a.start();
        b.start();

        bothRead.await();
        mayWrite.countDown();
        a.join();
        b.join();

        System.out.println("Correct total: 2");
        System.out.println("Actual total: " + box.count);
    }
}
```

Biên dịch và chạy:

```bash
javac ForcedLostUpdate.java
java ForcedLostUpdate
```

Đầu ra khi chạy hoàn tất bình thường:

```text
Correct total: 2
Actual total: 1
```

Cả hai worker đọc 0 **trước** khi gọi `bothRead.countDown()`. Main chỉ mở `mayWrite` sau khi cả hai đã đến mốc này. Do đó hai worker đều ghi `old + 1` tức 1, bất kể ai ghi trước. Hai lần ghi cùng biến thường không được sắp thứ tự happens-before với nhau, nên có data race; `join()` chỉ bảo đảm main chờ và thấy các tác dụng của worker sau khi chúng kết thúc. Kết quả 1 ở đây là do thiết kế lịch xen kẽ có kiểm soát, không phải lời hứa rằng mọi chương trình dùng `count++` đều trả 1.

**Bài học:** dùng latch để tái hiện lịch xen kẽ là công cụ kiểm thử tốt, nhưng bản thân latch trong ví dụ cố ý **không** bảo vệ phép tăng; tất cả thread đã qua rào mới cùng ghi giá trị cũ.

## 5. Java 17+: sửa bằng phép tăng atomic

Tạo file `AtomicCounterDemo.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger count = new AtomicInteger();

        Runnable increment = () -> {
            for (int i = 0; i < 10_000; i++) {
                count.incrementAndGet();
            }
        };

        Thread a = new Thread(increment, "A");
        Thread b = new Thread(increment, "B");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Final count: " + count.get());
    }
}
```

Chạy:

```bash
javac AtomicCounterDemo.java
java AtomicCounterDemo
```

Đầu ra:

```text
Final count: 20000
```

`AtomicInteger.incrementAndGet()` là phép tăng nguyên tử: không thể có hai lần cập nhật thành công dựa trên cùng trạng thái cũ theo cách làm mất lượt tăng. Hai thread vẫn chạy theo thứ tự tùy scheduler, nhưng kết quả cuối được bảo đảm. `join()` giúp main chỉ đọc sau khi cả hai hoàn tất.

Nếu nhiều trường phải thay đổi cùng nhau theo một bất biến (ví dụ số lượng và trạng thái đơn hàng), nhiều `AtomicInteger` riêng lẻ **không** tự tạo transaction. Khi đó cần lock chung, CAS trên một đối tượng trạng thái bất biến hoặc giao thức khác bao phủ toàn bộ bất biến.

### Khi dùng `synchronized`?

```java
final class SafeCounter {
    private int value;

    public synchronized void increment() {
        value++;
    }

    public synchronized int get() {
        return value;
    }
}
```

Đây là class phụ trợ **không có main độc lập**. Các lời gọi sử dụng cùng monitor của đối tượng `SafeCounter`, nên không có hai thread đồng thời thực hiện phần tăng trên cùng đối tượng. `get()` cũng dùng monitor đó, đảm bảo quan hệ happens-before qua mở/đóng khóa. Nếu code bên ngoài có đường đọc/ghi không tuân theo cùng khóa, thiết kế vẫn có lỗ hổng.

## 6. Happens-before là nền tảng của visibility và ordering

JMM dùng **happens-before (HB)** để chỉ quan hệ thứ tự một phần giữa các hành động. Một số quy tắc quan trọng:

| Nguồn quan hệ | Bảo đảm chính |
| --- | --- |
| **Program order** | Trong một thread, hành động trước trong thứ tự chương trình HB hành động sau |
| `Thread.start()` | Lời gọi start trên thread HB hành động trong thread mới |
| `Thread.join()` thành công | Hành động của worker HB việc thread khác phát hiện worker đã kết thúc |
| Monitor `synchronized` | Unlock một monitor HB lần lock tiếp theo trên **cùng monitor** |
| Trường `volatile` | Ghi volatile HB lần đọc volatile tiếp theo của **cùng trường** theo synchronization order |
| `CountDownLatch` | Các hành động trước `countDown()` HB hành động sau `await()` thành công |
| Thư viện concurrent | `BlockingQueue`, `Future`, `Executor` và các primitive khác có memory-consistency guarantees trong hợp đồng API |

Quan hệ HB có **tính bắc cầu**. Nếu A HB B và B HB C thì A HB C; nhờ vậy ghi dữ liệu thường trước một tín hiệu volatile có thể được công bố tới reader sau khi reader đọc tín hiệu thích hợp.

HB là quan hệ về ngữ nghĩa quan sát, **không đơn thuần** là “A được CPU chạy vật lý trước B”. Khi main gọi `a.start(); b.start();`, không có bảo đảm mọi hành động của A sẽ xảy ra trước mọi hành động của B. Khi main lần lượt `a.join(); b.join();`, main có thể thấy kết quả của cả hai, nhưng việc join **không tự sắp thứ tự toàn bộ thao tác giữa A và B**.

**Lưu ý:** `Thread.sleep()`, `Thread.yield()`, `setPriority()` và việc in log không nên dùng như primitive đồng bộ dữ liệu. Chúng có thể thay đổi timing, nhưng không thay thế quan hệ HB cần thiết.

## 7. Java 17+: công bố an toàn một kết quả chưa volatile

Nếu một thread chỉ cần thông báo đã tạo xong kết quả, `CountDownLatch` diễn đạt sự kiện tốt hơn vòng spin tự viết.

Tạo file `SafePublicationDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class SafePublicationDemo {
    private static int result;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            result = 42;
            ready.countDown();
        }, "producer");

        producer.start();
        ready.await();
        System.out.println("Result: " + result);
        producer.join();
    }
}
```

Chạy:

```bash
javac SafePublicationDemo.java
java SafePublicationDemo
```

Đầu ra:

```text
Result: 42
```

Writer ghi `result = 42` trước `countDown()`. Khi `ready.await()` trở về thành công, các hành động trước `countDown()` happens-before những hành động sau `await()`. Vì thế main nhìn thấy 42 ngay sau await dù `result` là trường thường. `join()` cuối cùng nhằm xác nhận vòng đời producer đã kết thúc, không phải điều kiện duy nhất tạo visibility trong ví dụ.

Có thể dùng một cờ `volatile boolean ready` cho giao thức single-writer công bố một lần:

```java
// Ý tưởng về hai trường của cùng đối tượng:
private int payload;
private volatile boolean ready;

// Writer: payload = 42; ready = true;
// Reader: if (ready) System.out.println(payload);
```

Đây là **snippet ý tưởng**, không phải file Java độc lập. Nếu reader quan sát ghi volatile `ready = true` phù hợp, những ghi trước đó trong writer có quan hệ HB với đọc sau cờ của reader. Nhưng volatile không làm các thao tác cập nhật nhiều trường thành atomic, và không nên lạm dụng busy-wait nếu có thể dùng latch hoặc queue.

## 8. Kết quả cả hai thread cùng đọc 0: ví dụ store buffering

Xét hai trường `int x = 0` và `int y = 0`, không đồng bộ:

```text
Thread A              Thread B
--------              --------
x = 1                 y = 1
r1 = y                r2 = x
```

Nếu cố áp đặt một thứ tự xen kẽ tuần tự duy nhất cho bốn thao tác, không thể có đồng thời `r1 == 0` và `r2 == 0`: để A đọc y bằng 0 thì A phải đọc trước khi B ghi y; để B đọc x bằng 0 thì B phải đọc trước khi A ghi x, tạo vòng mâu thuẫn với thứ tự lệnh nội bộ mỗi thread.

Nhưng trong chương trình **có data race**, JMM cho phép kết quả `r1 == 0, r2 == 0` của bài toán store buffering này. Do đó không thể phân tích mọi chương trình racy bằng cách chỉ hoán vị những dòng mã nguồn thành một lịch tuần tự trực giác. Trình biên dịch, JIT, cache và phần cứng có thể áp dụng tối ưu hóa trong giới hạn đặc tả.

Đây là ví dụ **về kết quả được phép**, không phải kết quả đảm bảo xuất hiện trên một JVM, máy hoặc một lần chạy cụ thể. Nếu cả x và y là volatile, kết quả “cả hai đọc 0” bị loại bởi thứ tự đồng bộ nhất quán dành cho các truy cập volatile trong tình huống này. Điều đó **không** có nghĩa bất kỳ thuật toán nào có nhiều trường volatile cũng tạo thành transaction.

## 9. Java data race không tương đương hoàn toàn với undefined behavior của C/C++

Không nên dùng một câu “mọi thứ đều có thể xảy ra” để mô tả JMM. Java vẫn có các quy tắc ràng buộc giá trị mà phép đọc được phép quan sát, kể cả trong chương trình có data race. Tuy nhiên, **tập kết quả hợp lệ rộng hơn** và có thể rất khác với cách lập trình viên suy luận theo một lịch xen kẽ đơn giản. Vì vậy chương trình racy vẫn không đáng tin cậy.

Một nguyên tắc quan trọng là **data-race-free ⇒ sequentially consistent** trong phạm vi các điều kiện của JMM: khi truy cập dữ liệu được đồng bộ đúng, người lập trình có thể suy luận thuận tiện hơn theo một lịch xen kẽ tuần tự. Nhưng **sequential consistency không làm tất cả thao tác nhiều bước trở thành atomic**: thuật toán vẫn phải bảo vệ từng bất biến riêng.

### Trường `long` và `double` không volatile

Đặc tả Java cho phép một số triển khai coi đọc/ghi các trường `long` hoặc `double` không volatile theo hai nửa 32-bit, có khả năng quan sát giá trị **torn** khi truy cập chia sẻ không đồng bộ. Nhiều JVM hiện đại cung cấp tính atomic trong thực tế, nhưng code cần tính portable không nên dựa vào tình cờ đó. `volatile long`/`volatile double` có bảo đảm atomic cho từng thao tác đọc/ghi, còn `AtomicLong` phù hợp cho tăng/cập nhật nguyên tử.

Tính atomic của **một phép đọc hoặc ghi** không tương đương tính nguyên tử của **toàn bộ thuật toán**.

## 10. Final, immutable data và safe publication

Giảm shared mutable state là cách hữu ích để giảm nguy cơ race. Trường `final` được gán trong quá trình khởi tạo đúng có những bảo đảm đặc biệt của JMM, đặc biệt khi `this` không thoát ra cho thread khác trước khi constructor kết thúc.

```java
final class Snapshot {
    final int id;
    final String label;

    Snapshot(int id, String label) {
        this.id = id;
        this.label = label;
    }
}
```

Đây là class phụ trợ, không có main. `String` là immutable, nên một snapshot gồm các giá trị như trên dễ chia sẻ hơn đối tượng liên tục đổi trạng thái. Nhưng `final` không biến những object mutable được một trường tham chiếu đến thành bất biến; cũng không thay thế việc xuất bản tham chiếu bằng giao thức phù hợp.

Các cách công bố phổ biến là đặt tham chiếu qua `volatile` field, `AtomicReference`, `BlockingQueue`, `synchronized` hoặc tạo thread sau khi object đã được xây dựng xong. Tránh khởi động thread bên trong constructor rồi để `this` lọt ra trong khi trạng thái chưa đầy đủ.

## 11. Chọn cơ chế đồng bộ theo mục tiêu

| Nhu cầu | Công cụ Java phù hợp | Điều cần lưu ý |
| --- | --- | --- |
| Chờ thread khác kết thúc | `Thread.join()` | Join có timeout có thể trở về khi thread chưa kết thúc |
| Công bố một tín hiệu hoàn tất | `CountDownLatch`, `Future` | Cần xử lý interrupt, timeout, lỗi task |
| Cờ chia sẻ đơn giản theo giao thức rõ | `volatile boolean` | Visibility/ordering, không thay thế nguyên tử tính |
| Bộ đếm cần tăng đúng | `AtomicInteger` | `counter++` trên volatile vẫn có thể mất cập nhật |
| Nhiều trường cùng một bất biến | `synchronized`, `Lock` | Toàn bộ đường đọc/ghi liên quan phải dùng cùng giao thức |
| Chuyển dữ liệu giữa producer/consumer | `BlockingQueue` | Truyền quyền sở hữu và memory-consistency effects |
| Trạng thái theo key | `ConcurrentHashMap` | Phân biệt thao tác atomic của API và chuỗi nhiều lời gọi |
| Snapshot chỉ đọc | Immutable object + safe publication | Không sửa object sau khi công bố; tránh constructor escape |

API chỉ an toàn trong phạm vi **hợp đồng cụ thể**. Ví dụ `ConcurrentHashMap.containsKey()` rồi `put()` là hai thao tác riêng; nếu cần “chưa có thì đặt” nguyên tử, dùng `putIfAbsent()` hoặc API phù hợp thay vì tách check–act.

## 12. Kiểm thử và chẩn đoán

1. **Liệt kê shared mutable state:** trường instance/static, mảng, collection, object được nhiều thread tham chiếu.
2. **Tìm conflicting accesses:** có ít nhất một writer không, và đọc/ghi từ thread khác có được sắp thứ tự HB không?
3. **Nêu bất biến cần giữ:** không mất cập nhật, không đọc dữ liệu trước khi công bố, không để kết quả trung gian bị dùng.
4. **Tạo interleaving trong test:** `CountDownLatch` hoặc `CyclicBarrier` buộc các worker tới cùng một mốc để tái hiện race; không dùng sleep tùy ý làm “bản sửa”.
5. **Stress test ở nhiều mức tải:** tăng số worker, dữ liệu và thay đổi môi trường. Không lỗi trong 10.000 lượt **không chứng minh** chương trình không có data race.
6. **Dùng công cụ đúng việc:** jcstress (OpenJDK) có thể kiểm tra tập kết quả của những litmus test concurrency; JFR, thread dump và profiler có ích cho lock contention và trạng thái runtime. Các công cụ không thay việc phân tích theo JMM.
7. **Đánh giá cả đường lỗi:** một thread chết trước khi `countDown()` có thể khiến thread khác đợi vô hạn; thiết kế timeout, interrupt và xử lý lỗi đúng với nghiệp vụ.

## 13. Các hiểu nhầm thường gặp

- **“Một lần chạy đúng chứng minh không có data race.”** Sai; lỗi phụ thuộc lịch chạy và hành vi quan sát có thể rất hiếm.
- **“Volatile giải quyết mọi race.”** Sai; `counter++` vẫn là read–modify–write.
- **“`sleep(100)` bảo đảm worker đã ghi xong.”** Sai; sleep không đợi điều kiện thật và không tạo happens-before.
- **“Một thread start trước nên ghi của nó chắc chắn xuất hiện trước thread start sau.”** Sai; start chỉ xác định quan hệ từ thread gọi tới thread mới, không sắp thứ tự toàn bộ hành động của hai worker.
- **“Atomic từng trường đồng nghĩa atomic một transaction nhiều trường.”** Sai; cần bảo vệ toàn bộ bất biến.
- **“Đặt lock ở writer là đủ.”** Sai nếu reader còn đọc không đồng bộ và không có cơ chế publication/visibility phù hợp.
- **“Data race lúc nào cũng cho giá trị ngẫu nhiên bất kỳ.”** Sai; JMM ràng buộc các quan sát hợp lệ, dù tập kết quả rộng và khó suy luận.
- **“Không còn data race thì mọi logic concurrent đều đúng.”** Sai; lỗi check-then-act nhiều bước vẫn có thể xảy ra khi mỗi truy cập riêng đều atomic.

## 14. Checklist thực hành

Trước khi chấp nhận mã chia sẻ dữ liệu giữa các thread, hãy tự hỏi:

1. Biến nào mutable, biến nào được chia sẻ và thread nào đọc/ghi?
2. Có cặp truy cập xung đột không được thứ tự hóa bởi happens-before?
3. Yêu cầu là nhìn thấy dữ liệu đã publish, hay thực hiện cả chuỗi cập nhật nguyên tử?
4. Đã chọn đúng primitive: volatile, atomic, latch, lock, queue, Future hoặc immutable snapshot?
5. Mọi đường ghi/đọc liên quan có tuân thủ cùng giao thức?
6. Timeout, interrupt và lỗi task có làm thiếu tín hiệu hay đọc trạng thái dở dang không?
7. Kết quả được cam kết có đúng dưới **mọi lịch chạy và quan sát hợp lệ** của JMM?

**Ghi nhớ:** data race là các truy cập xung đột không có thứ tự happens-before phù hợp; nó cho phép những hành vi quan sát khác với trực giác đọc mã tuần tự. Hãy thiết kế quan hệ đồng bộ rõ ràng và bảo vệ tính nguyên tử của bất biến, thay vì kỳ vọng scheduler sẽ chạy các thread theo thứ tự mong muốn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-010">Reordering của compiler, CPU và bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Reordering của compiler, CPU và bộ nhớ trong Java Memory Model

Trong chương trình một thread, ta thường đọc các lệnh theo thứ tự từ trên xuống. Nhưng khi hai hoặc nhiều thread cùng thao tác trên dữ liệu, **thứ tự lệnh được viết trong mã nguồn không tự trở thành thứ tự mà thread khác được bảo đảm quan sát**. Trình biên dịch, JIT của JVM và phần cứng đều có thể tối ưu cách thực thi; dữ liệu cũng có thể được giữ tạm trong thanh ghi, cache hoặc bộ đệm ghi. Java Memory Model (JMM) quy định những kết quả nào được phép và những quan hệ đồng bộ nào giúp chương trình có kết quả dự đoán được.

Ví dụ, nhìn hai câu lệnh:

```java
data = 42;
ready = true;
```

Đây là đoạn minh họa, giả định `data` và `ready` là hai field được thread khác đọc. Thread viết thấy `data` được gán trước `ready`; **thread đọc không mặc nhiên được bảo đảm** rằng thấy `ready == true` sẽ thấy `data == 42` nếu cả hai field thông thường được truy cập không đồng bộ. Nguyên nhân cốt lõi là chương trình chưa thiết lập quy tắc **happens-before** cần thiết, chứ không thể từ một kết quả bất thường mà xác định chắc chắn chính CPU hoặc compiler đã hoán đổi hai lệnh.

Bài học này bàn về concurrency của **Java nói chung**, tập trung vào reordering trong JMM và cách thiết kế mã Java 17+ đúng. Không phụ thuộc cơ chế concurrency riêng của Spring Framework.

## 1. Reordering nghĩa là gì?

**Reordering** là việc các thao tác được thực thi hoặc được quan sát theo một thứ tự khác với thứ tự cú pháp được viết, trong giới hạn mà ngôn ngữ và kiến trúc cho phép. Phải phân biệt ba góc nhìn:

1. **Thứ tự mã nguồn (program order):** thứ tự hành động xuất hiện trong luồng thực thi của một thread theo mô hình ngôn ngữ.
2. **Cách hiện thực tối ưu:** compiler/JIT có thể sắp xếp lại, loại bỏ, gộp thao tác hoặc lưu giá trị tạm; CPU có thể thực thi và hoàn tất thao tác theo pipeline và cơ chế bộ nhớ của nó.
3. **Thứ tự quan sát giữa các thread:** điều gì thread khác thực sự có quyền nhìn thấy theo JMM, các quan hệ đồng bộ và các thao tác xung đột.

Không có nghĩa JVM tùy ý làm sai chương trình. Các tối ưu phải **tôn trọng Java Memory Model**, bao gồm những kết quả không được phép thay đổi trong ngữ cảnh có đồng bộ đúng.

Khái niệm **as-if-serial** nói rằng tối ưu trong một thread phải giữ hành vi quan sát được của chương trình đơn luồng như thể các phép toán được thực hiện theo ngữ nghĩa tuần tự. Nhưng điều này **không đủ để đảm bảo** các thread khác nhìn thấy mọi ghi bộ nhớ theo đúng thứ tự trực quan của mã nguồn nếu chưa có quan hệ đồng bộ.

Ví dụ:

```java
int a = 10;
int b = 20;
int sum = a + b;
System.out.println(sum);
```

Trong một thread, chương trình phải in `30`; JVM có thể tối ưu tính giá trị mà không phải phát sinh từng bước đọc/ghi như người học hình dung. Điều quan trọng là kết quả hợp lệ được bảo toàn, không phải từng instruction máy phải đi theo thứ tự văn bản.

## 2. Vì sao compiler và JIT có thể thay đổi thứ tự?

Java source được `javac` biên dịch thành bytecode; JVM chạy bytecode thông qua interpreter và/hoặc trình biên dịch JIT tùy môi trường. Các bước tối ưu có thể:

- **Đưa một phép đọc ra khỏi vòng lặp** nếu phép đọc lặp lại được xem là không cần thiết theo những ràng buộc hiện có.
- **Giữ giá trị trong thanh ghi** hoặc loại bỏ các lần đọc/ghi trung gian nếu không ảnh hưởng hành vi phải bảo toàn.
- **Hoán đổi hai thao tác độc lập** trong cùng thread khi không phá các quan hệ thứ tự được ngôn ngữ yêu cầu.
- **Inline và tối ưu qua ranh giới phương thức**, khiến cấu trúc thực thi không giống cách sắp xếp lời gọi ở source.
- **Tối ưu việc truy cập trường hoặc phần tử**, miễn là tuân thủ JMM và các quy tắc về synchronization/exception/quan sát hợp lệ.

Một ví dụ nguy hiểm là vòng lặp chờ flag bình thường:

```java
class UnsafeStopSignal {
    boolean stopped;

    void requestStop() {
        stopped = true;
    }

    void work() {
        while (!stopped) {
            // Làm việc hoặc spin.
        }
    }
}
```

Đây là **anti-pattern minh họa**, không phải chương trình nên chạy thử trực tiếp: nếu một thread gọi `work()` còn thread khác gọi `requestStop()`, chương trình thiếu đồng bộ. Worker **không được bảo đảm** sẽ thấy việc cập nhật; vòng lặp có thể kéo dài vô hạn. Đây có thể liên quan đến việc compiler tối ưu việc đọc, cache/register hoặc visibility, nhưng một lần chạy không đủ để kết luận cơ chế cụ thể nào đã gây ra hành vi.

Khi `stopped` là một `volatile boolean` và tất cả đường truy cập dùng trường volatile đó, mỗi lần kiểm tra phải tôn trọng ngữ nghĩa volatile theo JMM. Tuy nhiên, **`volatile` không tạo lời hứa về lịch cấp CPU, thời gian kết thúc hay khả năng thức dậy của một worker đang block trong I/O**.

## 3. CPU, cache và store buffer: trực giác về khác biệt quan sát

Ở mức phần cứng hiện đại, nhiều CPU core có cache và đường thực thi riêng. Một phép ghi có thể qua **store buffer** trước khi các core khác có thể quan sát theo trật tự phù hợp; một phép đọc có thể nhận dữ liệu từ cache, thanh ghi hoặc hệ thống bộ nhớ. CPU còn có pipeline, speculative execution và cơ chế xử lý thứ tự bộ nhớ của kiến trúc.

Mô hình khái quát:

```text
            Core 1                           Core 2
       +--------------+                 +--------------+
       | registers    |                 | registers    |
       | store buffer |                 | store buffer |
       | cache        |                 | cache        |
       +------+-------+                 +------+-------+
              |                                |
              +---------- memory system -------+
                          |
                     shared data
```

Sơ đồ chỉ giúp hiểu **vì sao các core có thể quan sát việc đọc/ghi khác trực giác**. Nó không mô tả đúng mọi tầng cache hoặc trình tự chuyển dữ liệu của một CPU cụ thể.

Hai điều dễ nhầm:

- **Cache coherence** (duy trì tính nhất quán của giá trị tại một địa chỉ theo quy tắc phần cứng) **không đồng nghĩa** chương trình có thứ tự bộ nhớ toàn cục theo source cho mọi địa chỉ khác nhau.
- Java không bắt lập trình viên suy ra tính đúng đắn từ x86, ARM hay một CPU cụ thể. **JMM** là hợp đồng có hiệu lực giữa chương trình Java và mọi triển khai tuân thủ; compiler/JIT/JVM phải kết hợp các instruction, memory barrier hoặc kỹ thuật khác để bảo đảm hợp đồng đó trên phần cứng đang chạy.

Một chương trình data race có thể "chạy ổn" trên một cấu hình rồi hỏng khi đổi JDK, cấu hình JIT, CPU hoặc tải hệ thống. Điều đó không chứng minh một nền tảng "đã sửa" race; nó cho thấy chương trình đã dựa vào kết quả không được bảo đảm.

## 4. Litmus test: hai ghi, hai đọc và kết quả bất ngờ

Ví dụ kinh điển để suy nghĩ về visibility và reordering là **store-buffering litmus test** với hai biến `x` và `y` ban đầu bằng `0`:

```text
Giá trị đầu: x = 0, y = 0

Thread A                    Thread B
---------                   ---------
x = 1                       y = 1
r1 = y                      r2 = x

Kết quả quan tâm: r1 = 0 và r2 = 0
```

Nếu coi bốn thao tác như được **xen kẽ tuần tự trên một bộ nhớ nhất quán toàn cục**, trong khi vẫn giữ thứ tự chương trình ở từng thread, kết quả cả hai cùng đọc `0` là không thể: một trong hai phép ghi phải đứng trước phép đọc đối diện theo một trật tự đơn nhất.

Nhưng với các thao tác **ordinary (non-volatile) không có synchronization giữa các thread**, JMM cho phép hành vi không tương đương một thứ tự xen kẽ tuần tự đơn giản như vậy. Kết quả `r1 == 0 && r2 == 0` có thể hợp lệ. Không phải cứ chạy ví dụ là sẽ gặp; một nền tảng có thể quan sát chủ yếu những kết quả khác.

Đây là ví dụ phân tích, không phải stdout cố định. Nếu muốn viết phép thử Java độc lập, có thể dùng hai thread như sau:

```java
public class StoreBufferingIllustration {
    static int x, y, r1, r2;

    public static void main(String[] args) throws InterruptedException {
        Thread a = new Thread(() -> {
            x = 1;
            r1 = y;
        });
        Thread b = new Thread(() -> {
            y = 1;
            r2 = x;
        });

        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("r1=" + r1 + ", r2=" + r2);
    }
}
```

```bash
javac StoreBufferingIllustration.java
java StoreBufferingIllustration
```

**Stdout không cố định.** Chẳng hạn có thể thấy `r1=0, r2=1`, `r1=1, r2=0`, `r1=1, r2=1` hoặc `r1=0, r2=0`; các lịch chạy khác nhau và các quan sát hợp lệ của JMM ảnh hưởng kết quả. Một lần chạy thấy `1,1` không chứng minh code đã có đồng bộ.

Ở ví dụ trên, main gọi `join()` chỉ để **đọc kết quả sau khi cả hai worker kết thúc**. `join()` thiết lập happens-before từ hành động của worker tới main **sau join**, nhưng không tạo thứ tự đủ mạnh giữa thao tác `x = 1; r1 = y` của A với thao tác `y = 1; r2 = x` của B đang chạy cùng lúc. Vì vậy, `join()` không tự ngăn kết quả `0,0`.

Lưu ý: không thể dùng chính một phép thử này để khẳng định "compiler đã đảo thứ tự" hoặc "store buffer đã trì hoãn ghi" trong một lần chạy cụ thể. JMM mô tả **kết quả hợp lệ**, không cấp quyền suy ngược chắc chắn một nguyên nhân phần cứng từ output.

## 5. Java Memory Model: điều luật thay cho suy đoán CPU

JMM thuộc Java Language Specification, chương 17 (Threads and Locks). Nó quy định quan hệ giữa các hành động của chương trình, synchronization actions, **happens-before**, và những giá trị một phép đọc có thể quan sát.

Một số thuật ngữ quan trọng:

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| Program order | Thứ tự hành động trong mô hình thực thi từng thread |
| Synchronization action | Hành động đồng bộ có ý nghĩa với JMM, như lock/unlock monitor, volatile read/write, khởi động thread |
| Synchronization order | Trật tự tổng thể được mô hình bộ nhớ quy định cho các synchronization action, nhất quán với program order tương ứng |
| Synchronizes-with | Quan hệ đồng bộ cụ thể giữa các hành động, ví dụ unlock rồi lock cùng monitor hoặc volatile write rồi volatile read phù hợp |
| Happens-before (HB) | Bao gồm program order và synchronizes-with, với tính bắc cầu; dùng để xác định những hiệu ứng bộ nhớ được bảo đảm |
| Data race | Hai truy cập xung đột vào cùng biến, ít nhất một ghi, mà không có quan hệ happens-before giữa chúng |
| Correctly synchronized | Chương trình không có data race trong mọi cách thực thi tuần tự nhất quán tương ứng; được hưởng bảo đảm về sequential consistency của JMM |

**Sequential consistency (SC)** là trực giác rằng các thao tác của tất cả thread được phép xem như một thứ tự xen kẽ duy nhất và vẫn giữ program order trong từng thread. Một kết quả quan trọng của JMM: **chương trình Java được đồng bộ đúng là sequentially consistent** theo định nghĩa của JMM. Điều này **không** có nghĩa JVM phải thực thi vật lý từng instruction đúng thứ tự source; nó nghĩa kết quả của chương trình được đồng bộ đúng có thể được giải thích theo mô hình SC phù hợp.

### Happens-before và tính bắc cầu

Nếu hành động A **happens-before** B, và B happens-before C, thì A happens-before C. Quan hệ này giúp tạo **đường công bố dữ liệu** từ producer tới consumer.

Các cạnh HB thường gặp:

- Hành động trước trong cùng thread HB hành động sau theo program order.
- `Thread.start()` HB những hành động của thread vừa được start.
- Hành động của thread được đợi HB các hành động của thread gọi sau `Thread.join()` thành công.
- Unlock monitor HB lock tiếp theo trên **cùng monitor**.
- Một lần ghi volatile HB những lần đọc volatile tiếp theo cùng biến theo synchronization order.
- Hành động trước khi gửi task qua `ExecutorService` HB hành động trong task; hành động task HB hành động sau khi kết quả được lấy bằng `Future.get()` theo hợp đồng của các tiện ích concurrent.

Chỉ đọc/ghi biến ordinary từ nhiều thread mà không có một trong các đường đồng bộ phù hợp **không tự tạo HB**.

## 6. Ví dụ then chốt: message passing bằng `volatile`

Ta muốn publisher cập nhật payload rồi báo cho reader. Dùng một **volatile flag** là cách cơ bản để công bố dữ liệu nếu giao thức là đơn giản một lần.

Lưu thành `VolatilePublicationDemo.java`:

```java
public class VolatilePublicationDemo {
    private static int data;
    private static volatile boolean ready;

    public static void main(String[] args) throws InterruptedException {
        Thread reader = new Thread(() -> {
            while (!ready) {
                Thread.onSpinWait();
            }
            System.out.println("Data: " + data);
        });

        reader.start();

        data = 42;
        ready = true;

        reader.join();
    }
}
```

```bash
javac VolatilePublicationDemo.java
java VolatilePublicationDemo
```

**Stdout chính xác khi chương trình chạy hoàn tất:**

```text
Data: 42
```

Tại sao đúng?

1. Trong thread main, `data = 42` **happens-before** `ready = true` theo program order.
2. `ready = true` là **volatile write**. Khi reader đọc được `ready == true` từ volatile read, quan hệ synchronizes-with phù hợp được thiết lập từ volatile write đến volatile read.
3. Volatile read đó diễn ra trước lần đọc `data` theo program order trong reader.
4. Nhờ **tính bắc cầu HB**, ghi `data = 42` happens-before lần đọc `data`. Trong giao thức này không có ghi cạnh tranh khác tới `data`, nên reader được bảo đảm thấy `42`.

`volatile` trên **`ready`** là đủ cho ví dụ: nó tạo đường publication cho lần ghi ordinary trước đó. Không cần đánh dấu cả `data` volatile **khi chỉ có giao thức một lần và không có ghi cạnh tranh khác**.

Đây là một ví dụ học cơ chế; một vòng `while (!ready)` spin liên tục tiêu thụ CPU và không có timeout. Ứng dụng thật nên dùng blocking coordination phù hợp như `CountDownLatch`, `BlockingQueue` hoặc `Future`. Chỉ thêm `Thread.onSpinWait()` để giảm chi phí spin trên một số nền tảng **không tạo memory barrier, không bổ sung HB** và không thể sửa một flag ordinary đang race.

### Nếu bỏ `volatile` thì sao?

Với `private static boolean ready;`, thread writer gán `data = 42` rồi `ready = true` nhưng reader **không được bảo đảm** sẽ quan sát `ready` thay đổi. Ngay cả nếu thấy `true`, chương trình chưa có quan hệ công bố dữ liệu bảo đảm reader thấy `data == 42` trong ngữ cảnh truy cập dữ liệu thiếu đồng bộ.

Đừng dùng `Thread.sleep(100)` như bản sửa: ngủ một khoảng thời gian không sinh ra quan hệ HB giữa producer và reader.

## 7. `volatile` cấm những reordering nào và không làm được gì?

Trong JMM, volatile read/write là **synchronization actions** có thứ tự; các compiler và CPU phải triển khai để kết quả phù hợp với quy tắc bộ nhớ Java. Với mẫu message passing:

```java
data = 42;        // Ghi ordinary
ready = true;     // Ghi volatile
```

và:

```java
if (ready) {      // Đọc volatile
    use(data);    // Đọc ordinary
}
```

Đoạn này là minh họa: `use` là phương thức giả định. Volatile write đóng vai trò **release**, volatile read tương ứng đóng vai trò **acquire** trong cách giải thích thường dùng. Các hiệu ứng trước publication không được phép biến mất hoặc được quan sát theo cách phá HB cần thiết khi reader đã nhận publication.

Điều đó **không đồng nghĩa** mỗi biến ordinary trên toàn hệ thống bỗng được đồng bộ, hay toàn bộ chương trình bị cấm tối ưu. Các ràng buộc chỉ cần đủ để đáp ứng hành vi JMM.

Ba giới hạn rất quan trọng:

1. **Không biến phép toán tổng hợp thành atomic:** `volatile int count; count++;` vẫn là read-modify-write và có thể lost update.
2. **Không tạo critical section:** nhiều thread có thể đồng thời thực thi code sau khi thấy một flag volatile; không có mutex bảo vệ invariant nhiều field.
3. **Không đảm bảo thời gian hoặc tiến độ:** một volatile read không làm worker đang `sleep()`, đợi queue hoặc kẹt I/O tự thức dậy; cũng không bảo đảm thread được scheduler cấp CPU kịp thời.

Nếu chỉ cần flag dừng hoặc publication một chiều, volatile có thể phù hợp. Nếu cần cập nhật đếm atomic, dùng `AtomicInteger` hoặc lock; nếu cần invariant nhiều biến, thường dùng `synchronized`/`Lock` hoặc thiết kế chuyển quyền sở hữu.

## 8. `volatile` và litmus test: sửa cái gì?

Quay lại ví dụ `x` và `y`. Nếu **cả hai là volatile**:

```java
static volatile int x;
static volatile int y;

// Thread A: x = 1; r1 = y;
// Thread B: y = 1; r2 = x;
```

Các truy cập volatile nằm trong synchronization order tổng thể phù hợp với program order của mỗi thread. Nếu cả hai thread đọc giá trị ban đầu `0`, từ thứ tự:

```text
A: volatile write x=1  trước  volatile read y=0
B: volatile write y=1  trước  volatile read x=0
```

ta sẽ cần một vòng thứ tự mâu thuẫn với synchronization order và thứ tự của các phép ghi/đọc volatile. Vì vậy kết quả **`r1=0, r2=0` bị cấm** trong phiên bản cả `x` và `y` đều volatile, với cùng một lần khởi tạo và không có thread thứ ba sửa chúng.

Điểm quan trọng: nếu chỉ đổi một field thành volatile mà vẫn có những truy cập ordinary xung đột không được đồng bộ, không được suy ra chương trình đã đúng. Cần phân tích **toàn bộ giao thức** và quan hệ HB của từng lần đọc/ghi.

## 9. Monitor `synchronized`: mutual exclusion kết hợp ordering và visibility

Một vùng `synchronized` trên cùng monitor giải quyết hai vấn đề:

- **Mutual exclusion:** chỉ một thread giữ monitor đó để chạy critical section tại một thời điểm.
- **Memory ordering/visibility:** unlock trên monitor happens-before lock tiếp theo trên **chính monitor ấy**.

Ví dụ dùng monitor để trao giá trị:

Lưu thành `MonitorPublicationDemo.java`:

```java
public class MonitorPublicationDemo {
    private static final class Mailbox {
        private int value;
        private boolean ready;

        synchronized void publish(int newValue) {
            value = newValue;
            ready = true;
            notifyAll();
        }

        synchronized int awaitValue() throws InterruptedException {
            while (!ready) {
                wait();
            }
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Mailbox box = new Mailbox();

        Thread consumer = new Thread(() -> {
            try {
                System.out.println("Message: " + box.awaitValue());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        box.publish(42);
        consumer.join();
    }
}
```

```bash
javac MonitorPublicationDemo.java
java MonitorPublicationDemo
```

Stdout trên đường chạy bình thường:

```text
Message: 42
```

Vì sao không mất tín hiệu dù publisher có thể chạy trước consumer?

- Cả `publish` và `awaitValue` đồng bộ trên cùng đối tượng `Mailbox`.
- Trường `ready` được kiểm tra khi giữ monitor; nếu đã `true` thì consumer đọc ngay mà không `wait`.
- Nếu consumer `wait()` trước, nó tạm **nhả monitor**, cho publisher vào, cập nhật hai field và `notifyAll()`; consumer chỉ tiếp tục đọc `value` sau khi lấy lại monitor.
- `while (!ready)` đúng hơn `if` vì có thể có spurious wakeup hoặc một thread khác thay đổi điều kiện. `notifyAll()` không cho phép consumer đọc ngoài monitor.
- Các ghi trước unlock của publisher được bảo đảm quan sát phù hợp sau khi consumer lock lại monitor.

Mẫu này kết hợp lock và condition; trong code đa tác vụ thực tế, có thể dùng `BlockingQueue` hoặc `CountDownLatch` thay cho tự triển khai điều kiện nếu hợp đồng phù hợp hơn.

## 10. `Thread.start()` và `Thread.join()` cũng tạo thứ tự bộ nhớ

Không cần mọi field được chia sẻ đều là volatile. Khi vòng đời thread đã tạo quan hệ HB đúng, plain field có thể được đọc an toàn ở những điểm cụ thể.

Lưu thành `JoinVisibilityDemo.java`:

```java
public class JoinVisibilityDemo {
    private static int result;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> result = 42);

        worker.start();
        worker.join();

        System.out.println("Result: " + result);
    }
}
```

```bash
javac JoinVisibilityDemo.java
java JoinVisibilityDemo
```

Stdout chính xác:

```text
Result: 42
```

Thao tác `result = 42` trong worker happens-before các hành động của main sau `worker.join()` thành công. Với chỉ một writer như ví dụ và không có truy cập concurrent khác tới `result`, không cần dùng volatile.

Nhưng nếu main tự đọc `result` **trước** `join()` trong lúc worker đang chạy, đó là một kiểu truy cập khác và cần phân tích đồng bộ riêng. Tương tự, `start()` giúp công bố những hành động đã xảy ra trước `start()` tới thread mới; nó không tự đồng bộ mọi ghi phát sinh **sau** `start()` ở thread cha.

**Quan sát trạng thái `Thread.getState()` bằng polling không thể thay thế ý nghĩa đợi hoàn tất của `join()`.** Cần một primitive có hợp đồng completion/HB rõ ràng.

## 11. `Future.get()`, queue và các cạnh happens-before cấp thư viện

Các tiện ích trong `java.util.concurrent` có **memory consistency effects** được mô tả theo API. Một số trường hợp thường dùng:

| Cơ chế | Bảo đảm cần dùng |
| --- | --- |
| `ExecutorService.submit(...)` | Hành động trước khi gửi task happens-before hành động trong task |
| `Future.get()` | Hành động của task happens-before hành động sau khi caller lấy kết quả bằng `get()` |
| `BlockingQueue.put/take` | Hành động trước khi đặt phần tử vào queue happens-before hành động sau khi thread khác lấy phần tử tương ứng ra |
| `CountDownLatch.countDown/await` | Hành động trước `countDown()` happens-before hành động sau khi `await()` tương ứng trở về thành công |
| `synchronized` cùng monitor | Unlock happens-before lock tiếp theo của cùng monitor |
| `volatile` | Write happens-before các read volatile tiếp theo cùng biến theo quy tắc JMM |

Một ví dụ `Future` rõ ràng:

Lưu thành `FutureVisibilityDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureVisibilityDemo {
    private static int result;

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> future = executor.submit(() -> result = 42);

            future.get(); // Đợi hoàn tất và thiết lập cạnh HB.
            System.out.println("Result: " + result);
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac FutureVisibilityDemo.java
java FutureVisibilityDemo
```

Stdout chính xác:

```text
Result: 42
```

Ở đây task ghi trường ordinary `result`, còn main chỉ đọc sau `get()` thành công. Trạng thái `result` được công bố theo hợp đồng memory consistency của `Future`. Nếu nhiều task tiếp tục ghi đè `result` sau đó thì `get()` của một task chưa đủ bảo vệ một phép đọc trước các writer còn lại.

Điểm chung: những API đồng bộ cao hơn như future hoặc queue thường dễ dùng đúng hơn việc tự tạo hai flag boolean rồi dựa vào việc đọc/ghi không đồng bộ.

## 12. Reordering không đồng nghĩa `count++` trở thành atomic

Giả sử:

```java
private static volatile int count;

static void increment() {
    count++;
}
```

Đây là đoạn code minh họa một anti-pattern, không phải chương trình độc lập. `volatile` làm từng lần đọc và ghi `count` có ngữ nghĩa volatile. Nhưng `count++` vẫn có các bước logic:

```text
Thread A                    Thread B
read count = 5
                            read count = 5
compute 6
                            compute 6
write count = 6
                            write count = 6
```

Kết quả có thể là `6` sau hai lần tăng thay vì `7`. **Ordering/visibility không tự bảo đảm atomicity của read-modify-write**.

Sửa bằng một operation atomic:

```java
import java.util.concurrent.atomic.AtomicInteger;

final class Counter {
    private final AtomicInteger count = new AtomicInteger();

    int incrementAndGet() {
        return count.incrementAndGet();
    }
}
```

Hoặc dùng `synchronized` cho một critical section đúng. Nếu invariant gồm nhiều biến, phải bảo vệ cả giao dịch, không chỉ từng bước đọc/ghi độc lập.

## 13. Safe publication: khởi tạo xong mới công bố

Một object có thể có nhiều field liên quan nhau. Nếu một thread tạo object, khởi tạo các field rồi chuyển **tham chiếu** cho thread khác mà không có safe publication, reader có thể không được bảo đảm thấy trạng thái đã xây dựng đúng như mong đợi theo JMM.

Mẫu nguy hiểm:

```java
class Config {
    int port;
    String host;
}

class UnsafeConfigHolder {
    Config config; // Plain reference, không có giao thức publication.

    void initialize() {
        Config c = new Config();
        c.port = 8080;
        c.host = "localhost";
        config = c; // Thread khác đọc config không đồng bộ.
    }
}
```

Đây là minh họa lỗi, không phải một chương trình runnable hoàn chỉnh. Không nên lập luận rằng "phép gán `config = c` nằm cuối nên mọi thread chắc chắn thấy tất cả field mới". Nếu không có HB, việc công bố tham chiếu qua plain field trong lúc reader truy cập không đồng bộ **không tạo bảo đảm** như vậy.

Một giải pháp đơn giản là **immutable snapshot + volatile reference**, khi các field và đối tượng bên trong có ngữ nghĩa immutable thực sự:

```java
record Config(int port, String host) {}

final class ConfigHolder {
    private volatile Config current = new Config(8080, "localhost");

    void update(int port, String host) {
        current = new Config(port, host);
    }

    Config snapshot() {
        return current;
    }
}
```

`Config` là record giữ `int` và `String` (immutable), nên rất dễ lý luận về trạng thái đã công bố. Mỗi lần `snapshot()` lấy **một tham chiếu** đến một giá trị hoàn chỉnh. Volatile write/read cung cấp publication phù hợp; các reader không quan sát một object bị sửa từng field giữa chừng.

Nếu record chứa `List<Item>` với `Item` mutable thì record **không tự deep-immutable**; cần bản sao, giới hạn quyền sở hữu hoặc bảo vệ các phần tử theo yêu cầu.

## 14. `final` fields và giới hạn của việc khởi tạo

JMM có quy tắc đặc biệt cho **final fields** khi object được xây dựng đúng: các final field được khởi tạo trong constructor có bảo đảm visibility mạnh hơn field ordinary trong những trường hợp liên quan đến constructor freeze. Tuy vậy, có những giới hạn:

- **Không cho `this` thoát khỏi constructor** trước khi khởi tạo hoàn tất. Ví dụ không đăng ký listener trỏ đến `this` hoặc khởi động thread dùng `this` giữa constructor.
- `final List<String> items` chỉ cố định **tham chiếu**, không ngăn `items.add(...)` và không tự biến list thành thread-safe.
- Phải phân biệt **khởi tạo object đúng** và **cập nhật mutable state sau này**; quy tắc final field không bảo vệ các mutation về sau.
- Safe publication vẫn là thiết kế ưu tiên để chuyển object giữa các thread và bảo vệ các quan hệ giữa nhiều dữ liệu có thể đổi.

Các field `final` thích hợp với value object và bất biến, nhưng không phải "mẹo" sửa toàn bộ data race.

## 15. Double-checked locking và vai trò của `volatile`

Ví dụ singleton lazy initialization truyền thống thường viết kiểm tra nhanh rồi kiểm tra lại trong lock:

```java
final class LazyService {
    private static volatile LazyService instance;

    private LazyService() {
    }

    static LazyService getInstance() {
        LazyService local = instance;
        if (local == null) {
            synchronized (LazyService.class) {
                local = instance;
                if (local == null) {
                    local = new LazyService();
                    instance = local;
                }
            }
        }
        return local;
    }
}
```

Đây là một class minh họa đầy đủ, không có phương thức `main` vì mục tiêu là xem giao thức khởi tạo.

**Vì sao volatile quan trọng?**

- Writer phải xây dựng object rồi **công bố tham chiếu** qua volatile field.
- Reader ở đường đi nhanh có thể thấy tham chiếu không null và sử dụng nó **mà không lấy monitor**. Volatile read bảo đảm việc công bố phù hợp với các ghi đã xảy ra trước volatile write.
- Nếu xóa volatile, việc check không null ở đường đi nhanh không có synchronization với writer; trong Java hiện đại không được xem đó là một triển khai double-checked locking đúng.
- Kiểm tra bên trong `synchronized` ngăn nhiều thread đồng thời tạo instance khi còn null.

Với singleton đơn giản, static holder hoặc enum thường dễ hiểu hơn. Nếu constructor để `this` thoát ra quá sớm hoặc object có mutable state cần bảo vệ sau này, volatile singleton reference không tự giải quyết những vấn đề đó.

## 16. CPU memory barrier và JMM: cách hiểu đúng

Ở ngôn ngữ phần cứng, **memory fence/barrier** là các cơ chế giới hạn một số reorderings và quan sát bộ nhớ. JVM có thể sử dụng các instruction fence, ràng buộc JIT, các acquire/release operations hay những instruction có ngữ nghĩa ordering thích hợp để thực thi JMM trên phần cứng cụ thể.

Không có quy tắc đơn giản "mỗi lần `volatile` luôn phát sinh một instruction fence giống nhau trên mọi CPU/JDK". JMM quy định **hiệu ứng bắt buộc**, không quy định máy phải dùng một instruction cố định. Trên những nền tảng khác nhau, compiler và CPU có thể thực thi các ràng buộc khác nhau để đạt cùng hợp đồng Java.

Java còn có các API nâng cao như `VarHandle` với access modes ordinary, opaque, acquire, release và volatile. Chúng phục vụ những giao thức concurrent chuyên sâu; chọn sai mode có thể làm thiếu ordering. Một cặp lời gọi fence đặt tùy tiện **không tự thiết lập giao thức publication đúng** nếu không có các thao tác liên kết và điều kiện đọc/ghi phù hợp.

Khi học reordering, nên thành thạo `volatile`, `synchronized` và primitive concurrent trước. Chỉ dùng `VarHandle`, fences hoặc thuật toán lock-free khi có yêu cầu rõ và kiểm chứng được những điều kiện JMM của thuật toán.

## 17. Đừng dùng `sleep()` hay log để "sửa" vấn đề thứ tự

Những thay đổi sau đôi khi làm lỗi hiếm hơn nhưng không xây được hợp đồng đồng bộ:

- Chèn `Thread.sleep(10)` giữa hai thao tác.
- In `System.out.println(...)` trong vòng lặp rồi thấy lỗi biến mất.
- Thay đổi mức tối ưu JIT và kết luận race đã được sửa.
- Chạy một thread trong debugger rồi nhận kết quả ổn định.
- Chuyển từ máy nhiều lõi sang máy ít lõi và thấy test qua.

Một số thao tác logging hoặc debugger có thể **thay đổi lịch chạy và thêm đồng bộ phụ**, làm race khó tái hiện. Nhưng không được dựa vào hiệu ứng phụ đó để bảo đảm hành vi của dữ liệu chia sẻ. Thiết kế đúng phải chỉ rõ **điểm publication và cạnh HB** trong code nghiệp vụ.

Để phân biệt, thử tự hỏi: "Nếu bỏ tất cả `sleep`, log và breakpoint, chương trình còn có quan hệ synchronizes-with nào giữa writer và reader?" Nếu không, phải xem lại giao thức.

## 18. Khi nào được phép reordering và khi nào bị ràng buộc?

| Tình huống | Có thể giả định thread khác thấy theo source order không? | Lý do |
| --- | --- | --- |
| Hai field ordinary, writer và reader truy cập không đồng bộ | **Không** | Không có HB giữa các truy cập xung đột |
| Writer ghi dữ liệu rồi volatile flag; reader đọc thấy flag rồi lấy dữ liệu | **Có, theo phạm vi publication đã phân tích** | Volatile write/read tạo HB và HB có tính bắc cầu |
| Writer unlock monitor; reader lock cùng monitor trước khi đọc | **Có, với những ghi/đọc được bảo vệ phù hợp** | Unlock/lock cùng monitor có HB |
| Main gọi `start()` sau khi cấu hình dữ liệu; worker đọc dữ liệu khởi tạo | **Có cho các hành động xảy ra trước `start()`** | Thread start tạo HB |
| Main đọc kết quả sau `worker.join()` thành công | **Có cho những hành động trước khi worker kết thúc**, nếu không có writer xung đột khác | Thread termination/join HB |
| Main đọc kết quả sau `Future.get()` thành công | **Có theo memory consistency effects của Future** | Task actions HB caller sau get |
| Chỉ dùng `Thread.sleep(...)` để đợi writer | **Không** | Sleep không phải publish/acquire |
| `volatile count++` từ hai writer | **Không bảo đảm tổng số lần tăng** | Volatile ordering không tạo atomic read-modify-write |

Bảng nói về **bảo đảm của một giao thức cụ thể**, không phải bảo đảm mọi object sẽ bất biến mãi mãi sau khi được công bố. Nếu xuất hiện writer mới, phải xét tiếp các cạnh HB và bất biến tại thời điểm đọc.

## 19. Reordering, visibility và atomicity: ba vấn đề phải tách

| Khía cạnh | Câu hỏi | Công cụ/ví dụ |
| --- | --- | --- |
| Ordering | Các hành động có bị quan sát trái với thứ tự mà giao thức yêu cầu không? | Volatile publication; lock/unlock; HB |
| Visibility | Reader có bảo đảm quan sát một ghi trước đó hay không? | Cạnh HB qua volatile, monitor, start/join, future, queue |
| Atomicity | Một chuỗi thao tác có bị xen kẽ giữa chừng và phá invariant không? | `AtomicInteger.incrementAndGet()`, CAS đúng cách, critical section |
| Progress/liveness | Task có thể tiếp tục và kết thúc không? | Signaling, timeout, interruption, deadlock prevention |

Một chương trình có thể giải quyết ordering/visibility của một flag nhưng vẫn sai vì dùng `count++` từ nhiều writer. Hoặc có phép tăng atomic nhưng worker bị deadlock ở lock khác. **JMM không thay thế thiết kế coordination và lifecycle**, và điều kiện thread-safe phải được xét ở ranh giới nghiệp vụ cụ thể.

## 20. Cách phân tích một đoạn code đa luồng

Khi thấy một tình huống "đọc đúng khi chạy đơn luồng nhưng sai khi chạy nhiều thread", làm theo các bước:

1. **Liệt kê shared variables:** Những field nào có thể bị đọc/ghi từ ít nhất hai thread? Có mutable object hoặc collection nằm sau một tham chiếu được chia sẻ không?
2. **Đánh dấu các truy cập xung đột:** Có những lần đọc/ghi cùng biến với ít nhất một ghi không?
3. **Tìm cạnh HB:** Dùng volatile read/write cùng biến, monitor lock/unlock cùng đối tượng, thread start/join, future/queue/latch hoặc cơ chế khác có hợp đồng memory consistency không?
4. **Xác định invariant:** Chỉ cần publish một snapshot, hay cần atomic read-modify-write hoặc cập nhật đồng thời nhiều field?
5. **Thiết kế publication:** Dùng immutable snapshot + volatile reference, `synchronized`, atomic type, concurrent collection hoặc single-owner/message passing phù hợp.
6. **Đảm bảo progress:** Có nguy cơ spin mãi, block vô hạn, thiếu tín hiệu, deadlock hoặc bị cancel mà không giải phóng không?
7. **Kiểm tra theo hợp đồng**, sau đó mới stress test và đo performance. Test có thể tìm lỗi nhưng kết quả đúng ở vài lần chạy không tự chứng minh thiếu race.

Ví dụ nếu một ứng dụng có `volatile boolean done` nhưng đồng thời nhiều worker tăng một `int processed` ordinary, flag `done` chưa chắc sửa được việc các increment mất dữ liệu **giữa các worker**. Cần giải quyết atomicity hoặc ownership của `processed` trước khi dùng flag để công bố kết quả.

## 21. Câu hỏi tự kiểm tra

**1. Java compiler có được phép tối ưu hoặc hoán đổi hai thao tác mà không giữ nguyên thứ tự instruction máy như source không?**

Có, nếu kết quả và những ràng buộc phải tuân theo JMM được giữ. Không thể suy từ thứ tự source ra thứ tự instruction máy hoặc thứ tự quan sát của thread khác khi chưa có synchronization.

**2. Vì sao `data = 42; ready = true;` có thể không đủ cho thread khác?**

Nếu `data` và `ready` là các field ordinary chia sẻ không đồng bộ, không có HB bảo đảm reader nhìn thấy `ready` và payload đúng. Dùng volatile publication đúng giao thức, monitor hoặc cơ chế khác để thiết lập HB.

**3. Trong litmus test `x=1; r1=y` và `y=1; r2=x`, hai biến ordinary có thể cho cả `r1` và `r2` bằng `0` không?**

Có thể theo JMM. Nhưng chạy một lần chưa chắc quan sát kết quả đó; không được từ output mà khẳng định chính xác CPU hay JIT đã làm gì.

**4. Nếu cả `x` và `y` là volatile trong cùng litmus test, kết quả `0,0` có còn hợp lệ không?**

Không trong mô hình giả định đã nêu, vì các volatile action phải tuân thủ synchronization order nhất quán với program order và không thể tạo vòng thứ tự cần thiết cho hai phép đọc cùng thấy `0`.

**5. Vì sao `volatile count++` vẫn có thể mất lần tăng?**

Vì phép tăng là chuỗi đọc, tính và ghi. `volatile` không hợp nhất chúng thành một thao tác atomic. Dùng `AtomicInteger` hoặc khóa bảo vệ toàn bộ phép tăng.

**6. `Thread.join()` có sửa race giữa hai worker đã cùng đọc/ghi shared state khi chúng đang chạy không?**

Không. `join()` giúp main chờ và nhìn thấy công việc đã hoàn thành; nó không tạo mutual exclusion hoặc thứ tự đủ giữa hai worker đang cập nhật cùng dữ liệu trước đó.

**7. Cache coherence có nghĩa Java tự động nhìn thấy mọi ghi đúng thứ tự source ở tất cả thread không?**

Không. Cache coherence không phải hợp đồng JMM cấp ngôn ngữ cho nhiều địa chỉ và các hành động chưa đồng bộ. Cần phân tích HB theo Java.

**8. Có thể sửa một vòng polling flag ordinary bằng cách thêm `Thread.sleep()` không?**

Không. Sleep chỉ trì hoãn, không tạo cạnh publication giữa writer và reader. Hãy thay flag bằng volatile theo giao thức đúng hoặc dùng một primitive coordination.

**9. Dùng `record` và `final` có loại bỏ mọi rủi ro reordering với shared mutable state không?**

Không. Immutable value giúp giảm vấn đề, nhưng record chứa phần tử mutable vẫn có thể bị sửa. Cần khởi tạo đúng, tránh escape trong constructor, dùng safe publication và bảo vệ mọi mutation về sau.

## 22. Những điều cần nhớ

- **Reordering** là vấn đề về cách thực thi và quan sát hành động dưới các ràng buộc của JMM; source order không phải cam kết quan sát giữa các thread chưa đồng bộ.
- **Compiler/JIT, CPU và hệ thống bộ nhớ** đều có thể ảnh hưởng cách đọc/ghi biểu hiện, nhưng một kết quả bất thường không đủ chứng minh nguyên nhân duy nhất ở một tầng nào.
- **Happens-before** là công cụ chính để lập luận về thứ tự và visibility. Program order cộng synchronizes-with có tính bắc cầu.
- **`volatile`** thích hợp cho một số giao thức publication/flag. Nó **không** khiến phép `count++` thành atomic hay thay thế lock cho invariant nhiều bước.
- **`synchronized`**, thread `start/join`, `Future.get()` và queue/latch có memory consistency effects cụ thể; chọn công cụ theo hành vi cần bảo đảm.
- **Safe publication** và immutable data giúp tránh nhiều vấn đề khó; ngược lại, data race có thể tiềm ẩn ngay cả khi test thường xuyên cho kết quả đúng.
- **`sleep`, log, breakpoint hoặc cảm nhận về CPU cache** không phải giải pháp thay thế thiết kế đồng bộ.

Khi phân tích lỗi Java concurrency, hãy chỉ rõ **writer**, **reader**, các thao tác xung đột, ranh giới **atomicity** và đường **happens-before**. Cách lập luận này bền vững trước các thay đổi của JIT, CPU và hệ thống bộ nhớ hơn nhiều so với việc suy đoán thứ tự thực thi từ mã nguồn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-011">Giới hạn của volatile với compound actions</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của volatile với compound actions trong Java

Một field `volatile` có quy tắc đọc/ghi đặc biệt trong Java Memory Model (JMM): những thread khác có thể dựa vào cơ chế **visibility** và **ordering** để quan sát dữ liệu được công bố theo đúng giao thức. Tuy nhiên, `volatile` **không biến nhiều bước thành một thao tác nguyên tử**. Vì thế, dù `counter` được khai báo `volatile`, các biểu thức `counter++`, `counter += 2` hay `if (!claimed) claimed = true` vẫn có thể cho kết quả sai khi nhiều thread cùng chạy.

Bài này nói về **Java concurrency nói chung, trên Java 17+**, tập trung vào các giới hạn của volatile khi dùng với **compound actions** (thao tác ghép). Các cơ chế riêng của Spring nằm ngoài phạm vi.

Mục tiêu: hiểu đúng bảo đảm của volatile; nhận diện read–modify–write, check–then–act và bất biến nhiều field; chứng minh lost update; chọn Atomic*/CAS, `synchronized` hoặc `Lock` đúng yêu cầu; kiểm chứng bằng ví dụ và test có kiểm soát.

## 1. Volatile bảo đảm visibility và ordering, không bảo đảm compound atomicity

Cần tách ba khái niệm:

| Thuộc tính | Câu hỏi cần trả lời | Cơ chế liên quan |
| --- | --- | --- |
| **Visibility** | B có nhìn thấy ghi của A sau điểm công bố đúng không? | Volatile write/read, lock/unlock, join, Future... |
| **Ordering** | A và B có cạnh happens-before nào không? | Program order, synchronizes-with, bắc cầu |
| **Atomicity** | Một nhóm bước có được thực hiện như một đơn vị không bị thread khác xen vào không? | Atomic RMW, CAS, khóa, transaction phù hợp |

Một lần **ghi vào field volatile** *synchronizes-with* các lần **đọc field volatile đó tiếp sau theo synchronization order**. Nhờ program order và tính bắc cầu, thao tác ghi dữ liệu trước lần công bố có thể happens-before việc đọc dữ liệu sau khi reader thấy trạng thái đã được công bố.

Các phép **đọc/ghi đơn lẻ trên volatile field** có ngữ nghĩa nguyên tử của chính phép truy cập (kể cả `volatile long` và `volatile double`); điều đó khác hẳn với việc nguyên tử hóa một câu lệnh gồm đọc, tính toán rồi ghi. Volatile cũng không tự khóa một đoạn code.

### Ví dụ sử dụng đúng volatile để công bố dữ liệu

~~~java
// VolatilePublicationDemo.java
public class VolatilePublicationDemo {
    private static int payload;
    private static volatile boolean ready;

    public static void main(String[] args) throws InterruptedException {
        Thread reader = new Thread(() -> {
            while (!ready) {
                Thread.onSpinWait();
            }
            System.out.println("Payload: " + payload);
        });

        reader.start();
        payload = 42;
        ready = true;
        reader.join();
    }
}
~~~

~~~bash
javac VolatilePublicationDemo.java
java VolatilePublicationDemo
~~~

Khi các thread được lập lịch và chương trình hoàn thành bình thường, stdout:

~~~text
Payload: 42
~~~

Main thực hiện `payload = 42` trước `ready = true`. Reader quan sát `ready == true` qua volatile read rồi mới đọc `payload`. Chuỗi **program order → volatile synchronizes-with → program order**, kết hợp tính bắc cầu của happens-before, bảo đảm reader thấy dữ liệu đã công bố. Không có ghi cạnh tranh khác vào `payload`.

`Thread.onSpinWait()` chỉ là gợi ý cho vòng spin, **không tạo thêm cạnh đồng bộ** và không bảo đảm fairness/liveness. Ví dụ dùng spin để làm rõ JMM; ứng dụng thực tế thường dùng latch, queue hoặc future để tránh chờ bận.

## 2. Compound action gồm những gì?

**Compound action** là thao tác logic được xây từ nhiều hành động mà bất biến yêu cầu phải nhất quán. Các mẫu chính:

- **Read–modify–write:** `counter++`, `count += delta`, `balance -= amount`. Cần đọc giá trị, tính giá trị mới, rồi ghi.
- **Check–then–act:** `if (!reserved) { reserved = true; }`. Điều kiện có thể đổi sau khi kiểm tra và trước khi hành động.
- **Check–update:** `if (stock > 0) stock--;`. Hai thread có thể cùng thấy còn một món rồi cùng bán.
- **Cập nhật nhiều field:** `available--` và `sold++` cần giữ bất biến `available + sold == capacity`.
- **Read–compute–replace:** đọc một object/version, tính object mới rồi gán lại reference. Bản thân phép gán reference có thể atomic, nhưng chuỗi này vẫn có thể lost update.

Một lỗi phổ biến là thấy từng lần đọc/ghi volatile có bảo đảm visibility rồi suy ra **toàn bộ câu lệnh** đã an toàn. Hãy tìm **điểm nguyên tử của cả thao tác logic**, chứ không chỉ của field.

## 3. Volatile counter++ vẫn có lost update

Giả sử `counter = 0`. Hai thread T1 và T2 đều muốn tăng đúng một lần:

| Thứ tự logic | T1 | T2 |
| --- | --- | --- |
| 1 | Đọc `counter = 0` | |
| 2 | | Đọc `counter = 0` |
| 3 | Tính `0 + 1` | |
| 4 | | Tính `0 + 1` |
| 5 | Ghi volatile `counter = 1` | |
| 6 | | Ghi volatile `counter = 1` |

Kết quả **1 thay vì 2**. Mỗi truy cập volatile đơn lẻ vẫn đúng JMM; lỗi là hai thread đều tính từ phiên bản cũ rồi ghi đè nhau.

### Ví dụ Java 17+ tái hiện xác định

Dùng `CountDownLatch` chỉ để điều phối cho **cả hai thread đều đọc trước khi ghi**:

~~~java
// VolatileLostUpdateDemo.java
import java.util.concurrent.CountDownLatch;

public class VolatileLostUpdateDemo {
    private static volatile int counter;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable increment = () -> {
            int oldValue = counter;
            bothRead.countDown();
            try {
                allowWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            counter = oldValue + 1;
        };

        Thread a = new Thread(increment);
        Thread b = new Thread(increment);
        a.start();
        b.start();

        bothRead.await();
        allowWrite.countDown();
        a.join();
        b.join();

        System.out.println("Expected: 2");
        System.out.println("Actual: " + counter);
    }
}
~~~

~~~bash
javac VolatileLostUpdateDemo.java
java VolatileLostUpdateDemo
~~~

Stdout:

~~~text
Expected: 2
Actual: 1
~~~

`bothRead.await()` chỉ hoàn thành khi cả hai thread đã lưu `0` trong `oldValue`. Sau đó, cả hai ghi `oldValue + 1`, tức `1`. Main `join()` cả hai rồi mới đọc kết quả. Trong code thật không có latch ép lịch chạy, lỗi có thể hiếm gặp; hiếm gặp **không phải an toàn**.

Tương tự, `++counter`, `counter += 3` hoặc `counter = counter * 2` đều có read–modify–write. `++counter` và `counter++` khác giá trị của biểu thức, nhưng **không khác về tính nguyên tử**.

## 4. Dùng AtomicInteger cho một phép read–modify–write nguyên tử

`AtomicInteger` cung cấp `incrementAndGet()`, `getAndIncrement()`, `addAndGet(delta)`, `compareAndSet(expected, update)` và các phương thức cập nhật nguyên tử khác.

~~~java
// AtomicCounterDemo.java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger count = new AtomicInteger();

        Runnable increment = () -> count.incrementAndGet();
        Thread a = new Thread(increment);
        Thread b = new Thread(increment);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Atomic count: " + count.get());
    }
}
~~~

~~~bash
javac AtomicCounterDemo.java
java AtomicCounterDemo
~~~

Stdout:

~~~text
Atomic count: 2
~~~

Hai lần tăng được thực hiện như hai cập nhật nguyên tử trên **cùng instance** `AtomicInteger`. `incrementAndGet()` trả giá trị **sau tăng**; `getAndIncrement()` trả giá trị **trước tăng**. Chọn đúng phương thức theo nhu cầu.

**Tuy nhiên, các phương thức atomic riêng lẻ không làm mọi chuỗi gọi của bạn nguyên tử.** Ví dụ:

~~~java
// Mã minh họa: seats là AtomicInteger đã khai báo.
if (seats.get() > 0) {
    seats.decrementAndGet();
}
~~~

Nếu `seats == 1`, cả hai thread có thể cùng đọc `1` và đều giảm, khiến kết quả `-1`. `get()` và `decrementAndGet()` đều có bảo đảm riêng nhưng điều kiện **không được gắn nguyên tử với update**.

Một CAS loop phù hợp hơn nếu chỉ cần quản lý số ghế:

~~~java
// Phương thức minh họa trong class có field AtomicInteger seats.
boolean reserve() {
    while (true) {
        int current = seats.get();
        if (current <= 0) {
            return false;
        }
        if (seats.compareAndSet(current, current - 1)) {
            return true;
        }
        // Có writer khác vừa thay đổi: đọc lại, kiểm tra lại.
    }
}
~~~

`compareAndSet` kiểm tra giá trị dự kiến và ghi giá trị mới **trong một bước nguyên tử**. Nếu thất bại do cạnh tranh, đoạn code đọc lại trạng thái và đánh giá lại điều kiện. Đây là **mã minh họa**, không phải file Java chạy độc lập.

## 5. Check–then–act: volatile boolean không đủ để claim một lần

Ví dụ chỉ cho một thread nhận một tài nguyên:

~~~java
// Minh họa trong class, chưa thread-safe về mặt logic:
private volatile boolean claimed;

boolean claim() {
    if (!claimed) {
        claimed = true;
        return true;
    }
    return false;
}
~~~

Có lịch chạy T1 đọc false, T2 cũng đọc false; sau đó cả hai cùng ghi true và cùng trả true. Không cần lỗi cache: tại **thời điểm đọc**, cả hai thực sự có thể đọc false.

Dùng `AtomicBoolean.compareAndSet(false, true)`:

~~~java
// AtomicClaimDemo.java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicClaimDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicBoolean claimed = new AtomicBoolean();
        AtomicInteger winners = new AtomicInteger();
        CountDownLatch begin = new CountDownLatch(1);

        Runnable attempt = () -> {
            try {
                begin.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (claimed.compareAndSet(false, true)) {
                winners.incrementAndGet();
            }
        };

        Thread a = new Thread(attempt);
        Thread b = new Thread(attempt);
        a.start();
        b.start();
        begin.countDown();
        a.join();
        b.join();

        System.out.println("Successful claims: " + winners.get());
    }
}
~~~

~~~bash
javac AtomicClaimDemo.java
java AtomicClaimDemo
~~~

Stdout:

~~~text
Successful claims: 1
~~~

Ở đây điểm nguyên tử là việc **đọc trạng thái hiện tại và chuyển false → true** như một thao tác. Chỉ một CAS thắng khi không có code khác đặt lại trạng thái. Khi có reset, cần thiết kế thêm hợp đồng sở hữu/trạng thái và tránh ABA nếu giá trị có thể thay đổi rồi quay lại như ban đầu.

## 6. Nhiều field: volatile không tạo snapshot hay transaction

Giả sử có hai field:

~~~java
// Mã minh họa trong class:
private volatile int available = 10;
private volatile int sold;

// Writer:
available--;
sold++;

// Reader:
int a = available;
int s = sold;
~~~

Bất biến cần giữ là `available + sold == 10`. Reader có thể nhìn thấy `available == 9`, `sold == 0` ở giữa hai lần ghi. Ngoài ra, nếu nhiều writer cùng dùng `--` và `++`, còn có khả năng mất cập nhật.

Một giải pháp trực tiếp là đặt **đoạn kiểm tra–cập nhật và đoạn đọc snapshot** dưới **cùng monitor**:

~~~java
// SynchronizedInventoryDemo.java
public class SynchronizedInventoryDemo {
    static class Inventory {
        private int available = 1;
        private int sold;

        synchronized boolean sell() {
            if (available <= 0) {
                return false;
            }
            available--;
            sold++;
            return true;
        }

        synchronized String snapshot() {
            return "Available: " + available + ", sold: " + sold;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Inventory stock = new Inventory();

        Thread a = new Thread(stock::sell);
        Thread b = new Thread(stock::sell);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println(stock.snapshot());
    }
}
~~~

~~~bash
javac SynchronizedInventoryDemo.java
java SynchronizedInventoryDemo
~~~

Stdout:

~~~text
Available: 0, sold: 1
~~~

Mỗi `sell()` lấy monitor của **cùng object `stock`** và bảo vệ toàn bộ check–update. `snapshot()` dùng monitor đó, vì thế không thể đọc ở giữa hai lần cập nhật. Các field không cần volatile nếu mọi truy cập chia sẻ đều tuân thủ đúng giao thức.

Với `ReentrantLock`, có thể tổ chức tương tự, nhưng phải giải phóng khóa trong `finally` sau khi lấy khóa thành công:

~~~java
// Mã minh họa: lock là ReentrantLock đã khai báo, các field đã khai báo.
lock.lock();
try {
    if (available > 0) {
        available--;
        sold++;
    }
} finally {
    lock.unlock();
}
~~~

Khóa đem lại cả mutual exclusion và hiệu ứng bộ nhớ lock/unlock. **Mọi đường đọc và ghi liên quan đến bất biến phải dùng cùng cơ chế.** Chỉ khóa writer rồi để reader đọc hai field riêng ngoài khóa vẫn có thể quan sát trạng thái trung gian.

## 7. Volatile reference: công bố snapshot khác với cập nhật snapshot

Một reference `volatile` có thể giúp **công bố một object bất biến**:

~~~java
// Mã minh họa trong class Java 17+:
record Inventory(int available, int sold) {}

private volatile Inventory current = new Inventory(10, 0);

Inventory snapshot() {
    return current;
}
~~~

Reader chỉ cần đọc `current` một lần rồi sử dụng cả hai thành phần trên **cùng một instance**. Tuy nhiên, nếu hai writer cùng làm:

~~~java
// Không an toàn với nhiều writer:
Inventory old = current;
current = new Inventory(old.available() - 1, old.sold() + 1);
~~~

họ có thể cùng đọc một phiên bản cũ và ghi đè kết quả của nhau. Volatile reference là một **điểm publication**, không tự tạo atomic read–modify–write.

### AtomicReference với immutable state

~~~java
// AtomicSnapshotDemo.java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicSnapshotDemo {
    record Inventory(int available, int sold) {}

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Inventory> state =
            new AtomicReference<>(new Inventory(2, 0));

        Runnable sell = () -> state.updateAndGet(old ->
            old.available() == 0
                ? old
                : new Inventory(old.available() - 1, old.sold() + 1)
        );

        Thread a = new Thread(sell);
        Thread b = new Thread(sell);
        a.start();
        b.start();
        a.join();
        b.join();

        Inventory result = state.get();
        System.out.println("Available: " + result.available());
        System.out.println("Sold: " + result.sold());
        System.out.println("Total: " +
            (result.available() + result.sold()));
    }
}
~~~

~~~bash
javac AtomicSnapshotDemo.java
java AtomicSnapshotDemo
~~~

Stdout:

~~~text
Available: 0
Sold: 2
Total: 2
~~~

`AtomicReference.updateAndGet` cập nhật **một reference** nguyên tử; nhờ object bất biến, lần đọc `state.get()` trả về snapshot nhất quán. Hàm updater **có thể được gọi nhiều lần khi CAS retry**. Không đặt side effect như gửi email, thu tiền, ghi database trong lambda rồi giả định nó thực thi đúng một lần. Khi dữ liệu nằm ở database hoặc hệ thống ngoài JVM, CAS trong bộ nhớ không thay thế transaction của hệ thống đó.

Record dùng các thành phần `int` nên bản thân snapshot ở ví dụ là bất biến. Nếu chứa mảng/list mutable, cần xét **deep immutability**, không kết luận an toàn chỉ từ tên `record`.

### Volatile array không làm từng element atomic

~~~java
// Mã minh họa trong class:
private volatile int[] counters = new int[2];
~~~

Volatile áp dụng cho **field reference `counters`**, không khiến `counters[0]++` thành nguyên tử và không tự gắn ngữ nghĩa volatile cho từng phần tử. `volatile Settings settings` cũng không làm mọi field mutable trong `Settings` tự nhiên thread-safe. Nếu muốn cập nhật phần tử nguyên tử, cân nhắc `AtomicIntegerArray` hoặc khóa phù hợp.

## 8. CAS và các giới hạn cần biết

CAS (*compare-and-set*) cho phép kiểm tra giá trị dự kiến và cập nhật tại một điểm nguyên tử, nhưng:
- **Contention:** CAS có thể thất bại khi giá trị thay đổi; retry nhiều lần có thể tốn CPU.
- **Nhiều biến:** CAS một biến không tạo transaction cho các biến hoặc tài nguyên khác.
- **Updater lambda:** `updateAndGet`/`getAndUpdate` có thể tính lại, vì vậy hàm tính nên thuần, tránh side effect.
- **ABA:** trạng thái chuyển A → B → A, CAS chỉ so sánh thấy A và không biết đã có thay đổi trung gian. Khi việc nhận biết phiên bản quan trọng, có thể dùng phiên bản/stamp như `AtomicStampedReference`.
- **Liveness:** vòng retry không giới hạn có thể khiến một thread bị starvation dưới contention; cần cân nhắc yêu cầu thời gian chờ và tiến triển.

Không phải mọi CAS đơn giản đều gặp ABA; vấn đề cần xem xét khi **logic dựa vào việc giá trị không từng thay đổi**, chứ không chỉ vào giá trị hiện tại.

## 9. So sánh và chọn công cụ phù hợp

| Công cụ | Phù hợp | Không tự bảo đảm |
| --- | --- | --- |
| `volatile` | Flag/publication của dữ liệu với happens-before rõ ràng | Compound atomicity, multi-field transaction |
| `AtomicInteger`, `AtomicLong` | Tăng/giảm/CAS nguyên tử cho một biến | Chuỗi `get()` rồi `set()` không tự atomic |
| `AtomicBoolean` | Chuyển một trạng thái bằng CAS | Giao thức nhiều trạng thái hoặc reset phức tạp |
| `AtomicReference<T>` | CAS/swap một immutable snapshot | Các sửa đổi mutable bên trong object; I/O bên ngoài |
| `synchronized` | Check–update, invariant nhiều bước/nhiều field | Phải dùng đúng một monitor cho tất cả đường truy cập |
| `ReentrantLock` | Tương tự khóa, thêm `tryLock`/timeout/interruptible acquisition | Phải unlock trong finally, xem xét deadlock |
| `LongAdder` | Tổng số liệu thống kê tăng nhiều dưới contention | `sum()` không phải snapshot nguyên tử lúc cập nhật đồng thời |

Nguyên tắc:
1. Viết ra **bất biến** (ví dụ không oversell, tổng tiền không đổi, một winner).
2. Xác định **điểm cập nhật nguyên tử** cần có.
3. Chọn một atomic operation, CAS loop, hoặc lock giữ bất biến qua các bước.
4. Đảm bảo **reader** dùng cùng giao thức hoặc đọc một immutable snapshot nhất quán.
5. Sau khi đúng, mới đánh giá contention, độ trễ và cách dừng/retry.

## 10. Happens-before không thay thế atomicity

Một chương trình có thể dùng toàn bộ truy cập volatile và không mắc *data race* kiểu truy cập thường không có happens-before, nhưng vẫn mắc **race condition logic**. Ví dụ hai thread làm `volatile counter++`: từng volatile access có quy tắc ordering của JMM, song hai chuỗi đọc–tính–ghi vẫn bị xen kẽ.

Tương tự, main gọi `join()` sau khi hai worker làm phép tăng lỗi chỉ được bảo đảm **nhìn thấy kết quả cuối do chúng tạo ra**. Nếu chúng làm mất một lần tăng, `join()` không thể biến kết quả sai thành đúng.

Đừng dùng `Thread.sleep(...)`, `Thread.yield()` hoặc `Thread.onSpinWait()` để “sửa” phép tăng. Chúng không tạo atomicity cho hành động ghép. Thực nghiệm chạy đúng nhiều lần cũng không chứng minh mọi lịch xen kẽ hợp lệ đều đúng.

## 11. Checklist chẩn đoán và kiểm thử

1. **Xác định bất biến:** `count == số lần tăng`, `stock >= 0`, `available + sold == capacity`, hoặc chỉ một claim thành công.
2. **Tách thành từng hành động:** đọc giá trị, kiểm tra, tính toán, ghi; có khoảng trống để thread khác chen vào không?
3. **Vẽ lịch xấu nhất:** cả hai thread cùng thấy giá trị cũ trước lần ghi nào.
4. **Kiểm tra primitive:** nếu `AtomicInteger`, có gọi `get` rồi `set` riêng không? Nếu khóa, có dùng cùng monitor và cùng protocol không?
5. **Kiểm tra reader:** có thể thấy nửa trạng thái cũ, nửa mới không?
6. **Dùng bài test có điều phối:** `CountDownLatch`/barrier giúp ép lịch đọc–ghi xấu. Kiểm tra kết quả bằng assertion, không suy luận từ log/timing.
7. **Kiểm tra retry và side effect:** CAS loop có thể quay nhiều lần; updater có idempotent/pure không?
8. **Đo sau khi sửa:** đánh giá contention và throughput nhưng không bỏ bảo đảm tính đúng.

### Lỗi thường gặp

- “`volatile counter++` an toàn vì tất cả read/write là volatile.” → Sai; phép tăng ghép vẫn bị chen vào.
- “`AtomicInteger.get()` và `decrementAndGet()` đều atomic nên cả hai lời gọi atomic.” → Sai; không có transaction quanh cặp lời gọi.
- “Hai field volatile luôn có snapshot nhất quán.” → Sai; reader có thể đọc trạng thái trung gian.
- “Volatile reference làm object được trỏ tới thread-safe.” → Sai nếu object vẫn bị sửa mutable cạnh tranh.
- “`join()` sau cùng sửa được lost update.” → Sai; chỉ chờ và cung cấp visibility/ordering.
- “CAS updater được thực thi đúng một lần.” → Sai; có thể chạy lại khi retry.
- “Thêm `sleep` làm hết race.” → Không có bảo đảm JMM như vậy.

## 12. Tự kiểm tra

**1.** Hai thread gọi `counter++` trên `volatile int counter` từ 0. Kết quả có thể là 1 không? **Có.** Hai thread cùng đọc 0 rồi cùng ghi 1.

**2.** `if (seats.get() > 0) seats.decrementAndGet()` với `AtomicInteger` có an toàn khi stock=1 không? **Không.** Kiểm tra và giảm là hai thao tác riêng, cần CAS loop hoặc lock.

**3.** Hai thread cùng làm `if (!claimed) claimed = true` trên volatile boolean. Có thể cả hai cùng tin rằng claim thành công không? **Có.** Dùng `compareAndSet(false, true)` cho transition nguyên tử.

**4.** Dùng `volatile Inventory current` với immutable record là đủ cho nhiều writer làm read–compute–replace không? **Chưa đủ.** Publication của từng reference atomic nhưng compound update có thể lost update; dùng CAS hoặc khóa.

**5.** `AtomicReference.updateAndGet` có thể gọi updater nhiều lần? **Có**, khi tranh chấp và phải retry CAS.

**6.** Tại sao volatile flag dùng được để công bố một payload, nhưng volatile ++ không đủ? **Vì** flag cung cấp cạnh happens-before cho việc công bố đã hoàn thành, còn phép ++ cần **atomicity của cả read–modify–write**.

**Ghi nhớ:** `volatile` giải quyết **visibility và ordering của các truy cập cụ thể**; compound actions cần **atomicity tương ứng với bất biến**. Dùng phương thức atomic/CAS hoặc một giao thức khóa đủ phạm vi, và kiểm chứng cả đường đọc lẫn đường ghi.

### Tài liệu tham khảo

- Java Language Specification 17, `17.4 Java Memory Model: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
- JDK 17 `AtomicInteger`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html
- JDK 17 `AtomicBoolean`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicBoolean.html
- JDK 17 `AtomicReference`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-012">Double-checked locking và điều kiện dùng volatile</a>

<details>
<summary>Xem chi tiết</summary>

# Double-checked locking và điều kiện dùng volatile trong Java

## 1. Mục tiêu bài học

Sau bài học, bạn có thể:

- Hiểu **double-checked locking (DCL)** là gì, giải thích hai lần kiểm tra `null` giải quyết những vấn đề khác nhau.
- Phân tích vai trò của **`synchronized`**, **`volatile`**, quan hệ *happens-before*, visibility, ordering và **safe publication** trong Java Memory Model (JMM).
- Viết đúng một bộ khởi tạo lazy sử dụng DCL trên **Java 17+**, biết lý do mẫu DCL không có `volatile` không được xem là an toàn theo JMM.
- Phân biệt **tránh tạo trùng đối tượng** với **công bố đối tượng đã xây dựng đầy đủ**; nhận ra vì sao chỉ `volatile` hoặc chỉ một lần kiểm tra ngoài khóa không đủ.
- Xác định **điều kiện áp dụng DCL**, giới hạn của mẫu thiết kế và những phương án đơn giản hơn như khởi tạo sớm, **initialization-on-demand holder**, enum và truy cập `synchronized`.
- Không nhầm DCL với bảo vệ trạng thái **mutable** trong đối tượng sau khi khởi tạo, hoặc cơ chế singleton trên nhiều JVM/process.

Phạm vi là **Java concurrency nói chung**. Bài không đề cập singleton container, lifecycle, dependency injection hoặc quản lý bean thuộc Spring.

## 2. Bài toán: lazy initialization dưới cạnh tranh

Giả sử một đối tượng dịch vụ có chi phí tạo đáng kể và chỉ cần khi được sử dụng lần đầu:

~~~java
// Đoạn minh họa đơn giản; chưa thread-safe:
private static Resource instance;

public static Resource getInstance() {
    if (instance == null) {
        instance = new Resource();
    }
    return instance;
}
~~~

Nếu hai thread cùng thấy `instance == null`, cả hai có thể tạo đối tượng. Ngoài chuyện **tạo trùng**, truy cập trường thông thường thiếu đồng bộ hóa còn có thể vi phạm yêu cầu **visibility** và **safe publication**.

Yêu cầu điển hình của một lazy singleton trong **một classloader của một JVM**:

1. Chưa tạo tài nguyên khi class được load, chỉ tạo ở lần sử dụng đầu tiên.
2. Dưới cạnh tranh, **chỉ một lần khởi tạo thành công** được công bố làm instance dùng chung.
3. Thread đọc được instance đã **được khởi tạo đúng**, thấy các ghi cần thiết từ quá trình xây dựng.
4. Sau khởi tạo, các lần gọi phổ biến không phải tranh chấp monitor mỗi lần.
5. Nếu constructor thất bại, không công bố một tham chiếu chưa có đối tượng hợp lệ.

DCL xuất phát từ yêu cầu **tránh lấy khóa ở đường truy cập nhanh sau khi khởi tạo**, trong khi vẫn khóa và kiểm tra lại khi thực sự cần tạo đối tượng.

Đừng dùng DCL chỉ vì có từ “singleton”: việc khởi tạo tài nguyên có side effect, thay đổi cấu hình, hủy/khởi tạo lại hoặc có nhiều chủ sở hữu cần hợp đồng riêng trước khi chọn mẫu này.

## 3. DCL đúng trong Java 17+

Mẫu thông dụng dùng **một trường static volatile**, **khóa cố định** và **hai lần kiểm tra giá trị null**:

~~~java
public final class LazyResource {
    private static volatile LazyResource instance;

    private final int value;

    private LazyResource() {
        value = 42;
    }

    public static LazyResource getInstance() {
        LazyResource result = instance; // Đọc volatile một lần.
        if (result == null) {           // Check 1: đường nhanh.
            synchronized (LazyResource.class) {
                result = instance;      // Đọc lại volatile dưới khóa.
                if (result == null) {   // Check 2: tránh tạo trùng.
                    result = new LazyResource();
                    instance = result;  // Ghi volatile: công bố instance.
                }
            }
        }
        return result;
    }

    public int value() {
        return value;
    }
}
~~~

Đây là **lớp hoàn chỉnh có thể biên dịch**, nhưng không phải ứng dụng chạy độc lập vì chưa có `main()`. Các ví dụ chương trình có `main()` xuất hiện ở phần tiếp theo.

Phân tích từng phần:

- **`private static volatile LazyResource instance`**: mọi thread truy cập đường DCL phải đọc/ghi cùng một trường volatile. Volatile tham gia đảm bảo visibility và ordering cần thiết cho việc công bố đối tượng trên JMM hiện đại.
- **`LazyResource result = instance`**: lấy snapshot từ đọc volatile. Dùng biến cục bộ để không phải đọc trường volatile nhiều lần trên đường thường gặp; đó là tối ưu nhỏ và làm luồng kiểm tra dễ hiểu.
- **`if (result == null)` đầu tiên**: nếu đã công bố instance, trả thẳng tham chiếu mà không phải lấy monitor.
- **`synchronized (LazyResource.class)`**: chỉ một thread tại một thời điểm có thể thực hiện phần khởi tạo được bảo vệ. Khóa này gắn với chính lớp, ổn định và được mọi thread dùng chung.
- **`result = instance` và `if (result == null)` bên trong**: sau khi một thread chờ lock, thread khác có thể đã tạo instance. Đọc lại volatile và kiểm tra lần hai để không tạo lại.
- **`new LazyResource()` trước `instance = result`**: constructor hoàn thành bình thường trước khi code thực hiện volatile write công bố tham chiếu. Thread khác quan sát tham chiếu từ volatile read tiếp theo có quan hệ happens-before phù hợp.
- **`return result`**: trả tham chiếu đã đọc hoặc khởi tạo, không cần thêm lần đọc volatile.

### Luồng cạnh tranh với hai thread

Giả sử cả A và B nhìn thấy `null` tại check 1:

~~~text
A: đọc instance == null
B: đọc instance == null
A: lấy monitor LazyResource.class
B: chờ monitor
A: đọc lại instance == null
A: new LazyResource() hoàn thành
A: ghi volatile instance = object
A: rời synchronized
B: lấy monitor
B: đọc lại instance -> object
B: check 2 thấy khác null, KHÔNG tạo thêm
B: trả cùng object
~~~

Nếu B vào khóa trước thì vai trò đảo lại; kết luận không đổi. Thread có thể chạy theo nhiều thứ tự, nhưng quy tắc khóa và check 2 giữ đúng việc tạo instance dùng chung.

DCL giải quyết **lazy initialization**. Nó không mặc nhiên làm mọi phương thức trong `LazyResource` thread-safe. Nếu đối tượng có trạng thái mutable sau khởi tạo, phần trạng thái ấy vẫn cần giao thức đồng bộ phù hợp.

## 4. Ba vấn đề khác nhau: mutual exclusion, publication và logic

Đừng đánh đồng những cơ chế này:

| Cơ chế | Nó bảo vệ điều gì? | Một mình có đủ cho DCL không? |
| --- | --- | --- |
| Check 1 ngoài khóa | Bỏ qua monitor khi instance đã sẵn sàng | Không: có thể hai thread cùng thấy null |
| `synchronized` quanh tạo instance | Mutual exclusion và thứ tự giữa các thread sử dụng **cùng khóa** | Không, nếu đường đọc nhanh ngoài khóa vẫn thiếu safe publication |
| Check 2 trong khóa | Không tạo trùng sau khi đã phải chờ thread khác tạo | Không, nếu reference công bố ngoài khóa không an toàn |
| `volatile instance` | Visibility, ordering và publish/read reference qua volatile | Không, nếu không bảo vệ quyết định “nếu null thì tạo” |
| `final` trong Resource | Ngữ nghĩa khởi tạo trường final khi đối tượng được xây dựng đúng | Không thay thế quy tắc tạo một instance và công bố đúng cho mọi trạng thái |

**Công thức của mẫu đúng:** volatile reference **và** critical section **và** hai lần kiểm tra. Bỏ bất kỳ phần nào phải có lập luận khác thay thế, chứ không thể dựa vào kết quả chạy thử.

## 5. Vì sao thiếu volatile là nguy hiểm?

Một phiên bản cũ thường thấy:

~~~java
// Mã minh họa lỗi, không dùng trong code concurrent:
private static Resource instance; // Thiếu volatile!

public static Resource getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) {
                instance = new Resource();
            }
        }
    }
    return instance;
}
~~~

Đây là **mã giả minh họa nằm trong lớp tên Singleton**; `Resource` là kiểu giả định. Nó không phải file Java hoàn chỉnh có thể tự chạy.

Vấn đề: **đường đọc `instance` đầu tiên không đi qua cùng monitor**, cũng không đọc volatile. Vì thế việc thread tạo đối tượng đã ghi reference và việc thread khác đọc reference ngoài khóa **không được sắp thứ tự bằng happens-before** theo cách cần thiết. Thread đọc có thể quan sát dữ liệu không được công bố an toàn.

### “Thấy tham chiếu trước khi khởi tạo xong” nghĩa là gì?

Ta thường diễn tả bằng ba bước khái niệm:

~~~text
1. Cấp phát bộ nhớ cho Resource.
2. Gán dữ liệu/trạng thái khởi tạo của Resource.
3. Công bố địa chỉ đối tượng qua instance.
~~~

Đây **không phải ba câu lệnh bytecode cố định** của mọi constructor/JIT. Nó là cách diễn giải về quyền quan sát các hiệu ứng bộ nhớ. Khi thiếu ràng buộc JMM, một thread khác có thể nhìn thấy reference qua truy cập không đồng bộ nhưng không có bảo đảm nó quan sát đầy đủ trạng thái đã được thiết lập trong thread tạo.

Điều này đặc biệt quan trọng nếu Resource có các trường **không final** được constructor gán. JMM có các bảo đảm riêng cho **final fields** của đối tượng được xây dựng đúng cách, nhưng chúng không biến đoạn DCL thiếu volatile thành mẫu an toàn tổng quát, cũng không đảm bảo mọi trạng thái phụ thuộc và mọi yêu cầu singleton.

Không nên cố tạo một demo “lúc nào cũng in giá trị 0” để chứng minh lỗi này. Những lỗi do **publication/ordering** có thể không xuất hiện trong phần lớn lần chạy trên một CPU/JVM nhất định. **Thiếu happens-before đã đủ để bác bỏ tính đúng đắn**; việc một bài test pass không tạo ra bảo đảm JMM.

### Volatile write/read thiết lập happens-before

Theo quy tắc Java Memory Model, một **volatile write** tới một biến *happens-before* với các **volatile read kế tiếp của chính biến đó** trong synchronization order.

Khi thread A hoàn thành việc tạo object và ghi `instance = result` vào trường volatile:

1. Những ghi trước volatile write trong A, bao gồm khởi tạo object đúng cách, được sắp trước việc công bố theo program order.
2. Thread B đọc reference không-null từ cùng trường volatile sau đó có thứ tự nhớ cần thiết để thấy các hiệu ứng được công bố.
3. Những hành động sau volatile read trong B có thể dựa vào trạng thái được khởi tạo trước volatile write.

Diễn đạt theo sự kiện:

~~~text
Thread A                         Thread B
---------                        ---------
new Resource()
  constructor khởi tạo fields
volatile write: instance = r
          └──── happens-before ──> volatile read: r = instance
                                   đọc r.fields đã công bố
~~~

**Volatile ở trường chứa tham chiếu**, không phải cứ gắn volatile vào một trường bất kỳ trong Resource là đủ. Việc đặt `instance` volatile là phần thiết yếu của mẫu DCL chuẩn trong Java 17+.

## 6. Vì sao phải kiểm tra null lần hai?

Kể cả `instance` đã là volatile, cả A và B vẫn có thể cùng đọc `null` trước khi có thread nào ghi một tham chiếu hợp lệ. Nếu code chỉ kiểm tra ngoài khóa, rồi tạo đối tượng vô điều kiện khi vào khóa, sẽ có **hai lần tạo**.

Phiên bản thiếu check 2:

~~~java
// Minh họa lỗi logic:
if (instance == null) {
    synchronized (LockHolder.class) {
        instance = new Resource(); // Chưa kiểm tra lại sau khi chờ khóa.
    }
}
~~~

Đây là đoạn code minh họa, với `instance`, `LockHolder` và `Resource` cần được định nghĩa trong chương trình thật.

Vấn đề không nhất thiết là data race: ngay cả với reference volatile, các thao tác đọc/ghi riêng lẻ đúng hợp đồng bộ nhớ, **chuỗi check rồi khởi tạo vẫn bị race condition**. Check 2 biến việc kiểm tra và quyết định tạo thành một critical section thống nhất.

### Ví dụ chạy được: thiếu check 2 vẫn khởi tạo hai lần

Dùng latch để cả hai worker đều vượt qua check ngoài trước khi thread nào được tạo object. Nhờ vậy ví dụ có kết quả xác định, tránh suy luận từ thời gian `sleep()`.

Tệp **MissingSecondCheckDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class MissingSecondCheckDemo {
    private static volatile Resource instance;
    private static final CountDownLatch bothSawNull = new CountDownLatch(2);
    private static final AtomicInteger created = new AtomicInteger();

    static final class Resource {
        final int id;

        Resource(int id) {
            this.id = id;
        }
    }

    static Resource getInstance() throws InterruptedException {
        Resource result = instance;
        if (result == null) {
            bothSawNull.countDown();
            bothSawNull.await();

            synchronized (MissingSecondCheckDemo.class) {
                // Sai: thiếu kiểm tra lại instance ở trong khóa.
                result = new Resource(created.incrementAndGet());
                instance = result;
            }
        }
        return result;
    }

    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Resource> first =
                    pool.submit(MissingSecondCheckDemo::getInstance);
            Future<Resource> second =
                    pool.submit(MissingSecondCheckDemo::getInstance);

            Resource one = first.get();
            Resource two = second.get();

            System.out.println("constructed = " + created.get());
            System.out.println("same instance = " + (one == two));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 MissingSecondCheckDemo.java
java MissingSecondCheckDemo
~~~

Stdout:

~~~text
constructed = 2
same instance = false
~~~

**Vì sao chắc chắn?** Thread đầu đã đọc null phải chờ `bothSawNull` đạt 0 trước khi tạo object; thread thứ hai vì vậy cũng đọc null. Sau đó hai thread lần lượt lấy monitor, **nhưng không kiểm tra lại**, nên mỗi thread tạo một Resource khác nhau. `result` là biến cục bộ lưu riêng object mỗi thread vừa tạo, vì thế hai kết quả từ Future là hai reference khác nhau.

- `volatile` chỉ giữ hợp đồng bộ nhớ cho reference.
- `synchronized` đảm bảo mỗi lần khởi tạo ở trong monitor không diễn ra cùng lúc.
- Thiếu check 2 vẫn cho phép **hai lần khởi tạo nối tiếp** sau khi cả hai đã đi qua check 1.

Trong ví dụ có latch, đó là **công cụ kiểm soát lịch test để tái hiện lỗi**, không phải một phần cần dùng trong implementation DCL đúng.

## 7. Ví dụ DCL hoàn chỉnh có thể chạy trên Java 17+

Dưới đây là bản runnable áp dụng cả ba điều kiện: volatile, khóa, kiểm tra lại null. Ví dụ dùng các thread đồng thời nhưng kết quả cuối vẫn xác định.

Tệp **VolatileDclDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class VolatileDclDemo {
    private static volatile Resource instance;
    private static final AtomicInteger constructions = new AtomicInteger();

    static final class Resource {
        final int value;

        Resource(int value) {
            this.value = value;
            constructions.incrementAndGet();
        }
    }

    static Resource getInstance() {
        Resource result = instance;
        if (result == null) {
            synchronized (VolatileDclDemo.class) {
                result = instance;
                if (result == null) {
                    result = new Resource(42);
                    instance = result;
                }
            }
        }
        return result;
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Resource> first = new AtomicReference<>();
        AtomicReference<Resource> second = new AtomicReference<>();

        Thread a = new Thread(() -> {
            try {
                start.await();
                first.set(getInstance());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "caller-a");

        Thread b = new Thread(() -> {
            try {
                start.await();
                second.set(getInstance());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "caller-b");

        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();

        System.out.println("constructed = " + constructions.get());
        System.out.println("same instance = " + (first.get() == second.get()));
        System.out.println("value = " + first.get().value);
    }
}
~~~

~~~bash
javac -encoding UTF-8 VolatileDclDemo.java
java VolatileDclDemo
~~~

Stdout trong điều kiện chạy bình thường, không có yêu cầu interrupt từ ngoài:

~~~text
constructed = 1
same instance = true
value = 42
~~~

**Lập luận chính xác:**

1. `start.await()` chỉ điều phối điểm bắt đầu của hai worker; nó không bắt buộc hai thread cùng thấy null. DCL phải đúng dù một thread tới trước hay cả hai tới cùng lúc.
2. Cả hai đọc `instance` thông qua volatile.
3. Thread giành monitor trước tạo object một lần rồi ghi `instance` volatile.
4. Thread vào monitor sau đọc lại `instance`; thấy object đã công bố và không tạo thêm.
5. `join()` khiến main chờ hai worker kết thúc rồi quan sát kết quả. `AtomicReference` và `AtomicInteger` cũng cung cấp các thao tác chia sẻ thread-safe cho dữ liệu quan sát.
6. `Resource.value` là final và được đặt trong constructor trước lúc công bố reference; chương trình không sửa đối tượng sau khởi tạo.

Với demo xử lý nghiệp vụ thật, nên truyền lỗi từ worker về caller hoặc ghi nhận trạng thái lỗi; ví dụ này tập trung vào quy tắc công bố instance khi không có interrupt hay exception bất thường.

### Lưu ý về exception khi khởi tạo

Nếu `new Resource(...)` ném exception **trước câu lệnh gán** `instance = result`, bản DCL trên không công bố object thất bại; `instance` vẫn null và lần gọi sau có thể thử tạo lại. Có thể cần chính sách cụ thể nếu khởi tạo có side effect đã xảy ra một phần, hoặc nếu lỗi không nên được retry.

DCL không tạo tính nguyên tử cho các side effect ngoài constructor (ví dụ ghi file, tạo kết nối từ xa, gọi dịch vụ). Hãy thiết kế cleanup, retry và idempotency khi những yêu cầu đó thực sự có mặt.

## 8. Điều kiện áp dụng volatile trong DCL

### 8.1. Reference phải volatile trong mô hình DCL chuẩn

Trong Java 17+, lựa chọn đơn giản và rõ ràng là:

~~~java
private static volatile Resource instance;
~~~

Toàn bộ đường đọc ngoài khóa, đọc lại trong khóa và ghi công bố phải dùng cùng trường đó. Nếu có cache tham chiếu ở một trường **không volatile** hay đọc qua đường tắt khác, cần phân tích lại happens-before cho đường ấy.

### 8.2. Đừng để this thoát khỏi constructor

Ví dụ dưới đây là **anti-pattern** minh họa, không phải chương trình độc lập:

~~~java
class EscapingResource {
    EscapingResource(EventBus bus) {
        bus.register(this); // this bị công bố ra ngoài trong constructor!
        // Các bước khởi tạo quan trọng có thể chưa xong.
    }
}
~~~

`EventBus` là kiểu giả định. Đây là vấn đề **this escape**: thread khác có thể nhận reference trước khi constructor hoàn thành thông qua kênh công bố khác. DCL chỉ công bố instance **sau khi constructor trả về**, nhưng nó không sửa được việc constructor tự gửi `this` sang thread khác sớm hơn.

Một thiết kế tốt cần xây dựng đối tượng đầy đủ trước khi chia sẻ; tránh gọi callback, đăng ký listener làm lộ `this`, hoặc khởi chạy thread từ constructor nếu chúng có thể dùng trạng thái chưa hoàn chỉnh.

### 8.3. Volatile không bảo vệ trạng thái mutable sau khởi tạo

Giả sử singleton có field:

~~~java
final class CounterService {
    int count;

    void increment() {
        count++;
    }
}
~~~

Đây là một lớp minh họa không có `main()`. Nếu được công bố bằng DCL volatile, đối tượng có thể **được tạo và công bố an toàn**, nhưng hai thread gọi `increment()` đồng thời vẫn có data race trên `count` và có thể mất cập nhật.

Muốn an toàn cho `count`, phải dùng khóa, atomic counter hoặc thiết kế không mutable state. **Volatile của reference chỉ bảo đảm publication**, không “lây” tính thread-safe cho tất cả phương thức, cấu trúc dữ liệu hay trường bên trong đối tượng.

Tương tự, nếu cấu hình được thay đổi sau khởi tạo, việc giữ reference volatile **không tự khiến nhiều field trong cấu hình được cập nhật nguyên tử như một snapshot**. Khi cần thay đổi toàn cấu hình, có thể tạo **đối tượng immutable mới** rồi công bố toàn reference qua một volatile/AtomicReference; đó là mô hình khác với lazy DCL tạo instance duy nhất.

### 8.4. Final fields hỗ trợ thiết kế immutable nhưng không thay thế DCL

Đánh dấu các thành phần trạng thái của Resource là `final` và không sửa chúng sau constructor giúp dễ suy luận. Tuy nhiên:

- `final` có ngữ nghĩa đặc biệt trong JMM khi object được xây dựng đúng, nhưng không tự bảo đảm **chỉ một thread tạo object**.
- Một reference final tại nơi khác không biến **check-null-then-create** trên field thường thành nguyên tử.
- Mutable object được trỏ từ final field vẫn cần bảo vệ nếu có nhiều thread cập nhật nó.

### 8.5. Nếu không cần lazy, đừng sử dụng DCL

Nếu chi phí khởi tạo thấp và việc tạo tài nguyên không cần trì hoãn, dùng khởi tạo static/class initialization thường đơn giản hơn. DCL có nhiều điều kiện hơn, dễ bị sửa sai và cần bài test/invariant rõ ràng.

## 9. Các phương án thay thế đơn giản hơn

### 9.1. Khởi tạo sớm bằng static final

~~~java
public final class EagerSingleton {
    private static final EagerSingleton INSTANCE = new EagerSingleton();

    private EagerSingleton() {}

    public static EagerSingleton getInstance() {
        return INSTANCE;
    }
}
~~~

Đây là lớp hoàn chỉnh có thể biên dịch. **JVM class initialization** có bảo đảm đồng bộ hóa cho việc khởi tạo class. Đối tượng được tạo khi class được khởi tạo, không nhất thiết vào đúng thời điểm पहली tiên gọi `getInstance()`, và không có kiểm tra null trong getter.

Ưu điểm: ít mã, khó mắc lỗi về publication. Nhược điểm: tạo object khi class được khởi tạo, có thể sớm hơn lúc tài nguyên thật sự cần.

### 9.2. Initialization-on-demand holder

Đây là cách thường được ưu tiên cho lazy singleton **static**, khi không cần tùy biến chính sách khởi tạo:

Tệp **HolderSingletonDemo.java**:

~~~java
public class HolderSingletonDemo {
    static final class Resource {
        final int value;

        Resource(int value) {
            this.value = value;
        }
    }

    private static final class Holder {
        static final Resource INSTANCE = new Resource(42);
    }

    static Resource getInstance() {
        return Holder.INSTANCE;
    }

    public static void main(String[] args) {
        Resource first = getInstance();
        Resource second = getInstance();

        System.out.println("same instance = " + (first == second));
        System.out.println("value = " + first.value);
    }
}
~~~

~~~bash
javac -encoding UTF-8 HolderSingletonDemo.java
java HolderSingletonDemo
~~~

Stdout:

~~~text
same instance = true
value = 42
~~~

**Cơ chế:** class `Holder` được khởi tạo khi lần đầu sử dụng trường static không phải constant variable `INSTANCE`. Quy tắc class initialization của JVM bảo đảm an toàn trước nhiều thread mà code người dùng không phải tự viết khóa hoặc volatile. Lần gọi sau chỉ đọc tham chiếu đã được khởi tạo.

Giới hạn: đây vẫn là **một instance theo classloader đã khởi tạo class tương ứng**, không phải singleton toàn bộ hệ thống phân tán. Nếu initialization của holder thất bại theo cơ chế khởi tạo class, JLS có hành vi lỗi khởi tạo riêng; không giống mọi lần gọi DCL đều thử constructor lại.

### 9.3. Enum singleton

Tệp **EnumSingletonDemo.java**:

~~~java
public class EnumSingletonDemo {
    enum Service {
        INSTANCE;

        int calculate() {
            return 6 * 7;
        }
    }

    public static void main(String[] args) {
        Service first = Service.INSTANCE;
        Service second = Service.INSTANCE;

        System.out.println("same instance = " + (first == second));
        System.out.println("value = " + first.calculate());
    }
}
~~~

~~~bash
javac -encoding UTF-8 EnumSingletonDemo.java
java EnumSingletonDemo
~~~

Stdout:

~~~text
same instance = true
value = 42
~~~

Enum có cơ chế khởi tạo do JVM quản lý, đồng thời có hành vi serialization dành riêng cho enum. Đây thường là lựa chọn tốt nếu cần một instance cho một enum type và không có yêu cầu kế thừa lớp thông thường. Như các lựa chọn singleton khác, các trường mutable bên trong enum vẫn cần bảo vệ khi truy cập đồng thời.

### 9.4. Getter synchronized đơn giản

~~~java
public final class SynchronizedSingleton {
    private static SynchronizedSingleton instance;

    private SynchronizedSingleton() {}

    public static synchronized SynchronizedSingleton getInstance() {
        if (instance == null) {
            instance = new SynchronizedSingleton();
        }
        return instance;
    }
}
~~~

Lớp này có thể biên dịch độc lập; không có `main()`. Vì **mọi lần gọi getter đều phải qua cùng monitor**, code có mutual exclusion và quy tắc happens-before mà không cần volatile riêng cho `instance`.

Ưu điểm là rõ ràng, dễ bảo trì. Nhược điểm là phải vào `synchronized` ở mọi lần gọi, dù singleton đã được tạo. Không tự đoán chi phí này là đáng kể: hãy đo trong bối cảnh ứng dụng thật nếu hiệu năng là một yêu cầu có chứng cứ.

## 10. Bảng lựa chọn: DCL và các phương án khác

| Cách | Lazy theo lần dùng đầu? | Cơ chế an toàn khi công bố | Chi phí/độ phức tạp | Khi nào chọn |
| --- | --- | --- | --- | --- |
| Static final/eager | Không cần chờ getter; khởi tạo khi class initialization | JVM class initialization | Đơn giản | Tạo sớm chấp nhận được |
| Holder idiom | Có, theo lần dùng Holder | JVM class initialization | Đơn giản | Lazy singleton static thông thường |
| Enum singleton | Theo initialization của enum | JVM class initialization; semantics enum | Đơn giản | Singleton biểu diễn bằng enum phù hợp |
| Synchronized getter | Có | Monitor mỗi lần gọi | Dễ hiểu, có khóa lặp lại | Getter không cần tối ưu đường nhanh |
| Volatile DCL | Có | Volatile write/read + monitor + check 2 | Phức tạp hơn | Cần lazy và đường đọc sau khởi tạo không khóa |

**Quy tắc quyết định thực tế:**

1. Có thật sự cần khởi tạo lazy không? Nếu không, xem static final.
2. Có cần một instance static với constructor không tham số hoặc tự quản lý được không? Xem Holder.
3. Có thể mô hình hóa singleton bằng enum mà không vướng ràng buộc class/API không? Xem enum.
4. Getter được gọi đủ nhiều để chi phí monitor có ý nghĩa trong đo đạc không? Nếu chưa, synchronized getter thường đủ.
5. Nếu vẫn cần DCL, dùng mẫu đầy đủ **volatile + check 1 + lock + check 2 + publish** và document invariant.

## 11. Các lỗi thường gặp khi dùng DCL

| Cách viết/lập luận sai | Vấn đề | Cách sửa |
| --- | --- | --- |
| `instance` là field thông thường | Đường đọc ngoài khóa thiếu safe publication | Cho reference thành `volatile` hoặc chọn phương án có toàn bộ truy cập đồng bộ hóa |
| Chỉ kiểm tra `null` ngoài khóa | Nhiều thread vượt check và tạo nhiều object | Kiểm tra lại **bên trong** cùng monitor |
| Chỉ dùng `volatile` và `if (null) new` | Volatile không làm check–then–act nguyên tử | Thêm đồng bộ hóa/CAS đúng quy tắc |
| Đọc/ghi qua các lock khác nhau | Không có mutual exclusion chung cho khởi tạo | Dùng **cùng một monitor cố định** |
| Đưa `this` ra callback trong constructor | Có thể bị quan sát trước khi hoàn tất xây dựng | Tránh `this escape` |
| Khởi tạo xong rồi cập nhật các trường thường từ nhiều thread | DCL chỉ bảo vệ công bố ban đầu | Tách đồng bộ hóa cho trạng thái mutable |
| Suy ra DCL đúng vì test 1.000 lần không lỗi | Các lịch lỗi có thể hiếm, JIT/CPU khác nhau | Lập luận theo JMM, invariant và kiểm thử có kiểm soát |
| Dùng `Thread.sleep()` trước khi đọc `instance` | Sleep không tạo happens-before cho reference | Dùng volatile/synchronized/class initialization |
| Khởi tạo object rồi thay instance trong lúc chạy nhưng vẫn coi là singleton cố định | Luật lifetime khác hẳn lazy DCL | Thiết kế reload, version, atomic swap riêng |
| Nghĩ singleton là một instance cho cả nhiều JVM | Mỗi process/classloader có phạm vi trạng thái riêng | Dùng coordination ngoài JVM nếu cần một tài nguyên toàn hệ thống |

### Có thể sửa DCL không volatile bằng cách khóa chỉ khi null?

Không. Vấn đề nằm ở **đường đọc nhanh không khóa**: dù constructor/ghi reference diễn ra trong synchronized, thread đọc khi `instance != null` vẫn bỏ qua monitor, nên không nhận được bảo đảm đọc cùng khóa. Nếu không muốn volatile, mọi đường đọc dùng cùng monitor (như synchronized getter) hoặc dùng class initialization đúng hợp đồng.

### Có nên dùng `AtomicReference` thay `volatile`?

`AtomicReference` hỗ trợ đọc/ghi và CAS nguyên tử, nhưng **không tự biến một phép “nếu null thì tạo đối tượng” thành đúng một lần gọi constructor**. Ví dụ hai thread cùng tạo candidate rồi một CAS thắng: reference công bố cuối có thể chỉ có một nhưng **constructor đã chạy hai lần**. Nếu constructor có side effect hoặc việc tạo rất đắt, điều đó có thể vi phạm yêu cầu.

DCL dùng synchronized để **chặn việc tạo trùng**, không chỉ chọn reference chiến thắng. Nếu dùng CAS cần phân tích cả side effect của tạo candidate và policy bỏ candidate thua.

### Volatile có ngăn dữ liệu bị thay đổi đồng thời không?

Không. Nó cung cấp visibility/ordering cho field và đọc/ghi riêng lẻ đúng với hợp đồng, nhưng không làm một giao dịch trên nhiều trường trở nên nguyên tử. Ví dụ `volatile int count; count++;` vẫn có read–modify–write race. Nếu singleton quản lý bộ đếm, hãy dùng AtomicInteger hoặc khóa bảo vệ hành động đó.

## 12. Những giới hạn cần biết

### Phạm vi “một instance”

Một trường `static` thuộc về class được định nghĩa bởi **classloader** trong một JVM. Các classloader khác nhau có thể nạp các bản định nghĩa class riêng, nên DCL không bảo đảm “duy nhất toàn hệ thống”. Nhiều process, container hay instance ứng dụng cũng có trạng thái riêng.

Nếu cần **một công việc toàn hệ thống**, một khóa cục bộ Java hoặc singleton không đủ: phải thiết kế cơ chế coordination ở mức nguồn dữ liệu chung/hạ tầng tương ứng. Đây là nguyên tắc Java concurrency tổng quát, không phải cơ chế của một framework cụ thể.

### Constructor, callback và vòng phụ thuộc

DCL chỉ đơn giản khi constructor hoàn tất trước publication và không vòng phụ thuộc. Nếu `Resource` tự gọi lại `getInstance()` trong constructor, hoặc gọi dependency cũng gọi ngược lại singleton này, có thể xảy ra recursion, vòng khởi tạo hoặc trạng thái chưa được công bố. Đồng bộ hóa không tự giải quyết vòng phụ thuộc logic.

Nếu constructor tạo thread phụ hoặc phát callback tham chiếu `this`, thread khác có thể quan sát object trước khi constructor kết thúc. Hạn chế làm việc có side effect trong constructor và tách bước start/registration khi cần.

### Reflection, serialization và cloning

DCL thông thường chỉ đảm bảo đường **`getInstance()`** không tạo trùng trong cùng phạm vi lớp/khóa. Nếu lớp có các đường khởi tạo hay sao chép khác (constructor truy cập từ nơi khác, serialization không xử lý singleton, clone...), những đường đó phải được xem xét độc lập nếu “một instance” là yêu cầu chặt. Enum giúp quản lý một số vấn đề thuộc ngữ nghĩa enum, nhưng lựa chọn cần phù hợp API đang thiết kế.

### Retry khi initialization thất bại

Nếu constructor ném exception, DCL không gán reference và lần gọi tiếp có thể thử lại. Nhưng nếu đã tạo tài nguyên bên ngoài trước khi lỗi, lần sau có thể tạo thêm side effect. Trước khi áp dụng lazy initialization cho tài nguyên quan trọng, hãy quyết định: **retry, lưu lỗi, đóng tài nguyên từng phần, hay coi lỗi là không thể phục hồi**. DCL không tự có chính sách này.

## 13. Kiểm thử DCL như thế nào?

**Kiểm thử có ích:**

1. Đặt đếm trong constructor qua **AtomicInteger**, tạo nhiều thread cùng gọi getter và kiểm tra số lần tạo bằng 1 khi các lời gọi thành công.
2. Dùng **CountDownLatch** hoặc barrier để tăng cạnh tranh tại cùng một mốc (không dựa vào `sleep()`).
3. So sánh reference trả về bằng **`==`** khi hợp đồng yêu cầu cùng instance trong một classloader.
4. Kiểm tra các field được constructor thiết lập trước công bố và chạy các phương thức theo hợp đồng.
5. Test lỗi constructor, retry và cleanup nếu code có yêu cầu xử lý lỗi.
6. Khi có mutable state, test riêng đường đọc/ghi cạnh tranh của chính state ấy; không xem test singleton là test thread safety của object.

**Kiểm thử không thể chứng minh** mọi lịch trên mọi JVM/CPU đều đúng. Kết luận an toàn phải dựa vào cấu trúc đồng bộ hóa và quy tắc JMM. Demo `MissingSecondCheckDemo` tái hiện một lỗi logic bằng điều phối có chủ đích; còn lỗi missing volatile thường không thể dựa vào một stdout bắt buộc để chứng minh trong runtime cụ thể.

Nếu dùng benchmark đo `synchronized` so với DCL, cần công cụ đo phù hợp, thời gian warmup/JIT, phân biệt throughput và latency. Chi phí tối ưu hóa chỉ có ý nghĩa khi được đo, không nên đánh đổi tính đúng đắn lấy suy đoán.

## 14. Câu hỏi tự kiểm tra

**1. Vì sao cần `volatile` dù có `synchronized`?**  
Vì đường đọc nhanh có thể không lấy khóa. Volatile mang hợp đồng publication và visibility cho các thread nhìn thấy reference ngoài synchronized.

**2. Vì sao check 2 vẫn bắt buộc khi instance volatile?**  
Hai thread có thể cùng đọc null trước khi một thread công bố instance. Sau khi chờ khóa, phải đọc lại reference để tránh tạo trùng.

**3. Nếu dùng `private static final Resource INSTANCE = new Resource()`, có cần DCL không?**  
Không. JVM class initialization đã quản lý an toàn việc khởi tạo static; đổi lại instance có thể được tạo lúc class initialization thay vì lúc getter được gọi.

**4. Nếu singleton có trường `int count` và nhiều thread cùng gọi `count++`, DCL volatile có bảo vệ giá trị count không?**  
Không. DCL bảo vệ công bố reference ban đầu. Counter mutable cần atomicity/synchronization riêng.

**5. Nếu constructor thất bại trước câu lệnh `instance = result`, DCL có lưu một object thành công không?**  
Không; câu lệnh volatile write chưa chạy. Lần gọi sau có thể thử lại, nhưng side effect dở dang cần chính sách cleanup/retry riêng.

**6. Có thể thay synchronized bằng CAS trên một AtomicReference để chắc chắn constructor chỉ chạy một lần?**  
Không nhất thiết. Nhiều thread có thể tạo candidate trước CAS, dù chỉ một candidate được công bố.

**7. Một singleton trong một JVM có đồng nghĩa một singleton trong toàn hệ thống không?**  
Không. Process và classloader khác nhau có thể có các instance riêng. DCL chỉ bảo vệ phạm vi trường static/monitor tương ứng.

**8. Khi nào holder idiom phù hợp hơn DCL?**  
Khi cần một lazy static singleton thông thường và không cần tự kiểm soát đường khởi tạo bằng lock/policy đặc biệt. JVM class initialization cung cấp thread safety với mã ít hơn.

## 15. Ghi nhớ

Đối với mẫu **double-checked locking chuẩn trên Java 17+**, ba điều kiện đi cùng nhau là **trường instance volatile**, **một monitor chung khi khởi tạo**, và **kiểm tra lại null sau khi đã lấy khóa**. Volatile xử lý phần **safe publication/visibility**; synchronized và check 2 xử lý phần **không tạo trùng trong cạnh tranh**. DCL chỉ đảm bảo khởi tạo và công bố reference ban đầu; an toàn của trạng thái mutable, tài nguyên ngoài JVM và lifecycle phức tạp phải được thiết kế riêng.

Khi không có yêu cầu rõ ràng khiến DCL hữu ích, cân nhắc **holder**, **enum** hoặc **khởi tạo static** để giảm độ phức tạp và giảm nguy cơ sửa sai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-memory-model-013">Phân biệt tính hiển thị và loại trừ tương hỗ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tính hiển thị và loại trừ tương hỗ trong Java Memory Model

Khi nhiều thread chia sẻ dữ liệu có thể thay đổi, hai yêu cầu thường bị nhầm thành một: **thread đọc có nhìn thấy giá trị mà thread khác đã ghi không?** (*visibility*) và **hai thread có thể cùng sửa một trạng thái tại một thời điểm không?** (*mutual exclusion*). Java cung cấp các công cụ khác nhau cho từng nhu cầu. Hiểu rõ ranh giới này giúp tránh lỗi “đã dùng `volatile` nhưng counter vẫn sai” hoặc “đã khóa writer nhưng reader vẫn thấy dữ liệu không nhất quán”.

Bài này giải thích trên **Java Memory Model (JMM)** và các API Java chuẩn; không phụ thuộc cơ chế đồng thời riêng của framework.

## 1. Bốn khái niệm thường đi cùng nhau

| Khái niệm | Câu hỏi phải trả lời | Ví dụ |
| --- | --- | --- |
| **Visibility — tính hiển thị** | Khi một thread đã ghi, thread khác có bảo đảm quan sát giá trị đó theo quan hệ đồng bộ không? | Writer đặt `ready=true`; reader cần thấy thay đổi |
| **Mutual exclusion — loại trừ tương hỗ** | Có ngăn được nhiều thread cùng đi vào critical section bảo vệ cùng dữ liệu không? | Chỉ một thread được kiểm tra và trừ tồn kho mỗi lần |
| **Atomicity — tính nguyên tử** | Thao tác nghiệp vụ có được nhìn như một bước không bị xen ngang không? | `count++` phải là một lần tăng trọn vẹn |
| **Ordering — thứ tự quan sát** | Các thao tác ghi/đọc có các ràng buộc thứ tự nào đối với thread khác? | Ghi `payload` trước khi công bố `ready` |

**Visibility không kéo theo mutual exclusion.** Hai thread có thể đều đọc được giá trị mới nhất ở điểm chúng đọc nhưng vẫn cùng tính từ một snapshot và ghi đè cập nhật của nhau. **Mutual exclusion đúng cách thường đem lại visibility cần thiết**, bởi đồng bộ monitor/lock có quy tắc happens-before; tuy vậy phải dùng **cùng một khóa** trên tất cả đường đọc/ghi có liên quan.

Atomicity là mục tiêu ở mức thao tác; mutual exclusion là **một cách** đạt atomicity. Một operation `AtomicInteger.incrementAndGet()` cũng nguyên tử mà không cần programmer bao nó trong `synchronized`. Ngược lại, đọc hai field `volatile` riêng lẻ không tạo snapshot nguyên tử của cả cặp.

## 2. Tính hiển thị là gì?

Giả sử object có hai trường:

```java
class Message {
    int payload;
    boolean ready;
}
```

Một thread chạy `payload = 42; ready = true;`. Thread khác chạy vòng lặp cho tới khi `ready` đúng rồi đọc `payload`. Nếu cả hai trường là field thường và không có cơ chế đồng bộ, JMM **không bảo đảm** reader quan sát cập nhật `ready` trong một thời hạn hữu hạn, cũng không bảo đảm nó sẽ thấy `payload=42` khi tự suy luận chỉ từ thứ tự source code của writer.

Lý do: với các truy cập xung đột không có quan hệ happens-before, JVM/JIT và CPU có thể dùng tối ưu, cache/register và các thứ tự thực thi hợp lệ theo JMM; các thread có thể quan sát khác nhau. Đừng nghĩ “CPU luôn đọc lại RAM ở mỗi vòng lặp” hoặc “tăng `sleep` sẽ làm cache đồng bộ”. Cả hai đều không tạo hợp đồng memory visibility của Java.

### `volatile` là một cách công bố tín hiệu

Nếu `ready` được khai báo `volatile`:

```java
class Message {
    int payload;
    volatile boolean ready;
}
```

Writer:

```java
message.payload = 42;
message.ready = true;
```

Reader:

```java
if (message.ready) {
    System.out.println(message.payload);
}
```

Đây là các **mảnh minh họa**, giả sử writer và reader cùng tham chiếu tới một instance `message`. Một lần ghi `volatile` tạo quan hệ *synchronizes-with* đối với lần đọc `volatile` thích hợp theo synchronization order. Nếu reader đọc `ready` và thấy giá trị `true` do writer công bố, các ghi trước đó của writer (gồm `payload=42`) được nhìn thấy đúng theo quan hệ happens-before.

**Giới hạn:** mô hình này giả định không có thread khác sửa `payload` theo cách thiếu đồng bộ hoặc tái sử dụng `ready` sai giao thức. `volatile ready` là cơ chế **công bố** cho một message đã chuẩn bị xong, không làm mọi thao tác tương lai trên `payload` được bảo vệ. Reader phải thực sự quan sát cờ; code `if` ở ví dụ chỉ đi vào body nếu cờ đã đúng.

### Ví dụ Java 17 độc lập: truyền một message bằng `volatile`

Tạo tệp `VolatilePublicationExample.java`:

```java
import java.util.concurrent.locks.LockSupport;

public class VolatilePublicationExample {
    static class Message {
        int payload;
        volatile boolean ready;
    }

    public static void main(String[] args) throws InterruptedException {
        Message message = new Message();
        int[] received = {-1};

        Thread reader = new Thread(() -> {
            while (!message.ready) {
                // Giảm vòng quay bận, không phải cơ chế visibility.
                LockSupport.parkNanos(1_000_000L);
            }
            received[0] = message.payload;
        });

        Thread writer = new Thread(() -> {
            message.payload = 42;
            message.ready = true;
        });

        reader.start();
        writer.start();
        writer.join();
        reader.join();

        System.out.println("payload=" + received[0]);
    }
}
```

Chạy bằng JDK 17+:

```bash
javac VolatilePublicationExample.java
java VolatilePublicationExample
```

Stdout khi chương trình hoàn thành bình thường:

```text
payload=42
```

Tại sao đúng?

1. Writer ghi `payload=42` trước `ready=true` trong cùng thread.
2. Reader liên tục đọc **volatile field** `ready`; khi thấy `true`, quan hệ happens-before bảo đảm writer đã công bố payload.
3. Reader ghi kết quả vào `received[0]`; main chỉ đọc sau `reader.join()`. Việc `join` hoàn thành thành công tạo bảo đảm quan sát hành động của reader.
4. `LockSupport.parkNanos` chỉ giúp tránh spin 100% CPU trong ví dụ; **không thay thế `volatile`**. Mã production thường dùng `BlockingQueue`, `CountDownLatch` hoặc cơ chế chờ có deadline để không phải polling.

Về mặt test, chạy ví dụ thành công không phải phép chứng minh thực nghiệm rằng xóa `volatile` sẽ luôn gây lỗi. Phiên bản không `volatile` thiếu hợp đồng memory visibility; có thể vẫn tình cờ in `42` trên một máy. Lập luận đúng đến từ JMM.

## 3. Loại trừ tương hỗ là gì?

**Mutual exclusion** là bảo đảm tại một thời điểm nhiều nhất một thread được thực thi một vùng mã cần sự độc quyền trên **cùng một tài nguyên/khóa**.

Ví dụ, hàm rút tiền gồm hai thao tác: kiểm tra còn đủ tiền và trừ tiền. Nếu hai thread có thể chen giữa hai bước, cả hai có thể thấy cùng số dư cũ rồi cùng rút. Muốn duy trì bất biến `balance >= 0`, ta cần xem **toàn bộ kiểm tra + cập nhật** là một đơn vị được bảo vệ.

```java
class BankAccount {
    private int balance = 100;

    synchronized boolean withdraw(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount < 0");
        }
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }

    synchronized int balance() {
        return balance;
    }
}
```

Đây là **snippet class**, không có `main`. `synchronized` instance method sử dụng monitor của `this`. Với **cùng một instance BankAccount**, hai thread không thể cùng chạy `withdraw` một lúc. `balance()` cũng đi qua cùng monitor, giúp reader quan sát một trạng thái nhất quán theo giao thức khóa.

Mutual exclusion còn giúp visibility: khi thread A nhả monitor, thread B lấy **cùng monitor** sau đó, việc nhả monitor của A *happens-before* lần lấy monitor của B. Nhưng nếu writer khóa `lockA` còn reader khóa `lockB`, không có bảo đảm mutual exclusion hay monitor handoff giữa hai khóa đó.

## 4. `volatile` có visibility nhưng không chặn thread khác đi vào

Một biến `volatile int count` có các truy cập đọc/ghi đơn lẻ với semantics visibility/ordering của volatile. Tuy nhiên, phép tăng:

```java
count++;
```

vẫn là **read–modify–write** ở mức logic. Một lịch thực thi hợp lệ có thể như sau:

| Giai đoạn | Thread A | Thread B | `count` |
| --- | --- | --- | --- |
| Ban đầu | | | `0` |
| 1 | Đọc `0` | | `0` |
| 2 | | Đọc `0` | `0` |
| 3 | Tính và ghi `1` | | `1` |
| 4 | | Tính và ghi `1` | `1` |

Cả hai đọc giá trị có thật khi chúng đọc; không có vấn đề “đọc cache cũ” nào bắt buộc phải xuất hiện. Nhưng **hai lần tăng chỉ được tính một lần**. `volatile` không bảo vệ critical section và không gộp read–modify–write.

### Thí nghiệm Java 17: ép mất cập nhật dù dùng `volatile`

Chương trình sau cố ý tách read và write để đặt một barrier giữa hai bước. Đây là **test điều phối một lịch logic nguy hiểm**, không phải benchmark hay phép chứng minh data race ở mức JMM trong tất cả phiên bản chương trình.

Tạo `VolatileIsNotMutexExample.java`:

```java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class VolatileIsNotMutexExample {
    static class Counter {
        volatile int value;
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        CyclicBarrier bothRead = new CyclicBarrier(2);

        Runnable increment = () -> {
            int old = counter.value;
            try {
                bothRead.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (BrokenBarrierException e) {
                throw new IllegalStateException(e);
            }
            counter.value = old + 1;
        };

        Thread first = new Thread(increment);
        Thread second = new Thread(increment);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("expected=2, actual=" + counter.value);
    }
}
```

```bash
javac VolatileIsNotMutexExample.java
java VolatileIsNotMutexExample
```

Stdout trong đường chạy bình thường, khi cả hai worker tới barrier:

```text
expected=2, actual=1
```

Lý do kết quả xác định: cả hai worker đọc `0` trước khi `CyclicBarrier(2)` cho bất kỳ worker nào đi qua và ghi. Sau đó cả hai đều tính `0+1`. Thread nào ghi cuối cùng thì giá trị vẫn bằng `1`. Tình huống thể hiện **việc thao tác trên trạng thái volatile vẫn có thể mất cập nhật ở cấp nghiệp vụ**. Barrier tạo đồng bộ trong test; không suy ra từ riêng test này rằng Java bắt buộc phát sinh data race theo định nghĩa hình thức trên mọi lần chạy.

**Giới hạn test:** ví dụ không đặt deadline ở barrier để tập trung vào ý tưởng. Test suite/production cần dùng `await(timeout, unit)` và truyền exception của worker về thread test bằng `Future.get()`, nhằm tránh treo khi thiếu participant hoặc task lỗi. Không dùng `Thread.sleep` thay barrier để cố tạo một lịch xác định.

## 5. `synchronized` vừa mutual exclusion vừa visibility

`synchronized` có hai hiệu ứng quan trọng:

1. **Loại trừ tương hỗ:** chỉ một thread sở hữu cùng intrinsic monitor trong một thời điểm. Một critical section có thể bảo vệ nhiều field như một bất biến thống nhất.
2. **Memory visibility/ordering:** hành động `unlock` trên monitor happens-before hành động `lock` tiếp theo trên cùng monitor. Writer và reader tuân thủ cùng monitor có thể nhìn thấy trạng thái mà giao thức đồng bộ công bố.

Ví dụ:

```java
class SynchronizedCounter {
    private int count;

    public synchronized void increment() {
        count++;
    }

    public synchronized int get() {
        return count;
    }
}
```

Đây là **snippet class**; khi mọi đường đọc và ghi `count` đều qua những method dùng cùng monitor của **cùng instance**, `increment()` không bị xen ngang bởi một `increment()` khác. Thêm `volatile` cho field ở đây thường không cần thiết.

### Chương trình so sánh `synchronized` và atomic operation

Tạo `SafeCounterComparison.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class SafeCounterComparison {
    static class LockedCounter {
        private int count;

        synchronized void increment() {
            count++;
        }

        synchronized int get() {
            return count;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        LockedCounter locked = new LockedCounter();
        AtomicInteger atomic = new AtomicInteger();

        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                locked.increment();
                atomic.incrementAndGet();
            }
        };

        Thread first = new Thread(task);
        Thread second = new Thread(task);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("synchronized=" + locked.get());
        System.out.println("atomic=" + atomic.get());
    }
}
```

```bash
javac SafeCounterComparison.java
java SafeCounterComparison
```

Stdout:

```text
synchronized=200000
atomic=200000
```

**Vì sao chính xác?** `locked.increment()` yêu cầu cùng monitor. `atomic.incrementAndGet()` là một operation cập nhật nguyên tử có hợp đồng memory semantics tương ứng; nó không cần programmer giữ monitor bên ngoài. `join()` chờ hai thread hoàn thành trước khi main đọc kết quả, nên không phải “đọc quá sớm”.

Đây **không phải benchmark** chứng minh `AtomicInteger` luôn nhanh hơn `synchronized`. Tốc độ phụ thuộc mức contention, JVM, số core, hotspot và loại workload. Chọn theo tính đúng đắn của thao tác cần bảo vệ trước.

### Những giới hạn quan trọng của `synchronized`

- Một method `synchronized` trên object A **không** ngăn thread khác chạy `synchronized` trên object B. Cùng dữ liệu phải được bảo vệ bởi **cùng lock identity**, không chỉ “cùng từ khóa”.
- `static synchronized` lấy monitor của đối tượng `Class`, khác monitor của instance method `synchronized`.
- Một writer khóa mà reader đọc trực tiếp field không khóa vẫn có thể có vấn đề visibility/consistency. Tất cả đường đọc/ghi thuộc giao thức phải tham gia cùng quy tắc.
- Java monitor là reentrant: một thread có thể lấy lại monitor nó đang giữ; tuy vậy nested lock trên **những monitor khác nhau** vẫn có nguy cơ deadlock.
- Không giữ monitor quá lâu cho I/O, `sleep` hoặc đợi `Future` nếu không cần: các thread khác sẽ `BLOCKED` khi cố vào cùng monitor.
- `Thread.sleep` không nhả monitor. `Object.wait` nhả monitor **của chính đối tượng được gọi `wait`** trong khi chờ và lấy lại trước khi trả về.
- `synchronized` không hứa công bằng (fairness), thứ tự thức dậy hay tối ưu throughput.

## 6. Bảng chọn công cụ theo đúng yêu cầu

| Công cụ | Visibility | Mutual exclusion | Atomic read–modify–write | Phạm vi thường phù hợp |
| --- | --- | --- | --- | --- |
| Field thường `int`/`boolean`, không đồng bộ | Không có bảo đảm liên thread cho dữ liệu race | Không | Không đối với `++`/`--` | Chỉ truy cập cục bộ hoặc có giao thức khác bao quanh |
| `volatile` field | Có, theo volatile happens-before/ordering | **Không** | Các đọc/ghi riêng có semantics volatile; `x++` vẫn không atomic | Cờ dừng, một chiều công bố dữ liệu, reference snapshot |
| `synchronized` cùng monitor | Có, qua unlock → lock | **Có** | Có thể bảo vệ **toàn bộ** thao tác ghép trong critical section | Bất biến nhiều field, check–then–act |
| `ReentrantLock` / `Lock` đúng giao thức | Có, qua lock/unlock theo memory semantics | **Có** | Có thể bảo vệ thao tác ghép | Cần `tryLock`, timed lock, interruptible acquisition, `Condition` |
| `AtomicInteger` / `AtomicLong` | Có theo memory effects của API atomic | **Không khóa cả vùng mã tùy ý** | **Có** cho `incrementAndGet`, `compareAndSet` và các method atomic | Một biến/operation atomic |
| `AtomicReference` với CAS | Có theo memory effects của API atomic | Không khóa các thao tác bên ngoài | Có cho cập nhật **một reference** bằng CAS | Immutable state snapshot, cập nhật versioned |
| `LongAdder` | Có hợp đồng thread-safety phù hợp cho cập nhật | Không | Từng add an toàn, nhưng `sum()` không snapshot nguyên tử khi đang update | Metrics nhiều writer, chấp nhận tổng không tuyến tính hóa từng thời điểm |
| `BlockingQueue`/concurrent collection | Có bảo đảm theo hợp đồng chuyển giao/operation | Phụ thuộc triển khai; không khóa toàn workflow | Theo từng operation đã được API cam kết | Producer–consumer, map theo key |
| Immutable object + safe publication | Đọc ổn định theo cách công bố hợp lệ | Không cần lock để đọc object thực sự immutable | Không có phép cập nhật in-place | Dữ liệu cấu hình, snapshot |

**Đọc hàng “AtomicInteger”:** operation `incrementAndGet` là atomic, nhưng sequence `if (count.get() > 0) count.decrementAndGet()` vẫn có thể sai khi nhiều thread cùng chạy. Atomicity có **phạm vi**: một method atomic không tự làm cả một quy trình trở thành atomic transaction.

**Đọc hàng “volatile”:** Java bảo đảm đọc/ghi volatile đơn lẻ có semantics và atomicity phù hợp với một lần truy cập field; điều **không được bảo đảm** là nhiều phép truy cập cùng tạo một thao tác nguyên tử. Vì vậy đừng viết “volatile hoàn toàn không atomic” một cách thiếu phân biệt.

## 7. `ReentrantLock` và visibility

`java.util.concurrent.locks.ReentrantLock` cung cấp mutual exclusion và memory semantics như lock truyền thống, có thêm `tryLock`, `lockInterruptibly` và các `Condition`. Lấy khóa thành công và nhả khóa có hiệu ứng đồng bộ; các reader/writer **phải dùng cùng một instance lock** cho dữ liệu tương ứng.

Ví dụ class Java 17, dùng được khi đặt vào file Java nhưng **chưa có `main`**:

```java
import java.util.concurrent.locks.ReentrantLock;

class Inventory {
    private final ReentrantLock lock = new ReentrantLock();
    private int stock = 1;

    boolean sellOne() {
        lock.lock();
        try {
            if (stock == 0) {
                return false;
            }
            stock--;
            return true;
        } finally {
            lock.unlock();
        }
    }

    int currentStock() {
        lock.lock();
        try {
            return stock;
        } finally {
            lock.unlock();
        }
    }
}
```

Mọi thao tác cập nhật và đọc stock trong class đều dùng **cùng lock**, nên vừa không bán quá một sản phẩm nếu ban đầu chỉ có một, vừa có visibility khi đọc. Luôn `unlock()` trong `finally` sau khi `lock()` thành công; quên nhả khóa có thể khiến thread khác chờ vô hạn.

**Đừng nhầm `Lock` với `volatile`**: một `volatile boolean available` có thể giúp nhiều thread thấy giá trị cờ mới, nhưng không ngăn cả hai cùng thấy `true` trước khi một trong hai chuyển cờ sang `false`. Nếu điều kiện và cập nhật phải đi cùng nhau, chọn lock hoặc atomic compare-and-set phù hợp.

## 8. Nguyên tử hóa một thao tác mà không khóa cả critical section

Giả sử tồn kho chỉ là một số nguyên. `AtomicInteger` có `compareAndSet(expected, update)`: chỉ thay đổi nếu giá trị thực tế vẫn bằng expected tại thời điểm thao tác atomic được thực hiện. Từ đó có thể xây một vòng CAS để bảo vệ điều kiện trừ kho:

```java
import java.util.concurrent.atomic.AtomicInteger;

class AtomicInventory {
    private final AtomicInteger stock = new AtomicInteger(1);

    boolean sellOne() {
        while (true) {
            int current = stock.get();
            if (current == 0) {
                return false;
            }
            if (stock.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    int currentStock() {
        return stock.get();
    }
}
```

Đây là class minh họa, không có `main`. Hai thread cùng đọc `1`: chỉ một phép CAS thành công khi chuyển `1 → 0`. Thread còn lại thất bại, đọc lại `0` và trả `false`. Như vậy một điều kiện đơn biến vẫn được bảo toàn **không cần explicit mutex**.

**Giới hạn CAS:** nếu cập nhật liên quan nhiều field hoặc external side effect (gửi email, trừ tiền ở remote API), một CAS trên một số nguyên không gộp được mọi hiệu ứng. Vòng CAS có thể retry; **không đặt side effect không idempotent trong callback/logic có thể chạy lại**. Với nhiều biến cùng tạo một invariant, cân nhắc cùng monitor/lock hoặc một immutable aggregate được thay bằng `AtomicReference` theo giao thức đúng.

## 9. Visibility có nghĩa là “luôn thấy giá trị mới nhất” không?

Không nên diễn đạt visibility thành “mọi thread luôn thấy giá trị vừa ghi ngay lập tức theo đồng hồ thực”. JMM định nghĩa các quan hệ thứ tự và đồng bộ cho phép thread quan sát dữ liệu theo hợp đồng. Với `volatile`, các lần đọc/ghi đồng bộ có thứ tự phù hợp; nhưng:

- Reader chỉ biết cờ đã được công bố nếu **thực sự đọc nó**.
- Reader có thể chưa được scheduler chạy, nên không có cam kết độ trễ tuyệt đối.
- Nếu writer liên tục cập nhật một field khác mà không có giao thức tương ứng, một lần đọc cờ không biến các field khác thành snapshot nhất quán vô hạn.
- `volatile` không thay thế quyền sở hữu dữ liệu, bảo đảm liveness, timeout hoặc điều kiện nghiệp vụ.

Ví dụ: writer công bố `payload` rồi đặt `ready=true` là một giao thức handoff đơn giản. Nếu sau đó writer lại sửa `payload` khi reader đang đọc, phải bổ sung điều phối/immutability; lần công bố ban đầu không bảo vệ cuộc cập nhật sau.

## 10. Một số mối quan hệ happens-before cần nhớ

JMM dùng quan hệ *happens-before* để xác định thứ tự và hiệu ứng visibility giữa các hành động. Đây **không chỉ là thời điểm A xảy ra trước B trên đồng hồ**.

1. **Trong cùng thread:** hành động trước happens-before hành động sau theo *program order*.
2. **Monitor:** unlock monitor happens-before lần lock **tiếp theo** trên cùng monitor.
3. **Volatile:** ghi vào field volatile happens-before lần đọc sau đó của chính field volatile theo quy tắc đồng bộ của JMM.
4. **Thread start:** lời gọi `Thread.start()` happens-before các hành động bên trong thread mới.
5. **Thread join:** các hành động của thread hoàn tất happens-before thread khác quan sát thành công việc kết thúc qua `join()`.
6. **Tiện ích đồng thời:** nhiều API trong `java.util.concurrent` mô tả memory-consistency effects, ví dụ `BlockingQueue` chuyển giao object hoặc `Future.get()` sau khi task hoàn tất.
7. **Tính bắc cầu:** nếu A happens-before B và B happens-before C thì A happens-before C.

**Happens-before không phải mutex.** `Thread.join()` có thể giúp main nhìn thấy kết quả của hai worker sau khi hoàn thành, nhưng **không sửa lost update** mà hai worker đã gây ra trong lúc chạy. Tương tự, một signal volatile giữa producer và consumer có thể giúp công bố dữ liệu, nhưng hai producers đồng thời sửa cùng state vẫn cần giao thức chống xen ngang.

## 11. Các thiết kế thường cần visibility mà không cần mutex

### 11.1. Cờ dừng một chiều

`volatile boolean running = true;` phù hợp khi một thread liên tục kiểm tra cờ và thread khác đặt `false` để yêu cầu kết thúc. Cần bảo đảm code xử lý tín hiệu và không bị kẹt ở một blocking call không phản ứng với cờ. Với blocking I/O, thread interruption và API cancellation có thể thích hợp hơn hoặc cần kết hợp.

### 11.2. Công bố immutable snapshot

Writer tạo một object snapshot thực sự bất biến rồi gán vào `volatile` reference. Reader đọc **một reference** và sử dụng trọn snapshot đó. Cách này tránh việc đọc xen phiên bản giữa nhiều field.

```java
record Snapshot(int x, int y) {}

class Holder {
    volatile Snapshot current = new Snapshot(0, 0);

    void publish(int x, int y) {
        current = new Snapshot(x, y);
    }

    Snapshot read() {
        return current;
    }
}
```

Đây là snippet Java 17+ (không có `main`). `record` lưu hai primitive `int`, nên giá trị sau xây dựng không thay đổi. Gán vào `volatile current` công bố snapshot mới; reader lấy đúng **một snapshot** cho cả `x` và `y`. Nhưng nếu **nhiều writer** đều xây snapshot từ current rồi gán, chúng vẫn có thể ghi đè cập nhật của nhau; cần serialization hoặc `AtomicReference` CAS nếu invariant đòi không mất cập nhật.

### 11.3. Safe publication và `final` fields

Field `final` có các bảo đảm đặc biệt khi object được xây dựng đúng cách, nhưng **`final` reference không làm object được tham chiếu trở nên bất biến**. Ví dụ `final List<Item> items` vẫn có thể chứa list mutable và phần tử mutable. Không làm `this` escape trước khi constructor hoàn thành; khi chia sẻ các object có state tiếp tục được cập nhật, dùng giao thức công bố/đồng bộ phù hợp.

### 11.4. Handoff bằng thread-safe queue

Thay vì tự dựng cờ `ready` và field `payload`, producer có thể `queue.put(message)`, consumer `queue.take()` với `BlockingQueue`. Hợp đồng API bảo đảm chuyển giao dữ liệu và cơ chế chờ; vẫn cần limit queue, shutdown/cancellation và giới hạn tài nguyên. Đây thường là thiết kế dễ duy trì hơn một vòng quay chờ volatile.

## 12. Những thiết kế phải có mutual exclusion hoặc atomic workflow

### 12.1. Check–then–act

```java
if (!seat.isBooked()) {
    seat.book();
}
```

Ngay cả khi mỗi method được `synchronized` riêng lẻ, nếu hai thread được phép chạy xen giữa **check** và **book**, cả hai có thể thấy chưa đặt. Cần method atomic kiểu `bookOnce()` chứa cả kiểm tra lẫn cập nhật trong **cùng critical section**, hoặc CAS/trạng thái transactional tương đương.

### 12.2. Read–modify–write

```java
volatile int counter;
// ...
counter = counter + 1;
```

Đây là snippet minh họa field của class; không phải chương trình đầy đủ. Dù field `volatile`, hai thread có thể cùng lấy snapshot cũ. Dùng `AtomicInteger.incrementAndGet()` hoặc khóa bảo vệ toàn bộ read–compute–write.

### 12.3. Invariant nhiều field

Một đơn hàng có `reserved` và `available` với tổng cố định. Nếu writer cập nhật từng field và reader đọc giữa các bước, reader có thể thấy một nửa trạng thái mới. Hai field `volatile` **không tự bảo đảm snapshot atomic**; reader có thể đọc chúng ở hai thời điểm khác nhau. Giải pháp gồm cùng monitor/lock cho toàn bộ update và read, hoặc immutable snapshot thay bằng một reference atomic.

### 12.4. Thao tác ghép trên concurrent collection

`ConcurrentHashMap` bảo vệ từng operation theo hợp đồng API. Nhưng `if (!map.containsKey(k)) map.put(k,v)` vẫn có cửa sổ race ở cấp workflow. Chọn `putIfAbsent`/`computeIfAbsent` hoặc một transaction thích hợp. **Thread-safe object ≠ mọi chuỗi dùng object đó đều thread-safe.**

## 13. Các bẫy thường gặp khi chọn `volatile` hoặc `synchronized`

| Ngộ nhận | Điều sai | Cách chọn đúng |
| --- | --- | --- |
| “`volatile` làm mọi thao tác trên field thành atomic” | `count++` gồm nhiều hành động | `AtomicInteger`/mutex cho update |
| “Hai field đều `volatile` nên cặp luôn nhất quán” | Reader vẫn có thể đọc hai phiên bản | Khóa chung hoặc immutable snapshot qua một reference |
| “Writer dùng `synchronized` là đủ, reader không cần” | Reader thường không tham gia giao thức happens-before của monitor | Đồng bộ cả hai hoặc dùng publication đã chứng minh |
| “Khóa bằng object nào cũng giống nhau” | Lock identity khác → không loại trừ nhau | Dùng đúng cùng monitor/`Lock` |
| “`join()` giúp main thấy đúng mọi phép tăng” | `join` không biến các update worker thành atomic | Đồng bộ ở nơi update diễn ra |
| “`Thread.sleep` làm giá trị được flush ra” | Sleep không tạo visibility hay mutual exclusion cần thiết | `volatile`/lock/queue/latch theo điều kiện |
| “`AtomicInteger.get()` rồi `set()` là atomic workflow” | Hai thao tác riêng có thể bị chen vào | Dùng `compareAndSet`/method atomic |
| “Thread-safe map giúp check–then–put luôn an toàn” | Các bước vẫn có thể xen kẽ | Operation atomic trong API |
| “Bọc khóa lớn mọi nơi sẽ luôn tốt hơn” | Contention, lock convoy, deadlock, latency | Khóa đúng invariant với critical section đủ ngắn |
| “Không có nhiều core thì không race” | Scheduler có thể interleave trên một core | Bảo vệ giao thức độc lập số CPU |
| “Virtual thread tự làm state thread-safe” | Mọi thread Java vẫn phải tuân theo JMM | Đồng bộ/immutability như thường |
| “Một test qua 1.000 lần đủ chứng minh an toàn” | Test chỉ thấy một phần lịch chạy | JMM reasoning + stress/coordinated tests |

## 14. Đọc thread dump và profile: quan sát gì?

**Mutual exclusion có chi phí contention.** Nếu nhiều thread không lấy được cùng intrinsic monitor, chúng có thể xuất hiện ở trạng thái `BLOCKED` trong thread dump. Trên JFR/monitor profiling, xem ai giữ monitor và giữ bao lâu. Nếu dùng `ReentrantLock`, thread có thể park và hiển thị `WAITING`/`TIMED_WAITING` khi chờ lock, chứ không nhất thiết ở `BLOCKED`.

**Visibility bug khó phát hiện chỉ bằng trạng thái thread.** Một thread có thể ở `RUNNABLE` trong vòng lặp đọc cờ thường, mã vẫn không tạo happens-before. Thread dump không chứng minh field đang được đọc đúng giá trị. Muốn kết luận, kiểm tra tất cả đường truy cập và giao thức công bố, kết hợp JFR, logs, stress tests và phân tích JMM.

### Phương pháp chẩn đoán

1. Liệt kê field/object được chia sẻ, xác định thread nào đọc/ghi.
2. Ghi invariant nghiệp vụ: “không bán quá kho”, “counter không mất update”, “reader thấy toàn bộ payload đã công bố”.
3. Hỏi bài toán cần **visibility**, **mutual exclusion**, **atomicity của một operation** hay **atomicity của nhiều field**.
4. Kiểm tra mỗi đường truy cập có dùng cùng monitor/lock, volatile publication hoặc API atomic hợp đồng đúng không.
5. Nếu dùng volatile flag, kiểm tra thứ tự ghi payload → flag và reader thực sự đọc flag trước payload; chú ý lần cập nhật payload sau publication.
6. Nếu dùng monitor, kiểm tra lock identity, thời gian giữ khóa, I/O trong khóa và chuỗi lock acquisition gây deadlock.
7. Nếu dùng CAS, kiểm tra vòng retry, invariant đơn biến và side effects có thể lặp.
8. Viết test đồng thời với start gate/barrier khi muốn tái hiện một interleaving; nhưng nhớ barrier tạo happens-before và **có thể che visibility bug** đang nghiên cứu.
9. Test dưới tải, theo dõi kết quả và profile; không dùng việc “không tái hiện” thay cho chứng minh đúng.

## 15. Bài tập phân biệt nhanh

**1. Một thread đặt `stopped=true` và thread khác kiểm tra liên tục để thoát. Cần loại trừ tương hỗ không?** Nếu đây là cờ dừng một chiều độc lập, thường chỉ cần visibility, chẳng hạn `volatile boolean stopped`. Với thao tác blocking dài hoặc nhiều state liên quan, có thể cần cơ chế khác.

**2. Hai thread cùng gọi `volatileCounter++`, còn test `join()` trước khi in. Có thể mất cập nhật không?** Có. `join` bảo đảm main quan sát sau khi worker kết thúc; nó không bảo vệ read–modify–write giữa hai worker.

**3. Một method `synchronized` ghi field; method khác đọc field trực tiếp. Có chắc reader thấy cập nhật không?** Không. Reader không tham gia cùng monitor và không có bảo đảm visibility do chính khóa đó tạo ra trên đường đọc. Cần cơ chế đồng bộ/volatile/publication thích hợp.

**4. Hai `synchronized` instance methods của hai object khác nhau có ngăn nhau không?** Không. Mỗi instance có monitor riêng. Nếu cùng sửa một `static` mutable field, phải chọn đúng khóa chung.

**5. Nếu `AtomicInteger.compareAndSet` sửa được một field, còn hai field liên quan thì sao?** CAS trên một biến không tự tạo atomic transaction hai biến. Có thể dùng lock chung hoặc immutable aggregate được thay bằng một atomic reference.

**6. Khi nào `volatile Snapshot current` phù hợp?** Khi writer công bố một snapshot bất biến hoàn chỉnh và reader cần thấy nguyên trạng một phiên bản bằng cách đọc một reference. Nếu nhiều writers cần hợp nhất cập nhật dựa trên snapshot cũ, cần thêm CAS hoặc serialization.

**7. `synchronized` có bảo đảm mọi thread luôn vào khóa theo thứ tự FIFO không?** Không. Nó bảo đảm mutual exclusion và monitor memory semantics, không cam kết fairness.

**8. Khi nào cả visibility và mutual exclusion đều cần?** Ví dụ trừ tồn kho: các worker phải không xen ngang phần check–update và các reader phải thấy trạng thái nhất quán. Cùng một lock quanh update/read có thể đáp ứng cả hai.

## 16. Checklist thiết kế trước khi quyết định

- Đang cần **reader thấy thay đổi**, hay **ngăn nhiều writer tác động đồng thời**? Hai yêu cầu có cùng tồn tại không?
- Có một phép gán/cờ đơn giản, một read–modify–write, hay invariant nhiều field?
- Nếu dùng `volatile`, những dữ liệu nào được công bố trước ghi volatile? Có sửa tiếp sau publication không?
- Nếu dùng `synchronized`/`Lock`, mọi truy cập liên quan có dùng **cùng khóa**? Khóa có bị giữ khi đợi I/O không?
- Nếu dùng atomic class, **operation nào** được bảo đảm atomic? Có vô tình tách check và update?
- Có thể thiết kế immutable snapshot, ownership theo thread hoặc `BlockingQueue` để giảm shared mutable state không?
- Có test invariant sau khi thread hoàn tất, stress test và bằng chứng happens-before không?
- Khi chuyển từ platform thread sang virtual thread, giao thức dữ liệu có còn đúng trên mọi lịch chạy không?

**Quy tắc ghi nhớ:** `volatile` giúp **công bố/quan sát** giá trị qua JMM nhưng **không tạo một khu vực độc quyền**; `synchronized`/`Lock` bảo vệ **critical section** và đồng thời tạo bảo đảm memory visibility giữa các lần mở/đóng đúng khóa; atomic classes bảo vệ **những operation cụ thể**. Hãy chọn theo bất biến và phạm vi thao tác mà chương trình cần bảo toàn, thay vì chọn theo từ khóa quen tay.

</details>

- [Quay lại đầu trang](#back-to-top)
