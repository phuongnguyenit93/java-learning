<a id="back-to-top"></a>

# Số và đồng thời

## Menu
- [count++ là nhiều bước, không phải thao tác nguyên tử](#concurrent-numbers-001)
- [volatile không làm phép tăng trở thành nguyên tử](#concurrent-numbers-002)
- [AtomicInteger và AtomicLong](#concurrent-numbers-003)
- [getAndIncrement, incrementAndGet và các phép cập nhật](#concurrent-numbers-004)
- [Compare-and-set và việc hàm cập nhật có thể bị gọi lại](#concurrent-numbers-005)
- [LongAdder, DoubleAdder và bài toán thống kê có tranh chấp](#concurrent-numbers-006)
- [sum của adder không phải snapshot nguyên tử trong lúc đang cập nhật](#concurrent-numbers-007)
- [BigInteger, BigDecimal bất biến nhưng cập nhật tham chiếu dùng chung vẫn cần phối hợp](#concurrent-numbers-008)
- [Atomic không có nghĩa tự phát hiện tràn số](#concurrent-numbers-009)
- [Chọn mức đồng bộ theo hợp đồng nhất quán cần có](#concurrent-numbers-010)

## <a id="concurrent-numbers-001">count++ là nhiều bước, không phải thao tác nguyên tử</a>

<details>
<summary>Xem chi tiết</summary>

# count++ là nhiều bước, không phải thao tác nguyên tử

## 1. Vấn đề: tăng một biến đếm chia sẻ

Trong Java, `count++` là cách viết ngắn gọn của thao tác tăng biến thêm 1. Ở chương trình tuần tự, đây là một biểu thức quen thuộc. Tuy nhiên, khi **hai hoặc nhiều luồng cùng tăng một biến đếm chia sẻ**, `count++` **không bảo đảm** rằng mỗi lần gọi đều làm tăng kết quả cuối cùng thêm đúng 1.

Nguyên nhân là **phép tăng gồm đọc giá trị cũ, tính giá trị mới và ghi lại** (*read–modify–write*). Những bước logic này không được bảo vệ như một thao tác nguyên tử (*atomic operation*). Hai luồng có thể cùng đọc một giá trị ban đầu rồi ghi đè kết quả của nhau. Hiện tượng ấy gọi là **lost update** (mất cập nhật).

Ví dụ, nếu `count = 0` và hai luồng mỗi luồng thực hiện một lần `count++`, ta mong đợi `count = 2`. Nhưng nếu không có đồng bộ hóa phù hợp, chương trình có thể chỉ lưu `1`. Việc một lần chạy cho ra `2` **không chứng minh** mã đúng, vì lịch xen kẽ giữa các luồng có thể khác ở lần chạy tiếp theo.

Bài học này giúp bạn phân biệt **tính nguyên tử**, **khả năng quan sát giá trị giữa các luồng** và **thứ tự thực thi**; từ đó dùng đúng `synchronized`, `AtomicInteger` và `LongAdder`.

## 2. Phân rã count++: đọc, tính, ghi

Xét biến `int count`, biểu thức:

```java
count++;
```

Có thể hình dung về mặt ngữ nghĩa như sau:

```java
int oldValue = count;          // (1) Đọc giá trị hiện tại
int nextValue = oldValue + 1;  // (2) Tính kết quả mới
count = nextValue;              // (3) Ghi kết quả
```

Đây là **mô hình giải thích**, không phải cam kết rằng mọi JVM thực thi đúng ba lệnh máy hoặc ba bytecode. JIT và CPU có thể tối ưu cách thực hiện; tuy vậy, Java **không cung cấp hợp đồng nguyên tử** cho thao tác tăng một field `int` chia sẻ bằng `count++`.

| Thời điểm | Luồng A | Luồng B | Giá trị chia sẻ |
|---|---|---|---|
| 1 | Đọc `count = 0` | Chưa chạy | `0` |
| 2 | Giữ bản sao `oldA = 0` | Đọc `count = 0` | `0` |
| 3 | Tính `0 + 1 = 1` | Tính `0 + 1 = 1` | `0` |
| 4 | Ghi `count = 1` | Chưa ghi | `1` |
| 5 | Đã xong | Ghi `count = 1` | `1` |

Hai lần tăng xảy ra, nhưng giá trị cuối chỉ là **1**. Luồng B đã ghi đè kết quả của A; đó chính là lost update.

Kịch bản khác cũng có thể xảy ra: A hoàn tất ghi `1` trước khi B đọc, nên B đọc `1` và ghi `2`. Vì vậy race condition có thể lúc đúng, lúc sai tùy lịch chạy.

### Phân biệt postfix và prefix

- `count++` (*postfix*) tăng biến rồi trả về **giá trị cũ** của biểu thức.
- `++count` (*prefix*) tăng biến rồi trả về **giá trị mới** của biểu thức.
- `count += 1` cũng cần đọc, tính rồi ghi.

Ba cách viết đều **không tự tạo thao tác nguyên tử** khi nhiều luồng đồng thời cập nhật cùng một biến `int` chia sẻ. Sự khác biệt giữa prefix và postfix liên quan đến giá trị của biểu thức, không liên quan đến an toàn luồng.

### Khi nào count++ bình thường là đủ?

Nếu `count` là **biến cục bộ của một luồng** và không bị chia sẻ cho luồng khác, không có hai luồng cùng sửa nó nên không có lost update do cạnh tranh. Nguy cơ thường nằm ở field của đối tượng chia sẻ, `static` field, hoặc một phần tử mảng được nhiều luồng đọc/ghi.

Ngay cả khi riêng lẻ các lần đọc và ghi một `int` được thực hiện nguyên tử, **tổ hợp đọc–tăng–ghi** vẫn không nguyên tử. Không nên lấy thuộc tính của một lần đọc/ghi riêng làm suy luận cho toàn biểu thức.

## 3. Chương trình Java 17+ tái hiện vấn đề một cách xác định

Thử chạy nhiều luồng cùng viết `count++` có thể cho kết quả thay đổi qua mỗi lần chạy; rất khó dự đoán stdout chính xác. Ví dụ dưới đây dùng `CountDownLatch` để **buộc cả hai luồng cùng đọc giá trị cũ trước khi ghi**, qua đó tái hiện được một lịch xen kẽ gây mất cập nhật.

Phần không an toàn dùng hai câu `snapshot = count` và `count = snapshot + 1` để **phơi bày** các bước của phép tăng; nó mô phỏng thành phần read–modify–write của `count++`, chứ không khẳng định mỗi lần chạy `count++` luôn bị xen ngang theo cùng một cách. Phần thứ hai dùng hai phương án sửa lỗi với 100.000 lần tăng trên mỗi luồng.

Tạo file **`IncrementDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class IncrementDemo {
    private static int count;
    private static int guardedCount;
    private static final Object LOCK = new Object();
    private static final AtomicInteger atomicCount = new AtomicInteger();

    private static void demonstrateLostUpdate()
            throws InterruptedException {
        count = 0;
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable unsafe = () -> {
            int snapshot = count;
            bothRead.countDown();

            try {
                allowWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            count = snapshot + 1;
        };

        Thread a = new Thread(unsafe);
        Thread b = new Thread(unsafe);
        a.start();
        b.start();

        bothRead.await();
        allowWrite.countDown();
        a.join();
        b.join();

        System.out.println("two increments, actual: " + count);
    }

    private static void demonstrateFixes()
            throws InterruptedException {
        guardedCount = 0;
        atomicCount.set(0);

        Runnable safe = () -> {
            for (int i = 0; i < 100_000; i++) {
                synchronized (LOCK) {
                    guardedCount++;
                }
                atomicCount.incrementAndGet();
            }
        };

        Thread a = new Thread(safe);
        Thread b = new Thread(safe);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("synchronized: " + guardedCount);
        System.out.println("AtomicInteger: " + atomicCount.get());
    }

    public static void main(String[] args)
            throws InterruptedException {
        demonstrateLostUpdate();
        demonstrateFixes();
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac IncrementDemo.java
java IncrementDemo
```

Kết quả:

```text
two increments, actual: 1
synchronized: 200000
AtomicInteger: 200000
```

**Giải thích chi tiết:**

1. Hai luồng `a` và `b` cùng giữ `snapshot = 0`. `bothRead.await()` ở luồng main đợi cả hai đã đọc, rồi mới gọi `allowWrite.countDown()` cho phép ghi.
2. Mỗi luồng ghi `snapshot + 1 = 1`. Hai lần ghi trùng nhau khiến kết quả cuối `count == 1`. Đây là minh họa quyết định một lịch gây lost update.
3. Với `synchronized (LOCK)`, mỗi lần `guardedCount++` được bảo vệ bởi cùng đối tượng khóa. Chỉ một luồng được ở trong vùng khóa trong cùng thời điểm.
4. `atomicCount.incrementAndGet()` thực hiện nguyên tử phép tăng trên `AtomicInteger`. Hai luồng cùng gọi 100.000 lần, nên kết quả `200000`.
5. `a.join()` và `b.join()` đảm bảo main chỉ đọc kết quả **sau khi hai luồng hoàn tất**. `join()` không làm cho những phép `count++` bên trong luồng trở thành nguyên tử; nó chỉ tạo điểm chờ và bảo đảm về thứ tự quan sát sau khi luồng kết thúc.

Với code sửa lỗi, dù lịch chạy của hai luồng thay đổi, tổng cuối vẫn chính xác khi không có thao tác sửa thêm và không xảy ra tràn số. Bản mã sai cố tình trình bày các bước rời để bạn nhìn thấy nguyên nhân.

## 4. Atomicity, visibility và happens-before là ba vấn đề riêng

### Tính nguyên tử (atomicity)

Một phép tăng nguyên tử phải quan sát được như một cập nhật không thể bị phép tăng khác chen vào giữa làm mất kết quả. Nếu bộ đếm ban đầu 0 và có 200.000 lần tăng nguyên tử đã hoàn tất, kết quả là 200.000 (giả sử không có thao tác sửa khác và chưa tràn `int`).

`count++` trên field không đồng bộ không có bảo đảm ấy; `AtomicInteger.incrementAndGet()` có bảo đảm này. `synchronized` có thể biến một vùng đọc–tính–ghi thành vùng không bị luồng khác dùng **cùng khóa** xen vào.

### Khả năng nhìn thấy (visibility)

Một luồng có thể ghi dữ liệu nhưng luồng khác không nhất thiết nhìn thấy hiệu ứng ấy đúng lúc mong đợi nếu không có đồng bộ hóa thích hợp. Java Memory Model (JMM) quy định khi nào thay đổi của một luồng phải nhìn thấy đối với luồng khác.

Từ khóa `volatile` thiết lập một số bảo đảm visibility và ordering cho truy cập field; `synchronized` và các API đồng bộ cũng có quy tắc bộ nhớ riêng. Nhưng visibility **không tự bảo đảm** rằng hai thao tác đọc/ghi riêng lẻ được ghép thành một thao tác tăng nguyên tử.

### Thứ tự và quan hệ happens-before

Quan hệ **happens-before** của JMM diễn đạt bảo đảm thứ tự/khả năng quan sát giữa các thao tác. Ví dụ, sau khi `Thread.join()` hoàn tất thành công, luồng gọi `join()` có thể quan sát các hành động của luồng đã kết thúc theo hợp đồng JMM. Việc mở và giải phóng cùng monitor cũng tạo quan hệ đồng bộ hóa tương ứng.

Dùng `join()` để chờ các luồng là đúng khi đọc **kết quả cuối**, nhưng `join()` được gọi sau khi các lần tăng diễn ra **không thể khôi phục** những cập nhật đã mất trước đó.

## 5. Vì sao volatile không giải quyết count++?

Đây là đoạn mã **vẫn sai** khi nhiều luồng gọi đồng thời:

```java
private volatile int count = 0;

public void increment() {
    count++;
}
```

`volatile` cho tính chất của **mỗi lần đọc và ghi riêng**, nhưng `count++` có thể được xen kẽ như sau:

```text
A: đọc count -> 10
B: đọc count -> 10
A: tính 11 và ghi count = 11
B: tính 11 và ghi count = 11
```

Bộ đếm kết thúc ở `11` thay vì `12`. Hai luồng không có một thao tác “đọc+ghi” nguyên tử; cả hai chỉ dùng các lần đọc/ghi volatile riêng biệt. Vì vậy:

- `volatile int count; count++;` **không an toàn** như bộ đếm chia sẻ có nhiều updater.
- `volatile long count; count++;` cũng **không an toàn**.
- `count += 1` cũng không giải quyết được lỗi lost update.
- `volatile` phù hợp cho những giao thức chia sẻ giá trị/cờ trạng thái mà cập nhật không đòi hỏi read–modify–write nguyên tử.

Không nên phát biểu rằng chỉ cần “tránh cache CPU” là đủ để giải quyết. Đây là vấn đề về hợp đồng **atomicity** của toàn thao tác, không chỉ việc đọc dữ liệu mới nhất.

## 6. Cách sửa bằng synchronized

`synchronized` tạo một vùng được bảo vệ bởi **monitor**. Nếu tất cả đường đọc/ghi bộ đếm đều tuân thủ **cùng một khóa**, chỉ một luồng được thực thi phần được bảo vệ tại một thời điểm.

Ví dụ lớp counter:

```java
public final class SynchronizedCounter {
    private int count;

    public synchronized void increment() {
        count++;
    }

    public synchronized int get() {
        return count;
    }
}
```

Hai phương thức instance `synchronized` trên **cùng đối tượng `SynchronizedCounter`** cùng khóa `this`. Khi `increment()` khóa monitor, luồng khác gọi `get()` hoặc `increment()` trên chính instance đó phải chờ. Với cùng giao thức khóa, phép tăng được bảo vệ và kết quả đọc có bảo đảm visibility phù hợp.

Cũng có thể viết:

```java
private final Object lock = new Object();
private int count;

public void increment() {
    synchronized (lock) {
        count++;
    }
}
```

Hai đoạn này là **ví dụ thành phần lớp**, không phải chương trình chạy độc lập có `main()`. Nếu ghép vào một lớp, dùng một kiểu khóa thống nhất cho mọi truy cập liên quan.

Những điều kiện để sửa đúng:

- **Mọi updater** đều sử dụng cùng khóa; không có đoạn khác tăng `count++` bên ngoài vùng khóa.
- Nếu cần quan sát nhất quán, **mọi reader** cũng phải tuân thủ giao thức tương ứng.
- Không tạo một đối tượng `new Object()` riêng trong mỗi lần gọi để khóa; khóa khác nhau không loại trừ lẫn nhau.
- Giữ vùng khóa đủ nhỏ khi hợp lý, đặc biệt không chèn I/O kéo dài mà không cần thiết vào khối khóa.

`synchronized` phù hợp khi cần bảo vệ **nhiều bước hoặc nhiều field** theo một bất biến chung, ví dụ cập nhật counter đồng thời cập nhật một cấu trúc dữ liệu khác mà cả hai phải nhất quán.

## 7. Cách sửa bằng AtomicInteger

`java.util.concurrent.atomic.AtomicInteger` cung cấp giá trị `int` cùng các API cập nhật nguyên tử:

```java
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger count = new AtomicInteger(0);

int afterFirst = count.incrementAndGet(); // Tăng, trả giá trị mới: 1
int beforeSecond = count.getAndIncrement(); // Tăng, trả giá trị cũ: 1
int afterAdd = count.addAndGet(5);         // Tăng thêm 5, trả 7
int current = count.get();                // Đọc 7
```

`incrementAndGet()` có ngữ nghĩa **trả về giá trị sau khi tăng**, tương tự `++count`; `getAndIncrement()` **trả về giá trị trước khi tăng**, tương tự `count++`. Điểm khác quyết định là cả hai phương thức `AtomicInteger` cung cấp **cập nhật nguyên tử** đối với instance đó.

Ví dụ khi hai luồng cùng gọi `getAndIncrement()` và bộ đếm ban đầu 0: hai giá trị trả về tương ứng là `0` và `1` (không thể xác định trước luồng nào nhận giá trị nào); sau khi cả hai hoàn tất, `get()` là `2`.

**Đừng tách cập nhật thành hai lời gọi:**

```java
count.set(count.get() + 1); // VẪN CÓ THỂ LOST UPDATE
count.incrementAndGet();    // CẬP NHẬT NGUYÊN TỬ
```

`get()` và `set()` riêng lẻ có bảo đảm của API, nhưng ghép chúng bằng một biểu thức Java **không tạo thành một cập nhật nguyên tử**. Nếu logic phức tạp phụ thuộc giá trị cũ, xét `updateAndGet(...)`, `getAndUpdate(...)`, `compareAndSet(expected, update)` hoặc `accumulateAndGet(...)`.

Nhiều thuật toán atomic dựa trên compare-and-set (CAS) và có thể thử lại khi cạnh tranh. Vì vậy, hàm cập nhật truyền vào `updateAndGet`/`accumulateAndGet` nên không chứa side effect ngoài việc tính giá trị mới: hàm có thể được chạy lại khi cập nhật chưa thành công.

**Giới hạn:** `AtomicInteger` bảo vệ **một giá trị `int`**, không tự biến một chuỗi cập nhật nhiều field hoặc nhiều đối tượng thành một giao dịch nguyên tử. Nếu cần cấp số thứ tự duy nhất và tăng nguyên tử, `getAndIncrement()` thường phù hợp; nếu cần nhiều field đồng bộ, nên có giao thức khóa bao quát.

## 8. LongAdder dành cho bộ đếm thống kê có tranh chấp cao

`java.util.concurrent.atomic.LongAdder` hữu ích khi **rất nhiều luồng cùng tăng bộ đếm thống kê**. Nó có thể phân tán cập nhật vào nhiều thành phần nội bộ để giảm tranh chấp, thay vì buộc tất cả tranh chấp một giá trị như `AtomicInteger`.

```java
import java.util.concurrent.atomic.LongAdder;

LongAdder requests = new LongAdder();
requests.increment();
requests.add(5L);
long currentEstimate = requests.sum();
```

Điểm cần phân biệt:

- `LongAdder.increment()` cho phép tích lũy các lần tăng đồng thời.
- **`sum()` không phải snapshot nguyên tử** khi các luồng khác vẫn đang cập nhật. Kết quả trong lúc chạy có thể không phản ánh một trạng thái tuyến tính duy nhất.
- Sau khi các updater đã kết thúc và có đồng bộ hóa để đợi chúng hoàn tất, `sum()` có thể dùng để lấy tổng cuối.
- `LongAdder` không cung cấp một kết quả `getAndIncrement()` để cấp ID tăng tuần tự; không dùng nó nếu mọi lần tăng phải trả về số thứ tự nguyên tử độc nhất.

`LongAdder` thích hợp với bộ đếm request, sự kiện, đo thống kê thông lượng cao. `AtomicInteger` phù hợp hơn khi cần một counter cập nhật nguyên tử với giá trị đọc/ghi theo từng thao tác. Không mặc định `LongAdder` luôn nhanh hơn: hiệu năng phụ thuộc mức cạnh tranh và khối lượng công việc; hãy đo khi tối ưu hóa thực tế.

## 9. Bảng lựa chọn và những lỗi thường gặp

| Cách viết | An toàn với nhiều luồng cùng tăng? | Lý do |
|---|---|---|
| `int count; count++;` | Không, nếu cùng chia sẻ mà thiếu đồng bộ | Read–modify–write có thể xen kẽ |
| `volatile int count; count++;` | Không | Visibility không tạo cập nhật nguyên tử |
| `count += 1` hoặc `++count` | Không, khi biến chia sẻ không đồng bộ | Cùng nguyên nhân |
| `synchronized (sameLock) { count++; }` | Có, nếu mọi truy cập tuân thủ khóa | Bảo vệ cả đoạn đọc–tính–ghi |
| `AtomicInteger.incrementAndGet()` | Có | Phép tăng nguyên tử |
| `AtomicInteger.set(get() + 1)` | Không | Hai lời gọi riêng lẻ, có thể bị xen ngang |
| `LongAdder.increment()` | Có thể dùng để tích lũy đồng thời | `sum()` không là snapshot nguyên tử giữa các cập nhật |
| Biến đếm cục bộ chỉ do một luồng sử dụng | Có với mục đích tăng thông thường | Không có race do chia sẻ |

### Sai lầm: “Chạy thử vẫn thấy đủ”

Race phụ thuộc lịch luồng. Một lượt thực thi có kết quả đúng không đủ chứng minh an toàn. Hãy xây dựng phép thử cho các **lịch xen kẽ gây nguy cơ**, như ví dụ dùng latch. Với bộ đếm đúng, kiểm tra tổng sau khi tất cả tác vụ hoàn tất.

### Sai lầm: khóa mỗi nơi một object

`synchronized(lockA)` không bảo vệ khỏi luồng khác dùng `synchronized(lockB)` nếu `lockA != lockB`. Hai luồng sẽ chạy đồng thời trên hai monitor khác nhau. Khi một trạng thái dùng chung được bảo vệ bằng khóa, quy tắc khóa phải thống nhất trên mọi đường truy cập.

### Sai lầm: tưởng mọi hành động với AtomicInteger đều nguyên tử như một nhóm

`incrementAndGet()` là cập nhật nguyên tử, nhưng hai lời gọi liên tiếp, chẳng hạn `get()` rồi `set()` hoặc cập nhật hai đối tượng `AtomicInteger`, không tự tạo một giao dịch nguyên tử tổng thể. Nếu bất biến trải qua nhiều trạng thái, cần cơ chế thiết kế/khóa phù hợp.

### Sai lầm: đọc tổng trước khi nhiệm vụ hoàn tất

Ngay cả một counter nguyên tử vẫn đang tăng khi các worker đang chạy. `get()` giữa chừng không nhất thiết bằng kết quả cuối dự kiến. Đợi tất cả nhiệm vụ xong (`join()`, `Future.get()` hoặc primitive tương đương), rồi đọc.

### Sai lầm: quên giới hạn miền int

`int` có phạm vi từ `-2_147_483_648` đến `2_147_483_647`. `count++` và `AtomicInteger.incrementAndGet()` đều tuân theo ngữ nghĩa tràn của số nguyên 32-bit; tăng quá `Integer.MAX_VALUE` sẽ quay về `Integer.MIN_VALUE`. Nếu tổng có thể lớn hơn, cân nhắc `AtomicLong`, `LongAdder` và chính sách kiểm tra ngưỡng nghiệp vụ. **Tính nguyên tử không đồng nghĩa chống tràn số**.

### Sai lầm: đọc/ghi không tuân thủ cùng giao thức

Nếu một method `synchronized` tăng `count` nhưng method khác trực tiếp gán `count = 0` mà không khóa, bộ đếm không còn có bảo đảm nhất quán. Giữ field ở mức `private` và tập trung các thao tác vào các method cùng giao thức khóa hoặc cùng đối tượng atomic.

## 10. Quy trình ứng dụng

1. **Xác định quyền sở hữu biến:** nó chỉ sống trong một thread hay được nhiều thread đọc/ghi?
2. **Xác định yêu cầu:** chỉ cần tổng cuối cùng; cần trả ID tăng từng lần; hay cần giữ nhiều field đồng bộ?
3. **Một bộ đếm chia sẻ với thao tác tăng đơn lẻ:** dùng `AtomicInteger` hoặc `AtomicLong` và các method cập nhật nguyên tử.
4. **Đoạn cập nhật nhiều bước hoặc nhiều field:** dùng `synchronized` hoặc cơ chế khóa phù hợp, nhất quán ở mọi đường truy cập.
5. **Thống kê có rất nhiều updater và không đòi snapshot nguyên tử khi chạy:** cân nhắc `LongAdder`.
6. **Kiểm thử:** mô phỏng được tình huống race, xác nhận nguyên nhân; sau sửa, đợi worker hoàn tất và kiểm tra tổng cuối cùng cùng các bất biến liên quan.

**Tự kiểm tra 1:** Hai luồng đều gọi `count++` trên `volatile int count = 0` đúng một lần. Có chắc `count == 2` không? **Không**, vì cả hai vẫn có thể đọc 0 trước khi ghi 1.

**Tự kiểm tra 2:** Với `AtomicInteger` ban đầu bằng 0, hai lần `getAndIncrement()` trả giá trị nào? Theo một thứ tự cập nhật nguyên tử, chúng trả `0` rồi `1`; kết quả cuối là `2`. Không xác định trước **luồng nào** nhận từng giá trị.

**Tự kiểm tra 3:** Main gọi `join()` sau khi hai worker tăng `int count` không đồng bộ. Điều đó tự sửa được lost update không? **Không**. `join()` giúp chờ và quan sát sau khi các luồng kết thúc; nó không bảo vệ phần tăng đã diễn ra.

**Ghi nhớ:** `count++` là biểu thức đọc – tính – ghi, **không phải cập nhật nguyên tử** khi nhiều luồng cùng sửa một biến chia sẻ. `volatile` xử lý vấn đề quan sát bộ nhớ, còn `synchronized` và `AtomicInteger` tạo bảo đảm cập nhật phù hợp; `LongAdder` dành cho bộ đếm thống kê tranh chấp cao với giới hạn khác về việc lấy tổng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-numbers-002">volatile không làm phép tăng trở thành nguyên tử</a>

<details>
<summary>Xem chi tiết</summary>

# volatile không làm phép tăng trở thành nguyên tử

## 1. Tình huống: hai luồng cùng tăng một biến đếm

Một ứng dụng Java có nhiều luồng cùng ghi nhận sự kiện. Ta dùng một biến đếm:

~~~java
class Counter {
    volatile int value = 0;

    void increment() {
        value++;
    }
}
~~~

Nhiều người cho rằng thêm `volatile` khiến mọi luồng “luôn thấy giá trị mới nhất”, nên hai luồng mỗi luồng gọi `increment()` một lần thì chắc chắn `value == 2`. Kết luận đó **sai**.

`volatile` giải quyết các vấn đề nhất định về **khả năng nhìn thấy thay đổi giữa các luồng (visibility)** và **thứ tự bộ nhớ (ordering)** theo Java Memory Model. Nó **không biến một chuỗi đọc – tính toán – ghi thành một thao tác nguyên tử (atomic read-modify-write)**.

Nếu hai luồng cùng đọc `value == 0` trước khi luồng nào ghi lại, cả hai đều có thể tính ra `1` rồi cùng ghi `1`. Sau hai lần “tăng”, kết quả vẫn là `1`: một lần cập nhật đã bị mất (**lost update**).

Bài học này tập trung vào cách phân biệt visibility và atomicity, nhận ra lỗi trong `++`/`+=`, tái hiện lỗi có kiểm soát, chọn `AtomicInteger`, `synchronized` hoặc `Lock`, và kiểm thử đúng hợp đồng đồng thời.

## 2. volatile thực sự bảo đảm điều gì?

Trong Java, một trường được khai báo:

~~~java
private volatile int count;
~~~

có các đặc tính quan trọng:

1. **Visibility:** các thao tác đọc/ghi `volatile` tuân theo quan hệ đồng bộ của Java Memory Model. Một lần ghi volatile *happens-before* các lần đọc tiếp theo của **cùng biến volatile** trong thứ tự đồng bộ. Nhờ đó, thông tin được công bố đúng cách có thể được nhìn thấy bởi luồng đọc.
2. **Ordering:** thao tác volatile thiết lập những ràng buộc sắp xếp cần thiết đối với các thao tác bộ nhớ liên quan. Ví dụ, khi một luồng ghi dữ liệu thông thường rồi ghi một cờ volatile, luồng khác đọc được giá trị cờ đã công bố có thể nhìn thấy những ghi dữ liệu trước đó theo quan hệ happens-before.
3. **Từng lần truy cập volatile là một thao tác đọc hoặc ghi với ngữ nghĩa đồng bộ:** truy cập riêng lẻ tới một biến `volatile int`/`volatile long` không bị “xé” thành hai nửa. Nhưng **toàn bộ biểu thức `count++`** vẫn có nhiều bước.

Cụm “luôn thấy giá trị mới nhất” chỉ là cách nói trực giác, dễ gây hiểu lầm. Một luồng đọc tại một thời điểm vẫn có thể đọc giá trị trước khi luồng khác thực hiện lần ghi kế tiếp. `volatile` **không bắt các luồng chờ nhau** và không đảm bảo rằng giá trị đọc sẽ không đổi trước khi luồng đọc ghi ngược trở lại.

### Happens-before không đồng nghĩa atomic transaction

Giả sử luồng A ghi `data = 42` rồi `ready = true` (trường `ready` khai báo volatile). Luồng B đọc `ready == true` theo đúng giao thức công bố, rồi đọc `data`: các ghi trước lần công bố volatile của A được đặt trước các thao tác sau lần đọc tương ứng của B theo quan hệ happens-before.

Điều này rất hữu ích cho **cờ trạng thái** hoặc **công bố trạng thái đã hoàn tất**. Nhưng nếu hai luồng cùng viết `data = data + 1`, thì điều kiện cập nhật dựa trên giá trị cũ cần **atomicity** riêng. Cờ volatile không tự bảo vệ phép tính đó.

| Nhu cầu | volatile đơn thuần đáp ứng? | Vì sao |
|---|---|---|
| Một luồng ghi cờ dừng, luồng khác đọc cờ | Có thể phù hợp | Giao thức một cờ đơn giản, đọc/ghi độc lập |
| Nhiều luồng cùng gọi `counter++`, không mất cập nhật | **Không** | Đọc và ghi tách rời, có thể xen kẽ |
| Ghi một tham chiếu tới cấu hình bất biến đã xây dựng xong | Có thể phù hợp | Công bố tham chiếu qua volatile tạo quan hệ visibility |
| Nhiều luồng cập nhật từng trường của đối tượng do tham chiếu volatile trỏ tới | **Không tự động** | volatile trên tham chiếu không khóa các trường bên trong |
| Chuyển tiền: kiểm tra số dư rồi trừ tiền như một hành động | **Không** | Điều kiện và thay đổi phải cùng được bảo vệ |
| Đọc snapshot nhất quán của nhiều biến | **Không tự động** | Từng lần đọc độc lập có thể thuộc các thời điểm khác nhau |

## 3. Vì sao count++ là ba bước logic?

Biểu thức:

~~~java
count++;
~~~

về mặt logic thực hiện:

~~~text
1. Đọc giá trị hiện tại của count.
2. Tính giá trị mới = giá trị đã đọc + 1.
3. Ghi giá trị mới trở lại count.
~~~

Đây là mô hình logic của biểu thức, **không phải cam kết ba lệnh máy hoặc ba bytecode cố định**. Compiler và JVM có thể tối ưu nhưng vẫn phải tôn trọng Java Memory Model; việc có hai thao tác truy cập volatile trong một read-modify-write không tạo ra một transaction không thể bị xen vào.

Một interleaving hợp lệ:

| Thứ tự | Luồng A | Luồng B | Giá trị lưu trong count |
|---|---|---|---|
| 0 | — | — | 0 |
| 1 | Đọc 0 | — | 0 |
| 2 | — | Đọc 0 | 0 |
| 3 | Tính 1 | — | 0 |
| 4 | — | Tính 1 | 0 |
| 5 | Ghi 1 | — | 1 |
| 6 | — | Ghi 1 | 1 |

Cả hai lần đọc đều hợp lệ, vì tại thời điểm đó chưa có luồng nào ghi 1. Không có vấn đề “không nhìn thấy cập nhật” xảy ra; **cập nhật bị mất do hai lần tính độc lập cùng dựa trên trạng thái cũ**.

Những biểu thức sau cũng là thao tác ghép và **không trở thành atomic chỉ nhờ volatile**:

~~~java
counter = counter + 1;
counter += 1;
counter++;
++counter;
if (counter == 0) {
    counter = 1;
}
~~~

Đoạn trên chỉ minh họa các cách cập nhật trên biến `counter` có thể được khai báo trong phương thức/lớp; những biểu thức này không tự bảo vệ toàn bộ quan hệ đọc–kiểm tra–ghi. Với phép gán có điều kiện, hai luồng có thể cùng thấy 0 và cùng gán 1. Nếu phép tính phụ thuộc trạng thái hiện tại, hãy xem nó là một thao tác cần phân tích tính nguyên tử.

## 4. Chương trình Java 17+ tái hiện lost update có kiểm soát

Các bài thử đơn giản tạo 100 luồng rồi gọi `counter++` nhiều lần **có thể** tình cờ ra kết quả đúng, bởi lịch chạy luồng không được bảo đảm. Để chứng minh vấn đề mà không phụ thuộc vào may rủi, ví dụ sau dùng hai latch:

- Cả hai luồng **phải đọc giá trị cũ** rồi mới báo đã đọc.
- Chỉ khi cả hai đã đọc xong, luồng chính mới cho phép hai luồng ghi.
- Mỗi luồng ghi đúng `snapshot + 1`, nên hai lần tăng dựa trên cùng `snapshot == 0` và chỉ còn 1.
- Phần đối chiếu dùng thao tác `AtomicInteger.incrementAndGet()` cho kết quả 2.

Lưu trong **`VolatileLostUpdateDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class VolatileLostUpdateDemo {
    static final class VolatileCounter {
        volatile int value;
    }

    public static void main(String[] args) throws InterruptedException {
        VolatileCounter counter = new VolatileCounter();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch releaseWrites = new CountDownLatch(1);

        Runnable nonAtomicIncrement = () -> {
            int snapshot = counter.value;
            bothRead.countDown();
            try {
                releaseWrites.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            counter.value = snapshot + 1;
        };

        Thread first = new Thread(nonAtomicIncrement);
        Thread second = new Thread(nonAtomicIncrement);
        first.start();
        second.start();
        bothRead.await();
        releaseWrites.countDown();
        first.join();
        second.join();

        System.out.println("volatile count = " + counter.value);

        AtomicInteger atomicCounter = new AtomicInteger();
        Thread third = new Thread(atomicCounter::incrementAndGet);
        Thread fourth = new Thread(atomicCounter::incrementAndGet);
        third.start();
        fourth.start();
        third.join();
        fourth.join();

        System.out.println("atomic count   = " + atomicCounter.get());
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac VolatileLostUpdateDemo.java
java VolatileLostUpdateDemo
~~~

Kết quả chính xác:

~~~text
volatile count = 1
atomic count   = 2
~~~

Lưu ý: hai latch không phải thủ thuật để “làm cho volatile hỏng”; chúng chỉ ép một **interleaving hợp lệ** mà chương trình concurrent thực sự có thể gặp. `bothRead.await()` bảo đảm cả hai lần đọc đã diễn ra; `releaseWrites.countDown()` cho phép hai lần ghi tiếp tục. Không có lần ghi nào trước khi cả hai đã đọc, nên cả hai giữ `snapshot == 0`. Từng write vẫn là write volatile hợp lệ, nhưng không có cơ chế giải quyết lost update.

Khi dùng `AtomicInteger.incrementAndGet()`, mỗi lần tăng là một read-modify-write nguyên tử; hai lần tăng thành công từ 0 cho kết quả 2. `join()` chờ luồng hoàn thành và bảo đảm luồng chính đọc kết quả sau khi các thao tác trong luồng đã kết thúc theo quan hệ happens-before.

**Không dùng `Thread.sleep()` để khẳng định tái hiện race condition.** Sleep chỉ gợi ý thời gian, không ép thứ tự xảy ra. Cũng không kết luận `volatile++` an toàn vì một lần chạy 1 triệu phép tăng tình cờ cho đúng tổng.

## 5. Phân biệt visibility, atomicity và mutual exclusion

Ba khái niệm liên quan nhưng không thay thế được nhau:

- **Visibility:** một luồng nhìn thấy thông tin được luồng khác công bố đúng quan hệ bộ nhớ. `volatile`, monitor lock, các lớp atomic và nhiều primitive đồng bộ giúp thiết lập visibility.
- **Atomicity:** một hành động quan sát/cập nhật cần diễn ra như một đơn vị không bị xen giữa tại mức trừu tượng đang xét. `AtomicInteger.incrementAndGet()` bảo đảm cho **một biến atomic**; `synchronized` có thể bảo vệ một vùng logic lớn hơn.
- **Mutual exclusion:** tại một thời điểm chỉ một luồng giữ lock cần thiết để thực hiện vùng thao tác. `synchronized` hoặc `ReentrantLock` có thể bảo vệ cả chuỗi check-then-act trên nhiều biến nếu **mọi đường đọc/ghi liên quan tuân theo cùng lock**.

`volatile` cung cấp những quan hệ memory-order cần thiết nhưng **không cấp khóa**. `AtomicInteger` thực hiện một số thao tác nguyên tử trên một trạng thái, thường không cần monitor; tuy vậy **nhiều thao tác atomic nối nhau vẫn chưa chắc tạo thành một transaction**. Ví dụ `balance.get() >= amount` rồi `balance.addAndGet(-amount)` có thể bị luồng khác xen vào nếu logic yêu cầu thao tác kiểm tra và trừ phải đi cùng nhau.

## 6. Giải pháp khi cần tăng số đếm không mất cập nhật

### 6.1. AtomicInteger và AtomicLong

Khi nhiều luồng cập nhật **một bộ đếm nguyên** độc lập, chọn `AtomicInteger` (int) hoặc `AtomicLong` (long):

~~~java
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger counter = new AtomicInteger(0);
int after = counter.incrementAndGet();  // tăng nguyên tử, trả giá trị mới
int before = counter.getAndIncrement(); // tăng nguyên tử, trả giá trị trước tăng
int current = counter.get();           // đọc trạng thái atomic
~~~

Đây là đoạn minh họa đặt trong phương thức. Lưu ý hai hàm trả giá trị khác nhau, dù cả hai đều tăng đúng một đơn vị:

| Thao tác trên counter = 5 | Giá trị trả về | Giá trị lưu sau thao tác |
|---|---:|---:|
| `incrementAndGet()` | 6 | 6 |
| `getAndIncrement()` | 5 | 6 |
| `addAndGet(3)` | 8 | 8 |
| `getAndAdd(3)` | 5 | 8 |

Bảng xét từng thao tác **độc lập**, bắt đầu từ cùng giá trị 5. Những lần tăng này dùng thao tác nguyên tử (trong triển khai thường thông qua CAS hoặc intrinsic phù hợp), không chỉ thêm từ khóa volatile vào một `int`.

Nếu đếm dài hạn có thể vượt giới hạn `int`, dùng `AtomicLong`, nhưng kiểu `long` cũng hữu hạn. `AtomicInteger.incrementAndGet()` **vẫn có thể overflow và wrap** tại `Integer.MAX_VALUE`; nó chỉ bảo đảm atomicity, không tự kiểm tra điều kiện toán học. Nếu phải từ chối overflow, có thể dùng:

~~~java
counter.updateAndGet(Math::incrementExact);
~~~

`updateAndGet` áp dụng hàm tới giá trị trước đó và thử cập nhật nguyên tử; hàm có thể được JVM gọi lại khi cạnh tranh, nên phải **thuần (side-effect-free)**. `Math.incrementExact` ném `ArithmeticException` nếu cộng 1 vượt giới hạn int; không sinh side effect ngoài phép tính.

### 6.2. synchronized cho chuỗi thao tác

Khi muốn toàn bộ read-modify-write đi cùng nhau, một monitor bảo vệ cả phép tăng:

~~~java
public final class SynchronizedCounter {
    private int value;

    public synchronized void increment() {
        value++;
    }

    public synchronized int get() {
        return value;
    }
}
~~~

Đây là lớp có thể biên dịch độc lập trong file `SynchronizedCounter.java`, nhưng không có `main`. Hai phương thức `synchronized` instance sử dụng cùng monitor của **một instance**. Nếu mọi truy cập đến `value` đều thông qua các phương thức đồng bộ đúng quy tắc, `volatile` trên `value` là **không cần thiết**: monitor cung cấp cả mutual exclusion và quan hệ visibility.

Nếu viết `synchronized void increment()` nhưng ở chỗ khác vẫn trực tiếp đọc/ghi `value` mà không cùng lock, thiết kế có thể còn race hoặc thiếu visibility. Cần kiểm soát mọi đường truy cập, không chỉ một method.

### 6.3. ReentrantLock khi cần điều khiển lock tường minh

~~~java
import java.util.concurrent.locks.ReentrantLock;

public final class LockedCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int value;

    public void increment() {
        lock.lock();
        try {
            value++;
        } finally {
            lock.unlock();
        }
    }

    public int get() {
        lock.lock();
        try {
            return value;
        } finally {
            lock.unlock();
        }
    }
}
~~~

Lưu thành `LockedCounter.java` nếu muốn biên dịch lớp này riêng. Mẫu `try/finally` là cần thiết để luôn unlock khi phép tính ném exception. Mọi đường truy cập state phải dùng **cùng lock**. `ReentrantLock` hữu ích khi cần `tryLock`, timeout, interruptible locking hoặc `Condition`; đừng tự tăng độ phức tạp nếu `synchronized` đã đủ.

### 6.4. LongAdder cho bộ đếm thống kê tranh chấp cao

`LongAdder` thường có throughput tốt cho nhiều luồng cùng cộng vào **metric/counter**:

~~~java
import java.util.concurrent.atomic.LongAdder;

LongAdder requests = new LongAdder();
requests.increment();
requests.add(4L);
long observed = requests.sum();
~~~

Mã trên là ví dụ đặt trong một method. `sum()` có thể không phải **snapshot nguyên tử** khi các luồng khác vẫn cập nhật; một phép `sum` đồng thời với nhiều phép tăng không phù hợp nếu bạn cần số thứ tự duy nhất, giới hạn quota cứng hoặc quyết định phải chính xác tại một thời điểm. Nếu cần giá trị nguyên tử của một bộ đếm, ưu tiên `AtomicLong`; nếu cần nhất quán nhiều điều kiện, chọn cơ chế đồng bộ logic phù hợp.

## 7. CAS là gì? Vì sao compareAndSet hữu ích?

**Compare-and-set (CAS)** diễn đạt yêu cầu: “chỉ cập nhật thành giá trị mới nếu giá trị hiện tại vẫn là giá trị mình mong đợi”. Với `AtomicInteger`:

~~~java
AtomicInteger state = new AtomicInteger(0);

boolean success1 = state.compareAndSet(0, 1); // true
boolean success2 = state.compareAndSet(0, 2); // false
System.out.println(state.get());              // 1
~~~

Đoạn này nằm trong method có import `java.util.concurrent.atomic.AtomicInteger`. Lần thứ nhất thấy 0 và đặt thành 1 nguyên tử. Lần thứ hai kỳ vọng 0 nhưng trạng thái đã là 1, nên thất bại mà không ghi. CAS không thể bỏ qua bước xác minh trạng thái nếu yêu cầu là update điều kiện.

Vòng lặp CAS thường có dạng:

~~~java
int observed;
int updated;
do {
    observed = state.get();
    updated = Math.incrementExact(observed);
} while (!state.compareAndSet(observed, updated));
~~~

Giả sử `state` là `AtomicInteger` đã được khởi tạo. Nếu luồng khác cập nhật giữa `get` và `compareAndSet`, CAS thất bại, vòng lặp đọc lại và tính lại. Không phải phép cộng tự nhiên đã atomic: **thao tác commit có điều kiện** mới giúp ngăn mất cập nhật.

Có thể dùng `incrementAndGet` thay vòng lặp trên khi không cần chính sách riêng; triển khai của lớp atomic đã cung cấp thao tác phù hợp. Vòng lặp CAS có thể lặp nhiều lần khi tranh chấp cao, nên tránh gọi API có side effect, gửi email, ghi log nghiệp vụ hoặc trừ số dư ngoài hệ thống từ bên trong hàm có thể bị tính lại.

**Chú ý phạm vi:** CAS của một `AtomicInteger` chỉ cập nhật nguyên tử một giá trị đó. Nếu cần đồng bộ số dư và một lịch sử giao dịch, phải có cơ chế phối hợp toàn bộ state; CAS một bộ đếm không tự trở thành database transaction.

## 8. Khi nào volatile một mình là hợp lý?

`volatile` vẫn quan trọng và hữu dụng. Ví dụ một luồng phụ thực hiện công việc theo cờ dừng được điều khiển bởi luồng khác:

~~~java
public final class StopFlag {
    private volatile boolean stopped;

    public void requestStop() {
        stopped = true;
    }

    public boolean shouldStop() {
        return stopped;
    }
}
~~~

Đây là lớp có thể biên dịch trong `StopFlag.java`. Với mô hình **một cờ điều khiển đơn giản**, ghi `true` và đọc cờ là các truy cập độc lập; không có việc nhiều writer cần phối hợp cộng dồn. Luồng thực hiện công việc phải kiểm tra `shouldStop()` theo chu kỳ và biết cách kết thúc. Trong tác vụ có blocking I/O/sleep/wait, `Thread.interrupt()` hoặc cơ chế hủy cấp cao có thể cần thiết; một cờ không tự đánh thức luồng đang chờ.

Một trường hợp khác là **công bố tham chiếu cấu hình bất biến**: tạo đầy đủ đối tượng immutable rồi ghi tham chiếu vào trường volatile; các reader đọc tham chiếu đó có thể thấy trạng thái đã được công bố. Tuy nhiên, nếu bên trong đối tượng có `ArrayList` mutable và các luồng thay đổi danh sách mà không đồng bộ, `volatile` trên tham chiếu không bảo vệ những lần thay đổi đó.

### volatile trên tham chiếu, mảng, wrapper và số 64 bit

- `volatile int[] numbers` làm lần đọc/ghi **tham chiếu mảng** có ngữ nghĩa volatile; nó **không khiến `numbers[0]++` atomic**. Khi cần atomic cho từng phần tử, xem `AtomicIntegerArray` hoặc lock.
- `volatile Integer count` cũng không biến `count++` thành atomic; mỗi lần tăng còn có unboxing/boxing và thay tham chiếu. Nếu giá trị có thể null, unboxing còn có thể ném `NullPointerException`.
- `volatile long value` bảo đảm một lần đọc/ghi biến long là atomic theo Java Memory Model; nhưng `value++` vẫn không atomic. Đừng nhầm atomic access tới 64 bit với atomic read-modify-write.
- `volatile` không bảo đảm thứ tự xử lý logic trên **nhiều biến**. Hai field volatile riêng không tạo ra snapshot nhất quán nếu writer cập nhật chúng từng bước.

## 9. Check-then-act và nhiều biến: lỗi khó hơn counter++

Lỗi mất cập nhật chỉ là ví dụ đơn giản. Xét một quy tắc nghiệp vụ:

~~~java
if (availableSeats > 0) {
    availableSeats--;
    reserveSeat();
}
~~~

Đây là pseudocode Java nằm trong method có các biến/phương thức tương ứng. Ngay cả khi `availableSeats` là volatile, **hai luồng có thể cùng thấy 1, cùng đi vào nhánh, và cùng gọi `reserveSeat()`**. Kết quả có thể là hai lượt đặt chỗ dù chỉ còn một ghế. `availableSeats--` bị race, và gọi `reserveSeat()` còn là side effect cần bảo vệ.

Nếu chỉ cần giảm một biến nguyên với điều kiện lớn hơn 0, có thể viết một vòng CAS để đọc điều kiện và cập nhật cùng nhau. Nhưng nếu `reserveSeat()` cần lưu dữ liệu qua DB, gọi dịch vụ ngoài hoặc phối hợp nhiều biến, còn cần transaction/lock/idempotency thích hợp ở đúng hệ thống giữ state. Một `AtomicInteger` trong JVM **không bảo vệ dữ liệu trên nhiều tiến trình hoặc nhiều instance dịch vụ**.

Tương tự, công thức `if (balance.get() >= withdrawal) balance.addAndGet(-withdrawal)` không phải hành động nguyên tử chỉ vì `balance` là `AtomicInteger`: check và update tách rời. Phải đặt điều kiện và update trong một CAS loop hoặc cùng critical section; khi có nhiều nguồn dữ liệu phải mở rộng giải pháp cho toàn bộ invariant.

## 10. Kiểm thử race condition đúng cách

Đừng dùng sự ngẫu nhiên của thread scheduler làm điều kiện thành công của test. Một chương trình chạy 1.000.000 vòng có thể **không gặp interleaving lỗi trên máy hiện tại**, nhưng không chứng minh an toàn. Lệnh `Thread.sleep(100)` chỉ gây trễ; nó không cung cấp hợp đồng bảo đảm một luồng đã đọc trước khi luồng khác ghi.

Cách kiểm thử hữu ích:

1. **Tái hiện lịch chạy có chủ đích:** như hai `CountDownLatch` trong `VolatileLostUpdateDemo`, ép cả hai đọc cùng trạng thái cũ rồi cùng ghi.
2. **Khẳng định bất biến:** với n luồng, mỗi luồng tăng m lần bằng phương thức atomic/lock đúng, sau `join` giá trị mong đợi là `n × m` nếu không overflow và không lần nào thất bại.
3. **Kiểm thử trạng thái biên:** counter gần `Integer.MAX_VALUE`, giá trị âm, overflow, thao tác reset đồng thời với tăng, lỗi interrupt hoặc exception.
4. **Kiểm thử toàn bộ state:** nếu nghiệp vụ có điều kiện check-then-act hoặc nhiều trường, test phải bao quát điều kiện và hành động, không chỉ tăng số nguyên.
5. **Quan sát và kiểm toán:** đối với tác vụ bất đồng bộ, dùng latch, barrier, executor và timeout hợp lý để tránh test treo; ghi nhận lỗi từ thread con thay vì để thread chết mà test không biết.
6. **Phân biệt kiểm thử chức năng và stress:** stress test hữu ích để tìm lỗi nhưng không thay cho phân tích happens-before và tính nguyên tử của giao thức.

### Ví dụ đối chiếu giá trị mong đợi

Khi dùng `AtomicInteger`:

~~~java
AtomicInteger safe = new AtomicInteger();
safe.incrementAndGet();
safe.incrementAndGet();

if (safe.get() != 2) {
    throw new AssertionError("Lost atomic increments");
}
~~~

Đây là đoạn minh họa đặt trong một method có import `AtomicInteger`. Để kiểm chứng trong đa luồng, cần tạo/đồng bộ thread và chờ tất cả hoàn tất trước assertion, tương tự ví dụ chạy được ở phần 4. `get()` trong lúc các luồng còn chạy cho kết quả tại thời điểm đọc, không tự mang nghĩa “tổng cuối”.

### Không nhầm visibility sau join với tính nguyên tử

Sau khi gọi `thread.join()` thành công, luồng đang chờ có quan hệ happens-before cần thiết với các thao tác luồng vừa kết thúc; nhưng điều đó **không phục hồi cập nhật đã mất** trước khi join. Trong ví dụ hai luồng ghi cùng 1, main thấy 1 chính xác và đầy đủ sau join. Vấn đề nằm ở việc **hai luồng đã thực hiện hai read-modify-write không nguyên tử**, không phải lỗi đọc kết quả cuối.

## 11. Chọn cơ chế theo yêu cầu

| Nhu cầu | Lựa chọn thường phù hợp | Giới hạn cần nhớ |
|---|---|---|
| Một cờ dừng/công bố trạng thái với giao thức đơn giản | `volatile boolean`/tham chiếu volatile | Không đồng bộ side effect phức tạp hoặc nhiều trạng thái |
| Nhiều luồng tăng một số nguyên chính xác | `AtomicInteger`/`AtomicLong` | Có thể overflow; một thao tác atomic không thay transaction |
| Nhiều luồng cộng số metric với mức tranh chấp cao | `LongAdder` | `sum()` không phải snapshot atomic khi có cập nhật |
| Cập nhật điều kiện một trường nguyên | CAS hoặc `updateAndGet` với hàm thuần | Cần xử lý retry và cạnh tranh |
| Cần bảo vệ một chuỗi thao tác hoặc nhiều trường | `synchronized` hoặc `ReentrantLock` | Mọi đường truy cập phải dùng cùng lock |
| Cần nhất quán trạng thái của DB/đa tiến trình | Transaction, lock/atomic update cấp lưu trữ | Bộ nhớ atomic trong một JVM không đủ |

Không có từ khóa nào thay cho việc xác định **đơn vị nguyên tử của bài toán**. Nếu đơn vị đó chỉ là một lần tăng, một thao tác `AtomicInteger` có thể là đủ. Nếu đơn vị là “kiểm tra hạn mức, tạo hóa đơn, trừ hạn mức và ghi lịch sử”, cần giải pháp bảo vệ cả quy trình ở phạm vi chia sẻ state thực tế.

## 12. Lỗi phổ biến

- **Cho rằng volatile khiến ++ atomic:** một lần đọc volatile và một lần ghi volatile vẫn bị các luồng khác xen giữa.
- **Đổi `volatile int` thành `volatile long` để “an toàn hơn”:** mở rộng miền giá trị nhưng không sửa lost update.
- **Dùng `AtomicInteger.get()` rồi `set(get() + 1)`:** hai lệnh atomic riêng lẻ vẫn **không phải atomic increment**. Dùng `incrementAndGet` hoặc CAS đúng.
- **Cho rằng `synchronized` trên một method là đủ dù các nơi khác ghi không lock:** mọi đường truy cập tới invariant phải thống nhất.
- **Dùng `LongAdder` để cấp ID duy nhất hoặc xử lý hạn mức cứng:** `sum` không cung cấp snapshot tuyến tính hóa theo cách đó.
- **Đọc hai field volatile rồi tin snapshot nhất quán:** mỗi field có thể đến từ một giai đoạn cập nhật khác nhau.
- **Chỉ chạy thử nhiều vòng rồi kết luận không có race:** race là vấn đề tính đúng với mọi lịch chạy cho phép, không được chứng minh bằng một vài lần chạy.
- **Giải quyết bằng `sleep`, `yield` hoặc `Thread.onSpinWait`:** chúng có thể tác động lịch chạy/hiệu năng nhưng không biến read-modify-write thành atomic.
- **Đổi counter sang atomic nhưng vẫn có những chỗ gán thẳng/reset không phối hợp:** tất cả phép thay đổi trạng thái phải tuân theo cùng contract.
- **Bỏ qua overflow:** atomic increment của int không đồng nghĩa tăng chính xác trên tập số nguyên toán học không giới hạn.

## 13. Bài tập tự luyện và đáp án

**Bài 1.** Hai thread cùng thực hiện `volatileCounter++` khi giá trị ban đầu bằng 5. Kết quả có thể là bao nhiêu nếu mỗi thread chạy một lần và cả hai đã hoàn tất? Giải thích lịch chạy gây mất cập nhật.

**Bài 2.** Thay `counter++` bằng `atomicCounter.set(atomicCounter.get() + 1)` có giải quyết race không? Nếu không, phải sửa thế nào?

**Bài 3.** Hãy giải thích vì sao `volatile boolean stop` có thể phù hợp cho một cờ dừng đơn giản nhưng `volatile int soldTickets` không đủ cho thao tác “nếu còn vé thì bán một vé”.

**Bài 4.** Một collector dùng `LongAdder` để đếm request và cần hiển thị tổng gần thời gian thực. Nếu ở thời điểm xuất báo cáo phải có tổng chính xác, nhất quán với danh sách request cùng một thời điểm, chỉ gọi `sum()` có đủ không?

**Bài 5.** Vì sao assertion sau `join` vẫn có thể quan sát `1` trong ví dụ hai increment? `join` đã bảo đảm visibility hay chưa?

**Gợi ý đáp án:**

1. Có thể là **6 hoặc 7**: hai luồng đều đọc 5, đều ghi 6 dẫn tới 6; hoặc một luồng hoàn tất trước khi luồng kia đọc thì có 7. Với đúng hai lần tăng từ 5, không có reset/overflow, không hợp lý để lấy 8.
2. Không; `get` và `set` tách rời. Dùng `incrementAndGet` hoặc vòng CAS; nếu cần điều kiện/side effect thì bảo vệ toàn bộ logic.
3. Cờ dừng là thông điệp độc lập, còn bán vé là chuỗi kiểm tra số lượng rồi trừ/ghi nhận; nhiều thread có thể cùng vượt qua kiểm tra.
4. Không đủ nếu yêu cầu snapshot nhất quán nhiều cấu trúc đang cập nhật; cần cơ chế snapshot/transaction/synchronization đáp ứng invariant.
5. `join` đã cung cấp quan hệ happens-before giữa hoạt động thread kết thúc và luồng chờ; main thấy kết quả lưu cuối là 1, nhưng bản thân hai phép tính trước đó đã mất một lượt tăng.

## Tài liệu tham khảo Java SE 17

- [Java Language Specification §17.4 — Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4)
- `AtomicInteger`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html
- `CountDownLatch`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- `LongAdder`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html
- `ReentrantLock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html

**Điểm cần nhớ:** `volatile` giúp xác lập visibility và ordering theo Java Memory Model; `counter++` vẫn là read-modify-write nhiều bước. Nếu cần bảo toàn mọi lần tăng, dùng thao tác atomic thực sự hoặc bảo vệ toàn bộ phép cập nhật bằng lock.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-numbers-003">AtomicInteger và AtomicLong</a>

<details>
<summary>Xem chi tiết</summary>

# AtomicInteger và AtomicLong trong Java: cập nhật số an toàn giữa nhiều luồng

Trong chương trình có nhiều luồng, biểu thức `counter++` **không phải một thao tác nguyên tử** chỉ vì `counter` có kiểu nguyên thủy `int` hoặc được khai báo `volatile`. Khi hai luồng cùng đọc giá trị cũ rồi cùng ghi giá trị mới, một lần tăng có thể bị mất (*lost update*).

`AtomicInteger` và `AtomicLong` trong package `java.util.concurrent.atomic` cung cấp các thao tác nguyên tử như tăng, cộng, đổi và cập nhật có điều kiện trên **một giá trị số nguyên**. Chúng phù hợp với bộ đếm, số thứ tự, cờ trạng thái số học hoặc một trạng thái được mã hóa bằng số cần cập nhật giữa nhiều luồng, khi không cần khóa bao quanh toàn bộ khối nghiệp vụ.

Bài này giải thích ý nghĩa nguyên tử, cách sử dụng API, cơ chế CAS, bảo đảm bộ nhớ, giới hạn với nhiều biến, tràn số và trường hợp chọn cấu trúc khác.

## 1. Vì sao `counter++` có thể làm mất số lần tăng?

Xét một trường được chia sẻ:

```java
private int counter = 0;

void increase() {
    counter++; // Không bảo đảm nguyên tử khi có nhiều luồng cùng gọi
}
```

Ở mức logic, biểu thức tăng gồm ba việc: đọc `counter`, cộng 1, ghi lại kết quả. Một lịch xen kẽ có thể như sau:

| Thời điểm | Luồng A | Luồng B | Giá trị chung |
|---|---|---|---:|
| Ban đầu | | | 10 |
| 1 | Đọc 10 | | 10 |
| 2 | | Đọc 10 | 10 |
| 3 | Tính 11 và ghi 11 | | 11 |
| 4 | | Tính 11 và ghi 11 | 11 |

Hai luồng đều đã thực hiện một lần tăng nhưng giá trị cuối là **11 thay vì 12**. Ví dụ này là một lịch có thể xảy ra; kết quả cụ thể của một chương trình chạy nhiều luồng còn phụ thuộc lịch thực thi.

Nếu thay trường bằng `volatile int counter`, thao tác đọc/ghi có bảo đảm visibility tương ứng với volatile, **nhưng tổ hợp đọc → cộng → ghi vẫn không thành một thao tác nguyên tử**. `volatile` không sửa được lost update này.

Có thể bảo vệ phép tăng bằng khóa `synchronized`, hoặc dùng:

```java
import java.util.concurrent.atomic.AtomicInteger;

private final AtomicInteger counter = new AtomicInteger();

void increase() {
    counter.incrementAndGet();
}
```

`incrementAndGet()` là một phép cập nhật nguyên tử. Nếu các luồng chỉ thay đổi bộ đếm thông qua các phương thức nguyên tử trên cùng đối tượng, các lần tăng sẽ không bị mất vì thao tác đọc–sửa–ghi bị xen giữa.

## 2. AtomicInteger và AtomicLong khác nhau thế nào?

| Đặc điểm | `AtomicInteger` | `AtomicLong` |
|---|---|---|
| Giá trị lưu trữ | `int` có dấu, 32 bit | `long` có dấu, 64 bit |
| Phạm vi | -2.147.483.648 đến 2.147.483.647 | -9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 |
| Tạo với giá trị ban đầu | `new AtomicInteger(10)` | `new AtomicLong(10L)` |
| Đọc và gán | `get()`, `set(...)` | `get()`, `set(...)` |
| Tăng, giảm, cộng | `incrementAndGet()`, `addAndGet(...)`, ... | Các phương thức cùng tên với tham số/kết quả `long` |
| Cập nhật có điều kiện | `compareAndSet(...)`, `compareAndExchange(...)` | Các phương thức tương ứng trên `long` |
| Biểu thức ví dụ | `counter.incrementAndGet()` | `sequence.incrementAndGet()` |

Cả hai là **lớp đối tượng**, khác `int`/`long` nguyên thủy. Ví dụ `AtomicInteger counter = new AtomicInteger(5)` lưu trạng thái hiện tại bên trong đối tượng, còn biến `counter` là tham chiếu đến đối tượng đó. Cách dùng thường là giữ tham chiếu cố định bằng `final`, rồi thao tác trên giá trị nguyên tử:

```java
private final AtomicLong sequence = new AtomicLong(100L);

long nextSequence() {
    return sequence.incrementAndGet();
}
```

`final` ở đây ngăn gán lại tham chiếu `sequence`. Nó không làm giá trị bên trong đối tượng bất biến; `incrementAndGet()` vẫn cập nhật giá trị đó.

### Khởi tạo

```java
AtomicInteger zeroCounter = new AtomicInteger();     // ban đầu 0
AtomicInteger withInitial = new AtomicInteger(42);  // ban đầu 42
AtomicLong longCounter = new AtomicLong(1_000_000L);
```

Các đoạn mã rời trong bài chỉ minh họa cú pháp trong một lớp/phương thức phù hợp; chương trình Java độc lập có `main` xuất hiện ở phần ví dụ thực hành.

## 3. Các thao tác cơ bản và giá trị trả về

Một ưu điểm của API này là tên phương thức cho biết rõ nó trả về **giá trị trước** hay **giá trị sau** cập nhật.

| Phương thức | Ý nghĩa | Kết quả trả về |
|---|---|---|
| `get()` | Đọc trạng thái hiện tại | Giá trị đọc được |
| `set(v)` | Gán trạng thái mới | `void` |
| `getAndSet(v)` | Thay bằng `v` một cách nguyên tử | Giá trị cũ |
| `getAndIncrement()` | Tăng 1 | Giá trị trước khi tăng |
| `incrementAndGet()` | Tăng 1 | Giá trị sau khi tăng |
| `getAndDecrement()` | Giảm 1 | Giá trị trước khi giảm |
| `decrementAndGet()` | Giảm 1 | Giá trị sau khi giảm |
| `getAndAdd(delta)` | Cộng `delta` | Giá trị cũ |
| `addAndGet(delta)` | Cộng `delta` | Giá trị mới |
| `compareAndSet(expected, update)` | Chỉ đổi khi giá trị hiện tại bằng `expected` | `boolean` báo thành công |
| `compareAndExchange(expected, update)` | So sánh rồi có thể đổi | Giá trị quan sát được trước phép so sánh/đổi |
| `getAndUpdate(fn)` | Cập nhật bằng hàm trên trạng thái hiện tại | Giá trị cũ |
| `updateAndGet(fn)` | Cập nhật bằng hàm trên trạng thái hiện tại | Giá trị mới |
| `getAndAccumulate(x, fn)` | Kết hợp giá trị hiện tại với `x` bằng hàm hai đối số | Giá trị cũ |
| `accumulateAndGet(x, fn)` | Kết hợp hai giá trị rồi cập nhật | Giá trị mới |

Các phương thức tương ứng đều có trên `AtomicInteger` và `AtomicLong`, với chữ ký sử dụng `int`/`IntUnaryOperator`/`IntBinaryOperator` hoặc `long`/`LongUnaryOperator`/`LongBinaryOperator` theo lớp.

Ví dụ khác biệt “trước và sau”:

```java
AtomicInteger x = new AtomicInteger(5);

int oldValue = x.getAndIncrement(); // oldValue = 5, x = 6
int newValue = x.incrementAndGet(); // newValue = 7, x = 7
int beforeAdd = x.getAndAdd(3);     // beforeAdd = 7, x = 10
int afterAdd = x.addAndGet(2);      // afterAdd = 12, x = 12
```

Không nên viết `get()` rồi `set(get() + 1)` để thay thế `incrementAndGet()`: hai lệnh riêng không hợp thành một thao tác nguyên tử.

## 4. Cơ chế CAS và điểm tuyến tính hóa

**CAS** (*compare-and-set*, so sánh và gán có điều kiện) nhận ba yếu tố logic: giá trị đang lưu, giá trị mong đợi và giá trị mới. Chỉ khi giá trị hiện tại bằng giá trị mong đợi, phép thay đổi mới xảy ra.

```java
AtomicInteger state = new AtomicInteger(10);

boolean first = state.compareAndSet(10, 20); // true
boolean second = state.compareAndSet(10, 30); // false
int current = state.get();                  // 20
```

Sau phép CAS đầu, trạng thái là 20, vì vậy phép thứ hai với `expected = 10` thất bại. Nó **không** ghi 30; trạng thái cuối vẫn là 20.

`compareAndExchange(expected, newValue)` cũng so sánh rồi có thể cập nhật nguyên tử, nhưng trả về **giá trị thực tế quan sát được** tại lúc thao tác thay vì `boolean`:

```java
AtomicLong slot = new AtomicLong(5L);

long observed1 = slot.compareAndExchange(5L, 8L);  // 5; slot = 8
long observed2 = slot.compareAndExchange(5L, 9L);  // 8; slot vẫn = 8
```

Với những phép đọc–sửa–ghi nguyên tử, ta có thể lý luận như thể mỗi phép có một **điểm tuyến tính hóa** (linearization point): tại một thời điểm trừu tượng, cả thao tác trở nên có hiệu lực đối với các luồng cùng quan sát biến nguyên tử đó. Các thao tác có thể diễn ra đồng thời, nhưng tác động lên **một đối tượng atomic** có thứ tự tương thích với bảo đảm nguyên tử của API.

### Vòng lặp CAS để tăng có điều kiện

Giả sử chỉ muốn tăng bộ đếm khi giá trị còn dưới 100:

```java
static boolean incrementBelow100(AtomicInteger counter) {
    while (true) {
        int current = counter.get();
        if (current >= 100) {
            return false;
        }
        if (counter.compareAndSet(current, current + 1)) {
            return true;
        }
        // Luồng khác đã cập nhật trước: đọc lại và thử lại.
    }
}
```

Đây là **phương thức minh họa**, cần đặt trong một lớp và import `AtomicInteger`. Việc kiểm tra `current < 100` và việc ghi `current + 1` đi qua điều kiện CAS dựa trên cùng giá trị mong đợi; nếu một luồng khác đổi giá trị thì phép CAS thất bại và vòng lặp kiểm tra lại.

Trong ví dụ này điều kiện `current >= 100` còn bảo vệ trước nguy cơ tràn do cộng 1, vì mọi lần tăng thành công đều diễn ra khi `current < 100`. Nếu cho phép các thao tác khác làm bộ đếm giảm hoặc thay đổi tùy ý, cần định nghĩa rõ hợp đồng nghiệp vụ để bảo đảm giới hạn.

**Không khẳng định mọi phép CAS luôn thành công ở lần đầu:** dưới tranh chấp, nhiều luồng có thể phải thử lại. CAS tránh dùng một khóa ứng dụng cho thao tác đơn lẻ, nhưng vẫn tiêu tốn CPU nếu có tranh chấp cao.

### Hàm cập nhật có thể chạy nhiều lần

`updateAndGet(fn)` và `getAndUpdate(fn)` thuận tiện để gói vòng lặp cập nhật trong API:

```java
AtomicInteger count = new AtomicInteger(7);
int capped = count.updateAndGet(v -> Math.min(v + 1, 10)); // 8
```

Khi có tranh chấp, hàm `fn` **có thể được gọi nhiều lần** trước khi cập nhật thành công. Hàm nên **không có tác dụng phụ** và cho kết quả ổn định theo tham số truyền vào. Tránh gọi API mạng, ghi cơ sở dữ liệu, tăng một bộ đếm khác, log nghiệp vụ một lần duy nhất hoặc sinh ngẫu nhiên không kiểm soát ngay bên trong lambda: hàm có thể chạy lại và gây tác dụng phụ trùng lặp.

Đối với cập nhật cần nhiều bước hoặc nhiều tài nguyên, nên xem xét khóa hoặc một cơ chế phối hợp khác.

## 5. Bảo đảm visibility và quan hệ với volatile

Các phương thức chuẩn `get()` và `set(v)` của `AtomicInteger`/`AtomicLong` có hiệu ứng bộ nhớ tương đương **volatile read** và **volatile write** đối với giá trị atomic. Các thao tác như `compareAndSet` cung cấp ngữ nghĩa nguyên tử cùng ràng buộc bộ nhớ tương ứng theo tài liệu JDK.

Điều này giải quyết hai nhu cầu khác nhau:

1. **Visibility (khả năng quan sát):** cập nhật có thể được luồng khác quan sát thông qua những thao tác đọc phù hợp theo Java Memory Model.
2. **Atomic read–modify–write:** hai luồng cùng tăng không thể làm mất một lần tăng nếu đều dùng `incrementAndGet()` trên cùng đối tượng.

`volatile` đơn thuần hỗ trợ nhu cầu thứ nhất cho thao tác trên một trường, nhưng không tự làm phép `x++` nguyên tử. `AtomicInteger` bổ sung các phép cập nhật kết hợp nguyên tử.

Ví dụ về thứ tự công bố dữ liệu:

```java
class Holder {
    private int data;
    private final AtomicInteger ready = new AtomicInteger(0);

    void publish() {
        data = 42;
        ready.set(1);
    }

    int consumeAfterReady() {
        while (ready.get() == 0) {
            Thread.onSpinWait();
        }
        return data;
    }
}
```

Nếu một luồng thực hiện `publish()` và luồng khác nhìn thấy `ready.get() == 1` từ lần `set(1)` đó, quan hệ **happens-before** thông qua thao tác kiểu volatile khiến phép ghi `data = 42` trước khi phát cờ có thể được luồng đọc nhìn thấy, theo các giả định không có cập nhật cạnh tranh khác lên `data` và `ready`. Đây là **ví dụ giải thích memory ordering**, không phải khuyến nghị dùng vòng lặp bận trong ứng dụng thực tế; thường nên dùng `CountDownLatch`, `Future`, `BlockingQueue` hoặc công cụ đồng bộ phù hợp.

Một số API mở rộng có ngữ nghĩa bộ nhớ khác, như `lazySet` (release), `getAcquire`, `setRelease`, `getOpaque`, `setOpaque`, `getPlain` và `setPlain` ở các phiên bản Java hỗ trợ. **Không tùy tiện thay `get()/set()` bằng những biến thể yếu hơn** nếu chưa chứng minh được yêu cầu đồng bộ; các ví dụ và lý luận cơ bản ở đây dùng `get()`/`set()` và phép CAS chuẩn.

## 6. Chương trình Java 17+ hoàn chỉnh

Lưu vào tệp `AtomicNumbersDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AtomicNumbersDemo {
    public static void main(String[] args) throws InterruptedException {
        final int workers = 4;
        final int iterations = 10_000;
        AtomicInteger counter = new AtomicInteger();
        AtomicLong total = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int w = 0; w < workers; w++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        counter.incrementAndGet();
                        total.addAndGet(2L);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }

        start.countDown();
        done.await();
        System.out.println("counter = " + counter.get());
        System.out.println("total = " + total.get());

        AtomicInteger state = new AtomicInteger(5);
        System.out.println("getAndIncrement = "
                + state.getAndIncrement() + ", now = " + state.get());
        System.out.println("incrementAndGet = " + state.incrementAndGet());
        System.out.println("getAndAdd = "
                + state.getAndAdd(3) + ", now = " + state.get());
        System.out.println("addAndGet = " + state.addAndGet(2));
        System.out.println("CAS = " + state.compareAndSet(12, 20)
                + ", now = " + state.get());
        System.out.println("compareAndExchange = "
                + state.compareAndExchange(20, 30)
                + ", now = " + state.get());
        System.out.println("updateAndGet = "
                + state.updateAndGet(v -> v * 2));
        System.out.println("accumulateAndGet = "
                + state.accumulateAndGet(5, Integer::sum));

        System.out.println("int wrap = "
                + new AtomicInteger(Integer.MAX_VALUE)
                        .incrementAndGet());
        System.out.println("long wrap = "
                + new AtomicLong(Long.MAX_VALUE)
                        .incrementAndGet());
    }
}
```

Biên dịch và chạy (JDK 17+):

```bash
javac AtomicNumbersDemo.java
java AtomicNumbersDemo
```

**Stdout khi các luồng hoàn tất bình thường, không bị ngắt:**

```text
counter = 40000
total = 80000
getAndIncrement = 5, now = 6
incrementAndGet = 7
getAndAdd = 7, now = 10
addAndGet = 12
CAS = true, now = 20
compareAndExchange = 20, now = 30
updateAndGet = 60
accumulateAndGet = 65
int wrap = -2147483648
long wrap = -9223372036854775808
```

Bốn luồng cùng đếm, mỗi luồng tăng `counter` 10.000 lần và cộng `total` thêm 2L cho mỗi vòng. Vì mỗi thao tác trên từng atomic là nguyên tử, kết quả cuối sau khi toàn bộ vòng lặp chạy đủ lần là `4 × 10.000 = 40.000` và `4 × 10.000 × 2 = 80.000`. `CountDownLatch` bảo đảm `main` chờ các luồng hoàn thành trước khi in ra; nó không thay thế vai trò của `AtomicInteger`/`AtomicLong` đối với các cập nhật đồng thời.

Trong ví dụ, nếu một luồng bị interrupt trong khi chờ `start.await()`, nó sẽ thoát và có thể không thực hiện đủ số vòng. Dòng stdout trên phản ánh điều kiện chạy thông thường không bị ngắt; khi thiết kế hệ thống thật phải quyết định chính sách xử lý hủy tác vụ và kết quả một phần.

Các dòng thao tác trên `state` minh họa rõ giá trị trước/sau, CAS thành công và `compareAndExchange` trả giá trị đã quan sát. Hai dòng cuối nhắc rằng **atomic không có nghĩa kiểm tra tràn số**.

## 7. Atomic không có nghĩa mọi thứ đều an toàn

### 7.1. Mỗi phép cập nhật riêng lẻ được bảo vệ, không phải một giao dịch nhiều biến

Ví dụ dùng hai atomic:

```java
AtomicInteger stock = new AtomicInteger(10);
AtomicInteger sold = new AtomicInteger(0);

stock.decrementAndGet();
sold.incrementAndGet();
```

Mỗi dòng nguyên tử trên **một đối tượng riêng**, nhưng giữa hai dòng một luồng khác có thể đọc thấy `stock` đã giảm mà `sold` chưa tăng. Hai biến không tạo thành một **transaction** thống nhất.

Nếu yêu cầu bất biến `stock + sold == 10` tại mọi quan sát hợp lệ, cần một cơ chế đồng bộ điều phối cả hai trường, như `synchronized`/`Lock`, hoặc đóng gói toàn bộ trạng thái bất biến vào **một** đối tượng immutable và thay tham chiếu bằng `AtomicReference` với một thuật toán đúng. `AtomicInteger` và `AtomicLong` tự chúng không thể làm nguyên tử một cập nhật gồm nhiều biến.

### 7.2. Đọc rồi hành động riêng lẻ vẫn có race

Sai lầm dễ gặp:

```java
if (counter.get() < 100) {
    counter.incrementAndGet();
}
```

Hai luồng có thể đều quan sát 99 và cùng tăng: kết quả lên 101. Cả hai lần tăng đều nguyên tử, nhưng toàn bộ **kiểm tra → quyết định → tăng** chưa nguyên tử. Phải dùng vòng lặp CAS như ở phần 4 hoặc một cơ chế khóa thích hợp để kiểm tra và cập nhật theo cùng một trạng thái.

### 7.3. `AtomicLong` không ngăn tràn

`AtomicInteger` dùng số nguyên `int`; `AtomicLong` dùng `long`. Các phép cộng/tăng theo ngữ nghĩa số nguyên Java vẫn **wrap around** khi vượt miền có dấu:

```java
AtomicInteger a = new AtomicInteger(Integer.MAX_VALUE);
System.out.println(a.incrementAndGet()); // -2147483648

AtomicLong b = new AtomicLong(Long.MAX_VALUE);
System.out.println(b.incrementAndGet()); // -9223372036854775808
```

Nếu nghiệp vụ đòi **không bao giờ vượt giới hạn**, hãy xử lý điều kiện bằng CAS loop hoặc logic giới hạn phù hợp. Nếu cần đếm chính xác lớn hơn phạm vi `long`, phải dùng biểu diễn khác (ví dụ `BigInteger` với cơ chế đồng bộ/`AtomicReference`), không chỉ thay `AtomicInteger` bằng `AtomicLong` rồi giả định vô hạn.

### 7.4. CAS so sánh giá trị hiện tại, nhưng có thể gặp ABA

Giả sử trạng thái biến đổi theo chuỗi `A → B → A`. Một luồng giữ giá trị cũ `A` rồi dùng CAS mong đợi `A` có thể thành công vì hiện tại lại là `A`, dù đã xảy ra các thay đổi trung gian. Đây là **ABA problem**. Đối với bộ đếm đơn giản, ABA có thể không ảnh hưởng đến tính đúng; với thuật toán lock-free dựa trên lịch sử trạng thái, cần xem xét phiên bản đi kèm (ví dụ đếm phiên bản hoặc `AtomicStampedReference` với tham chiếu), hoặc một thiết kế đồng bộ khác. `AtomicInteger` không tự phát hiện lịch sử `A → B → A`.

### 7.5. Atomic không bảo đảm công bằng thứ tự luồng

Các phép CAS có thể thất bại và thử lại nhiều lần khi contention cao. Không nên suy ra tất cả luồng sẽ tiến triển công bằng, hay mọi thao tác đều thực thi với thời gian cố định trong mọi lịch chạy. Thiết kế cần phù hợp yêu cầu throughput, latency và khả năng tiến triển dưới tải.

## 8. Khi nào chọn LongAdder, synchronized hoặc AtomicReference?

**AtomicInteger/AtomicLong** phù hợp khi cần các thao tác **chính xác, riêng lẻ** như `getAndIncrement()`, `compareAndSet()` hoặc lấy một số thứ tự duy nhất.

Khi nhiều luồng cùng cập nhật một bộ đếm tích lũy và không cần CAS/số thứ tự duy nhất từ mỗi lần tăng, `LongAdder` có thể giảm tranh chấp bằng cách phân tán các cập nhật:

```java
import java.util.concurrent.atomic.LongAdder;

LongAdder requests = new LongAdder();
requests.increment();
requests.add(5L);

long approxAtMoment = requests.sum(); // sau hai cập nhật tuần tự: 6
```

**Cần phân biệt:** `LongAdder.sum()` không phải một snapshot nguyên tử đối với những cập nhật đồng thời đang diễn ra; không dùng `LongAdder` để gán ID duy nhất hoặc thực thi điều kiện CAS dựa trên một giá trị quan sát chính xác. So sánh hiệu năng bằng benchmark thực tế: `LongAdder` không mặc nhiên tốt hơn trong mọi tải hoặc khi tranh chấp thấp.

| Nhu cầu | Cấu trúc phù hợp |
|---|---|
| Sinh ID/số thứ tự tăng dần trong phạm vi kiểu | `AtomicLong.incrementAndGet()`; xử lý tràn theo yêu cầu |
| Tăng bộ đếm chính xác và đọc giá trị hiện tại | `AtomicInteger` hoặc `AtomicLong` |
| Thống kê lượng request có nhiều luồng cập nhật, chấp nhận `sum()` không là snapshot nguyên tử lúc đang ghi | `LongAdder` |
| Nhiều trường phải đồng thời thỏa bất biến | `synchronized`, `Lock`, hoặc thiết kế trạng thái gộp |
| Thay nguyên tử một đối tượng trạng thái bất biến | `AtomicReference<State>` |
| Nhiều giá trị lớn hơn 64 bit | Mô hình `BigInteger` cộng đồng bộ hoặc tham chiếu atomic thích hợp |

Lựa chọn công cụ phụ thuộc **bất biến cần bảo vệ**, chứ không chỉ việc dữ liệu có nhiều luồng truy cập.

## 9. Lỗi thường gặp và cách tránh

1. **Nghĩ `volatile int++` là nguyên tử.** `volatile` không gộp phép đọc, cộng và ghi. Dùng `incrementAndGet()` hoặc đồng bộ hóa.
2. **Tách một phép cập nhật thành `get()` rồi `set()`.** Hai lệnh riêng có thể xen kẽ. Dùng `addAndGet`, `getAndUpdate` hoặc CAS.
3. **Nhầm giá trị cũ và mới.** `getAndIncrement()` trả giá trị trước; `incrementAndGet()` trả giá trị sau. Chọn theo yêu cầu cấp số hay bộ đếm.
4. **Cho rằng hai Atomic là một giao dịch.** Cập nhật `balanceA` rồi `balanceB` để lại một khoảng thời gian có trạng thái trung gian quan sát được.
5. **Đặt tác dụng phụ trong hàm `updateAndGet`.** Lambda có thể chạy lại khi CAS thất bại; cần dùng hàm thuần, không gọi API có tác dụng ngoài.
6. **Dùng `AtomicLong` và cho rằng không bao giờ tràn.** `long` vẫn có giới hạn, tăng qua `Long.MAX_VALUE` sẽ wrap.
7. **Dựa vào `LongAdder.sum()` như snapshot nguyên tử đang đồng thời cập nhật.** `sum()` phục vụ thống kê, không cam kết snapshot tức thời dưới tranh chấp.
8. **Dùng vòng `while (flag.get() == 0)` để chờ dài.** Bận chờ tiêu thụ CPU; chọn cơ chế chờ, thông báo hoặc blocking phù hợp.
9. **Hiểu CAS là khóa toàn bộ đoạn code.** CAS bảo vệ việc so sánh/cập nhật một biến, không tự đồng bộ các hiệu ứng bên ngoài phép CAS.
10. **Nghĩ có atomic là toàn bộ ứng dụng thread-safe.** Cần xét tham chiếu chia sẻ, thứ tự thao tác, dữ liệu khác và bất biến nghiệp vụ.

## 10. Tự kiểm tra

**Câu 1.** Vì sao `volatile int count; count++;` vẫn mất lần tăng?  
**Đáp án:** Phép `++` gồm nhiều bước và có thể xen kẽ. `volatile` cho hiệu ứng visibility của các lần đọc/ghi, không làm toàn bộ phép đọc–sửa–ghi nguyên tử.

**Câu 2.** `new AtomicInteger(3).getAndIncrement()` trả về gì? Giá trị cuối?  
**Đáp án:** Trả 3 (giá trị cũ); đối tượng chứa 4.

**Câu 3.** `new AtomicLong(7).incrementAndGet()` trả về gì?  
**Đáp án:** 8, là giá trị mới sau cập nhật.

**Câu 4.** Tại sao `if (a.get() < 100) a.incrementAndGet();` có thể vượt 100?  
**Đáp án:** Điều kiện và lần tăng là hai thao tác riêng; nhiều luồng có thể cùng đọc 99 rồi cùng tăng. Dùng CAS loop để kiểm tra và cập nhật trên cùng giá trị quan sát.

**Câu 5.** `compareAndSet(10, 20)` trả `false` nói lên điều gì?  
**Đáp án:** Giá trị đang quan sát trong phép CAS không khớp 10 nên phép gán 20 không xảy ra. Nên đọc lại và quyết định thử tiếp nếu thuật toán yêu cầu.

**Câu 6.** Vì sao không nên gọi hệ thống ngoài trong lambda của `updateAndGet`?  
**Đáp án:** Hàm có thể được gọi nhiều lần do tranh chấp và CAS retry; hiệu ứng ngoài có thể bị lặp.

**Câu 7.** Khi nào dùng `LongAdder` thay `AtomicLong`?  
**Đáp án:** Khi cần nhiều luồng tích lũy bộ đếm thống kê dưới tranh chấp và không đòi đọc snapshot nguyên tử hoặc cấp số/compare-and-set từ từng lần cập nhật.

**Câu 8.** `AtomicLong.incrementAndGet()` trên `Long.MAX_VALUE` trả gì?  
**Đáp án:** `Long.MIN_VALUE` do phép toán số nguyên wrap; atomicity không ngăn tràn.

**Quy tắc ghi nhớ:** `AtomicInteger` và `AtomicLong` cung cấp các phép thao tác **nguyên tử trên một giá trị số nguyên**, cùng hiệu ứng bộ nhớ phù hợp của API. Chúng ngăn lost update khi dùng đúng phương thức, nhưng **không tự thay thế giao dịch nhiều biến, kiểm tra bất biến tổng hợp, xử lý tràn số hoặc chính sách tiến triển giữa các luồng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-numbers-004">getAndIncrement, incrementAndGet và các phép cập nhật</a>

<details>
<summary>Xem chi tiết</summary>

# AtomicInteger: getAndIncrement, incrementAndGet và các phép cập nhật nguyên tử trong Java

## 1. Vì sao phép tăng số thông thường có thể sai khi có nhiều luồng?

Giả sử nhiều luồng Java cùng tăng một bộ đếm:

~~~java
int counter = 0;
counter++;
~~~

Trông như một thao tác duy nhất, nhưng về ý nghĩa cập nhật, `counter++` gồm những bước đọc giá trị cũ, tính giá trị mới, rồi ghi lại. Hai luồng có thể cùng đọc `counter = 5`, cùng tính `6` và cùng ghi `6`: **hai lần tăng nhưng kết quả chỉ tăng một lần**. Đây là một dạng *lost update* (mất cập nhật).

Đặt một biến `int` thành `volatile` giúp các luồng có bảo đảm nhất định về khả năng quan sát và thứ tự đọc/ghi, nhưng **không biến biểu thức `counter++` thành thao tác nguyên tử**.

Khi chỉ cần một số nguyên dùng chung có các thao tác cập nhật độc lập, Java cung cấp `java.util.concurrent.atomic.AtomicInteger`. Các phương thức như `getAndIncrement()`, `incrementAndGet()`, `getAndAdd()`, `addAndGet()`, `getAndUpdate()`, `updateAndGet()` và `compareAndSet()` thực hiện thao tác với ngữ nghĩa nguyên tử phù hợp cho nhiều luồng.

~~~java
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger counter = new AtomicInteger(0);
counter.incrementAndGet();
~~~

Đây là đoạn minh họa đặt trong một lớp/phương thức. Đối tượng `AtomicInteger` giữ một giá trị `int` có thể thay đổi; nó **không** phải `Integer` bất biến.

Mục tiêu bài học: hiểu chính xác phương thức nào trả **giá trị trước cập nhật**, phương thức nào trả **giá trị sau cập nhật**, các phép cập nhật theo hàm, cơ chế so sánh rồi đổi (CAS), và giới hạn của bảo đảm nguyên tử.

## 2. getAndIncrement và incrementAndGet: cùng tăng 1, khác giá trị trả về

Hai phương thức đều tăng giá trị được giữ lên 1 theo một thao tác nguyên tử, nhưng thời điểm của giá trị trả về khác nhau:

| Phương thức | Phép cập nhật | Giá trị trả về |
| --- | --- | --- |
| `getAndIncrement()` | Tăng 1 | **Giá trị cũ**, ngay trước lần tăng thành công |
| `incrementAndGet()` | Tăng 1 | **Giá trị mới**, ngay sau lần tăng thành công |
| `getAndDecrement()` | Giảm 1 | **Giá trị cũ** |
| `decrementAndGet()` | Giảm 1 | **Giá trị mới** |

Ví dụ bắt đầu từ 10:

~~~java
AtomicInteger counter = new AtomicInteger(10);

int oldValue = counter.getAndIncrement(); // oldValue = 10, counter = 11
int newValue = counter.incrementAndGet(); // newValue = 12, counter = 12
~~~

Điểm quan trọng: tiền tố **`getAnd...`** nghĩa là phương thức trả **giá trị cũ** rồi áp dụng cập nhật; hậu tố **`...AndGet`** nghĩa là trả **giá trị sau khi đã cập nhật**. Trong cả hai trường hợp, cập nhật là nguyên tử; khác biệt thuộc về **giá trị mà lời gọi trả về**.

So sánh với cú pháp số nguyên thông thường giúp ghi nhớ:

~~~java
int x = 10;
int a = x++; // a = 10, x = 11
int b = ++x; // b = 12, x = 12
~~~

Về giá trị trả về trong một luồng, `getAndIncrement()` tương tự `x++`, còn `incrementAndGet()` tương tự `++x`. **Sự tương tự dừng ở ý nghĩa trả về**: các phép cập nhật trên `AtomicInteger` được thiết kế để dùng đồng thời, trong khi `x++` trên biến dùng chung không có bảo đảm cập nhật nguyên tử.

### Chọn giá trị cũ hay mới theo nghiệp vụ

Nếu muốn phát số thứ tự **bắt đầu từ 0**, có thể dùng:

~~~java
AtomicInteger sequence = new AtomicInteger(0);
int firstId = sequence.getAndIncrement();  // 0
int secondId = sequence.getAndIncrement(); // 1
~~~

Nếu muốn đếm **số công việc đã hoàn thành**, rồi dùng ngay tổng mới:

~~~java
AtomicInteger completed = new AtomicInteger(0);
int countAfterCompletion = completed.incrementAndGet(); // 1
~~~

Hai ví dụ trên chỉ minh họa ở mức tiến trình hiện tại. `AtomicInteger` không tạo ID bền vững sau khi ứng dụng khởi động lại, cũng không bảo đảm duy nhất trên nhiều JVM/máy chủ.

## 3. Các phương thức cộng, trừ và ghi trực tiếp

Không chỉ tăng giảm 1, `AtomicInteger` còn cung cấp nhiều phương thức:

| Phương thức | Hành động | Trả về |
| --- | --- | --- |
| `get()` | Đọc giá trị hiện tại | Giá trị đọc được |
| `set(v)` | Gán `v` | `void` |
| `getAndSet(v)` | Thay bằng `v` nguyên tử | Giá trị **trước** khi thay |
| `getAndAdd(delta)` | Cộng `delta` nguyên tử | Giá trị **trước** khi cộng |
| `addAndGet(delta)` | Cộng `delta` nguyên tử | Giá trị **sau** khi cộng |
| `getAndIncrement()` | Cộng 1 nguyên tử | Giá trị cũ |
| `incrementAndGet()` | Cộng 1 nguyên tử | Giá trị mới |
| `getAndDecrement()` | Trừ 1 nguyên tử | Giá trị cũ |
| `decrementAndGet()` | Trừ 1 nguyên tử | Giá trị mới |

Giả sử giá trị ban đầu là 20:

~~~java
AtomicInteger value = new AtomicInteger(20);

int before = value.getAndAdd(5); // before = 20; value = 25
int after = value.addAndGet(-3);  // after = 22; value = 22
int previous = value.getAndSet(0); // previous = 22; value = 0
~~~

Dùng `getAndAdd(-3)` hoặc `addAndGet(-3)` khi muốn trừ 3. Các cập nhật này là nguyên tử ngay cả khi nhiều luồng cùng tác động vào **cùng một đối tượng** `AtomicInteger`.

Tuy nhiên, `counter.get()` rồi `counter.set(counter.get() + 1)` **không tương đương** với `incrementAndGet()` trong môi trường đồng thời: phép đọc và ghi nằm trong các lời gọi riêng lẻ, một luồng khác có thể chen vào.

## 4. Chương trình Java 17 đầy đủ: giá trị cũ, mới và cập nhật theo hàm

Tạo tệp `AtomicUpdatesDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicUpdatesDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(10);

        System.out.println("getAndIncrement: "
                + counter.getAndIncrement() + ", now=" + counter.get());
        System.out.println("incrementAndGet: "
                + counter.incrementAndGet() + ", now=" + counter.get());

        System.out.println("getAndAdd(5): "
                + counter.getAndAdd(5) + ", now=" + counter.get());
        System.out.println("addAndGet(-2): "
                + counter.addAndGet(-2) + ", now=" + counter.get());

        System.out.println("getAndUpdate(x -> x * 2): "
                + counter.getAndUpdate(x -> x * 2)
                + ", now=" + counter.get());
        System.out.println("updateAndGet(x -> x + 3): "
                + counter.updateAndGet(x -> x + 3)
                + ", now=" + counter.get());

        System.out.println("getAndAccumulate(4): "
                + counter.getAndAccumulate(4, (current, x) -> current + x * 2)
                + ", now=" + counter.get());
        System.out.println("accumulateAndGet(50, max): "
                + counter.accumulateAndGet(50, Math::max)
                + ", now=" + counter.get());

        System.out.println("compareAndSet(50, 60): "
                + counter.compareAndSet(50, 60)
                + ", now=" + counter.get());
        System.out.println("compareAndSet(50, 70): "
                + counter.compareAndSet(50, 70)
                + ", now=" + counter.get());

        System.out.println("getAndSet(0): "
                + counter.getAndSet(0) + ", now=" + counter.get());

        AtomicInteger parallelCount = new AtomicInteger();
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 25_000; j++) {
                    parallelCount.incrementAndGet();
                }
            });
            workers[i].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        System.out.println("Parallel count: " + parallelCount.get());
    }
}
~~~

Chạy:

~~~bash
javac AtomicUpdatesDemo.java
java AtomicUpdatesDemo
~~~

Kết quả chuẩn:

~~~text
getAndIncrement: 10, now=11
incrementAndGet: 12, now=12
getAndAdd(5): 12, now=17
addAndGet(-2): 15, now=15
getAndUpdate(x -> x * 2): 15, now=30
updateAndGet(x -> x + 3): 33, now=33
getAndAccumulate(4): 33, now=41
accumulateAndGet(50, max): 50, now=50
compareAndSet(50, 60): true, now=60
compareAndSet(50, 70): false, now=60
getAndSet(0): 60, now=0
Parallel count: 100000
~~~

Các giá trị ở phần đầu xác định vì chỉ có luồng `main` cập nhật `counter`. Ở cuối, bốn luồng cùng tăng `parallelCount` 25.000 lần; lời gọi `join()` bảo đảm luồng chính đợi các luồng hoàn thành trước khi đọc tổng, nên kết quả luôn là **100.000** nếu tất cả luồng chạy thành công. Thứ tự **từng lần tăng** giữa các luồng không được xác định, nhưng tổng của các lần tăng nguyên tử thì không bị mất.

Hai phương thức `getAndAccumulate` và `accumulateAndGet` nhận một toán hạng bổ sung và hàm ghép, sẽ được giải thích kỹ bên dưới.

## 5. getAndUpdate và updateAndGet: cập nhật qua hàm

Đôi khi thao tác không đơn giản là cộng hoặc trừ. Chẳng hạn muốn tăng một giá trị theo quy tắc riêng, giới hạn kết quả hoặc chuyển trạng thái của một bộ đếm. Java hỗ trợ:

~~~java
int old = value.getAndUpdate(x -> x * 2);
int updated = value.updateAndGet(x -> x + 3);
~~~

Trong hai phương thức, một hàm `IntUnaryOperator` biến **giá trị hiện tại** thành **giá trị mới**. Khác biệt là:

- `getAndUpdate(function)`: trả **giá trị trước cập nhật thành công**.
- `updateAndGet(function)`: trả **giá trị mới sau cập nhật thành công**.

Ví dụ, nếu `value` ban đầu là 8:

~~~java
AtomicInteger value = new AtomicInteger(8);

int a = value.getAndUpdate(x -> x * 2); // a=8; value=16
int b = value.updateAndGet(x -> x + 3); // b=19; value=19
~~~

Các hàm cập nhật có thể được chạy **nhiều lần** trong một lời gọi khi có cạnh tranh giữa các luồng. Thực thi thường dùng cơ chế thử cập nhật rồi lặp lại khi trạng thái đã thay đổi. Vì thế, hàm cập nhật nên **không có tác dụng phụ** (*side-effect-free*) và tính kết quả từ các đối số:

~~~java
value.updateAndGet(x -> Math.min(x + 1, 100));
~~~

Không nên đặt thao tác ghi cơ sở dữ liệu, gửi tin nhắn, ghi log đếm số lần nghiệp vụ, gọi dịch vụ bên ngoài hoặc tự tăng một bộ đếm khác bên trong lambda với giả định lambda chỉ được gọi đúng một lần:

~~~java
// Ví dụ SAI về kỳ vọng: lambda có thể chạy lại khi tranh chấp.
value.updateAndGet(x -> {
    externalCounter.incrementAndGet();
    return x + 1;
});
~~~

Nếu lời gọi bị thử lại, `externalCounter` có thể bị tăng nhiều hơn một lần trong khi `value` chỉ cập nhật thành công một lần. Đưa tác dụng phụ ra sau thao tác thành công, đồng thời xem xét tính nhất quán của nghiệp vụ nếu cần cập nhật nhiều hệ thống.

### Ví dụ giới hạn tối đa 100

~~~java
AtomicInteger progress = new AtomicInteger(98);

int after = progress.updateAndGet(x -> Math.min(100, x + 1));
// after = 99

after = progress.updateAndGet(x -> Math.min(100, x + 1));
// after = 100

after = progress.updateAndGet(x -> Math.min(100, x + 1));
// after = 100
~~~

Trong các ví dụ này, một phép cập nhật nguyên tử được áp dụng trên một số nguyên tại một thời điểm. Nếu nghiệp vụ có nhiều trường cần giữ bất biến cùng nhau, cần một thiết kế đồng bộ tổng thể.

## 6. getAndAccumulate và accumulateAndGet: ghép thêm toán hạng

Khi phép cập nhật cần cả **giá trị hiện tại** và **một toán hạng** riêng, dùng:

~~~java
value.getAndAccumulate(x, (current, operand) -> ...);
value.accumulateAndGet(x, (current, operand) -> ...);
~~~

Hàm thuộc loại `IntBinaryOperator`. Thứ tự tham số là **giá trị hiện tại trước**, **toán hạng `x` sau**. Lấy ví dụ:

~~~java
AtomicInteger score = new AtomicInteger(10);

int previous = score.getAndAccumulate(
        4, (current, increment) -> current + increment * 2);
// previous=10, score=18

int latest = score.accumulateAndGet(25, Math::max);
// latest=25, score=25
~~~

Ở đây `Math::max` giữ giá trị lớn hơn giữa giá trị đang lưu và ngưỡng truyền vào. Thao tác có thể phù hợp với việc giữ “giá trị lớn nhất đã quan sát”.

Một số mục đích khác của hàm tích lũy:

- `Math::min`: giữ giá trị nhỏ nhất quan sát.
- `(current, increment) -> current + increment`: cộng toán hạng, tương tự `addAndGet` khi cần giá trị sau.
- `(current, factor) -> current * factor`: nhân với hệ số, có thể bị tràn `int`.
- `(current, newValue) -> Math.max(current, newValue)`: chỉ tăng đến mức cao nhất đã thấy.

Cũng giống `getAndUpdate`, hàm tích lũy có thể được thực thi lại trong khi tranh chấp. Hãy dùng **hàm thuần không có tác dụng phụ**, không giả định số lần callback được gọi bằng số cập nhật thành công.

## 7. compareAndSet: cập nhật có điều kiện bằng CAS

`compareAndSet(expectedValue, newValue)` thường được viết tắt là **CAS** (*compare-and-set*). Nó thực hiện logic nguyên tử:

> Nếu giá trị hiện tại bằng `expectedValue`, thay bằng `newValue` và trả `true`; nếu khác, không thay đổi và trả `false`.

~~~java
AtomicInteger stock = new AtomicInteger(5);

boolean first = stock.compareAndSet(5, 4);  // true; stock=4
boolean second = stock.compareAndSet(5, 3); // false; stock=4
~~~

CAS cần thiết khi cập nhật chỉ được phép xảy ra nếu trạng thái vẫn giống thời điểm ta quan sát. Nó giúp tránh kiểu cập nhật “đọc rồi ghi” làm mất thay đổi từ luồng khác.

Một vòng lặp CAS có thể biểu diễn logic “không cho giá trị giảm dưới 0”:

~~~java
static boolean tryTakeOne(AtomicInteger stock) {
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
~~~

Đây là **mảnh phương thức** nằm trong lớp có `import java.util.concurrent.atomic.AtomicInteger;`, giả định `stock` không null và bất biến của ứng dụng bảo đảm giá trị không âm ngay từ đầu. Nếu các luồng khác đặt giá trị âm hoặc có thao tác điều chỉnh phức tạp, phải kiểm tra lại điều kiện nghiệp vụ.

Giải thích:

1. Đọc giá trị hiện tại.
2. Nếu đã bằng 0, không thể lấy thêm.
3. Thử CAS chuyển từ `current` sang `current - 1`.
4. Nếu luồng khác vừa sửa giá trị, CAS thất bại; vòng lặp đọc lại rồi quyết định trên giá trị mới.

Một cách khác với chỉ một số nguyên là:

~~~java
static boolean tryTakeOne(AtomicInteger stock) {
    int old = stock.getAndUpdate(x -> x > 0 ? x - 1 : x);
    return old > 0;
}
~~~

Chỉ chọn **một** trong hai định nghĩa phương thức `tryTakeOne` khi đưa vào cùng lớp, vì chúng có cùng chữ ký. Cách `getAndUpdate` có điều kiện ngay trong hàm nguyên tử; dù giá trị không đổi khi hết hàng, lời gọi cập nhật vẫn trả giá trị cũ để quyết định.

### Giới hạn của CAS với nhiều bước và hiện tượng ABA

CAS trên `AtomicInteger` so sánh một giá trị `int` hiện tại; nó không ghi nhớ lịch sử. Nếu một luồng nhìn thấy `A`, một luồng khác đổi `A → B → A`, thì lần CAS đầu có thể vẫn thành công vì **giá trị cuối đã quay lại A**, dù trạng thái từng thay đổi. Đây là **vấn đề ABA**.

Nếu lịch sử chuyển trạng thái hoặc version là quan trọng, có thể phải mô hình hóa thêm phiên bản, dùng loại atomic khác như `AtomicStampedReference` cho đối tượng tham chiếu, hoặc sử dụng khóa/dữ liệu phù hợp. Không nên tự suy diễn rằng CAS trên một `int` sẽ giải quyết mọi giao dịch hoặc bất biến nhiều trường.

## 8. AtomicInteger bảo đảm điều gì về đồng thời và bộ nhớ?

`AtomicInteger` cung cấp các thao tác nguyên tử cho **một giá trị nguyên** và các ngữ nghĩa bộ nhớ được quy định. Những thao tác thông dụng như `get()`, `set()`, `compareAndSet()`, `getAndIncrement()` tương ứng với việc quan sát/cập nhật trạng thái có thứ tự bộ nhớ phù hợp; không cần tự đặt `volatile` lên một biến `int` khác để biến thao tác atomic thành an toàn.

Cần tách ba khái niệm:

- **Atomicity — tính nguyên tử:** Một lần cập nhật atomic thành công không bị “nửa chừng”; nhiều luồng tăng cùng bộ đếm không làm mất cập nhật.
- **Visibility — khả năng quan sát:** Giá trị được công bố/đọc theo ngữ nghĩa bộ nhớ của phương thức, giúp các luồng có thể nhìn thấy cập nhật phù hợp.
- **Ordering — thứ tự:** Các tác vụ có thể phải tuân theo quy tắc thứ tự bộ nhớ nhất định. Những phương thức mới như `getAcquire`, `setRelease` hay `getOpaque` có ngữ nghĩa yếu hơn/tập trung hơn và chỉ nên chọn khi hiểu đúng yêu cầu đồng thời.

**Bảo đảm trên một biến không tự biến nhiều bước thành một giao dịch.** Ví dụ:

~~~java
if (balance.get() >= 10) {
    balance.addAndGet(-10);
}
~~~

Đây không phải một phép “kiểm tra rồi trừ” nguyên tử. Hai luồng có thể cùng thấy `balance = 10` và cùng trừ, để lại `-10`. Muốn giữ bất biến “không trừ quá số dư”, dùng CAS hoặc một `updateAndGet` có điều kiện cùng logic trong lambda, đồng thời xác định rõ cách báo thành công/thất bại.

Trong trường hợp có **hai** biến cần cập nhật đồng thời (ví dụ `balanceA` và `balanceB` để chuyển tiền), hai `AtomicInteger` riêng không tạo ra giao dịch nguyên tử trên cả cặp. Cần đồng bộ bằng khóa, mô hình trạng thái chung dưới một phép cập nhật, hoặc cơ chế giao dịch phù hợp.

## 9. Phân biệt atomic update với synchronized và volatile

| Công cụ | Có thể giải quyết gì? | Không đủ trong trường hợp nào? |
| --- | --- | --- |
| `volatile int count` | Bảo đảm đặc tính visibility và ordering của đọc/ghi biến `volatile` | `count++` vẫn có thể mất cập nhật |
| `AtomicInteger.incrementAndGet()` | Tăng một giá trị nguyên tử | Nhiều trường/bất biến phức tạp không tự được bảo vệ |
| `synchronized` | Cho phép bảo vệ một đoạn mã phức tạp bằng cùng một monitor | Có thể làm tăng tranh chấp hoặc khóa nếu thiết kế không phù hợp |
| `ReentrantLock` | Khóa tường minh cho miền dữ liệu lớn hơn, có một số khả năng bổ sung | Cần quản lý khóa đúng, thường dùng `try/finally` |

`AtomicInteger` thường rất thuận tiện khi bộ đếm đơn giản; vòng lặp CAS có thể thử lại khi tranh chấp, không có nghĩa mọi thao tác sẽ xong sau đúng một lần thử. Một thiết kế đúng cần chọn **đơn vị trạng thái phải được bảo vệ**, rồi chọn công cụ phù hợp.

## 10. Tràn số nguyên, giới hạn miền và loại số khác

`AtomicInteger` lưu giá trị **`int` 32-bit có dấu**. Các phép tăng/cộng không tự ném lỗi khi vượt phạm vi `int`: kết quả tuân theo quy tắc tràn số nguyên Java theo biểu diễn 32-bit.

~~~java
AtomicInteger count = new AtomicInteger(Integer.MAX_VALUE);
int next = count.incrementAndGet();
System.out.println(next); // -2147483648
~~~

Vì vậy, một bộ đếm tăng liên tục có thể **quay sang số âm**. Tính nguyên tử không đồng nghĩa có kiểm soát overflow. Nếu cần phạm vi lớn hơn, dùng `AtomicLong` (64-bit); nếu cần số rất lớn không giới hạn bởi các loại số nguyên cơ bản, cần thiết kế bộ nhớ và đồng bộ khác, vì `AtomicInteger` không thể tự biến thành `BigInteger`.

Với bộ đếm cộng dồn có tranh chấp cao giữa nhiều luồng, `LongAdder` đôi khi cho hiệu năng tốt hơn `AtomicLong` nhờ phân tán cập nhật, nhưng `sum()` không nhất thiết là **ảnh chụp nguyên tử** của toàn bộ cập nhật đang diễn ra. Không thay `AtomicInteger` bằng `LongAdder` khi cần CAS/giá trị thứ tự chính xác mỗi lần cập nhật.

Nếu yêu cầu “tăng tới mức tối đa rồi dừng”, có thể dùng:

~~~java
AtomicInteger limit = new AtomicInteger(Integer.MAX_VALUE - 1);
int updated = limit.updateAndGet(x ->
        x == Integer.MAX_VALUE ? x : x + 1);
~~~

Biểu thức này không tràn nếu giá trị bắt đầu không vượt `Integer.MAX_VALUE`, và không có tác nhân khác đặt dữ liệu về trạng thái không hợp lệ. Với nghiệp vụ cần chính sách overflow khác, hãy xác định rõ: chặn giá trị, báo lỗi, hay chuyển sang loại dữ liệu rộng hơn.

## 11. Những lỗi phổ biến

**Lỗi 1 — Nhầm giá trị trả về.** `getAndIncrement()` trả giá trị **trước** khi tăng; `incrementAndGet()` trả giá trị **sau** khi tăng. Khi phát ID hoặc đếm sự kiện, nhầm điểm bắt đầu có thể gây lệch một đơn vị.

**Lỗi 2 — Tưởng `get()` rồi `set()` là một cập nhật nguyên tử.** Hai lời gọi riêng vẫn cho phép luồng khác chen vào. Dùng `getAndAdd`, `updateAndGet`, CAS hoặc khóa theo yêu cầu.

**Lỗi 3 — Dùng `volatile int` và `++` để tránh mất cập nhật.** `volatile` không gom phép đọc, cộng và ghi thành một hành động nguyên tử.

**Lỗi 4 — Viết hàm cập nhật có tác dụng phụ.** Lambda trong `getAndUpdate`/`updateAndGet`/`accumulateAndGet` có thể được chạy nhiều lần dưới tranh chấp. Không thực hiện side effect trong callback với kỳ vọng chạy đúng một lần.

**Lỗi 5 — Đọc giá trị ngay sau khi cập nhật rồi coi đó luôn là kết quả riêng của lời gọi.** Trong nhiều luồng, ngay sau `getAndIncrement()`, một luồng khác có thể tăng thêm trước `get()`. Nếu cần biết **giá trị đã cấp cho chính lời gọi của mình**, hãy dùng trực tiếp kết quả mà phương thức trả về.

**Lỗi 6 — Cho rằng AtomicInteger giải quyết giao dịch nhiều biến.** Cập nhật `a` rồi `b` không nguyên tử trên cặp biến; luồng khác có thể quan sát trạng thái trung gian. Dùng đồng bộ phù hợp hoặc gộp trạng thái nếu phải giữ bất biến chung.

**Lỗi 7 — Quên tràn int.** `incrementAndGet()` tại `Integer.MAX_VALUE` có thể quay về `Integer.MIN_VALUE` mà không ném ngoại lệ.

**Lỗi 8 — Tưởng một vòng CAS luôn thành công ngay.** Nếu giá trị bị luồng khác sửa, CAS trả false; vòng lặp phải đọc và quyết định lại. Trong tải tranh chấp nặng, nhiều lần thử có thể xảy ra.

**Lỗi 9 — Dùng bộ đếm AtomicInteger làm ID toàn cục.** Nó chỉ quản lý trạng thái trong phạm vi đối tượng/JVM. Restart, nhiều instance, nhiều máy hoặc phạm vi `int` đều có thể phá kỳ vọng ID duy nhất dài hạn.

**Lỗi 10 — Dùng lại số thứ tự đã cấp nhưng bỏ qua giá trị trả về.** Với `getAndIncrement` đa luồng, mỗi lời gọi có một giá trị cũ riêng trước khi wrap-around; cần lưu kết quả ấy tại đúng chỗ thay vì lấy `get()` sau đó.

## 12. Bài tập áp dụng và lời giải

### Bài 1. Dự đoán giá trị trả về

~~~java
AtomicInteger x = new AtomicInteger(5);
int a = x.getAndIncrement();
int b = x.incrementAndGet();
int c = x.getAndAdd(3);
int d = x.addAndGet(-2);
~~~

**Đáp án:** `a = 5`, `b = 7`, `c = 7`, `d = 8`; giá trị cuối của `x` là `8`.

Diễn giải: bắt đầu 5 → `getAndIncrement` trả 5, lưu 6 → `incrementAndGet` lưu/trả 7 → `getAndAdd(3)` trả 7, lưu 10 → `addAndGet(-2)` lưu/trả 8.

### Bài 2. Giữ giá trị lớn nhất từng quan sát

Muốn giữ `maximum` không bao giờ giảm, khi có quan sát mới `observed`, hãy viết:

~~~java
AtomicInteger maximum = new AtomicInteger(0);
int latest = maximum.accumulateAndGet(25, Math::max);
~~~

`latest` là 25. Nếu một luồng khác đồng thời báo giá trị 30, phép tích lũy kiểu `Math.max` bảo toàn quy tắc lớn nhất của những lần cập nhật thành công. Nếu giá trị có thể âm và cần max trong toàn miền `int`, phải chọn giá trị khởi tạo phù hợp thay vì mặc định 0.

### Bài 3. Sửa kiểm tra rồi cập nhật không an toàn

Mã sau bị race:

~~~java
if (stock.get() > 0) {
    stock.decrementAndGet();
}
~~~

Cách sửa khi chỉ cần bảo vệ một `AtomicInteger` và biết `stock` ban đầu không âm:

~~~java
int previous = stock.getAndUpdate(x -> x > 0 ? x - 1 : x);
boolean taken = previous > 0;
~~~

Đây là mảnh code giả định `stock` đã được khai báo. Hàm cập nhật vừa kiểm tra vừa tính trạng thái mới trong một thao tác nguyên tử; `previous > 0` cho biết lời gọi này có lấy được một đơn vị hay không.

### Bài 4. Vì sao đoạn code có thể in giá trị khác?

~~~java
int myNumber = shared.getAndIncrement();
int observedNow = shared.get();
~~~

`observedNow` có thể lớn hơn `myNumber + 1` bởi các luồng khác có thể thực hiện thêm cập nhật giữa hai lời gọi. Kết quả `myNumber` vẫn là giá trị cũ đã được cấp **nguyên tử cho chính lời gọi**. Không giả định `get()` tiếp theo là snapshot riêng của cập nhật của luồng mình.

### Bài 5. Kiểm tra tràn số

~~~java
AtomicInteger x = new AtomicInteger(Integer.MAX_VALUE);
System.out.println(x.getAndIncrement());
System.out.println(x.get());
~~~

**Đáp án:**

~~~text
2147483647
-2147483648
~~~

Lời gọi đầu trả giá trị cũ là `Integer.MAX_VALUE`, trong khi giá trị đã lưu tăng qua biên rồi tràn thành `Integer.MIN_VALUE`.

## 13. Tóm lược cách chọn phương thức

- Cần **giá trị trước khi tăng/giảm/cộng** → dùng `getAndIncrement`, `getAndDecrement`, `getAndAdd`.
- Cần **giá trị sau khi tăng/giảm/cộng** → dùng `incrementAndGet`, `decrementAndGet`, `addAndGet`.
- Cần **hàm tính từ giá trị đang lưu** → dùng `getAndUpdate` hoặc `updateAndGet` với callback thuần, chọn giá trị cũ/mới tương ứng.
- Cần **ghép một toán hạng mới với trạng thái hiện tại** → dùng `getAndAccumulate` hoặc `accumulateAndGet`.
- Cần **chỉ cập nhật nếu còn đúng giá trị kỳ vọng** → dùng `compareAndSet` và xử lý trường hợp thất bại.
- Cần **một giá trị đếm nguyên tử** → `AtomicInteger` có thể phù hợp; cần nhiều giá trị phải thay đổi cùng lúc → chọn cơ chế đồng bộ/giao dịch bao quát trạng thái đó.

**Ghi nhớ:** Các thao tác của `AtomicInteger` cung cấp cập nhật nguyên tử trên **một số nguyên**. Khác biệt giữa `getAnd...` và `...AndGet` nằm ở **giá trị trả về trước hay sau cập nhật**; tính an toàn đồng thời không có nghĩa mọi phép toán nhiều bước, nhiều biến hoặc mọi side effect trong callback đều nguyên tử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-numbers-005">Compare-and-set và việc hàm cập nhật có thể bị gọi lại</a>

<details>
<summary>Xem chi tiết</summary>

# Compare-and-set và việc hàm cập nhật có thể bị gọi lại

## 1. Vấn đề khi nhiều luồng cùng cập nhật một con số

Giả sử một chương trình Java lưu bộ đếm lượt truy cập, số ghế còn lại, tổng số đơn hàng hoặc trạng thái số của một thiết bị. Nếu nhiều luồng cùng đọc, tính toán và ghi biến, đoạn mã sau **không bảo đảm tăng chính xác**:

```java
// Counter là lớp chứa một trường mutable:
class Counter {
    int value = 0;

    void increment() {
        value = value + 1;
    }
}
```

Phép `value = value + 1` là chuỗi thao tác: đọc giá trị, tính `+1`, ghi kết quả. Hai luồng có thể cùng đọc 10 rồi cùng ghi 11, khiến một lần tăng bị mất (*lost update*). Thêm `volatile` giúp bảo đảm tính hiển thị nhất định của truy cập biến, nhưng **không biến cả phép đọc–sửa–ghi thành một thao tác nguyên tử**.

Các lớp trong `java.util.concurrent.atomic`, đặc biệt `AtomicInteger` và `AtomicLong`, hỗ trợ các thao tác nguyên tử trên một giá trị số. Cơ chế quan trọng để hiểu chúng là **compare-and-set (CAS)** và **vòng lặp thử lại** khi giá trị đang được luồng khác thay đổi.

Mục tiêu chính của bài: hiểu chính xác điều kiện CAS thành công, vì sao hàm đưa vào `updateAndGet()` hoặc `getAndUpdate()` có thể được gọi **nhiều lần cho một lời gọi API**, cách thiết kế hàm cập nhật an toàn, và lúc nào nên chọn API số nguyên tử khác.

## 2. Compare-and-set (CAS) là gì?

Trong `AtomicInteger`:

```java
boolean compareAndSet(int expectedValue, int newValue);
```

Ý nghĩa: tại thời điểm thao tác nguyên tử được thực hiện, nếu giá trị hiện tại **bằng** `expectedValue`, đặt nó thành `newValue` và trả `true`; nếu không, giữ nguyên giá trị và trả `false`.

```java
AtomicInteger seats = new AtomicInteger(2);

boolean first = seats.compareAndSet(2, 1); // true: 2 -> 1
boolean stale = seats.compareAndSet(2, 0); // false: current là 1
int remaining = seats.get();               // 1
```

Trong một lần CAS thành công, kiểm tra điều kiện và ghi giá trị được thực hiện cùng một bước nguyên tử đối với biến atomic đó. Nó bảo vệ thao tác **trên chính biến ấy** trước sự tranh chấp giữa các luồng cùng thao tác đúng thông qua atomic.

Có thể hình dung:

```text
Giá trị hiện tại = 10

Luồng A đọc 10, dự tính ghi 15.
Luồng B đọc 10, CAS(10, 20) thành công -> giá trị = 20.
Luồng A CAS(10, 15) thất bại vì giá trị hiện tại đã là 20.
Luồng A phải đọc lại 20 và tính phương án mới, ví dụ 25.
```

CAS không tự chọn cách tính lại. Vòng lặp của ứng dụng hoặc các API như `updateAndGet()` thực hiện công việc đó.

## 3. Vòng lặp CAS: vì sao hàm cập nhật được tính lại?

Hãy xem một mô hình minh họa của `updateAndGet()`, **không phải bản chép mã nguồn nội bộ JDK**:

```java
static int updateWithCas(
        AtomicInteger value,
        java.util.function.IntUnaryOperator updater) {
    for (;;) {
        int observed = value.get();
        int candidate = updater.applyAsInt(observed);
        if (value.compareAndSet(observed, candidate)) {
            return candidate;
        }
        // Có tranh chấp: đọc lại giá trị, gọi updater lần nữa.
    }
}
```

Phương thức trên là mã minh họa cần đặt trong một lớp Java, với `import java.util.concurrent.atomic.AtomicInteger;`. Khi CAS thất bại vì có một cập nhật cạnh tranh, vòng lặp tính lại `candidate` từ giá trị mới. Do đó:

- **Một lần gọi `updateAndGet(f)` có thể gọi `f` nhiều lần**; số lần tùy diễn biến tranh chấp.
- **Chỉ phép CAS thành công mới chốt cập nhật** của lời gọi đó. Các kết quả tính thử trước đó có thể bị loại bỏ.
- Giá trị mà `f` nhận ở mỗi lần chạy có thể khác nhau, vì trạng thái dùng để tính mới đã thay đổi.
- Không được suy rằng “hàm trả kết quả một lần” đồng nghĩa “callback đã chạy một lần”.

Đây là hợp đồng quan trọng của các phương thức cập nhật bằng hàm trên atomic: tài liệu Java khuyến nghị hàm cập nhật **không có tác dụng phụ** (*side-effect-free*) vì nó có thể bị áp dụng lại khi thao tác cập nhật thất bại do tranh chấp.

## 4. Ví dụ Java 17 có thể tái hiện retry một cách xác định

Trong chương trình sau, hai `CountDownLatch` dùng **chỉ để kiểm chứng**: buộc luồng cạnh tranh cập nhật số từ 10 thành 20 **sau khi** callback lượt đầu tiên đã đọc 10 nhưng **trước khi** CAS của lượt đó diễn ra.

Tạo tệp **`CasRetryDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CasRetryDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger seats = new AtomicInteger(2);
        System.out.println("firstCAS=" + seats.compareAndSet(2, 1)
                + ", seats=" + seats.get());
        System.out.println("staleCAS=" + seats.compareAndSet(2, 0)
                + ", seats=" + seats.get());

        AtomicInteger value = new AtomicInteger(10);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger firstSeen = new AtomicInteger(-1);
        AtomicInteger secondSeen = new AtomicInteger(-1);
        CountDownLatch firstCall = new CountDownLatch(1);
        CountDownLatch competitorDone = new CountDownLatch(1);

        Thread competitor = new Thread(() -> {
            try {
                firstCall.await();
                if (!value.compareAndSet(10, 20)) {
                    throw new AssertionError("Expected competing CAS to succeed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                competitorDone.countDown();
            }
        });
        competitor.start();

        int result = value.updateAndGet(old -> {
            int pass = calls.incrementAndGet();
            if (pass == 1) {
                firstSeen.set(old);
                firstCall.countDown();
                try {
                    competitorDone.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            } else {
                secondSeen.set(old);
            }
            return old + 5;
        });
        competitor.join();

        System.out.println("updateAndGet=" + result
                + ", calls=" + calls.get()
                + ", seen=" + firstSeen.get() + "," + secondSeen.get());
        int previous = value.getAndUpdate(x -> x + 5);
        System.out.println("getAndUpdate old=" + previous + ", now=" + value.get());
        int sum = value.accumulateAndGet(12, Integer::sum);
        System.out.println("accumulateAndGet=" + sum);

        AtomicLong longValue = new AtomicLong(4_000_000_000L);
        System.out.println("longAfter=" + longValue.updateAndGet(x -> x + 1));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac CasRetryDemo.java
java CasRetryDemo
```

Kết quả **đã kiểm chứng** khi biên dịch với `javac --release 17`:

```text
firstCAS=true, seats=1
staleCAS=false, seats=1
updateAndGet=25, calls=2, seen=10,20
getAndUpdate old=25, now=30
accumulateAndGet=42
longAfter=4000000001
```

**Giải thích vì sao callback chạy hai lần:**

1. `value` bắt đầu là 10. Lượt áp dụng hàm đầu tiên nhận `old = 10`, lưu dấu vết vào `firstSeen` và ra hiệu cho luồng cạnh tranh.
2. Callback chờ `competitorDone`. Luồng cạnh tranh gọi `value.compareAndSet(10, 20)` thành công, vì lần cập nhật chính chưa chốt.
3. Callback đầu tiên tiếp tục, đề xuất 15. CAS áp dụng đề xuất đó thất bại: số hiện tại đã là 20, không còn là 10.
4. API cập nhật thử lại; callback nhận `old = 20`, tính 25. Lần CAS tiếp theo thành công vì luồng cạnh tranh đã hoàn thành.
5. `updateAndGet()` trả 25. Bộ đếm chẩn đoán `calls = 2` chứng minh **một lời gọi API có hai lần đánh giá callback**.

Trong ví dụ, `calls.incrementAndGet()`, `firstSeen.set()` và hai latch là **tác dụng phụ chủ đích của mã thử nghiệm** để quan sát retry. **Không sao chép mô hình callback có tác dụng phụ này vào xử lý nghiệp vụ thực tế.** Các callback sản xuất bên dưới chỉ tính toán từ tham số nhận vào.

Lưu ý: đây là ví dụ có điều phối để làm lỗi CAS xuất hiện chắc chắn. Khi tranh chấp ngẫu nhiên trong hệ thống thật, callback có thể chạy một lần, hai lần hoặc nhiều lần; không giả định một số lần cụ thể.

## 5. Hàm cập nhật phải an toàn khi được gọi lại

Một hàm cập nhật **thuần** (*pure function*) chỉ tính kết quả từ dữ liệu đầu vào và các hằng số/đầu vào bất biến liên quan, không tạo tác dụng phụ có thể quan sát từ bên ngoài.

### Cách đúng: tính lại giá trị số

```java
AtomicInteger balance = new AtomicInteger(100);
int latest = balance.updateAndGet(current -> current + 20);
// balance = 120 nếu không có cập nhật cạnh tranh khác
```

Hàm `current -> current + 20` có thể được gọi lại với giá trị mới sau khi CAS thất bại; giá trị cuối được chốt sau một CAS thành công.

**Không yêu cầu hàm phải bất biến dưới phép áp dụng hai lần** theo nghĩa toán học `f(f(x)) = f(x)`. Hàm `f(x) = x + 20` không có tính chất đó nhưng vẫn phù hợp. Điều cần thiết là việc **tính thử nhiều lần không gây thêm tác dụng phụ**, và hàm tính đúng kết quả từ giá trị *hiện tại được truyền vào*.

### Cách sai: gửi email, ghi log nghiệp vụ, gọi API bên ngoài

```java
// PHẢN VÍ DỤ — không thực hiện hành động ngoài trong callback.
balance.updateAndGet(current -> {
    sendNotification("Đã cộng 20"); // Có thể chạy nhiều lần.
    return current + 20;
});
```

`sendNotification(...)` là ký hiệu minh họa hành vi bên ngoài, không phải phương thức có sẵn trong Java. Nếu CAS thất bại, thông báo có thể được gửi dù đề xuất `current + 20` **chưa từng được ghi**, hoặc gửi nhiều lần.

Tương tự, tránh gọi dịch vụ mạng, ghi file/database, phát thông điệp, tạo giao dịch thanh toán, tăng một bộ đếm nghiệp vụ khác, hoặc tạo ID mới bên trong callback mà bạn chỉ muốn thực hiện một lần.

Nếu cần hành động phụ sau khi cập nhật:

```java
int newValue = balance.updateAndGet(current -> current + 20);
// Sau khi lời gọi thành công, phần code này chỉ chạy một lần theo luồng này.
recordSuccessfulUpdate(newValue);
```

`recordSuccessfulUpdate(...)` cũng là ký hiệu minh họa, không phải phương thức Java có sẵn. Đây là bước **sau** khi cập nhật chốt thành công; tuy nhiên cập nhật atomic và hành động bên ngoài **không phải một giao dịch duy nhất**. Nếu ứng dụng có thể bị dừng giữa hai bước, hoặc cần bảo đảm gửi sự kiện đúng một lần khi có retry toàn bộ tác vụ, phải thiết kế thêm giao dịch, outbox hoặc khóa idempotency phù hợp.

### Các lỗi tinh vi khác

- **Đọc biến thay đổi từ bên ngoài:** `updateAndGet(x -> x + currentBonus.get())` có thể tính phần cộng khác nhau giữa các lần thử nếu `currentBonus` đổi. Nếu nghiệp vụ yêu cầu giữ nguyên khoản cộng cho *một* hành động, chụp giá trị thưởng trước rồi dùng hằng số đó trong callback.
- **Thời gian và random:** `Instant.now()`, số ngẫu nhiên hoặc truy vấn trạng thái ngoài trong callback có thể cho kết quả khác nhau khi retry. Quyết định đầu vào bất biến trước khi cập nhật.
- **Ngoại lệ:** nếu callback ném ngoại lệ, lời gọi cập nhật đó không chốt giá trị đề xuất; các tác dụng phụ đã xảy ra ở những lượt callback trước **không được hoàn tác**.
- **Đọc hai atomic riêng lẻ:** callback chỉ bảo đảm CAS nguyên tử cho **một biến atomic đích**. Nó không chốt đồng thời một bản ghi nghiệp vụ trải qua nhiều biến khác.

## 6. Các API liên quan và giá trị trả về

| API | Cập nhật giá trị | Kết quả trả về | Lưu ý |
| --- | --- | --- | --- |
| `compareAndSet(expected, next)` | Nếu hiện tại bằng expected thì ghi next | `boolean` | `false` khi điều kiện không còn đúng |
| `getAndSet(next)` | Ghi next nguyên tử | **Giá trị cũ** | Không cần callback |
| `getAndIncrement()` | Cộng 1 | **Giá trị cũ** | Phù hợp bộ đếm đơn giản |
| `incrementAndGet()` | Cộng 1 | **Giá trị mới** | Không cần viết lambda |
| `getAndAdd(delta)` | Cộng delta | **Giá trị cũ** | Không cần viết callback |
| `addAndGet(delta)` | Cộng delta | **Giá trị mới** | Không cần viết callback |
| `getAndUpdate(f)` | Tính số mới bằng f | **Giá trị cũ của lần cập nhật thành công** | f có thể gọi lại |
| `updateAndGet(f)` | Tính số mới bằng f | **Giá trị mới đã chốt** | f có thể gọi lại |
| `getAndAccumulate(x, f)` | Tính bằng f(current, x) | **Giá trị cũ** | f có thể gọi lại |
| `accumulateAndGet(x, f)` | Tính bằng f(current, x) | **Giá trị mới** | f có thể gọi lại |

`AtomicInteger` sử dụng `IntUnaryOperator` cho `getAndUpdate`/`updateAndGet` và `IntBinaryOperator` cho `getAndAccumulate`/`accumulateAndGet`. `AtomicLong` có API tương tự với `LongUnaryOperator` và `LongBinaryOperator`.

Trong chương trình kiểm chứng, sau khi `value = 25`:

```java
int old = value.getAndUpdate(x -> x + 5);      // old = 25; value = 30
int now = value.accumulateAndGet(12, Integer::sum); // now = 42; value = 42
```

Với phép cộng đơn giản, thường ưu tiên `addAndGet(5)` hay `getAndAdd(5)`. Những phương thức chuyên biệt này truyền ý định rõ ràng và tránh việc tạo callback nghiệp vụ không cần thiết. Khi công thức phụ thuộc trạng thái hiện tại, như giới hạn trên/dưới, các phương thức cập nhật bằng hàm là thích hợp.

### Ví dụ giới hạn giá trị

```java
AtomicInteger capacity = new AtomicInteger(7);
int capped = capacity.updateAndGet(
        current -> Math.min(10, current + 4));
// capped = 10
```

Callback `Math.min(10, current + 4)` không có tác dụng phụ; nếu luồng khác thay đổi `capacity`, lời gọi có thể tính lại dựa trên giá trị mới.

**Chú ý overflow:** `current + 4` với `int` vẫn có thể tràn nếu gần `Integer.MAX_VALUE`. Nếu muốn báo lỗi thay vì tràn, dùng `Math.addExact(current, 4)` và quy định rõ cách xử lý ngoại lệ. `AtomicInteger` không tự ngăn tràn số học.

## 7. Tự viết vòng CAS khi cần điều kiện cụ thể

Một tình huống là chỉ cho phép giữ chỗ khi số ghế còn lại lớn hơn 0:

```java
static boolean tryReserve(AtomicInteger remaining) {
    for (;;) {
        int old = remaining.get();
        if (old <= 0) {
            return false;
        }
        if (remaining.compareAndSet(old, old - 1)) {
            return true;
        }
        // Trạng thái đổi: đọc lại và kiểm tra điều kiện lần nữa.
    }
}
```

Đây là **đoạn mã phương thức**, đặt bên trong lớp Java và import `AtomicInteger`. Khi số ghế bằng 0, trả false; khi còn ghế, thử trừ đi 1 bằng CAS. Nếu thất bại do tranh chấp, vòng lặp **kiểm tra lại cả điều kiện** trước khi thử tiếp.

Nếu viết `if (remaining.get() > 0) remaining.decrementAndGet();` thì việc kiểm tra và giảm số ghế không nguyên tử như một thao tác kết hợp; hai luồng có thể cùng thấy “còn 1 ghế” và đều giảm xuống, gây âm số ghế. Vòng CAS ở trên gắn điều kiện `old > 0` với phiên bản số mà CAS đang kiểm tra.

Một lời gọi `tryReserve` hoàn thành thành công tương ứng một lần giảm nguyên tử. Nhưng toàn bộ tác vụ nghiệp vụ (ví dụ ghi database, thanh toán, tạo vé) cần cơ chế giao dịch riêng nếu phải đảm bảo nhất quán qua nhiều tài nguyên.

## 8. AtomicLong và giá trị lớn

Khi đếm số lớn vượt phạm vi `int`, dùng `AtomicLong`:

```java
AtomicLong processedBytes = new AtomicLong(4_000_000_000L);
long newTotal = processedBytes.updateAndGet(current -> current + 1);
// newTotal = 4_000_000_001L
```

Tính nguyên tử và yêu cầu callback không có tác dụng phụ vẫn như `AtomicInteger`. Chọn kiểu `long` không loại bỏ nguy cơ overflow `long`; Java không tự làm phép cộng bão hòa. Nếu có thể vượt `Long.MAX_VALUE`, cần lựa chọn kiểu/thuật toán và kiểm tra giới hạn thích hợp.

Nếu bạn cần số thập phân chính xác như `BigDecimal` thay vì `int`/`long`, có thể dùng `AtomicReference<BigDecimal>` vì `BigDecimal` bất biến:

```java
// Cần import java.math.BigDecimal và
// java.util.concurrent.atomic.AtomicReference.
AtomicReference<BigDecimal> total =
        new AtomicReference<>(new BigDecimal("10.00"));

total.updateAndGet(old -> old.add(new BigDecimal("2.50")));
// total.get() = 12.50
```

Callback `old -> old.add(...)` tạo giá trị `BigDecimal` mới mà không sửa số cũ; cách làm này phù hợp với việc tính lại khi CAS thất bại. Trong `AtomicReference`, `compareAndSet` so khớp tham chiếu mong đợi, **không gọi `BigDecimal.equals()` hay `compareTo()`**. Trường hợp nhiều trường dữ liệu cần chốt cùng lúc, cân nhắc giữ một đối tượng trạng thái bất biến trong một `AtomicReference` hoặc dùng đồng bộ/giao dịch thích hợp.

## 9. Ranh giới của CAS: những điều không được suy diễn

### 9.1. CAS không đảm bảo callback chỉ chạy một lần

Lỗi hay gặp nhất là đặt hành động có tác dụng phụ vào callback và cho rằng API “chỉ cập nhật một lần” nghĩa là callback “chỉ chạy một lần”. **Kết quả tính thử bị CAS từ chối nhưng tác dụng phụ bên ngoài vẫn có thể xảy ra.**

Một callback phù hợp có dạng `old -> old + increment`, trong đó `increment` đã được xác định ổn định trước khi gọi; không thao tác cơ sở dữ liệu, gửi thông báo hay sinh ID trong đó.

### 9.2. Một atomic không biến nhiều bước nghiệp vụ thành giao dịch

`AtomicInteger` chỉ chốt một giá trị của một đối tượng atomic. Khi thay đổi hai biến độc lập, hoặc cần đồng bộ con số với dữ liệu ở file/database, các cập nhật riêng lẻ không trở thành một thao tác nguyên tử chung. Có thể dùng khóa, giao dịch hoặc một đối tượng trạng thái bất biến duy nhất tùy cấu trúc bài toán.

### 9.3. Liveness và contention

CAS là kỹ thuật thường dùng để triển khai cấu trúc dữ liệu và phép toán không dùng khóa tường minh. Dưới tranh chấp cao, một luồng có thể thử lại nhiều lần và tốn CPU; một lần gọi riêng không có bảo đảm chạy thành công sau đúng một số lần cố định. Khi công thức đắt đỏ, tính đi tính lại cũng có chi phí đáng kể.

Đối với một bộ đếm cộng dồn được cập nhật rất nhiều luồng và không đòi hỏi điều kiện CAS riêng cho mỗi lần cập nhật, `LongAdder` có thể là lựa chọn hữu ích để cải thiện thông lượng. Tuy nhiên, `LongAdder.sum()` không phải snapshot nguyên tử của tất cả cập nhật đang đồng thời diễn ra; nó không thay thế `AtomicLong` khi cần đọc một trạng thái chính xác tại một thời điểm để ra quyết định.

### 9.4. Quan hệ với volatile và memory visibility

`AtomicInteger.get()` và `set()` có ngữ nghĩa truy cập bộ nhớ kiểu volatile; `compareAndSet()` cung cấp thao tác nguyên tử và các hiệu ứng bộ nhớ tương ứng theo Java API. Các thao tác này giúp các luồng nhìn thấy việc cập nhật giá trị atomic theo mô hình bộ nhớ Java.

Điều đó **không làm cho mọi biến thông thường** hoặc mọi quan hệ giữa các đối tượng trở nên thread-safe. Một tham chiếu atomic tới đối tượng mutable cũng không tự bảo vệ các trường mutable bên trong đối tượng ấy.

### 9.5. ABA: trạng thái có thể đổi rồi quay lại

Nếu giá trị ban đầu là A, luồng khác thay thành B rồi lại A trước lúc CAS, thì `compareAndSet(A, next)` có thể thành công vì CAS kiểm tra **giá trị hiện tại**, chứ không biết lịch sử thay đổi. Đây là *ABA problem*, cần quan tâm khi nghiệp vụ phụ thuộc việc “chưa có thay đổi nào xảy ra” chứ không chỉ so sánh số hiện tại. Với bài toán phù hợp, có thể quản lý cả phiên bản/trạng thái hoặc dùng `AtomicStampedReference`; đừng mặc định CAS cho biết đã không có hoạt động giữa hai lần đọc.

## 10. Lỗi thường gặp và cách tránh

1. **Lầm tưởng `volatile int` đủ cho `x++`.** Nó không làm phép đọc–sửa–ghi thành một thao tác nguyên tử. Dùng `AtomicInteger.incrementAndGet()` hoặc đồng bộ phù hợp.
2. **Viết callback có tác dụng phụ.** Nếu `updateAndGet()` retry, lệnh gửi email hoặc ghi database có thể chạy nhiều lần. Đưa tác dụng phụ ra ngoài và thiết kế ranh giới giao dịch riêng.
3. **Dùng dữ liệu đang thay đổi ngoài callback.** Cùng một lời gọi API có thể tính kết quả khác nhau không chỉ vì atomic đổi mà còn vì biến ngoài đổi. Chụp dữ liệu đầu vào cần nhất quán trước khi cập nhật.
4. **Hiểu nhầm giá trị trả về.** `getAndUpdate` trả số cũ, `updateAndGet` trả số mới; tương tự với cặp accumulate.
5. **Tự viết CAS loop nhưng không đọc và kiểm tra lại điều kiện.** Khi CAS thất bại, phải xem trạng thái mới có còn cho phép thao tác không.
6. **Dùng callback cho phép cộng đơn giản không cần thiết.** `getAndAdd` hoặc `addAndGet` diễn đạt rõ hơn và tránh callback.
7. **Cho rằng AtomicInteger/AtomicLong không bao giờ tràn.** Tính nguyên tử khác hoàn toàn với kiểm tra phạm vi số học; dùng `Math.addExact` hoặc giới hạn nghiệp vụ khi cần.
8. **Thử nghiệm đa luồng bằng `sleep()` rồi tin kết quả luôn tái hiện.** Lịch luồng không xác định; ví dụ kiểm chứng ở trên dùng `CountDownLatch` để tạo thứ tự rõ ràng.
9. **Nhầm callback chạy lại với cập nhật được chốt nhiều lần.** Trong một lời gọi hoàn tất thành công, kết quả của một lần cập nhật được CAS chấp nhận; việc tính thử trước đó có thể bị bỏ.
10. **Dùng một atomic để suy ra nhất quán toàn hệ thống.** Khi có nhiều biến hoặc tài nguyên khác, cần thêm giao thức đồng bộ hay giao dịch.

## 11. Tự kiểm tra kiến thức

**Câu 1.** Vì sao `updateAndGet(x -> x + 1)` có thể đánh giá lambda nhiều lần nhưng một lời gọi thành công chỉ chốt một lần cộng?  
**Đáp án:** mỗi lần thử tính một ứng viên từ trạng thái quan sát; nếu CAS thất bại, ứng viên bị bỏ và lambda được gọi lại với trạng thái mới. Chỉ lần CAS thành công mới ghi ứng viên.

**Câu 2.** Có nên viết `atomic.updateAndGet(x -> { sendEmail(); return x + 1; })` không?  
**Đáp án:** không. `sendEmail()` minh họa tác dụng phụ có thể lặp lại khi retry; cần tách khỏi callback, và thiết kế xử lý nghiệp vụ nếu yêu cầu chính xác một lần.

**Câu 3.** Khi `AtomicInteger` đang là 8, `compareAndSet(7, 9)` trả gì?  
**Đáp án:** `false`, giá trị vẫn 8 vì giá trị mong đợi là 7.

**Câu 4.** Sau `int old = value.getAndUpdate(x -> x * 2)` với `value = 4` (không có cạnh tranh), `old` và `value.get()` là gì?  
**Đáp án:** `old = 4`, `value.get() = 8`.

**Câu 5.** Một lambda `x -> x + 3` có bắt buộc thỏa `f(f(x)) == f(x)` để an toàn retry không?  
**Đáp án:** không. Hàm cộng 3 không idempotent theo nghĩa toán học; điều cần là tính thử lại không có tác dụng phụ và kết quả mới đúng từ trạng thái được truyền vào.

**Câu 6.** Nếu có ba biến `AtomicInteger` riêng cho `available`, `reserved` và `sold`, một lời gọi CAS trên `available` có bảo đảm ba biến đổi đồng thời không?  
**Đáp án:** không. CAS trên một atomic không tạo giao dịch xuyên nhiều atomic hoặc các tài nguyên khác.

## 12. Kết luận

- **CAS** kiểm tra giá trị đang có và chỉ ghi giá trị mới nếu trạng thái vẫn khớp với giá trị mong đợi.
- **`updateAndGet`, `getAndUpdate` và các biến thể accumulate có thể tính callback nhiều lần** do tranh chấp và retry.
- **Hàm cập nhật cần không có tác dụng phụ**; tính lại không đồng nghĩa chốt nhiều lần. Không đặt ghi database, gửi thông báo hay cập nhật một tài nguyên khác trong callback.
- Với phép cộng/tăng đơn giản, cân nhắc `incrementAndGet` hoặc `addAndGet`; với điều kiện gắn với trạng thái hiện tại, sử dụng hàm cập nhật thuần hoặc vòng CAS tự kiểm tra lại điều kiện.
- Chọn `AtomicInteger` hay `AtomicLong` theo phạm vi số và quy tắc nghiệp vụ; tính nguyên tử không loại bỏ overflow, lỗi giao dịch đa tài nguyên hay vấn đề tranh chấp.

Tài liệu chính thức Java SE 17:

- [`AtomicInteger`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html)
- [`AtomicLong`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html)
- [`AtomicReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html)
- [`CountDownLatch`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [`LongAdder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-numbers-006">LongAdder, DoubleAdder và bài toán thống kê có tranh chấp</a>

<details>
<summary>Xem chi tiết</summary>

# `LongAdder`, `DoubleAdder` và bài toán thống kê có tranh chấp trong Java

## 1. Bài toán: rất nhiều luồng cùng tăng một bộ đếm

Trong một máy chủ Java, nhiều luồng có thể cùng thống kê lượt request, số lỗi, cache hit hoặc tổng thời gian xử lý. Nếu dùng biến `long` và lệnh `count++`, nhiều cập nhật có thể bị **mất** vì `++` thực chất gồm đọc, tính, ghi. Hai luồng cùng đọc 10, tính 11 và ghi 11, trong khi kết quả đúng sau hai lần tăng phải là 12.

`volatile long count` không giải quyết được phép cộng: `volatile` cung cấp bảo đảm về hiển thị và thứ tự bộ nhớ, **không làm read-modify-write trở thành nguyên tử**. Khi cần đếm đồng thời, các cách tiếp cận thường là `synchronized`/`Lock`, `AtomicLong` hoặc `LongAdder`.

`LongAdder` và `DoubleAdder` (gói `java.util.concurrent.atomic`, có từ Java 8) được thiết kế cho kiểu công việc **nhiều luồng liên tục cộng vào một đại lượng thống kê**, đặc biệt khi có tranh chấp (*contention*) trên cùng một biến.

Sau bài này, bạn sẽ biết:

- Vì sao một bộ đếm `AtomicLong` có thể trở thành điểm nóng khi nhiều luồng cùng cập nhật.
- `LongAdder` và `DoubleAdder` giảm tranh chấp bằng cách phân tán phép cộng như thế nào.
- Chạy ví dụ nhiều luồng và đọc kết quả đáng tin sau `join()`.
- Phân biệt thread-safe updates với **đọc snapshot nguyên tử**.
- Dùng `ConcurrentHashMap<K, LongAdder>` để đếm theo nhãn.
- Chọn cách xử lý đúng cho reset thống kê, quota nghiêm ngặt và giá trị `double` xấp xỉ.

## 2. Cơ chế: phân tán phép ghi để giảm cạnh tranh

`AtomicLong` giữ một trạng thái nguyên tử dùng chung; nhiều luồng thường thử cập nhật trạng thái đó bằng CAS (*compare-and-set*) hoặc các primitive nguyên tử tương đương. Khi cùng tranh một điểm nóng, các lần thử có thể bị thất bại và phải thử lại.

`LongAdder` được tối ưu cho tình huống một lượng lớn thao tác `add`/`increment` cạnh tranh. Về mặt ý tưởng, nó có tổng cơ sở và có thể mở rộng các ô (*cells*) để trải phép ghi:

```text
Worker A ---> [cell A] --+
Worker B ---> [cell B] --+---> sum() = base + tổng cell
Worker C ---> [cell C] --+
Worker D ---> [base] ----+
```

Sơ đồ chỉ minh họa cơ chế: **không đảm bảo mỗi luồng sở hữu riêng một cell**, cũng không đảm bảo số ô cố định. Việc phân phối và mở rộng là chi tiết triển khai JDK. Điểm đánh đổi là cộng vào các phần độc lập có thể giảm contention khi ghi, nhưng `sum()` phải đọc/gộp các phần, có thể tốn công hơn so với `AtomicLong.get()`.

`DoubleAdder` áp dụng ý tưởng tương tự cho tổng `double`. Cả hai không phải lựa chọn nhanh nhất trong mọi hoàn cảnh: với rất ít tranh chấp, cập nhật thưa, bộ nhớ hạn chế hoặc đọc thường xuyên, `AtomicLong` có thể đơn giản và phù hợp hơn. Hiệu năng thực tế cần đo trên workload cụ thể.

### Ba yêu cầu cần phân biệt

1. **Mọi lần cộng hoàn thành đều được ghi nhận** theo hợp đồng đồng thời của adder, miễn không có reset cạnh tranh và không có vấn đề tràn/precision liên quan.
2. **Đọc tổng sau khi đã ngừng cập nhật**: `sum()` phản ánh số lần cộng đã hoàn tất, phù hợp để nghiệm thu kết quả các worker.
3. **Đọc snapshot nguyên tử trong lúc các worker vẫn cộng**: `sum()` **không cam kết điều này**; có thể lấy trạng thái của các cell ở những thời điểm khác nhau.

## 3. API và khác biệt quan trọng

| Thao tác | `LongAdder` | `DoubleAdder` |
|---|---|---|
| Khởi tạo | `new LongAdder()` | `new DoubleAdder()` |
| Cộng một lượng | `add(long delta)` | `add(double delta)` |
| Cộng/trừ 1 | `increment()`, `decrement()` | `add(1.0)`, `add(-1.0)` |
| Đọc tổng | `sum(): long` | `sum(): double` |
| Đọc rồi reset | `sumThenReset()` | `sumThenReset()` |
| Reset không lấy tổng | `reset()` | `reset()` |
| Lấy qua `Number` | `longValue()` tương ứng với tổng | `doubleValue()` tương ứng với tổng |
| Có `get()`, `compareAndSet()`? | Không | Không |

Các thao tác `add` có thể nhận giá trị âm; adder không chỉ đếm số dương. Giá trị khởi đầu của cả hai là 0. `LongAdder.sum()` trả về `long`, nên vẫn có khả năng **tràn số nguyên 64 bit**, không tự kiểm tra `Math.addExact`.

Cần lưu ý: `LongAdder` **không có phương thức `get()`** giống `AtomicLong`; muốn lấy tổng dùng `sum()`. `LongAdder.longValue()` cũng không làm snapshot trở nên nguyên tử.

## 4. Ví dụ hoàn chỉnh: bốn luồng cùng tăng `LongAdder`

**Tệp `LongAdderDemo.java`**, chạy Java 17+:

```java
import java.util.concurrent.atomic.LongAdder;

public class LongAdderDemo {
    public static void main(String[] args) throws InterruptedException {
        int workersCount = 4;
        int updatesPerWorker = 100_000;
        LongAdder requests = new LongAdder();

        Thread[] workers = new Thread[workersCount];

        for (int i = 0; i < workersCount; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < updatesPerWorker; j++) {
                    requests.increment();
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("expected=" + workersCount * updatesPerWorker);
        System.out.println("actual=" + requests.sum());
    }
}
```

Chạy:

```bash
javac LongAdderDemo.java
java LongAdderDemo
```

Kết quả:

```text
expected=400000
actual=400000
```

`join()` ở đây rất quan trọng. Nó bảo đảm tác vụ của các worker đã kết thúc trước khi main đọc tổng, đồng thời cung cấp quan hệ *happens-before* cho hành động của worker. Vì không còn ghi đồng thời, `sum()` lúc này cung cấp tổng của 400.000 lần `increment()` theo ví dụ.

Nếu đọc `sum()` ngay sau khi gọi `start()`, kết quả có thể nhỏ hơn 400.000 vì các worker chưa xong. Đó không tự chứng minh có lỗi mất cập nhật; khác biệt có thể chỉ do thời điểm quan sát.

Ví dụ thao tác cơ bản (đặt trong một phương thức khi chạy):

```java
LongAdder pending = new LongAdder();
pending.increment(); // +1
pending.add(5);       // +5
pending.decrement(); // -1
System.out.println(pending.sum()); // 5
```

Với biến đã chia sẻ cho nhiều luồng, `increment()`, `decrement()` và `add(long)` hỗ trợ cập nhật đồng thời. Nhưng việc đọc tổng trong khi vẫn đang ghi vẫn mang tính thống kê.

## 5. Ví dụ `DoubleAdder`: tổng thời gian xử lý và số mẫu

`DoubleAdder` dùng `double` để cộng dồn những đại lượng **chấp nhận độ chính xác dấu phẩy động**, ví dụ tổng latency hoặc phép đo. Muốn tính trung bình, thường dùng thêm một `LongAdder` đếm mẫu.

**Tệp `DoubleAdderDemo.java`:**

```java
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class DoubleAdderDemo {
    public static void main(String[] args) throws InterruptedException {
        int workersCount = 4;
        int samplesPerWorker = 10_000;

        DoubleAdder total = new DoubleAdder();
        LongAdder count = new LongAdder();
        Thread[] workers = new Thread[workersCount];

        for (int i = 0; i < workersCount; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < samplesPerWorker; j++) {
                    total.add(0.75);
                    count.increment();
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        long n = count.sum();
        double totalTime = total.sum();
        System.out.println("count=" + n);
        System.out.println("total=" + totalTime);
        System.out.println("average=" + totalTime / n);
    }
}
```

```bash
javac DoubleAdderDemo.java
java DoubleAdderDemo
```

```text
count=40000
total=30000.0
average=0.75
```

Ví dụ chọn `0.75`, một số biểu diễn chính xác trong nhị phân, để giá trị hiển thị không bị nhiễu do làm tròn. Với `0.1`, không nên giả định tổng được biểu diễn thập phân chính xác.

**Lưu ý về tính nhất quán:** Mỗi worker gọi `total.add(0.75)` rồi `count.increment()`. Nếu dashboard đọc `count.sum()` và `total.sum()` giữa lúc các worker chạy, hai kết quả có thể tương ứng những thời điểm khác nhau. Tính trung bình như vậy chỉ là một quan sát best effort. Sau khi `join()` tất cả worker, hai tổng mới ổn định theo giới hạn số học ví dụ.

Một trung bình có `n == 0` phải được xử lý riêng. Nếu tính `totalTime / n` với `double` và `n == 0`, kết quả có thể là `NaN` hoặc vô cực, không phải giá trị trung bình hợp lệ. Trong ứng dụng thực, chỉ chia khi có mẫu.

## 6. `sum()` thread-safe nhưng không phải snapshot nguyên tử

Một số tài liệu dùng từ “thread-safe” khiến người đọc suy ra rằng **mọi lần đọc đều nhìn thấy một trạng thái toàn cục nhất quán**. Điều đó không đúng với `LongAdder.sum()`/`DoubleAdder.sum()`.

Khi đang ghi đồng thời, một lời gọi `sum()` có thể cộng phần base và từng cell ở những thời điểm khác nhau. Kết quả hữu ích cho thống kê gần thời điểm đọc, nhưng không có bảo đảm nó đại diện cho một khoảnh khắc đơn nhất theo mô hình linearizable.

Ví dụ dashboard hiển thị “khoảng 10.000 request đã xử lý” có thể chấp nhận độ trễ quan sát. Nhưng tính toán **quota cứng** như giới hạn phát hành tối đa 100 mã phải dùng cơ chế có atomic check-then-update.

Đoạn mã sai về mặt quota:

```java
// Minh họa, quota là LongAdder dùng chung.
if (quota.sum() < 100) {
    quota.increment();
    // Cấp quyền cho request này.
}
```

Hai luồng có thể cùng thấy `99` rồi cùng cấp quyền và tăng, làm vượt 100. Thay `LongAdder` bằng `AtomicLong` rồi vẫn thực hiện `get() < 100` và `incrementAndGet()` riêng lẻ **cũng chưa đủ**: cặp kiểm tra và cập nhật phải được gắn thành một giao thức nguyên tử, chẳng hạn vòng CAS hoặc lock/transaction thích hợp.

`AtomicLong.get()` cung cấp phép đọc nguyên tử trên **một biến**; `AtomicLong.compareAndSet()` giúp triển khai phép cập nhật có điều kiện. `LongAdder` thiếu CAS trên toàn tổng, nên không dùng làm sequencer tạo số thứ tự duy nhất hoặc cơ chế quota chính xác.

## 7. `reset()` và `sumThenReset()`: thận trọng với cập nhật đang diễn ra

Cả hai adder có hai phương thức đáng chú ý:

- `reset()`: đặt bộ cộng dồn về zero.
- `sumThenReset()`: trả tổng đang quan sát rồi đặt lại về zero.

Chúng **không cung cấp phép cắt một cửa sổ thống kê nguyên tử** trong khi nhiều luồng đang `add()`. Theo hợp đồng API, dùng chúng phù hợp nhất khi **không có cập nhật đồng thời**. Nếu cạnh tranh với lần reset, các cập nhật có thể bị quan sát lệch cửa sổ hoặc không được bảo toàn theo yêu cầu thống kê chính xác.

**Tệp `AdderResetDemo.java`** (không có ghi đồng thời):

```java
import java.util.concurrent.atomic.LongAdder;

public class AdderResetDemo {
    public static void main(String[] args) {
        LongAdder requests = new LongAdder();
        requests.add(8);
        requests.increment();

        System.out.println("before=" + requests.sum());
        System.out.println("drained=" + requests.sumThenReset());
        System.out.println("after=" + requests.sum());

        requests.add(3);
        requests.reset();
        System.out.println("afterReset=" + requests.sum());
    }
}
```

```bash
javac AdderResetDemo.java
java AdderResetDemo
```

```text
before=9
drained=9
after=0
afterReset=0
```

Ứng dụng muốn thống kê theo từng phút có thể dùng reset best effort nếu chấp nhận sai số; nếu phải đảm bảo **không mất sự kiện**, cần điều phối worker dừng ghi vào bộ đếm cũ, thiết kế epoch hoặc dùng hệ thống event/metric có cơ chế cắt cửa sổ theo hợp đồng.

Hoán đổi `AtomicReference<LongAdder>` **không tự động giải quyết toàn bộ**: worker có thể đọc tham chiếu adder cũ trước thời điểm hoán đổi, sau đó cập nhật adder cũ khi luồng thu thập đã lấy tổng. Vì vậy hệ thống vẫn cần quy tắc phối hợp và trạng thái kết thúc ghi.

## 8. Thống kê theo nhãn bằng `ConcurrentHashMap` và `LongAdder`

Khi một ứng dụng ghi nhận nhiều loại sự kiện, một cách dùng phổ biến là mỗi khóa map giữ một adder riêng:

```text
"ok"     ---> LongAdder (số request thành công)
"retry"  ---> LongAdder (số request thử lại)
"failed" ---> LongAdder (số request thất bại)
```

**Tệp `ConcurrentMetricsDemo.java`:**

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class ConcurrentMetricsDemo {
    private static final ConcurrentHashMap<String, LongAdder> COUNTS =
            new ConcurrentHashMap<>();

    private static void record(String event) {
        COUNTS.computeIfAbsent(event, key -> new LongAdder())
                .increment();
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] workers = new Thread[4];

        for (int i = 0; i < workers.length; i++) {
            final int index = i;
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 10_000; j++) {
                    record("ok");
                    if (index % 2 == 0) {
                        record("retry");
                    }
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("ok=" + COUNTS.get("ok").sum());
        System.out.println("retry=" + COUNTS.get("retry").sum());
    }
}
```

```bash
javac ConcurrentMetricsDemo.java
java ConcurrentMetricsDemo
```

```text
ok=40000
retry=20000
```

`ConcurrentHashMap.computeIfAbsent()` phối hợp việc tạo giá trị cho **khóa chưa có**; các luồng sau đó tăng cùng đối tượng `LongAdder`. Dùng `if (!map.containsKey(key)) { map.put(key, new LongAdder()); }` là không an toàn nếu có cạnh tranh vì hai luồng có thể tạo ra hai bộ đếm rồi ghi đè tham chiếu của nhau.

Tuy vậy, `computeIfAbsent(...).increment()` là hai bước, **không phải một transaction nguyên tử cho cả map và bộ đếm**. Nếu một luồng đồng thời `remove` hoặc thay giá trị của khóa, worker đang giữ adder cũ vẫn có thể cộng vào một đối tượng không còn nằm trong map. Khi reset/đổi thế hệ metrics, cần quy tắc sở hữu và đồng bộ riêng.

Đồng thời, đừng cho phép tên nhãn là chuỗi vô hạn sinh từ input tùy ý của người dùng; mỗi nhãn tạo ít nhất một counter và có thể tiêu hao nhiều bộ nhớ. Thiết kế nên giới hạn số nhãn (*cardinality*).

## 9. `DoubleAdder` và sai số dấu phẩy động

`DoubleAdder` không làm phép cộng `double` trở nên chính xác thập phân. Hai vấn đề chính là:

### 9.1. `0.1` không được biểu diễn chính xác bằng `double`

Các số như `0.1` có biểu diễn nhị phân vô hạn, nên cộng dồn có thể tạo sai lệch so với phép tính thập phân lý tưởng. Với phép đo thống kê, có thể chấp nhận sai số theo dung sai đã định. Với tiền, khối lượng phải khớp tuyệt đối hoặc đối soát kế toán, `DoubleAdder` không phù hợp làm nguồn dữ liệu chính xác.

### 9.2. Thứ tự cộng ảnh hưởng kết quả

Phép cộng floating-point không kết hợp (*non-associative*) khi có làm tròn. Ví dụ mảnh mã sau có thể đặt trong `main`:

```java
double a = 1.0e16;
double b = -1.0e16;
double c = 1.0;

System.out.println((a + b) + c); // 1.0
System.out.println(a + (b + c)); // 0.0
```

Số `1.0` có thể bị mất khi cộng vào `-1e16` do hạn chế chữ số hữu hiệu. Trong `DoubleAdder`, việc các luồng cập nhật các cell khác nhau có thể làm thay đổi thứ tự gom tổng, nên không nên đòi hỏi kết quả **giống nhau đến bit cuối** giữa mọi lần chạy đối với dữ liệu nhạy cảm với thứ tự cộng.

### 9.3. Xử lý dữ liệu không hữu hạn

`DoubleAdder` có thể nhận `Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`; các giá trị này có thể khiến tổng trở thành `NaN` hoặc vô cực. Nếu yêu cầu thống kê là tổng số đo hữu hạn, cần xác thực đầu vào trước khi cộng:

```java
// Mảnh mã bên trong phương thức; sample là double,
// total là DoubleAdder đã khởi tạo.
if (!Double.isFinite(sample)) {
    throw new IllegalArgumentException("Non-finite sample");
}
total.add(sample);
```

Nếu số đo không được âm, kiểm tra thêm `sample >= 0.0`.

### 9.4. Chọn biểu diễn cho tiền

Khi cần lưu tiền một cách chính xác, có thể dùng `BigDecimal` kết hợp transaction/đồng bộ hoặc biểu diễn bằng số nguyên của **đơn vị tiền nhỏ nhất** (với chính sách quy đổi, kiểm tra tràn số và phần lẻ rõ ràng). Chỉ dùng `LongAdder` cộng tiền nguyên đơn vị cho thống kê khi chấp nhận kiểu snapshot của nó; nó không thay thế cơ chế giao dịch kế toán.

## 10. So sánh chọn công cụ

| Nhu cầu | Lựa chọn thường phù hợp | Ghi chú |
|---|---|---|
| Nhiều luồng ghi một bộ đếm metrics | `LongAdder` | Giảm điểm tranh chấp trong một số tải |
| Nhiều luồng cộng `double` cho số đo xấp xỉ | `DoubleAdder` | Có giới hạn precision, rounding |
| Cần `get()`, `compareAndSet()`, cấp sequence | `AtomicLong` | Thao tác nguyên tử trên một biến |
| Cần check-then-update quota hoặc nhiều trường nhất quán | CAS/lock/transaction | Thiết kế toàn bộ thao tác nguyên tử |
| Cần thống kê theo khóa | `ConcurrentHashMap<K,LongAdder>` | Giới hạn cardinality, chú ý xóa/thay counter |
| Cần phân cửa sổ mà không mất sự kiện | Giao thức đồng bộ hoặc metric hệ thống hỗ trợ bucket | `sumThenReset` không đủ khi cạnh tranh |
| Ít contention, đọc nhiều, tài nguyên giới hạn | Cân nhắc `AtomicLong` | Đơn giản, có thể ít overhead |
| Tiền hoặc thập phân cần đúng tuyệt đối | `BigDecimal`/đơn vị nguyên + giao dịch | Thread safety không đảm bảo độ chính xác thập phân |

Không tuyên bố adder luôn nhanh hơn atomics: benchmark cần dùng JMH, warm-up JIT, số luồng, tỷ lệ đọc/ghi, kích thước khóa và môi trường đại diện. `System.nanoTime()` quanh một vòng lặp đơn giản thường chưa đủ bằng chứng để lựa chọn cơ chế đồng thời.

## 11. Lỗi thường gặp

| Sai lầm | Hệ quả | Cách sửa |
|---|---|---|
| `long++` hoặc `volatile long++` giữa nhiều worker | Lost update | Adder, atomic hoặc lock |
| Gọi `adder.get()` | Không có API này | Dùng `sum()` |
| Cho rằng `sum()` nguyên tử | Snapshot nhiều cell thiếu tính nhất quán | Chỉ dùng cho metrics best effort, hoặc đợi writer |
| Dùng `sum() < limit` rồi `increment()` để giữ quota | Hai worker có thể cùng được cấp | CAS/lock/transaction |
| Gọi `sumThenReset()` khi worker vẫn cộng | Sai lệch cửa sổ, có thể không giữ mọi cập nhật | Điều phối cập nhật và reset |
| Xem `DoubleAdder` là `BigDecimal` đa luồng | `double` có sai số | Chọn biểu diễn phù hợp |
| Kỳ vọng kết quả floating-point luôn giống bit | Thứ tự cộng không cố định | Kiểm tra sai số phù hợp |
| Thay map counter đang được worker dùng | Worker có thể ghi vào counter cũ | Quy tắc ownership/epoch |
| Không giới hạn nhãn map | Tăng bộ nhớ vô hạn | Giới hạn miền nhãn |
| Cho rằng `LongAdder` không tràn | Vẫn là `long` | Đánh giá miền giá trị và điều kiện tràn |

## 12. Bài tập tự kiểm tra kèm lời giải

1. **8 worker gọi `LongAdder.increment()` mỗi worker 10.000 lần. Khi nào `sum()` chắc chắn đạt 80.000?**  
   Sau khi tất cả worker hoàn tất và không có cập nhật/reset nào khác, với điều kiện không tràn số.

2. **`LongAdder` có trả số thứ tự duy nhất trong lần `increment()` không?**  
   Không. `increment()` không trả giá trị; `sum()` không phải số thứ tự gắn với chính phép cập nhật. Có thể cân nhắc `AtomicLong.incrementAndGet()` cho cấp sequence.

3. **Vì sao `LongAdder.sum()` không thích hợp để kiểm tra quota chính xác?**  
   Không có snapshot nguyên tử, và riêng thao tác kiểm tra rồi cập nhật cũng cần được đồng bộ chung.

4. **`DoubleAdder` có `increment()` không?**  
   Không. Dùng `add(1.0)`.

5. **Sau khi worker kết thúc, `DoubleAdder` nhận 100 lần `0.75` cho tổng bao nhiêu?**  
   `75.0`. Đây là dữ liệu nhị phân biểu diễn chính xác và phép cộng trong miền không làm mất phần lẻ theo ví dụ.

6. **Tại sao gọi `sumThenReset()` trong lúc writer hoạt động không phù hợp để thống kê bắt buộc chính xác?**  
   Vì đọc và reset không phải thao tác nguyên tử trên toàn cấu trúc; cập nhật cạnh tranh có thể không được gắn đúng vào một kỳ thống kê.

7. **Vì sao kết hợp `ConcurrentHashMap.computeIfAbsent(k, ...)` với `LongAdder` thường được dùng?**  
   Nó tạo/tra cứu counter dùng chung theo khóa an toàn ở cấp map, rồi mỗi lần `increment()` có thể thực hiện đồng thời.

8. **Nếu `DoubleAdder` tính tiền, điều gì sai về mặt số học?**  
   `double` không biểu diễn chính xác mọi số thập phân và thứ tự cộng có thể thay đổi kết quả. Dùng mô hình số thập phân/chia đơn vị chính xác hơn.

## 13. Ghi nhớ và tài liệu

`LongAdder` và `DoubleAdder` giúp các luồng **cộng dồn số liệu thống kê** khi có contention. Lợi ích chủ yếu là giảm áp lực cập nhật trên một vị trí nguyên tử. Đổi lại, `sum()` không cam kết một snapshot nguyên tử khi đang ghi; `sumThenReset()` không tạo ranh giới kỳ thống kê chính xác khi cạnh tranh; `DoubleAdder` vẫn tuân theo giới hạn IEEE 754.

Tài liệu API chính thức:

- [Java 21 — `LongAdder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html)
- [Java 21 — `DoubleAdder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/DoubleAdder.html)
- [Java 21 — `AtomicLong`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html)
- [Java 21 — `ConcurrentHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-numbers-007">sum của adder không phải snapshot nguyên tử trong lúc đang cập nhật</a>

<details>
<summary>Xem chi tiết</summary>

# sum() của adder không phải snapshot nguyên tử trong lúc đang cập nhật

`LongAdder` và `DoubleAdder` trong `java.util.concurrent.atomic` được thiết kế cho trường hợp **nhiều luồng cùng cập nhật một tổng hoặc bộ đếm**, đặc biệt để giảm tranh chấp khi tần suất cập nhật cao. Chúng có thể cho thông lượng cập nhật tốt hơn `AtomicLong` trong một số tình huống contention cao, đổi lại cách đọc tổng bằng `sum()` có hợp đồng yếu hơn một thao tác đọc nguyên tử.

Điểm cốt lõi của bài: **mỗi lời gọi `increment()`/`add()` có thể cập nhật trạng thái chia sẻ một cách an toàn giữa các luồng, nhưng `sum()` không bảo đảm lấy toàn bộ tổng tại một thời điểm nguyên tử duy nhất khi có cập nhật đồng thời.** Bài học thuộc nhóm *Số và đồng thời*, tập trung giải thích cơ chế này, hậu quả khi dùng `sum()` làm điều kiện đúng/sai, thời điểm nào kết quả đáng tin cậy và khi nào cần chọn cơ chế khác.

## 1. Ba khái niệm khác nhau: cập nhật an toàn, đọc tổng và snapshot nguyên tử

Cần phân biệt:

1. **Cập nhật an toàn giữa các luồng (thread-safe update):** nhiều luồng gọi `adder.increment()` hoặc `adder.add(delta)` mà không phải dùng một biến `long` thông thường với `count++` gây mất cập nhật do data race.
2. **Giá trị tổng được đọc (aggregate read):** `adder.sum()` cộng những thành phần đang giữ tổng và trả về một `long`. Giá trị này hữu ích để quan sát số liệu hoạt động.
3. **Snapshot nguyên tử (atomic snapshot):** một thao tác đọc được bảo đảm có thể xem như đọc toàn bộ trạng thái ở **một thời điểm logic duy nhất** giữa lúc bắt đầu và kết thúc thao tác, dù các luồng khác vẫn cập nhật.

`LongAdder.sum()` đáp ứng mục đích (2) nhưng tài liệu API **không hứa điều (3)**. Trong lúc nó lấy số liệu từ các thành phần, một số cập nhật khác có thể diễn ra. Do vậy, kết quả có thể **không chứa tất cả cập nhật trùng với khoảng thời gian đọc**. Khi không còn cập nhật đồng thời, `sum()` trả tổng chính xác của các cập nhật đã hoàn tất, giả sử không có vấn đề tràn số hoặc thao tác reset làm thay đổi trạng thái.

Không nên nhầm **snapshot không nguyên tử** với **mọi phép cộng đều sai**. Các lời gọi cập nhật của `LongAdder` vẫn phục vụ đếm đồng thời; thiếu bảo đảm nằm ở thao tác *quan sát tập hợp các thành phần tại cùng một thời điểm*, không phải việc `LongAdder` biến thành một biến `long` không đồng bộ.

## 2. Vì sao LongAdder có tổng gồm nhiều phần?

`AtomicLong` giữ một giá trị nguyên tử duy nhất. Khi nhiều luồng cập nhật cùng giá trị bằng CAS hoặc các thao tác atomic, chúng có thể tranh chấp trên cùng điểm cập nhật.

`LongAdder` giải quyết bài toán khác: giữ một **giá trị nền** và có thể mở rộng thêm các **ô thành phần (cells/stripes)** khi có tranh chấp. Nhiều luồng được phân tán giữa các ô để giảm contention khi thêm vào tổng. Mô hình khái niệm:

~~~text
Writers:
Thread A --add(1)--> base / cell[0]
Thread B --add(1)-->        cell[1]
Thread C --add(1)-->        cell[2]
                                 |
Reader: sum() ------------------+--> cộng base + các cell
                                      theo từng lượt đọc
~~~

Đây là **sơ đồ giải thích cơ chế**, không phải cam kết về số lượng ô, cách ánh xạ thread sang ô hoặc thuật toán từng dòng trong mọi JDK. Tùy mức contention, cấu trúc có thể khác nhau.

Nếu một phép đọc tổng phải truy cập nhiều ô liên tiếp, nó không thể mặc nhiên coi các giá trị vừa đọc là **một bản chụp chung bất biến**. Trong khi reader đọc ô thứ nhất, writer có thể cập nhật ô thứ hai; trước khi reader đọc ô tiếp theo, writer khác cũng có thể cập nhật ô thứ nhất. Các lần đọc thành phần xảy ra vào những thời điểm khác nhau.

JavaDoc mô tả rõ hợp đồng của `LongAdder.sum()`: **không phải atomic snapshot**; nếu không có cập nhật đồng thời, kết quả chính xác, còn các cập nhật xảy ra trong khi tính tổng có thể không được tính vào kết quả trả về.

Điều này không có nghĩa `sum()` nhất thiết trả kết quả sai hoặc thiếu trong mỗi lần chạy. Trên workload đơn giản, một lần đo có thể tình cờ khớp tổng ở thời điểm nào đó. **Không thể chứng minh tính nguyên tử chỉ bằng việc chạy thử nhiều lần mà luôn thấy con số hợp lý.**

## 3. LongAdder có những API nào liên quan?

| API | Ý nghĩa | Cần lưu ý khi dùng đồng thời |
|---|---|---|
| `new LongAdder()` | Tạo tổng ban đầu bằng 0 | Chia sẻ đúng một instance giữa các writer khi cùng một bộ đếm |
| `add(long x)` | Cộng giá trị `x` (có thể âm) | An toàn cho cập nhật đồng thời |
| `increment()` | Tương đương `add(1)` | Hợp lý cho bộ đếm sự kiện |
| `decrement()` | Tương đương `add(-1)` | Có cập nhật giảm; không suy đoán tổng luôn tăng |
| `sum()` | Đọc tổng từ các thành phần | **Không phải atomic snapshot** khi có writer đồng thời |
| `longValue()` | Tương đương `sum()` | Không mạnh hơn `sum()` |
| `toString()` | Chuyển kết quả `sum()` thành chuỗi | Không phải một snapshot mạnh hơn |
| `reset()` | Đặt các phần giữ tổng về 0 | Chỉ dùng khi biết không có writer đồng thời |
| `sumThenReset()` | Đọc tổng rồi reset về 0 | Không phải thao tác nguyên tử đọc-và-xóa khi writer vẫn chạy |

Đặc biệt, `sumThenReset()` **không tương đương** với thao tác nguyên tử `AtomicLong.getAndSet(0)` về độ bảo đảm dưới contention. Các phần giữ tổng được xử lý lần lượt; nếu writer cập nhật trong lúc reset, API không bảo đảm giá trị trả về chính là toàn bộ dữ liệu trước ranh giới reset. Không dùng `sumThenReset()` như một công cụ tự động chia số liệu thành các cửa sổ **không mất hoặc đếm nhầm cập nhật** khi các writer vẫn hoạt động.

## 4. Ví dụ Java 17+ chạy độc lập, kết quả có thể kiểm chứng

Tệp `AdderSnapshotDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

public class AdderSnapshotDemo {
    public static void main(String[] args) throws InterruptedException {
        final int workers = 4;
        final int iterations = 50_000;
        LongAdder counter = new LongAdder();
        CountDownLatch go = new CountDownLatch(1);
        Thread[] threads = new Thread[workers];

        for (int i = 0; i < workers; i++) {
            threads[i] = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int j = 0; j < iterations; j++) {
                    counter.increment();
                }
            });
            threads[i].start();
        }

        System.out.println("before release = " + counter.sum());
        go.countDown();

        // Chỉ quan sát: KHÔNG khẳng định đây là snapshot nguyên tử.
        long during = counter.sum();
        System.out.println("during within range = " +
                (during >= 0 && during <= workers * iterations));

        // Sau join(): không còn writer cập nhật counter.
        for (Thread thread : threads) {
            thread.join();
        }
        System.out.println("after join = " + counter.sum());

        // sumThenReset được dùng khi hoàn toàn không có writer.
        LongAdder batch = new LongAdder();
        batch.add(3);
        batch.add(4);
        System.out.println("quiescent batch = " + batch.sumThenReset());
        System.out.println("after reset = " + batch.sum());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AdderSnapshotDemo.java
java AdderSnapshotDemo
~~~

Kết quả mong đợi (khi không có gián đoạn từ bên ngoài):

~~~text
before release = 0
during within range = true
after join = 200000
quiescent batch = 7
after reset = 0
~~~

Giải thích:

- Trước `go.countDown()`, mọi worker đang đợi cổng `CountDownLatch`, nên chưa có `increment()`: kết quả 0 là ổn định.
- Sau khi mở cổng, `during` có thể là 0, vài nghìn, 200.000 hoặc một giá trị khác trong miền kiểm tra; chương trình **không in số cụ thể của `during`** vì nó phụ thuộc lịch thực thi.
- Trong ví dụ chỉ có **tăng 1**, không reset/giảm/tràn `long` và tổng các lần tăng tối đa 200.000. Kiểm tra miền giá trị là hợp lý cho ví dụ này, **nhưng không chứng minh `sum()` là snapshot nguyên tử**.
- `Thread.join()` đợi cả bốn worker kết thúc. Mỗi worker thực hiện 50.000 cập nhật, nên sau `join()` không còn writer, `sum()` cho chính xác `4 × 50.000 = 200.000`. `join()` cũng thiết lập quan hệ happens-before để thread chính quan sát kết quả công việc đã hoàn tất.
- `batch.sumThenReset()` trả 7 và reset về 0 **vì ở đoạn này không có cập nhật đồng thời**.

Mã trên là ví dụ học về hợp đồng API, **không phải phép thử race có mục tiêu ép JVM lộ ra một kết quả sai**. Hành vi quan sát giữa chừng không mang tính xác định; nếu muốn kiểm chứng đúng/sai cần dựa vào hợp đồng và phân tích cơ chế, không viết unit test đòi một giá trị `during` cụ thể.

## 5. Diễn tiến thời gian: vì sao không có một thời điểm chụp chung?

Giả sử nội bộ đang giữ hai thành phần tương ứng với các nhóm writer. Diễn tiến minh họa:

| Mốc | Writer | Reader gọi `sum()` |
|---|---|---|
| t0 | Các ô đang chứa cập nhật đã hoàn tất | Bắt đầu tính tổng |
| t1 | Writer A cập nhật thành phần thứ nhất | Reader đã hoặc đang đọc thành phần thứ nhất |
| t2 | Writer B cập nhật thành phần thứ hai | Reader đọc thành phần thứ hai |
| t3 | Các cập nhật khác tiếp tục | Reader cộng những giá trị nó đã đọc |
| t4 | Writer vẫn có thể chạy | `sum()` trả một số |

Không có một "điểm đóng băng" chung cho tất cả thành phần. Một số cập nhật đồng thời có thể được bao gồm, một số không; không thể nói `sum()` nhất thiết phản ánh tổng **tại chính thời điểm gọi**, **thời điểm trả về**, hay **toàn bộ cập nhật đã xảy ra trong khi đọc**.

**Chú ý sự khác nhau giữa hai phát biểu:**
- **Đúng:** `sum()` không bảo đảm snapshot nguyên tử khi đang có cập nhật.
- **Sai nếu nói chung:** Mọi kết quả `sum()` trong lúc cập nhật đều là "số rác" hoặc tất cả đều không chính xác.

Với bộ đếm chỉ tăng từ 0, không reset, không overflow, việc đọc giữa chừng thường đủ để vẽ đồ thị số sự kiện tăng dần theo thời gian. Nhưng một biểu đồ cho mục đích quan sát **khác về yêu cầu** với số liệu phải đóng sổ chính xác tại một mốc nghiệp vụ.

## 6. So sánh LongAdder với AtomicLong

| Tiêu chí | `LongAdder` | `AtomicLong` |
|---|---|---|
| Trạng thái cộng dồn | Có thể gồm nhiều thành phần | Một giá trị `long` nguyên tử |
| Cập nhật `+1` | `increment()` | `incrementAndGet()` hoặc `getAndIncrement()` |
| Đọc | `sum()`: không atomic snapshot khi update đồng thời | `get()`: đọc nguyên tử một giá trị |
| Đọc rồi đặt 0 | `sumThenReset()`: không an toàn như một ranh giới nguyên tử khi còn writer | `getAndSet(0)`: thao tác trao đổi nguyên tử trên **một** giá trị |
| Contention tăng cao | Thường giảm tranh chấp cho workload nhiều cập nhật | Có thể tranh chấp trên một biến |
| Tài nguyên | Có thể dùng thêm bộ nhớ cho các ô | Một biến trạng thái chính |
| Phù hợp | Metric, histogram, thống kê cập nhật thường xuyên và đọc để quan sát | Bộ đếm cần quyết định dựa trên giá trị nguyên tử, thứ tự cập nhật, CAS/giới hạn |

`AtomicLong.get()` có tính nguyên tử ở cấp **một giá trị**; điều này không tạo snapshot nguyên tử của **nhiều** `AtomicLong` hoặc các cấu trúc dữ liệu khác. Muốn giữ bất biến giữa nhiều trường, vẫn cần cơ chế phối hợp thích hợp (ví dụ khóa chung hoặc trạng thái bất biến được công bố bằng một tham chiếu nguyên tử).

Không nên coi bảng là khẳng định `LongAdder` lúc nào cũng nhanh hơn. Hiệu năng phụ thuộc số luồng, mức tranh chấp, số lần đọc, CPU, cách sử dụng bộ đếm và JVM. Nếu cần tối ưu, đo workload thực bằng công cụ benchmark thích hợp, không suy từ một vòng lặp nhỏ in ra thời gian.

## 7. Trường hợp dùng LongAdder đúng: metric và thống kê

Một ví dụ phổ biến là **bộ đếm số yêu cầu xử lý**:

~~~java
import java.util.concurrent.atomic.LongAdder;

public class RequestMetrics {
    private final LongAdder processed = new LongAdder();

    public void onProcessed() {
        processed.increment();
    }

    public long reportApproximate() {
        return processed.sum();
    }
}
~~~

Mã này là một lớp dùng lại được; khi nhiều thread gọi `onProcessed()`, thao tác tăng an toàn. `reportApproximate()` dùng trong lúc worker tiếp tục chạy là phép đọc quan sát, có thể không chứa mọi cập nhật đồng thời. Đặt tên/đặc tả API rõ như `estimate`, `observedTotal` hoặc mô tả "giá trị tại lần scrape" để người tiêu thụ không nhầm với số đã đóng sổ.

Dùng trong hệ thống metric có thể chấp nhận chênh lệch quan sát ngắn hạn vì lần thu thập sau sẽ thấy các cập nhật đã hoàn tất (trừ trường hợp ứng dụng reset hoặc overflow). Không suy rộng điều này sang mọi cách thu thập/chia cửa sổ: nếu dùng `sumThenReset()` trong lúc writer cập nhật, không có bảo đảm phân bổ chính xác mỗi sự kiện vào đúng cửa sổ.

Một cách lập histogram có nhiều khóa:

~~~java
ConcurrentHashMap<String, LongAdder> frequencies =
        new ConcurrentHashMap<>();

frequencies.computeIfAbsent("OK", key -> new LongAdder()).increment();
frequencies.computeIfAbsent("ERROR", key -> new LongAdder()).increment();

long ok = frequencies.get("OK").sum();
~~~

Đoạn mã cần `import java.util.concurrent.ConcurrentHashMap;` và `LongAdder`, đặt trong một phương thức. `computeIfAbsent` bảo đảm tạo/công bố giá trị cho khóa theo cơ chế của `ConcurrentHashMap`, còn mỗi `LongAdder` giữ thống kê từng khóa. Nhưng việc đọc **nhiều khóa** thành một báo cáo không tạo ra snapshot nhất quán của toàn bản đồ hoặc của mọi `LongAdder` tại cùng một thời điểm.

## 8. Trường hợp không nên dùng sum() để ra quyết định nghiêm ngặt

### A. Không dùng làm bộ cấp ID duy nhất

~~~java
long next = adder.sum() + 1;
// Không an toàn để cấp ID duy nhất rồi mới gọi increment().
~~~

Hai thread có thể quan sát cùng tổng và tạo cùng ID; ngay cả khi `sum()` có một snapshot lý tưởng, phép **đọc rồi tăng** là hai bước tách rời, nên vẫn xảy ra race. Dùng `AtomicLong.incrementAndGet()` hoặc bộ cấp ID chuyên dụng khi cần một thao tác nguyên tử cấp số kế tiếp.

### B. Không dùng để kiểm soát hạn mức cứng

~~~java
if (adder.sum() < capacity) {
    adder.increment();
    // Không bảo đảm tổng cuối không vượt capacity.
}
~~~

Có hai vấn đề: `sum()` không là snapshot nguyên tử của `LongAdder` và tổ hợp **kiểm tra điều kiện rồi tăng** cũng không được thực hiện như một giao dịch nguyên tử. Nhiều thread có thể đồng thời thấy chỗ trống và cùng tăng.

Với hạn mức dạng một số nguyên duy nhất, dùng một trạng thái `AtomicLong` với vòng CAS, hoặc cơ chế đồng bộ dành riêng cho việc phân bổ tài nguyên:

~~~java
import java.util.concurrent.atomic.AtomicLong;

static boolean reserveOne(AtomicLong used, long capacity) {
    while (true) {
        long current = used.get();
        if (current >= capacity) {
            return false;
        }
        if (used.compareAndSet(current, current + 1)) {
            return true;
        }
    }
}
~~~

Đoạn phương thức ở trên cần đặt trong một lớp Java. Giả định `capacity` là một số không âm hợp lệ và không yêu cầu vượt miền `long`. Nếu CAS thất bại, thread khác đã thay đổi giá trị, nên thử lại từ trạng thái mới. Đây là thao tác **kiểm tra + tăng được tuyến tính hóa trên cùng một biến**, phù hợp hơn `if (sum < capacity) increment`.

### C. Không dùng để đóng sổ tiền hoặc số liệu kiểm toán

Một phép đọc `sum()` khi giao dịch đang chạy không tự bảo đảm toàn bộ nghiệp vụ đã cam kết được tính đúng ở ranh giới chốt. Cần mô hình giao dịch, ledger, nguồn sự kiện, đồng bộ hoặc checkpoint đúng semantics. `LongAdder` chỉ là bộ tích lũy số `long`, không thay thế tính nhất quán nghiệp vụ.

Nếu xử lý giá trị tiền có phần lẻ, càng không nên dùng `DoubleAdder` như cơ chế ghi sổ: kiểu `double` còn có sai số dấu phẩy động và thay đổi kết quả theo thứ tự cộng. Có thể biểu diễn số đơn vị nhỏ nhất dưới dạng `long` khi miền giá trị đủ, hoặc dùng kiểu số thập phân/chế độ lưu trữ phù hợp với hợp đồng hệ thống.

## 9. Muốn tổng cuối chính xác thì làm thế nào?

**Nguyên tắc:** đọc sau khi biết tất cả writer đã kết thúc và không ai còn cập nhật adder đó nữa. Một cách là đợi bằng `Thread.join()` như chương trình ở mục 4. Với tác vụ đưa vào `ExecutorService`, có thể đợi các `Future.get()` hoặc cơ chế completion thích hợp trước khi đọc tổng. Nếu còn writer ngoài nhóm đã đợi, kết quả vẫn là phép quan sát đồng thời.

Điểm quan trọng không nằm ở việc tự chọn một khoảng `Thread.sleep(...)` đủ dài. Ngủ 100 ms hoặc 1 giây **không chứng minh** writer đã xong và cũng không tạo ra snapshot nguyên tử. Cần tín hiệu hoàn thành có ràng buộc rõ ràng.

Một hệ thống luôn tiếp nhận sự kiện có thể không có "thời điểm tất cả writer dừng". Khi cần tổng chính xác cho mốc thời gian `T`, thiết kế phải xác định ranh giới sự kiện và có cơ chế phối hợp để tất cả cập nhật thuộc mốc đó được hoàn tất trước khi kết sổ. Có thể dùng cơ chế khóa/epoch/checkpoint thích hợp hoặc tổng chính xác từ nguồn dữ liệu bền vững. Đổi adder tham chiếu rồi gọi `sum()` trên instance cũ **chưa đủ** nếu những writer vẫn giữ tham chiếu cũ và tiếp tục cập nhật sau khi đổi.

**Đừng nhầm** việc `sum()` trả chính xác khi hệ thống yên lặng với việc `sum()` tự tạo ra sự yên lặng. Reader không chặn writer và không áp đặt một ranh giới cắt dữ liệu.

## 10. reset() và sumThenReset(): rủi ro khi dùng sai

`LongAdder.reset()` xóa giá trị tích lũy. `sumThenReset()` kết hợp đọc và reset về mặt hiệu ứng, nhưng tài liệu API nêu rõ các phương thức này **chỉ hiệu quả chính xác khi không có cập nhật đồng thời**.

Trong trường hợp writer vẫn đang thêm số:
- Một cập nhật có thể rơi vào thời điểm reader đã đọc một phần trạng thái nhưng chưa reset phần đó.
- Số đọc ra và tổng còn lại sau reset có thể không phân chia các cập nhật thành hai tập chính xác theo cùng một ranh giới nguyên tử.
- Một lần `sumThenReset()` không đảm bảo bao trọn mọi cập nhật hoàn tất trước một mốc thời gian nghiệp vụ do bạn tự đặt.

Đoạn sau **an toàn theo giả định không có writer khác**:

~~~java
LongAdder window = new LongAdder();
window.add(8);
window.add(2);

long completedWindow = window.sumThenReset(); // 10
long nextWindow = window.sum();               // 0
~~~

Đoạn này cần `import java.util.concurrent.atomic.LongAdder;` và nằm trong một phương thức Java. Nếu có writer chạy đồng thời, không thể dùng kết quả 10/0 như hợp đồng tổng quát.

Nếu cần cửa sổ metric với độ chính xác ở ranh giới, cần thiết kế một quy trình đóng cửa sổ: ngăn/chuyển tiếp writer theo một giao thức có xác nhận hoàn tất, sau đó đọc tổng phiên cũ. Các phương thức của `LongAdder` không tự cung cấp giao thức này.

## 11. DoubleAdder có cùng giới hạn snapshot và thêm giới hạn số học

`DoubleAdder` hoạt động tương tự nhưng cộng `double`. `DoubleAdder.sum()` cũng **không phải snapshot nguyên tử** khi có cập nhật đồng thời. Ngoài ra, thứ tự các phép cộng trong/giữa thread không được bảo đảm, và `double` không có tính kết hợp tuyệt đối về mặt số học máy tính:

~~~text
(a + b) + c  có thể khác  a + (b + c)
trong phép tính dấu phẩy động vì làm tròn.
~~~

Vì vậy, ngay cả sau khi mọi writer kết thúc, `DoubleAdder.sum()` có thể khác từng bit so với kết quả cộng tuần tự cùng các giá trị theo một thứ tự cố định. Đây là khác biệt **về tính ổn định số học**, tách biệt với vấn đề snapshot đồng thời. Nếu số lượng/biên độ giá trị quá khác nhau hoặc yêu cầu độ chính xác cao, phải chọn thiết kế tính tổng phù hợp.

Ví dụ:

~~~java
import java.util.concurrent.atomic.DoubleAdder;

DoubleAdder total = new DoubleAdder();
total.add(0.1);
total.add(0.2);

double result = total.sum();
System.out.println(result); // 0.30000000000000004 (với IEEE 754 double)
~~~

Đoạn này chỉ minh họa phép cộng số `double` thông thường trong cùng một thread. Không dùng `DoubleAdder` để kỳ vọng số tiền thập phân chính xác tuyệt đối. Với hệ thống cần tính tổng double ổn định hoặc có ràng buộc sai số, xem xét thuật toán cộng bù/sắp xếp dữ liệu và cách phối hợp các luồng, không giả định một bộ tích lũy concurrent tự giải quyết vấn đề.

## 12. Những ngộ nhận và lỗi phổ biến

1. **"LongAdder thread-safe thì sum() phải nguyên tử."** Cập nhật từng phần và đọc đồng thời nhiều phần là hai hợp đồng khác nhau.
2. **"Nếu thử nhiều lần sum() luôn đúng, API bảo đảm đúng."** Lịch chạy có thể không lộ khác biệt trong test; hợp đồng JavaDoc mới quyết định điều gì được bảo đảm.
3. **"volatile bao quanh adder sẽ làm sum nguyên tử."** `volatile` trên tham chiếu không khóa đồng thời quá trình đọc tất cả thành phần bên trong.
4. **"synchronized (adder) { return adder.sum(); } là đủ."** Khóa chỉ bảo vệ thao tác nếu **mọi writer** tuân thủ cùng khóa hoặc một cơ chế phối hợp tương đương. `increment()` mặc định không tự lấy monitor của đối tượng adder.
5. **"sumThenReset() giống AtomicLong.getAndSet(0)."** Khi cập nhật đồng thời, `sumThenReset()` không có ranh giới nguyên tử như `getAndSet` trên một `AtomicLong`.
6. **"Lấy tổng rồi tăng để cấp số thứ tự là an toàn."** Đây là read-modify-write không nguyên tử, bất kể cách cài bộ đếm.
7. **"AtomicLong giải quyết luôn snapshot nhiều chỉ số."** Nó chỉ bảo đảm thao tác trên một giá trị riêng lẻ; nhiều biến vẫn cần một giao thức đồng bộ chung nếu yêu cầu trạng thái nhất quán.
8. **"Có thể dừng writer bằng sleep."** `sleep()` không đợi hoàn thành một cách có ràng buộc; dùng `join()`, `Future.get()`, latch hoặc barrier theo cấu trúc công việc.
9. **"Một LongAdder dùng sumThenReset trong metric theo phút là luôn chính xác từng phút."** Không có bảo đảm ranh giới chính xác nếu các sự kiện vẫn được cập nhật trong lúc reset.
10. **"LongAdder không bao giờ tràn."** Nó cộng theo `long`; tổng vượt miền `long` vẫn có thể overflow/wrap như phép toán số nguyên Java. Chọn miền dữ liệu và cơ chế phát hiện/giới hạn phù hợp khi độ lớn có thể vượt ngưỡng.
11. **"DoubleAdder là phép cộng thập phân chính xác."** Nó dùng `double` và thứ tự cộng không cố định giữa thread; cần tính sai số nếu ứng dụng nhạy cảm với độ chính xác.
12. **"Nhiều LongAdder đọc từng cái rồi ghép sẽ thành ảnh chụp toàn hệ thống."** Các giá trị được đọc ở thời điểm khác nhau; nếu cần invariant liên quan nhiều thống kê, phải phối hợp thêm.

## 13. Chọn kiểu nào cho yêu cầu cụ thể?

| Nhu cầu | Lựa chọn phù hợp | Giải thích |
|---|---|---|
| Đếm số lần truy cập, log event, metric dưới contention cao | `LongAdder` | Cập nhật hiệu quả; chấp nhận `sum()` là quan sát không atomic snapshot |
| Số thứ tự tăng nguyên tử, quota một biến, CAS theo trạng thái hiện tại | `AtomicLong` | Có `get()` và read-modify-write atomic |
| Thống kê nhiều khóa với update liên tục | `ConcurrentHashMap<K, LongAdder>` | Bộ đếm độc lập theo khóa, nhưng ảnh chụp toàn map không tự nguyên tử |
| Tổng số chính xác sau khi toàn bộ tác vụ kết thúc | `LongAdder` + đồng bộ hoàn thành | `sum()` chính xác khi không còn cập nhật, trong miền số hợp lệ |
| Tổng chính xác tại một mốc thời gian trong hệ thống luôn chạy | Giao thức đóng mốc/checkpoint có đồng bộ | `sum()`/`sumThenReset()` đơn lẻ không xác định ranh giới |
| Tổng `double` thống kê chấp nhận sai số | `DoubleAdder` | Chú ý rounding và thứ tự cộng |
| Ghi sổ tiền/thông tin phải kiểm toán | Dữ liệu giao dịch và kiểu số đúng hợp đồng | Một bộ đếm in-memory không thay thế tính nhất quán và độ bền |

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Hai thread liên tục gọi `LongAdder.increment()`. Một thread khác gọi `sum()` trong lúc đó. Có thể coi giá trị là toàn bộ cập nhật đã diễn ra đúng lúc gọi hàm không?

**Đáp án:** Không. `sum()` có thể không bao gồm các cập nhật diễn ra trong khoảng tính tổng; JavaDoc không bảo đảm atomic snapshot trong tình huống này.

**Câu 2.** Sau khi cả hai worker đã `join()` và không thread nào khác cập nhật, mỗi worker đã thực hiện 1.000 lần `increment()`, `sum()` phải bằng bao nhiêu?

**Đáp án:** 2.000, giả sử ban đầu bằng 0, không reset/giảm/tràn số và worker đã hoàn tất thật. Lúc đọc không còn cập nhật đồng thời.

**Câu 3.** `if (adder.sum() < 10) adder.increment();` có giữ tổng không quá 10 dưới nhiều thread không?

**Đáp án:** Không. Đọc tổng và tăng không tạo một hành động kiểm tra-và-cập nhật nguyên tử. Dùng `AtomicLong.compareAndSet` trong vòng lặp hoặc đồng bộ/thuật toán quota phù hợp.

**Câu 4.** `sumThenReset()` có thể dùng để chốt chính xác cửa sổ 1 phút khi writer vẫn cập nhật vào cùng instance không?

**Đáp án:** Không có bảo đảm đó. Cần cơ chế xác định khi nào writer của cửa sổ cũ đã hoàn tất trước khi đọc/reset hoặc thay thiết kế thu thập số liệu.

**Câu 5.** Nếu đổi `LongAdder` sang `AtomicLong`, một lần gọi `get()` có đủ để lấy snapshot nhất quán của cả năm bộ đếm khác nhau không?

**Đáp án:** Không. Một `AtomicLong.get()` đọc nguyên tử **một** giá trị, không tạo giao dịch nguyên tử cho năm biến.

**Câu 6.** Sau khi dừng toàn bộ writer của `DoubleAdder`, `sum()` có chắc bằng kết quả cộng tuần tự trong một thứ tự cố định tới từng bit không?

**Đáp án:** Không. Không còn contention giúp lấy tổng các thành phần ổn định, nhưng dấu phẩy động có thể làm tròn khác nhau khi đổi thứ tự cộng.

## 15. Ghi nhớ và nguồn chuẩn

- **`LongAdder.add()/increment()`:** phục vụ cập nhật đồng thời an toàn, tối ưu cho thống kê dưới contention.
- **`LongAdder.sum()`:** đọc tổng để quan sát; **không phải atomic snapshot khi các writer còn chạy**.
- **Sau khi đồng bộ hoàn tất mọi writer:** `sum()` trả tổng đúng của các cập nhật đã hoàn tất, trong giới hạn số học và nếu không reset.
- **`sumThenReset()`/`reset()`:** chỉ dùng với ranh giới quiescent hoặc giao thức đồng bộ loại trừ cập nhật đồng thời.
- **`AtomicLong.get()/incrementAndGet()/compareAndSet()/getAndSet()`:** phù hợp cho trạng thái `long` đơn có yêu cầu nguyên tử hoặc read-modify-write.
- **`DoubleAdder.sum()`:** cùng giới hạn snapshot và thêm lưu ý về `double` không kết hợp tuyệt đối.

Tài liệu API chính thức để đối chiếu:
- [`LongAdder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html): `sum`, `reset`, `sumThenReset` và mục đích sử dụng trong thống kê đồng thời.
- [`DoubleAdder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/DoubleAdder.html): snapshot đồng thời và thứ tự cộng `double`.
- [`AtomicLong` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html): các thao tác atomic trên một giá trị `long`.
- [`Thread.join` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): đợi một thread kết thúc; các quy tắc happens-before liên quan nằm trong tài liệu `java.util.concurrent` và Java Memory Model.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-numbers-008">BigInteger, BigDecimal bất biến nhưng cập nhật tham chiếu dùng chung vẫn cần phối hợp</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger, BigDecimal bất biến nhưng cập nhật tham chiếu dùng chung vẫn cần phối hợp

## 1. Tình huống: giá trị an toàn, phép cập nhật có thể mất dữ liệu

`java.math.BigInteger` và `java.math.BigDecimal` là các kiểu số **bất biến** (*immutable*): sau khi tạo đối tượng, giá trị và biểu diễn của chính đối tượng đó không bị thay đổi bởi các phép cộng, trừ, nhân, chia. Phép toán tạo **đối tượng kết quả**, rồi trả về tham chiếu đến kết quả.

~~~java
BigInteger before = new BigInteger("100");
BigInteger after = before.add(BigInteger.ONE);

System.out.println(before); // 100
System.out.println(after);  // 101
~~~

Đây là đặc tính rất tốt khi chia sẻ **một đối tượng giá trị** cho nhiều luồng: các luồng có thể cùng đọc và tính toán trên đối tượng đó mà không phải khóa riêng chỉ để bảo vệ nội dung của `BigInteger` hay `BigDecimal`.

Tuy nhiên, có khác biệt quan trọng giữa hai thao tác:

1. **Đọc một số bất biến:** nhiều luồng cùng đọc `BigDecimal price` hoặc `BigInteger constant`, không ai thay đổi nội dung số.
2. **Cập nhật một tham chiếu dùng chung:** nhiều luồng chạy `balance = balance.add(delta)` để đặt tham chiếu `balance` sang một đối tượng mới.

Trường hợp thứ hai chứa chuỗi **đọc → tính → ghi**. Việc tính toán `add()` có thể an toàn, nhưng toàn bộ chuỗi đọc–sửa–ghi không tự động trở thành một hành động nguyên tử (*atomic*).

~~~java
// Đoạn minh họa: biến balance được chia sẻ giữa nhiều luồng.
balance = balance.add(new BigDecimal("1.00"));
~~~

Giả sử cả luồng A và B cùng thấy `balance = 10.00`:

| Thời điểm | Luồng A | Luồng B | Tham chiếu balance |
|---|---|---|---|
| 1 | Đọc đối tượng giá trị `10.00` | | `10.00` |
| 2 | | Đọc đối tượng giá trị `10.00` | `10.00` |
| 3 | Tạo đối tượng mới `11.00` | | `10.00` |
| 4 | | Tạo đối tượng mới `11.00` | `10.00` |
| 5 | Gán balance = `11.00` | | `11.00` |
| 6 | | Gán balance = `11.00` | `11.00` |

Sau hai lần cộng, giá trị cuối vẫn chỉ là **11.00**, trong khi mong đợi **12.00**. Đây là *lost update* (mất cập nhật). Không đối tượng `BigDecimal` nào bị sửa bên trong; lỗi nằm ở cách các luồng phối hợp cập nhật **tham chiếu chung**.

## 2. Phân biệt immutability, visibility và atomicity

Ba thuộc tính này giải quyết ba vấn đề khác nhau:

| Thuộc tính | Câu hỏi | BigInteger/BigDecimal giúp gì? |
|---|---|---|
| **Immutability** (bất biến) | Nội dung của một đối tượng có bị sửa sau khi tạo không? | Có: phép số học không thay đổi toán hạng |
| **Visibility** (khả năng nhìn thấy thay đổi) | Khi luồng A thay tham chiếu, luồng B có nhìn thấy tham chiếu mới đúng theo quy tắc bộ nhớ không? | Bản thân tính bất biến không tạo quan hệ happens-before cho việc thay một trường tham chiếu dùng chung |
| **Atomicity** (nguyên tử) | Phép cập nhật đọc–tính–ghi có diễn ra như một bước duy nhất đối với luồng khác không? | Không: phải phối hợp bằng khóa hoặc thao tác atomic |

Ví dụ:

~~~java
private BigDecimal balance = new BigDecimal("0.00");

// CẢNH BÁO: nếu gọi đồng thời từ nhiều luồng, không có atomicity.
void deposit(BigDecimal delta) {
    balance = balance.add(delta);
}
~~~

Không thể coi `BigDecimal` là "thread-safe" rồi kết luận phương thức `deposit` này cũng thread-safe. Khả năng sử dụng an toàn đối tượng bất biến **không truyền tự động** cho biến hoặc container chứa tham chiếu đến nó.

Tương tự, `BigInteger counter = counter.add(BigInteger.ONE)` không tương đương `AtomicInteger.incrementAndGet()` chỉ vì `BigInteger` bất biến.

### Trường final không có nghĩa trạng thái luôn bất biến

~~~java
final BigDecimal taxRate = new BigDecimal("0.10");
// taxRate = new BigDecimal("0.20"); // Không biên dịch: không thể gán lại biến final.
~~~

Một trường `final BigDecimal` không thể được gán lại qua trường đó sau khi khởi tạo; kết hợp tính bất biến của `BigDecimal`, đây là mô hình tốt để chia sẻ giá trị cố định khi đối tượng được tạo và công bố đúng.

Nhưng:

~~~java
final AtomicReference<BigDecimal> balance =
        new AtomicReference<>(BigDecimal.ZERO);

balance.set(new BigDecimal("1.00")); // Hợp lệ!
~~~

`final` ở đây chỉ giữ cố định **tham chiếu đến đối tượng AtomicReference**, không khóa trạng thái bên trong `AtomicReference`.

## 3. Vì sao volatile chưa giải quyết mất cập nhật?

Có thể khai báo:

~~~java
private volatile BigDecimal balance = BigDecimal.ZERO;
~~~

`volatile` đảm bảo các thao tác đọc/ghi của **trường tham chiếu** có quy tắc visibility/ordering do Java Memory Model quy định; với các thao tác `volatile`, một lần ghi xảy ra trước các lần đọc tiếp sau trong thứ tự đồng bộ thích hợp. Đọc/ghi một tham chiếu riêng lẻ cũng là thao tác nguyên tử ở cấp truy cập tham chiếu.

Nhưng đoạn sau vẫn **không nguyên tử**:

~~~java
balance = balance.add(BigDecimal.ONE);
~~~

Thực chất nó gồm:

~~~java
BigDecimal old = balance;            // volatile read
BigDecimal next = old.add(BigDecimal.ONE); // tính toán cục bộ
balance = next;                      // volatile write
~~~

Hai luồng hoàn toàn có thể đọc cùng `old` rồi cùng ghi một `next` bằng nhau. `volatile` giúp luồng thấy các lần ghi đúng theo memory model; nó **không khóa ba bước lại thành một giao dịch**.

Khi chỉ có **một luồng ghi** và nhiều luồng đọc cấu hình/snapshot bất biến, một trường `volatile` có thể đủ nếu nhu cầu thực sự chỉ là xuất bản giá trị mới và không có bất biến đa trường. Với **nhiều luồng cùng cập nhật dựa trên giá trị cũ**, cần cơ chế phối hợp toàn bộ chuỗi read–modify–write.

## 4. Cách 1: synchronized bao trùm đọc–tính–ghi

Giải pháp rõ ràng nhất cho đối tượng có trạng thái chia sẻ là bảo vệ thao tác cập nhật bằng cùng một monitor:

~~~java
import java.math.BigDecimal;

public final class SynchronizedBalance {
    private BigDecimal balance = new BigDecimal("0.00");

    public synchronized void deposit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public synchronized BigDecimal snapshot() {
        return balance;
    }
}
~~~

`synchronized` trên hai phương thức instance sử dụng khóa monitor của **cùng instance** `SynchronizedBalance`:

- Chỉ một luồng có thể chạy các đoạn được bảo vệ bởi monitor ấy tại một thời điểm.
- Luồng đang gọi `deposit` phải hoàn thành toàn bộ đọc–tính–ghi trước khi luồng khác đi vào một phương thức được bảo vệ bởi cùng khóa.
- Việc mở và đóng monitor thiết lập quan hệ happens-before liên quan, giúp các lần đọc thông qua cùng khóa thấy trạng thái được cập nhật đúng cách.

`snapshot()` trả một `BigDecimal` bất biến. Luồng khác có thể giữ lại đối tượng trả về mà không sợ nó bị sửa bên trong; nhưng đó là **ảnh chụp tại thời điểm đọc**, không tự phản ánh các lần nạp tiền tiếp theo.

Có thể dùng khối `synchronized(lock)` thay vì khóa trên `this`:

~~~java
private final Object lock = new Object();
private BigInteger total = BigInteger.ZERO;

void add(BigInteger delta) {
    synchronized (lock) {
        total = total.add(delta);
    }
}

BigInteger snapshot() {
    synchronized (lock) {
        return total;
    }
}
~~~

Khóa riêng giúp kiểm soát rõ ai có quyền phối hợp. Điều thiết yếu là **mọi đường đọc/ghi tham gia cùng quy tắc đồng bộ**: nếu một phương thức bỏ qua khóa, các bảo đảm nhất quán của lớp có thể bị phá vỡ.

### Khi chọn synchronized?

`synchronized` phù hợp khi có nhiều bất biến phải duy trì cùng lúc: kiểm tra số dư rồi trừ, cộng nhiều trường, hoặc đọc một snapshot gồm các giá trị liên quan. Nó cũng làm mã dễ hiểu khi số thao tác không nhiều.

Ví dụ, chuyển tiền giữa hai số dư được lưu trong **cùng một đối tượng** phải cập nhật cả hai dưới một khóa phù hợp, nếu yêu cầu cả hai thay đổi nguyên tử. Hai `AtomicReference<BigDecimal>` độc lập không tự tạo một giao dịch nguyên tử gồm cả hai tài khoản. Trong hệ thống dùng cơ sở dữ liệu hoặc nhiều tiến trình, khóa Java trong một JVM cũng **không thay thế** transaction/locking của kho dữ liệu chung.

## 5. Cách 2: AtomicReference<BigInteger> và CAS

`AtomicReference<T>` giữ một tham chiếu với các thao tác atomic. Kết hợp với giá trị bất biến `BigInteger` hoặc `BigDecimal`, nó giúp thực hiện cập nhật kiểu copy-on-write:

~~~java
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;

AtomicReference<BigInteger> counter =
        new AtomicReference<>(BigInteger.ZERO);

counter.updateAndGet(old -> old.add(BigInteger.ONE));
BigInteger result = counter.get();
~~~

`updateAndGet` làm cập nhật nguyên tử đối với các luồng cùng sử dụng `AtomicReference` đó. Logic của nó có thể hình dung như một vòng lặp **compare-and-set (CAS)**:

~~~java
BigInteger previous;
BigInteger next;
do {
    previous = counter.get();
    next = previous.add(BigInteger.ONE);
} while (!counter.compareAndSet(previous, next));
~~~

Nếu luồng khác đã đổi tham chiếu giữa `get()` và `compareAndSet()`, CAS thất bại; vòng lặp đọc giá trị mới rồi tính lại, **không ghi đè cập nhật của luồng khác**.

### Phân biệt phương thức AtomicReference

| Phương thức | Ngữ nghĩa |
|---|---|
| `get()` | Đọc tham chiếu hiện tại theo ngữ nghĩa bộ nhớ của atomic variable |
| `set(newValue)` | Ghi tham chiếu mới; **không** tự bảo vệ chuỗi read–modify–write được viết bên ngoài |
| `compareAndSet(expected, update)` | Chỉ đổi nếu tham chiếu hiện tại chính là `expected` |
| `updateAndGet(operator)` | Tính hàm cập nhật và trả **giá trị mới** sau lần cập nhật thành công |
| `getAndUpdate(operator)` | Cập nhật theo hàm rồi trả **giá trị cũ** của lần cập nhật thành công |
| `accumulateAndGet(value, operator)` | Gộp giá trị hiện tại và tham số bằng hàm, trả giá trị mới |
| `getAndAccumulate(value, operator)` | Tương tự nhưng trả giá trị cũ của lần cập nhật thành công |

Một lỗi rất phổ biến là viết:

~~~java
AtomicReference<BigDecimal> balance =
        new AtomicReference<>(new BigDecimal("0.00"));

// SAI khi nhiều luồng cùng cập nhật:
balance.set(balance.get().add(new BigDecimal("1.00")));
~~~

Đoạn trên vẫn là get → add → set; `get()` và `set()` riêng lẻ an toàn **không khiến sự kết hợp trở thành nguyên tử**. Dùng:

~~~java
BigDecimal delta = new BigDecimal("1.00");
balance.updateAndGet(old -> old.add(delta));
~~~

Chỉ những writer cùng tuân thủ nguyên tắc atomic update/CAS mới bảo toàn tính đúng đắn của cập nhật đó. Một writer khác tự ý gọi `set` bằng giá trị cũ có thể phá chính sách cộng dồn của hệ thống.

### CAS so sánh tham chiếu, không so sánh giá trị BigDecimal

`compareAndSet` kiểm tra nhận dạng tham chiếu (`==`), **không gọi `equals()`** và cũng không dùng `compareTo()` của `BigDecimal`:

~~~java
AtomicReference<BigDecimal> ref =
        new AtomicReference<>(new BigDecimal("1.0"));

BigDecimal another = new BigDecimal("1.0");
boolean updated =
        ref.compareAndSet(another, new BigDecimal("2.0"));
// updated == false: another khác đối tượng đang được lưu.
~~~

Dù `another.equals(ref.get())` đúng ở đầu ví dụ, CAS thất bại vì `another` là một instance khác. Với CAS loop, cách đúng là lấy `expected` từ chính `ref.get()` rồi thử đổi. Tránh dựa vào so sánh biểu diễn số để quyết định tính đúng của CAS.

## 6. Rất quan trọng: hàm truyền cho updateAndGet có thể chạy nhiều lần

Ở mức triển khai, `AtomicReference.updateAndGet` có thể gọi hàm cập nhật nhiều lần khi xảy ra tranh chấp. Do đó lambda nên là **hàm không có side effect**, chỉ tính kết quả mới từ đầu vào:

~~~java
AtomicReference<BigDecimal> total =
        new AtomicReference<>(new BigDecimal("0.00"));
BigDecimal price = new BigDecimal("10.25");

// ĐÚNG: hàm tính toán, không thực hiện side effect.
BigDecimal updated = total.updateAndGet(current -> current.add(price));
~~~

Không nên ghi log sự kiện nghiệp vụ, gửi email, gọi API thanh toán, tăng bộ đếm ngoài hoặc ghi database bên trong lambda cập nhật, vì lambda đó **có thể được thử lại**:

~~~java
// KHÔNG NÊN: side effect có thể xảy ra nhiều lần khi CAS bị tranh chấp.
// total.updateAndGet(current -> {
//     externalPaymentService.charge(price);
//     return current.add(price);
// });
~~~

Lưu ý quan trọng: điều này **không có nghĩa** mọi lần gọi `updateAndGet` sẽ thực hiện cập nhật nhiều lần. Một lời gọi thành công cập nhật giá trị một lần, nhưng phần **tính toán lambda** có thể lặp lại nhiều lần. Đó là lý do callback phải không gây side effect và nên dựa duy nhất trên giá trị hiện tại cùng các tham số bất biến.

Trong nghiệp vụ thật, hãy hoàn tất thay đổi trạng thái rồi mới phát sự kiện theo một cơ chế đảm bảo *exactly-once* hoặc idempotency thích hợp. Nếu cập nhật cần có giao dịch với hệ thống bên ngoài, một CAS trên bộ nhớ JVM chưa đủ.

## 7. Ví dụ đầy đủ: tái hiện lost update và sửa bằng AtomicReference, synchronized

Chương trình sau chủ ý dùng hai `CountDownLatch` để **bắt buộc** hai luồng cùng đọc một giá trị trước khi ghi, do đó tái hiện mất cập nhật một cách có kiểm soát, tránh dựa vào thời điểm chạy ngẫu nhiên. Sau đó, chương trình dùng `updateAndGet` cho `BigInteger` và `BigDecimal`, đồng thời dùng `synchronized` cho số dư.

Tạo tệp `ImmutableNumbersConcurrencyDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class ImmutableNumbersConcurrencyDemo {
    private static final BigDecimal DELTA = new BigDecimal("0.10");

    static final class SynchronizedWallet {
        private BigDecimal balance = new BigDecimal("0.00");

        public synchronized void deposit(BigDecimal amount) {
            balance = balance.add(amount);
        }

        public synchronized BigDecimal balance() {
            return balance;
        }
    }

    public static void main(String[] args) throws Exception {
        BigInteger original = new BigInteger("100");
        BigInteger computed = original.add(BigInteger.ONE);
        System.out.println("immutable: " + original + " -> " + computed);

        AtomicReference<BigInteger> shared = new AtomicReference<>(BigInteger.ZERO);
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch mayWrite = new CountDownLatch(1);

        Runnable unsafe = () -> {
            BigInteger old = shared.get();
            bothRead.countDown();
            try {
                mayWrite.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            shared.set(old.add(BigInteger.ONE));
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(unsafe);
            Future<?> second = pool.submit(unsafe);
            bothRead.await();
            mayWrite.countDown();
            first.get();
            second.get();
            System.out.println("lost update: " + shared.get());

            shared.set(BigInteger.ZERO);
            Runnable correctCount = () -> {
                for (int i = 0; i < 1_000; i++) {
                    shared.updateAndGet(old -> old.add(BigInteger.ONE));
                }
            };
            first = pool.submit(correctCount);
            second = pool.submit(correctCount);
            first.get();
            second.get();
            System.out.println("atomic count: " + shared.get());

            SynchronizedWallet wallet = new SynchronizedWallet();
            Runnable lockedDeposit = () -> {
                for (int i = 0; i < 100; i++) {
                    wallet.deposit(DELTA);
                }
            };
            first = pool.submit(lockedDeposit);
            second = pool.submit(lockedDeposit);
            first.get();
            second.get();
            System.out.println("locked balance: " + wallet.balance().toPlainString());

            AtomicReference<BigDecimal> atomicBalance =
                    new AtomicReference<>(new BigDecimal("0.00"));
            Runnable atomicDeposit = () -> {
                for (int i = 0; i < 100; i++) {
                    atomicBalance.updateAndGet(old -> old.add(DELTA));
                }
            };
            first = pool.submit(atomicDeposit);
            second = pool.submit(atomicDeposit);
            first.get();
            second.get();
            System.out.println("atomic balance: " +
                    atomicBalance.get().toPlainString());
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac ImmutableNumbersConcurrencyDemo.java
java ImmutableNumbersConcurrencyDemo
~~~

**Kết quả stdout chính xác:**

~~~text
immutable: 100 -> 101
lost update: 1
atomic count: 2000
locked balance: 20.00
atomic balance: 20.00
~~~

Giải thích từng phần:

1. `original` không đổi từ 100: `add()` tạo kết quả mới 101.
2. Hai luồng đọc `shared = 0` trước khi được phép ghi; cả hai tính `0 + 1` và dùng `set(1)`. Dù `shared` là `AtomicReference`, phép cập nhật **do người viết tách thành get + set** khiến kết quả bằng 1 thay vì 2.
3. Với `updateAndGet(old -> old.add(ONE))`, mỗi luồng tăng 1.000 lần nên tổng đúng bằng 2.000.
4. `SynchronizedWallet.deposit()` dùng chung monitor cho mỗi thao tác cộng `BigDecimal("0.10")`. Hai luồng mỗi luồng 100 lần cho đúng 20.00.
5. `AtomicReference<BigDecimal>.updateAndGet()` tạo số mới từ snapshot hiện tại rồi CAS; cũng cho đúng 20.00.

Kết quả của phần `lost update` được tạo có chủ đích nhờ latch; nó không chứng minh `AtomicReference.set()` có lỗi. Phần sai là sử dụng **hai thao tác atomic riêng lẻ** thay cho một phép update atomic. Ví dụ cũng không cần dùng `Thread.sleep` để hy vọng race xảy ra.

Các phép tính dùng `BigDecimal("0.10")` được tạo từ chuỗi để biểu diễn thập phân chính xác. Không dùng `new BigDecimal(0.1)` cho ví dụ này vì `double 0.1` không biểu diễn chính xác số thập phân một phần mười.

## 8. AtomicReference hay synchronized: nên chọn thế nào?

| Nhu cầu | Cách tiếp cận hợp lý | Giới hạn cần nhớ |
|---|---|---|
| Chia sẻ một hằng số `BigDecimal` giữa nhiều luồng chỉ đọc | Trường `final` / công bố đối tượng đúng | Không có trạng thái cộng dồn cần cập nhật |
| Một writer công bố snapshot số mới, nhiều reader chỉ đọc | `volatile BigDecimal` hoặc `AtomicReference<BigDecimal>` | Chưa giải quyết nhiều writer cùng read–modify–write |
| Nhiều writer tăng một số nguyên lớn | `AtomicReference<BigInteger>.updateAndGet` hoặc khóa | Hàm cập nhật thuần, có thể bị gọi lại; tranh chấp cao có thể tốn CAS |
| Nhiều writer cộng dồn một giá trị thập phân | `AtomicReference<BigDecimal>.updateAndGet` hoặc khóa | Chọn scale/rounding đúng theo nghiệp vụ |
| Cập nhật nhiều trường hoặc kiểm tra rồi thay đổi nhiều giá trị liên quan | `synchronized`/`ReentrantLock` hay snapshot bất biến được đổi nguyên tử | Bảo vệ toàn bộ invariant, mọi đường truy cập theo cùng giao thức |
| Bộ đếm `int`/`long` đơn giản | `AtomicInteger`/`AtomicLong`; có thể xem `LongAdder` cho chỉ số thống kê | Không cần `BigInteger` nếu miền giá trị nhỏ; LongAdder.sum() không là snapshot nguyên tử khi có ghi đồng thời |
| Giao dịch trải nhiều JVM hoặc database | Transaction/locking/conditional update phù hợp kho dữ liệu | Khóa và atomic ở một JVM không đảm bảo đồng bộ liên tiến trình |

Không có lớp `AtomicBigDecimal` hay `AtomicBigInteger` chuẩn trong `java.util.concurrent.atomic`. `AtomicReference<T>` hỗ trợ làm việc với các loại đối tượng bất biến như vậy.

`AtomicReference` không có khóa truyền thống nhưng không đồng nghĩa luôn nhanh hơn `synchronized`. Càng nhiều luồng tranh chấp, CAS có thể thất bại và phải tính lại `BigDecimal`/`BigInteger` nhiều lần, phát sinh thêm đối tượng. Với phép cập nhật phức tạp hoặc invariant nhiều trường, khóa có thể đơn giản và hiệu quả hơn. Chọn cơ chế theo tính đúng đắn trước, sau đó benchmark bằng workload thực.

## 9. Khi cập nhật phụ thuộc điều kiện: kiểm tra và thay đổi phải cùng một bước

Không chỉ phép cộng mới gặp race. Ví dụ rút tiền:

~~~java
// SAI: get và set tách rời; hai luồng có thể đều vượt qua điều kiện.
if (balance.get().compareTo(withdrawal) >= 0) {
    balance.set(balance.get().subtract(withdrawal));
}
~~~

Đối với `AtomicReference<BigDecimal>`, kiểm tra và thay đổi có thể đặt chung trong một phép cập nhật có điều kiện, ví dụ:

~~~java
BigDecimal withdrawal = new BigDecimal("7.00");
AtomicReference<BigDecimal> balance =
        new AtomicReference<>(new BigDecimal("10.00"));

BigDecimal after = balance.updateAndGet(current -> {
    if (current.compareTo(withdrawal) < 0) {
        throw new IllegalStateException("Insufficient funds");
    }
    return current.subtract(withdrawal);
});
~~~

Ở đây điều kiện được **đánh giá lại trên giá trị hiện tại** mỗi lần lambda bị chạy lại do CAS không thành công. Nếu trong lần thử mới tiền đã không đủ, thao tác sẽ ném lỗi thay vì ghi đè trạng thái của người khác.

Tuy nhiên, ví dụ trên chỉ cho thấy một chính sách đơn giản của **một biến số dư trong bộ nhớ**. Nghiệp vụ thật còn cần kiểm tra số âm, đơn vị tiền tệ, tính hợp lệ của `withdrawal`, giới hạn, audit, idempotency, persistence và xử lý chuyển tiền giữa các tài khoản. Khi một giao dịch yêu cầu cập nhật đồng thời nhiều tài khoản, CAS trên riêng từng số dư không đủ để bảo đảm tính nguyên tử của toàn giao dịch.

Có thể đưa nhiều trường cần nhất quán vào một **snapshot bất biến**, rồi dùng một `AtomicReference` chứa toàn bộ snapshot để thay thế nguyên tử:

~~~java
record LedgerSnapshot(BigDecimal balance, BigInteger operations) {}

AtomicReference<LedgerSnapshot> state = new AtomicReference<>(
        new LedgerSnapshot(new BigDecimal("0.00"), BigInteger.ZERO));

BigDecimal delta = new BigDecimal("1.00");

state.updateAndGet(old -> new LedgerSnapshot(
        old.balance().add(delta),
        old.operations().add(BigInteger.ONE)
));
~~~

Các reader dùng một lần `state.get()` sẽ thấy một snapshot chứa **cả hai trường thuộc cùng một phiên bản**. Nếu lấy `get()` hai lần cách nhau bởi update thì có thể đọc hai phiên bản khác nhau; nên giữ snapshot vào một biến cục bộ khi cần một trạng thái nhất quán. `record` giúp truyền dữ liệu theo dạng bất biến nếu mọi thành phần bên trong cũng bất biến, như hai kiểu số trong ví dụ.

## 10. Phân biệt snapshot cũ với "đối tượng bị thay đổi"

Một reader có thể giữ tham chiếu cũ:

~~~java
AtomicReference<BigDecimal> price =
        new AtomicReference<>(new BigDecimal("12.00"));

BigDecimal snapshot = price.get(); // 12.00
price.set(new BigDecimal("15.00"));

System.out.println(snapshot);   // 12.00
System.out.println(price.get()); // 15.00
~~~

Cả hai giá trị hợp lệ. `snapshot` không được cập nhật vì `BigDecimal` bất biến; `price` chỉ được đổi để trỏ đến một số mới. Nếu ứng dụng muốn reader **luôn lấy giá trị mới nhất**, nó phải truy cập lại holder đúng thời điểm. Nếu muốn tính một chuỗi nghiệp vụ dựa trên **cùng một phiên bản**, hãy giữ snapshot cục bộ và chỉ đọc một lần tại đầu công đoạn, hoặc dùng giao thức cập nhật nhất quán.

Không nhầm lẫn hai đặc tính:

- Bảo đảm không bị sửa nội dung của `snapshot`.
- Bảo đảm `snapshot` là giá trị mới nhất hoặc phù hợp với các trường khác.

Tính bất biến chỉ giải quyết đặc tính thứ nhất.

## 11. An toàn khi dùng BigDecimal trong nhiều luồng không đồng nghĩa tự động đúng nghiệp vụ

Với `BigDecimal`, còn có yêu cầu về **precision, scale, rounding** độc lập với synchronization:

~~~java
BigDecimal a = new BigDecimal("0.10");
BigDecimal b = new BigDecimal("0.20");
BigDecimal total = a.add(b); // chính xác, giá trị 0.30
~~~

Nếu dùng `divide` với phép chia thập phân vô hạn, Java cần một rounding mode/`MathContext` thích hợp. Cơ chế `AtomicReference` hoặc `synchronized` không sửa lỗi dùng sai precision hoặc rounding. Ngược lại, tạo số tiền từ `new BigDecimal(0.1)` có thể mang sai lệch do đầu vào `double`, dù tất cả cập nhật đều được khóa đúng.

Ví dụ cập nhật được làm tròn sau mỗi lần cộng:

~~~java
BigDecimal delta = new BigDecimal("0.005");
AtomicReference<BigDecimal> total =
        new AtomicReference<>(new BigDecimal("0.00"));

total.updateAndGet(current ->
        current.add(delta).setScale(2, java.math.RoundingMode.HALF_UP));
~~~

Đây là một quy tắc **làm tròn mỗi giao dịch** có chủ đích. Nếu nghiệp vụ muốn cộng mọi giá trị chính xác rồi làm tròn **một lần cuối**, kết quả có thể khác. Trước khi viết callback atomic, phải chọn điểm làm tròn đúng; concurrency chỉ bảo đảm việc áp dụng chính sách nhất quán, không tự chọn chính sách cho bạn.

Khi lambda update chạy lại do cạnh tranh, nó cũng không nên đọc dữ liệu thay đổi độc lập từ bên ngoài. Hãy chuẩn hóa đầu vào (`delta`, đơn vị, điều kiện) thành biến cục bộ bất biến trước khi vào lambda, hoặc thiết kế snapshot chứa các dữ liệu cần đọc nguyên tử cùng nhau.

## 12. Kiểm thử lỗi đồng thời

Với cập nhật tham chiếu dùng chung, một bài test chỉ chạy một luồng hầu như không thể phát hiện lost update. Cách kiểm tra hữu ích:

1. **Test có điều phối:** dùng `CountDownLatch` hoặc cơ chế phối hợp để buộc hai luồng đọc cùng snapshot, sau đó thử ghi; chương trình ở mục 7 thực hiện điều này.
2. **Test kết quả cuối:** hai luồng mỗi luồng tăng 1.000 lần phải cho đúng 2.000; với `BigDecimal("0.10")` hai luồng mỗi luồng cộng 100 lần phải cho đúng `20.00` theo scale đã chọn.
3. **Test thông điệp thất bại và ngoại lệ:** tác vụ trong `ExecutorService.submit` có thể ném exception; gọi `Future.get()` để nhận lỗi thay vì kết luận thành công chỉ vì thread pool đã nhận tác vụ.
4. **Test xử lý shutdown và interruption:** chờ tác vụ hoàn tất và giải phóng pool. Với code sản xuất, đặt timeout phù hợp và xử lý gián đoạn theo thiết kế.
5. **Kiểm tra mọi đường cập nhật:** nếu có cả `updateAndGet` và một đường `get + set` không được bảo vệ, hệ thống vẫn có thể mất dữ liệu.

Test lặp lại nhiều lần với nhiều thread có thể tăng khả năng bộc lộ race, nhưng **không chứng minh tuyệt đối** hệ thống không có lỗi đồng thời. Cần dựa thêm vào lập luận về atomicity, happens-before và giao thức truy cập. Để kiểm tra hiệu năng, đo bằng benchmark phù hợp thay vì coi thời gian một lần chạy test đồng thời là kết luận.

## 13. Các lỗi phổ biến cần tránh

| Lỗi | Vì sao sai | Cách giải quyết |
|---|---|---|
| "BigDecimal immutable nên balance += delta chắc chắn thread-safe" | Việc thay tham chiếu là đọc–tính–ghi, có thể mất cập nhật | Khóa hoặc atomic update |
| Dùng `volatile` rồi kết luận phép cộng atomic | `volatile` cung cấp visibility/ordering chứ không gộp ba bước | `synchronized` hoặc `AtomicReference.updateAndGet` |
| Gọi `ref.set(ref.get().add(x))` | Hai thao tác atomic rời rạc không tạo cập nhật nguyên tử | `updateAndGet(old -> old.add(x))` |
| Gọi dịch vụ ngoài trong lambda CAS | Callback có thể chạy lại khi cạnh tranh | Lambda thuần; side effect sau theo giao thức đáng tin |
| Dùng `compareAndSet` với đối tượng mới có giá trị bằng nhau | CAS kiểm tra cùng tham chiếu | Lấy expected từ `get()` |
| Bảo vệ writer bằng lock nhưng reader bỏ qua lock | Reader có thể nhìn trạng thái không theo giao thức đã định | Đồng bộ cả đường đọc/ghi hoặc công bố snapshot bằng volatile/atomic đúng cách |
| Sử dụng hai atomic field và kỳ vọng snapshot cả hai nhất quán | Hai biến độc lập không thay thế nguyên tử cùng lúc | Khóa hoặc một immutable snapshot trong một AtomicReference |
| Tưởng trường `final AtomicReference` không thể thay số | Chỉ tham chiếu đến holder là final | Phân biệt tính bất biến của field và trạng thái holder |
| Tin rằng `AtomicReference` làm phép toán rẻ hơn | Tranh chấp CAS có thể tính lại và tạo nhiều số mới | Đo, so với khóa |
| Dùng `double`/làm tròn sai cho số tiền | Đúng concurrency vẫn có thể sai số học | Dùng BigDecimal từ chuỗi, chọn scale/rounding chuẩn |
| Test concurrency không đợi Future kết thúc | Có thể bỏ qua lỗi của task | Gọi `Future.get()` và kiểm tra giá trị cuối |
| Coi synchronized cục bộ thay thế database transaction | Chỉ phối hợp các luồng theo khóa trong một JVM | Dùng giao dịch/khóa phù hợp hệ thống lưu trữ |

## 14. Tự kiểm tra

**Câu 1.** Hai luồng cùng gọi `shared = shared.add(BigInteger.ONE)` với `shared` là một trường `BigInteger`. Có chắc cộng được hai lần không?

**Đáp án:** Không. Mỗi `add()` tạo kết quả bất biến riêng, nhưng hai luồng có thể đọc cùng tham chiếu cũ và một update bị ghi đè. Cần bảo vệ phép read–modify–write.

**Câu 2.** Nếu đổi trường đó thành `volatile BigInteger shared`, đã đủ chưa?

**Đáp án:** Chưa. `volatile` bảo đảm visibility theo Java Memory Model, không làm `shared = shared.add(ONE)` trở thành một phép atomic.

**Câu 3.** Với `AtomicReference<BigDecimal> ref`, mã `ref.set(ref.get().add(amount))` có giải quyết mất cập nhật không?

**Đáp án:** Không; dùng `ref.updateAndGet(old -> old.add(amount))` hoặc khóa phù hợp.

**Câu 4.** Vì sao không gọi API thanh toán trong lambda của `updateAndGet`?

**Đáp án:** Hàm cập nhật có thể được gọi lại khi CAS không thành công; side effect có thể xảy ra nhiều lần dù cập nhật state chỉ thành công một lần.

**Câu 5.** Một snapshot gồm `balance` và `count` phải luôn tương ứng cùng phiên bản. Có nên dùng hai `AtomicReference` tách biệt không?

**Đáp án:** Hai atomic riêng không bảo đảm một lần đọc sẽ thu được cặp giá trị cùng phiên bản. Có thể dùng một đối tượng snapshot bất biến trong một `AtomicReference`, hoặc khóa bảo vệ toàn bộ invariant.

**Câu 6.** Luồng A giữ một `BigDecimal snapshot`, luồng B đổi holder từ `12.00` sang `15.00`. Giá trị trong snapshot cũ có đổi thành `15.00` không?

**Đáp án:** Không. Đối tượng `BigDecimal` cũ vẫn bất biến. Chỉ tham chiếu trong holder được thay bằng đối tượng mới.

**Câu 7.** CAS `compareAndSet(expected, update)` dùng `BigDecimal.compareTo()` để xác định expected phải không?

**Đáp án:** Không. Nó so sánh **nhận dạng tham chiếu**; hai `BigDecimal` khác instance dù có cùng giá trị vẫn không phải cùng expected reference.

---

**Ghi nhớ:** `BigInteger` và `BigDecimal` an toàn để chia sẻ dưới dạng **giá trị bất biến**. Nhưng cập nhật **biến tham chiếu dùng chung** vẫn cần điều phối các bước đọc–tính–ghi. Dùng `synchronized`/khóa khi bảo vệ invariant hoặc nhiều trường; dùng `AtomicReference.updateAndGet` khi trạng thái cần cập nhật được biểu diễn trong một snapshot bất biến và hàm cập nhật thuần. Luôn tách bạch tính đúng đắn đồng thời với quy tắc precision, scale và rounding của phép tính.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-numbers-009">Atomic không có nghĩa tự phát hiện tràn số</a>

<details>
<summary>Xem chi tiết</summary>

# Atomic không có nghĩa tự phát hiện tràn số trong Java

## 1. Vấn đề: thao tác nguyên tử vẫn có thể trả về giá trị sai về mặt nghiệp vụ

Trong môi trường nhiều luồng, chúng ta dùng `AtomicInteger` hoặc `AtomicLong` để cập nhật bộ đếm mà không bị mất lượt tăng do các phép đọc–sửa–ghi xen kẽ. Nhưng **atomic (nguyên tử) chỉ mô tả cách các luồng quan sát một thao tác cập nhật; nó không đảm bảo phép toán số học có kiểm tra tràn**.

Ví dụ, bộ đếm `AtomicInteger` ở giá trị lớn nhất:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger counter = new AtomicInteger(Integer.MAX_VALUE);
System.out.println(counter.incrementAndGet()); // -2147483648
~~~

Không có `ArithmeticException` nào được tự động ném. Giá trị `int` vượt giới hạn đã **quấn vòng** (*wrap around*) về `Integer.MIN_VALUE`, và thao tác tăng vẫn được thực hiện theo cơ chế nguyên tử. Một bộ đếm số yêu cầu không âm có thể bất ngờ thành số âm mặc dù hoàn toàn không gặp *lost update*.

Sau bài này, bạn cần phân biệt ba yêu cầu:

1. **Atomicity**: không để hai luồng giẫm lên cùng một cập nhật.
2. **Overflow detection**: phát hiện kết quả không biểu diễn được trong kiểu `int`/`long`.
3. **Business policy**: sau khi phát hiện tràn, hệ thống phải báo lỗi, từ chối cập nhật, bão hòa tại giới hạn hay chuyển sang miền số lớn hơn.

Một cấu trúc `Atomic*` mặc định chỉ giải quyết yêu cầu thứ nhất. Hai yêu cầu còn lại là trách nhiệm thiết kế của ứng dụng.

## 2. Cơ chế tràn số nguyên của Java

Kiểu nguyên có dấu trong Java có giới hạn cố định:

| Kiểu | Độ rộng | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | ---: | ---: | ---: |
| `int` | 32 bit | `-2_147_483_648` | `2_147_483_647` |
| `long` | 64 bit | `-9_223_372_036_854_775_808` | `9_223_372_036_854_775_807` |

Với phép cộng/trừ số nguyên thông thường, khi kết quả toán học nằm ngoài miền biểu diễn, phần bit kết quả được giữ theo phép toán số nguyên Java và diễn giải lại theo kiểu có dấu. Ví dụ:

~~~java
int x = Integer.MAX_VALUE;
System.out.println(x + 1); // -2147483648

long y = Long.MIN_VALUE;
System.out.println(y - 1L); // 9223372036854775807
~~~

Đây là **kết quả quy định của ngôn ngữ**, không phải hành vi bất định và không phải ngoại lệ tự động.

Các method atomic dùng cùng miền giá trị và không làm cho số nguyên trở thành vô hạn:

~~~java
AtomicInteger a = new AtomicInteger(Integer.MAX_VALUE);
int old = a.getAndIncrement();  // old = 2147483647
int now = a.get();              // now = -2147483648
~~~

Điểm dễ gây nhầm: `getAndIncrement()` trả **giá trị trước khi tăng**, nên lần gọi làm tràn có thể trả về một số trông vẫn hợp lệ trong khi **trạng thái đã bị thay đổi thành số âm**. Ngược lại, `incrementAndGet()` trả ngay giá trị mới đã tràn.

Tương tự, `AtomicLong.addAndGet(1L)`, `getAndAdd(...)`, `decrementAndGet()` và các biến thể khác vẫn có thể làm tràn tại biên của `long` nếu sử dụng phép toán số nguyên thông thường.

## 3. Atomicity bảo đảm điều gì, và không bảo đảm điều gì?

Đối với `AtomicInteger` hoặc `AtomicLong`, các phương thức cập nhật nguyên tử bảo đảm mỗi cập nhật quan sát được theo cơ chế nhất quán của biến atomic: một phép `incrementAndGet` không bị tách thành thao tác đọc và ghi độc lập mà luồng khác có thể xen vào làm mất một increment.

Nếu 4 luồng, mỗi luồng gọi `incrementAndGet()` 10.000 lần trên cùng một bộ đếm khởi đầu ở 0, tổng cuối cùng là 40.000 **nếu không có ngoại lệ hoặc tràn** và tất cả cập nhật hoàn thành.

Tuy nhiên, atomicity **không cung cấp**:

- Tự phát hiện tràn `int`/`long`.
- Tự chứng minh giá trị luôn không âm hoặc luôn nằm trong một miền nghiệp vụ.
- Tự chặn việc ai đó sử dụng `set(...)` để gán giá trị ngoài chính sách.
- Một giao dịch nguyên tử xuyên nhiều biến atomic độc lập, nhiều bảng database hoặc nhiều service.
- Bất kỳ bảo đảm nào rằng giá trị đọc được giữa các lần cập nhật vẫn là snapshot bất biến.

Nói cách khác, `AtomicInteger` có thể thực hiện một cách hoàn hảo phép cộng 1 theo kiểu `int`, nhưng **kết quả kiểu `int`** có thể khác kết quả số nguyên toán học bạn mong muốn.

## 4. Cách phát hiện tràn: `Math.addExact` và các hàm *Exact*

Java cung cấp các hàm kiểm tra tràn trong `java.lang.Math`:

| Hàm | Mục đích | Khi ngoài miền |
| --- | --- | --- |
| `Math.addExact(a, b)` | Cộng `int` hoặc `long` có kiểm tra | `ArithmeticException` |
| `Math.subtractExact(a, b)` | Trừ có kiểm tra | `ArithmeticException` |
| `Math.multiplyExact(a, b)` | Nhân có kiểm tra | `ArithmeticException` |
| `Math.incrementExact(x)` | Tăng 1 có kiểm tra | `ArithmeticException` |
| `Math.decrementExact(x)` | Giảm 1 có kiểm tra | `ArithmeticException` |
| `Math.negateExact(x)` | Đổi dấu có kiểm tra | `ArithmeticException` |
| `Math.toIntExact(longValue)` | Chuyển `long` sang `int` có kiểm tra miền | `ArithmeticException` |

Ví dụ:

~~~java
System.out.println(Math.addExact(10, 20)); // 30
System.out.println(Math.incrementExact(7)); // 8

try {
    Math.addExact(Integer.MAX_VALUE, 1);
} catch (ArithmeticException e) {
    System.out.println("overflow detected");
}
~~~

Stdout:

~~~text
30
8
overflow detected
~~~

`Math.addExact` giúp phát hiện tràn **bên trong một phép toán**, nhưng chỉ gọi `Math.addExact` ở ngoài biến atomic chưa tạo ra cập nhật đồng thời an toàn.

Sai lầm phổ biến:

~~~java
AtomicInteger counter = new AtomicInteger(0);

// SAI trong môi trường nhiều luồng:
int proposed = Math.addExact(counter.get(), 1);
counter.set(proposed);
~~~

Hai luồng có thể cùng đọc 0, cùng tính 1, rồi cùng set 1. Không có overflow, nhưng vẫn bị **lost update**. Cần kết hợp phép tính Exact và một **cập nhật nguyên tử**.

## 5. Kết hợp kiểm tra tràn và cập nhật nguyên tử bằng `updateAndGet`

Cách ngắn gọn cho `AtomicInteger` là:

~~~java
AtomicInteger counter = new AtomicInteger(0);
int next = counter.updateAndGet(current -> Math.addExact(current, 1));
~~~

Phương thức `updateAndGet(IntUnaryOperator)` đọc trạng thái, áp dụng hàm và cập nhật bằng một cơ chế nguyên tử; khi có tranh chấp, hàm có thể bị gọi lại với giá trị mới. Kết quả trả về là giá trị đã cập nhật thành công.

Ví dụ cộng thêm một bước `delta`:

~~~java
static int checkedAdd(AtomicInteger counter, int delta) {
    return counter.updateAndGet(current -> Math.addExact(current, delta));
}
~~~

Với `AtomicLong`:

~~~java
static long checkedAdd(AtomicLong counter, long delta) {
    return counter.updateAndGet(current -> Math.addExact(current, delta));
}
~~~

Hai đoạn trên là các **phương thức minh họa**, cần đặt vào lớp Java với imports tương ứng. Một ví dụ chương trình chạy độc lập có đầy đủ imports và main nằm ở mục 8.

### Điều gì xảy ra khi tính toán ném ngoại lệ?

Nếu `Math.addExact(current, delta)` phát hiện tràn, nó ném `ArithmeticException` trước khi lần thử đó thực hiện bước cập nhật atomic thành công. Vì vậy, **lần gọi bị lỗi không áp dụng giá trị bị tràn**.

Tuy nhiên, trong chương trình nhiều luồng, các luồng **khác** vẫn có thể cập nhật bộ đếm trước hoặc sau khi phương thức ném lỗi. Không được diễn giải ngoại lệ thành khẳng định rằng “giá trị hiện tại của biến vẫn đúng bằng giá trị tôi vừa đọc” trong mọi hoàn cảnh.

Khi code khác vẫn được phép gọi `incrementAndGet()`, `set(...)` hoặc `getAndAdd(...)` trực tiếp lên cùng bộ đếm, bảo đảm “luôn phát hiện tràn” cũng bị phá vỡ. Hãy bọc counter trong một API duy nhất áp dụng chính sách kiểm tra, không công khai mutable reference cho caller tùy ý ghi.

### Vì sao callback phải thuần?

`updateAndGet` và `getAndUpdate` **có thể gọi lại hàm cập nhật** khi có luồng khác sửa giá trị giữa bước đọc và so sánh/ghi (CAS). Do đó:

~~~java
// KHÔNG nên: side effect có thể xảy ra nhiều lần khi CAS phải retry.
counter.updateAndGet(current -> {
    sendNotification(); // Không được đặt tác vụ gửi bên trong callback.
    return Math.addExact(current, 1);
});
~~~

Thông báo, ghi log nghiệp vụ có chi phí, chuyển tiền, phát sự kiện hoặc ghi database không được thực hiện như side effect không idempotent bên trong hàm update. Hàm nên chỉ biến đổi giá trị đầu vào thành giá trị mới theo cách xác định; thực hiện tác vụ phụ ở tầng khác theo hợp đồng xử lý thành công và lỗi. Chú ý ngay cả tác vụ sau khi update thành công cũng không tự trở thành một transaction nguyên tử xuyên atomic counter và hệ thống ngoài.

## 6. Vì sao kiểm tra trước bằng `get()` không đủ?

Đoạn sau trông hợp lý nhưng bị race:

~~~java
// SAI: kiểm tra và increment là hai thao tác riêng biệt.
if (counter.get() < Integer.MAX_VALUE) {
    counter.incrementAndGet();
}
~~~

Giả sử giá trị lúc đầu bằng `Integer.MAX_VALUE - 1`:

1. Luồng A đọc và xác nhận nhỏ hơn `Integer.MAX_VALUE`.
2. Luồng B cũng đọc và xác nhận nhỏ hơn `Integer.MAX_VALUE`.
3. A tăng bộ đếm đến `Integer.MAX_VALUE`.
4. B tăng tiếp: tràn về `Integer.MIN_VALUE`.

Cả hai phép `get` và `incrementAndGet` riêng lẻ đều atomic. Nhưng **cặp kiểm tra–cập nhật không phải một thao tác nguyên tử**.

Thay bằng:

~~~java
counter.updateAndGet(current -> Math.incrementExact(current));
~~~

Nếu hai luồng cùng cập nhật gần biên, một trong các lần thử có thể thành công đưa giá trị tới `Integer.MAX_VALUE`, còn lần thử cập nhật vượt biên bị từ chối bằng ngoại lệ. Điều kiện kiểm tra và kết quả số học được gắn vào cùng giá trị dùng cho cập nhật nguyên tử thành công.

Một cách khác khi cần kiểm soát hành vi đặc biệt là vòng lặp `compareAndSet`:

~~~java
static int incrementChecked(AtomicInteger counter) {
    while (true) {
        int current = counter.get();
        int next = Math.incrementExact(current);
        if (counter.compareAndSet(current, next)) {
            return next;
        }
    }
}
~~~

Khi CAS thất bại, vòng lặp đọc lại và tính lại dựa trên trạng thái mới. `compareAndSet` chỉ ghi nếu giá trị hiện hành vẫn bằng giá trị được mong đợi. Đây là khuôn mẫu cơ bản để gắn kiểm tra điều kiện vào cập nhật một biến atomic; cần chú ý tiến trình có thể lặp nhiều lần nếu tranh chấp cao.

**Quan trọng:** đoạn CAS trên kiểm tra tràn theo **giá trị quan sát được ở mỗi lần thử**. Nó không kết hợp nguyên tử với một bất biến nằm ở biến khác hoặc database.

## 7. Chọn chính sách khi tiến sát giới hạn

Chỉ phát hiện tràn chưa quyết định ứng dụng sẽ làm gì. Hợp đồng phải chỉ rõ một trong các hành vi thích hợp:

### A. Từ chối cập nhật và báo lỗi

Dùng `Math.addExact` trong `updateAndGet`, để ngoại lệ được chuyển thành lỗi nghiệp vụ hoặc kết quả rõ ràng ở ranh giới API.

- Phù hợp với bộ đếm không được mất thông tin.
- Giúp giám sát phát hiện sự cố cấu hình hoặc ước lượng kích thước sai.
- Cần xử lý retry: nếu client tự gửi lại cùng yêu cầu, đừng để tạo hiệu ứng trùng lặp ở nơi khác.

### B. Bão hòa tại ngưỡng (*saturating counter*)

Nếu hợp đồng cho phép “tối đa là MAX”, có thể dùng:

~~~java
static int saturatingIncrement(AtomicInteger counter) {
    return counter.updateAndGet(current ->
            current == Integer.MAX_VALUE ? Integer.MAX_VALUE : current + 1);
}
~~~

Đây là chính sách **bão hòa**, không phải “tăng chính xác số lần gọi”. Khi đã đạt MAX, các lần tăng tiếp theo bị mất về mặt đếm. Với trường hợp chỉ cộng số không âm và counter luôn trong miền đã xác thực, quy tắc trên dễ hiểu; với `delta` dương/âm tổng quát, phải định nghĩa cả biên dưới lẫn biên trên và tránh overflow ngay trong phép so sánh ngưỡng.

### C. Chuyển sang miền biểu diễn lớn hơn

- `AtomicLong` có miền lớn hơn `AtomicInteger`, nhưng **vẫn tràn**.
- `BigInteger` có precision tùy ý, có thể kết hợp `AtomicReference<BigInteger>` và cập nhật bằng `updateAndGet`.
- `BigDecimal` cần dùng khi có phần thập phân chính xác; chọn kiểu số và cơ chế đồng thời dựa trên miền thực tế.

Ví dụ dùng số nguyên lớn:

~~~java
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;

AtomicReference<BigInteger> counter =
        new AtomicReference<>(BigInteger.valueOf(Long.MAX_VALUE));

BigInteger next = counter.updateAndGet(
        current -> current.add(BigInteger.ONE)
);

System.out.println(next); // 9223372036854775808
~~~

`BigInteger` không bị giới hạn 64 bit như `long`, nhưng kích thước số và chi phí cấp phát/tính toán tăng theo độ lớn. `AtomicReference<BigInteger>` tạo/cập nhật object mới, có thể chịu chi phí CAS retry khi nhiều luồng tranh chấp; điều này khác bản chất với một atomic primitive đơn giản.

### D. Phân đoạn số đo hoặc thay đổi mô hình

Với tổng số sự kiện trong hàng thập kỷ, một bộ đếm duy nhất có thể không phải mô hình tốt. Có thể lưu các bộ đếm theo khoảng thời gian hoặc partition rồi tổng hợp bằng kiểu số đủ lớn. Tuy nhiên, phân đoạn không tự giải quyết tràn nếu sau đó cộng dồn bằng kiểu `long` quá nhỏ.

Không nên âm thầm chọn chính sách wrap-around nếu người sử dụng hiểu counter là một tổng luôn tăng, không âm và chính xác.

## 8. Chương trình Java 17+ chạy độc lập

Tạo tệp `AtomicOverflowDemo.java`:

~~~java
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicOverflowDemo {

    static int checkedAdd(AtomicInteger counter, int delta) {
        return counter.updateAndGet(value -> Math.addExact(value, delta));
    }

    static long checkedAdd(AtomicLong counter, long delta) {
        return counter.updateAndGet(value -> Math.addExact(value, delta));
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger ordinaryInt = new AtomicInteger(Integer.MAX_VALUE);
        System.out.println("wrapped int: " + ordinaryInt.incrementAndGet());

        AtomicLong ordinaryLong = new AtomicLong(Long.MAX_VALUE);
        System.out.println("wrapped long: " + ordinaryLong.addAndGet(1L));

        AtomicInteger checked = new AtomicInteger(Integer.MAX_VALUE);
        try {
            checkedAdd(checked, 1);
        } catch (ArithmeticException ex) {
            System.out.println("checked overflow caught");
        }
        System.out.println("checked after failure: " + checked.get());
        System.out.println("checked decrement: " + checkedAdd(checked, -1));

        AtomicInteger concurrent = new AtomicInteger(0);
        Thread[] threads = new Thread[4];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10_000; j++) {
                    checkedAdd(concurrent, 1);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
        System.out.println("concurrent count: " + concurrent.get());

        AtomicReference<BigInteger> unbounded = new AtomicReference<>(
                BigInteger.valueOf(Long.MAX_VALUE)
        );
        BigInteger next = unbounded.updateAndGet(
                value -> value.add(BigInteger.ONE)
        );
        System.out.println("beyond long: " + next);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AtomicOverflowDemo.java
java AtomicOverflowDemo
~~~

Stdout mong đợi:

~~~text
wrapped int: -2147483648
wrapped long: -9223372036854775808
checked overflow caught
checked after failure: 2147483647
checked decrement: 2147483646
concurrent count: 40000
beyond long: 9223372036854775808
~~~

Giải thích kết quả:

1. Hai lệnh đầu chứng minh phép cộng atomic vẫn tràn theo giới hạn kiểu nguyên.
2. `checkedAdd(checked, 1)` ở ngưỡng MAX bị `Math.addExact` từ chối trước khi ghi giá trị mới, nên khi **không có luồng khác can thiệp**, `checked.get()` vẫn ở MAX.
3. Cộng `-1` hợp lệ và được thực hiện nguyên tử, đưa counter về `2147483646`.
4. Bốn luồng, mỗi luồng cập nhật 10.000 lần, cho đúng 40.000. Tất cả cập nhật đều hoàn tất trước khi đọc kết quả nhờ `Thread.join()`; số này nằm rất xa giới hạn `int`.
5. Bộ đếm `AtomicReference<BigInteger>` vượt giới hạn `long` mà không wrap.

Đây là ví dụ nhỏ minh họa ngữ nghĩa; phép kiểm tra nhiều luồng ở giá trị 40.000 **không phải bằng chứng rằng ứng dụng không bao giờ tràn**. Phải kiểm tra riêng tại các giá trị biên và giới hạn miền dữ liệu thực tế.

## 9. Khi nào nên dùng `LongAdder` hoặc `LongAccumulator`?

Java còn cung cấp `LongAdder` và `LongAccumulator` trong `java.util.concurrent.atomic`, thường hữu ích cho thống kê cập nhật với tranh chấp cao.

~~~java
import java.util.concurrent.atomic.LongAdder;

LongAdder events = new LongAdder();
events.increment();
events.add(5L);

System.out.println(events.sum()); // 6
~~~

Nhưng **`LongAdder` không tự kiểm tra tràn**: nó sử dụng các giá trị kiểu `long`, tổng có thể vượt miền và wrap. Hơn nữa, khi có cập nhật đồng thời, `sum()` không phải snapshot nguyên tử của tất cả phần đang được cập nhật; nó không phù hợp để làm một lần kiểm tra giới hạn chính xác rồi quyết định cho phép tăng tiếp.

Không giải quyết vấn đề bằng cách:

~~~java
// SAI nếu cần giới hạn cứng trong môi trường có cập nhật đồng thời.
if (events.sum() < LIMIT) {
    events.increment();
}
~~~

`LongAccumulator` cho phép khai báo hàm tích lũy, nhưng việc cung cấp toán tử cộng không tự tạo cơ chế tràn có kiểm soát hoặc transaction xuyên dữ liệu. Một hàm tích lũy tùy biến cũng phải được thiết kế về tính kết hợp, retry, tranh chấp và chính sách khi vượt miền. Nếu cần một giới hạn cứng trên một counter riêng lẻ, `AtomicInteger`/`AtomicLong` với cập nhật có kiểm tra là điểm khởi đầu dễ kiểm chứng hơn.

## 10. Các bẫy liên quan khi thiết kế counter

### A. Không kiểm tra bằng biểu thức đã tràn

~~~java
int next = counter.incrementAndGet();
if (next < 0) {
    // Trễ: bộ đếm đã được cập nhật thành giá trị âm.
}
~~~

Cách này có thể giúp phát hiện bất thường **sau khi xảy ra**, nhưng không đảm bảo bất biến “counter không bao giờ âm”. Nếu số âm vốn là giá trị hợp lệ, kiểm tra như vậy còn gây báo động giả.

### B. Đừng kỳ vọng `volatile` tự cộng nguyên tử

~~~java
volatile int total = 0;
~~~

Trong một trường của lớp, `volatile` giúp bảo đảm khả năng nhìn thấy giá trị và các quan hệ về thứ tự bộ nhớ theo Java Memory Model, nhưng biểu thức `total++` vẫn là tổ hợp đọc–cộng–ghi và có thể bị mất lượt cập nhật. `volatile` cũng không kiểm tra overflow. Nếu yêu cầu cập nhật nguyên tử, dùng phương án đồng bộ/atomic thích hợp.

### C. Nhiều biến atomic không tạo một giao dịch

Nếu cần trừ số dư ở một tài khoản và cộng số dư ở tài khoản khác, hai `AtomicLong` riêng lẻ không tự tạo giao dịch nguyên tử. Một phép trừ có kiểm tra và một phép cộng có kiểm tra vẫn có thể để trạng thái nửa chừng nếu bước sau thất bại. Hãy chọn transactional database, khóa đồng bộ thích hợp, hoặc cập nhật một object trạng thái bất biến được bọc trong một `AtomicReference` với CAS, tùy kiến trúc.

### D. Chính sách lỗi phải có ở mọi lối ghi

Một API `checkedIncrement()` không hữu ích nếu nơi khác vẫn có thể gọi trực tiếp `atomicCounter.getAndIncrement()`. Đóng gói reference trong lớp, chỉ mở các phương thức cập nhật đúng quy tắc và có kiểm thử biên.

### E. Overflow có thể đến từ chuyển kiểu

Một tổng `long` vẫn có thể bị cắt còn `int`:

~~~java
long count = 3_000_000_000L;
int unsafe = (int) count; // giá trị sai nếu mong giữ nguyên count

try {
    int safe = Math.toIntExact(count);
} catch (ArithmeticException e) {
    System.out.println("too large for int");
}
~~~

`Math.toIntExact` hữu ích ở ranh giới API khi dữ liệu được thu thập bằng `long` nhưng caller đòi `int`. Cast thường không tự phát hiện mất dữ liệu.

## 11. Kiểm thử đúng trọng tâm

Bộ kiểm thử nên có cả **biên số học** lẫn **tranh chấp đồng thời**. Một bộ chỉ kiểm tra 10.000 lần tăng sẽ không phát hiện lỗi wrap tại MAX.

| Trạng thái ban đầu | Cập nhật | Hành vi atomic thông thường | Hành vi checked `Math.addExact` |
| --- | --- | --- | --- |
| `0` | `+1` | `1` | `1` |
| `Integer.MAX_VALUE - 1` | `+1` | `Integer.MAX_VALUE` | `Integer.MAX_VALUE` |
| `Integer.MAX_VALUE` | `+1` | `Integer.MIN_VALUE` | Ném `ArithmeticException` |
| `Integer.MIN_VALUE` | `-1` | `Integer.MAX_VALUE` | Ném `ArithmeticException` |
| `Integer.MIN_VALUE` | `+1` | `Integer.MIN_VALUE + 1` | `Integer.MIN_VALUE + 1` |
| `Long.MAX_VALUE` (kiểu `long`) | `+1L` | `Long.MIN_VALUE` | Ném `ArithmeticException` |

Cần đặc biệt xét:

- **Giá trị trả về:** `getAndIncrement` trả giá trị cũ; `incrementAndGet` trả giá trị mới.
- **Trạng thái khi lỗi:** với test một luồng, kiểm tra giá trị không bị thay đổi sau ngoại lệ `addExact`.
- **Tranh chấp gần MAX:** hai luồng cùng tăng từ `MAX - 1`; với checked update, đúng một cập nhật có thể đưa lên MAX, cập nhật vượt MAX phải bị từ chối. Test cần đồng bộ điểm bắt đầu và bắt kết quả/ngoại lệ của từng thread để tránh bỏ sót lỗi.
- **Callback có side effect:** kiểm tra logic không phụ thuộc số lần gọi của hàm update khi CAS retry.
- **Điều kiện nghiệp vụ:** nếu counter phải không âm, trường hợp `delta < 0` còn cần kiểm tra miền không âm riêng; `Math.addExact` chỉ ngăn overflow, không ngăn kết quả âm hợp lệ về mặt `int`.
- **Các đường ghi khác:** kiểm tra không có caller dùng phương thức bypass chính sách checked.

Trong một test chạy nhiều luồng, phải chờ tất cả futures/threads hoàn tất và thu thập exception, nếu không lỗi ở worker thread có thể bị bỏ qua và test chính vẫn pass.

## 12. Bài tập vận dụng

**Bài 1.** Với:

~~~java
AtomicInteger a = new AtomicInteger(Integer.MAX_VALUE);
System.out.println(a.getAndIncrement());
System.out.println(a.get());
~~~

Đầu ra là gì? `getAndIncrement()` có ném exception không?

**Bài 2.** Một API dùng:

~~~java
if (counter.get() != Integer.MAX_VALUE) {
    counter.incrementAndGet();
}
~~~

Tại sao đoạn này vẫn có thể tràn khi có hai luồng? Hãy thay bằng một câu lệnh dùng `updateAndGet` và `Math.incrementExact`.

**Bài 3.** Với `AtomicLong` đang ở `Long.MIN_VALUE`, `decrementAndGet()` trả gì? Còn `updateAndGet(Math::decrementExact)` sẽ thế nào?

**Bài 4.** Counter phải nằm trong khoảng 0–100. Sử dụng `Math.addExact` có đủ để bảo đảm điều kiện này không? Nếu không, cần kiểm tra gì **bên trong cùng callback**?

**Bài 5.** Vì sao không nên gọi `sendEmail()` trong callback của `updateAndGet`? Nếu gọi bên ngoài callback sau khi update thành công thì những bảo đảm nào vẫn chưa có?

### Đáp án

1. Lần in đầu trả `2147483647` (giá trị cũ); lần in sau trả `-2147483648` (đã wrap). Không có exception tự động.
2. Hai luồng cùng có thể đọc `MAX - 1`, cả hai vượt qua kiểm tra; sau hai cập nhật trạng thái wrap. Dùng `counter.updateAndGet(Math::incrementExact)` hoặc `counter.updateAndGet(v -> Math.addExact(v, 1))`.
3. `decrementAndGet()` wrap thành `Long.MAX_VALUE`. `Math.decrementExact` ném `ArithmeticException` trước khi lần thử cập nhật quá biên được commit.
4. Không đủ: ví dụ tăng từ 100 lên 101 không overflow kiểu `int`. Trong callback, kiểm tra `next` nằm trong `[0, 100]` và báo lỗi hoặc bão hòa theo hợp đồng nghiệp vụ; kết hợp kiểm tra và cập nhật thành một thao tác atomic.
5. Callback có thể chạy nhiều lần do CAS retry, khiến email gửi trùng. Đưa email ra ngoài callback tránh side effect khi tính lại, nhưng việc cập nhật bộ đếm và gửi email vẫn không phải transaction nguyên tử, nên cần thiết kế giao tiếp/đảm bảo gửi phù hợp.

**Điểm cần nhớ:** `AtomicInteger` và `AtomicLong` giúp các luồng cập nhật đúng theo phép toán kiểu số nguyên Java; chúng **không** biến phép toán thành arithmetic có kiểm tra. Khi muốn từ chối tràn, kết hợp `Math.*Exact` với `updateAndGet` hoặc một vòng CAS đúng, đồng thời đóng gói mọi lối ghi và kiểm thử tại giới hạn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-numbers-010">Chọn mức đồng bộ theo hợp đồng nhất quán cần có</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn mức đồng bộ theo hợp đồng nhất quán cần có

## 1. Bài toán: “đúng” nghĩa là gì khi nhiều luồng cùng cập nhật số?

Trong ứng dụng đa luồng, nhiều worker có thể đồng thời tăng bộ đếm, cộng tổng doanh thu, trừ hàng tồn kho hoặc cập nhật số lượt và tổng giá trị. Cùng là dữ liệu số, nhưng mỗi nghiệp vụ cần **mức nhất quán khác nhau**:

- Một dashboard chỉ cần số request gần đúng *tại thời điểm quan sát* trong lúc các request vẫn đến.
- Một API cấp số thứ tự yêu cầu mỗi lần cấp nhận một giá trị duy nhất, không làm mất lần tăng.
- Một giao dịch đặt chỗ phải bảo đảm không có hai luồng cùng sử dụng một suất cuối cùng.
- Một báo cáo gồm `count` và `total` phải phản ánh **cùng một trạng thái**; không được đọc `count` mới nhưng `total` cũ nếu chúng có ràng buộc với nhau.

Vì thế câu hỏi đúng trước khi chọn `volatile`, `AtomicLong`, `LongAdder` hay `synchronized` là: **Hợp đồng nhất quán cần bảo đảm điều gì và ở thời điểm nào?**

Bài học này tập trung vào chọn cơ chế đồng bộ cho dữ liệu số trong **một tiến trình JVM** với Java 17+. Đồng bộ giữa nhiều JVM, database và dịch vụ phân tán cần một hợp đồng giao dịch/phối hợp khác; các primitive bên dưới không tự giải quyết phạm vi đó.

## 2. Ba vấn đề nền tảng: atomicity, visibility và ordering

### 2.1. Atomicity — phép cập nhật có bị xen kẽ làm mất dữ liệu không?

```java
class UnsafeCounter {
    private long count;

    void increment() {
        count++; // đọc -> cộng 1 -> ghi; không phải thao tác nguyên tử
    }

    long get() {
        return count;
    }
}
```

Nếu hai luồng cùng đọc `count == 5`, rồi cùng ghi `6`, ứng dụng có thể mất một lần tăng. Đây là **race condition**. Ngay cả khi một lần đọc/ghi `long` được thực hiện nguyên tử trên nền tảng/JVM cụ thể, biểu thức `count++` vẫn là chuỗi thao tác đọc–sửa–ghi và không được bảo vệ khỏi mất cập nhật.

### 2.2. Visibility — luồng khác có nhìn thấy thay đổi không?

Không có quan hệ đồng bộ phù hợp, một luồng có thể tiếp tục dùng giá trị cũ hoặc compiler/CPU/JVM sắp xếp thao tác theo những tối ưu được Java Memory Model (JMM) cho phép. `volatile` bảo đảm khả năng quan sát và các quan hệ thứ tự *happens-before* nhất định:

```java
class ShutdownSignal {
    private volatile boolean stopped;

    void stop() {
        stopped = true;
    }

    void work() {
        while (!stopped) {
            // Kiểm tra tín hiệu dừng từ luồng khác.
        }
    }
}
```

Đoạn code minh họa một tín hiệu; vòng bận (*busy spin*) không nhất thiết là thiết kế tốt cho công việc thật. Có thể dùng blocking queue, interruption hoặc cơ chế phối hợp khác để tránh đốt CPU.

Một phép ghi vào biến `volatile` *happens-before* lần đọc tiếp theo của cùng biến đó quan sát theo quy tắc JMM. Tuy nhiên `volatile` **không** biến `count++` thành nguyên tử và **không** làm cả nhóm biến trở thành một giao dịch.

### 2.3. Ordering — các thao tác có xuất hiện theo thứ tự cần thiết không?

JMM cho phép một số tối ưu sắp xếp lại thao tác khi quan sát từ luồng khác, miễn các quy tắc hợp lệ được giữ. `synchronized`, `volatile`, phép toán atomic và các cấu trúc concurrency chuẩn tạo ra các ranh giới thứ tự/visibility theo hợp đồng của chúng.

Chẳng hạn, một luồng ghi các trường dữ liệu rồi phát tín hiệu qua một biến `volatile`, luồng khác đọc tín hiệu phù hợp rồi đọc dữ liệu sẽ có quan hệ happens-before thích hợp. Điều này chỉ đúng khi toàn bộ quy tắc phát hành/đọc được thực hiện đúng; không phải cứ thêm một biến `volatile` bất kỳ là mọi dữ liệu chia sẻ đều an toàn.

## 3. Chọn mức nhất quán trước khi chọn công cụ

| Hợp đồng cần đạt | Cơ chế thường phù hợp | Bảo đảm chủ yếu |
| --- | --- | --- |
| Một cờ trạng thái độc lập được phát hành/đọc giữa các luồng | `volatile` hoặc `AtomicBoolean` | Visibility và thứ tự phù hợp; không có cập nhật ghép |
| Mỗi lần tăng một bộ đếm đơn phải được ghi nhận, cần kết quả tức thời | `AtomicInteger` / `AtomicLong` | Phép toán atomic cho **một biến** |
| Thống kê tổng số sự kiện với rất nhiều luồng ghi, chấp nhận đọc không phải snapshot nguyên tử khi đang cập nhật | `LongAdder`, `DoubleAdder` | Tổng hợp thông lượng tốt; `sum()` không phải snapshot nguyên tử dưới concurrent update |
| Cần chọn max/min/tổng tích lũy bằng phép gộp nhất quán với nhiều writer | `AtomicLong` với CAS/update, `LongAccumulator` khi phù hợp | Atomic update một trạng thái hoặc phép tích lũy song song có quy tắc |
| Phải kiểm tra điều kiện rồi cập nhật như **một hành động** | `compareAndSet` loop hoặc khóa | Điều kiện và thay đổi cùng một điểm quyết định |
| Nhiều trường liên quan phải luôn thỏa bất biến và snapshot nhất quán | `synchronized`, `ReentrantLock` hoặc trạng thái immutable trong `AtomicReference` | Cập nhật và đọc **cả trạng thái** theo một giao thức |
| Nhiều hàng DB hoặc nhiều dịch vụ phải nhất quán | Giao dịch DB, optimistic locking/version, protocol phân tán | Hợp đồng ngoài phạm vi JVM local |

Các lựa chọn trên là **định hướng**, không phải phép thay thế tự động. `AtomicLong` không chữa lỗi khi một nghiệp vụ trải qua nhiều biến; `LongAdder` không phù hợp để cấp mã thứ tự chính xác tại thời điểm tăng; khóa có thể an toàn nhưng làm giảm khả năng chạy song song nếu phạm vi quá rộng.

## 4. Trường hợp 1 — Chỉ cần nhìn thấy một trạng thái

Đối với một cờ độc lập như `enabled`, `stopped` hay `ready`, `volatile boolean` thường đủ nếu tất cả thao tác chỉ là ghi/đọc trạng thái và giao thức phát hành phù hợp.

```java
private volatile boolean enabled = true;

// Luồng điều khiển:
enabled = false;

// Các worker:
if (!enabled) {
    return;
}
```

Đây là các đoạn nhúng của một lớp, không phải một chương trình Java độc lập.

**Phân biệt với điều kiện đọc–rồi–ghi:** nếu hai worker cần “chỉ một người chuyển từ `false` sang `true` và thực thi công việc”, đoạn:

```java
if (!started) {
    started = true;
    startJob();
}
```

vẫn có race ngay cả khi `started` là `volatile`. Hai luồng có thể cùng đọc `false`. Hợp đồng “chỉ một luồng thắng” cần một hành động nguyên tử:

```java
AtomicBoolean started = new AtomicBoolean(false);

if (started.compareAndSet(false, true)) {
    startJob(); // chỉ luồng đổi false -> true thành công chạy
}
```

Đoạn này cần `import java.util.concurrent.atomic.AtomicBoolean;`. `compareAndSet` thực hiện phép đổi có điều kiện atomically trên một biến. Với công việc có thể thất bại, cần thêm thiết kế phục hồi trạng thái `started`/retry phù hợp; CAS chỉ giải quyết quyền bắt đầu một lần, không tự tạo workflow tin cậy.

## 5. Trường hợp 2 — Bộ đếm đơn cần kết quả chính xác tại điểm quan sát

`AtomicInteger` và `AtomicLong` hữu ích khi tất cả thông tin phải bảo vệ nằm trong **một giá trị**:

```java
import java.util.concurrent.atomic.AtomicLong;

AtomicLong count = new AtomicLong(0);

long afterIncrement = count.incrementAndGet();
long beforeIncrement = count.getAndIncrement();
long current = count.get();
```

Mỗi lời gọi atomic cập nhật/đọc trạng thái của biến đó theo thứ tự nhất quán (*linearizable*), không mất tăng nếu mọi worker cùng dùng đúng biến. `getAndIncrement()` trả giá trị cũ; `incrementAndGet()` trả giá trị mới.

Tuy nhiên hai lời gọi liên tiếp **không tạo ra một transaction**. Ví dụ:

```java
if (count.get() < 100) {
    count.incrementAndGet(); // vẫn có thể vượt 100 vì một worker khác xen vào
}
```

Cả `get()` và `incrementAndGet()` đều nguyên tử riêng lẻ, nhưng phép **kiểm tra–rồi–cập nhật** chưa được thực hiện nguyên tử.

### 5.1. Dùng CAS cho cập nhật có điều kiện

Ví dụ một bộ đếm quota có giới hạn `limit`:

```java
static boolean tryClaim(AtomicInteger used, int limit) {
    if (limit < 0) {
        throw new IllegalArgumentException("limit phải không âm");
    }

    while (true) {
        int current = used.get();
        if (current >= limit) {
            return false;
        }
        if (used.compareAndSet(current, current + 1)) {
            return true;
        }
        // Một luồng khác đã đổi used: đọc lại và thử tiếp.
    }
}
```

Đây là **phương thức nhúng** cần `import java.util.concurrent.atomic.AtomicInteger;`. Giả thiết `used` được tạo với số không âm và chỉ được thay đổi theo giao thức hợp lệ. Với giới hạn `int` dương không vượt miền, phép cộng tránh tràn khi `current < limit`. Việc `compareAndSet` thành công là **điểm tuyến tính hóa** cho thao tác nhận một suất; mọi luồng tranh chấp một suất cuối cùng sẽ không thể cùng thắng nếu cùng dùng `used`.

CAS loop có thể thử lại nhiều lần dưới tranh chấp và yêu cầu xử lý đúng state/overflow. Nếu nghiệp vụ gồm nhiều biến, một CAS trên `used` không đủ; cần đưa toàn bộ trạng thái vào cùng một đối tượng immutable cập nhật atomically hoặc sử dụng khóa.

### 5.2. `updateAndGet` và tác dụng phụ

```java
AtomicLong maxSeen = new AtomicLong(0);

long newMax = maxSeen.updateAndGet(
        current -> Math.max(current, 42L));
```

Lambda cập nhật của một số phương thức atomic **có thể được gọi lại** khi tranh chấp. Nó phải không có tác dụng phụ: tránh gọi API thanh toán, ghi DB, gửi email hoặc tăng một bộ đếm khác trong lambda. Chỉ tính trạng thái mới từ trạng thái cũ; thực hiện side effect sau khi xác định kết quả theo thiết kế nghiệp vụ.

`AtomicLong` có thể tràn số như `long` bình thường; tính nguyên tử **không** bổ sung kiểm tra tràn tự động. Nếu lượng đếm không được phép quay vòng, đưa kiểm tra/ngoại lệ tràn vào một cập nhật nguyên tử được thiết kế đúng.

## 6. Trường hợp 3 — Thống kê có rất nhiều writer: `LongAdder`

Với mục tiêu như “đếm số request đã thấy” trong hệ thống có nhiều luồng, `LongAdder` phân tán cập nhật nội bộ để giảm tranh chấp trên một điểm nóng:

```java
import java.util.concurrent.atomic.LongAdder;

LongAdder requests = new LongAdder();

requests.increment();
requests.add(5L);

long currentlyObserved = requests.sum();
```

**Hợp đồng quan trọng:** `LongAdder.sum()` **không phải snapshot nguyên tử** khi nhiều luồng đang cập nhật. Kết quả có thể không gồm đồng thời mọi cập nhật đang diễn ra. Nếu bạn cần kiểm tra giới hạn quota ở đúng thời điểm mỗi cập nhật hoặc cần một giá trị trả về duy nhất sau mỗi `increment`, hãy dùng cơ chế có hợp đồng atomic phù hợp, không dùng `LongAdder` thay `AtomicLong` một cách máy móc.

Sau khi tất cả writer hoàn tất và có quan hệ đồng bộ xác nhận hoàn tất (chẳng hạn chờ mọi `Future.get()`), `sum()` phản ánh các cập nhật đã kết thúc. Ví dụ ở mục 9 kiểm chứng điều này.

`LongAdder.reset()` và `sumThenReset()` cũng không nên dùng để giả định có **snapshot chính xác khi writer còn đang cập nhật**. Hãy áp dụng chúng khi không có concurrent updates, hoặc khi nghiệp vụ chấp nhận số liệu chỉ mang tính giám sát theo từng khoảng không hoàn toàn sắc nét.

Nếu giá trị là số thực, `DoubleAdder` có ý tưởng tương tự, nhưng do thứ tự cộng dấu phẩy động có thể khác nhau giữa các luồng, kết quả không bảo đảm trùng bit qua những lần chạy.

### 6.1. Bộ đếm theo nhãn trong `ConcurrentHashMap`

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();

counts.computeIfAbsent("success", key -> new LongAdder()).increment();
counts.computeIfAbsent("failure", key -> new LongAdder()).increment();

long success = counts.get("success").sum();
```

`computeIfAbsent` tạo/tra cứu giá trị theo khóa an toàn với concurrent access trên `ConcurrentHashMap`. Mỗi `LongAdder` tiếp tục ghi nhận các lần tăng. Nhưng đọc nhiều nhãn liên tiếp **không tạo thành một snapshot toàn map**; tổng các nhãn quan sát khi cập nhật đang diễn ra có thể không khớp bộ đếm toàn hệ thống tại một thời điểm duy nhất.

Nếu cần “thành công + thất bại = tổng số request” tại cùng một snapshot chuẩn để đối soát, hãy thiết kế cơ chế snapshot nhất quán hoặc lấy kết quả sau khi toàn bộ kỳ ghi đã kết thúc.

## 7. Trường hợp 4 — Nhiều giá trị phải giữ chung một bất biến

Giả sử mỗi giao dịch được chấp nhận làm đồng thời hai việc:

1. Tăng `count` lên 1.
2. Tăng `total` thêm 2 đơn vị.

Bất biến nghiệp vụ yêu cầu một snapshot phải luôn thỏa `total == 2 * count`. Hai `AtomicLong` riêng biệt không đủ để bảo đảm đọc được một cặp trạng thái nhất quán:

```java
AtomicLong count = new AtomicLong();
AtomicLong total = new AtomicLong();

// Writer:
count.incrementAndGet();
total.addAndGet(2);

// Reader:
long seenCount = count.get();
long seenTotal = total.get();
// Hai giá trị trên có thể đến từ hai thời điểm khác nhau.
```

Mỗi biến cập nhật nguyên tử, nhưng **cả cặp không được cập nhật cùng một lúc**. Reader có thể quan sát `count` mới và `total` cũ. `volatile` trên cả hai trường cũng không biến hai lời ghi thành một giao dịch.

### 7.1. `synchronized`: một khóa bảo vệ toàn bộ trạng thái

```java
record Snapshot(long count, long total) {}

final class Ledger {
    private long count;
    private long total;

    synchronized void add(long value) {
        count++;
        total += value;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(count, total);
    }
}
```

Đây là một ví dụ lớp/record có thể đặt vào chương trình cùng package. Mọi writer gọi `add`, mọi reader lấy cặp bằng `snapshot`, và **không có đường truy cập khác bỏ qua khóa**. Khi đó việc sửa hai trường và việc đọc hai trường được tuần tự hóa qua cùng monitor.

`snapshot()` trả về một record bất biến chứa **cặp giá trị được đọc trong cùng critical section**. Ngay khi monitor được thả, worker khác có thể cập nhật Ledger; snapshot có thể trở nên cũ nhưng vẫn **nhất quán nội tại**. Cũ không đồng nghĩa với bị rách giữa các trường.

Nếu chỉ đánh dấu `add` là `synchronized` nhưng `snapshot` đọc trường trực tiếp không đồng bộ, chưa có hợp đồng đầy đủ cho việc đọc cùng một trạng thái. Tất cả đường truy cập phải tuân thủ cùng giao thức bảo vệ.

### 7.2. Khóa tường minh khi cần điều khiển phức tạp hơn

`ReentrantLock` cũng có thể bảo vệ cùng invariant:

```java
import java.util.concurrent.locks.ReentrantLock;

final class LockedTotal {
    private final ReentrantLock lock = new ReentrantLock();
    private long count;

    void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }

    long snapshot() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}
```

Dùng `try/finally` để bảo đảm giải phóng khóa khi có ngoại lệ. `ReentrantLock` phù hợp nếu cần `tryLock`, khóa có thể bị ngắt khi chờ, nhiều condition hoặc mức điều khiển cao hơn. Với một invariant đơn giản, `synchronized` thường dễ đọc và ít nơi viết sai hơn.

Khi nhiều khóa xuất hiện, phải định nghĩa thứ tự giữ khóa để tránh deadlock; không giữ khóa quá rộng trong khi chờ I/O, gọi dịch vụ chậm hoặc chạy callback không kiểm soát.

`ReadWriteLock` có thể hỗ trợ nhiều reader đồng thời khi đọc chiếm ưu thế và thao tác được tổ chức đúng, nhưng không bảo đảm luôn nhanh hơn monitor; chi phí lock và contention phụ thuộc workload.

### 7.3. State immutable trong một `AtomicReference`

Một lựa chọn khác cho một cặp trạng thái là biểu diễn **toàn bộ invariant thành một đối tượng immutable** và thay cả đối tượng trong một thao tác atomic:

```java
import java.util.concurrent.atomic.AtomicReference;

record State(long count, long total) {}

AtomicReference<State> state =
        new AtomicReference<>(new State(0L, 0L));

state.updateAndGet(previous ->
        new State(previous.count() + 1, previous.total() + 2));

State snapshot = state.get();
```

Đây là đoạn nhúng. Đọc `state.get()` cho một bản snapshot nhất quán của **cả hai trường trong đối tượng immutable**. `updateAndGet` có thể gọi lambda nhiều lần khi tranh chấp; lambda phải thuần túy và không thực hiện side effect. Mỗi bản cập nhật tạo đối tượng mới nên có chi phí cấp phát và retry; không mặc định giải pháp này tốt hơn một khóa cho mọi tải.

Nếu invariant nằm trong nhiều đối tượng hoặc tài nguyên ngoài JVM (tài khoản DB, message broker), atomic publication của một `State` trong JVM không đủ để tạo transaction toàn hệ thống.

## 8. `happens-before`, thời điểm quan sát và ranh giới một phép toán

Một hợp đồng nhất quán cần nêu rõ **khi nào** caller được phép coi dữ liệu là hoàn tất.

Trong Java, các quan hệ happens-before thường gặp gồm:

- Một monitor unlock *happens-before* lần lock tiếp theo trên cùng monitor.
- Một ghi `volatile` *happens-before* một đọc phù hợp sau đó trên biến volatile đó.
- Các lớp atomic và cấu trúc trong `java.util.concurrent` cung cấp các bảo đảm bộ nhớ theo tài liệu API.
- Tác động của một task trong executor xảy ra trước khi một luồng khác nhận kết quả thông qua `Future.get()` theo quy tắc memory consistency của API.

Khi đã đợi **toàn bộ** `Future.get()` hoàn tất, một reader có thể lấy tổng cuối của các bộ đếm mà không còn concurrent writer từ chính nhóm tác vụ đó. Trái lại, đọc giữa chừng từ nhiều cấu trúc riêng biệt có thể không phản ánh một thời điểm chung, dù mỗi cấu trúc riêng lẻ thread-safe.

### Phân biệt ba hợp đồng thường bị nhầm

**An toàn khỏi data race:** Các truy cập được đồng bộ đúng; không bị đọc/ghi sai theo JMM.

**Atomic/linearizable cho một thao tác:** Lời gọi đơn như `AtomicLong.incrementAndGet()` có thể được hiểu như xảy ra tại một điểm giữa lúc bắt đầu và lúc trả về.

**Snapshot nhất quán của nhiều biến:** Tất cả thành phần phải đến từ cùng một trạng thái hợp lệ (chẳng hạn cùng monitor hoặc cùng đối tượng immutable được publish atomically).

Một hệ thống có thể đáp ứng tiêu chí thứ nhất và thứ hai cho từng biến nhưng vẫn vi phạm tiêu chí thứ ba. Đây chính là lý do **chọn mức đồng bộ theo hợp đồng**, thay vì chỉ hỏi “có thread-safe không?”.

## 9. Chương trình Java 17+ hoàn chỉnh: so sánh `AtomicLong`, `LongAdder`, snapshot có khóa

Lưu file **`ConsistencyContractDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class ConsistencyContractDemo {
    record Snapshot(long count, long total) {}

    static final class Ledger {
        private long count;
        private long total;

        public synchronized void add(long value) {
            count++;
            total += value;
        }

        public synchronized Snapshot snapshot() {
            return new Snapshot(count, total);
        }
    }

    public static void main(String[] args)
            throws InterruptedException, ExecutionException {

        int workers = 4;
        int iterations = 10_000;

        AtomicLong atomicCount = new AtomicLong();
        LongAdder adderCount = new LongAdder();
        Ledger ledger = new Ledger();

        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    for (int j = 0; j < iterations; j++) {
                        atomicCount.incrementAndGet();
                        adderCount.increment();
                        ledger.add(2);
                    }
                }));
            }

            // Chờ mọi writer hoàn tất trước khi đọc số liệu cuối.
            for (Future<?> future : futures) {
                future.get();
            }

            Snapshot snapshot = ledger.snapshot();

            System.out.println("atomic count = " + atomicCount.get());
            System.out.println("adder count = " + adderCount.sum());
            System.out.println("snapshot count = " + snapshot.count());
            System.out.println("snapshot total = " + snapshot.total());
            System.out.println("snapshot invariant = "
                    + (snapshot.total() == 2 * snapshot.count()));
        } finally {
            executor.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ConsistencyContractDemo.java
java ConsistencyContractDemo
```

Stdout chính xác khi các tác vụ hoàn tất bình thường:

```text
atomic count = 40000
adder count = 40000
snapshot count = 40000
snapshot total = 80000
snapshot invariant = true
```

**Cơ chế và ý nghĩa kết quả:**

1. Bốn worker, mỗi worker chạy 10.000 vòng. Tổng số vòng là 40.000.
2. `AtomicLong` nhận một phép tăng nguyên tử ở mỗi vòng; nó phù hợp nếu cần đọc một counter chính xác tại một thời điểm quan sát.
3. `LongAdder` cũng nhận 40.000 lần tăng. `sum()` được đọc **sau khi** tất cả `Future.get()` hoàn tất, nên kết quả cuối là 40.000. Đọc khi còn writer sẽ không có hợp đồng snapshot atomic.
4. `Ledger.add(2)` bảo vệ cùng lúc `count++` và `total += 2`; `snapshot()` giữ cùng monitor khi chụp cả hai giá trị. Bất biến `total == 2 * count` được bảo toàn trong snapshot.
5. Mặc dù ba loại bộ đếm cho cùng tổng cuối, **ba lời gọi trong mỗi vòng không phải giao dịch chung**. Nếu đọc đồng thời khi worker còn chạy, `atomicCount`, `adderCount` và `ledger` có thể báo khác nhau vì chúng được cập nhật nối tiếp. Ví dụ minh họa các hợp đồng riêng biệt, không xác nhận ba nguồn luôn đồng nhất tức thời.

Chương trình dùng `try/finally` để gọi `shutdown()` cho executor trong Java 17. Ứng dụng thực tế còn cần chính sách về timeout, hủy task, interruption và chờ shutdown phù hợp; `shutdown()` không có nghĩa chờ tất cả task hoàn tất. Ở đây vòng `Future.get()` đã đợi các task được gửi trước khi đọc.

## 10. Những quyết định có thể làm mất tính nhất quán

### 10.1. Chia một invariant thành nhiều primitive atomic

Nếu `availableSlots` và `reservedSlots` phải cùng nhau thỏa tổng cố định, cập nhật riêng hai `AtomicInteger` có thể tạo ra khoảng thời gian mà reader thấy cặp không hợp lệ. Để bảo vệ trạng thái liên quan, dùng một lock chung hoặc một snapshot immutable duy nhất trong `AtomicReference`.

### 10.2. Đặt `volatile` cho `int` rồi gọi `++`

`volatile int count` vẫn không biến thao tác `count++` thành nguyên tử. Chọn `AtomicInteger.incrementAndGet()` hoặc monitor nếu số đếm phải phản ánh mọi lần tăng.

### 10.3. Dùng `LongAdder.sum()` để quyết định “còn quota không?”

`sum()` trong lúc cập nhật không bảo đảm snapshot tuyến tính hóa; logic “`if(sum() < limit) increment()`” có cả vấn đề snapshot lẫn vấn đề check-then-act. Dùng CAS/lock/giao dịch thiết kế cho reservation.

### 10.4. Bảo vệ writer nhưng bỏ quên reader

`synchronized void update()` không giúp phương thức đọc trực tiếp trường không có đồng bộ trở thành snapshot nhất quán. Phải bảo vệ **tất cả** đường đọc/ghi của state theo cùng giao thức.

### 10.5. Giữ lock trong lúc gọi dịch vụ ngoài

Gọi database, HTTP API hoặc mã callback không kiểm soát trong lock có thể kéo dài critical section, gây contention và rủi ro deadlock. Khi cần tính nguyên tử qua tài nguyên ngoài, không thể đơn thuần “bọc cả API call bằng synchronized” và mong transaction phân tán xuất hiện.

### 10.6. Cho rằng `ConcurrentHashMap` làm nguyên tử mọi thao tác ghép

`ConcurrentHashMap.compute`, `putIfAbsent` và các thao tác theo khóa có hợp đồng đồng thời riêng, nhưng tính thread-safe của map không làm thao tác trên **nhiều khóa** trở thành một transaction. Ví dụ chuyển giá trị giữa hai tài khoản ở hai khóa cần giao thức nhất quán lớn hơn.

### 10.7. Quên tràn số, sai số số thực và định nghĩa chính xác

`AtomicLong` giữ atomicity nhưng không ngăn phép cộng `long` tràn miền. `DoubleAdder` là tổng dấu phẩy động, có thể khác bit vì thứ tự cộng không cố định. Nếu cộng tiền, chọn kiểu số và thiết kế nhất quán phù hợp (chẳng hạn integer minor units trong miền không tràn, hoặc `BigDecimal` sau khi xét yêu cầu hiệu năng); concurrency primitive không tự sửa độ chính xác số học.

## 11. Đo lường và kiểm thử hợp đồng

**Trước khi benchmark**, viết các assertion theo hợp đồng. Ví dụ với trường hợp tổng cuối như bài demo: khi tất cả task đã hoàn tất, `atomicCount.get() == workers * iterations`, `adderCount.sum() == workers * iterations`, `snapshot.total() == 2 * snapshot.count()`.

Nếu reader đọc giữa lúc còn writer, chỉ kiểm tra bất biến **được cơ chế thực sự bảo đảm**. Với `Ledger`, mọi snapshot phải nhất quán nội tại. Không kiểm tra ba cấu trúc độc lập luôn có cùng count tại từng thời điểm vì chương trình chưa hề tạo transaction giữa chúng.

Kiểm thử race nên chạy đủ thread, nhiều vòng và dữ liệu khác nhau, nhưng việc chạy 100 lần không lộ lỗi **không chứng minh** một chương trình thiếu đồng bộ là đúng. Lập luận theo happens-before, atomicity và invariant mới là cốt lõi; công cụ concurrency testing có thể hỗ trợ tìm interleaving khó thấy.

Về hiệu năng, cần xem xét:

- Số writer cạnh tranh, tần suất cập nhật, số lần đọc giá trị.
- Chi phí lock contention, allocation cho immutable state và CAS retry.
- Tỷ lệ read/write và phạm vi critical section.
- Yêu cầu throughput hay latency, môi trường CPU/JVM/JIT cụ thể.
- Giá trị kinh doanh của việc đọc một snapshot chính xác thay vì số liệu giám sát gần đúng.

`LongAdder` có thể cho thông lượng tốt ở điểm nóng nhiều writer, nhưng không có “nhanh hơn N lần” cố định. Dùng benchmark như JMH cho so sánh vi mô, đo workload thật cho quyết định kiến trúc.

## 12. Checklist quyết định cho một dữ liệu số dùng chung

Khi nhận yêu cầu mới, có thể tự trả lời tuần tự:

1. **State nào được chia sẻ?** Một cờ, một số, một cặp số, một map, hay giao dịch giữa dịch vụ?
2. **Phải đúng ở thời điểm nào?** Trong từng lần cập nhật, khi reader gọi API, khi hết một batch hay chỉ cuối kỳ?
3. **Đọc giá trị “gần đúng khi đang ghi” có được không?** Nếu có, bộ đếm thống kê kiểu `LongAdder` có thể phù hợp. Nếu không, cần cơ chế snapshot rõ ràng.
4. **Có bước kiểm tra rồi thay đổi không?** CAS/lock cần bao phủ cả hai, tránh `get` rồi `set` tách biệt.
5. **Có invariant giữa nhiều trường không?** Bảo vệ cùng một critical section hoặc publish immutable state atomically.
6. **Phạm vi chỉ trong một JVM?** Nếu DB hoặc dịch vụ khác cùng tham gia, cần thiết kế transaction/idempotency/version ngoài JVM.
7. **Ai được phép ghi, ai được phép đọc?** Cô lập state, thống nhất giao thức; không để “đường tắt” đọc/ghi bypass synchronization.
8. **Kiểu số có đủ miền và độ chính xác?** Chống tràn số, phân biệt `double` và thập phân chính xác; đồng bộ không thay thế chính sách số học.

Bảng tóm lược nhanh:

| Bạn cần | Ưu tiên xem xét |
| --- | --- |
| Tín hiệu dừng/ready đơn giản | `volatile`/`AtomicBoolean` |
| Mọi lần tăng một giá trị được tính đúng, đọc atomic | `AtomicLong`/`AtomicInteger` |
| Rất nhiều writer cập nhật metric, `sum()` chỉ cần quan sát | `LongAdder`/`DoubleAdder` |
| Cấp suất theo điều kiện | CAS loop hoặc lock bao trọn check–update |
| Nhiều trường có invariant và snapshot | `synchronized`, `ReentrantLock`, hoặc `AtomicReference` tới immutable state |
| Đối soát giữa nhiều JVM hoặc DB | Giao dịch, versioning, giao thức phân tán phù hợp |

## 13. Bài tập tự luyện

1. Tại sao `volatile int count; count++;` vẫn có thể mất lượt tăng? Hãy mô tả hai interleaving của hai worker.
2. Một dịch vụ cần cấp 100 vé và không vượt quá 100. Vì sao `if (atomic.get() < 100) atomic.incrementAndGet()` chưa đúng? Hãy dùng CAS để thiết kế phép claim.
3. Dashboard hiển thị tổng request/s mỗi giây với hàng trăm worker. Có cần snapshot tuyến tính hóa của từng lần đọc hay `LongAdder.sum()` là đủ? Câu trả lời phụ thuộc hợp đồng nào?
4. `successCount` và `failedCount` được tăng trong hai cấu trúc khác nhau. Tại sao đọc hai bộ đếm liên tiếp không chắc tạo cùng snapshot?
5. Nếu thay `Ledger` của bài demo bằng hai `AtomicLong`, invariant `total == 2 * count` còn được bảo đảm tại mọi lần đọc trong lúc cập nhật không? Giải thích.
6. Viết phiên bản `Ledger` dùng `AtomicReference<State>` với `record State(long count, long total)`. Chứng minh lambda update không có side effect.
7. Vì sao `Future.get()` của tất cả worker thay đổi điều có thể khẳng định về tổng cuối, nhưng **không** biến các cập nhật lúc đang chạy thành giao dịch xuyên ba bộ đếm?
8. Một service có hai bản sao JVM và cùng thay đổi một hàng tồn kho trong DB. Vì sao `synchronized` trong mỗi bản sao không đủ? Đề xuất ranh giới transaction thích hợp.

**Gợi ý trả lời:** (1) `++` gồm đọc–tính–ghi; hai luồng có thể cùng đọc giá trị cũ. (2) Cần CAS có điều kiện trên cùng biến. (3) Nếu giám sát chấp nhận số liệu trong lúc writer đang cập nhật không tuyến tính hóa, LongAdder có thể hợp lệ. (4) Mỗi lần đọc xảy ra ở thời điểm khác nhau, không có snapshot chung. (5) Không, các cập nhật và đọc hai biến không nguyên tử như một nhóm. (6) AtomicReference publish cặp immutable trong một lần thay đổi; lambda có thể chạy lại. (7) Sau khi tất cả task xong thì không còn writer từ nhóm đó, nhưng quá trình giữa chừng vẫn có thể khác thời điểm. (8) Khóa JVM chỉ có hiệu lực trong tiến trình; transaction/row lock/version tại DB bảo vệ trạng thái dùng chung.

## 14. Ghi nhớ khi áp dụng

Chọn cơ chế đồng bộ bắt đầu từ **đơn vị bất biến của nghiệp vụ**, rồi mới cân nhắc hiệu năng. Một cờ cần visibility, một bộ đếm cần atomic increment, thống kê có thể chấp nhận phép đọc trong lúc cập nhật không tuyến tính hóa, còn một nhóm biến phải được cập nhật và quan sát theo một giao thức thống nhất. Đảm bảo mỗi caller hiểu trạng thái được hứa hẹn là **một giá trị chính xác**, **một snapshot nội tại nhất quán**, hay **một số liệu giám sát tại thời điểm quan sát**.

Tài liệu Java SE 17:
- [`java.util.concurrent.atomic`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/package-summary.html) (`AtomicLong`, `AtomicReference`, `LongAdder`).
- [`ExecutorService`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html), [`Future`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html).
- [Java Language Specification §17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html).


</details>

- [Quay lại đầu trang](#back-to-top)
