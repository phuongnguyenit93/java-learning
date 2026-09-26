# So sánh String

String pool dễ làm người mới tưởng rằng `==` có thể dùng để so nội dung. Điều đó chỉ “tình cờ đúng” trong một số trường hợp identity được chia sẻ.

## <a id="string-equals">So sánh nội dung bằng equals</a>

`String.equals` so sequence nội dung theo hợp đồng của `String`:

```java
String a = new String("java");
String b = new String("java");

a.equals(b) // true
```

Khi câu hỏi là “hai text value có cùng nội dung không?”, `equals` là lựa chọn mặc định.

## <a id="string-reference-equality">== kiểm tra Identity</a>

`==` với reference chỉ hỏi hai biến có cùng trỏ tới một object hay không.

```java
a == b
```

không phải content comparison.

Pool làm một số literal chia sẻ identity, nhưng mã đúng không nên phụ thuộc vào optimization đó để quyết định text equality.

## <a id="case-insensitive-boundary">So sánh không phân biệt hoa thường</a>

`equalsIgnoreCase` hữu ích cho một số comparison đơn giản, nhưng case chuyển đổi có thể phụ thuộc ngôn ngữ/locale.

Nếu bài toán thực sự là locale-sensitive text matching, hãy xem xét API/locale quy tắc phù hợp thay vì mặc định `toLowerCase()` không tham số rồi so chuỗi.

```text
technical identifier/protocol token
→ thường cần quy tắc ổn định, không phụ thuộc locale

human language text
→ có thể cần locale-aware comparison/collation
```

Module `localization` đi sâu hơn vào locale/collation.

chương tiếp theo hỏi: nếu String không mutate, điều gì xảy ra khi ta nối String nhiều lần?
