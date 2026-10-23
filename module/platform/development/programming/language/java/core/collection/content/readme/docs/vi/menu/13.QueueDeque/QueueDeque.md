<a id="back-to-top"></a>

# Queue và Deque

## Menu
- [Queue, FIFO và những hàng đợi không theo FIFO](#queue-deque-001)
- [Các cặp add–offer, remove–poll và element–peek](#queue-deque-002)
- [Giá trị đặc biệt so với ngoại lệ khi rỗng hoặc đầy](#queue-deque-003)
- [Deque và thao tác thêm, xem, xóa ở hai đầu](#queue-deque-004)
- [Dùng Deque làm hàng đợi FIFO hoặc ngăn xếp LIFO](#queue-deque-005)
- [ArrayDeque: mảng vòng, tăng dung lượng và thao tác hai đầu](#queue-deque-006)
- [Vì sao ArrayDeque không nhận null](#queue-deque-007)
- [Xóa lần xuất hiện đầu hoặc cuối và duyệt ngược](#queue-deque-008)
- [So sánh ArrayDeque, LinkedList và Stack](#queue-deque-009)

## <a id="queue-deque-001">Queue, FIFO và những hàng đợi không theo FIFO</a>

<details>
<summary>Xem chi tiết</summary>

# Queue, FIFO và những hàng đợi không theo FIFO

## 1. Queue là giao diện thao tác; FIFO là một chính sách thứ tự

Trong Java, `Queue<E>` biểu diễn một tập hợp giữ các phần tử để chờ xử lý. Nó cung cấp những thao tác như thêm phần tử, xem phần tử ở đầu hàng và lấy phần tử ở đầu hàng ra.

Một điểm cần phân biệt ngay:

- **Queue** là giao diện và mô hình thao tác.
- **FIFO** là chính sách “vào trước, ra trước” — first in, first out.
- Không phải mọi đối tượng có kiểu `Queue` đều chọn phần tử theo FIFO.

Tài liệu `Queue` nói rõ các hàng đợi thường theo FIFO nhưng có ngoại lệ, gồm hàng đợi ưu tiên và hàng đợi LIFO. Mỗi triển khai phải mô tả quy tắc thứ tự của nó. [Queue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html)

Khái niệm chung cho mọi chính sách là **đầu hàng (head)**: phần tử mà thao tác lấy ra như `poll()` hoặc `remove()` sẽ chọn theo hợp đồng của triển khai.

Vì vậy, tên phương thức `poll` không tự có nghĩa “lấy phần tử được thêm sớm nhất”. Nó có nghĩa lấy phần tử ở đầu theo chính sách đang dùng.

Ví dụ, cùng thêm lần lượt:

```text
30, 10, 20
```

có thể có ba kết quả hợp lệ:

| Chính sách | Thứ tự lấy ra |
| --- | --- |
| FIFO | `30, 10, 20` |
| Ưu tiên số nhỏ trước | `10, 20, 30` |
| LIFO, vào sau ra trước | `20, 10, 30` |

Muốn dự đoán chương trình, phải biết cả giao diện đang gọi lẫn chính sách của đối tượng thực tế.

## 2. FIFO: thêm ở cuối, lấy ở đầu

### 2.1. Mô hình đầu và cuối

Một hàng đợi FIFO có hai vai trò khác nhau:

```text
lấy ra ← đầu hàng [A] [B] [C] cuối hàng ← thêm mới
```

Nếu `A`, `B`, `C` được thêm tuần tự theo thứ tự đó và không có thao tác khác thay đổi hàng, các lần lấy sẽ trả `A`, rồi `B`, rồi `C`.

| Thao tác | Trạng thái FIFO sau thao tác | Kết quả lấy ra |
| --- | --- | --- |
| Thêm A | `[A]` | Chưa lấy |
| Thêm B | `[A, B]` | Chưa lấy |
| Thêm C | `[A, B, C]` | Chưa lấy |
| Lấy đầu | `[B, C]` | `A` |
| Lấy đầu | `[C]` | `B` |
| Lấy đầu | `[]` | `C` |

Một phần tử mới thêm vào cuối không vượt qua các phần tử đang chờ trước nó. Đó là ý nghĩa của vào trước, ra trước.

### 2.2. Một cách dùng FIFO trong một luồng

```java
// Đoạn minh họa; có chương trình đầy đủ ở phần sau.
Queue<String> pending = new ArrayDeque<>();
pending.offer("A");
pending.offer("B");
pending.offer("C");

String next = pending.poll(); // A
```

Khi dùng `ArrayDeque` qua các thao tác hàng đợi như trên, thêm diễn ra ở cuối và lấy ở đầu.

FIFO nói về thứ tự lấy khỏi hàng, không nhất thiết là thứ tự hoàn thành xử lý ở ngoài hàng. Nếu nhiều bên cùng xử lý và một công việc lâu hơn công việc khác, kết quả hoàn tất có thể khác thứ tự lấy. Hàng đợi không tự biến thời gian xử lý của mọi phần tử thành giống nhau.

### 2.3. FIFO không có nghĩa phải loại trùng

Một hàng đợi không phải `Set`. Các triển khai trong bài có thể giữ hai phần tử bằng nhau:

```text
thêm A, thêm A
lấy ra: A, A
```

Nếu yêu cầu chỉ xử lý mỗi mã một lần, cần thiết kế riêng việc nhận diện đã gặp, không suy ra từ việc dữ liệu nằm trong một `Queue`.

## 3. Sáu phương thức cốt lõi và cách đọc đúng đầu hàng

`Queue` chia các thao tác cơ bản thành hai cách báo một số tình huống không thực hiện được:

| Mục đích | Dạng báo bằng ngoại lệ | Dạng trả giá trị đặc biệt |
| --- | --- | --- |
| Thêm phần tử | `add(e)` | `offer(e)` |
| Lấy và xóa đầu hàng | `remove()` | `poll()` |
| Xem đầu hàng, giữ nguyên hàng | `element()` | `peek()` |

Chính sách thứ tự quyết định **phần tử nào là đầu**. Các cặp phương thức chủ yếu khác cách báo trạng thái thất bại tương ứng, không đổi FIFO thành ưu tiên hoặc ngược lại.

### 3.1. offer và add

`offer(e)` thử thêm phần tử ngay. Với một hàng đợi bị giới hạn dung lượng, nó trả `false` khi không thể thêm vì hiện không còn chỗ.

`add(e)` trả `true` khi thành công; nếu không có chỗ do giới hạn dung lượng, nó báo bằng `IllegalStateException`.

Không hiểu “dạng trả giá trị đặc biệt” là mọi lỗi đều được chuyển thành `false`. Phần tử không hợp lệ, chẳng hạn `null` trong một hàng không chấp nhận `null`, vẫn có thể gây ngoại lệ.

Cũng không hiểu `offer` là lời gọi tự chờ tới khi có chỗ. Các thao tác chờ thuộc giao diện chuyên biệt như `BlockingQueue`, với những phương thức riêng. [Queue: add và offer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html)

### 3.2. poll và remove không đối số

Với các hàng đợi thông thường trong chương trình đầu:

- `poll()`: trả và xóa đầu hàng; khi hàng rỗng trả `null`.
- `remove()`: trả và xóa đầu hàng; khi hàng rỗng ném `NoSuchElementException`.

Đây là `remove()` **không có đối số**. Nó khác với `remove(Object value)` kế thừa từ `Collection`, vốn tìm và xóa một phần tử phù hợp theo giá trị và trả `boolean`.

Không đọc hai chữ ký này như cùng một thao tác.

### 3.3. peek và element

Hai phương thức đều xem đầu hàng mà không xóa:

- `peek()`: khi rỗng trả `null`.
- `element()`: khi rỗng ném `NoSuchElementException`.

Với một FIFO có `[A, B, C]`, nhiều lần `peek()` khi không có thay đổi khác vẫn nhìn thấy `A`, không lần lượt đi qua `A`, `B`, `C`.

### 3.4. Tránh dùng null như một công việc hợp lệ

`ArrayDeque` và `PriorityQueue` không cho thêm `null`. Giao diện `Queue` còn có những triển khai với quy tắc khác, nhưng ngay cả khi một triển khai cho phép, dùng `null` làm phần tử vẫn gây mơ hồ với các kết quả đặc biệt của `poll` và `peek`.

Nên giữ miền công việc không chứa `null` khi có thể. Khi lấy ra, kiểm tra kết quả trước khi dùng như một đối tượng hoặc mở hộp sang kiểu nguyên thủy.

Các hàng đợi chuyên biệt còn có điều kiện phần tử được phép lấy; ví dụ `DelayQueue` được phân biệt ở phần sau. Không khái quát một vòng lặp xử lý đơn giản cho mọi triển khai mà chưa đọc hợp đồng của nó. [Queue: quy tắc null](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html)

## 4. Chương trình đầy đủ: cùng giao diện, ba thứ tự lấy

Lưu mã vào **QueuePoliciesDemo.java**:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;

public class QueuePoliciesDemo {
    private static <E> List<E> drain(Queue<E> queue) {
        List<E> result = new ArrayList<>();
        E value;
        while ((value = queue.poll()) != null) {
            result.add(value);
        }
        return result;
    }

    public static void main(String[] args) {
        Queue<Integer> fifo = new ArrayDeque<>();
        Queue<Integer> priority = new PriorityQueue<>();
        Queue<Integer> lifo =
                Collections.asLifoQueue(new ArrayDeque<>());

        List<Integer> input = List.of(30, 10, 20);
        for (Integer value : input) {
            fifo.offer(value);
            priority.offer(value);
            lifo.offer(value);
        }

        System.out.println("input: " + input);
        System.out.println("FIFO head: " + fifo.peek());
        System.out.println("priority head: " + priority.peek());
        System.out.println("LIFO head: " + lifo.peek());
        System.out.println("FIFO size after peek: " + fifo.size());
        System.out.println("FIFO removal order: " + drain(fifo));
        System.out.println("priority removal order: " + drain(priority));
        System.out.println("LIFO removal order: " + drain(lifo));
        System.out.println("FIFO empty: " + fifo.isEmpty());
        System.out.println("empty peek: " + fifo.peek());
        System.out.println("empty poll: " + fifo.poll());

        try {
            fifo.remove();
        } catch (NoSuchElementException ex) {
            System.out.println("empty remove: NoSuchElementException");
        }
        try {
            fifo.element();
        } catch (NoSuchElementException ex) {
            System.out.println("empty element: NoSuchElementException");
        }

        Queue<String> repeated = new ArrayDeque<>();
        repeated.offer("A");
        repeated.offer("A");
        System.out.println("duplicates: " + drain(repeated));
    }
}
```

Biên dịch theo mức Java 17 và chạy:

```shell
javac --release 17 -encoding UTF-8 QueuePoliciesDemo.java
java QueuePoliciesDemo
```

Stdout:

```text
input: [30, 10, 20]
FIFO head: 30
priority head: 10
LIFO head: 20
FIFO size after peek: 3
FIFO removal order: [30, 10, 20]
priority removal order: [10, 20, 30]
LIFO removal order: [20, 10, 30]
FIFO empty: true
empty peek: null
empty poll: null
empty remove: NoSuchElementException
empty element: NoSuchElementException
duplicates: [A, A]
```

Cách đọc kết quả:

1. Ba biến đều có kiểu `Queue<Integer>`, nhưng đối tượng phía sau có ba chính sách khác nhau.
2. FIFO trả đầu `30`, vì đó là phần tử được thêm trước.
3. Hàng đợi ưu tiên tự nhiên trả đầu `10`, không phải phần tử được thêm trước.
4. Khung nhìn LIFO trả đầu `20`, phần tử được thêm sau cùng.
5. `peek` không làm giảm kích thước.
6. Hàm `drain` liên tục gọi `poll` và vì thế làm các hàng trong ví dụ rỗng.
7. Kết quả khi rỗng phân biệt rõ cặp `poll/remove` và `peek/element`.
8. Hai giá trị `A` cùng được giữ và cùng được lấy ra.

Hàm `drain` được dùng ở đây cho ba hàng đợi không trì hoãn và không chứa `null`. Với chúng, `poll()` trả `null` khi không còn phần tử. Hàm không được giới thiệu như một thuật toán “chắc chắn lấy hết mọi phần tử” cho tất cả hàng đợi chuyên biệt.

Chương trình lấy thứ tự bằng các lần `poll`. Nó không dùng thứ tự `for-each` của một hàng đợi ưu tiên để suy ra thứ tự xử lý.

## 5. PriorityQueue: ưu tiên quyết định đầu hàng

### 5.1. Phần tử được lấy trước có thể đến sau

`PriorityQueue` chọn đầu theo thứ tự tự nhiên của phần tử hoặc một `Comparator` được truyền vào.

Theo thứ tự tự nhiên của số nguyên, phần tử nhỏ nhất hiện có là đầu. Nếu thêm `30`, rồi `10`, rồi `20`, lần lấy đầu tiên là `10`.

Điều này phù hợp với chính sách ưu tiên và không phải một hàng đợi FIFO bị hỏng. Quy tắc chọn phần tử của nó khác ngay từ thiết kế. [PriorityQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)

Nếu không truyền `Comparator`, phần tử phải có thứ tự tự nhiên tương thích, thường qua `Comparable`. Một lớp công việc không tự được so theo trường tên `priority` chỉ vì trường đó tồn tại; cần cung cấp quy tắc so sánh phù hợp.

Với đối tượng công việc, “số nhỏ ưu tiên trước” hay “số lớn ưu tiên trước” là quy ước phải thiết kế. Tên trường `priority` không tự quyết định chiều so sánh.

Ví dụ:

```java
Queue<Integer> greatestFirst =
        new PriorityQueue<>(Comparator.reverseOrder());
```

giúp số lớn nhất theo thứ tự số thông thường được chọn trước, vì nó nhỏ nhất theo bộ thứ tự đảo đã cung cấp.

### 5.2. Bằng mức ưu tiên không có nghĩa FIFO trong nhóm

Nếu chỉ so sánh `priority`, hai công việc có cùng mức sẽ cho kết quả so sánh bằng `0`. `PriorityQueue` không cam kết công việc tới trước trong nhóm đó được lấy trước; trường hợp hòa được chọn tùy ý theo hợp đồng.

Không dùng một lần chạy thấy “A trước B” để suy ra tính ổn định cho mọi dữ liệu hoặc mọi phiên bản.

Nếu cần FIFO trong cùng mức ưu tiên, phải đưa thứ tự tới vào tiêu chí phụ, chẳng hạn một trường `sequence` được gán theo quy tắc đã xác định:

```java
Comparator<Job> order = Comparator.comparingInt(Job::priority)
        .thenComparingLong(Job::sequence);
```

Quy tắc tổng thể vẫn là ưu tiên trước, rồi mới đến sequence. Nó **không trở thành FIFO toàn cục**: một công việc ưu tiên cao tới sau vẫn có thể đi trước một công việc ưu tiên thấp tới sớm.

Trong ví dụ một luồng ở phần sau, các sequence `0, 1, 2, 3` biểu diễn thứ tự đưa công việc vào. Với một thiết kế nhiều luồng, cần định nghĩa riêng cách cấp và ý nghĩa thứ tự này; hàng đợi không tự tạo trường sequence cho người dùng.

### 5.3. PriorityQueue giữ các phần tử cùng độ ưu tiên

`PriorityQueue` không phải `TreeSet`. Kết quả so sánh bằng `0` không làm hai công việc tự bị gộp thành một phần tử.

Hai công việc khác nhau cùng priority có thể cùng nằm trong hàng. Việc thiếu tiêu chí phụ làm thứ tự lấy giữa chúng không được cam kết, không có nghĩa một công việc bị loại trùng.

Nếu ứng dụng cần không có hai công việc cùng mã, cần một quy tắc quản lý mã riêng. Bộ so sánh ưu tiên không phải cơ chế loại trùng.

### 5.4. Không dùng iterator để chứng minh thứ tự ưu tiên

Hợp đồng `PriorityQueue` không bảo đảm `iterator`, `for-each` hoặc mảng lấy từ hàng được sắp đúng thứ tự các lần `poll`.

Một hàng đợi ưu tiên dựa trên heap không cần duy trì cả cấu trúc thành một dãy đã sắp xếp hoàn chỉnh. Nó cần cung cấp đúng đầu hàng cho các thao tác truy xuất tương ứng.

Nếu muốn xem thứ tự lấy mà giữ nguyên hàng gốc, có thể tạo một bản sao ưu tiên với cùng quy tắc rồi `poll` bản sao. Nếu muốn một danh sách sắp xếp để trình bày, tạo danh sách và sắp bằng bộ so sánh phù hợp.

Đừng in trực tiếp `PriorityQueue` rồi chép output đó thành thứ tự xử lý được bảo đảm. [PriorityQueue: đầu hàng, trường hợp hòa và duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)

### 5.5. Khóa ưu tiên cần ổn định khi công việc đang chờ

Nếu sửa trường dùng để so sánh của một đối tượng đã ở trong hàng, cấu trúc không tự biết phải sắp lại vì người gọi vừa đổi một trường.

Ưu tiên đối tượng công việc có khóa so sánh bất biến hoặc thiết kế rõ quy trình cập nhật. Trong ví dụ, record có các thành phần `String`, `int`, `long` ổn định.

## 6. LIFO và Deque: chọn đầu thêm/lấy làm thay đổi chính sách

LIFO là **vào sau, ra trước** — last in, first out, thường gọi là ngăn xếp (stack).

Nếu thêm `A`, `B`, `C` rồi lấy theo LIFO, kết quả là:

```text
C, B, A
```

`Deque` là hàng hai đầu, cho phép thao tác ở cả đầu và cuối. Cùng một triển khai như `ArrayDeque` có thể phục vụ hai cách sử dụng:

| Cách dùng | Thêm | Lấy | Thứ tự với A, B, C được thêm lần lượt |
| --- | --- | --- | --- |
| FIFO | `offerLast` | `pollFirst` | A, B, C |
| LIFO theo đầu trước | `push` hoặc thao tác thêm đầu tương ứng | `pop` hoặc thao tác lấy đầu tương ứng | C, B, A |

`push` thêm vào đầu trước; `pop` lấy và xóa từ đầu trước. Thêm và lấy cùng phía làm phần tử mới nhất được lấy trước. [Deque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)

Khi sử dụng các phương thức `Queue` thông thường của `ArrayDeque`, thêm/lấy có ý nghĩa FIFO. Khi dùng `push/pop`, ta đang sử dụng nó như ngăn xếp.

Không trộn tùy tiện các thao tác hai đầu rồi vẫn khẳng định mọi dữ liệu tuân thủ một lịch sử FIFO. Chính sách sử dụng phải thống nhất giữa các phần của chương trình có quyền sửa cùng cấu trúc.

### Có thể có một biến kiểu Queue mà hành vi là LIFO

Java cung cấp một khung nhìn:

```java
Queue<String> lifo =
        Collections.asLifoQueue(new ArrayDeque<>());
```

Khung nhìn chuyển các thao tác hàng đợi sang các thao tác ngăn xếp tương ứng trên deque nền. Đây là ví dụ trực tiếp rằng kiểu `Queue` không tự bảo đảm FIFO.

Nó là một khung nhìn dùng deque nền, không phải một bản sao dữ liệu độc lập. Nếu giữ thêm tham chiếu tới deque, thao tác qua hai phía đều tác động tới cùng dữ liệu. [Collections.asLifoQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#asLifoQueue(java.util.Deque))

## 7. Chương trình đầy đủ: ưu tiên có tiêu chí phụ và cách dùng Deque

Lưu mã vào **PriorityAndStackDemo.java**:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

public class PriorityAndStackDemo {
    private record Job(String name, int priority, long sequence) {
    }

    private static List<String> jobOrder(Queue<Job> jobs) {
        List<String> result = new ArrayList<>();
        Job job;
        while ((job = jobs.poll()) != null) {
            result.add(job.name());
        }
        return result;
    }

    public static void main(String[] args) {
        Comparator<Job> order = Comparator.comparingInt(Job::priority)
                .thenComparingLong(Job::sequence);
        Queue<Job> jobs = new PriorityQueue<>(order);
        jobs.offer(new Job("report", 2, 0));
        jobs.offer(new Job("parse", 1, 1));
        jobs.offer(new Job("index", 1, 2));
        jobs.offer(new Job("notify", 2, 3));
        System.out.println("priority then sequence: " + jobOrder(jobs));

        Queue<Job> tied = new PriorityQueue<>(
                Comparator.comparingInt(Job::priority));
        tied.offer(new Job("A", 1, 0));
        tied.offer(new Job("B", 1, 1));
        System.out.println("equal-priority count: " + tied.size());
        List<String> tiedNames = jobOrder(tied);
        tiedNames.sort(Comparator.naturalOrder());
        System.out.println("all tied names, sorted afterward: " + tiedNames);

        Queue<Integer> greatestFirst =
                new PriorityQueue<>(Comparator.reverseOrder());
        greatestFirst.addAll(List.of(30, 10, 20));
        List<Integer> descending = new ArrayList<>();
        Integer number;
        while ((number = greatestFirst.poll()) != null) {
            descending.add(number);
        }
        System.out.println("reverse-comparator order: " + descending);

        Deque<String> fifo = new ArrayDeque<>();
        fifo.offerLast("A");
        fifo.offerLast("B");
        fifo.offerLast("C");
        System.out.println("deque FIFO first: " + fifo.pollFirst());

        Deque<String> stack = new ArrayDeque<>();
        stack.push("A");
        stack.push("B");
        stack.push("C");
        System.out.println("deque stack first: " + stack.pop());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 PriorityAndStackDemo.java
java PriorityAndStackDemo
```

Stdout:

```text
priority then sequence: [parse, index, report, notify]
equal-priority count: 2
all tied names, sorted afterward: [A, B]
reverse-comparator order: [30, 20, 10]
deque FIFO first: A
deque stack first: C
```

Cách đọc kết quả:

1. `report` được thêm sớm nhất nhưng priority `2`; hai công việc priority `1` được lấy trước theo quy ước số nhỏ trước của ví dụ.
2. Trong nhóm priority `1`, sequence `1` của `parse` đứng trước sequence `2` của `index`.
3. Hai công việc chỉ được so theo cùng priority vẫn cùng tồn tại: kích thước là `2`.
4. Danh sách tên của nhóm hòa được **sắp sau khi lấy ra** trước khi in. Dòng `[A, B]` đó không khẳng định hàng ưu tiên đã lấy A trước B.
5. Bộ so sánh đảo cho dãy số lấy `30` trước `20`, rồi `10`.
6. Deque dùng FIFO trả `A`; deque dùng ngăn xếp trả `C`.

Hai chương trình độc lập trong bài đã được biên dịch bằng `--release 17` và chạy đối chiếu đúng stdout. Tùy chọn này kiểm tra mức ngôn ngữ/API và tạo bytecode tương thích Java 17; nó không khẳng định JVM thực thi chính là JDK 17.

## 8. Các chính sách khác và những thuộc tính không được gộp với FIFO

### 8.1. Hàng đợi trì hoãn còn xét khi nào được lấy

`DelayQueue` là một ví dụ chuyên biệt: phần tử mang thông tin trì hoãn và chỉ có thể được lấy theo các điều kiện hết hạn của lớp.

Một phần tử được đưa vào sau nhưng tới thời điểm được phép lấy sớm hơn không phải chờ chỉ vì nó được thêm sau. Thứ tự xử lý không được suy ra đơn thuần từ thứ tự chèn.

Một chi tiết quan trọng: `poll()` có thể trả `null` khi chưa có phần tử nào hết hạn, dù cấu trúc vẫn giữ các phần tử chưa tới hạn. Vì vậy, vòng lặp dừng khi `poll` trả `null` không đồng nghĩa đã lấy hết mọi phần tử của loại hàng này. [DelayQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/DelayQueue.html)

Bài này không dùng ví dụ stdout dựa vào chờ thời gian, vì trọng tâm là phân biệt chính sách lấy với thứ tự chèn.

### 8.2. Chính sách thứ tự, giới hạn và chờ là các trục riêng

| Thuộc tính | Câu hỏi |
| --- | --- |
| Thứ tự | FIFO, LIFO, ưu tiên hay theo điều kiện thời gian? |
| Dung lượng | Có giới hạn số phần tử hay tự tăng? |
| Cách thất bại/chờ | Trả ngay, ném ngoại lệ hay có thao tác chờ? |
| Truy cập đồng thời | Triển khai có hỗ trợ nhiều luồng theo hợp đồng nào? |

Một hàng FIFO có thể có giới hạn. Một hàng ưu tiên có thể không có giới hạn cố định theo API. Có khả năng chờ không có nghĩa nó tự phải là FIFO.

`Queue` không khai báo các phương thức chờ như `put` và `take`; chúng thuộc `BlockingQueue`. Giao diện mở rộng bổ sung khả năng chờ, còn thứ tự cụ thể vẫn cần đọc ở triển khai. [BlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)

Ví dụ `ArrayBlockingQueue` có dung lượng cố định và chính sách phần tử FIFO. `PriorityQueue` là hàng ưu tiên tự tăng dung lượng lưu nội bộ, không có giới hạn cố định kiểu đó theo hợp đồng của lớp. Không suy ra cơ chế chờ hay khả năng nhiều luồng từ tên biến `Queue`. [ArrayBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html), [PriorityQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)

`ArrayDeque` và `PriorityQueue` thông thường không tự đồng bộ cho nhiều luồng cùng sửa. Những ví dụ trong bài được xét trong một luồng, không phải mẫu điều phối đa luồng.

## 9. Chọn cấu trúc theo yêu cầu xử lý

| Yêu cầu | Hướng sử dụng phù hợp |
| --- | --- |
| Xử lý theo thứ tự được đưa vào trong một luồng | Queue dùng ArrayDeque với kỷ luật FIFO |
| Cần lấy công việc theo một mức ưu tiên | PriorityQueue với quy tắc so sánh rõ |
| Cùng ưu tiên phải theo thứ tự đã xác định | Thêm tiêu chí phụ phù hợp, chẳng hạn sequence |
| Cần quay lại bước mới nhất trước | Deque dùng như ngăn xếp |
| Chỉ lấy khi đã tới hạn | Cân nhắc cấu trúc thời gian như DelayQueue |
| Có giới hạn và cần thao tác chờ giữa các luồng | Xem hợp đồng BlockingQueue và triển khai cụ thể |

Một số câu hỏi thiết kế cần trả lời trước:

1. Điều gì quyết định công việc tiếp theo?
2. Có cho phần tử bằng nhau xuất hiện nhiều lần không?
3. Nếu cùng ưu tiên, có cần một thứ tự phụ được bảo đảm không?
4. Khi hết chỗ hoặc không có phần tử lấy được, muốn trả ngay hay chờ?
5. Ai sở hữu và được phép sửa hàng đợi?

Với hàng ưu tiên, nếu các công việc ưu tiên cao liên tục tới, một công việc ưu tiên thấp có thể phải chờ lâu. Đây là hệ quả chính sách cần được xét trong bài toán, không phải lỗi vì “queue lẽ ra phải FIFO”. Việc thay đổi ưu tiên theo thời gian hoặc bảo đảm cơ hội xử lý là thiết kế bổ sung, không có sẵn chỉ từ `PriorityQueue`.

## 10. Hiểu chi phí theo triển khai thay vì gán một con số cho Queue

Tên giao diện `Queue` không hứa mọi thao tác của mọi triển khai đều O(1).

Một vài đặc tính liên quan tới các ví dụ:

- `ArrayDeque` có mô hình thời gian hằng số khấu hao cho phần lớn thao tác hai đầu thông thường.
- `PriorityQueue` cần duy trì cấu trúc ưu tiên; tài liệu mô tả thêm và lấy đầu theo O(log n), trong khi xem đầu là O(1).
- Tìm hoặc xóa một phần tử theo giá trị bằng các thao tác của `Collection` có thể có chi phí khác với lấy đầu hàng.

Những khác biệt đó phù hợp với nhiệm vụ của cấu trúc: lấy đúng ưu tiên khác với chỉ quản lý hai đầu theo lịch sử thao tác. Không lựa chọn một hàng ưu tiên cho một yêu cầu FIFO chỉ vì cả hai đều có `offer` và `poll`. [ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html), [PriorityQueue: chi phí thao tác](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)

Không dùng thứ tự `toString` hoặc một phép chạy nhỏ để đo tốc độ hay chứng minh mọi bảo đảm thứ tự. Tính đúng của chính sách được đọc từ hợp đồng và kiểm chứng bằng thao tác tương ứng.

## 11. Những lỗi thường gặp

| Cách hiểu hoặc cách viết | Vấn đề | Cách sửa |
| --- | --- | --- |
| Mọi Queue đều FIFO | Nhầm giao diện với chính sách triển khai | Xác định phần tử đầu được chọn theo quy tắc nào |
| PriorityQueue phải lấy phần tử đến sớm nhất | Ưu tiên mới là tiêu chí chính | Đọc Comparator hoặc thứ tự tự nhiên |
| Số priority lớn luôn được xử lý trước | Chiều ưu tiên là quy ước của bộ so sánh | Viết và kiểm tra chiều so sánh |
| Cùng priority chắc chắn FIFO | Trường hợp hòa không được PriorityQueue cam kết ổn định | Thiết kế tiêu chí phụ khi cần |
| Compare bằng 0 làm PriorityQueue loại trùng | Mang quy tắc của tập sắp xếp sang hàng đợi | Phân biệt thứ tự với việc giữ phần tử |
| For-each của PriorityQueue chính là thứ tự poll | Bộ duyệt không hứa thứ tự ưu tiên | Poll hoặc sắp một bản sao để quan sát đúng câu hỏi |
| Peek làm chuyển sang phần tử tiếp theo | Peek không xóa đầu hàng | Dùng poll khi cần lấy và loại khỏi hàng |
| Offer không bao giờ có ngoại lệ | Vẫn có thể có phần tử không hợp lệ | Phân biệt thiếu dung lượng với vi phạm miền đầu vào |
| Offer tự chờ khi đầy | Queue không cung cấp thao tác chờ theo nghĩa đó | Dùng giao diện/phương thức chờ phù hợp nếu cần |
| Gọi push/pop rồi vẫn nghĩ đang FIFO | Đã dùng cùng một đầu như ngăn xếp | Giữ nhất quán cách dùng hai đầu |
| Poll null luôn có nghĩa mọi hàng đều không chứa phần tử | Hàng chuyên biệt có điều kiện lấy như DelayQueue | Đọc hợp đồng cụ thể |
| FIFO bảo đảm hoàn thành công việc cùng thứ tự | Thứ tự lấy và thời gian xử lý bên ngoài khác nhau | Tách hai khái niệm |

Khi dự đoán output, hãy ghi lại loại triển khai, các thao tác đã dùng để đưa vào/lấy ra, và quy tắc chọn đầu. Chỉ nhìn tên biến `queue` không đủ.

## 12. Tự luyện và đối chiếu

### Bài 1: cùng dữ liệu, ba chính sách

Đưa lần lượt `8, 3, 5` vào ba hàng rỗng: FIFO, ưu tiên số nhỏ trước và LIFO. Lấy cho tới khi hết các phần tử có thể lấy của ba hàng thông thường này.

Đối chiếu:

```text
FIFO:     8, 3, 5
Ưu tiên:  3, 5, 8
LIFO:     5, 3, 8
```

Cần giải thích mỗi phần tử đầu tiên được chọn theo quy tắc nào.

### Bài 2: xem hay lấy?

Một FIFO có `[A, B]`:

```text
peek() → A; hàng vẫn [A, B]
poll() → A; hàng còn [B]
peek() → B; hàng vẫn [B]
poll() → B; hàng rỗng
poll() → null
```

Nếu thay lời gọi cuối bằng `remove()`, với hàng đã rỗng sẽ nhận `NoSuchElementException`.

### Bài 3: ưu tiên trước, sequence sau

Ba công việc được đưa vào:

```text
A: priority=2, sequence=0
B: priority=1, sequence=1
C: priority=1, sequence=2
```

Với bộ so ưu tiên tăng dần rồi sequence tăng dần, thứ tự là `B, C, A`.

Nếu bỏ tiêu chí sequence và chỉ so priority, vẫn có ba phần tử, A vẫn đứng sau nhóm priority 1, nhưng không được khẳng định B hay C lấy trước chỉ dựa vào thứ tự tới.

### Bài 4: Deque đang là FIFO hay LIFO?

- Thêm A, B, C bằng `offerLast`, lấy bằng `pollFirst`: FIFO.
- Thêm A, B, C bằng `push`, lấy bằng `pop`: LIFO.
- Trộn thêm ở hai đầu mà không có một quy tắc chung: không thể chỉ gọi tên cấu trúc để kết luận lịch sử là FIFO.

### Bài 5: đọc đúng offer thất bại

Một `ArrayBlockingQueue` dung lượng 2 đang có `A, B`. `offer("C")` trả về `false` vì không có chỗ để thêm ngay. Hai phần tử cũ vẫn ở đó theo FIFO.

Nếu dùng `add("C")` trong trạng thái đầy đó, tình huống thiếu chỗ được báo bằng `IllegalStateException`. Đó là khác biệt cách báo thất bại, không phải một chính sách thứ tự mới.

## 13. Tài liệu tra cứu

- [Queue Java 17 — giao diện, đầu hàng và các chính sách thứ tự](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html)
- [ArrayDeque — triển khai deque cho cách dùng FIFO/LIFO](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html)
- [Deque — thao tác hai đầu và ngăn xếp](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)
- [PriorityQueue — ưu tiên, trường hợp hòa và giới hạn thứ tự duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)
- [Collections.asLifoQueue — khung nhìn Queue theo LIFO](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#asLifoQueue(java.util.Deque))
- [BlockingQueue — các khả năng chờ riêng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)
- [ArrayBlockingQueue — hàng FIFO có giới hạn](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html)
- [DelayQueue — điều kiện hết hạn trước khi lấy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/DelayQueue.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="queue-deque-002">Các cặp add–offer, remove–poll và element–peek</a>

<details>
<summary>Xem chi tiết</summary>

# Các cặp add–offer, remove–poll và element–peek

## 1. Ba công việc, hai cách báo không thực hiện được

Một hàng đợi lưu phần tử trước khi chúng được xử lý. Khi dùng nó, bạn thường cần ba việc: đưa thêm phần tử vào, lấy phần tử ra, hoặc chỉ xem phần tử sắp được lấy.

Mỗi việc có hai phương thức tương ứng trong `Queue`. Khi thao tác có thể thực hiện được, các phương thức trong cùng cặp có mục đích tương đương. Chúng khác nhau rõ nhất khi hàng đợi đầy hoặc rỗng:

- `add(e)` và `offer(e)` dùng để thêm.
- `remove()` và `poll()` dùng để lấy ra và xóa.
- `element()` và `peek()` dùng để xem nhưng giữ nguyên phần tử.

Một phía báo tình trạng không thực hiện được bằng ngoại lệ; phía còn lại dùng giá trị đặc biệt là `false` hoặc `null`. Tuy nhiên, điều đó không có nghĩa mọi lỗi dữ liệu của `offer` đều được biến thành `false`.

Bài học sẽ giải thích từng cặp, kiểm chứng hàng đợi rỗng và hàng đợi có giới hạn dung lượng, rồi làm rõ các nhầm lẫn về null, thứ tự và phương thức `remove` có đối số. Mã hoàn chỉnh dùng API có trong Java 17.

Bạn cần biết biến, lời gọi phương thức, điều kiện và vòng lặp. Cách đọc kiểu `Queue<String>`, các giá trị trả về và các ngoại lệ chính được giới thiệu tại chỗ.

## 2. Queue, đầu hàng đợi và lớp triển khai

`Queue<E>` là một interface, trong đó `E` là kiểu phần tử. Ví dụ:

~~~java
Queue<String> pending = new ArrayDeque<>();
~~~

Đây là khai báo minh họa; cần nhập `java.util.Queue` và `java.util.ArrayDeque` khi đặt vào chương trình.

`Queue<String>` cho biết đoạn mã đang làm việc với hợp đồng hàng đợi chuỗi. `ArrayDeque` là lớp cụ thể cung cấp đối tượng phía sau biến. Bạn có thể gọi các thao tác của `Queue` qua biến `pending` mà không cần biết chi tiết mảng bên trong.

**Đầu hàng đợi** (head) là phần tử mà `remove()` hoặc `poll()` sẽ lấy ra. `element()` và `peek()` cũng quan sát chính đầu này, nhưng không lấy nó ra.

Trong một hàng đợi FIFO — vào trước, ra trước — phần tử mới thường được thêm vào đuôi, còn việc lấy ra diễn ra ở đầu:

~~~text
lấy ra ← đầu [A, B, C] đuôi ← thêm mới

A được lấy trước B, B trước C.
~~~

Các tên phương thức không tự bảo đảm mọi lớp triển khai đều FIFO. Ví dụ `PriorityQueue` chọn đầu theo độ ưu tiên. Vì vậy, trước khi dự đoán phần tử được trả về, phải biết quy tắc thứ tự của lớp đang dùng. [Java 17: Queue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html).

## 3. Bảng hợp đồng của sáu phương thức

Với một hàng đợi có thể sửa và dữ liệu hợp lệ:

| Mục đích | Dạng dùng ngoại lệ | Dạng dùng giá trị đặc biệt | Khi thành công |
| --- | --- | --- | --- |
| Thêm một phần tử | `add(e)` | `offer(e)` | Thêm phần tử và trả `true` |
| Lấy ra đầu hàng đợi | `remove()` | `poll()` | Trả phần tử đầu và xóa nó |
| Xem đầu hàng đợi | `element()` | `peek()` | Trả phần tử đầu, không xóa |

Các tình huống không thực hiện được cần đọc riêng:

| Tình huống | Phương thức | Kết quả |
| --- | --- | --- |
| Hết chỗ do giới hạn dung lượng | `add(e)` | `IllegalStateException` |
| Hết chỗ do giới hạn dung lượng | `offer(e)` | `false` |
| Không có phần tử để lấy | `remove()` | `NoSuchElementException` |
| Không có phần tử để lấy | `poll()` | `null` |
| Không có phần tử để xem | `element()` | `NoSuchElementException` |
| Không có phần tử để xem | `peek()` | `null` |

`add` và `offer` trả `boolean`, còn bốn phương thức đọc/lấy trả phần tử kiểu `E`. Đừng đọc `poll` như một phép chỉ trả lời có xóa được hay không: nó trả chính phần tử vừa được lấy ra.

“Dạng dùng giá trị đặc biệt” chỉ nói về tình huống đầy hoặc rỗng tương ứng. Những vi phạm hợp đồng khác như thêm null vào lớp cấm null có thể vẫn ném ngoại lệ.

## 4. add và offer: thêm giống nhau, báo hết chỗ khác nhau

Với hàng đợi FIFO đang có `[A]` và còn chỗ:

~~~text
add(B)   → true; hàng đợi thành [A, B]
~~~

Nếu thay bằng `offer(B)` trên cùng trạng thái ban đầu, việc thêm cũng thành công và trả `true`.

Không phải `add` tự thêm ở đầu còn `offer` tự thêm ở cuối. Vị trí thêm do hợp đồng của lớp triển khai quyết định; hai tên trong cặp không biểu thị hai hướng khác nhau.

Sự khác biệt cần quan sát xuất hiện khi hàng đợi có sức chứa cố định. Ví dụ, `ArrayBlockingQueue` được tạo với sức chứa 2:

~~~java
Queue<String> bounded = new ArrayBlockingQueue<>(2);
~~~

Sau khi thêm A và B, không còn chỗ cho C:

~~~text
offer(C) → false
add(C)   → IllegalStateException
~~~

C không được thêm trong cả hai trường hợp trên. Hàng đợi vẫn chứa `[A, B]`. `offer` không âm thầm thay A bằng C và cũng không tự xóa phần tử cũ để tạo chỗ.

Khi lấy A ra, hàng đợi còn B và có chỗ trống. Một lần `offer(C)` mới có thể thành công, tạo `[B, C]`. Đây là một thao tác thêm mới ở thời điểm sau, không phải lần offer bị từ chối trước đó đang chờ ngầm.

`ArrayBlockingQueue` là hàng đợi có dung lượng cố định. Chương trình trong bài dùng nó để tạo tình huống đầy một cách xác định, không tạo thêm luồng hoặc chờ đợi. [Java 17: ArrayBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html).

## 5. Khi offer trả false, chương trình phải quyết định tiếp theo

Chọn `offer` khi việc hàng đợi hết chỗ là một tình huống mà mã gọi cần xử lý bằng nhánh thông thường. Ví dụ, một hàm nhận yêu cầu có thể trả lại kết quả “chưa nhận được” cho nơi gửi.

Nếu bỏ qua giá trị trả về:

~~~java
// Minh họa việc bỏ qua kết quả; không chứng minh phần tử đã được nhận.
pending.offer("job-3");
~~~

bạn không biết phần tử đã vào hàng đợi hay bị từ chối. Không có ngoại lệ không đồng nghĩa thêm thành công.

Một phương thức minh họa thể hiện rõ việc nhận được hay chưa:

~~~java
static boolean trySubmit(Queue<String> pending, String job) {
    return pending.offer(job);
}
~~~

Đây là một phương thức để đặt trong lớp, giả sử chính sách của hàng đợi và dữ liệu đầu vào đã được xác định. Bên gọi vẫn phải dùng kết quả, chẳng hạn báo chưa nhận hoặc thực hiện chính sách thử lại có kiểm soát.

`true` chỉ xác nhận phần tử được thêm theo thao tác hàng đợi. Nó không chứng minh công việc đã được xử lý xong, kết quả đã được lưu hay phần tử không thể bị một tác nhân khác lấy ra ngay sau đó.

Chọn `add` khi không thêm được do dung lượng thể hiện một điều bất thường mà hợp đồng của đoạn mã muốn báo bằng ngoại lệ. Không có quy tắc rằng `offer` luôn đúng còn `add` luôn sai; cách chọn phải phù hợp với ý nghĩa tình trạng hết chỗ đối với bên gọi.

Cũng không dùng vòng lặp thử `offer` liên tục không có điều kiện dừng chỉ để làm mất kết quả false. Nếu cần chờ có chỗ, hãy chọn rõ một API và chính sách chờ phù hợp thay vì biến một thao tác tức thời thành vòng lặp tiêu tốn tài nguyên.

## 6. offer không có nghĩa “không bao giờ ném ngoại lệ”

Một `ArrayDeque` không nhận phần tử `null`. Cả:

~~~java
pending.add(null);
pending.offer(null);
~~~

đều ném `NullPointerException` nếu `pending` trỏ tới một `ArrayDeque`. Hai dòng trên là minh họa riêng từng lời gọi, không phải hai lệnh cần chạy nối tiếp mà không xử lý lỗi.

`false` của `offer` không được dùng như một mã chung thay cho mọi lỗi lập trình. Một kiểu phần tử không được lớp triển khai chấp nhận, hoặc thuộc tính phần tử vi phạm giới hạn của lớp đó, cũng có thể dẫn đến ngoại lệ thích hợp.

Đối với `PriorityQueue`, các phần tử còn phải so sánh được theo quy tắc mà hàng đợi dùng. Việc đổi từ `add` sang `offer` không tự tạo ra thứ tự cho các đối tượng không so sánh được.

Quy tắc thực hành là tách:

- **Không thể thêm lúc này vì dung lượng:** xử lý kết quả của offer hoặc ngoại lệ của add.
- **Phần tử vi phạm hợp đồng đầu vào:** sửa/kiểm tra dữ liệu hoặc xử lý lỗi theo hợp đồng của chương trình.

Không bao quanh mọi thứ bằng `catch (Exception)` rồi coi tất cả thành “hàng đợi đầy”. Cách đó che lẫn lỗi dữ liệu với tình huống không có chỗ.

## 7. remove() và poll(): cùng lấy ra đầu hàng đợi

Với FIFO đang chứa `[A, B]`:

~~~text
remove() → trả A; còn [B]
~~~

Nếu dùng `poll()` trên cùng trạng thái ban đầu, cũng trả A và để lại B.

Cả hai đều xóa phần tử được trả về. Không cần gọi `remove` thêm sau khi đã `poll` thành công. Nếu bạn làm vậy, có thể lấy nhầm phần tử kế tiếp.

Khi hàng đợi rỗng:

~~~text
remove() → NoSuchElementException
poll()   → null
~~~

Chọn `poll` khi hết phần tử là một kết quả dự kiến, chẳng hạn một lượt xử lý lấy hết những gì hiện có. Chọn `remove` khi đoạn mã có hợp đồng phải có phần tử và việc rỗng là dấu hiệu vi phạm giả định cần được báo rõ.

Với một hàng đợi đã quy định không chứa null, có thể viết:

~~~java
String next = pending.poll();
if (next != null) {
    System.out.println("Received: " + next);
}
~~~

Đây là đoạn minh họa với biến `pending` đã khai báo. Nếu `next` khác null, phần tử đã rời hàng đợi trước khi lệnh in chạy. Nếu xử lý sau đó thất bại, Queue không tự đưa nó trở lại.

Đừng gán ngay kết quả `poll` cho biến nguyên thủy như `int` nếu hàng đợi có thể rỗng. Kết quả `Integer` bằng null sẽ gây lỗi khi tự mở gói, trước khi bạn kịp kiểm tra tình trạng không có dữ liệu.

## 8. element và peek: chỉ xem, không lấy ra

Với hàng đợi ổn định `[A, B]`:

~~~text
element() → A; hàng đợi vẫn [A, B]
peek()    → A; hàng đợi vẫn [A, B]
~~~

Nếu không có thao tác khác làm thay đổi dữ liệu, gọi lại sẽ vẫn xem A. Hai phương thức này không đánh dấu “A đã xử lý” và không làm B lên đầu.

Khi rỗng:

~~~text
element() → NoSuchElementException
peek()    → null
~~~

Một lỗi phổ biến là dùng `peek` làm cả điều kiện lẫn thao tác xử lý trong vòng lặp, nhưng không bao giờ xóa:

~~~java
// Minh họa lỗi: nếu hàng đợi không đổi, phần tử đầu được đọc lặp lại.
while (pending.peek() != null) {
    System.out.println(pending.peek());
}
~~~

Đoạn này không phù hợp để lấy hết dữ liệu; trên hàng đợi không rỗng và không bị thay đổi, nó không tự tiến qua phần tử kế tiếp. Dùng `poll` để lấy ra hoặc dùng bộ duyệt nếu chỉ muốn đọc từng phần tử một lần theo chính sách duyệt của lớp.

`peek` cũng không giữ chỗ hoặc dành riêng phần tử cho người đọc. Một lần `peek` rồi một lần `poll` sau đó là hai lời gọi riêng. Nếu có mã khác hoặc luồng khác xen vào sửa hàng đợi, không được mặc định hai lần ấy đang nói về cùng phần tử.

## 9. Chương trình đầy đủ kiểm chứng sáu phương thức

Lưu mã thành `QueueMethodPairsDemo.java`. Ví dụ dùng:

- `ArrayDeque` cho thao tác FIFO cơ bản và chính sách cấm null.
- `ArrayBlockingQueue` sức chứa 2 để kiểm chứng hết chỗ.
- `LinkedList` để làm lộ sự mơ hồ nếu cho null vào Queue.
- `PriorityQueue` để phân biệt đầu theo ưu tiên với đầu theo lịch sử đến.
- `Deque` để liên hệ các tên phương thức.

Hàm `drainNonNullQueue` có điều kiện dữ liệu rõ trong tên: hàng đợi không lưu null. Chương trình chỉ gọi nó với PriorityQueue, lớp cấm null.

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class QueueMethodPairsDemo {
    static <E> List<E> drainNonNullQueue(Queue<E> queue) {
        List<E> values = new ArrayList<>();
        E value;
        while ((value = queue.poll()) != null) {
            values.add(value);
        }
        return values;
    }

    public static void main(String[] args) {
        Queue<String> fifo = new ArrayDeque<>();
        System.out.println("fifo add A=" + fifo.add("A"));
        System.out.println("fifo offer B=" + fifo.offer("B"));
        System.out.println("fifo=" + fifo);
        System.out.println("peek=" + fifo.peek());
        System.out.println("element=" + fifo.element());
        System.out.println("after inspection=" + fifo);
        System.out.println("poll=" + fifo.poll());
        System.out.println("after poll=" + fifo);
        System.out.println("remove=" + fifo.remove());
        System.out.println("after remove=" + fifo);
        System.out.println("empty poll=" + fifo.poll());
        System.out.println("empty peek=" + fifo.peek());
        try {
            fifo.remove();
        } catch (NoSuchElementException exception) {
            System.out.println("empty remove=" + exception.getClass().getSimpleName());
        }
        try {
            fifo.element();
        } catch (NoSuchElementException exception) {
            System.out.println("empty element=" + exception.getClass().getSimpleName());
        }

        Queue<String> bounded = new ArrayBlockingQueue<>(2);
        System.out.println("bounded offer A=" + bounded.offer("A"));
        System.out.println("bounded add B=" + bounded.add("B"));
        System.out.println("bounded full offer C=" + bounded.offer("C"));
        System.out.println("bounded after rejected offer=" + bounded);
        try {
            bounded.add("C");
        } catch (IllegalStateException exception) {
            System.out.println("bounded full add=" + exception.getClass().getSimpleName());
        }
        System.out.println("bounded after rejected add=" + bounded);
        System.out.println("bounded poll=" + bounded.poll());
        System.out.println("bounded retry offer C=" + bounded.offer("C"));
        System.out.println("bounded remaining=" + bounded);

        Queue<Integer> growing = new ArrayDeque<>(1);
        System.out.println("growing offers="
                + growing.offer(1) + "/" + growing.offer(2) + "/" + growing.offer(3));
        System.out.println("growing size=" + growing.size());

        Queue<String> noNull = new ArrayDeque<>();
        try {
            noNull.offer(null);
        } catch (NullPointerException exception) {
            System.out.println("offer null=" + exception.getClass().getSimpleName());
        }
        try {
            noNull.add(null);
        } catch (NullPointerException exception) {
            System.out.println("add null=" + exception.getClass().getSimpleName());
        }
        System.out.println("after invalid inserts size=" + noNull.size());

        Queue<String> permissive = new LinkedList<>();
        permissive.offer(null);
        permissive.offer("tail");
        System.out.println("nullable peek=" + permissive.peek());
        System.out.println("nullable size after peek=" + permissive.size());
        System.out.println("nullable poll=" + permissive.poll());
        System.out.println("nullable empty after poll=" + permissive.isEmpty());
        System.out.println("nullable remaining=" + permissive);

        Queue<String> named =
                new ArrayDeque<>(List.of("head", "middle", "tail"));
        System.out.println("remove middle=" + named.remove("middle"));
        System.out.println("after value remove=" + named);
        System.out.println("remove head=" + named.remove());
        System.out.println("remove missing=" + named.remove("missing"));
        System.out.println("after both removals=" + named);

        Queue<Integer> priority = new PriorityQueue<>();
        priority.addAll(List.of(7, 2, 5, 2));
        System.out.println("priority size=" + priority.size());
        System.out.println("priority peek=" + priority.peek());
        System.out.println("priority remove=" + priority.remove());
        System.out.println("priority poll=" + priority.poll());
        System.out.println("priority drain=" + drainNonNullQueue(priority));
        System.out.println("priority empty=" + priority.isEmpty());

        Deque<String> deque = new ArrayDeque<>();
        Queue<String> sameObject = deque;
        sameObject.offer("first");
        deque.offerLast("second");
        System.out.println("queue peek/deque peekFirst="
                + sameObject.peek() + "/" + deque.peekFirst());
        System.out.println("queue remove=" + sameObject.remove());
        System.out.println("deque pollFirst=" + deque.pollFirst());
        System.out.println("deque empty=" + deque.isEmpty());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 QueueMethodPairsDemo.java
java QueueMethodPairsDemo
~~~

Dùng JDK 17 trở lên. Tùy chọn `--release 17` kiểm tra mã và API theo Java 17. Tên tệp khớp với lớp `public`.

Kết quả chuẩn:

~~~text
fifo add A=true
fifo offer B=true
fifo=[A, B]
peek=A
element=A
after inspection=[A, B]
poll=A
after poll=[B]
remove=B
after remove=[]
empty poll=null
empty peek=null
empty remove=NoSuchElementException
empty element=NoSuchElementException
bounded offer A=true
bounded add B=true
bounded full offer C=false
bounded after rejected offer=[A, B]
bounded full add=IllegalStateException
bounded after rejected add=[A, B]
bounded poll=A
bounded retry offer C=true
bounded remaining=[B, C]
growing offers=true/true/true
growing size=3
offer null=NullPointerException
add null=NullPointerException
after invalid inserts size=0
nullable peek=null
nullable size after peek=2
nullable poll=null
nullable empty after poll=false
nullable remaining=[tail]
remove middle=true
after value remove=[head, tail]
remove head=head
remove missing=false
after both removals=[tail]
priority size=4
priority peek=2
priority remove=2
priority poll=2
priority drain=[5, 7]
priority empty=true
queue peek/deque peekFirst=first/first
queue remove=first
deque pollFirst=second
deque empty=true
~~~

## 10. Đọc kết quả theo từng cặp

### 10.1. Thành công không làm lộ khác biệt về báo lỗi

`fifo add A=true` và `fifo offer B=true` cho thấy cả hai thêm thành công. Hàng đợi FIFO là `[A, B]`.

`peek=A` và `element=A` đi cùng `after inspection=[A, B]`. Đây là bằng chứng rằng cặp xem đầu không xóa.

`poll=A` để lại B; `remove=B` làm rỗng hàng đợi. Mỗi thao tác trong cặp lấy ra đã loại đúng một phần tử.

### 10.2. Hàng đợi rỗng tách hai kiểu kết quả

`empty poll=null` và `empty peek=null` dùng giá trị đặc biệt. `empty remove=NoSuchElementException` và `empty element=NoSuchElementException` dùng ngoại lệ.

Các khối `try/catch` trong chương trình bắt đúng lỗi đang được minh họa để tiếp tục các ví dụ sau. Chúng không biến mọi ngoại lệ phát sinh ở nơi khác thành kết quả rỗng.

### 10.3. Hàng đợi đầy tách add khỏi offer

Sau hai lần thêm vào sức chứa 2, `bounded full offer C=false` cho biết C chưa được nhận. Chuỗi `[A, B]` không đổi.

`bounded full add=IllegalStateException` cũng không thêm C. Hàng đợi không bị trộn thêm một bản C sau ngoại lệ trong ví dụ này.

Khi `bounded poll=A` tạo chỗ trống, lần thử offer tiếp theo trả true và kết quả là `[B, C]`. C được thêm đúng một lần, ở lần thử thành công sau đó.

### 10.4. Dữ liệu không hợp lệ không trở thành false

`offer null=NullPointerException` và `add null=NullPointerException` xảy ra trên ArrayDeque rỗng, nên không thể giải thích bằng “hết chỗ”.

`after invalid inserts size=0` xác nhận không có thành viên nào được thêm trong hai lời gọi lỗi đó.

## 11. Dung lượng khởi tạo không nhất thiết là sức chứa tối đa

`new ArrayDeque<>(1)` không tạo một hàng đợi chỉ được giữ một phần tử. ArrayDeque dùng mảng có thể tăng dung lượng theo nhu cầu.

Chương trình thêm 1, 2, 3 và in:

~~~text
growing offers=true/true/true
growing size=3
~~~

Tham số khởi tạo ở đây là gợi ý dung lượng ban đầu đủ chứa số phần tử được yêu cầu, không phải giới hạn một thành viên. Vì thế, dùng ArrayDeque để thử “offer trả false khi thêm phần tử thứ hai” là một phép thử sai giả định.

ArrayDeque không đặt giới hạn dung lượng cố định theo hợp đồng sử dụng của nó. Điều đó không có nghĩa tài nguyên máy tính vô hạn hoặc mọi lời gọi đều được bảo đảm thành công trong mọi điều kiện. Đừng diễn giải lỗi tài nguyên như tình trạng đầy có thể được Queue.offer biểu diễn phổ quát bằng false. [Java 17: ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html).

ArrayBlockingQueue có một sức chứa cố định được chỉ định lúc tạo. Đây là lý do bài dùng hai lớp khác nhau cho hai mục đích kiểm chứng: sự phát triển dung lượng và việc từ chối vì đã đạt giới hạn.

Khi đọc mã thực tế, hãy xác định tham số trong hàm khởi tạo là dung lượng ban đầu hay giới hạn tối đa. Không suy ý nghĩa của cùng một số chỉ từ việc hai lớp đều có chữ “Array”.

## 12. Tránh dùng null làm phần tử của Queue

`poll` và `peek` dùng null để biểu thị không có đầu hàng đợi. Nếu chính dữ liệu đầu vào có thể là null, giá trị trả về không còn đủ để phân biệt hai trường hợp.

Nhiều lớp, trong đó ArrayDeque, PriorityQueue và ArrayBlockingQueue, cấm null. Tuy nhiên, `LinkedList` cho phép null ngay cả khi đối tượng được nhìn qua kiểu Queue.

Chương trình cố ý tạo một hàng đợi như vậy:

~~~text
[null, tail]
~~~

`peek` trả null nhưng `size` vẫn là 2 vì không xóa gì. Sau đó `poll` cũng trả null, nhưng lần này đã lấy thành viên null ra; hàng đợi vẫn còn `tail` và `isEmpty` là false.

Vì vậy, câu “poll trả null thì Queue luôn rỗng” chỉ phù hợp khi hợp đồng phần tử đã loại null. Không được suy ra điều đó cho mọi lớp triển khai mà không kiểm tra.

Tài liệu Queue khuyến cáo không dùng null làm phần tử ngay cả ở lớp cho phép nó. Ví dụ LinkedList của bài là phản ví dụ để hiểu sự mơ hồ, không phải cách biểu diễn công việc được đề xuất. [Java 17: LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html).

Nếu ứng dụng cần một thông điệp có ý nghĩa “không có giá trị” hoặc “kết thúc”, dùng một mô hình thông điệp tường minh phù hợp với dữ liệu, thay vì chiếm dụng null đang được API dùng làm kết quả đặc biệt. Mô hình ấy phải được xử lý như một phần của hợp đồng ứng dụng.

## 13. remove() khác remove(Object)

Tên `remove` có hai dạng quan trọng:

| Lời gọi | Ý nghĩa | Kiểu kết quả |
| --- | --- | --- |
| `queue.remove()` | Lấy ra đầu hàng đợi | Phần tử kiểu E |
| `queue.remove(value)` | Tìm và xóa một phần tử phù hợp giá trị | boolean |

Dạng không đối số là cặp của `poll` trong bài. Dạng có đối số được kế thừa từ `Collection` và không có nghĩa “lấy đầu”.

Với ArrayDeque FIFO chứa `[head, middle, tail]`:

~~~text
remove("middle") → true
hàng đợi        → [head, tail]

remove()        → head
hàng đợi        → [tail]

remove("missing") → false
~~~

Trong trường hợp tìm theo giá trị, không tìm thấy thường được báo bằng false theo hợp đồng; đừng gán lỗi rỗng của `remove()` cho mọi lời gọi có cùng tên. [Java 17: Collection.remove(Object)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#remove(java.lang.Object)).

Với biến mang kiểu `Queue<Integer>`, `remove(1)` là tìm phần tử có giá trị số 1 qua dạng đối số Object, không phải xóa ở chỉ số 1 như một số cách gọi trên List. Queue không cung cấp thao tác xóa theo chỉ số.

Cũng đừng kỳ vọng hai dạng có cùng chi phí. Ví dụ, xóa ở đầu của ArrayDeque khác với tìm một giá trị ở giữa. Khi đọc hiệu năng, phải xác định đúng chữ ký phương thức, không chỉ nhìn tên remove.

## 14. Cùng poll nhưng đầu của PriorityQueue khác FIFO

Chương trình đưa `7, 2, 5, 2` vào PriorityQueue theo thứ tự tự nhiên của Integer. Nó không in toàn bộ PriorityQueue rồi giả định chuỗi đó là thứ tự lấy ra.

Các kết quả:

~~~text
priority size=4
priority peek=2
priority remove=2
priority poll=2
priority drain=[5, 7]
~~~

Đầu hàng đợi là giá trị ưu tiên nhỏ nhất theo quy tắc hiện tại, không phải số 7 đến trước. Hai lần xuất hiện của số 2 cùng được giữ; đây không phải một Set loại trùng.

`remove()` và `poll()` vẫn thực hiện cùng mục đích lấy ra đầu. Chúng không chọn hai chính sách ưu tiên khác nhau. `peek` vẫn chỉ xem đầu.

Với những phần tử có cùng độ ưu tiên nhưng là các đối tượng khác nhau, không suy thứ tự đến trước từ việc khóa ưu tiên bằng nhau nếu lớp không cam kết như vậy. Nếu yêu cầu cần phân biệt thứ tự ở mức ấy, mô hình và bộ so sánh phải phản ánh yêu cầu.

Bộ duyệt của PriorityQueue không được cam kết đi qua theo thứ tự ưu tiên. Để kiểm chứng trình tự lấy ra trong bài, chương trình dùng các lời gọi lấy đầu thật sự. [Java 17: PriorityQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).

## 15. Quan hệ với Deque: thêm đuôi, đọc/lấy đầu khi dùng như Queue

`Deque` mở rộng Queue và cho thao tác ở cả hai đầu. Khi dùng các phương thức Queue trên một Deque, chúng tương ứng về thao tác với:

| Queue | Phương thức Deque tương ứng |
| --- | --- |
| `add(e)` | `addLast(e)` |
| `offer(e)` | `offerLast(e)` |
| `remove()` | `removeFirst()` |
| `poll()` | `pollFirst()` |
| `element()` | `getFirst()` |
| `peek()` | `peekFirst()` |

Bảng mô tả hướng và tác dụng. Cần vẫn đọc đúng chữ ký: chẳng hạn `Queue.add` trả boolean, còn `Deque.addLast` có kiểu trả về void.

Chương trình giữ hai biến nhìn vào cùng một ArrayDeque, một biến kiểu Deque và một biến kiểu Queue. Thêm `first` qua offer của Queue rồi thêm `second` qua offerLast của Deque tạo cùng chuỗi thành viên.

`sameObject.peek()` và `deque.peekFirst()` cùng thấy first. Sau khi remove qua biến Queue lấy first, pollFirst qua biến Deque lấy second. Không có hai kho dữ liệu độc lập chỉ vì hai biến có kiểu khác nhau.

Quan hệ này giúp bạn đọc mã khi các tên phương thức có hậu tố First/Last xuất hiện cạnh tên Queue ngắn hơn. Nó không có nghĩa được tự đổi việc thêm vào đầu thành thêm vào đuôi; các phương thức như addFirst phục vụ một hướng thao tác khác. [Java 17: Deque, bảng tương ứng với Queue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html).

## 16. Các phương thức này không tự chờ dữ liệu hoặc chờ chỗ

Tên `ArrayBlockingQueue` có thể khiến người mới nghĩ mọi lời gọi đều đợi. Cần đọc chính xác phương thức và chữ ký.

Trong chương trình, `offer(e)` trả kết quả theo việc có thể chèn mà không đợi chỗ trống xuất hiện; `poll()` không đợi một phần tử mới đến. Không có tham số thời gian chờ trong những chữ ký này.

`BlockingQueue` bổ sung các thao tác chờ như `put`, `take` và các biến thể có thời hạn. Chúng không nằm trong sáu chữ ký Queue đang học. Sự tồn tại của các phương thức bổ sung không làm `add` hay `element` tự biến thành thao tác chờ. [Java 17: BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html).

Cũng không suy tính an toàn khi nhiều luồng cùng dùng chỉ từ tên interface Queue. ArrayDeque trong bài không hỗ trợ tự do dùng chung và sửa từ nhiều luồng. ArrayBlockingQueue có hợp đồng phối hợp riêng, nhưng một chuỗi kiểm tra rồi hành động vẫn cần được đọc từng bước.

Ví dụ, “kiểm tra chưa đầy rồi add” là hai thao tác; tác nhân khác có thể thêm vào giữa. Tương tự, “peek thấy A rồi poll” không phải tự động dành A cho bên vừa peek.

Khi chỉ cần thử thêm, dùng kết quả của chính lời gọi offer để biết thao tác vừa thực hiện có được chấp nhận hay không. Khi cần lấy ra, dùng kết quả của chính poll. Đừng xem một lần kiểm tra trước đó như bảo đảm vĩnh viễn cho trạng thái sau.

## 17. Chọn cặp nào theo hợp đồng của đoạn mã?

Không cần học sáu tên như sáu hành vi rời rạc. Chọn theo hai bước: xác định mục đích, rồi xác định cách biểu diễn tình trạng không thực hiện được.

| Hoàn cảnh | Lựa chọn cần cân nhắc |
| --- | --- |
| Đưa vào hàng đợi có thể đầy và cần báo chưa nhận | `offer` rồi kiểm tra boolean |
| Việc không thêm được vi phạm giả định của đoạn mã | `add` và hợp đồng ngoại lệ rõ |
| Lấy công việc nếu hiện có, hết là tình trạng bình thường | `poll` rồi kiểm tra null khi dữ liệu cấm null |
| Đoạn mã yêu cầu chắc chắn có đầu để lấy | `remove()`, rỗng sẽ báo lỗi |
| Chỉ xem đầu, rỗng là kết quả dự kiến | `peek` |
| Chỉ xem đầu, rỗng vi phạm giả định | `element` |

Không bỏ qua kết quả offer. Không gọi poll nếu chỉ cần xem. Không bắt NoSuchElementException như một cách che lỗi ở mọi bước mà không biết hàng đợi đáng lẽ có dữ liệu hay không.

Với một lượt lấy hết hàng đợi cấm null, hàm trong chương trình:

~~~java
static <E> List<E> drainNonNullQueue(Queue<E> queue) {
    List<E> values = new ArrayList<>();
    E value;
    while ((value = queue.poll()) != null) {
        values.add(value);
    }
    return values;
}
~~~

vừa làm rỗng hàng đợi vừa tạo danh sách kết quả. Nó không phải hàm đọc giữ nguyên dữ liệu và không thích hợp với hàng đợi có phần tử null.

Nếu chỉ cần duyệt để hiển thị, chọn thao tác đọc và xem hợp đồng thứ tự duyệt của lớp cụ thể. Nếu cần giữ mọi dữ liệu nguồn nguyên vẹn, đừng truyền nó vào một hàm lấy hết rồi mới nhận ra chữ drain có nghĩa là rút dữ liệu ra.

## 18. Những lỗi thường gặp

| Nhận định | Cách đọc đúng |
| --- | --- |
| “Add thêm đầu, offer thêm cuối” | Trong cùng lớp Queue, hai phương thức cùng mục đích thêm; khác cách báo không có chỗ |
| “Offer không ném ngoại lệ” | Nó vẫn có thể báo dữ liệu không hợp lệ, như null bị cấm |
| “Không có ngoại lệ nghĩa là offer thành công” | Phải kiểm tra boolean trả về |
| “Offer true nghĩa là công việc đã xử lý xong” | Nó mới xác nhận thao tác thêm được chấp nhận |
| “Poll chỉ xem đầu” | Poll trả phần tử và xóa nó |
| “Peek sẽ chuyển sang phần tử kế tiếp mỗi lần gọi” | Nếu dữ liệu không đổi, nó tiếp tục xem cùng đầu |
| “Remove luôn ném khi không tìm thấy” | Phân biệt remove không đối số với remove(Object) |
| “new ArrayDeque(2) chỉ giữ được hai phần tử” | Tham số là dung lượng khởi tạo, không phải giới hạn cố định |
| “Mọi Queue đều FIFO” | Đọc lớp triển khai; PriorityQueue là ví dụ khác |
| “Poll null luôn chứng minh rỗng” | Cần hợp đồng phần tử cấm null |
| “Có chữ Blocking thì offer(e) tự đợi chỗ” | Phải xem đúng chữ ký; có những phương thức chờ riêng |
| “Peek rồi poll là một hành động” | Đó là hai lời gọi; thay đổi xen vào có thể làm kết quả khác |
| “Queue<Integer>.remove(1) là xóa chỉ số 1” | Qua kiểu Queue, đó là dạng tìm theo giá trị Object |

Để tìm lỗi nhanh, ghi ra trạng thái trước, chữ ký chính xác, giá trị trả về hoặc ngoại lệ và trạng thái sau. Bốn thông tin đó thường đủ để tách lỗi hiểu API khỏi lỗi quy tắc thứ tự.

## 19. Thực hành có lời giải

### Bài 1: đi qua ba mục đích

FIFO ban đầu là `[A, B]`. Gọi peek, poll, element, remove theo thứ tự. Mỗi lần trả gì và tập cuối thế nào?

**Lời giải:** Peek trả A và giữ nguyên. Poll trả A, còn B. Element trả B và giữ B. Remove trả B, hàng đợi rỗng.

### Bài 2: thao tác khi rỗng

Trên ArrayDeque rỗng, poll và peek trả gì? Remove không đối số và element làm gì?

**Lời giải:** Poll và peek trả null. Remove và element ném NoSuchElementException. Không có phần tử nào bị thêm hoặc tạo mới để thay thế.

### Bài 3: giới hạn hai phần tử

ArrayBlockingQueue sức chứa 2 đã có X và Y. Offer Z thất bại rồi add Z được thử. Hai kết quả và nội dung là gì?

**Lời giải:** Offer trả false, add ném IllegalStateException. Hàng đợi vẫn là `[X, Y]`. Không có Z chờ ngầm bên trong.

### Bài 4: thử lại sau khi có chỗ

Tiếp bài trước, poll lấy X ra rồi offer Z lại. Điều gì xảy ra?

**Lời giải:** Còn Y và một chỗ trống. Offer Z mới trả true, hàng đợi thành `[Y, Z]`. Lần offer thành công là một lời gọi sau, không phải kết quả trì hoãn của lời gọi bị từ chối.

### Bài 5: offer null

Bạn đổi `add(null)` sang `offer(null)` trên ArrayDeque để tránh ngoại lệ. Cách đó có đúng không?

**Lời giải:** Không. ArrayDeque cấm null nên cả hai đều ném NullPointerException. Sự khác nhau về đầy không xóa bỏ hợp đồng đầu vào.

### Bài 6: một vòng peek

Vòng lặp kiểm tra peek khác null rồi in peek nhưng không xóa có tự xử lý lần lượt các phần tử không?

**Lời giải:** Không. Nếu hàng đợi không đổi, nó cứ xem cùng đầu. Dùng poll để lấy từng phần tử ra, hoặc bộ duyệt nếu chỉ đọc mà giữ dữ liệu.

### Bài 7: remove có đối số

FIFO là `[one, two, three]`. Remove("two") trả gì? Sau đó remove() trả gì?

**Lời giải:** Remove có đối số trả true và xóa two, còn `[one, three]`. Remove không đối số trả phần tử one và xóa đầu, còn three.

### Bài 8: dung lượng khởi tạo

Bạn tạo `new ArrayDeque<Integer>(1)` rồi offer ba số hợp lệ. Có thể kết luận offer thứ hai sẽ false không?

**Lời giải:** Không. ArrayDeque có thể tăng dung lượng và không coi 1 là sức chứa tối đa. Chương trình đã kiểm chứng ba lần offer đều true trong điều kiện bình thường.

### Bài 9: thứ tự ưu tiên

Đưa `9, 4, 6` vào PriorityQueue tự nhiên. Peek, remove và poll lần lượt trả gì nếu không có thao tác xen vào?

**Lời giải:** Peek trả 4 và giữ nguyên. Remove trả 4, còn 6 và 9. Poll tiếp theo trả 6. Không phải 9 đến trước sẽ được lấy trước.

### Bài 10: null trong LinkedList

Queue dùng LinkedList chứa `[null, tail]`. Poll trả null. Có thể dừng xử lý và khẳng định đã hết dữ liệu không?

**Lời giải:** Không. Lời gọi vừa lấy thành viên null ra; tail vẫn còn. Đây là lý do cần tránh null làm dữ liệu Queue và xác định tiền điều kiện cho các vòng poll đến null.

### Bài 11: liên hệ Deque

Trên một Deque dùng như Queue, offer tương ứng đầu hay cuối? Remove không đối số tương ứng phía nào?

**Lời giải:** Offer thêm ở cuối, tương ứng offerLast. Remove không đối số lấy ở đầu, tương ứng removeFirst. Peek nhìn đầu qua peekFirst.

### Bài 12: chữ Blocking

Biến Queue trỏ tới ArrayBlockingQueue đang đầy. Gọi offer(e) không có thời hạn thì lời gọi có tự chờ đến khi có chỗ không?

**Lời giải:** Không đợi chỗ trống xuất hiện; nó báo không thêm được bằng false. Các thao tác chờ hoặc có thời hạn thuộc những chữ ký bổ sung của BlockingQueue và phải được chọn rõ.

## 20. Cách nhớ để đọc đúng mã thật

Đầu tiên, phân loại thao tác là **thêm**, **lấy ra** hay **chỉ xem**. Sau đó đọc cách báo không thực hiện được: ngoại lệ hoặc false/null. Cuối cùng, kiểm tra lớp triển khai để biết thứ tự, giới hạn dung lượng, chính sách null và cách dùng khi có nhiều luồng.

Với mỗi lời gọi, giá trị trả về nói về chính thao tác đó: offer true là đã thêm; poll khác null là đã lấy ra khi dữ liệu cấm null; peek khác null chỉ là đã nhìn thấy đầu tại thời điểm gọi. Giữ các ý nghĩa này tách bạch sẽ tránh phần lớn lỗi sử dụng ba cặp phương thức.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="queue-deque-003">Giá trị đặc biệt so với ngoại lệ khi rỗng hoặc đầy</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị đặc biệt so với ngoại lệ khi rỗng hoặc đầy

## 1. Rỗng và đầy là trạng thái; mã gọi quyết định chúng có bất thường không

Một hàng đợi nhận công việc có thể chưa có gì để xử lý. Một bộ đệm có giới hạn có thể đã hết chỗ. Cả hai đều là những trạng thái có thể dự kiến trong hoạt động bình thường.

Nhưng ở một đoạn mã khác, chương trình có thể đã xác lập điều kiện “phải còn một phần tử để lấy”. Nếu điều kiện ấy sai, tiếp tục im lặng như không có chuyện gì lại che mất lỗi.

Java cung cấp hai cách biểu diễn tình trạng không thực hiện được thao tác với `Queue` và `Deque`:

- Trả một **giá trị báo hiệu**, thường là `null` khi không có phần tử hoặc `false` khi không thêm được vì dung lượng.
- Ném một **ngoại lệ**, chẳng hạn `NoSuchElementException` khi cần phần tử nhưng cấu trúc rỗng, hoặc `IllegalStateException` khi thêm vào cấu trúc đã đầy.

Hai cách này không tự thay đổi thực tế “hiện có phần tử hay không” hoặc “còn chỗ hay không”. Chúng thay đổi cách kết quả được chuyển tới mã gọi.

Bài học tập trung vào việc chọn và xử lý đúng tín hiệu ấy. Chúng ta sẽ kiểm chứng cả hai đầu Deque, phân biệt trạng thái đầy/rỗng với lỗi đầu vào, và xử lý các bẫy của null, số 0 và tự mở gói. Mọi ví dụ chạy được dùng API có trong Java 17.

Bạn cần biết biến, điều kiện, vòng lặp và lời gọi phương thức. Không cần biết cách mảng hay danh sách liên kết được tổ chức bên trong để hiểu các hợp đồng này.

## 2. Hai cách biểu diễn kết quả khác nhau ở luồng xử lý

Xét một hàng đợi không chứa phần tử null và đang rỗng.

Với `poll()`:

~~~text
gọi poll
   ↓
nhận null như một giá trị trả về bình thường
   ↓
mã gọi kiểm tra và chọn nhánh xử lý
~~~

Với `remove()`:

~~~text
gọi remove
   ↓
ném NoSuchElementException
   ↓
luồng thực hiện thông thường rời lời gọi;
ngoại lệ được xử lý ở một nơi bắt thích hợp hoặc tiếp tục truyền ra
~~~

Không có phần tử nào được trả về bình thường ở lời gọi remove trên cấu trúc rỗng. Ngoại lệ không phải một giá trị mà bạn so sánh bằng `== null`.

Ngược lại, null do poll trả về không tự kích hoạt một khối catch. Nếu mã sau đó tự mở gói null thành kiểu nguyên thủy và gây lỗi, đó là một sự kiện khác cần xác định đúng vị trí.

Một giá trị báo hiệu đòi hỏi mã gọi phải kiểm tra nó. Một ngoại lệ đòi hỏi hợp đồng của đoạn mã xác định nơi xử lý hoặc cho phép nó truyền ra. Không cách nào tự cung cấp chính sách nghiệp vụ như “thử lại”, “bỏ công việc” hay “dừng một giai đoạn”.

## 3. Bản đồ cơ bản của Queue

`Queue<E>` mô tả một cấu trúc chứa phần tử để lấy theo chính sách của lớp triển khai. Đầu hàng đợi là phần tử sẽ được lấy ra tiếp theo; không mặc định mọi Queue đều dùng cùng một quy tắc thứ tự.

Ba công việc có hai dạng:

| Công việc | Dạng báo bằng ngoại lệ | Dạng báo bằng giá trị |
| --- | --- | --- |
| Thêm phần tử | `add(e)` | `offer(e)` |
| Lấy ra và xóa đầu | `remove()` | `poll()` |
| Chỉ xem đầu | `element()` | `peek()` |

Với dữ liệu hợp lệ:

- Khi thêm được, add và offer đều trả true.
- Khi đầy do giới hạn dung lượng, add ném IllegalStateException, offer trả false.
- Khi rỗng, remove và element ném NoSuchElementException.
- Khi rỗng, poll và peek trả null.

Đừng bỏ qua phần “lấy ra” so với “chỉ xem”. Poll thành công làm mất một thành viên khỏi hàng đợi; peek thành công giữ nguyên nó. Thay tên phương thức để đổi cách báo rỗng không được làm bạn vô tình đổi luôn tác dụng lên dữ liệu. [Java 17: Queue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html).

Các dạng remove ở bảng đều **không có đối số**. Phương thức remove(Object) tìm theo giá trị là một chữ ký khác, không phải dạng báo rỗng của cặp remove/poll.

## 4. Deque áp dụng cùng nguyên tắc ở cả hai đầu

`Deque` mở rộng Queue để thao tác ở đầu trước và đầu sau. Hậu tố `First`/`Last` chọn phía; nhóm tên phương thức chọn công việc và cách báo không thực hiện được.

| Phía | Công việc | Dạng ngoại lệ | Dạng giá trị báo hiệu |
| --- | --- | --- | --- |
| Đầu | Thêm | `addFirst(e)` | `offerFirst(e)` |
| Cuối | Thêm | `addLast(e)` | `offerLast(e)` |
| Đầu | Lấy ra | `removeFirst()` | `pollFirst()` |
| Cuối | Lấy ra | `removeLast()` | `pollLast()` |
| Đầu | Chỉ xem | `getFirst()` | `peekFirst()` |
| Cuối | Chỉ xem | `getLast()` | `peekLast()` |

Các phương thức offer trả boolean. AddFirst và addLast trả void: việc hoàn thành bình thường biểu thị thao tác đã thêm xong, còn khi đầy chúng báo lỗi bằng ngoại lệ. Đừng gán kết quả của addLast vào một biến boolean chỉ vì Queue.add có kiểu trả về boolean.

Với deque rỗng, các phương thức poll/peek ở cả hai đầu trả null. Các phương thức remove/get ở cả hai đầu ném NoSuchElementException. Với deque bị giới hạn và đã đầy, offer ở phía nào cũng có thể trả false; add ở phía đó ném IllegalStateException.

Đầu và cuối không có hai kho dung lượng riêng. Một deque có sức chứa hai thành viên không thể chứa thêm ở đầu chỉ vì các thành viên cũ được thêm ở cuối. [Java 17: Deque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html).

## 5. Khi rỗng là một kết quả bình thường

Giả sử mỗi lượt chương trình lấy những công việc hiện có rồi kết thúc. Đến cuối lượt, việc không có phần tử tiếp theo là điều được chờ đợi.

Với hợp đồng không lưu null, poll phù hợp để diễn đạt:

~~~java
String task = pending.pollFirst();
if (task == null) {
    System.out.println("Chưa có công việc.");
} else {
    System.out.println("Đã lấy: " + task);
}
~~~

Đoạn minh họa giả sử `pending` là một Deque<String> không chứa null. Nó đọc đúng giá trị trả về của lần lấy vừa thực hiện.

Nhánh không có công việc không đồng nghĩa cấu trúc dữ liệu bị lỗi. Nó có thể chỉ có nghĩa lượt này chưa nhận gì. Không cần tự biến mọi kết quả rỗng thành một ngoại lệ của ứng dụng nếu bên gọi được phép gặp rỗng.

Tương tự, peek thích hợp khi bạn chỉ muốn hỏi có gì ở đầu mà không lấy ra. Nhưng peek không làm tiến độ xử lý thay đổi. Nếu cần xử lý hết, việc chỉ xem đi xem lại sẽ không làm cấu trúc cạn.

Điểm quan trọng là **bình thường hay bất thường do hợp đồng sử dụng quyết định**, không do bản thân từ “rỗng”. Cùng một ArrayDeque có thể được dùng ở hai nơi với hai kỳ vọng khác nhau.

## 6. Khi rỗng chứng tỏ một giả định bị phá vỡ

Một thuật toán có thể yêu cầu lấy đúng một phần tử mà bước trước đã xác lập phải tồn tại. Khi không có mã khác xen vào thay đổi cấu trúc, việc rỗng ở thời điểm ấy có thể cho thấy lỗi trong luồng chương trình.

Dùng removeFirst thể hiện giả định cần có phần tử:

~~~java
String required = pending.removeFirst();
System.out.println(required);
~~~

Đoạn minh họa giả sử pending thuộc một phần thuật toán có hợp đồng phải không rỗng. Nếu không đáp ứng điều kiện đó, NoSuchElementException xuất hiện ngay ở lời gọi lấy.

Nếu thay bằng pollFirst rồi bỏ qua null, lỗi có thể bị đẩy xa hơn: một phép dùng chuỗi mới gây NullPointerException, hoặc một nhánh sau đó lặng lẽ bỏ qua bước đáng lẽ phải chạy. Tín hiệu gần nguyên nhân thường giúp xem lại giả định dễ hơn.

Điều này không phải chỉ dẫn “luôn dùng ngoại lệ để kiểm tra rỗng”. Nếu việc hết công việc được phép xảy ra thường xuyên, dùng poll và nhánh phù hợp thường thể hiện ý định trực tiếp hơn. Ngược lại, nếu rỗng là vi phạm giả định, một lỗi rõ không nên bị đổi thành kết quả bình thường chỉ để tránh nhìn thấy ngoại lệ.

NoSuchElementException là một ngoại lệ thời gian chạy dùng khi phần tử được yêu cầu không tồn tại. Bạn cần đọc đúng phương thức phát sinh nó thay vì coi nó là một tín hiệu chung rằng “mọi thứ đều không thành công”. [Java 17: NoSuchElementException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NoSuchElementException.html).

## 7. Khi đầy là một nhánh cần xử lý, không phải chèn ngầm

Một bộ đệm có sức chứa cố định thường phải có chính sách khi không còn chỗ. Ví dụ, trả kết quả chưa nhận cho phía gửi hoặc thử lại trong một giai đoạn khác.

Với một `LinkedBlockingDeque` được tạo sức chứa 2 và chứa `[older, newer]`:

~~~text
offerFirst(urgent) → false
offerLast(later)   → false
~~~

Hai phần tử mới không được chèn. Không có chuyện urgent âm thầm đẩy newer ra để chiếm chỗ. Cũng không có một lệnh chèn bị từ chối đang chờ sẵn bên trong.

Nếu cùng trạng thái đầy mà gọi addFirst hoặc addLast, chúng ném IllegalStateException. Ví dụ trong bài đối chiếu nội dung trước/sau để xác nhận hai kiểu báo đều giữ nguyên các thành viên đang có trong những thao tác riêng đó.

Sau khi pollLast lấy newer ra, một lần offerFirst mới có thể nhận urgent:

~~~text
[older]
   ↓ offerFirst(urgent) thành công
[urgent, older]
~~~

Giá trị true thuộc về lần thử mới sau khi đã có chỗ. False trước đó không tự đổi thành true khi trạng thái về sau thay đổi.

LinkedBlockingDeque có thể được tạo với giới hạn dung lượng cụ thể. Bài chọn sức chứa 2 để tạo trạng thái đầy dễ quan sát và chỉ dùng một luồng. [Java 17: LinkedBlockingDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html).

## 8. Đừng bỏ qua false rồi xem như công việc đã được nhận

Khi chọn dạng trả giá trị, bạn đồng thời nhận trách nhiệm xử lý kết quả. Gọi offerLast rồi bỏ qua boolean làm mất thông tin quan trọng ở đúng điểm có thể từ chối phần tử.

Một phương thức minh họa có hợp đồng đơn giản:

~~~java
static boolean tryAccept(Deque<String> buffer, String task) {
    return buffer.offerLast(task);
}
~~~

Đây là phương thức để đặt trong lớp, giả sử task và buffer đáp ứng chính sách dữ liệu. Bên gọi vẫn cần dùng kết quả trả về; thêm một lớp bọc mà tiếp tục bỏ qua boolean không giải quyết vấn đề.

Nếu trả false, có thể báo chưa nhận hoặc giữ công việc ở phía gọi để áp dụng chính sách đã định. Không khẳng định đã xếp hàng thành công chỉ vì chương trình chưa đi qua catch.

Nếu trả true, thao tác thêm đã được chấp nhận. Nó chưa chứng minh phần tử được xử lý xong. Hàng đợi chỉ giữ dữ liệu; logic xử lý nằm ở bước khác.

Cũng không thử lại vô hạn bằng một vòng offer liên tục không có chính sách. Việc dùng giá trị báo hiệu không tự mang theo cơ chế giảm tải, chờ chỗ trống hay giới hạn số lần thử.

## 9. Lỗi đầu vào khác với tình trạng không có chỗ

Dạng offer không phải một phiên bản “không bao giờ ném lỗi”. Nếu lớp cấm null, offerFirst(null) hoặc offerLast(null) có thể ném NullPointerException dù cấu trúc đang rỗng và còn nhiều chỗ.

Trong ArrayDeque, null không phải phần tử hợp lệ. Lỗi đó nói về **đầu vào**, không phải số thành viên đã đạt giới hạn.

Tương tự, một Queue yêu cầu các phần tử so sánh được có thể báo lỗi khi nhận kiểu không phù hợp. Đổi từ add sang offer không sửa được quy tắc chấp nhận phần tử của lớp triển khai.

Vì vậy, hãy phân loại kết quả theo đúng nguyên nhân:

| Tín hiệu | Câu hỏi đầu tiên nên kiểm tra |
| --- | --- |
| offer trả false | Lời gọi không nhận phần tử; trạng thái dung lượng và chính sách chèn là gì? |
| NoSuchElementException từ remove/get | Đoạn mã có yêu cầu phần tử khi cấu trúc rỗng không? |
| IllegalStateException từ add ở trạng thái đầy | Giới hạn dung lượng có bị đạt tới không? |
| NullPointerException khi thêm null | Lớp có cấm phần tử null không? |
| NullPointerException sau poll | Mã gọi có dùng hoặc tự mở gói kết quả null không? |

Tên một ngoại lệ riêng lẻ chưa mô tả toàn bộ nguyên nhân. Vị trí phát sinh và hợp đồng của lời gọi mới cho biết nó thuộc nhóm nào.

Đừng bắt một ngoại lệ quá rộng rồi chuyển mọi nguyên nhân thành “đầy” hoặc “rỗng”. Điều đó làm lỗi dữ liệu trông giống trạng thái bình thường, và khiến việc sửa đúng nguyên nhân khó hơn.

## 10. Chương trình đầy đủ dùng Java 17

Lưu thành `QueueFailureSignalsDemo.java`. Chương trình kiểm chứng:

- Bốn phép poll/peek và bốn phép remove/get trên deque rỗng.
- Hai phía chèn trên deque bị giới hạn và đã đầy.
- Việc tạo chỗ rồi thử lại.
- Số 0 hợp lệ so với kết quả null.
- Lỗi tự mở gói và lỗi thêm null.
- Một lớp cho phép null để kiểm tra giới hạn của các kết luận.
- Một giả định phải có phần tử bị vi phạm.

Các ngoại lệ dự kiến được bắt sát từng lời gọi để những nhóm ví dụ sau vẫn chạy. Mã không dùng một khối bắt lỗi chung cho toàn bộ chương trình.

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingDeque;

public class QueueFailureSignalsDemo {
    // Điều kiện: không lưu null và không có mã khác sửa deque trong lượt này.
    static List<String> consumeAvailable(Deque<String> pending) {
        List<String> consumed = new ArrayList<>();
        String task;
        while ((task = pending.pollFirst()) != null) {
            consumed.add(task);
        }
        return consumed;
    }

    public static void main(String[] args) {
        Deque<String> empty = new ArrayDeque<>();
        System.out.println("empty pollFirst=" + empty.pollFirst());
        System.out.println("empty pollLast=" + empty.pollLast());
        System.out.println("empty peekFirst=" + empty.peekFirst());
        System.out.println("empty peekLast=" + empty.peekLast());
        try {
            empty.removeFirst();
        } catch (NoSuchElementException exception) {
            System.out.println("empty removeFirst=" + exception.getClass().getSimpleName());
        }
        try {
            empty.removeLast();
        } catch (NoSuchElementException exception) {
            System.out.println("empty removeLast=" + exception.getClass().getSimpleName());
        }
        try {
            empty.getFirst();
        } catch (NoSuchElementException exception) {
            System.out.println("empty getFirst=" + exception.getClass().getSimpleName());
        }
        try {
            empty.getLast();
        } catch (NoSuchElementException exception) {
            System.out.println("empty getLast=" + exception.getClass().getSimpleName());
        }
        System.out.println("empty unchanged=" + empty.isEmpty());

        Deque<String> limited = new LinkedBlockingDeque<>(2);
        limited.addLast("older");
        limited.addLast("newer");
        List<String> before = new ArrayList<>(limited);
        System.out.println("full initial=" + limited);
        System.out.println("full offerFirst=" + limited.offerFirst("urgent"));
        System.out.println("full offerLast=" + limited.offerLast("later"));
        System.out.println("after false unchanged=" + before.equals(new ArrayList<>(limited)));
        try {
            limited.addFirst("urgent");
        } catch (IllegalStateException exception) {
            System.out.println("full addFirst=" + exception.getClass().getSimpleName());
        }
        try {
            limited.addLast("later");
        } catch (IllegalStateException exception) {
            System.out.println("full addLast=" + exception.getClass().getSimpleName());
        }
        System.out.println("after exceptions unchanged="
                + before.equals(new ArrayList<>(limited)));
        System.out.println("full peekFirst/peekLast="
                + limited.peekFirst() + "/" + limited.peekLast());
        System.out.println("full size after peeks=" + limited.size());
        System.out.println("make room pollLast=" + limited.pollLast());
        System.out.println("retry offerFirst=" + limited.offerFirst("urgent"));
        System.out.println("after retry=" + limited);
        System.out.println("consumed=" + consumeAvailable(limited));
        System.out.println("empty after consume=" + limited.isEmpty());

        Deque<Integer> numbers = new ArrayDeque<>();
        numbers.offerLast(0);
        Integer zero = numbers.pollFirst();
        System.out.println("zero value=" + zero);
        System.out.println("zero is present=" + (zero != null));
        Integer absent = numbers.pollFirst();
        System.out.println("absent value=" + absent);
        try {
            int unsafe = numbers.pollFirst();
            System.out.println("unboxed value=" + unsafe);
        } catch (NullPointerException exception) {
            System.out.println("unboxing empty result=" + exception.getClass().getSimpleName());
        }
        try {
            numbers.offerLast(null);
        } catch (NullPointerException exception) {
            System.out.println("invalid null input=" + exception.getClass().getSimpleName());
        }
        System.out.println("numbers size=" + numbers.size());

        Deque<String> nullable = new LinkedList<>();
        nullable.addLast(null);
        nullable.addLast("tail");
        System.out.println("nullable getFirst=" + nullable.getFirst());
        System.out.println("nullable size after get=" + nullable.size());
        System.out.println("nullable removeFirst=" + nullable.removeFirst());
        System.out.println("nullable size after remove=" + nullable.size());
        System.out.println("nullable remaining=" + nullable);

        Deque<String> required = new ArrayDeque<>();
        required.addLast("ready");
        System.out.println("required first removal=" + required.removeFirst());
        try {
            required.removeFirst();
        } catch (NoSuchElementException exception) {
            System.out.println("broken nonempty assumption="
                    + exception.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 QueueFailureSignalsDemo.java
java QueueFailureSignalsDemo
~~~

Dùng JDK 17 trở lên. `--release 17` kiểm tra mã và API theo Java 17. Tên tệp khớp với lớp public.

Kết quả chuẩn:

~~~text
empty pollFirst=null
empty pollLast=null
empty peekFirst=null
empty peekLast=null
empty removeFirst=NoSuchElementException
empty removeLast=NoSuchElementException
empty getFirst=NoSuchElementException
empty getLast=NoSuchElementException
empty unchanged=true
full initial=[older, newer]
full offerFirst=false
full offerLast=false
after false unchanged=true
full addFirst=IllegalStateException
full addLast=IllegalStateException
after exceptions unchanged=true
full peekFirst/peekLast=older/newer
full size after peeks=2
make room pollLast=newer
retry offerFirst=true
after retry=[urgent, older]
consumed=[urgent, older]
empty after consume=true
zero value=0
zero is present=true
absent value=null
unboxing empty result=NullPointerException
invalid null input=NullPointerException
numbers size=0
nullable getFirst=null
nullable size after get=2
nullable removeFirst=null
nullable size after remove=1
nullable remaining=[tail]
required first removal=ready
broken nonempty assumption=NoSuchElementException
~~~

## 11. Đọc kết quả theo trạng thái trước và sau

### 11.1. Deque rỗng nhưng không bị hỏng

Bốn dòng `empty pollFirst`, `pollLast`, `peekFirst` và `peekLast` đều trả null. Chúng không tạo ra một phần tử mặc định.

Bốn dạng remove/get báo NoSuchElementException. Sau tất cả các lời gọi, `empty unchanged=true` xác nhận deque vẫn rỗng.

Sự khác nhau nằm ở cách chuyển thông tin “không có phần tử” tới mã gọi, không nằm ở việc một phía tự sửa cấu trúc còn phía kia làm nó hỏng.

### 11.2. Từ chối vì đầy không xóa thành viên cũ

Hai lần offer trả false và hai lần add ném IllegalStateException. Chương trình chụp trình tự thành viên trước đó bằng ArrayList rồi so sánh sau từng nhóm:

~~~text
after false unchanged=true
after exceptions unchanged=true
~~~

Đây là phép kiểm tra nội dung và thứ tự trong ví dụ một luồng, không chỉ kiểm tra size. Cùng kích thước chưa đủ để chứng minh không đổi thành viên, nên ảnh chụp giúp đối chiếu rõ hơn.

Kết luận này nói về các lời gọi riêng đã kiểm chứng. Nó không có nghĩa một chuỗi nhiều thao tác khác nhau là một giao dịch sẽ được hoàn tác nếu bước cuối ném ngoại lệ.

### 11.3. Xem không giải phóng dung lượng

`full peekFirst/peekLast=older/newer` và `full size after peeks=2` cho thấy việc xem hai đầu không lấy gì ra.

Phải có thao tác thay đổi như pollLast mới tạo chỗ trong ví dụ. Sau khi lấy newer, offerFirst nhận urgent và thứ tự là `[urgent, older]`.

### 11.4. Hết công việc là điểm dừng hợp lệ

`consumed=[urgent, older]` cho biết hàm consumeAvailable đã lấy hai thành viên. Lần pollFirst tiếp theo trả null, vòng lặp kết thúc và `empty after consume=true`.

Hàm này có tiền điều kiện không chứa null và không có mã khác sửa deque trong lượt đang minh họa. Kết quả không được tự mở rộng thành bảo đảm cho mọi cấu trúc hoặc mọi cách dùng nhiều luồng.

## 12. Số 0 không phải dấu hiệu chuẩn của hàng đợi rỗng

`Queue<Integer>` hoặc `Deque<Integer>` lưu các đối tượng Integer. Một phần tử có giá trị 0 vẫn là phần tử thật.

Trong chương trình:

~~~text
zero value=0
zero is present=true
absent value=null
~~~

Lần pollFirst đầu lấy ra số 0. Lần sau mới không có phần tử và trả null. Nếu mã tự chọn điều kiện `value == 0` để biểu thị rỗng, nó đã làm mất khả năng biểu diễn một dữ liệu hợp lệ.

Không dùng một chuỗi rỗng, số âm hoặc một giá trị tùy tiện khác thay null nếu miền dữ liệu không dành riêng giá trị ấy bằng một hợp đồng tường minh. API đã định nghĩa kết quả báo rỗng; tự đổi ý nghĩa mà không kiểm soát miền đầu vào tạo thêm sự mơ hồ.

Cách kiểm tra an toàn trước khi mở gói trong ví dụ số:

~~~java
Integer next = numbers.pollFirst();
if (next != null) {
    int value = next;
    System.out.println(value);
}
~~~

Đoạn này giả sử numbers là một Deque<Integer> cấm null. Kiểm tra null diễn ra trước chuyển sang int.

## 13. Tự mở gói có thể làm bạn mất tín hiệu bình thường

Dòng:

~~~java
int value = numbers.pollFirst();
~~~

trông như một phép lấy số. Thực tế pollFirst trả Integer, rồi Java chuyển đối tượng đó thành int bằng quá trình tự mở gói (unboxing).

Nếu kết quả là null, quá trình chuyển gây NullPointerException. Trạng thái rỗng ban đầu vốn được API biểu diễn bằng một giá trị bình thường đã trở thành lỗi trong mã gọi.

Chương trình in `unboxing empty result=NullPointerException` để làm rõ đường đi này. Nó cũng in một NullPointerException khác khi cố thêm null vào ArrayDeque. Hai lỗi cùng tên nhưng xuất hiện vì hai nguyên nhân và ở hai thời điểm khác nhau.

Quy tắc ngôn ngữ xác định rằng mở gói tham chiếu null gây NullPointerException. Vì vậy, không thể tránh lỗi bằng cách chỉ đổi kiểu phần tử tổng quát sang Integer mà vẫn gán kết quả ngay vào int. [Java Language Specification 17: unboxing](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8).

Đối với các phần tử kiểu tham chiếu khác, lời gọi phương thức ngay trên kết quả poll cũng gặp vấn đề tương tự nếu không kiểm tra null. Cần quyết định cách xử lý vắng phần tử trước khi dùng dữ liệu như chắc chắn tồn tại.

## 14. Phương thức dùng ngoại lệ không luôn bảo đảm giá trị trả về khác null

Trong đa số ví dụ của bài, ArrayDeque và LinkedBlockingDeque cấm null. Điều đó giúp null báo rỗng không bị lẫn với dữ liệu.

Nhưng LinkedList cho phép null. Chương trình cố ý tạo deque:

~~~text
[null, tail]
~~~

và nhận:

~~~text
nullable getFirst=null
nullable size after get=2
nullable removeFirst=null
nullable size after remove=1
nullable remaining=[tail]
~~~

GetFirst hoàn thành bình thường và trả một thành viên null, vì cấu trúc **không rỗng**. RemoveFirst cũng hoàn thành bình thường, lấy chính thành viên null đó ra.

Như vậy, “ném ngoại lệ khi rỗng” khác với “mọi lần hoàn thành bình thường đều trả giá trị khác null” nếu lớp triển khai cho phép null làm dữ liệu. Ngoại lệ được gắn với việc không có phần tử, không gắn với mọi kết quả null có thể tồn tại.

Với poll/peek trên một lớp cho phép null, null có thể là giá trị phần tử hoặc là tín hiệu không có phần tử. Chỉ bọc kết quả trong một kiểu khác hoặc đổi tên biến không tự khôi phục thông tin đã nhập nhằng.

Tài liệu Queue/Deque khuyến cáo tránh dùng null làm phần tử. Ví dụ LinkedList ở đây là cách kiểm chứng giới hạn của kết luận, không phải lựa chọn tốt để biểu diễn công việc trong hàng đợi. [Java 17: LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html).

## 15. Chọn phạm vi xử lý ngoại lệ sát với hợp đồng

Nếu bạn chọn một phương thức có thể ném ngoại lệ khi rỗng, hãy biết chính xác đoạn nào được phép diễn giải là “không có phần tử”.

Một khối try quá rộng có thể bao cả bước lấy phần tử, bước phân tích dữ liệu và bước xử lý. Nếu phần xử lý cũng ném NoSuchElementException vì một nguồn dữ liệu khác, bắt nó rồi kết luận “deque rỗng” là diễn giải sai nguyên nhân.

Trong mã minh họa, mỗi khối try tập trung vào một lời gọi đang được kiểm chứng. Điều đó giúp liên hệ tín hiệu với trạng thái. Trong mã ứng dụng, bạn có thể cho phép lỗi truyền ra nếu nó là vi phạm giả định cần được sửa thay vì bắt để tiếp tục.

Nếu hết dữ liệu là tình huống dự kiến, poll và một nhánh rõ thường làm phạm vi kết quả vắng mặt dễ đọc hơn. Hàm sau là đoạn trích từ chương trình đầy đủ:

~~~java
static List<String> consumeAvailable(Deque<String> pending) {
    List<String> consumed = new ArrayList<>();
    String task;
    while ((task = pending.pollFirst()) != null) {
        consumed.add(task);
    }
    return consumed;
}
~~~

Hàm trả danh sách phần tử đã lấy ra và làm cạn deque đang được truyền vào trong ví dụ một luồng. Nó không phải phép xem hoặc tạo bản sao giữ nguyên dữ liệu nguồn.

Nếu xử lý phần tử xảy ra sau poll và bị lỗi, deque không tự trả phần tử về chỗ cũ. Việc khôi phục hay đánh dấu lỗi là một hợp đồng khác của ứng dụng, không được suy từ cách poll báo rỗng.

## 16. Không có giới hạn cố định thì đừng tự tạo trạng thái đầy giả

Phương thức offer có nhánh báo không nhận được không có nghĩa mọi lớp đều dễ xuất hiện trạng thái đầy theo cùng cách.

ArrayDeque có thể tăng dung lượng khi cần. Tham số của `new ArrayDeque<>(2)` không phải một giới hạn cứng hai thành viên. Bạn không thể dùng việc thêm phần tử thứ ba để chứng minh offer phải trả false.

LinkedBlockingDeque trong bài được tạo với sức chứa 2 nên tình huống đầy được xác định rõ. Không suy từ chữ “linked” hay “array” để đoán giới hạn; đọc hợp đồng hàm khởi tạo và lớp.

Không có giới hạn cố định theo API cũng không có nghĩa bộ nhớ máy là vô hạn. Các lỗi tài nguyên không phải một tín hiệu dung lượng phổ quát mà offer bắt buộc chuyển thành false. [Java 17: ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html).

Khi viết kiểm thử, trạng thái rỗng cần một cấu trúc không thành viên, còn trạng thái đầy cần một cách triển khai với giới hạn thực sự và phải lấp đủ số chỗ đó. Chọn dữ liệu nhỏ nhưng đúng điều kiện quan trọng hơn việc lặp nhiều lần trên một lớp không có giới hạn ấy.

## 17. Giá trị đặc biệt không phải một cơ chế chờ

Cả kiểu báo bằng giá trị lẫn kiểu báo bằng ngoại lệ đang học đều nói về thao tác tức thời theo các chữ ký cụ thể.

Tên LinkedBlockingDeque không khiến `offerFirst(e)` tự đợi đến khi có chỗ. `pollFirst()` không tự đợi một phần tử mới đến. Trong chương trình, mỗi lời gọi báo ngay trạng thái theo khả năng thêm/lấy mà không chờ điều kiện dung lượng hoặc dữ liệu thay đổi.

BlockingDeque có các phương thức bổ sung như put/take và các biến thể có thời hạn. Nếu yêu cầu là chờ hoặc chờ tối đa một khoảng thời gian, phải chọn đúng chữ ký và hiểu hợp đồng của nó. Không suy rằng việc chuyển từ add sang offer đã cung cấp một chính sách chờ. [Java 17: BlockingDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingDeque.html).

Cũng không coi “kiểm tra rồi làm” là một hành động nguyên tử:

~~~text
isEmpty cho false
   ↓
một tác nhân khác có thể lấy phần tử
   ↓
removeFirst vẫn có thể gặp rỗng
~~~

Tương tự, nhìn thấy một chỗ trống trước đó không bảo đảm một lần add sau vẫn có chỗ trong môi trường có thao tác xen vào.

Nếu việc chưa có phần tử là bình thường, hãy xử lý kết quả của chính lời gọi poll. Nếu cần chờ, chọn API chờ. Nếu cần bảo vệ cả một chuỗi thao tác, thiết kế cách phối hợp phù hợp. ArrayDeque tự nó không cung cấp việc chia sẻ và sửa dữ liệu an toàn giữa nhiều luồng.

Các ví dụ của bài chạy một luồng để kết quả trạng thái rõ ràng; chúng không phải phép thử cạnh tranh giữa các luồng.

## 18. Những nhầm lẫn cần phát hiện sớm

| Nhầm lẫn | Điều cần sửa |
| --- | --- |
| “Rỗng luôn là lỗi” | Xem hợp đồng: có thể là tình trạng bình thường của một lượt lấy công việc |
| “Không muốn lỗi thì cứ đổi mọi remove thành poll” | Có thể che giả định phải có phần tử và đẩy lỗi sang nơi khác |
| “Offer không ném ngoại lệ” | Đầu vào sai vẫn có thể báo lỗi |
| “Không có catch nghĩa là đã thêm thành công” | Phải đọc và xử lý boolean của offer |
| “False nghĩa là phần tử sẽ tự được thêm về sau” | Lần gọi đã không nhận phần tử; thử lại là một thao tác mới |
| “Peek tạo chỗ vì đã nhìn qua phần tử” | Peek/get chỉ đọc, không xóa |
| “Null mở gói thành số mặc định 0” | Mở gói null gây NullPointerException |
| “0 nghĩa là hàng đợi rỗng” | 0 có thể là một phần tử Integer hợp lệ |
| “Get/remove hoàn thành bình thường luôn trả khác null” | Một lớp cho phép null có thể trả chính thành viên null |
| “Đổi null thành chuỗi rỗng sẽ giải quyết mọi nhập nhằng” | Giá trị tự chọn cũng có thể thuộc miền dữ liệu nếu không có hợp đồng riêng |
| “AddFirst trả boolean giống Queue.add” | AddFirst/addLast trả void; offerFirst/offerLast mới trả boolean |
| “Deque có chỗ trống riêng ở mỗi đầu” | Giới hạn dung lượng áp dụng cho tổng số thành viên |
| “Trước đó kiểm tra không rỗng nên remove chắc chắn an toàn” | Cần xem có thay đổi xen vào hay không |
| “Một lệnh lỗi làm hoàn tác cả chuỗi trước đó” | Những thao tác đã hoàn thành không tự biến thành giao dịch |

Khi xem lỗi, giữ lại bốn thông tin: phương thức nào được gọi, trạng thái trước đó, tín hiệu thực nhận và trạng thái sau. Đừng chỉ ghi chung “thao tác thất bại”.

## 19. Thực hành có lời giải

### Bài 1: chọn tín hiệu cho lượt lấy hiện có

Một lượt xử lý được phép kết thúc khi chưa có công việc tiếp theo. PollFirst trả null trên deque cấm null. Có cần biến nó thành NoSuchElementException không?

**Lời giải:** Không nếu rỗng nằm trong hợp đồng bình thường của lượt này. Kiểm tra null và kết thúc hoặc chọn nhánh phù hợp. Ngoại lệ không cung cấp thêm dữ liệu trong trường hợp đã được dự kiến.

### Bài 2: giả định bắt buộc có phần tử

Một bước thuật toán phải lấy phần tử đã được bước trước tạo ra, không có thao tác xen vào. RemoveFirst ném NoSuchElementException. Nên hiểu gì trước tiên?

**Lời giải:** Kiểm tra giả định và luồng tạo/lấy phần tử. Không nên đổi ngay sang poll rồi bỏ qua null chỉ để lỗi biến mất; việc rỗng có thể là dấu hiệu thuật toán sai.

### Bài 3: đầy ở cả hai đầu

Deque sức chứa 2 đã có `[A, B]`. OfferFirst C và offerLast D lần lượt trả gì?

**Lời giải:** Cả hai trả false nếu không có thao tác tạo chỗ xen vào. Dung lượng áp dụng cho cả deque, không phải mỗi phía có hai chỗ.

### Bài 4: cùng trạng thái đầy nhưng dùng add

Vẫn `[A, B]` ở sức chứa 2, addFirst C và addLast D báo thế nào?

**Lời giải:** Mỗi lời gọi riêng ném IllegalStateException do không có chỗ. Trong điều kiện một luồng của ví dụ, thành viên cũ giữ nguyên sau từng lần bị từ chối.

### Bài 5: đọc có tạo chỗ không?

Deque đầy, bạn gọi peekFirst và getLast thành công. Offer sau đó có được bảo đảm true không?

**Lời giải:** Không. Hai lời gọi chỉ xem, không xóa thành viên. Nếu cấu trúc vẫn đầy thì offer vẫn có thể trả false.

### Bài 6: số 0 và vắng phần tử

Deque<Integer> cấm null chứa một số 0. PollFirst trả 0, lần sau trả null. Có thể dùng 0 làm điều kiện kết thúc không?

**Lời giải:** Không nếu 0 thuộc miền dữ liệu. Phải giữ Integer để kiểm tra null trước, rồi mới mở gói thành int khi có phần tử.

### Bài 7: tự mở gói null

Một biến int nhận trực tiếp pollFirst từ deque rỗng. Có nhận được 0 không?

**Lời giải:** Không. Kết quả Integer là null và tự mở gói gây NullPointerException. Không có giá trị int mặc định được trả thay.

### Bài 8: cùng tên lỗi, khác nguyên nhân

Một NullPointerException xuất hiện khi offerLast(null), một lỗi khác xuất hiện ở `int n = deque.pollFirst()`. Có phải cả hai đều nói deque đầy không?

**Lời giải:** Không. Trường hợp đầu có thể là vi phạm chính sách cấm null. Trường hợp sau là mở gói một kết quả null do không có phần tử. Phải xem đúng vị trí và hợp đồng của lời gọi.

### Bài 9: null thật trong LinkedList

Deque dùng LinkedList chứa `[null, X]`. GetFirst và removeFirst có bắt buộc ném lỗi không?

**Lời giải:** Không, vì cấu trúc không rỗng và LinkedList cho phép null. GetFirst trả null mà không xóa; removeFirst trả null và xóa thành viên ấy, để lại X.

### Bài 10: không có chỗ nhưng vẫn báo thành công

Hàm gọi offerLast rồi luôn trả true cho bên gửi. Khi deque đầy, điều gì sai?

**Lời giải:** Hàm bỏ qua false và báo nhận một phần tử chưa được chèn. Cần truyền hoặc xử lý kết quả thật, rồi áp dụng chính sách từ chối/thử lại đã xác định.

### Bài 11: giới hạn khởi tạo

Bạn tạo ArrayDeque với số 2 và kỳ vọng thành viên thứ ba gây IllegalStateException. Giả định nào cần sửa?

**Lời giải:** Số đó không phải sức chứa tối đa cố định của ArrayDeque. Muốn kiểm chứng đầy do giới hạn, dùng một lớp có giới hạn thật như LinkedBlockingDeque được tạo sức chứa 2 trong bài.

### Bài 12: kiểm tra trước khi lấy

Ở môi trường có tác nhân khác cùng lấy dữ liệu, isEmpty vừa trả false. Có thể bỏ qua khả năng removeFirst gặp rỗng không?

**Lời giải:** Không. Trạng thái có thể thay đổi giữa hai lời gọi. Xử lý kết quả của thao tác lấy thực sự, dùng API chờ nếu cần, hoặc bảo vệ chuỗi thao tác theo hợp đồng phối hợp phù hợp.

## 20. Câu hỏi cuối cùng trước khi chọn phương thức

Trước mỗi điểm đọc hoặc chèn, hãy hỏi: **trạng thái rỗng/đầy ở đây là một kết quả được phép hay là dấu hiệu giả định đã bị phá vỡ?**

Nếu là kết quả được phép, chọn dạng trả giá trị và xử lý đầy đủ false/null. Nếu là vi phạm giả định, giữ một tín hiệu lỗi rõ và xác định nơi nó được xử lý hoặc truyền ra.

Sau đó kiểm tra riêng chính sách dữ liệu, nhất là null, kiểu trả về và tác dụng xóa. Cách báo không thực hiện được không thay thế việc hiểu lớp đang dùng, và một giá trị bình thường chỉ hữu ích khi mã gọi giữ nguyên ý nghĩa của nó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="queue-deque-004">Deque và thao tác thêm, xem, xóa ở hai đầu</a>

<details>
<summary>Xem chi tiết</summary>

# Deque và thao tác thêm, xem, xóa ở hai đầu

## 1. Mô hình hai đầu: first và last

`Deque<E>` là hàng hai đầu — double-ended queue. Nó cho phép thêm, xem và lấy/xóa phần tử ở cả hai phía của một dãy.

Trong bài này, luôn vẽ:

```text
first / head                         last / tail
đầu trước       [A] [B] [C]           đầu sau
```

Với trạng thái đó:

- Thêm ở đầu trước đặt phần tử mới trước `A`.
- Thêm ở đầu sau đặt phần tử mới sau `C`.
- Xem đầu trước nhận `A` nhưng giữ nó trong deque.
- Lấy/xóa đầu sau nhận `C` và bỏ nó khỏi deque.

`first` và `last` là vị trí trong dãy hiện tại, không có nghĩa giá trị nhỏ nhất/lớn nhất hoặc cũ nhất/mới nhất trong mọi cách dùng. Chính cách chọn đầu thêm và đầu lấy tạo ra chính sách xử lý.

`Deque` cũng không cung cấp truy cập theo chỉ số như `List`. `getFirst()` không phải một cách viết của `get(0)` được giao diện `Deque` hỗ trợ; nó là phương thức chuyên biệt để xem đầu. [Deque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)

Một cách tạo để thực hành trong một luồng:

```java
Deque<String> values = new ArrayDeque<>();
```

Các ví dụ dùng API tương thích Java 17 và không lưu phần tử `null`.

## 2. Mười hai phương thức, nhìn theo ba việc và hai đầu

Mỗi việc ở mỗi đầu có hai cách báo một số tình huống không thực hiện được:

| Việc | Đầu trước: dạng ngoại lệ | Đầu trước: dạng giá trị đặc biệt | Đầu sau: dạng ngoại lệ | Đầu sau: dạng giá trị đặc biệt |
| --- | --- | --- | --- | --- |
| Thêm | `addFirst(e)` | `offerFirst(e)` | `addLast(e)` | `offerLast(e)` |
| Lấy và xóa | `removeFirst()` | `pollFirst()` | `removeLast()` | `pollLast()` |
| Xem, không xóa | `getFirst()` | `peekFirst()` | `getLast()` | `peekLast()` |

Đọc tên theo hai bước:

1. Phần động từ cho biết thêm, xem hay lấy/xóa.
2. `First` hoặc `Last` cho biết phía nhận thao tác.

Sau đó mới chọn cách muốn xử lý tình huống rỗng hoặc đầy:

| Tình huống | Dạng ngoại lệ | Dạng giá trị đặc biệt |
| --- | --- | --- |
| Không thêm được vì giới hạn dung lượng | `IllegalStateException` từ addFirst/addLast | `false` từ offerFirst/offerLast |
| Xem khi rỗng | `NoSuchElementException` từ getFirst/getLast | `null` từ peekFirst/peekLast |
| Lấy/xóa khi rỗng | `NoSuchElementException` từ removeFirst/removeLast | `null` từ pollFirst/pollLast |

Các cặp không chọn các phần tử khác nhau khi thao tác thành công. Chúng khác cách báo tình huống thất bại tương ứng.

“Dạng giá trị đặc biệt” không có nghĩa mọi lỗi đều được nuốt thành `false` hoặc `null`. Phần tử không hợp lệ vẫn có thể gây ngoại lệ theo hợp đồng triển khai. [Bảng phương thức Deque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)

## 3. Thêm ở hai đầu: addFirst/addLast và offerFirst/offerLast

### 3.1. Tác động lên thứ tự

Giả sử đang có `[B, C]`:

```text
addFirst("A") → [A, B, C]
addLast("D")  → [A, B, C, D]
```

Phần tử mới được đặt đúng phía chỉ định. Deque không tự sắp lại theo bảng chữ cái hoặc theo giá trị.

Với `offerFirst` và `offerLast`, thứ tự khi thêm thành công cũng như vậy:

```java
// Đoạn minh họa.
boolean firstAdded = values.offerFirst("A");
boolean lastAdded = values.offerLast("D");
```

Nếu thành công, mỗi thao tác làm kích thước tăng `1` và trả về `true`.

### 3.2. addFirst/addLast trả void, không trả boolean

Chữ ký:

```java
void addFirst(E value);
void addLast(E value);

boolean offerFirst(E value);
boolean offerLast(E value);
```

Đây là mô tả chữ ký, không phải chương trình độc lập.

Vì `addFirst` và `addLast` trả `void`, không viết:

```java
// Sai kiểu trả về, không biên dịch:
boolean changed = values.addFirst("A");
```

Thành công của một lời gọi `addFirst` được thể hiện bằng việc lời gọi hoàn tất bình thường và phần tử đã được thêm. Nếu muốn nhận cờ để xử lý thiếu chỗ như một tình huống dự kiến, dùng phương thức `offer` tương ứng.

### 3.3. Có đầy hay không phụ thuộc triển khai

`Deque` hỗ trợ cả triển khai có giới hạn dung lượng lẫn triển khai không có một mức tối đa cố định như vậy.

`ArrayDeque` dùng mảng có thể tăng dung lượng. Tham số trong `new ArrayDeque<>(2)` không đặt giới hạn tối đa là hai phần tử.

Ngược lại, một `LinkedBlockingDeque<>(2)` có dung lượng giới hạn hai phần tử. Khi nó đã đầy, `offerFirst` hoặc `offerLast` một đối số trả `false`, còn `addFirst` hoặc `addLast` báo `IllegalStateException`.

Không suy ra hành vi “khi đầy” từ tên giao diện mà chưa biết đối tượng thực tế. [ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html), [LinkedBlockingDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html)

### 3.4. Thêm phần tử lặp vẫn là thêm

Deque không phải tập loại trùng. Với các triển khai trong bài, thêm `"A"` hai lần tạo hai phần tử có thể được lấy ra hai lần.

`offerFirst("A")` không có nghĩa “thêm nếu chưa có A”. Giá trị `false` trong tình huống đầy không mang nghĩa “A đã trùng nên bị từ chối”.

## 4. Xem hai đầu: get và peek không xóa dữ liệu

Với `[A, B, C]`:

```text
getFirst()  → A
peekFirst() → A

getLast()   → C
peekLast()  → C

deque sau các lần xem: [A, B, C]
size vẫn là 3
```

Khác biệt chỉ xuất hiện khi deque rỗng:

- `getFirst()`, `getLast()`: ném `NoSuchElementException`.
- `peekFirst()`, `peekLast()`: trả `null`.

Khi deque chỉ có `[B]`, cả đầu trước lẫn đầu sau đều là cùng phần tử `B`. Đây không phải hai bản sao. Lấy/xóa phần tử duy nhất ở một phía sẽ làm cả deque rỗng.

Phương thức xem trả về tham chiếu tới phần tử, không tự tạo một bản sao sâu của phần tử. “Không xóa” nói về thao tác của deque; nó không biến một đối tượng phần tử có thể sửa đổi thành bất biến.

Nếu muốn liên tục xử lý phần tử tiếp theo, chỉ gọi `peekFirst` trong vòng lặp là chưa đủ: nếu không có thao tác sửa đổi khác, nó vẫn nhìn cùng đầu trước.

## 5. Lấy và xóa ở hai đầu: remove và poll

### 5.1. Xóa đầu nào thì trả phần tử ở đầu đó

Từ `[A, B, C]`:

```text
removeFirst() → trả A; còn [B, C]
pollLast()    → trả C; còn [B]
removeLast()  → trả B; còn []
```

Với thao tác thành công, `removeFirst/removeLast/pollFirst/pollLast`:

- Trả chính phần tử được lấy.
- Loại phần tử đó khỏi deque.
- Làm kích thước giảm `1`.

Chúng không trả `boolean` để báo có thay đổi như một số thao tác xóa theo giá trị.

### 5.2. Chọn cách xử lý rỗng

Khi rỗng là trạng thái bình thường của luồng xử lý, có thể dùng `poll`:

```java
// Đoạn minh họa: values không chứa phần tử null.
String item = values.pollFirst();
if (item == null) {
    System.out.println("Chưa có phần tử để xử lý");
} else {
    System.out.println("Đã lấy: " + item);
}
```

Nếu thuật toán có điều kiện trước bảo đảm phải có phần tử và việc rỗng cần được phát hiện bằng ngoại lệ, một phương thức `remove` có thể diễn đạt yêu cầu đó.

Không bắt ngoại lệ liên tục như một cách mặc định để hỏi deque có phần tử hay không khi API đã cung cấp dạng `poll` phù hợp.

### 5.3. Cẩn thận khi mở hộp giá trị trả về

Với `Deque<Integer>` rỗng:

```java
// Có thể ném NullPointerException do tự mở hộp null:
int item = numbers.pollFirst();
```

`pollFirst` trả `Integer` là `null`; lỗi xảy ra khi chuyển thành `int`. Muốn xử lý trạng thái rỗng, nhận vào `Integer` rồi kiểm tra trước khi mở hộp.

Đừng nhầm lỗi này với `NoSuchElementException` mà `removeFirst` chủ động báo khi rỗng.

## 6. Chương trình đầy đủ: theo dõi các thao tác hai đầu

Lưu toàn bộ mã vào **DequeEndsDemo.java**:

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

public class DequeEndsDemo {
    public static void main(String[] args) {
        Deque<String> values = new ArrayDeque<>();
        values.addLast("B");
        values.addFirst("A");
        System.out.println("offerLast C: " + values.offerLast("C"));
        System.out.println("initial: " + values);

        System.out.println("getFirst: " + values.getFirst());
        System.out.println("getLast: " + values.getLast());
        System.out.println("peekFirst: " + values.peekFirst());
        System.out.println("peekLast: " + values.peekLast());
        System.out.println("after inspections: " + values);
        System.out.println("size: " + values.size());

        System.out.println("removeFirst: " + values.removeFirst());
        System.out.println("after removeFirst: " + values);
        System.out.println("pollLast: " + values.pollLast());
        System.out.println("one element: " + values);
        System.out.println("one-element first: " + values.peekFirst());
        System.out.println("one-element last: " + values.peekLast());
        System.out.println("removeLast: " + values.removeLast());
        System.out.println("empty: " + values.isEmpty());

        System.out.println("empty peekFirst: " + values.peekFirst());
        System.out.println("empty peekLast: " + values.peekLast());
        System.out.println("empty pollFirst: " + values.pollFirst());
        System.out.println("empty pollLast: " + values.pollLast());
        try {
            values.getFirst();
        } catch (NoSuchElementException ex) {
            System.out.println("empty getFirst: NoSuchElementException");
        }
        try {
            values.removeLast();
        } catch (NoSuchElementException ex) {
            System.out.println("empty removeLast: NoSuchElementException");
        }
        try {
            values.offerFirst(null);
        } catch (NullPointerException ex) {
            System.out.println("null offerFirst: NullPointerException");
        }
    }
}
```

Biên dịch theo mức Java 17 và chạy:

```shell
javac --release 17 -encoding UTF-8 DequeEndsDemo.java
java DequeEndsDemo
```

Stdout:

```text
offerLast C: true
initial: [A, B, C]
getFirst: A
getLast: C
peekFirst: A
peekLast: C
after inspections: [A, B, C]
size: 3
removeFirst: A
after removeFirst: [B, C]
pollLast: C
one element: [B]
one-element first: B
one-element last: B
removeLast: B
empty: true
empty peekFirst: null
empty peekLast: null
empty pollFirst: null
empty pollLast: null
empty getFirst: NoSuchElementException
empty removeLast: NoSuchElementException
null offerFirst: NullPointerException
```

Cách đọc kết quả:

1. `addLast("B")` rồi `addFirst("A")` xác định rõ vị trí của từng phần tử.
2. `offerLast("C")` thành công và nối `C` ở cuối.
3. Các lần `get` và `peek` không thay đổi dãy hoặc kích thước.
4. Xóa đầu trước lấy `A`; xóa đầu sau lấy `C`.
5. Khi chỉ còn `B`, xem ở hai phía đều nhận `B`.
6. Khi rỗng, các phương thức `peek/poll` trả `null`; các phương thức `get/remove` tương ứng có thể báo `NoSuchElementException`.
7. `ArrayDeque` từ chối `null` bằng ngoại lệ ngay cả khi dùng `offerFirst`. Tên `offer` không làm phần tử không hợp lệ trở nên hợp lệ.

Trong các dòng in deque, đầu trước nằm ở bên trái, đầu sau nằm ở bên phải.

## 7. Deque dùng như Queue: thêm cuối, lấy đầu

`Deque` mở rộng `Queue`. Các phương thức hàng đợi có sẵn không chọn đầu một cách tùy ý; chúng có ánh xạ rõ:

| Phương thức Queue trên Deque | Đầu và thao tác tương ứng |
| --- | --- |
| `add(e)` | Thêm cuối như addLast |
| `offer(e)` | Thêm cuối như offerLast |
| `remove()` | Lấy/xóa đầu trước như removeFirst |
| `poll()` | Lấy/xóa đầu trước như pollFirst |
| `element()` | Xem đầu trước như getFirst |
| `peek()` | Xem đầu trước như peekFirst |

Lưu ý khác biệt chữ ký: `add(e)` trả `boolean`, trong khi `addLast(e)` trả `void`. Bảng mô tả vị trí và ý nghĩa thao tác, không có nghĩa mọi chữ ký giống hệt nhau.

Cách dùng FIFO điển hình:

```java
Deque<String> pending = new ArrayDeque<>();
pending.offerLast("A");
pending.offerLast("B");
pending.offerLast("C");

String first = pending.pollFirst(); // A
```

Nếu những người dùng cùng deque đều giữ quy tắc thêm cuối/lấy đầu, phần tử vào trước được lấy trước.

Không thể dùng thêm ở cả hai đầu tùy ý rồi suy ra dãy vẫn phản ánh một lịch sử FIFO duy nhất. Cần thống nhất quy tắc sử dụng. [Deque: ánh xạ Queue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)

## 8. Deque dùng như ngăn xếp: thêm và lấy cùng một đầu

Một cách dùng ngăn xếp:

```java
Deque<String> stack = new ArrayDeque<>();
stack.push("A");
stack.push("B");
stack.push("C");

String top = stack.pop(); // C
```

Các phương thức trên `Deque`:

| Phương thức | Tương ứng | Khi rỗng hoặc thiếu chỗ |
| --- | --- | --- |
| `push(e)` | `addFirst(e)` | Có thể báo IllegalStateException nếu không thêm được do giới hạn |
| `pop()` | `removeFirst()` | NoSuchElementException nếu rỗng |
| `peek()` | `peekFirst()` | null nếu rỗng |

Sau ba lần `push`, dãy nhìn từ đầu trước là `[C, B, A]`. `pop` lấy `C`, tức phần tử được đẩy vào gần nhất.

Trong cả cách dùng FIFO lẫn ngăn xếp, `peek()` của `Deque` đều nhìn đầu trước. Nó không tự đổi sang nhìn đầu sau khi người dùng gọi biến là `stack`.

Nếu muốn dùng ngăn xếp nhưng xử lý rỗng bằng `null`, có thể dùng `pollFirst` thay cho `pop` theo yêu cầu. Cần chọn rõ cách báo rỗng, không chỉ đổi tên biến.

Tài liệu `Deque` khuyến nghị dùng giao diện này cho cách dùng ngăn xếp thay vì lớp `Stack` cũ. [Deque: cách dùng LIFO](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)

## 9. Chương trình đầy đủ: ánh xạ phương thức và deque có giới hạn

Lưu mã vào **DequePoliciesAndCapacityDemo.java**:

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

public class DequePoliciesAndCapacityDemo {
    public static void main(String[] args) {
        Deque<Integer> fifo = new ArrayDeque<>();
        fifo.offer(10);
        fifo.add(20);
        fifo.offerLast(30);
        System.out.println("queue aliases: " + fifo);
        System.out.println("queue peek: " + fifo.peek());
        System.out.println("queue element: " + fifo.element());
        System.out.println("queue poll: " + fifo.poll());
        System.out.println("queue remove: " + fifo.remove());
        System.out.println("queue remaining: " + fifo);

        Deque<String> stack = new ArrayDeque<>();
        stack.push("A");
        stack.push("B");
        stack.push("C");
        System.out.println("stack, first on left: " + stack);
        System.out.println("stack peek: " + stack.peek());
        System.out.println("stack pop: " + stack.pop());
        System.out.println("stack remaining: " + stack);

        Deque<String> bounded = new LinkedBlockingDeque<>(2);
        System.out.println("bounded offerLast A: " + bounded.offerLast("A"));
        System.out.println("bounded offerFirst B: " + bounded.offerFirst("B"));
        System.out.println("bounded full: " + bounded);
        System.out.println("full offerFirst X: " + bounded.offerFirst("X"));
        System.out.println("full offerLast Y: " + bounded.offerLast("Y"));
        try {
            bounded.addFirst("X");
        } catch (IllegalStateException ex) {
            System.out.println("full addFirst: IllegalStateException");
        }
        try {
            bounded.addLast("Y");
        } catch (IllegalStateException ex) {
            System.out.println("full addLast: IllegalStateException");
        }
        System.out.println("bounded after failures: " + bounded);
        System.out.println("bounded pollFirst: " + bounded.pollFirst());
        System.out.println("offerLast C after removal: " + bounded.offerLast("C"));
        System.out.println("bounded final: " + bounded);

        Deque<String> growing = new ArrayDeque<>(2);
        growing.addLast("A");
        growing.addLast("B");
        growing.addLast("C");
        System.out.println("ArrayDeque with initial hint 2: " + growing);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 DequePoliciesAndCapacityDemo.java
java DequePoliciesAndCapacityDemo
```

Stdout:

```text
queue aliases: [10, 20, 30]
queue peek: 10
queue element: 10
queue poll: 10
queue remove: 20
queue remaining: [30]
stack, first on left: [C, B, A]
stack peek: C
stack pop: C
stack remaining: [B, A]
bounded offerLast A: true
bounded offerFirst B: true
bounded full: [B, A]
full offerFirst X: false
full offerLast Y: false
full addFirst: IllegalStateException
full addLast: IllegalStateException
bounded after failures: [B, A]
bounded pollFirst: B
offerLast C after removal: true
bounded final: [A, C]
ArrayDeque with initial hint 2: [A, B, C]
```

Cách đọc từng nhóm:

1. `offer`, `add` và `offerLast` đều đưa phần tử vào cuối trong phần dùng hàng đợi.
2. `peek` và `element` nhìn đầu trước; `poll` và `remove()` lấy từ đầu trước.
3. `push` thêm đầu trước, nên thứ tự hiển thị ngăn xếp là `[C, B, A]`.
4. `LinkedBlockingDeque<>(2)` đã đầy sau hai lần thêm thành công.
5. Cả hai phía đều không nhận thêm bằng `offer` khi đầy, kết quả là `false`.
6. Cả hai dạng `add` báo `IllegalStateException` trong trạng thái đầy.
7. Các lần thêm thất bại không đưa `X` hoặc `Y` vào dãy.
8. Sau khi lấy `B`, thêm `C` vào cuối thành công.
9. `new ArrayDeque<>(2)` vẫn nhận được ba phần tử, chứng minh tham số đó không phải giới hạn tối đa hai phần tử.

Trong ví dụ, `LinkedBlockingDeque` chỉ được dùng một luồng để minh họa thiếu dung lượng. Các phương thức `offerFirst(e)` và `offerLast(e)` một đối số vẫn là thử thêm ngay, không tự chờ có chỗ chỉ vì tên lớp có chữ `Blocking`.

`BlockingDeque` có các phương thức chờ hoặc có thời hạn riêng, chẳng hạn `putFirst`, `putLast`, `takeFirst`, `takeLast` và các overload tương ứng. Đó là khả năng bổ sung, không thay đổi ý nghĩa của mười hai phương thức cơ bản. [BlockingDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingDeque.html)

Hai chương trình độc lập trong bài đã được biên dịch bằng `--release 17` và chạy để đối chiếu đúng stdout. Tùy chọn này kiểm tra mức ngôn ngữ/API và tạo bytecode tương thích Java 17; nó không khẳng định JVM thực thi chính là JDK 17.

## 10. Phân biệt xóa ở đầu với tìm và xóa theo giá trị

Tên gần giống nhau có thể diễn đạt những công việc khác:

| Lời gọi | Ý nghĩa |
| --- | --- |
| `removeFirst()` | Lấy/xóa phần tử đang ở đầu trước |
| `removeLast()` | Lấy/xóa phần tử đang ở đầu sau |
| `removeFirstOccurrence(x)` | Tìm lần xuất hiện đầu tiên của giá trị x theo chiều trước tới sau rồi xóa |
| `removeLastOccurrence(x)` | Tìm lần xuất hiện cuối cùng của giá trị x rồi xóa |
| `remove(x)` có đối số | Trên Deque, xóa lần xuất hiện đầu tiên của x |
| `remove()` không đối số | Lấy/xóa đầu trước |

Ví dụ với các bản sao cấu trúc riêng cùng bắt đầu từ `[A, B, A, C]`:

```text
removeFirst()              → trả A; còn [B, A, C]
removeFirstOccurrence("B") → trả true; còn [A, A, C]
removeLastOccurrence("A")  → trả true; còn [A, B, C]
```

Các phương thức `Occurrence` không bắt buộc chỉ xóa đúng ở đầu trước hoặc đầu sau; chúng tìm giá trị trong dãy. Kết quả trả về và chi phí cũng có thể khác thao tác lấy đầu.

Với `Deque<Integer>`, `remove(1)` là xóa giá trị truy vấn `1`, không phải xóa vị trí số 1. Giao diện này không có truy cập/xóa theo chỉ số như `List`. [Deque: các thao tác theo lần xuất hiện](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)

## 11. Duyệt, xem và lấy hết phần tử là ba việc khác nhau

### 11.1. Duyệt không tự xóa

Một vòng `for-each` thông thường trên deque duyệt theo thứ tự từ đầu trước tới đầu sau:

```java
// Đoạn minh họa.
for (String value : values) {
    System.out.println(value);
}
```

Nếu muốn duyệt chiều ngược, `descendingIterator()` cung cấp bộ duyệt từ sau về trước. Nó không tự đảo dãy đang lưu và cũng không tự lấy phần tử ra.

### 11.2. Muốn lấy hết, dùng thao tác lấy/xóa

Với `ArrayDeque<String>` không chứa `null`, xử lý lần lượt từ đầu trước:

```java
String value;
while ((value = values.pollFirst()) != null) {
    System.out.println("Đã lấy: " + value);
}
```

Đoạn này làm deque rỗng khi xử lý hết trong bối cảnh một luồng không có bên khác thêm dữ liệu. Nếu đổi thành `pollLast`, nó lấy từ phía sau.

Chỉ thay `pollFirst` bằng `peekFirst` mà không có thao tác xóa khác có thể làm vòng lặp liên tục gặp cùng một phần tử.

### 11.3. Không sửa cấu trúc tùy tiện qua đường khác trong for-each

Với các triển khai như `ArrayDeque`, đang duyệt bằng một bộ duyệt rồi tự gọi thao tác thêm/xóa đầu có thể vi phạm quy tắc duyệt. Cơ chế fail-fast có thể phát hiện và báo `ConcurrentModificationException`, nhưng không nên dùng việc có hoặc không có ngoại lệ làm bằng chứng tính đúng.

Nếu mục đích là tiêu thụ dãy, một vòng lấy bằng `poll` thường thể hiện ý định rõ hơn. Nếu mục đích là xóa phần tử đang duyệt, dùng cơ chế bộ duyệt hỗ trợ và tuân thủ hợp đồng của nó. [ArrayDeque: bộ duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html)

## 12. Chọn cách dùng và triển khai đúng phạm vi

Một vài câu hỏi thực tế:

| Yêu cầu | Cách chọn thao tác |
| --- | --- |
| Thêm công việc mới vào cuối danh sách chờ | offerLast hoặc addLast tùy chính sách thiếu chỗ |
| Chèn một phần tử vào phía trước theo quy tắc ứng dụng | offerFirst hoặc addFirst |
| Xem công việc tiếp theo mà chưa lấy | peekFirst hoặc getFirst |
| Lấy công việc tiếp theo từ phía trước | pollFirst hoặc removeFirst |
| Lấy phần tử mới nhất được đặt ở cuối | pollLast hoặc removeLast |
| Dùng ngăn xếp đặt đỉnh ở đầu trước | push, pop, peek hoặc các dạng First phù hợp |

Với một luồng và cách dùng deque thông thường, `ArrayDeque` cung cấp các thao tác hai đầu với thời gian hằng số khấu hao cho phần lớn thao tác liên quan, theo mô tả của lớp. Điều đó không có nghĩa mọi phương thức của mọi `Deque`, chẳng hạn tìm một giá trị bên trong, đều O(1).

Nếu cần giới hạn dung lượng hoặc chờ giữa các luồng, phải chọn triển khai và các phương thức tương ứng. `ArrayDeque` không tự đồng bộ cho nhiều luồng cùng sửa. Ngược lại, việc một triển khai hỗ trợ đồng thời cũng không biến một chuỗi nhiều lời gọi rời thành một thao tác nguyên tử của thuật toán.

Ví dụ, “peek rồi poll” là hai lời gọi, không phải một lệnh vừa kiểm tra vừa đặt quyền sở hữu phần tử. Chọn thao tác lấy trực tiếp và xử lý kết quả thường giúp mô tả rõ công việc cần làm.

Không suy ra từ việc dữ liệu nằm trong deque rằng nó đã được sắp xếp, loại trùng hoặc có quyền ưu tiên tự động. Các tính chất đó cần một mô hình hoặc quy tắc riêng.

## 13. Những lỗi thường gặp

| Cách hiểu hoặc cách viết | Vấn đề | Cách sửa |
| --- | --- | --- |
| Nhầm First/Last là nhỏ nhất/lớn nhất | Đó là vị trí hai đầu, không phải thứ tự giá trị | Vẽ dãy với first bên trái, last bên phải |
| Gán addFirst vào boolean | Phương thức trả void | Dùng offerFirst nếu cần cờ |
| Gọi get rồi mong phần tử biến mất | Get chỉ xem | Dùng remove hoặc poll để lấy/xóa |
| Dùng peek trong vòng lặp mà không xóa | Có thể nhìn cùng phần tử mãi | Dùng poll khi cần tiêu thụ |
| Nghĩ poll và remove khác đầu lấy | Cùng đầu tương ứng; khác cách báo rỗng | Tách lựa chọn đầu và lựa chọn báo lỗi |
| Nghĩ offer không bao giờ ném ngoại lệ | Phần tử không hợp lệ vẫn có thể gây lỗi | Phân biệt thiếu chỗ với vi phạm miền dữ liệu |
| Nghĩ ArrayDeque(2) chỉ chứa được hai phần tử | Nhầm dung lượng khởi tạo với giới hạn tối đa | Đọc hợp đồng triển khai |
| Gọi peek() để xem đầu sau | Peek của Deque nhìn đầu trước | Dùng peekLast |
| Nghĩ push thêm vào cuối | Push tương ứng addFirst | Xác định vị trí đỉnh ngăn xếp |
| Nhầm removeFirst với removeFirstOccurrence | Một bên lấy đầu, một bên tìm giá trị | Đọc đủ tên và chữ ký |
| Gán poll của Integer vào int khi có thể rỗng | Tự mở hộp null | Kiểm tra Integer trước |
| Dùng null như dữ liệu khi cũng dùng null làm dấu rỗng | Kết quả trở nên mơ hồ, một số lớp còn từ chối | Giữ miền phần tử không null khi phù hợp |

Để tự kiểm tra một dòng mã, hãy trả lời ba câu: đang tác động đầu nào, có thay đổi dãy không, và khi không thực hiện được sẽ nhận ngoại lệ hay một giá trị đặc biệt?

## 14. Tự luyện và đối chiếu

### Bài 1: theo dõi dãy sau từng thao tác

Bắt đầu với deque rỗng:

```java
values.addLast("B");
values.addFirst("A");
values.offerLast("C");
values.offerFirst("X");
values.peekLast();
values.pollFirst();
values.removeLast();
```

Đối chiếu:

| Bước | Giá trị trả về cần chú ý | Dãy sau bước |
| --- | --- | --- |
| addLast B | void | `[B]` |
| addFirst A | void | `[A, B]` |
| offerLast C | true | `[A, B, C]` |
| offerFirst X | true | `[X, A, B, C]` |
| peekLast | C | `[X, A, B, C]` |
| pollFirst | X | `[A, B, C]` |
| removeLast | C | `[A, B]` |

### Bài 2: một phần tử và rỗng

Deque chỉ có `[7]`. Hãy dự đoán:

```text
peekFirst() → 7
peekLast()  → 7
pollLast()  → 7; deque rỗng
pollFirst() → null
getLast()   → NoSuchElementException
```

Giải thích vì sao xem ở hai đầu không có nghĩa đang có hai phần tử 7.

### Bài 3: chọn theo tình huống

1. Muốn thử thêm vào phía trước, đầy thì xử lý bằng một nhánh boolean: `offerFirst`.
2. Muốn xem phía sau, rỗng là trạng thái bình thường: `peekLast`.
3. Muốn lấy phía trước, rỗng là lỗi cần phát hiện bằng ngoại lệ: `removeFirst`.
4. Muốn lấy phía sau, rỗng thì nhận dấu không có kết quả: `pollLast`.

### Bài 4: phân biệt FIFO và ngăn xếp

Đưa A, B, C vào cuối rồi lấy đầu trước: A, B, C.

Đưa A, B, C bằng push rồi lấy bằng pop: C, B, A.

Cùng một kiểu `ArrayDeque` có thể phục vụ hai cách dùng. Các đầu được chọn mới quyết định chính sách.

### Bài 5: giới hạn dung lượng

Một `LinkedBlockingDeque<String>` dung lượng 2 đang có `[A, B]`.

- `offerLast("C")`: false, dãy giữ nguyên.
- `addFirst("C")`: IllegalStateException, không đưa C vào dãy.
- `pollLast()`: trả B, còn `[A]`.
- `offerFirst("C")`: true, thành `[C, A]`.

Không đổi sang một phương thức chờ nếu yêu cầu của bài chỉ là thử thêm ngay và xử lý kết quả.

## 15. Tài liệu tra cứu

- [Deque Java 17 — các thao tác hai đầu và ánh xạ Queue/ngăn xếp](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)
- [ArrayDeque Java 17 — dung lượng có thể tăng, null và bộ duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html)
- [LinkedBlockingDeque Java 17 — giới hạn dung lượng và các phương thức hai đầu](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html)
- [BlockingDeque Java 17 — phân biệt thử ngay với thao tác chờ](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingDeque.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="queue-deque-005">Dùng Deque làm hàng đợi FIFO hoặc ngăn xếp LIFO</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng Deque làm hàng đợi FIFO hoặc ngăn xếp LIFO

## 1. Cùng một cấu trúc, hai quy tắc chọn việc tiếp theo

Một ứng dụng nhận các công việc A, B, C. Nếu cần xử lý công việc đến trước trước, thứ tự lấy ra là A, B, C. Nhưng khi lưu các thao tác để hoàn tác, thao tác mới nhất thường phải được lấy ra trước: C, B, A.

Hai yêu cầu dùng hai chính sách:

- **FIFO — First In, First Out:** vào trước, ra trước; thường gọi là hàng đợi.
- **LIFO — Last In, First Out:** vào sau, ra trước; thường gọi là ngăn xếp.

`Deque<E>` là giao diện cho một dãy có thể thêm, xem và lấy phần tử ở cả hai đầu. Tên là dạng rút gọn của “double-ended queue”, nghĩa là hàng đợi hai đầu. Vì có hai đầu thao tác, Deque có thể biểu đạt cả FIFO lẫn LIFO.

Điều quyết định hành vi không chỉ là tên lớp, mà còn là **cặp đầu thêm và đầu lấy**:

```text
FIFO: thêm ở một đầu, lấy ở đầu đối diện
LIFO: thêm và lấy ở cùng một đầu
```

Trong Java, quy ước phổ biến là:

```text
FIFO: offerLast / pollFirst
LIFO: push / pop, tương đương addFirst / removeFirst
```

Bài này dùng `ArrayDeque` làm triển khai cụ thể trong mã đơn luồng. Mục tiêu là chọn đúng thao tác, hiểu trường hợp rỗng và thấy chính sách FIFO/LIFO thay đổi cách một thuật toán xử lý dữ liệu.

## 2. Quy ước đầu và cuối phải rõ ràng

Hãy vẽ Deque theo thứ tự từ đầu đến cuối:

```text
head / first                          tail / last
     A  <------------>  B  <------------>  C
```

Các tên `First` và `Last` chỉ đầu của dãy hiện tại. “First” không có nghĩa tự động là phần tử được thêm sớm nhất: nếu liên tục thêm ở đầu, phần tử mới nhất sẽ đứng first.

Có ba nhóm thao tác ở mỗi đầu:

| Việc cần làm | Đầu first | Đầu last |
|---|---|---|
| Thêm | `addFirst`, `offerFirst` | `addLast`, `offerLast` |
| Lấy và gỡ | `removeFirst`, `pollFirst` | `removeLast`, `pollLast` |
| Chỉ xem | `getFirst`, `peekFirst` | `getLast`, `peekLast` |

Tên phương thức cho biết cả hành động lẫn đầu thao tác. Các cặp trong cùng ô khác nhau chủ yếu ở cách biểu đạt một thất bại thông thường như rỗng hoặc không đủ chỗ; phần sau sẽ phân biệt cụ thể.

Theo [Deque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html), giao diện này mở rộng Queue, đồng thời cung cấp các thao tác ở cả hai đầu. Deque không phải List có truy cập theo chỉ số.

Một cách khai báo:

```java
// Trích đoạn; cần import java.util.Deque và java.util.ArrayDeque.
Deque<String> pending = new ArrayDeque<>();
```

`Deque` diễn tả khả năng mà mã gọi cần; `ArrayDeque` chọn cách lưu trữ. Việc dùng một kiểu giao diện không làm đối tượng tự có thêm khả năng đồng bộ hoặc bất biến.

## 3. Dùng Deque làm hàng đợi FIFO

### 3.1. Quy tắc thêm cuối, lấy đầu

Đưa lần lượt A, B, C vào cuối:

```text
offerLast(A): [A]
offerLast(B): [A, B]
offerLast(C): [A, B, C]
```

Sau đó lấy ở đầu:

```text
pollFirst(): A       còn [B, C]
pollFirst(): B       còn [C]
pollFirst(): C       còn []
```

Bất biến cần giữ là: trong các phần tử đang chờ, phần tử được thêm sớm nhất đứng ở đầu lấy tiếp theo.

Mẫu sử dụng với ArrayDeque:

```java
// Trích đoạn minh họa xử lý FIFO trong một luồng.
Deque<String> jobs = new ArrayDeque<>();
jobs.offerLast("A");
jobs.offerLast("B");
jobs.offerLast("C");

String next;
while ((next = jobs.pollFirst()) != null) {
    process(next);
}
```

`process` là xử lý nghiệp vụ trong chương trình bao quanh, không phải hàm có sẵn của Java. Trích đoạn chưa phải chương trình độc lập.

Mẫu dùng null để kết thúc phù hợp ở đây vì ArrayDeque không chấp nhận phần tử null. `pollFirst` trả null nghĩa là không còn phần tử, không bị nhầm với một công việc null thật.

### 3.2. Có thể chỉ dùng giao diện Queue

Nếu mã chỉ cần FIFO, không cần nhìn thấy cả hai đầu:

```java
// Trích đoạn; cần import java.util.Queue và ArrayDeque.
Queue<String> jobs = new ArrayDeque<>();
jobs.offer("A");
jobs.offer("B");
String next = jobs.poll();  // "A"
```

Với một Deque như ArrayDeque, các phương thức kế thừa từ Queue tương ứng:

| Qua Queue | Qua Deque |
|---|---|
| `add(e)` | `addLast(e)` |
| `offer(e)` | `offerLast(e)` |
| `remove()` | `removeFirst()` |
| `poll()` | `pollFirst()` |
| `element()` | `getFirst()` |
| `peek()` | `peekFirst()` |

Dùng kiểu Queue giúp phần mã ấy chỉ thao tác qua giao diện hàng đợi. Nó không ngăn một phần mã khác đang giữ cùng đối tượng Deque gọi thao tác ở đầu khác; quyền sở hữu và chia sẻ tham chiếu vẫn cần rõ.

Cũng không được suy rộng rằng mọi triển khai Queue đều FIFO. [Queue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html) còn có các chính sách khác, chẳng hạn hàng đợi ưu tiên. Ví dụ trong bài là FIFO vì đối tượng ArrayDeque và cặp thao tác đã chọn có quy tắc đó.

### 3.3. FIFO không tự sắp xếp theo giá trị

Nếu lần lượt thêm `"C"`, `"A"`, `"B"`, hàng đợi FIFO lấy ra C, A, B. Nó bảo toàn thứ tự chờ, không sắp xếp từ điển.

Nếu thêm hai công việc bằng nhau theo equals, Deque vẫn có thể giữ cả hai lần xuất hiện. Đây không phải Set khử trùng.

## 4. Dùng Deque làm ngăn xếp LIFO

### 4.1. Chọn đầu first làm đỉnh

Các phương thức quen dùng:

```java
// Trích đoạn minh họa LIFO.
Deque<String> history = new ArrayDeque<>();
history.push("A");
history.push("B");
history.push("C");

String top = history.peek();   // "C", chỉ xem
String removed = history.pop(); // "C", lấy và gỡ
```

Với Deque:

| Phương thức | Ý nghĩa |
|---|---|
| `push(e)` | Thêm ở đầu, tương đương `addFirst(e)` |
| `pop()` | Lấy và gỡ đầu, tương đương `removeFirst()` |
| `peek()` | Chỉ xem đầu, tương đương `peekFirst()`; null khi rỗng |
| `getFirst()` | Chỉ xem đầu, nhưng ném ngoại lệ khi rỗng |

Sau ba lần push:

```text
push(A): [A]
push(B): [B, A]
push(C): [C, B, A]
          ^
         top
```

Pop lần lượt lấy C, B, A. Bất biến là phần tử được push gần nhất trong những phần tử chưa lấy ra đứng tại đỉnh.

### 4.2. Đừng nhầm tên phương thức với vai trò biến

Một biến tên `queue` nhưng gọi `push` và `pop` vẫn có hành vi LIFO. Một biến tên `stack` nhưng thêm cuối rồi lấy đầu vẫn có hành vi FIFO.

Tên biến nên thể hiện ý định, nhưng cặp thao tác mới thực hiện ý định đó.

Nếu muốn lấy ở đỉnh mà xử lý rỗng bằng null, có thể dùng `pollFirst()` thay cho pop. LIFO vẫn đúng vì vẫn lấy ở chính đầu đang được dùng để thêm.

### 4.3. Có thể dùng đầu last làm đỉnh không?

Có. `addLast` với `removeLast`, hoặc `offerLast` với `pollLast`, cũng tạo LIFO. Cả thêm và lấy đều ở cùng đầu.

Tuy nhiên, khi dùng các tên `push`, `pop`, đỉnh theo quy ước API là first. Không push ở first rồi pop bằng removeLast nếu đang muốn LIFO.

Một bảng tự kiểm tra:

| Đầu thêm | Đầu lấy | Chính sách theo thứ tự thêm |
|---|---|---|
| Last | First | FIFO |
| First | Last | FIFO nếu nhất quán dùng cặp này |
| First | First | LIFO |
| Last | Last | LIFO |

Hai dòng FIFO đều hợp lý về toán học của dãy, nhưng quy ước Queue của Java dùng thêm last, lấy first. Chọn một quy ước rõ và giữ nhất quán giúp mã dễ đọc hơn.

## 5. Chương trình 1: cùng đầu vào, hai thứ tự đầu ra

Lưu tệp `DequeFifoLifoDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;

public class DequeFifoLifoDemo {
    public static void main(String[] args) {
        List<String> input = List.of("A", "B", "C");

        Deque<String> fifo = new ArrayDeque<>();
        for (String item : input) {
            fifo.offerLast(item);
        }
        System.out.println("FIFO head to tail = " + fifo);
        System.out.println("FIFO next = " + fifo.peekFirst());
        System.out.println("FIFO size after peek = " + fifo.size());

        List<String> fifoOutput = new ArrayList<>();
        String item;
        while ((item = fifo.pollFirst()) != null) {
            fifoOutput.add(item);
        }
        System.out.println("FIFO output = " + fifoOutput);
        System.out.println("FIFO empty = " + fifo.isEmpty());

        Deque<String> lifo = new ArrayDeque<>();
        for (String value : input) {
            lifo.push(value);
        }
        System.out.println("LIFO head to tail = " + lifo);
        System.out.println("LIFO top = " + lifo.peek());

        List<String> lifoOutput = new ArrayList<>();
        while (!lifo.isEmpty()) {
            lifoOutput.add(lifo.pop());
        }
        System.out.println("LIFO output = " + lifoOutput);
        System.out.println("LIFO empty = " + lifo.isEmpty());

        Queue<String> queueApi = new ArrayDeque<>(input);
        List<String> queueOutput = new ArrayList<>();
        while ((item = queueApi.poll()) != null) {
            queueOutput.add(item);
        }
        System.out.println("Queue API output = " + queueOutput);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 DequeFifoLifoDemo.java
java DequeFifoLifoDemo
```

Kết quả của lần chạy kiểm chứng:

```text
FIFO head to tail = [A, B, C]
FIFO next = A
FIFO size after peek = 3
FIFO output = [A, B, C]
FIFO empty = true
LIFO head to tail = [C, B, A]
LIFO top = C
LIFO output = [C, B, A]
LIFO empty = true
Queue API output = [A, B, C]
```

Các chương trình trong bài dùng ngôn ngữ và API có ở Java 17 trở lên. Chúng được biên dịch với `--release 17` và chạy kiểm chứng bằng Azul OpenJDK 24.0.2.

Những điểm quan trọng:

- FIFO giữ đầu vào A, B, C ở thứ tự từ head đến tail, rồi lấy theo cùng thứ tự.
- LIFO push ở head nên dãy từ head đến tail là C, B, A.
- Peek không làm giảm size.
- Poll hoặc pop có lấy và gỡ phần tử.
- Dùng giao diện Queue trên ArrayDeque cho cùng hành vi FIFO như các thao tác last/first tương ứng.

Không đọc `[C, B, A]` của ngăn xếp thành “ArrayDeque tự đảo mảng khi in”. Đó là dãy logic được tạo bởi ba lần thêm ở đầu.

Cũng không nên chỉ kiểm tra rằng đã lấy đủ ba phần tử. Cả FIFO và LIFO đều có đủ A, B, C; điều khác biệt mà bài toán cần bảo vệ là thứ tự lấy ra.

## 6. Chọn cách xử lý rỗng và không đủ chỗ

Deque cung cấp các nhóm phương thức để thể hiện cách xử lý thất bại.

### 6.1. Lấy và xem khi rỗng

| Việc | Ném `NoSuchElementException` nếu rỗng | Trả null nếu rỗng |
|---|---|---|
| Xem đầu | `getFirst()` | `peekFirst()` |
| Xem cuối | `getLast()` | `peekLast()` |
| Lấy và gỡ đầu | `removeFirst()` | `pollFirst()` |
| Lấy và gỡ cuối | `removeLast()` | `pollLast()` |

`pop()` thuộc nhóm lấy đầu và ném ngoại lệ. `peek()` của **Deque** thuộc nhóm trả null.

Chọn dựa trên ý nghĩa của luồng xử lý:

- Hàng đợi tạm hết việc là tình huống bình thường: poll thường biểu đạt phù hợp.
- Thuật toán đã bảo đảm chắc chắn phải có phần tử: remove hoặc pop có thể làm lộ lỗi giả định.
- Chỉ cần nhìn việc tiếp theo: peek hoặc get, không dùng phương thức gỡ.

Không dùng ngoại lệ như cách lặp bình thường để phát hiện hết dữ liệu nếu có một phương thức trả trạng thái phù hợp.

### 6.2. Thêm vào cấu trúc có giới hạn dung lượng

`addFirst`/`addLast` có thể ném IllegalStateException nếu một triển khai có giới hạn không còn chỗ. `offerFirst`/`offerLast` có thể trả false trong tình huống không đủ chỗ như vậy.

Nhưng offer không có nghĩa “mọi lỗi đều được chuyển thành false”. Ví dụ, đưa null vào ArrayDeque vẫn ném NullPointerException.

ArrayDeque tự tăng sức chứa khi cần, không phải một hàng đợi có giới hạn cố định do tham số constructor đặt ra. Nếu bài toán cần giới hạn chờ hoặc chờ tới khi có chỗ, phải chọn triển khai và API phù hợp; Queue/Deque thông thường không tự cung cấp cơ chế chờ.

### 6.3. Không dùng null như dữ liệu công việc trong ArrayDeque

Null bị từ chối để các phương thức như poll và peek có thể dùng null làm dấu không có phần tử.

Nếu cần một công việc mang ý nghĩa “thiếu dữ liệu” hoặc “kết thúc”, hãy biểu diễn bằng một giá trị hoặc kiểu dữ liệu rõ ràng, không nhét null vào cấu trúc không chấp nhận nó.

Giao diện Deque có thể được triển khai bởi lớp có chính sách null khác, nhưng dùng null cho hàng đợi/ngăn xếp thường tạo mơ hồ với các phương thức trả null. Các mẫu trong bài dựa trên hợp đồng không null của ArrayDeque.

## 7. Chương trình 2: kiểm chứng API rỗng và null

Lưu tệp `DequeEmptyPolicyDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

public class DequeEmptyPolicyDemo {
    static void showEmptyFailure(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected NoSuchElementException");
        } catch (NoSuchElementException ex) {
            System.out.println(label + " = " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        Deque<String> values = new ArrayDeque<>();

        System.out.println("empty peekFirst = " + values.peekFirst());
        System.out.println("empty peekLast = " + values.peekLast());
        System.out.println("empty pollFirst = " + values.pollFirst());
        System.out.println("empty pollLast = " + values.pollLast());
        System.out.println("size after empty reads = " + values.size());

        showEmptyFailure("empty getFirst", values::getFirst);
        showEmptyFailure("empty getLast", values::getLast);
        showEmptyFailure("empty removeFirst", values::removeFirst);
        showEmptyFailure("empty removeLast", values::removeLast);
        showEmptyFailure("empty pop", values::pop);

        try {
            values.offerLast(null);
        } catch (NullPointerException ex) {
            System.out.println("offer null = "
                    + ex.getClass().getSimpleName());
        }
        try {
            values.push(null);
        } catch (NullPointerException ex) {
            System.out.println("push null = "
                    + ex.getClass().getSimpleName());
        }

        System.out.println("offer X = " + values.offerLast("X"));
        System.out.println("peek X = " + values.peekFirst());
        System.out.println("size after peek X = " + values.size());
        System.out.println("pop X = " + values.pop());
        System.out.println("size after pop X = " + values.size());

        Deque<String> growing = new ArrayDeque<>(1);
        growing.offerLast("A");
        growing.offerLast("B");
        growing.offerLast("C");
        System.out.println("initial capacity is not limit = " + growing);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 DequeEmptyPolicyDemo.java
java DequeEmptyPolicyDemo
```

Kết quả của lần chạy kiểm chứng:

```text
empty peekFirst = null
empty peekLast = null
empty pollFirst = null
empty pollLast = null
size after empty reads = 0
empty getFirst = NoSuchElementException
empty getLast = NoSuchElementException
empty removeFirst = NoSuchElementException
empty removeLast = NoSuchElementException
empty pop = NoSuchElementException
offer null = NullPointerException
push null = NullPointerException
offer X = true
peek X = X
size after peek X = 1
pop X = X
size after pop X = 0
initial capacity is not limit = [A, B, C]
```

Hàm `showEmptyFailure` chỉ giúp tập trung phần kiểm tra ngoại lệ trong ví dụ. Nếu phương thức không ném ngoại lệ như mong đợi, nó ném AssertionError để không in ra một kết quả gây hiểu nhầm.

### 7.1. Phương thức trả null không làm thay đổi tập rỗng

Các lời gọi peek và poll trên Deque rỗng đều trả null, size vẫn bằng 0.

Đối với Deque có X, peek trả X và size vẫn bằng 1; pop trả X rồi size giảm về 0. Vì vậy, hai thao tác “nhìn” và “lấy” không thể thay thế nhau chỉ vì trả về cùng giá trị trong một ví dụ.

### 7.2. Offer vẫn có thể ném ngoại lệ

`offerLast(null)` ném NullPointerException. Không nên viết mã chỉ kiểm tra boolean rồi cho rằng đã xử lý mọi đầu vào sai.

Nếu mã nhận dữ liệu từ ngoài, xác định chính sách null trước khi đưa vào Deque. Việc kết thúc vòng lặp bằng poll trả null chỉ rõ ràng khi dữ liệu thành viên không thể null.

### 7.3. Sức chứa ban đầu không phải giới hạn

`new ArrayDeque<>(1)` vẫn chứa được A, B, C trong chương trình. Constructor chuẩn bị sức chứa ban đầu đủ cho nhu cầu được nêu; nó không thiết lập “chỉ được có một phần tử”.

Điều này quan trọng nếu Deque dùng để giữ việc đang chờ. Cần một giới hạn nghiệp vụ thực sự thì phải thiết kế hoặc chọn cấu trúc có hợp đồng giới hạn; không dùng initial capacity như một cơ chế chặn số lượng.

## 8. Vì sao ArrayDeque là lựa chọn thường dùng trong một luồng?

[ArrayDeque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html) mô tả nó là Deque dựa trên mảng có thể tăng sức chứa, không chấp nhận null và không tự an toàn cho truy cập đồng thời không có phối hợp.

Một mô hình hữu ích là mảng vòng: cấu trúc giữ vị trí hai đầu, nên thêm hoặc lấy ở đầu không cần dồn toàn bộ phần tử như cách bỏ phần tử đầu của một mảng tuyến tính ngây thơ.

Khi cần tăng mảng, một thao tác có thể phải cấp phát và chuyển dữ liệu. Vì thế, chi phí thường được phân tích theo **khấu hao** trên một chuỗi thao tác.

Với ArrayDeque, các thao tác ở hai đầu thông dụng phù hợp với chi phí hằng số khấu hao. Nhưng không suy rộng mọi phương thức của Deque đều O(1):

| Việc làm | Cách phân tích phù hợp với ArrayDeque |
|---|---|
| Thêm/lấy ở hai đầu | Hằng số khấu hao đối với các thao tác thông dụng |
| Xem hai đầu | Không phải quét toàn bộ dãy |
| `contains(x)` | Có thể cần tìm qua các phần tử |
| `remove(x)` theo giá trị | Có thể cần tìm và điều chỉnh dãy |
| Duyệt toàn bộ n phần tử | Cần xử lý n phần tử |
| Lưu n phần tử | Cần bộ nhớ cho cấu trúc và tham chiếu dữ liệu |

Nếu bài toán chủ yếu thêm và lấy ở hai đầu, Deque biểu đạt đúng thao tác. Nếu cần truy cập thường xuyên theo chỉ số hoặc sắp xếp theo một tiêu chí, hãy đánh giá cấu trúc khác thay vì cố dùng Deque cho mọi việc.

Không cần một benchmark tự chế bằng vài lời gọi đồng hồ để kết luận ArrayDeque luôn nhanh hơn mọi lớp khác. Chọn trước theo hợp đồng, thao tác và môi trường; đo khi hiệu năng thực tế cần được đánh giá.

### 8.1. So với Stack cũ

[Tài liệu Stack Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Stack.html) khuyến nghị dùng Deque và các triển khai của nó thay cho lớp Stack cũ để biểu đạt thao tác LIFO.

Một khai báo rõ ý định là:

```java
Deque<Integer> stack = new ArrayDeque<>();
```

Nếu chuyển mã cũ, phải kiểm tra cả chính sách rỗng. Deque.pop ném NoSuchElementException khi rỗng, và Deque.peek trả null. Không mặc định mọi chi tiết ngoại lệ hoặc hành vi của Stack cũ được giữ nguyên chỉ vì các phương thức cùng tên.

### 8.2. Hỗ trợ nhiều luồng là một quyết định riêng

Một ArrayDeque dùng đúng FIFO vẫn có thể được chia sẻ sai giữa nhiều luồng. Quy tắc lấy đầu nào không cung cấp tính nguyên tử cho các chuỗi thao tác.

Nếu cần nhiều bên tạo hoặc xử lý việc, cần chọn cơ chế phối hợp hoặc các collection đồng thời/chặn phù hợp. Không đổi sang một lớp khác chỉ vì tên có Queue rồi coi mọi yêu cầu về thứ tự, dung lượng và chờ đều đã được giải quyết.

Các ví dụ trong bài là mã một luồng; kiểm tra rỗng rồi pop là hợp lý trong giả định đó. Không mang nguyên giả định sang một ngữ cảnh có cập nhật đồng thời.

## 9. FIFO và LIFO thay đổi cách duyệt một cây

Xét cây nhị phân:

```text
          A
        /          B     C
      /           D   E     F
```

Giả sử khi phát hiện các nút con, ta muốn ưu tiên trái trước phải.

### 9.1. Hàng đợi FIFO: theo từng tầng

Bắt đầu với A. Sau khi xử lý A, thêm B rồi C ở cuối:

```text
lấy A -> hàng đợi [B, C]
lấy B -> hàng đợi [C, D, E]
lấy C -> hàng đợi [D, E, F]
```

Các nút đang chờ sớm hơn được lấy trước. Kết quả là:

```text
A, B, C, D, E, F
```

Đây là duyệt theo chiều rộng, thường gọi là breadth-first traversal.

### 9.2. Ngăn xếp LIFO: đi sâu theo nhánh

Sau khi xử lý A, muốn B được lấy tiếp thì phải push C trước, rồi push B:

```text
push C rồi B -> ngăn xếp [B, C]
pop B
push E rồi D -> ngăn xếp [D, E, C]
```

D được lấy trước E, và cả nhánh B được xử lý trước C. Kết quả:

```text
A, B, D, E, C, F
```

Đây là một kiểu duyệt sâu theo thứ tự trước, thường gọi là depth-first preorder traversal.

Điểm cốt lõi là: **với LIFO, thứ tự đẩy nhiều phần tử cần đảo lại nếu muốn thứ tự xử lý giống danh sách ưu tiên ban đầu**.

Cây chỉ là một ví dụ về cách chọn việc tiếp theo. Cùng ý tưởng xuất hiện khi duyệt thư mục, xử lý cấu trúc lồng nhau hoặc quản lý các bước tính toán đang chờ.

## 10. Chương trình 3: một cây, hai chính sách xử lý

Lưu tệp `DequeTreeTraversalDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class DequeTreeTraversalDemo {
    record Node(String name, Node left, Node right) {
    }

    static List<String> breadthFirst(Node root) {
        if (root == null) {
            return List.of();
        }

        Deque<Node> pending = new ArrayDeque<>();
        List<String> result = new ArrayList<>();
        pending.offerLast(root);

        while (!pending.isEmpty()) {
            Node current = pending.removeFirst();
            result.add(current.name());

            if (current.left() != null) {
                pending.offerLast(current.left());
            }
            if (current.right() != null) {
                pending.offerLast(current.right());
            }
        }
        return result;
    }

    static List<String> depthFirst(Node root) {
        if (root == null) {
            return List.of();
        }

        Deque<Node> pending = new ArrayDeque<>();
        List<String> result = new ArrayList<>();
        pending.push(root);

        while (!pending.isEmpty()) {
            Node current = pending.pop();
            result.add(current.name());

            if (current.right() != null) {
                pending.push(current.right());
            }
            if (current.left() != null) {
                pending.push(current.left());
            }
        }
        return result;
    }

    public static void main(String[] args) {
        Node root = new Node("A",
                new Node("B",
                        new Node("D", null, null),
                        new Node("E", null, null)),
                new Node("C",
                        null,
                        new Node("F", null, null)));

        System.out.println("breadth first FIFO = " + breadthFirst(root));
        System.out.println("depth first LIFO = " + depthFirst(root));
        System.out.println("empty tree FIFO = " + breadthFirst(null));
        System.out.println("empty tree LIFO = " + depthFirst(null));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 DequeTreeTraversalDemo.java
java DequeTreeTraversalDemo
```

Kết quả của lần chạy kiểm chứng:

```text
breadth first FIFO = [A, B, C, D, E, F]
depth first LIFO = [A, B, D, E, C, F]
empty tree FIFO = []
empty tree LIFO = []
```

### 10.1. Null của nút con không được đưa vào Deque

Các nút lá có liên kết con null. Mã kiểm tra liên kết trước khi thêm, nên ArrayDeque chỉ chứa các Node thật.

Root null được xử lý ngay bằng danh sách rỗng. Không cần nhét null vào hàng đợi như một nút đặc biệt.

Đây là sự khác nhau giữa một trường dữ liệu có thể null và chính phần tử được thêm vào Deque. Dữ liệu đầu vào của thuật toán có thể có liên kết trống, nhưng collection đang dùng vẫn giữ hợp đồng không nhận null.

### 10.2. Hàng đợi giữ thứ tự phát hiện

Trong breadthFirst, con trái rồi con phải được thêm ở cuối. Những nút đã chờ từ trước vẫn ở phía đầu, nên một nút ở tầng tiếp theo không chen lên trước các nút của tầng hiện tại.

Mỗi Node được xử lý một lần trong cây của ví dụ. Kết quả trả về tách riêng khỏi Deque đang dùng làm vùng chờ.

### 10.3. Ngăn xếp đảo thứ tự thêm các nút con

Trong depthFirst, mã push con phải trước, con trái sau. Con trái vì thế nằm ở đỉnh và được xử lý trước.

Nếu đổi hai khối if, chương trình vẫn dùng LIFO nhưng thứ tự ưu tiên nhánh sẽ đổi. “Dùng stack” không tự xác định muốn duyệt trái hay phải trước; thứ tự push còn là một quyết định của thuật toán.

### 10.4. Đây là cây, không phải bộ duyệt đồ thị tổng quát

Dữ liệu ví dụ là một cây hữu hạn, không có chu trình và không có nút con dùng chung qua nhiều đường. Vì thế không cần một tập đánh dấu đã thăm.

Với một đồ thị có chu trình hoặc nhiều đường dẫn tới cùng đỉnh, chỉ đổi Deque giữa FIFO và LIFO chưa đủ để có thuật toán đúng: cần chính sách đánh dấu và tránh lặp phù hợp.

Không áp dụng nguyên mẫu mã cây cho dữ liệu có chu trình rồi kết luận Deque bị lỗi khi chương trình không dừng.

### 10.5. Chi phí của hai mẫu

Với n nút, mỗi nút trong cây ví dụ được thêm, lấy và ghi nhận một lần, nên thời gian là O(n) khi thao tác đầu/cuối có chi phí khấu hao phù hợp.

Phần đang chờ có đặc điểm khác nhau:

- Duyệt rộng có thể phải giữ nhiều nút ở một tầng; bộ nhớ chờ phụ thuộc độ rộng của cây.
- Duyệt sâu ở cây nhị phân giữ đường đi và các nhánh chờ; có cận theo chiều cao cây cho kiểu triển khai này.
- Danh sách result còn giữ n tên, nên bản thân kết quả cần O(n) bộ nhớ.

Không bỏ qua bộ nhớ kết quả khi phân tích tổng chương trình. Deque chỉ là phần điều phối việc đang chờ.

## 11. Chọn FIFO hoặc LIFO theo yêu cầu, không theo thói quen

Một số quan hệ thường gặp:

| Yêu cầu | Chính sách phù hợp để cân nhắc | Lý do |
|---|---|---|
| Xử lý công việc theo thứ tự chờ | FIFO | Việc đã chờ trước được lấy trước |
| Duyệt theo từng tầng | FIFO | Các nút được phát hiện sớm hơn tiếp tục đứng trước |
| Hoàn tác thao tác gần nhất | LIFO | Thao tác sau cần được đảo trước |
| Duyệt sâu bằng cấu trúc tường minh | LIFO | Tiếp tục nhánh vừa được chọn |
| Khôi phục cấu trúc lồng nhau | LIFO | Mức lồng gần nhất thường được đóng trước |

Deque chỉ giữ thứ tự phần tử. Ví dụ, dùng nó cho hoàn tác không tự làm một hành động trở nên có thể đảo ngược. Phần tử cần chứa đủ thông tin để thực hiện hành động ngược theo nghiệp vụ.

FIFO cũng không tự cung cấp độ bền dữ liệu, xác nhận hoàn tất hoặc giao việc qua tiến trình. Một ArrayDeque trong bộ nhớ không trở thành một hệ thống hàng đợi bên ngoài chỉ vì tên biến là jobs.

Trong một thuật toán, đổi FIFO sang LIFO có thể đổi cả ý nghĩa kết quả. Hãy xác định vì sao phần tử đang chờ nào cần được xử lý tiếp, rồi chọn cặp thao tác tương ứng.

### 11.1. Không trộn vai trò trên cùng một dãy mà thiếu quy tắc

Nếu một phần mã offerLast để giữ FIFO còn phần khác push ở đầu, công việc được push có thể chen trước những công việc đã chờ. Điều đó có thể là yêu cầu ưu tiên hợp lệ, nhưng khi ấy không còn là FIFO thuần theo thời điểm thêm.

Nếu một phần mã dùng đầu first làm đỉnh mà phần khác lấy last, trạng thái ngăn xếp có thể được tiêu thụ ngược mong đợi.

Nên giữ Deque trong một phạm vi sở hữu rõ, đặt tên theo vai trò và cung cấp các thao tác nghiệp vụ nhất quán. Dùng kiểu Queue cho một phần chỉ cần FIFO cũng giúp giảm khả năng gọi nhầm qua tham chiếu ấy.

### 11.2. Không dùng việc in Deque thay cho việc định nghĩa thứ tự

ArrayDeque được duyệt từ first tới last. Với stack dùng first làm đỉnh, iterator đi từ đỉnh xuống đáy.

Nhưng một vòng for-each chỉ đọc, không pop. In ra đúng thứ tự mong đợi chưa chứng minh vòng xử lý thực sự lấy ở đúng đầu hoặc gỡ phần tử sau xử lý.

Hãy kiểm tra chính dãy lấy ra bằng poll/pop, đặc biệt khi thuật toán thêm tiếp phần tử trong lúc đang xử lý.

## 12. Các lỗi thường gặp khi dùng Deque

| Cách làm | Hậu quả hoặc điểm nhầm | Cách sửa |
|---|---|---|
| Muốn FIFO nhưng addLast và pollLast | Lấy mới nhất trước, thành LIFO | Lấy ở đầu đối diện |
| Muốn LIFO nhưng push rồi removeLast | Lấy phần tử cũ ở đáy | Dùng pop hoặc pollFirst theo chính sách rỗng |
| Dùng peek trong vòng lặp nhưng không gỡ | Có thể xử lý mãi cùng một phần tử | Dùng poll/pop khi cần tiêu thụ |
| Dùng pop khi rỗng là bình thường | Phải xử lý ngoại lệ không cần thiết | Dùng pollFirst hoặc điều kiện phù hợp |
| Dùng poll khi rỗng là lỗi mà không kiểm tra | Có thể bỏ qua lỗi giả định | Xử lý null hoặc dùng phương thức ném ngoại lệ |
| Offer null rồi chờ false | ArrayDeque ném NullPointerException | Xác định dữ liệu không null |
| Coi initial capacity là giới hạn | Deque vẫn tăng và giữ thêm dữ liệu | Chọn cơ chế giới hạn thật |
| Cho rằng Queue nào cũng FIFO | Triển khai ưu tiên có chính sách khác | Đọc hợp đồng lớp đang dùng |
| Thêm trái rồi phải bằng push mà muốn xử lý trái trước | Phải được lấy trước vì nằm trên đỉnh | Push theo thứ tự ngược lại |
| Gọi remove(x) như lấy đầu | Đây là tìm và gỡ theo giá trị | Phân biệt remove() với remove(Object) |
| Pop trong vòng for-each trên cùng Deque | Có thể làm iterator phát hiện sửa đổi | Dùng vòng lấy phần tử trực tiếp |
| Chia sẻ ArrayDeque giữa nhiều luồng không phối hợp | Đúng cặp đầu vẫn chưa bảo đảm nhất quán | Chọn cơ chế đồng thời phù hợp |

Một bẫy đơn giản nhưng đáng kiểm tra là vòng lặp:

```java
// Trích đoạn lỗi nếu không có bước nào gỡ hoặc thay đổi head.
while (jobs.peekFirst() != null) {
    process(jobs.peekFirst());
}
```

Nếu process không gỡ hoặc làm thay đổi dãy, cùng một công việc ở đầu sẽ được xử lý mãi. Việc “đọc được phần tử” và “đã tiêu thụ phần tử” là hai trạng thái khác nhau.

Mẫu tiêu thụ FIFO với ArrayDeque là lấy bằng pollFirst, kiểm tra kết quả rồi xử lý chính giá trị đã lấy. Không peek lại và hy vọng đó luôn là cùng phần tử trong mọi ngữ cảnh chia sẻ dữ liệu.

## 13. Kiểm tra thứ tự bằng những tình huống bộc lộ được sai đầu

Một kiểm tra chỉ có một phần tử không phân biệt được FIFO và LIFO. Với A duy nhất, cả hai đều lấy A.

Nên có ít nhất các tình huống sau:

### 13.1. Ba phần tử khác nhau

```text
thêm A, B, C

FIFO phải lấy: A, B, C
LIFO phải lấy: C, B, A
```

Đây là bài kiểm tra cơ bản nhất cho việc chọn đầu thêm/lấy.

### 13.2. Thêm xen kẽ với lấy

FIFO:

```text
thêm A
thêm B
lấy -> A
thêm C
lấy tiếp -> B, C
```

LIFO:

```text
push A
push B
pop -> B
push C
pop tiếp -> C, A
```

Trường hợp xen kẽ giúp phát hiện mã chỉ xử lý đúng khi thêm hết rồi mới lấy hết.

### 13.3. Rỗng, một phần tử và phần tử trùng

Kiểm tra rõ:

- Khi rỗng, dùng phương thức trả null hay phương thức ném ngoại lệ?
- Peek có giữ nguyên size không?
- Poll/pop có gỡ đúng một phần tử không?
- Hai lần thêm cùng một giá trị có được giữ như hai lần xuất hiện không?
- Sau khi lấy hết rồi thêm lại, thứ tự vẫn đúng không?
- Null bị xử lý ở đâu trong hợp đồng đầu vào?

Không dùng Set để kiểm tra đầu ra nếu cần bảo vệ thứ tự hoặc số lần xuất hiện; Set có thể che cả hai loại lỗi ấy.

### 13.4. So sánh dữ liệu quan sát được

ArrayDeque không cung cấp phép equals theo dãy giống List. Hai ArrayDeque có cùng phần tử không vì thế mặc định equals bằng true.

Khi cần đối chiếu thứ tự, có thể thu các phần tử lấy ra vào List như chương trình 1, hoặc tạo một List từ iterator để kiểm tra dãy đọc. Hai cách kiểm tra hai ý khác nhau: một cách bảo vệ quá trình tiêu thụ, cách kia chỉ bảo vệ thứ tự duyệt hiện tại.

Cũng không dùng một giá trị thời gian chạy của ví dụ nhỏ làm tiêu chí đúng sai cho FIFO/LIFO. Đây là hợp đồng thứ tự, có thể kiểm tra trực tiếp bằng dữ liệu rõ ràng.

## 14. Bài tập áp dụng

### Bài 1: xác định chính sách

Lần lượt gọi `addLast(A)`, `addLast(B)`, `addLast(C)`, rồi liên tục `pollLast()`.

Đầu ra là gì, có phải FIFO không?

**Gợi ý trả lời:** C, B, A; đây là LIFO vì thêm và lấy ở cùng đầu.

### Bài 2: đổi cách xử lý rỗng mà giữ LIFO

Một stack dùng push. Muốn thao tác lấy trả null khi rỗng thay vì ném ngoại lệ.

Nên dùng phương thức nào?

**Gợi ý trả lời:** pollFirst. Đỉnh của push ở first, nên đổi chính sách lỗi không được đồng thời đổi đầu lấy.

### Bài 3: chỉ xem việc tiếp theo

Một hàng đợi dùng offerLast/pollFirst. Cần xem việc sẽ xử lý tiếp nhưng chưa gỡ.

**Gợi ý trả lời:** peekFirst hoặc peek. Nếu chắc chắn không được rỗng và muốn lỗi rõ khi vi phạm giả định, có thể dùng getFirst hoặc element.

### Bài 4: ba nút con theo ưu tiên

Muốn một stack xử lý các nút con theo thứ tự X, Y, Z. Phải push chúng theo thứ tự nào?

**Gợi ý trả lời:** Z, Y, X, để X được push cuối và nằm trên đỉnh.

### Bài 5: null và offer

Vì offer trả boolean, có thể đưa null vào ArrayDeque rồi dùng false để báo dữ liệu không hợp lệ không?

**Gợi ý trả lời:** không. ArrayDeque không nhận null và offer null ném NullPointerException. Boolean không thay thế hợp đồng đầu vào.

### Bài 6: giới hạn hàng đợi

Khởi tạo `new ArrayDeque<>(100)` có bảo đảm từ chối công việc thứ 101 không?

**Gợi ý trả lời:** không. Đó là sức chứa ban đầu được chuẩn bị, không phải giới hạn cố định. Cần cấu trúc hoặc quy trình giới hạn phù hợp.

### Bài 7: duyệt rộng hay duyệt sâu?

Trong cây ví dụ, sau A muốn xử lý B rồi C trước khi tới D. Nên giữ vùng chờ theo FIFO hay LIFO?

**Gợi ý trả lời:** FIFO, vì các nút cùng tầng đã vào chờ trước phải được lấy trước các nút tầng tiếp theo.

### Bài 8: một kết quả kiểm tra gây hiểu nhầm

Chương trình lấy đủ A, B, C nhưng kết quả là C, B, A trong khi yêu cầu FIFO. Kiểm tra bằng Set vẫn qua.

Sai ở đâu?

**Gợi ý trả lời:** cách kiểm tra đã bỏ mất thứ tự. Cần đối chiếu dãy đầu ra, không chỉ tập thành viên.

Khi dùng Deque trong một thuật toán, hãy ghi rõ đầu thêm, đầu lấy và hành vi lúc rỗng. Ba quyết định ấy nối trực tiếp yêu cầu FIFO/LIFO với từng lời gọi API và giúp giữ quy tắc xử lý ổn định khi mã phát triển.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="queue-deque-006">ArrayDeque: mảng vòng, tăng dung lượng và thao tác hai đầu</a>

<details>
<summary>Xem chi tiết</summary>

# ArrayDeque: mảng vòng, tăng dung lượng và thao tác hai đầu

## 1. Vì sao một Deque dựa trên mảng vẫn thao tác đầu dãy hiệu quả?

Nếu dùng một mảng tuyến tính ngây thơ để biểu diễn hàng đợi, việc lấy phần tử đầu có thể kéo theo dồn mọi phần tử còn lại sang trái. Làm như vậy nhiều lần khiến công việc tăng theo chiều dài dãy.

ArrayDeque tránh cách tổ chức ấy. Nó giữ dữ liệu trong một mảng có thể tăng sức chứa và quản lý hai vị trí đầu–cuối theo dạng vòng. Khi lấy đầu, chủ yếu là xóa tham chiếu ở một ô rồi chuyển vị trí đầu; không cần dồn cả dãy sau mỗi lần lấy.

Ba ý chính của cấu trúc là:

- **Mảng vòng:** thứ tự logic có thể đi qua cuối mảng rồi tiếp tục ở đầu mảng.
- **Hai đầu:** thêm, xem và gỡ ở first hoặc last bằng những thao tác vị trí phù hợp.
- **Tăng dung lượng:** khi mảng hiện có không đủ chỗ, tạo mảng lớn hơn và giữ đúng thứ tự các phần tử.

[Tài liệu ArrayDeque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html) mô tả đây là triển khai Deque dựa trên mảng có thể tăng, không có giới hạn dung lượng cố định do người gọi đặt, cấm phần tử null và không tự an toàn khi nhiều luồng truy cập không có phối hợp.

Bài này phân biệt hai tầng:

1. **Hợp đồng sử dụng:** phần tử nào được thêm/lấy, thứ tự nào được quan sát, điều gì xảy ra khi rỗng.
2. **Mô hình triển khai:** mảng nền, vị trí hai đầu, quay vòng và cấp phát khi tăng dung lượng.

Các sơ đồ chỉ số là mô hình để hiểu cơ chế. Không dùng chúng để suy ra một chỉ số nội bộ cụ thể sau một lời gọi API trên mọi JDK.

## 2. Dãy logic khác với thứ tự ô vật lý

Giả sử Deque đang biểu đạt dãy:

```text
first -> A, B, C, D, E <- last
```

Không bắt buộc A phải ở ô 0 và E phải ở ô 4. Một cách bố trí trong mảng vòng 8 ô có thể là:

```text
chỉ số:   0   1   2   3   4   5   6   7
nội dung: C   D   E   .   .   .   A   B
                                  ^
                                 head

tail = 3, vị trí sẽ ghi khi thêm cuối
dấu . là ô trống trong mô hình
```

Đọc từ head theo chiều tiến của mảng:

```text
6 -> 7 -> 0 -> 1 -> 2
A    B    C    D    E
```

Thứ tự logic vẫn là A, B, C, D, E dù có hai đoạn nằm ở hai phía mảng.

Đây là ý nghĩa “vòng”: chỉ số được đưa về đầu khi đi qua ô cuối. Không có nghĩa các ô mảng ở bộ nhớ được nối vật lý thành một vòng.

### 2.1. head và tail có vai trò khác nhau

Trong mô hình phù hợp với triển khai OpenJDK 17:

- `head` là vị trí phần tử first khi dãy không rỗng.
- `tail` là vị trí sẽ dùng cho lần thêm last tiếp theo.
- Phần tử last hiện tại nằm ở vị trí lùi một bước từ tail.
- Khi trạng thái ổn định là rỗng, head bằng tail.

Đặc biệt, tail không phải luôn là chỉ số của phần tử cuối đang có. Nếu tail bằng 0, vị trí ngay trước nó theo vòng là ô cuối của mảng.

Không cần dùng reflection để lấy các trường ấy trong mã ứng dụng. API cung cấp `peekFirst`, `peekLast`, `getFirst`, `getLast` để đọc các đầu theo hợp đồng.

### 2.2. Phân biệt size và độ dài mảng nền

Gọi:

```text
n = số phần tử hiện có
L = số ô của mảng nền
```

n và L không phải một đại lượng. Mảng có thể còn chỗ dự phòng; một số thiết kế còn dành ô trống để phân biệt trạng thái.

ArrayDeque không có một phương thức public thông thường để hỏi chính xác độ dài mảng nội bộ. `size()` trả số phần tử, không trả số ô đang được cấp phát.

Constructor nhận một con số chuẩn bị sức chứa ban đầu, không hứa mảng vật lý có đúng từng ấy ô và không đặt giới hạn tối đa từng ấy phần tử.

## 3. Tăng hoặc giảm chỉ số theo vòng

Một cách minh họa việc tiến và lùi chỉ số là:

```java
// Các hàm minh họa đặt trong một lớp bao quanh.
// Điều kiện: length > 0 và 0 <= index < length.
static int nextIndex(int index, int length) {
    return index + 1 == length ? 0 : index + 1;
}

static int previousIndex(int index, int length) {
    return index == 0 ? length - 1 : index - 1;
}
```

Đây là mã mô hình, không phải mã nguồn được sao chép nguyên từ ArrayDeque. Nó biểu đạt rõ thao tác tại biên mảng:

```text
nextIndex(7, 8)     -> 0
previousIndex(0, 8) -> 7
```

Không được áp dụng tùy ý công thức mặt nạ bit như `(index + 1) & (length - 1)` nếu chưa có điều kiện length là lũy thừa của hai. Mô hình mảng vòng không tự yêu cầu độ dài mảng phải có dạng ấy.

Khi đọc các phiên bản JDK khác nhau, chi tiết chọn độ dài và công thức cập nhật chỉ số có thể khác. Hợp đồng ArrayDeque không hứa mọi phiên bản giữ cùng một độ dài mảng hay cùng một hệ số tăng.

### 3.1. Tính số phần tử trong mô hình có một khoảng trống

Với quy ước head là đầu có dữ liệu và tail là ô ghi tiếp, số phần tử ở trạng thái ổn định có thể được tính theo khoảng cách vòng:

```text
n = tail - head                  nếu tail >= head
n = tail - head + L              nếu tail < head
```

Trong hình ban đầu:

```text
head = 6, tail = 3, L = 8
n = 3 - 6 + 8 = 5
```

Công thức này đi cùng quy ước trạng thái của mô hình. Trong một cấu trúc vòng khác cho phép dùng kín mọi ô và giữ một biến đếm riêng, head bằng tail có thể cần được giải thích khác.

Không lấy một công thức của mảng vòng rồi gán cho mọi cách triển khai.

## 4. Bốn thao tác cốt lõi ở hai đầu

Bắt đầu lại từ bố trí minh họa:

```text
chỉ số:   0   1   2   3   4   5   6   7
nội dung: C   D   E   .   .   .   A   B
head = 6
tail = 3
logic = [A, B, C, D, E]
```

Giả sử các bước dưới đây chưa cần tăng mảng.

### 4.1. Thêm cuối

Để thêm F ở last:

1. Ghi F vào ô tail hiện tại, tức ô 3.
2. Đưa tail tiến một bước theo vòng.

```text
head = 6
tail = 4
logic = [A, B, C, D, E, F]
```

Không cần chuyển A, B, C, D, E sang vị trí mới.

### 4.2. Lấy đầu

Để lấy first:

1. Đọc A ở head.
2. Xóa tham chiếu trong ô ấy.
3. Đưa head tiến một bước theo vòng.

```text
trả về A
head = 7
tail = 4
logic = [B, C, D, E, F]
```

Việc “xóa” ở đây không bắt buộc hủy đối tượng A trong bộ nhớ. Nó bỏ tham chiếu từ ô của Deque; đối tượng còn có thể được giữ bởi tham chiếu khác.

### 4.3. Thêm đầu

Từ trạng thái vừa có, thêm X ở first:

1. Lùi head một bước theo vòng.
2. Ghi X vào head mới.

```text
head = 6
tail = 4
logic = [X, B, C, D, E, F]
```

Thứ tự B, C, D, E, F vẫn nguyên vẹn.

### 4.4. Lấy cuối

Để lấy last:

1. Lùi từ tail tới vị trí phần tử cuối.
2. Đọc F tại đó.
3. Xóa tham chiếu và cập nhật tail về vị trí vừa gỡ.

```text
trả về F
head = 6
tail = 3
logic = [X, B, C, D, E]
```

Bảng đối chiếu cả chuỗi:

| Bước | head | tail | Dãy logic sau bước |
|---|---:|---:|---|
| Ban đầu | 6 | 3 | A, B, C, D, E |
| Thêm last F | 6 | 4 | A, B, C, D, E, F |
| Lấy first | 7 | 4 | B, C, D, E, F |
| Thêm first X | 6 | 4 | X, B, C, D, E, F |
| Lấy last | 6 | 3 | X, B, C, D, E |

Số thao tác vị trí của mỗi bước là nhỏ và cố định khi không cần tăng mảng. Đó là lý do ArrayDeque xử lý hai đầu khác với một mảng tuyến tính luôn dồn nội dung về ô 0.

## 5. Trạng thái rỗng, ô trống và chính sách null

Trong [mã nguồn ArrayDeque của OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/ArrayDeque.java), các ô không giữ phần tử được đặt null; ở trạng thái ổn định có ô trống tại tail. Khi thêm làm mảng tạm kín, cơ chế tăng mảng khôi phục khoảng trống cần thiết.

Các chi tiết đó giải thích mô hình đang dùng, không phải một API để ứng dụng điều khiển trực tiếp.

### 5.1. head bằng tail ở trạng thái rỗng

Sau khi lấy hết, head và tail gặp nhau. Chúng không bắt buộc phải ở ô 0 sau mọi chuỗi poll; vị trí cụ thể là chi tiết nội bộ.

Một thao tác clear có thể đặt lại vị trí theo lựa chọn triển khai. Người gọi chỉ nên dựa vào size bằng 0, isEmpty đúng và hành vi rỗng của các phương thức.

### 5.2. Null không phải phần tử hợp lệ

ArrayDeque cấm thêm null. Nhờ đó, null có thể được dùng rõ ràng như giá trị báo không có phần tử của poll và peek.

Cấm null cũng phù hợp với cách dùng null đánh dấu ô trống trong triển khai này. Tuy nhiên, không suy luận rằng mọi mảng vòng về mặt lý thuyết đều không thể hỗ trợ null: một thiết kế khác có thể dùng thông tin đếm hoặc đánh dấu khác.

Nếu dữ liệu nghiệp vụ có trường có thể thiếu, hãy biểu đạt trong một đối tượng không null, hoặc chọn chính sách đầu vào rõ. Không dùng một phần tử null như công việc đặc biệt trong ArrayDeque.

### 5.3. Rỗng: phương thức trả null và phương thức ném ngoại lệ

| Ý định | Trả null khi rỗng | Ném NoSuchElementException khi rỗng |
|---|---|---|
| Chỉ xem first | `peekFirst()` | `getFirst()` |
| Chỉ xem last | `peekLast()` | `getLast()` |
| Lấy và gỡ first | `pollFirst()` | `removeFirst()` |
| Lấy và gỡ last | `pollLast()` | `removeLast()` |

Thêm null vẫn ném NullPointerException kể cả gọi offer. “Trả một giá trị trạng thái” không có nghĩa mọi đầu vào không hợp lệ đều được chuyển thành false hoặc null.

## 6. Gắn API với hàng đợi và ngăn xếp

Từ bốn thao tác cơ chế, có thể xây hai chính sách quen thuộc:

```text
FIFO: thêm last, lấy first
LIFO: thêm first, lấy first
```

Các tên phương thức rút gọn của Deque dùng cùng quy ước:

| Tên quen dùng | Thao tác hai đầu tương ứng |
|---|---|
| `offer(e)` | `offerLast(e)` |
| `poll()` | `pollFirst()` |
| `peek()` | `peekFirst()` |
| `push(e)` | `addFirst(e)` |
| `pop()` | `removeFirst()` |

Với FIFO, một phần tử được thêm ở last chờ các phần tử phía first được lấy trước. Với LIFO, phần tử mới được thêm first đứng ngay trước phần tử cũ và được lấy trước.

Cũng có thể làm ngăn xếp ở đầu last nếu thêm và lấy đều ở last, nhưng các tên push/pop của API đặt đỉnh ở first. Hãy giữ một quy ước nhất quán.

Cơ chế vòng không tự quyết định FIFO hay LIFO. Nó tạo khả năng thao tác hiệu quả ở hai đầu; mã gọi chọn chính sách bằng cặp thao tác.

## 7. Chương trình 1: quan sát từng đầu thao tác

Lưu tệp `ArrayDequeEndsDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.Deque;

public class ArrayDequeEndsDemo {
    public static void main(String[] args) {
        Deque<String> values = new ArrayDeque<>();
        values.addLast("B");
        values.addLast("C");
        values.addFirst("A");

        System.out.println("initial = " + values);
        System.out.println("peek first = " + values.peekFirst());
        System.out.println("peek last = " + values.peekLast());
        System.out.println("offer first X = " + values.offerFirst("X"));
        System.out.println("offer last D = " + values.offerLast("D"));
        System.out.println("after offers = " + values);
        System.out.println("poll first = " + values.pollFirst());
        System.out.println("poll last = " + values.pollLast());
        System.out.println("after polls = " + values);

        values.push("TOP");
        System.out.println("after push = " + values);
        System.out.println("pop = " + values.pop());
        System.out.println("remove first = " + values.removeFirst());
        System.out.println("remove last = " + values.removeLast());
        System.out.println("one member = " + values);
        System.out.println("get first = " + values.getFirst());
        System.out.println("get last = " + values.getLast());
        System.out.println("take final member = " + values.pollFirst());
        System.out.println("empty = " + values.isEmpty());
        System.out.println("empty poll = " + values.pollFirst());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 ArrayDequeEndsDemo.java
java ArrayDequeEndsDemo
```

Kết quả của lần chạy kiểm chứng:

```text
initial = [A, B, C]
peek first = A
peek last = C
offer first X = true
offer last D = true
after offers = [X, A, B, C, D]
poll first = X
poll last = D
after polls = [A, B, C]
after push = [TOP, A, B, C]
pop = TOP
remove first = A
remove last = C
one member = [B]
get first = B
get last = B
take final member = B
empty = true
empty poll = null
```

Các chương trình trong bài dùng ngôn ngữ và API có ở Java 17 trở lên, được biên dịch với `--release 17` và chạy kiểm chứng bằng Azul OpenJDK 24.0.2.

Output thể hiện dãy logic từ first tới last, không phải một bản in mảng vật lý. Có thể đối chiếu:

- AddFirst đặt A trước B, C.
- OfferFirst và offerLast thêm ở hai phía khác nhau.
- PollFirst và pollLast gỡ đúng hai phần tử vừa thêm ở hai đầu.
- Push đặt TOP ở first; pop lấy chính TOP.
- Khi chỉ còn B, cả hai đầu đều đọc ra B.
- Sau khi lấy B, poll tiếp trả null.

Các lời gọi peek và get chỉ xem phần tử. Chúng không dịch head hoặc tail để gỡ dữ liệu.

Không thể từ dãy in ra suy ra head đang bằng bao nhiêu. Hợp đồng cần kiểm tra là thứ tự và kết quả, còn vị trí ô thật thuộc triển khai.

## 8. Khi mảng vòng không còn đủ chỗ thì điều gì xảy ra?

Quay vòng chỉ tái sử dụng các ô đã trống. Nếu số phần tử tiếp tục tăng và không còn chỗ theo quy ước lưu trữ, quay về ô 0 không tạo thêm sức chứa.

ArrayDeque tăng mảng để tiếp tục giữ các phần tử hiện có và phần tử mới. Ý tưởng chung:

```text
mảng hiện tại không đủ chỗ
    -> tính kích thước mới phù hợp
    -> cấp phát mảng lớn hơn
    -> chuyển các tham chiếu cần giữ
    -> điều chỉnh vị trí hai đầu
    -> tiếp tục biểu đạt cùng dãy logic
```

### 8.1. Phải bảo toàn thứ tự, kể cả khi dữ liệu đang quấn vòng

Giả sử nội dung cũ được chia thành hai đoạn vật lý:

```text
đoạn cuối mảng: A, B
đoạn đầu mảng:  C, D, E
thứ tự logic:  A, B, C, D, E
```

Sau tăng mảng, thứ tự logic vẫn phải là A, B, C, D, E. Sao chép các ô theo chỉ số vật lý một cách ngây thơ mà bỏ qua head có thể cho C, D, E, A, B và làm sai Deque.

Một cách vẽ để hình dung mảng mới là:

```text
[A, B, C, D, E, ., ., ., ...]
```

Đây là một cách bố trí minh họa hợp lệ, không phải lời hứa rằng ArrayDeque luôn dồn head về 0 sau tăng mảng. Triển khai có thể di chuyển các đoạn theo cách khác miễn bảo toàn thứ tự và các bất biến.

### 8.2. Tăng mảng chuyển tham chiếu, không sao chép sâu đối tượng

Mảng nền giữ các tham chiếu tới phần tử. Tăng mảng không mặc định clone từng đối tượng nghiệp vụ.

Nếu một đối tượng đang được giữ ở ngoài Deque, tăng sức chứa không tự tách nó thành một bản khác. Điều được tổ chức lại là các ô chứa tham chiếu.

Tương tự, một Deque<Integer> còn có các đối tượng số nguyên do cơ chế đóng hộp; không nên coi toàn bộ dữ liệu đối tượng nằm liên tiếp trong mảng chỉ vì các tham chiếu được lưu trong một mảng.

### 8.3. Không dựa vào một hệ số tăng cố định trong mã ứng dụng

Hợp đồng ArrayDeque không cho phép người gọi cấu hình hệ số tăng và không hứa một chuỗi dung lượng nội bộ cố định.

Mã nguồn OpenJDK 17 có cách tính tăng phụ thuộc kích thước hiện tại và lượng cần thêm. Không nên học thành quy tắc “mọi lần đều nhân đôi” hoặc “độ dài luôn là lũy thừa hai”.

Nếu cần phân tích cơ chế, hãy ghi rõ phiên bản triển khai đang đọc. Nếu viết ứng dụng, hãy dựa vào hợp đồng sức chứa tự tăng và chi phí thích hợp, không dựa vào thời điểm resize đoán từ một con số bên trong.

## 9. Vì sao thêm có chi phí O(1) khấu hao nhưng có lần O(n)?

Một lần thêm thông thường có thể chỉ cần ghi một ô và cập nhật một vị trí. Nhưng một lần thêm đúng lúc cần tăng mảng phải xử lý nhiều tham chiếu hiện có.

Nếu đang có n phần tử gần lấp đầy mảng, một lần tăng mảng có thể tốn O(n) thời gian. Vì vậy, không có bảo đảm mọi addFirst hoặc addLast riêng lẻ luôn có chi phí hằng số.

Khái niệm **khấu hao** nhìn tổng công việc trên một chuỗi thao tác, rồi phân bổ chi phí những lần tăng mảng cho các lần thêm đã được sức chứa mới phục vụ.

### 9.1. Ý tưởng tăng theo tỷ lệ

Trong một mô hình tăng theo hệ số g > 1, tổng lượng dữ liệu sao chép qua các lần tăng có dạng một cấp số nhân:

```text
C + gC + g²C + ... + gᵏC
```

Tổng ấy cùng bậc với số hạng lớn nhất. Nếu xây dãy bằng m lần thêm từ nhỏ đến lớn, tổng chi phí chuyển dữ liệu có cận O(m), bên cạnh m lần ghi phần tử.

Do đó, tổng có thể là O(m), dẫn đến O(1) khấu hao cho mỗi lần thêm. Các ngưỡng và làm tròn thực tế không yêu cầu đúng một công thức duy nhất để hiểu ý tưởng này.

Đây là phân tích chuỗi thao tác, không phải xác suất “phần lớn dữ liệu ngẫu nhiên sẽ nhanh”. Nó cũng không có nghĩa một yêu cầu cần độ trễ tối đa chặt đã được đáp ứng.

### 9.2. Đặt kỳ vọng hiệu năng đúng

| Thao tác | Cách hiểu với mô hình ArrayDeque này |
|---|---|
| Thêm first/last | O(1) khấu hao; một lần cần tăng mảng có thể O(n) |
| Lấy first/last | Cập nhật đầu và xóa tham chiếu, không dồn cả dãy |
| Xem first/last | Truy cập vị trí đầu tương ứng |
| size | Tính theo trạng thái chỉ số, không duyệt từng phần tử |
| contains theo giá trị | Có thể phải tìm qua n phần tử |
| remove theo giá trị hoặc occurrence | Có thể phải tìm và dịch một phần dữ liệu |
| Duyệt toàn bộ | O(n) về số phần tử cần đọc |

Bảng không biến mọi phương thức của giao diện Deque thành cùng một chi phí. `removeFirst()` và `remove(Object)` có tên gần nhau nhưng phục vụ hai việc khác nhau.

Độ phức tạp còn giả sử các thao tác trên phần tử như equals có chi phí thích hợp. Nếu phép equals rất tốn kém, tìm theo giá trị có thêm chi phí ấy.

### 9.3. Chuẩn bị sức chứa khi biết quy mô

Nếu dự kiến số phần tử lớn, constructor có sức chứa ban đầu phù hợp có thể giảm nhu cầu tăng mảng trong giai đoạn đầu:

```java
// Trích đoạn: chuẩn bị cho quy mô dự kiến, không đặt giới hạn cứng.
Deque<String> pending = new ArrayDeque<>(1_000);
```

Đổi lại, cấp phát dự phòng lớn có chi phí bộ nhớ. Không chọn một con số khổng lồ chỉ để tránh mọi resize mà chưa hiểu tải thực tế.

Nếu quy mô vượt dự kiến, ArrayDeque vẫn có thể tăng. Nó không tự từ chối phần tử thứ 1001 chỉ vì constructor nhận 1000.

## 10. Chương trình 2: lấy đầu, thêm cuối rồi tiếp tục dùng cả hai đầu

Lưu tệp `ArrayDequeMixedEndsDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class ArrayDequeMixedEndsDemo {
    public static void main(String[] args) {
        Deque<Integer> values = new ArrayDeque<>(8);
        for (int value = 0; value < 6; value++) {
            values.addLast(value);
        }
        System.out.println("initial = " + values);

        List<Integer> removedFront = new ArrayList<>();
        for (int count = 0; count < 4; count++) {
            removedFront.add(values.removeFirst());
        }
        System.out.println("removed front = " + removedFront);
        System.out.println("after front removals = " + values);

        for (int value = 6; value <= 12; value++) {
            values.addLast(value);
        }
        System.out.println("after tail additions = " + values);

        values.addFirst(3);
        values.addFirst(2);
        System.out.println("after front additions = " + values);
        System.out.println("remove last one = " + values.removeLast());
        System.out.println("remove last two = " + values.removeLast());
        System.out.println("forward = " + new ArrayList<>(values));

        List<Integer> reverse = new ArrayList<>();
        Iterator<Integer> iterator = values.descendingIterator();
        while (iterator.hasNext()) {
            reverse.add(iterator.next());
        }
        System.out.println("reverse = " + reverse);

        List<Integer> drained = new ArrayList<>();
        Integer next;
        while ((next = values.pollFirst()) != null) {
            drained.add(next);
        }
        System.out.println("drained = " + drained);
        System.out.println("empty after drain = " + values.isEmpty());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 ArrayDequeMixedEndsDemo.java
java ArrayDequeMixedEndsDemo
```

Kết quả của lần chạy kiểm chứng:

```text
initial = [0, 1, 2, 3, 4, 5]
removed front = [0, 1, 2, 3]
after front removals = [4, 5]
after tail additions = [4, 5, 6, 7, 8, 9, 10, 11, 12]
after front additions = [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
remove last one = 12
remove last two = 11
forward = [2, 3, 4, 5, 6, 7, 8, 9, 10]
reverse = [10, 9, 8, 7, 6, 5, 4, 3, 2]
drained = [2, 3, 4, 5, 6, 7, 8, 9, 10]
empty after drain = true
```

Chương trình tạo một chuỗi thao tác hữu ích để kiểm tra tính liên tục của dãy:

1. Thêm một nhóm ở last.
2. Lấy bớt ở first.
3. Thêm tiếp nhiều giá trị ở last.
4. Thêm ở first.
5. Lấy ở last.
6. Duyệt hai chiều rồi lấy hết theo first.

Những chuỗi như vậy tạo điều kiện cho vị trí hai đầu tiến/lùi và cho việc tái sử dụng vùng trống trong một triển khai mảng vòng. Nhưng chương trình không đọc các chỉ số nội bộ, nên output không phải phép đo “resize xảy ra chính xác ở lần gọi nào”.

### 10.1. Thứ tự duyệt là thứ tự logic

`new ArrayList<>(values)` thu dãy từ first tới last, cho `[2, 3, ..., 10]`.

`descendingIterator()` đọc chiều ngược lại, cho `[10, 9, ..., 2]`. Nó không thay đổi nội dung Deque.

Dù dữ liệu đang nằm trong một đoạn hay quấn thành hai đoạn vật lý, iterator và toArray vẫn phải biểu đạt thứ tự logic phù hợp.

### 10.2. Lấy hết không cần tự biết head/tail

Vòng pollFirst đến khi null lấy ra cùng dãy `[2, 3, ..., 10]` rồi dừng.

Vì ArrayDeque cấm phần tử null, giá trị null ở đây báo hết dữ liệu. Mã gọi không cần biết phần tử tiếp theo nằm ở cuối mảng hay đã quay về ô 0.

Đó là giá trị của việc dùng collection chuẩn: cơ chế chỉ số được đóng gói, còn mã ứng dụng thao tác bằng thứ tự và ý nghĩa của các đầu.

### 10.3. Không kiểm tra bằng hình dạng mảng đoán trước

Một kiểm tra yêu cầu “sau chuỗi này head phải bằng 6” phụ thuộc vào một bố trí nội bộ không thuộc API. Cùng thứ tự đầu ra có thể được tạo bởi các phiên bản triển khai với dung lượng và chỉ số khác nhau.

Điều nên bảo vệ là không mất, không lặp ngoài ý muốn, đúng thứ tự và đúng giá trị trả về sau mỗi thao tác.

## 11. Chương trình 3: tăng qua nhiều mức mà vẫn giữ đủ dữ liệu

Lưu tệp `ArrayDequeGrowthDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.Deque;

public class ArrayDequeGrowthDemo {
    public static void main(String[] args) {
        Deque<Integer> values = new ArrayDeque<>(1);
        int count = 10_000;

        for (int value = 0; value < count; value++) {
            values.addLast(value);
        }

        System.out.println("size after additions = " + values.size());
        System.out.println("first = " + values.getFirst());
        System.out.println("last = " + values.getLast());

        int expected = 0;
        long checksum = 0L;
        Integer next;
        while ((next = values.pollFirst()) != null) {
            if (next != expected) {
                throw new AssertionError("Order differs at " + expected);
            }
            checksum += next;
            expected++;
        }
        if (expected != count) {
            throw new AssertionError("Element count differs");
        }

        System.out.println("verified elements = " + expected);
        System.out.println("checksum = " + checksum);
        System.out.println("empty after drain = " + values.isEmpty());

        values.addFirst(1);
        values.addFirst(2);
        values.addFirst(3);
        System.out.println("reuse at front = " + values);
        values.clear();
        System.out.println("size after clear = " + values.size());
        values.addLast(7);
        System.out.println("reuse after clear = " + values);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 ArrayDequeGrowthDemo.java
java ArrayDequeGrowthDemo
```

Kết quả của lần chạy kiểm chứng:

```text
size after additions = 10000
first = 0
last = 9999
verified elements = 10000
checksum = 49995000
empty after drain = true
reuse at front = [3, 2, 1]
size after clear = 0
reuse after clear = [7]
```

Sức chứa ban đầu được yêu cầu nhỏ, nhưng chương trình thêm được 10.000 giá trị. Nó kiểm tra từng phần tử khi lấy ra, không chỉ nhìn size ở cuối.

### 11.1. Kiểm tra thứ tự và số lượng riêng

Trong vòng lấy:

- Mỗi phần tử phải bằng giá trị expected.
- Expected tăng sau mỗi lần lấy.
- Sau khi dừng, tổng số phần tử phải đúng count.

Nếu chỉ kiểm tra tổng checksum, một số lỗi thiếu/lặp hoặc tráo thứ tự có thể không bị phát hiện. Vì vậy, chương trình còn đối chiếu tuần tự từng giá trị.

Checksum `49.995.000` là tổng từ 0 tới 9999. Biến long giúp mẫu dễ mở rộng mà không dùng một biến tổng int quá nhỏ khi tăng quy mô.

### 11.2. Đây là kiểm tra tính đúng, không phải benchmark

Chương trình không đo thời gian và không đọc sức chứa bên trong. Nó xác nhận rằng qua việc tăng quy mô sử dụng, dãy được giữ đúng và có thể dùng lại sau khi đã lấy hết.

Không thể từ một lần chạy đúng suy ra giới hạn độ trễ của từng add, hệ số tăng cụ thể hoặc dung lượng tối đa khả dụng trên mọi máy.

### 11.3. Có thể dùng lại sau drain và clear

Sau khi lấy hết, chương trình thêm 1, 2, 3 ở first nên dãy là `[3, 2, 1]`. Clear làm size về 0; thêm 7 ở last sau đó vẫn hoạt động.

Trạng thái rỗng là một trạng thái sử dụng bình thường, không có nghĩa đối tượng ArrayDeque đã bị hủy.

## 12. Bộ nhớ sau khi lấy phần tử và sau clear

Mỗi lần gỡ đầu/cuối, triển khai bỏ tham chiếu ở ô đã gỡ. Điều đó tránh việc mảng nền tiếp tục giữ một đối tượng chỉ vì ô cũ chưa được xóa.

Nhưng có hai loại bộ nhớ cần phân biệt:

1. Đối tượng phần tử và các đối tượng mà nó tham chiếu.
2. Mảng nền chứa các ô tham chiếu của Deque.

Gỡ phần tử bỏ một tham chiếu từ Deque. Đối tượng có thể đủ điều kiện được thu gom nếu không còn tham chiếu giữ nó ở nơi khác. Không thể kết luận đối tượng được thu gom ngay lập tức.

### 12.1. Size giảm không có nghĩa mảng tự co ngay

Trong triển khai OpenJDK 17 đang xét, lấy phần tử không có cơ chế tự giảm mảng sau mỗi lần xóa; clear xóa các tham chiếu và đặt lại trạng thái, không tự cấp một mảng nhỏ hơn.

Vì vậy, một Deque từng đạt đỉnh rất lớn có thể còn giữ mảng dự phòng lớn khi số phần tử hiện tại đã ít.

Có thể biểu đạt chi phí lưu trữ theo độ dài mảng nền L, thay vì luôn gắn với n hiện tại. L có thể lớn hơn đáng kể n sau một giai đoạn tải cao.

### 12.2. Khi cần tách khỏi mảng nền cũ

Nếu yêu cầu giữ phần dữ liệu còn lại trong một container mới, có thể tạo:

```java
// Trích đoạn: tạo container riêng từ thứ tự hiện tại.
ArrayDeque<String> compactCopy = new ArrayDeque<>(remaining);
```

Constructor xây một Deque mới từ các phần tử của collection nguồn theo thứ tự iterator. Nó không phải lời gọi public để sửa trực tiếp capacity của đối tượng cũ.

Sau đó cần quản lý tham chiếu tới container cũ nếu mục tiêu là không giữ nó nữa. Khi các bên khác còn giữ đối tượng cũ, đổi một biến ở một nơi chưa làm nó biến mất.

Bản sao vẫn chia sẻ các đối tượng phần tử; đây không phải sao chép sâu dữ liệu nghiệp vụ.

### 12.3. Không coi tự tăng là bộ nhớ vô hạn

ArrayDeque không có giới hạn dung lượng cố định kiểu hàng đợi bounded do người gọi đặt, nhưng vẫn chịu giới hạn mảng và bộ nhớ của JVM.

Nếu luồng tạo công việc nhanh hơn luồng xử lý trong thời gian dài, tự tăng không thay thế kiểm soát tải hoặc chính sách giới hạn ở cấp ứng dụng.

Những yêu cầu chờ khi đầy, từ chối khi quá tải hoặc xử lý đa luồng cần cấu trúc và quy trình có hợp đồng phù hợp. Constructor có capacity ban đầu không tạo ra các khả năng ấy.

## 13. Những lỗi thường gặp khi suy luận từ mảng vòng

| Nhận định hoặc cách làm | Vấn đề | Cách hiểu đúng |
|---|---|---|
| Phần tử first luôn ở ô 0 | Bỏ qua head | First là vị trí logic hiện tại |
| tail luôn trỏ phần tử last | Sai quy ước vị trí ghi tiếp | Last nằm ở ô lùi một bước từ tail |
| head lớn hơn tail là trạng thái hỏng | Dữ liệu có thể quấn vòng | Đọc qua cuối mảng rồi tiếp tục ở đầu |
| Lấy đầu phải dồn mọi phần tử | Nhầm với mảng tuyến tính ngây thơ | Chuyển head và xóa ô đã lấy |
| Quay vòng nghĩa là ghi đè dữ liệu chưa lấy | Quên điều kiện hết chỗ | ArrayDeque tăng mảng khi cần |
| Mọi lần tăng đều nhân đôi | Không phải hợp đồng API | Cơ chế tăng là chi tiết triển khai |
| Độ dài mảng luôn là lũy thừa hai | Không được suy ra từ mô hình vòng | Dùng công thức chỉ số phù hợp với thiết kế |
| Khấu hao O(1) nghĩa mọi add O(1) | Bỏ qua lần resize | Một lần có thể O(n), tổng chuỗi mới có cận khấu hao |
| `new ArrayDeque<>(n)` giới hạn n phần tử | Nhầm capacity ban đầu với bound | Đây là chuẩn bị sức chứa |
| Clear luôn trả lại mảng lớn cho JVM | Không đúng với triển khai đang xét | Phân biệt xóa tham chiếu phần tử và thay mảng nền |
| Remove theo giá trị cũng rẻ như removeFirst | Cần tìm và điều chỉnh nội dung | Chọn đúng thao tác hai đầu |
| Dùng null để đánh dấu một phần tử đặc biệt | ArrayDeque không nhận null | Dùng kiểu hoặc giá trị hợp lệ rõ ràng |

Một lỗi ở cấp mã sử dụng là vừa duyệt bằng iterator vừa thay đổi cấu trúc qua phương thức khác. Iterator không phải ảnh chụp; sửa đổi có thể bị phát hiện và gây ConcurrentModificationException.

Nếu cần tiêu thụ từ một đầu, dùng vòng poll/remove phù hợp. Nếu chỉ cần đọc ổn định độc lập, cân nhắc bản sao. Không dùng fail-fast như một cơ chế phối hợp nhiều luồng.

ArrayDeque không tự an toàn cho truy cập đồng thời không có phối hợp. Tính đúng của công thức vòng và tính nhất quán giữa nhiều luồng là hai vấn đề khác nhau.

## 14. Chọn ArrayDeque và kiểm tra điều gì?

ArrayDeque thường phù hợp khi cần giữ một dãy không null, thêm/lấy chủ yếu ở hai đầu và không cần truy cập theo chỉ số qua API.

Trước khi chọn, hãy xác định:

- Chỉ dùng FIFO, chỉ dùng LIFO hay thật sự cần cả hai đầu?
- Rỗng là trạng thái bình thường hay lỗi giả định?
- Quy mô đang chờ có thể tăng đến đâu?
- Có cần giới hạn cứng, chờ hoặc phối hợp nhiều luồng không?
- Có thao tác tìm/xóa giữa dãy thường xuyên không?
- Có yêu cầu độ trễ tối đa mà phân tích khấu hao chưa đáp ứng không?

Không chọn bằng một khẳng định chung “mảng luôn nhanh hơn danh sách liên kết”. Cấu trúc, loại thao tác, bộ nhớ và môi trường đều ảnh hưởng đánh giá.

### 14.1. Những chuỗi kiểm tra hữu ích

Các trường hợp đáng kiểm tra trực tiếp qua API:

1. Rỗng, một phần tử, rồi rỗng lại.
2. Thêm/lấy ở first liên tiếp.
3. Thêm/lấy ở last liên tiếp.
4. Thêm last nhưng lấy first qua nhiều vòng.
5. Thêm xen kẽ cả hai đầu, sau đó gỡ cả hai đầu.
6. Bắt đầu nhỏ rồi thêm nhiều, kiểm tra đủ thứ tự và số lượng.
7. Lấy hết, clear rồi dùng lại.
8. Các lần xuất hiện có giá trị trùng nhau vẫn được giữ đúng số lượng.
9. Null bị từ chối ở ranh giới được thiết kế.
10. Duyệt xuôi và ngược cho thứ tự tương ứng.

Không cần biết chính xác lúc nào mảng tăng để kiểm tra hợp đồng của mã sử dụng. Nếu mục tiêu riêng là phân tích một phiên bản JDK, hãy dùng mã nguồn và công cụ đo phù hợp, đồng thời ghi rõ phiên bản.

### 14.2. Đừng dùng equals của Deque như equals của List

ArrayDeque không định nghĩa equals theo nội dung dãy giống List. Khi cần đối chiếu thứ tự, thu các giá trị lấy ra vào List hoặc so từng phần tử theo một quy trình rõ ràng như chương trình 3.

Dùng Set để kiểm tra đầu ra còn làm mất cả thứ tự lẫn số lần xuất hiện. Một kiểm tra như vậy có thể bỏ qua chính lỗi mà mảng vòng cần tránh.

## 15. Bài tập áp dụng

### Bài 1: đọc dãy đang quấn vòng

Mảng minh họa có `L = 8`, head = 6, tail = 3, các ô 6, 7, 0, 1, 2 lần lượt là A, B, C, D, E.

Dãy logic và size là gì?

**Gợi ý trả lời:** A, B, C, D, E; size bằng `3 - 6 + 8 = 5`.

### Bài 2: phần tử last ở đâu?

Trong cùng mô hình, tail bằng 0 và Deque không rỗng. Phần tử last có nằm ở ô 0 không?

**Gợi ý trả lời:** không theo quy ước đang dùng. Nó nằm ở ô lùi một bước theo vòng, tức ô 7 nếu L = 8.

### Bài 3: xóa đầu có phải sao chép không?

Nếu Deque có 1000 phần tử và gọi pollFirst không cần thay mảng, có phải chuyển 999 phần tử còn lại sang trái không?

**Gợi ý trả lời:** không. Cấu trúc có thể đọc và xóa ô head, rồi tiến head. Đó là lợi ích của cách quản lý vòng.

### Bài 4: thêm vào khi đã kín

Nếu head đã đi qua nhiều vị trí nhưng số phần tử vẫn lấp đầy vùng lưu theo quy ước, quay về đầu mảng có tạo thêm chỗ không?

**Gợi ý trả lời:** không. Chỉ các ô thực sự đã trống mới được tái sử dụng. ArrayDeque cần tăng mảng khi không còn đủ chỗ, không ghi đè phần tử chưa được lấy.

### Bài 5: đọc O(1) khấu hao

Một lần addLast mất nhiều thời gian hơn các lần trước vì cần tăng mảng. Điều đó có mâu thuẫn với O(1) khấu hao không?

**Gợi ý trả lời:** không. Khấu hao nói về tổng công việc của một chuỗi; một thao tác riêng vẫn có thể tốn O(n).

### Bài 6: constructor và giới hạn

Khởi tạo ArrayDeque với 16 có bảo đảm phần tử thứ 17 bị từ chối không?

**Gợi ý trả lời:** không. Con số là nhu cầu sức chứa ban đầu, không phải giới hạn cố định của collection.

### Bài 7: size nhỏ sau một đỉnh tải lớn

Deque từng có rất nhiều phần tử, sau đó chỉ còn một ít. Có thể suy ra mảng nền đã nhỏ tương ứng không?

**Gợi ý trả lời:** không với triển khai đang xét. Size phản ánh số phần tử hiện tại; mảng nền có thể còn giữ sức chứa từ trước.

### Bài 8: tăng mảng có clone đối tượng không?

Một đối tượng Customer được giữ cả trong Deque lẫn một biến ngoài. Sau tăng mảng, đó có tự thành hai Customer độc lập không?

**Gợi ý trả lời:** không. Tăng mảng tổ chức lại các tham chiếu, không mặc định sao chép sâu đối tượng.

### Bài 9: đầu ra không đủ để đọc chỉ số

Hai phiên bản JDK cùng in `[A, B, C]`. Có thể kết luận chúng có cùng head, tail và độ dài mảng không?

**Gợi ý trả lời:** không. Đó là cùng dãy logic; nhiều bố trí nội bộ có thể biểu đạt cùng kết quả.

Khi dùng ArrayDeque, hãy giữ rõ sự khác nhau giữa dãy logic, vị trí vòng và dung lượng đã cấp phát. Nhờ đó có thể khai thác thao tác hai đầu mà vẫn hiểu đúng lúc tăng mảng, chi phí khấu hao và vòng đời bộ nhớ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="queue-deque-007">Vì sao ArrayDeque không nhận null</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao ArrayDeque không nhận null

## 1. Đây là hợp đồng của lớp, không phải hạn chế của mảng Java

`ArrayDeque` là một cách triển khai `Deque` bằng mảng có thể tăng dung lượng. Bạn có thể thêm và lấy phần tử ở cả hai đầu, dùng nó như hàng đợi hoặc ngăn xếp.

Nhưng có một điều kiện rõ ràng: **không được lưu null làm phần tử**. Việc gọi một phương thức chèn với null dẫn đến `NullPointerException`, kể cả khi phương thức mang tên `offer`.

Điều này không có nghĩa mảng tham chiếu trong Java không chứa được null. Mảng Object có thể chứa null. Đây là một lựa chọn của ArrayDeque, giúp giữ ý nghĩa của giá trị báo không có phần tử và phù hợp với cách lớp tổ chức các ô lưu trữ.

Có hai lớp giải thích cần phân biệt:

- Ở API, null được dùng làm kết quả báo rằng không có phần tử để xem hoặc lấy.
- Trong cách triển khai OpenJDK 17, các ô mảng không chứa thành viên của deque cũng được giữ là null.

Người dùng lớp phải tuân theo hợp đồng công khai. Chi tiết mảng giúp hiểu cơ chế, nhưng không phải lý do để chương trình truy cập trực tiếp các trường riêng bên trong.

Bài học dùng ví dụ Java 17 để kiểm chứng các đường chèn, trường hợp thêm cả lô và cách biểu diễn một thông điệp có dữ liệu bên trong vắng mặt. Bạn chỉ cần biết biến tham chiếu, phương thức và tập hợp tổng quát; các trường hợp dễ nhầm sẽ được tách rõ.

## 2. Từ “null” có thể đang chỉ những điều khác nhau

Xét các biểu thức:

| Trường hợp | Ý nghĩa |
| --- | --- |
| `Deque<String> q = null` | Chưa có đối tượng deque để gọi phương thức |
| `q.addLast(null)` | Cố thêm tham chiếu null làm một thành viên |
| `q.pollFirst() == null` | Với ArrayDeque hợp lệ, thao tác không lấy được phần tử |
| `new Message(null)` | Có đối tượng Message, nhưng một trường bên trong là null |
| `"null"` | Một đối tượng chuỗi chứa bốn ký tự n, u, l, l |
| `""` | Một đối tượng chuỗi có độ dài 0 |

ArrayDeque cấm trường hợp **phần tử chính là null**. Nó không tự cấm mọi đối tượng có trường null, không cấm chuỗi rỗng và không cấm chuỗi chứa chữ “null”.

Nếu biến q tự nó bằng null, lỗi khi gọi q.addLast khác với việc có một ArrayDeque thật từ chối một phần tử null. Cùng tên NullPointerException không đủ để kết luận lỗi nằm ở đâu; phải xem tham chiếu nào đang vắng hoặc điều kiện nào bị kiểm tra.

Kiểu `Deque<String>` trong Java 17 không tự biểu diễn ràng buộc “tham chiếu phần tử chắc chắn khác null” ở cấp trình biên dịch. Cách triển khai kiểm tra điều kiện của nó khi nhận dữ liệu. Vì vậy, một lời gọi có thể biên dịch nhưng vẫn bị từ chối lúc chạy.

## 3. Lý do ở API: giữ null làm tín hiệu không có phần tử

ArrayDeque có các phép:

~~~text
peekFirst / peekLast : xem một đầu, không xóa
pollFirst / pollLast : lấy ra và xóa một đầu
~~~

Khi rỗng, chúng trả null. Nếu không rỗng, chúng trả một phần tử thực đang được lưu ở đầu tương ứng.

Vì ArrayDeque không có thành viên null, hai tình trạng được phân biệt rõ:

~~~text
kết quả khác null → đã nhận một phần tử
kết quả bằng null → không có phần tử ở lần xem/lấy đó
~~~

Với một cấu trúc cho phép lưu null, kết quả null có thể mang hai nghĩa: “không có thành viên” hoặc “có thành viên nhưng giá trị của nó là null”. Nếu API chỉ trả một tham chiếu, người gọi không thể phân biệt chỉ bằng giá trị ấy.

Ví dụ phản chứng dùng LinkedList, lớp cho phép null:

~~~text
nội dung ban đầu: [null, next]
pollFirst()    : null
còn lại        : [next]
~~~

Null vừa được trả là dữ liệu đã lấy ra, không phải bằng chứng deque ban đầu rỗng. ArrayDeque loại bỏ sự nhập nhằng này bằng cách không chấp nhận dữ liệu null ngay từ đầu.

Deque nói chung không buộc mọi cách triển khai phải cấm null, nhưng khuyến cáo mạnh việc tránh null vì vai trò báo rỗng. ArrayDeque chọn chính sách cấm cụ thể và tường minh. [Java 17: Deque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html).

## 4. Lý do trong cách triển khai: ô trống và thành viên có ý nghĩa khác nhau

OpenJDK 17 dùng một mảng vòng để chứa tham chiếu. Những ô không thuộc thành viên được giữ null; đầu chỉ vị trí lấy tiếp theo, đuôi chỉ vị trí chèn tiếp theo. Khi lấy một phần tử ra, ô cũ được xóa về null. [Mã nguồn ArrayDeque, OpenJDK 17](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/ArrayDeque.java).

Mô hình minh họa dưới đây dùng tám ô giả định:

~~~text
chỉ số:   0      1      2      3      4      5      6      7
ô mảng:  [C]  [null] [null] [null] [null] [null]  [A]    [B]

đầu = 6: vị trí của A
đuôi = 1: ô trống để chèn tiếp theo
thứ tự thành viên: ô 6 (A) → ô 7 (B) → ô 0 (C)
~~~

Đường đi logic trong sơ đồ quấn qua cuối mảng rồi về ô 0. Các ô null không phải phần tử được duyệt. Số tám chỉ để minh họa, không là dung lượng mặc định.

Quy tắc không lưu phần tử null giúp việc đọc một ô ở đầu phân biệt được có phần tử hay không. Nếu cho null làm dữ liệu mà giữ nguyên cách kiểm tra ấy, một giá trị hợp lệ sẽ bị lẫn với ô trống.

Một cấu trúc mảng khác có thể hỗ trợ null bằng cách quản lý sự hiện diện theo một cơ chế khác. Do đó, không nên suy “mảng vòng về lý thuyết không thể lưu null”. Điểm đang học là sự phối hợp giữa hợp đồng và biểu diễn của ArrayDeque.

## 5. poll và get có thể dùng chung ý nghĩa của ô không chứa phần tử

Trong mô hình của ArrayDeque, khi có một tham chiếu thật ở đầu, pollFirst lấy nó ra, bỏ tham chiếu khỏi ô cũ và tiến đầu. Khi không có phần tử, kết quả là null.

GetFirst là dạng yêu cầu phải có phần tử: nếu không có, nó ném `NoSuchElementException`; nếu có, nó trả phần tử mà không xóa.

Nhờ ràng buộc cấm null, kết quả rỗng không bị nhầm với một thành viên hợp lệ. Nhưng người dùng vẫn phải chọn đúng tác dụng:

| Phương thức | Khi có phần tử | Khi rỗng |
| --- | --- | --- |
| `peekFirst()` | Trả phần tử, giữ nguyên deque | null |
| `pollFirst()` | Trả phần tử và xóa nó | null |
| `getFirst()` | Trả phần tử, giữ nguyên deque | NoSuchElementException |
| `removeFirst()` | Trả phần tử và xóa nó | NoSuchElementException |

Phía Last có các cặp tương tự cho đầu cuối. Các tên Queue ngắn hơn như peek và poll làm việc ở đầu trước của deque.

Cấm null không làm mọi thao tác trở thành phép lấy ra. Peek vẫn chỉ xem; không thể dùng việc nhìn thấy phần tử như bằng chứng nó đã bị xử lý hoặc đã rời cấu trúc.

## 6. Xóa tham chiếu cũ không phải cam kết thu gom bộ nhớ tức thời

Khi một phần tử bị loại, đặt ô cũ về null còn có tác dụng bỏ tham chiếu của deque tới đối tượng ấy. Một vùng mảng đã không còn giữ thành viên không nên tiếp tục giữ đối tượng chỉ vì giá trị cũ còn nằm trong ô.

Điều đó không có nghĩa đối tượng được thu gom ngay sau poll. Nếu biến khác hoặc cấu trúc khác vẫn tham chiếu tới nó, đối tượng vẫn có thể được dùng. Thời điểm thu gom bộ nhớ cũng không do lời gọi poll tự bảo đảm.

Trong chương trình kiểm chứng, chúng ta chỉ quan sát hợp đồng thành viên và kết quả phương thức. Không gọi bộ thu gom để cố chứng minh thời điểm một đối tượng biến mất, và không dùng phản chiếu để đọc mảng riêng của lớp.

Hãy tách “deque đã bỏ tham chiếu của nó” khỏi “không còn tham chiếu nào trong toàn chương trình”. Hai kết luận này không tương đương.

## 7. Những đường chèn nào đều từ chối null?

Với ArrayDeque, chính sách áp dụng cho các cách thêm trực tiếp:

| Lời gọi | Ý nghĩa khi phần tử hợp lệ | Khi phần tử là null |
| --- | --- | --- |
| `add(e)` | Thêm phía cuối | NullPointerException |
| `offer(e)` | Thêm phía cuối | NullPointerException |
| `addFirst(e)` | Thêm phía đầu | NullPointerException |
| `addLast(e)` | Thêm phía cuối | NullPointerException |
| `offerFirst(e)` | Thêm phía đầu | NullPointerException |
| `offerLast(e)` | Thêm phía cuối | NullPointerException |
| `push(e)` | Đưa vào đầu khi dùng như ngăn xếp | NullPointerException |

Không có đường vòng “dùng push thay add” để đưa null vào. Đổi tên phương thức không đổi hợp đồng phần tử.

Offer là dạng có thể dùng giá trị false để báo không chèn được vì điều kiện dung lượng ở những cấu trúc bị giới hạn. Nó không biến một tham chiếu đầu vào bị cấm thành kết quả false.

ArrayDeque tự tăng dung lượng khi cần theo hợp đồng của lớp và không đặt một sức chứa tối đa cố định qua số khởi tạo. Null bị từ chối là vấn đề dữ liệu, không phải bằng chứng deque đang đầy. [Java 17: ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html).

Nếu thay kiểu biến từ ArrayDeque sang Deque hoặc Queue nhưng vẫn dùng cùng đối tượng ArrayDeque, chính sách cũng không đổi. Kiểu interface không tắt các kiểm tra của đối tượng thực tế.

## 8. Hàm khởi tạo và addAll cũng phải tuân theo chính sách

Không chỉ lời gọi thêm từng phần tử mới có thể nhận dữ liệu không hợp lệ. Một nguồn Collection có thể chứa null rồi được truyền vào hàm khởi tạo hoặc addAll.

Ví dụ nguồn hợp lệ đối với Arrays.asList nhưng không hợp lệ làm thành viên ArrayDeque:

~~~java
List<String> input = Arrays.asList("before", null, "after");
~~~

Đoạn minh họa cần nhập `java.util.List` và `java.util.Arrays`. Arrays.asList cho phép phần tử null nên có thể dùng nó để kiểm tra chính deque nhận dữ liệu.

Nếu tạo `new ArrayDeque<>(input)`, việc khởi tạo không hoàn thành bình thường và mã gọi nhận NullPointerException. Bạn không nhận được một deque đã tạo thành công với một thành viên null.

Nếu đang có một deque rồi gọi addAll, cần chú ý thêm một điểm: **ngoại lệ không có nghĩa mọi thay đổi trước đó của lời gọi đã được hoàn tác**.

Trong ví dụ OpenJDK được kiểm chứng ở chương trình:

~~~text
deque trước: [stable]
nguồn:       [before, null, after]

addAll ném NullPointerException

deque sau:   [stable, before]
~~~

Before đã được thêm; null bị từ chối; after chưa được thêm. Không có thành viên null nào vượt qua chính sách, nhưng deque đã thay đổi một phần.

Hợp đồng thêm lô của Deque có nêu khả năng chỉ một phần dữ liệu được thêm trước khi lỗi xuất hiện. Đừng coi addAll là một giao dịch nguyên tử chỉ vì nó là một lời gọi phương thức. [Java 17: Deque.addAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html#addAll(java.util.Collection)).

## 9. Kiểm tra lô trước khi sửa deque

Nếu hợp đồng của hàm là “từ chối lô có null trước khi thêm bất kỳ phần tử nào của lô”, có thể thực hiện hai giai đoạn:

1. Chụp nguồn vào một danh sách riêng và kiểm tra các tham chiếu phần tử.
2. Chỉ thêm danh sách đã kiểm tra vào đích khi toàn bộ kiểm tra thành công.

Hàm trong chương trình:

~~~java
static <E> void appendValidated(
        ArrayDeque<E> target, Collection<? extends E> input) {
    List<E> snapshot = new ArrayList<>(input);
    for (E element : snapshot) {
        Objects.requireNonNull(element, "Phan tu cua lo khong duoc null");
    }
    target.addAll(snapshot);
}
~~~

Đây là đoạn trích từ mã hoàn chỉnh. Danh sách chụp giữ cùng các tham chiếu phần tử, không nhân bản sâu từng đối tượng.

Khi lô có null, lỗi xảy ra ở giai đoạn kiểm tra trước khi hàm gọi addAll trên target. Trong ví dụ, target vẫn là `[stable]`. Khi lô sạch gồm first và second, kết quả là `[stable, first, second]`.

Phạm vi bảo đảm cần nói chính xác: hàm tránh thay đổi target do **phát hiện null trong lô ở bước kiểm tra** trong điều kiện sử dụng của ví dụ. Nó không phải một cơ chế giao dịch cho mọi lỗi tài nguyên hoặc mọi thao tác cạnh tranh.

Kiểu đích được chọn là ArrayDeque, không phải một Deque bất kỳ có thể đầy giữa lô vì giới hạn cố định. Không tự mở rộng kết luận này sang những cách triển khai có điều kiện chấp nhận phần tử khác.

Việc lọc bỏ null là một chính sách khác với từ chối. Chỉ bỏ qua khi dữ liệu cho phép điều đó; đừng tự xóa một thông điệp thiếu rồi báo rằng toàn bộ lô đã được chấp nhận nguyên vẹn.

## 10. Chương trình đầy đủ dùng Java 17

Lưu thành `ArrayDequeNullDemo.java`. Chương trình kiểm tra các đường chèn, tìm/xóa theo null, nguồn Collection có null, thêm lô, tín hiệu rỗng và hai cách bọc dữ liệu bên trong có thể thiếu.

Hàm checkNullInput chỉ phục vụ các phép thử. Tham số Runnable giữ một lời gọi, và `() -> ...` tạo thao tác để hàm chạy. Mỗi phép thử biết deque nhận lời gọi là đối tượng thật, nên lỗi đang được kiểm tra là chính sách với đầu vào null.

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

public class ArrayDequeNullDemo {
    record Message(String payload) {}

    static void checkNullInput(String label, Runnable operation) {
        try {
            operation.run();
            System.out.println(label + "=accepted");
        } catch (NullPointerException exception) {
            System.out.println(label + "=" + exception.getClass().getSimpleName());
        }
    }

    static <E> void appendValidated(
            ArrayDeque<E> target, Collection<? extends E> input) {
        List<E> snapshot = new ArrayList<>(input);
        for (E element : snapshot) {
            Objects.requireNonNull(element, "Phan tu cua lo khong duoc null");
        }
        target.addAll(snapshot);
    }

    public static void main(String[] args) {
        ArrayDeque<String> direct = new ArrayDeque<>();
        direct.addLast("stable");
        checkNullInput("add", () -> direct.add(null));
        checkNullInput("offer", () -> direct.offer(null));
        checkNullInput("addFirst", () -> direct.addFirst(null));
        checkNullInput("addLast", () -> direct.addLast(null));
        checkNullInput("offerFirst", () -> direct.offerFirst(null));
        checkNullInput("offerLast", () -> direct.offerLast(null));
        checkNullInput("push", () -> direct.push(null));
        System.out.println("after direct attempts=" + direct);
        System.out.println("contains(null)=" + direct.contains(null));
        System.out.println("remove(null)=" + direct.remove(null));
        System.out.println("after null search=" + direct);

        checkNullInput("constructor", () ->
                new ArrayDeque<>(Arrays.asList("before", null, "after")));
        checkNullInput("List.of source", () ->
                List.of("before", null, "after"));

        ArrayDeque<String> bulk = new ArrayDeque<>();
        bulk.addLast("stable");
        List<String> badBatch = Arrays.asList("before", null, "after");
        checkNullInput("addAll", () -> bulk.addAll(badBatch));
        System.out.println("after failing addAll=" + bulk);

        ArrayDeque<String> checked = new ArrayDeque<>();
        checked.addLast("stable");
        checkNullInput("validated batch", () -> appendValidated(checked, badBatch));
        System.out.println("after validation rejection=" + checked);
        appendValidated(checked, List.of("first", "second"));
        System.out.println("after valid batch=" + checked);

        Deque<String> signals = new ArrayDeque<>();
        signals.addLast("A");
        System.out.println("nonempty peek=" + signals.peekFirst());
        System.out.println("size after peek=" + signals.size());
        System.out.println("nonempty poll=" + signals.pollFirst());
        System.out.println("empty peek=" + signals.peekFirst());
        System.out.println("empty poll=" + signals.pollFirst());
        try {
            signals.getFirst();
        } catch (NoSuchElementException exception) {
            System.out.println("empty getFirst=" + exception.getClass().getSimpleName());
        }

        Deque<String> nullable = new LinkedList<>();
        nullable.addLast(null);
        nullable.addLast("next");
        System.out.println("linked first poll=" + nullable.pollFirst());
        System.out.println("linked empty after null=" + nullable.isEmpty());
        System.out.println("linked next poll=" + nullable.pollFirst());

        Deque<Message> messages = new ArrayDeque<>();
        messages.addLast(new Message(null));
        messages.addLast(new Message("hello"));
        Message first = messages.pollFirst();
        System.out.println("message reference present=" + (first != null));
        System.out.println("message payload absent=" + (first.payload() == null));
        System.out.println("second payload=" + messages.pollFirst().payload());
        System.out.println("no message=" + messages.pollFirst());

        Deque<Optional<String>> options = new ArrayDeque<>();
        options.addLast(Optional.empty());
        options.addLast(Optional.of("data"));
        Optional<String> wrapper = options.pollFirst();
        System.out.println("optional wrapper present=" + (wrapper != null));
        System.out.println("optional payload present=" + wrapper.isPresent());
        System.out.println("next optional=" + options.pollFirst());
        System.out.println("no optional element=" + options.pollFirst());

        Deque<String> text = new ArrayDeque<>();
        text.addLast("");
        text.addLast("null");
        System.out.println("empty string length=" + text.pollFirst().length());
        String literal = text.pollFirst();
        System.out.println("literal value='" + literal + "'");
        System.out.println("literal is null=" + (literal == null));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 ArrayDequeNullDemo.java
java ArrayDequeNullDemo
~~~

Dùng JDK 17 trở lên. `--release 17` kiểm tra mã và API theo Java 17. Tên tệp phải khớp lớp public.

Kết quả chuẩn:

~~~text
add=NullPointerException
offer=NullPointerException
addFirst=NullPointerException
addLast=NullPointerException
offerFirst=NullPointerException
offerLast=NullPointerException
push=NullPointerException
after direct attempts=[stable]
contains(null)=false
remove(null)=false
after null search=[stable]
constructor=NullPointerException
List.of source=NullPointerException
addAll=NullPointerException
after failing addAll=[stable, before]
validated batch=NullPointerException
after validation rejection=[stable]
after valid batch=[stable, first, second]
nonempty peek=A
size after peek=1
nonempty poll=A
empty peek=null
empty poll=null
empty getFirst=NoSuchElementException
linked first poll=null
linked empty after null=false
linked next poll=next
message reference present=true
message payload absent=true
second payload=hello
no message=null
optional wrapper present=true
optional payload present=false
next optional=Optional[data]
no optional element=null
empty string length=0
literal value='null'
literal is null=false
~~~

## 11. Đọc kết quả theo từng ranh giới

### 11.1. Không có đường chèn null thành công

Bảy dòng đầu đều là NullPointerException. Sau chúng, `after direct attempts=[stable]` cho biết phần tử cũ vẫn được giữ và không có thành viên mới nào từ các lời gọi chèn null.

Phương thức offer cũng ném lỗi chứ không trả false. Push không tạo một ngoại lệ cho chính sách chỉ vì đang dùng deque như ngăn xếp.

### 11.2. Tìm null khác với chèn null

`contains(null)=false` và `remove(null)=false` cho thấy hai lời gọi tìm/xóa theo giá trị không tìm được thành viên null trong ArrayDeque của ví dụ. Nội dung vẫn là stable.

Đừng suy câu “ArrayDeque không nhận null làm phần tử” thành “bất kỳ phương thức nào có đối số null đều phải ném NullPointerException”. Chính sách với từng tham số vẫn phải đọc theo chữ ký phương thức. Các thao tác chèn và thao tác hỏi sự hiện diện không phải cùng một việc.

### 11.3. Thêm lô có thể đổi trạng thái trước khi báo lỗi

`after failing addAll=[stable, before]` đối chiếu rõ phần được thêm trước lỗi. Nếu mã bắt ngoại lệ rồi thử lại nguyên lô, nó có thể thêm lại một bản before nữa. ArrayDeque cho phép các phần tử bằng nhau xuất hiện nhiều lần, nên không có cơ chế loại trùng tự bù cho một chính sách thử lại sai.

`after validation rejection=[stable]` cho thấy cách kiểm tra trước đã đổi thời điểm phát hiện lỗi: target chưa nhận phần tử nào của lô.

### 11.4. Hai nguồn NullPointerException khác nhau

`constructor=NullPointerException` kiểm tra ArrayDeque nhận một nguồn cho phép null.

`List.of source=NullPointerException` kiểm tra một bước khác: List.of đã từ chối null lúc tạo nguồn. Lúc đó chưa có một lời gọi chèn dữ liệu ấy vào deque.

Vì vậy, khi viết ví dụ hoặc kiểm thử, hãy biết lỗi xảy ra ở nguồn hay ở nơi nhận. Tạo `List.of("before", null, "after")` rồi dùng nó để “chứng minh addAll từ chối null” là gán sai trách nhiệm cho phương thức chưa được chạy.

## 12. Không có phần tử và có thông điệp thiếu dữ liệu là hai trạng thái

Giả sử một thông điệp có thể tồn tại nhưng chưa có payload. Bạn không cần biến chính phần tử của deque thành null để diễn đạt điều đó.

Chương trình định nghĩa:

~~~java
record Message(String payload) {}
~~~

`new Message(null)` là một tham chiếu Message thật. Nó khác `null` và được ArrayDeque nhận. Chỉ trường payload bên trong mới không có giá trị.

Kết quả:

~~~text
message reference present=true
message payload absent=true
~~~

Hai dòng này trả lời hai câu hỏi riêng:

- Có lấy được một thông điệp ra không?
- Thông điệp ấy có payload không?

Khi không còn Message nào, pollFirst trả null ở cấp tham chiếu thông điệp. Khi đã nhận một Message, mã mới xem ý nghĩa trường bên trong theo hợp đồng của Message.

ArrayDeque không tự kiểm tra sâu mọi trường của đối tượng. Nếu nghiệp vụ yêu cầu payload bắt buộc phải có, hãy đặt điều kiện đó trong mô hình hoặc tại nơi nhận thông điệp. Việc deque chấp nhận một đối tượng không chứng minh đối tượng đáp ứng mọi quy tắc nghiệp vụ.

Mẫu wrapper giúp tách sự hiện diện của một phần tử khỏi sự hiện diện của dữ liệu chứa trong phần tử ấy. Nó không phải cách lách chính sách để lưu một thành viên null: thành viên được lưu là Message khác null.

## 13. Optional.empty cũng là một đối tượng khác null

Một lựa chọn minh họa khác là `Deque<Optional<String>>`:

~~~text
không có phần tử của deque          → poll trả null
có Optional.empty trong deque       → poll trả một Optional khác null, không có giá trị bên trong
có Optional chứa "data" trong deque → poll trả Optional[data]
~~~

Chương trình nhận:

~~~text
optional wrapper present=true
optional payload present=false
~~~

Điều đó không mâu thuẫn với chính sách cấm null. Optional.empty là một giá trị Optional hợp lệ; nó không phải tham chiếu null.

Ngược lại, `options.addLast(null)` vẫn bị từ chối, dù kiểu phần tử là Optional. Bọc kiểu tổng quát không làm tham chiếu null ở cấp ngoài trở nên hợp lệ.

Optional có API mô tả sự có hoặc không của giá trị bên trong. Khi mô hình một thông điệp thực, một kiểu miền như Message có thể diễn đạt tên trường và ý nghĩa sự vắng mặt rõ hơn. Không cần bọc mọi phần tử chỉ để tránh viết điều kiện đầu vào; chọn mô hình theo ý nghĩa dữ liệu. [Java 17: Optional](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html).

Đừng dùng đồng nhất tham chiếu của các Optional.empty để suy quy tắc nghiệp vụ. Trong ví dụ, chúng ta chỉ dùng tính khác null của phần tử và isPresent của giá trị được bọc.

## 14. Chuỗi rỗng và chuỗi “null” vẫn là dữ liệu thật

Chương trình thêm lần lượt `""` và `"null"` vào ArrayDeque rồi lấy ra.

Kết quả:

~~~text
empty string length=0
literal value='null'
literal is null=false
~~~

Chuỗi rỗng có một tham chiếu thật nhưng không có ký tự. Chuỗi “null” có bốn ký tự. Cả hai đều khác null ở cấp tham chiếu nên ArrayDeque nhận được.

Đừng dùng cách hiển thị làm bằng chứng về danh tính tham chiếu. Một chuỗi in ra chữ null và một tham chiếu null có thể gây nhầm nếu chỉ nhìn dòng văn bản thiếu ngữ cảnh. Chương trình đặt dấu nháy quanh chuỗi và kiểm tra `literal == null` để phân biệt.

Tương tự, số 0 hoặc một đối tượng có tên “EMPTY” vẫn có thể là dữ liệu hợp lệ nếu mô hình cho phép. Muốn dùng một đối tượng làm tín hiệu riêng của ứng dụng, phải xác định rõ kiểu và chính sách; deque không tự biết rằng tên hoặc nội dung của nó là đặc biệt.

Nếu thay một giá trị null bị lỗi bằng chuỗi “null” chỉ để chèn được, bạn đã thay dữ liệu, không chỉ sửa cách lưu. Việc đó chỉ hợp lệ khi hợp đồng đầu vào thực sự muốn một chuỗi như vậy.

## 15. Không suy chính sách chung cho mọi Collection từ ArrayDeque

Một số cấu trúc như ArrayList hoặc LinkedList có thể nhận null. ArrayDeque thì không. Việc các lớp cùng giữ tham chiếu đối tượng không buộc chúng có cùng hợp đồng phần tử.

Khi chuyển dữ liệu từ nguồn cho phép null sang đích cấm null, cần chọn chính sách tường minh:

| Chính sách | Ý nghĩa |
| --- | --- |
| Từ chối lô | Giữ nguyên yêu cầu rằng mọi thành viên phải có tham chiếu thật |
| Bỏ qua null | Chấp nhận mất những vị trí dữ liệu đó vì miền sử dụng đã cho phép |
| Dùng kiểu thông điệp khác null có trường tùy chọn | Giữ sự hiện diện của thông điệp, biểu diễn riêng dữ liệu bên trong vắng mặt |
| Sửa dữ liệu ở nguồn | Thực hiện một phép chuyển đổi có ý nghĩa và được xác định trước |

Không chỉ thay lớp bằng một cấu trúc cho phép null để làm ngoại lệ biến mất rồi tiếp tục dùng các vòng poll đến null như cũ. Khi dữ liệu null được phép vào, cách giải thích kết quả trả về đã thay đổi.

Cũng không coi Arrays.asList cho phép null là bằng chứng mọi Collection nhận lô đó đều phải chấp nhận. Mỗi nơi nhận có quyền áp dụng hợp đồng phần tử của nó; bước chuyển là ranh giới cần kiểm tra.

Trong chương trình, LinkedList chỉ được dùng làm phản ví dụ để hiểu sự mơ hồ ở kết quả null. Các thao tác còn lại chọn ArrayDeque để giữ chính sách không có thành viên null nhất quán.

## 16. Giới hạn của suy luận từ null và chi tiết mảng

Với ArrayDeque được dùng hợp lệ, kết quả null từ một lần poll/peek biểu thị không có phần tử để trả ở lần thao tác đó. Nó không có nghĩa sẽ không có phần tử nào được thêm về sau.

Một lần peek khác null cũng không dành riêng phần tử cho bên vừa nhìn. Nếu có thao tác sửa xen vào giữa peek và poll, hai lời gọi có thể quan sát những trạng thái khác nhau.

ArrayDeque không được đồng bộ hóa sẵn để nhiều luồng tùy ý cùng sửa. Các quy tắc về ô trống trong cách triển khai không phải cơ chế phối hợp giữa các luồng. Không thể dựa vào việc “đã kiểm tra khác null” để thay thế một hợp đồng chia sẻ dữ liệu đúng.

Chi tiết mảng vòng ở phần giải thích được đối chiếu với OpenJDK 17. Các số chỉ vị trí, kích thước mảng và cách tăng dung lượng không phải API để mã ứng dụng bám vào. Các ví dụ chỉ dùng các phương thức công khai để kiểm chứng những kết quả mà người dùng lớp được phép dựa vào.

Nếu cần tìm nguyên nhân NullPointerException, xác định trước lỗi là do biến deque null, do chèn phần tử null, do lấy từ một deque rỗng rồi dùng kết quả không kiểm tra, hay do trường bên trong đối tượng. Không gom các nguyên nhân ấy thành một câu “ArrayDeque lỗi null”.

## 17. Những nhầm lẫn thường gặp

| Nhầm lẫn | Điều cần hiểu đúng |
| --- | --- |
| “Mảng Java không thể có null” | Mảng tham chiếu có thể; ArrayDeque cấm null như một thành viên theo hợp đồng |
| “Offer dùng false nên offer(null) không ném lỗi” | Dữ liệu null bị cấm vẫn dẫn đến NullPointerException |
| “Dùng push có thể đưa null vào ngăn xếp” | Push vẫn phải tuân chính sách của cùng ArrayDeque |
| “Đổi biến sang Deque sẽ bỏ kiểm tra” | Đối tượng thực tế vẫn là ArrayDeque |
| “Có null trong ô mảng nghĩa là deque đang chứa null” | Ô không giữ thành viên khác với một phần tử hợp lệ |
| “Poll xóa ô thì đối tượng được thu gom ngay” | Nó bỏ một tham chiếu; còn phải xét các tham chiếu khác và cơ chế thu gom |
| “AddAll ném lỗi thì đích chắc chắn không thay đổi” | Có thể đã thêm một phần trước khi gặp null |
| “List.of có null là nguồn tốt để kiểm thử deque” | List.of có thể lỗi trước khi deque được gọi |
| “ArrayDeque sẽ kiểm tra mọi trường bên trong” | Nó kiểm tra tham chiếu phần tử, không tự áp mọi quy tắc của kiểu miền |
| “Optional.empty chính là null” | Đó là một Optional khác null, không chứa giá trị bên trong |
| “Chuỗi có chữ null là tham chiếu null” | Chuỗi là một đối tượng thật với nội dung ký tự |
| “Một số Collection nhận null thì ArrayDeque cũng phải nhận” | Chính sách của lớp nhận cần được đọc riêng |
| “Thay null bằng chuỗi rỗng không làm đổi dữ liệu” | Đó là một phép chuyển đổi ý nghĩa, cần hợp đồng rõ |

Một thói quen hữu ích là phân biệt ba cấp: đối tượng chứa, tham chiếu phần tử, và dữ liệu bên trong phần tử. Nhiều lỗi chỉ xuất hiện vì ba cấp ấy bị gọi chung bằng một từ “null”.

## 18. Thực hành có lời giải

### Bài 1: offer null khi còn rất nhiều chỗ

Một ArrayDeque vừa được tạo rỗng. OfferLast(null) có trả false vì chưa thêm được không?

**Lời giải:** Không. Null là phần tử bị cấm nên lời gọi ném NullPointerException. Trạng thái dung lượng không biến dữ liệu bị cấm thành một giá trị hợp lệ.

### Bài 2: cùng đối tượng, kiểu biến khác nhau

Bạn giữ một ArrayDeque qua biến Deque<String> rồi gọi push(null). Chính sách có thay đổi không?

**Lời giải:** Không. Đối tượng thực tế vẫn là ArrayDeque và push là một đường chèn vào nó. Null vẫn bị từ chối.

### Bài 3: ô trống trong mảng

Một sơ đồ mảng bên trong có nhiều ô null nhưng size của deque bằng 3. Có phải deque đang giữ các thành viên null không?

**Lời giải:** Không. Những ô không chứa thành viên có thể mang null để biểu diễn ô trống. Size và thứ tự thành viên không đồng nhất với toàn bộ độ dài mảng vật lý.

### Bài 4: đọc kết quả poll

ArrayDeque<String> hợp lệ đang chứa một A. PollFirst hai lần lần lượt cho gì?

**Lời giải:** Lần đầu trả A và xóa nó. Lần sau trả null vì không còn thành viên. Với lớp này, không có một phần tử null thật để gây nhập nhằng.

### Bài 5: thêm lô lỗi giữa chừng

Deque trước là `[stable]`, lô Arrays.asList gồm `before, null, after`. Có thể coi NullPointerException từ addAll là bằng chứng stable là thành viên duy nhất sau đó không?

**Lời giải:** Không. Ví dụ trong chương trình giữ `[stable, before]` sau lỗi. Phần đầu lô có thể đã được thêm, còn null không được lưu. Muốn từ chối lô trước khi sửa đích, cần kiểm tra trước theo hợp đồng phù hợp.

### Bài 6: phép kiểm thử lỗi sai nơi

Bạn tạo List.of chứa null rồi định truyền vào ArrayDeque. Ngoại lệ xuất hiện. Có thể kết luận hàm khởi tạo ArrayDeque đã chạy và từ chối nguồn không?

**Lời giải:** Không nếu lỗi xuất hiện lúc tạo List.of. Cần một nguồn thật sự cho phép null, chẳng hạn Arrays.asList, để đi tới bước kiểm tra deque nhận dữ liệu.

### Bài 7: đối tượng có trường null

Có thể thêm `new Message(null)` vào ArrayDeque<Message> không?

**Lời giải:** Có theo chính sách tham chiếu phần tử của ArrayDeque, vì đối tượng Message khác null. Nếu mô hình yêu cầu payload bắt buộc có, điều kiện đó phải được kiểm tra riêng.

### Bài 8: Optional.empty

Một ArrayDeque<Optional<String>> có thành viên Optional.empty. PollFirst trả đối tượng gì? Nó khác tình trạng deque rỗng thế nào?

**Lời giải:** Nó trả một Optional khác null nhưng không có giá trị bên trong. Khi deque rỗng, pollFirst trả null ở cấp tham chiếu phần tử.

### Bài 9: tìm null có phải chèn null không?

ArrayDeque hiện chứa stable. Contains(null) và remove(null) trong chương trình làm gì?

**Lời giải:** Chúng trả false và không thay đổi nội dung. Đây là tìm/xóa theo giá trị, không phải cố chèn một thành viên null. Không suy hành vi của mọi tham số null chỉ từ quy tắc chèn.

### Bài 10: chuỗi “null”

Bạn thêm chuỗi `"null"` rồi kiểm tra phần tử lấy ra bằng `== null`. Kết quả là gì?

**Lời giải:** False. Có một đối tượng String chứa bốn ký tự. Chuỗi rỗng cũng là tham chiếu thật và được nhận, dù độ dài bằng 0.

### Bài 11: xóa và thu gom

Sau pollFirst, deque không còn giữ thành viên X. Biến bên ngoài vẫn trỏ đến X. Có thể nói X đã được thu gom ngay không?

**Lời giải:** Không. Deque đã bỏ tham chiếu của nó, nhưng biến bên ngoài vẫn giữ đối tượng. API không cam kết thời điểm thu gom bộ nhớ.

### Bài 12: lọc hay từ chối?

Nguồn có null nhưng mỗi vị trí đều biểu diễn một công việc phải được ghi nhận. Có nên tự bỏ mọi null rồi báo nhận đủ lô không?

**Lời giải:** Không nếu điều đó làm mất ý nghĩa dữ liệu. Cần từ chối hoặc dùng mô hình thông điệp tường minh theo hợp đồng. Bỏ qua là một chính sách có thể làm thay đổi số và ý nghĩa thành viên, không phải sửa lỗi trong suốt.

## 19. Cách áp dụng trong mã thật

Ở ranh giới đưa dữ liệu vào ArrayDeque, hãy kiểm tra tham chiếu phần tử và xác định chính sách nếu nguồn có thể chứa null. Với lô dữ liệu, quyết định rõ có được thêm một phần trước lỗi hay phải kiểm tra trước.

Nếu cần biểu diễn “có một thông điệp nhưng nội dung chưa có”, giữ một đối tượng phần tử khác null và mô tả sự vắng mặt bên trong bằng kiểu dữ liệu phù hợp.

Khi đọc deque, dùng null theo đúng ý nghĩa báo không có phần tử và phân biệt phép xem với phép lấy ra. Sự rõ ràng ở hai ranh giới chèn/đọc là lợi ích trực tiếp của hợp đồng ArrayDeque không lưu null.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="queue-deque-008">Xóa lần xuất hiện đầu hoặc cuối và duyệt ngược</a>

<details>
<summary>Xem chi tiết</summary>

# Xóa lần xuất hiện đầu hoặc cuối và duyệt ngược

## 1. Bài toán: trùng giá trị nhưng khác vị trí

Trong một hàng đợi hai đầu (`Deque`), cùng một giá trị có thể xuất hiện nhiều lần. Giả sử danh sách mã tác vụ đang có thứ tự từ đầu đến cuối như sau:

```text
first / head                             last / tail
     A       B       A       C       A       D
```

Yêu cầu “xóa A” vẫn còn thiếu một quyết định: xóa A gần đầu nhất, A gần cuối nhất, hay mọi A? Nếu cần kiểm tra các tác vụ từ phía cuối, chương trình phải đổi hướng đọc nhưng chưa chắc được phép lấy chúng ra khỏi deque.

Bài này tập trung vào ba công cụ:

- `removeFirstOccurrence(value)`: xóa một lần xuất hiện khớp gần đầu nhất.
- `removeLastOccurrence(value)`: xóa một lần xuất hiện khớp gần cuối nhất.
- `descendingIterator()`: duyệt từ cuối về đầu, có thể kết hợp xóa phần tử vừa duyệt khi iterator hỗ trợ thao tác đó.

Mục tiêu là biết chọn đúng phần tử theo **giá trị và vị trí hiện tại**, biết đọc ngược mà giữ nguyên dữ liệu, và biết xóa trong lúc duyệt mà không làm sai quy tắc của iterator.

Các ví dụ sử dụng API có trong Java 17. `ArrayDeque` là lớp cài đặt chính; một ví dụ riêng dùng `LinkedList` để giải thích trường hợp có `null`. Không cần Spring Boot, thư viện ngoài hoặc môi trường máy chủ.

## 2. “Đầu”, “cuối” và “lần xuất hiện” có nghĩa gì?

### 2.1. Đầu và cuối là vị trí trong deque lúc gọi phương thức

Quy ước của bài là viết deque theo chiều `first → last`, tương ứng với thứ tự của iterator thông thường. Với:

```text
[A, B, A, C, A, D]
```

phần tử đầu là A bên trái; phần tử cuối là D bên phải. “A đầu tiên” là A khớp đầu tiên khi đi từ trái sang phải. “A cuối cùng” là A khớp cuối cùng theo cùng thứ tự đó; cũng chính là A gặp đầu tiên khi đi từ phải sang trái.

Có thể tạm đánh dấu ba A để theo dõi:

```text
[A₁, B, A₂, C, A₃, D]
```

Các chỉ số nhỏ ở đây chỉ là nhãn minh họa cho vị trí; cả ba phần tử vẫn mang giá trị `"A"`. Xóa lần xuất hiện đầu sẽ xóa A₁; xóa lần xuất hiện cuối sẽ xóa A₃.

Không suy ra “đầu tiên” là được tạo sớm nhất hoặc “cuối cùng” là được tạo muộn nhất. Nếu chương trình thêm phần tử mới bằng `addFirst`, phần tử mới lại nằm trước phần tử cũ. Quan hệ với thời gian chỉ đúng khi cách thêm phần tử của ứng dụng duy trì quan hệ đó.

### 2.2. Tìm theo giá trị khác lấy phần tử tại một đầu

Với dữ liệu `[A, B, A, C, A, D]`:

| Lệnh | Giá trị trả về | Deque sau lệnh |
| --- | --- | --- |
| `removeLastOccurrence("A")` | `true` | `[A, B, A, C, D]` |
| `removeLast()` | `"D"` | `[A, B, A, C, A]` |
| `removeFirstOccurrence("C")` | `true` | `[A, B, A, A, D]` |

Mỗi dòng trên bắt đầu từ một bản dữ liệu ban đầu riêng. Lệnh occurrence tìm phần tử bằng giá trị nên có thể xóa ở giữa deque. Lệnh `removeLast()` không nhận giá trị cần tìm; nó lấy chính phần tử đang ở cuối.

Đừng “tự tìm” A cuối bằng cách gọi `pollLast()` liên tiếp rồi bỏ qua các giá trị khác. Cách đó còn lấy cả D, C hoặc các phần tử không phải mục tiêu ra khỏi deque, làm thay đổi nhiều hơn yêu cầu.

## 3. Hai phương thức xóa lần xuất hiện

Chữ ký rút gọn:

```java
boolean removeFirstOccurrence(Object target);
boolean removeLastOccurrence(Object target);
```

Hai phương thức trả kết quả có xóa được một phần tử hay không:

| Tình huống | Kết quả | Thay đổi dữ liệu |
| --- | --- | --- |
| Có một hoặc nhiều phần tử khớp | `true` | Xóa đúng một phần tử khớp ở phía đã chọn |
| Không có phần tử khớp | `false` | Giữ nguyên |
| Deque rỗng, tìm một giá trị hợp lệ | `false` | Vẫn rỗng |
| Chỉ có một phần tử và phần tử đó khớp | `true` | Trở thành rỗng |

Đây là xóa theo giá trị. `false` không có nghĩa deque rỗng: deque có thể chứa nhiều phần tử khác và không có mục tiêu. Ngược lại, `true` không có nghĩa đã xóa mọi bản sao.

Sau một lần xóa, các phần tử còn lại vẫn giữ thứ tự tương đối của chúng. Ví dụ, xóa A ở giữa `[B, A, C]` cho `[B, C]`; B không đổi thành phần tử đứng sau C.

Hợp đồng `Deque` mô tả phép khớp qua `Objects.equals(target, element)`; `remove(Object)` tương đương xóa lần xuất hiện đầu. Xem [tài liệu Deque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html#removeFirstOccurrence(java.lang.Object)).

### 3.1. Phải chọn đúng biến thể của remove

Có ba ý nghĩa dễ bị lẫn:

```java
// Đoạn lệnh minh họa; deque là một Deque<String> đã được tạo.
String head = deque.remove();                 // lấy/xóa phần tử đầu
boolean changed = deque.remove("A");           // xóa một A gần đầu nhất
boolean changedAtTail = deque.removeLastOccurrence("A");
```

`remove()` không có đối số trả một phần tử và báo `NoSuchElementException` nếu rỗng. `remove("A")` có đối số trả `boolean`, có thể tìm sâu vào giữa deque và trả `false` nếu không thấy A.

Với `Deque<Integer>`, lệnh `remove(1)` là xóa giá trị `Integer` 1 qua `remove(Object)`, không phải xóa chỉ số 1. Giao diện `Deque` không có `remove(int index)`.

Nếu khai báo biến bằng `LinkedList<Integer>` hoặc `List<Integer>`, tập phương thức được nhìn thấy khác: có cả `remove(int)`. Do đó phải đọc cả kiểu của biến và danh sách tham số, không suy luận từ đối tượng thực tế một cách riêng lẻ. Khi ý định là xóa theo giá trị trong deque, tên `removeFirstOccurrence` hoặc `removeLastOccurrence` giúp thể hiện ý định rõ ràng.

### 3.2. Một lần gọi chỉ giải quyết một lần xuất hiện

Với `[A, B, A]`, một lần `removeFirstOccurrence("A")` cho `[B, A]`. Nếu cần xóa tất cả A, yêu cầu đã khác. Có thể duyệt và xóa theo điều kiện; không nên đọc tên occurrence thành “xóa mọi phần tử bằng nhau”.

Lặp phương thức occurrence đến khi trả `false` có thể cho kết quả xóa hết, nhưng phải tìm lại sau mỗi lần gọi. Phần chi phí ở cuối bài giải thích vì sao cách đó có thể tốn nhiều công việc khi dữ liệu lớn.

## 4. Phép khớp: bằng giá trị, không nhất thiết cùng đối tượng

### 4.1. Không dùng == để dự đoán kết quả xóa đối tượng

Hai đối tượng khác nhau có thể bằng nhau theo `equals`. Trong chương trình chạy ở phần sau, hai giá trị:

```java
Token stored = new Token(7);
Token probe = new Token(7);
```

không có cùng tham chiếu nhưng bằng nhau, vì `Token` là `record Token(int id)`. Dùng `probe` làm đối số vẫn xóa được `stored` đang nằm trong deque.

Kết quả này phụ thuộc định nghĩa bằng nhau của kiểu phần tử. Lớp thông thường không tự có quy tắc “cùng mã là bằng nhau” chỉ vì có trường `id`. Nếu lớp chưa định nghĩa `equals` phù hợp, hai đối tượng được tạo riêng có thể không khớp dù in ra thông tin giống nhau.

Đối với record có nhiều thành phần, phép bằng mặc định xét tất cả thành phần. `record Job(int id, String state)` không mặc nhiên coi hai Job cùng `id` nhưng khác `state` là bằng nhau. Nếu yêu cầu nghiệp vụ là “xóa Job gần cuối nhất có mã 7, bất kể trạng thái”, nên dùng điều kiện `job.id() == 7` khi duyệt ngược.

### 4.2. Kiểu tham số Object không biến mọi giá trị giống nhau thành bằng nhau

Phương thức nhận `Object`, nên một số đối số sai kiểu vẫn biên dịch. Trong ví dụ:

```java
Deque<Integer> numbers = new ArrayDeque<>(List.of(10, 1, 20, 1));
boolean a = numbers.removeFirstOccurrence(1L); // Long, không khớp Integer 1
boolean b = numbers.remove(1);                // Integer 1, xóa lần đầu
```

`a` là `false`, `b` là `true`; còn lại `[10, 20, 1]`. Giá trị số viết giống nhau không đủ: `Integer.equals(Long)` không trả `true`.

Tương tự, tìm `"a"` không mặc định khớp `"A"`. Nếu cần bỏ qua hoa/thường hoặc so một trường riêng, dùng điều kiện do ứng dụng định nghĩa; không sửa kỳ vọng của occurrence thành so sánh gần đúng.

### 4.3. Không cần hashCode để chọn một lần xuất hiện trong deque

Phép xóa đang xét thứ tự và phép bằng của phần tử. Không nên suy luận cách tìm trong deque giống tìm một khóa trong bảng băm. Việc một lớp có `hashCode` giống nhau không có nghĩa các đối tượng bằng nhau; cũng không xác định phải xóa bản nào.

Một đối tượng có trường thay đổi được còn có thể thay đổi kết quả `equals` sau khi đã được thêm vào. Hãy xác định giá trị hiện tại nào cần khớp hoặc dùng một mã ổn định trong điều kiện tìm kiếm. Khi chương trình cần phân biệt hai tác vụ có cùng nội dung nhưng là hai lần gửi khác nhau, nên có mã lần gửi riêng thay vì chỉ dựa trên nội dung bằng nhau.

## 5. Chương trình chạy: xóa đầu, xóa cuối và so sánh giá trị

Lưu mã vào **DequeOccurrenceDemo.java**:

```java
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public class DequeOccurrenceDemo {
    record Token(int id) {}

    private static Deque<String> sample() {
        return new ArrayDeque<>(List.of("A", "B", "A", "C", "A", "D"));
    }

    public static void main(String[] args) {
        Deque<String> first = sample();
        System.out.println("initial: " + first);
        System.out.println("first A: " + first.removeFirstOccurrence("A"));
        System.out.println("after first A: " + first);

        Deque<String> last = sample();
        System.out.println("last A: " + last.removeLastOccurrence("A"));
        System.out.println("after last A: " + last);
        System.out.println("missing Z: " + last.removeLastOccurrence("Z"));
        System.out.println("after missing Z: " + last);

        Deque<String> alias = sample();
        System.out.println("remove A: " + alias.remove("A"));
        System.out.println("after remove A: " + alias);
        System.out.println("removeLast endpoint: " + alias.removeLast());
        System.out.println("after removeLast: " + alias);

        Deque<String> one = new ArrayDeque<>(List.of("A"));
        System.out.println("single A: " + one.removeLastOccurrence("A"));
        System.out.println("single now: " + one);
        System.out.println("empty first A: " + one.removeFirstOccurrence("A"));

        Token stored = new Token(7);
        Token probe = new Token(7);
        Deque<Token> tokens = new ArrayDeque<>(List.of(stored, new Token(8)));
        System.out.println("same reference: " + (stored == probe));
        System.out.println("equal value: " + stored.equals(probe));
        System.out.println("remove equal token: " + tokens.removeFirstOccurrence(probe));
        System.out.println("tokens left: " + tokens);

        Deque<Integer> numbers = new ArrayDeque<>(List.of(10, 1, 20, 1));
        System.out.println("remove 1L: " + numbers.removeFirstOccurrence(1L));
        System.out.println("remove 1: " + numbers.remove(1));
        System.out.println("numbers left: " + numbers);

        Deque<String> nullable = new LinkedList<>(Arrays.asList(null, "A", null, "B"));
        System.out.println("remove last null: " + nullable.removeLastOccurrence(null));
        System.out.println("nullable left: " + nullable);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 DequeOccurrenceDemo.java
java DequeOccurrenceDemo
```

Stdout chính xác:

```text
initial: [A, B, A, C, A, D]
first A: true
after first A: [B, A, C, A, D]
last A: true
after last A: [A, B, A, C, D]
missing Z: false
after missing Z: [A, B, A, C, D]
remove A: true
after remove A: [B, A, C, A, D]
removeLast endpoint: D
after removeLast: [B, A, C, A]
single A: true
single now: []
empty first A: false
same reference: false
equal value: true
remove equal token: true
tokens left: [Token[id=8]]
remove 1L: false
remove 1: true
numbers left: [10, 20, 1]
remove last null: true
nullable left: [null, A, B]
```

Điểm cần quan sát:

- Hai deque `first` và `last` được khởi tạo độc lập. Vì thế có thể so sánh trực tiếp tác động của xóa A gần đầu và xóa A gần cuối.
- Trong deque `last`, D vẫn ở cuối sau khi xóa A cuối. Phương thức đã tìm A nằm trước D, không lấy D ra.
- Tìm Z không thấy trả `false`, giữ nguyên dữ liệu.
- `remove("A")` chọn A gần đầu; sau đó `removeLast()` lấy D và trả chính D.
- Xóa một phần tử bằng một đối tượng record bằng giá trị vẫn thành công dù hai tham chiếu khác nhau.
- `LinkedList` trong ví dụ cuối chứa được `null` và xóa được lần xuất hiện cuối của `null`.

Các chương trình trong bài đã được biên dịch bằng `javac --release 17` và chạy trên OpenJDK 24.0.2. Cờ `--release 17` kiểm tra việc sử dụng ngôn ngữ/API mục tiêu Java 17 và sinh bytecode tương ứng; không có nghĩa JVM dùng để chạy đã trở thành Java 17.

## 6. Đọc kết quả theo biến đổi trạng thái

Một cách tự kiểm tra là tách ba câu: bắt đầu ở đâu, phần tử nào khớp trước theo hướng chọn, và phần còn lại giữ thứ tự gì.

Ví dụ độc lập:

```text
Ban đầu:                    [X, A, B, A, C]
removeLastOccurrence("A"):   tìm từ cuối gặp C, rồi gặp A
Kết quả trả về:             true
Sau lệnh:                   [X, A, B, C]
```

Không đảo deque trước khi tìm. Hướng tìm đủ để chọn đúng A. Cũng không dùng “phần tử lớn nhất” để giải thích lần xuất hiện cuối: cuối là vị trí, không phải thứ tự tăng/giảm của giá trị.

Với chuỗi thao tác liên tiếp, lần sau phải đọc trạng thái mới:

| Bước | Thao tác | Trả về | Trạng thái sau bước |
| --- | --- | --- | --- |
| 0 | Khởi tạo | — | `[A, B, A, C, A]` |
| 1 | `removeFirstOccurrence("A")` | `true` | `[B, A, C, A]` |
| 2 | `removeLastOccurrence("A")` | `true` | `[B, A, C]` |
| 3 | `removeLastOccurrence("A")` | `true` | `[B, C]` |
| 4 | `removeFirstOccurrence("A")` | `false` | `[B, C]` |

Ở bước 3, A duy nhất đồng thời là lần xuất hiện đầu và cuối. Bước 4 trả `false` nhưng deque vẫn có hai phần tử.

## 7. Duyệt ngược bằng descendingIterator

### 7.1. Đổi hướng quan sát, giữ nguyên thứ tự lưu trữ

`iterator()` đi từ `first` đến `last`; `descendingIterator()` đi từ `last` đến `first`. Đối chiếu [mô tả iterator của Deque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html#descendingIterator()).

Với `[A, B, A, C]`:

```text
iterator():            A → B → A → C
descendingIterator():  C → A → B → A
deque sau khi chỉ đọc: [A, B, A, C]
```

Tên “descending” ở đây là ngược thứ tự trong deque. Nó không sắp xếp giảm dần. Deque số `[3, 1, 4]` được đọc ngược thành `4, 1, 3`, không phải `4, 3, 1`.

### 7.2. Vòng lặp cơ bản

Đoạn lệnh sau dùng `deque` đã có sẵn và cần import `java.util.Iterator`:

```java
Iterator<String> iterator = deque.descendingIterator();
while (iterator.hasNext()) {
    String value = iterator.next();
    System.out.println(value);
}
```

`hasNext()` kiểm tra còn phần tử chưa đọc trong lượt duyệt này. `next()` trả phần tử tiếp theo theo hướng ngược và tiến iterator. Chỉ hai lệnh đó không xóa phần tử khỏi deque.

Khi không còn phần tử, `hasNext()` trả `false`; gọi tiếp `next()` báo `NoSuchElementException`. Đây khác với `pollLast()`, vốn lấy/xóa phần tử cuối và trả `null` khi rỗng.

Một iterator đã đi hết không tự quay lại đầu cho lượt tiếp theo. Muốn duyệt lại từ cuối, tạo `descendingIterator()` mới. Các vòng lặp thường không nên dùng `null` làm dấu dừng của `next()`: iterator có `hasNext()`, và một lớp cài đặt cho phép `null` có thể trả một phần tử `null` hợp lệ.

### 7.3. Cách viết ngắn dành cho chỉ đọc

Có thể dùng:

```java
// Đoạn lệnh minh họa, deque đã được tạo.
deque.descendingIterator().forEachRemaining(System.out::println);
```

Hành động ở đây chỉ in dữ liệu. Nếu cần điều kiện xóa và kiểm soát từng bước, dùng vòng `while` với iterator rõ ràng như phần tiếp theo.

`descendingIterator()` trả `Iterator<E>`, không trả `Iterable<E>`. Vì thế không thể đặt trực tiếp kết quả đó vào vế phải của enhanced for:

```java
// Ví dụ sai, không biên dịch:
for (String value : deque.descendingIterator()) {
    System.out.println(value);
}
```

Enhanced for trên `deque` vẫn dùng iterator thông thường và đi từ đầu đến cuối. Đổi tên biến vòng lặp hoặc tên biến deque không đổi hướng duyệt.

## 8. Xóa qua iterator: đúng phần tử vừa đọc

### 8.1. remove của iterator không nhận giá trị

Khi iterator hỗ trợ xóa:

```java
// Đoạn lệnh minh họa; deque là ArrayDeque<String> đã được tạo.
Iterator<String> iterator = deque.descendingIterator();
while (iterator.hasNext()) {
    String value = iterator.next();
    if ("A".equals(value)) {
        iterator.remove();
        break;
    }
}
```

Lượt duyệt bắt đầu từ cuối. Gặp A đầu tiên theo hướng ngược thì `iterator.remove()` xóa chính phần tử A vừa được `next()` trả về. `break` bảo đảm chỉ xóa một phần tử.

Với `[A, B, A, C]`, iterator đọc C rồi A bên phải. Kết quả là `[A, B, C]`. Lúc gọi `remove()`, C vẫn là phần tử cuối của deque. Vì thế “phần tử được iterator trả gần nhất” không có nghĩa “phần tử cuối của deque”.

Nếu chỉ cần so bằng một giá trị, `removeLastOccurrence("A")` ngắn gọn hơn vòng lặp này. Iterator hữu ích khi cần điều kiện phong phú hơn, cần thu thập thông tin trong lúc đọc, hoặc cần chọn nhiều phần tử.

### 8.2. Trạng thái hợp lệ của iterator.remove()

Theo [hợp đồng Iterator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove()), mỗi lần `next()` thành công cho phép tối đa một lần `remove()`; hỗ trợ xóa là khả năng tùy chọn của iterator.

```text
Tạo iterator
    ↓
next() thành công
    ↓
có thể remove() một lần, hoặc bỏ qua
    ↓
next() thành công tiếp theo
    ↓
có thể remove() một lần tiếp theo
```

Với iterator của `ArrayDeque` trong bài:

| Chuỗi thao tác | Kết quả |
| --- | --- |
| Tạo iterator → `remove()` | `IllegalStateException` |
| `next()` thành công → `remove()` | Xóa phần tử vừa được trả |
| `next()` thành công → `remove()` → `remove()` | Lần xóa thứ hai báo `IllegalStateException` |
| `next()` thành công → `hasNext()` → `remove()` | `hasNext()` không đọc phần tử mới; vẫn xóa phần tử của `next()` trước đó |
| Iterator rỗng → `next()` | `NoSuchElementException` |

Một lớp cài đặt khác có thể không hỗ trợ `Iterator.remove()` và báo `UnsupportedOperationException`. Không quy khả năng của iterator `ArrayDeque` cho mọi iterator trong Java.

## 9. Chương trình chạy: duyệt ngược và xóa trong lúc duyệt

Lưu thành **DequeReverseDemo.java**:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class DequeReverseDemo {
    private static <E> List<E> reverseValues(Deque<E> deque) {
        List<E> result = new ArrayList<>();
        Iterator<E> iterator = deque.descendingIterator();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    public static void main(String[] args) {
        Deque<String> deque = new ArrayDeque<>(List.of("A", "B", "A", "C"));
        System.out.println("forward: " + new ArrayList<>(deque));
        System.out.println("reverse: " + reverseValues(deque));
        System.out.println("after read: " + deque);

        Iterator<String> iterator = deque.descendingIterator();
        while (iterator.hasNext()) {
            String value = iterator.next();
            if ("A".equals(value)) {
                iterator.remove();
                break;
            }
        }
        System.out.println("after reverse remove one A: " + deque);
        System.out.println("reverse now: " + reverseValues(deque));

        Deque<Integer> numbers = new ArrayDeque<>(List.of(1, 2, 3, 4, 5, 6));
        Iterator<Integer> reverse = numbers.descendingIterator();
        while (reverse.hasNext()) {
            if (reverse.next() % 2 == 0) {
                reverse.remove();
            }
        }
        System.out.println("after reverse remove evens: " + numbers);

        Deque<String> empty = new ArrayDeque<>();
        Iterator<String> exhausted = empty.descendingIterator();
        System.out.println("empty hasNext: " + exhausted.hasNext());
        try {
            exhausted.next();
        } catch (NoSuchElementException ex) {
            System.out.println("empty next: " + ex.getClass().getSimpleName());
        }

        Iterator<String> state = deque.descendingIterator();
        try {
            state.remove();
        } catch (IllegalStateException ex) {
            System.out.println("remove before next: " + ex.getClass().getSimpleName());
        }
        System.out.println("next before remove: " + state.next());
        state.remove();
        try {
            state.remove();
        } catch (IllegalStateException ex) {
            System.out.println("remove twice: " + ex.getClass().getSimpleName());
        }
        System.out.println("after iterator state checks: " + deque);

        Deque<String> consumed = new ArrayDeque<>(List.of("A", "B", "C"));
        List<String> removed = new ArrayList<>();
        while (!consumed.isEmpty()) {
            removed.add(consumed.pollLast());
        }
        System.out.println("pollLast values: " + removed);
        System.out.println("after pollLast loop: " + consumed);
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 DequeReverseDemo.java
java DequeReverseDemo
```

Stdout chính xác:

```text
forward: [A, B, A, C]
reverse: [C, A, B, A]
after read: [A, B, A, C]
after reverse remove one A: [A, B, C]
reverse now: [C, B, A]
after reverse remove evens: [1, 3, 5]
empty hasNext: false
empty next: NoSuchElementException
remove before next: IllegalStateException
next before remove: C
remove twice: IllegalStateException
after iterator state checks: [A, B]
pollLast values: [C, B, A]
after pollLast loop: []
```

Hàm `reverseValues` tạo một `List` để dễ in và so sánh kết quả của hướng đọc. Hàm chỉ dùng `hasNext()` và `next()`, nên deque gốc giữ nguyên. Việc tạo danh sách là lựa chọn của ví dụ, không phải yêu cầu bắt buộc để duyệt ngược.

Đoạn xóa A dừng sau lần khớp đầu theo hướng ngược. Đoạn xóa số chẵn không có `break`, vì yêu cầu là xóa mọi số chẵn. Sau cùng, chỉ các số lẻ `[1, 3, 5]` còn lại theo thứ tự ban đầu.

Kiểm tra trạng thái iterator dùng một iterator mới trên `[A, B, C]`: gọi `remove()` quá sớm báo lỗi; `next()` trả C; xóa C thành công; xóa lại mà chưa gọi `next()` mới báo lỗi. Deque cuối cùng là `[A, B]`.

Vòng `pollLast()` ở cuối là đối chứng: cũng cho giá trị `C, B, A`, nhưng lấy hết các phần tử ra. Hai cách có thể in cùng thứ tự mà tạo trạng thái dữ liệu hoàn toàn khác nhau.

## 10. Áp dụng điều kiện: xóa phần tử phù hợp gần cuối nhất

### 10.1. Khi phép bằng của toàn đối tượng không diễn đạt yêu cầu

Giả sử deque giữ các tác vụ. Mỗi tác vụ có mã và trạng thái. Cần hủy một tác vụ gần cuối nhất có trạng thái chờ, chứ không cần xóa một đối tượng bằng toàn bộ một đối tượng mẫu.

Hàm sau là một **phương thức tiện ích đặt trong lớp của ứng dụng**, không phải chương trình độc lập. Cần import `Deque`, `Iterator` và `java.util.function.Predicate`:

```java
static <E> boolean removeLastMatching(
        Deque<E> deque, Predicate<? super E> condition) {
    Iterator<E> iterator = deque.descendingIterator();
    while (iterator.hasNext()) {
        E value = iterator.next();
        if (condition.test(value)) {
            iterator.remove();
            return true;
        }
    }
    return false;
}
```

Điều kiện sử dụng với deque chuỗi, không chứa `null`:

```java
// Đoạn lệnh minh họa trong một phương thức:
Deque<String> jobs = new ArrayDeque<>(
        List.of("WAIT-1", "RUN-1", "WAIT-2", "DONE-1"));
boolean removed = removeLastMatching(jobs, s -> s.startsWith("WAIT-"));
// removed == true
// jobs còn [WAIT-1, RUN-1, DONE-1]
```

Thứ tự kiểm tra là DONE-1, WAIT-2; gặp WAIT-2 thì xóa và trả về ngay. WAIT-1 vẫn tồn tại. Nếu bỏ lệnh kết thúc sớm và tiếp tục xóa, chương trình chuyển sang yêu cầu “xóa mọi tác vụ chờ”.

Hàm này giả định iterator hỗ trợ xóa, deque không bị sửa từ nơi khác trong lúc duyệt, và điều kiện không tự thêm/xóa phần tử khỏi deque. Với `ArrayDeque` sử dụng trong bài, hỗ trợ xóa qua iterator có sẵn. Điều kiện có thể ném ngoại lệ; khi cần xử lý lỗi nghiệp vụ, phải quyết định rõ có chấp nhận các thay đổi đã thực hiện trước lỗi hay không. Hàm xóa một phần tử ở trên chỉ gọi xóa sau khi điều kiện trả `true`.

### 10.2. Chọn hướng theo câu hỏi, chọn số lần xóa theo điều kiện dừng

| Yêu cầu | Hướng và cách kết thúc |
| --- | --- |
| Xóa một phần tử bằng mục tiêu, gần đầu nhất | `removeFirstOccurrence(target)` |
| Xóa một phần tử bằng mục tiêu, gần cuối nhất | `removeLastOccurrence(target)` |
| Xóa một phần tử thỏa điều kiện, gần đầu nhất | `iterator()`, xóa rồi dừng |
| Xóa một phần tử thỏa điều kiện, gần cuối nhất | `descendingIterator()`, xóa rồi dừng |
| Xóa mọi phần tử thỏa điều kiện | Duyệt và xóa từng phần tử khớp; không dừng sau một lần |
| Chỉ xem từ cuối về đầu | `descendingIterator()`, không gọi `remove()` |

Hướng của iterator quyết định phần tử nào được gặp trước. Lệnh dừng quyết định xóa một hay nhiều phần tử. Phương thức occurrence chỉ quyết định được theo phép bằng; nó không nhận một `Predicate`.

Nếu cần biết chính phần tử đã xóa thay vì chỉ một cờ `boolean`, vòng iterator cho phép giữ `value` trước khi xóa rồi trả hoặc xử lý nó theo hợp đồng của ứng dụng. Đừng cố ép kết quả `boolean` của `removeLastOccurrence` thành kiểu phần tử.

## 11. Ba thao tác “đọc ngược” có tác động khác nhau

### 11.1. Chỉ duyệt

Dùng `descendingIterator()` để ghi log, kiểm tra điều kiện hoặc tìm một phần tử gần cuối. Nếu chỉ đọc, không cần tạo một bản sao và không thay đổi thứ tự deque.

### 11.2. Tạo danh sách theo chiều ngược

Hàm `reverseValues` trong chương trình cho một danh sách mới. Thêm hoặc xóa phần tử trong danh sách mới không thêm/xóa phần tử của deque gốc.

Tuy nhiên, đó là sao chép các tham chiếu phần tử. Nếu phần tử là đối tượng thay đổi được, sửa chính đối tượng đó qua danh sách mới vẫn có thể được quan sát qua deque. “Danh sách riêng” không có nghĩa mọi đối tượng bên trong được sao chép sâu.

Danh sách kết quả cũng không tự theo dõi những lần thêm/xóa vào deque sau khi hàm kết thúc. Nếu cần một lần đọc nhất quán trong chương trình nhiều luồng, tạo danh sách riêng không tự biến quá trình đọc thành một thao tác nguyên tử.

### 11.3. Lấy hết từ cuối

`pollLast()` lặp đến hết dữ liệu là một phép tiêu thụ. Chỉ dùng khi yêu cầu cho phép deque bị rút cạn.

```java
// Đoạn lệnh minh họa: deque là ArrayDeque<String> do luồng hiện tại quản lý.
while (!deque.isEmpty()) {
    String value = deque.pollLast();
    System.out.println(value);
}
```

Với `[A, B, C]`, lệnh in C, B, A rồi deque rỗng. Nếu đây là danh sách công việc còn chờ xử lý, việc chỉ muốn “xem từ mới đến cũ” mà dùng đoạn này có thể làm mất toàn bộ hàng chờ.

Không dùng vòng kiểm tra rỗng rồi lấy phần tử này như một bảo đảm đồng bộ cho nhiều luồng. Điều kiện sử dụng một luồng trong chú thích là một phần của ví dụ.

## 12. Sửa dữ liệu khi đang duyệt

### 12.1. Dùng chính iterator đang duyệt để xóa

Ví dụ dễ sai:

```java
// Ví dụ sai về cách sửa khi iterator đang hoạt động:
Iterator<String> iterator = deque.descendingIterator();
while (iterator.hasNext()) {
    String value = iterator.next();
    if ("A".equals(value)) {
        deque.removeLastOccurrence(value);
    }
}
```

Có hai vấn đề khác nhau.

Thứ nhất, thao tác xóa thực hiện qua deque thay vì iterator đang theo dõi lượt duyệt. Không tiếp tục dựa vào iterator đó sau kiểu sửa ngoài này. Với `ArrayDeque`, cơ chế phát hiện sửa đổi trong khi duyệt thường báo `ConcurrentModificationException`, nhưng không bảo đảm mọi lần sai đều ném ngoại lệ.

Thứ hai, `removeLastOccurrence(value)` tìm lại theo giá trị. Nó không diễn đạt trực tiếp “xóa đúng đối tượng mà iterator vừa trả”. Khi có phần tử bằng nhau, logic tìm lại và logic vị trí iterator có thể không chọn cùng lần xuất hiện trong những biến thể vòng lặp khác.

Cách đúng trong phạm vi ví dụ là `iterator.remove()`. Tương tự, vòng enhanced for cũng có một iterator phía sau; gọi `deque.remove(...)` trong thân vòng không làm biến mất vấn đề.

### 12.2. Ngoại lệ phát hiện lỗi không phải cơ chế đồng bộ

`ArrayDeque` không an toàn cho truy cập đồng thời nếu không có đồng bộ phù hợp. Hành vi fail-fast phát hiện lỗi theo khả năng tốt nhất; không được dùng việc “có hoặc không có ConcurrentModificationException” để chứng minh chương trình nhiều luồng đúng. Lớp này hỗ trợ các thao tác tùy chọn của `Iterator`, bao gồm xóa. Xem [tài liệu ArrayDeque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html).

Trong ứng dụng một luồng, quy tắc thực hành đơn giản là: tạo iterator, đọc/xóa qua iterator đó, kết thúc lượt duyệt, rồi mới thực hiện những thay đổi khác. Nếu có tác vụ hoặc luồng khác có thể sửa deque cùng lúc, cần chọn lớp cài đặt và chính sách đồng bộ theo thiết kế ứng dụng; không suy ra chính sách của chúng từ ví dụ `ArrayDeque`.

Không đưa kết quả stdout cố định cho một chương trình cố tình sửa deque ngoài iterator rồi chờ ngoại lệ. Việc ngoại lệ xuất hiện ở bước nào không phải kết quả ổn định để học thuộc.

## 13. null, dữ liệu sai kiểu và chi phí tìm

### 13.1. null phụ thuộc lớp cài đặt

`ArrayDeque` không cho thêm phần tử `null`. `LinkedList` cho phép `null`; ví dụ chương trình đầu cho thấy xóa `null` cuối trong `[null, A, null, B]` để còn `[null, A, B]`. Đối chiếu [tài liệu LinkedList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html).

Không khái quát rằng mọi deque phải có cùng kết quả khi tìm/xóa mục tiêu `null`. Hợp đồng giao diện cho phép một số ngoại lệ tùy lớp cài đặt đối với đối số không được hỗ trợ. Tương tự, tham số `Object` không có nghĩa mọi lớp deque phải im lặng trả `false` trước mọi đối số sai kiểu.

Khi dùng một điều kiện trên deque có thể chứa `null`, phải xử lý nó theo nghiệp vụ:

```java
// Điều kiện minh họa dành cho deque có thể có null:
value -> value != null && value.startsWith("WAIT-")
```

Trong chương trình đầu, `Arrays.asList(null, "A", null, "B")` được dùng làm dữ liệu khởi tạo `LinkedList`. Thay nó bằng `List.of(null, ...)` sẽ lỗi ngay khi tạo danh sách, trước cả khi deque được tạo. Đây là một lỗi của bước chuẩn bị dữ liệu, không phải của phương thức occurrence.

### 13.2. Tìm phần tử ở giữa có thể phải đi qua nhiều phần tử

Với `ArrayDeque`, xóa theo giá trị như `removeFirstOccurrence` và `removeLastOccurrence` có chi phí tuyến tính trong trường hợp xấu, không có bảo đảm tìm một giá trị bất kỳ trong thời gian hằng số. Đổi hướng có thể làm gặp mục tiêu sớm hơn trên một dữ liệu cụ thể, nhưng không xóa được trường hợp phải tìm gần hết deque.

Chỉ đọc toàn bộ n phần tử bằng iterator cần n lần `next()`. Nếu chỉ xử lý lần lượt và không tích lũy kết quả, không cần tạo danh sách n phần tử phụ. Hàm `reverseValues` tạo danh sách nên dùng thêm bộ nhớ theo số phần tử trả về.

Đừng suy ra “duyệt một lượt rồi xóa bằng iterator luôn là O(n)”. Với `ArrayDeque`, một lần xóa ở giữa qua iterator có thể phải dịch chuyển phần tử. Nhiều lần xóa trong một lượt duyệt vẫn có thể làm tổng công việc lớn hơn tuyến tính. Tài liệu lớp liệt kê `iterator.remove()` trong nhóm thao tác tuyến tính; xem [đặc tính chi phí của ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html).

Vòng gọi occurrence lặp nhiều lần cũng có thể tìm lại và dịch chuyển lặp lại, dẫn đến chi phí bậc hai trong trường hợp xấu. Nếu yêu cầu là lọc toàn bộ dữ liệu lớn, hãy cân nhắc thao tác lọc phù hợp của lớp đang dùng hoặc tạo deque kết quả theo đúng thứ tự mong muốn. Không đổi cấu trúc dữ liệu chỉ vì một ví dụ nhỏ; xác định tần suất tìm/xóa và đo trên khối lượng đại diện khi hiệu năng thực sự là yêu cầu.

## 14. Lỗi phổ biến và cách tự chẩn đoán

| Hiện tượng | Nguyên nhân có thể | Cách sửa suy luận |
| --- | --- | --- |
| Xóa mất D khi muốn bỏ A gần cuối | Dùng `removeLast()` | Xóa theo giá trị bằng `removeLastOccurrence("A")` |
| Vẫn còn một A sau lệnh | Nghĩ occurrence xóa tất cả | Một lần gọi xóa tối đa một phần tử |
| Hai đối tượng nhìn giống nhau nhưng không khớp | Quy tắc `equals` không theo điều kiện cần tìm | Kiểm tra mô hình bằng nhau; dùng điều kiện theo trường khi phù hợp |
| Tìm 1 không thấy trong deque số nguyên | Đối số là `1L` hoặc kiểu khác | Kiểm tra kiểu của mục tiêu và kiểu phần tử |
| Duyệt “descending” không cho thứ tự giảm dần | Nhầm đảo hướng với sắp xếp | Viết thứ tự hiện tại rồi đọc từ phải sang trái |
| Muốn xem ngược nhưng deque rỗng sau đó | Dùng `pollLast()` lặp | Chỉ đọc bằng `descendingIterator()` |
| Xóa bằng iterator ngay lập tức bị lỗi | Chưa gọi `next()` | Phải đọc một phần tử thành công trước |
| Lần xóa thứ hai liên tiếp bị lỗi | Mỗi `next()` chỉ cho một lần `remove()` | Chỉ xóa một lần cho phần tử vừa đọc |
| Vừa duyệt vừa sửa bị lỗi hoặc kết quả khó dự đoán | Sửa deque ngoài iterator | Dùng iterator đang duyệt hoặc đổi thiết kế lượt xử lý |
| Gọi enhanced for với descendingIterator không biên dịch | Kết quả là `Iterator`, không phải `Iterable` | Dùng vòng `while (hasNext())` |

Một cách kiểm tra trên giấy là ghi riêng bốn thông tin: thứ tự deque trước lệnh, hướng đang đọc, phần tử vừa được iterator trả, và phần tử thực sự bị xóa. Không chỉ nhìn giá trị cuối cùng được in vì nhiều cách có thể in giống nhau nhưng làm thay đổi dữ liệu khác nhau.

## 15. Bài tập tự kiểm tra có lời giải

### Bài 1: Một chuỗi lệnh trên dữ liệu trùng

Bắt đầu bằng `[A, B, A, C, A]`, thực hiện liên tiếp:

```java
removeFirstOccurrence("A");
removeLastOccurrence("A");
removeLastOccurrence("Z");
```

Mỗi lệnh trả gì và deque cuối cùng là gì?

**Lời giải:** lệnh một trả `true`, còn `[B, A, C, A]`; lệnh hai trả `true`, còn `[B, A, C]`; lệnh ba trả `false`, không đổi. Deque cuối là `[B, A, C]`. Z không tồn tại không đồng nghĩa deque rỗng.

### Bài 2: Từ “cuối” trong ba thao tác

Deque là `[A, B, A, C]`. Iterator ngược gọi `next()` hai lần, rồi `remove()`. Phần tử nào bị xóa?

**Lời giải:** hai giá trị đọc được là C rồi A bên phải. `remove()` xóa A vừa đọc; deque thành `[A, B, C]`. C vẫn ở cuối. Nếu gọi `deque.removeLast()` thay thế sẽ xóa C và là một thao tác khác.

### Bài 3: Đọc ngược không phải sắp xếp

Deque số là `[3, 1, 4]`. Chỉ duyệt bằng `descendingIterator()` rồi in deque. Hai kết quả là gì?

**Lời giải:** lượt duyệt trả `4, 1, 3`; deque vẫn là `[3, 1, 4]`. Không có bước so sánh để sắp xếp và không có bước xóa.

### Bài 4: Một phần tử và trạng thái iterator

Deque là `[X]`. Iterator ngược gọi `hasNext()`, `next()`, `remove()`, rồi `hasNext()`. Điều gì xảy ra? Gọi thêm `remove()` thì sao?

**Lời giải:** lần kiểm tra đầu là `true`; `next()` trả X; xóa thành công khiến deque rỗng; lần kiểm tra sau là `false`. Gọi `remove()` lần nữa mà chưa có `next()` thành công mới sẽ báo `IllegalStateException` với iterator `ArrayDeque`.

### Bài 5: Xóa theo một trường

Có ba record `Job(7, "WAIT")`, `Job(8, "WAIT")`, `Job(7, "DONE")` theo thứ tự đó. Cần xóa Job có mã 7 gần cuối nhất, không quan tâm trạng thái. Dùng đối tượng mẫu `Job(7, "WAIT")` với `removeLastOccurrence` có đúng ý không?

**Lời giải:** không. Phép bằng record xét cả mã và trạng thái, nên mẫu đó khớp Job đầu và không khớp Job cuối. Duyệt ngược với điều kiện `job.id() == 7`, xóa qua iterator rồi dừng sẽ chọn Job cuối có trạng thái DONE. Nếu yêu cầu thực sự là “Job mã 7 đang WAIT gần cuối nhất”, điều kiện phải bao gồm cả trạng thái.

### Bài 6: Chọn công cụ theo tác động dữ liệu

Cần hiển thị nhật ký từ mục gần cuối về đầu và giữ nguyên nhật ký. Một người dùng vòng `pollLast()` rồi lưu các giá trị vào danh sách. Kết quả hiển thị đúng; còn lỗi nào?

**Lời giải:** nhật ký gốc bị lấy hết phần tử. Dùng iterator ngược chỉ đọc hoặc hàm tạo danh sách theo chiều ngược như `reverseValues`. Việc giao diện hiển thị đúng không chứng minh trạng thái dữ liệu sau thao tác đúng.

## 16. Tài liệu đối chiếu

- [Deque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html): đối chiếu tên phương thức, hướng iterator, kiểu trả về và ý nghĩa occurrence.
- [Iterator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html): quy tắc `next()`/`remove()` và khả năng xóa tùy chọn.
- [ArrayDeque Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html): các đặc tính của lớp được dùng trong ví dụ.
- [LinkedList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html): đối chiếu ví dụ chấp nhận `null`.

Khi áp dụng, hãy trả lời rõ ba câu trước khi viết lệnh: đang chọn theo giá trị hay điều kiện, ưu tiên phía đầu hay phía cuối, và muốn chỉ đọc hay thực sự xóa. Ba quyết định đó xác định đúng công cụ và giúp dự đoán trạng thái deque sau thao tác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="queue-deque-009">So sánh ArrayDeque, LinkedList và Stack</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh ArrayDeque, LinkedList và Stack

## 1. So sánh theo vai trò cần dùng

Cùng một dãy A, B, C có thể được quản lý như hàng đợi vào trước ra trước hoặc ngăn xếp vào sau ra trước. ArrayDeque, LinkedList và Stack đều có thể xuất hiện trong mã xử lý các dãy ấy, nhưng chúng không phải ba lớp thay thế hoàn toàn cho nhau.

Trong mã một luồng cần thao tác hàng đợi hoặc ngăn xếp không null, `Deque<E>` với `ArrayDeque<E>` thường là điểm bắt đầu phù hợp. LinkedList còn có vai trò List và khả năng cập nhật qua một con trỏ duyệt đã xác định vị trí. Stack là lớp cũ kế thừa Vector, thường gặp ở mã cần tương thích.

Quyết định nên dựa trên thao tác và hợp đồng:

| Khía cạnh | ArrayDeque | LinkedList | Stack |
|---|---|---|---|
| Cấu trúc chính | Mảng vòng có thể tăng | Danh sách liên kết đôi | Mảng có thể tăng của Vector |
| Giao diện vai trò nổi bật | Deque, Queue | List, Deque, Queue | List qua Vector; API Stack riêng |
| Dùng FIFO | Có | Có | Không triển khai Queue/Deque |
| Dùng LIFO | Có qua push/pop | Có qua push/pop | Có qua API Stack |
| Truy cập theo chỉ số | Không có API get(index) | Có, cần đi qua nút | Có qua Vector |
| Phần tử null | Không chấp nhận | Chấp nhận | Chấp nhận |
| Đồng bộ sẵn | Không | Không | Các thao tác nền tảng dùng đồng bộ của Vector/Stack |
| Đặc điểm cần chú ý | Tăng mảng có chi phí từng đợt | Mỗi phần tử có nút và liên kết | API cũ, có cả các thao tác List/Vector |

Các đặc điểm nền được đối chiếu với tài liệu Java 17 của [ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html), [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html), [Stack](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Stack.html) và [Vector](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Vector.html).

Bài này giải thích vì sao các khác biệt đó quan trọng, thay vì chỉ chọn một lớp bằng khẩu hiệu “mảng nhanh” hoặc “danh sách liên kết thêm/xóa O(1)”.

## 2. Cấu trúc lưu trữ tạo ra những đánh đổi nào?

### 2.1. ArrayDeque: các tham chiếu trong một mảng vòng

Mô hình:

```text
mảng nền:
[ C | D | . | . | A | B ]
                  ^
                 đầu logic

dãy logic: A, B, C, D
```

Vị trí đầu và cuối được đưa vòng qua biên mảng. Thêm/lấy ở đầu không cần dồn toàn bộ các phần tử còn lại về ô 0.

Nếu thiếu chỗ, cấu trúc tăng mảng và tổ chức lại tham chiếu. Vì vậy, có những lần thêm tốn nhiều công hơn các lần thông thường.

Các ô tham chiếu nằm trong mảng không có nghĩa toàn bộ đối tượng phần tử cũng được đặt liên tiếp trong bộ nhớ. Đối tượng có thể tồn tại độc lập; mảng giữ tham chiếu tới chúng.

### 2.2. LinkedList: mỗi phần tử ở một nút liên kết đôi

Mô hình:

```text
first
  |
  v
[prev | A | next] <-> [prev | B | next] <-> [prev | C | next]
                                                   ^
                                                   |
                                                  last
```

Nhờ giữ hai đầu, thêm hoặc gỡ first/last không phải tìm từ đầu qua toàn bộ danh sách. Nhưng để lấy phần tử ở một chỉ số bất kỳ, cần lần theo liên kết từ đầu gần hơn.

Mỗi phần tử còn có một nút chứa thông tin liên kết. Điều đó tạo chi phí cấp phát và bộ nhớ ngoài tham chiếu tới dữ liệu. Không nên nêu một số byte cố định cho mọi JVM nếu chưa xác định cấu hình và cách đo.

### 2.3. Stack: hành vi LIFO trên nền Vector

Stack kế thừa Vector. Đỉnh stack được đặt ở cuối dãy của Vector:

```text
index:       0    1    2
dữ liệu:     A    B    C
             ^         ^
            đáy       đỉnh
```

Push thêm vào cuối, pop gỡ ở cuối. Iterator thông thường đi theo thứ tự chỉ số từ 0 trở lên, nên đi từ đáy tới đỉnh.

Do kế thừa, Stack còn có các phương thức List/Vector như get theo chỉ số hoặc thêm vào giữa. Những khả năng ấy không cần thiết cho một giao diện ngăn xếp thuần và có thể làm ý định sử dụng kém rõ.

[Tài liệu Stack Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Stack.html) khuyến nghị dùng Deque và các triển khai của nó để có tập thao tác LIFO phù hợp hơn trong mã mới.

## 3. Độ phức tạp phải gắn với đúng thao tác

Gọi n là số phần tử hiện có. Bảng sau mô tả các thao tác thông dụng với giả định phép so sánh giá trị có chi phí hằng số:

| Thao tác | ArrayDeque | LinkedList | Stack |
|---|---|---|---|
| Thêm/lấy để làm LIFO | Thêm O(1) khấu hao; lấy đầu O(1) | O(1) ở đầu, có cấp/gỡ nút | Push O(1) khấu hao; pop cuối O(1), kèm cơ chế đồng bộ |
| Thêm cuối, lấy đầu để làm FIFO | O(1) khấu hao cho thao tác đầu/cuối | O(1) cho liên kết ở hai đầu | Không có API Queue; mô phỏng bằng remove(0) phải dịch dãy |
| Xem đầu hoặc đỉnh | O(1) | O(1) | O(1), theo API Stack |
| get(i) | Không hỗ trợ theo chỉ số | O(n) trong trường hợp tổng quát | O(1) qua Vector |
| Tìm hoặc xóa theo giá trị | O(n) trong trường hợp tổng quát | O(n) để tìm vị trí | O(n) trong trường hợp tổng quát |
| Chèn giữa khi chỉ có chỉ số | Không có API chèn theo chỉ số | Phải tìm vị trí, tổng quát O(n) | Dịch các phần tử phía sau, O(n) |
| Cập nhật tại ListIterator đã ở vị trí | Không có ListIterator | Thay đổi liên kết cục bộ có thể O(1) | Không phải lợi thế tương tự vì nền mảng |
| Duyệt toàn bộ | O(n) | O(n) | O(n) |

Các con số không có nghĩa ba cấu trúc có cùng thời gian thực tế khi cùng ghi O(1). Cấp phát nút, sao chép mảng, đi theo tham chiếu và đồng bộ đều có chi phí khác nhau.

### 3.1. Khấu hao khác với từng lần gọi

Với mảng có thể tăng, đa số lần thêm chỉ ghi vào vùng có sẵn. Thỉnh thoảng phải cấp phát và chuyển nhiều tham chiếu.

O(1) khấu hao nghĩa tổng chi phí có thể được phân bổ trên một chuỗi thao tác; một lần thêm đúng lúc tăng mảng vẫn có thể tốn O(n).

Nếu yêu cầu có giới hạn độ trễ chặt, chỉ nhìn cận khấu hao chưa đủ. Cần đánh giá quy mô, lịch sử sử dụng và cơ chế cấp phát trong môi trường thật.

### 3.2. LinkedList không có “xóa giữa miễn phí”

Nếu đã có một ListIterator đang đứng ở vị trí cần cập nhật, thay đổi các liên kết lân cận có thể rẻ.

Nhưng nếu đầu vào chỉ là chỉ số 5000 hoặc một giá trị cần tìm, LinkedList phải đi tới vị trí ấy trước. Chi phí tìm không biến mất chỉ vì bước nối/gỡ nút là O(1).

Đặc biệt, lặp theo chỉ số:

```java
// Trích đoạn dễ tốn nhiều lần đi lại trên LinkedList.
for (int i = 0; i < list.size(); i++) {
    use(list.get(i));
}
```

có thể tạo tổng công việc bậc hai trên LinkedList. Một vòng duyệt iterator hoặc for-each phù hợp với cách lưu trữ liên kết hơn. Đây là trích đoạn minh họa, không phải chương trình độc lập.

### 3.3. Bộ nhớ và tính cục bộ

ArrayDeque và Vector giữ các tham chiếu trong mảng, còn LinkedList phải đi qua các nút. Điều này ảnh hưởng tính cục bộ khi đọc và lượng cấp phát.

Ngược lại, mảng có thể có sức chứa dư sau một đỉnh tải và phải xử lý tăng dung lượng. LinkedList có chi phí nút trên từng phần tử.

Không thể suy ra một tỷ lệ nhanh hơn cố định từ bảng độ phức tạp. Nếu hiệu năng là yêu cầu thực tế, đo bằng kịch bản đại diện, kiểm tra cả lượng cấp phát và độ trễ, không chỉ một vòng chạy nhỏ.

## 4. Chương trình 1: cùng vai trò có thể cho cùng đầu ra

Lưu tệp `ThreeCollectionsRolesDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;

public class ThreeCollectionsRolesDemo {
    static List<String> drainQueue(Queue<String> queue) {
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            result.add(queue.remove());
        }
        return result;
    }

    static List<String> drainDequeStack(Deque<String> stack) {
        List<String> result = new ArrayList<>();
        while (!stack.isEmpty()) {
            result.add(stack.pop());
        }
        return result;
    }

    public static void main(String[] args) {
        List<String> input = List.of("A", "B", "C");

        Queue<String> arrayQueue = new ArrayDeque<>(input);
        Queue<String> linkedQueue = new LinkedList<>(input);
        System.out.println("ArrayDeque FIFO = " + drainQueue(arrayQueue));
        System.out.println("LinkedList FIFO = " + drainQueue(linkedQueue));

        Deque<String> arrayStack = new ArrayDeque<>();
        Deque<String> linkedStack = new LinkedList<>();
        Stack<String> legacyStack = new Stack<>();
        for (String item : input) {
            arrayStack.push(item);
            linkedStack.push(item);
            legacyStack.push(item);
        }

        System.out.println("ArrayDeque iteration = " + arrayStack);
        System.out.println("LinkedList iteration = " + linkedStack);
        System.out.println("Stack iteration = " + legacyStack);
        System.out.println("ArrayDeque LIFO = " + drainDequeStack(arrayStack));
        System.out.println("LinkedList LIFO = " + drainDequeStack(linkedStack));

        List<String> legacyOutput = new ArrayList<>();
        while (!legacyStack.empty()) {
            legacyOutput.add(legacyStack.pop());
        }
        System.out.println("Stack LIFO = " + legacyOutput);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 ThreeCollectionsRolesDemo.java
java ThreeCollectionsRolesDemo
```

Kết quả của lần chạy kiểm chứng:

```text
ArrayDeque FIFO = [A, B, C]
LinkedList FIFO = [A, B, C]
ArrayDeque iteration = [C, B, A]
LinkedList iteration = [C, B, A]
Stack iteration = [A, B, C]
ArrayDeque LIFO = [C, B, A]
LinkedList LIFO = [C, B, A]
Stack LIFO = [C, B, A]
```

Các chương trình trong bài dùng ngôn ngữ và API có ở Java 17 trở lên, được biên dịch với `--release 17` và chạy kiểm chứng bằng Azul OpenJDK 24.0.2.

### 4.1. ArrayDeque và LinkedList đều làm FIFO qua Queue

Hai đối tượng được tạo từ cùng dãy A, B, C. Khi xử lý bằng Queue.remove, cả hai cho A, B, C.

Điều đó cho thấy có thể chọn một giao diện để biểu đạt vai trò. Nó không có nghĩa null, bộ nhớ hoặc chi phí ở các thao tác khác đều giống nhau.

Các vòng trong chương trình là mã một luồng, nên kiểm tra rỗng rồi remove không có cập nhật chen vào. Không tự chuyển giả định đó sang nhiều luồng.

### 4.2. Cả ba có thể cho cùng thứ tự pop

Sau push A, B, C, cả ba lấy ra C, B, A khi dùng đúng API LIFO.

Nhưng thứ tự duyệt ban đầu khác:

```text
ArrayDeque dùng push: [C, B, A]  -> đỉnh ở first
LinkedList dùng push: [C, B, A]  -> đỉnh ở first
Stack:                [A, B, C]  -> đỉnh ở cuối Vector
```

Cùng thứ tự pop không đồng nghĩa cùng thứ tự iterator. Đây là khác biệt quan trọng khi hiển thị lịch sử, serialize dãy hoặc sao chép sang collection khác.

### 4.3. Chương trình này không phải benchmark

Các dòng in chỉ kiểm tra hợp đồng thứ tự. Không đo thời gian nên không dùng output để kết luận lớp nào nhanh hơn.

Ngay cả khi thêm một đồng hồ vào ví dụ nhỏ, kết quả còn chịu khởi động JVM, tối ưu hóa, cấp phát và công việc in ra. Muốn đánh giá hiệu năng phải thiết kế phép đo riêng.

## 5. API rỗng và null: cùng tên chưa chắc cùng hành vi

### 5.1. Peek trên Deque khác peek trên Stack cũ

| Lời gọi khi rỗng | ArrayDeque | LinkedList | Stack |
|---|---|---|---|
| `peek()` | null | null | EmptyStackException |
| `pop()` | NoSuchElementException | NoSuchElementException | EmptyStackException |
| Xem và muốn ngoại lệ kiểu Deque | `getFirst()` | `getFirst()` | API Stack.peek riêng |
| Lấy và muốn null kiểu Queue/Deque | `poll()` hoặc `pollFirst()` | `poll()` hoặc `pollFirst()` | Không có phương thức poll của Queue |

Vì vậy, đổi khai báo từ Stack sang Deque rồi giữ nguyên tên peek/pop vẫn có thể làm thay đổi nhánh xử lý rỗng hoặc loại ngoại lệ được bắt.

Nếu ngoại lệ là một phần hợp đồng của hàm công khai, cần quyết định có giữ tương thích bằng lớp bao quanh hay cập nhật hợp đồng một cách rõ ràng.

### 5.2. Null làm mất nghĩa đơn giản của poll/peek

ArrayDeque cấm phần tử null. Khi poll trả null, không có sự mơ hồ với một thành viên null thật.

LinkedList nhận null. Nếu phần tử đầu là null, poll có thể trả null trong khi vừa gỡ một phần tử thật và danh sách vẫn còn dữ liệu khác.

Stack cũng có thể giữ null. Peek trả null khi đỉnh là thành viên null thật, nhưng peek trên Stack rỗng ném ngoại lệ. Không dùng “peek khác null” để kiểm tra Stack có phần tử.

Nhận null ở collection không có nghĩa null là một mô hình dữ liệu tốt cho mọi hàng đợi. Nếu muốn các mẫu xử lý bằng poll đến null, nên xác lập dữ liệu thành viên không null.

### 5.3. Push có kiểu trả về khác nhau

- `Stack.push(e)` trả lại e.
- `Deque.push(e)` trả void.

Mã cũ dùng biểu thức như gán kết quả push hoặc truyền nó làm đối số sẽ không thể đổi lớp bằng cách thay tên kiểu đơn thuần.

Nếu cần giữ giá trị vừa đẩy, hãy giữ biến phần tử rồi push, thay vì dựa vào một giá trị trả về không có trong Deque.

## 6. Chương trình 2: đối chiếu rỗng và null

Lưu tệp `EmptyAndNullComparisonDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Stack;

public class EmptyAndNullComparisonDemo {
    static void reportFailure(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected a runtime exception");
        } catch (RuntimeException ex) {
            System.out.println(label + " = " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        Deque<String> array = new ArrayDeque<>();
        LinkedList<String> linked = new LinkedList<>();
        Stack<String> legacy = new Stack<>();

        System.out.println("empty ArrayDeque peek = " + array.peek());
        System.out.println("empty LinkedList peek = " + linked.peek());
        reportFailure("empty Stack peek", legacy::peek);
        reportFailure("empty ArrayDeque pop", array::pop);
        reportFailure("empty LinkedList pop", linked::pop);
        reportFailure("empty Stack pop", legacy::pop);
        reportFailure("ArrayDeque offer null", () -> array.offerLast(null));

        linked.offerLast(null);
        linked.offerLast("X");
        System.out.println("LinkedList size with null = " + linked.size());
        System.out.println("LinkedList empty with null = " + linked.isEmpty());
        System.out.println("LinkedList peek null member = " + linked.peek());
        System.out.println("LinkedList poll null member = " + linked.poll());
        System.out.println("LinkedList remaining = " + linked);

        System.out.println("Stack push null result = " + legacy.push(null));
        System.out.println("Stack empty with null = " + legacy.empty());
        System.out.println("Stack peek null member = " + legacy.peek());
        System.out.println("Stack pop null member = " + legacy.pop());
        System.out.println("Stack empty after pop = " + legacy.empty());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 EmptyAndNullComparisonDemo.java
java EmptyAndNullComparisonDemo
```

Kết quả của lần chạy kiểm chứng:

```text
empty ArrayDeque peek = null
empty LinkedList peek = null
empty Stack peek = EmptyStackException
empty ArrayDeque pop = NoSuchElementException
empty LinkedList pop = NoSuchElementException
empty Stack pop = EmptyStackException
ArrayDeque offer null = NullPointerException
LinkedList size with null = 2
LinkedList empty with null = false
LinkedList peek null member = null
LinkedList poll null member = null
LinkedList remaining = [X]
Stack push null result = null
Stack empty with null = false
Stack peek null member = null
Stack pop null member = null
Stack empty after pop = true
```

Hàm reportFailure làm ví dụ dừng bằng AssertionError nếu một thao tác không ném ngoại lệ như dự kiến. Các tên lớp ngoại lệ trong output là kết quả thực tế đã được chạy, không phải nhãn dự đoán.

### 6.1. ArrayDeque vẫn có thể ném lỗi ở offer

Offer có thể dùng boolean cho một số thất bại như thiếu chỗ ở một triển khai giới hạn dung lượng, nhưng không biến mọi đầu vào sai thành false.

ArrayDeque.offerLast(null) ném NullPointerException. Do đó, chính sách dữ liệu cần được kiểm tra riêng với chính sách rỗng hoặc đầy.

### 6.2. LinkedList còn X sau khi poll trả null

Danh sách có hai phần tử: null rồi X. Lời gọi poll gỡ thành viên đầu null và trả null; danh sách còn X.

Mẫu:

```java
// Không tổng quát nếu queue có thể chứa thành viên null.
String next;
while ((next = queue.poll()) != null) {
    process(next);
}
```

có thể dừng trước khi lấy hết dữ liệu nếu dùng một LinkedList chứa null. Trích đoạn này minh họa lỗi giả định, không phải chương trình độc lập.

Với mã một luồng cho phép null, có thể tách điều kiện rỗng khỏi giá trị lấy được và xử lý null có chủ đích. Với nhiều luồng, chuỗi kiểm tra rồi lấy còn có yêu cầu phối hợp riêng.

### 6.3. Stack giữ null mà không rỗng

Sau push null, empty trả false; peek và pop trả null là dữ liệu thật. Sau pop, empty mới trả true.

Điều này nhắc rằng thay Stack hoặc LinkedList có null bằng ArrayDeque cần một quyết định về dữ liệu, không chỉ sửa import và constructor.

Không âm thầm lọc null nếu mỗi lần xuất hiện có ý nghĩa nghiệp vụ. Có thể phải từ chối đầu vào, đổi kiểu biểu diễn hoặc chọn cấu trúc phù hợp.

## 7. Khi nào khả năng List của LinkedList có giá trị?

ArrayDeque chủ yếu phục vụ các thao tác hai đầu. LinkedList còn có List và ListIterator, cho phép một con trỏ duyệt đi qua dãy và cập nhật tại vị trí đang biết.

Ví dụ, một quy trình đang quét tuần tự các điểm trong hành trình, thỉnh thoảng chèn một điểm ngay trước vị trí tiếp theo hoặc gỡ điểm vừa đọc. Nếu con trỏ đã ở đó, sửa liên kết cục bộ không cần tìm lại vị trí theo chỉ số.

Cần giữ hai bước tách biệt:

```text
đi đến vị trí cần cập nhật
    +
cập nhật ở vị trí đã biết
```

Bước thứ hai có thể O(1) với nút liên kết; bước thứ nhất vẫn có chi phí di chuyển.

Không chọn LinkedList chỉ vì nghe câu “chèn giữa O(1)” nếu mỗi thao tác của ứng dụng lại bắt đầu bằng một chỉ số ngẫu nhiên và phải đi tìm từ đầu hoặc cuối.

### 7.1. Không phải thay thế List bằng Deque chỉ để đổi triển khai

Nếu một hàm dùng `List.get(i)`, `ListIterator.add` hoặc thao tác theo chỉ số, ArrayDeque không đáp ứng cùng giao diện.

Khi ấy, quyết định thiết kế không chỉ là thay `new LinkedList<>()` bằng `new ArrayDeque<>()`. Cần xem thuật toán có thật sự cần khả năng List hay có thể được diễn đạt lại bằng thao tác hai đầu.

Ngược lại, nếu mã chỉ dùng offer/poll hoặc push/pop, khai báo bằng Queue/Deque giúp biểu đạt vai trò rõ và cho phép chọn triển khai phù hợp hơn.

### 7.2. Cấp phát nút và vị trí đã biết

LinkedList phải cấp phát nút khi thêm. O(1) của số liên kết cần thay không có nghĩa công việc cấp phát, thu gom rác hoặc truy cập bộ nhớ là miễn phí.

Do đó, lợi ích của ListIterator cần được đánh giá trong thuật toán cụ thể: mức độ cập nhật cục bộ, cách duyệt và dữ liệu thực tế, thay vì chỉ nhìn một thao tác cô lập.

## 8. Chương trình 3: vị trí con trỏ và cập nhật cục bộ

Lưu tệp `LinkedListCursorComparisonDemo.java`:

```java
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class LinkedListCursorComparisonDemo {
    public static void main(String[] args) {
        LinkedList<String> route = new LinkedList<>(List.of("A", "C", "D"));
        ListIterator<String> cursor = route.listIterator(1);

        System.out.println("initial route = " + route);
        cursor.add("B");
        System.out.println("after cursor add B = " + route);
        System.out.println("cursor next = " + cursor.next());
        cursor.remove();
        System.out.println("after cursor remove = " + route);
        System.out.println("cursor previous = " + cursor.previous());
        cursor.set("B2");
        System.out.println("after cursor set = " + route);
        System.out.println("indexed get 1 = " + route.get(1));

        route.addFirst("START");
        route.addLast("END");
        System.out.println("with both ends = " + route);
        System.out.println("remove first = " + route.removeFirst());
        System.out.println("remove last = " + route.removeLast());
        System.out.println("final route = " + route);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 LinkedListCursorComparisonDemo.java
java LinkedListCursorComparisonDemo
```

Kết quả của lần chạy kiểm chứng:

```text
initial route = [A, C, D]
after cursor add B = [A, B, C, D]
cursor next = C
after cursor remove = [A, B, D]
cursor previous = B
after cursor set = [A, B2, D]
indexed get 1 = B2
with both ends = [START, A, B2, D, END]
remove first = START
remove last = END
final route = [A, B2, D]
```

### 8.1. Chi phí tìm nằm ở lúc tạo hoặc di chuyển con trỏ

`listIterator(1)` xác định vị trí trước phần tử C. Với dữ liệu nhỏ, chi phí này không nổi bật; với dữ liệu lớn ở giữa dãy, cần nhớ rằng việc tìm vị trí vẫn tồn tại.

Sau đó, cursor.add chèn B tại vị trí con trỏ. Cursor.next đọc C; cursor.remove gỡ đúng phần tử vừa đọc. Không có một lời gọi tìm C lại theo giá trị.

Chương trình không đo thời gian và không tự chứng minh một tỷ lệ hiệu năng. Nó giúp chỉ ra điều kiện “đã có vị trí” nghĩa là gì trong API.

### 8.2. Iterator có quy tắc trạng thái

Remove của ListIterator liên quan tới phần tử vừa được next hoặc previous trả về. Nó không phải “xóa một phần tử bất kỳ” mà không cần biết trạng thái duyệt.

Set thay giá trị phần tử vừa đọc; add chèn tại vị trí con trỏ. Khi xây thuật toán cập nhật, cần giữ đúng hợp đồng các thao tác ấy.

Sau khi chương trình thay đổi hai đầu trực tiếp qua route, nó không dùng lại cursor cũ. Không nên tiếp tục dùng một iterator sau các thay đổi cấu trúc qua đường khác rồi mặc định trạng thái vẫn hợp lệ.

### 8.3. Cùng một LinkedList có hai vai trò API

Route còn có addFirst/addLast/removeFirst/removeLast, nên cũng dùng được như Deque ở hai đầu.

Điều này thể hiện sự linh hoạt, nhưng không có nghĩa ứng dụng nên trộn tùy tiện truy cập giữa dãy với một ngăn xếp cần bất biến chỉ thay đổi ở đỉnh. Chọn giao diện và quyền sở hữu phù hợp vẫn quan trọng.

## 9. Đồng bộ của Stack bảo vệ tới đâu?

Stack kế thừa Vector, một lớp có cơ chế đồng bộ cho các thao tác nền tảng. Điều đó khác với ArrayDeque và LinkedList không tự đồng bộ.

Nhưng cần phân biệt một thao tác và một chuỗi thao tác.

Giả sử stack đang có đúng một phần tử:

```text
Luồng 1: gọi empty() -> false
Luồng 2: gọi pop()   -> lấy phần tử cuối
Luồng 1: gọi pop()   -> EmptyStackException
```

Từng lời gọi vẫn có thể thực hiện đúng cơ chế đồng bộ của lớp. Lỗi giả định nằm ở khoảng giữa hai lời gọi của luồng 1: trạng thái đã thay đổi.

### 9.1. “Có synchronized” không phải mọi quy trình đều nguyên tử

Những chuỗi như kiểm tra-rồi-lấy, duyệt-rồi-cập nhật hoặc sao chép nhiều bước cần một ranh giới phối hợp phù hợp cho cả yêu cầu.

Trong mã cũ bắt buộc giữ Stack, có thể cần đồng bộ toàn bộ chuỗi liên quan theo cùng cơ chế mà mọi bên tham gia tuân thủ. Trong thiết kế mới, nên xem lại mô hình xử lý và API đồng thời cần thiết.

Không dùng Stack chỉ vì nó có đồng bộ rồi coi các yêu cầu về chờ dữ liệu, giới hạn số lượng, xử lý thất bại hoặc phối hợp nhiều bên đã được đáp ứng.

### 9.2. Iterator không phải bản chụp an toàn đồng thời

Các iterator của các lớp trong bài không tự cung cấp một ảnh chụp ổn định cho mọi thay đổi đồng thời. Fail-fast có thể giúp phát hiện sửa đổi, nhưng không bảo đảm một thuật toán dùng chung dữ liệu đã đúng.

Đừng dùng việc “không thấy ConcurrentModificationException trong vài lần chạy” làm bằng chứng an toàn nhiều luồng.

Tương tự, một Stack có những phương thức đồng bộ không đồng nghĩa vòng for-each trên nó tự giữ một khóa bao trùm toàn bộ vòng lặp.

### 9.3. Chọn vai trò đồng thời trước khi chọn lớp

Nếu dữ liệu chỉ thuộc một luồng, không nên chọn Stack chỉ để có một cơ chế đồng bộ mà thuật toán không cần.

Nếu dữ liệu được chia sẻ, hãy xác định các thao tác cần nguyên tử, có cần chờ khi rỗng/đầy không và chính sách giới hạn. Khi đó, so sánh ba lớp này mới là một phần của quyết định; chưa phải toàn bộ thiết kế.

Không bỏ đồng bộ khỏi một hệ thống chỉ vì ArrayDeque thường phù hợp với FIFO/LIFO đơn luồng. Điều kiện sử dụng là một phần của lựa chọn.

## 10. Chuyển mã Stack sang Deque cần kiểm tra những gì?

Một khai báo LIFO mới thường là:

```java
// Trích đoạn; cần import java.util.Deque và ArrayDeque.
Deque<String> history = new ArrayDeque<>();
```

Push thêm ở first; pop lấy first. Tuy nhiên, mã Stack cũ có thể phụ thuộc nhiều hơn hai thao tác đó.

| Điều mã cũ có thể phụ thuộc | Điều cần xem lại |
|---|---|
| Pop trên rỗng ném EmptyStackException | Deque.pop ném NoSuchElementException |
| Peek trên rỗng ném ngoại lệ | Deque.peek trả null; getFirst có ngoại lệ kiểu Deque |
| Stack.push trả lại phần tử | Deque.push trả void |
| Stack.empty | Deque dùng isEmpty |
| Stack.search trả độ sâu từ đỉnh | Deque không có API search tương đương trực tiếp |
| Iterator đi từ đáy lên đỉnh | Deque dùng push đi từ đỉnh xuống |
| Phần tử null hợp lệ | ArrayDeque từ chối null |
| Các thao tác Vector/List kế thừa | ArrayDeque không có truy cập/chèn theo chỉ số |
| Hành vi đồng bộ | ArrayDeque không tự giữ cơ chế ấy |
| Equals theo List | ArrayDeque không so sánh nội dung dãy bằng equals như List |

Không nên chuyển một kiểu công khai mà nhiều bên đang dùng bằng một phép thay văn bản đơn thuần. Trước hết cần xác định những hành vi nào thuộc hợp đồng thực sự.

### 10.1. Sao chép đúng dãy iterator chưa chắc đúng đỉnh stack

Stack push A, B, C sẽ duyệt ra A, B, C. Constructor ArrayDeque nhận collection giữ thứ tự iterator của nguồn, nên dãy mới là A, B, C từ first tới last.

Nếu sau đó dùng Deque.pop, giá trị lấy đầu là A, trong khi legacy Stack.pop phải lấy C.

Vì vậy:

```java
// Trích đoạn: không giữ cùng đỉnh nếu sau đó dùng Deque.push/pop.
Deque<String> copy = new ArrayDeque<>(legacyStack);
```

có thể sao chép đúng thứ tự duyệt nhưng sai ngữ nghĩa stack mong muốn.

### 10.2. Giữ quy ước đỉnh ở first

Với nguồn một luồng, không null và không bị thay đổi trong lúc sao chép, có thể duyệt từ đáy lên đỉnh rồi push từng phần tử vào Deque mới.

Mỗi phần tử mới trở thành first, nên đỉnh cuối cùng đúng là phần tử ở đỉnh Stack cũ.

Nếu nguồn có null, phải quyết định cách xử lý trước; đừng để một bản chuyển đổi dở dang được coi là thành công. Nếu nguồn được chia sẻ giữa nhiều luồng, cần lấy dữ liệu theo một ranh giới nhất quán phù hợp.

### 10.3. Một lựa chọn khác là đỉnh ở last

Có thể giữ thứ tự iterator A, B, C trong Deque mới nhưng dùng addLast/removeLast/getLast làm thao tác stack.

Cách đó cũng hợp lệ nếu toàn bộ mã dùng nhất quán cùng đầu. Nhưng không được vừa sao chép theo quy ước đỉnh last rồi lại dùng các tên push/pop vốn đặt đỉnh ở first.

Lựa chọn cần được thể hiện trong giao diện bao quanh hoặc quy tắc sử dụng, tránh để mỗi phần mã tự đoán.

## 11. Chương trình 4: giữ đúng phần tử sẽ được pop tiếp theo

Lưu tệp `StackToDequeMigrationDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Stack;

public class StackToDequeMigrationDemo {
    public static void main(String[] args) {
        Stack<String> legacy = new Stack<>();
        legacy.push("A");
        legacy.push("B");
        legacy.push("C");

        Deque<String> iteratorOrderCopy = new ArrayDeque<>(legacy);
        Deque<String> topPreservingCopy = new ArrayDeque<>();
        for (String item : legacy) {
            topPreservingCopy.push(item);
        }

        System.out.println("legacy bottom to top = " + legacy);
        System.out.println("legacy top = " + legacy.peek());
        System.out.println("legacy search B = " + legacy.search("B"));
        System.out.println("constructor copy first = "
                + iteratorOrderCopy.peekFirst());
        System.out.println("top-preserving copy = " + topPreservingCopy);
        System.out.println("top-preserving peek = " + topPreservingCopy.peek());

        List<String> output = new ArrayList<>();
        while (!topPreservingCopy.isEmpty()) {
            output.add(topPreservingCopy.pop());
        }
        System.out.println("migrated pop order = " + output);
        System.out.println("legacy remains = " + legacy);
        System.out.println("legacy top remains = " + legacy.peek());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 StackToDequeMigrationDemo.java
java StackToDequeMigrationDemo
```

Kết quả của lần chạy kiểm chứng:

```text
legacy bottom to top = [A, B, C]
legacy top = C
legacy search B = 2
constructor copy first = A
top-preserving copy = [C, B, A]
top-preserving peek = C
migrated pop order = [C, B, A]
legacy remains = [A, B, C]
legacy top remains = C
```

### 11.1. Constructor copy và top-preserving copy khác nhau

Constructor copy có first là A vì nó đi theo iterator của Stack. Bản top-preserving dùng push trong lúc duyệt, nên first là C.

Kết quả migrated pop order là C, B, A, đúng ngữ nghĩa LIFO cần giữ.

Stack gốc vẫn là A, B, C và đỉnh vẫn là C. Chương trình đã tạo container riêng chứ không tiêu thụ Stack cũ.

### 11.2. Search không phải contains

Stack.search(B) trả 2, nghĩa B ở vị trí thứ hai tính từ đỉnh theo quy ước 1-based của Stack. Deque.contains(B) chỉ trả một boolean, không thay thế ý nghĩa độ sâu đó.

Nếu ứng dụng thật sự cần vị trí từ đỉnh, phải xác định lại cách biểu đạt và chi phí tìm. Không thay search bằng contains chỉ để mã biên dịch được.

### 11.3. Sao chép container không sao chép sâu dữ liệu

Ví dụ dùng String bất biến, nên dễ quan sát sự tách riêng của container. Với đối tượng khả biến, cả hai container vẫn có thể giữ tham chiếu tới cùng đối tượng.

Việc chuyển lớp lưu trữ không tự giải quyết quyền sở hữu và vòng đời của từng phần tử.

## 12. Chọn lớp theo một yêu cầu cụ thể

### 12.1. Chỉ cần FIFO hoặc LIFO trong một luồng

Có thể bắt đầu bằng:

```java
// Trích đoạn biểu đạt vai trò.
Queue<Job> queue = new ArrayDeque<>();
Deque<Change> history = new ArrayDeque<>();
```

Job và Change là các kiểu nghiệp vụ trong chương trình bao quanh. ArrayDeque phù hợp khi dữ liệu không null và thao tác chính ở hai đầu.

Không cần chọn LinkedList chỉ vì tên “queue thường dùng linked list” nếu mã không tận dụng khả năng List hoặc con trỏ cập nhật.

### 12.2. Cần List cùng với thay đổi tại con trỏ đã có

LinkedList có thể có giá trị khi thuật toán đã duyệt tuần tự bằng ListIterator và cần chèn/gỡ cục bộ quanh vị trí ấy.

Nhưng phải tính cả chi phí cấp phát nút, đi qua liên kết và cách tiếp cận vị trí. Nếu chủ yếu truy cập ngẫu nhiên theo chỉ số, LinkedList thường không có lợi thế từ cấu trúc của nó.

Việc cần List không tự buộc chọn LinkedList; đó là dấu hiệu bài toán cần một so sánh theo thao tác List rộng hơn, thay vì chỉ so sánh hàng đợi/ngăn xếp.

### 12.3. Đang tích hợp với mã yêu cầu Stack

Nếu một API cũ yêu cầu đúng Stack hoặc dựa vào các phương thức kế thừa/ngoại lệ/đồng bộ của nó, giữ tương thích có thể là một ràng buộc thực tế.

Nên cô lập ràng buộc ấy ở ranh giới rõ và kiểm tra ngữ nghĩa trước khi chuyển. Khuyến nghị Deque cho mã LIFO mới không có nghĩa mọi mã Stack cũ đều phải đổi ngay trong một lần sửa không liên quan.

### 12.4. Cần hỗ trợ null

Trước hết hỏi null biểu đạt điều gì và có cần giữ như một phần tử thật không.

LinkedList hoặc Stack có thể nhận null, nhưng điều đó thay đổi cách đọc peek/poll và mẫu kết thúc vòng lặp. Nếu có thể biểu đạt trạng thái thiếu dữ liệu bằng một kiểu rõ hơn và giữ phần tử không null, việc dùng Queue/Deque thường dễ hiểu hơn.

Không chọn một collection chỉ để tránh một NullPointerException mà chưa quyết định ngữ nghĩa dữ liệu.

## 13. Bộ nhớ, độ trễ và các số đo cần đọc cẩn thận

Ba lớp có đánh đổi khác nhau, không phải một thứ hạng tuyệt đối cho mọi tải.

- ArrayDeque có mảng dự phòng và các lần tăng mảng; không có một nút liên kết riêng cho mỗi phần tử.
- LinkedList có nút và liên kết theo từng phần tử; thao tác hai đầu không phải tăng một mảng chung.
- Stack dựa trên Vector, có sức chứa dự phòng và chi phí đồng bộ liên quan đến cách dùng.

Sau một đỉnh tải, mảng có thể vẫn giữ sức chứa lớn dù số phần tử giảm. Điều đó cần được tách khỏi việc các tham chiếu phần tử đã được gỡ.

Một kết luận về bộ nhớ nên nói rõ đo số ô dự phòng, số đối tượng nút, lượng cấp phát hay tổng dữ liệu nghiệp vụ. Không chỉ nhìn size rồi mặc định các lớp dùng cùng bộ nhớ.

### 13.1. Cùng O(1) không nghĩa cùng độ trễ

Cấp phát, thu gom rác, đi theo tham chiếu, vùng nhớ sẵn có và khóa đồng bộ có thể tạo khác biệt thực tế.

Mặt khác, JVM có thể tối ưu một số trường hợp sử dụng khác nhau. Không dự đoán một tỷ lệ tốc độ cố định từ số từ khóa synchronized hoặc từ một sơ đồ mảng.

Nếu cần đo, dùng tập thao tác đúng với ứng dụng: thêm/lấy xen kẽ, độ sâu hoặc độ dài chờ đại diện, tải cấp phát và mức chia sẻ giữa các luồng.

### 13.2. Đừng đo những thao tác không cùng nghĩa

So sánh ArrayDeque.removeFirst với LinkedList.remove(Object) là so sánh hai yêu cầu khác nhau: một bên lấy đầu, một bên còn phải tìm giá trị.

Tương tự, so sánh Stack.pop với Deque.peek không cho biết chi phí hai cách triển khai stack, vì peek không gỡ dữ liệu.

Một phép đo chỉ hữu ích khi hợp đồng, dữ liệu và lượng công việc thực hiện tương ứng với nhau.

## 14. Những lỗi so sánh và chuyển đổi thường gặp

| Nhận định hoặc cách làm | Điều bị bỏ sót | Cách sửa |
|---|---|---|
| LinkedList luôn tốt hơn vì thêm/xóa O(1) | Có thể phải tìm vị trí và cấp phát nút | Tính toàn bộ đường đi tới cập nhật |
| ArrayDeque luôn nhanh nhất trong mọi tình huống | Không có bảo đảm tuyệt đối cho mọi tải | Chọn theo hợp đồng rồi đo khi cần |
| Dùng Stack cho FIFO vì có add/remove | API kế thừa không phải Queue; lấy đầu mảng có thể dịch dãy | Dùng vai trò Queue phù hợp |
| Stack có đồng bộ nên mọi chuỗi thao tác đều an toàn | Kiểm tra và cập nhật riêng có thể bị chen vào | Xác lập ranh giới nguyên tử cần thiết |
| LinkedList nhận null nên poll đến null luôn lấy hết | Null có thể là thành viên đầu thật | Cấm null hoặc xử lý tồn tại riêng |
| Chỉ thay Stack thành Deque rồi giữ catch EmptyStackException | Ngoại lệ rỗng đã đổi | Xem lại hợp đồng và nhánh xử lý |
| Copy Stack bằng constructor rồi pop ở first | Iterator Stack đi từ đáy lên | Giữ đúng quy ước đỉnh |
| Dùng kết quả push của Deque như Stack | Deque.push trả void | Giữ biến giá trị trước khi push |
| Thay LinkedList bằng ArrayDeque nhưng vẫn cần get(i) | ArrayDeque không là List | Xem lại giao diện và thuật toán |
| Dùng equals để đối chiếu hai ArrayDeque như List | ArrayDeque không có equals theo dãy | So dãy quan sát được bằng cách phù hợp |
| Chỉ so size sau chạy | Bỏ qua thứ tự và số lần xuất hiện cụ thể | Đối chiếu toàn bộ đầu ra |
| Bắt đầu từ một bài đo vài mili giây rồi chọn lớp | Kịch bản và khởi động JVM có thể chi phối | Dùng phép đo đại diện có phương pháp |

Khi đọc một lời khuyên chọn collection, hãy tìm điều kiện đi cùng: một luồng hay nhiều luồng, dữ liệu có null hay không, cần chỉ số hay chỉ cần hai đầu, và chi phí tìm vị trí đã được tính chưa.

## 15. Kiểm tra trước khi thay một lớp trong mã có sẵn

Một danh sách kiểm tra ngắn nhưng có tính quyết định:

1. Ghi rõ thứ tự lấy ra mong muốn: FIFO hay LIFO.
2. Kiểm tra ít nhất ba phần tử khác nhau và các bước thêm/lấy xen kẽ.
3. Kiểm tra rỗng với đúng loại phương thức và ngoại lệ.
4. Kiểm tra dữ liệu null theo hợp đồng hiện có.
5. Kiểm tra thứ tự iterator nếu dùng để hiển thị hoặc sao chép.
6. Kiểm tra các phương thức List/Vector/Stack đặc thù mà mã gọi đang dùng.
7. Kiểm tra chia sẻ tham chiếu, đồng bộ và toàn bộ chuỗi cập nhật.
8. Kiểm tra hành vi equals nếu lớp mới có ngữ nghĩa khác.

Các bài kiểm tra nên bảo vệ yêu cầu của ứng dụng, không chỉ xác nhận tên lớp mới hoặc một đoạn triển khai.

Nếu cần so thứ tự dữ liệu, thu các phần tử được lấy ra vào List như các chương trình trong bài. Hai lớp có cùng tập phần tử nhưng khác thứ tự vẫn có thể tạo ra hành vi nghiệp vụ khác.

Không dùng một Set để xác nhận thay thế stack/queue: Set có thể che mất thứ tự và số lần xuất hiện.

## 16. Bài tập áp dụng

### Bài 1: hàng đợi công việc đơn luồng

Chỉ cần thêm cuối, lấy đầu, không nhận null và không truy cập chỉ số. Trong ba lớp, nên cân nhắc lớp nào đầu tiên?

**Gợi ý trả lời:** ArrayDeque qua giao diện Queue hoặc Deque là lựa chọn phù hợp để bắt đầu. Vẫn cần xem quy mô, giới hạn và yêu cầu thực tế.

### Bài 2: xóa giữa LinkedList

Một hàm nhận chỉ số i và gọi remove(i). Có thể kết luận O(1) chỉ vì LinkedList dùng liên kết không?

**Gợi ý trả lời:** không. Phải đi tới vị trí i; chi phí tổng quát vẫn O(n). Cập nhật cục bộ tại con trỏ đã có là trường hợp khác.

### Bài 3: cùng pop, khác iterator

Push A, B, C vào Stack và vào ArrayDeque. Cả hai pop ra C trước. Iterator có cùng thứ tự không?

**Gợi ý trả lời:** không. Stack duyệt A, B, C theo thứ tự Vector; ArrayDeque dùng push duyệt C, B, A từ first tới last.

### Bài 4: đổi peek

Mã cũ bắt EmptyStackException quanh Stack.peek. Nếu đổi sang Deque.peek, trường hợp rỗng diễn ra thế nào?

**Gợi ý trả lời:** Deque.peek trả null. Cần xem lại nhánh xử lý hoặc chọn phương thức/adapter giữ hợp đồng mong muốn.

### Bài 5: Stack có một phần tử

Hai luồng cùng chạy kiểm tra không rỗng rồi pop. Đồng bộ từng phương thức có bảo đảm cả hai pop đều thành công không?

**Gợi ý trả lời:** không. Một luồng có thể lấy phần tử giữa hai lời gọi của luồng kia. Chuỗi kiểm tra-rồi-lấy cần cơ chế phù hợp cho toàn yêu cầu.

### Bài 6: chuyển dữ liệu có null

Một LinkedList đang giữ null như một phần tử thật. Có thể thay bằng ArrayDeque mà không quyết định lại dữ liệu không?

**Gợi ý trả lời:** không. ArrayDeque cấm null; phải xác định từ chối, biểu diễn lại hoặc giữ cấu trúc có hợp đồng phù hợp.

### Bài 7: constructor copy của Stack

Stack đang có A ở đáy và C ở đỉnh. Tạo ArrayDeque bằng constructor nhận Stack rồi gọi pop.

Giá trị nào có thể được lấy và vì sao?

**Gợi ý trả lời:** A, vì constructor giữ thứ tự iterator A, B, C và Deque.pop lấy first. Muốn giữ đỉnh C cần chuyển theo quy ước đỉnh rõ ràng.

### Bài 8: chọn vì có get(i)

Stack có get(i) O(1), nên có phải lựa chọn mặc định cho mọi danh sách truy cập ngẫu nhiên không?

**Gợi ý trả lời:** không. Khả năng kế thừa không thay mục đích của cấu trúc. Khi nhu cầu là List, cần so sánh các triển khai List theo thao tác thật, không chọn Stack vì có sẵn một phương thức.

Một lựa chọn đúng cần giữ được cả ý nghĩa thứ tự, dữ liệu hợp lệ và ranh giới phối hợp. ArrayDeque, LinkedList và Stack chỉ nên được so sánh sau khi các yêu cầu ấy đã được nêu rõ.


</details>

- [Quay lại đầu trang](#back-to-top)
