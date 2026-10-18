# So sánh String

String Pool dễ làm người mới tưởng rằng `==` có thể dùng để so nội dung. Điều đó chỉ “tình cờ đúng” trong một số trường hợp nhiều biến cùng giữ một tham chiếu.

## <a id="string-equals">So sánh nội dung bằng equals</a>

`String.equals` so sánh chuỗi nội dung theo hợp đồng của `String`:

```java
String a = new String("java");
String b = new String("java");

System.out.println(a.equals(b)); // true
```

Khi câu hỏi là “hai giá trị văn bản có cùng nội dung không?”, `equals` là lựa chọn mặc định.

### equals phân biệt chữ hoa và chữ thường

```java
"Java".equals("java"); // false
```

`equals` kiểm tra sự bằng nhau của giá trị String; nó không tự chuẩn hóa Unicode, không thực hiện so sánh thứ tự theo vùng miền và cũng không thực hiện mọi quy tắc gấp chữ hoa/thường.

### So sánh an toàn khi có null

Gọi phương thức trên `null` sẽ lỗi:

```java
String actual = null;
// actual.equals("java"); // NullPointerException
```

Khi giá trị mong đợi chắc chắn khác null:

```java
"java".equals(actual); // false
```

Khi cả hai phía đều có thể null:

```java
Objects.equals(left, right);
```

`Objects.equals` xử lý null trước rồi mới gọi tiếp `equals` khi phù hợp.

### Thứ tự sắp xếp khác với phép so sánh bằng nhau

`compareTo` trả lời câu hỏi về thứ tự từ điển theo chuỗi giá trị `char`/đơn vị mã UTF-16 của String:

```java
"abc".compareTo("abc"); // 0
"abc".compareTo("abd"); // < 0
"abd".compareTo("abc"); // > 0
```

Đừng giả định kết quả luôn là `-1`, `0` hoặc `1`; quy ước quan trọng là **dấu** của kết quả.

Đây là thứ tự tự nhiên của `String`, không phải quy tắc sắp xếp theo ngôn ngữ của con người. Nó hữu ích cho nhiều giá trị kỹ thuật; sắp xếp theo ngôn ngữ/vùng miền có thể cần `Collator` trong mô-đun Bản địa hóa (Localization).

## <a id="string-reference-equality">== kiểm tra định danh tham chiếu</a>

`==` với kiểu tham chiếu chỉ hỏi hai biến có cùng trỏ tới một đối tượng hay không.

```java
System.out.println(a == b);
```

không phải phép so sánh nội dung.

String Pool làm một số literal dùng chung tham chiếu, nhưng mã đúng không nên phụ thuộc vào cơ chế tối ưu đó để quyết định hai chuỗi có cùng nội dung hay không.

Mô hình tư duy:

```text
a ─────► đối tượng String #1: "java"

b ─────► đối tượng String #2: "java"

a == b
→ đối tượng #1 có phải đối tượng #2?

a.equals(b)
→ chuỗi nội dung có bằng nhau?
```

`==` với String chỉ nên được dùng khi **định danh tham chiếu chính là câu hỏi cần quan sát**, ví dụ thí nghiệm về String Pool hoặc `intern()`.

## <a id="case-insensitive-boundary">So sánh không phân biệt hoa thường</a>

`equalsIgnoreCase` hữu ích cho một số phép so sánh đơn giản, nhưng quy tắc chữ hoa/thường có thể phụ thuộc ngôn ngữ và vùng miền.

Quan trọng: `equalsIgnoreCase` **không dùng Locale**. Nó áp dụng quy tắc so sánh không phân biệt hoa/thường của String, nên không phải phép sắp xếp/so sánh theo vùng miền và có thể không cho ngữ nghĩa phù hợp với một số ngôn ngữ.

Nếu bài toán thực sự cần so khớp văn bản theo vùng miền, hãy dùng API và `Locale` phù hợp thay vì mặc định gọi `toLowerCase()` không tham số rồi so chuỗi.

Lỗi tư duy phổ biến:

```java
a.toLowerCase().equals(b.toLowerCase());
```

Lời gọi không truyền `Locale` dùng locale mặc định của tiến trình và còn tạo String trung gian. Nếu nghiệp vụ cần so sánh theo vùng miền, locale phải là một phần được chỉ rõ trong hợp đồng xử lý.

Nếu đây là mã định danh kỹ thuật hoặc token của giao thức, hãy theo đúng quy tắc của giao thức/nghiệp vụ; đừng coi việc chuyển chữ thường theo locale mặc định là một phép chuẩn hóa dùng được cho mọi trường hợp.

```text
mã định danh kỹ thuật/token giao thức
→ thường cần quy tắc ổn định, không phụ thuộc locale

văn bản ngôn ngữ tự nhiên
→ có thể cần so sánh/sắp xếp theo locale
```

Mô-đun Bản địa hóa (Localization) đi sâu hơn vào `Locale` và `Collator`.

Chương tiếp theo xây mô hình tư duy cho các thao tác String dùng hằng ngày: kiểm tra, tìm kiếm, trích xuất, biến đổi, tách/ghép và định dạng.
