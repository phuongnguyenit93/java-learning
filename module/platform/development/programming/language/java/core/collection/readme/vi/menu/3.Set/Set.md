# Set

Sau `List`, câu hỏi thay đổi từ “phần tử nằm ở vị trí nào?” sang “mỗi giá trị logic có được xuất hiện nhiều hơn một lần không?”. Khi câu trả lời là không, `Set` diễn đạt ý định tốt hơn một `List` rồi tự kiểm tra trùng bằng code bên ngoài.

## <a id="set-semantics">Ngữ nghĩa của Set</a>

Contract chung của `Set<E>` là **không chứa hai phần tử bằng nhau theo equality contract của `Set`**. Với các cách triển khai thông thường như `HashSet`, `equals` quyết định equality còn `hashCode` giúp tìm vùng ứng viên hiệu quả. Đặc điểm riêng của sorted set như `TreeSet` sẽ được giải thích ở đúng section của nó sau khi learner đã nắm semantics cơ bản của `Set`.

Ví dụ, nếu chỉ cần biết những user nào đã có đơn hàng:

```java
Set<Long> userIds = new HashSet<>();
userIds.add(7L);
userIds.add(8L);
userIds.add(7L);

System.out.println(userIds.size()); // 2
```

Lần `add(7L)` thứ hai không tạo thêm một phần tử. `Set.add` trả về `false` khi set không thay đổi vì phần tử tương đương đã tồn tại:

```java
boolean first = userIds.add(9L);  // true
boolean again = userIds.add(9L);  // false
```

`Set` không hứa truy cập theo index. Nếu code cần “phần tử thứ ba”, đó thường là dấu hiệu cần `List` hoặc một abstraction có encounter order rõ hơn.

Chính sách `null` không giống nhau giữa các implementation. `HashSet` và `LinkedHashSet` cho phép một phần tử `null`; `TreeSet` dùng natural ordering thông thường sẽ không chấp nhận `null`. API nên dựa vào contract/documentation của implementation cụ thể thay vì giả định mọi `Set` giống nhau.

Từ Java 21, `SequencedSet` kết hợp uniqueness của `Set` với encounter order có đầu/cuối. `LinkedHashSet` và các sorted-set interfaces hiện đại tham gia abstraction này, nên code có thể nói rõ khi cả uniqueness lẫn order đều là một phần của hợp đồng.

## <a id="set-algebra">Các phép toán tập hợp bằng Set API</a>

Vì `Set` mô hình hóa một tập phần tử duy nhất, các bulk operation của `Collection` có thể được đọc theo đúng tư duy tập hợp:

```java
Set<Long> first = new HashSet<>(Set.of(1L, 2L, 3L));
Set<Long> second = Set.of(3L, 4L);
```

### Hợp (union)

```java
Set<Long> union = new HashSet<>(first);
union.addAll(second); // [1, 2, 3, 4]
```

### Giao (intersection)

```java
Set<Long> intersection = new HashSet<>(first);
intersection.retainAll(second); // [3]
```

### Hiệu (difference)

```java
Set<Long> difference = new HashSet<>(first);
difference.removeAll(second); // [1, 2]
```

Điểm quan trọng là các operation này **thay đổi set nhận lời gọi** nếu set đó mutable. Khi muốn giữ dữ liệu gốc, tạo một copy trước rồi thao tác trên copy như các ví dụ trên.

## <a id="set-equality">Equality của Set không phụ thuộc thứ tự</a>

Hai `Set` bằng nhau khi chúng có cùng kích thước và mỗi phần tử của set này cũng nằm trong set kia. **Encounter order không tham gia `Set.equals`.**

```java
Set<Long> a = new HashSet<>(List.of(10L, 20L, 30L));
Set<Long> b = new LinkedHashSet<>(List.of(30L, 20L, 10L));

System.out.println(a.equals(b)); // true
```

Điều này khác `List.equals`, nơi vị trí của từng phần tử là một phần của equality. Vì vậy nếu business value chỉ quan tâm “có những thành viên nào” chứ không quan tâm thứ tự, `Set` thường biểu diễn semantics đúng hơn `List`.

## <a id="hashset-model">Mô hình HashSet</a>

`HashSet` phù hợp khi câu hỏi chính là membership/uniqueness và không cần một encounter order cụ thể. Mental model hữu ích là:

```text
element
  ↓ hashCode()
chọn vùng/bucket ứng viên
  ↓
equals() xác nhận phần tử tương đương
```

Nếu hash phân bố hợp lý, `add`, `contains` và `remove` có chi phí kỳ vọng gần O(1). Đây là đặc tính hiệu năng dự kiến, không phải đảm bảo rằng mọi lần gọi luôn là hằng số.

```java
Set<Long> processedOrderIds = new HashSet<>();

if (processedOrderIds.add(order.id())) {
    process(order);
}
```

Ở đây `add` vừa kiểm tra vừa ghi nhận uniqueness, tránh pattern “`contains` rồi mới `add`” không cần thiết trong code đơn thread.

Chi tiết bucket, resize hay tree bin là chi tiết implementation và có thể thay đổi. Điều contract cần người dùng hiểu là:

1. element phải duy trì hành vi equality/hash ổn định khi đang nằm trong set;
2. hash giúp tìm vùng ứng viên;
3. `equals` xác nhận equality đối với hash-based collection.

Nếu một object thay đổi field tham gia `equals/hashCode` sau khi đã được thêm vào `HashSet`, lookup có thể trở nên sai lệch theo góc nhìn người dùng vì object không còn nằm ở bucket phù hợp với hash mới. Module Equality/Hashing sẽ đi sâu vào invariant này; ở đây chỉ cần tránh dùng key/element mutable theo cách làm thay đổi identity logic.

`HashSet` không đảm bảo iteration order. Một output “có vẻ ổn định” ở vài lần chạy không phải contract để dựa vào.

## <a id="linkedhashset-order">Thứ tự của LinkedHashSet</a>

`LinkedHashSet` thêm một encounter order xác định vào uniqueness của hash set. Với thao tác `add` thông thường, thứ tự mặc định là thứ tự phần tử được chèn lần đầu.

```java
Set<Long> userIds = new LinkedHashSet<>();
userIds.add(7L);
userIds.add(2L);
userIds.add(9L);
userIds.add(2L);

System.out.println(userIds); // [7, 2, 9]
```

Thêm lại `2L` không tạo phần tử mới và không tự động chuyển nó về cuối theo semantics của `add` thông thường.

Đây là lựa chọn tốt khi business logic cần cả hai điều:

```text
không trùng
    +
giữ encounter order có thể dự đoán
```

Ví dụ, ta có thể lấy danh sách user duy nhất theo thứ tự user xuất hiện lần đầu trong stream đơn hàng:

```java
Set<Long> usersInFirstSeenOrder = new LinkedHashSet<>();
for (Order order : orders) {
    usersInFirstSeenOrder.add(order.userId());
}
```

Trong Java 21, `LinkedHashSet` triển khai `SequencedSet`. Ngoài encounter order vốn có, nó có API first/last, explicit positioning như `addFirst`/`addLast` và view `reversed()`. Điều này làm rõ hơn rằng order của cấu trúc là contract quan sát được, không chỉ là một chi tiết in ra đẹp mắt.

Đổi từ `HashSet` sang `LinkedHashSet` có thêm chi phí lưu thông tin liên kết thứ tự. Vì vậy chỉ trả chi phí đó khi order thật sự là yêu cầu có giá trị.

## <a id="treeset-order">Thứ tự của TreeSet</a>

`TreeSet` giải quyết một bài toán khác: nó giữ phần tử theo **sorted order** thay vì insertion order.

```java
Set<Long> orderIds = new TreeSet<>();
orderIds.add(30L);
orderIds.add(10L);
orderIds.add(20L);

System.out.println(orderIds); // [10, 20, 30]
```

Thứ tự đến từ:

- natural ordering của element nếu không truyền comparator;
- hoặc một `Comparator<? super E>` được cung cấp khi tạo set.

```java
record User(long id, String name) {}

Set<User> usersByName = new TreeSet<>(
        Comparator.comparing(User::name)
                  .thenComparingLong(User::id)
);
```

Với `TreeSet`, kết quả so sánh bằng 0 quyết định rằng hai element chiếm cùng một vị trí logic trong set:

```java
Comparator<User> byNameOnly = Comparator.comparing(User::name);
Set<User> users = new TreeSet<>(byNameOnly);

users.add(new User(1, "An"));
users.add(new User(2, "An"));

System.out.println(users.size()); // 1
```

Hai `User` trên có id khác nhau nhưng comparator chỉ nhìn `name`, nên `compare(a, b) == 0` và `TreeSet` xem chúng là cùng vị trí cho mục đích lưu trữ. Nếu ordering không nhất quán với `equals`, behavior của `TreeSet` vẫn xác định nhưng sorted set không còn thực hiện sạch contract equality chung của `Set`. Trong thực tế, comparator dùng cho `TreeSet` nên nhất quán với notion of equality mà API muốn công bố, hoặc cần chọn cấu trúc khác.

`TreeSet` thường có O(log n) cho `add`, `contains` và `remove`. Đổi lại, nó cung cấp sorted/navigable operations như `first`, `last`, `lower`, `higher`, `floor` và `ceiling`.

Từ Java 21, sorted-set hierarchy cũng có sequenced semantics, nên đầu/cuối và view đảo có thể được diễn đạt thống nhất. Dù API hiện đại hơn, điều cần chọn vẫn là **sorted order** chứ không phải insertion order.
