# Unicode và mã Point

Một trong những hiểu nhầm phổ biến nhất về Java text là: **một `char` luôn bằng một ký tự người dùng nhìn thấy**. Điều đó không đúng với Unicode hiện đại.

## <a id="utf16-char-model">Java char và UTF-16</a>

`char` là một **UTF-16 code unit 16-bit**.

Nhiều Unicode character nằm trong Basic Multilingual Plane có thể biểu diễn bằng một code unit. Nhưng character ngoài vùng đó cần hai code unit.

Vì vậy:

```java
text.length()
```

trả số UTF-16 code unit, không đảm bảo là số Unicode code point hay số ký tự người dùng nhìn thấy.

## <a id="code-point">Unicode Code Point</a>

**mã point** là một giá trị Unicode như `U+0041` hoặc `U+1F600`.

Java thường biểu diễn mã point bằng `int`, vì toàn bộ không gian Unicode lớn hơn phạm vi một `char`.

API như:

```java
text.codePointCount(...)
text.codePoints()
```

giúp làm việc ở mức code point thay vì code unit.

## <a id="surrogate-pairs">Surrogate Pair</a>

Trong UTF-16, mã point ngoài BMP được biểu diễn bằng một **high surrogate + low surrogate**.

Ví dụ emoji có thể khiến:

```text
String.length() == 2
codePointCount(...) == 1
```

Nếu iterate từng `char`, mã có thể tách đôi một mã point hợp lệ.

## <a id="unicode-iteration">Duyệt theo Code Point</a>

Khi logic thực sự cần xử lý Unicode mã point, dùng API mã-point-aware:

```java
text.codePoints().forEach(cp -> ...);
```

hoặc `codePointAt` kết hợp `Character.charCount(cp)` để tăng index đúng số code unit.

Không cần chuyển mọi String loop sang mã point. Chọn level cách biểu diễn phù hợp với câu hỏi đang giải quyết.

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

Một grapheme có thể gồm nhiều mã point, ví dụ base character + combining mark hoặc một số emoji sequence.

Vì vậy ngay cả `codePointCount` cũng chưa chắc bằng “số ký tự người dùng nhìn thấy”.

## <a id="unicode-normalization">Unicode Normalization</a>

Cùng một text nhìn giống nhau có thể được biểu diễn bằng mã-point sequence khác nhau.

Ví dụ một ký tự có dấu có thể tồn tại dưới dạng:

```text
precomposed code point
hoặc
base character + combining mark
```

`java.text.Normalizer` hỗ trợ các normalization form như NFC/NFD.

Normalization là một **chính sách ranh giới**: chỉ normalize khi use case cần canonical comparison/search/storage hành vi rõ ràng.

## <a id="canonical-equivalence">Canonical Equivalence và equals</a>

Hai String có thể canonically equivalent về Unicode nhưng vẫn:

```java
a.equals(b) == false
```

nếu mã-point sequence khác nhau.

Sau khi normalize cả hai về cùng form, equality có thể trở nên đúng theo use case mong muốn.

Điều này cho thấy `String.equals` so sequence của String, không thực hiện Unicode normalization hoặc linguistic equivalence tự động.

chương tiếp theo chuyển từ cách biểu diễn sang **mô tả mẫu text** bằng regular biểu thức.
