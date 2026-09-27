# EnumSet và EnumMap

Khi domain key/phần tử đến từ một `enum`, tập giá trị hợp lệ đã được biết trước và có thứ tự khai báo cố định. `EnumSet` và `EnumMap` tận dụng đúng đặc điểm này để biểu diễn intent rõ hơn so với collection tổng quát như `HashSet`/`HashMap`.

Ta dùng trạng thái order làm ví dụ:

```java
enum OrderStatus {
    NEW,
    PAID,
    PACKING,
    SHIPPED,
    CANCELLED
}
```

## <a id="enumset-model">Mô hình EnumSet</a>

`EnumSet<E extends Enum<E>>` là `Set` chuyên cho phần tử của **một kiểu enum**. JDK mô tả cách biểu diễn của nó như một bit vector, nên mỗi enum constant có thể được ánh xạ rất gọn vào một vị trí bit.

```java
EnumSet<OrderStatus> active = EnumSet.of(
        OrderStatus.NEW,
        OrderStatus.PAID,
        OrderStatus.PACKING
);

boolean needsWork = active.contains(OrderStatus.PAID);
```

Các factory làm intent domain rõ:

```java
EnumSet<OrderStatus> none = EnumSet.noneOf(OrderStatus.class);
EnumSet<OrderStatus> all = EnumSet.allOf(OrderStatus.class);
EnumSet<OrderStatus> shippingFlow =
        EnumSet.range(OrderStatus.PAID, OrderStatus.SHIPPED);
EnumSet<OrderStatus> inactive = EnumSet.complementOf(active);
```

`EnumSet` luôn duyệt theo **natural order của enum**, tức thứ tự các constant được khai báo, không phải thứ tự insert. Nó không cho `null` element và không synchronized.

Order xác định không đồng nghĩa với Java 21 `SequencedSet`: `EnumSet` có declaration-order iteration riêng nhưng không triển khai `SequencedSet`. Vì vậy không nên suy ra API first/last/reversed của sequenced collections chỉ từ việc một collection có thứ tự duyệt ổn định.

Iterator của `EnumSet` là **weakly consistent**: không ném `ConcurrentModificationException` và có thể có hoặc không phản ánh thay đổi xảy ra sau khi iterator được tạo. Quy ước này khác với fail-fast iterator của các collection như `ArrayList`.

Vì tập enum là hữu hạn và đã biết, `EnumSet` rất phù hợp cho các nhóm flag/capability/status. Nếu nghiệp vụ cần giữ thứ tự chèn tùy ý, `LinkedHashSet` mới là quy ước đúng hơn.

## <a id="enummap-model">Mô hình EnumMap</a>

`EnumMap<K extends Enum<K>, V>` là `Map` chuyên cho enum key. JDK triển khai nó bằng cách biểu diễn dựa trên array, tận dụng `ordinal` nội bộ để truy cập vị trí key hiệu quả mà không cần hash bucket tổng quát.

```java
EnumMap<OrderStatus, Integer> counts =
        new EnumMap<>(OrderStatus.class);

counts.put(OrderStatus.NEW, 5);
counts.put(OrderStatus.PAID, 12);
counts.put(OrderStatus.SHIPPED, 8);

System.out.println(counts.get(OrderStatus.PAID)); // 12
```

Key được duyệt theo thứ tự khai báo của enum, tạo encounter order ổn định và dễ dự đoán:

```java
for (var entry : counts.entrySet()) {
    System.out.println(entry.getKey() + " = " + entry.getValue());
}
```

`EnumMap` không cho `null` key nhưng cho phép `null` value. Nếu lưu null value, `get(key) == null` không phân biệt được “không có mapping” và “mapping có value null”; dùng `containsKey` khi cần phân biệt.

Tương tự `EnumSet`, `EnumMap` có encounter order xác định nhưng không triển khai `SequencedMap`. `SequencedMap` chuẩn hóa first/last/reversed cho những map tham gia interface đó; nó không phải điều kiện bắt buộc để một map có order xác định.

Iterator của các collection view của `EnumMap` cũng **weakly consistent**: không ném `ConcurrentModificationException` và có thể có hoặc không quan sát thay đổi sau thời điểm tạo iterator.

Giống `EnumSet`, `EnumMap` không synchronized. Khi cần chia sẻ mutation giữa thread, phải áp dụng concurrency strategy riêng.

## <a id="enum-collection-benefits">Lợi ích và giới hạn của enum collection</a>

Enum-specific collection đem lại ba lợi ích chính:

- **Diễn đạt domain rõ:** type signature nói ngay rằng chỉ một enum universe cụ thể được phép làm element/key.
- **Thứ tự ổn định:** traversal theo thứ tự khai báo enum, không phụ thuộc hash encounter order.
- **Representation chuyên biệt:** bit vector cho `EnumSet` và array-oriented storage cho `EnumMap` thường gọn và hiệu quả hơn cấu trúc hash tổng quát cho cùng domain.

Ví dụ một policy theo trạng thái:

```java
EnumSet<OrderStatus> cancellable =
        EnumSet.of(OrderStatus.NEW, OrderStatus.PAID);

EnumMap<OrderStatus, String> labels =
        new EnumMap<>(OrderStatus.class);
labels.put(OrderStatus.NEW, "Waiting for payment");
labels.put(OrderStatus.PAID, "Paid");
labels.put(OrderStatus.SHIPPED, "On the way");
```

Đổi lại, ordering của chúng gắn với thứ tự khai báo enum. Nếu nghiệp vụ cần thứ tự chèn (insertion order), thứ tự truy cập (access order) hoặc thứ tự comparator tùy ý thì `LinkedHashMap`, `TreeMap`, `LinkedHashSet` hoặc `TreeSet` có thể phù hợp hơn.

Đừng chọn `EnumSet`/`EnumMap` chỉ vì “nhanh hơn”. Lợi ích lớn nhất là chúng làm rõ rằng miền dữ liệu có một tập key/phần tử đóng và xác định. Hiệu năng và hiệu quả bộ nhớ là hệ quả tốt của cách biểu diễn chuyên biệt đó.
