<a id="back-to-top"></a>

# 3. Dữ liệu và ngẫu nhiên

## Menu
- [3.1 Chuyển String thành byte&#91;&#93; với charset xác định và quy tắc chuẩn hóa dữ liệu](#data-randomness-001)
- [3.2 Base64, HexFormat và sự khác biệt giữa biểu diễn dữ liệu với bảo vệ bí mật](#data-randomness-002)
- [3.3 Phân biệt số bit, số byte, độ dài khóa và độ dài đầu ra](#data-randomness-003)
- [3.4 Vai trò của entropy; phân biệt SecureRandom với Random và ThreadLocalRandom](#data-randomness-004)
- [3.5 Tạo byte ngẫu nhiên, seed, reseed và tác động của seed có thể dự đoán](#data-randomness-005)
- [3.6 getInstanceStrong, DRBG và khả năng chặn của nguồn ngẫu nhiên](#data-randomness-006)
- [3.7 Phân biệt salt, IV, nonce, challenge và token ngẫu nhiên](#data-randomness-007)
- [3.8 Thiết kế định dạng dữ liệu có phiên bản, độ dài và mã định danh thuật toán](#data-randomness-008)

## <a id="data-randomness-001">3.1 Chuyển String thành byte&#91;&#93; với charset xác định và quy tắc chuẩn hóa dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# 3.1 Chuyển String thành byte[] với charset xác định và quy tắc chuẩn hóa dữ liệu

Trong Java, `String` biểu diễn văn bản; `byte[]` biểu diễn dãy byte. Mật mã, hash, HMAC, chữ ký số, lưu trữ và giao thức mạng cuối cùng đều xử lý **byte cụ thể**, không xử lý “chữ trông giống nhau”. Bởi vậy, trước khi đưa văn bản vào một thuật toán mật mã, cần quyết định rõ **cách mã hóa ký tự (charset)** và **quy tắc chuẩn hóa dữ liệu (normalization/canonicalization)**. Một quyết định thiếu nhất quán có thể tạo digest khác nhau, làm xác minh chữ ký thất bại, khiến dữ liệu giải mã không khớp hoặc gây ra bất đồng về danh tính/ủy quyền.

Ví dụ, hai chuỗi cùng hiển thị gần như “Café”:

- `"Café"`: chữ `é` là một Unicode code point U+00E9.
- `"Cafe\u0301"`: chữ `e` U+0065 đi cùng dấu sắc kết hợp U+0301.

Chúng có thể nhìn giống nhau nhưng **`String.equals` trả `false` và byte UTF-8 ban đầu khác nhau**. Chuẩn hóa NFC có thể đưa chúng về một biểu diễn tương đương trước khi mã hóa UTF-8, nếu đó là chính sách đã được hai bên thống nhất.

## 1. Kết quả cần đạt

Sau bài này, bạn có thể:

1. Phân biệt `String`, UTF-16 code unit, Unicode code point, ký tự người dùng nhìn thấy (*grapheme cluster*) và byte.
2. Chuyển đổi văn bản bằng `StandardCharsets.UTF_8` theo cách xác định, thay vì phụ thuộc charset mặc định của môi trường.
3. Hiểu NFC, NFD, NFKC, NFKD; chọn đúng chuẩn hóa cho **từng loại dữ liệu**, nhận biết trường hợp không nên chuẩn hóa.
4. Nhận biết và chủ động xử lý input Unicode/UTF-8 không hợp lệ.
5. Thiết kế byte đầu vào nhất quán cho digest, HMAC, chữ ký số, AEAD AAD và lưu trữ; phân biệt chuẩn hóa văn bản với canonicalization của một giao thức.
6. Kiểm thử được kết quả byte, tính tương thích giữa các hệ thống và các tình huống Unicode khó.

## 2. Các lớp biểu diễn: từ người đọc đến byte

Có ít nhất bốn lớp cần tách rõ:

| Lớp | Đơn vị hoặc cấu trúc | Ví dụ | Điều cần nhớ |
| --- | --- | --- | --- |
| Văn bản hiển thị | Grapheme cluster; có thể gồm nhiều code point | Chữ `e` kèm dấu sắc | “Một ký tự nhìn thấy” không nhất thiết là một phần tử của `char[]` |
| Unicode | Code point như U+00E9, U+0301, U+1F510 | `é`, dấu kết hợp, emoji | Code point là giá trị trừu tượng; không đồng nhất với số byte |
| Java `String` | Chuỗi **UTF-16 code unit**; `char` là 16 bit | Emoji 🔐 cần một cặp surrogate, tức hai `char` | `String.length()` đếm code unit, không đếm code point |
| Byte | Chuỗi octet theo charset đã chọn | `é` thành `C3 A9` khi dùng UTF-8 | Mật mã làm việc trên chính các byte này |

Cần phân biệt:

- `String.length()`: số **UTF-16 code unit**.
- `String.codePointCount(0, s.length())`: số **code point** theo biểu diễn UTF-16; không đếm chính xác số grapheme cluster.
- `s.getBytes(StandardCharsets.UTF_8).length`: số byte sau khi mã hóa; thường khác cả hai con số trên.
- `byte[].length`: độ dài byte thực sự để lưu, truyền, băm hoặc ký.

Ví dụ emoji **🔐** là một Unicode code point, nhưng trong Java có `length() == 2` và khi mã hóa UTF-8 hợp lệ dài **4 byte**. Không dùng `substring` hoặc phép cắt theo chỉ số `char` tùy tiện ở giữa cặp surrogate.

### Charset là quy tắc ánh xạ, không phải “bộ chữ” trên giao diện

Charset quy định cách ánh xạ ký tự sang byte và ngược lại:

```text
String (Unicode/UTF-16 trong Java)
    -- [charset encoder: UTF-8] -->
byte[] (octet cụ thể)
    -- [charset decoder: UTF-8] -->
String (Unicode/UTF-16 trong Java)
```

Cùng văn bản, charset khác nhau có thể cho byte khác nhau. Với `"é"`:

- UTF-8: `C3 A9` — hai byte.
- UTF-16BE: `00 E9` — hai byte, big-endian.
- UTF-16LE: `E9 00` — hai byte, little-endian.

Với ký tự ngoài ASCII, giải mã bằng **khác charset** có thể tạo mojibake, thay ký tự hoặc lỗi. Với dữ liệu nhị phân thuần túy như ciphertext, salt, nonce, khóa, digest, không có “charset đúng” để chuyển thành String một cách tùy ý; dùng các định dạng nhị phân hoặc **Base64/hex** khi thật sự cần biểu diễn văn bản.

## 3. Quy tắc Java: chỉ định charset tường minh

Trong ứng dụng hiện đại, dùng UTF-8 khi hợp đồng giao thức chưa yêu cầu charset khác:

```java
import java.nio.charset.StandardCharsets;

String message = "Xin chào Java 🔐";
byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
```

Đây là **đoạn mã minh họa**, có thể đặt trong một method có import như trên. `StandardCharsets.UTF_8` là hằng có sẵn trong JDK, không cần tra tên charset khi chạy. Tương tự, JDK cung cấp `US_ASCII`, `ISO_8859_1`, `UTF_16`, `UTF_16BE` và `UTF_16LE`; chỉ dùng khi giao thức thực sự quy định chúng.

Tránh xây dựng hợp đồng bảo mật dựa trên:

```java
byte[] bytes = message.getBytes();    // Không chỉ rõ charset
String text = new String(bytes);       // Không chỉ rõ charset
```

Các overload không có tham số dùng default charset của runtime. Java 18+ mặc định UTF-8 theo JEP 400 trong các tình huống thông thường, còn Java 17 và một số môi trường/cấu hình có thể khác; **giao thức không nên phụ thuộc vào chi tiết này**. Ngay cả khi mọi máy hiện tại dùng UTF-8, vẫn nên ghi charset trong code và trong đặc tả dữ liệu.

Cũng không dùng `getBytes("UTF-8")` khi `StandardCharsets.UTF_8` đã có sẵn, trừ khi API yêu cầu tên charset; đối tượng `Charset` có kiểu dữ liệu rõ ràng và tránh checked exception do tìm kiếm tên charset không hợp lệ.

### Tính xác định cần cả hai hướng

Với input Unicode hợp lệ và cùng charset, quá trình mã hóa UTF-8 là xác định. Tuy nhiên, `new String(bytes, UTF_8)` **có thể thay byte UTF-8 không hợp lệ bằng ký tự thay thế U+FFFD**, và `String.getBytes(UTF_8)` có thể thay một `char` surrogate lẻ không hợp lệ bằng byte thay thế. Cách tiện lợi này hữu ích cho hiển thị văn bản hỏng nhưng có thể làm mất thông tin về byte đầu vào.

Nếu byte phải được kiểm tra nghiêm ngặt, ví dụ giao thức ký, định danh, giải mã bản tin hoặc API bảo mật, dùng `CharsetEncoder` / `CharsetDecoder` với `CodingErrorAction.REPORT` và xử lý `CharacterCodingException`. Bài chạy đầy đủ ở mục 7 minh họa cả hai chiều.

**Quy tắc:** đầu vào nhị phân đến từ mạng/file phải được giải mã theo một charset được công bố, có chính sách lỗi rõ ràng; không âm thầm “sửa” chuỗi mà bạn sắp đưa vào phép kiểm chứng mật mã.

## 4. Unicode normalization: cùng ý nghĩa hiển thị chưa chắc cùng byte

Unicode cho phép nhiều cách biểu diễn tương đương về mặt chuẩn. Java có `java.text.Normalizer` với bốn dạng:

| Form | Loại | Đặc tính | Cách dùng điển hình |
| --- | --- | --- | --- |
| **NFC** | Canonical composition | Tách theo quy tắc tương đương chuẩn tắc, rồi kết hợp thành dạng soạn trước khi có thể | Mặc định hợp lý cho nhiều trường văn bản cần so sánh ổn định |
| **NFD** | Canonical decomposition | Phân rã các ký tự tương đương chuẩn tắc thành dạng phân tách | Xử lý văn bản cần tách dấu, khi có mục đích rõ |
| **NFKC** | Compatibility composition | Thêm phân rã tương thích rồi kết hợp; có thể gộp biểu diễn khác nghĩa/kiểu chữ | Một số chính sách định danh được đặc tả kỹ, không phải mặc định toàn hệ thống |
| **NFKD** | Compatibility decomposition | Phân rã tương thích và giữ dạng tách | Pipeline xử lý đặc thù, không phải lựa chọn tự động cho secret |

API Java:

```java
import java.text.Normalizer;

String nfc  = Normalizer.normalize(input, Normalizer.Form.NFC);
String nfd  = Normalizer.normalize(input, Normalizer.Form.NFD);
String nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC);
String nfkd = Normalizer.normalize(input, Normalizer.Form.NFKD);
```

Đây là đoạn mã minh họa; giả sử `input` là một `String` hợp lệ. Một ví dụ dễ kiểm chứng:

```text
"Café"           -> UTF-8 raw: 43 61 66 C3 A9
"Cafe\u0301"      -> UTF-8 raw: 43 61 66 65 CC 81

normalize(NFC) cả hai -> "Café"
UTF-8 sau NFC         -> 43 61 66 C3 A9
```

Ví dụ tương thích: ký tự ligature `ﬃ` (U+FB03) vẫn là `ﬃ` khi dùng NFC; khi NFKC có thể thành chuỗi `ffi`. Do đó NFKC **có khả năng gộp biểu diễn mang sự khác biệt mà ứng dụng quan tâm**.

Không suy ra rằng normalization giải quyết mọi trường hợp “trông giống nhau”:

- `a` Latin và một chữ tương tự thuộc bảng chữ khác có thể nhìn giống nhưng **không phải cùng code point**, không tự trở thành một ký tự sau NFC.
- Chữ hoa/chữ thường, khoảng trắng, dấu gạch, dấu xuống dòng `\r\n`/`\n`, ký tự zero-width, URL percent-encoding, HTML entity và thứ tự thuộc tính JSON là **các vấn đề riêng**. NFC không thay thế mọi phép canonicalization đó.
- `Normalizer` không phải API phát hiện Unicode spoofing hoặc tính an toàn của identifier. Với ID công khai, cần chính sách tập ký tự, phát hiện nhầm lẫn và kiểm soát đăng ký tên theo sản phẩm.
- Một chuỗi có thể chứa code point không hợp lệ ở mức Unicode scalar value do surrogate lẻ trong Java; normalize không thay cho strict encoding.
- Việc dùng `toLowerCase(Locale.ROOT)` nếu có chính sách không phân biệt hoa/thường **không tương đương** với đầy đủ Unicode case folding. Không ghép lowercase tùy ý vào quy tắc định danh mật mã nếu hai hệ thống chưa thống nhất thuật toán.

### Đặt normalization trước hay sau mã hóa?

Normalization áp dụng lên **văn bản Unicode**. Quy trình có chủ đích:

```text
input text -> kiểm tra chính sách trường dữ liệu
           -> Unicode normalization đã chọn
           -> kiểm tra lại tính hợp lệ của dạng chuẩn
           -> encode UTF-8 nghiêm ngặt
           -> byte[] cho thuật toán/giao thức
```

Nếu nguồn là byte UTF-8: **decode strict -> normalize theo chính sách -> encode strict**. Nếu bạn đang cần kiểm tra chữ ký trên **body byte gốc**, phải kiểm chữ ký theo đặc tả trên byte gốc; không decode/normalize rồi giả định byte sau đó vẫn là body mà bên gửi đã ký.

Tính *idempotent* là yêu cầu tốt cho pipeline: sau khi áp cùng một form lên dữ liệu đã chuẩn hóa, lần lặp không nên tiếp tục thay đổi dữ liệu. Nhưng nếu pipeline còn thêm trim, lowercase, collapse whitespace, URL decode, serialize JSON hoặc quy tắc nghiệp vụ, hãy đánh giá các bước đó **riêng** và định nghĩa thứ tự chính xác.

## 5. Chọn chính sách theo dữ liệu, không áp dụng một hàm cho tất cả

**NFC + UTF-8 không phải “công thức vạn năng”.** Charset có thể thống nhất toàn giao thức, nhưng normalization cần dựa trên ý nghĩa trường dữ liệu và yêu cầu tương thích.

| Dữ liệu đầu vào | Gợi ý chính sách | Vì sao |
| --- | --- | --- |
| Tên hiển thị hoặc văn bản tìm kiếm | Có thể lưu/so sánh ở NFC nếu hợp đồng quy định; giữ bản gốc khi cần | Giúp giảm khác biệt chuẩn tắc nhưng vẫn tôn trọng cách người dùng nhập |
| Định danh tài khoản/tenant do người dùng chọn | Chính sách định danh riêng: form, tập ký tự, case handling, độ dài, reserved names, uniqueness | Tránh hai tên trông khác/giống đi qua các kiểm tra không nhất quán; cần chống mạo danh |
| Mã tài liệu, mã giao dịch có định dạng máy | Validate theo grammar rõ ràng (ví dụ ASCII giới hạn); không tự thay đổi ngoài quy tắc | ID là khóa nghiệp vụ và đầu vào phân quyền |
| Mật khẩu, passphrase | Tuân thủ **chính xác** quy ước đã công bố ở đăng ký và xác thực; không tự trim/lowercase/đổi form giữa chừng | Bất kỳ byte khác nào tạo kết quả xác minh khác; thay đổi chính sách cần migration có chủ đích |
| Dữ liệu/JSON đã được ký bởi bên khác | Ký/xác minh theo **byte chính xác** hoặc canonicalization mà giao thức chữ ký công bố | Hai cách serialize “cùng dữ liệu” thường cho byte khác |
| Chuỗi đưa vào AAD của AES-GCM | Dùng byte và thứ tự trường được định nghĩa, có version; giữ khả năng tái tạo trong lần giải mã | AAD lệch một byte khiến kiểm tra tag thất bại |
| Base64 của ciphertext/nonce/salt/key | Decode Base64 thành byte; không Unicode-normalize nội dung nhị phân | Secret/ciphertext là byte chứ không phải văn bản tự nhiên |
| Email, hostname quốc tế hoặc URL | Tuân thủ đặc tả xử lý **đúng miền** (IDNA, URL parser, case rules, v.v.) | NFC đơn lẻ không mô tả toàn bộ cú pháp, bảo mật và so sánh |

**Đặc biệt với mật khẩu:** nếu hệ thống đang dùng chính sách giữ nguyên chuỗi, đăng nhập phải dùng chính xác quy tắc của đăng ký. Nếu tiêu chuẩn nghiệp vụ yêu cầu normalization, cả hai đầu phải thống nhất từ đầu và có kế hoạch phiên bản dữ liệu. Không tự động xóa dấu, đổi chữ hoa, trim hoặc chuyển NFKC một cách âm thầm chỉ vì muốn “dễ đăng nhập”. Sau khi xác định byte, mật khẩu phải đi qua **password hashing/KDF chuyên dụng** với salt và tham số cost (chẳng hạn Argon2id theo chính sách), không dùng SHA-256 đơn lẻ, và không trực tiếp dùng `getBytes` làm khóa AES.

Với token ngẫu nhiên, khóa, IV/nonce và ciphertext, lựa chọn đúng là **bảo toàn byte**. Chuỗi Base64/hex là một *encoding dành cho vận chuyển*, không biến dữ liệu thành “chữ cần normalize”.

## 6. Ví dụ Java 17+ hoàn chỉnh: charset, NFC và xử lý lỗi nghiêm ngặt

Lưu chính xác thành file **`UnicodeBytesDemo.java`** với nội dung sau:

```java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

public class UnicodeBytesDemo {
    static byte[] strictUtf8(String text) throws CharacterCodingException {
        ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    static String strictDecode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    static byte[] canonicalBytes(String text) throws CharacterCodingException {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        return strictUtf8(Normalizer.normalize(text, Normalizer.Form.NFC));
    }

    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public static void main(String[] args) throws Exception {
        String composed = "Café";
        String decomposed = "Cafe\u0301";
        System.out.println("Same raw String: " + composed.equals(decomposed));
        System.out.println("Composed UTF-8: " + hex(strictUtf8(composed)));
        System.out.println("Decomposed UTF-8: " + hex(strictUtf8(decomposed)));

        byte[] a = canonicalBytes(composed);
        byte[] b = canonicalBytes(decomposed);
        System.out.println("NFC bytes equal: " + Arrays.equals(a, b));
        System.out.println("NFC UTF-8: " + hex(a));
        System.out.println("NFC digest equal: " + Arrays.equals(
                MessageDigest.getInstance("SHA-256").digest(a),
                MessageDigest.getInstance("SHA-256").digest(b)));

        System.out.println("NFKC of ligature: " +
                Normalizer.normalize("ﬃ", Normalizer.Form.NFKC));

        String emoji = "🔐";
        System.out.println("Emoji UTF-16 units: " + emoji.length());
        System.out.println("Emoji code points: " + emoji.codePointCount(0, emoji.length()));
        System.out.println("Emoji UTF-8 bytes: " + strictUtf8(emoji).length);
        System.out.println("UTF-8 round trip: " +
                composed.equals(strictDecode(strictUtf8(composed))));

        try {
            strictUtf8(String.valueOf((char) 0xD800));
            System.out.println("Malformed surrogate accepted");
        } catch (CharacterCodingException e) {
            System.out.println("Malformed surrogate rejected");
        }

        try {
            strictDecode(new byte[] {(byte) 0xC3, 0x28});
            System.out.println("Malformed UTF-8 accepted");
        } catch (CharacterCodingException e) {
            System.out.println("Malformed UTF-8 rejected");
        }
    }
}
```

Lệnh chạy ở thư mục chứa file, sử dụng JDK **17 trở lên**:

```bash
javac --release 17 UnicodeBytesDemo.java
java UnicodeBytesDemo
```

**Stdout chính xác:**

```text
Same raw String: false
Composed UTF-8: 436166c3a9
Decomposed UTF-8: 43616665cc81
NFC bytes equal: true
NFC UTF-8: 436166c3a9
NFC digest equal: true
NFKC of ligature: ffi
Emoji UTF-16 units: 2
Emoji code points: 1
Emoji UTF-8 bytes: 4
UTF-8 round trip: true
Malformed surrogate rejected
Malformed UTF-8 rejected
```

Diễn giải từng nhóm kết quả:

1. `equals` trên hai `String` thô là `false`. Các hex khác nhau vì `C3 A9` mã hóa U+00E9, còn `65 CC 81` mã hóa hai code point U+0065 và U+0301.
2. `Normalizer.Form.NFC` khiến byte của hai chuỗi này bằng nhau. Digest SHA-256 sau cùng cũng bằng nhau vì **input byte đã bằng nhau**; SHA-256 không tự chuẩn hóa Unicode.
3. `NFKC` đổi ligature `ﬃ` thành `ffi`. Đây là lý do không chọn NFKC chỉ vì “trông bình thường hơn”.
4. Emoji chứng tỏ code unit, code point và số byte là ba đại lượng khác nhau.
5. Encoder/decoder ở chế độ `REPORT` từ chối surrogate lẻ và byte UTF-8 sai, thay vì âm thầm làm mất khả năng kiểm tra byte nguyên gốc.

`MessageDigest` ở ví dụ này chỉ dùng để **minh họa tính phụ thuộc vào byte**, không phải ví dụ lưu trữ mật khẩu. `HexFormat` được thêm từ Java 17; với Java 11 không thể dùng nguyên ví dụ này mà không đổi phần hiển thị hex.

### Khi nào nên dùng encoding/decoding strict?

Có hai chế độ thường gặp:

- **Thuận tiện:** `text.getBytes(UTF_8)`, `new String(bytes, UTF_8)`. Hợp lý cho văn bản đã được kiểm tra hoặc hiển thị với chính sách cho phép replacement.
- **Bảo toàn tính hợp lệ:** `CharsetEncoder/Decoder` với `REPORT`. Nên cân nhắc cho định danh, chữ ký, bản tin có cú pháp nghiêm ngặt và bất kỳ nơi nào thay thế ký tự có thể làm thay đổi quyết định bảo mật.

Không xử lý lỗi bằng cách “thử charset khác” sau khi UTF-8 không hợp lệ, trừ khi giao thức **thật sự có cơ chế phát hiện charset** rõ ràng. Thử lùi qua nhiều encoding có thể làm hai thành phần hiểu cùng dữ liệu theo cách khác nhau.

## 7. Ứng dụng trong hash, HMAC và chữ ký số

Các phép mật mã phụ thuộc chính xác vào byte: `SHA256(byte[])`, `Mac.doFinal(byte[])` và `Signature.update(byte[])`. Vì vậy, câu “hai bên cùng ký nội dung text” chưa đủ làm đặc tả. Hai bên phải thống nhất:

1. **Dữ liệu nào được ký:** raw HTTP body, string field sau parse, hay một cấu trúc canonical được serialize?
2. **Bộ mã ký tự:** thường UTF-8; có BOM hay không; chấp nhận byte UTF-8 sai không?
3. **Normalization:** không normalize, hay NFC, hay một profile/version cụ thể?
4. **Cấu trúc và ranh giới trường:** thứ tự trường, separator, quy tắc escape, null/empty, độ dài, cách ký số và thời gian.
5. **Vòng đời chính sách:** trường phiên bản trong dữ liệu ký, cách xử lý bản ghi cũ nếu chính sách đổi.

### 7.1 Digest của chuỗi

Ví dụ:

```text
Digest A = SHA-256( UTF8("Café") )
Digest B = SHA-256( UTF8("Cafe\u0301") )
Digest A != Digest B

Digest C = SHA-256( UTF8(NFC("Café")) )
Digest D = SHA-256( UTF8(NFC("Cafe\u0301")) )
Digest C == Digest D
```

Kết quả không xuất phát từ “SHA-256 hiểu Unicode”; SHA-256 chỉ nhận dãy byte. Với dữ liệu gốc phải giữ nguyên (file upload, artifact, forensic log), **hash byte gốc**, đừng tự normalize. Đối với chuỗi được định nghĩa bằng chính sách NFC, mới hash byte đã qua NFC.

### 7.2 HMAC và chữ ký cho HTTP/JSON

Giả sử đối tác gửi JSON:

```json
{"name":"Café","amount":10}
```

Bên khác có thể tạo JSON có cùng dữ liệu nhưng thêm khoảng trắng, đảo thứ tự field, escape ký tự thành `\uXXXX` hoặc dùng `10.0` thay `10`. Các body đó có thể cho **byte khác**, dù parser có thể xây dựng cấu trúc tương tự. Unicode NFC **không canonicalize JSON**.

Hai cách hợp lệ phải được đặc tả rõ:

- **Ký raw body bytes:** sender tạo HMAC/chữ ký trên đúng body byte đã gửi; receiver kiểm tra trên **byte nhận nguyên vẹn**, trước khi deserialize/serialize lại. Đừng chuyển body thành String rồi dựng JSON lại để kiểm chữ ký.
- **Ký representation canonical:** cả sender và receiver tuân theo cùng một đặc tả canonical serialization (bao gồm UTF-8, thứ tự khóa, số, escape và các giới hạn khác), cùng version. Nếu dùng JSON Canonicalization Scheme (RFC 8785), hiểu chính xác các quy tắc của chuẩn; **không tự giả định nó chuẩn hóa Unicode string về NFC**.

Khi nối nhiều field để tạo input ký, tránh nối không có ranh giới: `("ab", "c")` và `("a", "bc")` đều tạo `"abc"`. Hãy dùng một canonical serialization có grammar rõ ràng hoặc **length-prefix** trên byte, kèm domain separation cho mục đích ký. Một dấu `:` đơn lẻ chưa đủ nếu dữ liệu được phép chứa `:` mà không escape.

**Tình huống dễ lỗi:** người gửi ký `method + path + body` sau khi URL decode một lần, còn người nhận canonicalize path khác hoặc decode hai lần. Khi đó có thể xảy ra mismatch chữ ký hoặc bỏ qua kiểm tra quyền. Normalization Unicode không thay thế canonicalization URL, path, query, header và request body theo đặc tả giao thức.

### 7.3 Timing và so sánh kết quả bảo mật

So sánh chữ ký/HMAC phải dùng cơ chế kiểm tra an toàn do thư viện/giao thức cung cấp. Khi so sánh hai digest/MAC byte cùng dạng trong code Java thuần, `MessageDigest.isEqual(a, b)` là công cụ thích hợp hơn `Arrays.equals` cho mục đích kiểm tra kết quả bảo mật; tuy nhiên, tính an toàn của toàn quy trình còn phụ thuộc phân tích độ dài, giao thức, key, algorithm và các bước trước khi so sánh. Trong ví dụ mục 6, `Arrays.equals` chỉ dùng để minh họa hai dãy byte hoặc digest từ dữ liệu công khai giống nhau; **đó không phải logic xác thực MAC**.

Không tự viết mật mã, MAC hoặc parser canonicalization tùy ý khi đã có thư viện và chuẩn giao thức được duy trì.

## 8. Mã hóa, giải mã và AAD: nhất quán byte qua nhiều năm

Khi AES-GCM được sử dụng cùng **AAD** (*Additional Authenticated Data*), bản mã được bảo vệ bằng authentication tag gắn với AAD byte ban đầu. Ví dụ AAD có thể mã hóa `tenantId`, `documentId` và phiên bản schema. Nếu hệ thống mã hóa bằng `UTF8(NFC(tenantId))` nhưng giải mã bằng UTF-8 thô hoặc đổi thứ tự các trường, kiểm tra tag có thể thất bại, dù nội dung hiển thị giống nhau.

Thiết kế phù hợp:

- Định nghĩa **định dạng nhị phân hoặc serialization không mơ hồ** cho AAD; ví dụ version + tên miền mục đích + các trường có length-prefix.
- Chuẩn hóa những **trường văn bản được phép chuẩn hóa**, trước khi serialize theo đặc tả; không normalize `byte[]` ciphertext/nonce/salt/key.
- Lưu version hoặc đủ metadata để dựng lại byte AAD khi đọc dữ liệu cũ; nếu có thể, lưu chính xác AAD đã xác thực hoặc một canonical representation được đặc tả bền vững.
- Dùng API AEAD đúng cách, gồm yêu cầu **không tái sử dụng nonce với cùng khóa** cho AES-GCM. Việc chọn charset/normalization **không thay thế** các yêu cầu sử dụng mật mã an toàn đó.

### Truyền dữ liệu nhị phân bằng Base64

**Sai về nguyên tắc:**

```java
String ciphertextAsText = new String(ciphertext, StandardCharsets.UTF_8);
```

Đây là **đoạn minh họa anti-pattern**, giả sử `ciphertext` là `byte[]`. Ciphertext ngẫu nhiên có thể chứa byte UTF-8 không hợp lệ. Việc ép nó thành String thường làm mất dữ liệu; khi encode ngược lại sẽ không thu được dãy byte gốc.

**Đúng khi cần lưu/truyền bằng văn bản:**

```java
import java.util.Base64;

String encoded = Base64.getEncoder().encodeToString(ciphertext);
byte[] restoredCiphertext = Base64.getDecoder().decode(encoded);
```

Các dòng là đoạn minh họa, biến `ciphertext` đã có từ trước. Base64 là **mã hóa biểu diễn**, không phải mã hóa bảo mật: ai có chuỗi Base64 cũng có thể decode. Không ghi ciphertext, key hay token vào log chỉ vì chúng đã ở dạng Base64.

## 9. Nguy cơ bảo mật từ canonicalization không thống nhất

Một hệ thống có thể thực hiện nhiều kiểm tra trên cùng chuỗi: gateway, bộ lọc đầu vào, authorization, cache, DB, file system. Nếu mỗi lớp hiểu chuỗi khác nhau, dữ liệu có thể được kiểm tra theo một danh tính nhưng truy xuất dưới danh tính khác.

Ví dụ nghiệp vụ:

- Gateway kiểm tra quyền đối với một đường dẫn đã decode **một lần**, service hoặc proxy khác decode **thêm lần nữa** và truy cập đường dẫn khác.
- Application lưu `username` với NFC, nhưng uniqueness check so với input thô. Hai biểu diễn chuẩn tắc tương đương có thể đi qua hai quyết định khác nhau.
- API xác thực `tenantId` theo chuỗi đã trim, còn truy vấn DB sử dụng giá trị chưa trim; authorization và retrieval có thể lệch nhau.
- Một client so sánh Unicode bằng quy tắc case-insensitive khác server, rồi chọn nhầm tài khoản hoặc đối tượng.
- Ứng dụng thay newline `\r\n` thành `\n` trước khi kiểm HMAC trong khi đối tác ký byte raw; chữ ký hợp lệ bị từ chối.

Quy tắc phòng tránh không chỉ là “normalize mọi thứ”. Quy trình đúng:

1. **Đọc byte theo một giao thức rõ ràng.** HTTP, JSON, path, query và form có quy tắc giải mã riêng.
2. **Phân tích cấu trúc và kiểm tra sự hợp lệ**, từ chối chuỗi sai encoding, ambiguous encoding hoặc input ngoài giới hạn theo chính sách.
3. **Chuẩn hóa ở ranh giới nghiệp vụ phù hợp**, chỉ với những trường mà policy quy định và theo đúng thứ tự.
4. **So sánh, kiểm tra quyền, lưu và truy vấn trên cùng dạng định danh**, không dùng bản raw ở một lớp và bản normalized ở lớp khác.
5. **Bảo toàn dữ liệu nguyên gốc khi cần:** file, raw body để kiểm chữ ký, log điều tra, chứng từ, dữ liệu bên thứ ba; đừng thay đổi chứng cứ trước khi hash.
6. **Gắn version và kiểm thử liên hệ nhiều hệ thống** nếu thuật toán canonicalization là phần của hợp đồng dài hạn.

Tránh double decoding, double normalization cộng với sửa khoảng trắng tùy tiện; việc gọi NFC nhiều lần vẫn ổn định, nhưng một chuỗi biến đổi khác nhau giữa các lớp có thể tạo bất đồng. Khi xác thực/ủy quyền, coi dữ liệu vào là không tin cậy dù truyền qua TLS.

## 10. Quản lý hợp đồng dữ liệu khi tích hợp nhiều ngôn ngữ

Java, JavaScript, Python, Go và các SDK khác có cách truy cập Unicode và byte khác nhau. Đừng truyền sang đối tác một câu chung chung “mã hóa string theo UTF-8”; nên công bố một **canonicalization profile** có ví dụ kiểm thử:

| Thuộc tính | Ví dụ mô tả trong đặc tả |
| --- | --- |
| Tên và version | `canonical-text-v1` |
| Nguồn dữ liệu | Trường `displayName` đã giải mã từ JSON UTF-8 |
| Form Unicode | NFC, không trim, không case folding |
| Charset đầu ra | UTF-8, không BOM |
| Input không hợp lệ | Reject malformed UTF-8 và Unicode scalar không hợp lệ |
| Giới hạn | Độ dài byte UTF-8 và ký tự sau normalize theo chính sách; phân biệt rõ đơn vị |
| Chuyển đổi khác | Không tự đổi newline, HTML/URL escape hay dấu câu |
| Serializing nhiều trường | Format canonical riêng có định nghĩa thứ tự, null/empty, length-prefix |
| Cập nhật chính sách | Tạo version mới; giữ cách đọc dữ liệu v1 trong thời gian migration |

Cùng một Unicode normalization form đã được đặt tên, nhưng trong hệ thống dài hạn nên ghi rõ thư viện/runtime và tương thích phiên bản Unicode khi cần. Unicode normalization có các bảo đảm ổn định theo tiêu chuẩn, nhưng đừng vì thế bỏ qua **thử nghiệm liên nền tảng** của toàn bộ pipeline (bao gồm parser, validation, case mapping, JSON và storage).

Một *golden vector* tối thiểu nên có:

```text
Input visible: Café
Unicode source A: U+0043 U+0061 U+0066 U+00E9
Unicode source B: U+0043 U+0061 U+0066 U+0065 U+0301
Policy: NFC + UTF-8 strict
Expected canonical hex for both: 436166c3a9
```

Golden vectors cần được chạy qua cả phía producer và consumer. Để kiểm chữ ký đa ngôn ngữ, bổ sung output HMAC/signature của một khóa và thông điệp **dành riêng cho test**, không bao giờ dùng key thật.

## 11. Kiểm thử và xử lý tình huống biên

Một bộ kiểm thử tốt gồm cả tính đúng đắn lẫn trường hợp có khả năng khiến hai hệ thống bất đồng:

| Input / tình huống | Điều cần xác minh |
| --- | --- |
| ASCII `Hello` | UTF-8 chính xác là `48 65 6C 6C 6F`; round trip ổn |
| `é` so với `e\u0301` | Byte thô khác; NFC rồi UTF-8 phải bằng nhau |
| Ligature `ﬃ` so với `ffi` | NFC không buộc chúng bằng nhau; NFKC có thể gộp; chính sách nào được phép? |
| Emoji, ký tự ngoài BMP | `String.length()` khác code point count và byte length; không cắt giữa surrogate |
| Chuỗi chứa surrogate lẻ | Encoder strict từ chối; không âm thầm thay byte trước khi ký |
| Byte UTF-8 sai, ví dụ `C3 28` | Decoder strict từ chối; xác định HTTP status/lỗi xử lý rõ ràng |
| Chuỗi chứa NUL, tab, newline | Parser, validation và serialization phải có quy tắc thống nhất |
| `\r\n`, `\n`, space đầu/cuối | Chỉ biến đổi nếu giao thức/field profile cho phép |
| Username giống hình dáng giữa các bảng chữ | Unicode NFC không giải quyết confusable/homograph; cần chính sách identifier |
| Giá trị `null`, chuỗi rỗng, whitespace-only | Phân biệt ý nghĩa dữ liệu, không nhập nhằng với missing field |
| Bản mã, nonce, digest chứa byte tùy ý | Base64 decode/encode round trip; không dùng `new String(ciphertext, UTF_8)` |
| JSON cùng semantic khác serialization | Raw-body signature khác; canonical JSON chỉ giống nếu cả hai theo cùng chuẩn |
| Nhiều dịch vụ khác phiên bản | Golden vectors khớp giữa các ngôn ngữ và thời điểm triển khai |
| Input rất dài | Kiểm giới hạn kích thước trước và sau normalize; tránh tiêu thụ CPU/bộ nhớ quá mức |

Khi log lỗi, có thể ghi **mã lỗi, request ID và thông tin độ dài/loại encoding đã được làm sạch**; hạn chế in raw password, token, khóa, payload nhạy cảm hoặc String chứa ký tự điều khiển. Nếu cần debug byte có vấn đề, chỉ dùng dữ liệu kiểm thử nhân tạo hoặc cơ chế quan sát được kiểm soát.

### Tách lỗi encoding khỏi lỗi normalization

- `MalformedInputException`/`CharacterCodingException`: input không hợp lệ ở tầng mã hóa/giải mã ký tự.
- Unicode normalization: một **phép chuyển biểu diễn** theo chính sách, không mặc nhiên là phép validation.
- Validation: giới hạn ký tự, độ dài, cấu trúc và quy tắc nghiệp vụ. Nên áp dụng trên dạng mà hệ thống dùng để lưu/so sánh, đồng thời giới hạn cả kích thước đầu vào gốc để chống lạm dụng.
- Authorization: quyết định quyền dựa trên định danh đã được xác thực và một canonical representation nhất quán. Không thể thay bằng charset conversion.

Khi có lỗi, nên từ chối rõ ràng thay vì sửa im lặng rồi để chữ ký/danh tính được xác minh theo một chuỗi khác.

## 12. Lỗi thường gặp và cách sửa

**1. Dùng charset mặc định:** `getBytes()` / `new String(bytes)` tạo hành vi phụ thuộc môi trường. Sửa bằng một `Charset` được ghi rõ trong hợp đồng, thường `StandardCharsets.UTF_8`.

**2. Giả định String giống nhau về hiển thị thì byte giống nhau:** ký tự soạn sẵn và tổ hợp dấu có thể cho byte khác. Sửa bằng chính sách equivalence/normalization theo từng field, hoặc cố tình giữ byte nguyên gốc nếu đó là yêu cầu.

**3. Dùng `String.length()` để giới hạn byte gửi, kích thước DB hoặc khóa:** UTF-8 có độ dài biến thiên, emoji có surrogate pair. Sửa bằng đo đúng đơn vị, nhất là `strictUtf8(text).length` sau normalization và giới hạn input để tránh lạm dụng.

**4. “Chuẩn hóa để mọi thứ tương đương” bằng NFKC/lowercase/trim:** có thể thay đổi tên, mật khẩu và khóa định danh. Sửa bằng chính sách có chủ đích và test các cặp có thể bị nhập làm một.

**5. Giải mã ciphertext bằng UTF-8 để tiện truyền:** byte tùy ý không phải văn bản. Sửa bằng `byte[]`, Base64 hoặc hex cho lớp vận chuyển.

**6. Xác minh HMAC của JSON bằng cách serialize object lại:** whitespace/thứ tự/escape/số có thể đổi. Sửa bằng ký đúng raw bytes hoặc thực hiện canonicalization của giao thức đã thỏa thuận.

**7. Normalize nội dung trước khi kiểm chữ ký raw-body:** thay byte và luôn làm lệch signature ở một số trường hợp. Sửa bằng xác minh theo đúng lớp biểu diễn mà giao thức yêu cầu.

**8. Cho phép encoder/decoder tự thay dữ liệu lỗi:** replacement có thể biến input hỏng thành byte khác và che mất vấn đề. Sửa bằng `REPORT` khi sự chính xác byte là yêu cầu bảo mật.

**9. Thay đổi chính sách giữa chừng mà không version:** signature cũ, dữ liệu AAD cũ hoặc username cũ có thể không xác minh được. Sửa bằng versioned policy, migration và golden vectors.

**10. Để validation và lookup dùng hai dạng chuỗi:** kiểm tra quyền trên ID khác với ID được truy xuất. Sửa bằng một representation được định nghĩa từ đầu đến cuối, kiểm thử tại ranh giới parser–authorization–storage.

## 13. Bài tập thực hành

Một API Java nhận yêu cầu tạo tài liệu có `title` (hiển thị cho người dùng), `externalId` (ID bên đối tác) và `payload` (JSON được đối tác ký bằng HMAC trên raw request body).

Hãy thiết kế giải pháp trước khi code:

1. Đối với `title`, quyết định có dùng NFC hay giữ nguyên; định nghĩa byte UTF-8 để lưu/search và một golden vector cho `é` so với `e\u0301`.
2. Với `externalId`, công bố grammar, validation, canonicalization và mức độ phân biệt chữ hoa/thường; đảm bảo uniqueness check, cache key, authorization và DB lookup dùng nhất quán cùng quy tắc.
3. Với `payload`, mô tả cách lấy **raw request body bytes** và xác minh HMAC **trước khi parse lại JSON**, nếu đúng hợp đồng bên gửi ký raw bytes. Kiểm thử body thêm một khoảng trắng làm thay đổi HMAC.
4. Chỉ ra hành vi khi request chứa malformed UTF-8 hoặc input `String` có surrogate lẻ; xây dựng unit test cho decoder/encoder strict.
5. Thử round trip hai hướng `UTF8 -> String -> UTF8` với văn bản hợp lệ và `byte[] -> Base64 -> byte[]` với dữ liệu ngẫu nhiên. Giải thích vì sao không áp dụng phép round trip đầu tiên cho ciphertext.
6. Nếu sau này đổi chính sách title từ “giữ nguyên” sang NFC, chỉ rõ cách xử lý bản ghi cũ, khóa tra cứu, dữ liệu đã được ký và version của quy tắc.

**Tiêu chí đạt:** biết chính xác **byte nào** được băm/ký/mã hóa, **vì sao** một chuỗi có hoặc không được chuẩn hóa, lúc nào cần giữ nguyên raw bytes, và cách chứng minh Java cùng hệ thống đối tác cho output giống nhau.

## Tài liệu tham khảo

- [Java 17 — StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html): các charset chuẩn.
- [Java 17 — Normalizer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html): NFC, NFD, NFKC, NFKD.
- [Java 17 — CharsetEncoder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetEncoder.html) và [CharsetDecoder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html): xử lý dữ liệu không hợp lệ.
- [Unicode Standard Annex #15 — Unicode Normalization Forms](https://unicode.org/reports/tr15/): định nghĩa chuẩn tắc và tương thích.
- [RFC 3629 — UTF-8](https://www.rfc-editor.org/rfc/rfc3629): quy tắc biểu diễn UTF-8.
- [RFC 8785 — JSON Canonicalization Scheme](https://www.rfc-editor.org/rfc/rfc8785): canonical JSON cho trường hợp cần serialization xác định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-randomness-002">3.2 Base64, HexFormat và sự khác biệt giữa biểu diễn dữ liệu với bảo vệ bí mật</a>

<details>
<summary>Xem chi tiết</summary>

# 3.2 Base64, HexFormat và sự khác biệt giữa biểu diễn dữ liệu với bảo vệ bí mật

## Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích **encoding (biểu diễn/mã hóa dữ liệu)** khác **encryption (mã hóa bảo mật)**, hashing, MAC và chữ ký số như thế nào.
- Hiểu cơ chế chuyển byte thành Base64 và hexadecimal (hex), chi phí kích thước và tình huống dùng thích hợp.
- Sử dụng đúng **java.util.Base64** và **java.util.HexFormat** (Java 17+) để mã hóa biểu diễn, giải mã và kiểm tra round-trip; phân biệt Base64 Basic, URL-safe và MIME.
- Xử lý đúng byte, charset UTF-8, đầu vào không hợp lệ, dữ liệu nhị phân và nguy cơ canonicalization.
- Nhận diện các sai lầm bảo mật: coi Base64/hex là bí mật, đưa secret đã encode vào log/URL, hiểu sai Basic Authentication, JWT hoặc chuỗi hash.

## 1. Bắt đầu từ byte, không bắt đầu từ chuỗi hiển thị

Máy tính lưu và truyền thông tin dưới dạng **byte**. Khi cần đưa byte vào môi trường thiên về văn bản (JSON, cấu hình, HTTP header, log chẩn đoán, QR code, tệp text, giao thức chỉ chấp nhận ký tự in được), ta dùng một cách **biểu diễn** byte thành chuỗi có thể đọc/truyền an toàn theo quy tắc cụ thể.

Ví dụ, chuỗi ký tự **Java!** theo UTF-8 tạo năm byte:

~~~text
Text:                 Java!
UTF-8 bytes (hex):    4a 61 76 61 21
Base64 của các byte:  SmF2YSE=
Hex của các byte:     4a61766121
~~~

Cả **SmF2YSE=** và **4a61766121** đều biểu diễn **cùng năm byte gốc**. Người khác chỉ cần biết quy tắc Base64/hex là có thể khôi phục chính xác năm byte đó. Không cần mật khẩu hay khóa bí mật.

Cần phân biệt hai tầng chuyển đổi:

~~~text
Ký tự Unicode --[UTF-8 encode]--> byte[]
                                  |
                           +------+------+
                           |             |
                        [Base64]       [Hex]
                           |             |
                          text          text
                           |             |
                         decode        parse
                           |             |
                           +------v------+
                                  |
                                byte[]
                        --[UTF-8 decode]--> Ký tự Unicode
~~~

**UTF-8** là cách biến ký tự thành byte; **Base64/hex** biến byte thành ký tự từ bảng chữ cái giới hạn. Chúng giải quyết các vấn đề tương thích biểu diễn, không giải quyết việc giữ bí mật. Byte của dữ liệu nhị phân bất kỳ, chẳng hạn ảnh, chữ ký, nonce hoặc ciphertext, không nhất thiết là văn bản UTF-8 hợp lệ: đừng tùy tiện chuyển byte[] bất kỳ thành String để “đọc” nội dung.

### 1.1 Bốn nhóm thao tác thường bị nhầm

| Thao tác | Mục tiêu | Đầu vào → đầu ra | Cần khóa bí mật? | Có khôi phục bản gốc không? |
| --- | --- | --- | --- | --- |
| **Encoding** (Base64, hex, UTF-8) | Đổi cách biểu diễn cho lưu/truyền/hiển thị | Byte ↔ ký tự theo quy tắc | Không | Có, khi đúng định dạng/charset |
| **Encryption** (ví dụ AES-GCM) | Giữ bí mật dữ liệu, thường có xác thực toàn vẹn | Plaintext → ciphertext | Có | Có, nếu có khóa và tham số hợp lệ |
| **Hash** (ví dụ SHA-256) | Tạo dấu vân tay có độ dài cố định từ dữ liệu | Byte → digest | Không đối với hash thông thường | Không có phép giải mã ngược; dữ liệu yếu có thể bị đoán/brute-force |
| **MAC/chữ ký số** (HMAC, Ed25519, v.v.) | Kiểm tra tính toàn vẹn/xác thực theo mô hình khóa | Dữ liệu + khóa → mã xác thực/chữ ký | Có khóa bí mật hoặc private key để tạo | Không dùng để khôi phục dữ liệu gốc |

**Một chuỗi trông như ký tự ngẫu nhiên chưa chắc được bảo mật.** Mức độ khó đọc bằng mắt không phải thuộc tính an toàn.

## 2. Base64 hoạt động như thế nào?

### 2.1 Cơ chế 6 bit và padding

Base64 đọc luồng bit theo nhóm **6 bit**, mỗi nhóm ánh xạ vào một trong 64 ký tự. Ba byte chứa 24 bit được biểu diễn thành bốn ký tự Base64. Vì vậy:

- Ba byte đầu vào → bốn ký tự.
- Một byte đầu vào → hai ký tự dữ liệu và thường hai ký tự **==** làm padding.
- Hai byte đầu vào → ba ký tự dữ liệu và thường một ký tự **=**.
- Nếu đầu vào chia hết cho ba byte thì không cần padding.

Với **Base64 Basic có padding**, độ dài đầu ra theo ký tự là **4 × ceil(số_byte / 3)**. Đây là mở rộng kích thước khoảng 33,3% với dữ liệu đủ lớn, cộng tác động của padding khi dữ liệu ngắn. Không được dùng độ dài String của một chuỗi Unicode thay cho số byte khi tính kích thước.

**Padding không chứa bí mật**; nó làm rõ phần byte cuối của dữ liệu. Một số biến thể bỏ padding vì quy tắc giao thức đã cho phép bên nhận tự suy ra. Thêm/bớt padding không biến Base64 thành phương tiện mã hóa bảo mật.

### 2.2 Ba biến thể trong Java

Java cung cấp ba cặp encoder/decoder chính ở **java.util.Base64**:

| Biến thể | Java API | Alphabet/đặc tính | Tình huống |
| --- | --- | --- | --- |
| **Basic** | Base64.getEncoder() / getDecoder() | A–Z, a–z, 0–9, **+** và **/**; padding **=** khi cần; không chèn xuống dòng | JSON, cấu hình, dữ liệu nhị phân trong text không hạn chế dấu + và / |
| **URL-safe** | Base64.getUrlEncoder() / getUrlDecoder() | Dùng **-** thay **+**, **_** thay **/**; có thể bỏ padding bằng withoutPadding() | Thành phần URL, token định dạng Base64URL, tên/ID theo giao thức |
| **MIME** | Base64.getMimeEncoder() / getMimeDecoder() | Có thể ngắt dòng (thông thường 76 ký tự, CRLF); decoder MIME chấp nhận một số ký tự ngoài alphabet và bỏ qua chúng | Nội dung MIME/email theo đặc tả phù hợp |

**Không tráo biến thể một cách tùy tiện.** Base64 Basic có dấu **+** và **/**: dấu **+** có thể bị xử lý thành khoảng trắng trong một số dạng query/form encoding, còn **/** có ý nghĩa phân tách path. Base64URL thay hai ký tự đó, nhưng vẫn không tự đem lại tính bí mật, chống sửa đổi hay chống phát lại.

**MIME decoder không thích hợp làm bộ kiểm tra nghiêm ngặt cho token bảo mật**, vì quy tắc bỏ qua ký tự của nó rộng hơn Basic/URL. Chọn decoder theo giao thức; đặt giới hạn độ dài và kiểm tra nội dung hợp lệ trước khi sử dụng.

### 2.3 Base64 không gắn với kiểu dữ liệu gốc

Base64 chỉ biết **byte[]**, không biết byte đó là ảnh PNG, JSON UTF-8, một khóa AES, một chữ ký số hay dữ liệu ngẫu nhiên. Muốn khôi phục ý nghĩa, ứng dụng phải biết từ **giao thức/metadata**:

- Byte nào đang được biểu diễn?
- Sau khi decode có được xem là UTF-8, protobuf, ảnh hay ciphertext?
- Cần bản mã được giải mã bằng khóa nào, nonce nào, thuật toán nào?
- Payload có được xác thực và kiểm tra quyền trước khi tin tưởng không?

Không dùng vẻ ngoài Base64 làm bằng chứng dữ liệu đã được mã hóa, ký hay kiểm tra toàn vẹn.

## 3. Hexadecimal và Java 17 HexFormat

**Hex** dùng hệ cơ số 16: mỗi **nibble 4 bit** biểu diễn bằng một chữ số từ 0–9, a–f. Một byte 8 bit tương ứng **hai chữ số hex**.

Ví dụ:

~~~text
Byte nhị phân:     1111 1011
Hex:               FB
Byte nhị phân:     0000 0000
Hex:               00
Ba byte:           FB FF 00
Hex liền mạch:     fbff00
Hex có dấu phân tách: FB:FF:00
~~~

Hex thích hợp để đọc dump byte, in giá trị hash, định danh, fingerprint, nonce/IV và địa chỉ dưới dạng văn bản. Khi dùng hex nguyên bản không thêm separator, kích thước **đúng gấp đôi số byte**: 32 byte → 64 ký tự hex. Hex thường dài hơn Base64, đổi lại dễ đối chiếu byte và đọc bằng mắt.

Java 17 đưa vào **java.util.HexFormat**, tránh tự viết vòng lặp đổi byte sang hex hoặc phụ thuộc thư viện ngoài cho thao tác cơ bản.

- **HexFormat.of().formatHex(bytes):** tạo chuỗi hex liên tục, mặc định chữ thường.
- **HexFormat.of().parseHex(text):** chuyển chuỗi hex hợp lệ về byte[], mặc định không có dấu phân tách.
- **HexFormat.of().withUpperCase():** chọn A–F viết hoa khi format.
- **HexFormat.ofDelimiter(":"):** dùng dấu **:** giữa các byte; parse và format cần thống nhất định dạng đã định.
- **formatHex** không tự thêm ký hiệu **0x**, dấu cách hay các ký tự tùy ý; định dạng ấy phải được quy định và xử lý rõ ràng.

Đầu vào lẻ số chữ số, ký tự không thuộc hex hoặc dấu phân tách không phù hợp sẽ bị từ chối (ví dụ IllegalArgumentException). Đừng âm thầm bỏ các ký tự lỗi, cắt ngắn hoặc tự sửa đầu vào chưa rõ quy tắc.

## 4. Thực hành Java 17+: round-trip Base64, Base64URL và HexFormat

Tạo tệp **RepresentationDemo.java** (lưu UTF-8):

~~~java
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

public class RepresentationDemo {
    public static void main(String[] args) {
        byte[] textBytes = "Java!".getBytes(StandardCharsets.UTF_8);

        String basic = Base64.getEncoder().encodeToString(textBytes);
        String hex = HexFormat.of().formatHex(textBytes);

        byte[] fromBase64 = Base64.getDecoder().decode(basic);
        byte[] fromHex = HexFormat.of().parseHex(hex);

        System.out.println("Base64: " + basic);
        System.out.println("Hex: " + hex);
        System.out.println("Base64 round-trip: "
                + Arrays.equals(textBytes, fromBase64));
        System.out.println("Hex round-trip: "
                + Arrays.equals(textBytes, fromHex));
        System.out.println("Text round-trip: "
                + new String(fromBase64, StandardCharsets.UTF_8));

        // Các byte này có bit 1 cao, không phải text UTF-8 để đọc trực tiếp.
        byte[] binary = {(byte) 0xFB, (byte) 0xFF, 0x00};

        System.out.println("Basic binary: "
                + Base64.getEncoder().encodeToString(binary));
        System.out.println("URL binary: "
                + Base64.getUrlEncoder().encodeToString(binary));
        System.out.println("Hex binary: "
                + HexFormat.ofDelimiter(":")
                        .withUpperCase()
                        .formatHex(binary));
        System.out.println("Base64URL one byte: "
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(new byte[] {(byte) 0xFF}));
    }
}
~~~

Biên dịch/chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 RepresentationDemo.java
java RepresentationDemo
~~~

Stdout chính xác:

~~~text
Base64: SmF2YSE=
Hex: 4a61766121
Base64 round-trip: true
Hex round-trip: true
Text round-trip: Java!
Basic binary: +/wA
URL binary: -_wA
Hex binary: FB:FF:00
Base64URL one byte: _w
~~~

Giải thích:

1. **getBytes(StandardCharsets.UTF_8)** xác định rõ cách chuyển text thành byte. Khi khôi phục text cũng dùng UTF-8 tương ứng; không dựa vào default charset của môi trường.
2. **encodeToString** tạo chuỗi biểu diễn từ byte; **decode** trả lại byte[]. Hai phép biến đổi là nghịch đảo nhau đối với dữ liệu hợp lệ.
3. **formatHex/parseHex** thực hiện cặp biến đổi tương tự, nhưng dùng hai chữ số hex cho mỗi byte. Kết quả Arrays.equals cho thấy các byte giữ nguyên.
4. Với byte **FB FF 00**, Base64 Basic là **+/wA**, Base64URL là **-_wA**; khác alphabet nhưng đều biểu diễn cùng ba byte.
5. Một byte **FF** thành **_w** khi Base64URL bỏ padding; nếu dùng encoder có padding, kết quả là **_w==**. Padding không phải một cơ chế an toàn.
6. **HexFormat.ofDelimiter(":").withUpperCase()** định dạng ba byte thành **FB:FF:00**; nếu cần parse dạng này, dùng HexFormat.ofDelimiter(":").parseHex(...) theo đúng quy tắc.

**Lưu ý về bộ nhớ:** Base64/hex chuyển dữ liệu thành String sẽ tạo bản sao mới. Nếu dữ liệu gốc là secret thì bản sao cũng là secret. Không in secret thật ra log để “kiểm tra format”.

## 5. Vì sao Base64/Hex không bảo vệ bí mật?

**Encoding không có khóa.** Kẻ nhận hoặc đọc được chuỗi có thể giải mã bằng thuật toán công khai. Ví dụ một chuỗi cấu hình:

~~~text
apiKeyBase64: ZGVtby1zZWNyZXQ=
~~~

Chỉ cần Base64 decode là nhận được văn bản **demo-secret**. Việc đặt tên biến **encryptedPassword**, thêm tiền tố bí hiểm hoặc mã hóa Base64 nhiều lần không làm xuất hiện tính bí mật.

Một chuỗi hex của token, số thẻ, mật khẩu, private key hay dữ liệu cá nhân cũng **nhạy cảm ngang với byte gốc**. Chuỗi có thể dài và khó đọc nhưng vẫn khôi phục được nguyên vẹn. Không gửi vào log, analytics, URL, ticket hoặc nơi công khai chỉ vì “đã encode”.

### 5.1 Ví dụ Java 17+: encode có thể đảo ngược, digest chỉ được biểu diễn

Tạo **RepresentationVsProtectionDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

public class RepresentationVsProtectionDemo {
    public static void main(String[] args) throws Exception {
        // Chỉ là chuỗi minh họa công khai, KHÔNG phải secret thật.
        byte[] sample = "demo-secret".getBytes(StandardCharsets.UTF_8);

        String encoded = Base64.getEncoder().encodeToString(sample);
        String recovered = new String(
                Base64.getDecoder().decode(encoded),
                StandardCharsets.UTF_8);

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(sample);
        String digestHex = HexFormat.of().formatHex(digest);
        String digestBase64 = Base64.getEncoder().encodeToString(digest);

        System.out.println("Recovered: " + recovered);
        System.out.println("SHA-256 bytes: " + digest.length);
        System.out.println("Digest hex chars: " + digestHex.length());
        System.out.println("Digest Base64 chars: " + digestBase64.length());
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 RepresentationVsProtectionDemo.java
java RepresentationVsProtectionDemo
~~~

Stdout:

~~~text
Recovered: demo-secret
SHA-256 bytes: 32
Digest hex chars: 64
Digest Base64 chars: 44
~~~

Điều cần quan sát: mã Base64 có thể khôi phục **demo-secret** ngay lập tức. Ngược lại, **SHA-256** trả về digest 32 byte; hex hoặc Base64 chỉ là hai cách **hiển thị cùng digest**, hoàn toàn không thêm/bớt thuộc tính mật mã của hash.

Hash không có hàm “decrypt” để phục hồi chuỗi đầu vào. Tuy vậy dữ liệu có không gian đoán nhỏ, như mật khẩu yếu, vẫn có thể bị dò bằng cách hash các ứng viên và so sánh. **Không dùng SHA-256 thuần để lưu mật khẩu**; dùng cơ chế password hashing có salt riêng và tham số chi phí, ví dụ Argon2id/scrypt/bcrypt/PBKDF2 theo yêu cầu hệ thống.

### 5.2 Thứ cần mã hóa bảo mật thực sự

Nếu mục tiêu là **giữ bí mật**, hãy dùng **encryption** thích hợp như AES-GCM (AEAD) với khóa bí mật, nonce duy nhất trong phạm vi cùng khóa, xác thực tag và quản lý khóa đúng cách. Sau đó, khi một API chỉ cho phép văn bản, mới biểu diễn byte ciphertext bằng Base64 hoặc hex:

~~~text
Plaintext bytes
    |
[AES-GCM encrypt: secret key + nonce + AAD]
    |
Ciphertext + authentication tag (binary bytes)
    |
[Base64 / hex, chỉ để đóng gói văn bản]
    |
Text mang ciphertext qua JSON / cấu hình / giao thức
    |
[Base64 / hex decode]
    |
Ciphertext + tag (binary bytes)
    |
[AES-GCM decrypt + verify tag]
    |
Plaintext bytes
~~~

Decoder Base64 **không** kiểm tra tag GCM, không xác thực người gửi và không tự giải mã plaintext. Khóa, nonce, AAD và phiên bản định dạng phải được xử lý ở tầng mật mã. Đừng nhầm **có thể decode** với **có quyền decrypt**.

Với xác thực nguồn gốc và toàn vẹn, dùng **MAC** như HMAC hoặc **chữ ký số** theo đúng giao thức. Chuỗi Base64 chứa một chữ ký không trở thành chữ ký chỉ nhờ Base64; phải có quy trình ký và kiểm tra chữ ký thật sự.

## 6. Định dạng ở các ranh giới giao thức

### 6.1 HTTP Basic Authentication

Header HTTP Basic Authentication về mặt định dạng có thể chứa:

~~~text
Authorization: Basic <Base64 của user-id:password>
~~~

Chuỗi **user-id:password** có thể được lấy lại bằng Base64 decode. Vì vậy Basic Authentication **phải dùng cùng kết nối TLS được xác thực hợp lệ**, không được xem Base64 là bảo vệ mật khẩu. Tránh sao chép header Authorization sang log, error tracker hoặc debug dump. Xác thực thông tin đăng nhập và giới hạn quyền vẫn do hệ thống xử lý, không do Base64.

### 6.2 JWT/JWS/JWE và thông tin có thể nhìn thấy

Một **JWT dạng JWS compact** thường có ba đoạn Base64URL phân cách bằng dấu chấm: header.payload.signature. Payload có thể được decode và đọc mà không cần private key. **Chữ ký JWS giúp phát hiện sửa đổi và xác thực theo khóa**, nhưng không giữ bí mật payload. Nếu cần bí mật, dùng cơ chế mã hóa payload được thiết kế cho mục tiêu đó, ví dụ JWE hoặc mã hóa ở tầng ứng dụng phù hợp.

Không đặt mật khẩu, private key hay dữ liệu cá nhân nhạy cảm vào payload JWT ký thông thường rồi giả định đã “ẩn” vì nó là Base64URL. Việc decode token không tương đương xác minh chữ ký, issuer, audience, expiration và các điều kiện sử dụng token.

### 6.3 API JSON, byte[], key fingerprint và ciphertext

- **Nhị phân trong JSON:** Base64 thường gọn hơn hex. Metadata phải mô tả content type và encoding. Không tự đoán từ ký tự trông giống Base64.
- **Digest/fingerprint/debug:** hex thường trực quan khi đối chiếu từng byte. Các giá trị fingerprint có thể công khai hay nhạy cảm tùy ngữ cảnh.
- **Ciphertext lưu trong DB dạng text:** Base64/hex hỗ trợ cột text; vẫn cần encryption thật và lưu đủ metadata (key ID/version, nonce, tag, AAD nếu giao thức yêu cầu).
- **Raw binary:** nếu protocol/database hỗ trợ BLOB hoặc binary frame, có thể lưu/truyền byte trực tiếp, tránh overhead text encoding.
- **Token ngẫu nhiên:** token có entropy đủ cao có thể được tạo bằng SecureRandom rồi biểu diễn bằng Base64URL. **Độ khó đoán đến từ entropy của byte ngẫu nhiên**, không đến từ Base64URL. Token vẫn là bearer secret nếu ai sở hữu nó đều có thể sử dụng.

## 7. Unicode, charset và tính chuẩn hóa dữ liệu

Encoding nhị phân không quyết định cách mã hóa ký tự. Nếu dữ liệu ban đầu là String, phải **thống nhất charset** ở hai đầu, thường là UTF-8:

~~~java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
~~~

Hai dòng trên minh họa một phần chương trình, cần biến text và import java.nio.charset.StandardCharsets khi sử dụng; đây không phải chương trình độc lập.

Lỗi kinh điển là dùng charset khác ở hai đầu, hoặc gọi **new String(rawBinaryBytes, UTF_8)** cho ciphertext/digest bất kỳ. Dữ liệu nhị phân có thể chứa chuỗi byte không phải UTF-8 hợp lệ, dẫn đến ký tự thay thế và mất khả năng round-trip. Với byte nhị phân, dùng Base64/hex trước rồi parse về byte.

Ngay cả khi dùng cùng UTF-8, văn bản nhìn giống nhau chưa chắc có **cùng byte**. Ví dụ chữ **é** có thể ở dạng một code point U+00E9 (UTF-8: **c3a9**) hoặc ký tự **e** cộng dấu U+0301 (UTF-8: **65cc81**). Nếu hệ thống ký số/hash trên byte, hai dạng đó tạo đầu vào khác nhau. Chỉ áp dụng Unicode normalization khi giao thức đã quy định nhất quán trước phép hash/ký; không tùy tiện chuẩn hóa dữ liệu đã ký hoặc thay đổi bytes sau khi xác thực.

### 7.1 Không lẫn lộn canonicalization với decoding

Một decoder có thể chấp nhận nhiều cách viết cùng biểu diễn byte, tùy variant và padding. Ví dụ một số chuỗi Base64 có hoặc không có dấu **=** cuối vẫn decode được cùng byte. Đừng mặc định **so sánh chuỗi encoded** là tương đương so sánh byte hoặc tương đương xác minh nội dung an toàn.

Với token/chữ ký, luôn tuân thủ chính xác **dạng canonical và signing input mà giao thức quy định**. Có giao thức ký trên byte payload đã canonicalize; có giao thức như JWS ký trên chính các đoạn encoded. Tự bỏ padding, đổi hoa/thường hex, thêm newline hay đổi Base64 Basic ↔ URL-safe trước khi xác minh có thể khiến chữ ký sai hoặc tạo lỗ hổng xử lý không thống nhất.

Nếu giao thức yêu cầu biểu diễn chuẩn, khi chấp nhận chuỗi ngoài hệ thống có thể thực hiện **decode rồi re-encode theo encoder canonical**, đối chiếu với đầu vào ban đầu và xử lý không khớp theo chính sách. Đây là bước kiểm tra định dạng, **không thay thế MAC/chữ ký số**.

## 8. Thiết kế parser và xử lý lỗi khi nhận dữ liệu không tin cậy

Hãy coi chuỗi Base64/hex nhận từ bên ngoài là **untrusted input** dù về mặt cú pháp nó đã hợp lệ. Quy trình khuyến nghị:

1. **Giới hạn kích thước chuỗi** trước khi decode để tránh cấp phát byte[] quá lớn. Với upload lớn, cân nhắc stream và giới hạn kích thước dữ liệu gốc ở tầng transport.
2. **Chọn đúng variant/định dạng**: Base64 Basic, URL-safe, MIME hoặc hex có delimiter. Ghi rõ trong hợp đồng API.
3. **Reject dữ liệu sai**: bắt IllegalArgumentException của decoder/parser tại ranh giới input và trả lỗi có kiểm soát, không in payload/secret ra log.
4. **Giới hạn kích thước sau decode** và kiểm tra cấu trúc: nonce, digest, key ID, độ dài field, media type, version.
5. **Xác thực/kiểm tra toàn vẹn và quyền truy cập** theo mô hình ứng dụng. Dữ liệu có thể decode không có nghĩa dữ liệu là đáng tin.
6. **Xử lý dữ liệu nhạy cảm thận trọng**: tránh lưu chuỗi mã hóa biểu diễn vào log, metric label, exception message, URL hoặc crash dump; giới hạn thời gian lưu trong bộ nhớ và bản sao không cần thiết.

Một số trường hợp lỗi cần tách bạch:

| Đầu vào | Kết quả mong đợi |
| --- | --- |
| Base64 Basic chứa ký tự không thuộc alphabet | Basic decoder báo lỗi; không lặng lẽ bỏ qua |
| Base64URL chứa dấu **+** hoặc **/** thay vì **-** hoặc **_** | Dùng URL decoder đúng chuẩn, không tự tráo ký tự thiếu quy định |
| Hex có số chữ số lẻ, ký tự ngoài 0–9/A–F/a–f | HexFormat.parseHex báo lỗi |
| Hex có dấu **:** nhưng parser mặc định không có delimiter | Chọn HexFormat.ofDelimiter(":") hoặc reject theo API contract |
| Decode thành byte[] nhưng loại nội dung không khớp (ví dụ cần 32 byte digest mà nhận 5 byte) | Reject sau kiểm tra ràng buộc kiểu/độ dài |
| Base64 decode hợp lệ nhưng ciphertext bị sửa hoặc sai tag | Tầng AEAD xác thực thất bại; không được tin plaintext |
| Token decode hợp lệ nhưng chữ ký sai hoặc token hết hạn | Tầng xác thực token phải reject |

**Không** tự sửa dữ liệu bằng cách bỏ ký tự tùy ý, chuyển charset nhiều lần hoặc cắt chuỗi đến khi parser chấp nhận. Chính sách tolerant parsing chỉ nên áp dụng khi đặc tả giao thức yêu cầu và đã đánh giá hệ quả bảo mật.

## 9. So sánh để chọn nhanh

| Tiêu chí | Base64 | Hex |
| --- | --- | --- |
| Đơn vị biểu diễn | Nhóm 6 bit → một ký tự | 4 bit → một ký tự |
| Kích thước text | Khoảng 4/3 số byte, có padding tùy variant | Đúng 2 lần số byte (không delimiter) |
| Dễ đọc từng byte | Khó hơn | Rất thuận tiện, mỗi byte hai chữ số |
| Ký tự đặc biệt | Basic có + / và =; URL-safe có - _ | Thường chỉ 0–9 a–f hoặc A–F |
| Java 17+ | java.util.Base64 | java.util.HexFormat |
| Bảo mật bí mật/toàn vẹn | Không có | Không có |
| Dùng phổ biến | JSON, binary-to-text, chuỗi token, ciphertext dạng text | Digest, fingerprint, log chẩn đoán an toàn, địa chỉ/buffer/debug |

Ví dụ với 32 byte dữ liệu bất kỳ:

- Hex liên tục: **64 ký tự**.
- Base64 Basic có padding: **44 ký tự** (có một dấu **=** cuối).
- Base64URL bỏ padding: **43 ký tự**.

**Lựa chọn dựa trên giao thức và khả năng đọc/chi phí kích thước**. Nếu dữ liệu cần giữ bí mật, hãy chọn và triển khai một cơ chế bảo vệ độc lập trước khi nghĩ đến định dạng biểu diễn.

## 10. Sai lầm phổ biến và cách sửa

1. **“Tôi đã Base64 encode mật khẩu/API key rồi nên có thể đưa lên Git.”** Sai: ai cũng decode được. Dùng secret manager/KMS, giữ bí mật và tránh commit; mật khẩu đăng nhập lưu bằng password hashing phù hợp.
2. **“Đưa bí mật vào URL sau Base64URL thì không ai biết.”** Sai: Base64URL không che dữ liệu. URL có thể vào browser history, access log, referrer, monitoring. Dùng kênh và cơ chế xác thực phù hợp, không đặt long-lived secret vào URL.
3. **“Chỉ cần HexFormat cho checksum là đủ chống giả mạo.”** Sai: hex là biểu diễn. Digest hash công khai không chứng minh người gửi; dùng HMAC/chữ ký số để chống sửa đổi có chủ đích.
4. **“Base64URL không có dấu + / nên chắc là dữ liệu được mã hóa an toàn.”** Sai: URL-safe chỉ đổi alphabet để phù hợp cú pháp.
5. **“Một JWT có ba đoạn Base64URL là nội dung không đọc được.”** Sai với JWS compact thông thường: header/payload đọc được sau decode, chữ ký xác thực chứ không giữ bí mật.
6. **“Đã decode Base64 thành công thì nội dung đáng tin.”** Sai: parse hợp lệ chỉ nói chuỗi có đúng dạng; mọi xác thực, phân quyền, schema validation vẫn cần thiết.
7. **“String.length() cho biết số byte cần mã hóa.”** Sai với Unicode/UTF-8; phải dùng độ dài của byte[] sau charset encode.
8. **“Ciphertext có thể đưa thẳng vào new String(bytes, UTF_8).”** Sai: ciphertext là byte nhị phân tùy ý. Dùng Base64/hex hoặc binary transport.
9. **“MIME decoder luôn là lựa chọn linh hoạt và an toàn nhất.”** Sai: nó có thể bỏ qua ký tự không thuộc alphabet; với token cần yêu cầu định dạng nghiêm ngặt.
10. **“Encode hai hoặc ba lần là đủ an toàn.”** Sai: mọi lớp encoding công khai đều có thể tháo ngược; không tạo ra khóa bí mật hoặc authentication tag.

## 11. Tình huống luyện tập và câu hỏi tự kiểm tra

**Tình huống A – Lưu digest SHA-256:** một hệ thống có digest 32 byte muốn lưu trong cột text và dễ so sánh bằng mắt. Hex 64 ký tự là lựa chọn thuận tiện; Base64 44 ký tự gọn hơn. Cả hai biểu diễn cùng digest, nên độ an toàn của SHA-256 không đổi khi đổi format. Digest này không phải password hash được thiết kế cho mật khẩu.

**Tình huống B – Token trong URL:** một hệ thống sinh 32 byte token bằng SecureRandom, rồi biểu diễn bằng Base64URL không padding, độ dài 43 ký tự. Khả năng khó đoán đến từ 32 byte ngẫu nhiên được sinh đúng cách; việc trình bày 43 ký tự không tạo thêm entropy. Token vẫn nhạy cảm và cần kiểm soát nơi truyền/lưu, hạn dùng và quyền sử dụng.

**Tình huống C – API nhận ciphertext:** một field JSON là Base64 của ciphertext AES-GCM. Server phải Base64 decode, kiểm tra định dạng/version/nonce, xác thực tag bằng khóa đúng rồi mới dùng plaintext. Dữ liệu Base64 đúng cú pháp vẫn có thể là ciphertext giả mạo, bị hỏng hoặc đến từ bên không có quyền.

**Tự kiểm tra:**

1. Chuỗi **SmF2YSE=** cần khóa nào để đọc lại? → Không cần khóa; đó là Base64 của năm byte UTF-8 cho **Java!**.
2. 16 byte dữ liệu đổi thành bao nhiêu ký tự hex và Base64 Basic có padding? → **32** ký tự hex và **24** ký tự Base64.
3. Vì sao Base64URL khác Base64 Basic? → Thay **+ / ** bằng **- _** để phù hợp hơn với URL; không thay đổi bí mật hay tính xác thực.
4. HexFormat.of().formatHex(byte[]) khác gì HexFormat.of().parseHex(String)? → Một hàm byte[] → text hex, một hàm text hex hợp lệ → byte[].
5. Một mật khẩu đã Base64 encode có thể lưu vào DB như password hash không? → Không. Base64 đảo ngược được; dùng password hashing có salt và chi phí phù hợp.
6. Nếu một giá trị Base64 decode được và có 32 byte thì đã là SHA-256 hợp lệ chưa? → Chỉ biết độ dài phù hợp, chưa biết nguồn gốc, cách tính hoặc tính toàn vẹn của giá trị.
7. Vì sao cần nêu charset khi đổi text sang byte[]? → Cùng ký tự nhưng charset hoặc chuẩn hóa Unicode khác có thể tạo byte khác, ảnh hưởng round-trip, digest và chữ ký.

**Nguyên tắc nhớ lâu:** **Base64 và HexFormat là cách viết dữ liệu dưới dạng text.** Chúng có thể bọc plaintext, ciphertext, hash, chữ ký hoặc token, nhưng bản thân chúng **không mã hóa bảo mật, không kiểm tra toàn vẹn, không xác thực và không cấp quyền truy cập**. Thiết kế bảo mật phải xác định yêu cầu bí mật/toàn vẹn/xác thực trước; encoding chỉ là lớp biểu diễn khi cần lưu, truyền hoặc hiển thị byte.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-randomness-003">3.3 Phân biệt số bit, số byte, độ dài khóa và độ dài đầu ra</a>

<details>
<summary>Xem chi tiết</summary>

# 3.3 Phân biệt số bit, số byte, độ dài khóa và độ dài đầu ra

Trong lập trình mật mã Java, các con số **128, 256, 2048, 32 hoặc 64** thường xuất hiện bên cạnh thuật toán và API. Chúng có thể biểu thị số **bit**, số **byte**, độ dài **khóa bí mật**, kích thước **modulus** của khóa bất đối xứng, độ dài **digest/MAC/tag**, hoặc số ký tự của chuỗi đã **mã hóa để hiển thị**. Những đại lượng đó liên quan nhưng **không thể thay thế cho nhau**. Nhầm đơn vị có thể khiến code không chạy, lưu trữ thiếu byte hoặc đánh giá sai độ an toàn.

## 1. Đơn vị nền tảng: bit, byte và cách đổi

- **Bit** là một vị trí nhị phân có giá trị 0 hoặc 1. Ký hiệu thường dùng: **bit** hoặc **b**.
- **Byte** trong Java có **8 bit**: hằng số `Byte.SIZE` bằng 8. Ký hiệu thông dụng cho byte là **B**. Vì vậy **1 B = 8 bit**.
- Nếu có đúng `n` byte, số bit cần để biểu diễn chúng là **8 × n**; nếu số bit chia hết cho 8, số byte là **số bit / 8**.
- Với độ dài bit bất kỳ `n`, số byte **tối thiểu để chứa** giá trị đó là **ceil(n / 8)**, hay với số nguyên không âm: **(n + 7) / 8**. Byte cuối có thể chứa các bit không sử dụng.
- `kB` = 1.000 byte, `KiB` = 1.024 byte. Phân biệt hai hệ đơn vị dung lượng này với các độ dài khóa thường biểu diễn bằng bit.

| Kích thước | Tương ứng byte | Ngữ cảnh thường gặp |
| --- | ---: | --- |
| 64 bit | 8 byte | Giá trị số nguyên 64 bit; nonce 64 bit ở một số giao thức |
| 96 bit | 12 byte | Nonce/IV khuyến nghị thường dùng cho AES-GCM |
| 128 bit | 16 byte | Khóa AES-128; tag GCM 128 bit |
| 192 bit | 24 byte | Khóa AES-192 |
| 256 bit | 32 byte | Khóa AES-256; đầu ra SHA-256 |
| 384 bit | 48 byte | Đầu ra SHA-384 |
| 512 bit | 64 byte | Đầu ra SHA-512 |
| 2.048 bit | 256 byte | Phần modulus của khóa RSA-2048 (độ dài thường dùng) |

**Cần nói rõ đại lượng:** “256 bit” chỉ là số bit; nếu không biết nó là *khóa*, *digest*, *tag*, *nonce* hay *modulus*, không thể suy ra chức năng hoặc độ mạnh bảo mật.

Ví dụ tính trực tiếp: 128 bit / 8 = **16 byte**; 32 byte × 8 = **256 bit**. Một khóa RSA có modulus 2.048 bit chứa khoảng 256 byte biểu diễn modulus; **toàn bộ public/private key được mã hóa theo định dạng tệp thường dài hơn**.

### 1.1. Byte trong Java và chiều dài mảng

Trong Java, `byte` là kiểu nguyên **8 bit có dấu**, biểu diễn các giá trị từ -128 đến 127. Tính có dấu chỉ là cách Java diễn giải mẫu bit thành số; **một phần tử `byte[]` vẫn mang đúng 8 bit**.

- `byte[].length` đếm **số byte trong mảng**, không đếm bit.
- `Byte.SIZE` = 8; `Byte.BYTES` = 1.
- `int` là 32 bit và `long` là 64 bit trong Java, nhưng số bit của kiểu dữ liệu **không phải độ dài của nội dung được serialize hay entropy**.
- Với số byte rất lớn, dùng phép nhân trên kiểu `long` hoặc `Math.multiplyExact` phù hợp để tránh tràn số khi đổi sang bit.

### 1.2. Độ dài chuỗi ký tự không đồng nghĩa độ dài byte

`String.length()` của Java trả về **số đơn vị mã UTF-16**, không phải số ký tự Unicode theo nghĩa code point, càng không phải số byte khi gửi qua mạng.

Ví dụ chuỗi **“Việt”** có 4 đơn vị mã UTF-16, nhưng khi chuyển qua UTF-8 thành **6 byte = 48 bit**, vì ký tự “ệ” chiếm 3 byte UTF-8. Một emoji ngoài BMP thường có 1 code point, 2 đơn vị mã UTF-16 và 4 byte UTF-8.

Cách đếm đúng trước khi đưa text vào hash, MAC, mã hóa hay giao thức:

```java
String text = "Việt";
byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
int bytes = data.length;
long bits = (long) bytes * Byte.SIZE;
```

**Hệ quả:** kiểm tra “tối đa 256 ký tự” không tương đương giới hạn “tối đa 256 byte”. Khi giới hạn payload, KDF hoặc trường binary, hãy nêu rõ **encoding** và đơn vị đếm.

## 2. Độ dài khóa: kích thước khóa cần khác với số byte của dữ liệu đã mã hóa

**Độ dài khóa (key length/key size)** là số bit của vật liệu khóa hoặc kích thước tham số khóa mà thuật toán định nghĩa. Cách biểu diễn khác nhau giữa các họ thuật toán.

### 2.1. Khóa đối xứng: AES-128, AES-192, AES-256

AES chấp nhận khóa **128, 192 hoặc 256 bit**, lần lượt là **16, 24 hoặc 32 byte**. Con số sau “AES-” là số bit của **khóa**, không phải số byte của bản mã, không phải số vòng tự chọn và cũng không phải tag.

- Trong Java JCA/JCE, `KeyGenerator.getInstance("AES")` tạo bộ sinh khóa AES; `KeyGenerator.init(128)` yêu cầu **128 bit**, không phải 128 byte.
- Với khóa AES thông thường do phần mềm xuất được, `SecretKey.getEncoded()` trả về byte khóa: AES-128 dài 16 byte.
- `SecretKey.getEncoded()` **có thể trả `null`** đối với khóa không được phép xuất, ví dụ một số khóa trên HSM/provider. Đừng xem việc có hay không có byte xuất ra là phép xác minh duy nhất cho mọi loại khóa.
- **AES có block size cố định 128 bit (16 byte)** bất kể khóa AES dài 128, 192 hay 256 bit. Không suy ra block size từ key size.
- Có nhiều byte khóa hơn chưa chắc làm hệ thống an toàn hơn nếu khóa được tạo từ mật khẩu yếu, bị hardcode, lộ qua log hoặc được quản trị sai.

Khi thấy cấu hình `AES-256-GCM`, phải tách ít nhất **khóa 256 bit**, **block AES 128 bit**, **nonce thường 96 bit**, và **tag thường 128 bit**; đây là bốn giá trị khác mục đích.

### 2.2. Khóa bất đối xứng: RSA-2048 và đường cong elliptic

**RSA-2048** nói tới độ dài **modulus n**, thông thường 2.048 bit = 256 byte, **không có nghĩa file chứa public key dài đúng 256 byte**. Public key RSA còn chứa exponent; khóa mã hóa thành X.509 SubjectPublicKeyInfo theo DER có ASN.1 và metadata. Private key PKCS#8 chứa nhiều thành phần hơn, do đó có thể dài hơn đáng kể.

- `RSAPublicKey.getModulus().bitLength()` cho **bit length thực của modulus**.
- `publicKey.getEncoded().length` cho **số byte của cấu trúc public key được mã hóa**. Hai số đo phục vụ hai câu hỏi khác nhau.
- Với RSA-2048, ciphertext RSA sau một phép mã hóa có kích thước modulus, thường **256 byte**, nhưng **plaintext tối đa lại nhỏ hơn nhiều**, phụ thuộc padding; chẳng hạn RSA-OAEP với SHA-256 làm hash OAEP có giới hạn **256 − 2 × 32 − 2 = 190 byte** mỗi lần mã hóa. Lưu ý cấu hình hash cho OAEP/MGF1 cần thống nhất theo giao thức, không tự suy diễn từ tên transformation.
- RSA chữ ký thường có kích thước bằng số byte modulus; đó không phải kích thước private key được lưu trên đĩa.

**P-256** là đường cong có tham số trường và bậc nhóm cỡ 256 bit; **không nên đánh đồng** nó với khóa AES-256 về mức an toàn tương đương. Public key EC có thể là điểm mã hóa dạng nén, không nén hoặc bọc X.509/DER; signature ECDSA cũng có thể ở dạng DER độ dài biến thiên hoặc dạng raw `r || s` cố định theo giao thức. Vì vậy số byte của “public key”/“signature” còn phụ thuộc **định dạng**.

### 2.3. Key size không bằng security strength

**Security strength (độ mạnh bảo mật)** ước lượng mức công việc tấn công, thường quy về số bit bảo mật đối với một mô hình tấn công cụ thể. Nó **không bằng số bit viết trong tên thuật toán**.

| Thuật toán/tham số | Độ dài hay nhìn thấy | Độ mạnh ước lượng cổ điển (xấp xỉ) |
| --- | --- | --- |
| AES-128 | Khóa 128 bit | 128 bit khi khóa ngẫu nhiên, không lộ và thuật toán dùng đúng |
| AES-256 | Khóa 256 bit | 256 bit chống vét cạn khóa trong mô hình lý tưởng |
| RSA-2048 | Modulus 2.048 bit | Khoảng 112 bit |
| RSA-3072 | Modulus 3.072 bit | Khoảng 128 bit |
| ECDSA/ECDH trên P-256 | Tham số đường cong cỡ 256 bit | Khoảng 128 bit |

Các số cuối là **ước lượng cổ điển, phụ thuộc giả định và hướng dẫn mật mã**; không phải cam kết tuyệt đối cho mọi hệ thống, đặc biệt khi có lỗi triển khai, kênh kề, hoặc mô hình máy tính lượng tử. **RSA-2048 không có “bảo mật 2.048 bit”.** Để chọn thuật toán và kích thước khóa cần xét mục tiêu bảo mật, thời gian bảo vệ dữ liệu, chuẩn giao thức và quy định hiện hành.

### 2.4. Độ dài vật liệu khóa và entropy là hai câu hỏi khác

**Entropy** đo độ khó đoán nguồn sinh khóa. Một mảng 32 byte bất kỳ không tự động có 256 bit entropy: 32 byte từ `SecureRandom` được dùng phù hợp là một chuyện; 32 ký tự lặp lại từ mật khẩu yếu là chuyện khác.

- Khóa ngẫu nhiên: dùng nguồn số ngẫu nhiên an toàn, ví dụ `KeyGenerator` hoặc `SecureRandom` của Java theo yêu cầu thuật toán.
- Khóa sinh từ mật khẩu: dùng password KDF phù hợp, kết hợp **salt**, cấu hình chi phí và quản lý khóa; không cắt/đệm chuỗi mật khẩu thành 16 hoặc 32 byte rồi gọi đó là AES-128/AES-256 an toàn.
- KDF có thể cho phép **độ dài đầu ra cấu hình được**. Ví dụ tham số `keyLength` của `PBEKeySpec` là **bit**, trong khi `salt` là **byte[]** và `iterations` là **số vòng lặp**; những con số này không cùng đơn vị.
- Một khóa được “kéo dài” bằng KDF không tự có entropy nhiều hơn mật khẩu đầu vào; độ dài vật lý và mức khó đoán phải được đánh giá riêng.

## 3. Độ dài đầu ra: digest, MAC, tag, ciphertext và encoded text

**Độ dài đầu ra** phải luôn kèm tên thao tác: đầu ra của hash, MAC, signature, encryption hay serialization. Mỗi thao tác có công thức hoặc cấu hình riêng.

### 3.1. Hash/digest: kích thước đầu ra được thuật toán quy định

| Thuật toán hash | Digest nhị phân | Biểu diễn hex thường gặp | Base64 chuẩn có padding |
| --- | ---: | ---: | ---: |
| SHA-256 | 256 bit = **32 byte** | 64 ký tự ASCII | 44 ký tự ASCII |
| SHA-384 | 384 bit = **48 byte** | 96 ký tự ASCII | 64 ký tự ASCII |
| SHA-512 | 512 bit = **64 byte** | 128 ký tự ASCII | 88 ký tự ASCII |
| SHA3-256 | 256 bit = **32 byte** | 64 ký tự ASCII | 44 ký tự ASCII |

Hash nhận đầu vào có độ dài thay đổi nhưng trả digest kích thước cố định theo thuật toán. `MessageDigest.getInstance("SHA-256").digest(bytes)` trả **mảng 32 byte**. Kích thước digest **không phụ thuộc số byte của thông điệp đầu vào**; dữ liệu 3 byte hay 3 GB đều tạo digest SHA-256 dài 32 byte.

**SHA-256 có đầu ra 256 bit không có nghĩa “chống va chạm 256 bit”**. Với hash lý tưởng 256 bit, chống va chạm tổng quát chịu giới hạn birthday khoảng **2^128 phép thử**, trong khi tìm tiền ảnh tổng quát khoảng **2^256**. Độ dài digest là kích thước dữ liệu; các mục tiêu chống tấn công phải nêu rõ loại tấn công.

Đừng nhầm **block size của hàm nén SHA-256** (512 bit = 64 byte mỗi khối nội bộ) với **digest size** (256 bit = 32 byte). Tên SHA-256 nói tới kích thước đầu ra, không phải kích thước khóa (SHA-256 không dùng khóa).

### 3.2. HMAC: độ dài khóa và độ dài tag độc lập

**HmacSHA256** dùng SHA-256 trong cơ chế MAC. Java `Mac.getInstance("HmacSHA256")` thường tạo **tag 32 byte (256 bit)**, dù khóa HMAC đầu vào có thể dài **16, 32 hoặc 64 byte** nếu đáp ứng yêu cầu thiết kế; **tên “SHA256” không bắt khóa phải dài 256 bit**.

- `SecretKey` cho HMAC là khóa bí mật, thường có số byte do chương trình/giao thức chọn.
- `Mac.getMacLength()` cho biết **độ dài MAC đầu ra tính bằng byte** của instance.
- Một giao thức có thể cắt ngắn tag HMAC theo thông số được phê duyệt; khi đó số byte truyền/lưu **khác** digest đầy đủ. Không tự ý truncate ở cả hai đầu mà không có đặc tả về độ dài tag và mức bảo mật.
- HMAC cung cấp bằng chứng về khóa chung và tính toàn vẹn/xác thực thông điệp, **không mã hóa** nội dung.

Một ví dụ phản chứng: khóa HMAC dài 64 byte không khiến HmacSHA256 xuất ra 64 byte; tag mặc định vẫn **32 byte**. Ngược lại, đổi sang HmacSHA512 đổi kích thước tag đầy đủ thành **64 byte**, nhưng không có nghĩa bắt buộc khóa dài 64 byte.

### 3.3. AES-GCM: phân biệt khóa, nonce, tag và toàn bộ output

Trong ví dụ phổ biến dùng **AES-128-GCM** với **nonce 12 byte** và **tag 128 bit = 16 byte**:

| Thành phần | Ví dụ độ dài | Vai trò |
| --- | ---: | --- |
| AES key | 16 byte = 128 bit | Khóa bí mật |
| GCM nonce/IV | 12 byte = 96 bit | Giá trị cần duy nhất dưới cùng khóa; không phải khóa |
| Plaintext | 5 byte | Dữ liệu đầu vào, ví dụ text “hello” UTF-8 |
| Ciphertext thuần | 5 byte | Phần dữ liệu đã mã hóa trong GCM, không tính tag |
| Authentication tag | 16 byte = 128 bit | Bảo vệ toàn vẹn/xác thực ciphertext và AAD |
| Kết quả `Cipher.doFinal(plaintext)` khi mã hóa với GCM trong JCA thông dụng | **21 byte** | **5 byte ciphertext + 16 byte tag** |
| Nếu tự đóng gói `nonce || ciphertext || tag` | **33 byte** | **12 + 5 + 16**, chưa tính version/metadata khác |

Ở đây **`GCMParameterSpec(128, nonce)` nhận độ dài tag theo bit**, còn `nonce` là `byte[]` dài 12 phần tử. **128 ở `GCMParameterSpec` không chỉ độ dài khóa AES**.

Nếu giao thức lưu nonce bên ngoài, `doFinal()` không tự thêm 12 byte nonce vào output. Phải định nghĩa định dạng lưu/truyền có thể giải mã được, bao gồm version, nonce, ciphertext, tag và dữ liệu AAD cần thiết. GCM không dùng padding kiểu PKCS#5 cho plaintext; ví dụ ở trên minh họa đúng độ dài đối với JCA AES/GCM/NoPadding.

**Cảnh báo về nonce:** nonce 96 bit cần được tạo/quản lý sao cho **không lặp lại với cùng khóa**. Nó thường có thể công khai, nhưng việc lặp nonce với AES-GCM có thể phá nghiêm trọng tính bí mật và tính toàn vẹn. Không dùng độ dài “đủ 12 byte” làm bằng chứng rằng nonce là duy nhất.

### 3.4. Encoding làm tăng số byte hiển thị, không tăng độ mạnh mật mã

Một byte nhị phân bất kỳ có thể không phải ký tự văn bản hợp lệ. Khi cần in/lưu byte khóa, digest hoặc ciphertext dưới dạng text, phải chọn **encoding**; kích thước text sẽ khác mảng nhị phân.

- **Hex:** mỗi byte tạo **2 ký tự ASCII**; `n` byte → **2n ký tự**. Digest 32 byte → 64 ký tự hex.
- **Base64 chuẩn có padding:** `n` byte → **4 × ceil(n / 3)** ký tự ASCII. Digest 32 byte → 44 ký tự, thường kết thúc bằng dấu `=`.
- **Base64URL bỏ padding:** dùng bảng ký tự phù hợp URL và có thể ít ký tự hơn bản có padding. Với 32 byte → **43 ký tự** không padding.
- Chuỗi hex/Base64 chứa ký tự ASCII nên khi mã hóa UTF-8 thường có **1 byte mỗi ký tự**. Tuy vậy **32 byte nhị phân ≠ 32 byte chuỗi Base64**, và số ký tự chưa nói gì về entropy mới.

Hex, Base64 và Base64URL **chỉ biểu diễn byte**, không phải mã hóa để bảo mật hoặc hash. Đừng công khai key chỉ vì nó đã được chuyển sang Base64; không log hex của khóa bí mật trong ứng dụng.

### 3.5. Signature, RSA ciphertext và định dạng container

Một chữ ký RSA với modulus 2.048 bit thường dài **256 byte** ở dạng raw signature. Chữ ký Ed25519 theo đặc tả dài **64 byte**, nhưng các định dạng đóng gói tệp có thể thêm header/chứng chỉ. ECDSA có đầu ra là cặp số `(r, s)`, nên độ dài byte phụ thuộc định dạng raw/DER và quy tắc mã hóa.

Với RSA-OAEP, đầu ra mã hóa (ciphertext) có độ dài bằng số byte modulus, nhưng **kích thước plaintext tối đa** bị giới hạn bởi modulus và padding. Ví dụ RSA-2048 + OAEP dùng SHA-256 làm hash OAEP: **tối đa 190 byte plaintext**, không phải 190 ký tự Unicode.

**Kết luận thực hành của mục này:** cần viết rõ “byte của payload nhị phân”, “bit của khóa”, “byte của digest”, “bit của tag”, “byte của file DER”, “ký tự Base64”, thay vì nói chung “độ dài 256”.

## 4. Java 17+: đo trực tiếp độ dài các loại dữ liệu mật mã

Chương trình sau dùng JCA/JCE của Java để so sánh: UTF-8, SHA-256, hex/Base64, AES-128, AES-GCM và HmacSHA256. Các byte khóa/nonce được sinh ngẫu nhiên nên thay đổi giữa các lần chạy, nhưng **những số đo độ dài được in ra là cố định** đối với cấu hình này.

Lưu đúng tên file **CryptoLengthsDemo.java**:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoLengthsDemo {
    public static void main(String[] args) throws Exception {
        byte[] utf8 = "Việt".getBytes(StandardCharsets.UTF_8);
        System.out.printf("Text UTF-8: %d bytes = %d bits%n",
                utf8.length, utf8.length * Byte.SIZE);

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest("abc".getBytes(StandardCharsets.UTF_8));
        System.out.printf("SHA-256: %d bytes = %d bits%n",
                digest.length, digest.length * Byte.SIZE);
        System.out.printf("SHA-256 hex: %d characters%n",
                HexFormat.of().formatHex(digest).length());
        System.out.printf("SHA-256 Base64: %d characters%n",
                Base64.getEncoder().encodeToString(digest).length());

        KeyGenerator aesGenerator = KeyGenerator.getInstance("AES");
        aesGenerator.init(128);
        SecretKey aesKey = aesGenerator.generateKey();
        System.out.printf("AES-128 key: %d bytes%n",
                aesKey.getEncoded().length);

        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] plaintext = "hello".getBytes(StandardCharsets.UTF_8);
        Cipher encryptor = Cipher.getInstance("AES/GCM/NoPadding");
        encryptor.init(Cipher.ENCRYPT_MODE, aesKey,
                new GCMParameterSpec(128, nonce));
        byte[] ciphertextAndTag = encryptor.doFinal(plaintext);
        System.out.printf(
                "GCM nonce: %d bytes; plaintext: %d bytes; output: %d bytes%n",
                nonce.length, plaintext.length, ciphertextAndTag.length);

        KeyGenerator macGenerator = KeyGenerator.getInstance("HmacSHA256");
        macGenerator.init(256);
        SecretKey macKey = macGenerator.generateKey();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(macKey);
        byte[] hmac = mac.doFinal(plaintext);
        System.out.printf("HmacSHA256 key: %d bytes; tag: %d bytes%n",
                macKey.getEncoded().length, hmac.length);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên, chỉ rõ UTF-8 cho mã nguồn:

```bash
javac -encoding UTF-8 CryptoLengthsDemo.java
java CryptoLengthsDemo
```

Stdout kỳ vọng (các giá trị độ dài đã được kiểm tra bằng JShell):

```text
Text UTF-8: 6 bytes = 48 bits
SHA-256: 32 bytes = 256 bits
SHA-256 hex: 64 characters
SHA-256 Base64: 44 characters
AES-128 key: 16 bytes
GCM nonce: 12 bytes; plaintext: 5 bytes; output: 21 bytes
HmacSHA256 key: 32 bytes; tag: 32 bytes
```

**Đọc kết quả theo đúng đại lượng:**

1. Chữ “Việt” được biến thành 6 byte UTF-8; đây là **dung lượng biểu diễn** của text, không phải 48 bit độ ngẫu nhiên.
2. SHA-256 digest là 32 byte, nhưng biểu diễn hex là 64 ký tự; Base64 chuẩn là 44 ký tự.
3. `aesGenerator.init(128)` nhận **bit**, còn `aesKey.getEncoded().length` đo **byte**.
4. `GCMParameterSpec(128, nonce)` chỉ **tag 128 bit**; kết quả của `doFinal()` là 5 byte ciphertext cộng 16 byte tag; nonce 12 byte đang được giữ **tách riêng**.
5. Khóa HMAC do ta cố ý sinh dài 256 bit = 32 byte; HmacSHA256 trả tag 32 byte **do thuật toán MAC**, không vì chương trình bắt hai độ dài trùng nhau.

Ví dụ dùng khóa có thể xuất byte từ provider phần mềm phổ biến. Đối với HSM hoặc các khóa không cho xuất, `getEncoded()` có thể trả `null`; khi đó lấy thông số khóa từ API/provider được hỗ trợ thay vì cố dump khóa để kiểm tra size. Không log hoặc gửi mảng khóa ra bên ngoài.

## 5. Java 17+: modulus RSA và kích thước tệp khóa là hai số khác nhau

Ví dụ độc lập này tạo cặp khóa RSA 2.048 bit, đo bit length của modulus rồi kiểm tra xem byte mã hóa X.509/PKCS#8 của key object có lớn hơn byte của modulus không.

Lưu **RsaLengthsDemo.java**:

```java
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

public class RsaLengthsDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        int modulusBits = publicKey.getModulus().bitLength();
        int modulusBytes = (modulusBits + 7) / 8;

        System.out.printf("RSA modulus: %d bits = %d bytes%n",
                modulusBits, modulusBytes);
        System.out.printf("X.509 encoded public key exceeds modulus: %b%n",
                publicKey.getEncoded().length > modulusBytes);
        System.out.printf("PKCS#8 encoded private key exceeds modulus: %b%n",
                pair.getPrivate().getEncoded().length > modulusBytes);
    }
}
```

```bash
javac -encoding UTF-8 RsaLengthsDemo.java
java RsaLengthsDemo
```

Stdout khi dùng provider JDK thông thường:

```text
RSA modulus: 2048 bits = 256 bytes
X.509 encoded public key exceeds modulus: true
PKCS#8 encoded private key exceeds modulus: true
```

**Tại sao?** `getModulus()` trả số nguyên `n` trong RSA; `bitLength()` đếm số bit cần cho `n`. `getEncoded()` của public key thường chứa SubjectPublicKeyInfo X.509, còn private key thường chứa PKCS#8, bao gồm nhiều giá trị và metadata. **Không dùng `getEncoded().length * 8` để phát biểu độ dài modulus RSA hoặc mức bảo mật.**

Việc sinh cặp khóa RSA ngẫu nhiên có thể mất thời gian tùy môi trường, nhưng số đo modulus trong ví dụ không đổi theo byte ngẫu nhiên. Các biểu diễn DER/PEM và nhà cung cấp khác nhau có thể cho độ dài file khác nhau; vì vậy chương trình **không** hardcode số byte chính xác của tệp khóa. Hãy bảo vệ private key, tránh in nội dung khóa hoặc đưa khóa thực vào tài liệu.

## 6. Cách chọn độ dài khi thiết kế API, lưu trữ và kiểm tra đầu ra

Trước khi tạo schema lưu trữ hoặc truyền dữ liệu, xác định **bốn câu hỏi**:

1. **Đại lượng gì?** Khóa bí mật, IV/nonce, salt, hash, MAC/tag, ciphertext, chữ ký, public key hay chuỗi biểu diễn?
2. **Đơn vị gì?** Bit, byte, số ký tự ASCII, UTF-16 code unit hay Unicode code point?
3. **Định dạng gì?** Raw binary, hex, Base64, Base64URL, DER, PEM, định dạng envelope có header/nonce/tag?
4. **Do ai quyết định kích thước?** Thuật toán cố định, tham số cấu hình, độ dài plaintext, tùy chọn padding hoặc giao thức?

Ví dụ triển khai một trường hợp gửi **32 byte digest SHA-256**:

- Với trường binary, kích thước digest là **32 byte**.
- Với trường text hex, cần chứa **64 ký tự ASCII**.
- Với Base64 chuẩn có padding, cần chứa **44 ký tự ASCII**.
- Nếu lưu thêm id thuật toán, version và separator, phải tính **phần overhead** ngoài digest.
- Không biến SHA-256 thành chuỗi bằng cách gọi `new String(digest, UTF_8)`: digest là dữ liệu binary tùy ý, có thể không phải UTF-8 hợp lệ; dùng `HexFormat` hoặc `Base64` nếu thật sự cần text.

Ví dụ lưu kết quả mã hóa AES-GCM của plaintext 1.000 byte với tag 16 byte và nonce 12 byte: `doFinal` trong cấu hình thông thường trả **1.016 byte**, còn envelope `nonce || ciphertext || tag` yêu cầu ít nhất **1.028 byte** trước khi thêm version, AAD metadata và các trường khác. Nếu đem envelope 1.028 byte này đi Base64 chuẩn, đầu ra thành **4 × ceil(1.028/3) = 1.372 ký tự ASCII**. Mỗi bước thêm **định dạng**, không tự gia tăng độ bảo mật.

### 6.1. Dùng API Java theo đúng đơn vị

| API Java | Giá trị/đơn vị quan trọng | Sai lầm cần tránh |
| --- | --- | --- |
| `Byte.SIZE`, `Byte.BYTES` | 8 bit/byte và 1 byte | Xem `byte` là 1 bit do kiểu tên “byte” |
| `byte[].length` | Số byte | Đọc thẳng thành số bit |
| `String.length()` | Đơn vị mã UTF-16 | Coi là byte UTF-8 hoặc code point |
| `getBytes(UTF_8).length` | Byte của văn bản sau UTF-8 | Không chốt encoding |
| `KeyGenerator.init(128)` cho AES | Kích thước khóa tính bằng **bit** | Truyền 16 vì muốn khóa 16 byte |
| `PBEKeySpec(..., keyLength)` | `keyLength` tính bằng **bit** | Nhầm với `salt.length` tính bằng byte |
| `GCMParameterSpec(128, iv)` | Độ dài authentication tag tính bằng **bit** | Hiểu 128 là số byte IV hoặc key size |
| `MessageDigest.digest(...).length` | Byte của digest thực tế | Nhìn tên SHA-256 rồi ghi 256 byte |
| `MessageDigest.getDigestLength()` | Byte của digest (nếu provider hỗ trợ báo độ dài) | Giả định trả bit |
| `Mac.getMacLength()` | Byte của MAC đầy đủ | Coi đó là độ dài khóa MAC |
| `RSAPublicKey.getModulus().bitLength()` | Bit của modulus RSA | Coi `getEncoded().length` là modulus |
| `Key.getEncoded()` | Byte của định dạng xuất khóa, hoặc `null` | Tự suy ra bit security từ byte encoded |

### 6.2. Quy tắc lưu trữ và giới hạn

- Với dữ liệu cryptographic binary, lưu dưới dạng binary nếu hệ thống cho phép; chỉ encode thành text ở ranh giới thật sự cần thiết (JSON, HTTP header, cấu hình). 
- Nếu lưu digest/hash thành hex, giới hạn cột/tệp theo **độ dài chuỗi đã encode**, không chỉ độ dài digest nhị phân.
- Đầu ra mã hóa phụ thuộc chế độ, padding, tag, overhead và plaintext thực tế. Đừng cấp phát buffer chỉ theo kích thước khóa; dùng API hỗ trợ tính output size hoặc xử lý kết quả `doFinal()` trả về.
- Khi thay đổi cấu hình thuật toán (ví dụ SHA-256 → SHA-512 hoặc tag 96 → 128 bit), kiểm tra schema, serialization và compatibility.
- Dữ liệu khóa nhạy cảm không nên lưu/log dưới dạng hex, Base64 hoặc plaintext; các encoding đó không cung cấp bảo mật.

## 7. Lỗi phổ biến và cách suy nghĩ đúng

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “AES-256 cần `byte[256]`.” | AES-256 cần **256 bit = 32 byte** khóa. |
| “Khóa AES-256 tạo ciphertext cố định 256 byte.” | Key size **không quyết định** độ dài ciphertext; nó phụ thuộc plaintext và chế độ/tag/padding. |
| “RSA-2048 public key luôn là 256 byte.” | **Modulus** thường 256 byte; public key được encode có thêm exponent và metadata. |
| “SHA-256 trả 256 byte.” | Digest SHA-256 là **256 bit = 32 byte**. |
| “Chuỗi hex 64 ký tự nghĩa là digest 64 byte.” | 64 ký tự hex biểu diễn **32 byte** binary. |
| “HmacSHA256 buộc key phải đúng 32 byte.” | Key size và tag size là hai đại lượng riêng; tag đầy đủ HmacSHA256 là **32 byte**. |
| “Tag GCM 128 nghĩa là nonce 128 byte.” | `GCMParameterSpec(128, nonce)` có tag **128 bit = 16 byte**; nonce được truyền bằng mảng byte riêng. |
| “Dùng Base64 làm khóa mạnh hơn.” | Base64 chỉ biến đổi biểu diễn; entropy không tăng. |
| “32 byte mật khẩu tự có 256 bit bảo mật.” | Độ mạnh phụ thuộc cách sinh/mật khẩu và KDF, không chỉ số byte. |
| “`String.length()` là byte length của request.” | Nó đếm UTF-16 code units; cần encode thành byte trước khi đo. |

## 8. Bài tập tự kiểm tra

**Câu 1.** KeyGenerator AES cần khóa 192 bit. Mảng byte xuất của khóa AES thông thường dài bao nhiêu?  
**Đáp án:** **24 byte**. Trong Java dùng `aesGenerator.init(192)`, không dùng `init(24)`.

**Câu 2.** SHA-512 băm một file 8 GB. Kết quả digest dài bao nhiêu, nếu chuyển sang hex thì dài bao nhiêu?  
**Đáp án:** **64 byte digest**; **128 ký tự hex**. Dung lượng file không đổi độ dài digest.

**Câu 3.** Một MAC HmacSHA256 dùng khóa 48 byte và không cắt tag. Dự kiến tag dài bao nhiêu?  
**Đáp án:** **32 byte**. Kích thước key 48 byte không biến tag SHA-256 thành 48 byte.

**Câu 4.** AES-GCM mã hóa 20 byte plaintext, dùng tag 128 bit và lưu nonce 12 byte bên cạnh bản mã. Cần bao nhiêu byte cho `nonce || ciphertext || tag` trước header?  
**Đáp án:** **12 + 20 + 16 = 48 byte**. Phần `Cipher.doFinal()` của ví dụ JCA là **36 byte** (ciphertext + tag), chưa chứa nonce.

**Câu 5.** Chuỗi Base64 dài 44 ký tự có chắc chắn giải mã ra đúng 44 byte không?  
**Đáp án:** **Không.** Base64 mang 6 bit dữ liệu mỗi ký tự và có thể có padding; ví dụ 32 byte binary tạo **44 ký tự Base64 chuẩn**. Cần decode để biết byte thực.

**Câu 6.** RSA-2048 có modulus 256 byte; tại sao `publicKey.getEncoded().length` có thể lớn hơn 256?  
**Đáp án:** Vì đó là **định dạng khóa công khai được đóng gói** với exponent, cấu trúc ASN.1 và thông tin thuật toán, không chỉ raw modulus.

**Câu 7.** Một biến `String` có 4 đơn vị mã UTF-16 thì chắc chắn có 4 byte UTF-8?  
**Đáp án:** **Không.** Các code point có thể dùng nhiều byte UTF-8. Chuỗi “Việt” trong ví dụ có 4 đơn vị mã UTF-16 nhưng 6 byte UTF-8.

### Ghi nhớ khi đọc đặc tả

**Luôn ghi kèm “của cái gì” và “đơn vị gì”:** *AES key 256 bit*, *GCM tag 128 bit*, *nonce 12 byte*, *SHA-256 digest 32 byte*, *hex digest 64 ký tự*, *RSA modulus 2.048 bit*, *RSA public key DER dài bao nhiêu byte*. Độ dài vật lý, độ dài sau encoding, độ mạnh bảo mật và entropy là các khái niệm riêng; muốn chọn kích thước đúng cần nhận diện cả bốn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-randomness-004">3.4 Vai trò của entropy; phân biệt SecureRandom với Random và ThreadLocalRandom</a>

<details>
<summary>Xem chi tiết</summary>

# 3.4. Vai trò của entropy; phân biệt SecureRandom với Random và ThreadLocalRandom

## Mục tiêu học tập

Kết thúc bài, bạn sẽ hiểu **entropy** là gì, vì sao một bộ sinh “trông ngẫu nhiên” vẫn có thể bị dự đoán; biết phân biệt `java.util.Random`, `java.util.concurrent.ThreadLocalRandom` và `java.security.SecureRandom`; chọn đúng API cho secret/token, salt, IV/nonce, mô phỏng và tác vụ đa luồng; đồng thời sử dụng Java 17+ để tạo dữ liệu ngẫu nhiên an toàn theo đúng mục đích.

Đây là bài về **dữ liệu và ngẫu nhiên trong Java security-cryptography**. Việc gọi đúng generator là nền móng bảo mật, nhưng không tự thay thế mã hóa, quản lý khóa, xác thực, giới hạn thử hoặc kiểm tra quyền truy cập.

## 1. Tại sao “ngẫu nhiên” chưa chắc “an toàn”?

Một trò chơi cần xúc xắc công bằng. Một máy chủ lại cần reset token mà kẻ tấn công không đoán được, ngay cả khi biết nhiều token khác. Hai bài toán đều muốn phân bố có vẻ ngẫu nhiên, nhưng bài thứ hai có yêu cầu chống dự đoán mang tính đối kháng.

Giả sử một chương trình phát dãy `8, 1, 6, 3, 7, 2`. Chỉ nhìn sáu số, ta không thể biết nó đến từ nguồn vật lý, một PRNG có seed cố định hay danh sách hard-code. **Thống kê bề ngoài không đủ để xác nhận an toàn mật mã.** Khi adversary biết thuật toán hoặc đoán được seed/trạng thái, dãy có thể hoàn toàn dự đoán được dù histogram rất đẹp.

Thuật ngữ:

| Khái niệm | Ý nghĩa | Cạm bẫy |
| --- | --- | --- |
| **Entropy** | Mức bất định của nguồn dữ liệu, liên quan trực tiếp khả năng đoán một giá trị | Số ký tự và kích thước buffer không trực tiếp cho biết entropy |
| **Nguồn entropy** | Nguồn bất định thực sự, thường do hệ điều hành/nền tảng thu thập và xử lý | Timestamp, PID, hostname hoặc `System.nanoTime()` không bảo đảm khó đoán |
| **Seed** | Dữ liệu đầu vào khởi tạo trạng thái bộ sinh | Seed dài 256 bit nhưng lấy từ một timestamp vẫn dễ brute force |
| **PRNG** | Pseudo-random number generator: thuật toán xác định sinh nhiều giá trị từ seed/trạng thái | Phân bố tương đối đều không có nghĩa chống dự đoán |
| **CSPRNG/DRBG** | PRNG thiết kế cho mật mã; các bit đầu ra phải khó dự đoán khi trạng thái bí mật an toàn | Vẫn phụ thuộc cách seed, provider, trạng thái, chính sách reseed |
| **Salt** | Giá trị khác nhau giữa các lần băm/dẫn xuất, thường công khai | Salt không thay thế KDF/password hash có chi phí |
| **Nonce/IV** | Giá trị dùng trong một phép mật mã; yêu cầu uniqueness/unpredictability tùy thuật toán | Với AES-GCM, tái sử dụng nonce cùng khóa là lỗi rất nặng |
| **Secret token** | Giá trị mang quyền/khả năng xác thực | Cần **khó đoán**, giữ kín, có TTL/rotation/revocation |

### 1.1. Min-entropy: cách nhìn phù hợp với đối thủ đoán giá trị

Với biến ngẫu nhiên `X`, min-entropy định nghĩa theo xác suất của **giá trị dễ đoán nhất**:

```text
H_min(X) = -log₂(max_x P[X = x])
```

Nếu generator luôn trả lại cùng một số, `H_min = 0 bit`. Nếu mã sáu chữ số được chọn đều từ `000000` đến `999999`, không gian là một triệu giá trị và min-entropy tối đa xấp xỉ `log₂(1_000_000) = 19,93 bit`. Điều này giải thích vì sao OTP sáu số phải kết hợp giới hạn thử, thời hạn ngắn, chống replay và bảo vệ kênh truyền.

Một token gồm 32 byte ngẫu nhiên lý tưởng có **256 bit** dữ liệu không thể đoán khi được lấy từ nguồn thích hợp. Nhưng **32 byte đầu ra không chứng minh có 256 bit entropy mới được thu thập mỗi lần gọi**: nhiều CSPRNG sử dụng thuật toán mật mã để mở rộng một seed/trạng thái bí mật thành nhiều byte. Độ an toàn phụ thuộc seed ban đầu, thiết kế DRBG và môi trường.

Băm SHA-256 một timestamp sinh ra 256 bit *output* nhưng không tạo ra 256 bit entropy nếu timestamp dễ đoán. Mã hóa Base64/hex cũng chỉ thay cách hiển thị, không thêm thông tin bất định. Tạo 256 ký tự từ một `Random(42)` vẫn có seed đã biết.

### 1.2. Từ entropy đến số ngẫu nhiên ở ứng dụng

```text
OS/nguồn entropy
      │ đánh giá và xử lý nguồn
      ▼
seed bí mật (sức mạnh phụ thuộc entropy thực)
      │ khởi tạo
      ▼
CSPRNG / DRBG của security provider
      │ sinh nextBytes(...)
      ▼
byte để tạo token, salt, khóa, nonce...
```

Mô hình trên là khái niệm, không phải cam kết rằng JDK nào cũng dùng cùng thuật toán/hệ điều hành. Một DRBG có thể tiếp tục sinh số từ trạng thái mật mã giữa các lần reseed thay vì “đọc 32 byte entropy vật lý” với mỗi `nextBytes(32)`. Điều này **không** tự làm nó kém an toàn.

Phải tách hai câu hỏi: *nguồn entropy có khó đoán không?* và *thuật toán mở rộng seed có được thiết kế cho mật mã không?*. Kiểm tra một dãy bằng vài phép thống kê đơn giản không thể thay thế đánh giá hai lớp này.

## 2. Bảng so sánh ba API chính

| Thuộc tính | `Random` | `ThreadLocalRandom` | `SecureRandom` |
| --- | --- | --- | --- |
| Package | `java.util` | `java.util.concurrent` | `java.security` |
| Mục đích | Mô phỏng, dữ liệu ngẫu nhiên thông thường, test có thể tái lập | Số giả ngẫu nhiên hiệu quả cho công việc đa luồng | Dữ liệu khó dự đoán dùng trong bảo mật |
| Bản chất | Trong Java 17, lớp `Random` có LCG với seed 48 bit | Generator per-thread, triển khai có tối ưu cho concurrency | Engine qua `SecureRandomSpi` của security provider; có thể là DRBG |
| Khởi tạo | `new Random()`, `new Random(42L)` | `ThreadLocalRandom.current()` | `new SecureRandom()` |
| Seed cố định | Cùng seed + cùng chuỗi lời gọi → cùng dãy | Không hỗ trợ tự set seed qua `setSeed` | Không tự dùng seed yếu hoặc cố định trong production |
| Concurrent | Thread-safe nhưng instance chung có thể contention | Thiết kế giảm contention khi nhiều thread | Thread-safe; hiệu năng tùy provider/thuật toán |
| Có chống dự đoán mật mã? | **Không** | **Không** | **Có**, với triển khai và seed an toàn |
| Token/secret/API key | **Không dùng** | **Không dùng** | **Dùng** với độ dài, vòng đời và lưu trữ thích hợp |
| Trường hợp phù hợp | Game, đơn vị test, simulation | Parallel simulation, sampling, jitter không bí mật | Token, salt, IV/nonce, secret material |

**Quy tắc chọn:** nếu ai đó đoán được số tiếp theo sẽ làm mất quyền truy cập, lộ bí mật hoặc phá tính đúng của giao thức, dùng `SecureRandom`/API mật mã phù hợp. Nếu chỉ cần dữ liệu để phân bố tải, trò chơi hoặc test, `Random` và `ThreadLocalRandom` thường thích hợp hơn.

### 2.1. `Random`: tái lập là tính năng và cũng là giới hạn

JDK 17 quy định `Random` dùng seed 48 bit và công thức tuyến tính đồng dư (LCG). Hai instance được khởi tạo cùng seed, dùng cùng sequence phương thức, sẽ trả kết quả giống nhau. Điều đó giúp **reproducible simulation**, nhưng không chống được đối thủ suy ra trạng thái.

```java
import java.util.Random;

Random a = new Random(2026L);
Random b = new Random(2026L);
boolean same = a.nextInt(1_000) == b.nextInt(1_000);
// same là true vì cùng seed và cùng lời gọi.
```

Đây là đoạn minh họa để đặt trong method; ví dụ chạy độc lập ở mục 6. `new Random()` tự chọn seed nhưng vẫn **không bảo đảm an toàn mật mã**. `Math.random()` cũng không dùng để tạo secret.

`Random` instance thread-safe, tuy vậy nhiều thread dùng chung một instance có thể tranh chấp trạng thái và giảm throughput. Tối ưu hiệu năng thường dẫn đến `ThreadLocalRandom`; tối ưu bảo mật phải chọn `SecureRandom`.

### 2.2. `ThreadLocalRandom`: giảm contention, không tăng bảo mật

Dùng:

```java
import java.util.concurrent.ThreadLocalRandom;

int number = ThreadLocalRandom.current().nextInt(10, 20);
// Số trong [10, 20), giá trị cụ thể không cố định.
```

Mỗi lần cần số trong worker/thread hiện tại, hãy gọi `ThreadLocalRandom.current()` ở chính thread đó. Không giữ generator của thread A rồi chia cho thread B. API `setSeed(long)` của lớp này luôn ném `UnsupportedOperationException`; nếu cần seed reproducible, dùng thiết kế generator khác.

Một hiểu nhầm: property `java.util.secureRandomSeed=true` có thể khiến nguồn seed khởi tạo khác đi, nhưng **không biến thuật toán `ThreadLocalRandom` thành CSPRNG**. Mục đích của lớp vẫn là hiệu năng và phân bố phi mật mã.

Ứng dụng nên dùng lớp này cho mô phỏng song song, random delay/jitter không liên quan bí mật, sampling hoặc tạo dữ liệu giả. Không dùng nó cho reset token, mật khẩu, key, nonce cần bảo mật hay session ID.

### 2.3. `SecureRandom`: engine dành cho mật mã

`SecureRandom` kế thừa `Random` trong hệ phân cấp Java, nhưng **không sử dụng thuật toán LCG 48 bit của `Random` để tạo đầu ra mật mã**. Nó chuyển sang triển khai của security provider:

```java
import java.security.SecureRandom;

SecureRandom secure = new SecureRandom();
byte[] bytes = new byte[32];
secure.nextBytes(bytes);
```

`new SecureRandom()` chọn provider/algorithm dựa trên cấu hình runtime. Có thể in `secure.getAlgorithm()`, `secure.getProvider().getName()` để chẩn đoán nhưng không nên hard-code tên provider trên mọi máy.

`SecureRandom` bảo đảm thread safety ở lớp API: triển khai provider có thể thread-safe nội bộ, hoặc JDK đồng bộ hóa lời gọi. **Thread-safe không có nghĩa throughput luôn như nhau**; tùy provider, reseed và nguồn entropy, chi phí có thể khác nhau.

## 3. Seed và vòng đời `SecureRandom`: chỗ dễ sai nhất

### 3.1. Tự khởi tạo (self-seeding)

Một đối tượng `SecureRandom` sinh bằng constructor mặc định, khi lần đầu được dùng để lấy byte, thường để provider tự lấy nguồn entropy thích hợp. Ứng dụng chỉ cần:

```java
SecureRandom secure = new SecureRandom();
byte[] token = new byte[32];
secure.nextBytes(token);
```

**Đừng tự “giúp” nó bằng timestamp hay hằng số.** Theo tài liệu Java 17, với một `SecureRandom` dạng PRNG, gọi `setSeed` **trước lần `nextBytes` hoặc `reseed` đầu tiên** có thể khiến bước self-seeding không diễn ra. Lúc đó ứng dụng tự chịu trách nhiệm seed phải có đủ entropy.

```java
// SAI trong production: timestamp có thể bị đoán!
SecureRandom insecure = new SecureRandom();
insecure.setSeed(System.currentTimeMillis());
byte[] secret = new byte[32];
insecure.nextBytes(secret);
```

Đoạn trên là ví dụ **để nhận diện lỗi**, không phải hướng dẫn khởi tạo. Tương tự, `new SecureRandom(new byte[] {1, 2, 3})` với seed cố định không phải cách tạo token an toàn.

### 3.2. Các API liên quan và ranh giới sử dụng

Với dữ liệu bí mật thông thường, tạo một `SecureRandom` để provider tự seed và gọi `nextBytes(byte[])`. `generateSeed(int)` cung cấp seed material theo cơ chế của provider, không phải bước bắt buộc trước `nextBytes`. `setSeed(...)` nhận dữ liệu do caller cung cấp; nếu gọi bằng timestamp/seed yếu trước lần tự seed, kết quả có thể mất tính khó đoán. `reseed()` yêu cầu entropy mới nhưng có thể không được hỗ trợ hoặc phải chờ nguồn entropy.

`getInstanceStrong()` chọn theo security property `securerandom.strongAlgorithms`, còn `getInstance("DRBG")` yêu cầu engine DRBG nếu có provider hỗ trợ. Chúng không tự đồng nghĩa với tốc độ tốt hơn hoặc hoạt động không chặn. Bài 3.5 đào sâu seed/reseed và Bài 3.6 phân tích DRBG, cấu hình strong cùng phép đo độ trễ; ở đây chỉ cần nhớ **chọn `SecureRandom` cho secret, kiểm soát entropy, không tự seed bằng giá trị đoán được**.

### 3.3. Dùng chung và đo hiệu năng

`SecureRandom` hỗ trợ sử dụng từ nhiều thread; có thể chia sẻ instance được khởi tạo đúng. Không nên liên tục tạo instance chỉ vì nghĩ “mỗi constructor sẽ thêm entropy”. Nếu ứng dụng sinh số lượng token cực lớn, đo latency/throughput trên provider và OS thực, xác định chi phí initialization/locking trước khi quyết định tối ưu.

**Không** thay `SecureRandom` bằng `ThreadLocalRandom` chỉ để cải thiện tốc độ khi output phải giữ bí mật. Cũng không nhầm “nhiều thread” với “nhiều nguồn entropy độc lập”: thread confinement là vấn đề hiệu năng, còn cryptographic unpredictability là vấn đề bảo mật.

## 4. Ứng dụng entropy: token, key, salt, nonce và OTP

| Nhu cầu | Chọn gì | Lý do và điều kiện kèm theo |
| --- | --- | --- |
| Session ID, API key, reset token, bearer token | `SecureRandom.nextBytes`, encode Base64URL/hex | Cần chống đoán, bảo quản bí mật, TTL, rotation, revoke, kiểm quyền |
| CSRF token ngẫu nhiên | `SecureRandom`/framework bảo mật chuẩn | Không chỉ một số pseudo-random dễ đoán |
| AES/HMAC secret key | `KeyGenerator` hoặc cơ chế sinh khóa mật mã phù hợp | Phải quản lý lưu trữ, vòng đời, rotation và quyền truy cập |
| Password hashing salt | `SecureRandom`, salt riêng cho mỗi bản ghi | Salt thường công khai; phải dùng KDF/password hashing chuẩn và cost phù hợp |
| AES-GCM nonce/IV | Quy trình bảo đảm **không tái sử dụng cặp khóa–nonce** | Nonce có thể sinh ngẫu nhiên 12 byte trong giới hạn cho phép hoặc dùng counter được quản lý |
| OTP 6 chữ số | `SecureRandom.nextInt(1_000_000)` | Chỉ 1 triệu khả năng: cần TTL, rate limit, chống replay, khóa thử |
| Simulation/game/test | `Random(seed)` nếu cần lặp lại | Không yêu cầu secrecy |
| Parallel simulation/backoff | `ThreadLocalRandom.current()` | Tối ưu contention, không dùng cho secret |
| ID công khai | Chiến lược ID đáp ứng uniqueness, collision và quy mô | Public ID không tự mang quyền; không lấy bí mật của ID thay authorization |

### 4.1. Token dài không đồng nghĩa entropy cao

32 byte ngẫu nhiên lý tưởng = 256 bit dữ liệu, khi encode **Base64URL không padding** sẽ có 43 ký tự. **43 ký tự không tạo 43 × 6 bit entropy mới**. Nếu seed yếu hoặc token bị rò qua log, URL, cookie/storage hoặc analytics, string dài cũng không cứu được.

Token thực tế phải dùng HTTPS; thường cần thời hạn, bảo vệ trước replay và thu hồi. Trong hệ thống kiểm tra bearer token, bảo quản/lưu token an toàn (có thể lưu dạng digest phù hợp để giảm rủi ro lộ plaintext token trên server) và giới hạn thử khi cần. Không xem token chỉ là một “random integer”.

### 4.2. Salt và nonce khác secret key

**Salt**: tạo khác nhau giữa các tài khoản/lần dẫn xuất để chống bảng tính sẵn và tránh tình trạng hai user có cùng password tạo cùng hash với cùng tham số. Salt không cần bí mật; có thể lưu cạnh password hash. Nhưng không tự viết `SHA256(password + salt)` để thay KDF mạnh.

**Nonce/IV**: ràng buộc phụ thuộc thuật toán. Với AES-GCM, nonce 96 bit được dùng phổ biến; dữ liệu mã hóa cần nonce **duy nhất dưới cùng khóa**. Nếu sinh bằng 12 byte `SecureRandom`, vẫn có xác suất trùng khi số lượt tăng; phải kiểm giới hạn sử dụng hoặc dùng cơ chế quản lý nonce phù hợp với lượng dữ liệu. **`SecureRandom` không phải chứng minh nonce tuyệt đối không lặp**.

**Khóa**: bí mật, có độ mạnh theo thuật toán và được lưu/rotation đúng chính sách. Không biến timestamp hoặc mật khẩu người dùng thành raw AES key bằng hash rồi xem như có 256 bit entropy. Nếu đầu vào là password, dùng password-based KDF chuẩn và salt/cost.

### 4.3. Vì sao `nextInt(bound)` tốt hơn tự lấy modulo?

Khi lấy số giới hạn, dùng `secure.nextInt(1_000_000)` để lấy trong khoảng 0..999999. Tránh `secure.nextInt() % 1_000_000` vì có thể âm và phân bố lệch. Cách lấy range của JDK xử lý vấn đề chọn giá trị bounded theo API.

Ví dụ OTP sáu chữ số:

```java
import java.security.SecureRandom;
import java.util.Locale;

SecureRandom secure = new SecureRandom();
int number = secure.nextInt(1_000_000);
String otp = String.format(Locale.ROOT, "%06d", number);
```

Đây là **đoạn minh họa trong một method**, không phải chương trình đầy đủ. OTP có thể bắt đầu bằng `0`; cần giữ định dạng chuỗi và bổ sung TTL, số lần thử tối đa, một lần sử dụng.

## 5. Chọn đúng API trong các tình huống thường gặp

**A. Kiểm thử Monte Carlo phải tái lập.** Dùng `Random` với seed cố định và điều khiển thứ tự lời gọi; không dùng `SecureRandom` chỉ để làm test kết quả bất định khó tái hiện. Với simulation hiện đại có thể xét các PRNG khác của `java.util.random` (Java 17), nhưng **interface `RandomGenerator` không tự đảm bảo an toàn mật mã**.

**B. Nhiều worker xử lý sampling không bảo mật.** Dùng `ThreadLocalRandom.current()` ngay trong worker để giảm contention. Không ép `ThreadLocalRandom.setSeed`; nếu test song song cần lặp lại chính xác, thiết kế luồng PRNG có seed/stream phân chia rõ ràng.

**C. API server phát reset token.** Dùng `SecureRandom.nextBytes` với độ dài đủ lớn; encode truyền tải; lưu và so sánh an toàn; hết hạn sau khoảng thời gian phù hợp, dùng một lần và tránh log token.

**D. Sinh IV khi dùng AES-GCM.** Có thể dùng `SecureRandom` 12 byte trong chính sách giới hạn số bản mã; thiết kế chống reuse với cùng khóa. Randomness không thay thế invariant uniqueness.

**E. Phát mã PIN/OTP sáu chữ số.** Chọn `SecureRandom`, nhưng vì số lượng khả năng hữu hạn nhỏ cần rate limit, TTL, retry count, lockout/risk controls và chống replay. Không coi generator an toàn là đủ.

**F. Sinh UUID.** `UUID.randomUUID()` của Java dùng nguồn ngẫu nhiên mạnh cho UUID v4, có 122 bit ngẫu nhiên sau các bit version/variant. Nó phù hợp nhiều nhu cầu ID nhưng không mặc nhiên đáp ứng yêu cầu token 256 bit hoặc thay cơ chế phân quyền.

## 6. Chương trình Java 17+ chạy độc lập

Tạo file **`EntropyRandomDemo.java`**. Chương trình minh họa ba API, đồng thời **chỉ in kết quả có thể xác minh ổn định**, không in token/salt bí mật.

```java
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class EntropyRandomDemo {
    private static final SecureRandom SECURE = new SecureRandom();

    private EntropyRandomDemo() {
        // Utility class for the example.
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        SECURE.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public static void main(String[] args) {
        // Same Random seed and calls -> reproducible sequence.
        Random first = new Random(2026L);
        Random second = new Random(2026L);
        boolean sameSequence = true;
        for (int i = 0; i < 10; i++) {
            if (first.nextInt(1_000) != second.nextInt(1_000)) {
                sameSequence = false;
            }
        }

        // Efficient per-thread generator for non-security tasks.
        int sample = ThreadLocalRandom.current().nextInt(10, 20);
        boolean validRange = sample >= 10 && sample < 20;

        // Cryptographic generator for sensitive random bytes.
        byte[] salt = new byte[16];
        SECURE.nextBytes(salt);
        String token = newToken();
        byte[] decoded = Base64.getUrlDecoder().decode(token);

        System.out.println("randomRepeatable=" + sameSequence);
        System.out.println("threadLocalRangeValid=" + validRange);
        System.out.println("saltBytes=" + salt.length);
        System.out.println("tokenBytes=" + decoded.length);
        System.out.println("tokenChars=" + token.length());
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```shell
javac EntropyRandomDemo.java
java EntropyRandomDemo
```

Stdout kỳ vọng:

```text
randomRepeatable=true
threadLocalRangeValid=true
saltBytes=16
tokenBytes=32
tokenChars=43
```

Giải thích:

- `randomRepeatable=true` vì hai `Random` cùng seed và được gọi cùng chuỗi lệnh; không có giá trị int cụ thể được hard-code vào bài.
- `threadLocalRangeValid=true` vì `nextInt(10,20)` trả số trong `[10,20)`; chương trình không yêu cầu kết quả của generator là cố định.
- Salt thực tế dài 16 byte, token gốc dài 32 byte; Base64URL 32 byte không padding tạo chuỗi 43 ký tự.
- `SecureRandom` sinh byte mới mà không có caller-supplied seed. Không in byte bí mật và không dùng các kết quả ngẫu nhiên làm stdout cố định.
- Ví dụ mới mô tả cách tạo dữ liệu; nó chưa bao gồm password hashing, AES-GCM encryption hay hệ thống xác thực hoàn chỉnh.

## 7. Kết hợp `SecureRandom` với API mật mã chuẩn

Khi cần **khóa AES**, ưu tiên `KeyGenerator` của Java Cryptography Architecture thay vì tự phát minh cách ghép số:

```java
import java.security.SecureRandom;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

SecureRandom secure = new SecureRandom();
KeyGenerator generator = KeyGenerator.getInstance("AES");
generator.init(256, secure);
SecretKey key = generator.generateKey();
```

Đoạn này **chưa độc lập**, cần đặt trong method có `throws Exception` hoặc xử lý checked exception và nhập các class đã nêu. Java 17 chuẩn hỗ trợ khóa AES 256 bit; provider/policy cụ thể vẫn có thể áp dụng điều kiện khác. Không xuất khóa ra log và phải có nơi lưu/rotation an toàn.

Với AES-GCM, có thể tạo nonce ví dụ:

```java
byte[] nonce = new byte[12];
secure.nextBytes(nonce);
// Chỉ dùng nonce một lần dưới cùng key khi mã hóa AES-GCM.
```

Dòng trên dùng biến `secure` của đoạn trước. Việc quản lý uniqueness, giới hạn số lượt và bảo vệ key không được thay bằng suy luận “nonce random nên chắc chắn không bao giờ trùng”.

Lưu ý: `SecureRandom` là nguồn dữ liệu; `KeyGenerator`, `Cipher`, `Mac`, `Signature` là các engine dùng dữ liệu đó theo giao thức/thuật toán tương ứng. Không dùng một generator thường cho các security engine cần độ khó đoán.

## 8. Vấn đề vận hành: provider, blocking và quan sát đúng

Để biết runtime sử dụng gì, có thể tạo file độc lập **`SecureRandomInfo.java`**:

```java
import java.security.SecureRandom;
import java.security.Security;

public class SecureRandomInfo {
    public static void main(String[] args) {
        SecureRandom rng = new SecureRandom();
        System.out.println("Algorithm: " + rng.getAlgorithm());
        System.out.println("Provider: " + rng.getProvider().getName());
        System.out.println("Strong candidates: " +
                Security.getProperty("securerandom.strongAlgorithms"));
    }
}
```

Chạy:

```shell
javac SecureRandomInfo.java
java SecureRandomInfo
```

**Stdout thay đổi theo JDK, hệ điều hành và cấu hình provider**, nên không bịa một tên thuật toán/provider cố định. Hãy kiểm việc sử dụng JDK được cập nhật, chính sách entropy, provider, yêu cầu compliance và latency/throughput đo trên môi trường thật.

Với container/VM snapshot, deployment nhiều bản sao và quy định HSM, cần threat model đối với nguồn entropy và trạng thái generator. Không tự thay bằng seed là container ID, hostname hoặc timestamp. Provider có thể giới hạn thuật toán, kiểm soát reseed và locking; đánh giá chúng dựa trên tài liệu và đo đạc, không dựa vào giả định rằng `getInstanceStrong()` luôn tốt hơn.

## 9. Lỗi thường gặp cần tránh

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `new Random(System.currentTimeMillis())` để làm reset token | Seed/time dễ suy đoán, thuật toán không chống dự đoán | Dùng `SecureRandom` |
| `Math.random()` rồi chuyển thành hex để làm API key | Không có cam kết cryptographic security | Sinh đủ random bytes bằng CSPRNG và encode |
| `ThreadLocalRandom` để tạo session ID | Per-thread và nhanh không đồng nghĩa an toàn | Dùng `SecureRandom`/framework bảo mật |
| `SecureRandom.setSeed(42L)` trước `nextBytes` | Seed yếu, có thể ngăn self-seeding | Để provider tự seed |
| `new SecureRandom(new byte[]{1,2,3})` | Seed biết trước, không đủ entropy | Dùng constructor mặc định và nguồn entropy chuẩn |
| Dùng `getInstanceStrong()` cho từng request | Có thể overhead/blocking, không chứng minh tốt hơn | Chọn provider theo chính sách và đo |
| Tự áp `x % n` | Có thể làm lệch phân bố và có kết quả âm | Dùng `nextInt(n)` với `n > 0` |
| Cắt token 256 bit còn vài ký tự | Làm giảm không gian đoán | Giữ đủ bytes/bit theo thiết kế |
| Kiểm “hai token bất kỳ phải khác” như một phép chứng minh entropy | Va chạm có xác suất > 0, test ngắn không chứng minh bảo mật | Test shape/contract và đánh giá RNG bằng phương pháp phù hợp |
| Dùng random nonce AES-GCM vô hạn với cùng khóa | Xác suất collision tăng theo số lần mã hóa | Quản lý nonce duy nhất và giới hạn/rotation khóa |
| Log token, key, seed hoặc salt kèm mật khẩu | Lộ dữ liệu nhạy cảm, vô hiệu hóa tính khó đoán | Log metadata tối thiểu, bảo vệ credential |
| Coi public ID như token phân quyền | ID đoán được không có nghĩa người đoán được quyền | Có authorization độc lập; token phải khó đoán nếu là bearer |

### Cách test đúng

Với `Random(seed)` dùng trong simulation, test có thể assert output sequence và seed ổn định. Với `SecureRandom`, nên kiểm contract: kích thước raw byte, Base64URL roundtrip, loại ký tự, quy trình hết hạn/thu hồi và bảo vệ secret. Không assert một token cố định và không coi vài phép đếm bit là chứng minh chất lượng nguồn entropy.

Nếu cần reproducible test cho logic phát token, thiết kế interface/adapter đầu vào cho test, nhưng đường production phải dùng `SecureRandom` được khởi tạo an toàn. Đặc biệt không đưa “seed test” vào production qua biến cấu hình nhầm lẫn.

## 10. Tổng kết thành quyết định thực hành

- **Cần ngẫu nhiên để mô phỏng và tái lập?** `Random(seed)`.
- **Cần tốc độ và ít contention trong xử lý song song không bảo mật?** `ThreadLocalRandom.current()`.
- **Cần ngẫu nhiên mà người khác không đoán được?** `SecureRandom` hoặc API mật mã tiêu chuẩn dùng CSPRNG.
- **Cần khóa, salt, token, nonce?** Chọn đúng primitive và ràng buộc cụ thể: khóa phải bí mật, salt nên riêng từng bản ghi, token cần TTL/anti-replay, nonce cần uniqueness theo thuật toán.
- **Cần tăng “entropy”?** Xem **nguồn**, seed và cách provider khởi tạo; không băm timestamp, không thêm Base64, không gọi `setSeed` bằng hằng số.
- **Cần đo độ mạnh?** Dùng threat model, bảo đảm nguồn entropy và thuật toán; kiểm tra thống kê trên output không đủ để chứng minh an toàn mật mã.

Bốn câu hỏi tự kiểm tra:

1. Vì sao `new Random(123L)` cho chuỗi giống nhau sau mỗi lần chạy, và tại sao điều đó tốt cho test nhưng xấu cho token? **Đáp:** Thuật toán xác định theo seed; tái lập test có lợi, nhưng kẻ tấn công có thể dự đoán output.
2. Một OTP sáu chữ số sinh bởi `SecureRandom` đã an toàn tuyệt đối chưa? **Đáp:** Chưa, vì chỉ có một triệu khả năng; phải giới hạn thử, thời hạn, chống replay và bảo vệ kênh.
3. Vì sao `SecureRandom.setSeed(System.nanoTime())` trước lần `nextBytes` là nguy hiểm? **Đáp:** Thời gian dễ suy đoán và có thể ngăn cơ chế tự lấy seed phù hợp.
4. Vì sao dùng `ThreadLocalRandom` trong 100 thread không tương đương có 100 CSPRNG? **Đáp:** Thiết kế per-thread giải quyết contention, không tạo cam kết chống dự đoán mật mã.

## Nguồn tham khảo

- [Java SE 17 – SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): self-seeding, `setSeed`, `reseed`, `getInstanceStrong`, thread safety và khả năng blocking.
- [Java SE 17 – Random](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html): seed 48 bit, LCG, tái lập và cảnh báo không bảo mật.
- [Java SE 17 – ThreadLocalRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadLocalRandom.html): per-thread, `setSeed` không hỗ trợ và trường hợp sử dụng.
- [Java SE 17 – RandomGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/RandomGenerator.html): giao diện chung từ Java 17; một implementation không mặc nhiên là CSPRNG.
- [NIST – Full Entropy Assumption of SP 800-90](https://www.nist.gov/publications/discussion-full-entropy-assumption-sp-800-90-series): vai trò của entropy và min-entropy trong việc đánh giá bộ sinh số ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-randomness-005">3.5 Tạo byte ngẫu nhiên, seed, reseed và tác động của seed có thể dự đoán</a>

<details>
<summary>Xem chi tiết</summary>

# 3.5. Tạo byte ngẫu nhiên, seed, reseed và tác động của seed có thể dự đoán

Trong module **Java Advance – Security & Cryptography**, “ngẫu nhiên” thường là thành phần an ninh: token phiên đăng nhập, mã khôi phục, khóa, salt, nonce/IV hoặc challenge. Một chương trình có thể tạo đủ 32 byte và trông rất ngẫu nhiên, nhưng nếu trạng thái ban đầu đến từ **seed có thể đoán**, đối thủ có thể thu hẹp số khả năng đến mức dò được. Bài học dùng **Java 21** để phân biệt nguồn entropy, trạng thái PRNG, seed ban đầu và reseed, sau đó thực hành `SecureRandom` đúng cách.

## Mục tiêu

Sau bài này, bạn có thể:

1. Tạo các mảng byte khó dự đoán bằng `java.security.SecureRandom` và mã hóa chúng thành chuỗi khi cần.
2. Giải thích **entropy → seed/state → quá trình sinh byte → reseed** mà không nhầm “độ dài output” với “độ bí mật thực”.
3. Hiểu khi nào `SecureRandom` tự seed, tác động của `SecureRandom(byte[])`, `setSeed(...)`, `generateSeed(...)` và `reseed()`.
4. Chỉ ra vì sao timestamp, số tuần tự, PID, username, seed hard-code hay seed quá nhỏ đều nguy hiểm nếu được dùng làm nguồn duy nhất cho bí mật.
5. Nhận diện giới hạn provider, dùng DRBG trong Java 21 khi cần chính sách reseed rõ ràng, và phân biệt random **token**, **salt**, **nonce** với sinh **khóa**.
6. Kiểm thử đúng: xác minh độ dài, định dạng, khả năng vận hành và nguồn entropy; không đánh đồng vài mẫu “khác nhau” với bằng chứng an toàn mật mã.

## 1. Bản chất của random trong ứng dụng bảo mật

Một máy tính thực hiện nhiều phép tính hoàn toàn xác định. Vì vậy, nhiều bộ sinh số ngẫu nhiên được xây dựng dưới dạng **pseudorandom generator**: thuật toán mở rộng một trạng thái ban đầu thành chuỗi byte trông ngẫu nhiên. Nếu biết đủ trạng thái và thuật toán, có thể tái tạo chuỗi đó; mức an toàn phụ thuộc trước hết vào **độ khó đoán trạng thái ban đầu** và chất lượng bộ sinh.

Sơ đồ khái niệm:

```text
Nguồn entropy của hệ điều hành / thiết bị
                    |
             Seed ban đầu đủ mạnh
                    |
        PRNG/DRBG có thiết kế mật mã
                    |
         nextBytes(...) -> byte[]
                    |
       Reseed định kỳ/khi chính sách yêu cầu
       bằng entropy mới (nếu được hỗ trợ)
```

**Khái niệm cần phân biệt:**

| Khái niệm | Ý nghĩa | Điều cần nhớ |
| --- | --- | --- |
| Random bytes | Mảng byte đầu ra từ bộ sinh | Byte ngẫu nhiên có thể chứa `0x00`, byte âm khi đọc kiểu `byte` có dấu; không phải chuỗi UTF-8. |
| Seed | Dữ liệu khởi tạo hoặc bổ sung trạng thái của PRNG | Độ dài seed không tự nói lên entropy của nó. |
| Entropy | Mức bất định của thông tin đối với đối thủ | Một giá trị có 256 bit biểu diễn vẫn có thể chỉ có vài bit khó đoán. |
| CSPRNG | Bộ sinh giả ngẫu nhiên phù hợp mật mã | Kết quả phải khó dự đoán dù đối thủ biết thuật toán. |
| DRBG | Một lớp CSPRNG dựa trên thuật toán xác định và entropy ban đầu | Có thể hỗ trợ giới hạn số lần sinh, reseed, prediction resistance. |
| Reseed | Làm mới/bổ sung trạng thái từ nguồn entropy mới theo cơ chế provider | Không phải cùng thao tác với việc cố định seed để tái lập output. |
| Token/salt/nonce | Các giá trị có yêu cầu khác nhau | Token cần khó đoán; salt thường cần độc nhất và đủ ngẫu nhiên; nonce của AEAD yêu cầu **không tái sử dụng cùng khóa**. |

Ví dụ giới hạn entropy: nếu seed chỉ là một trong **1.000 giá trị timestamp** mà đối thủ có thể khoanh vùng, không thể xem nó là “256 bit bí mật” chỉ vì chương trình băm timestamp bằng SHA-256 rồi lấy 32 byte. Một giá trị được tính xác định từ 1.000 khả năng vẫn chỉ có tối đa 1.000 trạng thái, tương ứng dưới 10 bit bất định. **Hash hay PRNG không tạo thêm entropy bí mật từ đầu vào có thể đoán.**

## 2. Chọn API đúng trước khi bàn tới seed

Nếu dữ liệu không có yêu cầu bảo mật, `Random(seed)` hữu ích để tái lập phép thử, `ThreadLocalRandom.current()` giảm tranh chấp giữa các thread và `SplittableRandom` phục vụ tính toán/mô phỏng. Các API đó **không bảo đảm chống dự đoán**; `Math.random()` cũng không thích hợp cho token hay khóa.

Khi cần token, salt, nonce hoặc vật liệu bí mật, dùng `java.security.SecureRandom` đã được cấu hình phù hợp; với **khóa** nên ưu tiên `KeyGenerator`/`KeyPairGenerator` theo thuật toán. Mẫu sử dụng thông thường là:

```java
SecureRandom random = new SecureRandom();
byte[] bytes = new byte[32];
random.nextBytes(bytes);
```

Đoạn minh họa cần `import java.security.SecureRandom;`. Trọng tâm còn lại của bài này không phải so sánh lại cả họ bộ sinh, mà là giải thích **self-seeding, tác động của seed do caller cung cấp, và reseed đúng khi provider hỗ trợ**.

## 3. Thực hành đầy đủ: tạo 32 byte cho token

Tạo file **`SecureBytesDemo.java`**:

```java
import java.security.SecureRandom;
import java.util.Base64;

public class SecureBytesDemo {
    public static void main(String[] args) {
        SecureRandom random = new SecureRandom();

        byte[] token = new byte[32];
        random.nextBytes(token);

        // Chuyển byte nhị phân sang chuỗi phù hợp URL/cookie.
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(token);

        // Chỉ in kích thước: không ghi token bí mật vào log.
        System.out.println("Byte count: " + token.length);
        System.out.println("Bit count: " + token.length * 8);
        System.out.println("Base64url chars: " + encoded.length());
    }
}
```

Kiểm tra JDK 21 bằng `java -version` và `javac -version`; trong thư mục chứa file, chạy:

```text
mkdir out
javac --release 21 -encoding UTF-8 -d out SecureBytesDemo.java
java -cp out SecureBytesDemo
```

Kết quả với ứng dụng trên:

```text
Byte count: 32
Bit count: 256
Base64url chars: 43
```

**Vì sao?** `nextBytes(byte[])` điền 32 byte nhị phân. 32 × 8 = 256 **bit dữ liệu đầu ra**; mức an toàn thực còn phụ thuộc seed và provider. Base64url bỏ padding mã hóa đúng 32 byte thành 43 ký tự; bản thân Base64 **không mã hóa bí mật**, chỉ chuyển biểu diễn. Giá trị token khác nhau giữa các lần chạy và không có một stdout token cố định.

**Không làm các việc sau:**

- Không tạo `new String(token, UTF_8)` để “chuyển byte ngẫu nhiên thành text”: nhiều chuỗi byte không phải UTF-8 hợp lệ và có thể bị thay thế byte khi giải mã.
- Không công khai token thực trong log, exception, metric label, URL không bảo vệ hoặc ảnh chụp debug.
- Không dùng `Random.nextBytes` hay `UUID.randomUUID()` để thay mọi loại secret theo cảm tính. UUID phiên bản 4 có định dạng và lượng bit ngẫu nhiên khác token 32 byte; chọn token theo mô hình đe dọa, độ dài, cách lưu và giao thức.
- Không dùng việc in ra “hai token khác nhau” làm bài kiểm định mật mã. Một nguồn yếu cũng có thể tạo các chuỗi nhìn rất khác nhau.

Một đối tượng `SecureRandom` có thể được **tái sử dụng**; Java 21 tài liệu hóa rằng API này an toàn khi gọi từ nhiều thread (provider được dùng trực tiếp nếu khai báo thread-safe, hoặc lớp bao đồng bộ truy cập engine). Tuy vậy, hiệu năng và khả năng chặn khi chờ entropy vẫn phụ thuộc implementation; cần đo trong môi trường triển khai nếu tải lớn.

## 4. Seed ban đầu được tạo như thế nào?

Đối với một `SecureRandom` sử dụng PRNG, Javadoc Java 21 mô tả:

- Tạo bằng `new SecureRandom()` hoặc `SecureRandom.getInstance(...)` thường **chưa lập tức lấy đủ seed**. Lần gọi `nextBytes(...)` đầu tiên sẽ khiến PRNG tự seed từ nguồn entropy do implementation xác định, **trừ khi trước đó đã gọi `setSeed(...)`**.
- `new SecureRandom(byte[] seed)` tạo đối tượng với seed do người gọi cung cấp. Nếu seed đó là nguồn duy nhất và có thể đoán, tính khó dự đoán của output có thể bị phá vỡ. Hành vi chi tiết phụ thuộc provider.
- `random.setSeed(byte[])` hoặc `random.setSeed(long)` cung cấp seed đầu vào từ caller. Theo tài liệu Java, với trạng thái đã tồn tại, seed mới **bổ sung** chứ không thay thế seed trước đó. Tuy nhiên, nếu gọi trước lần tự seed đầu tiên, không thể dựa vào cơ chế tự lấy entropy mặc định.
- `setSeed(long)` chỉ nhận một số `long` 64 bit; việc truyền số thời gian, bộ đếm, PID hoặc hằng số càng làm số trạng thái tiềm năng bị giới hạn.

### So sánh bốn kiểu khởi tạo

| Mẫu | Đánh giá | Vì sao |
| --- | --- | --- |
| `new SecureRandom(); random.nextBytes(out);` | **Khuyến nghị mặc định** | Provider tự dùng nguồn entropy phù hợp. |
| `SecureRandom.getInstance("DRBG"); ...` | Hợp lý nếu runtime có DRBG và bạn cần cấu hình/kiểm tra DRBG | Cần xác minh service/provider. |
| `new SecureRandom("known".getBytes(UTF_8));` | **Nguy hiểm** nếu seed đoán được là nguồn entropy duy nhất | Seed đã biết hoặc có không gian tìm kiếm nhỏ. |
| `SecureRandom r = new SecureRandom(); r.setSeed(System.currentTimeMillis()); r.nextBytes(out);` | **Tránh sử dụng** | Timestamp dự đoán được; gọi `setSeed` trước khi tự seed có thể bỏ qua bước lấy entropy mặc định. |

Một seed dài 32 byte lấy từ chuỗi lặp/zero vẫn yếu. Ngược lại, sau khi provider **đã tự seed an toàn**, `setSeed(123)` không có nghĩa toàn bộ trạng thái chắc chắn trở nên đoán được: phương thức bổ sung vào trạng thái hiện hữu. Nhưng việc này **không đem lại lợi ích bảo mật có thể tin cậy** và gây nhầm lẫn trong audit; nên tránh seed thủ công nếu không có lý do đã được thiết kế và đánh giá.

**Điểm nhạy cảm về thứ tự:** `new SecureRandom(); setSeed(weak); nextBytes(out)` khác với `new SecureRandom(); nextBytes(out); setSeed(weak)` về thời điểm self-seeding. Không suy diễn rằng mọi provider đều có cùng thuật toán hoặc cùng kết quả byte cho một seed, nhưng **không giao an ninh của secret cho nguồn seed có thể dự đoán**.

### Phân biệt `nextBytes`, `generateSeed` và `getSeed`

| Phương thức | Nguồn và mục đích |
| --- | --- |
| `nextBytes(byte[])` | Lấy byte random từ bộ sinh hiện tại để dùng trong ứng dụng. Đây là lựa chọn thường dùng. |
| `generateSeed(int)` | Lấy byte seed theo seed-generation algorithm của implementation; hữu ích để seed một bộ sinh **khác** khi có yêu cầu rõ ràng. Có thể tốn tài nguyên hoặc chặn. |
| `SecureRandom.getSeed(int)` | Phương thức static nhận byte seed theo cơ chế seed generation; hiếm khi cần cho ứng dụng thông thường. |
| `setSeed(byte[])` / `setSeed(long)` | Caller đưa entropy đầu vào; **không bắt buộc** khi dùng `new SecureRandom()`. |
| `reseed()` / `reseed(params)` | Yêu cầu provider lấy entropy mới để reseed; **có thể không được hỗ trợ**. |

Không cần và không nên theo một “nghi thức” kiểu `generateSeed(32)` → `setSeed(...)` → `nextBytes(...)` mỗi khi lấy token. Với cấu hình thông thường, `nextBytes` đã đáp ứng nhu cầu và provider có chính sách lấy seed thích hợp.

## 5. Minh họa seed đoán được bằng bộ sinh xác định

Ví dụ dưới đây **cố ý sử dụng `java.util.Random` không an toàn cho mật mã** để có output được Java bảo đảm tái lập. Mục đích là nhìn thấy tác động của **cùng seed**; nó **không phải mã tạo token sản xuất**.

Tạo file **`RepeatableSeedDemo.java`**:

```java
import java.util.Arrays;
import java.util.Random;

public class RepeatableSeedDemo {
    public static void main(String[] args) {
        byte[] first = new byte[16];
        byte[] second = new byte[16];

        new Random(123L).nextBytes(first);
        new Random(123L).nextBytes(second);

        System.out.println("Same bytes: " + Arrays.equals(first, second));
        System.out.println("Bytes generated: " + first.length);
    }
}
```

Chạy:

```text
javac --release 21 -encoding UTF-8 -d out RepeatableSeedDemo.java
java -cp out RepeatableSeedDemo
```

Stdout chính xác:

```text
Same bytes: true
Bytes generated: 16
```

Hai bộ sinh `Random` độc lập nhưng có **seed 123** giống nhau sẽ phát sinh **cùng chuỗi byte**. Nếu ứng dụng dùng chuỗi đó làm token, người biết seed có thể đoán được token. Ngay cả khi seed “thay đổi mỗi lần” nhưng chỉ chọn trong một khoảng nhỏ, đối thủ có thể thử tất cả seed khả dĩ.

Đối với `SecureRandom`, **không dựa vào giả định “cùng seed luôn cho cùng output” hoặc “cùng seed chắc chắn cho output khác nhau”**: thuật toán, việc bổ sung entropy, thời điểm self-seeding và provider có thể khác. Bài học về bảo mật ở đây là: nếu thiết kế dựa **duy nhất** trên seed có thể đoán và không có entropy thật bổ sung thì đầu ra không có bí mật đủ mạnh, bất kể nhìn như ngẫu nhiên.

### Kịch bản rủi ro thực tế

Giả sử backend làm việc sau để tạo token:

```java
// Ví dụ phản diện: KHÔNG dùng để tạo token bảo mật.
SecureRandom random = new SecureRandom();
random.setSeed(System.currentTimeMillis());
byte[] token = new byte[32];
random.nextBytes(token);
```

- Đối thủ thấy thời điểm tài khoản được tạo/xác thực và có thể ước lượng clock máy chủ trong một khoảng ngắn.
- Nếu seed do timestamp cung cấp là entropy duy nhất, số giá trị có thể thử rất nhỏ so với 2^256; kích thước token 32 byte **không cứu được thiết kế**.
- Việc tái tạo byte cụ thể phụ thuộc provider, trạng thái và thứ tự gọi API; đây là phân tích **nguy cơ**, không phải cam kết mọi provider tự động tạo đúng cùng token từ cùng timestamp.
- Nếu hệ thống chạy nhiều container/VM được clone từ snapshot có cùng trạng thái PRNG và thiếu reseed đúng cách, khả năng tái lập/đụng độ output là một rủi ro vận hành khác. Cần cấu hình entropy tốt, khởi tạo đúng và kiểm thử mô hình clone/restore của nền tảng.

Các seed sai thường gặp: `System.currentTimeMillis()`, `System.nanoTime()`, `Thread.currentThread().getId()`, process ID, username, email, số thứ tự tăng, một chuỗi cố định trong config, hash của các giá trị này. **Băm seed yếu hoặc nối thêm dữ liệu công khai không tự tạo entropy mới**.

## 6. Reseed là gì và khi nào cần?

**Seed lần đầu** giúp PRNG xây dựng trạng thái bí mật. **Reseed** là cập nhật trạng thái sau đó, thường với entropy mới, nhằm tuân thủ vòng đời của DRBG, giảm thời gian phụ thuộc vào một trạng thái và hỗ trợ các yêu cầu về prediction resistance trong những thiết kế có bảo đảm tương ứng.

Trong Java 21:

- `SecureRandom.reseed()` yêu cầu engine lấy entropy từ nguồn của nó. Không phải mọi provider/algorithm đều hỗ trợ; API có thể ném `UnsupportedOperationException`.
- `SecureRandom.reseed(SecureRandomParameters)` cho phép truyền cấu hình cụ thể nếu implementation chấp nhận.
- `SecureRandom.setSeed(...)` nhận input do **caller** cung cấp; không tương đương việc provider tự lấy entropy mới. Một lần `setSeed(0L)` không được coi là reseed an toàn.
- Một số DRBG tự reseed khi vượt ngưỡng tuổi seed/số lần sinh theo cơ chế riêng; không nên giả định tự động hay thủ công trên **mọi** implementation.
- `nextBytes`, `generateSeed` và `reseed` đều **có thể chặn** khi chờ entropy, tùy provider và hệ điều hành.

Đối với ứng dụng Java bình thường, **không cần tự gọi `reseed()` trước mỗi lần tạo token**. Nếu tổ chức có chính sách cụ thể về vòng đời DRBG, thời điểm khôi phục snapshot, thiết bị HSM/FIPS hoặc prediction resistance, hãy chọn provider hỗ trợ, đọc tài liệu cấu hình rồi kiểm thử. Gọi thủ công liên tục có thể làm tăng độ trễ mà không giúp ích theo cách tưởng tượng.

### Trường hợp cần phân biệt

| Tình huống | Cách tiếp cận |
| --- | --- |
| Tạo token đăng nhập trong một dịch vụ Java 21 thông thường | Một `SecureRandom` được khởi tạo mặc định và `nextBytes` thường phù hợp. |
| Dịch vụ chạy nhiều tháng | Xác minh chính sách reseed/entropy của provider nếu yêu cầu an ninh đặc thù; không tự tạo seed bằng timestamp. |
| VM/container phục hồi snapshot | Đánh giá nguy cơ trùng trạng thái, bảo đảm entropy và cơ chế khởi tạo/reseed đúng sau restore. |
| Phải chứng minh reseed/prediction resistance theo chính sách | Sử dụng DRBG/provider có capability tương ứng và kiểm tra `getParameters()`. |
| Unit test cần kết quả lặp lại | Dùng test double hoặc `Random` cố định **chỉ trong kiểm thử phi bảo mật**; production vẫn dùng CSPRNG. |

## 7. Java 21 DRBG và ví dụ kiểm tra khả năng reseed

Java có API `java.security.DrbgParameters` từ Java 9. Java 21 cung cấp cơ chế để yêu cầu thuộc tính DRBG và truy vấn **effective parameters**; thuật toán `"DRBG"` tùy provider. Ba khả năng chính:

- `DrbgParameters.Capability.NONE`: không bảo đảm hỗ trợ reseed/prediction resistance.
- `DrbgParameters.Capability.RESEED_ONLY`: có reseed, không yêu cầu prediction resistance trong mỗi lần sinh.
- `DrbgParameters.Capability.PR_AND_RESEED`: có cả reseed và khả năng prediction resistance.

**Prediction resistance** trong ngữ cảnh DRBG không có nghĩa “gọi `new SecureRandom()` một lần là bất khả xâm phạm”. Đó là thuộc tính/đòi hỏi cụ thể của cơ chế DRBG và nguồn entropy, cần được provider hỗ trợ.

Tạo file **`DrbgReseedDemo.java`**:

```java
import java.security.DrbgParameters;
import java.security.SecureRandom;
import java.security.SecureRandomParameters;

public class DrbgReseedDemo {
    public static void main(String[] args) throws Exception {
        SecureRandom drbg = SecureRandom.getInstance("DRBG");
        SecureRandomParameters params = drbg.getParameters();

        boolean canReseed =
                params instanceof DrbgParameters.Instantiation actual
                && actual.getCapability().supportsReseeding();

        System.out.println("Algorithm: " + drbg.getAlgorithm());
        System.out.println("Reseed supported: " + canReseed);

        if (canReseed) {
            drbg.reseed(); // Provider tự lấy entropy mới.
        }

        byte[] randomBytes = new byte[16];
        drbg.nextBytes(randomBytes);
        System.out.println("Generated: " + randomBytes.length + " bytes");
    }
}
```

Chạy:

```text
javac --release 21 -encoding UTF-8 -d out DrbgReseedDemo.java
java -cp out DrbgReseedDemo
```

Trên một JDK 21 có provider `DRBG`, output có dạng:

```text
Algorithm: DRBG
Reseed supported: true
Generated: 16 bytes
```

Dòng `Reseed supported` **phụ thuộc provider và cấu hình** (cũng có thể là `false`); ví dụ tránh gọi `reseed()` nếu provider không công bố hỗ trợ. Nếu không cài provider cho `DRBG`, `getInstance("DRBG")` có thể ném `NoSuchAlgorithmException`; khi đó hãy kiểm tra danh sách provider hoặc dùng `new SecureRandom()` cho mục tiêu tạo byte ngẫu nhiên thông thường.

### Khi cần yêu cầu capability ngay từ khởi tạo

Đoạn sau là **snippet** để nhúng vào chương trình Java, không phải một file chạy độc lập:

```java
import java.security.DrbgParameters;
import java.security.SecureRandom;

SecureRandom drbg = SecureRandom.getInstance(
        "DRBG",
        DrbgParameters.instantiation(
                128,
                DrbgParameters.Capability.RESEED_ONLY,
                null));
```

Đối số `128` là **security strength yêu cầu tính bằng bit** của DRBG, không phải số byte sẽ lấy. `RESEED_ONLY` đặt yêu cầu tối thiểu về capability; lời gọi có thể thất bại nếu không có implementation đáp ứng. Thực tế phải đọc `drbg.getParameters()` để biết tham số hiệu lực, vì chúng có thể mạnh hơn yêu cầu.

`DrbgParameters.nextBytes(strength, predictionResistance, additionalInput)` và `DrbgParameters.reseed(predictionResistance, additionalInput)` dành cho use case có cấu hình DRBG cụ thể. **Additional input** hoặc **personalization string** không thay thế nguồn entropy bí mật; đừng dùng tên người dùng/public data như “seed bổ sung” rồi cho rằng mức an ninh tăng lên.

## 8. Đặt random bytes vào đúng ngữ cảnh: token, salt, nonce, khóa

Cùng gọi `nextBytes` nhưng từng trường hợp có điều kiện bảo mật riêng.

| Dùng vào việc gì | Yêu cầu chính | Ví dụ điển hình |
| --- | --- | --- |
| Token phiên / reset token | Khó đoán, đủ dài, truyền/lưu an toàn, hạn sử dụng/thu hồi | 32 byte từ CSPRNG → Base64url (43 ký tự). |
| Salt cho password KDF | Độc nhất trên mỗi mật khẩu và đủ ngẫu nhiên; **không cần giữ bí mật** | 16 byte từ CSPRNG; lưu cạnh thông số KDF và password hash. |
| Nonce/IV cho AES-GCM | **Tuyệt đối không lặp với cùng một khóa**; phải theo giới hạn và quy ước của GCM | Thường dùng 12 byte cho GCM; nguồn random tốt giảm xác suất đụng, nhưng không bảo đảm toán học không lặp. |
| Secret key | Đúng thuật toán, kích thước và vòng đời khóa | Ưu tiên `KeyGenerator` hoặc API key management/HSM phù hợp, thường dựa CSPRNG. |
| Mã OTP ngắn | Random đúng phân bố nhưng **không gian rất nhỏ** | Mã 6 chữ số chỉ 1.000.000 khả năng; bắt buộc có TTL, rate limit, chống replay. |
| Mã định danh công khai | Có thể cần tính duy nhất hơn là bí mật | Chọn thiết kế ID theo yêu cầu; không tự suy ID = secret. |

**Nonce ≠ seed ≠ secret key.** Một nonce có thể công khai nhưng lặp nonce trong GCM với cùng khóa có thể phá an toàn. Seed là dữ liệu dùng để lập/trộn trạng thái bộ sinh; token/key là đầu ra phải bảo vệ. Không được tái sử dụng chính seed của một instance như thể nó là nonce duy nhất cho mọi bản mã.

Ví dụ chọn số từ `0` đến `999_999` có giới hạn:

```java
SecureRandom random = new SecureRandom();
int code = random.nextInt(1_000_000);
String sixDigits = String.format(java.util.Locale.ROOT, "%06d", code);
```

`nextInt(bound)` tránh thao tác chia dư thô như `(randomByte & 0xff) % 10`, vốn cho phân bố lệch nếu 256 không chia hết cho 10. Đoạn này chỉ minh họa tạo mã 6 chữ số; không phải một hệ thống OTP an toàn nếu thiếu thời hạn, xác minh server-side, giới hạn số lần thử và chống sử dụng lại.

### Gợi ý sử dụng byte dữ liệu có trách nhiệm

- `byte[]` có thể chứa số âm nếu log dưới dạng giá trị `byte` Java (`-128..127`); đó chỉ là biểu diễn signed byte, không phải “random lỗi”.
- Nếu phải lưu/truyền byte, dùng Base64url/hex đúng định dạng và lưu ý encoding không tăng entropy.
- Giữ secret trong memory tối thiểu, đừng đưa seed/raw key vào log; xóa mảng byte nhạy cảm khi thích hợp và khả thi (`Arrays.fill`), dù việc xóa không được bảo đảm toàn bộ bản sao hoặc bộ nhớ do runtime/GC quản lý.
- Không suy ra rằng `getInstanceStrong()` luôn tốt hơn `new SecureRandom()` trong mọi ứng dụng. Nó chọn thuật toán theo `securerandom.strongAlgorithms` và có thể chặn/tốn tài nguyên, cần benchmark và đánh giá môi trường cụ thể.
- Không cố định tên provider/thuật toán theo máy lập trình khi chưa có yêu cầu tương thích rõ ràng.

## 9. Kiểm tra môi trường và provider trên Java 21

Nên báo cáo **runtime thật**, provider và thuật toán khi xem xét lỗi sản xuất.

Có thể chạy đoạn snippet trong `jshell`:

```java
import java.security.SecureRandom;
SecureRandom random = new SecureRandom();
System.out.println(Runtime.version());
System.out.println(random.getAlgorithm());
System.out.println(random.getProvider().getName());
System.out.println(random.getParameters());
```

Output ở đây **không cố định**: bản JDK, nhà phân phối, security properties, provider cấu hình và hệ điều hành đều có thể ảnh hưởng. `getParameters()` có thể trả về `null` cho provider không công bố effective DRBG parameters. Các phương thức hữu ích khác: `SecureRandom.getInstance("DRBG")` (nếu provider hỗ trợ), `SecureRandom.getInstanceStrong()` (nếu chính sách yêu cầu), `Security.getProviders()` để liệt kê provider.

Khi tự kiểm tra:

1. `java -version`, `javac -version` và `Runtime.version()` phải phù hợp runtime mục tiêu.
2. Nhận diện provider và thuật toán thực tế bằng `getProvider()`/`getAlgorithm()`.
3. Kiểm tra startup/entropy source, policy reseed, giới hạn provider trong container/VM/HSM.
4. Ghi nhận lỗi và metadata không nhạy cảm; **không log seed, random token, private key**.
5. Dùng test tích hợp và security review đúng threat model, đừng coi vài sample output là chứng minh CSPRNG.

## 10. Các lỗi nhận thức và vận hành hay gặp

| Triệu chứng/khẳng định | Sai ở đâu | Cách xử lý |
| --- | --- | --- |
| “Tạo 32 byte nghĩa là có 256 bit entropy” | Output có thể được mở rộng từ seed rất yếu | Kiểm tra nguồn seed/entropy và provider. |
| “Thêm timestamp giúp SecureRandom mạnh hơn” | Timestamp thường dự đoán được; gọi `setSeed` trước `nextBytes` có thể ngăn self-seeding | Bỏ seed tự chế; dùng entropy từ provider. |
| “Cùng seed trên SecureRandom luôn ra cùng byte” | Provider và việc lấy/thêm entropy khác nhau | Không dùng `SecureRandom` làm cách tái lập dữ liệu kiểm thử. |
| “Gọi reseed mỗi request mới bảo mật” | Có thể không hỗ trợ, có thể block, tốn tài nguyên; provider có policy riêng | Chỉ dùng theo yêu cầu thiết kế và tài liệu provider. |
| `UnsupportedOperationException` từ `reseed()` | Provider/engine không hỗ trợ reseed | Dùng capability check khi là DRBG hoặc chọn provider đáp ứng yêu cầu. |
| `NoSuchAlgorithmException: DRBG` | Không có service DRBG trong provider hiện hành | Xem JDK, cấu hình provider, hoặc dùng `new SecureRandom()` cho nhu cầu chung. |
| Nhiều token trùng trong thử nghiệm | Có thể có seed cố định, clone state, lỗi tái sử dụng dữ liệu/caching hoặc bug hệ thống | Kiểm tra quy trình khởi tạo, triển khai, tính duy nhất và dữ liệu lưu; điều tra toàn đường đi. |
| Token dài nhưng vẫn bị brute-force online | Secret bị lộ hoặc logic cho phép thử tùy ý; token ngắn có entropy thấp | Kiểm tra TTL, giới hạn thử, phân quyền, lưu token và mã hóa đường truyền. |
| Token byte bị mất ký tự khi chuyển `new String(bytes)` | Byte tùy ý không phải văn bản UTF-8 hợp lệ | Dùng Base64url/hex để biểu diễn binary. |
| Dùng random IV cho GCM rồi mặc nhiên bảo đảm không trùng | Random chỉ làm xác suất đụng thấp, không biến uniqueness thành tuyệt đối | Thiết kế quản lý nonce, giới hạn số lần mã hóa mỗi khóa và giám sát nguy cơ lặp. |

**Không suy diễn tính bảo mật từ thống kê nhỏ.** Ví dụ hai lần tạo token khác nhau chỉ cho biết hai lần output khác nhau; bộ sinh không an toàn vẫn đạt điều đó. Ngược lại, xác suất trùng hợp tự nhiên của một CSPRNG không bằng 0; một phép thử tính uniqueness riêng lẻ cũng không chứng minh RNG hỏng.

**Không ép đầu ra random thành mật khẩu hoặc key bằng thao tác tùy tiện.** Nếu cần khóa AES, chọn `KeyGenerator.getInstance("AES")` cùng độ dài chính sách cho phép và hệ quản trị khóa phù hợp. Nếu cần hash mật khẩu, dùng password KDF được thiết kế riêng với salt, cost và quản lý tham số, thay vì “sha256(password + random)” tự chế.

## 11. Bài tập thực hành và tiêu chí đánh giá

**Bài 1 – Tạo token:** Chạy `SecureBytesDemo` trên Java 21 nhiều lần. Giải thích vì sao output chỉ in **32/256/43** thay vì in token thật. Nếu thay 32 byte bằng 16 byte, hãy tự tính chiều dài Base64url không padding và kiểm tra lại.

**Bài 2 – Phân tích entropy:** Một hệ thống lấy seed từ giờ phút gần đúng có thể nằm trong 2.000 mili giây khả dĩ. Nếu đó là entropy duy nhất, có tối đa bao nhiêu seed mà đối thủ cần kiểm tra? Vì sao băm seed bằng SHA-256 không làm tăng số trường hợp? *Gợi ý: 2.000 khả năng nhỏ hơn 2^11.*

**Bài 3 – Seed và tái lập:** Chạy `RepeatableSeedDemo`. Thử đổi một trong hai seed từ `123L` sang `124L`, giải thích vì sao output có thể khác. Sau đó nêu lý do không dùng mẫu `Random(seed)` để tạo reset token.

**Bài 4 – Reseed:** Chạy `DrbgReseedDemo`. Ghi lại algorithm, provider, `getParameters()` và kết quả capability; nêu lý do không được gọi `reseed()` bừa bãi nếu không kiểm tra hỗ trợ.

**Bài 5 – Review một thiết kế:** Cho ba dòng `SecureRandom r = new SecureRandom(); r.setSeed(System.currentTimeMillis()); r.nextBytes(token);`, hãy mô tả khi nào initial self-seeding bị thay thế, rủi ro timestamp và phiên bản sửa đúng bằng `new SecureRandom()` + `nextBytes(token)`. Đừng khẳng định mọi provider sinh cùng byte với cùng timestamp.

**Bài 6 – Chọn random cho mục đích:** Phân biệt một token bí mật 32 byte, salt 16 byte, nonce AES-GCM 12 byte và OTP 6 chữ số. Với mỗi loại, cho biết điều kiện về bí mật, tính duy nhất, chống replay/rate-limit và lưu trữ.

Hoàn thành bài khi có thể trả lời chính xác: **“Byte được tạo từ đâu? Seed ban đầu có khó đoán không? Khi nào provider tự seed hoặc reseed? Tôi đang dùng provider/DRBG nào và loại dữ liệu này đòi hỏi tính bí mật hay tính duy nhất?”**

## Tài liệu tham khảo chính thức

- [Java SE 21: `SecureRandom` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html) — constructor, `nextBytes`, `setSeed`, `generateSeed`, `reseed` và provider.
- [Java SE 21: `DrbgParameters` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/DrbgParameters.html) — reseed/prediction-resistance capability và ví dụ sử dụng.
- [Java SE 21: `SecureRandomParameters`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandomParameters.html).
- [JDK 21: Java Cryptography Architecture (JCA) Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html) — RNG/provider và entropy.
- [JDK 21: JDK Providers Documentation](https://docs.oracle.com/en/java/javase/21/security/oracle-providers.html) — các thuật toán/provider đi kèm Oracle JDK.
- [JDK 21: Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html) — tên thuật toán và service bảo mật.
- [NIST SP 800-90A Revision 1](https://csrc.nist.gov/pubs/sp/800/90/a/r1/final) — các mô hình DRBG và reseed.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-randomness-006">3.6 getInstanceStrong, DRBG và khả năng chặn của nguồn ngẫu nhiên</a>

<details>
<summary>Xem chi tiết</summary>

# 3.6 getInstanceStrong, DRBG và khả năng chặn của nguồn ngẫu nhiên

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt **entropy source**, **seed**, **CSPRNG/DRBG** và API `SecureRandom`; giải thích vì sao thuật toán sinh chuỗi giả ngẫu nhiên vẫn bảo mật khi được khởi tạo đúng.
2. Hiểu `SecureRandom.getInstanceStrong()` thực sự lựa chọn gì thông qua `securerandom.strongAlgorithms` và vì sao chữ “strong” không đồng nghĩa “không chặn” hoặc “phù hợp mọi tác vụ”.
3. Sử dụng `SecureRandom.getInstance("DRBG", DrbgParameters.instantiation(...))`, đọc strength/capability hiệu lực, phân biệt reseed với prediction resistance.
4. Xác định những lời gọi có thể **block** khi cần entropy, đo thời gian tạo và sinh byte đúng cách, lựa chọn giải pháp phù hợp cho hệ thống có yêu cầu latency.
5. Tránh các lỗi bảo mật thường gặp: seed dự đoán được, dùng `Random` cho token, cấu hình provider theo giả định, yêu cầu reseed/strong ở mọi request.

## 1. Nền tảng tối thiểu: entropy source, seed và DRBG

`SecureRandom` là engine API của JCA; provider quyết định cách triển khai và lấy entropy. **Entropy source** cung cấp tính bất định từ hệ điều hành/phần cứng; **seed/trạng thái** là bí mật khởi tạo; **CSPRNG/DRBG** mở rộng trạng thái bằng thuật toán mật mã thành byte khó dự đoán. “Deterministic” của DRBG mô tả phép sinh từ trạng thái, **không** đồng nghĩa đối thủ có thể đoán được trạng thái.

```text
Entropy source -> instantiate/reseed -> DRBG state -> nextBytes(...)
```

Sinh 32 byte không đồng nghĩa bảo đảm security strength 256 bit. `nextBytes` phát sinh byte dùng trong ứng dụng; `generateSeed` tạo seed material; `reseed` có thể bổ sung entropy từ provider; `setSeed` nhận dữ liệu caller và **không tạo entropy mới** từ timestamp hay PID. Trong bài này, trọng tâm riêng là **chọn strong/DRBG theo policy, đọc capability hiệu lực và đo được độ trễ do provider/entropy source**.

## 2. Ba cách lấy `SecureRandom` và ý nghĩa thực tế

| Cách lấy instance | Ai chọn triển khai? | Khi nên dùng | Điểm cần kiểm tra |
| --- | --- | --- | --- |
| `new SecureRandom()` | Provider mặc định theo cấu hình runtime | Hầu hết nhu cầu sinh byte ngẫu nhiên bảo mật trong ứng dụng | Thuật toán/provider thực tế và đặc tính latency trên môi trường đích |
| `SecureRandom.getInstanceStrong()` | Danh sách `securerandom.strongAlgorithms` trong **security properties** | Yêu cầu chính sách/ứng dụng cần triển khai được nền tảng đánh dấu strong | Thuật toán, provider, entropy source, startup/first-call latency và khả năng chặn |
| `SecureRandom.getInstance("DRBG", params)` | JCA tìm provider hỗ trợ service `SecureRandom.DRBG` và tham số tối thiểu | Cần khai báo strength, reseed hoặc prediction resistance có chủ đích | Runtime có provider DRBG không, capability thực tế có đáp ứng không |

Cả ba đều đưa về **API `SecureRandom`**. Sự khác biệt nằm ở cơ chế chọn implementation và các ràng buộc bổ sung; không nên mặc định rằng `getInstanceStrong()` mạnh hơn mọi DRBG, hoặc một DRBG nhất thiết không bao giờ block.

### 2.1 `getInstanceStrong()` không phải tên thuật toán

`getInstanceStrong()` (có từ Java 8) tra cứu property:

```java
String configured = java.security.Security.getProperty(
        "securerandom.strongAlgorithms");
```

Đoạn trên là **biểu thức minh họa** để dùng trong một phương thức Java; `Security.getProperty` đọc **security property**, không phải `System.getProperty` thông thường.

Property `securerandom.strongAlgorithms` chứa danh sách những thuật toán/provider mà bản phân phối Java xác định là phù hợp để sinh giá trị bảo mật quan trọng, ví dụ khóa tồn tại lâu dài. `getInstanceStrong()` dựa vào danh sách đó để lấy một implementation có sẵn. Đường đi logic:

```text
SecureRandom.getInstanceStrong()
       |
       v
Security.getProperty("securerandom.strongAlgorithms")
       |
       v
Tra danh sách thuật toán/provider được nền tảng cấu hình
       |
       v
SecureRandom engine + provider được chọn
       |
       v
nextBytes / generateSeed / reseed (hành vi tùy provider)
```

Một JDK có thể chọn provider gắn với cơ chế ngẫu nhiên của hệ điều hành; JDK khác có thể chọn thuật toán khác. Tên như `NativePRNGBlocking`, `Windows-PRNG` hay `DRBG` là **đặc thù của runtime**, không phải danh sách bắt buộc chung.

**Ranh giới quan trọng:** phương thức factory có thể trả về nhanh nhưng lần `nextBytes` đầu tiên lại phải lấy entropy. Nó cũng có thể tốn thời gian lúc khởi tạo. Không được dùng tên “Strong” để suy ra latency; phải kiểm tra provider và đo thực tế.

Java SE yêu cầu nền tảng cung cấp ít nhất một triển khai strong, nhưng `getInstanceStrong()` vẫn khai báo `NoSuchAlgorithmException` khi không lấy được thuật toán từ cấu hình/provider hiện có. Trong môi trường cấu hình bất thường, ứng dụng cần xử lý lỗi minh bạch, không âm thầm thay bằng bộ sinh không bảo mật.

### 2.2 `getInstance("DRBG")` yêu cầu một engine cụ thể

`SecureRandom.getInstance("DRBG")` yêu cầu một provider đăng ký thuật toán `DRBG` cho engine `SecureRandom`. Có thể đi xa hơn bằng overload nhận `SecureRandomParameters` để chỉ rõ yêu cầu:

```java
SecureRandom drbg = SecureRandom.getInstance(
        "DRBG",
        DrbgParameters.instantiation(
                128,
                DrbgParameters.Capability.RESEED_ONLY,
                null));
```

Đây là **đoạn minh họa nhúng**; trong chương trình đầy đủ phải import `SecureRandom` và `DrbgParameters` (xem thực hành 2).

`128` là security strength **tối thiểu yêu cầu**, `RESEED_ONLY` yêu cầu có khả năng reseed, và `null` là không truyền personalization string. Nếu provider không đáp ứng yêu cầu, việc khởi tạo sẽ thất bại; không nên coi “DRBG” là thuật toán luôn có trên mọi Java runtime.

JDK dùng họ cơ chế theo NIST SP 800-90A như Hash_DRBG, HMAC_DRBG hoặc CTR_DRBG tùy provider/configuration. `DRBG` trong lời gọi `getInstance` là tên **SecureRandom service algorithm**, không trực tiếp chọn một cơ chế Hash/HMAC/CTR cụ thể. Muốn biết implementation thực tế, xem tài liệu provider và cấu hình môi trường.

## 3. Vòng đời DRBG: instantiate → generate → reseed

Một DRBG duy trì trạng thái nội bộ theo các pha:

1. **Instantiate:** nhận nguồn entropy và các tham số, thiết lập trạng thái bí mật. Có implementation khởi tạo lười, chỉ lấy entropy khi sinh byte lần đầu.
2. **Generate:** tạo byte từ trạng thái hiện có bằng `nextBytes`, cập nhật trạng thái; nếu không cần truy cập entropy thì thường nhanh hơn đọc entropy mới cho từng lần gọi.
3. **Reseed:** bổ sung entropy mới và làm mới trạng thái khi policy/giới hạn thuật toán hoặc yêu cầu ứng dụng cần. Bước này có thể cần chờ nguồn entropy.
4. **Prediction resistance (PR):** khi được yêu cầu và provider hỗ trợ, thao tác tạo byte có thể cần nguồn entropy mới để bảo vệ tốt hơn trước kịch bản lộ trạng thái trước đó. Không thể có PR thực chất chỉ bằng cách đặt cờ mà không có nguồn entropy đáng tin.

### 3.1 Capability: yêu cầu tối thiểu và tính năng hiệu lực

`DrbgParameters.Capability` có ba mức:

| Capability khi yêu cầu | Bảo đảm tối thiểu | Có thể làm gì? |
| --- | --- | --- |
| `NONE` | Không buộc hỗ trợ reseed hoặc PR | Triển khai vẫn *có thể* có khả năng tốt hơn yêu cầu |
| `RESEED_ONLY` | Hỗ trợ reseed | Có thể gọi `reseed(...)`; không đảm bảo hỗ trợ PR |
| `PR_AND_RESEED` | Hỗ trợ cả reseed và PR | Có thể yêu cầu prediction resistance khi generate/reseed |

**Đừng đồng nhất “requested” và “effective”.** Một provider có thể trả về capability cao hơn yêu cầu tối thiểu. Sau khi lấy instance, xem:

```java
var params = drbg.getParameters();
if (params instanceof DrbgParameters.Instantiation actual) {
    int effectiveStrength = actual.getStrength();
    boolean canReseed = actual.getCapability().supportsReseeding();
    boolean canRequestPR =
            actual.getCapability().supportsPredictionResistance();
}
```

Đây là **đoạn mã nhúng** trong phương thức đã có biến `SecureRandom drbg` và import `DrbgParameters`. Thông tin trả về mô tả thiết lập **thực tế** (theo hợp đồng API), không buộc giống hệt tham số lúc yêu cầu.

### 3.2 Các tham số dùng lúc generate và reseed

- `DrbgParameters.instantiation(strength, capability, personalizationString)`: cấu hình khi tạo DRBG; `strength = -1` có nghĩa dùng strength mặc định khi instantiation.
- `DrbgParameters.nextBytes(strength, predictionResistance, additionalInput)`: tham số cho *một lần* `nextBytes(bytes, params)`; `strength = -1` lấy strength hiệu lực. Cờ `predictionResistance=true` yêu cầu PR cho lần này, chỉ dùng khi implementation hỗ trợ.
- `DrbgParameters.reseed(predictionResistance, additionalInput)`: tham số cho `reseed(params)`; thao tác này đọc entropy theo cơ chế provider.
- `personalizationString` và `additionalInput` phục vụ phân biệt ngữ cảnh/đa dạng hóa trạng thái theo giao diện DRBG, **không thay thế nguồn entropy an toàn** và không phải “khóa bí mật” bắt buộc.

Nếu chỉ yêu cầu `RESEED_ONLY`, không được tự gọi `nextBytes(..., DrbgParameters.nextBytes(-1, true, ...))` rồi giả định provider sẽ hỗ trợ. Tăng yêu cầu capability/strength có thể khiến `getInstance` thất bại. Các thao tác reseed đặc biệt có thể làm tăng latency vì tiếp xúc nguồn entropy.

## 4. Vì sao và khi nào nguồn ngẫu nhiên có thể block?

**Block** nghĩa là thread gọi API phải chờ trước khi phương thức trả về; khác với “sinh byte CPU tốn vài mili giây”. Theo API Java 17, tùy implementation, **`nextBytes`, `generateSeed` và `reseed` đều có thể block trong lúc thu thập entropy**.

Ba tình huống thường gặp:

| Tình huống | Điều có thể diễn ra | Lưu ý |
| --- | --- | --- |
| Lần sử dụng PRNG đầu tiên | Tạo seed từ entropy source, có thể đợi | Thời gian `new SecureRandom()` ngắn chưa chứng minh `nextBytes` đầu tiên nhanh |
| `generateSeed(n)` hoặc explicit `reseed()` | Provider liên hệ nguồn seed/entropy | Không dùng `generateSeed` thay cho `nextBytes` một cách máy móc |
| Yêu cầu PR hoặc provider cần tự reseed | Nhu cầu entropy mới xuất hiện trong thao tác generate | `DRBG` không đồng nghĩa “không chặn”; policy và môi trường quyết định |

Các cơ chế OS được thảo luận phổ biến gồm `/dev/random` và `/dev/urandom` trên Unix-like, API ngẫu nhiên hệ điều hành trên Windows, thiết bị HSM hoặc provider phần mềm. **Không áp đặt quy tắc “`/dev/random` luôn block” hoặc “`/dev/urandom` luôn là cách sửa”**: hành vi đã thay đổi qua phiên bản kernel và cách provider tích hợp. Đọc cấu hình thực tế của JDK/OS rồi kiểm chứng bằng đo lường.

Với ứng dụng web hoặc worker có SLA, cần xét thêm:

- Khi **startup hoặc first use**, ứng dụng có thể đứng đợi entropy trước request đầu tiên.
- Nếu mỗi request tự tạo `getInstanceStrong()` hoặc `reseed()`, lượng truy cập entropy và độ trễ có thể tăng đáng kể.
- Nếu mọi thread dùng một đối tượng `SecureRandom`, Java bảo đảm **an toàn cho truy cập đa luồng**; tuy nhiên provider có thể đồng bộ nội bộ khiến contention, nên throughput/latency vẫn cần đo.
- Trong môi trường VM/container, sự phụ thuộc host, runtime image, provider và entropy source có thể khác hẳn máy phát triển. Quan sát thread dump và thời gian gọi thay vì kết luận dựa vào tên hệ điều hành.

**Không thể đảm bảo một API tạo secret luôn non-blocking trên mọi runtime.** Nếu nghiệp vụ có yêu cầu timeout, isolation và cơ chế backpressure, hãy thiết kế ranh giới xử lý từ đầu; việc hết thời gian chờ ở tầng caller không đảm bảo có thể ngắt ngay một thao tác native đang chờ entropy.

## 5. Thực hành 1: `getInstanceStrong` và token URL-safe

Chương trình tạo một token bằng `new SecureRandom()`, sau đó khám phá provider được chọn bởi `getInstanceStrong()`. **Nó không in giá trị token**, tránh đưa secret vào log.

Lưu file **`StrongRandomDemo.java`** (Java 17+):

```java
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

public class StrongRandomDemo {
    public static void main(String[] args) {
        String property = Security.getProperty("securerandom.strongAlgorithms");
        System.out.println("strong config present: " +
                (property != null && !property.isBlank()));

        SecureRandom defaultRandom = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        defaultRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder()
                .withoutPadding().encodeToString(tokenBytes);

        System.out.println("token bytes: " + tokenBytes.length);
        System.out.println("token characters: " + token.length());
        System.out.println("default provider: " +
                defaultRandom.getProvider().getName());

        try {
            SecureRandom strong = SecureRandom.getInstanceStrong();
            byte[] sample = new byte[16];
            strong.nextBytes(sample);
            System.out.println("strong provider: " + strong.getProvider().getName());
            System.out.println("strong algorithm: " + strong.getAlgorithm());
            System.out.println("strong sample bytes: " + sample.length);
        } catch (NoSuchAlgorithmException e) {
            System.out.println("strong implementation unavailable");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 StrongRandomDemo.java
java StrongRandomDemo
```

Ví dụ **một lần chạy thực tế** trên Windows/JDK có các provider tương ứng (các dòng provider/algorithm/phần cấu hình **có thể khác**):

```text
strong config present: true
token bytes: 32
token characters: 43
default provider: SUN
strong provider: SunMSCAPI
strong algorithm: Windows-PRNG
strong sample bytes: 16
```

Điểm có thể kiểm tra ổn định khi chương trình chạy thành công:

- `32` byte cho token; Base64URL không padding sinh **43 ký tự** từ 32 byte, bất kể giá trị byte thực tế.
- `strong.sample` dùng 16 byte nhưng không in byte mật mã.
- `default provider`, `strong provider` và `strong algorithm` **là dữ liệu runtime**, không nên đưa tên mẫu vào assertion portable.
- `NoSuchAlgorithmException` được xử lý riêng; nếu cấu hình strong thiếu, nhánh báo unavailable chạy thay vì chương trình bí mật chuyển sang `java.util.Random`.

Token 32 byte có **độ rộng 256 bit**, nhưng mức an toàn thực tế còn phụ thuộc chất lượng RNG/entropy và cách quản lý token. Với token phiên, cần thêm thời hạn, cơ chế thu hồi, bảo vệ khi truyền/lưu trữ và không log token.

**Đối chiếu:** `getInstanceStrong()` không bắt buộc để sinh mọi token. Một `SecureRandom` mặc định của môi trường đã được kiểm chứng là lựa chọn thông dụng; nếu chính sách yêu cầu provider cụ thể, hãy quy định rõ và thử nghiệm trên môi trường thật.

## 6. Thực hành 2: yêu cầu DRBG strength/reseed, đọc cấu hình hiệu lực

Chương trình này dùng **DRBG có hỗ trợ reseed**, phát sinh 32 byte, rồi chủ động reseed để thể hiện API. Trong sản phẩm thực tế, **không cần reseed thủ công ở mỗi request**; đây là thao tác dạy vòng đời.

Lưu file **`DrbgParametersDemo.java`** (Java 17+):

```java
import java.nio.charset.StandardCharsets;
import java.security.DrbgParameters;
import java.security.SecureRandom;

public class DrbgParametersDemo {
    public static void main(String[] args) throws Exception {
        DrbgParameters.Instantiation requested =
                DrbgParameters.instantiation(
                        128,
                        DrbgParameters.Capability.RESEED_ONLY,
                        "lesson-3.6-v1".getBytes(StandardCharsets.UTF_8));

        SecureRandom random = SecureRandom.getInstance("DRBG", requested);
        if (!(random.getParameters() instanceof DrbgParameters.Instantiation actual)) {
            throw new IllegalStateException("No effective DRBG parameters");
        }

        byte[] bytes = new byte[32];
        random.nextBytes(
                bytes,
                DrbgParameters.nextBytes(
                        -1, false, "operation-1".getBytes(StandardCharsets.UTF_8)));

        System.out.println("algorithm: " + random.getAlgorithm());
        System.out.println("strength >= 128: " + (actual.getStrength() >= 128));
        System.out.println("reseed supported: " +
                actual.getCapability().supportsReseeding());
        System.out.println("generated bytes: " + bytes.length);

        random.reseed(DrbgParameters.reseed(false, null));
        System.out.println("reseed: OK");
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 DrbgParametersDemo.java
java DrbgParametersDemo
```

Khi runtime có provider `DRBG` đáp ứng strength và capability yêu cầu, ví dụ đầu ra đã kiểm tra:

```text
algorithm: DRBG
strength >= 128: true
reseed supported: true
generated bytes: 32
reseed: OK
```

**Vì sao các kết quả boolean bằng `true`?** Factory chỉ tạo thành công khi request tối thiểu được đáp ứng; `getParameters()` báo thiết lập hiệu lực. Ví dụ không giả định chính xác strength là 128 hoặc capability là `RESEED_ONLY`: provider có quyền báo mức cao hơn.

Giải mã từng tham số:

1. `128`: strength tối thiểu theo cơ chế DRBG, không phải “phát sinh đúng 128 bit”.
2. `RESEED_ONLY`: bảo đảm API reseed khả dụng; **không** xin prediction resistance.
3. `"lesson-3.6-v1"`: personalization string để tách ngữ cảnh, không phải secret seed.
4. `nextBytes(-1, false, "operation-1"... )`: dùng strength hiệu lực, không xin PR cho lần phát sinh; chuỗi additional input không tạo entropy mới.
5. `reseed(false, null)`: yêu cầu lấy entropy mới để cập nhật trạng thái; có thể block tùy nguồn entropy/provider.

Nếu provider thiếu `DRBG` hoặc không đáp ứng capability, `getInstance` có thể ném `NoSuchAlgorithmException`. Khi bài toán có chính sách bảo mật ràng buộc, **không im lặng giảm strength hoặc đổi qua RNG không mật mã** để chạy tiếp.

### 6.1 Khi thực sự yêu cầu prediction resistance

Một ứng dụng có thể yêu cầu:

```java
DrbgParameters.Instantiation strongRequest =
        DrbgParameters.instantiation(
                256,
                DrbgParameters.Capability.PR_AND_RESEED,
                null);
SecureRandom prDrbg = SecureRandom.getInstance("DRBG", strongRequest);

byte[] output = new byte[32];
prDrbg.nextBytes(output, DrbgParameters.nextBytes(-1, true, null));
```

Đây là **đoạn mã minh họa trong phương thức có import `SecureRandom`/`DrbgParameters`**, không phải file chạy độc lập. Nó **có thể thất bại** nếu provider không hỗ trợ strength 256/PR; khi thành công, cờ `true` yêu cầu prediction resistance cho lần generate này. Provider có thể phải lấy entropy mới và chặn thread trong quá trình thực hiện. Đừng tự suy ra PR được bảo đảm chỉ vì dữ liệu có 256 bit.

## 7. Thực hành 3: đo riêng thời gian tạo instance và phát sinh byte

Đo **constructor/factory**, **lần `nextBytes` đầu** và **lần kế** riêng biệt vì nhiều triển khai seed theo nhu cầu. Chương trình cho phép chạy mặc định hoặc thêm đối số `strong` để quan sát lựa chọn `getInstanceStrong()`.

Lưu file **`RandomLatencyProbe.java`**:

```java
import java.security.SecureRandom;

public class RandomLatencyProbe {
    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        SecureRandom random =
                args.length > 0 && args[0].equals("strong")
                ? SecureRandom.getInstanceStrong()
                : new SecureRandom();
        long constructed = System.nanoTime();

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        long first = System.nanoTime();

        random.nextBytes(bytes);
        long second = System.nanoTime();

        System.out.println("algorithm: " + random.getAlgorithm());
        System.out.println("provider: " + random.getProvider().getName());
        System.out.printf("construct: %.3f ms%n",
                (constructed - start) / 1_000_000.0);
        System.out.printf("first nextBytes: %.3f ms%n",
                (first - constructed) / 1_000_000.0);
        System.out.printf("second nextBytes: %.3f ms%n",
                (second - first) / 1_000_000.0);
    }
}
```

Biên dịch và chạy (chạy `strong` **chỉ trên môi trường đã được phép thử và có thể chịu latency**):

```bash
javac --release 17 RandomLatencyProbe.java
java RandomLatencyProbe
java RandomLatencyProbe strong
```

Một lần chạy chế độ mặc định trên môi trường thử cho kết quả:

```text
algorithm: DRBG
provider: SUN
construct: 38.408 ms
first nextBytes: 18.310 ms
second nextBytes: 0.059 ms
```

**Tất cả tên provider và giá trị đo thay đổi theo JVM, OS, tải máy, JIT, entropy source và lần khởi động**. Đây là dữ liệu minh họa đã đo, tuyệt đối không phải benchmark hoặc ngưỡng bảo đảm. Lần thứ nhất chậm hơn có thể do seeding, class loading hoặc yếu tố khởi động khác; một phép đo **không chứng minh** nguồn entropy bị thiếu.

Khi chẩn đoán hệ thống thực, ghi lại:

- JVM/vendor/version, hệ điều hành và môi trường VM/container;
- `getAlgorithm()`, `getProvider().getName()` và security property liên quan;
- thời gian khởi tạo, lần phát sinh đầu tiên, các lần tiếp theo; nếu có reseed, đo riêng;
- tần suất, phân vị p95/p99, thread dump/điểm call stack lúc treo và tương quan với startup hoặc load.

Không dùng việc thay `/dev/random` bằng một đường dẫn khác hoặc thay đổi `java.security.egd` như “thuốc chữa” phổ quát: ý nghĩa cấu hình phụ thuộc implementation và có thể làm lệch chính sách entropy của hệ thống. Kiểm tra tài liệu provider/JDK và đánh giá bảo mật trước khi thay đổi.

## 8. Lựa chọn thực tế trong ứng dụng

| Nhu cầu | Điểm khởi đầu hợp lý | Điều phải tránh |
| --- | --- | --- |
| Session token, CSRF token, mã bí mật dùng một lần | `SecureRandom` của môi trường đã kiểm chứng; `nextBytes` đủ dài, encode an toàn | `Math.random()`, `java.util.Random`, seed từ thời gian, ghi secret vào log |
| Khóa có giá trị cao/tồn tại lâu dài | API sinh khóa chuyên dụng và chính sách RNG/provider rõ ràng; cân nhắc `getInstanceStrong()` nếu chính sách cần | Mặc định strong sẽ nhanh hoặc mọi runtime chọn giống nhau |
| Cần strength/capability DRBG cụ thể | `getInstance("DRBG", DrbgParameters.instantiation(...))`, kiểm tra effective parameters | Xin `PR_AND_RESEED` mà không đánh giá chi phí entropy/độ trễ |
| API latency thấp hoặc khởi động số lượng lớn | Đo trên runtime đích, khởi tạo có kiểm soát, tránh reseed/factory không cần thiết trong hot path | Hứa “non-blocking” chỉ vì thuật toán tên DRBG |
| HSM/FIPS/chính sách provider | Chọn provider được thẩm định qua cấu hình/overload, kiểm thử lỗi và lifecycle | Dựa vào tên provider chỉ xuất hiện trên máy phát triển |

### 8.1 Quản lý vòng đời và concurrency

Một đối tượng `SecureRandom` **an toàn khi được nhiều thread gọi** theo hợp đồng Java 17. Tuy nhiên nó có thể được provider đồng bộ bên trong, nên ứng dụng có throughput cao vẫn cần đo tranh chấp. Khởi tạo riêng ở mỗi request không tự tăng tính an toàn và có thể gây tăng chi phí khởi tạo/entropy.

Một pattern thông dụng là giữ `SecureRandom` thích hợp trong thành phần chuyên cấp random bytes, tiêm phụ thuộc vào những nơi cần; bảo đảm cấu hình RNG được kiểm thử sớm khi ứng dụng khởi động. Nếu chính sách yêu cầu strong/HSM, xử lý việc không khởi tạo được như một lỗi cấu hình/bảo mật có thể quan sát, thay vì tự fallback sang RNG không thích hợp.

### 8.2 Test thế nào khi đầu ra ngẫu nhiên?

- Unit test tập trung vào **định dạng, độ dài, điều kiện hợp lệ và cách xử lý lỗi**, không assert token có giá trị byte cố định.
- Có thể inject một nguồn byte giả lập trong kiểm thử thuần logic (không đi qua đường sinh secret thật); production luôn sử dụng `SecureRandom` phù hợp.
- Hai lần sinh byte có xác suất trùng rất nhỏ nhưng không **bảo đảm tuyệt đối khác nhau**; dùng test xác suất đơn lẻ như `assertNotEquals(a, b)` là không cần thiết cho hợp đồng API.
- Không gửi raw token, seed hoặc trạng thái RNG vào log/trace/test report. Nếu cần quan sát, ghi thông tin cấu hình và độ trễ, không ghi bí mật.

## 9. Lỗi thường gặp và cách sửa

1. **“`getInstanceStrong` luôn không chặn vì mạnh.”** Sai: nó chọn theo property, còn hành vi chặn phụ thuộc provider/nguồn entropy; hãy đo riêng creation và generate.
2. **“DRBG deterministic nên không bảo mật.”** Sai: DRBG mật mã cần seed không dự đoán được; “deterministic” mô tả bước sinh từ trạng thái, không đánh giá chất lượng seed.
3. **“Có 32 byte output tức 256-bit strength.”** Sai: phân biệt độ dài byte với strength đã được provider bảo đảm.
4. **“`setSeed(System.nanoTime())` giúp hết block và tăng entropy.”** Sai và có nguy cơ giảm an toàn, nhất là nếu seed được đưa vào trước khi provider tự seed. Để provider dùng entropy tin cậy.
5. **“Cứ yêu cầu PR/reseed mỗi request để an toàn nhất.”** Không đúng chung: nhu cầu entropy/latency có thể rất lớn; cần threat model và chính sách rõ.
6. **“Gọi `generateSeed(32)` thay cho `nextBytes` trong mọi use case.”** Sai: API seed material có thể có hành vi lấy entropy khác và không phải cách tối ưu phổ quát để cấp token.
7. **“Cùng Java 17 thì provider/algorithm/time giống nhau.”** Sai: bản phân phối, OS, cấu hình bảo mật và thiết bị quyết định lựa chọn thực tế.
8. **“Nếu thiếu DRBG/strong cứ fallback về `Random`.”** Không chấp nhận cho secret; cần xử lý lỗi hoặc chọn triển khai bảo mật được chính sách cho phép, có kiểm chứng.
9. **“Không biết code nào chặn thì đặt timeout là đủ.”** Timeout ở lớp gọi không nhất thiết ngắt được thao tác native/entropy đang chờ. Thiết kế thread budget, isolation, retry/backpressure và observability phù hợp.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** `getInstanceStrong()` chọn thuật toán dựa vào đâu?  
Dựa vào property `securerandom.strongAlgorithms` và provider/algorithm hiện có của runtime, không phải dựa vào một thuật toán “Strong” chung của Java.

**Câu 2.** `nextBytes` của DRBG có nhất thiết truy cập OS entropy mỗi lần không?  
Không. Nó thường sinh từ trạng thái nội bộ sau instantiate; nhưng có thể truy cập entropy khi instantiate/reseed hoặc khi policy/PR yêu cầu.

**Câu 3.** Yêu cầu `RESEED_ONLY` bảo đảm điều gì?  
Ít nhất khả năng reseed, **không** bắt buộc prediction resistance; kiểm tra `getParameters()` để biết capability hiệu lực.

**Câu 4.** Muốn tìm nguồn của độ trễ `SecureRandom` ở production thì xem gì?  
Algorithm, provider, security property, JVM/OS, thời gian init/first/steady-state, lúc reseed và thread stacks; không kết luận chỉ từ một số đo.

**Câu 5.** Vì sao không tự `setSeed` bằng thời gian?  
Dữ liệu thời gian có entropy thấp/dễ đoán; lời gọi quá sớm còn có thể ngăn cơ chế tự seed của PRNG theo hợp đồng API.

Ghi nhớ mô hình: **entropy source → seed/reseed → trạng thái DRBG → `nextBytes`**, còn **`getInstanceStrong()` là cơ chế chọn triển khai theo chính sách nền tảng**. Một lựa chọn phù hợp cần cả an toàn mật mã, khả năng tương thích provider và độ trễ đã kiểm chứng trên môi trường thực.

## Tài liệu chính thức

- [Java 17 — SecureRandom API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): cơ chế chọn instance, seed, thread safety và khả năng block.
- [Java 17 — DrbgParameters API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/DrbgParameters.html): instantiate, nextBytes, reseed và cấu hình DRBG.
- [Java 17 — DrbgParameters.Capability](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/DrbgParameters.Capability.html): NONE, RESEED_ONLY và PR_AND_RESEED.
- [Java 17 — JCA Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): kiến trúc SecureRandom/provider và cấu hình bảo mật.
- [NIST SP 800-90A Rev. 1](https://csrc.nist.gov/pubs/sp/800/90/a/r1/final): cơ sở khái niệm về deterministic random bit generators.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-randomness-007">3.7 Phân biệt salt, IV, nonce, challenge và token ngẫu nhiên</a>

<details>
<summary>Xem chi tiết</summary>

# 3.7 Phân biệt salt, IV, nonce, challenge và token ngẫu nhiên

**Salt**, **initialization vector (IV)**, **nonce**, **challenge** và **token ngẫu nhiên** đều có thể chứa những byte do `SecureRandom` sinh ra, nhưng **không thể thay thế tùy ý**. Mỗi loại phục vụ một mục tiêu bảo mật khác nhau và có quy tắc riêng về tính duy nhất, khả năng dự đoán, giữ bí mật, phạm vi sử dụng và vòng đời.

Hiểu đúng các khái niệm giúp tránh những lỗi nghiêm trọng như tái sử dụng nonce trong AES-GCM, dùng chung salt cho mọi tài khoản, chấp nhận replay một challenge hoặc làm lộ bearer token.

## 1. So sánh nhanh năm khái niệm

| Giá trị | Mục đích chính | Thuộc tính bắt buộc | Có phải bí mật? | Ví dụ và nơi lưu |
| --- | --- | --- | --- | --- |
| **Salt** | Tách biệt kết quả dẫn xuất/mã băm mật khẩu giữa các bản ghi, dù mật khẩu giống nhau. | Riêng biệt cho từng mật khẩu/verifier; được cung cấp lại khi xác minh. | **Không.** | 16 byte ngẫu nhiên, lưu cùng bản ghi hash, thuật toán và cost. |
| **IV** (initialization vector) | Khởi tạo một lần mã hóa theo mode cụ thể. | **Phụ thuộc mode**: CBC cần IV khó dự đoán; nhiều mode dựa trên counter yêu cầu không lặp. | Thường **không**. | AES-CBC dùng IV 16 byte; lưu/truyền cùng ciphertext. |
| **Nonce** (number used once) | Đánh dấu một lần sử dụng khóa hoặc một thông điệp trong giao thức. | **Không lặp lại trong phạm vi được chỉ định**; với AES-GCM, tuyệt đối không lặp cùng key. | Tùy giao thức; GCM nonce thường **không**. | AES-GCM thường dùng nonce/IV 12 byte, đi cùng ciphertext. |
| **Challenge** | Buộc bên kia chứng minh sở hữu khóa/quyền đối với một thử thách **mới**; ngăn phát lại chứng cứ cũ. | Khó đoán, mới, giới hạn thời gian; gắn với user, phiên/hành động và thường dùng một lần. | Thường **không**. | Server sinh 32 byte; lưu trạng thái challenge, context, expiry, used. |
| **Random bearer token** | Cấp khả năng sử dụng phiên/quyền/khôi phục thông qua việc **sở hữu giá trị**. | Khó đoán với entropy cao; phải bảo vệ, giới hạn scope, hết hạn và thu hồi được. | **Có.** | 32 byte ngẫu nhiên cho session/reset/API token; server lưu verifier và metadata quyền. |

Các độ dài trên là **ví dụ thực hành**, không phải công thức áp dụng cho mọi hệ thống. IV/nonce phải đúng kích thước và quy tắc của **thuật toán, mode, protocol** đang dùng. Challenge và token cần entropy, vòng đời và giao thức tương ứng.

Một câu hỏi phân biệt nhanh: “Giá trị này dùng để **dẫn xuất mật khẩu**, **mã hóa**, **chống replay**, hay **mang quyền**?” Trả lời câu đó trước khi chọn cách sinh random.

## 2. Đừng nhầm uniqueness, unpredictability và secrecy

Ba thuộc tính khác nhau:

1. **Tính duy nhất (uniqueness):** giá trị không lặp trong phạm vi đã định. Bộ đếm tăng dần có thể duy nhất nếu quản lý đúng xuyên restart/đa node, nhưng dễ dự đoán.
2. **Tính khó dự đoán (unpredictability):** kẻ tấn công không thể tính giá trị mới trước khi nó xuất hiện. Challenge, secret token và IV CBC cần tính chất này trong ngữ cảnh thích hợp.
3. **Tính bí mật (secrecy):** người không được cấp quyền không được biết hoặc sử dụng giá trị. Bearer token phải được bảo vệ như một credential. Salt và IV/nonce trong các mode phổ biến thường đi kèm bản ghi/ciphertext mà không cần mã hóa riêng.

| Giá trị ví dụ | Duy nhất? | Khó dự đoán? | Bí mật? | Kết luận |
| --- | --- | --- | --- | --- |
| Bộ đếm 1, 2, 3 | Có thể có | Không | Không | Có thể làm nonce cho **một số giao thức** nếu bảo đảm không lặp; không tự động phù hợp CBC. |
| 12 byte sinh từ CSPRNG | Xác suất trùng rất nhỏ, không tuyệt đối bằng 0 | Có | Không mặc định | Có thể dùng cho AES-GCM khi tuân thủ giới hạn lượng bản mã/khóa. |
| 32 byte CSPRNG được gửi công khai | Xác suất trùng rất nhỏ | Có trước khi công bố | Không | Có thể làm challenge công khai; không còn là bearer secret. |
| 32 byte CSPRNG được giữ kín | Xác suất trùng rất nhỏ | Có | Có, nếu vận chuyển/lưu trữ đúng | Có thể là bearer token nhưng vẫn cần hạn dùng, scope và thu hồi. |

**Encoding không thêm entropy.** 32 byte ngẫu nhiên có tối đa 256 bit entropy dù được biểu diễn thành 43 ký tự Base64URL, 64 ký tự hex hay một kiểu chuỗi khác. Timestamp và ID tăng dần không tự trở thành giá trị bảo mật nhờ mã hóa Base64.

## 3. Salt: làm riêng kết quả dẫn xuất mật khẩu

### 3.1. Vì sao phải có salt?

Nếu Alice và Bob cùng đặt một mật khẩu và hệ thống chỉ băm bằng một hàm xác định, kết quả của hai bản ghi sẽ giống nhau. Việc dùng **salt khác nhau cho mỗi bản ghi** khiến đầu ra khác nhau, làm giảm khả năng dùng bảng hash tính trước và buộc kẻ tấn công xử lý từng verifier riêng.

~~~text
Alice: password + salt_A + cost -> PasswordKDF -> verifier_A
Bob:   password + salt_B + cost -> PasswordKDF -> verifier_B

Bản ghi Alice: {algorithm, parameters/cost, salt_A, verifier_A}
Khi đăng nhập: đọc salt_A, algorithm, cost; dẫn xuất lại rồi so sánh
~~~

- Sinh salt **mới** bằng CSPRNG khi tạo verifier mật khẩu mới; đổi mật khẩu cũng nên tạo salt mới.
- Salt **không cần giấu**. Lưu cùng verifier là bình thường và cần thiết để xác minh.
- Không dùng salt cố định/toàn cục, username hoặc timestamp thay cho salt đủ ngẫu nhiên.
- **Salt không thay thế password hashing/KDF chậm.** Dùng trực tiếp `SHA-256(password + salt)` vẫn tạo điều kiện thử mật khẩu quá nhanh. Nên dùng Argon2id, scrypt hoặc PBKDF2 với cấu hình phù hợp.
- **Pepper khác salt:** pepper là bí mật riêng, được giữ ngoài database mật khẩu (chẳng hạn trong secret manager/HSM), là lớp bảo vệ bổ sung chứ không thay salt.
- Thuật ngữ salt còn xuất hiện ở KDF như HKDF; tại đó phải áp dụng **quy tắc của KDF cụ thể**, không mặc định mọi salt đều là salt của mật khẩu.

### 3.2. Ví dụ Java 17+: cùng mật khẩu, hai salt

Tệp **`PasswordSaltDemo.java`** dùng `PBKDF2WithHmacSHA256` trong JDK để minh họa. Với ứng dụng mới, cân nhắc Argon2id bằng thư viện đáng tin nếu phù hợp; với PBKDF2, số vòng `600_000` minh họa mức OWASP khuyến nghị cho HMAC-SHA256 tại thời điểm tham khảo và cần được kiểm tra lại theo hướng dẫn/hiệu năng thực tế.

~~~java
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordSaltDemo {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int ITERATIONS = 600_000;

    private static byte[] randomSalt() {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return salt;
    }

    private static byte[] derive(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public static void main(String[] args) throws Exception {
        char[] password = "demo password only".toCharArray();
        try {
            byte[] salt1 = randomSalt();
            byte[] salt2 = randomSalt();
            byte[] verifier1 = derive(password, salt1);
            byte[] verifier2 = derive(password, salt2);

            System.out.println("Distinct salts: "
                    + !Arrays.equals(salt1, salt2));
            System.out.println("Distinct verifiers: "
                    + !MessageDigest.isEqual(verifier1, verifier2));
            System.out.println("Verification: " + MessageDigest.isEqual(
                    verifier1, derive(password, salt1)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
~~~

~~~bash
javac PasswordSaltDemo.java
java PasswordSaltDemo
~~~

Đầu ra điển hình (xác suất hai salt ngẫu nhiên 128 bit trùng nhau là cực nhỏ):

~~~text
Distinct salts: true
Distinct verifiers: true
Verification: true
~~~

Nhận xét: cùng mật khẩu được xác minh thành công với **salt đúng của bản ghi**, còn hai salt mới thường tạo verifier khác nhau. Khi lưu thực tế cần lưu cả algorithm, salt, cost, verifier; cần chính sách nâng cấp thông số khi đăng nhập và rate limit để chống đoán mật khẩu trực tuyến.

## 4. IV và nonce: phụ thuộc thuật toán/mode

### 4.1. IV có phải nonce không?

**IV** là tham số khởi tạo một phép mã hóa. **Nonce** nhấn mạnh rằng giá trị được dùng một lần trong phạm vi quy định. Trong **AES-GCM**, cùng một tham số thường được gọi là **IV** hoặc **nonce**: **không phải** hai giá trị riêng cần sinh cho mỗi bản mã.

| Mode | Cách dùng | Yêu cầu | Kích thước AES thường gặp |
| --- | --- | --- | --- |
| **CBC** | IV khởi tạo phép ghép khối. | Với IV thông thường, phải khó dự đoán và được sinh mới đúng cách. Không tự cung cấp xác thực bản mã. | **16 byte**, bằng kích thước khối AES. |
| **CTR** | Nonce/counter tạo dòng khóa để XOR với dữ liệu. | Không tái sử dụng cùng dãy counter block/keystream dưới một khóa; khả năng dự đoán của nonce có thể được chấp nhận. | Tùy thiết kế cấu trúc counter và giao thức. |
| **GCM** | IV/nonce tham gia xây dựng trạng thái CTR và xác thực AEAD. | **Không lặp cặp (AES key, nonce).** | **12 byte (96 bit)** là lựa chọn ưu tiên phổ biến. |

CBC/CTR không tự cung cấp xác thực; với dữ liệu ứng dụng mới, thường chọn một **AEAD** như AES-GCM khi phù hợp, dùng cả encryption và authentication đúng cách.

### 4.2. Vì sao không được tái sử dụng GCM nonce?

Nếu mã hóa hai plaintext khác nhau bằng **cùng khóa AES-GCM và cùng nonce**, tính bảo mật và bảo đảm xác thực của GCM có thể bị phá vỡ. Dù hai bản mã được tạo ở hai microservice hoặc hai lần chạy JVM, **chúng vẫn vi phạm nếu cùng key/nonce**.

Các chiến lược sinh nonce:

- **CSPRNG 96 bit:** đơn giản, thuận tiện, nhưng không bảo đảm tuyệt đối không va chạm; giới hạn số lần mã hóa trên cùng key và chính sách rotation phải được thiết kế theo tải và đặc tả.
- **Counter được quản lý:** có thể bảo đảm không lặp nếu lưu tiến độ bền vững và phối hợp nhiều process/node. Không reset counter khi khởi động lại mà vẫn dùng khóa cũ.
- **Phân chia không gian nonce:** cấp vùng nonce riêng cho từng node hoặc dùng cơ chế phối hợp tương đương, tránh trùng giữa các máy.

“Nonce không bí mật” **không có nghĩa** được tái sử dụng. Ngược lại, “random” **không có nghĩa** chắc chắn duy nhất. Hãy thiết kế quản lý nonce cho **cả vòng đời khóa**.

### 4.3. Envelope AES-GCM cần lưu gì?

~~~text
Key K           -> giữ trong keystore/KMS, không gắn vào bản mã dưới dạng thô
Nonce / IV      -> gửi cùng bản mã để giải mã, không cần giấu
Ciphertext      -> dữ liệu đã mã hóa
Authentication tag -> bằng chứng xác thực của phép GCM
AAD             -> metadata được xác thực nhưng không mã hóa, phải nhất quán

Envelope minh họa: nonce(12 byte) || ciphertext || tag(16 byte)
~~~

Trong Java, `Cipher.doFinal` với `AES/GCM/NoPadding` thường trả ciphertext có kèm authentication tag. Cả phía encrypt/decrypt phải dùng đúng nonce, key, tag length và AAD. Thay đổi AAD hoặc bản mã phải khiến xác thực thất bại.

### 4.4. Java 17+: GCM nonce mới cho phép mã hóa

Tệp **`GcmNonceDemo.java`** tạo một khóa AES ngẫu nhiên phục vụ ví dụ, sinh 12 byte nonce cho lần encrypt và ghép vào đầu envelope. Việc chỉ sinh một lần trong ví dụ không thay cho chiến lược chống trùng trên nhiều node và trong suốt vòng đời khóa thực tế.

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class GcmNonceDemo {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    static byte[] encrypt(
            SecretKey key, byte[] plaintext, byte[] aad)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        byte[] ciphertextAndTag = cipher.doFinal(plaintext);

        return ByteBuffer.allocate(nonce.length + ciphertextAndTag.length)
                .put(nonce).put(ciphertextAndTag).array();
    }

    static byte[] decrypt(
            SecretKey key, byte[] envelope, byte[] aad)
            throws GeneralSecurityException {
        if (envelope.length < NONCE_BYTES + TAG_BITS / 8) {
            throw new IllegalArgumentException("Envelope too short");
        }

        ByteBuffer input = ByteBuffer.wrap(envelope);
        byte[] nonce = new byte[NONCE_BYTES];
        input.get(nonce);
        byte[] ciphertextAndTag = new byte[input.remaining()];
        input.get(ciphertextAndTag);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertextAndTag);
    }

    public static void main(String[] args)
            throws GeneralSecurityException {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128);
        SecretKey key = kg.generateKey();

        byte[] aad = "record:v1".getBytes(StandardCharsets.UTF_8);
        byte[] envelope = encrypt(
                key, "Hello".getBytes(StandardCharsets.UTF_8), aad);

        System.out.println("Recovered: " + new String(
                decrypt(key, envelope, aad), StandardCharsets.UTF_8));
        System.out.println("Nonce bytes: " + NONCE_BYTES);

        envelope[envelope.length - 1] ^= 1;
        try {
            decrypt(key, envelope, aad);
            throw new AssertionError("Tampering undetected");
        } catch (AEADBadTagException expected) {
            System.out.println("Tampering rejected: true");
        }
    }
}
~~~

~~~bash
javac GcmNonceDemo.java
java GcmNonceDemo
~~~

Stdout:

~~~text
Recovered: Hello
Nonce bytes: 12
Tampering rejected: true
~~~

Mã chạy chứng minh **nonce không cần bí mật** để giải mã, nhưng phải là nonce đúng của phép mã hóa. GCM từ chối bản mã/tag bị sửa thay vì trả plaintext không đáng tin cậy. Ví dụ chưa bao gồm version, key ID, kho khóa, khóa xoay vòng hoặc quản lý nonce cho tải lớn; đó là phần bắt buộc của thiết kế production.

### 4.5. Nonce trong ngữ cảnh khác

“Nonce” không phải kiểu dữ liệu có kích thước và chính sách chung. Trong một giao thức xác thực nó có thể là giá trị chống replay; trong giao thức chữ ký số có thể có nonce nội bộ nhạy cảm với yêu cầu riêng; trong Content Security Policy của web, nonce là giá trị gắn với **một HTTP response**. Đừng đem quy tắc “GCM nonce 12 byte công khai” áp vào mọi loại nonce.

## 5. Challenge: freshness và chống phát lại

Challenge là thử thách mới do server/bên xác minh đưa ra để yêu cầu client chứng minh có một bí mật/khóa. Challenge thường **công khai** vì client phải nhận nó để tạo chữ ký hoặc thông điệp xác thực.

### Ví dụ luồng xác thực bằng chữ ký

~~~text
1. Client yêu cầu xác thực/hành động
2. Server sinh challenge C mới từ CSPRNG
3. Server lưu {C hoặc hash(C), user, session, action, expiry, used=false}
4. Server gửi C cho client
5. Client ký thông điệp giao thức gồm C và context bằng private key
6. Client gửi chữ ký và định danh khóa
7. Server verify chữ ký bằng public key tin cậy,
   kiểm tra challenge đúng, context đúng, còn hạn, chưa dùng
8. Server đánh dấu consumed atomically rồi mới chấp nhận
~~~

**Chỉ gửi lại chính challenge không chứng minh được danh tính.** Phải có bằng chứng mật mã thật: chữ ký/MAC hoặc cơ chế đã được giao thức chuẩn định nghĩa.

Challenge an toàn cần:

- Được sinh mới, đủ khó đoán; ví dụ **32 byte `SecureRandom`** khi giao thức không đặt định dạng khác.
- **Ràng buộc user, phiên, origin, action, transaction** theo yêu cầu, để tránh chữ ký hợp lệ cho A bị dùng cho B.
- Có **thời hạn ngắn**, chỉ sử dụng một lần nếu giao thức yêu cầu.
- Được kiểm tra và tiêu thụ theo cách **atomic** để hai yêu cầu song song không cùng tái sử dụng được.
- Xác minh bằng khóa và thuật toán chính xác; không bỏ qua lỗi chữ ký hoặc chấp nhận challenge của hệ thống khác.

Trong WebAuthn, challenge chỉ là một thành phần cùng với kiểm tra RP ID, origin, authenticator data và các điều kiện chuyên biệt khác. Không thay giao thức WebAuthn bằng một bản tự chế chỉ kiểm tra challenge.

Challenge đôi khi **đồng thời là nonce** của giao thức (vì giá trị phải mới). Điều đó không biến nó thành IV mã hóa, cũng không làm challenge thành một bearer token.

## 6. Token ngẫu nhiên: giá trị mang quyền phải giữ bí mật

Một **opaque bearer token** là giá trị ngẫu nhiên không mang thông tin có thể suy ra trực tiếp. Server liên kết nó với trạng thái/quyền trong database/session store. **Ai sở hữu token hợp lệ có thể dùng quyền mà token đại diện**, nên token phải được bảo vệ.

Ví dụ: session token, password-reset token, email-verification token, API token. Mỗi loại có vòng đời và scope khác nhau; một token reset dùng một lần không thể được xử lý như session token dài hạn.

### Quy trình cấp và xác minh token

1. Server sinh **32 byte bằng CSPRNG** làm token bí mật mới; có thể biểu diễn bằng Base64URL không padding.
2. Server tạo bản ghi **quyền hạn, user, scope, expiry, trạng thái thu hồi**; nếu dùng một lần thì có `used`.
3. Thay vì lưu token thô, với token có entropy cao có thể lưu **`SHA-256(tokenBytes)`** làm verifier, giảm nguy cơ đọc lộ DB.
4. Khi client xuất trình token, server kiểm tra format, băm lại theo cùng biểu diễn byte, tìm/so sánh verifier, kiểm tra expiry/scope/revocation và chống dùng lại.
5. Server từ chối token sai, hết hạn hoặc đã thu hồi; thao tác một lần cần tiêu thụ trạng thái **atomic**.

**Vì sao có thể hash SHA-256 cho token nhưng không cho mật khẩu?** Mật khẩu do người chọn thường có entropy thấp, cần password KDF chậm để chống thử đoán. Một token 256 bit ngẫu nhiên có entropy cao, nên fast hash có thể phù hợp để lưu verifier; không được vì thế suy ra rằng mọi token đều có entropy cao (ví dụ token lấy từ ID/timestamp).

### Bảo vệ bearer token khi vận hành

- **Không log/tokenize toàn bộ giá trị** vào analytics, crash report hoặc URL query; dùng redaction và không in secret để debug.
- Dùng HTTPS; với session trên web, ưu tiên giải pháp framework tin cậy và cấu hình cookie `Secure`, `HttpOnly`, `SameSite` phù hợp.
- Đặt expiry phía server, quyền hẹp, revocation, rotation, rate limit; bảo vệ chống session fixation và CSRF theo kiến trúc.
- Nếu lưu hash token, server phải bảo đảm **không giữ token gốc trong cùng bản ghi**, và tránh so sánh byte bí mật bằng các hàm dễ gây rò timing; `MessageDigest.isEqual` là một lựa chọn phù hợp khi so sánh các digest.
- Token **có cấu trúc** như JWT ký số không tự động là opaque random token; cách xác thực JWT phụ thuộc chữ ký/claims/protocol, không chỉ entropy của chuỗi JWT.

Salt và challenge có thể công khai. **Bearer token đang còn hiệu lực thì không.**

## 7. Java 17+: sinh, encode và quan sát độ dài

`java.security.SecureRandom` là API chuẩn để sinh byte khó đoán phục vụ các trường hợp bảo mật. Kích thước và cách sử dụng do **mục đích** quyết định. Mã dưới đây chỉ minh họa khâu tạo dữ liệu; nó không thay phép dẫn xuất mật khẩu, mã hóa hay kiểm tra challenge/token.

Tệp **`RandomMaterialsDemo.java`**:

~~~java
import java.security.SecureRandom;
import java.util.Base64;

public class RandomMaterialsDemo {
    private static final SecureRandom RNG = new SecureRandom();

    private static byte[] bytes(int size) {
        byte[] result = new byte[size];
        RNG.nextBytes(result);
        return result;
    }

    private static int urlLength(byte[] value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value)
                .length();
    }

    public static void main(String[] args) {
        byte[] salt = bytes(16);
        byte[] cbcIv = bytes(16);
        byte[] gcmNonce = bytes(12);
        byte[] challenge = bytes(32);
        byte[] token = bytes(32);

        System.out.printf("salt=%d bytes, %d chars%n",
                salt.length, urlLength(salt));
        System.out.printf("CBC IV=%d bytes, %d chars%n",
                cbcIv.length, urlLength(cbcIv));
        System.out.printf("GCM nonce=%d bytes, %d chars%n",
                gcmNonce.length, urlLength(gcmNonce));
        System.out.printf("challenge=%d bytes, %d chars%n",
                challenge.length, urlLength(challenge));
        System.out.printf("token=%d bytes, %d chars%n",
                token.length, urlLength(token));
    }
}
~~~

~~~bash
javac RandomMaterialsDemo.java
java RandomMaterialsDemo
~~~

Stdout chính xác (chỉ in độ dài, không in byte ngẫu nhiên):

~~~text
salt=16 bytes, 22 chars
CBC IV=16 bytes, 22 chars
GCM nonce=12 bytes, 16 chars
challenge=32 bytes, 43 chars
token=32 bytes, 43 chars
~~~

Điều cần chú ý:

1. `SecureRandom.nextBytes` khác `java.util.Random`, `Math.random()` hay timestamp seed; những nguồn thông thường không phù hợp sinh bí mật.
2. Base64URL là **encoding**, không phải encryption; biểu diễn 32 byte thành 43 ký tự không làm tăng entropy.
3. Challenge và token đều được ví dụ sinh 32 byte, nhưng **challenge dùng để xác minh proof mới**, còn **token phải được giữ kín**. Không được gộp logic xử lý chúng chỉ vì cùng kích thước.
4. 12 byte nonce GCM là lựa chọn phổ biến cho **một chế độ mã hóa nhất định**; không mặc định mọi nonce/challenge phải dài 12 byte.
5. Có thể dùng một `SecureRandom` dùng chung, nhưng từng chức năng phải có quản lý vòng đời và chính sách kiểm tra riêng.

## 8. Bốn kịch bản áp dụng: ai tạo, ai giữ, ai xác minh?

### Kịch bản A: đăng ký và xác thực mật khẩu

~~~text
Lần tạo verifier:
  người dùng chọn password
  server sinh salt mới
  PasswordKDF(password, salt, cost) -> verifier
  database lưu {algorithm, cost, salt, verifier}

Lần đăng nhập:
  server đọc chính salt/cost đã lưu
  PasswordKDF(passwordNhập, saltĐãLưu, costĐãLưu)
  server so sánh kết quả với verifier
~~~

Salt được **lưu lâu dài và dùng lại để kiểm tra cùng bản ghi**. Sinh salt mới mỗi lần đăng nhập sẽ làm kết quả không khớp.

### Kịch bản B: mã hóa một record bằng AES-GCM

~~~text
Encrypt với khóa K:
  cấp nonce N chưa sử dụng với K
  (K, N, plaintext, AAD) -> ciphertext + tag
  lưu {version, keyId, N, ciphertext, tag, AAD/context}

Decrypt:
  tìm đúng K; lấy N, ciphertext, tag, AAD/context
  xác thực tag rồi mới chấp nhận plaintext
~~~

Nonce đi cùng ciphertext, không được dùng lại với K. Nếu nhiều node dùng K, phải điều phối giá trị nonce hoặc chia không gian nonce an toàn.

### Kịch bản C: thử thách ký số chống replay

~~~text
Server sinh C mới, lưu user/session/action/expiry/used
Client ký C + context bằng khóa của mình
Server verify chữ ký và kiểm tra C chưa hết hạn/chưa dùng
Server tiêu thụ C atomically rồi cho phép hành động
~~~

Challenge thường công khai; **private key** mới là phần bí mật mà chữ ký chứng minh sở hữu.

### Kịch bản D: reset mật khẩu qua token

~~~text
Server sinh token T mới, hạn ngắn, dùng một lần
Server lưu hash(T) và {user, expiry, used=false}
Server phát T qua kênh reset được kiểm soát
Người dùng xuất trình T
Server kiểm tra hash, user, expiry, used
Server consume atomically, đổi password, thu hồi quyền liên quan
~~~

Token reset **không phải salt mật khẩu**. Salt được duy trì để xác minh một verifier; token reset là credential tạm thời, hết tác dụng sau khi sử dụng.

## 9. Lỗi phổ biến và cách sửa

| Sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Dùng cùng salt cho mọi người dùng | Kết quả mật khẩu giống nhau có thể tương quan; suy yếu chống precomputation. | Salt mới, độc lập theo từng verifier. |
| Chỉ dùng `SHA-256(password + salt)` | Khó chống brute-force offline. | Dùng password KDF chuyên dụng có cost phù hợp. |
| Nghĩ IV/nonce phải bí mật nên tái sử dụng được | Bỏ qua quy tắc quan trọng của mode. | Lưu/truyền IV theo chuẩn, bảo đảm yêu cầu fresh/unique của mode. |
| Dùng cùng `(AES-GCM key, nonce)` hai lần | Có thể phá tính bí mật/xác thực. | Bảo đảm không trùng nonce theo vòng đời key, kể cả nhiều node. |
| Dùng bộ đếm dễ đoán làm IV CBC | Vi phạm yêu cầu khó dự đoán trong sử dụng CBC thông thường. | Sinh IV CBC ngẫu nhiên đúng kích thước, hoặc dùng mode phù hợp. |
| Dùng CBC/CTR mà không xác thực | Không phát hiện dữ liệu bị sửa đáng tin cậy. | Ưu tiên AEAD; nếu bắt buộc mode khác, kết hợp xác thực chuẩn. |
| Server gửi challenge rồi chỉ so sánh echo | Không chứng minh sở hữu khóa hoặc quyền. | Verify chữ ký/MAC và ngữ cảnh giao thức. |
| Challenge không expiry hoặc không one-time | Có thể replay lại proof. | Bind context, hạn dùng và tiêu thụ atomic. |
| Token tạo từ `Random`/timestamp/user ID | Dễ dự đoán, bị đoán/chiếm quyền. | CSPRNG + entropy đủ cao. |
| Đưa bearer token vào log/query string | Token bị lộ qua các hệ thống phụ trợ. | Giữ kín, HTTPS, cookie/header phù hợp, redact log. |
| Base64 dài nên “đủ entropy” | Đánh giá sai độ khó đoán. | Tính entropy của nguồn byte trước encoding. |
| Token random thì khỏi cần expiry/revoke | Token bị đánh cắp vẫn sử dụng được. | Scope hẹp, hết hạn, thu hồi, rotation và rate limit. |

## 10. Checklist thiết kế trước khi gọi `SecureRandom`

1. **Mục tiêu** là dẫn xuất mật khẩu, khởi tạo mã hóa, chống replay hay cấp quyền?
2. **Phạm vi uniqueness** là mỗi verifier, mỗi khóa, mỗi request hay mỗi token còn hiệu lực?
3. **Có cần khó đoán** không? Nonce dùng counter và challenge khó đoán có vai trò khác nhau.
4. **Có phải bí mật** không? Salt và IV phổ biến công khai; bearer token phải bảo vệ.
5. **Kích thước do ai quy định**? Mode/giao thức/entropy mục tiêu, không sử dụng một size cho mọi thứ.
6. **Cần lưu lại gì**? Salt + KDF parameters; nonce + ciphertext; challenge + context/status; token + scope/expiry/revocation.
7. **Thời hạn và tái sử dụng ra sao**? Salt dùng lại để verify; GCM nonce không tái dùng dưới cùng key; challenge thường one-time; token có vòng đời.
8. **Nhiều node và concurrency có thể gây lỗi nào**? Counter lặp, hai request cùng consume challenge/token, đổi khóa và version.
9. **Lộ giá trị gây hậu quả gì**? Lộ salt/nonce thường không trực tiếp cấp quyền, lộ bearer token có thể chiếm quyền.
10. **Đã dùng thư viện/giao thức chuẩn chưa**? Không tự chế một giao thức chỉ vì có CSPRNG và hash.

## 11. Tự kiểm tra (có đáp án)

**Câu 1.** Alice và Bob có cùng mật khẩu, dùng cùng password KDF nhưng salt khác nhau. Verifier có nhất thiết giống nhau không?

**Đáp án:** Không; với salt độc lập, verifier thông thường khác nhau, và đó là mục đích của salt. Xác minh phải dùng salt đã lưu của từng bản ghi.

**Câu 2.** Nonce GCM có thể công khai nên có thể dùng lại dưới cùng khóa không?

**Đáp án:** Không. Tính công khai và tính không lặp là hai yêu cầu độc lập; reuse nonce GCM có thể gây mất bảo mật nghiêm trọng.

**Câu 3.** Bộ đếm tăng dần có thể là nonce không?

**Đáp án:** Có trong giao thức/mode cho phép nonce dự đoán được, nếu quản lý để không lặp qua node/restart. Không thể tự suy rộng sang IV CBC hay mọi giao thức khác.

**Câu 4.** Client chỉ gửi lại challenge ngẫu nhiên mà server phát ra. Vậy đã chứng minh danh tính chưa?

**Đáp án:** Chưa. Cần chứng minh mật mã (ví dụ chữ ký) và server phải kiểm tra context, expiry, replay.

**Câu 5.** 32 byte random token sau khi Base64URL thành 43 ký tự có 344 bit entropy không?

**Đáp án:** Không. Entropy tối đa vẫn là 256 bit của 32 byte nguồn.

**Câu 6.** Salt và session token có thể lưu/truyền giống nhau không?

**Đáp án:** Không. Salt là tham số public của KDF, còn bearer token là secret credential; lộ bearer token có thể cấp quyền cho người lạ.

**Câu 7.** Vì sao hash SHA-256 có thể hợp lý khi lưu verifier của random token 256 bit nhưng không dùng trực tiếp cho password?

**Đáp án:** Token được sinh có entropy cao, gần như không thể đoán brute-force; mật khẩu con người thường có entropy thấp, cần password KDF chậm để chống tấn công offline.

## 12. Tài liệu tham khảo chính thức

- [Java 17 `SecureRandom` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html) — CSPRNG trong JCA.
- [Java 17 `Cipher` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html) — AES-GCM, AAD, tag và giải mã.
- [Java 17 `GCMParameterSpec` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html) — IV và độ dài tag GCM.
- [NIST SP 800-38D — GCM and GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final) — đặc tả GCM và ràng buộc IV/nonce.
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) — password KDF, salt và pepper.
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) — entropy và vòng đời session token.
- [OWASP Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html) — reset token, expiry và one-time.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-randomness-008">3.8 Thiết kế định dạng dữ liệu có phiên bản, độ dài và mã định danh thuật toán</a>

<details>
<summary>Xem chi tiết</summary>

# 3.8 Thiết kế định dạng dữ liệu có phiên bản, độ dài và mã định danh thuật toán

## 1. Vì sao dữ liệu mật mã cần một định dạng được đặc tả?

Một mảng byte sau khi mã hóa **không tự nói lên cách giải mã**. Ứng dụng cần biết dữ liệu thuộc phiên bản nào, dùng thuật toán và tham số nào, chọn khóa nào, nonce/IV nằm ở đâu, có bao nhiêu byte ciphertext và authentication tag. Nếu các thông tin này chỉ nằm trong mã nguồn hoặc được đoán từ dữ liệu, việc đọc lại dữ liệu cũ, chuyển hệ thống, luân chuyển khóa và xử lý bản ghi lỗi đều trở nên khó khăn.

Bài này thiết kế một **binary envelope** (bản ghi bao dữ liệu mật mã) phục vụ lưu trữ/truyền tải. Ba thành phần cốt lõi là:

- **Version**: quy định *cấu trúc và quy tắc diễn giải* byte của bản ghi.
- **Length**: cho phép xác định ranh giới trường một cách duy nhất, kiểm tra truncation/trailing bytes và giới hạn chi phí xử lý đầu vào.
- **Algorithm identifier**: ánh xạ một số nguyên ổn định sang một **bộ thuật toán + tham số cụ thể**, không phụ thuộc tên provider JDK.

Một định dạng tốt còn cần **magic**, **key identifier**, **nonce/IV**, quy tắc encoding/endianness, cách xác thực metadata và chính sách từ chối dữ liệu không hỗ trợ.

> Ví dụ bên dưới là **định dạng minh họa do ứng dụng tự định nghĩa**, không phải chuẩn file của Java SE, JCA hay RFC 5116. Trong dự án thực tế có thể dùng chuẩn giao thức/envelope đã được kiểm chứng (ví dụ JWE khi phù hợp) thay vì tự tạo giao thức mới. Mục tiêu của bài là hiểu cách đặc tả và triển khai cấu trúc byte không mơ hồ.

## 2. Tách format version, algorithm suite và key ID

| Trường | Trả lời câu hỏi | Không nên hiểu nhầm |
| --- | --- | --- |
| `magic` | “Đây có phải họ định dạng mà parser hiểu?” | Magic **không** phải chữ ký hay bằng chứng dữ liệu đáng tin. |
| `version` | “Các byte tiếp theo được bố trí và kiểm tra theo schema nào?” | Version của **file/protocol** không phải version Java hoặc độ mạnh mật mã. |
| `suite_id` | “Dùng thuật toán, mode, độ dài khóa, nonce và tag nào?” | Không được ánh xạ tùy ý sang chuỗi `Cipher.getInstance()` lấy trực tiếp từ file. |
| `key_id` (`kid`) | “Nên tra khóa ứng viên nào trong kho khóa tin cậy?” | ID **không phải khóa**, không bí mật, không được tự cấp quyền truy cập. |
| `nonce`/`IV` | “Giá trị khởi tạo duy nhất cho lần mã hóa bằng khóa này là gì?” | Không phải mật khẩu/salt; việc nonce có thể công khai **không** cho phép tái sử dụng với cùng khóa AES-GCM. |
| `length` | “Trường kế tiếp dài bao nhiêu byte?” | Độ dài đến từ dữ liệu ngoài và phải được kiểm tra **trước khi cấp phát**. |
| `ciphertext` + `tag` | “Dữ liệu đã mã hóa và bằng chứng toàn vẹn ở đâu?” | Chỉ được trả plaintext sau khi kiểm tra tag thành công. |

**Version và suite không thay thế nhau.** Có thể giữ `version=1` khi thêm một suite mới nếu cách ghi byte không đổi; khi thay thứ tự trường, encoding hoặc ý nghĩa trường bắt buộc, cần version/schema mới. Bảng ánh xạ `suite_id` là một phần của tài liệu giao thức: ID không được tái sử dụng để mang ý nghĩa khác.

Đối với ứng dụng lưu lâu dài, `kid` cho phép chọn khóa cũ trong thời gian đọc dữ liệu đã mã hóa sau khi hệ thống đổi khóa ghi mới. Khóa thật phải được lấy từ key store/KMS/HSM hoặc kho khóa đã xác thực; không lấy khóa từ chính bản ghi chưa đáng tin.

## 3. Đặc tả cụ thể: envelope nhị phân v1

Giả sử ứng dụng chỉ chấp nhận `suite_id = 0x01`, định nghĩa là:

- **AES-128-GCM**, JCA transformation `AES/GCM/NoPadding`.
- Khóa **16 byte** (128 bit), nonce **12 byte**, authentication tag **16 byte** (128 bit).
- Payload plaintext dài từ **0 đến 1.048.576 byte** (1 MiB).
- Ciphertext được nối với tag: `body = ciphertext || tag`, dài **plaintext_length + 16**.
- `key_id` là ASCII thuộc `[A-Za-z0-9._-]`, dài **1–32 byte**; ví dụ `key-A`.
- Tất cả độ dài trong bảng dưới là **số byte**, không phải số ký tự Unicode. Số nguyên nhiều byte ghi theo **big-endian (network byte order)**.

### 3.1 Layout chính xác

| Offset, tính từ 0 | Số byte | Trường | Quy tắc |
| --- | ---: | --- | --- |
| `0..3` | 4 | `magic` | ASCII `JENC` (`4A 45 4E 43` hex) |
| `4` | 1 | `version` | Unsigned `0x01` |
| `5` | 1 | `suite_id` | Unsigned `0x01` = AES-128-GCM/nonce-96/tag-128 |
| `6` | 1 | `kid_length` | Unsigned, phải trong `[1,32]` |
| `7` | 1 | `nonce_length` | Unsigned, phải bằng `12` trong v1/suite 1 |
| `8..11` | 4 | `body_length` | Unsigned 32-bit big-endian, phải trong `[16,1.048.592]` |
| `12..(11+k)` | `k` | `key_id` | ASCII hợp lệ, `k = kid_length` |
| `(12+k)..(23+k)` | 12 | `nonce` | 12 byte sinh cho lần mã hóa |
| `(24+k)..(23+k+c)` | `c` | `body` | Ciphertext nối tag 16 byte, `c = body_length` |

`fixed_header_length = 12`, `authenticated_header_length = 12 + k + 12 = 24 + k` và `total_record_length = authenticated_header_length + body_length`. Parser phải yêu cầu **đúng** số byte này; không âm thầm bỏ qua byte thừa.

```text
+----------+---------+----------+---------+-----------+----------------+
| MAGIC(4) | VER(1)  | SUITE(1) | KIDLEN(1)| NONCELEN(1)| BODYLEN(4 BE)  |
+----------+---------+----------+---------+-----------+----------------+
| KEY_ID (k bytes) | NONCE (12 bytes) | ENCRYPTED_BODY (c bytes: C || TAG16) |
+------------------+------------------+--------------------------------------+
<------------ Authenticated header / AES-GCM AAD ------------->
```

Ví dụ với plaintext ASCII `Hello Java` (10 byte) và `key_id = "key-A"` (5 byte): header dài `12 + 5 + 12 = 29` byte, `body` dài `10 + 16 = 26` byte, toàn bản ghi dài **55 byte**. Giá trị nonce, ciphertext và tag ngẫu nhiên theo lần mã hóa, nên không thể dự đoán toàn bộ hex output.

### 3.2 Bảng ID phải do code đáng tin kiểm soát

```text
version 1, suite 0x01:
  algorithm      = AES
  JCA transform  = AES/GCM/NoPadding
  key bits       = 128
  nonce bytes    = 12
  tag bits       = 128
  body encoding  = ciphertext || 16-byte tag
```

Nếu cần suite mới, cấp **ID mới** và viết đặc tả + bộ kiểm thử riêng. Không đặt chuỗi JCA như `"AES"`, `"AES/ECB/PKCS5Padding"` hoặc tên provider vào dữ liệu rồi gọi trực tiếp theo chuỗi đó: đầu vào có thể bị sửa để ép một thuật toán không an toàn hoặc không được triển khai. **Không fallback tự động** về thuật toán yếu hơn khi suite được yêu cầu không có.

Trong v1, `nonce_length` có mặt để minh họa trường độ dài và dễ kiểm tra cấu trúc; nó **không cho phép** thay nonce sang 8 hoặc 16 byte tùy ý. Giá trị hợp lệ vẫn cố định bằng 12 theo suite 1. Các giá trị có thể thay đổi phải được mô tả bằng giới hạn và thuật toán rõ ràng.

## 4. Metadata phải được xác thực cùng ciphertext

Đối với **AEAD** như AES-GCM, dữ liệu được chia thành:

- **Plaintext** `P`: được mã hóa và xác thực.
- **Associated Authenticated Data** (AAD) `A`: không mã hóa nhưng được tag xác thực.
- **Nonce** `N`: được gửi công khai; phải đáp ứng điều kiện duy nhất theo mỗi khóa.
- **Ciphertext + authentication tag** `C || T`: kết quả bên gửi; bên nhận chỉ chấp nhận khi kiểm tra tag thành công.

Trong envelope v1, ta đặt **nguyên byte header từ offset `0` đến hết nonce** vào AAD:

```text
A = MAGIC || VERSION || SUITE || KIDLEN || NONCELEN
    || BODYLEN || KEY_ID || NONCE

(C || T) = AES-GCM-Encrypt(key, nonce, P, A)
P       = AES-GCM-Decrypt(key, nonce, C || T, A)
```

Vì vậy nếu đối thủ sửa `version`, `suite_id`, `key_id`, `length` hoặc `nonce`, một bản ghi được chấp nhận phải vượt qua cả **kiểm tra cú pháp/allowlist** và **xác thực tag**. Một trường header không hợp lệ có thể bị parser từ chối trước khi tới bước xác thực; việc đọc trường ở giai đoạn này chỉ nhằm **định tuyến/kiểm tra**, chưa biến dữ liệu thành đáng tin.

Cần dùng **chính xác byte gốc** của header để tính AAD khi giải mã. Tránh giải mã thành đối tượng rồi tuần tự hóa lại AAD với thứ tự, case, charset hoặc dạng Unicode khác. Nếu có dữ liệu ngữ cảnh bên ngoài như tenant ID, object ID, mục đích sử dụng hay protocol domain, hai bên phải ràng buộc chúng vào AAD bằng encoding duy nhất (thường length-prefix + domain separator), để không thể chuyển một ciphertext hợp lệ sang ngữ cảnh khác.

> **AES-GCM không tự chống replay.** Kẻ tấn công có thể gửi lại *nguyên bản ghi hợp lệ* mà tag vẫn đúng. Nếu ứng dụng yêu cầu freshness/chống replay, cần thêm sequence number, record ID, timestamp theo chính sách và trạng thái theo dõi bản ghi đã dùng; đặt các trường ảnh hưởng tới quyết định vào AAD hoặc plaintext đã xác thực.

## 5. Ví dụ Java 17: tạo, đọc và xác thực envelope

Chương trình sau minh họa đầy đủ `seal()` và `open()` cho đúng **v1/suite 1**. Nó dùng `ByteBuffer` big-endian, `Byte.toUnsignedInt()`, giới hạn kích thước **trước khi cấp phát**, danh sách khóa tin cậy theo `kid`, và `Cipher.updateAAD()` **trước** `doFinal()`.

Tạo file **`EnvelopeDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class EnvelopeDemo {
    private static final byte[] MAGIC = "JENC".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final int SUITE_AES128_GCM = 1;
    private static final int FIXED_HEADER = 12;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int MAX_KID_BYTES = 32;
    private static final int MAX_PLAIN_BYTES = 1_048_576;
    private static final int MAX_BODY_BYTES = MAX_PLAIN_BYTES + TAG_BYTES;
    private static final SecureRandom RNG = new SecureRandom();

    private static boolean validKid(String kid) {
        return kid != null && kid.matches("[A-Za-z0-9._-]{1,32}");
    }

    private static void requireAes128(SecretKey key) throws GeneralSecurityException {
        if (key == null || !"AES".equalsIgnoreCase(key.getAlgorithm())
                || key.getEncoded() == null || key.getEncoded().length != 16) {
            throw new GeneralSecurityException("AES-128 key required");
        }
    }

    public static byte[] seal(byte[] plain, SecretKey key, String kid)
            throws GeneralSecurityException {
        requireAes128(key);
        if (plain == null || plain.length > MAX_PLAIN_BYTES || !validKid(kid)) {
            throw new GeneralSecurityException("Invalid input");
        }

        byte[] kidBytes = kid.getBytes(StandardCharsets.US_ASCII);
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);
        int bodyLength = plain.length + TAG_BYTES;

        ByteBuffer headerBuffer = ByteBuffer
                .allocate(FIXED_HEADER + kidBytes.length + NONCE_BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        headerBuffer.put(MAGIC);
        headerBuffer.put((byte) VERSION);
        headerBuffer.put((byte) SUITE_AES128_GCM);
        headerBuffer.put((byte) kidBytes.length);
        headerBuffer.put((byte) NONCE_BYTES);
        headerBuffer.putInt(bodyLength);
        headerBuffer.put(kidBytes);
        headerBuffer.put(nonce);
        byte[] header = headerBuffer.array();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(header);
        byte[] body = cipher.doFinal(plain);
        if (body.length != bodyLength) {
            throw new GeneralSecurityException("Unexpected ciphertext length");
        }

        byte[] record = Arrays.copyOf(header, header.length + body.length);
        System.arraycopy(body, 0, record, header.length, body.length);
        return record;
    }

    public static byte[] open(byte[] record, Map<String, SecretKey> keyRing)
            throws GeneralSecurityException {
        if (record == null || record.length < FIXED_HEADER
                || record.length > FIXED_HEADER + MAX_KID_BYTES
                        + NONCE_BYTES + MAX_BODY_BYTES) {
            throw new GeneralSecurityException("Invalid record size");
        }

        ByteBuffer buffer = ByteBuffer.wrap(record).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        int version = Byte.toUnsignedInt(buffer.get());
        int suite = Byte.toUnsignedInt(buffer.get());
        int kidLength = Byte.toUnsignedInt(buffer.get());
        int nonceLength = Byte.toUnsignedInt(buffer.get());
        int bodyLength = buffer.getInt();

        if (!Arrays.equals(magic, MAGIC) || version != VERSION
                || suite != SUITE_AES128_GCM) {
            throw new GeneralSecurityException("Unsupported format");
        }
        if (kidLength < 1 || kidLength > MAX_KID_BYTES
                || nonceLength != NONCE_BYTES
                || bodyLength < TAG_BYTES || bodyLength > MAX_BODY_BYTES) {
            throw new GeneralSecurityException("Invalid field length");
        }

        int headerLength = FIXED_HEADER + kidLength + nonceLength;
        if (record.length != headerLength + bodyLength) {
            throw new GeneralSecurityException("Length mismatch");
        }

        byte[] kidBytes = new byte[kidLength];
        buffer.get(kidBytes);
        String kid = new String(kidBytes, StandardCharsets.US_ASCII);
        if (!validKid(kid)) {
            throw new GeneralSecurityException("Invalid key ID");
        }
        byte[] nonce = new byte[nonceLength];
        buffer.get(nonce);

        SecretKey key = keyRing.get(kid);
        if (key == null) {
            throw new GeneralSecurityException("Unknown key ID");
        }
        requireAes128(key);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(record, 0, headerLength);
        return cipher.doFinal(record, headerLength, bodyLength);
    }

    private static String check(byte[] record, Map<String, SecretKey> keyRing) {
        try {
            open(record, keyRing);
            return "ACCEPTED";
        } catch (GeneralSecurityException e) {
            return "REJECTED";
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();
        Map<String, SecretKey> keyRing = Map.of("key-A", key);
        byte[] plain = "Hello Java".getBytes(StandardCharsets.US_ASCII);
        byte[] record = seal(plain, key, "key-A");

        System.out.println("Round-trip: " + Arrays.equals(plain, open(record, keyRing)));
        System.out.println("Record bytes: " + record.length);

        byte[] nonceChanged = record.clone();
        nonceChanged[FIXED_HEADER + "key-A".length()] ^= 1;
        System.out.println("Nonce modified: " + check(nonceChanged, keyRing));

        byte[] tagChanged = record.clone();
        tagChanged[tagChanged.length - 1] ^= 1;
        System.out.println("Tag modified: " + check(tagChanged, keyRing));

        byte[] truncated = Arrays.copyOf(record, record.length - 1);
        System.out.println("Truncated: " + check(truncated, keyRing));
    }
}
```

Biên dịch/chạy từ thư mục chứa file, dùng JDK 17+:

```bash
javac --release 17 EnvelopeDemo.java
java EnvelopeDemo
```

Stdout (nội dung ổn định dù khóa và nonce sinh ngẫu nhiên):

```text
Round-trip: true
Record bytes: 55
Nonce modified: REJECTED
Tag modified: REJECTED
Truncated: REJECTED
```

**Giải thích những điểm quan trọng:**

1. `KeyGenerator` tạo khóa AES-128 minh họa. Trong production, khóa sống ngoài bản ghi; `keyRing` là ánh xạ đáng tin `key_id → key`, dùng để đọc lại dữ liệu mã hóa bằng khóa cũ. Không xuất khóa ra file envelope.
2. `SecureRandom` sinh nonce 96-bit. Ví dụ phù hợp với số bản ghi hạn chế; **không bảo đảm tránh trùng nonce tuyệt đối** ở quy mô lớn hoặc nhiều máy. Với một khóa AES-GCM, phải có chiến lược bảo đảm nonce duy nhất, giới hạn lượng bản ghi và luân chuyển khóa theo nguy cơ va chạm; hệ thống nhiều writer cần phối hợp hoặc tách khóa.
3. `GCMParameterSpec(128, nonce)` cố định tag 128-bit; JCA `Cipher.doFinal()` trả `ciphertext || tag` khi mã hóa, kiểm tra tag khi giải mã. `updateAAD(header)` được gọi trước khi xử lý ciphertext.
4. `open()` kiểm tra kích thước tổng, magic/version/suite, độ dài, charset, key ID và **đúng toàn bộ số byte** trước khi giải mã. Không suy ra phần còn lại từ `available()` hoặc tin vào chiều dài chưa giới hạn.
5. Việc thay 1 byte nonce/tag khiến bản ghi không được chấp nhận; cắt mất 1 byte khiến `record.length` không khớp độ dài khai báo. Hai tình huống đầu chủ yếu thất bại ở **authentication tag**, tình huống cuối bị **parser** từ chối trước khi gọi cipher.
6. `requireAes128()` kiểm tra `key.getEncoded()` được vì ví dụ dùng khóa xuất được (`KeyGenerator`). Khóa HSM/KMS không xuất được thường trả `null`; khi dùng chúng, phải kiểm tra loại/kích thước bằng metadata từ nhà cung cấp khóa và tích hợp provider phù hợp, **không coi `getEncoded() == null` là khóa không hợp lệ**.
7. Đây là ví dụ bản ghi nhỏ, giữ toàn bộ ciphertext trong bộ nhớ. Dữ liệu lớn cần **chunked authenticated format** riêng, với chỉ số chunk/độ dài/tổng số chunk hoặc marker kết thúc được xác thực; không được tùy tiện cắt GCM ciphertext rồi tự giải mã từng mảnh như những bản ghi độc lập.

Chương trình tự khởi tạo `Cipher` mới cho mỗi bản ghi. Việc dùng lại đối tượng `Cipher` không được phép dẫn tới dùng lại cùng cặp **key + nonce**.

## 6. Giới hạn độ dài và chuẩn hóa byte là yêu cầu an toàn

### 6.1 Parse các trường không đáng tin theo thứ tự an toàn

Luồng đọc khuyến nghị:

1. **Kiểm tra tổng kích thước input** (ít nhất 12 byte và không quá giới hạn toàn bản ghi). Không dành bộ nhớ theo độ dài do file cung cấp khi chưa kiểm tra.
2. Đọc cố định 12 byte đầu để kiểm tra `magic`, `version`, `suite_id`, ba trường độ dài; dùng **unsigned** cho trường 1 byte.
3. Kiểm tra mọi `length` với **cận trên do giao thức đặt ra**, so với số byte thực tế còn lại và kiểm tra phép cộng có khả năng overflow trước khi cấp phát.
4. Chỉ khi đã chứng minh `record.length == fixed_header + kid_length + nonce_length + body_length` mới trích `key_id`, `nonce` và body.
5. Tra khóa ứng viên từ nguồn tin cậy, kiểm tra key/suite, đặt AAD đúng byte gốc, **xác minh tag thành công** rồi mới chuyển plaintext cho logic nghiệp vụ.
6. Không sử dụng riêng trạng thái header chưa xác thực để đưa ra quyết định cấp quyền hay ghi dữ liệu lâu dài.

Ở ví dụ, `body_length` lưu trên 4 byte unsigned nhưng code Java đọc `int` có dấu. Do giới hạn hợp lệ nhỏ hơn 2 GiB, mọi giá trị có bit dấu bật sẽ bị loại bởi phép so sánh phạm vi; không cần cấp phát bộ nhớ theo số unsigned rất lớn. Nếu cần định dạng hỗ trợ hơn 2 GiB, thiết kế một **streaming parser** và phép tính kích thước `long` có kiểm soát riêng.

### 6.2 Encoding phải duy nhất

Các quy tắc cần cố định trong đặc tả, không dựa vào mặc định hệ thống:

- `ByteOrder.BIG_ENDIAN` hay một thứ tự byte được nêu rõ. Tránh `DataInputStream`/`ByteBuffer` và ngôn ngữ khác diễn giải không đồng nhất.
- `length` luôn đo **byte của encoding đã chọn**, không dùng `String.length()` thay số byte UTF-8.
- Với chuỗi tùy biến, chọn **UTF-8 chuẩn**, nêu rõ cách xử lý chuỗi lỗi encoding, normalization Unicode, quy tắc case, thứ tự trường. Ví dụ v1 giới hạn `key_id` thành ASCII để loại bỏ nhập nhằng.
- Không cho phép nhiều encoding khác nhau cùng đại diện một bản ghi ngữ nghĩa nếu định dạng dùng cho chữ ký, hashing/canonicalization; tránh field trùng, thứ tự không xác định, whitespace/case linh động.
- Không thêm byte cuối file, bỏ qua unknown field, hay tự thay `version=0` khi parse thất bại.

Khi đóng gói các trường metadata thay đổi độ dài để tính AAD hoặc chữ ký, phải có ranh giới rõ ràng, ví dụ `type || length || value` với quy tắc duy nhất. Ghép `userId || tenantId` trực tiếp có thể nhập nhằng (`"ab" + "c"` bằng `"a" + "bc"`). Nếu dùng TLV/CBOR/Protocol Buffers, phải quy định xử lý trường bắt buộc, field trùng, thứ tự/canonical encoding và khả năng tương thích cụ thể; **format version** vẫn cần định nghĩa ý nghĩa cấu trúc.

### 6.3 Kích thước ghi trong header chưa đồng nghĩa nội dung đã được xác thực

`body_length` giúp biết cần bao nhiêu byte để parse, nhưng trước khi xác minh tag, đối thủ có thể sửa nó. Vì vậy:

- **Bound trước, authenticate sau:** dùng length để từ chối ngay những bản ghi sai/hại tài nguyên, rồi bao length trong AAD để bảo vệ ý nghĩa của dữ liệu được chấp nhận.
- Tránh `new byte[untrustedLength]` trước khi kiểm tra `0 <= length <= MAX`.
- Tránh lấy `ciphertext.length` thay cho trường độ dài khi giao thức yêu cầu kiểm tra tính nhất quán; phải phát hiện file bị cắt/thêm byte.
- Với file lưu trữ lớn, phải có quy tắc chống quá tải khi giải mã, nén/decompress (nếu có), và parser streaming thay vì giữ toàn bộ trong RAM.

## 7. Nâng cấp phiên bản, đổi thuật toán và vòng đời khóa

**Trường hợp 1: thay thuật toán nhưng giữ layout.** Nếu ngày mai hệ thống hỗ trợ suite khác, cấp ID mới và chỉ đưa vào allowlist sau khi có đặc tả và test vector. `suite_id=2` chẳng hạn phải nêu rõ JCA transformation, kích thước khóa/nonce/tag, format body và kiểm thử; không được đọc `suite_id=2` rồi tự đoán thuật toán từ tên hoặc mặc định provider.

**Trường hợp 2: đổi bố cục bản ghi.** Ví dụ cần `context_id`, `created_at`, `KDF parameters` hoặc nhiều đoạn ciphertext. Thiết kế `version=2` với layout và parser riêng. Các byte version và trường metadata của mỗi phiên bản phải được xác thực trong phạm vi AAD hoặc chữ ký tương ứng. Không tự động đoán version dựa trên độ dài, không chấp nhận thiếu trường bắt buộc và không cho phép downgrade “thử lại v1 cho tới khi chạy được”.

**Trường hợp 3: xoay khóa.** Tạo khóa ghi mới, cấp `key_id` mới, giữ khóa đọc cũ trong thời gian cần đọc dữ liệu lịch sử. Nếu giải mã rồi mã hóa lại, tạo **nonce mới cho khóa mới**, xác thực bản ghi cũ trước, ghi/đổi trạng thái theo cách tránh mất dữ liệu; sau khi hoàn tất di trú có thể xóa quyền đọc khóa cũ theo chính sách. `key_id` phải gắn với **tenant/purpose/quyền sử dụng** trong kho khóa đáng tin, không cho phép định tuyến chéo tùy ý.

**Trường hợp 4: mã hóa từ mật khẩu thay vì khóa đã tạo.** Một format khác có thể cần **KDF ID, salt, iteration/work factor và thông số KDF** để tái tạo khóa. Những trường này phải có encoding, độ dài, giới hạn chi phí và khả năng xác thực rõ ràng. **Salt của KDF** và **nonce của AEAD** là hai giá trị khác mục đích; không lấy một giá trị thay bừa cho giá trị kia. Không đưa mật khẩu, raw key hay thông tin bí mật nhạy cảm vào header.

**Chống downgrade:** Cho phép đọc v1 vì dữ liệu lịch sử là một **chính sách tương thích**; chấp nhận một suite lỗi thời trong giao thức mới là quyết định bảo mật khác. Viết mới bằng phiên bản/suite đã phê duyệt, chỉ cho phép legacy trong phạm vi cần thiết, ghi nhận thời điểm hết hỗ trợ.

## 8. Test bắt buộc đối với bộ đọc/ghi

| Trường hợp | Kết quả kỳ vọng | Vì sao |
| --- | --- | --- |
| Round-trip plaintext rỗng, ngắn và tối đa 1 MiB | Dữ liệu sau giải mã khớp từng byte | Biên độ body hợp lệ từ 16 byte tag đến giới hạn đã công bố |
| Sửa một byte `nonce`, `key_id` hoặc body/tag | Bị từ chối | Header và ciphertext/tag không thể bị tráo đổi âm thầm |
| Sửa `magic`, `version` hoặc `suite_id` sang giá trị không hỗ trợ | Bị từ chối ngay | Không thử fallback ngầm |
| `kid_length=0`, `kid_length=255`, `nonce_length != 12` | Bị từ chối trước cấp phát | Bounds và invariant của suite |
| `body_length` âm khi đọc bằng `int`, lớn quá mức hoặc nhỏ hơn tag | Bị từ chối trước cấp phát | Không bị integer overflow/memory DoS |
| File thiếu 1 byte hoặc có 1 byte thừa | Bị từ chối | Ranh giới bản ghi chính xác |
| `key_id` lạ hoặc key sai | Không giải mã được | Chỉ đọc khóa thuộc keyring tin cậy |
| Key đúng nhưng context bên ngoài sai (nếu format có context binding) | Authentication thất bại | Ngăn chuyển bản ghi giữa ngữ cảnh |
| Nhiều bản ghi cùng key/nonce | Phải ngăn tại phía ghi và kiểm tra chiến lược nonce | AES-GCM yêu cầu nonce duy nhất với mỗi khóa |
| V2/schema mới hoặc suite mới | Chỉ parser đúng phiên bản hỗ trợ mới chấp nhận | Nâng cấp có kiểm soát và tương thích rõ ràng |

Ngoài unit test, nên có **test vector** chứa header hex, plaintext, key thử nghiệm, nonce cố định **chỉ trong test**, ciphertext/tag kỳ vọng; kiểm thử giữa Java và ngôn ngữ khác nếu giao thức liên nền tảng. Không dùng key/nonce cố định từ test vector để mã hóa thực tế.

## 9. Những lỗi thiết kế thường gặp

1. **Không có version:** dữ liệu cũ được giải mã bằng format hoặc thuật toán mặc định mới và không thể khôi phục khi hệ thống nâng cấp.
2. **Chỉ ghi tên `"AES"` hoặc `"RSA"`:** thiếu mode, padding, kích thước khóa, tag/nonce; provider có thể có mặc định khác. Mã hóa có xác thực nên chọn transformation rõ ràng như `AES/GCM/NoPadding`.
3. **Nhận chuỗi thuật toán từ file và gọi thẳng JCA:** cho dữ liệu chưa xác thực quyết định thuật toán. Dùng **numeric suite ID → allowlist cố định**.
4. **Độ dài không giới hạn hoặc đo sai đơn vị:** parser có thể cấp phát quá mức, đọc lệch khung hoặc chấp nhận trường bị cắt. Dùng byte lengths, unsigned parsing, checked arithmetic, exact-consumption.
5. **Metadata không nằm trong AAD/chữ ký:** có thể tráo `key_id`, version, context hoặc tham số mà không được bảo vệ đầy đủ; phải ràng buộc đúng byte.
6. **Tái dùng nonce AES-GCM với cùng khóa:** làm suy yếu nghiêm trọng bảo mật và tính toàn vẹn. Không sinh bằng `Random`; `SecureRandom` cũng cần giới hạn số bản ghi/chiến lược tránh trùng thích hợp.
7. **Trả plaintext từng phần trước khi xác minh tag:** dữ liệu có thể bị lợi dụng dù cuối cùng tag sai. Chỉ công bố kết quả sau khi `doFinal` xác thực thành công.
8. **Coi Base64, magic, CRC hoặc checksum là bảo mật:** chúng hỗ trợ vận chuyển/phát hiện lỗi ngẫu nhiên, **không thay thế authentication tag/chữ ký**.
9. **Dùng Java object serialization làm wire format tùy biến:** phụ thuộc class/JDK, tăng rủi ro giải tuần tự hóa và không có schema mật mã rõ ràng. Dùng định dạng nhị phân có phiên bản và kiểm tra biên.
10. **Nhầm xác thực với chống replay hay xác thực danh tính người gửi:** AES-GCM chứng minh bản ghi hợp lệ với người giữ khóa; cần cơ chế khác để chống gửi lại hoặc chứng minh một bên đã ký.

## 10. Checklist trước khi lưu trữ hoặc công bố format

- [ ] Có đặc tả byte chính thức: magic, version, suite registry, thứ tự trường, endianness, charset, độ dài/giới hạn và quy tắc lỗi.
- [ ] Mỗi suite quy định đầy đủ algorithm/mode/padding, loại và kích thước khóa, nonce, tag, format ciphertext.
- [ ] Toàn bộ header quan trọng được đưa vào AAD (hoặc chữ ký); context/purpose cần ràng buộc cũng được xác thực.
- [ ] Parser reject unknown version/suite, malformed/truncated/trailing bytes, duplicate fields và mọi length ngoài giới hạn **trước khi cấp phát lớn**.
- [ ] Không dùng bản ghi làm nguồn tin cậy cho khóa hoặc quyền; key ID chỉ tra khóa ứng viên trong registry đáng tin.
- [ ] Chiến lược nonce uniqueness, key rotation, compatibility và chính sách cấm downgrade đã được thiết kế rõ.
- [ ] Có test vector, test biên, kiểm thử tamper, test nhiều runtime/provider và chính sách xử lý replay nếu cần.
- [ ] Quy trình ghi dữ liệu đủ tin cậy khi restart/crash; với chunked format, thứ tự, số chunk và điểm kết thúc đều được bảo vệ.

## Tài liệu gốc

- [RFC 5116 — An Interface and Algorithms for Authenticated Encryption](https://www.rfc-editor.org/rfc/rfc5116.html): biểu diễn AEAD, AAD không mơ hồ, nonce uniqueness; định nghĩa AES-128-GCM với nonce 12 byte và tag 16 byte.
- [NIST SP 800-38D — Galois/Counter Mode (GCM) and GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final): yêu cầu về GCM, IV/nonce và xác thực.
- [Java SE 17 — `javax.crypto.Cipher`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): transformation, `updateAAD`, `doFinal` và authentication tag.
- [Java SE 17 — `GCMParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): `tLen` tính bằng bit, IV/nonce truyền cho GCM.
- [Java SE 17 — `ByteBuffer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): quy tắc đọc/ghi byte và thứ tự byte.

**Ghi nhớ:** Một format mật mã an toàn phải vừa **tự mô tả đủ để đọc**, vừa **không tin dữ liệu tự mô tả cho tới khi đã kiểm tra ranh giới và xác thực**. Version, length và algorithm ID là nền móng cho khả năng tiến hóa, còn AAD/tag, nonce và kiểm tra parser là nền móng cho an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)
