# StringBuilder

`StringBuilder` không phải “String có thể sửa”. Nó là **bộ đệm có thể thay đổi dùng để xây String**, sau đó tạo kết quả String bằng `toString()`.

## <a id="builder-mutable-buffer">Bộ đệm có thể thay đổi</a>

```java
StringBuilder builder = new StringBuilder();
builder.append("Hello");
builder.append(' ');
builder.append(name);
String result = builder.toString();
```

Khác String, cùng một đối tượng `StringBuilder` có thể thay đổi bộ đệm nội bộ qua nhiều lần `append`.

Đây là lý do `StringBuilder` phù hợp khi xây chuỗi tăng dần trong một phạm vi kiểm soát rõ.

### VÌ SAO — StringBuilder giải quyết vấn đề gì?

String bất biến rất tốt cho việc chia sẻ, nhưng không lý tưởng nếu ta cần xây kết quả qua rất nhiều bước:

```text
nối String lặp lại
→ nhiều giá trị String bất biến trung gian

StringBuilder
→ một đối tượng xây chuỗi có thể thay đổi
→ append nhiều lần
→ toString khi hoàn tất
```

### Nối tiếp nhiều lời gọi append

Nhiều phương thức thay đổi nội dung trả lại chính đối tượng `StringBuilder`:

```java
String result = new StringBuilder()
        .append("user=")
        .append(userId)
        .append(", active=")
        .append(active)
        .toString();
```

`append` có nhiều overload cho giá trị nguyên thủy, String, `CharSequence` và đối tượng. `StringBuilder` là công cụ xây chuỗi, không phải “String có thể thay đổi”.

## <a id="builder-usage">Xây chuỗi tăng dần</a>

`StringBuilder` phù hợp khi:

- vòng lặp append nhiều phần;
- định dạng đầu ra theo nhiều nhánh;
- tạo văn bản trong phạm vi một phương thức hoặc một luồng;
- cần giảm số String trung gian.

Sau khi gọi `toString()`, String kết quả là bất biến và độc lập về ngữ nghĩa với các thay đổi tiếp theo của `StringBuilder`.

### Giá trị chụp lại tại thời điểm gọi toString

```java
StringBuilder b = new StringBuilder("java");

String first = b.toString();
b.append("-core");
String second = b.toString();

System.out.println(first);  // java
System.out.println(second); // java-core
```

`first` không thay đổi khi `StringBuilder` tiếp tục bị sửa.

### Các thao tác thường gặp

Ngoài `append` còn có:

```text
insert(index, value)
delete(start, end)
replace(start, end, value)
reverse()
```

Chọn chúng khi thật sự đang chỉnh **bộ đệm xây chuỗi**. Nếu đã có giá trị String cuối cùng và chỉ cần biến đổi đơn giản, API String thường diễn đạt mục đích tốt hơn.

### Ranh giới an toàn luồng

`StringBuilder` không đồng bộ hóa nội bộ và không được thiết kế để một đối tượng có thể thay đổi bị nhiều luồng cùng sửa mà không có cơ chế phối hợp.

Mẫu sử dụng phổ biến:

```text
StringBuilder cục bộ trong phương thức/luồng
→ xây nội dung
→ toString
→ đưa giá trị String bất biến ra ngoài
```

## <a id="builder-capacity">Độ dài và dung lượng (capacity)</a>

`length()` là số đơn vị mã UTF-16 hiện có trong `StringBuilder`, tương tự cách `String.length()` được tính.

`capacity()` là dung lượng bộ đệm nội bộ hiện có trước khi cần mở rộng.

Dung lượng (capacity) là chi tiết hiệu năng hữu ích khi dự đoán đầu ra lớn, nhưng không phải một phần của nội dung String kết quả.

Đặt trước dung lượng có thể giảm số lần mở rộng/sao chép nếu biết gần đúng kích thước cuối; không cần tối ưu sớm cho chuỗi nhỏ.

### Dung lượng (capacity) không phải độ dài tối đa

```java
StringBuilder b = new StringBuilder(8);
b.append("this text is longer than eight");
```

`StringBuilder` tự mở rộng khi cần. Dung lượng ban đầu chỉ là gợi ý hiệu năng, không phải giới hạn độ dài tối đa.

Phần triển khai có chiến lược tăng dung lượng riêng; mã ứng dụng không nên phụ thuộc vào một công thức mở rộng dung lượng cụ thể.

`ensureCapacity(n)` có thể hữu ích khi biết cận dưới kích thước khá lớn, nhưng chỉ đáng dùng khi khối lượng xử lý và đo đạc thực tế cho thấy việc mở rộng/sao chép thực sự là vấn đề.

### setLength cần cẩn thận

`setLength` có thể cắt ngắn hoặc mở rộng `StringBuilder`. Khi mở rộng, vị trí mới chứa ký tự null (`\u0000`), không phải khoảng trắng. Vì vậy nó không phải API “đệm bằng khoảng trắng”.

Chương tiếp theo so `StringBuilder` với biến thể có đồng bộ hóa: `StringBuffer`.
