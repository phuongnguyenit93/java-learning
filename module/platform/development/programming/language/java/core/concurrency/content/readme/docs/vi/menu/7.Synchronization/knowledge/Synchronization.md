<a id="back-to-top"></a>

# Synchronization

## Menu
- [Intrinsic lock và monitor của mọi object](#synchronization-001)
- [synchronized method so với synchronized block](#synchronization-002)
- [Khóa theo instance và theo class](#synchronization-003)
- [Reentrant synchronization là gì?](#synchronization-004)
- [Mutual exclusion và memory visibility của monitor](#synchronization-005)
- [Thiết kế critical section đủ nhỏ và dễ kiểm chứng](#synchronization-006)
- [Lock ordering để ngăn deadlock](#synchronization-007)
- [Tránh khóa trên String literal hay public object](#synchronization-008)
- [Đợi monitor bằng wait() và đánh thức bằng notify()/notifyAll()](#synchronization-009)
- [Giữ invariant khi chờ và sau khi thức dậy](#synchronization-010)
- [Contended monitor và rủi ro giữ khóa quá lâu](#synchronization-011)
- [Vấn đề khi gọi callback hoặc blocking I/O trong synchronized](#synchronization-012)

## <a id="synchronization-001">Intrinsic lock và monitor của mọi object</a>

<details>
<summary>Xem chi tiết</summary>

# Intrinsic lock và monitor của mọi object trong Java

## 1. Mục tiêu bài học

Sau bài học, bạn có thể:

- Giải thích **intrinsic lock** (khóa nội tại, hay **monitor lock**) gắn với mỗi đối tượng Java và vì sao khóa là **danh tính đối tượng**, không phải tên biến hoặc giá trị của đối tượng.
- Dùng ba hình thức **synchronized**: khối synchronized, phương thức instance synchronized và phương thức static synchronized; xác định chính xác đối tượng monitor được khóa trong từng trường hợp.
- Hiểu **mutual exclusion**, **reentrancy**, thời điểm lấy/nhả monitor, và quan hệ **happens-before** giữa unlock/lock để bảo vệ dữ liệu chia sẻ.
- Phân biệt **BLOCKED** vì chờ monitor với **WAITING/TIMED_WAITING** do chờ điều kiện; nắm cách **Object.wait()**, **notify()**, **notifyAll()** tương tác với monitor.
- Nhận diện lỗi khóa sai đối tượng, khóa không bao phủ bất biến, lộ lock ra ngoài, giữ lock khi ngủ hoặc làm I/O, và deadlock do nhiều monitor.
- Chạy các ví dụ Java 17+ và tự giải thích kết quả mà không dựa vào thứ tự scheduling ngẫu nhiên.

Bài học thuộc **Java concurrency nói chung**, dựa trên Java Language Specification, Java Memory Model (JMM) và API JDK. Các cơ chế đồng bộ hóa riêng của Spring thuộc phạm vi khác.

## 2. Intrinsic lock là gì?

Về mặt ngữ nghĩa của Java, **mỗi object, bao gồm cả array và đối tượng Class, có một monitor liên kết với nó**. Khi một thread thực hiện synchronized trên một object, nó phải lấy quyền sở hữu monitor của object đó trước khi vào critical section; khi rời critical section, nó nhả monitor tương ứng. JVM có thể triển khai hoặc tối ưu cơ chế này theo nhiều cách; không nên giả định cấu trúc cụ thể của object header hoặc thao tác hệ điều hành chỉ từ hợp đồng ngôn ngữ.

~~~java
// Minh họa cú pháp, đặt trong một phương thức của lớp Java:
Object lock = new Object();

synchronized (lock) {
    // Chỉ một thread tại một thời điểm sở hữu monitor của lock
    // để thực thi vùng mã này.
}
~~~

Đây là đoạn minh họa cú pháp, **không phải chương trình độc lập có main()**.

### Khóa gắn với object identity

Hai biến tham chiếu trỏ đến **cùng một object** thì dùng cùng monitor:

~~~java
Object a = new Object();
Object alias = a;

synchronized (a) {
    // Đang sở hữu đúng monitor mà synchronized(alias) sẽ yêu cầu.
}
~~~

Ngược lại, hai object khác nhau dù có cùng nội dung logic vẫn là **hai monitor riêng**:

~~~java
Object a = new Object();
Object b = new Object();
// a != b, nên synchronized(a) và synchronized(b) không chặn nhau.
~~~

Không nhầm **equals()** với khóa: monitor được xác định bằng identity của object (**==**), không phải kết quả equals(), hashCode() hay chuỗi hiển thị. Nếu hai thread định dùng chung khóa, chúng phải thực sự cầm **cùng reference đến object khóa**.

**Một giới hạn thực tế:** biểu thức synchronized phải đánh giá thành một **reference khác null**. Nếu synchronized(null) sẽ ném **NullPointerException** trước khi vào critical section. Không thể synchronized trực tiếp trên primitive như int; chỉ object reference mới có monitor theo cơ chế này.

## 3. Mutual exclusion: cùng monitor, một chủ sở hữu

Một monitor có thể được **một thread** sở hữu tại một thời điểm. Nếu thread A đã giữ monitor của lock, thread B muốn vào synchronized(lock) phải chờ đến khi A nhả. Nếu thread C synchronized trên object khác, C không bị monitor của lock chặn, trừ khi nó còn đòi một khóa/tài nguyên khác.

Điều này bảo vệ **critical section**: chuỗi thao tác cần được quan sát như một đơn vị không bị các thread dùng **cùng monitor** xen vào.

~~~text
Thread A                         Thread B
--------                         --------
synchronized(lock) {             synchronized(lock) {
  đọc count                        chờ monitor của lock
  count = count + 1                ...
  ghi count                        khi A nhả lock -> vào
} // nhả lock                      ...
                                 } // nhả lock
~~~

Nếu một thread sửa count **ngoài** synchronized(lock), luật loại trừ này không tự ngăn nó. Cả reader và writer của dữ liệu cần tuân thủ cùng một chiến lược bảo vệ, hoặc một giao thức JMM tương thích khác.

### Ví dụ 1: bảo vệ phép tăng không nguyên tử

Tệp **MonitorCounterDemo.java**:

~~~java
public class MonitorCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        Object monitor = new Object();
        int[] counter = {0};

        Runnable increment = () -> {
            for (int i = 0; i < 10_000; i++) {
                synchronized (monitor) {
                    counter[0]++;
                }
            }
        };

        Thread a = new Thread(increment, "counter-a");
        Thread b = new Thread(increment, "counter-b");
        a.start();
        b.start();
        a.join();
        b.join();

        synchronized (monitor) {
            System.out.println("counter = " + counter[0]);
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 MonitorCounterDemo.java
java MonitorCounterDemo
~~~

Stdout:

~~~text
counter = 20000
~~~

**Vì sao chính xác?** Hai worker đều dùng **cùng đối tượng monitor**, nên toàn bộ các bước đọc–tăng–ghi của counter[0] nằm trong một critical section mỗi lần. Không có lost update giữa hai worker. Hai lần join() làm main đợi cả hai kết thúc trước khi in; main còn đọc trong synchronized(monitor) để nhất quán với chiến lược bảo vệ biến.

Nếu thay mỗi lần synchronized(monitor) bằng synchronized(new Object()), mỗi thread/lượt sẽ khóa object khác nhau; không còn bảo vệ counter chung. Nếu chỉ đặt synchronized quanh dòng in cuối, các phép tăng trước đó vẫn thiếu đồng bộ hóa.

### Vùng khóa cần bao phủ **bất biến**, không chỉ một câu lệnh

Giả sử một yêu cầu là “nếu còn hàng thì giữ đúng một sản phẩm”. Đoạn mã sau sai vì check và decrement tách khỏi một critical section chung:

~~~java
// Mã minh họa lỗi: stock là biến chia sẻ, lock là khóa chung.
synchronized (lock) {
    if (stock <= 0) {
        return false;
    }
}
synchronized (lock) {
    stock--;
}
return true;
~~~

Giữa hai synchronized, thread khác có thể làm thay đổi stock. Nếu tất cả thao tác stock đi qua cùng monitor, check và update phải nằm **trong một lần giữ khóa**:

~~~java
// Mã minh họa bên trong phương thức trả boolean:
synchronized (lock) {
    if (stock <= 0) {
        return false;
    }
    stock--;
    return true;
}
~~~

Các mảnh trên minh họa hàm nghiệp vụ và cần khai báo biến/phương thức phù hợp mới biên dịch độc lập. **Mục tiêu của khóa là bảo vệ quy tắc đúng đắn toàn bộ**, không chỉ làm từng phép đọc/ghi riêng lẻ.

## 4. Ba hình thức synchronized và monitor tương ứng

### 4.1. synchronized trên một biểu thức

~~~java
synchronized (lock) {
    // critical section
}
~~~

JVM đánh giá biểu thức **lock** để lấy reference object, sau đó sở hữu monitor của object đó khi thực thi body. Body rời đi bằng cách kết thúc bình thường, return, break, hoặc exception thì monitor được nhả theo ngữ nghĩa synchronized. Lưu ý biểu thức có thể có side effect; tốt nhất khóa một field **private final Object** rõ ràng thay vì một biểu thức có thể thay reference.

### 4.2. Instance synchronized method khóa this

~~~java
public synchronized void update() {
    // Giống về monitor với synchronized (this) { ... }
}
~~~

Một phương thức instance synchronized lấy **monitor của this**. Mỗi instance có monitor riêng: hai instance của cùng class có thể chạy đồng thời các phương thức synchronized của mình.

Nếu một object có hai phương thức instance synchronized, hai thread gọi hai phương thức ấy **trên cùng instance** sẽ cạnh tranh cùng monitor. Nhưng nếu một phương thức khác truy cập field mà không lấy khóa, phương thức đó vẫn có thể vi phạm chiến lược đồng bộ.

### 4.3. Static synchronized method khóa Class object

~~~java
public static synchronized void updateGlobal() {
    // Monitor của chính Class object của lớp khai báo phương thức.
}
~~~

Một phương thức **static synchronized** lấy monitor của **Class object** tương ứng với lớp khai báo phương thức (ví dụ: **Example.class**). Nó **không** khóa this, vì phương thức static không có this. **Instance synchronized** và **static synchronized** thường dùng hai monitor khác nhau; không nên kỳ vọng chúng tự loại trừ nhau.

### Ví dụ 2: xem monitor nào đang được giữ

Tệp **MonitorKindsDemo.java**:

~~~java
public class MonitorKindsDemo {
    synchronized void instanceWork() {
        System.out.println(
            "instance lock held = " + Thread.holdsLock(this)
        );
        System.out.println(
            "class lock held = "
                + Thread.holdsLock(MonitorKindsDemo.class)
        );
    }

    static synchronized void classWork() {
        System.out.println(
            "static class lock held = "
                + Thread.holdsLock(MonitorKindsDemo.class)
        );
    }

    public static void main(String[] args) {
        MonitorKindsDemo service = new MonitorKindsDemo();
        service.instanceWork();
        classWork();

        Object explicitLock = new Object();
        synchronized (explicitLock) {
            System.out.println(
                "explicit lock held = " + Thread.holdsLock(explicitLock)
            );
        }

        System.out.println(
            "explicit lock after exit = "
                + Thread.holdsLock(explicitLock)
        );
    }
}
~~~

~~~bash
javac -encoding UTF-8 MonitorKindsDemo.java
java MonitorKindsDemo
~~~

Stdout:

~~~text
instance lock held = true
class lock held = false
static class lock held = true
explicit lock held = true
explicit lock after exit = false
~~~

**Thread.holdsLock(Object)** trả true nếu thread **hiện tại** đang sở hữu monitor của object đó. Nó thích hợp cho assert/chẩn đoán một phạm vi nhất định. Đừng dùng nó để hỏi thread khác có đang giữ monitor hay không hoặc thay thế giao thức đồng bộ.

## 5. Reentrant monitor: cùng thread có thể lấy lại chính khóa mình giữ

Intrinsic lock của Java là **reentrant**. Nếu thread A đã sở hữu monitor của lock, A có thể vào thêm một synchronized(lock) lồng nhau mà không bị chính mình chặn. JVM duy trì mức sở hữu/đếm vào khóa tương ứng; mỗi lần rời synchronized giảm mức giữ, và monitor chỉ được giải phóng hoàn toàn khi thread đã rời hết các mức giữ.

### Ví dụ 3: synchronized lồng nhau

Tệp **ReentrantMonitorDemo.java**:

~~~java
public class ReentrantMonitorDemo {
    public static void main(String[] args) {
        Object lock = new Object();

        synchronized (lock) {
            System.out.println("outer = " + Thread.holdsLock(lock));

            synchronized (lock) {
                System.out.println("inner = " + Thread.holdsLock(lock));
            }

            System.out.println("after inner = " + Thread.holdsLock(lock));
        }

        System.out.println("after outer = " + Thread.holdsLock(lock));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ReentrantMonitorDemo.java
java ReentrantMonitorDemo
~~~

Stdout:

~~~text
outer = true
inner = true
after inner = true
after outer = false
~~~

Cùng một thread có thể gọi lẫn nhau giữa các phương thức synchronized trên cùng instance mà không gây self-deadlock chỉ vì lấy lại monitor đó. Nhưng **reentrancy không ngăn deadlock** khi nhiều thread giành **nhiều monitor khác nhau** theo thứ tự đối nghịch.

## 6. Khi nào monitor được nhả?

Khi synchronized block hoặc synchronized method **rời phạm vi** do chạy bình thường, return hoặc exception, monitor tương ứng được nhả tự động. Không có lệnh unlock tường minh cho intrinsic lock.

Ví dụ:

~~~java
// Phần thân minh họa của một phương thức Java:
synchronized (lock) {
    if (invalid) {
        throw new IllegalStateException("invalid");
    }
} // Khi exception thoát khỏi synchronized, monitor được nhả.
~~~

**lock** và **invalid** là các biến giả định; ví dụ không tự chạy. Việc nhả monitor khi exception thoát **không tự rollback** các field đã bị thay đổi trước exception. Nếu cập nhật nhiều trường rồi lỗi ở giữa, cần thiết kế bất biến và phục hồi nhất quán riêng.

### Ví dụ 4: exception vẫn giải phóng monitor

Tệp **MonitorExceptionDemo.java**:

~~~java
public class MonitorExceptionDemo {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();

        Thread worker = new Thread(() -> {
            try {
                synchronized (lock) {
                    System.out.println("worker: acquired");
                    throw new IllegalStateException("failed");
                }
            } catch (IllegalStateException ex) {
                System.out.println("worker: caught");
            }
        }, "worker");

        worker.start();
        worker.join();

        synchronized (lock) {
            System.out.println("main: acquired");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 MonitorExceptionDemo.java
java MonitorExceptionDemo
~~~

Stdout:

~~~text
worker: acquired
worker: caught
main: acquired
~~~

Main lấy được monitor sau khi worker đã thoát khối synchronized dù worker có ném exception. Trong ví dụ này, join() cũng buộc main chờ worker kết thúc nên stdout có thứ tự xác định.

## 7. Monitor trong Java Memory Model: không chỉ là chặn truy cập

Synchronized có hai loại bảo đảm quan trọng:

**Mutual exclusion:** tại một thời điểm chỉ một thread giữ monitor đang xét; các critical section dùng chung monitor không chồng lấn.

**Memory ordering/visibility:** hành động **unlock** của thread A trên một monitor **happens-before** hành động **lock tiếp theo** của thread B trên **cùng monitor**. Nhờ tính truyền bắc cầu của happens-before, các ghi trước khi A nhả monitor trở nên nhìn thấy theo các ràng buộc JMM đối với B sau khi lấy monitor.

~~~text
Thread A                               Thread B
--------                               --------
synchronized (lock) {                 synchronized (lock) {
    shared = 42;      -------- HB -->     read shared -> 42
} // monitor unlock                     } // monitor unlock
                     (nếu không có ghi khác chen vào)
~~~

Đây là sơ đồ lý tưởng hóa hai critical section với cùng lock và không có cập nhật khác chen giữa. Điểm mấu chốt là **cùng monitor**. Nếu A ghi dưới lock1 còn B đọc dưới lock2 không tương ứng, chỉ sự hiện diện của synchronized ở hai nơi không bảo đảm HB cho hai truy cập đó.

### Vì sao chỉ khóa bên ghi hoặc bên đọc vẫn sai?

~~~java
// Anti-pattern: writer khóa, reader không khóa.
// shared là một field thường được chia sẻ.
void writer() {
    synchronized (lock) {
        shared = 42;
    }
}

int reader() {
    return shared; // Không tham gia cùng giao thức monitor.
}
~~~

Đoạn trên minh họa phương thức trong một lớp, với các field lock và shared được định nghĩa ở nơi khác. Nếu không có một quan hệ đồng bộ hóa khác, reader không có bảo đảm thích hợp để thấy cập nhật. Khóa không tự phát tín hiệu đến mọi phép đọc tự do.

**Thread.sleep()** và **Thread.yield()** không thay thế monitor unlock/lock hay volatile/atomic operations về memory visibility.

### Không nên lẫn monitor với toàn bộ thread safety

Kể cả khi một class khóa đúng lúc khởi tạo, trạng thái mutable bên trong nó vẫn cần bảo vệ theo phạm vi cập nhật. Một biến đếm có thể dùng AtomicInteger; một bất biến trên nhiều field có thể cần một monitor hoặc cấu trúc bất biến được công bố an toàn. Chọn cơ chế theo dữ liệu và invariant, không chỉ theo việc “có synchronized ở đâu đó”.

## 8. BLOCKED, WAITING và TIMED_WAITING liên quan thế nào?

Một thread có thể được quan sát trong các trạng thái sau:

| Thread.State | Trường hợp liên quan | Ý nghĩa |
| --- | --- | --- |
| **RUNNABLE** | Đang hoặc đủ điều kiện chạy | Không nhất thiết đang có CPU |
| **BLOCKED** | Chờ **monitor** để vào synchronized | Chưa sở hữu monitor cần thiết |
| **WAITING** | Object.wait() không timeout, join() không timeout, latch await() không timeout | Đợi hành động/sự kiện khác |
| **TIMED_WAITING** | Thread.sleep(), Object.wait(timeout), join(timeout) | Đợi với giới hạn thời gian |
| **TERMINATED** | run() đã kết thúc | Không nói kết quả nghiệp vụ thành công hay thất bại |

**getState()** là snapshot; trạng thái có thể đổi ngay sau khi được đọc. Chỉ riêng Thread.State không phải một hợp đồng để điều phối chương trình.

Lưu ý: thread chờ **ReentrantLock.lock()** không nhất thiết hiển thị BLOCKED. BLOCKED của Thread.State gắn cụ thể với giành **intrinsic monitor**. Những dạng chờ trong java.util.concurrent thường dựa trên cơ chế park/condition có trạng thái quan sát khác.

## 9. sleep(), yield() và chờ khác có tự nhả monitor không?

**Không.** Nếu một thread gọi Thread.sleep(...) hoặc Thread.yield() khi đang giữ monitor, nó **vẫn giữ monitor** trong lúc ngủ/đề nghị nhường CPU. Các thread khác cần đúng monitor đó vẫn không thể vào synchronized cho đến khi chủ sở hữu rời khỏi khối hoặc dùng cơ chế wait tương ứng.

**CountDownLatch.await()**, **Thread.join()** và thao tác I/O ở trong synchronized cũng **không tự nhả các monitor mà caller đang sở hữu**. Chúng có thể làm thread chờ lâu, khiến các thread khác đang cần monitor bị chặn.

**Object.wait() là ngoại lệ có ngữ nghĩa riêng:** khi thread đang giữ monitor của object và gọi wait() trên chính object đó, thao tác wait sẽ **nhả monitor của đúng object được wait** trong khi đợi, và thread phải lấy lại monitor ấy trước khi tiếp tục. Wait không nhả các monitor khác mà thread đang sở hữu.

### Ví dụ 5: đợi latch không nhả monitor

Tệp **WaitKeepsMonitorDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class WaitKeepsMonitorDemo {
    public static void main(String[] args) throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (monitor) {
                System.out.println("holder: acquired");
                entered.countDown();

                try {
                    release.await(); // Không tự nhả monitor.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }

                System.out.println("holder: releasing");
            }
        }, "holder");

        holder.start();
        entered.await();
        release.countDown();

        synchronized (monitor) {
            System.out.println("main: acquired");
        }

        holder.join();
    }
}
~~~

~~~bash
javac -encoding UTF-8 WaitKeepsMonitorDemo.java
java WaitKeepsMonitorDemo
~~~

Stdout (không có ngắt ngoài):

~~~text
holder: acquired
holder: releasing
main: acquired
~~~

Ngay cả khi holder đang ở **WAITING** do latch, nó vẫn có thể giữ monitor. Main phải chờ holder rời synchronized. Ví dụ dùng latch để tạo điều kiện chờ rõ ràng, không đoán timing bằng sleep.

**Tránh thiết kế deadlock:** nếu main giữ monitor rồi mới gọi release.countDown(), còn worker cần chính monitor đó để đếm xuống latch khác, có thể tạo vòng chờ. Hãy vẽ rõ tài nguyên nào giữ, tài nguyên nào đang chờ.

## 10. Object.wait(), notify() và notifyAll(): điều kiện dựa trên monitor

Mọi object có **wait()**, **notify()**, **notifyAll()** và một wait set riêng. Giao thức này phục vụ chờ **điều kiện của trạng thái chia sẻ**, không tạo một hàng đợi payload hay ghi nhớ các lượt notify đã qua.

- Thread gọi **wait()** phải đang giữ monitor của chính object được wait; nó nhả **đúng monitor ấy** trong lúc đợi và phải lấy lại trước khi tiếp tục. Các monitor khác đang giữ không tự nhả.
- Thread gọi **notify()/notifyAll()** phải giữ monitor tương ứng. **notify()** chọn một waiter không xác định; **notifyAll()** đánh thức mọi waiter hiện tại, nhưng chúng vẫn phải cạnh tranh lấy lại monitor sau khi thread phát tín hiệu nhả khóa.
- **Spurious wakeup**, tín hiệu dành cho điều kiện khác và thay đổi trạng thái do thread khác là lý do bắt buộc phải chờ trong **while (!predicate)**, không chỉ kiểm tra **if** một lần.
- Thay đổi predicate và phát tín hiệu cần được bảo vệ bởi **cùng monitor**, nếu không dễ bỏ lỡ tín hiệu hoặc đọc state sai. Gọi wait/notify khi không giữ monitor gây **IllegalMonitorStateException**.

~~~java
// Snippet minh họa trong method có lock, ready và consume:
synchronized (lock) {
    while (!ready) {
        lock.wait(); // Cần xử lý hoặc khai báo InterruptedException.
    }
    consume();
}
~~~

Đây là phần giới thiệu ngữ nghĩa monitor. Việc lựa chọn notify hay notifyAll, timed wait, interrupt, producer–consumer và nhiều waiter với predicate khác nhau được thực hành sâu trong bài **Đợi monitor bằng wait() và đánh thức bằng notify()/notifyAll()**. Một ví dụ đúng không dựa vào notify như token, mà dựa vào trạng thái được kiểm tra lại sau khi đã lấy lại monitor.

## 11. Thiết kế lock object an toàn

### Dùng private final lock khi cần khóa nội bộ

~~~java
public final class Balance {
    private final Object lock = new Object();
    private long cents;

    public Balance(long initialCents) {
        cents = initialCents;
    }

    public void deposit(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount < 0");
        }
        synchronized (lock) {
            cents = Math.addExact(cents, amount);
        }
    }

    public long snapshot() {
        synchronized (lock) {
            return cents;
        }
    }
}
~~~

Đây là lớp Java có thể biên dịch nhưng không có main(). **Private final lock** giảm khả năng code bên ngoài vô tình lấy cùng monitor, đồng thời giữ một identity ổn định trong vòng đời đối tượng. Trường cents được đọc/ghi dưới cùng khóa.

Ví dụ minh họa tính an toàn của trạng thái trong một JVM; nếu đối tượng phải quản lý giao dịch ở nơi lưu trữ chung hoặc nhiều process, synchronized không thay thế transaction/idempotency thích hợp.

### Tránh khóa các object công khai hoặc thay đổi identity

Không tùy tiện sử dụng **String literal**, đối tượng Integer cache, Class object dùng chung, hoặc đối tượng nhận từ code không kiểm soát làm lock cho trạng thái nội bộ. Các object đó có thể bị thread khác lấy khóa vì mục đích không liên quan, làm phát sinh contention hoặc deadlock khó giải thích.

~~~java
// Anti-pattern: String literal có thể được intern và dùng chung.
synchronized ("shared-lock") {
    // Không nên dùng làm private monitor của dữ liệu ứng dụng.
}
~~~

Tránh:

~~~java
// Anti-pattern: dùng field có thể đổi object làm monitor.
private Object lock = new Object();

void replaceLock() {
    lock = new Object();
}
~~~

Những đoạn này minh họa lỗi thiết kế, không phải các chương trình độc lập. Nếu một thread giữ monitor cũ còn thread khác chuyển sang monitor mới, critical section tưởng chung lại không loại trừ nhau.

### synchronized(this) và phương thức synchronized có phải luôn sai?

Không. Chúng hợp lệ nếu **monitor của this là một phần cố ý của hợp đồng khóa** và việc code khác lấy khóa đó được xem xét. Nhưng với lớp có dữ liệu nội bộ riêng tư, **private final Object lock** thường giúp xác định ranh giới đồng bộ hóa rõ hơn.

## 12. Deadlock và lock ordering

Một monitor reentrant vẫn có thể tham gia deadlock khi hai thread lấy hai monitor **khác nhau** theo thứ tự ngược nhau:

~~~text
Thread A                         Thread B
--------                         --------
lock(monitor1)                   lock(monitor2)
  muốn lock(monitor2)              muốn lock(monitor1)
  -> chờ B                         -> chờ A
             === DEADLOCK ===
~~~

Nếu không thread nào tự bỏ lock, cả hai có thể chờ mãi. JVM không tự nhả monitor chỉ vì một thread đã chờ lâu. Deadlock thường xảy ra khi lock nhiều đối tượng trong quá trình chuyển tiền, cập nhật nhiều cấu trúc hoặc gọi callback dưới khóa.

Một nguyên tắc phòng tránh là **áp dụng thứ tự lấy khóa nhất quán**: ví dụ luôn lấy monitor của object có ID nhỏ hơn trước, rồi đến ID lớn hơn, và nhả theo scope ngược lại. Nếu có trường hợp cùng ID nhưng hai object khác nhau, cần chiến lược định thứ tự tổng quát/khóa tie-break; không chỉ so hashCode() và cho rằng không trùng. Các lựa chọn khác là giảm số lock, tránh gọi code ngoài khi đang giữ lock, hoặc dùng mô hình sở hữu dữ liệu/trao message.

**Không nên dùng chương trình cố ý deadlock trong bài thực hành chạy mặc định**, vì nó có thể treo không giới hạn. Dùng sơ đồ tài nguyên và công cụ chẩn đoán như thread dump để phân tích mà không biến ví dụ học thành quy trình chạy không có điểm kết thúc.

## 13. Sự khác biệt với ReentrantLock và các tiện ích JDK khác

**Intrinsic lock** là monitor được sử dụng qua cú pháp synchronized; Java tự thực hiện enter/exit monitor. **java.util.concurrent.locks.ReentrantLock** là lớp lock tường minh với phương thức lock(), unlock(), tryLock() và khả năng dùng Condition. Cả hai có tính reentrant, nhưng **không phải cùng một khóa** ngay cả khi liên quan cùng object chứa dữ liệu.

Ví dụ mẫu dùng ReentrantLock:

~~~java
// Đoạn minh họa: lock là ReentrantLock đã khởi tạo.
lock.lock();
try {
    updateSharedState();
} finally {
    lock.unlock();
}
~~~

Đây không phải chương trình độc lập; updateSharedState() là phương thức giả định. Với ReentrantLock, phải đảm bảo unlock() trong finally vì Java không tự nhả lock tường minh khi thoát scope. Nếu muốn timeout hay khả năng ngắt khi giành lock, có các API phù hợp như tryLock(timeout, unit) và lockInterruptibly(), với điều kiện sử dụng đúng hợp đồng.

Với intrinsic lock, không có API **try-enter synchronized với timeout** trực tiếp tương đương, cũng không có tham số fairness được cấu hình. Thread đang chờ monitor enter không tự trở thành thread đã lấy monitor chỉ vì gọi interrupt() ở thread khác; cơ chế interrupt không phải lệnh ép monitor nhả.

Các tiện ích như CountDownLatch, Semaphore, BlockingQueue và AtomicInteger phục vụ mục đích khác nhau; hãy chọn chúng khi bài toán là chờ sự kiện, giới hạn số phép chạy đồng thời, trao dữ liệu, hoặc cập nhật nguyên tử một biến. Một monitor thường hợp lý khi cần bảo vệ bất biến ngắn, rõ ràng của trạng thái chia sẻ.

## 14. Hiệu năng, phạm vi và khả năng chẩn đoán

- **Giữ vùng synchronized ngắn và tập trung.** Đừng giữ monitor qua I/O mạng, sleep, chờ dài, callback có thể gọi ngược vào hệ thống hoặc tác vụ CPU nặng khi không cần cho bất biến.
- **Không biến mọi phương thức thành synchronized vô điều kiện.** Nếu dùng nhiều monitor khác nhau, phải giải thích quan hệ giữa dữ liệu và khóa; nếu dùng một monitor quá rộng, có thể tạo contention.
- **Không coi synchronized là luôn chậm.** JVM có tối ưu hóa; đánh giá bằng đo đạc ứng dụng thật và dữ liệu contention, thay vì dựa vào giả định từ các phiên bản JVM cũ.
- **Không đồng bộ hóa chỉ bên writer.** Reader và writer phải tuân thủ chiến lược memory visibility tương ứng.
- **Monitor không bảo vệ trạng thái giữa nhiều JVM/process.** Nếu dữ liệu được chia sẻ qua DB hay dịch vụ ngoài, cần giao thức ở nơi quản lý dữ liệu đó.
- **Quan sát thread dump khi nghi deadlock hoặc contention.** Stack trace, danh sách monitor đang giữ/chờ và trạng thái BLOCKED/WAITING giúp xác định đường khóa; kết hợp log có ID tác vụ và thời điểm để phân tích.
- **Thread.State không phải trạng thái nghiệp vụ.** Thread đang BLOCKED có thể đang xử lý một tác vụ RUNNING; thread TERMINATED không tự chứng minh công việc thành công.

### Bảng quyết định thực hành

| Nhu cầu | Cơ chế nên cân nhắc | Lý do |
| --- | --- | --- |
| Bảo vệ một bất biến nhiều bước trên một object trong JVM | synchronized(private final lock) | Mutual exclusion và happens-before qua cùng monitor |
| Một thao tác tăng số nguyên | AtomicInteger.incrementAndGet() | Một thao tác nguyên tử gọn, tránh monitor nếu không có bất biến phức tạp |
| Chờ một thread kết thúc | Thread.join() | Chờ completion thực tế, không đoán bằng sleep |
| Đợi tín hiệu một lần | CountDownLatch | Giao thức sự kiện rõ hơn tự viết wait/notify |
| Trao phần tử producer/consumer | BlockingQueue | Chờ và trao dữ liệu thread-safe theo API |
| Cần tryLock, timeout, interruptible acquisition | ReentrantLock | API tường minh phù hợp với yêu cầu |
| Bảo vệ trạng thái nhiều tiến trình | Cơ chế đồng bộ của nguồn dữ liệu chung | Monitor chỉ có phạm vi một JVM/classloader tương ứng |

## 15. Những nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| Hai object equals nhau dùng chung monitor | Khóa dựa trên identity, không dựa trên equals |
| synchronized bảo vệ toàn bộ object bất kể ai đọc/ghi | Chỉ code dùng giao thức khóa thích hợp mới nhận được bảo đảm |
| synchronized trên field đang thay reference vẫn là cùng khóa | Thay object khóa có thể tách đôi critical section |
| static synchronized khóa mọi instance | Nó khóa Class object; instance synchronized khóa this |
| Một thread không được vào cùng monitor hai lần | Intrinsic lock **reentrant** |
| sleep/yield/join/CountDownLatch.await làm nhả mọi monitor đang giữ | Chúng không tự nhả các monitor của caller |
| notify() lập tức chuyển CPU và monitor cho waiter | Notifier vẫn giữ monitor đến khi nhả; waiter phải tranh khóa và kiểm tra predicate |
| wait() và sleep() là như nhau | wait() trên object nhả **monitor của chính object đó** trong khi chờ; sleep() không nhả |
| Object.wait() luôn trở về khi điều kiện đúng | Có thể spurious wakeup, timeout, interrupt; dùng vòng while kiểm tra predicate |
| BLOCKED bao gồm mọi cách chờ | BLOCKED đặc biệt mô tả chờ intrinsic monitor enter |
| synchronized chỉ giúp tránh hai thread cùng chạy | Monitor unlock/lock còn có ngữ nghĩa happens-before về bộ nhớ |
| Một critical section được tách thành hai synchronized vẫn nguyên tử | Thread khác có thể chen giữa; phải giữ khóa trong toàn quyết định |
| Monitor tự động phòng deadlock | Hai hay nhiều monitor có thể tạo vòng chờ |
| Mọi object monitor đều là singleton lock toàn ứng dụng | Monitor của từng object có identity và phạm vi riêng |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Nếu hai biến a và b trỏ cùng một object, synchronized(a) và synchronized(b) có loại trừ nhau không?  
**Trả lời:** Có. Hai biểu thức yêu cầu cùng monitor vì identity object giống nhau.

**Câu 2.** Hai instance khác nhau gọi phương thức instance synchronized của cùng class có nhất thiết chặn nhau không?  
**Trả lời:** Không. Mỗi instance có monitor riêng, trừ khi code bên trong dùng thêm một monitor chung.

**Câu 3.** Static synchronized method lấy khóa nào?  
**Trả lời:** Monitor của Class object của lớp khai báo phương thức, ví dụ Example.class.

**Câu 4.** Tại sao phép tăng counter trong MonitorCounterDemo chính xác?  
**Trả lời:** Cả hai worker khóa cùng monitor trong toàn read–modify–write, nên không mất cập nhật; join đợi hoàn tất trước khi main in.

**Câu 5.** Khi thread đang giữ monitor rồi gọi Thread.sleep(100), thread khác có vào synchronized cùng monitor được không?  
**Trả lời:** Không, cho đến khi chủ sở hữu nhả monitor. Ngủ không đồng nghĩa nhả khóa.

**Câu 6.** Vì sao wait() phải đặt trong vòng while?  
**Trả lời:** Có thể spurious wakeup, nhiều waiter tranh điều kiện, hoặc điều kiện thay đổi trước khi reacquire monitor; phải kiểm tra lại predicate.

**Câu 7.** notifyAll() có khiến mọi waiter chạy cùng lúc trong critical section không?  
**Trả lời:** Không. Chúng vẫn phải lấy lại monitor và chỉ một thread có thể giữ monitor mỗi thời điểm.

**Câu 8.** Có thể xử lý deadlock bằng cách gọi interrupt() thread đang BLOCKED chờ monitor không?  
**Trả lời:** Không nên coi interrupt là cơ chế phá deadlock intrinsic monitor. Thiết kế lock ordering và tránh vòng chờ là cách phòng ngừa chính.

**Câu 9.** Nếu writer đồng bộ trên lock1 còn reader đồng bộ trên lock2 khác, có bảo đảm visibility không?  
**Trả lời:** Không thể suy ra từ hai synchronized đó. Phải dùng cùng một giao thức happens-before hoặc hình thức đồng bộ hóa khác đúng yêu cầu.

**Câu 10.** Một synchronized block có exception thoát ra sẽ giữ monitor mãi không?  
**Trả lời:** Không. Java tự nhả monitor khi rời synchronized do exception; nhưng các side effect dở dang không được tự rollback.

## 17. Kết luận

**Intrinsic lock** là quyền sở hữu monitor gắn với một object. synchronized dùng monitor đó để **loại trừ truy cập đồng thời** và thiết lập **happens-before** giữa các lượt khóa phù hợp. Instance synchronized khóa this; static synchronized khóa Class object; synchronized(expression) khóa chính object mà biểu thức đánh giá ra.

Khi thiết kế concurrency, hãy xác định **dữ liệu nào được bảo vệ bởi monitor nào**, giữ nguyên **identity** khóa, bao trùm **toàn bộ bất biến**, hạn chế giữ lock khi chờ lâu và dùng **wait/notifyAll với predicate trong while** nếu cần chờ điều kiện monitor. Với bài toán khác, chọn primitive JDK phù hợp thay vì biến mọi thao tác thành synchronized.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-002">synchronized method so với synchronized block</a>

<details>
<summary>Xem chi tiết</summary>

# synchronized method so với synchronized block trong Java

## 1. Mục tiêu và phạm vi

Hai đoạn mã dưới đây có thể dùng **cùng một monitor lock** nhưng cung cấp mức kiểm soát khác nhau:

~~~java
public synchronized void increment() {
    count++;
}

public void increment() {
    synchronized (this) {
        count++;
    }
}
~~~

Khi cả hai là **instance method** và đối tượng `this` giống nhau, phần thân được bảo vệ bởi cùng monitor. Bản `synchronized method` khóa monitor của đối tượng nhận lời gọi cho **toàn bộ thân phương thức**; `synchronized block` cho phép chọn một đối tượng monitor và thu hẹp vùng mã được bảo vệ. Nếu thay `this` bằng một lock object khác, chúng không còn đương nhiên dùng chung monitor.

Sau bài học, bạn sẽ:
- Mô tả chính xác cú pháp, đối tượng khóa, phạm vi và ý nghĩa của `synchronized` trên instance method, static method và code block.
- Giải thích mutual exclusion, reentrancy, visibility và quy tắc happens-before khi mở/đóng cùng monitor.
- Phân tích sự khác nhau giữa khóa cả method và chỉ khóa một critical section; chọn lock granularity mà vẫn bảo vệ trọn bất biến.
- Nhận ra bẫy đồng bộ từng getter/setter nhưng bỏ hở chuỗi check–then–act hoặc read–modify–write.
- Sử dụng `private final Object lock`, xác định tác dụng của `synchronized(this)` và `synchronized(MyClass.class)`, và tránh khóa nhầm đối tượng.
- Chạy các ví dụ Java 17+ với kết quả ổn định, biết khi nào cần dùng `Lock` hoặc công cụ concurrent khác.

Bài học chỉ đề cập **Java SE và JVM concurrency**, không phụ thuộc framework.

## 2. `synchronized` thực sự bảo vệ điều gì?

Mỗi object trong Java có một **intrinsic lock**, còn gọi là **monitor**. Khi một thread thực thi đoạn `synchronized` liên quan đến object đó, thread phải sở hữu monitor trước khi vào critical section. Nếu thread khác đang giữ cùng monitor, thread đến sau phải chờ cho tới khi có thể lấy khóa.

~~~text
             Monitor của đối tượng sharedCounter
                           │
Thread A ─── lock(sharedCounter) ──▶ critical section ──▶ unlock
                           │                  │
Thread B ─────── phải chờ ─┴──────────────────┘
                                            └─▶ lock rồi mới được vào
~~~

**Mutual exclusion chỉ áp dụng cho các đoạn đồng bộ trên đúng cùng monitor.** Không phải mọi mã truy cập một object đều tự bị khóa. Một method thường khác vẫn có thể chạy trong lúc thread giữ monitor nếu method đó không cố acquire monitor ấy.

Ví dụ:

~~~java
class Counter {
    private int count;

    synchronized void increment() { // dùng monitor của this
        count++;
    }

    int readUnsafe() {               // KHÔNG acquire monitor
        return count;
    }
}
~~~

Hai thread gọi `increment()` và `readUnsafe()` đồng thời **không được bảo đảm** readUnsafe quan sát nhất quán theo hợp đồng đồng bộ của increment. Field private không tự biến đường đọc thành an toàn. Nếu nhiều thread cần đọc/ghi giá trị đang thay đổi, tất cả đường truy cập phải tuân thủ cùng một giao thức: ví dụ `synchronized int get()` với cùng monitor, hoặc thiết kế dùng atomic API.

Synchronized còn quan trọng với **Java Memory Model (JMM)**:
- **Unlock** trên monitor `m` synchronizes-with mọi **lock tiếp theo** trên chính monitor `m` theo synchronization order.
- Từ quan hệ đó và program order, những ghi được bảo vệ trước unlock có thể được quan sát đúng bởi một đoạn mã acquire cùng lock sau đó.
- Mối quan hệ bảo đảm visibility/ordering theo happens-before; không cần giải thích bằng giả định “mọi giá trị luôn nằm trực tiếp trong RAM”.

`synchronized` không làm hệ điều hành chạy các thread theo thứ tự cụ thể, không cấp fairness mặc định, không tạo timeout, và không bảo đảm tác vụ sẽ hoàn thành trong thời gian cố định.

## 3. synchronized instance method: khóa toàn bộ thân method

Cú pháp:

~~~java
class Balance {
    private int cents;

    public synchronized void add(int amount) {
        cents += amount;
    }

    public synchronized int getCents() {
        return cents;
    }
}
~~~

Khi gọi `balance.add(100)`, thread phải lấy **monitor của chính instance `balance`** trước khi thực hiện phần thân. Về mặt lock và phạm vi critical section, ý nghĩa tương đương:

~~~java
public void add(int amount) {
    synchronized (this) {
        cents += amount;
    }
}
~~~

Mọi đường cập nhật và đọc đang bảo vệ field `cents` đều dùng monitor của cùng instance. Nhiều thread gọi `add` hoặc `getCents` trên một object sẽ không cùng ở trong các critical sections này.

### Các đặc điểm

- Method modifier `synchronized` chỉ khai báo trên **method**: quyền khóa gắn với toàn bộ thực thi thân phương thức, không chọn lọc từng dòng.
- Với instance method, lock object là `this` của receiver trong lời gọi. Cùng class nhưng **khác instance** thì monitor khác nhau.
- Một method được đồng bộ không tự biến **mọi method khác** của class thành synchronized.
- Thực thi đi vào method phải acquire lock trước khi chạy phần thân; thoát method bình thường hoặc do ngoại lệ đều nhả monitor.
- `synchronized` không phải một phần của chữ ký method để phân biệt overload; khi override, method override **không bắt buộc kế thừa** modifier synchronized. Cần xem implementation thực sự được gọi.
- Synchronized instance method không phải khóa toàn class: nó không ngăn method của instance khác hoặc static method mặc định chạy song song.

### Hai instance, hai monitor

~~~java
Counter first = new Counter();
Counter second = new Counter();

// Một thread có thể khóa first trong khi thread khác khóa second.
~~~

Nếu invariant nằm trong từng instance riêng, đó có thể là điều mong muốn. Nếu cả hai instance thực ra sửa cùng một **static mutable field**, khóa monitor của từng instance là không đủ để bảo vệ static field. Khi đó cần một shared lock/atomic protocol có phạm vi đúng với shared state.

## 4. synchronized block: chọn monitor và giới hạn vùng khóa

Cú pháp:

~~~java
synchronized (lockExpression) {
    // các hành động được bảo vệ bằng monitor lockExpression
}
~~~

`lockExpression` phải có kiểu tham chiếu; biểu thức được đánh giá để lấy **một object monitor** trước khi vào block. Nếu kết quả là `null`, runtime ném `NullPointerException`; không có monitor nào để acquire. Block có thể xuất hiện trong method, constructor, static initializer hoặc một block mã hợp lệ khác.

Ví dụ:

~~~java
class Inventory {
    private final Object quantityLock = new Object();
    private int quantity;

    void add(int delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("delta must be positive");
        }

        synchronized (quantityLock) {
            quantity += delta;
        }
    }

    int current() {
        synchronized (quantityLock) {
            return quantity;
        }
    }
}
~~~

Đoạn code này bảo vệ field `quantity` bằng `private final Object quantityLock`. Phần kiểm tra tham số `delta <= 0` không phụ thuộc mutable state chung nên có thể làm **trước khi acquire lock**; vùng critical section chỉ bao lấy thay đổi quantity.

Điểm khác biệt lớn nhất với synchronized method là khả năng **chọn monitor khác `this`**. Nếu hai thread gọi `add()` trên cùng Inventory, chúng tranh quantityLock. Nếu code ngoài thực hiện `synchronized(inventory)`, nó **không** khóa quantityLock, nên không trực tiếp chặn `add()`.

### Tại sao thường ưu tiên private final lock?

- **private:** code gọi bên ngoài không biết hoặc không thể vô tình acquire cùng monitor và giữ quá lâu.
- **final:** tất cả lời gọi dùng cùng đối tượng khóa đã xác định; không bị thay lock reference giữa các lời gọi.
- **Object mới dành riêng cho mục đích khóa:** không gắn lock với String literal, Integer cached object, Class object dùng chung hoặc tham số mutable có thể bị caller cũng khóa.

`private final` không biến tất cả state thành thread-safe; nó chỉ giúp **giữ ổn định danh tính monitor**. Mọi method đọc/ghi cùng invariant vẫn phải tuân thủ cùng lock hoặc một protocol tương thích.

### Khi nào lock nguyên method sẽ quá rộng?

~~~java
class ReportService {
    private final Object stateLock = new Object();
    private int completed;

    void process(String input) {
        String formatted = input.strip().toUpperCase(java.util.Locale.ROOT);
        // Tính toán không đụng mutable state chung: không cần giữ lock.

        synchronized (stateLock) {
            completed++;
        }

        System.out.println(formatted);
        // In ra console có side effect riêng, không cần giữ stateLock.
    }

    int completed() {
        synchronized (stateLock) {
            return completed;
        }
    }
}
~~~

Ở đây chỉ thao tác tăng/đọc `completed` cần cùng lock. Nếu đổi `process` thành `synchronized void process(...)` trên `this`, mà các đường cập nhật khác vẫn dùng `stateLock`, sẽ tạo ra **hai giao thức khóa khác nhau** và có thể không bảo vệ đúng invariant. Khi refactor, không chỉ đổi cú pháp: phải kiểm tra mọi đường truy cập state.

Đừng giữ lock qua I/O, sleep, callback hoặc công việc dài **khi không cần**: thời gian giữ monitor lâu làm các thread khác chờ. Tuy nhiên, cũng đừng đưa **một nửa của invariant** ra ngoài lock chỉ để tối ưu. An toàn và tính nguyên tử của thao tác nghiệp vụ là điều kiện trước tiên.

## 5. Bảng so sánh trực tiếp

| Tiêu chí | synchronized instance method | synchronized block |
|---|---|---|
| Cú pháp | `synchronized void method() { ... }` | `synchronized (lock) { ... }` |
| Monitor mặc định | Receiver `this` | Chính object đánh giá từ biểu thức `lock` |
| Phạm vi bảo vệ | Toàn bộ thân method | Chỉ những lệnh trong block |
| Có chọn private lock riêng? | Không bằng modifier instance synchronized; muốn lock khác dùng block | Có, bằng `synchronized (privateLock)` |
| Nơi sử dụng | Method declaration | Trong thân method, constructor, initializer hoặc block hợp lệ |
| Dùng với static? | Có: static synchronized lấy `Class` monitor | Có: `synchronized (SomeClass.class)` hoặc shared lock khác |
| Khi exception thoát critical section | JVM nhả monitor | JVM nhả monitor |
| Tính reentrant | Có | Có |
| Visibility/happens-before | Theo monitor mà method acquire/release | Theo monitor mà block acquire/release |
| Hiệu năng | Phụ thuộc contention, work bên trong và JIT/JVM | Phụ thuộc contention, work bên trong, monitor và thời gian giữ |
| Dễ đọc | Ngắn khi toàn method là một critical section | Rõ biên giới khi chỉ một đoạn cần được bảo vệ |

Không kết luận `synchronized block` luôn nhanh hơn `synchronized method`. Nếu block khóa đúng `this` và ôm trọn toàn bộ method, ngữ nghĩa tương đương; HotSpot/JIT có nhiều tối ưu, hiệu năng thực tế phải đo trên workload. Ưu thế của block là **thiết kế phạm vi và identity của lock** rõ hơn, chứ không phải cú pháp tự sinh “khóa nhanh”.

## 6. static synchronized method sử dụng monitor nào?

Đối với:

~~~java
class GlobalCounter {
    private static int total;

    static synchronized void increment() {
        total++;
    }

    static synchronized int get() {
        return total;
    }
}
~~~

Lock được acquire là **Class object** tương ứng với `GlobalCounter.class` (đối tượng `java.lang.Class` đại diện cho class trong ngữ cảnh class loader liên quan). Về monitor và vùng khóa, có thể viết tương đương:

~~~java
static void increment() {
    synchronized (GlobalCounter.class) {
        total++;
    }
}
~~~

Điều này **khác** `synchronized(this)` trong instance method:

~~~text
new GlobalCounter()             ──▶ instance monitor X
new GlobalCounter()             ──▶ instance monitor Y
GlobalCounter.class             ──▶ class monitor Z

static synchronized method      ──▶ khóa Z
instance synchronized method    ──▶ khóa X hoặc Y (tùy receiver)
~~~

Vì vậy:
- Hai lời gọi static synchronized lên **cùng class monitor** sẽ loại trừ nhau.
- Static synchronized method và instance synchronized method trên một instance **không tự loại trừ nhau**.
- Nếu sửa cùng một static field từ instance method khóa `this` và static method khóa `Class`, vẫn có thể xảy ra race.
- Hai `Class` objects của “cùng tên class” do **hai class loaders khác nhau** định nghĩa không nhất thiết là cùng object; không dùng khóa class làm cơ chế đồng bộ tiến trình khác hoặc classloader khác.

Có thể dùng một `private static final Object LOCK = new Object();` và các block khóa LOCK thay vì khóa Class object, khi muốn tách hẳn khóa nghiệp vụ khỏi monitor mà code khác có thể dùng.

## 7. Ví dụ độc lập 1: cùng monitor, method/block đều đúng

Chương trình chạy với **Java 17+**, cho hai thread cùng tăng một counter instance: một thread dùng synchronized method, thread kia dùng synchronized(this) block. Cả hai dùng monitor của **cùng object**. Phần sau lặp lại đối với static synchronized method và synchronized(Class.class).

Lưu tệp **SynchronizedFormsDemo.java**:

~~~java
public class SynchronizedFormsDemo {
    static final class Counter {
        private int value;
        private static int global;

        synchronized void incrementMethod() {
            value++;
        }

        void incrementBlock() {
            synchronized (this) {
                value++;
            }
        }

        synchronized int value() {
            return value;
        }

        static synchronized void incrementStaticMethod() {
            global++;
        }

        static void incrementStaticBlock() {
            synchronized (Counter.class) {
                global++;
            }
        }

        static synchronized int global() {
            return global;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();

        Thread first = new Thread(() -> {
            for (int i = 0; i < 100_000; i++) {
                counter.incrementMethod();
            }
        }, "method-worker");

        Thread second = new Thread(() -> {
            for (int i = 0; i < 100_000; i++) {
                counter.incrementBlock();
            }
        }, "block-worker");

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("instance=" + counter.value());

        Thread third = new Thread(() -> {
            for (int i = 0; i < 100_000; i++) {
                Counter.incrementStaticMethod();
            }
        }, "static-method-worker");

        Thread fourth = new Thread(() -> {
            for (int i = 0; i < 100_000; i++) {
                Counter.incrementStaticBlock();
            }
        }, "static-block-worker");

        third.start();
        fourth.start();
        third.join();
        fourth.join();

        System.out.println("static=" + Counter.global());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SynchronizedFormsDemo.java
java SynchronizedFormsDemo
~~~

Stdout chính xác, nếu không có thread nào khác sửa Counter trong cùng chương trình:

~~~text
instance=200000
static=200000
~~~

**Giải thích:**
1. `first` và `second` cùng dùng instance `counter`. `incrementMethod()` acquire monitor của counter; `incrementBlock()` dùng `synchronized (this)` với `this == counter`.
2. Mỗi bước `value++` được bảo vệ toàn vẹn; các thread có thể xen kẽ theo **lần tăng**, nhưng không thể cùng thực hiện critical section trên counter.
3. `join()` chờ đủ 200.000 lần tăng trước khi main đọc kết quả. Method `value()` cũng đồng bộ trên monitor tương ứng.
4. `incrementStaticMethod()` và `incrementStaticBlock()` cùng khóa `Counter.class`; sau khi hai thread hoàn tất, `global()` đọc 200.000.
5. Việc **cả hai trường đều xuất hiện trong cùng class** không khiến instance lock và class lock giao nhau. Ví dụ dùng hai trường khác nhau, mỗi trường có một giao thức khóa nhất quán riêng.

Chương trình chứng minh **tính đúng của hai hình thức khóa**; nó không đo latency, contention hay mức overhead. Nếu cần benchmark, warm up JVM, đo nhiều lần và thay đổi độ lớn critical section, số thread, CPU quota và tải bên ngoài.

## 8. Synchronized getter + synchronized setter vẫn có thể làm mất cập nhật

Một lỗi thiết kế quan trọng là đặt `synchronized` cho từng method, rồi cho caller phối hợp chúng thành một thao tác nghiệp vụ:

~~~java
class SplitCounter {
    private int count;

    synchronized int get() { return count; }

    synchronized void set(int next) { count = next; }
}

// Caller A, B cùng chạy:
int old = counter.get();
counter.set(old + 1);
~~~

Mỗi method được bảo vệ **riêng lẻ**, nhưng monitor đã nhả khi `get()` trả về; thread khác có thể vào `get()` trước khi caller đầu tiên gọi `set()`. Hai thread đều đọc 0, rồi cùng ghi 1. Các method đều synchronized đúng cú pháp mà phép tăng tổng thể vẫn **không atomic**.

~~~text
A: lock → get()=0 → unlock
B: lock → get()=0 → unlock
A: lock → set(1) → unlock
B: lock → set(1) → unlock

Expected = 2, actual = 1
~~~

**Cách đúng** là di chuyển toàn bộ read–modify–write vào **một phương thức atomic**, hoặc giữ cùng monitor bao trọn cặp lời gọi nếu API thật sự yêu cầu:

~~~java
synchronized void increment() {
    count++;
}
~~~

Tương tự, một nghiệp vụ “nếu số lượng còn trên 0 thì giảm” phải khóa **toàn bộ check + decrement**. Khóa chỉ phần giảm vẫn cho hai thread cùng kết luận là có thể thực hiện tác vụ, tạo sai invariant nghiệp vụ.

### Ví dụ độc lập 2: dàn dựng lỗi và sửa cùng một lock

Lưu tệp **SplitCriticalSectionDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class SplitCriticalSectionDemo {
    static final class Counter {
        private int count;

        synchronized int get() {
            return count;
        }

        synchronized void set(int next) {
            count = next;
        }

        void incrementAtomically() {
            synchronized (this) {
                count++;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter split = new Counter();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable brokenIncrement = () -> {
            int snapshot = split.get();
            bothRead.countDown();
            try {
                allowWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            split.set(snapshot + 1);
        };

        Thread a = new Thread(brokenIncrement, "split-a");
        Thread b = new Thread(brokenIncrement, "split-b");
        a.start();
        b.start();
        bothRead.await();
        allowWrite.countDown();
        a.join();
        b.join();

        System.out.println("split=" + split.get());

        Counter safe = new Counter();
        Thread c = new Thread(safe::incrementAtomically, "atomic-c");
        Thread d = new Thread(safe::incrementAtomically, "atomic-d");
        c.start();
        d.start();
        c.join();
        d.join();

        System.out.println("atomic=" + safe.get());
    }
}
~~~

Lệnh:

~~~bash
javac SplitCriticalSectionDemo.java
java SplitCriticalSectionDemo
~~~

Stdout trong điều kiện chạy bình thường, không có interrupt ngoài:

~~~text
split=1
atomic=2
~~~

**Vì sao kết quả xác định?**
- `bothRead` buộc cả hai thread của phép tăng sai hoàn tất `split.get()` trước khi có thread nào đi tiếp để `set()`.
- Khi main mở `allowWrite`, cả hai `snapshot` là 0; cả hai gọi `set(1)` (dù có khóa từng setter) nên kết quả cuối là 1.
- Tất cả lời gọi `get()` và `set()` đều dùng chung monitor của split; **chính khoảng hở giữa hai method** khiến mất cập nhật, không phải thiếu khóa trong từng method.
- Ở nhánh safe, toàn bộ `count++` được thực hiện dưới `synchronized (this)` trên cùng Counter. Không có khoảng hở giữa read và write cho thread khác dùng cùng monitor.
- `join()` cho main đọc kết quả sau khi tất cả worker kết thúc; CountDownLatch ở nhánh đầu **dàn dựng lỗi**, không phải biện pháp sửa lỗi.

Nếu một bài toán yêu cầu nhiều field thay đổi nhất quán, đặt toàn bộ thao tác đó dưới một monitor chung hoặc sử dụng state bất biến với một atomic reference swap. Tách critical section quá mịn có thể làm hỏng invariant, dù mỗi field có getter/setter synchronized.

## 9. Monitor identity: cùng code không có nghĩa cùng lock

Xét hai method:

~~~java
class WrongLocks {
    private final Object firstLock = new Object();
    private final Object secondLock = new Object();
    private int value;

    void add() {
        synchronized (firstLock) {
            value++;
        }
    }

    int read() {
        synchronized (secondLock) {
            return value;
        }
    }
}
~~~

Đoạn code này dùng **hai monitor khác nhau** để truy cập một state chung. Mutual exclusion trên firstLock không ngăn read() dùng secondLock chạy trong lúc add() đang cập nhật; đồng thời, unlock firstLock **không tạo** quan hệ synchronizes-with cho lock secondLock. Nếu cần một protocol đọc/ghi nhất quán, cả hai method phải dùng **cùng monitor** hoặc cơ chế đồng bộ tương đương.

Đây là ví dụ ngược lại với việc dùng nhiều lock hợp lệ cho **hai vùng state độc lập**. Nếu hai trường thực sự có invariant độc lập, có thể có two locks, mỗi field được bảo vệ bởi đúng một lock. Nhưng nếu logic cần đọc/tính toán trên cả hai trường thành một snapshot, thiết kế nhiều lock phải xác định rõ cách acquire cùng lúc, thứ tự khóa và khả năng deadlock.

### Lỗi khóa trên object mới cho mỗi lời gọi

~~~java
void broken() {
    synchronized (new Object()) {
        count++;
    }
}
~~~

Đây chỉ là **đoạn trích** trong class có field count. Vì mỗi lần gọi tạo monitor riêng, các thread không dùng chung lock nên không bảo vệ count. Một số JVM có thể loại bỏ lock không tranh chấp này khi tối ưu, nhưng lỗi thiết kế tồn tại độc lập với tối ưu.

### Lỗi khóa trên mutable reference

~~~java
private Object lock = new Object();

void update() {
    synchronized (lock) {
        // ...
    }
}

void replaceLock() {
    lock = new Object();
}
~~~

Nếu lock có thể đổi sang object khác, các thread có thể đồng thời giữ **hai monitor khác nhau** và đều tin rằng đã độc quyền. Vì thế field dùng làm monitor nên ổn định, thường là `private final Object lock`; đừng thay thế lock khi vẫn có thread hoạt động.

### Lỗi dùng String literal hoặc boxed value làm monitor

~~~java
synchronized ("GLOBAL_LOCK") {
    // ...
}
~~~

Các chuỗi literal được intern; code khác không liên quan có thể vô tình khóa cùng String. Boxed values cũng có thể được cache/chia sẻ. Khóa trên object dễ bị alias từ nơi ngoài tạo coupling và nguy cơ contention/deadlock khó chẩn đoán. Ưu tiên object lock riêng, không trỏ ra ngoài.

### Bẫy khóa this làm API bị điều khiển từ ngoài

~~~java
final class Service {
    synchronized void runTask() {
        // ...
    }
}
~~~

Bên ngoài vẫn có thể thực hiện `synchronized(service) { ... }` và giữ cùng monitor, ngăn runTask() vào trong. Không có gì sai về mặt JMM, nhưng nó có thể làm giảm khả năng kiểm soát lock ownership. Với shared service có nhiều caller, một private lock thường giảm coupling hơn; chọn giữa method và block theo ngữ nghĩa public API.

## 10. Lock granularity: khóa rộng hay khóa hẹp?

**Lock granularity** là phạm vi trạng thái và đoạn mã bảo vệ bởi một monitor. Thiết kế cần cân bằng ba yếu tố:

1. **Correctness:** Toàn bộ invariant phải nguyên tử đối với những thao tác có thể cạnh tranh.
2. **Contention:** Giữ monitor quá lâu khiến thread khác chờ lâu, dù đoạn chờ không cần chia sẻ.
3. **Complexity:** Nhiều monitor làm khó kiểm soát lock order, coupling và deadlock.

Ví dụ một method thực hiện tính toán tốn thời gian nhưng chỉ tăng counter dùng chung:

~~~java
class TaskProcessor {
    private final Object counterLock = new Object();
    private long completed;

    void process(byte[] input) {
        long checksum = checksumLocally(input);

        synchronized (counterLock) {
            completed++;
        }

        publishResult(checksum);
    }

    long completed() {
        synchronized (counterLock) {
            return completed;
        }
    }

    private long checksumLocally(byte[] bytes) {
        long sum = 0;
        for (byte b : bytes) sum += b & 0xff;
        return sum;
    }

    private void publishResult(long checksum) {
        // Một side effect bên ngoài critical section,
        // có concurrency contract riêng của nơi nhận kết quả.
    }
}
~~~

Đây là class minh họa, không phải chương trình có main. `checksumLocally()` không sửa shared instance state và chạy ngoài lock. `publishResult()` giả định có giao thức hợp lệ riêng; nếu nghiệp vụ yêu cầu publish kết quả và tăng count là **một transaction**, thì tách chúng ra là không đủ, cần thiết kế atomicity ở đúng phạm vi nguồn dữ liệu.

**Không tùy tiện kéo đọc state ra ngoài lock:** Nếu checksum phụ thuộc mutable shared data mà thread khác có thể sửa, snapshot hoặc phép đọc đó cũng cần được bảo vệ. Chỉ chuyển ra ngoài các phép tính thật sự độc lập hoặc chạy trên immutable input.

### Nếu hai tài nguyên thật sự độc lập

~~~java
final class TwoCounters {
    private final Object leftLock = new Object();
    private final Object rightLock = new Object();
    private int left, right;

    void incrementLeft() {
        synchronized (leftLock) { left++; }
    }

    void incrementRight() {
        synchronized (rightLock) { right++; }
    }
}
~~~

Hai lời gọi có thể chạy đồng thời vì chúng dùng monitor khác nhau và chỉ sửa các trường độc lập. Nếu về sau thêm invariant như `left + right <= capacity`, phải **thiết kế lại** cơ chế khóa, không được dựa vào từng lock độc lập để bảo vệ phép kiểm tra tổng.

## 11. Reentrancy: cùng một thread có thể acquire lại monitor mình đang giữ

Java intrinsic monitors có tính **reentrant**. Khi một thread đã giữ monitor `this`, nó có thể gọi một method synchronized khác của cùng object (hoặc vào `synchronized(this)`) mà không tự deadlock.

~~~java
class ReentrantExample {
    private int count;

    synchronized void incrementTwice() {
        incrementOnce(); // cùng thread acquire lại monitor this
        incrementOnce();
    }

    synchronized void incrementOnce() {
        count++;
    }

    synchronized int get() {
        return count;
    }
}
~~~

Khi `incrementTwice()` gọi `incrementOnce()` hai lần, cùng thread có thể vào lại monitor. JVM theo dõi mức acquire/release tương ứng; monitor chỉ tự do cho thread khác khi thread sở hữu đã hoàn tất các mức khóa. Tính reentrant không cho phép **hai thread** đồng thời chiếm cùng monitor.

### Exception và return

Với synchronized method, JVM nhả monitor khi method thoát (dù `return` hay exception). Với synchronized block, việc thoát block cũng nhả monitor, kể cả khi ngoại lệ thoát khỏi block. Không phải viết `finally { unlock(); }` thủ công như khi dùng `ReentrantLock`.

Nhưng nếu một method sửa **một nửa** trạng thái rồi ném exception, monitor được nhả **không có nghĩa** trạng thái tự rollback. Chương trình cần thiết kế exception safety/invariant riêng nếu thao tác nhiều bước có thể thất bại giữa chừng.

## 12. Deadlock vẫn có thể xảy ra với synchronized

`synchronized` bảo đảm mutual exclusion theo monitor, nhưng không tự tránh deadlock khi nhiều monitor được acquire theo thứ tự trái ngược:

~~~text
Thread A: lock(L1) → chờ lock(L2)
Thread B: lock(L2) → chờ lock(L1)

Cả hai giữ một monitor và chờ monitor kia → có thể deadlock.
~~~

Với synchronized block lồng nhau, phải có **lock ordering policy** nhất quán, ví dụ mọi luồng cần cả hai khóa đều acquire L1 rồi L2. Một lựa chọn khác là gộp hai invariant vào một lock nếu phù hợp, tránh việc phải khóa đồng thời nhiều resource.

Đối với synchronized method, deadlock cũng xuất hiện nếu method trên object A khi đang giữ monitor A gọi method synchronized trên B, trong khi luồng khác giữ B rồi gọi ngược A. “Không thấy block lồng nhau” không đủ để kết luận không có deadlock; xem **call graph và lock order**.

### Không giữ monitor khi gọi code không kiểm soát nếu tránh được

Gọi callback, observer hoặc I/O ngoài trong khi giữ monitor có thể làm code gọi vào lại những lock khác, chờ lâu, hoặc block vô thời hạn. Nếu không cần nguyên tử qua callback, nên chụp dữ liệu cần thiết trong lock, thoát lock rồi mới gọi; nhưng bảo đảm snapshot phù hợp với invariant. Khi bắt buộc giữ lock vì correctness, cần kiểm soát lock graph và thời hạn thao tác.

## 13. wait/notify liên quan tới monitor nào?

`Object.wait()`, `notify()` và `notifyAll()` phối hợp bằng **monitor của chính object nhận lời gọi**. Thread phải giữ đúng monitor đó khi gọi, nếu không sẽ bị `IllegalMonitorStateException`.

Ví dụ:

~~~java
class Signal {
    private final Object monitor = new Object();
    private boolean ready;

    void markReady() {
        synchronized (monitor) {
            ready = true;
            monitor.notifyAll();
        }
    }

    void awaitReady() throws InterruptedException {
        synchronized (monitor) {
            while (!ready) {
                monitor.wait();
            }
        }
    }
}
~~~

Đây là class Java 17+ minh họa, không có main. Gọi `wait()` làm thread **tạm nhả monitor** của monitor và chờ; khi được đánh thức, thread phải acquire lại monitor trước khi tiếp tục. Dùng vòng `while` để kiểm tra lại điều kiện sau khi tỉnh dậy, vì có thể có spurious wakeup hoặc các luồng khác đã thay đổi trạng thái.

Nếu `markReady()` dùng `synchronized (monitor)` nhưng `awaitReady()` lại là synchronized instance method dùng monitor của `this`, đó sẽ là **hai monitor khác nhau**, phá vỡ giao thức phối hợp. Muốn dùng wait/notify an toàn, phải chọn đúng một monitor và điều kiện chung.

`Thread.sleep(...)` **không nhả monitor đang giữ**; không nhầm sleep với wait. Trong thiết kế mới, các công cụ mức cao như `CountDownLatch`, `BlockingQueue`, `Condition` hoặc `Future` thường rõ ràng và ít lỗi hơn nếu phù hợp nhu cầu.

## 14. Synchronized không bảo đảm mọi bất biến tự động

Giả sử biến `balance` được bảo vệ bằng synchronized. Điều sau đây chưa đủ nếu hai method đều đồng bộ nhưng caller thực hiện rời rạc:

~~~java
if (account.balance() >= amount) {  // synchronized getter
    account.withdraw(amount);        // synchronized update
}
~~~

Thread khác có thể rút tiền sau khi lần kiểm tra đầu tiên đã trả về, trước khi caller gọi withdraw. Cần một operation duy nhất kiểm tra và cập nhật:

~~~java
class Account {
    private long balance;

    synchronized boolean tryWithdraw(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("negative amount");
        }
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }

    synchronized long balance() {
        return balance;
    }
}
~~~

Đây là class minh họa làm việc với state trong một JVM, không thể thay thế transaction protocol cho tài nguyên ngoài hoặc nhiều process. Nó cho thấy **đơn vị atomic phải là thao tác nghiệp vụ**, không phải từng accessor. Cùng ý tưởng có thể viết:

~~~java
boolean tryWithdraw(long amount) {
    if (amount < 0) throw new IllegalArgumentException("negative amount");
    synchronized (accountLock) {
        if (balance < amount) return false;
        balance -= amount;
        return true;
    }
}
~~~

Ở phiên bản block, `accountLock` là private final lock của instance; cả các đường đọc/ghi balance khác cần dùng cùng lock để bảo đảm tính đúng.

## 15. Khi nào nên dùng synchronized method, synchronized block, Lock hoặc atomic class?

| Yêu cầu | Cách tiếp cận hợp lý | Lưu ý |
|---|---|---|
| Phần lớn thân instance method là critical section và public monitor hợp lý | `synchronized` instance method | Ngắn, dễ đọc, khóa `this` cho toàn body. |
| Chỉ một đoạn của method đụng shared mutable state | `synchronized(privateLock)` block | Giữ lock đúng phạm vi và bảo vệ đủ invariant. |
| Static state được quản lý toàn class | `static synchronized` hoặc block khóa shared static lock | Class monitor khác instance monitor. |
| Một counter nguyên tử đơn giản | `AtomicInteger.incrementAndGet()` / `AtomicLong` | Không cần lock để chỉ tăng một biến; chuỗi check-then-act vẫn cần CAS thích hợp. |
| Nhiều field cần bất biến tổng hợp | Method/block với cùng monitor hoặc `Lock` | Phải bảo vệ toàn bộ operation, kể cả kiểm tra điều kiện. |
| Cần timeout khi lấy khóa hoặc chờ lock interruptibly | `ReentrantLock.tryLock(timeout)` / `lockInterruptibly()` | Lấy/nhả lock thủ công, `unlock()` trong finally sau khi acquire thành công. |
| Nhiều reader, ít writer với cấu trúc phù hợp | `ReadWriteLock` hoặc `StampedLock` tùy thiết kế | Phức tạp hơn, phải đo hiệu quả và hiểu semantics. |
| Tránh hoàn toàn mutable shared state | Immutable data / local variables / thread confinement | Giảm yêu cầu đồng bộ, thường dễ kiểm chứng nhất. |

Không chọn một cơ chế chỉ vì nó “nhanh hơn”. Trước tiên đặt câu hỏi: **bất biến nào cần được bảo vệ, state đó được chia sẻ ở đâu, ai được quyền cập nhật, và khi nào phép đọc phải thấy kết quả?** Sau đó kiểm tra contention và hiệu năng trong workload thật.

### Lưu ý với virtual threads

Trong Java 21, một số thao tác blocking xảy ra khi virtual thread giữ monitor có thể làm carrier thread bị **pinning**, gây tác động đến khả năng mở rộng dưới workload chờ dài; hỗ trợ của JVM đã tiếp tục được cải tiến ở các bản JDK mới. Quy tắc mutex/happens-before của synchronized method và block về cơ bản vẫn là các quy tắc Java giống nhau. Không sử dụng “synchronized block thay vì method” như mẹo tự tránh pinning: **monitor đang giữ và loại thao tác blocking** mới là yếu tố cần xét. Phải đánh giá trên **JDK cụ thể** và không giữ monitor qua blocking dài khi không cần.

## 16. Những câu sai thường gặp trong phỏng vấn và code review

**“synchronized method khóa cả class.”** Sai nếu là **instance method**: nó khóa monitor của receiver. Chỉ `static synchronized` dùng class monitor; các instance có monitor riêng.

**“synchronized(this) khác synchronized instance method ngay cả khi bọc toàn method.”** Không đúng về lock identity và critical section: chúng đều dùng monitor `this`. Khác nhau về cú pháp/khả năng chọn scope, không phải mặc định về tính an toàn.

**“Tôi đã khóa getter và setter nên get rồi set là atomic.”** Sai. Mỗi method nhả monitor khi trả về; một thread khác có thể chen vào giữa hai lời gọi. Gộp read–modify–write hoặc check–then–act vào một critical section.

**“Hai synchronized blocks ở hai method luôn loại trừ nhau.”** Chỉ khi chúng acquire **cùng monitor object**. `lockA` và `lockB` là hai object khác nhau thì không.

**“Khóa nhiều hơn luôn an toàn hơn.”** Khóa nhiều có thể tăng contention hoặc deadlock và vẫn không cứu được protocol sai. Phải bảo vệ đúng **mọi đường truy cập** và đúng **đơn vị nghiệp vụ**.

**“Code bên ngoài một synchronized method sẽ bị ngăn truy cập object đó.”** Không. Chỉ những đoạn code có yêu cầu acquire cùng monitor mới chờ. Một method không synchronized có thể chạy đồng thời, kể cả lúc monitor đang được giữ.

**“Synchronized chỉ lo atomicity, không lo visibility.”** Sai. Unlock và lock tiếp theo trên cùng monitor có quan hệ synchronizes-with, tạo happens-before bảo đảm visibility/ordering theo JMM.

**“Synchronized block luôn nhanh hơn method.”** Không có bảo đảm. Khi chúng khóa cùng monitor và cùng phạm vi, logic đồng bộ tương tự. Khác biệt quan trọng là lựa chọn lock granularity và monitor.

**“Một method synchronized ném exception sẽ giữ khóa mãi.”** Sai. Monitor tự nhả khi thoát method/block, kể cả do exception. Tuy vậy, dữ liệu có thể ở trạng thái dở dang nếu operation không exception-safe.

**“Không có synchronized trong method này nên không thể deadlock.”** Sai. Method có thể gọi method khác đang acquire monitor, hoặc có lock lồng nhau trên call graph.

**“synchronized bảo đảm FIFO fairness.”** Không. JVM không hứa các thread đang tranh monitor sẽ giành khóa theo thứ tự yêu cầu; ứng dụng không được suy ra deadline hay starvation freedom từ modifier này.

## 17. Checklist áp dụng vào code thực tế

1. Liệt kê tất cả **mutable state** mà nhiều thread có thể cùng truy cập.
2. Định nghĩa invariant cần giữ: đơn trường, đa trường, check-then-act hay một sequence dài hơn.
3. Quyết định **một monitor identity nhất quán** cho mọi read/write liên quan.
4. Nếu toàn bộ thân method thật sự là một critical section và khóa receiver là hợp lý, dùng synchronized method.
5. Nếu chỉ một phần cần bảo vệ hoặc cần private lock, dùng synchronized block nhưng giữ **đủ** các bước liên quan.
6. Với static state, kiểm tra lock là class monitor hay một private static final lock; đừng nhầm với instance monitor.
7. Tránh lock mới mỗi lời gọi, lock mutable, String literal và object mà external code có thể giữ lâu.
8. Không gọi code có thể blocking dài hoặc callback trong lock nếu invariant không đòi hỏi.
9. Nếu có nhiều locks, ghi rõ lock ordering và chẩn đoán nguy cơ deadlock.
10. Đảm bảo phép đọc trạng thái được đồng bộ phù hợp; không dựa vào field private/final để bảo vệ mutable state.
11. Viết test đồng thời bằng CountDownLatch/Future/join để kiểm tra correctness. Không coi một stress test thành công là bằng chứng loại trừ mọi race.
12. Đo workload thực tế trước khi kết luận lock granularity nào tối ưu; correctness luôn đi trước hiệu năng.

## 18. Bài tập tự kiểm tra

**Câu 1.** Hai thread gọi `synchronized void add()` trên **hai instance khác nhau** của Counter. Chúng có phải chờ nhau không?

**Đáp án:** Không vì hai instance có monitor khác nhau, trừ khi bên trong add còn dùng thêm một shared lock khác. Nếu hai instance sửa cùng static field, instance-level synchronized không tự bảo vệ field đó.

**Câu 2.** `synchronized void update()` và `void read() { synchronized(this) { ... } }` thuộc **cùng instance**. Hai critical sections có chạy đồng thời trên hai thread không?

**Đáp án:** Không. Cả hai acquire cùng monitor của receiver.

**Câu 3.** `static synchronized void update()` và `synchronized void updateInstance()` thuộc cùng class. Có đương nhiên loại trừ nhau không?

**Đáp án:** Không. Static dùng Class monitor; instance dùng monitor của receiver.

**Câu 4.** Getter và setter đều synchronized trên `this`. Có đủ an toàn cho `set(get()+1)` không?

**Đáp án:** Không. Lock nhả giữa hai method. Cần một operation đồng bộ bao toàn read–modify–write.

**Câu 5.** Vì sao `synchronized(new Object())` thường không giải quyết tranh chấp shared counter?

**Đáp án:** Mỗi lời gọi tạo monitor khác nhau. Các thread không acquire cùng một khóa nên không có mutual exclusion trên counter.

**Câu 6.** Nếu có method xử lý 1 giây I/O nhưng chỉ tăng shared counter một lần, nên dùng synchronized method hay block?

**Đáp án:** Nếu I/O không thuộc invariant cần khóa, dùng block bảo vệ đúng lần tăng với shared lock để tránh giữ monitor suốt I/O. Nếu tài nguyên ngoài cùng tham gia invariant, cần thiết kế giao thức nhất quán ở phạm vi rộng hơn.

## 19. Tài liệu đối chiếu

- **Java Language Specification `8.4.3.6 — synchronized Methods:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.3.6
- **Java Language Specification `14.19 — The synchronized Statement:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.19
- **Java Language Specification `17.1 — Synchronization:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1
- **Java Language Specification `17.4.4 — Synchronization Order:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.4
- **Java Language Specification `17.4.5 — Happens-before Order:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5
- **ReentrantLock API (Java 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html
- **Thread API (Java 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html

**Ghi nhớ cốt lõi:** `synchronized instance method` tương đương một `synchronized(this)` ôm trọn thân method; `static synchronized` tương đương dùng monitor `MyClass.class`; `synchronized block` cho phép chọn một monitor khác và thu hẹp vùng giữ khóa. **Cùng monitor và đúng phạm vi atomicity mới quyết định tính đúng**, không phải chỉ việc nhìn thấy từ khóa synchronized.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-003">Khóa theo instance và theo class</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa theo instance và theo class trong Java Synchronization

## 1. Cùng là `synchronized` nhưng không nhất thiết cùng một khóa

Trong Java, mỗi đối tượng có một **intrinsic lock**, còn gọi là **monitor**. Khi thread đi vào một phương thức hoặc khối `synchronized`, nó phải chiếm được **monitor tương ứng**. Hai thread chỉ loại trừ lẫn nhau khi chúng cần **cùng một monitor**, không phải chỉ vì hai đoạn code đều viết từ khóa `synchronized`.

Hai kiểu thường gặp:

- **Instance lock (khóa theo đối tượng):** `synchronized` trên một instance method hoặc `synchronized(this)`. Monitor thuộc về **đối tượng cụ thể** đang được gọi.
- **Class lock (khóa theo lớp):** `static synchronized` hoặc `synchronized(MyType.class)`. Monitor thuộc về **đối tượng `Class` đại diện cho `MyType`** trong JVM.

Vì thế, hai đối tượng cùng lớp thường có **hai instance monitor khác nhau**, nhưng những phương thức `static synchronized` khai báo trên cùng một lớp được điều phối qua **cùng một class monitor**.

Ví dụ ý niệm:

```text
Counter a = new Counter()       Counter b = new Counter()
       │                               │
       ▼                               ▼
  monitor của a                  monitor của b
       │                               │
 a.increment()                   b.increment()
       └──── hai monitor khác nhau ────┘

                  Counter.class
                        │
                  class monitor
                        │
                 Counter.reset()
                 Counter.nextId()
           (static synchronized cùng lớp)
```

**Điểm mấu chốt:** đồng bộ là một *giao thức về danh tính khóa*. Từ khóa `synchronized` tự nó không biến mọi hoạt động trong cùng một class thành tuần tự.

Nội dung này thuộc **Java concurrency và synchronization nói chung**; không phụ thuộc framework.

## 2. Bốn hình thức cú pháp và monitor tương ứng

Giả sử lớp tên `Ledger`:

```java
class Ledger {
    synchronized void instanceMethod() {
        // Giữ monitor của this.
    }

    void instanceBlock() {
        synchronized (this) {
            // Giữ đúng monitor của this.
        }
    }

    static synchronized void staticMethod() {
        // Giữ monitor của Ledger.class.
    }

    static void classBlock() {
        synchronized (Ledger.class) {
            // Giữ đúng monitor của Ledger.class.
        }
    }
}
```

Đây là **đoạn định nghĩa lớp minh họa**, không phải chương trình độc lập. Hai cặp tương đương về **danh tính monitor**:

1. `synchronized void instanceMethod()` tương ứng với việc khóa `this` trong một instance method.
2. `static synchronized void staticMethod()` tương ứng với việc khóa `Ledger.class` khi thực thi phần thân static method được khai báo ở `Ledger`.

Tuy vậy, phạm vi giữ khóa trong một phương thức `synchronized` là **toàn bộ lời gọi phương thức**; còn một khối `synchronized(lock)` chỉ giữ khóa ở bên trong khối. Hai hình thức có thể khác nhau về **độ rộng critical section**, dù sử dụng cùng monitor.

Bảng tổng hợp:

| Cú pháp | Monitor cần lấy | Hai lời gọi có thể chạy đồng thời khi nào? |
| --- | --- | --- |
| `synchronized void work()` | `this` | Trên hai instance khác nhau |
| `synchronized (this) { ... }` | `this` | Khi các thread dùng hai instance khác nhau |
| `static synchronized void work()` | `DeclaringClass.class` | Nếu chúng ở hai lớp khai báo khác nhau với class monitor khác nhau |
| `synchronized (Ledger.class) { ... }` | `Ledger.class` | Nếu đoạn code kia dùng monitor khác |
| `synchronized (sharedLock) { ... }` | Chính object được biến `sharedLock` tham chiếu | Nếu `sharedLock` của hai lần gọi là hai object khác nhau |

`Ledger.class` là một đối tượng `java.lang.Class`, vì vậy nó cũng là một object có intrinsic monitor như các object khác. Nó không phải “khóa toàn bộ JVM”. Một class có thể được nạp bởi nhiều class loader khác nhau; mỗi định nghĩa runtime tương ứng có đối tượng `Class` riêng. Thông thường trong một ứng dụng dùng cùng class loader, mọi nơi viết `Ledger.class` đều tham chiếu cùng monitor.

### 2.1. Thực sự có bao nhiêu lock?

Với:

```java
Ledger a = new Ledger();
Ledger b = new Ledger();
```

có ít nhất ba object có thể dùng làm monitor mà bài đang quan tâm:

- `a`: monitor của instance thứ nhất.
- `b`: monitor của instance thứ hai.
- `Ledger.class`: monitor của Class object của `Ledger`.

`a != b` nên giữ monitor `a` không tự khóa `b`. `a` và `Ledger.class` cũng là hai object khác nhau, nên giữ monitor `a` **không** ngăn thread khác lấy `Ledger.class`, trừ khi thread đó phải đợi theo một giao thức khác.

## 3. Ví dụ chạy được: xác định monitor bằng `Thread.holdsLock`

Phương thức static `Thread.holdsLock(object)` cho biết **luồng hiện tại** có đang sở hữu intrinsic monitor của object đó hay không. Có thể dùng nó để xác nhận sự khác biệt mà không phụ thuộc thứ tự lập lịch của các thread.

Tạo tệp `MonitorIdentityDemo.java`:

```java
public class MonitorIdentityDemo {
    synchronized void checkInstance() {
        System.out.println("instance owns this="
                + Thread.holdsLock(this));
        System.out.println("instance owns class="
                + Thread.holdsLock(MonitorIdentityDemo.class));
    }

    static synchronized void checkClass() {
        System.out.println("static owns class="
                + Thread.holdsLock(MonitorIdentityDemo.class));
    }

    void checkExplicitBlocks() {
        synchronized (this) {
            System.out.println("block owns this="
                    + Thread.holdsLock(this));
        }
        synchronized (MonitorIdentityDemo.class) {
            System.out.println("block owns class="
                    + Thread.holdsLock(MonitorIdentityDemo.class));
        }
    }

    public static void main(String[] args) {
        MonitorIdentityDemo demo = new MonitorIdentityDemo();
        demo.checkInstance();
        checkClass();
        demo.checkExplicitBlocks();
    }
}
```

Lệnh chạy với JDK 17+:

```bash
javac MonitorIdentityDemo.java
java MonitorIdentityDemo
```

Kết quả:

```text
instance owns this=true
instance owns class=false
static owns class=true
block owns this=true
block owns class=true
```

Các dòng thể hiện **monitor nào đang thuộc về thread hiện tại**:

- `checkInstance()` là instance synchronized method nên giữ `demo` (`this`), không tự giữ `MonitorIdentityDemo.class`.
- `checkClass()` là static synchronized method nên giữ `MonitorIdentityDemo.class`.
- `checkExplicitBlocks()` chứng minh cả hai monitor cũng có thể lấy bằng khối synchronized.

Ở đây tất cả lời gọi chạy tuần tự trên main để quan sát chính xác; ví dụ không nhằm đo throughput hay số thread chạy đồng thời. Trong một ứng dụng lớn, `Thread.holdsLock` hữu ích cho assertion hoặc chẩn đoán, nhưng không nên dùng nó để “tự kiểm tra rồi khỏi khóa” khi cập nhật state.

## 4. Instance lock: tách sự đồng bộ theo từng đối tượng

Một instance method `synchronized` sử dụng monitor của **receiver** (đối tượng ở bên trái dấu chấm khi gọi). Nếu thread A gọi `a.deposit(...)` và thread B gọi `a.withdraw(...)` mà cả hai method đều synchronized, chúng tranh **cùng monitor `a`**. Không có chuyện cả hai thực thi phần thân đã bảo vệ cùng lúc trên cùng `a`.

Ngược lại, khi thread A gọi `a.deposit(...)` và thread B gọi `b.deposit(...)` với `a != b`, hai thread lấy **hai monitor khác nhau**. Chúng có thể tiến triển đồng thời. Điều này thường là mong muốn nếu `a` và `b` nắm giữ dữ liệu độc lập.

Tạo tệp `PerInstanceCounterDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class PerInstanceCounterDemo {
    private static final class Counter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int get() {
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter a = new Counter();
        Counter b = new Counter();
        CountDownLatch begin = new CountDownLatch(1);

        Thread first = new Thread(() -> {
            try {
                begin.await();
                for (int i = 0; i < 1_000; i++) {
                    a.increment();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "first");

        Thread second = new Thread(() -> {
            try {
                begin.await();
                for (int i = 0; i < 1_000; i++) {
                    b.increment();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "second");

        first.start();
        second.start();
        begin.countDown();
        first.join();
        second.join();

        System.out.println("a=" + a.get());
        System.out.println("b=" + b.get());
    }
}
```

```bash
javac PerInstanceCounterDemo.java
java PerInstanceCounterDemo
```

Kết quả khi không có interrupt bên ngoài:

```text
a=1000
b=1000
```

Mỗi đối tượng `Counter` bảo vệ **trường `value` của chính nó**. `a.increment()` và `b.increment()` không tranh cùng một monitor, giúp tránh serialize các cập nhật thực sự độc lập. `join()` bảo đảm main đọc kết quả sau khi cả hai worker kết thúc.

Lưu ý: ví dụ không khẳng định hai thread luôn chạy song song trên hai CPU core. Nó chỉ cho thấy chúng **không bị một instance lock chung ép loại trừ lẫn nhau**.

### 4.1. Phạm vi của instance lock phải khớp với phạm vi state

Nếu `value` là một trường instance riêng cho từng `Counter`, việc khóa từng instance là hợp lý. Nhưng nếu nhiều instance thực tế cùng tham chiếu đến một mutable object bên ngoài, ví dụ dùng chung một `HashMap` static, thì hai instance lock khác nhau **không đủ** để bảo vệ cấu trúc chung.

Quy tắc: **khóa cần gắn với dữ liệu/đơn vị bất biến cần bảo vệ**, không chỉ gắn với tên method.

## 5. Class lock: điều phối state dùng chung ở cấp lớp

Một `static` field được chia sẻ bởi các instance thuộc cùng định nghĩa runtime của lớp. Nếu nhiều thread truy cập field này, khóa từng instance có thể không bảo vệ được nó, bởi các thread có thể sử dụng instance khác nhau.

Ví dụ rủi ro:

```java
class TicketIssuer {
    private static int nextId;

    synchronized int issue() {
        return ++nextId;
    }
}
```

Đây là đoạn định nghĩa lớp minh họa. `issue()` là **instance synchronized method**, vì vậy:

- `issuerA.issue()` giữ khóa của `issuerA`.
- `issuerB.issue()` giữ khóa của `issuerB`.
- Cả hai sửa **cùng `TicketIssuer.nextId`** nhưng không dùng chung monitor.
- `++nextId` là read-modify-write, có thể lost update; thread khác có thể quan sát giá trị không đúng nếu thiếu đồng bộ.

Cách đơn giản nếu nghiệp vụ là một bộ cấp ID chung trong JVM:

```java
class TicketIssuer {
    private static int nextId;

    static synchronized int issue() {
        return ++nextId;
    }
}
```

Lúc này các lời gọi `TicketIssuer.issue()` dùng monitor `TicketIssuer.class`. Nếu mọi đường cập nhật và đọc liên quan đều tuân thủ cùng khóa, `nextId` được bảo vệ trong phạm vi JVM/class loader tương ứng.

Hoặc dùng khối:

```java
class TicketIssuer {
    private static int nextId;

    static int issue() {
        synchronized (TicketIssuer.class) {
            return ++nextId;
        }
    }
}
```

Hai cách đều là **mã minh họa lớp**, không phải chương trình Java có `main`. Trong thực tế, nếu chỉ cần bộ đếm nguyên tử có thể dùng `AtomicInteger.incrementAndGet()`, nhưng nếu phải bảo vệ **nhiều trường liên quan** thì phải xác định một critical section hoặc snapshot bao trùm toàn bộ invariant.

### 5.1. Class lock không phải “khóa mọi đối tượng của lớp”

Khi một thread giữ `TicketIssuer.class`, một thread khác vẫn có thể vào `issuer.synchronizedInstanceMethod()` vì method đó cần monitor của `issuer`, không phải `TicketIssuer.class`.

Tương tự, hai static synchronized methods thuộc về **hai lớp khác nhau** sẽ không tự loại trừ nhau chỉ vì chúng cùng chạy trong một JVM. Danh tính class monitor, không phải từ khóa static, quyết định xung đột.

## 6. Minh chứng lost update xác định khi khóa sai phạm vi

Đoạn dưới cho hai thread dùng **hai instance khác nhau**, cùng sửa một trường `static volatile int`. Latch buộc cả hai đọc giá trị cũ trước khi bất kỳ thread nào ghi, qua đó chứng minh việc đặt `synchronized` vào instance method **không bảo vệ shared static state**.

Tạo tệp `WrongScopeLockDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class WrongScopeLockDemo {
    private static volatile int sharedCounter;

    synchronized void badIncrement(
            CountDownLatch bothRead,
            CountDownLatch allowWrite) {
        int oldValue = sharedCounter;
        bothRead.countDown();
        try {
            allowWrite.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        sharedCounter = oldValue + 1;
    }

    static synchronized void safeIncrement() {
        sharedCounter++;
    }

    public static void main(String[] args) throws InterruptedException {
        WrongScopeLockDemo a = new WrongScopeLockDemo();
        WrongScopeLockDemo b = new WrongScopeLockDemo();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Thread first = new Thread(
                () -> a.badIncrement(bothRead, allowWrite));
        Thread second = new Thread(
                () -> b.badIncrement(bothRead, allowWrite));

        first.start();
        second.start();
        bothRead.await();
        allowWrite.countDown();
        first.join();
        second.join();
        System.out.println("wrong lock result=" + sharedCounter);

        sharedCounter = 0;
        Thread safeA = new Thread(WrongScopeLockDemo::safeIncrement);
        Thread safeB = new Thread(WrongScopeLockDemo::safeIncrement);
        safeA.start();
        safeB.start();
        safeA.join();
        safeB.join();
        System.out.println("class lock result=" + sharedCounter);
    }
}
```

```bash
javac WrongScopeLockDemo.java
java WrongScopeLockDemo
```

Kết quả khi không có interrupt bên ngoài:

```text
wrong lock result=1
class lock result=2
```

**Phân tích từng bước:**

1. `first` giữ monitor `a`; `second` giữ monitor `b`. Vì `a != b`, chúng không ngăn nhau vào `badIncrement`.
2. Cả hai đọc `sharedCounter = 0` trước khi main phát `allowWrite`. Latch giúp kết quả này xác định.
3. Mỗi thread lưu lại `oldValue = 0` rồi ghi `oldValue + 1`. Kết quả cuối là `1` dù hai thao tác tăng đã được yêu cầu.
4. Main đợi `join()` rồi đặt `sharedCounter = 0` để bắt đầu phép thử thứ hai.
5. Hai lời gọi `safeIncrement()` cùng tranh **monitor `WrongScopeLockDemo.class`**. Vì toàn bộ read-modify-write nằm trong một class critical section, cả hai phép tăng được giữ, kết quả `2`.

Trường `sharedCounter` là `volatile` trong ví dụ để tránh chuyển trọng tâm sang stale-read, **nhưng volatile không biến phép `x++` thành atomic**. Nếu chuyển `badIncrement` thành một method `static synchronized` trên lớp, hai lời gọi sẽ chờ nhau qua cùng class monitor, không còn lịch xen kẽ mất cập nhật như trên.

Bài học tổng quát không phải “mọi static field đều bắt buộc khóa bằng class monitor”; điều bắt buộc là **mọi thread phải thống nhất cùng một cơ chế đồng bộ bảo vệ shared state**. Một `private static final Object LOCK` riêng hoặc `AtomicInteger` cũng có thể phù hợp tùy invariant.

## 7. Instance lock và class lock độc lập với nhau

Một thread đang giữ khóa của instance **không có nghĩa** nó giữ luôn khóa của class. Điều ngược lại cũng đúng. Có thể chứng minh bằng chương trình không cần suy đoán thứ tự log giữa các thread.

Tạo tệp `IndependentMonitorsDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class IndependentMonitorsDemo {
    synchronized void holdInstance(
            CountDownLatch entered,
            CountDownLatch release) {
        entered.countDown();
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static synchronized void useClassMonitor() {
        System.out.println("class method can enter");
    }

    public static void main(String[] args) throws InterruptedException {
        IndependentMonitorsDemo object = new IndependentMonitorsDemo();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() ->
                object.holdInstance(entered, release));
        holder.start();
        entered.await();

        useClassMonitor();  // Không cần chờ monitor của object.
        release.countDown();
        holder.join();

        System.out.println("holder finished");
    }
}
```

```bash
javac IndependentMonitorsDemo.java
java IndependentMonitorsDemo
```

Kết quả khi không có interrupt bên ngoài:

```text
class method can enter
holder finished
```

Thread `holder` đang giữ monitor của `object` và chờ latch `release`. Main gọi static synchronized method và vào được monitor của `IndependentMonitorsDemo.class` **trước khi** phát `release`. Nếu hai method tranh cùng một monitor, cách sắp xếp này sẽ bị kẹt; nó chạy được vì monitor khác nhau.

Chương trình này là bài thử riêng biệt về danh tính khóa. Không nên tạo vòng chờ latch có nguy cơ deadlock chỉ để “kiểm tra” một hệ thống sản xuất. Trong test có thể dùng `await(timeout)` và báo lỗi rõ ràng nếu không nhận được tín hiệu.

## 8. Một object có thể giữ cả hai monitor, nhưng không tự động

Trong một instance method, bạn được phép viết:

```java
class GlobalLedger {
    private static int total;
    private int local;

    synchronized void addToBoth() {
        synchronized (GlobalLedger.class) {
            local++;
            total++;
        }
    }
}
```

Đây là mã minh họa. Khi gọi `object.addToBoth()`, thread chiếm monitor của `object` trước, rồi cố chiếm monitor của `GlobalLedger.class`. Nó sở hữu **cả hai** trong phần thân bên trong. Khi rời khối bên trong, nó nhả class monitor nhưng vẫn giữ instance monitor cho đến khi instance method trả về.

Điều đó có thể hợp lý nếu thao tác thật sự cần bảo vệ một invariant liên quan giữa state instance và state class, nhưng tạo ra nguy cơ **deadlock vì thứ tự lấy khóa** nếu chỗ khác lấy class monitor trước rồi cố lấy instance monitor.

Ngoài ra, có thể đồng bộ theo **một lock riêng** thay vì giữ hai lock:

```java
class GlobalLedger {
    private static final Object GLOBAL_LOCK = new Object();
    private static int total;
    private int local;

    void addToBoth() {
        synchronized (GLOBAL_LOCK) {
            local++;
            total++;
        }
    }
}
```

Đây vẫn là mã minh họa, nhưng mọi instance cùng dùng `GLOBAL_LOCK` nên bảo vệ phép cập nhật chung. Đổi lại, các cập nhật trên instance khác nhau sẽ bị serialize ngay cả khi có thể tách dữ liệu thành các vùng độc lập. Phải cân nhắc đơn vị invariant và mức contention.

**Không dùng “synchronized mọi method” một cách máy móc:** bảo vệ đúng từng object hoặc một state chung là yêu cầu đúng đắn; giữ khóa rộng hơn mức cần thiết có thể làm chậm ứng dụng.

## 9. Deadlock khi trộn instance và class lock

Giả sử hai đường thao tác:

```text
Thread A: giữ instance lock -> chờ class lock
Thread B: giữ class lock    -> chờ instance lock

A chờ B nhả class lock
B chờ A nhả instance lock
=> vòng chờ, có thể deadlock
```

Một ví dụ cấu trúc nguy hiểm (minh họa, **không phải chương trình độc lập và không nên chạy để tạo deadlock**):

```java
class Risky {
    synchronized void instanceThenClass() {
        synchronized (Risky.class) {
            // Cập nhật chung.
        }
    }

    static void classThenInstance(Risky target) {
        synchronized (Risky.class) {
            synchronized (target) {
                // Cập nhật khác.
            }
        }
    }
}
```

Nếu Thread A đã lấy monitor của `target` trong `instanceThenClass`, còn Thread B lấy `Risky.class` trong `classThenInstance`, mỗi bên có thể chờ monitor mà bên kia giữ.

**Cách phòng tránh:**

1. Hạn chế giữ nhiều monitor cùng lúc khi thiết kế cho phép.
2. Nếu phải giữ nhiều lock, quy định **một thứ tự khóa nhất quán** cho tất cả code, chẳng hạn luôn lấy class lock trước rồi mới đến instance lock.
3. Tránh gọi các callback, plugin hoặc method có thể chạy code không kiểm soát trong khi đang giữ khóa; chúng có thể lấy lock khác theo thứ tự ngược.
4. Không dựa vào `Thread.sleep()` để phòng deadlock. Sleep không nhả monitor.
5. Dùng thread dump, Java Flight Recorder hoặc công cụ chẩn đoán deadlock khi cần. Công cụ chỉ giúp phát hiện; biện pháp chính vẫn là thiết kế thứ tự khóa.

Một deadlock do hai intrinsic monitor không thể sửa bằng cách “thử `synchronized` lại với timeout” vì ngôn ngữ không cung cấp thao tác try-lock cho `synchronized`. Nếu nghiệp vụ cần timeout khi lấy khóa, có thể cân nhắc `ReentrantLock.tryLock(timeout, unit)` cùng giao thức rollback và thứ tự khóa phù hợp.

## 10. Reentrancy: cùng thread được vào lại monitor đang giữ

Intrinsic lock của Java là **reentrant (tái nhập)**. Nếu một thread đã giữ monitor `this`, nó có thể gọi một instance synchronized method khác trên **cùng object** mà không tự chặn chính nó.

```java
class ReentrantCounter {
    private int count;

    synchronized void addTwo() {
        increment();
        increment();
    }

    synchronized void increment() {
        count++;
    }

    synchronized int value() {
        return count;
    }
}
```

Đây là đoạn định nghĩa lớp minh họa. Khi gọi `obj.addTwo()`, thread đã giữ monitor `obj` và có thể gọi `obj.increment()` hai lần. Monitor theo dõi mức tái nhập; chỉ khi các vùng synchronized tương ứng được thoát hết thì monitor mới được nhả cho thread khác.

Tương tự, một static synchronized method của `ReentrantCounter` có thể gọi static synchronized method khác trên cùng `ReentrantCounter.class` mà không tự deadlock.

Nhưng **reentrancy chỉ áp dụng cho cùng monitor và cùng thread**. Việc đang giữ instance monitor không đồng nghĩa “tái nhập” class monitor; nếu method sau cần class monitor, đó vẫn là việc chiếm một **monitor khác** có thể bị thread khác giữ.

Khi một đoạn synchronized ném exception, JVM vẫn nhả monitor khi rời method/block theo cách phù hợp; không phải tự viết `unlock()` cho intrinsic lock. Điều này khác `ReentrantLock`, nơi cần `try/finally` để gọi `unlock()`.

## 11. Java Memory Model: happens-before phải cùng monitor

`synchronized` không chỉ cung cấp mutual exclusion. JMM còn có quy tắc:

> Một hành động **unlock** trên monitor M *happens-before* mọi hành động **lock M** về sau theo synchronization order.

Điều này giúp thread đọc nhìn thấy các thay đổi mà thread ghi đã thực hiện trước khi nhả **chính monitor đó**.

Ví dụ với monitor của instance:

```java
class Status {
    private int value;

    synchronized void set(int next) {
        value = next;
    }

    synchronized int get() {
        return value;
    }
}
```

Nếu cùng object `status`, thread A gọi `status.set(42)` hoàn thành trước khi thread B sau đó chiếm được monitor `status` để gọi `status.get()`, quan hệ đồng bộ hỗ trợ việc B quan sát giá trị được ghi. Nếu nhiều writer khác cập nhật sau đó, B có thể thấy một giá trị khác phù hợp thứ tự thực tế; không thể khẳng định mọi lần `get` đều trả đúng `42` mà bỏ qua các lần ghi khác.

**Sai monitor, thiếu bảo đảm:**

```java
class BadStatus {
    private static int value;

    synchronized void set(int next) {
        value = next;
    }

    synchronized int get() {
        return value;
    }
}
```

Nếu Thread A gọi `a.set(42)` còn Thread B gọi `b.get()` với `a != b`, hai method khóa **hai monitor khác nhau** dù đọc/ghi **cùng static field**. Không có quan hệ unlock/lock trên cùng monitor giữa hai lời gọi này. Cùng một từ khóa `synchronized` không đủ để kết luận rằng static field được công bố đúng.

Cũng không được suy luận rằng `volatile` hoặc `AtomicInteger` bảo vệ nhiều trường liên quan như một transaction. Chúng có thể cung cấp visibility hoặc atomicity theo từng biến/thao tác, nhưng bất biến nhiều trường cần một giao thức chung.

## 12. `wait()`, `notify()` và `notifyAll()` gắn với monitor cụ thể

Mỗi object monitor có tập thread đang chờ riêng. `wait` và `notify/notifyAll` là method của `Object`, không phải “tín hiệu toàn class”.

Ví dụ dùng **instance monitor**:

```java
synchronized (this) {
    while (!ready) {
        this.wait();
    }
}
```

Ví dụ dùng **class monitor**:

```java
synchronized (SignalBus.class) {
    while (!sharedReady) {
        SignalBus.class.wait();
    }
}
```

Đây là **hai đoạn minh họa riêng**, giả sử `ready` và `sharedReady` là các điều kiện được bảo vệ đúng, còn `SignalBus` là class đã khai báo. Mỗi lời gọi `wait()` yêu cầu luồng hiện tại **đang giữ monitor của chính object gọi wait**. Nếu không, nó ném `IllegalMonitorStateException`.

- `this.wait()` nhả và chờ trên monitor **instance `this`**.
- `SignalBus.class.wait()` nhả và chờ trên monitor **Class object `SignalBus.class`**.
- `this.notifyAll()` **không** đánh thức thread đang chờ `SignalBus.class.wait()`, và ngược lại.
- Thread sau khi nhận thông báo phải chiếm lại **đúng monitor tương ứng** trước khi tiếp tục từ `wait`.
- Nên kiểm tra điều kiện trong vòng `while` để xử lý spurious wakeup hoặc thay đổi điều kiện trước lúc thread lấy lại monitor.

Không gọi `wait/notify` trên monitor chỉ vì “cùng tên lớp”; phải khớp **object identity** giữa các thread tham gia. Với nhu cầu producer/consumer, các cấu trúc cao hơn như `BlockingQueue` hoặc `Condition` thường giảm lỗi giao thức.

## 13. Kế thừa, method hiding và `getClass()`: các bẫy ít rõ ràng

### 13.1. Instance synchronized method kế thừa vẫn khóa receiver thực

Nếu lớp `Base` có `synchronized void work()` và `Derived extends Base`, gọi method kế thừa trên `new Derived()` sẽ giữ monitor của **instance Derived đó**, không phải `Base.class`. Monitor của instance không thay đổi chỉ vì method được khai báo trong lớp cha.

Nếu hai method synchronized khác nhau (kế thừa hoặc khai báo tại lớp con) được gọi trên **cùng receiver**, chúng tranh cùng instance monitor `this`.

### 13.2. Static synchronized method thuộc lớp khai báo của method

Xét:

```java
class Base {
    static synchronized void baseGlobal() {
        // Monitor Base.class.
    }
}

class Derived extends Base {
    static synchronized void derivedGlobal() {
        // Monitor Derived.class.
    }
}
```

Đây là mã minh họa. `Base.baseGlobal()` và `Derived.derivedGlobal()` dùng **hai class monitor khác nhau**, nên không tự loại trừ lẫn nhau.

Nếu `Derived` chỉ thừa hưởng tên một static synchronized method khai báo ở `Base` mà không khai báo method mới, method được thực thi vẫn do `Base` khai báo và khóa `Base.class`. Static methods là **hiding** chứ không phải overriding đa hình như instance methods. Điều quan trọng là monitor của **lớp khai báo method static synchronized đang thực thi**.

### 13.3. `this.getClass()` không luôn bằng `DeclaringType.class`

Trong một instance method của `Base`, biểu thức `this.getClass()` có thể trả về `Derived.class` nếu receiver là object subclass. Trong khi đó, `Base.class` luôn chỉ class monitor của `Base`.

Vì vậy, dùng:

```java
synchronized (getClass()) {
    // Với một subclass, khóa Class object của subclass.
}
```

**không tương đương tổng quát** với:

```java
synchronized (Base.class) {
    // Luôn khóa đúng Base.class.
}
```

Hai đoạn trên là minh họa bên trong một instance method của `Base`. Nếu mục tiêu bảo vệ state `static` được khai báo chung ở `Base`, sử dụng `Base.class` hoặc một private static lock được quy định rõ, thay vì `getClass()` làm thay đổi lock theo runtime subclass.

### 13.4. Class loader tạo ranh giới bổ sung

Tên lớp giống nhau chưa chắc cùng một `Class` object nếu được nạp bởi hai class loader khác nhau. `synchronized(MyType.class)` điều phối các thread truy cập **cùng định nghĩa runtime của MyType**, không điều phối mọi process hay mọi class loader có tên đó.

Dữ liệu thật sự dùng chung ngoài một JVM không được bảo vệ chỉ bằng `static synchronized`. Muốn điều phối giữa các JVM/process cần cơ chế tại tầng tài nguyên chia sẻ như giao dịch cơ sở dữ liệu, khóa phân tán hoặc giao thức phù hợp — ngoài phạm vi intrinsic monitor Java.

## 14. Khi nào dùng monitor riêng thay vì khóa trực tiếp `this` hoặc `Class`?

Có thể chọn một lock **private, final và không công bố**:

```java
class Inventory {
    private final Object instanceLock = new Object();
    private static final Object CLASS_LOCK = new Object();

    private int localUnits;
    private static int globalUnits;

    void addLocal(int n) {
        synchronized (instanceLock) {
            localUnits += n;
        }
    }

    static void addGlobal(int n) {
        synchronized (CLASS_LOCK) {
            globalUnits += n;
        }
    }
}
```

Đây là mã minh họa. Ưu điểm:

- `instanceLock` là một object riêng cho từng `Inventory`; nó bảo vệ state của từng instance giống ý định khóa `this`.
- `CLASS_LOCK` là một object duy nhất theo runtime class definition; nó bảo vệ shared static state.
- Caller bên ngoài thường **không thể tự ý chiếm** các private lock này, khác với `synchronized(inventory)` hoặc `synchronized(Inventory.class)`, vốn có thể do code bên ngoài thực hiện. Điều này giảm nguy cơ khóa bị dùng chung ngoài ý muốn.
- Có thể tách nhiều nhóm state độc lập theo những private lock khác nhau để giảm contention, nhưng cần thiết kế rõ invariant xuyên nhóm.

**Bẫy lớn:** không tạo `new Object()` trong mỗi lần gọi method rồi synchronize nó, nếu ý định là các lời gọi phải chặn nhau:

```java
void wrong() {
    Object localLock = new Object();
    synchronized (localLock) {
        // Mỗi lời gọi giữ một lock mới, không bảo vệ state chung.
    }
}
```

Tương tự, nếu muốn tất cả instances chia sẻ một monitor, `private final Object lock` **không đủ** vì mỗi instance sẽ có object lock riêng. Phải dùng `private static final Object LOCK` hoặc một object lock chung được truyền có kiểm soát.

Không dùng `String` literal, object được intern, kiểu boxed số nguyên hoặc các object toàn cục không thuộc quyền sở hữu của bạn làm monitor. Chúng có thể vô tình được chia sẻ ở nơi khác, tạo contention/deadlock khó truy vết. Hãy chọn lock object riêng và có owner rõ ràng.

## 15. Tác động hiệu năng và mức độ cạnh tranh

**Instance locking** thường cho phép concurrency tốt khi mỗi instance chứa state độc lập. Một dịch vụ có 1.000 tài khoản độc lập có thể cập nhật nhiều tài khoản cùng lúc thay vì chờ một class lock chung. Nhưng nếu mỗi thao tác phải cập nhật một tổng chung, vẫn cần một cơ chế bảo vệ tổng đó.

**Class locking** serialize các lời gọi cần cùng class monitor. Nó đơn giản khi các thay đổi tác động tới state toàn cục của class, nhưng có thể trở thành nút thắt nếu code đang giữ khóa thực hiện I/O chậm, `sleep`, chờ Future, gọi callback ngoài hoặc làm việc CPU lớn.

Một số nguyên tắc thực hành:

- **Giữ critical section ngắn nhất có thể nhưng đủ rộng** để bảo vệ toàn bộ invariant; không chia nhỏ quá mức làm hở trạng thái trung gian.
- **Không ngủ trong synchronized** để chờ thread khác cập nhật state được bảo vệ cùng monitor: `Thread.sleep()` không nhả monitor. Nếu chờ condition theo monitor, dùng `wait()` đúng giao thức; thông thường nên cân nhắc các primitive `java.util.concurrent`.
- **Không giả định class lock luôn chậm hơn instance lock.** Tốc độ phụ thuộc contention, kích thước critical section, JVM và workload.
- **Dùng benchmark đúng cách** như JMH khi cần đo throughput/latency; một vài lần `System.nanoTime` có thể bị tác động bởi JIT, warmup và lịch chạy.
- **Chọn theo phạm vi dữ liệu**, không theo cảm giác “global” của một method. Static method có thể làm việc với state riêng được truyền vào; instance method có thể sửa state static. Danh tính khóa phải khớp invariant, không phải modifier của method bằng mọi giá.

## 16. Quy tắc chọn khóa theo bài toán

| Trạng thái cần bảo vệ | Khóa thường hợp lý | Vì sao | Lưu ý |
| --- | --- | --- | --- |
| `balance` thuộc một đối tượng `Account` | Instance monitor `this` hoặc private instance lock | Các thao tác trên cùng account loại trừ nhau; account khác có thể độc lập | Reader cũng phải dùng cùng lock |
| Bộ đếm `static nextId` chia sẻ nhiều instance | Class monitor, private static lock hoặc atomic counter | Tất cả writer thống nhất cùng cơ chế | `synchronized instanceMethod` trên nhiều instances không đủ |
| Hai trường instance phải đổi cùng nhau | Một monitor bảo vệ **cả cập nhật và snapshot đọc** | Không đọc lẫn thế hệ dữ liệu | Không chỉ khóa từng getter riêng |
| Một cache static mutable dùng chung | Private static lock hoặc concurrent collection phù hợp | Phạm vi chia sẻ cấp class | Kiểm tra invariant cần atomic trên nhiều entry hay không |
| State mutable nằm trong một service singleton | Lock của đúng service object có thể đủ | Tất cả callers dùng cùng receiver | Nếu sau này có nhiều instance cùng sửa static field sẽ khác |
| Hai accounts cần chuyển tiền như một giao dịch | Giao thức khóa cả hai accounts theo thứ tự cố định, hoặc một coordinator lock | Phải bảo vệ invariant xuyên accounts | Tránh deadlock và thất bại giữa chừng |
| Config được thay nguyên phiên bản | Immutable snapshot + `volatile`/`AtomicReference` | Reader có thể đọc không cần monitor cho từng field | Reader lấy snapshot một lần; nhiều writer cần CAS/lock |

Không có quy tắc máy móc “mọi static field phải static synchronized” hoặc “mọi instance field chỉ dùng synchronized method”. Cần xác định **đâu là state dùng chung, ai có thể chạm vào nó, invariant nào cần đúng và mọi reader/writer có dùng cùng một giao thức hay không**.

## 17. Lỗi phổ biến và cách nhận diện

### 17.1. Mỗi instance tự khóa, nhưng cùng cập nhật một static field

Dấu hiệu là một method `synchronized` không static chứa `++staticCounter`, và method được gọi trên nhiều receiver khác nhau. Có thể gây lost update. Sửa bằng class lock/private static lock, `AtomicInteger` nếu chỉ là biến đếm, hoặc tái thiết kế owner của state.

### 17.2. Static synchronized method và instance synchronized method được tưởng là chặn nhau

Chúng không chặn nhau chỉ vì thuộc cùng class. Static method khóa `Class` object; instance method khóa receiver. Nếu cả hai sửa cùng state, cần cho chúng dùng **cùng monitor**.

### 17.3. Getter chỉ đồng bộ riêng lẻ khi cần snapshot nhiều trường

Hai getter `synchronized` được gọi liên tiếp vẫn nhả monitor ở giữa. Writer có thể cập nhật giữa hai getter. Cần một method `snapshot()` đọc cả nhóm field dưới **một critical section** hoặc immutable snapshot được công bố nguyên tử.

### 17.4. Khóa object khác với object mà thread khác khóa

`synchronized(this)` trong hai object khác nhau là hai monitor. `synchronized(new Object())` trong từng lời gọi cũng là các monitor khác nhau. Nếu cần cùng lock, hãy bảo đảm **cùng identity**, không chỉ cùng class/type của object.

### 17.5. Khóa quá rộng và gọi code không kiểm soát

Giữ class monitor khi gọi network, database, callback hoặc `join()` lâu có thể chặn mọi thread khác cần cùng monitor. Tính toán tốn kém nên đưa ra ngoài critical section khi không làm hở invariant. Cần kiểm tra thứ tự lock ở mọi đường gọi.

### 17.6. Dùng `getClass()` và nghĩ luôn khóa base class

Với subclass, `getClass()` trả runtime class. Nếu muốn khóa state static của base class, tham chiếu đúng `Base.class` hoặc private static lock của base.

### 17.7. Quên khả năng deadlock khi dùng hai monitor

Thread A lấy instance rồi class, thread B lấy class rồi instance. Cần một thứ tự toàn cục cho việc chiếm nhiều khóa, hoặc giảm về một lock bảo vệ invariant cần thiết.

### 17.8. Nhầm `volatile` với khóa

`volatile` cung cấp visibility/thứ tự đối với biến đó nhưng không khóa một read-modify-write như `counter++`, càng không biến nhiều trường thành transaction. Cần synchronized/atomic operation hoặc cấu trúc khác tương ứng.

## 18. Bài tập tự kiểm tra

**Câu 1.** Hai đối tượng `a` và `b` của cùng lớp có method `synchronized void f()`. Thread A gọi `a.f()`, thread B gọi `b.f()`. Hai thread có buộc phải lần lượt vào method không?

**Trả lời:** Không. Mỗi method lấy monitor của receiver; `a != b` nên hai monitor khác nhau. Chúng có thể chạy đồng thời, trừ khi có cơ chế đồng bộ khác.

**Câu 2.** `static synchronized void g()` khóa object nào?

**Trả lời:** Monitor của đối tượng `Class` đại diện cho lớp **khai báo method `g` đang thực thi**. Ví dụ `Ledger.g()` khai báo ở `Ledger` sẽ khóa `Ledger.class`, không phải một instance của `Ledger`.

**Câu 3.** Thread A ở trong `synchronized(a)`, thread B muốn gọi `static synchronized h()` của lớp `A`. Có tự chặn nhau không?

**Trả lời:** Không nếu `a` là instance `A` bình thường: `a` và `A.class` khác monitor. Nếu code B còn cần một monitor khác đang bị giữ thì nó vẫn có thể chờ vì nguyên nhân riêng.

**Câu 4.** Vì sao `synchronized void inc() { ++sharedStatic; }` có thể sai nếu dùng nhiều instance?

**Trả lời:** Mỗi instance khóa chính nó, nhưng tất cả sửa cùng static field. Hai thread dùng hai instance không chặn nhau, nên read-modify-write có thể mất cập nhật.

**Câu 5.** Một thread giữ `Ledger.class` rồi gọi một synchronized instance method của `ledger`. Có chắc chắn đó là reentrant acquisition không?

**Trả lời:** Không. Đây là hai monitor khác nhau. Reentrancy chỉ áp dụng khi thread lấy lại **cùng monitor** mà nó đã sở hữu.

**Câu 6.** `synchronized(Base.class)` và `synchronized(this.getClass())` có luôn giống nhau trong method của `Base` không?

**Trả lời:** Không. Nếu receiver là `Derived extends Base`, `this.getClass()` có thể là `Derived.class`. Hai class monitors khác nhau.

**Câu 7.** Nếu mọi writer lấy class monitor nhưng reader chỉ lấy instance monitor để đọc static field, có bảo đảm happens-before qua lock không?

**Trả lời:** Không chỉ nhờ hai vùng synchronized đó. Unlock/lock phải thuộc cùng monitor để tạo cạnh đồng bộ tương ứng; reader cần cùng class lock hoặc một cơ chế công bố khác có hợp đồng rõ.

**Câu 8.** Khi cần chuyển tiền giữa hai đối tượng `Account` khác nhau, chỉ khóa account nguồn có đủ không?

**Trả lời:** Thường không nếu thao tác cập nhật cả hai account và cần invariant xuyên chúng. Cần giao thức bảo vệ cả hai object theo thứ tự tránh deadlock, hoặc khóa coordinator và các API đọc/ghi tuân thủ cùng hợp đồng.

## 19. Tổng kết

**Instance lock** là monitor của **một đối tượng cụ thể**: `synchronized instanceMethod()` và `synchronized(this)` khóa receiver. **Class lock** là monitor của **`Class` object**: `static synchronized staticMethod()` và `synchronized(DeclaringClass.class)` khóa một class definition cụ thể. Hai instance có hai monitor khác nhau; instance monitor và class monitor cũng khác nhau.

Sự an toàn không đến từ việc “chỗ nào cũng có synchronized” mà đến từ việc **mọi thao tác liên quan cùng bảo vệ một invariant bằng đúng monitor hoặc cùng một giao thức đồng bộ**. Khi chọn khóa, hãy xác định phạm vi state thực sự chia sẻ, sự tương ứng giữa reader/writer, quan hệ happens-before, mức contention và thứ tự lấy khóa để tránh deadlock. Đó là nền tảng để dùng synchronization chính xác trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-004">Reentrant synchronization là gì?</a>

<details>
<summary>Xem chi tiết</summary>

# Reentrant synchronization là gì trong Java?

## 1. Định nghĩa và lý do cần reentrancy

**Reentrant synchronization** (đồng bộ tái nhập) có nghĩa: **một thread đang sở hữu một khóa được phép lấy lại chính khóa đó** mà không tự chặn mình. Quy tắc loại trừ các thread khác vẫn giữ nguyên: thread khác không thể chiếm khóa khi owner chưa nhả hết các lần giữ.

Java có hai cơ chế quan trọng:

- **Intrinsic monitor của `synchronized`:** một thread có thể vào thêm những khối hoặc phương thức synchronized sử dụng **cùng monitor**.
- **`ReentrantLock` trong `java.util.concurrent.locks`:** cùng thread có thể gọi `lock()` lồng nhau; khóa theo dõi số lần giữ (*hold count*) và phải có số lần `unlock()` tương ứng.

Reentrant giúp phương thức có khóa gọi helper có khóa, gọi đệ quy hoặc gọi phương thức của lớp cha mà **không bị tự kẹt khi lấy lại cùng khóa**. Nó không có nghĩa nhiều thread được đồng thời chạy vùng critical section.

**Điểm quyết định:** reentrancy gắn với **cùng một thread và cùng một đối tượng khóa**, không gắn với tên biến, tên phương thức hay việc hai object cùng kiểu. Thread giữ monitor của instance A vẫn có thể phải chờ monitor của instance B.

## 2. Monitor của `synchronized` tái nhập như thế nào?

Các dạng thường gặp:

```java
// Snippet: lockObject là tham chiếu tới đối tượng khóa đã khai báo.
synchronized (lockObject) {
    // Vùng tới hạn.
}
```

- `synchronized (object)` lấy monitor của **object**.
- Phương thức instance `synchronized` lấy monitor của `this`.
- Phương thức `static synchronized` lấy monitor của `Class` object, ví dụ `Account.class`. Monitor class **khác** monitor instance.

JVM quản lý quyền sở hữu và số tầng tái nhập. Khi owner vào cùng monitor thêm lần nữa, mức giữ tăng. Khi thoát một tầng, mức giữ giảm. Thread khác chỉ có thể lấy monitor sau khi owner đã thoát hết các tầng.

**Khác với khóa tường minh:** thoát khối hoặc phương thức synchronized tự nhả monitor đúng số mức, kể cả khi `return` hoặc exception. Không cần và không thể gọi một `unlock()` cho intrinsic monitor như với `ReentrantLock`.

### Ví dụ Java 17+ hoàn chỉnh

Tạo tệp `IntrinsicReentrancy.java`:

```java
public class IntrinsicReentrancy {
    private int balance = 10;

    public synchronized void withdraw(int amount) {
        System.out.println("Outer entered");
        decrease(amount);
        System.out.println("Outer resumed");
    }

    private synchronized void decrease(int amount) {
        balance -= amount;
        System.out.println("Inner balance: " + balance);
    }

    public synchronized int balance() {
        return balance;
    }

    public static void main(String[] args) {
        IntrinsicReentrancy account = new IntrinsicReentrancy();
        account.withdraw(3);
        System.out.println("Final balance: " + account.balance());
    }
}
```

Chạy:

```bash
javac IntrinsicReentrancy.java
java IntrinsicReentrancy
```

Đầu ra:

```text
Outer entered
Inner balance: 7
Outer resumed
Final balance: 7
```

Thread main lấy monitor của `account` khi vào `withdraw`, rồi tái nhập chính monitor đó khi gọi `decrease`. Khi `decrease` trả về, `withdraw` **vẫn giữ monitor**; chỉ khi `withdraw` kết thúc, monitor mới được giải phóng hoàn toàn đối với lời gọi này. Phương thức `balance` dùng cùng monitor để đọc.

Ví dụ chỉ minh họa cơ chế reentry; `withdraw` không kiểm tra số dư, nên không phải thiết kế giao dịch tài chính đầy đủ. Khi thread khác gọi phương thức synchronized của **cùng account**, thread đó phải chờ owner thoát hết các tầng.

### Reentry qua đệ quy và kế thừa

Một phương thức synchronized có thể tự gọi hoặc gọi tiếp helper synchronized trên cùng receiver. Mỗi tầng đệ quy lấy lại monitor được sở hữu bởi cùng thread. Reentrancy **không** ngăn đệ quy vô hạn hoặc `StackOverflowError`, chỉ tránh self-deadlock tại monitor.

Lớp con gọi `super.someSynchronizedMethod()` trên cùng receiver cũng tái nhập được nếu cả hai khóa `this`. Nhưng `synchronized(other)` dùng monitor khác; static synchronized cũng không dùng monitor của receiver.

## 3. `ReentrantLock`, owner thread và hold count

`ReentrantLock` là khóa tường minh có thể tái nhập, hỗ trợ thêm `tryLock()`, `lockInterruptibly()`, `newCondition()`, và `getHoldCount()`.

Mẫu an toàn khi lấy khóa:

```java
// Snippet: lock là ReentrantLock đã khai báo.
lock.lock();
try {
    // Thao tác trên trạng thái được bảo vệ.
} finally {
    lock.unlock();
}
```

`finally` bảo đảm unlock cả khi có exception. Chỉ gọi `unlock()` nếu acquisition đã thành công. Không nên đặt `unlock()` trong một `finally` chạy sau khi `tryLock()` trả về false.

| Hành động của thread T | Hold count của T | Hệ quả |
| --- | --- | --- |
| T lấy lock đang rảnh | 1 | T trở thành owner |
| T gọi `lock()` lại cùng lock | 2 | T vẫn là owner |
| T gọi `unlock()` một lần | 1 | Thread khác vẫn phải đợi |
| T gọi `unlock()` lần thứ hai | 0 | Khóa được giải phóng |

`getHoldCount()` đếm số lần **thread hiện tại** giữ khóa. `isHeldByCurrentThread()` kiểm tra owner của thread gọi. Không nên suy ra một snapshot về trạng thái thread khác từ những phương thức này.

### Ví dụ Java 17+ với hold count

Tạo tệp `ReentrantLockDepth.java`:

```java
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockDepth {
    private final ReentrantLock lock = new ReentrantLock();

    private void outer() {
        lock.lock();
        try {
            System.out.println("Outer: " + lock.getHoldCount());
            inner();
            System.out.println("Back in outer: " + lock.getHoldCount());
        } finally {
            lock.unlock();
        }
        System.out.println("After outer: " + lock.getHoldCount());
    }

    private void inner() {
        lock.lock();
        try {
            System.out.println("Inner: " + lock.getHoldCount());
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        new ReentrantLockDepth().outer();
    }
}
```

Chạy:

```bash
javac ReentrantLockDepth.java
java ReentrantLockDepth
```

Đầu ra:

```text
Outer: 1
Inner: 2
Back in outer: 1
After outer: 0
```

Chỉ một thread thực hiện mã, nhưng nó lấy cùng lock hai lần. `inner` nhả một mức, `outer` nhả mức còn lại. Nếu `inner` quên unlock, sau khi `outer` unlock khóa vẫn còn hold count 1, khiến thread khác có thể chờ mãi. Nếu thread không phải owner gọi `unlock()`, hoặc unlock quá số lần sở hữu, `ReentrantLock` ném `IllegalMonitorStateException`.

**Kết luận thực hành:** mỗi lần `lock()` thành công cần một `unlock()` tương ứng. Cả `synchronized` và `ReentrantLock` đều tái nhập, nhưng một bên có lexical scope tự nhả, bên kia cần lập trình viên cân bằng thủ công.

## 4. Reentrancy không làm mất mutual exclusion

Tái nhập chỉ được phép cho **thread chủ sở hữu**, chứ không trao quyền cho thread khác chen vào. Xem một class với monitor private:

```java
final class Counter {
    private final Object mutex = new Object();
    private int count;

    public void incrementTwice() {
        synchronized (mutex) {
            incrementOne();
            incrementOne();
        }
    }

    private void incrementOne() {
        synchronized (mutex) {
            count++;
        }
    }

    public int get() {
        synchronized (mutex) {
            return count;
        }
    }
}
```

Đây là class phụ trợ, chưa có `main`. Khi thread T vào `incrementTwice`, T giữ mutex. Hai lần gọi `incrementOne` lấy lại cùng mutex, nên T chạy được. Một thread U cố `get` hay `incrementTwice` phải đợi cho đến khi T nhả hết.

Dùng `private final Object mutex` giúp giữ quyền kiểm soát lock của object, tránh caller cố tình hoặc vô tình giữ monitor bên ngoài. Nếu helper khóa **một đối tượng khác**, đó không phải reentry và có thể bị chặn.

### Ví dụ Java 17+: nhiều worker và khóa lồng nhau

Tạo tệp `NestedCounter.java`:

```java
public class NestedCounter {
    private final Object mutex = new Object();
    private int count;

    public void addBatch(int size) {
        synchronized (mutex) {
            for (int i = 0; i < size; i++) {
                incrementOne();
            }
        }
    }

    private void incrementOne() {
        synchronized (mutex) {
            count++;
        }
    }

    public int get() {
        synchronized (mutex) {
            return count;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        NestedCounter counter = new NestedCounter();
        Thread[] workers = new Thread[4];

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> counter.addBatch(1_000));
            workers[i].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        System.out.println("Final count: " + counter.get());
    }
}
```

Chạy:

```bash
javac NestedCounter.java
java NestedCounter
```

Đầu ra:

```text
Final count: 4000
```

Bốn thread cập nhật cùng bộ đếm; thứ tự scheduling có thể khác nhau nhưng kết quả luôn là 4.000 khi tất cả hoàn tất. Monitor chung ngăn hai thread cùng sửa `count`; trong mỗi thread, helper được tái nhập cùng monitor. `join()` giúp main đợi đủ worker trước khi đọc kết quả.

Việc khóa lồng nhau trên từng bước ở đây là có chủ đích để minh họa. Với vòng lặp lớn, có thể viết một helper yêu cầu caller đã giữ khóa để giảm overhead, nhưng phải thực thi nhất quán tiền điều kiện đó. Reentrancy không có nghĩa mọi helper đều nên tự khóa.

## 5. Self-deadlock và giới hạn của reentrancy

**Self-deadlock** có thể xuất hiện nếu một thread đang giữ tài nguyên rồi lại chờ chính tài nguyên ấy bằng một cơ chế không tái nhập. Ví dụ `Semaphore(1)` không theo dõi owner như `ReentrantLock`: một thread `acquire()` hai lần mà không có `release()` ở giữa sẽ phải chờ permit thứ hai, có thể chặn vô thời hạn. Không nên chạy ví dụ gây treo này như một chương trình demo.

Với monitor Java hoặc `ReentrantLock`, owner có thể lấy lại **chính khóa đang giữ**, nên tránh được dạng self-deadlock do reentry.

Nhưng **reentrancy không tránh được deadlock giữa các khóa khác nhau**:

```text
Thread T1                  Thread T2
---------                  ---------
giữ lock A                 giữ lock B
chờ lock B                 chờ lock A
       \                    /
          vòng chờ khóa
```

A và B có thể đều là `ReentrantLock`. T1 không sở hữu B; T2 không sở hữu A. Vì vậy reentrancy không giúp họ vượt qua vòng chờ.

Các cách phòng tránh gồm áp dụng thứ tự lấy nhiều khóa thống nhất, tránh chờ I/O hoặc gọi callback khi đang giữ nhiều lock, hạn chế critical section, cân nhắc `tryLock(timeout, unit)` nếu có đường phục hồi, và phân tích thread dump/JFR khi xảy ra bế tắc.

**Trường hợp dễ nhầm:** thread T giữ lock L rồi gọi `worker.join()` trong khi worker cần L để kết thúc. T chờ worker, worker chờ L do T giữ: đây cũng là deadlock dù L reentrant. Không giữ khóa mà worker cần khi chờ worker.

## 6. So sánh `synchronized` và `ReentrantLock`

| Đặc điểm | `synchronized` | `ReentrantLock` |
| --- | --- | --- |
| Có tái nhập | Có | Có |
| Khóa của | Monitor object hoặc `Class` object | Instance `ReentrantLock` |
| Giải phóng khi exception | Tự nhả khi thoát scope | Phải `unlock()` trong `finally` |
| Số lần giữ | JVM quản lý, không có API `getHoldCount` | Có `getHoldCount()` |
| Lấy khóa không chờ | Không có `tryEnter` của cú pháp | `tryLock()` |
| Lấy khóa có timeout | Không có monitor acquisition timeout | `tryLock(long, TimeUnit)` |
| Lấy khóa có thể interrupt | Không có dạng acquisition tương đương | `lockInterruptibly()` |
| Điều kiện chờ | `wait()`, `notify()`, `notifyAll()` | `Condition.await()`, `signal()` |
| Fairness | Không cấu hình trực tiếp | Có tùy chọn `new ReentrantLock(true)` |

**Fair lock không đảm bảo OS scheduling công bằng hay thứ tự hoàn thành tác vụ**. Ngay trên fair `ReentrantLock`, `tryLock()` không timeout có thể lấy ngay khi khóa đang rảnh dù còn thread đang chờ. Không dùng fairness để chứng minh một yêu cầu nghiệp vụ phải hoàn thành trước yêu cầu khác.

Nếu chỉ cần vùng bảo vệ ngắn và không cần API bổ sung, `synchronized` thường dễ duy trì. Dùng `ReentrantLock` khi thật sự cần timeout, khả năng hủy việc đợi lấy khóa hoặc các condition độc lập.

## 7. `wait()`, `Condition.await()` và reentrant lock

Khi thread đang giữ lock rồi cần **đợi một điều kiện**, phải phân biệt việc **tái nhập khóa** với việc **nhả khóa tạm để chờ**:

- `Object.wait()` cần được gọi khi thread đang sở hữu đúng monitor object. Khi đợi, thread nhả monitor **đó** với số mức tái nhập thích hợp và phải lấy lại trước khi `wait()` trở về. Nó không tự nhả các monitor khác.
- `Condition.await()` do `ReentrantLock` tạo qua `newCondition()` sẽ nhả mức giữ của lock liên kết khi chờ, rồi lấy lại theo hợp đồng Condition trước khi trở về. Phải giữ lock trước khi await.
- `Thread.sleep()` **không nhả monitor hay `ReentrantLock`** mà thread đang giữ.
- `notify()` hoặc `signal()` không chuyển quyền sở hữu lock ngay tức khắc; thread được đánh thức còn phải lấy lại lock.
- `wait()`/`await()` có thể thức dậy ngoài dự kiến (*spurious wakeup*); luôn kiểm tra điều kiện bằng **`while`**.

Ví dụ ý tưởng:

```java
// Snippet: mutex, ready và consume() được khai báo trong class.
synchronized (mutex) {
    while (!ready) {
        mutex.wait();
    }
    consume();
}
```

Đây là snippet, **không phải chương trình biên dịch độc lập**. Thread phát tín hiệu phải sửa `ready` dưới cùng mutex rồi gọi `mutex.notifyAll()` phù hợp. Vòng `while` bảo vệ điều kiện ngay cả khi thức dậy bất thường hoặc thread khác đã thay đổi state.

## 8. Callback tái nhập và invariant

Tái nhập có một rủi ro dễ bỏ qua: code đang giữ lock gọi một callback do caller cung cấp. Callback chạy **trên cùng thread** và có thể gọi ngược API dùng cùng lock; vì lock reentrant, callback **không bị chặn**. Nó có thể thay đổi state khi thao tác bên ngoài vẫn đang giả định state chưa đổi.

```java
// Snippet minh họa; mutex, state, callback và use() đã được khai báo.
synchronized (mutex) {
    int previous = state;
    callback.run();
    use(previous); // Có thể không còn phù hợp nếu callback đã đổi state.
}
```

Lock ngăn **thread khác** chen vào, nhưng không cấm chính owner tái nhập qua một đường gọi không dự kiến. Callback còn có thể chặn I/O, lấy khóa thứ hai, gọi lại đệ quy vô hạn hoặc ném exception.

Để an toàn: không chạy code không kiểm soát trong lock nếu không cần thiết; định nghĩa rõ hợp đồng callback có được reenter không; duy trì invariant ở mọi thời điểm callback quan sát; cân nhắc chụp snapshot rồi gọi callback ngoài lock nếu nghiệp vụ cho phép. Tuy nhiên nếu phải thực hiện check–then–act nguyên tử, **không** tùy tiện thả lock giữa check và update.

## 9. Các hiểu nhầm về phạm vi khóa

- Hai instance cùng class có **monitor instance khác nhau**. `accountA` và `accountB` không tự loại trừ nhau.
- `static synchronized` khóa `SomeClass.class`; phương thức instance synchronized khóa `this`. Hai khóa khác nhau dù code trong cùng class.
- Hai đối tượng `new ReentrantLock()` là hai khóa khác nhau; việc đặt cùng tên biến không liên quan.
- Dùng đối tượng công khai như String literal làm monitor có thể gây chia sẻ khóa không dự kiến; ưu tiên `private final Object mutex = new Object()` cho trạng thái riêng.
- Monitor trong một JVM không tự bảo vệ dữ liệu bị một tiến trình khác ghi. Khi bất biến trải qua file/database/service, phải dùng giao thức cập nhật tại chính nguồn dữ liệu đó.
- Reentrancy không thay thế chính sách bảo vệ mọi đường đọc/ghi. Dù helper tái nhập đúng, một getter đọc state mutable ngoài lock vẫn có thể là data race.

## 10. Những lỗi thường gặp

1. **“Một phương thức synchronized không gọi được một phương thức synchronized khác.”** Sai nếu cùng owner thread và cùng monitor.
2. **“Reentrant cho phép hai thread cùng giữ khóa.”** Sai; chỉ thread đang sở hữu được tái nhập.
3. **“Lock hai lần thì unlock một lần là đủ.”** Sai đối với `ReentrantLock`; hold count phải về 0 mới giải phóng.
4. **“Thread khác có thể unlock thay để sửa deadlock.”** Sai; `ReentrantLock` yêu cầu thread chủ sở hữu, ngược lại ném `IllegalMonitorStateException`.
5. **“Mọi deadlock biến mất khi dùng `ReentrantLock`.”** Sai với hai khóa khác nhau hoặc vòng phụ thuộc chờ.
6. **“Sleep sẽ thả monitor để thread khác chạy.”** Sai: `sleep` không tự giải phóng monitor.
7. **“Static và instance synchronized khóa cùng object.”** Sai: khóa class và khóa receiver khác nhau.
8. **“Fairness của `ReentrantLock(true)` cam kết thứ tự xử lý.”** Sai: không bảo đảm scheduler hay thời điểm hoàn tất.
9. **“Callback không thể sửa state khi callback được gọi dưới lock.”** Sai: callback cùng thread có thể tái nhập.
10. **“Reentrant đồng nghĩa thread-safe cho mọi dữ liệu.”** Sai: tính đúng còn phụ thuộc invariant, phạm vi khóa và công bố dữ liệu.

## 11. Checklist lựa chọn và sử dụng

1. Xác định **đúng đối tượng khóa**: monitor `this`, `Class` object, private mutex hay `ReentrantLock`.
2. Xác định **owner thread**: chỉ thread đã giữ **cùng khóa** mới tái nhập.
3. Phân tích các lời gọi lồng nhau: helper, đệ quy, superclass method có sử dụng cùng khóa không?
4. Với `ReentrantLock`, mỗi `lock()` thành công phải có `unlock()` tương ứng trong `finally`.
5. Bảo vệ **toàn bộ invariant**: lock lồng nhau không tự sửa được giao thức check–then–act bị chia nhỏ.
6. Tránh vòng phụ thuộc nhiều khóa; quy định thứ tự lấy lock ổn định; không chờ worker đang cần khóa mình giữ.
7. Xem xét timeout, interrupt và lỗi khi chờ khóa; chỉ unlock nếu đã acquire thành công.
8. Không cho callback ngoài kiểm soát tùy ý thay đổi state trung gian; xem xét nguy cơ tái nhập bất ngờ.
9. Kiểm thử nhiều thread, đường exception và lộ lock; không dựa vào thứ tự scheduler.
10. Chỉ chọn `ReentrantLock` khi cần khả năng bổ sung; `synchronized` đủ cho nhiều vùng critical section đơn giản.

**Ghi nhớ:** đồng bộ tái nhập giúp **cùng thread lấy lại cùng khóa** để gọi phương thức lồng nhau hoặc đệ quy mà không tự chặn, trong khi vẫn loại trừ thread khác. Java `synchronized` tự quản lý các tầng monitor; `ReentrantLock` theo dõi hold count và đòi unlock cân bằng. Đây là tính chất của khóa, còn thread safety phụ thuộc vào thiết kế invariant và giao thức truy cập trạng thái.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-005">Mutual exclusion và memory visibility của monitor</a>

<details>
<summary>Xem chi tiết</summary>

# Mutual exclusion và memory visibility của monitor trong Java

> **Phạm vi:** hai bảo đảm nền tảng của cơ chế `synchronized` trong Java SE: **mutual exclusion** (loại trừ lẫn nhau khi thao tác trên state dùng chung) và **memory visibility/ordering** theo Java Memory Model. Nội dung tập trung vào Java nói chung, độc lập với các cơ chế concurrency dành riêng cho Spring.

## 1. Mục tiêu và ý tưởng cốt lõi

Một chương trình nhiều thread có thể sai theo **hai cách khác nhau** ngay cả khi mỗi thread đều chạy đúng những dòng mã được viết:

- **Race về thao tác:** nhiều thread xen kẽ bước đọc–tính–ghi hoặc cùng lúc thay đổi những trường phải nhất quán. Ví dụ hai phép `counter++` mất một lần tăng.
- **Thiếu quan hệ visibility/ordering:** một thread đã ghi giá trị nhưng thread khác không có cơ chế đồng bộ thích hợp để bảo đảm việc đọc thấy những ghi đó theo yêu cầu của chương trình.

Java cung cấp **intrinsic monitor** thông qua `synchronized` để giải quyết đồng thời hai vấn đề khi **tất cả truy cập liên quan tuân thủ cùng một giao thức khóa**:

1. **Mutual exclusion:** chỉ một thread được sở hữu một monitor tại một thời điểm; do đó các critical section dùng cùng monitor không chạy chồng lên nhau.
2. **Memory visibility/ordering:** một lần **unlock** monitor `m` *synchronizes-with* các lần **lock sau đó trên chính monitor `m`** theo quy tắc Java Memory Model (JMM). Quan hệ này tham gia xây dựng *happens-before*.

Sau bài học, bạn cần hiểu được lúc nào một phần mã thực sự được bảo vệ, tại sao một reader phải dùng đúng monitor, cách đọc quan hệ *happens-before*, khi nào `synchronized` còn chưa đủ để bảo toàn invariant, và cách kiểm chứng bằng ví dụ Java 17+.

**Kết luận thiết kế quan trọng:** không đủ nếu chỉ nói “writer có synchronized”. Cần biết **đối tượng monitor nào** được dùng, **những thao tác nào** nằm bên trong critical section, và **reader lấy cùng monitor lúc nào**.

## 2. Một monitor gắn với một object cụ thể

Trong Java, mỗi object có một **intrinsic lock / monitor**. Biểu thức `synchronized (guard) { ... }` cần giành quyền sở hữu monitor của đúng object mà `guard` tham chiếu.

Nếu một thread đã giữ monitor đó, thread khác muốn vào vùng `synchronized` dùng cùng monitor phải chờ. Nếu hai đoạn code dùng **hai object khác nhau**, chúng không loại trừ nhau chỉ vì cùng đặt tên biến là `guard`.

Ví dụ minh họa:

```java
final class SharedCounter {
    private final Object monitor = new Object();
    private int value;

    void increment() {
        synchronized (monitor) {
            value++;
        }
    }

    int value() {
        synchronized (monitor) {
            return value;
        }
    }
}
```

Đây là một **class có thể biên dịch** trong file `SharedCounter.java`. `monitor` là cùng một object được cả hai method sử dụng; nó là `private final` để ngăn caller trực tiếp nắm monitor và ngăn code bình thường đổi tham chiếu.

Với mỗi instance `SharedCounter`, mọi lần `increment()` sẽ đi lần lượt qua critical section. Khi `value()` lấy monitor sau khi một `increment()` đã nhả, nó nhận được các bảo đảm memory visibility tương ứng.

**Hai instance khác nhau có monitor riêng.** Nếu tạo hai `new SharedCounter()`, mỗi instance có một `new Object()` và hai counter không loại trừ nhau; điều này thường là điều mong muốn khi state của chúng độc lập.

### Các dạng synchronized và monitor tương ứng

| Cú pháp | Monitor được lấy | Hệ quả |
|---|---|---|
| `synchronized (guard) { ... }` | Object do `guard` trỏ tới khi đánh giá biểu thức | Những vùng dùng **cùng object** phối hợp với nhau. |
| `synchronized void update() { ... }` trên instance | `this` | Các synchronized instance method của cùng instance dùng cùng monitor. |
| `static synchronized void update() { ... }` | Object `Class` của class chứa method | Các static synchronized method cùng class dùng monitor của `Class`. |
| `synchronized (SomeType.class) { ... }` | Object `SomeType.class` | Có thể phối hợp với static synchronized của cùng class tương ứng. |

Không được nhầm `static synchronized` với việc khóa **mọi instance**: nó khóa Class object, còn các instance method synchronized khóa từng instance. Vì vậy một static synchronized method và một instance synchronized method **không nhất thiết chặn nhau**.

**Reentrancy:** một thread đã sở hữu monitor được phép vào lại chính monitor đó, chẳng hạn một instance synchronized method gọi instance synchronized method khác trên cùng `this`. Quy tắc “mỗi monitor một owner” vẫn đúng: đây là **một owner** vào nhiều mức, không phải hai thread cùng vào.

## 3. Mutual exclusion: vì sao nó ngăn lost update?

Giả sử có:

```java
counter++;
```

Đây là một **biểu thức minh họa**, với `counter` là trường `int` trong class chứa nó. Ở mức hành vi, phép tăng gồm đọc giá trị cũ, tính giá trị mới, rồi ghi về. Hai thread có thể xen kẽ:

| Bước | Thread A | Thread B | `counter` |
|---|---|---|---|
| 0 | — | — | 0 |
| 1 | Đọc 0 | — | 0 |
| 2 | — | Đọc 0 | 0 |
| 3 | Ghi 1 | — | 1 |
| 4 | — | Ghi 1 | 1 |

Kết quả cuối là **1**, dù cả hai đã thực hiện một phép tăng; đây là **lost update**. Bảng là một lịch minh họa đơn giản, không mô tả toàn bộ hành vi bộ nhớ mà JMM cho phép đối với chương trình có data race.

Đặt toàn bộ thao tác trong một critical section chung:

```java
synchronized (guard) {
    counter++;
}
```

Đây cũng là **mã minh họa**, giả sử `guard` và `counter` là các trường đã khai báo. Nếu cả A và B dùng **cùng instance `guard`**, A phải hoàn thành critical section rồi nhả monitor trước khi B giành được (hoặc ngược lại). Vì thế, lần tăng thứ hai sẽ đọc giá trị sau lần tăng thứ nhất, không có lịch hai thread cùng đọc 0 rồi cùng ghi 1 **trong vùng được cùng khóa bảo vệ**.

Hai phần của bài toán “tăng số đếm” phải được chứng minh riêng:

- **Atomicity ở cấp thao tác:** đọc–tính–ghi nằm chung một critical section, không có writer khác cùng dùng monitor chen vào giữa.
- **Visibility khi quan sát:** reader dùng cùng monitor, hoặc có một quan hệ *happens-before* khác phù hợp; nếu reader truy cập trực tiếp bên ngoài synchronization thì không được suy ra bảo đảm ấy từ monitor của writer.

Một khóa chỉ loại trừ code **có dùng khóa đó**. `synchronized` không chặn một thread cố tình ghi trường trực tiếp từ code hoàn toàn không lấy monitor.

## 4. Memory visibility theo Java Memory Model

### Quy tắc release/acquire của monitor

Theo **JLS `17.4.4**, một hành động **unlock** trên monitor `m` *synchronizes-with* mọi hành động **lock tiếp theo trên cùng monitor `m`** trong synchronization order.

Cần đọc chính xác:

- **Cùng monitor:** phải đúng một object monitor, không phải hai object “tương đương” theo `equals()`.
- **Unlock và lock:** là hai synchronization actions, có ý nghĩa vượt ngoài việc scheduler cho phép thread chạy.
- **Tiếp theo trong synchronization order:** không có bảo đảm thread nào được lock trước theo thứ tự đến. Khi một lần lock thực sự nằm sau unlock tương ứng, ta có cạnh memory ordering được JMM quy định.

### Từ synchronizes-with đến happens-before

JMM định nghĩa quan hệ **happens-before (HB)** từ thứ tự chương trình (*program order*) và các cạnh *synchronizes-with*, có tính bắc cầu. Giả sử:

```text
Thread A                                Thread B
------------------------------          ----------------------------
synchronized (m) {                      synchronized (m) {
    data = 42;                              int observed = data;
} // unlock(m)                          } // unlock(m)

A: ghi data=42
    --program order--> A: unlock(m)
    --synchronizes-with--> B: lock(m) sau unlock của A
    --program order--> B: đọc data

Vậy ghi của A happens-before đọc của B.
```

Sơ đồ này **chỉ xét tình huống B lấy monitor sau A đã unlock**. Nếu B vào vùng synchronized trước A, B có thể thấy giá trị cũ. Một thread tạo ra sớm, gọi `start()` sớm hoặc bắt đầu chờ khóa sớm **không chứng minh** nó giành monitor trước.

HB cung cấp quy tắc về những ghi mà hành động đọc được phép quan sát. Nếu A ghi 42, B lock sau unlock của A và **không có một cập nhật hợp lệ khác đối với `data` xen giữa**, B không thể viện cớ “cache chưa kịp đồng bộ” để thấy giá trị cũ theo cách vi phạm HB.

**Không cần mô hình “unlock flush toàn bộ cache vật lý, lock reload toàn bộ RAM”.** Đó không phải hợp đồng của ngôn ngữ. JVM/JIT/CPU có nhiều cách triển khai barriers và tối ưu, miễn giữ các ràng buộc quan sát do JMM quy định.

### Hai tình huống cần phân biệt

**1. Dùng đúng monitor:**

```java
// Các field của một class:
private final Object guard = new Object();
private int result;

void writeResult(int value) {
    synchronized (guard) {
        result = value;
    }
}

int readResult() {
    synchronized (guard) {
        return result;
    }
}
```

Đây là các **thành viên của cùng một class**, không phải chương trình độc lập. Nếu `readResult` giành monitor sau khi `writeResult` đã nhả, giá trị ghi trước unlock đã được công bố cho reader theo HB (trừ khi có writer khác cập nhật hợp lệ trước lần đọc đó).

**2. Dùng khác monitor:**

```java
// Mã minh họa thiếu cùng giao thức đồng bộ:
private final Object writerGuard = new Object();
private final Object readerGuard = new Object();
private int result;

void writeResult(int value) {
    synchronized (writerGuard) {
        result = value;
    }
}

int readResult() {
    synchronized (readerGuard) {
        return result;
    }
}
```

Writer unlock `writerGuard`, còn reader lock `readerGuard`. Hai hành động đó **không** tạo cạnh *synchronizes-with* theo **quy tắc monitor cùng object**. Không có mutual exclusion giữa hai khối; chương trình còn có thể có data race nếu không tồn tại HB từ nguồn khác. Sửa bằng cách dùng **cùng monitor** cho mọi truy cập cần bảo vệ, hoặc đổi sang primitive có hợp đồng synchronization phù hợp.

## 5. Visibility không đồng nghĩa với một kết quả hay thứ tự cố định

Một ví dụ hay bị hiểu sai:

- A định ghi `data = 42` dưới monitor `m`.
- B định đọc `data` dưới cùng monitor `m`.
- Hai thread được khởi chạy gần nhau.

Cả hai đều dùng đúng monitor. Nhưng B có thể giành monitor **trước** khi A ghi; khi ấy B đọc giá trị ban đầu là hợp lệ. `synchronized` chỉ đảm bảo rằng các critical section **không chồng lên nhau**, và bảo đảm visibility cho lần lock xảy ra **sau** unlock của writer.

Nếu yêu cầu nghiệp vụ là **B phải đợi A công bố dữ liệu**, cần thêm protocol để phối hợp giai đoạn:

- Predicate `ready` được ghi/đọc dưới cùng monitor, kết hợp `wait()/notifyAll()`.
- Hoặc dùng `BlockingQueue`, `CountDownLatch`, `Future.get()` nếu API tương ứng khớp mục tiêu.

Không dùng `Thread.sleep(50)` như bằng chứng A đã xong: ngủ một khoảng thời gian **không tạo mối quan hệ HB cần thiết** và không bảo đảm thứ tự giữa các thread.

### Monitor bảo vệ tính nhất quán của nhiều trường

Giả sử ledger giữ invariant:

\[
\text{total} = 10 \times \text{updates}
\]

Nếu writer làm `total += 10` rồi `updates++` bên trong cùng monitor, một reader cũng lấy đúng monitor để đọc **cả hai trường** sẽ quan sát snapshot trước hoặc sau **một lần cập nhật nguyên vẹn**, không quan sát trạng thái giữa hai bước.

Nếu reader gọi hai getter synchronized **riêng biệt**, nó có thể thấy `total` từ một lần cập nhật và `updates` từ lần sau, vì khóa được nhả ở giữa hai lời gọi. Để bảo đảm nhất quán đa trường, reader cần **một critical section bao quanh toàn bộ snapshot**, hoặc lấy một immutable snapshot được xây dựng bên trong khóa.

Một chi tiết quan trọng: hai trường không nhất thiết phải là `volatile` khi **mọi đường đọc/ghi cần thiết đều tuân theo cùng monitor**. Các unlock/lock thiết lập visibility và critical section thiết lập atomicity. Thêm volatile từng trường không biến hai lần đọc riêng lẻ thành một snapshot nhất quán.

## 6. Thực hành Java 17+: cập nhật atomic và snapshot nhìn thấy đúng

Chương trình sau có hai writer cùng cập nhật `total` và `updates`. Một reader chạy song song, lấy snapshot nhiều lần và kiểm tra invariant. Cả ba cùng sử dụng một instance `Ledger`.

Tạo file **`MonitorSafetyLab.java`**:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MonitorSafetyLab {
    record Snapshot(long total, long updates) {
        boolean valid() { return total == updates * 10L; }
    }

    static final class Ledger {
        private final Object guard = new Object();
        private long total;
        private long updates;

        void addTen() {
            synchronized (guard) {
                total += 10;
                updates++;
            }
        }

        Snapshot snapshot() {
            synchronized (guard) {
                return new Snapshot(total, updates);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Ledger ledger = new Ledger();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> first = executor.submit(() -> {
                for (int i = 0; i < 10_000; i++) ledger.addTen();
            });
            Future<?> second = executor.submit(() -> {
                for (int i = 0; i < 10_000; i++) ledger.addTen();
            });
            Future<Boolean> reader = executor.submit(() -> {
                for (int i = 0; i < 20_000; i++) {
                    if (!ledger.snapshot().valid()) return false;
                }
                return true;
            });

            first.get();
            second.get();
            boolean readConsistent = reader.get();
            Snapshot end = ledger.snapshot();

            System.out.println("readSnapshotsConsistent=" + readConsistent);
            System.out.println("finalTotal=" + end.total());
            System.out.println("finalUpdates=" + end.updates());
            System.out.println("finalInvariant=" + end.valid());
        } finally {
            executor.shutdown();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 MonitorSafetyLab.java
java MonitorSafetyLab
```

**Stdout đã xác minh:**

```text
readSnapshotsConsistent=true
finalTotal=200000
finalUpdates=20000
finalInvariant=true
```

### Phân tích cơ chế, không dựa vào “chạy may mắn”

1. Mỗi lời gọi `addTen()` lấy monitor `guard` của **cùng Ledger** và cập nhật cả `total` lẫn `updates` trước khi nhả.
2. Một writer khác không thể vào `addTen()` dùng cùng `guard` khi writer đầu còn trong critical section. Do đó có đúng 20.000 phép tăng hợp lệ, mỗi lần đóng góp 10.
3. `snapshot()` cũng phải giành đúng `guard`. Khi nó trả về, record `Snapshot` chứa hai giá trị được đọc **trong một critical section**; không có writer nào xen giữa hai lần đọc.
4. `record Snapshot` là dữ liệu chỉ chứa hai `long`; các component final và value object này không có đường thay đổi nội dung. Trả record ra khỏi lock không làm snapshot trở thành mutable.
5. `Future.get()` là điểm main đợi các task hoàn tất, có memory-consistency guarantees của executor/Future. Khi main đọc snapshot cuối, cả hai writer đã xong; vì thế tổng bằng `20_000 × 10 = 200_000`. **`Future.get()` giúp hoàn tất/visibility ở phía main nhưng không làm các update giữa hai writer tự atomic**; trách nhiệm đó thuộc về monitor.
6. Reader có thể lấy 20.000 snapshot **trước, trong hoặc sau** phần lớn hoạt động writer. Vì mọi snapshot được tạo dưới khóa chung, biểu thức invariant luôn đúng, dù lịch thread thay đổi.

Đối với một chương trình benchmark, ví dụ này không đo overhead; nó kiểm tra **correctness**. Từ kết quả đúng trên một lần chạy không thể kết luận “mã không có race” nói chung, nhưng ở đây quy tắc khóa có thể chứng minh invariant cho mọi interleaving hợp lệ.

**Giới hạn của ví dụ:** phép tính `updates * 10L` có thể overflow trong bài toán tổng quát; số vòng của chương trình hiện tại nhỏ và không có nguy cơ overflow. Nếu dùng ledger thực tế, cần mô hình số tiền, kiểm tra overflow và trạng thái transaction phù hợp. `shutdown()` dừng nhận task mới; ở đây `Future.get()` đã đợi toàn bộ task đã submit. Trong ứng dụng dài hạn, cần quy trình graceful termination và xử lý `InterruptedException`/`ExecutionException` theo contract.

## 7. Phân biệt “đọc từng field an toàn” và “snapshot nhất quán”

Ví dụ cấu trúc sau *không* bảo đảm snapshot đa trường:

```java
// Các method của một class giả định chứa guard, total và updates:
long getTotal() {
    synchronized (guard) {
        return total;
    }
}

long getUpdates() {
    synchronized (guard) {
        return updates;
    }
}

// Thread reader:
long a = getTotal();
long b = getUpdates();
```

Đây là **đoạn code minh họa**, chưa phải class độc lập. Giữa hai lần gọi getter, reader đã nhả monitor. Writer có thể cập nhật cả hai trường và nhả monitor trước khi reader gọi getter thứ hai; kết quả `a` và `b` không phải từ cùng thời điểm logic.

Khắc phục:

```java
// Method của class có guard, total và updates:
Snapshot snapshot() {
    synchronized (guard) {
        return new Snapshot(total, updates);
    }
}
```

Đây là đoạn minh họa cùng kiểu `record Snapshot(long total, long updates)` trong ví dụ trên. Snapshot là một *đơn vị logic* của thao tác đọc, nên cần tạo dưới một lần giành monitor.

Tương tự, nếu yêu cầu nghiệp vụ là “kiểm tra đủ tài nguyên rồi trừ”, hãy gộp **check + update** vào một critical section:

```java
// Method của class có trường int available và monitor guard:
boolean reserveOne() {
    synchronized (guard) {
        if (available == 0) return false;
        available--;
        return true;
    }
}
```

Hai method `hasStock()` và `decrement()`, dù mỗi method riêng có synchronized, cũng có thể để một thread khác chen vào khoảng giữa. Mutual exclusion phải bảo vệ **toàn bộ transaction logic** mà bạn muốn là atomic.

## 8. Khi nào synchronized được nhả?

### Khi rời synchronized block/method

Java tự giải phóng mức sở hữu monitor tương ứng khi control flow rời `synchronized`, kể cả khi có `return` hoặc exception. Một instance synchronized method dùng monitor của `this`; static synchronized method dùng object `Class`.

```java
int compute() {
    synchronized (guard) {
        if (inputInvalid()) return -1;
        return calculate();
    } // monitor được nhả kể cả khi return hoặc exception
}
```

Đây là **method minh họa**; `guard`, `inputInvalid()` và `calculate()` được định nghĩa bên ngoài đoạn code. Việc nhả monitor **không** tự hoàn tác những cập nhật dữ liệu đã xảy ra trước khi exception được ném. Nếu invariant có thể bị dở dang khi exception, cần exception-safe update hoặc chiến lược rollback.

### Reentrancy

Một thread đã giữ monitor vẫn có thể vào lại monitor đó, ví dụ một method `synchronized` gọi method `synchronized` khác trên cùng instance. JVM duy trì số lần sở hữu tương ứng; monitor chỉ khả dụng cho thread khác khi đã nhả hết các mức.

### Thread khác đang tranh monitor

Thread chưa lấy được monitor ở lối vào `synchronized` có thể ở trạng thái `Thread.State.BLOCKED`. Khi owner nhả monitor, contender có cơ hội lấy; JVM không bảo đảm contender nào thắng theo FIFO hay một thời hạn cụ thể. `Thread.interrupt()` không làm một lời gọi đang chờ *monitor entry* tự ném `InterruptedException`.

**Không nhầm với `ReentrantLock`:** explicit lock thuộc `java.util.concurrent.locks` có những cơ chế chờ và API khác (`lockInterruptibly`, `tryLock`). `Thread.State.BLOCKED` dành cho monitor, không bao quát mọi hoạt động chờ khóa.

## 9. Object.wait/notify: visibility gắn với việc chờ điều kiện

Khi thread muốn **đợi một điều kiện thay đổi**, chỉ lấy monitor rồi quay vòng kiểm tra điều kiện sẽ lãng phí CPU và có thể giữ monitor quá lâu. Java cung cấp `Object.wait()`/`notify()`/`notifyAll()` cho giao thức condition wait trên intrinsic monitor.

Mẫu minh họa đúng:

```java
private final Object guard = new Object();
private boolean ready;
private String value;

void publish(String text) {
    synchronized (guard) {
        value = text;
        ready = true;
        guard.notifyAll();
    }
}

String await() throws InterruptedException {
    synchronized (guard) {
        while (!ready) {
            guard.wait();
        }
        return value;
    }
}
```

Đây là **các thành viên của một class**, không phải tệp Java độc lập. Đặt cả predicate `ready`, dữ liệu `value`, thao tác `wait` và thông báo dưới **cùng monitor**.

Cơ chế:

1. Consumer phải sở hữu monitor `guard` trước khi gọi `guard.wait()`; nếu gọi ngoài vùng sở hữu monitor, JVM ném `IllegalMonitorStateException`.
2. `wait()` đưa thread vào wait set và **nhả monitor `guard`**, để producer có thể vào vùng `synchronized(guard)`.
3. Producer ghi dữ liệu, đổi predicate rồi gọi `notifyAll()` khi còn giữ monitor. **`notifyAll()` không tự giải phóng monitor**: producer chỉ nhả khi rời synchronized.
4. Consumer được thức dậy phải **lấy lại monitor** trước khi `wait()` trả về. Lúc đó các cập nhật của producer đã được công bố qua quy tắc unlock→subsequent lock.
5. Consumer kiểm tra lại `ready` bằng vòng `while` vì có thể có **spurious wakeup**, hoặc một waiter khác đã tiêu thụ/đổi điều kiện trong lúc nó chờ giành lại monitor.
6. Nếu producer đã publish trước khi consumer vào `wait`, consumer thấy `ready == true` dưới khóa và **không cần wait**; predicate ngăn lost notification.

`Object.wait()` **chỉ nhả monitor của object được gọi**. Nếu thread đang giữ thêm monitor khác thì `wait` không tự nhả monitor đó. `Thread.sleep()` hoàn toàn **không nhả monitor đang giữ**; dùng sleep để đợi điều kiện là một thiết kế khác và không cung cấp quy tắc notify/reacquire.

### Tại sao notify không phải ranh giới “reader chạy ngay”?

Bởi vì producer vẫn giữ monitor từ lúc `notifyAll()` cho đến khi thoát critical section. Thread vừa được thông báo có thể phải đợi ở `BLOCKED` trong khi cố lấy lại monitor; nó không thể đi tiếp vào critical section trước thời điểm đó. Trường hợp nhiều consumer được notifyAll, từng consumer sẽ tranh monitor theo lịch không xác định.

Trong ứng dụng thông thường, `BlockingQueue`/`CountDownLatch`/`Condition` có thể đơn giản hơn việc tự viết `wait/notify`, nhưng nguyên lý monitor ownership và visibility vẫn là nền tảng giúp hiểu cơ chế.

## 10. Khi nào memory visibility không được bảo đảm bởi monitor?

### Reader không cùng monitor

```java
// Mô tả một class chứa trường int value và Object guard:
void update(int number) {
    synchronized (guard) {
        value = number;
    }
}

int unguardedRead() {
    return value; // không acquire cùng monitor
}
```

Đây là mã minh họa. Lần đọc không khóa không có cạnh HB từ unlock của writer **chỉ nhờ monitor rule**. Có thể tồn tại HB từ cơ chế khác trong một tình huống gọi cụ thể, nhưng bản thân API này không thể tuyên bố thread-safe chỉ vì writer có synchronized.

### Dùng hai object monitor khác nhau

`synchronized (new Object())` bên trong từng lần gọi tạo monitor mới, nên các lần gọi không loại trừ nhau. Hai object khác nhau dù `equals` trả true vẫn có monitor độc lập. Đừng dùng cùng tên biến hoặc cùng chuỗi ký tự như bằng chứng identity.

### Khóa một bước, bỏ khóa bước khác

Nếu phép cập nhật gồm “đọc bản ghi, kiểm tra điều kiện, ghi bản ghi”, chỉ khóa bước ghi có thể vẫn sai vì giá trị kiểm tra đã cũ. Cần critical section bao phủ toàn bộ **invariant transaction** hoặc primitive atomic ở mức logic.

### Khóa cấp instance nhưng state cấp static

Một instance synchronized method trên object A không loại trừ instance synchronized method trên object B. Nếu cả hai cùng ghi một trường `static`, việc `synchronized` trên từng instance **không** tạo mutual exclusion chung. Cần khóa được chia sẻ bởi tất cả writer/reader của state static, hoặc thiết kế lại ownership.

### Có published reference nhưng mutable state tiếp tục đổi

Safe publication của object không đồng nghĩa mọi lần mutation về sau đều thread-safe. Nếu một thread đăng reference vào shared map qua queue rồi tiếp tục thay đổi fields không có synchronization, thread khác có thể gặp data race khi đọc.

## 11. Volatile, atomic và synchronized giải quyết các yêu cầu khác nhau

| Yêu cầu | `synchronized` với cùng monitor | `volatile` | `AtomicInteger` |
|---|---|---|---|
| Visibility/ordering các truy cập phối hợp đúng | Có: unlock→lock, HB | Có: volatile write→read và quy tắc JMM liên quan | Có memory semantics được API quy định |
| Atomic read-modify-write `x++` | **Có** nếu toàn bộ thao tác trong cùng critical section | **Không**, dù `x` là volatile | **Có** với `incrementAndGet()` |
| Bảo vệ invariant gồm nhiều trường | **Có** nếu đọc/ghi cả nhóm dưới một monitor | Không tự bảo vệ group transaction | Không tự bảo vệ nhóm nhiều atomic variables |
| Reader phải dùng protocol nhất quán | Có | Có: đọc đúng volatile và giao thức publish | Có: dùng atomic operation/snapshot phù hợp |
| Thread có thể phải đợi lock | Có | Không để chỉ đọc/ghi volatile | Có thể có contention/retry ở atomic |

Đừng coi `volatile` là “synchronized nhẹ”. Hai cơ chế có API, atomicity và memory semantics khác nhau. Một bộ đếm độc lập có thể phù hợp với `AtomicInteger`; các trường có invariant như `total` và `updates` trong ví dụ phù hợp với monitor bảo vệ toàn bộ snapshot.

**Đặc biệt:** `volatile long` có bảo đảm atomicity cho access đơn lẻ, nhưng hai phép volatile access không trở thành một transaction. Tương tự, `final` trên một object reference không làm object mutable được tham chiếu tự an toàn.

Một công cụ `ReentrantLock` theo API `Lock` cung cấp memory effects của acquire/release tương tự monitor lock/unlock, đồng thời thêm khả năng như timed/interruptible acquisition. Khi chuyển đổi từ `synchronized` sang `Lock`, phải bảo đảm mọi đường truy cập dùng **đúng cùng lock và try/finally**; nó không phải một tối ưu hiệu năng mặc nhiên.

## 12. Những điểm tinh tế của monitor visibility

### Không phải mọi quan hệ HB đều yêu cầu monitor

JMM cũng quy định các cạnh khác:

- Actions trước `Thread.start()` HB actions trong thread mới.
- Hành động của thread kết thúc HB việc thread khác xác nhận nó kết thúc bằng `join()` thành công.
- Volatile write *synchronizes-with* volatile read phù hợp theo synchronization order.
- `Future.get()`, `BlockingQueue`, các đồng bộ trong `java.util.concurrent` có memory-consistency guarantees riêng.

Do đó không nên kết luận “reader không lock thì chắc chắn đọc sai” nếu có một HB path khác bảo đảm điều đó. Cần đánh giá **giao thức hoàn chỉnh**. Nhưng một class dùng monitor để quản lý state thường nên duy trì chính sách mọi truy cập thông thường qua monitor đó, tránh ngầm dựa vào một caller cụ thể đã `join()`.

### Happens-before không có nghĩa có một lịch CPU cố định

HB là quan hệ trên **memory actions** và các quy tắc quan sát, không phải cam kết thread A được xếp lịch ngay trước thread B. Một reader có thể chờ monitor lâu, hoặc vào sớm trước writer. Sự đúng đắn thường phải chứng minh cho **mọi lịch được phép**, không dựa vào kết quả thường thấy trên một máy.

### Visibility và atomicity là hai điều độc lập

Lấy đúng monitor cho phép reader thấy dữ liệu đã công bố và không đọc giữa critical section của writer. Nhưng nếu writer **không gộp** toàn bộ cập nhật invariant trong cùng một critical section, reader vẫn có thể đọc một trạng thái giữa hai vùng synchronized khác nhau; tất cả các đọc vẫn hợp lệ theo HB, nhưng invariant nghiệp vụ đã không được bảo vệ.

Ví dụ sai:

```java
// Giả sử total, updates, guard là các trường của class:
void addTenWrong() {
    synchronized (guard) {
        total += 10;
    }
    synchronized (guard) {
        updates++;
    }
}
```

Đây là method minh họa, không phải chương trình độc lập. Reader gọi `snapshot()` giữa hai khối có thể nhận `total` mới nhưng `updates` cũ. Sửa bằng **một vùng synchronized bao quanh cả hai cập nhật**.

### Lock granularity và hiệu năng

Khóa hẹp giúp nhiều thread xử lý những state độc lập song song; khóa quá rộng làm tăng contention. Nhưng tách một monitor bảo vệ invariant thành hai monitor mà không thiết kế lại protocol có thể phá tính đúng đắn. **Ưu tiên chứng minh invariant trước khi giảm contention**; profiling thực tế mới cho thấy chi phí monitor có đáng tối ưu không.

## 13. Thread state và công cụ chẩn đoán

Khi chương trình có latency cao hoặc nghi data race:

1. **Xác định monitor identity:** code có sử dụng cùng object khóa cho writer và reader không?
2. **Lập bản đồ tất cả đường đọc/ghi:** có getter không synchronized, callback hoặc truy cập trực tiếp từ thread khác không?
3. **Xác định invariant cần nguyên tử:** check-then-act hay snapshot hai trường có bị chia thành nhiều critical section không?
4. **Tìm HB path:** unlock trên monitor nào, reader lock monitor nào và vào sau unlock không? Có HB edges từ `join`, `Future.get`, volatile hoặc queue không?
5. **Quan sát contention:** nếu nhiều thread ở `BLOCKED` trên monitor, tìm owner và code giữ lock quá lâu.
6. **Thử nghiệm correctness:** tạo tải nhiều writer/reader, kiểm tra invariant; dùng `CountDownLatch`/barrier để phối hợp checkpoint thay vì `sleep` làm “bảo đảm”.

Công cụ hữu ích cho Java 17+:

```bash
jcmd <PID> Thread.print -l
```

Thread dump có thể cho thấy `BLOCKED (on object monitor)` và monitor đang bị tranh, nhưng chỉ là ảnh chụp thời điểm. Một thread `WAITING (on object monitor)` tại `Object.wait()` đang đợi điều kiện; không đồng nghĩa nó vẫn giữ monitor mà nó wait. Thread `WAITING (parking)` có thể đang đợi primitive khác, như explicit lock.

JFR có thể giúp thu thập các sự kiện contention/wait trong khoảng thời gian:

```bash
jcmd <PID> JFR.start name=monitors settings=profile duration=60s filename=monitors.jfr
jfr summary monitors.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.JavaMonitorWait monitors.jfr
```

Các lệnh trên áp dụng khi JDK/JVM hỗ trợ JFR và process tồn tại đủ lâu. Event có thể không được bật hoặc bị lọc theo threshold, vì thế **không thấy event không chứng minh không có contention**. Dùng workload thực tế, stack trace, số liệu blocked time và p95/p99 để hiểu tác động.

**ThreadMXBean:** `java.lang.management.ThreadMXBean` có API đọc blocked count/time với monitor contention khi JVM hỗ trợ và monitoring được bật. Nó không phải thước đo cho mọi kiểu chờ khóa, context switch của OS hoặc đúng/sai của Java Memory Model. Dữ liệu quan sát chỉ giúp tìm vị trí; correctness vẫn cần dựa vào contract của mã nguồn.

## 14. Lỗi phổ biến và cách sửa

1. **“Synchronized chỉ ngăn hai thread vào cùng lúc.”** Thiếu một nửa: monitor unlock→lock còn tạo memory ordering/visibility qua HB.
2. **“Writer khóa rồi thì reader có thể đọc biến thường trực tiếp.”** Không có bảo đảm đó từ monitor rule. Reader phải theo protocol hoặc có HB path khác phù hợp.
3. **“Hai object khác nhau nhưng `equals()` bằng nhau là cùng lock.”** Sai: monitor dựa trên object identity.
4. **“`synchronized(new Object())` ở mỗi lần gọi bảo vệ shared field.”** Sai: mỗi lần dùng monitor mới, không có mutual exclusion xuyên lời gọi.
5. **“Hai synchronized getters liên tiếp là một snapshot nguyên tử.”** Sai: monitor được nhả giữa các lời gọi, writer có thể chen vào.
6. **“`static synchronized` khóa cùng monitor với instance synchronized.”** Sai: Class object khác monitor `this`.
7. **“`count++` dưới volatile là atomic.”** Sai: volatile không gộp read-modify-write thành một action nguyên tử.
8. **“Đã synchronized thì thread đầu tiên gọi method sẽ luôn vào trước.”** Sai: `synchronized` không bảo đảm FIFO/fairness.
9. **“`notifyAll()` nhả monitor ngay.”** Sai: chỉ thông báo; caller vẫn giữ monitor đến khi rời critical section.
10. **“`wait()` nhả tất cả các monitor đang giữ.”** Sai: nó chỉ nhả monitor của chính object nhận `wait()`.
11. **“`sleep()` hoặc `yield()` giúp các ghi được thread khác thấy an toàn.”** Không phải synchronization protocol cho shared state.
12. **“Có HB nên không bao giờ deadlock.”** HB không tự ngăn lock-order deadlock. A giữ khóa X chờ Y, B giữ Y chờ X vẫn có thể không tiến triển.
13. **“Exception làm JVM quên nhả intrinsic monitor.”** Java nhả khi scope synchronized kết thúc đột ngột; tuy nhiên state đã mutate không tự rollback.
14. **“Muốn nhanh chỉ cần xóa synchronized.”** Thay đổi phải giữ tính đúng đắn; giảm thời gian critical section hoặc partition invariant an toàn trước khi quyết định bỏ monitor.
15. **“Thấy kết quả đúng một triệu lần tức không còn race.”** Test là bằng chứng trên các lịch đã chạy; phân tích ownership, HB và invariant mới giải thích tính đúng đắn cho mọi lịch hợp lệ.

## 15. Câu hỏi tự kiểm tra có đáp án

**Câu 1.** A ghi `x=7` rồi unlock monitor `m`; B sau đó lock cùng `m` và đọc `x`, không có writer khác. B có thể đọc giá trị cũ vì `x` không volatile không?  
**Đáp án:** Không, với điều kiện unlock của A đứng trước lock của B trong synchronization order, ghi của A HB lần đọc của B; đây là bảo đảm memory visibility.

**Câu 2.** A dùng `synchronized(left)`, B dùng `synchronized(right)` trên hai object khác nhau, cả hai đọc/ghi cùng `x`. Có mutual exclusion và HB qua monitor rule không?  
**Đáp án:** Không. Hai monitor độc lập; cần cùng monitor hoặc cơ chế đồng bộ khác tạo HB hợp lệ.

**Câu 3.** Hai writer mỗi thread thực hiện 10.000 lần `count++` trong cùng monitor; main gọi `join` cả hai rồi đọc `count` dưới monitor. Kết quả là bao nhiêu nếu ban đầu 0?  
**Đáp án:** 20.000. Monitor bảo vệ từng lần tăng; join bảo đảm hai writer hoàn tất trước khi đọc.

**Câu 4.** Một thread gọi synchronized instance method trên `a`, thread khác gọi cùng method trên `b`. Chúng có tranh một monitor không?  
**Đáp án:** Không nếu `a` và `b` là hai instance khác nhau. Mỗi method khóa `this` của instance tương ứng.

**Câu 5.** Vì sao writer cập nhật `total` và `updates` dưới một monitor nhưng reader cần đọc hai trường trong **cùng một critical section**?  
**Đáp án:** Nếu reader nhả khóa giữa hai lần đọc, writer có thể hoàn tất một update ở giữa; hai giá trị có thể thuộc hai trạng thái logic khác nhau.

**Câu 6.** Consumer gọi `wait()` trên object `m` khi đang sở hữu `m`. Nó có giữ `m` trong lúc chờ không?  
**Đáp án:** Không. `wait()` nhả monitor `m`, rồi consumer phải lấy lại `m` trước khi `wait()` trả về. Monitor khác đang giữ không được tự nhả.

**Câu 7.** Producer gọi `notifyAll()` rồi làm việc lâu trước khi rời synchronized. Consumer có thể chạy tiếp bên trong cùng monitor ngay sau notify không?  
**Đáp án:** Không. Producer vẫn sở hữu monitor; consumer phải đợi producer unlock rồi mới có thể reacquire.

**Câu 8.** `volatile int counter; counter++;` có tương đương `synchronized(guard) { counter++; }` không?  
**Đáp án:** Không. Volatile access có ordering/visibility, nhưng `counter++` vẫn là read-modify-write và có thể mất cập nhật.

**Câu 9.** Một lần writer unlock `m` trước reader lock `m` có buộc reader luôn nhìn thấy giá trị của **writer đó**, dù thread thứ ba đã thay đổi giá trị hợp lệ giữa hai lần không?  
**Đáp án:** Không nhất thiết. HB bảo đảm các quy tắc quan sát và ordering, nhưng một writer khác có thể cập nhật giá trị trước lần đọc; cần xét toàn bộ lịch và các ghi liên quan.

**Câu 10.** Nếu `synchronized` giải quyết correctness, vẫn có thể có vấn đề performance hoặc liveness không?  
**Đáp án:** Có. Giữ monitor lâu tạo contention; lồng nhiều monitor không nhất quán có thể deadlock; scheduler không cam kết fairness. Đúng dữ liệu và tiến triển/hiệu năng là các thuộc tính cần đánh giá riêng.

## Tài liệu tham khảo

- [Java Language Specification, Java SE 17 `17.1 — Synchronization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1): monitor, lock/unlock, mutual exclusion.
- [JLS `17.4.4 — Synchronization Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.4): quy tắc *synchronizes-with* của unlock/lock.
- [JLS `17.4.5 — Happens-before Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5): program order, synchronizes-with, HB, correctly synchronized.
- [JLS `14.19 — synchronized Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.19): scope của synchronized và abrupt completion.
- [Java SE 17 Object API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): `wait`, `notify`, `notifyAll` và điều kiện sở hữu monitor.
- [Java SE 17 Thread.State](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.State.html): `BLOCKED`, `WAITING` và trạng thái thread.
- [Java SE 17 ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): quản lý task và memory consistency.
- [Java SE 17 Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): completion và memory consistency qua `get()`.

**Ghi nhớ:** mutual exclusion làm những thread dùng **cùng monitor** không chạy xen giữa critical section của nhau; memory visibility xuất phát từ cạnh **unlock(m) → subsequent lock(m)** trong Java Memory Model. Muốn chương trình đúng, cả writer và reader phải dùng giao thức nhất quán, và **toàn bộ invariant logic** phải nằm trong phạm vi được bảo vệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-006">Thiết kế critical section đủ nhỏ và dễ kiểm chứng</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế critical section đủ nhỏ và dễ kiểm chứng trong Java

> **Mục tiêu:** Nhận diện chính xác dữ liệu và invariant cần bảo vệ; thiết kế một critical section đủ **lớn để đúng**, nhưng đủ **nhỏ để giảm tranh chấp và dễ kiểm chứng**; biết chuyển các thao tác độc lập ra ngoài khóa, tránh gọi code không kiểm soát khi đang giữ lock, và giữ các thay đổi cần nguyên tử trong cùng một ranh giới đồng bộ.

Bài thuộc nhóm **Synchronization** của Java concurrency nói chung. Các ví dụ dùng Java SE 17+ với `synchronized`, `ReentrantLock` và một số công cụ `java.util.concurrent`. Không dùng cơ chế của Spring.

## 1. Critical section là gì?

Một **critical section** là đoạn mã cần được thực thi theo một cơ chế phối hợp nhằm bảo vệ dữ liệu hoặc invariant mà nhiều thread có thể cùng truy cập. Với mutual exclusion bằng một lock, tại một thời điểm chỉ một thread được ở trong những critical section dùng **cùng lock ấy**.

Ví dụ đơn giản:

~~~java
// Minh họa, không phải chương trình độc lập.
private final Object lock = new Object();
private int count;

void increment() {
    synchronized (lock) {
        count++;
    }
}
~~~

`count++` là read-modify-write, gồm đọc giá trị cũ, cộng và ghi. Khi các thread đều thực hiện cập nhật qua **cùng `lock`**, toàn bộ cập nhật này nằm trong vùng loại trừ lẫn nhau. Khóa giúp chống lost update; đồng thời `unlock` và `lock` tiếp theo trên cùng monitor còn tạo quan hệ *happens-before* để quan sát dữ liệu.

Đừng đồng nhất “critical section nhỏ” với “có ít dòng mã”. Một khối một dòng có thể chứa lệnh I/O rất lâu hoặc callback gọi ngược vào hệ thống. Ngược lại, một khối có nhiều dòng kiểm tra và cập nhật có thể là **đơn vị atomic nhỏ nhất đúng với invariant**.

### Ba tiêu chí để đánh giá

1. **Correctness:** Không có thread khác chen giữa những bước cần cùng một invariant; mọi đường truy cập state dùng cùng protocol đồng bộ.
2. **Reviewability:** Người đọc nhìn vào đoạn mã biết ngay object nào được khóa, state nào được bảo vệ và trạng thái hợp lệ trước/sau đoạn mã.
3. **Contention:** Khóa được giữ trong thời gian hợp lý; mã tốn thời gian hoặc không phụ thuộc vào state được đưa ra ngoài nếu việc đó **không phá invariant**.

Thứ tự ưu tiên là **đúng trước, đơn giản dễ kiểm chứng sau, rồi mới tối ưu contention dựa trên đo lường**. Thu hẹp khóa bằng cách tách đôi một thao tác phải atomic làm chương trình nhanh hơn trên benchmark đôi khi, nhưng không còn đúng.

## 2. Bắt đầu từ invariant, không bắt đầu từ dòng `synchronized`

Một **invariant** là điều kiện phải luôn đúng ở những điểm các thread khác được phép quan sát state. Chẳng hạn kho hàng:

- `available >= 0`: tồn kho không âm.
- Chỉ duyệt tối đa số sản phẩm còn có thể đặt.
- Một đơn hàng chỉ được chấp nhận nếu kiểm tra và trừ tồn kho thuộc **cùng một lần quyết định**.

Cách tổ chức sai (minh họa):

~~~java
if (inventory.available() > 0) { // Đọc có khóa hoặc atomic.
    inventory.decrement();        // Ghi có khóa hoặc atomic riêng.
}
~~~

Ngay cả khi mỗi method tự synchronized, một thread khác có thể lấy sản phẩm cuối **giữa hai lời gọi**. Kiểm tra-để-rồi-hành-động (*check-then-act*) là compound action; đúng hay sai phụ thuộc cả ranh giới của hai bước.

Thiết kế tốt hơn là đưa **điều kiện và chuyển trạng thái** vào cùng một method:

~~~java
boolean reserveOne() {
    synchronized (lock) {
        if (available == 0) {
            return false;
        }
        available--;
        return true;
    }
}
~~~

Đây là một critical section đủ nhỏ: trong lock chỉ làm điều thực sự phụ thuộc vào `available`; method trả boolean thay vì buộc caller tự kiểm tra rồi cập nhật ở hai thời điểm.

### Định nghĩa ownership ngay ở API

Tốt hơn nên để shared state là `private`, không cung cấp setter/getter tùy ý phá quy tắc đồng bộ. API như `reserveOne()`, `snapshot()`, `transfer()` hoặc `submit(...)` nói rõ **một thao tác nghiệp vụ nguyên tử**.

Một getter trả `available` có thể hợp lệ để hiển thị thống kê, nhưng không nên dùng giá trị đó làm giấy phép thực hiện một cập nhật ở thời điểm sau. Giá trị getter là snapshot đã có thể cũ ngay khi ra khỏi lock.

## 3. Ví dụ 1 — test được tính đúng của vùng kiểm tra và giảm tồn kho

Tạo `InventoryDemo.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class InventoryDemo {
    private static final class Inventory {
        private final Object lock = new Object();
        private int available = 1;

        boolean reserveOne() {
            synchronized (lock) {
                if (available == 0) {
                    return false;
                }
                available--;
                return true;
            }
        }

        int remaining() {
            synchronized (lock) {
                return available;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Inventory inventory = new Inventory();
        AtomicInteger approved = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        Runnable task = () -> {
            try {
                start.await();
                if (inventory.reserveOne()) {
                    approved.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(task, "a");
        Thread b = new Thread(task, "b");
        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();

        System.out.println("approved = " + approved.get());
        System.out.println("remaining = " + inventory.remaining());
    }
}
~~~

Chạy:

~~~bash
javac InventoryDemo.java
java InventoryDemo
~~~

Stdout khi không bị ngắt bên ngoài:

~~~text
approved = 1
remaining = 0
~~~

Hai thread cùng có thể cạnh tranh ở `reserveOne()`, nhưng **chỉ một thread** được đi qua phần kiểm tra/cập nhật tại một thời điểm. Với `available = 1`, người vào lock trước giảm về `0`; người còn lại nhận `false`. Kết quả luôn duyệt đúng một lần.

`CountDownLatch` chỉ giúp hai task bắt đầu gần nhau để minh họa tranh chấp; tính đúng đến từ monitor, không đến từ việc các thread có chạy cùng lúc hay không. `approved` dùng `AtomicInteger` để kết quả của hai thread không tạo một race thứ hai trong ví dụ.

**Ranh giới cần nhớ:** Không đưa `available == 0` ra ngoài lock rồi chỉ khóa `available--`. Điều kiện và state transition thuộc **cùng một quyết định atomic**.

## 4. Quy trình xác định ranh giới của critical section

Trước khi thêm `synchronized` hay `lock.lock()`, hãy lập một “bản đồ state”:

| Câu hỏi | Điều cần xác nhận |
| --- | --- |
| Dữ liệu nào được chia sẻ? | Các field mutable, reference, collection, object graph mà nhiều thread chạm tới |
| Invariant là gì? | Ví dụ số dư không âm, mỗi ID duy nhất, tổng hai tài khoản bảo toàn |
| Thao tác nào đọc state để quyết định ghi? | Check-then-act, read-modify-write, lặp qua cấu trúc rồi cập nhật |
| Ai được quyền sửa? | Một writer, nhiều writer, hay chuyển quyền sở hữu state |
| Cần atomicity đến đâu? | Một biến, một bản ghi, một nhóm field hay nhiều object |
| Khóa nào sở hữu invariant? | Một private monitor/`ReentrantLock` thống nhất; có thứ tự nếu nhiều khóa |
| Điều gì có thể làm ngoài lock? | Tính toán độc lập, parse input, tạo immutable object, I/O hoặc thông báo không cần giữ invariant |
| Việc gì không thể tách? | Kiểm tra state hiện tại và cập nhật dựa vào nó; đọc nhiều field thành một snapshot nhất quán |

### Phân loại thao tác theo *dependency*

**Independent work** là công việc dùng input cục bộ/bất biến và không cần state đang được bảo vệ. Ví dụ parse một chuỗi request, kiểm tra định dạng, tạo một immutable DTO. Thường có thể thực hiện trước lock.

**State-dependent work** cần đọc shared state để đưa ra quyết định, như “nếu đủ tiền thì trừ tiền” hoặc “nếu key chưa tồn tại thì insert”. Phải thực hiện dưới lock, hoặc dùng primitive atomic chuyên dụng.

**External side effect** là I/O, gửi message, log network, callback, hoặc gọi API tùy độ trễ. Thường nên nằm **sau khi thả lock**, nhưng phải thiết kế rõ state transition và thất bại ở bước này. Không thể cứ dời side effect ra ngoài rồi mặc định nghiệp vụ vẫn nguyên tử.

## 5. Ví dụ 2 — tính toán độc lập bên ngoài, commit ngắn bên trong

Tạo `ShortCriticalSectionDemo.java` (Java 17+):

~~~java
public class ShortCriticalSectionDemo {
    private static final class Total {
        private final Object lock = new Object();
        private long cents;

        void addTextAmount(String input) {
            // Đọc, parse và validate input bất biến ở ngoài lock.
            long delta = Long.parseLong(input);
            if (delta <= 0) {
                throw new IllegalArgumentException("positive amount required");
            }

            // Chỉ cập nhật state chia sẻ khi đã có delta hợp lệ.
            synchronized (lock) {
                cents = Math.addExact(cents, delta);
            }
        }

        long snapshot() {
            synchronized (lock) {
                return cents;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Total total = new Total();
        Runnable work = () -> {
            for (int i = 0; i < 1_000; i++) {
                total.addTextAmount("1");
            }
        };

        Thread a = new Thread(work);
        Thread b = new Thread(work);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("total = " + total.snapshot());
    }
}
~~~

Chạy:

~~~bash
javac ShortCriticalSectionDemo.java
java ShortCriticalSectionDemo
~~~

Stdout:

~~~text
total = 2000
~~~

Ở ví dụ, `Long.parseLong(input)` và kiểm tra `delta` không phụ thuộc shared `cents` nên có thể làm ngoài lock. Nhưng **đọc `cents`, cộng và ghi lại** phải cùng critical section. `Math.addExact` ném `ArithmeticException` khi overflow, và do RHS được đánh giá trước phép gán nên một lần overflow sẽ không gán giá trị tràn vào `cents`.

**Cẩn thận:** Nếu tính toán bên ngoài dựa trên `cents` đã đọc trước đó, kết quả có thể bị lỗi stale snapshot. Chỉ tách những phần **thực sự độc lập**. Nếu phải tính ngoài lock từ snapshot rồi commit, cần kiểm tra lại version/CAS hoặc một protocol optimistic rõ ràng (xem phần 10).

## 6. Vùng khóa nhỏ không được phá invariant nhiều field

Giả sử một ledger có `total` và `version`. Invariant nghiệp vụ là **mỗi lần tăng total thành công phải tăng version đúng một lần**. Đây là code sai:

~~~java
// Sai: hai cập nhật không được bảo vệ như một tổ hợp.
synchronized (lock) {
    total += delta;
}
// Một reader có thể chen vào giữa.
synchronized (lock) {
    version++;
}
~~~

Dù mỗi phần riêng lẻ nằm trong lock, người đọc `total` và `version` có thể nhận một snapshot thuộc **hai phiên bản khác nhau** nếu nó lấy lock ở giữa. Bảo vệ một method cập nhật và một method snapshot:

~~~java
void add(long delta) {
    synchronized (lock) {
        total += delta;
        version++;
    }
}

Snapshot snapshot() {
    synchronized (lock) {
        return new Snapshot(total, version);
    }
}
~~~

Khóa phải bao cả các field cần nhất quán. Reader cũng phải lấy **cùng lock** khi đọc snapshot; nếu chỉ writer khóa còn reader đọc trần, vẫn có thể sai.

### Ví dụ 3 — snapshot nhất quán từ nhiều field

Tạo `ConsistentSnapshotDemo.java`:

~~~java
public class ConsistentSnapshotDemo {
    private record Snapshot(long total, long version) {}

    private static final class Ledger {
        private final Object lock = new Object();
        private long total;
        private long version;

        void addOne() {
            synchronized (lock) {
                total++;
                version++;
            }
        }

        Snapshot snapshot() {
            synchronized (lock) {
                return new Snapshot(total, version);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Ledger ledger = new Ledger();

        Runnable work = () -> {
            for (int i = 0; i < 10_000; i++) {
                ledger.addOne();
            }
        };

        Thread first = new Thread(work);
        Thread second = new Thread(work);
        first.start();
        second.start();
        first.join();
        second.join();

        Snapshot result = ledger.snapshot();
        System.out.println("total = " + result.total());
        System.out.println("version = " + result.version());
        System.out.println("consistent = "
                + (result.total() == result.version()));
    }
}
~~~

Chạy:

~~~bash
javac ConsistentSnapshotDemo.java
java ConsistentSnapshotDemo
~~~

Stdout:

~~~text
total = 20000
version = 20000
consistent = true
~~~

`snapshot()` trả về một immutable record tạo **trong critical section**, nên cặp field thể hiện một trạng thái nhất quán của ledger. Người gọi có thể xử lý snapshot ngoài lock mà không cần tiếp tục giữ monitor, vì đây là dữ liệu bất biến gồm hai số.

Không nên trả trực tiếp một mutable internal object cho caller rồi tuyên bố snapshot thread-safe; khi state còn được mutate bởi worker khác, caller cần bản copy hoặc giao thức đồng bộ tương ứng.

## 7. Giảm thời gian giữ lock bằng cách không thực hiện I/O dưới lock

Các thao tác có thời gian khó dự báo thường không nên được đặt dưới một khóa dữ liệu chung:

- Đọc/ghi file, database hoặc mạng.
- Gọi HTTP, RPC, queue client hay dịch vụ bên ngoài.
- Ghi log tới logger đồng bộ hoặc sink có thể chờ I/O.
- `Thread.sleep`, đợi `Future.get`, `BlockingQueue.take`, chờ latch khi các thread khác cần cùng lock để hoàn thành.
- Tính toán rất nặng nhưng không cần state mutable được bảo vệ.
- Gọi code của plugin, callback, listener hoặc method override mà ta không kiểm soát.

Nếu thread A giữ lock khi chờ I/O, các thread B/C cần lock phải đợi cả thời gian I/O, dù thao tác bảo vệ state chỉ cần vài micro/millisecond. Vấn đề còn nghiêm trọng hơn nếu I/O phụ thuộc một worker đang cố lấy chính lock ấy: ta có thể tự tạo deadlock.

Mẫu khuyến nghị:

~~~text
1. Xử lý input độc lập ở ngoài lock
2. Lấy lock
3. Kiểm tra invariant hiện tại và chuyển state tối thiểu
4. Tạo snapshot/result hoặc một ý định side effect bất biến
5. Thả lock
6. Thực thi I/O/callback/notification dựa trên result
7. Nếu cần, ghi nhận trạng thái thành công/thất bại bằng một bước được thiết kế riêng
~~~

**Giới hạn:** Nếu side effect phải xảy ra “nguyên tử” với state trong bộ nhớ hoặc hệ thống ngoài tiến trình, chỉ dời side effect ra ngoài lock **không tạo transaction phân tán**. Cần thiết kế idempotency key, state machine, outbox, retry/compensation hoặc cơ chế giao dịch thích hợp tùy hệ thống. Khóa JVM không bảo đảm atomicity giữa memory và database/mạng.

### Ví dụ side effect không nên được giữ dưới khóa

Sai:

~~~java
synchronized (lock) {
    state = State.READY;
    remoteNotifier.send(event); // Có thể block, throw hoặc gọi lại.
}
~~~

Một lựa chọn dễ kiểm chứng hơn nếu nghĩa nghiệp vụ cho phép:

~~~java
Event event;
synchronized (lock) {
    state = State.READY;
    event = new Event(state);
}
remoteNotifier.send(event); // Đã thả lock, xử lý lỗi riêng.
~~~

Hai đoạn là **minh họa cấu trúc**, không phải chương trình độc lập (các kiểu `State`, `Event` và `remoteNotifier` cần tự định nghĩa). Nếu gửi event thất bại, state đã là READY; nghiệp vụ phải định nghĩa cách retry/ghi nhận lỗi. Không được giả định rằng thao tác đã trở thành một transaction nhờ dời lời gọi ra ngoài khóa.

## 8. Tránh gọi code không kiểm soát khi giữ khóa (*open calls*)

Một callback có thể làm bất cứ điều gì: tốn thời gian, ném exception, gọi ngược vào object hiện tại, gọi object khác đang giữ lock, hoặc block chờ một thread khác. Khi gọi callback bên trong lock, ta ngầm mở rộng critical section sang code không kiểm soát.

Ví dụ không nên làm:

~~~java
synchronized (lock) {
    for (Runnable listener : listeners) {
        listener.run(); // Callback có thể reenter, chậm hoặc chờ.
    }
}
~~~

Giải pháp là **chụp danh sách callback dưới lock rồi gọi ngoài lock** nếu semantics cho phép. Snapshot này cũng tránh thay đổi `listeners` giữa lúc lặp qua chính danh sách nội bộ.

### Ví dụ 4 — chụp danh sách listener rồi gọi ngoài lock

Tạo `OpenCallDemo.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OpenCallDemo {
    private static final class EventSource {
        private final Object lock = new Object();
        private final List<Runnable> listeners = new ArrayList<>();

        void subscribe(Runnable listener) {
            Objects.requireNonNull(listener);
            synchronized (lock) {
                listeners.add(listener);
            }
        }

        void fire() {
            List<Runnable> snapshot;
            synchronized (lock) {
                snapshot = List.copyOf(listeners);
            }

            for (Runnable listener : snapshot) {
                listener.run(); // Không giữ lock nội bộ.
            }
        }
    }

    public static void main(String[] args) {
        EventSource source = new EventSource();
        source.subscribe(() -> System.out.println("listener A"));
        source.subscribe(() -> System.out.println("listener B"));
        source.fire();
    }
}
~~~

Chạy:

~~~bash
javac OpenCallDemo.java
java OpenCallDemo
~~~

Stdout:

~~~text
listener A
listener B
~~~

`List.copyOf` tạo snapshot cấu trúc danh sách listener tại thời điểm lấy lock, và `fire()` gọi callback **sau khi lock đã được thả**. Nếu listener đăng ký thêm listener trong callback, thay đổi ấy có thể áp dụng ở lượt `fire()` tiếp theo; nó không làm thay đổi snapshot của lượt hiện tại.

Lưu ý: `listener.run()` có thể ném exception, khiến vòng lặp dừng trước các listener còn lại; nếu yêu cầu “thông báo tất cả bất kể một listener lỗi”, phải có chính sách bắt lỗi riêng, báo cáo và retry. Snapshot chỉ giải quyết lock ownership, không giải quyết mọi semantics của hệ thống event.

Một số ứng dụng cần snapshot toàn bộ **nội dung event** cùng danh sách listener; để listener đọc mutable state sau unlock có thể lại gặp inconsistent state. Hãy tạo immutable event snapshot trong critical section nếu đó là dữ liệu listener cần quan sát.

## 9. Không phải thao tác blocking nào cũng nguy hiểm như nhau

Một lời gọi chờ **không tự động deadlock**. Nguy cơ xuất hiện khi ta giữ khóa mà thread khác cần để tạo tiến triển.

Ví dụ:

~~~text
Thread A:
    lock(L)
    wait(Future completed by B)  <-- A vẫn giữ L

Thread B:
    lock(L)                      <-- B cần L mới hoàn tất Future
    complete Future
~~~

A đợi B, B đợi A thả L: đây là chu trình chờ và có thể deadlock.

Cách sửa là thay đổi giao thức để A **không giữ L khi chờ công việc đòi hỏi L**, hoặc chuyển công việc sang một mô hình event/state machine không tạo vòng chờ. Chỉ thêm timeout có thể giảm thời gian treo nhưng không sửa invariant hay dependency cycle.

Trong Java, `Object.wait()` khi được gọi đúng trong một khối synchronized sẽ **nhả monitor của chính object được wait** trong lúc chờ, rồi lấy lại trước khi trả về. Đây là ngoại lệ quan trọng so với `Thread.sleep()`, vốn **không nhả monitor đang giữ**. Tuy vậy, dùng `wait/notify` vẫn phải có vòng `while` kiểm tra predicate và một protocol rõ ràng.

Ví dụ monitor condition:

~~~java
synchronized (lock) {
    while (!condition) {
        lock.wait();
    }
    // Kiểm tra/làm việc theo condition dưới lock.
}
~~~

Đoạn minh họa này chỉ có nghĩa khi thread khác thay đổi `condition` dưới cùng lock và gọi `notifyAll`/`notify` phù hợp; nếu không, thread có thể chờ mãi. Thực tế thường ưu tiên `BlockingQueue`, `CountDownLatch`, `Condition` hoặc concurrent utilities khi đúng bài toán.

## 10. Khi muốn tính ngoài lock từ một snapshot: vấn đề stale data

Có những phép tính tốn thời gian **phụ thuộc state hiện tại**, ví dụ tính giá từ tồn kho, dựng một immutable cấu hình mới, hoặc lập lịch dựa trên số slot còn trống. Đọc state dưới lock, thả lock để tính, rồi quay lại cập nhật **không an toàn nếu không kiểm tra rằng state chưa đổi**.

Mẫu có nguy cơ:

~~~java
Snapshot old;
synchronized (lock) {
    old = snapshotOfState();
}
Proposal proposal = expensiveCalculation(old);
synchronized (lock) {
    apply(proposal); // Sai nếu state đổi và proposal phụ thuộc old.
}
~~~

Một phương án *optimistic* có thể dùng **version**:

~~~java
// Mã giả mô tả protocol, không phải chương trình độc lập.
while (true) {
    Snapshot base;
    synchronized (lock) {
        base = snapshotWithVersion();
    }

    Proposal proposal = expensiveCalculation(base); // Ngoài lock.

    synchronized (lock) {
        if (currentVersion != base.version()) {
            continue; // State đổi; bỏ đề xuất cũ và thử lại.
        }
        apply(proposal);
        currentVersion++;
        break;
    }
}
~~~

Điều kiện để đúng:

1. Mọi writer ảnh hưởng state cần thiết đều cập nhật cùng **version** theo protocol.
2. `snapshotWithVersion()` phải lấy một snapshot nhất quán dưới lock.
3. `apply(proposal)` và việc tăng version phải chung critical section.
4. Tính toán bên ngoài không tạo side effect không thể rollback vì nó có thể phải **lặp lại**.
5. Nên có chính sách retry/backoff hoặc giới hạn xung đột; dưới tải cao, optimistic retry có thể tốn hơn việc giữ lock ngắn.

Đây là một thiết kế khác, **không phải lý do tự ý tách mọi critical section**. Với tính toán nhanh và contention thấp, giữ tính toán ngắn trong lock thường đơn giản, dễ chứng minh hơn.

## 11. Khi cần nhiều khóa: thứ tự khóa cố định

Một critical section có thể cần bảo vệ trạng thái của **nhiều đối tượng**. Ví dụ chuyển tiền giữa hai tài khoản phải kiểm tra số dư, trừ tiền ở nguồn và cộng tiền ở đích trong một thao tác logic nhất quán. Nếu hai thread lấy hai khóa theo hai thứ tự đối nghịch, chúng có thể rơi vào deadlock:

~~~text
Thread A: giữ lock(account1), chờ lock(account2)
Thread B: giữ lock(account2), chờ lock(account1)
~~~

Giải pháp là xác định **thứ tự khóa tổng, ổn định** (chẳng hạn ID duy nhất, bất biến) và mọi đường code đều lấy nhiều khóa theo cùng thứ tự, độc lập với thứ tự tham số. Không được nhả khóa tài khoản nguồn trước khi giữ khóa tài khoản đích nếu invariant yêu cầu cả chuyển khoản là atomic.

Ở bài này, điều quan trọng là **ranh giới critical section phải bao trọn invariant xuyên nhiều object**. Bài **Lock ordering để ngăn deadlock** tiếp tục bằng mã chuyển khoản đầy đủ, cách đặt thứ hạng, xử lý khóa trùng và kiểm thử vòng chờ.

## 12. `ReentrantLock`: rõ ràng acquire/release và cleanup

`synchronized` tự giải phóng monitor khi rời block, kể cả khi ném exception. `ReentrantLock` cần `unlock()` rõ ràng, thông thường trong `finally`:

~~~java
// Minh họa method trong class có một ReentrantLock dùng chung.
private final java.util.concurrent.locks.ReentrantLock lock =
        new java.util.concurrent.locks.ReentrantLock();

private int value;

void increment() {
    lock.lock();
    try {
        value++;
    } finally {
        lock.unlock();
    }
}
~~~

`ReentrantLock` hữu ích khi cần:

- `tryLock()` để tránh chờ vô hạn hoặc có fallback.
- `tryLock(timeout, unit)` để giới hạn thời gian **chờ lấy lock**.
- `lockInterruptibly()` để có thể hủy việc đợi lock.
- `Condition` để chờ một predicate với protocol tương ứng.
- Tùy chọn fairness cho lock nếu ứng dụng có nhu cầu và chấp nhận tradeoff; fairness của lock không đồng nghĩa scheduler OS luôn công bằng.

**Tránh các lỗi:**

- `lock()` rồi `return` hoặc throw trước khi vào `try/finally`.
- Gọi `unlock()` từ thread không sở hữu lock.
- Lấy nhiều ReentrantLock theo thứ tự không nhất quán.
- Giả định `tryLock() == false` có nghĩa trạng thái nghiệp vụ đã sai; nó chỉ nói thread chưa lấy được lock.
- Giữ lock trong khi chờ I/O hoặc task khác phụ thuộc lock.

Nếu chỉ cần một critical section đơn giản, `synchronized` thường dễ kiểm chứng hơn vì cấu trúc lexical của lock rõ ràng và việc release được JVM thực hiện tự động.

## 13. Chọn atomic primitive thay vì lock khi đúng phạm vi

Đối với **một counter độc lập**, `AtomicInteger.incrementAndGet()` biểu đạt phép read-modify-write atomic mà không cần khóa monitor do bạn quản lý:

~~~java
private final java.util.concurrent.atomic.AtomicInteger count =
        new java.util.concurrent.atomic.AtomicInteger();

void increment() {
    count.incrementAndGet();
}
~~~

Nhưng không được tự chia `count.get()` và `count.set(old + 1)` rồi cho là atomic. Với nhiều field cùng invariant, hai `AtomicInteger` riêng lẻ cũng không cung cấp transaction chung.

`ConcurrentHashMap.compute/merge` có thể thực hiện cập nhật nguyên tử theo key; song chuỗi `map.get(k)` rồi `map.put(k, value)` vẫn là check-then-act không atomic ở cấp operation kết hợp. `LongAdder` tốt cho một số workload thống kê nhiều writer, nhưng `sum()` khi có cập nhật đồng thời không phải snapshot atomic.

**Định nghĩa phạm vi** trước khi chọn primitive. “Không có synchronized” không tự nghĩa là tối ưu hơn hay đúng hơn. Một lock gọn, bao đúng invariant đa field có thể đơn giản và an toàn hơn kết hợp nhiều atomic variables thiếu protocol.

## 14. Tối ưu thời gian giữ khóa: đừng đổi correctness lấy throughput

Có hai chi phí khác nhau:

- **Thời gian thực thi bên trong lock**: tính toán, cấp phát, kiểm tra điều kiện, thao tác memory/I/O.
- **Thời gian chờ để lấy lock** (*contention / blocked time*): phụ thuộc tần suất cạnh tranh, số thread, scheduling, độ dài critical section và thiết kế lock.

Để cải thiện:

1. **Loại I/O/callback có thể block** khỏi lock khi semantics cho phép.
2. **Tính trước dữ liệu độc lập** như parse, validate input immutable, tạo DTO bất biến.
3. **Chụp immutable snapshot trong lock**, xử lý/report bên ngoài.
4. **Giảm shared mutable state**: thread confinement, message passing, immutable values, queue.
5. **Giảm tần suất khóa** khi có thể batch cập nhật dưới cùng lock mà vẫn giữ latency phù hợp.
6. **Tách lock theo dữ liệu độc lập** khi không có invariant chéo; tránh lock striping tùy tiện nếu nhiều field phụ thuộc nhau.
7. **Đo lường trước/sau** bằng workload đại diện: throughput, p95/p99 latency, block/wait time, queue length và hot lock.

Một critical section nhỏ về số dòng nhưng gọi thuật toán chậm vẫn là vùng khóa dài. Một critical section “rất ngắn” được thực hiện hàng triệu lần cũng có thể gây contention lớn. Đo đạc mới cho biết bottleneck thật.

### Lưu ý về granularity khi tách lock

Giả sử tài khoản A và B không bao giờ tương tác, mỗi tài khoản một lock có thể tăng concurrency. Nhưng khi có thao tác chuyển khoản, invariant của cả A và B cần đồng bộ theo protocol **nhiều khóa** hoặc một lock chung. Tách khóa mà không phân tích dữ liệu phụ thuộc làm code khó kiểm chứng hơn và tạo nguy cơ deadlock.

Khóa quá rộng làm chậm tiến triển của các công việc không liên quan; khóa quá hẹp phá atomicity. **Phạm vi đúng** là phạm vi tối thiểu bao trọn invariant và các điều kiện quyết định thay đổi nó.

## 15. Xác minh tính đúng: review bằng invariant và phép xen kẽ

### A. Viết một bảng “ai giữ lock khi nào”

Ví dụ cho `reserveOne()`:

| Trạng thái | Thread A | Thread B |
| --- | --- | --- |
| `available = 1` | Lấy lock và đọc 1 | Chờ lock |
| Trong critical section | Ghi `available = 0`, trả true | Vẫn chờ |
| Sau unlock | Hoàn tất reserve | Lấy lock, đọc 0, trả false |

Bảng này cho thấy B không thể xen vào giữa thao tác kiểm tra và ghi của A, vì chúng dùng một lock và đều truy cập state qua `reserveOne()`. Có thể viết kiểu bảng tương tự cho mọi invariant quan trọng để review.

### B. Tìm những lối đi bỏ qua lock

Câu hỏi cần kiểm tra trong code review:

- Shared field có `private` không? Caller có giữ tham chiếu tới một collection nội bộ rồi sửa trực tiếp?
- Constructor/initializer có làm rò rỉ `this` trước khi state sẵn sàng không?
- Mọi reader **và** writer có dùng cùng lock hoặc protocol visibility không?
- Có đường “fast path” đọc state không lock dù writer dùng lock?
- Có copy/snapshot nào được tạo **sau khi** thả lock nên có thể nhận nhiều phiên bản không?
- Method gọi callback khi giữ lock có thể gọi ngược vào object khác và tạo vòng chờ?
- Có multiple locks nào bị lấy theo thứ tự trái ngược?
- Có blocking operation chỉ có thể hoàn thành nhờ một thread đang chờ lock ấy không?

### C. Thử dưới cạnh tranh có kiểm soát

`CountDownLatch` hoặc `CyclicBarrier` có thể giúp điều khiển hai thread cùng bắt đầu để kiểm chứng invariant, thay vì dựa vào `Thread.sleep`. Test nên:

- Chờ tất cả worker bằng `join()`/`Future.get()`, không kiểm tra kết quả sớm.
- Xác minh một **kết quả bất biến**, ví dụ approved count không vượt stock, tổng tiền bảo toàn, version tương ứng số update.
- Lặp với số worker/tải khác nhau khi cần; song một stress test pass không phải chứng minh hình thức.
- Dùng timeout để tránh test treo vĩnh viễn khi xuất hiện deadlock, kèm thread dump/stack trace.
- Đo correctness riêng khỏi hiệu năng để tránh chấp nhận race vì nhanh hơn trong một lần chạy.

### D. Phân biệt deadlock, starvation và contention

| Hiện tượng | Dấu hiệu | Cách suy luận |
| --- | --- | --- |
| **Contention** | Nhiều thread mất thời gian chờ cùng lock | Giảm work độc lập trong lock, giảm shared state, đo blocked time |
| **Deadlock** | Các thread tạo chu trình chờ khóa/tài nguyên và không tiến triển | Phân tích lock order, chờ callback/`Future` dưới lock, lấy thread dump |
| **Starvation** | Một công việc có thể chờ rất lâu vì không có cơ hội được phục vụ | Xem queue, scheduling, fairness policy và load |
| **Race condition** | Kết quả sai tùy xen kẽ thao tác | Tìm compound action không được bảo vệ, invariant thiếu HB/atomicity |

Chỉ “thêm khóa lớn quanh mọi thứ” có thể làm race biến mất nhưng tạo contention hoặc deadlock. Giải pháp lâu dài là thiết kế lock ownership và boundary rõ ràng.

## 16. Một số phản-pattern và cách sửa

| Phản-pattern | Vì sao nguy hiểm | Hướng sửa |
| --- | --- | --- |
| Khóa riêng từng `get` và `set` trong một compound action | Thread khác chen vào giữa | Tạo một API atomic bao cả check/modify/write |
| Đọc field trần ngoài lock vì “chỉ đọc” | Thiếu visibility hoặc snapshot nhất quán | Đọc dưới cùng lock; immutable/volatile snapshot nếu phù hợp |
| Lấy snapshot của hai field bằng hai lock riêng | Có thể pha phiên bản | Chụp cặp dữ liệu dưới cùng critical section |
| Gọi HTTP/DB/file dưới monitor dữ liệu | Giữ lock qua latency không dự báo | Thiết kế state transition + side effect sau unlock |
| Gọi listener trong synchronized | Callback có thể block, reenter hoặc gây lock cycle | Copy listener/event snapshot, gọi ngoài lock |
| Tính kết quả từ snapshot cũ rồi commit không kiểm tra | Lost update hoặc vi phạm invariant | Lock cả decision hoặc version/CAS retry |
| Lấy A rồi B ở thread này, B rồi A ở thread khác | Deadlock | Áp dụng lock ordering thống nhất |
| Dùng `Thread.sleep` để tạo thứ tự | Không có hợp đồng đồng bộ | Dùng latch, condition, queue, future |
| Nhả lock mà không dùng `finally` với `ReentrantLock` | Dễ kẹt lock sau exception | `try/finally` |
| Tách lock quá sớm để tối ưu | Invariant chéo dễ bị bỏ sót | Kiểm chứng invariant rồi mới cân nhắc chia lock |
| Giữ lock qua `Future.get()` phụ thuộc worker cũng cần lock | Chu trình chờ | Thay protocol hoặc chờ ngoài lock |
| Chỉ thử trên một máy rồi kết luận an toàn | Race hiếm có thể không tái hiện | Lập luận JMM, invariant và kiểm thử cạnh tranh |

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Hai thread cùng làm `if (stock > 0) stock--`. Nếu `if` và `stock--` nằm trong **hai** khối synchronized riêng, có đủ không?  
**Đáp án:** Không. Thread khác có thể thay đổi stock giữa kiểm tra và cập nhật. Phải giữ chúng trong cùng critical section hoặc dùng một atomic operation biểu đạt đầy đủ điều kiện.

**Câu 2.** Parse chuỗi số dương từ input immutable có phụ thuộc balance hiện tại không?  
**Đáp án:** Không. Có thể parse/validate định dạng ngoài lock. Nhưng việc kiểm tra có đủ balance và thay đổi balance phải được bảo vệ cùng nhau.

**Câu 3.** Writer thay đổi `total` và `version` dưới hai lock khác nhau, reader đọc từng field dưới hai lock khác nhau. Snapshot có đảm bảo nhất quán không?  
**Đáp án:** Không. Invariant cặp field cần một giao thức đồng bộ thống nhất để đọc/ghi như một đơn vị logic.

**Câu 4.** Gọi một listener chậm dưới `synchronized` có thể làm hỏng correctness dù listener chỉ in log không?  
**Đáp án:** Có thể làm các thread khác chờ lâu; nếu listener gọi ngược hoặc đợi một thread cần cùng lock, có thể tạo chu trình chờ. Nên lấy snapshot và gọi ngoài lock khi semantics cho phép.

**Câu 5.** Có thể chụp state ở thời điểm t1, tính ngoài lock, rồi ghi ở t2 mà không so sánh version không?  
**Đáp án:** Chỉ nếu phép tính không phụ thuộc state có thể thay đổi hoặc có protocol khác bảo đảm. Nếu phụ thuộc snapshot t1, phải revalidate/CAS hay tính trong lock.

**Câu 6.** Hai thread chuyển khoản A→B và B→A, mỗi thread khóa nguồn trước rồi khóa đích. Nguy cơ gì?  
**Đáp án:** Deadlock do khóa ngược thứ tự. Cần thứ tự khóa toàn cục hoặc thiết kế khác có invariant rõ.

**Câu 7.** `volatile long balance` có thay cho một critical section kiểm tra đủ tiền và trừ tiền không?  
**Đáp án:** Không. `volatile` tạo visibility/order cho từng access, không biến check-then-act thành atomic.

**Câu 8.** Critical section tối ưu nhất là ít dòng nhất phải không?  
**Đáp án:** Không. Đúng phạm vi invariant mới là tiêu chí đầu tiên; sau đó mới loại những việc độc lập hoặc kéo dài thời gian giữ khóa.

## Tài liệu tham khảo Java SE

- [Java Language Specification `17 — Threads and Locks (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): monitor, synchronization, happens-before và data races.
- [`Object` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): `wait()`, `notify()`, `notifyAll()` và monitor.
- [`ReentrantLock` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): lock/unlock, tryLock, fairness và interruption.
- [`Lock` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Lock.html): hợp đồng synchronization.
- [`CountDownLatch` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): điều phối test cạnh tranh.
- [`AtomicInteger` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): atomic single-variable update.
- [`ConcurrentHashMap` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): cập nhật atomic theo key.

**Ghi nhớ:** Một critical section tốt phải **bao trọn quyết định dựa trên shared state**, sử dụng **một lock/protocol nhất quán**, và tránh giữ khóa qua công việc độc lập hay có thời gian không kiểm soát. Chọn ranh giới từ invariant, không chọn ranh giới từ số dòng mã.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-007">Lock ordering để ngăn deadlock</a>

<details>
<summary>Xem chi tiết</summary>

# Lock ordering để ngăn deadlock trong Java

## 1. Khi hai thread cần giữ nhiều khóa

Một thao tác đồng thời đôi khi phải cập nhật **nhiều đối tượng như một đơn vị nhất quán**: chuyển tiền giữa hai tài khoản, chuyển phần tử giữa hai cấu trúc, đổi chủ sở hữu một tài nguyên hoặc cập nhật hai thành phần có quan hệ với nhau. Để bảo vệ bất biến, code có thể phải giữ hai khóa cùng lúc.

Nhưng nếu các thread lấy khóa theo những thứ tự khác nhau, một thread có thể giữ khóa A và chờ B, trong khi thread khác giữ B và chờ A. Cả hai đều chờ tài nguyên do bên kia giữ; **deadlock (bế tắc)** xuất hiện và không thread nào tự tiến triển.

**Lock ordering** là quy tắc mọi đường code phải lấy nhiều khóa theo **cùng một thứ tự toàn cục ổn định**. Khi thứ tự được áp dụng nhất quán, những quan hệ chờ khóa đi một chiều theo thứ tự đó, loại bỏ kiểu vòng chờ phát sinh chỉ từ các khóa trong giao thức này.

Bài này tập trung vào **Synchronization và concurrency của Java nói chung**: intrinsic monitor qua `synchronized`, `ReentrantLock`, quy tắc thứ tự, giới hạn và cách chẩn đoán. Không cần framework để áp dụng.

### Kết quả cần đạt

Sau bài, bạn có thể:

1. Nhận diện vòng chờ trong wait-for graph và giải thích vì sao hai vùng khóa riêng lẻ vẫn có thể deadlock.
2. Định nghĩa thứ tự toàn cục trên các tài nguyên, kể cả tình huống hai caller truyền tham số đảo chiều.
3. Tạo ví dụ Java 17+ lấy khóa theo thứ tự cố định và giữ bất biến nhiều đối tượng.
4. Hiểu điều kiện để chứng minh lock ordering phòng tránh deadlock, giới hạn của `tryLock`/timeout và những bẫy với callback, khóa ẩn, lock khác loại.
5. Quan sát thread dump, trạng thái `BLOCKED` và kết quả `ThreadMXBean.findDeadlockedThreads()` để điều tra bế tắc monitor/ownable synchronizer.

## 2. Deadlock và wait-for graph

### 2.1. Hai đường đi đảo thứ tự khóa

Xét hai khóa A và B.

**Thread 1**:

~~~java
// MINH HỌA nguy cơ; lockA và lockB đã khai báo.
synchronized (lockA) {
    synchronized (lockB) {
        doWork();
    }
}
~~~

**Thread 2**:

~~~java
// MINH HỌA nguy cơ; cùng hai khóa ở trên nhưng ngược thứ tự.
synchronized (lockB) {
    synchronized (lockA) {
        doOtherWork();
    }
}
~~~

Nếu thread 1 lấy A, thread 2 lấy B, rồi mỗi bên yêu cầu khóa còn lại, chúng chờ nhau vô hạn nếu không có cơ chế can thiệp từ ngoài. `synchronized` không có timeout khi chờ lấy monitor; `Thread.interrupt()` đơn thuần không phá được luồng đang `BLOCKED` chờ vào một monitor.

Sơ đồ chờ:

~~~text
Thread 1 giữ A ---> cần B ---> B đang do Thread 2 giữ
      ^                               |
      |                               v
      +----------- Thread 2 cần A <---+
~~~

Đây là **vòng chờ tài nguyên**. Việc A và B được bảo vệ bởi hai `synchronized` riêng không sai; điều nguy hiểm là **thứ tự lồng khóa không thống nhất**.

### 2.2. Bốn điều kiện Coffman

Một deadlock kiểu tài nguyên khóa thường liên quan đồng thời bốn điều kiện:

| Điều kiện | Ý nghĩa trong bài toán khóa |
| --- | --- |
| Mutual exclusion | Một khóa chỉ cho một chủ sở hữu tại thời điểm tương ứng |
| Hold and wait | Thread đang giữ khóa này nhưng tiếp tục chờ khóa khác |
| No preemption | Không tự ý lấy lại khóa đang thuộc một thread khác |
| Circular wait | Tồn tại vòng A chờ B, B chờ C, …, Z chờ A |

Lock ordering chủ yếu **phá điều kiện circular wait** bằng cách quy định hướng lấy khóa. Nó không yêu cầu xóa mutual exclusion hoặc giảm bảo vệ bất biến. Nhưng lập luận chỉ đúng nếu **mọi đường đi tham gia cùng hệ khóa** tuân thủ thứ tự và các phụ thuộc/khóa ẩn khác cũng đã được xem xét.

Deadlock khác với **starvation** (một thread luôn bị thiệt khi cạnh tranh), **livelock** (thread liên tục đổi trạng thái mà không tạo tiến triển) và **blocking dài** do I/O. Một ứng dụng treo không mặc nhiên đồng nghĩa có chu trình deadlock.

## 3. Nguyên tắc lock ordering: đặt một thứ tự tổng

Giả sử mỗi tài nguyên có một khóa riêng. Gán mỗi tài nguyên một **thứ hạng duy nhất, ổn định**; ví dụ account 100 có thứ hạng nhỏ hơn account 200. Với hai khóa, code phải luôn:

1. Xác định khóa nào có thứ hạng thấp hơn.
2. Lấy khóa thấp trước.
3. Lấy khóa cao sau.
4. Thực hiện thao tác cần bảo vệ.
5. Thoát vùng khóa theo cơ chế tương ứng.

Ngay cả khi lời gọi là `transfer(from=A,to=B)` hoặc `transfer(from=B,to=A)`, thứ tự lấy khóa **không dựa vào hướng nghiệp vụ**; nó vẫn là A rồi B nếu A có thứ hạng thấp hơn.

### Vì sao quy tắc này ngăn vòng chờ?

Giả sử tất cả thread chỉ được giữ khóa có thứ hạng thấp khi chờ khóa có thứ hạng cao hơn. Khi có chuỗi chờ, thứ hạng các khóa đang được chờ phải tăng dần. Một vòng chờ sẽ đòi hỏi thứ hạng tăng mãi rồi quay về khóa đầu tiên nhỏ hơn, mâu thuẫn với thứ tự tăng nghiêm ngặt. Vậy không thể tạo chu trình **giữa các khóa cùng tuân thủ giao thức đó**.

Điều kiện thiết yếu:

- Mỗi tài nguyên khác nhau phải có thứ hạng **phân biệt**; trường hợp bằng nhau phải có quy tắc phá hòa rõ ràng.
- Thứ hạng **không đổi khi tài nguyên đang hoạt động**.
- **Mọi code lấy hai khóa** đều dùng cùng quy tắc, kể cả đọc snapshot nhiều đối tượng.
- Nếu đang giữ một khóa ở bậc cao, không gọi tiếp phương thức lấy khóa bậc thấp.
- Callback, listener, logging phức tạp hoặc gọi code ngoài phạm vi có thể lấy thêm khóa; cần tính cả khóa được lấy **gián tiếp**.

Đây là một nguyên tắc thiết kế áp dụng cho mọi đường đi của chương trình, không phải vài dòng code chỉ trong một phương thức.

## 4. Ví dụ chạy được — chuyển dữ liệu theo thứ tự khóa ổn định (Java 17+)

Giả sử hai tài khoản trong bộ nhớ có `balance`; chuyển giữa chúng cần nguyên tử xét trên **cả hai tài khoản**. Mỗi account có một monitor **riêng tư** và thứ hạng được cấp phát khi tạo.

Tạo tệp `OrderedTransferDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicLong;

public class OrderedTransferDemo {
    static final class Account {
        private static final AtomicLong NEXT_ID = new AtomicLong();
        private final long lockOrderId = NEXT_ID.getAndIncrement();
        private final Object lock = new Object();
        private int balance;

        Account(int initialBalance) {
            if (initialBalance < 0) {
                throw new IllegalArgumentException("Negative balance");
            }
            balance = initialBalance;
        }

        int balance() {
            synchronized (lock) {
                return balance;
            }
        }
    }

    static void transfer(Account from, Account to, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (from == to) {
            return;
        }

        Account first = from.lockOrderId < to.lockOrderId ? from : to;
        Account second = first == from ? to : from;

        synchronized (first.lock) {
            synchronized (second.lock) {
                if (from.balance < amount) {
                    throw new IllegalArgumentException("Insufficient funds");
                }
                from.balance -= amount;
                to.balance += amount;
            }
        }
    }

    static int total(Account a, Account b) {
        if (a == b) {
            return a.balance();
        }

        Account first = a.lockOrderId < b.lockOrderId ? a : b;
        Account second = first == a ? b : a;

        synchronized (first.lock) {
            synchronized (second.lock) {
                return a.balance + b.balance;
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Account a = new Account(100);
        Account b = new Account(100);

        Thread one = new Thread(
            () -> transfer(a, b, 10), "transfer-a-to-b"
        );
        Thread two = new Thread(
            () -> transfer(b, a, 20), "transfer-b-to-a"
        );

        one.start();
        two.start();
        one.join();
        two.join();

        System.out.println(
            "A=" + a.balance() + ", B=" + b.balance()
        );
        System.out.println("total=" + total(a, b));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac OrderedTransferDemo.java
java OrderedTransferDemo
~~~

Stdout khi cả hai chuyển khoản hoàn thành bình thường:

~~~text
A=110, B=90
total=200
~~~

**Giải thích kết quả:**

- Tài khoản A bắt đầu 100, gửi 10 và nhận 20, nên cuối cùng 110. B nhận 10 rồi gửi 20, nên cuối cùng 90. Cả hai giao dịch đều hợp lệ bất kể thứ tự thread nào lấy khóa trước.
- `AtomicLong` cấp thứ hạng riêng cho từng `Account` trong ví dụ. Khóa dùng để bảo vệ `balance` là `private final Object lock`, nên caller không thể vô tình `synchronized` trên một monitor công khai.
- Cả `transfer` lẫn `total` đều xếp thứ tự khóa theo `lockOrderId` và lấy `first.lock` trước `second.lock`. Vì vậy không có đường gọi nào đảo A/B chỉ vì đổi chiều chuyển khoản.
- Hai cập nhật `from.balance -= amount` và `to.balance += amount` xảy ra khi giữ cả hai monitor; một thao tác đọc tổng hợp cũng giữ đúng hai monitor nên không nhìn thấy trạng thái chuyển dở.
- `balance()` cũng lấy monitor tương ứng. Trong ví dụ, hai lần đọc riêng lẻ chỉ chạy **sau `join()`**, khi không còn chuyển khoản đang hoạt động. Nếu muốn đọc đồng thời **cả hai số dư như một snapshot**, phải dùng một phương thức lấy cả hai khóa theo thứ tự như `total` hoặc trả về một immutable snapshot chứa cả hai trường.
- `Thread.join()` chờ mỗi thread kết thúc, nhưng bản thân `join()` không làm lại một giao dịch thất bại và không tự báo lỗi của thread. Trong sản phẩm thực, cần có cơ chế nhận kết quả/exception của task thay vì chỉ tin rằng thread đã dừng là thành công.

**Phạm vi ví dụ:** Code quản lý hai object nằm trong một tiến trình JVM. Nó minh họa khóa bộ nhớ Java; không thay thế transaction hoặc đồng bộ liên tiến trình cho dữ liệu ở database hay dịch vụ khác. Với trường hợp thực tế có thể tạo số lượng tài khoản đủ để thứ hạng `long` tràn vòng, phải dùng cơ chế cấp ID không trùng hoặc xử lý tràn; ví dụ giả định ID chưa tràn.

### Điều gì xảy ra khi thử viết theo chiều chuyển khoản?

Thiết kế nguy hiểm thường viết:

~~~java
// Minh họa sai, không phải chương trình độc lập.
synchronized (from.lock) {
    synchronized (to.lock) {
        moveBalance(from, to, amount);
    }
}
~~~

Nếu một caller chuyển A → B và caller khác chuyển B → A, chúng lấy khóa theo thứ tự ngược nhau. Vì thế khóa phải được sắp theo **thứ tự tài nguyên**, không theo vai trò `from`/`to`.

## 5. Chọn khóa và thứ hạng như thế nào?

### 5.1. Dùng khóa riêng thay vì khóa dễ bị code ngoài nắm giữ

Một đối tượng công khai hoặc `String` được chia sẻ rộng có thể vô tình bị thành phần khác dùng làm monitor. Khi đó phương thức của bạn không biết những khóa nào đã được giữ trước khi nó chạy.

Ưu tiên:

~~~java
// Minh họa trong một class; không phải chương trình độc lập.
private final Object lock = new Object();

void update() {
    synchronized (lock) {
        // Bảo vệ trạng thái nội bộ.
    }
}
~~~

Không dùng `synchronized(this)` theo thói quen nếu instance được trao cho nhiều thành phần và bạn không kiểm soát các chỗ có thể khóa nó. Một khóa `private final` giúp thể hiện ownership. Nhưng một class dùng nhiều khóa riêng vẫn phải xác định quan hệ thứ tự giữa chúng nếu có các phương thức giữ hơn một khóa.

### 5.2. Chọn rank bất biến và có thứ tự toàn phần

Một ID nghiệp vụ **duy nhất và bất biến** là lựa chọn tốt nếu hợp đồng bảo đảm nó thực sự duy nhất. Nếu ID có thể thay đổi, lấy khóa dựa vào ID động sẽ khiến hai thread dùng hai thứ tự khác nhau.

Có thể cấp riêng một rank lúc xây dựng object. Với nhiều cấp độ khóa, hãy định nghĩa thứ tự chung, ví dụ:

~~~text
Lock level 10: cấu hình toàn cục
Lock level 20: session
Lock level 30: tài nguyên con
Lock level 40: cache cục bộ
~~~

Nếu giữ khóa cấp 30 rồi gọi code cần khóa cấp 10, đường gọi đó vi phạm giao thức. Không cần lúc nào cũng giữ cả bốn khóa; quy tắc chỉ áp dụng khi nhiều khóa được giữ đồng thời.

### 5.3. Cẩn trọng với `hashCode()` và `System.identityHashCode()`

Không nên tự động sắp khóa theo `object.hashCode()`:

- `hashCode()` có thể do class override, có thể dựa trên trạng thái mutable hoặc không duy nhất.
- `System.identityHashCode(object)` ổn định theo identity trong vòng đời object nhưng **có thể va chạm** giữa hai object khác nhau.
- Nếu hai thread xử lý cặp A/B có identity hash bằng nhau rồi “giữ nguyên thứ tự đầu vào”, chúng vẫn có thể đảo khóa và deadlock.

Nếu thiết kế dựa trên identity hash, cần một **tie lock** chung dùng trong trường hợp va chạm, hoặc tốt hơn dùng rank phân biệt được cấp một cách có kiểm soát. Không giả định hai hash code luôn khác nhau.

### 5.4. Nếu hai tham số trỏ cùng một đối tượng?

Đối tượng khóa monitor Java có tính **reentrant**: cùng thread có thể `synchronized` cùng một monitor nhiều lần mà không tự deadlock. Nhưng thiết kế vẫn nên xử lý `from == to` rõ ràng, vì đó có thể là giao dịch vô nghĩa hoặc cần hành vi nghiệp vụ riêng.

Với `ReentrantLock`, cùng thread cũng có thể acquire lại một lock mình đang sở hữu; phải release đúng số lần acquire. Với **khóa không reentrant** hoặc primitive khác, lấy lại cùng khóa có thể tự bế tắc. Vì vậy quy tắc xếp khóa nên xử lý tham số trùng và loại primitive đang sử dụng.

## 6. Với ba khóa trở lên: thứ tự phải nhất quán toàn hệ

Giả sử có A, B, C được xếp A < B < C. Những đường đi hợp lệ khi cần nhiều khóa sẽ đi theo chiều tăng, ví dụ A → C hoặc A → B → C. Một đường đi C → A có thể tạo vòng với đường khác và phải được sửa.

### Ví dụ ba thread có vòng chờ

~~~text
Thread 1: giữ A, chờ B
Thread 2: giữ B, chờ C
Thread 3: giữ C, chờ A

Wait-for graph:
  T1 --> T2 --> T3 --> T1
~~~

Chỉ nhìn từng phương thức riêng có thể không thấy vòng. Nó có thể hình thành qua lời gọi lồng nhau hoặc giữa các module có cùng tài nguyên chia sẻ.

Nếu một code path cần lấy nhiều lock, hãy tập hợp các lock, loại bỏ phần tử trùng theo identity và **sắp theo thứ hạng chung** trước khi acquire. Khi dùng `Lock`, release tất cả khóa đã lấy trong `finally`, theo thứ tự ngược lại cho dễ kiểm chứng.

**Đừng chỉ sắp theo dữ liệu “hiện tại”** nếu khóa liên quan có thể được thay thế hoặc rank thay đổi trong lúc thực thi. Thứ hạng dùng để xếp phải bền vững và các caller phải chia sẻ cùng định nghĩa.

### Bảng review thứ tự khóa

| Đường code | Khóa lấy lần lượt | Đạt quy tắc A < B < C? |
| --- | --- | --- |
| Xử lý A rồi B | A → B | Có |
| Đọc snapshot A và C | A → C | Có |
| Kiểm tra B rồi C | B → C | Có |
| Cập nhật C rồi gọi hàm dùng A | C → A | **Không** |
| Lấy A rồi B rồi C | A → B → C | Có |
| Gọi callback khi giữ C và callback lấy B | C → B | **Không** |

Việc lập bản đồ những đường gọi như vậy thường phát hiện được rủi ro bị che giấu bởi helper method hoặc callback.

## 7. `synchronized` và `ReentrantLock` trong lock ordering

### 7.1. Intrinsic monitor

`synchronized` có ưu điểm tự release monitor khi thoát block, kể cả khi có exception. Nhược điểm khi debug deadlock là yêu cầu vào monitor không có timeout hoặc cơ chế `lockInterruptibly`; một thread bị `BLOCKED` đợi monitor không thể chỉ dùng interrupt để kết thúc ngay việc chờ lock.

Thứ tự toàn cục là cách phòng tránh thực chất; nếu đã mắc kẹt, thường phải điều tra thread dump và sửa code.

### 7.2. Explicit lock

`ReentrantLock` hỗ trợ `lock()`, `tryLock()`, `tryLock(timeout, unit)` và `lockInterruptibly()`. Nó cho phép quyết định **không tiếp tục chờ vô hạn** trong một số trường hợp, nhưng không tự áp dụng thứ tự khóa.

Ví dụ mẫu có hai lock đã được xếp đúng thứ tự:

~~~java
// Minh họa Java 17+, giả sử first/second là ReentrantLock phân biệt.
first.lock();
try {
    second.lock();
    try {
        updateTogether();
    } finally {
        second.unlock();
    }
} finally {
    first.unlock();
}
~~~

Luôn unlock đúng khóa, kể cả khi việc cập nhật ném exception. Nếu có nhiều khóa hơn, nên gói thứ tự và cleanup trong một abstraction rõ ràng; tránh copy-paste phức tạp dẫn đến unlock sai.

**Chú ý:** `ReentrantLock.lock()` có thể chờ vô hạn, kể cả nếu thread bị interrupt trước hoặc trong khi chờ; sau khi acquire có thể vẫn còn interrupt flag. Nếu muốn thao tác lấy khóa phản ứng với interrupt, dùng `lockInterruptibly()` hoặc timed `tryLock` và xử lý `InterruptedException` phù hợp.

### 7.3. `tryLock` là cách thất bại có kiểm soát, không thay thế toàn bộ thiết kế

`tryLock()` có thể trả về `false` ngay nếu chưa lấy được khóa. `tryLock(timeout, unit)` chờ có giới hạn và có thể ném `InterruptedException`. Khi lấy nhiều khóa, nếu không lấy được khóa tiếp theo, phải release những khóa đã giữ, rồi trả kết quả thất bại hoặc retry theo chính sách.

Một ví dụ ngắn, hoàn chỉnh, cho thấy lựa chọn **không block vô thời hạn**.

Tạo tệp `TryLockDemo.java`:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class TryLockDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();

        try {
            Thread worker = new Thread(() -> {
                if (lock.tryLock()) {
                    try {
                        System.out.println("Acquired");
                    } finally {
                        lock.unlock();
                    }
                } else {
                    System.out.println("Lock unavailable");
                }
            }, "competitor");

            worker.start();
            worker.join();
        } finally {
            lock.unlock();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac TryLockDemo.java
java TryLockDemo
~~~

Stdout:

~~~text
Lock unavailable
~~~

Thread `main` đang giữ `ReentrantLock` trong suốt khoảng worker gọi `tryLock()`. Worker ở thread khác nên `tryLock()` thất bại ngay, in một dòng rồi kết thúc. `join()` trở về, sau đó `main` release khóa trong `finally`. Đây là ví dụ đã được sắp đặt để kết quả xác định; trong chương trình bình thường, `tryLock` có thể thành công hoặc thất bại tùy tình trạng khóa.

Cần phân biệt **tránh block vô hạn** với **bảo đảm cập nhật thành công**: khi không lấy được khóa, thao tác nghiệp vụ chưa được thực hiện. Caller phải có quy tắc trả lỗi, xếp hàng, hủy, retry hữu hạn hoặc chờ đến deadline, không được giả vờ đã cập nhật.

### 7.4. Retry và backoff cũng có bẫy

Nếu hai thread cùng lấy một khóa rồi không lấy được khóa thứ hai và lập tức retry đồng loạt, chúng có thể rơi vào **livelock** hoặc làm CPU bận. Giải pháp có thể là backoff, jitter và giới hạn số lần thử; tuy nhiên retry không sửa thứ tự khóa không nhất quán ở lõi thiết kế. Hãy ưu tiên quy tắc lock ordering khi có thể.

Với timed `tryLock`, deadline của nghiệp vụ cần tính qua **toàn bộ chuỗi khóa**, tránh chờ tối đa N giây cho mỗi khóa rồi nhân độ trễ lên nhiều lần.

## 8. Deadlock do khóa ẩn và gọi code bên ngoài

Ngay cả khi một phương thức của bạn luôn lấy A rồi B, chương trình vẫn có thể deadlock nếu nó đang giữ một khóa không được tính vào thứ tự. Ví dụ:

- Giữ lock account rồi gọi callback do plugin viết; callback cố lấy lock cấu hình toàn cục có rank thấp hơn.
- Giữ lock cache rồi gọi phương thức có `synchronized` trên object khác mà không biết; luồng kia giữ object đó rồi cần cache.
- Giữ monitor khi thực hiện logging, observer notification hoặc hàm override có thể gọi ngược vào code gốc.
- Giữ lock khi chờ một `Future.get()`; task trong future cần chính lock đang bị giữ.
- Tự tạo dependency giữa worker trong một thread pool có một worker: task A chờ task B trong cùng pool nhưng B chưa có worker để chạy.

Hai trường hợp cuối có thể là **deadlock phụ thuộc công việc** hoặc pool starvation, dù không nhất thiết có vòng đợi hai monitor. Thứ tự khóa monitor đơn thuần không xử lý mọi loại chờ.

Các nguyên tắc phòng ngừa:

1. Giữ critical section **ngắn**; chuẩn bị dữ liệu bên ngoài khóa khi an toàn.
2. Không gọi callbacks hoặc mã của bên thứ ba trong lúc giữ lock nếu có thể.
3. Không đợi I/O, network, user input hoặc task khác vô hạn khi đang giữ lock.
4. Tài liệu hóa khóa nào được mỗi phương thức yêu cầu; nếu phương thức gọi sâu vào code khác, phải kiểm tra những khóa ẩn.
5. Nếu phải giữ nhiều lock, xem chúng là **một giao thức thứ tự duy nhất** xuyên các lớp liên quan.

## 9. Làm sao phát hiện deadlock trong JVM?

### 9.1. Biểu hiện

Một số dấu hiệu: request treo liên tục, thread không kết thúc dù không có tiến triển, CPU có thể thấp, thread dump có các luồng `BLOCKED` hoặc `WAITING`. Nhưng CPU thấp và nhiều thread đang chờ chưa đủ để kết luận deadlock: ứng dụng bình thường cũng có worker đang chờ queue/lock.

**Bằng chứng mạnh** là một chu trình tài nguyên trong đó mỗi thread giữ một khóa mà thread khác đang yêu cầu, hoặc công cụ JVM chỉ ra deadlock.

### 9.2. Thread dump và công cụ JDK

Có thể sử dụng các công cụ theo bản JDK/JVM đã triển khai:

~~~bash
jcmd <PID> Thread.print -l
~~~

hoặc khi `jstack` có sẵn:

~~~bash
jstack -l <PID>
~~~

`<PID>` là process ID thật của JVM; dòng trên là **mẫu lệnh**, cần thay bằng PID thực tế. Không phải stdout của một chương trình demo.

Trong thread dump, quan sát tên thread, stack frame đang đợi monitor, thông tin khóa đang sở hữu và khóa muốn lấy. Với deadlock monitor được JVM nhận diện, nhiều bản dump có phần thông báo deadlock kèm chuỗi khóa/owner.

Các trạng thái có ý nghĩa:

| Trạng thái thường thấy | Diễn giải |
| --- | --- |
| `BLOCKED` | Thread chờ lấy intrinsic monitor cho một vùng `synchronized` |
| `WAITING` | Có thể chờ `Object.wait`, `LockSupport.park`, `Thread.join` hoặc primitive khác |
| `TIMED_WAITING` | Chờ có deadline/time limit; không mặc nhiên deadlock |
| `RUNNABLE` | Đang thực thi hoặc sẵn sàng chạy; không tự chứng minh có tiến triển hữu ích |

Với `ReentrantLock`, thread có thể xuất hiện ở `WAITING` hoặc `TIMED_WAITING` (đợi qua `LockSupport`), không phải `BLOCKED` chờ intrinsic monitor. Vì vậy không chỉ lọc các thread `BLOCKED` khi điều tra.

### 9.3. ThreadMXBean

`java.lang.management.ThreadMXBean` cung cấp:

- `findMonitorDeadlockedThreads()`: phát hiện vòng deadlock liên quan **object monitor**.
- `findDeadlockedThreads()`: tìm vòng deadlock liên quan **object monitor và ownable synchronizer**, khi JVM hỗ trợ khả năng này; không phát hiện mọi dạng chờ ngoài JVM hoặc deadlock phụ thuộc hệ thống.
- `getThreadInfo(ids, true, true)`: lấy thông tin monitor và synchronizer theo khả năng hỗ trợ của JVM.

Ví dụ cách truy vấn, **không phải chương trình độc lập**:

~~~java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

ThreadMXBean bean = ManagementFactory.getThreadMXBean();
if (bean.isSynchronizerUsageSupported()) {
    long[] ids = bean.findDeadlockedThreads();
    if (ids != null) {
        System.out.println("Deadlocked threads: " + ids.length);
    }
}
~~~

Đoạn minh họa trên chỉ truy vấn thông tin; ứng dụng phải có cơ chế cảnh báo/điều tra và tránh lặp kiểm tra với tần suất gây chi phí cao. Việc không tìm thấy chu trình tại một thời điểm không chứng minh mọi luồng đang tiến triển tốt, cũng không loại trừ deadlock xảy ra sau đó.

### 9.4. Khi phát hiện deadlock trong production

Thu thập thread dump hoặc JFR/log phù hợp, xác định thread nào giữ/chờ khóa nào, lần theo nguồn gọi vào monitor/lock, vẽ wait-for graph và tìm đường đi đảo thứ tự. Sửa giao thức cấp khóa ở mã nguồn; tránh coi việc restart tiến trình là lời giải gốc. Nếu cần can thiệp vận hành, chú ý việc force-stop các thread khi chúng giữ bất biến có thể phá hỏng trạng thái.

Không sử dụng `Thread.stop()` để “phá deadlock”: API này không an toàn, đã deprecated và không phải chiến lược phục hồi đúng.

## 10. Kiểm thử quy tắc thứ tự khóa

Một test đơn thuần khởi chạy hai thread rồi ngủ 100 ms không chứng minh không có deadlock; các thread có thể chạy tuần tự trong lần test đó.

Để kiểm tra có ý nghĩa:

1. Thiết kế **invariant** cần giữ: tổng số dư, số phần tử, trạng thái cập nhật, thứ tự bản ghi.
2. Khởi chạy nhiều task theo cả hai hướng thao tác (A → B, B → A), có thể dùng `CountDownLatch` hoặc `CyclicBarrier` để bắt đầu cạnh tranh cùng lúc.
3. Đợi hoàn tất với timeout ở tầng test; nếu timeout, thu thập thread dump và xác định vì sao. Timeout không tự chứng minh deadlock.
4. Kiểm tra kết quả/invariant sau khi mọi task hoàn tất; đừng xem “không treo” là đủ.
5. Phân tích **tất cả đường gọi và khóa ẩn**, bao gồm snapshot getter, callback và error handler.
6. Chạy stress test phù hợp và đo contention/latency, nhưng xem **lập luận thứ tự** là bảo đảm thiết kế quan trọng nhất.

Một ví dụ test có thể sinh nhiều cặp account, chuyển khoản qua lại, rồi xác minh tổng không đổi, số dư không âm, không thread nào kẹt. Cần giới hạn số task và thời gian chờ, để test không bị treo vĩnh viễn khi phát hiện lỗi.

## 11. Những sai lầm phổ biến

**“Lúc nào cũng lấy khóa của đối tượng `from` trước `to`.”** Khi hai caller đi hai chiều ngược nhau, chúng đảo thứ tự và có thể tạo vòng chờ. Xếp theo rank ổn định độc lập với hướng nghiệp vụ.

**“`hashCode` là thứ tự duy nhất.”** Hash có thể va chạm và bị override. Dùng rank riêng duy nhất hoặc có tie-breaker được áp dụng nhất quán.

**“Chỉ cần sửa chỗ có hai `synchronized` lồng nhau là xong.”** Code có thể lấy khóa gián tiếp qua helper/callback; phải kiểm tra toàn bộ call graph và đường lấy khóa nhiều cấp.

**“`ReentrantLock` tự tránh deadlock.”** Lock explicit vẫn có thể lấy sai thứ tự. `tryLock`/timeout giúp không chờ mãi ở một số tình huống nhưng cần cleanup và retry policy.

**“Lấy hai khóa rất nhanh nên không thể deadlock.”** Deadlock phụ thuộc thứ tự cạnh tranh, không chỉ thời gian critical section. Thời gian giữ khóa ngắn giảm xác suất, không chứng minh an toàn.

**“Chỉ thread BLOCKED mới có deadlock.”** `ReentrantLock` chờ qua `LockSupport`; phụ thuộc `Future` hoặc queue có thể khiến thread `WAITING`. Đọc stack và thông tin owner.

**“Mọi timeout đều là deadlock.”** Timeout có thể do CPU quá tải, I/O lâu, starvation hoặc pool cạn worker. Cần bằng chứng của chuỗi chờ và loại tài nguyên.

**“Đã dùng lock ordering thì mọi lỗi concurrency biến mất.”** Thứ tự khóa chỉ xử lý một nhóm deadlock; dữ liệu vẫn có thể bị race nếu có đường đọc/ghi không khóa, hay hệ thống vẫn có livelock, starvation, hủy lỗi, thời gian chờ và sự cố external resource.

## 12. Checklist review trước khi đưa vào ứng dụng

| Câu hỏi | Tiêu chí |
| --- | --- |
| Có tài nguyên nào cần giữ hai khóa cùng lúc? | Đã liệt kê tất cả đường gọi, kể cả đọc snapshot |
| Rank của mỗi khóa có duy nhất và ổn định? | Không phụ thuộc thứ tự đầu vào, trạng thái mutable hoặc hash có thể va chạm |
| Mọi đường code có lấy khóa theo cùng thứ tự? | Không có helper/callback lén lấy khóa bậc thấp trong khi giữ bậc cao |
| Lock có được đóng gói riêng? | Tránh để code ngoài giữ monitor không biết trước |
| Code có gọi I/O hoặc chờ task trong lúc giữ lock? | Đã giảm tối đa các lời chờ không giới hạn |
| `ReentrantLock` có unlock đúng trong `finally`? | Mỗi lần acquire có release tương ứng |
| Thất bại lấy khóa có trả kết quả rõ ràng? | Không cập nhật dở, không retry vô hạn |
| Có cách điều tra deadlock? | Thread dump/JFR/metrics, stack và lock owner |
| Có kiểm tra bất biến sau khi chạy đồng thời? | Test nhiều hướng, có timeout và chẩn đoán |
| Đã hiểu giới hạn? | Lock ordering không tự xử lý resource deadlock ngoài phạm vi khóa đã xếp |

### Câu hỏi tự kiểm tra

**1.** Thread 1 lấy A → B; thread 2 lấy B → A. Khi nào deadlock xuất hiện?

Khi thread 1 giữ A và cần B trong lúc thread 2 giữ B và cần A, tạo vòng chờ. Việc mỗi lock riêng lẻ hoạt động đúng không loại bỏ vòng này.

**2.** Trong một hệ thống có rank A < B, chuyển tiền từ B sang A nên lấy khóa nào trước?

Vẫn là **A rồi B**, bất kể chiều nghiệp vụ B → A.

**3.** Vì sao xếp khóa theo `System.identityHashCode` mà không xử lý giá trị bằng nhau là chưa đủ?

Hai object khác nhau có thể có cùng identity hash. Nếu hai thread tự giữ thứ tự tham số khi bằng nhau, chúng có thể lấy A/B ngược nhau. Cần tie lock hoặc rank duy nhất.

**4.** Một thread đang `BLOCKED` chờ vào `synchronized`. Gọi `interrupt()` có tự động làm nó dừng chờ monitor không?

Không. Yêu cầu vào intrinsic monitor không phải một phép chờ interruptible theo kiểu `ReentrantLock.lockInterruptibly()`. Phải thiết kế tránh vòng chờ hoặc dùng primitive phù hợp từ đầu.

**5.** Hai thread luôn lấy A rồi B, nhưng trong lúc giữ B có callback cố lấy khóa C, còn một thread thứ ba giữ C và cần A. Đã đủ chứng minh không có deadlock chưa?

Chưa. Khóa C là một phần của wait-for graph và phải được đưa vào thứ tự toàn cục, hoặc không gọi callback trong critical section.

**Ghi nhớ:** Khi cần đồng thời nhiều khóa, hãy **định nghĩa một thứ tự tổng ổn định trên tài nguyên và tuân thủ ở mọi đường lấy khóa**. Chứng minh được không tồn tại cạnh chờ đi ngược thứ tự giúp loại bỏ deadlock do vòng chờ của những khóa này, đồng thời vẫn duy trì khóa cần thiết để bảo vệ bất biến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-008">Tránh khóa trên String literal hay public object</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh khóa trên String literal hay public object trong Java

`synchronized` sử dụng **monitor gắn với một object cụ thể**. Nếu object khóa có thể được các thành phần khác truy cập, chúng cũng có thể lấy đúng monitor đó và chặn mã của mình. Hai lỗi thiết kế thường gặp là khóa trên **String literal**, vốn có thể được intern và chia sẻ, hoặc khóa trên **public object**, vốn cho phép caller chủ động giữ monitor nội bộ.

Bài này thuộc mục **Synchronization** trong **Java concurrency nói chung (Java 17+)**. Chỉ đề cập ngôn ngữ Java và thư viện chuẩn, không đi vào cơ chế riêng của Spring.

Mục tiêu: hiểu monitor identity, lý do String literal bị chia sẻ, rủi ro của public lock và `this`/class monitor; tạo khóa `private final Object` đúng phạm vi; nhận diện contention/deadlock, bảo đảm mọi reader/writer dùng cùng lock và tránh sửa một API hiện có mà phá hợp đồng đồng bộ.

## 1. Monitor của synchronized là identity của object

Một khối đồng bộ trong Java có dạng:

~~~java
// Mã minh họa: lock đã được khai báo trước.
synchronized (lock) {
    // Critical section dùng monitor của object mà lock tham chiếu.
}
~~~

Khi thread đi vào khối, nó **lấy monitor** của object. Khi ra khỏi khối, kể cả vì exception, monitor được **nhả**. Thread khác muốn vào một khối `synchronized` dùng **đúng cùng object** sẽ phải đợi monitor được nhả.

Khóa được phân biệt theo **reference identity** (`==`), không theo `equals()`, hash code hoặc tên biến. Hai reference trỏ cùng object thì dùng cùng monitor; hai object khác nhau dù nội dung giống nhau thì không tự dùng chung monitor.

~~~java
Object a = new Object();
Object b = a;
Object c = new Object();

System.out.println(a == b); // true
System.out.println(a == c); // false
~~~

Đây là đoạn mã minh họa các identity, không phải file chạy độc lập.

### Vì sao khóa quan trọng về bộ nhớ?

Nếu toàn bộ truy cập một bất biến dùng **cùng monitor**:

- **Mutual exclusion:** chỉ một thread giữ monitor tại một thời điểm, nên hai critical sections dùng cùng monitor không chen vào nhau.
- **Visibility và ordering:** lần unlock monitor `m` *synchronizes-with* lần lock sau trên cùng monitor `m` theo Java Memory Model. Kết hợp program order và tính bắc cầu của *happens-before*, thread vào khóa sau có thể dựa vào các ghi mà thread trước thực hiện trong phạm vi được đồng bộ.

Dùng hai monitor khác nhau sẽ **không bảo vệ cùng một bất biến** nếu hai thread cập nhật cùng dữ liệu. Ngược lại, vô tình dùng **một monitor chung** cho hai nghiệp vụ độc lập có thể gây tranh chấp không cần thiết.

## 2. String literal có thể trỏ tới cùng object toàn cục

String literal trong Java được **intern**; những literal cùng nội dung có thể trỏ đến cùng một instance `String`, kể cả nằm trong các class không liên quan. Chuỗi là immutable nhưng vẫn là một object có monitor; **bất biến của nội dung không làm nó trở thành khóa riêng tư**.

Ví dụ Java 17+ chạy độc lập:

~~~java
// StringLiteralIdentityDemo.java
public class StringLiteralIdentityDemo {
    public static void main(String[] args) {
        String first = "shared-lock";
        String second = "shared-lock";
        String constantExpression = "shared-" + "lock";
        String distinct = new String("shared-lock");

        System.out.println("same literal: " + (first == second));
        System.out.println("constant expression: " +
            (first == constantExpression));
        System.out.println("new String same object: " +
            (first == distinct));
        System.out.println("intern returns literal: " +
            (first == distinct.intern()));
    }
}
~~~

~~~bash
javac StringLiteralIdentityDemo.java
java StringLiteralIdentityDemo
~~~

Stdout:

~~~text
same literal: true
constant expression: true
new String same object: false
intern returns literal: true
~~~

Giải thích:
- Hai literal giống nhau trỏ tới cùng interned object.
- Phép nối hằng `"shared-" + "lock"` là constant expression, được biên dịch thành literal tương ứng.
- `new String(...)` tạo một object String riêng, dù nội dung bằng nhau.
- `intern()` trả về reference chuẩn cho chuỗi đã intern.

### Hai class không liên quan vẫn có thể chặn nhau

~~~java
// Hai đoạn mã minh họa ở hai class khác nhau.
// Class A:
synchronized ("CACHE_LOCK") {
    updateCache();
}

// Class B:
synchronized ("CACHE_LOCK") {
    writeAuditLog();
}
~~~

Hai khối cùng literal `"CACHE_LOCK"` có thể **tranh chấp cùng monitor**, dù `updateCache()` và `writeAuditLog()` độc lập. Nếu B giữ khóa lâu, A phải đợi. Nếu có các khóa khác đang được giữ, cạnh chờ ngoài ý muốn còn có thể góp phần tạo deadlock.

**Sai lầm dễ bỏ sót:** khai báo `private static final String LOCK = "CACHE_LOCK";` **không đủ**. Field là private nhưng **object String mà nó trỏ tới vẫn là interned literal có thể lấy bằng cùng chuỗi**. `final` chỉ làm reference của field không bị gán lại.

Không nên chữa bằng `new String("CACHE_LOCK")` như một khuôn mẫu: object mới có identity riêng trong tình huống đơn giản, nhưng kiểu String không nói lên ý định khóa, có thể bị gán cho nơi khác và dễ khiến người đọc hiểu nhầm giữa equality với identity. Dùng `private final Object` hoặc `private static final Object` làm monitor chuyên biệt dễ kiểm soát hơn.

## 3. Public object cho mã ngoài quyền giữ monitor nội bộ

Cấu trúc nên tránh:

~~~java
// Mã minh họa API thiếu đóng gói:
public class Counter {
    public static final Object LOCK = new Object();
    private static int value;

    public static void increment() {
        synchronized (LOCK) {
            value++;
        }
    }
}
~~~

Code ngoài có thể làm:

~~~java
synchronized (Counter.LOCK) {
    // Công việc chậm hoặc chờ một điều kiện bất kỳ.
}
~~~

Từ thời điểm code ngoài giữ monitor cho tới khi nó nhả, mọi `Counter.increment()` sẽ phải chờ. `public static final` giữ identity ổn định nhưng **không ngăn caller lấy được object**. Lộ monitor là một phụ thuộc API về tiến độ và có thể tạo lock contention, DoS ở mức ứng dụng hoặc vòng chờ.

Các cách lộ tương tự: một field `public`/`protected` chứa lock, getter trả reference lock, truyền lock vào callback hay thành phần không kiểm soát, hoặc dùng object instance công khai của lớp làm monitor.

### Ví dụ xác định: mã ngoài can thiệp vào tiến độ

Chương trình dưới cố ý cung cấp public lock. Hai `CountDownLatch` tạo thứ tự rõ ràng, tránh `Thread.sleep` để cố đoán lịch chạy.

~~~java
// PublicLockInterferenceDemo.java
import java.util.concurrent.CountDownLatch;

public class PublicLockInterferenceDemo {
    static class UnsafeCounter {
        public final Object lock = new Object();
        private int value;

        void increment() {
            synchronized (lock) {
                value++;
            }
        }

        int valueAfterCoordination() {
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        UnsafeCounter counter = new UnsafeCounter();
        CountDownLatch outsideHasLock = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread outside = new Thread(() -> {
            synchronized (counter.lock) {
                outsideHasLock.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        outside.start();
        outsideHasLock.await();

        Thread worker = new Thread(counter::increment);
        worker.start();

        System.out.println("Before release: " +
            counter.valueAfterCoordination());
        release.countDown();

        outside.join();
        worker.join();
        System.out.println("After release: " +
            counter.valueAfterCoordination());
    }
}
~~~

~~~bash
javac PublicLockInterferenceDemo.java
java PublicLockInterferenceDemo
~~~

Stdout:

~~~text
Before release: 0
After release: 1
~~~

Điểm bảo đảm: main chỉ khởi động worker **sau khi** thread ngoài xác nhận đã lấy monitor. Worker không thể cập nhật `value` trước khi monitor được nhả. Main đọc `value == 0` trước khi mở latch; sau khi `outside.join()` và `worker.join()`, giá trị là 1. Các `join()` cũng tạo bảo đảm visibility cho lần đọc cuối.

Đây là **ví dụ cố tình để lộ khóa**, không phải API gợi ý dùng trong production. Getter `valueAfterCoordination()` chỉ được đọc tại các điểm phối hợp này; trong code thực tế, thao tác đọc và cập nhật bất biến phải được đồng bộ bằng cùng giao thức.

## 4. synchronized(this) và synchronized instance method

Một phương thức:

~~~java
public synchronized void update() {
    // ...
}
~~~

dùng monitor của `this`, tương đương về monitor với:

~~~java
public void update() {
    synchronized (this) {
        // ...
    }
}
~~~

Hai đoạn trên là mã minh họa **hai cách viết**, không phải chương trình độc lập. Nếu object instance đã được đưa cho caller, caller cũng có thể gọi `synchronized(instance)`. Như vậy một **public synchronized method** có thể bị caller bên ngoài chặn bằng việc giữ monitor của object.

Điều này **không phải luôn sai**: có API cố ý công bố giao thức đồng bộ qua object instance. Nhưng nếu việc lấy khóa chỉ là **implementation detail** bảo vệ dữ liệu private, nên dùng một object lock riêng và không trả nó cho caller.

Cũng đừng hiểu `final class` là monitor private: `final` ngăn kế thừa class, không ngăn mã ngoài giữ monitor của instance đã được công bố.

## 5. Cách thiết kế thường phù hợp: private final Object

Đối với trạng thái gắn với một instance:

~~~java
private final Object lock = new Object();
~~~

Đối với trạng thái `static` chung một class:

~~~java
private static final Object LOCK = new Object();
~~~

Ba yếu tố đều có ý nghĩa:
- `private`: code ngoài không thể lấy lock thông qua API thông thường.
- `final`: field không bị gán lại reference, giúp mọi chỗ khóa trên **cùng identity ổn định**.
- `new Object()`: tạo object chuyên dùng làm monitor, không bị vô tình chia sẻ do interning hay singleton chung.

### Ví dụ Java 17+: Counter an toàn với private lock

~~~java
// PrivateLockCounterDemo.java
public class PrivateLockCounterDemo {
    static final class Counter {
        private final Object lock = new Object();
        private int value;

        void increment() {
            synchronized (lock) {
                value++;
            }
        }

        int getValue() {
            synchronized (lock) {
                return value;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        Runnable add = () -> {
            for (int i = 0; i < 1000; i++) {
                counter.increment();
            }
        };

        Thread a = new Thread(add);
        Thread b = new Thread(add);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Count: " + counter.getValue());
    }
}
~~~

~~~bash
javac PrivateLockCounterDemo.java
java PrivateLockCounterDemo
~~~

Stdout:

~~~text
Count: 2000
~~~

Từng instance `Counter` có monitor riêng. Cả `increment()` lẫn `getValue()` dùng **cùng monitor** để bảo vệ `value`. Caller có reference `Counter` vẫn không thể giữ monitor private bằng API thông thường. Main `join()` hai worker rồi in kết quả chính xác 2000.

Nếu có một đường truy cập `value` không dùng lock hoặc dùng lock khác, thiết kế vẫn có thể sai. Private lock không tự xử lý mọi race condition.

### Vì sao không nên gán lại khóa?

Nếu `lock` không final và bị thay bằng object mới, thread A có thể giữ monitor cũ trong lúc thread B lấy monitor mới. Khi đó hai thread cùng lọt vào critical section tưởng như bảo vệ cùng dữ liệu. Giữ identity ổn định và không thay reference là điều kiện thiết kế cơ bản.

## 6. static synchronized và khóa Class object

Một `static synchronized` method dùng monitor của **class object** tương ứng (`MyClass.class`), khác với monitor của instance:

~~~java
// Minh họa trong class MyClass:
public static synchronized void refresh() {
    // Khóa MyClass.class
}
~~~

Tương đương về monitor với:

~~~java
public static void refresh() {
    synchronized (MyClass.class) {
        // ...
    }
}
~~~

Class object được mã khác truy cập bằng class literal; do đó code ngoài cũng có thể `synchronized(MyClass.class)` và chặn static synchronized method. Các static synchronized methods cùng class dùng chung class monitor, kể cả khi chúng bảo vệ trạng thái độc lập.

Nếu chỉ cần khóa bảo vệ static state nội bộ, `private static final Object LOCK = new Object()` giúp tránh công bố monitor. Instance synchronized methods thường khóa `this`; chúng **không tự đồng bộ** với static synchronized methods.

**Chọn lock theo phạm vi bất biến:**

| Phạm vi trạng thái | Lock gợi ý | Vì sao |
| --- | --- | --- |
| Instance fields không chia sẻ giữa object | `private final Object lock` | Mỗi object có monitor riêng |
| Static fields dùng chung trong class | `private static final Object LOCK` | Một monitor ổn định cho static state |
| Nhiều field cùng một bất biến | Một lock chung cho tất cả field liên quan | Không cho reader thấy nửa trạng thái |
| Hai nhóm trạng thái độc lập | Có thể dùng hai lock khác nhau | Tránh contention, nhưng kiểm soát thứ tự nếu có lồng lock |

Đừng tách lock khi bất biến cần cập nhật nhiều field nguyên tử; cũng đừng khóa toàn bộ ứng dụng bằng một lock static chỉ vì cách đó viết đơn giản.

## 7. Các loại đối tượng không nên dùng làm monitor tùy tiện

Nguyên tắc tổng quát là: **không sử dụng một object mà quyền sở hữu, identity hoặc vòng đời nằm ngoài phạm vi đồng bộ của mình**.

| Đối tượng khóa | Rủi ro chính |
| --- | --- |
| `"string literal"` | Interned String có thể được code khác dùng |
| `String.intern()` trên input động | Monitor bị chia sẻ theo nội dung với code khác, không có ownership rõ |
| `Boolean.TRUE` / `Boolean.FALSE` | Singleton được dùng rộng rãi |
| Boxed values như `Integer.valueOf(1)` | Có cache/identity chia sẻ; wrapper dạng value-based không phải lock đáng tin |
| `SomeClass.class` | Class object công khai |
| `this` khi instance được đưa ra ngoài | Caller có thể giữ monitor instance |
| `public final Object LOCK` | Người khác biết chính identity khóa |
| Object do thư viện bên ngoài cung cấp | Thư viện có thể có giao thức monitor khác |
| Một reference khóa mutable bị gán lại | Các thread có thể dùng những monitor khác nhau |

Với boxed primitives, Java/JDK còn có cảnh báo liên quan việc đồng bộ trên **value-based classes**. Không nên dùng một giá trị số/boolean làm monitor cho trạng thái riêng.

### Có nên khóa theo chuỗi key?

Ví dụ:

~~~java
// Mã minh họa nên tránh:
synchronized (userKey.intern()) {
    updateAccount(userKey);
}
~~~

Các key có nội dung giống nhau bị khóa chung, kể cả đến từ code không liên quan; interning chuỗi đầu vào còn gây khó khăn khi quản lý việc chia sẻ monitor và vòng đời dữ liệu. **Không dùng intern làm mặc định để tạo per-key lock.**

Nếu thật sự cần khóa theo key, hãy xây dựng registry object lock với **atomic creation, ownership, lifecycle/eviction** rõ ràng, hoặc dùng thao tác nguyên tử của `ConcurrentHashMap` (ví dụ `compute`/`computeIfAbsent` khi phù hợp với bất biến). Một `new Object()` mới trong mỗi lần gọi sẽ **không** giúp hai request cùng key đồng bộ nhau. Registry lock tự viết cũng cần tránh race khi thêm/xóa khóa và tránh giữ lock lâu cho I/O.

## 8. Những hệ quả về liveness và khả năng chẩn đoán

### Contention không mong đợi

Một literal hoặc public monitor bị chia sẻ khiến nhiều tác vụ không liên quan **xếp hàng** trên cùng monitor, tăng latency và giảm throughput. Một thư viện mới thêm `synchronized("CACHE_LOCK")` có thể vô tình làm chậm code cũ cùng dùng chuỗi ấy.

Trong thread dump, thread chờ lấy monitor intrinsic thường ở trạng thái `BLOCKED`. Nhưng **BLOCKED không tự chứng minh deadlock**: chờ một đoạn khóa bình thường là hợp lệ. Cần kiểm tra chủ monitor, stack trace, thời gian chờ và tiến triển của tác vụ.

### Deadlock do vòng chờ

Giả sử T1 giữ monitor A và chờ B, còn T2 giữ B và chờ A:

~~~text
T1: giữ A -> chờ B
T2: giữ B -> chờ A
=> Cả hai có thể chờ vô hạn.
~~~

Một monitor công khai giúp mã ngoài thêm những cạnh chờ mà thành phần bên trong không kiểm soát. Private lock giảm sự can thiệp bất ngờ, nhưng **không loại bỏ deadlock** nếu code của mình vẫn lấy nhiều khóa theo thứ tự không nhất quán. Nên xác định lock ordering, giữ đoạn tới hạn ngắn và tránh gọi callback/I/O kéo dài trong lúc giữ khóa khi có thể.

### wait/notify trên monitor bị lộ

`Object.wait()`, `notify()`, `notifyAll()` liên quan tới monitor của object. Nếu đối tượng condition monitor bị lộ, mã ngoài có thể gửi thông báo không đúng giao thức. Ngay cả với monitor private, `wait()` có thể **spurious wakeup**, nên việc chờ predicate phải đặt trong vòng `while` kiểm tra lại điều kiện.

### Giới hạn về bảo mật

Private lock là **đóng gói và kiểm soát đồng bộ**, không phải cơ chế bảo mật tuyệt đối với mọi mã chạy trong cùng JVM. Nếu mã không tin cậy có quyền reflection/bytecode instrumentation hay truy cập đặc quyền vào nội bộ, cần biện pháp cách ly thích hợp ở cấp ứng dụng/hệ thống. Bài toán chính ở đây là giảm phụ thuộc monitor ngầm giữa những thành phần thông thường.

## 9. ReentrantLock vẫn cần đóng gói tương tự

`ReentrantLock` không phải monitor intrinsic của `synchronized`, nhưng một reference `ReentrantLock` công khai cũng cho code ngoài khả năng gọi `lock()` và giữ khóa. Cùng nguyên tắc: nếu lock là implementation detail, dùng `private final ReentrantLock` và không trả reference qua API.

~~~java
// PrivateReentrantLockDemo.java
import java.util.concurrent.locks.ReentrantLock;

public class PrivateReentrantLockDemo {
    static final class Counter {
        private final ReentrantLock lock = new ReentrantLock();
        private int value;

        void increment() {
            lock.lock();
            try {
                value++;
            } finally {
                lock.unlock();
            }
        }

        int getValue() {
            lock.lock();
            try {
                return value;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        Thread a = new Thread(counter::increment);
        Thread b = new Thread(counter::increment);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Value: " + counter.getValue());
    }
}
~~~

~~~bash
javac PrivateReentrantLockDemo.java
java PrivateReentrantLockDemo
~~~

Stdout:

~~~text
Value: 2
~~~

Với `ReentrantLock` phải `unlock()` trong `finally` **sau khi đã lấy thành công**. Nếu dùng `tryLock()`, chỉ unlock khi nó trả `true`. `tryLock`/timeout có thể giúp xây dựng giao thức chờ thích hợp, nhưng không tự sửa sai phạm vi khóa hoặc mọi khả năng deadlock.

## 10. Review code và refactor an toàn

Một quy trình kiểm tra:

1. **Tìm expression khóa** trong mọi `synchronized(x)` và các method `synchronized`. Xác định object thật mà `x` trỏ tới.
2. **Kiểm tra identity:** có String literal, `intern`, boxed cache, class literal, singleton hoặc object public không? Hai tên khác nhau có thể tham chiếu cùng object không?
3. **Kiểm tra ownership:** lock có bị lộ qua field/getter/callback không? Caller hoặc thư viện khác có thể giữ lock không?
4. **Xác định bất biến:** lock bảo vệ những field nào? Mọi đường đọc/ghi có cùng giao thức không?
5. **Đảm bảo identity ổn định:** ưu tiên `private final`/`private static final`; không gán lại khóa.
6. **Đánh giá nested locking:** có thứ tự lấy khóa rõ ràng không? Có giữ khóa khi gọi mã ngoài, chờ network hoặc lock khác không?
7. **Kiểm tra API contract trước khi đổi monitor:** một public synchronized method hoặc object monitor được công bố **cố ý** có thể đã là phần hợp đồng người dùng đang dựa vào. Chuyển sang private lock có thể phá đồng bộ của caller.
8. **Kiểm chứng sau sửa:** dùng kiểm thử đồng thời, invariant assertions, thread dump và số liệu contention khi cần; kết quả stress test không thay thế phân tích ownership.

| Mẫu cần cân nhắc tránh | Lựa chọn thường rõ ràng hơn | Mục đích |
| --- | --- | --- |
| `synchronized("LOCK")` | `private final Object lock = new Object()` | Monitor không trùng với literal interned |
| `private static final String LOCK = "LOCK"` | `private static final Object LOCK = new Object()` | Field private **và** identity riêng |
| `public static final Object LOCK` | `private static final Object LOCK` | Không lộ monitor nội bộ |
| `public synchronized void update()` khi object dùng công khai | Khối `synchronized(privateLock)` trong phương thức thường | Không bị code ngoài khóa trực tiếp instance |
| `synchronized(MyClass.class)` | `synchronized(PRIVATE_STATIC_LOCK)` | Không dùng class monitor rộng rãi |
| `synchronized(key.intern())` | Registry lock được quản lý hoặc atomic operation theo key | Tránh va chạm ngoài ý muốn |

Đổi khóa từ `this` sang private lock không phải refactor máy móc: nếu consumer cũ dựa vào `synchronized(instance)` để điều phối, cần xem xét tương thích và cập nhật giao thức nhất quán.

## 11. Những sai lầm phổ biến

- **“Hai biến String khác tên là hai khóa khác nhau.”** Sai: nếu trỏ cùng interned literal thì cùng monitor.
- **“Private String literal là private monitor.”** Sai: private chỉ áp dụng cho field, còn object vẫn chia sẻ.
- **“Có final nên public Object LOCK an toàn.”** Sai: caller vẫn lấy được identity dù không gán lại được field.
- **“synchronized(this) luôn riêng tư.”** Sai: caller giữ instance cũng có thể giữ monitor ấy.
- **“static synchronized khóa một instance.”** Sai: nó khóa class object.
- **“Hai String bằng equals thì synchronized cùng khóa.”** Sai nếu identity khác; monitor dựa trên `==`.
- **“Private lock bảo đảm không deadlock.”** Sai: nested locks trong nội bộ vẫn có thể tạo vòng chờ.
- **“Mọi thread BLOCKED đều deadlock.”** Sai: có thể chỉ chờ monitor đang được giữ hợp lệ.
- **“Dùng new String để tạo lock là giải pháp tốt nhất.”** Không cần; object monitor chuyên dụng rõ ràng hơn.

## 12. Tự kiểm tra

**1.** Hai class độc lập cùng dùng `synchronized("CACHE")`. Vì sao chúng có thể chặn nhau?  
**Đáp án:** Vì cùng String literal được intern và hai đoạn synchronized giữ cùng monitor.

**2.** Vì sao `private static final String LOCK = "X"` vẫn có rủi ro?  
**Đáp án:** Bất kỳ đoạn code nào dùng literal `"X"` tương ứng đều có thể lấy cùng interned object.

**3.** `public final Object lock = new Object()` có bảo đảm chỉ lớp sở hữu giữ monitor không?  
**Đáp án:** Không. `final` chỉ cố định reference; field public tiết lộ khóa.

**4.** `public synchronized void increment()` dùng monitor nào?  
**Đáp án:** Monitor của `this`; caller có instance có thể `synchronized(instance)`.

**5.** `static synchronized` và `synchronized(this)` có tự cùng monitor không?  
**Đáp án:** Không. Cái đầu dùng class object, cái sau dùng instance object.

**6.** Nếu object khóa bị gán lại khi chương trình đang chạy thì vấn đề gì?  
**Đáp án:** Những thread có thể giữ các monitor khác nhau, phá mutual exclusion dù chúng tưởng cùng bảo vệ một field.

**7.** Khi nào `private final Object` chưa đủ để an toàn?  
**Đáp án:** Khi code có đường truy cập không dùng lock, lock ordering sai, bất biến trải nhiều lock, hoặc tài nguyên không cùng phạm vi sở hữu.

**8.** Với `ReentrantLock`, khi nào unlock?  
**Đáp án:** Trong `finally` sau khi đã lấy khóa thành công; `tryLock` thất bại thì không được unlock.

**Ghi nhớ:** Monitor của `synchronized` được nhận diện theo **object identity**. Chọn khóa có identity riêng, ổn định và không bị lộ khi nó chỉ bảo vệ implementation detail. Tránh literal/interned String và public object; đồng thời phải giữ giao thức đồng bộ nhất quán trên mọi đường đọc/ghi và tôn trọng hợp đồng API hiện có.

### Tài liệu tham khảo chuẩn

- Java Language Specification 17, `17.1 (Locks), `17.4 (Memory Model): https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
- Java Language Specification 17, `3.10.5 String Literals: https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.5
- JDK 17 `String.intern()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#intern()
- JDK 17 `ReentrantLock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-009">Đợi monitor bằng wait() và đánh thức bằng notify()/notifyAll()</a>

<details>
<summary>Xem chi tiết</summary>

# Đợi monitor bằng wait() và đánh thức bằng notify()/notifyAll()

Trong Java, mọi đối tượng đều có một **intrinsic monitor** (khóa nội tại). Từ khóa `synchronized` cho phép một thread giữ monitor để bảo vệ trạng thái chia sẻ. Nhưng đôi khi giữ khóa rồi liên tục hỏi “điều kiện đã đúng chưa?” sẽ tốn CPU hoặc ngăn thread khác cập nhật dữ liệu. Java cung cấp ba phương thức của `java.lang.Object` để **phối hợp theo điều kiện** trên một monitor: `wait()`, `notify()` và `notifyAll()`.

Hãy hình dung một hộp thư chỉ chứa một thông điệp: producer phải chờ khi hộp đầy, consumer phải chờ khi hộp rỗng. `wait()` cho thread **nhả monitor và chờ**, để thread khác vào vùng `synchronized` thay đổi điều kiện. Sau khi điều kiện thay đổi, thread cập nhật có thể gọi `notify()` hoặc `notifyAll()` để đánh thức waiter.

Điều quan trọng nhất: **đánh thức không đồng nghĩa điều kiện đã đúng**; waiter phải lấy lại monitor rồi **kiểm tra lại điều kiện**.

## 1. Ba phương thức hoạt động trên monitor của đối tượng

Các phương thức này nằm trong `Object` (không riêng `Thread`), và thao tác trên **wait set** gắn với đối tượng nhận lời gọi:

| Phương thức | Ý nghĩa | Lưu ý chính |
|---|---|---|
| `lock.wait()` | Thread hiện tại nhả monitor **của `lock`** rồi vào trạng thái chờ một tín hiệu/gián đoạn. | Phải đang giữ monitor của `lock`; có thể thức dậy giả (*spurious wakeup*). |
| `lock.wait(millis)` | Tương tự nhưng có giới hạn chờ; có thể trả về khi hết thời gian. | Với overload `wait(long)`, `millis == 0` nghĩa là chờ không thời hạn. |
| `lock.notify()` | Chọn **một** thread đang chờ trong wait set của `lock` (nếu có) để đánh thức. | Không chọn theo FIFO, không bảo đảm chọn đúng điều kiện cần xử lý. |
| `lock.notifyAll()` | Đánh thức **mọi** thread đang chờ trong wait set của `lock` tại thời điểm đó. | Chúng vẫn phải tranh monitor và tự kiểm tra lại điều kiện. |

`notify()`/ `notifyAll()` **không lưu tín hiệu** cho thread bắt đầu đợi sau đó. Nếu lúc gọi không có waiter, lời notify không có người nhận. Một thiết kế đúng lưu **trạng thái điều kiện** trong biến được bảo vệ, sau đó đợi bằng vòng `while` dưới cùng monitor.

### Điều kiện sở hữu monitor

Thread phải sở hữu monitor của **đúng đối tượng** gọi `wait()`/`notify()`/`notifyAll()`. Nếu không, Java ném `IllegalMonitorStateException`. Ví dụ đúng:

```java
Object lock = new Object();

synchronized (lock) {
    lock.wait(); // Thread đang giữ monitor của lock.
}
```

Đây là **trích đoạn**, phải nằm trong phương thức khai báo hoặc xử lý `InterruptedException`. Không chạy độc lập như trên vì chờ không có thread nào đánh thức sẽ gây treo.

Trích đoạn dưới đây sai vì khóa một monitor nhưng gọi `wait()` trên đối tượng khác:

```java
Object first = new Object();
Object second = new Object();

synchronized (first) {
    second.wait(); // IllegalMonitorStateException.
}
```

Kể cả khi có `synchronized`, **đúng monitor** mới quan trọng. `synchronized(this)` không trao quyền gọi `someLock.wait()` nếu thread chưa giữ monitor của `someLock`.

## 2. wait() nhả khóa nào, giữ khóa nào?

Khi thread đang giữ monitor của đối tượng `lock` và gọi `lock.wait()`:

1. JVM đưa thread vào wait set của `lock` và nhả monitor của **chính `lock`** như một thao tác phối hợp.
2. Thread tạm dừng thực thi tại `wait()`, để thread khác có thể vào `synchronized(lock)` cập nhật trạng thái chung.
3. Khi được thông báo, bị interrupt, hết timeout hoặc tự thức dậy giả, nó phải **lấy lại monitor `lock`** trước khi `wait()` có thể hoàn tất/nhảy sang xử lý exception.
4. Sau khi lấy lại monitor, thread tiếp tục ở vùng `synchronized` và kiểm tra điều kiện.

Nếu thread giữ đồng thời monitor khác, `lock.wait()` **không tự nhả những monitor khác đó**. Điều này có thể dẫn đến deadlock nếu thread cập nhật cần monitor mà waiter vẫn giữ.

Sơ đồ dòng chảy:

```text
Thread consumer                         Thread producer
---------------                         ---------------
synchronized(lock)
  while (empty) {
    lock.wait()
       | nhả monitor lock
       v                               synchronized(lock)
    WAITING                                add item
       ^                                  lock.notifyAll()
       | được đánh thức                    | vẫn giữ lock đến khi thoát block
       +-----------------------------------+
    chờ lấy lại monitor
    kiểm tra lại empty
    lấy item
  }
thoát synchronized(lock)
```

Không có bảo đảm producer nhả khóa ngay lúc `notifyAll()`. Nó chỉ nhả khi thoát vùng `synchronized(lock)` hoặc bằng một cơ chế nhả monitor hợp lệ khác. Vì vậy câu “notify gọi xong là waiter chạy ngay” là **sai**.

## 3. Quy tắc bắt buộc: condition predicate + while + cùng monitor

Mẫu an toàn cơ bản:

```java
synchronized (lock) {
    while (!condition) {
        lock.wait();
    }
    // Thực hiện thao tác chỉ khi condition hiện tại là true.
}
```

Đây là trích đoạn minh họa: `lock` và `condition` được giả định đã khai báo, `InterruptedException` được xử lý ở phương thức chứa nó. Để hoàn chỉnh giao thức, thread thay đổi `condition` cũng phải dùng **cùng monitor**:

```java
synchronized (lock) {
    condition = true;
    lock.notifyAll();
}
```

**Vì sao không dùng `if`?**

- **Spurious wakeup:** Java cho phép `wait()` trở về dù không có notify theo cách mà ứng dụng trông đợi.
- **Nhiều waiter tranh cùng tài nguyên:** một thread vừa được đánh thức có thể lấy monitor **sau** thread khác; dữ liệu đã bị thread khác tiêu thụ.
- **Sai điều kiện sau notifyAll:** nhiều thread được đánh thức nhưng chỉ một thread có điều kiện phù hợp.
- **Timeout hoặc interruption:** cần quyết định theo trạng thái và mục tiêu chờ; việc chờ kết thúc không tự chứng minh điều kiện hợp lệ.

Dùng `while` đảm bảo mọi lần thức dậy đều kiểm tra lại điều kiện dưới khóa. Cần đặt kiểm tra điều kiện và `wait()` **trong cùng một vùng `synchronized`**: nếu tách khóa, có thể xảy ra tình huống thread khác thay đổi điều kiện và notify ở khe giữa *check* và *wait*; sau đó waiter ngủ vô hạn.

Điều kiện phải là trạng thái có ý nghĩa (ví dụ `hasItem`, `closed`, `available > 0`), **không** phải “có một lời notify nào đó”. Notification là lời nhắc để kiểm tra lại trạng thái; chính predicate mới quyết định khi nào được tiếp tục.

## 4. Ví dụ 1: một waiter và notify()

Ví dụ đầu cho thấy mẫu guard bằng `while`. `CountDownLatch` chỉ giúp main biết worker đã khởi động, không dùng để truyền điều kiện `opened`.

Tạo tệp **`MonitorSignal.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class MonitorSignal {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        boolean[] opened = {false};
        CountDownLatch started = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            synchronized (lock) {
                started.countDown();
                while (!opened[0]) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            System.out.println("Worker passed the gate");
        }, "worker");

        worker.start();
        started.await();

        synchronized (lock) {
            opened[0] = true;
            lock.notify();
        }

        worker.join();
        System.out.println("Main finished");
    }
}
```

```bash
javac MonitorSignal.java
java MonitorSignal
```

Kết quả:

```text
Worker passed the gate
Main finished
```

Có hai lịch chạy hợp lệ:

- Worker đã vào `wait()` trước khi main khóa monitor để mở cổng. Main cập nhật `opened[0] = true` rồi notify; worker thức dậy, lấy lại monitor, thấy điều kiện đúng và đi tiếp.
- Main lấy monitor ngay sau worker gọi `started.countDown()` nhưng **trước khi worker gọi `wait()`**. Main phải chờ worker nhả monitor mới vào được synchronized; worker thấy `opened` vẫn false và chuyển vào wait (nhả monitor), rồi main mở cổng/notify. Nếu main mở cổng trước khi worker lần đầu vào synchronized trong một biến thể không có latch, worker sẽ thấy predicate true và bỏ qua wait.

Cả hai tình huống đều an toàn do việc **kiểm tra điều kiện và đợi dùng cùng monitor**. Không có “thông báo bị thất lạc” khiến worker đợi mãi trong giao thức này. Dữ liệu `boolean[]` không đánh dấu `volatile` vì mọi lần đọc/ghi `opened[0]` đều nằm dưới `lock`; unlock/lock cùng monitor cung cấp quan hệ bộ nhớ cần thiết.

Khi chỉ có **một waiter** cần được đánh thức và một điều kiện đơn giản, `notify()` đủ. Với nhiều waiter và nhiều loại điều kiện, `notifyAll()` thường dễ lập luận đúng hơn.

## 5. notify() và notifyAll(): chọn một hay đánh thức tất cả?

`notify()` chọn một waiter bất kỳ trong wait set của monitor. Nó không bảo đảm FIFO, không ưu tiên thread chờ lâu hơn và không biết “thread nào đang đợi điều kiện phù hợp”. Nếu monitor dùng để đợi **nhiều loại điều kiện** (chẳng hạn *not-empty* cho consumer và *not-full* cho producer), `notify()` có thể đánh thức một waiter mà predicate của nó vẫn sai. Waiter ấy quay lại `wait()`, trong khi waiter khác cần tiến triển vẫn ngủ. Một lịch xen kẽ như vậy có thể dẫn đến **mất tiến triển**, dù mọi phép truy cập dữ liệu đều synchronized.

`notifyAll()` đánh thức tất cả waiter trong cùng wait set, khiến mỗi thread lần lượt tranh khóa rồi kiểm tra predicate riêng. Nó thường là lựa chọn **an toàn về tính đúng đắn** cho giao thức dùng một monitor với nhiều điều kiện và khi thay đổi trạng thái có thể cho nhiều waiter tiếp tục.

| Tình huống | Cân nhắc |
|---|---|
| Chính xác một waiter, một điều kiện | `notify()` có thể đủ. |
| Nhiều waiter, thay đổi trạng thái cho phép tất cả tiếp tục | `notifyAll()` cần thiết để không bỏ sót waiter còn hợp lệ. |
| Một monitor dùng nhiều predicate khác nhau | `notifyAll()` thường an toàn hơn `notify()`, kết hợp vòng `while` ở mỗi waiter. |
| Rất nhiều waiter, chỉ một người có thể tiến triển | `notifyAll()` có thể gây nhiều lần tranh monitor (*thundering herd*); vẫn đúng nhưng có chi phí. |
| Muốn đánh thức chính xác một nhóm điều kiện | Cân nhắc `ReentrantLock` với nhiều `Condition` riêng hoặc abstraction cấp cao. |

**Lưu ý:** ngay cả với `notifyAll()`, không phải mọi waiter đều được tiếp tục hành động. Mỗi thread phải kiểm tra lại predicate; thread nào vẫn không đủ điều kiện sẽ chờ tiếp. `notifyAll()` cũng **không bảo đảm fairness** hoặc thứ tự lấy khóa.

## 6. Ví dụ 2: notifyAll() mở cổng cho nhiều waiter

Bài toán: nhiều thread cùng chờ cổng mở. Khi cổng mở vĩnh viễn, tất cả đều được phép tiếp tục. Nếu chỉ `notify()` một lần, ít nhất một waiter có thể không được đánh thức (nếu chúng đang thực sự đợi); `notifyAll()` phù hợp hơn.

Tạo tệp **`MonitorBroadcast.java`**:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class MonitorBroadcast {
    static final class Gate {
        private boolean open;

        synchronized void awaitOpen() throws InterruptedException {
            while (!open) {
                wait();
            }
        }

        synchronized void open() {
            open = true;
            notifyAll();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Gate gate = new Gate();
        AtomicInteger passed = new AtomicInteger();

        Runnable waiter = () -> {
            try {
                gate.awaitOpen();
                passed.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(waiter, "A");
        Thread b = new Thread(waiter, "B");
        Thread c = new Thread(waiter, "C");
        a.start();
        b.start();
        c.start();

        gate.open();

        a.join();
        b.join();
        c.join();
        System.out.println("Workers passed: " + passed.get());
    }
}
```

```bash
javac MonitorBroadcast.java
java MonitorBroadcast
```

Kết quả khi không có external interrupt:

```text
Workers passed: 3
```

`Gate.open()` đặt trạng thái `open = true` và gọi `notifyAll()` trong cùng synchronized method. Nếu một waiter đã chờ, nó được đánh thức và sẽ kiểm tra `open` sau khi lấy lại khóa. Nếu một waiter chưa kịp đi vào `awaitOpen()`, nó sẽ thấy `open == true` và **không chờ**. Vì vậy kết quả không phụ thuộc vào việc có waiter nào thực sự ngủ lúc `notifyAll()` được gọi. `join()` bảo đảm main đợi đủ ba thread trước khi in.

## 7. Ví dụ 3: producer–consumer với hộp thư một phần tử

Ví dụ hoàn chỉnh sử dụng **cùng một monitor** để bảo vệ hộp một slot, hai predicate và cờ đóng. Producer đợi `notFull`, consumer đợi `notEmpty`. `notifyAll()` phù hợp vì các waiter có thể chờ **các điều kiện khác nhau** trên cùng monitor.

Tạo tệp **`MonitorMailbox.java`**:

```java
public class MonitorMailbox {
    static final class Mailbox {
        private Integer value;
        private boolean closed;

        public synchronized void put(int item) throws InterruptedException {
            while (value != null && !closed) {
                wait();
            }
            if (closed) {
                throw new IllegalStateException("mailbox is closed");
            }
            value = item;
            notifyAll();
        }

        // null nghĩa là mailbox đã đóng và không còn dữ liệu.
        public synchronized Integer take() throws InterruptedException {
            while (value == null && !closed) {
                wait();
            }
            if (value == null) {
                return null;
            }
            int item = value;
            value = null;
            notifyAll();
            return item;
        }

        public synchronized void close() {
            closed = true;
            notifyAll();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Mailbox mailbox = new Mailbox();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 3; i++) {
                    mailbox.put(i);
                }
                mailbox.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                mailbox.close();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                Integer item;
                while ((item = mailbox.take()) != null) {
                    System.out.println("Received: " + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();
        System.out.println("Finished");
    }
}
```

```bash
javac MonitorMailbox.java
java MonitorMailbox
```

Kết quả:

```text
Received: 1
Received: 2
Received: 3
Finished
```

**Đọc kỹ giao thức:**

1. Toàn bộ state `value` và `closed` là private và chỉ được truy cập trong synchronized method của **cùng đối tượng Mailbox**.
2. Producer gọi `put()`: nếu slot còn giá trị, nó vào `wait()` và nhả monitor để consumer có thể gọi `take()`; khi slot trống, nó ghi một item và `notifyAll()`.
3. Consumer gọi `take()`: nếu slot rỗng và chưa đóng, nó nhả monitor bằng `wait()`; khi có item, nó lấy item, làm trống slot và `notifyAll()` cho producer.
4. Producer gửi lần lượt 1, 2, 3; vì chỉ có một producer và một consumer, consumer nhận đúng thứ tự đó. Sau khi gửi xong, producer gọi `close()`.
5. `close()` đặt `closed = true` rồi `notifyAll()` để mọi waiter kiểm tra lại; consumer vẫn lấy item còn trong slot trước khi nhận `null` báo kết thúc.
6. Main `join()` cả hai thread trước khi in `Finished`, do đó output không phụ thuộc lịch lập lịch cụ thể trong trường hợp chương trình chạy bình thường, không bị external interrupt.

Nếu cần xử lý nhiều producer/consumer, chính giao thức `while` và `notifyAll()` vẫn có ý nghĩa; tuy vậy nên xác định rõ hợp đồng `close()`, dữ liệu còn lại, việc hủy tác vụ, và tránh dùng `null` làm dữ liệu thật. Đây là một ví dụ học về monitor, không phải implementation đầy đủ cho một queue sản xuất.

Trong ứng dụng Java thường ngày, `ArrayBlockingQueue` hoặc `LinkedBlockingQueue` cung cấp cơ chế chờ/đánh thức và chính sách dung lượng đã được thư viện thiết kế, kiểm thử. Thường nên ưu tiên abstraction như `BlockingQueue` thay vì tự viết monitor khi mục tiêu chỉ là truyền dữ liệu giữa producer và consumer.

## 8. InterruptedException: đợi có thể bị ngắt

`Object.wait()` ném `InterruptedException` nếu thread bị interrupt khi đang chờ (hoặc có interrupt trước khi bắt đầu chờ theo quy tắc API). Khi exception được ném, **trạng thái interrupt của thread bị xóa**; nếu phương thức không ném tiếp mà muốn tầng gọi còn biết yêu cầu hủy, thường cần khôi phục bằng `Thread.currentThread().interrupt()`.

Một điểm quan trọng: `wait()` phải **lấy lại monitor của đối tượng** trước khi hoàn tất do thức dậy hay do interrupt. Vùng `synchronized` vẫn có tính loại trừ hỗ trợ dọn state sau lời gọi.

Ví dụ xử lý interrupt đúng khi không cần đợi nữa:

Tạo tệp **`MonitorInterrupt.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class MonitorInterrupt {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        CountDownLatch ready = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            synchronized (lock) {
                ready.countDown();
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Wait interrupted");
                }
            }
        }, "waiter");

        worker.start();
        ready.await();
        worker.interrupt();
        worker.join();

        System.out.println("Interrupt restored: "
                + worker.isInterrupted());
    }
}
```

```bash
javac MonitorInterrupt.java
java MonitorInterrupt
```

Kết quả:

```text
Wait interrupted
Interrupt restored: true
```

`ready.countDown()` báo worker đã vào vùng khóa, nhưng tại thời điểm main gọi `interrupt()` worker **có thể vừa vào `wait()` hoặc chưa kịp gọi `wait()`**. Cả hai cách xen kẽ đều dẫn tới nhánh `InterruptedException` của lời gọi `wait()` trong ví dụ. Worker khôi phục interrupt, thoát khỏi `synchronized`; main đợi `join()` xong rồi quan sát cờ interrupt còn đặt. Việc gọi `worker.interrupt()` **không đồng nghĩa** cưỡng bức dừng thread hoặc làm `notifyAll()` trên monitor: đó là tín hiệu hủy hợp tác tới thread đích.

Không “nuốt” exception rồi tiếp tục như thể điều kiện đã đúng. Có thể ném tiếp `InterruptedException`, khôi phục cờ và kết thúc công việc, hoặc thực hiện cleanup rồi chuyển trách nhiệm hủy cho tầng gọi theo hợp đồng.

## 9. Timed wait và cách sử dụng deadline

`wait(long millis)` và `wait(long millis, int nanos)` cho phép một giới hạn thời gian. Kết quả trở về **không khẳng định điều kiện đã đúng**: có thể do notify, timeout hoặc thức dậy giả. Vì vậy guard `while` vẫn cần thiết.

Nếu chỉ chờ đến một deadline cụ thể, tính **thời gian còn lại** mỗi vòng bằng nguồn thời gian đo khoảng như `System.nanoTime()`; không gọi lại `wait(timeout)` với toàn bộ thời gian gốc sau mỗi lần thức dậy, vì tổng thời gian có thể vượt dự định. Ví dụ thuật toán (trích đoạn, không phải class chạy độc lập):

```java
long timeoutNanos = 1_000_000_000L; // 1 giây.
long deadline = System.nanoTime() + timeoutNanos;

synchronized (lock) {
    while (!condition) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            break; // Hết thời hạn; kiểm tra/đánh dấu thất bại.
        }
        long ms = remaining / 1_000_000L;
        int ns = (int) (remaining % 1_000_000L);
        lock.wait(ms, ns);
    }
}
```

Ở đây `lock`, `condition` được giả định đã khai báo, và phương thức chứa mã phải xử lý `InterruptedException`. Trong các hệ thống cần chống tràn số với timeout rất lớn, cần thêm chính sách giới hạn/kiểm soát overflow; ví dụ dùng timeout ngắn để minh họa. `wait(0)` theo overload chỉ millisecond là **chờ vô hạn**, nên khi còn thời gian nhỏ hơn 1 ms cần dùng overload có nanos như trên.

Tránh dùng `Thread.sleep()` như sự thay thế cho `wait()`: `sleep()` **không nhả monitor đang giữ**, do đó thread khác cần chính monitor đó có thể không vào được để sửa điều kiện. `wait()` được thiết kế để nhả monitor của object mà nó chờ.

## 10. wait() khác sleep(), join(), Condition và BlockingQueue

| Công cụ | Nhả monitor nào khi chờ? | Mục đích |
|---|---|---|
| `Object.wait()` | Nhả **monitor của đối tượng nhận `wait()`**, không nhả các monitor khác. | Đợi predicate của trạng thái được bảo vệ bởi monitor. |
| `Thread.sleep(...)` | Không nhả intrinsic monitor nào do thread đang giữ. | Tạm dừng tối thiểu một khoảng thời gian; không phải condition wait. |
| `Thread.join()` | Nội bộ có cơ chế chờ thread hoàn tất; không nhả các monitor không liên quan do caller giữ. | Đợi một thread kết thúc, không dùng để đánh thức theo điều kiện nghiệp vụ. |
| `ReentrantLock.newCondition()` | `Condition.await()` nhả/reacquire **associated lock** theo hợp đồng của `Condition`. | Đợi có cấu trúc với nhiều condition queues, `signal()`/`signalAll()`. |
| `CountDownLatch.await()` | Không tự nhả một intrinsic monitor bất kỳ mà caller đang giữ. | Đợi đếm tín hiệu giảm tới 0; thường là one-shot. |
| `BlockingQueue.take()/put()` | Cơ chế khóa/chờ nội bộ do collection quản lý. | Trao đổi dữ liệu producer–consumer thuận tiện và đã có contract. |

Với `ReentrantLock`, luôn giữ lock phù hợp lúc gọi `Condition.await()`/`signal()` theo contract và mở khóa trong `finally`. `Condition` cũng yêu cầu kiểm tra predicate trong vòng `while` vì thức dậy không tự làm điều kiện đúng.

Một mô hình khác thường phù hợp hơn nếu nhu cầu chỉ là chờ một sự kiện hoặc kết quả:

- `CountDownLatch` cho tín hiệu hoàn thành một lần;
- `Semaphore` cho số permit;
- `CompletableFuture`/`Future` cho kết quả tác vụ;
- `BlockingQueue` cho truyền dữ liệu;
- `Condition` với lock nếu phải điều phối nhiều predicate trong một cấu trúc dữ liệu tự viết.

## 11. Happens-before và tính hiển thị dữ liệu

Java Memory Model quy định việc **unlock một monitor** synchronizes-with lần **lock sau đó trên cùng monitor**. Khi producer đổi `value` dưới `synchronized(lock)`, sau đó waiter lấy lại monitor và đọc `value` dưới cùng khóa, các hiệu ứng được nhìn thấy theo quan hệ happens-before thích hợp.

Tương ứng, `wait()` nhả monitor để thread khác có thể thay đổi shared state, và reacquire monitor trước khi waiter kiểm tra lại. Một `notify()` đơn lẻ **không phải cơ chế thay thế** cho việc bảo vệ predicate; quan hệ bộ nhớ của vùng monitor và việc kiểm tra trạng thái dưới monitor mới là cách lập luận đầy đủ.

Không tự thêm `volatile` cho mọi field chỉ vì thấy nhiều thread: nếu **mọi đường đọc/ghi** field đều nằm trong cùng giao thức synchronized thì monitor đã cung cấp cả visibility lẫn mutual exclusion cần thiết. Nhưng nếu một nơi đọc field ở ngoài vùng synchronized và không có quy tắc đồng bộ bổ sung, class có thể không còn thread-safe.

Trong một chương trình nhiều thread, `start()` giúp công bố các hành động trước khi khởi động worker, và `join()` cho phép thread chờ kết quả sau khi worker kết thúc. Hai API đó có vai trò khác với monitor condition wait: `wait()` cho phép **phối hợp khi cả hai thread vẫn đang sống**.

## 12. Những lỗi phổ biến và hậu quả

| Sai lầm | Hệ quả | Cách sửa |
|---|---|---|
| Gọi `wait()`/ `notify()` ngoài monitor của object nhận lời gọi | `IllegalMonitorStateException`. | Khóa đúng đối tượng bằng `synchronized(lock)` hoặc synchronized method trên `this`. |
| Dùng `if (!ready) wait()` | Có thể tiếp tục khi predicate vẫn sai. | Dùng `while (!ready) wait()` và kiểm tra lại dưới khóa. |
| Đổi điều kiện ngoài khóa rồi `notify()` trong khóa | Có thể có race/visibility bug. | Bảo vệ predicate và notification bằng cùng giao thức monitor. |
| Giữ khóa rồi `Thread.sleep()` chờ điều kiện | Thread cập nhật có thể không lấy được khóa. | `wait()` nhả monitor; hoặc dùng abstraction concurrent thích hợp. |
| Nghĩ `notify()` chuyển khóa ngay cho waiter | Waiter vẫn phải tranh và lấy lại monitor. | Thoát synchronized sớm sau khi cập nhật trạng thái khi hợp lý. |
| Dùng `notify()` với nhiều loại waiter | Có thể đánh thức sai waiter và khiến tiến triển bị kẹt. | Cân nhắc `notifyAll()` hoặc `Condition` riêng cho từng predicate. |
| Coi một lời notify là trạng thái “sự kiện đã xảy ra” | Notify trước khi có waiter không được lưu. | Lưu predicate trong state được bảo vệ; kiểm tra trước khi wait. |
| Đợi vô hạn mà không có đường đóng/hủy | Thread có thể chờ mãi. | Thiết kế tín hiệu đóng, notifyAll, interruption và timeout đúng hợp đồng. |
| Nuốt `InterruptedException` rồi tiếp tục công việc như bình thường | Hủy bỏ không được tôn trọng; có thể sai nghiệp vụ. | Ném tiếp hoặc khôi phục cờ interrupt rồi kết thúc hợp tác. |
| Gọi callback hoặc I/O kéo dài trong synchronized | Monitor bị giữ lâu, tăng contention và nguy cơ deadlock. | Giữ critical section ngắn; trao dữ liệu qua queue/abstraction cấp cao. |

**Không dùng `wait()` làm “hàng đợi thông điệp” trực tiếp.** Wait set không lưu payload, không lưu số lượt notify, không có cam kết FIFO. Hãy quản lý trạng thái rõ ràng hoặc dùng các cấu trúc đồng thời có contract phù hợp.

## 13. Cách thiết kế một giao thức monitor đúng

Trước khi viết code, hãy trả lời:

1. **Monitor nào sở hữu trạng thái?** Chọn một đối tượng lock ổn định (thường `private final Object lock` hoặc monitor instance `this` được đóng gói tốt).
2. **Predicate là gì?** Ví dụ `!queue.isEmpty()`, `queue.size() < capacity`, `closed`. Đặt tất cả đọc/ghi predicate trong giao thức đồng bộ phù hợp.
3. **Làm gì khi chưa đủ điều kiện?** Trong synchronized, `while (!predicate) lock.wait()`, với interruption/timeout được xử lý rõ.
4. **Ai đổi predicate và báo tin?** Thread thay đổi state rồi gọi `notifyAll()` (hoặc `notify()` nếu chứng minh được đủ) khi giữ đúng monitor.
5. **Có nhiều loại waiter không?** Nếu có, `notifyAll()` thường dễ bảo đảm tiến triển hơn; hoặc dùng nhiều `Condition`.
6. **Có trạng thái đóng/hủy không?** Phải đánh thức những waiter không thể tiếp tục để họ thoát, tránh giữ tiến trình sống vô hạn.
7. **Có kết quả hay dữ liệu cần truyền không?** Lưu trong state được bảo vệ hoặc dùng `BlockingQueue`/`Future`, không coi một lần notify là payload.
8. **Có nhiều monitor lồng nhau không?** `wait()` chỉ nhả monitor được gọi; tránh giữ các khóa khác khiến thread thông báo không thể tiến triển.

Đối với code học tập nhỏ, monitor rất hữu ích để hiểu JMM và condition synchronization. Với ứng dụng xử lý hàng đợi, pool, tín hiệu một lần hoặc tác vụ bất đồng bộ, các API trong `java.util.concurrent` thường mô tả nhu cầu chính xác hơn và giảm khả năng viết sai giao thức chờ/đánh thức.

## 14. Câu hỏi tự kiểm tra

1. **Vì sao `wait()` phải nằm trong `synchronized` của đúng đối tượng?** Vì nó thao tác trên wait set/monitor của đối tượng đó; không giữ monitor sẽ ném `IllegalMonitorStateException`.
2. **Khi gọi `lock.wait()`, thread có nhả mọi khóa đang giữ không?** Không. Nó nhả monitor của `lock`, không nhả các monitor khác đang giữ.
3. **`notify()` có bảo đảm thread được đánh thức chạy ngay không?** Không. Thread phải chờ lấy lại monitor; notifier vẫn giữ khóa cho tới khi nhả.
4. **Vì sao dùng `while` thay `if`?** Vì spurious wakeup, tranh chấp tài nguyên và thay đổi trạng thái có thể khiến predicate sai sau thức dậy.
5. **`notify()` khi chưa có waiter có được lưu để dùng về sau không?** Không. Phải lưu trạng thái/predicate, không dựa vào notification.
6. **Khi nào `notifyAll()` thường tốt hơn `notify()`?** Khi có nhiều waiter, nhất là nhiều predicate, hoặc một thay đổi cho phép nhiều thread cùng tiến triển.
7. **`Thread.sleep()` có nhả monitor hiện đang giữ không?** Không.
8. **`wait(timeout)` trả về có chứng minh điều kiện đúng không?** Không; phải kiểm tra lại predicate dưới khóa.
9. **`InterruptedException` từ `wait()` ảnh hưởng cờ interrupt thế nào?** Cờ bị xóa khi exception được ném; nếu không ném tiếp có thể cần khôi phục cờ.
10. **Vì sao các field không cần `volatile` trong `Mailbox`?** Vì mọi đường đọc/ghi state đều dùng cùng monitor, tạo mutual exclusion và quan hệ happens-before thích hợp.

**Ghi nhớ:** `wait()` = *nhả monitor và đợi predicate*; `notify()` = *đánh thức một waiter bất kỳ*; `notifyAll()` = *đánh thức toàn bộ waiter hiện tại*. Mọi waiter phải **lấy lại monitor, kiểm tra lại điều kiện trong `while`, và chỉ hành động khi predicate thực sự đúng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-010">Giữ invariant khi chờ và sau khi thức dậy</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ invariant khi chờ và sau khi thức dậy trong Java Synchronization

## Mục tiêu và phạm vi

Khi nhiều thread phối hợp qua một trạng thái chia sẻ, một thread có thể phải **chờ điều kiện** trước khi tiếp tục. Ví dụ consumer đợi buffer có dữ liệu, producer đợi còn chỗ trống, hoặc worker đợi một cờ `ready`. Gọi `wait()` hay `Condition.await()` chỉ là một phần nhỏ của bài toán: trước khi nhả khóa, trạng thái phải **nhất quán**; sau khi được đánh thức và lấy lại khóa, thread phải **kiểm tra lại điều kiện**, vì một tín hiệu không bảo đảm điều kiện vẫn đúng.

Bài này giúp bạn:
- Phân biệt **invariant** của đối tượng với **condition predicate** của thao tác đang chờ.
- Hiểu giao thức **giữ khóa → kiểm tra predicate → chờ và nhả khóa → lấy lại khóa → kiểm tra lại → hành động**.
- Biết vì sao phải dùng `while` thay vì `if` quanh `wait()/await()`, kể cả khi có `notifyAll()/signalAll()`.
- Tránh lỗi lost notification, trạng thái trung gian sai invariant, đánh thức nhầm nhóm waiter, timeout được tính lại sai và bỏ qua `InterruptedException`.
- Sử dụng monitor `synchronized` và `ReentrantLock` + `Condition`, nhận biết lúc nào nên dùng `BlockingQueue` hoặc `CountDownLatch`.
- Tự chạy các ví dụ **Java 17+** với kết quả, lập luận invariant và giới hạn của demo được nêu rõ.

**Phạm vi:** các primitive đồng bộ của Java và Java Memory Model nói chung. Không sử dụng annotation, executor hoặc tính năng riêng của Spring vì đã có chương riêng về Spring.

## 1. Invariant và predicate không phải cùng một điều

**Invariant (bất biến)** mô tả quan hệ phải đúng khi một đối tượng chia sẻ ở trạng thái có thể được các thread khác quan sát theo giao thức của đối tượng.

Với bounded buffer có sức chứa `capacity` và `size` là số phần tử:

~~~text
0 <= size <= capacity
size == số phần tử đang thực có trong buffer
các phần tử được lấy theo đúng thứ tự FIFO nếu buffer cam kết FIFO
~~~

Đối với producer và consumer, **predicate** để một thao tác được phép tiến lên lại khác:

~~~text
Producer đưa một phần tử: notFull  = (size < capacity)
Consumer lấy một phần tử: notEmpty = (size > 0)
~~~

Một buffer rỗng **vẫn thỏa invariant**, nhưng predicate của consumer đang sai nên consumer phải chờ. Một buffer đầy **vẫn thỏa invariant**, nhưng producer cần chờ. Khi các thread thay đổi buffer, chúng phải luôn giữ invariant đúng tại những điểm **nhả lock, đi vào wait hoặc công bố trạng thái**; một predicate đang chờ có thể đổi từ đúng sang sai mà invariant vẫn luôn đúng.

Ví dụ buffer capacity=1, đang chứa `A`:

| Thời điểm | `size` | Invariant | `notEmpty` | `notFull` |
| --- | ---: | --- | --- | --- |
| Trước khi lấy A | 1 | Đúng | Đúng | Sai |
| Sau khi lấy A | 0 | Đúng | Sai | Đúng |
| Sau khi thêm B | 1 | Đúng | Đúng | Sai |

**Nguyên tắc:** invariant bảo vệ **sự hợp lệ của state**; predicate quyết định **quyền thực hiện thao tác tiếp theo**. Một signal chỉ nói “trạng thái có thể đã thay đổi”; nó không trực tiếp thay cho predicate.

## 2. Vì sao cần kiểm tra và đợi trong cùng giao thức khóa?

Sai lầm kinh điển:

~~~java
// Minh họa logic sai: check và wait không được phối hợp.
if (!ready) {
    waitForSignal();
}
~~~

Nếu thread A nhìn thấy `ready=false`, thread B đổi `ready=true` và phát tín hiệu, rồi A mới bắt đầu đợi, tín hiệu đã qua. A có thể chờ mãi dù trạng thái đã sẵn sàng. Đây là dạng **missed notification / lost signal**.

Với monitor, mẫu đúng:

~~~java
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
    // Chỉ tiến lên khi predicate đúng dưới monitor.
    consumeProtectedState();
}
~~~

Đây là **đoạn minh họa trong method**, giả định `monitor`, `ready`, `consumeProtectedState()` đã khai báo và `InterruptedException` được xử lý/khai báo. Thread sửa state phải sử dụng **cùng monitor**:

~~~java
synchronized (monitor) {
    ready = true;
    monitor.notifyAll();
}
~~~

Trình tự:
1. Thread chờ lấy monitor rồi đọc predicate.
2. Nếu predicate sai, nó gọi `monitor.wait()` **khi vẫn đang giữ monitor đó**.
3. `wait()` đặt thread vào wait set và **nhả monitor của `monitor`** để thread khác có thể lấy lock, thay đổi trạng thái và phát signal.
4. Khi `wait()` trở về bình thường, thread đã **lấy lại monitor**; nó lặp qua `while` để kiểm tra predicate mới.
5. Chỉ khi predicate đúng, thread mới thực hiện thao tác và cập nhật trọn invariant trong vùng lock cần thiết.

Việc kiểm tra và bắt đầu chờ được phối hợp thông qua cùng monitor nên producer không thể chen vào “khoảng giữa” và khiến một lần cập nhật hợp lệ bị bỏ lỡ. Nếu producer cập nhật **trước** khi consumer lấy monitor, consumer quan sát predicate đúng và **không chờ**. Ta không cần đếm các `notify` đã gửi: **state mới là sự thật**.

### Wait nhả lock nào?

`Object.wait()` chỉ nhả **monitor của đối tượng gọi `wait()`**; nó không tự nhả những monitor/`ReentrantLock` khác mà thread đang giữ. Trước khi quay về hoặc ném `InterruptedException`, thread phải lấy lại monitor được dùng để chờ. Nếu code giữ thêm lock B trong khi `wait()` trên A, các thread cần B vẫn có thể bị kẹt: đây là một nguồn deadlock cần tránh.

`Thread.sleep()` **không nhả monitor**. Vì vậy, gọi `Thread.sleep(100)` bên trong `synchronized` không thể thay cho một condition wait hợp lệ.

## 3. Giữ invariant trước khi nhả khóa

Xét một số lượng `total` được chia thành `available` và `reserved`:

~~~text
available >= 0
reserved >= 0
available + reserved == total
~~~

Đoạn sau sai về nguyên tắc:

~~~java
synchronized (lock) {
    available--; // Invariant available + reserved == total bị sai tạm thời.
    lock.wait(); // Nhả lock khi trạng thái chưa nhất quán.
    reserved++;  // Có thể chưa bao giờ đến đây.
}
~~~

Đây là **đoạn cố ý sai**, chỉ minh họa lỗi, không phải class chạy độc lập. Khi `wait()` nhả monitor, các thread khác lấy được `lock` sẽ có thể nhìn thấy `available + reserved == total - 1`. Nếu thread bị interrupt, phần tăng `reserved` có thể không chạy. `synchronized` tự nhả lock khi ra khỏi khối nhưng **không rollback dữ liệu**.

Đúng hơn là **chờ trước khi bắt đầu thay đổi invariant**, rồi cập nhật đầy đủ dưới lock:

~~~java
synchronized (lock) {
    while (available == 0) {
        lock.wait();  // State vẫn hợp lệ trước khi nhả lock.
    }

    available--;
    reserved++;       // Hoàn tất một chuyển đổi hợp lệ.
}
~~~

Đây cũng là đoạn trong method có `throws InterruptedException` và các field được khai báo. Đường trả lại tài nguyên phải sửa hai field dưới **cùng `lock`** rồi `notifyAll()` khi có thể khiến `available > 0`.

Trong một critical section có thể có trạng thái trung gian khi thread vẫn nắm lock, nhưng phải cẩn thận với callback, phương thức synchronized reentrant và exception. Một phương thức khác có thể được gọi trong khi state chưa hoàn chỉnh và quan sát sai invariant. Tốt nhất là **validate trước, commit trạng thái ngắn và liền mạch, khôi phục invariant trước bất kỳ lệnh wait/unlock/return nào**.

## 4. Vì sao sau khi thức dậy phải dùng while?

`Object.wait()` và `Condition.await()` có thể kết thúc chờ **khi predicate vẫn sai**. Có ít nhất ba lý do:

1. **Spurious wakeup:** Java cho phép wait/await trở về dù không có tín hiệu ứng với predicate. Vì vậy phải viết code chịu được trường hợp này.
2. **Nhiều waiter tranh tài nguyên:** Hai consumer cùng được `notifyAll()`, nhưng consumer đầu tiên lấy phần tử duy nhất; consumer thứ hai lấy lại lock sau đó thì buffer đã rỗng.
3. **State đổi sau signal:** Producer phát tín hiệu, rồi một thread khác có thể chiếm lock và tiêu thụ tài nguyên trước waiter cũ.

~~~text
Hai consumer C1, C2 đang đợi notEmpty.
Producer thêm đúng một item X và notifyAll().

C1 lấy monitor trước --> lấy X, size=0, nhả monitor.
C2 lấy monitor sau  --> size vẫn bằng 0.

if (!queue.isEmpty()) { wait(); }  // SAI: sau wakeup có thể remove rỗng.
while (queue.isEmpty()) { wait(); } // ĐÚNG: kiểm tra lại trước khi remove.
~~~

Mẫu đúng của consumer là:

~~~java
synchronized (monitor) {
    while (queue.isEmpty()) {
        monitor.wait();
    }
    return queue.removeFirst();
}
~~~

Đây là đoạn minh họa bên trong method có `throws InterruptedException`. **Lần kiểm tra cuối cùng và `removeFirst()` nằm dưới cùng monitor**. Nếu bỏ lock giữa hai bước, race check-then-act quay lại.

Không được dùng một biến kiểu `receivedNotify` làm nguồn sự thật rồi bỏ qua predicate của queue. `notify()/notifyAll()` không chứa phần tử, không tự bảo đảm số lượng tài nguyên, và tín hiệu không được tích lũy cho những waiter sẽ bắt đầu chờ về sau.

## 5. Object.wait(), notify() và notifyAll() đúng hợp đồng

| API | Vai trò | Quy tắc quan trọng |
| --- | --- | --- |
| `monitor.wait()` | Chờ không giới hạn thời gian | Caller phải giữ monitor; nhả monitor khi chờ và lấy lại trước khi trở về |
| `monitor.wait(timeoutMillis)` | Chờ có giới hạn | `wait(0)` nghĩa là không giới hạn; timeout không bảo đảm predicate đúng |
| `monitor.notify()` | Đánh thức **một** waiter bất kỳ trong wait set | Không chọn theo nghiệp vụ, không bảo đảm fairness; caller phải giữ monitor |
| `monitor.notifyAll()` | Đánh thức mọi waiter trong wait set | Mỗi waiter phải tranh monitor và kiểm tra lại predicate |
| `Thread.sleep(duration)` | Tạm ngừng theo thời gian | **Không nhả monitor**, không phải cơ chế condition synchronization |

Cả `wait`, `notify` và `notifyAll` yêu cầu thread gọi đang giữ **chính monitor đó**. Nếu không có `synchronized(monitor)` hoặc quyền sở hữu monitor tương đương, chúng ném `IllegalMonitorStateException`. Giữ monitor của **object khác** không hợp lệ.

### Tín hiệu không chuyển lock ngay lập tức

~~~java
synchronized (monitor) {
    ready = true;
    monitor.notifyAll();
    // Thread hiện tại vẫn giữ monitor đến khi rời block.
}
~~~

Các waiter được đánh thức chưa thể chạy code sau `wait()` cho tới khi đã lấy lại monitor. Nếu thread phát tín hiệu giữ lock rất lâu sau `notifyAll()`, latency thức dậy cũng dài.

### Khi nào notifyAll() hợp lý?

Nếu cùng một monitor có nhiều loại waiter, ví dụ producers đợi `notFull` còn consumers đợi `notEmpty`, `notify()` có thể chọn một waiter **đang đợi predicate chưa đúng**. Nó thức dậy, tự chờ lại, trong khi waiter thuộc nhóm cần chạy vẫn ngủ. Trong giao thức monitor chung, `notifyAll()` thường dễ chứng minh tiến triển hơn. Đổi lại, đánh thức nhiều waiter có thể gây thêm contention.

`Condition` với `ReentrantLock` giúp mỗi predicate có một wait set riêng (`notFull`, `notEmpty`), nên có thể `signal()` đúng nhóm. Ngay cả khi signal đúng nhóm, **predicate vẫn phải được kiểm tra lại trong while**.

## 6. Ví dụ chạy được 1: bounded buffer dùng wait/notifyAll

Một bounded buffer có hai predicate, `queue.size() < capacity` cho producer và `!queue.isEmpty()` cho consumer. Ta giữ cùng monitor cho **kiểm tra**, **cập nhật queue**, **tín hiệu** và **snapshot size**.

Tạo file **`MonitorBufferDemo.java`**:

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MonitorBufferDemo {
    static final class BoundedBuffer<T> {
        private final ArrayDeque<T> queue = new ArrayDeque<>();
        private final int capacity;

        BoundedBuffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
        }

        synchronized void put(T value) throws InterruptedException {
            Objects.requireNonNull(value);
            while (queue.size() == capacity) {
                wait();
            }
            queue.addLast(value);
            notifyAll();
        }

        synchronized T take() throws InterruptedException {
            while (queue.isEmpty()) {
                wait();
            }
            T value = queue.removeFirst();
            notifyAll();
            return value;
        }

        synchronized int size() {
            return queue.size();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(2);
        List<Integer> received = new ArrayList<>();

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 6; i++) {
                    received.add(buffer.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 6; i++) {
                    buffer.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        consumer.start();
        producer.start();

        producer.join();
        consumer.join();

        System.out.println("received=" + received);
        System.out.println("remaining=" + buffer.size());
    }
}
~~~

Biên dịch và chạy Java 17+:

~~~bash
javac MonitorBufferDemo.java
java MonitorBufferDemo
~~~

Stdout khi không có interruption:

~~~text
received=[1, 2, 3, 4, 5, 6]
remaining=0
~~~

**Vì sao đúng?**

- Một producer đưa lần lượt số 1–6, một consumer lấy lần lượt sáu phần tử, và queue cam kết FIFO. Thứ tự được giữ dù thread nào được chạy trước.
- `put()` chỉ thêm khi `queue.size() < capacity`; `take()` chỉ lấy khi `!queue.isEmpty()`.
- Không thread nào truy cập trực tiếp `ArrayDeque` từ bên ngoài buffer. **Mọi đường truy cập** đều qua monitor của đối tượng buffer.
- `wait()` chỉ được gọi trong lúc queue vẫn hợp lệ; nó nhả monitor để phía kia có thể thay đổi queue.
- Sau wakeup, `while` lặp kiểm tra lại predicate. `notifyAll()` được phát **sau khi state đổi**, giúp phía đối diện thử tiến lên.
- `join()` khiến main đọc `received` sau khi consumer đã kết thúc. List `received` chỉ được consumer ghi; main không đọc song song với nó.

**Giới hạn:** Demo biết trước số lần truyền và nhận, không có hủy công việc, shutdown bất thường hoặc timeout. Nếu producer dừng sớm vì lỗi, consumer có thể chờ vô hạn; code production cần protocol `close`/end-of-stream, timeout hoặc cancellation rõ ràng. `ArrayDeque` không thread-safe khi dùng trực tiếp; sự an toàn ở đây đến từ monitor bảo vệ buffer. Đối với producer–consumer thông thường, cân nhắc `BlockingQueue` chuẩn thay vì viết tay.

### Phân tích invariant qua một lần chờ

~~~text
Consumer giữ monitor:
  size == 0 => chưa thể take, invariant vẫn đúng
     |
     v
  wait() nhả monitor
     |
     v
Producer lấy monitor, put(X), size == 1, notifyAll(), nhả monitor
     |
     v
Consumer lấy lại monitor, kiểm tra size > 0
     |
     +-- true: lấy X, size == 0, invariant vẫn đúng
     |
     +-- false: tiếp tục wait nếu consumer khác đã lấy X
~~~

Giữa kiểm tra predicate cuối và thao tác lấy phần tử, **không nhả monitor**. Do đó không có interleaving gây remove-from-empty ở chính critical section đó.

## 7. Ví dụ chạy được 2: Gate giữ trạng thái, không phụ thuộc notify

Một `Gate` ban đầu đóng; mọi thread gọi `awaitOpen()` phải đợi đến khi `open()` được gọi. Trạng thái open **được lưu lại**; waiter đến sau vẫn đi qua mà không đòi hỏi một notification mới.

Tạo file **`GateInvariantDemo.java`**:

~~~java
public class GateInvariantDemo {
    static final class Gate {
        private boolean open;

        synchronized void awaitOpen() throws InterruptedException {
            while (!open) {
                wait();
            }
        }

        synchronized void open() {
            open = true;
            notifyAll();
        }

        synchronized boolean isOpen() {
            return open;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Gate gate = new Gate();
        Thread[] workers = new Thread[3];

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                try {
                    gate.awaitOpen();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "waiter-" + i);
            workers[i].start();
        }

        gate.open(); // Có thể xảy ra trước khi một waiter thật sự chờ.

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("open=" + gate.isOpen());
        System.out.println("completed=3");
    }
}
~~~

Chạy:

~~~bash
javac GateInvariantDemo.java
java GateInvariantDemo
~~~

Stdout bình thường:

~~~text
open=true
completed=3
~~~

Dù main mở gate rất sớm, những thread bắt đầu sau vẫn kiểm tra `open==true` và **không gọi wait**. Nếu một waiter đã ở wait set, `notifyAll()` giúp nó tỉnh dậy để kiểm tra predicate. Main không khẳng định thứ tự thực thi workers, chỉ đợi chúng kết thúc bằng `join()`. Đây là mẫu cho **điều kiện một chiều**. Nếu mở/đóng gate nhiều lần hoặc có các pha, phải định nghĩa generation/phase; `CountDownLatch` (one-shot), `CyclicBarrier` hoặc `Phaser` thường phù hợp hơn.

## 8. Condition.await(): cùng nguyên tắc, nhiều wait set

`ReentrantLock` kết hợp `Condition` cũng có giao thức **predicate dưới lock**. Nó giúp phân nhóm producer và consumer theo hai điều kiện riêng.

Mẫu:

~~~java
lock.lockInterruptibly();
try {
    while (!ready) {
        condition.await();
    }
    useState();
} finally {
    lock.unlock();
}
~~~

Đây là đoạn minh họa trong method có `lock`, `condition`, `ready` và `useState` đã khai báo. Những quy tắc cần giữ:
- Caller phải đang giữ đúng `ReentrantLock` liên kết với `Condition`.
- `await()` nhả lock trong khi chờ, rồi **lấy lại lock trước khi trở về** hoặc ném `InterruptedException`.
- `signal()`/`signalAll()` phát tín hiệu cho đúng condition; thread phát signal vẫn giữ lock cho tới khi unlock.
- Waiter kiểm tra lại predicate bằng `while`, kể cả khi có signal hoặc spurious wakeup.
- `unlock()` luôn ở `finally`; lock phải bao trọn phần đổi invariant.

### Ví dụ chạy được 3: buffer có notFull và notEmpty

Tạo file **`ConditionBufferDemo.java`**:

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionBufferDemo {
    static final class Buffer<T> {
        private final ArrayDeque<T> items = new ArrayDeque<>();
        private final int capacity;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();

        Buffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity <= 0");
            }
            this.capacity = capacity;
        }

        void put(T item) throws InterruptedException {
            Objects.requireNonNull(item);
            lock.lockInterruptibly();
            try {
                while (items.size() == capacity) {
                    notFull.await();
                }
                items.addLast(item);
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        T take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (items.isEmpty()) {
                    notEmpty.await();
                }
                T result = items.removeFirst();
                notFull.signal();
                return result;
            } finally {
                lock.unlock();
            }
        }

        int size() {
            lock.lock();
            try {
                return items.size();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Buffer<Integer> buffer = new Buffer<>(2);
        List<Integer> received = new ArrayList<>();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    received.add(buffer.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        producer.start();

        producer.join();
        consumer.join();

        System.out.println("received=" + received);
        System.out.println("remaining=" + buffer.size());
    }
}
~~~

Chạy:

~~~bash
javac ConditionBufferDemo.java
java ConditionBufferDemo
~~~

Stdout trong đường thực thi bình thường:

~~~text
received=[1, 2, 3, 4, 5]
remaining=0
~~~

Buffer dùng một `ReentrantLock` bảo vệ **tất cả** truy cập `items`. Producer chỉ `await` trên `notFull`; consumer chỉ `await` trên `notEmpty`. Mỗi cập nhật thành công phát signal cho phía đối diện khi predicate có thể đã đúng, nhưng waiter vẫn kiểm tra lại `while`. `signal()` cho một waiter phù hợp với kiểu buffer này; với protocol phức tạp, cần xem xét liveness và khả năng phải dùng `signalAll()`.

**Chú ý:** một `Condition` không tự khiến tất cả thao tác với một data structure an toàn. Nếu code bên ngoài trực tiếp sửa `items` mà không giữ `lock`, invariant vẫn có thể bị phá.

## 9. Timeout: thức dậy vì hết hạn không phải predicate đúng

`Object.wait(long millis)` hỗ trợ thời gian chờ; `wait(0)` nghĩa là chờ vô hạn. `Condition.await(long, TimeUnit)` có kết quả theo tín hiệu/timeout, và `Condition.awaitNanos` trả thời gian còn lại. Nhưng trong mọi trường hợp, khi quay về từ wait:
- Timeout **không** biến false thành true.
- Signal **không** chứng minh predicate vẫn true khi waiter giữ lại lock.
- Spurious wakeup **không** thay đổi invariant nghiệp vụ.

Cách sai:

~~~java
while (!ready) {
    monitor.wait(1000);
}
~~~

Nếu cứ dùng lại nguyên một giây sau mỗi lần thức dậy, tổng thời gian có thể kéo dài vượt một giây nhiều lần. Ngoài ra, nếu thực sự cần hard deadline, phải tính cả thời gian đợi lock.

### Mẫu Condition.awaitNanos để quản lý ngân sách chờ

~~~java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// Các field và method trong một class, không phải file độc lập:
private final ReentrantLock lock = new ReentrantLock();
private final Condition changed = lock.newCondition();
private boolean ready;

public boolean awaitReady(long timeout, TimeUnit unit)
        throws InterruptedException {
    long remaining = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
        while (!ready) {
            if (remaining <= 0) {
                return false;
            }
            remaining = changed.awaitNanos(remaining);
        }
        return true;
    } finally {
        lock.unlock();
    }
}

public void publishReady() {
    lock.lock();
    try {
        ready = true;
        changed.signalAll();
    } finally {
        lock.unlock();
    }
}
~~~

`awaitNanos` nhả lock rồi lấy lại trước khi quay về; mỗi lần tỉnh thread lặp kiểm tra predicate, và `remaining` được giảm theo hợp đồng API để không “cấp lại” nguyên timeout. Nếu timeout, method trả `false` mà không sửa trạng thái, và `finally` nhả lock.

**Giới hạn:** ngân sách trên áp dụng cho **thời gian chờ condition**; `lockInterruptibly()` lúc đầu có thể đợi lâu mà chưa trừ vào `remaining`. Nếu cần deadline cho toàn bộ hành trình bao gồm cả lock acquisition, tính deadline từ `System.nanoTime()` và sử dụng `tryLock(remaining, unit)` hoặc giao thức tương đương. Không tuyên bố một timed wait riêng là deadline end-to-end của cả request.

## 10. Interrupt và exception phải bảo toàn state

### 10.1. InterruptedException khi đang wait/await

Khi bị interrupt, `Object.wait()`/`Condition.await()` có thể ném `InterruptedException`. Với monitor wait, thread phải lấy lại monitor trước khi exception được ném theo hợp đồng. **Interruption không có nghĩa điều kiện đã đúng**.

Trong buffer:
- `put` bị interrupt khi đang đợi chỗ trống: vì chưa gọi `addLast`, lần put đó chưa thêm phần tử.
- `take` bị interrupt khi đợi có dữ liệu: vì chưa gọi `removeFirst`, lần take đó chưa lấy phần tử.
- Các thread khác vẫn có thể đã thay đổi buffer trong lúc thread này chờ; không nên hiểu “không có cập nhật từ thao tác bị ngắt” thành “buffer không đổi”.

Nếu phương thức có thể ném tiếp exception, thường nên `throws InterruptedException` để caller quyết định cancellation. Nếu không thể ném lên và cần thoát:

~~~java
try {
    doInterruptibleWait();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // Khôi phục cờ interrupt.
    return;
}
~~~

Đây là đoạn minh họa trong một method `void`. Không nuốt interruption rồi chờ mãi nếu ứng dụng có quy trình shutdown.

### 10.2. Lock được nhả không có nghĩa thay đổi được rollback

~~~java
synchronized (monitor) {
    available--;
    operationThatMayThrow();
    reserved++;
}
~~~

Đoạn trên minh họa lỗi: nếu method giữa chừng ném exception, `synchronized` tự nhả monitor khi thoát, nhưng invariant `available + reserved == total` có thể sai. Cần **validate trước khi commit**, thực hiện thay đổi nhất quán dưới lock, hoặc có rollback chính xác khi exception xảy ra. Giảm phạm vi side effect có thể ném trong lúc invariant tạm chưa đúng.

Với `ReentrantLock`, dùng:

~~~java
lock.lock();
try {
    // Kiểm tra predicate và cập nhật invariant trọn vẹn.
} finally {
    lock.unlock();
}
~~~

Đây là đoạn minh họa, giả định `lock` đã khởi tạo. Không bỏ `unlock()` trong nhánh exception; cũng không cố unlock khi chưa thực sự lấy được lock.

### 10.3. Shutdown và điều kiện sẽ không bao giờ đúng

Nếu producer ngừng vĩnh viễn mà consumer đợi `notEmpty` không thời hạn, chương trình có thể treo dù invariant vẫn đúng. Một protocol thực phải mô tả điều kiện **closed/cancelled** và đánh thức waiter:

~~~java
synchronized (monitor) {
    while (queue.isEmpty() && !closed) {
        monitor.wait();
    }
    if (queue.isEmpty() && closed) {
        // Kết thúc theo hợp đồng, ví dụ trả Optional.empty().
    } else {
        // Lấy phần tử đang có.
    }
}
~~~

Đây là **pseudocode Java** minh họa control flow với `monitor`, `queue`, `closed` được bảo vệ cùng lock, không phải method chạy độc lập. Đường `close` cần đặt `closed=true` và `notifyAll()` khi giữ monitor. Khi có nhiều loại waiter, phải xem xét shutdown có đánh thức **mọi nhóm đang chờ** không. Nếu dùng queue chuẩn, có thể dùng sentinel/poison pill hoặc cancellation protocol thích hợp, bảo đảm không nhầm sentinel với dữ liệu hợp lệ.

## 11. Happens-before và memory visibility

Giao thức monitor vừa tránh lost notification vừa đảm bảo visibility:

~~~java
// Producer:
synchronized (monitor) {
    data = prepared;
    ready = true;
    monitor.notifyAll();
}

// Consumer:
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
    use(data);
}
~~~

Đây là hai đoạn minh họa dùng các biến và method khai báo ở phạm vi bên ngoài. Theo Java Memory Model, các ghi trước **unlock** một monitor happens-before các hành động sau **lock** tiếp theo trên cùng monitor. Vì vậy consumer sau khi lấy lại monitor có cơ chế để thấy dữ liệu producer công bố. Chỉ gọi `notifyAll()` mà không bảo vệ predicate/dữ liệu chia sẻ theo một giao thức memory visibility thì chưa đủ.

`Condition.await()` đi kèm `ReentrantLock` cũng nhả/lấy lại đúng lock và hưởng memory semantics tương ứng. Các lớp `BlockingQueue`, `CountDownLatch`, `Semaphore`, `Future` có hợp đồng memory consistency riêng; khi dùng đúng abstraction có thể tránh rất nhiều lỗi chứng minh thủ công.

**Không** thay vòng chờ bằng cách nhìn `thread.getState() == WAITING` hoặc dựa vào `Thread.sleep`. `Thread.State` chỉ là một snapshot tại thời điểm đọc, không thể quyết định an toàn điều kiện nghiệp vụ, và không cung cấp mutual exclusion.

## 12. Các lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Hướng sửa |
| --- | --- | --- |
| `if (!ready) wait()` | Spurious wakeup hoặc waiter khác làm predicate sai | Dùng `while (!ready) wait()` |
| Kiểm tra ready ở ngoài lock rồi mới wait | Có khoảng trống mất tín hiệu | Kiểm tra và wait dưới cùng monitor |
| Gọi wait/notify mà không giữ monitor đúng | `IllegalMonitorStateException` | Giữ đúng monitor khi gọi |
| Wait trong lúc invariant đang bị phá dở | Nhả monitor cho thread khác đọc state sai | Khôi phục invariant trước wait/unlock |
| Tin rằng notifyAll làm waiter chạy ngay | Thread còn phải lấy lại monitor | Giữ critical section ngắn, check predicate |
| Chỉ notify với nhiều loại waiter khác nhau | Có thể đánh thức thread không tiến triển | `notifyAll` hoặc nhiều `Condition` |
| Dùng sleep trong synchronized để chờ | Không nhả monitor, không theo predicate | `wait/await` hoặc primitive cấp cao |
| Timed wait tính lại nguyên timeout mỗi vòng | Nhiều lần tỉnh kéo dài vượt mục tiêu | Deadline hoặc remaining time |
| Bỏ qua InterruptedException | Hủy/shutdown có thể bị treo | Propagate hoặc restore interrupt rồi thoát |
| Exception giữa hai bước cập nhật invariant | Lock nhả nhưng state không tự rollback | Commit nguyên tử/rollback có cấu trúc |
| Chờ vô hạn không có protocol close | Thread có thể đợi không bao giờ xong | Closed flag, signal, timeout/cancel |
| Đọc state ngoài lock đã thống nhất | Visibility hoặc snapshot không nhất quán | Cùng lock hoặc safe published snapshot |
| Gắn quyết định notify với getState() | Snapshot có race, không là predicate | Dựa vào state được bảo vệ |
| Giữ lock khác khi gọi wait | Wait không nhả mọi lock; có thể deadlock | Kiểm soát lock ordering và phạm vi lock |

## 13. Hướng thiết kế và kiểm chứng

**Bước 1 — Viết invariant:** mô tả điều phải luôn đúng khi đối tượng nhả lock. Với buffer: `0 <= size <= capacity`, size bằng số phần tử thực.

**Bước 2 — Viết predicate của từng kiểu waiter:** producer chờ `size < capacity`, consumer chờ `size > 0`. Nếu có trạng thái closed hoặc nhiều pha, kết hợp nó vào điều kiện một cách rõ ràng.

**Bước 3 — Chọn một khóa bảo vệ trạng thái:** mọi đường đọc/ghi liên quan invariant phải dùng cùng monitor/lock, hoặc một giao thức ownership khác có bảo đảm tương ứng. Không để callback sửa state trực tiếp ngoài khóa.

**Bước 4 — Kiểm tra invariant tại điểm nhả lock:** trước khi `wait()`, `await()`, return hoặc exception làm rời critical section, state phải nhất quán.

**Bước 5 — Khi thức dậy, kiểm tra lại predicate:** luôn dùng `while`; đừng suy ra predicate đúng từ việc có signal.

**Bước 6 — Signal sau khi thay đổi state:** phát tín hiệu dưới đúng monitor/lock, đúng loại waiter, và đừng quên shutdown/closed. Signal không làm thay predicate.

**Bước 7 — Thiết kế lỗi và timeout:** mỗi waiter có đường ra khi producer không còn tiếp tục, khi bị interrupt hoặc khi deadline hết; không để trạng thái nửa cập nhật rò ra.

**Bước 8 — Chọn abstraction cấp cao khi phù hợp:** `BlockingQueue` cho producer/consumer, `CountDownLatch` cho one-shot readiness, `Semaphore` cho permits, `Phaser` cho nhiều pha. Tự viết monitor protocol khi invariant thực sự đặc biệt.

**Bước 9 — Test các interleaving quan trọng:** signal tới trước khi waiter bắt đầu chờ; hai waiter thức dậy tranh một phần tử; timeout trước/sau signal; interrupt trong wait; exception ở giữa cập nhật; close khi queue trống. Dùng `CountDownLatch`/barrier để điều phối test, **không coi `Thread.sleep` là bằng chứng ordering**.

**Bước 10 — Quan sát liveness và performance:** kiểm tra không deadlock, không starvation bất thường, không tăng queue vô hạn; đo queue wait, lock contention, successful throughput, p95/p99 latency và lỗi. Một trạng thái luôn thỏa invariant nhưng các thread chờ mãi vẫn là thiết kế không đạt yêu cầu hoạt động.

## 14. Câu hỏi tự kiểm tra

1. Buffer trống có vi phạm invariant `0 <= size <= capacity` không? **Không**, nhưng consumer có predicate `size > 0` đang sai.
2. Vì sao không gọi `wait` trong lúc hai trường chưa được cập nhật đồng bộ? **Vì wait nhả monitor, để thread khác nhìn trạng thái trung gian sai.**
3. Vì sao `while` cần thiết sau `notifyAll`? **Có spurious wakeup và tranh chấp khiến predicate vẫn sai.**
4. Nếu signal đến trước khi waiter vào wait set, làm sao tránh lost notification? **Ghi và đọc một predicate dưới cùng monitor; waiter sẽ bỏ qua wait khi đã đúng.**
5. `Object.wait()` nhả mọi lock thread đang giữ không? **Không, chỉ monitor của object gọi wait.**
6. `Thread.sleep()` bên trong synchronized có nhả monitor không? **Không.**
7. Vì sao `notifyAll()` không bảo đảm waiter chạy ngay? **Waiter còn phải lấy lại monitor sau khi thread phát tín hiệu nhả nó.**
8. Vì sao `wait(0)` không phải timeout ngay? **Giá trị 0 nghĩa là chờ không thời hạn.**
9. Khi `Condition.awaitNanos()` trả về nhưng predicate vẫn sai, cần làm gì? **Kiểm tra remaining và tiếp tục vòng while hoặc trả timeout.**
10. Có thể xem `InterruptedException` là predicate đã đúng không? **Không**, đó là đường hủy riêng.
11. Khi một method ném exception giữa cập nhật, synchronized tự rollback không? **Không, nó chỉ đảm bảo nhả monitor.**
12. Một consumer có thể chờ mãi nếu producer ngừng không? **Có**, nên cần giao thức close, cancellation, timeout hay sentinel.
13. Khi nào dùng `BlockingQueue` thay vì tự viết wait/notify? **Khi yêu cầu chính chỉ là chuyển phần tử từ producer sang consumer.**
14. Có thể dùng `thread.getState()` để quyết định condition đã đúng không? **Không**, đó là snapshot của thread, không phải trạng thái nghiệp vụ có lock.
15. Invariant và predicate khác nhau ở đâu? **Invariant nói đối tượng hợp lệ hay không; predicate nói một thao tác đã được phép tiến lên hay chưa.**

## Kết luận

**Invariant phải đúng ở các thời điểm quyền truy cập được bàn giao**, đặc biệt trước khi `wait()/await()` nhả lock, trước khi unlock hoặc một exception khiến method rời critical section. **Predicate phải được kiểm tra lại sau khi thread thức dậy và lấy lại lock**. Notification không lưu trạng thái nghiệp vụ, không bảo đảm waiter chạy ngay, và không chứng minh điều kiện đúng.

Một giao thức an toàn là **giữ khóa → kiểm tra predicate → chờ và nhả khóa nếu sai → lấy lại khóa → kiểm tra lại trong while → cập nhật trọn invariant → signal khi cần → nhả khóa**. Cần thêm lối thoát cho timeout, interruption và shutdown, cùng cách khôi phục invariant khi exception xảy ra.

## Tài liệu Java SE 17

- [Object.wait/notify/notifyAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): monitor, wait set, spurious wakeup, interruption.
- [Condition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html): await, awaitNanos, signal, signalAll.
- [ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): lock, lockInterruptibly, unlock.
- [BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): take/put/offer/poll và memory consistency.
- [Java Language Specification, Chapter 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Threads and Locks; happens-before.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-011">Contended monitor và rủi ro giữ khóa quá lâu</a>

<details>
<summary>Xem chi tiết</summary>

# Contended monitor và rủi ro giữ khóa quá lâu trong Java

Khi nhiều thread muốn vào cùng một khối `synchronized`, chỉ một thread được giữ **monitor** tại một thời điểm; những thread còn lại có thể phải đợi. Đây là **monitor contention**. Việc đợi là một phần bình thường của đồng bộ, nhưng nếu thread sở hữu monitor giữ khóa quá lâu, một đoạn mã tưởng chừng nhỏ có thể trở thành nút thắt cho toàn bộ ứng dụng: độ trễ tăng, throughput giảm, các thread chờ dồn lại, thậm chí xuất hiện deadlock hoặc starvation.

Bài học tập trung vào **Java Concurrency nói chung**: intrinsic monitor, vòng đời thread, Java Memory Model, cách thiết kế critical section và các công cụ quan sát trên JVM. Các cơ chế tích hợp riêng với Spring thuộc nội dung khác.

## 1. Monitor là gì và khi nào xảy ra contention?

Mỗi object Java có thể được dùng làm **intrinsic lock/monitor** cho một vùng mã `synchronized`. Khi một thread đã vào khối:

~~~java
synchronized (lock) {
    // Truy cập trạng thái chia sẻ được lock bảo vệ.
}
~~~

thread khác muốn vào `synchronized (lock)` phải đợi cho đến khi monitor được giải phóng. Nếu mỗi thread dùng một object khóa khác nhau, chúng **không** tranh chấp cùng monitor; việc chọn đúng identity của lock quyết định cả mutual exclusion lẫn bảo đảm visibility.

Một thread sở hữu monitor có thể vào lại `synchronized` trên cùng monitor vì monitor Java **reentrant**. Mỗi lần vào phải có một lần ra tương ứng; khi thoát các khối synchronized, JVM giải phóng monitor kể cả khi có exception thoát ra. Điều này không có nghĩa thao tác nghiệp vụ tự rollback sau exception.

### 1.1 Phân biệt ba loại tình huống

| Tình huống | Kết quả | Ví dụ |
| --- | --- | --- |
| **Không tranh chấp** | Thread lấy monitor và chạy critical section, thường nhanh. | Chỉ một thread sử dụng bộ đếm. |
| **Contention bình thường** | Một số thread đợi ngắn, rồi lần lượt vào critical section. | Nhiều thread cập nhật queue hoặc counter chung. |
| **Contention kéo dài** | Thread sở hữu lock chờ I/O, sleep, tính toán lâu hoặc gọi callback; các thread khác xếp hàng. | Worker giữ khóa khi gọi dịch vụ mạng hoặc xử lý file lớn. |

**Contention không đồng nghĩa deadlock.** Nếu holder nhả monitor trong thời gian hữu hạn và thread khác được scheduler cấp CPU, chúng có thể tiếp tục. Deadlock là trường hợp phụ thuộc vòng tròn khiến một nhóm thread không thể tiến triển; nó có thể liên quan nhiều monitor hoặc các loại chờ khác nhau.

**Contention cũng không tự chứng minh lỗi correctness.** Lock có thể bảo vệ invariant hoàn toàn đúng nhưng làm performance kém. Ngược lại, bỏ lock để tăng throughput khi chưa thay giao thức có thể gây race và dữ liệu sai.

## 2. `synchronized` điều khiển ownership và memory visibility

`synchronized` cung cấp hai bảo đảm cần phân biệt:

1. **Mutual exclusion:** tại một thời điểm, chỉ một thread có thể sở hữu một monitor cho các critical section trên chính monitor đó.
2. **Happens-before:** lần unlock trên monitor **happens-before** lần lock về sau trên cùng monitor. Các cập nhật thực hiện theo giao thức đúng sẽ được thread khác quan sát theo quy tắc Java Memory Model.

Ví dụ:

~~~java
private final Object lock = new Object();
private int balance;

void deposit(int amount) {
    synchronized (lock) {
        balance += amount;
    }
}

int balance() {
    synchronized (lock) {
        return balance;
    }
}
~~~

Đây là **mảnh lớp minh họa**, chưa phải file có `main`. Cả writer và reader dùng **cùng `lock`**, nên không chỉ phép cộng được bảo vệ khỏi chen ngang mà cả lần đọc cũng nhận visibility phù hợp.

Nếu chỉ đồng bộ writer rồi reader đọc `balance` ngoài lock, không thể coi mọi đường truy cập đã tuân thủ cùng protocol. Nếu dùng hai monitor khác nhau, việc cả hai chỗ đều có từ khóa `synchronized` cũng không tạo mutual exclusion chung.

Một monitor không tự quyết định fairness; Java **không bảo đảm FIFO** cho việc các contender lấy monitor. Thread chờ lâu có thể vẫn bị contender khác vượt qua tùy lịch thực thi.

### 2.1 `synchronized` method khóa object nào?

| Cú pháp | Monitor được sử dụng |
| --- | --- |
| `synchronized (mutex) { ... }` | Chính object reference `mutex`. |
| `public synchronized void update() { ... }` | `this` của instance đang chạy method. |
| `public static synchronized void reset() { ... }` | `Class` object của lớp khai báo method, ví dụ `Counter.class`. |

Hai instance method synchronized thuộc **hai instance khác nhau** không tự khóa nhau. Instance method synchronized và static synchronized cũng dùng **hai monitor khác nhau**. Khi bảo vệ state dùng chung, phải thiết kế lock identity và quyền sở hữu phù hợp.

Tránh `synchronized ("literal")`, `synchronized (Integer.valueOf(...))` hoặc khóa vào object mà code bên ngoài có thể vô tình dùng chung. String interned và boxed values được tái sử dụng có thể gây contention ngoài mong muốn. Thông thường chọn:

~~~java
private final Object lock = new Object();
~~~

và cho phép truy cập state chỉ qua các method của owner.

## 3. Thread `BLOCKED`, `WAITING` và `TIMED_WAITING` khác nhau

Khi xem thread dump, từ “đang chờ” có thể mô tả nhiều cơ chế khác nhau. Trong Java, `Thread.State` giúp phân biệt nguyên nhân.

| Trạng thái | Ý nghĩa thường gặp | Ví dụ |
| --- | --- | --- |
| `RUNNABLE` | Đang thực thi hoặc sẵn sàng để JVM thực thi; không nhất thiết chiếm CPU đúng lúc quan sát. | Tính toán trong method. |
| `BLOCKED` | Đang chờ lấy intrinsic monitor để vào hoặc vào lại vùng `synchronized`. | Contender ở trước `synchronized(lock)` khi holder chưa nhả. |
| `WAITING` | Chờ vô thời hạn do một operation chờ điều kiện hoặc hoàn tất. | `Object.wait()` không timeout, `Thread.join()` không timeout, `CountDownLatch.await()`. |
| `TIMED_WAITING` | Chờ có thời hạn. | `Thread.sleep(100)`, `Thread.join(100)`, timed `Object.wait()`. |
| `TERMINATED` | Thread đã kết thúc. | `run()` trả về hoặc thoát bởi exception. |

**Quan trọng:** một thread đang đợi `ReentrantLock` có thể xuất hiện ở `WAITING` hoặc `TIMED_WAITING` dựa trên primitive park của lock, không nhất thiết ở `BLOCKED`. `BLOCKED` trong `Thread.State` nói cụ thể về **intrinsic monitor**.

Ngoài ra, một thread ở `WAITING` vẫn có thể **đang giữ một monitor khác**. Trạng thái thread không cho biết nó không nắm giữ khóa nào. Phải đọc stack trace và phần “locked monitors” trong thread dump.

### 3.1 Bức tranh contention

~~~text
Thread A                            Thread B                       Thread C
--------                            --------                       --------
synchronized (lock)                synchronized (lock)            synchronized (lock)
       |                                   |                              |
       v                                   v                              v
owns monitor                         BLOCKED waiting                BLOCKED waiting
       |
critical section dài
       |
unlock() khi thoát synchronized
                                           |
                                           v
                                    một contender lấy monitor
~~~

Không có quy tắc FIFO bắt buộc trong intrinsic monitor để xác định B hay C thắng. Điều ta có thể dựa vào là **chỉ một holder** chạy trong cùng critical section tại một thời điểm.

## 4. Ví dụ Java 17+: quan sát hai thread tranh cùng monitor

Ví dụ dùng latch để holder giữ monitor đến khi main cho phép nhả. Như vậy thứ tự message là xác định, không phụ thuộc việc `sleep()` có đủ dài hay không.

Tạo file `MonitorContentionDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;

public final class MonitorContentionDemo {
    public static void main(String[] args) throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch holderEntered = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CountDownLatch contenderApproaching = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (monitor) {
                System.out.println("Holder entered");
                holderEntered.countDown();
                try {
                    releaseHolder.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("Holder leaving");
            }
        }, "holder");

        Thread contender = new Thread(() -> {
            contenderApproaching.countDown();
            synchronized (monitor) {
                System.out.println("Contender entered");
            }
        }, "contender");

        holder.start();
        holderEntered.await();
        contender.start();
        contenderApproaching.await();

        releaseHolder.countDown();
        holder.join();
        contender.join();
        System.out.println("Finished");
    }
}
~~~

Chạy trên JDK 17 trở lên:

~~~bash
javac MonitorContentionDemo.java
java MonitorContentionDemo
~~~

Stdout khi không có external interruption:

~~~text
Holder entered
Holder leaving
Contender entered
Finished
~~~

**Phân tích:** `holder` lấy monitor trước khi báo `holderEntered`. `contender` không thể in “Contender entered” trước khi holder thoát `synchronized`. Main dùng `releaseHolder.countDown()` để cho holder hoàn tất rồi join hai thread.

`contenderApproaching.countDown()` chỉ báo thread đã đến trước câu lệnh đồng bộ; nó **không chứng minh contender đã kịp đến trạng thái BLOCKED** trước lúc main mở cổng. Muốn chụp đúng trạng thái BLOCKED, phải để holder giữ monitor trong lúc lấy thread dump hoặc quan sát `Thread.getState()` khi contender thực sự đang đợi. Đừng xem một lần chạy in đúng thứ tự là chứng minh đã đo được contention.

Một chi tiết cố ý trong demo: holder gọi `CountDownLatch.await()` **ngay khi đang giữ monitor**. Await trên latch không nhả intrinsic monitor đó, nên các contender có thể bị chặn trong lúc holder đợi. Đây là cách tái hiện tình huống **giữ khóa quá lâu** để học, không phải mẫu nên áp dụng cho business logic.

## 5. Vì sao critical section dài gây hại?

Độ dài critical section là thời gian mà một thread giữ lock từ lúc vào đến lúc ra. Khi nhiều thread tranh chấp, thời gian này góp phần quyết định độ trễ của tất cả contender.

Ví dụ ý niệm: 20 yêu cầu cùng tranh một monitor; mỗi holder giữ monitor 50 ms chỉ để xử lý việc nằm trong lock. Trong một lịch chạy tuần tự lý tưởng, 20 critical section đã chiếm khoảng 1.000 ms tổng thời gian giữ khóa, chưa kể scheduling và overhead; các yêu cầu đến sau có thể phải đợi nhiều lượt. Đây chỉ là **ước lượng minh họa**, không phải công thức đo latency thực của JVM.

Hệ quả thường gặp:

- **Lock convoy:** các thread lần lượt xếp hàng sau một holder chậm. Một vài lần xử lý kéo dài tạo đuôi latency lớn.
- **Throughput giảm:** công việc có thể chạy song song bên ngoài nhưng bị tuần tự hóa bên trong monitor.
- **Tail latency tăng:** trung bình có thể còn ổn nhưng p95/p99 của những request cần lock tăng mạnh.
- **Thread exhaustion:** trong thread pool có số worker hữu hạn, nhiều worker đợi lock khiến ít worker còn rảnh để làm việc khác.
- **Priority inversion:** thread có ưu tiên thấp hơn đang giữ lock mà thread khác cần; thread ưu tiên cao phải đợi. Java thread priority không cung cấp một giải pháp portable đảm bảo cho tình huống này.
- **Độ nhạy với lỗi:** I/O chậm, retry ngoài ý muốn hoặc callback ném exception có thể biến một critical section nhỏ thành điểm nghẽn lớn.

Với mỗi lock, hãy tìm **đường chạy chậm nhất**, không chỉ dòng mã trung bình. Một nhánh hiếm gọi mạng, xử lý file, chờ latch hoặc gọi method không rõ thời lượng vẫn có thể khiến hàng đợi phía sau tăng vọt.

## 6. Những thao tác rất nguy hiểm khi thực hiện trong monitor

### 6.1 I/O, mạng, cơ sở dữ liệu và remote call

~~~java
synchronized (lock) {
    updateLocalState();
    remoteClient.send(payload); // Có thể chậm, retry hoặc không trả về.
}
~~~

Đây là mảnh mã minh họa, không phải file runnable; `remoteClient`, `payload`, `updateLocalState` và `lock` là các thành phần giả định. Tất cả contender khác cùng lock phải đợi **trong suốt remote call**, ngay cả khi họ chỉ cần đọc một giá trị.

Một hướng thiết kế là chuẩn bị dữ liệu/snapshot dưới lock, nhả lock, thực hiện I/O bên ngoài. Tuy nhiên nếu kết quả I/O quyết định việc sửa state, cần thiết kế commit/validation lại để tránh **stale snapshot**. Không chỉ di chuyển dòng gọi ra ngoài lock mà bỏ qua tính đúng đắn.

### 6.2 `Thread.sleep()` và các phép chờ khác

~~~java
synchronized (lock) {
    Thread.sleep(500);
}
~~~

Mảnh này giả định method khai báo/xử lý `InterruptedException`. `sleep()` **không nhả monitor** đang giữ. Trong 500 ms, contender có thể bị BLOCKED dù thread holder không làm công việc hữu ích.

Tương tự, `CountDownLatch.await()`, `Future.get()` và `Thread.join()` không tự nhả **monitor không liên quan** mà thread đang giữ. Gọi chúng khi đang giữ lock có thể gây lock convoy hoặc deadlock nếu tác vụ được chờ lại cần chính lock đó.

Ngược lại, `Object.wait()` được gọi khi đang giữ monitor của **đúng object đó** sẽ nhả monitor đó và chờ theo giao thức wait/notify; khi tỉnh dậy, thread phải lấy lại monitor trước khi return. `wait()` **không nhả những monitor khác** mà thread đang giữ. Nó cũng cần vòng `while` kiểm tra predicate vì có thể có spurious wakeup.

**Không thay `sleep()` bằng `wait()` tùy tiện**: wait/notify là protocol điều kiện có yêu cầu sở hữu monitor, tín hiệu và kiểm tra predicate. Với task completion, ưu tiên `Future`/latch; với producer–consumer, cân nhắc `BlockingQueue`.

### 6.3 Callback và mã bên ngoài không kiểm soát

~~~java
synchronized (lock) {
    state = next;
    listener.onChanged(state);
}
~~~

Đây là mảnh mã minh họa với `state`, `next`, `listener` đã khai báo. Callback có thể gọi lại object hiện tại, lấy lock khác, chờ I/O hoặc ném exception. Dù monitor Java reentrant, callback lấy lock theo thứ tự khác có thể tạo vòng chờ giữa nhiều thread.

Hãy cân nhắc copy snapshot và danh sách listener trong vùng lock, rồi gọi callback **sau khi nhả khóa**. Nếu callback cần phản ánh thứ tự sự kiện chính xác, phải thiết kế hàng đợi sự kiện/sequence version thay vì giả định chuyển vị trí lệnh giữ nguyên toàn bộ semantics.

## 7. Giảm thời gian giữ monitor mà vẫn bảo vệ invariant

Mục tiêu là **làm ít việc nhất có thể khi sở hữu monitor**, nhưng vẫn giữ các điều kiện dữ liệu phải được cập nhật atomically.

Ví dụ cần tính checksum của nội dung lớn rồi cập nhật bộ đếm. Tính checksum trên dữ liệu **local/immutable** có thể làm trước khi lấy lock. Chỉ cộng kết quả vào trạng thái chia sẻ bên trong monitor.

~~~java
int checksum = calculateChecksum(immutableInput); // Không cần lock.
synchronized (lock) {
    aggregateChecksum += checksum;
    processedCount++;
}
~~~

Đây là mảnh minh họa, chưa có method/class độc lập. Giả định `immutableInput` không bị thread khác sửa trong lúc tính, và checksum không phụ thuộc trạng thái chia sẻ đã thay đổi. Cả hai trường `aggregateChecksum` và `processedCount` được cập nhật dưới **cùng lock**, nên vẫn có một ranh giới atomic hợp lý với các reader cũng dùng lock.

### 7.1 Mẫu Prepare → Lock → Commit

Một quy trình thông dụng:

1. **Prepare:** kiểm tra tham số, parse input, tính toán thuần, tạo value object candidate; không truy cập state mutable bị chia sẻ nếu chưa có snapshot hợp lệ.
2. **Lock:** lấy monitor đúng owner của invariant.
3. **Validate:** kiểm tra lại precondition **phụ thuộc state hiện tại** trong critical section.
4. **Commit:** cập nhật nhóm trường cần atomically; không gọi I/O hay callback ở giữa.
5. **Unlock:** thoát khối synchronized sớm, cho thread khác có cơ hội.
6. **Post-commit:** phát event/gửi I/O sau lock, với protocol idempotency/thứ tự rõ ràng nếu cần.

Mẫu này giúp critical section ngắn hơn. Nhưng nếu việc chuẩn bị dùng một snapshot có thể lỗi thời, hãy so sánh version/kiểm tra lại trong lock trước khi commit.

### 7.2 Tối ưu không được làm hỏng snapshot

Giả sử phải chuyển `available--` và `reserved++` để giữ `available + reserved == capacity`. Có thể tính trước các tham số chỉ phụ thuộc input, nhưng việc đọc `available`, kiểm tra điều kiện và thay **cả hai trường** phải cùng critical section.

~~~java
boolean reserveOne() {
    synchronized (lock) {
        if (available == 0) {
            return false;
        }
        available--;
        reserved++;
        return true;
    }
}
~~~

Đây là mảnh method minh họa có các field và lock giả định. Nếu reader cần quan sát tổng, reader phải dùng cùng lock và **đọc cả hai field trong một lần lock**. Hai getter synchronized riêng lẻ có thể trả hai snapshot khác nhau nếu writer chen giữa chúng.

## 8. Ví dụ Java 17+: critical section nhỏ, cập nhật chính xác

Tạo `SmallCriticalSectionDemo.java`:

~~~java
public final class SmallCriticalSectionDemo {
    private static final class Totals {
        private final Object lock = new Object();
        private int processed;
        private long sum;

        void record(int contribution) {
            synchronized (lock) {
                processed++;
                sum += contribution;
            }
        }

        Snapshot snapshot() {
            synchronized (lock) {
                return new Snapshot(processed, sum);
            }
        }
    }

    private record Snapshot(int processed, long sum) {}

    private static int prepareWork(int input) {
        // Mô phỏng phần tính toán thuần bằng dữ liệu local.
        return input * 2;
    }

    public static void main(String[] args) throws InterruptedException {
        Totals totals = new Totals();
        Runnable task = () -> {
            for (int i = 1; i <= 1_000; i++) {
                int contribution = prepareWork(i);
                totals.record(contribution);
            }
        };

        Thread a = new Thread(task, "worker-a");
        Thread b = new Thread(task, "worker-b");
        a.start();
        b.start();
        a.join();
        b.join();

        Snapshot snapshot = totals.snapshot();
        System.out.println("Processed: " + snapshot.processed());
        System.out.println("Sum: " + snapshot.sum());
    }
}
~~~

Biên dịch/chạy JDK 17+:

~~~bash
javac SmallCriticalSectionDemo.java
java SmallCriticalSectionDemo
~~~

Stdout:

~~~text
Processed: 2000
Sum: 2002000
~~~

**Vì sao đúng?** Mỗi thread xử lý 1.000 giá trị, tổng 2.000 record. `prepareWork(i)` trả `i*2` và không đụng đến shared state, nên chạy ngoài monitor. `Totals.record()` giữ lock chỉ trong hai phép cập nhật. `snapshot()` đọc cả hai trường dưới cùng monitor. Tổng contribution của một thread là `2 × (1 + 2 + ... + 1000) = 1.001.000`; hai thread tạo `2.002.000`.

Không thể suy từ ví dụ nhỏ rằng thay đổi luôn nhanh hơn mọi phiên bản giữ lock dài; việc giảm lock scope thường có lợi khi phần prepare tốn thời gian. Nếu mọi phép tính phụ thuộc trực tiếp shared state thì phải cân bằng giữa correctness và mức tranh chấp.

## 9. Lock scope và sự đánh đổi về correctness

Một thay đổi tưởng như “tối ưu” có thể phá bảo đảm atomic:

~~~java
// Sai nếu check phụ thuộc state chia sẻ.
boolean canReserve;
synchronized (lock) {
    canReserve = available > 0;
}

// Thread khác có thể reserve hết quota tại đây.
if (canReserve) {
    synchronized (lock) {
        available--;
        reserved++;
    }
}
~~~

Đây là mảnh code lỗi, không phải chương trình độc lập. Kiểm tra và commit được chia thành **hai critical section khác nhau**, nên một thread khác có thể chen vào giữa. Tính đúng đắn yêu cầu kiểm tra lại precondition **trong cùng lần giữ lock** với cập nhật.

Cũng cần nghĩ đến lỗi và exception safety: `synchronized` nhả monitor nếu phát sinh exception, nhưng **không tự rollback** những phép ghi đã thực hiện trước exception. Nếu method cập nhật ba trường rồi gọi code có thể throw ở giữa, monitor được nhả với state có thể chưa nhất quán. Thiết kế **prepare/calculate all → validate → commit các phép gán an toàn**, hoặc có rollback đã kiểm chứng.

### 9.1 Snapshot/version khi tính toán ngoài lock

Nếu phép tính nặng phụ thuộc state hiện tại, có thể áp dụng optimistic pattern:

~~~text
lock:
    snapshot = copy(state, version)
unlock

heavyComputation(snapshot)  // có thể mất nhiều thời gian

lock:
    if (version đã đổi) retry hoặc từ chối
    else commit(candidate), tăng version
unlock
~~~

Đây là **mã giả**, không phải Java runnable. Việc snapshot phải đầy đủ và không chứa reference đến object mutable có thể tiếp tục thay đổi ngoài lock. Nếu version thay đổi, kết quả prepare có thể lỗi thời; đừng commit bất chấp.

Nếu phép tính có side effect không thể lặp an toàn (gửi tiền, gọi dịch vụ ngoài), retry cần cơ chế idempotency hoặc chuyển sang quy trình transaction rõ ràng. Không đưa side effect vào phần có thể được thực hiện lại mà không kiểm soát.

## 10. Các kiểu deadlock do giữ monitor khi chờ

Một lỗi đặc biệt nguy hiểm: holder giữ monitor rồi đợi một thread/task mà để hoàn thành lại cần monitor đó.

~~~java
Object lock = new Object();

Thread worker = new Thread(() -> {
    synchronized (lock) {
        // Worker cần lock để hoàn tất.
    }
});

synchronized (lock) {
    worker.start();
    worker.join(); // Caller vẫn giữ lock.
}
~~~

Đây là **mảnh minh họa lỗi**, method chứa nó phải xử lý `InterruptedException`. Caller giữ `lock` trong lúc `join()`; worker không vào được monitor nên không thể hoàn tất; caller lại đợi worker, tạo vòng chờ. `join()` **không giải phóng lock tùy ý** mà caller đang giữ.

Một cách sửa:

~~~java
worker.start();
worker.join(); // Chờ khi không giữ lock mà worker cần.
~~~

Đây cũng là mảnh code, giả định `worker` đã được xây dựng và method xử lý interruption.

Deadlock với hai monitor có thể xảy ra khi:

~~~text
Thread A: owns lock1, waits lock2
Thread B: owns lock2, waits lock1
~~~

Nếu thật sự cần nhiều lock, dùng **thứ tự khóa toàn cục** theo ID ổn định, nhất quán trên mọi đường gọi, và tránh callback có thể lấy lock khác ngược thứ tự. Với state thuộc cùng một invariant, một owner lock có thể đơn giản hơn.

**Phân biệt:**

- Contented monitor: các contender có thể tiến triển khi holder cuối cùng nhả lock.
- Deadlock: các participant tạo vòng phụ thuộc, không participant nào trong vòng có thể tự giải phóng chờ.
- Starvation: một thread không được phục vụ trong thời gian không giới hạn dù thread khác tiến triển; intrinsic monitor không có bảo đảm FIFO.
- Livelock: các thread vẫn chạy/phản ứng nhưng không hoàn thành công việc hữu ích.

Chỉ đo CPU sử dụng cao hay thấp không đủ để phân biệt những trường hợp này; cần dữ liệu về thread state, lock ownership và giao thức chờ.

## 11. Intrinsic monitor không có timed hoặc interruptible acquisition

Với `synchronized` thông thường, thread đang chờ **lấy monitor** không có API cho việc chỉ chờ tối đa N millisecond hoặc hủy việc chờ bằng `InterruptedException` như `lockInterruptibly()`. Nếu thread bị gọi `interrupt()` khi đang BLOCKED để lấy monitor, nó không tự bỏ yêu cầu lấy khóa ngay; interrupted flag có thể được ghi nhận để code xử lý sau khi đi tiếp.

Điều đó khác với `Object.wait()` khi thread đã vào monitor và chủ động nhả monitor để đợi điều kiện: wait là interruptible và yêu cầu sở hữu monitor đúng object.

Nếu cần giới hạn thời gian lấy lock hoặc xử lý cancellation trong lúc đợi, cân nhắc `ReentrantLock`:

- `tryLock()`: thử lấy lock ngay; trả `true/false`.
- `tryLock(timeout, unit)`: đợi có thời hạn, có thể ném `InterruptedException`.
- `lockInterruptibly()`: cho phép interruption khi đang chờ lock.
- `lock()`: đợi lấy lock theo hợp đồng không cùng semantics hủy như `lockInterruptibly()`.

Một lưu ý: `ReentrantLock` bảo đảm exclusion và memory effects tương tự monitor khi dùng đúng; nó **không** tự làm critical section ngắn lại. Nếu bạn vẫn gọi I/O chậm trong khi giữ ReentrantLock, contention vẫn tồn tại.

## 12. Ví dụ Java 17+: ReentrantLock.tryLock có timeout

Ví dụ này giữ lock bằng một thread rồi yêu cầu thread khác thử lấy lock trong thời hạn ngắn. Latch bảo đảm holder chưa nhả trước khi contender hoàn tất việc thử.

Tạo `TimedLockDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class TimedLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                holderHasLock.countDown();
                releaseHolder.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "holder");

        Thread contender = new Thread(() -> {
            try {
                boolean acquired = lock.tryLock(100, TimeUnit.MILLISECONDS);
                System.out.println("Acquired: " + acquired);
                if (acquired) {
                    try {
                        // Bảo vệ state nếu lấy được lock.
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "contender");

        holder.start();
        holderHasLock.await();
        contender.start();
        contender.join();
        releaseHolder.countDown();
        holder.join();
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac TimedLockDemo.java
java TimedLockDemo
~~~

Stdout khi không có external interruption:

~~~text
Acquired: false
~~~

**Vì sao xác định?** Holder không nhả lock cho đến khi main gọi `releaseHolder.countDown()`. Main chỉ mở cổng **sau khi contender kết thúc**, nên contender phải hết thời gian thử lock mà không thể lấy được khóa. Ngay cả nếu contender được scheduler cho chạy muộn, holder vẫn giữ lock cho đến khi contender hoàn thành.

`tryLock(100, TimeUnit.MILLISECONDS)` giới hạn **thời gian chờ để lấy lock** chứ không giới hạn toàn bộ tác vụ hoặc công việc sau khi lấy lock. Trong code thật, đừng dùng một timeout nhỏ tùy ý để thay SLA; xác định deadline ở cấp yêu cầu và xử lý nhánh không lấy được lock. Đảm bảo mọi nhánh đã acquire đều `unlock()` trong `finally`.

**Lưu ý:** ví dụ diễn giải đường thành công/thất bại khi không bị interrupt bởi tác nhân bên ngoài. Trong chương trình thực tế, cần thiết kế shutdown/cancellation để các latch được giải phóng và các thread được join theo chính sách rõ.

## 13. Điều kiện chờ đúng: `Object.wait()` và `Condition.await()`

Có trường hợp thread giữ lock nhưng cần chờ **một điều kiện dữ liệu** do thread khác thay đổi. Nếu đợi trong lúc vẫn giữ monitor, thread thay đổi điều kiện có thể không thể vào vùng khóa để phát tín hiệu.

Sử dụng monitor wait đúng cách:

~~~java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
    // ready đang đúng dưới monitor lock.
}
~~~

Mảnh code giả định `lock` và `ready` tồn tại và method xử lý `InterruptedException`. `wait()`:

1. Yêu cầu thread đang sở hữu monitor của `lock`.
2. **Nhả monitor `lock`** trong lúc đợi để thread khác có thể vào và thay đổi predicate.
3. Khi thức dậy, phải reacquire monitor rồi mới trả về từ `wait()`.
4. Có thể tỉnh dậy giả (*spurious wakeup*), nên **luôn** kiểm tra predicate trong vòng `while`, không chỉ `if`.
5. Không tự nhả những monitor khác mà thread đang giữ.

Thread thay đổi trạng thái phải dùng cùng monitor và thông báo theo protocol:

~~~java
synchronized (lock) {
    ready = true;
    lock.notifyAll();
}
~~~

Đây là mảnh minh họa. `notifyAll()` đánh thức các waiter để họ cạnh tranh reacquire monitor; không lập tức giao CPU hoặc làm các waiter vào critical section ngay. Nếu state chứa nhiều điều kiện phức tạp, cân nhắc `java.util.concurrent.locks.Condition` cùng `ReentrantLock`, hoặc dùng `BlockingQueue`/`CountDownLatch`/`Semaphore` nếu chúng khớp bài toán hơn.

Không thay toàn bộ `wait/notify` thành `sleep` rồi polling; nó vừa làm mất ý nghĩa predicate, vừa làm thời gian phản ứng khó kiểm soát.

## 14. Quan sát contention bằng thread dump và JVM tooling

Khi một ứng dụng Java có latency tăng, hãy kiểm tra liệu nhiều thread đang chờ cùng monitor không. Công cụ khả dụng tùy JDK/JVM và môi trường, nhưng tư duy quan sát là thống nhất:

### 14.1 Thread dump

Có thể tạo thread dump bằng công cụ JDK phù hợp, ví dụ trên HotSpot:

~~~bash
jcmd <pid> Thread.print -l
~~~

`<pid>` là **placeholder của PID tiến trình Java**, không phải một lệnh đầy đủ để chạy nguyên văn. Output tùy JDK nhưng thường cho thấy tên thread, trạng thái và vùng stack hiện tại. Tìm:

- Nhiều thread `BLOCKED` ở cùng method/monitor.
- Dòng cho biết thread đang “waiting to lock” monitor và object/owner tương ứng khi JVM xuất thông tin đó.
- Thread holder và stack của nó: có `Thread.sleep`, I/O, gọi phương thức ngoài, xử lý dữ liệu lớn hoặc chờ task khác trong lúc giữ lock không?
- Dấu hiệu vòng chờ giữa lock A/B, hoặc thread giữ lock rồi chờ completion của worker cần lock ấy.

Đừng kết luận chỉ từ **một** thread `BLOCKED` rằng có deadlock. Contention ngắn có thể là bình thường; cần xem số contender, độ dài, tần suất và khả năng tiến triển.

### 14.2 ThreadMXBean

`java.lang.management.ThreadMXBean` có thể hỗ trợ đọc trạng thái, locked monitors, deadlock và một số thống kê contention (phụ thuộc JVM và cấu hình). Ví dụ:

~~~java
ThreadMXBean bean = ManagementFactory.getThreadMXBean();
long[] deadlocked = bean.findDeadlockedThreads();
~~~

Đây là mảnh mã minh họa cần import `java.lang.management.ManagementFactory` và `java.lang.management.ThreadMXBean`. API trả về `null` khi không tìm thấy deadlock thuộc phạm vi có thể phát hiện; không có kết quả **không chứng minh** không có starvation, livelock, I/O wait hoặc mọi vòng phụ thuộc tác vụ.

`isThreadContentionMonitoringSupported()` cho biết JVM có hỗ trợ thống kê blocked/waited time hay không. Khi được hỗ trợ, có thể bật bằng `setThreadContentionMonitoringEnabled(true)` nếu có quyền và cần đo; việc thu thập có overhead và các số đo là dữ liệu runtime theo khả năng JVM, không phải SLA bảo đảm.

### 14.3 Java Flight Recorder

JFR có event phục vụ phân tích monitor contention, trong đó có `jdk.JavaMonitorEnter` trên những JDK hỗ trợ. Event có threshold/cấu hình thu thập, nên không phải mọi lần lấy monitor đều được ghi lại. Kiểm tra cấu hình recording, thời điểm thu thập, thread liên quan và stack trace để tìm critical section chậm.

JFR hữu ích để so sánh mức contention **trước/sau** một thay đổi code. Một số công cụ profiler còn đo thời gian giữ lock và thời gian đợi; cần phân biệt hai đại lượng này để tránh tối ưu sai chỗ.

## 15. Những điều cần đo khi cải thiện lock

Đừng đánh giá một bản sửa bằng một lần chạy “nhanh hơn”. Hãy đo theo workload có tranh chấp thực tế:

| Chỉ số | Giúp trả lời câu hỏi |
| --- | --- |
| **Blocked time / contention count** | Thread đợi monitor bao nhiêu lần và bao lâu? |
| **Critical-section hold time** | Holder giữ lock trong bao lâu? Có outlier bất thường? |
| **Throughput** | Hệ thống hoàn thành bao nhiêu operation mỗi giây dưới tải? |
| **p95/p99 latency** | Những request chậm nhất bị ảnh hưởng thế nào? |
| **Thread pool saturation** | Bao nhiêu worker bận chờ lock, queue backlog ra sao? |
| **CPU utilization / context switching** | Có nhiều thread tranh CPU hoặc overhead do contention không? |
| **Correctness invariants** | Bản tối ưu có giữ kết quả chính xác, không mất cập nhật không? |

Cùng một phép đo có thể phản ánh nhiều nguyên nhân. Ví dụ blocked time tăng có thể do hold time dài, số contender tăng hoặc một đường code mới phải dùng lock. Cần xem tương quan giữa nguyên nhân và tải, không chỉ quy kết cho từ khóa `synchronized`.

Nếu critical section rất ngắn và contention hiếm, giữ một lock đơn giản thường có lợi về tính rõ ràng. Tăng độ phức tạp bằng lock striping hoặc CAS không chắc cải thiện khi chi phí conflict/GC/retry lớn hơn.

## 16. Các hướng tối ưu nâng cao và giới hạn

### 16.1 Giảm phạm vi hoặc số lần cần lấy lock

Gộp các cập nhật mà về nghiệp vụ có thể batch để giảm số lần acquire, nhưng đừng giữ monitor lâu hơn chỉ để xử lý batch khổng lồ. Cân nhắc trade-off giữa kích thước batch, độ trễ của contender và invariant cần bảo vệ.

### 16.2 Tách state độc lập

Nếu hai nhóm dữ liệu **không cùng invariant**, có thể mỗi nhóm có lock riêng hoặc một object owner riêng. Nhưng nếu một báo cáo yêu cầu snapshot nhất quán trên cả hai nhóm, tách lock làm bài toán atomic snapshot khó hơn. Không nên chia lock theo từng field của cùng một invariant chỉ để bớt contention.

### 16.3 Immutable snapshot + atomic update

Nếu state có thể biểu diễn như một immutable value object, một `AtomicReference<State>` cho phép reader lấy **một reference snapshot** mà không phải giữ monitor. Writer có thể tạo state mới rồi commit bằng `compareAndSet`. Đây là cách khác để giữ invariant đa trường, nhưng CAS có thể retry nhiều lần dưới tranh chấp, tạo object mới và đòi hỏi tránh side effect không idempotent trong phần thử lại.

### 16.4 Cấu trúc dữ liệu concurrent tiêu chuẩn

`ConcurrentHashMap`, `BlockingQueue`, `LongAdder` và các primitive trong `java.util.concurrent` có những hợp đồng riêng, phù hợp nhiều workload. Ví dụ `LongAdder` hữu ích cho thống kê throughput cao khi nhiều writer cùng cộng, nhưng `sum()` trong lúc update đồng thời **không phải atomic snapshot** của mọi cell; không dùng nó để bảo vệ invariant thanh toán đòi con số chính xác ngay tại mọi thời điểm.

Không có primitive “tốt nhất” cho mọi bài toán. Nếu throughput tăng nhưng xuất hiện inconsistency hoặc bỏ mất tính chất snapshot, tối ưu đã làm đổi hợp đồng nghiệp vụ.

### 16.5 Virtual threads và monitor contention

Trong Java 21+, virtual threads có thể giúp một lượng lớn tác vụ chờ I/O mà không cần gắn một platform thread cố định cho từng task. Tuy nhiên **virtual thread không biến một monitor thành nhiều monitor**: nhiều virtual threads tranh cùng `synchronized(lock)` vẫn phải tuần tự trong critical section, nên lock contention và hold time dài vẫn là nút thắt logic.

Những chi tiết triển khai scheduler, pinning và tối ưu liên quan `synchronized` đã thay đổi giữa các phiên bản JDK. Khi đánh giá hiệu năng virtual threads, cần kiểm tra **phiên bản JDK cụ thể và profiler thực tế**, không áp dụng một kết luận về pinning của một JDK cho mọi phiên bản.

## 17. Các lỗi thường gặp

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Giữ `synchronized` khi gọi HTTP/database/file I/O. | Các contender cùng monitor chờ theo thời gian I/O. | Prepare/snapshot dưới lock, I/O ngoài lock, validate/commit lại đúng protocol. |
| `Thread.sleep()` trong critical section. | Thread ngủ nhưng vẫn giữ monitor. | Di chuyển sleep/chờ ra ngoài lock nếu có thể. |
| `CountDownLatch.await()` hoặc `Future.get()` khi giữ monitor worker cần. | Lock convoy hoặc deadlock phụ thuộc task. | Đợi ở ngoài vùng lock; xem lại đồ thị phụ thuộc. |
| Callback dưới monitor và callback lấy lock khác. | Lock-order inversion, deadlock, độ trễ khó đoán. | Gọi callback sau lock với snapshot/ordering phù hợp. |
| Dùng hai monitor khác nhau cho một invariant. | Mutual exclusion/visibility không áp dụng cho toàn trạng thái. | Một owner lock hoặc giao thức đa lock đã chứng minh đúng. |
| Chỉ synchronized writer mà getter không theo lock. | Reader có thể nhìn trạng thái trung gian/thiếu HB. | Đọc snapshot dưới cùng protocol. |
| Chia check và update ra hai critical section. | Thread khác chen vào; quota oversubscription/lost update. | Check lại và commit trong cùng critical section. |
| Nghĩ `Thread.interrupt()` hủy ngay thread `BLOCKED` chờ monitor. | Cancellation không có hiệu lực kỳ vọng. | Dùng primitive hỗ trợ interruptible/timed acquisition nếu cần. |
| Xem mọi thread `WAITING` là monitor contention. | Chẩn đoán sai do latch, future, park, I/O. | Phân biệt `BLOCKED` với trạng thái chờ khác; xem stack/owner. |
| Coi fairness của intrinsic monitor là FIFO. | Một contender có thể bị chờ lâu hơn dự kiến. | Thiết kế policy fairness bằng primitive phù hợp nếu có yêu cầu. |
| Thêm lock mới mà không đo tải và kiểm invariant. | Có thể giảm throughput hoặc tạo deadlock mới. | Đo latency/contention, test correctness, review lock ordering. |

## 18. Checklist khi phát hiện monitor contended

1. **Xác định lock identity.** Những thread đang tranh có cùng monitor thật không? Là `this`, `Class` hay private object?
2. **Xác định holder.** Thread nào sở hữu lock? Stack holder đang thực hiện gì?
3. **Đo hold time và blocked time.** Contention do hoạt động thường xuyên hay có một nhánh chậm bất thường?
4. **Tìm blocking operation trong critical section.** Có I/O, sleep, join, Future, latch, callback hoặc nested lock không?
5. **Viết invariant.** Những trường/operation nào thực sự phải đi cùng monitor? Reader có cần snapshot?
6. **Đề xuất phạm vi tối thiểu.** Đưa công việc local/immutable ra ngoài, giữ check + commit state trong lock.
7. **Xem lại exception và cancellation.** Thao tác bị lỗi có bỏ dở state hoặc giữ thread chờ vô hạn không?
8. **Xem lại lock ordering.** Các call path khác có lấy lock theo thứ tự ngược hoặc gọi callback giữ thêm lock không?
9. **Kiểm chứng** bằng test concurrency và profiling trước/sau dưới cùng tải.
10. **Chỉ thay primitive khi có lý do.** `ReentrantLock`, atomic state hoặc concurrent collection thay đổi hợp đồng, cần review lại correctness.

## 19. Câu hỏi tự kiểm tra

**1.** Một thread đang `BLOCKED` chờ `synchronized(lock)` thường bị chặn ở bước nào?  
**Đáp:** Ở bước lấy intrinsic monitor đang do thread khác sở hữu.

**2.** Thread holder gọi `Thread.sleep(500)` trong `synchronized(lock)` có nhả monitor không?  
**Đáp:** Không. `sleep` tạm dừng thread, nhưng lock vẫn do nó sở hữu.

**3.** `Object.wait()` trên object lock nhả những lock nào?  
**Đáp:** Nhả monitor của chính object được wait, không tự nhả các monitor khác đang sở hữu; khi trở lại từ wait, phải lấy lại monitor đó.

**4.** Hai method đều `synchronized` có chắc tranh cùng monitor không?  
**Đáp:** Không. Instance synchronized khóa `this`; static synchronized khóa `Class` object; hai instance khác nhau có monitor khác nhau.

**5.** Vì sao main giữ lock rồi `join()` worker cần cùng lock có thể deadlock?  
**Đáp:** Main chờ worker kết thúc nhưng worker không thể lấy lock do main giữ; join không tự giải phóng monitor của main.

**6.** `interrupt()` một contender chờ intrinsic monitor có bảo đảm nó bỏ chờ ngay không?  
**Đáp:** Không. Chờ lấy intrinsic monitor không có cùng semantics interruptible acquisition của `ReentrantLock.lockInterruptibly()`.

**7.** Nếu chuyển check quota ra ngoài synchronized còn update vẫn trong lock thì có an toàn không?  
**Đáp:** Không nếu không kiểm tra lại dưới lock; một thread khác có thể thay state giữa check và update.

**8.** Vì sao một `ReentrantLock` không tự xử lý contention dài?  
**Đáp:** Vì thời gian giữ lock vẫn phụ thuộc code bên trong. ReentrantLock thêm các cơ chế lấy khóa có timeout/interruption, không tự giảm hold time.

**9.** Thread dump thấy 20 thread `BLOCKED` có chứng minh deadlock không?  
**Đáp:** Chưa. Cần xem lock owner, đường chạy của holder, tiến triển theo thời gian và vòng phụ thuộc.

**10.** Một thay đổi tối ưu critical section cần đạt những gì?  
**Đáp:** Giữ invariant, HB/visibility và atomicity như cũ, đồng thời cải thiện blocked time/latency/throughput được đo trên workload phù hợp.

**Ghi nhớ:** Monitor contention xuất hiện khi nhiều thread cùng muốn một intrinsic lock. **Giữ khóa ngắn, bảo vệ đúng invariant và không chờ việc ngoài trong lúc giữ khóa** là những nguyên tắc thiết kế chính. Khi cần timeout/cancellation ở bước lấy khóa, dùng primitive có hợp đồng phù hợp; khi điều tra ứng dụng chậm, phân biệt trạng thái `BLOCKED`, `WAITING` và tìm chính xác holder cùng thời gian giữ monitor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-012">Vấn đề khi gọi callback hoặc blocking I/O trong synchronized</a>

<details>
<summary>Xem chi tiết</summary>

# Vấn đề khi gọi callback hoặc blocking I/O trong `synchronized`

Trong Java, `synchronized` bảo vệ một **critical section** bằng intrinsic monitor lock. Khi một thread giữ monitor, thread khác cần cùng monitor phải chờ. Đây là cách hữu ích để bảo vệ invariant của trạng thái dùng chung. Nhưng nếu code **gọi callback do bên ngoài cung cấp** hoặc **thực hiện thao tác có thể block** ngay trong critical section, thời gian giữ monitor trở nên khó dự đoán. Hậu quả có thể là latency tăng, lock contention, deadlock, nghẽn tài nguyên và lỗi vòng đời khó truy tìm.

Một nguyên tắc thiết kế quan trọng:

> **Giữ vùng `synchronized` ngắn và chỉ bảo vệ những thao tác trạng thái thật sự cần atomic. Không gọi mã người dùng không kiểm soát hoặc chờ I/O kéo dài khi đang giữ monitor, trừ khi đã chứng minh rõ hợp đồng và lý do.**

Bài này thuộc phần **Synchronization** của **Java concurrency nói chung**. Ví dụ dùng Java SE 17+ (`Thread`, monitor, `java.util.concurrent`, callback và các thao tác blocking thông dụng), không sử dụng cơ chế đồng bộ riêng của Spring Framework.

## 1. `synchronized` khóa gì và khóa trong bao lâu?

Ví dụ:

```java
synchronized (monitor) {
    updateSharedState();
    slowOperation();
}
```

Đây là đoạn minh họa, `monitor` và các phương thức chưa được khai báo. Thread vào block phải lấy **monitor của đúng đối tượng `monitor`**. Monitor được giữ từ lúc vào cho đến khi rời block, kể cả khi có `return` hoặc exception làm thoát block. JVM bảo đảm nhả intrinsic lock khi luồng điều khiển rời vùng `synchronized`.

Ba cách viết cần phân biệt:

| Cú pháp | Monitor được giữ |
| --- | --- |
| `synchronized (lock) { ... }` | Chính đối tượng `lock` |
| `synchronized void method() { ... }` | `this` của instance |
| `static synchronized void method() { ... }` | Đối tượng `Class` tương ứng |

Các monitor khác nhau **không tự loại trừ nhau**. Nếu hai phương thức bảo vệ cùng một invariant nhưng khóa hai đối tượng khác nhau, chúng chưa có mutual exclusion đúng.

Ngoài mutual exclusion, `synchronized` còn có ý nghĩa của Java Memory Model: **unlock trên một monitor happens-before lần lock tiếp theo trên cùng monitor**. Các ghi trước unlock được công bố phù hợp cho thread đã lock lại, với điều kiện các đường truy cập cũng theo giao thức bảo vệ.

**`synchronized` không tự biết thao tác nào sẽ chậm.** Nếu code trong vùng khóa ngủ 10 giây, gọi mạng 30 giây hoặc đợi callback không quay về, lock tiếp tục bị giữ trong thời gian đó. Thời gian chờ lock của các thread khác tăng theo thời gian chủ lock chưa rời vùng khóa.

## 2. Vì sao callback là ranh giới đặc biệt nguy hiểm?

**Callback** là mã được gọi lại thông qua một đối tượng, interface, lambda hoặc listener mà logic của nó có thể nằm ngoài quyền kiểm soát của class hiện tại.

Ví dụ anti-pattern:

```java
final class UnsafeNotifier {
    private final java.util.List<java.util.function.Consumer<String>>
            listeners = new java.util.ArrayList<>();

    public synchronized void addListener(
            java.util.function.Consumer<String> listener) {
        listeners.add(listener);
    }

    public synchronized void publish(String event) {
        for (var listener : listeners) {
            listener.accept(event); // Gọi mã không kiểm soát dưới monitor this.
        }
    }
}
```

Class trên là ví dụ minh họa, không có `main`. Nó có thể đồng bộ truy cập tới `listeners` nhưng **đang giữ monitor của notifier trong khi chạy `listener.accept(event)`**. Listener do client đăng ký có thể:

- Thực hiện logging hoặc I/O đồng bộ rất chậm.
- Chờ `Future.get()`, `Thread.join()`, `CountDownLatch.await()` hoặc một task khác.
- Gọi lại `publish` hoặc `addListener` (reentrancy).
- Gọi một component khác đang giữ lock của chính nó, tạo nguy cơ **lock-order inversion**.
- Đăng ký hoặc hủy đăng ký listener khi đang duyệt.
- Ném unchecked exception và dừng lượt dispatch giữa chừng.
- Gọi một API đã bị đóng hoặc bắt đầu thao tác `shutdown` làm phát sinh vòng chờ.

Vì vậy, lời hứa "thread-safe notifier" chỉ đúng nếu class còn xử lý **callback policy**, **lifecycle** và **giao thức đồng bộ** của mọi nhánh. Khóa bảo vệ cấu trúc `listeners` không đồng nghĩa callback an toàn.

## 3. Rủi ro 1: lock contention và convoy

Hãy xét chuỗi:

```text
Thread A                       Thread B                  Thread C
--------                       --------                  --------
lock notifier
update list
gọi listener chậm
    |
    |                         gọi addListener()
    |                         -> chờ monitor
    |                                                     gọi snapshot()
    |                                                     -> chờ monitor
    |
listener trả về
unlock notifier
                              lấy được monitor ...
```

Trong thời gian listener chậm, thread B và C không thể vào các vùng `synchronized` dùng cùng monitor. Chúng có thể ở trạng thái `BLOCKED` khi chờ intrinsic monitor; nếu có nhiều caller, thời gian chờ bị dồn lại, tạo **lock convoy** hoặc contention kéo dài.

Điều này không đồng nghĩa JVM bị deadlock. Nếu callback trả về, hệ thống có thể tiếp tục. Nhưng **độ trễ của những lời gọi không liên quan trực tiếp đến callback** vẫn bị kéo dài do bị buộc xếp hàng sau callback đó.

Ví dụ nếu `getStatus()` chỉ đọc một số nguyên và được khai báo `synchronized` trên cùng object, nó cũng có thể bị chặn bởi listener đang làm HTTP request mặc dù thao tác đọc chỉ cần rất ít thời gian.

**Mức độ nghiêm trọng phụ thuộc hợp đồng:** trong công cụ nội bộ đơn giản, callback cực ngắn và được kiểm soát, giữ lock có thể chấp nhận được; trong public API nhận callback tùy ý, đây là giả định rủi ro lớn và cần tránh.

## 4. Rủi ro 2: callback đợi thread khác cần chính monitor — deadlock

Một ví dụ tự tạo vòng chờ:

```text
Thread A (giữ monitor M)
  -> gọi callback dưới lock M
     -> callback submit task T rồi đợi T hoàn tất

Thread B (thực thi task T)
  -> cần lock M để gọi một method synchronized
  -> bị BLOCKED vì A đang giữ M

A đợi B; B đợi A nhả M => deadlock.
```

Đoạn Java sau chỉ để phân tích **anti-pattern**, tuyệt đối không chạy nó như một test tự động không có timeout:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;

final class DeadlockIllustration {
    private int value;

    synchronized int readValue() {
        return value;
    }

    synchronized void wrongWait(ExecutorService pool)
            throws InterruptedException, ExecutionException {
        // Caller giữ monitor của this.
        pool.submit(this::readValue).get();
        // Worker cần chính monitor đó trước khi Future hoàn tất.
    }
}
```

Nếu executor thực thi task trên **thread khác**, và task này cố lấy monitor trước khi hoàn tất, thread caller `wrongWait` giữ monitor trong lúc đợi future: vòng phụ thuộc không thể tự giải quyết. Nếu executor có chính sách chạy task inline trên caller thread, hành vi có thể khác. Điểm cần rút ra là **đừng chờ một công việc có thể cần lock mình đang giữ**.

### Nhưng intrinsic lock là reentrant, có phải callback gọi ngược luôn deadlock?

**Không.** Java intrinsic monitor có tính **reentrant**: cùng một thread đang giữ monitor có thể vào lại phương thức `synchronized` trên **chính monitor đó**. Ví dụ một listener chạy ngay trong thread A và gọi `notifier.addListener(...)` có thể lấy lại monitor `notifier` mà không tự deadlock do monitor đó.

Tuy nhiên, reentrancy vẫn có vấn đề:

- Callback có thể thay đổi state giữa một operation tưởng chừng đang thực thi atomic ở lớp gọi.
- Listener có thể thêm/xóa phần tử khi đang duyệt `ArrayList` bằng iterator, gây `ConcurrentModificationException` hoặc phá giả định về iteration.
- Callback có thể gọi lại `publish` đệ quy, dẫn đến dispatch lồng nhau, thứ tự event khó hiểu hoặc `StackOverflowError`.
- Callback có thể lấy **một lock khác** rồi tạo vòng chờ với thread khác, cho dù monitor ban đầu là reentrant.

Phân biệt chính xác: **gọi lại trên cùng monitor từ cùng thread không tự deadlock**; **chờ thread khác hoặc đảo thứ tự nhiều lock** có thể deadlock.

## 5. Rủi ro 3: lock-order inversion giữa hai component

Một deadlock phổ biến không cần `Future.get()`:

```text
Thread A: lock ServiceA -> callback gọi ServiceB -> chờ lock ServiceB
Thread B: lock ServiceB -> callback gọi ServiceA -> chờ lock ServiceA
```

Ở thời điểm này:

- A giữ khóa của ServiceA và chờ khóa của ServiceB.
- B giữ khóa của ServiceB và chờ khóa của ServiceA.

Không thread nào có thể tiến, dù callback ban đầu chỉ định "thông báo thay đổi". Khi mỗi class tự quản lý lock, nguy cơ càng khó phát hiện: không một class nào có thể nhìn thấy toàn bộ **thứ tự khóa** của các thành phần khác.

Giải pháp nền tảng là **không gọi callback bên ngoài khi đang giữ lock nội bộ**. Trong những đoạn thật sự cần lấy nhiều lock, hãy định nghĩa **thứ tự lock nhất quán** và tránh chạy mã không kiểm soát khi giữ chúng.

Với `ReentrantLock`, `tryLock(timeout, unit)` có thể giúp một số thiết kế tránh chờ vô hạn; nó **không tự loại bỏ deadlock** nếu logic lock acquisition và tài nguyên phụ thuộc chéo vẫn sai.

## 6. Rủi ro 4: exception, partial work và invariant

Khi callback ném exception, intrinsic monitor sẽ được JVM nhả khi phương thức thoát bất thường. Nhưng **nhả lock không đồng nghĩa rollback mọi thay đổi**:

```java
synchronized (lock) {
    status = Status.UPDATING;
    listener.onUpdate(); // Ném RuntimeException.
    status = Status.READY; // Bị bỏ qua.
}
```

Đây là đoạn minh họa với `lock`, `status`, `Status` và `listener` giả định. Nếu callback ném unchecked exception, trạng thái có thể nằm lại ở `UPDATING`. Các caller sau đó lấy được monitor nhưng có thể gặp invariant hoặc lifecycle không như thiết kế.

Cần quyết định **thứ tự thay đổi state và phát callback**:

1. Cập nhật state hợp lệ trong lock.
2. Tạo dữ liệu event hoặc immutable snapshot.
3. Nhả lock.
4. Gọi callback bên ngoài lock.
5. Xử lý exception của callback theo chính sách: propagate, ghi nhận rồi tiếp tục, retry hoặc thông báo kết quả từng listener.

Không có chính sách duy nhất đúng với mọi API. Nhưng với public API, **callback exception behavior** phải được document: nếu listener A ném lỗi, listener B có còn được gọi không? Thay đổi state trước callback có giữ nguyên không? Có phát lại event không?

### Tính atomic của state khác với "all-or-nothing delivery"

Cập nhật một danh sách dưới lock có thể là một thay đổi atomic đối với state nội bộ. Nhưng nếu event được gửi cho A, rồi B ném exception trước khi C nhận event, việc giao thông báo đã **thành công một phần**. Đừng hứa "exactly once to all listeners" nếu không có giao thức delivery và lỗi phù hợp.

Nếu cần giao dịch bao gồm external system, khóa trong bộ nhớ JVM cũng **không tạo transaction** bao trùm HTTP request hoặc ghi database.

## 7. Rủi ro 5: callback có thể làm hỏng iterator

Ví dụ:

```java
synchronized (this) {
    for (var listener : listeners) {
        listener.onEvent(event);
    }
}
```

Nếu listener được gọi trên cùng thread và đăng ký một listener mới thông qua phương thức `synchronized` của cùng notifier, monitor reentrant không chặn callback. Trong lúc iterator của `ArrayList` đang hoạt động, sửa đổi cấu trúc list qua `add` có thể dẫn đến `ConcurrentModificationException` khi iterator kiểm tra lại.

**Tính reentrant của lock không biến `ArrayList` iterator thành ổn định trước sửa đổi cấu trúc.** Một giải pháp phổ biến là **chụp danh sách listener dưới lock** rồi duyệt bản chụp bên ngoài lock.

Cần định nghĩa snapshot semantics:

- Listener đăng ký **sau khi snapshot được tạo** sẽ không nằm trong lượt dispatch hiện tại.
- Listener bị xóa **sau khi snapshot được tạo** vẫn có thể nhận event trong lượt dispatch đang diễn ra.
- Hai `publish` có thể được gọi đồng thời, dẫn đến hai callback trên **cùng listener object** chạy cùng lúc nếu không có cơ chế serialization khác.
- Thứ tự callback trong một lượt có thể là thứ tự trong snapshot; không hứa thứ tự toàn cục giữa các lượt publish concurrent.

## 8. Mẫu an toàn: snapshot dưới lock, callback bên ngoài lock

Lưu thành `CallbackOutsideLockDemo.java`, chạy với JDK 17+:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class CallbackOutsideLockDemo {
    static final class Notifier {
        private final List<Consumer<String>> listeners =
                new ArrayList<>();

        public synchronized void subscribe(Consumer<String> listener) {
            listeners.add(Objects.requireNonNull(listener));
        }

        public synchronized int listenerCount() {
            return listeners.size();
        }

        public void publish(String event) {
            List<Consumer<String>> snapshot;
            synchronized (this) {
                snapshot = List.copyOf(listeners);
            }

            // Không giữ monitor của Notifier ở đây.
            for (Consumer<String> listener : snapshot) {
                listener.accept(event);
            }
        }
    }

    public static void main(String[] args) {
        Notifier notifier = new Notifier();

        notifier.subscribe(event -> {
            System.out.println("Under notifier lock: "
                    + Thread.holdsLock(notifier));
            System.out.println("Listener count: "
                    + notifier.listenerCount());
        });

        notifier.subscribe(event ->
                System.out.println("Second listener: " + event));

        notifier.publish("READY");
    }
}
```

Biên dịch và chạy:

```bash
javac CallbackOutsideLockDemo.java
java CallbackOutsideLockDemo
```

Stdout chính xác:

```text
Under notifier lock: false
Listener count: 2
Second listener: READY
```

**Giải thích chi tiết:**

- `subscribe` và `listenerCount` cùng dùng monitor của `Notifier`, nên truy cập list được bảo vệ.
- `publish` chỉ giữ monitor trong lúc `List.copyOf(listeners)` tạo snapshot. Sau block đó, callback chạy **không giữ monitor của notifier**.
- `Thread.holdsLock(notifier)` cho biết **thread hiện tại** đang sở hữu monitor của đối tượng đó hay không tại thời điểm kiểm tra. Dòng `false` minh họa đúng thiết kế, không đo các lock khác mà callback có thể tự lấy.
- Callback được gọi ngay trên **caller thread** của `publish`. Code không tự chuyển callback sang background hoặc tự làm chúng serial giữa hai lượt publish đồng thời.
- `List.copyOf` chụp danh sách reference listener; nó không biến object listener mutable thành thread-safe. Nếu cùng listener được gọi đồng thời bởi hai caller, logic bên trong listener cần tự đảm bảo an toàn.

Ở đây callback nhận `String` immutable. Nếu event là một mutable object, phải thiết kế thêm **ownership/safe publication** để listener không đọc object đang bị thread khác sửa.

### Lựa chọn thay thế: `CopyOnWriteArrayList`

Đối với tập listener nhỏ, **đọc và phát event rất thường xuyên nhưng đăng ký/hủy hiếm**, `CopyOnWriteArrayList<Listener>` có thể phù hợp:

- Thao tác ghi danh sách được cấu trúc tự đồng bộ.
- Iterator có snapshot semantics; có thể duyệt mà không tự giữ monitor registry.
- Chi phí sửa danh sách thường cao hơn vì phải copy cấu trúc mảng; không phù hợp mọi workload.
- Callback vẫn có thể chậm, ném exception hoặc chạy đồng thời trên cùng listener, nên không phải "lá chắn" cho callback logic.

Đổi từ `ArrayList` có lock sang `CopyOnWriteArrayList` chỉ giải quyết **cấu trúc lưu danh sách**, không giải quyết toàn bộ giao thức callback.

## 9. Callback nằm ngoài lock vẫn cần xác định điểm commit

Vì callback chạy sau khi nhả lock, state có thể tiếp tục được update trước hoặc trong lúc listener xử lý event.

Ví dụ:

```text
Thread A                             Thread B
--------                             --------
lock
  state = VERSION_1
  snapshot event VERSION_1
unlock
                                     lock
                                       state = VERSION_2
                                     unlock
gọi listener với VERSION_1
```

Khi callback nhận event `VERSION_1`, state hiện tại của service có thể đã là `VERSION_2`. Điều này **không phải lỗi** nếu API định nghĩa event là một immutable snapshot của update đã xảy ra, không phải lời hứa callback luôn nhìn thấy trạng thái mới nhất.

Nếu callback **đọc lại live state**, có thể thấy phiên bản mới hơn event. Tài liệu nên nói rõ:

- Event mang dữ liệu snapshot, version hay ID nào?
- Callback có được xem state mới nhất bằng lời gọi khác không, và hai giá trị có nhất thiết đồng nhất?
- Có bảo đảm **thứ tự callback** bằng thứ tự commit toàn cục không?
- Hai caller cùng cập nhật state có thể phát callback song song không?
- Listener nào có thể xuất hiện/bị xóa khi dispatch đang diễn ra?

**Chụp ngoài khóa không thay thế ordering policy.** Nếu cần dispatch tuần tự đúng theo thứ tự commit, dùng một queue được quản lý cùng giao thức bảo vệ khi enqueue, rồi một worker tiêu thụ event theo thứ tự xác định. Nhưng phải tính đến backlog, queue đầy, worker chết và shutdown, không chỉ vấn đề lock.

## 10. Blocking I/O trong `synchronized` nguy hiểm ở đâu?

Một thao tác **blocking I/O** có thể đợi dữ liệu từ mạng, file, socket, pipe, stream, database driver hoặc tài nguyên khác. Thời gian chờ phụ thuộc bên ngoài JVM và có thể lâu hoặc không có giới hạn nếu không thiết lập timeout/cancellation phù hợp.

Ví dụ anti-pattern:

```java
synchronized (lock) {
    updateMetadata();
    outputStream.write(payload);
    outputStream.flush();
}
```

Đây là đoạn minh họa với các biến/phương thức giả định. `write` hoặc `flush` có thể chờ bên nhận, buffer hoặc thiết bị; trong lúc đó **monitor `lock` vẫn được giữ**. Các thread muốn truy cập metadata qua cùng monitor phải chờ, dù việc đọc metadata không liên quan đến mạng.

Các tác hại thường gặp:

1. **Lock hold time** khó dự đoán vì phụ thuộc I/O latency.
2. **Throughput giảm** do các lời gọi cần cùng monitor bị xếp hàng phía sau I/O.
3. **Tail latency** tăng mạnh khi một lần I/O chậm bất thường.
4. **Lock inversion** nếu I/O gián tiếp kích hoạt callback hoặc thread khác cần monitor ban đầu.
5. **Shutdown/cancellation khó** khi thread giữ lock mắc kẹt trong một lời gọi không đáp ứng interruption.
6. **Inconsistent business state** nếu cập nhật state trước I/O rồi I/O thất bại, nhưng caller tưởng hai việc đã cùng commit.

Lưu ý: **không phải mọi I/O đều block trong mọi lần gọi**. Vấn đề là API **có thể block** và thời gian chờ không được critical section giới hạn. Một lần benchmark nhanh không chứng minh an toàn trên mọi tải.

## 11. Những lời gọi chờ nào nhả lock, lời gọi nào không?

Rất dễ nhầm từ "waiting" trong tiếng Anh với việc "JVM tự nhả monitor".

| Thao tác gọi khi đang giữ monitor `M` | Có tự nhả monitor `M` khi chờ? | Ghi chú |
| --- | --- | --- |
| `Thread.sleep(...)` | **Không** | Thread ngủ nhưng vẫn giữ các monitor đang sở hữu |
| `Thread.join()` | **Không tự nhả monitor M** | Có thể chờ target thread; nếu target cần M có nguy cơ deadlock |
| `Future.get()` | **Không** | Có thể chờ task khác cần chính monitor |
| `CountDownLatch.await()` | **Không** | Đợi latch; latch countDown có thể phụ thuộc thread cần M |
| `BlockingQueue.take()/put()` | **Không** | Queue tự quản lý đồng bộ của nó nhưng không nhả monitor M do caller giữ |
| Blocking socket/file I/O | **Không** | I/O không tự unlock monitor do caller giữ |
| `Object.wait()` gọi trên **M** khi giữ monitor M | **Có: nhả monitor M trong lúc wait** | Lấy lại monitor M trước khi `wait()` trả về; dùng vòng `while` kiểm tra điều kiện |
| `Condition.await()` dưới `ReentrantLock L` | Nhả và lấy lại **lock L** theo hợp đồng Condition | Không nhả các monitor/lock **khác** đang giữ |

Một nuance quan trọng: nếu gọi `otherObject.wait()`, thread phải sở hữu monitor của **otherObject**; lời gọi đó nhả monitor của otherObject, **không có nghĩa** nó nhả mọi monitor đang giữ, kể cả monitor M của service. Vì vậy "dùng `wait` trong synchronized là ổn" chỉ đúng khi thiết kế chờ trên **đúng monitor và đúng predicate**, không giữ các khóa phụ khác gây vòng chờ.

Tương tự, `Condition.await()` có cơ chế atomically release/reacquire **lock gắn với Condition**, không phải công cụ giải phóng toàn bộ lock trên stack.

## 12. Race giữa I/O bên ngoài và cập nhật state sau khi mở khóa

Giải pháp "cứ đưa network call ra ngoài lock" là điểm khởi đầu nhưng **chưa đủ** khi I/O lấy dữ liệu từ state và kết quả I/O quyết định bước cập nhật tiếp theo.

Mẫu ba pha:

```text
Pha 1 — dưới lock:
  đọc state và tạo immutable snapshot / operation ID / version
  hoặc đặt trạng thái "đang xử lý" theo invariant

Pha 2 — ngoài lock:
  thực hiện I/O hoặc callback có thể chậm
  kiểm tra timeout, cancellation, lỗi

Pha 3 — dưới lock:
  kiểm tra version/ownership/invariant
  commit kết quả nếu còn hợp lệ
  nếu stale: từ chối, retry có giới hạn hoặc xử lý conflict
```

Hai loại thiết kế phổ biến:

- **Snapshot + validate:** nếu state có thể đổi khi I/O đang diễn ra, ghi thêm version/token và chỉ commit nếu version vẫn đúng.
- **Reservation/state machine:** nếu nghiệp vụ cần giữ quyền sử dụng tài nguyên trong khi I/O, tạo reservation hoặc state chuyển tiếp trong lock trước, nhả lock cho I/O, rồi commit/abort qua operation được đồng bộ. Đảm bảo timeout và rollback/resolution khi I/O thất bại.

Đừng giữ monitor chỉ vì "muốn I/O và cập nhật là một thao tác atomic". Khóa monitor JVM **không biến I/O ngoài process thành transaction**, và việc giữ lock lâu chỉ khuếch đại contention. Nếu external side effect đã xảy ra rồi commit nội bộ thất bại, cần cơ chế idempotency/compensation hoặc giao thức giao dịch phù hợp; không thể chỉ dựa vào `synchronized`.

## 13. Ví dụ Java 17+: queue `put` có thể chờ nhưng không giữ monitor của state

`BlockingQueue.put` là một thao tác **blocking coordination** dễ kiểm chứng để minh họa nguyên tắc giống I/O có thể block: producer đợi queue còn chỗ. Nó không phải socket I/O, nhưng cho thấy cùng lỗi thiết kế sẽ xảy ra nếu code giữ monitor trong lúc chờ.

Lưu thành `QueueOutsideLockDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class QueueOutsideLockDemo {
    static final class State {
        private int value = 10;

        public synchronized void increment() {
            value++;
        }

        public synchronized int get() {
            return value;
        }

        public void send(
                BlockingQueue<Integer> queue,
                CountDownLatch snapshotTaken)
                throws InterruptedException {
            int snapshot;
            synchronized (this) {
                snapshot = value;
            }

            // Đã nhả monitor trước thao tác queue.put có thể block.
            snapshotTaken.countDown();
            queue.put(snapshot);
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        State state = new State();
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
        CountDownLatch snapshotTaken = new CountDownLatch(1);

        queue.put(-1); // Làm queue đầy trước khi worker gửi.

        Thread sender = new Thread(() -> {
            try {
                state.send(queue, snapshotTaken);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        sender.start();
        snapshotTaken.await();

        // State vẫn truy cập được, dù sender có thể đang đợi queue.
        state.increment();
        System.out.println("State now: " + state.get());

        System.out.println("Removed: " + queue.take());
        sender.join();
        System.out.println("Sent: " + queue.take());
    }
}
```

Biên dịch/chạy:

```bash
javac QueueOutsideLockDemo.java
java QueueOutsideLockDemo
```

Stdout chính xác trong lần chạy bình thường:

```text
State now: 11
Removed: -1
Sent: 10
```

**Điểm cần quan sát:**

- Worker chụp `value == 10` dưới monitor và nhả monitor trước `snapshotTaken.countDown()`.
- Main chỉ cập nhật sau khi nhận signal snapshot đã chụp; do vậy giá trị state mới là 11, trong khi message được gửi vẫn là snapshot 10.
- Queue ban đầu đã đầy. `queue.put(snapshot)` **có thể phải chờ** đến khi main lấy `-1` ra. Không có yêu cầu worker nhất định đã gọi `put` tại thời điểm main in dòng đầu; kết quả vẫn xác định nhờ latch và snapshot.
- Nếu `send` giữ monitor suốt `queue.put`, main có thể bị chặn trong `increment()` trong khi chính main là thread duy nhất sau đó lấy item khỏi queue: một vòng chờ khó tiến triển sẽ xuất hiện.
- `CountDownLatch.await` ở main là điều phối hợp lệ; worker nhả monitor trước tín hiệu. `Thread.sleep` không thể thay thế việc chứng minh snapshot đã được tạo.

Cùng nguyên tắc áp dụng khi gửi dữ liệu qua socket hoặc ghi file: **chụp payload hợp lệ dưới lock, gửi ngoài lock**, rồi xử lý kết quả bằng một pha commit riêng nếu nghiệp vụ yêu cầu.

## 14. Snapshot có thể cũ: kiểm tra version trước khi commit

Một response từ bên ngoài có thể quay về sau khi state đã bị thread khác thay đổi. Nếu cứ ghi response xuống state mà không kiểm tra, có thể ghi đè dữ liệu mới bằng thông tin cũ.

Ví dụ Java 17+ sau tập trung vào **logic validate/commit**, không tạo network call. Phần xử lý bên ngoài được mô phỏng bằng một giá trị cố định để stdout không phụ thuộc mạng.

Lưu thành `VersionedCommitDemo.java`:

```java
public class VersionedCommitDemo {
    private record Snapshot(long value, long version) {}

    private static final class State {
        private long value = 10;
        private long version;

        synchronized Snapshot snapshot() {
            return new Snapshot(value, version);
        }

        synchronized void incrementLocally() {
            value++;
            version++;
        }

        synchronized boolean commitIfUnchanged(
                Snapshot expected, long externalResult) {
            if (version != expected.version()) {
                return false;
            }
            value = externalResult;
            version++;
            return true;
        }

        synchronized long get() {
            return value;
        }
    }

    public static void main(String[] args) {
        State state = new State();

        Snapshot beforeExternalWork = state.snapshot();

        // Một cập nhật khác xuất hiện khi pha xử lý ngoài lock diễn ra.
        state.incrementLocally();

        // Cố gắng commit kết quả đã tính trên snapshot cũ.
        boolean accepted = state.commitIfUnchanged(
                beforeExternalWork, 99);

        System.out.println("Accepted stale result: " + accepted);
        System.out.println("Current value: " + state.get());
    }
}
```

```bash
javac VersionedCommitDemo.java
java VersionedCommitDemo
```

Stdout chính xác:

```text
Accepted stale result: false
Current value: 11
```

Đây là **optimistic concurrency control** ở mức state trong process: chỉ commit nếu version chưa đổi. Phương thức `commitIfUnchanged` giữ monitor trong cả bước kiểm tra và cập nhật nên không có khoảng xen kẽ giữa hai thao tác.

Các giới hạn:

- Version phải tăng khi **mọi thay đổi liên quan tới invariant** xảy ra; nếu bỏ sót một mutation, kiểm tra version có thể sai.
- Trong một hệ thống dài hạn cần tính đến version overflow và cách quản lý identity/generation; không dùng một version nhỏ dễ tái sử dụng nếu điều đó tạo vấn đề ABA.
- Nếu external request đã tạo side effect không thể hoàn tác, việc bỏ commit nội bộ không tự rollback side effect ngoài JVM.
- Nếu cần retry, phải xác định timeout, cancellation, giới hạn retry, idempotency và thứ tự phát event.
- Đoạn demo chạy tuần tự để giải thích semantics; trong ứng dụng thật, các pha giữa snapshot và commit có thể chạy ở thread khác. Invariant vẫn được bảo vệ khi mọi mutation đi qua đúng monitor.

## 15. Callback chờ nhưng registry vẫn xử lý request khác

Mẫu chụp snapshot rồi gọi callback ngoài lock có lợi ngay cả khi callback không làm I/O mà chờ một tín hiệu.

Ví dụ Java 17+ có callback cố tình đợi latch, trong khi thread chính vẫn đọc được registry:

Lưu thành `CallbackProgressDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CallbackProgressDemo {
    static final class Hub {
        private final List<Runnable> callbacks = new ArrayList<>();

        synchronized void register(Runnable callback) {
            callbacks.add(callback);
        }

        synchronized int count() {
            return callbacks.size();
        }

        void fire() {
            List<Runnable> snapshot;
            synchronized (this) {
                snapshot = List.copyOf(callbacks);
            }
            for (Runnable callback : snapshot) {
                callback.run(); // Không giữ monitor của Hub.
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Hub hub = new Hub();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        hub.register(() -> {
            System.out.println("Callback entered");
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Callback released");
        });

        Thread caller = new Thread(hub::fire);
        caller.start();
        entered.await();

        try {
            System.out.println(
                    "Registry size while callback waits: " + hub.count());
        } finally {
            release.countDown();
            caller.join();
        }
    }
}
```

```bash
javac CallbackProgressDemo.java
java CallbackProgressDemo
```

Stdout chính xác trên đường chạy bình thường:

```text
Callback entered
Registry size while callback waits: 1
Callback released
```

**Vì sao ví dụ không bị kẹt?**

- `fire` giữ monitor của Hub **chỉ để copy callback list**.
- Callback có thể đợi `release` nhưng không còn giữ monitor Hub. Main vẫn gọi được `hub.count()` và sau đó mở latch.
- Nếu viết `synchronized void fire()` rồi gọi callback ngay trong method, main sẽ chờ lock trong `count()`, trong khi callback đợi main gọi `release.countDown()`: chương trình có nguy cơ mắc kẹt.
- `Thread.join()` được gọi sau `release.countDown()` để chờ thread callback kết thúc, không phải vòng polling `Thread.getState()`.

Trong production, tránh chờ vô hạn khi có dữ liệu hoặc callback không đáng tin: thêm timeout, chính sách hủy và giám sát lỗi. Ở đây latch được dùng để mô phỏng chính xác một callback đang chờ.

## 16. Khi muốn thực hiện callback bất đồng bộ, dùng executor có phải đủ?

Không hẳn. Một kiến trúc thường thấy:

```text
Thread ghi state
   |
   v
[lock, update state, create immutable event, unlock]
   |
   v
[executor/queue]
   |
   v
[callback trên worker khác]
```

Ý tưởng tốt là không buộc thread thay đổi state chạy callback nặng. Tuy nhiên, cần hiểu:

1. **Submit có thể bị từ chối** nếu executor shutdown hoặc queue đầy; `RejectedExecutionException` hoặc chính sách rejection phải được xử lý.
2. Một số chính sách như **CallerRunsPolicy** có thể chạy tác vụ ngay trên thread gọi; một số executor thực hiện trực tiếp trên caller. Do đó, **không gọi `executor.execute(callback)` bên trong lock rồi mặc nhiên tin rằng callback sẽ luôn chạy ngoài lock**. Hãy nhả lock **trước khi submit**.
3. Executor có thể sử dụng nhiều worker, nên callback cùng listener **có thể chạy đồng thời** hoặc event hoàn thành lệch thứ tự.
4. Future và exception cần được thu thập/định tuyến. Nếu callback ném lỗi trên worker, publisher có thể không nhận được lỗi theo cách trực tiếp như callback đồng bộ.
5. Queue không giới hạn có thể tích tụ backlog; queue bounded yêu cầu chính sách backpressure hoặc drop/retry có kiểm soát.
6. Khi shutdown, phải quyết định các event đã accepted có được gửi hết không, timeout khi drain, cách hủy và cách giải phóng tài nguyên.

Nếu API cam kết **thứ tự event theo commit**, phải định nghĩa cách enqueue tương ứng với thứ tự commit và worker dispatch. Hai thread có thể commit lần lượt dưới lock rồi submit ngoài lock theo thứ tự đảo; queue FIFO khi đó vẫn dispatch theo thứ tự enqueue, không nhất thiết thứ tự commit. Có thể dùng sequence number và một dispatcher phù hợp hoặc reservation/queue protocol được thiết kế rõ ràng.

**Async callback không xóa yêu cầu thread safety của listener.** Nó chỉ chuyển ranh giới thực thi. Listener vẫn có thể chia sẻ mutable state hoặc gọi ngược API đang được dùng.

## 17. Có cần giữ lock qua I/O để bảo đảm cùng thứ tự ghi?

Đôi khi mục tiêu của code là "đừng gửi message B trước message A". Giữ một monitor qua toàn bộ network send có thể **tình cờ** ép serialization, nhưng đổi lại mọi caller phải chờ network. Cần tách hai yêu cầu:

- **Bảo vệ state và gán thứ tự:** critical section ngắn để tạo sequence, lấy snapshot hoặc enqueue event theo thứ tự.
- **Thực hiện I/O:** một worker sở hữu output channel hoặc một hàng đợi đã xác định ordering; worker xử lý ngoài monitor state.

Với socket hoặc stream mà nhiều thread ghi, phải hiểu **atomicity của message framing**. Một `OutputStream.write(byte[])` không tạo ra hợp đồng transaction đa message cho protocol tùy ý; dữ liệu có thể phải đóng gói frame và gửi bởi một owner duy nhất hoặc khóa riêng chuyên dụng cho writer.

Một lock riêng cho **writer** cũng có thể cần thiết nếu giao thức output yêu cầu serialization. Nhưng **đừng dùng chính monitor quản lý toàn bộ state** để giữ xuyên một network call lâu, trừ khi đã chứng minh tradeoff là cần thiết và có timeout rõ ràng.

**Không có bảo đảm rằng file write hay socket write có thể cancel ngay bằng `Thread.interrupt()`.** Tính interruptible phụ thuộc API, loại channel và hệ điều hành. Với blocking network I/O, đặt timeout, thiết kế close/cancel theo API và xử lý partial write/retry theo protocol.

## 18. Giữ dữ liệu nhất quán trong khi gọi callback bên ngoài lock

Khi chụp state cho callback, nên ưu tiên **immutable event**:

```java
record AccountEvent(long accountId, long version, long balance) {}
```

Nếu thread thay đổi state sau khi event được tạo, callback vẫn đọc được payload đã chụp. Đây là ví dụ record immutable vì chỉ chứa kiểu primitive. Với record chứa `List<Item>` và `Item` mutable, cần copy cấu trúc và xử lý mutable element riêng.

Giải pháp "chụp dưới lock" chỉ đúng nếu các giá trị đang đọc được bảo vệ bởi **cùng giao thức lock**. Nếu một writer khác có quyền sửa field mà không giữ lock này, snapshot vẫn có thể thiếu nhất quán.

### Một event và một callback có thể chạy đồng thời với event khác

Khi publisher đã nhả lock, thread khác có thể commit thay đổi mới, trong khi event cũ chưa dispatch. Nếu listener kỳ vọng thứ tự commit nghiêm ngặt, phải tạo thêm chính sách ordering. Nếu chỉ cần **at-least-once notification** hoặc **eventual update**, cần xác định duplicate/retry/out-of-order và giải pháp idempotent.

Public contract nên trả lời:

- "Event mang snapshot tại thời điểm nào?"
- "Callback được gọi đồng bộ hay bất đồng bộ, trên thread nào?"
- "Có thể gọi trùng, gọi đồng thời hoặc gọi lệch thứ tự không?"
- "`unsubscribe` có ngăn callback đã được chụp snapshot không?"
- "Event đã được queue nhưng chưa gửi khi close sẽ ra sao?"
- "Callback lỗi thì state đã commit có bị hoàn tác không?"

Không nên viết chỉ "listener thread-safe" mà bỏ qua các điểm kể trên.

## 19. Khi bắt buộc phải giữ lock qua mã chậm

Có những hệ thống cần tính tuần tự mạnh hoặc tài nguyên legacy không có thiết kế owner/queue. Khi không thể tách ngay, cần làm rõ tradeoff và giảm rủi ro:

1. Xác định monitor nào đang giữ và ai khác cần monitor đó.
2. Đặt **timeout** hoặc deadline cho những lời gọi có hỗ trợ. Đừng mặc định một lời gọi I/O luôn trả về.
3. Không gọi callback tùy ý của khách hàng nếu thiếu contract về không block/reentrance/locking order.
4. Tránh giữ đồng thời nhiều lock; nếu cần, dùng một thứ tự lấy lock thống nhất.
5. Đặt rollback, reset hoặc trạng thái lỗi hợp lệ trong `try/finally` khi cần, nhưng nhớ rằng external side effect có thể không rollback được.
6. Theo dõi **lock contention**, thời gian chờ, queue depth, callback duration, deadlock và timeout.
7. Đánh giá chuyển sang snapshot + ngoài lock, state machine, producer–consumer hoặc actor-like owner khi có thể.

Với `ReentrantLock`, người viết phải nhả bằng `unlock()` trong `finally`; còn block `synchronized` được JVM tự nhả khi thoát. **Không** nhầm `finally` với khả năng hủy một blocking call: nếu thread kẹt mãi trong lời gọi I/O, control flow chưa chạy đến `finally`.

Một đặc tính của `ReentrantLock.lockInterruptibly()` là có thể đợi **lấy khóa** theo cách interruptible; nó không làm những network call đã được gọi trong critical section tự cancel.

## 20. Callback dưới lock có bao giờ là đúng?

Không phải mọi callback đều bị cấm tuyệt đối. Một số internal hook rất ngắn, do chính implementation kiểm soát, không block, không tái nhập và không lấy lock khác có thể được gọi trong critical section khi contract rõ ràng.

Ví dụ:

- Một pure function được kiểm soát nội bộ, không gọi code tùy ý, dùng để tính nhanh giá trị từ state đã khóa.
- Một thao tác cập nhật invariant trên object phụ mà thread-safe protocol đã xác định.
- Một callback nội bộ được audit và không có đường chờ/lấy lock ngược.

Nhưng đối với callback nhận từ **public API** như `Consumer<T>` hoặc `Runnable`, implementation không kiểm soát hành vi. Hợp đồng "callback này phải nhanh và không block" có thể là một ràng buộc, song nó khó bảo đảm caller nào cũng tuân thủ. Thiết kế không chạy callback dưới monitor vẫn thường dễ bảo trì và robust hơn.

Ngoài ra, ngay cả pure callback không block cũng có thể ném unchecked exception. Nếu toàn bộ operation kỳ vọng invariant đang ở trạng thái chuyển tiếp, cần khôi phục invariant trước khi trả control ra bên ngoài.

## 21. Checklist code review: có call-out khi giữ monitor không?

Khi review một class dùng `synchronized`, hãy tìm những lời gọi sau:

| Dấu hiệu trong critical section | Câu hỏi cần kiểm tra |
| --- | --- |
| `listener.accept(...)`, `callback.run()` | Callback có thể block, tái nhập, ném lỗi hoặc lấy lock khác không? |
| `InputStream.read`, `OutputStream.write/flush` | Có timeout không, chi phí I/O có giới hạn không, partial I/O xử lý thế nào? |
| `Future.get`, `join`, `await` | Task đang được đợi có thể cần chính monitor để hoàn tất không? |
| `BlockingQueue.put/take` | Queue có thể đầy/rỗng, cần thread giữ monitor mới có tiến độ không? |
| `Thread.sleep` | Có vô tình giữ monitor trong lúc ngủ không? Sleep không nhả lock |
| `executor.execute(...)` | Executor có thể inline task, reject hoặc block trong custom implementation không? |
| `log(...), serialize(...), toString()` | Có callback/collaborator user-defined hoặc I/O ẩn không? |
| `otherService.method(...)` | Có lock bên kia, callback ngược hoặc lock-order inversion không? |

**Những rủi ro gián tiếp cũng đáng kiểm tra.** Một method tên `format` hoặc `toString` có thể gọi formatter do người dùng cung cấp; một logger có appender chậm; một serializer có custom adapter; một thao tác collection có comparator do caller định nghĩa. Không chỉ nhìn tên hàm "I/O" hay "callback".

### Công cụ phát hiện

- Thread dump và `ThreadMXBean` có thể hỗ trợ xem monitor owner, thread chờ monitor và một số trường hợp deadlock.
- Java Flight Recorder (JFR) hoặc profiler phù hợp có thể đo monitor contention, blocking và thời gian callback.
- Test dùng `CountDownLatch` để tạo lịch chờ chủ động giúp lộ vòng phụ thuộc; đừng chỉ dùng `Thread.sleep` rồi đoán thứ tự.
- Stress test hữu ích nhưng không thay được phân tích ownership, thứ tự lock và happens-before.

Một `Thread.State.BLOCKED` là dấu hiệu thread đang đợi intrinsic monitor, **không tự chứng minh deadlock**. Cần phân tích lock owner và vòng phụ thuộc hoặc progress qua thời gian.

## 22. Lỗi phổ biến và cách giải thích chính xác

**"Synchronized khiến callback tự động thread-safe."** Không. Nó chỉ giới hạn những thread muốn giữ cùng monitor; callback có thể truy cập state khác hoặc được các publisher khác gọi đồng thời.

**"Callback gọi lại method synchronized của chính object luôn deadlock."** Không. Intrinsic monitor reentrant với cùng thread. Nhưng callback có thể tạo đệ quy, iterator invalidation hoặc deadlock khi chờ thread khác.

**"Chỉ cần chuyển `listener.accept` ra ngoài lock là giữ nguyên mọi semantics."** Không. Snapshot và ordering thay đổi; listener có thể nhận event cũ trong lúc state mới đã commit. Cần document rõ.

**"`Thread.sleep` tạm nhả lock."** Sai. Sleep không tự nhả intrinsic monitor đang giữ.

**"`Future.get` hoặc `BlockingQueue.take` sẽ nhả mọi lock khi chờ."** Sai. Chúng không nhả monitor bên ngoài của caller.

**"`Object.wait` sẽ nhả tất cả lock đang sở hữu."** Sai. `wait` nhả monitor của **đối tượng mà nó được gọi trên**, không nhả tùy ý những monitor khác.

**"Nhả lock trước I/O đồng nghĩa thao tác đã atomic."** Sai. State có thể đổi khi I/O diễn ra. Nếu cần commit theo snapshot, phải kiểm tra version/reservation và xử lý stale result.

**"Dùng executor nghĩa là callback chắc chắn ở thread khác."** Sai. Có direct executor và rejection policy chạy tác vụ trên caller thread. Hãy nhả monitor trước cả bước submit.

**"Có `finally` thì lock được nhả ngay khi thread bị block."** Sai. `finally` chỉ chạy khi control rời khỏi `try`; một lời gọi block vô hạn ngăn code đến bước cleanup.

**"Một monitor có thể bảo vệ cả network transaction."** Không thể bảo đảm transaction bên ngoài JVM bằng intrinsic monitor. Cần giao thức transaction/idempotency/compensation tương ứng.

**"Không có exception thì không có vấn đề."** Không. Contention và deadlock có thể làm ứng dụng treo mà không ném exception; slow callback có thể chỉ làm tail latency tăng.

## 23. Câu hỏi tự kiểm tra

**Câu 1:** Một `synchronized void publish(...)` duyệt `ArrayList` listener rồi gọi callback. Listener gọi `addListener` synchronized trên cùng instance. Điều gì đúng?

**Trả lời:** Monitor reentrant nên lời gọi cùng thread không tự deadlock vì lock đó. Nhưng việc thêm vào `ArrayList` trong khi iterator đang duyệt có thể phá iterator, và callback vẫn được chạy dưới monitor. Cần snapshot và callback ngoài lock hoặc một protocol khác.

**Câu 2:** Thread A giữ monitor khi gọi `Future.get()`. Task của future muốn gọi method synchronized trên cùng object. Có nguy cơ gì?

**Trả lời:** Nếu task chạy trên thread khác, A đợi task; task đợi A nhả monitor: **deadlock**. Tách việc đợi future khỏi critical section.

**Câu 3:** `Object.wait()` có tương đương `Thread.sleep()` về monitor không?

**Trả lời:** Không. `wait` gọi đúng trên monitor đang giữ sẽ tạm nhả monitor đó và lấy lại trước khi trả về; `sleep` không nhả monitor.

**Câu 4:** Snapshot listener rồi gọi callback bên ngoài lock có bảo đảm callback luôn thấy state hiện tại mới nhất không?

**Trả lời:** Không. Callback nhận snapshot tại một mốc, trong khi thread khác có thể cập nhật state sau đó. Nếu yêu cầu ordering, phải thêm protocol dispatch/version.

**Câu 5:** Vì sao queue `put` trong ví dụ an toàn hơn khi không giữ monitor state?

**Trả lời:** Queue có thể đầy làm `put` đợi; nhả monitor trước `put` cho phép thread khác cập nhật state/tiêu thụ queue để hệ thống tiếp tục.

**Câu 6:** Nếu I/O gửi thành công nhưng `commitIfUnchanged` trả `false`, intrinsic monitor có rollback external side effect không?

**Trả lời:** Không. Phải thiết kế idempotency, compensation hoặc giao thức đồng bộ với tài nguyên bên ngoài.

**Câu 7:** Gọi callback qua `executor.execute` khi đang `synchronized` đã chắc chắn an toàn chưa?

**Trả lời:** Chưa. Một executor hoặc rejection handler có thể chạy task inline ngay trên caller. Ngoài ra task có thể bị reject. Nhả monitor trước submit và định nghĩa chính sách lỗi, backlog và ordering.

**Câu 8:** Khi nào gọi callback dưới lock có thể chấp nhận được?

**Trả lời:** Khi mã callback được kiểm soát, rất ngắn, không block/tái nhập/lấy lock ngược, invariant và exception đã được chứng minh phù hợp. Với callback không kiểm soát từ public API, nên tránh.

## 24. Checklist triển khai

1. **Xác định invariant** cần đồng bộ: field nào phải đọc/ghi như một đơn vị, monitor nào bảo vệ?
2. **Vạch ranh giới call-out:** mọi callback, I/O, queue/future/await, logging/serialization có thể block hoặc gọi code ngoài quyền kiểm soát.
3. **Rút ngắn critical section:** copy immutable event/snapshot hoặc thay đổi state hợp lệ rồi nhả monitor.
4. **Thực thi chậm ngoài monitor:** callback, I/O, future wait, submit task; thiết kế timeout/cancel nếu API hỗ trợ.
5. **Kiểm tra lại state nếu cần:** version, token, reservation hoặc state machine khi commit kết quả sau pha ngoài lock.
6. **Giữ thứ tự nếu đó là contract:** queue/dispatcher với protocol commit–enqueue có thể chứng minh đúng.
7. **Xử lý exception rõ ràng:** callback fail, I/O fail, partial side effect, rejected task, lỗi shutdown, permit/lock release.
8. **Công bố concurrency contract:** callback thread model, concurrent delivery, removal semantics, snapshot visibility, failure policy, blocking, reentrancy và ordering.
9. **Test trường hợp xấu:** callback chờ, callback tái nhập, hai monitor ngược thứ tự, queue đầy, timeout, lỗi I/O, close khi còn task.
10. **Đo contention và tiến độ:** monitor wait, callback duration, queue depth, tail latency; đừng đánh đổi tính đúng để chỉ giảm thời gian đo được.

Với Java concurrency, câu hỏi mấu chốt không chỉ là **"đã synchronized chưa?"** mà còn là **"mình đang giữ monitor trong khi trao quyền chạy cho ai, đợi điều gì, và state có thể thay đổi thế nào trước khi tiếp tục?"**. Bảo vệ state bằng critical section ngắn, xử lý callback/I/O ở ngoài và xác nhận lại invariant khi commit giúp giảm đáng kể deadlock, nghẽn và những lỗi khó tái hiện.

</details>

- [Quay lại đầu trang](#back-to-top)
