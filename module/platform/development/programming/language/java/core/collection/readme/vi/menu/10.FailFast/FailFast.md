# Thay đổi khi đang duyệt và Fail-Fast

Tiếp nối chặng **Khả năng thay đổi, view và Fail-Fast**, chương này tập trung vào điều xảy ra khi cấu trúc collection thay đổi trong lúc Iterator đang duyệt. Iterator giữ trạng thái về vị trí duyệt; nếu cấu trúc thay đổi bên ngoài iterator, vị trí đó có thể trở nên không còn ý nghĩa. Nhiều cách triển khai chuẩn của Java phản ứng bằng cơ chế **fail-fast** để báo lỗi sớm thay vì âm thầm tiếp tục trên một quá trình duyệt đã mất tính nhất quán.

Fail-fast là cơ chế phát hiện lỗi lập trình khi duyệt collection. Nó không phải cơ chế đồng bộ luồng và không nên được dùng làm nền tảng cho tính đúng đắn.

## <a id="structural-modification">Thay đổi cấu trúc (Structural Modification)</a>

**Thay đổi cấu trúc (structural modification)** là thay đổi làm biến đổi cấu trúc mà iterator đang duyệt. Ý nghĩa chính xác phụ thuộc cách triển khai, nhưng với các collection phổ biến thường gồm thêm hoặc xóa phần tử/ánh xạ.

Ví dụ với `ArrayList`:

```java
List<String> users = new ArrayList<>(List.of("U01", "U02", "U03", "U04"));

users.add("U04");    // thay đổi cấu trúc: kích thước thay đổi
users.remove("U01"); // thay đổi cấu trúc: kích thước thay đổi
users.set(0, "U10"); // không phải thay đổi cấu trúc đối với ArrayList
```

Với `Map`, thêm/xóa một ánh xạ thường là thay đổi cấu trúc; thay giá trị của khóa đã tồn tại thường không thay cấu trúc tập khóa. Tuy nhiên mã nguồn ứng dụng không nên phụ thuộc vào trường nội bộ như `modCount` của một cách triển khai cụ thể. Quy ước của collection/iterator mới là ranh giới cần tin cậy.

Một ngoại lệ hữu ích cho cách hiểu đơn giản trên là `LinkedHashMap` ở chế độ thứ tự truy cập: một lần truy cập như `get` có thể làm entry đổi vị trí trong thứ tự duyệt, và việc sắp lại thứ tự đó có thể được xem là thay đổi cấu trúc đối với iterator fail-fast của nó. Vì vậy không nên rút gọn “thay đổi cấu trúc” thành “kích thước thay đổi”; cần đọc quy ước của collection cụ thể.

## <a id="fail-fast-best-effort">Fail-fast chỉ cố gắng phát hiện lỗi, không bảo đảm tuyệt đối</a>

Mô hình tư duy điển hình của iterator fail-fast là:

```text
tạo iterator
   ↓
ghi nhận trạng thái sửa đổi kỳ vọng
   ↓
collection bị thay đổi cấu trúc ngoài iterator
   ↓
iterator phát hiện trạng thái không còn khớp
   ↓
ConcurrentModificationException
```

JDK mô tả hành vi fail-fast theo kiểu **cố gắng phát hiện tốt nhất (best effort)**. Không thể bảo đảm tuyệt đối ngoại lệ sẽ được ném trong mọi tình huống tranh chấp hoặc mọi thời điểm. Vì vậy đoạn mã kiểu “nếu có sửa sai thì CME sẽ bảo vệ tôi” là thiết kế sai.

Fail-fast hữu ích vì nó biến một lỗi duyệt dữ liệu dễ bị che giấu thành tín hiệu rõ hơn trong lúc phát triển/gỡ lỗi. Tính đúng đắn vẫn phải đến từ việc chọn cách thay đổi dữ liệu hợp lệ hoặc chọn mô hình collection/xử lý đồng thời phù hợp.

## <a id="concurrent-modification-exception">ConcurrentModificationException thực sự có nghĩa gì?</a>

Tên `ConcurrentModificationException` dễ khiến người học nghĩ rằng phải có hai luồng. Thực tế một luồng duy nhất cũng có thể gây CME khi nó sửa collection qua tham chiếu collection trong lúc đang dùng iterator của collection đó. Ví dụ dưới đây dùng trực tiếp iterator của `ArrayList` để điểm phát hiện rõ ràng:

```java
List<String> users = new ArrayList<>(List.of("U01", "U02", "U03"));
Iterator<String> iterator = users.iterator();

System.out.println(iterator.next()); // U01

users.add("U04");                   // thay đổi cấu trúc ngoài iterator

iterator.next();                    // iterator ArrayList phát hiện trạng thái không khớp -> CME
```

Vòng lặp for-each trên collection cũng dùng iterator ở phía dưới, nên cùng nguyên tắc áp dụng khi sửa trực tiếp collection trong vòng lặp. Tuy nhiên fail-fast chỉ cố gắng phát hiện lỗi và không bảo đảm tuyệt đối; không nên cố tình viết mã sai rồi dựa vào việc “chắc chắn sẽ có CME” để bảo vệ tính đúng đắn.

CME nên được đọc là: **iterator phát hiện collection bị sửa theo cách làm quy ước duyệt không còn hợp lệ**. Nó không chứng minh có tranh chấp dữ liệu (data race), cũng không phải tín hiệu để `catch` rồi bỏ qua.

Trong môi trường nhiều luồng, việc chọn `ConcurrentHashMap`, `CopyOnWriteArrayList`, khóa hoặc mô hình sở hữu thuộc bài toán xử lý đồng thời rộng hơn. Iterator của collection hỗ trợ xử lý đồng thời có thể có quy ước khác, chẳng hạn **nhất quán yếu (weakly consistent)**, nên không thể áp logic fail-fast của `ArrayList` cho mọi collection.

## <a id="iterator-safe-mutation">Thay đổi an toàn qua Iterator</a>

Nếu cần xóa phần tử hiện tại trong lúc duyệt, hãy dùng thao tác mà iterator cho phép:

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

`Iterator.remove()` gắn thay đổi dữ liệu với đúng trạng thái duyệt hiện tại. Thông thường cần gọi `next()` trước và không được gọi `remove()` hai lần cho cùng một phần tử nếu chưa có `next()` mới.

Khi logic chỉ là lọc/xóa theo điều kiện, API mức cao thường rõ hơn:

```java
orders.removeIf(order -> order.totalCents() == 0);
```

Với `ListIterator`, các thao tác như `add`, `remove` và `set` có quy ước riêng để thay đổi dữ liệu trong lúc duyệt. Với Map, có thể duyệt `entrySet().iterator()` rồi gọi `iterator.remove()`.

Điểm chung là thay đổi dữ liệu phải đi qua cơ chế mà giao thức duyệt hỗ trợ. Nếu yêu cầu là “một luồng duyệt trong khi luồng khác sửa”, đó là bài toán về collection hỗ trợ truy cập đồng thời và đồng bộ hóa, chứ không phải mẹo tránh CME.
