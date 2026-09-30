# Các mô hình dữ liệu cốt lõi — Map

Sau `List` và `Set`, ta vẫn còn một nhu cầu rất phổ biến mà `Collection<E>` không biểu đạt tốt: tìm một giá trị bằng một **khóa riêng**. Ví dụ, hệ thống có nhiều `Order` nhưng yêu cầu đầu vào thường cung cấp `orderId`. `Map<K, V>` mô hình hóa trực tiếp quan hệ đó.

## <a id="map-semantics">Ngữ nghĩa của Map</a>

`Map<K, V>` lưu các ánh xạ `khóa → giá trị`. Mỗi khóa chỉ xuất hiện tối đa một lần, trong khi nhiều khóa có thể trỏ tới các giá trị bằng nhau.

```java
Map<Long, Order> orderById = new HashMap<>();

orderById.put(1001L, new Order(1001, 7, 120));
orderById.put(1002L, new Order(1002, 8, 90));

Order order = orderById.get(1002L);
```

Nếu `put` một khóa đã tồn tại, ánh xạ cũ bị thay thế và phương thức trả về giá trị trước đó:

```java
Order previous = orderById.put(
        1002L,
        new Order(1002, 8, 110)
);
```

Điều này khác `Set`: `Set` hỏi “phần tử này đã tồn tại chưa?”, còn `Map` hỏi “khóa này đang ánh xạ tới giá trị nào?”.

## <a id="map-basic-contract">Các thao tác cơ bản của Map</a>

Vì `Map` không kế thừa `Collection`, nó có tập thao tác cơ bản riêng. Người học mới nên nắm nhóm thao tác này trước khi đi tới `compute`/`merge`:

| Ý định | thao tác |
| --- | --- |
| số ánh xạ | `size()` |
| map có rỗng không | `isEmpty()` |
| đọc giá trị theo khóa | `get(key)` |
| thêm/thay ánh xạ | `put(key, value)` |
| có khóa hay không | `containsKey(key)` |
| có giá trị hay không | `containsValue(value)` |
| xóa ánh xạ theo khóa | `remove(key)` |
| xóa toàn bộ ánh xạ | `clear()` |
| sao chép toàn bộ ánh xạ từ Map khác | `putAll(otherMap)` |

```java
Map<Long, Order> orders = new HashMap<>();
orders.put(1001L, orderA);
orders.put(1002L, orderB);

boolean has1001 = orders.containsKey(1001L);
boolean containsOrderB = orders.containsValue(orderB);
Order found = orders.get(1002L);

orders.remove(1001L);
```

`containsKey` thường quan trọng hơn `containsValue`: tra cứu theo khóa là mô hình trừu tượng chính của `Map`, còn tìm theo giá trị thường phải kiểm tra nhiều ánh xạ và không phải lý do chính để chọn Map.

## <a id="map-entry-model">Map.Entry biểu diễn một ánh xạ khóa → giá trị</a>

`Map.Entry<K, V>` đại diện cho **một ánh xạ riêng lẻ** bên trong Map: một khóa đi cùng giá trị hiện tại của nó.

```java
for (Map.Entry<Long, Order> entry : orderById.entrySet()) {
    Long id = entry.getKey();
    Order order = entry.getValue();
    System.out.println(id + " -> " + order);
}
```

Khi thuật toán cần cả khóa lẫn giá trị, `entrySet()` cho đúng đơn vị dữ liệu cần duyệt và tránh mẫu “duyệt khóa rồi lại `get(key)`”. Một số entry dạng khung nhìn cho phép `setValue`, nhưng đây là thao tác tùy chọn và phụ thuộc quy ước của Map cụ thể; mã nguồn không nên giả định mọi `Map.Entry` đều sửa được.

Ba khung nhìn dạng Collection giúp duyệt Map theo các góc khác nhau:

```java
Set<Long> keys = orderById.keySet();
Collection<Order> values = orderById.values();
Set<Map.Entry<Long, Order>> entries = orderById.entrySet();
```

Khi cần cả khóa và giá trị, duyệt `entrySet()` thường rõ ràng hơn gọi `get(key)` sau khi duyệt khóa:

```java
for (Map.Entry<Long, Order> entry : orderById.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}
```

Chính sách `null` và thứ tự phụ thuộc cách triển khai. `HashMap` cho phép một khóa `null` và nhiều giá trị `null`; `Map.of(...)` từ chối cả hai. Không nên dùng `get(key) == null` để phân biệt “không có ánh xạ” với “ánh xạ tới null” nếu cách triển khai cho phép giá trị `null`; khi cần phân biệt, dùng `containsKey`.

`Map` cơ bản không hứa một thứ tự duyệt chung. Từ Java 21, `SequencedMap` **chuẩn hóa** nhóm thao tác `first`/`last`/`reversed` cho những Map tham gia mô hình trừu tượng này. Tuy nhiên “có thứ tự xác định” không đồng nghĩa “phải là `SequencedMap`”: `EnumMap`, chẳng hạn, duyệt theo thứ tự khai báo enum nhưng không triển khai `SequencedMap`.

## <a id="map-views">keySet, values và entrySet là các khung nhìn liên kết với Map</a>

`keySet()`, `values()` và `entrySet()` không mặc định tạo ba collection độc lập. Chúng là **khung nhìn liên kết với Map gốc**: thay đổi hợp lệ qua một phía có thể được quan sát từ phía còn lại.

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

Các khung nhìn không hỗ trợ mọi thao tác thay đổi dữ liệu. Ví dụ `keySet().add(key)` không có đủ thông tin để tạo một ánh xạ vì không biết giá trị, nên thao tác đó không được hỗ trợ. Khi cần bản chụp độc lập, hãy tạo bản sao rõ ràng:

```java
Set<Long> keySnapshot = Set.copyOf(names.keySet());
List<String> valueSnapshot = List.copyOf(names.values());
```

Đây là cùng một nhóm khái niệm với `List.subList`: **khung nhìn chia sẻ trạng thái với nguồn**, còn bản sao có trạng thái cấu trúc riêng.

## <a id="map-default-operations">Các thao tác Map thường dùng trước compute/merge</a>

Trước khi cần `compute` hoặc `merge`, nhiều logic Map được diễn đạt đủ rõ bằng các thao tác mức đơn giản hơn:

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

- `getOrDefault` chỉ cung cấp giá trị dự phòng khi khóa **không có ánh xạ**; nếu khóa tồn tại và đang ánh xạ tới `null`, kết quả vẫn là `null`; phương thức không chèn giá trị dự phòng;
- `putIfAbsent` chỉ ghi khi khóa chưa có ánh xạ khác `null`;
- `replace` chỉ thay khi khóa đang tồn tại theo phiên bản nạp chồng tương ứng;
- `remove(key, value)` chỉ xóa khi cả khóa và giá trị hiện tại khớp.

Các phương thức mặc định này làm ý định rõ hơn mẫu `containsKey → get → put/remove`. Trong môi trường nhiều luồng, không suy ra tính nguyên tử từ interface `Map`; các cách triển khai đồng thời có quy ước riêng thuộc module về xử lý đồng thời.

## <a id="map-equality">Tính bằng nhau của Map dựa trên các ánh xạ</a>

Hai `Map` bằng nhau khi chúng biểu diễn cùng tập ánh xạ `khóa → giá trị`. Thứ tự duyệt và lớp triển khai cụ thể không quyết định `Map.equals`.

```java
Map<Long, String> a = new HashMap<>();
a.put(1L, "A");
a.put(2L, "B");

Map<Long, String> b = new LinkedHashMap<>();
b.put(2L, "B");
b.put(1L, "A");

System.out.println(a.equals(b)); // true
```

Tính bằng nhau của khóa/giá trị bên trong vẫn dựa vào quy tắc tính bằng nhau của chính chúng. Chi tiết thiết kế `equals/hashCode` thuộc module **Quy ước của Object**; ở đây cần nhớ rằng thay đổi cách triển khai thứ tự không tự làm hai Map khác nhau nếu các ánh xạ vẫn giống nhau.

## <a id="hashmap-model">Mô hình HashMap</a>

`HashMap` là cách triển khai tổng quát khi cần tra cứu theo khóa nhanh và không cần thứ tự duyệt cụ thể.

Mô hình tư duy:

```text
khóa
 ↓ hashCode()
chọn vùng/bucket ứng viên
 ↓
equals() xác nhận khóa tương đương
 ↓
giá trị tương ứng
```

```java
Map<Long, Order> orderById = new HashMap<>();
for (Order order : orders) {
    orderById.put(order.id(), order);
}

Order found = orderById.get(1002L);
```

Với phân bố hash hợp lý, `put`, `get` và `remove` có chi phí kỳ vọng gần O(1). Đây là đặc tính kỳ vọng của bảng băm, không phải bảo đảm tuyệt đối cho trường hợp xấu nhất với mọi khóa và mọi trạng thái.

Giống `HashSet`, tính đúng đắn phụ thuộc vào khóa giữ `equals/hashCode` ổn định trong thời gian nó nằm trong Map. Nếu trường tham gia hash thay đổi sau `put`, tra cứu bằng khóa có thể không tìm được ánh xạ như mong đợi.

```java
record OrderKey(long id) {}

Map<OrderKey, Order> map = new HashMap<>();
map.put(new OrderKey(1001), order);
```

Record là ví dụ thuận tiện vì định danh logic của khóa dựa trên giá trị và record là bất biến. Không bắt buộc khóa phải là record; điều quan trọng là quy ước tính bằng nhau/hash phải phù hợp và ổn định.

`HashMap` không đảm bảo thứ tự duyệt. Nếu kết quả đang vô tình trùng với thứ tự chèn, đó vẫn không phải quy ước.

## <a id="linkedhashmap-order">Thứ tự của LinkedHashMap</a>

`LinkedHashMap` giữ ánh xạ như HashMap nhưng đồng thời duy trì thứ tự duyệt. Có hai chế độ quan trọng.

### thứ tự chèn

Constructor thông thường giữ thứ tự khóa được chèn lần đầu:

```java
Map<Long, Order> orders = new LinkedHashMap<>();
orders.put(30L, order30);
orders.put(10L, order10);
orders.put(20L, order20);

System.out.println(orders.keySet()); // [30, 10, 20]
```

`put` lại giá trị cho một khóa đã có không tự động biến nó thành khóa mới ở cuối theo ngữ nghĩa thứ tự chèn thông thường.

### thứ tự truy cập

Constructor có tham số `accessOrder = true` dùng thứ tự truy cập thay vì thứ tự chèn:

```java
Map<Long, Order> recent = new LinkedHashMap<>(16, 0.75f, true);
recent.put(1L, order1);
recent.put(2L, order2);
recent.put(3L, order3);

recent.get(1L);
System.out.println(recent.keySet()); // [2, 3, 1]
```

Chế độ này là nền tảng hữu ích cho một số mẫu cache/LRU, nhưng cache dùng trong hệ thống thực tế còn có chính sách loại bỏ, xử lý đồng thời và bộ nhớ riêng; `LinkedHashMap` chỉ cung cấp cơ chế thứ tự nền.

Trong Java 21, `LinkedHashMap` triển khai `SequencedMap`. API có thể truy cập entry đầu/cuối, định vị rõ ràng và khung nhìn đảo thứ tự mà không cần tự chuyển qua List khóa. Điều này làm thứ tự duyệt trở thành một khả năng rõ ràng trong hệ thống kiểu.

## <a id="treemap-order">Thứ tự của TreeMap</a>

`TreeMap` duy trì ánh xạ theo **thứ tự sắp xếp của khóa**.

```java
Map<Long, Order> orders = new TreeMap<>();
orders.put(30L, order30);
orders.put(10L, order10);
orders.put(20L, order20);

System.out.println(orders.keySet()); // [10, 20, 30]
```

Khóa được sắp theo thứ tự tự nhiên hoặc theo `Comparator` truyền vào constructor.

```java
record UserKey(long id, String region) {}

Map<UserKey, User> users = new TreeMap<>(
        Comparator.comparing(UserKey::region)
                  .thenComparingLong(UserKey::id)
);
```

Giống `TreeSet`, kết quả so sánh bằng 0 quyết định rằng hai khóa là cùng một vị trí logic đối với `TreeMap`. Nếu comparator bỏ qua trường quan trọng, `put` khóa thứ hai có thể thay thế giá trị của khóa thứ nhất.

Vì vậy với Map có sắp xếp, thứ tự nên **nhất quán với `equals`**. Một `TreeMap` mà phép so sánh xem hai khóa không bằng nhau theo `equals` là tương đương vẫn có hành vi xác định, nhưng nó không còn tuân theo sạch quy ước chung về tính bằng nhau của `Map`, vì quan hệ tương đương giữa các khóa bên trong cây đang được quyết định bởi phép so sánh thay vì `equals`.

`TreeMap` thường có O(log n) cho `get`, `put` và `remove`, đổi lại có các thao tác điều hướng như `firstEntry`, `lastEntry`, `lowerEntry`, `floorEntry`, `ceilingEntry` và `higherEntry`.

Với thứ tự tự nhiên thông thường, `TreeMap` không cho null khóa vì không thể so sánh nó theo quy ước đó. Null giá trị vẫn có thể được lưu. Comparator tùy chỉnh có thể định nghĩa xử lý null khóa, nhưng API nên làm điều đó có chủ đích.

Trong Java 21, hệ phân cấp Map có sắp xếp cũng tham gia `SequencedMap`, nên ngữ nghĩa đầu/cuối/đảo thứ tự có interface chung. Lý do chọn `TreeMap` vẫn là nhu cầu **tra cứu/điều hướng theo khóa có sắp xếp**.

## <a id="map-compute-merge">Cập nhật bằng compute và merge</a>

Nhiều bài toán Map có dạng “đọc giá trị cũ, tính giá trị mới rồi ghi lại”. `Map` có các phương thức giúp diễn đạt trực tiếp mẫu này.

Đếm số đơn hàng theo người dùng:

```java
Map<Long, Integer> orderCountByUser = new HashMap<>();

for (Order order : orders) {
    orderCountByUser.merge(order.userId(), 1, Integer::sum);
}
```

`merge(key, value, remappingFunction)` yêu cầu `value` đưa vào khác `null`:

- nếu chưa có ánh xạ hoặc ánh xạ hiện tại là `null`, dùng giá trị được cung cấp;
- nếu đã có giá trị khác `null`, gọi hàm ánh xạ lại với giá trị cũ/mới;
- nếu hàm ánh xạ lại trả `null`, ánh xạ bị xóa.

Tạo collection theo khóa:

```java
Map<Long, List<Order>> ordersByUser = new HashMap<>();

for (Order order : orders) {
    ordersByUser
            .computeIfAbsent(order.userId(), id -> new ArrayList<>())
            .add(order);
}
```

`computeIfAbsent` chỉ tạo giá trị khi khóa chưa có ánh xạ khác `null`; nếu hàm ánh xạ trả `null` thì không tạo ánh xạ.

Khi cần tính lại dựa trên cả khóa và giá trị hiện tại:

```java
totals.compute(userId, (id, current) ->
        current == null ? order.totalCents() : current + order.totalCents()
);
```

Nếu hàm ánh xạ lại của `compute` trả `null`, ánh xạ bị xóa.

Các API này làm logic cập nhật ngắn và gần với ý định hơn mẫu `get → if → put`. Tuy nhiên không nên suy ra rằng mọi `Map` khiến chuỗi cập nhật trở thành nguyên tử trong môi trường nhiều luồng; tính nguyên tử cụ thể thuộc quy ước của các cách triển khai như `ConcurrentMap`/`ConcurrentHashMap` và thuộc module về xử lý đồng thời.
