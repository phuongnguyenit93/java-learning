<a id="back-to-top"></a>

# Hiệu năng

## Menu
- [Chi phí duyệt, tìm kiếm, so sánh và sao chép theo độ dài](#string-performance-001)
- [Nối lặp bằng += và tổng lượng dữ liệu phải sao chép](#string-performance-002)
- [Ước lượng capacity của StringBuilder](#string-performance-003)
- [Tránh tạo nhiều chuỗi trung gian khi xử lý theo từng bước](#string-performance-004)
- [Biên dịch lại regex so với tái sử dụng Pattern](#string-performance-005)
- [Đánh đổi giữa tính dễ đọc và tối ưu theo dữ liệu đo](#string-performance-006)
- [Đo bằng JMH với làm nóng, chống bỏ mã và tránh gấp hằng](#string-performance-007)
- [Đánh giá bộ nhớ và kết quả trên dữ liệu ASCII, tiếng Việt và emoji](#string-performance-008)

## <a id="string-performance-001">Chi phí duyệt, tìm kiếm, so sánh và sao chép theo độ dài</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí duyệt, tìm kiếm, so sánh và sao chép String theo độ dài trong Java

Hiệu năng của thao tác với `String` thường phụ thuộc vào **độ dài dữ liệu cần đọc** và **lượng dữ liệu phải tạo ra**, chứ không chỉ vào số lần gọi phương thức. Một biểu thức trông ngắn như `text.indexOf(pattern)`, `a.equals(b)` hoặc `text.substring(from, to)` có thể xử lý hàng nghìn hay hàng triệu đơn vị ký tự. Hiểu chi phí giúp chọn thuật toán, tránh sao chép lặp đi lặp lại và đánh giá đúng các điểm nghẽn.

Bài này xét `String` của Java 17+, tập trung vào chi phí **duyệt, tìm kiếm, so sánh và sao chép theo độ dài**. Ký hiệu Big-O mô tả tốc độ tăng chi phí khi kích thước dữ liệu tăng, **không phải số mili giây cố định**. Một số tối ưu phụ thuộc phiên bản JDK, JIT, charset, nội dung chuỗi và cách JVM hiện thực; vì vậy cần phân biệt mô hình thuật toán với cam kết của Java API.

## 1. Xác định đúng kích thước đầu vào

Dùng các ký hiệu sau:

| Ký hiệu | Ý nghĩa |
| --- | --- |
| `n` | `text.length()`: số **UTF-16 code units** của chuỗi nguồn. |
| `m` | `pattern.length()`: số UTF-16 code units của mẫu cần tìm. |
| `k` | Độ dài đoạn được so sánh, duyệt hay sao chép; ví dụ `endIndex - beginIndex`. |
| `r` | Số lần lặp hoặc số phần được tạo thêm. |
| `L` | Tổng độ dài của các phần cần ghép thành kết quả. |

Trong Java, `String.length()` trả số đơn vị mã UTF-16, **không phải** số Unicode code points, số ký tự người dùng nhìn thấy (*grapheme clusters*) hay số byte UTF-8.

```java
String x = "A\uD83D\uDE00"; // A và emoji 😀
int units = x.length();                            // 3
int points = x.codePointCount(0, x.length());     // 2
int bytes = x.getBytes(StandardCharsets.UTF_8).length; // 5
```

Đây là **đoạn minh họa**, cần import `java.nio.charset.StandardCharsets`. Emoji được lưu bằng cặp surrogate, tương ứng **hai** UTF-16 code units và **bốn** byte UTF-8. Khi phân tích độ phức tạp của `charAt`, `indexOf`, `substring` hoặc `compareTo`, các chỉ số Java thường tính theo UTF-16 code units.

Phân biệt:

- **Thời gian:** công việc cần thực hiện khi `n`, `m` hoặc `k` tăng.
- **Bộ nhớ phụ:** vùng nhớ mới do thao tác tạo ra, ngoài các đối tượng đầu vào đã tồn tại.
- **Chi phí tích lũy:** một thao tác `O(n)` nếu lặp `r` lần có thể thành `O(rn)`; nếu độ dài chuỗi tăng dần theo mỗi vòng, tổng còn lớn hơn.

## 2. Bảng tổng quan chi phí cần nhớ

Bảng dưới là **mô hình học thuật và thực tế thường gặp trên Java 17**, không phải bảo đảm độ phức tạp bắt buộc của mọi JVM/phiên bản. Các thao tác có thể dừng sớm hoặc được tối ưu bởi JIT.

| Thao tác | Chi phí thời gian thường gặp | Bộ nhớ thêm thường gặp | Ghi chú |
| --- | --- | --- | --- |
| `s.length()` | `O(1)` | `O(1)` | Độ dài được lưu trong đối tượng/dữ liệu; không duyệt để đếm ở mỗi lần gọi. |
| `s.isEmpty()` | `O(1)` | `O(1)` | Tương đương kiểm tra độ dài bằng 0. |
| `s.charAt(i)` | `O(1)` | `O(1)` | Truy cập một UTF-16 code unit sau kiểm tra chỉ số. |
| Một vòng duyệt từ `0` đến `n - 1` | `O(n)` | `O(1)` nếu chỉ tích lũy biến | Có thể dừng sớm khi đạt điều kiện. |
| `s.codePointCount(a, b)` | `O(k)` tổng quát | `O(1)` | Cần xác định cặp surrogate trong đoạn dài `k`; chuỗi Latin-1 có đường đi tối ưu. |
| `s.indexOf(ch)` / `lastIndexOf(ch)` | `O(n)` trong trường hợp phải quét hết | `O(1)` thường gặp | Trường hợp tốt có thể tìm thấy ngay. |
| `s.indexOf(pattern)` / `s.contains(pattern)` | Phụ thuộc thuật toán tìm chuỗi và mẫu; mô hình so khớp đơn giản có worst case `O(nm)` | Phụ thuộc triển khai | Không gán bừa `O(n)` hay `O(nm)` như cam kết API cho mọi JDK. |
| `s.startsWith(prefix)` | `O(m)` tối đa cho tiền tố dài `m` | `O(1)` thường gặp | Có thể dừng ở ký tự khác đầu tiên. |
| `s.endsWith(suffix)` | `O(m)` tối đa cho hậu tố dài `m` | `O(1)` thường gặp | Không cần quét toàn bộ phần đầu khi chỉ xét hậu tố. |
| `s.equals(t)` | `O(n)` worst case với hai chuỗi cùng độ dài; có thể dừng sớm | `O(1)` thường gặp | Khác độ dài trả về nhanh; cùng nội dung phải đối chiếu. |
| `s.compareTo(t)` | `O(min(n,m))` cho phần tiền tố so sánh | `O(1)` thường gặp | Dừng ở ký tự khác đầu tiên hoặc cuối chuỗi ngắn hơn. |
| `s.regionMatches(..., len)` | `O(len)` worst case | `O(1)` thường gặp | Chỉ so vùng đã chỉ định. |
| `s.substring(a, b)` | `O(k)` khi tạo bản sao đoạn dài `k` trên OpenJDK hiện đại | `O(k)` khi phải sao chép | Trường hợp rỗng/toàn bộ chuỗi có thể được tối ưu; không lấy chi tiết cấp phát làm hợp đồng API. |
| `s.toCharArray()` | `O(n)` | `O(n)` | Tạo mảng `char[]`, có `n` UTF-16 code units. |
| `s.getBytes(charset)` | Phụ thuộc charset; ít nhất cần tạo/ghi số byte đầu ra, thường tuyến tính theo dữ liệu | `O(b)` với `b` byte đầu ra | Không lấy `n` làm số byte vì UTF-8 biến độ dài. |

**Điểm mấu chốt:** một lệnh ngắn trong source không đồng nghĩa với thao tác `O(1)`. `charAt(i)` chỉ lấy một đơn vị ký tự; `equals`, `indexOf` hay `toCharArray` có thể cần xử lý hầu hết chuỗi.

## 3. Duyệt chuỗi: một lần quét và nhiều lần quét

### 3.1. Vòng lặp `charAt`

```java
int digits = 0;
for (int i = 0; i < text.length(); i++) {
    if (text.charAt(i) >= '0' && text.charAt(i) <= '9') {
        digits++;
    }
}
```

Đây là **đoạn minh họa**, giả sử `text` đã được khai báo. `length()` và mỗi lần `charAt(i)` có chi phí nhỏ, thường `O(1)`; vòng lặp có `n` lần nên tổng là **`O(n)` thời gian và `O(1)` bộ nhớ phụ**. Không phải `O(n²)` chỉ vì gọi `length()` trong điều kiện vòng lặp.

Nếu bài toán chỉ cần tìm **ký tự đầu tiên** thỏa điều kiện, dùng `break` hoặc trả kết quả sớm. Worst case vẫn `O(n)` nhưng dữ liệu thực tế có thể kết thúc sớm.

### 3.2. Code unit khác code point

`charAt` truy cập **UTF-16 code unit**, nên khi chuỗi có emoji hay ký tự ngoài BMP, mỗi lần `charAt` không nhất thiết đại diện trọn một Unicode code point. Nếu logic yêu cầu đọc từng code point:

```java
int count = 0;
for (int offset = 0; offset < text.length();) {
    int cp = text.codePointAt(offset);
    count++;
    offset += Character.charCount(cp);
}
```

Đoạn trên duyệt từng code point. Với mỗi bước, `codePointAt` chỉ xem một hoặc hai code units gần vị trí hiện tại; tổng vẫn **`O(n)`** tính theo số UTF-16 code units. Có thể dùng `text.codePoints()` khi pipeline stream làm mã rõ hơn, nhưng stream có thêm chi phí điều phối/đối tượng và không tự nhiên nhanh hơn vòng lặp trên mọi trường hợp.

### 3.3. Gọi hàm quét toàn chuỗi trong vòng lặp

```java
// Tốn thêm nhiều lần tìm kiếm:
for (int i = 0; i < queries.size(); i++) {
    boolean present = bigText.contains(queries.get(i));
    // ...
}
```

Giả sử có `r` truy vấn. Mỗi lần `contains` có thể tìm trên văn bản dài `n` và mẫu dài `m_i`; **không** được tính cả vòng lặp chỉ `O(r)`. Tổng là tổng chi phí của từng phép tìm kiếm, phụ thuộc dữ liệu. Nếu cùng truy vấn được lặp lại nhiều lần, cân nhắc lưu kết quả; nếu cần tra cứu rất nhiều từ/mẫu, cấu trúc chỉ mục hoặc thuật toán đa mẫu có thể hữu ích, nhưng phải đo trước khi đầu tư.

## 4. Tìm kiếm: một ký tự và chuỗi con

### 4.1. `indexOf(int)`, `lastIndexOf(int)`

```java
String s = "ababaX";

int firstA = s.indexOf('a');         // 0
int lastA = s.lastIndexOf('a');      // 4
int absent = s.indexOf('z');        // -1
```

Các phương thức có thể dừng khi thấy kết quả cần tìm; nếu ký tự không tồn tại, thường phải kiểm tra đến hết vùng tìm kiếm. Vì vậy dùng mô hình **`O(n)` worst case**, `O(1)` bộ nhớ phụ. `indexOf(int)` cũng hỗ trợ Unicode code point ngoài BMP (đối chiếu cặp surrogate); kết quả vị trí vẫn là chỉ số UTF-16.

### 4.2. `indexOf(String)`, `contains(String)`

Giả sử nguồn dài `n`, mẫu dài `m`:

```java
String s = "ababaX";
int a = s.indexOf("aba");     // 0
int b = s.indexOf("aba", 1);  // 2
boolean found = s.contains("baba"); // true
```

Không nên khẳng định một Big-O duy nhất cho mọi triển khai và mọi mẫu. Một phương pháp tìm chuỗi con đơn giản thử tối đa `n - m + 1` vị trí và có thể so sánh tới `m` đơn vị tại mỗi vị trí, nên **worst case `O(nm)`**. Các thư viện/JVM có thể tối ưu hóa cho từng dạng chuỗi, mẫu ngắn hoặc nội dung cụ thể. `contains` kiểm tra sự xuất hiện của chuỗi con, không phải tìm bằng regex. Trên Java, `s.contains(x)` dùng cơ chế tìm kiếm chuỗi con (tương đương kiểm tra `indexOf(x) >= 0`).

**Ví dụ trường hợp so khớp lặp lại:** nguồn gồm nhiều chữ `a`, mẫu bắt đầu bởi nhiều `a` rồi mới là `b` có thể buộc thuật toán so khớp ngây thơ so sánh lại nhiều vị trí. Đây là mô hình phân tích thuật toán, không khẳng định một thời gian chạy cố định trên HotSpot 17.

### 4.3. Khi chỉ cần kiểm tra đầu hoặc cuối

Nếu yêu cầu chỉ là kiểm tra một **tiền tố**, dùng `startsWith(prefix)`; nếu chỉ kiểm tra một **hậu tố**, dùng `endsWith(suffix)`. Chúng chỉ cần so sánh vùng tương ứng, tối đa `O(m)` với `m` là độ dài tiền/hậu tố sau kiểm tra độ dài, thay vì tìm trên toàn bộ chuỗi. Ví dụ:

```java
String path = "/api/orders/123";
boolean apiPath = path.startsWith("/api/");
boolean endsWithId = path.endsWith("/123");
```

Không dùng `indexOf(prefix) == 0` nếu `startsWith(prefix)` nói rõ ý định hơn; lợi ích hiệu năng cụ thể còn phụ thuộc môi trường.

### 4.4. Regex khác tìm kiếm chuỗi thuần

`String.matches(regex)`, `split(regex)`, `replaceAll(regex, replacement)` dùng **regex** và có chi phí phân tích mẫu, tìm khớp. Độ phức tạp của regex phụ thuộc biểu thức, nhất là với backtracking và các nhánh lồng nhau; có mẫu gây tăng trưởng rất lớn khi dữ liệu không khớp. Vì vậy không thể suy ra từ `indexOf` rằng mọi tìm kiếm bằng regex đều tuyến tính.

Nếu chỉ cần tìm một chuỗi ký tự **nguyên văn**, ưu tiên `indexOf`/`contains`; nếu cần regex lặp lại, cân nhắc biên dịch `Pattern` một lần và sử dụng `Matcher` mới cho từng đầu vào. Việc đo trên dữ liệu đại diện quan trọng hơn mẹo tối ưu chung chung.

## 5. So sánh: khác ngay đầu thì rẻ, trùng dài thì đắt

### 5.1. `equals`

```java
boolean x = "prefix-a".equals("prefix-b"); // false
boolean y = "same".equals(new String("same")); // true
```

`equals` kiểm tra nội dung có bằng nhau hay không. Nếu hai đối tượng `String` chính là cùng tham chiếu, hoặc độ dài khác nhau, có thể trả về nhanh. Nếu cùng độ dài và nhiều ký tự đầu giống nhau, phải so sánh sâu hơn; hai chuỗi dài thực sự bằng nhau cần đối chiếu đủ nội dung để xác nhận, nên mô hình worst case là **`O(n)`**.

Chú ý `==` kiểm tra **tham chiếu đối tượng**, không phải tính bằng nhau của nội dung. Thay `equals` bằng `==` để “tối ưu” là thay đổi ý nghĩa, dễ gây lỗi.

### 5.2. `compareTo`

`s.compareTo(t)` so sánh thứ tự từ điển theo giá trị UTF-16 của từng đơn vị ký tự. Nó trả về số âm, 0 hoặc số dương; không nên giả định luôn chỉ trả -1, 0, 1.

- Nếu ký tự đầu tiên khác nhau, có thể kết thúc sớm.
- Nếu `s` và `t` có một tiền tố dài giống nhau, phải so sâu hơn.
- Nếu một chuỗi là tiền tố của chuỗi kia, sau khi so hết chuỗi ngắn hơn sẽ xét chênh lệch độ dài.

Với độ dài lần lượt `n` và `m`, số phần tử cần đối chiếu nhiều nhất tỷ lệ với **`min(n,m)`**, nên mô hình là `O(min(n,m))` cho thời gian so sánh và `O(1)` bộ nhớ phụ thường gặp. Chi phí có thể tăng đáng kể khi sắp xếp một danh sách gồm nhiều chuỗi rất dài có tiền tố giống nhau.

### 5.3. `regionMatches`, `equalsIgnoreCase`

`regionMatches` so một đoạn có độ dài `k` thay vì phải tạo `substring` trước:

```java
String s = "prefixVALUEsuffix";
boolean same = s.regionMatches(6, "VALUE", 0, 5); // true
```

Khi chỉ cần kiểm tra đoạn, cách này giúp **tránh tạo một đối tượng/chuỗi con trung gian**. Chi phí so sánh vùng khoảng `O(k)` worst case; dừng sớm khi không khớp.

`equalsIgnoreCase` so sánh không phân biệt hoa/thường theo quy tắc Unicode của API, không phải cách so sánh ngôn ngữ theo locale; vẫn có thể cần đọc nhiều đơn vị ký tự. Nếu cần thứ tự/ngữ nghĩa văn bản theo ngôn ngữ tự nhiên, dùng `Collator` với locale phù hợp.

## 6. Sao chép: thời gian và bộ nhớ tăng theo lượng dữ liệu tạo ra

### 6.1. `substring(begin, end)`

```java
String s = "hello-world";
String piece = s.substring(6, 11); // "world"
```

Với `k = end - begin`, OpenJDK 17 thông thường tạo dữ liệu ký tự của phần kết quả dài `k` nên **chi phí khoảng `O(k)` về thời gian/bộ nhớ** khi cần bản sao. Đây là tính chất **triển khai**, không phải hợp đồng nói mọi lời gọi `substring` đều cấp phát; ví dụ lấy toàn bộ chuỗi hay chuỗi rỗng có thể được tối ưu.

Không mang giả định từ một số JDK rất cũ rằng mọi substring là “view” chia sẻ mảng nền với chuỗi gốc. Với JVM hiện đại, việc giữ một substring nhỏ thường không buộc giữ cả vùng ký tự dài cũ; nhưng chi phí cắt ra nhiều substring vẫn cần tính đến.

Nếu chỉ cần **kiểm tra** một đoạn, `regionMatches` thường tránh được bước tạo substring. Nếu cần thật sự lưu và sử dụng đoạn văn bản lâu dài, `substring` có thể là lựa chọn đúng và dễ đọc; không nên né sao chép bằng cấu trúc phức tạp khi chưa có vấn đề thực tế.

### 6.2. `toCharArray`, `getBytes`, `getChars`

- `toCharArray()` tạo một `char[]` mới có `n` code units: **`O(n)` thời gian, `O(n)` bộ nhớ phụ** cho mảng.
- `getBytes(StandardCharsets.UTF_8)` mã hóa thành `byte[]` mới: thường có chi phí tỷ lệ với số đơn vị đầu vào và byte đầu ra. Với `b` byte đầu ra, bộ nhớ cho dữ liệu kết quả là **`O(b)`**.
- `getChars(srcBegin, srcEnd, dst, dstBegin)` sao chép `k` code units vào **mảng do người gọi cấp**: thời gian `O(k)`; không cần tự tạo `char[]` trả về cho mỗi lần gọi, nhưng phải bảo đảm vùng đích đủ chỗ.

Hai chuỗi có cùng `length()` không nhất thiết cho cùng số byte UTF-8: ASCII dùng một byte mỗi code point, nhiều chữ tiếng Việt/emoji cần nhiều hơn. Vì vậy kích thước payload và chi phí truyền dữ liệu phải xét `getBytes(...).length`, không dựa hoàn toàn vào `String.length()`.

### 6.3. Chuỗi là immutable, kết quả biến đổi có thể tạo dữ liệu mới

`String` bất biến; các thao tác tạo giá trị văn bản đã biến đổi, chẳng hạn `toUpperCase`, `replace` hoặc `repeat`, không sửa nội dung gốc. Nếu kết quả khác đầu vào và được vật hóa, phải có nơi lưu dữ liệu kết quả; chi phí tối thiểu liên quan tới **số ký tự/byte đầu ra**. Nhưng không nên tự gán một Big-O chính xác cho mọi overload `replace` và mọi biểu thức regex: thuật toán dò tìm, thay thế, chuẩn hóa có thể khác nhau.

Ví dụ `"ab".repeat(4)` tạo `"abababab"`, dài tám code units. Nếu số lần lặp là `r` và chuỗi gốc dài `n`, cần tạo sản phẩm kích thước `n × r` (trừ các trường hợp đặc biệt như `r = 0` hoặc `1`).

## 7. Nối chuỗi lặp: chi phí cộng dồn

Độ phức tạp của **một phép nối** khác với tổng chi phí khi phải nối lại cùng một tiền tố nhiều lần. Xét một vòng lặp với danh sách `parts` đã có:

```java
String result = "";
for (String part : parts) {
    result += part;
}
```

Vì `String` bất biến, mỗi lượt có thể tạo kết quả mới chứa toàn bộ tiền tố đã tích lũy. Với `r` phần, mỗi phần dài `c` đơn vị UTF-16, tổng độ dài các kết quả trung gian theo mô hình sao chép tuần tự là `c × (1 + 2 + … + r) = c × r(r + 1)/2`, tức **`Θ(c·r²)`**. Trong khi đó chuỗi cuối chỉ dài `L = c·r`. Đây là mô hình công việc, không phải số byte cấp phát chính xác của mọi JVM.

```java
StringBuilder builder = new StringBuilder();
for (String part : parts) {
    builder.append(part);
}
String result = builder.toString();
```

Với bộ đệm mở rộng theo tỷ lệ, xây dựng bằng `StringBuilder` thường có tổng chi phí append **gần `O(L)` theo lượng dữ liệu được thêm**, cộng chi phí tạo kết quả cuối. Nếu biết kích thước hợp lý, có thể dự trù capacity để hạn chế mở rộng. Phân biệt điều này với một biểu thức đơn như `a + b + c`: compiler/JVM hiện đại có thể tối ưu hiệu quả, nên không cần thay mọi dấu `+` bằng builder.

Phần tính tổng dữ liệu sao chép cho các đoạn không đều, ví dụ chạy được và sự khác nhau giữa số ký tự lý thuyết với allocation thực nằm ở bài chuyên sâu về nối lặp `+=`. Tại đây, điểm cần giữ là **chi phí tích lũy phụ thuộc việc dữ liệu cũ phải được xử lý lại bao nhiêu lần**.

## 8. Ví dụ Java 17+ chạy độc lập

Tạo tệp `StringCostDemo.java` bằng mã sau:

```java
import java.nio.charset.StandardCharsets;
import java.util.List;

public class StringCostDemo {
    public static void main(String[] args) {
        String text = "ababaX";
        String pattern = "aba";

        System.out.println("length: " + text.length());
        System.out.println("charAt(3): " + text.charAt(3));
        System.out.println("first pattern: " + text.indexOf(pattern));
        System.out.println("pattern after 0: " + text.indexOf(pattern, 1));
        System.out.println("last pattern: " + text.lastIndexOf(pattern));
        System.out.println("missing char: " + text.indexOf('z'));
        System.out.println("startsWith: " + text.startsWith("aba"));
        System.out.println("endsWith: " + text.endsWith("X"));
        System.out.println("equals: " + text.equals(new String("ababaX")));
        System.out.println("compareTo: " + text.compareTo("ababaY"));
        System.out.println("regionMatches: "
                + text.regionMatches(2, pattern, 0, 3));

        String piece = text.substring(2, 5);
        char[] copy = text.toCharArray();
        System.out.println("substring: " + piece);
        System.out.println("copied chars: " + copy.length);

        int sum = 0;
        for (int i = 0; i < text.length(); i++) {
            sum += text.charAt(i);
        }
        System.out.println("code-unit sum: " + sum);

        String unicode = "A\uD83D\uDE00"; // A và emoji
        System.out.println("UTF-16 units: " + unicode.length());
        System.out.println("code points: "
                + unicode.codePointCount(0, unicode.length()));
        System.out.println("UTF-8 bytes: "
                + unicode.getBytes(StandardCharsets.UTF_8).length);

        List<String> parts = List.of("[0]", "[1]", "[2]", "[3]");
        StringBuilder builder = new StringBuilder(12);
        for (String part : parts) {
            builder.append(part);
        }
        System.out.println("built: " + builder);
        System.out.println("built length: " + builder.length());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac -encoding UTF-8 StringCostDemo.java
java StringCostDemo
```

Đầu ra:

```text
length: 6
charAt(3): b
first pattern: 0
pattern after 0: 2
last pattern: 2
missing char: -1
startsWith: true
endsWith: true
equals: true
compareTo: -1
regionMatches: true
substring: aba
copied chars: 6
code-unit sum: 575
UTF-16 units: 3
code points: 2
UTF-8 bytes: 5
built: [0][1][2][3]
built length: 12
```

Giải thích theo chi phí:

1. `charAt(3)` chỉ lấy một code unit; vòng cộng mã ký tự phải duyệt sáu code units nên là một phép quét `O(n)`.
2. `indexOf("aba")` và `lastIndexOf("aba")` cho vị trí khác nhau; thời gian tìm phụ thuộc nội dung nguồn/mẫu, không chỉ độ dài kết quả trả về.
3. `regionMatches(2, pattern, 0, 3)` so đúng ba code units mà không yêu cầu tạo substring.
4. `substring(2, 5)` tạo nội dung `"aba"`; `toCharArray()` tạo bản sao sáu code units.
5. `unicode.length()` bằng 3 nhưng có 2 code points và 5 byte UTF-8.
6. `StringBuilder` ghép bốn phần thành 12 ký tự mà không yêu cầu tự dựng lại chuỗi tiền tố sau từng `append`.

Đây là **ví dụ giải thích hành vi**. Đầu ra chứng minh giá trị đúng, **không trực tiếp chứng minh thời gian chạy hay Big-O**; muốn đánh giá hiệu năng thực tế cần phép đo có phương pháp.

## 9. Sắp xếp nhiều chuỗi: chi phí so sánh cộng dồn

Khi sắp xếp `q` chuỗi bằng comparator dựa trên `String.compareTo`, một thuật toán so sánh sắp xếp thường cần khoảng `O(q log q)` lần so sánh. Nhưng **một lần so sánh chuỗi** có thể đọc tới `O(min(n_i, n_j))` code units. Nếu mọi chuỗi có độ dài tối đa `M`, ước lượng cấp cao thường là **`O(q log q × M)`**, không chỉ `O(q log q)`. Đây là giới hạn theo mô hình comparator-based phổ biến, không phải khẳng định mọi trường hợp dữ liệu luôn đạt tới đó.

Ví dụ danh sách gồm các mã có tiền tố chung rất dài sẽ tốn nhiều phép đọc ký tự hơn danh sách có ký tự đầu rất khác nhau, dù số phần tử và số lần gọi comparator tương tự.

Nếu so sánh theo ngữ nghĩa địa phương/tiếng Việt, `String.compareTo` không tương đương sắp xếp theo quy tắc ngôn ngữ; cân nhắc `Collator`. Việc tạo khóa so sánh hoặc bộ nhớ đệm có thể cải thiện một số workload, nhưng phải tính thêm bộ nhớ và chi phí tạo khóa.

## 10. Trường hợp đặc biệt làm thay đổi chi phí thực tế

### Chuỗi rỗng và đối tượng có cùng tham chiếu

Một số phương thức có đường đi nhanh: `isEmpty()`, so sánh độ dài trước khi `equals`, hay kết quả cho `substring` rỗng. Vì vậy một thao tác có worst case `O(n)` không có nghĩa mọi lời gọi đều quét `n` ký tự.

### Compact Strings và cách JVM lưu dữ liệu

OpenJDK/HotSpot hiện đại có cơ chế **Compact Strings** (JEP 254): nếu nội dung có thể biểu diễn bằng Latin-1, bộ nhớ nền có thể dùng một byte mỗi code unit; nếu không, dùng biểu diễn UTF-16. Điều này ảnh hưởng mức tiêu thụ bộ nhớ, băng thông bộ nhớ và các nhánh tối ưu bên trong. Đây là **chi tiết triển khai**, không làm thay đổi ý nghĩa `String.length()` hay hợp đồng Unicode của `String`, và không nên dùng để suy rằng tất cả chuỗi Java luôn tốn đúng một hoặc hai byte trên mỗi ký tự (vì còn header, alignment và các vùng nhớ khác).

### Bộ nhớ tạm và áp lực garbage collector

Một thuật toán tạo ra nhiều substring, mảng `char[]`, byte array hoặc chuỗi trung gian có thể gây nhiều allocation và GC, dù độ phức tạp tiệm cận giống cách khác. Chẳng hạn `s.substring(a, b).equals(target)` có thể vừa tạo đoạn mới vừa so sánh; `s.regionMatches(a, target, 0, target.length())` giúp tránh bước tạo đoạn trung gian khi mục tiêu chỉ là kiểm tra.

JIT đôi khi loại bỏ allocation bằng escape analysis khi chứng minh được đối tượng không thoát, nhưng đó là tối ưu phụ thuộc JVM và workload; không xem như điều chắc chắn trong thiết kế thuật toán.

### Cache, Unicode và tính chất dữ liệu

Một chuỗi có nhiều ASCII có thể chạy khác chuỗi có nhiều surrogate, dù cùng số code units. Dữ liệu có mẫu tìm kiếm xuất hiện ở đầu thường khác dữ liệu không có mẫu; so sánh các chuỗi khác ngay ký tự đầu khác chuỗi có tiền tố chung dài. Những thay đổi này ảnh hưởng chi phí thực tế mà không mâu thuẫn với Big-O worst case.

## 11. Đo hiệu năng cho đúng

Không nên lấy vài dòng `System.nanoTime()` chạy đúng một lần rồi kết luận phương thức A luôn nhanh hơn B. Với JVM, cần tính tới JIT warmup, cấp phát/GC, biến động CPU, nội dung dữ liệu và khả năng JVM tối ưu bỏ kết quả không dùng.

Để đo đáng tin cậy:

1. **Xác định workload:** độ dài `n`, kích thước mẫu `m`, bảng ký tự, tỷ lệ tìm thấy/không thấy và số lần lặp.
2. **Giữ ngữ nghĩa tương đương:** so sánh hai cách làm tạo cùng kết quả; tránh trường hợp một cách có bước chuẩn hóa/tạo dữ liệu khác.
3. **Khởi động JVM và warmup đủ:** hạn chế đo phần class loading/JIT thay vì chính thao tác.
4. **Dùng JMH** (Java Microbenchmark Harness) cho microbenchmark, tiêu thụ kết quả với cách JMH hỗ trợ để tránh dead-code elimination.
5. **Đo cả throughput, latency, allocation/GC khi thích hợp**; lặp trên nhiều kích thước đầu vào để nhìn độ tăng.
6. **Đánh giá tính dễ đọc và mức độ ảnh hưởng thực tế:** nếu tối ưu chỉ tiết kiệm vài nanosecond ở đường chạy hiếm gặp, chưa chắc đáng tăng độ phức tạp mã nguồn.

Ví dụ phép thử có ý nghĩa là so sánh việc ghép 10, 100, 10.000 phần bằng vòng `String +=` và `StringBuilder`, giữ nguyên tổng nội dung; hoặc so `substring(...).equals(...)` với `regionMatches(...)` khi cùng điều kiện đúng/sai. Tránh benchmark bằng các hằng số được compiler/JIT gộp trước khi đo.

## 12. Các lỗi phân tích thường gặp

| Suy luận sai | Phân tích đúng |
| --- | --- |
| “`length()` có `n` ký tự nên chắc chắn `O(n)`.” | Độ dài đã có sẵn; `length()` thường `O(1)`. |
| “`charAt` là `O(1)` nên duyệt `n` lần vẫn là `O(1)`.” | Cộng `n` lần gọi cho ra `O(n)`. |
| “`equals` luôn kiểm tra đủ chuỗi.” | Có thể kiểm tra tham chiếu/độ dài hoặc dừng ở ký tự khác sớm; worst case dài mới tuyến tính. |
| “`compareTo` tốn thời gian theo chuỗi dài hơn.” | Chỉ cần so tối đa tiền tố chung đến hết chuỗi ngắn hơn: `O(min(n,m))`. |
| “`indexOf(String)` luôn `O(n)` như tìm một ký tự.” | Mẫu dài có chi phí so khớp bổ sung; thuật toán triển khai quyết định worst case. |
| “`substring` luôn `O(1)` và không tạo bản sao.” | Trên OpenJDK hiện đại, đoạn kết quả khác biệt thường cần sao chép theo độ dài `k`. |
| “Nối `+=` trong vòng lặp dài là rẻ như một lần append.” | Sao chép tiền tố tích lũy có thể dẫn tới chi phí bậc hai theo số phần. |
| “`String.length()` là số ký tự Unicode hay byte UTF-8.” | Đó là số UTF-16 code units. |
| “Giảm Big-O là đủ để chứng minh nhanh hơn.” | Hằng số, allocation, GC, JIT, dữ liệu và kích thước nhỏ có thể làm kết quả khác; cần benchmark đúng. |
| “Có một giá trị Big-O cố định cho mọi regex.” | Độ phức tạp phụ thuộc biểu thức, công cụ regex và đầu vào. |

## 13. Bài tập tự kiểm tra

**Bài 1.** Có `n` code units, mỗi vòng gọi `s.charAt(i)` đúng một lần và chỉ tăng biến đếm. Thời gian và bộ nhớ phụ?

**Đáp án:** `O(n)` thời gian, `O(1)` bộ nhớ phụ nếu không tạo cấu trúc tăng theo `n`.

**Bài 2.** Hai chuỗi cùng độ dài 100.000, chỉ khác ký tự đầu tiên. `equals` và `compareTo` có nhất thiết so đến ký tự cuối không?

**Đáp án:** Không. Có thể dừng ngay tại vị trí đầu tiên khác nhau (sau các kiểm tra cần thiết). Worst case tuyến tính áp dụng khi chúng giống nhau hoặc khác rất muộn.

**Bài 3.** Tạo 5.000 phần dài 10 ký tự bằng vòng `result += part`. Đầu ra cuối dài bao nhiêu và vì sao mô hình bậc hai đáng quan tâm?

**Đáp án:** Đầu ra dài 50.000 code units. Nếu mỗi lần nối tạo kết quả mới và chép lại toàn bộ tiền tố, tổng lượng dữ liệu được chép tỷ lệ `10(1 + ... + 5000)`, tăng bậc hai theo số phần.

**Bài 4.** Chỉ cần kiểm tra vùng dài 20 ký tự tại offset 100 của một chuỗi lớn có bằng `target` dài 20 hay không. Phương thức nào tránh tạo substring?

**Đáp án:** `s.regionMatches(100, target, 0, 20)`. Nó so vùng hiện hữu trực tiếp, tối đa theo độ dài vùng so sánh.

**Bài 5.** Tìm kiếm ký tự `'x'` trong chuỗi dài `n` không có `'x'`. Đây là trường hợp nào về thời gian?

**Đáp án:** Thường phải quét hết vùng tìm kiếm, nên là trường hợp `O(n)` của `indexOf('x')`.

## 14. Cách chọn thao tác theo nhu cầu

- **Đọc đúng một vị trí:** `charAt(i)`, chi phí gần hằng số; dùng `codePointAt` nếu muốn lấy một Unicode code point.
- **Kiểm tra một tính chất trên toàn chuỗi:** duyệt một lần, dừng ngay khi đủ điều kiện; tránh quét toàn chuỗi lặp không cần thiết.
- **Tìm một ký tự:** `indexOf(int)` hoặc `lastIndexOf(int)`.
- **Tìm chuỗi con:** `indexOf(String)` / `contains`; cân nhắc độ dài mẫu và số lượng truy vấn.
- **Kiểm tra tiền tố/hậu tố/đoạn:** `startsWith`, `endsWith`, `regionMatches` thay vì tạo substring chỉ để so.
- **So nội dung:** `equals` hoặc `compareTo` đúng ngữ nghĩa; xét trường hợp có tiền tố chung dài.
- **Cần bản sao dữ liệu:** `substring`, `toCharArray`, `getBytes` với chi phí theo kích thước kết quả.
- **Ghép nhiều phần:** `StringBuilder`, `String.join` hoặc `Collectors.joining`; tránh `+=` nối tăng dần ở vòng lặp lớn.

Ghi nhớ: **số phần tử phải đọc, số lần đọc lại và lượng dữ liệu phải tạo ra** thường quyết định chi phí lớn nhất. Hãy đặt đúng kích thước `n, m, k, L`, phân biệt worst case với đường chạy sớm, rồi đo trên dữ liệu thật trước khi tối ưu sâu.

### Tài liệu tham khảo

- [Java SE 17 — `String` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html).
- [Java SE 17 — `StringBuilder` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html).
- [JEP 254 — Compact Strings](https://openjdk.org/jeps/254).



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-performance-002">Nối lặp bằng += và tổng lượng dữ liệu phải sao chép</a>

<details>
<summary>Xem chi tiết</summary>

# Nối lặp bằng += và tổng lượng dữ liệu phải sao chép trong Java

Toán tử **+=** thường được dùng để nối thêm nội dung vào một biến String. Ví dụ, sau mỗi lượt đọc dữ liệu, chương trình viết **result += part** để bổ sung một đoạn mới. Cách viết ngắn gọn này có thể gây ra lượng công việc rất lớn khi vòng lặp chạy nhiều lần, vì String trong Java **bất biến (immutable)**: không thể mở rộng trực tiếp nội dung String cũ.

Điểm cần phân tích không chỉ là **số lần nối** mà là **tổng độ dài của tất cả các kết quả trung gian được tạo**. Trong mô hình nối tuần tự phải tạo lại tiền tố cũ, chi phí có thể tăng **bậc hai theo số lượt**; StringBuilder giúp xây dựng kết quả dài dần bằng một vùng đệm có thể thay đổi. Bài này trình bày công thức, ví dụ Java 17, các lựa chọn thay thế và giới hạn của mô hình chi phí khi xét JVM thực tế.

## 1. String bất biến và ý nghĩa của phép +=

Trong Java, câu lệnh:

~~~java
result += part;
~~~

về mặt giá trị tương đương với:

~~~java
result = result + part;
~~~

Khi result là String, phép nối tạo giá trị String kết quả, còn String cũ không bị sửa nội dung. Nếu biến khác vẫn giữ tham chiếu đến chuỗi cũ, nó tiếp tục thấy nội dung cũ. Ví dụ sau minh họa trong một phương thức:

~~~java
String original = "Java";
String result = original;
result += " 17";

System.out.println(original); // Java
System.out.println(result);   // Java 17
~~~

Phép nối một lần hay trong một biểu thức ngắn thường không có vấn đề nghiêm trọng; vấn đề xuất hiện khi **kết quả của lượt trước tiếp tục được đưa vào phép nối ở lượt sau**:

~~~java
String result = "";
for (int i = 0; i < n; i++) {
    result += "x";
}
~~~

Vòng lặp có n lượt; nhưng khi chuỗi dài dần, lượng dữ liệu cần tạo cho mỗi kết quả cũng dài dần. Không thể chỉ nhìn n lượt rồi suy ra toàn bộ công việc là O(n).

## 2. Mô hình sao chép tiền tố khi nối lặp

Giả sử mỗi lượt thêm đúng một ký tự x, bắt đầu từ chuỗi rỗng. Xét **mô hình triển khai đơn giản**: mỗi lượt nối tạo ra một chuỗi mới chứa toàn bộ tiền tố cũ và ký tự mới.

| Lượt | Chuỗi kết quả | Độ dài chuỗi mới | Tổng độ dài đã tạo |
| ---: | --- | ---: | ---: |
| 1 | x | 1 | 1 |
| 2 | xx | 2 | 3 |
| 3 | xxx | 3 | 6 |
| 4 | xxxx | 4 | 10 |
| 5 | xxxxx | 5 | 15 |

Sau n lượt, độ dài chuỗi cuối cùng chỉ là n, nhưng tổng độ dài của các giá trị kết quả sau từng phép nối là:

\[
T(n)=1+2+3+\cdots+n=\frac{n(n+1)}{2}.
\]

Vì vậy **T(n) = Θ(n²)** nếu mỗi lượt phải tái tạo toàn bộ String kết quả. Ví dụ **10.000 lượt** thì String cuối dài 10.000 ký tự, còn tổng độ dài các kết quả trung gian theo mô hình là:

\[
T(10\,000)=\frac{10\,000 \times 10\,001}{2}=50\,005\,000.
\]

Con số **50.005.000 đơn vị ký tự** là kết quả **phân tích toán học**, không phải số byte đã đo được trên máy. Java không quy định số byte vật lý phải copy trong từng phép nối; compiler và JVM có thể tối ưu quá trình này. Công thức cho thấy tại sao việc tạo lại tiền tố dài dần có nguy cơ kém hiệu quả ở quy mô lớn.

### Chỉ tính phần chuỗi cũ

Nếu chỉ tính lượng **tiền tố cũ** cần giữ lại khi nối, lần lượt có 0, 1, 2, ..., n - 1 đơn vị ký tự:

\[
P(n)=0+1+2+\cdots+(n-1)=\frac{n(n-1)}{2}.
\]

Thêm một ký tự mới ở mỗi lượt cho tổng cộng n đơn vị, ta được P(n) + n = T(n). Hai công thức mô tả hai cách đếm khác nhau: **chỉ tiền tố cũ** so với **toàn bộ kết quả mới**.

## 3. Ví dụ Java 17 kiểm chứng phép tính

Tạo tệp **RepeatedConcatDemo.java**:

~~~java
public class RepeatedConcatDemo {
    public static void main(String[] args) {
        int n = 5;
        String joined = "";
        long sumLengths = 0;

        for (int i = 0; i < n; i++) {
            joined += "x";
            sumLengths += joined.length();
        }

        StringBuilder builder = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            builder.append('x');
        }
        String built = builder.toString();

        System.out.println("+= result: " + joined);
        System.out.println("builder result: " + built);
        System.out.println("equal: " + joined.equals(built));
        System.out.println("sum lengths: " + sumLengths);
        System.out.println("formula: " + ((long) n * (n + 1) / 2));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 RepeatedConcatDemo.java
java RepeatedConcatDemo
~~~

Kết quả:

~~~text
+= result: xxxxx
builder result: xxxxx
equal: true
sum lengths: 15
formula: 15
~~~

Biến **sumLengths** cộng độ dài chuỗi sau mỗi lượt. Nó chỉ kiểm tra công thức 1 + 2 + ... + n; **đây không phải phép đo số byte JVM đã cấp phát/copy**. Hai cách xây dựng cho kết quả cùng nội dung, nhưng có chiến lược sử dụng vùng nhớ khác nhau. Ép n thành long trước phép nhân giúp tránh tràn int trong những ví dụ n vừa phải; với n sát giới hạn int nên ép cả n + 1 sang long.

## 4. Nếu mỗi lượt thêm một phần có độ dài khác nhau

Thực tế, một vòng lặp có thể ghép từng dòng, từng trường JSON hay những phần văn bản có kích thước không đều. Gọi **a_i** là độ dài phần thêm ở lượt i, **S_i** là độ dài toàn bộ kết quả sau lượt đó.

\[
S_i=a_1+a_2+\cdots+a_i.
\]

Khi mô hình đòi tái tạo String kết quả trong mỗi lượt, tổng độ dài các kết quả trung gian là:

\[
T=\sum_{i=1}^{n}S_i
 = n a_1 +(n-1)a_2+\cdots+1a_n.
\]

Hay tương đương:

\[
T=\sum_{j=1}^{n}(n-j+1)a_j.
\]

Đây là cách nhìn **theo từng phần dữ liệu**: phần thêm vào sớm xuất hiện trong nhiều kết quả trung gian về sau hơn phần thêm ở cuối. Nếu mỗi phần dài k ký tự, tổng độ dài là:

\[
T=k\frac{n(n+1)}{2}.
\]

Khi k là hằng số, đây vẫn là **Θ(n²) theo số lượt n**. Nếu phần có độ dài thay đổi, phải chỉ rõ biến nào đang được dùng để phân tích độ phức tạp.

Ví dụ ba phần **A**, **BB**, **CCC** có độ dài 1, 2, 3. Các kết quả lũy tiến dài 1, 3, 6, nên T = 1 + 3 + 6 = **10**, dù String cuối chỉ dài **6**.

### Mô hình và số byte thực tế có giống nhau?

Không nhất thiết. T(n) đếm các **đơn vị ký tự** trong kết quả trung gian theo mô hình. Java String có thể dùng biểu diễn **Latin-1 hoặc UTF-16** tùy dữ liệu và JVM, JIT có thể tránh một số thao tác trung gian, và chi phí có cả quản lý đối tượng lẫn garbage collection.

Do đó không nên khẳng định mỗi phép **+=** luôn copy đúng một số byte bằng công thức trên. Kết luận hợp lý là: **nối lặp mà thực sự phải tạo lại String dài dần có nguy cơ tăng chi phí bậc hai**. Chi phí máy thật cần được đo trên JDK, máy và workload cụ thể.

## 5. StringBuilder xử lý chuỗi dài dần như thế nào?

**StringBuilder** là một bộ dựng chuỗi có nội dung thay đổi được. Khi gọi **append(part)**, nó cố gắng thêm dữ liệu vào vùng lưu trữ đang có, thay vì tạo một String bất biến chứa toàn bộ tiền tố ở mỗi lượt:

~~~java
StringBuilder builder = new StringBuilder();
for (String part : parts) {
    builder.append(part);
}
String result = builder.toString();
~~~

Đây là đoạn mã minh họa; biến parts phải được khai báo ở chương trình thực tế.

StringBuilder có hai khái niệm:

- **length():** số đơn vị char trong nội dung hiện có.
- **capacity():** sức chứa hiện tại trước khi cần mở rộng vùng lưu trữ.

Nếu vùng chứa đủ, append có thể thêm vào mà không cần tạo lại toàn bộ tiền tố. Khi thiếu dung lượng, builder có thể phải cấp vùng mới và copy nội dung cũ. Với chiến lược mở rộng theo tỷ lệ, chi phí mở rộng có thể được phân bổ (**amortized**), làm tổng công việc xây chuỗi thường gần tuyến tính theo tổng dữ liệu được thêm vào. Không cần giả định append là O(1) trong mọi trường hợp: thêm một chuỗi có độ dài m vẫn phải xử lý m đơn vị dữ liệu.

Khi ước lượng trước kích thước đầu ra, có thể khởi tạo:

~~~java
StringBuilder builder = new StringBuilder(estimatedLength);
~~~

Tham số ở đây là **capacity**, không phải nội dung/length. Một builder mới với capacity 1000 vẫn có length() == 0. Ước lượng vừa phải giúp hạn chế việc mở rộng; ước lượng quá lớn có thể gây lãng phí bộ nhớ.

Quan trọng: thông thường hãy **gọi builder.toString() một lần sau vòng lặp**. Nếu gọi toString() ở mỗi lượt để lấy snapshot, chi phí tạo những chuỗi tiền tố dài dần vẫn có thể đạt mức bậc hai vì yêu cầu đầu ra của chính bài toán đã lớn.

## 6. Ví dụ StringBuilder hoàn chỉnh với dấu phân cách

Tạo tệp **BuilderJoinDemo.java**:

~~~java
public class BuilderJoinDemo {
    public static void main(String[] args) {
        String[] parts = {"A", "BB", "CCC"};
        StringBuilder builder = new StringBuilder(12);

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(parts[i]);
        }

        String result = builder.toString();
        System.out.println(result);
        System.out.println("length: " + result.length());
        System.out.println("capacity >= length: "
                + (builder.capacity() >= builder.length()));
    }
}
~~~

~~~bash
javac -encoding UTF-8 BuilderJoinDemo.java
java BuilderJoinDemo
~~~

~~~text
A | BB | CCC
length: 12
capacity >= length: true
~~~

Ba phần chứa 1 + 2 + 3 = 6 ký tự; hai dấu phân cách ** | ** chứa 3 ký tự mỗi dấu, nên độ dài tổng cộng là 12. Builder được cấp capacity 12 phù hợp với ví dụ. Dòng cuối kiểm tra quan hệ capacity >= length, không phụ thuộc công thức tăng capacity nội bộ của một phiên bản JDK.

Khi chỉ cần nối các chuỗi với một dấu phân cách, **String.join(" | ", parts)** thường ngắn và rõ nghĩa hơn vòng lặp thủ công. Với stream có thể dùng **Collectors.joining(" | ")**; chọn phương thức thể hiện rõ ý định thay vì viết nhiều thao tác append nếu không cần thiết.

## 7. Khi nào Java tối ưu toán tử +?

Trong Java 9+, trình biên dịch Java có thể triển khai các biểu thức nối chuỗi qua **invokedynamic** và **StringConcatFactory**; JVM có thể tối ưu thêm ở runtime. Một biểu thức nối một số phần cố định, ví dụ:

~~~java
String label = "ID=" + id + "; status=" + status;
~~~

không nên bị mặc định xem là chuỗi các phép cấp phát vô ích. Java hiện đại có thể xây kết quả rất hiệu quả. Với phần nhỏ và biểu thức đơn giản, toán tử **+** thường là lựa chọn dễ đọc, hợp lý.

Nhưng vòng lặp:

~~~java
String result = "";
for (String part : parts) {
    result += part;
}
~~~

đưa **giá trị từ lượt trước** vào lượt sau. Nếu ở mỗi lượt chuỗi tăng dần phải được tái tạo, tổng lượng kết quả trung gian tuân theo công thức ở phần đầu. JVM có thể tối ưu một số trường hợp cụ thể, nhưng **ngôn ngữ không bảo đảm việc nối lặp này luôn được chuyển thành một bộ đệm tái sử dụng**. Vì vậy, với lượng dữ liệu lớn hoặc số vòng lặp phụ thuộc đầu vào, StringBuilder là giải pháp rõ ràng và ổn định hơn.

**Đừng thay mọi phép + bằng StringBuilder thủ công:** tiêu chí đúng là có bao nhiêu lượt, độ dài kết quả tăng ra sao, biểu thức có thật sự nằm trên đường thực thi nóng hay không, và người đọc mã có hiểu rõ ý định không.

## 8. Đo thời gian và bộ nhớ đúng cách

Không nên dựng hai đoạn code nhỏ, đo mỗi đoạn một lần bằng **System.nanoTime()**, rồi coi đó là kết luận tuyệt đối. Thời gian chạy phụ thuộc JIT warm-up, garbage collector, trạng thái CPU, số liệu đầu vào và việc compiler có thể loại bỏ công việc nếu kết quả không được dùng.

Khi phải xác minh hiệu năng thực tế:

1. Dùng **cùng dữ liệu đầu vào** và kiểm tra **đầu ra giống nhau**.
2. Warm-up mã được đo, chạy nhiều lần.
3. Không tính thời gian **println**, đọc tệp hay tạo dữ liệu vào phép đo nối chuỗi.
4. Thử nhiều giá trị **n** và kích thước mỗi part, thay vì một con số đơn lẻ.
5. Dùng **JMH** khi cần benchmark tin cậy; quan sát throughput, allocations và GC.
6. Phân biệt **thời gian thực**, **số byte cấp phát** và **tổng độ dài tiền tố theo công thức**.

Ví dụ đoạn văn bản chứa Unicode: String.length() đếm **đơn vị UTF-16**, không nhất thiết bằng số ký tự người dùng nhìn thấy. Điều đó càng cho thấy không thể lấy phép đếm ký tự làm số byte copy vật lý mà không kiểm tra thêm.

## 9. Tình huống áp dụng

| Nhu cầu | Lựa chọn thường phù hợp | Vì sao |
| --- | --- | --- |
| Nối vài phần cố định trong một biểu thức | Dùng + | Cú pháp gọn, JVM tối ưu biểu thức hiệu quả. |
| Xây một String dài qua nhiều vòng | Dùng StringBuilder | Tái sử dụng vùng đệm, hạn chế tạo lại tiền tố dài dần. |
| Ghép danh sách có delimiter | String.join hoặc StringBuilder | Thể hiện rõ quy tắc dấu phân cách. |
| Ghép stream thành một chuỗi | Collectors.joining | Diễn đạt phép gom các phần tử. |
| Các luồng độc lập đều xây chuỗi | Mỗi tác vụ một StringBuilder | StringBuilder không đồng bộ, tránh chia sẻ mutable state. |
| Thật sự cần bộ đệm đồng bộ hóa | Cân nhắc StringBuffer hoặc giải pháp đồng bộ phù hợp | StringBuffer đồng bộ hóa phương thức nhưng không thay cho thiết kế đồng bộ toàn bộ quy trình. |
| Cần String kết quả ở mỗi lượt | Chấp nhận chi phí snapshot | Tổng độ dài **mọi đầu ra bắt buộc** vốn có thể rất lớn. |

## 10. Những lỗi thường gặp

| Nhầm lẫn | Giải thích và cách sửa |
| --- | --- |
| N phép nối luôn là O(n). | Mỗi lượt có thể phải xử lý tiền tố dài dần; hãy tính **tổng độ dài kết quả**. |
| Mọi phép + với String đều là O(n²). | Độ phức tạp phụ thuộc dạng code; biểu thức cố định có thể được tối ưu. |
| Công thức T(n) là số byte cấp phát chính xác. | Đây là mô hình tổng độ dài kết quả, không phải phép đo vật lý. |
| StringBuilder không bao giờ copy dữ liệu cũ. | Khi mở rộng capacity, nó có thể phải copy vùng chứa. |
| new StringBuilder(1000) tạo 1000 ký tự. | Số 1000 là capacity ban đầu, length vẫn bằng 0. |
| Gọi builder.toString() ở mọi vòng vẫn rẻ. | Mỗi snapshot là kết quả String; tổng kích thước các snapshot có thể bậc hai. |
| Dùng StringBuffer luôn nhanh hơn vì có đồng bộ. | Đồng bộ có chi phí; bộ đệm cục bộ thường dùng StringBuilder. |
| Đo một lần với nanoTime là đủ. | JIT/GC/CPU gây nhiễu; dùng phép đo có warm-up, nhiều mẫu hoặc JMH. |
| Nối String từ nhiều luồng bằng một StringBuilder chung là an toàn. | StringBuilder không bảo đảm thread safety; tách theo tác vụ hoặc đồng bộ đúng cách. |

## 11. Bài tự kiểm tra

**Câu 1.** Với 8 lần thêm một ký tự, theo mô hình tái tạo toàn bộ String, tổng độ dài các kết quả trung gian là bao nhiêu?

Đáp án: **8 × 9 / 2 = 36**. Chuỗi cuối chỉ dài 8; tổng độ dài **tiền tố cũ** là 7 × 8 / 2 = **28**.

**Câu 2.** Nối ba phần có độ dài 2, 1, 3 theo thứ tự. Tổng độ dài các kết quả sau mỗi lượt là bao nhiêu?

Đáp án: ba độ dài lũy tiến là 2, 3, 6; tổng **11**. Theo công thức trọng số: 3 × 2 + 2 × 1 + 1 × 3 = 11.

**Câu 3.** Nếu dùng StringBuilder để tạo kết quả cuối một lần nhưng trong từng lượt vẫn phải xuất ra bản sao toàn bộ tiền tố, có còn tránh được mọi chi phí bậc hai không?

Đáp án: **không**. Yêu cầu tạo ra n snapshot với độ dài tăng dần đã có tổng kích thước đầu ra theo mô hình là Θ(n²).

**Câu 4.** Với một biểu thức ghép họ, tên và lời chào, nên ưu tiên toán tử + hay StringBuilder?

Đáp án: **toán tử +** thường đủ rõ ràng và hiệu quả trong biểu thức ngắn; không cần tối ưu thủ công khi chưa có bằng chứng về nút thắt.

## 12. Kết luận

Khi thấy **result += part bên trong vòng lặp**, hãy hỏi không chỉ “lặp bao nhiêu lần?” mà còn “**String kết quả tăng dài như thế nào?**”. Với n phần một ký tự và mô hình tái tạo chuỗi mới ở mọi lượt, tổng độ dài các kết quả trung gian bằng **n(n + 1) / 2**, tức tăng bậc hai theo n, trong khi kết quả cuối chỉ dài n. Với phần dài không đều, tổng chi phí mô hình là tổng độ dài các tiền tố.

**StringBuilder** cho phép ghi thêm vào vùng đệm thay đổi được, mở rộng khi cần và tạo String kết quả khi hoàn tất; đây là lựa chọn phổ biến để nối dài trong vòng lặp. **Dấu +** vẫn phù hợp với biểu thức nối ngắn. Mô hình chi phí giúp lựa chọn thiết kế hợp lý, còn hiệu năng thật của chương trình cần được đánh giá bằng công cụ đo trên dữ liệu thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-performance-003">Ước lượng capacity của StringBuilder</a>

<details>
<summary>Xem chi tiết</summary>

# Ước lượng `capacity` của `StringBuilder` trong Java

## Mục tiêu

Khi ghép nhiều chuỗi trong vòng lặp, việc tạo `StringBuilder` với dung lượng ban đầu phù hợp có thể giảm số lần mở rộng vùng chứa trung gian. Tuy nhiên, chọn quá lớn sẽ giữ tài nguyên không cần thiết. Bài học này giải thích `capacity` là gì, cách ước lượng từ dữ liệu đầu vào, khi nào dùng `new StringBuilder(capacity)` hoặc `ensureCapacity()` và cách kiểm chứng giả định bằng Java 17+.

## 1. Phân biệt `length` và `capacity`

`StringBuilder` là đối tượng lưu một chuỗi ký tự có thể thay đổi (mutable). Hai thuộc tính dễ nhầm là:

- `length()`: số **đơn vị mã UTF-16 đang có nội dung** trong builder.
- `capacity()`: số **đơn vị mã UTF-16 có thể chứa trước khi cần mở rộng**. Dung lượng có thể lớn hơn `length()`.

Luôn có `0 <= length() <= capacity()`. Khi `append()` thêm nội dung, `length()` tăng theo lượng văn bản thực sự thêm; nếu cần nhiều chỗ hơn, builder tự tăng `capacity()`. Khi gọi `toString()`, chuỗi được trả về có nội dung theo `length()`, không kèm phần dung lượng chưa sử dụng.

Ví dụ nếu một builder đang có `length() == 5` và `capacity() == 20`, bạn có thể thêm tối đa 15 đơn vị UTF-16 trước khi cần mở rộng vùng chứa. **Capacity không phải số byte**, không nhất thiết là số Unicode code point, và cũng không phải độ dài tối đa vĩnh viễn của `StringBuilder`.

### Giá trị khởi tạo thường gặp

| Lời gọi | `length()` ban đầu | `capacity()` ban đầu |
| --- | ---: | ---: |
| `new StringBuilder()` | 0 | 16 |
| `new StringBuilder(100)` | 0 | 100 |
| `new StringBuilder("")` | 0 | 16 |
| `new StringBuilder("abc")` | 3 | 19 |
| `new StringBuilder(existingSeq)` | `existingSeq.length()` | `existingSeq.length() + 16` |

Bộ dựng từ `String` hoặc `CharSequence` vừa **chép nội dung** vừa dành thêm dung lượng. Ngược lại, bộ dựng với số nguyên chỉ tạo vùng chứa với capacity được yêu cầu, chưa thêm văn bản. `new StringBuilder(-1)` ném `NegativeArraySizeException`. Khi dữ liệu đầu vào là một chuỗi đã biết, `new StringBuilder(existingString)` hợp lý hơn việc tự ghép chuỗi đó thêm lần nữa.

## 2. Tại sao cần ước lượng dung lượng?

Giả sử bạn tạo một chuỗi kết quả dài hàng chục nghìn đơn vị UTF-16 từ hàng nghìn đoạn nhỏ. Nếu bắt đầu với capacity rất thấp, builder có thể phải cấp phát vùng chứa lớn hơn và sao chép nội dung đã có khi vượt giới hạn. Việc cấp phát trước gần với kích thước cuối cùng thường giúp giảm các lần mở rộng, đặc biệt khi xử lý khối lượng lớn lặp lại thường xuyên.

Dẫu vậy, `StringBuilder` **tự mở rộng khi cần**; việc không đoán trước dung lượng không làm chuỗi sai. Mục tiêu là giảm cấp phát và sao chép nếu đây thực sự là phần việc tốn thời gian.

Hai kiểu sử dụng phổ biến:

```java
// Đơn giản, thường đủ tốt với chuỗi nhỏ hoặc chưa biết độ dài.
StringBuilder message = new StringBuilder();
message.append("Hello, ").append("Java");

// Khi đã biết gần đúng kích thước kết quả.
int expectedLength = 1024;
StringBuilder buffer = new StringBuilder(expectedLength);
```

Đây là các đoạn minh họa trong ngữ cảnh của một phương thức. Capacity **không** giới hạn nội dung ở 1024: append thêm vẫn có thể khiến bộ đệm lớn lên.

### Cách tăng capacity khi bị đầy

Với triển khai OpenJDK được dùng trong Java 17, tăng dung lượng tự động thường chọn ít nhất:

```text
newCapacity ≈ max(minimumCapacity, oldCapacity * 2 + 2)
```

Trong đó `minimumCapacity` là số đơn vị UTF-16 cần chứa sau thao tác. Công thức có các nhánh xử lý tràn số và giới hạn mảng; vì vậy đây là **mô tả quy tắc tăng thông thường của triển khai**, không phải cam kết rằng mọi JDK hoặc mọi trường hợp đều trả về đúng công thức. Theo API, điều quan trọng là builder đảm bảo có chỗ cho nội dung nếu có thể cấp phát.

Ví dụ: từ capacity 4, `append` làm length vượt 4 sẽ mở rộng. Không nên dựa vào capacity cụ thể sau khi tăng để tính đúng/sai của chương trình.

## 3. Công thức ước lượng từ các phần sẽ nối

Nếu các đoạn văn bản và ký tự phân cách đã biết, ta có thể tính **chính xác số đơn vị UTF-16 của kết quả** trước khi tạo builder:

```text
estimatedCapacity = tổng length() của các phần dữ liệu
                  + tổng độ dài của các dấu phân cách
                  + độ dài prefix/suffix/nhãn cố định
                  + độ dài các ký tự được thêm có điều kiện
```

Ví dụ nối `n` chuỗi với một dấu phẩy giữa hai phần, **không có dấu phẩy cuối**:

```text
estimatedCapacity = sum(part.length()) + max(0, n - 1)
```

Với dấu phân cách là một `String separator` bất kỳ:

```text
estimatedCapacity = sum(part.length())
                  + max(0, n - 1) * separator.length()
```

Trong công thức trên, `length()` đo **đơn vị UTF-16**; đây chính là đơn vị cần dùng cho capacity. Nếu nối thêm `\n` một lần sau mỗi dòng, cần tính thêm một đơn vị cho mỗi `\n`. Với dấu xuống dòng `\r\n`, cần thêm hai đơn vị mỗi lần. Với `System.lineSeparator()`, lấy `separator.length()` thay vì giả định có đúng một ký tự.

### Ví dụ đầy đủ: nối dữ liệu với dấu phân cách

Lưu thành `CapacityEstimateDemo.java`:

```java
import java.util.List;

public class CapacityEstimateDemo {
    public static void main(String[] args) {
        List<String> parts = List.of("Java", "17", "StringBuilder");
        String separator = ",";

        int estimate = 0;
        for (String part : parts) {
            estimate = Math.addExact(estimate, part.length());
        }
        int separatorCount = Math.max(0, parts.size() - 1);
        int separatorsLength = Math.multiplyExact(
                separatorCount, separator.length());
        estimate = Math.addExact(estimate, separatorsLength);

        StringBuilder result = new StringBuilder(estimate);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                result.append(separator);
            }
            result.append(parts.get(i));
        }

        System.out.println("estimated=" + estimate);
        System.out.println("length=" + result.length());
        System.out.println("capacity=" + result.capacity());
        System.out.println("text=" + result);
    }
}
```

Chạy trên Java 17+:

```bash
javac CapacityEstimateDemo.java
java CapacityEstimateDemo
```

Kết quả:

```text
estimated=21
length=21
capacity=21
text=Java,17,StringBuilder
```

`"Java"` dài 4, `"17"` dài 2 và `"StringBuilder"` dài 13; thêm 2 dấu phẩy = 21. Vì builder được tạo đúng capacity cần thiết, chương trình không cần mở rộng khi nối dữ liệu này. Khi `parts` rỗng, công thức cho 0 và builder có capacity 0; khi chỉ có một phần, không có separator.

Hai phương thức `Math.addExact` và `Math.multiplyExact` giúp phát hiện tràn `int` khi tổng hợp kích thước, thay vì âm thầm tạo một số âm hoặc quá nhỏ. Chúng ném `ArithmeticException` khi tràn. Tuy nhiên, một số capacity cực lớn dù còn nằm trong phạm vi `int` vẫn có thể không cấp phát được vì giới hạn của máy ảo hoặc bộ nhớ.

**Điểm đánh đổi:** tính chính xác toàn bộ độ dài đầu vào tốn thêm một lượt duyệt. Nếu dữ liệu đã được duyệt để xác thực hoặc thống kê thì gần như có sẵn; nếu tất cả đoạn cực ngắn, chi phí đo trước có thể lớn hơn lợi ích tránh vài lần mở rộng. Hãy đo trong tình huống thực tế.

## 4. Khi không biết chính xác kích thước kết quả

Một số đoạn văn bản được tạo ra từ số hoặc phép chuyển đổi định dạng. Cần phân biệt **giá trị** với **số ký tự sau chuyển đổi**:

- `builder.append(12345)` thêm 5 đơn vị UTF-16, không phải 12.345 đơn vị.
- `builder.append(-12)` thêm 3 đơn vị: dấu `-` và hai chữ số.
- `builder.append(true)` thêm 4 đơn vị, `false` thêm 5.
- `builder.append((String) null)` thêm chuỗi `"null"` dài 4.
- `builder.append('\n')` thêm 1 đơn vị; `builder.append("\r\n")` thêm 2.
- `builder.append("😀")` thêm 2 đơn vị UTF-16 dù người dùng nhìn thấy một emoji.

Nếu đối số đến từ `String.valueOf(x)`, `toString()`, `String.format()`, JSON serializer hoặc formatter theo locale, dung lượng phụ thuộc **đầu ra thực tế** (ví dụ dấu nhóm hàng nghìn, số chữ số, escape, khoảng trắng, trường tùy chọn). Không dùng số byte UTF-8 hay số trường dữ liệu làm capacity trực tiếp.

Khi chưa biết chính xác, có thể dùng ước lượng theo số bản ghi:

```java
int rowCount = 500;
int estimatedUnitsPerRow = 48;
int estimate = Math.multiplyExact(rowCount, estimatedUnitsPerRow);
StringBuilder report = new StringBuilder(estimate);
```

Đây là đoạn minh họa, giả định ước lượng trung bình 48 đơn vị UTF-16 cho mỗi dòng **đã bao gồm dấu phân cách hoặc newline**. Với 500 dòng, estimate bằng 24.000. Nếu thực tế dài hơn, builder vẫn tự mở rộng; nếu thực tế ngắn hơn, phần dư không xuất hiện trong chuỗi kết quả nhưng vùng chứa có thể chiếm bộ nhớ.

### Chọn giữa ước lượng chính xác và ước lượng gần đúng

| Dữ liệu | Cách tính phù hợp | Lý do |
| --- | --- | --- |
| Danh sách các `String` có sẵn | Cộng `length()`, prefix, suffix và separator | Có thể tính chính xác |
| Ghép `char` hoặc newline cố định | Cộng số lần thêm nhân độ dài từng thành phần | Thường tính chính xác |
| Dòng CSV cần escape dấu ngoặc kép | Tính sau escaping, hoặc ước lượng dựa trên dữ liệu | Dữ liệu nguồn có thể dài ra |
| Số, ngày, phần trăm định dạng theo locale | Tính độ dài chuỗi đã định dạng nếu cần chính xác | Kích thước phụ thuộc cách biểu diễn |
| Số lượng bản ghi biết nhưng nội dung biến động | Dung lượng trung bình nhân số bản ghi | Ước lượng đủ dùng |
| Luồng không biết lúc nào kết thúc | Bắt đầu vừa phải, mở rộng khi cần, có thể `ensureCapacity` | Không có tổng độ dài đáng tin |

Một công thức quá phức tạp, trùng lặp logic với chính bước nối chuỗi, có thể dễ sai và gây thêm overhead. Ước lượng nên phục vụ tối ưu hóa, không trở thành nguồn lỗi nghiệp vụ.

## 5. `ensureCapacity`, `setLength` và `trimToSize` khác nhau thế nào?

`StringBuilder` có ba API dễ bị hiểu nhầm:

| API | Tác động lên `length()` | Tác động lên `capacity()` |
| --- | --- | --- |
| `ensureCapacity(minimum)` | Không đổi | Bảo đảm tối thiểu số đơn vị UTF-16 được yêu cầu nếu cần tăng; có thể tăng nhiều hơn |
| `setLength(newLength)` | Đổi đúng thành `newLength` | Có thể phải tăng khi `newLength` vượt capacity; phần được thêm có giá trị `\u0000` |
| `trimToSize()` | Không đổi | Cố giảm phần dung lượng chưa dùng; API không bảo đảm luôn đạt đúng `length()` |

`ensureCapacity` phù hợp khi **đã tạo builder và sau đó mới biết nhu cầu lớn hơn**:

```java
StringBuilder builder = new StringBuilder();
builder.append("header:");
builder.ensureCapacity(1000);
builder.append("data");

System.out.println(builder.length());           // 11
System.out.println(builder.capacity() >= 1000); // true
```

Đây là đoạn có thể chạy trong `jshell` hoặc trong phương thức. Lệnh `ensureCapacity(1000)` không thêm 1000 ký tự; nó chỉ yêu cầu chỗ trống. Nếu số được truyền nhỏ hơn hoặc bằng 0, `ensureCapacity` không cần thay đổi dung lượng. Nó không phải lời hứa "capacity đúng bằng 1000": sau tăng, capacity có thể lớn hơn.

Đừng dùng `setLength(1000)` để "đặt trước capacity" vì nó làm cho chuỗi thực sự có độ dài 1000 bằng cách thêm các đơn vị `\u0000`. Các đơn vị NUL đó là **nội dung thật**, làm sai dữ liệu nếu gửi đi hoặc ghi tệp. Để dành chỗ, dùng constructor hoặc `ensureCapacity`.

`trimToSize()` có thể giúp giải phóng phần dư lớn sau một giai đoạn nối chuỗi nếu phải giữ builder lâu, nhưng cũng có thể khiến lần nối tiếp theo lại phải mở rộng. Đừng gọi nó sau mỗi `append()`.

## 6. Quan sát mở rộng dung lượng trong Java

Lưu thành `BuilderGrowthDemo.java`:

```java
public class BuilderGrowthDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder(4);
        System.out.println("initial=" + builder.length() + "/" + builder.capacity());

        builder.append("abcd");
        System.out.println("filled=" + builder.length() + "/" + builder.capacity());

        builder.append('e');
        System.out.println("expanded=" + builder.length() + "/" + builder.capacity());

        builder.append("0123456789");
        System.out.println("expanded-again=" + builder.length()
                + "/" + builder.capacity());

        StringBuilder initial = new StringBuilder();
        StringBuilder fromText = new StringBuilder("abc");
        System.out.println("default=" + initial.capacity());
        System.out.println("from-text=" + fromText.capacity());
    }
}
```

Chạy:

```bash
javac BuilderGrowthDemo.java
java BuilderGrowthDemo
```

Trên OpenJDK thông dụng (đã kiểm chứng với triển khai OpenJDK), kết quả:

```text
initial=0/4
filled=4/4
expanded=5/10
expanded-again=15/22
default=16
from-text=19
```

Ở lần đầu, builder có 4 chỗ. Sau `"abcd"` nó đầy nhưng chưa tăng. Thêm `'e'` làm length lên 5 nên capacity mở rộng thành 10. Thêm 10 ký tự tiếp theo làm length thành 15, capacity lúc đó thành 22. **Các giá trị mở rộng sau append mang tính quan sát triển khai**, không phải điều kiện mà code ứng dụng cần phụ thuộc vào. Giá trị capacity từ bộ dựng được tài liệu Java định nghĩa rõ hơn và có thể dùng trực tiếp để kiểm chứng ước lượng.

## 7. Unicode: vì sao không dùng byte hoặc số code point làm capacity?

Với `StringBuilder`, `length()` và `capacity()` đo theo **đơn vị mã UTF-16**, không phải byte UTF-8. Ví dụ:

```java
import java.nio.charset.StandardCharsets;

String value = "A\uD83D\uDE00";
StringBuilder builder = new StringBuilder(value.length());
builder.append(value);

System.out.println(builder.length());
System.out.println(builder.capacity());
System.out.println(value.codePointCount(0, value.length()));
System.out.println(value.getBytes(StandardCharsets.UTF_8).length);
```

Có thể chạy trong `jshell`; kết quả lần lượt là:

```text
3
3
2
5
```

`"A😀"` gồm 3 đơn vị UTF-16, 2 code point và 5 byte UTF-8. Vì vậy capacity cần để append đúng một bản `value` là 3. Nếu dùng 2 (code point count), builder vẫn chạy nhưng phải mở rộng. Nếu dùng 5 (byte UTF-8), builder sẽ cấp dư theo đơn vị capacity.

Một dấu kết hợp hoặc cụm emoji phức tạp còn có thể gồm nhiều code point nhưng người đọc cảm nhận như một ký tự. Không dùng số grapheme cluster để đo capacity. Nếu có thao tác thêm mã Unicode bằng `appendCodePoint(int)`, số đơn vị tăng là `Character.charCount(codePoint)`: 1 cho ký tự BMP hợp lệ, 2 cho ký tự bổ sung.

Đừng suy luận bộ nhớ heap thực tế bằng công thức cứng `capacity * 2`. Triển khai Java hiện đại có thể dùng Compact Strings và cơ chế lưu trữ trong JVM; có chi phí đối tượng, mảng, căn chỉnh và mã hóa nội bộ. `capacity()` là chỉ số hữu ích để dự báo **chỗ chứa logic**, không phải đồng hồ đo byte RAM.

## 8. Tối ưu hóa đúng chỗ

Chỉ định dung lượng là tối ưu hóa đáng cân nhắc khi:

1. Nối rất nhiều phần trong vòng lặp và kích thước cuối tương đối đoán được.
2. Mỗi yêu cầu hoặc mỗi lần xử lý đều tạo ra builder lớn; chi phí cấp phát và copy xuất hiện trong profiler.
3. Kết quả luôn theo mẫu ổn định: có nhãn, phân cách hoặc số bản ghi đã biết.
4. Một builder lớn được dùng trong đường chạy nóng và việc chọn capacity đã được benchmark chứng minh có lợi.

Không nhất thiết chỉ định capacity trong mọi lệnh nối vài từ. `StringBuilder()` mặc định đã có một lượng chỗ nhỏ; compiler cũng có thể tối ưu nhiều biểu thức nối chuỗi bằng `+` theo cách riêng. Đừng thay mọi phép nối chuỗi bằng một builder chỉ vì tin rằng constructor có số capacity luôn nhanh hơn.

Chú ý những trường hợp gây phản tác dụng:

- **Ước lượng quá thấp:** vẫn tự tăng nhiều lần, có thể cấp phát/sao chép trung gian.
- **Ước lượng quá cao:** lãng phí vùng chứa, tăng áp lực bộ nhớ hoặc dẫn đến `OutOfMemoryError`.
- **Tính toán sai/tràn số:** số âm có thể gây `NegativeArraySizeException`; cộng/nhân bằng `int` không kiểm soát có thể tràn. Dùng `Math.addExact`/`multiplyExact` khi tính từ nhiều phần và đặt giới hạn nghiệp vụ phù hợp.
- **Lấy `String.length()` của nguồn trước escaping:** dữ liệu sau escaping có thể dài hơn.
- **Dùng capacity làm số byte network:** độ dài payload UTF-8 phải tính từ `byte[]` thực sự gửi.
- **Gọi `toString()` liên tục trong vòng lặp:** tạo `String` trung gian không cần thiết; thường nên append xong rồi chuyển sang `String` một lần.
- **Chia sẻ builder giữa các luồng:** `StringBuilder` không đồng bộ hóa và không bảo đảm an toàn khi nhiều thread sửa đồng thời; dùng sở hữu riêng theo tác vụ hoặc giải pháp đồng bộ phù hợp.

### Kiểm tra hiệu năng như thế nào?

Đo `builder.length()` và `builder.capacity()` sau khi xây dựng giúp kiểm chứng ước lượng có thiếu/thừa nhiều hay không. Nhưng capacity **không tự chứng minh chương trình chạy nhanh hơn**. Khi cần so sánh chiến lược, benchmark bằng JMH với dữ liệu đại diện, có warmup, nhiều lần chạy và tránh để JVM tối ưu bỏ kết quả. Theo dõi tổng thời gian, allocation rate và GC bằng profiler khi thích hợp; nếu phải duyệt dữ liệu một lần nữa để tính capacity, hãy tính cả chi phí duyệt ấy.

Một cách thực dụng là bắt đầu bằng `new StringBuilder()`, chỉ định capacity khi bài toán đã có dự báo hợp lý; điều chỉnh sau khi có đo đạc thay vì dùng công thức nhân cố định cho mọi chuỗi.

## 9. Bài tập tự kiểm tra

**Bài 1.** Cần ghép ba chuỗi `"abc"`, `"Java"`, `"17"`, giữa chúng có chuỗi phân cách `" | "` (3 đơn vị UTF-16). Capacity tối thiểu để không tăng trong quá trình nối là bao nhiêu?

**Bài 2.** Cần tạo 100 dòng, mỗi dòng có đúng 30 đơn vị UTF-16 và kết thúc bằng `"\r\n"`. Capacity tối thiểu là bao nhiêu? Nếu đổi newline thành `"\n"` thì kết quả ra sao?

**Bài 3.** Vì sao `new StringBuilder(100)` khác hoàn toàn với `new StringBuilder().setLength(100)`? Cả hai có `length()` ban đầu bằng 0 không?

**Bài 4.** Với `String text = "A\uD83D\uDE00";`, kết quả của `text.length()` và `text.getBytes(StandardCharsets.UTF_8).length` bằng bao nhiêu? Nên truyền số nào vào constructor khi chỉ append `text` một lần?

**Bài 5.** Ứng dụng luôn nối 10 từ ngắn. Việc tính toán chính xác length trước khi nối có chắc tăng hiệu năng không? Bạn cần đo gì để kết luận?

**Gợi ý đáp án:** (1) 3 + 4 + 2 + 2×3 = **15**; (2) 100×(30+2) = **3200**, dùng LF là **3100**; (3) constructor số nguyên đặt capacity 100, length 0, còn `setLength(100)` thêm đủ nội dung NUL để length 100; (4) length 3, UTF-8 byte 5, capacity **3**; (5) không chắc: phải đo chi phí tính capacity, số lần tăng/cấp phát, thời gian và GC trong workload thực tế.

### Tài liệu tham khảo

- [`StringBuilder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html)
- [`AbstractStringBuilder` — OpenJDK source](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/AbstractStringBuilder.java) (quy tắc mở rộng ở mức triển khai, có thể thay đổi theo phiên bản).
- [`Character.charCount` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html#charCount(int)).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-performance-004">Tránh tạo nhiều chuỗi trung gian khi xử lý theo từng bước</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh tạo nhiều chuỗi trung gian khi xử lý theo từng bước trong Java

## Mục tiêu

Khi chuẩn hóa dữ liệu, tạo thông báo, dựng CSV, ghép nhiều trường hoặc biến đổi văn bản trong vòng lặp, một chương trình Java có thể tạo ra nhiều đối tượng `String` chỉ để sử dụng trong thời gian rất ngắn. Bài học này giúp bạn xác định khi nào các chuỗi trung gian làm tăng cấp phát bộ nhớ, chọn kỹ thuật giảm số lần sao chép/nối chuỗi, và giữ nguyên ngữ nghĩa của phép biến đổi. Mục tiêu là giảm công việc không cần thiết ở đoạn mã thực sự cần tối ưu, chứ không loại bỏ mọi `String` tạm bằng mọi giá.

## 1. Vì sao phát sinh chuỗi trung gian?

`String` trong Java là **bất biến** (immutable): sau khi tạo, nội dung của một đối tượng `String` không bị sửa trực tiếp. Các phương thức như `replace(...)`, `toUpperCase(...)`, `substring(...)` hoặc thao tác nối chuỗi có thể trả về một chuỗi mới để biểu diễn kết quả. Tùy trường hợp, một số phương thức được phép trả về chính đối tượng cũ nếu nội dung không đổi; do đó không nên tuyên bố “mỗi lời gọi đều chắc chắn tạo một `String` mới”.

Ví dụ:

```java
String source = "  Java-Core  ";
String a = source.trim();
String b = a.replace('-', ' ');
String c = b.toUpperCase(java.util.Locale.ROOT);
System.out.println(c);
```

Kết quả:

```text
JAVA CORE
```

`a` và `b` là các giá trị trung gian phục vụ bước tiếp theo. Cách viết trên dễ đọc, đúng chức năng và hoàn toàn có thể là lựa chọn tốt đối với dữ liệu nhỏ hoặc xử lý ít lần. Nhưng nếu đoạn xử lý này chạy hàng triệu lần hoặc trên văn bản rất dài, các bản sao và đối tượng tạm có thể gây thêm **allocation**, lưu lượng bộ nhớ và áp lực cho garbage collector (GC). Chúng cũng có thể phải duyệt qua nội dung nhiều lần.

Phân biệt ba chi phí:
- **Cấp phát:** tạo các đối tượng `String` hoặc bộ đệm phụ, đặc biệt khi lặp lại rất nhiều lần.
- **Sao chép/duyệt:** chuyển nội dung từ đầu vào sang kết quả mới hoặc kiểm tra lại từng ký tự qua nhiều công đoạn.
- **Giữ bộ nhớ:** lưu tham chiếu đến nhiều kết quả tạm hoặc dùng bộ đệm có dung lượng quá lớn trong thời gian dài.

JVM có tối ưu hóa khi chạy, chẳng hạn tối ưu nối chuỗi hoặc loại bỏ một số cấp phát trong trường hợp phù hợp. Tuy vậy, không thể dựa vào suy đoán rằng JIT luôn loại bỏ mọi chuỗi trung gian. Khi hiệu năng quan trọng, cần đo trên đúng bản JDK và workload.

## 2. Nhận diện chuỗi trung gian trong vòng lặp

Với vòng lặp `result += part`, mỗi kết quả bất biến có thể chứa lại toàn bộ tiền tố trước đó; nếu chuỗi tăng đều, tổng dữ liệu phải tạo theo mô hình nối tuần tự có thể tăng bậc hai theo số lượt. `StringBuilder` cho phép tích lũy bằng một vùng đệm rồi gọi `toString()` khi hoàn tất. Bài về nối lặp `+=` đã phân tích công thức chi phí này; ở đây cần nhận diện **những giá trị tạm chỉ được tạo ra để lập tức chép vào một kết quả khác**.

Các câu hỏi hữu ích khi xem xét một pipeline:

- Có đang tạo chuỗi định dạng bằng `prefix + value + suffix` chỉ để đưa ngay vào `builder.append(...)` không? Nếu có, có thể append từng thành phần trực tiếp.
- Có tạo `text.substring(begin, end)` chỉ để append đoạn đó vào bộ đệm không? Overload `append(text, begin, end)` có thể tránh giá trị trung gian này.
- Có thật sự cần giữ một `String` sau mỗi bước không? Nếu các kết quả chỉ phục vụ bước tiếp theo, cân nhắc gộp các thao tác xây dựng đầu ra; nếu là biến đổi Unicode, chuẩn hóa hoặc escaping phức tạp, vẫn phải giữ ngữ nghĩa chuẩn.

Không thể kết luận nhanh hơn chỉ từ việc số biến tạm giảm. Sau khi bảo đảm kết quả tương đương, hãy đo allocation và thời gian trên dữ liệu đại diện. Những ví dụ ở phần tiếp theo tập trung trực tiếp vào cách bỏ chuỗi tạm khi định dạng hoặc sao chép một đoạn.

## 3. Gộp nhiều bước xây dựng đầu ra vào cùng một `StringBuilder`

Giả sử chương trình cần nối các phần dưới dạng `[value]`. Cách sau tạo chuỗi trình bày cho từng phần rồi mới nối kết quả:

```java
StringBuilder out = new StringBuilder();
for (String value : values) {
    String formatted = "[" + value + "]";
    out.append(formatted);
}
```

Có thể append trực tiếp từng phần vào bộ đệm:

```java
StringBuilder out = new StringBuilder();
for (String value : values) {
    out.append('[').append(value).append(']');
}
String result = out.toString();
```

Lúc này, phần định dạng `formatted` không còn cần thiết. `append(char)` thêm ký tự, còn `append(String)` thêm nội dung chuỗi mà không cần tạo chuỗi kết hợp mới ở tầng mã nguồn.

Ví dụ đầy đủ — lưu thành `DirectAppendDemo.java`:

```java
public class DirectAppendDemo {
    public static void main(String[] args) {
        String[] values = {"A", "B", "C"};

        StringBuilder out = new StringBuilder();
        for (String value : values) {
            out.append('[').append(value).append(']');
        }

        System.out.println(out.toString());
    }
}
```

```bash
javac -encoding UTF-8 DirectAppendDemo.java
java DirectAppendDemo
```

```text
[A][B][C]
```

Cách tiếp cận tương tự phù hợp khi dựng phần thân thông báo, command line, định dạng log có điều kiện, template đơn giản hoặc xuất dữ liệu dạng văn bản. Một builder nên có chủ sở hữu rõ ràng và chỉ tạo kết quả bất biến khi một bước thực sự cần nó.

## 4. Tránh tạo `substring` nếu chỉ cần chép một đoạn vào kết quả

Giả sử có văn bản chứa phần cần lấy trong một khoảng chỉ số. Cách sau tạo chuỗi tạm để chèn vào builder:

```java
String fragment = text.substring(begin, end);
builder.append(fragment);
```

Nếu chỉ cần chép các ký tự từ `text`, dùng overload:

```java
builder.append(text, begin, end);
```

`StringBuilder.append(CharSequence, int start, int end)` chép nội dung ở khoảng **[start, end)** trực tiếp vào builder, tránh yêu cầu tạo đối tượng `String` từ `substring`. Chỉ số được tính bằng **đơn vị mã UTF-16**, giống `String.length()` và `String.substring(...)`.

Ví dụ chạy độc lập — lưu thành `AppendRangeDemo.java`:

```java
public class AppendRangeDemo {
    public static void main(String[] args) {
        String source = "prefix:payload:suffix";
        int begin = "prefix:".length();
        int end = source.indexOf(":suffix");

        StringBuilder builder = new StringBuilder();
        builder.append("value=");
        builder.append(source, begin, end);

        System.out.println(builder.toString());
    }
}
```

```bash
javac -encoding UTF-8 AppendRangeDemo.java
java AppendRangeDemo
```

```text
value=payload
```

Kiểm tra `begin` và `end` trước khi append khi chỉ số lấy từ dữ liệu không tin cậy (ví dụ `indexOf` có thể trả về `-1`). Nếu thao tác cắt có thể nằm giữa cặp surrogate của một ký tự bổ sung Unicode, cần chọn ranh giới theo code point để tránh tạo chuỗi chứa surrogate lẻ. Khi API yêu cầu một `String` độc lập ở chính bước đó, không thể loại bỏ mọi chuyển đổi thành `String`.

## 5. Khi nào nên xử lý trực tiếp một lượt duyệt?

Một pipeline quen thuộc:

```java
String output = input.trim()
        .toLowerCase(java.util.Locale.ROOT)
        .replace('-', '_')
        .replace(' ', '_');
```

Mỗi bước có thể tạo kết quả mới và quét nội dung lại. Nếu nghiệp vụ chỉ yêu cầu một bộ quy tắc nhỏ, có thể duyệt đầu vào một lần và ghi thẳng kết quả vào `StringBuilder`. Tuy nhiên, **không được đổi thuật toán rồi vô tình đổi kết quả**: `trim()`, `strip()`, xử lý hoa/thường Unicode và thao tác `char` không tương đương nhau trong mọi trường hợp.

Ví dụ sau có phạm vi được định nghĩa rõ: chỉ đổi **chữ ASCII A–Z thành a–z** và thay khoảng trắng ASCII cùng dấu gạch nối bằng `_`. Đây **không phải** cách viết lại tổng quát của `toLowerCase(Locale.ROOT)` hay `strip()` đối với mọi văn bản Unicode.

Lưu thành `SinglePassAsciiDemo.java`:

```java
public class SinglePassAsciiDemo {
    static String normalizeAscii(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '-' || ch == ' ') {
                out.append('_');
            } else if (ch >= 'A' && ch <= 'Z') {
                out.append((char) (ch + ('a' - 'A')));
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    public static void main(String[] args) {
        System.out.println(normalizeAscii("A-B C"));
        System.out.println(normalizeAscii("Java 17"));
    }
}
```

```bash
javac -encoding UTF-8 SinglePassAsciiDemo.java
java SinglePassAsciiDemo
```

```text
a_b_c
java_17
```

**Ưu điểm:** một vòng duyệt và một builder, không tạo chuỗi riêng ở từng bước biến đổi. **Giới hạn:** hàm này giữ nguyên các ký tự Unicode khác ngoài ASCII, không loại bỏ khoảng trắng đầu/cuối, không xử lý quy tắc viết thường đặc biệt của ngôn ngữ và không xử lý token/locale. Nếu cần quy tắc Unicode đầy đủ, hãy dùng các API chuẩn hoặc thiết kế thuật toán theo code point/locale và kiểm thử thật kỹ. Tối ưu bộ nhớ không cho phép làm sai kết quả nghiệp vụ.

Một cách tối ưu thường an toàn hơn viết lại toàn bộ pipeline là **gộp riêng những bước tạo đầu ra**: vừa duyệt/tokenize đầu vào, vừa nối delimiter và phần cần thiết vào một builder. Giữ các phép biến đổi Unicode khó ở API chuẩn nếu chi phí của chúng chưa được đo là nút thắt.

## 6. Chọn đúng công cụ cho nhu cầu nối chuỗi

| Nhu cầu | Công cụ hợp lý | Lưu ý |
| --- | --- | --- |
| Nối vài giá trị trong một biểu thức | Toán tử `+` | Ngắn gọn, JVM hiện đại có tối ưu nối chuỗi |
| Tích lũy dữ liệu trong vòng lặp hoặc nhiều nhánh | `StringBuilder` | Tạo builder cho một đơn vị công việc; `toString()` một lần khi xong |
| Nối các phần có dấu phân cách | `String.join(...)` hoặc `StringJoiner` | Tốt khi cần các chuỗi có sẵn; cân bằng tính dễ đọc và cấp phát |
| Nối phần tử từ stream | `Collectors.joining(...)` | Dễ đọc; mỗi phép `map(...)` tạo `String` vẫn có thể sinh trung gian |
| Định dạng nhiều giá trị theo mẫu phức tạp | `String.format(...)` / `formatted(...)` | Mạnh về định dạng; có chi phí parse/format, nên đo nếu ở hot path |
| Ghi lượng văn bản lớn ra file/network | `Writer`, `BufferedWriter` hoặc API streaming thích hợp | Ghi dần để tránh giữ toàn bộ chuỗi kết quả trong RAM |
| Bộ đệm có thao tác trên nhiều luồng | Phân định sở hữu và đồng bộ hóa | `StringBuilder` không thread-safe; `StringBuffer` đồng bộ từng phương thức nhưng không tự bảo đảm tính nguyên tử của cả quy trình nhiều bước |

Ví dụ nối delimiter bằng `StringJoiner`:

```java
java.util.StringJoiner joiner = new java.util.StringJoiner(", ", "[", "]");
joiner.add("red").add("green").add("blue");
System.out.println(joiner.toString());
```

```text
[red, green, blue]
```

`StringJoiner` phù hợp khi phần tử đã là chuỗi. Nhưng `String.join` hay `Collectors.joining` không tự loại bỏ các chuỗi tạm sinh bởi những bước trước đó như `stream.map(x -> expensiveTransform(x))`. Với dữ liệu rất lớn, có thể ghi trực tiếp từng bản ghi ra `Writer` thay vì dồn tất cả vào `StringBuilder`.

## 7. Quản lý dung lượng của `StringBuilder`

`StringBuilder` có `capacity()` là dung lượng bộ đệm hiện tại và `length()` là số đơn vị mã UTF-16 đang chứa. Khi append vượt capacity, bộ đệm phải mở rộng; việc mở rộng có thể đòi hỏi cấp phát và sao chép. Nếu có ước lượng đáng tin cậy về kích thước đầu ra, có thể khởi tạo dung lượng:

```java
int estimatedChars = 128;
StringBuilder builder = new StringBuilder(estimatedChars);
builder.append("prefix:").append("data");
```

Chọn capacity dựa trên dữ liệu thực tế, chẳng hạn tổng chiều dài các phần và delimiter. Nếu chỉ biết số phần tử mà không biết độ dài từng phần, đừng nhân một hằng số quá lớn dẫn đến cấp phát dư thừa. `capacity()` có đơn vị UTF-16, không phải số Unicode code point hoặc số byte UTF-8. `StringBuilder.length()` cũng không bằng số ký tự người dùng cảm nhận trong mọi ngôn ngữ.

Những lưu ý thực tế:

- `new StringBuilder()` đã có khả năng tăng dung lượng; không phải mọi trường hợp đều cần dự đoán capacity.
- `ensureCapacity(...)` có thể hữu ích khi nhu cầu tăng đáng kể đã được biết trước.
- `setLength(0)` xóa nội dung logic để tái sử dụng bộ đệm **nhưng có thể giữ nguyên vùng nhớ lớn đã cấp phát**. Tái sử dụng builder từng phình rất lớn trong cache dài hạn có thể giữ bộ nhớ quá lâu.
- `toString()` trả về một `String` bất biến. Không gọi lặp đi lặp lại `toString()` trong vòng lặp chỉ để kiểm tra độ dài hoặc nội dung đơn giản; dùng `builder.length()`, `charAt(...)` hoặc trạng thái riêng khi phù hợp.
- Đừng giữ một `StringBuilder` tĩnh dùng chung giữa các request/luồng. Ngoài nguy cơ race condition, nó có thể giữ dung lượng không mong muốn; ưu tiên builder cục bộ.

## 8. Tránh tạo chuỗi trung gian ở các ranh giới xử lý

### 8.1. Chỉ tạo thông báo khi thật sự cần

Ví dụ logger hỗ trợ tham số hóa thường cho phép:

```java
logger.debug("Processed id={}, size={}", id, size);
```

Khi mức DEBUG bị tắt, framework có thể tránh định dạng văn bản cuối cùng. Cú pháp cụ thể tùy thư viện log đang dùng. Với các thao tác nặng trước khi gọi logger (ví dụ `expensiveToString()`), tham số hóa không mặc nhiên ngăn phương thức đó được đánh giá trước; hãy kiểm tra API lazy/supplier hoặc điều kiện enabled theo framework.

### 8.2. Đọc/ghi theo luồng nếu đầu ra rất lớn

Tạo một `StringBuilder` khổng lồ cho hàng triệu bản ghi có thể giảm số lượng `String` trung gian nhưng vẫn giữ toàn bộ kết quả trong RAM. Nếu đích cuối là tệp hoặc socket, dùng `BufferedWriter` (với charset rõ ràng) để ghi từng phần:

```java
try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(
        path, java.nio.charset.StandardCharsets.UTF_8)) {
    for (String value : values) {
        writer.write(value);
        writer.newLine();
    }
}
```

Đoạn trên **là mẫu đặt trong một phương thức** đã có biến `Path path` và nguồn `Iterable<String> values`, không phải một chương trình độc lập. Cần xử lý lỗi I/O, quyền truy cập và đóng tài nguyên theo yêu cầu ứng dụng. Việc ghi streaming tránh tạo một `String` kết quả chứa cả tệp; API writer vẫn có thể dùng bộ đệm nội bộ.

### 8.3. Tận dụng dữ liệu sẵn có

Khi chỉ cần kiểm tra tiền tố, hậu tố, ký tự ở một vị trí hoặc tìm chuỗi con, ưu tiên `startsWith`, `endsWith`, `charAt`, `indexOf` thay vì cắt ra nhiều `substring` để so sánh. Ví dụ:

```java
if (value.startsWith("ID:")) {
    // Xử lý tiếp theo tùy nghiệp vụ.
}
```

Với phần có thể lấy theo khoảng chỉ số, `append(value, begin, end)` giúp không tạo `substring` trung gian. Tuy vậy, không nên giữ tham chiếu đến nguồn rất lớn chỉ để tránh tạo một chuỗi nhỏ nếu nhu cầu lưu trữ lâu dài đòi một bản sao độc lập.

## 9. Khi nhiều phương thức biến đổi nối tiếp vẫn là lựa chọn đúng

Các phép xử lý như chuẩn hóa Unicode, case folding theo ngôn ngữ, escaping JSON/XML, regex phức tạp, phân tích ngữ pháp hoặc an toàn injection có các quy tắc tinh vi. Viết một bộ duyệt thủ công chỉ vì muốn dùng `StringBuilder` có thể làm mất tính đúng đắn. Ví dụ:

- `String.toLowerCase(Locale.ROOT)` có thể xử lý ánh xạ Unicode không tương đương với cộng một khoảng số trên `char`.
- `String.strip()` dùng tiêu chí Unicode whitespace khác `trim()`; hai phương thức không thể hoán đổi tùy tiện.
- Biểu thức chính quy có semantics đặc thù; `replaceAll(...)`, `replace(...)` và so khớp với `Pattern` biên dịch sẵn không tương đương nhau trong mọi trường hợp.
- Escaping dữ liệu cấu trúc nên giao cho serializer/library phù hợp để tránh lỗi bảo mật, dù thao tác này có tạo chuỗi hoặc bộ đệm phụ.
- Dãy Unicode có thể có surrogate pair và combining marks; từng `char` không nhất thiết là một code point, càng không tương đương một grapheme mà người dùng nhìn thấy.

Hãy giữ pipeline dễ hiểu khi nó đã đủ nhanh hoặc khi phép biến đổi khó. Nếu profile cho thấy chi phí ở một bước, tối ưu đúng bước đó trước. Các lời gọi `replace(...)` không tìm thấy gì có thể trả về chuỗi gốc; số cấp phát thực tế phải được xác nhận bằng công cụ đo.

## 10. Đo hiệu năng và cấp phát đúng cách

Một đoạn thử đơn giản bằng `System.nanoTime()` dễ bị sai lệch bởi JIT warmup, GC, cache, kích thước dữ liệu, dead-code elimination và cách chuẩn bị đầu vào. Để so sánh các cách xử lý chuỗi:

1. **Xác nhận kết quả bằng nhau trước:** kiểm thử trường hợp rỗng, `null` nếu API cho phép, delimiter, đầu/cuối chuỗi, dấu tiếng Việt, ký tự bổ sung Unicode và dữ liệu lớn. Nếu thay đổi ngữ nghĩa, số liệu tốc độ không còn so sánh cùng chức năng.
2. **Xác định workload:** số phần tử, chiều dài trung bình, phân bố chuỗi ASCII/Unicode, tần suất chạy, giới hạn heap và tỷ lệ dữ liệu cần biến đổi thực sự.
3. **Profile ứng dụng:** dùng Java Flight Recorder (JFR), profiler hoặc công cụ đo cấp phát để tìm nơi sinh đối tượng; quan sát throughput, latency, allocation rate và GC pauses.
4. **Benchmark đoạn nóng:** dùng JMH với warmup, nhiều lần đo và tiêu thụ kết quả để tránh tối ưu bỏ mã; so trên cùng phiên bản Java, phần cứng và dữ liệu. Theo dõi cả đơn vị `ops/s` hoặc `ns/op` và số byte cấp phát trên mỗi thao tác khi có thể.
5. **Đưa thay đổi nhỏ vào ứng dụng thực:** đo lại end-to-end. Cải thiện microbenchmark không chắc cải thiện request latency nếu thời gian chủ yếu nằm ở I/O, lock hoặc database.

Tối ưu có ý nghĩa khi **giữ đúng chức năng, giảm được chi phí đo thấy và không làm mã khó bảo trì vượt lợi ích**. Nên ưu tiên thay thế vòng `result += item` chạy nhiều lần bằng một builder hoặc ghi trực tiếp ra writer trước khi tối ưu các biểu thức nhỏ.

## 11. Những lỗi thường gặp

| Lỗi | Hệ quả | Cách xử lý |
| --- | --- | --- |
| `result += part` bên trong vòng lặp lớn | Tạo chuỗi kết quả tích lũy mới nhiều lần | Dùng `StringBuilder` hoặc `Writer` |
| `builder.append(prefix + value + suffix)` ở mỗi vòng | Có thể tạo chuỗi `prefix + value + suffix` trước khi append | Gọi `append(prefix).append(value).append(suffix)` |
| `builder.toString().isEmpty()` mỗi vòng | Yêu cầu tạo/kiểm tra chuỗi kết quả nhiều lần | Dùng `builder.length() == 0` |
| `builder.append(text.substring(a, b))` chỉ để chép đoạn | Có thể tạo bản `String` tạm | Dùng `builder.append(text, a, b)` |
| Tái sử dụng builder toàn cục hoặc builder khổng lồ | Race condition, dữ liệu lẫn nhau, giữ bộ nhớ | Dùng builder cục bộ theo công việc |
| Tự viết lại xử lý Unicode để “nhanh hơn” | Sai ánh xạ chữ, sai whitespace, tách surrogate | Giữ API Unicode chuẩn; tối ưu khi có bằng chứng |
| Dự đoán capacity quá lớn | Cấp phát RAM không cần thiết | Dùng ước lượng thực tế hoặc để builder tự tăng |
| Dùng `StringBuilder` cho mọi phép `+` | Mã dài hơn nhưng lợi ích chưa chắc có | Chỉ sửa các đoạn code nóng đã xác định |

Một mẹo nhỏ: nếu muốn thêm delimiter giữa các phần, hãy kiểm tra builder trước khi append:

```java
if (builder.length() > 0) {
    builder.append(", ");
}
builder.append(value);
```

Cách này tránh tạo chuỗi trung gian và tránh delimiter thừa ở đầu; tuy nhiên cần chính sách rõ ràng khi `value` là chuỗi rỗng, `null` hoặc phần tử cần lọc.

## 12. Bài tập thực hành

**Bài 1 — nối kết quả:** từ mảng 10.000 chuỗi, tạo văn bản phân cách bằng dấu phẩy với hai cách: vòng lặp `+=` và `StringBuilder`. So sánh kết quả bằng `equals`, sau đó benchmark đúng cách; phân biệt “hai kết quả bằng nhau” với “đã chứng minh cách nào nhanh hơn”.

**Bài 2 — bỏ chuỗi định dạng tạm:** viết lại mẫu `builder.append("(" + id + ":" + value + ")")` thành các lời gọi `append(...)` nối tiếp. Kiểm thử trường hợp `id` âm, `value` rỗng và ký tự Unicode.

**Bài 3 — tránh `substring`:** với chuỗi `"before[content]after"`, tìm vị trí giữa dấu `[` và `]` rồi thêm trực tiếp vào builder bằng `append(source, start, end)`. Kiểm thử trường hợp thiếu dấu đóng.

**Bài 4 — pipeline:** định nghĩa tập quy tắc ASCII của riêng bạn, viết một hàm duyệt một lần tạo đầu ra tương đương chuỗi các phép `replace`. Viết test so sánh cho nhiều đầu vào trước khi đo cấp phát.

**Bài 5 — dữ liệu lớn:** thay mô hình xây dựng một chuỗi CSV cực lớn bằng ghi từng hàng qua `BufferedWriter`. Đo mức sử dụng heap và thời gian ghi cho cả hai trên bộ dữ liệu có kiểm soát.

### Kết luận thực hành

Đối với các bước xây dựng văn bản lặp lại, hãy nghĩ đến việc **append trực tiếp từng phần vào một `StringBuilder`**, dùng khoảng chỉ số để tránh `substring` chỉ nhằm sao chép, và chuyển sang ghi streaming khi đầu ra quá lớn. Những phép biến đổi Unicode phức tạp cần ưu tiên tính đúng đắn. Chọn nơi tối ưu dựa trên profiler, kiểm thử kết quả và đo cấp phát, không chỉ dựa vào số dấu `+` xuất hiện trong mã.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-performance-005">Biên dịch lại regex so với tái sử dụng Pattern</a>

<details>
<summary>Xem chi tiết</summary>

# Biên dịch lại regex so với tái sử dụng Pattern trong Java

Trong Java, regular expression (regex) thường được dùng để kiểm tra định dạng, tìm kiếm hoặc tách dữ liệu trong chuỗi. Khi **cùng một regex được áp dụng cho nhiều chuỗi**, gọi `Pattern.compile(regex)` lặp lại có thể tạo chi phí không cần thiết. Cách thông thường để giảm chi phí là **biên dịch regex một lần thành `Pattern` rồi tạo `Matcher` mới cho từng đầu vào**.

Điểm quan trọng: tái sử dụng `Pattern` chủ yếu tránh việc phân tích/biên dịch lại biểu thức. Nó không làm mất chi phí quét văn bản, không bảo đảm cải thiện tốc độ trong mọi tình huống và không chữa được regex có thuật toán khớp quá tốn kém.

## 1. Pattern, Matcher và hai giai đoạn xử lý

Ví dụ:

~~~java
Pattern pattern = Pattern.compile("[A-Za-z][A-Za-z0-9_]{2,11}");
boolean valid = pattern.matcher("anna_27").matches();
~~~

- **`Pattern.compile(regex)`** phân tích cú pháp regex, kiểm tra tính hợp lệ, xây dựng biểu diễn nội bộ để sử dụng khi khớp. Việc này có chi phí CPU và có thể cấp phát bộ nhớ; biểu thức sai cú pháp gây `PatternSyntaxException` (unchecked exception).
- **`Pattern`** là đối tượng biểu diễn regex đã biên dịch. Theo hợp đồng API Java, `Pattern` **bất biến và an toàn để dùng chung giữa các thread**.
- **`pattern.matcher(input)`** tạo `Matcher` cho đầu vào cụ thể. `Matcher` giữ trạng thái tìm kiếm (vị trí, nhóm bắt được, vùng tìm kiếm, v.v.), nên **không an toàn để nhiều thread cùng thao tác**.
- **`matcher.matches()`** kiểm tra **toàn bộ chuỗi đầu vào** có phù hợp regex không.
- **`matcher.find()`** tìm một đoạn con khớp, bắt đầu tại vị trí tìm kiếm hiện tại. **`matcher.lookingAt()`** kiểm tra khớp từ đầu vùng tìm kiếm, nhưng không bắt buộc phải phủ hết vùng đó.

Đừng nhầm việc *biên dịch regex* với việc *so khớp một chuỗi*: giữ `Pattern` loại bỏ bước biên dịch lại, nhưng mỗi lượt kiểm tra vẫn cần một `Matcher` (hoặc reset một matcher dùng tuần tự, trong phạm vi có kiểm soát).

## 2. Những cách viết và tác động hiệu năng

Giả sử `regex` là một biểu thức cố định và `value` là chuỗi cần kiểm tra:

| Cách viết | Hành vi khi gọi lặp lại |
| --- | --- |
| `value.matches(regex)` | Tiện lợi cho một lần kiểm tra; gọi `Pattern.matches(regex, value)`, có bước biên dịch regex trong lời gọi. |
| `Pattern.matches(regex, value)` | Phương thức tiện ích thực hiện biên dịch regex rồi kiểm tra toàn bộ đầu vào; không giữ lại `Pattern` để tái sử dụng cho lần sau. |
| `Pattern.compile(regex).matcher(value).matches()` | Thể hiện rõ một lần biên dịch mới trên mỗi lượt gọi. |
| `PATTERN.matcher(value).matches()` với `PATTERN` đã được biên dịch trước | Dùng lại `Pattern`; mỗi lượt vẫn tạo `Matcher` và thực hiện so khớp. |

Ba cách đầu có thể phù hợp với kiểm tra đơn lẻ hoặc đoạn mã ít khi chạy. Khi nằm trong vòng lặp xử lý hàng nghìn bản ghi với cùng regex, chi phí biên dịch lại có thể đáng kể. Mức chênh lệch thực tế phụ thuộc vào độ phức tạp regex, độ dài đầu vào, tần suất gọi, cách JVM tối ưu và phần việc khác của ứng dụng; **không nên khẳng định một tỷ lệ tăng tốc cố định nếu chưa đo**.

Phương thức `String.matches(regex)` **không tương đương** `String.contains(text)`: đối số của `matches` là regex và nó kiểm tra toàn bộ chuỗi. Với mục tiêu tìm chuỗi con thông thường, dùng phương thức thao tác chuỗi phù hợp thường rõ ràng hơn.

## 3. Ví dụ Java 17+ chạy được: biên dịch mỗi lượt và tái sử dụng

Lưu chương trình sau thành **PatternReuseDemo.java** (UTF-8). Regex yêu cầu tên bắt đầu bằng chữ ASCII, tiếp theo là 2–11 ký tự chữ/số/gạch dưới: tổng cộng 3–12 ký tự. Chương trình minh họa ba cách kiểm tra cùng một tập đầu vào; bộ đếm chỉ ghi nhận số lệnh `compile()` **mà vòng lặp đầu tiên gọi trực tiếp**, không phải phép đo tổng số thao tác nội bộ của JVM.

~~~java
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternReuseDemo {
    private static final String USERNAME_REGEX =
            "[A-Za-z][A-Za-z0-9_]{2,11}";

    // Biên dịch một lần khi lớp được khởi tạo.
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile(USERNAME_REGEX);

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    public static void main(String[] args) {
        List<String> names = List.of("anna_27", "ab", "9tom", "bob");

        int explicitCompilations = 0;
        int countCompileAgain = 0;
        for (String name : names) {
            // Chi phí biên dịch lặp lại dù regex không đổi.
            Pattern p = Pattern.compile(USERNAME_REGEX);
            explicitCompilations++;

            if (p.matcher(name).matches()) {
                countCompileAgain++;
            }
        }

        int countReuse = 0;
        for (String name : names) {
            // Mỗi input có Matcher riêng; dùng chung Pattern.
            Matcher matcher = USERNAME_PATTERN.matcher(name);
            if (matcher.matches()) {
                countReuse++;
            }
        }

        int countStringMatches = 0;
        for (String name : names) {
            if (name.matches(USERNAME_REGEX)) {
                countStringMatches++;
            }
        }

        System.out.println("Explicit compile() in loop: "
                + explicitCompilations);
        System.out.println("Matches (compile each time): "
                + countCompileAgain);
        System.out.println("Matches (reuse Pattern): "
                + countReuse);
        System.out.println("Matches (String.matches): "
                + countStringMatches);

        String text = "Order #42";
        System.out.println("matches() for digits: "
                + DIGITS.matcher(text).matches());
        System.out.println("find() for digits: "
                + DIGITS.matcher(text).find());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 PatternReuseDemo.java
java PatternReuseDemo
~~~

Kết quả:

~~~text
Explicit compile() in loop: 4
Matches (compile each time): 2
Matches (reuse Pattern): 2
Matches (String.matches): 2
matches() for digits: false
find() for digits: true
~~~

**Giải thích:** Tên `anna_27` và `bob` hợp lệ, còn `ab` quá ngắn và `9tom` không bắt đầu bằng chữ. Cả ba cách tạo cùng kết quả, nhưng vòng lặp đầu tiên gọi `Pattern.compile` bốn lần. `USERNAME_PATTERN` chỉ biên dịch một lần khi lớp khởi tạo, sau đó được tái sử dụng. Kết quả `matches() for digits: false` do `"Order #42"` không hoàn toàn gồm các chữ số; `find()` là `true` vì tìm thấy đoạn `42`.

Đây là **minh họa về số lần biên dịch và tính đúng đắn**, không phải benchmark hiệu năng. Đầu ra trên không cho biết một cách nào nhanh hơn bao nhiêu nanosecond.

## 4. Những mẫu tổ chức code nên dùng

### Regex cố định, được gọi thường xuyên

Biên dịch một lần thành hằng dùng chung:

~~~java
import java.util.regex.Pattern;

public final class Usernames {
    private static final Pattern VALID_NAME =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]{2,11}");

    private Usernames() {}

    public static boolean isValid(String value) {
        return value != null
                && VALID_NAME.matcher(value).matches();
    }
}
~~~

Đây là ví dụ **lớp tiện ích riêng**; khi dùng, lưu thành `Usernames.java` và gọi `Usernames.isValid("anna_27")`. Việc dùng `static final` phù hợp khi regex là hằng, được dùng nhiều nơi hoặc nhiều lần trong vòng đời ứng dụng. `final` cố định tham chiếu; tính an toàn khi dùng chung đến từ hợp đồng `Pattern` bất biến. Mỗi lần gọi phương thức tạo `Matcher` mới nên không chia sẻ trạng thái khớp giữa các luồng.

### Regex thay đổi theo yêu cầu nhưng dùng cho nhiều chuỗi

Không nhất thiết biến mọi regex thành `static final`. Nếu regex được tạo theo một tham số của tác vụ, hãy biên dịch **một lần cho tác vụ đó**, rồi dùng lại trong vòng lặp:

~~~java
Pattern selected = Pattern.compile(regexForThisRequest, flags);

for (String value : values) {
    if (selected.matcher(value).find()) {
        // Xử lý giá trị khớp.
    }
}
~~~

Đây là **đoạn minh họa trong phương thức hiện có**: `regexForThisRequest` (kiểu `String`), `flags` (kiểu `int`), `values` (tập chuỗi) và phần xử lý phải được cung cấp bởi chương trình gọi. Khi giữ lại một `Pattern`, phải giữ **cùng regex và flags** như khi biên dịch ở từng vòng lặp. Thay đổi `Pattern.CASE_INSENSITIVE`, `Pattern.MULTILINE`, `Pattern.UNICODE_CASE` hoặc flags khác có thể đổi kết quả so khớp.

Nếu regex thực sự thay đổi sau mỗi bản ghi, tái sử dụng một `Pattern` khác regex là **sai logic**. Chỉ áp dụng cache cho cùng cặp biểu thức và cấu hình cờ.

### Nhiều regex động, có lặp lại

Cache `Pattern` chỉ có ích khi biểu thức được gọi lại đủ thường xuyên để bù chi phí tra cứu và lưu bộ nhớ. Với regex động từ nhiều người dùng:

- Dùng cache **có giới hạn số phần tử** hoặc chính sách hết hạn; cache không giới hạn theo regex tùy ý có thể tăng bộ nhớ liên tục.
- Khóa cache cần phân biệt **regex và flags**. Các biểu thức chỉ giống tên nhưng khác flags không phải cùng cấu hình.
- Xem xét tần suất trùng lặp thực tế trước khi thêm cache phức tạp. Nếu mỗi regex chỉ dùng một lần, cache có thể tăng overhead.
- Nếu biểu thức được người dùng nhập vào có ý nghĩa là **văn bản thuần** cần tìm, chuyển thành regex literal với `Pattern.quote(userText)`. Đây là xử lý ý nghĩa ký tự đặc biệt, không phải tối ưu biên dịch.

Ví dụ: `Pattern.compile(Pattern.quote("a.b"))` nhận dấu chấm là ký tự `.` thực, còn `Pattern.compile("a.b")` dùng dấu chấm như metacharacter khớp một ký tự bất kỳ.

## 5. Tái sử dụng Pattern trong môi trường đa luồng

Một `Pattern` có thể dùng đồng thời cho nhiều luồng; mỗi luồng tạo `Matcher` riêng:

~~~java
private static final Pattern ID_PATTERN =
        Pattern.compile("[A-Z]{2}-\\d{4}");

// Trong một phương thức được nhiều thread gọi:
boolean valid = ID_PATTERN.matcher(value).matches();
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập: `value` là đầu vào `String` và code đặt trong lớp có import `java.util.regex.Pattern`. Dùng chung `Matcher` giữa nhiều thread hoặc giữ một `Matcher` tĩnh để gọi `reset()` đồng thời là sai vì trạng thái của nó có thể bị thay đổi xen kẽ.

Nếu xử lý rất nhiều giá trị trong **một thread** và muốn giảm thêm cấp phát `Matcher`, có thể tái sử dụng matcher trong phạm vi đó bằng `matcher.reset(nextInput)`. Tuy nhiên đây là một tối ưu bổ sung cần benchmark và quản lý trạng thái/nhóm kết quả cẩn thận; phương án mặc định đơn giản, an toàn và dễ kiểm thử hơn là **Pattern chung, Matcher mới cho mỗi input**.

## 6. Khi nào biên dịch lại vẫn hợp lý?

Biên dịch mới mỗi lần có thể chấp nhận được nếu biểu thức chỉ dùng một lần, đoạn code không nằm trên đường xử lý nóng, hoặc regex không lặp lại. Việc duy trì thêm cache hay hằng toàn cục cho một thao tác thưa thớt đôi khi không mang lại giá trị rõ rệt.

Nếu một regex có nguy cơ `PatternSyntaxException`, chuyển sang biên dịch một lần thường khiến lỗi xuất hiện sớm hơn, ví dụ khi khởi tạo lớp hoặc khởi động tác vụ. Đó là thay đổi **thời điểm phát hiện lỗi**, không phải tự động thay đổi cú pháp regex. Với biểu thức do người dùng nhập, vẫn cần xử lý lỗi cú pháp ở ranh giới nhập liệu phù hợp.

## 7. Đo hiệu năng thế nào cho đáng tin?

Đừng dùng một lần đo ngắn `System.nanoTime()` để kết luận về tốc độ regex, vì JIT, khởi động JVM, GC, tối ưu dead-code, dữ liệu đầu vào và khác biệt máy có thể che lấp kết quả. Khi cần quyết định tối ưu, hãy dùng công cụ benchmark Java phù hợp như **JMH** và so sánh hai đường đi có cùng ý nghĩa:

1. **Compile-and-match:** mỗi lượt xử lý gọi `Pattern.compile(regex)`, tạo matcher và so khớp.
2. **Reuse-and-match:** biên dịch `Pattern` ngoài phần đo đang lặp, trong lượt xử lý chỉ tạo matcher và so khớp.
3. Sử dụng cùng regex, flags, đầu vào và thao tác `matches()`/`find()`; tiêu thụ kết quả để công việc không bị loại bỏ bởi tối ưu biên dịch.
4. Chạy warmup và nhiều lần đo; thử cả regex đơn giản/phức tạp, đầu vào ngắn/dài, chuỗi khớp/không khớp, và tải có/không có cạnh tranh giữa thread. Xem cả throughput, độ trễ và cấp phát bộ nhớ nếu chúng ảnh hưởng ứng dụng.

Để diễn giải đúng, cần nhớ rằng `Pattern.compile` thường là phần overhead dễ tránh khi **một regex được dùng lại nhiều lần**; nhưng công việc khớp regex trên input thực tế có thể chiếm phần lớn thời gian. Tối ưu luôn nên bắt đầu từ hồ sơ hiệu năng và yêu cầu nghiệp vụ của chương trình.

### Tái sử dụng Pattern không chữa được regex tốn kém

Ví dụ các biểu thức có nhiều phép lặp lồng nhau hoặc nhiều nhánh mơ hồ có thể gây backtracking nặng trên input xấu, ngay cả khi chỉ biên dịch một lần. Với input không tin cậy, cần giới hạn độ dài, thiết kế regex giảm khả năng backtracking, đặt ranh giới thời gian/tài nguyên phù hợp hoặc chọn phương thức phân tích cú pháp phù hợp hơn. Tăng tốc bước biên dịch không đồng nghĩa bảo đảm an toàn trước tấn công từ chối dịch vụ qua regex (ReDoS).

## 8. Sai lầm thường gặp

| Sai lầm | Vì sao sai hoặc tốn kém | Cách làm đúng |
| --- | --- | --- |
| Gọi `value.matches(regex)` trong vòng lặp lớn khi regex không đổi | Dùng lại biểu thức dạng text nhưng vẫn chịu bước biên dịch theo API tiện ích. | Tạo `Pattern` trước vòng lặp, dùng `pattern.matcher(value).matches()`. |
| Gọi `Pattern.compile(regex)` trong hàm được gọi hàng triệu lần | Cùng biểu thức phải phân tích lại trên từng lượt. | Khởi tạo một lần ở phạm vi phù hợp và tái sử dụng. |
| Chia sẻ một `Matcher` giữa các thread | `Matcher` lưu trạng thái và không an toàn cho thao tác đồng thời. | Chia sẻ `Pattern`, mỗi lượt/thread tạo `Matcher` riêng. |
| Đổi `matches()` thành `find()` khi tối ưu | Có thể thay đổi hoàn toàn ý nghĩa kiểm tra. | Giữ nguyên thao tác so khớp và flags khi so sánh. |
| Cache vô hạn mọi regex từ đầu vào động | Tích lũy `Pattern` và key không cần thiết, làm tăng bộ nhớ. | Cache có giới hạn, đo mức trùng lặp hoặc chỉ dùng mỗi tác vụ. |
| Tin rằng mọi phiên bản JVM luôn cache `Pattern.matches`/`String.matches` | API không cam kết một bộ nhớ cache có thể dựa vào để tối ưu. | Chủ động giữ tham chiếu `Pattern` khi cần tái sử dụng. |
| Regex phức tạp bị backtracking nhưng chỉ tối ưu compile | Thời gian so khớp có thể lớn hơn nhiều chi phí compile. | Xem lại biểu thức, input và hồ sơ hiệu năng. |

## 9. Tự kiểm tra

1. Với cùng một regex được dùng để kiểm tra 100.000 chuỗi, chỗ nào nên đặt `Pattern.compile` để tránh biên dịch lặp lại?
2. Vì sao `private static final Pattern` có thể được nhiều thread dùng chung nhưng `private static final Matcher` thường là lựa chọn sai?
3. Khi input là `"Order #42"` và regex là `"\\d+"`, vì sao `matches()` trả `false` nhưng `find()` trả `true`?
4. Nếu regex được tạo mới từ mỗi yêu cầu và chỉ sử dụng một lần, cache có chắc cải thiện hiệu năng không?
5. Vì sao phép đo benchmark phải giữ nguyên regex, flags, input và thao tác khớp giữa hai phương án?

**Ghi nhớ:** Khi regex được dùng thường xuyên, biên dịch một lần và tái sử dụng `Pattern`; tạo `Matcher` mới cho mỗi đầu vào (đặc biệt trong chương trình đa luồng). Chỉ thêm cache cho biểu thức động sau khi thấy sự lặp lại, và dùng benchmark trên tải thực để xác định hiệu quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-performance-006">Đánh đổi giữa tính dễ đọc và tối ưu theo dữ liệu đo</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh đổi giữa tính dễ đọc và tối ưu theo dữ liệu đo khi xử lý String trong Java

## 1. Mục tiêu: mã dễ hiểu, đúng trước; tối ưu khi có bằng chứng

Xử lý `String` hiện diện trong hầu hết ứng dụng Java: ghép thông điệp, tạo đường dẫn, lọc dữ liệu, chuẩn hóa chuỗi, tách ký tự, định dạng số, đọc JSON hoặc tạo nội dung trả về. Nhiều cách viết có cùng kết quả nhưng khác độ rõ ràng, mức cấp phát bộ nhớ, lượng xử lý và khả năng bảo trì.

**Nguyên tắc cốt lõi:** ưu tiên cách diễn đạt đúng, dễ đọc và dễ kiểm thử; khi có yêu cầu hiệu năng cụ thể hoặc số đo cho thấy đoạn xử lý chuỗi là nút thắt, mới chọn phương án tối ưu có thể chứng minh là cải thiện. Kết quả đo nên phản ánh đúng dữ liệu, quy mô và điều kiện chạy thực tế.

Điều này không có nghĩa bỏ qua các lỗi thiết kế dễ nhận thấy. Ví dụ, việc nối chuỗi tăng dần bằng `+=` trong một vòng lặp dài có khả năng gây nhiều cấp phát và sao chép lại; dùng một `StringBuilder` xuyên suốt vòng lặp thường là cách viết vừa đúng ý định vừa hợp lý. Nhưng đổi mọi `"Hello " + name` sang một `StringBuilder` nhiều dòng không tự động làm ứng dụng nhanh hơn.

Ba câu hỏi trước khi sửa mã:

1. **Đúng không?** Hai cách làm có giống nhau về Unicode, `null`, dấu phân cách, locale, thứ tự, lỗi và kết quả không?
2. **Chậm ở đâu?** Profiler, benchmark hoặc chỉ số production có chỉ ra đoạn chuỗi này góp phần đáng kể vào mục tiêu cần cải thiện không?
3. **Đáng đổi không?** Mức cải thiện trong workload đại diện có đủ lớn và ổn định để bù thêm mã, bộ nhớ, rủi ro và chi phí bảo trì không?

## 2. Bốn tầng tối ưu cần phân biệt

Không phải mọi thay đổi nhanh hơn trong một phép đo vi mô đều cải thiện ứng dụng.

| Tầng | Câu hỏi cần trả lời | Bằng chứng phù hợp |
|---|---|---|
| Tính đúng và khả năng bảo trì | Mã có rõ nghĩa, đúng định dạng, dễ kiểm thử và thay đổi không? | Review, unit test, so sánh kết quả với trường hợp biên |
| Cục bộ: xử lý String | Phép nối, chuyển đổi hoặc regex có tốn CPU/cấp phát quá mức không? | JMH với đầu vào đại diện, profiler CPU và allocation |
| Luồng xử lý ứng dụng | Đoạn String có thực sự nằm trên critical path không? | Profiling request, số lần gọi, phân tích heap/GC |
| Trải nghiệm/hệ thống | Thay đổi có cải thiện latency, throughput, tài nguyên hoặc chi phí không? | Load test, p95/p99 latency, throughput, GC và memory thực tế |

Ví dụ: thay `String.format` bằng `StringBuilder` trong một đoạn chạy 500 lần/ngày có thể cải thiện phép đo trong JMH mà gần như không ảnh hưởng thời gian phản hồi ứng dụng nếu phần lớn độ trễ là truy vấn dữ liệu hoặc mạng. Ngược lại, thay đổi nhỏ trong một vòng lặp chạy hàng triệu lần trên đường xử lý nóng có thể đáng giá.

Trước khi tối ưu, xác định mục tiêu: giảm latency, tăng throughput, giảm byte cấp phát trên một yêu cầu, giảm thời gian GC, hay giảm bộ nhớ lưu giữ. Các mục tiêu này có thể không cùng đi theo một hướng.

## 3. Một số lựa chọn String thường gặp

### 3.1. Phép nối `+`: ưu tiên khi biểu thức ngắn

~~~java
String greeting = "Xin chào, " + name + "!";
~~~

Đây là cách viết rõ ràng cho một vài thành phần cố định. Với Java hiện đại, trình biên dịch và JVM có cơ chế tối ưu nối chuỗi (chẳng hạn `invokedynamic` và `StringConcatFactory` trong triển khai thông thường từ Java 9), nên không chính xác khi mặc định rằng mỗi dấu `+` đồng nghĩa một `StringBuilder` được tạo hoặc một lần cấp phát độc lập.

Tuy nhiên, mẫu cập nhật dồn vào cùng biến trong một vòng lặp lớn cần chú ý:

~~~java
String result = "";
for (String item : items) {
    result += item;
}
~~~

Vì `String` bất biến, thao tác nối phải tạo kết quả mới về mặt ngữ nghĩa. Qua nhiều vòng lặp, lượng dữ liệu đã nối có thể bị sao chép nhiều lần; tổng chi phí có thể tăng nhanh theo số phần tử. Cách tự nhiên hơn cho kiểu tích lũy này là:

~~~java
StringBuilder builder = new StringBuilder();
for (String item : items) {
    builder.append(item);
}
String result = builder.toString();
~~~

Đây là lựa chọn thiết kế cho một quá trình xây dựng chuỗi dài; không phải quy tắc thay thế mọi dấu `+` trong mã nguồn.

### 3.2. `String.join`: ý định ghép danh sách rõ nhất

~~~java
List<String> tags = List.of("Java", "Spring", "SQL");
String output = String.join(", ", tags);
// Java, Spring, SQL
~~~

Nếu nhiệm vụ đơn thuần là ghép một dãy với cùng dấu phân cách, `String.join` diễn đạt trực tiếp ý định và giảm lỗi dư dấu phân cách đầu/cuối. Không cần tự cài đặt vòng lặp chỉ vì cho rằng vòng lặp chắc chắn nhanh hơn.

Khi cần liên tục thêm dữ liệu từng bước, tiền tố/hậu tố hoặc quy tắc rỗng, `StringJoiner` có thể phù hợp:

~~~java
StringJoiner joiner = new StringJoiner(", ", "[", "]");
joiner.add("Java").add("Spring");
// [Java, Spring]
~~~

Dùng `StringBuilder` nếu cần kiểm soát chi tiết hơn: định dạng xen kẽ, chèn/xóa ký tự hoặc nhiều quy tắc nối khác nhau.

### 3.3. `StringBuilder`: hợp lý ở đoạn tạo chuỗi có trạng thái

~~~java
StringBuilder builder = new StringBuilder();
for (int i = 0; i < tags.size(); i++) {
    if (i > 0) {
        builder.append(", ");
    }
    builder.append(tags.get(i));
}
String output = builder.toString();
~~~

So với `String.join`, cách này dài hơn và tự gánh trách nhiệm quản lý dấu phân cách. Nó trở nên có giá trị khi yêu cầu đầu ra phức tạp hơn và việc xây dựng chuỗi trong một bộ đệm là tự nhiên.

Một chú ý về trường hợp biên: đừng dùng `builder.length() > 0` làm dấu hiệu chắc chắn “đã thêm ít nhất một phần tử” nếu phần tử đầu có thể là chuỗi rỗng. Dùng chỉ số hoặc biến `first` để bảo toàn số phần tử đã xử lý.

`StringBuilder` là đối tượng mutable, không an toàn khi nhiều luồng tự do sửa cùng instance. Thường tạo nó trong phạm vi một phương thức/tác vụ và không chia sẻ giữa các request.

### 3.4. Định dạng và regex: ưu tiên đúng ý nghĩa

~~~java
String label = String.format(Locale.US, "price=%.2f", 12.5);
// price=12.50
~~~

Thay bằng `"price=" + price` có thể nhanh trong một số trường hợp nhưng **không tương đương**: kết quả có thể thành `price=12.5` và không giữ quy tắc hai chữ số thập phân. Nếu cần locale hoặc làm tròn, phải bảo toàn hành vi đó.

Với regex, đoạn sau dễ hiểu:

~~~java
String normalized = text.replaceAll("\\s+", " ");
~~~

Nếu cùng một mẫu regex được dùng lặp đi lặp lại trên đường xử lý nóng, có thể cân nhắc biên dịch một `Pattern` dùng lại:

~~~java
private static final Pattern WHITESPACE = Pattern.compile("\\s+");

// Trong phương thức:
String normalized = WHITESPACE.matcher(text).replaceAll(" ");
~~~

`Pattern` được biên dịch trước có thể tránh chi phí biên dịch pattern lặp lại. Nhưng tổng thời gian vẫn phụ thuộc dữ liệu và tần suất sử dụng; kiểm chứng bằng đo đạc. Không thay regex bằng một vòng lặp tự viết nếu vô tình làm thay đổi hành vi đối với tab, xuống dòng, ký tự Unicode hoặc đầu vào đặc biệt.

## 4. “Tương đương” là điều kiện tiên quyết để so hiệu năng

Muốn so sánh hai cách viết, phải xác định một **hợp đồng kết quả** chung. Ví dụ với danh sách các thành phần:

- Dữ liệu có thể là chuỗi rỗng không?
- Có thể chứa `null` không, và khi gặp `null` thì giữ, bỏ qua hay báo lỗi?
- Có cần dấu phân cách, escape/quote, prefix/suffix không?
- Thứ tự phần tử có bắt buộc giữ nguyên không?
- Kết quả phải theo locale nào?
- Unicode được xử lý theo code unit, code point hay grapheme cluster?
- Có yêu cầu định dạng chuẩn CSV, JSON, SQL, URL không?

Ví dụ, hai cách sau **không tương đương** nếu danh sách có chuỗi rỗng:

~~~java
List<String> items = List.of("", "B");

String joined = String.join("|", items); // |B

StringBuilder wrong = new StringBuilder();
for (String item : items) {
    if (wrong.length() > 0) {
        wrong.append('|');
    }
    wrong.append(item);
}
// wrong.toString() là "B", không phải "|B"
~~~

Nếu muốn so đo `String.join` với `StringBuilder`, phiên bản builder phải bảo toàn đúng việc phân cách phần tử, kể cả chuỗi rỗng:

~~~java
StringBuilder correct = new StringBuilder();
for (int i = 0; i < items.size(); i++) {
    if (i != 0) {
        correct.append('|');
    }
    correct.append(items.get(i));
}
// correct.toString() là "|B"
~~~

Tối ưu không được làm thay đổi dữ liệu mà người dùng hoặc hệ thống khác quan sát được. Hãy có unit test cho đầu vào rỗng, một phần tử, nhiều phần tử, Unicode, dữ liệu dài và các quy tắc lỗi trước khi so hiệu năng.

## 5. Hiểu chi phí thực tế của String

### 5.1. Cấp phát và sao chép

`String` bất biến giúp giá trị ổn định và có thể chia sẻ an toàn giữa luồng khi bản thân đối tượng được xuất bản đúng cách. Đổi lại, việc xây dựng một kết quả mới có thể cần cấp phát và sao chép dữ liệu.

Cần phân biệt:

- **Số đối tượng cấp phát**: có thể ảnh hưởng áp lực GC.
- **Số byte cấp phát**: một ít đối tượng lớn vẫn có thể tốn nhiều bộ nhớ.
- **Tổng ký tự/byte phải duyệt hoặc sao chép**: ảnh hưởng CPU và băng thông bộ nhớ.
- **Bộ nhớ giữ lại lâu**: ảnh hưởng heap, GC và cache; khác với cấp phát ngắn hạn.
- **Chi phí cuối cùng của `toString()`**: xây dựng trong bộ đệm vẫn cần tạo một kết quả `String` ở cuối.

Đừng suy ra số cấp phát chỉ từ việc đếm số dòng mã. JVM có tối ưu JIT, phân tích escape và khả năng loại bỏ một số cấp phát; mức tối ưu phụ thuộc ngữ cảnh và JDK. Dùng profiler/công cụ đo allocation để biết chuyện gì thật sự diễn ra.

### 5.2. Chi phí thuật toán thường quan trọng hơn vài ký tự mã

Nếu mỗi vòng lặp phải ghép cả phần chuỗi đã có, chi phí có thể tăng rất nhanh khi số lượng phần tử tăng. Trong khi đó, một `StringBuilder` được sử dụng xuyên suốt thường duy trì khả năng thêm dữ liệu hiệu quả.

Tuy nhiên, không cần tự suy đoán công thức chi phí chính xác cho mọi phiên bản compiler/JVM. Điều cần nhớ là **đặc điểm tăng trưởng theo kích thước dữ liệu**. Hãy thử nhiều cỡ dữ liệu đại diện thay vì chỉ một chuỗi ngắn.

### 5.3. `StringBuilder` và capacity

Có thể khởi tạo `StringBuilder` với dung lượng dự kiến:

~~~java
StringBuilder builder = new StringBuilder(1024);
~~~

Điều này có thể giảm số lần mở rộng bộ đệm nếu đã biết gần đúng kích thước đầu ra. Nhưng cấp phát quá lớn cho hàng triệu request nhỏ cũng làm tăng chi phí bộ nhớ. Chỉ pre-size khi đã có ước lượng hợp lý và số đo chứng minh nó hữu ích.

`length()` là độ dài nội dung hiện có; `capacity()` là sức chứa bộ đệm và có thể lớn hơn `length()`. Đây không phải hai đại lượng tương đương.

### 5.4. Compact Strings và khác biệt môi trường

Các JDK hiện đại có tối ưu biểu diễn `String` (chẳng hạn Compact Strings), nên số byte chiếm dụng có thể phụ thuộc nội dung ký tự và phiên bản JVM. Không nên giả định mọi `char` của mọi chuỗi tương ứng cố định hai byte heap, cũng không nên suy ra hiệu năng thực chỉ từ một mô hình bộ nhớ đơn giản.

Nếu tối ưu cấp phát là yêu cầu quan trọng, ghi lại JDK, JVM, cấu hình heap, GC, dạng dữ liệu Latin/Unicode và các tham số đo để kết quả có thể tái hiện.

## 6. Chọn phép đo theo vấn đề cần giải quyết

Đo đạc nên bắt đầu từ triệu chứng thực tế:

| Triệu chứng | Đo cái gì trước? | Có thể khám phá |
|---|---|---|
| Request chậm | p95/p99 latency, CPU profile | Chi phí xử lý String có nằm trên critical path không |
| Throughput giảm khi tải tăng | requests/s, CPU utilization, contention | Vòng lặp xử lý, mã hóa/giải mã hay log quá nhiều |
| GC dày đặc | allocation rate, bytes/op, thời gian GC | Tạo nhiều String tạm, phân tích regex lặp, format lặp |
| Heap cao | retained heap, heap dump, số lượng đối tượng | Cache String không giới hạn hoặc giữ dữ liệu lâu |
| Đoạn xử lý cụ thể bị nghi chậm | JMH time/op hoặc throughput/op | So sánh triển khai tương đương ở nhiều kích thước dữ liệu |

Đo cả **baseline** trước sửa và ứng viên sau sửa. Một kết quả đo riêng lẻ sau tối ưu không thể cho biết cải thiện bao nhiêu.

Một cách đặt mục tiêu cụ thể: “Giảm allocation của bước tạo kết quả xuống mức đủ làm giảm GC dưới tải đại diện, không đổi đầu ra”. Mục tiêu nên dựa trên nhu cầu và số đo thực, không đặt con số cải thiện tùy ý.

## 7. Vì sao benchmark bằng System.nanoTime() dễ sai?

Một cách thử thường thấy:

~~~java
long start = System.nanoTime();
// Một lần gọi mã cần đo
long elapsed = System.nanoTime() - start;
~~~

Đo một lần như vậy không đủ để kết luận API nào nhanh hơn. Kết quả bị ảnh hưởng bởi nhiều yếu tố: khởi động JVM, biên dịch JIT, warm-up, GC, lập lịch hệ điều hành, CPU frequency, cache, input quá nhỏ, noise và thậm chí việc trình biên dịch tối ưu bỏ tính toán không được sử dụng.

Lặp nhiều lần trong cùng một phương thức cũng chưa chắc giải quyết được lỗi đo: kết quả có thể bị JIT coi là bất biến, benchmark có thể tối ưu khác với production, hoặc một phiên bản đang làm ít việc hơn phiên bản kia.

Với microbenchmark Java, **JMH (Java Microbenchmark Harness)** là lựa chọn tiêu chuẩn vì cung cấp warm-up, các vòng đo, fork JVM, quản lý trạng thái và cách tiêu thụ kết quả. Nhưng JMH cũng không thay thế profiling và load test toàn hệ thống.

## 8. Thiết kế phép thử công bằng với JMH

Phần này minh họa **mã benchmark JMH**, không phải chương trình Java độc lập chỉ cần `javac` với JDK. Cần cài dependency JMH và cấu hình annotation processor của JMH trong dự án trước khi chạy.

~~~java
import java.util.List;
import java.util.stream.IntStream;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(2)
public class JoinBenchmark {
    @Param({"10", "100", "1000"})
    public int size;

    private List<String> items;

    @Setup(Level.Trial)
    public void setup() {
        items = IntStream.range(0, size)
                .mapToObj(Integer::toString)
                .toList();
    }

    @Benchmark
    public String usingJoin() {
        return String.join(",", items);
    }

    @Benchmark
    public String usingBuilder() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(items.get(i));
        }
        return builder.toString();
    }
}
~~~

Giải thích thiết kế:

1. Hai phiên bản tạo **cùng một chuỗi** cho cùng danh sách đầu vào; không thực hiện thêm việc lọc, format hay tạo dữ liệu ngẫu nhiên ở chỉ một phiên bản.
2. `@Param` kiểm thử ba quy mô để thấy xu hướng khi lượng dữ liệu tăng; những kích thước thực tế của ứng dụng có thể khác và cần thay thế phù hợp.
3. `@Setup(Level.Trial)` tạo dữ liệu ngoài đoạn được đo để không trộn chi phí tạo input với chi phí ghép chuỗi.
4. `@State(Scope.Thread)` cho mỗi luồng benchmark trạng thái riêng, tránh chia sẻ `List` hoặc bộ đệm mutable theo cách vô tình gây nhiễu.
5. `@Warmup` cho JVM có cơ hội biên dịch/tối ưu trước khi đo; `@Measurement` thu kết quả sau warm-up; `@Fork` đo trên JVM mới để giảm phụ thuộc trạng thái một lần chạy.
6. Kết quả `String` được trả từ `@Benchmark` để JMH có thể sử dụng đầu ra thay vì để trình biên dịch dễ loại bỏ toàn bộ phép tính.

**Không điền sẵn “kết quả winner” cho hai phương thức.** Máy, phiên bản JDK, dữ liệu và thiết lập JMH khác nhau có thể tạo thứ tự và mức chênh lệch khác nhau.

### Những biến số quan trọng cần lưu lại

- Phiên bản JDK/JVM, GC, CPU, hệ điều hành, cấu hình heap.
- Cỡ và phân bố input: số phần tử, độ dài, ASCII/Unicode, có null/rỗng hay không.
- Chế độ JMH: average time hoặc throughput, số warm-up/measurement/fork.
- Đơn vị đo rõ ràng: microsecond/op, ops/s, bytes/op.
- Phương pháp so sánh: cùng máy, điều kiện tải ổn định, kết quả qua nhiều lần thử; xem độ dao động và sai số chứ không chỉ giá trị trung bình.
- Kết quả kiểm thử tính đúng của cả hai triển khai trên cùng tập dữ liệu.

Nếu quan tâm memory allocation, dùng profiler JMH phù hợp (ví dụ GC profiler) để xem bytes/op bên cạnh thời gian. Nếu quan tâm latency trong dịch vụ, đo tiếp dưới tải ở tầng request.

## 9. Ví dụ Java 17+ chạy được: giữ kết quả, so hai cách viết

Tạo tệp **`StringTradeoffDemo.java`**:

~~~java
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class StringTradeoffDemo {
    private static final Pattern SPACES = Pattern.compile("\\s+");

    static String readableJoin(List<String> parts) {
        return String.join(" | ", parts);
    }

    static String manualBuilder(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        List<String> parts = List.of("Java", "Spring", "SQL");
        String readable = readableJoin(parts);
        String manual = manualBuilder(parts);

        System.out.println("join: " + readable);
        System.out.println("builder: " + manual);
        System.out.println("equivalent: " + readable.equals(manual));

        List<String> edge = List.of("", "B");
        System.out.println("empty: " + String.join("|", edge));

        String formatted = String.format(
                Locale.US, "price=%.2f", 12.5);
        System.out.println(formatted);

        String text = "  a   b \t c  ";
        String normalized = SPACES.matcher(text.strip())
                .replaceAll(" ");
        System.out.println("normalized: " + normalized);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac StringTradeoffDemo.java
java StringTradeoffDemo
~~~

Đầu ra:

~~~text
join: Java | Spring | SQL
builder: Java | Spring | SQL
equivalent: true
empty: |B
price=12.50
normalized: a b c
~~~

Ở ví dụ này, `readableJoin()` truyền đạt yêu cầu rõ hơn và dễ thay đổi hơn. `manualBuilder()` được giữ để cho thấy cách xây dựng một triển khai tương đương khi có nhu cầu bổ sung quy tắc hoặc khi benchmark cho thấy lợi ích đáng kể. Việc hai kết quả bằng nhau **chưa chứng minh** một cách nhanh hơn cách còn lại.

`String.format` bảo toàn yêu cầu hai chữ số thập phân; `Pattern` lưu regex để tái sử dụng nếu áp dụng cho nhiều chuỗi. Các lựa chọn này mang ý nghĩa khác nhau: độ rõ ràng, tính đúng của định dạng và cơ hội giảm công việc lặp; không thể thay thế đánh giá thực tế bằng suy luận từ độ dài mã.

## 10. Đọc kết quả benchmark đúng cách

Giả sử một phép đo JMH (chỉ là ví dụ về **cách phân tích**, không phải số liệu thực nghiệm) cho thấy hai bản chênh lệch ít trên workload nhỏ. Trước khi chọn bản có số trung bình thấp hơn, hãy xem:

- Chênh lệch có lớn so với độ dao động/sai số không?
- Chênh lệch có tồn tại qua nhiều lần chạy, nhiều fork và bộ dữ liệu không?
- Khi input tăng, hiệu năng thay đổi ra sao?
- Có khác biệt allocation hoặc GC làm thay đổi mục tiêu hay không?
- Số lần gọi thực tế đủ nhiều để tác động đến request latency hoặc tài nguyên máy chủ không?
- Phiên bản nhanh hơn có gây thêm lỗi, phức tạp hoặc khó bảo trì không?

**Hiệu năng cục bộ khác hiệu quả thực tế.** Nếu một đoạn chỉ chiếm 1% thời gian request, ngay cả tối ưu lý tưởng loại bỏ hoàn toàn chi phí đoạn đó cũng chỉ có thể tiết kiệm tối đa khoảng 1% tổng thời gian trong giả định các phần còn lại không đổi (ý tưởng của Amdahl's law). Đừng suy ra rằng cải thiện 2 lần một hàm tự động cải thiện 2 lần toàn ứng dụng.

Với các mục tiêu như giảm độ trễ đầu cuối, quyết định cuối cùng cần dựa trên số đo đầu cuối và phạm vi tác động thực, ngoài microbenchmark.

## 11. Khi nào nên chấp nhận mã phức tạp hơn?

Một tối ưu hóa đáng cân nhắc nếu đồng thời có nhiều điều kiện:

1. Nó nằm ở đoạn xử lý quan trọng theo profiler hoặc chỉ số vận hành.
2. Nó cải thiện chỉ số được ưu tiên trên input và quy mô đại diện.
3. Kết quả vẫn đúng theo bộ test hiện có, gồm các trường hợp biên.
4. Chênh lệch vượt noise đo đạc và được tái hiện.
5. Chi phí tăng về code complexity, allocation, bảo trì và rủi ro đã được xem xét.
6. Người đọc sau này có thể hiểu **vì sao** phương án phức tạp được chọn.

Ví dụ comment hợp lý:

~~~java
// This formatter is reused within one request because profiling
// identified repeated pattern compilation on the hot path.
~~~

Comment giải thích lý do đo được và điều kiện áp dụng, thay vì phát biểu chung kiểu “fast code”.

Nếu một benchmark đã cũ hoặc điều kiện JDK thay đổi, tối ưu từng hợp lý có thể cần được đánh giá lại. Mã dễ hiểu hơn là lợi ích thực: giúp sửa lỗi, kiểm thử và mở rộng mà không vô tình đổi hợp đồng dữ liệu.

## 12. Checklist trước và sau một tối ưu String

**Trước khi sửa:**

1. Mô tả chính xác hành vi mong muốn và các dữ liệu biên cần giữ nguyên.
2. Có số đo baseline cho latency/throughput/allocation đúng nhu cầu.
3. Dùng profiler xác định đoạn thực sự tốn tài nguyên.
4. Ghi rõ JDK, input và môi trường đo.
5. Xác định giả thuyết: ví dụ giảm việc biên dịch regex lặp lại hoặc tránh chuỗi trung gian trong vòng lặp.

**Sau khi sửa:**

1. Chạy lại unit test và các ca Unicode/locale/null/rỗng phù hợp.
2. Đo ứng viên và baseline trong điều kiện tương đương.
3. Kiểm tra độ dao động, allocation/GC và hành vi khi input tăng.
4. Đo tác động ở tầng ứng dụng hoặc load test nếu mục tiêu là hiệu năng hệ thống.
5. Chỉ giữ phần phức tạp thêm nếu lợi ích đáng kể, lặp lại được và đáng chi phí bảo trì.
6. Ghi chú ngắn lý do tối ưu và điều kiện đo để người bảo trì biết khi nào cần xem xét lại.

Có thể mô tả kết luận review theo mẫu: “`String.join` vẫn được giữ vì hai phương án có kết quả tương đương, chênh lệch đo không đáng kể đối với input thực tế, và phiên bản này ngắn/dễ đọc hơn”, hoặc “Dùng bộ đệm chung trong vòng lặp vì profiling cho thấy allocation ở đường xử lý nóng giảm đáng kể và load test xác nhận cải thiện”. Đây là **mẫu diễn đạt quyết định**, không phải kết quả đo đã có sẵn của ứng dụng.

## 13. Những ngộ nhận cần tránh

**“StringBuilder luôn nhanh hơn dấu +.”** Không có quy luật tổng quát cho mọi biểu thức, compiler/JVM và workload. `+` trong một biểu thức ngắn thường là lựa chọn hợp lý; ghép dồn bằng `+=` trong vòng lặp dài đáng xem xét.

**“Đo bằng nanoTime một lần đủ để kết luận.”** Một lần chạy chứa warm-up, jitter và tối ưu JIT chưa ổn định. Sử dụng JMH cho microbenchmark, kèm profiling khi xác định nút thắt.

**“Thay String.format bằng nối chuỗi mà không đổi hành vi.”** Locale, precision, dấu nhóm và số chữ số thập phân có thể khác. Cần chứng minh tính tương đương trước khi so nhanh/chậm.

**“Giảm một cấp phát chắc chắn giảm latency.”** JVM có nhiều tối ưu; chi phí cấp phát, GC và phần việc khác thay đổi theo tải. Phải đo metric thực sự cần cải thiện.

**“Đầu vào benchmark càng nhỏ càng đại diện.”** Một chuỗi vài ký tự không phản ánh các xử lý trên dữ liệu hàng nghìn phần tử, Unicode, regex dài hoặc input hỗn hợp.

**“Cách nhanh nhất trên máy phát triển chắc chắn tốt nhất ở production.”** Phần cứng, JDK, profile dữ liệu, tải song song và giới hạn tài nguyên có thể khác nhau.

**“Càng ít dòng mã càng nhanh.”** Độ dài source không phản ánh tổng chi phí thực thi; ngược lại, mã dài hơn cũng không chắc tối ưu hơn. Đọc logic và đo thực tế mới là căn cứ.

**“Đã benchmark thì không cần test đúng/sai.”** Hai cách làm có thể đạt số liệu đẹp vì một bản bỏ bớt việc cần làm. Đảm bảo output, exception, Unicode, locale và các điều kiện biên trước.

## 14. Kết luận áp dụng

Khi xử lý `String` trong Java, hãy chọn **cách đơn giản nhất diễn đạt đúng yêu cầu**: `+` cho một biểu thức ngắn, `String.join` cho danh sách có delimiter, `StringJoiner` khi thêm phần tử từng bước và cần prefix/suffix, `StringBuilder` khi tạo chuỗi có trạng thái hoặc trong vòng lặp, `String.format` khi cần quy tắc định dạng và `Pattern` khi cùng regex phải dùng nhiều lần.

Nếu có nghi ngờ hiệu năng, hãy phát biểu một giả thuyết có thể đo, chuẩn bị đầu vào đại diện, lấy baseline, dùng JMH/profiler đúng phạm vi, kiểm tra lại tính đúng, rồi xác nhận tác động trong ứng dụng. **Tính dễ đọc là một giá trị kỹ thuật; số đo đáng tin mới là cơ sở để đánh đổi nó lấy độ phức tạp tối ưu.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-performance-007">Đo bằng JMH với làm nóng, chống bỏ mã và tránh gấp hằng</a>

<details>
<summary>Xem chi tiết</summary>

# Đo hiệu năng String bằng JMH với warmup, chống bỏ mã và tránh gấp hằng

## 1. Vì sao cần JMH?

Các thao tác `String` như `indexOf`, `equals`, `substring` hoặc nối chuỗi chạy rất nhanh, nên phép đo thủ công dễ trở nên sai lệch. Xét đoạn **mã cố ý sai**:

~~~java
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    "mississippi".indexOf('s');
}
System.out.println(System.nanoTime() - start);
~~~

Kết quả của `indexOf` không được sử dụng; JVM có thể loại công việc đó. Hơn nữa, đầu vào là literal cố định nên JVM có thể tính trước kết quả, thay vì thực hiện tìm kiếm một triệu lần. Dùng `nanoTime` không giúp khắc phục vấn đề nếu đoạn code được đo không còn thực thi như dự tính.

**JMH (Java Microbenchmark Harness)** của OpenJDK được thiết kế để đo microbenchmark Java/JVM. JMH điều phối các phép đo, các iteration, việc làm nóng (warmup), JVM fork, annotation processing và sử dụng kết quả tính toán. Nó giúp hạn chế ba bẫy trọng tâm:

1. **Warmup thiếu:** số đo bị trộn với nạp lớp, interpreter, JIT compilation, profiling và deoptimization.
2. **Dead-code elimination (DCE):** kết quả không được quan sát nên trình tối ưu bỏ luôn phép tính.
3. **Constant folding:** đầu vào là hằng, trình tối ưu trả một hằng kết quả thay vì thực hiện thuật toán cần đo.

**JMH không bảo đảm một benchmark thiết kế sai trở thành đúng.** Kết quả chỉ có ý nghĩa khi workload, dữ liệu, môi trường và cách đọc score được xác định rõ.

## 2. Vòng đời phép đo: fork, warmup, measurement

JMH chạy mỗi `@Benchmark` thông qua mã harness được tạo bởi annotation processor. Trong quá trình chạy, nó có thể khởi động các **fork JVM độc lập**; trong mỗi fork, nó chạy các warmup iteration rồi measurement iteration.

| Thành phần | Ý nghĩa |
| --- | --- |
| `@Benchmark` | Đánh dấu method chứa công việc cần đo. |
| `@Warmup` | Chạy workload để JVM có thời gian tối ưu, nhưng không đưa các iteration đó vào điểm đo chính. |
| `@Measurement` | Các iteration thu thập số liệu; trong mỗi iteration có nhiều lần gọi benchmark. |
| `@Fork` | Số JVM con tách biệt, giúp phát hiện khác biệt qua lần khởi động/runtime. |
| `@BenchmarkMode` | Chọn cách diễn giải score như AverageTime, Throughput hoặc SampleTime. |
| `@OutputTimeUnit` | Chọn đơn vị hiển thị như nano giây hoặc micro giây. |
| `@Threads` hoặc `-t` | Số worker thread cùng chạy benchmark. |

Ví dụ cấu hình annotation (fragment trong class benchmark):

~~~java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
~~~

Ở cấu hình này, mỗi fork dùng 5 warmup iteration và 5 measurement iteration, mỗi iteration chạy khoảng một giây. Hai fork giúp quan sát biến thiên ở hai JVM riêng. Đây chỉ là **cấu hình khởi đầu**, không phải chứng minh JVM đã ổn định. Nếu score còn thay đổi xu hướng rõ ràng ở các iteration cuối, xem lại warmup, workload và môi trường.

JIT HotSpot có thể diễn giải bytecode, biên dịch ở nhiều mức và deoptimize khi giả định thay đổi. Chỉ đo lần chạy đầu có thể vô tình đo quá trình **làm nóng JVM** thay vì hiệu năng sau khi chương trình đã chạy lâu.

### Đọc đúng các mode

- `Mode.AverageTime` (`-bm avgt`) báo thời gian trung bình mỗi operation: ví dụ `ns/op`; nhỏ hơn thường tốt hơn khi mọi điều kiện khác như nhau.
- `Mode.Throughput` (`-bm thrpt`) báo số operation trong một đơn vị thời gian; cao hơn thường tốt hơn.
- `Mode.SampleTime` (`-bm sample`) lấy mẫu thời gian và có thể giúp xem phân bố/tail latency.
- `Mode.SingleShotTime` (`-bm ss`) phù hợp với một số trường hợp cần đo single-shot, không tương đương phép đo throughput ổn định.

Đừng so hai thuật toán với mode, đơn vị, thread count hay tập đầu vào khác nhau mà rút ra kết luận tốc độ.

## 3. Chống dead-code elimination

### Ví dụ sai: bỏ kết quả

~~~java
@Benchmark
public void wrongDiscardedResult() {
    text.indexOf(needle);
}
~~~

`String.indexOf` trả về một `int`. Nếu không ai dùng giá trị ấy và JVM thấy phép gọi không có hiệu ứng quan sát được, JIT **có thể** loại tính toán. Một score rất nhỏ chưa chứng minh `indexOf` thật sự nhanh.

### Cách 1: return kết quả

~~~java
@Benchmark
public int correctReturn() {
    return text.indexOf(needle);
}
~~~

JMH tiêu thụ giá trị mà method `@Benchmark` trả về. Với một kết quả, đây thường là cách đơn giản nhất để giữ công việc có thể quan sát.

### Cách 2: Blackhole

~~~java
@Benchmark
public void correctBlackhole(Blackhole bh) {
    int position = text.indexOf(needle);
    bh.consume(position);
}
~~~

`Blackhole` thuộc package `org.openjdk.jmh.infra`. Đối tượng được JMH cung cấp có thể tiêu thụ một hoặc nhiều kết quả để chống tối ưu bỏ công việc không mong muốn.

~~~java
@Benchmark
public void twoResults(Blackhole bh) {
    bh.consume(text.indexOf("a"));
    bh.consume(text.indexOf("Z"));
}
~~~

Các ví dụ là fragment trong class benchmark với các field/input thích hợp. Class hoàn chỉnh ở phần 7.

**Tránh dùng `System.out.println` hoặc logging trong đoạn đo để chống DCE:** chi phí I/O hoặc khóa log có thể nhiều hơn rất xa chi phí xử lý String. Cũng không nên thêm truy cập `volatile` ngẫu nhiên, bởi nó đưa ngữ nghĩa đồng bộ và overhead mới vào thí nghiệm.

`correctReturn` và `correctBlackhole` là hai cách sử dụng kết quả **của cùng phép tính**; không nên đối chiếu chúng như hai thuật toán cạnh tranh. Sink/harness có overhead riêng, nhất là với phép đo chỉ vài nano giây.

## 4. Tránh constant folding

Một benchmark vẫn có thể sai **dù đã return**:

~~~java
@Benchmark
public int wrongConstantFolded() {
    return "mississippi".indexOf('s');
}
~~~

Đầu vào là literal và kết quả luôn là `2`. JVM **có thể** rút gọn toàn bộ phép tìm kiếm thành trả hằng `2`; đo điều đó không đại diện cho thời gian tìm trên `String` nhận từ runtime. Đưa literal vào field `static final` hoặc bọc nó bằng helper không loại được nguy cơ này.

### Đầu vào qua State và Param

Đối với benchmark cần đo tìm kiếm trên chuỗi đã có, JMH cho phép tạo input ở thời điểm setup:

~~~java
@State(Scope.Thread)
public class InputState {
    @Param({"128", "4096"})
    public int length;

    String text;
    String needle;

    @Setup(Level.Trial)
    public void prepare() {
        char[] chars = new char[length];
        java.util.Arrays.fill(chars, 'a');
        text = new String(chars) + "Z";
        needle = "Z";
    }
}
~~~

- `@State(Scope.Thread)` cấp state riêng cho mỗi worker thread, tránh chia sẻ một đối tượng mutable ngoài ý muốn.
- `@Param` cho JMH thử các giá trị input khác nhau; `length` không phải hằng được nhúng trực tiếp vào benchmark method.
- `@Setup(Level.Trial)` chuẩn bị input trước khi đo, để ta chỉ đo tìm kiếm trên String đã có.
- Dữ liệu được cấp tại runtime giúp giảm đường constant folding phi thực tế.

Đây là ví dụ kỹ thuật về **cách cấp input**, chưa phải class độc lập. Mã hoàn chỉnh ở phần 7 tích hợp các annotation trên.

**Không đồng nghĩa “cấm JIT tối ưu”.** HotSpot vẫn có thể inline `indexOf`, dùng intrinsic, branch prediction và cache. Đây có thể là các tối ưu hợp lệ của ứng dụng thực sự.

### Chọn lúc setup

| Annotation | Dùng khi | Lưu ý |
| --- | --- | --- |
| `@Setup(Level.Trial)` | Đầu vào dùng suốt một trial, ví dụ chuỗi chuẩn bị để tìm kiếm. | Chi phí khởi tạo không tính vào thao tác đo. |
| `@Setup(Level.Iteration)` | Muốn làm mới đầu vào giữa các iteration. | Có thể thay đổi trạng thái cache/profile. |
| `@Setup(Level.Invocation)` | Buộc làm mới dữ liệu cho từng invocation. | Overhead có thể ảnh hưởng nghiêm trọng microbenchmark nhỏ; tránh dùng mặc định. |

Nếu mục đích là đo **tạo String**, thao tác tạo phải nằm trong method benchmark. Nếu chỉ đo **indexOf trên String đã có**, nên tạo String trước như trên.

## 5. Tạo project JMH Maven

Khuyến nghị dùng Maven archetype chính thức của JMH để có đủ JMH core, annotation processor và đóng gói runnable benchmark runner. Chỉ thêm annotation `@Benchmark` vào source rồi gọi method bằng `main` sẽ không tự tạo ra quá trình đo JMH.

Yêu cầu: cài Maven và JDK 17+. Trong thư mục làm việc riêng chưa có dự án Maven trùng tên, chạy:

~~~bash
mvn archetype:generate -DinteractiveMode=false -DarchetypeGroupId=org.openjdk.jmh -DarchetypeArtifactId=jmh-java-benchmark-archetype -DarchetypeVersion=1.37 -DgroupId=org.example.bench -DartifactId=string-jmh-demo -Dversion=1.0-SNAPSHOT
cd string-jmh-demo
~~~

`1.37` là một phiên bản JMH **ghim cụ thể** cho ví dụ để người học có thể tái hiện; không có nghĩa đây luôn là phiên bản mới nhất.

Thêm file `src/main/java/org/example/bench/StringJmhPitfalls.java` với code ở phần 7. Có thể dùng project benchmark riêng tham chiếu module sản phẩm bằng Maven dependency. Khi đóng gói, chú ý giữ cấu hình annotation processing của archetype để JMH tạo runner và metadata benchmark.

Các API trong ví dụ dùng được trên Java 17+. Nếu project của bạn đặt compiler release/target khác, cấu hình Maven compiler phù hợp với JDK đang sử dụng.

## 6. Thiết kế workload tìm kiếm String

Ví dụ dùng `String.indexOf("Z")` trên chuỗi có `length` chữ `a` và ký tự `Z` ở cuối. Đây là một workload đơn giản:

- Với `length=128`, `indexOf("Z")` trả 128.
- Với `length=4096`, `indexOf("Z")` trả 4096.
- `@Param` kiểm tra hai kích thước, không gộp chúng thành một score.
- `@Setup` tạo String trước khi bắt đầu measurement.
- Benchmark tiêu thụ kết quả qua return hoặc Blackhole.

Workload này đại diện cho tìm token ở cuối **chuỗi ASCII có cấu trúc đều**. Nó **không** đại diện cho mọi `String.indexOf`: ứng dụng thực có thể có trường hợp match ở đầu, không match, input Unicode, độ dài hoặc phân bố ký tự khác. Benchmark có ý nghĩa phải gắn với tập input và một câu hỏi rõ ràng.

## 7. Benchmark hoàn chỉnh chạy trên Java 17+

File: **`src/main/java/org/example/bench/StringJmhPitfalls.java`**.

~~~java
package org.example.bench;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
public class StringJmhPitfalls {

    @Param({"128", "4096"})
    public int length;

    private String text;
    private String needle;

    @Setup(Level.Trial)
    public void prepare() {
        char[] chars = new char[length];
        Arrays.fill(chars, 'a');
        text = new String(chars) + "Z";
        needle = "Z";
    }

    // Phản ví dụ: JIT có thể xóa công việc nếu bỏ kết quả.
    @Benchmark
    public void wrongDiscardedResult() {
        text.indexOf(needle);
    }

    // Phản ví dụ: literal có thể được gấp thành hằng.
    @Benchmark
    public int wrongConstantFolded() {
        return "mississippi".indexOf('s');
    }

    // Cách giữ kết quả đơn giản nhất.
    @Benchmark
    public int correctReturn() {
        return text.indexOf(needle);
    }

    // Giữ kết quả qua Blackhole.
    @Benchmark
    public void correctBlackhole(Blackhole bh) {
        bh.consume(text.indexOf(needle));
    }
}
~~~

Đây là **benchmark JMH**, không cần viết `main`: mã runner của JMH được đóng gói trong `target/benchmarks.jar`. Nếu chỉ gọi `javac StringJmhPitfalls.java` mà không có dependency và annotation processor, biên dịch sẽ thất bại do thiếu class JMH.

| Method | Vấn đề hoặc biện pháp | Có dùng kết quả để quyết định tốc độ thuật toán? |
| --- | --- | --- |
| `wrongDiscardedResult` | Kết quả bị bỏ → nguy cơ DCE. | Không. |
| `wrongConstantFolded` | Toàn literal → nguy cơ constant folding. | Không. |
| `correctReturn` | Input qua state, return được JMH xử lý. | Có thể, với workload đúng đã mô tả. |
| `correctBlackhole` | Input qua state, Blackhole tiêu thụ. | Có thể, nhưng không so riêng overhead sink như một thuật toán. |

Hai method có tiền tố `wrong` chỉ là **minh họa vì sao benchmark có thể báo score đánh lừa**. Mã này đo `indexOf` trên String đã có; nếu cần đo cả tạo String/GC, hãy viết workload khác với các phép cấp phát trong method cần đo.

## 8. Biên dịch, chạy và đọc JMH report

Trong thư mục Maven project vừa tạo:

~~~bash
mvn clean verify
java -jar target/benchmarks.jar StringJmhPitfalls -wi 5 -i 5 -w 1s -r 1s -f 2 -t 1 -bm avgt -tu ns
~~~

Lệnh thứ hai lọc các method thuộc `StringJmhPitfalls` và chạy với:

| Cờ | Ý nghĩa |
| --- | --- |
| `-wi 5` | 5 warmup iterations. |
| `-w 1s` | Khoảng 1 giây mỗi warmup iteration. |
| `-i 5` | 5 measurement iterations. |
| `-r 1s` | Khoảng 1 giây mỗi measurement iteration. |
| `-f 2` | 2 JVM fork riêng. |
| `-t 1` | 1 worker thread. |
| `-bm avgt` | Average time mỗi operation. |
| `-tu ns` | Đơn vị nano giây. |

Với 4 benchmark method × 2 cấu hình `length` × 2 forks, cấu hình này thực sự tốn thời gian do mỗi fork chạy warmup và measurement nhiều giây. Nếu chỉ muốn xác nhận project/build có thể chạy, giảm số iteration/thời lượng **cho smoke test**; khi báo cáo hiệu năng, dùng cấu hình đo đủ tin cậy.

JMH in quá trình warmup, đo và kết quả với các cột như `Benchmark`, `(length)`, `Mode`, `Cnt`, `Score`, `Error`, `Units`. Ở đây `Score` thường biểu thị `ns/op`; `Error` thể hiện mức bất định JMH ước lượng từ dữ liệu quan sát. **Không có một stdout số ns/op chính xác cố định** giữa các máy, JDK hay lần chạy. Không được bịa kết quả benchmark trước khi thật sự đo.

Lưu kết quả có cấu trúc:

~~~bash
java -jar target/benchmarks.jar StringJmhPitfalls.correctReturn -wi 5 -i 5 -w 1s -r 1s -f 2 -t 1 -bm avgt -tu ns -rf json -rff jmh-results.json
~~~

Lệnh trên chỉ chạy `correctReturn`, xuất báo cáo JSON. Khi so sánh số liệu về sau, ghi lại JDK vendor/version, JVM flags, phiên bản JMH, loại CPU, hệ điều hành, thread count, GC, chế độ điện năng, phiên bản code và input.

## 9. Các lỗi thực nghiệm ngoài warmup/DCE/folding

### Tối ưu hợp lệ của JVM

JIT có thể inline method hoặc dùng intrinsic cho thao tác `String`. Những tối ưu này có thể là đường thực thi thực tế, vì vậy đừng mặc định vô hiệu hóa mọi tối ưu. Nhưng nếu công việc không được dùng, JIT có thể **loại cả thuật toán**; đó là lỗi benchmark.

**Loop-invariant code motion** có thể đẩy tính toán không đổi ra khỏi vòng lặp; **escape analysis/scalar replacement** có thể khiến một đối tượng tạm không cần cấp phát trên heap. Benchmark có đối tượng được tạo rồi không dùng có thể báo chi phí thấp một cách vô nghĩa so với kịch bản thật.

### GC, cache và thread

- JMH không cách ly hoàn toàn CPU khỏi tải nền, cache, branch prediction, nhiệt độ hoặc thay đổi tần số xung.
- `@State(Scope.Benchmark)` có thể chia sẻ state giữa các thread. Nếu không định đo tranh chấp, dùng `Scope.Thread` như mẫu và `-t 1` khi học cơ bản.
- `@Setup(Level.Invocation)` có overhead; dùng nó cho mọi benchmark nhỏ thường làm méo số liệu.
- Nếu mục tiêu liên quan allocation, có thể thử profiler `-prof gc` để bổ sung thông tin GC/allocation; đọc theo ý nghĩa profiler, không suy ra chỉ từ score.
- Khi một invocation cố ý xử lý N phần tử, nên mô tả đúng mỗi operation gồm gì; `@OperationsPerInvocation(N)` chỉ thích hợp khi N được xác định chính xác và cố định.

### Dữ liệu không đại diện

Đo trên một `String` lặp chữ `a` giúp học JMH nhưng không đủ đại diện cho log đa ngôn ngữ, đường dẫn URL, chuỗi Unicode, token đầu/cuối hoặc trường hợp không tìm thấy. Muốn đo ảnh hưởng của độ dài, xác suất match, bộ chữ, kích thước cache, hãy đưa từng chiều vào `@Param` hoặc một workload được định nghĩa rõ.

## 10. Quy trình thiết kế và diễn giải benchmark có ích

1. **Đặt câu hỏi:** đo tìm kiếm, dựng chuỗi hay nối chuỗi? Đưa đúng thao tác vào method benchmark.
2. **Xác minh correctness:** các phương án so sánh cần cho cùng kết quả trên cùng input. JMH không thay thế unit test.
3. **Thiết kế input:** dùng state/param runtime, thử vài kích thước và kịch bản đại diện.
4. **Giữ kết quả sống:** return hoặc `Blackhole.consume`; tránh log hay I/O.
5. **Loại nguy cơ gấp hằng phi thực tế:** không benchmark một biểu thức literal cố định rồi suy rộng.
6. **Làm nóng, đo, fork:** dùng đủ iteration/thời lượng để quan sát ổn định và biến thiên.
7. **Bảo đảm điều kiện tương đương:** cùng JDK, flags, máy, thread count, input, mode và đơn vị.
8. **Đọc score cùng error:** tránh tuyên bố thắng nếu khác biệt quá nhỏ so với nhiễu.
9. **Đối chiếu với ứng dụng thật:** microbenchmark đo một điểm rất nhỏ; latency/throughput end-to-end còn phụ thuộc nhiều tầng và GC.

### Dấu hiệu cần nghi ngờ

| Quan sát | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| Method bỏ kết quả nhanh ngang benchmark rỗng | DCE. | Dùng return hoặc Blackhole. |
| Phép tìm kiếm literal “nhanh bất thường” | Constant folding. | Dùng input từ state runtime. |
| Iteration đầu chậm rồi giảm rõ rệt | JVM vẫn warmup/JIT. | Tăng warmup, xem từng iteration/fork. |
| Fork dao động mạnh | GC, tải CPU, profile hoặc môi trường khác. | Kiểm tra môi trường, chạy lại, phân tích biến thiên. |
| Thêm println làm score tăng vọt | Benchmark đo I/O. | Loại log khỏi vùng đo. |
| Hai kết quả rất sát nhau, error chồng lấn | Tín hiệu nhỏ so với nhiễu. | Không kết luận quá mức; điều chỉnh thí nghiệm. |

## 11. Câu hỏi tự kiểm tra

**1. Dùng `System.nanoTime` và vòng lặp triệu lần đã đủ chính xác chưa?** Chưa. Compiler có thể xóa/gấp mã và phép đo chưa kiểm soát warmup/fork.

**2. `@Warmup(iterations=5)` có chắc tạo steady state?** Không. Cần xem xu hướng qua iteration và nhiều fork.

**3. `return "mississippi".indexOf('s')` đã chống DCE, còn bẫy nào?** Constant folding, vì kết quả có thể được tính trước là `2`.

**4. Khi nào dùng Blackhole?** Khi muốn JMH tiêu thụ một hoặc nhiều giá trị được tính trong benchmark.

**5. Đo `indexOf` trên chuỗi sẵn có thì tạo chuỗi ở đâu?** Thường ở `@Setup(Level.Trial)`, ngoài thời gian invocation cần đo.

**6. Vì sao không có stdout ns/op duy nhất cho ví dụ?** JMH đo runtime thực, phụ thuộc máy, JVM, input, tải hệ thống và sự tối ưu.

**7. Có nên so `correctReturn` với `correctBlackhole` để chọn thuật toán nhanh?** Không. Chúng cùng làm một phép tính và chỉ khác cách sink nhận kết quả.

## Tài liệu tham khảo

- [OpenJDK JMH — README](https://github.com/openjdk/jmh/blob/master/README.md): tạo project benchmark bằng Maven archetype, runner và lệnh build.
- [JMHSample_08_DeadCode](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_08_DeadCode.java): ví dụ dead-code elimination.
- [JMHSample_09_Blackholes](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_09_Blackholes.java): tiêu thụ kết quả benchmark.
- [JMHSample_10_ConstantFold](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_10_ConstantFold.java): nguy cơ constant folding.
- [JMH 1.37 — Maven Central](https://central.sonatype.com/artifact/org.openjdk.jmh/jmh-core/1.37): phiên bản cố định trong ví dụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-performance-008">Đánh giá bộ nhớ và kết quả trên dữ liệu ASCII, tiếng Việt và emoji</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá bộ nhớ và kết quả String trên dữ liệu ASCII, tiếng Việt và emoji

**String.length() không trả về số byte.** Trong Java, chuỗi ASCII, văn bản tiếng Việt và emoji có thể khác nhau về số đơn vị mã UTF-16, số Unicode code point, kích thước khi mã hóa UTF-8 và dung lượng bộ nhớ của đối tượng trong JVM. Đánh giá hiệu năng String phải xác định rõ **đơn vị đo**, **dữ liệu đầu vào**, **thao tác** và **phiên bản JVM**.

Bài học này xây dựng phép so sánh có thể chạy trên Java 17+, giải thích cơ chế Compact Strings, những trường hợp Unicode dễ sai và quy trình đo heap/hiệu năng phù hợp. Các số byte mã hóa được xác định trực tiếp từ dữ liệu; con số payload nội bộ chỉ là **ước lượng có điều kiện**, còn thời gian chạy và heap thực tế cần đo bằng công cụ.

## 1. Các đại lượng cần phân biệt

| Đại lượng | Cách lấy trong Java | Thể hiện |
| --- | --- | --- |
| **UTF-16 code unit** | **s.length()** | Số đơn vị kiểu **char** trong mô hình API Java String |
| **Unicode code point** | **s.codePointCount(0, s.length())** | Số điểm mã Unicode, không nhất thiết là số ký tự nhìn thấy |
| **UTF-8 bytes** | **s.getBytes(StandardCharsets.UTF_8).length** | Kích thước byte khi truyền/lưu dưới định dạng UTF-8 |
| **Grapheme cluster** | Bộ phân đoạn grapheme phù hợp Unicode | Đơn vị ký tự người dùng cảm nhận; có thể gồm nhiều code point |
| **Heap memory** | Profiler/heap dump/JOL tùy mục tiêu | Header, byte[] nội bộ, tham chiếu, padding và dữ liệu còn được giữ |
| **Allocation/op** | Benchmark/profiler GC | Dung lượng cấp phát tạm mỗi lần thực hiện thao tác |

Không suy ra heap từ **getBytes(UTF_8).length**. Khi gọi **getBytes**, JVM còn có thể cấp phát mảng byte mới cho dữ liệu được mã hóa.

### UTF-16 unit và code point khác nhau thế nào?

Một **char** trong Java lưu một đơn vị mã 16-bit, không phải lúc nào cũng là một Unicode code point. Nhiều chữ trong BMP cần một unit; một điểm mã ngoài BMP, chẳng hạn emoji **🙂**, cần **hai unit** gọi là *surrogate pair*. Do đó **"🙂".length() là 2** nhưng codePointCount là **1**.

Ngược lại, một cụm emoji hiển thị như **👩‍💻** chứa **ba code point**: 👩, ZWJ (ký tự nối U+200D) và 💻. Vì hai hình emoji thành phần nằm ngoài BMP, chuỗi cần **năm UTF-16 code unit**, trong khi bộ hiển thị có thể vẽ nó thành **một** biểu tượng.

## 2. Bộ dữ liệu đại diện và số liệu chính xác

Ta dùng sáu trường hợp kiểm thử:

- **ASCII**: năm chữ ASCII.
- **café**: có **é** U+00E9, ngoài ASCII nhưng thuộc Latin-1.
- **Việt**: có **ệ** U+1EC7, ngoài Latin-1.
- **🙂**: một điểm mã ngoài BMP.
- **👩‍💻**: chuỗi emoji có ZWJ nối ba điểm mã.
- **Việt**: dạng phân rã Unicode, gồm **V, i, e, dấu chấm dưới U+0323, dấu mũ U+0302, t**. Chuẩn hóa NFC cho dạng tương đương **Việt**.

| Dữ liệu | **length()** | **codePointCount()** | **Byte UTF-8** |
| --- | ---: | ---: | ---: |
| ASCII | 5 | 5 | 5 |
| café | 4 | 4 | 5 |
| Việt | 4 | 4 | 6 |
| 🙂 | 2 | 1 | 4 |
| 👩‍💻 | 5 | 3 | 11 |
| Việt dạng phân rã | 6 | 6 | 8 |

**Giải thích:**

- ASCII: một ký tự thông thường tương ứng một code unit và một byte UTF-8.
- Chữ **é** mã hóa UTF-8 thành hai byte nhưng chỉ một UTF-16 unit.
- Chữ **ệ** mã hóa UTF-8 thành ba byte và một UTF-16 unit.
- **🙂** dùng hai UTF-16 unit nhưng chỉ một code point; UTF-8 dùng bốn byte.
- **👩‍💻** có ba code point: 4 byte UTF-8 cho 👩, 3 byte cho ZWJ và 4 byte cho 💻, tổng cộng 11.
- Hai dấu kết hợp trong **Việt** làm tăng cả số code unit/code point và kích thước byte, dù hình thức hiển thị có thể tương tự bản dựng sẵn.

Không thể lấy một con số **“số ký tự”** rồi coi nó đồng thời là length, code point, grapheme và byte.

## 3. Ví dụ Java 17+ chạy được

Tạo tệp **StringMemoryComparison.java** với nội dung:

~~~java
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public class StringMemoryComparison {
    public static void main(String[] args) {
        String vietnamese = "Vi\u1EC7t";
        String decomposed = "Vie\u0323\u0302t";

        describe("ASCII", "ASCII");
        describe("Latin1", "caf\u00E9");
        describe("Vietnamese", vietnamese);
        describe("Emoji", "\uD83D\uDE42");
        describe("EmojiZWJ", "\uD83D\uDC69\u200D\uD83D\uDCBB");
        describe("Decomposed", decomposed);

        System.out.println("raw equal: "
                + decomposed.equals(vietnamese));
        System.out.println("NFC equal: "
                + Normalizer.normalize(
                        decomposed, Normalizer.Form.NFC)
                        .equals(vietnamese));
    }

    private static void describe(String name, String value) {
        int units = value.length();
        int points = value.codePointCount(0, units);
        int utf8Bytes = value.getBytes(StandardCharsets.UTF_8).length;

        boolean latin1 = value.chars()
                .allMatch(unit -> unit <= 0xFF);
        int payloadEstimate = units * (latin1 ? 1 : 2);

        System.out.println(name + ": utf16=" + units
                + ", codePoints=" + points
                + ", utf8Bytes=" + utf8Bytes
                + ", compactPayloadEstimate=" + payloadEstimate);
    }
}
~~~

Các escape Unicode biểu diễn chính xác các ký tự tiếng Việt và emoji, kể cả khi font console không thể hiện thị chúng. Chương trình in nhãn ASCII để stdout không phụ thuộc cách hiển thị Unicode của terminal.

Chạy:

~~~bash
javac -encoding UTF-8 StringMemoryComparison.java
java StringMemoryComparison
~~~

Stdout (đã kiểm chứng bằng Java):

~~~text
ASCII: utf16=5, codePoints=5, utf8Bytes=5, compactPayloadEstimate=5
Latin1: utf16=4, codePoints=4, utf8Bytes=5, compactPayloadEstimate=4
Vietnamese: utf16=4, codePoints=4, utf8Bytes=6, compactPayloadEstimate=8
Emoji: utf16=2, codePoints=1, utf8Bytes=4, compactPayloadEstimate=4
EmojiZWJ: utf16=5, codePoints=3, utf8Bytes=11, compactPayloadEstimate=10
Decomposed: utf16=6, codePoints=6, utf8Bytes=8, compactPayloadEstimate=12
raw equal: false
NFC equal: true
~~~

Cột **compactPayloadEstimate** do chương trình tính theo quy tắc **một byte mỗi UTF-16 unit khi mọi unit nằm trong Latin-1, ngược lại hai byte mỗi unit**. Đây là **ước lượng riêng payload** trong những JVM sử dụng cơ chế Compact Strings, **không phải đo kích thước String trên heap**.

## 4. Compact Strings và cách hiểu đúng bộ nhớ nội bộ

Trong nhiều bản **OpenJDK/HotSpot từ Java 9**, String dùng cơ chế **Compact Strings**: nội dung có thể nằm trong mảng **byte[]** với một trong hai chế độ mã hóa nội bộ.

**LATIN1:** tất cả UTF-16 unit có giá trị không vượt quá U+00FF. JVM có thể lưu một byte cho mỗi unit.

**UTF16:** khi có unit vượt U+00FF, JVM dùng hai byte cho mỗi unit trong payload chuỗi.

Bảng ước lượng chỉ riêng **mảng dữ liệu nội bộ**:

| Chuỗi | Ứng viên chế độ | Byte payload ước tính |
| --- | --- | ---: |
| ASCII | LATIN1 | 5 |
| café | LATIN1 | 4 |
| Việt | UTF16 | 8 |
| 🙂 | UTF16 | 4 |
| 👩‍💻 | UTF16 | 10 |
| Việt phân rã | UTF16 | 12 |

Trường hợp **café** rất hữu ích để phát hiện nhầm lẫn. Nó có **5 byte UTF-8** nhưng payload LATIN1 chỉ khoảng **4 byte**. Ngược lại **Việt** có **6 byte UTF-8** nhưng payload UTF16 có thể là **8 byte**. Vì vậy **mã hóa để truyền dữ liệu** và **cách lưu trong heap** không thể đồng nhất.

Những giới hạn phải ghi rõ trong báo cáo:

1. **Compact Strings là chi tiết triển khai**, không phải bảo đảm của đặc tả Java. Không suy số byte theo cách này cho mọi JVM hay khi Compact Strings bị tắt.
2. **String object** và **byte[] object** còn có object header, tham chiếu, alignment và metadata. Tổng shallow size lớn hơn payload.
3. Nhiều biến có thể trỏ cùng String, literal có thể được intern, JVM có thể dùng tối ưu chia sẻ dữ liệu hoặc String deduplication. Không được cộng máy móc payload theo số biến.
4. Một ký tự nằm ngoài Latin-1 có thể khiến **toàn bộ payload** dùng UTF16, chứ không chỉ riêng ký tự đó.
5. Kích thước shallow và retained phụ thuộc object graph. Số byte vừa cấp phát cho một thao tác cũng khác dung lượng còn tồn tại sau garbage collection.
6. JVM có thể thay đổi biểu diễn hoặc tối ưu nối chuỗi theo phiên bản, flags và compiler/JIT.

**Ví dụ:** trong chuỗi pha trộn hàng nghìn chữ ASCII với một chữ **ệ**, ước lượng payload của String có Compact Strings theo mô hình đơn giản là **hai byte cho mỗi UTF-16 unit**. Điều này không có nghĩa toàn bộ ứng dụng đều nhân đôi bộ nhớ vì object khác và chia sẻ dữ liệu vẫn tồn tại.

## 5. Lưu ý riêng với emoji và grapheme cluster

Khi duyệt String theo từng **char**, một emoji ngoài BMP bị tách thành hai surrogate:

~~~java
String smile = "\uD83D\uDE42";
System.out.println(smile.length()); // 2
System.out.println(smile.codePointCount(0, smile.length())); // 1
System.out.println(Character.isHighSurrogate(smile.charAt(0))); // true
System.out.println(Integer.toHexString(smile.codePointAt(0))); // 1f642
~~~

Đây là snippet minh họa, đặt trong một phương thức Java khi chạy; chương trình hoàn chỉnh nằm ở phần 3.

**Sai lầm phổ biến** là dùng **substring(0, 1)** để lấy “emoji đầu tiên”. Với **🙂**, cách làm này chỉ lấy một nửa surrogate pair và có thể tạo dữ liệu Unicode không hợp lệ. Khi cần cắt theo code point, dùng **offsetByCodePoints** hoặc API xử lý code point thay vì chỉ số char tùy ý.

Nhưng **code point cũng chưa chắc tương đương ký tự nhìn thấy**. Chuỗi emoji ZWJ như **👩‍💻** có ba code point dù có thể hiển thị thành một hình. Dấu kết hợp trong tiếng Việt cũng có thể cùng chữ cơ sở tạo một grapheme cluster. Khi UI giới hạn “20 ký tự hiển thị”, cần xác định quy tắc phân đoạn **grapheme** phù hợp Unicode.

Không giả định **BreakIterator.getCharacterInstance** trên mọi phiên bản JDK đều xử lý chính xác mọi chuỗi emoji ZWJ mới theo Unicode UAX #29. Khi ứng dụng cần cắt/sắp xếp con trỏ theo grapheme, hãy kiểm tra bộ dữ liệu thực tế và cân nhắc thư viện phân đoạn grapheme cập nhật, ví dụ ICU4J phù hợp yêu cầu.

## 6. Chuẩn hóa Unicode ảnh hưởng kết quả và bộ nhớ

String.equals so sánh các đơn vị mã đã lưu; hai chuỗi có hình thức hiển thị tương tự nhưng khác dạng Unicode thường **không bằng nhau** theo equals.

~~~java
String a = "Vi\u1EC7t";       // Việt dạng dựng sẵn
String b = "Vie\u0323\u0302t"; // e kèm dấu tổ hợp

System.out.println(a.equals(b)); // false
System.out.println(
        java.text.Normalizer.normalize(
                b, java.text.Normalizer.Form.NFC)
                .equals(a)); // true
~~~

Đây cũng là snippet đặt bên trong phương thức. Dạng a dài **4 UTF-16 unit**, dài **6 byte UTF-8**. Dạng b dài **6 unit**, dài **8 byte UTF-8**. Sau NFC, dạng b có thể trở thành dạng dựng sẵn với độ dài ngắn hơn.

Nếu benchmark có dữ liệu tiếng Việt, cần nêu rõ **đầu vào đã được chuẩn hóa NFC/NFD hay chưa**. Nếu ứng dụng thực phải normalize trước mỗi thao tác, hãy đo chi phí normalize riêng hoặc đo toàn bộ pipeline, đừng loại bỏ chi phí đó mà vẫn khẳng định đây là hiệu năng end-to-end.

NFC không tự biến mọi chuỗi trông giống nhau thành bằng nhau: trường hợp biến thể chữ, quy tắc locale và dấu cần xử lý theo nghiệp vụ. Cũng không nên trộn việc chuẩn hóa với so sánh byte payload mà không công bố bước biến đổi.

## 7. Đánh giá bộ nhớ: đo cái gì và bằng công cụ nào?

Nên tách ba câu hỏi:

**Câu hỏi A: Payload UTF-8 bao nhiêu byte?** Dùng **getBytes(StandardCharsets.UTF_8).length** như chương trình phần 3. Đây là con số tái lập cho dữ liệu được mã hóa, rất hữu ích khi giới hạn dung lượng request hoặc file.

**Câu hỏi B: Một String chiếm bao nhiêu byte trên heap?** Dùng công cụ kiểm tra object layout như **JOL (Java Object Layout)** hoặc heap dump/profiler, đồng thời quan sát cả String và mảng byte nội bộ. Phân biệt **shallow size** (kích thước riêng object) và **retained size** (dữ liệu chỉ còn được giữ thông qua object đó).

**Câu hỏi C: Mỗi thao tác cấp phát bao nhiêu bộ nhớ?** Dùng allocation profiler hoặc bộ đếm **B/op** trong một microbenchmark đáng tin cậy. Ví dụ, tạo chuỗi mới, mã hóa UTF-8 và normalize có thể tạo số đối tượng tạm khác nhau dù kết quả đầu ra có cùng số byte.

Không lấy hiệu giữa hai giá trị **Runtime.totalMemory() - Runtime.freeMemory()** trước/sau một thao tác ngắn làm “kích thước chính xác của String”. Con số còn phụ thuộc GC, allocation buffer, class loading, string pool, JIT và các đối tượng nền.

Nếu cần đo heap theo nhóm ASCII/tiếng Việt/emoji:

1. Cố định **JDK version, HotSpot/JVM, architecture, heap settings** và trạng thái Compact Strings; ghi lại các tham số thực tế.
2. Tạo cùng số phần tử theo từng chính sách so sánh: cùng UTF-16 unit, cùng code point, cùng byte UTF-8 hoặc cùng grapheme.
3. Tạo đối tượng thật ở runtime và giữ tham chiếu sống trong collection trong giai đoạn đo; tránh đo toàn literal được intern hoặc constant folded.
4. Chọn phép đo phù hợp: shallow bytes, retained bytes, allocation rate hay kích thước byte mã hóa; ghi rõ có tính overhead của collection hay không.
5. Lặp phép đo và quan sát theo cỡ mẫu; kiểm tra sharing, deduplication và object alignment trước khi kết luận.

**Một byte payload không phải một byte heap**. Đặc biệt đối với hàng triệu chuỗi rất ngắn, overhead của object và tham chiếu có thể chi phối tổng bộ nhớ nhiều hơn chênh lệch giữa một và hai byte payload mỗi code unit.

## 8. Đánh giá hiệu năng CPU: thiết kế phép so sánh công bằng

Phải xác định thao tác nào được đo:

| Thao tác | Ý nghĩa về hiệu năng |
| --- | --- |
| **length()** | Trả về số UTF-16 code unit; không đếm grapheme |
| **charAt(i)** | Truy cập UTF-16 unit; có thể trả surrogate |
| **codePointAt() / codePoints()** | Duyệt Unicode code point; xử lý khác với duyệt char |
| **getBytes(UTF_8)** | Mã hóa UTF-8, thường cấp phát mảng byte |
| **new String(bytes, UTF_8)** | Giải mã UTF-8 sang String |
| **Normalizer.normalize(..., NFC)** | Chuẩn hóa Unicode, có chi phí theo dữ liệu |
| **StringBuilder.append()** | Ghép các đoạn chuỗi với buffer có thể được mở rộng |

Không có định luật chung “ASCII luôn nhanh hơn tiếng Việt” hoặc “emoji luôn chậm hơn”. **length()** có đặc điểm rất khác **encode UTF-8**, và kết quả còn phụ thuộc dữ liệu, version JVM, JIT, warm-up, tính nhất quán của workload.

### Cố định cùng loại khối lượng công việc

Tối thiểu nên chạy nhiều nhóm thí nghiệm:

- **Cùng số UTF-16 code unit:** phù hợp khi so hoạt động theo String.length hoặc charAt.
- **Cùng số Unicode code point:** phù hợp khi thao tác phải duyệt code point.
- **Cùng số byte UTF-8:** phù hợp với ràng buộc kích thước payload.
- **Cùng số grapheme cluster:** phù hợp với thao tác UI theo ký tự cảm nhận, cần bộ phân đoạn đúng.

Một chuỗi gồm 5 emoji ZWJ mang nhiều dữ liệu hơn chuỗi gồm 5 chữ ASCII theo byte và code unit; không thể gọi đây là phép so sánh cùng “5 ký tự” nếu chưa xác định ý nghĩa của ký tự.

### Benchmark Java đúng phương pháp

Đối với microbenchmark, dùng **JMH (Java Microbenchmark Harness)** thay vì đo một lần bằng System.nanoTime. Quy trình chuẩn cần:

1. Warm-up cho JVM/JIT và dùng nhiều lần chạy độc lập (**fork**).
2. Dữ liệu tạo trước trong **@State** nếu chỉ đo thao tác String; nếu muốn đo cả tạo dữ liệu, phải định nghĩa benchmark riêng.
3. Dùng kết quả để tránh **dead-code elimination** (ví dụ JMH **Blackhole**).
4. Chọn đúng đơn vị **ns/op**, **ops/s** và với allocation dùng **B/op** khi profiler hỗ trợ.
5. Ghi cấu hình JDK, GC, heap, CPU, số thread, size và tỉ lệ các dạng chuỗi.
6. Báo cáo trung bình cùng phân tán/sai số và kiểm tra phép đo tăng tuyến tính hay xuất hiện điểm bất thường.

Ví dụ một benchmark mã hóa 1.000 UTF-16 unit thuộc ba nhóm nội dung trả lời câu hỏi **chi phí encode ở cùng số unit**. Một benchmark khác cố định cùng 4.096 byte UTF-8 trả lời câu hỏi **chi phí encode trên cùng kích thước payload**. Hai kết quả không nhất thiết cho cùng thứ tự tốc độ, vì chúng đánh giá hai workload khác nhau.

Ngoài microbenchmark, với API/web/file thực tế còn phải đo **end-to-end**: chuẩn hóa, encode/decode, xử lý String, ghi/đọc I/O và các bước phụ trợ. Chênh lệch vài nanosecond ở một API có thể không đáng kể khi I/O chi phối.

## 9. Ví dụ áp dụng trong sản phẩm

### 9.1. Giới hạn request theo byte

Nếu endpoint chỉ cho phép 1.024 byte UTF-8, đừng kiểm tra **text.length() <= 1024**. Với tiếng Việt và emoji, một code point có thể sử dụng nhiều byte UTF-8.

~~~java
int utf8Bytes = text.getBytes(
        java.nio.charset.StandardCharsets.UTF_8).length;
boolean withinLimit = utf8Bytes <= maxUtf8Bytes;
~~~

Đoạn này minh họa, cần khai báo **text** và **maxUtf8Bytes** trong phạm vi phù hợp. Nếu chuỗi quá lớn hoặc cần streaming, có thể dùng encoder để tránh tạo toàn bộ byte[] chỉ nhằm tính kích thước.

### 9.2. Cắt nội dung theo giới hạn ký tự hiển thị

Nếu giao diện giới hạn 20 “ký tự người dùng nhìn thấy”, substring theo **20 char** có thể cắt một cặp surrogate; cắt theo **20 code point** vẫn có thể chia đôi một chuỗi emoji nối ZWJ hoặc dấu kết hợp. Hãy xác định thuật toán grapheme phù hợp trước khi cắt.

### 9.3. Tạo khóa và so sánh tiếng Việt

Nếu hai nguồn dữ liệu một bên dùng chữ dựng sẵn và một bên dùng dấu kết hợp, **String.equals** và hashCode có thể khác. Chuẩn hóa về một dạng theo quy tắc hệ thống **cả lúc ghi lẫn lúc tra cứu** trước khi đưa vào HashMap/HashSet để tránh khóa “giống mắt thường nhưng không bằng nhau”.

### 9.4. Ghép nhiều đoạn String

Khi ghép rất nhiều đoạn trong vòng lặp, có thể dùng **StringBuilder**:

~~~java
StringBuilder builder = new StringBuilder();
for (String part : parts) {
    builder.append(part);
}
String result = builder.toString();
~~~

Đoạn này minh họa với collection/array **parts** đã có. Không khẳng định StringBuilder luôn nhanh hơn phép cộng chuỗi: compiler và JIT có các tối ưu riêng, phải so sánh đúng workload.

## 10. Lỗi thường gặp

| Lỗi hoặc ngộ nhận | Hậu quả | Cách sửa |
| --- | --- | --- |
| Dùng **length()** làm số byte mạng | Giới hạn byte sai với Unicode | Đo UTF-8 bytes trực tiếp |
| Coi một emoji = một char | Cắt vỡ surrogate pair | Duyệt code point, phân đoạn grapheme khi cần |
| Coi một code point = một ký tự hiển thị | Emoji ZWJ và dấu tổ hợp bị đếm sai | Dùng thuật toán grapheme phù hợp |
| Coi mọi ký tự tiếng Việt = hai byte UTF-8 | Sai với các dấu và điểm mã khác nhau | Đo theo code point thực tế |
| Coi **café** buộc phải dùng payload UTF16 | é thuộc Latin-1 | Phân biệt ASCII với Latin-1 |
| Lấy payload Compact String bằng byte UTF-8 | Lẫn hai cơ chế mã hóa | Tách byte nội bộ với serialized bytes |
| Lấy payload estimate làm heap object size | Bỏ qua header, byte[] và sharing | Dùng JOL/profiler/heap dump |
| Benchmark **nanoTime** một lượt | Nhiễu JIT/GC và loại bỏ phép tính | Dùng JMH với warm-up, forks, Blackhole |
| Bỏ qua NFC/NFD khi so sánh | Chuỗi tiếng Việt nhìn giống nhưng equals khác | Chuẩn hóa đồng nhất theo nghiệp vụ |
| Đo ASCII ngắn với emoji dài hơn rồi so tốc độ trực tiếp | Workload không công bằng | Cố định tiêu chí so sánh |

## 11. Mẫu báo cáo đánh giá tái lập

Một báo cáo kỹ thuật đủ tin cậy nên có:

**Dữ liệu:** sáu nhóm ASCII/Latin-1/tiếng Việt dựng sẵn/tiếng Việt phân rã/emoji đơn/emoji ZWJ; số phần tử, tỉ lệ nhóm, cách chuẩn hóa và cách tạo mẫu.

**Mã hóa:** với từng nhóm, bảng **UTF-16 units**, **code points**, **UTF-8 bytes** như phần 2. Nếu đo grapheme, ghi bộ phân đoạn, version và quy tắc Unicode.

**JVM:** Java 17 hay 21 hoặc bản khác, HotSpot hoặc JVM khác, Compact Strings bật/tắt, heap/GC flags, CPU và môi trường chạy.

**Bộ nhớ:** ghi rõ đo **byte mã hóa**, **payload nội bộ ước tính**, **shallow size**, **retained size** hay **allocation/op**; không gọi chúng chung là “size String”.

**Hiệu năng:** ghi thao tác, cùng đơn vị workload, warm-up/forks, kết quả theo đơn vị, phương sai và thời gian chạy toàn pipeline nếu phù hợp.

**Kết luận:** rút ra nhận xét **trong điều kiện đã đo**, có thể lặp lại và không khái quát thành tính chất bất biến của mọi JVM.

## 12. Câu hỏi tự kiểm tra

**1. Vì sao "café" dùng 5 byte UTF-8 nhưng có thể chỉ cần 4 byte payload Compact String?**

Vì **é U+00E9** mã hóa UTF-8 bằng hai byte nhưng vẫn nằm trong dải Latin-1 để lưu một byte trong cơ chế Compact Strings. Hai con số thuộc **hai cách mã hóa khác nhau**.

**2. Vì sao "Việt" có length 4, codePointCount 4, nhưng UTF-8 là 6 byte?**

Vì **ệ** là một code point thuộc BMP nhưng cần ba byte UTF-8; ba ký tự còn lại cần một byte mỗi ký tự.

**3. Chuỗi "🙂" có length 2; có phải hai emoji không?**

Không. Nó gồm một code point ngoài BMP và hai UTF-16 code unit.

**4. Nếu emoji "👩‍💻" có ba code point thì người dùng luôn thấy ba biểu tượng?**

Không. ZWJ có thể nối các phần thành một glyph; hình thức hiển thị phụ thuộc môi trường. Code point khác grapheme.

**5. Có được kết luận "emoji tốn gấp đôi heap" từ hai bảng payload không?**

Không. Phải cố định cỡ mẫu, nội dung, cơ chế JVM và đo cả object overhead/sharing. Bảng payload không đo heap thực.

**6. Khi so tốc độ giữa ba loại chuỗi, phép đo nào cần cố định?**

Phải cố định loại thao tác cùng **đơn vị workload** phù hợp: UTF-16 unit, code point, grapheme hay byte UTF-8; đồng thời kiểm soát JIT, warm-up, allocation và môi trường máy.

**Ghi nhớ:** hiệu năng String đa ngôn ngữ chỉ có ý nghĩa khi phân biệt **UTF-16 length**, **Unicode code point/grapheme**, **byte UTF-8**, **payload nội bộ** và **heap thực tế**. Kết luận đúng là kết luận gắn với **dữ liệu và phép đo xác định**.


</details>

- [Quay lại đầu trang](#back-to-top)
