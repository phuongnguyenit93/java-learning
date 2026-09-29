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

### equals là case-sensitive

```java
"Java".equals("java"); // false
```

`equals` kiểm tra equality của String value; nó không thực hiện Unicode normalization, locale-sensitive collation hay case folding toàn diện.

### Null-safe comparison

Call method trên `null` sẽ lỗi:

```java
String actual = null;
// actual.equals("java"); // NullPointerException
```

Khi expected value chắc chắn non-null:

```java
"java".equals(actual); // false
```

Khi cả hai phía đều có thể null:

```java
Objects.equals(left, right);
```

`Objects.equals` xử lý null trước rồi mới delegate tới `equals`.

### Ordering khác equality

`compareTo` trả lời câu hỏi lexicographic ordering:

```java
"abc".compareTo("abc"); // 0
"abc".compareTo("abd"); // < 0
"abd".compareTo("abc"); // > 0
```

Đừng assume result luôn là `-1`, `0` hoặc `1`; contract quan trọng là **dấu** của kết quả.

Natural ordering này hữu ích cho technical ordering của String. Human-language ordering có thể cần `Collator` trong module `localization`.

### contentEquals

Khi cần so với một `CharSequence` khác:

```java
String text = "java";
StringBuilder builder = new StringBuilder("java");

text.contentEquals(builder); // true
```

Không cần convert mọi character sequence thành String chỉ để kiểm tra content.

## <a id="string-reference-equality">== kiểm tra Identity</a>

`==` với reference chỉ hỏi hai biến có cùng trỏ tới một object hay không.

```java
a == b
```

không phải content comparison.

Pool làm một số literal chia sẻ identity, nhưng mã đúng không nên phụ thuộc vào optimization đó để quyết định text equality.

Mental model:

```text
a ─────► String object #1: "java"

b ─────► String object #2: "java"

a == b
→ object #1 có phải object #2?

a.equals(b)
→ text sequence có bằng nhau?
```

`==` với String chỉ nên được dùng khi **identity chính là câu hỏi cần quan sát**, ví dụ experiment về pool/intern.

## <a id="case-insensitive-boundary">So sánh không phân biệt hoa thường</a>

`equalsIgnoreCase` hữu ích cho một số comparison đơn giản, nhưng case chuyển đổi có thể phụ thuộc ngôn ngữ/locale.

Quan trọng: `equalsIgnoreCase` **không dùng Locale**. Nó áp dụng case-insensitive rule của String, nên không phải locale-aware collation và có thể không cho semantic phù hợp với một số ngôn ngữ.

Nếu bài toán thực sự là locale-sensitive text matching, hãy xem xét API/locale quy tắc phù hợp thay vì mặc định `toLowerCase()` không tham số rồi so chuỗi.

Pitfall phổ biến:

```java
a.toLowerCase().equals(b.toLowerCase())
```

Call không truyền `Locale` dùng default locale của process và còn tạo intermediate String. Nếu domain cần locale-sensitive comparison, locale phải là một phần explicit của contract.

Nếu đây là technical identifier/protocol token, hãy theo rule của protocol/domain; đừng coi lowercase theo default locale là universal normalization.

```text
technical identifier/protocol token
→ thường cần quy tắc ổn định, không phụ thuộc locale

human language text
→ có thể cần locale-aware comparison/collation
```

Module `localization` đi sâu hơn vào locale/collation.

Chương tiếp theo xây mental model cho các thao tác String dùng hằng ngày: inspection, search, extraction, transformation, split/join và formatting.
