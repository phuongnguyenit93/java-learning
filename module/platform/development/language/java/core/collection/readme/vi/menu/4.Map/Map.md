# Map

Sau `List` và `Set`, ta vẫn còn một nhu cầu rất phổ biến mà `Collection<E>` không biểu đạt tốt: tìm một value bằng một **key riêng**. Ví dụ, hệ thống có nhiều `Order` nhưng request thường đưa vào `orderId`. `Map<K, V>` mô hình hóa trực tiếp quan hệ đó.

## <a id="map-semantics">Ngữ nghĩa của Map</a>

`Map<K, V>` lưu các mapping `key → value`. Mỗi key chỉ xuất hiện tối đa một lần, trong khi nhiều key có thể trỏ tới các value bằng nhau.

```java
Map<Long, Order> orderById = new HashMap<>();

orderById.put(1001L, new Order(1001, 7, 120));
orderById.put(1002L, new Order(1002, 8, 90));

Order order = orderById.get(1002L);
```

Nếu `put` một key đã tồn tại, mapping cũ bị thay thế và method trả về value trước đó:

```java
Order previous = orderById.put(
        1002L,
        new Order(1002, 8, 110)
);
```

Điều này khác `Set`: `Set` hỏi “element này đã tồn tại chưa?”, còn `Map` hỏi “key này đang ánh xạ tới value nào?”.

## <a id="map-basic-contract">Các thao tác cơ bản của Map</a>

Vì `Map` không kế thừa `Collection`, nó có vocabulary cơ bản riêng. Một learner mới nên nắm nhóm operation này trước khi đi tới `compute`/`merge`:

| Ý định | Operation |
| --- | --- |
| số mapping | `size()` |
| map có rỗng không | `isEmpty()` |
| đọc value theo key | `get(key)` |
| thêm/thay mapping | `put(key, value)` |
| có key hay không | `containsKey(key)` |
| có value hay không | `containsValue(value)` |
| xóa mapping theo key | `remove(key)` |
| xóa toàn bộ mapping | `clear()` |
| copy toàn bộ mapping từ map khác | `putAll(otherMap)` |

```java
Map<Long, Order> orders = new HashMap<>();
orders.put(1001L, orderA);
orders.put(1002L, orderB);

boolean has1001 = orders.containsKey(1001L);
boolean containsOrderB = orders.containsValue(orderB);
Order found = orders.get(1002L);

orders.remove(1001L);
```

`containsKey` thường quan trọng hơn `containsValue`: lookup theo key là abstraction chính của `Map`, còn tìm theo value thường phải kiểm tra nhiều mapping và không phải lý do chính để chọn map.

## <a id="map-entry-model">Map.Entry là một mapping key → value</a>

`Map.Entry<K, V>` đại diện cho **một mapping riêng lẻ** bên trong map: một key đi cùng value hiện tại của nó.

```java
for (Map.Entry<Long, Order> entry : orderById.entrySet()) {
    Long id = entry.getKey();
    Order order = entry.getValue();
    System.out.println(id + " -> " + order);
}
```

Khi thuật toán cần cả key lẫn value, `entrySet()` cho đúng đơn vị dữ liệu cần duyệt và tránh pattern “duyệt key rồi lại `get(key)`”. Một số entry view cho phép `setValue`, nhưng đó là optional/mutable-view behavior của map cụ thể; code không nên giả định mọi `Map.Entry` đều sửa được.

Ba collection view giúp duyệt map theo các góc khác nhau:

```java
Set<Long> keys = orderById.keySet();
Collection<Order> values = orderById.values();
Set<Map.Entry<Long, Order>> entries = orderById.entrySet();
```

Khi cần cả key và value, duyệt `entrySet()` thường rõ ràng hơn gọi `get(key)` sau khi duyệt key:

```java
for (Map.Entry<Long, Order> entry : orderById.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}
```

Chính sách `null` và ordering phụ thuộc cách triển khai. `HashMap` cho phép một null key và nhiều null values; `Map.of(...)` từ chối cả hai. Không nên dùng `get(key) == null` để phân biệt “không có mapping” với “mapping tới null” nếu cách triển khai cho phép null value; khi cần phân biệt, dùng `containsKey`.

`Map` cơ bản không hứa một encounter order chung. Từ Java 21, `SequencedMap` **chuẩn hóa** vocabulary first/last/reversed cho những map tham gia abstraction này. Tuy nhiên “có order xác định” không đồng nghĩa “phải là `SequencedMap`”: `EnumMap`, chẳng hạn, duyệt theo thứ tự khai báo enum nhưng không triển khai `SequencedMap`.

## <a id="map-views">keySet, values và entrySet là backed views</a>

`keySet()`, `values()` và `entrySet()` không mặc định tạo ba collection độc lập. Chúng là **view được backing bởi map gốc**: thay đổi hợp lệ qua một phía có thể được quan sát từ phía còn lại.

```java
Map<Long, String> names = new HashMap<>();
names.put(1L, "An");
names.put(2L, "Binh");

Set<Long> keys = names.keySet();
keys.remove(1L);

System.out.println(names.containsKey(1L)); // false

names.put(3L, "Chi");
System.out.println(keys.contains(3L));      // true
```

Các view không có mọi mutation operation. Ví dụ `keySet().add(key)` không có đủ information để tạo một mapping vì không biết value, nên operation đó không được hỗ trợ. Khi cần snapshot độc lập, copy rõ ràng:

```java
Set<Long> keySnapshot = Set.copyOf(names.keySet());
List<String> valueSnapshot = List.copyOf(names.values());
```

Đây là cùng family khái niệm với `List.subList`: **view chia sẻ backing state**, còn copy có structural state riêng.

## <a id="map-default-operations">Các thao tác Map thường dùng trước compute/merge</a>

Trước khi cần `compute` hoặc `merge`, nhiều logic map được diễn đạt đủ rõ bằng các operation mức đơn giản hơn:

```java
Order fallback = new Order(-1, -1, 0);
Order found = orderById.getOrDefault(1001L, fallback);

orderById.putIfAbsent(1003L, new Order(1003, 9, 75));
orderById.replace(1003L, new Order(1003, 9, 80));

boolean replaced = orderById.replace(
        1003L,
        new Order(1003, 9, 80),
        new Order(1003, 9, 85)
);

boolean removed = orderById.remove(1003L, new Order(1003, 9, 85));
```

- `getOrDefault` chỉ cung cấp fallback khi key **không có mapping**; nếu key tồn tại và đang map tới `null`, kết quả vẫn là `null`; method không insert fallback;
- `putIfAbsent` chỉ ghi khi key chưa có mapping non-null;
- `replace` chỉ thay khi key đang tồn tại theo overload tương ứng;
- `remove(key, value)` chỉ xóa khi cả key và value hiện tại match.

Các default method này làm intent rõ hơn pattern `containsKey → get → put/remove`. Trong môi trường nhiều thread, không suy ra atomicity từ interface `Map`; concurrent implementation có contract riêng thuộc module concurrency.

## <a id="map-equality">Equality của Map dựa trên các mapping</a>

Hai `Map` bằng nhau khi chúng biểu diễn cùng tập mapping `key → value`. Encounter order và concrete implementation không quyết định `Map.equals`.

```java
Map<Long, String> a = new HashMap<>();
a.put(1L, "A");
a.put(2L, "B");

Map<Long, String> b = new LinkedHashMap<>();
b.put(2L, "B");
b.put(1L, "A");

System.out.println(a.equals(b)); // true
```

Equality của key/value bên trong vẫn dựa vào equality contract của chính chúng. Chi tiết thiết kế `equals/hashCode` thuộc module Object Contract; ở đây cần nhớ rằng thay đổi ordering implementation không tự làm hai map khác value nếu mappings vẫn giống nhau.

## <a id="hashmap-model">Mô hình HashMap</a>

`HashMap` là implementation tổng quát khi cần key lookup nhanh và không cần encounter order cụ thể.

Mental model:

```text
key
 ↓ hashCode()
chọn vùng/bucket ứng viên
 ↓
equals() xác nhận key tương đương
 ↓
value tương ứng
```

```java
Map<Long, Order> orderById = new HashMap<>();
for (Order order : orders) {
    orderById.put(order.id(), order);
}

Order found = orderById.get(1002L);
```

Với hash distribution hợp lý, `put`, `get` và `remove` có expected cost gần O(1). Đây là đặc tính kỳ vọng của hash table, không phải đảm bảo worst-case tuyệt đối cho mọi key và mọi trạng thái.

Giống `HashSet`, correctness phụ thuộc vào key giữ `equals/hashCode` ổn định trong thời gian nó nằm trong map. Nếu field tham gia hash thay đổi sau `put`, lookup bằng key có thể không tìm được mapping như mong đợi.

```java
record OrderKey(long id) {}

Map<OrderKey, Order> map = new HashMap<>();
map.put(new OrderKey(1001), order);
```

Record là ví dụ thuận tiện vì key identity của nó là value-based và immutable. Không bắt buộc key phải là record; điều quan trọng là equality/hash contract phù hợp và ổn định.

`HashMap` không đảm bảo iteration order. Nếu output đang vô tình trùng với insertion order, đó vẫn không phải contract.

## <a id="linkedhashmap-order">Thứ tự của LinkedHashMap</a>

`LinkedHashMap` giữ mapping như hash map nhưng đồng thời duy trì encounter order. Có hai mode quan trọng.

### Insertion order

Constructor thông thường giữ thứ tự key được chèn lần đầu:

```java
Map<Long, Order> orders = new LinkedHashMap<>();
orders.put(30L, order30);
orders.put(10L, order10);
orders.put(20L, order20);

System.out.println(orders.keySet()); // [30, 10, 20]
```

`put` lại value cho một key đã có không tự động biến nó thành key mới ở cuối theo insertion-order semantics thông thường.

### Access order

Constructor có tham số `accessOrder = true` dùng thứ tự truy cập thay vì thứ tự chèn:

```java
Map<Long, Order> recent = new LinkedHashMap<>(16, 0.75f, true);
recent.put(1L, order1);
recent.put(2L, order2);
recent.put(3L, order3);

recent.get(1L);
System.out.println(recent.keySet()); // [2, 3, 1]
```

Mode này là nền tảng hữu ích cho một số cache/LRU patterns, nhưng cache production còn có eviction, concurrency và memory policy riêng; `LinkedHashMap` chỉ cung cấp cơ chế ordering nền.

Trong Java 21, `LinkedHashMap` triển khai `SequencedMap`. API có thể truy cập entry đầu/cuối, explicit positioning và reversed view mà không cần tự chuyển qua key list. Điều này làm encounter order trở thành một capability rõ ràng trong type system.

## <a id="treemap-order">Thứ tự của TreeMap</a>

`TreeMap` duy trì mapping theo **sorted order của key**.

```java
Map<Long, Order> orders = new TreeMap<>();
orders.put(30L, order30);
orders.put(10L, order10);
orders.put(20L, order20);

System.out.println(orders.keySet()); // [10, 20, 30]
```

Key được sắp theo natural ordering hoặc theo `Comparator` truyền vào constructor.

```java
record UserKey(long id, String region) {}

Map<UserKey, User> users = new TreeMap<>(
        Comparator.comparing(UserKey::region)
                  .thenComparingLong(UserKey::id)
);
```

Giống `TreeSet`, kết quả comparison bằng 0 quyết định rằng hai key là cùng một vị trí logic đối với `TreeMap`. Nếu comparator bỏ qua field quan trọng, `put` key thứ hai có thể thay thế value của key thứ nhất.

`TreeMap` thường có O(log n) cho `get`, `put` và `remove`, đổi lại có các navigable operations như `firstEntry`, `lastEntry`, `lowerEntry`, `floorEntry`, `ceilingEntry` và `higherEntry`.

Với natural ordering thông thường, `TreeMap` không cho null key vì không thể so sánh nó theo contract đó. Null value vẫn có thể được lưu. Comparator tùy chỉnh có thể định nghĩa xử lý null key, nhưng API nên làm điều đó có chủ đích.

Trong Java 21, sorted-map hierarchy cũng tham gia `SequencedMap`, nên first/last/reversed semantics có interface chung. Lý do chọn `TreeMap` vẫn là nhu cầu **sorted lookup/navigation theo key**.

## <a id="map-compute-merge">Cập nhật bằng compute và merge</a>

Nhiều bài toán map có dạng “đọc value cũ, tính value mới rồi ghi lại”. `Map` có các method giúp diễn đạt trực tiếp pattern này.

Đếm số đơn hàng theo user:

```java
Map<Long, Integer> orderCountByUser = new HashMap<>();

for (Order order : orders) {
    orderCountByUser.merge(order.userId(), 1, Integer::sum);
}
```

`merge(key, value, remappingFunction)` yêu cầu `value` đưa vào là non-null:

- nếu chưa có mapping hoặc mapping hiện tại là `null`, dùng value được cung cấp;
- nếu đã có non-null value, gọi remapping function với old/new value;
- nếu remapping function trả `null`, mapping bị xóa.

Tạo collection theo key:

```java
Map<Long, List<Order>> ordersByUser = new HashMap<>();

for (Order order : orders) {
    ordersByUser
            .computeIfAbsent(order.userId(), id -> new ArrayList<>())
            .add(order);
}
```

`computeIfAbsent` chỉ tạo value khi key chưa có mapping non-null; nếu mapping function trả `null` thì không tạo mapping.

Khi cần tính lại dựa trên cả key và value hiện tại:

```java
totals.compute(userId, (id, current) ->
        current == null ? order.totalCents() : current + order.totalCents()
);
```

Nếu remapping function của `compute` trả `null`, mapping bị xóa.

Các API này làm logic cập nhật ngắn và gần với ý định hơn pattern `get → if → put`. Tuy nhiên không nên suy ra rằng mọi `Map` khiến chuỗi update trở thành atomic trong môi trường nhiều thread; atomicity cụ thể thuộc contract của implementation như `ConcurrentMap`/`ConcurrentHashMap` và thuộc curriculum concurrency.
