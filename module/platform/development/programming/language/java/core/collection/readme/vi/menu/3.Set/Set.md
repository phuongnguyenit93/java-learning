# Các mô hình dữ liệu cốt lõi — Set

Sau `List`, câu hỏi thay đổi từ “phần tử nằm ở vị trí nào?” sang “mỗi giá trị logic có được xuất hiện nhiều hơn một lần không?”. Khi câu trả lời là không, `Set` diễn đạt ý định tốt hơn một `List` rồi tự kiểm tra trùng bằng mã nguồn bên ngoài.

## <a id="set-semantics">Ngữ nghĩa của Set</a>

Quy ước chung của `Set<E>` là **không chứa hai phần tử bằng nhau theo quy tắc tính bằng nhau của `Set`**. Với các cách triển khai thông thường như `HashSet`, `equals` quyết định tính bằng nhau còn `hashCode` giúp tìm vùng ứng viên hiệu quả. Đặc điểm riêng của Set có sắp xếp như `TreeSet` sẽ được giải thích ở đúng phần của nó sau khi người học đã nắm ngữ nghĩa cơ bản của `Set`.

Ví dụ, nếu chỉ cần biết những người dùng nào đã có đơn hàng:

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

`Set` không hứa truy cập theo chỉ số. Nếu mã nguồn cần “phần tử thứ ba”, đó thường là dấu hiệu cần `List` hoặc một mô hình trừu tượng có thứ tự duyệt rõ hơn.

Chính sách `null` không giống nhau giữa các cách triển khai. `HashSet` và `LinkedHashSet` cho phép một phần tử `null`; `TreeSet` dùng thứ tự tự nhiên thông thường sẽ không chấp nhận `null`. API nên dựa vào quy ước/tài liệu của cách triển khai cụ thể thay vì giả định mọi `Set` giống nhau.

Từ Java 21, `SequencedSet` kết hợp tính duy nhất của `Set` với thứ tự duyệt có đầu/cuối. `LinkedHashSet` và các interface Set có sắp xếp hiện đại tham gia mô hình trừu tượng này, nên mã nguồn có thể nói rõ khi cả tính duy nhất lẫn thứ tự đều là một phần của quy ước.

## <a id="set-algebra">Các phép toán tập hợp bằng Set API</a>

Vì `Set` mô hình hóa một tập phần tử duy nhất, các thao tác hàng loạt của `Collection` có thể được đọc theo đúng tư duy tập hợp:

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

Điểm quan trọng là các thao tác này **thay đổi Set nhận lời gọi** nếu Set đó có thể thay đổi. Khi muốn giữ dữ liệu gốc, hãy tạo một bản sao trước rồi thao tác trên bản sao như các ví dụ trên.

## <a id="set-equality">Tính bằng nhau của Set không phụ thuộc thứ tự</a>

Hai `Set` bằng nhau khi chúng có cùng kích thước và mỗi phần tử của set này cũng nằm trong set kia. **thứ tự duyệt không tham gia `Set.equals`.**

```java
Set<Long> a = new HashSet<>(List.of(10L, 20L, 30L));
Set<Long> b = new LinkedHashSet<>(List.of(30L, 20L, 10L));

System.out.println(a.equals(b)); // true
```

Điều này khác `List.equals`, nơi vị trí của từng phần tử là một phần của tính bằng nhau. Vì vậy nếu giá trị nghiệp vụ chỉ quan tâm “có những thành viên nào” chứ không quan tâm thứ tự, `Set` thường biểu diễn ngữ nghĩa đúng hơn `List`.

## <a id="hashset-model">Mô hình HashSet</a>

`HashSet` phù hợp khi câu hỏi chính là kiểm tra thành viên/tính duy nhất và không cần một thứ tự duyệt cụ thể. Mô hình tư duy hữu ích là:

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

Ở đây `add` vừa kiểm tra vừa ghi nhận tính duy nhất, tránh mẫu “`contains` rồi mới `add`” không cần thiết trong mã nguồn đơn luồng.

Chi tiết bucket, tăng dung lượng hay tree bin là chi tiết cách triển khai và có thể thay đổi. Điều người dùng cần hiểu từ quy ước là:

1. phần tử phải duy trì hành vi tính bằng nhau/hash ổn định khi đang nằm trong set;
2. hash giúp tìm vùng ứng viên;
3. `equals` xác nhận tính bằng nhau đối với collection dựa trên hash.

Nếu một đối tượng thay đổi trường tham gia `equals/hashCode` sau khi đã được thêm vào `HashSet`, tra cứu có thể trở nên sai lệch theo góc nhìn người dùng vì đối tượng không còn nằm ở bucket phù hợp với hash mới. Chương **Tính bằng nhau, hash và tính đúng đắn khi tra cứu** sẽ đi sâu vào bất biến này; ở đây chỉ cần tránh dùng khóa/phần tử có thể thay đổi theo cách làm thay đổi logic định danh.

`HashSet` không đảm bảo thứ tự duyệt. Một kết quả “có vẻ ổn định” ở vài lần chạy không phải quy ước để dựa vào.

## <a id="linkedhashset-order">Thứ tự của LinkedHashSet</a>

`LinkedHashSet` thêm một thứ tự duyệt xác định vào tính duy nhất của HashSet. Với thao tác `add` thông thường, thứ tự mặc định là thứ tự phần tử được chèn lần đầu.

```java
Set<Long> userIds = new LinkedHashSet<>();
userIds.add(7L);
userIds.add(2L);
userIds.add(9L);
userIds.add(2L);

System.out.println(userIds); // [7, 2, 9]
```

Thêm lại `2L` không tạo phần tử mới và không tự động chuyển nó về cuối theo ngữ nghĩa của `add` thông thường.

Đây là lựa chọn tốt khi logic nghiệp vụ cần cả hai điều:

```text
không trùng
    +
giữ thứ tự duyệt có thể dự đoán
```

Ví dụ, ta có thể lấy danh sách người dùng duy nhất theo thứ tự họ xuất hiện lần đầu trong luồng dữ liệu đơn hàng:

```java
Set<Long> usersInFirstSeenOrder = new LinkedHashSet<>();
for (Order order : orders) {
    usersInFirstSeenOrder.add(order.userId());
}
```

Trong Java 21, `LinkedHashSet` triển khai `SequencedSet`. Ngoài thứ tự duyệt vốn có, nó có API đầu/cuối, khả năng định vị rõ ràng như `addFirst`/`addLast` và khung nhìn `reversed()`. Điều này làm rõ hơn rằng thứ tự của cấu trúc là quy ước quan sát được, không chỉ là một chi tiết in ra đẹp mắt.

Đổi từ `HashSet` sang `LinkedHashSet` có thêm chi phí lưu thông tin liên kết thứ tự. Vì vậy chỉ trả chi phí đó khi thứ tự thật sự là yêu cầu có giá trị.

## <a id="treeset-order">Thứ tự của TreeSet</a>

`TreeSet` giải quyết một bài toán khác: nó giữ phần tử theo **thứ tự sắp xếp** thay vì thứ tự chèn.

```java
Set<Long> orderIds = new TreeSet<>();
orderIds.add(30L);
orderIds.add(10L);
orderIds.add(20L);

System.out.println(orderIds); // [10, 20, 30]
```

Thứ tự đến từ:

- thứ tự tự nhiên của phần tử nếu không truyền comparator;
- hoặc một `Comparator<? super E>` được cung cấp khi tạo set.

```java
record User(long id, String name) {}

Set<User> usersByName = new TreeSet<>(
        Comparator.comparing(User::name)
                  .thenComparingLong(User::id)
);
```

Với `TreeSet`, kết quả so sánh bằng 0 quyết định rằng hai phần tử chiếm cùng một vị trí logic trong set:

```java
Comparator<User> byNameOnly = Comparator.comparing(User::name);
Set<User> users = new TreeSet<>(byNameOnly);

users.add(new User(1, "An"));
users.add(new User(2, "An"));

System.out.println(users.size()); // 1
```

Hai `User` trên có id khác nhau nhưng comparator chỉ nhìn `name`, nên `compare(a, b) == 0` và `TreeSet` xem chúng là cùng vị trí cho mục đích lưu trữ. Nếu thứ tự không nhất quán với `equals`, hành vi của `TreeSet` vẫn xác định nhưng Set có sắp xếp không còn thực hiện sạch quy ước tính bằng nhau chung của `Set`. Trong thực tế, comparator dùng cho `TreeSet` nên nhất quán với khái niệm tính bằng nhau mà API muốn công bố, hoặc cần chọn cấu trúc khác.

`TreeSet` thường có O(log n) cho `add`, `contains` và `remove`. Đổi lại, nó cung cấp các thao tác sắp xếp/điều hướng như `first`, `last`, `lower`, `higher`, `floor` và `ceiling`.

Từ Java 21, hệ phân cấp Set có sắp xếp cũng có ngữ nghĩa Sequenced, nên đầu/cuối và khung nhìn đảo có thể được diễn đạt thống nhất. Dù API hiện đại hơn, lý do chọn cấu trúc này vẫn là **thứ tự sắp xếp**, không phải thứ tự chèn.
