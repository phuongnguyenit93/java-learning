# Các mô hình dữ liệu cốt lõi — Queue và Deque

`List` và `Set` chủ yếu mô tả dữ liệu đang được giữ. `Queue` và `Deque` nhấn mạnh thêm **thứ tự xử lý**: phần tử nào được lấy ra tiếp theo, và bên gọi được phép thao tác ở đầu nào.

Ví dụ, một danh sách đơn hàng chờ xử lý không nhất thiết cần chỉ số. Điều quan trọng hơn là đơn nào vào hàng trước, đơn nào được lấy ra trước, hoặc có cần đẩy một đơn khẩn cấp lên đầu hay không.

## <a id="queue-semantics">Ngữ nghĩa của Queue</a>

`Queue<E>` mô tả cấu trúc mà thao tác chính là **đưa phần tử vào** và **lấy phần tử ở đầu hàng đợi ra để xử lý**. Với Queue FIFO thông thường, phần tử vào trước được lấy ra trước:

```text
offer A → [A]
offer B → [A, B]
offer C → [A, B, C]
poll    → A
poll    → B
```

```java
Queue<Order> pending = new ArrayDeque<>();
pending.offer(orderA);
pending.offer(orderB);
pending.offer(orderC);

Order next = pending.poll(); // orderA
```

FIFO là mô hình phổ biến, nhưng `Queue` không bắt buộc mọi cách triển khai phải dùng thứ tự chèn. `PriorityQueue` là ví dụ quan trọng: phần tử ở đầu được quyết định bởi thứ tự ưu tiên.

Vì vậy, khi một API nhận `Queue<Order>`, điều bên gọi có thể tin là có các thao tác queue như `offer`, `poll`, `peek`; thứ tự cụ thể còn phụ thuộc quy ước của cách triển khai.

Với Queue xử lý thông thường, `ArrayDeque` thường là cách triển khai phù hợp: không có dung lượng cố định theo API thông thường, thao tác hai đầu hiệu quả, và không cho phép `null`.

Không nên dùng `null` làm phần tử của Queue ngay cả với cách triển khai có thể chấp nhận nó, vì `poll` và `peek` dùng `null` làm giá trị đặc biệt khi Queue rỗng. Tách “dữ liệu thật” khỏi “không có phần tử” làm mã nguồn rõ ràng hơn.

## <a id="deque-semantics">Ngữ nghĩa của Deque</a>

`Deque<E>` là **hàng đợi hai đầu (double-ended queue)**: thêm, đọc và xóa được ở cả đầu và cuối.

```text
             Deque
     first ← [A][B][C] → last
             ↑       ↑
        thao tác   thao tác
        đầu trái   đầu phải
```

```java
Deque<Order> pending = new ArrayDeque<>();

pending.offerLast(normalOrder);
pending.offerFirst(urgentOrder);

Order first = pending.pollFirst();
Order last = pending.pollLast();
```

Từ một quy ước này ta có thể biểu diễn hai mẫu phổ biến:

### Queue FIFO

```java
Deque<Order> queue = new ArrayDeque<>();
queue.offerLast(orderA);
queue.offerLast(orderB);

Order next = queue.pollFirst();
```

### Stack LIFO

```java
Deque<Order> stack = new ArrayDeque<>();
stack.push(orderA);
stack.push(orderB);

Order latest = stack.pop(); // orderB
```

Với mã nguồn mới, `Deque`/`ArrayDeque` thường được ưu tiên hơn `Stack` kiểu cũ vì quy ước hai đầu rõ ràng và không mang mô hình kế thừa cũ từ `Vector`.

Từ Java 21, `Deque` cũng là một `SequencedCollection`. Điều đó phản ánh đúng bản chất có đầu/cuối xác định và cung cấp nhóm thao tác thống nhất như đầu/cuối/đảo thứ tự bên cạnh các phương thức chuyên biệt của `Deque`.

Không nên kết luận rằng mọi thao tác của mọi `Deque` đều có cùng chi phí. `ArrayDeque` dùng cấu trúc mảng vòng có khả năng tăng dung lượng; `LinkedList` dùng nút liên kết. Chọn cách triển khai dựa vào kiểu sử dụng và đặc tính dữ liệu, không chỉ dựa vào cùng một interface.

## <a id="exception-vs-special-value">Ngoại lệ và giá trị đặc biệt</a>

`Queue` có hai nhóm phương thức cho cùng loại thao tác. Khác biệt nằm ở cách báo “không thể thực hiện”:

| Ý định | Ném ngoại lệ khi thất bại | Trả giá trị đặc biệt |
| --- | --- | --- |
| thêm | `add(e)` | `offer(e)` |
| lấy và xóa phần tử đầu | `remove()` | `poll()` |
| đọc phần tử đầu, không xóa | `element()` | `peek()` |

Với queue rỗng:

```java
Queue<Order> queue = new ArrayDeque<>();

Order a = queue.poll(); // null
Order b = queue.peek(); // null

// queue.remove();  // NoSuchElementException
// queue.element(); // NoSuchElementException
```

Với Queue có giới hạn dung lượng, `add(e)` có thể ném `IllegalStateException` nếu hiện tại không thể thêm phần tử, trong khi `offer(e)` trả `false`. Các cách triển khai như `ArrayDeque` thường không có kiểu thất bại do giới hạn dung lượng này trong sử dụng thông thường, nhưng cặp API vẫn giữ cùng quy ước chung.

Chọn cặp nào phụ thuộc vào ý nghĩa của trạng thái rỗng/đầy:

- nếu “không có phần tử” là một trạng thái bình thường, `poll/peek` thường tiện hơn;
- nếu nó biểu thị vi phạm bất biến và bên gọi muốn lỗi rõ ngay, `remove/element` có thể phù hợp hơn;
- khi thêm vào Queue có thể hợp lệ nhưng tạm thời bị từ chối, `offer` diễn đạt thất bại bằng giá trị trả về.

`Deque` có các cặp tương tự cho từng đầu, ví dụ `addFirst/offerFirst`, `removeFirst/pollFirst` và `getFirst/peekFirst`.

## <a id="priority-queue">PriorityQueue</a>

`PriorityQueue` vẫn thực hiện `Queue`, nhưng “phần tử tiếp theo” được quyết định bởi **độ ưu tiên thứ tự** thay vì thời điểm chèn.

```java
record Job(String name, int priority) {}

Queue<Job> jobs = new PriorityQueue<>(
        Comparator.comparingInt(Job::priority)
);

jobs.offer(new Job("normal", 50));
jobs.offer(new Job("urgent", 10));
jobs.offer(new Job("low", 90));

System.out.println(jobs.poll().name()); // urgent
```

Với comparator trên, độ ưu tiên nhỏ hơn đứng ở đầu Queue. Nếu không truyền comparator, phần tử phải có thứ tự tự nhiên phù hợp.

Mô hình tư duy triển khai là một **heap ưu tiên**:

```text
không duy trì toàn bộ dữ liệu theo thứ tự tuyến tính
        ↓
duy trì bất biến đủ để phần tử đầu luôn là phần tử ưu tiên nhất
        ↓
offer / poll điều chỉnh heap
```

Trong cách triển khai chuẩn, `offer` và `poll` có chi phí O(log n), còn xem phần tử đầu bằng `peek` là O(1).

Một bẫy quan trọng: **việc duyệt `PriorityQueue` không đảm bảo đi theo thứ tự ưu tiên**.

```java
for (Job job : jobs) {
    // Không được giả định thứ tự ở đây giống thứ tự poll().
}
```

Nếu cần lấy theo độ ưu tiên, hãy gọi `poll` lặp lại trên queue phù hợp hoặc tạo một bản sao khi cần giữ queue gốc:

```java
Queue<Job> copy = new PriorityQueue<>(jobs);
while (!copy.isEmpty()) {
    System.out.println(copy.poll());
}
```

Các phần tử có độ ưu tiên bằng nhau cũng không có cơ chế phân xử FIFO tự động. Nếu thứ tự khi bằng nhau quan trọng, comparator cần thêm tiêu chí, ví dụ số thứ tự.
