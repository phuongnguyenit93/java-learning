# Nền tảng Collection: Vì sao Collections Framework tồn tại?

Trước khi học `List`, `Set`, `Map` hay `Queue`, cần trả lời một câu đơn giản hơn: **Collection là gì và tại sao chương trình Java lại cần nó?**

Giả sử chương trình chỉ có ba đơn hàng. Ta hoàn toàn có thể viết ba biến riêng:

```java
Order first = new Order(1001, 7, 120);
Order second = new Order(1002, 8, 90);
Order third = new Order(1003, 7, 150);
```

Nhưng dữ liệu thực tế hiếm khi dừng ở con số ba. Số lượng đơn hàng có thể là 0, 10, 1.000 hoặc thay đổi trong lúc chương trình chạy. Khi đó mã nguồn cần một cách để **gom nhiều đối tượng lại thành một nhóm và thao tác với cả nhóm đó**: thêm phần tử, xóa phần tử, tìm kiếm, duyệt, kiểm tra số lượng, giữ thứ tự, loại trùng hoặc tra cứu theo khóa.

Đó là bài toán mà Java Collections Framework giải quyết.

Trong module này, hãy dùng một bộ dữ liệu nhỏ làm ví dụ xuyên suốt:

```java
record User(long id, String name) {}
record Order(long id, long userId, int totalCents) {}
```

Ta có thể giữ các `Order` theo thứ tự tạo, tập hợp các `userId` duy nhất, ánh xạ `orderId → Order`, hoặc đưa đơn hàng vào hàng đợi xử lý. Cùng là “nhiều phần tử”, nhưng mỗi bài toán có một hợp đồng khác nhau.

## <a id="collection-purpose">Collection là gì và vì sao cần nó?</a>

Ở mức tư duy cơ bản, một **collection** là một đối tượng dùng để quản lý **một nhóm phần tử** thay vì bắt mã nguồn phải quản lý từng biến riêng lẻ.

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

Thay vì phải tạo `order1`, `order2`, `order3`, ... và viết logic riêng cho từng biến, mã nguồn làm việc với **một đối tượng đại diện cho cả nhóm**.

Đọc khai báo này từ trái sang phải:

```java
List<Order> orders = new ArrayList<>();
```

- `List<Order>` nói biến tuân theo quy ước của `List` và kiểu phần tử là `Order`;
- `new ArrayList<>()` chọn `ArrayList` làm lớp triển khai cụ thể;
- toán tử diamond `<>` cho trình biên dịch suy ra `Order` từ kiểu bên trái, nên không cần lặp lại `new ArrayList<Order>()`.

Collection dùng generic lưu **kiểu tham chiếu (reference type)**. Khi cần lưu kiểu nguyên thủy (primitive), Java dùng kiểu bao (wrapper type) tương ứng, ví dụ `List<Integer>` cho `int`, `List<Long>` cho `long`. Chi tiết boxing/unboxing thuộc module Cơ bản ngôn ngữ và Generics; ở đây chỉ cần nhận ra vì sao không viết `List<int>`.

### Vấn đề trước khi có collection

Mảng đã cho Java một cách lưu nhiều phần tử cùng kiểu:

```java
Order[] orders = new Order[100];
```

Mảng vẫn rất hữu ích khi kích thước cố định hoặc khi API/hiệu năng yêu cầu nó. Nhưng nếu dùng mảng cho dữ liệu động, ứng dụng thường phải tự xử lý nhiều việc:

```text
Mảng đã đầy thì làm gì?
→ tự tạo mảng lớn hơn và sao chép dữ liệu

Muốn xóa phần tử ở giữa?
→ tự dịch các phần tử còn lại

Muốn không cho trùng?
→ tự viết logic kiểm tra

Muốn tra cứu theo id?
→ tự duyệt hoặc tự xây cấu trúc chỉ mục

Muốn xử lý FIFO/LIFO?
→ tự quản lý vị trí đầu/cuối
```

Collections Framework cung cấp những cấu trúc đã chuẩn hóa để ứng dụng không phải tự phát minh lại các cơ chế phổ biến đó.

### Java Collections Framework là gì?

**Java Collections Framework (JCF)** là tập hợp các interface, lớp triển khai và thuật toán/tiện ích chuẩn của Java để biểu diễn và thao tác với nhóm dữ liệu.

Mô hình tư duy:

```text
Dữ liệu có nhiều phần tử
        ↓
Cần một đối tượng quản lý cả nhóm
        ↓
Collections Framework cung cấp các quy ước khác nhau
        ↓
List / Set / Queue / Deque / Map
        ↓
chọn quy ước theo hành vi bài toán cần
```

Ví dụ, cùng một tập `Order` nhưng nhu cầu khác nhau dẫn tới cấu trúc khác nhau:

```java
List<Order> ordersInArrivalOrder = new ArrayList<>();
Set<Long> uniqueUserIds = new HashSet<>();
Map<Long, Order> orderById = new HashMap<>();
Deque<Order> pendingOrders = new ArrayDeque<>();
```

Đây là lý do Collection không phải chỉ là “một cái List để chứa nhiều đối tượng”. Nó là **một họ mô hình trừu tượng cho nhiều kiểu bài toán quản lý nhóm dữ liệu**.

## <a id="collection-vs-collections">Collection, Collections và Collections Framework khác nhau thế nào?</a>

Ba tên gần giống nhau nhưng vai trò khác hẳn:

- `Collection<E>` là interface gốc cho các **nhóm phần tử** như `List`, `Set`, `Queue`;
- `Collections` là lớp tiện ích chứa các thuật toán/hàm hỗ trợ tĩnh như `sort`, `reverse`, `binarySearch` và các phương thức tạo lớp bọc;
- **Java Collections Framework (JCF)** là toàn bộ hệ thống gồm interface, lớp triển khai và các thuật toán/tiện ích liên quan.

`Map<K, V>` thuộc Java Collections Framework nhưng không kế thừa `Collection<E>`; phần tiếp theo sẽ giải thích tại sao.

Sau khi hiểu **vì sao cần một collection**, hệ phân cấp mới có ý nghĩa: nó trả lời câu hỏi **“nhóm dữ liệu này cần hành vi nào?”**.

Lộ trình cấp cao của module là:

```text
Vì sao Collections Framework tồn tại?
        ↓
Những mô hình dữ liệu cốt lõi giải quyết các nhu cầu nào?
List / Set / Map / Queue / Deque
        ↓
Làm sao duyệt chúng qua một giao thức chung?
Iterator / ListIterator / ranh giới Spliterator
        ↓
Thứ tự, so sánh và điều hướng hoạt động ra sao?
        ↓
Tính bằng nhau và hash ảnh hưởng tra cứu thế nào?
        ↓
Ai sở hữu quyền thay đổi dữ liệu, khung nhìn và fail-fast ảnh hưởng ra sao?
        ↓
Chọn lớp triển khai hoặc cấu trúc chuyên biệt như thế nào?
```

Bốn chương tiếp theo sẽ mở rộng chặng học thứ hai thành `List`, `Set`, `Map` và `Queue`/`Deque`. Các chương sau đó nhìn lại những mô hình này qua góc độ duyệt dữ liệu, thứ tự, tính bằng nhau/hash, quyền thay đổi và lựa chọn lớp triển khai. Ở cuối module, mục tiêu là nhìn vào **hành vi cần có** rồi chọn interface và lớp triển khai phù hợp, thay vì chọn `ArrayList` hoặc `HashMap` theo thói quen.

## <a id="collection-hierarchy">Hệ phân cấp Collection</a>

Điểm bắt đầu quan trọng nhất không phải là thuộc tên lớp, mà là biết **câu hỏi nào dẫn tới interface nào**.

```text
Iterable<E>
    ↓
Collection<E>
    ├── List<E>       → giữ thứ tự, truy cập theo vị trí, cho phép phần tử trùng
    ├── Set<E>        → đảm bảo tính duy nhất
    │     └── SortedSet<E>       → quy ước Set có thứ tự sắp xếp
    │            └── NavigableSet<E> → tìm phần tử lân cận/điều hướng theo phạm vi
    └── Queue<E>      → ưu tiên thao tác đưa vào / lấy ra để xử lý
          └── Deque<E> → thao tác ở cả hai đầu

Map<K, V>             → ánh xạ khóa → giá trị, nằm ở nhánh riêng
    └── SortedMap<K, V>       → quy ước Map có khóa được sắp xếp
           └── NavigableMap<K, V> → tìm khóa lân cận/điều hướng theo phạm vi
```

`Collection<E>` kế thừa `Iterable<E>`, nên các collection có một giao thức duyệt chung. `List`, `Set` và `Queue` bổ sung các quy ước chuyên biệt hơn. `SortedSet` rồi `NavigableSet` lần lượt bổ sung thứ tự sắp xếp và các thao tác tìm phần tử lân cận. Ở nhánh `Map`, `SortedMap` và `NavigableMap` làm điều tương tự cho khóa. `Deque` là một dạng `Queue` có hai đầu.

Sau khi nắm hệ phân cấp cơ bản, Java 21 bổ sung các interface **Sequenced** để những cấu trúc có thứ tự duyệt xác định dùng chung nhóm thao tác đầu/cuối/`reversed()`:

```text
SequencedCollection<E>
├── List<E>
├── Deque<E>
└── SequencedSet<E>

SequencedMap<K, V>    → phiên bản có thứ tự đầu/cuối ở nhánh Map
```

Quan hệ này có vài “cầu nối” cần đọc chính xác: `List` kế thừa `SequencedCollection`; `Deque` đồng thời là `Queue` và `SequencedCollection`; còn `SequencedSet` đồng thời là `Set` và `SequencedCollection`. `SortedSet` cũng là một `SequencedSet`; `SortedMap` cũng là một `SequencedMap`. Các interface mới giúp API diễn đạt trực tiếp các thao tác như `getFirst()`, `getLast()` hoặc `reversed()`, nhưng không thay thế các quy ước Sorted/Navigable nền tảng.

## <a id="sorted-navigable-contracts">Quy ước Sorted và Navigable</a>

Ở chương nhập môn chỉ cần nhớ vai trò của chúng:

- `SortedSet` / `SortedMap` thêm quy ước **luôn quan sát dữ liệu theo một thứ tự sắp xếp**;
- `NavigableSet` / `NavigableMap` mở rộng thêm khả năng tìm phần tử/khóa gần nhất và làm việc với các khoảng dữ liệu;
- `TreeSet` và `TreeMap` là hai cách triển khai quen thuộc của các quy ước này.

Các thao tác cụ thể như `lower`, `floor`, `ceiling`, `higher`, `subSet` và `subMap` chỉ có ý nghĩa sau khi đã hiểu `Set`, `Map` và thứ tự. Phần cơ chế chi tiết được dời tới chương về thứ tự/sắp xếp thay vì bắt người học học ngay ở bản đồ mở đầu.

## <a id="collection-api-contract">Các thao tác chung của Collection</a>

Phần lớn lớp triển khai của `Collection<E>` chia sẻ một tập thao tác cơ bản. Học một lần ở đây giúp không phải học lại cùng một nhóm phương thức cho từng `List`, `Set` hay `Queue`:

| Ý định | thao tác chung |
| --- | --- |
| số phần tử | `size()` |
| collection có rỗng không | `isEmpty()` |
| có chứa giá trị không | `contains(value)` |
| lấy giao thức duyệt | `iterator()` |
| thêm một phần tử | `add(value)` |
| xóa một phần tử khớp | `remove(value)` |
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

Các phương thức thay đổi dữ liệu trong `Collection` là **các thao tác tùy chọn (optional operations)**. Interface công bố thao tác tồn tại, nhưng một collection cụ thể có thể cố ý không hỗ trợ thay đổi dữ liệu và ném `UnsupportedOperationException`. Ví dụ `List.of(...)` vẫn trả về một `List`, nhưng `add/remove/clear` trên nó không được hỗ trợ.

Khi API bên ngoài yêu cầu mảng, `toArray` là ranh giới chuyển đổi chuẩn:

```java
Order[] orderArray = selected.toArray(new Order[0]);
Object[] objectArray = selected.toArray();
```

`toArray()` không tham số trả `Object[]`; `toArray(new Order[0])` yêu cầu kết quả có kiểu thành phần lúc chạy là `Order[]`. Đây là phép chuyển dạng biểu diễn tại thời điểm gọi, không biến mảng và collection thành cùng một cấu trúc có thể thay đổi. Một phiên bản nạp chồng dùng hàm tạo sẽ được nhắc ngay sau khi cú pháp tham chiếu phương thức được giới thiệu.

## <a id="functional-syntax-boundary">Lambda và tham chiếu phương thức trong ví dụ Collection</a>

Một số API Collection nhận **hành vi** do bên gọi cung cấp, nên các chương sau sẽ xuất hiện cú pháp như:

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

Lambda, functional interface và tham chiếu phương thức có lộ trình học riêng trong module Lập trình hàm. Ở đây chúng chỉ là **cú pháp để truyền hành vi vào API Collection**, không phải khái niệm nền của Collection.

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

Với `List<Order>`, câu hỏi tự nhiên là “phần tử ở vị trí 0 là gì?” hoặc “hãy duyệt mọi đơn hàng”. Với `Map<Long, Order>`, câu hỏi tự nhiên là “đơn hàng có khóa 1002 là gì?”. Nếu `Map` kế thừa `Collection`, các API như `add(E)` sẽ không thể hiện được việc một ánh xạ cần cả khóa lẫn giá trị.

Map vẫn cung cấp các **khung nhìn dạng Collection** để nối hai mô hình:

```java
Set<Long> ids = orderById.keySet();
Collection<Order> values = orderById.values();
Set<Map.Entry<Long, Order>> entries = orderById.entrySet();
```

Các khung nhìn này rất hữu ích khi cần duyệt khóa, giá trị hoặc entry, nhưng `Map` vẫn giữ quy ước riêng về tính duy nhất của khóa và cách tra cứu.

Từ Java 21, `SequencedMap` bổ sung khái niệm entry đầu/cuối và khung nhìn đảo thứ tự cho các map có thứ tự duyệt xác định. Nó vẫn thuộc nhánh `Map`, không chuyển `Map` thành `Collection`.

## <a id="interface-vs-implementation">Interface và cách triển khai</a>

Trong mã ứng dụng, kiểu biến nên mô tả **khả năng mà đoạn mã thật sự cần**. Lớp cụ thể chỉ nên xuất hiện khi cần chọn cách lưu trữ.

```java
List<Order> orders = new ArrayList<>();
orders.add(new Order(1001, 7, 120));
orders.add(new Order(1002, 7, 80));
```

Ở đây:

- `List<Order>` là quy ước: có thứ tự, có vị trí, cho phép trùng.
- `ArrayList<Order>` là lớp triển khai: dùng mảng động để thực hiện quy ước đó.

Một phương thức chỉ cần duyệt các đơn hàng có thể nhận kiểu rộng hơn:

```java
int totalRevenue(Collection<Order> orders) {
    int total = 0;
    for (Order order : orders) {
        total += order.totalCents();
    }
    return total;
}
```

Phương thức này không cần chỉ số, không cần biết dữ liệu đến từ `ArrayList` hay `HashSet`, nên `Collection<Order>` diễn đạt đúng phụ thuộc của nó.

Ngược lại, nếu phương thức cần `get(0)` thì `List<Order>` mới là quy ước phù hợp. Nguyên tắc “program to interface” không có nghĩa là luôn chọn interface rộng nhất; nó có nghĩa là chọn **interface nhỏ nhất vẫn biểu đạt đủ hành vi cần thiết**.

Giữ ranh giới này giúp thay đổi lớp triển khai dễ hơn:

```java
List<Order> orders = new ArrayList<>();
// Có thể đổi sang lớp triển khai List khác nếu kiểu sử dụng thay đổi,
// miễn là mã phía sử dụng chỉ phụ thuộc vào quy ước List.
```

Generics quyết định kiểu phần tử như `List<Order>` hay `Map<Long, Order>`; module Collection tập trung vào quy ước lưu trữ và thao tác. Các quy tắc về phương sai (variance)/PECS chi tiết thuộc module Generics.

## <a id="collection-characteristics">Các đặc tính cần nhìn trước khi chọn</a>

Tên lớp triển khai chỉ có ý nghĩa sau khi ta xác định các đặc tính dữ liệu cần giữ. Các chiều quan trọng gồm:

| Đặc tính | Câu hỏi cần trả lời |
| --- | --- |
| thứ tự duyệt | Khi duyệt, thứ tự phần tử có ý nghĩa và có được đảm bảo không? |
| Truy cập theo vị trí | Có cần truy cập theo chỉ số không? |
| Phần tử trùng | Hai giá trị “bằng nhau” có được cùng tồn tại không? |
| tra cứu theo khóa | Có cần tìm giá trị bằng một khóa riêng không? |
| Thứ tự sắp xếp | Dữ liệu có phải luôn nằm theo thứ tự tự nhiên hoặc `Comparator` không? |
| Chính sách `null` | Cách triển khai có chấp nhận `null` không? |
| Khả năng thay đổi | Cấu trúc có được thêm/xóa/thay đổi sau khi tạo không? |
| Chi phí thao tác | Kiểu sử dụng chủ yếu là đọc theo chỉ số, tra cứu, chèn/xóa đầu/cuối hay sắp thứ tự? |

Không nên suy ra chính sách `null` hoặc khả năng thay đổi chỉ từ interface. Ví dụ, `HashMap` cho phép một null khóa, trong khi `Map.of(...)` không cho phép khóa/giá trị là `null`. `ArrayList` có thể thay đổi kích thước, còn `List.of(...)` là không cho phép sửa.

Với cùng dữ liệu:

```java
List<Long> processingOrder = new ArrayList<>();     // thứ tự + trùng có thể hợp lệ
Set<Long> uniqueUsers = new HashSet<>();            // duy nhất
Map<Long, Order> orderById = new HashMap<>();       // tra cứu theo id
Deque<Order> pending = new ArrayDeque<>();           // xử lý ở hai đầu
```

Các chương tiếp theo sẽ lần lượt làm rõ từng quy ước. Khi học lớp triển khai, hãy luôn quay lại câu hỏi: **hành vi nào của bài toán buộc ta chọn cấu trúc này?**

## <a id="complexity-mental-model">Mô hình tư duy về chi phí O(1), O(n), O(log n)</a>

Collection thường được so sánh bằng **độ phức tạp thao tác**. Người học chưa cần biết toàn bộ thuật toán Big-O; chỉ cần đọc được các ký hiệu xuất hiện trong module:

| Ký hiệu | Mô hình tư duy | Khi `n` tăng lớn |
| --- | --- | --- |
| `O(1)` | làm một lượng công việc gần như không phụ thuộc số phần tử | tăng rất ít |
| `O(log n)` | mỗi bước loại bỏ được một phần lớn vùng tìm kiếm | tăng chậm |
| `O(n)` | có thể phải đi qua số phần tử tỉ lệ với kích thước collection | tăng gần tuyến tính |

Ví dụ với 1.000.000 phần tử, `ArrayList.get(index)` vẫn có thể nhảy thẳng tới vị trí cần đọc (`O(1)`), trong khi tìm một giá trị bằng cách quét danh sách có thể phải kiểm tra rất nhiều phần tử (`O(n)`). Một cây cân bằng như `TreeMap` thường tìm theo đường đi có chiều cao `O(log n)`.

Hai cách diễn đạt bổ sung cũng xuất hiện nhiều:

- **amortized O(1)**: phần lớn thao tác rất rẻ nhưng đôi lúc có một lần đắt hơn, ví dụ `ArrayList` phải tăng dung lượng; tính trên một chuỗi dài thao tác thì chi phí trung bình vẫn gần hằng số;
- **kỳ vọng O(1) (expected O(1))**: hiệu năng kỳ vọng gần hằng số khi điều kiện như phân bố hash hợp lý; đây không phải bảo đảm tuyệt đối cho trường hợp xấu nhất.

Big-O chỉ nói **xu hướng tăng chi phí**, không nói một thao tác chắc chắn nhanh hơn thao tác khác trong mọi trường hợp. Cách bố trí bộ nhớ, tính cục bộ của bộ nhớ đệm, kích thước dữ liệu và kiểu sử dụng thực tế vẫn quan trọng.
