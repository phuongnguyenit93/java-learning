# Unicode, điểm mã và ký tự người dùng nhìn thấy

Một trong những hiểu nhầm phổ biến nhất về văn bản trong Java là: **một `char` luôn bằng một ký tự người dùng nhìn thấy**. Điều đó không đúng với Unicode hiện đại.

## <a id="utf16-char-model">Java char và UTF-16</a>

`char` là một **đơn vị mã UTF-16 (code unit) 16-bit**.

Nhiều ký tự Unicode nằm trong **Mặt phẳng Đa ngữ Cơ bản (Basic Multilingual Plane - BMP)** có thể biểu diễn bằng một đơn vị mã. Nhưng ký tự ngoài vùng đó cần hai đơn vị mã.

Vì vậy:

```java
text.length();
```

trả số đơn vị mã UTF-16, không đảm bảo là số điểm mã Unicode hay số ký tự người dùng nhìn thấy.

Ví dụ:

```java
String text = "A😀";

text.length(); // 3 đơn vị mã: 'A' + một cặp surrogate
```

Đây là nền tảng để hiểu vì sao các API dựa trên chỉ số như `charAt` và `substring` dùng vị trí theo đơn vị mã UTF-16.

## <a id="code-point">Điểm mã Unicode (code point)</a>

**Điểm mã (code point)** là một giá trị Unicode như `U+0041` hoặc `U+1F600`.

Java thường biểu diễn điểm mã bằng `int`, vì toàn bộ không gian Unicode lớn hơn phạm vi một `char`.

Điểm mã thường được viết dạng:

```text
U+0041  → A
U+00E9  → é
U+1F600 → 😀
```

API như:

```java
text.codePointCount(0, text.length());
text.codePoints();
```

giúp làm việc ở mức điểm mã thay vì đơn vị mã.

Một API hỗ trợ quan trọng khác:

```java
char[] chars = Character.toChars(0x1F600); // cặp surrogate cho 😀
```

`int` ở đây không có nghĩa mọi số nguyên đều nằm trong phạm vi điểm mã Unicode. `Character.isValidCodePoint(cp)` kiểm tra phạm vi `0..0x10FFFF`, nhưng **không đồng nghĩa `cp` là một giá trị vô hướng Unicode (Unicode scalar value)**: các giá trị surrogate `U+D800..U+DFFF` vẫn nằm trong phạm vi điểm mã. Nếu nghiệp vụ cần một giá trị vô hướng hợp lệ, phải loại riêng vùng surrogate ngoài việc kiểm tra phạm vi.

## <a id="surrogate-pairs">Cặp surrogate</a>

Trong UTF-16, điểm mã ngoài BMP được biểu diễn bằng một **high surrogate + low surrogate** (surrogate cao + surrogate thấp).

Ví dụ emoji có thể khiến:

```text
String.length() == 2
codePointCount(...) == 1
```

Nếu duyệt từng `char`, mã có thể tách đôi một điểm mã hợp lệ.

```java
String emoji = "😀";

char high = emoji.charAt(0);
char low  = emoji.charAt(1);

Character.isHighSurrogate(high); // true
Character.isLowSurrogate(low);   // true
```

Hai `char` riêng lẻ không tương đương hai ký tự người dùng.

## <a id="unicode-iteration">Duyệt theo điểm mã Unicode</a>

Khi logic thực sự cần xử lý điểm mã Unicode, dùng các API nhận biết code point:

```java
text.codePoints().forEach(System.out::println);
```

Trong mã thật, phần xử lý có thể thay `System.out::println` bằng logic nghiệp vụ làm việc với từng điểm mã.

hoặc `codePointAt` kết hợp `Character.charCount(cp)` để tăng chỉ số đúng số đơn vị mã.

Ví dụ duyệt bằng chỉ số:

```java
for (int i = 0; i < text.length(); ) {
    int cp = text.codePointAt(i);
    // xử lý cp
    i += Character.charCount(cp);
}
```

Khi cần di chuyển N điểm mã từ một vị trí, dùng `offsetByCodePoints` thay vì tự cộng chỉ số.

Không cần chuyển mọi vòng lặp String sang mức điểm mã. Hãy chọn tầng biểu diễn phù hợp với câu hỏi đang giải quyết.

## <a id="code-unit-code-point-grapheme">Đơn vị mã, điểm mã và cụm ký tự</a>

Ba mức cần phân biệt:

```text
đơn vị mã UTF-16 (code unit)
→ đơn vị được các API char/String cơ bản dùng để đếm và đánh chỉ số

điểm mã Unicode (code point)
→ đơn vị mã Unicode

cụm ký tự (grapheme cluster)
→ cụm ký tự người dùng thường cảm nhận như một ký tự hiển thị
```

Một cụm ký tự có thể gồm nhiều điểm mã, ví dụ ký tự cơ sở + dấu kết hợp hoặc một chuỗi emoji gồm nhiều thành phần.

Vì vậy ngay cả `codePointCount` cũng chưa chắc bằng “số ký tự người dùng nhìn thấy”.

Ví dụ:

```text
"e" + dấu sắc kết hợp (COMBINING ACUTE ACCENT)

số điểm mã = 2
người dùng thường nhìn = "é" như một cụm ký tự
```

Chuỗi emoji còn có thể gồm nhiều điểm mã nối bởi **bộ chọn biến thể (variation selector)**, **bộ điều chỉnh màu da (skin-tone modifier)** hoặc **ký tự nối không độ rộng (zero-width joiner)**. Tên tiếng Anh được giữ trong ngoặc để tiện tra cứu tài liệu Unicode.

Quy tắc chọn tầng biểu diễn:

```text
giao thức/lưu trữ dùng chỉ số theo Java String
→ đơn vị mã có thể là tầng đúng

xử lý ký hiệu Unicode
→ điểm mã

di chuyển con trỏ/xóa/đếm theo ký tự người dùng nhìn thấy
→ ranh giới cụm ký tự
```

### CƠ CHẾ — xử lý ranh giới cụm ký tự trong Java

API String cơ bản không có “chỉ số cụm ký tự” thay thế trực tiếp cho `charAt(i)`. Khi logic giao diện/trình soạn thảo thực sự cần di chuyển hoặc cắt theo ký tự người dùng nhìn thấy, cần một API phân đoạn văn bản phù hợp.

Java có `BreakIterator`:

```java
BreakIterator iterator =
        BreakIterator.getCharacterInstance(locale);

iterator.setText(text);
```

Nó cung cấp cơ chế duyệt ranh giới văn bản theo quy tắc vùng miền. Tuy nhiên phân tách cụm ký tự đầy đủ là một phạm vi Unicode/Bản địa hóa rộng hơn, nên chương này chỉ thiết lập ranh giới:

```text
đánh chỉ số theo đơn vị mã
→ String API cơ bản

xử lý theo điểm mã
→ codePoints / codePointAt

ranh giới ký tự người dùng cảm nhận
→ API phân đoạn như BreakIterator
→ quy tắc Unicode/Bản địa hóa sâu hơn nếu nghiệp vụ yêu cầu
```

### String có thể chứa surrogate không ghép cặp

`String` là một chuỗi đơn vị mã UTF-16; nó không tự bảo đảm mọi chuỗi đều là văn bản Unicode hợp lệ về cấu trúc.

```java
String malformed = "\uD83D"; // một đơn vị mã high surrogate đứng riêng
```

Các API như `codePoints()` và `codePointCount(...)` cũng **không phải bộ xác thực Unicode**. Chúng ghép surrogate pair hợp lệ thành một điểm mã, nhưng surrogate đứng riêng vẫn được xử lý như một giá trị riêng thay vì tự động báo lỗi.

Vì vậy “đang có String” hoặc “đã duyệt bằng codePoints()” không đồng nghĩa “đã kiểm tra tính hợp lệ của **chuỗi giá trị vô hướng Unicode (Unicode scalar value sequence)**”. Điều này đặc biệt quan trọng khi mã hóa ra byte hoặc nhận văn bản từ ranh giới không tin cậy. `String` bảo đảm một chuỗi đơn vị mã UTF-16, chứ không tự xác thực toàn bộ văn bản Unicode đầu vào.

## <a id="unicode-normalization">Chuẩn hóa Unicode</a>

Cùng một văn bản nhìn giống nhau có thể được biểu diễn bằng các chuỗi điểm mã khác nhau.

Ví dụ một ký tự có dấu có thể tồn tại dưới dạng:

```text
điểm mã dựng sẵn (precomposed)
hoặc
ký tự cơ sở + dấu kết hợp
```

`java.text.Normalizer` hỗ trợ các dạng chuẩn hóa như NFC/NFD.

Các dạng chính:

```text
NFD
→ phân rã chuẩn tắc (canonical decomposition)

NFC
→ phân rã chuẩn tắc rồi kết hợp lại

NFKD
→ phân rã tương thích (compatibility decomposition)

NFKC
→ phân rã tương thích rồi kết hợp lại
```

NFKC/NFKD có thể làm mất thêm các khác biệt mang tính tương thích/trình bày so với chuẩn hóa chuẩn tắc, nên không chọn chúng chỉ vì tưởng rằng chúng “chuẩn hóa mạnh hơn”.

Chuẩn hóa là một **chính sách ranh giới**: chỉ chuẩn hóa khi trường hợp sử dụng cần hành vi so sánh/tìm kiếm/lưu trữ theo dạng chuẩn rõ ràng.

Đừng chuẩn hóa mù quáng mọi đầu vào. Mã định danh, token nhạy cảm về bảo mật, dữ liệu chữ ký số hoặc giao thức bên ngoài có thể yêu cầu giữ nguyên chuỗi chính xác.

## <a id="canonical-equivalence">Tương đương chuẩn tắc và equals</a>

Hai String có thể **tương đương chuẩn tắc (canonically equivalent)** về Unicode nhưng vẫn:

```java
System.out.println(a.equals(b)); // false
```

nếu chuỗi điểm mã khác nhau.

Sau khi chuẩn hóa cả hai về cùng dạng, phép so sánh bằng nhau có thể cho kết quả phù hợp với trường hợp sử dụng mong muốn.

Điều này cho thấy `String.equals` so chuỗi nội dung của String, không tự thực hiện chuẩn hóa Unicode hoặc tương đương theo ngôn ngữ.

Ví dụ cụ thể:

```java
String composed = "é";
String decomposed = "e\u0301";

composed.equals(decomposed); // false

String a = Normalizer.normalize(composed, Normalizer.Form.NFC);
String b = Normalizer.normalize(decomposed, Normalizer.Form.NFC);

a.equals(b); // true
```

Tương đương chuẩn tắc là khái niệm Unicode; so sánh/tìm kiếm theo ngôn ngữ còn có thể cần tầng `Locale`/`Collator` cao hơn.

Chương tiếp theo dùng chính mô hình Unicode này để trả lời một câu hỏi ở ranh giới hệ thống: **văn bản trong Java được chuyển thành byte và khôi phục từ byte bằng Charset như thế nào?**
