# Duyệt Collection

Sau khi chọn được cấu trúc dữ liệu phù hợp, phần lớn thuật toán vẫn cần đi qua các phần tử. Nếu mỗi implementation bắt caller biết cách mảng, node, tree hay hash bucket được tổ chức bên trong thì code sẽ bị gắn chặt với implementation. Java tách **giao thức duyệt** khỏi **cấu trúc lưu trữ** thông qua `Iterable`, `Iterator`, `ListIterator` và `Spliterator`.

## <a id="iterator-contract">Hợp đồng Iterator</a>

`Iterator<E>` đại diện cho một cursor logic đi qua một sequence phần tử:

```java
Iterator<Order> iterator = orders.iterator();

while (iterator.hasNext()) {
    Order order = iterator.next();
    System.out.println(order.id());
}
```

Ba thao tác cốt lõi:

- `hasNext()` hỏi còn phần tử tiếp theo hay không;
- `next()` trả phần tử tiếp theo và tiến cursor;
- `remove()` là thao tác xóa **optional**, chỉ có khi iterator cụ thể hỗ trợ.

Nếu gọi `next()` khi không còn phần tử, iterator ném `NoSuchElementException`. Caller không nên dùng exception như điều kiện vòng lặp bình thường; pattern chuẩn là kiểm tra `hasNext()` trước.

`Iterator` không hứa mọi cấu trúc có cùng order. Order của iterator đến từ collection:

- `ArrayList` duyệt theo list order;
- `LinkedHashSet` duyệt theo encounter order của nó;
- `TreeSet` duyệt theo sorted order;
- `HashSet` không hứa một encounter order cụ thể;
- iterator của `PriorityQueue` không hứa priority-sorted order.

Điều này cho phép thuật toán viết theo interface mà vẫn tôn trọng semantics của nguồn:

```java
void printOrders(Iterable<Order> source) {
    Iterator<Order> it = source.iterator();
    while (it.hasNext()) {
        System.out.println(it.next());
    }
}
```

Iterator thường là stateful và dùng cho một lần traversal tiến về phía trước. Muốn duyệt lại, thường cần lấy iterator mới từ collection.

Việc sửa trực tiếp cấu trúc collection trong lúc đang dùng iterator có thể làm invalid assumption của iterator và dẫn tới fail-fast behavior ở nhiều collection thông thường. Chapter Fail-Fast sẽ đi sâu vào `ConcurrentModificationException`; ở đây chỉ cần biết rằng mutation trong lúc duyệt phải theo đúng contract của iterator/collection.

## <a id="enhanced-for">Enhanced for</a>

Vòng `for-each` là syntax giúp code sử dụng traversal protocol mà không phải tự quản lý iterator.

```java
for (Order order : orders) {
    System.out.println(order.id());
}
```

Với một object thực hiện `Iterable<Order>`, có thể hình dung compiler chuyển logic thành dạng gần như:

```java
for (Iterator<Order> it = orders.iterator(); it.hasNext(); ) {
    Order order = it.next();
    System.out.println(order.id());
}
```

Đây là mental model, không phải yêu cầu rằng bytecode phải có đúng hình thức trên. Điểm quan trọng là enhanced for phụ thuộc vào iteration contract thay vì vào `ArrayList`, `HashSet` hay một implementation cụ thể.

Array cũng dùng được enhanced for:

```java
Order[] array = {orderA, orderB};
for (Order order : array) {
    System.out.println(order.id());
}
```

Array là trường hợp riêng của language; nó không implement `Iterable` và không tạo `Iterator` theo contract Collection.

Enhanced for phù hợp khi chỉ cần đọc/ xử lý từng phần tử. Nó không cung cấp trực tiếp:

- index hiện tại;
- API `Iterator.remove()`;
- điều khiển hai iterator song song;
- khả năng dừng/chia traversal theo `Spliterator`.

Khi thuật toán cần những khả năng đó, dùng API traversal trực tiếp sẽ rõ hơn.

## <a id="iterator-remove">Xóa an toàn qua Iterator</a>

Một lỗi phổ biến là xóa trực tiếp từ collection trong enhanced for:

```java
for (Order order : orders) {
    if (order.totalCents() == 0) {
        orders.remove(order); // có thể làm iterator đang dùng bị invalid
    }
}
```

Với iterator hỗ trợ remove, cách đúng là xóa thông qua chính iterator đó:

```java
Iterator<Order> it = orders.iterator();

while (it.hasNext()) {
    Order order = it.next();
    if (order.totalCents() == 0) {
        it.remove();
    }
}
```

`Iterator.remove()` xóa phần tử cuối cùng vừa được `next()` trả về. Contract có hai giới hạn quan trọng:

1. `remove()` là optional operation; iterator của unmodifiable collection hoặc implementation không hỗ trợ có thể ném `UnsupportedOperationException`;
2. không được gọi `remove()` trước `next()` hoặc gọi hai lần liên tiếp cho cùng một `next()`; trường hợp đó ném `IllegalStateException`.

Ví dụ:

```java
Iterator<Order> it = orders.iterator();

Order first = it.next();
it.remove(); // hợp lệ nếu iterator hỗ trợ

// it.remove(); // IllegalStateException: chưa có next() mới
```

Khi chỉ cần lọc một mutable collection theo predicate, `removeIf` thường biểu đạt ý định gọn hơn:

```java
orders.removeIf(order -> order.totalCents() == 0);
```

Nhưng hiểu `Iterator.remove()` vẫn quan trọng vì nó giải thích mutation được phối hợp với traversal như thế nào. Các chi tiết fail-fast và structural modification thuộc chapter riêng sau đó.

## <a id="listiterator-contract">ListIterator: duyệt hai chiều và chỉnh sửa theo vị trí</a>

`ListIterator<E>` mở rộng ý tưởng của `Iterator` riêng cho `List`. Ngoài đi tới trước, nó có thể đi lùi, biết index lân cận và cung cấp mutation operation gắn với vị trí traversal.

```java
List<Order> orders = new ArrayList<>(List.of(orderA, orderB, orderC));
ListIterator<Order> it = orders.listIterator();

while (it.hasNext()) {
    int index = it.nextIndex();
    Order order = it.next();
    System.out.println(index + " -> " + order.id());
}

while (it.hasPrevious()) {
    Order order = it.previous();
    System.out.println(order.id());
}
```

Các operation đặc trưng:

| Operation | Ý nghĩa |
| --- | --- |
| `hasPrevious()` / `previous()` | duyệt ngược |
| `nextIndex()` / `previousIndex()` | quan sát vị trí cursor |
| `set(e)` | thay phần tử vừa được `next/previous` trả về |
| `add(e)` | chèn tại vị trí cursor |
| `remove()` | xóa phần tử vừa được traversal trả về |

Ví dụ normalize một order trong khi duyệt:

```java
ListIterator<Order> it = orders.listIterator();
while (it.hasNext()) {
    Order current = it.next();
    if (current.totalCents() < 0) {
        it.set(new Order(current.id(), current.userId(), 0));
    }
}
```

`ListIterator` hữu ích khi thuật toán thật sự cần **vị trí + traversal hai chiều + mutation phối hợp với traversal**. Nếu chỉ cần đọc từng phần tử, enhanced `for` đơn giản hơn; nếu chỉ cần xóa theo predicate, `removeIf` thường rõ hơn. `add/set/remove` vẫn là optional operation nếu list phía dưới không hỗ trợ mutation.

## <a id="spliterator-boundary">Ranh giới Spliterator</a>

`Spliterator<E>` có thể xem là traversal abstraction mở rộng từ ý tưởng iterator. Nó hỗ trợ hai khả năng:

1. duyệt phần tử;
2. thử chia nguồn thành các phần để xử lý độc lập.

```java
Spliterator<Order> spliterator = orders.spliterator();

spliterator.tryAdvance(order ->
        System.out.println(order.id())
);
```

`tryAdvance` xử lý một phần tử nếu còn dữ liệu và trả boolean cho biết có thành công hay không. `forEachRemaining` xử lý phần còn lại.

Khả năng đặc trưng là `trySplit()`:

```java
Spliterator<Order> left = orders.spliterator();
Spliterator<Order> right = left.trySplit();
```

Implementation có thể trả một spliterator khác đại diện cho một phần dữ liệu, hoặc trả `null` nếu không thể/không muốn chia tiếp. Không nên giả định mọi source chia đều hoặc chia hiệu quả như nhau.

Spliterator còn công bố **characteristics** giúp consumer hiểu nguồn, ví dụ:

- `ORDERED` — có encounter order;
- `DISTINCT` — element là distinct theo contract nguồn;
- `SORTED` — source có sorted order;
- `SIZED` — biết chính xác kích thước;
- `SUBSIZED` — các phần sau khi split cũng có size chính xác theo contract.

```java
boolean ordered = spliterator.hasCharacteristics(Spliterator.ORDERED);
long estimate = spliterator.estimateSize();
```

Ranh giới quan trọng của chapter này là mối liên hệ với Stream:

```text
Collection
    ↓ spliterator()
Spliterator
    ↓ cung cấp traversal + splitting + characteristics
Stream pipeline
```

`Collection.stream()` và `parallelStream()` dùng spliterator như nguồn traversal. `Spliterator` tạo nền cho việc partition dữ liệu, nhưng **không tự biến traversal thành parallel** và không tự giải quyết thread safety. Cách Stream pipeline thực thi, parallelism và concurrency là các chủ đề riêng.

Với Collection module, điều cần nhớ là: `Iterator` tối ưu cho traversal tuần tự từng phần tử; `Spliterator` mở rộng giao thức đó bằng thông tin về nguồn và khả năng chia traversal cho các consumer cao hơn như Stream.
