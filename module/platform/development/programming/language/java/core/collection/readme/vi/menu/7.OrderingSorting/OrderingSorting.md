# Thứ tự, so sánh và điều hướng

Collection không chỉ trả lời câu hỏi “chứa phần tử nào”, mà nhiều khi còn phải trả lời “các phần tử xuất hiện theo thứ tự nào”. Thứ tự này có thể đến từ cấu trúc dữ liệu, từ thứ tự tự nhiên của phần tử, hoặc từ một quy tắc so sánh do bên gọi cung cấp. Ba nguồn này liên quan với nhau nhưng không đồng nghĩa.

Trong các ví dụ dưới đây, ta dùng một tập `Order` nhỏ để cùng quan sát nhiều kiểu thứ tự:

```java
record Order(long id, long userId, int totalCents) {}

List<Order> orders = new ArrayList<>(List.of(
        new Order(3, 2, 10_000),
        new Order(1, 1, 20_000),
        new Order(2, 3, 10_000)
));
```

## <a id="encounter-order">Thứ tự duyệt (encounter order)</a>

**Thứ tự duyệt** là thứ tự mà một thao tác tuần tự nhìn thấy các phần tử khi đi qua collection. Nó mô tả cách dữ liệu được gặp, không mặc định có nghĩa là “thứ tự thêm vào” hoặc “đã được sắp xếp”.

Ví dụ:

- `ArrayList` gặp phần tử theo thứ tự chỉ số.
- `LinkedHashSet` duy trì thứ tự gặp dựa trên thứ tự chèn.
- `TreeSet` gặp phần tử theo thứ tự sắp xếp của set.
- `HashSet` không cam kết một thứ tự duyệt cụ thể; không nên viết logic phụ thuộc vào thứ tự quan sát tình cờ của nó.

Java 21 đưa khái niệm này thành API rõ hơn qua `SequencedCollection`, `SequencedSet` và `SequencedMap`. Chẳng hạn `List` là một `SequencedCollection`, còn `SortedSet` là một `SequencedSet`. Các mô hình trừu tượng này định nghĩa khả năng truy cập đầu/cuối và **khung nhìn theo thứ tự ngược (reverse-ordered view)** qua `reversed()`. Các thao tác thay đổi ở đầu/cuối như `addFirst`, `addLast`, `removeFirst` hoặc `removeLast` là thao tác tùy chọn và vẫn có thể không được lớp triển khai cụ thể hỗ trợ.

```java
List<Order> original = new ArrayList<>(orders);
List<Order> reversed = original.reversed();

System.out.println(reversed.getFirst()); // phần tử cuối của original
```

`reversed()` trả về một khung nhìn theo thứ tự ngược, không phải bản sao độc lập. Thay đổi cấu trúc ở một phía có thể được quan sát từ phía còn lại nếu collection đó cho phép thay đổi. Vì vậy, khi API yêu cầu một bản chụp độc lập, cần tạo bản sao thay vì chỉ lấy khung nhìn.

Điểm quan trọng là **thứ tự duyệt là thuộc tính của collection/khung nhìn tại thời điểm duyệt**. Một `List` có thứ tự duyệt xác định nhưng chưa chắc đã “được sắp xếp”; `HashSet` có thể in ra cùng một thứ tự trong vài lần chạy nhưng vẫn không có quy ước bảo đảm thứ tự đó.

## <a id="natural-ordering">Thứ tự tự nhiên (natural ordering)</a>

Một kiểu có **thứ tự tự nhiên** khi chính kiểu đó định nghĩa cách so sánh các đối tượng của nó, thường qua `Comparable<T>` và `compareTo`. Thứ tự tự nhiên nên biểu diễn một thứ tự có ý nghĩa tương đối ổn định đối với bản thân kiểu, không phải một tiêu chí tạm thời của một màn hình hoặc báo cáo.

Ví dụ, nếu `Order` coi `id` là thứ tự tự nhiên:

```java
record Order(long id, long userId, int totalCents)
        implements Comparable<Order> {

    @Override
    public int compareTo(Order other) {
        return Long.compare(this.id, other.id);
    }
}

List<Order> orders = new ArrayList<>(List.of(
        new Order(3, 2, 10_000),
        new Order(1, 1, 20_000),
        new Order(2, 3, 10_000)
));

orders.sort(null); // null => dùng thứ tự tự nhiên
```

Thứ tự tự nhiên cũng là mặc định của nhiều cấu trúc cần thứ tự như `TreeSet`, `TreeMap` hoặc `PriorityQueue` khi không truyền `Comparator` riêng.

Một quy tắc thực tế là: `compareTo` nên tạo ra quan hệ so sánh nhất quán và bắc cầu. Với Set/Map có sắp xếp, việc thứ tự tự nhiên không nhất quán với `equals` có thể làm khái niệm “trùng” của collection khác với trực giác dựa trên `equals`. Hệ quả này được giải thích sâu hơn ở chương tính bằng nhau/hash; quy ước `equals/hashCode` tự thân thuộc module **Quy ước của Object** (`object-contract`).

## <a id="comparison-result-contract">Kết quả so sánh âm, 0 và dương có nghĩa gì?</a>

`Comparable.compareTo` và `Comparator.compare` không yêu cầu trả đúng `-1`, `0`, `1`. Quy ước chỉ quan tâm **dấu** của kết quả:

```text
compare(a, b) < 0
→ a đứng trước b theo thứ tự so sánh

compare(a, b) == 0
→ a và b bằng nhau theo thứ tự so sánh

compare(a, b) > 0
→ a đứng sau b theo thứ tự so sánh
```

Ví dụ:

```java
int result = Long.compare(orderA.id(), orderB.id());
```

Nếu `result < 0`, `orderA` đứng trước `orderB` theo id. Bên gọi không nên kiểm tra `result == -1` hay `result == 1`, vì cách triển khai được phép trả bất kỳ số âm/dương nào miễn đúng dấu.

Khi dùng thứ tự cho `TreeSet`/`TreeMap`, kết quả `0` còn ảnh hưởng tính duy nhất của phần tử/khóa. Vì vậy “bằng nhau theo thứ tự” là một khái niệm có hậu quả thực tế, không chỉ là chi tiết của thao tác sắp xếp.

## <a id="comparator-ordering">Sắp xếp bằng Comparator</a>

`Comparator<T>` đặt quy tắc sắp xếp **bên ngoài** kiểu dữ liệu. Đây là lựa chọn phù hợp khi cùng một đối tượng cần nhiều cách sắp xếp: theo tổng tiền, theo người dùng, theo thời gian, theo độ ưu tiên...

```java
Comparator<Order> byTotalDescendingThenId =
        Comparator.comparingInt(Order::totalCents)
                .reversed()
                .thenComparingLong(Order::id);

orders.sort(byTotalDescendingThenId);
```

Việc thêm tiêu chí phân xử khi bằng nhau như `thenComparingLong(Order::id)` giúp tạo thứ tự tổng thể rõ ràng khi tiêu chí chính bằng nhau. Cùng một `Comparator` có thể được truyền cho `List.sort`, `TreeSet`, `TreeMap` hoặc `PriorityQueue`, nhưng ý nghĩa của kết quả phụ thuộc vào cấu trúc:

- Với `List.sort`, comparator quyết định thứ tự sau thao tác sắp xếp.
- Với `TreeSet`, kết quả so sánh bằng `0` còn xác định hai phần tử có cùng vị trí logic trong set.
- Với `TreeMap`, comparator xác định thứ tự và tính duy nhất của khóa theo quan hệ so sánh.
- Với `PriorityQueue`, comparator xác định phần tử nào có ưu tiên ở đầu Queue; iterator của Queue không vì thế mà trả toàn bộ phần tử theo thứ tự đã sắp xếp.

Do đó comparator không chỉ là “hàm để sắp xếp”. Khi đưa nó vào collection có sắp xếp, nó trở thành một phần của quy ước nhận dạng và tổ chức dữ liệu của collection.

## <a id="navigable-range-operations">Điều hướng và khung nhìn theo phạm vi sau khi đã hiểu thứ tự</a>

Sau khi người học đã hiểu `Set`, `Map` và thứ tự, các API của `NavigableSet`/`NavigableMap` mới có ý nghĩa đầy đủ.

```java
NavigableSet<Integer> ids = new TreeSet<>(List.of(10, 20, 30, 40));

System.out.println(ids.lower(20));   // 10: < 20
System.out.println(ids.floor(25));   // 20: <= 25
System.out.println(ids.ceiling(25)); // 30: >= 25
System.out.println(ids.higher(30));  // 40: > 30
```

Khung nhìn theo phạm vi cho phép nhìn một đoạn của tập đã sắp xếp mà không phải sao chép toàn bộ dữ liệu:

```java
NavigableSet<Integer> middle = ids.subSet(20, true, 40, false); // [20, 30]
NavigableSet<Integer> head = ids.headSet(30, true);              // <= 30
NavigableSet<Integer> tail = ids.tailSet(20, false);             // > 20
```

Các kết quả này là **các khung nhìn liên kết với dữ liệu gốc**. Thay đổi dữ liệu hợp lệ qua khung nhìn tác động lên nguồn, và nguồn thay đổi cũng có thể được khung nhìn quan sát theo quy ước.

Nhánh `Map` áp dụng cùng mô hình tư duy cho **khóa**:

```java
NavigableMap<Long, Order> byId = new TreeMap<>();
byId.put(10L, orderA);
byId.put(20L, orderB);
byId.put(30L, orderC);

Map.Entry<Long, Order> floor = byId.floorEntry(25L); // key 20
NavigableMap<Long, Order> firstTwo =
        byId.subMap(10L, true, 30L, false);           // keys 10, 20
```

`lower/floor/ceiling/higher` trả lời câu hỏi “phần tử/khóa gần nhất ở phía nào?”, còn `subSet/subMap/head*/tail*` tạo các khung nhìn theo phạm vi. Đây là lý do chọn cấu trúc Sorted/Navigable khi bài toán thực sự cần điều hướng theo thứ tự, không chỉ vì muốn kết quả “trông đã được sắp xếp”.

## <a id="stable-sort">Sắp xếp ổn định và cách xử lý phần tử bằng nhau</a>

Một phép sắp xếp là **ổn định (stable)** nếu các phần tử mà comparator xem là bằng nhau giữ nguyên thứ tự tương đối ban đầu của chúng. `List.sort` và `Collections.sort` trong Java được quy định là ổn định.

```java
List<Order> orders = new ArrayList<>(List.of(
        new Order(3, 2, 10_000),
        new Order(1, 1, 20_000),
        new Order(2, 3, 10_000)
));

orders.sort(Comparator.comparingInt(Order::totalCents));

// Hai order 3 và 2 cùng totalCents = 10_000.
// Sau khi sắp xếp, order 3 vẫn đứng trước order 2 vì phép sắp xếp ổn định.
```

Sắp xếp ổn định hữu ích khi dữ liệu đã có một thứ tự phụ từ trước. Ví dụ, ta có thể sắp theo thời gian trước, sau đó sắp xếp ổn định theo trạng thái để giữ thứ tự thời gian bên trong từng nhóm trạng thái.

Cần phân biệt sắp xếp ổn định với collection có sắp xếp. `TreeSet` không “giữ cả hai phần tử bằng nhau rồi nhớ thứ tự cũ”; nếu comparator trả `0` cho hai phần tử, Set coi chúng là tương đương theo thứ tự và chỉ giữ một đại diện. Nếu nghiệp vụ cần giữ cả hai, comparator của Set phải có tiêu chí phân xử phù hợp hoặc cần chọn cấu trúc khác.

## <a id="collections-utility-algorithms">Các thuật toán tiện ích trong Collections</a>

`Collections` là **lớp tiện ích**, không phải interface `Collection<E>`. Nó chứa nhiều thuật toán tĩnh làm việc trên collection/List đã có. Không cần học thuộc toàn bộ API; các thao tác đại diện giúp thấy vai trò của lớp tiện ích này:

```java
List<Integer> values = new ArrayList<>(List.of(30, 10, 20));

Collections.sort(values);          // [10, 20, 30]
Collections.reverse(values);       // [30, 20, 10]
Collections.shuffle(values);       // đổi thứ tự ngẫu nhiên

int min = Collections.min(values);
int max = Collections.max(values);
```

Với mã nguồn hiện đại, `list.sort(comparator)` thường trực tiếp hơn `Collections.sort(list, comparator)`, nhưng cả hai đều cho thấy cùng ý tưởng: **thuật toán có thể tách khỏi lớp triển khai List cụ thể**.

`binarySearch` có điều kiện tiên quyết quan trọng: List phải được sắp theo **cùng thứ tự** dùng để tìm kiếm.

```java
List<Integer> sorted = new ArrayList<>(List.of(10, 20, 30, 40));
int index = Collections.binarySearch(sorted, 30); // 2
```

Nếu List chưa được sắp xếp hoặc thứ tự dùng khi tìm kiếm không khớp thứ tự đã sắp, kết quả không có ý nghĩa. Khi không tìm thấy, phương thức trả một giá trị âm mã hóa vị trí chèn; bên gọi không nên hiểu mọi số âm đơn giản là “chỉ số -1”.

Các tiện ích như `reverse`, `shuffle`, `sort` thay đổi List nếu List hỗ trợ thay đổi dữ liệu. Với List không cho phép sửa như `List.of(...)`, tiện ích thay đổi dữ liệu có thể ném `UnsupportedOperationException`. Đây là lý do luôn phải đọc **cả quy ước của thuật toán lẫn quy ước về khả năng thay đổi của collection đầu vào**.
