# Equality và Hashing trong Collection

Collection dựa vào quy ước của phần tử để trả lời các câu hỏi rất thực tế: “phần tử này đã tồn tại chưa?”, “key này nằm ở đâu?”, “hai giá trị có phải cùng một phần tử logic không?”. Vì vậy một lỗi trong equality, hashing hoặc comparison có thể làm collection trông như “mất dữ liệu” dù object vẫn đang nằm bên trong cấu trúc.

Chương này tập trung vào **hệ quả của equality/hash/comparison đối với collection**. Cách thiết kế đúng `equals` và `hashCode` thuộc module `object-contract`.

## <a id="element-equality-effects">Equality của phần tử ảnh hưởng collection như thế nào?</a>

Không phải chỉ `HashSet` và `HashMap` mới quan tâm đến equality. Nhiều API collection dùng `equals` để xác định match:

- `List.contains(x)` và `List.remove(x)` tìm phần tử bằng `equals`.
- `HashSet` dùng `hashCode` để thu hẹp vùng tìm kiếm rồi dùng `equals` để xác nhận.
- `HashMap` áp dụng cùng ý tưởng cho key.
- Equality của `List` và nhiều collection khác cũng được xây dựng từ equality của phần tử.

Với record đơn giản, Java tạo equality theo toàn bộ component:

```java
record User(long id, String email) {}

List<User> users = new ArrayList<>();
users.add(new User(10, "a@example.com"));

System.out.println(users.contains(new User(10, "a@example.com"))); // true
```

Nếu nghiệp vụ muốn “User cùng id là cùng định danh” nhưng `equals` lại tính cả email, collection sẽ phản ánh đúng quy ước Java hiện tại, không phản ánh ý định nghiệp vụ chưa được mã hóa. Vì vậy trước khi trách collection, cần kiểm tra mô hình equality của phần tử.

## <a id="hash-bucket-boundary">Ranh giới hash bucket</a>

Một mental model hữu ích cho hash-based collection là:

```text
hashCode()
   ↓
thu hẹp vùng/bucket ứng viên
   ↓
equals()
   ↓
xác nhận key/phần tử thực sự khớp
```

Hash không chứng minh hai object bằng nhau. Hai object khác nhau hoàn toàn có thể collision và đi vào cùng vùng ứng viên; `equals` mới phân biệt chúng. Ngược lại, nếu hai object bằng nhau theo `equals` nhưng trả hash khác nhau, hash-based collection có thể tìm ở hai vùng khác nhau và phá vỡ quy ước lookup/uniqueness.

```java
Map<Long, Order> byId = new HashMap<>();
byId.put(101L, new Order(101L, 1L, 20_000));

Order order = byId.get(101L);
```

Chi tiết bucket, tree bin hoặc ngưỡng resize là chi tiết triển khai của từng JDK và không nên trở thành giả định nghiệp vụ. Quy ước cần nhớ là hash giúp khoanh vùng, equality xác nhận phần tử khớp, và key phải giữ ổn định các thuộc tính tham gia hai phép tính đó trong thời gian nằm trong map.

## <a id="sorted-equality-boundary">Equality trong sorted collection</a>

`TreeSet` và `TreeMap` có một ranh giới khác: chúng tổ chức dữ liệu theo `compareTo` hoặc `Comparator`. Khi kết quả so sánh là `0`, sorted collection xem hai giá trị/key là cùng vị trí logic cho mục đích uniqueness.

Ví dụ kinh điển là `BigDecimal`:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));      // false
System.out.println(a.compareTo(b));   // 0

Set<BigDecimal> hashed = new HashSet<>(List.of(a, b));
Set<BigDecimal> sorted = new TreeSet<>(List.of(a, b));

System.out.println(hashed.size()); // 2
System.out.println(sorted.size()); // 1
```

Đây không phải lỗi ngẫu nhiên của `TreeSet`. Set đang dùng quan hệ tương đương theo ordering để xác định vị trí. Tuy nhiên, nếu ordering không nhất quán với `equals`, sorted set có thể không tuân theo trực giác chung về equality của `Set` và gây bất ngờ cho bên gọi.

Khi tạo comparator cho `TreeSet`/`TreeMap`, hãy tự hỏi: “Nếu comparator trả `0`, tôi có thực sự muốn collection coi hai giá trị này là cùng key/phần tử không?”. Nếu câu trả lời là không, comparator cần tie-breaker.

## <a id="collection-equality-boundary">Không phải mọi Collection đều có value equality giống List/Set</a>

`Collection<E>` tự nó **không định nghĩa một contract value-equality chung cho mọi collection implementation**. Các subtype cụ thể mới có thể đặt quy ước equality riêng.

- `List` định nghĩa equality theo **cùng size + phần tử bằng nhau ở từng vị trí**.
- `Set` định nghĩa equality theo **cùng các member**, không quan tâm encounter order.
- `Map` định nghĩa equality theo **cùng các mapping**.
- `Queue` nói chung không ép một element-based equality chung; nhiều queue/deque implementation như `ArrayDeque` và `PriorityQueue` giữ identity-based `equals/hashCode` từ `Object`.

```java
Queue<Integer> a = new ArrayDeque<>(List.of(1, 2, 3));
Queue<Integer> b = new ArrayDeque<>(List.of(1, 2, 3));

System.out.println(a.equals(b)); // false: hai object khác nhau
```

`LinkedList` là trường hợp cần đọc theo **type contract mà nó đồng thời thực hiện**: nó là `List`, nên equality của nó tuân theo `List` value equality dù nó cũng dùng được như `Deque`.

Vì vậy không nên suy diễn “cùng phần tử thì mọi Collection đều equals nhau”. Equality của container là một phần của contract subtype/implementation, không phải tính chất chung của `Collection`.

## <a id="collection-equality-contracts">Equality của List, Set và Map khác nhau thế nào?</a>

Element equality là nguyên liệu, nhưng từng abstraction dùng nó để định nghĩa **equality của cả container** theo semantics riêng:

| Abstraction | Hai container bằng nhau khi... |
| --- | --- |
| `List` | cùng size và element bằng nhau ở từng vị trí tương ứng |
| `Set` | cùng size và có cùng các member; encounter order không quan trọng |
| `Map` | có cùng các mapping `key → value`; encounter order không quan trọng |

```java
List<Integer> listA = List.of(1, 2);
List<Integer> listB = List.of(2, 1);
System.out.println(listA.equals(listB)); // false

Set<Integer> setA = new HashSet<>(List.of(1, 2));
Set<Integer> setB = new LinkedHashSet<>(List.of(2, 1));
System.out.println(setA.equals(setB));   // true
```

Điều này giải thích vì sao chọn `List` hay `Set` không chỉ là quyết định performance. Abstraction còn quyết định **giá trị logic của container là gì**. Chi tiết đầy đủ về luật `equals/hashCode` của object vẫn thuộc Object Contract.

## <a id="mutable-element-risk">Rủi ro khi key hoặc phần tử thay đổi</a>

Hash-based và sorted collection giả định rằng thuộc tính quyết định vị trí của key/phần tử không tự thay đổi trong lúc nó đang được lưu.

Xét một key mutable:

```java
final class UserKey {
    long id;
    String region;

    UserKey(long id, String region) {
        this.id = id;
        this.region = region;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserKey that)) return false;
        return id == that.id && Objects.equals(region, that.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, region);
    }
}

UserKey key = new UserKey(10, "VN");
Map<UserKey, String> names = new HashMap<>();
names.put(key, "Phuong");

key.region = "SG"; // thay đổi dữ liệu tham gia hashCode/equals

System.out.println(names.get(key)); // không còn được đảm bảo tìm thấy entry
```

Entry chưa tự di chuyển sang bucket tương ứng với hash mới. Tương tự, nếu phần tử trong `TreeSet` thay đổi field mà comparator đọc, cây không tự sắp xếp lại; thứ tự và lookup có thể không còn hợp lệ.

Cách an toàn thường là dùng key có định danh ổn định, ưu tiên object bất biến cho key, hoặc xóa phần tử trước khi thay đổi thuộc tính định vị rồi chèn lại. Việc thay đổi **value** của `Map` không gây cùng loại rủi ro nếu value không tham gia định danh của key, dù các bất biến nghiệp vụ của ứng dụng vẫn phải được giữ riêng.
