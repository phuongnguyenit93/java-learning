<a id="back-to-top"></a>

# Queue đồng thời

## Menu
- [ConcurrentLinkedQueue và ConcurrentLinkedDeque](#concurrent-queues-001)
- [BlockingQueue: put, take và thao tác có thời hạn chờ](#concurrent-queues-002)
- [Hàng đợi có giới hạn và điều tiết tốc độ bên sản xuất](#concurrent-queues-003)
- [ArrayBlockingQueue so với LinkedBlockingQueue](#concurrent-queues-004)
- [PriorityBlockingQueue và giới hạn dung lượng thực tế](#concurrent-queues-005)
- [DelayQueue và hợp đồng Delayed](#concurrent-queues-006)
- [SynchronousQueue và TransferQueue](#concurrent-queues-007)
- [BlockingDeque, ngắt luồng và quy ước kết thúc luồng xử lý](#concurrent-queues-008)

## <a id="concurrent-queues-001">ConcurrentLinkedQueue và ConcurrentLinkedDeque</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentLinkedQueue và ConcurrentLinkedDeque

## 1. Bài toán: nhiều luồng cùng đưa và lấy phần tử

Một luồng tạo công việc, một luồng khác lấy công việc; hoặc nhiều luồng cùng đẩy và lấy dữ liệu ở hai đầu. Cấu trúc chứa phải bảo vệ được việc liên kết, tìm và loại bỏ các phần tử khi các thao tác chồng lấp.

`ConcurrentLinkedQueue<E>` và `ConcurrentLinkedDeque<E>` là hai lựa chọn thuộc `java.util.concurrent`:

- **`ConcurrentLinkedQueue`** là hàng đợi đồng thời theo thứ tự FIFO: thêm ở đuôi, lấy ở đầu.
- **`ConcurrentLinkedDeque`** là hàng đợi hai đầu đồng thời: cho phép thêm, xem và lấy ở cả đầu lẫn đuôi.

Cả hai dựa trên các nút liên kết, không có giới hạn sức chứa nội tại và không nhận phần tử null. Các thao tác truy cập/cập nhật được thiết kế để sử dụng an toàn giữa nhiều luồng. [ConcurrentLinkedQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html), [ConcurrentLinkedDeque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedDeque.html)

Điều cần học không chỉ là tên lớp. Bạn cần biết **thêm ở đâu, lấy ở đâu, làm gì khi đang trống và khi nào được kết luận không còn việc**. Collection bảo vệ thao tác chứa/lấy; giao thức xử lý công việc vẫn do ứng dụng xác định.

Ví dụ dùng Java 17 trở lên. Kiến thức nền gồm `Queue`, `Deque`, generic, tham chiếu đối tượng và vòng lặp. Phần đa luồng giải thích điều kiện phối hợp và kết thúc ngay trong bài.

### 1.1. Thuật ngữ và mối liên hệ

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| Đầu (head/first) | Phía lấy phần tử của một hàng đợi FIFO |
| Đuôi (tail/last) | Phía thêm phần tử mới của hàng đợi FIFO |
| FIFO | Phần tử được đưa trước được lấy trước theo thứ tự hàng đợi |
| LIFO | Phần tử được đưa sau được lấy trước, như ngăn xếp |
| Bên tạo/bên nhận | Tác vụ đưa phần tử vào và tác vụ lấy phần tử ra |
| Không có giới hạn nội tại (unbounded) | Không có hạn mức phần tử cố định do cấu trúc tự áp đặt |
| Thao tác không chờ dữ liệu | Lần gọi lấy trả kết quả ngay theo trạng thái quan sát, không đợi một phần tử tương lai xuất hiện |

“Không có giới hạn nội tại” không có nghĩa bộ nhớ vô hạn. “An toàn đa luồng” không có nghĩa nhiều bước của chương trình tự hợp thành một giao dịch.

## 2. Chọn đúng đầu và đúng kiểu kết quả

### 2.1. ConcurrentLinkedQueue: một đường FIFO

Mô hình cơ bản:

```text
lay o dau                              them o duoi
poll / peek  <- [viec-cu, viec-ke-tiep, viec-moi] <- offer
```

`offer(e)` đưa phần tử vào đuôi. `poll()` lấy và xóa phần tử ở đầu. `peek()` chỉ xem đầu, không lấy đi.

Có hai nhóm phương thức tùy cách xử lý queue trống:

| Ý định | Trả giá trị đặc biệt | Ném ngoại lệ nếu trống |
| --- | --- | --- |
| Lấy và xóa đầu | `poll()` trả null | `remove()` ném `NoSuchElementException` |
| Xem đầu, không xóa | `peek()` trả null | `element()` ném `NoSuchElementException` |

Đây là hợp đồng của các phương thức `Queue`. `remove()` không đối số khác `remove(object)`, vốn tìm và xóa một lần xuất hiện của đối tượng được chỉ định. [Queue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html)

Đối với `ConcurrentLinkedQueue` không giới hạn nội tại, `offer` trả true khi thêm thành công; nó không trả false vì “queue đã đủ N phần tử”. Không có cơ chế chờ chỗ trống như một hàng đợi có sức chứa được quản lý.

### 2.2. ConcurrentLinkedDeque: hai đầu độc lập trong giao diện

`Deque` là viết tắt của “double-ended queue”, tức hàng đợi hai đầu.

| Ý định | Đầu `First` | Đuôi `Last` |
| --- | --- | --- |
| Thêm | `offerFirst(e)` hoặc `addFirst(e)` | `offerLast(e)` hoặc `addLast(e)` |
| Lấy, trả null nếu trống | `pollFirst()` | `pollLast()` |
| Lấy, ném ngoại lệ nếu trống | `removeFirst()` | `removeLast()` |
| Xem, trả null nếu trống | `peekFirst()` | `peekLast()` |
| Xem, ném ngoại lệ nếu trống | `getFirst()` | `getLast()` |

`addFirst/addLast` trả void; `offerFirst/offerLast` trả boolean. Với `ConcurrentLinkedDeque`, các thao tác offer ở hai đầu trả true khi thêm thành công, không bị từ chối do một giới hạn sức chứa nội tại. Phần tử null vẫn không hợp lệ.

Khi dùng deque như queue, `offer/add` thêm ở đuôi, còn `poll/remove` lấy ở đầu. Khi dùng như stack, `push` tương ứng thêm đầu và `pop` tương ứng lấy đầu. [Deque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)

Do đó:

- `offerLast` rồi `pollFirst` tạo cách sử dụng FIFO.
- `push` rồi `pop`, cùng làm việc ở đầu, tạo cách sử dụng LIFO.
- Nếu chương trình trộn thêm/lấy hai đầu, thứ tự xử lý cần được mô tả theo quy tắc thực tế đó; không gọi toàn bộ cách dùng là FIFO một cách máy móc.

### 2.3. Có thể chứa phần tử trùng

Hai lần thêm cùng một giá trị tạo hai lần xuất hiện. Đây là queue/deque, không phải set.

Một lần lấy thành công loại bỏ một lần xuất hiện. Nếu cùng một tham chiếu được đưa vào hai lần, hai bên nhận có thể cùng nhận tham chiếu đó từ hai vị trí khác nhau. Điều này không chứng minh queue đã phát một vị trí hai lần.

Nếu nghiệp vụ cần mã công việc duy nhất, quy tắc chống trùng cần được xử lý riêng. Không lấy tính an toàn của hàng đợi làm cơ chế bảo đảm tính duy nhất.

## 3. Chương trình tuần tự để xác định thứ tự và ngoại lệ

Lưu thành **`LinkedEndpointsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LinkedEndpointsDemo {
    public static void main(String[] args) {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
        queue.offer("one");
        queue.offer("two");
        queue.offer("three");
        System.out.println("queue peek = " + queue.peek());
        System.out.println("queue drain = " + drain(queue));
        System.out.println("empty queue poll = " + queue.poll());
        expectEmpty("empty queue remove", queue::remove);

        ConcurrentLinkedDeque<String> deque = new ConcurrentLinkedDeque<>();
        deque.offerLast("B");
        deque.offerFirst("A");
        deque.offerLast("C");
        System.out.println("deque forward = " + new ArrayList<>(deque));
        List<String> reverse = new ArrayList<>();
        Iterator<String> iterator = deque.descendingIterator();
        iterator.forEachRemaining(reverse::add);
        System.out.println("deque reverse = " + reverse);
        System.out.println("deque ends = " + deque.peekFirst()
                + ", " + deque.peekLast());
        System.out.println("poll first = " + deque.pollFirst());
        System.out.println("poll last = " + deque.pollLast());
        System.out.println("middle remains = " + new ArrayList<>(deque));

        deque.push("X");
        deque.push("Y");
        System.out.println("stack pop = " + deque.pop());
        System.out.println("remove last = " + deque.removeLast());
        System.out.println("remaining = " + new ArrayList<>(deque));
        deque.clear();
        System.out.println("empty deque pollLast = " + deque.pollLast());
        expectEmpty("empty deque pop", deque::pop);
    }

    private static <T> List<T> drain(Queue<T> queue) {
        List<T> values = new ArrayList<>();
        T value;
        while ((value = queue.poll()) != null) {
            values.add(value);
        }
        return values;
    }

    private static void expectEmpty(String label, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected NoSuchElementException");
        } catch (NoSuchElementException ex) {
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all LinkedEndpointsDemo.java
java LinkedEndpointsDemo
```

Stdout khi chạy thành công:

```text
queue peek = one
queue drain = [one, two, three]
empty queue poll = null
empty queue remove -> NoSuchElementException
deque forward = [A, B, C]
deque reverse = [C, B, A]
deque ends = A, C
poll first = A
poll last = C
middle remains = [B]
stack pop = Y
remove last = B
remaining = [X]
empty deque pollLast = null
empty deque pop -> NoSuchElementException
```

Chương trình cố ý chỉ dùng một luồng để tách việc hiểu API khỏi việc phân tích lịch chạy:

1. `peek` đọc `one` nhưng vẫn để nó ở đầu, nên lần xả sau đó vẫn lấy được đủ ba phần tử theo FIFO.
2. `poll` trên queue đã xả hết trả null, còn `remove` ném ngoại lệ.
3. Deque được tạo thành `A, B, C` bằng các thao tác ở hai đầu.
4. Iterator thường duyệt từ đầu tới đuôi; `descendingIterator` đi theo chiều ngược lại.
5. Lấy đầu A và đuôi C để lại B.
6. Đẩy X rồi Y ở đầu làm Y trở thành phần tử được `pop` trước.
7. Trạng thái rỗng cuối cùng cho thấy sự khác nhau giữa `pollLast` và `pop`.

Các bản `ArrayList` trong ví dụ chỉ phục vụ in cấu trúc lúc không có bên khác sửa. Chúng không phải cách biến một nguồn đang cập nhật thành ảnh chụp nhất quán toàn cục.

Hàm `drain` dừng khi `poll` trả null. Điều đó phù hợp ở đây vì không có bên tạo nào còn hoạt động. Không suy rộng điều kiện dừng này sang mọi hệ thống có phần tử được thêm về sau.

## 4. Dùng kết quả của lần lấy, không giữ một quan sát cũ

### 4.1. `isEmpty` rồi `remove` có khoảng tranh chấp

Đoạn minh họa cố ý có vấn đề khi nhiều bên cùng lấy:

```java
if (!queue.isEmpty()) {
    String value = queue.remove();
    System.out.println(value);
}
```

Một luồng thấy queue còn phần tử. Trước khi nó gọi `remove`, luồng khác lấy mất phần tử cuối cùng. Lần `remove` sau đó gặp queue trống và có thể ném `NoSuchElementException`.

Mỗi thao tác riêng đều có thể hoàn toàn đúng. Điều sai là chương trình xem kết quả kiểm tra trước như một quyền giữ lại phần tử.

### 4.2. Một lần `poll` vừa thử nhận vừa trả kết quả

Đoạn minh họa không độc lập, với `queue` là `ConcurrentLinkedQueue<String>`:

```java
String value = queue.poll();
if (value != null) {
    System.out.println("Da nhan: " + value);
} else {
    System.out.println("Lan nay chua co phan tu");
}
```

Vì lớp không nhận phần tử null, null có thể dùng làm tín hiệu lần lấy không nhận được phần tử. Không cần một `isEmpty` trước đó.

Tương tự, một `peek` khác null không giữ quyền lấy phần tử ấy bằng lần `poll` tiếp theo. Nếu nhiều bên cùng lấy, kết quả quan sát đầu có thể trở nên cũ ngay sau khi phương thức trả về.

Hai luồng cùng lấy phần tử cuối bằng `pollFirst`/`pollLast` trên deque không được nhận cùng một lần xuất hiện; một bên có thể nhận phần tử, bên kia nhận null. Không cần tự khóa chỉ để thực hiện một lần gọi lấy đúng hợp đồng.

### 4.3. Null tạm thời và điều kiện kết thúc là hai vấn đề

Một bên nhận thấy null khi queue đang trống, nhưng bên tạo có thể thêm phần tử sau đó. Vì vậy, null không tự có nghĩa:

- Mọi bên tạo đã hoàn tất.
- Sẽ không còn công việc mới.
- Mọi công việc đã lấy ra đều xử lý thành công.
- Hệ thống có thể đóng toàn bộ nhóm luồng ngay.

Một giao thức kết thúc có thể dùng số lượng việc đã biết, tín hiệu hoàn tất phối hợp đúng, hoặc phần tử điều khiển khác null. Phải xác định trước điều kiện mà ứng dụng cần chứng minh.

## 5. Chương trình hai bên tạo, hai bên nhận với ConcurrentLinkedQueue

### 5.1. Giao thức được chọn cho thí nghiệm

Ví dụ sau tạo đúng 2.000 công việc có mã riêng. Hai tác vụ đưa việc vào đuôi; hai tác vụ lấy từ đầu.

Để bên nhận biết khi nào được dừng, luồng chính chờ cả hai bên tạo hoàn tất rồi thêm **hai phần tử điều khiển STOP khác null**, mỗi bên nhận lấy một STOP thì kết thúc.

Giao thức đúng trong phạm vi ví dụ vì:

1. Mọi công việc được thêm xong trước khi luồng chính thêm STOP.
2. Cả công việc và STOP đều được thêm ở đuôi, còn bên nhận chỉ lấy từ đầu FIFO.
3. Có đúng hai tác vụ nhận và hai STOP; mỗi tác vụ trả về ngay sau khi lấy một STOP.
4. Luồng chính chờ cả hai tác vụ nhận hoàn tất trước khi kiểm tra tổng kết.

STOP là cùng một tham chiếu được thêm hai lần. Queue cho phép hai lần xuất hiện này; nó không dùng null để báo dừng.

### 5.2. Chương trình đầy đủ

Lưu thành **`LinkedQueueTransferDemo.java`**:

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LinkedQueueTransferDemo {
    private static final int PER_PRODUCER = 1_000;
    private static final Work STOP = new Work(-1);

    public static void main(String[] args) throws Exception {
        ConcurrentLinkedQueue<Work> queue = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<List<Integer>> consumerA =
                    pool.submit(() -> consume(queue, start));
            Future<List<Integer>> consumerB =
                    pool.submit(() -> consume(queue, start));
            Future<?> producerA = pool.submit(() -> {
                produce(queue, start, 0);
                return null;
            });
            Future<?> producerB = pool.submit(() -> {
                produce(queue, start, PER_PRODUCER);
                return null;
            });
            start.countDown();

            producerA.get(20, TimeUnit.SECONDS);
            producerB.get(20, TimeUnit.SECONDS);
            queue.offer(STOP);
            queue.offer(STOP);

            List<Integer> a = consumerA.get(20, TimeUnit.SECONDS);
            List<Integer> b = consumerB.get(20, TimeUnit.SECONDS);
            Set<Integer> unique = new HashSet<>();
            long sum = 0;
            for (int id : a) {
                require(unique.add(id), "duplicate id");
                sum += id;
            }
            for (int id : b) {
                require(unique.add(id), "duplicate id");
                sum += id;
            }
            int total = 2 * PER_PRODUCER;
            require(a.size() + b.size() == total, "wrong processed count");
            require(unique.size() == total, "missing work");
            require(sum == (long) total * (total - 1) / 2, "wrong checksum");
            require(queue.isEmpty(), "queue must be drained");

            System.out.println("processed = " + (a.size() + b.size()));
            System.out.println("distinct ids = " + unique.size());
            System.out.println("id sum = " + sum);
            System.out.println("queue empty = " + queue.isEmpty());
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static void produce(ConcurrentLinkedQueue<Work> queue,
            CountDownLatch start, int firstId) throws InterruptedException {
        await(start);
        for (int i = 0; i < PER_PRODUCER; i++) {
            queue.offer(new Work(firstId + i));
        }
    }

    private static List<Integer> consume(ConcurrentLinkedQueue<Work> queue,
            CountDownLatch start) throws InterruptedException {
        await(start);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        List<Integer> result = new ArrayList<>();
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("consumer interrupted");
            }
            if (System.nanoTime() - deadline >= 0) {
                throw new IllegalStateException("consumer timed out");
            }
            Work work = queue.poll();
            if (work == STOP) {
                return result;
            }
            if (work != null) {
                result.add(work.id());
            } else {
                Thread.onSpinWait();
            }
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start timed out");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Work(int id) {}
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all LinkedQueueTransferDemo.java
java LinkedQueueTransferDemo
```

Stdout khi chạy thành công:

```text
processed = 2000
distinct ids = 2000
id sum = 1999000
queue empty = true
```

Mỗi tác vụ nhận giữ một `ArrayList` riêng; không có hai luồng cùng ghi một danh sách kết quả. Sau khi `Future.get` trả về, luồng chính mới đọc các danh sách và hợp chúng để kiểm tra mã trùng, số lượng và tổng mã.

Chương trình không in số việc của từng bên nhận vì không có cam kết phân chia đều. Một bên có thể nhận nhiều hơn bên còn lại. Điều cần kiểm tra là các mã 0 đến 1.999 xuất hiện đúng trong kết quả tổng hợp.

### 5.3. Những chi tiết quan trọng của mã

**Nhóm luồng có bốn luồng.** Nếu chỉ có hai luồng rồi cho hai tác vụ nhận chiếm hết chúng để quay chờ, hai tác vụ tạo có thể nằm trong hàng chờ của executor và không có cơ hội chạy. Đó là lỗi phối hợp tác vụ, không phải lỗi của queue.

**`poll` vẫn không chờ dữ liệu.** Khi thấy null, mã chạy một vòng chờ chủ động có kiểm tra ngắt và thời hạn. `Thread.onSpinWait` không biến nó thành `take`.

Vòng chờ này chỉ phục vụ thí nghiệm hữu hạn, nhỏ và có thời hạn. Với ứng dụng cần ngủ chờ việc, một `BlockingQueue` có thao tác chờ phù hợp thường diễn đạt nhu cầu rõ hơn. Không sao chép vòng quay này vào một vòng phục vụ lâu dài mà chưa có thiết kế chờ và sử dụng CPU.

**Mã kiểm tra ngắt và chuyển lỗi ra ngoài.** Nếu tác vụ không tiến triển, thời hạn làm bài thử thất bại thay vì treo vô hạn. `finally` yêu cầu nhóm luồng dừng và chờ kết thúc.

**“Xử lý” trong ví dụ là ghi nhận mã vào danh sách cục bộ.** Nhận khỏi queue không tự cung cấp xác nhận bền vững, hoàn tác hay thử lại cho một tác vụ bên ngoài. Nếu công việc thực tế thất bại sau khi đã lấy, ứng dụng phải định nghĩa cách xử lý tiếp.

### 5.4. Vì sao FIFO không bảo đảm thứ tự hoàn tất?

Queue bảo vệ thứ tự các lần lấy theo thứ tự hàng đợi. Nhưng sau khi A lấy việc trước, nó có thể bị tạm dừng; B lấy việc sau và hoàn tất xử lý trước A.

Tương tự, khi nhiều bên cùng `offer` chồng lấp, không dùng thời điểm gọi hàm đo ở bên ngoài để tự đặt một thứ tự tuyệt đối khác với thứ tự mà queue xác lập cho các thao tác. Những lời gọi nối tiếp không chồng lấp có quan hệ thứ tự rõ ràng hơn.

Không nên in nhật ký sau xử lý rồi lấy thứ tự nhật ký làm bằng chứng queue đã vi phạm FIFO. Cần phân biệt lúc đưa vào, lúc lấy khỏi và lúc hoàn tất công việc.

Một tác vụ nhận gặp STOP cũng không chứng minh tác vụ nhận khác đã xử lý xong mọi phần tử nó từng lấy. Đó là lý do luồng chính chờ cả hai kết quả nhận.

### 5.5. Không chuyển nguyên giao thức STOP sang cách dùng hai đầu khác

Nếu thêm STOP ở đuôi deque rồi lại lấy bằng `pollLast`, bên nhận có thể lấy STOP trước những việc cũ còn ở đầu. Khi ấy lập luận FIFO của chương trình không còn đúng.

Mỗi khi đổi đầu thêm/lấy hoặc đổi số bên nhận, cần xác định lại số tín hiệu kết thúc và vị trí của chúng so với công việc. Collection không tự biết một phần tử nào là dữ liệu hay tín hiệu dừng.

## 6. Chương trình ConcurrentLinkedDeque: cùng lấy từ hai đầu

### 6.1. Điều kiện kết thúc khác ví dụ trước

Ở ví dụ này, luồng chính đưa sẵn 1.000 số vào deque **trước khi gửi tác vụ**, và không còn bên nào thêm nữa. Một tác vụ lấy đầu, tác vụ kia lấy đuôi.

Vì nguồn đã đóng theo quy tắc của chương trình, khi một tác vụ nhận null, nó có thể dừng. Không cần STOP hay chờ một bên tạo tương lai.

Lưu thành **`OppositeEndsDemo.java`**:

```java
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class OppositeEndsDemo {
    private static final int COUNT = 1_000;

    public static void main(String[] args) throws Exception {
        ConcurrentLinkedDeque<Integer> deque = new ConcurrentLinkedDeque<>();
        for (int i = 0; i < COUNT; i++) {
            deque.offerLast(i);
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Set<Integer>> first =
                    pool.submit(() -> drain(deque, start, true));
            Future<Set<Integer>> last =
                    pool.submit(() -> drain(deque, start, false));
            start.countDown();

            Set<Integer> a = first.get(10, TimeUnit.SECONDS);
            Set<Integer> b = last.get(10, TimeUnit.SECONDS);
            Set<Integer> overlap = new HashSet<>(a);
            overlap.retainAll(b);
            Set<Integer> all = new HashSet<>(a);
            all.addAll(b);
            long sum = all.stream().mapToLong(Integer::longValue).sum();

            require(overlap.isEmpty(), "same occurrence claimed twice");
            require(all.size() == COUNT, "missing element");
            require(sum == (long) COUNT * (COUNT - 1) / 2, "wrong checksum");
            require(deque.isEmpty(), "deque not drained");

            System.out.println("opposite-end claimed = " + all.size());
            System.out.println("overlap = " + overlap.size());
            System.out.println("id sum = " + sum);
            System.out.println("deque empty = " + deque.isEmpty());
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static Set<Integer> drain(ConcurrentLinkedDeque<Integer> deque,
            CountDownLatch start, boolean fromFirst) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start timed out");
        }
        Set<Integer> result = new HashSet<>();
        Integer value;
        while ((value = fromFirst ? deque.pollFirst() : deque.pollLast()) != null) {
            require(result.add(value), "local duplicate");
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all OppositeEndsDemo.java
java OppositeEndsDemo
```

Stdout khi chạy thành công:

```text
opposite-end claimed = 1000
overlap = 0
id sum = 499500
deque empty = true
```

Hai tập kết quả cục bộ không giao nhau và hợp lại đủ 1.000 phần tử. Khi chỉ còn một phần tử, cạnh tranh giữa `pollFirst` và `pollLast` không làm cùng một lần xuất hiện được trả cho cả hai tác vụ.

Một tác vụ có thể lấy hết trước khi tác vụ kia thực hiện nhiều thao tác; bài thử không yêu cầu hai phía chia đều 500/500. Các lớp này không hứa công bằng phân phối giữa các luồng.

### 6.2. Không gọi mọi cách dùng deque là FIFO

Nếu chỉ thêm đuôi/lấy đầu, deque dùng theo FIFO. Trong ví dụ hai đầu, một tác vụ lấy các phần tử phía đuôi trong khi tác vụ kia lấy phía đầu; đây là một quy tắc nhận việc khác.

Bài thử chỉ kiểm tra tính đúng của tập phần tử được lấy, không ép một thứ tự xử lý toàn cục theo thứ tự tăng của số. Nếu ứng dụng cần thứ tự hoàn tất tuyệt đối, phải có cơ chế điều phối công việc tương ứng.

Deque là khả năng truy cập hai đầu. Nó không tự trở thành một bộ lập lịch hoàn chỉnh, không tự phân chia quyền sở hữu công việc và không tự cung cấp chính sách ưu tiên hay công bằng.

## 7. Giới hạn về bộ nhớ, quan sát và phạm vi nguyên tử

### 7.1. Không giới hạn sức chứa không có nghĩa không cần điều tiết

Cả hai lớp không có một hạn mức phần tử cố định để `offer` trả false vì đầy. Nếu bên tạo liên tục nhanh hơn bên nhận, số nút và dữ liệu được giữ có thể tiếp tục tăng.

Đó không phải một cơ chế tự điều tiết áp lực đầu vào (backpressure). Bộ nhớ của JVM vẫn hữu hạn; sử dụng hàng đợi không giới hạn không thay thế việc kiểm soát tốc độ nhận việc, giới hạn số công việc đang xử lý hay chính sách quá tải.

`ConcurrentLinkedQueue` được tài liệu mô tả dùng thuật toán non-blocking. Ở mức cách dùng đang học, cả hai lớp không cung cấp `take`, `put` hay `poll(timeout, unit)` để chờ điều kiện dữ liệu/chỗ trống. Điều này không hứa mỗi thao tác có độ trễ cố định hay mỗi luồng được phục vụ công bằng.

Không nên dùng từ “không chặn” để suy ra “không tốn CPU khi chưa có việc”. Một vòng `while (poll() == null)` vẫn do chính chương trình lặp lại và có thể dùng CPU liên tục.

### 7.2. `size` không phải phép đọc hằng thời gian

Đối với cả hai lớp, `size()` phải duyệt để đếm; tài liệu nêu rõ nó không có chi phí hằng thời gian. Khi cấu trúc thay đổi trong lúc đếm, kết quả có thể không chính xác so với trạng thái tại một thời điểm mà bạn muốn dùng. [ConcurrentLinkedQueue — size](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html#size()), [ConcurrentLinkedDeque — size](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedDeque.html#size())

Không nên đặt `size()` trong vòng lặp nóng chỉ để biết có lấy được phần tử không. Dùng kết quả của `poll` cho lần lấy; nếu chỉ theo dõi tình trạng, vẫn phải hiểu dữ liệu quan sát có thể tạm thời.

Các mẫu sau không tạo giao thức nguyên tử:

- `size() > 0` rồi lấy.
- `size() < limit` rồi thêm.
- `contains(x)` rồi xóa hoặc xử lý x như thể nó còn được giữ lại.
- `peek()` rồi `poll()` và giả định chắc chắn nhận đúng cùng đối tượng.

Nếu phải giữ một điều kiện qua nhiều bước hoặc nhiều collection, cần cơ chế phối hợp đủ cho toàn bộ điều kiện đó.

### 7.3. Iterator nhất quán yếu và thao tác nhiều phần tử

Iterator/spliterator của hai lớp là nhất quán yếu. Có thể duyệt trong khi các luồng khác cập nhật, nhưng không được xem đó là ảnh chụp cố định toàn bộ cấu trúc.

Với deque, iterator thường đi đầu tới đuôi và `descendingIterator` đi đuôi tới đầu. Chính sách quan sát thay đổi và thứ tự duyệt là hai đặc tính khác nhau: nhất quán yếu không có nghĩa mất mọi quy tắc thứ tự.

Các thao tác nhiều phần tử như `addAll`, `removeIf` và `forEach` không được cam kết nguyên tử như một khối duy nhất. Người đọc đồng thời có thể quan sát một phần tác động của một nhóm thêm. [ConcurrentLinkedDeque — chính sách duyệt và bulk operations](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedDeque.html)

Không có `ConcurrentModificationException` không chứng minh một bản báo cáo có mọi số cùng phiên bản. Tương tự, `toArray` hoặc sao chép sang một collection mới không tự nâng mức nhất quán của quá trình thu thập nguồn đang thay đổi.

Nếu cần bản chụp nhất quán, hãy xác định giao thức chung cho mọi bên cập nhật và bên chụp, hoặc thiết kế trạng thái theo phiên bản. Khóa bên ngoài một chỗ không tự làm các luồng chỉ gọi API của queue/deque phải lấy khóa đó.

### 7.4. Công bố phần tử không làm trạng thái bên trong tự đồng bộ mãi

Hợp đồng bộ nhớ của concurrent collection quy định các hành động trước khi đặt một đối tượng vào được sắp thứ tự happens-before với các hành động sau khi luồng khác truy cập/lấy đối tượng đó theo hợp đồng. Điều này hỗ trợ việc tạo dữ liệu rồi chuyển nó qua hàng đợi. [java.util.concurrent — Memory Consistency Properties](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html#MemoryVisibility)

Tuy nhiên, queue/deque lưu và trả tham chiếu, không tự sao chép sâu đối tượng. Nếu bên tạo tiếp tục sửa các trường thường sau khi đưa vào trong lúc bên nhận đang đọc, các thay đổi tương lai đó vẫn cần cơ chế bảo vệ riêng.

Hai chương trình đa luồng dùng mã `Integer` hoặc record chỉ chứa `int`, nên tránh được trạng thái phần tử có thể bị sửa sau công bố. Với đối tượng nghiệp vụ có nhiều trường liên quan, nên định nghĩa quyền sở hữu hoặc tính bất biến/phối hợp trường rõ ràng.

### 7.5. Null bị cấm ở đầu vào, nhưng hợp lệ như tín hiệu trả về

Những lệnh minh họa sau cố ý vi phạm hợp đồng:

```java
queue.offer(null);       // NullPointerException.
deque.offerFirst(null);  // NullPointerException.
deque.offerLast(null);   // NullPointerException.
```

Các biến ở đây được hiểu là hai lớp đang học; đoạn này không phải chương trình độc lập. Constructor nhận collection cũng từ chối nguồn null hoặc phần tử null.

Ngược lại, `poll/peek` và các biến thể hai đầu trả null khi không có phần tử phù hợp trong lần gọi đó. Null đầu ra không phải một phần tử null đã được lưu.

Nếu dữ liệu nghiệp vụ thật sự có trạng thái “không có giá trị”, dùng một đối tượng phần tử khác null biểu diễn trạng thái đó theo thiết kế. Nếu cần tín hiệu dừng, dùng một phần tử điều khiển không thể nhầm với dữ liệu thông thường; không chèn null.

## 8. Xóa theo giá trị và chọn cấu trúc theo nhu cầu

### 8.1. Xóa đầu/đuôi khác xóa một lần xuất hiện

`pollFirst`/`pollLast` chọn theo vị trí đầu hoặc đuôi. `removeFirstOccurrence(x)`/`removeLastOccurrence(x)` tìm một phần tử bằng x theo chiều tương ứng.

Đoạn minh họa tuần tự, với import `Deque`, `List` và `ConcurrentLinkedDeque`:

```java
Deque<String> values = new ConcurrentLinkedDeque<>(
        List.of("A", "B", "A", "C", "A"));

values.removeFirstOccurrence("A"); // [B, A, C, A]
values.removeLastOccurrence("A");  // [B, A, C]
values.remove("A");                // [B, C]
```

`remove(Object)` của deque tương đương xóa lần xuất hiện đầu tiên. Nếu không tìm thấy giá trị, phép xóa theo giá trị trả false. Nó không có cùng ý nghĩa với `remove()` không đối số vốn lấy đầu và ném ngoại lệ khi trống.

Các phép tìm/xóa theo giá trị không chỉ chọn trực tiếp một đầu. Nếu ứng dụng chủ yếu tra cứu/xóa theo mã, cần xem một cấu trúc theo khóa hay một chỉ mục khác có phù hợp hơn không, thay vì coi queue như một map.

### 8.2. Bảng lựa chọn theo yêu cầu

| Nhu cầu | Cách tiếp cận nên xem xét | Điều phải xác định |
| --- | --- | --- |
| Nhiều luồng thêm đuôi/lấy đầu, lần lấy không chờ | `ConcurrentLinkedQueue` | Kết thúc, quá tải, lỗi xử lý sau lấy |
| Cần chọn cả đầu và đuôi đồng thời | `ConcurrentLinkedDeque` | Đầu thêm/lấy cụ thể và ý nghĩa thứ tự |
| Cần ngủ chờ tới khi có việc hoặc chờ có thời hạn | `BlockingQueue` phù hợp | Ngắt, thời hạn, kết thúc và có cần giới hạn sức chứa không |
| Cần chờ theo hai đầu | `BlockingDeque` phù hợp | Quy tắc đầu/đuôi và sức chứa |
| Dữ liệu chỉ do một luồng sở hữu | Cấu trúc thông thường phù hợp với nhu cầu | Không chia sẻ ngoài quyền sở hữu đó |
| Cần chống trùng theo mã | Giao thức có set/map hoặc cơ chế nhận việc phù hợp | Phạm vi chống trùng và vòng đời mã |
| Cần lưu việc bền vững/khôi phục sau lỗi tiến trình | Cơ chế lưu trữ và xác nhận tương ứng | Queue trong bộ nhớ không tự cung cấp yêu cầu này |

`BlockingQueue` bổ sung các thao tác như `take`, `put`, lấy/thêm có thời hạn; có triển khai có hoặc không có giới hạn sức chứa. `BlockingDeque` mở rộng ý tưởng đó tới hai đầu. [BlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html), [BlockingDeque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingDeque.html)

Không phải thêm chữ “concurrent” vào tên cấu trúc là chọn xong kiến trúc. Yêu cầu về chờ, giới hạn, thứ tự và vòng đời công việc cần được viết rõ trước.

### 8.3. Kiểm thử theo hợp đồng, không theo phân chia may rủi

Với nhiều luồng, hãy kiểm tra số phần tử cần nhận, tập mã, phần tử trùng, điều kiện kết thúc và việc lỗi tác vụ được quan sát.

Không khẳng định hai bên nhận phải được chia đều, không lấy nhật ký hoàn tất làm thứ tự lấy khỏi hàng đợi, và không dùng `sleep` để chứng minh rằng bên tạo đã chắc chắn hoàn tất.

Ba chương trình trong bài là minh chứng chạy được cho các giao thức đã định. Chúng không phải phép đo hiệu năng và không chứng minh mọi giao thức sử dụng hai lớp đều đúng. Khi thay cách thêm/lấy hay cách báo dừng, cần phân tích lại lịch xen kẽ và các điều kiện bất biến.

## 9. Luyện tập

### Bài 1 — Queue trống trong một khoảnh khắc

Một bên nhận gọi `poll()`, thấy null rồi dừng; bên tạo vẫn đang hoạt động.

**Câu hỏi:** Lần lấy có sai không? Giao thức dừng có đủ cơ sở không?

**Đối chiếu:** Lần lấy có thể hoàn toàn đúng vì queue đang trống ở thời điểm đó. Dừng vĩnh viễn cần biết không còn dữ liệu tương lai hoặc có một tín hiệu kết thúc khác; null tạm thời không cung cấp điều đó.

### Bài 2 — FIFO và thứ tự in kết quả

A lấy việc 1 rồi mất nhiều thời gian; B lấy việc 2 rồi in kết quả trước A.

**Câu hỏi:** Có thể kết luận `ConcurrentLinkedQueue` vi phạm FIFO không?

**Đối chiếu:** Không. Thứ tự lấy và thứ tự hoàn tất là hai sự kiện khác nhau. Cần quan sát đúng ranh giới mà hợp đồng FIFO mô tả.

### Bài 3 — STOP ở sai đầu

Chương trình thêm mọi việc bằng `offerLast`, sau đó thêm STOP bằng `offerLast`, nhưng bên nhận dùng `pollLast`.

**Câu hỏi:** Vì sao có thể dừng khi vẫn còn việc?

**Đối chiếu:** STOP nằm ở đuôi nên được lấy trước các việc cũ ở đầu. Giao thức chèn tín hiệu cuối dựa trên FIFO không còn phù hợp với cách lấy LIFO/ở đuôi đó.

### Bài 4 — Hai lần thêm cùng đối tượng

Một đối tượng công việc được đưa vào hai lần. Hai bên nhận cùng lấy được tham chiếu tới đối tượng đó.

**Câu hỏi:** Queue có nhất thiết phát trùng một lần xuất hiện không? Có cần xét trạng thái bên trong đối tượng không?

**Đối chiếu:** Có hai lần xuất hiện hợp lệ vì queue cho trùng. Hai lần lấy có thể nhận cùng tham chiếu từ hai vị trí khác nhau. Nếu cả hai cùng sửa đối tượng, trạng thái đối tượng cần giao thức riêng.

### Bài 5 — Giới hạn bằng `size`

Hai bên tạo cùng kiểm tra `queue.size() < 100` rồi thêm.

**Câu hỏi:** Có thể xem queue đã có sức chứa tối đa 100 không?

**Đối chiếu:** Không. Phép đếm có thể tốn thời gian và sai lệch khi cập nhật, còn kiểm tra–thêm là nhiều bước. Hai lớp đang học cũng không có giới hạn nội tại đó. Muốn giới hạn cần cơ chế tương ứng với yêu cầu.

### Bài 6 — Hai đầu nhưng chỉ một phần tử

Deque có một phần tử và không còn bên thêm. A gọi `pollFirst`, B gọi `pollLast`.

**Câu hỏi:** Hai bên có được nhận cùng một lần xuất hiện không? Một bên thấy null có được dừng trong điều kiện đã nêu không?

**Đối chiếu:** Chỉ một lần lấy nhận được phần tử; bên kia có thể nhận null. Vì đã biết không còn bên thêm, tác vụ đó được dừng, trong khi việc tổng kết vẫn cần chờ tác vụ còn lại hoàn thành xử lý phần tử đã lấy.

## 10. Tài liệu đối chiếu

- [ConcurrentLinkedQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html): FIFO, không nhận null, duyệt và giới hạn thao tác tổng hợp.
- [ConcurrentLinkedDeque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedDeque.html): truy cập hai đầu, iterator hai chiều và xóa theo lần xuất hiện.
- [Queue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html), [Deque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html): ý nghĩa từng nhóm phương thức.
- [java.util.concurrent Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html): quan hệ công bố dữ liệu và các họ cấu trúc concurrent.
- [BlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html), [BlockingDeque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingDeque.html): đối chiếu nhu cầu chờ và giới hạn.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-queues-002">BlockingQueue: put, take và thao tác có thời hạn chờ</a>

<details>
<summary>Xem chi tiết</summary>

# BlockingQueue: put, take và thao tác có thời hạn chờ

## 1. Hàng đợi còn là nơi phối hợp giữa các luồng

Một luồng tạo công việc, một luồng khác xử lý công việc. Nếu dùng một hàng đợi thông thường, bên xử lý phải biết làm gì khi chưa có phần tử; bên tạo phải biết làm gì khi không còn chỗ. Tự viết vòng lặp kiểm tra, ngủ rồi kiểm tra lại khiến chương trình vừa khó dừng vừa dễ dùng CPU không cần thiết.

`BlockingQueue<E>` bổ sung các thao tác có khả năng chờ điều kiện phù hợp. Bên tạo dữ liệu được gọi là bên sản xuất (producer); bên lấy dữ liệu để xử lý là bên tiêu thụ (consumer).

Với hàng đợi có giới hạn dung lượng, mô hình cơ bản là:

```text
Bên sản xuất
    → đưa công việc vào hàng đợi
        → bên tiêu thụ lấy công việc ra
            → xử lý công việc

Khi hết chỗ: bên đưa vào có thể chờ hoặc từ bỏ.
Khi chưa có phần tử để lấy: bên lấy ra có thể chờ hoặc từ bỏ.
```

Cần phân biệt **chờ để đưa/lấy dữ liệu** với **chờ công việc được xử lý xong**. Một lời gọi đưa vào thành công chưa nói công việc đã được xử lý. Một lời gọi lấy ra thành công cũng chưa nói việc xử lý sau đó sẽ thành công.

Bài này tập trung vào `put`, `take`, `offer` và `poll` có thời hạn chờ, cùng gián đoạn, dung lượng và điều kiện dừng. Các ví dụ dùng `ArrayBlockingQueue` để có dung lượng cố định và thứ tự FIFO rõ ràng.

`BlockingQueue` là giao diện; không suy ra mọi lớp triển khai đều lấy phần tử theo FIFO hoặc đều có một vùng lưu trữ với cùng cách hoạt động. Ví dụ dùng hàng đợi theo ưu tiên hoặc theo thời gian sẵn sàng sẽ có quy tắc chọn phần tử khác.

Các chương trình dưới đây độc lập, dùng Java 17 trở lên và không cần thư viện ngoài. Lưu đúng tên tệp, biên dịch và chạy trong thư mục chứa tệp. Kết quả nhiều luồng được thu lại trước khi in để không phụ thuộc vào thứ tự in giữa các luồng.

## 2. Chọn cách phản ứng khi thao tác chưa thực hiện được

Một nhóm phương thức cho phép chọn chính sách ngay tại điểm gọi:

| Nhu cầu | Báo bằng ngoại lệ nếu chưa làm được | Trả giá trị đặc biệt | Chờ không có timeout do bên gọi đặt | Chờ có timeout |
|---|---|---|---|---|
| Thêm phần tử | `add(e)` | `offer(e)` | `put(e)` | `offer(e, timeout, unit)` |
| Lấy và xóa đầu hàng đợi | `remove()` | `poll()` | `take()` | `poll(timeout, unit)` |
| Chỉ xem đầu hàng đợi | `element()` | `peek()` | Không có dạng tương ứng | Không có dạng tương ứng |

Khi không còn chỗ, `offer` trả `false`; khi không có phần tử để lấy, `poll` trả `null`. Dạng có timeout dùng cùng các giá trị đó nếu không thực hiện được trong thời gian chờ. `put` không trả một cờ thành công; `take` trả phần tử lấy được. Xem [bảng phương thức của BlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).

### 2.1. Giá trị đặc biệt khác lỗi đầu vào

`false` không có nghĩa mọi lỗi của `offer` đều được chuyển thành `false`. Ví dụ, đưa `null` vào `BlockingQueue` gây `NullPointerException`. Các lỗi về kiểu hoặc thuộc tính phần tử còn phụ thuộc quy định của lớp triển khai.

Việc không cho phép phần tử `null` giúp phân biệt kết quả `poll` không có dữ liệu với một dữ liệu thật. Không dùng `null` làm thông điệp kết thúc đặt trong hàng đợi.

Cũng cần đọc đúng overload: `remove()` lấy đầu hàng đợi và có thể ném lỗi khi rỗng; `remove(Object)` tìm và xóa một phần tử phù hợp. Chúng không có cùng mục đích.

### 2.2. Chương trình với thao tác không chờ điều kiện

Tệp `QueueMethodFormsDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class QueueMethodFormsDemo {
    public static void main(String[] args) {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        System.out.println("offerA=" + queue.offer("A"));
        System.out.println("offerB=" + queue.offer("B"));
        System.out.println("peek=" + queue.peek());

        try {
            queue.add("B");
        } catch (IllegalStateException ex) {
            System.out.println("fullAdd=" + ex.getClass().getSimpleName());
        }
        System.out.println("remove=" + queue.remove());
        System.out.println("emptyPoll=" + queue.poll());
        try {
            queue.element();
        } catch (java.util.NoSuchElementException ex) {
            System.out.println("emptyElement=" + ex.getClass().getSimpleName());
        }
        try {
            queue.offer(null);
        } catch (NullPointerException ex) {
            System.out.println("nullOffer=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 QueueMethodFormsDemo.java
java QueueMethodFormsDemo
```

Stdout:

```text
offerA=true
offerB=false
peek=A
fullAdd=IllegalStateException
remove=A
emptyPoll=null
emptyElement=NoSuchElementException
nullOffer=NullPointerException
```

Dung lượng chỉ là 1. A được thêm thành công, B chưa thể thêm nên `offer` trả `false`. `peek` chỉ xem A; vì A vẫn còn nên `add("B")` ném `IllegalStateException`.

Sau khi `remove()` lấy A, `poll()` trả `null` vì rỗng. `element()` trên hàng đợi rỗng ném `NoSuchElementException`. Lần đưa `null` bị từ chối bằng ngoại lệ riêng.

Các kết quả này được tạo trong một luồng, không có tranh chấp. Chúng giúp nhận ra chính sách của từng phương thức trước khi thêm phần chờ giữa các luồng.

“Không chờ” ở đây nghĩa là không đợi hàng đợi có chỗ hoặc có phần tử. Đừng suy ra một lời gọi không có timeout luôn là thuật toán không khóa hoặc không thể mất thời gian do phối hợp nội bộ.

## 3. put và take: chờ điều kiện thay đổi

### 3.1. put chờ chỗ, take chờ phần tử

Với `ArrayBlockingQueue`, dung lượng được ấn định khi tạo và phần tử được lấy theo FIFO. Nếu đầy, `put(e)` chờ có chỗ để thêm; nếu rỗng, `take()` chờ có phần tử để lấy. Nếu điều kiện đã phù hợp, chúng có thể hoàn thành mà không cần chờ một luồng khác. Xem [ArrayBlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html).

Đừng hiểu “blocking” là chương trình tự tạo thêm luồng. Chính luồng đang gọi phương thức có thể phải chờ. Nếu luồng đó đang phục vụ một yêu cầu, khả năng phục vụ của nó cũng bị giữ trong thời gian chờ.

Một lần `take` thành công loại phần tử khỏi hàng đợi. Hai bên tiêu thụ không cùng nhận một lần lấy của cùng phần tử còn nằm trong hàng đợi. Tuy vậy, ứng dụng có thể chủ động thêm cùng một đối tượng nhiều lần; cấu trúc này không phải set loại trùng.

### 3.2. Chương trình phối hợp đưa và lấy

Tệp `WaitingPutTakeDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WaitingPutTakeDemo {
    public static void main(String[] args) throws Exception {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        queue.put("first");
        var producerStarted = new CountDownLatch(1);
        var consumerStarted = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);

        try {
            var producer = pool.submit(() -> {
                producerStarted.countDown();
                queue.put("second");
                return "put completed";
            });
            if (!producerStarted.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("producer did not start");
            }

            System.out.println("takenFirst=" + queue.take());
            System.out.println("producer=" + producer.get(10, TimeUnit.SECONDS));
            System.out.println("takenSecond=" + queue.take());

            var consumer = pool.submit(() -> {
                consumerStarted.countDown();
                return queue.take();
            });
            if (!consumerStarted.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("consumer did not start");
            }
            queue.put("third");
            System.out.println("consumer=" + consumer.get(10, TimeUnit.SECONDS));
            System.out.println("empty=" + queue.isEmpty());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 WaitingPutTakeDemo.java
java WaitingPutTakeDemo
```

Stdout:

```text
takenFirst=first
producer=put completed
takenSecond=second
consumer=third
empty=true
```

Hàng đợi ban đầu chứa `"first"`. Bên sản xuất muốn đưa `"second"` vào. Luồng chính lấy `"first"`, nhường chỗ; sau khi tác vụ sản xuất hoàn tất, luồng chính lấy `"second"`.

Ở phần sau, bên tiêu thụ gọi `take` trên hàng đợi hiện không có dữ liệu. Luồng chính đưa `"third"` vào và bên tiêu thụ nhận được nó.

Các cổng `producerStarted`, `consumerStarted` dùng để biết tác vụ đã đi đến điểm trước lời gọi hàng đợi. Cổng được mở không chứng minh luồng lập tức chuyển sang trạng thái chờ ngay ở lệnh tiếp theo. Lịch thực thi có thể khiến luồng chính làm thay đổi điều kiện trước khi lời gọi hàng đợi thật sự bắt đầu.

Kết quả đúng của chương trình không cần đoán thời điểm đó: việc đưa `"second"` không thể hoàn thành trước khi chỗ của `"first"` được giải phóng, và bên tiêu thụ cuối chỉ nhận dữ liệu sau khi dữ liệu đã được đưa vào.

Timeout 10 giây ở cổng và `Future.get` là giới hạn để phát hiện thí nghiệm không tiến triển. Nó không làm cho các lời gọi `put` và `take` trong tác vụ tự có timeout 10 giây.

### 3.3. Chờ không có timeout vẫn cần một lối kết thúc

Một lời gọi `take()` có thể tiếp tục chờ nếu không còn ai đưa dữ liệu. Một lời gọi `put()` có thể tiếp tục chờ nếu hàng đợi đầy và không còn ai lấy dữ liệu. Chương trình phải xác định bên nào còn có thể làm điều kiện thay đổi.

Các cách kết thúc thường cần xem xét là gián đoạn luồng, thông điệp kết thúc theo một quy ước, hoặc thay lời gọi bằng dạng có timeout để định kỳ kiểm tra trạng thái. Chọn cách nào tùy vòng đời của quy trình.

Không chuyển sang `put` chỉ vì muốn “không mất dữ liệu” mà bỏ qua khả năng bên tiêu thụ đã dừng. Dữ liệu chưa mất khỏi biến ở bên sản xuất không đồng nghĩa hệ thống còn tiến triển được.

## 4. offer và poll có thời hạn chờ

### 4.1. Đọc kết quả theo ba nhánh

Với `offer(e, timeout, unit)`:

- `true`: phần tử đã được đưa vào.
- `false`: không đưa được phần tử trong thời gian chờ theo phép gọi.
- `InterruptedException`: luồng bị gián đoạn trong thao tác chờ.

Với `poll(timeout, unit)`:

- Trả phần tử khác `null`: phần tử đã được lấy và xóa khỏi hàng đợi.
- `null`: không lấy được phần tử trong thời gian chờ.
- `InterruptedException`: thao tác chờ bị gián đoạn.

Không gom nhánh gián đoạn thành nhánh hết thời gian. Một nhánh là chính sách về thời gian, nhánh kia thường là yêu cầu hủy hoặc dừng từ cơ chế quản lý luồng.

Hai phương thức này không dùng `TimeoutException` để báo timeout. Đừng nhầm với `Future.get(timeout, unit)`, vốn là một API chờ kết quả khác.

### 4.2. Chương trình với điều kiện đầy và rỗng ổn định

Tệp `TimedQueueDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TimedQueueDemo {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        queue.put("A");

        boolean accepted = queue.offer("B", 40, TimeUnit.MILLISECONDS);
        System.out.println("fullTimedOffer=" + accepted);
        System.out.println("existing=" + queue.take());

        String missing = queue.poll(40, TimeUnit.MILLISECONDS);
        System.out.println("emptyTimedPoll=" + missing);

        System.out.println("availableTimedOffer=" +
                queue.offer("C", 1, TimeUnit.SECONDS));
        System.out.println("availableTimedPoll=" +
                queue.poll(1, TimeUnit.SECONDS));

        System.out.println("zeroTimedOffer=" +
                queue.offer("D", 0, TimeUnit.NANOSECONDS));
        System.out.println("zeroTimedPoll=" +
                queue.poll(0, TimeUnit.NANOSECONDS));
        System.out.println("zeroTimedPollEmpty=" +
                queue.poll(0, TimeUnit.NANOSECONDS));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TimedQueueDemo.java
java TimedQueueDemo
```

Stdout:

```text
fullTimedOffer=false
existing=A
emptyTimedPoll=null
availableTimedOffer=true
availableTimedPoll=C
zeroTimedOffer=true
zeroTimedPoll=D
zeroTimedPollEmpty=null
```

Khi hàng đợi có A và không có bên lấy trong lúc `offer("B", ...)`, kết quả là `false`. A vẫn còn. Sau khi lấy A, không có bên sản xuất trong lúc `poll(...)`, nên kết quả là `null`.

Khi điều kiện phù hợp, dạng có timeout vẫn có thể thực hiện ngay: C được đưa vào và lấy ra mà không phải dùng hết một giây. Timeout là giới hạn cho việc chờ điều kiện, không phải yêu cầu phải ngủ đúng bằng thời gian đó.

Ở các lời gọi có thời gian 0, hàng đợi vẫn được thử theo điều kiện hiện tại: D được đưa vào khi có chỗ, rồi lấy ra khi có dữ liệu. Khi đã rỗng, lần `poll` tiếp trả `null`. Không dùng 0 với ý nghĩa “chờ mãi”.

Không in số mili giây đã trôi trong stdout, vì thời gian thực tế phụ thuộc môi trường thực thi. Điều được kiểm tra ở đây là nhánh kết quả và dữ liệu còn lại, không phải một con số thời gian chính xác.

### 4.3. TimeUnit và giới hạn của độ chính xác

`TimeUnit.MILLISECONDS` và `TimeUnit.SECONDS` làm cùng một số 50 mang ý nghĩa khác nhau. Viết rõ đơn vị ở điểm gọi giúp tránh nhầm giây với mili giây.

Đơn vị nanosecond không hứa hệ điều hành hoặc bộ lập lịch có thể đánh thức luồng chính xác đến nanosecond. Xem [ý nghĩa và giới hạn độ phân giải của TimeUnit](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeUnit.html).

Một luồng được đánh thức còn phải được cấp CPU và hoàn tất phần phối hợp cần thiết trước khi bên gọi quan sát kết quả. Vì vậy, không lấy timeout như một cam kết thời gian thực cứng rằng phương thức chắc chắn trở về trước một thời điểm trên đồng hồ.

### 4.4. Timeout không hoàn tác một thao tác đã thành công

Nếu `offer` trả `true`, công việc đã được thêm; việc bên gọi sau đó nhận ra yêu cầu gốc đã quá hạn không tự xóa công việc khỏi hàng đợi.

Nếu `poll` trả một phần tử, phần tử đó đã bị lấy ra. Một lỗi xảy ra trong bước xử lý sau không tự đưa nó về vị trí cũ.

Từ đó cần xác định “quá hạn” ở tầng nào:

- Hết thời gian được phép chờ chỗ trong hàng đợi.
- Hết thời gian được phép chờ để lấy một công việc.
- Công việc đã nằm trong hàng đợi quá lâu.
- Yêu cầu toàn bộ phải hoàn tất trước một hạn nghiệp vụ.

Đây là những chính sách riêng. Chỉ truyền timeout vào `offer` không giải quyết ba chính sách còn lại.

## 5. InterruptedException và cách giữ đúng tín hiệu dừng

### 5.1. Gián đoạn là một nhánh điều khiển

`put`, `take` và các dạng `offer`/`poll` có thời hạn chờ khai báo `InterruptedException`. Một lời gọi đang chờ có thể được yêu cầu ngừng chờ bằng `interrupt()`.

Gián đoạn không phải `Thread.stop`: nó không cưỡng bức kết thúc mọi mã xử lý của một luồng. Đoạn mã nhận tín hiệu phải có cách phản ứng phù hợp.

Có hai cách xử lý phổ biến:

1. Phương thức còn có thể báo lỗi lên bên gọi: truyền tiếp `InterruptedException`.
2. Ở ranh giới như `Runnable.run`, không truyền ngoại lệ kiểm tra được lên trên: khôi phục trạng thái gián đoạn, rồi kết thúc tác vụ theo chính sách.

Khôi phục cờ rồi tiếp tục gọi thao tác chờ trong một vòng lặp vô hạn thường không thể hiện một quyết định dừng hợp lý. Tác vụ phải quyết định có thoát, dọn tài nguyên hay báo kết quả gì.

### 5.2. Chương trình gián đoạn một bên lấy dữ liệu

Tệp `InterruptQueueDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class InterruptQueueDemo {
    public static void main(String[] args) throws Exception {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        var started = new CountDownLatch(1);
        var outcome = new AtomicReference<String>();

        Thread consumer = new Thread(() -> {
            started.countDown();
            try {
                queue.take();
                outcome.set("unexpected item");
            } catch (InterruptedException ex) {
                boolean cleared = !Thread.currentThread().isInterrupted();
                Thread.currentThread().interrupt();
                outcome.set("failure=" + ex.getClass().getSimpleName()
                        + ", cleared=" + cleared
                        + ", restored=" + Thread.currentThread().isInterrupted());
            }
        }, "queue-consumer");

        consumer.start();
        try {
            if (!started.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("consumer did not start");
            }
            consumer.interrupt();
            consumer.join(10_000);
            if (consumer.isAlive()) {
                throw new IllegalStateException("consumer did not stop");
            }
            System.out.println(outcome.get());
            System.out.println("queueEmpty=" + queue.isEmpty());
            System.out.println("consumerAlive=" + consumer.isAlive());
        } finally {
            consumer.interrupt();
            consumer.join(10_000);
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 InterruptQueueDemo.java
java InterruptQueueDemo
```

Stdout:

```text
failure=InterruptedException, cleared=true, restored=true
queueEmpty=true
consumerAlive=false
```

Không có bên sản xuất nào đưa phần tử vào hàng đợi. Luồng chính yêu cầu gián đoạn sau cổng `started`. Tín hiệu có thể đến trước khi `take` thật sự chờ hoặc trong lúc nó đang chờ; cả hai tình huống đều phù hợp với thí nghiệm.

Ở ví dụ dùng `ArrayBlockingQueue` này, khi bắt ngoại lệ, cờ gián đoạn đã được xóa. Tác vụ kiểm tra điều đó, gọi `interrupt()` trên chính mình để khôi phục cờ, ghi kết quả rồi kết thúc.

Cách sử dụng `InterruptedException` có thể bao gồm việc kiểm tra và xóa trạng thái bằng `Thread.interrupted()`. Xem [InterruptedException Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InterruptedException.html).

`isInterrupted()` chỉ đọc trạng thái; `Thread.interrupted()` đọc rồi xóa trạng thái của luồng hiện tại. Không gọi phương thức thứ hai chỉ để ghi nhật ký nếu không muốn tiêu thụ tín hiệu. Xem [hợp đồng kiểm tra trạng thái của Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#interrupted()).

### 5.3. Mẫu sai: biến gián đoạn thành “chưa có dữ liệu”

Đoạn sau là mã minh họa sai, không phải chương trình độc lập:

```java
// SAI: mất tín hiệu dừng và tiếp tục chờ vô hạn.
while (true) {
    try {
        handle(queue.take());
    } catch (InterruptedException ex) {
        System.out.println("try again");
    }
}
```

Nếu chủ sở hữu luồng muốn dừng tác vụ, vòng lặp lại nuốt tín hiệu và bắt đầu chờ tiếp. Một thông báo nhật ký không thay thế việc thực hiện chính sách hủy.

Một mẫu xử lý ở ranh giới tác vụ có thể là:

```java
// Mã minh họa trong thân một Runnable, chưa phải chương trình độc lập.
try {
    while (!Thread.currentThread().isInterrupted()) {
        handle(queue.take());
    }
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
}
```

Ở mẫu này, nhánh bắt nằm ngoài vòng lặp nên tác vụ rời vòng xử lý khi thao tác chờ bị gián đoạn. Hàm `handle` và việc dọn tài nguyên còn phải tuân theo thiết kế ứng dụng; tín hiệu gián đoạn không tự hoàn tác công việc đã làm dở.

### 5.4. Dừng bộ thực thi khác với đóng hàng đợi

Gọi `shutdown()` trên `ExecutorService` không tự chèn một thông điệp vào hàng đợi của ứng dụng và không tự khiến một vòng `take()` đang chờ biết rằng nguồn dữ liệu đã hết.

`shutdownNow()` cố gắng ngăn tác vụ chưa chạy và dừng các tác vụ đang chạy, thường qua gián đoạn; nó không cam kết mọi mã sẽ dừng nếu mã đó không hợp tác. Xem [vòng đời của ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html).

Trong các chương trình minh họa, phần `finally` dùng `shutdownNow()` để yêu cầu dọn các tác vụ còn lại nếu thí nghiệm thất bại. Đó không phải giao thức xử lý hoàn tất mọi công việc của một hệ thống thật.

## 6. Dung lượng, áp lực ngược và khả năng tiến triển

### 6.1. Giới hạn hàng đợi chỉ có tác dụng khi chính sách đi kèm phù hợp

Nếu bên sản xuất nhanh hơn bên tiêu thụ, công việc chờ tăng lên. Một hàng đợi có giới hạn buộc ứng dụng thể hiện điều sẽ xảy ra khi đầy:

| Lựa chọn | Hệ quả cần chấp nhận |
|---|---|
| `put` | Bên sản xuất có thể phải chờ đến khi có chỗ hoặc bị gián đoạn |
| `offer` | Bên sản xuất nhận ngay kết quả không thêm được theo điều kiện dung lượng |
| `offer` có timeout | Bên sản xuất chỉ dành một khoảng chờ cho lần thêm này |
| Chuyển dữ liệu sang nơi khác khi không thêm được | Cần một chính sách dung lượng và lỗi cho nơi nhận thay thế |

Việc làm chậm bên sản xuất khi bên tiêu thụ không theo kịp thường được gọi là áp lực ngược (backpressure). Hàng đợi có giới hạn tạo một điểm để áp dụng chính sách đó; nó không tự chọn giúp ứng dụng nên bỏ, thử lại, báo lỗi hay lưu bền.

Nếu `offer` trả `false` mà bên gọi bỏ qua, dữ liệu có thể bị bỏ quên. Nếu bên gọi thử lại vô hạn với một timeout mới mỗi lần, giới hạn chờ tổng thể có thể biến mất. Nếu chuyển mọi công việc thất bại vào một danh sách không giới hạn, vấn đề dung lượng chỉ được dời sang nơi khác.

### 6.2. Không mặc định mọi hàng đợi đều có giới hạn nhỏ

`LinkedBlockingQueue` cho phép chỉ định dung lượng. Nếu dùng constructor không tham số, giới hạn mặc định là `Integer.MAX_VALUE`, không phải một giới hạn nhỏ giúp điều tiết tải thực tế. Xem [constructor và dung lượng của LinkedBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html).

Bộ nhớ có thể cạn trước khi chạm một giới hạn số lượng rất lớn. Kích thước mỗi phần tử, dữ liệu mà nó giữ tham chiếu và số công việc đang được xử lý đều cần được tính đến khi chọn dung lượng.

Đừng suy ra `put` sẽ tạo áp lực ngược hữu ích chỉ từ việc biến có kiểu `BlockingQueue`. Cần xem lớp triển khai và cách tạo đối tượng.

### 6.3. remainingCapacity không đặt chỗ trước

Đoạn minh họa dưới đây không đảm bảo lần thêm sẽ thành công ngay:

```java
// Mã minh họa có khoảng trống giữa kiểm tra và hành động.
if (queue.remainingCapacity() > 0) {
    queue.put(job);
}
```

Sau khi bên này thấy còn chỗ, bên khác có thể lấy chỗ đó. `put` vẫn có thể phải chờ. Tương tự, kiểm tra `isEmpty()` rồi `take()` không đảm bảo việc lấy không chờ.

Nếu muốn thử mà không chờ điều kiện, dùng chính `offer` hoặc `poll` rồi xử lý kết quả. Nếu muốn chờ giới hạn, dùng dạng có timeout. Việc kiểm tra trước không thay thế chính sách của thao tác thực tế.

`size` và `remainingCapacity` vẫn hữu ích để quan sát tải, nhưng không phải vé giữ chỗ cho một bước sau.

### 6.4. Tránh giữ tài nguyên mà bên làm thay đổi điều kiện cần đến

Một tình huống thiết kế có thể không tiến triển:

```text
Bên sản xuất giữ khóa L → gọi put trên hàng đợi đầy → chờ
Bên tiêu thụ cần khóa L → mới có thể đi đến bước take
```

Hàng đợi không thể tự giải phóng một khóa ngoài mà mã ứng dụng đang giữ. Cần thu hẹp vùng giữ tài nguyên hoặc thay cách phối hợp.

Một tình huống khác là dùng cùng một nhóm thực thi nhỏ cho nhiều tác vụ sản xuất và tiêu thụ. Nếu tất cả luồng đều bị giữ bởi các bên `put` đang chờ, còn tác vụ tiêu thụ chưa được chạy, không còn ai nhường chỗ.

Không cần tránh mọi lời gọi chặn; cần chỉ rõ ai còn có thể làm điều kiện thay đổi trong lúc lời gọi ấy chờ.

### 6.5. FIFO của phần tử khác công bằng của luồng

`ArrayBlockingQueue` dùng FIFO cho phần tử. Constructor có cờ công bằng còn điều khiển thứ tự cấp quyền cho các bên đang chờ theo chính sách của lớp.

Hai vấn đề khác nhau: thứ tự công việc được lấy không tự nói luồng nào được chạy trước, và một luồng lấy công việc trước có thể xử lý xong sau luồng khác. Công bằng cũng có chi phí; không đặt cờ chỉ vì tên nghe có vẻ luôn tốt hơn.

## 7. Giao thức kết thúc: thông điệp dừng phải được thiết kế

### 7.1. BlockingQueue không tự biết bên sản xuất đã hết việc

Một lần `poll` trả `null` chỉ nói lần lấy đó không nhận được dữ liệu trong thời gian chờ. Nó không chứng minh không còn bên sản xuất nào sẽ thêm dữ liệu sau này.

Giao diện không có một thao tác đóng chung dùng để thông báo vĩnh viễn hết dữ liệu. Một cách thường dùng là thông điệp kết thúc riêng do ứng dụng nhận diện.

Không dùng một chuỗi có thể trùng dữ liệu thật như `"STOP"` nếu miền dữ liệu cho phép chuỗi đó. Không dùng `null`. Một kiểu thông điệp rõ ràng giúp tách công việc và tín hiệu kết thúc.

### 7.2. Chương trình một bên sản xuất, một bên tiêu thụ

Tệp `ProducerConsumerStopDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProducerConsumerStopDemo {
    sealed interface Message permits Data, Stop {}
    record Data(int value) implements Message {}
    enum Stop implements Message { INSTANCE }
    record Totals(int count, int sum) {}

    static Totals consume(BlockingQueue<Message> queue)
            throws InterruptedException {
        int count = 0;
        int sum = 0;
        while (true) {
            Message message = queue.take();
            if (message == Stop.INSTANCE) {
                return new Totals(count, sum);
            }
            Data data = (Data) message;
            count++;
            sum += data.value();
        }
    }

    public static void main(String[] args) throws Exception {
        BlockingQueue<Message> queue = new ArrayBlockingQueue<>(2);
        var pool = Executors.newSingleThreadExecutor();

        try {
            var result = pool.submit(() -> consume(queue));
            queue.put(new Data(10));
            queue.put(new Data(20));
            queue.put(new Data(30));
            queue.put(Stop.INSTANCE);

            Totals totals = result.get(10, TimeUnit.SECONDS);
            System.out.println("processed=" + totals.count());
            System.out.println("sum=" + totals.sum());
            System.out.println("queueEmpty=" + queue.isEmpty());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ProducerConsumerStopDemo.java
java ProducerConsumerStopDemo
```

Stdout:

```text
processed=3
sum=60
queueEmpty=true
```

`Message` chỉ có hai dạng: `Data` chứa số cần xử lý và `Stop.INSTANCE` biểu thị kết thúc. Với đúng một bên tiêu thụ, bên sản xuất đưa ba dữ liệu rồi một tín hiệu dừng.

Vì ví dụ dùng `ArrayBlockingQueue` FIFO và không có bên sản xuất khác, bên tiêu thụ gặp ba dữ liệu trước khi gặp tín hiệu dừng. Nó trả về tổng thay vì tiếp tục `take`.

Dung lượng 2 nhỏ hơn tổng số thông điệp. Chương trình vẫn tiến triển vì bên tiêu thụ được chạy độc lập, lấy dữ liệu và giải phóng chỗ trong lúc bên sản xuất đưa tiếp.

Đây là thí nghiệm với đường xử lý thành công đã xác định. Trong ứng dụng thật, nếu bên tiêu thụ hỏng, bên sản xuất có thể bị giữ trong `put`; cần có cơ chế báo lỗi, hủy và xử lý dữ liệu chưa hoàn tất.

### 7.3. Nhiều bên tiêu thụ cần đủ tín hiệu hoặc một giao thức khác

Nếu có ba bên tiêu thụ và mỗi bên thoát khi gặp một tín hiệu dừng, một tín hiệu chỉ đánh thức và kết thúc được một bên nhận nó. Hai bên còn lại có thể tiếp tục chờ.

Một giao thức phổ biến là gửi một tín hiệu cho mỗi bên tiêu thụ, nhưng chỉ sau khi bảo đảm mọi bên sản xuất đã kết thúc và không thêm dữ liệu nữa. Số bên tiêu thụ, thứ tự chèn và cách xử lý lỗi phải được biết rõ.

Đừng để một bên sản xuất tự gửi tín hiệu dừng khi những bên sản xuất khác còn hoạt động. Dữ liệu đưa sau tín hiệu có thể bị bỏ lại nếu các bên tiêu thụ đã thoát.

Cách “bên tiêu thụ lấy tín hiệu rồi đặt lại cho bên khác” cũng có thêm điều kiện: bước đặt lại có thể chờ, tác vụ có thể bị gián đoạn hoặc dừng giữa chừng. Không mặc định nó là lời giải không cần phối hợp.

### 7.4. Dừng có xử lý hết và dừng ngay là hai yêu cầu khác nhau

Nếu muốn xử lý hết dữ liệu đã chấp nhận, cần một thứ tự dừng cho phép các bên tiêu thụ tiếp tục làm việc đến khi nguồn đóng theo quy ước.

Nếu muốn dừng ngay, phải quyết định dữ liệu còn trong hàng đợi và công việc đang xử lý sẽ đi đâu: bỏ, lưu lại, chuyển giao hoặc báo chưa hoàn tất. Gửi một tín hiệu không tự thu hồi công việc đã được lấy ra.

Một cờ `volatile boolean running` cũng không tự đánh thức một luồng đang chờ trong `take()`. Phải có hành động làm lời gọi đó thoát hoặc chuyển sang một dạng chờ có giới hạn phù hợp.

Không giải quyết việc đánh thức bằng vòng lặp kiểm tra cờ liên tục mà bỏ hết khả năng chờ của hàng đợi.

## 8. Chuyển giao dữ liệu và tính nhìn thấy giữa các luồng

Hành động của bên sản xuất trước khi đặt một đối tượng vào `BlockingQueue` có quan hệ happens-before với hành động sau khi luồng khác truy cập hoặc lấy chính phần tử đó. Điều này cho phép hàng đợi làm điểm chuyển giao dữ liệu giữa các luồng. Xem [bảo đảm chuyển giao dữ liệu của BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).

Ví dụ suy luận: bên sản xuất hoàn tất khởi tạo công việc rồi mới `put`; bên tiêu thụ `take` được công việc đó rồi đọc dữ liệu đã khởi tạo. Không cần tự thêm một cờ “đã khởi tạo” chỉ để thay thế điểm chuyển giao mà hàng đợi đã cung cấp.

Cần giữ đúng ranh giới:

- Bảo đảm này gắn với đối tượng được chuyển qua hàng đợi.
- Hàng đợi không tạo một bản sao sâu của đối tượng.
- Việc bên sản xuất tiếp tục sửa đối tượng sau khi đưa vào không tự được đồng bộ với mọi lần đọc sau ở bên tiêu thụ.
- Hàng đợi không tự làm một `ArrayList` hoặc một cấu trúc con có thể sửa bên trong trở thành đối tượng dùng chung an toàn.

Trong `ProducerConsumerStopDemo`, dữ liệu là các record đơn giản có trường số không bị sửa sau khi tạo. Bên sản xuất chuyển giao thông điệp rồi không tiếp tục can thiệp dữ liệu của thông điệp đó.

Một quy ước dễ xem xét là bên sản xuất xây dựng dữ liệu xong trước khi gửi; bên nhận sở hữu phần việc sau khi lấy, hoặc dữ liệu được giữ bất biến nếu cần nhiều bên cùng đọc. Khi phải chia sẻ trạng thái thay đổi, thiết kế cơ chế đồng bộ cho chính trạng thái ấy.

Một lần lấy thành công cũng chỉ nói phần tử đã được chuyển khỏi hàng đợi đến bên nhận. Muốn bên gửi biết xử lý hoàn tất cần một kênh trả kết quả, xác nhận hoặc một cơ chế khác. Đừng gán ý nghĩa đó cho việc `put` đã trả về.

## 9. Thời hạn cho nhiều bước và các giới hạn cần giữ rõ

### 9.1. Không cấp lại toàn bộ ngân sách ở mỗi lần thử

Giả sử một yêu cầu chỉ được dành tổng cộng 300 mili giây cho giai đoạn chờ. Nếu mỗi lần `offer` thất bại lại gọi tiếp với 300 mili giây, tổng thời gian chờ có thể vượt xa ý định ban đầu.

Cần chọn mốc bắt đầu và trừ thời gian đã dùng trước khi quyết định chờ tiếp. `System.nanoTime()` được dùng để đo khoảng thời gian trong cùng JVM, không phải timestamp để ghi ngày giờ hoặc so giữa các máy. Xem [System.nanoTime Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime()).

Phương thức dưới đây là một đoạn tích hợp minh họa, chưa phải chương trình độc lập. Cần đặt nó trong một lớp có import `BlockingQueue` và `TimeUnit`; `startedAt` lấy từ `System.nanoTime()` tại đầu giai đoạn được tính ngân sách:

```java
static boolean offerWithinBudget(
        BlockingQueue<String> queue,
        String item,
        long startedAt,
        long budgetNanos) throws InterruptedException {

    long elapsed = System.nanoTime() - startedAt;
    long remaining = budgetNanos - elapsed;
    if (remaining <= 0) {
        return false;
    }
    return queue.offer(item, remaining, TimeUnit.NANOSECONDS);
}
```

Đoạn này chọn chính sách không thử đưa thêm khi ngân sách đã hết, kể cả nếu hiện tại có chỗ. Đây là quyết định của hàm tích hợp, khác với một lời gọi `offer` có timeout 0 vẫn có thể thử điều kiện hiện tại.

Giả định ngân sách không âm, có độ dài hợp lý và các mốc đo thuộc cùng JVM. Không dùng việc cộng vào một timestamp ngày giờ để thay cho phép đo khoảng thời gian.

### 9.2. Ngân sách của thao tác xếp hàng chưa là hạn của toàn bộ công việc

Thời gian sau khi nhận phần tử còn có thể gồm chờ trong hàng đợi, chờ tài nguyên khác, thực hiện I/O và xử lý kết quả. Muốn kiểm soát tổng thể, từng giai đoạn phải nhận phần ngân sách còn lại hoặc kiểm tra cùng một hạn theo mô hình của ứng dụng.

Ngay cả khi không cấp lại ngân sách, JVM và bộ lập lịch vẫn không được biến thành hệ thống thời gian thực cứng. Một cơ chế timeout giúp ứng dụng bỏ chờ theo chính sách; nó không bảo đảm mọi tài nguyên ngoài hoặc mọi tác vụ đã bắt đầu đều bị dừng đúng thời điểm.

### 9.3. Tránh dùng một lần hết thời gian để kết luận nguồn đã đóng

Một bên tiêu thụ dùng `poll(1, SECONDS)` có thể định kỳ kiểm tra yêu cầu dừng, ghi nhận trạng thái hoặc thực hiện công việc bảo trì. Nếu nhận `null`, nó cần quyết định bước tiếp theo theo giao thức chung.

Dừng chỉ vì một lần không nhận dữ liệu có thể làm hỏng một nguồn sản xuất theo đợt. Ngược lại, lặp mãi sau khi nguồn thực sự đã kết thúc cũng để lại tác vụ không cần thiết.

Vì vậy, timeout và điều kiện hoàn tất nguồn cần được mô hình hóa riêng. Tín hiệu nguồn đã hết nên do một bên có đủ thông tin xác nhận, thay vì được suy ra từ tốc độ hiện tại.

### 9.4. Quan sát theo từng giai đoạn

Khi hệ thống chậm, phân biệt:

- Thời gian chờ chỗ để được chấp nhận.
- Thời gian công việc nằm trong hàng đợi.
- Thời gian xử lý sau khi đã lấy.
- Số lần thêm bị từ chối hoặc hết thời gian.
- Số lần bị gián đoạn và nguyên nhân yêu cầu dừng.
- Số công việc đang xử lý, tách khỏi số còn trong hàng đợi.

Hàng đợi rỗng không chứng minh mọi công việc đã hoàn tất: các bên tiêu thụ có thể đã lấy hết nhưng vẫn đang làm. Hàng đợi không đầy cũng không chứng minh hệ thống không bị quá tải ở một tầng khác.

Các thí nghiệm trong bài kiểm chứng hợp đồng thao tác và cách phối hợp có kiểm soát. Chúng không thay thế việc đo tải ứng dụng hoặc chứng minh tất cả lịch thực thi có thể có đều không gặp vấn đề.

## 10. Lỗi thường gặp và thực hành

### 10.1. Nhận diện lỗi từ chính sách đã chọn

| Mẫu sử dụng | Vấn đề | Hướng xem xét |
|---|---|---|
| `put` trong luồng không được phép chờ lâu | Luồng có thể bị giữ khi đầy | Dùng chính sách từ chối hoặc chờ có giới hạn phù hợp |
| Bỏ qua kết quả `offer` | Không biết công việc có được chấp nhận không | Xử lý rõ nhánh `false` |
| Bắt gián đoạn rồi tiếp tục vòng lặp | Có thể làm mất yêu cầu dừng | Truyền tiếp hoặc khôi phục cờ và thoát theo chính sách |
| Dùng `null` làm thông điệp dừng | Không hợp lệ với BlockingQueue | Dùng kiểu hoặc đối tượng thông điệp riêng |
| Thấy `poll` trả `null` rồi kết luận hết việc | Nguồn có thể vẫn thêm sau đó | Dùng giao thức kết thúc nguồn |
| Kiểm tra `remainingCapacity` rồi coi đã giữ chỗ | Luồng khác có thể chiếm chỗ trước | Dựa vào kết quả thao tác thực tế |
| Cấp lại toàn bộ timeout khi thử lại | Tổng thời gian không còn bị giới hạn như dự định | Tính ngân sách còn lại |
| Chỉ gọi `shutdown` rồi chờ consumer tự thoát `take` | Hàng đợi không nhận tín hiệu nguồn kết thúc | Thiết kế cách đánh thức và kết thúc tác vụ |

Các hướng trên là quyết định về hành vi ứng dụng. Không có một phương thức duy nhất phù hợp với mọi loại công việc hoặc mọi yêu cầu mất dữ liệu, độ trễ và dừng hệ thống.

### Bài 1: chọn phương thức đúng với yêu cầu

Một hàng đợi có giới hạn dung lượng. Với mỗi yêu cầu sau, hãy chọn phương thức và nhánh xử lý:

1. Nếu chưa có chỗ thì trả về cho bên gọi ngay, để bên gọi báo quá tải.
2. Cho phép chờ chỗ tối đa trong một khoảng cụ thể.
3. Tác vụ nền được phép chờ dữ liệu đến nhưng phải dừng được.
4. Chỉ muốn xem phần tử đầu, không lấy nó ra.

Hướng giải thích: dùng `offer`, `offer` có timeout, `take` với chính sách gián đoạn hoặc kết thúc, và `peek`. Cần nêu rõ giá trị trả về hoặc ngoại lệ của từng nhánh.

### Bài 2: kết quả nào xảy ra khi đầy và rỗng?

Với dung lượng 1, lần lượt đưa A vào, thử đưa B bằng `offer` có timeout khi không có bên lấy, lấy A rồi `poll` có timeout khi không có bên thêm.

Giải thích vì sao lần đưa B nhận `false`, lần lấy sau nhận `null`, và vì sao không có `TimeoutException` từ hai phương thức hàng đợi đó.

Sau đó thêm trường hợp luồng bị gián đoạn trong lúc chờ. Nhánh này phải được mô tả riêng, không đổi tên nó thành hết thời gian.

### Bài 3: tìm nơi không còn khả năng tiến triển

Một nhóm thực thi có hai luồng. Hai tác vụ sản xuất đều đang `put` vào hàng đợi đầy; tác vụ tiêu thụ được xếp trong cùng nhóm nhưng chưa chạy.

Hãy chỉ ra ai có thể tạo chỗ trống ở trạng thái đó. Việc tăng dung lượng có sửa được quan hệ phụ thuộc một cách chắc chắn không?

Hướng giải thích: tăng dung lượng có thể làm tình huống đến muộn hơn, nhưng không thay thế thiết kế bảo đảm bên tiêu thụ có thể chạy. Cần xem bố trí luồng, chính sách chờ và quan hệ giữa các tác vụ.

### Bài 4: dừng nhiều bên tiêu thụ

Có hai bên sản xuất và ba bên tiêu thụ. Một bên sản xuất vừa hoàn tất và muốn gửi thông điệp dừng.

- Nó đã có đủ thông tin rằng nguồn hết dữ liệu chưa?
- Nếu mỗi bên tiêu thụ thoát khi lấy tín hiệu, cần tổ chức số tín hiệu thế nào?
- Nếu một bên tiêu thụ hỏng khi hàng đợi đầy, bên gửi tín hiệu có thể bị giữ ở đâu?

Hướng giải thích: phải phối hợp hoàn tất của mọi bên sản xuất, rồi thiết kế việc kết thúc đủ các bên tiêu thụ. Cần một đường báo lỗi và hủy, không chỉ đường thành công có thông điệp dừng.

### Bài 5: chuyển giao và xử lý hoàn tất

Bên sản xuất tạo một đối tượng, đưa vào hàng đợi rồi tiếp tục sửa các trường của nó. Bên tiêu thụ lấy đối tượng và đồng thời đọc các trường đó.

Vì sao tính an toàn của hàng đợi chưa đủ để kết luận việc sửa và đọc sau đó an toàn? Nếu bên gửi muốn biết xử lý đã xong, nó cần thêm thông tin gì?

Hướng giải thích: điểm chuyển giao bảo đảm tính nhìn thấy cho phần việc trước khi gửi; nó không làm mọi thay đổi tiếp theo được đồng bộ. Cần dữ liệu bất biến, chuyển quyền sử dụng hoặc đồng bộ riêng, cùng kênh xác nhận nếu phải theo dõi kết quả xử lý.

### Các câu hỏi cần trả lời trước khi dùng thao tác chờ

1. Bên gọi có được phép giữ luồng trong lúc chờ không?
2. Ai có thể làm điều kiện chờ thay đổi?
3. Dung lượng có thực sự được giới hạn theo mục tiêu vận hành không?
4. Nhánh hết thời gian và nhánh gián đoạn được xử lý ra sao?
5. Nguồn kết thúc bằng giao thức nào, ai có quyền xác nhận kết thúc?
6. Có đang giữ khóa hoặc tài nguyên mà bên còn lại cần không?
7. Thành công đang được hiểu là đã xếp hàng, đã lấy hay đã xử lý xong?
8. Đối tượng sau khi chuyển giao có tiếp tục bị nhiều bên sửa không?

Dùng đúng `put`, `take` và dạng có timeout cần cả lựa chọn phương thức lẫn một câu trả lời rõ về tiến triển, quyền sở hữu dữ liệu và vòng đời tác vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-queues-003">Hàng đợi có giới hạn và điều tiết tốc độ bên sản xuất</a>

<details>
<summary>Xem chi tiết</summary>

# Hàng đợi có giới hạn và điều tiết tốc độ bên sản xuất

Trong mô hình sản xuất–tiêu thụ (producer–consumer), bên sản xuất tạo công việc, đưa vào hàng đợi, còn bên tiêu thụ lấy ra để xử lý. Hai bên không nhất thiết hoạt động cùng tốc độ.

Nếu bên sản xuất liên tục tạo nhanh hơn khả năng xử lý, công việc chờ sẽ tăng. Một hàng đợi có giới hạn đặt ra sức chứa tối đa cho phần đang chờ và buộc chương trình trả lời câu hỏi: **khi hết chỗ, bên sản xuất phải làm gì?**

Đó là điểm nối giữa cấu trúc dữ liệu và **điều tiết ngược (backpressure)**: trạng thái quá tải ở phía xử lý được phản hồi về phía đưa việc, khiến phía đó chờ, giảm lượng đưa vào hoặc nhận một kết quả từ chối rõ ràng.

Các thuật ngữ trong bài:

- **Sức chứa (capacity):** số phần tử tối đa có thể nằm trong bộ đệm hàng đợi theo hợp đồng lớp.
- **Công việc chờ:** phần tử đã được nhận vào nhưng chưa bị lấy ra.
- **Công việc đang xử lý:** phần tử đã rời hàng đợi nhưng tác dụng xử lý chưa hoàn tất.
- **Tiếp nhận thành công:** thao tác đưa vào hoặc bàn giao đã thành công; khác với xử lý thành công.
- **Chính sách quá tải:** cách ứng dụng phản ứng khi không thể nhận thêm ngay.
- **Ngân sách chờ:** khoảng thời gian hoặc giới hạn tổng mà một yêu cầu được phép dành cho việc tiếp nhận.

Bài học dùng `BlockingQueue` và các triển khai có trong Java 17. Đích chính là hiểu hợp đồng dữ liệu, điều kiện chờ và cách truyền tín hiệu về bên sản xuất, không chỉ chọn một con số capacity.

## 1. Hàng đợi là bộ đệm, không tự làm bên tiêu thụ nhanh hơn

Có thể hình dung một luồng xử lý như sau:

~~~text
Bên sản xuất -> tiếp nhận -> [hàng đợi có sức chứa C] -> bên tiêu thụ -> hoàn tất
~~~

Một chỗ trống được giải phóng khi bên tiêu thụ **lấy phần tử ra**, chưa nhất thiết khi xử lý xong phần tử đó.

Nếu bên sản xuất tạo trung bình `120` việc mỗi giây còn bên tiêu thụ hoàn tất được khoảng `100` việc mỗi giây, phần chờ có xu hướng tăng. Một bộ đệm lớn chỉ kéo dài thời gian trước khi đầy; nó không giải quyết chênh lệch lâu dài.

Trong mô hình đơn giản coi tốc độ đều, bỏ qua các chi tiết khởi động và dao động, bộ đệm trống `200` chỗ có thể hấp thụ khoảng `10` giây chênh lệch `20` việc mỗi giây. Đây là phép tính để hiểu xu hướng, không phải cam kết thời điểm đầy của hệ thống thực.

Hàng đợi có giới hạn giúp ứng dụng đưa quyết định quá tải ra sớm hơn, thay vì để lượng công việc chưa xử lý tăng đến khi bộ nhớ hoặc độ trễ vượt khả năng chịu đựng.

### 1.1. Có giới hạn chưa đủ để tạo backpressure đúng

Nếu hàng đợi đầy nhưng chương trình bỏ qua kết quả thất bại rồi tiếp tục tạo việc như cũ, tín hiệu chưa được truyền về nguồn sản xuất.

Các phản ứng khác nhau có ý nghĩa khác nhau:

- Chờ chỗ trống làm luồng đưa việc chậm lại.
- Trả lỗi hoặc từ chối cho bên gọi có thể làm tầng trước giảm tốc.
- Bỏ việc là chính sách mất dữ liệu có chủ đích, nếu yêu cầu cho phép.
- Đẩy việc sang một danh sách không giới hạn chỉ chuyển nơi tích lũy.

Vì vậy, capacity là một thành phần; chính sách của bên gọi mới hoàn thiện cơ chế điều tiết.

### 1.2. Không phải bộ giới hạn số yêu cầu mỗi giây

Hàng đợi sức chứa `C` có thể nhận một đợt lớn khi đang trống, rồi chặn/từ chối khi đầy. Nó giới hạn phần chờ, không tự bảo đảm một tốc độ cố định như “tối đa 50 yêu cầu mỗi giây”.

Nếu cần quy tắc theo thời gian, phải có cơ chế điều tiết tốc độ riêng. Hàng đợi có thể kết hợp với cơ chế đó, nhưng hai công cụ trả lời hai câu hỏi khác nhau.

## 2. Bốn cách đưa vào khi đang đầy

`BlockingQueue` cung cấp bốn nhóm API để ứng dụng chọn cách phản ứng khi thao tác chưa thể thực hiện ngay. [Java 17: BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)

| Cách đưa vào | Nếu nhận được | Nếu hàng đợi đầy |
|---|---|---|
| `add(item)` | Trả `true`. | Ném `IllegalStateException` do hết sức chứa. |
| `offer(item)` | Trả `true`. | Trả `false` ngay theo cơ chế không đợi chỗ trống. |
| `put(item)` | Kết thúc bình thường. | Chờ cho đến khi có chỗ hoặc bị ngắt. |
| `offer(item, timeout, unit)` | Trả `true`. | Chờ có giới hạn; trả `false` nếu hết thời gian mà chưa nhận được. |

Những kết quả này là một phần giao thức, không phải chi tiết có thể bỏ qua.

Ví dụ minh họa trong một phương thức:

~~~java
java.util.concurrent.BlockingQueue<String> pending =
        new java.util.concurrent.ArrayBlockingQueue<>(128);

boolean accepted = pending.offer("task");
if (!accepted) {
    // Bên gọi phải nhận biết lần tiếp nhận này đã bị từ chối.
}
~~~

Đoạn trên chưa chọn một chính sách nghiệp vụ cụ thể. Trong mã thật, nhánh từ chối phải được biến thành hành vi có ý nghĩa: báo cho bên gọi, chờ theo ngân sách, gộp thay đổi, hoặc bỏ việc có ghi nhận nếu được phép.

### 2.1. offer không chặn để chờ sức chứa

`offer(item)` hữu ích khi bên sản xuất không được phép đợi chỗ trống. Ví dụ một luồng tiếp nhận có thể cần trả kết quả quá tải ngay cho tầng trước.

Nếu gọi `offer` rồi không kiểm tra `boolean`, công việc bị từ chối có thể biến mất mà bên gọi vẫn nghĩ đã nhận thành công.

Không dùng vòng lặp bận như sau để né việc chọn chính sách:

~~~java
while (!pending.offer("task")) {
    // Thử lại liên tục.
}
~~~

Đây là mẫu minh họa không phù hợp cho việc chờ. Nó có thể tiêu tốn CPU, tranh chấp với bên tiêu thụ và không có hạn dừng. Nếu được phép chờ, API chờ đã biểu đạt điều đó rõ hơn.

### 2.2. put tạo phản hồi qua thời gian chờ của luồng

`put` không từ chối chỉ vì hàng đợi đang đầy; nó có thể phải chờ. Nếu bên tiêu thụ không còn tiến triển và không có hủy, một lần chờ có thể không kết thúc.

Điều tiết qua `put` có tác dụng khi việc làm chậm chính luồng sản xuất là phù hợp và tầng trước thực sự nhận được ảnh hưởng đó. Nếu mỗi yêu cầu lại tạo một luồng mới chỉ để đứng chờ `put`, ứng dụng có thể hết tài nguyên luồng hoặc giữ quá nhiều đối tượng chờ bên ngoài hàng đợi.

### 2.3. Timed offer giới hạn một lần tiếp nhận

Ví dụ:

~~~java
boolean accepted = pending.offer("task", 200,
        java.util.concurrent.TimeUnit.MILLISECONDS);
~~~

Đoạn minh họa nằm trong phương thức có xử lý hoặc khai báo `InterruptedException`. Kết quả `false` cần được xử lý như một lần chưa được nhận, không như một việc đang được xử lý ngầm ở hàng đợi.

Thời hạn của API không phải lời hứa thời gian thực rằng luồng chắc chắn quay lại đúng sau `200` mili giây theo đồng hồ ngoài. Lịch chạy của hệ điều hành và tranh chấp có thể ảnh hưởng lúc luồng được chạy tiếp. Không kiểm thử bằng yêu cầu số mili giây thực tế phải bằng chính xác một hằng số.

Nếu thử lại nhiều lần, mỗi lần cho `200` mili giây có thể cộng thành một thời gian rất dài. Khi yêu cầu có ngân sách tổng, cần theo dõi phần thời gian còn lại thay vì đặt lại toàn bộ thời hạn ở mỗi lần thử. `System.nanoTime` được thiết kế để đo thời gian trôi qua trong JVM, không dùng làm ngày giờ lịch. [Java 17: System.nanoTime](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime())

### 2.4. add phù hợp khi đầy được xem là lỗi tại ranh giới gọi

`add` có thể hữu ích khi hợp đồng của đoạn mã coi việc đầy là lỗi cần báo bằng ngoại lệ. Nhưng trong một đường tiếp nhận mà quá tải là kết quả dự kiến, `offer` thường giúp thể hiện nhánh được nhận/từ chối trực tiếp.

Việc đổi `add` thành `put` không chỉ là đổi cú pháp xử lý lỗi: nó thay đổi khả năng chặn luồng và yêu cầu về hủy/dừng.

## 3. Lấy ra và ý nghĩa của hoàn tất

Nhóm lấy phần tử có hình dạng tương ứng:

| Cách lấy | Khi có phần tử | Khi đang rỗng |
|---|---|---|
| `remove()` không đối số | Lấy và xóa phần tử đầu. | Ném `NoSuchElementException`. |
| `poll()` | Lấy và xóa phần tử đầu. | Trả `null`. |
| `take()` | Lấy và xóa phần tử đầu. | Chờ phần tử hoặc bị ngắt. |
| `poll(timeout, unit)` | Lấy phần tử nếu có trong thời gian chờ. | Trả `null` khi hết thời gian chờ mà chưa có. |

`BlockingQueue` không chấp nhận phần tử `null`, nên `null` có thể được dùng làm kết quả “không lấy được phần tử”. Đừng dùng `null` làm thông điệp kết thúc.

### 3.1. Lấy ra không có nghĩa xử lý xong

Ngay sau `take`, phần tử không còn được tính vào `size`, nhưng bên tiêu thụ có thể đang ghi file, tính toán hoặc gọi hệ thống khác.

Do đó:

- Hàng đợi rỗng chưa chứng minh mọi việc đã xong.
- `put`/`offer` thành công chưa chứng minh bên tiêu thụ đã chạy.
- Ngay cả bàn giao trực tiếp qua `SynchronousQueue` cũng chưa chứng minh công việc được xử lý hoàn tất.

Nếu bên sản xuất cần biết kết quả, dùng một cơ chế phản hồi hoàn tất phù hợp như đối tượng kết quả, future hoặc tín hiệu phối hợp. Hàng đợi chỉ quản lý tiếp nhận và lấy phần tử theo hợp đồng của nó.

### 3.2. Sức chứa thường chỉ giới hạn phần đang chờ

Giả sử có `C` chỗ đợi và `W` bên tiêu thụ, mỗi bên giữ một việc đang xử lý. Tổng công việc đã được nhận nhưng chưa hoàn tất có thể lớn hơn `C`.

Nếu mỗi bên tiêu thụ rút cả lô bằng `drainTo` rồi giữ lô ngoài hàng đợi, phần công việc bên ngoài còn có thể lớn hơn nữa. Muốn giới hạn toàn bộ số việc đang hoạt động, cần tính cả các phần đang được giữ ngoài bộ đệm.

## 4. Chọn loại hàng đợi theo hợp đồng

### 4.1. ArrayBlockingQueue: sức chứa mảng cố định

Ví dụ:

~~~java
java.util.concurrent.BlockingQueue<String> queue =
        new java.util.concurrent.ArrayBlockingQueue<>(64);
~~~

`ArrayBlockingQueue` là bộ đệm có giới hạn dùng mảng, lấy theo FIFO. Sức chứa không thay đổi sau khi tạo; `put` chờ khi đầy và `take` chờ khi rỗng. Có tùy chọn công bằng cho các luồng đang chờ. [Java 17: ArrayBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html)

FIFO của phần tử không có nghĩa nhiều bên tiêu thụ hoàn tất công việc theo cùng thứ tự. Hai việc được lấy theo thứ tự có thể có thời gian xử lý khác nhau.

Tùy chọn công bằng liên quan thứ tự cấp quyền truy cập cho các bên đang chờ; nó không bảo đảm lịch chạy hay thời gian hoàn tất nghiệp vụ của từng luồng. Cần đánh đổi với thông lượng theo tải thực.

### 4.2. LinkedBlockingQueue: phải chủ động chọn giới hạn

~~~java
java.util.concurrent.BlockingQueue<String> queue =
        new java.util.concurrent.LinkedBlockingQueue<>(64);
~~~

Lớp này dùng các nút liên kết và có thể được tạo với giới hạn. Nếu dùng constructor không đối số, capacity là `Integer.MAX_VALUE`. Đó là một giới hạn kỹ thuật rất lớn, thường không phải mức bảo vệ bộ nhớ/độ trễ hữu ích cho một ứng dụng. [Java 17: LinkedBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html)

Không suy từ chữ `Blocking` rằng nó nhất thiết sẽ sớm làm chậm bên sản xuất vì hết chỗ. Cần xem capacity thật và đường API đang gọi.

Mảng hay nút liên kết có đặc điểm cấp phát/phối hợp khác nhau. Không chọn chỉ bằng một khẳng định “loại này luôn nhanh hơn”; phải đo cùng tải và cùng yêu cầu.

### 4.3. LinkedBlockingDeque khi cần hai đầu

`LinkedBlockingDeque` có các API ở đầu và cuối, cũng có constructor capacity. Constructor mặc định dùng `Integer.MAX_VALUE`. Chọn nó khi bài toán thực sự cần hai đầu; việc có thêm API không tự cải thiện điều tiết tải. [Java 17: LinkedBlockingDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html)

Cần quy định rõ bên nào thêm/lấy ở đầu nào để không vô tình làm thay đổi thứ tự công việc.

### 4.4. SynchronousQueue: bàn giao, không có bộ đệm

`SynchronousQueue` không có chỗ chứa nội bộ, kể cả một chỗ. Một lần đưa vào phải gặp một lần lấy tương ứng. `offer` không đợi sẽ thất bại nếu không có bên nhận đang sẵn để nhận; dạng chờ có thể đợi cuộc bàn giao. [Java 17: SynchronousQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SynchronousQueue.html)

Đây là mô hình phối hợp trực tiếp. `size` bằng `0` không có nghĩa không có luồng đang chờ trao hoặc nhận; sức chứa bằng không là đặc tính của mô hình.

Không tạo `ArrayBlockingQueue` capacity `0` để thay mô hình này. Chọn đúng lớp có hợp đồng bàn giao.

## 5. remainingCapacity không phải đặt chỗ

Mẫu sau có một khoảng tranh chấp:

~~~java
if (queue.remainingCapacity() > 0) {
    queue.add(item);
}
~~~

Đây là đoạn minh họa, giả sử các biến đã có. Bên khác có thể chiếm chỗ giữa hai lời gọi; `add` vẫn có thể thất bại.

`ArrayBlockingQueue` ghi rõ không thể luôn dự đoán một lần thêm thành công chỉ bằng `remainingCapacity` vì các luồng khác có thể đang thêm hoặc lấy phần tử. [Java 17: ArrayBlockingQueue.remainingCapacity](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html#remainingCapacity())

Dùng trực tiếp thao tác thể hiện ý định:

- Muốn thử ngay: `offer` và xử lý kết quả.
- Muốn chờ: `put` cùng giao thức hủy.
- Muốn chờ có giới hạn: timed `offer` cùng ngân sách.

`size` và `remainingCapacity` vẫn hữu ích cho giám sát. Chúng không thay thế quyết định tiếp nhận của chính thao tác thêm.

## 6. Chương trình kiểm chứng đầy đủ

Tệp `BoundedQueueFlowDemo.java` dùng API Java 17, đã biên dịch bằng `javac --release 17` và chạy trên OpenJDK Zulu 24.0.2. Đây không phải tuyên bố đã chạy trên JVM 17.

Các tình huống chờ dùng latch để đặt thứ tự có ý nghĩa. Không dùng `Thread.sleep` để suy đoán bên sản xuất đã bị chặn. Các kết quả chỉ được in ở những điểm có thể giải thích bằng trạng thái hàng đợi và tín hiệu phối hợp.

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class BoundedQueueFlowDemo {
    record Job(int value, boolean stop) {}

    static void await(CountDownLatch event) throws InterruptedException {
        if (!event.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Event did not arrive");
        }
    }

    static void join(Thread thread, AtomicReference<Throwable> error)
            throws InterruptedException {
        thread.join(5000);
        if (thread.isAlive()) {
            thread.interrupt();
            throw new AssertionError("Thread did not terminate");
        }
        if (error.get() != null) {
            throw new AssertionError("Background failure", error.get());
        }
    }

    static Thread start(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    public static void main(String[] args) throws Exception {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);
        System.out.println("offer-A = " + queue.offer("A"));
        System.out.println("offer-B = " + queue.offer("B"));
        System.out.println("remaining-when-full = " + queue.remainingCapacity());
        System.out.println("offer-C-full = " + queue.offer("C"));
        try {
            queue.add("C");
            throw new AssertionError("add should have failed");
        } catch (IllegalStateException ex) {
            System.out.println("add-C-full = " + ex.getClass().getSimpleName());
        }
        System.out.println("timed-offer-C-full = "
                + queue.offer("C", 10, TimeUnit.MILLISECONDS));
        System.out.println("take-first = " + queue.take());
        System.out.println("offer-C-after-space = " + queue.offer("C"));
        System.out.println("remaining-content = " + new ArrayList<>(queue));
        queue.clear();
        System.out.println("poll-empty = " + queue.poll());
        try {
            queue.offer(null);
            throw new AssertionError("null should have failed");
        } catch (NullPointerException ex) {
            System.out.println("offer-null = " + ex.getClass().getSimpleName());
        }

        BlockingQueue<String> waiting = new ArrayBlockingQueue<>(1);
        waiting.put("first");
        CountDownLatch aboutToPut = new CountDownLatch(1);
        CountDownLatch putReturned = new CountDownLatch(1);
        AtomicReference<Throwable> putError = new AtomicReference<>();
        Thread sender = start("waiting-sender", () -> {
            try {
                aboutToPut.countDown();
                waiting.put("second");
                putReturned.countDown();
            } catch (Throwable ex) {
                putError.set(ex);
            }
        });
        await(aboutToPut);
        System.out.println("put-returned-before-space = "
                + (putReturned.getCount() == 0));
        System.out.println("released-item = " + waiting.take());
        await(putReturned);
        join(sender, putError);
        System.out.println("put-item-after-space = " + waiting.take());

        BlockingQueue<String> cancellable = new ArrayBlockingQueue<>(1);
        cancellable.put("held");
        CountDownLatch cancellationAttempt = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();
        AtomicReference<Throwable> cancelError = new AtomicReference<>();
        Thread cancelledSender = start("cancelled-sender", () -> {
            try {
                cancellationAttempt.countDown();
                cancellable.put("not-admitted");
            } catch (InterruptedException ex) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                restored.set(Thread.currentThread().isInterrupted());
            } catch (Throwable ex) {
                cancelError.set(ex);
            }
        });
        await(cancellationAttempt);
        cancelledSender.interrupt();
        join(cancelledSender, cancelError);
        System.out.println("put-interrupted = " + interrupted.get());
        System.out.println("interrupt-restored = " + restored.get());
        System.out.println("queue-after-cancel = " + new ArrayList<>(cancellable));

        BlockingQueue<String> inFlight = new ArrayBlockingQueue<>(1);
        inFlight.put("work");
        CountDownLatch taken = new CountDownLatch(1);
        CountDownLatch finishAllowed = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<Throwable> processingError = new AtomicReference<>();
        Thread processor = start("in-flight-processor", () -> {
            try {
                inFlight.take();
                taken.countDown();
                await(finishAllowed);
                completed.set(true);
            } catch (Throwable ex) {
                processingError.set(ex);
            }
        });
        try {
            await(taken);
            System.out.println("empty-while-processing = " + inFlight.isEmpty());
            System.out.println("completed-while-empty = " + completed.get());
        } finally {
            finishAllowed.countDown();
        }
        join(processor, processingError);
        System.out.println("completed-after-join = " + completed.get());

        BlockingQueue<Job> jobs = new ArrayBlockingQueue<>(2);
        List<Integer> processed = new ArrayList<>();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        Thread consumer = start("job-consumer", () -> {
            try {
                for (;;) {
                    Job job = jobs.take();
                    if (job.stop()) {
                        return;
                    }
                    processed.add(job.value());
                }
            } catch (Throwable ex) {
                jobError.set(ex);
            }
        });
        int admitted = 0;
        try {
            for (int value = 1; value <= 6; value++) {
                if (!jobs.offer(new Job(value, false), 5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Admission timed out");
                }
                admitted++;
            }
            if (!jobs.offer(new Job(0, true), 5, TimeUnit.SECONDS)) {
                throw new AssertionError("Stop message timed out");
            }
        } catch (Throwable ex) {
            consumer.interrupt();
            throw ex;
        }
        join(consumer, jobError);
        System.out.println("pipeline-admitted = " + admitted);
        System.out.println("pipeline-processed = " + processed);
        System.out.println("pipeline-sum = "
                + processed.stream().mapToInt(Integer::intValue).sum());

        SynchronousQueue<String> handoff = new SynchronousQueue<>();
        System.out.println("handoff-offer-without-receiver = " + handoff.offer("early"));
        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<Throwable> handoffError = new AtomicReference<>();
        Thread receiver = start("handoff-receiver", () -> {
            try {
                received.set(handoff.take());
            } catch (Throwable ex) {
                handoffError.set(ex);
            }
        });
        if (!handoff.offer("direct", 5, TimeUnit.SECONDS)) {
            receiver.interrupt();
            throw new AssertionError("Handoff timed out");
        }
        join(receiver, handoffError);
        System.out.println("handoff-received = " + received.get());
        System.out.println("handoff-size = " + handoff.size());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 BoundedQueueFlowDemo.java
java BoundedQueueFlowDemo
~~~

Stdout đã kiểm chứng:

~~~text
offer-A = true
offer-B = true
remaining-when-full = 0
offer-C-full = false
add-C-full = IllegalStateException
timed-offer-C-full = false
take-first = A
offer-C-after-space = true
remaining-content = [B, C]
poll-empty = null
offer-null = NullPointerException
put-returned-before-space = false
released-item = first
put-item-after-space = second
put-interrupted = true
interrupt-restored = true
queue-after-cancel = [held]
empty-while-processing = true
completed-while-empty = false
completed-after-join = true
pipeline-admitted = 6
pipeline-processed = [1, 2, 3, 4, 5, 6]
pipeline-sum = 21
handoff-offer-without-receiver = false
handoff-received = direct
handoff-size = 0
~~~

Các thời hạn năm giây trong ví dụ là giới hạn chờ để phép kiểm chứng không kẹt vô hạn. Chúng không được đề xuất như thời hạn nghiệp vụ dùng chung cho mọi hệ thống.

## 7. Diễn giải các tình huống

### 7.1. Ba cách xử lý một hàng đợi đầy

Hàng đợi sức chứa `2` nhận `A` và `B`:

~~~text
remaining-when-full = 0
offer-C-full = false
add-C-full = IllegalStateException
timed-offer-C-full = false
~~~

Không có bên tiêu thụ trong giai đoạn này, nên timed offer không thể nhận được chỗ trống trong lúc chờ. Chương trình chỉ kiểm tra kết quả `false`, không kiểm tra số mili giây thực tế phải bằng đúng `10`.

Sau khi `take` lấy `A`, một chỗ được giải phóng. `offer("C")` thành công và thứ tự phần đang đợi là `[B, C]`.

### 7.2. put chưa thể hoàn tất khi chưa có chỗ

Bộ đệm sức chứa `1` đã có `first`. Bên gửi báo sắp gọi `put("second")`. Không có ai lấy phần tử trước khi luồng chính kiểm tra tín hiệu hoàn tất, nên:

~~~text
put-returned-before-space = false
~~~

Latch `aboutToPut` đánh dấu đã đi đến đoạn thử đưa vào; nó không phải công cụ đọc trạng thái nội bộ `WAITING` của luồng. Kết luận cần dùng là: khi chỗ duy nhất còn bị `first` chiếm và chưa có bên lấy, lần `put("second")` không thể hoàn tất thành công.

Luồng chính lấy `first`, sau đó chờ `put` trả về và lấy được `second`. Đây là phản hồi qua sức chứa: bên sản xuất chỉ tiến qua lần đưa vào khi phía nhận đã làm xuất hiện chỗ trống.

### 7.3. Hủy một lần chờ

Bộ đệm khác đã chứa `held`, không có bên lấy. Luồng gửi bị ngắt trong hoặc trước đoạn chờ `put` và xử lý `InterruptedException`:

~~~text
put-interrupted = true
interrupt-restored = true
queue-after-cancel = [held]
~~~

Đối tượng `not-admitted` không được nhận trong tình huống này. Nhánh bắt lỗi khôi phục cờ ngắt rồi thoát tác vụ. Nó không coi hủy là lý do để thử đưa lại mãi.

Nếu phương thức đang dùng có thể khai báo `throws InterruptedException`, việc truyền lỗi lên bên gọi cũng là một lựa chọn phù hợp. Nếu đang ở ranh giới không truyền được lỗi ấy, cần giữ và xử lý tín hiệu hủy theo giao thức của tác vụ. [Java 17: Thread.interrupt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#interrupt())

### 7.4. Rỗng nhưng chưa hoàn tất

Bên tiêu thụ lấy `work` ra rồi đợi một tín hiệu cho phép hoàn thành. Trong khoảng đó:

~~~text
empty-while-processing = true
completed-while-empty = false
~~~

Chỉ sau khi được phép đi tiếp và luồng kết thúc, cờ hoàn tất mới là `true`. `isEmpty` quan sát phần đang chờ, không theo dõi phần đã được lấy để xử lý.

### 7.5. Một pipeline có điểm kết thúc rõ

Phần `jobs` có một bên sản xuất và một bên tiêu thụ, sức chứa `2`. Bên sản xuất đưa các giá trị `1..6` bằng timed offer và chỉ tăng số đã nhận sau khi lời gọi thành công.

Thông điệp dừng là một `Job` có trường `stop=true`, được đưa sau toàn bộ công việc thường. Consumer xử lý FIFO và dừng khi lấy thông điệp ấy. Luồng chính `join` trước khi đọc danh sách kết quả:

~~~text
pipeline-admitted = 6
pipeline-processed = [1, 2, 3, 4, 5, 6]
pipeline-sum = 21
~~~

Danh sách `processed` chỉ được consumer sửa trong giai đoạn xử lý. Luồng chính đọc nó sau khi consumer kết thúc, nên không có các luồng cùng ghi một `ArrayList`.

Ví dụ đồng thời kiểm tra số tiếp nhận và kết quả hoàn tất, thay vì chỉ nhìn queue rỗng.

### 7.6. Bàn giao không cần chỗ chứa

`offer("early")` trên SynchronousQueue không có receiver trả `false`. Lần timed offer sau gặp receiver đang hoặc sắp `take`, bàn giao được `direct`:

~~~text
handoff-received = direct
handoff-size = 0
~~~

Queue vẫn không có bộ đệm. Việc bàn giao thành công chỉ xác nhận phía nhận đã nhận đối tượng theo hợp đồng, chưa phải một xác nhận xử lý nghiệp vụ hoàn tất.

## 8. Chính sách quá tải là một phần của yêu cầu

| Chính sách | Khi phù hợp | Điều bên gọi cần biết |
|---|---|---|
| Chờ chỗ trống | Có thể làm chậm luồng sản xuất và vẫn có đường hủy. | Có thể chưa tiến triển nếu phía tiêu thụ dừng. |
| Chờ trong ngân sách | Yêu cầu chỉ chịu được một độ trễ tiếp nhận nhất định. | Hết ngân sách nghĩa là chưa nhận; cần phản hồi rõ. |
| Từ chối ngay | Tầng trước có khả năng giảm tốc, thử lại hoặc báo quá tải. | Phải nhận được kết quả từ chối, không báo thành công giả. |
| Bỏ việc có chủ đích | Dữ liệu cho phép mất theo một quy tắc đã định nghĩa. | Phải rõ bỏ mục nào và đo số mục bị bỏ. |
| Gộp các cập nhật thay thế nhau | Chỉ trạng thái mới nhất hoặc một bản tổng hợp có ý nghĩa. | Phải chứng minh việc gộp không mất thông tin cần thiết. |
| Bên gọi tự xử lý | Luồng gọi được phép làm loại công việc đó và chấp nhận thời gian xử lý. | Ngữ cảnh thực thi, độ trễ và xử lý lỗi thay đổi. |

Không có chính sách chung đúng cho mọi loại công việc. Một mẫu đo nhiệt độ mới có thể thay mẫu cũ theo yêu cầu; một lệnh thanh toán không thể bị bỏ âm thầm theo cùng lý do.

### 8.1. Thử lại phải có giới hạn và điều kiện

Khi bị từ chối, thử lại ngay liên tục có thể khuếch đại tải. Nếu dùng thử lại, cần quy định số lần hoặc ngân sách tổng, khoảng giãn phù hợp và điều kiện hủy. Chỉ tiếp tục khi yêu cầu vẫn còn có ý nghĩa.

Đặc biệt, đừng báo đã nhận công việc rồi mới âm thầm chuyển nó qua một cơ chế thử lại không có giới hạn lưu trữ. Khi ấy giới hạn đã bị đưa ra ngoài hàng đợi chính.

### 8.2. Truyền tín hiệu ngược đến nguồn thật

Một luồng trung gian bị chặn không tự khiến một nguồn bên ngoài ngừng phát dữ liệu. Có thể vẫn còn bộ đệm mạng, danh sách trong bộ đọc hoặc tác vụ đang được tạo trước khi chờ.

Muốn backpressure có tác dụng trên cả đường đi, phải chỉ rõ tín hiệu từ chối/chờ đi qua các tầng như thế nào và tầng nào có thể tạm ngừng nhận thêm.

## 9. Chọn sức chứa theo ngân sách bộ nhớ và độ trễ

Sức chứa được đếm bằng **phần tử**, không phải số byte. Một phần tử có thể giữ mảng dữ liệu lớn hoặc tham chiếu đến cả một đồ thị đối tượng.

Khi tính ngân sách, xét:

- Số phần tử đang nằm trong queue.
- Các công việc đã lấy ra nhưng đang xử lý.
- Các lô đã rút khỏi queue.
- Đối tượng mà bên sản xuất tạo trước khi được phép tiếp nhận.
- Các yêu cầu đang đứng chờ hoặc được giữ ở tầng khác.
- Kích thước và thời gian sống của dữ liệu đi kèm.

Tăng capacity thường giúp hấp thụ một đợt tăng ngắn, nhưng có thể làm công việc nằm chờ lâu hơn trước khi bị từ chối hoặc trước khi xử lý. Một bộ đệm rất lớn có thể chuyển vấn đề từ “từ chối sớm” sang “chờ lâu rồi vẫn hết hạn”.

Cách chọn nên bắt đầu từ yêu cầu:

1. Công việc chấp nhận chờ bao lâu trước khi bắt đầu?
2. Hệ thống có bao nhiêu bộ nhớ cho phần chưa xử lý?
3. Đợt tăng tải thường kéo dài bao lâu?
4. Bên tiêu thụ có thể mở rộng thực sự đến đâu?
5. Khi quá tải kéo dài, chính sách nào được phép thực hiện?

Không dùng một con số queue capacity lấy tùy ý rồi xem nó là chứng minh khả năng chịu tải. Đo lưu lượng, độ sâu hàng đợi, thời gian chờ tiếp nhận, thời gian nằm hàng và thời gian xử lý để điều chỉnh theo dữ liệu thực.

## 10. Dừng, hủy và sự cố của consumer

### 10.1. BlockingQueue không có close chung

Giao diện `BlockingQueue` không cung cấp một phép `close` hoặc `shutdown` nội tại dùng chung cho mọi triển khai. Ứng dụng phải định nghĩa việc không còn nhận và không còn xử lý công việc theo giao thức riêng. [Java 17: vòng đời BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)

Trong ví dụ, thông điệp dừng được dùng vì có một producer và một consumer rõ ràng. Không dùng một giá trị nghiệp vụ có thể xuất hiện thật làm dấu dừng mà không có kiểu hoặc trường phân biệt.

### 10.2. Nhiều producer/consumer cần quy tắc chặt hơn

Nếu dùng thông điệp dừng:

- Chỉ phát thông điệp dừng khi các producer thường đã hoàn tất hoặc bị ngăn không đưa thêm.
- Định nghĩa đủ thông điệp hoặc cơ chế chuyển tiếp cho số consumer cần dừng.
- Chính thông điệp dừng cũng phải được tiếp nhận; không bỏ qua `offer` thất bại vì queue đầy.
- Chờ các consumer kết thúc để xác nhận phần đã lấy ra cũng hoàn tất theo quy tắc xử lý.
- Xác định trường hợp consumer lỗi giữa chừng và công việc chưa xử lý sẽ được báo hay khôi phục ra sao.

Một thông điệp dừng lấy ra bởi một consumer không tự làm tất cả consumer khác thức dậy và dừng.

### 10.3. Consumer chết có thể giữ producer chờ

Một producer dùng `put` không biết consumer đã ngừng vì lỗi. Nếu không còn ai lấy mà queue đầy, nó vẫn có thể chờ.

Cần có bên giám sát vòng đời, phản hồi lỗi và cách hủy việc chờ khi đường xử lý không còn tiến triển. Timed offer giúp tạo điểm kiểm tra lại điều kiện sống và ngân sách; nó không tự sửa consumer.

### 10.4. Không chờ trong khi giữ tài nguyên mà consumer cần

Nếu producer giữ một khóa rồi chờ queue có chỗ, trong khi consumer cần đúng khóa đó để xử lý/lấy bước tiếp theo, hai bên có thể không còn tiến triển.

Tình huống tương tự xảy ra nếu mọi luồng của một pool hữu hạn đều đang chạy producer bị chặn, còn các tác vụ consumer lại đang xếp hàng chờ được pool ấy chạy. Hàng đợi đúng hợp đồng vẫn không cứu được một đồ thị phụ thuộc thực thi bị kẹt.

Cần thiết kế nơi chạy producer/consumer và thứ tự giữ tài nguyên phù hợp, không chỉ tăng capacity để trì hoãn sự cố.

## 11. Công bố đối tượng qua hàng đợi

`BlockingQueue` có bảo đảm nhìn thấy dữ liệu: các hành động trước khi đưa đối tượng vào queue xảy ra trước theo quan hệ bộ nhớ đối với các truy cập/lấy ra đối tượng đó ở luồng khác. Điều này hỗ trợ bàn giao đối tượng đã khởi tạo. [Java 17: BlockingQueue memory consistency](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)

Nó không tự sao chép sâu phần tử và không tự bảo vệ việc producer tiếp tục sửa chính payload sau khi đã đưa vào.

Mẫu thuận lợi là tạo một thông điệp bất biến hoặc bàn giao quyền sửa rõ ràng. Record `Job` trong ví dụ chỉ chứa `int` và `boolean`, nên không có một danh sách hay mảng có thể sửa nằm bên trong để hai bên vô tình cùng thay đổi.

Nếu thông điệp chứa đối tượng có thể sửa, cần quy định ai còn được sửa và khi nào. Tính an toàn của hàng đợi và tính an toàn của dữ liệu được gửi là hai lớp hợp đồng.

## 12. Liên hệ với hàng đợi của ThreadPoolExecutor

Một pool cố định số luồng không đồng nghĩa hàng đợi có giới hạn hữu ích. `Executors.newFixedThreadPool` dùng hàng đợi chia sẻ không giới hạn theo mô tả API, nên lượng tác vụ chờ vẫn có thể tăng dù số luồng thực thi bị cố định. [Java 17: Executors.newFixedThreadPool](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html#newFixedThreadPool(int))

Với `ThreadPoolExecutor` cấu hình trực tiếp, việc tiếp nhận xét số luồng, khả năng đưa vào work queue và giới hạn luồng tối đa; khi không thể nhận, chính sách từ chối tham gia quyết định. Đặt một BlockingQueue có giới hạn không có nghĩa `execute` sẽ tự chuyển thành `put` và chặn đến khi có chỗ. [Java 17: ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)

`CallerRunsPolicy` là một ví dụ làm chậm bên gửi bằng cách chạy tác vụ bị từ chối ngay trên luồng gọi khi executor chưa shutdown. Nhưng khi executor đã shutdown, chính sách này bỏ tác vụ; nó cũng thay đổi ngữ cảnh và thời gian làm việc của luồng gọi. Không chọn nó mà bỏ qua hợp đồng đó. [Java 17: CallerRunsPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.CallerRunsPolicy.html)

Đây là ví dụ cho nguyên tắc chung: phải đọc hợp đồng của **tầng tiếp nhận**, không chỉ của cấu trúc queue nằm bên trong.

## 13. Những lỗi thường gặp

| Cách nghĩ dễ mắc | Điều còn thiếu |
|---|---|
| “Queue bounded nên tôi đã giới hạn toàn bộ bộ nhớ.” | Payload, công việc đang chạy, producer đang chờ và các bộ đệm ngoài queue vẫn giữ dữ liệu. |
| “offer đã được gọi nên tác vụ đã nhận.” | Phải kiểm tra `boolean`. |
| “put thành công nên việc đã xong.” | Chỉ biết đã tiếp nhận; hoàn tất cần phản hồi riêng. |
| “remainingCapacity còn chỗ nên add chắc thành công.” | Quan sát và thêm là hai thời điểm khác nhau. |
| “Queue rỗng nên có thể tắt hệ thống.” | Có thể còn producer và công việc đang xử lý. |
| “LinkedBlockingQueue mặc định sẽ sớm làm chậm producer.” | Capacity mặc định là `Integer.MAX_VALUE`, không phải ngân sách ứng dụng đã chọn. |
| “Thử lại mãi thì không mất việc.” | Có thể không còn ngân sách, không còn khả năng tiến triển hoặc đang giữ tải ngoài queue. |
| “FIFO bảo đảm hoàn tất đúng thứ tự.” | Nhiều consumer có thể hoàn tất khác thứ tự lấy. |
| “Poison object đã gửi một lần là mọi consumer dừng.” | Cần giao thức cho số consumer và điểm dừng của mọi producer. |

Một hàng đợi trong bộ nhớ cũng không tự là kho lưu bền hay cơ chế xử lý đúng một lần qua sự cố tiến trình. Nếu yêu cầu có bảo đảm lưu bền hoặc khôi phục, cần một thành phần sở hữu những bảo đảm ấy.

## 14. Bài tập tự kiểm tra

### Bài 1 — Nhận hay từ chối?

Một hàng đợi sức chứa `2` đang đầy. So sánh `add`, `offer`, `put` và timed `offer`.

**Đáp án:** `add` ném lỗi hết chỗ; `offer` trả `false`; `put` đợi chỗ hoặc bị ngắt; timed `offer` đợi trong thời hạn rồi trả `false` nếu vẫn không nhận được.

### Bài 2 — Sức chứa còn một

Hai producer cùng thấy `remainingCapacity() == 1` rồi cùng `add`. Có bảo đảm cả hai thành công không?

**Đáp án:** Không. Phép quan sát không đặt trước chỗ. Một bên có thể chiếm chỗ trước; hãy dùng kết quả của thao tác tiếp nhận phù hợp.

### Bài 3 — Một nghìn chỗ và tám consumer

Queue sức chứa `1.000` có tám consumer, mỗi consumer đang giữ một việc. Số công việc đã nhận nhưng chưa xong có thể vượt `1.000` không?

**Đáp án:** Có. Sức chứa chỉ giới hạn phần đang nằm trong queue. Công việc đã lấy để xử lý được tính riêng; còn có thể có các lô hoặc dữ liệu chờ ở tầng khác.

### Bài 4 — Rỗng nhưng còn làm

Consumer đã lấy phần tử cuối và đang ghi kết quả. Producer thấy `isEmpty` là `true`. Có thể coi toàn bộ đã hoàn tất không?

**Đáp án:** Không. Cần tín hiệu hoàn tất hoặc chờ consumer tương ứng, đồng thời biết không còn producer sẽ thêm việc mới.

### Bài 5 — Timed offer lặp lại

Mỗi lần offer được chờ một giây, vòng lặp thử lại không giới hạn. Nó có đáp ứng yêu cầu “mỗi yêu cầu chờ tối đa một giây tổng cộng” không?

**Đáp án:** Không. Mỗi lần thử đặt lại một khoảng chờ. Cần ngân sách tổng và quyết định khi hết ngân sách hoặc bị hủy.

### Bài 6 — Không có bộ đệm

Vì sao `SynchronousQueue.size()` là `0` mà `put` vẫn có thể phải chờ?

**Đáp án:** Lớp này bàn giao trực tiếp giữa bên gửi và bên nhận, không lưu phần tử trong bộ đệm. Chờ là đợi cuộc bàn giao phù hợp, không phải đợi ô mảng trống.

### Bài 7 — Thông điệp dừng

Có ba consumer và nhiều producer. Một producer gửi một thông điệp dừng trước khi các producer khác xong. Có những vấn đề gì?

**Đáp án:** Một consumer có thể dừng trong khi vẫn còn việc được thêm; các consumer khác có thể tiếp tục chờ không có tín hiệu dừng. Cần điểm ngừng nhận chung và cách dừng đủ các consumer, đồng thời xử lý việc tiếp nhận thông điệp dừng.

### Bài 8 — Bộ đệm lớn có chữa quá tải kéo dài?

Producer tạo đều nhanh hơn tốc độ xử lý. Tăng queue từ `100` lên `10.000` có làm hệ thống ổn định lâu dài không?

**Đáp án:** Không tự làm được. Nó tăng khả năng hấp thụ tạm thời và lượng việc chờ, nhưng không xóa chênh lệch tốc độ. Cần điều tiết nguồn, tăng năng lực xử lý phù hợp hoặc áp dụng chính sách quá tải được phép.

Một hàng đợi có giới hạn phát huy tác dụng khi sức chứa, cách tiếp nhận, tín hiệu hoàn tất và đường hủy được thiết kế cùng nhau. Khi hết chỗ, hành động của bên sản xuất phải có ý nghĩa rõ đối với nguồn công việc; đó mới là cơ sở của điều tiết tải có thể kiểm chứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-queues-004">ArrayBlockingQueue so với LinkedBlockingQueue</a>

<details>
<summary>Xem chi tiết</summary>

# ArrayBlockingQueue so với LinkedBlockingQueue

## 1. Cùng phối hợp sản xuất–tiêu thụ, khác cách lưu trữ và phối hợp bên trong

Trong một quy trình nhiều luồng, bên sản xuất tạo công việc rồi đặt vào hàng đợi; bên tiêu thụ lấy công việc ra để xử lý. Khi tốc độ hai bên khác nhau, hàng đợi giữ phần công việc đang chờ và tạo một nơi áp dụng chính sách khi chưa có dữ liệu hoặc không còn chỗ.

`ArrayBlockingQueue<E>` và `LinkedBlockingQueue<E>` đều thực hiện vai trò đó qua giao diện `BlockingQueue<E>`. Cả hai là hàng đợi FIFO: thêm ở cuối, lấy ở đầu. Cả hai có thể dùng với nhiều bên sản xuất và nhiều bên tiêu thụ.

Sự khác biệt quan trọng nằm ở ba câu hỏi:

1. Dung lượng được chọn và giới hạn thế nào?
2. Các tham chiếu đến phần tử được chứa trong mảng hay các nút liên kết?
3. Các thao tác thêm và lấy phối hợp bằng cơ chế khóa nào?

Các khác biệt ấy ảnh hưởng đến cách cấp phát bộ nhớ, tranh chấp và hiệu năng. Chúng không làm cho hợp đồng `put`, `take` hoặc timeout biến thành một bộ API khác.

Vì vậy, cần học theo hai lớp: hiểu hành vi chung để viết quy trình đúng, rồi hiểu đánh đổi của từng cài đặt để chọn và đo. Đừng chọn chỉ vì “mảng nhanh hơn” hoặc “hai khóa chắc chắn nhanh hơn một khóa”.

Bài này dùng Java 17 trở lên. Ba chương trình độc lập dưới đây đều có tên tệp, lệnh biên dịch và stdout. Các chương trình kiểm chứng hành vi và cách dùng chung giao diện; chúng không phải phép đo hiệu năng.

## 2. Bảng so sánh để xác định điều phải xem xét

| Khía cạnh | ArrayBlockingQueue | LinkedBlockingQueue |
|---|---|---|
| Cấu trúc chứa phần tử | Mảng tham chiếu có kích thước cố định | Các nút liên kết chứa tham chiếu |
| Dung lượng khi tạo | Phải chỉ định dung lượng | Có thể chỉ định; mặc định là `Integer.MAX_VALUE` |
| Dung lượng sau khi tạo | Không tự thay đổi | Giới hạn đã chọn cũng không tự thay đổi |
| Thứ tự phần tử | FIFO | FIFO |
| Tùy chọn công bằng qua constructor | Có cờ `fair` | Không có cờ tương ứng |
| Cấp phát cấu trúc chứa | Mảng được tạo trước theo dung lượng | Nút được cấp phát trong quá trình đưa phần tử |
| Phối hợp thêm/lấy trong OpenJDK 17 | Một khóa chính, hai điều kiện chờ | Khóa thêm và khóa lấy riêng, cùng phối hợp số đếm |
| Kết luận về tốc độ | Cần đo trên tải phù hợp | Cần đo trên tải phù hợp |

Các điểm về cấu trúc khóa ở đây mô tả cài đặt OpenJDK 17; phần dung lượng, FIFO và constructor là điều người dùng đọc qua hợp đồng API. Xem [ArrayBlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html) và [LinkedBlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html).

“Linked” không có nghĩa dung lượng sẽ tự tăng không giới hạn nếu ứng dụng đã truyền giới hạn cụ thể. “Array” cũng không có nghĩa ứng dụng được truy cập vị trí bên trong như một danh sách đánh chỉ số. Cả hai vẫn được sử dụng như hàng đợi.

Trong phần còn lại, cần tách các thuộc tính dễ bị trộn:

- **Dung lượng:** tối đa bao nhiêu phần tử được hàng đợi giữ.
- **Số phần tử hiện có:** trạng thái tại một lần quan sát.
- **Bộ nhớ thực dùng:** gồm cấu trúc hàng đợi, dữ liệu phần tử và các tham chiếu khác còn giữ.
- **Khả năng xử lý:** tốc độ và độ trễ của cả quy trình.

Một thay đổi ở một thuộc tính chưa chứng minh các thuộc tính còn lại sẽ tốt hơn.

## 3. Dung lượng: khác biệt constructor có thể đổi chính sách vận hành

### 3.1. Khi cùng đặt giới hạn, cả hai đều là hàng đợi có giới hạn

Hai cách tạo sau đều chọn tối đa 100 phần tử được xếp hàng:

```java
// Mã minh họa trong một phương thức, chưa phải chương trình độc lập.
BlockingQueue<Job> array = new ArrayBlockingQueue<>(100);
BlockingQueue<Job> linked = new LinkedBlockingQueue<>(100);
```

Nếu đầy, `put` có thể chờ chỗ. Nếu không muốn chờ điều kiện đầy/rỗng, dùng `offer` hoặc `poll` và xử lý kết quả. Dạng có timeout cho phép chờ theo chính sách của bên gọi.

Dung lượng phải dương khi dùng các constructor có tham số dung lượng của hai lớp này. Không chọn dung lượng 0 để biểu diễn một cơ chế chuyển giao không có vùng đệm; đó là một yêu cầu khác với hai cấu trúc đang so sánh.

### 3.2. LinkedBlockingQueue mặc định chưa tạo một giới hạn nhỏ hữu ích

Cách tạo không tham số của `LinkedBlockingQueue` dùng `Integer.MAX_VALUE`. Con số ấy không phải lời hứa có đủ bộ nhớ để chứa hơn hai tỷ công việc. Tài nguyên có thể cạn trước rất nhiều.

Nếu thay một hàng đợi dung lượng 100 bằng `new LinkedBlockingQueue<>()`, ứng dụng đã đổi cả giới hạn chấp nhận công việc, không chỉ đổi cách lưu trữ. Bên sản xuất có thể tiếp tục gửi trong lúc bên tiêu thụ chậm, làm số công việc chờ và thời gian chờ tăng lên.

Vì vậy, trước khi nói về bộ nhớ hoặc tốc độ, phải kiểm tra constructor thật sự được gọi.

### 3.3. Chương trình đối chiếu dung lượng và thứ tự

Tệp `QueueCapacityComparisonDemo.java`:

```java
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class QueueCapacityComparisonDemo {
    static void inspect(String label, BlockingQueue<Integer> queue) {
        boolean first = queue.offer(10);
        boolean second = queue.offer(20);
        boolean third = queue.offer(30);
        System.out.println(label + ": offers=" + first + "," + second + "," + third
                + "; remaining=" + queue.remainingCapacity());
        System.out.println(label + ": poll=" + queue.poll() + ","
                + queue.poll() + "," + queue.poll());
    }

    public static void main(String[] args) {
        inspect("array-2", new ArrayBlockingQueue<>(2));
        inspect("linked-2", new LinkedBlockingQueue<>(2));

        var defaultLinked = new LinkedBlockingQueue<Integer>();
        System.out.println("defaultLinkedInitial=" +
                defaultLinked.remainingCapacity());
        defaultLinked.offer(99);
        System.out.println("defaultLinkedAfterOne=" +
                defaultLinked.remainingCapacity());

        List<Integer> seed = List.of(10, 20);
        var seededArray = new ArrayBlockingQueue<Integer>(2, false, seed);
        var seededLinked = new LinkedBlockingQueue<Integer>(seed);
        System.out.println("seededArrayRemaining=" +
                seededArray.remainingCapacity());
        System.out.println("seededLinkedRemaining=" +
                seededLinked.remainingCapacity());
        System.out.println("seededArrayOffer30=" + seededArray.offer(30));
        System.out.println("seededLinkedOffer30=" + seededLinked.offer(30));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 QueueCapacityComparisonDemo.java
java QueueCapacityComparisonDemo
```

Stdout:

```text
array-2: offers=true,true,false; remaining=0
array-2: poll=10,20,null
linked-2: offers=true,true,false; remaining=0
linked-2: poll=10,20,null
defaultLinkedInitial=2147483647
defaultLinkedAfterOne=2147483646
seededArrayRemaining=0
seededLinkedRemaining=2147483645
seededArrayOffer30=false
seededLinkedOffer30=true
```

Hai lời gọi `inspect` đầu cùng dùng dung lượng 2. Cả hai nhận 10, 20, từ chối 30 bằng `false`, rồi lấy ra 10, 20 theo FIFO. Sau hai lần lấy, lần `poll` thứ ba trả `null`.

Phần `defaultLinked` chỉ thêm đúng một phần tử. Chương trình không thử lấp đầy giới hạn mặc định. Các số lớn được in là giới hạn số lượng còn lại theo API, không phải lượng bộ nhớ còn dùng được.

### 3.4. Constructor nhận collection không có nghĩa lấy size làm capacity

Một lỗi thay thế dễ bỏ sót là cho rằng khởi tạo `LinkedBlockingQueue` từ collection hai phần tử sẽ tạo dung lượng 2. Thực tế, constructor này cũng dùng giới hạn `Integer.MAX_VALUE` rồi nạp nội dung ban đầu.

Trong chương trình:

- `seededArray` được chỉ định dung lượng 2 và nạp đủ hai phần tử, nên không nhận thêm 30 bằng `offer`.
- `seededLinked` nhận cùng collection ban đầu nhưng giới hạn không phải 2, nên nhận thêm 30.

Để giữ giới hạn mong muốn khi khởi tạo dữ liệu cho một hàng đợi liên kết, có thể tạo bằng constructor dung lượng rồi nạp dữ liệu theo quy trình phù hợp. Việc nạp cũng phải kiểm tra số phần tử và chính sách khi đầy.

Đừng thay constructor trong một lần đổi lớp mà bỏ qua sự khác biệt này; đây có thể là thay đổi hành vi quan trọng hơn cả chênh lệch hiệu năng.

## 4. Mảng vòng và nút liên kết: hiểu đúng chi phí bộ nhớ

### 4.1. ArrayBlockingQueue giữ một mảng tham chiếu

Mô hình khái niệm của hàng đợi mảng:

```text
Các ô của một mảng đã được tạo trước
    [ tham chiếu ][ tham chiếu ][ ô trống ][ ô trống ]

Vị trí lấy và vị trí thêm di chuyển trong mảng.
Đi đến cuối thì quay lại đầu để tái sử dụng ô trống.
```

Đây là mảng chứa tham chiếu tới đối tượng, không phải một vùng đã cấp phát sẵn mọi đối tượng công việc. Tạo hàng đợi dung lượng 1.000 chưa tạo 1.000 đối tượng `Job`.

Các ô được tái sử dụng khi phần tử được lấy ra. Đường thêm/lấy thông thường không cần tạo một nút liên kết mới cho mỗi phần tử. Tuy nhiên, không được biến điều đó thành câu “mọi lời gọi đều không cấp phát gì”: ứng dụng vẫn có thể tạo công việc, dùng iterator hoặc gọi thao tác trả bản sao dữ liệu.

Khi chọn dung lượng rất lớn, mảng tham chiếu được tạo trước cũng là một khoản bộ nhớ phải chấp nhận ngay, kể cả khi hàng đợi phần lớn rỗng.

### 4.2. LinkedBlockingQueue giữ chuỗi nút

Mô hình khái niệm của hàng đợi liên kết:

```text
Đầu → [ nút: tham chiếu A ] → [ nút: tham chiếu B ] → Cuối

Mỗi nút mang liên kết đến nút tiếp theo.
Đối tượng A, B vẫn là dữ liệu riêng ở bên ngoài nút.
```

Trong OpenJDK 17, nút chứa trường phần tử và trường nối đến nút kế tiếp. Dung lượng là một giới hạn số lượng; không có một mảng tham chiếu dài bằng giới hạn đó được tạo trước. Nút được cấp phát theo đường thêm dữ liệu. Xem [cài đặt LinkedBlockingQueue của OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/concurrent/LinkedBlockingQueue.java).

Điều này tạo ra một đánh đổi cần đo: không phải trả trước cho một mảng dài bằng dung lượng, nhưng có thêm các đối tượng nút và hoạt động cấp phát/thu hồi trong quá trình sử dụng.

Không gán một số byte cố định cho mỗi nút trên mọi JVM. Kích thước đối tượng, căn chỉnh và cách biểu diễn tham chiếu phụ thuộc cấu hình máy ảo.

### 4.3. So sánh cấu trúc rỗng và cấu trúc gần đầy là hai tình huống khác

Nếu dung lượng chọn rất lớn nhưng mức chiếm dụng thường nhỏ, mảng được cấp phát trước có thể đáng kể. Nếu hàng đợi liên tục giữ nhiều phần tử hoặc luân chuyển rất nhanh, chi phí nút và thu hồi bộ nhớ của cách liên kết lại cần được xem xét.

Đó là các tình huống suy luận để đặt giả thuyết đo, không phải kết luận một bên luôn ít bộ nhớ hơn ở mọi trạng thái.

Cũng phải tính dữ liệu mà từng phần tử giữ. Một công việc mang ảnh, bộ đệm lớn hoặc một đồ thị đối tượng có thể chiếm phần lớn bộ nhớ, vượt xa chi phí ô tham chiếu hay nút hàng đợi.

Một ô mảng trống không tạo ra công việc, còn một tham chiếu trong hàng đợi có thể giữ cả dữ liệu lớn sống lâu hơn mong muốn.

### 4.4. Capacity chưa là giới hạn toàn bộ bộ nhớ của quy trình

Dung lượng giới hạn số phần tử đã xếp trong hàng đợi. Nó không giới hạn số luồng đang bị giữ, số đối tượng mà bên sản xuất đã tạo trước khi gửi, hoặc công việc đã được bên tiêu thụ lấy ra nhưng chưa xử lý xong.

Ví dụ suy luận: hàng đợi có dung lượng 100 nhưng hàng nghìn tác vụ sản xuất mỗi tác vụ đã tạo một công việc lớn rồi gọi `put`. Các công việc đang nằm ngoài hàng đợi mà tác vụ còn giữ cũng dùng bộ nhớ.

Tương tự, nhiều bên tiêu thụ có thể cùng giữ công việc đang xử lý. Chỉ quan sát `queue.size()` không cho biết tổng số công việc chưa hoàn tất.

Khi đặt mục tiêu giới hạn bộ nhớ hoặc số công việc đang hoạt động, cần định nghĩa ranh giới đầy đủ của quy trình. Hai hàng đợi không tự quản lý mọi đối tượng ngoài phần tử đã xếp của mình.

## 5. Một khóa và hai khóa: khác biệt tạo cơ hội, không tạo bảo đảm tốc độ

### 5.1. ArrayBlockingQueue phối hợp qua một khóa chính

Trong OpenJDK 17, `ArrayBlockingQueue` bảo vệ trạng thái mảng, số đếm và vị trí thêm/lấy bằng một `ReentrantLock` chính. Nó có hai điều kiện `notEmpty` và `notFull` để chờ dữ liệu hoặc chờ chỗ. Xem [cài đặt ArrayBlockingQueue của OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/concurrent/ArrayBlockingQueue.java).

Mô hình để hình dung:

```text
Thêm  → khóa chính → cập nhật mảng/số đếm → nhả khóa
Lấy   → khóa chính → cập nhật mảng/số đếm → nhả khóa

Rỗng/đầy → chờ điều kiện phù hợp
```

Một khóa chính không có nghĩa một luồng `take` đang chờ rỗng sẽ giữ khóa mãi khiến bên sản xuất không vào được. Cơ chế chờ điều kiện phải cho phép bên khác thay đổi điều kiện.

Nó cũng không có nghĩa toàn bộ xử lý công việc của người dùng nằm bên trong khóa hàng đợi. Sau khi `take` trả phần tử, mã xử lý riêng của ứng dụng không tự bị gói trong vùng khóa nội bộ đó.

### 5.2. LinkedBlockingQueue tách đường thêm và đường lấy

Trong cài đặt OpenJDK 17, `LinkedBlockingQueue` có khóa riêng cho bên thêm và bên lấy, cùng số đếm nguyên tử để phối hợp. Một số thao tác cần phối hợp cả hai phía. Đây là chi tiết cài đặt giúp giải thích khả năng chồng lấp một số hoạt động, không phải API khóa để ứng dụng dựa vào.

```text
Thêm → khóa thêm → cập nhật phía cuối
                    ↕ phối hợp số đếm và tín hiệu
Lấy  → khóa lấy  → cập nhật phía đầu
```

Các bên sản xuất vẫn có thể tranh chấp cùng khóa thêm. Các bên tiêu thụ vẫn có thể tranh chấp cùng khóa lấy. Hai phía cũng không hoàn toàn độc lập vì phải chia sẻ trạng thái và đánh thức đúng bên đang chờ.

Vì vậy, hai khóa không có nghĩa mọi `put` và `take` luôn chạy song song, càng không có nghĩa thông lượng phải gấp đôi. Các thao tác như xóa tùy ý, tạo một số dạng kết quả quan sát hoặc xử lý hàng loạt có thể có đường phối hợp khác với thêm/lấy đầu cuối.

### 5.3. Không lớp nào trở thành cấu trúc không khóa chỉ vì tên Concurrent

Cả hai lớp đang so sánh đều là hàng đợi có thể chặn luồng. Dùng `offer(e)` để không đợi chỗ trống không tự biến đường thực thi bên trong thành thuật toán không khóa.

Tốc độ quan sát được còn chịu ảnh hưởng của lịch thực thi, số luồng hoạt động, mức chiếm dụng hàng đợi, việc đánh thức luồng và cấp phát. Chi tiết cấu trúc giúp đặt giả thuyết, nhưng không thay thế phép đo.

Nếu thay lớp và tốc độ khác đi, cần tìm nguyên nhân trong cùng một cấu hình dung lượng và cùng mô hình tải, thay vì gán kết quả ngay cho số khóa.

## 6. FIFO và công bằng là hai khái niệm riêng

FIFO nói về thứ tự phần tử trong hàng đợi: phần tử được đưa vào trước đứng trước phần tử được đưa vào sau theo thứ tự chèn được hàng đợi xác lập.

Công bằng nói về chính sách cấp quyền cho các luồng đang chờ cạnh tranh thao tác. `ArrayBlockingQueue` có constructor với cờ `fair`; mặc định không bật chính sách công bằng. `LinkedBlockingQueue` không có constructor có cờ tương ứng.

Các cách tạo minh họa, chưa phải chương trình độc lập:

```java
BlockingQueue<Job> normal = new ArrayBlockingQueue<>(100);
BlockingQueue<Job> fair = new ArrayBlockingQueue<>(100, true);
BlockingQueue<Job> linked = new LinkedBlockingQueue<>(100);
```

Cờ công bằng không đổi FIFO của phần tử thành một thứ tự mới. Nó cũng không đảm bảo thứ tự hoàn tất công việc của nhiều bên tiêu thụ.

Ví dụ suy luận:

```text
Bên tiêu thụ A lấy công việc 1 → xử lý lâu
Bên tiêu thụ B lấy công việc 2 → xử lý nhanh → hoàn tất trước
```

Không có mâu thuẫn với FIFO: thứ tự lấy và thứ tự xử lý xong là hai chuyện khác nhau.

Tài liệu `ArrayBlockingQueue` mô tả công bằng thường làm giảm thông lượng nhưng giảm biến động và tránh một số bên chờ bị thiếu cơ hội. Không coi đó là lời hứa thời gian chờ tối đa theo số mili giây hay quyền được CPU ngay lập tức.

Muốn đánh giá cờ này, quan sát cả phân bố thời gian chờ giữa các bên, không chỉ tổng số phần tử mỗi giây. Một lần chạy với vài luồng in ra trước/sau hoặc đặt `Thread.sleep` không chứng minh được toàn bộ chính sách công bằng.

Khi đổi từ một `ArrayBlockingQueue` đã bật công bằng sang `LinkedBlockingQueue`, cần ghi nhận đây là một thay đổi điều kiện so sánh. Không âm thầm coi hai cấu hình ấy có cùng chính sách chờ.

## 7. Giữ nguyên chính sách chờ khi đổi lớp triển khai

### 7.1. API chung giữ hành vi mà bên gọi dựa vào

Với cùng giới hạn và trạng thái:

- `put(e)` có thể chờ chỗ; `take()` có thể chờ dữ liệu.
- `offer(e)` báo không thêm được bằng `false`.
- `poll()` báo chưa lấy được phần tử bằng `null`.
- Dạng có timeout cho phép chờ trong một khoảng theo đơn vị đã chọn.
- Các thao tác chờ có khả năng báo `InterruptedException`.
- Không lớp nào nhận phần tử `null`.

Đây là hợp đồng qua `BlockingQueue`, không phải khác biệt cần dùng để chọn một trong hai. Xem [BlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).

Tệp `QueueWaitingPolicyDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class QueueWaitingPolicyDemo {
    static void inspect(String label, BlockingQueue<String> queue)
            throws InterruptedException {
        queue.put("A");
        boolean accepted = queue.offer("B", 20, TimeUnit.MILLISECONDS);
        String first = queue.take();
        String missing = queue.poll(20, TimeUnit.MILLISECONDS);

        System.out.println(label + ": fullOffer=" + accepted
                + "; taken=" + first + "; emptyPoll=" + missing);
        System.out.println(label + ": immediateOffer=" + queue.offer("C")
                + "; next=" + queue.take()
                + "; remaining=" + queue.remainingCapacity());
    }

    public static void main(String[] args) throws InterruptedException {
        inspect("array-1", new ArrayBlockingQueue<>(1));
        inspect("linked-1", new LinkedBlockingQueue<>(1));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 QueueWaitingPolicyDemo.java
java QueueWaitingPolicyDemo
```

Stdout:

```text
array-1: fullOffer=false; taken=A; emptyPoll=null
array-1: immediateOffer=true; next=C; remaining=1
linked-1: fullOffer=false; taken=A; emptyPoll=null
linked-1: immediateOffer=true; next=C; remaining=1
```

Mỗi hàng đợi đều có dung lượng 1. Khi đang giữ A và không có bên lấy, thử đưa B có timeout thất bại bằng `false`. Sau khi lấy A và không có bên thêm, lần `poll` có timeout trả `null`.

Khi có chỗ, C được đưa và lấy bình thường. Kết quả giống nhau cho thấy mã dùng giao diện có thể giữ chính sách ở điểm gọi khi đổi lớp.

Chương trình không in thời gian thực tế. Timeout 20 mili giây là tham số cho phép chờ, không phải một kỳ vọng stdout về độ chính xác của bộ lập lịch.

### 7.2. Những thứ vẫn phải được xử lý ở bên gọi

Đổi từ mảng sang liên kết không sửa được các lỗi sau:

- Bỏ qua `false` từ `offer` và coi như công việc đã được nhận.
- Nuốt `InterruptedException` rồi tiếp tục chờ vô hạn.
- Kiểm tra `remainingCapacity()` trước rồi coi đã giữ chỗ.
- Nhìn `isEmpty()` rồi kết luận nguồn đã hết công việc.
- Tự khóa bên ngoài rồi chờ `put`, trong khi bên tiêu thụ cần chính khóa đó.
- Đưa cả bên sản xuất đang chờ và bên tiêu thụ chưa chạy vào một nhóm thực thi không còn luồng rảnh.

Các lỗi này thuộc quan hệ phối hợp và vòng đời tác vụ. Cách lưu phần tử không tự phá một vòng phụ thuộc ở tầng ứng dụng.

### 7.3. Chia sẻ đối tượng và kết thúc nguồn cũng giữ nguyên ranh giới

Cả hai chuyển giao tham chiếu qua hàng đợi. Bảo đảm tính nhìn thấy tại điểm chuyển giao không tự làm dữ liệu con có thể thay đổi trở thành đối tượng dùng chung an toàn.

Nếu bên sản xuất tiếp tục sửa công việc sau khi gửi và bên tiêu thụ đồng thời đọc, ứng dụng cần một quy ước sở hữu, dữ liệu bất biến hoặc đồng bộ riêng.

Cả hai cũng không có một lệnh đóng hàng đợi chung để tự kết thúc nguồn. Thông điệp dừng hoặc gián đoạn phải nằm trong một giao thức rõ. Một lần `poll` hết thời gian không phải bằng chứng mọi bên sản xuất đã hoàn tất.

Đừng sửa các quy tắc xử lý dữ liệu và kết thúc chỉ vì thay constructor.

## 8. Cùng một quy trình cho hai lớp

### 8.1. Hai bên sản xuất, hai bên tiêu thụ

Chương trình sau đặt cùng dung lượng 4 cho cả hai lớp. Hai bên sản xuất đưa các mã 1 đến 20 và 21 đến 40. Hai bên tiêu thụ cùng lấy dữ liệu.

Số 0 được dành riêng làm tín hiệu dừng; dữ liệu thật trong ví dụ luôn là số dương. Luồng chính chỉ gửi hai tín hiệu sau khi cả hai bên sản xuất đã hoàn tất, tương ứng một tín hiệu cho mỗi bên tiêu thụ.

Tệp `CommonQueuePipelineDemo.java`:

```java
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CommonQueuePipelineDemo {
    record Result(int received, int unique, int sum, boolean allIds, int remaining) {}

    static int consume(BlockingQueue<Integer> queue,
            ConcurrentLinkedQueue<Integer> received, CyclicBarrier start)
            throws Exception {
        start.await(10, TimeUnit.SECONDS);
        int sum = 0;
        while (true) {
            int value = queue.take();
            if (value == 0) {
                return sum;
            }
            received.add(value);
            sum += value;
        }
    }

    static Void produce(BlockingQueue<Integer> queue, int from, int to,
            CyclicBarrier start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        for (int id = from; id <= to; id++) {
            queue.put(id);
        }
        return null;
    }

    static Result run(BlockingQueue<Integer> queue) throws Exception {
        var received = new ConcurrentLinkedQueue<Integer>();
        var start = new CyclicBarrier(4);
        var pool = Executors.newFixedThreadPool(4);

        try {
            var firstConsumer = pool.submit(() -> consume(queue, received, start));
            var secondConsumer = pool.submit(() -> consume(queue, received, start));
            var firstProducer = pool.submit(() -> produce(queue, 1, 20, start));
            var secondProducer = pool.submit(() -> produce(queue, 21, 40, start));

            firstProducer.get(10, TimeUnit.SECONDS);
            secondProducer.get(10, TimeUnit.SECONDS);
            queue.put(0);
            queue.put(0);

            int sum = firstConsumer.get(10, TimeUnit.SECONDS)
                    + secondConsumer.get(10, TimeUnit.SECONDS);
            var unique = new HashSet<>(received);
            boolean allIds = true;
            for (int id = 1; id <= 40; id++) {
                allIds &= unique.contains(id);
            }
            return new Result(received.size(), unique.size(), sum,
                    allIds, queue.size());
        } finally {
            pool.shutdownNow();
        }
    }

    static void print(String label, Result result) {
        System.out.println(label + ": received=" + result.received()
                + "; unique=" + result.unique()
                + "; sum=" + result.sum()
                + "; allIds=" + result.allIds()
                + "; remaining=" + result.remaining());
    }

    public static void main(String[] args) throws Exception {
        print("array-4", run(new ArrayBlockingQueue<>(4)));
        print("linked-4", run(new LinkedBlockingQueue<>(4)));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CommonQueuePipelineDemo.java
java CommonQueuePipelineDemo
```

Stdout:

```text
array-4: received=40; unique=40; sum=820; allIds=true; remaining=0
linked-4: received=40; unique=40; sum=820; allIds=true; remaining=0
```

Bốn tác vụ có bốn luồng thực thi, cùng đi qua cổng bắt đầu. Các bên tiêu thụ có thể chạy khi các bên sản xuất đang phải chờ chỗ. Chương trình không đặt mọi luồng vào trạng thái sản xuất bị chặn rồi mới mong một tác vụ tiêu thụ trong cùng nhóm bắt đầu.

Luồng chính lấy kết quả của cả hai bên sản xuất trước khi gửi tín hiệu dừng. Vì vậy, không có dữ liệu mới được một bên sản xuất hợp lệ của thí nghiệm đặt sau các tín hiệu đó.

Sau khi mọi bên tiêu thụ hoàn tất, kết quả được kiểm tra theo các thuộc tính không phụ thuộc lịch phân chia công việc:

- Nhận đủ 40 phần tử dữ liệu.
- Có 40 mã khác nhau và đủ miền 1 đến 40.
- Tổng các mã là 820.
- Hàng đợi không còn phần tử.

Không in mỗi bên tiêu thụ nhận những mã nào, vì phần phân chia đó có thể thay đổi giữa các lần chạy.

### 8.2. Chương trình chứng minh điều gì và chưa chứng minh điều gì?

Nó kiểm chứng rằng cùng một quy trình dùng `BlockingQueue` chạy đúng trên cả hai lớp với giới hạn giống nhau. Nó kiểm tra không thiếu/trùng mã trong thí nghiệm có điều kiện nguồn đã xác định.

Nó không đo hay kết luận:

- Lớp nào có thông lượng cao hơn.
- Lớp nào có độ trễ cuối phân bố thấp hơn.
- Mức cấp phát của một tải dài.
- Mức công bằng giữa các tác vụ.
- Mọi lịch thực thi của một ứng dụng khác đều không có lỗi.

Các số nguyên nhỏ và công việc cộng số ở đây giúp tập trung vào phối hợp; chúng không đại diện cho kích thước và thời gian xử lý mọi công việc thật.

Nếu một bên tiêu thụ hỏng trong ứng dụng thật, bên sản xuất có thể tiếp tục chờ `put`. Một quy trình đầy đủ còn cần báo lỗi và hủy để các bên khác biết cách dừng. Việc chọn mảng hay liên kết không tự bổ sung đường lỗi đó.

### 8.3. Không suy ra thứ tự tổng từ thứ tự mã của nhiều bên sản xuất

Mỗi bên sản xuất có vòng lặp tăng dần riêng. Nhưng hai bên cùng đưa vào nên thứ tự xen kẽ không bắt buộc là 1, 2, 3 rồi đến hết 40.

FIFO áp dụng cho thứ tự chèn được xác lập ở hàng đợi. Nó không tự sắp mã số tăng dần và cũng không sắp theo thời điểm một luồng bắt đầu nghĩ đến việc gửi dữ liệu.

Nếu nghiệp vụ cần thứ tự theo một số thứ tự toàn cục, hãy thiết kế việc cấp và công bố số đó, hoặc chọn cơ chế xử lý phù hợp. Không lấy tính FIFO để hứa một thứ tự khác chưa được định nghĩa.

## 9. Chọn lớp và đo hiệu năng mà không đổi câu hỏi giữa chừng

### 9.1. Những hướng lựa chọn có căn cứ

| Điều đang ưu tiên | Hướng cân nhắc |
|---|---|
| Muốn dung lượng bắt buộc rõ và tái sử dụng vùng mảng tham chiếu | Xem `ArrayBlockingQueue` |
| Muốn lựa chọn công bằng qua constructor | Xem `ArrayBlockingQueue` với cờ phù hợp |
| Muốn cấu trúc nút theo lượng dữ liệu thay vì cấp một mảng bằng capacity ngay từ đầu | Xem `LinkedBlockingQueue`, vẫn chọn giới hạn có chủ ý |
| Có tải thêm/lấy nhiều luồng cần kiểm tra khả năng chồng lấp hai phía | Đo `LinkedBlockingQueue` cùng cấu hình tải với phương án mảng |
| Bên tiêu thụ chậm hơn nguồn một cách kéo dài | Xem lại năng lực xử lý và chính sách đầy; đổi lớp không đủ |
| Cần độ bền sau khi tiến trình dừng hoặc xác nhận xử lý | Cần thêm cơ chế ngoài hai hàng đợi trong bộ nhớ |

Đây là các hướng đặt giả thuyết lựa chọn. Không có một hàng duy nhất trong bảng đủ để thay thế các yêu cầu vận hành thực tế.

Một mảng được cấp trước có thể phù hợp với mức tải ổn định và mong muốn kiểm soát cấu trúc bộ nhớ. Một hàng đợi liên kết có thể là ứng viên cần đo khi số luồng và đường thêm/lấy làm tranh chấp trở thành yếu tố đáng kể. Cả hai quyết định vẫn phải xét dữ liệu phần tử, giới hạn và độ trễ.

### 9.2. So sánh phải giữ cùng chính sách dung lượng

Một phép đo dùng `new ArrayBlockingQueue<>(100)` đối chiếu với `new LinkedBlockingQueue<>()` có thể đang so sánh một bên bị buộc chờ với một bên tiếp tục tích lũy dữ liệu.

Nếu chỉ đếm số lần bên sản xuất gửi xong, kết quả không cho biết bên nào xử lý công việc nhanh hơn hay có giữ độ trễ chấp nhận được không. Dữ liệu chưa xử lý có thể đang dồn trong bộ nhớ.

Điểm so sánh công bằng về câu hỏi cần trả lời là cùng dung lượng, cùng chính sách khi đầy, cùng số bên sản xuất/tiêu thụ và cùng công việc. Nếu chủ ý so hai chính sách khác nhau, phải nêu rõ đó là thí nghiệm về chính sách.

Cờ công bằng của phương án mảng cũng là một biến cấu hình phải được ghi lại.

### 9.3. Những đại lượng cần đo riêng

- **Thông lượng chấp nhận:** bao nhiêu phần tử được đưa vào thành công mỗi đơn vị thời gian.
- **Thông lượng hoàn tất:** bao nhiêu công việc đã được xử lý xong.
- **Thời gian chờ đưa vào:** bên sản xuất bị giữ bao lâu khi đầy hoặc khi tranh chấp.
- **Thời gian nằm trong hàng đợi:** từ lúc được nhận đến lúc được lấy.
- **Độ trễ toàn bộ:** đến khi việc xử lý thực sự hoàn tất.
- **Cấp phát và thu hồi bộ nhớ:** cấu trúc hàng đợi và dữ liệu cần được phân biệt.
- **Trạng thái tải:** số phần tử đang xếp, số đang xử lý, số gửi thất bại hoặc hết thời gian.

Một phép đo `offer` trên hàng đợi luôn đầy có thể báo rất nhiều lời gọi mỗi giây chỉ vì chúng nhanh chóng trả `false`. Nếu bỏ qua số lần thành công, con số cao ấy dễ bị hiểu sai thành năng lực xử lý dữ liệu.

Tương tự, số công việc đã nhận cao nhưng thời gian nằm trong hàng đợi tăng mãi là một tình trạng khác với quy trình theo kịp tải.

### 9.4. Đừng dùng một vòng lặp và nanoTime để kết luận chung

Máy ảo cần có điều kiện chạy ổn định; kết quả có thể chịu ảnh hưởng của biên dịch JIT, tối ưu hóa, cấp phát, bộ thu gom rác, CPU và lịch thực thi. Một phép in nhật ký trong mỗi thao tác cũng có thể làm chi phí in lấn át chi phí hàng đợi.

Nếu cần đo ở mức vi mô, có thể dùng [JMH của OpenJDK](https://github.com/openjdk/jmh) để tổ chức phép đo, rồi kết hợp đo quy trình ứng dụng thực. Không lấy một con số từ thí nghiệm chức năng trong bài làm số liệu hiệu năng.

Thiết kế tải nên bao gồm các trạng thái có ý nghĩa: gần rỗng, mức chiếm dụng trung bình và thường xuyên đầy; một bên sản xuất–một bên tiêu thụ và nhiều bên mỗi phía; công việc nhỏ và dữ liệu đại diện.

Giữ cùng việc tạo phần tử ở hai phương án. Nếu một bên tạo payload lớn mới mỗi lần, bên kia tái sử dụng đối tượng nhỏ, phép đo đã thay cả chi phí dữ liệu.

### 9.5. Tăng capacity không sửa được chênh lệch tốc độ dài hạn

Xét mô hình đơn giản: bên sản xuất đưa 600 công việc mỗi giây, bên tiêu thụ chỉ hoàn tất tương đương 400 công việc mỗi giây, các tốc độ coi như ổn định và không có cơ chế từ chối.

Phần chờ tăng khoảng 200 công việc mỗi giây. Một vùng trống cho thêm 1.000 công việc chỉ hấp thụ được khoảng 5 giây theo mô hình này. Đây là phép ước lượng để hiểu xu hướng, không phải dự đoán chính xác lịch thực thi hay độ trễ của một hàng đợi Java.

Đổi mảng sang liên kết có thể thay chi phí phối hợp, nhưng không tự làm một nguồn chậm ngoài hàng đợi xử lý đủ 600 công việc mỗi giây. Cần xem nút nghẽn thực sự, khả năng tăng xử lý, điều tiết nguồn và chính sách quá tải.

Hàng đợi lớn giúp hấp thụ đợt tăng ngắn trong một số trường hợp, đồng thời có thể giữ nhiều dữ liệu hơn và làm công việc chờ lâu hơn. Dung lượng là một quyết định về vận hành, không chỉ một tham số tối ưu.

## 10. Các thao tác phụ và những giả định không nên mang theo

### 10.1. Xóa tùy ý khác lấy đầu/cuối

Một hàng đợi liên kết không tự có tra cứu theo giá trị O(1). Muốn tìm một đối tượng tùy ý để xóa vẫn phải xác định vị trí của nó. Với hàng đợi mảng, xóa mục ở giữa có thể đòi hỏi sắp lại phần tham chiếu liên quan trong cài đặt.

Vì vậy, nếu mô hình thường xuyên tìm và hủy theo mã công việc, cần đánh giá riêng đường đó. Đừng đo chỉ `put`/`take` rồi suy ra mọi thao tác hủy đều có cùng chi phí.

### 10.2. Lấy theo lô không tạo một giao dịch xử lý

`drainTo` có thể lấy những phần tử hiện có sang collection đích để xử lý một nhóm. Điều đó chưa có nghĩa phần xử lý phía sau là nguyên tử hoặc chắc chắn hoàn tất.

Nếu collection đích hoặc bước xử lý lỗi, ứng dụng cần hiểu dữ liệu đã được chuyển đến đâu. Cũng không dùng một collection đích đang bị nhiều bên sửa thiếu đồng bộ chỉ vì nguồn là một hàng đợi an toàn nhiều luồng.

Kích thước lô là một biến cấu hình khác: lô lớn có thể đổi phân chia công việc, thời gian giữ tài nguyên và độ trễ. Khi so sánh hai lớp, giữ hoặc ghi rõ biến này.

### 10.3. Duyệt hàng đợi không phải cơ chế nhận việc

Iterator dùng để quan sát có tính nhất quán yếu trong lúc cập nhật. Đừng dùng một lần duyệt rồi tự xem mỗi phần tử là đã được giao độc quyền cho người đọc.

Nếu đúng nhu cầu là nhận và bỏ phần tử khỏi hàng đợi, dùng thao tác lấy phù hợp. Nếu cần báo cáo trên một trạng thái cố định, hãy định nghĩa cách ổn định hoặc sao chép dữ liệu và mức nhất quán được yêu cầu.

Việc in được danh sách phần tử trông hợp lý không chứng minh mọi cập nhật đồng thời đã xuất hiện trong cùng một ảnh chụp.

## 11. Thực hành để tự kiểm tra lựa chọn

### Bài 1: constructor đã đổi điều gì?

Một ứng dụng đang dùng `new ArrayBlockingQueue<>(200)`. Người viết thay thành `new LinkedBlockingQueue<>()` rồi thấy bên sản xuất ít chờ hơn.

Hãy giải thích vì sao chưa thể kết luận cấu trúc liên kết nhanh hơn. Cần đo thêm gì về số phần tử tồn, số công việc hoàn tất, bộ nhớ và thời gian chờ?

Hướng giải thích: giới hạn chấp nhận dữ liệu đã thay đổi. Trước tiên cần đối chiếu hai cấu hình cùng capacity hoặc mô tả rõ sự khác biệt chính sách đang được thử.

### Bài 2: tải rỗng nhiều và tải luân chuyển cao

Một cấu hình có capacity rất lớn nhưng thường chỉ giữ ít phần tử. Cấu hình khác luôn có dữ liệu luân chuyển với tốc độ cao.

Vì sao hai cấu hình có thể có đánh đổi bộ nhớ khác nhau giữa mảng tạo trước và nút cấp phát theo đường thêm? Các dữ liệu nào cần thu thập trước khi quyết định?

Hướng giải thích: xem bộ nhớ của mảng rỗng, số nút đang sống, tốc độ cấp phát, tác động thu hồi và kích thước payload. Không gán số byte hoặc kết luận cố định khi chưa xác định JVM và tải.

### Bài 3: hai khóa có nghĩa nhanh gấp đôi không?

Một bên sản xuất và một bên tiêu thụ thực hiện công việc chậm ngoài hàng đợi. Đổi sang cơ chế có khóa thêm/lấy riêng nhưng tổng thời gian gần như không đổi.

Điều này có mâu thuẫn với cơ chế của `LinkedBlockingQueue` không? Hãy chỉ ra phần thời gian nào có thể đang chiếm ưu thế.

Hướng giải thích: cơ hội chồng lấp các thao tác hàng đợi chưa làm tăng tốc mọi phần của quy trình. Công việc ngoài hàng đợi hoặc một nguồn tài nguyên khác có thể là nơi quyết định tốc độ.

### Bài 4: FIFO hay công bằng?

Hai bên tiêu thụ cùng nhận việc từ một hàng đợi FIFO. Một bên hoàn tất nhiều hơn bên còn lại.

Có thể dựa riêng vào số lượng đó để kết luận hàng đợi không FIFO không? Bật công bằng có đảm bảo hai bên hoàn tất bằng nhau không?

Hướng giải thích: thứ tự phần tử, quyền truy cập khi chờ và tốc độ xử lý riêng của mỗi bên là ba yếu tố khác nhau. Cần chọn phép quan sát đúng với điều muốn đánh giá.

### Bài 5: giữ nguyên giao thức khi đổi lớp

Lấy `CommonQueuePipelineDemo`, thay hai constructor bằng cùng một dung lượng khác, vẫn giữ bốn tác vụ và hai tín hiệu dừng.

Hãy nêu những kết quả chức năng phải giữ và những thuộc tính không được khẳng định: phân chia mã cho từng bên, thời gian chạy, mức công bằng hay thứ tự hoàn tất.

Sau đó giả sử một bên tiêu thụ ném lỗi trước khi nhận tín hiệu dừng. Chỉ đổi lớp hàng đợi có đủ làm các bên còn lại thoát khỏi mọi lời gọi chờ không? Cần một cơ chế báo lỗi và hủy nào ở tầng phối hợp?

### Những câu hỏi trước khi chốt lựa chọn

1. Constructor thật sự đặt giới hạn nào?
2. Mức dữ liệu tồn thường gặp và mức đột biến là bao nhiêu?
3. Chi phí nằm ở hàng đợi hay ở công việc được xử lý?
4. Mục tiêu bộ nhớ có tính cả payload, bên sản xuất chờ và việc đang xử lý không?
5. Có cần chính sách công bằng riêng không?
6. Khi đầy, ứng dụng chờ, từ chối hay thử lại theo ngân sách nào?
7. Phép đo có giữ cùng dung lượng, tải, chính sách và dữ liệu không?
8. Giao thức kết thúc và xử lý lỗi có hoạt động trên cả hai lớp không?

Chọn giữa hai lớp là chọn một đánh đổi lưu trữ và phối hợp trong một quy trình đã được định nghĩa rõ. Hợp đồng đúng, dung lượng có chủ ý và phép đo phù hợp giúp quyết định có căn cứ hơn tên lớp hay một kết quả thời gian đơn lẻ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-queues-005">PriorityBlockingQueue và giới hạn dung lượng thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# PriorityBlockingQueue và giới hạn dung lượng thực tế

`PriorityBlockingQueue` giải quyết việc **chọn phần tử theo ưu tiên trong môi trường nhiều luồng**. Nó không tự giải quyết việc **giới hạn lượng công việc được nhận**.

Hai yêu cầu đó thường đi cùng nhau trong một bộ lập lịch, nhưng là hai quyết định độc lập:

- Trong các công việc đã được nhận và còn chờ, việc nào được lấy trước?
- Khi công việc chưa xử lý đã quá nhiều, có được nhận thêm không?

Sai lầm phổ biến là nghĩ `new PriorityBlockingQueue<>(100)` chỉ cho chứa `100` việc, hoặc nghĩ `put` sẽ chặn bên sản xuất khi đạt mức đó. Đối số constructor là **dung lượng khởi tạo**, không phải sức chứa tối đa.

Bài học dùng API Java 17, giải thích thứ tự ưu tiên, các giới hạn bộ nhớ thực tế và một ví dụ giới hạn tiếp nhận có giao thức giấy phép rõ ràng.

Các thuật ngữ cần phân biệt:

- **Dung lượng khởi tạo (initial capacity):** quy mô lưu trữ ban đầu để cấu trúc có thể tự tăng khi thêm.
- **Giới hạn tiếp nhận:** số lượng mà ứng dụng cho phép giữ trong phạm vi đã định nghĩa.
- **Ưu tiên lấy ra:** thứ tự chọn trong các phần tử hiện đang đủ điều kiện được lấy.
- **Công việc đang chạy:** phần tử đã rời queue và không còn tham gia lựa chọn trong queue.
- **Điều tiết ngược (backpressure):** phản hồi quá tải về phía gửi bằng chờ, từ chối hoặc một chính sách rõ ràng.
- **Giấy phép (permit):** một đơn vị ngân sách được cấp trước khi tiếp nhận và trả lại theo đúng điểm giải phóng tài nguyên.

## 1. PriorityBlockingQueue thực sự bảo đảm gì?

`PriorityBlockingQueue` là hàng đợi đồng thời **không có giới hạn sức chứa theo chính sách của lớp**, dùng thứ tự tự nhiên hoặc một `Comparator` để chọn phần tử. Việc thêm vẫn có thể thất bại do cạn tài nguyên, chẳng hạn `OutOfMemoryError`; “không giới hạn” không có nghĩa máy có bộ nhớ vô hạn. [Java 17: PriorityBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html)

Từ `Blocking` cần được đọc theo hợp đồng cụ thể:

- `take` có thể chờ khi chưa có phần tử.
- `poll(timeout, unit)` có thể chờ phần tử trong một khoảng thời gian.
- `put` không chờ một chỗ trống do queue đầy, vì lớp không có giới hạn sức chứa kiểu đó.
- Timed `offer` không tạo thêm một giới hạn tiếp nhận.

Có thể hình dung:

~~~text
Phía gửi:
    thêm hợp lệ -> queue có thể tăng vùng lưu trữ

Phía nhận:
    queue rỗng -> take chờ
    queue có phần tử -> lấy phần tử đứng đầu theo thứ tự ưu tiên
~~~

Đây là hàng đợi chặn ở phía lấy khi rỗng, không phải một bộ đệm ưu tiên có sức chứa cố định.

## 2. initialCapacity không phải maximumCapacity

Đoạn minh họa trong một phương thức:

~~~java
java.util.concurrent.PriorityBlockingQueue<Integer> queue =
        new java.util.concurrent.PriorityBlockingQueue<>(1);

queue.offer(30);
queue.offer(10);
queue.offer(20);
~~~

Ba phần tử có thể được nhận dù constructor có số `1`. Lớp được phép mở rộng vùng lưu trữ.

Điều tương tự không đúng với `ArrayBlockingQueue<>(1)`, nơi đối số là sức chứa cố định của bộ đệm. Đọc cùng một số ở hai constructor khác nhau nhưng bỏ tên tham số có thể dẫn đến một thiết kế quá tải sai hoàn toàn.

### 2.1. remainingCapacity là dấu hiệu hợp đồng

`PriorityBlockingQueue.remainingCapacity()` luôn trả `Integer.MAX_VALUE` vì lớp không bị ràng buộc sức chứa theo hợp đồng. [Java 17: PriorityBlockingQueue.remainingCapacity](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html#remainingCapacity())

Không diễn giải kết quả ấy thành:

- Máy còn đủ bộ nhớ cho đúng từng đó phần tử.
- Đây là một số lượng chỗ trống thực đang được đặt sẵn.
- `size + remainingCapacity` là giới hạn nghiệp vụ của ứng dụng.
- Lần thêm kế tiếp chắc chắn không thể lỗi vì cạn tài nguyên.

Đó là giá trị báo rằng queue không có chính sách giới hạn nội tại kiểu bounded buffer.

### 2.2. Đặt initialCapacity rất lớn cũng không tạo giới hạn

Đặt trước dung lượng lớn có thể ảnh hưởng việc cấp phát ban đầu và số lần mở rộng về sau. Nó không cấm queue phát triển vượt mức đó.

Nếu cần một ngân sách công việc, phải có cơ chế tiếp nhận riêng. Không dùng cấu hình lưu trữ ban đầu thay cho một quy tắc nghiệp vụ.

## 3. put và timed offer không làm producer chờ vì đầy

| Phương thức trên PriorityBlockingQueue | Ý nghĩa liên quan sức chứa |
|---|---|
| `offer(item)` | Không trả `false` vì hết chỗ theo một giới hạn capacity nội tại. |
| `add(item)` | Thêm theo hợp đồng của lớp; không dùng làm phép kiểm tra một mức tối đa do constructor đặt ra. |
| `put(item)` | Không chờ chỗ trống do queue đầy. |
| `offer(item, timeout, unit)` | Không đợi sức chứa; tham số thời hạn không tạo cơ chế tiếp nhận có giới hạn. |
| `take()` | Có thể chờ vì queue rỗng. |
| `poll(timeout, unit)` | Có thể chờ phần tử khi rỗng, rồi trả `null` nếu hết thời gian mà chưa có. |

Javadoc nêu rõ `put` không chặn do sức chứa và timed `offer` không chặn hoặc trả `false` theo điều kiện đầy; tham số thời gian của timed offer bị bỏ qua ở lớp này. [Java 17: put và timed offer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html#offer(E,long,java.util.concurrent.TimeUnit))

Cần hiểu “không chờ vì đầy” đúng phạm vi. Lớp vẫn phải phối hợp truy cập nội bộ, so sánh và có thể cấp phát/mở rộng mảng. Không suy rằng một lần thêm luôn hoàn tất trong một khoảng thời gian cố định, hoàn toàn không có tranh chấp hay không có chi phí cấp phát.

Mã nguồn OpenJDK 17 cho thấy `put` và timed `offer` chuyển sang đường thêm của `offer`, còn đường này vẫn dùng khóa nội bộ và xử lý tăng vùng chứa. [OpenJDK 17: PriorityBlockingQueue](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/concurrent/PriorityBlockingQueue.java)

### 3.1. Đổi ArrayBlockingQueue sang PriorityBlockingQueue thay đổi cả điều tiết tải

Một hệ thống đang dựa vào `ArrayBlockingQueue.put` để làm chậm producer sẽ mất chính cơ chế chờ sức chứa đó nếu chỉ thay bằng PriorityBlockingQueue.

Thay đổi có thể vẫn biên dịch vì hai lớp cùng là `BlockingQueue`. Nhưng hợp đồng sức chứa khác nhau. Kiểm tra tương thích kiểu chưa đủ để chứng minh tương thích hành vi.

### 3.2. Không chờ đầy không có nghĩa chấp nhận mọi đầu vào

PriorityBlockingQueue không chấp nhận `null`. Nếu dùng thứ tự tự nhiên, phần tử phải có khả năng so sánh phù hợp; dữ liệu không so sánh được có thể gây `ClassCastException`.

Một comparator sai, dữ liệu không hợp lệ hoặc cạn tài nguyên vẫn là các vấn đề riêng. Đừng gộp mọi thất bại thành kết quả quá tải theo capacity.

## 4. Ưu tiên là thứ tự lấy trong tập đang chờ

Priority queue chọn phần tử nhỏ nhất theo thứ tự đã quy định. Nếu muốn số `1` biểu diễn khẩn hơn số `5`, comparator tăng dần theo trường priority là phù hợp.

Ví dụ kiểu dữ liệu bất biến và comparator:

~~~java
record Work(String name, int priority, long sequence) {}

java.util.Comparator<Work> order =
        java.util.Comparator.comparingInt(Work::priority)
                            .thenComparingLong(Work::sequence);
~~~

Đây là đoạn minh họa dùng cú pháp Java 17 trong phạm vi khai báo phù hợp. Comparator xét priority trước, sequence sau. Các hàm tạo comparator giúp tránh cách trừ hai số có thể bị tràn khi so sánh. [Java 17: Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)

### 4.1. Ưu tiên bằng nhau không tự có FIFO

PriorityBlockingQueue không bảo đảm thứ tự giữa các phần tử bằng nhau về ưu tiên. Nếu cần thứ tự phụ, hãy đưa nó vào comparator.

Một số thứ tự được cấp lúc tạo yêu cầu có thể làm tiêu chí phụ. Tuy nhiên, trong môi trường nhiều luồng, thứ tự cấp số và thứ tự thực sự được nhận vào queue có thể khác nhau. Phải định nghĩa “đến trước” nghĩa là tạo trước, bắt đầu gọi trước hay đã được tiếp nhận trước.

Ví dụ trong chương trình dùng sequence đã được gán rõ, nên kiểm chứng thứ tự theo metadata đó; nó không tuyên bố tự khôi phục mọi khái niệm FIFO ngoài hệ thống.

### 4.2. Phần tử đang chạy không còn bị queue sắp lại

Một công việc ít khẩn đã được consumer lấy ra có thể đang chạy khi việc khẩn hơn được đưa vào. Queue ưu tiên không tự dừng hoặc lấy lại công việc đang chạy.

Với nhiều consumer, thứ tự lấy ra cũng không bảo đảm thứ tự hoàn tất. Các công việc có thể tốn thời gian khác nhau hoặc bị ảnh hưởng bởi tài nguyên khác.

### 4.3. Tính ưu tiên không tạo tính công bằng dài hạn

Nếu việc khẩn liên tục xuất hiện, việc ít khẩn có thể đợi rất lâu. Đó là vấn đề đói tài nguyên của chính sách lập lịch, khác với tính an toàn của queue.

Nếu cần giới hạn thời gian chờ theo nhóm, có thể cần ngân sách theo nhóm, luân phiên có trọng số hoặc cơ chế tăng ưu tiên được thiết kế riêng. Không chỉ thêm một trường priority rồi mặc nhiên có mọi bảo đảm công bằng.

### 4.4. Không sửa trường làm thay đổi thứ tự khi phần tử còn nằm trong heap

Queue không tự theo dõi mọi thay đổi trong đối tượng đã được thêm. Nếu sửa priority tại chỗ, heap không mặc nhiên được sắp lại theo giá trị mới.

Ưu tiên giá trị bất biến. Khi cần đổi ưu tiên, phải có giao thức cập nhật rõ, có thể là gỡ rồi thêm lại; riêng hai lời gọi đó không tự là một giao dịch chung với các bên khác.

Tương tự, comparator phụ thuộc vào dữ liệu ngoài đang thay đổi, chẳng hạn thời gian hiện tại, không tự làm heap tái cân bằng liên tục. Cần thiết kế thuật toán lập lịch tương ứng.

## 5. Duyệt khác với lấy theo ưu tiên

Một heap chỉ bảo đảm cách chọn phần tử đứng đầu; mảng bên trong không cần được sắp tăng hoàn toàn.

Vì vậy không dùng `iterator`, `for-each`, `toString` hoặc `toArray` để suy rằng mọi phần tử đang xuất hiện theo thứ tự ưu tiên. PriorityBlockingQueue không hứa thứ tự duyệt đó.

Nếu chỉ muốn xem hoặc xuất dữ liệu có thứ tự:

1. Lấy một bản dữ liệu riêng theo API phù hợp.
2. Sắp bản đó bằng đúng comparator.
3. Hiểu rằng các cập nhật sau có thể khiến bản xem khác nguồn hiện tại.

Nếu muốn **lấy ra để xử lý**, dùng `take`/`poll` theo hợp đồng hoặc `drainTo` để lấy một nhóm theo thứ tự ưu tiên. `drainTo` làm thay đổi queue, không phải phép xem không tác động.

PriorityQueue thông thường cũng có sự khác nhau giữa thứ tự đầu heap và thứ tự iterator, nhưng nó không cung cấp đồng bộ cho các cập nhật cạnh tranh như PriorityBlockingQueue. [Java 17: PriorityQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)

Không thay PriorityBlockingQueue bằng PriorityQueue rồi chỉ dựa vào việc chương trình vẫn “lấy đúng số nhỏ nhất” trong một phép thử một luồng.

## 6. Giới hạn thực tế đến từ bộ nhớ và công việc chưa hoàn tất

Queue có thể mở rộng, nhưng mỗi phần tử vẫn giữ dữ liệu:

- Tham chiếu trong cấu trúc lưu trữ.
- Payload mà phần tử dẫn tới.
- Dữ liệu gián tiếp như mảng, chuỗi, ngữ cảnh yêu cầu hoặc đối tượng tác vụ.
- Công việc đang được tạo ở producer trước khi tiếp nhận.
- Công việc đã lấy ra nhưng đang chạy.

Nếu tốc độ đưa vào vượt tốc độ xử lý đủ lâu, số việc chờ có thể tăng đến khi độ trễ hoặc bộ nhớ không còn chấp nhận được.

Ưu tiên chỉ chọn thứ tự phục vụ trong phần đã nhận. Nó không làm tổng khối lượng xử lý giảm đi và không tự ngăn việc ít khẩn tích lũy.

### 6.1. Số phần tử không bằng số byte

Một giới hạn `N` phần tử cũng chưa trực tiếp giới hạn byte nếu kích thước payload chênh lệch lớn. Cần xác định ngân sách phù hợp với dữ liệu.

Có thể giới hạn số việc, tổng trọng lượng dữ liệu ước tính, số phiên đang hoạt động hoặc một tổ hợp điều kiện. Cách đếm phải gắn với tài nguyên cần bảo vệ.

### 6.2. OutOfMemoryError không phải chính sách tiếp nhận

Không đợi đến lúc cấp phát thất bại để coi đó là tín hiệu “queue đầy” thông thường. Khi ấy nhiều phần khác của tiến trình cũng có thể không còn tài nguyên để hoạt động hoặc phản hồi.

Hãy đặt giới hạn chủ động theo ngân sách bộ nhớ, độ trễ và khả năng xử lý. Ví dụ trong bài chỉ thêm một lượng nhỏ để chứng minh initialCapacity không phải giới hạn; nó không thử làm cạn bộ nhớ.

### 6.3. Chọn thời điểm giải phóng ngân sách

Có ít nhất hai phạm vi khác nhau:

- **Giới hạn số việc chờ:** trả ngân sách khi lấy phần tử khỏi queue.
- **Giới hạn số việc đã nhận nhưng chưa xong:** giữ ngân sách qua cả xử lý, chỉ trả khi hoàn tất hoặc hủy theo giao thức.

Một semaphore có thể tham gia cả hai mô hình, nhưng vị trí `release` khác nhau. Trả lúc `take` rồi tuyên bố đã giới hạn toàn bộ việc chưa hoàn tất là sai phạm vi.

## 7. Một ví dụ giới hạn tiếp nhận bằng Semaphore

Ý tưởng là lấy một giấy phép trước khi đưa vào PriorityBlockingQueue và trả lại khi phần tử đã bị lấy ra.

`Semaphore` quản lý số giấy phép bằng các thao tác lấy/trả; nó tách việc quản lý ngân sách khỏi cách queue sắp thứ tự phần tử. [Java 17: Semaphore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html)

Quy tắc của ví dụ:

~~~text
Trước khi nhận:
    phải lấy được một giấy phép

Nếu thêm không thành công:
    trả lại giấy phép vừa giữ

Khi poll/take thực sự lấy một phần tử:
    trả lại đúng một giấy phép

Nếu poll thấy rỗng:
    không trả giấy phép
~~~

Phần tử vừa được một producer giữ giấy phép nhưng chưa công bố vào queue cũng chiếm ngân sách. Nhờ vậy hai producer không cùng tiêu thụ một chỗ trống chỉ vì cùng thấy `size` còn nhỏ.

### 7.1. Giữ queue bên trong kín

Không để bên ngoài lấy trực tiếp queue rồi gọi `offer` mà bỏ qua việc lấy giấy phép. Không cho phép một đường `clear`/`remove`/`drainTo` tự ý lấy phần tử mà quên trả ngân sách.

Ví dụ chỉ cung cấp một số phương thức đã được nối với giấy phép. Nó **không triển khai toàn bộ giao diện BlockingQueue**, nên không phải lớp thay thế dùng được ở mọi nơi đòi một BlockingQueue.

Nếu mở rộng API, mỗi đường thêm, lấy, hủy và xử lý lỗi đều phải duy trì cùng bất biến.

### 7.2. Không đợi trong lúc giữ khóa mà bên lấy cần

Semaphore có thể đợi giấy phép. Việc đợi không được giữ một khóa ngoài đang ngăn consumer lấy phần tử/trả giấy phép.

Giấy phép là ngân sách, không phải một lối tắt thay thế mọi đồng bộ của cấu trúc dữ liệu. Queue vẫn dùng hợp đồng đồng thời của chính nó.

## 8. Chương trình chạy độc lập

Lưu tệp `PriorityCapacityDemo.java`. Chương trình dùng API Java 17, đã được biên dịch với `javac --release 17` và chạy trên OpenJDK Zulu 24.0.2. Không coi đây là bằng chứng đã chạy trên JVM 17.

Phần `PriorityBuffer` là ví dụ giới hạn **phần đang chờ cùng các chỗ đã được đặt nhưng chưa công bố**, không giới hạn mọi công việc đang thực thi. Nó dùng comparator rõ ràng và không lộ queue nền.

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class PriorityCapacityDemo {
    record Work(String name, int priority, long sequence) {}

    static final Comparator<Work> ORDER =
            Comparator.comparingInt(Work::priority)
                      .thenComparingLong(Work::sequence);

    static final class PriorityBuffer<E> {
        private final PriorityBlockingQueue<E> queue;
        private final Semaphore free;

        PriorityBuffer(int capacity, Comparator<? super E> comparator) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            queue = new PriorityBlockingQueue<>(Math.min(capacity, 11),
                                                Objects.requireNonNull(comparator));
            free = new Semaphore(capacity);
        }

        private void publishReserved(E item) {
            boolean inserted = false;
            try {
                inserted = queue.offer(item);
                if (!inserted) {
                    throw new AssertionError("Unexpected priority queue rejection");
                }
            } finally {
                if (!inserted) {
                    free.release();
                }
            }
        }

        boolean offer(E item) {
            Objects.requireNonNull(item);
            if (!free.tryAcquire()) {
                return false;
            }
            publishReserved(item);
            return true;
        }

        boolean offer(E item, long timeout, TimeUnit unit)
                throws InterruptedException {
            Objects.requireNonNull(item);
            if (!free.tryAcquire(timeout, unit)) {
                return false;
            }
            publishReserved(item);
            return true;
        }

        void put(E item) throws InterruptedException {
            Objects.requireNonNull(item);
            free.acquire();
            publishReserved(item);
        }

        E poll() {
            E item = queue.poll();
            if (item != null) {
                free.release();
            }
            return item;
        }

        E take() throws InterruptedException {
            E item = queue.take();
            free.release();
            return item;
        }
    }

    static List<String> names(List<Work> work) {
        return work.stream().map(Work::name).toList();
    }

    static void await(CountDownLatch event) throws InterruptedException {
        if (!event.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Event timed out");
        }
    }

    static void finish(Thread thread, AtomicReference<Throwable> problem)
            throws InterruptedException {
        thread.join(5000);
        if (thread.isAlive()) {
            thread.interrupt();
            throw new AssertionError("Thread timed out");
        }
        if (problem.get() != null) {
            throw new AssertionError("Thread failed", problem.get());
        }
    }

    public static void main(String[] args) throws Exception {
        PriorityBlockingQueue<Integer> raw = new PriorityBlockingQueue<>(1);
        raw.offer(5);
        raw.offer(2);
        raw.offer(8);
        raw.offer(1);
        raw.put(3);
        System.out.println("timed-offer-zero = "
                + raw.offer(4, 0, TimeUnit.NANOSECONDS));
        System.out.println("initial-one-current-size = " + raw.size());
        System.out.println("raw-remainingCapacity = " + raw.remainingCapacity());
        List<Integer> rawOrder = new ArrayList<>();
        raw.drainTo(rawOrder);
        System.out.println("raw-priority-order = " + rawOrder);

        PriorityBlockingQueue<Work> scheduled = new PriorityBlockingQueue<>(1, ORDER);
        scheduled.add(new Work("low", 5, 3));
        scheduled.add(new Work("same-later", 1, 2));
        scheduled.add(new Work("same-earlier", 1, 1));
        List<Work> display = new ArrayList<>(scheduled);
        display.sort(ORDER);
        System.out.println("sorted-display = " + names(display));
        List<Work> drained = new ArrayList<>();
        scheduled.drainTo(drained);
        System.out.println("drained-order = " + names(drained));
        System.out.println("scheduled-size-after-drain = " + scheduled.size());

        PriorityBlockingQueue<String> empty = new PriorityBlockingQueue<>();
        CountDownLatch beforeTake = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<Throwable> takeProblem = new AtomicReference<>();
        Thread taker = new Thread(() -> {
            try {
                beforeTake.countDown();
                received.set(empty.take());
            } catch (Throwable ex) {
                takeProblem.set(ex);
            }
        }, "empty-taker");
        taker.setDaemon(true);
        taker.start();
        await(beforeTake);
        System.out.println("take-received-before-offer = " + (received.get() != null));
        empty.offer("ready");
        finish(taker, takeProblem);
        System.out.println("take-received-after-offer = " + received.get());

        PriorityBuffer<Work> bounded = new PriorityBuffer<>(2, ORDER);
        System.out.println("bounded-offer-low = "
                + bounded.offer(new Work("low", 5, 1)));
        System.out.println("bounded-offer-high = "
                + bounded.offer(new Work("high", 1, 2)));
        System.out.println("bounded-offer-urgent-full = "
                + bounded.offer(new Work("urgent", 0, 3)));
        System.out.println("bounded-timed-offer-full = "
                + bounded.offer(new Work("later", 2, 4), 0, TimeUnit.NANOSECONDS));
        System.out.println("bounded-first = " + bounded.take().name());
        System.out.println("bounded-offer-urgent-after-space = "
                + bounded.offer(new Work("urgent", 0, 3)));
        System.out.println("bounded-next = " + bounded.take().name());
        System.out.println("bounded-last = " + bounded.take().name());
        System.out.println("bounded-poll-empty = " + bounded.poll());

        PriorityBuffer<Integer> waiting = new PriorityBuffer<>(1, Integer::compare);
        waiting.put(9);
        CountDownLatch beforePut = new CountDownLatch(1);
        CountDownLatch afterPut = new CountDownLatch(1);
        AtomicReference<Throwable> putProblem = new AtomicReference<>();
        Thread putter = new Thread(() -> {
            try {
                beforePut.countDown();
                waiting.put(1);
                afterPut.countDown();
            } catch (Throwable ex) {
                putProblem.set(ex);
            }
        }, "bounded-putter");
        putter.setDaemon(true);
        putter.start();
        await(beforePut);
        System.out.println("bounded-put-returned-before-space = "
                + (afterPut.getCount() == 0));
        System.out.println("bounded-existing-item = " + waiting.take());
        await(afterPut);
        finish(putter, putProblem);
        System.out.println("bounded-new-item = " + waiting.take());

        PriorityBuffer<Integer> cancelled = new PriorityBuffer<>(1, Integer::compare);
        cancelled.put(7);
        CountDownLatch attempting = new CountDownLatch(1);
        AtomicBoolean wasInterrupted = new AtomicBoolean();
        AtomicReference<Throwable> cancelProblem = new AtomicReference<>();
        Thread cancelledPutter = new Thread(() -> {
            try {
                attempting.countDown();
                cancelled.put(1);
            } catch (InterruptedException ex) {
                wasInterrupted.set(true);
                Thread.currentThread().interrupt();
            } catch (Throwable ex) {
                cancelProblem.set(ex);
            }
        }, "cancelled-putter");
        cancelledPutter.setDaemon(true);
        cancelledPutter.start();
        await(attempting);
        cancelledPutter.interrupt();
        finish(cancelledPutter, cancelProblem);
        System.out.println("bounded-cancelled = " + wasInterrupted.get());
        System.out.println("bounded-offer-while-held = " + cancelled.offer(2));
        System.out.println("bounded-held-item = " + cancelled.poll());
        System.out.println("bounded-offer-after-take = " + cancelled.offer(2));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 PriorityCapacityDemo.java
java PriorityCapacityDemo
~~~

Stdout đã kiểm chứng:

~~~text
timed-offer-zero = true
initial-one-current-size = 6
raw-remainingCapacity = 2147483647
raw-priority-order = [1, 2, 3, 4, 5, 8]
sorted-display = [same-earlier, same-later, low]
drained-order = [same-earlier, same-later, low]
scheduled-size-after-drain = 0
take-received-before-offer = false
take-received-after-offer = ready
bounded-offer-low = true
bounded-offer-high = true
bounded-offer-urgent-full = false
bounded-timed-offer-full = false
bounded-first = high
bounded-offer-urgent-after-space = true
bounded-next = urgent
bounded-last = low
bounded-poll-empty = null
bounded-put-returned-before-space = false
bounded-existing-item = 9
bounded-new-item = 1
bounded-cancelled = true
bounded-offer-while-held = false
bounded-held-item = 7
bounded-offer-after-take = true
~~~

Các latch đặt thứ tự của phép thử, không dùng `Thread.sleep` để suy đoán trạng thái chờ. Các thời hạn năm giây chỉ giúp phép kiểm chứng báo lỗi thay vì đợi mãi.

## 9. Đọc kết quả

### 9.1. Dung lượng khởi tạo 1 vẫn chứa 6 phần tử

~~~text
timed-offer-zero = true
initial-one-current-size = 6
raw-remainingCapacity = 2147483647
~~~

Queue nhận bốn lần offer, một lần put và một timed offer với thời hạn `0`. Không có mức tối đa `1` do constructor thiết lập. Thời hạn `0` của offer không từ chối vì một “capacity 1” tưởng tượng.

Dòng `remainingCapacity` là `Integer.MAX_VALUE` theo hợp đồng, không phải kết quả đo bộ nhớ trống của máy.

### 9.2. Thứ tự đầu ra đến từ thao tác lấy

~~~text
raw-priority-order = [1, 2, 3, 4, 5, 8]
~~~

`drainTo` lấy các phần tử ra theo ưu tiên khi không có bên khác cập nhật trong giai đoạn kiểm chứng.

Với các `Work`, comparator dùng priority trước, sequence sau:

~~~text
sorted-display = [same-earlier, same-later, low]
drained-order = [same-earlier, same-later, low]
~~~

Bản xem được sắp xếp riêng; bản drain được lấy ra từ queue. Dữ liệu giống nhau trong thí nghiệm yên không có nghĩa hai thao tác có cùng tác dụng. Sau drain, queue rỗng.

### 9.3. Phần Blocking thể hiện ở take khi rỗng

~~~text
take-received-before-offer = false
take-received-after-offer = ready
~~~

Latch báo luồng nhận đã tới đoạn gọi `take`. Khi chưa có phần tử và chưa có bên offer, nó chưa thể nhận được gì. Sau khi `ready` được thêm, lần take hoàn tất.

Không cần dùng Thread state để chứng minh một đợt chờ nội bộ cụ thể; điều cần kiểm chứng là phần tử chỉ được trả sau khi có dữ liệu.

### 9.4. Giới hạn ở wrapper khác với giới hạn của queue nền

~~~text
bounded-offer-low = true
bounded-offer-high = true
bounded-offer-urgent-full = false
bounded-timed-offer-full = false
~~~

Wrapper có hai giấy phép. Hai phần tử đã chiếm ngân sách nên yêu cầu mới bị từ chối hoặc không lấy được giấy phép trong thời hạn `0`.

Đây là `false` do quy tắc tiếp nhận của wrapper, không phải do PriorityBlockingQueue nền tự đạt initialCapacity.

### 9.5. Việc cực khẩn vẫn có thể bị từ chối khi chưa được nhận

`urgent` có priority `0`, cao hơn theo quy ước của ví dụ, nhưng vẫn bị từ chối khi hai chỗ đều đã được dùng.

Sau khi lấy `high` ra, giấy phép được trả; `urgent` được nhận và trở thành phần tử tiếp theo:

~~~text
bounded-first = high
bounded-offer-urgent-after-space = true
bounded-next = urgent
bounded-last = low
~~~

Điều này tách **chính sách được vào hệ thống** với **chính sách lấy ra**. Ưu tiên không tự cho phép một yêu cầu vượt ngân sách hoặc đẩy một phần tử cũ ra ngoài.

### 9.6. put của wrapper có thể chờ, put của PBQ nền thì không chờ sức chứa

Wrapper sức chứa một đã có `9`. Lần put `1` chưa hoàn tất trước khi có chỗ. Consumer vẫn lấy `9` vì `1` chưa được tiếp nhận vào queue:

~~~text
bounded-put-returned-before-space = false
bounded-existing-item = 9
bounded-new-item = 1
~~~

Không được suy rằng producer có một việc khẩn đang chờ giấy phép thì việc ấy đã tham gia thứ tự heap. Chỉ các phần tử đã được nhận mới được queue chọn.

### 9.7. Hủy trước khi có giấy phép không trả thừa ngân sách

Trong phần hủy, một chỗ đang bị `7` chiếm. Producer chờ giấy phép cho `1` và bị ngắt.

~~~text
bounded-cancelled = true
bounded-offer-while-held = false
bounded-held-item = 7
bounded-offer-after-take = true
~~~

Lần chờ bị hủy chưa giữ được giấy phép nên không được gọi release như thể đã lấy được. Khi `7` thực sự bị lấy ra, đúng một giấy phép được trả và lần offer tiếp theo thành công.

## 10. Lập luận về bất biến của ví dụ

Với capacity `C`, mỗi phần tử nằm trong queue phải được bảo chứng bởi một giấy phép đã lấy và chưa trả. Một producer chưa lấy được giấy phép không thể gọi đường công bố.

Các giấy phép đang dùng có thể tương ứng với:

- Phần tử đã công bố và còn trong queue.
- Chỗ producer đã giữ nhưng chưa thêm xong.
- Phần tử vừa bị lấy khỏi queue nhưng thao tác trả giấy phép chưa thực hiện xong.

Do đó số phần tử thực trong queue không vượt `C` nếu mọi đường truy cập đều đi qua wrapper và comparator/phần tử tuân thủ hợp đồng.

### 10.1. Nhánh thêm thất bại

Sau khi đã lấy giấy phép, nếu đường thêm không hoàn tất thành công, `finally` trả lại giấy phép. Điều này tránh giữ vĩnh viễn một đơn vị ngân sách cho việc chưa được nhận.

Comparator trong ví dụ là phép so sánh ngắn, ổn định trên các thành phần bất biến. Không xây giao thức dựa vào comparator có tác dụng bên ngoài, thay đổi ngẫu nhiên hoặc ném lỗi để điều khiển tiếp nhận.

### 10.2. Nhánh lấy rỗng

`poll` chỉ release khi thực sự trả một phần tử khác `null`. Nếu mỗi lần poll rỗng cũng release, giấy phép có thể tăng vượt capacity và wrapper mất giới hạn.

Đây là lỗi dễ xuất hiện khi dùng `finally` ở sai phạm vi: “phương thức lấy đã được gọi” khác với “một phần tử đã bị lấy”.

### 10.3. Nhánh chờ bị ngắt

`free.acquire` ném `InterruptedException` trước khi có giấy phép thì không cần trả giấy phép. Nếu đã giữ giấy phép và thêm thành công, phần tử đi theo vòng đời tiếp nhận bình thường.

Ví dụ hủy được đặt trong giai đoạn không có giấy phép, nên kết quả xác định. Nó không tuyên bố rằng một lệnh interrupt ở thời điểm bất kỳ luôn thu hồi được một phần tử đã được nhận. Hủy tác vụ và hoàn tác tiếp nhận cần một giao thức riêng.

### 10.4. Timed offer chỉ đặt ngân sách chờ giấy phép

Trong wrapper, tham số thời gian được áp dụng cho `Semaphore.tryAcquire`. Sau khi có giấy phép, đường thêm vẫn cần thực hiện thao tác queue và so sánh.

Không coi đây là một cam kết thời gian thực cho toàn bộ phương thức. Nếu API sản phẩm cần thời hạn tổng nghiêm ngặt, cần định nghĩa phạm vi và xử lý các bước còn lại tương ứng.

### 10.5. Mở rộng API đòi mở rộng chứng minh

Nếu thêm `remove`, `clear`, `drainTo` hoặc một iterator có thể xóa, phải trả ngân sách đúng số phần tử thực sự bị lấy/gỡ và xử lý lỗi nhất quán.

Không đưa trực tiếp queue nền cho một executor hoặc một tiện ích khác rồi mong nó biết trả giấy phép. Lớp minh họa cố ý chỉ có các phương thức đã thể hiện đầy đủ giao thức; mọi tích hợp rộng hơn cần một thiết kế hoàn chỉnh cho giao diện đó.

## 11. Giới hạn tiếp nhận không giải quyết mọi vấn đề lập lịch

### 11.1. Khi đầy toàn việc ít khẩn

Một việc khẩn đến muộn có thể bị từ chối như ví dụ. Nếu yêu cầu là luôn có chỗ cho nhóm khẩn, cần một chính sách khác: dành ngân sách theo lớp, quản lý nhiều hàng đợi hoặc chọn thay thế công việc theo quy tắc rõ ràng.

Không chỉ thêm một lệnh `remove` tùy ý trước offer rồi xem đó là một bộ đệm ưu tiên có giới hạn đúng. Quyết định gỡ ai, nhận ai, hoàn trả ngân sách và báo việc bị loại phải được phối hợp.

### 11.2. Giới hạn số chờ khác với giới hạn chưa xong

Wrapper trả giấy phép khi take/poll lấy phần tử. Nếu có nhiều consumer, phần đang chạy ở ngoài queue vẫn giữ tài nguyên.

Muốn giới hạn tổng việc đã nhận nhưng chưa hoàn tất, cần giữ ngân sách qua quá trình xử lý và có cách xác nhận hoàn tất/hủy đúng một lần. Khi ấy phải xét cả consumer lỗi, thử lại và khôi phục. Đó là một giao thức khác với lớp đang minh họa.

### 11.3. Chờ giấy phép không làm producer khác tự ngừng tạo payload

Một producer có thể đã xây một đối tượng rất lớn rồi mới chờ put. Nếu có rất nhiều producer như vậy, lượng dữ liệu đứng ngoài queue vẫn lớn.

Cần đặt ranh giới ngân sách phù hợp với nơi tài nguyên được tạo và truyền tín hiệu quá tải về nguồn thật. Không chỉ đếm nội dung heap của queue rồi bỏ các đối tượng chờ bên ngoài.

### 11.4. Poison object trong priority queue cần đặc biệt thận trọng

Một dấu dừng cũng chịu comparator. Nếu nó được xem là khẩn nhất, consumer có thể lấy dấu dừng trước các việc thường còn chờ. Nếu nó ít khẩn nhất, các đợt việc mới có thể khiến nó chờ rất lâu.

PriorityBlockingQueue không có close nội tại. Giao thức ngừng nhận, xử lý nốt và dừng consumer cần được thiết kế cùng thứ tự ưu tiên, không sao chép nguyên mẫu dấu dừng của FIFO queue mà bỏ qua comparator. [Java 17: BlockingQueue và shutdown](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)

## 12. Nếu dùng với ThreadPoolExecutor

`ThreadPoolExecutor` thường thử đưa tác vụ vào work queue sau khi đã đạt số luồng lõi. Nếu queue chấp nhận, tác vụ tiếp tục xếp hàng; việc mở thêm đến số luồng tối đa gắn với trường hợp không đưa được vào queue theo thuật toán tiếp nhận. [Java 17: ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)

Vì PriorityBlockingQueue thông thường không trả `false` do đầy, chọn nó không tự khiến pool mở rộng theo backlog đến mức tối đa hoặc kích hoạt từ chối theo một capacity nhỏ do constructor đặt ra.

Ngoài ra, mọi tác vụ thực sự được đặt trong priority queue phải so sánh được bằng quy tắc của queue. Các tầng bọc tác vụ có thể thay đổi kiểu đối tượng được đưa vào, nên thiết kế ưu tiên của executor phải kiểm tra đường gửi cụ thể.

Ví dụ `PriorityBuffer` ở trên không triển khai BlockingQueue và không được trình bày như một work queue có thể truyền thẳng cho constructor executor. Nếu cần tích hợp, phải có một giao diện hoàn chỉnh duy trì giới hạn trên tất cả đường mà executor sử dụng.

Mục đích của ví dụ là chứng minh cách tách **ngân sách tiếp nhận** khỏi **thứ tự heap**, không giả định các thành phần ngoài đã biết giao thức giấy phép.

## 13. Quan sát và kiểm thử giới hạn thực tế

Theo dõi những số liệu trả lời đúng câu hỏi:

- Số phần tử đang chờ và thời gian chờ theo từng nhóm ưu tiên.
- Tốc độ được nhận, bị từ chối, bị hủy và hoàn tất.
- Số producer đang đợi ngân sách.
- Số công việc đang chạy ngoài queue.
- Dung lượng payload được giữ và độ trễ xử lý.
- Khả năng các việc ít khẩn bị chờ kéo dài.

Phép thử cần kiểm tra bất biến trực tiếp:

1. Constructor capacity ban đầu không bị nhầm thành giới hạn tối đa.
2. Wrapper từ chối/chờ đúng lúc hết giấy phép.
3. Poll rỗng và hủy trước khi lấy giấy phép không làm tăng ngân sách.
4. Lấy phần tử thành công trả đúng một giấy phép.
5. Thứ tự lấy chỉ được kiểm tra trên tập phần tử đã thực sự được nhận.
6. Khi cần một thứ tự phụ, nó đã được đưa vào comparator và được mô tả rõ.
7. Nơi xử lý vẫn có thể tiến triển trong khi producer đang chờ.

Không kiểm chứng bằng cách cố làm cạn heap hoặc dự đoán một thời gian ngủ sẽ buộc các luồng chạy theo ý muốn. Các latch trong chương trình tạo các trạng thái kiểm tra rõ, còn việc tăng từ initial capacity `1` lên `6` đã đủ chứng minh điểm hợp đồng cần học.

## 14. Bài tập tự kiểm tra

### Bài 1 — Số 100 trong constructor

`new PriorityBlockingQueue<>(100)` đã có `100` phần tử. Lần offer kế tiếp có buộc phải trả `false` không?

**Đáp án:** Không. `100` là initial capacity, không phải giới hạn tiếp nhận. Lớp có thể tăng lưu trữ và không trả `false` vì một giới hạn sức chứa nội tại như vậy.

### Bài 2 — Thời hạn offer

Vì sao `offer(item, 1, SECONDS)` trên PriorityBlockingQueue không tự tạo backpressure khi backlog tăng?

**Đáp án:** Lớp không chờ chỗ trống vì không có giới hạn capacity theo chính sách; tham số thời gian của dạng offer đó không tạo một ngưỡng tiếp nhận.

### Bài 3 — Integer.MAX_VALUE

`remainingCapacity` trả `Integer.MAX_VALUE`. Có thể xem đó là số payload lớn còn chắc chắn chứa được trên heap không?

**Đáp án:** Không. Đó là giá trị theo hợp đồng queue không bị giới hạn capacity, không phải phép đo bộ nhớ và không phải một lời hứa cấp phát.

### Bài 4 — Duyệt có thứ tự?

Một vòng for-each in dữ liệu không tăng dần, nhưng các lần poll cho phần tử đúng ưu tiên. Queue có vi phạm hợp đồng không?

**Đáp án:** Không. Iterator không được hứa duyệt theo thứ tự ưu tiên. Cần sắp bản xem riêng hoặc lấy ra bằng API của queue tùy mục đích.

### Bài 5 — Việc khẩn đến khi đầy

Wrapper đang chứa đủ các việc ít khẩn, một việc khẩn hơn được offer và bị từ chối. Có mâu thuẫn với ưu tiên không?

**Đáp án:** Không. Quy tắc tiếp nhận và quy tắc lấy ra là hai tầng khác nhau. Muốn dành chỗ cho việc khẩn phải có chính sách ngân sách/lập lịch bổ sung.

### Bài 6 — Poll rỗng

Một wrapper dùng semaphore release trong mọi lần poll, kể cả lúc trả null. Điều gì sai?

**Đáp án:** Nó trả giấy phép dù không giải phóng phần tử nào, làm ngân sách tăng và có thể vượt giới hạn. Chỉ release khi thật sự lấy được phần tử.

### Bài 7 — Đã take nhưng vẫn đang chạy

Wrapper trả giấy phép ngay sau take. Nó có giới hạn toàn bộ số việc chưa hoàn tất không?

**Đáp án:** Không. Nó giới hạn phần đang chờ cùng các chỗ đã được đặt. Công việc đã lấy ra vẫn đang dùng tài nguyên ngoài queue. Muốn giới hạn toàn bộ phải chọn điểm trả ngân sách khác và có giao thức hoàn tất.

### Bài 8 — Dấu dừng khẩn nhất

Một priority queue có nhiều việc thường. Đưa dấu dừng với ưu tiên cao nhất rồi cho consumer thoát khi lấy nó có bảo đảm xử lý hết backlog không?

**Đáp án:** Không. Dấu dừng có thể được lấy trước các việc đang chờ. Cần ngừng nhận và xác định rõ chính sách xử lý nốt/hủy trước khi dừng consumer.

PriorityBlockingQueue mang lại thứ tự chọn việc và khả năng chờ khi rỗng. Giới hạn tài nguyên thực tế phải được thiết kế ở ranh giới tiếp nhận, với ngân sách và điểm giải phóng rõ ràng; chỉ khi hai tầng ấy cùng phù hợp thì bộ lập lịch mới có hành vi quá tải có thể kiểm chứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-queues-006">DelayQueue và hợp đồng Delayed</a>

<details>
<summary>Xem chi tiết</summary>

# DelayQueue và hợp đồng Delayed

## 1. Hiểu đúng: có trong hàng đợi chưa có nghĩa là được lấy ra

Giả sử một yêu cầu gửi lại chỉ được thử sau một khoảng nghỉ. Ta muốn đặt yêu cầu vào nơi lưu trữ ngay, nhưng chỉ giao nó cho phía xử lý khi đã đến hạn. Nếu dùng hàng đợi thông thường, phần tử đứng đầu được lấy ra theo quy tắc của hàng đợi mà không có điều kiện thời gian này. Nếu tự kiểm tra rồi đưa phần tử chưa đến hạn trở lại, ta phải tự giải quyết việc chờ, đánh thức và sắp lại thứ tự khi có yêu cầu đến hạn sớm hơn.

`DelayQueue<E extends Delayed>` biểu diễn một hàng đợi có điều kiện thời gian. Mỗi phần tử tự mô tả khi nào nó đến hạn thông qua `Delayed`. Hàng đợi tìm ứng viên đến hạn sớm nhất, nhưng các thao tác lấy theo hạn chỉ giao ứng viên khi thời gian còn lại của nó đã hết.

Hãy tách ba việc:

1. **Lưu giữ:** phần tử đã được đưa vào hàng đợi.
2. **Đủ điều kiện lấy:** phần tử đã đến hạn.
3. **Thực hiện công việc:** mã của ứng dụng nhận phần tử rồi quyết định làm gì.

`DelayQueue` làm hai việc đầu. Nó không gọi một phương thức xử lý công việc thay cho ứng dụng. Đưa một đối tượng có phương thức `run()` vào hàng đợi cũng không tự làm phương thức ấy chạy.

Ví dụ, ta đưa lần lượt ba phần tử có hạn 30, 10 và 20 vào cùng một hàng đợi. Tại thời điểm 0, hàng đợi lưu ba phần tử, ứng viên đầu là phần tử có hạn 10, nhưng chưa phần tử nào được phép lấy bằng `poll()`. Tại thời điểm 20, phần tử có hạn 10 và 20 đều đã đủ điều kiện; phần tử có hạn 30 vẫn được lưu lại. Thứ tự đưa vào không quyết định thứ tự lấy.

Trong bài này, **đến hạn** là thời gian còn lại bằng 0 hoặc âm. “Hết thời gian trì hoãn” không đồng nghĩa với “dữ liệu vô hiệu cần xóa bỏ”. Ứng dụng có thể lấy phần tử đến hạn để thực hiện một công việc hoàn toàn hợp lệ, chẳng hạn thử lại một thao tác.

Điểm dễ gây nhầm nhất là hai câu sau có thể cùng đúng:

- `queue.isEmpty()` trả về `false`.
- `queue.poll()` trả về `null`.

Không có mâu thuẫn: hàng đợi có dữ liệu, nhưng chưa có dữ liệu đủ điều kiện giao qua thao tác này. Vì thế, không dùng `size() > 0` làm bằng chứng rằng một lần gọi `take()` tiếp theo sẽ trả về ngay.

**Mục tiêu học:** sau bài này, bạn cần giải thích được hai phương thức của `Delayed`, viết một phần tử có hạn ổn định, phân biệt quan sát với lấy theo hạn, và xử lý đúng việc chờ/ngắt/thử lại. Phần đầu giải thích hợp đồng; ba chương trình phía sau lần lượt kiểm chứng ngữ nghĩa, cơ chế chờ và một luồng thử lại có giới hạn.

## 2. Delayed nối dữ liệu của ứng dụng với quy tắc thời gian

### 2.1. Hai phương thức cùng nói về một hạn

`Delayed` kế thừa `Comparable<Delayed>`. Khi tạo loại phần tử dùng cho `DelayQueue`, ta phải cung cấp cả hai phần:

| Phương thức | Câu hỏi nó trả lời | Điều cần giữ nhất quán |
| --- | --- | --- |
| `getDelay(TimeUnit unit)` | Từ bây giờ đến hạn còn bao nhiêu, theo đơn vị được yêu cầu? | Giá trị giảm theo thời gian và bằng 0 hoặc âm khi đến hạn. |
| `compareTo(Delayed other)` | Phần tử nào có hạn sớm hơn? | Thứ tự phải phù hợp với thứ tự thời gian còn lại. |

Đây là hợp đồng được nêu trong [API Delayed của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Delayed.html). Chỉ viết một `getDelay()` trông hợp lý chưa đủ: nếu `compareTo()` xếp sai ứng viên đầu, hành vi của hàng đợi cũng sai.

Ta phân biệt:

- **Khoảng trì hoãn ban đầu:** ví dụ 5 giây kể từ lúc tạo.
- **Hạn:** mốc cố định thu được từ thời điểm tạo cộng khoảng trì hoãn.
- **Thời gian còn lại:** hạn trừ thời điểm hiện tại.

Với một phần tử thông thường trong bài, hạn là dữ liệu bất biến; thời gian còn lại được tính lại mỗi lần gọi. Thay đổi của đồng hồ làm thời gian còn lại giảm, không cần sửa trường hạn của phần tử.

Một cách viết sai là lưu `delay = 5` rồi luôn trả về đúng 5 giây. Khi hàng đợi kiểm tra lần sau, phần tử vẫn tuyên bố “còn 5 giây”; thời gian đã trôi qua không được phản ánh. Cách viết sai khác là trừ thời điểm hiện tại ngay trong quá trình tạo, lưu kết quả còn lại một lần, rồi trả lại số đã đóng băng đó mãi.

Quy tắc nên dùng là: **ghi nhớ hạn; tính lại phần còn lại**.

### 2.2. Đừng để thứ tự ưu tiên khác thứ tự đến hạn

Giả sử A đến hạn sau một giờ còn B đã đến hạn. Nếu `compareTo()` xếp theo tên và đặt A trước B, một phần tử đã sẵn sàng có thể bị che sau ứng viên chưa sẵn sàng. Không thể sửa lỗi này bằng cách chỉ làm `getDelay()` chính xác.

Trong các chương trình bên dưới, tất cả phần tử trong một hàng đợi dùng cùng một miền thời gian và so sánh trực tiếp các hạn bất biến:

```java
// Trích đoạn ý tưởng; không phải chương trình chạy độc lập.
return Long.compare(deadline, item.deadline);
```

Đừng thay bằng `(int) (deadline - item.deadline)`. Hiệu của hai số `long` có thể vượt miền `int`; phép ép kiểu làm mất thông tin, thậm chí đảo dấu. Ví dụ chênh lệch vài giây khi biểu diễn bằng nano giây đã có thể vượt `Integer.MAX_VALUE`. Dùng `Long.compare` tránh phép trừ trong bản thân phép so sánh, nhưng vẫn cần thiết kế miền hạn sao cho giá trị lưu có ý nghĩa: nó không tự sửa một phép tính hạn đã tràn trước đó.

Cũng không nên gọi `this.getDelay(...)` và `other.getDelay(...)` ở hai thời điểm khác nhau rồi trừ chúng để so sánh. Bạn đang thêm sự dịch chuyển của đồng hồ vào phép so sánh. Khi đã có cùng hệ quy chiếu, so sánh các hạn cố định đơn giản và ổn định hơn.

Các lớp ví dụ chỉ chấp nhận đúng loại phần tử của mình. Lớp dùng đồng hồ kiểm soát còn yêu cầu cùng một đối tượng đồng hồ. Đây là một ràng buộc của ví dụ để tránh trộn các hệ quy chiếu. Một lớp `Delayed` bất kỳ không mặc nhiên có thể so sánh có nghĩa với mọi lớp `Delayed` khác.

Hai phần tử có cùng hạn có thể so sánh bằng 0 dù chúng là hai công việc khác nhau. Đừng suy ra rằng hàng đợi sẽ gộp chúng hoặc giữ thứ tự đưa vào giữa chúng. Nếu bài toán cần một thứ tự ổn định khi trùng hạn, hãy bổ sung một tiêu chí phụ ổn định, chẳng hạn số thứ tự tăng dần, và kiểm soát vòng đời của số này. Bài hiện tại không đặt yêu cầu ấy nên không thêm một cơ chế phụ vào mã.

### 2.3. Đơn vị đo là một phần của hợp đồng

Tham số `unit` không phải lời gợi ý. Nếu phía gọi yêu cầu nano giây mà phần tử trả về số giây chưa chuyển đổi, điều kiện đến hạn bị tính sai rất lớn. Cách viết quen thuộc là lưu hạn theo một đơn vị chung rồi chuyển thời gian còn lại:

```java
// Trích đoạn: remaining được biểu diễn theo nano giây.
return unit.convert(remaining, TimeUnit.NANOSECONDS);
```

[API TimeUnit](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeUnit.html) phân biệt chuyển đổi đơn vị với độ chính xác đo thời gian. Khi đổi từ đơn vị nhỏ sang lớn, phần lẻ bị bỏ: 1 nano giây trở thành 0 mili giây. Vì vậy `getDelay(MILLISECONDS) == 0` chưa chứng minh phần tử đã đến hạn nếu nó vẫn còn 1 nano giây.

`DelayQueue` dùng dấu của thời gian còn lại theo nano giây để xác định điều kiện này. Chương trình đầu cố ý giữ lại đúng 1 nano giây để bạn nhìn thấy sự khác biệt mà không phụ thuộc tốc độ máy.

Ở chiều chuyển sang đơn vị nhỏ, kết quả quá lớn được `TimeUnit` chặn tại biên của `long`. Đây không phải phép xác thực đầu vào thay cho ứng dụng. Nếu chương trình chỉ hỗ trợ các khoảng ngắn, hãy kiểm tra miền sau chuyển đổi và từ chối khoảng ngoài thiết kế. Các ví dụ dùng đồng hồ thật bên dưới giới hạn khoảng trì hoãn trong cộng/trừ một ngày.

## 3. Chọn đồng hồ và giữ hạn bất biến

### 3.1. Khoảng chờ trong tiến trình khác một ngày giờ trên lịch

Một yêu cầu “đợi thêm 100 mili giây” là bài toán đo thời gian trôi qua. `System.nanoTime()` phù hợp với mục đích đó. Giá trị của nó không phải thời điểm kể từ một mốc lịch chuẩn, có thể âm và chỉ có ý nghĩa khi so sánh trong cùng miền đo của JVM.

Ngược lại, “chạy vào 09:00 ngày mai theo múi giờ của người dùng” là yêu cầu về lịch. Muốn đưa yêu cầu ấy vào một cơ chế chờ tương đối, bạn phải quyết định cách quy đổi và cách phản ứng với việc chỉnh đồng hồ hoặc khởi động lại. Bài này không biến `nanoTime()` thành một lịch lưu bền vững.

Không lưu một giá trị hạn `nanoTime()` vào cơ sở dữ liệu rồi coi nó là thời điểm có thể dùng lại sau khi JVM khởi động lại. Nếu cần lưu lịch, hãy lưu ý nghĩa thời gian thích hợp của bài toán, rồi thiết lập lại khoảng chờ trong tiến trình mới.

[API System.nanoTime()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime()) cũng lưu ý miền hiệu thời gian hữu hạn của `long`. Phép đo nano giây không cho ta một trục số vô hạn; các khoảng xấp xỉ 292 năm đã chạm giới hạn của phép biểu diễn hiệu có dấu. Khi kiểm tra một khoảng đã trôi qua, cách viết theo hiệu thời gian thường phù hợp hơn việc giả định một phép cộng hạn luôn không tràn.

### 3.2. Thiết kế ngắn hạn dùng trong các chương trình

Hai chương trình dùng đồng hồ thật bên dưới có một thiết kế có chủ đích:

1. Một `ORIGIN` chung được lấy từ `System.nanoTime()`.
2. `elapsed()` tính khoảng đã trôi qua kể từ gốc ấy.
3. Mỗi phần tử có `deadline = elapsed() + delayNanos`.
4. `getDelay()` tính `deadline - elapsed()`.
5. `compareTo()` so sánh hai `deadline` trong cùng hệ quy chiếu.

`Math.addExact` làm phép cộng báo lỗi khi không thể biểu diễn, thay vì âm thầm cuộn số. Hạn được giữ trong trường `final`. Khoảng trì hoãn bị giới hạn trong cộng/trừ một ngày; chương trình chạy trong thời gian ngắn và không giữ phần tử qua những khoảng cực lớn. Với các giả thiết này, so sánh trực tiếp các độ lệch hạn là rõ ràng.

Đây là thiết kế cho các ví dụ ngắn hạn, không phải chứng minh rằng cộng/trừ hạn theo `long` an toàn với mọi thời gian sống của một hệ thống. `Math.addExact` ở phép cộng không tự bảo vệ mọi phép trừ trong một tiến trình chạy hàng trăm năm. Khi thay đổi miền sử dụng, phải xem lại cả chuyển đơn vị, phép cộng, phép trừ và quy tắc so sánh.

Cho phép khoảng âm trong ví dụ là có ý nghĩa: một phần tử được tạo với hạn ở quá khứ đã đủ điều kiện lấy ngay. “Hạn đã qua” không phải lỗi kiểu dữ liệu. Ứng dụng có thể chọn từ chối nó theo quy tắc nghiệp vụ riêng, nhưng không nên nhầm quy tắc ấy với điều kiện của `Delayed`.

### 3.3. Không sửa hạn khi phần tử còn trong hàng đợi

Bạn có thể khai báo `deadline` là `volatile` rồi sửa nó từ một luồng khác. Điều này chỉ liên quan đến khả năng nhìn thấy giá trị; nó không yêu cầu hàng đợi tự xây lại thứ tự và thay đổi lịch chờ.

Quan sát riêng với [mã nguồn OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/concurrent/DelayQueue.java): `DelayQueue` dùng một `PriorityQueue` bên trong cùng khóa và điều kiện chờ. Việc nhận ứng viên đầu dựa vào cấu trúc ưu tiên ấy. Sửa trực tiếp một khóa sắp xếp của đối tượng đang nằm trong cấu trúc không phải một thao tác cập nhật hàng đợi.

Vì vậy, thiết kế an toàn và dễ giải thích trong bài là: **một lần đưa vào tương ứng một phần tử có hạn bất biến**. Nếu cần lên lịch lại, hãy tạo phần tử mới với hạn mới, kèm quy tắc hủy/phiên bản phù hợp với bài toán. Trường hợp phần tử cũ đã được phía nhận lấy đi là một cuộc đua khác mà thao tác sửa trường không giải quyết được.

## 4. Đọc API theo hai câu hỏi: đang lưu gì, hiện lấy được gì?

Bảng dưới dùng “đến hạn” theo điều kiện nano giây đã giải thích. Nó là bản đồ để đọc các thí nghiệm, không phải lời hứa rằng nhiều thao tác liên tiếp trở thành một giao dịch.

| Nhóm | Thao tác | Cách hiểu cần nhớ |
| --- | --- | --- |
| Đưa vào | `offer(e)`, `put(e)`, `offer(e, timeout, unit)` | Không có giới hạn sức chứa; không chờ một chỗ trống. Tham số thời gian của bản `offer` không tạo thời gian trì hoãn cho phần tử. |
| Quan sát | `peek()`, `element()` | Nhìn ứng viên đầu, kể cả khi chưa đến hạn. `peek()` trả `null` khi không có ứng viên; `element()` báo lỗi khi trống. |
| Lấy ngay | `poll()`, `remove()` không đối số | Chỉ lấy phần tử đến hạn. Khi chưa có phần tử như vậy, `poll()` trả `null`; `remove()` ném `NoSuchElementException`. |
| Lấy có chờ | `take()`, `poll(timeout, unit)` | Chờ phần tử đến hạn; bản có giới hạn thời gian có thể trả `null`. Có thể bị ngắt bằng `InterruptedException`. |
| Kiểm tra lượng lưu | `size()`, `isEmpty()` | Tính cả phần tử chưa đến hạn. |
| Xóa có chủ đích | `remove(Object)`, `clear()` | Có thể xóa phần tử chưa đến hạn; `clear()` xóa toàn bộ phần tử đang lưu. |
| Chuyển phần sẵn sàng | `drainTo(...)` | Chuyển các phần tử đến hạn, có thể giới hạn số lượng bằng biến thể có `maxElements`. |

Nguồn tra cứu cho từng phương thức là [API DelayQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/DelayQueue.html). Với `element()` và `remove()` không đối số, xem thêm các phương thức kế thừa từ [AbstractQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractQueue.html): chúng dựa lần lượt vào `peek()` và `poll()`. Chính sự khác nhau của hai thao tác nền giải thích trường hợp `element()` nhìn thấy phần tử tương lai trong khi `remove()` vẫn báo không có phần tử lấy được.

Ba lưu ý trước khi chạy:

- `offer(e, 10, SECONDS)` không có nghĩa “cho e chờ 10 giây”. Hạn vẫn đến từ chính `e`. Đây là tham số chờ để đưa vào trong giao diện `BlockingQueue`, và `DelayQueue` không cần nó cho sức chứa.
- Không chờ sức chứa không có nghĩa cấu trúc này không dùng khóa hay mọi lời gọi hoàn tất tức thì bất kể cạnh tranh luồng.
- Không cho phép `null`. Giá trị `null` từ `poll()` có ý nghĩa thiếu phần tử đủ điều kiện, không phải một mục dữ liệu bạn đã đưa vào.


## 5. Thí nghiệm thứ nhất: tách đồng hồ khỏi tốc độ chạy của máy

Chương trình này dùng một đồng hồ số do ta điều khiển để quan sát các thao tác **không chờ**. Các số 10, 20, 30 là hạn theo nano giây trên trục thời gian thử nghiệm. Khi đổi đồng hồ từ 0 sang 20, ta không ngủ 20 nano giây; ta chủ động đặt “bây giờ” của các phần tử sang 20.

Nhờ vậy, kết quả không phụ thuộc việc máy mất bao lâu để in một dòng hoặc chạy một lệnh. Ta kiểm chứng quan hệ giữa hạn và thao tác, thay vì cố đo những khoảng quá nhỏ bằng `Thread.sleep()`.

Lưu toàn bộ mã sau vào `DelayQueueSemanticsDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DelayQueueSemanticsDemo {
    public static void main(String[] args) {
        AtomicLong clock = new AtomicLong(0);
        ManualItem early = new ManualItem("early", 10, clock);
        ManualItem middle = new ManualItem("middle", 20, clock);
        ManualItem late = new ManualItem("late", 30, clock);
        DelayQueue<ManualItem> queue = new DelayQueue<>();
        queue.offer(late);
        queue.offer(early);
        queue.offer(middle);

        require(queue.size() == 3 && queue.peek() == early, "initial ordering");
        System.out.println("stored = " + queue.size());
        System.out.println("peek before expiry = " + queue.peek());
        System.out.println("poll before expiry = " + queue.poll());

        clock.set(20);
        System.out.println("poll at time 20 = " + queue.poll());
        List<ManualItem> drained = new ArrayList<>();
        int transferred = queue.drainTo(drained);
        require(transferred == 1 && drained.get(0) == middle, "expired drain");
        System.out.println("drained = " + drained);
        System.out.println("future-only empty = " + queue.isEmpty());
        System.out.println("element with future only = " + queue.element());
        try {
            queue.remove();
            throw new AssertionError("no expired head should be removable");
        } catch (NoSuchElementException ex) {
            System.out.println("remove without argument -> "
                    + ex.getClass().getSimpleName());
        }
        System.out.println("remove future by object = " + queue.remove(late));
        System.out.println("stored after removal = " + queue.size());

        AtomicLong tinyClock = new AtomicLong(0);
        ManualItem tiny = new ManualItem("tiny", 1, tinyClock);
        DelayQueue<ManualItem> tinyQueue = new DelayQueue<>();
        tinyQueue.offer(tiny);
        System.out.println("tiny delay in milliseconds = "
                + tiny.getDelay(TimeUnit.MILLISECONDS));
        System.out.println("tiny delay in nanoseconds = "
                + tiny.getDelay(TimeUnit.NANOSECONDS));
        System.out.println("tiny poll = " + tinyQueue.poll());
    }

    private static final class ManualItem implements Delayed {
        private final String name;
        private final long deadline;
        private final AtomicLong clock;

        private ManualItem(String name, long deadline, AtomicLong clock) {
            this.name = Objects.requireNonNull(name);
            this.deadline = deadline;
            this.clock = Objects.requireNonNull(clock);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long remaining = Math.subtractExact(deadline, clock.get());
            return unit.convert(remaining, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (!(other instanceof ManualItem item) || item.clock != clock) {
                throw new ClassCastException("same item type and clock required");
            }
            return Long.compare(deadline, item.deadline);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all DelayQueueSemanticsDemo.java
java DelayQueueSemanticsDemo
```

Kết quả:

```text
stored = 3
peek before expiry = early
poll before expiry = null
poll at time 20 = early
drained = [middle]
future-only empty = false
element with future only = late
remove without argument -> NoSuchElementException
remove future by object = true
stored after removal = 0
tiny delay in milliseconds = 0
tiny delay in nanoseconds = 1
tiny poll = null
```

### Giải thích từng chuyển trạng thái

Ở đầu chương trình, thứ tự thêm là `late`, `early`, `middle`, nhưng `peek()` nhìn thấy `early`. Điều này kiểm chứng quy tắc xếp theo hạn. `stored = 3` và `poll before expiry = null` cùng xuất hiện vì số lượng đang lưu không phải số lượng đã đến hạn.

Khi `clock.set(20)`, `early` có thời gian còn lại là -10. `poll()` lấy nó trước. `middle` có thời gian còn lại là 0, nên đủ điều kiện cho `drainTo()` tiếp theo. `late` vẫn còn 10 nên không bị chuyển vào danh sách `drained`.

Ở trạng thái chỉ còn `late`, ta quan sát ba khía cạnh của cùng một phần tử:

- Nó vẫn là dữ liệu được lưu, nên `isEmpty()` là `false`.
- Nó là ứng viên đầu, nên `element()` trả nó về.
- Nó chưa đến hạn, nên `remove()` không đối số ném `NoSuchElementException`.

Sau đó `remove(late)` xóa được nó. Đây là thao tác tìm đối tượng cần xóa, không phải thao tác lấy công việc đến hạn tiếp theo. Trong lớp ví dụ, không ghi đè `equals()`, nên việc truyền lại đúng đối tượng `late` làm ý nghĩa xóa rất rõ ràng.

Thí nghiệm `tiny` kiểm chứng đơn vị đo. Cùng một khoảng còn lại là 1 nano giây, đổi sang mili giây cho kết quả 0. Hàng đợi vẫn không giao nó qua `poll()`. Nếu chỉ kiểm tra số mili giây rồi kết luận rằng phần tử đã sẵn sàng, bạn sẽ kết luận sớm hơn điều kiện của hàng đợi.

### Giới hạn của đồng hồ điều khiển

Đừng mở rộng thí nghiệm này bằng cách gọi `take()` rồi chỉ `clock.set(...)` ở luồng khác và kỳ vọng hàng đợi tự thức dậy ngay. Đồng hồ điều khiển nằm trong phần tử; nó không điều khiển bộ chờ thời gian của `DelayQueue`. Đổi một `AtomicLong` không tự gửi tín hiệu đến điều kiện chờ bên trong hàng đợi.

Do đó chương trình chỉ dùng `peek()`, `poll()`, `drainTo()`, các thao tác quan sát và xóa. Nó giúp kiểm chứng điều kiện đến hạn mà không giả làm một bộ lập lịch theo thời gian ảo. Muốn kiểm thử các phương thức chờ, ta chuyển sang đồng hồ thật ở chương trình tiếp theo.

`Math.subtractExact` trong `ManualItem` cũng là một lựa chọn kiểm chứng: các giá trị thử nhỏ, nên không tràn. Nếu thay chúng bằng hai cực của `long`, ngoại lệ cho thấy dữ liệu thử đã vượt thiết kế; nó không chứng minh hàng đợi hỗ trợ mọi cặp hạn bất kỳ.

## 6. Thí nghiệm thứ hai: chờ, có ứng viên sớm hơn và ngắt luồng

Bài toán tiếp theo là phía nhận phải chờ khi chưa có mục đến hạn. Ta đặt một phần tử sau 12 giờ vào hàng đợi, chuẩn bị một lần `take()`, rồi đưa thêm một phần tử đã đến hạn. Phía nhận phải nhận được phần tử mới, không phải cố chờ phần tử 12 giờ chỉ vì nó được thêm trước.

Đây cũng là nơi cần phân biệt:

- **Thời gian chờ được tính từ hạn:** phần tử nào được giao.
- **Giới hạn chờ của lời gọi:** phía gọi chịu chờ tối đa bao lâu trong `poll(timeout, unit)`.
- **Tín hiệu ngắt:** yêu cầu dừng một luồng đang thực hiện thao tác có thể chờ.

Lưu thành `DelayQueueWaitingDemo.java`:

```java
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DelayQueueWaitingDemo {
    public static void main(String[] args) throws Exception {
        DelayQueue<TimedItem> queue = new DelayQueue<>();
        queue.put(new TimedItem("later", 12, TimeUnit.HOURS));
        CountDownLatch enteringTake = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<TimedItem> consumer = pool.submit(() -> {
                enteringTake.countDown();
                return queue.take();
            });
            await(enteringTake);
            queue.offer(new TimedItem("ready", -1, TimeUnit.SECONDS));
            TimedItem received = consumer.get(5, TimeUnit.SECONDS);
            require("ready".equals(received.name), "wrong head");
            require(received.getDelay(TimeUnit.NANOSECONDS) <= 0, "not expired");
            System.out.println("taken = " + received.name);
            System.out.println("next candidate = " + queue.peek().name);

            TimedItem timed = queue.poll(1, TimeUnit.MILLISECONDS);
            require(timed == null && queue.size() == 1, "timed poll");
            System.out.println("timed poll without expired item = " + timed);
            showInterruption(queue);
            queue.clear();
            System.out.println("empty after clear = " + queue.isEmpty());
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static void showInterruption(DelayQueue<TimedItem> queue)
            throws Exception {
        CountDownLatch entering = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread receiver = new Thread(() -> {
            entering.countDown();
            try {
                queue.take();
            } catch (InterruptedException ex) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        });
        try {
            receiver.start();
            await(entering);
            receiver.interrupt();
            await(finished);
            require(interrupted.get(), "take must observe interruption");
            System.out.println("interrupted take observed = " + interrupted.get());
        } finally {
            receiver.interrupt();
            receiver.join(5_000);
            if (receiver.isAlive()) {
                throw new IllegalStateException("receiver did not terminate");
            }
        }
    }

    private static final class TimedItem implements Delayed {
        private static final long ORIGIN = System.nanoTime();
        private static final long MAX_DELAY = TimeUnit.DAYS.toNanos(1);
        private final String name;
        private final long deadline;

        private TimedItem(String name, long delay, TimeUnit unit) {
            this.name = Objects.requireNonNull(name);
            long nanos = Objects.requireNonNull(unit).toNanos(delay);
            if (nanos < -MAX_DELAY || nanos > MAX_DELAY) {
                throw new IllegalArgumentException("delay outside demo range");
            }
            deadline = Math.addExact(elapsed(), nanos);
        }

        private static long elapsed() {
            return System.nanoTime() - ORIGIN;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(deadline - elapsed(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (!(other instanceof TimedItem item)) {
                throw new ClassCastException("TimedItem required");
            }
            return Long.compare(deadline, item.deadline);
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("latch timed out");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all DelayQueueWaitingDemo.java
java DelayQueueWaitingDemo
```

Kết quả:

```text
taken = ready
next candidate = later
timed poll without expired item = null
interrupted take observed = true
empty after clear = true
```

### Vì sao kết quả không cần chờ 12 giờ?

`later` có hạn trong tương lai. `ready` được tạo với khoảng -1 giây, vì vậy đã đến hạn khi được đưa vào. Phép so sánh đặt `ready` trước `later`; lần lấy trả `ready`. Sau đó `peek()` vẫn thấy `later`, xác nhận phần tử tương lai không bị mất.

Quan sát triển khai OpenJDK 17u đã dẫn ở trên: khi `offer()` đưa một ứng viên mới lên đầu, việc chờ được báo hiệu để phía nhận đánh giá lại ứng viên đầu. Đây là lời giải thích về triển khai cụ thể cho hành vi trong bài; hợp đồng học ở mức ứng dụng vẫn là thứ tự hạn và điều kiện đủ hạn.

`enteringTake` được đếm xuống ngay trước `take()`. Latch này phối hợp việc bắt đầu, **không chứng minh** luồng nhận đã ngủ trong `take()` trước khi `ready` được thêm. Có thể phần tử đã được thêm ngay trước lúc luồng nhận đi vào lời gọi. Cả hai cách xen kẽ đều phải dẫn đến kết quả hợp lệ như trên.

Đó là lý do chương trình không tuyên bố đã đo được thời gian đánh thức hoặc ép được một lịch xen kẽ duy nhất. Một kiểm thử đúng không cần biến một tín hiệu “sắp gọi” thành bằng chứng “đang bị chặn”.

### Giới hạn thời gian không đổi hạn của phần tử

Sau lần lấy đầu, hàng đợi chỉ còn `later`. `poll(1, MILLISECONDS)` không làm nó đến hạn sớm hơn. Khi không có mục đến hạn trong khoảng chờ này, kết quả là `null` dù hàng đợi vẫn lưu một phần tử.

Chương trình không kiểm tra một mili giây chính xác bằng đồng hồ ngoài. Độ phân giải hẹn giờ, lịch cấp CPU và tạm dừng của JVM có thể làm thời điểm lời gọi tiếp tục muộn hơn. Điều cần kiểm chứng là không lấy nhầm mục tương lai và không chờ vô hạn.

Các mốc 5 giây dùng cho `Future.get()`, latch và kết thúc luồng là giới hạn phát hiện chương trình không tiến triển trong môi trường chạy bình thường. Chúng không phải một bảo đảm thời gian thực của Java. Nếu máy bị dừng đủ lâu bởi trình gỡ lỗi hoặc hệ điều hành, kiểm thử có thể thất bại do giới hạn này.

### Ngắt là một phần của giao thức dừng

`showInterruption()` tạo một luồng nhận riêng, yêu cầu ngắt nó và kiểm tra rằng `InterruptedException` được quan sát. Ngắt có thể đến trước khi `take()` thực sự chờ hoặc trong lúc nó đang chờ; chương trình không dựa vào việc phân biệt hai trường hợp ấy.

Trong nhánh bắt ngoại lệ, luồng khôi phục cờ ngắt bằng `Thread.currentThread().interrupt()` rồi kết thúc. Việc khôi phục giúp giữ lại tín hiệu cho mã gọi bao quanh trong những thiết kế có lớp gọi bên ngoài. Tại ví dụ này luồng kết thúc ngay, nên tác dụng chính là minh họa cách không nuốt mất yêu cầu ngắt.

Khối `finally` tiếp tục ngắt và `join` có giới hạn để tránh bỏ lại luồng chạy ngoài ý muốn nếu một kiểm tra trước đó thất bại. Nhóm thực thi cũng được dừng và đợi kết thúc. Việc gọi `clear()` sau cùng chỉ dọn phần tử `later`; nó không thay thế các bước quản lý luồng.

Nếu bạn viết vòng lặp nhận thực tế, phải quyết định một trong các chính sách khi bị ngắt: truyền ngoại lệ lên trên, kết thúc vòng lặp, hoặc xử lý theo một giao thức dừng đã định nghĩa. Bắt ngoại lệ rồi lặp lại `take()` vô điều kiện có thể làm hệ thống không dừng được khi phía quản lý yêu cầu dừng.

### Tại sao trường hạn đều là final?

Ngay cả khi `ready` và `later` được nhìn thấy từ các luồng khác nhau, mã không có nhánh sửa hạn của chúng. Sau khi tạo, mỗi đối tượng giữ nguyên danh tính và hạn; thời gian còn lại thay đổi vì đồng hồ tiếp tục chạy.

Hàng đợi giải quyết việc phối hợp đưa/lấy; nó không khiến mọi dữ liệu tùy ý bên trong phần tử trở thành an toàn để sửa đồng thời. Nếu cần thêm trạng thái có thể thay đổi, phải thiết kế riêng cách đồng bộ hóa và vòng đời của trạng thái ấy. Với ví dụ này, giữ dữ liệu cần thiết bất biến là lựa chọn gọn và dễ kiểm chứng.


## 7. Ghép thành luồng thử lại có giới hạn

Một lần thử thất bại thường không nên được chạy lại ngay trong một vòng lặp nóng. Ta muốn chờ một khoảng ngắn, đồng thời giữ rõ số lần thử và điều kiện dừng.

Ví dụ dưới mô phỏng một công việc: lần 1 và 2 cần thử lại, lần 3 thành công. Nó không gọi mạng và không dùng lỗi ngẫu nhiên, nên kết quả có thể kiểm chứng. Mỗi lần lên lịch lại tạo một `RetryItem` mới với hạn mới; không sửa phần tử đang nằm trong hàng đợi.

Lưu thành `DelayQueueRetryDemo.java`:

```java
import java.util.Objects;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayQueueRetryDemo {
    public static void main(String[] args) throws Exception {
        DelayQueue<RetryItem> queue = new DelayQueue<>();
        queue.offer(new RetryItem("job-7", 1, 0, TimeUnit.NANOSECONDS));

        boolean completed = false;
        while (!completed) {
            RetryItem item = queue.poll(5, TimeUnit.SECONDS);
            require(item != null, "retry did not become available");
            require(item.getDelay(TimeUnit.NANOSECONDS) <= 0, "not expired");
            if (item.attempt < 3) {
                System.out.println("attempt " + item.attempt + " -> retry");
                queue.offer(new RetryItem(item.id, item.attempt + 1,
                        5, TimeUnit.MILLISECONDS));
            } else {
                System.out.println("attempt " + item.attempt + " -> success");
                completed = true;
            }
        }
        System.out.println("queue empty = " + queue.isEmpty());
    }

    private static final class RetryItem implements Delayed {
        private static final long ORIGIN = System.nanoTime();
        private static final long MAX_DELAY = TimeUnit.DAYS.toNanos(1);
        private final String id;
        private final int attempt;
        private final long deadline;

        private RetryItem(String id, int attempt, long delay, TimeUnit unit) {
            this.id = Objects.requireNonNull(id);
            if (attempt < 1) {
                throw new IllegalArgumentException("positive attempt required");
            }
            this.attempt = attempt;
            long nanos = Objects.requireNonNull(unit).toNanos(delay);
            if (nanos < -MAX_DELAY || nanos > MAX_DELAY) {
                throw new IllegalArgumentException("delay outside demo range");
            }
            deadline = Math.addExact(elapsed(), nanos);
        }

        private static long elapsed() {
            return System.nanoTime() - ORIGIN;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(deadline - elapsed(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (!(other instanceof RetryItem item)) {
                throw new ClassCastException("RetryItem required");
            }
            return Long.compare(deadline, item.deadline);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all DelayQueueRetryDemo.java
java DelayQueueRetryDemo
```

Kết quả:

```text
attempt 1 -> retry
attempt 2 -> retry
attempt 3 -> success
queue empty = true
```

### Theo dõi đường đi của một yêu cầu

Lần đưa vào đầu tiên có khoảng trì hoãn 0. Nó đã đến hạn khi phía nhận kiểm tra, nên lần lấy đầu không cần chờ cho một hạn tương lai.

Sau khi nhận lần 1, chính vòng lặp trong `main` quyết định rằng cần thử lại. Nó tạo lần 2 với khoảng 5 mili giây rồi gọi `offer()`. Khi vòng lặp lấy tiếp, phần tử chỉ được giao nếu đã đến hạn. Nếu máy đã tiêu thụ quá 5 mili giây giữa lúc thêm và lúc gọi lấy, việc trả về ngay cũng đúng: thời gian trì hoãn đã trôi qua.

Sau lần 2, quy trình tương tự tạo lần 3. Lần 3 thành công, `completed` thành `true` và vòng lặp kết thúc. Không còn phần tử để hẹn lại, nên `queue empty = true`.

Điểm quyết định không nằm ở một cơ chế bí mật của `DelayQueue`. Mã ứng dụng chủ động:

1. Lấy một phần tử đến hạn.
2. Thực hiện hoặc mô phỏng một lần thử.
3. Quyết định kết thúc hay lên lịch lại.
4. Nếu lên lịch lại, tạo một phần tử bất biến mới.

Khoảng 5 mili giây là khoảng trì hoãn đến lúc **đủ điều kiện lấy**, không phải cam kết rằng câu lệnh xử lý sẽ bắt đầu đúng sau 5 mili giây. Khi có nhiều yêu cầu hoặc phía nhận còn bận, một mục đã đến hạn vẫn có thể nằm trong hàng đợi.

Ví dụ giới hạn ba lần để có một vòng đời hoàn chỉnh. Khi áp dụng vào công việc thật, cần định nghĩa rõ số lần tối đa, lỗi nào được thử lại, xử lý khi dừng và cách tránh thực hiện trùng. Một hàng đợi theo hạn không tự biến một thao tác bên ngoài thành “thực hiện đúng một lần”.

Trong chương trình, `poll(5, SECONDS)` chỉ là một giới hạn chờ để phát hiện bất thường trong minh họa. Nó không phải chính sách thử lại sau 5 giây; chính khoảng truyền cho `RetryItem` mới tạo hạn. Phân biệt hai tham số này giúp tránh một lỗi thiết kế rất phổ biến: nhầm thời gian phía nhận chịu chờ với thời gian công việc cần trì hoãn.

## 8. Những lỗi cần nhận ra từ triệu chứng

### 8.1. Hàng đợi có nhiều phần tử nhưng phía nhận vẫn chờ

Trước tiên hãy hỏi: các phần tử ấy đã đến hạn chưa? Quan sát `peek()` rồi đọc `getDelay(NANOSECONDS)` để hiểu ứng viên tại thời điểm quan sát. Đây là thông tin chẩn đoán, không phải một thao tác đặt chỗ: một luồng khác có thể lấy hoặc thêm phần tử ngay sau đó.

Nếu ứng viên còn thời gian dương, việc `take()` chờ là bình thường. Nếu một phần tử bạn tin là đến hạn bị đứng sau ứng viên tương lai, kiểm tra `compareTo()` và việc có sửa khóa sắp xếp sau khi thêm hay không.

Đừng chữa bằng vòng lặp:

```java
// Phản ví dụ: chỉ là trích đoạn, không chạy độc lập.
while (!queue.isEmpty()) {
    Object item = queue.poll();
    // item vẫn có thể là null; vòng lặp có thể quay liên tục.
}
```

Một vòng lặp không chờ như vậy có thể tiêu thụ CPU khi toàn bộ mục còn ở tương lai. Nếu mô hình yêu cầu đợi mục kế tiếp, dùng thao tác có chờ và có chính sách dừng; nếu chỉ cần kiểm tra tức thời, xử lý kết quả `null` rồi trả quyền điều khiển cho bên gọi.

### 8.2. Phần tử không bao giờ được lấy

Kiểm tra xem `getDelay()` có đang trả mãi khoảng trì hoãn ban đầu hay không. Cũng kiểm tra đơn vị trả về, dấu phép trừ và việc hai loại phần tử có dùng cùng gốc thời gian.

Nếu viết `now - deadline` thay vì `deadline - now`, một mục tương lai có thể có dấu âm và được lấy sớm. Nếu luôn trả về một số dương, mục không đến hạn theo lời khai của nó. Nếu so sánh theo tên trong khi thời gian theo hạn, ứng viên đầu không còn đại diện cho mục sớm nhất.

Các lỗi này thuộc hợp đồng của phần tử. Thêm `synchronized` quanh lời gọi `poll()` không sửa được một biểu diễn thời gian sai.

### 8.3. Muốn hẹn lại nhưng chỉ thay một trường

Thay hạn trực tiếp có thể để thứ tự bên trong và thời gian chờ không còn phù hợp với giá trị mới. Dùng trường `volatile` cũng không biến thao tác sửa ấy thành “xóa rồi chèn lại”.

Với một công việc có thể được hẹn lại từ nhiều luồng, chỉ gọi `remove(oldItem)` rồi thêm bản mới chưa đủ để bảo đảm không có lần chạy cũ: phía nhận có thể đã lấy `oldItem`. Bạn cần quyết định kết quả cạnh tranh ấy ở cấp ứng dụng, chẳng hạn kiểm tra phiên bản công việc trước khi thực hiện hoặc có trạng thái hủy được đồng bộ thích hợp. Bài này chỉ yêu cầu nhận diện ranh giới; không giả định một giao thức hủy duy nhất đúng cho mọi ứng dụng.

`remove(Object)` dùng quy tắc khớp đối tượng. Trong ba lớp ví dụ, hai đối tượng có cùng tên hoặc mã công việc vẫn là hai đối tượng khác nhau vì không ghi đè `equals()`. Khi tự thiết kế `equals()`, phải cân nhắc liệu nhiều lần thử của cùng công việc có cần được phân biệt hay không. Đừng mặc nhiên coi mã công việc giống nhau là đã xác định đúng lần hẹn cần xóa.

### 8.4. Đưa nhiều dữ liệu vào rồi kỳ vọng hàng đợi tự giới hạn

`DelayQueue` không có sức chứa cấu hình để tự tạo áp lực ngược cho phía gửi. `remainingCapacity()` trả `Integer.MAX_VALUE` không có nghĩa JVM có bộ nhớ vô hạn hay có thể giữ đúng ngần ấy đối tượng trong thực tế.

Một mục đã đến hạn không tự biến mất chỉ vì thời gian trôi qua. Phải có thao tác lấy/xóa nó. Nếu phía gửi liên tục thêm, phía nhận bị dừng hoặc xử lý chậm, dữ liệu có thể tích tụ trong bộ nhớ.

Vì thế cần phân biệt hai bài toán:

- Quy tắc đủ hạn trả lời **khi nào được lấy**.
- Kiểm soát lượng công việc trả lời **có cho phép nhận thêm không**.

Nếu hệ thống cần giới hạn lượng mục đang chờ, phải bổ sung chính sách nhận việc phù hợp và giải phóng hạn mức đúng vòng đời. Không thể đạt điều ấy chỉ bằng cách chọn bản `offer` có tham số thời gian.

### 8.5. Gọi clear rồi chờ mọi luồng kết thúc

`clear()` xóa dữ liệu đang lưu. Nó không đóng hàng đợi và không quy định rằng các lần `take()` đang chờ phải kết thúc với một tín hiệu “đã đóng”. Một phía nhận vẫn có thể tiếp tục đợi mục đến hạn mới.

Quản lý dừng là giao thức khác: ứng dụng có thể giữ tham chiếu luồng để ngắt, dùng nhóm thực thi và hủy công việc đang chờ, hoặc thiết kế một tín hiệu kết thúc phù hợp. Phải đợi việc kết thúc nếu vòng đời tài nguyên yêu cầu điều ấy.

Đừng sao chép máy móc một phần tử báo dừng từ hàng đợi FIFO:

- Nếu phần tử báo dừng có hạn xa, phía nhận vẫn có thể phải chờ.
- Nếu nó đến hạn ngay, phía nhận có thể dừng trước các công việc tương lai mà bạn còn muốn xử lý.
- Nếu có nhiều phía nhận, cần quyết định tín hiệu kết thúc dành cho bao nhiêu phía.

Câu hỏi đúng là “khi dừng, các mục đang chờ được hoàn tất, hủy hay lưu lại?”, rồi mới chọn cách báo dừng.

### 8.6. Duyệt để suy ra thứ tự sắp nhận

Bộ duyệt của `DelayQueue` có tính nhất quán yếu và không bảo đảm thứ tự hạn. Nó có thể quan sát cả phần tử tương lai. Mảng thu được từ `toArray()` cũng không phải một danh sách đã sắp theo thời điểm được nhận.

Nếu chỉ cần hiển thị chẩn đoán, có thể sao chép rồi sắp xếp bản sao theo dữ liệu hạn đã biết, đồng thời hiểu rằng các luồng khác vẫn tiếp tục thay đổi hàng đợi. Bản sao ấy không phải ảnh chụp nguyên tử của mọi trạng thái trong hệ thống và không đặt chỗ cho lần lấy tiếp theo.

Nếu cần xử lý theo điều kiện của hàng đợi, hãy dùng thao tác lấy của chính hàng đợi. Đừng dùng vòng `for-each` làm bộ lập lịch.

### 8.7. Coi drainTo là “xóa tất cả” hoặc chuyển giao không thể thất bại

Chương trình đầu đã cho thấy `late` còn lại sau `drainTo()`. Đây là thao tác chuyển phần đủ điều kiện, không phải cách sao chép/xóa mọi phần tử đang được lưu. Muốn dọn toàn bộ dữ liệu là một yêu cầu khác.

Hãy chọn nơi nhận có khả năng thêm phần tử bình thường. Theo hợp đồng `drainTo`, nếu việc thêm sang nơi nhận ném ngoại lệ, không được suy ra một giao dịch đã tự hoàn tác hoàn toàn. Tránh chuyển vào chính hàng đợi nguồn. Khi cần tính nguyên tử trên hai cấu trúc hoặc xử lý nghiệp vụ ngoài bộ nhớ, phải thiết kế một giao thức riêng; một lời gọi chuyển hàng loạt không cung cấp mọi bảo đảm ấy.

## 9. Chọn đúng công cụ cho câu hỏi đang giải

`DelayQueue` phù hợp khi ứng dụng muốn sở hữu loại phần tử, điều kiện hạn và vòng nhận/xử lý. Chẳng hạn chương trình thử lại vừa rồi muốn kiểm soát rõ từng lần nhận và tạo lại phần tử.

Nếu yêu cầu chính là “chạy đoạn mã sau một khoảng” hoặc “chạy định kỳ”, hãy xem [ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html). Giao diện ấy có thao tác lập lịch thực hiện và trả về đối tượng để theo dõi/hủy công việc. `DelayQueue` chỉ cung cấp nền lưu và lấy theo hạn; nếu dùng nó, bạn tự sở hữu phần thực thi.

Nếu chỉ cần trao đổi dữ liệu giữa các luồng mà không có điều kiện thời gian trên từng mục, hãy bắt đầu từ yêu cầu thứ tự và sức chứa của một `BlockingQueue` phù hợp. Đừng thêm `Delayed` khi không có ý nghĩa nghiệp vụ nào cho hạn.

Nếu mục tiêu là dữ liệu hết hạn cần được dọn, việc chọn `DelayQueue` vẫn chưa tạo ra một hệ thống dọn tự động: cần có phía nhận và logic xóa tài nguyên. Nếu cần khôi phục sau khi tiến trình dừng, còn phải có nguồn lưu bền vững và quy tắc dựng lại lịch. Hàng đợi trong bộ nhớ không thay thế các yêu cầu ấy.

Các lựa chọn trên đều không tự bảo đảm bắt đầu xử lý đúng vào một thời điểm tuyệt đối. Đến hạn tạo điều kiện chạy; CPU, luồng xử lý, độ trễ hệ thống và công việc trước đó vẫn ảnh hưởng lúc thực hiện thật.

## 10. Tự kiểm tra và thực hành có lời giải

### Câu 1. Hàng đợi chỉ có một mục còn 30 giây. Dự đoán các kết quả

Giả sử không có luồng khác thêm/xóa và hạn chưa đến trong lúc quan sát. `size()`, `peek()`, `poll()` và `remove()` không đối số cho kết quả gì?

**Lời giải:** `size()` là 1; `peek()` trả mục ấy; `poll()` trả `null`; `remove()` ném `NoSuchElementException`. Phải phân biệt tồn tại với đủ điều kiện lấy. `remove(mucDo)` lại có thể xóa mục khi khớp đối tượng.

**Thực hành:** dùng đồng hồ điều khiển của chương trình đầu, tạo một mục có hạn 30 thay vì ngủ 30 giây. Kiểm chứng trạng thái trước hạn, đúng hạn và sau hạn. Chỉ dùng các thao tác không chờ trong thí nghiệm này.

### Câu 2. Sửa compareTo để ưu tiên công việc có mã nhỏ hơn có hợp lý không?

Giả sử mục mã 1 còn một giờ, mục mã 2 đã đến hạn. Bạn muốn mã 1 có “ưu tiên nghiệp vụ cao hơn”.

**Lời giải:** không thể thay thứ tự đến hạn bằng thứ tự mã mà vẫn giữ hợp đồng đang cần. Khi mục chưa đến hạn đứng trước, mục đã đến hạn có thể bị che. Nếu có ưu tiên nghiệp vụ, trước tiên phải định nghĩa cách nó tương tác với điều kiện thời gian. Một tiêu chí phụ cho các hạn bằng nhau khác với việc bỏ qua hạn hoàn toàn.

**Thực hành:** trên một bản thử riêng dùng đồng hồ điều khiển, cố ý đảo phép so sánh hạn rồi quan sát `peek()`/`poll()`. Giải thích bằng ứng viên đầu, sau đó phục hồi phép so sánh đúng. Không dùng phiên bản cố ý sai cho xử lý công việc thật.

### Câu 3. Tại sao thấy 0 mili giây mà poll vẫn trả null?

**Lời giải:** phép đổi đơn vị có thể bỏ phần lẻ. Khoảng 1 nano giây vẫn dương ở đơn vị nano giây nhưng là 0 ở mili giây. Điều kiện của hàng đợi dựa vào nano giây.

**Thực hành:** trong chương trình đầu, cho `tinyClock` chuyển từ 0 sang 1, rồi gọi lại `poll()`. Lúc này phần tử `tiny` phải được trả về. Không thêm một độ trễ thật vào bài kiểm chứng đơn vị.

### Câu 4. Vừa gọi remove(oldItem) nhưng một lần xử lý cũ vẫn xảy ra. Có thể không?

**Lời giải:** có. Phía nhận có thể đã lấy phần tử trước thao tác xóa; hoặc đối tượng bạn truyền không khớp mục cần xóa theo quy tắc bằng nhau. Hàng đợi không quay ngược việc đã giao phần tử. Nếu yêu cầu hủy phải tác động cả công việc đã được nhận, cần một trạng thái/giao thức hủy ở cấp công việc và một thời điểm kiểm tra rõ ràng.

**Thực hành:** mô tả hai lịch xen kẽ: xóa trước lần lấy, và lần lấy trước thao tác xóa. Ghi kết quả kỳ vọng của thao tác xóa trong từng lịch, rồi nêu điều bạn vẫn chưa biết về việc công việc bên ngoài đã thực hiện hay chưa.

### Câu 5. Thay 5 mili giây thử lại bằng 5 giây có phải chỉ sửa poll không?

**Lời giải:** không. Muốn hẹn lần thử sau 5 giây, phải đổi khoảng truyền cho `RetryItem` mới. `poll(timeout, unit)` chỉ giới hạn lần chờ của phía nhận. Nếu thời gian chờ ngắn hơn hạn của mục, kết quả `null` là một khả năng hợp lệ cần xử lý, không phải bằng chứng mất mục.

**Thực hành:** chọn một khoảng trì hoãn dài hơn giới hạn chờ, rồi thiết kế nhánh xử lý `null` rõ ràng. Xác định lúc nào tiếp tục chờ và lúc nào toàn bộ công việc bị hủy. Không chỉ xóa kiểm tra `require` rồi để vòng lặp quay vô hạn.

### Câu 6. Muốn dừng luồng nhận khi chưa có mục đến hạn thì clear có đủ không?

**Lời giải:** không. `clear()` dọn dữ liệu nhưng không đóng hàng đợi. Luồng đang nhận cần một giao thức dừng, chẳng hạn bị ngắt và kết thúc khi nhận `InterruptedException`, sau đó phía quản lý đợi nó kết thúc.

**Thực hành:** đọc lại `showInterruption()` và chỉ ra ba việc khác nhau: gửi yêu cầu ngắt, phía nhận quan sát yêu cầu, và phía quản lý đợi luồng kết thúc. Giải thích vì sao latch đặt trước `take()` không xác nhận trạng thái đang chờ.

## 11. Kiểm chứng và tài liệu tra cứu

Ba chương trình trong bài được biên dịch với `javac --release 17 -encoding UTF-8 -Xlint:all` và chạy trên OpenJDK 24.0.2; kết quả đầu ra khớp các khối `text` đã nêu. `--release 17` kiểm tra khả năng dùng cú pháp/API ở mức Java 17 khi biên dịch; nó không có nghĩa lần chạy này đã diễn ra trên một JVM 17.

Khi tự chạy, giữ đúng tên tệp, dùng JDK 17 trở lên và chạy từng chương trình độc lập. Chương trình đầu không đo thời gian thật; hai chương trình sau dùng hạn tương đối và có giới hạn chờ để tránh bỏ lại vòng nhận vô hạn trong minh họa. Không có kết quả nào yêu cầu một thời điểm thực hiện chính xác đến nano giây.

Các nguồn gốc để đối chiếu hợp đồng:

- [Delayed — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Delayed.html): thời gian còn lại và thứ tự so sánh nhất quán.
- [DelayQueue — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/DelayQueue.html): quy tắc lấy, quan sát, xóa, chuyển và duyệt.
- [AbstractQueue — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractQueue.html): hành vi của `remove()` và `element()` kế thừa.
- [TimeUnit — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeUnit.html): chuyển đơn vị, bỏ phần lẻ và giới hạn biểu diễn.
- [System.nanoTime() — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime()): đo thời gian trôi qua và giới hạn của hiệu thời gian.
- [ScheduledExecutorService — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): lập lịch thực hiện khi cần quản lý việc chạy.
- [Mã nguồn DelayQueue trong OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/concurrent/DelayQueue.java): phần quan sát triển khai về cấu trúc ưu tiên và báo hiệu khi ứng viên đầu thay đổi; không thay thế hợp đồng API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-queues-007">SynchronousQueue và TransferQueue</a>

<details>
<summary>Xem chi tiết</summary>

# SynchronousQueue và TransferQueue

## 1. Ba mốc khác nhau: được chấp nhận, có bên nhận, xử lý hoàn tất

Trong một quy trình chuyển công việc giữa các luồng, chữ “gửi thành công” có thể mang nhiều nghĩa:

1. Hàng đợi đã chấp nhận công việc để giữ hoặc chuyển tiếp.
2. Công việc đã được ghép với một bên nhận.
3. Bên nhận đã xử lý xong và có kết quả.

Nếu không xác định rõ mốc nào đang được xác nhận, chương trình có thể tiếp tục quá sớm hoặc chờ ở sai nơi.

Một hàng đợi có vùng đệm thường cho phép bên sản xuất gửi trước, bên tiêu thụ lấy sau. Một cơ chế chuyển giao trực tiếp yêu cầu thao tác gửi và thao tác nhận gặp nhau. Một cơ chế trả kết quả còn phải xác nhận cả phần xử lý sau khi nhận.

```text
Chấp nhận công việc
    → ghép với thao tác nhận
        → bên nhận thực hiện xử lý
            → công bố kết quả hoặc lỗi
```

`SynchronousQueue` tập trung vào chuyển giao trực tiếp: không có chỗ để giữ một phần tử chờ như hàng đợi dung lượng 1.

`TransferQueue` là giao diện bổ sung cách gửi có thể chờ bên nhận. Cài đặt thông dụng trong thư viện chuẩn là `LinkedTransferQueue`; nó cho phép cả gửi thông thường vào một hàng đợi không giới hạn dung lượng và gửi theo cách chờ được nhận.

Hai cơ chế giúp bên sản xuất biết thêm về điểm chuyển giao. Chúng chưa tự trở thành lời gọi xử lý nghiệp vụ đồng bộ đến lúc có kết quả.

Bài dùng Java 17 trở lên. Bốn chương trình bên dưới độc lập, không cần thư viện ngoài. Lưu đúng tên tệp, biên dịch và chạy từ thư mục chứa tệp. Kết quả được in từ luồng chính ở các điểm có phối hợp rõ, thay vì suy đoán từ thứ tự in giữa nhiều luồng.

## 2. SynchronousQueue: điểm gặp giữa bên gửi và bên nhận

### 2.1. Không phải hàng đợi có một ô

`SynchronousQueue<E>` không có dung lượng chứa nội bộ, kể cả một phần tử. Mỗi lần đưa vào thành công phải được ghép với một thao tác lấy tương ứng. Ngược lại, bên lấy cũng cần một bên làm dữ liệu sẵn sàng để chuyển giao. Xem [SynchronousQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SynchronousQueue.html).

Mô hình khái niệm:

```text
Hàng đợi có vùng đệm:
gửi A → [ A được giữ ] → sau đó mới có bên lấy A

SynchronousQueue:
bên gửi A ── gặp ── bên nhận A
           không có ô giữ A giữa hai giai đoạn độc lập
```

Điều này không đòi hai luồng bắt đầu lời gọi đúng cùng một thời điểm trên đồng hồ. Một bên có thể đến trước và chờ bên còn lại.

Cũng không có nghĩa không dùng bộ nhớ nào: bên gửi đang chờ vẫn có thể giữ đối tượng cần chuyển, trạng thái lời gọi và tài nguyên của tác vụ. Không có vùng đệm phần tử không đồng nghĩa toàn bộ quy trình không có công việc tồn.

### 2.2. Chọn phương thức là chọn cách chờ đối tác

| Lời gọi trên SynchronousQueue | Ý nghĩa chính |
|---|---|
| `put(e)` | Chờ ghép được với thao tác nhận nếu cần |
| `offer(e)` | Chỉ chuyển nếu có thể ghép với bên nhận đang chờ; không giữ lại khi trả `false` |
| `offer(e, timeout, unit)` | Dành một khoảng chờ để có bên nhận |
| `take()` | Chờ có bên gửi để nhận phần tử |
| `poll()` | Thử nhận từ một bên đang làm phần tử sẵn sàng |
| `poll(timeout, unit)` | Dành một khoảng chờ cho phần tử được chuyển đến |

`offer` không thể thành công chỉ vì cấu trúc “đang rỗng”. Với cơ chế này, không có ô trống để chiếm; điều kiện cần là đối tác nhận.

`take` trả phần tử đã nhận. `put` hoàn tất bình thường xác nhận việc chuyển giao đã được ghép, không phải việc xử lý dữ liệu sau lời gọi nhận đã hoàn tất.

### 2.3. Chương trình chuyển giao và các phép quan sát

Tệp `SynchronousHandoffDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class SynchronousHandoffDemo {
    public static void main(String[] args) throws Exception {
        var queue = new SynchronousQueue<String>();
        System.out.println("initialSize=" + queue.size());
        System.out.println("remainingCapacity=" + queue.remainingCapacity());
        System.out.println("offerWithoutReceiver=" + queue.offer("unused"));
        System.out.println("pollWithoutSender=" + queue.poll());

        var started = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var sender = pool.submit(() -> {
                started.countDown();
                queue.put("A");
                return "handoff completed";
            });
            if (!started.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("sender did not start");
            }

            System.out.println("sizeBeforeReceive=" + queue.size());
            System.out.println("emptyBeforeReceive=" + queue.isEmpty());
            System.out.println("peekBeforeReceive=" + queue.peek());
            System.out.println("received=" + queue.take());
            System.out.println("sender=" + sender.get(10, TimeUnit.SECONDS));

            System.out.println("timedOfferWithoutReceiver=" +
                    queue.offer("B", 30, TimeUnit.MILLISECONDS));
            System.out.println("timedPollWithoutSender=" +
                    queue.poll(30, TimeUnit.MILLISECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SynchronousHandoffDemo.java
java SynchronousHandoffDemo
```

Stdout:

```text
initialSize=0
remainingCapacity=0
offerWithoutReceiver=false
pollWithoutSender=null
sizeBeforeReceive=0
emptyBeforeReceive=true
peekBeforeReceive=null
received=A
sender=handoff completed
timedOfferWithoutReceiver=false
timedPollWithoutSender=null
```

Ban đầu không có bên nhận, nên `offer("unused")` trả `false` và phần tử không được giữ lại. Không có bên gửi, nên `poll()` trả `null`.

Sau đó tác vụ gửi gọi `put("A")`, còn luồng chính gọi `take()`. Cặp lời gọi ghép với nhau: luồng chính nhận A, bên gửi có thể hoàn tất.

Cổng `started` nằm trước `put`. Nó cho biết tác vụ đã đi đến điểm đó, không chứng minh luồng chắc chắn đã chuyển sang trạng thái chờ tại một thời điểm cụ thể. Dù bên nào đi đến thao tác hàng đợi trước, chương trình vẫn có đủ hai bên để chuyển giao.

Ở cuối chương trình, bên gửi trước đã hoàn tất và không có đối tác mới. Lần `offer` có timeout trả `false`; lần `poll` có timeout trả `null`.

Không in thời gian đã trôi vì bài kiểm tra nhánh kết quả, không hứa độ chính xác của lịch thực thi.

### 2.4. Vì sao size luôn 0 mà vẫn chuyển được dữ liệu?

Với các phương thức quan sát kiểu collection, `SynchronousQueue` hoạt động như một collection rỗng:

- `size()` là 0.
- `isEmpty()` là `true`.
- `remainingCapacity()` là 0.
- `peek()` là `null`.
- Iterator không cung cấp một danh sách phần tử đang chờ để duyệt.

Trong chương trình, các kết quả trước khi nhận vẫn như vậy dù tác vụ gửi đã bắt đầu. Không thể dùng chúng để đếm bên gửi đang chờ, đo tổng công việc của hệ thống hay kết luận không có hoạt động chuyển giao.

Đừng viết “nếu size bằng 0 thì không gọi take”. Với `SynchronousQueue`, chính thao tác lấy mới tham gia vào việc nhận dữ liệu.

Tương tự, `clear()` không phải một thao tác hủy mọi bên đang chờ. Quy trình dừng phải dùng cơ chế thích hợp cho tác vụ đang thực hiện, chẳng hạn gián đoạn có hợp tác.

## 3. TransferQueue: chọn chờ được nhận hoặc chỉ xếp hàng

### 3.1. Phân biệt giao diện và lớp triển khai

```text
BlockingQueue
    ├── SynchronousQueue
    └── TransferQueue
            └── LinkedTransferQueue
```

Sơ đồ thể hiện khả năng qua giao diện, không khẳng định mọi đường là quan hệ lớp cha trực tiếp.

`SynchronousQueue` không triển khai giao diện `TransferQueue`; nó không có phương thức `transfer` chỉ vì hành vi `put` cũng đòi một lần nhận.

`TransferQueue<E>` mở rộng `BlockingQueue<E>`, thêm các phép chuyển giao và thông tin về bên nhận đang chờ. Giao diện này không tự quy định mọi cài đặt phải không giới hạn dung lượng. Xem [TransferQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TransferQueue.html).

Trong bài, lớp cụ thể được dùng là `LinkedTransferQueue`. Đây là một hàng đợi dựa trên nút liên kết, không có giới hạn capacity đặt qua constructor. Đừng nhầm nó với `LinkedBlockingQueue`, vốn có constructor cho phép chỉ định dung lượng.

### 3.2. Các phép gửi có ý nghĩa khác nhau

| Phép gọi | Điều bên gửi yêu cầu | Có giữ dữ liệu để chờ bên nhận không? |
|---|---|---|
| `put(e)`, `offer(e)` thông thường trên LinkedTransferQueue | Hàng đợi chấp nhận dữ liệu | Có thể giữ để bên nhận đến sau; không chờ xác nhận nhận |
| `transfer(e)` | Chờ chuyển được phần tử cho bên nhận | Có thể chờ việc nhận, kể cả khi chưa có bên nhận lúc gọi |
| `tryTransfer(e)` | Chỉ chuyển ngay cho bên nhận đang chờ | Nếu thất bại trả `false`, không xếp phần tử lại để chờ |
| `tryTransfer(e, timeout, unit)` | Chờ việc nhận trong một khoảng | Có thể chờ; hết thời gian thì báo thất bại theo hợp đồng |

`tryTransfer(e)` không phải tên khác của `offer(e)`. Trên `LinkedTransferQueue`, `offer` có thể nhận dữ liệu khi chưa có người lấy, còn `tryTransfer` không làm điều đó nếu phải trả `false`.

Tương tự, `offer(e, timeout, unit)` trên `LinkedTransferQueue` không biến thành lời gọi chờ người nhận. Vì lớp không bị giới hạn dung lượng, timeout đó không tạo một giới hạn chờ được nhận. Phải chọn đúng `tryTransfer` có timeout nếu đó là yêu cầu.

### 3.3. Chương trình không có bên nhận

Tệp `BufferedTransferChoicesDemo.java`:

```java
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;

public class BufferedTransferChoicesDemo {
    public static void main(String[] args) throws InterruptedException {
        TransferQueue<String> queue = new LinkedTransferQueue<>();
        queue.put("A");
        System.out.println("offerB=" + queue.offer("B"));
        System.out.println("timedOfferC=" +
                queue.offer("C", 1, TimeUnit.SECONDS));
        System.out.println("buffered=" + queue);
        System.out.println("taken=" + queue.take() + ","
                + queue.take() + "," + queue.take());

        System.out.println("immediateTransfer=" + queue.tryTransfer("X"));
        System.out.println("afterImmediateFailure=" + queue.poll());
        System.out.println("timedTransfer=" +
                queue.tryTransfer("Y", 30, TimeUnit.MILLISECONDS));
        System.out.println("afterTimedFailure=" + queue.poll());
        System.out.println("remainingCapacity=" + queue.remainingCapacity());
        System.out.println("waitingConsumer=" + queue.hasWaitingConsumer());
        System.out.println("waitingConsumerCount=" +
                queue.getWaitingConsumerCount());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 BufferedTransferChoicesDemo.java
java BufferedTransferChoicesDemo
```

Stdout:

```text
offerB=true
timedOfferC=true
buffered=[A, B, C]
taken=A,B,C
immediateTransfer=false
afterImmediateFailure=null
timedTransfer=false
afterTimedFailure=null
remainingCapacity=2147483647
waitingConsumer=false
waitingConsumerCount=0
```

Không có luồng tiêu thụ nào trong chương trình. A, B, C vẫn được chấp nhận qua `put` và `offer`, rồi luồng chính lấy chúng theo thứ tự đã gửi.

Điều quan trọng là lời gọi đưa A vào đã kết thúc trước khi chương trình bắt đầu lấy A. `LinkedTransferQueue.put` không cần một đối tác nhận xuất hiện ngay để hoàn tất.

Ngược lại, `tryTransfer("X")` không có bên nhận đang chờ nên trả `false`. X không xuất hiện ở lần `poll` sau. Lần chuyển Y có timeout cũng thất bại và không để Y lại trong hàng đợi.

Các bảo đảm về hết thời gian hoặc gián đoạn không để phần tử tiếp tục nằm chờ được mô tả rõ trong [các phép chuyển của LinkedTransferQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedTransferQueue.html#tryTransfer(E,long,java.util.concurrent.TimeUnit)).

`remainingCapacity()` ở đây là `Integer.MAX_VALUE`, biểu thị lớp không bị giới hạn dung lượng theo capacity. Nó không đo bộ nhớ còn lại và không chứng minh nguồn tài nguyên vô hạn.

Các phép đếm bên nhận trả 0 hoặc `false` vì trong thí nghiệm không có bên nhận nào. Trong một quy trình thật có nhiều luồng, chúng chỉ là quan sát trạng thái tại một thời điểm, không phải quyền giữ chỗ.

## 4. transfer xác nhận chuyển giao, không xác nhận xử lý xong

### 4.1. Điểm dễ hiểu sai nhất

Xét đường gọi:

```text
Bên sản xuất: transfer(work) ── hoàn tất khi việc chuyển giao đã ghép
                                 │
Bên tiêu thụ:               take() nhận work
                                 → xử lý
                                 → tạo kết quả
```

Mã xử lý nằm sau `take()`. Bên gửi có thể được tiếp tục trước khi phần xử lý đó chạy đến kết quả.

Thậm chí không nên suy ra rằng mọi câu lệnh ngay sau `take` ở bên nhận đều đã chạy trước khi `transfer` ở bên gửi trở về. Hai luồng được lập lịch độc lập sau khi chuyển giao đã được ghép.

Nếu nghiệp vụ cần kết quả, hãy mô hình hóa một đường phản hồi riêng. Một cách ở mức ứng dụng là gửi cùng đối tượng công việc một `CompletableFuture` để bên xử lý công bố kết quả hoặc lỗi.

### 4.2. Chương trình tách nhận và xử lý bằng cổng chờ

Tệp `ReceiptAndReplyDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;

public class ReceiptAndReplyDemo {
    record Work(String text, CompletableFuture<Integer> reply) {}

    public static void main(String[] args) throws Exception {
        TransferQueue<Work> queue = new LinkedTransferQueue<>();
        var received = new CountDownLatch(1);
        var allowProcessing = new CountDownLatch(1);
        var work = new Work("java", new CompletableFuture<>());
        var pool = Executors.newFixedThreadPool(2);

        try {
            var consumer = pool.submit(() -> {
                Work item = queue.take();
                received.countDown();
                try {
                    if (!allowProcessing.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("processing gate timeout");
                    }
                    item.reply().complete(item.text().length());
                    return "consumer finished";
                } catch (Exception ex) {
                    item.reply().completeExceptionally(ex);
                    throw ex;
                }
            });
            var producer = pool.submit(() -> {
                queue.transfer(work);
                return "receipt confirmed";
            });

            System.out.println("transfer=" + producer.get(10, TimeUnit.SECONDS));
            if (!received.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("receipt gate timeout");
            }
            System.out.println("replyDoneBeforeProcessing=" + work.reply().isDone());

            allowProcessing.countDown();
            System.out.println("result=" + work.reply().get(10, TimeUnit.SECONDS));
            System.out.println("consumer=" + consumer.get(10, TimeUnit.SECONDS));
        } finally {
            allowProcessing.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ReceiptAndReplyDemo.java
java ReceiptAndReplyDemo
```

Stdout:

```text
transfer=receipt confirmed
replyDoneBeforeProcessing=false
result=4
consumer=consumer finished
```

`Work` chứa chuỗi cần xử lý và một future dành cho phản hồi. Bên tiêu thụ lấy được công việc, mở cổng `received`, rồi chưa được phép xử lý đến khi luồng chính mở `allowProcessing`.

Bên sản xuất gọi `transfer(work)`. Luồng chính đợi lời gọi đó hoàn tất, sau đó xác nhận bên nhận đã đi qua cổng nhận. Tuy vậy, future phản hồi vẫn chưa hoàn tất vì cổng xử lý còn đóng.

Kết quả `replyDoneBeforeProcessing=false` không dựa vào việc ngủ một khoảng rồi hy vọng bên kia chưa chạy xong. Nó dựa trên điều kiện rõ ràng: chương trình chưa cho phép xử lý.

Sau khi mở cổng, bên tiêu thụ tính độ dài chuỗi `"java"` và hoàn tất future bằng 4. Luồng chính nhận kết quả qua `get`, rồi đợi tác vụ tiêu thụ kết thúc.

### 4.3. Bốn trạng thái cần quản lý nếu có đường phản hồi

Một ứng dụng có thể cần phân biệt:

1. Chưa chuyển giao được.
2. Đã có bên nhận, đang chờ xử lý.
3. Xử lý hoàn tất với kết quả.
4. Xử lý thất bại hoặc bị hủy.

`transfer` giải quyết ranh giới chuyển giao, không thay thế mọi trạng thái còn lại.

`CompletableFuture` hỗ trợ công bố kết quả hoặc hoàn tất bằng lỗi. Nếu quên hoàn tất đường lỗi, bên chờ có thể giữ một future không còn ai chịu trách nhiệm kết thúc. Xem [CompletableFuture Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html).

Ví dụ bắt các ngoại lệ thông thường sau khi đã nhận để báo lỗi vào future, rồi để tác vụ thất bại. Đây là mẫu học về hai mốc, không phải một hệ thống phục hồi đầy đủ cho mọi lỗi, dừng tiến trình hoặc mất nguồn tài nguyên.

### 4.4. Timeout phản hồi khác timeout chuyển giao

Nếu bên gửi đợi phản hồi 1 giây rồi hết thời gian, công việc có thể đã được nhận và vẫn đang xử lý. Một lần hết thời gian ở `Future.get` không tự lấy công việc khỏi tay bên tiêu thụ.

Ngược lại, nếu chuyển giao có timeout chưa thành công, chưa nên chờ kết quả như thể đã có người chịu trách nhiệm xử lý.

Cần gắn chính sách hủy với trạng thái thực tế. Không tự gửi lại mọi công việc chỉ vì chưa có phản hồi: lần xử lý đầu có thể vẫn đang tiếp tục, dẫn đến thực hiện trùng.

Với các thao tác bên ngoài cần tránh lặp, mã nhận diện công việc và cơ chế xử lý lặp là quyết định ở mức nghiệp vụ. Một cấu trúc chuyển giao trong bộ nhớ không tự cung cấp việc xử lý đúng một lần trên toàn hệ thống.

## 5. Hết thời gian và gián đoạn phải là các nhánh riêng

### 5.1. Kết quả timeout

Trên `SynchronousQueue`:

- `offer(e, timeout, unit)` trả `false` khi không chuyển được trong thời gian chờ.
- `poll(timeout, unit)` trả `null` khi không nhận được phần tử trong thời gian chờ.

Trên `TransferQueue`, dạng `tryTransfer` có timeout trả `false` nếu không chuyển được theo thời gian chờ. Với `LinkedTransferQueue` trong ví dụ, phần tử của lần thất bại đó không bị để lại để một bên khác nhận sau.

Không đổi ý nghĩa của `false` thành “đã xếp hàng để xử lý sau”. Nếu ứng dụng muốn một nhánh dự phòng xếp hàng, phải viết và chấp nhận chính sách đó rõ ràng.

Timeout không phải thời gian phải chờ đủ nếu đối tác đã sẵn sàng. Nó cũng không phải cam kết thời gian thực cứng về thời điểm luồng chắc chắn được CPU và trở về với bên gọi.

### 5.2. Kết quả gián đoạn

`SynchronousQueue.put`, `take`, các dạng chờ có timeout, cũng như `transfer` và `tryTransfer` có timeout, có thể báo `InterruptedException`.

Đây thường là đường điều khiển để tác vụ hợp tác với yêu cầu dừng. Không nuốt ngoại lệ rồi lặp lại việc chuyển giao vô hạn mà không xét yêu cầu hủy.

Nếu phương thức có thể truyền tiếp ngoại lệ, giữ nguyên nhánh đó cho bên gọi quyết định. Nếu ở ranh giới tác vụ không truyền được ngoại lệ kiểm tra, có thể khôi phục cờ gián đoạn rồi kết thúc theo chính sách.

### 5.3. Chương trình hủy một lần transfer chưa có người nhận

Tệp `InterruptedTransferDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicReference;

public class InterruptedTransferDemo {
    public static void main(String[] args) throws Exception {
        TransferQueue<String> queue = new LinkedTransferQueue<>();
        var started = new CountDownLatch(1);
        var outcome = new AtomicReference<String>();

        Thread sender = new Thread(() -> {
            started.countDown();
            try {
                queue.transfer("cancelled");
                outcome.set("unexpected receipt");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                outcome.set("failure=" + ex.getClass().getSimpleName()
                        + ", restored=" + Thread.currentThread().isInterrupted());
            }
        }, "transfer-sender");

        sender.start();
        try {
            if (!started.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("sender did not start");
            }
            sender.interrupt();
            sender.join(10_000);
            if (sender.isAlive()) {
                throw new IllegalStateException("sender did not stop");
            }
            System.out.println(outcome.get());
            System.out.println("remainingElement=" + queue.poll());
            System.out.println("senderAlive=" + sender.isAlive());
        } finally {
            sender.interrupt();
            sender.join(10_000);
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 InterruptedTransferDemo.java
java InterruptedTransferDemo
```

Stdout:

```text
failure=InterruptedException, restored=true
remainingElement=null
senderAlive=false
```

Không có bên tiêu thụ nào có thể nhận phần tử trong thí nghiệm. Luồng chính yêu cầu gián đoạn, rồi đợi bên gửi kết thúc trước khi kiểm tra hàng đợi.

Cổng `started` nằm trước lời gọi `transfer`; tín hiệu có thể đến trước khi lời gọi bắt đầu chờ hoặc trong khi chờ. Chương trình không dùng trạng thái luồng quan sát được để ép một lịch thực thi duy nhất.

Kết quả cho thấy nhánh `InterruptedException` được xử lý, cờ được khôi phục và không còn phần tử `"cancelled"` để `poll` nhận sau đó.

Điều kiện “không có người nhận” giúp kết quả thí nghiệm rõ ràng. Nếu trong ứng dụng thật việc chuyển giao đã thành công rồi mới có yêu cầu dừng, không được xem tín hiệu đến sau như một thao tác hoàn tác phần xử lý đã giao.

### 5.4. Tách chính sách bỏ chờ và chính sách thu hồi công việc

Bỏ chờ một lần chuyển chưa thành công khác với thu hồi một công việc đã được người khác nhận. Bỏ chờ phản hồi cũng khác với dừng hoạt động xử lý hoặc I/O.

Hãy ghi rõ mỗi timeout áp dụng cho giai đoạn nào, ai còn giữ quyền sử dụng công việc và ai phải hoàn tất đường phản hồi. Nếu có nhiều lần thử, dùng một ngân sách tổng phù hợp thay vì tự cấp lại toàn bộ thời gian ở mọi bước.

Không dùng `clear`, `remove` hoặc một lần quan sát kích thước để thay cho giao thức hủy và xác nhận của ứng dụng. Những thao tác collection không mô hình hóa đầy đủ một công việc đã chuyển đến luồng khác.

## 6. Thông tin bên nhận đang chờ: quan sát chưa là đặt chỗ

`TransferQueue` có:

- `hasWaitingConsumer()`: có bên tiêu thụ đang chờ nhận qua `take` hoặc `poll` có thời hạn hay không.
- `getWaitingConsumerCount()`: ước lượng số bên đang chờ.

Các số liệu này hữu ích để quan sát hoặc đưa ra một lựa chọn có thể chấp nhận sai lệch. Chúng không bảo đảm trạng thái còn giữ nguyên đến lời gọi tiếp theo.

Đoạn minh họa dưới đây có khoảng trống giữa kiểm tra và hành động, chưa phải chương trình độc lập:

```java
// Không được suy ra transfer sẽ không chờ từ lần kiểm tra này.
if (queue.hasWaitingConsumer()) {
    queue.transfer(item);
}
```

Sau lần kiểm tra, một bên sản xuất khác có thể ghép với người nhận đó; người nhận cũng có thể hết thời gian hoặc bị gián đoạn. Lời gọi `transfer` của bên này vẫn có thể phải chờ.

Nếu đúng nhu cầu là chỉ chuyển ngay khi ghép được, gọi `tryTransfer(item)` và xử lý kết quả của chính lời gọi. Không dùng lần quan sát trước để biến một phép chờ thành một phép không chờ.

Tương tự, không chờ bằng vòng lặp liên tục gọi `getWaitingConsumerCount()` để cố tạo độ chính xác. Số đếm được mô tả là ước lượng và có thể tốn công hơn một phép kiểm tra đơn giản; nó không phải cơ chế đồng bộ điều khiển.

Một lối viết có thể phù hợp khi nghiệp vụ chấp nhận dự phòng xếp hàng là:

```java
// Mã minh họa trong một phương thức, chưa phải chương trình độc lập.
// Chính sách: không chuyển ngay được thì chấp nhận xếp hàng để nhận sau.
if (!queue.tryTransfer(item)) {
    queue.put(item);
}
```

Phải hiểu chính sách ở nhánh sau: nó từ bỏ yêu cầu nhận ngay và cho phép giữ dữ liệu. Đây không còn là một quy tắc “không có bên nhận thì từ chối”.

Nếu cần ưu tiên, hạn thời gian hoặc giới hạn dữ liệu tồn, nhánh dự phòng phải đáp ứng những yêu cầu ấy thay vì chỉ bảo đảm lời gọi có vẻ thành công.

## 7. Dung lượng, áp lực ngược và khả năng tiến triển

### 7.1. SynchronousQueue buộc bên gửi gặp bên nhận, nhưng chưa giới hạn mọi tài nguyên

Khi bên sản xuất gọi `put`, nó không thể gửi trước nhiều phần tử vào một vùng đệm nội bộ. Đây là điểm chuyển giao buộc nguồn phối hợp với bên nhận.

Tuy nhiên, nếu ứng dụng liên tục tạo tác vụ hoặc luồng sản xuất mới, mỗi tác vụ đang chờ vẫn có thể giữ một đối tượng công việc. Công việc tồn đã chuyển từ một hàng đợi phần tử sang các tác vụ bị giữ.

Vì vậy, không thể kết luận “capacity bằng 0 nên hệ thống không thể tích lũy dữ liệu”. Cần kiểm soát cả số bên gửi hoạt động, kích thước công việc và số việc đang xử lý.

### 7.2. LinkedTransferQueue không có giới hạn capacity mặc định để chống tích lũy

Gửi qua `put` hoặc `offer` trên `LinkedTransferQueue` cho phép nguồn đi trước bên nhận. Nếu nguồn nhanh hơn trong thời gian dài, dữ liệu chờ có thể tăng cho đến khi tài nguyên không còn đáp ứng.

Dùng `transfer` khiến một bên sản xuất chờ được nhận cho từng lần gửi. Nhưng nếu có rất nhiều bên sản xuất độc lập, hoặc bên tiêu thụ lấy xong lại đẩy công việc sang nơi khác không giới hạn, tổng tải chưa chắc đã được giới hạn theo ý định.

Muốn giới hạn số công việc chưa hoàn tất đến lúc xử lý xong, ranh giới cấp quyền phải kéo dài qua cả giai đoạn xử lý và hoàn tất. Chỉ điều kiện “đã nhận” là một ranh giới sớm hơn.

### 7.3. Ai có thể làm lời gọi đang chờ hoàn tất?

Một thiết kế có thể không tiến triển nếu gửi và nhận bị xếp sai tài nguyên. Ví dụ suy luận:

```text
Nhóm thực thi chỉ có một luồng.
Tác vụ gửi bắt đầu: transfer(work) → chờ người nhận.
Tác vụ nhận được xếp sau trong cùng nhóm.
Không còn luồng rảnh để chạy tác vụ nhận.
```

Thay `transfer` bằng `SynchronousQueue.put` vẫn có cùng quan hệ chờ. Cần cho đối tác có thể chạy, hoặc đổi giao thức.

Một tình huống khác: bên gửi giữ khóa L rồi chờ người nhận, trong khi người nhận phải lấy khóa L mới đi đến lời gọi nhận. Cấu trúc chuyển giao không thể tự giải phóng khóa ngoài của ứng dụng.

Trước khi chọn một phương thức chờ, cần xác định đối tác nào có thể xuất hiện, đối tác đó cần tài nguyên gì và liệu những tài nguyên ấy có bị bên chờ giữ hay không.

## 8. Thứ tự, tính nhìn thấy dữ liệu và vòng đời

### 8.1. Đừng gán một thứ tự toàn cục chưa được hứa

`SynchronousQueue` có tùy chọn công bằng khi tạo. Bật cờ cho chính sách cấp quyền FIFO cho các bên chờ theo hợp đồng lớp; mặc định không hứa thứ tự công bằng đó.

Điều này không làm mọi bên xử lý hoàn tất theo thứ tự đã nhận. Hai công việc có độ dài xử lý khác nhau vẫn có thể hoàn tất ngược nhau.

Với `LinkedTransferQueue`, hợp đồng mô tả FIFO đối với từng bên sản xuất. Trong dữ liệu A, B, C của chương trình chỉ có một bên gửi nên thứ tự dễ kiểm chứng. Với nhiều bên gửi, không suy ra thứ tự số mã, thời gian nghiệp vụ hay thứ tự hoàn tất từ một danh sách kết quả quan sát được.

Nếu thứ tự toàn cục là yêu cầu nghiệp vụ, hãy định nghĩa mốc và cách xác lập nó; không chỉ dựa vào tên queue.

### 8.2. Điểm chuyển giao bảo đảm tính nhìn thấy theo chiều gửi sang nhận

Các hành động trước khi đặt đối tượng vào hàng đợi đồng thời có quan hệ happens-before với hành động sau khi luồng khác truy cập hoặc lấy phần tử đó. Bên gửi nên hoàn tất xây dựng dữ liệu trước khi chuyển giao. Xem [bảo đảm bộ nhớ của BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).

Hàng đợi chuyển tham chiếu, không tự sao chép sâu. Nếu bên gửi tiếp tục sửa dữ liệu sau khi gửi và bên nhận cùng đọc hoặc sửa, cần quy tắc sở hữu hay đồng bộ cho chính dữ liệu đó.

Không suy ra chiều ngược rằng mọi cập nhật trong phần xử lý của bên nhận đã được bên gửi nhìn thấy chỉ vì `transfer` kết thúc. Phần xử lý có thể còn chưa chạy.

Trong ví dụ phản hồi, future là một cơ chế riêng để công bố kết quả hoàn tất. Hành động của tác vụ có quan hệ happens-before với hành động sau khi lấy kết quả bằng `Future.get()`. Xem [bảo đảm của Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html).

### 8.3. Quan sát LinkedTransferQueue cũng không tạo ảnh chụp giao dịch

`size()` của `LinkedTransferQueue` cần duyệt và không nên được xem như một con số giữ chỗ chính xác trong lúc dữ liệu thay đổi. Iterator có tính nhất quán yếu; các thao tác xử lý nhiều phần tử không tự thành một giao dịch chung.

Cần quan sát đúng mốc: số dữ liệu đang chờ nhận, số bên tiêu thụ đang đợi, số lần gửi đang chờ và số công việc đang xử lý là những đại lượng khác nhau.

Riêng `SynchronousQueue.size()` luôn 0 nên dùng đồ thị số phần tử xếp hàng sẽ không thể hiện các bên gửi đang chờ. Một chỉ số có vẻ “hàng đợi rỗng” không chứng minh hệ thống rảnh.

### 8.4. Nguồn kết thúc phải có giao thức riêng

Hai dạng cấu trúc không tự có một thao tác đóng nguồn chung cho mọi ứng dụng. Có thể cần gián đoạn, tín hiệu kết thúc hoặc một cơ chế quản lý vòng đời rõ.

Với `SynchronousQueue`, tín hiệu kết thúc cũng phải được ghép với người nhận. Nếu người nhận đã dừng, cố `put` tín hiệu không giới hạn có thể làm bên điều phối chờ tiếp.

Với nhiều bên tiêu thụ, một tín hiệu chỉ được một bên nhận. Phải quyết định số tín hiệu, thời điểm mọi bên sản xuất đã hoàn tất và cách xử lý nếu một bên đã hỏng.

Với công việc có future phản hồi, quá trình dừng còn phải giải quyết các phản hồi chưa hoàn tất. Không chỉ làm tác vụ nhận thoát rồi để người gửi chờ mãi.

## 9. Chọn theo mốc cần xác nhận

| Nhu cầu | Hướng phù hợp để xem xét | Điều vẫn cần thêm |
|---|---|---|
| Chuyển trực tiếp, không giữ phần tử trong vùng đệm giữa hai bên | `SynchronousQueue` | Đối tác phải có khả năng chạy; chính sách hủy/dừng |
| Đôi khi chỉ xếp hàng, đôi khi chờ được nhận | `TransferQueue` với cài đặt phù hợp, chẳng hạn `LinkedTransferQueue` | Chọn đúng phương thức và giới hạn tải ở mức ứng dụng |
| Chỉ chấp nhận khi chuyển ngay được cho bên đang chờ | `tryTransfer(e)`, hoặc `SynchronousQueue.offer(e)` theo cấu trúc đang dùng | Xử lý rõ nhánh thất bại |
| Chờ người nhận trong một khoảng | `tryTransfer` có timeout, hoặc timed `offer` của SynchronousQueue | Ngân sách thời gian và nhánh gián đoạn |
| Cần dữ liệu được xếp chờ với dung lượng hữu hạn | Một hàng đợi có giới hạn phù hợp | Chính sách đầy và vận hành |
| Cần kết quả xử lý | Giao thức phản hồi, ví dụ future trong thông điệp | Hoàn tất cả đường thành công, lỗi, hủy |
| Cần độ bền sau khi tiến trình dừng | Nguồn lưu trữ hoặc hệ thống thông điệp phù hợp | Quy tắc xác nhận, phục hồi, xử lý lặp |

Bảng không coi một lớp là nhanh hơn hoặc mạnh hơn trong mọi bối cảnh. Cơ chế đúng là cơ chế có mốc hoàn tất khớp với điều bên gọi thực sự cần biết.

Một vài lỗi gọi API nên nhận ra ngay:

- Gọi `offer` có timeout trên `LinkedTransferQueue` rồi tưởng đã chờ người nhận.
- Gọi `tryTransfer` thất bại nhưng tưởng phần tử vẫn được giữ để nhận sau.
- Dùng `hasWaitingConsumer` như một quyền giữ trước người nhận.
- Coi `transfer` hoàn tất là bằng chứng xử lý đã thành công.
- Coi `size=0` của `SynchronousQueue` là hệ thống không có tác vụ đang chờ.
- Dừng hoặc gửi lại công việc mà không biết nó đã được nhận hay chưa.

Những lỗi này chủ yếu là nhầm trạng thái và trách nhiệm. Tên phương thức chỉ hữu ích khi được nối với đúng mốc trong quy trình.

## 10. Thực hành và tự kiểm tra

### Bài 1: “rỗng” nhưng gửi không được

Tạo một `SynchronousQueue`, gọi `size`, `isEmpty`, rồi thử `offer("A")` khi không có bên nhận.

Vì sao `size=0`, `isEmpty=true` nhưng `offer` lại trả `false`? Nếu thay bằng hàng đợi có vùng đệm còn chỗ, điều kiện nào đã khác?

Hướng giải thích: `SynchronousQueue` không có ô lưu trữ để giữ A. Sự hiện diện của đối tác nhận, không phải số ô còn trống, là điều kiện chuyển giao.

### Bài 2: chọn đúng phép gửi trên LinkedTransferQueue

Không có bên tiêu thụ. Hãy dự đoán sự khác nhau giữa:

1. `put("A")`.
2. `offer("B", 1, SECONDS)`.
3. `tryTransfer("C")`.
4. `tryTransfer("D", 1, SECONDS)`.

Hướng giải thích: A và B có thể được xếp để nhận sau; C không được xếp khi chuyển ngay thất bại; D chờ khả năng chuyển trong một khoảng rồi không bị giữ lại nếu hết thời gian theo hợp đồng lớp.

Không chạy `transfer` không timeout trên cùng một luồng rồi mong đoạn mã phía sau tự đi đến `take`; luồng ấy sẽ không tạo được đối tác cho chính nó.

### Bài 3: nhận chưa là xử lý xong

Giữ cổng xử lý đóng như `ReceiptAndReplyDemo`. Bên gửi đã hoàn tất `transfer`.

Có thể đọc kết quả xử lý từ một biến thường và coi như chắc chắn đã có giá trị mới không? Cơ chế nào trong ví dụ được dùng để công bố phần hoàn tất thực sự?

Hướng giải thích: phần xử lý vẫn chưa được cho phép bắt đầu. Future phản hồi mô hình hóa mốc kết quả riêng; điểm chuyển giao không thay thế mốc đó.

### Bài 4: cuộc đua sau hasWaitingConsumer

Bên P1 thấy `hasWaitingConsumer=true`. Trước khi P1 gửi, P2 chuyển phần tử cho người nhận đó.

Vì sao P1 vẫn có thể chờ nếu gọi `transfer`? Nếu yêu cầu của P1 là chỉ thử chuyển ngay thì phải dùng kết quả của thao tác nào?

Hướng giải thích: quan sát không đặt chỗ. Dùng `tryTransfer` để quyết định chuyển được ngay hay không trong chính thao tác cần thực hiện.

### Bài 5: dừng một quy trình có phản hồi

Nhiều công việc đã được nhận nhưng chưa hoàn tất future. Bên quản lý yêu cầu dừng các bên tiêu thụ.

Cần xử lý gì với những người đang đợi kết quả? Nếu một người đã hết thời gian chờ, có thể tự suy ra công việc đã ngừng chạy không? Nếu gửi lại, làm thế nào nhận diện lần thực hiện trước?

Hướng giải thích: phải quản lý vòng đời xử lý và phản hồi, bao gồm lỗi/hủy. Timeout của bên chờ chưa là xác nhận dừng công việc. Nhận diện và xử lý lặp thuộc giao thức nghiệp vụ.

### Các câu hỏi trước khi chốt một thiết kế chuyển giao

1. Bên gửi cần biết được chấp nhận, được nhận hay xử lý xong?
2. Cấu trúc có vùng đệm không; phương thức đang gọi có chờ việc nhận không?
3. Nếu chuyển thất bại, phần tử còn được giữ ở đâu?
4. Đối tác cần tài nguyên và luồng nào để có thể xuất hiện?
5. Kết quả hết thời gian và gián đoạn được xử lý khác nhau thế nào?
6. Các số đo size hoặc số người chờ có đang bị dùng như quyền giữ chỗ không?
7. Sau khi nhận, ai được phép sửa dữ liệu và ai công bố kết quả?
8. Khi dừng, ai chịu trách nhiệm kết thúc các lần gửi và phản hồi còn chờ?

Khi ba mốc chấp nhận, chuyển giao và xử lý hoàn tất được tách rõ, người học có thể chọn đúng phương thức và giải thích được cả thành công, timeout, hủy lẫn đường dừng của quy trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-queues-008">BlockingDeque, ngắt luồng và quy ước kết thúc luồng xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# BlockingDeque, ngắt luồng và quy ước kết thúc luồng xử lý

## Từ hàng đợi hai đầu đến vòng đời của luồng

Một dịch vụ nhận việc thường có hai nhóm luồng: bên sản xuất tạo công việc và bên tiêu thụ lấy công việc để xử lý. Hàng đợi giải quyết việc bàn giao giữa hai nhóm. Nhưng khi dịch vụ cần dừng, một câu hỏi khác xuất hiện: luồng tiêu thụ đang chờ việc sẽ được đánh thức bằng cách nào, và các việc còn lại sẽ đi đâu?

`BlockingDeque<E>` bổ sung khả năng chờ vào hàng đợi hai đầu. Luồng có thể thêm hoặc lấy phần tử ở đầu trước (`first`) hay đầu sau (`last`); một số phương thức chờ khi hàng đợi đầy hoặc rỗng. Khả năng này giúp xây dựng luồng xử lý có điều tiết và chọn hướng lấy việc. Nó cũng tạo ra những điểm chờ cần được đưa vào thiết kế kết thúc.

Cần phân biệt bốn sự kiện:

1. Công việc được chấp nhận vào hàng đợi.
2. Công việc được một luồng lấy ra.
3. Phần xử lý công việc hoàn thành.
4. Luồng tiêu thụ thực sự kết thúc.

Bốn sự kiện này không đồng nghĩa. Hàng đợi rỗng chỉ nói rằng không còn phần tử đang nằm trong hàng đợi tại thời điểm quan sát; một luồng vẫn có thể đang xử lý phần tử đã lấy. Tương tự, gọi `interrupt()` là đưa ra yêu cầu ngắt, không phải chứng nhận luồng đã kết thúc.

Trong bài này, `producer` là bên sản xuất, `consumer` là bên tiêu thụ, `interrupt` là yêu cầu ngắt có sự hợp tác, còn `end message` là thông điệp kết thúc do ứng dụng tự quy ước. Hiểu từng khái niệm trước, sau đó ghép chúng thành một quy trình dừng có thể kiểm chứng.

## Hai đầu, bốn cách xử lý khi chưa thể hoàn thành

`BlockingDeque` kế thừa cả `Deque` và `BlockingQueue`. Với thao tác thêm hoặc lấy, tên phương thức thể hiện cách ứng xử khi điều kiện chưa đáp ứng:

| Mục đích | Ném ngoại lệ nếu không làm được ngay | Trả giá trị báo thất bại ngay | Chờ đến khi có thể làm | Chờ có thời hạn |
| --- | --- | --- | --- | --- |
| Thêm đầu trước | `addFirst(e)` | `offerFirst(e)` | `putFirst(e)` | `offerFirst(e, timeout, unit)` |
| Thêm đầu sau | `addLast(e)` | `offerLast(e)` | `putLast(e)` | `offerLast(e, timeout, unit)` |
| Lấy đầu trước | `removeFirst()` | `pollFirst()` | `takeFirst()` | `pollFirst(timeout, unit)` |
| Lấy đầu sau | `removeLast()` | `pollLast()` | `takeLast()` | `pollLast(timeout, unit)` |

Thêm thất bại vì đầy: `addFirst/addLast` ném `IllegalStateException`, còn `offerFirst/offerLast` trả `false`. Lấy thất bại vì rỗng: `removeFirst/removeLast` ném `NoSuchElementException`, còn `pollFirst/pollLast` trả `null`. Phiên bản có thời hạn của `offer` trả `false` nếu hết thời gian chờ chỗ; phiên bản có thời hạn của `poll` trả `null` nếu hết thời gian chờ phần tử.

Các phương thức `put`, `take` và phiên bản chờ có thời hạn khai báo `InterruptedException`. `peekFirst/peekLast` chỉ xem, trả `null` khi rỗng; `getFirst/getLast` chỉ xem, ném ngoại lệ khi rỗng. Không có phương thức xem đầu hàng đợi rồi chờ cho đến khi xuất hiện phần tử.

Đây là hợp đồng của [BlockingDeque trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingDeque.html). Hãy chọn cả hướng thao tác lẫn chính sách chờ; chọn đúng một trong hai vẫn chưa đủ.

### Những tên không có First hoặc Last dùng đầu nào?

Khi được dùng như `BlockingQueue`, hàng đợi thêm ở cuối và lấy ở đầu:

~~~text
put(e), offer(e), add(e)      -> thêm Last
take(), poll(), remove()     -> lấy First
peek(), element()            -> xem First
~~~

Do đó `putLast` kết hợp `takeFirst` tạo cách phục vụ vào trước ra trước, thường gọi là FIFO. `putFirst` kết hợp `takeFirst` lại đưa phần tử mới lên trước phần tử cũ, giống cách dùng ngăn xếp.

`push(e)` tương đương thêm đầu trước theo kiểu ném ngoại lệ nếu đầy; `pop()` tương đương lấy đầu trước theo kiểu ném ngoại lệ nếu rỗng. Việc đối tượng có kiểu `BlockingDeque` không biến `push/pop` thành phương thức chờ. Muốn chờ, phải chọn `putFirst/takeFirst` rõ ràng.

Trong một quy trình cần giữ thứ tự, việc cho phép các đoạn mã tùy ý dùng cả hai đầu làm thay đổi ý nghĩa của thứ tự đó. Chẳng hạn, quy tắc “thông điệp kết thúc được thêm sau mọi công việc” không còn đủ nếu một consumer khác lấy từ đầu sau.

### LinkedBlockingDeque và dung lượng chung

Một cách khởi tạo rõ ràng:

~~~java
BlockingDeque<String> inbox = new LinkedBlockingDeque<>(100);
~~~

`100` là giới hạn tổng số phần tử của cả deque, không phải mỗi đầu được 100 phần tử. Nếu đã có 100 phần tử, `putFirst` và `putLast` đều phải chờ chỗ. Chuyển từ thêm cuối sang thêm đầu không vượt qua được giới hạn.

Nếu dùng hàm tạo không tham số, `LinkedBlockingDeque` có giới hạn `Integer.MAX_VALUE`. Trong thực tế, bộ nhớ có thể cạn từ lâu trước khi đạt số phần tử đó. Vì vậy, khi muốn tạo điều tiết ngược tới bên sản xuất, hãy chọn dung lượng có chủ ý. Các đặc điểm này được mô tả ở [LinkedBlockingDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html).

Dung lượng giới hạn phần tử đang chờ, không giới hạn số byte của từng phần tử, không tính toàn bộ dữ liệu producer giữ bên ngoài, và không tính các việc consumer đã lấy ra nhưng chưa xử lý xong. Nếu một công việc giữ mảng lớn hoặc kích hoạt nhiều tác vụ khác, chỉ nhìn `size()` sẽ đánh giá thiếu lượng tài nguyên đang sử dụng.

## Vì sao một vòng lặp lấy việc có thể không bao giờ thoát?

Xét đoạn minh họa, giả sử `process` là phương thức xử lý của ứng dụng:

~~~java
for (;;) {
    Job job = inbox.takeFirst();
    process(job);
}
~~~

Khi không còn việc, `takeFirst()` chờ. Không có suy luận tự động nào rằng “đã rỗng lâu, chắc là hết dữ liệu”. Hàng đợi không biết producer sẽ gửi việc tiếp sau một giây hay không bao giờ gửi nữa.

`BlockingQueue` không có thao tác `close/shutdown` nội tại để tuyên bố hết dữ liệu; ứng dụng cần một quy ước riêng. `BlockingDeque` cũng cần quy ước ấy khi được dùng như kênh truyền việc. Xem [hợp đồng BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).

Có hai ý định dừng khác nhau:

| Ý định | Điều cần xảy ra | Cơ chế thường dùng |
| --- | --- | --- |
| Kết thúc có trật tự | Ngừng nhận việc mới, xử lý hết các việc đã chấp nhận theo chính sách, rồi consumer thoát | Thông điệp kết thúc hoặc kênh có vòng đời do ứng dụng quản lý |
| Hủy sớm | Yêu cầu consumer bỏ chờ hoặc ngừng phần xử lý khi có thể; xử lý số việc còn lại theo chính sách hủy | Ngắt luồng, cờ hủy có đồng bộ, thao tác đóng tài nguyên phù hợp |

Nếu yêu cầu là xử lý hết việc đã nhận, không thể chỉ ngắt ngay mọi consumer rồi tuyên bố thành công. Nếu yêu cầu là dừng khẩn cấp, cũng không thể mặc định chờ hàng triệu công việc trước một thông điệp kết thúc.

Tên hàm `stop` của ứng dụng phải thể hiện được ý định nào đang được thực hiện: dừng nhận, yêu cầu hủy, hay chờ xác nhận hoàn tất.

## Ngắt luồng là giao thức hợp tác

Mỗi luồng có trạng thái ngắt. Gọi `target.interrupt()` yêu cầu luồng `target` chú ý tới việc ngắt. Phần mã đang chạy quyết định phản ứng theo hợp đồng của thao tác nó đang thực hiện.

Đối với chỗ chờ có thể ngắt như chờ lấy từ deque rỗng hoặc chờ thêm vào deque đầy, `InterruptedException` cho phép mã rời khỏi chỗ chờ. Đối với vòng tính toán thuần CPU không kiểm tra ngắt, cờ được đặt không tự làm vòng lặp dừng. Một thao tác I/O cũng phải xét đúng API; không được suy luận rằng mọi kiểu đọc mạng hoặc đọc tệp đều ném `InterruptedException` khi gọi `interrupt()`.

Ba phương thức dễ nhầm:

| Phương thức | Luồng được kiểm tra hoặc yêu cầu | Có xóa trạng thái ngắt khi kiểm tra? |
| --- | --- | --- |
| `target.interrupt()` | Luồng `target` | Không phải phương thức kiểm tra |
| `target.isInterrupted()` | Luồng `target` | Không |
| `Thread.interrupted()` | Luồng hiện tại đang gọi | Có |

`Thread.interrupted()` là phương thức `static`. Viết qua một biến `Thread` không biến nó thành phép kiểm tra luồng mà biến đó trỏ tới. Khi chỉ muốn quan sát cờ, dùng `isInterrupted()`.

Các quy tắc của trạng thái ngắt và sự khác nhau giữa các điểm chờ nằm trong [Thread của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#interrupt()).

### Bắt InterruptedException rồi phải quyết định điều gì?

Khi một chỗ chờ báo `InterruptedException` theo cơ chế ngắt thông thường, trạng thái ngắt đã được xóa khi ngoại lệ được giao cho mã bắt. Do đó, kiểm tra `isInterrupted()` ngay trong `catch` có thể nhận `false` dù nguyên nhân đi vào `catch` chính là yêu cầu ngắt. Chương trình đầy đủ phía dưới kiểm chứng trường hợp này bằng deque luôn rỗng.

Nếu phương thức cho phép truyền ngoại lệ, cách trực tiếp là để bên gọi quyết định:

~~~java
static String receive(BlockingDeque<String> inbox)
        throws InterruptedException {
    return inbox.takeFirst();
}
~~~

Phương thức không nuốt tín hiệu; bên gọi nhận `InterruptedException` và phải xử lý.

Nếu đang ở biên `Runnable.run()`, không thể khai báo thêm checked exception. Một mẫu dừng hợp tác là khôi phục cờ rồi thoát khỏi `run`. Đoạn sau là minh họa cấu trúc; `Job` và `process` thuộc ứng dụng:

~~~java
try {
    for (;;) {
        Job job = inbox.takeFirst();
        process(job);
    }
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} finally {
    releaseOwnedResources();
}
~~~

Việc khôi phục giúp phần gọi hoặc phần dọn tài nguyên còn cơ hội quan sát trạng thái ngắt. Việc thoát mới làm vòng tiêu thụ kết thúc. Hai hành động có vai trò khác nhau.

Nếu phương thức chuyển `InterruptedException` lên trên thì không cần bắt chỉ để khôi phục rồi lại ném như một quy tắc máy móc. Nếu một thành phần chủ động tiêu thụ tín hiệu ngắt và tiếp tục, nó phải có chính sách được thiết kế rõ, không phải vì muốn bỏ thông báo lỗi.

### Hai cách bắt lỗi làm hỏng ý định dừng

Cách thứ nhất là bắt rồi lặp tiếp, không giữ lại dấu vết:

~~~java
for (;;) {
    try {
        handle(inbox.takeFirst());
    } catch (InterruptedException ex) {
        System.err.println("Interrupted while waiting");
    }
}
~~~

Đây là đoạn minh họa lỗi. Sau `catch`, luồng quay lại `takeFirst`. Nếu deque vẫn rỗng và không có yêu cầu ngắt mới, nó có thể chờ mãi. Dòng log không thay thế hành động dừng.

Cách thứ hai là khôi phục cờ nhưng vẫn cố tiếp tục cùng vòng chờ:

~~~java
for (;;) {
    try {
        handle(inbox.takeFirst());
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
    }
}
~~~

Đoạn này cũng là minh họa lỗi đối với chính sách “ngắt thì thoát”. Khi lại gặp chỗ chờ kiểm tra ngắt, nó có thể lặp bắt ngoại lệ rồi khôi phục liên tục, tiêu tốn CPU hoặc sinh log dồn dập. Khôi phục cờ không tự làm câu lệnh `for (;;)` dừng.

Nếu vòng lặp dùng `while (!Thread.currentThread().isInterrupted())`, việc phục hồi cờ có thể làm lần kiểm tra sau kết thúc vòng. Tuy nhiên, vẫn cần xem mọi nhánh điều khiển và mã xử lý việc; một `return` tại biên dừng thường thể hiện ý định rõ hơn.

### Ngắt không hoàn tác việc đã làm

Giả sử consumer đã lấy công việc, ghi một phần kết quả vào cơ sở dữ liệu, rồi bị ngắt khi đang chờ bước tiếp theo. Deque không đưa công việc trở lại và không hoàn tác bản ghi. Tương tự, ngắt producer không xóa những phần tử producer đã gửi thành công trước đó.

Nếu yêu cầu ngắt đua với việc một thao tác hoàn thành, không được kết luận rằng “đã gọi interrupt nên chắc chắn không có phần tử nào được lấy”. Hãy xử lý dựa trên kết quả thật sự của thao tác và trạng thái công việc mà luồng đang sở hữu.

Những yêu cầu như không mất việc, xử lý đúng một lần, hay phục hồi sau tiến trình bị tắt cần giao thức xác nhận, lưu trữ bền vững, tính lặp an toàn hoặc giao dịch phù hợp. Hàng đợi trong bộ nhớ và cờ ngắt không cung cấp các bảo đảm ấy.

## Thông điệp kết thúc: một phần của dữ liệu điều khiển

Một thông điệp kết thúc là phần tử có ý nghĩa “luồng dữ liệu này đã hết việc theo quy ước”. Consumer lấy nó như lấy một phần tử bình thường, nhận ra loại thông điệp rồi thoát.

Không dùng `null` làm dấu kết thúc: deque không chấp nhận phần tử `null`, còn `poll` dùng `null` để báo không lấy được phần tử. Một chuỗi như `"STOP"` cũng dễ đụng dữ liệu thật nếu công việc vốn là văn bản.

Có thể tách loại ngay trong hệ kiểu:

~~~java
sealed interface Message permits Item, End {}

record Item(int number) implements Message {}

enum End implements Message {
    INSTANCE
}
~~~

`Item` mang dữ liệu, `End` mang ý nghĩa điều khiển. Consumer có thể kiểm tra `message == End.INSTANCE` vì đó là hằng `enum`. Không cần một giá trị số đặc biệt như `-1` phải được mọi người nhớ để tránh dùng cho việc thật.

Đối tượng `End.INSTANCE` có thể được thêm nhiều lần vào deque: mỗi lần thêm là một vị trí phần tử riêng, dù cùng tham chiếu. Điều này hữu ích khi cần một thông điệp cho mỗi consumer.

### Đặt dấu kết thúc ở đâu?

Giả sử mọi công việc đều được `putLast` và mọi consumer đều dùng `takeFirst`:

~~~text
First [Item(1), Item(2), End] Last
~~~

Một consumer đi qua các phần tử theo hướng này sẽ lấy công việc trước `End`. Nếu thay bằng `putFirst(End)`:

~~~text
First [End, Item(1), Item(2)] Last
~~~

Consumer có thể thoát ngay và để lại việc. Đây là thay đổi chính sách từ xử lý hết sang vượt qua việc tồn, không phải tối ưu thao tác dừng.

Ngay cả đặt `End` ở cuối cũng chưa đủ nếu vẫn còn producer có thể gửi sau nó:

~~~text
First [Item(1), End, Item(2)] Last
~~~

Bài học không phải là chọn một tên phương thức rồi coi bài toán đã xong. Cần đóng được giai đoạn sản xuất trước khi phát dấu kết thúc, đồng thời thống nhất hướng lấy trong toàn bộ các consumer.

### Một dấu kết thúc có đánh thức tất cả consumer không?

Không. Với ba consumer đều thoát khi lấy `End`, một phần tử `End` chỉ được một consumer lấy. Hai consumer còn lại có thể tiếp tục chờ.

Một giao thức dễ kiểm tra là gửi đúng một dấu cho mỗi consumer đang tham gia giai đoạn kết thúc. Consumer nhận dấu thì thoát ngay và không lấy dấu thứ hai. Trong ví dụ có hai consumer, producer gửi hai dấu.

Có những thiết kế chuyền lại một dấu cho consumer khác, nhưng phải chứng minh không mất dấu, không phát lại sai vòng đời và không treo khi deque đầy. Nếu chưa cần cơ chế ấy, nhiều dấu đơn giản hơn. Nếu số consumer thay đổi động, việc đếm không thể là một số cứng nằm ngoài cơ chế quản lý vòng đời.

### Dấu kết thúc đi qua hàng đợi đầy như thế nào?

Thông điệp điều khiển cũng chiếm dung lượng. Với deque đầy, `putLast(End.INSTANCE)` chờ giống như khi gửi việc.

Nếu consumer vẫn hoạt động và đang xử lý bình thường, chờ chỗ có thể là điều chấp nhận được. Nếu mọi consumer đã hỏng hoặc bị hủy, chờ vô hạn sẽ biến thủ tục dừng thành điểm treo.

Một ứng dụng cần giới hạn thời gian có thể dùng `offerLast(End.INSTANCE, timeout, unit)`. Khi trả `false`, ứng dụng phải xử lý thất bại thật sự: ghi nhận chưa kết thúc có trật tự, yêu cầu hủy phù hợp, kiểm tra lỗi consumer, và chờ xác nhận. Không được bỏ qua `false` rồi tiếp tục báo đã dừng.

Chỉ thêm thời hạn cho thao tác gửi không bảo đảm cả quy trình có cùng thời hạn. Nếu gửi nhiều dấu, mỗi lần chờ một thời hạn riêng sẽ cộng dồn thời gian. Khi yêu cầu là hạn chót chung, hãy theo dõi ngân sách thời gian còn lại.

## Chương trình đầy đủ: hai đầu, ngắt và kết thúc có trật tự

Lưu toàn bộ mã dưới đây vào `DequeTerminationDemo.java`. Chương trình dùng cú pháp Java 17, không cần thư viện ngoài. Mã được biên dịch với `--release 17` và đã chạy bằng OpenJDK Zulu 24.0.2; kết quả dưới đây là stdout của lần chạy đó.

Năm phần kiểm chứng:

1. Thao tác hai đầu, các tên kiểu queue và các tên kiểu stack.
2. Ngắt một consumer đang chuẩn bị chờ trên deque rỗng; quan sát cờ và dọn tài nguyên.
3. Ngắt một producer trên deque luôn đầy; phần tử mới không được chèn.
4. Cho dấu kết thúc vào đầu trước để thấy việc còn bị bỏ lại.
5. Dùng hai dấu ở cuối để hai consumer xử lý hết sáu việc rồi thoát.

Chương trình dùng `CountDownLatch` làm mốc phối hợp, không dùng `Thread.sleep` để đoán lịch chạy. Latch `ready` chỉ chứng minh luồng con đã đi tới đoạn ngay trước thao tác deque; nó không khẳng định luồng chắc chắn đang ở trạng thái `WAITING`. Kết quả vẫn xác định vì không có luồng nào đưa phần tử vào deque rỗng hay lấy khỏi deque đầy của hai phép thử ngắt.

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DequeTerminationDemo {
    sealed interface Message permits Item, End {}
    record Item(int number) implements Message {}
    enum End implements Message { INSTANCE }

    static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Latch timed out");
        }
    }

    static Thread start(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    static void join(Thread thread, AtomicReference<Throwable> failure)
            throws InterruptedException {
        thread.join(5000);
        if (thread.isAlive()) {
            thread.interrupt();
            throw new AssertionError("Thread did not terminate: " + thread.getName());
        }
        if (failure.get() != null) {
            throw new AssertionError("Background failure", failure.get());
        }
    }

    static void ends() throws InterruptedException {
        BlockingDeque<String> deque = new LinkedBlockingDeque<>(3);
        deque.putLast("B");
        deque.putFirst("A");
        deque.putLast("C");
        System.out.println("two-ends-content = " + deque);
        System.out.println("take-first = " + deque.takeFirst());
        System.out.println("take-last = " + deque.takeLast());
        deque.put("D");
        System.out.println("queue-put-content = " + deque);
        System.out.println("queue-take = " + deque.take());
        deque.push("X");
        System.out.println("stack-pop = " + deque.pop());
        deque.clear();
        System.out.println("timed-poll-empty = "
                + deque.pollLast(0, TimeUnit.MILLISECONDS));
        try {
            deque.pop();
        } catch (NoSuchElementException ex) {
            System.out.println("empty-pop = " + ex.getClass().getSimpleName());
        }

        BlockingDeque<String> one = new LinkedBlockingDeque<>(1);
        one.putLast("held");
        System.out.println("other-end-offer-full = " + one.offerFirst("urgent"));
        System.out.println("other-end-take = " + one.takeLast());
    }

    static void interruptTake() throws InterruptedException {
        BlockingDeque<String> empty = new LinkedBlockingDeque<>(1);
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean caught = new AtomicBoolean();
        AtomicBoolean insideCatch = new AtomicBoolean(true);
        AtomicBoolean afterRestore = new AtomicBoolean();
        AtomicBoolean cleaned = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread taker = start("waiting-taker", () -> {
            ready.countDown();
            try {
                empty.takeFirst();
                failure.set(new AssertionError("Unexpected item"));
            } catch (InterruptedException ex) {
                caught.set(true);
                insideCatch.set(Thread.currentThread().isInterrupted());
                Thread.currentThread().interrupt();
                afterRestore.set(Thread.currentThread().isInterrupted());
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                cleaned.set(true);
            }
        });
        await(ready);
        taker.interrupt();
        join(taker, failure);
        System.out.println("take-interrupted = " + caught.get());
        System.out.println("status-inside-catch = " + insideCatch.get());
        System.out.println("status-after-restore = " + afterRestore.get());
        System.out.println("cleanup-ran = " + cleaned.get());
        System.out.println("taker-terminated = " + !taker.isAlive());

        List<Boolean> flags = new ArrayList<>();
        Thread probe = start("flag-probe", () -> {
            Thread.currentThread().interrupt();
            flags.add(Thread.currentThread().isInterrupted());
            flags.add(Thread.interrupted());
            flags.add(Thread.currentThread().isInterrupted());
        });
        join(probe, failure);
        System.out.println("flag-probe = " + flags);
    }

    static void interruptPut() throws InterruptedException {
        BlockingDeque<String> full = new LinkedBlockingDeque<>(1);
        full.putLast("kept");
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean caught = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread producer = start("waiting-producer", () -> {
            ready.countDown();
            try {
                full.putFirst("not-inserted");
                failure.set(new AssertionError("Unexpected insertion"));
            } catch (InterruptedException ex) {
                caught.set(true);
                Thread.currentThread().interrupt();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        await(ready);
        producer.interrupt();
        join(producer, failure);
        System.out.println("put-interrupted = " + caught.get());
        System.out.println("after-producer-interrupt = " + full);
    }

    static void markerAtWrongEnd() throws InterruptedException {
        BlockingDeque<Message> deque = new LinkedBlockingDeque<>(3);
        deque.putLast(new Item(1));
        deque.putLast(new Item(2));
        deque.putFirst(End.INSTANCE);
        System.out.println("front-stop-taken-first = "
                + (deque.takeFirst() == End.INSTANCE));
        System.out.println("work-left-after-front-stop = " + deque.size());
    }

    static void graceful() throws InterruptedException {
        BlockingDeque<Message> inbox = new LinkedBlockingDeque<>(2);
        ConcurrentLinkedQueue<Integer> completed = new ConcurrentLinkedQueue<>();
        AtomicInteger endMessages = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable consume = () -> {
            try {
                for (;;) {
                    Message message = inbox.takeFirst();
                    if (message == End.INSTANCE) {
                        endMessages.incrementAndGet();
                        return;
                    }
                    completed.add(((Item) message).number());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            }
        };
        Thread first = start("consumer-1", consume);
        Thread second = start("consumer-2", consume);
        boolean sentAll = false;
        try {
            for (int n = 1; n <= 6; n++) {
                if (!inbox.offerLast(new Item(n), 5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Could not send item");
                }
            }
            for (int n = 0; n < 2; n++) {
                if (!inbox.offerLast(End.INSTANCE, 5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Could not send end message");
                }
            }
            sentAll = true;
        } finally {
            if (!sentAll) {
                first.interrupt();
                second.interrupt();
            }
        }
        join(first, failure);
        join(second, failure);
        List<Integer> sorted = new ArrayList<>(completed);
        Collections.sort(sorted);
        System.out.println("graceful-completed = " + sorted);
        System.out.println("graceful-end-messages = " + endMessages.get());
        System.out.println("graceful-consumers-terminated = "
                + (!first.isAlive() && !second.isAlive()));
        System.out.println("graceful-inbox-empty = " + inbox.isEmpty());
    }

    public static void main(String[] args) throws InterruptedException {
        ends();
        interruptTake();
        interruptPut();
        markerAtWrongEnd();
        graceful();
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 DequeTerminationDemo.java
java DequeTerminationDemo
~~~

Stdout:

~~~text
two-ends-content = [A, B, C]
take-first = A
take-last = C
queue-put-content = [B, D]
queue-take = B
stack-pop = X
timed-poll-empty = null
empty-pop = NoSuchElementException
other-end-offer-full = false
other-end-take = held
take-interrupted = true
status-inside-catch = false
status-after-restore = true
cleanup-ran = true
taker-terminated = true
flag-probe = [true, true, false]
put-interrupted = true
after-producer-interrupt = [kept]
front-stop-taken-first = true
work-left-after-front-stop = 2
graceful-completed = [1, 2, 3, 4, 5, 6]
graceful-end-messages = 2
graceful-consumers-terminated = true
graceful-inbox-empty = true
~~~

### Đọc kết quả theo đúng điều đã được chứng minh

`two-ends-content = [A, B, C]` cho thấy thêm `A` ở đầu trước đưa nó lên trước `B`, còn thêm `C` ở cuối đặt nó sau `B`. Sau khi lấy `A` và `C`, `put("D")` thêm cuối nên nội dung là `[B, D]`; `take()` lấy đầu và trả `B`. Hai dòng này tránh việc học thuộc `put/take` mà không biết đầu thao tác.

`stack-pop = X` xác nhận cặp `push/pop` dùng cùng đầu. Sau `clear`, `pollLast` có thời hạn bằng không trả `null`, trong khi `pop` trên deque rỗng ném `NoSuchElementException`. Chọn kiểu có khả năng chờ không thay đổi hợp đồng của các phương thức khác.

`other-end-offer-full = false` kiểm chứng dung lượng dùng chung: phần tử `held` ở deque dung lượng một làm `offerFirst` thất bại dù trước đó `held` được thêm ở đầu sau.

Ba dòng về ngắt tách rõ ba việc:

- `take-interrupted = true`: thao tác chờ đã báo ngoại lệ.
- `status-inside-catch = false`: cờ đã được tiêu thụ khi ngoại lệ được giao.
- `status-after-restore = true`: chính câu lệnh khôi phục trong `catch` đã đặt lại cờ.

`cleanup-ran = true` và `taker-terminated = true` là hai xác nhận khác nhau. Có chạy khối `finally` không đủ để suy luận mọi mã sau nó đã kết thúc; vì vậy chương trình còn chờ `join` và kiểm tra luồng đã hết sống.

`flag-probe = [true, true, false]` đến từ một luồng thăm dò riêng. Nó tự ngắt, quan sát không xóa, gọi phép đọc có xóa, rồi quan sát lại. Luồng chính không tự đặt cờ ngắt của mình, nên các thí nghiệm sau không vô tình nhận yêu cầu ngắt từ thí nghiệm trước.

`after-producer-interrupt = [kept]` chứng minh phần tử cũ vẫn còn, còn `not-inserted` không được thêm trong trường hợp full-queue đã thiết kế. Kết luận chỉ áp dụng cho thao tác bị gián đoạn đó; nó không hứa producer chưa từng gửi việc nào trước khi bị ngắt.

`front-stop-taken-first = true` và `work-left-after-front-stop = 2` là phản ví dụ cho cách chèn dấu dừng vào trước backlog rồi gọi đó là kết thúc có trật tự.

Phần cuối chỉ in sau khi chờ cả hai consumer. Danh sách kết quả được sao chép và sắp xếp để stdout ổn định. `[1, 2, 3, 4, 5, 6]` chứng minh các công việc đã được ghi nhận đầy đủ trong thí nghiệm; nó không chứng minh thứ tự hoàn thành của hai consumer là 1 rồi 2 rồi 3. Một consumer có thể lấy việc trước nhưng hoàn thành sau consumer khác.

`graceful-end-messages = 2` xác nhận hai thông điệp đã được lấy. Kết hợp số việc, số dấu, trạng thái hai luồng và deque rỗng mới tạo thành bằng chứng phù hợp với giao thức đơn giản này.

### Vì sao còn có timeout, daemon và bộ giữ lỗi?

Giới hạn năm giây trong `await`, `join` và `offerLast` giúp phép thử báo thất bại khi không tiến triển. Đó là giới hạn bảo vệ thí nghiệm, không phải cam kết thời gian của deque hay một thông số vận hành được khuyến nghị cho mọi dịch vụ.

Các luồng thử được đặt daemon để một lỗi trong thí nghiệm không giữ JVM chạy mãi. Việc kết thúc đúng vẫn được kiểm tra bằng `join` và `isAlive`. Trong dịch vụ thật, đổi consumer thành daemon không phải giao thức dừng; JVM có thể thoát mà không chờ xử lý hoặc dọn tài nguyên của daemon.

`AtomicReference<Throwable>` chuyển lỗi từ luồng con về luồng chính. Nếu chỉ nhìn luồng đã chết rồi kết luận thành công, một consumer ném ngoại lệ và chết sớm cũng có thể bị hiểu lầm là đã kết thúc có trật tự.

Trong ví dụ, `join` có thời hạn nhưng không tin rằng hết chờ đồng nghĩa luồng đã xong. Nó kiểm tra `isAlive` rồi báo lỗi nếu còn sống. Đây là điểm cần giữ khi chuyển thí nghiệm thành một quy trình giám sát thực tế.

## Thiết kế một quy trình dừng có trật tự

Một thiết kế dùng `putLast/takeFirst` và thông điệp kết thúc có thể chia thành các bước có trách nhiệm rõ ràng:

1. Ngừng chấp nhận nguồn công việc mới.
2. Chờ các producer đang tham gia hoàn thành việc gửi hoặc báo kết quả gửi cuối cùng.
3. Xác nhận không producer nào có thể thêm việc sau mốc kết thúc.
4. Gửi đủ dấu kết thúc, bằng đúng hướng đã thống nhất.
5. Chờ mọi consumer kết thúc và thu thập lỗi của chúng.
6. Kiểm tra những điều kiện hoàn tất của ứng dụng, rồi mới báo thành công.

Trong ví dụ, luồng chính là producer duy nhất nên kết thúc vòng `for` gửi sáu việc là một mốc đơn giản. Với nhiều producer, một thành phần điều phối phải chờ tất cả producer hoặc đóng một cổng nhận việc được đồng bộ đúng trước khi phát dấu.

Một cờ `accepting = false` riêng lẻ chưa chắc đóng được cổng. Nếu producer kiểm tra cờ, bị tạm dừng, rồi thêm việc sau khi thành phần khác đã gửi `End`, thứ tự vẫn sai. Phép quyết định nhận việc và mốc đóng cần có giao thức chung; đó là logic vòng đời của ứng dụng, không phải thứ deque tự suy ra.

### Đã lấy hết việc khác với đã hoàn thành hết việc

Với hai consumer, consumer thứ nhất có thể nhận `End` và thoát trong lúc consumer thứ hai vẫn xử lý công việc nó đã lấy trước đó. Dấu dừng ở cuối bảo vệ thứ tự lấy từ hàng đợi trong điều kiện đã nêu, không buộc mọi phần xử lý trước đó hoàn thành cùng lúc.

Vì vậy, phải chờ tất cả consumer hoặc một bộ đếm xác nhận hoàn thành tương ứng. Một lần thấy `inbox.isEmpty()` không thay thế được bước chờ.

Ví dụ dùng ghi số vào `ConcurrentLinkedQueue` làm phần xử lý rất ngắn. Trong ứng dụng thật, “hoàn thành” phải được định nghĩa tại nơi có kết quả cần bảo đảm: ghi tệp xong, giao dịch đã commit, phản hồi đã lưu, hay chỉ mới gửi yêu cầu cho dịch vụ khác. Nếu consumer tạo tác vụ bất đồng bộ rồi tiếp tục, việc consumer thoát không tự chứng minh những tác vụ con đã hoàn tất.

### Xử lý lỗi consumer là một phần của giao thức

Nếu một consumer chết trước khi nhận dấu, số dấu dự kiến có thể không còn khớp số luồng đang chạy. Các producer cũng có thể bị kẹt khi deque đầy và không còn người lấy.

Điều phối cần thu thập lỗi và chọn một trong các kết quả có tên rõ ràng: đã xử lý xong, kết thúc có lỗi với việc còn lại, hoặc hủy và chuyển số việc chưa làm sang nơi khác. Không báo thành công chỉ vì cuối cùng không còn luồng sống.

Chính sách thử lại phải tính tới việc consumer đã làm một phần tác động trước khi lỗi. Đưa công việc lại vào đầu deque có thể làm công việc đó được xử lý lần nữa; gửi lại vô hạn còn có thể làm công việc khác bị đói. Các lựa chọn như mã định danh công việc, số lần thử, nơi lưu việc lỗi và xử lý lặp an toàn thuộc thiết kế nghiệp vụ.

## Khi cần hủy sớm

Một quy trình hủy sớm thường bắt đầu bằng ngừng nhận việc mới, phát yêu cầu ngắt tới các luồng thuộc quyền quản lý, rồi chờ chúng phản ứng. Các việc đang chờ và đang xử lý phải có chính sách riêng.

Nếu phần xử lý là vòng tính toán dài, hãy tạo điểm kiểm tra ở ranh giới công việc hợp lý. Đoạn minh họa sau giả định mỗi `processChunk` là một bước hữu hạn và không bỏ qua ngoại lệ ngắt từ các API nó gọi:

~~~java
while (hasNextChunk()) {
    if (Thread.currentThread().isInterrupted()) {
        return;
    }
    processChunk();
}
~~~

Kiểm tra trước mỗi khối tạo cơ hội dừng, không tạo một giao dịch nguyên tử giữa “kiểm tra” và “xử lý”. Yêu cầu ngắt vẫn có thể tới ngay sau lần kiểm tra. Độ trễ hủy phụ thuộc độ dài mỗi khối và các điểm chờ thực tế.

Nếu một tài nguyên có cơ chế hủy riêng, chẳng hạn thao tác đóng kênh hoặc hủy truy vấn theo API của nó, cần dùng đúng hợp đồng đó. Không gọi `Thread.stop` để ép kết thúc. Ép luồng dừng giữa lúc cập nhật dữ liệu có thể phá vỡ tính nhất quán của trạng thái mà các luồng khác cùng sử dụng.

### Cờ volatile không tự đánh thức take

Đoạn minh họa sau có vấn đề nếu `requestStop` chỉ đặt `running = false`:

~~~java
while (running) {
    Job job = inbox.takeFirst();
    process(job);
}
~~~

`volatile` có thể giúp lần đọc `running` sau nhìn thấy giá trị mới, nhưng consumer đang chờ trong `takeFirst` chưa quay lại để đọc cờ. Cần một cơ chế rời chỗ chờ: ngắt, một thông điệp điều khiển đúng giao thức, hoặc chờ có thời hạn rồi kiểm tra trạng thái.

Nếu chọn `pollFirst(timeout, unit)`, `null` chỉ có nghĩa chưa lấy được phần tử trong lần chờ đó. Nó không chứng minh mọi producer đã kết thúc. Điều kiện dừng phải kết hợp trạng thái vòng đời được đồng bộ, không phải “thấy null là hết dữ liệu”.

Thời hạn chờ ngắn giúp phản ứng sớm hơn với cờ nhưng tăng số lần thức dậy. Thời hạn dài giảm số lần kiểm tra nhưng tăng độ trễ. Đây là đánh đổi của thiết kế thăm dò; thông điệp hoặc ngắt thường diễn đạt một sự kiện cụ thể hơn.

### Dọn tài nguyên và giữ lại công việc đang sở hữu

Sau khi `takeFirst` trả một `Job`, trách nhiệm tạm thời đã chuyển sang consumer. Nếu consumer bị ngắt trong `process`, cần biết nó đang giữ công việc nào để ghi trạng thái thích hợp.

Một khối `finally` phù hợp để giải phóng tài nguyên do luồng sở hữu. Nhưng việc phát sinh một ngoại lệ khác trong dọn tài nguyên không được làm mất dấu lỗi gốc; cách quản lý tài nguyên phải giữ thông tin thất bại có ích. Nếu dùng tài nguyên `AutoCloseable`, xem xét `try-with-resources` theo hợp đồng đóng của tài nguyên.

Không mặc định rằng luôn đưa công việc trở lại deque là an toàn. Nếu deque đầy và consumer cố `putFirst` trong lúc đang thoát, nó lại có thể chờ vô hạn; nếu tác động bên ngoài đã xảy ra, thử lại có thể nhân đôi tác động. Kết quả hủy phải được giải quyết bằng một chính sách cụ thể, không bằng một câu lệnh chèn lại chung cho mọi tình huống.

## Chờ xác nhận: join, latch và executor

Gọi `interrupt` xong là thời điểm phát yêu cầu. Chờ luồng kết thúc là một bước tiếp theo. Với luồng do ứng dụng trực tiếp tạo, `join` là một cách chờ; bản có thời hạn cần kiểm tra lại luồng còn sống hay không.

Khi `join` xác nhận luồng đã kết thúc, các hành động trong luồng đó có quan hệ happens-before với phần mã đi tiếp sau khi phát hiện kết thúc. Vì vậy, ví dụ chỉ đọc danh sách `flags` sau `join`. Danh sách ấy chỉ có một luồng ghi và không bị đọc đồng thời khi đang ghi; không cần dùng nó như một danh sách chia sẻ cho nhiều bên cập nhật. Quy tắc về kết thúc luồng nằm trong [JLS 17.4.5](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5).

Một `CountDownLatch` có thể xác nhận nhiều consumer đã đi qua điểm hoàn thành mà ứng dụng quy định. Nhưng nếu đặt `countDown` trong `finally`, việc đếm về không cũng có thể bao gồm luồng kết thúc do lỗi. Cần giữ thêm kết quả hoặc lỗi; latch chỉ truyền sự kiện đã tới điểm đó. Xem [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html).

### shutdown của executor không đóng deque bên trong tác vụ

Nếu một `Runnable` chạy vòng `takeFirst` dài hạn bên trong `ExecutorService`, gọi `shutdown()` không tự đưa `End` vào deque và không làm tác vụ đang chạy rời `takeFirst`. Executor ngừng nhận tác vụ mới nhưng tác vụ consumer đã gửi vẫn cần hoàn thành.

`shutdownNow()` cố gắng dừng tác vụ đang chạy, thường bằng ngắt; mã phải hợp tác. `awaitTermination` chỉ chờ, có thể hết thời hạn hoặc chính luồng đang chờ bị ngắt. Đây là sự khác nhau giữa yêu cầu kết thúc và xác nhận kết thúc trong [ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html).

Nếu dùng `Future.cancel(true)`, đó là yêu cầu hủy có thể kèm ngắt luồng thực thi theo hợp đồng triển khai. Trạng thái đã hủy của `Future` không phải bằng chứng rằng mọi phần dọn tài nguyên bên trong đã chạy xong. Nếu ứng dụng cần biết một tài nguyên đã được giải phóng, hãy có xác nhận cho đúng sự kiện đó. Xem [Future.cancel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html#cancel(boolean)).

## Các lỗi phối hợp thường gặp

### Giữ khóa ứng dụng trong lúc chờ hàng đợi

Giả sử producer giữ `synchronized(lock)` rồi gọi `putLast` khi đầy. Consumer cần chính `lock` để hoàn tất công việc trước khi có thể lấy tiếp. Producer chờ chỗ, consumer chờ khóa; một deque an toàn cho nhiều luồng không sửa được vòng chờ do ứng dụng tạo.

Hãy sắp xếp để không giữ tài nguyên mà phía bên kia cần trong lúc chờ deque. Tính nguyên tử của một cập nhật nghiệp vụ và sự điều tiết của kênh truyền cần được thiết kế cùng nhau; bọc tất cả vào một khóa lớn có thể làm mất khả năng tiến triển.

### Để producer chiếm hết các luồng trong cùng pool

Nếu mọi luồng của một pool hữu hạn đều chạy producer đang kẹt trong `putLast`, còn consumer chỉ nằm trong danh sách tác vụ chờ của pool, không có consumer nào được chạy để tạo chỗ. Có đủ tác vụ tiêu thụ trên giấy không có nghĩa có tài nguyên thực thi cho chúng.

Cần kiểm tra cả quan hệ giữa hàng đợi công việc của ứng dụng và hàng đợi tác vụ của executor. Giải pháp có thể là tách tài nguyên thực thi, dùng giao thức gửi không chờ với xử lý từ chối, hoặc tổ chức công việc khác. Tăng dung lượng chỉ có thể trì hoãn tình trạng đầy.

### clear không có nghĩa đóng

`clear()` loại bỏ phần tử đang nằm trong deque. Nó không gửi thông điệp kết thúc cho consumer đang chờ trên một deque rỗng, cũng không dừng công việc consumer đã lấy.

Ngay cả nếu thao tác xóa tạo chỗ cho producer, producer lại có thể gửi thêm việc. Nếu dùng xóa trong quy trình hủy, phải ngừng bên gửi và xác định số việc bị loại bỏ; không thể coi việc xóa là toàn bộ quy trình hủy.

### Kiểm tra isEmpty rồi mới take

Đoạn `if (!inbox.isEmpty()) inbox.takeFirst()` không bảo đảm `takeFirst` sẽ không chờ. Consumer khác có thể lấy phần tử giữa hai thao tác. Nếu ý định là thử lấy ngay, gọi `pollFirst` và xử lý `null`.

Tương tự, `remainingCapacity() > 0` không giữ chỗ cho một lần `putLast` sau đó. Kết quả của chính `offerLast` mới cho biết lần gửi đó có được chấp nhận ngay hay không.

### Tưởng bàn giao an toàn đồng nghĩa đối tượng bên trong bất biến

Việc công bố phần tử qua cấu trúc đồng thời có quy tắc hiển thị bộ nhớ. Nhưng nếu producer tiếp tục sửa một danh sách nằm trong `Job` sau khi đã gửi, deque không tự khóa danh sách ấy cho consumer.

Trong ví dụ, `Item` chỉ chứa `int` và `End` là hằng enum nên việc bàn giao đơn giản. Với dữ liệu phức tạp, có thể chọn dữ liệu bất biến, bản sao thích hợp hoặc quy tắc chuyển quyền sở hữu: sau khi gửi thành công, producer không sửa nội dung đó nữa. Một `record` có trường trỏ tới danh sách mutable vẫn không tự làm danh sách bất biến.

## Chọn cơ chế theo điều cần bảo đảm

| Tình huống | Hướng thiết kế | Điều phải xác nhận thêm |
| --- | --- | --- |
| Nguồn dữ liệu hữu hạn, muốn xử lý hết | Đóng giai đoạn gửi, thêm dấu cuối, consumer lấy đầu | Không có việc gửi sau dấu; đủ dấu; tất cả consumer hoàn thành |
| Consumer đang chờ nhưng cần hủy sớm | Ngắt luồng do thành phần quản lý sở hữu | Consumer bắt đúng, thoát, dọn tài nguyên và được chờ |
| Xử lý dài không có chỗ chờ | Kiểm tra cờ ở ranh giới khối việc | Độ trễ hủy và số tác động đã thực hiện |
| Muốn kiểm tra trạng thái định kỳ | `pollFirst` có thời hạn | `null` không phải hết dữ liệu; trạng thái kết thúc phải đáng tin |
| Không gửi được dấu vì đầy | Gửi có thời hạn và theo dõi lỗi consumer | Có nhánh thất bại hoặc hủy; không báo hoàn tất giả |
| Phải phục hồi sau tiến trình chết | Kênh bền vững và xác nhận nghiệp vụ phù hợp | Quy tắc thử lại, lặp an toàn và lưu trạng thái |

Khi dùng cả hai đầu để ưu tiên hoặc thử lại, hãy viết lại điều kiện kết thúc cho đúng các hướng đó. Một giao thức được chứng minh cho `putLast/takeFirst` không tự áp dụng cho một hệ thống khác có `putFirst` và `takeLast` xen kẽ.

## Bài tập tự kiểm tra

### 1. Dung lượng một, hai đầu có hai chỗ không?

Deque dung lượng một đã có `A` bằng `putLast`. Lệnh `offerFirst("B")` trả gì?

**Đáp án:** `false`. Giới hạn áp dụng cho tổng số phần tử; đầu trước không có một vùng dung lượng riêng.

### 2. Vì sao pop không chờ?

Một deque đang rỗng. Đổi biến từ kiểu `Deque` sang `BlockingDeque` rồi gọi `pop()` có làm nó chờ việc mới không?

**Đáp án:** Không. `pop` vẫn ném `NoSuchElementException` khi rỗng. Muốn chờ lấy ở đầu trước, dùng `takeFirst` và xử lý khả năng ngắt.

### 3. Cờ false trong catch có chứng minh chưa bị ngắt không?

Consumer nhận `InterruptedException` từ một lần chờ `takeFirst`. Trong `catch`, `isInterrupted` là `false`.

**Đáp án:** Không. Đây là kết quả phù hợp với việc ngoại lệ được giao sau khi cờ đã được xóa. Phải xử lý ngoại lệ theo chính sách hủy; nếu không truyền lên, mẫu phổ biến ở biên `Runnable` là khôi phục cờ và thoát.

### 4. Một End cho bốn consumer

Bốn consumer đều lấy đầu trước và thoát khi gặp `End`. Producer gửi một `End` rồi chờ cả bốn.

**Đáp án:** Chỉ một consumer lấy phần tử đó. Ba consumer còn lại có thể chờ mãi. Với giao thức mỗi consumer lấy một dấu rồi thoát, gửi đủ bốn dấu sau khi mọi producer đã hoàn thành.

### 5. End ở cuối nhưng còn producer đang gửi

Thành phần điều phối gửi `End` vào cuối khi producer thứ hai chưa kết thúc. Producer ấy gửi thêm `Item(9)` sau `End`.

**Đáp án:** Thứ tự gửi không bảo đảm consumer đi tới `Item(9)`. Cần một mốc kết thúc giai đoạn sản xuất có đồng bộ; không dùng `isEmpty` hay một lần nhìn cờ để thay cho việc đóng cổng nhận và chờ các lượt gửi đang tham gia.

### 6. isEmpty là true, vì sao chưa thể đóng tệp kết quả?

Một consumer vừa lấy công việc cuối và đang ghi tệp. Luồng điều phối nhìn deque rỗng.

**Đáp án:** Công việc đã ra khỏi hàng đợi nhưng chưa hoàn thành. Chờ consumer hoặc xác nhận hoàn thành nghiệp vụ trước khi đóng tài nguyên nó đang dùng.

### 7. Chỉ đặt volatile running thành false

Consumer đang ở `takeFirst` trên deque rỗng. Luồng khác đặt cờ dừng.

**Đáp án:** Cờ nhìn thấy được không tự đánh thức thao tác chờ. Cần thêm ngắt, thông điệp điều khiển đúng giao thức, hoặc thiết kế thăm dò có thời hạn. Việc thêm cơ chế phải khớp ý định xử lý hết hay hủy sớm.

### 8. Kết quả sắp xếp có chứng minh thứ tự hoàn thành không?

Hai consumer ghi số việc đã xử lý vào cấu trúc đồng thời; sau khi chờ chúng, chương trình sắp xếp kết quả thành `[1, 2, 3, 4, 5, 6]`.

**Đáp án:** Nó chứng minh tập kết quả trong thí nghiệm, không chứng minh lịch hoàn thành theo thứ tự. Muốn giữ thứ tự kết quả, cần cơ chế đánh số và ghép kết quả theo thứ tự hoặc cách thực thi phù hợp; tính FIFO của lúc lấy không đủ khi nhiều consumer xử lý song song.


</details>

- [Quay lại đầu trang](#back-to-top)
