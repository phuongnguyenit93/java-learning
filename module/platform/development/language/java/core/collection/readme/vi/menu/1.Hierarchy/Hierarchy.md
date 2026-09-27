# Hệ thống Java Collections

Trước khi học `List`, `Set`, `Map` hay `Queue`, cần trả lời một câu đơn giản hơn: **Collection là gì và tại sao chương trình Java lại cần nó?**

Giả sử chương trình chỉ có ba đơn hàng. Ta hoàn toàn có thể viết ba biến riêng:

```java
Order first = new Order(1001, 7, 120);
Order second = new Order(1002, 8, 90);
Order third = new Order(1003, 7, 150);
```

Nhưng dữ liệu thực tế hiếm khi dừng ở con số ba. Số lượng đơn hàng có thể là 0, 10, 1.000 hoặc thay đổi trong lúc chương trình chạy. Khi đó code cần một cách để **gom nhiều object lại thành một nhóm và thao tác với cả nhóm đó**: thêm phần tử, xóa phần tử, tìm kiếm, duyệt, kiểm tra số lượng, giữ thứ tự, loại trùng hoặc tra cứu theo khóa.

Đó là bài toán mà Java Collections Framework giải quyết.

Trong module này, hãy dùng một bộ dữ liệu nhỏ làm ví dụ xuyên suốt:

```java
record User(long id, String name) {}
record Order(long id, long userId, int totalCents) {}
```

Ta có thể giữ các `Order` theo thứ tự tạo, tập hợp các `userId` duy nhất, ánh xạ `orderId → Order`, hoặc đưa đơn hàng vào hàng đợi xử lý. Cùng là “nhiều phần tử”, nhưng mỗi bài toán có một hợp đồng khác nhau.

## <a id="collection-purpose">Collection là gì và vì sao cần nó?</a>

Ở mức tư duy cơ bản, một **collection** là một object dùng để quản lý **một nhóm phần tử** thay vì bắt code phải quản lý từng biến riêng lẻ.

Ví dụ:

```java
List<Order> orders = new ArrayList<>();

orders.add(new Order(1001, 7, 120));
orders.add(new Order(1002, 8, 90));
orders.add(new Order(1003, 7, 150));

System.out.println(orders.size());

for (Order order : orders) {
    System.out.println(order.id());
}
```

Thay vì phải tạo `order1`, `order2`, `order3`, ... và viết logic riêng cho từng biến, code làm việc với **một object đại diện cho cả nhóm**.

Đọc declaration này từ trái sang phải:

```java
List<Order> orders = new ArrayList<>();
```

- `List<Order>` nói biến tuân theo contract của `List` và kiểu phần tử là `Order`;
- `new ArrayList<>()` chọn `ArrayList` làm implementation cụ thể;
- diamond `<>` cho compiler suy ra `Order` từ kiểu bên trái, nên không cần lặp lại `new ArrayList<Order>()`.

Generic collection lưu **reference type**. Khi cần lưu primitive, Java dùng wrapper type tương ứng, ví dụ `List<Integer>` cho `int`, `List<Long>` cho `long`. Chi tiết boxing/unboxing thuộc Language Basics và Generics; ở đây chỉ cần nhận ra vì sao không viết `List<int>`.

### Vấn đề trước khi có collection

Array đã cho Java một cách lưu nhiều phần tử cùng kiểu:

```java
Order[] orders = new Order[100];
```

Array vẫn rất hữu ích khi kích thước cố định hoặc khi API/performance yêu cầu nó. Nhưng nếu dùng array cho dữ liệu động, application thường phải tự xử lý nhiều việc:

```text
Mảng đã đầy thì làm gì?
→ tự tạo mảng lớn hơn và copy dữ liệu

Muốn xóa phần tử ở giữa?
→ tự dịch các phần tử còn lại

Muốn không cho trùng?
→ tự viết logic kiểm tra

Muốn lookup theo id?
→ tự duyệt hoặc tự xây cấu trúc index

Muốn xử lý FIFO/LIFO?
→ tự quản lý vị trí đầu/cuối
```

Collections Framework cung cấp những cấu trúc đã chuẩn hóa để application không phải tự phát minh lại các cơ chế phổ biến đó.

### Java Collections Framework là gì?

**Java Collections Framework (JCF)** là tập hợp các interface, implementation và utility/algorithm chuẩn của Java để biểu diễn và thao tác với nhóm dữ liệu.

Mental model:

```text
Dữ liệu có nhiều phần tử
        ↓
Cần một object quản lý cả nhóm
        ↓
Collection Framework cung cấp các contract khác nhau
        ↓
List / Set / Queue / Deque / Map
        ↓
chọn contract theo hành vi bài toán cần
```

Ví dụ, cùng một tập `Order` nhưng nhu cầu khác nhau dẫn tới cấu trúc khác nhau:

```java
List<Order> ordersInArrivalOrder = new ArrayList<>();
Set<Long> uniqueUserIds = new HashSet<>();
Map<Long, Order> orderById = new HashMap<>();
Deque<Order> pendingOrders = new ArrayDeque<>();
```

Đây là lý do Collection không phải chỉ là “một cái List để chứa nhiều object”. Nó là **một họ abstraction cho nhiều kiểu bài toán quản lý nhóm dữ liệu**.

## <a id="collection-vs-collections">Collection, Collections và Collections Framework khác nhau thế nào?</a>

Ba tên gần giống nhau nhưng vai trò khác hẳn:

- `Collection<E>` là interface gốc cho các **nhóm phần tử** như `List`, `Set`, `Queue`;
- `Collections` là utility class chứa các static algorithm/helper như `sort`, `reverse`, `binarySearch` và các wrapper factory;
- **Java Collections Framework (JCF)** là toàn bộ hệ thống gồm interface, implementation và các algorithm/utility liên quan.

`Map<K, V>` thuộc Java Collections Framework nhưng không kế thừa `Collection<E>`; phần tiếp theo sẽ giải thích tại sao.

Sau khi hiểu **vì sao cần một collection**, hierarchy mới có ý nghĩa: nó trả lời câu hỏi **“nhóm dữ liệu này cần hành vi nào?”**.

Một lộ trình học hợp lý cho toàn module là:

```text
Cần giữ một nhóm dữ liệu
        ↓
Có cần vị trí/thứ tự và chấp nhận trùng? → List
        ↓
Có cần duy nhất?                         → Set
        ↓
Có cần tra cứu bằng khóa?                → Map
        ↓
Có cần xử lý theo đầu/cuối/ưu tiên?       → Queue / Deque
        ↓
Cần duyệt mà không phụ thuộc cách triển khai? → Iterator
        ↓
Sau đó mới xét ordering, equality/hashCode, mutability,
fail-fast và chi phí của từng cách triển khai
```

Ở cuối module, mục tiêu là nhìn vào **hành vi cần có** rồi chọn interface và cách triển khai phù hợp, thay vì chọn `ArrayList` hoặc `HashMap` theo thói quen.

## <a id="collection-hierarchy">Hệ phân cấp Collection</a>

Điểm bắt đầu quan trọng nhất không phải là thuộc tên lớp, mà là biết **câu hỏi nào dẫn tới interface nào**.

```text
Iterable<E>
    ↓
Collection<E>
    ├── List<E>       → giữ thứ tự, truy cập theo vị trí, cho phép phần tử trùng
    ├── Set<E>        → đảm bảo tính duy nhất
    │     └── SortedSet<E>       → contract set có thứ tự sắp xếp
    │            └── NavigableSet<E> → tìm phần tử lân cận/range navigation
    └── Queue<E>      → ưu tiên thao tác đưa vào / lấy ra để xử lý
          └── Deque<E> → thao tác ở cả hai đầu

Map<K, V>             → ánh xạ khóa → giá trị, nằm ở nhánh riêng
    └── SortedMap<K, V>       → contract map có key được sắp xếp
           └── NavigableMap<K, V> → tìm key lân cận/range navigation
```

`Collection<E>` kế thừa `Iterable<E>`, nên các collection có một giao thức duyệt chung. `List`, `Set` và `Queue` bổ sung các hợp đồng chuyên biệt hơn. `SortedSet` rồi `NavigableSet` lần lượt bổ sung sorted order và các thao tác tìm phần tử lân cận. Ở nhánh `Map`, `SortedMap` và `NavigableMap` làm điều tương tự cho key. `Deque` là một dạng `Queue` có hai đầu.

Sau khi nắm hierarchy cơ bản, Java 21 bổ sung các interface **sequenced** để những cấu trúc có encounter order xác định dùng chung vocabulary đầu/cuối/reversed:

```text
SequencedCollection<E>
├── List<E>
├── Deque<E>
└── SequencedSet<E>

SequencedMap<K, V>    → phiên bản có thứ tự đầu/cuối ở nhánh Map
```

Quan hệ này có vài “cầu nối” cần đọc chính xác: `List` kế thừa `SequencedCollection`; `Deque` đồng thời là `Queue` và `SequencedCollection`; còn `SequencedSet` đồng thời là `Set` và `SequencedCollection`. `SortedSet` cũng là một `SequencedSet`; `SortedMap` cũng là một `SequencedMap`. Các interface mới giúp API diễn đạt trực tiếp các thao tác như `getFirst()`, `getLast()` hoặc `reversed()`, nhưng không thay thế contract sorted/navigable nền tảng.

## <a id="sorted-navigable-contracts">Sorted và Navigable contracts</a>

Ở chapter nhập môn chỉ cần nhớ vai trò của chúng:

- `SortedSet` / `SortedMap` thêm quy ước **luôn quan sát dữ liệu theo một thứ tự sắp xếp**;
- `NavigableSet` / `NavigableMap` mở rộng thêm khả năng tìm phần tử/key gần nhất và làm việc với các khoảng dữ liệu;
- `TreeSet` và `TreeMap` là hai cách triển khai quen thuộc của các contract này.

Các operation cụ thể như `lower`, `floor`, `ceiling`, `higher`, `subSet` và `subMap` chỉ có ý nghĩa sau khi đã hiểu `Set`, `Map` và ordering. Phần cơ chế chi tiết được dời tới chapter Ordering/Sorting thay vì bắt learner học ngay ở bản đồ mở đầu.

## <a id="collection-api-contract">Các thao tác chung của Collection</a>

Phần lớn implementation của `Collection<E>` chia sẻ một vocabulary cơ bản. Học một lần ở đây giúp không phải học lại cùng một nhóm method cho từng `List`, `Set` hay `Queue`:

| Ý định | Operation chung |
| --- | --- |
| số phần tử | `size()` |
| collection có rỗng không | `isEmpty()` |
| có chứa value không | `contains(value)` |
| lấy giao thức duyệt | `iterator()` |
| thêm một phần tử | `add(value)` |
| xóa một phần tử match | `remove(value)` |
| xóa các phần tử thỏa điều kiện | `removeIf(predicate)` |
| xóa toàn bộ | `clear()` |
| có chứa toàn bộ phần tử của collection khác | `containsAll(other)` |
| thêm toàn bộ | `addAll(other)` |
| xóa những phần tử cũng có trong collection khác | `removeAll(other)` |
| chỉ giữ phần giao | `retainAll(other)` |

```java
Collection<Order> selected = new ArrayList<>();
selected.add(orderA);
selected.add(orderB);

boolean hasOrderA = selected.contains(orderA);
boolean hasAll = selected.containsAll(List.of(orderA, orderB));

selected.remove(orderA);
selected.addAll(List.of(orderC, orderD));
```

Các method thay đổi dữ liệu trong `Collection` là **optional operations**. Interface công bố operation tồn tại, nhưng concrete collection có thể cố ý không hỗ trợ mutation và ném `UnsupportedOperationException`. Ví dụ `List.of(...)` vẫn trả về một `List`, nhưng `add/remove/clear` trên nó không được hỗ trợ.

Khi API bên ngoài yêu cầu array, `toArray` là boundary chuẩn:

```java
Order[] orderArray = selected.toArray(new Order[0]);
Object[] objectArray = selected.toArray();
```

`toArray()` không tham số trả `Object[]`; `toArray(new Order[0])` yêu cầu kết quả có runtime component type là `Order[]`. Đây là phép chuyển representation tại thời điểm gọi, không biến array và collection thành cùng một mutable container. Một overload dùng generator sẽ được nhắc ngay sau khi cú pháp method reference được giới thiệu.

## <a id="functional-syntax-boundary">Lambda và method reference trong ví dụ Collection</a>

Một số API Collection nhận **hành vi** do bên gọi cung cấp, nên các chapter sau sẽ xuất hiện cú pháp như:

```java
orders.removeIf(order -> order.totalCents() == 0);
Comparator.comparingLong(Order::id);
selected.toArray(Order[]::new);
```

Trong module Collection, chỉ cần đọc chúng theo ý nghĩa:

```text
order -> ...
→ một hàm nhận order rồi trả kết quả

Order::id
→ tham chiếu tới hành vi lấy id từ Order

Order[]::new
→ tham chiếu tới cách tạo Order[] với kích thước được yêu cầu
```

Lambda, functional interface và method reference có curriculum riêng trong phần functional programming. Ở đây chúng chỉ là **cú pháp để truyền hành vi vào API Collection**, không phải khái niệm nền của Collection.

## <a id="map-separate-hierarchy">Vì sao Map nằm riêng?</a>

`Map<K, V>` không phải là `Collection<V>`. Một collection mô hình hóa một nhóm **phần tử**; một map mô hình hóa một nhóm **ánh xạ từ khóa sang giá trị**. Đơn vị ý nghĩa của hai cấu trúc khác nhau.

Ví dụ:

```java
List<Order> orders = List.of(
        new Order(1001, 7, 120),
        new Order(1002, 7, 80)
);

Map<Long, Order> orderById = Map.of(
        1001L, orders.get(0),
        1002L, orders.get(1)
);
```

Với `List<Order>`, câu hỏi tự nhiên là “phần tử ở vị trí 0 là gì?” hoặc “hãy duyệt mọi đơn hàng”. Với `Map<Long, Order>`, câu hỏi tự nhiên là “đơn hàng có khóa 1002 là gì?”. Nếu `Map` kế thừa `Collection`, các API như `add(E)` sẽ không thể hiện được việc một ánh xạ cần cả key lẫn value.

Map vẫn cung cấp các **collection view** để nối hai thế giới:

```java
Set<Long> ids = orderById.keySet();
Collection<Order> values = orderById.values();
Set<Map.Entry<Long, Order>> entries = orderById.entrySet();
```

Các view này rất hữu ích khi cần duyệt key, value hoặc entry, nhưng `Map` vẫn giữ hợp đồng riêng về key uniqueness và lookup.

Từ Java 21, `SequencedMap` bổ sung khái niệm entry đầu/cuối và view đảo thứ tự cho các map có encounter order xác định. Nó vẫn thuộc nhánh `Map`, không chuyển `Map` thành `Collection`.

## <a id="interface-vs-implementation">Interface và implementation</a>

Trong code ứng dụng, kiểu biến nên mô tả **khả năng mà đoạn code thật sự cần**. Lớp cụ thể chỉ nên xuất hiện khi cần chọn cách lưu trữ.

```java
List<Order> orders = new ArrayList<>();
orders.add(new Order(1001, 7, 120));
orders.add(new Order(1002, 7, 80));
```

Ở đây:

- `List<Order>` là hợp đồng: có thứ tự, có vị trí, cho phép trùng.
- `ArrayList<Order>` là implementation: dùng mảng động để thực hiện hợp đồng đó.

Một method chỉ cần duyệt các đơn hàng có thể nhận kiểu rộng hơn:

```java
int totalRevenue(Collection<Order> orders) {
    int total = 0;
    for (Order order : orders) {
        total += order.totalCents();
    }
    return total;
}
```

Method này không cần index, không cần biết dữ liệu đến từ `ArrayList` hay `HashSet`, nên `Collection<Order>` diễn đạt đúng phụ thuộc của nó.

Ngược lại, nếu method cần `get(0)` thì `List<Order>` mới là hợp đồng phù hợp. “Program to interface” không có nghĩa là luôn chọn interface rộng nhất; nó có nghĩa là chọn **interface nhỏ nhất vẫn biểu đạt đủ hành vi cần thiết**.

Giữ ranh giới này giúp thay đổi implementation dễ hơn:

```java
List<Order> orders = new ArrayList<>();
// Có thể đổi sang implementation List khác nếu workload thay đổi,
// miễn là code phía sử dụng chỉ phụ thuộc vào hợp đồng List.
```

Generics quyết định kiểu phần tử như `List<Order>` hay `Map<Long, Order>`; module Collection tập trung vào hợp đồng lưu trữ và thao tác. Các quy tắc variance/PECS chi tiết thuộc module Generics.

## <a id="collection-characteristics">Các đặc tính cần nhìn trước khi chọn</a>

Tên implementation chỉ có ý nghĩa sau khi ta xác định các đặc tính dữ liệu cần giữ. Các chiều quan trọng gồm:

| Đặc tính | Câu hỏi cần trả lời |
| --- | --- |
| Encounter order | Khi duyệt, thứ tự phần tử có ý nghĩa và có được đảm bảo không? |
| Positional access | Có cần truy cập theo index không? |
| Duplicates | Hai giá trị “bằng nhau” có được cùng tồn tại không? |
| Key lookup | Có cần tìm value bằng một key riêng không? |
| Sorted order | Dữ liệu có phải luôn nằm theo natural order/Comparator không? |
| Null policy | Implementation có chấp nhận `null` không? |
| Mutability | Cấu trúc có được thêm/xóa/thay đổi sau khi tạo không? |
| Operation cost | Workload chủ yếu là đọc theo index, lookup, chèn/xóa đầu/cuối hay sắp thứ tự? |

Không nên suy ra chính sách `null` hoặc mutability chỉ từ interface. Ví dụ, `HashMap` cho phép một null key, trong khi `Map.of(...)` không cho phép key/value là `null`. `ArrayList` có thể thay đổi kích thước, còn `List.of(...)` là unmodifiable.

Với cùng dữ liệu:

```java
List<Long> processingOrder = new ArrayList<>();     // thứ tự + trùng có thể hợp lệ
Set<Long> uniqueUsers = new HashSet<>();            // duy nhất
Map<Long, Order> orderById = new HashMap<>();       // lookup theo id
Deque<Order> pending = new ArrayDeque<>();           // xử lý ở hai đầu
```

Các chapter tiếp theo sẽ lần lượt làm rõ từng hợp đồng. Khi học implementation, hãy luôn quay lại câu hỏi: **hành vi nào của bài toán buộc ta chọn cấu trúc này?**

## <a id="complexity-mental-model">Mental model về chi phí O(1), O(n), O(log n)</a>

Collection thường được so sánh bằng **độ phức tạp thao tác**. Người học chưa cần biết toàn bộ thuật toán Big-O; chỉ cần đọc được các ký hiệu xuất hiện trong module:

| Ký hiệu | Mental model | Khi `n` tăng lớn |
| --- | --- | --- |
| `O(1)` | làm một lượng công việc gần như không phụ thuộc số phần tử | tăng rất ít |
| `O(log n)` | mỗi bước loại bỏ được một phần lớn vùng tìm kiếm | tăng chậm |
| `O(n)` | có thể phải đi qua số phần tử tỉ lệ với kích thước collection | tăng gần tuyến tính |

Ví dụ với 1.000.000 phần tử, `ArrayList.get(index)` vẫn có thể nhảy thẳng tới vị trí cần đọc (`O(1)`), trong khi tìm một value bằng cách quét list có thể phải kiểm tra rất nhiều phần tử (`O(n)`). Một tree cân bằng như `TreeMap` thường tìm theo đường đi có chiều cao `O(log n)`.

Hai từ qualifier cũng xuất hiện nhiều:

- **amortized O(1)**: phần lớn operation rất rẻ nhưng đôi lúc có một lần đắt hơn, ví dụ `ArrayList` phải resize; tính trên một chuỗi dài operation thì chi phí trung bình vẫn gần hằng số;
- **expected O(1)**: hiệu năng kỳ vọng gần hằng số khi điều kiện như phân bố hash hợp lý; đây không phải bảo đảm worst-case tuyệt đối.

Big-O chỉ nói **xu hướng tăng chi phí**, không nói một operation chắc chắn nhanh hơn operation khác trong mọi trường hợp. Memory layout, cache locality, kích thước dữ liệu và workload thật vẫn quan trọng.
