# Tính bằng nhau, hash và tính đúng đắn khi tra cứu

Collection dựa vào quy ước của phần tử để trả lời các câu hỏi rất thực tế: “phần tử này đã tồn tại chưa?”, “khóa này nằm ở đâu?”, “hai giá trị có phải cùng một phần tử logic không?”. Vì vậy một lỗi trong tính bằng nhau, hash hoặc phép so sánh có thể làm collection trông như “mất dữ liệu” dù đối tượng vẫn đang nằm bên trong cấu trúc.

Chương này tập trung vào **hệ quả của tính bằng nhau, hash và phép so sánh đối với collection**. Cách thiết kế đúng `equals` và `hashCode` thuộc module **Quy ước của Object** (`object-contract`).

## <a id="element-equality-effects">Tính bằng nhau của phần tử ảnh hưởng collection như thế nào?</a>

Không phải chỉ `HashSet` và `HashMap` mới quan tâm đến tính bằng nhau. Nhiều API collection dùng `equals` để xác định phần tử khớp:

- `List.contains(x)` và `List.remove(x)` tìm phần tử bằng `equals`.
- `HashSet` dùng `hashCode` để thu hẹp vùng tìm kiếm rồi dùng `equals` để xác nhận.
- `HashMap` áp dụng cùng ý tưởng cho khóa.
- tính bằng nhau của `List` và nhiều collection khác cũng được xây dựng từ tính bằng nhau của phần tử.

Với record đơn giản, Java tạo tính bằng nhau theo toàn bộ thành phần:

```java
record User(long id, String email) {}

List<User> users = new ArrayList<>();
users.add(new User(10, "a@example.com"));

System.out.println(users.contains(new User(10, "a@example.com"))); // true
```

Nếu nghiệp vụ muốn “User cùng id là cùng định danh” nhưng `equals` lại tính cả email, collection sẽ phản ánh đúng quy ước Java hiện tại, không phản ánh ý định nghiệp vụ chưa được mã hóa. Vì vậy trước khi trách collection, cần kiểm tra mô hình tính bằng nhau của phần tử.

## <a id="hash-bucket-boundary">Ranh giới hash bucket</a>

Một mô hình tư duy hữu ích cho collection dựa trên hash là:

```text
hashCode()
   ↓
thu hẹp vùng/bucket ứng viên
   ↓
equals()
   ↓
xác nhận khóa/phần tử thực sự khớp
```

Hash không chứng minh hai đối tượng bằng nhau. Hai đối tượng khác nhau hoàn toàn có thể va chạm hash và đi vào cùng vùng ứng viên; `equals` mới phân biệt chúng. Ngược lại, nếu hai đối tượng bằng nhau theo `equals` nhưng trả hash khác nhau, collection dựa trên hash có thể tìm ở hai vùng khác nhau và phá vỡ quy ước tra cứu/tính duy nhất.

```java
Map<Long, Order> byId = new HashMap<>();
byId.put(101L, new Order(101L, 1L, 20_000));

Order order = byId.get(101L);
```

Chi tiết bucket, tree bin hoặc ngưỡng tăng dung lượng là chi tiết triển khai của từng JDK và không nên trở thành giả định nghiệp vụ. Quy ước cần nhớ là hash giúp khoanh vùng, tính bằng nhau xác nhận phần tử khớp, và khóa phải giữ ổn định các thuộc tính tham gia hai phép tính đó trong thời gian nằm trong Map.

## <a id="sorted-equality-boundary">Tính bằng nhau trong collection có sắp xếp</a>

`TreeSet` và `TreeMap` có một ranh giới khác: chúng tổ chức dữ liệu theo `compareTo` hoặc `Comparator`. Khi kết quả so sánh là `0`, collection có sắp xếp xem hai giá trị/khóa là cùng vị trí logic cho mục đích tính duy nhất.

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

Đây không phải lỗi ngẫu nhiên của `TreeSet`. Set đang dùng quan hệ tương đương theo thứ tự để xác định vị trí. Tuy nhiên, nếu thứ tự không nhất quán với `equals`, Set có sắp xếp có thể không tuân theo trực giác chung về tính bằng nhau của `Set` và gây bất ngờ cho bên gọi.

Khi tạo comparator cho `TreeSet`/`TreeMap`, hãy tự hỏi: “Nếu comparator trả `0`, tôi có thực sự muốn collection coi hai giá trị này là cùng khóa/phần tử không?”. Nếu câu trả lời là không, comparator cần thêm tiêu chí phân xử.

## <a id="collection-equality-boundary">Không phải mọi Collection đều có quy tắc bằng nhau theo giá trị như List/Set</a>

`Collection<E>` tự nó **không định nghĩa một quy tắc bằng nhau theo giá trị chung cho mọi cách triển khai collection**. Các kiểu con cụ thể mới có thể đặt quy tắc tính bằng nhau riêng.

- `List` định nghĩa tính bằng nhau theo **cùng kích thước + phần tử bằng nhau ở từng vị trí**.
- `Set` định nghĩa tính bằng nhau theo **cùng các phần tử**, không quan tâm thứ tự duyệt.
- `Map` định nghĩa tính bằng nhau theo **cùng các ánh xạ**.
- `Queue` nói chung không áp đặt một quy tắc bằng nhau theo phần tử chung; nhiều cách triển khai Queue/Deque như `ArrayDeque` và `PriorityQueue` giữ `equals/hashCode` dựa trên định danh từ `Object`.

```java
Queue<Integer> a = new ArrayDeque<>(List.of(1, 2, 3));
Queue<Integer> b = new ArrayDeque<>(List.of(1, 2, 3));

System.out.println(a.equals(b)); // false: hai đối tượng khác nhau
```

`LinkedList` là trường hợp cần đọc theo **quy ước của kiểu mà nó đồng thời thực hiện**: nó là `List`, nên tính bằng nhau của nó tuân theo quy tắc bằng nhau theo giá trị của `List` dù nó cũng dùng được như `Deque`.

Vì vậy không nên suy diễn “cùng phần tử thì mọi Collection đều equals nhau”. Tính bằng nhau của cấu trúc chứa là một phần của quy ước kiểu con/cách triển khai, không phải tính chất chung của `Collection`.

## <a id="collection-equality-contracts">Quy tắc bằng nhau của List, Set và Map khác nhau thế nào?</a>

Tính bằng nhau của phần tử là nền tảng, nhưng từng mô hình trừu tượng dùng nó để định nghĩa **tính bằng nhau của cả cấu trúc chứa** theo ngữ nghĩa riêng:

| Mô hình trừu tượng | Hai cấu trúc bằng nhau khi... |
| --- | --- |
| `List` | cùng kích thước và phần tử bằng nhau ở từng vị trí tương ứng |
| `Set` | cùng kích thước và có cùng các phần tử; thứ tự duyệt không quan trọng |
| `Map` | có cùng các ánh xạ `khóa → giá trị`; thứ tự duyệt không quan trọng |

```java
List<Integer> listA = List.of(1, 2);
List<Integer> listB = List.of(2, 1);
System.out.println(listA.equals(listB)); // false

Set<Integer> setA = new HashSet<>(List.of(1, 2));
Set<Integer> setB = new LinkedHashSet<>(List.of(2, 1));
System.out.println(setA.equals(setB));   // true
```

Điều này giải thích vì sao chọn `List` hay `Set` không chỉ là quyết định hiệu năng. Mô hình trừu tượng còn quyết định **giá trị logic của cấu trúc là gì**. Chi tiết đầy đủ về luật `equals/hashCode` của đối tượng vẫn thuộc module **Quy ước của Object**.

## <a id="mutable-element-risk">Rủi ro khi khóa hoặc phần tử thay đổi</a>

Collection dựa trên hash và collection có sắp xếp giả định rằng thuộc tính quyết định vị trí của khóa/phần tử không tự thay đổi trong lúc nó đang được lưu.

Xét một khóa có thể thay đổi:

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

Entry không tự di chuyển sang bucket tương ứng với hash mới. Tương tự, nếu phần tử trong `TreeSet` thay đổi trường mà comparator đọc, cây không tự sắp xếp lại; thứ tự và tra cứu có thể không còn hợp lệ.

Cách an toàn thường là dùng khóa có định danh ổn định, ưu tiên đối tượng bất biến cho khóa, hoặc xóa phần tử trước khi thay đổi thuộc tính định vị rồi chèn lại. Việc thay đổi **giá trị** của `Map` không gây cùng loại rủi ro nếu giá trị không tham gia định danh của khóa, dù các bất biến nghiệp vụ của ứng dụng vẫn phải được giữ riêng.
