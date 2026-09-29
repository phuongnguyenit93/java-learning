# Mutable và Immutable Collection

Khi một phương thức trả về collection, câu hỏi quan trọng không chỉ là “có những phần tử nào” mà còn là “ai được quyền thay đổi cấu trúc này?”. Java có nhiều mức khác nhau: collection có thể sửa, khung nhìn (view) chặn thao tác sửa, bản chụp (snapshot) không liên kết với collection nguồn, và các factory tạo collection không sửa được.

Các khái niệm này mô tả **quyền thay đổi collection**. Chúng không tự biến object bên trong collection thành immutable.

## <a id="modifiable-vs-unmodifiable">Modifiable và unmodifiable</a>

Một collection **modifiable** cho phép các thao tác thay đổi phù hợp với quy ước của nó, chẳng hạn `add`, `remove` hoặc `clear`. Một **unmodifiable view** chặn các thao tác thay đổi đi qua khung nhìn, thường bằng `UnsupportedOperationException`, nhưng collection nguồn đứng phía sau vẫn có thể thay đổi.

```java
List<Order> source = new ArrayList<>();
source.add(new Order(1, 1, 20_000));

List<Order> view = Collections.unmodifiableList(source);

// view.add(...) -> UnsupportedOperationException
source.add(new Order(2, 2, 10_000));

System.out.println(view.size()); // 2: view nhìn thấy thay đổi của source
```

Vì vậy “unmodifiable” không đồng nghĩa với “không bao giờ thay đổi”. Nó chỉ nói rằng reference/view này không cung cấp đường sửa cấu trúc.

Với Java 21, cùng ý tưởng được áp dụng cho sequenced collections qua `Collections.unmodifiableSequencedCollection`, `unmodifiableSequencedSet` và `unmodifiableSequencedMap`. Điều này giữ quy ước về encounter order/đầu-cuối trong khi chặn thay đổi qua wrapper.

## <a id="immutable-factory">Factory tạo collection không sửa được</a>

`List.of`, `Set.of` và `Map.of`/`Map.ofEntries` tạo ra collection **unmodifiable** có nội dung được xác định ngay lúc tạo. Không có collection nguồn mutable bên ngoài để sau đó thay đổi cấu trúc phía sau như `Collections.unmodifiableList(source)`.

```java
List<String> users = List.of("U01", "U02", "U03");
Set<String> roles = Set.of("USER", "ADMIN");
Map<String, Integer> limits = Map.of(
        "STANDARD", 10,
        "PREMIUM", 100
);
```

Các quy ước quan trọng:

- Các factory này không chấp nhận `null` element/key/value.
- `List.of` cho phép phần tử trùng nhau.
- `Set.of` từ chối argument trùng nhau.
- `Map.of`/`Map.ofEntries` từ chối key trùng.
- Không nên dựa vào encounter order của `Set.of` hoặc `Map.of` như một quy ước nghiệp vụ.

Tên gọi “immutable factory” thường được dùng trong trao đổi, nhưng về mặt API Java mô tả kết quả là **unmodifiable**. Nếu element bên trong là mutable, trạng thái của element vẫn có thể đổi:

```java
List<StringBuilder> values = List.of(new StringBuilder("A"));
values.getFirst().append("B");

System.out.println(values); // [AB]
```

Collection không thêm/xóa/thay thế element được, còn bản thân `StringBuilder` vẫn mutable.

## <a id="copy-of">copyOf và bản chụp cấu trúc</a>

`List.copyOf`, `Set.copyOf` và `Map.copyOf` nhận dữ liệu hiện có và tạo kết quả unmodifiable đại diện cho **nội dung tại thời điểm copy**.

```java
List<Order> source = new ArrayList<>();
source.add(new Order(1, 1, 20_000));

List<Order> snapshot = List.copyOf(source);

source.add(new Order(2, 2, 10_000));

System.out.println(source.size());   // 2
System.out.println(snapshot.size()); // 1
```

Các phương thức `copyOf` có thể tái sử dụng instance khi đầu vào đã là unmodifiable collection phù hợp. Bên gọi không nên dựa vào định danh object; quy ước cần dựa vào nội dung và khả năng thay đổi.

Đây vẫn là **bản sao nông (shallow copy)**: reference tới các element được sao chép, object bên trong không được clone. Nếu một `Order` mutable bị thay đổi thông qua reference khác, bản chụp vẫn quan sát trạng thái mới của chính object đó.

`copyOf` còn có các contract quan trọng về dữ liệu đầu vào:

- `List.copyOf` / `Set.copyOf` từ chối `null` element;
- `Map.copyOf` từ chối `null` key và `null` value;
- `List.copyOf` giữ iteration order của collection nguồn trong list kết quả;
- `Set.copyOf` và `Map.copyOf` **không hứa bảo toàn encounter order** của `LinkedHashSet`/`LinkedHashMap` nguồn.

Vì vậy `copyOf` là snapshot về **nội dung**, nhưng không nên tự suy ra rằng mọi đặc tính quan sát như insertion order của một implementation nguồn cũng được snapshot theo.

Một điểm cần nhớ: `Set.copyOf(collection)` có thể nhận collection nguồn chứa các phần tử bằng nhau và chỉ giữ một đại diện; điều này khác với `Set.of(a, b, ...)`, nơi argument trùng lặp bị từ chối.

## <a id="backed-view-fixed-size-snapshot">Backed view, fixed-size adapter và snapshot</a>

Ba khái niệm này rất dễ bị gom chung thành “list không sửa được”, nhưng chúng có behavior khác nhau:

| Dạng | Ví dụ | Có nhìn thấy source đổi? | Có đổi size qua reference này? |
| --- | --- | --- | --- |
| backed mutable view | `list.subList(...)`, `map.keySet()` | có | tùy operation/view |
| unmodifiable backed view | `Collections.unmodifiableList(source)` | có | không |
| fixed-size backed adapter | `Arrays.asList(array)` | có, hai chiều với array | không, nhưng `set` được |
| unmodifiable snapshot | `List.copyOf(source)` | không với structural change sau copy | không |

Ví dụ `Map` view:

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

Ví dụ fixed-size adapter:

```java
String[] array = {"A", "B"};
List<String> fixed = Arrays.asList(array);

fixed.set(0, "X");
System.out.println(array[0]); // X

// fixed.add("C"); // UnsupportedOperationException
```

Điểm cần hỏi không phải chỉ là “có gọi được `add` không?”, mà là **reference này có share backing storage/state với nguồn không, mutation nào được phép, và thay đổi từ phía nào sẽ được quan sát ở phía kia?**

## <a id="view-vs-copy">View và bản sao phòng vệ</a>

Sự khác biệt nên được quyết định từ ownership:

```java
List<Order> source = new ArrayList<>(List.of(
        new Order(1, 1, 20_000),
        new Order(2, 2, 10_000)
));

List<Order> readOnlyView = Collections.unmodifiableList(source);
List<Order> snapshot = List.copyOf(source);
List<Order> reverseView = source.reversed(); // Java 21: view theo thứ tự ngược

source.add(new Order(3, 3, 15_000));

System.out.println(readOnlyView.size()); // 3
System.out.println(snapshot.size());     // 2
System.out.println(reverseView.getFirst().id()); // 3
```

Chọn **view** khi muốn nhiều phía quan sát cùng một collection đang sống và thay đổi của bên sở hữu phải xuất hiện ngay ở view. Chọn **bản sao phòng vệ (defensive copy)** khi ranh giới API cần một bản chụp độc lập để bên gọi không bị ảnh hưởng bởi thay đổi cấu trúc sau đó của collection nguồn.

Cả view lẫn bản sao đều không tự giải quyết an toàn luồng (thread-safety) hoặc tính bất biến sâu (deep immutability). Nếu collection được chia sẻ giữa nhiều thread, cần thêm cơ chế đồng bộ/concurrent collection phù hợp; phần đó thuộc nhóm kiến thức concurrency.
