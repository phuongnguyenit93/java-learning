# Unicode và Code Point

Một trong những hiểu nhầm phổ biến nhất về Java text là: **một `char` luôn bằng một ký tự người dùng nhìn thấy**. Điều đó không đúng với Unicode hiện đại.

## <a id="utf16-char-model">Java char và UTF-16</a>

`char` là một **UTF-16 code unit 16-bit**.

Nhiều Unicode character nằm trong Basic Multilingual Plane có thể biểu diễn bằng một code unit. Nhưng character ngoài vùng đó cần hai code unit.

Vì vậy:

```java
text.length()
```

trả số UTF-16 code unit, không đảm bảo là số Unicode code point hay số ký tự người dùng nhìn thấy.

Ví dụ:

```java
String text = "A😀";

text.length(); // 3 code units: 'A' + surrogate pair
```

Đây là nền tảng để hiểu vì sao các API index-based như `charAt` và `substring` dùng offset theo UTF-16 code unit.

## <a id="code-point">Unicode Code Point</a>

**code point** là một giá trị Unicode như `U+0041` hoặc `U+1F600`.

Java thường biểu diễn code point bằng `int`, vì toàn bộ không gian Unicode lớn hơn phạm vi một `char`.

Code point thường được viết dạng:

```text
U+0041  → A
U+00E9  → é
U+1F600 → 😀
```

API như:

```java
text.codePointCount(...)
text.codePoints()
```

giúp làm việc ở mức code point thay vì code unit.

Một helper quan trọng khác:

```java
Character.toChars(0x1F600); // char[] surrogate pair cho 😀
```

`int` ở đây không có nghĩa mọi integer đều là valid code point; dùng `Character.isValidCodePoint` khi input có thể không tin cậy.

## <a id="surrogate-pairs">Surrogate Pair</a>

Trong UTF-16, code point ngoài BMP được biểu diễn bằng một **high surrogate + low surrogate**.

Ví dụ emoji có thể khiến:

```text
String.length() == 2
codePointCount(...) == 1
```

Nếu iterate từng `char`, mã có thể tách đôi một code point hợp lệ.

```java
String emoji = "😀";

char high = emoji.charAt(0);
char low  = emoji.charAt(1);

Character.isHighSurrogate(high); // true
Character.isLowSurrogate(low);   // true
```

Hai `char` riêng lẻ không tương đương hai ký tự người dùng.

## <a id="unicode-iteration">Duyệt theo Code Point</a>

Khi logic thực sự cần xử lý Unicode code point, dùng API code-point-aware:

```java
text.codePoints().forEach(cp -> ...);
```

hoặc `codePointAt` kết hợp `Character.charCount(cp)` để tăng index đúng số code unit.

Ví dụ indexed iteration:

```java
for (int i = 0; i < text.length(); ) {
    int cp = text.codePointAt(i);
    // process cp
    i += Character.charCount(cp);
}
```

Khi cần di chuyển N code point từ một offset, xem `offsetByCodePoints` thay vì tự cộng index.

Không cần chuyển mọi String loop sang code point. Chọn level biểu diễn phù hợp với câu hỏi đang giải quyết.

## <a id="code-unit-code-point-grapheme">Code Unit, Code Point và Grapheme</a>

Ba mức cần phân biệt:

```text
UTF-16 code unit
→ đơn vị lưu trữ của char/String API cơ bản

Unicode code point
→ đơn vị mã Unicode

grapheme cluster
→ cụm ký tự người dùng thường cảm nhận như một ký tự hiển thị
```

Một grapheme có thể gồm nhiều code point, ví dụ base character + combining mark hoặc một số emoji sequence.

Vì vậy ngay cả `codePointCount` cũng chưa chắc bằng “số ký tự người dùng nhìn thấy”.

Ví dụ:

```text
"e" + COMBINING ACUTE ACCENT

code points = 2
user thường nhìn = "é" như một grapheme
```

Emoji sequence còn có thể gồm nhiều code point nối bởi variation selector, skin-tone modifier hoặc zero-width joiner.

Rule chọn abstraction:

```text
protocol/storage index theo Java String
→ code unit có thể đúng

Unicode symbol processing
→ code point

cursor/delete/count theo ký tự người dùng nhìn thấy
→ grapheme boundary
```

### HOW — xử lý grapheme boundary trong Java

String API cơ bản không có “grapheme index” thay thế trực tiếp cho `charAt(i)`. Khi UI/editor logic thực sự cần di chuyển hoặc cắt theo ký tự người dùng nhìn thấy, cần một segmentation API phù hợp.

Java có `BreakIterator`:

```java
BreakIterator iterator =
        BreakIterator.getCharacterInstance(locale);

iterator.setText(text);
```

Nó cung cấp text-boundary iteration theo locale-aware rules. Tuy nhiên grapheme segmentation đầy đủ là một domain Unicode/localization rộng hơn, nên chapter này chỉ thiết lập boundary:

```text
code-unit indexing
→ String API cơ bản

code-point processing
→ codePoints / codePointAt

user-perceived character boundaries
→ segmentation API như BreakIterator
→ localization/Unicode policy sâu hơn nếu domain yêu cầu
```

### String có thể chứa surrogate không ghép cặp

`String` là sequence UTF-16 code unit; nó không tự bảo đảm mọi sequence đều là Unicode text well-formed.

```java
String malformed = "\uD83D"; // isolated high surrogate code unit
```

Vì vậy “đang có String” không đồng nghĩa “đã validate Unicode scalar sequence”. Điều này đặc biệt quan trọng khi encode ra bytes hoặc nhận text từ boundary không tin cậy.

## <a id="unicode-normalization">Unicode Normalization</a>

Cùng một text nhìn giống nhau có thể được biểu diễn bằng code-point sequence khác nhau.

Ví dụ một ký tự có dấu có thể tồn tại dưới dạng:

```text
precomposed code point
hoặc
base character + combining mark
```

`java.text.Normalizer` hỗ trợ các normalization form như NFC/NFD.

Các form chính:

```text
NFD
→ canonical decomposition

NFC
→ canonical decomposition + composition

NFKD / NFKC
→ compatibility normalization
```

NFKC/NFKD có thể thay đổi distinctions mang tính compatibility/presentation nhiều hơn canonical normalization, nên không chọn chúng chỉ vì “normalize mạnh hơn”.

Normalization là một **chính sách ranh giới**: chỉ normalize khi use case cần canonical comparison/search/storage hành vi rõ ràng.

Đừng normalize mù quáng mọi input. Identifier, security-sensitive token, digital-signature input hoặc external protocol có thể yêu cầu sequence exact.

## <a id="canonical-equivalence">Canonical Equivalence và equals</a>

Hai String có thể canonically equivalent về Unicode nhưng vẫn:

```java
a.equals(b) == false
```

nếu code-point sequence khác nhau.

Sau khi normalize cả hai về cùng form, equality có thể trở nên đúng theo use case mong muốn.

Điều này cho thấy `String.equals` so sequence của String, không thực hiện Unicode normalization hoặc linguistic equivalence tự động.

Ví dụ concrete:

```java
String composed = "é";
String decomposed = "e\u0301";

composed.equals(decomposed); // false

String a = Normalizer.normalize(composed, Normalizer.Form.NFC);
String b = Normalizer.normalize(decomposed, Normalizer.Form.NFC);

a.equals(b); // true
```

Canonical equivalence là Unicode concept; linguistic equality/search còn có thể cần locale/collation layer cao hơn.

chương tiếp theo chuyển từ cách biểu diễn sang **mô tả mẫu text** bằng regular biểu thức.
