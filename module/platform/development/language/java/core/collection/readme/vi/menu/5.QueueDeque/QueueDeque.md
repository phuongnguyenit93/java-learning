# Queue và Deque

`List` và `Set` chủ yếu mô tả dữ liệu đang được giữ. `Queue` và `Deque` nhấn mạnh thêm **thứ tự xử lý**: phần tử nào được lấy ra tiếp theo, và caller được phép thao tác ở đầu nào.

Ví dụ, một danh sách đơn hàng chờ xử lý không nhất thiết cần index. Điều quan trọng hơn là đơn nào vào hàng trước, đơn nào được lấy ra trước, hoặc có cần đẩy một đơn khẩn cấp lên đầu hay không.

## <a id="queue-semantics">Ngữ nghĩa của Queue</a>

`Queue<E>` mô tả cấu trúc mà thao tác chính là **đưa phần tử vào** và **lấy/phần tử ở head ra để xử lý**. Với queue FIFO thông thường, phần tử vào trước được lấy ra trước:

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

FIFO là model phổ biến, nhưng `Queue` không bắt buộc mọi implementation phải dùng insertion order. `PriorityQueue` là ví dụ quan trọng: head được quyết định bởi priority ordering.

Vì vậy, khi một API nhận `Queue<Order>`, điều caller có thể tin là có các thao tác queue như `offer`, `poll`, `peek`; thứ tự cụ thể còn phụ thuộc contract của implementation.

Với queue xử lý thông thường, `ArrayDeque` thường là implementation phù hợp: không có capacity cố định theo API thông thường, thao tác hai đầu hiệu quả, và không cho phép `null`.

Không nên dùng `null` làm element của queue ngay cả với implementation có thể chấp nhận nó, vì `poll` và `peek` dùng `null` làm special value khi queue rỗng. Tách “dữ liệu thật” khỏi “không có phần tử” làm code rõ ràng hơn.

## <a id="deque-semantics">Ngữ nghĩa của Deque</a>

`Deque<E>` là **double-ended queue**: thêm, đọc và xóa được ở cả đầu và cuối.

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

Từ một contract này ta có thể biểu diễn hai pattern phổ biến:

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

Với code mới, `Deque`/`ArrayDeque` thường được ưu tiên hơn legacy `Stack` vì contract hai đầu rõ ràng và không mang mô hình kế thừa cũ từ `Vector`.

Từ Java 21, `Deque` cũng là một `SequencedCollection`. Điều đó phản ánh đúng bản chất có đầu/cuối xác định và cung cấp vocabulary thống nhất như first/last/reversed bên cạnh các method chuyên biệt của `Deque`.

Không nên kết luận rằng mọi thao tác của mọi `Deque` đều có cùng chi phí. `ArrayDeque` dùng cấu trúc mảng vòng có khả năng tăng dung lượng; `LinkedList` dùng node liên kết. Chọn implementation dựa vào workload và đặc tính dữ liệu, không chỉ dựa vào cùng một interface.

## <a id="exception-vs-special-value">Exception và special value</a>

`Queue` có hai nhóm method cho cùng loại thao tác. Khác biệt nằm ở cách báo “không thể thực hiện”:

| Ý định | Ném exception khi thất bại | Trả special value |
| --- | --- | --- |
| thêm | `add(e)` | `offer(e)` |
| lấy và xóa head | `remove()` | `poll()` |
| đọc head, không xóa | `element()` | `peek()` |

Với queue rỗng:

```java
Queue<Order> queue = new ArrayDeque<>();

Order a = queue.poll(); // null
Order b = queue.peek(); // null

// queue.remove();  // NoSuchElementException
// queue.element(); // NoSuchElementException
```

Với queue có giới hạn capacity, `add(e)` có thể ném `IllegalStateException` nếu hiện tại không thể thêm phần tử, trong khi `offer(e)` trả `false`. Các implementation như `ArrayDeque` thường không có bounded-capacity failure kiểu này trong sử dụng thông thường, nhưng pair API vẫn giữ cùng contract chung.

Chọn cặp nào phụ thuộc vào ý nghĩa của trạng thái rỗng/đầy:

- nếu “không có phần tử” là một trạng thái bình thường, `poll/peek` thường tiện hơn;
- nếu nó biểu thị vi phạm invariant và caller muốn failure rõ ngay, `remove/element` có thể phù hợp hơn;
- khi thêm vào queue có thể hợp lệ nhưng tạm thời bị từ chối, `offer` diễn đạt failure bằng return value.

`Deque` có các cặp tương tự cho từng đầu, ví dụ `addFirst/offerFirst`, `removeFirst/pollFirst` và `getFirst/peekFirst`.

## <a id="priority-queue">PriorityQueue</a>

`PriorityQueue` vẫn thực hiện `Queue`, nhưng “phần tử tiếp theo” được quyết định bởi **priority ordering** thay vì thời điểm chèn.

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

Với comparator trên, priority nhỏ hơn đứng ở head. Nếu không truyền comparator, element phải có natural ordering phù hợp.

Mental model triển khai là một **priority heap**:

```text
không duy trì toàn bộ dữ liệu theo thứ tự tuyến tính
        ↓
duy trì invariant đủ để head luôn là phần tử ưu tiên nhất
        ↓
offer / poll điều chỉnh heap
```

Trong implementation chuẩn, `offer` và `poll` có chi phí O(log n), còn xem head bằng `peek` là O(1).

Một bẫy quan trọng: **iteration của `PriorityQueue` không đảm bảo đi theo priority order**.

```java
for (Job job : jobs) {
    // Không được giả định thứ tự ở đây giống thứ tự poll().
}
```

Nếu cần lấy theo priority, hãy gọi `poll` lặp lại trên queue phù hợp hoặc tạo một bản sao khi cần giữ queue gốc:

```java
Queue<Job> copy = new PriorityQueue<>(jobs);
while (!copy.isEmpty()) {
    System.out.println(copy.poll());
}
```

Các phần tử có priority bằng nhau cũng không có FIFO tie-break tự động. Nếu tie order quan trọng, comparator cần thêm tiêu chí, ví dụ sequence number.
