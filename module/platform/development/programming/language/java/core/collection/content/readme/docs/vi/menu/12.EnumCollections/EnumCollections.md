# Collection chuyên biệt cho enum — EnumSet và EnumMap

Khi miền khóa/phần tử đến từ một `enum`, tập giá trị hợp lệ đã được biết trước và có thứ tự khai báo cố định. `EnumSet` và `EnumMap` tận dụng đúng đặc điểm này để biểu diễn ý định rõ hơn so với collection tổng quát như `HashSet`/`HashMap`.

Ta dùng trạng thái đơn hàng làm ví dụ:

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

`EnumSet<E extends Enum<E>>` là `Set` chuyên cho phần tử của **một kiểu enum**. JDK mô tả cách biểu diễn của nó như một vector bit, nên mỗi hằng enum có thể được ánh xạ rất gọn vào một vị trí bit.

```java
EnumSet<OrderStatus> active = EnumSet.of(
        OrderStatus.NEW,
        OrderStatus.PAID,
        OrderStatus.PACKING
);

boolean needsWork = active.contains(OrderStatus.PAID);
```

Các phương thức tạo làm ý định miền rõ:

```java
EnumSet<OrderStatus> none = EnumSet.noneOf(OrderStatus.class);
EnumSet<OrderStatus> all = EnumSet.allOf(OrderStatus.class);
EnumSet<OrderStatus> shippingFlow =
        EnumSet.range(OrderStatus.PAID, OrderStatus.SHIPPED);
EnumSet<OrderStatus> inactive = EnumSet.complementOf(active);
```

`EnumSet` luôn duyệt theo **thứ tự tự nhiên của enum**, tức thứ tự các hằng được khai báo, không phải thứ tự chèn. Nó không cho phần tử `null` và không tự đồng bộ hóa.

Có thứ tự xác định không đồng nghĩa với việc là `SequencedSet` trong Java 21: `EnumSet` có thứ tự duyệt theo thứ tự khai báo riêng nhưng không triển khai `SequencedSet`. Vì vậy không nên suy ra API đầu/cuối/đảo thứ tự của sequenced collection chỉ từ việc một collection có thứ tự duyệt ổn định.

Iterator của `EnumSet` là **nhất quán yếu (weakly consistent)**: không ném `ConcurrentModificationException` và có thể có hoặc không phản ánh thay đổi xảy ra sau khi iterator được tạo. Quy ước này khác với iterator fail-fast của các collection như `ArrayList`.

Vì tập enum là hữu hạn và đã biết, `EnumSet` rất phù hợp cho các nhóm cờ, khả năng hoặc trạng thái. Nếu nghiệp vụ cần giữ thứ tự chèn tùy ý, `LinkedHashSet` mới là quy ước đúng hơn.

## <a id="enummap-model">Mô hình EnumMap</a>

`EnumMap<K extends Enum<K>, V>` là `Map` chuyên cho khóa enum. JDK triển khai nó bằng cách biểu diễn dựa trên mảng, tận dụng `ordinal` nội bộ để truy cập vị trí khóa hiệu quả mà không cần bucket hash tổng quát.

```java
EnumMap<OrderStatus, Integer> counts =
        new EnumMap<>(OrderStatus.class);

counts.put(OrderStatus.NEW, 5);
counts.put(OrderStatus.PAID, 12);
counts.put(OrderStatus.SHIPPED, 8);

System.out.println(counts.get(OrderStatus.PAID)); // 12
```

Khóa được duyệt theo thứ tự khai báo của enum, tạo thứ tự duyệt ổn định và dễ dự đoán:

```java
for (var entry : counts.entrySet()) {
    System.out.println(entry.getKey() + " = " + entry.getValue());
}
```

`EnumMap` không cho `null` khóa nhưng cho phép `null` giá trị. Nếu lưu null giá trị, `get(key) == null` không phân biệt được “không có ánh xạ” và “ánh xạ có giá trị null”; dùng `containsKey` khi cần phân biệt.

Tương tự `EnumSet`, `EnumMap` có thứ tự duyệt xác định nhưng không triển khai `SequencedMap`. `SequencedMap` chuẩn hóa các thao tác đầu/cuối/đảo thứ tự cho những Map tham gia interface đó; nó không phải điều kiện bắt buộc để một Map có thứ tự xác định.

Iterator của các khung nhìn dạng Collection của `EnumMap` cũng **nhất quán yếu (weakly consistent)**: không ném `ConcurrentModificationException` và có thể có hoặc không quan sát thay đổi sau thời điểm tạo iterator.

Giống `EnumSet`, `EnumMap` không tự đồng bộ hóa. Khi cần chia sẻ dữ liệu có thể thay đổi giữa nhiều luồng, phải áp dụng chiến lược xử lý đồng thời riêng.

## <a id="enum-collection-benefits">Lợi ích và giới hạn của enum collection</a>

Collection chuyên biệt cho enum đem lại ba lợi ích chính:

- **Diễn đạt miền rõ:** chữ ký kiểu cho biết ngay chỉ một tập giá trị enum cụ thể được phép làm phần tử/khóa.
- **Thứ tự ổn định:** duyệt theo thứ tự khai báo enum, không phụ thuộc thứ tự từ cấu trúc hash.
- **Cách biểu diễn chuyên biệt:** vector bit cho `EnumSet` và lưu trữ dựa trên mảng cho `EnumMap` thường gọn và hiệu quả hơn cấu trúc hash tổng quát cho cùng miền.

Ví dụ một chính sách theo trạng thái:

```java
EnumSet<OrderStatus> cancellable =
        EnumSet.of(OrderStatus.NEW, OrderStatus.PAID);

EnumMap<OrderStatus, String> labels =
        new EnumMap<>(OrderStatus.class);
labels.put(OrderStatus.NEW, "Waiting for payment");
labels.put(OrderStatus.PAID, "Paid");
labels.put(OrderStatus.SHIPPED, "On the way");
```

Đổi lại, thứ tự của chúng gắn với thứ tự khai báo enum. Nếu nghiệp vụ cần thứ tự chèn, thứ tự truy cập hoặc thứ tự do comparator tùy chỉnh xác định thì `LinkedHashMap`, `TreeMap`, `LinkedHashSet` hoặc `TreeSet` có thể phù hợp hơn.

Đừng chọn `EnumSet`/`EnumMap` chỉ vì “nhanh hơn”. Lợi ích lớn nhất là chúng làm rõ rằng miền dữ liệu có một tập khóa/phần tử đóng và xác định. Hiệu năng và hiệu quả bộ nhớ là hệ quả tốt của cách biểu diễn chuyên biệt đó.
