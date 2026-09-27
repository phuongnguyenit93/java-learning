# Fail-Fast Iterator

Iterator giữ trạng thái về vị trí duyệt. Nếu cấu trúc collection thay đổi bên ngoài iterator trong lúc đang duyệt, vị trí đó có thể trở nên không còn ý nghĩa. Nhiều implementation chuẩn của Java phản ứng bằng cơ chế **fail-fast** để báo lỗi sớm thay vì âm thầm tiếp tục trên một traversal đã mất tính nhất quán.

Fail-fast là cơ chế phát hiện bug khi duyệt collection. Nó không phải cơ chế đồng bộ thread và không nên được dùng làm nền tảng cho correctness.

## <a id="structural-modification">Thay đổi cấu trúc (Structural Modification)</a>

**Structural modification** là thay đổi làm biến đổi cấu trúc mà iterator đang duyệt. Ý nghĩa chính xác phụ thuộc implementation, nhưng với các collection phổ biến thường gồm thêm hoặc xóa phần tử/mapping.

Ví dụ với `ArrayList`:

```java
List<String> users = new ArrayList<>(List.of("U01", "U02", "U03", "U04"));

users.add("U04");    // structural: size thay đổi
users.remove("U01"); // structural: size thay đổi
users.set(0, "U10"); // không structural đối với ArrayList
```

Với `Map`, thêm/xóa một mapping thường là structural; thay value của key đã tồn tại thường không thay cấu trúc key set. Tuy nhiên code application không nên phụ thuộc vào field nội bộ như `modCount` của một implementation cụ thể. Contract của collection/iterator mới là boundary cần tin cậy.

## <a id="fail-fast-best-effort">Fail-fast là best-effort</a>

Mental model điển hình của fail-fast iterator là:

```text
tạo iterator
   ↓
ghi nhận trạng thái sửa đổi kỳ vọng
   ↓
collection bị structural modification ngoài iterator
   ↓
iterator phát hiện trạng thái không còn khớp
   ↓
ConcurrentModificationException
```

JDK mô tả hành vi fail-fast theo **best effort**. Không thể bảo đảm tuyệt đối exception sẽ được ném trong mọi race hoặc mọi thời điểm. Vì vậy đoạn code kiểu “nếu có sửa sai thì CME sẽ bảo vệ tôi” là thiết kế sai.

Fail-fast hữu ích vì nó biến một bug traversal dễ bị che giấu thành tín hiệu rõ hơn trong lúc phát triển/debug. Correctness vẫn phải đến từ việc chọn cách mutation hợp lệ hoặc chọn collection/concurrency model phù hợp.

## <a id="concurrent-modification-exception">ConcurrentModificationException thực sự có nghĩa gì?</a>

Tên `ConcurrentModificationException` dễ khiến người học nghĩ rằng phải có hai thread. Thực tế một thread duy nhất cũng có thể gây CME khi nó sửa collection qua reference collection trong lúc đang dùng iterator của collection đó. Ví dụ dưới đây dùng trực tiếp `ArrayList` iterator để điểm phát hiện rõ ràng:

```java
List<String> users = new ArrayList<>(List.of("U01", "U02", "U03"));
Iterator<String> iterator = users.iterator();

System.out.println(iterator.next()); // U01

users.add("U04");                   // structural modification ngoài iterator

iterator.next();                    // ArrayList iterator phát hiện mismatch -> CME
```

Enhanced `for` trên collection cũng dùng iterator ở phía dưới, nên cùng nguyên tắc áp dụng khi sửa trực tiếp collection trong vòng lặp. Tuy nhiên fail-fast chỉ là best-effort; không nên cố tình viết code sai rồi dựa vào việc “chắc chắn sẽ có CME” để bảo vệ correctness.

CME nên được đọc là: **iterator phát hiện collection bị sửa theo cách làm quy ước duyệt không còn hợp lệ**. Nó không chứng minh có tranh chấp dữ liệu (data race), cũng không phải tín hiệu để `catch` rồi bỏ qua.

Trong môi trường nhiều thread, việc chọn `ConcurrentHashMap`, `CopyOnWriteArrayList`, locking hoặc mô hình sở hữu thuộc bài toán concurrency rộng hơn. Iterator của concurrent collection có thể có quy ước khác, chẳng hạn weakly consistent, nên không thể áp logic fail-fast của `ArrayList` cho mọi collection.

## <a id="iterator-safe-mutation">Thay đổi an toàn qua Iterator</a>

Nếu cần xóa phần tử hiện tại trong lúc duyệt, hãy dùng operation mà iterator cho phép:

```java
List<Order> orders = new ArrayList<>(List.of(
        new Order(1, 1, 20_000),
        new Order(2, 2, 0),
        new Order(3, 3, 15_000)
));

Iterator<Order> iterator = orders.iterator();
while (iterator.hasNext()) {
    Order order = iterator.next();
    if (order.totalCents() == 0) {
        iterator.remove();
    }
}
```

`Iterator.remove()` gắn mutation với đúng trạng thái traversal hiện tại. Thông thường cần gọi `next()` trước và không được gọi `remove()` hai lần cho cùng một phần tử nếu chưa có `next()` mới.

Khi logic chỉ là lọc/xóa theo điều kiện, API mức cao thường rõ hơn:

```java
orders.removeIf(order -> order.totalCents() == 0);
```

Với `ListIterator`, các thao tác như `add`, `remove` và `set` có quy ước riêng để thay đổi dữ liệu trong lúc duyệt. Với map, có thể duyệt `entrySet().iterator()` rồi gọi `iterator.remove()`.

Điểm chung là mutation phải đi qua cơ chế mà traversal protocol hỗ trợ. Nếu yêu cầu là “một thread duyệt trong khi thread khác sửa”, đó là bài toán concurrent collection/synchronization chứ không phải mẹo tránh CME.
