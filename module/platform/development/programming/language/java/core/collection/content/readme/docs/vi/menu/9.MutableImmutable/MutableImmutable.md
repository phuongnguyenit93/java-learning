# Quyền thay đổi dữ liệu và các khung nhìn của Collection

Chương này mở đầu chặng **Khả năng thay đổi, khung nhìn và Fail-Fast** bằng câu hỏi ai sở hữu quyền thay đổi cấu trúc. Khi một phương thức trả về collection, câu hỏi quan trọng không chỉ là “có những phần tử nào” mà còn là “ai được quyền thay đổi cấu trúc này?”. Java có nhiều mức khác nhau: collection có thể sửa, khung nhìn (view) chặn thao tác sửa, bản chụp (snapshot) không liên kết với collection nguồn, và các phương thức tạo collection không cho phép sửa.

Các khái niệm này mô tả **quyền thay đổi collection**. Chúng không tự biến đối tượng bên trong collection thành bất biến. Chương tiếp theo giữ cùng chặng học nhưng tập trung vào điều xảy ra khi cấu trúc bị thay đổi trong lúc Iterator đang duyệt.

## <a id="modifiable-vs-unmodifiable">Có thể sửa và không cho phép sửa</a>

Một collection **có thể sửa** cho phép các thao tác thay đổi phù hợp với quy ước của nó, chẳng hạn `add`, `remove` hoặc `clear`. Một **khung nhìn không cho phép sửa** chặn các thao tác thay đổi đi qua khung nhìn, thường bằng `UnsupportedOperationException`, nhưng collection nguồn đứng phía sau vẫn có thể thay đổi.

```java
List<Order> source = new ArrayList<>();
source.add(new Order(1, 1, 20_000));

List<Order> view = Collections.unmodifiableList(source);

// view.add(...) -> UnsupportedOperationException
source.add(new Order(2, 2, 10_000));

System.out.println(view.size()); // 2: view nhìn thấy thay đổi của source
```

Vì vậy “không cho phép sửa” không đồng nghĩa với “không bao giờ thay đổi”. Nó chỉ nói rằng tham chiếu/khung nhìn này không cung cấp đường sửa cấu trúc.

Với Java 21, cùng ý tưởng được áp dụng cho các sequenced collection qua `Collections.unmodifiableSequencedCollection`, `unmodifiableSequencedSet` và `unmodifiableSequencedMap`. Điều này giữ quy ước về thứ tự duyệt/đầu-cuối trong khi chặn thay đổi qua lớp bọc.

## <a id="immutable-factory">Các phương thức tạo collection không cho phép sửa</a>

`List.of`, `Set.of` và `Map.of`/`Map.ofEntries` tạo ra collection **không cho phép sửa** có nội dung được xác định ngay lúc tạo. Không có collection nguồn có thể thay đổi bên ngoài để sau đó thay đổi cấu trúc phía sau như `Collections.unmodifiableList(source)`.

```java
List<String> users = List.of("U01", "U02", "U03");
Set<String> roles = Set.of("USER", "ADMIN");
Map<String, Integer> limits = Map.of(
        "STANDARD", 10,
        "PREMIUM", 100
);
```

Các quy ước quan trọng:

- Các phương thức tạo này không chấp nhận `null` phần tử/khóa/giá trị.
- `List.of` cho phép phần tử trùng nhau.
- `Set.of` từ chối đối số trùng nhau.
- `Map.of`/`Map.ofEntries` từ chối khóa trùng.
- Không nên dựa vào thứ tự duyệt của `Set.of` hoặc `Map.of` như một quy ước nghiệp vụ.

Cụm “phương thức tạo collection bất biến” đôi khi được dùng trong trao đổi, nhưng về mặt API Java mô tả kết quả là **không cho phép sửa**. Nếu phần tử bên trong có thể thay đổi, trạng thái của phần tử vẫn có thể đổi:

```java
List<StringBuilder> values = List.of(new StringBuilder("A"));
values.getFirst().append("B");

System.out.println(values); // [AB]
```

Collection không thêm/xóa/thay thế phần tử được, còn bản thân `StringBuilder` vẫn có thể thay đổi.

## <a id="copy-of">copyOf và bản chụp cấu trúc</a>

`List.copyOf`, `Set.copyOf` và `Map.copyOf` nhận dữ liệu hiện có và tạo kết quả không cho phép sửa đại diện cho **nội dung tại thời điểm sao chép**.

```java
List<Order> source = new ArrayList<>();
source.add(new Order(1, 1, 20_000));

List<Order> snapshot = List.copyOf(source);

source.add(new Order(2, 2, 10_000));

System.out.println(source.size());   // 2
System.out.println(snapshot.size()); // 1
```

Các phương thức `copyOf` có thể tái sử dụng đối tượng khi đầu vào đã là collection không cho phép sửa phù hợp. Bên gọi không nên dựa vào định danh đối tượng; quy ước cần dựa vào nội dung và khả năng thay đổi.

Đây vẫn là **bản sao nông (shallow copy)**: tham chiếu tới các phần tử được sao chép, đối tượng bên trong không được sao chép sâu. Nếu một `Order` có thể thay đổi bị sửa thông qua tham chiếu khác, bản chụp vẫn quan sát trạng thái mới của chính đối tượng đó.

`copyOf` còn có các quy ước quan trọng về dữ liệu đầu vào:

- `List.copyOf` / `Set.copyOf` từ chối `null` phần tử;
- `Map.copyOf` từ chối `null` khóa và `null` giá trị;
- `List.copyOf` giữ thứ tự duyệt của collection nguồn trong list kết quả;
- `Set.copyOf` và `Map.copyOf` **không hứa bảo toàn thứ tự duyệt** của `LinkedHashSet`/`LinkedHashMap` nguồn.

Vì vậy `copyOf` là bản chụp về **nội dung**, nhưng không nên tự suy ra rằng mọi đặc tính quan sát như thứ tự chèn của một cách triển khai nguồn cũng được bản chụp theo.

Một điểm cần nhớ: `Set.copyOf(collection)` có thể nhận collection nguồn chứa các phần tử bằng nhau và chỉ giữ một đại diện; điều này khác với `Set.of(a, b, ...)`, nơi đối số trùng lặp bị từ chối.

## <a id="backed-view-fixed-size-snapshot">Khung nhìn liên kết, cấu trúc cố định kích thước và bản chụp</a>

Ba khái niệm này rất dễ bị gom chung thành “list không sửa được”, nhưng chúng có hành vi khác nhau:

| Dạng | Ví dụ | Có nhìn thấy nguồn đổi? | Có đổi kích thước qua tham chiếu này? |
| --- | --- | --- | --- |
| khung nhìn có thể thay đổi, liên kết với nguồn | `list.subList(...)`, `map.keySet()` | có | tùy thao tác/khung nhìn |
| khung nhìn không cho phép sửa, liên kết với dữ liệu gốc | `Collections.unmodifiableList(source)` | có | không |
| cấu trúc cố định kích thước, liên kết với mảng | `Arrays.asList(array)` | có, hai chiều với mảng | không, nhưng `set` được |
| bản chụp không cho phép sửa | `List.copyOf(source)` | không thấy thay đổi cấu trúc sau khi sao chép | không |

Ví dụ khung nhìn của `Map`:

```java
Map<Long, Order> byId = new HashMap<>();
byId.put(1L, orderA);
byId.put(2L, orderB);

Set<Long> keys = byId.keySet();
keys.remove(1L);

System.out.println(byId.containsKey(1L)); // false
```

Ví dụ `subList`:

```java
List<String> source = new ArrayList<>(List.of("A", "B", "C"));
List<String> tail = source.subList(1, 3);
tail.set(0, "X");

System.out.println(source); // [A, X, C]
```

Ví dụ cấu trúc cố định kích thước:

```java
String[] array = {"A", "B"};
List<String> fixed = Arrays.asList(array);

fixed.set(0, "X");
System.out.println(array[0]); // X

// fixed.add("C"); // UnsupportedOperationException
```

Điểm cần hỏi không phải chỉ là “có gọi được `add` không?”, mà là **tham chiếu này có chia sẻ vùng lưu trữ/trạng thái với nguồn không, thao tác thay đổi nào được phép, và thay đổi từ phía nào sẽ được quan sát ở phía kia?**

## <a id="view-vs-copy">Khung nhìn và bản sao phòng vệ</a>

Sự khác biệt nên được quyết định từ quyền sở hữu dữ liệu:

```java
List<Order> source = new ArrayList<>(List.of(
        new Order(1, 1, 20_000),
        new Order(2, 2, 10_000)
));

List<Order> readOnlyView = Collections.unmodifiableList(source);
List<Order> snapshot = List.copyOf(source);
List<Order> reverseView = source.reversed(); // Java 21: khung nhìn theo thứ tự ngược

source.add(new Order(3, 3, 15_000));

System.out.println(readOnlyView.size()); // 3
System.out.println(snapshot.size());     // 2
System.out.println(reverseView.getFirst().id()); // 3
```

Chọn **khung nhìn** khi muốn nhiều phía quan sát cùng một collection đang tồn tại và thay đổi của bên sở hữu phải xuất hiện ngay ở khung nhìn. Chọn **bản sao phòng vệ (defensive copy)** khi ranh giới API cần một bản chụp độc lập để bên gọi không bị ảnh hưởng bởi thay đổi cấu trúc sau đó của collection nguồn.

Cả khung nhìn lẫn bản sao đều không tự giải quyết an toàn luồng (thread-safety) hoặc tính bất biến sâu (deep immutability). Nếu collection được chia sẻ giữa nhiều luồng, cần thêm cơ chế đồng bộ hoặc collection hỗ trợ truy cập đồng thời phù hợp; phần đó thuộc module về xử lý đồng thời.
