<a id="back-to-top"></a>

# Mã hóa văn bản

## Menu
- [Quan hệ giữa byte, char UTF-16, code point và ký tự hiển thị](#text-encoding-001)
- [Unicode, UTF-8, UTF-16 và các charset thường gặp](#text-encoding-002)
- [Charset, StandardCharsets và tên charset](#text-encoding-003)
- [Chọn charset tường minh khi đọc và ghi](#text-encoding-004)
- [Charset mặc định, thay đổi UTF-8 từ JDK 18 và ngoại lệ theo API](#text-encoding-005)
- [BOM và khác biệt UTF-16, UTF-16BE, UTF-16LE](#text-encoding-006)
- [Lỗi mojibake khi encode và decode không cùng charset](#text-encoding-007)
- [Malformed input, unmappable character và chính sách xử lý](#text-encoding-008)
- [CharsetEncoder, CharsetDecoder và CodingErrorAction](#text-encoding-009)
- [Giải mã nhiều khối khi ký tự bị chia giữa hai lần đọc](#text-encoding-010)
- [Không cắt chuỗi byte hoặc char tùy ý giữa một ký tự](#text-encoding-011)
- [Phân biệt charset với Base64 và nén dữ liệu](#text-encoding-012)

## <a id="text-encoding-001">Quan hệ giữa byte, char UTF-16, code point và ký tự hiển thị</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa byte, `char` UTF-16, code point và ký tự hiển thị

> **Phạm vi:** Java Core – I/O → Mã hóa văn bản. Bài học giải thích vì sao “một ký tự” không luôn tương ứng với một `byte`, một Java `char` hoặc một Unicode code point; cách đọc, ghi, đếm, cắt và hiển thị văn bản đúng trong Java. Ví dụ dùng Java 17 trừ khi có ghi phiên bản khác.

## 1. Bốn tầng dữ liệu cần phân biệt

Khi lưu một tên người hoặc một biểu tượng cảm xúc vào tệp, hệ thống đi qua nhiều cách biểu diễn khác nhau:

`Văn bản người dùng nhìn thấy → cụm grapheme → dãy Unicode code point → dãy UTF-16 code unit trong API String/char → byte[] theo charset khi mã hóa ra I/O.`

Chiều đọc tệp diễn ra theo hướng ngược lại: **byte** cần được **giải mã** bằng một charset để tạo chuỗi ký tự. Không thể suy ra byte có nghĩa gì nếu thiếu thông tin mã hóa.

| Tầng | Đơn vị đo | Biểu diễn trong Java | Điều cần nhớ |
| --- | --- | --- | --- |
| **Byte** | 8 bit, giá trị bit 0–255 | `byte`/`byte[]`, `InputStream`, `ByteBuffer` | Java `byte` có giá trị signed -128…127, nhưng mẫu bit của nó vẫn là 8 bit. Số byte phụ thuộc charset. |
| **UTF-16 code unit** | 16 bit | Một phần tử `char`; `String.length()` đếm số code unit | Một code point ngoài BMP dùng **hai** `char` (surrogate pair). |
| **Unicode code point** | Giá trị `U+0000`…`U+10FFFF` | Thường dùng `int`, `String.codePoints()`, `codePointAt` | Đơn vị nhận dạng mã Unicode; một “ký tự nhìn thấy” vẫn có thể gồm **nhiều** code point. |
| **Grapheme cluster** (cụm ký tự mở rộng) | Một đơn vị văn bản mà người dùng thường coi là “một ký tự” khi chọn/xóa | Không có kiểu dữ liệu nguyên thủy tương ứng; có thể phân đoạn bằng regex `\X` từ Java 9 | Một cụm có thể gồm chữ + dấu tổ hợp, emoji + bộ đổi kiểu, emoji + ZWJ hoặc cặp regional indicator. |
| **Glyph / hình vẽ** | Hình do font và bộ dựng chữ tạo ra | Thuộc trình render UI, không phải một kiểu `String` | Số glyph, số ô hiển thị và số grapheme **không nhất thiết bằng nhau**. |

**Từ ngữ chính xác:** trong phần đặc tả Java, `char` là một **UTF-16 code unit**, không bảo đảm là một “ký tự Unicode hoàn chỉnh”. `String` cung cấp API và chỉ số theo UTF-16 code unit, dù Java runtime hiện đại có thể tối ưu lưu trữ nội bộ bằng cơ chế khác (ví dụ compact strings). Đừng suy ra bố cục byte trong bộ nhớ chỉ từ `String.length()`.

### Byte khác ký tự thế nào?

- `A` là code point **U+0041**. UTF-8 lưu thành **1 byte** `41`; UTF-16 cần **1 code unit** `0041`.
- `é` dạng ghép sẵn là **U+00E9**. UTF-8 lưu thành **2 byte** `C3 A9`; UTF-16 cần **1 code unit** `00E9`.
- `🙂` là **U+1F642**, ngoài BMP. UTF-8 lưu thành **4 byte** `F0 9F 99 82`; UTF-16 dùng hai code unit `D83D DE42` nhưng chỉ **1 code point** và thông thường **1 grapheme cluster**.
- `é` dạng chữ `e` cộng dấu kết hợp là **U+0065 U+0301**. UTF-8 gồm **3 byte** `65 CC 81`, UTF-16 gồm **2 code unit**, có **2 code point**, nhưng người đọc thường nhìn như **1 grapheme cluster** (trông giống `é`).
- Emoji **👩‍💻** thường là dãy `U+1F469` (người phụ nữ), `U+200D` (zero-width joiner), `U+1F4BB` (máy tính). Dãy này gồm **11 byte UTF-8**, **5 UTF-16 code unit**, **3 code point**, thường là **1 extended grapheme cluster**; font hoặc phần mềm không hỗ trợ có thể vẽ nó thành nhiều hình.

Hai từ “ký tự” và “độ dài” sẽ dễ gây nhầm nếu không ghi rõ đang đo **bytes**, **code units**, **code points** hay **grapheme clusters**.

## 2. UTF-8 và UTF-16 nằm ở hai bước khác nhau

**Unicode** định nghĩa hệ thống code point và các quy tắc xử lý văn bản. **UTF-8** và **UTF-16** là các cách biến code point thành đơn vị mã hóa (code unit). Một **charset** còn bao gồm các quy tắc encode/decode tương ứng. Code point không đồng nghĩa với byte.

- **UTF-8:** một Unicode scalar value được mã hóa thành **1–4 byte**. ASCII U+0000…U+007F dùng 1 byte. Những ký tự ngoài ASCII có thể chiếm 2, 3 hoặc 4 byte. UTF-8 có độ dài thay đổi nhưng không phụ thuộc thứ tự byte kiểu UTF-16.
- **UTF-16:** một Unicode scalar value thuộc BMP và không phải surrogate cần **1 code unit 16 bit**; code point `U+10000`…`U+10FFFF` cần **2 code unit** tạo thành surrogate pair. Khi ghi UTF-16 ra tệp, mỗi code unit lại phải được đưa thành **byte** theo thứ tự byte thích hợp, ví dụ UTF-16BE hoặc UTF-16LE.
- **Java `String` và `char`:** xét về ngữ nghĩa API, chỉ số của `String` theo UTF-16 code unit. Nhưng khi đọc/ghi tệp UTF-8, Java encode/decode giữa các đơn vị này và dãy byte; không có nghĩa tệp phải lưu UTF-16.
- **BOM:** một số tệp UTF-16 có byte-order mark giúp nhận biết endian; UTF-8 không cần BOM để xác định thứ tự byte. Đừng tự bỏ/giữ BOM vô điều kiện: hãy bám vào hợp đồng định dạng dữ liệu.
- Một `byte` trong Java là signed; khi in hex dùng `b & 0xFF` để nhận giá trị unsigned 0–255. Không dùng `String.length()` làm kích thước byte mà một payload UTF-8 sẽ chiếm.

### 2.1. Chuyển đổi byte và chuỗi luôn cần charset

~~~java
import java.nio.charset.StandardCharsets;

// Ví dụ fragment: dữ liệu đầu vào hợp lệ theo UTF-8.
String text = "Tiếng Việt 🙂";
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);       // Encode
String restored = new String(bytes, StandardCharsets.UTF_8); // Decode
boolean equal = text.equals(restored);                      // true
~~~

Đây là **fragment** nhằm trình bày bước chuyển đổi; `StandardCharsets.UTF_8` giúp kết quả không phụ thuộc charset mặc định của máy. Với payload đến từ mạng, database hoặc tệp cũ, **không thể mặc định byte luôn là UTF-8**; cần biết contract thực tế. Từ JDK 18, JEP 400 quy định UTF-8 là default charset cho các Java SE API chịu tác động, nhưng thay đổi này không tự chuyển đổi tệp legacy.

Với dữ liệu UTF-8 hợp lệ, encode rồi decode cùng charset sẽ giữ nội dung. Nếu mảng byte không đúng encoding, constructor `new String(bytes, UTF_8)` có thể thay chuỗi byte lỗi bằng ký tự thay thế; khi cần kiểm tra nghiêm ngặt, dùng `CharsetDecoder` và cấu hình lỗi dạng `CodingErrorAction.REPORT`.

## 3. `char` và surrogate pair trong Java

Kiểu `char` chứa **16 bit**. Unicode từng có thể biểu diễn các mã thông dụng trong một đơn vị 16 bit, nhưng ngày nay nhiều code point ngoài **Basic Multilingual Plane (BMP, U+0000…U+FFFF)**. UTF-16 biểu diễn code point bổ sung bằng hai `char`:

- **High surrogate:** từ `U+D800` tới `U+DBFF`.
- **Low surrogate:** từ `U+DC00` tới `U+DFFF`.
- Một high surrogate theo ngay sau bởi low surrogate hợp lệ đại diện cho một code point từ `U+10000` tới `U+10FFFF`.

Ví dụ:

~~~text
Emoji 🙂 = U+1F642
UTF-16 high surrogate = 0xD83D
UTF-16 low surrogate  = 0xDE42
"🙂".length()          = 2
"🙂".codePointCount(0, 2) = 1
~~~

**Chú ý về Unicode scalar value:** tổng không gian code point là `U+0000`…`U+10FFFF`, nhưng **U+D800…U+DFFF** được dành riêng cho surrogate và **không phải** Unicode scalar value hợp lệ để mã hóa trực tiếp bằng UTF-8. Một `String` vẫn có thể chứa surrogate lẻ do thao tác cắt hoặc nguồn dữ liệu bất thường. Khi ấy `codePoints()`/`codePointCount()` xử lý các surrogate lẻ theo hợp đồng API, **không chứng minh chuỗi là Unicode scalar sequence hợp lệ**.

Các API nên nhớ:

| API | Đơn vị trả về / thao tác |
| --- | --- |
| `s.length()` | Số **UTF-16 code unit** trong chuỗi |
| `s.charAt(i)` | Một `char` tại **chỉ số UTF-16**, có thể chỉ là một nửa surrogate pair |
| `s.codePointAt(i)` | Code point tại vị trí UTF-16 `i` nếu gặp cặp surrogate hợp lệ; trả giá trị đơn vị đơn nếu lẻ |
| `s.codePointCount(begin, end)` | Số code point trong khoảng **chỉ số UTF-16**, gồm cả surrogate lẻ được tính riêng |
| `s.codePoints()` | `IntStream` dãy code point; surrogate pair hợp lệ được gộp |
| `s.chars()` | `IntStream` dãy giá trị **char code unit**; không gộp cặp surrogate |
| `s.offsetByCodePoints(index, count)` | Dịch một vị trí UTF-16 theo số **code point**, trả về vị trí UTF-16 |
| `Character.charCount(cp)` | Số UTF-16 code unit để biểu diễn code point (1 hoặc 2) |
| `Character.toChars(cp)` / `StringBuilder.appendCodePoint(cp)` | Chuyển code point thành một hoặc hai code unit để đưa vào chuỗi |

**Cảnh báo quan trọng:** các chỉ số của `substring(begin,end)`, `charAt(i)`, `indexOf`/`lastIndexOf` và phần lớn API xử lý vị trí `String` đều là **vị trí UTF-16**. Nếu bạn lấy `substring(0, 1)` của chuỗi bắt đầu bằng emoji ngoài BMP, có thể cắt mất một nửa surrogate pair.

### 3.1. Duyệt code point đúng cách

~~~java
// Fragment: 's' là một String Java đã được khởi tạo.
for (int i = 0; i < s.length(); ) {
    int cp = s.codePointAt(i);
    // Xử lý cp ở đây, ví dụ Character.isLetter(cp).
    i += Character.charCount(cp);
}
~~~

Đoạn trên tiến theo **code point**, không theo grapheme. Với chuỗi tiếng Việt chứa combining marks, một lần lặp có thể nhận chữ cơ sở và lần tiếp theo nhận dấu tổ hợp; với emoji ZWJ, nó gặp nhiều code point dù người dùng thấy một emoji ghép.

## 4. Một “ký tự hiển thị” có thể gồm nhiều code point

Các phần mềm xử lý text thường dùng **extended grapheme cluster** theo Unicode Text Segmentation (UAX #29) làm gần đúng đơn vị người dùng nhận biết. Ví dụ quan trọng:

| Dữ liệu | Dãy code point | Số code point | Grapheme thường thấy |
| --- | --- | --- | --- |
| `A` | `U+0041` | 1 | 1 |
| `é` ghép sẵn | `U+00E9` | 1 | 1 |
| `é` phân rã | `U+0065 U+0301` | 2 | 1 |
| `🙂` | `U+1F642` | 1 | 1 |
| `🇻🇳` | `U+1F1FB U+1F1F3` (regional indicators) | 2 | 1 |
| `👩‍💻` | `U+1F469 U+200D U+1F4BB` | 3 | 1 |

Các quy tắc kết hợp phụ thuộc **phiên bản Unicode** và thư viện phân đoạn; thêm emoji mới có thể đổi kết quả theo phiên bản runtime. Trình render còn phụ thuộc font, hệ điều hành, thư viện định hình chữ và ngôn ngữ. Một grapheme không bảo đảm là **một glyph**, một pixel width hay một ô monospace.

### 4.1. Java regex `\X` cho extended grapheme cluster

Từ **Java 9**, `java.util.regex.Pattern` hỗ trợ `\X` (khớp một extended grapheme cluster) và `\b{g}` (biên cụm grapheme). Trong Java string literal, dấu backslash cần được escape: viết `"\\X"` để regex nhận ký hiệu `\X`.

~~~java
// Fragment dành cho Java 9+.
java.util.regex.Matcher m =
    java.util.regex.Pattern.compile("\\X").matcher(text);
int graphemes = 0;
while (m.find()) {
    graphemes++;
}
~~~

`text` là chuỗi cần đếm. Với Java 8, **không dựa vào `\X`** vì tài liệu regex Java 8 không có cú pháp đó. Nếu ứng dụng yêu cầu phân đoạn grapheme đúng theo một phiên bản Unicode cụ thể trên nhiều JDK, cần kiểm tra phiên bản thuật toán, thử dữ liệu thực tế, và cân nhắc thư viện Unicode chuyên biệt như ICU4J theo yêu cầu dự án.

### 4.2. `BreakIterator` có phải là `\X` không?

`java.text.BreakIterator.getCharacterInstance(locale)` có thể tìm ranh giới ký tự cho tác vụ giao diện, nhưng **không nên mặc định** kết quả giống hoàn toàn quy tắc **extended grapheme cluster** của regex `\X` ở mọi phiên bản JDK/locale, nhất là emoji ghép bằng ZWJ. Nếu yêu cầu kỹ thuật là Unicode UAX #29 cụ thể, hãy **kiểm thử** ranh giới với chuỗi mẫu và môi trường triển khai.

## 5. Ví dụ chạy độc lập: đếm cả bốn tầng

Lưu là **`UnicodeLayers.java`** (mã chạy với Java 17, sử dụng `\X` vốn có từ Java 9):

~~~java
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnicodeLayers {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private static int graphemeCount(String value) {
        Matcher matcher = GRAPHEME.matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void inspect(String label, String value) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        int chars = value.length();
        int codePoints = value.codePointCount(0, chars);
        int graphemes = graphemeCount(value);
        System.out.printf(
            "%s: bytes=%d, chars=%d, codePoints=%d, graphemes=%d%n",
            label, bytes, chars, codePoints, graphemes
        );
    }

    public static void main(String[] args) {
        inspect("A", "A");
        inspect("e-composed", "\u00E9");
        inspect("e-decomposed", "e\u0301");
        inspect("smile", "\uD83D\uDE42");
        inspect("woman-technologist", "\uD83D\uDC69\u200D\uD83D\uDCBB");
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 UnicodeLayers.java
java UnicodeLayers
~~~

**Stdout đã kiểm tra:**

~~~text
A: bytes=1, chars=1, codePoints=1, graphemes=1
e-composed: bytes=2, chars=1, codePoints=1, graphemes=1
e-decomposed: bytes=3, chars=2, codePoints=2, graphemes=1
smile: bytes=4, chars=2, codePoints=1, graphemes=1
woman-technologist: bytes=11, chars=5, codePoints=3, graphemes=1
~~~

Điều cần quan sát:

1. `e-composed` và `e-decomposed` thường trông giống nhau nhưng có **số byte, số code unit, số code point khác nhau**.
2. `smile` chỉ có **1 code point** nhưng `String.length() == 2` vì surrogate pair.
3. `woman-technologist` là **một cụm grapheme mở rộng** nhưng gồm nhiều code point do ZWJ kết hợp.
4. Các con số byte được đo theo **UTF-8** đã chỉ định; mã hóa tệp ra UTF-16 sẽ cho số byte khác. Các con số grapheme là kết quả của thuật toán phân đoạn Java đang chạy, không phải đo “số hình vẽ” trên màn hình.

## 6. Ví dụ chạy độc lập: vì sao `charAt`/`substring` dễ cắt sai

Lưu là **`UnicodeIndex.java`**:

~~~java
public class UnicodeIndex {
    public static void main(String[] args) {
        String text = "A\uD83D\uDE42B"; // A + 🙂 + B

        System.out.println("utf16=" + text.length());
        System.out.println("codePoints="
            + text.codePointCount(0, text.length()));
        System.out.printf("charAt(1)=U+%04X%n", (int) text.charAt(1));
        System.out.printf("codePointAt(1)=U+%04X%n", text.codePointAt(1));

        int afterTwoPoints = text.offsetByCodePoints(0, 2);
        System.out.println("indexAfterTwoCodePoints=" + afterTwoPoints);
        System.out.println("middle=" + text.substring(1, afterTwoPoints));
        System.out.println("splitSurrogate="
            + Character.isHighSurrogate(text.substring(1, 2).charAt(0)));
    }
}
~~~

~~~bash
javac --release 17 UnicodeIndex.java
java UnicodeIndex
~~~

Stdout:

~~~text
utf16=4
codePoints=3
charAt(1)=U+D83D
codePointAt(1)=U+1F642
indexAfterTwoCodePoints=3
middle=🙂
splitSurrogate=true
~~~

Chuỗi có bốn vị trí UTF-16: vị trí 0 là `A`, vị trí 1–2 là high/low surrogate của `🙂`, vị trí 3 là `B`. `substring(1,2)` chỉ chứa **một high surrogate lẻ**; nó **không phải** ký tự emoji hoàn chỉnh. `offsetByCodePoints(0,2)` trả **chỉ số UTF-16 bằng 3**, không phải “giá trị code point thứ hai”.

Đây là cách xử lý **ranh giới code point**, vẫn chưa bảo đảm ranh giới **grapheme cluster**. Nếu chuỗi là `e\u0301`, cắt sau code point `e` sẽ tách dấu ra khỏi chữ; muốn thao tác theo “ký tự người dùng”, cần phân đoạn grapheme.

## 7. Chuỗi trông giống nhau nhưng `equals` trả `false`: normalization

Unicode cho phép cùng một văn bản được biểu diễn bằng chuỗi code point khác nhau. Ví dụ `é` ghép sẵn `U+00E9` và `e` + `U+0301` có thể trông giống nhau nhưng chuỗi Java khác từng code unit.

~~~java
// Fragment có thể đặt trong main; java.text.Normalizer thuộc Java SE.
String composed = "\u00E9";
String decomposed = "e\u0301";

System.out.println(composed.equals(decomposed)); // false
System.out.println(
    java.text.Normalizer.normalize(
        composed, java.text.Normalizer.Form.NFC
    ).equals(
        java.text.Normalizer.normalize(
            decomposed, java.text.Normalizer.Form.NFC
        )
    )
); // true
~~~

- **NFC** tạo dạng chuẩn hóa canonical với việc composition khi áp dụng được; **NFD** decomposition canonical. `Normalizer` thuộc Java SE, không phải dependency ngoài.
- **Normalization không phải encode/decode**: nó có thể đổi chuỗi code point biểu diễn cùng nội dung canonically equivalent, còn encode/decode là chuyển giữa ký tự và byte theo charset.
- `String.equals` kiểm tra nội dung tuần tự của các code unit, không tự NFC/NFD, không so sánh theo locale và không so sánh “trông giống nhau”.
- **Không chuẩn hóa mù quáng** dữ liệu dùng để hash, ký số, so khớp định danh, mật khẩu, protocol hoặc truy vấn database; quy tắc normalization phải được quy định chung ở mọi đầu vào và bên lưu trữ. Ký tự nhìn gần giống nhưng thuộc **code point khác nhau** (homoglyph) cũng không được đảm bảo hợp nhất bởi normalization.
- Với sort/compare theo ngôn ngữ, cân nhắc API `Collator` và quy tắc locale; đó là câu chuyện khác với đếm byte/UTF-16/code point/grapheme.

## 8. Đọc/ghi file: vị trí của encoding trong đường đi dữ liệu

~~~text
           GHI                                ĐỌC
String/char (UTF-16 API)                  byte[]/InputStream
        |                                         |
   UTF-8 encoder                             UTF-8 decoder
        |                                         |
  byte[] / OutputStream                    String/char (UTF-16 API)
        |                                         |
    tệp / mạng                             xử lý code point/
                                          grapheme khi cần
~~~

**Đúng:** API có hợp đồng charset rõ ràng, ví dụ `Files.writeString(path, text, StandardCharsets.UTF_8)` và `Files.readString(path, StandardCharsets.UTF_8)` (từ Java 11); hoặc `InputStreamReader(input, StandardCharsets.UTF_8)` cho stream trong Java 8.

**Sai thường gặp:** đọc từng byte UTF-8 rồi ép mỗi byte sang `char` để ghép thành chuỗi tiếng Việt. Ví dụ `é` có byte `C3 A9`, không phải hai “chữ” tương ứng với hai byte. Cần decoder UTF-8 xử lý chuỗi byte hoàn chỉnh hoặc decoder có trạng thái qua những lần đọc.

**Ranh giới buffer:** chuỗi byte UTF-8 của một code point có thể bị chia giữa hai lần `InputStream.read(...)`. Đừng decode từng chunk bằng cách tạo `new String(chunk, UTF_8)` riêng rẽ nếu chunk có thể kết thúc ở giữa một chuỗi nhiều byte; phải dùng decoder có trạng thái hoặc lớp `Reader` duy trì trạng thái decoder qua các lần đọc.

**Dữ liệu lỗi:** trên hệ thống cần từ chối malformed UTF-8 thay vì âm thầm thay thế, có thể sử dụng:

~~~java
// Fragment: ví dụ kiểm tra byte sai UTF-8.
java.nio.charset.CharsetDecoder decoder =
    java.nio.charset.StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);

byte[] invalid = {(byte) 0xC3, (byte) 0x28};
// decoder.decode(ByteBuffer.wrap(invalid)) ném CharacterCodingException.
~~~

Đoạn này mô tả cách cấu hình; để thực thi, cần gọi `decoder.decode(java.nio.ByteBuffer.wrap(invalid))` bên trong method có `throws java.nio.charset.CharacterCodingException` hoặc khối `try/catch`. Quan hệ cần nhớ: **bytes** được giải mã thành **UTF-16 code units trong String API**; từ đó code point và grapheme phục vụ xử lý văn bản, không phải tạo lại encoding một lần nữa.

## 9. Đếm độ dài, cắt chuỗi và giới hạn nhập liệu

Đừng đặt tên biến mơ hồ như `maxCharacters` khi giới hạn chưa xác định. Câu hỏi trước khi chọn API là: **giới hạn bằng đơn vị gì?**

| Yêu cầu | Phép đo/giải pháp | Cạm bẫy |
| --- | --- | --- |
| API, giao thức giới hạn **byte UTF-8** | `s.getBytes(UTF_8).length` | `s.length()` không phải độ dài payload; đừng cắt byte giữa sequence UTF-8. |
| Thư viện/API định nghĩa độ dài bằng **UTF-16 code unit** | `s.length()` | Emoji ngoài BMP được tính hai. |
| Phân tích Unicode theo **code point** | `s.codePointCount(0,s.length())`, `s.codePoints()` | Dấu kết hợp, ZWJ, cờ emoji có nhiều code point; surrogate lẻ cần validation. |
| Giao diện đếm ký tự người dùng | Phân đoạn **extended grapheme clusters** | Các bản Unicode khác nhau và quy tắc sản phẩm có thể cần kiểm thử. |
| Cắt/truncate chuỗi để hiển thị | Cắt tại **ranh giới grapheme** đã tính và render thử | Cắt theo code point vẫn có thể tách combining mark/ZWJ; chỉ grapheme count cũng không đo chiều rộng trên màn hình. |
| Chiều rộng terminal, căn cột | Thư viện đo display width phù hợp terminal/font | Một grapheme có thể rộng 0, 1, 2 hay khác tùy renderer; ANSI control cũng ảnh hưởng. |
| Chuẩn hóa tìm kiếm/so khớp | Quy định normalization, case folding, locale/collation | `equalsIgnoreCase` không giải quyết mọi quy tắc Unicode/locale hoặc văn bản dễ nhầm. |

**Ví dụ thực tế:** Một form giới hạn “10 ký tự” cho nickname có thể mong đợi 10 grapheme, trong khi API kiểm tra 10 UTF-16 code unit sẽ tính nhiều emoji là hai và phân biệt chữ có dấu dạng ghép với dạng phân rã. Nếu backend còn có giới hạn 40 byte UTF-8, cần **hai** ràng buộc riêng: grapheme theo UX và byte theo lưu trữ. Ranh giới byte có thể nhỏ hơn hoặc lớn hơn số grapheme dựa vào nội dung.

**Khi cần cắt theo code point:** dùng `offsetByCodePoints` để tìm chỉ số UTF-16 cho `substring`. **Khi cần cắt theo grapheme:** duyệt matcher `\X`, lấy `matcher.end()` (vị trí UTF-16 sau cụm) rồi cắt ở đó. Java regex indices tính bằng UTF-16 code unit, nhưng matcher đã xác định điểm kết thúc cụm.

~~~java
// Fragment Java 9+: lấy tối đa 3 grapheme, giữ nguyên dữ liệu gốc.
java.util.regex.Matcher matcher =
    java.util.regex.Pattern.compile("\\X").matcher(text);
int endIndex = 0;
int count = 0;
while (count < 3 && matcher.find()) {
    endIndex = matcher.end();
    count++;
}
String firstThreeGraphemes = text.substring(0, endIndex);
~~~

Đoạn trên chỉ hợp lệ khi `text` được khai báo `String` trong scope. Nếu ít hơn 3 grapheme, kết quả là toàn bộ chuỗi; nếu không có, kết quả chuỗi rỗng. Với giới hạn nghiêm ngặt, phải quyết định cách xử lý các cluster có code point lạ hoặc Unicode version cũ.

## 10. Lỗi phổ biến cần tránh

1. **“Một `char` là một chữ Unicode.”** `char` là code unit 16 bit; code point ngoài BMP cần surrogate pair.
2. **“`String.length()` là số chữ nhìn thấy.”** Nó đếm code unit; không đo byte, code point hay grapheme.
3. **“`codePointCount` là số ký tự nhập liệu.”** Combining sequence/emoji ZWJ/cờ thường nhiều code point nhưng một grapheme.
4. **“UTF-8 mỗi ký tự một byte.”** Đúng chỉ với ASCII; UTF-8 có từ 1 đến 4 byte mỗi Unicode scalar value.
5. **“`String` lưu như byte[] UTF-8.”** API biểu diễn chuỗi theo UTF-16; cách tối ưu bộ nhớ của JDK là chi tiết triển khai.
6. **“`substring` cắt theo code point.”** Indices là UTF-16; dễ chẻ surrogate pair.
7. **“Dùng `String.chars()` để duyệt code point.”** `chars()` cho code unit; dùng `codePoints()` cho code point.
8. **“`new String(bytes)` luôn đọc đúng tiếng Việt.”** Constructor không chỉ định charset phụ thuộc default charset; cần biết encoding thật.
9. **“Decode từng buffer riêng biệt sẽ luôn đúng.”** Một sequence byte có thể nằm ở hai buffer; giữ trạng thái decoder.
10. **“`equals` so sánh theo mắt nhìn.”** Chữ ghép sẵn và chữ + combining mark có thể `equals == false`.
11. **“Một grapheme = một glyph = một ô hiển thị.”** Renderer/font/shaping quyết định glyph và chiều rộng.
12. **“Surrogate lẻ là ký tự Unicode hợp lệ để encode.”** Đây là dữ liệu UTF-16 lỗi theo nghĩa Unicode scalar sequence; cần quyết định báo lỗi hay thay thế.
13. **“Chỉ cần nâng JDK là emoji mới luôn hiển thị đúng.”** Hiển thị còn phụ thuộc font/UI và phiên bản Unicode; segmenter và renderer là hai bước khác nhau.

## 11. Bài tập tư duy

1. Chuỗi `"🙂"` chiếm bao nhiêu byte UTF-8, `char` UTF-16, code point và grapheme cluster?
2. Hai chuỗi `"\u00E9"` và `"e\u0301"` có `equals` không? Có thể chuẩn hóa để so sánh canonical thế nào?
3. Vì sao `"A🙂B".substring(1,2)` không phải emoji hoàn chỉnh? Hãy dùng `offsetByCodePoints`.
4. API nhận nickname tối đa 12 “ký tự nhìn thấy”, nhưng storage cho tối đa 48 byte UTF-8: cần kiểm tra bao nhiêu loại giới hạn?
5. Một file UTF-8 được đọc theo block 4096 byte. Code point 4 byte nằm vắt qua ranh giới block: decoder phải làm gì?
6. Tại sao ký hiệu `👩‍💻` có thể có 3 code point nhưng chỉ một grapheme và vẫn có thể hiển thị thành nhiều hình?

**Đáp án định hướng:**

1. **4 byte UTF-8, 2 UTF-16 code unit, 1 code point, thường 1 grapheme**.
2. `equals` là `false` vì khác dãy UTF-16; chuẩn hóa hai bên bằng `Normalizer.Form.NFC` rồi so sánh.
3. Khoảng `1..2` chỉ chứa high surrogate. Lấy điểm kết thúc bằng `offsetByCodePoints(1,1)` (trả 3), rồi `substring(1,3)`.
4. Hai giới hạn độc lập: tối đa 12 grapheme theo UX và tối đa 48 byte UTF-8 theo giao thức/lưu trữ.
5. Giữ byte chưa đủ hoặc trạng thái decoder qua lần đọc tiếp theo, sau đó mới quyết định code point; không decode chunk riêng bằng constructor `String`.
6. Zero-width joiner nối sequence theo quy tắc grapheme/emoji; renderer vẫn có thể thiếu font hoặc hỗ trợ tổ hợp.

## 12. Tài liệu tham khảo

- Oracle Java SE 21 – [`String`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html): UTF-16 representation, `length`, `charAt`, `codePointCount`, `offsetByCodePoints`, `chars` và `codePoints`.
- Oracle Java SE 21 – [`Character`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html): BMP, supplementary code point, surrogate pair, `toChars` và `charCount`.
- Oracle Java SE 9 – [`Pattern`](https://docs.oracle.com/javase/9/docs/api/java/util/regex/Pattern.html): regex `\X` và `\b{g}`.
- Oracle Java SE 21 – [`Normalizer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/Normalizer.html): Unicode NFC/NFD/NFKC/NFKD.
- Unicode Consortium – [UAX #29: Unicode Text Segmentation](https://www.unicode.org/reports/tr29/): ranh giới grapheme.
- OpenJDK – [JEP 400: UTF-8 by Default](https://openjdk.org/jeps/400): ảnh hưởng charset mặc định từ Java 18.

**Ghi nhớ:** `byte[]` đo dữ liệu đã encode; `String.length()` đo đơn vị UTF-16; `codePointCount()` đo đơn vị Unicode code point; **grapheme segmentation** gần với thao tác ký tự người dùng. Xác định loại độ dài trước khi đọc/ghi, validate, substring hoặc dựng giao diện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-002">Unicode, UTF-8, UTF-16 và các charset thường gặp</a>

<details>
<summary>Xem chi tiết</summary>

# Unicode, UTF-8, UTF-16 và các charset thường gặp

## Mục tiêu bài học

Sau bài này, bạn có thể giải thích khác biệt giữa **Unicode**, **code point**, **code unit**, **byte**, **glyph** và **grapheme cluster**; đọc được quy tắc mã hóa UTF-8/UTF-16; lựa chọn charset khi đọc/ghi văn bản Java; nhận biết BOM, endianness, văn bản lỗi mã hóa (*mojibake*), dữ liệu Unicode không hợp lệ và vấn đề chuẩn hóa; đồng thời sử dụng API Java 17 để xử lý văn bản an toàn.

**Điểm cốt lõi:** Unicode xác định các mã số và thuộc tính ký tự; **UTF-8/UTF-16 là các cách mã hóa** Unicode để lưu trữ và truyền dữ liệu. Không thể nhìn vài byte tùy ý rồi khẳng định đó là một chuỗi văn bản đúng nếu chưa xác định cách giải mã.

## 1. Những khái niệm phải tách bạch

| Thuật ngữ | Ý nghĩa | Ví dụ |
|---|---|---|
| **Character (ký tự)** | Khái niệm chữ/ký hiệu trong văn bản; ý nghĩa phụ thuộc ngữ cảnh | chữ `é`, chữ `A`, emoji |
| **Unicode code point** | Giá trị nguyên được ký hiệu `U+XXXX`, từ `U+0000` đến `U+10FFFF` | `U+0041` là `A`; `U+1F600` là 😀 |
| **Unicode scalar value** | Code point **trừ** vùng surrogate `U+D800..U+DFFF` | `U+1F600` hợp lệ; `U+D83D` tự nó không phải scalar value |
| **Code unit** | Đơn vị mà một encoding dùng để biểu diễn chuỗi | UTF-8: 8 bit; UTF-16: 16 bit; UTF-32: 32 bit |
| **Byte** | Đơn vị 8 bit trong dữ liệu tệp/mạng | `0xC3`, `0xA9` là hai byte UTF-8 của `é` |
| **Grapheme cluster** | Cụm ký tự người đọc thường xem là một ký tự hiển thị | `e` + dấu sắc kết hợp có thể hiển thị thành “é” |
| **Glyph** | Hình dạng vẽ ra bởi font/rendering engine | Một code point có thể có nhiều hình dạng; một glyph có thể đại diện nhiều code point |
| **Charset / character encoding** | Quy tắc chuyển giữa chuỗi ký tự và chuỗi byte | UTF-8, UTF-16LE, windows-1252, Shift_JIS |

**Một ký tự nhìn thấy được không nhất thiết bằng một code point, một `char`, hay một byte.** Ví dụ chữ “é” có thể được biểu diễn bằng `U+00E9` (ký tự dựng sẵn) hoặc bằng `U+0065 U+0301` (chữ e và dấu sắc kết hợp). Hai chuỗi có thể giống nhau khi hiển thị nhưng không bằng nhau khi so sánh code point.

Unicode có cả các code point chưa gán; “nằm trong khoảng `U+0000..U+10FFFF`” không tự bảo đảm đó là ký tự được gán hoặc hợp lệ để sử dụng ở mọi ngữ cảnh. **Surrogate code point** nằm trong `U+D800..U+DFFF` được dành riêng cho biểu diễn UTF-16, không được mã hóa riêng lẻ thành chuỗi UTF-8 hợp lệ.

### Mối quan hệ dữ liệu trong chương trình

```text
Văn bản ở mức khái niệm
  ↓ chọn chuỗi Unicode code point / scalar values
Chuỗi code point
  ↓ dùng charset/encoding cụ thể
Dãy code unit
  ↓ sắp xếp byte theo encoding và byte order (nếu có)
Dãy byte trên file/network
  ↓ dùng cùng encoding để giải mã
Chuỗi code point → text render với font, layout, normalization...
```

Đường đi từ text sang byte được gọi là **encoding**; chiều ngược lại là **decoding**. Một decoder phải biết charset (từ chuẩn giao thức, metadata, header, BOM hay cấu hình đã thỏa thuận). Suy đoán charset từ byte chỉ là heuristic, không thể chắc chắn với mọi đầu vào.

## 2. UTF-8: biến độ dài, tương thích ASCII

UTF-8 biểu diễn một Unicode scalar value bằng **1–4 byte**. Số byte phụ thuộc dải code point:

| Dải Unicode scalar value | Byte UTF-8 | Mẫu bit |
|---|---:|---|
| `U+0000..U+007F` | 1 | `0xxxxxxx` |
| `U+0080..U+07FF` | 2 | `110xxxxx 10xxxxxx` |
| `U+0800..U+FFFF` (trừ surrogate) | 3 | `1110xxxx 10xxxxxx 10xxxxxx` |
| `U+10000..U+10FFFF` | 4 | `11110xxx 10xxxxxx 10xxxxxx 10xxxxxx` |

Các bit `x` chứa giá trị code point. Byte tiếp diễn có tiền tố `10`. Decoder UTF-8 chuẩn phải **từ chối** chuỗi quá dài so với dạng ngắn nhất (*overlong encoding*), byte tiếp diễn không đúng, các giá trị surrogate và giá trị lớn hơn `U+10FFFF`.

Ví dụ cụ thể:

| Văn bản | Code point | Byte UTF-8 (hex) | Độ dài |
|---|---|---|---:|
| `A` | `U+0041` | `41` | 1 |
| `é` | `U+00E9` | `C3 A9` | 2 |
| `中` | `U+4E2D` | `E4 B8 AD` | 3 |
| 😀 | `U+1F600` | `F0 9F 98 80` | 4 |

ASCII chuẩn dùng byte `00..7F`, nên text chỉ chứa các ký tự ASCII có cùng byte ở UTF-8. Đây là lý do UTF-8 phổ biến trong giao thức, source code, JSON và hệ thống tệp hiện đại. **Nhưng** byte `0xE9` đơn lẻ (ký tự é trong ISO-8859-1) không phải một chuỗi UTF-8 hoàn chỉnh.

UTF-8 **không có byte order**; UTF-8 BOM tùy chọn là chuỗi `EF BB BF` biểu diễn `U+FEFF` ở đầu file. BOM UTF-8 đôi khi giúp công cụ cũ nhận dạng encoding, nhưng nhiều giao thức/công cụ không cần hoặc không mong đợi BOM. Không thêm BOM nếu định dạng không cho phép. Trên Java, khi decode `EF BB BF` bằng `StandardCharsets.UTF_8`, `U+FEFF` có thể tồn tại trong `String`; đừng mặc nhiên tin mọi API sẽ tự loại bỏ nó.

UTF-8 là mã hóa Unicode, **không phải mã hóa luôn ra một byte cho mỗi chữ**. Nếu ghi chiều dài payload cho HTTP/socket/file nhị phân thì phải đếm **byte sau encoding**, không dùng `String.length()`.

## 3. UTF-16: code unit 16 bit, surrogate pair

UTF-16 dùng các **code unit 16 bit**, mỗi đơn vị tương ứng **2 byte**.

- Với Unicode scalar value từ `U+0000` đến `U+FFFF` ngoài vùng surrogate (BMP), UTF-16 thường dùng **một code unit**.
- Với giá trị từ `U+10000` đến `U+10FFFF`, UTF-16 dùng **surrogate pair**, gồm *high surrogate* (`D800..DBFF`) và *low surrogate* (`DC00..DFFF`), tổng cộng **hai code unit = 4 byte**.
- Một high surrogate không đi kèm low surrogate đúng cách, hoặc low surrogate đứng đơn độc, là **chuỗi UTF-16 sai cấu trúc** khi được diễn giải như văn bản Unicode.

Ví dụ 😀 = `U+1F600` trở thành cặp `D83D DE00` trong UTF-16. Chuyển code point `U+10000` trở lên thành surrogate pair có thể hiểu bằng cách:

1. Lấy `U = codePoint - 0x10000`.
2. High surrogate = `0xD800 + (U >> 10)`.
3. Low surrogate = `0xDC00 + (U & 0x3FF)`.

Ví dụ này giải thích vì sao một emoji có thể chiếm **hai vị trí `char` trong Java**, dù là một code point Unicode.

### Endianness và BOM của UTF-16

| Encoding | Byte của `A` (`U+0041`) | Byte của 😀 (`U+1F600`) |
|---|---|---|
| `UTF-16BE` | `00 41` | `D8 3D DE 00` |
| `UTF-16LE` | `41 00` | `3D D8 00 DE` |
| `UTF-8` | `41` | `F0 9F 98 80` |

**BE** (*big-endian*) lưu byte cao trước; **LE** (*little-endian*) lưu byte thấp trước. `UTF-16` không chỉ rõ BE/LE ở tên có thể dùng BOM `FE FF` (BE) hoặc `FF FE` (LE) để nhận diện; khi không có BOM, chính sách đọc phải do chuẩn giao thức/API quy định, không đoán theo hệ điều hành. Với charset chuẩn Java, `StandardCharsets.UTF_16` nhận diện BOM và dùng big-endian làm mặc định khi không có BOM; encoder tương ứng có thể phát BOM. `UTF_16BE`/`UTF_16LE` chọn byte order tường minh và không tự phát BOM chỉ vì encoding được chọn.

Không nhầm UTF-16 với “16 bit cho mọi ký tự”: emoji ngoài BMP cần 4 byte. Và **code unit UTF-16 không phải code point Unicode**.

## 4. UTF-32, ASCII và các charset không phải Unicode

UTF-32 dùng **một code unit 32 bit cho mỗi Unicode scalar value**, với byte order BE/LE. Nó dễ định vị code point theo đơn vị cố định, nhưng thường tốn 4 byte/ký tự và không phổ biến như UTF-8 cho dữ liệu trao đổi. UTF-32 vẫn không bảo đảm một code point tương ứng một grapheme cluster.

Các tên thường gặp:

| Charset | Đặc điểm | Chú ý sử dụng |
|---|---|---|
| **US-ASCII** | 7 bit, byte `00..7F` | Không biểu diễn tiếng Việt có dấu, emoji |
| **ISO-8859-1 (Latin-1)** | Một byte cho `U+0000..U+00FF` | Nhiều byte có ý nghĩa khác windows-1252 |
| **windows-1252** | Mã hóa một byte kiểu Tây Âu | Các byte `0x80..0x9F` khác Latin-1; thường gặp trong dữ liệu Windows cũ |
| **UTF-8** | Unicode 1–4 byte, tương thích ASCII | Ưu tiên cho trao đổi dữ liệu mới |
| **UTF-16/UTF-16LE/UTF-16BE** | Unicode, code unit 16 bit | Phải rõ byte order/BOM |
| **UTF-32LE/UTF-32BE** | Unicode, code unit 32 bit | Không nằm trong sáu charset bắt buộc của `StandardCharsets` |
| **Shift_JIS**, **EUC-JP** | Charset tiếng Nhật đời trước | Cần biết đúng biến thể/nguồn dữ liệu |
| **GB18030**, **GBK**, **Big5** | Charset/biến thể dùng trong dữ liệu tiếng Trung | Không đồng nghĩa với UTF-8 |
| **EUC-KR**, **windows-949** | Charset tiếng Hàn đời trước | Không giả định chúng tương đương |

Một số tên lịch sử không bao phủ toàn bộ Unicode và không biểu diễn mọi ký tự. Hai charset “trông giống” nhau vẫn có thể gán byte thành ký tự khác, gây lỗi khó phát hiện. Chẳng hạn byte `0x80` trong windows-1252 thường biểu diễn `€`, còn trong ISO-8859-1 là ký tự điều khiển `U+0080`. Đây là một nguyên nhân phổ biến khiến văn bản hiển thị thành ký tự lạ.

Các alias như `UTF8`, `utf-8`, `ISO8859_1` có thể được Java nhận diện qua registry charset; khi phát triển nên dùng hằng số chuẩn hoặc tên chính thức, tránh tự suy luận biến thể. Mức hỗ trợ một số charset lịch sử phụ thuộc JDK/provider. **Sáu charset luôn có trong Java SE `StandardCharsets`**: `US_ASCII`, `ISO_8859_1`, `UTF_8`, `UTF_16`, `UTF_16BE`, `UTF_16LE`.

### Unicode khác với UTF-8/UTF-16

| Câu nói | Đánh giá |
|---|---|
| “Unicode là UTF-8” | Sai: Unicode là hệ thống mã ký tự; UTF-8 là một encoding của Unicode |
| “UTF-16 luôn dùng 2 byte cho một ký tự” | Sai: code point ngoài BMP cần 4 byte; grapheme có thể có nhiều code point |
| “UTF-8 và UTF-16 chứa các ký tự khác nhau” | Sai với chuỗi scalar Unicode hợp lệ: cùng tập giá trị có thể biểu diễn; cách lưu byte khác |
| “ASCII là một tập con tương thích byte của UTF-8” | Đúng đối với dữ liệu ASCII chuẩn `00..7F` |
| “Cùng byte phải decode ra cùng text” | Sai nếu dùng các charset khác nhau |
| “BOM là bắt buộc trong UTF-8” | Sai |
| “Độ dài Java String bằng số ký tự nhìn thấy” | Sai |

## 5. Unicode trong Java: `String`, `char`, code point

Java `String` thể hiện chuỗi theo **đơn vị UTF-16** ở tầng API. `char` là giá trị **16 bit**, có thể là một code unit của surrogate pair, không tự bảo đảm tương ứng một code point hoàn chỉnh. Các phương thức quan trọng:

| API Java | Đếm/truy cập gì? |
|---|---|
| `String.length()` | Số **UTF-16 code unit** |
| `String.charAt(index)` | Một `char` (code unit) tại chỉ số |
| `String.codePointAt(index)` | Một code point tại vị trí code unit được chỉ định; ghép surrogate pair hợp lệ |
| `String.codePointCount(begin, end)` | Số code point trong khoảng chỉ số UTF-16 |
| `String.codePoints()` | `IntStream` code point (surrogate không ghép được vẫn có thể xuất hiện như giá trị đơn lẻ) |
| `Character.toChars(codePoint)` | Các `char` UTF-16 để biểu diễn code point |
| `String.getBytes(charset)` | Encode `String` thành byte theo charset |
| `new String(bytes, charset)` | Decode byte theo charset |

### Ví dụ 1 — số `char`, code point và byte hoàn toàn khác nhau

Lưu tên tệp **`UnicodeBasicsDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.stream.Collectors;

public class UnicodeBasicsDemo {
    public static void main(String[] args) {
        String text = "Aé😀";

        System.out.println("UTF-16 units: " + text.length());
        System.out.println("Code points: " +
            text.codePointCount(0, text.length()));
        System.out.println("Code points: " +
            text.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(", ")));

        System.out.println("UTF-8: " +
            HexFormat.of().formatHex(
                text.getBytes(StandardCharsets.UTF_8)));
        System.out.println("UTF-16BE: " +
            HexFormat.of().formatHex(
                text.getBytes(StandardCharsets.UTF_16BE)));
        System.out.println("UTF-16LE: " +
            HexFormat.of().formatHex(
                text.getBytes(StandardCharsets.UTF_16LE)));
    }
}
```

Chạy Java 17+:

```bash
javac UnicodeBasicsDemo.java
java UnicodeBasicsDemo
```

Stdout chính xác:

```text
UTF-16 units: 4
Code points: 3
Code points: U+0041, U+00E9, U+1F600
UTF-8: 41c3a9f09f9880
UTF-16BE: 004100e9d83dde00
UTF-16LE: 4100e9003dd800de
```

Phân tích: `A` = một `char`; `é` = một `char`; 😀 = **hai** `char` (high/low surrogate), nên `length() == 4`, nhưng chỉ có ba code point. UTF-8 cần 1 + 2 + 4 = **7 byte**. `HexFormat` được bổ sung ở Java 17. Đừng dùng `charAt`/substring tùy tiện để cắt giữa surrogate pair.

> Nếu bài toán cần đếm **ký tự người dùng nhìn thấy**, code point count cũng chưa đủ: chuỗi `e\u0301`, một số emoji có tone modifier, cờ và emoji ZWJ có thể là một grapheme cluster gồm nhiều code point. Có thể sử dụng các thuật toán phân đoạn văn bản theo Unicode được thư viện hỗ trợ, ví dụ ICU4J cho yêu cầu grapheme đầy đủ; không tự mặc định rằng duyệt từng code point sẽ duyệt từng ký tự hiển thị.

## 6. Encoding và decoding trong Java 17

### Chọn charset tường minh khi chuyển byte ↔ text

Ví dụ các thao tác thường gặp:

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String decoded = new String(bytes, StandardCharsets.UTF_8);
```

Đây là **đoạn minh họa** với `text` kiểu `String`; cần import `java.nio.charset.StandardCharsets`. Nếu chuỗi là Unicode hợp lệ và cùng charset hai chiều, kết quả thường khớp. Nếu `String` chứa surrogate đơn lẻ không hợp lệ hoặc charset không biểu diễn được ký tự, cách xử lý lỗi của encoder/decoder có thể làm mất thông tin.

Đối với file văn bản, `Path`/`Files` (NIO.2) thường là điểm bắt đầu phù hợp:

```java
Files.writeString(path, content, StandardCharsets.UTF_8);
String result = Files.readString(path, StandardCharsets.UTF_8);
```

Đây là **đoạn minh họa**: `path` là `Path`, `content` là `String`, phương thức gọi xử lý `IOException`. File lớn nên đọc/ghi dần bằng `Files.newBufferedReader` và `Files.newBufferedWriter`, hoặc `InputStreamReader`/`OutputStreamWriter` chỉ rõ charset:

```java
try (var reader =
         Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý một dòng; readLine() loại bỏ ký tự kết thúc dòng
    }
}
```

`reader` đóng tự động. `readLine()` trả dữ liệu không gồm `\\r`, `\\n` hay `\\r\\n`; nếu muốn giữ nguyên byte, line ending hay BOM, thao tác trên byte gốc trước khi thay đổi.

### Charset mặc định có thể là nguồn lỗi

Những API cho phép bỏ charset có thể phụ thuộc version/runtime:

- Trên **Java 17**, `Charset.defaultCharset()` tùy môi trường; có thể khác giữa máy phát triển, máy CI hoặc hệ điều hành.
- Từ **Java 18**, JEP 400 quy định UTF-8 làm charset mặc định trong các trường hợp tiêu chuẩn, kèm những chế độ tương thích; không nên xây dựng giao thức dựa trên ngầm định runtime.
- Khi có chuẩn dữ liệu như HTTP response, CSV trao đổi, file cấu hình hoặc message protocol, **quy định và truyền charset cụ thể**.

`new String(bytes)` hay `string.getBytes()` không đối số charset dễ dẫn đến tác vụ chạy đúng tại máy A nhưng sai trên máy B. Dùng `StandardCharsets.UTF_8` nếu đặc tả cho phép UTF-8.

## 7. Khi byte lỗi hoặc chọn sai charset

### Phân biệt hai trường hợp

1. **Byte hợp lệ theo charset X nhưng lại decode theo Y** → thường thành chữ lạ (*mojibake*). Ví dụ UTF-8 của `é` (`C3 A9`) nếu diễn giải dưới ISO-8859-1 sẽ thành `Ã©`.
2. **Byte không hợp lệ theo charset đang decode** → decoder có thể báo lỗi hoặc thay bằng `U+FFFD` (replacement character `�`). Ví dụ byte đơn `E9` từ Latin-1 không phải chuỗi UTF-8 hợp lệ.

Đừng dùng cách đổi qua lại charset nhiều lần để “sửa” văn bản nếu chưa biết **byte gốc** và các bước encode/decode đã thực hiện; thao tác thay thế `�` có thể đã làm mất thông tin.

### Ví dụ 2 — decode nghiêm ngặt và thay thế khi gặp lỗi

Lưu tên tệp **`CharsetErrorDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class CharsetErrorDemo {
    public static void main(String[] args) {
        // "café" mã hóa ISO-8859-1
        byte[] latin1 = {
            (byte) 0x63, (byte) 0x61,
            (byte) 0x66, (byte) 0xE9
        };

        System.out.println("Latin-1: " +
            new String(latin1, StandardCharsets.ISO_8859_1));
        System.out.println("UTF-8 replacement: " +
            new String(latin1, StandardCharsets.UTF_8));

        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(latin1));
        } catch (CharacterCodingException ex) {
            System.out.println("Strict UTF-8: " +
                ex.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac CharsetErrorDemo.java
java CharsetErrorDemo
```

Stdout:

```text
Latin-1: café
UTF-8 replacement: caf�
Strict UTF-8: MalformedInputException
```

`new String(bytes, UTF_8)` dùng hành vi thay thế byte UTF-8 lỗi bằng replacement character; còn `CharsetDecoder` với `CodingErrorAction.REPORT` làm lỗi hiển lộ. Trong hệ thống nhập dữ liệu cần tính chính xác, đặc biệt ID, log kiểm toán hoặc dữ liệu đã ký số, decoder nghiêm ngặt thường thích hợp hơn việc âm thầm thay ký tự.

Các `CodingErrorAction`:
- `REPORT`: trả lỗi; với `decode(ByteBuffer)`, thường ném `MalformedInputException` hoặc `UnmappableCharacterException`.
- `REPLACE`: chèn chuỗi/ký tự thay thế khi có lỗi.
- `IGNORE`: bỏ dữ liệu sai, có thể làm mất thông tin và thường không phù hợp để lưu trữ dữ liệu quan trọng.

**Malformed input** là chuỗi byte/code unit vi phạm encoding; **unmappable character** là ký tự hợp lệ nhưng charset đích không có cách biểu diễn. Ví dụ encode `"Tiếng Việt"` theo Latin-1 có nhiều ký tự không mã hóa được: encoder có thể thay thế, bỏ, hoặc báo lỗi tùy cấu hình. Đừng coi `getBytes(ISO_8859_1)` là phép chuyển không mất dữ liệu cho tiếng Việt.

### Trường hợp UTF-8 đến theo nhiều chunk

Một ký tự UTF-8 có thể trải qua ranh giới đọc từ socket hoặc file. Ví dụ 😀 có `F0 9F 98 80`: lần đọc đầu chỉ có `F0 9F`, lần sau mới có `98 80`. Gọi `new String(chunk, UTF_8)` riêng trên mỗi chunk có thể tạo replacement characters.

Với dữ liệu nhiều chunk, hãy giữ byte chưa xử lý qua `ByteBuffer.compact()` hoặc cấu trúc lưu đệm tương ứng, và dùng **`CharsetDecoder` có trạng thái** qua nhiều lần `decode(input, output, endOfInput)` rồi `flush(output)` khi đến EOF. Kiểm tra `CoderResult.UNDERFLOW`, `OVERFLOW`, lỗi và dung lượng `CharBuffer`. Tránh lấy ranh giới TCP `read()` làm ranh giới text/message. Một cách đơn giản hơn khi đầu vào là stream byte tuần tự: dùng `InputStreamReader` bọc `InputStream`, đã được thiết kế để decode dữ liệu đến dần.

## 8. Unicode normalization: cùng cách nhìn, khác byte

Một chuỗi có thể biểu diễn cùng nội dung văn bản theo nhiều chuỗi code point. Ví dụ:

- `café` với `é = U+00E9` (composed)
- `cafe\u0301` với `e = U+0065`, dấu sắc kết hợp `U+0301` (decomposed)

Trong Java, `String.equals` so sánh nội dung UTF-16 thực tế, không tự chuẩn hóa Unicode.

**Normalization forms** (Unicode Standard Annex #15):

| Dạng | Mô tả | Ví dụ dùng |
|---|---|---|
| **NFC** | Canonical decomposition rồi composition | Đồng nhất những biểu diễn canonically equivalent |
| **NFD** | Canonical decomposition | Một số quy trình xử lý phân tách dấu |
| **NFKC** | Compatibility decomposition rồi composition | Một số bài toán tìm kiếm/chuẩn hóa tương thích, cần cân nhắc thay đổi ý nghĩa trình bày |
| **NFKD** | Compatibility decomposition | Phân tích dạng ký tự có biến thể tương thích |

Các dạng `NFKC`/`NFKD` có thể làm mất khác biệt trình bày mang ý nghĩa (ví dụ một số ký tự tương thích). Không dùng bừa chúng cho văn bản nguồn, chữ ký số hoặc định danh có chuẩn riêng.

### Ví dụ 3 — NFC và so sánh chuỗi

Lưu tên tệp **`NormalizationDemo.java`**:

```java
import java.text.Normalizer;

public class NormalizationDemo {
    public static void main(String[] args) {
        String composed = "café";
        String decomposed = "cafe\u0301";

        System.out.println("Raw equals: " +
            composed.equals(decomposed));
        System.out.println("NFC equals: " +
            composed.equals(
                Normalizer.normalize(
                    decomposed, Normalizer.Form.NFC
                )));
        System.out.println("Raw code points: " +
            decomposed.codePointCount(0, decomposed.length()));
    }
}
```

Chạy:

```bash
javac NormalizationDemo.java
java NormalizationDemo
```

Stdout:

```text
Raw equals: false
NFC equals: true
Raw code points: 5
```

Chuỗi `cafe\u0301` có `c`, `a`, `f`, `e`, dấu sắc = **5 code point**; nó thường hiển thị giống “café” được dựng sẵn với 4 code point. Việc so sánh, tìm kiếm, băm hoặc tạo khóa Unicode có thể cần quy tắc normalization **được thống nhất tại ranh giới dữ liệu**. Không chuẩn hóa một phía trước khi kiểm tra chữ ký/HMAC trên byte gốc.

**Normalization khác case folding:** `toLowerCase()` không giải quyết mọi so sánh Unicode không phân biệt hoa/thường. Nếu cần xử lý tìm kiếm/ngôn ngữ, phải xác định locale và semantics rõ ràng; `String.toLowerCase(Locale.ROOT)` có thể phù hợp cho một số định danh phi ngôn ngữ nhưng không phải giải pháp tổng quát cho mọi quy tắc Unicode.

## 9. Quy trình đọc/ghi đúng với tệp và API

### Đọc từ byte vào Java text

```text
Nguồn byte → xác minh encoding bằng hợp đồng giao thức/metadata
          → nhận diện BOM khi đặc tả có quy định
          → CharsetDecoder (REPORT hoặc chính sách lỗi rõ ràng)
          → String / Reader theo code unit UTF-16 của Java
          → xử lý code point / grapheme theo yêu cầu nghiệp vụ
```

Không “đọc text” bằng cách cast từng byte sang `char`. Khi byte chứa UTF-8, một code point có thể chiếm bốn byte; khi một file UTF-16LE được decode bằng UTF-8, ngay cả byte `00` xen kẽ cũng có thể làm dữ liệu hiển thị sai.

### Ghi Java text ra byte

```text
String → lựa chọn normalization nếu giao thức đòi hỏi
       → CharsetEncoder với chính sách lỗi minh bạch
       → byte[]/OutputStream/Files
       → BOM/metadata/media type nhất quán với charset
```

Nếu chọn UTF-8, một số API như `Files.writeString(path, text, UTF_8)` đã đủ. Với dữ liệu nhị phân có trường “length”, phải tính `text.getBytes(UTF_8).length`. Nếu dùng `Writer`, nó encode ký tự sang byte; `BufferedWriter` chỉ là buffer, không tự chọn charset chuẩn cho ứng dụng.

### BOM, newline và dấu hiệu file

Ba khái niệm độc lập:

- **BOM**: một số byte đầu thể hiện byte order hoặc signature encoding, ví dụ `EF BB BF` trong UTF-8, `FE FF` trong UTF-16BE, `FF FE` trong UTF-16LE.
- **Newline**: `LF`, `CRLF` là nội dung văn bản; charset quyết định byte tương ứng, nhưng không tự quy định hệ thống phải dùng loại xuống dòng nào.
- **Binary file marker / magic bytes**: có thể giúp nhận diện định dạng file, không nhất thiết là một charset.

Một file không có BOM **không đồng nghĩa ASCII hoặc UTF-8**. Một file chứa BOM cũng phải được kiểm tra cùng hợp đồng định dạng và API decode cụ thể, vì không phải mọi decoder đều tự bỏ BOM.

## 10. Tình huống thực tế: khi nào chọn cách nào?

| Tình huống | Lựa chọn phù hợp | Lý do |
|---|---|---|
| File cấu hình/JSON/log mới | UTF-8 theo đặc tả, thường không BOM | Phổ biến, tương thích ASCII, lưu được Unicode |
| CSV trao đổi giữa nhiều hệ thống | Thống nhất UTF-8, ghi rõ charset trong hợp đồng; quy tắc BOM theo nơi tiêu thụ | CSV không tự định danh encoding |
| File UTF-16 của hệ thống legacy | Dùng đúng UTF-16LE/BE hoặc UTF-16 theo BOM/đặc tả | Không decode nhầm sang UTF-8 |
| File Latin-1 / windows-1252 cũ | Chỉ rõ charset thực tế, xử lý và chuyển đổi có kiểm chứng | Tránh mojibake ở các byte ngoài ASCII |
| Socket TCP nhận UTF-8 dần | `InputStreamReader` hoặc decoder streaming với framing rõ | Không phân tách ký tự theo mỗi lần đọc byte |
| Định danh/so sánh Unicode | Đặc tả normalization + case/locale + chính sách ký tự | Tránh hai dạng chuỗi trông giống nhau nhưng khác byte |
| Ký số, hash nội dung text | Chốt byte encoding và normalization trước khi ký | Hash/HMAC làm việc với byte, khác byte thì khác chữ ký |
| Cắt/giới hạn text trong UI | Cân nhắc grapheme cluster thay vì `String.length()` | Tránh cắt surrogate, dấu kết hợp hoặc emoji nối |

**Thực hành cho dữ liệu Việt:** UTF-8 biểu diễn đầy đủ chữ có dấu, kể cả dạng combining marks. ISO-8859-1 và US-ASCII không biểu diễn đầy đủ tiếng Việt; không nên chuyển dữ liệu Việt vào chúng theo cách thay thế mặc định rồi mong khôi phục nguyên trạng.

## 11. Những lỗi hay gặp và cách kiểm tra

1. **Khai báo UTF-8 nhưng byte không phải UTF-8.** So sánh bytes hex đầu vào với metadata thực tế. Nội dung chỉ có ASCII rất khó giúp phát hiện mismatch; dùng test có chữ `Tiếng Việt`, `é`, `中`, emoji.
2. **`String.length()` bị dùng làm độ dài payload byte.** Dùng `text.getBytes(UTF_8).length` hoặc đếm byte ngay từ quá trình ghi.
3. **Cắt nửa surrogate pair.** Khi cần xử lý code point, dùng `codePoints()`, `offsetByCodePoints()`, `Character.charCount()` phù hợp. Khi cần xử lý ký tự hiển thị, dùng grapheme segmentation.
4. **Một lần socket `read` rồi decode ngay cả chunk.** Byte của ký tự nhiều byte có thể bị chia; giữ decoder state và xử lý framing.
5. **Giải mã sai encoding rồi encode lại thêm lần nữa.** Chuỗi mojibake có thể được lưu vĩnh viễn; giữ bản byte gốc để chẩn đoán, không biến đổi thử trên dữ liệu sản xuất.
6. **Bỏ qua BOM.** Byte mở đầu có thể làm xuất hiện `U+FEFF`, làm sai header đầu tiên của CSV, khóa cấu hình hoặc chuỗi so sánh. Chỉ loại BOM khi format cho phép, kiểm tra đúng byte/ký tự đầu.
7. **Tự cho rằng mọi charset là Unicode.** Charset legacy có thể không biểu diễn ký tự; dùng encoder `REPORT` nếu tuyệt đối không được mất thông tin.
8. **So sánh chuỗi không xét normalization.** Hai chuỗi giống về hình thức có thể khác code point; áp dụng NFC/NFD theo hợp đồng trước khi tạo khóa hoặc tìm kiếm nếu phù hợp.
9. **Đánh đồng normalization với bảo mật.** Ký tự dễ nhầm (*confusable*), ký tự điều khiển định hướng hai chiều (bidi), zero-width và mixed scripts có thể gây hiểu sai; chính sách định danh cần kiểm tra riêng. Không nghĩ NFC tự loại các nguy cơ đó.
10. **Phụ thuộc charset mặc định của runtime.** Dùng charset rõ ràng khi ghi/đọc dữ liệu được trao đổi giữa các máy.
11. **Nhầm Java `char` với Unicode scalar value.** Một `char` có thể là surrogate. Một code point có thể không tương ứng một grapheme, và nhiều code point có thể kết hợp thành một glyph.
12. **Không phân biệt data encoding và escaping.** JSON `"\u00E9"` là biểu diễn *escape* trong cú pháp JSON; các ký tự byte của file JSON vẫn có encoding riêng (thường UTF-8). URL percent-encoding, HTML entities, Base64 cũng không phải charset thay thế UTF-8.

### Cách chẩn đoán nhanh dữ liệu lỗi

1. Tìm **byte gốc** và nguồn tạo, lưu nguyên mẫu để tái hiện.
2. Xem thông số giao thức/file thực tế: charset, BOM, byte order, media type, cấu hình kết nối.
3. In byte hex có giới hạn trong môi trường thử nghiệm để so `C3 A9` (UTF-8 của é) với `E9` (Latin-1 của é).
4. Decode nghiêm ngặt bằng charset được chỉ định; báo lỗi khi dữ liệu vi phạm. Nếu decode được nhưng chữ lạ, kiểm chứng charset nguồn có đúng không.
5. Kiểm tra normalization khi text nhìn giống mà so sánh thất bại; phân biệt lỗi hiển thị font/glyph với lỗi encoding bytes.
6. Cố định regression tests cho chữ tiếng Việt, emoji, dấu kết hợp, byte lỗi, BOM và ranh giới buffer.

## 12. Tự kiểm tra

**Câu 1.** `String s = "A😀";` có `s.length()` bằng mấy, `codePointCount(0, s.length())` bằng mấy, UTF-8 dài bao nhiêu byte?

**Đáp án:** `length() == 3` vì `A` dùng một `char`, 😀 dùng surrogate pair hai `char`; có **2 code point**; UTF-8 có **5 byte** (`41 F0 9F 98 80`).

**Câu 2.** Tại sao dãy byte `C3 A9` có thể thành `Ã©`?  
**Đáp án:** đây là byte UTF-8 của `é`, nhưng nếu giải mã từng byte theo ISO-8859-1 sẽ nhận `U+00C3` (Ã) và `U+00A9` (©).

**Câu 3.** UTF-16LE mã hóa 😀 thành byte nào? UTF-16BE thì sao?  
**Đáp án:** cặp surrogate `D83D DE00`; UTF-16LE là `3D D8 00 DE`, UTF-16BE là `D8 3D DE 00`.

**Câu 4.** Vì sao `String.equals` có thể báo false với hai chữ “é” trông giống nhau?  
**Đáp án:** một chuỗi dùng `U+00E9`; chuỗi còn lại dùng `U+0065 U+0301`. Chúng khác code point/code unit; có thể cần NFC theo hợp đồng dữ liệu.

**Câu 5.** Muốn không âm thầm mất byte khi nhận file UTF-8 lỗi thì dùng API nào?  
**Đáp án:** `CharsetDecoder` của `StandardCharsets.UTF_8` kết hợp `CodingErrorAction.REPORT`; xử lý `CharacterCodingException`.

**Câu 6.** Có thể giả định một lần `InputStream.read(byte[])` nhận đủ một ký tự UTF-8 hay một message TCP không?  
**Đáp án:** Không. UTF-8 có thể bị chia giữa các lần đọc và TCP không bảo đảm ranh giới message. Cần reader/decoder streaming và giao thức framing.

**Ghi nhớ:** Unicode mô tả code point; UTF-8/UTF-16/UTF-32 quy định cách biểu diễn các giá trị đó trong code unit/byte. Trong Java 17, `String` được đánh chỉ số theo code unit UTF-16; dùng charset tường minh khi chuyển `String ↔ byte[]`, kiểm soát lỗi decoding, BOM, normalization và ranh giới dữ liệu để tránh văn bản sai hoặc mất thông tin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-003">Charset, StandardCharsets và tên charset</a>

<details>
<summary>Xem chi tiết</summary>

# Charset, StandardCharsets và tên charset trong Java I/O

## 1. Charset là gì và giải quyết vấn đề nào?

File và luồng I/O lưu, truyền **byte**; còn `String` trong Java biểu diễn văn bản Unicode. Để đổi giữa hai dạng phải có một quy tắc mã hóa ký tự, gọi là **character encoding** hoặc **charset**.

- **Encode (mã hóa):** ký tự Unicode → dãy byte theo charset được chọn.
- **Decode (giải mã):** dãy byte → ký tự Unicode bằng chính quy tắc tương ứng.

Ví dụ, ký tự `é` (U+00E9) được biểu diễn:

| Charset | Byte hex của `é` |
| --- | --- |
| UTF-8 | `C3 A9` |
| ISO-8859-1 | `E9` |
| UTF-16BE | `00 E9` |
| UTF-16LE | `E9 00` |

Cùng một ký tự có các byte khác nhau giữa các charset. Ngược lại, cùng một dãy byte có thể được diễn giải ra ký tự khác nhau, hoặc trở thành dữ liệu không hợp lệ, khi chọn sai charset.

Ví dụ dãy `41 C3 A9` là chuỗi `Aé` khi giải mã bằng UTF-8, nhưng thành `AÃ©` theo ISO-8859-1. Đó là lỗi chữ bị biến dạng (*mojibake*), thường gặp khi ứng dụng gửi UTF-8 nhưng phía nhận lại giải mã theo charset khác.

**Charset không phải** thuật toán nén, mã hóa bảo mật hay định dạng tệp. Nó chỉ xác định cách ánh xạ giữa văn bản và byte. Cũng cần phân biệt charset với dấu tiếng Việt/Unicode normalization: hai chuỗi hiển thị giống nhau có thể dùng các chuỗi code point khác nhau; việc mã hóa không tự chuẩn hóa chúng.

## 2. `java.nio.charset.Charset`: đối tượng mô tả một bộ mã

`Charset` là lớp trong gói `java.nio.charset`, đại diện cho một cách chuyển đổi giữa chuỗi ký tự và byte. Trong Java, một đối tượng `Charset` thường được truyền trực tiếp cho API I/O thay vì tên dạng chuỗi.

Những phương thức hữu ích:

| API | Tác dụng |
| --- | --- |
| `Charset.forName("UTF-8")` | Tìm charset theo tên chuẩn hoặc tên khác được runtime nhận biết; ném ngoại lệ nếu tên không hợp lệ/không được hỗ trợ |
| `Charset.isSupported("UTF-8")` | Kiểm tra một tên hợp lệ có được hỗ trợ không |
| `charset.name()` | Lấy **tên chuẩn (canonical name)** của charset |
| `charset.aliases()` | Lấy các tên khác (*aliases*) mà provider công bố |
| `Charset.availableCharsets()` | Lấy danh sách charset khả dụng trên runtime dưới dạng map tên chuẩn → đối tượng |
| `Charset.defaultCharset()` | Xem charset mặc định của runtime |
| `charset.newEncoder()` và `charset.newDecoder()` | Tạo bộ chuyển đổi để kiểm soát cách xử lý ký tự/byte lỗi |

Không cần gọi `new Charset(...)`; đây là lớp trừu tượng, đối tượng thực tế do thư viện Java và các provider cung cấp.

### Tên chuẩn, tên khác và việc so sánh

Tên charset có dạng chuỗi, ví dụ `UTF-8`, `US-ASCII`, `ISO-8859-1`. Tên chuẩn là tên `charset.name()` trả về. Tên khác (*alias*) có thể dùng để tra cứu cùng charset; ví dụ nhiều JDK chấp nhận `UTF8` cho `UTF-8`, nhưng **tập alias ngoài tên chuẩn có thể phụ thuộc provider/runtime**.

Tra cứu tên charset **không phân biệt chữ hoa/chữ thường**, vì vậy `Charset.forName("uTf-8")` và `Charset.forName("UTF-8")` trỏ tới cùng charset về mặt ngữ nghĩa. Dùng `charset.equals(other)` để so sánh các đối tượng charset, hoặc chuẩn hóa qua `charset.name()` khi cần lưu tên; không dựa vào `==` hay so chuỗi alias tùy ý.

Các tên truyền vào phải đúng cú pháp mà `Charset` cho phép; ký tự không hợp lệ có thể làm `isSupported` ném `IllegalCharsetNameException` thay vì trả về `false`.

## 3. `StandardCharsets`: dùng hằng thay cho tên chuỗi

`java.nio.charset.StandardCharsets` cung cấp sẵn các đối tượng `Charset` cho sáu bộ mã được **mọi triển khai Java SE bảo đảm hỗ trợ**:

| Hằng | Tên chuẩn | Điểm cần biết |
| --- | --- | --- |
| `StandardCharsets.UTF_8` | `UTF-8` | Unicode, mã hóa biến độ dài 1–4 byte/code point; lựa chọn mặc định cho giao tiếp/tệp văn bản mới nếu không có đặc tả khác |
| `StandardCharsets.US_ASCII` | `US-ASCII` | 7 bit; chỉ biểu diễn trực tiếp U+0000 đến U+007F |
| `StandardCharsets.ISO_8859_1` | `ISO-8859-1` | Một byte cho mỗi ký tự U+0000 đến U+00FF; không chứa phần lớn ký tự tiếng Việt |
| `StandardCharsets.UTF_16` | `UTF-16` | Unicode UTF-16, khi encode thường ghi BOM để chỉ byte order; decoder có thể nhận BOM |
| `StandardCharsets.UTF_16BE` | `UTF-16BE` | UTF-16 thứ tự byte lớn trước; không tự thêm BOM |
| `StandardCharsets.UTF_16LE` | `UTF-16LE` | UTF-16 thứ tự byte nhỏ trước; không tự thêm BOM |

**Tên hằng Java** như `UTF_8` dùng dấu gạch dưới do quy tắc identifier; **tên charset chuẩn** như `UTF-8` dùng dấu gạch ngang. Không truyền chuỗi `"UTF_8"` và mặc định nó đồng nghĩa với `"UTF-8"`; hãy sử dụng hằng hoặc tên chuẩn chính xác.

Ưu tiên:

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String decoded = new String(bytes, StandardCharsets.UTF_8);
```

Thay vì tự tra cứu mỗi lần bằng chuỗi:

```java
Charset charset = Charset.forName("UTF-8");
```

Hai đoạn trên **là đoạn mã minh họa**: `text` và `bytes` phải được khai báo trong ngữ cảnh thích hợp. Khi charset cố định tại thời điểm viết chương trình, hằng `StandardCharsets` giúp tránh sai chính tả, tránh xử lý ngoại lệ tên không hỗ trợ và nói rõ ý định ngay trong mã nguồn.

Nếu tên charset đến từ cấu hình bên ngoài, chuỗi HTTP hoặc tệp metadata, `Charset.forName(name)` vẫn hữu ích; chỉ cần kiểm tra tên và xử lý lỗi. `StandardCharsets` không chứa toàn bộ các charset mà một JDK có thể hỗ trợ.

## 4. Ví dụ Java 17+: tra cứu tên, encode và decode

Tạo tệp **`CharsetNamesDemo.java`**:

```java
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class CharsetNamesDemo {
    public static void main(String[] args) {
        Charset utf8 = StandardCharsets.UTF_8;
        Charset fromName = Charset.forName("uTf-8");

        String original = "Aé";
        byte[] bytes = original.getBytes(utf8);

        System.out.println("Tên chuẩn: " + utf8.name());
        System.out.println("Cùng charset: " + utf8.equals(fromName));
        System.out.println("UTF-8 hex: " + HexFormat.of().formatHex(bytes));
        System.out.println("Giải mã UTF-8: " + new String(bytes, utf8));
        System.out.println("Giải mã Latin-1: "
                + new String(bytes, StandardCharsets.ISO_8859_1));
        System.out.println("Hỗ trợ UTF-8: " + Charset.isSupported("UTF-8"));
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 CharsetNamesDemo.java
java CharsetNamesDemo
```

Kết quả logic/Unicode của chương trình:

```text
Tên chuẩn: UTF-8
Cùng charset: true
UTF-8 hex: 41c3a9
Giải mã UTF-8: Aé
Giải mã Latin-1: AÃ©
Hỗ trợ UTF-8: true
```

Giải thích:

1. `Charset.forName("uTf-8")` có thể tra cứu UTF-8 bất kể chữ hoa/thường, nên `equals` trả `true`.
2. Chữ `A` được mã hóa thành `41`; `é` thành `C3 A9`. `HexFormat` có trong Java 17, mặc định in chữ hex thường.
3. Giải mã đúng cùng UTF-8 tái lập `Aé`. Giải mã nhầm Latin-1 cho `AÃ©` vì hai byte `C3`, `A9` được diễn giải thành hai ký tự Latin-1 khác.
4. `UTF-8` thuộc bộ mã Java SE bắt buộc hỗ trợ, nên `isSupported("UTF-8")` trả `true`.

Lưu mã nguồn với UTF-8 để các ký tự có dấu được biên dịch đúng. `javac -encoding UTF-8` làm rõ charset của **tệp mã nguồn**; nó không tự chọn charset cho `getBytes` hay `new String`. Việc hiển thị tiếng Việt/`é` trên terminal cũng cần terminal/font hỗ trợ Unicode.

## 5. Tên charset động, hỗ trợ và ngoại lệ

Với charset đến từ cấu hình, cần hiểu sự khác nhau giữa “tên sai cú pháp” và “tên hợp lệ nhưng runtime không hỗ trợ”.

```java
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

public class ResolveCharsetDemo {
    public static void main(String[] args) {
        for (String name : new String[]{"UTF-8", "x-demo-unknown-charset", "bad name!"}) {
            try {
                Charset charset = Charset.forName(name);
                System.out.println(name + " => " + charset.name());
            } catch (IllegalCharsetNameException e) {
                System.out.println(name + " => tên không hợp lệ");
            } catch (UnsupportedCharsetException e) {
                System.out.println(name + " => không được hỗ trợ");
            }
        }
    }
}
```

Đây là chương trình độc lập, nhưng `"x-demo-unknown-charset"` chỉ là tên minh họa: phần mềm có provider charset tự bổ sung có thể hỗ trợ tên đó, vì vậy không coi đầu ra cho tên giả là bất biến trên mọi runtime.

- `IllegalCharsetNameException`: chuỗi tên vi phạm cú pháp hợp lệ (ví dụ chứa dấu cách hoặc dấu chấm than không được phép ở vị trí đó).
- `UnsupportedCharsetException`: tên có cú pháp hợp lệ nhưng không tìm được provider hỗ trợ.
- `Charset.isSupported(name)`: có thể kiểm tra trước với **tên đúng cú pháp**, nhưng vẫn phải xử lý lỗi cú pháp và không giả định mọi tên đều có mặt trên mọi môi trường.
- `NullPointerException`: có thể xảy ra khi đưa `null` vào API tra cứu tên.

Nếu chương trình chỉ hỗ trợ UTF-8 theo giao thức, có thể **kiểm tra tên được cho phép** rồi dùng `StandardCharsets.UTF_8`; không nhất thiết phải chấp nhận mọi charset mà máy chủ tình cờ có. Khi lưu cấu hình, ưu tiên `charset.name()` làm dạng biểu diễn chuẩn và ghi rõ charset trong metadata/giao thức.

## 6. Chuyển đổi đúng ở ranh giới I/O

### Chuỗi, mảng byte và file

Các API sau nhận `Charset` trực tiếp:

| Mục đích | Ví dụ |
| --- | --- |
| String → byte[] | `text.getBytes(StandardCharsets.UTF_8)` |
| byte[] → String | `new String(bytes, StandardCharsets.UTF_8)` |
| Đọc file thành String | `Files.readString(path, StandardCharsets.UTF_8)` |
| Ghi String vào file | `Files.writeString(path, text, StandardCharsets.UTF_8)` |
| Byte stream → reader | `new InputStreamReader(input, StandardCharsets.UTF_8)` |
| Writer → byte stream | `new OutputStreamWriter(output, StandardCharsets.UTF_8)` |

Ví dụ sử dụng `Files`, với `path` là `Path` hợp lệ và `text` là `String`:

```java
Files.writeString(path, text, StandardCharsets.UTF_8);
String restored = Files.readString(path, StandardCharsets.UTF_8);
```

Đây là **đoạn mã minh họa**, không phải chương trình độc lập; cần import `java.nio.file.Files`, `java.nio.file.Path` và xử lý `IOException`. Với file vừa ghi, thao tác ghi mặc định có thể tạo hoặc cắt ngắn file đích; tránh dùng trên dữ liệu quan trọng khi chưa xác định đường dẫn.

Với byte stream, nên chỉ rõ charset ở **bước chuyển byte ↔ ký tự**. Ví dụ khi nhận HTTP response hoặc đọc socket, việc đọc `InputStream` chỉ tạo ra byte; đến khi tạo `InputStreamReader` hay giải mã dữ liệu mới cần áp dụng charset phù hợp với giao thức. `BufferedReader` và `BufferedWriter` chỉ bổ sung bộ đệm/tiện ích xử lý dòng, không tự sửa được lỗi dùng sai charset.

### Không lệ thuộc charset mặc định

Các API không truyền charset có thể sử dụng **charset mặc định của môi trường hoặc hợp đồng riêng của API**. Trên Java 17, charset mặc định thường phụ thuộc cấu hình hệ điều hành và JVM; từ Java 18, JEP 400 quy định UTF-8 là mặc định trong phần lớn trường hợp, với một số ngoại lệ/chế độ tương thích. Vì vậy không nên coi việc chạy đúng trên máy cá nhân là bảo đảm khi chuyển sang máy khác.

`Charset.defaultCharset()` dùng để **quan sát** lựa chọn mặc định; không nên lấy nó thay một charset được giao thức/tệp quy định. Với nội dung mới do ứng dụng kiểm soát, thống nhất rõ UTF-8 ở cả phía ghi và phía đọc.

Một số định dạng/giao thức có charset quy định riêng, hoặc thông tin BOM/metadata. Khi đọc nội dung có sẵn, cần tuân theo chuẩn cụ thể của định dạng thay vì áp đặt UTF-8 chỉ vì đó là lựa chọn tốt cho file mới.

## 7. Mã hóa ký tự không thể biểu diễn và dữ liệu byte sai

Sự khác nhau giữa hai tình huống:

- **Unmappable character:** chuỗi Unicode có ký tự không thể được biểu diễn trong charset đích. Ví dụ `é` không thể mã hóa trong US-ASCII.
- **Malformed input:** dãy byte không tuân theo quy tắc mã hóa khi decode. Ví dụ `C3 28` không phải chuỗi byte UTF-8 hợp lệ, vì `C3` cần một byte tiếp nối `80..BF` nhưng `28` không phải byte tiếp nối.

Những API tiện lợi như `String.getBytes(Charset)` và `new String(bytes, Charset)` thường **thay dữ liệu lỗi bằng ký tự/byte thay thế**. Điều này giúp xử lý đơn giản nhưng có thể âm thầm làm mất hoặc đổi dữ liệu. Khi cần phát hiện lỗi thay vì thay thế, dùng `CharsetEncoder`/`CharsetDecoder` cùng `CodingErrorAction.REPORT`.

Ví dụ độc lập **`StrictCharsetDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class StrictCharsetDemo {
    public static void main(String[] args) {
        byte[] invalidUtf8 = {(byte) 0xC3, 0x28};
        String replaced = new String(invalidUtf8, StandardCharsets.UTF_8);
        System.out.println("Có ký tự thay thế: "
                + (replaced.codePointAt(0) == 0xFFFD));

        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(invalidUtf8));
            System.out.println("Decode thành công");
        } catch (CharacterCodingException e) {
            System.out.println("UTF-8 không hợp lệ");
        }

        try {
            StandardCharsets.US_ASCII.newEncoder()
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap("é"));
            System.out.println("Encode thành công");
        } catch (CharacterCodingException e) {
            System.out.println("ASCII không mã hóa được é");
        }
    }
}
```

Chạy bằng Java 17+:

```bash
javac -encoding UTF-8 StrictCharsetDemo.java
java StrictCharsetDemo
```

Đầu ra:

```text
Có ký tự thay thế: true
UTF-8 không hợp lệ
ASCII không mã hóa được é
```

Chữ thay thế Unicode **U+FFFD** xuất hiện khi decode chuỗi byte UTF-8 lỗi bằng `new String(bytes, UTF_8)`. Khi bật `REPORT`, `decode` ném `CharacterCodingException` (hoặc lớp con) để chương trình tự quyết định cách xử lý. Với encoder ASCII, `é` không được biểu diễn nên báo lỗi thay vì âm thầm thay thành `?`.

Không dùng `CodingErrorAction.REPLACE` nếu tác vụ đòi hỏi bảo toàn dữ liệu chính xác; ngược lại, nếu nghiệp vụ cho phép thay thế, cần ghi rõ cách xử lý dữ liệu lỗi.

## 8. UTF-8, UTF-16, BOM và giới hạn của `String.length()`

UTF-8 lưu mỗi Unicode code point bằng 1–4 byte. UTF-16 lưu một code point trong một hoặc hai **đơn vị mã 16 bit**. Một `char` Java là một **UTF-16 code unit**, không phải lúc nào cũng là một ký tự Unicode hoàn chỉnh.

Chẳng hạn ký tự biểu tượng nằm ngoài Basic Multilingual Plane có thể có `String.length() == 2` (cặp surrogate) trong khi mã UTF-8 gồm 4 byte. Không dùng `String.length()` làm số byte của bản mã hóa; muốn biết dung lượng thực tế phải encode và kiểm tra `byte[].length`.

**BOM (Byte Order Mark)** có thể đánh dấu thứ tự byte trong một số dạng Unicode. `StandardCharsets.UTF_16` xử lý BOM theo quy tắc của UTF-16, trong khi `UTF_16BE`/`UTF_16LE` quy định byte order tường minh. UTF-8 **không yêu cầu BOM**; một file UTF-8 có BOM có thể mở đầu bằng ba byte `EF BB BF`. Tùy bộ đọc, BOM UTF-8 có thể xuất hiện như ký tự `U+FEFF` trong chuỗi kết quả thay vì tự động bị bỏ đi. Nếu định dạng cho phép hoặc yêu cầu BOM, cần xử lý theo hợp đồng cụ thể của định dạng đó.

Không dựa vào việc “nhìn thấy tiếng Việt đúng” để chứng minh hai phía dùng cùng charset: văn bản thuần ASCII nhiều khi cho đầu ra giống nhau dưới nhiều charset. Hãy kiểm tra với ký tự ngoài ASCII và so sánh byte khi chẩn đoán lỗi.

## 9. Sai lầm thường gặp và cách phòng tránh

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Ghi UTF-8, đọc ISO-8859-1 | Văn bản sai dấu, ví dụ `é` thành `Ã©` | Thống nhất charset lúc ghi và lúc đọc |
| Chuyển chuỗi sang byte không truyền charset | Phụ thuộc hợp đồng API/cấu hình runtime | Truyền `StandardCharsets.UTF_8` nếu định dạng cho phép |
| Dùng `StandardCharsets.UTF_8.toString()` để suy luận cú pháp bất kỳ | Nhầm tên charset với tên hằng hoặc alias | Dùng `name()` và tên chuẩn `UTF-8` |
| Xem `String.length()` là số byte | Sai với ký tự không ASCII/emoji | Dùng `text.getBytes(charset).length` |
| Dùng ASCII/Latin-1 cho văn bản Unicode tùy ý | Ký tự ngoài bộ mã bị thay hoặc báo lỗi | Dùng UTF-8 hoặc chọn bộ mã đúng yêu cầu |
| Decode byte lỗi bằng API thay thế nhưng không kiểm tra | Mất thông tin đầu vào mà không biết | Dùng `CharsetDecoder` với `REPORT` khi cần nghiêm ngặt |
| Tưởng mọi alias đều được hỗ trợ | Chạy được ở máy này nhưng không ở máy khác | Dùng sáu charset bắt buộc của `StandardCharsets` và tên chuẩn |
| Lẫn BOM UTF-8 với ký tự nội dung | Chuỗi có thêm `U+FEFF` đầu chuỗi | Đọc và xử lý BOM đúng quy định của file |

**Bài thực hành:** Encode `"Aé"` bằng `UTF_8` và `ISO_8859_1`. So sánh byte: UTF-8 là `41 C3 A9`, ISO-8859-1 là `41 E9`. Sau đó decode mỗi mảng bằng đúng charset của nó để nhận lại `"Aé"`; thử decode sai charset để quan sát kết quả. Đổi `é` thành ký tự tiếng Việt hoặc emoji và thử xem charset nào không thể biểu diễn ký tự đó.

**Ghi nhớ:** `Charset` là đối tượng mô tả bộ mã; `StandardCharsets` cung cấp sáu bộ mã bảo đảm có sẵn; tên charset có **tên chuẩn** và có thể có **alias**. Muốn bảo toàn văn bản khi I/O, chỉ rõ và thống nhất charset ở cả phía **encode** và **decode**; khi dữ liệu phải chính xác, xử lý malformed/unmappable một cách tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-004">Chọn charset tường minh khi đọc và ghi</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn charset tường minh khi đọc và ghi văn bản trong Java

Một chương trình có thể ghi tệp thành công nhưng khi đọc lại xuất hiện chữ **“Ã©”, “Tiáº¿ng” hoặc ký tự “�”**. Nguyên nhân thường nằm ở cách chương trình **mã hóa ký tự thành byte khi ghi** và **giải mã byte thành ký tự khi đọc**. Chọn charset tường minh giúp quy định cùng một cách diễn giải dữ liệu ở cả hai phía, tránh phụ thuộc vào môi trường chạy.

Trong Java, hãy ưu tiên truyền một đối tượng `Charset` cụ thể, thường là `StandardCharsets.UTF_8`, ở ranh giới chuyển đổi **byte ↔ văn bản**. Điều quan trọng không chỉ là “dùng UTF-8”, mà còn là biết hệ thống và tệp thực sự đã sử dụng charset nào.

## 1. Charset làm nhiệm vụ gì?

Java biểu diễn văn bản trong các đối tượng như `String`, `char`, `Reader` và `Writer`. Trong khi đó, tệp và nhiều kết nối I/O lưu hoặc truyền **byte**. `java.nio.charset.Charset` mô tả cách ánh xạ giữa ký tự Unicode và một dãy byte.

- **Encoding (mã hóa):** ký tự → byte, diễn ra khi ghi văn bản ra tệp hoặc gửi byte trên mạng.
- **Decoding (giải mã):** byte → ký tự, diễn ra khi đọc byte từ tệp hoặc mạng thành văn bản.
- **Charset:** quy tắc cho hai phép chuyển đổi; ví dụ `UTF-8`, `UTF-16`, `ISO-8859-1`.
- **Charset của dữ liệu** cần được xác định từ quy ước định dạng, giao thức, tài liệu nguồn hoặc metadata đáng tin cậy; không thể chắc chắn suy ra chỉ từ kiểu `String` của Java.

~~~text
                    GHI VĂN BẢN
String/char --encode UTF-8--> byte[] / tệp / luồng mạng
    "é"                        C3 A9

                    ĐỌC VĂN BẢN
byte[] / tệp / luồng mạng --decode UTF-8--> String/char
          C3 A9                             "é"
~~~

Ví dụ, `é` có mã Unicode U+00E9. Khi mã hóa bằng UTF-8, ký tự này thành **hai byte `C3 A9`** (hệ thập lục phân). Đọc hai byte đó bằng UTF-8 thì nhận lại `é`; đọc bằng ISO-8859-1 lại thành **`Ã©`**, vì charset này gán ý nghĩa khác cho từng byte.

**Không có “UTF-8 String” theo nghĩa String giữ nguyên byte UTF-8 bên trong.** `String` là dữ liệu văn bản Unicode; charset chỉ tham gia khi chuyển sang hoặc từ biểu diễn byte. Gọi `text.getBytes(charset)` mới tạo ra byte được mã hóa, còn `new String(bytes, charset)` mới giải mã byte thành chuỗi.

## 2. Tại sao phải chọn charset tường minh?

Một số API cũ sử dụng **charset mặc định của môi trường chạy** nếu bạn không cung cấp tham số. Ví dụ:

~~~java
// Dễ phụ thuộc môi trường: không nêu charset để đọc/ghi.
String text = new String(bytes);
byte[] encoded = text.getBytes();

Reader reader = new InputStreamReader(inputStream);
Writer writer = new OutputStreamWriter(outputStream);
~~~

Đây là **các dòng minh họa**, các biến `bytes` và `inputStream`/`outputStream` phải có sẵn; không phải chương trình độc lập. Những overload không có tham số charset dễ bị hiểu nhầm thành “luôn UTF-8”.

Lịch sử môi trường quan trọng:

- Trên **Java 17 và các bản trước**, charset mặc định của Java có thể thay đổi theo hệ điều hành, thiết lập locale hoặc cấu hình JVM; một chương trình chạy trên hai máy có thể ghi các byte khác nhau nếu dựa vào mặc định.
- Từ **Java 18**, JEP 400 chuẩn hóa charset mặc định thành UTF-8 trong đa số trường hợp, nhưng vẫn có ngoại lệ hoặc thiết lập tương thích, và không phải API nào cũng có cùng quy tắc. Vì vậy, code trao đổi dữ liệu nên tiếp tục khai báo charset theo **hợp đồng dữ liệu**.
- Một số API hiện đại đã **quy định mặc định UTF-8**, như `Files.readString(path)` hoặc `Files.writeString(path, text)`. Dẫu vậy, truyền `StandardCharsets.UTF_8` giúp người đọc thấy rõ ý định và dễ thay đổi khi tệp yêu cầu encoding khác.
- Không nên giả định charset của terminal, console, tệp CSV do bên thứ ba tạo hoặc header HTTP giống charset mặc định của JVM.

Ví dụ nên dùng:

~~~java
String text = new String(bytes, StandardCharsets.UTF_8);
byte[] encoded = text.getBytes(StandardCharsets.UTF_8);

Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
~~~

Đây cũng là các dòng minh họa, dùng import `java.nio.charset.StandardCharsets`, `java.io.Reader`, `java.io.Writer`, `java.io.InputStreamReader` và `java.io.OutputStreamWriter`. Trong chương trình thực, stream phải được quản lý vòng đời đúng hợp đồng ownership.

**Mục tiêu của chọn charset tường minh:** dữ liệu đi qua ranh giới byte/văn bản phải được hiểu theo **một quy ước đã xác định**, không thay đổi chỉ vì ứng dụng được chạy ở môi trường khác.

## 3. Chọn charset nào?

### UTF-8 thường là lựa chọn mặc định tốt cho văn bản mới

`StandardCharsets.UTF_8` là charset Unicode phổ biến, biểu diễn ký tự bằng số lượng byte biến thiên và tương thích với ASCII cho các ký tự ASCII. Nó biểu diễn tiếng Việt và hầu hết văn bản đa ngôn ngữ, được sử dụng rộng rãi trong hệ thống và định dạng hiện đại.

Ví dụ:

~~~java
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

Charset charset = StandardCharsets.UTF_8;
~~~

Không cần tự xây tên charset bằng chuỗi nếu Java đã có hằng số chuẩn. `StandardCharsets` bảo đảm có sẵn các charset như `UTF_8`, `UTF_16`, `UTF_16BE`, `UTF_16LE`, `ISO_8859_1`, `US_ASCII`, nên cũng tránh sai chính tả khi viết tên.

### Nhưng phải tôn trọng charset thực tế của dữ liệu

Nếu đặc tả yêu cầu UTF-16LE, dữ liệu đã được xuất bằng ISO-8859-1, hoặc một hệ thống kế thừa sử dụng một charset khác, **không tự áp UTF-8 để “chữa” tệp**. Phải đọc đúng charset ban đầu, sau đó nếu cần mới chủ động **chuyển mã** sang UTF-8 khi xuất.

Ví dụ định dạng cho biết nguồn là ISO-8859-1:

~~~java
String decoded = new String(sourceBytes, StandardCharsets.ISO_8859_1);
byte[] utf8Bytes = decoded.getBytes(StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa với `sourceBytes` có sẵn: trước tiên **giải mã theo encoding nguồn**, sau đó **mã hóa theo encoding đích**. Không thể “đổi encoding” bằng cách đổi tên tệp hay gọi `new String` với một charset ngẫu nhiên.

Đặc biệt, **không dùng `US_ASCII` hoặc `ISO_8859_1` cho văn bản chứa mọi loại ký tự tiếng Việt**: các charset này không biểu diễn được tất cả ký tự cần thiết. Một số API mã hóa có thể thay ký tự không biểu diễn được bằng ký tự thay thế, dẫn đến **mất dữ liệu không phục hồi được**.

## 4. Đọc và ghi tệp text: `Files.readString` và `Files.writeString`

Với tệp văn bản tương đối nhỏ, API của `java.nio.file.Files` là lựa chọn đơn giản:

~~~java
Path path = Path.of("note.txt");
Files.writeString(path, "Xin chào", StandardCharsets.UTF_8);
String value = Files.readString(path, StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa, cần import `java.nio.file.Files`, `java.nio.file.Path` và `java.nio.charset.StandardCharsets`; ngoại lệ I/O phải được xử lý hoặc khai báo. Charset ở phía ghi và phía đọc **khớp nhau**.

- `Files.writeString(path, text, charset)` mã hóa nội dung theo charset được truyền rồi ghi ra tệp; theo lựa chọn mở mặc định, tệp có thể được tạo hoặc nội dung tệp đang có bị ghi thay thế.
- `Files.readString(path, charset)` đọc nội dung tệp rồi giải mã thành `String` bằng charset đã chỉ định.
- Hai API có thể yêu cầu bộ nhớ tương ứng với kích thước văn bản. Với tệp lớn hoặc cần xử lý theo dòng, hãy dùng reader/writer theo luồng.

### Chương trình Java 17+ chạy độc lập

Lưu thành tệp **`ExplicitCharsetDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ExplicitCharsetDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("explicit-charset-", ".txt");
        String original = "Tiếng Việt: cà phê ☕";

        try {
            Files.writeString(file, original, StandardCharsets.UTF_8);

            byte[] storedBytes = Files.readAllBytes(file);
            String restored = Files.readString(file, StandardCharsets.UTF_8);

            System.out.println("Round-trip: " + original.equals(restored));
            System.out.println("Text: " + restored);
            System.out.println("Bytes equal: " + Arrays.equals(
                    storedBytes, restored.getBytes(StandardCharsets.UTF_8)));

            byte[] eAcuteUtf8 = "é".getBytes(StandardCharsets.UTF_8);
            System.out.println("Correct decode: "
                    + new String(eAcuteUtf8, StandardCharsets.UTF_8));
            System.out.println("Wrong decode: "
                    + new String(eAcuteUtf8, StandardCharsets.ISO_8859_1));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac -encoding UTF-8 ExplicitCharsetDemo.java
java ExplicitCharsetDemo
~~~

Với terminal hỗ trợ hiển thị Unicode, kết quả chuẩn là:

~~~text
Round-trip: true
Text: Tiếng Việt: cà phê ☕
Bytes equal: true
Correct decode: é
Wrong decode: Ã©
~~~

Điểm cần quan sát:

1. `Files.writeString` **mã hóa** `original` thành byte UTF-8 trong tệp.
2. `Files.readAllBytes` lấy đúng byte thô; chưa giải mã chúng.
3. `Files.readString` **giải mã** cùng byte bằng UTF-8 nên thu lại chuỗi ban đầu. Dòng `Round-trip: true` chứng minh văn bản khôi phục bằng chuỗi gốc trong ví dụ.
4. So sánh `storedBytes` với `restored.getBytes(UTF_8)` kiểm tra chuỗi được mã hóa lại cho ra đúng byte của tệp.
5. Hai dòng cuối dùng **cùng byte UTF-8 của `é`** và đổi duy nhất charset lúc đọc: UTF-8 → `é`, ISO-8859-1 → `Ã©`. Đây là hiện tượng *mojibake* — dữ liệu byte bị diễn giải theo quy tắc sai.
6. `finally` xóa tệp tạm để ví dụ không phụ thuộc vào tên tệp cố định và không để lại tệp được tạo trên hệ thống khi chương trình kết thúc thông thường.

Tùy cấu hình terminal, **việc hiển thị Unicode trên console** có thể khác với nội dung Unicode trong `String`. Lệnh `javac -encoding UTF-8` là **charset để trình biên dịch đọc tệp mã nguồn Java**, không tự quyết định charset của những tệp mà ứng dụng ghi/đọc; hai vấn đề cần phân biệt rõ.

## 5. Đọc và ghi văn bản theo luồng bằng `Reader`/`Writer`

Khi xử lý dữ liệu lớn hoặc liên tục, sử dụng cầu nối rõ charset:

- `InputStreamReader`: giải mã **byte từ `InputStream`** thành ký tự cho code đọc.
- `OutputStreamWriter`: mã hóa **ký tự từ code** thành byte cho `OutputStream`.
- `BufferedReader`/`BufferedWriter`: thêm buffer và API tiện lợi khi đọc/ghi văn bản; không thay charset của tầng chuyển đổi bên dưới.

Sơ đồ:

~~~text
ĐỌC: InputStream --[InputStreamReader + UTF-8]--> Reader/String
GHI: Writer/String --[OutputStreamWriter + UTF-8]--> OutputStream
~~~

Ví dụ là các phương thức trong cùng một lớp (được gọi từ ứng dụng khác):

~~~java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TextFileLines {
    public static void writeTwoLines(Path file) throws IOException {
        try (BufferedWriter writer =
                     Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("Hà Nội");
            writer.newLine();
            writer.write("Đà Nẵng");
            writer.newLine();
        }
    }

    public static void readLines(Path file) throws IOException {
        try (BufferedReader reader =
                     Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}
~~~

Đây là **lớp tiện ích độc lập có thể biên dịch**, nhưng không có `main`; một caller tạo `Path` và gọi `writeTwoLines(file)` rồi `readLines(file)` để thấy hai dòng tên địa điểm. `readLine()` trả lại nội dung từng dòng **không bao gồm** ký tự kết thúc dòng. `writer.newLine()` thêm dấu xuống dòng theo quy ước hệ thống; điều này **khác** với charset: charset kiểm soát ký tự được chuyển thành byte như thế nào.

Bạn cũng có thể tạo cầu nối trực tiếp khi nguồn/đích chưa phải tệp:

~~~java
// Các biến input/output là stream đã được caller cung cấp.
BufferedReader reader = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.UTF_8));
BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(output, StandardCharsets.UTF_8));
~~~

Hai dòng trên là **minh họa**: nếu bọc thêm `try-with-resources` để đóng wrapper, hãy chắc chắn phương thức hiện tại **sở hữu** cả stream gốc. Đóng `BufferedReader`/`BufferedWriter` thường đóng cả luồng nằm bên dưới; đừng vô tình đóng `System.in`, `System.out` hoặc stream framework/caller còn đang dùng.

Nếu dùng `Files.newBufferedReader(file)` hoặc `Files.newBufferedWriter(file)` mà không truyền charset, các overload cụ thể đó đã dùng UTF-8 theo hợp đồng API, nhưng phiên bản có `StandardCharsets.UTF_8` thể hiện rõ quy ước của bài học.

## 6. Phân biệt byte stream và character stream

| Nhiệm vụ | API tiêu biểu | Charset cần ở đâu? |
|---|---|---|
| Sao chép ảnh, ZIP, PDF, byte bất kỳ | `InputStream`, `OutputStream`, `Files.copy` | **Không** tự decode/encode; giữ nguyên byte |
| Ghi một `String` thành tệp text | `Files.writeString(path, text, charset)` | Tại bước **mã hóa** |
| Đọc một tệp text thành `String` | `Files.readString(path, charset)` | Tại bước **giải mã** |
| Đọc text theo dòng | `Files.newBufferedReader(path, charset)` | Reader áp dụng charset khi đọc byte |
| Ghi text theo dòng | `Files.newBufferedWriter(path, charset)` | Writer áp dụng charset khi ghi byte |
| Cần làm việc với luồng mạng chứa text | `InputStreamReader`, `OutputStreamWriter` | Charset đúng theo **giao thức dữ liệu** |
| Đã có byte và cần chuỗi | `new String(bytes, charset)` | Tại bước **giải mã** |
| Đã có chuỗi và cần byte | `text.getBytes(charset)` | Tại bước **mã hóa** |

Không nên đọc ảnh thành chuỗi rồi ghi lại bằng charset UTF-8: byte của ảnh có thể không hợp lệ dưới góc nhìn văn bản và sẽ bị thay đổi. Khi chỉ cần sao chép nguyên trạng, dùng luồng byte; khi cần hiểu nội dung văn bản, chọn charset và dùng API text.

## 7. Dữ liệu cũ và charset không khớp: xử lý ra sao?

Giả sử một tệp đã chứa byte biểu diễn text theo charset X, trong khi chương trình lại giải mã theo Y. Một trong hai tình huống thường gặp:

- **Byte vẫn hợp lệ dưới Y nhưng tạo ký tự sai:** ví dụ hai byte UTF-8 của `é` được đọc theo ISO-8859-1 và trở thành `Ã©`. Chương trình có thể không ném lỗi dù nội dung hiển thị sai.
- **Dãy byte không hợp lệ dưới Y:** một decoder kiểm tra nghiêm ngặt có thể ném lỗi; một decoder khác có thể thay các chuỗi lỗi bằng **U+FFFD REPLACEMENT CHARACTER (`�`)** hoặc ký tự thay thế khác tùy chính sách API.

Phân biệt **lỗi ở bước giải mã** với **lỗi ở bước mã hóa**:

1. Xác định byte gốc có còn nguyên vẹn không, bằng cách đọc **byte thô** trước (không qua text reader).
2. Tìm charset thật của nguồn từ tài liệu/giao thức/export settings; xem xét BOM/metadata nếu định dạng sử dụng, nhưng không coi chúng là bằng chứng chắc chắn cho mọi tệp.
3. Giải mã byte gốc bằng charset đúng, kiểm tra các ký tự có dấu và ký tự ngoài ASCII.
4. Nếu cần chuẩn hóa, **mã hóa lại** văn bản đúng thành UTF-8 để ghi tệp mới. Bảo toàn bản gốc khi chưa chắc chắn.
5. Nếu bản thân byte đã bị thay thế/ghi đè từ trước thì việc đọc lại bằng charset mới có thể **không cứu được** nội dung đã mất.

### Kiểm soát lỗi giải mã với `CharsetDecoder`

Khi cần **phát hiện byte UTF-8 không hợp lệ** thay vì âm thầm thay thế, có thể chọn chính sách `CodingErrorAction.REPORT`:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StrictUtf8Reader {
    public static void readStrictly(Path file) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (InputStream bytes = Files.newInputStream(file);
             Reader reader = new InputStreamReader(bytes, decoder)) {
            char[] buffer = new char[4096];
            while (reader.read(buffer) != -1) {
                // Xử lý phần ký tự vừa đọc theo chiều dài thực tế.
            }
        }
    }
}
~~~

Đây là **lớp tiện ích biên dịch được** và không có `main`. Ví dụ chủ yếu cho thấy cách **cấu hình decoder**; khi triển khai xử lý thật phải lưu kết quả của `reader.read(buffer)` rồi chỉ sử dụng số ký tự đã đọc. `REPORT` khiến lỗi giải mã được báo qua ngoại lệ I/O thay vì chấp nhận ký tự thay thế. Khi dùng `new String(bytes, charset)`, đừng mặc nhiên cho rằng API sẽ ném lỗi với mọi byte không hợp lệ: hành vi thay thế/lỗi phụ thuộc API và decoder.

Đối với mã hóa ký tự không thể biểu diễn trong charset đích, `CharsetEncoder` cũng có thể được cấu hình với `CodingErrorAction.REPORT`. Việc báo lỗi sớm đặc biệt hữu ích với dữ liệu cần bảo toàn đầy đủ, chẳng hạn xuất/nhập văn bản có tên riêng tiếng Việt.

## 8. Các điểm thực tế cần thận trọng

### Encoding của mã nguồn khác với encoding của dữ liệu runtime

- `javac -encoding UTF-8 Main.java`: chỉ định cách compiler **đọc mã nguồn**.
- `Files.readString(path, StandardCharsets.UTF_8)`: chỉ định cách ứng dụng **đọc nội dung tệp lúc chạy**.
- `System.out.println(text)`: chuỗi là Unicode nhưng quá trình chuyển ra byte và hiển thị trên terminal còn phụ thuộc cấu hình luồng chuẩn/terminal. Nó **không thay thế** việc kiểm tra byte trong file.

Ba khâu này có thể dùng các thiết lập khác nhau. Đừng dùng việc console in “đúng dấu” để kết luận tệp đã được lưu đúng byte.

### Đảm bảo hai phía đồng thuận

Với một giao thức hoặc API trao đổi text, charset nên là một phần của **hợp đồng**. Người gửi mã hóa UTF-8 và người nhận giải mã UTF-8. Nếu giao thức có metadata charset (ví dụ `Content-Type` của một định dạng HTTP thích hợp), hãy xử lý theo đặc tả giao thức tương ứng; không tự áp một charset bất kể metadata hợp lệ.

Nếu sản phẩm cho phép người dùng chọn mã hóa khi import, cần công khai lựa chọn và dùng chính lựa chọn đó để đọc. Đối với tệp do ứng dụng tự quản lý, chọn **một charset cố định**, ghi rõ trong tài liệu và kiểm thử bằng chuỗi có dấu.

### BOM, newline và dữ liệu không phải text

Một số tệp có **byte-order mark (BOM)** ở đầu. Cách xử lý BOM phụ thuộc charset và API; đừng giả định mọi text reader tự động bỏ BOM. Với UTF-8, BOM không bắt buộc. Nếu định dạng của bạn cho phép hoặc yêu cầu BOM, cần quy định rõ cách ghi/đọc.

**Dấu xuống dòng** như LF/CRLF là vấn đề **quy ước nội dung**; một charset đúng không tự chuẩn hóa tất cả xuống dòng. Nếu so sánh byte giữa hai hệ thống, cần tính cả dấu xuống dòng và BOM chứ không chỉ ký tự hiển thị.

## 9. Các lỗi phổ biến và cách sửa

| Sai lầm | Hậu quả | Cách đúng |
|---|---|---|
| `new String(bytes)` mà không biết default charset | Cùng byte có thể cho kết quả khác khi đổi môi trường | `new String(bytes, StandardCharsets.UTF_8)` nếu nguồn thực sự UTF-8 |
| `text.getBytes()` khi xuất dữ liệu cho dịch vụ khác | Byte đầu ra phụ thuộc charset mặc định của API/JVM | `text.getBytes(StandardCharsets.UTF_8)` theo hợp đồng |
| Đọc UTF-8 bằng ISO-8859-1 | Văn bản dạng `Ã©` thay vì `é` | Đọc theo đúng charset gốc |
| Ghi tiếng Việt bằng US-ASCII | Ký tự ngoài bảng mã có thể bị thay thế | Dùng UTF-8 hoặc charset đích có khả năng biểu diễn |
| Dùng `Reader`/`Writer` để sao chép ảnh/ZIP | Có thể phá hủy byte nhị phân | Dùng stream byte |
| Nghĩ `javac -encoding UTF-8` giải quyết lỗi file lúc chạy | Chỉ ảnh hưởng cách compiler đọc source | Chỉ định charset tại thao tác runtime |
| Quên đóng reader/writer do mình mở | Có thể mất dữ liệu còn trong bộ đệm hoặc rò rỉ tài nguyên | Sử dụng `try-with-resources` khi sở hữu |
| Thấy console đúng dấu rồi kết luận file đúng encoding | Terminal có cơ chế hiển thị riêng | Kiểm tra file bằng byte hoặc đọc lại đúng charset |
| Nghĩ mọi overload `Files` đều dùng charset mặc định | Một số overload đã có UTF-8 được chỉ định rõ | Đọc hợp đồng từng API; truyền charset khi cần làm rõ |

## 10. Tự kiểm tra

**Câu 1.** Ứng dụng A ghi chuỗi `"Cà phê"` thành UTF-8; ứng dụng B đọc cùng tệp bằng ISO-8859-1. Vấn đề ở bước nào?

> Ở bước **giải mã của B**: B diễn giải byte theo charset không đúng với quy ước lúc A mã hóa. Cần cho B sử dụng đúng UTF-8, miễn là byte gốc vẫn còn nguyên.

**Câu 2.** Tại sao `Files.readAllBytes(path)` không có tham số charset nhưng `Files.readString(path, charset)` lại có?

> `readAllBytes` chỉ lấy **byte thô**, không diễn giải thành ký tự. `readString` cần **giải mã** byte thành văn bản nên phải có quy tắc charset.

**Câu 3.** Muốn sao chép một tệp ZIP không thay đổi nội dung, có cần chọn UTF-8 không?

> **Không.** ZIP là dữ liệu nhị phân; nên sao chép byte. Charset chỉ dùng khi cần đọc/ghi **phần văn bản** ở một định dạng mà đặc tả có quy định.

**Câu 4.** Code chạy trên Java 17 và Java 21, nên dùng `new String(bytes)` nếu biết tệp UTF-8 không?

> Nên dùng **`new String(bytes, StandardCharsets.UTF_8)`**. Điều này làm rõ quy ước của tệp và tránh ảnh hưởng của sự khác nhau về default charset, phiên bản hoặc cấu hình JVM.

**Câu 5.** Một tệp gốc là ISO-8859-1. Muốn đổi sang UTF-8 thì bước nào phải làm trước?

> **Giải mã byte gốc bằng ISO-8859-1**, sau đó **mã hóa chuỗi Unicode thu được bằng UTF-8**. Không diễn giải trực tiếp byte gốc theo UTF-8 khi chúng không được mã hóa bằng UTF-8.

## Ghi nhớ

Khi **ghi văn bản**, chọn charset để **encode ký tự thành byte**; khi **đọc văn bản**, chọn charset để **decode byte thành ký tự**. Trong code Java mới, ưu tiên **`StandardCharsets.UTF_8`** cho dữ liệu do mình kiểm soát, nhưng luôn tôn trọng encoding thực tế hoặc quy định của định dạng/giao thức. Việc chỉ định charset ở đúng ranh giới byte/văn bản là một phần của hợp đồng dữ liệu và là cách ngăn lỗi hiển thị sai dấu trên nhiều môi trường.

**Tài liệu tham khảo:** [Java SE 17 — `Charset`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/Charset.html), [Java SE 17 — `Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html), [JEP 400 — UTF-8 by Default](https://openjdk.org/jeps/400).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-005">Charset mặc định, thay đổi UTF-8 từ JDK 18 và ngoại lệ theo API</a>

<details>
<summary>Xem chi tiết</summary>

# Charset mặc định, UTF-8 từ JDK 18 và các ngoại lệ theo API

Khi một chương trình Java chuyển giữa **byte** và **văn bản**, nó phải chọn một `Charset`: quy tắc mã hóa ký tự thành byte (encode) hoặc giải mã byte thành ký tự (decode). Nếu không chỉ rõ charset, *một số* API sử dụng **default charset của JVM**. Từ **JDK 18**, Java áp dụng **UTF-8 làm charset mặc định** theo JEP 400, giúp nhiều API có hành vi thống nhất hơn trên Windows, Linux và macOS. Tuy nhiên, không phải mọi API thiếu tham số charset đều lấy `Charset.defaultCharset()`: nhiều API đã quy định UTF-8, ISO-8859-1, charset thiết bị đầu cuối hoặc charset theo định dạng dữ liệu.

Điều quan trọng trong thực tế là **xác định đúng quy tắc của API đang dùng** và **chỉ định charset rõ ràng ở ranh giới dữ liệu** khi dữ liệu phải tương thích giữa các môi trường hoặc phiên bản Java.

## 1. Charset là gì và default charset dùng để làm gì?

Các đối tượng `String` trong Java biểu diễn chuỗi ký tự Unicode ở mức API; khi đọc/ghi tệp, gửi dữ liệu qua mạng hoặc trao đổi với hệ thống ngoài, chuỗi phải được biểu diễn bằng byte. Một ký tự có thể chiếm số byte khác nhau tùy charset.

Ví dụ ký tự `é`:

| Charset | Các byte theo hệ thập lục phân |
|---|---|
| UTF-8 | `C3 A9` |
| ISO-8859-1 | `E9` |
| US-ASCII | Không biểu diễn trực tiếp được; hành vi thay thế hoặc báo lỗi tùy API/bộ mã hóa |

Nếu ghi bằng UTF-8 nhưng đọc bằng ISO-8859-1, hai byte `C3 A9` có thể bị giải mã thành `Ã©`: đây là hiện tượng **mojibake** (văn bản bị biến dạng do sai mã hóa). Không phải lỗi dấu tiếng Việt trong `String`; đó là lỗi do hai phía diễn giải byte không cùng quy tắc.

`java.nio.charset.Charset.defaultCharset()` trả về đối tượng charset mặc định của JVM. Những API *được định nghĩa là dùng default charset* sẽ sử dụng charset này khi không nhận đối số charset. Vì vậy cần phân biệt:

- **Default charset**: mặc định chung của JVM, đọc bằng `Charset.defaultCharset()`.
- **Charset được truyền tường minh**: ví dụ `StandardCharsets.UTF_8`; không phụ thuộc default charset.
- **Charset cố định theo hợp đồng API**: ví dụ `Files.readString(path)` mặc định UTF-8, bất kể default charset là gì.
- **Charset của môi trường tương tác**: ví dụ đầu vào/đầu ra console, có thể khác `Charset.defaultCharset()`.
- **Charset theo định dạng hoặc giao thức**: ví dụ API nạp `Properties` từ `InputStream` theo ISO-8859-1.

Không được suy ra chỉ từ việc API **không có tham số charset** rằng nó chắc chắn sử dụng default charset.

## 2. Trước và sau JDK 18: thay đổi gì?

JEP 400, có hiệu lực từ **JDK 18**, làm cho UTF-8 trở thành default charset chuẩn của các API Java SE phụ thuộc default charset, trừ khi JVM được cấu hình đổi hành vi theo cơ chế hỗ trợ.

| Phiên bản | Default charset thông thường | Ảnh hưởng |
|---|---|---|
| JDK 17 trở xuống | Phụ thuộc hệ điều hành, locale và cấu hình môi trường tại thời điểm JVM khởi động | Cùng code không truyền charset có thể tạo/đọc các byte khác nhau giữa máy |
| JDK 18 trở lên | **UTF-8 theo mặc định** trên mọi nền tảng | Các API dùng default charset hoạt động nhất quán hơn |
| JDK 18+ với `-Dfile.encoding=COMPAT` | Quay về cách xác định charset gắn với môi trường kiểu JDK 17 | Dùng để thử nghiệm và xử lý tương thích khi nâng cấp |
| JDK 18+ với `-Dfile.encoding=UTF-8` | UTF-8 | Làm rõ ý định cấu hình bằng tham số khởi động |

Ví dụ trên một máy Windows dùng code page cũ, JDK 17 có thể dùng `windows-1252` hoặc `windows-31j` làm default charset, trong khi JDK 18+ theo mặc định dùng UTF-8. Tuy nhiên, **JDK 17 không có nghĩa là luôn không dùng UTF-8**: nhiều môi trường Linux và macOS đã có mặc định UTF-8 từ trước. Kết quả cụ thể phải kiểm tra trên JVM đang chạy.

Tác động của thay đổi nằm ở **các thao tác mã hóa/giải mã phụ thuộc default charset**. JEP 400 không tự chuyển mã những tệp cũ đã ghi, không tự phát hiện charset lịch sử của tệp và không làm thay đổi hợp đồng mã hóa đã quy định riêng của từng API.

### Hai thuộc tính cần phân biệt

- `file.encoding` liên quan đến lựa chọn default charset của JVM. Với JDK 18+, các giá trị được hỗ trợ theo đặc tả để lựa chọn cơ chế này là `UTF-8` và `COMPAT` khi truyền lúc khởi động.
- `native.encoding` mô tả charset có nguồn gốc từ cấu hình môi trường/hệ điều hành, hữu ích để nhận diện chế độ tương thích. Nó **có thể khác** `file.encoding` khi default charset là UTF-8.

Với JDK 18+, khi chạy `-Dfile.encoding=COMPAT`, default charset được suy ra từ `native.encoding` theo cơ chế tương thích; nó có thể vẫn là UTF-8 nếu môi trường dùng UTF-8. Đừng kỳ vọng `COMPAT` chắc chắn trả ra một charset khác UTF-8.

**Không coi `System.setProperty("file.encoding", "...")` sau khi chương trình đã chạy là cách chuyển charset an toàn.** JVM và các thành phần liên quan có thể đã khởi tạo/cached charset. Nếu cần điều chỉnh ở mức JVM, truyền tùy chọn lúc khởi động; nếu cần mã hóa ổn định ở mức dữ liệu, truyền `Charset` trực tiếp vào API.

## 3. Nhận diện charset trên JVM đang chạy

Tạo tệp **`CharsetProbe.java`** lưu ở UTF-8:

~~~java
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CharsetProbe {
    public static void main(String[] args) {
        System.out.println("default = " + Charset.defaultCharset().name());
        System.out.println("file.encoding = " + System.getProperty("file.encoding"));
        System.out.println("native.encoding = " + System.getProperty("native.encoding"));

        String text = "\u00E9"; // Ký tự é
        System.out.println("implicit = " + Arrays.toString(text.getBytes()));
        System.out.println("explicit UTF-8 = "
                + Arrays.toString(text.getBytes(StandardCharsets.UTF_8)));
    }
}
~~~

Biên dịch bằng JDK 17+; tham số `-encoding UTF-8` bảo đảm trình biên dịch diễn giải tệp nguồn UTF-8 theo ý định:

~~~bash
javac -encoding UTF-8 CharsetProbe.java
java CharsetProbe
~~~

Ba dòng đầu và dòng `implicit` **phụ thuộc JVM/môi trường hoặc cờ khởi động**, vì vậy không có một bản stdout duy nhất đúng cho mọi máy. Điều bất biến là:

~~~text
explicit UTF-8 = [-61, -87]
~~~

Dòng này in `byte[]` theo kiểu `byte` có dấu trong Java, nên các byte `C3 A9` được biểu diễn thành `-61, -87`. Đây vẫn là byte UTF-8 hợp lệ của `é`.

Trên JDK 18+, thử độc lập hai chế độ sau:

~~~bash
java -Dfile.encoding=UTF-8 CharsetProbe
java -Dfile.encoding=COMPAT CharsetProbe
~~~

Khi `UTF-8` được sử dụng làm default charset, dòng `implicit` cũng là `[-61, -87]`. Khi `COMPAT` chọn một charset khác, dòng này **có thể thay đổi**, ví dụ một ký tự được mã hóa bằng một byte theo ISO-8859-1 hoặc một encoding tương thích khác. Đừng hard-code kết quả `native.encoding` hay charset Windows trong bài kiểm tra chạy trên nhiều hệ điều hành.

Có thể xem thêm thuộc tính khởi động bằng `java -XshowSettings:properties -version`. Cách đáng tin nhất để biết default charset Java thực sự đang dùng trong code vẫn là `Charset.defaultCharset()`.

## 4. Các API thực sự phụ thuộc default charset

Trong những lời gọi sau, nếu **không truyền charset**, chuyển đổi ký tự ↔ byte sẽ dựa trên default charset của JVM theo hợp đồng API:

| API/ví dụ | Vùng ảnh hưởng |
|---|---|
| `"text".getBytes()` | Mã hóa `String` thành byte |
| `new String(byteArray)` | Giải mã byte thành `String` |
| `new InputStreamReader(inputStream)` | Giải mã byte từ một input stream thành ký tự |
| `new OutputStreamWriter(outputStream)` | Mã hóa ký tự khi ghi ra output stream |
| `new FileReader(file)`, `new FileWriter(file)` (constructor không chỉ charset) | Đọc/ghi ký tự trên tệp |
| Các constructor `Scanner` hoặc `Formatter` dùng default charset với nguồn/đích là byte | Chuyển đổi ký tự ở tầng vào/ra theo đúng overload |

Ví dụ cặp lời gọi phụ thuộc môi trường:

~~~java
// Mảnh mã minh họa: sử dụng charset mặc định, có thể khác giữa JDK 17 và 18+.
byte[] bytes = "Tiếng Việt".getBytes();
String decoded = new String(bytes);
~~~

Nếu cả hai thao tác xảy ra trong **cùng JVM** và không có biến đổi byte, chúng thường cho lại chuỗi ban đầu với charset hỗ trợ văn bản đó; nhưng **các byte đã tạo có thể khác giữa máy**. Việc ghi tệp trên máy A rồi đọc bằng máy B mới là ranh giới dễ xuất hiện lỗi mã hóa, đặc biệt với các charset không biểu diễn được tất cả ký tự.

Để khống chế hành vi, chọn overload có `Charset`:

~~~java
import java.nio.charset.StandardCharsets;

byte[] bytes = "Tiếng Việt".getBytes(StandardCharsets.UTF_8);
String decoded = new String(bytes, StandardCharsets.UTF_8);
~~~

Đoạn này là **mảnh mã minh họa** vì không có `main`; nó cho thấy cách chỉ định UTF-8 độc lập với default charset. `StandardCharsets.UTF_8` là hằng charset chuẩn có sẵn trên mọi triển khai Java SE.

Với luồng, cũng truyền vào constructor:

~~~java
Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
~~~

Đây là mảnh mã minh họa; `input` và `output` là các stream đã được mở, phải được đóng đúng cách. Với tệp, có thể chọn constructor `FileReader`/`FileWriter` nhận `Charset` trên các JDK hiện đại, hoặc dùng `Files.newBufferedReader`/`Files.newBufferedWriter` nhận `Charset`.

## 5. Những ngoại lệ nổi bật: API không dùng default charset

Thay đổi từ JDK 18 **không có nghĩa mọi API Java đều đổi về UTF-8**. Có API đã cố định UTF-8 trước đó, có API cố định encoding khác và có API phụ thuộc đầu cuối/định dạng.

| API hoặc tình huống | Quy tắc thực tế |
|---|---|
| `Files.readString(path)`, `Files.writeString(path, text)` | Mặc định **UTF-8** theo hợp đồng API (từ khi các API này xuất hiện ở JDK 11) |
| `Files.newBufferedReader(path)`, `Files.newBufferedWriter(path)` | Mặc định **UTF-8** cả trên JDK 17 |
| `Files.readAllLines(path)`, `Files.lines(path)` khi không chỉ charset | Mặc định **UTF-8** |
| `Properties.load(InputStream)`, `Properties.store(OutputStream, ...)` | Định dạng properties dạng byte dùng **ISO-8859-1**, với các escape Unicode theo quy tắc riêng |
| `Properties.load(Reader)`, `Properties.store(Writer, ...)` | Ký tự đã/đang được xử lý bởi `Reader`/`Writer`; charset do phía tạo reader/writer quyết định |
| `PropertyResourceBundle` nạp file `.properties` | Từ JDK 9, mặc định thử UTF-8, có cơ chế fallback ISO-8859-1 khi byte không phải UTF-8 hợp lệ theo quy tắc ResourceBundle; **không đồng nhất với `Properties.load(InputStream)`** |
| `System.out`, `System.err` và việc đọc/ghi ở console | Charset của các stream/console có thể theo môi trường đầu cuối, không nhất thiết bằng `Charset.defaultCharset()`; có quy tắc hệ thống riêng |
| `FileInputStream`, `FileOutputStream`, `Files.readAllBytes` | Hoạt động trên **byte thô**, không tự encode/decode text |
| `InputStreamReader(input, someCharset)`, `new String(bytes, someCharset)` | Dùng **charset được truyền tường minh**, không dùng default |

Vì vậy, chuyển Java 17 → 18 có thể khiến `new FileReader("old.txt")` diễn giải khác trước, trong khi `Files.readString(path)` vốn luôn dùng UTF-8 nên **không thay đổi charset chỉ vì JEP 400**.

### 5.1. Các phương thức của `Files` vốn cố định UTF-8

Các API file dạng ký tự tiện lợi của `java.nio.file.Files` thường mặc định UTF-8. Điều này đã đúng từ trước JDK 18:

~~~java
String content = Files.readString(path); // UTF-8
Files.writeString(path, content);         // UTF-8

try (BufferedReader reader = Files.newBufferedReader(path)) {
    String line = reader.readLine();      // UTF-8
}
~~~

Đây là **mảnh mã minh họa** cần import `Files`, `Path`, `BufferedReader` và `IOException`, đồng thời khai báo `path`. Các phương thức cũng có overload nhận `Charset` để xử lý dữ liệu theo encoding khác. Không dùng kết quả của `Charset.defaultCharset()` để đoán encoding của các overload trên.

### 5.2. `Properties.load(InputStream)` là trường hợp dễ nhầm nhất

Định dạng properties đọc trực tiếp từ byte qua `Properties.load(InputStream)` **không chuyển sang UTF-8 ở JDK 18**. API này giả định **ISO-8859-1** đối với byte đầu vào và hỗ trợ Unicode escape cho ký tự khác. Trong khi đó, `Properties.load(Reader)` tiếp nhận sẵn các ký tự được reader giải mã theo charset do ứng dụng chọn.

Ví dụ một file byte UTF-8 chứa dòng `name=Café`: nếu dùng `load(InputStream)`, ký tự `é` có thể thành `Ã©`; muốn nạp đúng UTF-8 thì bọc `InputStreamReader` với `StandardCharsets.UTF_8` rồi dùng `load(Reader)`.

Đặc biệt, đừng áp dụng nhầm quy tắc này cho `ResourceBundle.getBundle(...)` tải `PropertyResourceBundle`, vì hành vi tải resource bundle dạng properties đã đổi theo JEP 226 từ **JDK 9**. Đây là hai API có quy tắc khác nhau dù tệp cùng phần mở rộng `.properties`.

### 5.3. Console, `System.in/out/err` có quy tắc riêng

Trong ứng dụng chạy từ terminal, bytes đầu vào và đầu ra phải phù hợp encoding mà terminal hoặc môi trường host đang sử dụng. JEP 400 cố ý không bắt buộc `System.out` và `System.err` phải luôn dùng UTF-8 như `Charset.defaultCharset()`; việc tự đổi đầu ra console có thể khiến chữ bị lỗi trên một số môi trường.

- `System.out`/`System.err` là các `PrintStream` có thiết lập encoding gắn với môi trường đầu cuối hoặc thuộc tính liên quan (chẳng hạn `stdout.encoding` và `stderr.encoding`). Không suy ra chúng bằng default charset.
- `System.in` là **luồng byte**. Khi bọc bằng `new InputStreamReader(System.in)` hoặc `new Scanner(System.in)` không chỉ charset, chính wrapper mặc định có thể dùng **default charset**, vốn không nhất thiết trùng với encoding của terminal.
- Với ứng dụng nhập liệu tương tác, xem xét `System.console()`/`Console` khi có console thích hợp; `System.console()` có thể trả `null` khi chạy trong IDE, pipeline hoặc khi chuyển hướng I/O.
- Ở những JDK có thuộc tính `stdin.encoding`, `stdout.encoding` và `stderr.encoding`, có thể tham khảo chúng theo tài liệu phiên bản triển khai để chọn charset tương ứng. Chúng **không phải bằng chứng** rằng `file.encoding` luôn giống encoding console.

Khi kết nối với một giao thức xác định UTF-8 hoặc ghi ra tệp, nên ghi rõ UTF-8 ở chỗ chuyển ký tự thành byte. Với tương tác trực tiếp terminal, phải xem đặc điểm của thiết bị đầu cuối.

## 6. Ví dụ Java 17+: kiểm chứng API cố định UTF-8 và ISO-8859-1

Tạo tệp **`CharsetApiRulesDemo.java`**, lưu bằng UTF-8:

~~~java
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

public class CharsetApiRulesDemo {
    public static void main(String[] args) throws Exception {
        String text = "Caf\u00E9";
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);

        System.out.println("UTF-8 bytes: " + Arrays.toString(utf8));
        System.out.println("Explicit decode: "
                + text.equals(new String(utf8, StandardCharsets.UTF_8)));

        Path file = Files.createTempFile("charset-rule-", ".txt");
        try {
            Files.write(file, utf8);
            System.out.println("Files.readString: "
                    + text.equals(Files.readString(file)));
        } finally {
            Files.deleteIfExists(file);
        }

        byte[] propertiesUtf8 = ("name=" + text + "\n")
                .getBytes(StandardCharsets.UTF_8);

        Properties byteProperties = new Properties();
        byteProperties.load(new ByteArrayInputStream(propertiesUtf8));
        System.out.println("Properties.load(InputStream): "
                + text.equals(byteProperties.getProperty("name")));

        Properties readerProperties = new Properties();
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(propertiesUtf8), StandardCharsets.UTF_8)) {
            readerProperties.load(reader);
        }
        System.out.println("Properties.load(Reader UTF-8): "
                + text.equals(readerProperties.getProperty("name")));
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac -encoding UTF-8 CharsetApiRulesDemo.java
java CharsetApiRulesDemo
~~~

Stdout ổn định trên JDK 17+ (kể cả khi default charset khác UTF-8):

~~~text
UTF-8 bytes: [67, 97, 102, -61, -87]
Explicit decode: true
Files.readString: true
Properties.load(InputStream): false
Properties.load(Reader UTF-8): true
~~~

Tại sao kết quả như vậy?

1. `text.getBytes(StandardCharsets.UTF_8)` tạo byte UTF-8 không phụ thuộc môi trường. Ký tự `é` được mã hóa thành `C3 A9`.
2. `new String(utf8, UTF_8)` giải mã đúng cùng charset; phép so sánh chuỗi trả `true`.
3. `Files.readString(file)` không truyền charset vẫn mặc định UTF-8 theo hợp đồng riêng; trả lại đúng chuỗi, kể cả trên JDK 17.
4. `Properties.load(InputStream)` dùng ISO-8859-1 và diễn giải hai byte UTF-8 của `é` thành hai ký tự, nên chuỗi khác giá trị gốc.
5. `Properties.load(Reader)` nhận ký tự đã được giải mã bằng UTF-8 nên đọc đúng nội dung.

Chương trình sử dụng file tạm và xóa trong `finally` để không ghi đè một file có sẵn. Ví dụ là kiểm chứng **khác biệt giữa hợp đồng API**, không cần đổi `file.encoding` để tạo khác biệt này.

## 7. Ví dụ nhỏ về lỗi khi chuyển phiên bản JDK

Giả sử một hệ thống trước đây chạy JDK 17 trên máy dùng charset cũ, mã như sau:

~~~java
// Mảnh mã minh họa: thiếu chỉ định charset ở ranh giới dữ liệu.
try (FileWriter writer = new FileWriter("report.txt")) {
    writer.write("Café");
}
~~~

Nếu default charset trước đây là ISO-8859-1, `é` có thể được ghi thành byte `E9`. Khi chuyển sang JDK 18+, cùng code thiếu charset có thể ghi UTF-8, trong đó `é` thành `C3 A9`. Một ứng dụng cũ tiếp tục mong chờ ISO-8859-1 sẽ đọc sai file mới; ngược lại, ứng dụng nâng cấp cũng có thể đọc sai dữ liệu cũ.

Giải pháp bền vững là **biết charset thực tế của dữ liệu và chọn tường minh**. Chẳng hạn, khi hệ thống xác định file phải là UTF-8:

~~~java
// Mảnh mã minh họa: cần các import và xử lý IOException phù hợp.
try (Writer writer = new OutputStreamWriter(
        new FileOutputStream("report.txt"), StandardCharsets.UTF_8)) {
    writer.write("Café");
}
~~~

Hoặc trên Java hiện đại:

~~~java
Files.writeString(Path.of("report.txt"), "Café", StandardCharsets.UTF_8);
~~~

Các đoạn trên chỉ nhằm làm rõ lựa chọn charset; với tệp thực phải xem xét chính sách tạo/ghi đè, quyền truy cập và lỗi I/O. Nếu **dữ liệu cũ thật sự dùng encoding legacy**, cần đọc bằng charset legacy **chính xác** để chuyển mã sang UTF-8; chỉ đổi cờ JVM hoặc gắn nhãn “UTF-8” lên byte cũ không tự biến đổi byte thành UTF-8.

Khi di chuyển dữ liệu, xử lý lỗi giải mã có ý nghĩa quan trọng: một số decoder mặc định có thể thay byte không hợp lệ bằng ký tự thay thế, che giấu mất mát dữ liệu. Dữ liệu quan trọng nên được kiểm tra bằng charset đúng và cấu hình decoder phù hợp để phát hiện lỗi thay thế hoặc ký tự không ánh xạ được.

## 8. Kiểm thử migration JDK 17 → 18+

Một quy trình kiểm thử hữu ích:

1. **Liệt kê vị trí phụ thuộc default charset**: `new String(bytes)`, `String.getBytes()`, `FileReader/FileWriter`, `InputStreamReader/OutputStreamWriter`, và các overload tương ứng của `Scanner` hoặc `Formatter`. Đối chiếu hợp đồng từng overload.
2. **Xác định nguồn gốc byte**: dữ liệu tệp cũ, tệp mới, tài nguyên cấu hình, terminal, payload mạng và dữ liệu đối tác có thể thuộc các charset khác nhau.
3. **Thử với UTF-8 rõ ràng ngay trên môi trường cũ**: trên các JDK triển khai hỗ trợ, thử `java -Dfile.encoding=UTF-8 ...` để phát hiện ảnh hưởng khi nâng cấp. Với `javac`, dùng `-encoding UTF-8` khi file nguồn được lưu UTF-8.
4. **Thử chế độ tương thích trên JDK 18+**: `java -Dfile.encoding=COMPAT ...` để so sánh trong quá trình chẩn đoán; đừng coi đây là giải pháp lâu dài thay cho việc ghi rõ encoding ở ranh giới dữ liệu.
5. **Kiểm tra output bằng byte và nội dung đã giải mã**: dùng một tập mẫu gồm ASCII, dấu tiếng Việt, ký tự không thuộc Latin-1 và dữ liệu đã tồn tại. Một kiểm thử chỉ dùng ASCII thường không phân biệt được UTF-8 với nhiều charset cũ.
6. **Giữ nguyên các API có hợp đồng đặc thù**: `Properties.load(InputStream)` và `Files.readString` không cần đổi ý nghĩa chỉ vì JEP 400. Nếu thay API thì phải chủ động đánh giá chuyển đổi format.
7. **Kiểm tra stdout/console riêng**: việc file UTF-8 hợp lệ không chứng minh terminal cũng đang diễn giải stdout theo UTF-8; hãy phân biệt hiển thị trên terminal với byte thật trong file.

Trong dự án, đưa charset vào định nghĩa định dạng tệp hoặc giao thức và kiểm thử vòng đời ghi → đọc trên các môi trường mục tiêu. Không dựa vào charset máy lập trình viên làm hợp đồng dữ liệu.

## 9. Lỗi tư duy thường gặp

| Hiểu nhầm | Thực tế |
|---|---|
| “Java 17 mặc định luôn là ANSI; Java 18 luôn UTF-8 trong mọi API” | JDK 17 có thể đã dùng UTF-8; JDK 18+ mặc định UTF-8 **cho API dùng default charset**, vẫn có ngoại lệ |
| “Không truyền charset thì chắc chắn dùng `Charset.defaultCharset()`” | Các API `Files` văn bản, `Properties.load(InputStream)` và console có quy tắc khác |
| “`COMPAT` luôn có nghĩa ISO-8859-1” | `COMPAT` dùng charset tương thích theo môi trường; có thể là UTF-8 hoặc charset khác |
| “Chỉ cần gọi `System.setProperty("file.encoding", "UTF-8")` là mọi luồng đổi ngay” | Cấu hình lúc runtime không đáng tin để thay đổi bộ mã đang được sử dụng |
| “Đổi sang JDK 18 sẽ tự chuyển mọi file cũ sang UTF-8” | Byte đã lưu không thay đổi; cần đọc đúng charset nguồn rồi encode sang charset đích |
| “`System.out.println(...)` chắc chắn xuất UTF-8 từ JDK 18” | Charset console/stdout có quy tắc riêng; terminal còn phải giải mã byte đúng |
| “`.properties` thì luôn ISO-8859-1” | `Properties.load(InputStream)` dùng ISO-8859-1; `PropertyResourceBundle` từ JDK 9 thường đọc UTF-8, có fallback |
| “Dùng ASCII để test đã đủ” | Với ký tự ASCII đơn giản, rất nhiều charset tạo cùng byte; phải dùng ký tự ngoài ASCII để thấy khác biệt |

## 10. Ghi nhớ cốt lõi

- **JDK 17 trở xuống**: default charset phụ thuộc cấu hình nền tảng; **JDK 18 trở lên**: mặc định UTF-8 theo **JEP 400**.
- **`Charset.defaultCharset()`** cho biết charset mặc định của JVM thực tế; trên JDK 18+ có thể thử `-Dfile.encoding=UTF-8` hoặc `-Dfile.encoding=COMPAT` lúc khởi động.
- Chỉ những API **thật sự dựa vào default charset** mới nhận ảnh hưởng tương ứng; API mã hóa cố định và stream byte thô không tự đổi ý nghĩa.
- **`Files.readString/newBufferedReader` mặc định UTF-8**, còn **`Properties.load(InputStream)` dùng ISO-8859-1**; đây là hai ví dụ dễ đối chiếu nhất.
- **Console/stdin/stdout/stderr** có quy tắc encoding riêng; không đồng nhất với default charset mà không kiểm tra.
- Khi dữ liệu phải tồn tại lâu dài hoặc trao đổi qua nhiều máy, **luôn nêu rõ charset**, thường dùng `StandardCharsets.UTF_8`, và kiểm thử cả thao tác encode và decode.

### Tài liệu tham khảo chính thức

- [JEP 400 — UTF-8 by Default](https://openjdk.org/jeps/400)
- [Java SE — Charset.defaultCharset()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/Charset.html#defaultCharset())
- [Java SE — Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html)
- [Java SE — Properties](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Properties.html)
- [Oracle — Supported Encodings](https://docs.oracle.com/en/java/javase/25/intl/supported-encodings.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-006">BOM và khác biệt UTF-16, UTF-16BE, UTF-16LE</a>

<details>
<summary>Xem chi tiết</summary>

# BOM và sự khác biệt giữa UTF-16, UTF-16BE, UTF-16LE trong Java

## Mục tiêu bài học

Khi đọc hoặc ghi văn bản bằng **luồng byte**, tên charset không chỉ quyết định cách ánh xạ ký tự Unicode thành byte: với UTF-16, nó còn quyết định **thứ tự byte (byte order)** và cách xử lý **BOM (Byte Order Mark)**. Sau bài học, bạn sẽ:

- Giải thích được vì sao cùng một văn bản lại có dãy byte khác nhau khi dùng UTF-16BE và UTF-16LE.
- Nhận biết BOM `FE FF`, `FF FE` và phân biệt BOM với dữ liệu ký tự thông thường.
- Hiểu chính xác cách Java `StandardCharsets.UTF_16`, `UTF_16BE` và `UTF_16LE` mã hóa/giải mã dữ liệu.
- Viết chương trình Java 17+ có thể kiểm chứng byte thực tế, xử lý tệp có hoặc không có BOM và tránh đọc sai nội dung.

## 1. Nền tảng: UTF-16 mã hóa *đơn vị mã* thành byte

Unicode xác định **code point** (điểm mã), chẳng hạn `U+0041` cho chữ `A` và `U+1F600` cho emoji 😀. UTF-16 biểu diễn code point thành một hoặc hai **code unit 16 bit**:

- Với ký tự thuộc Basic Multilingual Plane (BMP), trừ vùng surrogate, UTF-16 thường dùng một code unit 16 bit. Ví dụ `A` là `0x0041`.
- Với code point ngoài BMP, UTF-16 dùng **một cặp surrogate** gồm hai code unit. Ví dụ `U+1F600` được biểu diễn bằng `D83D DE00`.
- **16 bit = 2 byte**, nhưng thứ tự của hai byte trong mỗi code unit có thể khác nhau. Do đó UTF-16 không đồng nghĩa “mọi ký tự luôn chiếm hai byte”.

Khi cần chuyển code unit `0x0041` thành byte:

| Kiểu lưu | Thứ tự byte | Dãy hex |
| --- | --- | --- |
| Big-endian (BE) | Byte cao trước | `00 41` |
| Little-endian (LE) | Byte thấp trước | `41 00` |

Với chuỗi `A😀`, các code unit lần lượt là `0041 D83D DE00`:

- **UTF-16BE:** `00 41 D8 3D DE 00`.
- **UTF-16LE:** `41 00 3D D8 00 DE`.

Hai dãy khác nhau về thứ tự byte trong **từng code unit**, nhưng đều có thể biểu diễn cùng một chuỗi khi bộ giải mã biết rõ byte order.

## 2. BOM là gì, nằm ở đâu?

**BOM (Byte Order Mark)** là dấu hiệu ở **đầu dữ liệu byte** giúp bên đọc nhận biết thứ tự byte của UTF-16 khi định dạng không quy định sẵn BE hay LE. BOM liên hệ với code point `U+FEFF`, được mã hóa khác nhau tùy byte order:

| Hai byte đầu | Ý nghĩa trong UTF-16 |
| --- | --- |
| `FE FF` | BOM của UTF-16 big-endian |
| `FF FE` | BOM của UTF-16 little-endian |

Vì vậy, tệp chứa chữ `A` có thể trông như sau:

```text
UTF-16 BE có BOM: FE FF | 00 41
UTF-16 LE có BOM: FF FE | 41 00
UTF-16 BE không BOM:  00 41
UTF-16 LE không BOM:  41 00
```

Dấu `|` chỉ để phân biệt **BOM** và **payload**; nó không phải byte trong tệp.

Có vài điểm rất quan trọng:

1. **Không phải mọi dữ liệu UTF-16 đều bắt buộc có BOM.** Một giao thức hoặc định dạng có thể công bố rõ UTF-16BE/UTF-16LE, khi đó byte order đã biết và không cần BOM.
2. **BOM không chứa độ dài dữ liệu.** Nó chỉ hỗ trợ nhận biết cách giải mã ở đầu dòng byte.
3. **BOM không phải ký tự hiển thị thông thường.** Khi bộ giải mã UTF-16 nhận diện BOM là chữ ký byte order, chữ ký đó thường không trở thành ký tự trong kết quả giải mã.
4. **BOM có thể được hiểu là ký tự `U+FEFF` nếu dùng bộ giải mã theo byte order cố định.** Đó là lý do một chuỗi có thể bắt đầu bằng ký tự vô hình sau khi đọc không đúng chế độ.
5. **Không nhầm với UTF-8 BOM `EF BB BF`.** UTF-8 không có hai kiểu byte order; BOM UTF-8 là vấn đề khác, không phải cách chọn BE/LE.

## 3. Ba charset UTF-16 trong Java khác nhau thế nào?

Java cung cấp sẵn ba hằng trong `java.nio.charset.StandardCharsets`:

| Charset Java | Khi **mã hóa** (String → byte[]) | Khi **giải mã** (byte[] → String) |
| --- | --- | --- |
| `StandardCharsets.UTF_16` | Tạo BOM `FE FF` rồi mã hóa nội dung theo **big-endian**. | Nếu có BOM `FE FF` / `FF FE` ở đầu thì nhận diện BE/LE tương ứng và tiêu thụ BOM; nếu **không có BOM**, mặc định giải mã theo **big-endian**. |
| `StandardCharsets.UTF_16BE` | Mã hóa big-endian; **không tự thêm BOM**. | Giải mã theo big-endian cố định; **không tự bỏ BOM** như chữ ký byte order. |
| `StandardCharsets.UTF_16LE` | Mã hóa little-endian; **không tự thêm BOM**. | Giải mã theo little-endian cố định; **không tự bỏ BOM** như chữ ký byte order. |

Đây là **hành vi của các charset Java này**, không phải lời cam kết rằng mọi trình đọc văn bản của mọi ngôn ngữ đều chọn BE khi thiếu BOM. Khi trao đổi dữ liệu giữa các hệ thống, phải làm theo tài liệu của định dạng hoặc giao thức.

### Ví dụ trực giác

Nếu nhận `41 00` mà **không có BOM**:

- Giải mã bằng `UTF_16LE`: kết quả là `A` (`U+0041`).
- Giải mã bằng `UTF_16`: Java mặc định BE, nên hai byte thành code unit `0x4100` (`U+4100`), **không phải** `A`.

Không có cách chung để đoán chắc ý nghĩa chỉ từ hai byte `41 00`; quyết định encoding phải đến từ BOM hợp lệ, metadata đáng tin cậy hoặc quy định giao thức.

## 4. Java 17+ — quan sát byte được mã hóa

Tạo tệp **`Utf16BytesDemo.java`** (mã nguồn độc lập):

```java
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class Utf16BytesDemo {
    private static void show(String name, Charset charset, String text) {
        byte[] bytes = text.getBytes(charset);
        System.out.println(name + " = " + HexFormat.of().formatHex(bytes));
    }

    public static void main(String[] args) {
        String text = "A" + new String(Character.toChars(0x1F600));

        show("UTF-16", StandardCharsets.UTF_16, text);
        show("UTF-16BE", StandardCharsets.UTF_16BE, text);
        show("UTF-16LE", StandardCharsets.UTF_16LE, text);

        System.out.println("UTF-16 code units = " + text.length());
        System.out.println("Unicode code points = "
                + text.codePointCount(0, text.length()));
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 Utf16BytesDemo.java
java Utf16BytesDemo
```

Đầu ra chính xác:

```text
UTF-16 = feff0041d83dde00
UTF-16BE = 0041d83dde00
UTF-16LE = 41003dd800de
UTF-16 code units = 3
Unicode code points = 2
```

Giải thích các con số:

- `feff` ở đầu dòng UTF-16 là BOM big-endian, do bộ mã hóa `UTF_16` tự tạo.
- Phần nội dung big-endian `0041d83dde00` giống dòng UTF-16BE. Dòng UTF-16LE đảo thứ tự byte của từng code unit thành `41003dd800de`.
- Chuỗi `A😀` có **hai Unicode code point** nhưng **ba code unit UTF-16**: `A` dùng một code unit, 😀 dùng hai code unit (surrogate pair).
- Do có BOM, dòng UTF-16 chứa **8 byte**; hai dòng BE/LE không BOM đều chứa **6 byte**. Không nên suy ra độ dài byte chỉ bằng `String.length() * 2`.

`HexFormat` có từ Java 17; ví dụ dùng nó để kết quả hex có thể so sánh chính xác giữa các máy.

## 5. Java 17+ — Java đọc BOM như thế nào?

Tạo tệp **`Utf16DecodeDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class Utf16DecodeDemo {
    public static void main(String[] args) {
        HexFormat hex = HexFormat.of();
        byte[] beWithBom = hex.parseHex("feff0041");
        byte[] leWithBom = hex.parseHex("fffe4100");
        byte[] leWithoutBom = hex.parseHex("4100");

        System.out.println("BE BOM with UTF-16 = "
                + new String(beWithBom, StandardCharsets.UTF_16));
        System.out.println("LE BOM with UTF-16 = "
                + new String(leWithBom, StandardCharsets.UTF_16));

        String wrong = new String(leWithoutBom, StandardCharsets.UTF_16);
        System.out.println("LE without BOM using UTF-16 = U+"
                + String.format("%04X", wrong.codePointAt(0)));

        System.out.println("LE without BOM using UTF-16LE = "
                + new String(leWithoutBom, StandardCharsets.UTF_16LE));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 Utf16DecodeDemo.java
java Utf16DecodeDemo
```

Đầu ra chính xác:

```text
BE BOM with UTF-16 = A
LE BOM with UTF-16 = A
LE without BOM using UTF-16 = U+4100
LE without BOM using UTF-16LE = A
```

Hai trường hợp có BOM đều cho ra `A` vì `UTF_16` **đọc chữ ký đầu dữ liệu** và chọn đúng BE hoặc LE. Khi không có BOM, `UTF_16` của Java không thể biết rằng `4100` được tạo theo LE, vì vậy nó áp dụng BE mặc định. Đổi sang `UTF_16LE` giải mã đúng.

**Cẩn thận với decoder cố định byte order:** dùng `UTF_16LE` để đọc `FF FE 41 00` có thể đưa `U+FEFF` vào đầu chuỗi vì charset này không có cơ chế tự loại BOM như `UTF_16`. Đừng tự động loại bỏ ký tự đầu chuỗi của mọi file; chỉ xử lý BOM ở vị trí đầu và khi giao thức/charset mong đợi điều đó.

## 6. Ghi và đọc tệp UTF-16LE có BOM theo yêu cầu định dạng

Giả sử một hệ thống ngoài Java bắt buộc file được mã hóa **UTF-16LE và có BOM `FF FE`**. Nếu chỉ dùng:

```java
Files.writeString(path, text, StandardCharsets.UTF_16LE);
```

thì Java ghi nội dung theo LE nhưng **không tự thêm BOM**. Ngược lại, `StandardCharsets.UTF_16` tự phát BOM `FE FF` và dùng BE, **không thay thế được** yêu cầu cụ thể “UTF-16LE có BOM”.

Bạn có thể tự thêm hai byte đầu theo hợp đồng định dạng. Chương trình sau tạo file, đọc lại byte và giải mã:

Tạo tệp **`Utf16LeFileDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class Utf16LeFileDemo {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("sample-utf16le.txt");
        String text = "A";

        byte[] payload = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[payload.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(payload, 0, bytes, 2, payload.length);

        Files.write(path, bytes);

        byte[] saved = Files.readAllBytes(path);
        System.out.println("file bytes = " + HexFormat.of().formatHex(saved));
        System.out.println("decoded = " + Files.readString(path, StandardCharsets.UTF_16));

        String explicitLe = Files.readString(path, StandardCharsets.UTF_16LE);
        System.out.println("fixed LE sees U+FEFF = "
                + (explicitLe.codePointAt(0) == 0xFEFF));
    }
}
```

Chạy ở thư mục mà ứng dụng có quyền tạo/ghi tệp:

```bash
javac -encoding UTF-8 Utf16LeFileDemo.java
java Utf16LeFileDemo
```

Đầu ra chính xác:

```text
file bytes = fffe4100
decoded = A
fixed LE sees U+FEFF = true
```

Đã có BOM LE, nên `Files.readString(path, StandardCharsets.UTF_16)` nhận diện đúng byte order và trả về chuỗi `A`. Với `UTF_16LE` cố định, cặp byte đầu được diễn giải thành code point `U+FEFF` trong chuỗi, nên phép kiểm tra trên trả về `true`.

Ví dụ ghi một file vật lý `sample-utf16le.txt`. Nếu chạy lại trong cùng thư mục, `Files.write` ghi nội dung mới vào tệp đó theo mặc định. Đây là hành vi của chương trình minh họa; file không được tạo chỉ bằng cách đọc bài học này.

## 7. Chọn charset khi dùng `Reader` và `Writer`

`InputStream` / `OutputStream` làm việc với byte; `Reader` / `Writer` làm việc với ký tự. Mã hóa xảy ra tại ranh giới chuyển đổi:

```java
// Đoạn minh họa, giả định inputStream và outputStream đã được tạo.
Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_16);
Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_16LE);
```

Hai dòng trên không phải chương trình độc lập. Trong thực tế, chọn charset theo **dữ liệu thật** và quản lý đóng stream theo quyền sở hữu tài nguyên:

- **Dữ liệu có BOM UTF-16 và cần tự nhận diện BE/LE:** đọc bằng `UTF_16`.
- **Dữ liệu rõ ràng UTF-16BE không BOM:** đọc/ghi bằng `UTF_16BE`.
- **Dữ liệu rõ ràng UTF-16LE không BOM:** đọc/ghi bằng `UTF_16LE`.
- **Định dạng yêu cầu UTF-16LE có BOM:** ghi byte `FF FE` rồi nội dung `UTF_16LE`; khi đọc, dùng `UTF_16` để nhận diện BOM hoặc xử lý bỏ BOM trước khi giải mã cố định LE theo hợp đồng.
- **Định dạng chỉ nói “UTF-16” và chấp nhận byte order tự mô tả bằng BOM:** `UTF_16` của Java là lựa chọn phù hợp với quy tắc encoding của Java.

`Files.readString`, `Files.writeString`, `InputStreamReader` và `OutputStreamWriter` đều có cách cung cấp charset; nên **nêu rõ charset** thay vì dựa vào charset mặc định của máy khi trao đổi dữ liệu giữa các hệ thống.

## 8. Lỗi phổ biến và cách chẩn đoán

### Lỗi 1: Đọc UTF-16LE không BOM bằng `UTF_16` rồi bị sai ký tự

Nguyên nhân: Java `UTF_16` mặc định **BE** khi không nhận ra BOM. Với dữ liệu đã biết LE, chỉ định `StandardCharsets.UTF_16LE`.

### Lỗi 2: Tự thêm BOM rồi lại mã hóa bằng `UTF_16`

`String.getBytes(StandardCharsets.UTF_16)` vốn tự thêm `FE FF`. Nếu còn tự ghi thêm BOM phía trước, bạn có thể tạo byte thừa hoặc đặt thêm dấu `U+FEFF` ở phần văn bản sau dấu BOM đầu. Hãy dùng một chiến lược rõ ràng: **`UTF_16` tự sinh BOM và BE**, hoặc **UTF_16BE/UTF_16LE cố định với BOM được ghi thủ công nếu cần**.

### Lỗi 3: Luôn xóa ký tự đầu của mọi chuỗi nếu nhìn thấy U+FEFF

Không được xóa tùy tiện dữ liệu người dùng. `U+FEFF` có thể là kết quả giải mã chữ ký BOM bằng charset cố định, nhưng cũng là code point có thật trong dữ liệu. Chỉ coi byte đầu là BOM theo quy tắc của định dạng đang đọc; không sửa nội dung chỉ dựa trên cảm giác “ký tự này vô hình”.

### Lỗi 4: Giả định UTF-16 luôn có hai byte mỗi ký tự

Emoji và nhiều code point ngoài BMP cần **hai code unit**, tức **bốn byte** trước khi tính BOM. Ngay cả `String.length()` cũng đếm code unit UTF-16, không đếm code point hay byte.

### Lỗi 5: Giải mã sai nhưng không phát hiện vì có ký tự thay thế

Dữ liệu UTF-16 có số byte lẻ, surrogate không hợp lệ hoặc byte order sai có thể gây lỗi giải mã. Các API tiện lợi kiểu `new String(bytes, charset)` có thể thay chuỗi byte không hợp lệ bằng ký tự thay thế. Nếu cần **phát hiện dữ liệu hỏng thay vì âm thầm thay thế**, dùng `CharsetDecoder` với `CodingErrorAction.REPORT` và xử lý `CharacterCodingException`. Tuy nhiên, một chuỗi sai endian vẫn có thể tạo ra các code point hợp lệ; decoder nghiêm ngặt cũng **không thể tự đoán đúng charset** trong trường hợp đó.

### Lỗi 6: Dựa hoàn toàn vào BOM khi giao thức đã khai báo rõ charset

Không phải file nào cũng có BOM. Nếu metadata nói “UTF-16LE”, cần tuân theo quy định của định dạng về việc BOM có được phép xuất hiện hay không. Với dữ liệu ngoài hệ thống, nên kiểm tra metadata/đặc tả trước, rồi dùng decoder tương ứng và kiểm thử byte thực tế.

## 9. Bảng quyết định nhanh

| Tình huống | Lựa chọn hợp lý |
| --- | --- |
| Có `FE FF` ở đầu, muốn giải mã UTF-16 tự chọn endian | `StandardCharsets.UTF_16` |
| Có `FF FE` ở đầu, muốn giải mã UTF-16 tự chọn endian | `StandardCharsets.UTF_16` |
| Không BOM, phía gửi xác nhận big-endian | `StandardCharsets.UTF_16BE` |
| Không BOM, phía gửi xác nhận little-endian | `StandardCharsets.UTF_16LE` |
| Cần Java tự ghi UTF-16 kèm BOM theo mặc định | `StandardCharsets.UTF_16` (BOM BE) |
| Bắt buộc ghi UTF-16LE kèm BOM `FF FE` | Tự ghi hai byte `FF FE` + payload `UTF_16LE` |
| Cần đối chiếu dữ liệu giữa hệ thống | Ghi nhận dãy byte hex, BOM, charset đã khai báo và code point sau giải mã |

## 10. Tự kiểm tra

**Câu 1.** Byte `00 41` là chữ `A` trong UTF-16BE hay UTF-16LE?

**Đáp án:** UTF-16BE. Cùng chữ `A` trong LE là `41 00`.

**Câu 2.** Tệp bắt đầu `FF FE 41 00`, dùng `StandardCharsets.UTF_16` để đọc thì được gì?

**Đáp án:** `A`. `FF FE` là BOM LE, vì vậy hai byte dữ liệu `41 00` được diễn giải theo LE.

**Câu 3.** `text.getBytes(StandardCharsets.UTF_16LE)` có tự ghi BOM `FF FE` không?

**Đáp án:** Không. Charset `UTF_16LE` xác định thứ tự byte cố định nhưng không tự thêm BOM.

**Câu 4.** Tại sao `new String(new byte[]{0x41, 0x00}, StandardCharsets.UTF_16)` không cho ra `A`?

**Đáp án:** Không có BOM, `UTF_16` của Java mặc định BE nên hai byte là code unit `0x4100`.

**Câu 5.** Chuỗi `A😀` có bao nhiêu code point, bao nhiêu code unit UTF-16 và bao nhiêu byte khi mã hóa bằng `StandardCharsets.UTF_16` của Java?

**Đáp án:** Hai code point; ba code unit; sáu byte nội dung + hai byte BOM BE = **tám byte**.

## Ghi nhớ

- **`UTF_16`:** Java tự ghi BOM `FE FF` khi encode; khi decode dùng BOM để chọn BE/LE, **không có BOM thì mặc định BE**.
- **`UTF_16BE` và `UTF_16LE`:** byte order cố định, **không tự ghi/tiêu thụ BOM theo cơ chế tự nhận diện**.
- **BOM chỉ có ý nghĩa khi giải mã theo đúng hợp đồng của nguồn dữ liệu.** Xem byte gốc, biết charset/định dạng từ đâu và kiểm thử bằng chuỗi có cả ký tự BMP lẫn supplementary để tránh lỗi ẩn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-007">Lỗi mojibake khi encode và decode không cùng charset</a>

<details>
<summary>Xem chi tiết</summary>

# Lỗi mojibake khi encode và decode không cùng charset

## 1. Mojibake là gì?

**Mojibake** là hiện tượng văn bản bị hiển thị thành những ký tự sai, lạ hoặc khó đọc vì **dữ liệu byte được giải mã (decode) bằng charset khác với charset đã dùng để mã hóa (encode)**, hoặc vì một bước chuyển đổi trung gian sử dụng charset không phù hợp.

Ví dụ điển hình:

- Dữ liệu đúng: `café`.
- Mã hóa theo UTF-8: byte `63 61 66 C3 A9`.
- Giải mã nhầm toàn bộ byte bằng ISO-8859-1: hiển thị `cafÃ©`.
- Giải mã **đúng** bằng UTF-8: hiển thị `café`.

Trong chủ đề Java I/O, điều cần ghi nhớ là **String trong bộ nhớ và byte trên đĩa/mạng nằm ở hai tầng khác nhau**. Charset quyết định cách chuyển qua lại giữa hai tầng.

~~~text
String "café"
    |
    | encode UTF-8
    v
Bytes: 63 61 66 C3 A9
    |
    +--- decode UTF-8 --------> "café"   (đúng)
    |
    +--- decode ISO-8859-1 ---> "cafÃ©" (mojibake)
~~~

Từ “mojibake” mô tả **triệu chứng chữ bị sai**, không phải tên của một charset hay một định dạng file riêng. Một văn bản có dấu tiếng Việt biến thành `Ã`, `Â`, `áº`, `á»`... là dấu hiệu thường gặp, nhưng **không thể kết luận chính xác lỗi chỉ từ vẻ ngoài của vài ký tự**.

Mojibake khác với lỗi font: nếu chuỗi ký tự bên trong đã sai vì decode sai, thay font thường không chữa được. Nếu code point đúng nhưng font thiếu glyph, vấn đề thuộc khâu hiển thị; cần kiểm tra để phân biệt.

## 2. Encode, decode và charset phải tương thích

**Encode** là chuyển ký tự Unicode thành byte theo charset; **decode** là chuyển byte thành ký tự theo charset.

Ví dụ Unicode code point của `é` là U+00E9:

| Charset | Byte biểu diễn `é` |
| --- | --- |
| UTF-8 | `C3 A9` |
| ISO-8859-1 | `E9` |
| UTF-16BE | `00 E9` |
| UTF-16LE | `E9 00` |

Một byte có thể mang nghĩa khác tùy charset. Khi decoder ISO-8859-1 nhận `C3 A9`, nó giải thích thành hai ký tự U+00C3 (`Ã`) và U+00A9 (`©`). Khi decoder UTF-8 nhận đúng hai byte đó, nó tạo một code point U+00E9 (`é`). Đây là cơ chế cụ thể đằng sau `é → Ã©`.

**Không có “charset của Java String” theo nghĩa charset file**: String là mô hình ký tự Unicode trong Java. Khi gọi `getBytes(charset)` để đưa String ra file/mạng, hoặc `new String(bytes, charset)` để đọc vào, ta mới chọn cách biến đổi. Java `char` là đơn vị mã UTF-16; `String.length()` đếm đơn vị UTF-16, không phải số byte UTF-8.

Cùng một thông điệp phải có **hợp đồng thống nhất**:

~~~text
Bên ghi                   Bên đọc
String --encode UTF-8--> Bytes --decode UTF-8--> String
~~~

Nếu bên ghi UTF-8 và bên đọc ISO-8859-1 hoặc Windows-1252, dữ liệu ký tự có thể sai dù các byte trong file hoặc gói tin vẫn nguyên vẹn. Nếu bên ghi ISO-8859-1 còn bên đọc UTF-8, byte như `E9` không hợp lệ dưới dạng một ký tự UTF-8 độc lập; decoder có thể báo lỗi hoặc sinh ký tự thay thế tùy API/chính sách.

**Chú ý:** Charset không phải định dạng file. JSON, CSV, XML, text log có quy tắc cú pháp riêng, đồng thời cần encoding để biến byte thành ký tự. Đọc đúng charset vẫn chưa tương đương parse đúng dữ liệu.

## 3. Ví dụ Java 17: tự tạo mojibake và phục hồi khi còn đủ thông tin

Tạo file **MojibakeDemo.java** (lưu mã nguồn dưới UTF-8):

~~~java
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class MojibakeDemo {
    public static void main(String[] args) {
        String original = "café";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);

        String correct = new String(bytes, StandardCharsets.UTF_8);
        String wrong = new String(bytes, StandardCharsets.ISO_8859_1);

        // Chỉ khả thi vì mapping ISO-8859-1 ở bước sai
        // vẫn giữ được giá trị của từng byte và String chưa bị sửa.
        byte[] recoveredBytes =
                wrong.getBytes(StandardCharsets.ISO_8859_1);
        String recovered =
                new String(recoveredBytes, StandardCharsets.UTF_8);

        System.out.println("utf8Hex=" + HexFormat.of().formatHex(bytes));
        System.out.println("correct=" + correct);
        System.out.println("wrong=" + wrong);
        System.out.println("recovered=" + recovered);
        System.out.println("sameBytes=" + Arrays.equals(bytes, recoveredBytes));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 MojibakeDemo.java
java MojibakeDemo
~~~

Kết quả khi terminal hiển thị Unicode đúng:

~~~text
utf8Hex=636166c3a9
correct=café
wrong=cafÃ©
recovered=café
sameBytes=true
~~~

Giải thích từng bước:

1. `getBytes(UTF_8)` mã hóa `café` thành `63 61 66 C3 A9`.
2. `new String(bytes, UTF_8)` hiểu `C3 A9` là một ký tự `é`.
3. `new String(bytes, ISO_8859_1)` hiểu `C3` và `A9` là hai ký tự `Ã©`: mojibake.
4. Vì ISO-8859-1 ánh xạ lần lượt mọi giá trị byte 0–255 thành code point tương ứng, `wrong.getBytes(ISO_8859_1)` tái tạo được byte ban đầu.
5. Giải mã byte khôi phục bằng UTF-8 cho lại `café`. Mệnh đề `sameBytes=true` xác nhận điều này trong ví dụ.

**Không được tổng quát hóa thao tác sửa ở bước 4 cho mọi chuỗi bị lỗi.** Nó chỉ đúng khi biết chính xác lịch sử biến đổi và các byte vẫn được bảo toàn. Nếu hệ thống đã thay ký tự bằng `?`, ký tự thay thế U+FFFD (`�`), xóa dấu, chuẩn hóa hoặc thay đổi chuỗi, phép encode ngược có thể không khôi phục byte gốc.

Lưu ý thêm: in ra terminal cũng là một bước xuất ký tự ra byte. Nếu console chọn encoding/font không phù hợp, nội dung terminal có thể trông sai dù String trong Java hoàn toàn đúng. Nên kiểm tra byte hex và kết quả `equals` bên cạnh hình thức hiển thị.

## 4. Mojibake xuất hiện ở đâu trong luồng Java I/O?

Các lỗi thường xuất hiện tại **ranh giới String ↔ byte**, đặc biệt nếu một bên dùng charset ngầm định:

~~~text
Java String
   -> Writer / OutputStreamWriter / getBytes(charset)
   -> byte trên file, network, queue, DB driver
   -> Reader / InputStreamReader / new String(bytes, charset)
   -> Java String
   -> logger / terminal / browser
~~~

Nếu file ghi UTF-8 nhưng đọc lại bằng charset khác:

~~~java
// Mảnh code trong phương thức đã có Path path, xử lý IOException.
Files.writeString(path, "café", StandardCharsets.UTF_8);

String correct = Files.readString(path, StandardCharsets.UTF_8);
String wrong = Files.readString(path, StandardCharsets.ISO_8859_1);
~~~

`correct` chứa `café`, `wrong` chứa `cafÃ©`. Phương thức `readString` chỉ giải mã file, không tự phát hiện file đã được tạo bằng charset nào.

Đối với stream, đặt charset ở đúng cầu nối:

~~~java
// Mảnh code có InputStream input và Reader đóng theo phạm vi sử dụng.
try (var reader = new java.io.InputStreamReader(
        input, StandardCharsets.UTF_8)) {
    // Đọc các ký tự từ reader.
}
~~~

Chiều ghi dùng `OutputStreamWriter(output, StandardCharsets.UTF_8)`. Nếu làm việc hoàn toàn với byte để copy file, hãy dùng `InputStream`/`OutputStream` trực tiếp; không chuyển đổi sang String.

Với giao tiếp HTTP, phải kiểm tra `Content-Type`, tham số `charset` khi áp dụng, các quy tắc của media type và cách framework/SDK giải mã. Với CSV/export/import và các hệ thống cũ, cấu hình charset của bên phát và bên đọc phải nhất quán. **Đừng tự gán ISO-8859-1 hoặc UTF-8 chỉ vì hệ thống “thường dùng” charset đó.**

## 5. Các dấu hiệu và giới hạn của việc suy đoán lỗi

| Triệu chứng | Khả năng có thể xảy ra | Cần kiểm tra |
| --- | --- | --- |
| `é` thành `Ã©`; `à` thành `Ã ` | UTF-8 bị hiểu thành charset Latin-1/Windows-1252 | Byte nguồn, charset tại bước decode |
| Một số ký tự thành `�` | Decoder đã thay chuỗi byte lỗi bằng U+FFFD | Byte trước decode; chính sách xử lý lỗi |
| Chữ không hỗ trợ thành `?` | Encoder thay ký tự không biểu diễn được bằng `?` | Charset lúc encode và byte `3F` |
| Ký tự đầu file là `ï»¿` | BOM UTF-8 bị hiểu như ký tự Latin-1 | Ba byte đầu `EF BB BF`, chính sách BOM |
| Đúng trong file, sai trên terminal | Encoding hoặc font của console | Byte thực tế và String trước lúc in |
| Chữ đúng nhưng dấu hiển thị/ghép bất thường | Có thể là normalization, font/rendering hoặc thứ tự dấu | Code point thực tế, Unicode normalization |

Bảng trên là **manh mối chẩn đoán**, không phải bằng chứng quyết định: nhiều chuỗi lỗi khác nhau có thể có biểu hiện giống nhau. Ví dụ `Ã©` có thể đã được lưu *như nội dung thật* bởi một hệ thống trước đó; khi đó file UTF-8 hiện tại giải mã đúng nhưng bản thân văn bản đã bị làm sai từ bước trước.

**Mojibake và dữ liệu bị mất là hai vấn đề khác nhau:**

- Nếu byte gốc còn nguyên ở file/log/protocol, thường có thể đọc lại bằng charset đúng.
- Nếu chỉ còn String `cafÃ©` nhưng biết quá trình decode nhầm ISO-8859-1, đôi khi có thể tạo lại byte như ví dụ.
- Nếu hệ thống đã thay thế byte/ký tự không biểu diễn được bằng `?` hoặc `�`, nhiều giá trị ban đầu có thể rơi vào cùng một ký tự thay thế; việc phục hồi chính xác thường không thể bảo đảm mà không có bản gốc.
- Nếu dữ liệu đã trải qua nhiều lần encode/decode sai, cần dựng lại *từng bước* trước khi thử sửa; áp dụng công thức chung một cách mù quáng dễ làm hỏng dữ liệu lần nữa.

## 6. Ví dụ về mất dữ liệu: ký tự không nằm trong charset đích

Dùng US-ASCII để mã hóa `é` là sai nếu cần giữ nguyên dấu, vì US-ASCII không biểu diễn được code point đó. Một số API tiện lợi có thể dùng byte thay thế.

~~~java
// Mảnh code minh họa; StandardCharsets đã được import.
String original = "é";
byte[] bytes = original.getBytes(StandardCharsets.US_ASCII);
String after = new String(bytes, StandardCharsets.US_ASCII);

System.out.println(java.util.HexFormat.of().formatHex(bytes));
System.out.println(after);
~~~

Kết quả mặc định của `String.getBytes(StandardCharsets.US_ASCII)` với ký tự không biểu diễn được:

~~~text
3f
?
~~~

Byte `3F` biểu diễn dấu hỏi `?`. Không thể biết ký tự ban đầu là `é`, `€` hay một ký tự khác chỉ từ byte `3F`. Đây là **mất mát ở bước encode**, khác với trường hợp byte UTF-8 còn nguyên nhưng bị decode sai.

Khi cần **phát hiện** thay vì âm thầm thay thế, dùng `CharsetEncoder`/`CharsetDecoder` với `CodingErrorAction.REPORT`.

Tạo **StrictDecodeDemo.java**:

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class StrictDecodeDemo {
    public static void main(String[] args) {
        // E9 là 'é' trong ISO-8859-1 nhưng không tự tạo
        // thành một ký tự UTF-8 hợp lệ khi đứng một mình.
        byte[] sourceBytes = {(byte) 0xE9};

        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(sourceBytes));
            System.out.println("valid UTF-8");
        } catch (CharacterCodingException exception) {
            System.out.println("invalid UTF-8");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 StrictDecodeDemo.java
java StrictDecodeDemo
~~~

Stdout:

~~~text
invalid UTF-8
~~~

Một byte `E9` không đủ tạo chuỗi UTF-8 hợp lệ; decoder báo lỗi. Thay vì tiếp tục xử lý dữ liệu bị thay thế ký tự, cách này cho phép chương trình dừng và báo sai hợp đồng encoding sớm.

**Lưu ý:** kiểm tra UTF-8 hợp lệ chỉ trả lời “có giải mã được theo UTF-8 hay không”. Nó không chứng minh file chắc chắn là văn bản, vì dữ liệu nhị phân bất kỳ có thể tình cờ chứa byte UTF-8 hợp lệ.

## 7. Double encoding: vì sao đôi khi xuất hiện nhiều tầng ký tự sai?

Mojibake có thể bị **lưu lại như một String** rồi encode thành file UTF-8 mới. Khi đó file mới là UTF-8 hợp lệ, nhưng nội dung lại là các ký tự sai của lần decode trước.

Ví dụ một ký tự `é`:

~~~text
Bản gốc:           é
UTF-8 gốc:         C3 A9
Decode nhầm Latin-1: Ã©
Lưu "Ã©" bằng UTF-8: C3 83 C2 A9
Đọc file mới bằng UTF-8: Ã©  (vẫn sai về ngữ nghĩa)
~~~

Nếu lại tiếp tục diễn giải sai những byte UTF-8 đó, chuỗi có thể méo thêm một tầng nữa. Đây là lý do có những log hoặc bản export chứa các cụm `Ãƒ`, `Â` lặp lại. Mỗi tầng xử lý sai cần được xác định từ log/byte thực tế; chỉ đổi charset của bước đọc cuối sẽ không tự sửa nội dung đã lưu sai ở bước trước.

Khi đọc một file xuất hiện `cafÃ©` nhưng byte của file hiện tại là mã hóa UTF-8 của chính chuỗi `cafÃ©`, ứng dụng đã đọc **đúng** byte hiện tại. Lỗi xảy ra trước khi lưu, nên cần truy về thời điểm String bị tạo từ byte bằng charset sai.

## 8. Quy trình điều tra và sửa lỗi có kiểm soát

**Bước 1 — Lưu lại byte gốc.** Trước khi sửa, giữ file gốc hoặc bản sao bất biến. Tránh mở rồi Save bằng text editor với charset phỏng đoán. Với dữ liệu mạng, lưu payload byte hoặc hex dump tại ranh giới I/O phù hợp.

**Bước 2 — Xác định nơi text còn đúng.** So sánh String trước encode, byte sau encode, byte trước decode, String sau decode, và cuối cùng là nơi hiển thị. Nếu String đúng trước `System.out.println` nhưng terminal sai, đừng sửa logic đọc file vội.

**Bước 3 — Đo byte và charset thực sự.** Dùng `HexFormat` trong Java 17 để xem byte; xác minh `StandardCharsets.UTF_8`, `ISO_8859_1`, charset do API/framework sử dụng, HTTP `charset`, thiết lập xuất CSV, decoder/encoder của bên tích hợp. Không dùng `String.length()` làm số byte.

**Bước 4 — Tái hiện với dữ liệu nhỏ.** Một từ như `café`, `cà phê` hoặc tên có dấu đủ giúp tách lỗi. Đối chiếu cả chuỗi hiển thị và hex của byte trước/sau mỗi bước.

**Bước 5 — Sửa tại ranh giới sai.** Nếu file UTF-8 bị đọc như Latin-1, thay lựa chọn charset ở *bước đọc*. Nếu nguồn tạo byte sai, sửa encoder/thiết lập export. Nếu file đã chứa String mojibake được lưu dưới UTF-8, phục hồi từ bản gốc hoặc xác minh lịch sử biến đổi trước khi lập công cụ migration.

**Bước 6 — Kiểm chứng và phòng ngừa.** Kiểm tra round-trip ký tự tiếng Việt, dấu, ký tự ngoài ASCII và trường hợp malformed input. So sánh byte khi yêu cầu không thay đổi dữ liệu; với text, kiểm tra chuỗi Unicode sau parse. Đặt test tích hợp qua đúng luồng (file → service → API → consumer) để bắt mismatch giữa các hệ thống.

Một cách chẩn đoán chủ động trong Java:

~~~java
// Mảnh code khi đã có byte[] payload và String candidate.
String hex = java.util.HexFormat.of().formatHex(payload);
String decoded = new String(payload, StandardCharsets.UTF_8);
boolean same = candidate.equals(decoded);
// Ghi hex/charset vào log kỹ thuật phù hợp, không log dữ liệu nhạy cảm.
~~~

Hex chỉ mô tả byte; nó không tự cho biết byte cần được giải mã bằng charset nào. Khi sản phẩm phải xử lý dữ liệu người dùng, cần cân nhắc không ghi nội dung cá nhân hoặc payload nhạy cảm vào log.

## 9. Thiết kế ứng dụng tránh mojibake

- **Chỉ định charset ở điểm chuyển đổi.** Với text UTF-8 dùng `StandardCharsets.UTF_8` cùng `Files.readString`, `Files.writeString`, `InputStreamReader`, `OutputStreamWriter` hoặc `String.getBytes`/`new String`.
- **Định nghĩa rõ encoding trong hợp đồng tích hợp.** File CSV, email, API HTTP, queue message, database import/export cần thỏa thuận charset và metadata tương ứng.
- **Phân biệt text và binary.** Đối với file ảnh, ZIP hoặc payload nhị phân, truyền byte trực tiếp; đừng dùng String làm container tùy ý cho byte.
- **Ưu tiên Unicode xuyên suốt.** Giữ String ở tầng nghiệp vụ, chỉ encode khi ra I/O và chỉ decode khi vào I/O; tránh encode/decode thừa không có mục đích.
- **Không phụ thuộc charset mặc định một cách vô thức.** Java hiện đại có thay đổi về mặc định giữa phiên bản và môi trường; charset được chỉ định rõ sẽ dễ kiểm soát hơn.
- **Báo lỗi khi yêu cầu bảo toàn dữ liệu.** Dùng `CharsetDecoder`/`CharsetEncoder` với `CodingErrorAction.REPORT` khi cần từ chối đầu vào sai hoặc ký tự không biểu diễn được.
- **Thử với dữ liệu đa ngôn ngữ.** Đừng chỉ test ASCII: ASCII có cùng byte trong UTF-8 và nhiều charset khác, nên không phát hiện mismatch; thêm tiếng Việt, ký tự có dấu và trường hợp không thuộc charset legacy.
- **Tránh “sửa” dữ liệu bằng phép chuyển đổi mù.** Trước khi encode lại một String sai, xác minh charset gốc, bước hỏng, khả năng đảo ngược và ảnh hưởng đến các bản ghi vốn đã đúng.

## 10. Bài tập tự kiểm tra

**Câu 1.** Dữ liệu đúng là `café`; sau khi nhận được `cafÃ©` thì có thể khẳng định bộ dữ liệu gốc đã mất không?  
**Đáp án:** Chưa. Nếu byte UTF-8 `63 61 66 C3 A9` còn nguyên và chỉ decode nhầm ISO-8859-1, có thể decode lại đúng. Nếu ký tự đã bị thay thế và byte gốc không còn thì có thể đã mất thông tin.

**Câu 2.** Vì sao `new String(bytes, StandardCharsets.UTF_8)` chỉ đúng khi biết `bytes` là UTF-8?  
**Đáp án:** Vì decoder diễn giải byte theo quy tắc UTF-8. Nếu byte được encode bằng charset khác, kết quả có thể sai hoặc phát sinh lỗi; constructor không tự tìm ra charset gốc.

**Câu 3.** Ký tự `é` được encode bằng US-ASCII thành `?`. Encode lại `?` bằng UTF-8 có sửa được không?  
**Đáp án:** Không. Ký tự gốc đã bị thay thế thành `?` ở bước encode đầu; UTF-8 chỉ mã hóa đúng ký tự `?` hiện có.

**Câu 4.** File UTF-8 hiện tại chứa chuỗi `cafÃ©`. Đổi `readString(path, UTF_8)` thành `readString(path, ISO_8859_1)` có luôn sửa được không?  
**Đáp án:** Không. File có thể đã lưu đúng byte UTF-8 của chuỗi mojibake từ một bước trước. Phải lần theo lịch sử dữ liệu, xác định byte gốc và charset của từng bước.

**Câu 5.** Vì sao nên kiểm tra hex byte thay vì chỉ nhìn chữ hiển thị?  
**Đáp án:** Console/font có thể khiến chữ trông sai ngay cả khi String đúng; hex cho biết byte thực tế đã đi qua ranh giới I/O, giúp xác định encode hay decode sai.

**Câu 6.** Khi cần từ chối byte sai UTF-8 thay vì hiển thị `�`, nên dùng API nào?  
**Đáp án:** `CharsetDecoder` với `CodingErrorAction.REPORT`, rồi xử lý `CharacterCodingException`.

**Kết luận học tập:** Mojibake thường xuất hiện khi bên ghi và bên đọc **không dùng cùng charset**, hoặc vì một khâu trung gian đã lưu lại String bị giải mã sai. Muốn sửa chính xác, phải phân biệt **byte gốc**, **String đã giải mã**, **charset ở từng ranh giới I/O** và khả năng **mất dữ liệu do thay thế ký tự**. Với Java, ưu tiên charset tường minh và kiểm tra byte/round-trip trước khi can thiệp dữ liệu thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-008">Malformed input, unmappable character và chính sách xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Malformed input, unmappable character và chính sách xử lý trong Java

Trong ứng dụng Java, chuyển đổi văn bản giữa **byte** và **ký tự** không phải lúc nào cũng thành công. Hai loại lỗi quan trọng của Java Charset API là **malformed input** (chuỗi đơn vị đầu vào không hợp lệ theo quy tắc mã hóa) và **unmappable character** (dữ liệu đầu vào hợp lệ, nhưng không có biểu diễn tương ứng trong bộ mã đích). Java cho phép quyết định **báo lỗi**, **thay thế** hoặc **bỏ qua** khi gặp từng loại lỗi.

Bài này thuộc phần **Mã hóa văn bản** của Java I/O. Mục tiêu là phân biệt chính xác hai loại lỗi, hiểu thời điểm chúng xuất hiện trong quá trình encode/decode, cấu hình `CharsetDecoder` / `CharsetEncoder` với `CodingErrorAction` và chọn chính sách phù hợp để tránh âm thầm mất hoặc làm sai dữ liệu.

## 1. Đặt đúng ranh giới: byte, UTF-16 và Charset

Các đối tượng Java như `String`, `char[]` và `CharBuffer` chứa các **UTF-16 code unit**. Trong khi đó, file, socket hoặc `ByteBuffer` chứa **byte**. Charset quyết định cách biến đổi hai dạng biểu diễn:

```text
DECODE (giải mã):
byte[] / ByteBuffer -- CharsetDecoder (ví dụ UTF-8) --> char / CharBuffer / String

ENCODE (mã hóa):
String / CharBuffer -- CharsetEncoder (ví dụ US-ASCII) --> byte[] / ByteBuffer
```

Một `char` của Java dài 16 bit. Ký tự Unicode ngoài BMP như `😊` là **hai UTF-16 code unit** tạo thành một surrogate pair. Charset encoder đọc các code unit và phải hiểu đúng các cặp này để tạo byte. Không thể giả sử mọi `char` đều là một Unicode code point, cũng như không thể giả sử mỗi code point chỉ có một byte.

Trong Java, các API chính là:

- `java.nio.charset.Charset`: mô tả bộ mã ký tự, chẳng hạn `StandardCharsets.UTF_8` hay `StandardCharsets.US_ASCII`.
- `CharsetDecoder`: diễn giải byte thành ký tự.
- `CharsetEncoder`: diễn giải ký tự thành byte.
- `CoderResult`: kết quả thấp tầng khi mã hóa/giải mã từng phần; phân biệt thành công, thiếu dữ liệu, thiếu không gian hoặc lỗi.
- `CodingErrorAction`: quy định cách xử lý **mỗi nhóm lỗi** gồm `REPORT`, `REPLACE` và `IGNORE`.
- `MalformedInputException` và `UnmappableCharacterException`: các ngoại lệ báo loại lỗi tương ứng khi sử dụng chế độ `REPORT` và phương thức one-shot phù hợp.

Các bộ mã như UTF-8 và US-ASCII là hai ví dụ giúp nhìn rõ hai lỗi, nhưng nguyên tắc xử lý áp dụng cho nhiều charset khác.

## 2. Malformed input: dữ liệu đầu vào không hợp lệ

**Malformed input** nghĩa là các byte hoặc các UTF-16 code unit ở phía đầu vào **vi phạm quy tắc cấu trúc** của bộ mã đang dùng. Nội dung đó không phải một chuỗi mã hóa hợp lệ để xử lý đầy đủ.

### Khi giải mã (byte → char)

Giả sử chương trình giải mã theo UTF-8. Byte `0xC3` mở đầu một chuỗi UTF-8 gồm hai byte, nhưng byte kế tiếp `0x28` (dấu `(`) **không phải continuation byte** hợp lệ. Cặp `C3 28` do đó có lỗi cấu trúc.

Ví dụ dãy byte:

```text
43 C3 28 44
 C  ?  (  D
```

- `43` là chữ `C` trong ASCII/UTF-8.
- `C3` cần thêm một continuation byte hợp lệ nhưng không có, nên đây là **malformed input**.
- `28` tự nó là ký tự `(` hợp lệ trong UTF-8, và bộ giải mã có thể xử lý tiếp sau phần đầu vào hỏng tùy thuật toán, như minh họa ở ví dụ Java phía dưới.
- `44` là chữ `D` hợp lệ.

Các ví dụ malformed UTF-8 khác gồm continuation byte đứng lẻ, chuỗi mã hóa quá dài (overlong), byte mở đầu không hợp lệ, dữ liệu kết thúc khi chuỗi nhiều byte chưa hoàn thành và byte sequence mã hóa surrogate/code point vượt giới hạn Unicode.

Không nên khắc phục bằng cách xem mỗi byte là `char` hay tự bỏ byte có bit cao. Cần phân tích đúng charset và chính sách lỗi.

### Khi mã hóa (char → byte)

Malformed input cũng có thể phát sinh ở **encoder**. Một UTF-16 **high surrogate** không được theo sau bởi low surrogate tương ứng (hoặc low surrogate đứng riêng) là chuỗi UTF-16 không hợp lệ. Ví dụ `"\uD83D!"` chứa high surrogate đơn lẻ rồi đến `!`. Dù bộ mã đích là UTF-8, encoder cũng không thể biến chuỗi UTF-16 hỏng này thành Unicode text hợp lệ.

Điều cần phân biệt: ký tự `😊` có **hai** `char` vẫn là hợp lệ khi chúng tạo thành một surrogate pair đúng thứ tự. Không được coi “hai char cho một ký tự” là lỗi.

## 3. Unmappable character: đầu vào hợp lệ nhưng không biểu diễn được

**Unmappable** nghĩa là dữ liệu đầu vào đã hợp lệ theo dạng nguồn, nhưng **bộ mã đích không thể biểu diễn ý nghĩa ký tự đó**.

Ví dụ quen thuộc:

- `"Café"` là chuỗi Java hợp lệ.
- `US-ASCII` chỉ biểu diễn các ký tự có code point `U+0000..U+007F`.
- Chữ `é` (U+00E9) nằm ngoài phạm vi US-ASCII, nên mã hóa `"Café"` sang US-ASCII gặp **unmappable character**; nó **không phải** malformed UTF-16.

**Decoder cũng có thể báo unmappable** nếu một byte sequence hợp lệ theo cấu trúc của charset nhưng charset đó không ánh xạ nó sang ký tự. Việc một dãy byte cụ thể được phân loại malformed hay unmappable phụ thuộc **quy tắc charset và bộ mã hóa/giải mã**, không thể kết luận chỉ từ một byte “lớn hơn 127”. Trong UTF-8, các byte sequence không hợp lệ thường được báo malformed.

### So sánh hai loại lỗi

| Trường hợp | Malformed input | Unmappable character |
|---|---|---|
| Bản chất | Đầu vào vi phạm **cú pháp/cấu trúc** của cách biểu diễn | Đầu vào **hợp lệ**, nhưng không có ánh xạ sang biểu diễn đích |
| Ví dụ decoder | UTF-8 `C3 28` | Dãy byte hợp lệ nhưng không ánh xạ sang Unicode trong một charset cụ thể |
| Ví dụ encoder | UTF-16 high surrogate đứng lẻ khi chuyển sang UTF-8 | `é` khi chuyển từ Java text sang US-ASCII |
| Kết quả khi `REPORT` | `MalformedInputException` | `UnmappableCharacterException` |
| Thông số cấu hình | `onMalformedInput(...)` | `onUnmappableCharacter(...)` |
| Rủi ro nếu thay/bỏ | Che giấu byte đầu vào bị hỏng | Mất ký tự gốc khi chuyển sang bộ mã không hỗ trợ |

Đừng dùng `UnmappableCharacterException` làm tên gọi chung cho mọi “lỗi Unicode”, hoặc dùng `MalformedInputException` để chỉ việc font không hiển thị được emoji. **Thiếu glyph trong font** là vấn đề hiển thị, khác với việc charset encode/decode thành công hay không.

## 4. Ba chính sách xử lý: REPORT, REPLACE, IGNORE

`java.nio.charset.CodingErrorAction` định nghĩa ba lựa chọn dùng riêng cho **malformed** và **unmappable**:

| Chính sách | Hành vi | Hệ quả |
|---|---|---|
| `REPORT` | Báo lỗi cho caller; trong one-shot encode/decode thông thường sẽ ném `CharacterCodingException` tương ứng | Phát hiện dữ liệu sai và giữ caller chủ động xử lý |
| `REPLACE` | Thay chuỗi đầu vào gây lỗi bằng chuỗi hoặc byte thay thế đã cấu hình | Cho phép tiếp tục nhưng có thể **mất thông tin gốc** |
| `IGNORE` | Bỏ phần đầu vào gây lỗi, tiếp tục với phần còn lại | Dễ gây **mất dữ liệu âm thầm** và thay đổi nội dung |

**Giá trị mặc định của `CharsetDecoder` và `CharsetEncoder` mới tạo là `REPORT` cho cả hai loại lỗi.** Nhưng các API tiện ích bọc quanh charset có thể chọn mặc định **thay thế**. Vì thế phải kiểm tra hợp đồng của API đang gọi; đừng suy ra mọi cách chuyển mã đều có chung chính sách.

Cấu hình thông qua:

```java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
```

Đây là **đoạn minh họa**, đặt trong lớp có import `java.nio.charset.*` hoặc import các kiểu cần dùng. Hai phép cấu hình **độc lập**. Ví dụ có thể `REPORT` với malformed nhưng `REPLACE` với unmappable khi chuyển sang một charset cũ, nếu nghiệp vụ quy định đúng như vậy. Tuy nhiên, mọi cách thay/bỏ dữ liệu đều cần giải thích rõ với người dùng hoặc gắn cảnh báo/log nếu tính toàn vẹn quan trọng.

### REPLACE thực sự thay bằng gì?

- `CharsetDecoder` thường dùng ký tự thay thế Unicode `U+FFFD` (hiển thị như `�`). Có thể đổi chuỗi thay thế bằng `decoder.replaceWith("...")` nếu hợp lệ theo các giới hạn của decoder.
- `CharsetEncoder` thay bằng một hoặc nhiều **byte**; `US_ASCII.newEncoder()` thường dùng byte `?` (`0x3F`) làm replacement. Có thể cấu hình `encoder.replaceWith(byte[])` nhưng byte thay thế phải được charset chấp nhận theo hợp đồng của encoder.
- Việc thấy `?` hoặc `�` trong kết quả **không chứng minh** chính xác dữ liệu gốc bị lỗi gì: ký tự này cũng có thể vốn tồn tại trong nội dung gốc.

Sau khi thay thế, **không thể tự khôi phục** byte hoặc ký tự đã mất chỉ từ kết quả. `IGNORE` còn nguy hiểm hơn vì không giữ lại cả dấu vết của phần bị lỗi.

## 5. Ví dụ 1 — malformed UTF-8 dưới ba chính sách

Tạo tệp `MalformedUtf8Demo.java` (Java 17+):

```java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class MalformedUtf8Demo {
    private static String decode(byte[] bytes, CodingErrorAction action)
            throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(action)
                .onUnmappableCharacter(action)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = {'C', (byte) 0xC3, '(', 'D'};
        try {
            decode(bytes, CodingErrorAction.REPORT);
        } catch (CharacterCodingException e) {
            System.out.println("REPORT=" + e.getClass().getSimpleName());
        }
        String replaced = decode(bytes, CodingErrorAction.REPLACE);
        System.out.println("REPLACE=" + replaced.replace("\uFFFD", "<U+FFFD>"));
        System.out.println("IGNORE=" + decode(bytes, CodingErrorAction.IGNORE));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 --release 17 MalformedUtf8Demo.java
java MalformedUtf8Demo
```

Kết quả đã kiểm tra:

```text
REPORT=MalformedInputException
REPLACE=C<U+FFFD>(D
IGNORE=C(D
```

Ở đây `C3` là byte có vấn đề. Khi `REPORT`, `CharsetDecoder.decode(ByteBuffer)` báo `MalformedInputException`, là lớp con của `CharacterCodingException`. Khi `REPLACE`, phần malformed được thay bằng `U+FFFD`; mã nguồn **chỉ đổi U+FFFD thành chuỗi `<U+FFFD>` lúc in** để kết quả không phụ thuộc font terminal. Khi `IGNORE`, phần malformed bị loại khỏi kết quả.

Trong một bộ dữ liệu khác, số byte bị xem là cùng một lỗi và cách bộ giải mã phục hồi tiếp sau lỗi sẽ phụ thuộc charset/sequence cụ thể. Không nên tổng quát hóa quy tắc “bỏ đúng một byte” từ ví dụ này.

Một decoder có trạng thái; mỗi lượt xử lý độc lập trong ví dụ dùng `newDecoder()` để không tái sử dụng trạng thái sau lỗi hoặc sau khi giải mã hoàn tất.

## 6. Ví dụ 2 — unmappable US-ASCII và surrogate bị lỗi

Tạo tệp `EncoderActionsDemo.java` (Java 17+):

```java
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class EncoderActionsDemo {
    private static String ascii(String input, CodingErrorAction action)
            throws CharacterCodingException {
        CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder()
                .onMalformedInput(action)
                .onUnmappableCharacter(action);
        ByteBuffer bytes = encoder.encode(CharBuffer.wrap(input));
        byte[] result = new byte[bytes.remaining()];
        bytes.get(result);
        return new String(result, StandardCharsets.US_ASCII);
    }

    public static void main(String[] args) throws Exception {
        String text = "Caf\u00E9";
        try {
            ascii(text, CodingErrorAction.REPORT);
        } catch (CharacterCodingException e) {
            System.out.println("ASCII REPORT=" + e.getClass().getSimpleName());
        }
        System.out.println("ASCII REPLACE=" + ascii(text, CodingErrorAction.REPLACE));
        System.out.println("ASCII IGNORE=" + ascii(text, CodingErrorAction.IGNORE));

        String broken = "\uD83D!";
        try {
            StandardCharsets.UTF_8.newEncoder()
                    .encode(CharBuffer.wrap(broken));
        } catch (CharacterCodingException e) {
            System.out.println("UTF-8 broken surrogate=" + e.getClass().getSimpleName());
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 EncoderActionsDemo.java
java EncoderActionsDemo
```

```text
ASCII REPORT=UnmappableCharacterException
ASCII REPLACE=Caf?
ASCII IGNORE=Caf
UTF-8 broken surrogate=MalformedInputException
```

Với `"Café"`, chữ `é` là Unicode hợp lệ nhưng US-ASCII không có biểu diễn, vì vậy `REPORT` ném `UnmappableCharacterException`. Trong Java US-ASCII encoder này, `REPLACE` dùng byte `?`; `IGNORE` xóa chữ `é` khỏi đầu ra. Việc biến `"Café"` thành `"Caf?"` hoặc `"Caf"` rõ ràng là **biến đổi mất mát**.

Biến `broken` chứa high surrogate `\uD83D` không đi cùng low surrogate. Đó là **malformed input của encoder**, kể cả khi charset đích là UTF-8 có thể biểu diễn mọi Unicode scalar value hợp lệ. Không thể “sửa” lỗi này chỉ bằng cách đổi mã đích từ ASCII sang UTF-8.

**Lưu ý về mã nguồn:** Java biên dịch escape `\u00E9` thành chữ `é`, còn `\uD83D` cố ý tạo high surrogate đơn lẻ để minh họa dữ liệu UTF-16 hỏng; đừng đem chuỗi `broken` vào luồng dữ liệu thật rồi coi đó là text hợp lệ.

## 7. Ví dụ 3 — chính sách lỗi thay đổi theo API Reader

Một nhầm lẫn thường thấy: cấu hình `CharsetDecoder.newDecoder()` mặc định `REPORT`, rồi suy ra mọi `Reader` dùng UTF-8 cũng mặc định báo lỗi. **Không đúng với mọi constructor**.

Tạo tệp `ReaderPolicyDemo.java`:

```java
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;

public class ReaderPolicyDemo {
    public static void main(String[] args) throws Exception {
        byte[] bytes = {'A', (byte) 0xC3, '(', 'B'};
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            StringWriter output = new StringWriter();
            reader.transferTo(output);
            System.out.println("simple reader="
                    + output.toString().replace("\uFFFD", "<U+FFFD>"));
        }

        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT))) {
            reader.transferTo(new StringWriter());
        } catch (MalformedInputException e) {
            System.out.println("strict reader=" + e.getClass().getSimpleName());
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 ReaderPolicyDemo.java
java ReaderPolicyDemo
```

```text
simple reader=A<U+FFFD>(B
strict reader=MalformedInputException
```

Hai đoạn đọc **cùng một dãy byte**. Constructor `InputStreamReader(InputStream, Charset)` xử lý malformed/unmappable bằng cơ chế thay thế; constructor `InputStreamReader(InputStream, CharsetDecoder)` dùng **decoder do người gọi cấu hình**, nên `REPORT` được giữ và ngoại lệ xuất hiện **khi đọc**, cụ thể trong `transferTo`.

Nếu ứng dụng cần *đọc file thất bại ngay khi có văn bản không hợp lệ*, nên chọn đường đọc/cấu hình có `REPORT`. Nếu ứng dụng là trình xem file có chủ đích cho phép ký tự thay thế, `REPLACE` có thể hợp lý. Đừng để constructor tiện dụng vô tình quyết định chính sách bảo toàn dữ liệu quan trọng.

Đối với `InputStreamReader`, việc đóng `Reader` cũng đóng `InputStream` bên dưới. Lỗi chuyển mã có thể ném trong vòng lặp đọc hoặc khi hoàn tất đọc, không nhất thiết ngay lúc tạo đối tượng reader.

## 8. Một số API “tiện dụng” có mặc định khác nhau

| API hoặc cách gọi | Chính sách / điểm cần kiểm tra |
|---|---|
| `charset.newDecoder().decode(buffer)` | Decoder mới mặc định `REPORT` cho malformed và unmappable; có thể đổi trước khi decode |
| `charset.newEncoder().encode(buffer)` | Encoder mới mặc định `REPORT` cho hai loại lỗi; có thể cấu hình từng loại |
| `new String(bytes, charset)` | Java API thay các chuỗi byte malformed/unmappable bằng replacement; không dùng khi yêu cầu phát hiện mọi lỗi |
| `string.getBytes(charset)` | Java API thay chuỗi ký tự không mã hóa được bằng replacement byte; không phải đường báo lỗi strict |
| `new InputStreamReader(input, charset)` | Xử lý malformed/unmappable bằng replacement |
| `new InputStreamReader(input, decoder)` | Giữ chính sách do `CharsetDecoder` được truyền vào |
| `new OutputStreamWriter(output, charset)` | Xử lý malformed/unmappable bằng replacement |
| `new OutputStreamWriter(output, encoder)` | Giữ chính sách do `CharsetEncoder` được truyền vào |

**Đừng nhầm “cho phép ghi xong” với “dữ liệu đã được ghi chính xác”.** Ví dụ `text.getBytes(StandardCharsets.US_ASCII)` có thể hoàn tất thành công với một byte `?` thay cho `é`. Nếu muốn xác nhận lossless khi chuyển đổi giữa bộ mã, cần dùng encoder/decoder strict hoặc kiểm tra đầu vào/khả năng biểu diễn một cách có kiểm soát.

Với `Files.newBufferedReader(path, charset)`, `Files.readString(path, charset)` và các API file bậc cao khác, hãy đọc contract về malformed/unmappable của **chính phương thức đó**. Khi chính sách phải kiểm soát chặt, dùng `InputStreamReader` cùng `CharsetDecoder` tự cấu hình là cách biểu đạt ý định rõ ràng.

## 9. Cấu hình REPLACE tường minh và lựa chọn ký tự thay thế

Để decoder dùng `?` thay `U+FFFD` cho lỗi malformed (khi cấu hình cho phép):

```java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .replaceWith("?");
```

Đây là **mã minh họa**: cần import các lớp `CharsetDecoder`, `CodingErrorAction` và `StandardCharsets`. Chuỗi replacement của decoder phải hợp lệ về độ dài theo `maxCharsPerByte()`, không thể truyền chuỗi dài tùy ý. Nó được chèn vào **luồng ký tự đầu ra**, không phải byte.

Đối với encoder US-ASCII, có thể yêu cầu thay ký tự unmappable bằng byte đại diện cho `?`:

```java
CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .replaceWith(new byte[] {(byte) '?'});
```

Đây cũng là **mã minh họa**, cần import `CharsetEncoder`, `CodingErrorAction` và `StandardCharsets`. Mảng replacement phải hợp lệ cho charset, và có giới hạn kích thước. Thực tế có thể đặt `REPLACE` cho unmappable nhưng `REPORT` cho malformed để tránh bỏ qua cấu trúc Unicode hỏng trong khi hỗ trợ chuyển mã theo chính sách mất mát đã thỏa thuận.

**Cảnh báo:** Dùng dấu `?` làm replacement khiến không phân biệt được dấu hỏi hợp lệ vốn có trong text và dấu hỏi do chuyển mã lỗi. Đừng viết thuật toán “đếm dấu `?` để biết số lỗi” nếu không có thông tin bổ sung.

## 10. Khi nào chọn chính sách nào?

### REPORT — ưu tiên khi yêu cầu toàn vẹn dữ liệu

Chọn **`REPORT`** cho tình huống dữ liệu sai cần được phát hiện và xử lý:

- File cấu hình, dữ liệu lưu trữ nguồn, gói tin theo giao thức quy định UTF-8 nghiêm ngặt.
- Định danh, tên trường, mã ký, khóa tra cứu hoặc nội dung mà thay một ký tự có thể đổi ý nghĩa.
- Luồng nhập dữ liệu (ingest) cần báo chất lượng, ghi nhận lỗi và cho phép xử lý lại từ **byte gốc**.
- Khi chuyển sang một charset đích không đủ biểu diễn text, caller phải quyết định dùng UTF-8, từ chối thao tác, hoặc một phép chuyển đổi đã được phê duyệt.

Nên bắt `MalformedInputException` và `UnmappableCharacterException` ở biên mà chương trình có ngữ cảnh để xử lý. Ngoại lệ mang **độ dài đoạn lỗi** qua `inputLength()`; nó không tự cung cấp đầy đủ file path, vị trí byte tuyệt đối hay byte gốc. Hãy bổ sung ngữ cảnh một cách an toàn từ nguồn mà ứng dụng sở hữu.

Đối với xử lý incremental, `CharsetDecoder.decode(input, output, endOfInput)` có thể trả `CoderResult.malformedForLength(n)` hoặc `unmappableForLength(n)` thay vì trực tiếp ném ngoại lệ; caller phải kiểm tra kết quả và có thể gọi `throwException()` nếu muốn hành vi strict. `CoderResult.UNDERFLOW`/`OVERFLOW` là trạng thái vận hành bộ đệm, **không mặc nhiên là lỗi dữ liệu**.

### REPLACE — phù hợp khi cần tiếp tục và chấp nhận mất mát

Chọn **`REPLACE`** khi hiển thị nội dung lỗi một cách khoan dung có giá trị hơn việc dừng toàn bộ, chẳng hạn trình xem log hỏng hoặc công cụ khám phá văn bản ngoại lai. Lúc đó nên ghi rõ:

- Charset nguồn và cách phát hiện/khai báo nó.
- Ký tự/byte thay thế, và vị trí đã được thay nếu hệ thống cần khả năng truy vết.
- Nguồn dữ liệu gốc còn được lưu nếu cần phân tích lỗi.
- Đầu ra chỉ là **bản trình bày đã sửa theo chính sách**, không chứng minh đúng bằng dữ liệu ban đầu.

Không dùng `REPLACE` âm thầm trước khi tính hash, ký dữ liệu hoặc so sánh định danh mà các byte/ký tự chính xác là thiết yếu.

### IGNORE — chỉ dùng khi chủ đích bỏ dữ liệu lỗi

`IGNORE` xóa phần đầu vào gây lỗi. Nó có thể biến `"Café"` thành `"Caf"` hoặc loại byte lỗi giữa hai đoạn hợp lệ, làm dính các từ/định danh vốn tách biệt. Chỉ dùng khi yêu cầu nghiệp vụ **chấp nhận loại bỏ**, có thông tin giám sát phù hợp và tác động đã được đánh giá. Nếu mong muốn “tiếp tục đọc nhưng không giấu lỗi”, `REPLACE` kèm cảnh báo thường dễ chẩn đoán hơn.

**Không có chính sách duy nhất luôn tối ưu.** Quyết định dựa trên hợp đồng dữ liệu và rủi ro mất mát, không dựa vào việc một cách có ném exception hay không.

## 11. Xử lý dữ liệu streaming và lỗi xảy ra ở ranh giới buffer

Khi đọc qua `InputStreamReader`, `Reader` hoặc NIO buffer, lỗi chuyển mã thường chỉ xuất hiện **trong lúc thực sự đọc/ghi**. Khi một chuỗi UTF-8 nhiều byte bị chia giữa hai chunk, bộ giải mã cần giữ trạng thái để đợi chunk sau. **Thiếu byte trong một chunk chưa chắc malformed**, trừ khi đã xác nhận **kết thúc đầu vào** mà chuỗi vẫn chưa đầy đủ.

Nếu gọi trực tiếp phiên bản incremental của `CharsetDecoder`, cần tuân thủ trình tự trạng thái:

1. Chuẩn bị `ByteBuffer` và `CharBuffer`; gọi `decode(input, output, false)` cho phần đầu vào còn tiếp tục.
2. Với `UNDERFLOW`, xem xét có cần bổ sung byte hay đã hết input; `OVERFLOW` nghĩa là cần thêm chỗ ở output buffer.
3. Khi không còn byte nữa, gọi `decode(input, output, true)` để bộ giải mã xác nhận kết thúc; dữ liệu cuối còn dở có thể được báo malformed.
4. Hoàn tất `flush(output)` và xử lý kết quả. `reset()` trước một luồng nội dung mới nếu tái sử dụng decoder đúng trạng thái cho phép.
5. Nếu đang dùng `REPORT`, kiểm tra `CoderResult.isError()` và xử lý/báo lỗi; đừng bỏ qua kết quả rồi đi tiếp như thể giải mã thành công.

Quy trình trên là **mô tả cơ chế, không phải chương trình hoàn chỉnh**. Với bài toán đọc văn bản thông thường, `InputStreamReader`/`BufferedReader` cùng decoder cấu hình sẵn giúp tránh phải tự quản lý trạng thái coder. Nếu tự viết bộ xử lý từng chunk, tuyệt đối không tạo một decoder mới cho **mỗi** đoạn byte tùy ý: một sequence hợp lệ bị chia giữa hai chunk có thể bị coi thành các đoạn malformed.

Một ví dụ khác: high surrogate nằm cuối một `CharBuffer` và low surrogate ở buffer tiếp theo có thể hợp lệ khi mã hóa cùng phiên làm việc. Đừng tự coi mỗi `CharBuffer` như một chuỗi độc lập nếu muốn bảo toàn Unicode xuyên ranh giới buffer.

## 12. Ghi nhận lỗi, phục hồi và kiểm tra chất lượng dữ liệu

Một pipeline nhập văn bản đáng tin cậy nên trả lời được:

1. **Charset là gì?** Charset là một phần của hợp đồng file/giao thức; không nên tự gán UTF-8 hay Windows-1252 chỉ vì byte có vẻ “đọc được”.
2. **Nguồn byte gốc có được giữ lại không?** Nếu chỉ giữ bản text sau `REPLACE`/`IGNORE`, không thể khôi phục chính xác dữ liệu cũ.
3. **Có phân biệt loại lỗi không?** `MalformedInputException` chỉ cấu trúc đầu vào sai; `UnmappableCharacterException` chỉ mapping không tồn tại. Ở UI/log, ghi loại lỗi và thao tác đang thực hiện (encode hay decode).
4. **Bên nào quyết định chính sách?** Lớp reader/writer tiện dụng có thể thay dữ liệu ngầm. Khi strictness quan trọng, truyền `CharsetDecoder` / `CharsetEncoder` được cấu hình rõ ràng.
5. **Có thể dùng lại cùng byte sau lỗi không?** Dữ liệu lưu ở bộ nhớ / file có thể được xử lý lại bằng một decoder mới hoặc bằng pipeline retry có kiểm soát; không tự tái sử dụng coder đã ở trạng thái không hợp lệ.
6. **Có kiểm tra round-trip không?** Với một charset giới hạn, encode rồi decode có thể không trả lại text ban đầu nếu đã có replacement. Khi tính lossless quan trọng, kiểm tra sự phù hợp của charset và dùng `REPORT` trước khi lưu.

### Một tình huống thực tế

Một hệ thống xuất dữ liệu khách hàng sang US-ASCII, trong khi tên có dấu. Nếu để `getBytes(US_ASCII)` tự thay `é` thành `?`, tên sau khi lưu có thể bị hỏng vĩnh viễn mà hệ thống không báo lỗi. Cách làm phù hợp hơn là:

- Nếu định dạng đích chấp nhận UTF-8, **chọn UTF-8** và ghi rõ charset.
- Nếu định dạng bắt buộc US-ASCII, encode bằng `CharsetEncoder` có `REPORT`; báo người dùng những trường không thể xuất hoặc áp dụng phép chuyển tự (transliteration) đã được yêu cầu và kiểm chứng.
- Chỉ dùng `REPLACE`/`IGNORE` khi hợp đồng xuất dữ liệu cho phép mất ký tự.

**Transliteration** như đổi `"é"` thành `"e"` là quy tắc biến đổi ngôn ngữ riêng, **không đồng nghĩa** với chính sách `CodingErrorAction.REPLACE` và không tự có trong bộ mã ASCII.

## 13. Lỗi thường gặp

1. **Nhầm malformed và unmappable.** `C3 28` là UTF-8 malformed; `é` trong Java text chuyển sang US-ASCII là unmappable.
2. **Cho rằng UTF-8 xử lý được mọi chuỗi `String`.** UTF-8 mã hóa các Unicode scalar value; `String` có thể chứa UTF-16 surrogate lẻ, vốn là malformed.
3. **Bỏ qua charset.** Cùng byte có thể hợp lệ ở charset này nhưng sai ở charset khác. Phân tích lỗi luôn đi cùng charset thực tế.
4. **Tưởng `newDecoder()` và `new String(bytes, charset)` xử lý lỗi giống nhau.** Decoder mới mặc định `REPORT`; constructor `String` dạng này thay dữ liệu lỗi.
5. **Dùng `IGNORE` để “sửa lỗi encoding”.** Nó bỏ dữ liệu, không khôi phục byte gốc hay chữa đúng charset.
6. **Xem replacement là không mất mát.** Một `?` hoặc `U+FFFD` không đủ để tái dựng phần đã bị thay.
7. **Quên xử lý lúc đọc/ghi.** Cấu hình strict có thể ném exception trong vòng lặp I/O hoặc khi hoàn tất, không phải ngay tại constructor.
8. **Tạo decoder mới tại mỗi chunk.** Chuỗi nhiều byte nằm qua ranh giới chunk có thể bị hỏng giả. Dùng một decoder có trạng thái cho cả dữ liệu liên tục.
9. **Bỏ qua `CoderResult`.** Trong API incremental, `UNDERFLOW`/`OVERFLOW` phải được xử lý đúng, và `isError()` cần có hành vi rõ ràng.
10. **Ghi vào log cả dữ liệu thô nhạy cảm để điều tra lỗi.** Ghi loại lỗi, vị trí/phạm vi và metadata cần thiết; hạn chế hiển thị byte nguồn nếu chúng có thể chứa dữ liệu riêng tư.
11. **Hiểu nhầm sự cố font là sự cố charset.** Dữ liệu giải mã đúng vẫn có thể hiện ô vuông nếu font thiếu glyph; đó là tầng hiển thị.
12. **Dùng charset cũ thay vì xác nhận yêu cầu.** Nếu cần giữ tiếng Việt và emoji, UTF-8 thường là lựa chọn thích hợp; chỉ chọn bộ mã giới hạn khi giao thức/hệ thống yêu cầu.

## 14. Tự kiểm tra

**Câu 1.** Decoder UTF-8 gặp byte `E2 82` rồi kết thúc input. Loại lỗi nào phù hợp?  
**Đáp án:** malformed, vì sequence UTF-8 ba byte bị thiếu byte cuối khi thật sự EOF. Nếu mới là cuối *chunk* nhưng còn byte ở chunk tiếp theo, coder phải chờ thêm dữ liệu.

**Câu 2.** Encoder US-ASCII gặp chuỗi `"Hòa"` hợp lệ. Loại lỗi nào khi đụng chữ có dấu?  
**Đáp án:** unmappable. Không phải Unicode hỏng; bộ mã US-ASCII không biểu diễn được các code point đó.

**Câu 3.** `REPORT` khác `REPLACE` ở đâu?  
**Đáp án:** `REPORT` đưa lỗi tới caller, còn `REPLACE` chèn ký tự/byte đại diện và tiếp tục, thường mất khả năng phục hồi dữ liệu gốc.

**Câu 4.** Một ứng dụng đọc file bằng `new String(bytes, UTF_8)` và không gặp exception. Có bảo đảm file chứa UTF-8 hợp lệ không?  
**Đáp án:** không. API này có chính sách thay chuỗi không hợp lệ; cần decoder strict để xác minh.

**Câu 5.** Một file chứa tên quan trọng, có thể tự loại byte lỗi bằng `IGNORE` trước khi lưu không?  
**Đáp án:** không nên. Nếu hợp đồng dữ liệu đòi hỏi chính xác, hãy `REPORT` để xử lý tại nguồn hoặc yêu cầu sửa dữ liệu.

**Câu 6.** `InputStreamReader(input, UTF_8.newDecoder().onMalformedInput(REPORT))` có thể ném lỗi lúc nào?  
**Đáp án:** khi thao tác đọc thực sự yêu cầu decoder xử lý byte lỗi, ví dụ trong `read()` hoặc `transferTo()`.

## Kết luận

**Malformed input** là dữ liệu đầu vào **sai cấu trúc mã hóa**; **unmappable character** là dữ liệu hợp lệ nhưng **không có ánh xạ trong bộ mã đích**. Java tách hai nhóm để cho phép cấu hình độc lập với `onMalformedInput(...)` và `onUnmappableCharacter(...)`. Chọn `REPORT` khi cần giữ toàn vẹn và phát hiện dữ liệu sai; `REPLACE` khi có chủ đích hiển thị/tiếp tục với mất mát được chấp nhận; `IGNORE` chỉ khi việc loại bỏ đã được quy định rõ. Đồng thời phải hiểu hợp đồng của API cấp cao đang dùng vì `CharsetEncoder`/`CharsetDecoder` và các constructor tiện dụng không nhất thiết có cùng mặc định.

### Tài liệu Java SE 17

- [`CharsetDecoder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html)
- [`CharsetEncoder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetEncoder.html)
- [`CodingErrorAction`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CodingErrorAction.html)
- [`CoderResult`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CoderResult.html)
- [`MalformedInputException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/MalformedInputException.html), [`UnmappableCharacterException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/UnmappableCharacterException.html)
- [`InputStreamReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html), [`OutputStreamWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStreamWriter.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-009">CharsetEncoder, CharsetDecoder và CodingErrorAction</a>

<details>
<summary>Xem chi tiết</summary>

# CharsetEncoder, CharsetDecoder và CodingErrorAction trong Java

Khi Java làm việc với file, HTTP, socket hoặc các định dạng văn bản, hệ thống phải chuyển đổi giữa **ký tự Unicode** và **byte**. `CharsetEncoder` chuyển ký tự sang byte; `CharsetDecoder` chuyển byte về ký tự; `CodingErrorAction` quy định phải làm gì khi đầu vào không hợp lệ hoặc không biểu diễn được trong charset đích.

Đây là phần **mã hóa văn bản** thuộc Java Core I/O, tập trung vào `java.nio.charset` và cách kết hợp bộ mã hóa/giải mã với `ByteBuffer`, `CharBuffer`, `Reader` và `Writer`. Các ví dụ sử dụng Java 17+.

## 1. Ba thành phần giải quyết ba vấn đề khác nhau

```text
Java String / char[] / CharBuffer
        |
        | CharsetEncoder (ví dụ UTF-8)
        v
  ByteBuffer: các byte mã hóa
        |
        | file, mạng, thiết bị, bộ nhớ
        v
  ByteBuffer: các byte nhận được
        |
        | CharsetDecoder (cùng charset)
        v
Java String / char[] / CharBuffer

CodingErrorAction:
  REPORT   → báo lỗi mã hóa/giải mã
  REPLACE  → thay đoạn không hợp lệ bằng giá trị thay thế
  IGNORE   → bỏ đoạn không hợp lệ
```

Các API chính:

| Thành phần | Hướng chuyển đổi | Input → output | Khởi tạo thông dụng |
| --- | --- | --- | --- |
| `Charset` | Định nghĩa ánh xạ ký tự ↔ byte | Không phải một tiến trình độc lập | `StandardCharsets.UTF_8` |
| `CharsetEncoder` | Mã hóa | `CharBuffer` → `ByteBuffer` | `charset.newEncoder()` |
| `CharsetDecoder` | Giải mã | `ByteBuffer` → `CharBuffer` | `charset.newDecoder()` |
| `CodingErrorAction` | Chính sách khi lỗi | `REPORT`, `REPLACE`, `IGNORE` | `onMalformedInput(...)` / `onUnmappableCharacter(...)` |
| `CoderResult` | Trạng thái một lần xử lý tăng dần | `UNDERFLOW`, `OVERFLOW` hoặc kết quả lỗi | Kết quả `encode(..., ..., ...)` / `decode(..., ..., ...)` |

Một ví dụ đơn giản: chữ `é` là một code point Unicode và thường chiếm một đơn vị `char` Java, nhưng trong UTF-8 mã hóa thành **hai byte** `C3 A9`. Emoji `😀` chiếm hai đơn vị `char` Java (một cặp surrogate) và bốn byte UTF-8 `F0 9F 98 80`. Vì thế:

- `String.length()` đếm **UTF-16 code unit**, không đếm byte đầu ra.
- `CharBuffer` làm việc với các đơn vị `char`, không bảo đảm mỗi phần tử là một Unicode code point hoàn chỉnh.
- `ByteBuffer` làm việc với byte. Ranh giới một lần nhận/gửi mạng có thể cắt giữa dãy byte của cùng một ký tự.
- Thỏa thuận charset giữa bên ghi và bên đọc là điều kiện cần để diễn giải byte đúng. Charset không được suy ra đáng tin chỉ bằng cách nhìn một mảng byte bất kỳ.

## 2. Tạo coder từ Charset

```java
Charset charset = StandardCharsets.UTF_8;
CharsetEncoder encoder = charset.newEncoder();
CharsetDecoder decoder = charset.newDecoder();
```

Đây là mảnh mã; cần import `java.nio.charset.*`. Mỗi lần `newEncoder()`/`newDecoder()` tạo một đối tượng mã hóa/giải mã có **trạng thái** riêng. Với UTF-8, encoder đọc đơn vị UTF-16, kiểm tra cặp surrogate và tạo byte hợp lệ. Decoder đọc byte UTF-8, kiểm tra dãy byte và tạo `char` tương ứng.

Có hai kiểu dùng coder:

1. **Một lần, toàn bộ nội dung**: `encoder.encode(CharBuffer)` và `decoder.decode(ByteBuffer)`. Hai phương thức này thực hiện một phiên chuyển đổi hoàn chỉnh, bao gồm reset và flush nội bộ; rất thích hợp khi đã có đủ dữ liệu.
2. **Tăng dần, nhiều phần dữ liệu**: `encoder.encode(CharBuffer, ByteBuffer, boolean endOfInput)` và `decoder.decode(ByteBuffer, CharBuffer, boolean endOfInput)`, sau đó `flush(output)`. Thích hợp khi dữ liệu đến từng khối, bộ đệm đầu ra có dung lượng hữu hạn hoặc cần kiểm soát tiến trình chuyển đổi.

Không dùng lẫn hai cách trong **cùng một phiên coder đang xử lý**: phương thức tiện ích một lần reset và hoàn tất phiên chuyển đổi, nên gọi nó giữa quá trình tăng dần có thể dẫn đến `IllegalStateException` hoặc làm sai logic.

## 3. Malformed input khác unmappable character như thế nào?

Cả hai đều là lỗi chuyển đổi, nhưng nguyên nhân khác nhau.

### 3.1. Malformed input: đầu vào không hợp lệ với quy tắc mã hóa

- **Khi giải mã UTF-8**: byte `C3` cần có byte tiếp theo dạng continuation, nhưng chuỗi `C3 28` là không hợp lệ (byte `28` là dấu ngoặc mở, không phải continuation byte). Đây là lỗi **malformed**.
- **Khi mã hóa từ ký tự Java**: một `char` surrogate cao (`\uD800`–`\uDBFF`) không được theo sau bởi surrogate thấp hợp lệ sẽ tạo dãy UTF-16 lỗi; encoder có thể báo **malformed**.
- UTF-8 bị cắt dở ở cuối dữ liệu cũng là lỗi malformed **khi đã xác nhận không còn byte đến nữa**. Nếu có thể còn dữ liệu ở lần đọc kế, coder có thể giữ lại phần chưa đủ và trả `UNDERFLOW`.

### 3.2. Unmappable character: đầu vào hợp lệ nhưng charset đích không biểu diễn được

- Chuỗi Java `"café"` là Unicode hợp lệ, nhưng **US-ASCII** không có mã byte cho `é`; khi mã hóa US-ASCII, đây là lỗi **unmappable**.
- Khi giải mã, một số charset có thể chứa dãy byte được coi là hợp lệ về cấu trúc nhưng không có ký tự Unicode tương đương; decoder cũng có thể phát hiện **unmappable**. Không suy diễn rằng mọi byte ngoài ASCII được decoder US-ASCII phân loại giống nhau; cách phân loại cụ thể thuộc về codec.

Hai nhánh được cấu hình riêng: `onMalformedInput(action)` và `onUnmappableCharacter(action)`. Gọi một nhánh **không tự động** thay đổi nhánh kia.

### 3.3. Ngoại lệ tương ứng

Cả `MalformedInputException` và `UnmappableCharacterException` đều là con của `CharacterCodingException`; `CharacterCodingException` là con của `IOException`. Với cách gọi một lần `encode(CharBuffer)` / `decode(ByteBuffer)` và chính sách `REPORT`, lỗi thường xuất hiện dưới dạng những ngoại lệ này. Với cách gọi tăng dần, lỗi được báo trong **`CoderResult`**, trừ khi bên gọi dùng `throwException()` để chuyển thành ngoại lệ.

## 4. CodingErrorAction: REPORT, REPLACE và IGNORE

```java
encoder.onMalformedInput(CodingErrorAction.REPORT);
encoder.onUnmappableCharacter(CodingErrorAction.REPORT);

decoder.onMalformedInput(CodingErrorAction.REPLACE);
decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
```

Các dòng trên là mảnh mã, trong đó `encoder`/`decoder` đã được tạo bằng `Charset.newEncoder()` / `newDecoder()`.

| Hành động | Điều xảy ra khi gặp đầu vào lỗi | Lợi ích | Rủi ro |
| --- | --- | --- | --- |
| `REPORT` | Trả `CoderResult` lỗi hoặc ném `CharacterCodingException`, tùy API | Phát hiện lỗi, tránh mất dữ liệu âm thầm | Cần kiểm soát luồng lỗi |
| `REPLACE` | Bỏ đoạn lỗi và ghi chuỗi/byte thay thế | Có thể hiển thị tiếp dữ liệu hỏng | Thông tin gốc không còn nguyên vẹn |
| `IGNORE` | Bỏ hẳn đoạn lỗi và xử lý phần tiếp theo | Chỉ thích hợp khi được phép bỏ dữ liệu | Làm mất ký tự, có thể gây hiểu sai nội dung |

**Giá trị thay thế không giống nhau ở hai hướng**:

- Encoder thay ký tự lỗi bằng **byte[]**; US-ASCII thường dùng byte `3F` (ký tự `?`), nhưng giá trị thực tế tùy charset/encoder. Có thể gọi `encoder.replaceWith(byte[])` với chuỗi byte hợp lệ được codec chấp nhận.
- Decoder thay byte lỗi bằng **String**; giá trị mặc định thường là Unicode Replacement Character `U+FFFD` (ký hiệu `�`). Có thể gọi `decoder.replaceWith(String)` với chuỗi thay thế hợp lệ.

Không suy ra rằng encoder có thể đặt `U+FFFD` làm byte thay thế; replacement của encoder là **byte đã mã hóa**, còn replacement của decoder là **ký tự Unicode**. Chính sách thay thế và giá trị thay thế là hai cấu hình riêng.

**Mặc định của đối tượng coder mới** (`newEncoder()` / `newDecoder()`) là **`REPORT` cho cả malformed và unmappable**. Tuy nhiên, nhiều API tiện ích cấp cao lại có mặc định **thay thế**. Đừng lấy kết quả từ `new String(bytes, UTF_8)` hay `String.getBytes(UTF_8)` để kết luận rằng coder mới cũng tự `REPLACE`.

## 5. Ví dụ chạy được: so sánh ba chính sách lỗi

Lưu thành file `CodingErrorPolicies.java`, sử dụng JDK 17+:

```java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class CodingErrorPolicies {
    public static void main(String[] args) {
        CodingErrorAction[] actions = {
                CodingErrorAction.REPORT,
                CodingErrorAction.REPLACE,
                CodingErrorAction.IGNORE
        };

        // "AéB" là Unicode hợp lệ, nhưng US-ASCII không mã hóa được 'é'.
        for (CodingErrorAction action : actions) {
            CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder()
                    .onMalformedInput(action)
                    .onUnmappableCharacter(action);
            try {
                ByteBuffer encoded = encoder.encode(
                        CharBuffer.wrap("A\u00E9B"));
                byte[] bytes = new byte[encoded.remaining()];
                encoded.get(bytes);
                System.out.println("encode " + action + ": "
                        + new String(bytes, StandardCharsets.US_ASCII));
            } catch (CharacterCodingException e) {
                System.out.println("encode " + action + ": "
                        + e.getClass().getSimpleName());
            }
        }

        // UTF-8 lỗi: C3 phải đi với continuation byte, nhưng 28 không hợp lệ.
        byte[] invalidUtf8 = {
                0x41, (byte) 0xC3, 0x28, 0x42
        };
        for (CodingErrorAction action : actions) {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(action)
                    .onUnmappableCharacter(action);
            try {
                String decoded = decoder.decode(
                        ByteBuffer.wrap(invalidUtf8)).toString();
                // In rõ ký tự thay thế thay vì phụ thuộc font console.
                System.out.println("decode " + action + ": "
                        + decoded.replace("\uFFFD", "<U+FFFD>"));
            } catch (CharacterCodingException e) {
                System.out.println("decode " + action + ": "
                        + e.getClass().getSimpleName());
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac CodingErrorPolicies.java
java CodingErrorPolicies
```

Stdout đã kiểm chứng với JDK:

```text
encode REPORT: UnmappableCharacterException
encode REPLACE: A?B
encode IGNORE: AB
decode REPORT: MalformedInputException
decode REPLACE: A<U+FFFD>(B
decode IGNORE: A(B
```

Giải thích từng trường hợp:

- `"AéB"` gồm ba ký tự hiển thị; chữ `é` không thuộc US-ASCII. Với `REPORT` ta nhận `UnmappableCharacterException`; `REPLACE` biến nó thành `?`; `IGNORE` bỏ hẳn chữ `é`.
- Byte đầu vào cho decoder là `41 C3 28 42`. `41`/`42` lần lượt là `A`/`B` và `28` là `(`. Byte `C3` không tạo được dãy UTF-8 hợp lệ với byte ngay sau, nên `REPORT` báo `MalformedInputException`; `REPLACE` thay riêng đoạn lỗi và vẫn đọc được `(B`, còn `IGNORE` xóa đoạn lỗi.
- Demo tạo **một coder mới cho mỗi lần chuyển đổi**. Đây là cách an toàn khi thay đổi chính sách cho các đầu vào độc lập; không tái sử dụng coder có trạng thái dở dang.

## 6. Khác biệt giữa coder trực tiếp và API tiện ích

Các API trông giống nhau nhưng có **chính sách lỗi khác nhau**:

| API | Công dụng | Chính sách lỗi thường áp dụng |
| --- | --- | --- |
| `charset.newEncoder().encode(CharBuffer)` | Mã hóa một lần với coder mới | Mặc định `REPORT`, có thể ném `CharacterCodingException` |
| `charset.newDecoder().decode(ByteBuffer)` | Giải mã một lần với coder mới | Mặc định `REPORT`, có thể ném `CharacterCodingException` |
| `charset.encode(CharBuffer)` / `charset.decode(ByteBuffer)` | Hàm tiện ích trên `Charset` | Dùng chính sách `REPLACE` |
| `String.getBytes(charset)` | Mã hóa toàn bộ `String` thành byte | Có thể thay thế UTF-16 lỗi; không phù hợp nếu bắt buộc từ chối dữ liệu lỗi |
| `new String(byte[], charset)` | Giải mã toàn bộ byte thành `String` | Thay thế chuỗi byte lỗi; không phải trình kiểm chứng tính hợp lệ của UTF-8 |
| `new OutputStreamWriter(stream, charset)` | Ghi ký tự ra stream byte | Mặc định thay thế dữ liệu lỗi trong quá trình ghi |
| `new InputStreamReader(stream, charset)` | Đọc byte thành ký tự | Mặc định thay thế dữ liệu lỗi trong quá trình đọc |

Ví dụ, đoạn này **không phải xác thực UTF-8 nghiêm ngặt**:

```java
String text = new String(bytes, StandardCharsets.UTF_8);
```

`bytes` là `byte[]` có sẵn; nếu byte UTF-8 không hợp lệ, Java có thể thay bằng `U+FFFD` rồi trả `String`. Khi yêu cầu từ chối dữ liệu sai định dạng, dùng:

```java
CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
String text = strict.decode(ByteBuffer.wrap(bytes)).toString();
```

Đây là mảnh mã; cần import `java.nio.*`, `java.nio.charset.*` và xử lý `CharacterCodingException`. Nó báo lỗi thay vì làm mất dấu vết lỗi đầu vào. Điều này đặc biệt hữu ích cho kiểm tra upload, định dạng cấu hình yêu cầu UTF-8 hợp lệ, hoặc giao thức không cho phép tự sửa ký tự.

## 7. Bộ đệm và CoderResult trong chuyển đổi tăng dần

Trường hợp đọc mạng/file theo từng khối, không thể mặc định một lần `read` kết thúc đúng ranh giới ký tự. API tăng dần:

```java
CoderResult result = decoder.decode(
        inputBytes, outputChars, endOfInput);
```

Ở đây `inputBytes` là `ByteBuffer`, `outputChars` là `CharBuffer` và `endOfInput` là `boolean`. Với encoder, hướng bộ đệm đảo lại:

```java
CoderResult result = encoder.encode(
        inputChars, outputBytes, endOfInput);
```

`CoderResult` có bốn nhóm trạng thái:

- **`UNDERFLOW`**: đã xử lý được mọi dữ liệu hiện có **hoặc** dữ liệu còn lại chưa đủ để tạo ký tự/byte tiếp theo. Khi vẫn còn nguồn, cần nhận thêm dữ liệu; không tự coi `UNDERFLOW` là EOF.
- **`OVERFLOW`**: buffer đầu ra không còn đủ chỗ. Cần đọc/ghi (drain) nội dung của buffer đầu ra, tạo thêm chỗ rồi gọi coder tiếp; **không** coi là lỗi codec.
- **`isMalformed()`**: chuỗi input sai cấu trúc theo charset. Chỉ trả nhóm này khi chính sách tương ứng là `REPORT`.
- **`isUnmappable()`**: input hợp lệ nhưng không thể biểu diễn. Chỉ trả nhóm này khi chính sách tương ứng là `REPORT`.

`result.length()` ở một `CoderResult` lỗi cho biết **số đơn vị đầu vào gây lỗi** (byte với decoder, `char` với encoder), không phải tổng byte đã đọc từ đầu luồng. `result.throwException()` chuyển trạng thái lỗi tương ứng thành `MalformedInputException`/`UnmappableCharacterException`. Không gọi `throwException()` một cách mù quáng với `UNDERFLOW` hoặc `OVERFLOW`.

### Quy trình coder một phiên

```text
reset() nếu tái sử dụng coder đã hoàn tất phiên trước
   |
   v
encode/decode(input, output, false)
   |                 |
   | UNDERFLOW       | OVERFLOW
   v                 v
thêm input          xả output, tiếp tục
   |
   | đến khi biết chắc không còn input
   v
encode/decode(input còn lại, output, true)
   |
   | xử lý OVERFLOW nếu có, cho tới UNDERFLOW
   v
flush(output)
   |
   | nếu OVERFLOW: xả output và gọi flush lần nữa
   v
phiên kết thúc; reset trước phiên mới
```

`endOfInput == true` có nghĩa **đây là byte/char cuối cùng trong toàn phiên chuyển đổi**, không phải “cuối buffer hiện tại”. Đặt cờ này quá sớm có thể biến ký tự đang chờ phần kế thành malformed hoặc vi phạm trạng thái coder. Nếu dữ liệu cuối thực sự còn dang dở, khi `endOfInput == true` codec phải áp dụng chính sách lỗi đã cấu hình.

### Buffer: flip, compact và clear

- `input.flip()` chuyển input từ **chế độ ghi dữ liệu mới vào buffer** sang **chế độ coder đọc**.
- Sau khi coder trả `UNDERFLOW` nhưng input vẫn còn byte/char của chuỗi dở, dùng `input.compact()` để giữ phần chưa tiêu thụ ở đầu buffer rồi **thêm dữ liệu mới** phía sau. `input.clear()` lúc này sẽ làm **mất** phần còn lại.
- Đối với output, khi có dữ liệu cần lấy/ghi xuống đích, gọi `output.flip()`, tiêu thụ hết `output.remaining()`, rồi `output.clear()` để nhận thêm kết quả. Nếu chỉ tiêu thụ một phần output, dùng `compact()` hoặc giữ phần còn lại.
- `position`/`limit` được coder dịch chuyển theo số đơn vị đã đọc/ghi; không dùng `array().length` thay cho `remaining()` để xác định nội dung hợp lệ.
- Nếu một đơn vị mã hóa hoặc trạng thái codec đòi hỏi nhiều chỗ hơn bộ đệm hiện tại, cần buffer lớn hơn hoặc chiến lược nạp/xả phù hợp. Đừng lặp lại `OVERFLOW` với output vẫn đầy mà không giải phóng dung lượng.

## 8. Ví dụ Java 17+ chạy được: UTF-8 chia giữa hai khối

Ký tự euro `€` (code point `U+20AC`) có UTF-8 là `E2 82 AC`. Giả sử mạng chỉ gửi byte đầu `E2` trong lần đầu, còn `82 AC` đến ở lần thứ hai. Decoder phải **giữ byte dở dang** giữa hai lần, và chỉ kết thúc khi đã biết hết dữ liệu.

Lưu thành `IncrementalUtf8Decoder.java`:

```java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class IncrementalUtf8Decoder {
    public static void main(String[] args) throws Exception {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        ByteBuffer input = ByteBuffer.allocate(8);
        CharBuffer output = CharBuffer.allocate(8);

        // Khối 1: byte đầu tiên của ký tự €, chưa đủ để giải mã.
        input.put((byte) 0xE2);
        input.flip();
        CoderResult first = decoder.decode(input, output, false);
        System.out.println("first: " + first
                + ", remaining=" + input.remaining()
                + ", chars=" + output.position());

        // compact() giữ E2; thêm 82 AC để có đủ E2 82 AC.
        input.compact();
        input.put((byte) 0x82).put((byte) 0xAC);
        input.flip();

        // Bây giờ đã nhận được byte cuối cùng.
        CoderResult last = decoder.decode(input, output, true);
        CoderResult flushed = decoder.flush(output);
        output.flip();

        int codePoint = Character.codePointAt(output, 0);
        System.out.println("finish: " + last
                + ", flush=" + flushed
                + ", codepoint=U+"
                + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT));
    }
}
```

Chạy:

```bash
javac IncrementalUtf8Decoder.java
java IncrementalUtf8Decoder
```

Stdout đã kiểm chứng với JDK:

```text
first: UNDERFLOW, remaining=1, chars=0
finish: UNDERFLOW, flush=UNDERFLOW, codepoint=U+20AC
```

Ở lần `decode(..., false)` đầu, `UNDERFLOW` và `remaining=1` nghĩa là byte `E2` **chưa bị tiêu thụ**; decoder cần thêm byte để xác định ký tự. `compact()` giữ nguyên byte này, sau đó chương trình thêm `82 AC`. Lần `decode(..., true)` thứ hai xử lý đầy đủ thành `U+20AC` và `flush` hoàn tất phiên. Demo dành cho hai khối cụ thể với buffer đủ chỗ; khi xử lý luồng dài, bạn cần vòng lặp xử lý `OVERFLOW`, lần đọc rỗng, EOF và lỗi I/O theo nguồn dữ liệu thực tế.

## 9. Chính sách lỗi trong trường hợp chuyển đổi tăng dần

Giả sử đã chọn `REPORT`:

```java
CoderResult result = decoder.decode(input, output, false);

if (result.isMalformed() || result.isUnmappable()) {
    int invalidUnitCount = result.length();
    // Ghi nhận offset và độ dài lỗi nếu ứng dụng cần.
    // Không tự tăng position mà không biết rõ cách xử lý dữ liệu.
    result.throwException();
}
```

Đây là mảnh mã; `input`/`output`/`decoder` phải được khởi tạo phù hợp và phương thức gọi xử lý `CharacterCodingException`. Khi coder phát hiện lỗi, `input.position()` chỉ tới đầu chuỗi byte lỗi theo hợp đồng thông thường của API; `result.length()` có thể giúp xác định đoạn cần báo, nhưng **đừng tự động bỏ qua** những byte đó nếu chính sách dữ liệu yêu cầu từ chối toàn bộ bản ghi.

Nếu ứng dụng thực sự cần bỏ qua lỗi và tiếp tục, tốt hơn chọn rõ `IGNORE` hoặc `REPLACE` và kiểm tra chính sách có phù hợp nghiệp vụ hay không. Cố gắng tự dịch `ByteBuffer.position` để “chữa lỗi” có thể khiến bỏ sót byte, lặp vô hạn hoặc biến đầu vào độc hại thành văn bản khác với ý định.

**Lưu ý tính nguyên tử:** Khi `REPORT` xảy ra sau một phần dữ liệu hợp lệ, output có thể đã chứa các ký tự/byte được tạo trước lỗi. Không coi phép chuyển đổi tăng dần là transaction tự rollback. Nếu hệ thống chỉ chấp nhận dữ liệu hợp lệ hoàn toàn, xác thực trước rồi mới commit kết quả hoặc giữ output ở vùng tạm.

## 10. Tùy biến giá trị thay thế

Ví dụ kiểm soát encoder US-ASCII:

```java
CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder()
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .replaceWith(new byte[] { (byte) '!' });
ByteBuffer bytes = encoder.encode(CharBuffer.wrap("A\u00E9B"));
// Kết quả ASCII là "A!B".
```

Ví dụ kiểm soát decoder UTF-8:

```java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .replaceWith("[?]");
CharBuffer chars = decoder.decode(ByteBuffer.wrap(
        new byte[] { 0x41, (byte) 0xC3, 0x28, 0x42 }));
// Với charset/giới hạn thay thế cho phép, kết quả sẽ là "A[?](B".
```

**Hai ví dụ trên là mảnh mã minh họa; không dùng nguyên văn ví dụ decoder thứ hai mà bỏ qua ràng buộc độ dài replacement:** `CharsetDecoder.replaceWith(String)` chỉ nhận chuỗi thay thế không rỗng có độ dài không vượt `maxCharsPerByte()`; với decoder UTF-8 của JDK thông thường, chuỗi `"[?]"` dài 3 **vượt giới hạn** và sẽ ném `IllegalArgumentException`. Một cấu hình **chạy được và hợp lệ** là `decoder.replaceWith("?")`, khi đó kết quả `"A?(B"`.

Vì vậy, khi thay thế decoder thực tế, viết:

```java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .replaceWith("?");
```

Mảnh mã này cấu hình thay thế byte UTF-8 lỗi bằng ký tự `?`. Tương tự, `CharsetEncoder.replaceWith(byte[])` kiểm tra mảng byte không rỗng, độ dài/giá trị hợp lệ theo charset; không thể truyền tùy tiện chuỗi byte khác encoding.

## 11. Dùng cùng Reader và Writer ở ranh giới I/O

Có thể gắn coder được cấu hình vào các luồng Java:

```java
CharsetDecoder strictDecoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

try (Reader reader = new InputStreamReader(inputStream, strictDecoder)) {
    // Đọc ký tự; khi nhận byte UTF-8 không hợp lệ có thể ném IOException.
}

CharsetEncoder strictEncoder = StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

try (Writer writer = new OutputStreamWriter(outputStream, strictEncoder)) {
    // Ghi ký tự; UTF-16 lỗi có thể gây IOException.
}
```

Đây là hai **mảnh mã riêng**: cần import `java.io.*`, `java.nio.charset.*`; `inputStream`, `outputStream` là các luồng đã mở và được code hiện tại sở hữu. `InputStreamReader`/`OutputStreamWriter` sẽ quản lý bộ đệm và sử dụng coder đã cấu hình. Đóng wrapper cũng đóng luồng phía dưới; nếu luồng do nơi khác sở hữu (chẳng hạn `System.out` hoặc socket được quản lý chung) thì phải có thỏa thuận về lifecycle.

Không chia sẻ **cùng một instance** `CharsetEncoder` hoặc `CharsetDecoder` cho nhiều luồng đồng thời: coder có trạng thái và API **không đảm bảo thread-safe**. Dùng mỗi tiến trình/chuyển đổi một coder riêng, hoặc đồng bộ nghiêm ngặt khi buộc phải dùng lại. `reset()` cho phép khởi tạo phiên mới khi phiên trước đã kết thúc/được hủy có kiểm soát; nó không thay cho cơ chế khóa.

## 12. Chọn chính sách theo yêu cầu nghiệp vụ

| Tình huống | Gợi ý | Vì sao |
| --- | --- | --- |
| Đọc dữ liệu giao thức yêu cầu UTF-8 hợp lệ | `CharsetDecoder` + `REPORT` | Không âm thầm biến dữ liệu sai thành dữ liệu khác |
| Xử lý tệp import cần ghi nhận dòng hỏng | `REPORT`, quản lý offset và báo lỗi | Giữ bằng chứng lỗi để người dùng sửa |
| Xem trước văn bản lỗi trong giao diện | Có thể dùng `REPLACE`, kèm thông báo dữ liệu đã được thay | Vẫn hiển thị được nội dung còn lại |
| Xuất văn bản tiếng Việt sang codec chỉ có ASCII | Ưu tiên UTF-8; nếu bắt buộc ASCII, dùng `REPORT` hoặc chính sách mất dữ liệu rõ ràng | Tránh mất dấu/tên người âm thầm |
| Làm sạch dữ liệu theo quy định cho phép loại bỏ ký tự lỗi | `IGNORE` chỉ khi đặc tả cho phép | Bỏ dữ liệu là quyết định nghiệp vụ, không phải khôi phục nguyên vẹn |
| Đọc các chunk từ mạng, kênh NIO | Coder tăng dần, giữ phần buffer dở, xử lý `UNDERFLOW`/`OVERFLOW` | Chunk không bảo đảm trùng ranh giới ký tự |

Không dùng `IGNORE` như một biện pháp “sửa an toàn” cho đầu vào không đáng tin. Ví dụ chuỗi định danh, chữ ký số, kiểm tra đường dẫn, quyền truy cập hoặc cú pháp giao thức có thể thay đổi ý nghĩa khi byte/char bị xóa. Với dữ liệu quan trọng, ưu tiên **từ chối đầu vào không hợp lệ** và báo rõ offset/lỗi; nếu dùng `REPLACE`, công khai rằng dữ liệu không còn nguyên bản.

## 13. Những giới hạn cần phân biệt

### Charset không tự sửa dữ liệu đã mã hóa sai

Nếu trước đó file bị ghi sai charset, `CharsetDecoder` không thể tự suy ra “đúng lẽ ra là” gì. Chẳng hạn giải mã byte UTF-8 bằng charset khác có thể cho ký tự **hợp lệ về mặt kỹ thuật nhưng sai nội dung** (*mojibake*), nên `REPORT` cũng có thể **không** phát hiện được. Việc giải mã đúng phụ thuộc metadata/protocol, không chỉ kiểm tra lỗi cú pháp byte.

### Charset không đồng nghĩa Unicode normalization

`"é"` có thể biểu diễn bằng một code point `U+00E9` hoặc hai code point `U+0065 U+0301`. Cả hai mã hóa UTF-8 hợp lệ, nhưng byte khác nhau. `CharsetEncoder` không tự chuẩn hóa NFC/NFD; nếu nghiệp vụ cần so sánh canonical, dùng `java.text.Normalizer` với yêu cầu rõ ràng trước/sau bước chuyển đổi. Không chuẩn hóa định danh nhạy cảm một cách tùy tiện.

### Byte Order Mark (BOM) và phát hiện charset

Một số định dạng có thể dùng BOM để báo thứ tự byte hoặc encoding. `CharsetDecoder` không phải thư viện tự phát hiện encoding từ nội dung bất kỳ; việc có/không xử lý BOM tùy charset và cách bạn chọn decoder. Đối với UTF-8, đừng mặc định mọi file đều có BOM hoặc sẽ được bỏ BOM theo cùng một cách. Định nghĩa chính sách BOM rõ khi đọc/ghi giao thức.

### Đếm kích thước và kết thúc luồng

`encoder.averageBytesPerChar()`, `maxBytesPerChar()`, `decoder.averageCharsPerByte()` và `maxCharsPerByte()` là thông tin ước lượng/giới hạn hữu ích cho cấp phát buffer; **không thay thế** kết quả chuyển đổi thực tế khi cần kích thước chính xác. Với trạng thái codec và `OVERFLOW`, vẫn phải xử lý đúng cơ chế buffer.

`flush()` trên coder **không phải** `OutputStream.flush()`: coder.flush ghi dữ liệu còn trong trạng thái mã hóa ra **buffer output do bạn cấp**, thường được gọi **sau lần encode/decode cuối với `endOfInput=true`**. Sau đó, ứng dụng vẫn phải lấy dữ liệu từ `ByteBuffer`/`CharBuffer` và ghi xuống nơi đích. Khi dùng `Reader`/`Writer`, tầng wrapper quản lý nhiều phần của quy trình này.

### Không coi coder là công cụ xác thực bảo mật toàn diện

`REPORT` chỉ xác thực tính hợp lệ **theo charset cụ thể**. Nó không kiểm tra JSON đúng schema, URL an toàn, Unicode confusable, ký tự điều khiển bị cấm, HTML escaping hoặc checksum. Sau bước mã hóa/giải mã vẫn cần kiểm tra theo ngữ pháp và yêu cầu an toàn của dữ liệu.

## 14. Lỗi thường gặp và cách khắc phục

| Sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Dùng `new String(bytes, UTF_8)` để xác thực UTF-8 | Byte sai có thể bị thay mà không báo | `UTF_8.newDecoder()` + `REPORT` |
| Nghĩ `newDecoder()` mặc định `REPLACE` | Ngoại lệ bất ngờ | Biết coder mới mặc định `REPORT` |
| Chỉ cấu hình `onMalformedInput` nhưng quên `onUnmappableCharacter` | Hai nhóm lỗi bị xử lý khác nhau | Chọn cả hai chính sách rõ ràng |
| Cho rằng malformed và unmappable là cùng một lỗi | Báo sai nguyên nhân | Phân biệt input sai cấu trúc với ký tự không biểu diễn được |
| Dùng `input.clear()` khi decoder trả `UNDERFLOW` với byte còn lại | Mất nửa ký tự UTF-8 | `compact()` trước khi nạp byte mới |
| Truyền `endOfInput=true` cho từng khối mạng | Xem byte chưa đủ là input bị cắt | Chỉ đặt `true` khi đã xác nhận EOF |
| Lặp lại `OVERFLOW` mà không xả output | Không thể tiến triển | `flip`, drain, `clear/compact` rồi tiếp tục |
| Quên `flush(output)` của coder ở cuối phiên | Có thể thiếu output của charset có trạng thái | Hoàn tất lần mã hóa/giải mã cuối rồi flush coder |
| Dùng một instance coder đồng thời ở nhiều thread | Trạng thái lỗi/race | Mỗi phiên/thread một coder hoặc đồng bộ |
| Chọn `IGNORE` để “khắc phục” dữ liệu quan trọng | Mất ký tự, thay đổi ý nghĩa | `REPORT` và xử lý lỗi theo đặc tả |
| Nhầm `replaceWith(byte[])` với `replaceWith(String)` | Sai hướng chuyển đổi hoặc exception | Encoder thay byte; decoder thay String, tuân thủ giới hạn |
| Cho rằng decoder bảo đảm nội dung đúng nghĩa | Mojibake vẫn có thể hợp lệ theo charset sai | Thống nhất charset ngoài giao thức và xác thực dữ liệu sau giải mã |

## 15. Câu hỏi tự kiểm tra

1. **Khác nhau về chiều dữ liệu?** `CharsetEncoder` nhận `char` để tạo byte, `CharsetDecoder` nhận byte để tạo `char`.
2. **Tại sao `"AéB"` mã hóa US-ASCII bằng `REPORT` thất bại?** `é` là Unicode hợp lệ nhưng không biểu diễn được trong US-ASCII: `UnmappableCharacterException`.
3. **Vì sao byte `C3 28` không giải mã UTF-8 chuẩn được?** `C3` khởi đầu chuỗi hai byte nhưng `28` không phải continuation byte: malformed.
4. **`REPORT` khác `REPLACE` và `IGNORE` ra sao?** Báo lỗi thay vì thay đoạn lỗi hoặc xóa đoạn lỗi.
5. **`newDecoder()` mặc định chính sách gì?** `REPORT`; đừng nhầm với `Charset.decode(...)` hoặc `new String(bytes, charset)` thường thay thế lỗi.
6. **`UNDERFLOW` có chắc là EOF không?** Không; có thể chỉ đang thiếu byte/char cho ký tự kế tiếp.
7. **Tại sao phải gọi `compact()` cho input có byte dở?** Để giữ byte chưa tiêu thụ và nối với byte của khối tiếp theo.
8. **`endOfInput=true` được đặt lúc nào?** Khi chắc chắn không còn input trong **phiên** chuyển đổi, không phải khi hết một chunk tạm thời.
9. **`flush(output)` thuộc bộ phận nào?** Thuộc `CharsetEncoder`/`CharsetDecoder`, xả trạng thái codec vào output buffer sau pha input cuối.
10. **Cần chống mất dữ liệu âm thầm khi đọc UTF-8 cần làm gì?** Tạo `UTF_8.newDecoder()`, cấu hình hai nhóm lỗi `REPORT`, xử lý `CharacterCodingException` và không commit nội dung khi thất bại.

## Tài liệu API Java 17

- [`CharsetEncoder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetEncoder.html).
- [`CharsetDecoder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html).
- [`CodingErrorAction` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CodingErrorAction.html).
- [`CoderResult` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CoderResult.html).
- [`Charset` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/Charset.html).
- [`InputStreamReader` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html).
- [`OutputStreamWriter` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStreamWriter.html).

**Ghi nhớ:** Coder thực hiện chuyển đổi theo một charset; `CodingErrorAction` quyết định có báo, thay hay bỏ dữ liệu lỗi; `CoderResult` hướng dẫn cách xử lý từng lần chuyển đổi tăng dần. Để xử lý văn bản đúng, cần thống nhất charset, chọn chính sách lỗi phù hợp và tôn trọng trạng thái/bộ đệm của coder.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-010">Giải mã nhiều khối khi ký tự bị chia giữa hai lần đọc</a>

<details>
<summary>Xem chi tiết</summary>

# Giải mã nhiều khối khi ký tự bị chia giữa hai lần đọc trong Java I/O

## 1. Vấn đề: ranh giới khối byte không phải ranh giới ký tự

Khi đọc văn bản từ tệp, socket hoặc một `InputStream`, dữ liệu đến dưới dạng **byte**. Một lần gọi `read(byte[])` chỉ trả về một đoạn byte tại thời điểm đó; đoạn này không nhất thiết kết thúc đúng cuối một ký tự theo bảng mã. Ví dụ trong UTF-8, một ký tự Unicode có thể được mã hóa thành **1–4 byte**. Nếu một lần đọc dừng giữa các byte của ký tự, việc giải mã từng khối độc lập có thể tạo ký tự thay thế `�` hoặc báo lỗi sai.

Ví dụ ký tự `€` (U+20AC) trong UTF-8:

~~~text
Văn bản gốc:               €
Các byte UTF-8 (hex):      E2 82 AC

Lần đọc 1:                 E2 82       ← chưa đủ ký tự
Lần đọc 2:                       AC    ← hoàn tất ký tự

Đúng:   giữ E2 82, nối AC, giải mã → €
Sai:    giải mã E2 82 riêng, rồi AC riêng → chuỗi không còn là €
~~~

Một trường hợp tương tự: emoji `😀` (U+1F600) có chuỗi UTF-8 `F0 9F 98 80`. Bất kỳ ranh giới đọc nào nằm giữa bốn byte này đều cần giữ trạng thái giải mã. Trong Java, emoji đó còn được biểu diễn thành **hai đơn vị `char` (cặp surrogate)** sau khi giải mã thành UTF-16.

Bài học tập trung vào **giải mã tăng dần (incremental decoding)**: xử lý nhiều khối byte liên tiếp như **một dòng văn bản liên tục**, giữ dữ liệu chưa hoàn chỉnh giữa các lần đọc, nhận biết EOF thật, và phát tín hiệu lỗi chính xác.

Kết quả cần đạt:

- Giải thích tại sao không nên tạo `new String(chunk, charset)` riêng cho mỗi khối byte của cùng một dòng dữ liệu.
- Hiểu cách `InputStreamReader` giữ trạng thái giúp ứng dụng đọc ký tự thông thường.
- Tự dùng `CharsetDecoder` với `ByteBuffer` và `CharBuffer` khi cần kiểm soát quá trình giải mã nhiều khối.
- Phân biệt `CoderResult.UNDERFLOW`, `OVERFLOW`, đầu vào lỗi và EOF; đặt `endOfInput` và gọi `flush()` đúng vòng đời.
- Thử được các ranh giới byte bất kỳ, bao gồm ranh giới cắt giữa ký tự tiếng Việt và emoji.

## 2. Ba lớp dữ liệu: byte, code point và char

Trước khi viết vòng lặp, cần tách ba khái niệm:

| Khái niệm | Ví dụ | Vai trò |
| --- | --- | --- |
| Byte | `E2`, `82`, `AC` | Đơn vị dữ liệu thô mà I/O từ tệp hoặc mạng truyền đi |
| Unicode code point | `U+20AC` của `€` | Giá trị trừu tượng đại diện cho một ký tự Unicode |
| Đơn vị mã UTF-16 (`char`) | `'\u20AC'` hoặc một nửa cặp surrogate | Cách nhiều API ký tự Java biểu diễn văn bản |

Trong UTF-8, ASCII như `A` dùng 1 byte; `€` dùng 3 byte; `😀` dùng 4 byte. Sau giải mã, `€` là một `char`, còn `😀` cần hai `char`. Vì vậy:

- **Số byte đọc không bằng số ký tự Java nhận được.**
- **Số `char` không phải lúc nào cũng bằng số code point.**
- **Số byte của một lần đọc không quyết định số ký tự có thể xuất ra ngay.**

Một bộ giải mã đang chờ byte cuối của ký tự có thể **chưa xuất bất kỳ `char` nào**, dù ứng dụng vừa nạp thêm byte. Điều này là bình thường; không nên tự chèn ký tự rỗng hoặc ký tự thay thế chỉ vì khối hiện tại chưa tạo ra output.

## 3. Tại sao giải mã từng mảng byte bằng new String là sai?

Đoạn mã sau minh họa lỗi thường gặp:

~~~java
StringBuilder result = new StringBuilder();
byte[] buffer = new byte[2];
int count;

while ((count = input.read(buffer)) != -1) {
    result.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
}
~~~

**Đây là đoạn minh họa không độc lập:** `input` là `InputStream` đã được mở; cần có import tương ứng và đóng stream ở bên sở hữu. Lỗi nằm ở chỗ **mỗi lần gọi constructor của `String` khởi tạo một quá trình giải mã độc lập**. Nếu dữ liệu UTF-8 bị chia giữa hai `read`, bộ giải mã của lần trước không còn cơ hội nhận các byte tiếp theo.

Ví dụ chính xác hơn, xem một chuỗi chỉ có ký tự `€` được tách thành hai mảng: `{E2, 82}` và `{AC}`. Vì mỗi phần riêng lẻ là chuỗi UTF-8 không hợp lệ, API `new String(byte[], UTF_8)` thường áp dụng chính sách **thay dữ liệu không hợp lệ** thay vì ghép hai phần thành `€`. Việc mỗi phần được giải mã không ném ngoại lệ không chứng minh văn bản còn đúng.

Không giải quyết lỗi bằng cách chọn buffer 4096 hoặc 8192 byte: buffer lớn hơn làm **giảm khả năng gặp** ranh giới chia ký tự trên dữ liệu thông thường, nhưng không loại bỏ được nó. Bất kỳ kích thước khối hữu hạn nào vẫn có thể cắt giữa một chuỗi UTF-8 dài nhiều byte.

Có hai hướng đúng:

1. **Dùng lớp Reader để đọc ký tự đã được giải mã**: phù hợp phần lớn chương trình chỉ cần văn bản.
2. **Giữ một `CharsetDecoder` trong suốt dòng dữ liệu**: phù hợp khi tự điều khiển xử lý ByteBuffer/CharBuffer, đọc bất đồng bộ, xử lý khung dữ liệu hoặc cần chính sách báo lỗi rõ ràng.

## 4. Cách đơn giản: để InputStreamReader giữ trạng thái giải mã

`InputStreamReader` là cầu nối giữa `InputStream` (byte) và `Reader` (ký tự). Lớp này giữ trạng thái giải mã qua nhiều lần đọc byte bên dưới. Người gọi chỉ nhận các đơn vị `char` sau khi thư viện có đủ byte.

~~~java
try (Reader reader = new InputStreamReader(
        input, StandardCharsets.UTF_8)) {
    char[] chars = new char[1024];
    int count;

    while ((count = reader.read(chars)) != -1) {
        // Xử lý đúng chars[0] đến chars[count - 1].
    }
}
~~~

Đoạn minh họa yêu cầu `import java.io.Reader`, `import java.io.InputStreamReader` và `import java.nio.charset.StandardCharsets`, cùng biến `InputStream input` đã được cấp. `try-with-resources` đóng Reader và do đó đóng InputStream bên dưới; chỉ dùng cách này khi đoạn mã **sở hữu** InputStream hoặc được trao quyền đóng.

Dù Reader giải quyết việc UTF-8 bị chia giữa các lần đọc byte, chính Reader vẫn có thể trả từng khối `char[]` cắt giữa **hai đơn vị surrogate** của một code point Unicode. Nếu chỉ ghi nguyên trạng từng khối với `Writer.write(chars, 0, count)` trong cùng một dòng văn bản, cách này phù hợp; nếu xử lý từng khối như chuỗi ký tự độc lập để đảo ngược, lọc hoặc phân tích code point, cần xem xét ranh giới UTF-16.

### 4.1. Ví dụ chạy độc lập với nguồn cung cấp từng byte

Tệp **ReaderAcrossChunksDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class ReaderAcrossChunksDemo {
    public static void main(String[] args) throws Exception {
        String original = "A€😀B";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);

        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            StringBuilder restored = new StringBuilder();
            char[] buffer = new char[2];
            int count;

            while ((count = reader.read(buffer)) != -1) {
                restored.append(buffer, 0, count);
            }

            System.out.println("UTF-8 bytes: " + bytes.length);
            System.out.println("UTF-16 units: " + restored.length());
            System.out.println("Restored: " + restored);
            System.out.println("Matches: " + original.contentEquals(restored));
        }
    }
}
~~~

Chạy với Java 17+:

~~~bash
javac -encoding UTF-8 ReaderAcrossChunksDemo.java
java ReaderAcrossChunksDemo
~~~

Kết quả:

~~~text
UTF-8 bytes: 9
UTF-16 units: 5
Restored: A€😀B
Matches: true
~~~

`ByteArrayInputStream` trong ví dụ đã có toàn bộ byte trong RAM; Reader có quyền gom byte theo nhu cầu nên lần gọi `read(char[])` không nhất thiết trùng với từng đơn vị byte. Tuy nhiên, kết quả minh họa quy tắc quan trọng: **thông qua cùng một Reader, dữ liệu UTF-8 được giải mã liên tục, không tạo decoder mới cho từng lần đọc**.

Khi phải quan sát/chủ động cấp **chính xác từng mảnh byte**, cần ví dụ `CharsetDecoder` ở phần tiếp theo.

## 5. CharsetDecoder: trạng thái giải mã được giữ ở đâu?

`java.nio.charset.CharsetDecoder` là đối tượng có trạng thái, dùng để biến byte thành các đơn vị UTF-16 của Java. Tạo một decoder duy nhất cho **một dòng dữ liệu liên tục**:

~~~java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
~~~

Các import cần thiết: `java.nio.charset.CharsetDecoder`, `CodingErrorAction` và `StandardCharsets`. Đây là đoạn minh họa, chưa phải chương trình độc lập.

Khi xử lý đầu vào theo nhiều khối, đối tượng quan trọng là:

- `ByteBuffer input`: giữ những byte hiện có, **bao gồm phần chưa đủ để tạo ký tự**.
- `CharBuffer output`: nơi decoder đặt các đơn vị `char` đã giải mã.
- `CharsetDecoder decoder`: giữ trạng thái của quá trình giải mã, bao gồm các quy tắc của charset và tình trạng cuối luồng.
- `CoderResult result`: báo trạng thái decoder sau một lần xử lý.

Với `CharsetDecoder.decode(input, output, endOfInput)`:

| Kết quả | Ý nghĩa | Cách xử lý |
| --- | --- | --- |
| `UNDERFLOW` | Không thể tạo thêm đầu ra với lượng byte đang có; có thể vì thiếu byte để hoàn thành ký tự hoặc đã tiêu thụ hết | Giữ byte còn lại, nạp thêm; nếu là cuối luồng thì chuẩn bị flush |
| `OVERFLOW` | CharBuffer không đủ chỗ để tạo thêm đầu ra | Đưa các ký tự đã tạo cho nơi tiêu thụ, xóa/trống CharBuffer rồi gọi decode tiếp |
| `isMalformed()` | Chuỗi byte không hợp lệ theo charset | Xử lý theo chính sách lỗi; nếu REPORT thì ném ngoại lệ |
| `isUnmappable()` | Dữ liệu không ánh xạ được thành đầu ra theo charset | Xử lý theo chính sách lỗi; không được tự hiểu là EOF |

**Chú ý:** `UNDERFLOW` không nhất thiết có nghĩa “hết toàn bộ dữ liệu”. Khi `endOfInput=false`, nó có thể chỉ báo rằng decoder đang cần những byte tiếp theo. Đừng kết thúc xử lý toàn bộ nguồn mỗi khi nhìn thấy `UNDERFLOW`.

### 5.1. Chọn chính sách lỗi

Có ba lựa chọn tiêu biểu cho chuỗi byte lỗi:

- `CodingErrorAction.REPORT`: để `CoderResult` báo lỗi (hoặc ném `CharacterCodingException` khi dùng `throwException()`). Phù hợp khi dữ liệu cần chính xác tuyệt đối.
- `CodingErrorAction.REPLACE`: dùng chuỗi thay thế do decoder quy định (thường thấy `�` với UTF-8). Phù hợp khi ưu tiên hiển thị dữ liệu có thể phục hồi.
- `CodingErrorAction.IGNORE`: bỏ dữ liệu lỗi. Có nguy cơ mất dữ liệu một cách im lặng; chỉ dùng khi nghiệp vụ cho phép.

**Byte bị chia giữa hai lần đọc không phải lỗi.** Khi decoder nhận `endOfInput=false`, phần byte chưa hoàn chỉnh cần được giữ lại để thử với byte kế tiếp. Chỉ khi dòng thật sự kết thúc mà vẫn thiếu byte cần thiết thì chuỗi bị cụt mới trở thành đầu vào lỗi theo hợp đồng charset.

## 6. Vòng đời ByteBuffer: position, limit, flip và compact

`ByteBuffer` có ba chỉ số chính:

- `capacity`: dung lượng toàn bộ vùng chứa.
- `position`: vị trí đọc/ghi tiếp theo.
- `limit`: ranh giới hiện tại mà thao tác không được vượt qua.

Khi **ghi byte vào buffer**, buffer thường ở chế độ ghi: `position` tăng sau `put`, `limit` thường bằng `capacity`. Trước khi decoder **đọc từ buffer**, gọi `flip()` để đặt `limit = position` và `position = 0`.

~~~text
ByteBuffer đang ở chế độ đọc:

[ E2 ][ 82 ][  · ][  · ][  · ][  · ]
  ^position      ^limit          ^capacity
  Các byte E2 82 chưa đủ để tạo €

Decoder trả UNDERFLOW, position vẫn trỏ tới byte chưa tiêu thụ.
Gọi compact():
- đưa byte còn lại về đầu mảng
- đặt position sau số byte còn lại
- đưa limit về capacity để có chỗ nạp tiếp

Nạp AC, gọi flip():

[ E2 ][ 82 ][ AC ][  · ][  · ][  · ]
  ^position            ^limit

Decoder tiếp tục → €
~~~

Trong thuật toán đúng, **`compact()` rất quan trọng**: nó bảo toàn những byte decoder chưa tiêu thụ. Nếu dùng `clear()` sau `UNDERFLOW`, bạn sẽ bỏ các byte còn dở của ký tự; khi byte tiếp theo đến, phần còn lại không còn ghép được với phần trước.

Khác biệt cốt lõi:

| Thao tác | Thực hiện | Khi nào dùng |
| --- | --- | --- |
| `flip()` | Chuyển phần dữ liệu vừa ghi thành vùng có thể đọc | Sau khi nạp thêm byte trước khi decode |
| `compact()` | Giữ dữ liệu **chưa đọc** ở đầu buffer và mở chỗ ghi mới | Sau một lần decode trước khi nạp byte tiếp |
| `clear()` | Đặt position=0, limit=capacity, không xóa nội dung vật lý | Chỉ khi chắc chắn không còn byte cũ cần giữ |
| `remaining()` | Trả số phần tử giữa position và limit | Biết còn bao nhiêu byte để đọc/ghi |
| `hasRemaining()` | Kiểm tra còn phần tử hay không | Điều khiển vòng lặp; không tự nói lên EOF của nguồn |

`CharBuffer` cũng có `position`/`limit`. Sau mỗi `decode`, để đưa các ký tự đã tạo cho nơi tiêu thụ: gọi `output.flip()`, đọc các ký tự còn lại, rồi `output.clear()` để decoder có thể tạo thêm. **Không gọi `output.clear()` trước khi lấy dữ liệu đã giải mã**.

## 7. Thuật toán giải mã tăng dần

Một vòng lặp chuẩn cho một chuỗi byte liên tục gồm các bước sau:

1. Tạo **một** `CharsetDecoder` và cấu hình chính sách lỗi.
2. Tạo `ByteBuffer` đủ chỗ chứa chuỗi byte tối đa của charset và có khoảng trống để nạp thêm; tạo `CharBuffer` đủ chỗ cho đầu ra mà decoder có thể sinh khi gặp một ký tự.
3. Lấy một phần byte từ nguồn rồi nạp vào `ByteBuffer`. Dùng `compact()` trước khi nạp để giữ những byte chưa tiêu thụ; dùng `flip()` để decoder nhìn thấy toàn bộ byte hợp lệ.
4. Gọi `decode(input, output, false)` khi **vẫn có thể còn byte** từ nguồn. Với `OVERFLOW`, thu đầu ra rồi gọi lại. Với `UNDERFLOW`, giữ byte còn lại và chờ khối mới.
5. Khi **xác nhận nguồn thực sự EOF**, gọi `decode(input, output, true)` với các byte cuối cùng (có thể rỗng). Lúc này các byte dư không tạo thành ký tự hợp lệ sẽ được xử lý như dữ liệu cuối bị cụt theo chính sách lỗi.
6. Sau khi decode cuối trả `UNDERFLOW`, gọi `decoder.flush(output)`; xử lý `OVERFLOW` bằng cách thu output và gọi flush tiếp, cho đến `UNDERFLOW`.
7. Dùng `decoder.reset()` chỉ khi muốn sử dụng lại decoder cho **một dòng độc lập khác**, sau khi vòng đời hiện tại đã kết thúc đúng cách. Không reset giữa các khối của cùng một nguồn.

Đây là **máy trạng thái**. Gọi `flush()` trước khi `decode(..., true)` hoàn tất hoặc nạp thêm byte vào decoder đã kết thúc mà chưa reset là sai trình tự. Một `CharsetDecoder` không được coi là đối tượng an toàn để nhiều thread cùng thao tác vào trạng thái chia sẻ; mỗi luồng giải mã độc lập nên sở hữu decoder riêng hoặc đồng bộ theo thiết kế.

### 7.1. Lưu ý với ByteBuffer quá nhỏ

Nếu buffer đầu vào không đủ chứa một chuỗi byte cần thiết để decoder tiến triển, vòng lặp có thể mắc kẹt: `UNDERFLOW` giữ tất cả byte, nhưng `compact()` không để lại chỗ nạp thêm. Với UTF-8, một code point cần tối đa 4 byte; ví dụ dưới dùng buffer 8 byte. Khi thiết kế decoder tổng quát cho charset khác, cần bảo đảm dung lượng/bộ đệm phù hợp hoặc có cơ chế mở rộng.

Tương tự, `CharBuffer` phải có đủ chỗ cho một đơn vị đầu ra mà decoder cần sinh cùng lúc; emoji ngoài BMP có thể yêu cầu hai đơn vị `char`. Ví dụ dùng `CharBuffer.allocate(2)`. Nếu CharBuffer có dung lượng 1 và decoder cần phát cặp surrogate cùng lúc, thuật toán đơn giản có thể tiếp tục gặp `OVERFLOW` mà không tạo tiến triển.

## 8. Ví dụ đầy đủ Java 17: cố tình cắt UTF-8 thành các khối 2 byte

Ví dụ dưới tự cung cấp hai byte mỗi lượt, khiến ký tự `€` (3 byte) và `😀` (4 byte) bị chia giữa các lượt. Kết quả phải khớp **chính xác** chuỗi gốc. Ví dụ có xử lý `UNDERFLOW`, `OVERFLOW`, phần dư ByteBuffer và flush cuối.

Lưu vào **Utf8IncrementalDecoderDemo.java**:

~~~java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class Utf8IncrementalDecoderDemo {
    public static void main(String[] args) throws Exception {
        String original = "A€😀B";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);
        String decoded = decodeInChunks(bytes, 2);

        System.out.println("UTF-8 bytes: " + bytes.length);
        System.out.println("UTF-16 units: " + decoded.length());
        System.out.println("Decoded: " + decoded);
        System.out.println("Matches: " + original.equals(decoded));
    }

    static String decodeInChunks(byte[] bytes, int chunkSize)
            throws CharacterCodingException {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        ByteBuffer input = ByteBuffer.allocate(8);
        CharBuffer output = CharBuffer.allocate(2);
        StringBuilder result = new StringBuilder();

        // Chế độ đọc với không byte nào đang chờ.
        input.limit(0);
        int next = 0;

        while (next < bytes.length) {
            // Giữ byte chưa tiêu thụ, mở chỗ cho dữ liệu mới.
            input.compact();
            int count = Math.min(
                    chunkSize, Math.min(input.remaining(), bytes.length - next));
            if (count == 0) {
                throw new IllegalStateException("Input buffer too small");
            }

            input.put(bytes, next, count);
            next += count;
            input.flip();

            boolean endOfInput = next == bytes.length;
            decodeAvailable(decoder, input, output, result, endOfInput);
        }

        // Trường hợp toàn bộ đầu vào rỗng vẫn phải kết thúc decoder.
        if (bytes.length == 0) {
            decodeAvailable(decoder, input, output, result, true);
        }

        // Chỉ flush sau khi lần decode cuối đã trả UNDERFLOW.
        while (true) {
            CoderResult status = decoder.flush(output);
            drain(output, result);

            if (status.isOverflow()) {
                continue;
            }
            if (status.isError()) {
                status.throwException();
            }
            if (status.isUnderflow()) {
                break;
            }
        }

        return result.toString();
    }

    private static void decodeAvailable(
            CharsetDecoder decoder, ByteBuffer input, CharBuffer output,
            StringBuilder result, boolean endOfInput)
            throws CharacterCodingException {
        while (true) {
            CoderResult status = decoder.decode(input, output, endOfInput);
            drain(output, result);

            if (status.isOverflow()) {
                // CharBuffer đã được thu và làm trống: thử decode tiếp.
                continue;
            }
            if (status.isError()) {
                status.throwException();
            }
            if (status.isUnderflow()) {
                // ByteBuffer có thể vẫn giữ đầu một ký tự chưa đủ byte.
                break;
            }
        }
    }

    private static void drain(CharBuffer output, StringBuilder result) {
        output.flip();
        result.append(output);
        output.clear();
    }
}
~~~

Biên dịch và chạy (JDK 17+):

~~~bash
javac -encoding UTF-8 Utf8IncrementalDecoderDemo.java
java Utf8IncrementalDecoderDemo
~~~

Kết quả mong đợi:

~~~text
UTF-8 bytes: 9
UTF-16 units: 5
Decoded: A€😀B
Matches: true
~~~

**Điểm then chốt trong ví dụ:**

- `bytes.length == 9` vì `A` chiếm 1 byte, `€` chiếm 3, `😀` chiếm 4, `B` chiếm 1.
- `decodeInChunks(bytes, 2)` cố tình nạp 2 byte mỗi lượt (lượt cuối có thể ngắn hơn). Một số lượt đọc chỉ cung cấp **mảnh đầu/mảnh cuối của cùng một ký tự**.
- `input.compact()` chuyển byte chưa giải mã sang đầu buffer; `input.put` nạp tiếp đúng đoạn của nguồn; `input.flip()` chuẩn bị cho decoder.
- `decodeAvailable` xử lý `OVERFLOW` và thu ký tự từ `CharBuffer` liên tục. Với `UNDERFLOW` chưa đến cuối luồng, nó trả quyền điều khiển để nạp thêm byte.
- `endOfInput` chỉ trở thành `true` sau khi **byte cuối của nguồn đã được cung cấp**. Không suy ra EOF từ một lần `UNDERFLOW`.
- `decoder.flush(output)` diễn ra sau lần decode cuối, bảo đảm toàn bộ trạng thái kết thúc đã được xử lý.
- `StringBuilder.append(output)` lấy các ký tự còn lại trong CharBuffer sau `flip()`; `clear()` chuẩn bị nơi chứa đầu ra mới.

Ví dụ phục vụ học cơ chế. Trong sản phẩm thực, cần kết hợp việc nạp byte từ `InputStream`/`ReadableByteChannel`, xử lý ngoại lệ I/O, hủy thao tác và vòng đời tài nguyên. Một byte source có thể kết thúc với `-1` (EOF), còn `0` nếu đọc qua channel không chặn có thể chỉ là chưa có byte lúc này. Hai trạng thái ấy **không được đánh đồng**.

## 9. Nếu byte đến từ InputStream hoặc Channel thực tế

### 9.1. InputStream.read(byte[])

Với `InputStream`, giá trị `read(buffer)` là:

- Số byte thực tế đã đọc.
- `-1` khi hết dữ liệu.
- Với mảng có độ dài 0, lời gọi có thể trả `0`; đừng dùng mảng rỗng làm bộ đệm vòng lặp.

Ví dụ đọc byte từ tệp theo từng lượt, sau đó có thể cấp phần byte đó vào **cùng decoder** hoặc đơn giản bọc `InputStreamReader`:

~~~java
try (var input = java.nio.file.Files.newInputStream(path);
     var reader = new java.io.InputStreamReader(
             input, java.nio.charset.StandardCharsets.UTF_8)) {
    char[] buffer = new char[2048];
    int count;
    while ((count = reader.read(buffer)) != -1) {
        // Xử lý từng phần ký tự đã giải mã.
    }
}
~~~

Đoạn này cần `Path path` và phương thức khai báo hoặc bắt `IOException`. Nó minh họa Reader giữ trạng thái dù InputStream bên dưới đọc byte theo nhiều lượt; không phải chương trình độc lập.

### 9.2. ReadableByteChannel và I/O không chặn

Một channel không chặn có thể trả về `0` khi **chưa có byte sẵn**, dù kết nối còn mở. Khi đó:

- Không gọi `decode(..., true)`.
- Không gọi `flush()` để “lấy nốt chữ”.
- Giữ byte còn lại và trạng thái decoder, đợi lượt đọc sau.
- Chỉ báo `endOfInput=true` khi giao thức/API xác nhận đã thực sự kết thúc phần dữ liệu cần giải mã.

Với giao thức mà mỗi thông điệp có độ dài riêng, ranh giới kết thúc một thông điệp không tự động là EOF của **toàn bộ kết nối**. Cần quyết định decoder tiếp tục trên cùng luồng ký tự hay mỗi thông điệp là một chuỗi byte độc lập (và phải dùng decoder riêng/reset đúng lúc). Không lấy “mỗi lần socket.read” làm ranh giới ký tự hoặc ranh giới thông điệp.

## 10. Phân biệt UNDERFLOW, OVERFLOW và lỗi: hai tình huống đối chiếu

**Tình huống A — UTF-8 hợp lệ bị chia thành nhiều khối:**

~~~text
Dòng byte:       E2 82 AC
Khối 1:          E2 82
decode(false):   UNDERFLOW; không có ký tự €
Giữ byte:        E2 82
Khối 2:          AC
decode(true):    tạo €; sau đó UNDERFLOW
flush():         UNDERFLOW, hoàn tất
~~~

Đây là kết quả đúng. Dữ liệu chưa hoàn chỉnh ở khối đầu tiên **không phải malformed**.

**Tình huống B — UTF-8 bị cụt thật sự ở cuối nguồn:**

~~~text
Dòng byte:       E2 82
Khối 1:          E2 82
decode(false):   UNDERFLOW; chờ byte tiếp theo
Nguồn kết thúc thật:
decode(true):    MALFORMED (khi chính sách là REPORT)
~~~

Nếu muốn từ chối dữ liệu lỗi, gọi `status.throwException()` để ném `MalformedInputException` (một loại `CharacterCodingException`). Nếu cấu hình `REPLACE`, decoder có thể sinh ký tự thay thế thay vì dừng; ứng dụng phải quyết định chính sách phù hợp.

**Tình huống C — Output buffer không chứa đủ:**

~~~text
Đã đọc đủ byte; decoder muốn phát thêm ký tự
CharBuffer đã đầy hoặc không đủ chỗ
decode(...):     OVERFLOW
Đọc hết ký tự hiện có từ CharBuffer, rồi clear()
decode(...):     tiếp tục xử lý ByteBuffer ở vị trí còn lại
~~~

`OVERFLOW` **không có nghĩa dữ liệu sai**; nó báo thiếu chỗ trống ở đầu ra. Ngược lại, nếu cứ gọi lại `decode` với cùng CharBuffer đầy mà không thu dữ liệu, chương trình có thể lặp mãi không tiến triển.

## 11. Không reset decoder giữa các khối

Sai lầm phổ biến là viết:

~~~java
for (byte[] chunk : chunks) {
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    // Decode riêng từng chunk...
}
~~~

Đoạn trên chỉ là minh họa ý tưởng sai: biến `chunks` được giả định có sẵn, code không độc lập. Mỗi decoder mới có trạng thái ban đầu, nên khối sau không biết các byte còn dở của khối trước. Ngay cả khi giữ cùng decoder nhưng gọi `reset()` sau mỗi lượt cũng làm mất trạng thái của chuỗi dữ liệu liên tục.

Đúng là tạo một decoder cho **cả nguồn**, giữ ByteBuffer chưa tiêu thụ và trạng thái decoder qua các lượt. Chỉ gọi `reset()` sau khi vòng đời hiện tại hoàn tất và khi muốn xử lý một nguồn mới, độc lập về mặt mã hóa. Với charset có trạng thái chuyển đổi (stateful encoding), giữ trạng thái càng quan trọng; không thể áp đặt cách nghĩ “mỗi byte xác định duy nhất cách diễn giải” cho mọi charset.

## 12. Lỗi dữ liệu, kiểm tra chất lượng và chiến lược phục hồi

Một ứng dụng nhập tệp cấu hình hoặc dữ liệu hợp đồng thường cần tính chính xác: dùng `REPORT` để biết nguồn chứa chuỗi byte không hợp lệ hoặc bị cụt. Một trình hiển thị log lại có thể ưu tiên tiếp tục: `REPLACE` hợp lý nếu chấp nhận mất thông tin và có cơ chế thông báo.

Có thể tạo ví dụ kiểm tra lỗi không cần tệp:

~~~java
byte[] incomplete = {(byte) 0xE2, (byte) 0x82};
// Giải mã bằng decoder UTF-8 với REPORT.
// Với endOfInput=false: cần chờ byte kế tiếp.
// Với endOfInput=true: báo malformed.
~~~

Đây là đoạn dữ liệu minh họa; để kiểm tra đầu vào lỗi, hãy dùng chính vòng lặp `decode` trong ví dụ đầy đủ và truyền `incomplete`. `CharsetDecoder` có thể trả `CoderResult` lỗi, rồi `throwException()` ném ngoại lệ; không nên bắt lỗi và tự ghi `"?"` nếu yêu cầu giữ chính xác văn bản.

**Không thể sửa mất dữ liệu bằng cách tự nối các `String` đã giải mã lỗi.** Sau khi mỗi đoạn đã biến byte lỗi thành `�`, thông tin byte gốc có thể mất; phải giữ byte gốc hoặc giải mã lại toàn bộ từ nguồn chuẩn.

Nếu cần phục hồi ở cấp giao thức, hãy xác định rõ liệu phần byte còn dở là:

- Một ký tự đang chờ byte tiếp theo (chưa phải EOF).
- Một chuỗi byte thật sự sai/mất byte.
- Một khối bị cắt vì giới hạn thông điệp; phải quy định có được tiếp tục sang thông điệp tiếp theo hay không.
- Dữ liệu đúng UTF-8 nhưng bị xử lý bằng charset sai.

Các trường hợp này có cùng dấu hiệu “chưa tạo được ký tự”, nhưng cách giải quyết không giống nhau.

## 13. Chú ý về BOM, charset và mã hóa theo từng luồng

UTF-8 không cần Byte Order Mark (BOM), nhưng một nguồn có thể bắt đầu với ba byte `EF BB BF`. Nếu giải mã như nội dung bình thường, decoder UTF-8 có thể tạo ký tự `U+FEFF`. Việc coi ký tự đầu này là dấu hiệu cần loại bỏ hay là dữ liệu cần giữ **phụ thuộc định dạng và hợp đồng đầu vào**.

Nếu BOM bị chia làm nhiều lần đọc, nó cũng cần được xử lý như một chuỗi byte liên tục, tuyệt đối không giải mã riêng mỗi phần. Không nên tự ý loại mọi `U+FEFF` xuất hiện trong văn bản, vì điều đó có thể biến đổi nội dung hợp lệ.

Với UTF-16 và các charset khác, quy tắc byte-order và trạng thái có thể khác UTF-8. Hãy chọn đúng `Charset` ngay từ đầu, đồng thời giữ **cùng decoder** cho cả dòng dữ liệu. Không đổi charset giữa các khối chỉ vì khối nào đó giải mã chưa ra chữ.

## 14. Kiểm thử ranh giới: cùng dữ liệu, mọi cách chia khối đều phải đúng

Một bài kiểm thử tốt không chỉ dùng buffer lớn. Hãy lấy cùng một chuỗi văn bản chứa ASCII, chữ có dấu, ký tự ba byte và emoji bốn byte, rồi thử cung cấp dữ liệu theo các kích thước khối 1, 2, 3, 4, 5... byte. Đối với dữ liệu UTF-8 hợp lệ, **kết quả giải mã cuối cùng phải giống nhau**.

Ví dụ có thể bổ sung ngay vào `main` của `Utf8IncrementalDecoderDemo` sau khi đã có `bytes` và `original`:

~~~java
for (int size = 1; size <= 9; size++) {
    String actual = decodeInChunks(bytes, size);
    if (!original.equals(actual)) {
        throw new AssertionError("Incorrect chunk size: " + size);
    }
}
System.out.println("All chunk sizes passed");
~~~

Đây là **đoạn chèn vào lớp ví dụ ở mục 8**, không phải chương trình độc lập. Nếu chèn sau những lệnh in đã có và chạy lại, chương trình sẽ in thêm:

~~~text
All chunk sizes passed
~~~

Ý nghĩa kiểm thử:

- Kích thước 1 buộc mỗi byte của `€` và `😀` lần lượt đi qua nhiều khối.
- Kích thước 2 và 3 tạo nhiều vị trí cắt giữa ký tự.
- Kích thước 4 trở lên vẫn cần kiểm tra khi dữ liệu có thứ tự ký tự khác.
- Kiểm thử `byte[]` rỗng để bảo đảm decoder vẫn được kết thúc và flush mà không lỗi.
- Kiểm thử byte cuối bị cắt để bảo đảm chế độ `REPORT` phát hiện malformed thay vì âm thầm mất dữ liệu.

Các kiểm thử này giúp phát hiện lỗi thường gặp trong việc gọi `compact()`, đánh dấu `endOfInput` quá sớm, không thu `CharBuffer`, reset decoder giữa lượt hoặc viết thêm ký tự thay thế vô ý.

## 15. Những lỗi triển khai thường gặp

| Lỗi | Hậu quả | Cách đúng |
| --- | --- | --- |
| `new String(chunk, UTF_8)` cho từng lần đọc rồi nối | Một code point nhiều byte bị cắt có thể thành `�` hoặc nội dung sai | `InputStreamReader` hoặc một `CharsetDecoder` xuyên suốt |
| `ByteBuffer.clear()` sau UNDERFLOW dù còn byte | Vứt phần đầu chưa hoàn chỉnh của ký tự | `compact()` để giữ byte chưa tiêu thụ |
| Đặt `endOfInput=true` sau mỗi khối | Decoder coi cuối khối như cuối dòng, báo malformed nhầm | Chỉ đặt true khi EOF thật sự |
| Không gọi `decode(..., true)` khi EOF | Decoder không nhận tín hiệu kết thúc để xử lý phần dư | Thực hiện lần decode cuối |
| Không gọi `flush()` sau decode cuối | Với decoder có trạng thái, có thể bỏ sót đầu ra cuối | Flush đến UNDERFLOW |
| Gặp OVERFLOW nhưng không thu CharBuffer | Vòng lặp thiếu tiến triển, có thể bị kẹt | Flip → đọc đầu ra → clear → decode tiếp |
| Gặp UNDERFLOW rồi vứt input.remaining() | Mất dữ liệu đang chờ byte kế | Bảo toàn phần chưa tiêu thụ |
| Tạo buffer đầu vào quá nhỏ | Không đủ chỗ ghép chuỗi byte nhiều byte | Cấp buffer đủ hoặc mở rộng theo nhu cầu |
| Tạo buffer đầu ra quá nhỏ | Không đủ chỗ sinh cặp surrogate | Cấp đủ chỗ cho đầu ra tối thiểu |
| Reset decoder sau mỗi lượt | Xóa trạng thái của dòng dữ liệu | Chỉ reset trước dòng độc lập mới |
| Đồng nhất `read() == 0` với EOF | Kết thúc sai ở channel không chặn | Phân biệt chưa có dữ liệu với `-1` |
| Không chọn charset rõ ràng | Cùng byte có thể được diễn giải khác nhau | Chỉ định charset theo định dạng đầu vào |
| Nuốt `CharacterCodingException` | Che dữ liệu nguồn hỏng hoặc cấu hình sai | Báo lỗi hoặc ghi nhận chính sách thay thế rõ |
| Đếm ký tự bằng số byte | Sai độ dài với tiếng Việt/emoji | Phân biệt UTF-8 bytes, UTF-16 units và code point |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Một byte stream cung cấp `E2 82`, decoder UTF-8 với `endOfInput=false` trả UNDERFLOW. Đây có phải dữ liệu lỗi?

**Trả lời:** Chưa. Bộ giải mã thiếu byte thứ ba của một chuỗi UTF-8 ba byte; giữ `E2 82` và nạp tiếp `AC` để nhận `€`.

**Câu 2.** Đã xác nhận EOF, decoder nhận `E2 82` với `endOfInput=true` và chế độ REPORT. Nên chờ nữa không?

**Trả lời:** Không. Nếu EOF thật sự, dữ liệu bị cụt; báo lỗi malformed theo `CoderResult`/`CharacterCodingException`. Chỉ khi giao thức chưa thực sự kết thúc mới được chờ thêm.

**Câu 3.** Decoder trả OVERFLOW. Có nên lập tức nạp thêm byte vào `ByteBuffer`?

**Trả lời:** Không cần. Trước tiên giải phóng `CharBuffer` và tiếp tục decode các byte đã có; OVERFLOW nói về **chỗ trống đầu ra**, không phải thiếu byte đầu vào.

**Câu 4.** Vì sao một `String` hiển thị emoji có `length() == 2` dù emoji được xem là một code point?

**Trả lời:** `String.length()` đếm đơn vị mã UTF-16. Code point ngoài BMP dùng một cặp surrogate gồm hai đơn vị `char`.

**Câu 5.** Khi nào nên chọn `InputStreamReader` thay vì tự gọi `CharsetDecoder`?

**Trả lời:** Khi mục tiêu chỉ là đọc văn bản tuần tự và không cần tự kiểm soát mảng byte, trạng thái decoder hoặc CoderResult. `InputStreamReader` giải quyết vấn đề byte bị chia trong các lượt đọc bên dưới.

**Câu 6.** Một channel không chặn trả `0` byte. Có được gọi `decode(input, output, true)` không?

**Trả lời:** Không chỉ vì kết quả 0. Đây thường là tình trạng chưa có byte sẵn; phải đợi thêm hoặc theo dõi trạng thái theo API giao thức. Chỉ đánh dấu EOF khi nguồn thực sự hoàn tất.

## 17. Ghi nhớ để áp dụng

1. **Một nguồn văn bản là một chuỗi byte liên tục.** Ranh giới `read` không có ý nghĩa mã hóa; một ký tự UTF-8 có thể trải qua nhiều khối.
2. **Giải mã theo trạng thái.** Đơn giản nhất là một `InputStreamReader` cho cả nguồn; khi dùng NIO trực tiếp, giữ một `CharsetDecoder` xuyên suốt.
3. **`UNDERFLOW` cần dữ liệu tiếp hoặc đã hết dữ liệu hiện có; `OVERFLOW` cần chỗ chứa đầu ra.** Cả hai đều không tự là lỗi.
4. **Giữ byte chưa tiêu thụ bằng `ByteBuffer.compact()`**, nạp thêm rồi `flip()`. Với CharBuffer, `flip()` để lấy ký tự và `clear()` khi đã tiêu thụ hết.
5. **Chỉ gọi `decode(..., true)` tại EOF thật**, sau đó `flush()` đến UNDERFLOW. Không reset giữa các khối, không đóng luồng khi chỉ mới hết một lần đọc.
6. **Kiểm thử với khối 1–4 byte, dữ liệu có dấu và emoji.** Kiểm thử dữ liệu bị cụt với chính sách REPORT để bảo đảm phát hiện lỗi thay vì tạo văn bản sai.

### Tài liệu API tham khảo

- [CharsetDecoder — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html)
- [CoderResult — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CoderResult.html)
- [CodingErrorAction — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CodingErrorAction.html)
- [ByteBuffer — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)
- [InputStreamReader — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html)
- [StandardCharsets — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-011">Không cắt chuỗi byte hoặc char tùy ý giữa một ký tự</a>

<details>
<summary>Xem chi tiết</summary>

# Không cắt chuỗi byte hoặc char tùy ý giữa một ký tự

## 1. Vấn đề: một ký tự nhìn thấy không nhất thiết là một byte hay một `char`

Trong Java I/O, tệp và kết nối truyền **byte**, còn `String`, `Reader`, `Writer` làm việc với dữ liệu ký tự. Khi chuyển văn bản thành byte (encoding), một ký tự Unicode có thể cần nhiều byte. Khi biểu diễn văn bản trong `String` Java (UTF-16), một Unicode code point cũng có thể cần **hai `char`**.

Do đó, câu “cắt một đoạn dài N ký tự” cần làm rõ đơn vị muốn đếm:

1. **Byte**: đơn vị lưu trữ/truyền dữ liệu; trong UTF-8 một Unicode code point có thể chiếm 1–4 byte.
2. **UTF-16 code unit**: một giá trị `char` trong Java, dài 16 bit; một code point ngoài BMP chiếm hai code unit.
3. **Unicode code point**: giá trị Unicode (ví dụ U+1F600 cho 😀); một code point có thể chiếm một hoặc hai `char`.
4. **Grapheme cluster**: một cụm người đọc nhìn như một ký tự, đôi khi gồm nhiều code point (ví dụ chữ `e` kết hợp dấu sắc, hoặc emoji ghép bằng ZWJ).

Nếu cắt ở vị trí bất kỳ, chương trình có thể chia đôi byte UTF-8, tách một cặp surrogate UTF-16, hoặc giữ code point nguyên vẹn nhưng vẫn cắt giữa một **cụm ký tự hiển thị**.

### Minh họa với `A😀B`

| Biểu diễn | Các đơn vị | Độ dài |
| --- | --- | ---: |
| Unicode code point | `A` · `😀` · `B` | 3 code point |
| Java UTF-16 `char` | `0041` · `D83D` · `DE00` · `0042` | 4 code unit |
| UTF-8 byte (hex) | `41` · `F0 9F 98 80` · `42` | 6 byte |

Chữ `A` chiếm một byte UTF-8 và một `char`; emoji 😀 chiếm **bốn byte UTF-8** và **hai `char` UTF-16**. Vì vậy:

- Cắt mảng UTF-8 sau byte `F0`, `9F` hoặc `98` sẽ chia dở chuỗi mã hóa của emoji.
- `"A😀B".substring(0, 2)` giữ `A` cùng **nửa đầu** cặp surrogate; kết quả là chuỗi UTF-16 không hợp lệ về cấu trúc cặp.
- `"A😀B".substring(0, 3)` giữ được hai code point `A` và 😀 nguyên vẹn.

Đây là nguyên tắc áp dụng cho mọi thao tác đọc theo block, gửi từng packet, giới hạn kích thước trường dữ liệu, chia chunk, rút gọn nội dung hoặc cắt `String`.

## 2. Vì sao UTF-8 không được cắt ở offset byte bất kỳ?

UTF-8 là mã hóa độ dài thay đổi: code point ASCII dùng một byte, nhiều ký tự tiếng Việt thường dùng 2–3 byte, và code point ngoài BMP như 😀 dùng bốn byte. Một byte mở đầu cho biết độ dài chuỗi mã hóa; các byte tiếp theo là **continuation byte**, có dạng bit `10xxxxxx`.

Ví dụ emoji 😀 (U+1F600):

```text
Unicode code point:    U+1F600
UTF-8 đầy đủ:          F0 9F 98 80
Cắt sau 1 byte:        F0 | 9F 98 80
Cắt sau 2 byte:        F0 9F | 98 80
Cắt sau 3 byte:        F0 9F 98 | 80
Ranh giới hợp lệ:      | F0 9F 98 80 |
```

Với ba cách cắt sai, cả hai mảnh riêng lẻ không còn là chuỗi UTF-8 hợp lệ tương ứng với emoji ban đầu. Nếu giải mã **riêng từng mảnh** rồi nối các `String`, decoder có thể trả ký tự thay thế `U+FFFD` (�), báo lỗi hoặc bỏ dữ liệu tùy cách cấu hình. Nối byte **trước khi giải mã** thì có thể khôi phục nguyên vẹn vì các byte chưa bị biến đổi.

**Quy tắc:** Ranh giới chunk **truyền/đọc byte** có thể nằm ở mọi vị trí, miễn là bộ giải mã giữ được trạng thái và các byte còn dở; ranh giới **văn bản đã được giải mã độc lập** thì phải tôn trọng mã hóa. Không thể áp đặt rằng mỗi lần `InputStream.read(byte[])` sẽ dừng ở cuối một Unicode code point: `InputStream` không biết charset.

### Chỉ nhìn bit `10xxxxxx` chưa đủ để kiểm tra văn bản

Trong dữ liệu UTF-8 **được xác nhận hợp lệ**, một offset nằm ngay trước byte mở đầu (hoặc EOF) là ứng viên ranh giới code point. Nhưng việc tìm byte không có dạng continuation **không thay thế một bộ giải mã có kiểm tra**: đầu vào vẫn có thể bị overlong encoding, surrogate được mã hóa trái phép, code point ngoài phạm vi Unicode hoặc thiếu byte cuối. Khi xử lý dữ liệu không tin cậy, hãy dùng `CharsetDecoder` có chính sách lỗi rõ ràng.

## 3. UTF-16 trong Java: `char` không phải luôn là một ký tự hoàn chỉnh

Java `String` được truy cập bằng **UTF-16 code unit**:

- `String.length()` là số code unit UTF-16, **không phải** số code point hay số ký tự hiển thị.
- `String.charAt(index)`, `substring(begin, end)`, `StringBuilder.delete(...)` và mảng `char[]` làm việc theo **chỉ số code unit**.
- Với code point từ U+10000 đến U+10FFFF, UTF-16 dùng **surrogate pair**: high surrogate (`D800–DBFF`) rồi low surrogate (`DC00–DFFF`).
- Một `char` surrogate riêng lẻ không phải Unicode scalar value hợp lệ; mã hóa nó độc lập có thể gây thay thế ký tự hoặc báo lỗi.

Ví dụ:

```java
String text = "A😀B";
System.out.println(text.length());                       // 4 UTF-16 units
System.out.println(text.codePointCount(0, text.length())); // 3 code points
```

Đây là một **đoạn minh họa trong ngữ cảnh**: hai dòng lần lượt in `4` và `3`. Để xử lý nguyên từng code point, nên dùng `codePointAt`, `codePointBefore`, `offsetByCodePoints`, `codePoints()`, `Character.charCount` và `StringBuilder.appendCodePoint`.

### Tại sao `substring` không tự bảo vệ cặp surrogate?

`substring(begin, end)` nhận offset theo code unit nên Java cho phép tạo một `String` kết thúc bằng high surrogate đơn lẻ. Điều đó không khiến chương trình Java lập tức lỗi. Lỗi thường xuất hiện **sau đó**: khi mã hóa sang UTF-8, ghi tệp, truyền mạng, áp dụng thuật toán yêu cầu văn bản Unicode hợp lệ, hoặc hiển thị.

Mã dưới đây có nguy cơ làm hỏng văn bản:

```java
// Sai về ý nghĩa “giữ hai code point đầu tiên”.
String result = "A😀B".substring(0, 2);
```

Nếu cần lấy **hai code point đầu tiên**, xác định ranh giới UTF-16 bằng `offsetByCodePoints`:

```java
String text = "A😀B";
int end = text.offsetByCodePoints(0, 2);
String firstTwoCodePoints = text.substring(0, end); // "A😀"
```

`String.offsetByCodePoints` giúp bước đi qua cặp surrogate hoàn chỉnh; tuy nhiên nếu chuỗi đầu vào **đã chứa surrogate lẻ**, hãy xác thực dữ liệu trước khi coi nó là văn bản Unicode hợp lệ.

## 4. Ví dụ Java 17: cắt sai và cắt đúng

Chương trình chạy độc lập bên dưới minh họa cả ranh giới byte UTF-8 lẫn code unit UTF-16, với output không phụ thuộc font console.

**Tệp `UnicodeBoundaryDemo.java`** (lưu mã nguồn UTF-8):

```java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class UnicodeBoundaryDemo {
    public static void main(String[] args) {
        String text = "A😀B";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        System.out.println("UTF-8 hex: "
                + HexFormat.of().withUpperCase().formatHex(bytes));
        System.out.println("UTF-16 units: " + text.length());
        System.out.println("Code points: "
                + text.codePointCount(0, text.length()));

        // Cắt sau hai UTF-16 code unit: A + high surrogate.
        String wrongCharPrefix = text.substring(0, 2);
        System.out.println("Ends with high surrogate: "
                + Character.isHighSurrogate(
                        wrongCharPrefix.charAt(wrongCharPrefix.length() - 1)));

        // Cắt đúng sau hai Unicode code point: A + emoji.
        int safeEnd = text.offsetByCodePoints(0, 2);
        String left = text.substring(0, safeEnd);
        String right = text.substring(safeEnd);
        System.out.println("Safe substring round-trip: "
                + (left + right).equals(text));

        // Cắt sau byte số 3: A + hai byte đầu của emoji bốn byte.
        byte[] invalidPrefix = Arrays.copyOfRange(bytes, 0, 3);
        boolean rejected = false;
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(invalidPrefix));
        } catch (CharacterCodingException expected) {
            rejected = true;
        }
        System.out.println("Incomplete UTF-8 rejected: " + rejected);
        System.out.println("Full UTF-8 round-trip: "
                + text.equals(new String(bytes, StandardCharsets.UTF_8)));
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 UnicodeBoundaryDemo.java
java UnicodeBoundaryDemo
```

**Stdout chính xác:**

```text
UTF-8 hex: 41F09F988042
UTF-16 units: 4
Code points: 3
Ends with high surrogate: true
Safe substring round-trip: true
Incomplete UTF-8 rejected: true
Full UTF-8 round-trip: true
```

Tại sao decoder nghiêm ngặt báo lỗi trong ví dụ? Vì `CharsetDecoder.decode(ByteBuffer)` dạng tiện ích được gọi như một thao tác **giải mã toàn bộ input**, nên đoạn `A F0 9F` kết thúc khi sequence UTF-8 cho 😀 vẫn chưa đủ bốn byte. Đó là **dữ liệu cụt ở EOF**. Trong quá trình đọc streaming, một đoạn byte chưa hoàn chỉnh giữa các lần đọc **chưa phải EOF** và cần được giữ lại cho lần kế tiếp.

## 5. Đọc luồng byte thành text: phải mang trạng thái giải mã qua các lần đọc

Giả sử một luồng mạng trả hai byte đầu UTF-8 ở lần `read` thứ nhất, rồi trả hai byte còn lại ở lần sau. Luồng byte **không sai**: ranh giới các lần `read` là chi tiết truyền tải. Cách xử lý sai là:

```java
// Mã minh họa sai: chỉ có ý nghĩa nếu byteChunks là danh sách
// các khối byte nhận được từ I/O.
for (byte[] chunk : byteChunks) {
    text.append(new String(chunk, StandardCharsets.UTF_8));
}
```

Mỗi lần `new String(chunk, UTF_8)` tạo decoder mới, coi chunk là một văn bản UTF-8 **độc lập**. Nếu chunk dừng giữa byte sequence, thông tin dở dang không được nối sang lượt tiếp theo.

**Cách an toàn thông dụng:** bọc `InputStream` bằng **`InputStreamReader`** với `StandardCharsets.UTF_8` hoặc `CharsetDecoder` được cấu hình, rồi đọc `char[]`. Reader sẽ xử lý trạng thái giải mã giữa các lượt lấy byte từ luồng bên dưới. Có thể dùng `BufferedReader` cho đọc text theo dòng.

```java
// Đoạn minh họa, InputStream source đã mở và caller chuyển quyền sở hữu.
try (Reader reader = new InputStreamReader(source, StandardCharsets.UTF_8)) {
    char[] buffer = new char[2048];
    int count;
    while ((count = reader.read(buffer)) != -1) {
        // Xử lý đúng buffer[0 .. count), tiếp tục cân nhắc surrogate.
    }
}
```

Reader **không tự biến mỗi mảng `char[]` thành một cụm ký tự hiển thị hoàn chỉnh**. Nó giải mã byte đúng thành code unit UTF-16, nhưng một cặp surrogate có thể bị tách ở ranh giới một `read(char[])`. Nếu chỉ sao chép và nối tất cả code unit theo đúng thứ tự, dữ liệu được giữ nguyên; nếu xử lý từng chunk **như một chuỗi Unicode độc lập**, vẫn phải kiểm soát các cặp surrogate ở ranh giới.

### Ví dụ chạy được: InputStream luôn trả tối đa một byte

Chương trình tạo luồng giả lập mà mỗi lần được yêu cầu đọc một mảng byte thì luồng chỉ cho tối đa **một byte**. Việc này cố ý khiến các byte UTF-8 của tiếng Việt và emoji bị chia rời.

**Tệp `FragmentedUtf8Read.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class FragmentedUtf8Read {
    static class OneByteAtATime extends FilterInputStream {
        OneByteAtATime(InputStream delegate) {
            super(delegate);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            return in.read(b, off, Math.min(len, 1));
        }
    }

    public static void main(String[] args) throws IOException {
        String original = "Hà Nội 😀";
        byte[] sourceBytes = original.getBytes(StandardCharsets.UTF_8);
        StringBuilder restored = new StringBuilder();

        try (Reader reader = new InputStreamReader(
                new OneByteAtATime(new ByteArrayInputStream(sourceBytes)),
                StandardCharsets.UTF_8)) {
            char[] buffer = new char[2];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                restored.append(buffer, 0, count);
            }
        }

        System.out.println("Round-trip equal: "
                + original.contentEquals(restored));
        System.out.println("Unicode code points equal: "
                + (original.codePointCount(0, original.length())
                == restored.codePointCount(0, restored.length())));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 FragmentedUtf8Read.java
java FragmentedUtf8Read
```

**Stdout chính xác:**

```text
Round-trip equal: true
Unicode code points equal: true
```

Mỗi lần một mảng byte được đọc từ nguồn đều có thể trả một byte; `InputStreamReader` vẫn giải mã thành Unicode đúng nhờ giữ trạng thái decoder. `StringBuilder.append(buffer, 0, count)` nối **đúng số `char` đã nhận** và giữ thứ tự. Trong ví dụ, ta không giải mã mỗi byte riêng lẻ và cũng không mã hóa độc lập mỗi khối `char[]`.

## 6. Xử lý từng code point từ Reader khi không được tách cặp surrogate

Một số thuật toán cần xử lý từng code point, chẳng hạn kiểm tra ký tự được phép, phân loại Unicode, hoặc tự xây dựng các đoạn logic theo code point. Khi đó đọc **một `char`** rồi coi đó là toàn bộ code point sẽ sai nếu gặp emoji hoặc một code point khác ngoài BMP.

Cần đọc và ghép high surrogate với low surrogate liền sau:

```java
static int nextCodePoint(Reader reader) throws IOException {
    int first = reader.read();
    if (first == -1) {
        return -1; // EOF
    }

    char ch = (char) first;
    if (Character.isLowSurrogate(ch)) {
        throw new IOException("Unexpected low surrogate");
    }
    if (!Character.isHighSurrogate(ch)) {
        return first;
    }

    int second = reader.read();
    if (second == -1 || !Character.isLowSurrogate((char) second)) {
        throw new IOException("Broken surrogate pair");
    }
    return Character.toCodePoint(ch, (char) second);
}
```

Đây là **phương thức minh họa**, cần import `java.io.Reader`, `java.io.IOException`; chưa phải chương trình độc lập. Nó trả `-1` ở EOF và một code point hợp lệ ở các lần khác. Trong nhánh high surrogate, lời gọi đọc tiếp là bắt buộc để biết cặp có hợp lệ hay không. Nếu dữ liệu có low surrogate lẻ hoặc high surrogate không đi cùng low surrogate, ví dụ chọn **báo lỗi** thay vì âm thầm thay ký tự.

Nếu đã có `String` trong bộ nhớ và biết nó là văn bản Unicode hợp lệ, thường chỉ cần dùng `String.codePoints()` hoặc `offsetByCodePoints`, không cần tự viết bộ lặp.

## 7. Cắt String theo số code point, không lấy `substring(0, n)` một cách mù quáng

`String.substring(0, n)` lấy **n UTF-16 code unit**, trong khi mô tả “lấy n code point” là ý nghĩa khác. Một hàm cắt theo code point có thể viết như sau:

```java
// Hàm trong ngữ cảnh: cần String value và số lượng n phù hợp.
static String prefixByCodePoints(String value, int n) {
    int count = value.codePointCount(0, value.length());
    if (n < 0 || n > count) {
        throw new IllegalArgumentException("Invalid code-point count");
    }
    int end = value.offsetByCodePoints(0, n);
    return value.substring(0, end);
}
```

Hàm này không chia đôi **cặp surrogate được ghép đúng**. Nhưng `codePointCount` và `offsetByCodePoints` vẫn có thể bước qua surrogate lẻ như một code point riêng biệt theo quy tắc API; vì vậy nếu tính hợp lệ Unicode là điều kiện bảo mật/dữ liệu, hãy xác thực chuỗi trước.

Khi chia String thành nhiều đoạn, nên xác định **mỗi ranh giới theo code point** rồi lấy các đoạn `substring(start, end)`. Không tính toán offset theo `index += n` code unit và cũng không lấy `text.length() / parts` rồi cắt trực tiếp.

### Ranh giới code point vẫn chưa là ranh giới ký tự hiển thị

Ví dụ chữ `é` có thể được biểu diễn bằng **hai code point**: `e` (U+0065) + dấu sắc kết hợp (U+0301). Nếu chia sau `e`, cả hai code point vẫn hợp lệ, nhưng dấu có thể bị bỏ lại đầu đoạn kế tiếp. Một emoji gia đình nối bằng zero-width joiner (ZWJ) hoặc một biểu tượng lá cờ cũng có thể gồm nhiều code point.

Nếu yêu cầu là “không cắt giữa một ký tự mà người dùng nhìn thấy”, đơn vị cần giữ nguyên là **extended grapheme cluster**, không chỉ Unicode code point. Java có `BreakIterator.getCharacterInstance(locale)` cho các quy tắc ngắt ký tự; tuy nhiên cần kiểm tra khả năng tương thích Unicode/emoji của **phiên bản JDK cụ thể**. Khi ứng dụng phụ thuộc chính xác vào các cụm emoji hiện đại, có thể dùng thư viện segmentation tuân theo Unicode UAX #29 và kiểm thử từng tổ hợp (ví dụ ICU4J). Đừng khẳng định chỉ dùng `codePoints()` là đủ cho mọi thao tác cắt chữ trên giao diện.

## 8. Khi trường dữ liệu giới hạn số byte UTF-8: tính theo từng code point

Một lỗi hay gặp là giới hạn payload “tối đa 5 byte” rồi cắt `Arrays.copyOf(utf8, 5)` mà không biết byte thứ năm có đang nằm trong một sequence hay không. Thay vào đó, với **văn bản Unicode hợp lệ**, duyệt từng code point, tính số byte sau mã hóa UTF-8 và chỉ thêm cả code point khi đủ ngân sách.

**Tệp `Utf8ByteBudget.java`:**

```java
import java.nio.charset.StandardCharsets;

public class Utf8ByteBudget {
    static String prefixWithinUtf8Bytes(String text, int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Negative byte budget");
        }

        StringBuilder result = new StringBuilder();
        int used = 0;

        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            // Không chấp nhận một surrogate đơn lẻ.
            if (codePoint >= Character.MIN_SURROGATE
                    && codePoint <= Character.MAX_SURROGATE) {
                throw new IllegalArgumentException("Unpaired surrogate");
            }

            String single = new String(Character.toChars(codePoint));
            int byteCount = single.getBytes(StandardCharsets.UTF_8).length;

            if (byteCount > maxBytes - used) {
                break;
            }
            result.appendCodePoint(codePoint);
            used += byteCount;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    public static void main(String[] args) {
        String text = "A😀B";
        String at3 = prefixWithinUtf8Bytes(text, 3);
        String at5 = prefixWithinUtf8Bytes(text, 5);
        String at6 = prefixWithinUtf8Bytes(text, 6);

        System.out.println("Budget 3 bytes: "
                + at3.getBytes(StandardCharsets.UTF_8).length);
        System.out.println("Budget 5 bytes: "
                + at5.getBytes(StandardCharsets.UTF_8).length);
        System.out.println("Budget 6 bytes: "
                + at6.getBytes(StandardCharsets.UTF_8).length);
        System.out.println("Budget 5 is A+emoji: " + at5.equals("A😀"));
        System.out.println("Budget 6 is all text: " + at6.equals(text));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 Utf8ByteBudget.java
java Utf8ByteBudget
```

**Stdout chính xác:**

```text
Budget 3 bytes: 1
Budget 5 bytes: 5
Budget 6 bytes: 6
Budget 5 is A+emoji: true
Budget 6 is all text: true
```

Vì `A` chiếm một byte và 😀 chiếm bốn byte, ngân sách ba byte chỉ giữ `A`; ngân sách năm byte giữ `A😀`; ngân sách sáu byte giữ cả `A😀B`. Hàm này giữ **prefix liên tục**: nếu code point tiếp theo không còn đủ chỗ thì dừng, chứ không bỏ qua nó để lấy ký tự ở phía sau.

Mã cố ý thiên về dễ hiểu: tạo một `String` và mảng byte tạm cho từng code point có thể tốn thời gian/bộ nhớ với chuỗi rất dài. Khi tối ưu, có thể viết bộ đếm số byte UTF-8 dựa trên dải code point đã xác thực hoặc dùng `CharsetEncoder` với buffer giới hạn. Tuyệt đối không tối ưu bằng cách cắt mảng byte ở vị trí bất kỳ rồi hy vọng decoder tự sửa.

**Lưu ý phạm vi:** hàm này giữ nguyên từng Unicode code point, nhưng vẫn có thể cắt giữa **cụm grapheme** như chữ kèm combining mark. Nếu giới hạn byte đi cùng yêu cầu không tách ký tự hiển thị, trước hết phân đoạn theo grapheme rồi mã hóa/tính byte cho cả cụm.

## 9. Kỹ thuật giải mã streaming bằng CharsetDecoder

Khi làm việc ở tầng thấp (ví dụ một parser nhận `ByteBuffer` từ network), `CharsetDecoder` cho phép kiểm soát chính xác việc tiếp nhận dữ liệu chưa đủ byte:

```text
Chunk 1: [F0 9F]             -- chưa đủ UTF-8 cho 😀
                |
                v
    Decoder (endOfInput=false)
                |
    UNDERFLOW: giữ lại F0 9F
                |
                v
Chunk 2: [98 80] + bytes còn lại
                |
                v
    Decoder hoàn tất F0 9F 98 80 -> surrogate pair UTF-16
```

Các khái niệm quan trọng:

- `CharsetDecoder.decode(input, output, false)` có thể trả `CoderResult.UNDERFLOW` khi không còn byte đủ để tạo thêm ký tự; nếu input còn byte dở, **giữ lại** các byte đó và ghép với chunk sau.
- `CoderResult.OVERFLOW` nghĩa là buffer output hết chỗ; cần xử lý/giải phóng `CharBuffer` và gọi `decode` tiếp với byte còn lại.
- Khi thật sự đã đến EOF, gọi `decode(..., true)`, sau đó `flush(output)` theo vòng đời decoder để xử lý trạng thái cuối. Lúc này byte UTF-8 cụt mới được phân loại là malformed tùy chính sách lỗi.
- Sau một lần decode, `ByteBuffer.compact()` có thể dồn các byte chưa tiêu thụ về đầu buffer; sau đó ghi thêm byte mới và `flip()` trước lần decode tiếp.
- `onMalformedInput(CodingErrorAction.REPORT)` và `onUnmappableCharacter(CodingErrorAction.REPORT)` cho phép báo lỗi thay vì tự thay; hai lựa chọn còn lại của `CodingErrorAction` là `REPLACE` hoặc `IGNORE`.

Đoạn trên là **sơ đồ quy trình**, không phải code chương trình độc lập. Khi dùng decoder stateful, cần thực hiện đúng các bước `decode`/`flush`, phân biệt EOF thật với ranh giới chunk, và xử lý cả `UNDERFLOW` lẫn `OVERFLOW`. Đối với hầu hết tác vụ file text, dùng `InputStreamReader` với charset đúng đơn giản và ít rủi ro hơn.

### Khi dữ liệu không hợp lệ cần xử lý thế nào?

Nếu decoder được cấu hình `REPORT`, một UTF-8 sequence lỗi có thể gây `MalformedInputException` hoặc `UnmappableCharacterException`. Với `REPLACE`, ký tự thay thế có thể xuất hiện và **không thể khôi phục dữ liệu ban đầu** từ văn bản đã thay thế. Với `IGNORE`, dữ liệu lỗi có thể bị bỏ qua.

Tùy bối cảnh: log có thể chấp nhận `REPLACE` để tiếp tục hiển thị, còn tệp cấu hình, dữ liệu chữ ký hoặc hệ thống cần tính toàn vẹn thường nên dùng `REPORT` và ghi nhận lỗi. Đừng coi một chuỗi `String` hiện ra không có lỗi lập trình là bằng chứng bytes gốc đúng charset.

## 10. Ghi text theo chunk: đừng mã hóa nửa cặp surrogate một cách độc lập

Sai lầm đối xứng với lỗi giải mã byte là **mã hóa từng mảnh `String`/`char[]` bằng encoder mới** khi mảnh bị cắt giữa high surrogate và low surrogate:

```java
// Minh họa lỗi: giả sử halves được lấy tại offset char bất kỳ.
byte[] part1 = halves[0].getBytes(StandardCharsets.UTF_8);
byte[] part2 = halves[1].getBytes(StandardCharsets.UTF_8);
// Nối part1, part2 sẽ không nhất thiết bằng mã hóa cả chuỗi gốc.
```

Hai lời gọi `getBytes` tạo hai thao tác mã hóa hoàn chỉnh độc lập. Nếu `halves[0]` kết thúc bằng high surrogate và `halves[1]` mở đầu bằng low surrogate, mỗi thao tác chỉ thấy **nửa cặp**, có thể thay thế dữ liệu hoặc báo lỗi. Cách giải quyết là:

1. Nếu đã có cả `String`, dùng ranh giới `offsetByCodePoints` (hoặc grapheme khi cần) trước khi chia thành các đoạn cần mã hóa **độc lập**.
2. Nếu cần ghi một luồng text liên tục, giữ **một Writer/CharsetEncoder** trong suốt vòng đời luồng và đóng nó đúng cách; không liên tục tạo encoder mới từ mỗi chunk tùy ý.
3. Nếu chuyển dữ liệu từ `Reader` sang `Writer`, dùng một pipeline text liên tục; nếu xử lý mỗi chunk theo code point, mang một high surrogate còn dở sang chunk sau.
4. Nếu dữ liệu ban đầu là byte UTF-8 và chỉ cần chuyển tải, tốt nhất giữ nguyên byte và tránh giải mã rồi mã hóa lại không cần thiết.

Một writer liên tục có thể giữ trạng thái mã hóa; tuy nhiên đừng giả định `flush()` ở ranh giới ngẫu nhiên là đủ để kết thúc hợp lệ một nửa surrogate. Đối với encoder được quản lý thủ công, phải chỉ rõ khi nào thực sự kết thúc input (`endOfInput`) và khi nào còn ký tự ở lần sau.

## 11. Khi nào “được cắt” và khi nào “không được cắt”?

| Tác vụ | Có thể chia ở mọi offset không? | Nguyên tắc đúng |
| --- | --- | --- |
| Truyền byte UTF-8 qua socket thành nhiều lần ghi | Có, nếu bên nhận giữ liên tục và giải mã sau/decoder streaming | Ranh giới TCP/byte chunk không phải ranh giới ký tự |
| Lưu byte thô thành các block rồi nối lại nguyên thứ tự | Có | Không giải mã từng block như văn bản độc lập |
| Tạo nhiều file UTF-8 mà file nào cũng phải đọc độc lập | Không | Cắt tại ranh giới UTF-8 code point hoặc văn bản cao hơn |
| `substring` để giới hạn số code point | Không | `offsetByCodePoints` trước khi `substring` |
| Chia đoạn hiển thị trong UI mà không tách emoji ghép | Không, kể cả chỉ cắt theo code point | Cần ranh giới grapheme cluster |
| Đọc file text thành các `char[]` rồi ghép nguyên thứ tự | Có, nếu không làm hỏng/thay đổi từng mảng độc lập | `Reader` giải mã byte, ghép đủ code unit |
| Đếm giới hạn cột DB tính theo UTF-8 bytes | Không | Tính số byte mã hóa cho từng code point/cụm hợp lệ |
| Chia chunk để gửi từng `String.getBytes(UTF_8)` độc lập | Không, nếu cắt giữa surrogate | Cắt trước theo ranh giới an toàn, rồi mã hóa |
| Cần đúng cùng số byte sau copy | Có thể chia block ở vị trí bất kỳ | Dùng byte stream; không convert text vô cớ |

Nói cách khác, **không phải việc chia buffer tự nó là lỗi**. Lỗi là coi những mảnh chưa hoàn chỉnh của **một biểu diễn mã hóa** như các ký tự hoặc văn bản độc lập, hoặc biến đổi mảnh rồi mất thông tin cần để ghép lại.

## 12. Các lỗi phổ biến và cách phát hiện

### 12.1 Một ký tự bị thay bằng �

Nguyên nhân thường là giải mã một fragment UTF-8 không đầy đủ như một văn bản trọn vẹn hoặc chọn charset sai. Hãy kiểm tra byte gốc ở dạng hex, charset phía tạo dữ liệu và logic theo chunk. Dùng decoder `REPORT` trong kiểm thử để phát hiện lỗi sớm thay vì mặc định thay ký tự.

### 12.2 `String.length()` nhỏ/lớn khác với số chữ nhìn thấy

`String.length()` là số code unit UTF-16. Với emoji ngoài BMP, số này **lớn hơn số code point**; với chữ kết hợp dấu hoặc emoji ghép, **số code point còn có thể lớn hơn số grapheme**. Đừng dùng `length()` một mình để tính số ký tự hiển thị trong UI.

### 12.3 Chữ vẫn hiển thị đúng khi test ASCII, nhưng lỗi với tiếng Việt/emoji

ASCII là tập con của UTF-8 mà mỗi code point chỉ chiếm một byte và một UTF-16 code unit. Test `Hello` không bộc lộ lỗi ranh giới. Cần có dữ liệu kiểm thử chứa cả ASCII, chữ Việt có dấu, ký tự 2–3 byte, emoji bốn byte, combining mark và ranh giới chunk ngẫu nhiên.

### 12.4 Đọc char buffer nhỏ rồi xử lý từng mảng như “một câu hoàn chỉnh”

`Reader.read(char[])` chỉ trả số code unit thực đọc được, có thể kết thúc giữa một cặp surrogate. Giữ mảnh chưa hoàn chỉnh hoặc xử lý qua iterator code point có trạng thái; tránh gọi `new String(buffer, 0, count)` rồi mã hóa **mỗi mảnh độc lập** mà không kiểm tra ranh giới surrogate.

### 12.5 Dùng `CharsetDecoder.decode` độc lập cho từng ByteBuffer

Phương thức tiện ích `decode(ByteBuffer)` có ý nghĩa decode trọn input, nên một block bị cụt có thể gây lỗi cuối input. Nếu cần xử lý streaming, tái sử dụng decoder qua các chunk với `decode(..., false)` và chỉ kết thúc bằng `true` tại EOF thực. Đọc text thông thường nên dùng `InputStreamReader`.

### 12.6 Cắt tại code point nhưng vẫn bể glyph trên màn hình

Từ có combining mark, emoji ZWJ hoặc emoji lá cờ có thể chứa nhiều code point trong một grapheme. Tách tại code point boundary vẫn có thể chia một ký tự hiển thị. Với yêu cầu UI, cần phân đoạn grapheme theo thuật toán Unicode thích hợp.

## 13. Chiến lược kiểm thử ranh giới và tính toàn vẹn

Khi triển khai hệ thống I/O text hoặc API chia chunk, nên kiểm tra **bất biến** thay vì chỉ nhìn bằng mắt:

- **Bất biến byte:** đối với thao tác chỉ chia/gom byte, `Arrays.equals(originalBytes, joinedChunks)` phải là `true`.
- **Bất biến văn bản:** đối với input UTF-8 hợp lệ, giải mã liên tục theo cùng charset phải tái tạo đúng chuỗi gốc: `original.equals(decoded)`.
- **Bất biến cặp surrogate:** các `String` con được tuyên bố là Unicode hợp lệ không được kết thúc bằng high surrogate lẻ hay bắt đầu bằng low surrogate lẻ.
- **Bất biến ngân sách byte:** `prefix.getBytes(UTF_8).length <= maxBytes`, đồng thời `prefix` phải là prefix liên tục của input theo đơn vị đã chọn.
- **Bất biến cụm hiển thị:** với yêu cầu giao diện, không chia giữa grapheme cluster, bao gồm trường hợp combining mark và emoji phức hợp.
- **Bất biến EOF:** chunk tạm thời hết byte không đồng nghĩa đã kết thúc stream; chỉ gọi decoder end-of-input khi có EOF thật.

Test ranh giới quan trọng nhất là cắt **ngay sau từng byte** của các chuỗi UTF-8 nhiều byte và **ngay giữa high/low surrogate**. Với `A😀B`, hãy thử mọi vị trí cắt từ byte 1 đến byte 5 và từ UTF-16 offset 1 đến 3; so sánh việc giải mã riêng từng mảnh với giải mã trên luồng được ghép.

Với dữ liệu không tin cậy, thêm ca UTF-8 malformed: byte continuation thiếu starter, starter thiếu continuation, chuỗi overlong, surrogate code point và code point ngoài dải hợp lệ. `CharsetDecoder` ở chế độ `REPORT` giúp kiểm tra phản ứng nhất quán trước lỗi.

## 14. Bài tập thực hành

1. Với chuỗi `"Xé😀Y"`, hãy ghi ra `length()`, `codePointCount(0, length())` và số byte UTF-8. Giải thích vì sao các con số khác nhau.
2. Tự cắt byte UTF-8 của emoji 😀 ở ba vị trí sai, giải mã từng đoạn bằng `new String(bytes, UTF_8)`, quan sát ký tự thay thế, rồi thử nối **byte** trước khi giải mã.
3. Dùng `String.offsetByCodePoints(0, n)` viết hàm lấy `n` code point đầu, sau đó kiểm thử với chữ Việt, emoji và ký tự kết hợp dấu.
4. Sửa ví dụ `FragmentedUtf8Read` để tạo mỗi lần đọc tối đa hai byte. Kiểm tra `Round-trip equal: true` vẫn giữ nguyên.
5. Sửa `Utf8ByteBudget` cho các ngân sách 0, 1, 4, 5 và 6 byte; viết assertion rằng đoạn trả về luôn giải mã UTF-8 đúng.
6. Xây dựng ví dụ chứa `"e\u0301"` (chữ `e` và combining acute accent). Hãy giải thích vì sao cắt theo một code point vẫn không bảo toàn cụm ký tự hiển thị.
7. Giả sử một hệ thống gửi nội dung UTF-8 qua network theo các chunk không cố định. So sánh ba cách: giải mã từng chunk bằng `new String`, gom toàn bộ byte rồi giải mã một lần, và dùng `InputStreamReader`/decoder streaming. Chọn cách phù hợp khi nội dung có thể rất lớn.
8. Cho `byte[]` chứa dữ liệu UTF-8 sai. Viết phương thức trả lỗi khi gặp byte sai bằng `newDecoder().onMalformedInput(REPORT)`, rồi giải thích vì sao cách này giúp tránh âm thầm mất dữ liệu.

## 15. Ghi nhớ và tài liệu đọc thêm

Điểm cốt lõi không phải tránh mọi phép chia buffer, mà là **giữ đúng ranh giới của đơn vị cần xử lý**:

1. **Byte stream** được phép chia thành các block bất kỳ nếu ghép byte lại đúng thứ tự trước khi decode, hoặc dùng decoder giữ trạng thái.
2. **UTF-8 text độc lập** phải giữ nguyên từng chuỗi byte của code point; mảnh byte thiếu không phải một ký tự hoàn chỉnh.
3. **Java `char` là UTF-16 code unit**; không cắt đôi high/low surrogate khi muốn tách code point. Sử dụng `String.offsetByCodePoints`/`codePoints`.
4. **Ký tự hiển thị** có thể gồm nhiều code point; phân đoạn theo grapheme khi ứng dụng/UI cần không tách chữ mà người dùng nhìn thấy.
5. **Lỗi decoding/encoding** cần chính sách rõ ràng (`REPORT`, `REPLACE`, `IGNORE`) và kiểm thử đầu vào nhiều byte, không chỉ ASCII.

Tài liệu chính thức để tra cứu: [`String` trong Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html), [`Character`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html), [`InputStreamReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html), [`CharsetDecoder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html), và [Unicode Text Segmentation (UAX #29)](https://unicode.org/reports/tr29/).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="text-encoding-012">Phân biệt charset với Base64 và nén dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt charset với Base64 và nén dữ liệu trong Java

**Charset, Base64 và nén dữ liệu giải quyết ba bài toán khác nhau** dù đều có thể biến đổi dãy byte:

- **Charset** quy định cách **mã hóa văn bản thành byte** và **giải mã byte thành văn bản**, chẳng hạn UTF-8.
- **Base64** biến **byte bất kỳ thành biểu diễn văn bản ASCII** có thể truyền qua môi trường chỉ nhận ký tự an toàn, rồi chuyển ngược về chính các byte đó.
- **Nén dữ liệu** biến **byte thành dạng byte được biểu diễn gọn hơn khi có thể**, và giải nén khôi phục byte gốc; kết quả nén không nhất thiết nhỏ hơn đầu vào.

Một chuỗi Base64 không cho biết văn bản ban đầu dùng charset nào; một luồng GZIP cũng không tự biến byte thành ký tự. Chọn sai lớp biến đổi sẽ tạo lỗi kiểu “chữ tiếng Việt bị vỡ”, “không giải mã được”, “file giải nén lỗi” hoặc “tưởng đã mã hóa bảo mật nhưng dữ liệu vẫn đọc được”.

## 1. Nhìn từ kiểu dữ liệu và mục đích

| Khái niệm | Đầu vào | Đầu ra | Mục đích | Có làm nhỏ dữ liệu không? |
| --- | --- | --- | --- | --- |
| Charset, ví dụ UTF-8 | Văn bản Unicode | Byte đã mã hóa | Biểu diễn ký tự thành byte để lưu/truyền | Không phải kỹ thuật nén |
| Giải mã charset | Byte hợp lệ theo charset | Văn bản Unicode | Khôi phục ký tự từ byte | Không phải giải nén |
| Base64 encode | Byte bất kỳ | Dãy ký tự ASCII | Đưa dữ liệu nhị phân qua kênh văn bản | Thường **tăng** dung lượng |
| Base64 decode | Dãy ký tự Base64 hợp lệ | Byte ban đầu | Phục hồi byte từ biểu diễn Base64 | Không phải giải nén |
| Nén lossless, ví dụ GZIP | Byte bất kỳ | Byte đã nén | Giảm lượng byte nếu dữ liệu có cấu trúc lặp/phù hợp | Thường giảm với dữ liệu thích hợp; có thể tăng |
| Giải nén GZIP | Byte GZIP hợp lệ | Byte ban đầu | Khôi phục chính xác byte gốc | Kết quả có thể lớn hơn rất nhiều |

Với các thao tác thông thường trong Java:

```text
               charset UTF-8
"Chào Java"  --------------------> byte[] UTF-8
     ^                                  |
     |                                  | Base64 encode
     | UTF-8 decode                     v
     +----------- byte[] <--------- "Q2jDoG8gSmF2YQ=="
                    ^                Base64 decode
                    |
          GZIP là một cách biến đổi
          byte[] -> byte[] riêng biệt
```

Sơ đồ chỉ mô tả mối quan hệ, không có nghĩa GZIP tự xuất hiện trong chuỗi Base64 bên phải. Khi kết hợp các bước, **thứ tự thực hiện và thứ tự đảo ngược** là yếu tố quyết định để lấy lại dữ liệu.

## 2. Charset: ý nghĩa của byte trong văn bản

Một `Charset` xác định quy tắc ánh xạ giữa ký tự Unicode và các byte, với quá trình encode/decode. Trong Java, `String` chứa văn bản dưới dạng các đơn vị mã UTF-16; còn file, socket hoặc `OutputStream` vận chuyển byte. `String.getBytes(StandardCharsets.UTF_8)` và `new String(bytes, StandardCharsets.UTF_8)` là hai hướng chuyển đổi thường gặp.

Ví dụ:

```java
import java.nio.charset.StandardCharsets;

String text = "Café";
byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(utf8, StandardCharsets.UTF_8);
```

Đoạn này là minh họa cần đặt trong một class/phương thức. Với `Café`, dãy byte UTF-8 (viết dưới dạng hexadecimal) là:

```text
43 61 66 C3 A9
 C  a  f   é
```

Chữ `é` dùng **hai byte** `C3 A9`, không phải hai ký tự. Trong UTF-8, ký tự ASCII thường có 1 byte, ký tự khác có thể dùng 2, 3 hoặc 4 byte. Ngược lại, một `char` của Java là một đơn vị UTF-16; ký tự ngoài BMP như `🙂` dùng cặp surrogate, tức hai `char`. Vì vậy phải phân biệt **byte**, **`char`** và **Unicode code point**.

Nếu chọn nhầm charset khi *decode*:

```java
String wrong = new String(utf8, StandardCharsets.ISO_8859_1);
```

Chuỗi `Café` có thể trở thành `CafÃ©`. Đây là **lỗi giải mã charset**, không liên quan đến Base64 hoặc nén. Không thể sửa đúng bằng cách “Base64 thêm một lần” hoặc “giải nén”.

Các nguyên tắc cần nhớ:

1. Charset đúng phải đến từ quy ước dữ liệu, giao thức, metadata đáng tin hoặc cấu hình nguồn/đích; **không tự đoán** charset từ kết quả hiển thị.
2. Ghi rõ `StandardCharsets.UTF_8` khi định dạng yêu cầu UTF-8. Tránh phụ thuộc default charset nếu dữ liệu cần tương thích giữa các môi trường.
3. Thao tác trên dữ liệu nhị phân (ảnh, ZIP, GZIP, byte đã mã hóa) thì giữ `byte[]` hoặc `InputStream`; không gọi `new String(binaryBytes, UTF_8)` chỉ để “chứa dữ liệu”.
4. Nếu byte lỗi hoặc ký tự không được charset hỗ trợ, encode/decode có thể **thay thế** dữ liệu hoặc ném lỗi tùy API/chính sách `CodingErrorAction`. Muốn kiểm tra nghiêm ngặt, dùng `CharsetDecoder`/`CharsetEncoder` được cấu hình `REPORT`.

## 3. Base64: biểu diễn byte bằng ký tự ASCII

Base64 chuẩn thường dùng bảng 64 ký tự:

```text
A-Z   a-z   0-9   +   /
```

Nó mã hóa từng nhóm **3 byte đầu vào thành 4 ký tự Base64**; khi phần cuối không đủ 3 byte, biểu diễn có thể có dấu đệm `=`. Vì vậy kích thước Base64 chuẩn có padding xấp xỉ **4/3** kích thước byte gốc, chưa tính thông tin bao bọc hoặc xuống dòng:

```text
byte[] { 0x4D, 0x61, 0x6E }    ASCII: "Man"
                |
                | Base64 encode
                v
             "TWFu"
                |
                | Base64 decode
                v
byte[] { 0x4D, 0x61, 0x6E }
```

**Base64 không sử dụng charset của văn bản gốc để hiểu ký tự**; nó chỉ biến dãy byte thành biểu diễn ASCII và quay lại. Với file ảnh, đầu vào vẫn là byte ảnh, không phải `String`.

Trong Java:

```java
import java.util.Base64;

byte[] rawBytes = /* dữ liệu nhị phân có sẵn */;
String encoded = Base64.getEncoder().encodeToString(rawBytes);
byte[] decoded = Base64.getDecoder().decode(encoded);
```

Đây là đoạn minh họa cần biến `rawBytes` được cung cấp từ nơi khác. `decoded` khớp với `rawBytes` nếu Base64 hợp lệ. `Base64.getDecoder().decode(...)` có thể ném `IllegalArgumentException` khi biểu diễn Base64 sai.

Nếu đầu vào ban đầu là văn bản, **phải chọn charset trước khi Base64**:

```java
String message = "Xin chào";
String encoded = Base64.getEncoder().encodeToString(
        message.getBytes(StandardCharsets.UTF_8));

byte[] raw = Base64.getDecoder().decode(encoded);
String restored = new String(raw, StandardCharsets.UTF_8);
```

`StandardCharsets` và `Base64` cần import tương ứng. Chú ý: `Base64.decode` chỉ trả về **byte**, còn `new String(..., UTF_8)` mới quyết định cách diễn giải byte thành ký tự.

### Chọn biến thể Base64

`java.util.Base64` có các biến thể đáp ứng bối cảnh khác nhau:

| Java API | Đặc điểm | Trường hợp điển hình |
| --- | --- | --- |
| `Base64.getEncoder()` / `getDecoder()` | Bảng chuẩn có `+`, `/` và padding mặc định | Nội dung Base64 thông thường |
| `Base64.getUrlEncoder()` / `getUrlDecoder()` | Dùng `-`, `_` thay cho `+`, `/` | Token/thành phần URL theo format cho phép Base64URL |
| `Base64.getMimeEncoder()` / `getMimeDecoder()` | Hỗ trợ biểu diễn MIME xuống dòng | MIME/email theo quy ước tương ứng |

Không tự hoán đổi hai bộ alphabet khi hệ thống gửi/nhận yêu cầu định dạng cố định. Padding có thể được bỏ bằng `withoutPadding()` khi giao thức cho phép; bên nhận phải tuân thủ đúng chuẩn biểu diễn đã chọn. **Base64 không phải mã hóa bảo mật**: bất kỳ ai có chuỗi Base64 hợp lệ đều có thể decode mà không cần khóa.

## 4. Nén: biến đổi byte để giảm dung lượng khi có thể

Nén **lossless** như GZIP cố biểu diễn cùng dữ liệu bằng ít byte hơn dựa trên mẫu lặp và cấu trúc dữ liệu. Khi giải nén thành công, byte đầu ra phải **giống chính xác** byte trước khi nén.

```text
byte[] nguyên bản
      |
      | GZIP compress
      v
byte[] GZIP (binary, gồm thông tin định dạng và dữ liệu nén)
      |
      | GZIP decompress
      v
byte[] nguyên bản
```

Ví dụ API chuẩn Java:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

static byte[] gzip(byte[] input) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream compressor = new GZIPOutputStream(out)) {
        compressor.write(input);
    }
    return out.toByteArray();
}

static byte[] gunzip(byte[] compressed) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPInputStream decompressor = new GZIPInputStream(
            new ByteArrayInputStream(compressed))) {
        decompressor.transferTo(out);
    }
    return out.toByteArray();
}
```

Hai phương thức này cần đặt trong một class. `GZIPOutputStream.close()` hoàn tất luồng GZIP, vì vậy `out.toByteArray()` chỉ được gọi sau khi khối `try-with-resources` đóng compressor. `GZIPInputStream` phải được cung cấp byte **thực sự theo định dạng GZIP**; nó không phải một công cụ “giải mã charset”.

Vì GZIP chứa header/trailer và metadata phục vụ giải nén/kiểm tra, với văn bản ngắn hoặc dữ liệu đã nén, **kết quả có thể lớn hơn đầu vào**. Các thuật ngữ và format cũng cần tách bạch:

- **GZIP** (ví dụ `GZIPInputStream`) là định dạng dòng nén theo quy cách GZIP, không phải file ZIP nhiều entry.
- **ZIP** (ví dụ `ZipInputStream`/`ZipOutputStream`) là định dạng container lưu nhiều entry và metadata.
- **DEFLATE** là thuật toán nén được dùng trong các format khác nhau; không nên giả định “cùng thuật toán” thì có thể mở tùy ý bằng bất kỳ lớp `GZIP`, `ZIP` hoặc `Inflater` nào.
- Nén **lossless** khác với nén có mất mát như một số codec ảnh/âm thanh. Trong xử lý `byte[]` và trao đổi dữ liệu cần khôi phục chính xác, phải chọn nén lossless.

## 5. Khi kết hợp: thứ tự tạo dữ liệu và khôi phục

Giả sử phải gửi **văn bản tiếng Việt**, muốn nén, nhưng kênh truyền chỉ nhận một chuỗi ký tự Base64. Pipeline đúng:

```text
CHIỀU GỬI
String Unicode
  -- UTF-8 encode --> byte[] UTF-8
  -- GZIP compress --> byte[] GZIP
  -- Base64 encode --> String ASCII Base64

CHIỀU NHẬN (ĐẢO THỨ TỰ)
String ASCII Base64
  -- Base64 decode --> byte[] GZIP
  -- GZIP decompress --> byte[] UTF-8
  -- UTF-8 decode --> String Unicode
```

Dữ liệu GZIP vốn là byte nhị phân. Ta chỉ đưa qua Base64 **nếu** giao thức đòi biểu diễn bằng chuỗi văn bản (chẳng hạn một trường JSON chứa Base64). Trong giao thức hỗ trợ binary, có thể gửi byte GZIP trực tiếp và bỏ qua Base64.

GZIP nên áp dụng trên byte văn bản **trước** Base64: Base64 làm tăng số ký tự và có thể giảm hiệu quả nén hoặc tạo thêm công việc. Tuy vậy, hiệu quả thực tế phụ thuộc dữ liệu và thiết lập, vì vậy đừng khẳng định mọi lần nén đều giảm kích thước.

### Ví dụ đầu-cuối Java 17+ chạy được

Ví dụ dùng cả ba biến đổi, sau đó giải theo thứ tự ngược. Ngoài ra chương trình kiểm tra trường hợp **chọn sai charset** để chỉ ra rằng giải được Base64 không đồng nghĩa đọc đúng tiếng Việt.

Lưu file đúng tên **`TextTransformsDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class TextTransformsDemo {

    public static void main(String[] args) throws IOException {
        String sourceText = "Chào Java ☕";

        // Bước 1: Charset encode (String -> byte UTF-8).
        byte[] utf8 = sourceText.getBytes(StandardCharsets.UTF_8);

        // Base64 riêng lẻ: không có tác dụng nén.
        String b64Text = Base64.getEncoder().encodeToString(utf8);
        byte[] decodedB64 = Base64.getDecoder().decode(b64Text);

        System.out.println("Source UTF-8 bytes: " + utf8.length);
        System.out.println("Base64 text length: " + b64Text.length());
        System.out.println("Base64 round-trip bytes equal: " +
                Arrays.equals(utf8, decodedB64));

        // Bước 2: GZIP (byte UTF-8 -> byte GZIP).
        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream zipper =
                     new GZIPOutputStream(compressedOut)) {
            zipper.write(utf8);
        }
        byte[] compressed = compressedOut.toByteArray();

        // Bước 3: Base64 (byte GZIP -> chuỗi ASCII).
        String transmitted = Base64.getEncoder()
                .encodeToString(compressed);

        // Bước 4: đảo chiều: Base64 -> GZIP bytes.
        byte[] receivedCompressed =
                Base64.getDecoder().decode(transmitted);

        // Bước 5: giải nén -> byte UTF-8.
        ByteArrayOutputStream restoredOut = new ByteArrayOutputStream();
        try (GZIPInputStream unzipper = new GZIPInputStream(
                new ByteArrayInputStream(receivedCompressed))) {
            unzipper.transferTo(restoredOut);
        }
        byte[] restoredBytes = restoredOut.toByteArray();

        // Bước 6: Charset decode (byte UTF-8 -> String).
        String restoredText =
                new String(restoredBytes, StandardCharsets.UTF_8);

        System.out.println("GZIP + Base64 round-trip bytes equal: " +
                Arrays.equals(utf8, restoredBytes));
        System.out.println("Restored text: " + restoredText);

        // Cùng byte, charset sai -> kết quả ký tự sai.
        String decodedWrongly =
                new String(utf8, StandardCharsets.ISO_8859_1);
        System.out.println("Wrong charset changes text: " +
                !sourceText.equals(decodedWrongly));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên, dùng terminal lưu được UTF-8:

```bash
javac -encoding UTF-8 TextTransformsDemo.java
java TextTransformsDemo
```

**Stdout chính xác:**

```text
Source UTF-8 bytes: 14
Base64 text length: 20
Base64 round-trip bytes equal: true
GZIP + Base64 round-trip bytes equal: true
Restored text: Chào Java ☕
Wrong charset changes text: true
```

Vì sao kết quả là như vậy?

- Chuỗi `"Chào Java ☕"` chiếm **14 byte UTF-8**: `Ch` = 2, `à` = 2, `o` = 1, khoảng trắng = 1, `Java` = 4, khoảng trắng = 1, `☕` = 3. Tổng `2 + 2 + 1 + 1 + 4 + 1 + 3 = 14`.
- Base64 chuẩn với padding của 14 byte cần `4 × ceil(14 / 3) = 20` ký tự ASCII. Base64 chỉ tạo biểu diễn văn bản; byte sau decode phải giống byte trước encode.
- GZIP rồi Base64, sau đó decode Base64 và giải nén GZIP, khôi phục **đúng từng byte**: `Arrays.equals` trả `true`. Việc so sánh `byte[]` là bằng chứng mạnh hơn chỉ nhìn dòng chữ hiển thị.
- Giải mã đúng UTF-8 khôi phục `"Chào Java ☕"`. Dùng ISO-8859-1 trên chính byte UTF-8 đó làm sai chữ có dấu và ký tự `☕`.

Chương trình cố ý **không in kích thước byte GZIP**, vì bài học là sự phân biệt ba cơ chế, còn byte GZIP dài bao nhiêu có thể phụ thuộc bản triển khai, mức nén và dữ liệu. Không được dùng ví dụ ngắn này để kết luận GZIP luôn giảm kích thước.

## 6. Chọn API nào trong một bài toán thực tế?

| Tình huống | Đúng hướng xử lý |
| --- | --- |
| Ghi nội dung Unicode vào file UTF-8 | `text.getBytes(UTF_8)` rồi ghi byte, hoặc `OutputStreamWriter` / `Files.writeString` với UTF-8 |
| Đọc file văn bản UTF-8 | `InputStreamReader(input, UTF_8)` / `Files.newBufferedReader(path, UTF_8)` |
| Gửi ảnh trong trường JSON Base64 theo hợp đồng API | Đọc byte ảnh rồi `Base64.getEncoder().encodeToString(bytes)` |
| Nhận trường JSON Base64 chứa ảnh | `Base64.getDecoder().decode(value)`, rồi xử lý như byte ảnh; **không** ép thành UTF-8 |
| Gửi file nhị phân qua kênh hỗ trợ binary | Gửi byte trực tiếp; không cần charset hoặc Base64 |
| Nén một luồng byte theo định dạng GZIP | `GZIPOutputStream`; bên nhận dùng `GZIPInputStream` |
| Gửi văn bản vừa nén vừa ở dạng Base64 | UTF-8 encode → GZIP → Base64; nhận thì đảo ngược |
| Giải nội dung ZIP nhiều file | Dùng `ZipInputStream` hoặc API ZIP thích hợp; không dùng `GZIPInputStream` |
| Tăng tính bí mật của dữ liệu | Dùng **mã hóa mật mã** phù hợp, ví dụ TLS khi truyền hoặc cơ chế encryption xác thực; Base64 và GZIP không bảo vệ bí mật |

### Ví dụ đọc một trường Base64 trong JSON

Một API có thể quy định trường `payload` chứa **Base64 của byte UTF-8**, không hề nén. Sau khi parser JSON đã lấy ra `String payload`:

```java
byte[] bytes = Base64.getDecoder().decode(payload);
String text = new String(bytes, StandardCharsets.UTF_8);
```

Đây là đoạn mã minh họa cần `payload`, import `Base64` và `StandardCharsets`. **Không gọi `GZIPInputStream`** nếu hợp đồng API không nói dữ liệu đã nén GZIP. Ngược lại, nếu API quy định “Base64 của GZIP”, cần thêm đúng bước giải nén sau Base64 decode rồi mới giải mã charset.

## 7. Phân biệt “encoding” với “encryption”

Cụm “mã hóa” trong tiếng Việt có thể chỉ *character encoding*, *binary-to-text encoding* hoặc *encryption*, nhưng chúng có mục tiêu rất khác nhau:

- **Character encoding** như UTF-8: biểu diễn chữ thành byte theo quy tắc chung, không có khóa bí mật.
- **Binary-to-text encoding** như Base64: biểu diễn byte thành ký tự an toàn theo quy tắc chung, không có khóa bí mật.
- **Compression** như GZIP: giảm độ dài biểu diễn trong các trường hợp phù hợp, không có khóa bí mật.
- **Encryption (mật mã)**: dùng khóa và thuật toán mật mã để bảo vệ nội dung. Ví dụ TLS bảo vệ dữ liệu khi truyền; mã hóa có xác thực còn cung cấp kiểm tra tính toàn vẹn và xác thực theo đặc tính thuật toán/giao thức.

Một chuỗi Base64 “trông khó đọc” vẫn có thể decode được bằng công cụ công khai. GZIP không phải encryption: ai có dữ liệu GZIP hợp lệ đều có thể giải nén. Nếu cần giữ bí mật, **không dựa vào Base64 hoặc GZIP**.

## 8. Các lỗi hay gặp và cách kiểm chứng

| Biểu hiện | Nguyên nhân thường gặp | Cách kiểm tra/sửa |
| --- | --- | --- |
| `Chào` trở thành ký tự lạ như `ChÃ o` | Byte UTF-8 được decode theo charset khác | Xác định charset thực tế; decode lại từ byte gốc |
| Base64 decode thành công nhưng text sai | Base64 phục hồi byte đúng, nhưng charset decode sai | So sánh byte; dùng đúng charset ở bước tiếp theo |
| `IllegalArgumentException` khi Base64 decode | Chuỗi sai alphabet/padding/định dạng hoặc dùng nhầm Base64URL | Kiểm tra format nguồn và decoder tương ứng |
| `ZipException: Not in GZIP format` | Đầu vào không phải GZIP, hoặc chưa Base64 decode, hoặc bị hỏng | Kiểm tra thứ tự và format thực tế |
| Nén dữ liệu ngắn ra file lớn hơn | Header/trailer và overhead vượt phần tiết kiệm | Thử trên dữ liệu thực, không coi tăng kích thước là lỗi |
| Ghi byte GZIP bằng `new String(gzipBytes, UTF_8)` | Dữ liệu nhị phân bị giải mã như văn bản và có thể bị thay thế | Ghi binary trực tiếp; nếu cần text, Base64 encode byte GZIP |
| Gửi chuỗi Base64 để “giấu” mật khẩu | Base64 chỉ là biểu diễn công khai | Dùng giao thức bảo vệ thích hợp; không coi Base64 là bí mật |
| Mở file `.zip` bằng `GZIPInputStream` | ZIP và GZIP là format khác nhau | Chọn đúng bộ đọc theo định dạng |
| Nén sau Base64 và kết luận mọi trường hợp nhỏ hơn | Base64 không phải nén, hiệu quả phụ thuộc dữ liệu | Nén byte trước Base64 khi cần cả hai; đo kết quả |
| Không giới hạn kích thước dữ liệu giải nén | Một đầu vào nén nhỏ có thể bung thành lượng byte rất lớn | Với dữ liệu không tin cậy, giới hạn byte đầu ra, thời gian, bộ nhớ và xử lý lỗi |

Khi cần chứng minh một chu trình khôi phục *lossless* là đúng, kiểm tra **byte ban đầu và byte sau toàn bộ chuỗi đảo ngược** bằng `Arrays.equals` cho dữ liệu nhỏ, hoặc hash/checksum, kích thước và quy trình xác minh đáng tin với dữ liệu lớn. Đừng chỉ nhìn hai chuỗi có vẻ giống nhau trên terminal: terminal, normalization Unicode và charset sai có thể che khuất khác biệt.

## 9. Câu hỏi tự kiểm tra

1. **Vì sao `Base64.decode` không thể tự biết “đây là tiếng Việt”?** Vì nó chỉ biết quy tắc khôi phục byte. Bên gọi phải biết charset để giải mã byte thành ký tự.
2. **UTF-8 có nén tốt hơn GZIP không?** Không thể so sánh như hai thuật toán nén: UTF-8 là character encoding, GZIP là nén lossless byte.
3. **Base64 có giữ nguyên dãy byte qua encode/decode không?** Có, với cặp encoder/decoder phù hợp và đầu vào biểu diễn hợp lệ; nó không có mục tiêu giữ nguyên số ký tự hay dung lượng.
4. **Tại sao 14 byte UTF-8 có thể thành 20 ký tự Base64?** Vì chuẩn Base64 dùng 4 ký tự cho mỗi nhóm tối đa 3 byte, thêm padding khi cần.
5. **Nhận một payload “Base64 của GZIP của UTF-8” thì xử lý theo thứ tự nào?** Base64 decode → GZIP decompress → UTF-8 decode.
6. **Có nên Base64 hóa byte ảnh rồi chuyển qua `String.getBytes(UTF_8)`?** Nếu giao thức cần truyền dạng text bằng byte UTF-8 thì có thể encode chuỗi Base64 ASCII ra byte UTF-8; nhưng việc này không làm ảnh thành văn bản Unicode. Khi nhận phải đảo đủ các bước và không decode trực tiếp byte ảnh như text.
7. **Vì sao GZIP không bảo mật?** Nó là format nén không dùng khóa bí mật; bất kỳ người nhận có dữ liệu GZIP hợp lệ đều có thể giải nén.

**Ghi nhớ ba câu:** **Charset quyết định byte đại diện cho chữ gì. Base64 quyết định byte được trình bày dưới dạng ký tự an toàn ra sao. Nén quyết định byte có thể được lưu/truyền gọn hơn như thế nào.** Muốn khôi phục chính xác, phải áp dụng các bước ngược theo thứ tự đảo và với đúng charset/format.

</details>

- [Quay lại đầu trang](#back-to-top)
