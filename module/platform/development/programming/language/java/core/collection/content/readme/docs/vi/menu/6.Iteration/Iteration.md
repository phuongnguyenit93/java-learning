# Duyệt dữ liệu với Iterator

Sau khi chọn được cấu trúc dữ liệu phù hợp, phần lớn thuật toán vẫn cần đi qua các phần tử. Nếu mỗi cách triển khai bắt bên gọi biết cách mảng, nút, cây hay bucket hash được tổ chức bên trong thì mã nguồn sẽ bị gắn chặt với cách triển khai. Java tách **giao thức duyệt** khỏi **cấu trúc lưu trữ** thông qua `Iterable`, `Iterator`, `ListIterator` và `Spliterator`.

## <a id="iterator-contract">Hợp đồng Iterator</a>

`Iterator<E>` đại diện cho một con trỏ logic đi qua một chuỗi phần tử:

```java
Iterator<Order> iterator = orders.iterator();

while (iterator.hasNext()) {
    Order order = iterator.next();
    System.out.println(order.id());
}
```

Ba thao tác cốt lõi:

- `hasNext()` hỏi còn phần tử tiếp theo hay không;
- `next()` trả phần tử tiếp theo và tiến con trỏ;
- `remove()` là thao tác xóa **tùy chọn**, chỉ có khi iterator cụ thể hỗ trợ.

Nếu gọi `next()` khi không còn phần tử, iterator ném `NoSuchElementException`. Bên gọi không nên dùng ngoại lệ như điều kiện vòng lặp bình thường; mẫu chuẩn là kiểm tra `hasNext()` trước.

`Iterator` không hứa mọi cấu trúc có cùng thứ tự. Thứ tự của iterator đến từ collection:

- `ArrayList` duyệt theo thứ tự của List;
- `LinkedHashSet` duyệt theo thứ tự duyệt của nó;
- `TreeSet` duyệt theo thứ tự sắp xếp;
- `HashSet` không hứa một thứ tự duyệt cụ thể;
- iterator của `PriorityQueue` không hứa duyệt theo thứ tự ưu tiên.

Điều này cho phép thuật toán viết theo interface mà vẫn tôn trọng ngữ nghĩa của nguồn:

```java
void printOrders(Iterable<Order> source) {
    Iterator<Order> it = source.iterator();
    while (it.hasNext()) {
        System.out.println(it.next());
    }
}
```

Iterator thường giữ trạng thái và dùng cho một lần duyệt tiến về phía trước. Muốn duyệt lại, thường cần lấy iterator mới từ collection.

Việc sửa trực tiếp cấu trúc collection trong lúc đang dùng iterator có thể làm mất hiệu lực các giả định của iterator và dẫn tới hành vi fail-fast ở nhiều collection thông thường. Chương Fail-Fast sẽ đi sâu vào `ConcurrentModificationException`; ở đây chỉ cần biết rằng thay đổi dữ liệu trong lúc duyệt phải theo đúng quy ước của iterator/collection.

## <a id="enhanced-for">Vòng lặp for-each</a>

Vòng `for-each` là cú pháp giúp mã nguồn sử dụng giao thức duyệt mà không phải tự quản lý iterator.

```java
for (Order order : orders) {
    System.out.println(order.id());
}
```

Với một đối tượng thực hiện `Iterable<Order>`, có thể hình dung trình biên dịch chuyển logic thành dạng gần như:

```java
for (Iterator<Order> it = orders.iterator(); it.hasNext(); ) {
    Order order = it.next();
    System.out.println(order.id());
}
```

Đây là mô hình tư duy, không phải yêu cầu rằng bytecode phải có đúng hình thức trên. Điểm quan trọng là vòng `for-each` phụ thuộc vào quy ước duyệt thay vì vào `ArrayList`, `HashSet` hay một cách triển khai cụ thể.

Mảng cũng dùng được vòng lặp `for-each`:

```java
Order[] array = {orderA, orderB};
for (Order order : array) {
    System.out.println(order.id());
}
```

Mảng là trường hợp riêng của ngôn ngữ; nó không triển khai `Iterable` và không tạo `Iterator` theo quy ước Collection.

Vòng `for-each` phù hợp khi chỉ cần đọc/xử lý từng phần tử. Nó không cung cấp trực tiếp:

- chỉ số hiện tại;
- API `Iterator.remove()`;
- điều khiển hai iterator song song;
- khả năng dừng/chia quá trình duyệt theo `Spliterator`.

Khi thuật toán cần những khả năng đó, dùng trực tiếp API duyệt sẽ rõ hơn.

## <a id="iterator-remove">Xóa an toàn qua Iterator</a>

Một lỗi phổ biến là xóa trực tiếp từ collection trong vòng `for-each`:

```java
for (Order order : orders) {
    if (order.totalCents() == 0) {
        orders.remove(order); // có thể làm iterator đang dùng mất hiệu lực
    }
}
```

Với iterator hỗ trợ `remove`, cách đúng là xóa thông qua chính iterator đó:

```java
Iterator<Order> it = orders.iterator();

while (it.hasNext()) {
    Order order = it.next();
    if (order.totalCents() == 0) {
        it.remove();
    }
}
```

`Iterator.remove()` xóa phần tử cuối cùng vừa được `next()` trả về. Quy ước có hai giới hạn quan trọng:

1. `remove()` là thao tác tùy chọn; iterator của collection không cho phép sửa hoặc cách triển khai không hỗ trợ có thể ném `UnsupportedOperationException`;
2. không được gọi `remove()` trước `next()` hoặc gọi hai lần liên tiếp cho cùng một `next()`; trường hợp đó ném `IllegalStateException`.

Ví dụ:

```java
Iterator<Order> it = orders.iterator();

Order first = it.next();
it.remove(); // hợp lệ nếu iterator hỗ trợ

// it.remove(); // IllegalStateException: chưa có next() mới
```

Khi chỉ cần lọc một collection có thể thay đổi theo điều kiện, `removeIf` thường biểu đạt ý định gọn hơn:

```java
orders.removeIf(order -> order.totalCents() == 0);
```

Nhưng hiểu `Iterator.remove()` vẫn quan trọng vì nó giải thích thay đổi dữ liệu được phối hợp với quá trình duyệt như thế nào. Các chi tiết fail-fast và thay đổi cấu trúc thuộc chương riêng sau đó.

## <a id="listiterator-contract">ListIterator: duyệt hai chiều và chỉnh sửa theo vị trí</a>

`ListIterator<E>` mở rộng ý tưởng của `Iterator` riêng cho `List`. Ngoài đi tới trước, nó có thể đi lùi, biết chỉ số lân cận và cung cấp các thao tác thay đổi dữ liệu gắn với vị trí duyệt.

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

Các thao tác đặc trưng:

| thao tác | Ý nghĩa |
| --- | --- |
| `hasPrevious()` / `previous()` | duyệt ngược |
| `nextIndex()` / `previousIndex()` | quan sát vị trí con trỏ |
| `set(e)` | thay phần tử vừa được `next/previous` trả về |
| `add(e)` | chèn tại vị trí con trỏ |
| `remove()` | xóa phần tử vừa được quá trình duyệt trả về |

Ví dụ chuẩn hóa một Order trong khi duyệt:

```java
ListIterator<Order> it = orders.listIterator();
while (it.hasNext()) {
    Order current = it.next();
    if (current.totalCents() < 0) {
        it.set(new Order(current.id(), current.userId(), 0));
    }
}
```

`ListIterator` hữu ích khi thuật toán thật sự cần **vị trí + duyệt hai chiều + thay đổi dữ liệu phối hợp với quá trình duyệt**. Nếu chỉ cần đọc từng phần tử, vòng `for-each` đơn giản hơn; nếu chỉ cần xóa theo điều kiện, `removeIf` thường rõ hơn. `add/set/remove` vẫn là các thao tác tùy chọn nếu List phía dưới không hỗ trợ thay đổi dữ liệu.

## <a id="spliterator-boundary">Ranh giới Spliterator</a>

`Spliterator<E>` có thể xem là mô hình duyệt mở rộng từ ý tưởng iterator. Nó hỗ trợ hai khả năng:

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

Cách triển khai có thể trả một spliterator khác đại diện cho một phần dữ liệu, hoặc trả `null` nếu không thể/không muốn chia tiếp. Không nên giả định mọi nguồn chia đều hoặc chia hiệu quả như nhau.

Spliterator còn công bố **các đặc tính (characteristics)** giúp bên sử dụng hiểu nguồn, ví dụ:

- `ORDERED` — có thứ tự duyệt;
- `DISTINCT` — phần tử là duy nhất theo quy ước nguồn;
- `SORTED` — nguồn có thứ tự sắp xếp;
- `SIZED` — biết chính xác kích thước;
- `SUBSIZED` — các phần sau khi tách cũng có kích thước chính xác theo quy ước.

```java
boolean ordered = spliterator.hasCharacteristics(Spliterator.ORDERED);
long estimate = spliterator.estimateSize();
```

Ranh giới quan trọng của chương này là mối liên hệ với Stream:

```text
Collection
    ↓ spliterator()
Spliterator
    ↓ cung cấp khả năng duyệt + chia nhỏ + đặc tính nguồn
Stream pipeline
```

`Collection.stream()` và `parallelStream()` dùng spliterator làm nguồn duyệt. `Spliterator` tạo nền cho việc chia dữ liệu, nhưng **không tự biến quá trình duyệt thành song song** và không tự giải quyết an toàn luồng. Cách pipeline Stream thực thi, tính song song và xử lý đồng thời là các chủ đề riêng.

Với module Collection, điều cần nhớ là: `Iterator` tối ưu cho việc duyệt tuần tự từng phần tử; `Spliterator` mở rộng giao thức đó bằng thông tin về nguồn và khả năng chia quá trình duyệt cho các thành phần tiêu thụ cấp cao hơn như Stream.
