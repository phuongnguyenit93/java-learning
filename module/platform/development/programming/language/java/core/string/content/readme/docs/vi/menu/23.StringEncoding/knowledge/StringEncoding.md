<a id="back-to-top"></a>

# Chuỗi và byte

## Menu
- [Phân biệt encode chuỗi thành byte với decode byte thành chuỗi](#string-encoding-001)
- [getBytes với Charset hoặc StandardCharsets](#string-encoding-002)
- [new String(byte&#91;&#93;,Charset) và giải mã một vùng byte](#string-encoding-003)
- [Độ dài UTF-8 không bằng length của String](#string-encoding-004)
- [Charset mặc định và ảnh hưởng môi trường hoặc phiên bản JDK](#string-encoding-005)
- [Dữ liệu sai mã, ký tự thay thế và mất thông tin](#string-encoding-006)
- [CharsetEncoder, CharsetDecoder và chính sách REPORT](#string-encoding-007)
- [Đối chiếu chuỗi qua ranh giới tệp, console và dữ liệu truyền](#string-encoding-008)
- [Vì sao Base64, escape và chuyển charset là các thao tác khác nhau](#string-encoding-009)

## <a id="string-encoding-001">Phân biệt encode chuỗi thành byte với decode byte thành chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt encode chuỗi thành byte và decode byte thành chuỗi trong Java

Khi Java trao đổi văn bản với file, mạng, HTTP, cơ sở dữ liệu hay hệ thống khác, dữ liệu thường phải đi qua ranh giới giữa **`String` (ký tự)** và **`byte[]` (byte)**. Hai thao tác đi theo **hai hướng ngược nhau**:

~~~text
                         ENCODE (mã hóa ký tự)
String (ký tự Unicode) ------------------------> byte[]
       ^                                           |
       |                 DECODE                    |
       +-------------------------------------------+
                 (giải mã byte thành ký tự)

Ví dụ với charset UTF-8:
"café" --encode--> 63 61 66 C3 A9 --decode--> "café"
~~~

Trong bài này, **encode** nghĩa là *character encoding* (chuyển ký tự sang byte theo một bộ mã ký tự, `Charset`); **decode** là đảo chiều chuyển byte sang ký tự theo `Charset`. Đây **không phải mã hóa bảo mật** (encryption/decryption). Nếu muốn bảo mật dữ liệu, cần các cơ chế mật mã riêng; chuyển mã ký tự không làm dữ liệu trở thành bí mật.

## 1. Định nghĩa và API Java

| Nội dung | Encode: chuỗi → byte | Decode: byte → chuỗi |
| --- | --- | --- |
| Đầu vào | `String` (văn bản) | `byte[]` (dữ liệu nhị phân) |
| Đầu ra | `byte[]` | `String` |
| Quy tắc chuyển đổi | Dùng `CharsetEncoder` | Dùng `CharsetDecoder` |
| API thường dùng | `text.getBytes(charset)` | `new String(bytes, charset)` |
| Ví dụ `UTF-8` | `"é"` → `C3 A9` | `C3 A9` → `"é"` |
| Sai cách xử lý | Ký tự không biểu diễn được có thể bị thay thế hoặc báo lỗi | Dãy byte lỗi có thể bị thay thế hoặc báo lỗi |

Một **charset** quy định cách biểu diễn ký tự bằng dãy byte và cách khôi phục ký tự từ dãy byte. Trong Java, dùng kiểu `java.nio.charset.Charset`, đặc biệt các hằng chuẩn ở `java.nio.charset.StandardCharsets`:

- `StandardCharsets.UTF_8`: bộ mã Unicode phổ biến, có số byte biến đổi theo ký tự, tương thích ASCII cho các ký tự ASCII.
- `StandardCharsets.UTF_16BE`, `UTF_16LE`: UTF-16 với thứ tự byte xác định.
- `StandardCharsets.UTF_16`: UTF-16 có quy tắc về BOM/thứ tự byte khác với hai biến thể chỉ rõ endian.
- `StandardCharsets.US_ASCII`: phạm vi ASCII, không biểu diễn được mọi chữ tiếng Việt.
- `StandardCharsets.ISO_8859_1`: bộ mã một byte cho tập ký tự Latin-1; không phải Unicode đầy đủ.

**`String` là văn bản Unicode trong Java**, còn các byte là **biểu diễn bên ngoài** theo charset. Không suy ra byte mã hóa từ cách JVM lưu trữ nội bộ `String`. Cùng một `String` có thể tạo ra các dãy byte **khác nhau** khi dùng UTF-8, UTF-16 hay ISO-8859-1.

## 2. Encode: từ `String` sang `byte[]`

Phương thức quen thuộc là:

~~~java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
~~~

Quá trình encode đọc nội dung ký tự của `text` và sinh dãy byte theo UTF-8. Ví dụ:

- Chữ `A` (U+0041) → byte UTF-8 `41`.
- Chữ `é` (U+00E9) → hai byte `C3 A9`.
- Emoji `😀` (U+1F600) → bốn byte `F0 9F 98 80`.

Một ký tự hiển thị **không nhất thiết bằng một byte**. Thậm chí `String.length()` trong Java trả về **số đơn vị UTF-16**, không phải số Unicode code point, số ký tự người dùng nhìn thấy hay số byte sau encode. Emoji `😀` là một code point nhưng chiếm **hai đơn vị UTF-16** và **bốn byte UTF-8**.

### Ví dụ Java 17 chạy độc lập

~~~java
// EncodingDemo.java
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class EncodingDemo {
    public static void main(String[] args) {
        String original = "Aé😀";
        byte[] utf8 = original.getBytes(StandardCharsets.UTF_8);

        System.out.println("UTF-8 hex = "
                + HexFormat.of().withUpperCase().formatHex(utf8));
        System.out.println("UTF-16 units = " + original.length());
        System.out.println("Unicode code points = "
                + original.codePointCount(0, original.length()));
        System.out.println("UTF-8 bytes = " + utf8.length);

        String restored = new String(utf8, StandardCharsets.UTF_8);
        System.out.println("Round-trip equal = " + original.equals(restored));
    }
}
~~~

Biên dịch/chạy với JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 EncodingDemo.java
java EncodingDemo
~~~

Kết quả:

~~~text
UTF-8 hex = 41C3A9F09F9880
UTF-16 units = 4
Unicode code points = 3
UTF-8 bytes = 7
Round-trip equal = true
~~~

`-encoding UTF-8` trong lệnh `javac` chỉ xác định **charset của tệp mã nguồn `.java`**. Tham số `StandardCharsets.UTF_8` trong chương trình mới xác định **charset khi encode/decode dữ liệu**. Đây là hai vị trí khác nhau, dù đều sử dụng UTF-8 trong ví dụ.

`HexFormat` (có từ Java 17) chỉ **hiển thị byte dưới dạng chữ số thập lục phân để kiểm tra**. Dãy `41 C3 A9 F0 9F 98 80` vẫn là bảy byte; không phải bảy ký tự của một chuỗi văn bản gốc.

## 3. Decode: từ `byte[]` sang `String`

Phương thức thường dùng:

~~~java
String text = new String(bytes, StandardCharsets.UTF_8);
~~~

Decoder phải biết **cách hiểu các byte**. Bản thân `byte[]` không lưu nhãn “tôi đang ở UTF-8”; nếu không có giao ước hoặc metadata bên ngoài, máy nhận **không thể bảo đảm biết đúng charset** chỉ bằng cách nhìn mảng byte.

Ví dụ với dãy byte `63 61 66 C3 A9`:

- Giải mã bằng **UTF-8** → `"café"`.
- Giải mã bằng **ISO-8859-1** → `"cafÃ©"` (hai byte `C3` và `A9` được coi là hai ký tự Latin-1).

Các byte **không đổi**, cách diễn giải chúng thành ký tự **đổi**. Hiện tượng chữ bị hỏng do encode/decode nhầm charset gọi là **mojibake**.

### Ví dụ: cùng byte, hai cách giải mã

~~~java
// CharsetMismatchDemo.java
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class CharsetMismatchDemo {
    public static void main(String[] args) {
        String original = "café";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);

        String correct = new String(bytes, StandardCharsets.UTF_8);
        String wrong = new String(bytes, StandardCharsets.ISO_8859_1);

        System.out.println("Bytes = "
                + HexFormat.of().withUpperCase().formatHex(bytes));
        System.out.println("Correct = " + correct);
        System.out.println("Wrong = " + wrong);
        System.out.println("Correct equal = " + original.equals(correct));
        System.out.println("Wrong equal = " + original.equals(wrong));
    }
}
~~~

Chạy `javac --release 17 -encoding UTF-8 CharsetMismatchDemo.java` và `java CharsetMismatchDemo`. Trên terminal hiển thị Unicode đúng, kết quả là:

~~~text
Bytes = 636166C3A9
Correct = café
Wrong = cafÃ©
Correct equal = true
Wrong equal = false
~~~

Nếu terminal hoặc hệ thống log còn sử dụng charset hiển thị khác, chữ in ra màn hình có thể tiếp tục bị biến dạng. Khi điều tra lỗi, hãy xem **hex của byte đầu vào**, **charset được khai báo**, **chuỗi sau decode**, và **charset của nơi xuất ra màn hình** như những bước riêng.

## 4. Vì sao phải dùng cùng charset?

Mục tiêu thường là:

~~~text
String ban đầu
   | encode theo charset X
   v
byte[]
   | lưu/truyền nguyên vẹn
   v
byte[]
   | decode theo charset X
   v
String khôi phục
~~~

Với dữ liệu Unicode hợp lệ và UTF-8, round-trip thông thường giữ nguyên nội dung:

~~~java
String input = "Tiếng Việt và 😀";
byte[] wire = input.getBytes(StandardCharsets.UTF_8);
String output = new String(wire, StandardCharsets.UTF_8);
System.out.println(input.equals(output)); // true
~~~

Tuy nhiên, “encode rồi decode cùng charset là luôn khôi phục 100%” **không đúng vô điều kiện**:

1. **Charset có thể không biểu diễn được ký tự**: ví dụ `US_ASCII` không biểu diễn được `é` hay tiếng Việt có dấu. Nếu API dùng chế độ thay thế, dữ liệu gốc bị mất ngay trong bước encode.
2. **Chuỗi Java có thể chứa surrogate UTF-16 không ghép cặp**: chuỗi đó không thể biểu diễn trực tiếp như các Unicode scalar value hợp lệ trong UTF-8; encoder có thể thay thế hoặc báo lỗi.
3. **Dãy byte có thể bị hỏng hoặc cắt giữa chừng** khi truyền/lưu, khiến decode sai hoặc lỗi.
4. **Hai đầu dùng khác charset** sẽ diễn giải byte không tương thích.
5. **Có bước biến đổi khác** giữa hai phía (chuẩn hóa Unicode, chuyển xuống dòng, sửa nội dung, thay thế ký tự...) khiến hai `String` khác nhau dù charset đúng.

UTF-8 là lựa chọn mặc định tốt cho giao thức/tệp văn bản mới khi hai phía đều thống nhất dùng UTF-8. Với dữ liệu di sản dùng charset khác, phải tuân theo charset **thực sự được dùng khi tạo byte**, không tự đổi sang UTF-8 tại bước decode.

## 5. Khi encode/decode không thể giữ nguyên dữ liệu

Ngay cả khi viết đúng hướng chuyển đổi, kết quả khứ hồi chưa chắc bằng bản gốc nếu dữ liệu không hợp lệ hoặc charset không biểu diễn được ký tự. Phân biệt ba tình huống:

| Tình huống | Điều xảy ra | Cách xử lý |
| --- | --- | --- |
| `"café"` được encode bằng US-ASCII | `é` không có mã tương ứng; `getBytes(US_ASCII)` thường thay bằng byte dấu `?` | Chọn charset biểu diễn được dữ liệu hoặc dùng `CharsetEncoder` với `REPORT` |
| Byte UTF-8 `C3 28` được decode bằng UTF-8 | Byte `C3` thiếu continuation hợp lệ; `new String` thường thêm U+FFFD (`�`) | Dùng `CharsetDecoder` với `REPORT` nếu phải từ chối byte lỗi |
| Byte UTF-8 `C3 A9` bị decode bằng ISO-8859-1 | Byte có thể hợp lệ nhưng tạo `Ã©` thay vì `é` (mojibake) | Xác nhận lại charset nguồn; kiểm tra byte gốc trước khi chuyển |

Các constructor/phương thức tiện lợi thường dùng cơ chế **thay thế**, có thể làm mất dữ liệu mà không ném lỗi. Ví dụ nhỏ sau phân biệt một decoder mềm và một decoder nghiêm ngặt:

~~~java
byte[] invalid = {(byte) 0xC3, (byte) 0x28};
String lenient = new String(invalid, StandardCharsets.UTF_8);
System.out.println(lenient.codePointAt(0) == 0xFFFD); // true

try {
    StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(invalid));
} catch (CharacterCodingException ex) {
    System.out.println(ex.getClass().getSimpleName()); // MalformedInputException
}
~~~

Đây là đoạn đặt trong phương thức, với import `java.nio.ByteBuffer`, `java.nio.charset.CharacterCodingException`, `CodingErrorAction` và `StandardCharsets`. Bài **Dữ liệu sai mã, ký tự thay thế và mất thông tin** phân tích tình huống mất dữ liệu; bài **CharsetEncoder, CharsetDecoder và chính sách REPORT** giải thích hai API này, kể cả xử lý từng chunk qua `CoderResult`.

## 6. Charset mặc định không phải hợp đồng trao đổi dữ liệu

`text.getBytes()` và `new String(bytes)` dùng charset mặc định của JVM. Trên JDK 17 trở xuống, charset mặc định thường phụ thuộc hệ điều hành/locale; từ JDK 18, JEP 400 quy định UTF-8 trong cấu hình thông thường, nhưng dữ liệu cũ hoặc chế độ tương thích vẫn cần được xem xét. `Charset.defaultCharset()` cho biết giá trị thực tế của JVM đang chạy.

Trong giao thức/tệp mới đã chọn UTF-8, hãy luôn viết `getBytes(StandardCharsets.UTF_8)` và `new String(bytes, StandardCharsets.UTF_8)`. Nếu dữ liệu nguồn dùng charset khác, phải **decode bằng charset nguồn** thay vì tùy tiện ép UTF-8. Bài **Charset mặc định và ảnh hưởng môi trường hoặc phiên bản JDK** trình bày chi tiết JEP 400, các API chịu ảnh hưởng và cách kiểm thử khi nâng cấp.

## 7. Áp dụng encode/decode tại ranh giới thực tế

Ví dụ dữ liệu văn bản đi qua file hoặc mạng:

~~~text
String nguồn
   | getBytes(UTF_8)
   v
byte[] -- ghi file / truyền mạng / lưu nguyên byte --> byte[]
   | new String(bytes, UTF_8)
   v
String phía nhận
~~~

- Với file văn bản, `Files.writeString(path, text, StandardCharsets.UTF_8)` và `Files.readString(path, StandardCharsets.UTF_8)` định nghĩa hai chiều ngay ở API I/O. Khi so sánh toàn vẹn byte, dùng `Files.write`/`readAllBytes` rồi so sánh `byte[]`.
- Với HTTP/JSON, xác định charset theo **đặc tả định dạng hoặc giao thức** và chỉ đo `Content-Length` từ body byte thực sự được gửi đi. JSON escaping, phần header hay nén là các bước riêng, có thể làm kích thước cuối khác kích thước chuỗi ban đầu.
- Với stream, một ký tự UTF-8 nhiều byte có thể bị chia giữa hai lần đọc. Dùng `InputStreamReader` với charset đã chọn hoặc decoder giữ trạng thái; decode mỗi mảnh byte riêng lẻ có thể sinh ký tự thay thế.

Bài **Đối chiếu chuỗi qua ranh giới tệp, console và dữ liệu truyền** đưa ra bài chạy đầy đủ để kiểm tra byte tại nhiều điểm, phân biệt lỗi dữ liệu với lỗi hiển thị ở terminal.

## 8. Một vài yếu tố ngoài charset cần phân biệt

- **BOM**: UTF-8 BOM `EF BB BF` thường xuất hiện dưới dạng U+FEFF sau decode; chỉ xử lý theo quy tắc của định dạng tệp. UTF-16BE/LE cần thỏa thuận về byte order và cách dùng BOM.
- **Unicode normalization**: `é` có thể là U+00E9 hoặc `e` + U+0301. Hai cách biểu diễn có thể trông giống nhưng vẫn khác byte; charset không tự chuẩn hóa.
- **Biểu diễn nhị phân**: ảnh, tệp ZIP, âm thanh không tự nhiên là văn bản. Decode chúng thành `String` có thể làm mất byte, kể cả khi encode ngược.
- **Base64 và escaping**: Base64 chuyển byte thành các ký tự ASCII để vận chuyển; escape bảo đảm cú pháp trong Java/JSON/HTML. Cả hai đều khác chuyển mã ký tự.

## 9. Checklist trước khi tích hợp

1. Bên tạo dữ liệu đang có **văn bản** hay **byte nhị phân**?
2. Charset nguồn là gì, và nó được ghi ở đâu trong giao thức/hợp đồng?
3. Trước khi giải mã, byte có được giữ nguyên và có cần từ chối đầu vào malformed không?
4. Trước khi mã hóa, charset đích có biểu diễn được mọi ký tự cần gửi không?
5. Khi có khác biệt, đã kiểm tra byte hex, code point và charset của lớp hiển thị riêng rẽ chưa?

## 10. Phân biệt đường đi đúng và sai

~~~java
String input = "café";
byte[] utf8 = input.getBytes(StandardCharsets.UTF_8);
String right = new String(utf8, StandardCharsets.UTF_8);
String wrong = new String(utf8, StandardCharsets.ISO_8859_1);

System.out.println(input.equals(right)); // true
System.out.println(input.equals(wrong)); // false
~~~

Đây là đoạn minh họa trong phương thức. Hai lời gọi `new String` đọc **cùng một dãy byte**; chỉ charset khác nhau. Vì thế cần bắt đầu từ hợp đồng byte nguồn, sau đó xác thực từng lớp biến đổi khi ứng dụng đi qua file, mạng hoặc môi trường mới.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** `"é".getBytes(StandardCharsets.UTF_8)` sinh bao nhiêu byte? Nếu decode chúng bằng `ISO_8859_1` thì ra chuỗi nào?

**Đáp án:** Hai byte `C3 A9`; decode Latin-1 tạo `"Ã©"` vì hai byte được hiểu là hai ký tự riêng.

**Câu 2.** Vì sao `"😀".length()` bằng `2` trong Java nhưng encode UTF-8 tạo `4` byte?

**Đáp án:** `String.length()` đếm **hai đơn vị UTF-16** biểu diễn một code point U+1F600, còn UTF-8 biểu diễn code point đó bằng **bốn byte**.

**Câu 3.** Khi Java đọc hai khối byte của một socket, tại sao không nên dùng `new String(chunk, UTF_8)` riêng cho từng khối?

**Đáp án:** Một ký tự UTF-8 nhiều byte có thể vắt qua ranh giới khối; decoder độc lập cho từng khối coi byte thiếu là lỗi. Dùng decoder có trạng thái hoặc `InputStreamReader`.

**Câu 4.** Encode `"café"` bằng US-ASCII có thể gây vấn đề gì? Muốn báo lỗi thay vì thay thế ký tự, nên dùng gì?

**Đáp án:** `é` không thuộc ASCII; API encode mềm có thể thay thế nó, làm mất dữ liệu. Dùng `CharsetEncoder` và `onUnmappableCharacter(CodingErrorAction.REPORT)`.

**Câu 5.** Nếu byte của một file ZIP được đưa vào `String` bằng UTF-8 rồi encode lại, có đảm bảo giữ nguyên file gốc không?

**Đáp án:** Không. ZIP là dữ liệu nhị phân, có thể chứa chuỗi byte không hợp lệ theo UTF-8 và decoder sẽ thay thế. Giữ nguyên dạng byte hoặc byte stream.

## Ghi nhớ

**Encode** là `String → byte[]` theo charset; **decode** là `byte[] → String` theo charset. Muốn khôi phục đúng dữ liệu, phải có **byte nguyên vẹn**, **charset thống nhất**, và **chính sách xử lý lỗi phù hợp**. Dùng `StandardCharsets.UTF_8` tường minh cho dữ liệu văn bản mới khi giao thức cho phép; kiểm tra byte bằng hex khi xảy ra lỗi; dùng `CharsetEncoder`/`CharsetDecoder` ở chế độ `REPORT` khi mất dữ liệu là không chấp nhận được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-encoding-002">getBytes với Charset hoặc StandardCharsets</a>

<details>
<summary>Xem chi tiết</summary>

# `String.getBytes(Charset)` với `Charset` và `StandardCharsets` trong Java

Trong Java, một `String` biểu diễn **văn bản Unicode**, còn `byte[]` là dãy byte để truyền qua mạng, ghi tệp hoặc đưa vào giao thức nhị phân. Để chuyển một chuỗi thành byte, chương trình phải chọn **bảng mã ký tự (character encoding / charset)**. Phương thức `getBytes(Charset)` thực hiện việc *encoding* (mã hóa văn bản thành byte); lựa chọn charset quyết định chính xác dãy byte đầu ra.

Bài này giải thích cách dùng `String.getBytes(Charset)` cùng `java.nio.charset.Charset` hoặc các hằng `java.nio.charset.StandardCharsets`, cách giải mã ngược, ảnh hưởng của charset, sự mất dữ liệu và những trường hợp cần kiểm soát lỗi mã hóa. Ví dụ dùng Java 17+.

## 1. Từ `String` sang `byte[]`: hai biểu diễn khác nhau

```java
import java.nio.charset.StandardCharsets;

String text = "Xin chào";
byte[] data = text.getBytes(StandardCharsets.UTF_8);
```

Lệnh trên dùng **UTF-8** để chuyển từng ký tự của `text` thành dãy byte. `data` là một mảng `byte[]` mới; đó **không phải** cùng một đối tượng hay cùng kiểu với `String`. Việc sửa các phần tử trong `data` không sửa chuỗi gốc.

Để chuyển lại thành văn bản, dùng charset tương ứng:

```java
String restored = new String(data, StandardCharsets.UTF_8);
boolean same = text.equals(restored); // true
```

Quy trình:

```text
String "Xin chào"
      │ getBytes(StandardCharsets.UTF_8)
      ▼
    byte[]                 (encoding)
      │ new String(bytes, StandardCharsets.UTF_8)
      ▼
String "Xin chào"          (decoding)
```

Quy tắc quan trọng: **bên mã hóa và bên giải mã phải thống nhất charset**. Byte không tự mang đầy đủ thông tin xác định đang dùng UTF-8, ISO-8859-1 hay UTF-16. Khi dùng charset khác ở hai đầu, kết quả có thể sai ký tự (*mojibake*) hoặc có dấu thay thế.

## 2. Cú pháp `getBytes`: nên dùng overload nào?

| API | Hành vi | Lưu ý |
| --- | --- | --- |
| `text.getBytes(StandardCharsets.UTF_8)` | Mã hóa với `Charset` được chọn tường minh. | **Cách ưu tiên** trong ứng dụng cần kết quả dự đoán được. |
| `text.getBytes(charset)` | Mã hóa với một biến kiểu `Charset`. | Phù hợp khi charset do cấu hình hoặc giao thức cung cấp. |
| `text.getBytes("UTF-8")` | Chọn charset bằng tên `String`. | Có thể ném checked exception `UnsupportedEncodingException`. |
| `text.getBytes()` | Dùng **default charset** của JVM. | Dễ phụ thuộc môi trường; nên tránh cho dữ liệu lưu trữ/giao tiếp cố định. |

Chữ ký chính cần biết:

```java
public byte[] getBytes(java.nio.charset.Charset charset)
```

- Đầu vào `charset` phải khác `null`; truyền `null` sẽ dẫn đến `NullPointerException`.
- Kết quả là mảng byte được mã hóa theo `charset`.
- Nếu chuỗi chứa đầu vào Unicode lỗi hoặc ký tự không biểu diễn được trong charset đã chọn, **overload `getBytes(Charset)` mặc định thay bằng chuỗi byte thay thế của charset**, chứ không mặc nhiên ném lỗi. Vì vậy có nguy cơ mất thông tin.
- Nếu yêu cầu **phát hiện lỗi** thay vì âm thầm thay thế, sử dụng `CharsetEncoder` được cấu hình `CodingErrorAction.REPORT` (mục 7).

Lưu ý lịch sử: `getBytes()` không tham số dùng default charset; trên Java 17 charset mặc định có thể phụ thuộc hệ thống. Từ Java 18, mặc định là UTF-8 theo JEP 400 trong cấu hình tiêu chuẩn, nhưng ứng dụng có thể chạy trên các môi trường và thiết lập khác. **Viết charset tường minh** vẫn là cách chắc chắn để đáp ứng một giao thức cụ thể.

## 3. `Charset` và `StandardCharsets`

`Charset` là kiểu biểu diễn một bộ quy tắc biến đổi giữa ký tự Unicode và byte (đồng thời cung cấp bộ mã hóa/giải mã). `StandardCharsets` chứa các **hằng `Charset` bảo đảm có sẵn** trên mọi triển khai Java chuẩn.

```java
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

Charset utf8 = StandardCharsets.UTF_8;
Charset anotherUtf8 = Charset.forName("UTF-8");
boolean equivalent = utf8.equals(anotherUtf8); // true
```

### Các hằng thông dụng

| Hằng | Đặc điểm | Tình huống |
| --- | --- | --- |
| `StandardCharsets.UTF_8` | Unicode, độ dài 1–4 byte cho một Unicode code point hợp lệ; tương thích ASCII cho các ký tự ASCII. | Lựa chọn thường dùng cho API, JSON, tệp văn bản và dữ liệu đa ngôn ngữ. |
| `StandardCharsets.US_ASCII` | Chỉ biểu diễn các ký tự ASCII 7-bit. | Giao thức giới hạn bộ ký tự ASCII. |
| `StandardCharsets.ISO_8859_1` | Bảng mã một byte, biểu diễn Unicode U+0000–U+00FF. | Tương thích dữ liệu Latin-1 cũ. |
| `StandardCharsets.UTF_16` | Mã hóa UTF-16; Java tạo byte-order mark (BOM) theo quy định của charset này. | Khi giao thức yêu cầu UTF-16 kèm cơ chế nhận biết thứ tự byte. |
| `StandardCharsets.UTF_16BE` | UTF-16 big-endian, không tự thêm BOM khi encoding. | Dữ liệu nhị phân quy định big-endian rõ ràng. |
| `StandardCharsets.UTF_16LE` | UTF-16 little-endian, không tự thêm BOM khi encoding. | Dữ liệu nhị phân quy định little-endian rõ ràng. |

**UTF-8 khác UTF-16:** cùng một `String` có thể tạo số lượng byte khác nhau, vị trí byte khác nhau và BOM khác nhau. UTF-16BE/LE dùng 2 byte cho một UTF-16 code unit; code point bổ sung dùng một cặp surrogate, tương ứng 4 byte.

Dùng `StandardCharsets.UTF_8` thay `Charset.forName("UTF-8")` nếu tên charset đã biết tại thời điểm viết mã: rõ ý nghĩa, không nhập sai tên và không cần tra cứu bằng chuỗi.

### Khi tên charset đến từ cấu hình

```java
Charset charset = Charset.forName(configuredName);
byte[] payload = text.getBytes(charset);
```

Đây là **đoạn minh họa**, trong đó `configuredName` và `text` phải có sẵn. `Charset.forName(...)` có thể ném `IllegalCharsetNameException` nếu tên không hợp lệ hoặc `UnsupportedCharsetException` nếu tên hợp lệ nhưng không được hỗ trợ. Các charset ngoài tập hằng chuẩn có thể có trên một JDK nhưng vắng trên triển khai khác; hãy xác thực tên cấu hình.

## 4. Xem byte thực tế: cùng một chuỗi, nhiều charset

Xét chuỗi `"Aé€"` gồm ba ký tự: `A` (U+0041), `é` (U+00E9) và `€` (U+20AC). Ký tự `€` **không** thuộc tập có thể biểu diễn bằng ISO-8859-1.

| Charset | Byte đầu ra dạng hex | Ý nghĩa |
| --- | --- | --- |
| UTF-8 | `41 c3 a9 e2 82 ac` | `A` dùng 1 byte; `é` 2 byte; `€` 3 byte. |
| ISO-8859-1 | `41 e9 3f` | `A`, `é` được biểu diễn; `€` không có mã nên mặc định thay bằng `?` (`3f`) trong trường hợp này. |
| UTF-16BE | `00 41 00 e9 20 ac` | Ba code unit 16-bit theo thứ tự byte lớn trước. |
| UTF-16 | `fe ff 00 41 00 e9 20 ac` | Có BOM `fe ff` ở đầu theo quy tắc encoding `UTF_16`. |

Để kiểm tra dạng hex một cách dễ đọc trong Java 17+, có `java.util.HexFormat`:

```java
String text = "A\u00E9\u20AC";
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

String hex = HexFormat.of().formatHex(bytes);
// "41c3a9e282ac"
```

Đoạn mã trên cần import `java.nio.charset.StandardCharsets` và `java.util.HexFormat`. `HexFormat` chỉ **biểu diễn byte thành chữ số hex để quan sát**, không thay đổi charset hay byte thật.

Không nên dùng `Arrays.toString(bytes)` để đọc byte dạng hex: Java `byte` là số có dấu từ -128 đến 127; chẳng hạn byte hex `c3` thường hiện là `-61` ở dạng số thập phân. Khi cần giá trị byte không dấu, có thể dùng `b & 0xFF`.

## 5. Chương trình Java 17+ biên dịch và chạy được

Tạo tệp `CharsetBytesDemo.java`:

```java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class CharsetBytesDemo {
    public static void main(String[] args) {
        // Unicode escape giúp tệp nguồn không phụ thuộc cách nhập chữ đặc biệt.
        String text = "A\u00E9\u20AC"; // A, é, €

        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        byte[] latin1 = text.getBytes(StandardCharsets.ISO_8859_1);
        byte[] utf16be = text.getBytes(StandardCharsets.UTF_16BE);

        System.out.println("UTF-8: " + HexFormat.of().formatHex(utf8));
        System.out.println("Latin-1: " + HexFormat.of().formatHex(latin1));
        System.out.println("UTF-16BE: " + HexFormat.of().formatHex(utf16be));

        String restored = new String(utf8, StandardCharsets.UTF_8);
        String latin1Restored = new String(
                latin1, StandardCharsets.ISO_8859_1);

        System.out.println("UTF-8 round trip: " + text.equals(restored));
        System.out.println("Latin-1 round trip: "
                + text.equals(latin1Restored));

        System.out.println("Latin-1 encodes euro: "
                + StandardCharsets.ISO_8859_1.newEncoder()
                        .canEncode("\u20AC"));

        // Emoji nằm ngoài BMP: 2 UTF-16 code units nhưng 4 byte UTF-8.
        String emoji = "A\uD83D\uDE00";
        System.out.println("Emoji example String.length: " + emoji.length());
        System.out.println("Emoji example UTF-8 bytes: "
                + emoji.getBytes(StandardCharsets.UTF_8).length);

        CharsetEncoder strict = StandardCharsets.ISO_8859_1.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            ByteBuffer encoded = strict.encode(CharBuffer.wrap(text));
            System.out.println("Strict bytes: " + encoded.remaining());
        } catch (CharacterCodingException e) {
            System.out.println("Strict Latin-1: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 CharsetBytesDemo.java
java CharsetBytesDemo
```

Kết quả:

```text
UTF-8: 41c3a9e282ac
Latin-1: 41e93f
UTF-16BE: 004100e920ac
UTF-8 round trip: true
Latin-1 round trip: false
Latin-1 encodes euro: false
Emoji example String.length: 3
Emoji example UTF-8 bytes: 5
Strict Latin-1: UnmappableCharacterException
```

Giải thích:

1. `getBytes(StandardCharsets.UTF_8)` giữ nguyên ý nghĩa cả ba ký tự; giải mã lại bằng cùng charset cho `true`.
2. ISO-8859-1 biểu diễn được `A` và `é`, nhưng không biểu diễn được `€`. Với `getBytes(Charset)`, ký tự không mã hóa được bị thay thế: giải mã xong không còn chuỗi ban đầu.
3. `CharsetEncoder.canEncode(...)` trả về `false` khi charset không mã hóa được `€`.
4. `CharsetEncoder` đặt `CodingErrorAction.REPORT` phát hiện lỗi và ném `UnmappableCharacterException`, cho phép chương trình dừng hoặc báo lỗi thay vì làm mất dữ liệu.
5. `"A😀"` có `String.length() == 3` vì gồm một code unit cho `A` và **hai UTF-16 code units** cho emoji, nhưng UTF-8 chỉ cần 1 + 4 = 5 byte.

## 6. Đo byte đầu ra của `getBytes(Charset)`

`String.length()` đếm đơn vị mã UTF-16, còn `getBytes(charset).length` đếm số byte **sau đúng lần encode đó**. Ví dụ `"A😀"` có `length() == 3` nhưng `getBytes(StandardCharsets.UTF_8).length == 5`. Nếu giao thức giới hạn payload byte, hãy dùng chính `byte[]` sắp được gửi:

~~~java
byte[] payload = text.getBytes(StandardCharsets.UTF_8);
int byteCount = payload.length;
~~~

Đo byte của toàn bộ JSON/body **sau khi serialize** nếu lớp serialization còn có thể thêm escape hoặc metadata. Bài **Độ dài UTF-8 không bằng length của String** hướng dẫn tính giới hạn byte và cắt chuỗi theo code point.

## 7. Ký tự không biểu diễn được, dữ liệu lỗi và chính sách thay thế

### 7.1. `getBytes(Charset)` mặc định thay thế thay vì báo lỗi

Ví dụ `"€".getBytes(StandardCharsets.ISO_8859_1)` không thể biểu diễn đúng dấu euro. Với các hằng chuẩn trong chương trình trên, đầu ra mặc định có byte thay thế `3f` (dấu `?`). Khi đã thay thế, không thể phục hồi ký tự gốc chỉ nhờ giải mã cùng charset.

Cũng có thể xảy ra đầu vào Unicode không hợp lệ, ví dụ một `String` chứa **surrogate lẻ** không tạo thành cặp hợp lệ. `getBytes(Charset)` cũng dùng cách thay thế mặc định. Việc Java `String` lưu được một code unit dạng surrogate không đồng nghĩa đó là một chuỗi Unicode hợp lệ để encode.

Không suy rộng byte thay thế luôn là `3f` với **mọi** charset; thay thế do charset quyết định.

### 7.2. Cần từ chối thay vì thay thế: `CharsetEncoder`

Khi ký tự bị thay thế có thể gây sai dữ liệu nghiệp vụ, hãy mã hóa bằng encoder:

```java
CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

try {
    ByteBuffer result = encoder.encode(CharBuffer.wrap("Café"));
    // result chứa dãy byte nếu mọi ký tự đều hợp lệ cho ASCII.
} catch (CharacterCodingException e) {
    // Ghi nhận dữ liệu không mã hóa được và xử lý theo yêu cầu nghiệp vụ.
}
```

Đây là **đoạn minh họa**, cần import các lớp `java.nio.ByteBuffer`, `java.nio.CharBuffer`, `java.nio.charset.*`; chương trình đầy đủ ở mục 5 đã có một ví dụ runnable. Với ASCII, `é` không biểu diễn được nên nhánh `catch` được thực thi.

Các chính sách thường dùng của encoder/decoder:

- `CodingErrorAction.REPORT`: phát hiện và báo lỗi.
- `CodingErrorAction.REPLACE`: sử dụng replacement đã cấu hình.
- `CodingErrorAction.IGNORE`: bỏ qua dữ liệu lỗi, có thể mất nội dung.

Nếu yêu cầu dữ liệu không được mất ký tự, dùng `REPORT` và xử lý ngoại lệ rõ ràng.

### 7.3. Giải mã byte không hợp lệ

`new String(bytes, charset)` mặc định cũng thay thế chuỗi byte lỗi bằng ký tự thay thế khi cần. Chẳng hạn các byte `c3 28` không phải một chuỗi UTF-8 hợp lệ:

```java
byte[] broken = {(byte) 0xC3, (byte) 0x28};

String decoded = new String(broken, StandardCharsets.UTF_8);
// Chứa ký tự thay thế U+FFFD trước dấu "(".
```

Khi cần từ chối payload lỗi:

```java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

try {
    String decoded = decoder.decode(ByteBuffer.wrap(broken)).toString();
} catch (CharacterCodingException e) {
    // Payload không phải UTF-8 hợp lệ; xử lý hoặc trả lỗi.
}
```

Đây cũng là **đoạn minh họa** và cần các import liên quan từ `java.nio.*`, `java.nio.charset.*`. Dùng `CharsetDecoder` nếu tính hợp lệ của byte đầu vào là yêu cầu bắt buộc; một phép giải mã tự thay thế không đủ để phát hiện lỗi.

## 8. Tình huống sử dụng trong ứng dụng

### 8.1. Gửi văn bản qua mạng hoặc tính kích thước request

```java
byte[] requestBody = jsonText.getBytes(StandardCharsets.UTF_8);
int contentLength = requestBody.length;
```

Đoạn này giả sử `jsonText` đã là **JSON hợp lệ**. `getBytes` chỉ mã hóa thành byte, **không tự escape chuỗi hay tạo JSON**. Truyền MIME/charset phù hợp, ví dụ `Content-Type: application/json; charset=utf-8` khi giao thức/ứng dụng yêu cầu; đừng tự thiết kế giao thức mà không xác nhận charset giữa các bên.

### 8.2. Ghi và đọc dữ liệu văn bản

```java
Path path = Path.of("message.txt");
String message = "Xin chào";
Files.writeString(path, message, StandardCharsets.UTF_8);
String readBack = Files.readString(path, StandardCharsets.UTF_8);
```

Ví dụ cần import `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.charset.StandardCharsets` và xử lý `IOException`. Với luồng byte, cách tương đương về charset là ghi `message.getBytes(StandardCharsets.UTF_8)` vào tệp rồi giải mã các byte khi đọc. Ưu tiên `Files.writeString/readString` nếu mục đích thuần túy là I/O văn bản.

### 8.3. Dữ liệu theo giao thức yêu cầu bảng mã khác UTF-8

Khi giao thức yêu cầu Latin-1, ASCII hoặc UTF-16LE, hãy dùng chính charset được quy định:

```java
byte[] ascii = "OK".getBytes(StandardCharsets.US_ASCII);
byte[] utf16le = "OK".getBytes(StandardCharsets.UTF_16LE);
```

Cùng chữ `"OK"` nhưng `ascii` có hai byte `4f 4b`, còn `utf16le` có bốn byte `4f 00 4b 00`. **Không thay thế charset giao thức bằng UTF-8 theo cảm tính** khi phía nhận đã quy định định dạng.

## 9. Lỗi thường gặp và cách tránh

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Dùng `text.getBytes()` rồi giả định luôn là UTF-8 | Default charset có thể phụ thuộc phiên bản JVM/cấu hình. | Dùng `getBytes(StandardCharsets.UTF_8)`. |
| Encode UTF-8, decode bằng Latin-1 hoặc ngược lại | Các byte có ánh xạ ký tự khác nhau. | Ghi rõ charset và dùng nhất quán ở hai đầu. |
| Dùng `text.length()` làm số byte gửi đi | `String.length()` đo UTF-16 code units. | Dùng `encoded.length`. |
| Dùng ISO-8859-1 cho tiếng Việt, emoji hoặc ký tự ngoài Latin-1 | Không biểu diễn được nhiều ký tự; `getBytes(Charset)` có thể thay thế và mất dữ liệu. | Chọn charset phù hợp; nếu không được mất dữ liệu, dùng encoder với `REPORT`. |
| Nghĩ `byte` âm là lỗi encoding | Java `byte` có dấu; `-61` là cùng bit với byte hex `c3`. | Dùng `HexFormat` hoặc `b & 0xFF`. |
| Dùng `UTF_16` nhưng bên nhận chờ đúng UTF-16LE không BOM | UTF-16 mặc định có BOM và thứ tự byte theo charset. | Chọn đúng `UTF_16LE` / `UTF_16BE` theo giao thức. |
| Dùng `new String(bytes)` mà quên charset | Decoder dùng charset mặc định. | `new String(bytes, StandardCharsets.UTF_8)`. |
| Dùng `Arrays.toString(bytes)` làm chuỗi để truyền/ghi tệp | Đây là văn bản biểu diễn mảng, không phải dữ liệu byte gốc. | Gửi trực tiếp `byte[]` qua API byte, hoặc dùng encoding nhị phân sang văn bản như Base64 khi cần. |
| Dùng `getBytes` như giải pháp mã hóa bảo mật | Encoding ký tự không tạo tính bí mật hay xác thực. | Nếu cần bảo mật, dùng API mật mã phù hợp sau khi xác định byte đầu vào. |

### Cẩn thận khi dùng byte để so khớp hoặc băm nội dung

Hai chuỗi nhìn giống nhau có thể khác **chuỗi code points**, chẳng hạn `"\u00E9"` (một ký tự `é`) và `"e\u0301"` (`e` cộng dấu sắc kết hợp). Khi mã hóa UTF-8, byte của chúng khác nhau. Nếu hệ thống cần cách biểu diễn Unicode chuẩn hóa trước khi so sánh/băm/ký, có thể dùng `java.text.Normalizer` theo quy tắc cụ thể của giao thức trước khi gọi `getBytes`. Chỉ thực hiện chuẩn hóa khi đó là yêu cầu đã thống nhất; không âm thầm thay đổi dữ liệu gốc.

## 10. Tự kiểm tra

**Câu 1.** Vì sao `"A".getBytes(StandardCharsets.UTF_8).length == 1`, nhưng `"😀".getBytes(StandardCharsets.UTF_8).length == 4`?

**Trả lời:** UTF-8 dùng số byte biến thiên theo code point. ASCII `A` dùng một byte; emoji ngoài BMP `😀` dùng bốn byte.

**Câu 2.** Có thể dùng `getBytes(StandardCharsets.ISO_8859_1)` để chuyển mọi `String` Unicode mà không mất dữ liệu không?

**Trả lời:** Không. ISO-8859-1 chỉ biểu diễn một tập ký tự giới hạn. `getBytes(Charset)` thay thế ký tự không biểu diễn được; dùng charset khác như UTF-8 hoặc encoder `REPORT` để phát hiện.

**Câu 3.** Phần mềm nhận byte `41 c3 a9` từ nguồn ghi rõ UTF-8. Giải mã thế nào?

**Trả lời:** `new String(bytes, StandardCharsets.UTF_8)` tạo chuỗi `"Aé"` nếu mảng byte tương ứng đúng ba giá trị hex nêu trên.

**Câu 4.** Lúc nào chọn `StandardCharsets.UTF_8` và lúc nào chọn `Charset.forName(name)`?

**Trả lời:** Dùng hằng `StandardCharsets.UTF_8` nếu charset được xác định ngay trong mã. Dùng `Charset.forName(name)` nếu tên charset nhận động từ cấu hình/giao thức và có bước kiểm tra tên được hỗ trợ.

**Câu 5.** Vì sao `new String(text.getBytes(UTF_8), UTF_8)` không bảo đảm round-trip với **mọi** `String` Java có thể tạo?

**Trả lời:** Chuỗi có surrogate lẻ là đầu vào Unicode không hợp lệ; `getBytes(Charset)` có thể thay thế thay vì bảo toàn code unit đó. Với văn bản Unicode hợp lệ và charset UTF-8 ở cả hai đầu, round-trip bảo toàn nội dung.

## 11. Ghi nhớ khi áp dụng

- Xác định **charset theo đặc tả dữ liệu**, thường là UTF-8 nếu hai bên thống nhất.
- Encode với `text.getBytes(charset)` và decode với `new String(bytes, charset)`.
- Dùng `StandardCharsets` cho các charset phổ biến bảo đảm được Java hỗ trợ.
- Đừng đồng nhất độ dài `String` với độ dài `byte[]`; kiểm tra kích thước payload sau encoding.
- Khi không được phép mất hoặc thay thế ký tự, dùng `CharsetEncoder` / `CharsetDecoder` với `CodingErrorAction.REPORT`.

Tài liệu chuẩn Java 17: [`String.getBytes(Charset)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html), [`Charset`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/Charset.html), [`StandardCharsets`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-encoding-003">new String(byte&#91;&#93;,Charset) và giải mã một vùng byte</a>

<details>
<summary>Xem chi tiết</summary>

# Giải mã `byte[]` thành `String` với `Charset` và giải mã một vùng byte

## Mục tiêu

Dữ liệu đọc từ tệp, socket, HTTP hoặc một giao thức nhị phân thường bắt đầu dưới dạng `byte[]`. Để biến các byte này thành văn bản Java, phải **giải mã (decode)** chúng theo đúng bảng mã đã dùng khi tạo dữ liệu. Hai constructor quan trọng là:

```java
new String(byte[] bytes, Charset charset)
new String(byte[] bytes, int offset, int length, Charset charset)
```

Sau bài này, bạn có thể chọn đúng `Charset`, hiểu chính xác `offset` và `length` tính theo **byte**, giải mã một phần mảng mà không tự cắt sai ranh giới ký tự Unicode, đồng thời nhận biết trường hợp dữ liệu bị lỗi.

## 1. Byte không tự mang ý nghĩa là ký tự

Java `String` lưu nội dung văn bản Unicode; `byte[]` lưu dãy byte. Các giá trị byte giống nhau có thể tạo ra văn bản khác nhau tùy bảng mã.

Ví dụ, ký tự `é` có dạng UTF-8 gồm hai byte `C3 A9`, còn trong ISO-8859-1 nó là một byte `E9`. Đọc `E9` như một ký tự UTF-8 hợp lệ sẽ không cho ra chữ `é`. Vì thế, **charset khi giải mã phải khớp charset khi mã hóa**; chỉ định UTF-8 không thể tự sửa dữ liệu vốn được tạo bằng một bảng mã khác.

`Charset` nằm trong package `java.nio.charset`. Với các charset phổ biến, dùng hằng số có sẵn trong `StandardCharsets`:

```java
import java.nio.charset.StandardCharsets;

byte[] bytes = "Chào Java".getBytes(StandardCharsets.UTF_8);
String text = new String(bytes, StandardCharsets.UTF_8);
System.out.println(text);
```

Kết quả:

```text
Chào Java
```

`getBytes(StandardCharsets.UTF_8)` là chiều **mã hóa (encode)** từ `String` sang `byte[]`; `new String(bytes, StandardCharsets.UTF_8)` là chiều **giải mã (decode)** ngược lại. Chúng tạo thành một vòng khứ hồi khi dữ liệu có thể được biểu diễn trong bảng mã đã chọn và không bị sửa đổi trên đường đi.

Nên chỉ định charset rõ ràng, đặc biệt với dữ liệu trao đổi giữa các chương trình. Constructor `new String(bytes)` dùng charset mặc định của môi trường Java đang chạy; không nên dựa vào mặc định khi giao thức hoặc định dạng tệp đã quy định một charset cụ thể.

## 2. Giải mã toàn bộ mảng: `new String(byte[], Charset)`

Cú pháp:

```java
String text = new String(bytes, StandardCharsets.UTF_8);
```

Trong đó:

- `bytes`: mảng byte chứa dữ liệu văn bản đã mã hóa.
- `StandardCharsets.UTF_8`: `Charset` dùng để diễn giải dãy byte.
- `text`: đối tượng `String` mới chứa văn bản đã giải mã.

Constructor giải mã **toàn bộ** mảng từ chỉ số `0` đến `bytes.length - 1`. Nếu mảng rỗng, kết quả là chuỗi rỗng `""`. Với `bytes` hoặc `charset` bằng `null`, lời gọi không hợp lệ và ném `NullPointerException`.

Ví dụ với dữ liệu UTF-8 xác định sẵn:

```java
byte[] encoded = {(byte) 0x43, (byte) 0x61, (byte) 0x66,
                  (byte) 0xC3, (byte) 0xA9};

String decoded = new String(encoded, StandardCharsets.UTF_8);
System.out.println(decoded);
```

Kết quả:

```text
Café
```

Các byte ASCII `43 61 66` cho `Caf`; cặp UTF-8 `C3 A9` cho `é`. Phép ép kiểu `(byte)` ở đây là cần thiết với các literal hex lớn hơn `0x7F`, vì kiểu `byte` trong Java có dấu (miền giá trị từ -128 đến 127). Dấu âm trong phần hiển thị của mảng byte không có nghĩa dữ liệu mã hóa đã thay đổi.

Sau khi constructor hoàn tất, thay đổi các phần tử của mảng đầu vào **không làm đổi nội dung của `String` đã tạo**. Điều này phù hợp với tính bất biến của `String`.

## 3. Giải mã một vùng: `new String(byte[], offset, length, Charset)`

Nhiều bản tin có header, dữ liệu văn bản và phần đuôi nhị phân. Nếu chỉ phần giữa là văn bản, cần giải mã đúng vùng được chỉ định:

```java
String text = new String(bytes, offset, length, StandardCharsets.UTF_8);
```

**Ý nghĩa của các tham số:**

| Tham số | Ý nghĩa |
| --- | --- |
| `bytes` | Toàn bộ mảng byte nguồn |
| `offset` | Chỉ số **byte đầu tiên** của vùng cần đọc, bắt đầu từ 0 |
| `length` | **Số byte** cần giải mã, không phải chỉ số byte cuối |
| `charset` | Bảng mã của phần byte văn bản |

Vùng được chọn có chỉ số trong khoảng **`[offset, offset + length)`**: bao gồm byte tại `offset` và loại trừ byte tại `offset + length`. Đây là đúng kiểu khoảng nửa mở thường gặp trong Java.

```text
Mảng byte:  [H0] [H1] [C] [a] [f] [C3] [A9] [T]
Chỉ số:     0    1    2   3   4    5    6    7
                       <------ 5 byte ----->
                       offset=2, length=5
```

Ở minh họa này, `H0`/`H1` là header, `T` là trailer. Dãy byte từ vị trí 2 đến 6 tương ứng văn bản UTF-8 `Café`. Byte `C3` và `A9` phải cùng nằm trong vùng giải mã để tạo thành ký tự `é`.

Ví dụ:

```java
byte[] packet = {
        0x01, 0x02,             // Header
        0x43, 0x61, 0x66,
        (byte) 0xC3, (byte) 0xA9, // "Café" trong UTF-8
        0x7F                    // Trailer
};

String payload = new String(packet, 2, 5, StandardCharsets.UTF_8);
System.out.println(payload);
```

Kết quả:

```text
Café
```

Nếu chỉ cần giải mã, dùng constructor có vùng sẽ tiện hơn việc tạo một mảng tạm bằng `Arrays.copyOfRange(bytes, offset, offset + length)` rồi gọi `new String(... )`. Tuy nhiên, constructor vẫn cần xử lý và tạo chuỗi đầu ra; "không phải tự cắt mảng" **không đồng nghĩa với giải mã không cấp phát bộ nhớ**.

### Kiểm tra giới hạn vùng

`offset` và `length` phải không âm, và vùng phải nằm hoàn toàn trong `bytes`. Nếu vượt giới hạn, constructor ném `IndexOutOfBoundsException`. Ví dụ:

```java
byte[] data = {65, 66, 67}; // "ABC"
String value = new String(data, 1, 2, StandardCharsets.UTF_8);
// Kết quả: "BC"
```

`new String(data, 1, 3, StandardCharsets.UTF_8)` vượt quá độ dài mảng và gây lỗi. `offset=1, length=2` là chọn byte tại chỉ số 1 và 2, **không phải** "từ chỉ số 1 đến chỉ số 2" theo cách hiểu tham số `length` là điểm kết thúc.

`length=0` tạo chuỗi rỗng nếu `offset` hợp lệ (kể cả `offset == bytes.length`).

## 4. Chọn ranh giới byte phù hợp với charset

Với UTF-8, một ký tự Unicode có thể dùng từ một đến bốn byte. `offset` và `length` là tọa độ **byte**, vì vậy một vùng byte hợp lệ về giới hạn mảng vẫn có thể cắt ngang chuỗi mã hóa của một ký tự.

Ví dụ:

```java
byte[] eAcute = {(byte) 0xC3, (byte) 0xA9};

System.out.println(new String(eAcute, 0, 2, StandardCharsets.UTF_8));
System.out.println((int) new String(eAcute, 0, 1, StandardCharsets.UTF_8).charAt(0));
```

Kết quả:

```text
é
65533
```

Giải mã đủ hai byte cho ra `é`; chỉ lấy byte đầu `C3` khiến UTF-8 thiếu byte nối tiếp, nên constructor thay bằng ký tự thay thế Unicode **U+FFFD**, có giá trị thập phân 65533. Đây không phải lỗi chỉ số mảng, mà là **lỗi dãy byte sau khi chọn vùng**.

Nếu muốn tách một vùng theo số ký tự của **văn bản đã giải mã**, hãy giải mã phần dữ liệu hợp lệ trước, rồi dùng API xử lý `String` phù hợp. Đặc biệt, `String.length()` đếm đơn vị mã UTF-16, không phải byte và cũng không luôn bằng số Unicode code point.

Nếu dữ liệu đến theo nhiều khối từ mạng và một ký tự UTF-8 nằm trên ranh giới hai khối, không được giải mã mỗi khối độc lập rồi mong kết quả luôn chính xác. Hãy ghép đủ byte trước khi giải mã, hoặc dùng `CharsetDecoder` theo cơ chế giải mã tăng dần, giữ trạng thái giữa các khối.

## 5. Byte không hợp lệ và chế độ kiểm soát lỗi

Hai constructor `String` dùng `Charset` ở trên sẽ **thay các dãy byte sai hoặc không thể biểu diễn bằng chuỗi thay thế mặc định của charset**. Với UTF-8, thường thấy ký tự thay thế `�` (`U+FFFD`) trong đầu ra.

```java
byte[] badUtf8 = {(byte) 0xC3, (byte) 0x28};
String text = new String(badUtf8, StandardCharsets.UTF_8);

System.out.println((int) text.charAt(0));
System.out.println(text.charAt(1));
```

Kết quả:

```text
65533
(
```

Byte `C3` cần byte tiếp nối UTF-8 hợp lệ, nhưng `28` không đáp ứng; byte lỗi được thay bằng `U+FFFD`, còn `28` được hiểu thành dấu `(`. Hành vi thay thế giúp tiếp tục hiển thị dữ liệu có lỗi, nhưng có thể **che giấu việc đầu vào bị hỏng**.

Khi yêu cầu kiểm soát chặt, ví dụ đọc mã định danh, thông điệp giao thức hoặc dữ liệu cần xác minh, dùng `CharsetDecoder` và yêu cầu báo lỗi:

```java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

byte[] badUtf8 = {(byte) 0xC3, (byte) 0x28};

CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

try {
    String text = decoder.decode(ByteBuffer.wrap(badUtf8)).toString();
    System.out.println(text);
} catch (CharacterCodingException error) {
    System.out.println("Dữ liệu UTF-8 không hợp lệ");
}
```

Kết quả:

```text
Dữ liệu UTF-8 không hợp lệ
```

Đây là **đoạn mã minh họa** để đặt trong một phương thức hoặc `main`; các lệnh `import` đặt ở đầu tệp. `REPORT` yêu cầu báo lỗi thay vì âm thầm thay ký tự. Khi chỉ muốn giải mã một vùng với decoder, dùng `ByteBuffer.wrap(bytes, offset, length)` để tạo buffer có vị trí và giới hạn tương ứng vùng byte. `CharsetDecoder` có trạng thái và không nên dùng chung một instance giữa các luồng nếu không đồng bộ đúng cách.

## 6. Chương trình Java 17+ chạy hoàn chỉnh

Tạo tệp **`ByteDecodingDemo.java`** với nội dung:

```java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class ByteDecodingDemo {
    public static void main(String[] args) {
        byte[] prefix = {0x01, 0x02, 0x03};
        byte[] content = "Chào Java".getBytes(StandardCharsets.UTF_8);
        byte[] suffix = {0x7F};
        byte[] packet = new byte[prefix.length + content.length + suffix.length];

        System.arraycopy(prefix, 0, packet, 0, prefix.length);
        System.arraycopy(content, 0, packet, prefix.length, content.length);
        System.arraycopy(suffix, 0, packet,
                prefix.length + content.length, suffix.length);

        String allTextBytes = new String(content, StandardCharsets.UTF_8);
        String selectedText = new String(
                packet, prefix.length, content.length, StandardCharsets.UTF_8);

        System.out.println("Giải mã đầy đủ: " + allTextBytes);
        System.out.println("Giải mã vùng: " + selectedText);
        System.out.println("Số đơn vị UTF-16: " + selectedText.length());
        System.out.println("Số byte UTF-8: " + content.length);

        byte[] invalid = {(byte) 0xC3, (byte) 0x28};
        String replaced = new String(invalid, StandardCharsets.UTF_8);
        System.out.println("Mã ký tự thay thế: " + (int) replaced.charAt(0));

        CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            strict.decode(ByteBuffer.wrap(invalid));
            System.out.println("Dữ liệu hợp lệ");
        } catch (CharacterCodingException ex) {
            System.out.println("Dữ liệu không hợp lệ");
        }
    }
}
```

Biên dịch tệp được lưu bằng UTF-8 và chạy:

```bash
javac -encoding UTF-8 ByteDecodingDemo.java
java ByteDecodingDemo
```

Đầu ra hiển thị trên console hỗ trợ Unicode:

```text
Giải mã đầy đủ: Chào Java
Giải mã vùng: Chào Java
Số đơn vị UTF-16: 9
Số byte UTF-8: 10
Mã ký tự thay thế: 65533
Dữ liệu không hợp lệ
```

Giải thích: `"Chào Java"` gồm 9 đơn vị UTF-16 nhưng 10 byte UTF-8, vì chữ `à` được mã hóa bằng hai byte. `packet` chứa ba byte header, mười byte văn bản, một byte trailer. Constructor bốn tham số chỉ lấy đúng mười byte văn bản; hai phần nhị phân không đi vào kết quả. Đoạn cuối phân biệt hành vi thay thế ký tự của constructor với cơ chế báo lỗi của decoder.

## 7. Lưu ý thực tế và lỗi thường gặp

| Tình huống | Nguyên nhân hoặc cách xử lý |
| --- | --- |
| Chữ tiếng Việt biến thành `�` hoặc ký tự lạ | Kiểm tra charset nguồn và tính hợp lệ của byte, đừng đoán UTF-8 nếu định dạng thực tế khác |
| Lấy nhầm phần header hoặc trailer | Tính `offset` từ đầu mảng và `length` là số byte thực sự của payload |
| Cắt giữa ký tự đa byte | Chọn ranh giới mã hóa đúng, hoặc giải mã dạng streaming bằng decoder giữ trạng thái |
| `IndexOutOfBoundsException` | Rà lại chỉ số byte, độ dài và giới hạn mảng |
| Dữ liệu lỗi nhưng chương trình không ném ngoại lệ | Constructor `String` đang dùng cơ chế thay thế; chuyển sang `CharsetDecoder` với `REPORT` nếu cần |
| Đếm `String.length()` để tính kích thước bản tin | Độ dài `String` là số đơn vị UTF-16; dùng độ dài `byte[]` khi giao thức cần số byte |
| Tệp UTF-8 bắt đầu bằng BOM | Các byte `EF BB BF` biểu diễn `U+FEFF`; khi giải mã UTF-8 với `String`, hãy kiểm tra và xử lý dấu này nếu định dạng tệp yêu cầu bỏ BOM |
| Đưa dữ liệu của người dùng vào `new String(bytes)` không nêu charset | Nêu charset theo giao thức, ví dụ `StandardCharsets.UTF_8`, để dữ liệu có ý nghĩa nhất quán |

Không được nhầm `new String(bytes, 2, 5, UTF_8)` với `substring(2, 5)`: `substring` nhận vị trí trong văn bản Java sau khi giải mã và đối số thứ hai là **chỉ số kết thúc loại trừ**; constructor vùng byte nhận **số byte**. Hai thao tác có đơn vị và ý nghĩa khác nhau.

## 8. Tự kiểm tra

**Câu 1.** Cho `byte[] data = {65, 66, 67, 68};`. Giá trị của `new String(data, 1, 2, StandardCharsets.US_ASCII)` là gì?

**Đáp án:** `"BC"`. Vùng giải mã gồm byte chỉ số 1 và 2, ứng với mã ASCII 66 và 67.

**Câu 2.** Vì sao `new String(utf8, 0, 1, UTF_8)` có thể tạo `�` dù chỉ số `0` và độ dài `1` đều hợp lệ?

**Đáp án:** Một byte đầu có thể là phần đầu của ký tự UTF-8 hai đến bốn byte. Vùng byte không chứa đủ dãy mã hóa nên đầu vào không hợp lệ với decoder.

**Câu 3.** Khi nào nên dùng `CharsetDecoder` thay cho constructor `String`?

**Đáp án:** Khi cần phát hiện dữ liệu không hợp lệ thay vì tự động thay ký tự, hoặc cần giải mã dữ liệu nhiều đợt với trạng thái duy trì giữa các lần nhận byte.

## Ghi nhớ

Dùng **`new String(bytes, charset)`** để giải mã toàn bộ dãy byte và **`new String(bytes, offset, length, charset)`** để giải mã đúng vùng byte thuộc payload. `offset` là vị trí byte đầu, `length` là **số byte**, và bảng mã phải khớp dữ liệu nguồn. Với UTF-8, đặc biệt chú ý ranh giới ký tự nhiều byte và sử dụng `CharsetDecoder` nếu cần xác thực lỗi đầu vào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-encoding-004">Độ dài UTF-8 không bằng length của String</a>

<details>
<summary>Xem chi tiết</summary>

# Độ dài UTF-8 không bằng `String.length()` trong Java

Một `String` Java là chuỗi văn bản Unicode được biểu diễn bằng các **đơn vị mã UTF-16** (UTF-16 code units). Ngược lại, UTF-8 là cách **mã hóa văn bản thành byte**. Vì vậy, hai biểu thức sau đo hai đại lượng khác nhau:

~~~java
int utf16Units = text.length();
int utf8Bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
~~~

`text.length()` trả về số đơn vị mã UTF-16 trong chuỗi; `getBytes(StandardCharsets.UTF_8).length` trả về số byte sau khi mã hóa chuỗi sang UTF-8. Cần phân biệt chúng khi giới hạn kích thước dữ liệu, tính kích thước nội dung gửi qua mạng, lưu trữ hoặc cắt chuỗi.

## 1. Ba cách đếm dễ bị nhầm

| Đại lượng | Java API | Đo điều gì? |
| --- | --- | --- |
| Đơn vị mã UTF-16 | `text.length()` | Số giá trị `char` cấu thành `String` |
| Unicode code point | `text.codePointCount(0, text.length())` | Số code point trong chuỗi; một code point ngoài BMP thường chiếm hai `char` |
| Số byte UTF-8 | `text.getBytes(StandardCharsets.UTF_8).length` | Độ dài của mảng byte sau khi mã hóa UTF-8 |

**Không đồng nhất cả ba với “số ký tự người dùng nhìn thấy”.** Chẳng hạn, chữ `é` có thể biểu diễn bằng một code point `U+00E9` hoặc bằng `e` cộng dấu sắc kết hợp `U+0301` (hai code point). Nhiều emoji gia đình, cờ hoặc emoji kèm biến thể được tạo thành từ nhiều code point nhưng hiển thị như một cụm ký tự. Đếm *grapheme cluster* (cụm chữ hiển thị) là bài toán riêng.

### Vì sao Java `String.length()` đếm UTF-16?

Trong Java, phương thức `length()` có ngữ nghĩa lịch sử là đếm số đơn vị mã UTF-16, không phải số byte trên đĩa hoặc số Unicode code point.

- Các code point trong vùng BMP (`U+0000` đến `U+FFFF`), trừ vùng surrogate, thường cần **một** đơn vị mã UTF-16.
- Một code point bổ sung ngoài BMP (`U+10000` đến `U+10FFFF`), chẳng hạn `😀` (`U+1F600`), cần **hai** đơn vị mã UTF-16: một cặp surrogate.
- `String` Java có thể chứa surrogate đơn lẻ không hợp lệ; khi đó độ dài vẫn đếm đơn vị mã, nhưng việc mã hóa sang byte phải xử lý chuỗi UTF-16 không hợp lệ.

Ví dụ: `"A😀B"` chứa ba code point (`A`, `😀`, `B`), nhưng `length()` trả về **4** vì emoji chiếm hai đơn vị mã UTF-16.

### Vì sao UTF-8 có số byte khác?

Với một Unicode code point hợp lệ, UTF-8 dùng độ dài thay đổi:

| Khoảng code point | Số byte UTF-8 | Ví dụ |
| --- | ---: | --- |
| `U+0000`–`U+007F` | 1 | `A` |
| `U+0080`–`U+07FF` | 2 | `é` (`U+00E9`) |
| `U+0800`–`U+FFFF` (không gồm surrogate) | 3 | `ệ` (`U+1EC7`) |
| `U+10000`–`U+10FFFF` | 4 | `😀` (`U+1F600`) |

Vì `char` trong Java là một đơn vị mã UTF-16 còn byte UTF-8 thuộc một mã hóa khác, **không tồn tại công thức chung** kiểu `utf8Bytes = text.length()`, `text.length() * 2` hoặc `text.length() * 3`. Phải dùng mã hóa UTF-8 thực tế, hoặc thuật toán dựa trên từng code point hợp lệ.

## 2. Ví dụ dễ kiểm chứng

| Chuỗi | `length()` | Code point | Byte UTF-8 | Giải thích |
| --- | ---: | ---: | ---: | --- |
| `"Hello"` | 5 | 5 | 5 | Mọi ký tự đều là ASCII, mỗi ký tự 1 byte UTF-8 |
| `"Việt"` | 4 | 4 | 6 | `V`, `i`, `t`: mỗi chữ 1 byte; `ệ`: 3 byte |
| `"A😀B"` | 4 | 3 | 6 | `A`: 1 byte; `😀`: 4 byte; `B`: 1 byte |
| `"e\u0301"` | 2 | 2 | 3 | `e` (1 byte) và dấu sắc kết hợp `U+0301` (2 byte) |
| `"\u00E9"` (`é`) | 1 | 1 | 2 | `é` dạng dựng sẵn (`U+00E9`): 2 byte |

Trong hai hàng cuối, biểu diễn Java `"\u0301"` và `"\u00E9"` dùng ký pháp Unicode escape. Dù người đọc có thể thấy hai cách biểu diễn chữ `é` giống nhau, số đơn vị mã, code point và byte vẫn khác.

## 3. Chương trình Java 17+ đo từng giá trị

Tạo file `Utf8LengthDemo.java` với nội dung:

~~~java
import java.nio.charset.StandardCharsets;

public class Utf8LengthDemo {
    public static void main(String[] args) {
        inspect("ASCII", "Hello");
        inspect("Vietnamese", "Vi\u1EC7t");
        inspect("Emoji", "A\uD83D\uDE00B");
        inspect("Combining", "e\u0301");
        inspect("Precomposed", "\u00E9");
    }

    private static void inspect(String name, String text) {
        int utf16Units = text.length();
        int codePoints = text.codePointCount(0, text.length());
        int utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;

        System.out.printf(
                "%s: length=%d, codePoints=%d, utf8Bytes=%d%n",
                name, utf16Units, codePoints, utf8Bytes
        );
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 Utf8LengthDemo.java
java Utf8LengthDemo
~~~

Kết quả chuẩn:

~~~text
ASCII: length=5, codePoints=5, utf8Bytes=5
Vietnamese: length=4, codePoints=4, utf8Bytes=6
Emoji: length=4, codePoints=3, utf8Bytes=6
Combining: length=2, codePoints=2, utf8Bytes=3
Precomposed: length=1, codePoints=1, utf8Bytes=2
~~~

Ở đây ký tự có dấu và emoji được viết dưới dạng Unicode escape để chương trình minh họa có thể sao chép thuận lợi ngay cả khi cách hiển thị/phông chữ của terminal khác nhau. `StandardCharsets.UTF_8` chỉ rõ charset, tránh phụ thuộc charset mặc định của môi trường.

Một số điều cần nhận ra từ bảng:

1. Với ASCII, ba cách đếm tình cờ bằng nhau; kết quả đó **không** đúng cho toàn bộ Unicode.
2. Với chữ `ệ`, một code point là một đơn vị mã UTF-16 nhưng mã hóa thành ba byte UTF-8.
3. Với `😀`, Java đếm hai đơn vị mã UTF-16, Unicode đếm một code point, UTF-8 dùng bốn byte.
4. Hai cách biểu diễn `é` cho biết cùng hình thức hiển thị không bảo đảm cùng biểu diễn nhị phân.

## 4. Đếm byte đúng cách và lựa chọn charset

Muốn biết số byte UTF-8, hãy **chỉ rõ UTF-8** ở đúng nơi chuyển chuỗi thành byte:

~~~java
import java.nio.charset.StandardCharsets;

String text = "Dữ liệu";
byte[] payload = text.getBytes(StandardCharsets.UTF_8);
int byteCount = payload.length;
~~~

Đây là đoạn mã minh họa để dùng bên trong chương trình. `payload.length` là kích thước của **chính mảng byte vừa tạo**; giá trị là số byte, không phải số ký tự.

Nên tránh:

~~~java
// Không đo kích thước UTF-8:
int wrongBytes = text.length();

// Không nên để charset phụ thuộc môi trường:
byte[] platformDependent = text.getBytes();
~~~

`getBytes()` không truyền tham số dùng charset mặc định của JVM. Charset mặc định có thể phụ thuộc phiên bản Java, cấu hình và cách khởi động. Khi yêu cầu là **UTF-8**, hãy dùng overload `getBytes(StandardCharsets.UTF_8)` để thể hiện rõ ý định.

**Giới hạn của cách đếm này:** nó đo chuỗi sau khi mã hóa thành UTF-8, không nhất thiết là số byte thực tế của toàn bộ tài liệu HTTP, JSON, file nén hoặc bản tin mạng. Những lớp đó có thể thêm dấu nháy, escape, dấu phân cách, header, BOM, framing hoặc thay đổi biểu diễn. Ví dụ nội dung Java `"😀"` được mã hóa UTF-8 trực tiếp thành 4 byte, nhưng trong một định dạng JSON nếu serializer chọn escape thành `"\uD83D\uDE00"` thì số byte của văn bản JSON được phát ra sẽ khác. Phải đo **đúng byte của payload cuối cùng** nếu giao thức đặt giới hạn theo payload được truyền đi.

### Chuỗi UTF-16 không hợp lệ

Một `String` Java có thể chứa một surrogate không ghép cặp, ví dụ `"\uD800"`. Đây không phải một Unicode scalar value hợp lệ để mã hóa trực tiếp thành UTF-8. `String.getBytes(StandardCharsets.UTF_8)` sử dụng hành vi thay thế cho đầu vào không hợp lệ; byte thu được phản ánh kết quả thay thế, không phải bản mã UTF-8 của một code point hợp lệ. Nếu cần từ chối dữ liệu sai định dạng, dùng `CharsetEncoder` với `CodingErrorAction.REPORT` thay vì chấp nhận thay thế âm thầm:

~~~java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class StrictUtf8Demo {
    public static void main(String[] args) {
        String text = "Java \uD83D\uDE00";

        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(text));
            System.out.println(bytes.remaining());
        } catch (CharacterCodingException ex) {
            System.out.println("Invalid Unicode input");
        }
    }
}
~~~

Đây là chương trình độc lập `StrictUtf8Demo.java`; lệnh chạy:

~~~bash
javac -encoding UTF-8 StrictUtf8Demo.java
java StrictUtf8Demo
~~~

Kết quả:

~~~text
9
~~~

Giải thích: `"Java "` chiếm 5 byte UTF-8 và emoji chiếm 4 byte. `remaining()` đếm số byte còn lại trong `ByteBuffer` ngay sau khi mã hóa. Nếu thay chuỗi hợp lệ bằng một surrogate đứng đơn lẻ, encoder cấu hình `REPORT` sẽ báo lỗi và nhánh `catch` chạy.

## 5. Cắt chuỗi theo hạn mức byte mà không phá vỡ UTF-8

Giả sử một hệ thống chỉ cho phép **5 byte UTF-8**. Chuỗi `"A😀B"` dài 4 đơn vị UTF-16 nhưng chiếm 6 byte. Cắt tùy ý mảng byte hoặc dùng `substring(0, 5)` sẽ sai: `substring` nhận vị trí **UTF-16 code unit**, không nhận byte, và vị trí 5 còn vượt quá độ dài chuỗi này.

Cách an toàn cho **chuỗi UTF-16 hợp lệ** là duyệt từng code point, cộng đúng số byte UTF-8 của code point đó và dừng trước khi vượt hạn mức. Ví dụ tạo file `Utf8PrefixDemo.java`:

~~~java
import java.nio.charset.StandardCharsets;

public class Utf8PrefixDemo {
    public static void main(String[] args) {
        String input = "A\uD83D\uDE00B";

        String prefix = prefixWithinUtf8Bytes(input, 5);
        System.out.println(prefix.length());
        System.out.println(prefix.codePointCount(0, prefix.length()));
        System.out.println(prefix.getBytes(StandardCharsets.UTF_8).length);
        System.out.println(prefix.equals("A\uD83D\uDE00"));
    }

    public static String prefixWithinUtf8Bytes(String input, int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be nonnegative");
        }

        StringBuilder result = new StringBuilder();
        int written = 0;

        for (int offset = 0; offset < input.length();) {
            int codePoint = input.codePointAt(offset);

            // Một surrogate đứng đơn lẻ biểu thị UTF-16 không hợp lệ.
            if (codePoint >= Character.MIN_SURROGATE
                    && codePoint <= Character.MAX_SURROGATE) {
                throw new IllegalArgumentException("Invalid UTF-16 input");
            }

            int utf8Width;
            if (codePoint <= 0x7F) {
                utf8Width = 1;
            } else if (codePoint <= 0x7FF) {
                utf8Width = 2;
            } else if (codePoint <= 0xFFFF) {
                utf8Width = 3;
            } else {
                utf8Width = 4;
            }

            if (utf8Width > maxBytes - written) {
                break;
            }

            result.appendCodePoint(codePoint);
            written += utf8Width;
            offset += Character.charCount(codePoint);
        }

        return result.toString();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 Utf8PrefixDemo.java
java Utf8PrefixDemo
~~~

Kết quả:

~~~text
3
2
5
true
~~~

Giới hạn là 5 byte nên prefix hợp lệ dài nhất theo **thứ tự từ đầu chuỗi** là `"A😀"`: `A` cần 1 byte, emoji cần 4 byte, tổng 5. Chuỗi này có 3 đơn vị mã UTF-16 và 2 code point. Chữ `B` cuối sẽ vượt hạn mức.

Hàm trên chủ ý từ chối surrogate đơn lẻ; sử dụng `maxBytes - written` thay cho cộng vào số byte đã dùng để tránh phép cộng vượt miền `int` trong trường hợp cực lớn. Hàm dừng tại code point, vì thế không tạo nửa surrogate pair hoặc nửa chuỗi byte UTF-8.

**Tuy nhiên, “không cắt giữa code point” vẫn chưa bảo đảm “không cắt giữa cụm chữ người dùng nhìn thấy”.** Chẳng hạn `e` và dấu kết hợp `U+0301` là hai code point thuộc cùng một chữ hiển thị. Khi giao diện yêu cầu cắt theo grapheme cluster (hoặc xử lý emoji nhiều thành phần), phải có quy tắc phân đoạn cụm chữ phù hợp, sau đó kiểm tra hạn mức byte trên từng cụm. Tránh coi `codePointCount` là giải pháp chung cho mọi bài toán đếm ký tự giao diện.

## 6. Unicode normalization: trông giống nhau vẫn khác byte

Hai chuỗi sau thường hiển thị giống nhau nhưng khác biểu diễn:

~~~java
String decomposed = "e\u0301"; // e + dấu sắc kết hợp
String composed = "\u00E9";   // é dựng sẵn
~~~

| Biểu diễn | UTF-16 units | Code points | UTF-8 bytes |
| --- | ---: | ---: | ---: |
| `decomposed` | 2 | 2 | 3 |
| `composed` | 1 | 1 | 2 |

Nếu yêu cầu nghiệp vụ cần dạng Unicode chuẩn hóa, Java có `java.text.Normalizer`:

~~~java
import java.text.Normalizer;

String normalized = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
boolean sameRepresentation = normalized.equals(composed); // true
~~~

Đây là đoạn minh họa trong phương thức. `NFC` có thể kết hợp các chuỗi decomposed thành dạng dựng sẵn khi Unicode cho phép; vì vậy có thể thay đổi cả số code point lẫn số byte. **Chỉ chuẩn hóa khi quy định dữ liệu hoặc yêu cầu nghiệp vụ cho phép**, không âm thầm chuẩn hóa một payload đã được ký số, băm, so sánh byte chính xác hoặc lưu theo chuẩn giữ nguyên đầu vào.

Để kiểm tra đồng nhất ở mức byte, chuẩn hóa (nếu cần) phải là một bước đã thống nhất ở cả bên gửi và bên nhận trước khi so sánh/mã hóa.

## 7. Ứng dụng thực tế

### Giới hạn độ dài khi ghi dữ liệu

Nếu quy định lưu trữ là “tối đa 1.024 **byte UTF-8**”, không được thay bằng `text.length() <= 1024`. Cần so sánh kết quả của `getBytes(StandardCharsets.UTF_8).length` với 1.024, sau khi đã xác định quy tắc xử lý normalization và dữ liệu không hợp lệ. Một giới hạn theo “ký tự” của cơ sở dữ liệu có thể có ngữ nghĩa riêng, vì vậy cần đọc tài liệu của chính cột và hệ quản trị trước khi suy ra byte limit.

### HTTP hoặc nội dung truyền qua mạng

Với request body đúng bằng các byte của `text.getBytes(StandardCharsets.UTF_8)`, số byte có thể tính từ `payload.length`. Trường `Content-Length` (khi giao thức sử dụng) nói về số byte của thông điệp body theo framing áp dụng, không phải số đơn vị UTF-16 của chuỗi nguồn. Nếu JSON serializer hoặc lớp truyền thông thay đổi nội dung, phải căn theo byte cuối cùng mà lớp đó xuất ra.

### Đọc dữ liệu và giải mã

Khi nhận byte UTF-8, thao tác chuyển ngược là:

~~~java
import java.nio.charset.StandardCharsets;

byte[] payload = /* byte từ nguồn dữ liệu */;
String decoded = new String(payload, StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa, trong đó `payload` phải được cung cấp từ nguồn thực tế. `decoded.length()` tiếp tục là số đơn vị mã UTF-16, **không phải** `payload.length`. Bộ giải mã có thể thay thế chuỗi byte UTF-8 không hợp lệ; nếu cần từ chối đầu vào sai, sử dụng `CharsetDecoder` với `CodingErrorAction.REPORT`.

### Kiểm thử dữ liệu đa ngôn ngữ

Nên có các ca kiểm thử với ASCII, tiếng Việt có dấu, chữ ngoài BMP (emoji), dấu kết hợp, chuỗi rỗng và ranh giới dung lượng (ví dụ đúng 5 byte và vượt 5 byte). Nếu hệ thống có đầu vào không tin cậy, kiểm thử cả surrogate đơn lẻ hoặc byte UTF-8 lỗi theo hợp đồng xử lý của hệ thống.

## 8. Lỗi thường gặp

1. **Dùng `length()` để kiểm tra kích thước UTF-8.** Với `"Việt"`, phép kiểm tra thấy 4 nhưng UTF-8 thực tế cần 6 byte. Sửa bằng cách mã hóa UTF-8 rõ ràng hoặc dùng thuật toán đếm đã kiểm chứng.
2. **Mặc định một `char` luôn là một ký tự Unicode.** `😀` chiếm hai `char`. Nếu duyệt đơn vị mã riêng lẻ để mã hóa/cắt có thể tách cặp surrogate; nên duyệt code point khi bài toán cần code point.
3. **Lấy `codePointCount` thay số byte.** `"A😀B"` có 3 code point nhưng cần 6 byte UTF-8.
4. **Cắt mảng byte UTF-8 ở vị trí bất kỳ rồi giải mã.** Có thể cắt giữa byte đầu và byte tiếp nối của cùng code point, tạo UTF-8 không hợp lệ hoặc ký tự thay thế. Cắt ở ranh giới code point; nếu hiển thị cho người dùng, xét thêm ranh giới grapheme cluster.
5. **Dùng `getBytes()` không nêu charset.** Kết quả không thể hiện rõ yêu cầu UTF-8. Dùng `getBytes(StandardCharsets.UTF_8)`.
6. **Cho rằng hai chuỗi nhìn giống nhau luôn cùng byte.** Các dạng Unicode dựng sẵn/kết hợp và chuỗi chứa ký tự vô hình có thể khác nhau. Xác định quy ước normalization và so sánh theo đúng yêu cầu.
7. **Đếm byte của chuỗi nguồn rồi coi là byte của toàn bộ JSON/HTTP.** Serialization có thể thêm escape, dấu nháy và metadata. Giới hạn theo payload cần đo sản phẩm cuối cùng.

## 9. Bài luyện tập có đáp án

**Bài 1.** Với `String s = "A😀B";`, ba giá trị sau là bao nhiêu?

~~~java
s.length();
s.codePointCount(0, s.length());
s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
~~~

**Đáp án:** `4`, `3` và `6`. UTF-16 dùng hai đơn vị mã cho emoji; UTF-8 dùng bốn byte.

**Bài 2.** Bộ đệm cho phép tối đa 4 byte UTF-8. Có thể giữ nguyên `"A😀B"` không? Nếu phải giữ prefix theo code point, prefix dài nhất là gì?

**Đáp án:** Không, chuỗi ban đầu chiếm 6 byte. Prefix dài nhất theo thứ tự từ đầu chuỗi là `"A"` (1 byte), vì thêm `😀` cần 4 byte nữa và sẽ vượt 4 byte. Không được giữ “nửa emoji”.

**Bài 3.** Dữ liệu `"e\u0301"` và `"\u00E9"` đều hiển thị dạng `é`. Có chắc `getBytes(UTF_8).length` bằng nhau không?

**Đáp án:** Không. Dạng decomposed chiếm 3 byte, dạng composed chiếm 2 byte. Có thể chuẩn hóa theo NFC khi hợp đồng dữ liệu cho phép.

**Bài 4.** Một API nhận JSON giới hạn 100 byte body. Lấy `String.length()` của một thuộc tính để quyết định có vượt giới hạn không?

**Đáp án:** Không. Phải xét JSON được serialize thực tế bằng encoding của request body; một thuộc tính có thể chứa ký tự được escape và body còn chứa tên trường, dấu nháy, ngoặc và các trường khác.

## 10. Ghi nhớ

- `String.length()` → **đơn vị mã UTF-16**.
- `codePointCount(...)` → **Unicode code point**.
- `getBytes(StandardCharsets.UTF_8).length` → **số byte sau khi mã hóa UTF-8**.
- Cả ba phép đếm đều không tự động trả lời “bao nhiêu ký tự người dùng nhìn thấy”.
- Cần giới hạn **byte** thì kiểm tra bằng **byte thực tế**; cắt theo byte phải tôn trọng ranh giới Unicode và, khi cần, ranh giới cụm chữ.

Tài liệu API: [`String.length()` và `codePointCount`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html), [`StandardCharsets.UTF_8`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/StandardCharsets.html) và [`Normalizer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/Normalizer.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-encoding-005">Charset mặc định và ảnh hưởng môi trường hoặc phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Charset mặc định và ảnh hưởng của môi trường, phiên bản JDK

## 1. Vì sao charset mặc định quan trọng?

Trong Java, `String` chứa các ký tự Unicode; còn file, socket, thông điệp và nhiều API I/O trao đổi **byte**. **Encoding** biến ký tự thành byte; **decoding** biến byte thành ký tự. Một dãy byte chỉ có thể được giải mã đúng khi phía đọc dùng đúng charset mà phía ghi đã dùng.

**Charset mặc định** (*default charset*) của một JVM được lấy bằng `Charset.defaultCharset()`. Một số API tự sử dụng charset này khi lập trình viên không truyền đối số `Charset` hay tên encoding. Vì vậy, cùng một mã nguồn có thể ghi ra những byte khác nhau, hoặc đọc cùng một file ra kết quả khác nhau, khi đổi máy chạy hay phiên bản JDK.

Ví dụ, chữ `é` trong UTF-8 có hai byte hex `C3 A9`; trong ISO-8859-1 chỉ có một byte `E9`. Đọc hai byte UTF-8 `C3 A9` dưới ISO-8859-1 sẽ tạo ra hai ký tự `Ã©` thay vì một ký tự `é` — một dạng **mojibake** (chữ bị lỗi mã hóa). Nếu charset không thể biểu diễn một ký tự khi ghi, tùy API/cấu hình bộ mã hóa, ký tự có thể bị thay thế hoặc thao tác có thể phát sinh lỗi; không nên mặc định rằng mọi chuyển đổi đều bảo toàn dữ liệu.

Phân biệt ba điều:

- `String.length()` đếm số *UTF-16 code unit*, không đếm byte UTF-8 và cũng không nhất thiết bằng số ký tự người dùng nhìn thấy.
- `s.getBytes(charset).length` là số byte thực tế sau khi mã hóa bằng charset được chỉ định.
- Charset mặc định chỉ có ý nghĩa với những API **thực sự sử dụng** nó. Một API đã quy định UTF-8 hoặc đã nhận charset tường minh thì không phụ thuộc vào default charset.

## 2. Quy tắc theo phiên bản JDK

| Phiên bản | Charset mặc định thông thường | Hệ quả |
| --- | --- | --- |
| JDK 17 trở xuống | Phụ thuộc hệ điều hành, locale, cấu hình môi trường tại lúc JVM khởi động; nhiều máy Linux/macOS thường dùng UTF-8, một số môi trường Windows dùng mã trang như `windows-1252` | Ứng dụng bỏ qua đối số charset có thể chạy khác nhau giữa các máy. |
| JDK 18 trở lên (JEP 400) | **UTF-8** là mặc định cho Java SE APIs có sử dụng default charset, ngoại trừ trường hợp cấu hình tương thích được hỗ trợ | Kết quả có tính nhất quán cao hơn giữa hệ điều hành; nhưng dữ liệu cũ ở charset khác có thể bị đọc sai. |

JEP 400 (**UTF-8 by Default**) có hiệu lực từ **JDK 18**. Sự thay đổi này liên quan đến **charset mặc định của JVM**, không có nghĩa là mọi file trên hệ thống tự đổi sang UTF-8, mọi thư viện đều mặc định UTF-8, hay mọi byte tùy ý đều là UTF-8 hợp lệ.

Trên JDK 18+, các lựa chọn được hỗ trợ khi khởi động JVM gồm:

- `-Dfile.encoding=UTF-8`: dùng UTF-8 làm default charset.
- `-Dfile.encoding=COMPAT`: chọn default charset theo cách tương thích với JDK 17 trở xuống, thường lấy encoding của môi trường hệ thống.

`COMPAT` được giới thiệu để hỗ trợ chuyển đổi sang JDK 18+, **không nên coi là tùy chọn tương đương trên JDK 17**. Những giá trị `file.encoding` khác `UTF-8` và `COMPAT` không được hỗ trợ theo đặc tả của JDK mới; tránh dựa vào hành vi riêng của một bản JDK.

JDK 17 đưa vào thuộc tính `native.encoding` để quan sát encoding của môi trường host. Trên JDK 18+, `Charset.defaultCharset()` có thể là UTF-8 dù `native.encoding` cho biết một charset khác. `file.encoding` là tùy chọn cấu hình lúc khởi động; **đừng gọi `System.setProperty("file.encoding", "...")` giữa chừng rồi kỳ vọng mọi bộ đọc/ghi và default charset đổi đồng bộ**.

## 3. API nào chịu ảnh hưởng, API nào không?

| Viết theo charset mặc định (phụ thuộc JDK/môi trường) | Phiên bản tường minh, ổn định |
| --- | --- |
| `text.getBytes()` | `text.getBytes(StandardCharsets.UTF_8)` |
| `new String(bytes)` | `new String(bytes, StandardCharsets.UTF_8)` |
| `new InputStreamReader(inputStream)` | `new InputStreamReader(inputStream, StandardCharsets.UTF_8)` |
| `new OutputStreamWriter(outputStream)` | `new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)` |
| `new FileReader(file)` | `new FileReader(file, StandardCharsets.UTF_8)` |
| `new FileWriter(file)` | `new FileWriter(file, StandardCharsets.UTF_8)` |

`StandardCharsets.UTF_8` là hằng số trong `java.nio.charset` và luôn biểu diễn UTF-8. Khi tài liệu hoặc giao thức yêu cầu charset khác (ví dụ file lịch sử dùng `windows-1252`), hãy chỉ định **đúng charset đó** ở cả hai phía thay vì cố ép mọi dữ liệu thành UTF-8.

Một số phương thức `java.nio.file.Files` đã chọn UTF-8 **ngay cả trước JDK 18** khi không truyền charset, chẳng hạn:

- `Files.readString(path)`, `Files.writeString(path, text)`;
- `Files.newBufferedReader(path)`, `Files.newBufferedWriter(path)`;
- `Files.readAllLines(path)`.

Do đó, đừng suy luận rằng mọi overload thiếu đối số charset đều dùng `Charset.defaultCharset()`. Cần đọc hợp đồng của **chính overload đang gọi**. Với dữ liệu nhị phân, `Files.readAllBytes(path)` chỉ trả về byte, không giải mã và không sử dụng charset.

**Ngoại lệ quan trọng:** `System.out` và `System.err` có thể dùng encoding gắn với console/standard streams, khác với `Charset.defaultCharset()`. Trên JDK mới, `Console.charset()` và các thuộc tính liên quan đến stdout/stderr hỗ trợ quan sát cấu hình này. Việc chữ hiển thị sai trong terminal chưa đủ để kết luận byte trong file sai; hãy kiểm tra riêng encoding của file, luồng và terminal.

## 4. Ví dụ Java 17+: byte cố định, charset mặc định thay đổi theo JVM

Tạo file **`DefaultCharsetDemo.java`** (lưu mã nguồn bằng UTF-8):

```java
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class DefaultCharsetDemo {
    public static void main(String[] args) {
        String text = "Aé";

        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        String decodedCorrectly = new String(utf8, StandardCharsets.UTF_8);
        String decodedIncorrectly = new String(utf8, StandardCharsets.ISO_8859_1);

        System.out.println("String.length(): " + text.length());
        System.out.println("UTF-8 byte count: " + utf8.length);
        System.out.println("UTF-8 hex: " + HexFormat.of().withUpperCase().formatHex(utf8));
        System.out.println("UTF-8 round trip correct: " + text.equals(decodedCorrectly));
        System.out.println("ISO-8859-1 decode same text: " + text.equals(decodedIncorrectly));

        Charset actualDefault = Charset.defaultCharset();
        byte[] implicit = text.getBytes();
        System.out.println("Default charset: " + actualDefault.name());
        System.out.println("Default bytes match UTF-8: " + Arrays.equals(implicit, utf8));
        System.out.println("file.encoding: " + System.getProperty("file.encoding"));
        System.out.println("native.encoding: " + System.getProperty("native.encoding"));
    }
}
```

Biên dịch/chạy từ terminal tại thư mục chứa file:

```bash
javac -encoding UTF-8 DefaultCharsetDemo.java
java DefaultCharsetDemo
```

**Năm dòng đầu có kết quả xác định** trên Java 17+:

```text
String.length(): 2
UTF-8 byte count: 3
UTF-8 hex: 41C3A9
UTF-8 round trip correct: true
ISO-8859-1 decode same text: false
```

Các dòng phía sau **phụ thuộc phiên bản JDK, môi trường và tham số khởi động**. Chẳng hạn với JDK 18+ ở chế độ mặc định, `Default charset: UTF-8` và `Default bytes match UTF-8: true`; `native.encoding` có thể giống hoặc khác UTF-8 tùy máy. Không có một stdout chính xác chung cho `file.encoding` và `native.encoding` trên mọi hệ điều hành.

Với JDK 18+ có thể chạy thêm:

```bash
java -Dfile.encoding=UTF-8 DefaultCharsetDemo
java -Dfile.encoding=COMPAT DefaultCharsetDemo
```

Ở máy có native encoding khác UTF-8, lần chạy `COMPAT` giúp quan sát khác biệt. Nếu native encoding vốn đã là UTF-8 thì hai lần chạy có thể cho cùng kết quả `Default bytes match UTF-8: true`. Với JDK 17, có thể thử `java -Dfile.encoding=UTF-8 DefaultCharsetDemo` để kiểm tra ứng dụng trước khi nâng cấp, nhưng chỉ dùng `COMPAT` trên JDK 18+.

**Giải thích:** `"Aé"` có hai UTF-16 code unit nhưng thành ba byte UTF-8 (`41 C3 A9`). Giải mã cùng byte UTF-8 bằng ISO-8859-1 tạo ra chuỗi khác; ghi và đọc phải thống nhất charset. `text.getBytes()` ở cuối ví dụ cố ý dùng overload mặc định để thấy điểm rủi ro; trong mã ứng dụng nên ưu tiên overload truyền charset.

## 5. Ghi/đọc file với charset được chỉ định

Đoạn sau là **mã minh họa đặt bên trong một phương thức có `throws IOException`**, với các import `java.nio.file.Files`, `java.nio.file.Path` và `java.nio.charset.StandardCharsets`:

```java
Path path = Path.of("message.txt");
String message = "Xin chào Việt Nam";

Files.writeString(path, message, StandardCharsets.UTF_8);
String restored = Files.readString(path, StandardCharsets.UTF_8);
System.out.println(message.equals(restored)); // true
```

`Files.writeString` ghi ký tự thành byte UTF-8, còn `Files.readString` đọc chúng bằng UTF-8, không phụ thuộc vào default charset của JVM. Nếu thiếu đối số charset ở **hai phương thức cụ thể này**, mặc định của chúng vẫn là UTF-8. Khi file đến từ hệ thống ngoài, trước tiên phải biết charset thật của nguồn: chọn UTF-8 cho dữ liệu UTF-8; dùng charset khác nếu đặc tả nguồn quy định khác. Không tự động chuyển một file cũ sang UTF-8 chỉ bằng cách thay đổi tham số JVM.

Một luồng byte cũng có thể được khai báo encoding rõ ràng:

```java
// Hai dòng minh họa độc lập về ý tưởng, cần có in/out phù hợp.
var reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8);
var writer = new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8);
```

Trong ứng dụng thực, `in` và `out` là các `InputStream`/`OutputStream` đang được quản lý vòng đời (thường dùng `try-with-resources`); charset phải tương ứng với giao thức và dữ liệu nguồn.

## 6. Khi nâng cấp JDK hoặc chuyển môi trường, lỗi nào dễ xuất hiện?

**Tình huống 1 — file cũ dùng charset của Windows.** Một ứng dụng chạy trên JDK 17 ghi file bằng `new FileWriter(file)` khi default charset là `windows-1252`. Sang JDK 21, `new FileReader(file)` thường giải mã với UTF-8. Nếu file chứa byte không phải UTF-8 hợp lệ, tùy API có thể gặp ký tự thay thế hoặc lỗi đọc; nếu dãy byte hợp lệ nhưng mang nghĩa khác, có thể thấy chữ sai. Cách xử lý đúng là xác định encoding gốc của file, đọc bằng charset đó rồi, nếu cần, **chuyển mã có kiểm tra** sang UTF-8.

**Tình huống 2 — trao đổi byte giữa dịch vụ.** Dịch vụ A dùng `getBytes()` để gửi bản tin, dịch vụ B dùng `new String(bytes)` để nhận. Nếu default charset khác nhau, bản tin có thể sai hoặc mất dữ liệu. Hãy định nghĩa charset trong hợp đồng giao tiếp (thường UTF-8) và dùng rõ ràng ở cả bên gửi lẫn nhận.

**Tình huống 3 — test chạy ổn trên máy lập trình nhưng lỗi tại CI/container.** Image hệ điều hành, locale hoặc bản JDK khác làm thay đổi default charset (đặc biệt với JDK 17 trở xuống). Test chỉ có ASCII có thể vẫn xanh vì nhiều charset mã hóa ASCII giống nhau. Bổ sung dữ liệu có dấu tiếng Việt, ký tự ngoài ASCII và kiểm tra **byte đầu ra** cùng kết quả giải mã.

**Tình huống 4 — nguồn Java và terminal.** File `.java` được lưu theo mã cũ có thể bị `javac` giải mã không đúng nếu encoding biên dịch đổi. Dùng `javac -encoding UTF-8` khi source ở UTF-8; kiểm tra encoding của IDE/build tool. Terminal có thể hiển thị sai dù bytes được mã hóa đúng, nên không lấy vẻ ngoài của console làm phép thử duy nhất.

## 7. Chẩn đoán và chuyển đổi an toàn

Có thể xem cấu hình của JVM bằng:

```bash
java -XshowSettings:properties -version
```

Trong kết quả, chú ý `file.encoding` và (khi có) `native.encoding`. Trong ứng dụng, in `Charset.defaultCharset().name()` như ví dụ trên để biết charset mặc định **thực tế**. Với JDK 18+, so sánh lần chạy mặc định với `-Dfile.encoding=COMPAT` để phát hiện chỗ phụ thuộc vào mã trang cũ. Với JDK 17, thử `-Dfile.encoding=UTF-8` để chuẩn bị nâng cấp.

Quy trình xử lý một lỗi mojibake:

1. Xác định byte **gốc** ở nguồn (đọc hex hoặc log byte); giữ lại bản gốc trước khi sửa.
2. Xác định charset do format, giao thức, header hoặc hệ thống tạo file quy định. Nếu không có hợp đồng, cần điều tra thay vì tin rằng mặc định của JVM có thể “đoán” đúng.
3. Giải mã bằng charset gốc **một lần**, kiểm tra các ký tự đặc trưng và trường hợp dữ liệu không hợp lệ.
4. Khi cần chuẩn hóa, mã hóa lại sang UTF-8, cập nhật metadata/giao thức và test luồng đọc–ghi xuyên môi trường.
5. Thay các điểm dùng `getBytes()`, `new String(bytes)`, `InputStreamReader`, `OutputStreamWriter`, `FileReader`, `FileWriter` không khai báo charset bằng phiên bản phù hợp với hợp đồng dữ liệu; rà cả thư viện ngoài và tùy chọn build.

**Không** “sửa” chữ lỗi bằng cách liên tục đổi giữa các charset hoặc thay `file.encoding` toàn hệ thống mà không hiểu byte nguồn. Đổi cấu hình có thể che lỗi ở một luồng nhưng làm hỏng luồng khác. Nếu dữ liệu đã bị thay thế mất thông tin trong bước giải mã trước đó, chỉ thay charset ở bước sau không khôi phục được byte gốc.

## 8. Checklist khi viết Java bền vững

- Có hợp đồng encoding rõ ràng cho file, HTTP body, CSV, log hoặc dữ liệu trao đổi; ưu tiên UTF-8 khi tự thiết kế format mới.
- Dùng `StandardCharsets.UTF_8` hoặc charset do định dạng quy định khi chuyển `String` ⇄ `byte[]` và khi khởi tạo reader/writer.
- Không đồng nhất default charset với `native.encoding`, encoding của nguồn `.java`, charset console, locale hoặc encoding thật của một file cũ.
- Test ký tự ngoài ASCII trên JDK/môi trường triển khai; xác thực cả bytes lẫn chuỗi đã giải mã.
- Coi `-Dfile.encoding=COMPAT` là công cụ kiểm thử/chuyển tiếp trên JDK 18+, còn việc truyền charset tường minh là hướng giải quyết lâu dài.

## Tài liệu chính thức

- [JEP 400 — UTF-8 by Default](https://openjdk.org/jeps/400).
- [Oracle JDK 18 Release Notes — JEP 400](https://www.oracle.com/java/technologies/javase/18-relnote-issues.html).
- [Oracle Java SE 21 Internationalization Guide — Supported Encodings](https://docs.oracle.com/en/java/javase/21/intl/supported-encodings.html).
- [Java 17 API — `FileReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileReader.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-encoding-006">Dữ liệu sai mã, ký tự thay thế và mất thông tin</a>

<details>
<summary>Xem chi tiết</summary>

# Dữ liệu sai mã, ký tự thay thế và mất thông tin trong Java

## Mục tiêu bài học

Sau bài này, bạn có thể phân biệt dữ liệu byte không hợp lệ, dữ liệu bị đọc bằng sai bảng mã và dữ liệu đã mất ký tự trong lúc chuyển đổi; xác định thời điểm Java dùng ký tự thay thế; kiểm tra lỗi bằng `CharsetDecoder`/`CharsetEncoder`; và chọn cách xử lý để không âm thầm làm hỏng dữ liệu.

## 1. Từ byte đến chuỗi: lỗi xuất hiện ở đâu?

`byte[]` là các byte thô. `String` trong Java biểu diễn văn bản Unicode bằng các đơn vị mã UTF-16; nó không tự lưu thông tin rằng dữ liệu gốc đến từ UTF-8, UTF-16, ISO-8859-1 hay bảng mã nào khác. Khi trao đổi với tệp, mạng, cơ sở dữ liệu hoặc API, chương trình phải **giải mã** byte thành ký tự và **mã hóa** ký tự trở lại byte.

```text
byte[] --[giải mã bằng charset]--> String --[xử lý văn bản]-->
String --[mã hóa bằng charset]--> byte[]
```

Hai bên phải thống nhất bảng mã và tuân thủ quy tắc của bảng mã đó:

- **Malformed input (dữ liệu sai mã / cấu trúc mã không hợp lệ):** dãy byte không phải chuỗi byte hợp lệ theo charset đang dùng, chẳng hạn byte mở đầu UTF-8 thiếu byte tiếp theo; hoặc chuỗi Java chứa surrogate lẻ khi cần mã hóa Unicode.
- **Unmappable character (ký tự không biểu diễn được):** ký tự hợp lệ nhưng bảng mã đích không có biểu diễn tương ứng, chẳng hạn `€` hoặc `á` khi mã hóa bằng US-ASCII. Một số decoder cũng có thể báo unmappable khi charset không ánh xạ một chuỗi byte hợp lệ sang ký tự.
- **Mojibake (chữ bị sai do nhầm charset):** byte hợp lệ nhưng được giải mã theo charset khác với charset đã dùng để tạo chúng; kết quả vẫn là ký tự Unicode hợp lệ nhưng đọc thành chữ rác, ví dụ `café` thành `cafÃ©`.
- **Mất thông tin (lossy conversion):** byte hoặc ký tự gốc bị thay bằng ký tự khác, bỏ qua, hay bị biến đổi không thể đảo ngược. Dấu `�` (U+FFFD, REPLACEMENT CHARACTER) và dấu `?` là hai triệu chứng thường gặp, nhưng bản thân sự xuất hiện của chúng chưa chứng minh dữ liệu đã hỏng.

Cần phân biệt **byte không hợp lệ** với **ký tự trông có vẻ sai**. Một dãy byte có thể hợp lệ trong ISO-8859-1 nhưng không hợp lệ trong UTF-8; và văn bản `cafÃ©` không chứa byte UTF-8 sai cấu trúc sau khi đã thành `String`.

## 2. Những biểu hiện thường gặp

| Biểu hiện | Nguyên nhân thường gặp | Hướng kiểm tra |
| --- | --- | --- |
| `�` xuất hiện giữa câu | Decoder thay byte lỗi bằng U+FFFD; cũng có thể đó là ký tự có sẵn trong nguồn | Giữ byte gốc; xác minh charset; giải mã với chế độ `REPORT` |
| `?` thay chữ có dấu hoặc emoji | Encoder không biểu diễn được ký tự và dùng byte thay thế, thường là `0x3F` trong ASCII | Kiểm tra charset khi ghi và kiểm tra encoder với `REPORT` |
| `Ã©`, `Â`, `â€™` hoặc chữ rác nhưng không có `�` | Đọc byte bằng sai charset, hoặc mã hóa/giải mã nhiều lần | Truy nguồn charset ở từng bước, so sánh byte hex |
| Văn bản bị mất ký tự hoặc bị dính chữ | Hệ thống bỏ qua lỗi bằng `IGNORE`, cắt byte, cắt chuỗi hoặc xử lý stream sai | Kiểm tra chiều dài và ranh giới dữ liệu; tránh `IGNORE` |
| Ký tự đầu văn bản bất thường | BOM không được xử lý đúng hoặc bị hiểu như nội dung | Kiểm tra byte đầu, định dạng tệp và chính sách BOM |
| Một vài chữ giống nhau nhưng so sánh không bằng | Khác dãy code point Unicode (ví dụ ký tự dựng sẵn và tổ hợp) | In code point; xem xét chuẩn hóa Unicode khi nghiệp vụ cần |

Ví dụ byte UTF-8 của `é` là `C3 A9`. Nếu hiểu chúng lần lượt là hai ký tự ISO-8859-1, ta nhận được `Ã©`. Đây là **sai diễn giải byte**, không phải một lỗi cú pháp UTF-8 đã được decoder phát hiện.

## 3. Một dãy UTF-8 sai mã trông như thế nào?

UTF-8 mã hóa một code point Unicode bằng một đến bốn byte. Byte tiếp diễn bắt đầu bằng bit `10`; một byte mở đầu nhiều byte phải được theo sau bởi đúng số byte tiếp diễn, đồng thời phải tuân thủ các giới hạn giá trị Unicode.

Ví dụ:

- `41` là ký tự `A` hợp lệ.
- `C3 A9` là `é` hợp lệ.
- `C3 28` **không hợp lệ**: `C3` cần một byte tiếp diễn nhưng `28` là dấu `(`, không có dạng `10xxxxxx`.
- `E2 82` **bị cắt cụt**: chưa đủ byte để hoàn thành chuỗi ba byte.
- Byte tiếp diễn đứng một mình như `80`, mã hóa quá dài (overlong), mã hóa vùng surrogate hoặc code point vượt `U+10FFFF` đều không phải UTF-8 hợp lệ.

Decoder có thể báo lỗi hoặc áp dụng một chính sách xử lý. Khi chọn **REPLACE**, decoder thay phần dữ liệu gây lỗi bằng chuỗi thay thế của decoder, phổ biến là U+FFFD với UTF-8; số ký tự `�` xuất hiện phụ thuộc cách phân đoạn lỗi, nên không dùng số dấu `�` để suy ra chính xác bao nhiêu byte đã hỏng.

Một `String` đã chứa U+FFFD có thể được mã hóa UTF-8 hoàn toàn hợp lệ thành ba byte `EF BF BD`. Việc mã hóa lại chuỗi này không khôi phục được byte lỗi ban đầu, và kiểm tra tính hợp lệ UTF-8 của byte vừa mã hóa **không thể chứng minh dữ liệu gốc chưa từng bị mất**.

## 4. Hành vi mặc định và ba chính sách xử lý lỗi

Trong Java:

- `new String(bytes, StandardCharsets.UTF_8)` chuyển byte thành văn bản và **thay thế** các đoạn byte không hợp lệ; vì vậy có thể không ném ngoại lệ.
- `text.getBytes(StandardCharsets.US_ASCII)` thay các ký tự không biểu diễn được bằng byte thay thế của charset, nên có thể trả về mảng byte dù văn bản đã mất ký tự.
- `CharsetDecoder` tạo từ `charset.newDecoder()` và `CharsetEncoder` tạo từ `charset.newEncoder()` cho phép đặt hai chính sách riêng bằng `onMalformedInput(...)` và `onUnmappableCharacter(...)`.

| `CodingErrorAction` | Hành vi | Dùng khi nào? |
| --- | --- | --- |
| `REPORT` | Báo lỗi thay vì tự sửa dữ liệu | Nhập liệu cần toàn vẹn, dữ liệu định danh, giao thức, kiểm thử |
| `REPLACE` | Dùng chuỗi/byte thay thế | Hiển thị nội dung bị lỗi khi chấp nhận mất mát và có ghi nhận |
| `IGNORE` | Bỏ qua phần lỗi | Rất hiếm khi hợp lý: có thể xóa dữ liệu mà không thấy dấu vết |

`REPORT` là lựa chọn an toàn khi **bắt buộc giữ nguyên nội dung**. `REPLACE` phù hợp hơn với mục tiêu hiển thị dễ đọc trong một luồng dữ liệu vốn đã có lỗi, với điều kiện không dùng kết quả đó làm bản gốc để lưu đè.

Lưu ý: hành vi thay thế của decoder và encoder không giống nhau. Decoder UTF-8 thường sinh `�` (Unicode U+FFFD). Encoder US-ASCII thường sinh byte `3F`, khi đọc lại thành ký tự `?`. `?` vốn có thể là ký tự hợp lệ do người dùng nhập, vì vậy chỉ nhìn `String` sau chuyển đổi không đủ phân biệt nguyên nhân.

## 5. Ví dụ Java 17+: phát hiện thay vì âm thầm mất dữ liệu

Lưu tệp `CharsetLossDemo.java` với mã nguồn UTF-8:

```java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class CharsetLossDemo {
    public static void main(String[] args) {
        byte[] invalidUtf8 = {0x43, 0x61, (byte) 0xC3, 0x28};
        System.out.println("Giải mã thay thế: "
                + new String(invalidUtf8, StandardCharsets.UTF_8));

        try {
            String strict = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(invalidUtf8))
                    .toString();
            System.out.println("UTF-8 hợp lệ: " + strict);
        } catch (CharacterCodingException e) {
            System.out.println("Báo lỗi: UTF-8 không hợp lệ");
        }

        String original = "Giá €";
        byte[] ascii = original.getBytes(StandardCharsets.US_ASCII);
        System.out.println("Mã hóa ASCII thay thế: "
                + new String(ascii, StandardCharsets.US_ASCII));

        try {
            StandardCharsets.US_ASCII.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(original));
            System.out.println("ASCII biểu diễn được tất cả ký tự");
        } catch (CharacterCodingException e) {
            System.out.println("Báo lỗi: ASCII không biểu diễn được ký tự");
        }
    }
}
```

Biên dịch và chạy trong terminal có hỗ trợ UTF-8:

```bash
javac -encoding UTF-8 CharsetLossDemo.java
java CharsetLossDemo
```

Kết quả (dấu `�` là một ký tự Unicode, không phải dấu `?`):

```text
Giải mã thay thế: Ca�(
Báo lỗi: UTF-8 không hợp lệ
Mã hóa ASCII thay thế: Gi? ?
Báo lỗi: ASCII không biểu diễn được ký tự
```

**Giải thích:** hai byte đầu `43 61` thành `Ca`. Byte `C3` không có byte tiếp diễn hợp lệ nên constructor chuỗi thêm U+FFFD; `28` vẫn thành `(`. Decoder có `REPORT` ném một `CharacterCodingException` (trường hợp này là `MalformedInputException`). Tương tự, ký tự `á` và `€` nằm ngoài US-ASCII; `getBytes(US_ASCII)` thay cả hai bằng `?`, còn encoder nghiêm ngặt báo lỗi unmappable.

Không dùng `try/catch` để tiếp tục lưu bản thay thế như thể không có lỗi. Hãy ghi lại vị trí nguồn, charset mong đợi và loại lỗi; tùy hợp đồng dữ liệu mà yêu cầu nguồn gửi lại, từ chối bản ghi hoặc cách ly dữ liệu hỏng.

## 6. Sai charset nhưng byte vẫn hợp lệ: mojibake

Lưu `MojibakeDemo.java`:

```java
import java.nio.charset.StandardCharsets;

public class MojibakeDemo {
    public static void main(String[] args) {
        byte[] utf8 = "café".getBytes(StandardCharsets.UTF_8);
        String wrong = new String(utf8, StandardCharsets.ISO_8859_1);
        System.out.println("Sai charset: " + wrong);

        String recovered = new String(
                wrong.getBytes(StandardCharsets.ISO_8859_1),
                StandardCharsets.UTF_8);
        System.out.println("Khôi phục: " + recovered);
    }
}
```

```bash
javac -encoding UTF-8 MojibakeDemo.java
java MojibakeDemo
```

```text
Sai charset: cafÃ©
Khôi phục: café
```

Ở đây `wrong` chỉ có các ký tự Unicode hợp lệ. Decoder không thể tự biết tác giả byte vốn dùng UTF-8. Ta khôi phục được **trong tình huống cụ thể này** vì chuỗi sai vẫn giữ trọn từng giá trị byte theo ISO-8859-1 và chưa bị thay thế, cắt hoặc chuẩn hóa.

**Đừng áp dụng công thức đảo charset một cách mù quáng.** Nếu `�`, `?` hoặc thao tác lossy đã thay byte/ký tự gốc; nếu nội dung đã qua nhiều lần chuyển mã; hoặc nếu charset giả định sai, phép đảo không thể phục hồi dữ liệu đáng tin cậy và có thể làm hỏng thêm. Muốn sửa chính xác, ưu tiên quay về byte/tệp/bản ghi nguồn và thông tin charset thực tế.

## 7. Dữ liệu đã mất có phục hồi được không?

Phân biệt ba tình huống:

1. **Còn byte gốc, chưa bị ghi đè:** thường có thể thử giải mã lại đúng charset; dùng decoder `REPORT` để xác nhận tính hợp lệ. Nếu charset không tự mô tả, phải dựa vào metadata, quy định giao thức hoặc nhà cung cấp dữ liệu.
2. **Chỉ còn một `String` bị mojibake nhưng chuyển đổi vừa qua không mất thông tin:** đôi khi đảo chiều được như ví dụ `cafÃ©`, sau khi xác minh nguồn và chuỗi biến đổi.
3. **Byte/ký tự đã bị thay bằng `�`/`?`, bị bỏ qua, cắt mất hoặc ghi đè:** thường không thể suy ra chính xác dữ liệu ban đầu từ kết quả cuối. Cần lấy lại nguồn, bản sao lưu hoặc yêu cầu gửi lại; thuật toán đoán ký tự không bảo đảm toàn vẹn.

`U+FFFD` có thể xuất hiện **hợp lệ** trong tài liệu gốc, cũng như `?` có thể là dấu câu. Vì vậy không nên viết quy tắc kiểu “cứ thấy U+FFFD là xóa” hoặc “đổi mọi `?` thành chữ có dấu”. Nếu cần kiểm soát nhập liệu, hãy xác thực ngay lúc còn giữ mảng byte gốc.

Ngoài encoding, mất thông tin cũng có thể do tự ý chuẩn hóa Unicode, cắt chuỗi theo đơn vị UTF-16, cắt độ dài cột dữ liệu, loại dấu bằng quy tắc nghiệp vụ hoặc chuyển sang charset có miền ký tự nhỏ hơn. Những thao tác này phải được coi là chuyển đổi có chủ ý và được kiểm thử riêng, không gộp với xử lý byte sai mã.

## 8. Chẩn đoán lỗi từng bước

Khi thấy chữ lỗi, kiểm tra theo thứ tự sau:

1. **Khoanh đúng ranh giới gặp lỗi:** tệp đọc vào, byte response HTTP, thông điệp queue, bản ghi database, bước chuyển đổi CSV/JSON, ghi log hay xuất tệp. Đừng chỉ sửa giao diện nếu byte đã hỏng trước đó.
2. **Giữ nguyên byte trước khi giải mã:** lưu mẫu nhỏ ở dạng hex hoặc tệp nhị phân có kiểm soát; tránh đưa dữ liệu nhạy cảm vào log. Với `43 61 C3 28`, kiểm tra được chính xác byte `C3` thiếu continuation.
3. **Xác nhận charset theo hợp đồng:** thông số charset của tệp/API, HTTP Content-Type, cấu hình JDBC, encoding của bên tạo dữ liệu. Không dựa hoàn toàn vào charset mặc định của máy hoặc suy đoán từ vài ký tự.
4. **Giải mã nghiêm ngặt trước khi biến đổi:** `newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT)` cho biết đầu vào có vi phạm charset đã chọn không. Decoder chỉ kiểm tra **tính hợp lệ với charset đang chọn**; nó không tự phát hiện mọi trường hợp chọn sai charset.
5. **So sánh code point và byte:** Java 17 có `HexFormat.of().formatHex(bytes)` để xem hex; `text.codePoints()` duyệt code point Unicode. `String.length()` đếm đơn vị UTF-16 nên có thể khác số code point, nhất là với emoji.
6. **Kiểm tra khứ hồi nếu phù hợp:** `decode(encode(text))` hoặc `encode(decode(bytes))` có thể giúp phát hiện việc mất thông tin, nhưng không chứng minh charset được chọn đúng; dữ liệu mojibake cũng có thể khứ hồi chính xác với một charset sai.
7. **Truy nguồn lần thay đổi đầu tiên:** nếu dữ liệu đã sai trước khi vào Java, sửa nguồn hoặc lớp tích hợp; nếu bị sai tại Java, thay đúng cấu hình/đoạn đọc-ghi và kiểm thử bằng byte thật.

Ví dụ chẩn đoán nhỏ, dùng như **đoạn mã bên trong một phương thức** (đã có biến `byte[] bytes`):

```java
System.out.println(java.util.HexFormat.of().formatHex(bytes));
String text = new String(bytes, StandardCharsets.UTF_8);
text.codePoints().forEach(cp ->
        System.out.printf("U+%04X ", cp));
System.out.println();
```

Lưu ý cần import `java.nio.charset.StandardCharsets` khi dùng đoạn trên. Hex của byte đầu vào và code point của chuỗi sau giải mã cung cấp **hai bằng chứng khác nhau**; không thể thay thế một bên bằng bên còn lại.

## 9. Đọc và ghi dữ liệu an toàn ở ranh giới I/O

**Đọc tệp:** nếu dữ liệu bắt buộc là UTF-8 hợp lệ, hãy cấu hình decoder báo lỗi trước khi tạo chuỗi. Khi làm việc với stream, dùng một decoder xuyên suốt luồng, chẳng hạn truyền `CharsetDecoder` có `REPORT` vào `InputStreamReader(InputStream, CharsetDecoder)`; xử lý `IOException`/`MalformedInputException` phù hợp. Tránh giải mã từng chunk `byte[]` độc lập vì một ký tự UTF-8 có thể nằm vắt qua ranh giới hai lần đọc.

**Ghi tệp:** đặt charset tường minh khi ghi, ví dụ `Files.writeString(path, text, StandardCharsets.UTF_8)` khi định dạng quy định UTF-8. Nếu phải xuất sang một charset hạn chế, dùng `CharsetEncoder` với `REPORT` để phát hiện ký tự không biểu diễn được trước khi ghi; kiểm soát việc ghi một phần và ghi đè.

**HTTP, JSON, cơ sở dữ liệu:** tuân thủ charset/định dạng đã thỏa thuận tại từng ranh giới. JSON qua các kênh UTF-8 hiện đại cần được xử lý nhất quán; dữ liệu truyền trong body HTTP phải phù hợp Content-Type và cấu hình thực tế. Không chữa lỗi ở frontend bằng các phép `new String(text.getBytes(...), ...)` tùy tiện. Với JDBC, ưu tiên API chuỗi Unicode thích hợp, kiểm tra charset/collation/driver kết nối và đường đi xuất nhập dữ liệu, thay vì áp dụng một charset “chữa cháy” sau truy vấn.

**BOM và Unicode:** BOM là tín hiệu nhận dạng đầu tệp trong một số định dạng; không phải mọi API tự loại bỏ BOM, và UTF-8 không bắt buộc có BOM. Phân biệt U+FEFF đầu chuỗi với dữ liệu bị sai mã, chỉ xử lý theo đặc tả tệp. Khác biệt NFC/NFD là vấn đề chuẩn hóa Unicode, không đồng nghĩa với byte UTF-8 không hợp lệ.

## 10. Sai lầm cần tránh

- Dựa vào `Charset.defaultCharset()` hoặc constructor không truyền charset ở ranh giới trao đổi dữ liệu; môi trường khác nhau có thể dẫn tới diễn giải khác nhau.
- Cho rằng Java luôn ném lỗi khi `new String(bytes, UTF_8)` gặp byte sai: constructor này có thể thay thế mà không báo.
- Dùng `CodingErrorAction.IGNORE` để “làm sạch”: byte lỗi biến mất, vị trí từ ngữ có thể bị nối sai và khó điều tra.
- Lưu lại chuỗi đã chứa dấu thay thế lên dữ liệu nguồn, làm mất cơ hội giải mã lại byte gốc.
- Nhầm `�` với dấu `?` hoặc tự động thay tất cả bằng một ký tự đoán từ ngữ cảnh.
- Coi “không có `�`” hoặc “khứ hồi byte bằng nhau” là bằng chứng chắc chắn charset đúng. Mojibake có thể xảy ra mà không có exception.
- Chia byte stream theo chunk rồi decode từng chunk, hoặc cắt `String` giữa cặp surrogate; cả hai có thể sinh lỗi ở ranh giới.
- Gộp ba công việc khác nhau — xác thực byte, chuyển mã văn bản và chuẩn hóa nội dung — thành một thao tác không có kiểm thử.

## 11. Quy tắc lựa chọn và bài tập tự kiểm tra

| Tình huống | Hành động phù hợp |
| --- | --- |
| Nhận dữ liệu nhị phân được khai báo UTF-8, yêu cầu chính xác tuyệt đối | Giữ byte nguồn, giải mã `REPORT`, từ chối hoặc cách ly khi lỗi |
| Chỉ cần hiển thị thông báo cũ đã hỏng | Có thể `REPLACE`, nhưng ghi nhận và không dùng kết quả thay cho bản gốc |
| Xuất chữ Unicode sang US-ASCII | Kiểm tra `CharsetEncoder` với `REPORT`; đổi định dạng sang UTF-8 hoặc xử lý chuyển tự có chủ đích |
| Thấy `cafÃ©` | Tìm charset tạo byte và charset đã đọc; khôi phục từ byte gốc khi có thể |
| Chỉ còn `?` thay cho chữ gốc | Kiểm tra bản sao lưu/nguồn ban đầu; không giả định có thể khôi phục từ dấu `?` |

Tự luyện tập:

1. Thay dãy byte ví dụ thành `E2 82` và `C3 A9`. Dự đoán trường hợp nào `REPORT` ném lỗi, trường hợp nào cho ra `é`; sau đó chạy kiểm chứng.
2. Thử mã hóa `"Xin chào 👋"` bằng `US_ASCII` và `UTF_8`. Xác định bước nào có nguy cơ mất dữ liệu, rồi kiểm tra lại bằng encoder nghiêm ngặt.
3. Mô phỏng gửi byte UTF-8 nhưng đọc bằng ISO-8859-1. Ghi lại hex gốc, văn bản sai, văn bản được đọc lại đúng; giải thích điều kiện cần để phục hồi.
4. Viết một hàm đọc UTF-8 từ `InputStream` với decoder `REPORT` và kiểm thử trường hợp chuỗi nhiều byte bị chia ở biên buffer.

**Nguyên tắc:** byte gốc và charset theo hợp đồng là điểm tựa để chẩn đoán. Kiểm tra lỗi **trước** khi thay thế hoặc bỏ qua; chỉ chấp nhận biến đổi lossy khi đó là quyết định có chủ đích và đã được kiểm thử.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-encoding-007">CharsetEncoder, CharsetDecoder và chính sách REPORT</a>

<details>
<summary>Xem chi tiết</summary>

# CharsetEncoder, CharsetDecoder và chính sách REPORT trong Java

Khi trao đổi dữ liệu với tệp, mạng hoặc hệ thống khác, ứng dụng phải chuyển đổi giữa chuỗi ký tự Unicode và dãy byte. `CharsetEncoder`, `CharsetDecoder` cho phép thực hiện chuyển đổi này một cách có kiểm soát; `CodingErrorAction.REPORT` yêu cầu **phát hiện và báo lỗi dữ liệu không thể chuyển đổi**, thay vì âm thầm thay thế hoặc bỏ ký tự. Đây là lựa chọn phù hợp khi tính toàn vẹn dữ liệu quan trọng.

## 1. Nền tảng: ký tự, byte và charset

- `String` chứa văn bản Unicode dưới dạng các đơn vị mã UTF-16 của Java. Một ký tự ngoài BMP có thể chiếm hai `char` (surrogate pair), vì vậy một `char` không luôn tương đương một ký tự Unicode hoàn chỉnh.
- `Charset` mô tả quy tắc ánh xạ giữa ký tự và byte, ví dụ `StandardCharsets.UTF_8` hoặc `StandardCharsets.US_ASCII`.
- **Encoding** (mã hóa văn bản): `CharBuffer` → `CharsetEncoder` → `ByteBuffer`.
- **Decoding** (giải mã byte): `ByteBuffer` → `CharsetDecoder` → `CharBuffer`.
- `ByteBuffer` và `CharBuffer` có `position`, `limit`, `capacity`. Bộ mã hóa/giải mã đọc từ `position` hiện tại của đầu vào và ghi tại `position` hiện tại của đầu ra. Sau khi ghi thủ công, gọi `flip()` trước khi đọc; trước khi ghi tiếp vào buffer đã đọc dở, dùng `compact()` nếu cần giữ dữ liệu chưa xử lý.

Cùng một dãy byte có thể mang ý nghĩa khác nhau dưới các charset khác nhau. Vì vậy phải biết charset từ giao thức hoặc hợp đồng dữ liệu; không thể suy ra charset chính xác chỉ bằng `REPORT`.

## 2. Vai trò của các API

| API | Vai trò |
| --- | --- |
| `Charset.newEncoder()` | Tạo bộ mã hóa ký tự thành byte cho charset đã chọn. |
| `Charset.newDecoder()` | Tạo bộ giải mã byte thành ký tự cho charset đã chọn. |
| `CodingErrorAction.REPORT` | Dừng và báo lỗi khi gặp dữ liệu malformed hoặc unmappable. |
| `CodingErrorAction.REPLACE` | Dùng chuỗi/byte thay thế khi gặp lỗi, tiếp tục xử lý. |
| `CodingErrorAction.IGNORE` | Bỏ qua dữ liệu gây lỗi, tiếp tục xử lý. |
| `CharacterCodingException` | Lớp ngoại lệ chung cho lỗi chuyển đổi bằng các phương thức tiện ích có kiểm tra. |
| `MalformedInputException` | Đầu vào không hợp lệ về cấu trúc mã hóa. |
| `UnmappableCharacterException` | Đầu vào hợp lệ nhưng không có ánh xạ sang bảng mã đích. |
| `CoderResult` | Kết quả từng bước chuyển đổi: `UNDERFLOW`, `OVERFLOW`, malformed hoặc unmappable. |

### Malformed và unmappable khác nhau thế nào?

| Chiều chuyển đổi | Malformed | Unmappable |
| --- | --- | --- |
| Decode (byte → char) | Byte vi phạm cấu trúc của charset; ví dụ `C3 28` không phải chuỗi UTF-8 hợp lệ. | Chuỗi byte có cấu trúc hợp lệ theo charset nhưng không ánh xạ được thành ký tự Unicode; tùy charset mà trường hợp này có thể xảy ra. |
| Encode (char → byte) | Đầu vào ký tự UTF-16 sai cấu trúc, ví dụ một high surrogate đứng lẻ (`U+D800`). | Ký tự Unicode hợp lệ nhưng charset đích không biểu diễn được; ví dụ ký tự `€` với US-ASCII. |

Cấu trúc lỗi được xác định theo charset đang dùng. Không phải mọi charset đều sinh ra cả hai loại lỗi trong thực tế.

## 3. Cấu hình REPORT đúng cách

Tạo bộ mã hóa và bộ giải mã với charset tường minh; cấu hình cả **malformed input** và **unmappable character**:

~~~java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT);

CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
    .onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT);
~~~

Đoạn trên là ví dụ cấu hình bên trong phương thức hoặc chương trình đã import `java.nio.charset.*`; xem chương trình đầy đủ ở phần tiếp theo.

Một encoder/decoder mới trong Java vốn có chính sách lỗi mặc định `REPORT`; vẫn nên khai báo tường minh để thể hiện hợp đồng dữ liệu và tránh việc bỏ sót khi tái sử dụng đối tượng đã đổi cấu hình. Cả hai nhánh cần được đặt bởi có hai nguyên nhân lỗi độc lập.

**Khác biệt quyết định giữa hai kiểu API:**

- Với `encoder.encode(CharBuffer)` và `decoder.decode(ByteBuffer)` (phương thức tiện ích một lần), lỗi `REPORT` được chuyển thành `CharacterCodingException`, thường là một trong hai lớp con ở bảng trên.
- Với `encoder.encode(in, out, endOfInput)` và `decoder.decode(in, out, endOfInput)` (API từng bước), lỗi `REPORT` **trả về một `CoderResult` lỗi**. Người gọi phải kiểm tra `result.isError()`, rồi gọi `result.throwException()` hoặc có cơ chế xử lý rõ ràng. Chỉ cấu hình `REPORT` mà bỏ qua `CoderResult` vẫn có thể khiến ứng dụng không xử lý đúng lỗi.
- `REPLACE` và `IGNORE` làm thay đổi dữ liệu. Dùng chúng khi nghiệp vụ chủ động chấp nhận mất thông tin; không dùng để kiểm chứng đầu vào nguyên vẹn.

## 4. Chương trình Java 17+: báo lỗi rõ ràng

Lưu đoạn sau thành **StrictCharsetDemo.java** (UTF-8). Ví dụ dùng bộ mã hóa và giải mã riêng cho từng lượt, cố ý tạo ba dạng đầu vào để quan sát ngoại lệ.

~~~java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

public class StrictCharsetDemo {
    static CharsetDecoder strictDecoder(Charset charset) {
        return charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    static CharsetEncoder strictEncoder(Charset charset) {
        return charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    public static void main(String[] args) throws CharacterCodingException {
        ByteBuffer bytes = strictEncoder(StandardCharsets.UTF_8)
            .encode(CharBuffer.wrap("Cà phê"));
        String text = strictDecoder(StandardCharsets.UTF_8)
            .decode(bytes).toString();
        System.out.println("Round-trip UTF-8: " + text);

        try {
            strictDecoder(StandardCharsets.UTF_8)
                .decode(ByteBuffer.wrap(
                    new byte[] {(byte) 0xC3, (byte) 0x28}));
        } catch (MalformedInputException ex) {
            System.out.println(
                "Malformed UTF-8: " + ex.getClass().getSimpleName());
        }

        try {
            strictEncoder(StandardCharsets.US_ASCII)
                .encode(CharBuffer.wrap("A€B"));
        } catch (UnmappableCharacterException ex) {
            System.out.println(
                "Unmappable ASCII: " + ex.getClass().getSimpleName());
        }

        try {
            strictEncoder(StandardCharsets.UTF_8)
                .encode(CharBuffer.wrap(new char[] {'\uD800'}));
        } catch (MalformedInputException ex) {
            System.out.println(
                "Malformed surrogate: " + ex.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 StrictCharsetDemo.java
java StrictCharsetDemo
~~~

Kết quả chuẩn:

~~~text
Round-trip UTF-8: Cà phê
Malformed UTF-8: MalformedInputException
Unmappable ASCII: UnmappableCharacterException
Malformed surrogate: MalformedInputException
~~~

**Giải thích:** Văn bản “Cà phê” được UTF-8 biểu diễn đầy đủ nên khôi phục đúng. Byte `C3` mở đầu một chuỗi hai byte trong UTF-8 nhưng `28` không phải byte tiếp diễn, nên decoder báo malformed. Ký tự `€` hợp lệ nhưng US-ASCII không mã hóa được, nên encoder báo unmappable. Một high surrogate đứng lẻ không tạo thành chuỗi UTF-16 hợp lệ, nên encoder UTF-8 báo malformed. Ngoại lệ được bắt riêng để chỉ rõ loại hỏng; trong ứng dụng thật nên ghi thêm ngữ cảnh như nguồn dữ liệu và vị trí xử lý, tránh xuất dữ liệu nhạy cảm ra log.

## 5. Giải mã theo luồng: phân biệt thiếu byte tạm thời và dữ liệu hỏng

Một ký tự UTF-8 có thể bị chia giữa hai lần nhận byte. Nếu chunk đầu chứa `E2 82` (hai byte đầu của `€`), decoder chưa thể kết luận hỏng khi **còn dữ liệu sẽ đến**. Hãy gọi `decode(..., false)` để nhận `UNDERFLOW`, giữ hai byte chưa dùng, nạp thêm `AC`, rồi gọi `decode(..., true)` khi biết đã hết đầu vào. Cuối cùng gọi `flush(...)`.

Chương trình độc lập **IncrementalDecodeDemo.java** minh họa đường đi này trên bộ đệm đủ lớn để không sinh `OVERFLOW`:

~~~java
import java.nio.*;
import java.nio.charset.*;

public class IncrementalDecodeDemo {
    public static void main(String[] args)
            throws CharacterCodingException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer input = ByteBuffer.allocate(8);
        CharBuffer output = CharBuffer.allocate(8);

        input.put(new byte[] {(byte) 0xE2, (byte) 0x82});
        input.flip();
        CoderResult first = decoder.decode(input, output, false);
        if (first.isError()) first.throwException();
        System.out.println(
            "Lần 1: " + first
            + ", bytes còn lại: " + input.remaining());

        input.compact(); // Giữ hai byte chưa giải mã.
        input.put((byte) 0xAC);
        input.flip();
        CoderResult last = decoder.decode(input, output, true);
        if (last.isError()) last.throwException();
        if (!last.isUnderflow()) {
            throw new IllegalStateException("Output buffer quá nhỏ");
        }

        CoderResult flushed = decoder.flush(output);
        if (flushed.isError()) flushed.throwException();
        if (!flushed.isUnderflow()) {
            throw new IllegalStateException("Output buffer quá nhỏ");
        }

        output.flip();
        System.out.println("Kết quả: " + output);
    }
}
~~~

~~~bash
javac -encoding UTF-8 IncrementalDecodeDemo.java
java IncrementalDecodeDemo
~~~

~~~text
Lần 1: UNDERFLOW, bytes còn lại: 2
Kết quả: €
~~~

Ở lần đầu, decoder không tiêu thụ hai byte dang dở; `compact()` dời chúng về đầu buffer, để lại chỗ cho byte mới. Sau khi nhận `AC` và thông báo hết đầu vào, bộ giải mã hoàn thành ký tự `€`. Nếu báo `endOfInput=true` ngay ở chunk đầu, hai byte dang dở sẽ được coi là đầu vào lỗi ở cuối luồng và `REPORT` báo lỗi.

### Quy trình cho bộ đệm kích thước hữu hạn

Ví dụ trên chỉ có một ký tự và buffer đủ chứa. Với dữ liệu dài hoặc bộ đệm nhỏ, cần vòng lặp theo `CoderResult`:

1. Nạp byte, `flip()` đầu vào, rồi gọi `decode(input, output, endOfInput)`.
2. Nếu `isError()` thì gọi `throwException()` hoặc xử lý rõ ràng theo chính sách lỗi; không bỏ qua.
3. Nếu `isOverflow()` thì xuất các ký tự đang có trong `output` (thường `flip()`, đọc, rồi `clear()` hoặc `compact()`) và tiếp tục decode cùng phần đầu vào còn lại.
4. Nếu `isUnderflow()` và chưa kết thúc luồng, giữ byte chưa giải mã bằng `input.compact()` rồi nạp thêm; không loại bỏ các byte ở cuối chunk. Nếu đã kết thúc luồng, chuyển sang `flush(output)` và cũng xử lý `OVERFLOW` cho tới khi flush trả `UNDERFLOW`.
5. Khi muốn dùng cùng decoder cho **một phiên chuyển đổi mới**, gọi `reset()` trước khi bắt đầu. Không dùng chung một encoder/decoder đang có trạng thái giữa các thread.

Bộ mã hóa từng bước có cấu trúc tương tự với `encode(CharBuffer, ByteBuffer, endOfInput)`, sau đó là `flush(ByteBuffer)`. Đừng nhầm `UNDERFLOW` với “chuyển đổi thành công toàn bộ”: khi `endOfInput=false`, nó còn có thể biểu thị dữ liệu cuối chunk chưa đủ để tạo ký tự.

## 6. REPORT so với chuyển đổi tiện ích thường gặp

~~~java
String text = new String(bytes, StandardCharsets.UTF_8);
byte[] raw = text.getBytes(StandardCharsets.UTF_8);
~~~

Hai lời gọi tiện ích này tiện cho văn bản tin cậy, nhưng các trường hợp malformed/unmappable có thể được **thay thế** âm thầm theo hợp đồng API. Chúng không cung cấp lựa chọn `CodingErrorAction.REPORT`. Với dữ liệu cần kiểm chứng nghiêm ngặt, dùng `newDecoder()` / `newEncoder()` và thiết lập cả hai chính sách lỗi.

Nếu đọc từ `InputStream`, có thể cấp một decoder đã cấu hình cho `InputStreamReader(InputStream, CharsetDecoder)`. Tương tự, `OutputStreamWriter(OutputStream, CharsetEncoder)` nhận encoder đã cấu hình. Cách này giúp tích hợp chính sách nghiêm ngặt vào việc đọc/ghi luồng; vẫn phải xử lý `IOException` phù hợp và đóng/flush stream đúng cách.

## 7. Lỗi phổ biến và cách tránh

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Không truyền charset tường minh | Kết quả phụ thuộc cấu hình/hợp đồng môi trường và có thể sai giữa các hệ thống. | Dùng charset đã thống nhất, thường là `StandardCharsets.UTF_8`. |
| Dùng `REPLACE` khi cần bảo toàn byte/ký tự | Dữ liệu bị thay thế mà không báo cho nghiệp vụ. | Chọn `REPORT` và xử lý ngoại lệ/kết quả lỗi. |
| Chỉ đặt `onMalformedInput(REPORT)` | Có thể bỏ qua chính sách cho lỗi unmappable. | Cấu hình cả `onMalformedInput` và `onUnmappableCharacter`. |
| Nghĩ `decode(in, out, ...)` luôn ném lỗi ngay | Với API từng bước, `REPORT` trả `CoderResult` lỗi. | Kiểm tra `isError()` và gọi `throwException()`. |
| Đánh dấu kết thúc luồng quá sớm | Ký tự đa byte bị chia chunk bị coi là malformed. | Dùng `endOfInput=false` cho chunk chưa cuối; giữ byte còn lại. |
| Bỏ qua `OVERFLOW` | Output buffer không đủ, dữ liệu chưa được xử lý hết. | Xả buffer đầu ra và gọi tiếp. |
| Quên `flip()/compact()/flush()/reset()` | Sai `position`/`limit`, mất byte, sót ký tự hoặc sai trạng thái phiên. | Tuân thủ vòng đời buffer và bộ chuyển đổi. |
| Xem `REPORT` là cách tự nhận biết sai charset | Byte có thể hợp lệ trong charset sai nhưng nội dung vẫn vô nghĩa. | Dùng thông tin charset từ hợp đồng/giao thức, BOM khi được định nghĩa, và kiểm tra nội dung ở tầng nghiệp vụ. |

## 8. Gợi ý áp dụng và tự kiểm tra

- **Import CSV/JSON nghiêm ngặt:** Quy định UTF-8, giải mã bằng decoder dùng `REPORT`, từ chối file có byte malformed trước khi parse nội dung. Lưu ý UTF-8 hợp lệ vẫn có thể chứa dữ liệu sai schema; cần kiểm tra schema riêng.
- **Xuất sang hệ thống dùng charset hạn chế:** Encode bằng `REPORT` để biết chính xác văn bản có ký tự không xuất được; yêu cầu chỉnh dữ liệu hoặc thỏa thuận charset khác, thay vì mất ký tự.
- **Nhận dữ liệu qua socket:** Giữ các byte dang dở giữa các lần đọc, chỉ đặt `endOfInput=true` khi thông điệp hoặc luồng thật sự kết thúc theo giao thức.

Tự trả lời ba câu hỏi: (1) Vì sao `C3 28` khiến UTF-8 decoder báo `MalformedInputException`? (2) Vì sao `€` tạo `UnmappableCharacterException` với US-ASCII encoder? (3) Nếu nhận `E2 82` rồi `AC` ở hai lần đọc khác nhau, vì sao phải giữ byte dư và không đặt `endOfInput=true` ở lần đầu?

**Điểm cần nhớ:** `REPORT` giúp dữ liệu lỗi trở nên quan sát và xử lý được. Với phương thức một lần, xử lý `CharacterCodingException`; với API từng bước, xử lý `CoderResult`, trạng thái buffer và vòng đời `decode/encode → flush → reset` đúng cách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-encoding-008">Đối chiếu chuỗi qua ranh giới tệp, console và dữ liệu truyền</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu chuỗi qua ranh giới tệp, console và dữ liệu truyền trong Java

## Mục tiêu

Khi một chuỗi tiếng Việt hoặc emoji trông đúng trong chương trình nhưng bị lỗi sau khi lưu tệp, in ra terminal hay gửi cho dịch vụ khác, câu hỏi cần đặt ra là: **bên gửi đã biến ký tự thành byte bằng quy tắc nào, và bên nhận đang biến byte trở lại thành ký tự bằng quy tắc nào?**

Sau bài học, bạn có thể kiểm tra một chuỗi ở ba mức **ký tự Unicode, đơn vị UTF-16 và byte**, đối chiếu kết quả qua tệp/console/dữ liệu truyền, phát hiện sai lệch charset, BOM, xuống dòng và chuẩn hóa Unicode, rồi thiết kế quy trình truyền nhận có thể kiểm chứng trong Java 17+.

## 1. Ba lớp cần phân biệt khi so sánh chuỗi

Trong Java, `String` là chuỗi bất biến được biểu diễn theo các **đơn vị mã UTF-16**. Khi lưu ra tệp hoặc truyền trên mạng, chương trình phải **mã hóa** (`encode`) chuỗi thành `byte[]`; ở đầu nhận, các byte được **giải mã** (`decode`) theo một `Charset` để có chuỗi trở lại.

```text
String trong Java ── encode với charset ──> byte[] ── ghi tệp / truyền đi
String trong Java <── decode với charset ─── byte[] <── đọc tệp / nhận về
```

Đây là ba phép so sánh khác nhau:

| Câu hỏi | API dùng trong Java | Ý nghĩa |
| --- | --- | --- |
| Hai chuỗi có cùng dãy đơn vị mã UTF-16? | `a.equals(b)` | So sánh nội dung `String` chính xác, phân biệt chữ hoa/thường và dạng Unicode khác nhau |
| Hai chuỗi có cùng dãy Unicode code point? | `s.codePoints()` | Quan sát ký tự theo code point, tránh nhầm một emoji với hai đơn vị UTF-16 |
| Hai bản ghi có đúng cùng các byte? | `Arrays.equals(bytesA, bytesB)` | Kiểm tra dữ liệu được lưu/truyền thực sự giống nhau |
| Byte có hợp lệ theo charset dự kiến? | `CharsetDecoder` với `CodingErrorAction.REPORT` | Phát hiện dãy byte lỗi trước khi chấp nhận nội dung |

**Ví dụ:** chuỗi `"A😀"` có `String.length() == 3` (một đơn vị cho `A`, hai đơn vị UTF-16 cho emoji), nhưng chỉ có **2 Unicode code point**. Mã hóa UTF-8 cho ra **5 byte**: `41 f0 9f 98 80`. Độ dài `String` không phải số ký tự người đọc nhận thấy, và càng không phải số byte.

```java
String text = "A\uD83D\uDE00";
System.out.println(text.length());                        // 3 đơn vị UTF-16
System.out.println(text.codePointCount(0, text.length())); // 2 code point
System.out.println(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length); // 5 byte
```

Đoạn trên là mã minh họa có thể chạy trong `jshell`; hai mã `\uD83D\uDE00` là surrogate pair của emoji 😀. Không cắt một `String` tại chỉ số tùy ý nếu có thể chia đôi một surrogate pair; với văn bản thực tế, khái niệm "ký tự người dùng nhìn thấy" (grapheme cluster) còn có thể gồm nhiều code point.

## 2. Charset là hợp đồng giữa các ranh giới

`java.nio.charset.StandardCharsets.UTF_8` xác định rõ cách ánh xạ Unicode ↔ byte. Nếu ghi UTF-8 mà đọc với `ISO_8859_1` hoặc Windows-1252, byte có thể còn nguyên nhưng **ký tự sau giải mã khác đi**, thường thấy như chuỗi kiểu `"Ã..."` (mojibake). Ngược lại, nếu mã hóa bằng charset không hỗ trợ ký tự nguồn, dữ liệu có thể bị thay bằng dấu hỏi hoặc ký tự thay thế và không thể khôi phục chỉ bằng việc đổi charset khi đọc.

| Nội dung | UTF-8 | UTF-16BE | Nhận xét |
| --- | --- | --- | --- |
| `A` (U+0041) | `41` | `00 41` | Cùng ký tự, số byte khác nhau |
| `é` (U+00E9) | `c3 a9` | `00 e9` | Nếu đọc `c3 a9` bằng Latin-1 sẽ không nhận lại `é` |
| 😀 (U+1F600) | `f0 9f 98 80` | `d8 3d de 00` | UTF-16 dùng surrogate pair |
 
Trong thực hành hiện đại, **chọn UTF-8 làm hợp đồng trao đổi mặc định** khi không có định dạng giao thức riêng; hai đầu phải nhất trí trên cùng charset. Trong Java 17, không giả định mọi API đều dùng một charset giống nhau. Các overload có truyền `Charset` rõ ràng giúp mã nguồn dễ hiểu và tránh khác biệt theo máy chạy.

```java
import java.nio.charset.StandardCharsets;

byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
```

Đây là đoạn minh họa, giả sử `text` đã tồn tại. `getBytes(UTF_8)` mã hóa; `new String(bytes, UTF_8)` giải mã. Cặp thao tác này cần giữ **đúng byte thực sự đi qua ranh giới**, không mã hóa thành byte rồi quay lại chuỗi bằng charset khác ở giữa.

## 3. Ví dụ chạy được: kiểm tra đồng thời tệp và "dữ liệu truyền"

Chương trình sau tạo dữ liệu nguồn, lấy byte UTF-8 làm payload truyền, ghi chính các byte ấy vào tệp tạm, rồi kiểm tra cả byte lẫn `String` khi đọc về. Nó dùng một phép giải mã sai để cho thấy vì sao **byte không thay đổi vẫn có thể hiển thị sai**.

Lưu thành `BoundaryRoundTrip.java`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;

public class BoundaryRoundTrip {
    public static void main(String[] args) throws IOException {
        // \u00E0 là à; \uD83D\uDE00 là 😀; \n là LF.
        String original = "Xin ch\u00E0o \uD83D\uDE00\n";

        // Giả lập payload mà bên gửi đưa vào kênh truyền.
        byte[] wire = original.getBytes(StandardCharsets.UTF_8);
        String received = new String(wire, StandardCharsets.UTF_8);

        Path file = Files.createTempFile("string-boundary-", ".txt");
        try {
            Files.write(file, wire);
            byte[] disk = Files.readAllBytes(file);
            String restored = Files.readString(file, StandardCharsets.UTF_8);

            System.out.println("file-bytes-match=" + Arrays.equals(wire, disk));
            System.out.println("file-text-match=" + original.equals(restored));
            System.out.println("wire-text-match=" + original.equals(received));

            String wrong = new String(disk, StandardCharsets.ISO_8859_1);
            System.out.println("wrong-charset-match=" + original.equals(wrong));

            String composed = "\u00E9";
            String decomposed = "e\u0301";
            System.out.println("unicode-equal-before=" + composed.equals(decomposed));
            System.out.println("unicode-equal-after=" + composed.equals(
                    Normalizer.normalize(decomposed, Normalizer.Form.NFC)));

            String probe = "A\uD83D\uDE00";
            byte[] encodedProbe = probe.getBytes(StandardCharsets.UTF_8);
            System.out.println("utf16-units=" + probe.length());
            System.out.println("code-points=" + probe.codePointCount(0, probe.length()));
            System.out.println("utf8-length=" + encodedProbe.length);
            System.out.println("utf8-hex=" + HexFormat.of().formatHex(encodedProbe));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac BoundaryRoundTrip.java
java BoundaryRoundTrip
```

Kết quả chuẩn (không phụ thuộc khả năng hiển thị emoji của terminal vì chỉ in các nhãn ASCII và số):

```text
file-bytes-match=true
file-text-match=true
wire-text-match=true
wrong-charset-match=false
unicode-equal-before=false
unicode-equal-after=true
utf16-units=3
code-points=2
utf8-length=5
utf8-hex=41f09f9880
```

Giải thích: `wire` là dữ liệu byte mà một giao thức truyền thực tế phải mang đi; `disk` là byte nằm trên tệp. `Arrays.equals(wire, disk)` xác nhận từng byte giống nhau. `original.equals(restored)` xác nhận nội dung giải mã giống dữ liệu nguồn. `original.equals(wrong)` là `false` vì cùng byte đã bị giải mã theo charset khác.

Trong hệ thống thật, thay "giả lập payload" bằng byte thu được **sau khi đi qua mạng/hàng đợi/message broker**; chỉ gọi `getBytes(UTF_8)` ở đầu nhận từ chuỗi gốc sẽ bỏ qua khả năng dữ liệu đã bị thay đổi trên đường truyền.

## 4. Đối chiếu qua ranh giới tệp

### Ghi và đọc chuỗi với charset rõ ràng

Java 11+ có các phương thức `Files.writeString` và `Files.readString`; chúng dùng UTF-8 nếu không truyền charset. Để hợp đồng được hiển thị ngay trong code, có thể truyền tường minh:

```java
Files.writeString(path, source, StandardCharsets.UTF_8);
String actual = Files.readString(path, StandardCharsets.UTF_8);
boolean sameText = source.equals(actual);
```

Đoạn trên minh họa trong ngữ cảnh đã có các biến `path` (`Path`) và `source` (`String`), cùng xử lý `IOException`. Với dữ liệu nhị phân hoặc khi cần kiểm tra chính xác byte, dùng `Files.write(path, byte[])` và `Files.readAllBytes(path)`.

Một số API cũ hoặc overload không có charset rõ ràng, ví dụ `new FileReader(file)` trong Java 17, dùng charset mặc định của runtime. Không nên trộn tùy tiện cách đọc/ghi dựa trên mặc định với cách cố định UTF-8. Java 18+ chuẩn hóa mặc định UTF-8 cho nhiều API qua JEP 400, nhưng điều đó không làm mọi luồng console và mọi tệp lịch sử tự động trở thành UTF-8.

### BOM, xuống dòng và biến đổi ngoài ý muốn

- **BOM (Byte Order Mark):** Một tệp UTF-8 có thể bắt đầu bằng ba byte `ef bb bf`. Khi giải mã UTF-8 thành chuỗi, dấu này có thể hiện thành ký tự đầu `U+FEFF`; `"header".equals(text)` khi ấy là `false` dù màn hình không cho thấy dấu lạ. Chỉ loại BOM theo chính sách định dạng tệp đã thống nhất, không xóa mọi `U+FEFF` tùy tiện giữa nội dung.
- **Xuống dòng:** `\n` (LF) và `\r\n` (CRLF) là các dãy khác nhau cả ở `String` và byte. API `readAllLines` trả về các dòng **không giữ ký tự kết thúc dòng**; việc ghép lại có thể thay đổi dữ liệu. Nếu cần đối chiếu nguyên vẹn, ưu tiên `readString` hoặc byte thô.
- **Đầu/cuối văn bản:** `trim()`, `strip()`, việc tự thêm `println` hoặc bộ lọc chuyển đổi newline có thể đổi nội dung. Ghi lại các bước biến đổi này khi truy tìm khác biệt.
- **Byte không tương thích charset:** Phương thức tạo `String` tiện lợi có thể thay byte lỗi bằng `U+FFFD`. Một chuỗi đọc được không tự chứng minh rằng toàn bộ byte đều hợp lệ theo định dạng đã cam kết.

Khi biết định dạng tệp cho phép BOM ở đầu, có thể kiểm tra:

```java
if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
    text = text.substring(1);
}
```

Đây chỉ là đoạn minh họa sau khi đã đọc tệp thành biến `text`. Việc loại BOM nên diễn ra **một lần tại ranh giới nhập dữ liệu**, có ghi rõ quy định.

## 5. Đối chiếu qua console

`System.out.println(text)` đi qua một `PrintStream`, lớp mã hóa ký tự của console/terminal và phần mềm hiển thị. Dòng chữ nhìn thấy trên màn hình là **kết quả trình bày**, không phải phép kiểm chứng rằng byte UTF-8 hay Unicode gốc còn nguyên.

Những nơi có thể xảy ra khác biệt gồm: charset của terminal hoặc output stream, shell/IDE xử lý Unicode, phông chữ không có glyph, cấu hình chuyển hướng stdout sang tệp, và newline mà `println` tự thêm. Ví dụ một emoji có thể thành hình vuông do thiếu glyph dù byte hợp lệ. Ngược lại, khi code ghi đúng chuỗi ra console nhưng redirect sang tệp theo encoding khác, đọc tệp ấy bằng UTF-8 có thể lỗi.

Cách kiểm tra đáng tin hơn là ghi **bản chẩn đoán ASCII** bên cạnh nội dung:

```java
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

String text = "A\uD83D\uDE00";
System.out.println("utf8=" +
        HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8)));
text.codePoints().forEach(cp ->
        System.out.printf("U+%04X%n", cp));
```

Đây là đoạn minh họa có thể chạy trong `jshell`. Với chuỗi này, kết quả là:

```text
utf8=41f09f9880
U+0041
U+1F600
```

`codePoints()` cho thấy giá trị Unicode của từng code point; `HexFormat` cho thấy byte khi **chủ động mã hóa UTF-8**. Các số này không nhất thiết chính là byte do `System.out` gửi xuống terminal. Để kiểm tra file stdout đã redirect, cần xem trực tiếp byte của file đó.

Đối với nhập từ `System.in`, đừng vô tình dùng bộ đọc mặc định khác với charset mà phía terminal gửi. Nếu đã xác nhận input là UTF-8, có thể tạo `new InputStreamReader(System.in, StandardCharsets.UTF_8)` (trong một `BufferedReader`); nếu terminal thực tế gửi charset khác, việc ép UTF-8 ở Java sẽ không tự sửa dữ liệu. `System.console()` có thể trả về `null` trong IDE hoặc lúc redirect, nên không giả định chương trình luôn gắn console tương tác.

## 6. Đối chiếu qua dữ liệu truyền (HTTP, socket, message)

Một giao thức truyền byte không tự biết "chuỗi Java" là gì. Bên gửi phải **quy định mã hóa payload**; bên nhận phải giải mã nhất quán. Với nội dung dạng text truyền qua HTTP, có thể công bố charset trong `Content-Type`, chẳng hạn:

```http
Content-Type: text/plain; charset=UTF-8
```

Nếu không có `charset` hoặc định dạng giao thức quy định riêng, bên nhận phải áp dụng quy tắc của giao thức đó; không nên đoán charset từ cách chữ hiển thị. Trong Java HTTP Client (Java 11+), một cặp lựa chọn có charset tường minh là:

```java
// Trích đoạn minh họa; giả sử đã có HttpRequest.Builder builder,
// HttpResponse<byte[]> response, và String text.
builder.header("Content-Type", "text/plain; charset=UTF-8")
       .POST(HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8));

byte[] receivedBytes = response.body();
String receivedText = new String(receivedBytes, StandardCharsets.UTF_8);
```

Hai đoạn trên minh họa hai phía khác nhau; đây **không** phải chương trình độc lập gửi request. Khi kết nối thật, kiểm tra header mà dịch vụ gửi về trước khi quyết định charset giải mã. Một cách khác là chọn `BodyHandlers.ofString(StandardCharsets.UTF_8)` nếu hợp đồng đầu nhận đã chắc chắn. Với socket, dùng `OutputStreamWriter` và `InputStreamReader` cùng charset; còn giao thức nhị phân thì xử lý frame và chiều dài ở cấp byte.

**Độ dài payload** là số byte, không phải `String.length()`. Ví dụ `"A😀"` dài 3 đơn vị UTF-16 nhưng chiếm 5 byte UTF-8. Độ dài của frame, payload, hash hoặc `Content-Length` phải được tính từ byte thực sự gửi đi. Thư viện HTTP thường tự tính độ dài khi gửi body; tránh tự điền một giá trị bằng `text.length()`.

Cũng phải phân biệt **charset** (byte ↔ ký tự) với **truyền tải/biến đổi khác**: nén gzip, escaping JSON, Base64 và việc chuyển `\n` thành hai ký tự `\\n` trong dữ liệu JSON là các lớp xử lý riêng. Byte payload JSON có thể khác chuỗi hiển thị sau khi parser xử lý escape mà vẫn mang cùng giá trị văn bản theo giao thức.

## 7. Kiểm tra byte ngay tại ranh giới nhận

Ví dụ `new String(bytes, StandardCharsets.UTF_8)` có thể thay byte UTF-8 không hợp lệ bằng U+FFFD. Nếu một thông điệp cần giữ nguyên nội dung, kiểm tra **byte nhận được** trước khi đưa vào parser hoặc ghi đè dữ liệu nguồn:

~~~java
byte[] malformed = {(byte) 0xC3, 0x28};
try {
    String value = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(malformed))
            .toString();
    System.out.println(value);
} catch (CharacterCodingException error) {
    System.out.println("valid-utf8=false");
}
~~~

Đặt đoạn trong phương thức và import `java.nio.ByteBuffer` cùng các lớp `java.nio.charset.*` liên quan. Kết quả cho dữ liệu trên là `valid-utf8=false`. Kết quả giải mã strict chứng minh byte tuân thủ charset **đã chọn**, nhưng không tự chứng minh phía gửi đã chọn đúng charset theo giao thức. Bài **CharsetEncoder, CharsetDecoder và chính sách REPORT** đi sâu vào chuyển đổi nghiêm ngặt, bao gồm decoder theo luồng.

## 8. Hai chuỗi nhìn giống nhau vẫn có thể khác nhau

Unicode có thể biểu diễn ký tự có dấu theo nhiều cách. Chẳng hạn `"\u00E9"` (một code point `é`) và `"e\u0301"` (chữ `e` cộng dấu sắc kết hợp) thường nhìn giống nhau nhưng:

- `String.equals` ban đầu là `false` vì dãy đơn vị UTF-16 khác.
- UTF-8 byte cũng khác: `c3 a9` so với `65 cc 81`.
- Sau `Normalizer.normalize(value, Normalizer.Form.NFC)`, hai chuỗi trở nên bằng nhau.

```java
import java.text.Normalizer;

String first = "\u00E9";
String second = "e\u0301";
boolean rawEqual = first.equals(second); // false
boolean normalizedEqual = first.equals(
        Normalizer.normalize(second, Normalizer.Form.NFC)); // true
```

Đoạn trên là mã minh họa. **Chuẩn hóa phải là quyết định của hợp đồng dữ liệu**, không thực hiện bừa ở giữa chuỗi ký số, đường dẫn, mã định danh hay payload cần kiểm tra toàn vẹn byte: nó thay đổi biểu diễn nguồn. Nếu mục đích là hiển thị hoặc tìm kiếm không phân biệt biến thể chuẩn hóa, hãy thống nhất việc chuẩn hóa **trước khi so sánh** ở cả hai phía.

## 9. Quy trình chẩn đoán khi dữ liệu đi qua nhiều lớp

Khi dữ liệu khác giữa file, console và bên nhận, làm theo thứ tự sau:

1. **Chọn mốc chuẩn:** giữ `String` gốc tại bên gửi và `byte[]` ngay trước khi rời tiến trình. Nếu tệp là mốc chuẩn, lấy byte thô bằng `Files.readAllBytes`.
2. **Xác định hợp đồng:** ghi charset, định dạng payload, BOM có được phép hay không, quy ước newline, Unicode normalization, và các lớp nén/escape.
3. **Đối chiếu byte ở từng ranh giới:** dùng `Arrays.equals` khi có hai mảng byte, hoặc ghi kích thước và bản hex/hash của byte nguồn và byte nhận. Hash chỉ chứng minh byte đồng nhất theo mức tin cậy của thuật toán, không tự chứng minh ký tự đúng.
4. **Giải mã bằng charset đã thống nhất:** dùng `new String(bytes, charset)` nếu đầu vào đáng tin; dùng `CharsetDecoder` strict khi cần từ chối byte không hợp lệ.
5. **Đối chiếu nội dung Unicode:** so sánh bằng `String.equals`, xem `codePoints()` khi cần, kiểm tra BOM/newline/normalization nếu byte hoặc text chưa khớp.
6. **Cuối cùng mới đánh giá hiển thị:** so sánh terminal, editor, log viewer. Lỗi font hoặc cách hiển thị có thể khác với lỗi byte thật.

| Triệu chứng | Kiểm tra ưu tiên | Khả năng và hướng xử lý |
| --- | --- | --- |
| Tệp có chữ `Ã` hoặc ký tự lạ | Xem byte hex, charset lúc ghi và lúc đọc | Sai charset; thống nhất UTF-8 cho các đầu liên quan |
| Emoji hiện thành ô vuông ở console | Kiểm tra code point và UTF-8 hex trước khi in | Có thể do terminal/font; đừng đổi byte khi dữ liệu gốc đúng |
| File đọc về khác ở đầu chuỗi | Kiểm tra `ef bb bf`, `U+FEFF` | BOM; thống nhất chính sách xử lý |
| Hai chuỗi trông giống nhưng `equals` sai | Kiểm tra code point, dấu kết hợp, khoảng trắng/newline | Unicode normalization hoặc ký tự ẩn/khác dấu xuống dòng |
| API nhận sai số byte hay cắt payload | Đếm `byte[].length` và kiểm tra framing | Dùng số byte, không dùng `String.length()` |
| Đầu nhận có `�` (U+FFFD) | Giải mã strict từ byte gốc | Byte lỗi hoặc charset không khớp, không nên chấp nhận mất dữ liệu im lặng |
| Nội dung trong IDE đúng, redirect stdout bị lỗi | Xem byte của file redirect và encoding terminal | Console/luồng redirect có cấu hình mã hóa khác |

## 10. Bài tập tự kiểm tra

1. `String s = "A\uD83D\uDE00";` có `s.length()` bằng bao nhiêu? Có bao nhiêu code point? Mã hóa UTF-8 được bao nhiêu byte?
2. Một tệp lưu byte `c3 a9`. Đọc theo UTF-8 rồi theo ISO-8859-1 có giữ nguyên cùng ký tự không? Byte gốc có thay đổi chỉ bởi thao tác đọc không?
3. Hai hệ thống truyền `"e\u0301"` và `"\u00E9"`. Tại sao giao diện có thể hiển thị giống nhau dù `String.equals` là `false`? Trường hợp nào nên chuẩn hóa trước khi so sánh?
4. Nếu `System.out.println(text)` hiện ký tự lỗi, bạn nên kiểm tra `String.codePoints()`, hex UTF-8, hay đổi mã hóa tệp ngay? Vì sao?
5. Khi truyền `"A😀"` trong HTTP body UTF-8, có thể lấy `String.length()` làm độ dài body không?

**Gợi ý đáp án:** (1) 3 đơn vị UTF-16, 2 code point, 5 byte; (2) khác ký tự vì giải mã bằng charset khác, byte gốc không đổi; (3) khác cách biểu diễn Unicode, chỉ chuẩn hóa khi hợp đồng cho phép; (4) kiểm tra code point và hex để xác định lỗi dữ liệu hay lỗi hiển thị trước; (5) không, body UTF-8 có 5 byte.

### Tài liệu tham khảo

- [`String` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`StandardCharsets` và `CharsetDecoder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/package-summary.html)
- [`Files` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`Normalizer` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-encoding-009">Vì sao Base64, escape và chuyển charset là các thao tác khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao Base64, escape và chuyển charset là các thao tác khác nhau

## 1. Ba bài toán khác nhau dù đều hay được gọi là “encode”

Trong Java, ta thường cần đưa văn bản vào file, truyền dữ liệu nhị phân qua một giao thức chỉ nhận ký tự, hoặc nhúng văn bản vào JSON/HTML. Cả ba tình huống đều có thể xuất hiện từ *encoding*, nhưng thực chất là ba loại biến đổi có **đầu vào, đầu ra và mục đích khác nhau**:

- **Mã hóa ký tự bằng charset** (*character encoding/decoding*): quy định cách chuyển **ký tự trong `String` ↔ byte**. Ví dụ UTF-8 biến ký tự `é` thành hai byte `C3 A9`. Khi **chuyển charset (transcoding)**, ta giải mã dãy byte theo charset nguồn rồi mã hóa văn bản theo charset đích.
- **Base64**: biến **byte ↔ chuỗi ký tự ASCII từ bảng chữ cái Base64** nhằm mang dữ liệu nhị phân qua môi trường dạng văn bản. Base64 không biết byte ban đầu là tiếng Việt, ảnh hay file ZIP. Nó cũng **không phải mã hóa bảo mật**.
- **Escape**: biểu diễn các ký tự có ý nghĩa đặc biệt một cách an toàn, đúng cú pháp trong **một ngữ cảnh cụ thể**, như `\"` trong Java string literal, `\n` khi hiển thị dấu xuống dòng, `&lt;` trong HTML, hoặc `\\` trong chuỗi JSON. Escape không tự quyết định cách lưu ký tự thành byte.

Điểm mấu chốt là: **Base64 giải quyết việc biểu diễn byte dưới dạng ký tự**, **charset giải quyết ánh xạ văn bản và byte**, còn **escape giải quyết ý nghĩa cú pháp của ký tự trong một ngữ cảnh**. Chúng có thể xuất hiện liên tiếp trong một quy trình, nhưng không thay thế được nhau.

## 2. So sánh trực tiếp

| Tiêu chí | Charset / chuyển charset | Base64 | Escape / unescape |
| --- | --- | --- | --- |
| Câu hỏi giải quyết | “Những ký tự này thành byte nào, và ngược lại?” | “Làm sao biểu diễn các byte bằng ký tự ASCII?” | “Ký tự này phải viết thế nào để không bị hiểu là cú pháp?” |
| Đầu vào điển hình | `String` khi encode; `byte[]` khi decode | `byte[]` khi encode; chuỗi Base64 khi decode | Văn bản/ký tự cần nhúng vào ngữ cảnh |
| Đầu ra điển hình | `byte[]` hoặc `String` | Chuỗi ký tự Base64 hoặc `byte[]` | Chuỗi biểu diễn đã escape hoặc nội dung đã unescape |
| Quy tắc phụ thuộc | Charset như UTF-8, ISO-8859-1 | Bảng chữ cái Base64 thường hoặc URL-safe, padding | Cú pháp Java, JSON, HTML, regex, URL... |
| Có hiểu nội dung văn bản? | Có: giải nghĩa byte là ký tự theo charset đã chọn | Không: xử lý byte theo nhóm bit | Có ngữ cảnh cú pháp; không nhất thiết hiểu mã hóa byte |
| Có tạo tính bí mật? | Không | Không | Không |
| Sai kiểu thường gặp | Mojibake `HÃ©`, ký tự thay thế | Chuỗi Base64 sai alphabet/padding | Dấu nháy, xuống dòng hoặc ký tự đặc biệt bị hiểu sai |

Ví dụ có thể hình dung bằng luồng dữ liệu:

```text
String: "Hé"
    | getBytes(UTF_8)           [mã hóa ký tự bằng UTF-8]
    v
byte[]: 48 C3 A9
    | Base64.getEncoder()       [mã hóa Base64 trên byte]
    v
String Base64: "SMOp"
    | Base64.getDecoder()       [đảo Base64: lấy lại byte]
    v
byte[]: 48 C3 A9
    | new String(bytes, UTF_8)  [giải mã byte thành ký tự]
    v
String: "Hé"
```

Dòng `SMOp` **không phải “chuỗi Hé đã đổi sang charset Base64”**. Base64 là cách biểu diễn ba byte `48 C3 A9`; thông tin UTF-8 do bước phía trước quyết định. Nếu nhận lại những byte đó nhưng dùng sai charset, văn bản có thể sai dù Base64 đã decode chính xác.

## 3. Charset: `String` và `byte[]` là hai loại dữ liệu khác nhau

Trong Java, `String` biểu diễn chuỗi ký tự Unicode; còn `byte[]` chỉ là các giá trị byte, **không kèm nhãn charset**. Để gửi hoặc ghi một chuỗi thành byte, phải xác định quy tắc mã hóa ký tự:

```java
import java.nio.charset.StandardCharsets;

String text = "Hé";
byte[] utf8Bytes = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(utf8Bytes, StandardCharsets.UTF_8);
```

Đây là đoạn minh họa đặt trong phương thức; chương trình độc lập đầy đủ có ở phần 6.

Với `"Hé"`:

- UTF-8: `48 C3 A9` — `H` có byte `48`, `é` có hai byte `C3 A9`.
- ISO-8859-1: `48 E9` — `é` được biểu diễn bằng một byte `E9`.
- Cả hai là cách biểu diễn **cùng văn bản** theo hai charset khác nhau; số byte không nhất thiết bằng `String.length()`.

### Chuyển charset đúng nghĩa là gì?

Giả sử có `byte[] oldBytes` được biết chắc là mã hóa theo ISO-8859-1, và hệ thống nhận cần UTF-8:

```java
String unicodeText = new String(oldBytes, StandardCharsets.ISO_8859_1);
byte[] newBytes = unicodeText.getBytes(StandardCharsets.UTF_8);
```

Đó là thao tác **transcoding**: *giải mã byte với charset nguồn → có văn bản → mã hóa văn bản bằng charset đích*. Không thể chỉ đổi tên charset hoặc đặt Base64 lên mảng byte rồi coi dữ liệu đã thành UTF-8.

Nếu giải mã byte UTF-8 `48 C3 A9` bằng ISO-8859-1, Java cho `HÃ©`, hiện tượng thường gọi là **mojibake**. Điều này không cho thấy Base64 hỏng; lỗi nằm ở bước diễn giải byte thành ký tự.

**Cẩn thận với dữ liệu không hợp lệ hoặc không thể biểu diễn:** các overload tiện lợi như `new String(bytes, charset)` có thể dùng ký tự thay thế cho chuỗi byte lỗi theo cơ chế của API; `String.getBytes(charset)` có thể thay thế ký tự không mã hóa được. Nếu ứng dụng bắt buộc phát hiện lỗi thay vì thay thế, hãy cấu hình `CharsetDecoder`/`CharsetEncoder` với `CodingErrorAction.REPORT` và xử lý exception. Với văn bản Unicode có thể chứa nhiều ngôn ngữ, UTF-8 thường là lựa chọn phù hợp cho trao đổi dữ liệu.

Ưu tiên **khai báo charset tường minh** bằng `StandardCharsets.UTF_8` khi đọc/ghi; đừng buộc người đọc phải suy đoán `getBytes()` hay `new String(bytes)` dùng charset mặc định nào của môi trường.

## 4. Base64 làm gì với byte?

Base64 đọc luồng byte theo nhóm bit, ánh xạ các nhóm 6 bit thành ký tự ASCII và có thể bổ sung dấu `=` làm padding. Theo dạng thông dụng, **3 byte → 4 ký tự Base64**; với 1 hoặc 2 byte cuối, kết quả được padding để hoàn chỉnh nhóm 4 ký tự. Vì vậy Base64 thường làm **dữ liệu lớn hơn**, không phải nén.

Trong Java, dùng `java.util.Base64`:

```java
byte[] bytes = "Hé".getBytes(StandardCharsets.UTF_8);
String encoded = Base64.getEncoder().encodeToString(bytes); // "SMOp"
byte[] decoded = Base64.getDecoder().decode(encoded);
String text = new String(decoded, StandardCharsets.UTF_8); // "Hé"
```

Lưu ý: `Base64.getEncoder()` nhận **byte**, không nhận trực tiếp `String`; hàm `encodeToString` trả về các ký tự Base64 do encoder tạo ra. Bước `getBytes(UTF_8)` là quyết định riêng và cần được phía nhận biết để khôi phục văn bản ban đầu.

### Một luồng sai charset vẫn có Base64 hoàn toàn hợp lệ

Giả sử lấy chuỗi `"Hé"`, chuyển thành byte UTF-8 rồi Base64 thành `SMOp`. Phía nhận decode Base64 ra đúng `48 C3 A9` nhưng viết:

```java
String wrong = new String(
        Base64.getDecoder().decode("SMOp"),
        StandardCharsets.ISO_8859_1
); // "HÃ©"
```

Base64 đã hoàn thành chính xác trách nhiệm của nó; **chuỗi sai do chọn ISO-8859-1 thay vì UTF-8**. Việc “thử Base64 một lần nữa” không sửa được lỗi charset.

### Base64 không mã hóa bí mật và không xác minh tính toàn vẹn

Ai có chuỗi Base64 đều có thể decode dữ liệu. Base64 không cung cấp mật khẩu, mã hóa đối xứng, tính bí mật, chữ ký hoặc kiểm tra nguồn gốc. Nếu cần bảo vệ thông tin, phải áp dụng cơ chế bảo mật phù hợp như TLS và mã hóa/xác thực thực sự; Base64 chỉ có thể là lớp biểu diễn bổ sung.

Có nhiều biến thể: `Base64.getEncoder()` dùng alphabet thông dụng với `+` và `/`; `Base64.getUrlEncoder()` dùng alphabet URL-safe với `-` và `_`. Chọn đúng biến thể theo giao thức đang dùng; `withoutPadding()` chỉ nên sử dụng khi định dạng trao đổi cho phép. URL-safe Base64 vẫn **khác** phần trăm hóa URL (*percent-encoding*).

## 5. Escape là quy tắc cú pháp, không phải quy tắc byte

Một chuỗi chứa ký tự đặc biệt có thể làm hỏng cấu trúc nơi nó được nhúng vào. Escape xử lý vấn đề **ký tự nào có nghĩa đặc biệt trong ngữ cảnh đó**.

### Escape trong Java string literal

Đoạn mã nguồn:

```java
String message = "A\nB";
```

Trong file Java, ký hiệu `\n` biểu diễn **một ký tự xuống dòng** trong giá trị runtime. Sau khi chương trình chạy, `message` không còn giữ dấu `\` và chữ `n` riêng lẻ tại vị trí đó. Trình biên dịch Java đã diễn giải cú pháp escape.

Ngược lại:

```java
String visible = message.replace("\n", "\\n");
```

`visible` chứa ký tự `A`, `\`, `n`, `B`. Đó là một **cách biểu diễn có escape** để nhìn thấy xuống dòng trên cùng một dòng, chứ không phải chuyển charset. Nếu mã hóa cả hai `String` thành UTF-8, giá trị đầu là `41 0A 42`, còn giá trị sau là `41 5C 6E 42`.

Phép `replace` phía trên **chỉ là minh họa cho một ký tự xuống dòng**; nó chưa phải bộ serialize/unescape đầy đủ. Ví dụ, nếu dữ liệu gốc đã chứa hai ký tự `\` và `n`, chỉ dùng `replace("\\n", "\n")` để “giải escape” sẽ không phân biệt được đâu là ký tự gốc và đâu là ký hiệu được thêm sau đó. Định dạng escape cần quy tắc bao gồm cả việc xử lý **dấu backslash có sẵn**.

### Escape phụ thuộc nơi sử dụng

| Ngữ cảnh | Vấn đề | Ví dụ minh họa |
| --- | --- | --- |
| Java string literal | Dấu nháy, backslash, ký tự xuống dòng có cú pháp riêng | `"He said \"Hi\""` |
| JSON string | Dấu `"` và `\` phải escape; ký tự điều khiển phải biểu diễn đúng quy tắc JSON | Giá trị chứa xuống dòng được serialize thành `\n` bên trong JSON string |
| HTML text | Các dấu `&` và `<` có thể mở entity hoặc markup | `<` thành `&lt;` trong phần văn bản HTML |
| Regex | `.`, `*`, `+`, `[` ... là toán tử | Regex khớp dấu chấm literal trong Java: `"\\."` |
| URL | Một số byte cần được percent-encode theo thành phần URL | Khoảng trắng UTF-8 trong phần URL tương ứng có thể biểu diễn `%20` |

**Không tồn tại một hàm “escape mọi thứ” dùng an toàn cho mọi ngữ cảnh.** HTML text khác HTML attribute; JSON string khác regex; từng phần của URL cũng có quy tắc riêng. Với JSON hoặc HTML trong ứng dụng thực tế, ưu tiên thư viện serializer/encoder phù hợp với đúng ngữ cảnh thay vì tự nối chuỗi hoặc thay vài ký tự bằng tay.

### Thứ tự xử lý có thể quan trọng

Khi ghi JSON chứa chuỗi Base64 của file nhị phân, ta có thể:

1. Mã hóa `byte[]` thành **Base64 string**.
2. Đưa **Base64 string** vào một trường dữ liệu của đối tượng JSON.
3. Dùng JSON serializer để escape ký tự và dựng JSON đúng cú pháp.
4. Khi gửi JSON qua mạng, mã hóa **toàn bộ văn bản JSON** thành byte theo charset của giao thức (thông thường UTF-8).

Ở phía nhận, làm ngược các lớp thích hợp: đọc byte JSON thành văn bản theo charset quy định → JSON parser lấy giá trị string → Base64 decoder khôi phục byte gốc. JSON escape và Base64 tác động lên **những lớp khác nhau**. Nếu byte gốc là file ảnh, **không được tùy tiện tạo `String` từ byte ảnh rồi `getBytes` lại**; truyền byte trực tiếp qua Base64.

## 6. Java 17+ runnable: thấy rõ ba phép biến đổi

Tạo tệp `EncodingLayersDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

public class EncodingLayersDemo {
    private static String hex(byte[] bytes) {
        return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(bytes);
    }

    public static void main(String[] args) {
        String text = "Hé";
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        byte[] latin1 = text.getBytes(StandardCharsets.ISO_8859_1);
        String base64 = Base64.getEncoder().encodeToString(utf8);
        byte[] decodedBytes = Base64.getDecoder().decode(base64);
        String restored = new String(decodedBytes, StandardCharsets.UTF_8);
        String wrong = new String(utf8, StandardCharsets.ISO_8859_1);

        String original = "A\nB";
        String escaped = original.replace("\n", "\\n");

        System.out.println("text: " + text);
        System.out.println("utf8: " + hex(utf8));
        System.out.println("latin1: " + hex(latin1));
        System.out.println("base64: " + base64);
        System.out.println("decoded bytes: " + hex(decodedBytes));
        System.out.println("round-trip: " + text.equals(restored));
        System.out.println("wrong charset: " + wrong);
        System.out.println("newline bytes: "
                + hex(original.getBytes(StandardCharsets.UTF_8)));
        System.out.println("escaped text: " + escaped);
        System.out.println("escaped bytes: "
                + hex(escaped.getBytes(StandardCharsets.UTF_8)));
        System.out.println("escaped differs: " + !original.equals(escaped));
    }
}
```

Lệnh biên dịch/chạy trong thư mục chứa file:

```bash
javac --release 17 -encoding UTF-8 EncodingLayersDemo.java
java EncodingLayersDemo
```

Stdout đã được kiểm chứng:

```text
text: Hé
utf8: 48 C3 A9
latin1: 48 E9
base64: SMOp
decoded bytes: 48 C3 A9
round-trip: true
wrong charset: HÃ©
newline bytes: 41 0A 42
escaped text: A\nB
escaped bytes: 41 5C 6E 42
escaped differs: true
```

**Đọc kết quả theo đúng lớp:**

- `utf8` và `latin1` khác nhau vì **charset** khác nhau; một đối tượng `String` nguồn vẫn là `"Hé"`.
- `base64` là chuỗi ASCII `SMOp` biểu diễn chính xác **ba byte UTF-8**, không trực tiếp biểu diễn ký tự Unicode theo quy tắc charset.
- `decoded bytes` bằng dữ liệu đầu vào `utf8`; phục hồi đúng chữ khi dùng lại `UTF_8`.
- `wrong charset` thành `HÃ©`: mất nghĩa văn bản vì chọn cách đọc byte sai.
- `newline bytes` có byte `0A` thật, còn `escaped bytes` có hai byte `5C 6E` tương ứng dấu `\` và chữ `n`. Trường hợp này là **biến đổi văn bản để hiển thị**, không phải Base64 hay đổi charset.
- `HexFormat` (Java 17+) ở đây chỉ dùng để **in byte thành các cặp chữ số thập lục phân để quan sát**. Hex cũng là một cách biểu diễn byte, khác với charset và khác với ý nghĩa cú pháp escape.

## 7. Chọn đúng thao tác theo yêu cầu

**Tình huống A — đọc file văn bản UTF-8:** Đọc byte rồi decode bằng UTF-8; ghi file bằng cách encode `String` sang UTF-8. Các API như `Files.readString(path, StandardCharsets.UTF_8)` và `Files.writeString(path, text, StandardCharsets.UTF_8)` biểu diễn rõ chủ ý. Không cần Base64 nếu định dạng file chấp nhận byte văn bản trực tiếp.

**Tình huống B — chuyển file ISO-8859-1 sang UTF-8:** Decode byte bằng ISO-8859-1 thành `String`, sau đó encode thành byte UTF-8. Nếu byte ban đầu đã bị hiểu sai hoặc dữ liệu chứa ký tự không biểu diễn được, phải đánh giá dữ liệu gốc thay vì kỳ vọng một lớp Base64 sửa được.

**Tình huống C — đưa ảnh hoặc file ZIP vào trường JSON:** Base64 encode trực tiếp **byte[] của file** rồi đặt chuỗi kết quả vào đối tượng JSON. Đừng gọi `new String(imageBytes, UTF_8)` vì ảnh không phải văn bản UTF-8. Dùng JSON serializer cho phần cú pháp JSON.

**Tình huống D — nhúng nội dung người dùng vào HTML hoặc JSON:** Escape/serialize **đúng ngữ cảnh đầu ra**. Base64 không tự làm HTML/JSON an toàn; đổi charset cũng không làm mất ý nghĩa cú pháp của dấu `<`, `"` hoặc `&`.

**Tình huống E — dấu phân cách trong URL:** Dùng quy tắc percent-encoding cho thành phần URL liên quan. URL-safe Base64 giúp bảng chữ cái bớt xung đột với một số ký tự đặc biệt, nhưng không thay thế quy tắc dựng và encode URI/URL.

**Tình huống F — truyền token hoặc dữ liệu nhạy cảm:** Base64 chỉ giúp biểu diễn dữ liệu; không bảo vệ khỏi người đọc có quyền truy cập token. Cần cơ chế kiểm soát truy cập, mã hóa/xác thực và giao thức vận chuyển phù hợp.

## 8. Lỗi phổ biến và bài tự kiểm tra

1. **Gọi Base64 là mã hóa bảo mật:** dữ liệu Base64 có thể decode dễ dàng, nên không được dùng để giấu mật khẩu.
2. **Decode Base64 thành `String` mà không chọn charset:** Base64 decoder trả về byte; nếu byte đó vốn là văn bản UTF-8, dùng `new String(bytes, StandardCharsets.UTF_8)`.
3. **Đổi charset bằng `new String(bytes, wrongCharset)`:** hành động này chỉ đổi cách diễn giải byte, có thể gây mojibake. Transcoding phải biết charset nguồn chính xác.
4. **Chuyển binary tùy ý qua `String`:** byte của file ảnh, ZIP hoặc dữ liệu nén không mặc định là văn bản; có thể gây thay thế dữ liệu khi decode sai.
5. **Tưởng `\n` và LF thực luôn giống nhau:** trong Java source `"\n"` là một LF, còn `"\\n"` là hai ký tự `\` và `n`. Mỗi cách có byte khác nhau sau khi encode.
6. **Dùng escape sai ngữ cảnh:** `Pattern.quote` dành cho **regex literal**, không phải JSON/HTML; HTML escaping không biến dữ liệu thành Base64.
7. **Nhầm URL-safe Base64 với URL percent-encoding:** chúng có bảng chữ cái, mục đích và quy tắc giải mã khác nhau.
8. **Bỏ qua dữ liệu lỗi:** Base64 decoder có thể báo lỗi khi gặp chuỗi không hợp lệ; charset decoder cấu hình nghiêm ngặt có thể phát hiện byte không giải mã được. Chọn chính sách lỗi rõ ràng ở ranh giới dữ liệu.

Thử trả lời ba câu để tự kiểm tra:

- Khi `"Hé"` trở thành ba byte `48 C3 A9`, thao tác đó là **UTF-8 encoding** hay Base64? → **UTF-8 encoding**.
- Khi ba byte đó trở thành `SMOp`, thao tác đó là **Base64** hay chuyển charset? → **Base64**.
- Khi một LF thực được biểu diễn cho người đọc là hai ký tự `\` và `n`, thao tác đó là **escape ký tự theo quy ước hiển thị** hay mã hóa Base64? → **Escape/biểu diễn văn bản**.

**Ghi nhớ:** **charset** gắn nghĩa ký tự với byte, **Base64** biểu diễn byte bằng chữ ASCII, còn **escape** biểu diễn ký tự cho đúng ngữ pháp tại nơi sử dụng. Khi ghép nhiều lớp, hãy ghi rõ thứ tự thao tác, kiểu dữ liệu sau từng bước và quy tắc đảo chiều để tránh nhầm mã hóa dữ liệu, trình bày cú pháp và bảo mật.


</details>

- [Quay lại đầu trang](#back-to-top)
