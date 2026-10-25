<a id="back-to-top"></a>

# Monitor của Object

## Menu
- [Mỗi đối tượng có monitor phục vụ cơ chế đồng bộ](#object-monitor-001)
- [wait(), notify() và notifyAll() là phương thức final](#object-monitor-002)
- [Điều kiện sở hữu monitor và IllegalMonitorStateException](#object-monitor-003)
- [wait() nhả monitor của đối tượng đang chờ và phải giành lại trước khi trả về](#object-monitor-004)
- [Thức dậy không bảo đảm điều kiện đã đúng](#object-monitor-005)
- [Dùng vòng while kiểm tra điều kiện trước và sau khi chờ](#object-monitor-006)
- [notify() không chuyển khóa ngay cho luồng được đánh thức](#object-monitor-007)
- [Xử lý InterruptedException và thời hạn chờ theo hợp đồng](#object-monitor-008)

## <a id="object-monitor-001">Mỗi đối tượng có monitor phục vụ cơ chế đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Monitor của mỗi đối tượng trong Java và cơ chế đồng bộ

Trong Java, **mỗi đối tượng có thể được dùng làm khóa đồng bộ nội tại (intrinsic lock, thường gọi là monitor)**. Khi một luồng đi vào khối `synchronized` gắn với đối tượng đó, nó phải sở hữu monitor của đối tượng trước khi thực thi phần code được bảo vệ. Cơ chế này giúp phối hợp truy cập dữ liệu dùng chung và tạo bảo đảm về khả năng nhìn thấy thay đổi giữa các luồng.

> “Mỗi đối tượng có monitor” là mô hình lập trình và ngữ nghĩa mà Java cung cấp. JVM có thể tối ưu cách biểu diễn và cấp phát khóa ở mức triển khai; không nên suy luận rằng mọi instance đều phải có một cấu trúc monitor riêng được cấp phát sẵn trong bộ nhớ.

## 1. Monitor gắn với **đối tượng nào**?

Ví dụ:

~~~java
Object lockA = new Object();
Object lockB = new Object();

synchronized (lockA) {
    // Luồng đang giữ monitor của lockA.
}

synchronized (lockB) {
    // Một monitor khác, độc lập với lockA.
}
~~~

- Hai luồng cùng dùng **chính xác cùng một đối tượng** `lockA` sẽ phải cạnh tranh cùng một intrinsic lock: tại một thời điểm tối đa một luồng sở hữu monitor ấy để thực thi đoạn `synchronized` tương ứng.
- Một luồng giữ monitor của `lockA` không tự động ngăn luồng khác đi vào `synchronized (lockB)`. Hai khóa khác nhau không bảo vệ lẫn nhau.
- Mỗi đối tượng Java có thể làm khóa, kể cả instance của lớp do ta tạo, mảng và đối tượng `Class`. `null` không phải đối tượng; `synchronized (null)` gây `NullPointerException`.
- Biến tham chiếu **không sở hữu monitor riêng**. Hai biến cùng trỏ đến một instance sẽ dùng cùng khóa; hai instance khác nhau có khóa khác nhau.

Một ví dụ quan trọng về đối tượng được khóa:

| Cú pháp | Monitor phải sở hữu |
| --- | --- |
| `synchronized (someObject) { ... }` | Đối tượng mà `someObject` trỏ tới |
| `synchronized void method()` (phương thức instance) | `this` của instance đang gọi |
| `static synchronized void method()` | Đối tượng `SomeClass.class` tương ứng với lớp khai báo |

Bởi vậy, phương thức `static synchronized` **không khóa mọi instance** theo nghĩa khóa của `this`. Nó khóa đối tượng `Class`; đồng bộ tĩnh và đồng bộ instance thường là hai monitor khác nhau.

## 2. Khi đi vào và rời `synchronized` xảy ra điều gì?

Giả sử nhiều luồng cùng chạy:

~~~java
synchronized (lock) {
    // Truy cập trạng thái dùng chung do lock bảo vệ.
}
~~~

Trình tự logic:

1. Luồng yêu cầu quyền sở hữu monitor của `lock`.
2. Nếu monitor rảnh, luồng giành khóa và chạy phần thân. Nếu một luồng khác đang giữ nó, luồng hiện tại không thể vào phần thân ngay và có thể chuyển sang trạng thái `BLOCKED`.
3. Khi rời khối, kể cả khi thoát do exception, luồng nhả khóa theo ngữ nghĩa của `synchronized`; một luồng đang chờ khóa có thể tiếp tục cạnh tranh để giành monitor.
4. Java **không hứa hẹn công bằng/FIFO** về luồng nào sẽ giành khóa tiếp theo.

Monitor là **reentrant**: một luồng đã sở hữu monitor có thể vào `synchronized` khác trên cùng đối tượng mà không tự chặn chính mình. JVM theo dõi mức lồng nhau và khóa chỉ được nhả hoàn toàn sau khi luồng thoát hết các mức khóa tương ứng.

~~~java
class ReentrantExample {
    synchronized void outer() {
        inner(); // Cùng luồng tiếp tục đi vào monitor của this.
    }

    synchronized void inner() {
        // Có thể thực hiện dù outer() đang giữ chính monitor này.
    }
}
~~~

Nhớ rằng chỉ code thực sự dùng **cùng monitor** mới được bảo vệ. Một hàm đọc hoặc ghi trường dùng chung mà bỏ qua khóa sẽ không tự động được an toàn chỉ vì hàm khác có `synchronized`.

## 3. Monitor giải quyết hai vấn đề: loại trừ tương hỗ và visibility

Giả sử hai luồng cùng tăng một biến:

~~~java
count++;
~~~

Lệnh này về mặt logic gồm đọc giá trị, tính giá trị mới rồi ghi lại. Nó không tự động là một thao tác nguyên tử. Hai luồng có thể cùng đọc một giá trị cũ và làm mất một lần tăng.

Ví dụ hoàn chỉnh, chạy với Java 17 trở lên:

**Tệp `MonitorCounterDemo.java`**

~~~java
public class MonitorCounterDemo {
    static final class Counter {
        private int count;

        synchronized void increment() {
            count++;
        }

        synchronized int get() {
            return count;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();

        Runnable task = () -> {
            for (int i = 0; i < 10_000; i++) {
                counter.increment();
            }
        };

        Thread first = new Thread(task);
        Thread second = new Thread(task);
        first.start();
        second.start();

        first.join();
        second.join();

        System.out.println("So lan tang: " + counter.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MonitorCounterDemo.java
java MonitorCounterDemo
~~~

Kết quả:

~~~text
So lan tang: 20000
~~~

Cả hai luồng gọi `increment()` trên **cùng instance `counter`**, nên chúng phải lần lượt nắm monitor của instance đó. `count++` được bảo vệ như một vùng tới hạn; không bị mất cập nhật giữa hai luồng này. `get()` cũng đi qua monitor ấy. `join()` bảo đảm main chờ cả hai luồng kết thúc trước khi in kết quả.

Ngoài loại trừ tương hỗ, `synchronized` có quy tắc **happens-before** của Java Memory Model: thao tác **mở khóa một monitor** happens-before thao tác **khóa lại chính monitor đó** xảy ra sau. Nhờ đó, các ghi trước khi nhả khóa được nhìn thấy đúng qua lần giành khóa tiếp theo cùng monitor. Điều kiện “cùng monitor” rất quan trọng; khóa một đối tượng khác không tạo ra quan hệ này.

Không nên quy mọi hiệu ứng visibility cho monitor nếu ví dụ còn dùng `Thread.start()`/`Thread.join()`: bản thân các thao tác vòng đời luồng này cũng có quy tắc happens-before riêng. Điều cốt lõi của `synchronized` là code đọc và ghi dữ liệu chung tuân thủ **cùng một chính sách khóa**.

## 4. Monitor còn cho phép **chờ một điều kiện** qua `wait` / `notify` / `notifyAll`

Ngoài quyền sở hữu khóa, mỗi đối tượng cung cấp các thao tác phối hợp luồng kế thừa từ `java.lang.Object`:

- `wait()`: luồng hiện tại chờ tín hiệu/điều kiện trên đối tượng, **tạm nhả monitor của chính đối tượng đó** trong thời gian chờ.
- `notify()`: đánh thức **một** luồng đang chờ trên đối tượng đó, nếu có; không đảm bảo chọn luồng nào.
- `notifyAll()`: đánh thức **tất cả** luồng đang chờ trên đối tượng đó để chúng có cơ hội kiểm tra điều kiện.

Muốn gọi các phương thức này trên `obj`, luồng gọi phải **đang sở hữu monitor của `obj`** (ví dụ đang ở trong `synchronized (obj)` hay synchronized instance method của `obj`). Nếu không, `IllegalMonitorStateException` được ném ra. Có được monitor của **đối tượng khác** là chưa đủ.

Luồng gọi `wait()` phải lấy lại monitor của đối tượng trước khi thoát khỏi `wait()` và tiếp tục chạy. `notify()` và `notifyAll()` **không lập tức nhả khóa**: luồng được đánh thức chỉ có thể tiếp tục sau khi monitor sẵn có để giành lại.

Có thể hình dung hai lý do chờ khác nhau:

| Tình huống | Ý nghĩa |
| --- | --- |
| Luồng muốn vào `synchronized` nhưng monitor đang bị giữ | Chờ **giành khóa**; thường quan sát ở trạng thái `BLOCKED` |
| Luồng đã có monitor rồi gọi `wait()` | Nhả monitor tương ứng và chờ **điều kiện/tín hiệu**; `WAITING` hoặc `TIMED_WAITING` với wait có timeout |
| Luồng được `notifyAll()` đánh thức | Phải **giành lại monitor**; có thể tạm `BLOCKED` trước khi thoát `wait()` |

Các trạng thái trên giúp đọc thread dump; thứ tự scheduling cụ thể vẫn phụ thuộc JVM/hệ điều hành.

## 5. Ví dụ điều kiện thực tế: hộp thư một phần tử

Một luồng gửi giá trị, luồng khác nhận. Cả `send()` và `receive()` đồng bộ trên **chính instance `Mailbox`**. Điều kiện được biểu diễn bằng `message == null`:

**Tệp `MonitorWaitNotifyDemo.java`**

~~~java
public class MonitorWaitNotifyDemo {
    static final class Mailbox {
        private Integer message;

        synchronized void send(int value) throws InterruptedException {
            while (message != null) {
                wait();             // Chờ hộp thư trống; nhả monitor của this.
            }
            message = value;
            notifyAll();            // Báo trạng thái hộp thư đã thay đổi.
        }

        synchronized int receive() throws InterruptedException {
            while (message == null) {
                wait();             // Chờ có dữ liệu; nhả monitor của this.
            }
            int result = message;
            message = null;
            notifyAll();            // Báo hộp thư lại trống.
            return result;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Mailbox mailbox = new Mailbox();
        int[] received = {-1};

        Thread reader = new Thread(() -> {
            try {
                received[0] = mailbox.receive();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        reader.start();

        mailbox.send(42);
        reader.join();

        System.out.println("Nhan duoc: " + received[0]);
    }
}
~~~

Chạy:

~~~bash
javac MonitorWaitNotifyDemo.java
java MonitorWaitNotifyDemo
~~~

Kết quả khi không có yêu cầu ngắt luồng:

~~~text
Nhan duoc: 42
~~~

Giải thích luồng chạy:

1. Nếu `reader` vào `receive()` trước, nó thấy `message == null` rồi `wait()`; việc chờ nhả monitor để luồng main có thể vào `send()`.
2. `send(42)` lưu giá trị và gọi `notifyAll()` trong khi giữ monitor. Khi `send()` rời phương thức, nó nhả monitor; `reader` lấy lại monitor, kiểm tra điều kiện và nhận `42`.
3. Nếu main chạy `send(42)` trước, `reader` sẽ thấy dữ liệu đã sẵn có nên không cần đợi. Vì trạng thái điều kiện được lưu trong `message`, tín hiệu không phải vật chứa dữ liệu.
4. Sau `reader.join()`, main đọc kết quả đã ghi. Kết quả in ra độc lập với thứ tự hai luồng được lập lịch trong trường hợp không bị ngắt.

**Vì sao phải dùng `while` thay cho `if` quanh `wait()`?** Một luồng có thể thức dậy mà chưa có điều kiện mong muốn (*spurious wakeup*), hoặc luồng khác đã thay đổi điều kiện trong khoảng thời gian luồng đang tìm cách giành lại monitor. Luôn kiểm tra lại **predicate của trạng thái** dưới cùng monitor. Với nhiều kiểu người chờ/điều kiện, `notifyAll()` thường dễ lập luận đúng hơn `notify()`, dù có thể làm nhiều luồng thức dậy kiểm tra rồi lại chờ.

Khi `wait()` bị ngắt, nó ném `InterruptedException` sau khi lấy lại monitor theo ngữ nghĩa API; ứng dụng nên truyền ngoại lệ lên nơi điều phối hoặc xử lý hủy tác vụ rõ ràng. Ví dụ trên khôi phục cờ ngắt cho luồng con ở ranh giới `Runnable`. Trong ứng dụng thực tế, nên có đường đi xử lý kết quả bị hủy/thất bại thay vì mặc định nhận được một thông điệp.

## 6. Phân biệt rõ `wait`, `notify`, `sleep` và tranh chấp khóa

| Hoạt động | Cần sở hữu monitor? | Có nhả monitor đang giữ? | Mục đích |
| --- | --- | --- | --- |
| Vào `synchronized (obj)` | Phải giành monitor `obj` | Không | Bảo vệ vùng tới hạn |
| `obj.wait()` | Có, của `obj` | Có, **chỉ monitor của `obj`** | Chờ điều kiện trên `obj` |
| `obj.notify()` / `notifyAll()` | Có, của `obj` | Không | Thông báo cho luồng đang chờ |
| `Thread.sleep(...)` | Không | Không | Tạm ngừng theo thời gian |

Một lưu ý dễ bỏ sót: nếu luồng đang giữ nhiều monitor lồng nhau, `obj.wait()` chỉ nhả monitor của `obj`, **không nhả các monitor khác** mà luồng đó đang giữ. Việc giữ các khóa khác trong lúc chờ có thể làm chương trình bế tắc.

Các biến thể `wait(long timeout)` / `wait(long timeout, int nanos)` có thể kết thúc khi hết thời gian, được thông báo hoặc bị đánh thức không có lý do xác định; vì vậy vẫn cần vòng `while` kiểm tra điều kiện. `wait(0)` là chờ vô thời hạn, không phải “chờ 0 mili giây rồi trả về”.

## 7. Những sai lầm phổ biến và cách tránh

**Sai: khóa một đối tượng mới mỗi lần gọi.**

~~~java
synchronized (new Object()) {
    // Mỗi lần thường là một khóa mới, không phối hợp với các lời gọi khác.
}
~~~

Muốn phối hợp giữa các luồng, chia sẻ cùng một lock ổn định, chẳng hạn trường `private final Object lock = new Object();`. Tránh khóa trên chuỗi literal, đối tượng `Class` công khai hoặc đối tượng mà code bên ngoài có thể tự ý khóa nếu điều đó gây tranh chấp ngoài dự kiến.

**Sai: gọi `wait()` khi không sở hữu khóa của đúng đối tượng.**

~~~java
Object lock = new Object();
lock.wait(); // IllegalMonitorStateException khi chạy.
~~~

Cách dùng đúng (đoạn dưới chỉ minh họa cú pháp; muốn chương trình hữu ích phải có điều kiện và luồng gửi tín hiệu):

~~~java
synchronized (lock) {
    while (!conditionIsTrue()) {
        lock.wait();
    }
}
~~~

Ở đây `conditionIsTrue()` là tên **minh họa**, không phải phương thức đã được định nghĩa trong ví dụ runnable ở trên.

**Sai: tưởng `notify()` chuyển ngay khóa sang luồng nhận.** Luồng báo vẫn giữ monitor cho tới khi rời `synchronized`; luồng nhận phải cạnh tranh lại để giành khóa.

**Sai: đặt `wait()` trong `if` hoặc phụ thuộc hoàn toàn vào “tín hiệu”.** Tín hiệu không lưu trữ thành một “vé” cho người đến sau. Phải có trạng thái điều kiện được bảo vệ, cập nhật trạng thái trước khi báo và kiểm tra trong `while`.

**Sai: đọc/ghi dữ liệu chung bằng các khóa khác nhau.** Nếu người viết dùng `lockA` nhưng người đọc dùng `lockB`, chúng không bảo đảm loại trừ tương hỗ hoặc happens-before qua hai monitor ấy. Thiết kế một quy ước “trường nào được bảo vệ bởi khóa nào” rồi áp dụng nhất quán, bao gồm cả đọc.

**Sai: giữ khóa trong lúc chạy tác vụ chậm, I/O hoặc gọi code không kiểm soát.** Các luồng khác phải chờ lâu hơn; nếu các luồng lấy nhiều khóa theo thứ tự ngược nhau, còn có nguy cơ deadlock. Thu hẹp vùng tới hạn và cố định thứ tự giành nhiều khóa khi cần.

**Sai: cho rằng monitor tự bảo đảm mọi thứ.** Intrinsic lock không mặc nhiên bảo đảm fairness, phát hiện deadlock, timeout khi giành khóa bằng `synchronized`, hay đánh thức đúng một loại người chờ khi nhiều điều kiện dùng chung wait-set. Với hàng đợi, ứng dụng thực tế thường thuận tiện hơn khi dùng `BlockingQueue`; với nhu cầu khóa có timeout/interruptible acquisition hoặc nhiều condition queue riêng, cân nhắc `ReentrantLock` và `Condition` từ `java.util.concurrent.locks`. Đây là các công cụ bổ sung, không làm thay đổi cơ chế monitor của `Object`.

## 8. Tự kiểm tra hiểu biết

1. Hai biến `a` và `b` cùng trỏ đến một instance: `synchronized(a)` và `synchronized(b)` có tranh chấp cùng khóa không? **Có**, vì khóa gắn với đối tượng, không gắn với tên biến.
2. Hai instance của cùng lớp có mặc nhiên chặn nhau khi gọi `synchronized` instance method? **Không**; mỗi instance có monitor riêng. `static synchronized` lại dùng đối tượng `Class`.
3. Tại sao chỉ dùng `volatile` cho `count` chưa đủ để thực hiện `count++` an toàn? **Vì** visibility không biến thao tác đọc–sửa–ghi nhiều bước thành nguyên tử.
4. Sau `notifyAll()`, luồng đang `wait()` có chạy tiếp ngay không? **Không**; nó còn phải lấy lại monitor và kiểm tra lại điều kiện.
5. Dùng `Thread.sleep()` ở trong `synchronized` có giúp luồng khác giành monitor ấy không? **Không**; `sleep` không nhả khóa.

## Kết luận

Monitor là cơ chế đồng bộ nội tại gắn với **danh tính một đối tượng Java**. `synchronized` dùng monitor ấy để bảo vệ vùng tới hạn và thiết lập quy tắc visibility; `wait`/`notify`/`notifyAll` dùng cùng monitor để phối hợp theo điều kiện. Khi thiết kế code đa luồng, hãy xác định rõ **đối tượng khóa nào được chia sẻ, trạng thái nào do khóa đó bảo vệ, và điều kiện nào phải kiểm tra lại dưới khóa**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-monitor-002">wait(), notify() và notifyAll() là phương thức final</a>

<details>
<summary>Xem chi tiết</summary>

# `Object.wait()`, `notify()` và `notifyAll()` là phương thức `final` trong Java

Ba phương thức `wait()`, `notify()` và `notifyAll()` thuộc lớp `java.lang.Object`. Chúng giúp các luồng phối hợp thông qua **monitor nội tại (intrinsic monitor)** của một đối tượng. Điểm quan trọng của hợp đồng API: cả ba đều được khai báo `final`, nên lớp con **không thể override** để thay đổi cách các lời gọi này được phân giải. Tuy nhiên, `final` không có nghĩa là không thể gọi, không thể dùng với `synchronized`, hay bản thân chương trình sẽ tự động an toàn đa luồng.

## 1. Chữ ký và phạm vi của `final`

Các chữ ký công khai liên quan trong Java 17+ (minh họa khai báo API, không phải một lớp Java độc lập để biên dịch):

```java
public final void wait() throws InterruptedException;
public final native void wait(long timeoutMillis) throws InterruptedException;
public final void wait(long timeoutMillis, int nanos) throws InterruptedException;

public final native void notify();
public final native void notifyAll();
```

- `wait()` đưa luồng hiện tại vào tập chờ (*wait set*) của đối tượng nhận lời gọi, đồng thời nhả monitor của chính đối tượng đó. Trước khi tiếp tục thực thi sau `wait`, luồng phải giành lại monitor.
- `wait(long)` và `wait(long, int)` là các overload có thời gian chờ; `wait(0)` cũng có thể chờ vô thời hạn. Thời gian hết chỉ tạo cơ hội thức dậy, không bảo đảm điều kiện ứng dụng đã đúng.
- `notify()` chọn **một** luồng đang chờ trên monitor của đối tượng để đánh thức; không bảo đảm chọn luồng nào.
- `notifyAll()` đánh thức **tất cả** luồng đang chờ trên monitor đó. Các luồng vẫn phải lần lượt tranh chấp và giành lại monitor trước khi chạy tiếp.
- Cả ba phương thức là **instance method** được mọi lớp tham chiếu kế thừa từ `Object`. Phải gọi trên **đúng đối tượng dùng làm monitor**, ví dụ `monitor.wait()` và `monitor.notifyAll()`.

Trong chữ ký, `final` là thuộc tính cấm override; `native` (ở một số overload) là chi tiết triển khai. Hai từ khóa có ý nghĩa khác nhau.

## 2. Tại sao các phương thức này là `final`?

Một đối tượng Java có thể được dùng làm monitor bởi `synchronized (obj)`. `Object.wait` và `Object.notify/notifyAll` là giao diện chuẩn để tương tác với cơ chế chờ/đánh thức liên quan tới monitor đó. Không cho lớp con override các phương thức cùng chữ ký giúp hợp đồng gọi phương thức này ổn định ở mọi kiểu đối tượng, tránh việc một lớp con cung cấp hành vi có tên `wait`/`notify` nhưng phá vỡ kỳ vọng của mã đồng bộ.

Ví dụ dưới đây **cố ý không biên dịch được**, nhằm chứng minh giới hạn của `final`:

```java
class BadMonitor extends Object {
    @Override
    public void notify() { // Lỗi biên dịch: không thể override Object.notify() vì final
        System.out.println("fake notification");
    }
}
```

Tương tự, không thể override `notifyAll()` hoặc bất kỳ chữ ký `wait()` nào đã khai báo `final`. Viết thêm phương thức khác chữ ký, như `wait(String reason)`, về mặt ngôn ngữ là **overload**, không phải override; cách đặt tên này dễ gây nhầm lẫn và không mang ngữ nghĩa chờ monitor của `Object.wait()`.

Cần phân biệt rõ: **`final` bảo vệ khả năng override ở mức ngôn ngữ/API**; tính nguyên tử của thao tác chờ với việc nhả monitor và các quy tắc sở hữu monitor là ngữ nghĩa của cơ chế đồng bộ Java. `final` không thay thế việc khóa và kiểm tra điều kiện đúng cách.

## 3. Hợp đồng monitor khi gọi `wait` và `notify`

Giả sử nhiều luồng dùng chung đối tượng `monitor` và một biến trạng thái `ready`. Mẫu chuẩn:

```java
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
    // Ở đây đã giữ lại monitor và ready được kiểm tra là true.
}

// Ở một luồng khác:
synchronized (monitor) {
    ready = true;
    monitor.notifyAll();
}
```

Đây là **đoạn minh họa**, giả định `monitor` và `ready` là trạng thái chia sẻ được bảo vệ bằng cùng một monitor; ví dụ runnable đầy đủ có ở mục 4.

Trình tự quan trọng:

1. Luồng chờ vào `synchronized (monitor)` và giữ monitor. Không được gọi `monitor.wait()` nếu không đang giữ monitor của `monitor`.
2. Nếu điều kiện chưa thỏa, `wait()` **nhả monitor của `monitor`** và chờ. Nhờ vậy luồng khác mới có thể vào khối `synchronized` để thay đổi trạng thái.
3. Luồng phát tín hiệu vào cùng monitor, cập nhật trạng thái trước rồi gọi `notify()` hoặc `notifyAll()`. Lời gọi thông báo **không lập tức nhả monitor**; việc nhả diễn ra khi luồng phát tín hiệu rời khối đồng bộ (hoặc bằng cơ chế kết thúc giữ khóa tương ứng).
4. Luồng được đánh thức phải giành lại monitor. Sau đó vòng `while` **kiểm tra lại điều kiện**, vì điều kiện có thể đã đổi, nhiều luồng có thể cùng thức dậy hoặc có *spurious wakeup* (thức dậy không do thông báo theo cách lập trình viên dự kiến).

**Lý do dùng `while` thay vì `if`:** trạng thái được chia sẻ mới quyết định có thể tiếp tục hay chưa; sự kiện thức dậy không phải lời cam kết trạng thái sẽ luôn đúng. Thông báo cũng **không được lưu lại** để dành cho một lần `wait()` trong tương lai.

Ngoài ra, `wait()` nhả **chỉ monitor của đối tượng nhận lời gọi**; nếu luồng đang đồng thời giữ monitor khác, những monitor khác không được `wait()` tự động nhả.

## 4. Ví dụ Java 17+ chạy được: đợi tín hiệu mở cổng

File: `ObjectMonitorFinalDemo.java`

```java
import java.util.concurrent.CountDownLatch;

public class ObjectMonitorFinalDemo {
    private static final class Gate {
        private final Object monitor = new Object();
        private boolean open;

        void awaitOpen(CountDownLatch waiting) throws InterruptedException {
            synchronized (monitor) {
                while (!open) {
                    waiting.countDown();
                    monitor.wait();
                }
            }
        }

        void open() {
            synchronized (monitor) {
                open = true;
                monitor.notifyAll();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Gate gate = new Gate();
        CountDownLatch waiting = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                gate.awaitOpen(waiting);
                System.out.println("Worker: tiếp tục");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        worker.start();
        waiting.await();
        gate.open();
        worker.join();
        System.out.println("Main: hoàn tất");
    }
}
```

Biên dịch và chạy (terminal hỗ trợ UTF-8):

```bash
javac -encoding UTF-8 --release 17 ObjectMonitorFinalDemo.java
java ObjectMonitorFinalDemo
```

Stdout:

```text
Worker: tiếp tục
Main: hoàn tất
```

Giải thích:

- `monitor` là đối tượng khóa riêng, còn `open` là **điều kiện chia sẻ** được đọc/ghi trong cùng khối `synchronized (monitor)`.
- `awaitOpen` chờ khi `open == false`; lúc gọi `monitor.wait()`, luồng nhả monitor để `open()` có thể giành khóa, đặt `open = true` và `notifyAll()`.
- `CountDownLatch waiting` chỉ làm bước phối hợp trong **bài demo**: buộc luồng chính đợi tới lúc worker chuẩn bị chờ. Vì worker giữ `monitor` đến khi gọi `wait()`, luồng chính không thể mở cổng trước khi worker nhả monitor. Đây không phải điều kiện bắt buộc để dùng `wait/notifyAll`.
- `worker.join()` bảo đảm dòng `Worker: tiếp tục` xuất hiện trước `Main: hoàn tất`. Ví dụ đã được biên dịch và chạy bằng JDK 21 với mức tương thích `--release 17`.

Ở ví dụ, `Gate` cũng kế thừa các phương thức `final` của `Object`, nhưng cách sử dụng tốt hơn là khai báo một đối tượng `monitor` riêng, không public, để tránh mã bên ngoài can thiệp vào cùng khóa.

## 5. Chọn `notify()` hay `notifyAll()`

| Phương thức | Hiệu lực | Khi dùng |
| --- | --- | --- |
| `notify()` | Đánh thức một luồng bất kỳ trong wait set của đối tượng | Khi đã **chứng minh được** một luồng bất kỳ đang chờ đều có thể xử lý trạng thái mới và không làm kẹt tiến độ |
| `notifyAll()` | Đánh thức tất cả luồng đang chờ trên cùng đối tượng; từng luồng phải giành lại monitor | Lựa chọn dễ lập luận hơn khi có nhiều điều kiện hoặc nhiều loại luồng chờ; luôn kiểm tra điều kiện bằng `while` |

`notifyAll()` có thể khiến nhiều luồng thức dậy rồi lại chờ, nên có chi phí tranh chấp khóa; nó **không** cho các luồng chạy đồng thời bên trong cùng monitor. `notify()` có thể gây kẹt nếu đánh thức một luồng không thể làm việc tiếp, trong khi luồng cần thức dậy vẫn đang đợi. Khi chưa chứng minh rõ điều kiện chọn người chờ, ưu tiên tính đúng đắn với `notifyAll()`.

## 6. Lỗi phổ biến và cách tránh

**Gọi mà không sở hữu đúng monitor.** Đoạn sau sẽ ném `IllegalMonitorStateException` khi thực thi:

```java
Object lock = new Object();
lock.notifyAll(); // Chưa synchronized(lock)
```

Đúng là:

```java
Object lock = new Object();
synchronized (lock) {
    lock.notifyAll();
}
```

`wait()` cũng yêu cầu quyền sở hữu monitor tương ứng. `synchronized (this)` không cho phép gọi `other.wait()` nếu chưa giữ monitor của `other`.

**Dùng `if` để kiểm tra điều kiện trước khi chờ.** Có thể thức dậy không mong đợi hoặc điều kiện đã bị luồng khác thay đổi. Dùng vòng `while (!condition) lock.wait()`, với việc đọc/ghi `condition` dưới cùng khóa.

**Nhầm `Thread.sleep()` với `Object.wait()`.** `Thread.sleep(...)` tạm dừng luồng nhưng **không nhả** monitor mà luồng đang giữ; `Object.wait()` nhả monitor của đối tượng nhận lời gọi. `sleep` không phải cơ chế nhận `notify`.

**Cho rằng `notifyAll()` làm các luồng chạy ngay.** Luồng phát tín hiệu vẫn giữ monitor tới lúc ra khỏi khối đồng bộ; các luồng nhận tín hiệu sau đó mới có thể cạnh tranh để lấy lại khóa.

**Nhầm tín hiệu với trạng thái.** Nếu gọi `notifyAll()` lúc chưa có ai chờ, không có thông báo nào được ghi nhớ. Luôn lưu trạng thái có ý nghĩa (`open`, số phần tử trong hàng đợi, cờ hủy…) và kiểm tra trạng thái ấy trong vòng lặp.

**Bỏ qua ngắt luồng.** `wait()` có thể ném `InterruptedException`. Khi bị ngắt trong lúc chờ, luồng phải giành lại monitor trước khi phương thức hoàn tất việc ném ngoại lệ. Nếu bắt ngoại lệ mà không thể xử lý tại đó, thường nên lan truyền nó hoặc phục hồi cờ ngắt bằng `Thread.currentThread().interrupt()` như trong ví dụ.

**Cho rằng `final` đồng nghĩa thread-safe.** Một lớp có thể gọi `wait()`/`notifyAll()` đúng chữ ký nhưng vẫn sai vì truy cập trạng thái thiếu đồng bộ, dùng không cùng monitor, chờ sai điều kiện hoặc khóa chéo. Cần thiết kế giao thức phối hợp luồng đúng.

## 7. Cách ghi nhớ và áp dụng

- **`final`:** không override được `wait`, `notify`, `notifyAll` ở các chữ ký chuẩn của `Object`.
- **Cùng monitor:** `synchronized(lock)`, `lock.wait()` và `lock.notifyAll()` phải liên quan đúng đối tượng `lock`.
- **Điều kiện mới quan trọng:** cập nhật trạng thái dưới khóa, chờ bằng `while`; `notify/notifyAll` chỉ là tín hiệu yêu cầu kiểm tra lại.
- **Không kỳ vọng thứ tự:** `notify()` không chọn người chờ theo thứ tự được bảo đảm; `notifyAll()` không bảo đảm lịch chạy.

Trong ứng dụng có phối hợp phức tạp, API cấp cao như `BlockingQueue`, `CountDownLatch` hoặc `Lock`/`Condition` thường dễ kiểm soát hơn. Chúng không làm thay đổi sự thật cốt lõi: các phương thức monitor chuẩn được kế thừa từ `Object` vẫn là `final`.

### Tự kiểm tra

1. Vì sao `@Override public void notify()` trong lớp con báo lỗi? — Vì `Object.notify()` là `final`.
2. Luồng đang giữ `synchronized (a)` có được gọi `b.wait()` không? — Chỉ khi nó **cũng** đang sở hữu monitor của `b`; giữ monitor của `a` là chưa đủ.
3. `notifyAll()` xong là mọi luồng chờ chạy qua `wait()` ngay? — Không. Mỗi luồng còn phải lấy lại monitor rồi kiểm tra lại điều kiện trong vòng `while`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-monitor-003">Điều kiện sở hữu monitor và IllegalMonitorStateException</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện sở hữu monitor và `IllegalMonitorStateException` trong Java

## 1. Mục tiêu và ngữ cảnh

Khi phối hợp các luồng bằng `synchronized`, `wait()`, `notify()` và `notifyAll()`, câu hỏi quan trọng nhất là: **luồng đang thực thi có sở hữu đúng monitor của đối tượng nhận lệnh hay không?** Nếu không, các thao tác chờ hoặc phát tín hiệu của `Object` ném `IllegalMonitorStateException`. Bài học này giải thích điều kiện sở hữu, cách nhận diện sai monitor và cách viết đoạn chờ/thông báo an toàn.

Sau bài học, bạn có thể:
- Xác định monitor đang được khóa bởi `synchronized` dạng khối, phương thức instance và phương thức static.
- Giải thích vì sao `obj.wait()`, `obj.notify()`, `obj.notifyAll()` yêu cầu **chính luồng hiện tại** sở hữu monitor của `obj`.
- Phân biệt việc **giải phóng monitor khi `wait`** với việc **chỉ đánh thức luồng khi `notify`**.
- Chẩn đoán `IllegalMonitorStateException` và sửa lỗi mà không vô tình thay đổi đối tượng dùng làm khóa.

## 2. Monitor ownership là gì?

Mỗi đối tượng Java thông thường có một *intrinsic lock* (monitor) dùng cho đồng bộ bằng `synchronized`. Tại một thời điểm, một monitor chỉ có tối đa một luồng sở hữu; cùng một luồng có thể vào lại (*reentrant*) monitor đó nhiều lần.

Một luồng T sở hữu monitor của đối tượng `lock` trong các tình huống sau:

| Cú pháp đang chạy | Monitor mà luồng sở hữu |
| --- | --- |
| `synchronized (lock) { ... }` | Chính đối tượng `lock` |
| `synchronized void method() { ... }` trong đối tượng `x` | Chính `x` (tức `this` của lời gọi) |
| `static synchronized void method() { ... }` của `Example` | Đối tượng `Example.class` |

Ví dụ, `synchronized (a)` không làm luồng sở hữu monitor của `b`, kể cả nếu `a.equals(b)` là `true`. Điều quan trọng là **cùng một tham chiếu đối tượng thực tế** (identity), không phải cùng giá trị theo `equals()`.

`Thread.holdsLock(lock)` cho biết **luồng hiện tại** có đang giữ intrinsic lock của `lock` hay không; phương thức này hữu ích khi kiểm tra/debug. Không sử dụng kết quả kiểm tra như cách thay cho việc khóa thật, và đừng nhầm với việc hỏi luồng khác đang giữ khóa nào.

Khi ra khỏi khối/phương thức `synchronized`, kể cả do ngoại lệ, JVM tự nhả mức sở hữu monitor tương ứng. Nhờ tính *reentrant*, cùng luồng có thể khóa lại chính monitor đó trong lời gọi lồng nhau.

## 3. Hợp đồng sở hữu của `Object.wait/notify/notifyAll`

Ba thao tác này đều được gọi trên **đối tượng đích**, nên monitor cần giữ cũng thuộc **đối tượng đích**:

| Thao tác | Yêu cầu lúc gọi | Tác động chính |
| --- | --- | --- |
| `lock.wait()` / `wait(timeout)` | Luồng hiện tại giữ monitor của `lock` | Vào tập chờ của `lock`; tạm nhả monitor của `lock`, rồi giành lại trước khi tiếp tục |
| `lock.notify()` | Luồng hiện tại giữ monitor của `lock` | Chọn một luồng đang chờ trên `lock` để đánh thức; không nhả monitor ngay |
| `lock.notifyAll()` | Luồng hiện tại giữ monitor của `lock` | Đánh thức tất cả luồng đang chờ trên `lock`; không nhả monitor ngay |

Thiếu quyền sở hữu đúng monitor khi gọi một trong ba thao tác sẽ ném `java.lang.IllegalMonitorStateException`. Đây là `RuntimeException` (*unchecked*), nên không bắt buộc khai báo `throws`. Nó phản ánh lỗi sử dụng cơ chế đồng bộ, không phải tín hiệu bình thường của một hàng đợi đang trống.

Phân biệt các trường hợp khác:
- `wait` khai báo `InterruptedException` khi luồng bị ngắt trước hoặc trong lúc đợi; đây là ngoại lệ *checked*. Sau khi giành lại monitor, lệnh chờ mới kết thúc bằng ngoại lệ này.
- `wait(long timeoutMillis)` với thời gian âm ném `IllegalArgumentException`; `wait(0)` có nghĩa chờ không giới hạn thời gian chờ, không phải chờ 0 mili giây rồi quay lại.
- `notify` không yêu cầu đang tồn tại luồng chờ; gọi hợp lệ khi không có người chờ chỉ không đánh thức ai.

### Sai: đồng bộ trên đối tượng khác

```java
Object gate = new Object();
Object unrelated = new Object();

synchronized (unrelated) {
    gate.notifyAll(); // IllegalMonitorStateException: chưa giữ monitor của gate
}
```

**Đúng:** dùng cùng đối tượng cho khóa và lời gọi:

```java
Object gate = new Object();

synchronized (gate) {
    gate.notifyAll(); // Hợp lệ, dù lúc này có thể không có luồng chờ
}
```

Tương tự, ở phương thức instance `synchronized`, `this.wait()` hợp lệ trong thời gian giữ monitor của `this`; nhưng `other.wait()` vẫn sai nếu chưa giữ monitor của `other`. Một phương thức `static synchronized` khóa `TenLop.class`, không khóa một instance cụ thể.

## 4. Chương trình Java 17+ chạy được: đúng và sai monitor

Tạo tệp `MonitorOwnershipDemo.java`:

```java
public class MonitorOwnershipDemo {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();

        System.out.println("outside=" + Thread.holdsLock(lock));

        try {
            lock.wait(1);
        } catch (IllegalMonitorStateException expected) {
            System.out.println("wait: IllegalMonitorStateException");
        }

        try {
            lock.notify();
        } catch (IllegalMonitorStateException expected) {
            System.out.println("notify: IllegalMonitorStateException");
        }

        try {
            lock.notifyAll();
        } catch (IllegalMonitorStateException expected) {
            System.out.println("notifyAll: IllegalMonitorStateException");
        }

        synchronized (lock) {
            System.out.println("inside=" + Thread.holdsLock(lock));

            lock.notifyAll(); // Hợp lệ dù không có ai đang chờ
            lock.wait(1);     // Tạm nhả lock; quay lại sau timeout hoặc thức sớm

            System.out.println("afterWait=" + Thread.holdsLock(lock));
        }

        System.out.println("afterBlock=" + Thread.holdsLock(lock));
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac MonitorOwnershipDemo.java
java MonitorOwnershipDemo
```

Kết quả chuẩn:

```text
outside=false
wait: IllegalMonitorStateException
notify: IllegalMonitorStateException
notifyAll: IllegalMonitorStateException
inside=true
afterWait=true
afterBlock=false
```

Giải thích:
1. Trước khối `synchronized`, luồng `main` chưa sở hữu monitor của `lock`; ba lời gọi phải ném ngoại lệ.
2. Khi vào `synchronized (lock)`, `Thread.holdsLock(lock)` là `true`.
3. `lock.wait(1)` có thể kết thúc do hết hạn hoặc thức sớm (*spurious wakeup*), nhưng luôn giành lại monitor của `lock` trước khi trả về bình thường. Vì thế `afterWait=true`.
4. Khi thoát khối, monitor đã được nhả, nên `afterBlock=false`. Thời gian chờ chính xác không phải một phần của kết quả cần kiểm chứng.

## 5. `wait()` nhả khóa nào? `notifyAll()` làm gì?

Xét tiến trình hai luồng cùng phối hợp qua đối tượng `gate`:

1. Luồng A vào `synchronized (gate)`, thấy điều kiện chưa thỏa rồi gọi `gate.wait()`.
2. A đi vào *wait set* gắn với `gate` và **nhả toàn bộ các mức giữ monitor của `gate`**. Nếu A còn giữ monitor của đối tượng khác, các monitor khác không tự được nhả.
3. Luồng B có thể vào `synchronized (gate)`, cập nhật trạng thái dùng chung và gọi `gate.notifyAll()`.
4. Các luồng đã được thông báo chuyển sang cạnh tranh giành monitor của `gate`; B vẫn giữ monitor tới khi thoát phần `synchronized`.
5. A chỉ tiếp tục thực thi sau khi giành lại `gate`, rồi **kiểm tra lại điều kiện**.

Do đó, một lời gọi `notify()` **không chuyển quyền sở hữu monitor ngay lập tức** sang luồng được đánh thức. `notify()` cũng không bảo đảm chọn luồng nào trong tập chờ; `notifyAll()` đánh thức mọi luồng chờ, nhưng chúng vẫn phải lần lượt giành khóa.

Không được đồng nhất `wait` với `Thread.sleep`: `sleep` không tự nhả monitor đang giữ. Nếu ngủ trong `synchronized` để chờ luồng khác sửa điều kiện cùng monitor, bạn có thể tự ngăn tiến triển.

## 6. Khóa đúng là điều kiện cần, nhưng chưa đủ

Đoạn kiểm chứng ở mục 4 chứng minh luồng có hoặc không có quyền sở hữu monitor. Trong chương trình thực tế, **quyền gọi `wait/notify` hợp lệ chưa tự bảo đảm giao thức phối hợp đúng**. Có thể giữ đúng `lock` nhưng đọc điều kiện bằng một khóa khác, không kiểm tra điều kiện sau khi tỉnh dậy, hoặc thông báo trước khi cập nhật trạng thái.

Để kiểm tra hai lớp điều kiện riêng biệt, xét bảng sau:

| Câu hỏi | Quy tắc cần xác minh |
| --- | --- |
| Có quyền gọi `lock.wait()`/ `lock.notifyAll()`? | Chính thread hiện tại đang giữ monitor của `lock`, có thể kiểm tra bằng `Thread.holdsLock(lock)` khi debug. |
| Dữ liệu điều kiện có an toàn? | Mọi lần đọc và ghi predicate chia sẻ tuân theo cùng `lock`. |
| Có bỏ lỡ thay đổi? | Kiểm tra predicate trước khi chờ và lặp lại sau mỗi lần `wait()` trở về, ngay trong `synchronized (lock)`. |
| Có đánh thức đúng nhóm cần xử lý? | Thay đổi trạng thái trước khi báo; ưu tiên `notifyAll()` nếu nhiều loại điều kiện chờ, rồi để mỗi waiter tự kiểm tra predicate. |
| Có xử lý ngắt? | Truyền `InterruptedException` hoặc khôi phục cờ ngắt và kết thúc hợp lý nếu không thể truyền lên. |

Ví dụ producer–consumer đầy đủ với vòng `while` nằm ở bài **“Dùng vòng `while` kiểm tra điều kiện trước và sau khi chờ”**. Ở đây, trọng tâm chẩn đoán là **receiver của `wait/notify` có thực sự là đối tượng đang được đồng bộ hay không**; hai đối tượng khác nhau vẫn có monitor khác nhau ngay cả khi `equals()` trả về `true`.

## 7. Chẩn đoán lỗi trong mã thật

Khi thấy stack trace tại `Object.wait`, `Object.notify` hoặc `Object.notifyAll` cùng `IllegalMonitorStateException`, kiểm tra theo thứ tự:

1. **Xác định receiver của lời gọi**: ví dụ `queueLock.wait()` cần monitor của `queueLock`, không phải mặc nhiên `this` hoặc `Thread.currentThread()`.
2. **Tìm vùng khóa đang bao quanh lời gọi**: có đúng `synchronized (queueLock)` hoặc phương thức `synchronized` trên chính receiver đó không?
3. **Kiểm tra identity**: có vô tình tạo `new Object()` cho mỗi lần gọi, thay biến khóa, khóa bản sao, hoặc dùng hai object `equals` nhau nhưng khác reference không?
4. **Xác định thread thực thi**: một thread sở hữu monitor không cấp quyền cho thread khác gọi `wait/notify` thay nó.
5. **Sau khi sửa quyền sở hữu**, rà lại điều kiện `while`, cập nhật dữ liệu và `notifyAll` dưới một monitor, tránh race condition hoặc lost notification.

Một vài bẫy tiêu biểu:

| Mẫu sai | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `lock.wait()` ngoài `synchronized(lock)` | Không sở hữu `lock` | Đặt thao tác và kiểm tra điều kiện trong `synchronized(lock)` |
| `synchronized(this) { lock.notify(); }` với `lock != this` | Đang sở hữu monitor khác | Đồng bộ trên `lock` |
| `synchronized(a) { b.wait(); }` dù `a.equals(b)` | Monitor dựa trên identity | Dùng chính `b` làm khóa |
| Chuyển `wait` sang `sleep` để “tránh exception” | `sleep` không nhả monitor, có thể kẹt luồng | Dùng `wait` dưới monitor đúng, hoặc chọn công cụ điều phối phù hợp |
| `if (!ready) lock.wait()` | Không xử lý thức dậy giả / trạng thái đổi | `while (!ready) lock.wait()` |
| Chỉ `notify()` khi nhiều loại điều kiện cùng đợi | Có thể đánh thức luồng không thể tiến triển | Cân nhắc `notifyAll()` và kiểm tra lại điều kiện |

### Ranh giới với `java.util.concurrent.locks`

`ReentrantLock`/`Condition` có quy tắc sở hữu riêng: `Condition.await()`/`signal()` cần giữ đúng `ReentrantLock` gắn với condition. Giữ `ReentrantLock` **không** tự đồng nghĩa với sở hữu intrinsic monitor của một `Object` để gọi `wait/notify`. Đừng trộn hai giao thức khóa trên cùng một điều kiện trạng thái.

## 8. Tự kiểm tra

1. Trong `synchronized (a) { b.notifyAll(); }`, điều gì quyết định lời gọi có hợp lệ? **Đáp án:** luồng hiện tại phải đồng thời giữ monitor của `b`; chỉ giữ `a` không đủ nếu `a` và `b` khác đối tượng.
2. `static synchronized` của `Demo` khóa đối tượng nào? **Đáp án:** `Demo.class`.
3. Luồng vừa được `notify` có chạy ngay không? **Đáp án:** không; trước tiên phải giành lại monitor sau khi chủ hiện tại nhả.
4. Tại sao `wait()` phải đặt trong `while`? **Đáp án:** trạng thái cần kiểm tra lại sau thức dậy giả, cạnh tranh giành khóa hoặc thay đổi từ luồng khác.
5. `IllegalMonitorStateException` có phải `InterruptedException` không? **Đáp án:** không; lỗi thứ nhất là vi phạm sở hữu monitor, lỗi thứ hai báo chờ bị ngắt.

**Ghi nhớ:** `synchronized (x)` trao quyền sở hữu monitor **của chính `x` cho luồng đang chạy**. Khi gọi `x.wait()`, `x.notify()` hay `x.notifyAll()`, phải đang giữ chính monitor đó; sau thức dậy, luôn kiểm tra lại điều kiện của trạng thái chung.

## Tài liệu tham khảo

- [Java SE 21 — `Object` (`wait`, `notify`, `notifyAll`)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html).
- [Java SE 21 — `IllegalMonitorStateException`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/IllegalMonitorStateException.html).
- [Java SE 21 — `Thread.holdsLock`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#holdsLock(java.lang.Object)).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-monitor-004">wait() nhả monitor của đối tượng đang chờ và phải giành lại trước khi trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Object.wait(): nhả monitor khi chờ và phải giành lại trước khi tiếp tục

## 1. Ý nghĩa cốt lõi

Trong Java, mỗi đối tượng có một **intrinsic lock (monitor)** dùng bởi `synchronized`. Khi một luồng đang sở hữu monitor của đối tượng `lock` và gọi `lock.wait()`:

1. Luồng **tạm nhả toàn bộ quyền sở hữu monitor của chính `lock`** rồi đi vào trạng thái chờ. Luồng khác nhờ đó có thể vào `synchronized (lock)` để thay đổi điều kiện và phát thông báo.
2. Khi được thông báo, bị ngắt, hoặc tự thức dậy không có thông báo (spurious wakeup), luồng **chưa thể chạy tiếp ngay**. Nó phải **giành lại monitor của `lock`**; chỉ sau khi giành được monitor, `wait()` mới trả về bình thường hoặc ném `InterruptedException`.
3. Vì vậy, đoạn mã ngay sau `wait()` bên trong cùng khối `synchronized (lock)` luôn chạy khi luồng đã sở hữu lại monitor đó.

Điểm dễ nhầm nhất: **`notify()` / `notifyAll()` không lập tức chuyển monitor cho luồng đang chờ**. Luồng thông báo vẫn giữ monitor đến lúc thoát `synchronized` (hoặc nhả bằng cơ chế thích hợp).

## 2. Điều kiện tiên quyết: chờ trên đúng monitor đang sở hữu

Các phương thức `wait()`, `notify()` và `notifyAll()` thuộc `java.lang.Object`. Luồng gọi phải đang sở hữu **monitor của đúng đối tượng nhận lời gọi**:

```java
Object lock = new Object();

synchronized (lock) {
    lock.wait(); // Hợp lệ: đang sở hữu monitor của lock.
}
```

Đây là một *đoạn minh họa*, cần đặt trong phương thức xử lý hoặc khai báo `InterruptedException`; bản thân đoạn này sẽ chờ vô hạn nếu không có sự kiện đánh thức phù hợp.

Ngược lại:

```java
Object a = new Object();
Object b = new Object();

synchronized (a) {
    b.wait(); // IllegalMonitorStateException: không sở hữu monitor của b.
}
```

Quy tắc tương tự áp dụng cho `b.notify()` và `b.notifyAll()`. `synchronized(this)` cũng không trao quyền gọi `someOtherObject.wait()`, trừ khi `this` chính là đối tượng đó.

Monitor của `synchronized` trên phương thức thể hiện (instance synchronized method) là `this`; monitor của phương thức `static synchronized` là đối tượng `Class` tương ứng. Không được nhầm hai monitor đó.

## 3. Diễn tiến từ lúc chờ đến lúc tiếp tục

Giả sử hai luồng cùng dùng một `lock` và một biến điều kiện `ready`, đều chỉ đọc/ghi `ready` trong `synchronized (lock)`:

| Thời điểm | Luồng A (đang chờ) | Luồng B (đánh thức) | Monitor `lock` |
| --- | --- | --- | --- |
| 1 | Vào `synchronized (lock)`, thấy `ready == false` | Chưa vào | A sở hữu |
| 2 | Gọi `lock.wait()`: nhả monitor và chờ | Có thể tranh quyền vào | Được giải phóng |
| 3 | Tiếp tục chờ | Vào `synchronized (lock)`, đặt `ready = true` | B sở hữu |
| 4 | Được `notifyAll()` đánh thức nhưng chưa thể vượt qua `wait()` | Gọi `lock.notifyAll()` và vẫn ở trong khối khóa | B vẫn sở hữu |
| 5 | Cạnh tranh để giành lại monitor | Thoát `synchronized (lock)` | Được giải phóng |
| 6 | Giành lại monitor, `wait()` trả về, kiểm tra lại `ready` | Đã rời khối khóa | A sở hữu |

`notify()` chỉ chọn một luồng đang chờ trên đối tượng (không bảo đảm chọn luồng nào); `notifyAll()` đánh thức tất cả luồng đang chờ trên đối tượng. **Các luồng được đánh thức vẫn phải lần lượt cạnh tranh lấy monitor**, không được chạy song song trong cùng một monitor. Thứ tự giành monitor và thời điểm chạy không được bảo đảm.

Với `wait()` không thời hạn, Java thường biểu diễn luồng đang chờ là `WAITING`. Với `wait(timeout)`, luồng có thể ở `TIMED_WAITING`. Trong giai đoạn cố giành lại monitor, luồng có thể ở `BLOCKED`. Đây là mô hình để hiểu cơ chế, không nên viết chương trình dựa vào việc quan sát một ảnh chụp `Thread.State` chính xác từng thời điểm.

## 4. Chương trình Java 17+ kiểm chứng được

Lưu thành tệp `MonitorWaitDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class MonitorWaitDemo {
    private static final Object MONITOR = new Object();
    private static boolean ready;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch waiterEntered = new CountDownLatch(1);

        Thread waiter = new Thread(() -> {
            synchronized (MONITOR) {
                System.out.println("waiter: before wait");
                waiterEntered.countDown();

                try {
                    while (!ready) {
                        MONITOR.wait();
                    }
                    System.out.println("waiter: resumed, owns monitor");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("waiter: interrupted, owns monitor");
                }
            }
        }, "waiter");

        waiter.start();
        waiterEntered.await();

        synchronized (MONITOR) {
            System.out.println("main: acquired monitor");
            ready = true;
            MONITOR.notifyAll();
            System.out.println("main: notified, still owns monitor");
        }

        waiter.join();
    }
}
```

Biên dịch và chạy:

```bash
javac MonitorWaitDemo.java
java MonitorWaitDemo
```

Kết quả khi không có sự kiện ngắt từ bên ngoài:

```text
waiter: before wait
main: acquired monitor
main: notified, still owns monitor
waiter: resumed, owns monitor
```

**Vì sao thứ tự này có thể kiểm chứng?**

- `waiter` in dòng đầu khi đang giữ `MONITOR`. `CountDownLatch` chỉ bảo đảm luồng `main` không bắt đầu bước tranh monitor trước khi luồng chờ đi đến điểm đã báo hiệu; nó không thay thế cơ chế `wait/notifyAll`.
- Khi `main` in `main: acquired monitor`, nó đã giành được monitor mà `waiter` từng giữ. Trong mã này, `waiter` đã nhả monitor qua `wait()`; nếu không nhả, `main` không thể vào.
- Dù `main` đã gọi `notifyAll()`, dòng `waiter: resumed, owns monitor` chỉ được in **sau khi `main` rời khối `synchronized`** và `waiter` giành lại monitor.
- `ready` được đọc/ghi dưới cùng một monitor. Vòng `while` kiểm tra **điều kiện thực tế**, không giả định rằng một lần nhận thông báo luôn đồng nghĩa điều kiện đúng.
- `waiter.join()` giúp `main` chờ luồng con hoàn tất trước khi kết thúc. Không sử dụng `Thread.sleep()` để phỏng đoán thời điểm luồng khác chạy.

Nhánh `InterruptedException` trong ví dụ chỉ chạy nếu luồng chờ bị ngắt, nên không thuộc kết quả bình thường vừa liệt kê. Ngay trong `catch` ấy, `waiter` cũng đã giành lại `MONITOR`; sau đó ví dụ khôi phục cờ ngắt bằng `Thread.currentThread().interrupt()`.

## 5. Tại sao phải kiểm tra điều kiện bằng `while`?

`wait()` chờ **sự thay đổi điều kiện**, nhưng bản thân nó không mang theo điều kiện và không cam kết điều kiện đã đúng khi trả về. Nó có thể thức dậy vì một thông báo dành cho nhiều luồng, vì một thông báo không liên quan đến điều kiện của luồng cụ thể, hoặc do *spurious wakeup*. Giữa lúc được thông báo và lúc giành lại monitor, luồng khác cũng có thể thay đổi trạng thái.

Mẫu thông dụng:

```java
synchronized (lock) {
    while (!condition) {
        lock.wait();
    }

    // condition đã được kiểm tra khi đang giữ monitor lock.
    useSharedState();
}
```

Đây là **mã minh họa**, với `condition` và `useSharedState()` do chương trình cung cấp; phương thức chứa mã cần xử lý `InterruptedException`. Bên phát tín hiệu thay đổi điều kiện trong **cùng monitor**:

```java
synchronized (lock) {
    condition = true;
    lock.notifyAll();
}
```

Không dùng `if (!condition) lock.wait();` như thể một lần thức dậy là đủ. Cũng không gọi `wait()` trước rồi mới kiểm tra điều kiện: nếu tín hiệu đã đến trước đó, luồng có thể chờ vô hạn. Kiểm tra điều kiện và thao tác `wait()` cần nằm trong cùng vùng đồng bộ để không bỏ lỡ thay đổi ở khoảng trống giữa hai thao tác.

Một lời gọi `notifyAll()` **không lưu tín hiệu** để cấp cho luồng gọi `wait()` trong tương lai. Biến điều kiện là trạng thái có ý nghĩa; thông báo chỉ khiến các luồng xem xét lại trạng thái đó.

## 6. Những gì được nhả — và không được nhả

`lock.wait()` nhả **chỉ intrinsic monitor của đối tượng `lock`**. Nó không nhả một monitor khác mà luồng tình cờ đang giữ, và không tự động nhả `ReentrantLock` hoặc tài nguyên ngoài Java.

```java
synchronized (outer) {
    synchronized (lock) {
        lock.wait(); // Nhả lock, nhưng vẫn giữ monitor của outer.
    }
}
```

Đoạn này minh họa nguy cơ deadlock: nếu luồng muốn thay đổi điều kiện cần vào `synchronized (outer)`, nó có thể bị chặn bởi luồng đang chờ. Tránh giữ các khóa không liên quan xuyên qua một lần chờ.

Nếu luồng đã vào monitor của **cùng đối tượng** nhiều lần do khóa có tính tái nhập (reentrant), `wait()` nhả **toàn bộ số lần sở hữu monitor đó** khi chờ; trước khi tiếp tục, luồng phải giành lại monitor và khôi phục mức sở hữu tương ứng. Vì vậy `wait()` không chỉ giảm một mức đếm khóa.

## 7. Các cách `wait()` kết thúc và tác động của interruption

| Tình huống | Điều diễn ra |
| --- | --- |
| `notify()` hoặc `notifyAll()` | Luồng đang chờ được chọn/đánh thức, rồi **đợi giành lại monitor** mới trả về từ `wait()`. |
| `wait(timeout)` hết hạn | Luồng thức dậy khi hết thời gian chờ, nhưng vẫn phải **giành lại monitor** trước khi trả về. Không bảo đảm trả về đúng thời điểm hết hạn nếu monitor vẫn bị giữ. |
| Spurious wakeup | `wait()` có thể trả về mà không có tín hiệu hay timeout; vẫn phải giành lại monitor và kiểm tra điều kiện. |
| Luồng bị `interrupt()` | Luồng phải giành lại monitor trước khi `wait()` ném `InterruptedException`. Thông thường trạng thái ngắt được xóa khi ném ngoại lệ; mã bắt ngoại lệ quyết định xử lý hay đặt lại cờ ngắt. |

Các overload liên quan: `wait()` chờ không có thời hạn định trước; `wait(long timeoutMillis)` và `wait(long timeoutMillis, int nanos)` có thời hạn, với `timeoutMillis == 0` và `nanos == 0` nghĩa là chờ không thời hạn. Thời gian thức dậy thực tế phụ thuộc lịch chạy và cạnh tranh monitor. Sau bất kỳ cách thức dậy nào, **điều kiện phải được kiểm tra lại**.

Ví dụ kiểm tra bằng API chuẩn ngay sau khi `wait()` trở về:

```java
synchronized (lock) {
    while (!condition) {
        lock.wait();
        assert Thread.holdsLock(lock);
    }
}
```

`Thread.holdsLock(lock)` trả về `true` khi luồng hiện tại đang giữ intrinsic monitor của `lock`. Đoạn này là mã minh họa; có thể bật Java assertions bằng tùy chọn `-ea`. Trong đường ném `InterruptedException`, quyền sở hữu monitor cũng đã được phục hồi trước khi luồng đi vào `catch` nằm trong cùng khối `synchronized`.

## 8. So sánh với thao tác gần giống

| Thao tác | Có đòi sở hữu monitor của đối tượng nhận lời gọi? | Có nhả monitor khi chờ? | Điểm cần nhớ |
| --- | --- | --- | --- |
| `lock.wait()` | Có | **Có, của chính `lock`** | Phải giành lại trước khi trở về/ném ngoại lệ. |
| `lock.notify()` / `lock.notifyAll()` | Có | **Không** | Đánh thức luồng chờ; luồng phát thông báo vẫn sở hữu monitor cho đến khi nhả khóa. |
| `Thread.sleep(...)` | Không | **Không** | Chỉ tạm dừng luồng hiện tại; không thả những monitor mà luồng đang giữ. |
| `Condition.await()` cùng `ReentrantLock` | Phải giữ khóa liên kết với `Condition` | Nhả và tái giành **khóa liên kết**, không phải intrinsic monitor của `Object` | Cơ chế của `java.util.concurrent.locks`, không trộn trực tiếp với `Object.wait()`. |

## 9. Lỗi thường gặp và cách tránh

1. **Gọi trên sai đối tượng**: `synchronized (a)` nhưng gọi `b.wait()` hoặc `b.notifyAll()` gây `IllegalMonitorStateException`. Đồng bộ và gọi phương thức trên đúng cùng một đối tượng.
2. **Tưởng `notifyAll()` giao monitor ngay**: luồng chờ không chạy tiếp cho đến khi luồng phát tín hiệu nhả monitor và nó giành được lại.
3. **Chờ bên trong `if`**: lần thức dậy không bảo đảm điều kiện đúng. Dùng `while` và bảo vệ cả đọc/ghi trạng thái bằng cùng monitor.
4. **Gọi `wait()` dù điều kiện đã đúng**: tín hiệu không được lưu thành hàng đợi. Kiểm tra điều kiện trước khi quyết định chờ.
5. **Dùng `sleep()` để “nhường khóa”**: `Thread.sleep()` vẫn giữ monitor, dễ khiến luồng phát tín hiệu không vào được khối `synchronized`.
6. **Giữ monitor khác khi chờ**: `wait()` không nhả những monitor còn lại; có thể làm chuỗi luồng mắc kẹt.
7. **Nuốt `InterruptedException` mà không cân nhắc hủy tác vụ**: chọn cách xử lý phù hợp; khi không xử lý được tại chỗ, có thể khôi phục cờ ngắt hoặc truyền ngoại lệ lên tầng gọi.

## 10. Tự kiểm tra kiến thức

**Tình huống A.** Luồng A ở trong `synchronized (lock)` và gọi `lock.wait()`. Luồng B có thể vào `synchronized (lock)` không? **Có**, vì A đã nhả monitor đó khi chờ.

**Tình huống B.** B đã gọi `lock.notifyAll()` nhưng chưa ra khỏi `synchronized (lock)`. A có thể in dòng ngay sau `lock.wait()` không? **Không**, vì A còn phải giành lại monitor mà B vẫn đang giữ.

**Tình huống C.** A đang giữ cả `outer` và `lock`, sau đó gọi `lock.wait()`. Luồng khác có thể giành `outer` không? **Không**, vì `wait()` chỉ nhả monitor của `lock`.

**Tình huống D.** `wait()` trả về sau một thông báo có đồng nghĩa `condition == true` không? **Không**. Phải kiểm tra lại điều kiện trong `while` khi đã có monitor.

**Ghi nhớ:** `wait()` **nhả đúng monitor của đối tượng đang chờ để luồng khác có cơ hội thay đổi trạng thái**; khi chấm dứt việc chờ, luồng **bắt buộc giành lại monitor ấy trước khi tiếp tục**. Đây là lý do mẫu `synchronized` + điều kiện `while` + `wait/notifyAll` hoạt động an toàn khi tất cả truy cập trạng thái chung tuân theo cùng một khóa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-monitor-005">Thức dậy không bảo đảm điều kiện đã đúng</a>

<details>
<summary>Xem chi tiết</summary>

# Thức dậy không bảo đảm điều kiện đã đúng

Trong cơ chế monitor của `Object`, một luồng gọi `wait()` vì **trạng thái dùng chung chưa thỏa mãn một điều kiện**. Khi `wait()` kết thúc bình thường, luồng chỉ biết mình đã có cơ hội kiểm tra lại trạng thái. Nó **không được phép suy ra điều kiện đã đúng** chỉ vì vừa được đánh thức.

Quy tắc cốt lõi: **luôn kiểm tra điều kiện trong vòng `while`, dưới cùng monitor bảo vệ dữ liệu đó**.

## 1. `wait()` và `notifyAll()` thực sự làm gì?

Giả sử hai luồng phối hợp qua một đối tượng `monitor` và cờ `ready`:

1. Luồng chờ giữ khóa monitor qua `synchronized (monitor)`, kiểm tra `ready`. Nếu `false`, nó gọi `monitor.wait()` và **nhả khóa monitor** trong lúc chờ.
2. Luồng khác lấy khóa đó, có thể cập nhật trạng thái, rồi gọi `monitor.notify()` hoặc `monitor.notifyAll()` khi đang giữ khóa.
3. Luồng được thông báo chỉ có thể tiếp tục từ `wait()` **sau khi lấy lại khóa monitor**. Trong khoảng từ lúc được thông báo tới lúc lấy lại khóa, trạng thái dùng chung có thể đã thay đổi.
4. Luồng chờ phải đọc lại `ready` khi đã giữ khóa; nếu vẫn `false`, nó chờ tiếp.

`notify()` chọn một luồng đang chờ trên monitor, còn `notifyAll()` thông báo cho tất cả luồng đang chờ trên monitor; **cả hai không gán giá trị cho `ready`, không xác nhận điều kiện và không trao khóa ngay lập tức**. Các luồng được đánh thức tiếp tục cạnh tranh để lấy monitor.

### Những nguyên nhân điều kiện vẫn sai sau khi thức dậy

- **Tín hiệu không đồng nghĩa điều kiện đúng:** luồng phát tín hiệu có thể gọi `notifyAll()` để báo rằng *có thay đổi*, nhưng thay đổi đó chưa đủ cho điều kiện của luồng chờ.
- **Luồng khác đến trước:** sau khi được thông báo, một luồng khác có thể lấy monitor, tiêu thụ phần tử cuối trong hàng đợi hoặc thay đổi trạng thái, rồi nhả khóa trước khi luồng đang thức dậy lấy được khóa.
- **Spurious wakeup (thức dậy tự phát):** Java cho phép `wait()` trở về bình thường dù không có thông báo tương ứng. Chương trình đúng phải xử lý trường hợp này.
- **Hết thời gian chờ:** với `wait(timeout)`, hàm có thể trở về do hết hạn; điều kiện vẫn cần kiểm tra.

Nếu bị ngắt khi chờ, `wait()` ném `InterruptedException` sau khi lấy lại monitor. Đây là nhánh xử lý ngắt riêng, không phải bằng chứng điều kiện đã thỏa mãn.

## 2. Sai với `if`, đúng với `while`

Ví dụ dưới đây là **đoạn mã minh họa** đặt bên trong một phương thức của lớp có `monitor` và `ready`:

```java
// SAI: chỉ kiểm tra điều kiện một lần.
synchronized (monitor) {
    if (!ready) {
        monitor.wait();
    }
    useResource(); // Có thể chạy khi ready vẫn false.
}
```

Ngay cả khi `ready` là `false` lúc bắt đầu chờ, `wait()` có thể trở về mà `ready` vẫn `false`. Lệnh sử dụng tài nguyên vì vậy có thể chạy sai.

```java
// ĐÚNG: điều kiện được kiểm tra lại mỗi lần quay về từ wait().
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
    useResource(); // Tại thời điểm giữ monitor này, ready == true.
}
```

Điểm quan trọng nằm ở **vòng lặp kiểm tra điều kiện**, không nằm ở số lần nhận `notify`. Mỗi lần quay về từ `wait()`, `while` đọc trạng thái mới dưới khóa; nếu điều kiện vẫn sai, luồng lại chờ. Luồng cập nhật `ready` cũng phải dùng **chính monitor này**, giữ tính nhất quán và quan hệ hiển thị bộ nhớ của `synchronized`.

## 3. Ví dụ Java 17+ có thể chạy

Chương trình sau chủ động gọi `notifyAll()` một lần **khi `ready` vẫn `false`**, rồi mới cập nhật `ready = true` và thông báo lần nữa. Hai `CountDownLatch` chỉ giúp điều phối thời điểm trong bản trình diễn; dữ liệu `ready` vẫn được bảo vệ bằng monitor của `Object`.

Lưu vào **`WaitPredicateDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class WaitPredicateDemo {
    private static final Object monitor = new Object();
    private static boolean ready = false;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch firstWait = new CountDownLatch(1);
        CountDownLatch secondWait = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            synchronized (monitor) {
                int attempt = 0;
                while (!ready) {
                    attempt++;
                    System.out.println("worker: ready=false, wait #" + attempt);

                    if (attempt == 1) {
                        firstWait.countDown();
                    } else if (attempt == 2) {
                        secondWait.countDown();
                    }

                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("worker: bị ngắt, kết thúc");
                        return;
                    }
                }

                System.out.println("worker: ready=true, tiếp tục");
            }
        });

        worker.start();
        firstWait.await();

        synchronized (monitor) {
            System.out.println("main: notifyAll khi ready vẫn false");
            monitor.notifyAll();
        }

        secondWait.await();
        synchronized (monitor) {
            ready = true;
            System.out.println("main: ready=true rồi notifyAll");
            monitor.notifyAll();
        }

        worker.join();
    }
}
```

Biên dịch và chạy:

```bash
javac WaitPredicateDemo.java
java WaitPredicateDemo
```

Đầu ra minh họa trong một lần chạy thông thường (không có lần thức dậy tự phát phụ):

```text
worker: ready=false, wait #1
main: notifyAll khi ready vẫn false
worker: ready=false, wait #2
main: ready=true rồi notifyAll
worker: ready=true, tiếp tục
```

**Diễn giải:** sau `notifyAll()` đầu tiên, luồng `worker` lấy lại monitor, thấy `ready` vẫn `false` và vào `wait()` thêm lần nữa. Chỉ khi luồng `main` đổi `ready` thành `true` dưới monitor thì `worker` mới qua được điều kiện của `while`. Spurious wakeup có thể làm xuất hiện thêm một số lần chờ, nhưng không khiến `worker` đi qua vòng lặp khi `ready` còn sai.

## 4. Phân biệt nguyên nhân thức dậy với trạng thái cho phép chạy

Giả sử có nhiều waiter cùng chờ `ready == true`. Mỗi nguyên nhân thức dậy chỉ đưa luồng trở lại **điểm kiểm tra predicate**; không nguyên nhân nào thay thế được bước kiểm tra đó.

| Nguyên nhân `wait()` kết thúc bình thường | `ready` có chắc `true`? | Cách `while` xử lý |
| --- | --- | --- |
| `notifyAll()` khi `ready` vẫn `false` | Không | Giành lại monitor rồi chờ tiếp. |
| `notifyAll()` sau khi `ready` đã `true` | Không chắc ở thời điểm waiter lấy lại khóa; thread khác có thể đổi trạng thái | Đọc lại `ready`, chỉ đi tiếp nếu đúng. |
| Spurious wakeup | Không | Kiểm tra lại và chờ tiếp nếu điều kiện vẫn sai. |
| `wait(timeout)` hết hạn | Không | Kiểm tra trạng thái và ngân sách chờ theo hợp đồng timeout của caller. |

Một trường hợp đáng chú ý là **nhiều nhóm waiter chờ các điều kiện khác nhau trên cùng monitor**: producer chờ bộ đệm chưa đầy, consumer chờ bộ đệm không rỗng. `notifyAll()` đánh thức cả hai nhóm, nhưng mỗi luồng chỉ được làm việc khi predicate của nhóm mình đang đúng. Ví dụ Java hoàn chỉnh cho producer–consumer và giải thích chi tiết vòng `while` nằm ở bài **“Dùng vòng `while` kiểm tra điều kiện trước và sau khi chờ”**.

Nếu `wait()` ném `InterruptedException`, luồng đi vào **nhánh xử lý ngắt** sau khi giành lại monitor; đó không phải một lần trở về bình thường để coi điều kiện đã thỏa.

## 5. Checklist khi viết code với monitor

- **Gắn chờ với điều kiện cụ thể:** định nghĩa predicate, chẳng hạn `queue.isEmpty()` hoặc `!ready`, và kiểm tra lại predicate bằng `while`.
- **Một khóa cho việc kiểm tra, chờ và sửa trạng thái:** gọi `wait()`, `notify()` hay `notifyAll()` chỉ khi đang sở hữu monitor tương ứng; nếu không Java ném `IllegalMonitorStateException`.
- **Thay đổi trạng thái trước khi thông báo:** dưới cùng monitor, cập nhật dữ liệu chia sẻ rồi `notifyAll()` khi thay đổi có thể cho phép luồng khác tiến tiếp. Chỉ thông báo mà không cập nhật predicate có thể khiến tất cả lại chờ.
- **Không đặt công việc phụ thuộc điều kiện ngay sau `wait()` mà không kiểm tra lại.** Nếu `wait()` có timeout, vẫn phải xét predicate và chính sách hết hạn; timeout không đồng nghĩa thành công.
- **Xử lý ngắt có chủ đích:** nếu không thể tiếp tục khi `InterruptedException` xuất hiện, có thể khôi phục cờ ngắt với `Thread.currentThread().interrupt()` rồi kết thúc; không âm thầm nuốt ngắt.
- **Ưu tiên cấu trúc đồng bộ cấp cao khi phù hợp:** `BlockingQueue`, `CountDownLatch`, `Semaphore` hoặc `Condition` có thể giúp diễn đạt mục tiêu rõ hơn; nếu dùng `Condition.await()`, việc chờ điều kiện thông thường cũng cần vòng lặp kiểm tra predicate.

**Ghi nhớ:** tín hiệu đánh thức chỉ tạo *cơ hội kiểm tra lại*. **Điều kiện trên trạng thái dùng chung, được đọc trong `while` khi giữ đúng khóa, mới cho phép luồng tiếp tục.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-monitor-006">Dùng vòng while kiểm tra điều kiện trước và sau khi chờ</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng vòng `while` kiểm tra điều kiện trước và sau khi `wait()`

## 1. Vấn đề cần giải quyết

Khi nhiều thread cùng truy cập một đối tượng, một thread có thể phải **chờ trạng thái chung thỏa điều kiện** rồi mới tiếp tục. Ví dụ:

- Consumer chỉ được lấy dữ liệu khi bộ đệm **không rỗng**.
- Producer chỉ được thêm dữ liệu khi bộ đệm **chưa đầy**.
- Thread xử lý chỉ được chạy khi một cờ trạng thái đã chuyển thành `ready == true`.

Trong cơ chế **intrinsic monitor** của Java, thread có thể gọi `Object.wait()` để tạm ngừng và nhường monitor, còn thread khác dùng `notify()` hoặc `notifyAll()` để thông báo trạng thái có thể đã đổi. Tuy nhiên, **được đánh thức không đồng nghĩa với điều kiện đã đúng**.

Quy tắc quan trọng là:

> **Luôn kiểm tra điều kiện trong vòng `while` khi dùng `wait()`.** Vòng lặp kiểm tra trước lần chờ đầu tiên và kiểm tra lại sau mỗi lần `wait()` trả về.

```java
synchronized (lock) {
    while (!conditionIsTrue()) {
        lock.wait();
    }
    // Vẫn giữ monitor của lock; điều kiện vừa được kiểm tra lại.
    performOperationThatRequiresCondition();
}
```

Đây là đoạn minh họa khuôn mẫu: `lock`, `conditionIsTrue()` và `performOperationThatRequiresCondition()` cần được định nghĩa trong chương trình thực tế.

## 2. `while` bảo vệ điều kiện như thế nào?

Một lời gọi `wait()` trên đối tượng `lock` có các đặc điểm chính:

1. Thread **phải đang sở hữu monitor của chính `lock`**, thường bằng `synchronized (lock)` hoặc một phương thức `synchronized` của `lock`. Nếu không, Java ném `IllegalMonitorStateException`.
2. Khi thực sự chờ, `lock.wait()` đưa thread vào wait set của `lock` và **nhả monitor của `lock`**, cho thread khác cơ hội thay đổi trạng thái. Nó không tự động nhả các monitor khác mà thread đang giữ.
3. Một thread chờ có thể được đánh thức bởi `notify()`, `notifyAll()`, ngắt (`interrupt`), hết thời gian đối với `wait(timeout)`, hoặc **spurious wakeup** (thức dậy không có thông báo tương ứng).
4. Trước khi `wait()` hoàn tất, thread phải **giành lại monitor của `lock`**. Khi đó, thread khác có thể đã sửa trạng thái một lần nữa.
5. Vì vậy, sau khi trở lại từ `wait()`, `while` đọc lại điều kiện **dưới cùng monitor**. Nếu chưa đạt, thread tiếp tục chờ; nếu đã đạt, thread thực hiện thao tác ngay trong miền đồng bộ đó.

Cần phân biệt: `notifyAll()` làm các thread đang chờ đủ điều kiện tranh chấp monitor; **thread gọi `notifyAll()` vẫn giữ monitor cho tới khi rời khối/phương thức `synchronized`**. Một lời thông báo là tín hiệu *hãy kiểm tra lại*, không phải lời cam kết *bạn chắc chắn có quyền tiếp tục*.

### Vì sao `if` không an toàn?

```java
synchronized (lock) {
    if (queue.isEmpty()) {
        lock.wait();
    }
    return queue.removeFirst(); // Có thể lỗi nếu queue vẫn rỗng.
}
```

Giả sử consumer A và B cùng đang đợi dữ liệu. Producer thêm **một** phần tử rồi gọi `notifyAll()`. A giành monitor trước và lấy phần tử; B giành monitor sau. Với `if`, B đi thẳng đến `removeFirst()` mặc dù queue đã rỗng. Với `while`, B thấy điều kiện chưa đạt và quay lại `wait()`.

Ngay cả khi chỉ có một consumer, spurious wakeup cũng đủ làm cho `if` không đúng. Do đó, chuyển `if` thành `while` là yêu cầu **đúng đắn về logic**, không chỉ là biện pháp phòng lỗi hiếm gặp.

## 3. Ví dụ Java 17+: bộ đệm một phần tử

Bộ đệm chỉ giữ tối đa một số nguyên. Producer phải chờ khi ô đã có dữ liệu; consumer phải chờ khi ô còn rỗng. Cả hai dùng **monitor của chính đối tượng `Slot`** vì các phương thức đều là `synchronized`; lời gọi `wait()` và `notifyAll()` ở đây tương đương `this.wait()` và `this.notifyAll()`.

Lưu đoạn sau vào tệp **`WaitWhileDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class WaitWhileDemo {
    static final class Slot {
        private Integer value; // null nghĩa là ô đang trống.

        public synchronized void put(int next) throws InterruptedException {
            while (value != null) {
                wait(); // Nhả monitor của Slot khi phải chờ ô trống.
            }

            value = next; // Thay đổi trạng thái khi vẫn giữ monitor.
            notifyAll(); // Báo cho các thread đang chờ kiểm tra lại.
        }

        public synchronized int take() throws InterruptedException {
            while (value == null) {
                wait(); // Nhả monitor của Slot khi phải chờ dữ liệu.
            }

            int result = value;
            value = null; // Giải phóng ô cho lần put tiếp theo.
            notifyAll();
            return result;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Slot slot = new Slot();
        List<Integer> received = new ArrayList<>();

        Thread producer = new Thread(() -> {
            try {
                for (int n = 1; n <= 3; n++) {
                    slot.put(n);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int n = 0; n < 3; n++) {
                    received.add(slot.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println(received);
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac WaitWhileDemo.java
java WaitWhileDemo
```

Đầu ra khi không có thread nào bị ngắt ngoài ý muốn:

```text
[1, 2, 3]
```

Thứ tự lập lịch producer/consumer có thể thay đổi, nhưng các giá trị được truyền qua `Slot` theo thứ tự `1, 2, 3`. Chương trình chỉ in kết quả **sau cả hai `join()`**, nên đầu ra không phụ thuộc thứ tự các thông báo hay thời điểm chạy của từng thread. Danh sách `received` chỉ được consumer ghi và được main đọc sau `join()`.

**Phân tích điều kiện bảo vệ:**

| Phương thức | Điều kiện phải chờ | Điều kiện để tiếp tục | Trạng thái thay đổi |
| --- | --- | --- | --- |
| `put(next)` | `value != null` | `value == null` | `value = next` |
| `take()` | `value == null` | `value != null` | `value = null` |

Trong cả hai phương thức, việc **kiểm tra điều kiện → đọc/sửa trạng thái → gửi thông báo** diễn ra dưới cùng monitor. Nếu đọc điều kiện bên ngoài `synchronized` rồi mới gọi `wait()`, một thay đổi có thể lọt vào khoảng trống giữa hai thao tác, khiến thread bỏ lỡ tín hiệu và chờ vô hạn.

Đoạn demo phục hồi cờ interrupt trong các hàm `Runnable` vì `Runnable.run()` không khai báo `throws InterruptedException`. Trong ứng dụng thực tế cần thiết kế cơ chế dừng và lan truyền lỗi của các worker; ví dụ cố ý tập trung vào vòng chờ, không mô phỏng hủy tác vụ.

## 4. Vòng lặp hoạt động trước và sau lần chờ ra sao?

Xét consumer đang gọi `take()`:

1. Consumer chiếm monitor và kiểm tra `value == null`. Nếu đã có dữ liệu, **không gọi `wait()`**.
2. Nếu trống, consumer gọi `wait()`, nhả monitor và vào trạng thái chờ.
3. Producer vào `put()`, đặt `value` rồi gọi `notifyAll()` trong lúc giữ monitor.
4. Producer ra khỏi phương thức; consumer mới có cơ hội giành lại monitor để `wait()` trả về.
5. Consumer chạy **lại điều kiện `value == null` ở đầu `while`**. Nếu thread khác đã lấy dữ liệu, nó tiếp tục chờ. Nếu có dữ liệu, nó lấy giá trị, đặt `value = null`, thông báo và trả về.

Điểm mấu chốt: **không cần viết hai vòng `while` riêng biệt**. Một vòng `while` bao quanh `wait()` vừa kiểm tra *trước* lần chờ đầu, vừa kiểm tra *sau* mỗi lần thức dậy.

Các thread không được bảo đảm lần lượt giành monitor theo thứ tự đã chờ. `notifyAll()` không tạo ra fairness, không tự chuyển quyền sở hữu monitor cho consumer cụ thể và không thay thế kiểm tra predicate.

## 5. `notify()` và `notifyAll()` liên quan gì tới `while`?

- `notify()` chọn **một** thread trong wait set của monitor, nhưng không bảo đảm chọn thread đang đợi đúng loại điều kiện vừa trở thành true.
- `notifyAll()` đánh thức **tất cả** thread đang đợi monitor đó. Từng thread phải cạnh tranh lại để vào `synchronized`, rồi `while` quyết định thread nào thực sự có thể tiến hành.
- Khi nhiều kiểu thread cùng chờ trên một monitor, chẳng hạn producer chờ **not-full** và consumer chờ **not-empty**, dùng `notifyAll()` thường là lựa chọn đơn giản và an toàn hơn. `notify()` chọn nhầm nhóm thread có thể làm hệ thống tiếp tục chờ dù có thao tác khả thi.
- Dùng `notifyAll()` **không** cho phép thay `while` bằng `if`: nhiều thread có thể cùng thức dậy nhưng chỉ một thread có điều kiện phù hợp ở thời điểm lấy monitor.

Để tránh đánh thức nhiều thread không cần thiết ở hệ thống phức tạp, có thể dùng các `Condition` riêng với `ReentrantLock`; dù vậy, quy tắc **chờ trong vòng lặp kiểm tra predicate** vẫn giữ nguyên.

## 6. Ngắt, timeout và trường hợp điều kiện không bao giờ thỏa

### Ngắt (`InterruptedException`)

`wait()` có thể ném `InterruptedException` khi thread bị ngắt. Khi ném ngoại lệ này, trạng thái interrupt được xóa; thường nên **truyền tiếp ngoại lệ** bằng `throws InterruptedException`, hoặc khôi phục cờ bằng `Thread.currentThread().interrupt()` tại ranh giới không thể truyền tiếp. Không bắt rồi âm thầm bỏ qua, bởi caller sẽ mất tín hiệu yêu cầu dừng.

Một lần `wait()` bị ngắt không có nghĩa điều kiện đã đúng; đoạn code ngay sau vòng lặp **không nên chạy** nếu việc chờ kết thúc do ngoại lệ.

### Chờ có thời hạn

Dùng `wait(timeout)` thì thread còn có thể thức dậy do **hết hạn**; cần kiểm tra lại điều kiện y hệt. Không nên gọi lại cùng timeout nguyên vẹn sau mỗi lần thức dậy, vì nhiều lần thức dậy sớm có thể kéo dài tổng thời gian chờ vô hạn.

Ví dụ phương thức dưới đây là **đoạn minh họa đặt trong một lớp có `lock` và `ready` dùng chung**; đây không phải chương trình độc lập:

```java
boolean awaitReady(long timeoutNanos) throws InterruptedException {
    if (timeoutNanos < 0) {
        throw new IllegalArgumentException("timeoutNanos < 0");
    }

    long started = System.nanoTime();
    synchronized (lock) {
        long remaining = timeoutNanos;
        while (!ready) {
            if (remaining <= 0) {
                return false; // Hết hạn và điều kiện vẫn sai.
            }
            java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(lock, remaining);
            remaining = timeoutNanos - (System.nanoTime() - started);
        }
        return true;
    }
}
```

Ở đây, mọi lần đọc/ghi `ready` phải được bảo vệ bằng chính `lock`. `System.nanoTime()` dùng để đo khoảng thời gian trôi qua; `TimeUnit.NANOSECONDS.timedWait` chuyển khoảng chờ thành lời gọi `Object.wait` phù hợp. Nếu cần deadline cực dài, chính sách timeout và xử lý tràn số cần được thiết kế riêng.

Lưu ý `wait(0)` có nghĩa **chờ vô thời hạn**, không phải kiểm tra rồi trả về ngay. Với `wait(long millis, int nanos)`, tham số phải nằm trong phạm vi API cho phép.

### Điều kiện không thể thỏa

Nếu không có thread nào cập nhật trạng thái hoặc phát tín hiệu thích hợp, `while` có thể chờ mãi. Hãy thiết kế thêm điều kiện kết thúc, cơ chế hủy hoặc deadline khi bài toán yêu cầu. Vòng `while` bảo đảm **không tiếp tục khi điều kiện sai**; tự nó không bảo đảm tiến triển.

## 7. Những lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| Dùng `if` thay `while` | Thức dậy giả hoặc thread khác thay đổi trạng thái trước khi giành lại monitor | Kiểm tra predicate bằng `while` |
| Gọi `wait()` ngoài `synchronized` của cùng đối tượng | Không sở hữu monitor, gặp `IllegalMonitorStateException` | Khóa đúng đối tượng nhận `wait()` |
| Đọc điều kiện ở ngoài khóa | Trạng thái có thể đổi giữa lúc kiểm tra và chờ, gây mất tín hiệu | Bảo vệ cả predicate và thao tác với cùng monitor |
| Gọi `notifyAll()` trước khi cập nhật trạng thái hoặc quên phát tín hiệu | Thread được đánh thức nhưng không thấy trạng thái phù hợp; có thể chờ mãi | Cập nhật trạng thái rồi thông báo khi vẫn giữ monitor |
| Cho rằng `notifyAll()` giao ngay monitor | Thread báo hiệu vẫn đang giữ khóa; thread được báo phải đợi tranh chấp | Dự liệu thời điểm giành lại khóa |
| Bỏ qua `InterruptedException` | Yêu cầu hủy bị nuốt, thread khó dừng đúng cách | Truyền tiếp hoặc phục hồi cờ interrupt phù hợp |
| Giữ thêm khóa khác khi `wait()` | `wait()` chỉ nhả monitor của đối tượng nhận lời gọi; khóa khác vẫn bị giữ | Thiết kế thứ tự khóa và miền chờ để tránh deadlock |
| Thay `wait()` bằng `Thread.sleep()` khi đang giữ monitor | `sleep()` **không nhả monitor**, thread khác có thể không thay đổi được điều kiện | Dùng `wait()` đúng cách hoặc tiện ích đồng bộ cấp cao |

**Phân biệt thêm:** `wait()` của `Object` không giống `Thread.join()`, `Thread.sleep()` hay `LockSupport.park()`. Chúng có cơ chế và điều kiện sử dụng riêng. Bài này chỉ bàn về condition waiting với intrinsic monitor của `Object`.

## 8. Checklist tự kiểm tra

Với mỗi điểm chờ dựa trên `Object.wait()`, tự hỏi:

1. **Predicate là gì?** Ví dụ: “queue không rỗng”, không phải “đã nhận notify”.
2. **Ai bảo vệ predicate?** Mọi đường đọc/ghi liên quan đều phải tuân thủ cùng monitor.
3. **`while` có bao `wait()`?** Điều kiện được kiểm tra trước chờ và sau mỗi lần thức dậy.
4. **Ai thay đổi trạng thái và báo hiệu?** Sửa trạng thái trước, rồi `notifyAll()` khi giữ đúng monitor.
5. **Có đường ngắt, timeout và kết thúc không?** Thread không được âm thầm dùng dữ liệu chưa hợp lệ hoặc chờ vĩnh viễn ngoài ý muốn.

Khi sử dụng cấu trúc dữ liệu đồng thời thông dụng trong ứng dụng thực tế, ưu tiên API đã đóng gói giao thức chờ như `BlockingQueue.put()`/`take()`. Khi cần học, đánh giá hoặc tự xây dựng một monitor, vòng **`while (conditionNotSatisfied) wait()`** là nền tảng để giữ bất biến đúng dưới mọi cách lập lịch hợp lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-monitor-007">notify() không chuyển khóa ngay cho luồng được đánh thức</a>

<details>
<summary>Xem chi tiết</summary>

# `notify()` không chuyển khóa ngay cho luồng được đánh thức

Khi một luồng gọi `notify()` trên một đối tượng trong Java, **luồng đang đợi có thể được chọn để thức dậy, nhưng không được trao ngay monitor lock**. Luồng gọi `notify()` vẫn giữ khóa cho đến khi nó thực sự nhả monitor. Chỉ sau khi giành lại đúng monitor, luồng được đánh thức mới có thể hoàn tất `wait()` và tiếp tục chạy phần mã được bảo vệ bởi `synchronized`.

Đây là điểm cần nắm vững khi phối hợp luồng bằng `Object.wait()`, `Object.notify()` và `Object.notifyAll()`.

## 1. Monitor, wait set và ý nghĩa của “đánh thức”

Mỗi đối tượng Java có một **monitor** (khóa dùng cho `synchronized`) và một **wait set** (tập các luồng đang chờ thông qua `wait()` trên chính đối tượng đó).

- `synchronized (lock) { ... }` yêu cầu luồng lấy monitor của `lock` trước khi vào khối. Trong lúc luồng giữ monitor, luồng khác không thể đồng thời đi vào vùng `synchronized` dùng cùng monitor.
- `lock.wait()` chỉ được gọi khi luồng đang giữ monitor của `lock`. Nó đưa luồng vào wait set và **nhả monitor của `lock`** trong khi chờ. Khi trở về từ `wait()`, luồng đã lấy lại monitor đó. Nếu luồng trước đó đã lấy monitor lồng nhau (reentrant), `wait()` giải phóng và sau đó lấy lại số lần sở hữu tương ứng theo đặc tả.
- `lock.notify()`, cũng phải gọi khi đang sở hữu monitor của `lock`, chọn tùy ý **một** luồng trong wait set của đối tượng này để cho phép tiếp tục chờ lấy khóa. Nếu không có luồng đang chờ thì lời gọi không có tác dụng lưu lại cho tương lai.
- `lock.notifyAll()` loại tất cả các luồng đang chờ khỏi wait set. Điều này **không** làm tất cả cùng chạy trong vùng `synchronized`: mỗi thời điểm chỉ một luồng có thể sở hữu monitor ấy.

Điểm phân biệt cốt lõi: **`wait()` nhả monitor khi bắt đầu chờ; `notify()` không nhả monitor khi báo hiệu**. Cách nói “luồng được đánh thức” chỉ có nghĩa luồng đó đủ điều kiện tiếp tục quá trình trở về từ `wait()`, chưa có nghĩa nó đã đi qua `wait()` hoặc đang thực thi.

## 2. Trình tự chính xác khi `notify()` được gọi

Giả sử hai luồng cùng dùng đối tượng `lock`: W (waiter) đợi điều kiện, N (notifier) thay đổi điều kiện và báo hiệu.

1. **W vào `synchronized (lock)`**, thấy điều kiện chưa thỏa và gọi `lock.wait()`. W nằm trong wait set, đồng thời nhả monitor để các luồng khác có thể vào.
2. **N lấy monitor**, cập nhật trạng thái chia sẻ rồi gọi `lock.notify()`. JVM chọn một luồng trong wait set (giả sử W). **N vẫn là chủ sở hữu monitor** ngay sau lời gọi.
3. **N tiếp tục chạy** các câu lệnh còn lại trong vùng `synchronized`. W chưa thể hoàn tất `wait()` bởi chưa thể lấy lại monitor đang do N giữ. Nếu N thực hiện xử lý chậm trong vùng khóa, W phải chờ thêm.
4. **N nhả monitor**, thường khi ra khỏi khối/phương thức `synchronized`. Từ thời điểm này, W có thể cạnh tranh lấy monitor với các luồng khác.
5. **Khi W lấy lại được monitor**, `wait()` mới có thể trả về bình thường (nếu không có tình huống ngắt), rồi W kiểm tra lại điều kiện và tiếp tục xử lý.

`notify()` không có cam kết về thời điểm chạy tiếp, thứ tự ưu tiên, tính công bằng hay việc W sẽ là luồng tiếp theo lấy khóa. Nếu có nhiều luồng đợi, `notify()` không bảo đảm chọn luồng nào. Trạng thái `Thread.State` quan sát được chỉ là ảnh chụp nhất thời; thường có thể thấy luồng bị chặn khi đợi monitor, nhưng không nên phụ thuộc vào một chuỗi trạng thái chính xác cho mọi JVM.

Có thể hình dung thứ tự bắt buộc với cùng monitor:

```text
W: giữ lock → wait() và nhả lock ──────────────┐
N:                   lấy lock → notify() → xử lý tiếp → nhả lock
W:                                                     tranh lock → wait() trở về
```

Thông báo làm W đủ điều kiện tiếp tục, **không phải thao tác “bàn giao khóa”** từ N sang W.

## 3. Chương trình Java 17+ chạy được, thứ tự kết quả ổn định

Lưu thành `NotifyMonitorDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class NotifyMonitorDemo {
    private static final Object MONITOR = new Object();
    private static final CountDownLatch WAITER_READY = new CountDownLatch(1);
    private static boolean canProceed = false;

    public static void main(String[] args) throws InterruptedException {
        Thread waiter = new Thread(() -> {
            synchronized (MONITOR) {
                WAITER_READY.countDown();
                try {
                    while (!canProceed) {
                        MONITOR.wait();
                    }
                    System.out.println("waiter: da lay lai monitor");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "waiter");

        waiter.start();
        WAITER_READY.await();

        synchronized (MONITOR) {
            canProceed = true;
            MONITOR.notify();
            System.out.println("notifier: notify xong, van giu monitor");
            System.out.println("notifier: sap thoat synchronized");
        }

        waiter.join();
    }
}
```

Biên dịch và chạy:

```bash
javac NotifyMonitorDemo.java
java NotifyMonitorDemo
```

Kết quả chuẩn (ba dòng luôn theo thứ tự này nếu chương trình chạy bình thường, không bị ngắt từ bên ngoài):

```text
notifier: notify xong, van giu monitor
notifier: sap thoat synchronized
waiter: da lay lai monitor
```

**Giải thích vì sao kết quả ổn định:**

- `WAITER_READY.countDown()` xảy ra **bên trong** vùng khóa của waiter. Main chờ latch rồi mới cố lấy `MONITOR`; khi main vào được khối `synchronized`, waiter đã nhả monitor thông qua `wait()` (hoặc đã tạm thức dậy và chờ tiếp). Latch chỉ điều phối việc thiết lập ví dụ, không thay thế monitor trong cơ chế chính.
- `canProceed` được đọc và ghi khi đã giữ cùng `MONITOR`. Vòng `while` còn xử lý được trường hợp thức dậy không do thông báo (spurious wakeup); không cần `volatile` cho biến này trong ví dụ.
- Sau `MONITOR.notify()`, main vẫn ở trong khối `synchronized` nên in xong **cả hai dòng** của notifier trước khi waiter có cơ hội lấy lại monitor và in dòng của mình. Chương trình không dùng `sleep()` hoặc dự đoán lịch chạy để tạo ra thứ tự này.
- `waiter.join()` giúp main chờ luồng con kết thúc trước khi kết thúc chương trình; `join()` không phải thao tác bàn giao monitor ở chỗ gọi `notify()`.

Nếu thay dòng `MONITOR.notify()` bằng `MONITOR.notifyAll()`, với chỉ một waiter trong ví dụ, tính chất “không nhả khóa ngay” vẫn giữ nguyên.

## 4. Vì sao đặt việc chậm ngay sau `notify()` có thể gây trễ?

Xét **đoạn mã minh họa bên trong phương thức có `throws InterruptedException`**, sử dụng đối tượng monitor và biến điều kiện đã được khai báo:

```java
synchronized (monitor) {
    condition = true;
    monitor.notify();
    Thread.sleep(500); // Luồng báo hiệu vẫn giữ monitor trong lúc ngủ.
}
```

`Thread.sleep(500)` tạm dừng luồng gọi nhưng **không giải phóng monitor**. Dù một waiter đã được `notify()` chọn, nó vẫn không thể trở về từ `wait()` cho đến khi luồng báo hiệu rời khối `synchronized` (và waiter lấy lại khóa). Thời lượng ngủ thực tế không phải cam kết thời gian chờ chính xác.

Tương tự, thực hiện I/O, tính toán tốn thời gian hoặc đợi tài nguyên khác trong vùng khóa sau `notify()` có thể kéo dài thời gian chờ. Nên giữ đoạn bảo vệ ngắn, cập nhật trạng thái và báo hiệu dưới monitor, rồi làm các công việc không cần khóa ở bên ngoài.

Nếu notifier tự gọi `wait()` trên cùng monitor thì thao tác `wait()` của notifier **có** giải phóng monitor; đó là hành vi riêng của `wait()`, không phải do `notify()` đã chuyển khóa. Ra khỏi `synchronized` bình thường hoặc do ngoại lệ cũng nhả monitor theo quy tắc của khối.

## 5. Mối liên hệ với tính nhìn thấy của dữ liệu

Việc waiter phải lấy lại monitor còn bảo đảm một phần quan trọng của tính đúng đắn:

- N ghi `canProceed = true` khi giữ monitor, rồi nhả monitor lúc rời `synchronized`.
- Lần mở khóa của N **happens-before** lần lấy khóa tiếp theo của W trên cùng monitor. Vì thế, sau khi W lấy lại khóa, nó nhìn thấy các cập nhật phù hợp với quy tắc Java Memory Model.
- Đặc tả cũng có quy tắc đồng bộ giữa tác vụ thông báo và lần trở về từ `wait()` được thông báo; tuy nhiên **không quy tắc nào biến `notify()` thành thao tác nhả khóa ngay lập tức**.

Không được dựa vào việc gọi `notify()` mà tự bỏ `synchronized` quanh việc kiểm tra và cập nhật trạng thái dùng chung. Hai bên phải thống nhất đối tượng khóa và điều kiện.

## 6. Mẫu phối hợp đúng theo điều kiện

Hai đoạn sau là **mã mẫu trong các phương thức phù hợp**, với `monitor` là cùng một đối tượng và `condition` là trạng thái chia sẻ luôn được bảo vệ bởi monitor:

**Luồng chờ:**

```java
synchronized (monitor) {
    while (!condition) {
        monitor.wait();
    }
    // Điều kiện đã thỏa, và luồng đang giữ monitor.
}
```

**Luồng báo hiệu:**

```java
synchronized (monitor) {
    condition = true;    // Đổi trạng thái trước khi thông báo.
    monitor.notifyAll(); // Luồng chờ vẫn phải giành lại monitor.
}
```

Hãy nhớ rằng `wait()` có thể trở về do thông báo, do ngắt, do hết hạn với bản `wait(timeout)`, hoặc do **spurious wakeup**. Vì vậy, điều kiện phải được kiểm tra trong **`while`**, không chỉ trong `if`. `wait()` dạng không timeout vẫn có thể bị `InterruptedException`; mã bao quanh phải xử lý hoặc khai báo ngoại lệ đó. Nếu waiter thức dậy nhưng điều kiện lại không còn đúng vì một luồng khác đã xử lý trước, vòng `while` đưa waiter vào chờ tiếp.

Tín hiệu `notify()` **không được lưu lại**: nếu gọi khi wait set rỗng, một luồng bắt đầu `wait()` sau đó sẽ không nhận lại tín hiệu cũ. Việc đặt phép kiểm tra điều kiện và lời gọi `wait()` trong cùng vùng `synchronized` tránh khe hở kiểm tra–chờ có thể gây mất tín hiệu ở mô hình dùng đúng monitor.

Chọn `notify()` khi có thể chứng minh việc đánh thức tùy ý một waiter là đủ và đúng; chọn `notifyAll()` khi có nhiều waiter hoặc nhiều điều kiện chờ, vì `notify()` có thể đánh thức một luồng không đáp ứng được điều kiện cần tiến triển. `notifyAll()` có chi phí đánh thức thêm luồng nhưng vẫn không cho chạy đồng thời trong cùng monitor.

## 7. Lỗi thường gặp và cách sửa

| Hiểu sai / cách viết | Điều thực sự xảy ra | Cách xử lý |
| --- | --- | --- |
| “Gọi `notify()` là waiter chạy ngay.” | Notifier tiếp tục giữ monitor; waiter phải chờ và lấy lại khóa. | Chỉ dựa trên điều kiện chia sẻ, không giả định thứ tự chạy tức thì. |
| Gọi `monitor.notify()` ở ngoài `synchronized (monitor)`. | Ném `IllegalMonitorStateException` do không giữ đúng monitor. | Lấy khóa **cùng đối tượng** khi gọi `wait/notify/notifyAll`. |
| Dùng `if (!condition) monitor.wait();`. | Spurious wakeup hoặc cạnh tranh điều kiện khiến luồng tiếp tục sai. | Kiểm tra lại điều kiện bằng `while`. |
| Đổi điều kiện ngoài khóa, sau đó mới thông báo. | Có thể gây race hoặc lỗi nhìn thấy dữ liệu và phá vỡ giao thức chờ. | Bảo vệ cả cập nhật, kiểm tra và thao tác báo hiệu bằng cùng monitor. |
| Gọi `notify()` trên đối tượng khác đối tượng mà waiter dùng `wait()`. | Wait set thuộc từng đối tượng; waiter không được đánh thức bằng thông báo đó. | Dùng cùng đối tượng monitor thực sự. |
| Giữ monitor để làm I/O hoặc `sleep()` sau `notify()`. | Waiter có thể bị chặn lâu dù đã được thông báo. | Thu ngắn vùng khóa; dời việc không cần khóa ra ngoài. |
| Cho rằng `notifyAll()` trao khóa cùng lúc cho mọi waiter. | Mọi waiter được phép tiếp tục, nhưng từng luồng phải giành khóa một cách loại trừ. | Viết điều kiện đúng, chấp nhận cạnh tranh và kiểm tra lại. |
| Bỏ qua `InterruptedException` hoặc giả định chỉ `notify()` mới làm `wait()` trở về. | Luồng chờ có thể bị ngắt; chương trình dễ treo hoặc bỏ sót yêu cầu dừng. | Có chiến lược xử lý ngắt rõ ràng và vòng kiểm tra điều kiện. |

## 8. Câu hỏi kiểm tra

**Hỏi:** Sau `notify()`, notifier còn thực hiện 100 câu lệnh trong cùng khối `synchronized`. Waiter có thể tiếp tục ngay sau `wait()` trong lúc đó không?

**Đáp:** Không, nếu cả hai thao tác dùng cùng monitor. Waiter phải lấy lại monitor trước khi `wait()` kết thúc, nên phải chờ notifier nhả khóa. Số lượng câu lệnh không quan trọng; ranh giới sở hữu monitor mới quyết định.

**Hỏi:** Ngay khi notifier ra khỏi `synchronized`, waiter có chắc chắn chạy trước một luồng thứ ba đang cố lấy khóa không?

**Đáp:** Không. Java không bảo đảm waiter được `notify()` chọn sẽ giành monitor kế tiếp; các luồng có thể cạnh tranh.

**Hỏi:** Nếu không có waiter nào vào `wait()` lúc `notify()` được gọi thì sao?

**Đáp:** Không có thông báo tồn đọng. Một waiter xuất hiện sau đó chỉ có thể tiến triển nếu điều kiện cho phép, được báo hiệu trong tương lai hoặc có nguyên nhân thức dậy khác.

## 9. Ghi nhớ và tài liệu gốc

**Công thức nhớ:** `wait()` = chờ và nhả monitor; `notify()` = báo cho một waiter nhưng **vẫn giữ monitor**; waiter trở về từ `wait()` = **đã lấy lại monitor**.

- [Java Language Specification §17.1: Synchronization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1)
- [Java Language Specification §17.2.1–17.2.2: Wait và Notification](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.2)
- [Java Language Specification §17.4.5: Happens-before](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5)
- [Java SE 17 API: `Object`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-monitor-008">Xử lý InterruptedException và thời hạn chờ theo hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý InterruptedException và thời hạn chờ theo hợp đồng của Object monitor

## 1. Bài toán và mục tiêu

Một thread muốn chờ trạng thái do thread khác thay đổi (ví dụ hàng đợi có dữ liệu, công việc đã hoàn thành) có thể dùng monitor của **Object** cùng với **wait**, **notify** và **notifyAll**. Hợp đồng chờ phải phân biệt ba kết cục: **điều kiện thỏa**, **hết thời hạn**, và **yêu cầu ngắt (interruption)**. Chúng không tương đương: thức dậy không chắc thành công; hết hạn không phải bị ngắt; bị ngắt không phải lỗi nghiệp vụ của điều kiện.

Bài này thuộc chủ đề **Monitor của Object** trong Java Core. Mục tiêu là viết đúng vòng lặp chờ predicate, thiết kế timeout có ngân sách tổng, xử lý **InterruptedException** mà không đánh mất tín hiệu hủy, và ghi rõ giới hạn của thời hạn chờ.

## 2. Hợp đồng của monitor và wait

Mỗi đối tượng Java có monitor. Thread phải **sở hữu monitor của chính đối tượng** khi gọi các phương thức **wait**, **notify** hoặc **notifyAll**; thường dùng **synchronized (monitor)**. Gọi không đúng monitor sẽ ném **IllegalMonitorStateException**.

| Phương thức | Hành vi quan trọng | Lưu ý |
| --- | --- | --- |
| **monitor.wait()** | Nhả monitor này và chờ; trước khi trả về hoặc ném **InterruptedException**, thread phải giành lại monitor. | Không nhả các monitor khác mà thread đang giữ; có thể thức dậy không cần notify (spurious wakeup). |
| **monitor.wait(long millis)** | Chờ tối đa theo mốc yêu cầu, có thể dậy do notify, timeout hoặc spurious wakeup; **millis = 0** nghĩa là chờ vô hạn. | millis âm gây **IllegalArgumentException**; không bảo đảm thời gian trả về chính xác. |
| **monitor.wait(long millis, int nanos)** | Bổ sung phần nanos trong khoảng **0..999999**; thời gian âm/nanos ngoài khoảng không hợp lệ. | Không hứa độ chính xác nanosecond. |
| **monitor.notify()** | Chọn một waiter trên cùng monitor để đánh thức. | Không chọn được waiter cụ thể; nó chỉ tiếp tục sau khi lấy lại monitor. |
| **monitor.notifyAll()** | Đánh thức các waiter trên cùng monitor. | Mỗi waiter phải tự kiểm tra lại điều kiện; không phải tất cả đều xử lý thành công. |

**wait trả về bình thường không chứng minh điều kiện đã đúng**, vì có thể timeout, có thông báo dành cho điều kiện khác, hoặc spurious wakeup. Luôn kiểm tra predicate trong **while** dưới cùng monitor:

~~~java
// Các trường monitor, ready thuộc cùng một đối tượng chia sẻ.
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
    // ready == true, kiểm tra lúc đang sở hữu monitor.
}

// Ở phía thread phát tín hiệu:
synchronized (monitor) {
    ready = true;
    monitor.notifyAll();
}
~~~

Đây là hai đoạn minh họa, không phải một chương trình Java độc lập. Predicate và thao tác đổi trạng thái cần cùng một khóa: đọc **ready** ngoài synchronized rồi mới gọi wait có thể bỏ lỡ thay đổi xảy ra giữa hai bước. Dùng **if** thay **while** dễ khiến thread tiếp tục dù điều kiện còn sai.

Khi **wait** đang chờ, nó nhả monitor của đối tượng gọi wait; khi kết thúc, phải giành lại monitor trước khi caller làm việc tiếp. Tương tự, **notifyAll** không trao monitor ngay cho waiter: thread báo tín hiệu vẫn giữ khóa đến khi thoát synchronized. Vì thế có thể có độ trễ sau khi đã được báo hoặc sau khi timeout.

## 3. InterruptedException: truyền tín hiệu ngắt theo hợp đồng

**Thread.interrupt()** là lời yêu cầu hợp tác để một thread dừng hoặc thay đổi công việc. Nếu thread đang trong **Object.wait**, thao tác wait sẽ ném **InterruptedException** theo hợp đồng xử lý ngắt; thread đã giành lại monitor khi ngoại lệ được ném. Interrupt flag được xóa khi ngoại lệ này được ném, nên caller chỉ kiểm tra lại flag có thể bỏ sót yêu cầu nếu ai đó đã nuốt ngoại lệ.

Chính sách xử lý phụ thuộc tầng gọi:

- **Phương thức ở tầng thấp có thể khai báo throws InterruptedException:** truyền ngoại lệ lên để caller quyết định hủy. Đây là cách rõ ràng nhất để không nhầm interruption với timeout.
- **Tầng không được khai báo ngoại lệ checked** (ví dụ **Runnable.run()**): khi catch, gọi **Thread.currentThread().interrupt()** để khôi phục flag và chấm dứt công việc chờ. Như vậy tầng bên ngoài vẫn có thể quan sát yêu cầu ngắt.
- **Không catch rỗng, không chỉ log rồi chờ lại** nếu chưa có chính sách rõ ràng. Điều này nuốt tín hiệu hủy và có thể làm tác vụ kẹt.
- **Không luôn vừa khôi phục flag vừa ném InterruptedException** theo thói quen: một tầng truyền ngoại lệ đã mang thông tin ngắt; việc khôi phục flag thường dành cho tầng bắt không thể truyền tiếp.

~~~java
// Trong một Runnable có trường gate kiểu Gate của ví dụ đầy đủ.
@Override
public void run() {
    try {
        boolean ready = gate.awaitOpen(java.time.Duration.ofSeconds(3));
        if (!ready) {
            return; // Hết hạn theo hợp đồng, không phải interruption.
        }
        // Chỉ xử lý tiếp khi điều kiện đã đúng.
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return; // Giữ yêu cầu ngắt để caller/tầng ngoài có thể nhận biết.
    }
}
~~~

Đoạn trên là ví dụ từng phần; chương trình độc lập ở mục 5. **Thread.interrupted()** đọc rồi xóa cờ interrupt; nếu chỉ muốn quan sát mà không xóa, dùng **Thread.currentThread().isInterrupted()**. Sau khi đã khôi phục cờ, việc tiếp tục chạy một thao tác blocking mà không có chính sách hủy có thể làm nó lập tức ném InterruptedException lần nữa.

Cần nêu rõ độ ưu tiên khi **điều kiện đã đúng** và **interrupt flag đã được đặt**. Trong API mẫu bên dưới, nếu predicate đã đúng khi vào synchronized thì trả true mà không cần wait; interruption chỉ biểu hiện thành ngoại lệ khi lời gọi wait phải xử lý ngắt. Nếu muốn “đã bị interrupt thì hủy bất kể trạng thái”, phải thêm kiểm tra và ghi rõ hợp đồng riêng.

## 4. Timeout không phải một lần gọi wait cố định

Giả sử hợp đồng của API là “chờ điều kiện trong ngân sách 2 giây”. Mẫu sau **sai về tổng thời gian**:

~~~java
synchronized (monitor) {
    while (!ready) {
        monitor.wait(2_000); // Mỗi lần thức dậy lại cấp mới 2 giây.
    }
}
~~~

Nếu wakeup xảy ra nhiều lần trong khi predicate còn false, tổng thời gian có thể vượt xa 2 giây hoặc không kết thúc. Timeout không được cấp mới mỗi vòng. Phải đo **thời gian đã trôi qua** và tính lượng còn lại.

Sử dụng **System.nanoTime()** vì đây là nguồn thời gian phù hợp để đo khoảng cách giữa hai lần đọc; không dùng **System.currentTimeMillis()** để đo deadline khi đồng hồ tường có thể đổi do chỉnh giờ. Chỉ trừ các kết quả nanoTime để tính elapsed, không dùng giá trị nanoTime làm thời điểm trên lịch.

**TimeUnit.NANOSECONDS.timedWait(monitor, remainingNanos)** quy đổi ngân sách nanosecond dương thành wait phù hợp. Luôn kiểm tra **remainingNanos > 0** trước khi gọi; nếu tự gọi **wait(0)** vì làm tròn về 0 ms, tác vụ có thể chờ vô hạn.

Quy ước biên phù hợp cho API chờ predicate:
1. Timeout âm → **IllegalArgumentException**, không bắt đầu chờ.
2. Timeout bằng 0 → chỉ kiểm tra predicate một lần, trả true/false ngay.
3. Timeout dương → chờ bằng phần ngân sách còn lại, lặp lại khi chưa thỏa điều kiện.
4. Hết ngân sách và predicate còn false → trả **false**, không đánh đồng với **InterruptedException**.
5. Nếu predicate đã đúng lúc thread giành lại monitor (dù thời gian thực tế đã vượt ngân sách do tranh khóa), API mẫu trả **true**; true nói lên trạng thái quan sát, không chứng minh thay đổi đã đến trước deadline tuyệt đối.

Giới hạn: **synchronized** không cung cấp timeout khi thread đang tranh khóa *để vào* khối synchronized. Ngay cả khi wait hết hạn, thread có thể bị trì hoãn khi giành lại monitor hoặc được hệ điều hành lên lịch. Do đó timeout trong bài là ngân sách của vòng chờ điều kiện khi đã vào monitor, **không phải cam kết hàm trả về đúng trước một mốc cứng**.

## 5. Ví dụ Java 17+ biên dịch và chạy được

Tạo **MonitorContractDemo.java**:

~~~java
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MonitorContractDemo {
    static final class Gate {
        private final Object monitor = new Object();
        private boolean open;

        void open() {
            synchronized (monitor) {
                open = true;
                monitor.notifyAll();
            }
        }

        /**
         * @return true nếu quan sát được open == true; false nếu
         *         hết ngân sách mà open vẫn false.
         * @throws InterruptedException nếu bị ngắt trong lúc wait.
         * @throws IllegalArgumentException nếu timeout âm.
         * @throws ArithmeticException nếu Duration.toNanos() tràn long.
         */
        boolean awaitOpen(Duration timeout) throws InterruptedException {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be >= 0");
            }

            long budgetNanos = timeout.toNanos();

            synchronized (monitor) {
                long started = System.nanoTime();
                long remainingNanos = budgetNanos;

                while (!open) {
                    if (remainingNanos <= 0) {
                        return false;
                    }

                    // Chỉ truyền thời gian còn lại, không reset ngân sách.
                    TimeUnit.NANOSECONDS.timedWait(monitor, remainingNanos);

                    long elapsedNanos = System.nanoTime() - started;
                    remainingNanos = budgetNanos - elapsedNanos;
                }
                return true;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Gate alreadyOpen = new Gate();
        alreadyOpen.open();
        System.out.println("Success: "
                + alreadyOpen.awaitOpen(Duration.ZERO));

        Gate neverOpen = new Gate();
        System.out.println("Timeout: "
                + neverOpen.awaitOpen(Duration.ofMillis(30)));

        Gate interruptedGate = new Gate();
        Thread worker = new Thread(() -> {
            try {
                interruptedGate.awaitOpen(Duration.ofSeconds(10));
                System.out.println("Interrupted: false");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Interrupted: true");
            }
        });

        worker.start();
        worker.interrupt();
        worker.join();
    }
}
~~~

Biên dịch bằng Java 17 trở lên:

~~~text
javac MonitorContractDemo.java
java MonitorContractDemo
~~~

Stdout mong đợi:

~~~text
Success: true
Timeout: false
Interrupted: true
~~~

Giải thích: Gate thứ nhất đã mở, nên chờ với **Duration.ZERO** vẫn trả true. Gate thứ hai không bao giờ được mở, nên trả false khi hết ngân sách. Gate thứ ba không mở và worker nhận yêu cầu interrupt; wait xử lý ngắt bằng ngoại lệ, worker khôi phục cờ trước khi thoát. Nếu interrupt đến trước lúc worker gọi wait, wait vẫn có thể phát hiện cờ khi được gọi: ví dụ không phụ thuộc vào việc worker đã kịp ngủ hay chưa.

Mọi truy cập biến **open** đều nằm trong **synchronized (monitor)**. Ghi trạng thái rồi nhả khóa và đọc trạng thái sau khi giành khóa tạo bảo đảm hiển thị bộ nhớ giữa các thread; biến **open** không cần volatile trong thiết kế này. **notifyAll()** đánh thức các waiter để họ kiểm tra lại predicate khi lấy được khóa. Không gọi **Thread.sleep** thay wait bên trong synchronized: sleep không nhả monitor, khiến thread phát tín hiệu có thể không lấy được khóa để đổi trạng thái.

**Về số học:** Duration lớn có thể khiến **toNanos()** ném **ArithmeticException**; đây là đầu vào không biểu diễn được trong ngân sách long nanosecond, không phải timeout thông thường. Trong ví dụ, thời lượng hợp lệ rất ngắn nên phép hiệu **System.nanoTime()** là phù hợp. Khi tự thiết kế hợp đồng có thời hạn rất lớn hoặc vô hạn, cần quyết định xử lý quá lớn một cách tường minh, không âm thầm chuyển thành **wait(0)**.

## 6. Thiết kế hợp đồng gọi và xử lý kết quả

| Tình huống khi giành monitor và kiểm tra | Kết quả của awaitOpen | Hành động phù hợp phía caller |
| --- | --- | --- |
| **open** đã true hoặc quan sát được true sau một lần thức dậy | **true** | Thực hiện bước yêu cầu Gate đã mở. |
| Hết ngân sách và **open** vẫn false | **false** | Báo hết hạn, thử lại có giới hạn, hoặc chọn phương án dự phòng. |
| Bị ngắt khi wait | Ném **InterruptedException** | Dừng tác vụ hoặc chuyển yêu cầu hủy lên tầng có trách nhiệm. |
| Timeout âm | Ném **IllegalArgumentException** | Sửa tham số đầu vào. |
| Duration không đổi được sang long nanoseconds | Ném **ArithmeticException** | Điều chỉnh hợp đồng về miền giá trị chấp nhận được. |
| Timeout null | Ném **NullPointerException** | Không truyền đối số null. |

Nếu một caller chọn retry sau **false**, phải dùng **ngân sách tổng** của toàn quy trình retry; không tái cấp timeout vô hạn mỗi vòng. Cũng cần thiết kế predicate đủ diễn đạt trạng thái terminal: một producer thất bại hoặc bị hủy có thể không bao giờ mở Gate. Trong hệ thống thật, nếu có **done**, **failed**, **cancelled**, phải định nghĩa nhánh xử lý các trạng thái đó; đừng chỉ chờ boolean không thể thành true.

Thông báo monitor không giữ lại như “phiếu” cho lần wait sau. Việc tránh bỏ lỡ tín hiệu đến từ **việc lưu trạng thái điều kiện dưới cùng monitor và kiểm tra trước khi chờ**, không phải từ số lần gọi notify. Với nhiều waiter chờ điều kiện khác nhau trên cùng monitor, **notifyAll** thường an toàn hơn **notify**, dù có thể đánh thức nhiều thread để rồi họ lại wait.

## 7. Những lỗi phổ biến và checklist

| Lỗi | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Gọi **wait/notify** không giữ đúng monitor | Vi phạm quyền sở hữu khóa; **IllegalMonitorStateException** | Dùng cùng đối tượng trong synchronized và khi gọi. |
| Dùng **if (!ready) wait()** | Thức dậy giả hoặc thông báo không làm ready đúng | Dùng vòng **while (!ready)**. |
| Kiểm tra predicate ngoài synchronized | Có thể đọc giá trị cũ, bỏ lỡ tín hiệu | Bảo vệ mọi lần đọc/ghi state bằng cùng monitor. |
| Catch InterruptedException rồi bỏ qua | Đánh mất tín hiệu hủy; chờ không kiểm soát | Truyền tiếp hoặc khôi phục flag và thoát ở tầng không truyền được. |
| Cứ lặp lại wait với cùng timeout | Ngân sách tổng bị reset | Tính lại thời gian còn lại sau mỗi lần thức dậy. |
| Dùng **wait(0)** cho “0 ms” | Thực tế là chờ vô hạn | Kiểm tra ngân sách hết trước khi wait. |
| Nghĩ notifyAll báo thành công | Wakeup không thay thế việc kiểm tra điều kiện | Predicate quyết định kết quả. |
| Dùng **currentTimeMillis** đo elapsed | Thay đổi đồng hồ tường làm sai deadline | Dùng hiệu **nanoTime**. |
| Quên tranh khóa và lịch thread | Hàm có thể trả về sau deadline | Mô tả phạm vi timeout chính xác trong API. |
| Dùng **sleep** khi giữ monitor | Sleep không nhả khóa, producer khó đổi state | Dùng wait có predicate. |

Trước khi hoàn tất code, tự hỏi: (1) Predicate có luôn được đọc và sửa dưới cùng monitor không? (2) Tất cả wait nằm trong **while** không? (3) Timeout 0 có trả ngay không? (4) Mỗi wakeup có trừ thời gian đã chờ không? (5) Interruption có đường xử lý riêng khỏi false không? (6) Tầng bắt ngoại lệ có truyền hoặc khôi phục cờ và dừng hợp lý không? (7) API có nói rõ rằng đây không phải deadline cứng của cả hàm không?

## 8. Câu hỏi tự ôn tập

1. Kể các lý do **wait(1000)** có thể trở về bình thường mà predicate vẫn false. Vì sao phải dùng while?
2. Khi **wait** ném **InterruptedException**, thread đã giành lại monitor chưa, và trạng thái interrupt flag ra sao?
3. Vì sao năm lần wakeup giữa vòng chờ có thể biến **wait(2000)** lặp lại thành tổng thời gian hơn 2 giây? Cách tính đúng?
4. Timeout bằng 0, timeout âm và thời hạn quá lớn khác nhau thế nào?
5. Tại sao không thể dùng synchronized + Object.wait để bảo đảm toàn bộ lời gọi luôn kết thúc trước deadline tuyệt đối?
6. Vì sao gọi notifyAll chỉ đúng khi state đã cập nhật dưới cùng monitor? Điều gì xảy ra nếu cập nhật ngoài khóa?

**Ghi nhớ:** **Object.wait** là cơ chế chờ và phối hợp monitor, không phải dấu hiệu hoàn thành nghiệp vụ. Hợp đồng đúng phân biệt rõ **thành công**, **hết hạn**, **ngắt**; dùng while kiểm tra state, ngân sách chờ còn lại và chính sách truyền/cất giữ tín hiệu interrupt.


</details>

- [Quay lại đầu trang](#back-to-top)
