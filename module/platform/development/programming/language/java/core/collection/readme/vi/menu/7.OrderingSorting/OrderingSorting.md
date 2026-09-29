# Ordering và Sorting

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

## <a id="encounter-order">Thứ tự duyệt (Encounter Order)</a>

**Thứ tự duyệt** là thứ tự mà một thao tác tuần tự nhìn thấy các phần tử khi đi qua collection. Nó mô tả cách dữ liệu được gặp, không mặc định có nghĩa là “thứ tự thêm vào” hoặc “đã được sắp xếp”.

Ví dụ:

- `ArrayList` gặp phần tử theo thứ tự chỉ số.
- `LinkedHashSet` duy trì thứ tự gặp dựa trên thứ tự chèn.
- `TreeSet` gặp phần tử theo thứ tự sắp xếp của set.
- `HashSet` không cam kết một thứ tự duyệt cụ thể; không nên viết logic phụ thuộc vào thứ tự quan sát tình cờ của nó.

Java 21 đưa khái niệm này thành API rõ hơn qua `SequencedCollection`, `SequencedSet` và `SequencedMap`. Chẳng hạn `List` là một `SequencedCollection`, còn `SortedSet` là một `SequencedSet`. Các abstraction này cho phép thao tác với đầu/cuối và tạo **khung nhìn theo thứ tự ngược (reverse-ordered view)** bằng `reversed()` khi kiểu cụ thể hỗ trợ.

```java
List<Order> original = new ArrayList<>(orders);
List<Order> reversed = original.reversed();

System.out.println(reversed.getFirst()); // phần tử cuối của original
```

`reversed()` trả về một khung nhìn theo thứ tự ngược, không phải bản sao độc lập. Thay đổi cấu trúc ở một phía có thể được quan sát từ phía còn lại nếu collection đó cho phép thay đổi. Vì vậy, khi API yêu cầu một bản chụp độc lập, cần tạo bản sao thay vì chỉ lấy khung nhìn.

Điểm quan trọng là **encounter order là thuộc tính của collection/khung nhìn tại thời điểm duyệt**. Một `List` có encounter order xác định nhưng chưa chắc đã “sorted”; `HashSet` có thể in ra cùng một thứ tự trong vài lần chạy nhưng vẫn không có quy ước bảo đảm thứ tự đó.

## <a id="natural-ordering">Thứ tự tự nhiên (Natural Ordering)</a>

Một kiểu có **thứ tự tự nhiên** khi chính kiểu đó định nghĩa cách so sánh các instance của nó, thường qua `Comparable<T>` và `compareTo`. Natural ordering nên biểu diễn một thứ tự có ý nghĩa tương đối ổn định đối với bản thân kiểu, không phải một tiêu chí tạm thời của một màn hình hoặc báo cáo.

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

orders.sort(null); // null => dùng natural ordering
```

Natural ordering cũng là mặc định của nhiều cấu trúc cần thứ tự như `TreeSet`, `TreeMap` hoặc `PriorityQueue` khi không truyền `Comparator` riêng.

Một quy tắc thực tế là: `compareTo` nên tạo ra quan hệ so sánh nhất quán và bắc cầu. Với sorted set/map, việc natural ordering không nhất quán với `equals` có thể làm khái niệm “trùng” của collection khác với trực giác dựa trên `equals`. Hệ quả này được giải thích sâu hơn ở chương Equality/Hashing; quy ước `equals/hashCode` tự thân thuộc module `object-contract`.

## <a id="comparison-result-contract">Kết quả so sánh âm, 0 và dương có nghĩa gì?</a>

`Comparable.compareTo` và `Comparator.compare` không yêu cầu trả đúng `-1`, `0`, `1`. Contract chỉ quan tâm **dấu** của kết quả:

```text
compare(a, b) < 0
→ a đứng trước b theo ordering

compare(a, b) == 0
→ a và b bằng nhau theo ordering

compare(a, b) > 0
→ a đứng sau b theo ordering
```

Ví dụ:

```java
int result = Long.compare(orderA.id(), orderB.id());
```

Nếu `result < 0`, `orderA` đứng trước `orderB` theo id. Caller không nên kiểm tra `result == -1` hay `result == 1`, vì implementation được phép trả bất kỳ số âm/dương nào miễn đúng dấu.

Khi dùng ordering cho `TreeSet`/`TreeMap`, kết quả `0` còn ảnh hưởng uniqueness của phần tử/key. Vì vậy “bằng nhau theo ordering” là một khái niệm có hậu quả thực tế, không chỉ là chi tiết của sort.

## <a id="comparator-ordering">Sắp xếp bằng Comparator</a>

`Comparator<T>` đặt quy tắc sắp xếp **bên ngoài** kiểu dữ liệu. Đây là lựa chọn phù hợp khi cùng một đối tượng cần nhiều cách sắp xếp: theo tổng tiền, theo người dùng, theo thời gian, theo độ ưu tiên...

```java
Comparator<Order> byTotalDescendingThenId =
        Comparator.comparingInt(Order::totalCents)
                .reversed()
                .thenComparingLong(Order::id);

orders.sort(byTotalDescendingThenId);
```

Việc thêm tie-breaker như `thenComparingLong(Order::id)` giúp tạo thứ tự tổng thể rõ ràng khi tiêu chí chính bằng nhau. Cùng một `Comparator` có thể được truyền cho `List.sort`, `TreeSet`, `TreeMap` hoặc `PriorityQueue`, nhưng ý nghĩa của kết quả phụ thuộc vào cấu trúc:

- Với `List.sort`, comparator quyết định thứ tự sau thao tác sort.
- Với `TreeSet`, kết quả so sánh bằng `0` còn xác định hai phần tử có cùng vị trí logic trong set.
- Với `TreeMap`, comparator xác định thứ tự và tính duy nhất của key theo quan hệ so sánh.
- Với `PriorityQueue`, comparator xác định phần tử nào có ưu tiên ở head; iterator của queue không vì thế mà trả toàn bộ phần tử theo thứ tự đã sort.

Do đó comparator không chỉ là “hàm để sort”. Khi đưa nó vào sorted collection, nó trở thành một phần của quy ước nhận dạng và tổ chức dữ liệu của collection.

## <a id="navigable-range-operations">Navigation và range view sau khi đã hiểu ordering</a>

Sau khi learner đã hiểu `Set`, `Map` và ordering, các API của `NavigableSet`/`NavigableMap` mới có ý nghĩa đầy đủ.

```java
NavigableSet<Integer> ids = new TreeSet<>(List.of(10, 20, 30, 40));

System.out.println(ids.lower(20));   // 10: < 20
System.out.println(ids.floor(25));   // 20: <= 25
System.out.println(ids.ceiling(25)); // 30: >= 25
System.out.println(ids.higher(30));  // 40: > 30
```

Range view cho phép nhìn một đoạn của tập sorted mà không phải copy toàn bộ dữ liệu:

```java
NavigableSet<Integer> middle = ids.subSet(20, true, 40, false); // [20, 30]
NavigableSet<Integer> head = ids.headSet(30, true);              // <= 30
NavigableSet<Integer> tail = ids.tailSet(20, false);             // > 20
```

Các result này là **backed views**. Mutation hợp lệ qua view tác động lên source, và source thay đổi cũng có thể được view quan sát theo contract.

Nhánh `Map` áp dụng cùng mental model cho **key**:

```java
NavigableMap<Long, Order> byId = new TreeMap<>();
byId.put(10L, orderA);
byId.put(20L, orderB);
byId.put(30L, orderC);

Map.Entry<Long, Order> floor = byId.floorEntry(25L); // key 20
NavigableMap<Long, Order> firstTwo =
        byId.subMap(10L, true, 30L, false);           // keys 10, 20
```

`lower/floor/ceiling/higher` trả lời câu hỏi “phần tử/key gần nhất ở phía nào?”, còn `subSet/subMap/head*/tail*` tạo các range view. Đây là lý do chọn sorted/navigable structure khi bài toán thực sự cần navigation theo thứ tự, không chỉ vì muốn output “trông đã sort”.

## <a id="stable-sort">Stable Sort và cách xử lý phần tử bằng nhau</a>

Một phép sort là **ổn định (stable)** nếu các phần tử mà comparator xem là bằng nhau giữ nguyên thứ tự tương đối ban đầu của chúng. `List.sort` và `Collections.sort` trong Java được quy định là stable.

```java
List<Order> orders = new ArrayList<>(List.of(
        new Order(3, 2, 10_000),
        new Order(1, 1, 20_000),
        new Order(2, 3, 10_000)
));

orders.sort(Comparator.comparingInt(Order::totalCents));

// Hai order 3 và 2 cùng totalCents = 10_000.
// Sau sort, order 3 vẫn đứng trước order 2 vì sort ổn định.
```

Stable sort hữu ích khi dữ liệu đã có một thứ tự phụ từ trước. Ví dụ, ta có thể sort theo thời gian trước, sau đó stable-sort theo trạng thái để giữ thứ tự thời gian bên trong từng nhóm trạng thái.

Cần phân biệt stable sort với sorted collection. `TreeSet` không “giữ cả hai phần tử bằng nhau rồi nhớ thứ tự cũ”; nếu comparator trả `0` cho hai phần tử, set coi chúng là tương đương theo ordering và chỉ giữ một đại diện. Nếu nghiệp vụ cần giữ cả hai, comparator của set phải có tie-breaker phù hợp hoặc cần chọn cấu trúc khác.

## <a id="collections-utility-algorithms">Utility algorithms trong Collections</a>

`Collections` là **utility class**, không phải `Collection<E>` interface. Nó chứa nhiều static algorithm làm việc trên collection/list đã có. Không cần học thuộc toàn bộ API; các operation đại diện giúp thấy vai trò của layer này:

```java
List<Integer> values = new ArrayList<>(List.of(30, 10, 20));

Collections.sort(values);          // [10, 20, 30]
Collections.reverse(values);       // [30, 20, 10]
Collections.shuffle(values);       // đổi thứ tự ngẫu nhiên

int min = Collections.min(values);
int max = Collections.max(values);
```

Với code hiện đại, `list.sort(comparator)` thường trực tiếp hơn `Collections.sort(list, comparator)`, nhưng cả hai đều cho thấy cùng ý tưởng: **algorithm có thể tách khỏi concrete list implementation**.

`binarySearch` có precondition quan trọng: list phải được sắp theo **cùng ordering** dùng cho search.

```java
List<Integer> sorted = new ArrayList<>(List.of(10, 20, 30, 40));
int index = Collections.binarySearch(sorted, 30); // 2
```

Nếu list chưa sorted hoặc ordering dùng khi search không khớp ordering đã sort, kết quả không có ý nghĩa. Khi không tìm thấy, method trả một giá trị âm mã hóa insertion point; caller không nên hiểu mọi số âm đơn giản là “index -1”.

Các utility như `reverse`, `shuffle`, `sort` thay đổi list nếu list hỗ trợ mutation. Với list unmodifiable như `List.of(...)`, mutation utility có thể ném `UnsupportedOperationException`. Đây là lý do luôn phải đọc **cả algorithm contract lẫn mutability contract của collection đầu vào**.
