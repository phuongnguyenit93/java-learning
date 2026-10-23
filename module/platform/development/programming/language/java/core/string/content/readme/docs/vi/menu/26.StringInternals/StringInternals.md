<a id="back-to-top"></a>

# Bộ nhớ và cài đặt

## Menu
- [Phân biệt mô hình UTF-16 của API với biểu diễn nội bộ](#string-internals-001)
- [Compact Strings và các dạng LATIN1, UTF16 trong OpenJDK](#string-internals-002)
- [Chi phí đối tượng, mảng lưu dữ liệu và chuỗi tạm](#string-internals-003)
- [Bộ nhớ đệm hashCode là chi tiết cài đặt](#string-internals-004)
- [Constant pool và vai trò của Constable, ConstantDesc](#string-internals-005)
- [Phép nối qua StringConcatFactory và invokedynamic](#string-internals-006)
- [Chi tiết substring, intern và tối ưu có thể khác giữa phiên bản JDK](#string-internals-007)

## <a id="string-internals-001">Phân biệt mô hình UTF-16 của API với biểu diễn nội bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt mô hình UTF-16 của API với biểu diễn nội bộ của Java String

Khi làm việc với `String`, lập trình viên Java thường nghe hai mệnh đề có vẻ trái ngược nhau:

- API của `String` dùng **chỉ số đơn vị mã UTF-16** (*UTF-16 code units*). Vì thế `length()`, `charAt()`, `substring()` và nhiều vị trí trả về đều tuân theo cách đánh chỉ số này.
- Trên những bản **OpenJDK/HotSpot hiện đại**, `String` thường dùng cơ chế **Compact Strings**: một mảng `byte[]` bên trong và thông tin cho biết dữ liệu đang được lưu ở dạng Latin-1 hay UTF-16. Một chuỗi ASCII có thể chỉ cần một byte cho mỗi đơn vị mã trong vùng dữ liệu của nó.

Hai mệnh đề **không mâu thuẫn**: **mô hình mà API cam kết** khác với **cách JVM được quyền tối ưu hóa biểu diễn trong bộ nhớ**. Chương trình cần dựa vào hợp đồng API; chi tiết lưu trữ nội bộ chỉ nên dùng để hiểu hiệu năng/bộ nhớ và luôn gắn với phiên bản, nhà triển khai JVM.

## 1. Ba khái niệm phải phân biệt

| Khái niệm | Đơn vị | Vai trò |
| --- | --- | --- |
| **UTF-16 code unit** | Một giá trị 16 bit; Java biểu diễn bằng `char` | Đơn vị chỉ số của `String.length()`, `charAt()`, `substring()` |
| **Unicode code point** | Một giá trị Unicode như U+0041 hoặc U+1F600 | Đơn vị thường dùng khi xử lý ký tự Unicode, tra cứu `codePointAt()`, duyệt `codePoints()` |
| **Byte lưu trữ hoặc truyền đi** | 8 bit | Có thể là byte nội bộ do JVM chọn, hoặc byte bên ngoài tạo bởi `getBytes(charset)`; hai trường hợp là khác nhau |

**Không được đồng nhất ba đơn vị này.** Trong UTF-16:

- Code point thuộc **BMP** (Basic Multilingual Plane), chẳng hạn `A` (U+0041) hoặc `Đ` (U+0110), thường chiếm **một** code unit UTF-16.
- Code point **ngoài BMP**, chẳng hạn emoji `😀` (U+1F600), cần **hai** code unit UTF-16 liên tiếp: **high surrogate** `U+D83D` và **low surrogate** `U+DE00`.
- Một **ký tự người dùng nhìn thấy** (*grapheme cluster*) còn có thể gồm **nhiều code point**; ví dụ chữ `e` đi với dấu sắc kết hợp `U+0301` thường trông giống `é`.

`String` là chuỗi các `char` UTF-16 theo mô hình API. Nó thậm chí có thể chứa một surrogate đứng một mình, mặc dù surrogate đơn lẻ **không phải Unicode scalar value hợp lệ** để chuyển trực tiếp thành UTF-8 chuẩn.

## 2. Các API dựa trên chỉ số UTF-16, không dựa trên số byte

Xét giá trị Java:

~~~java
String text = "A\uD83D\uDE00B"; // A😀B, viết bằng Unicode escapes
~~~

Mảng code unit được quan sát qua API:

~~~text
Chỉ số UTF-16:   0       1          2       3
charAt(index): U+0041  U+D83D     U+DE00   U+0042
Code point:    U+0041  <--- U+1F600 --->   U+0042
Hình hiển thị:   A            😀               B
~~~

Vì vậy:

- `text.length()` là **4**: tổng số code unit UTF-16.
- `text.codePointCount(0, text.length())` là **3**: `A`, `😀`, `B`.
- `text.charAt(1)` là **high surrogate** `\uD83D`, **không phải** toàn bộ emoji.
- `text.codePointAt(1)` kết hợp cặp surrogate bắt đầu tại vị trí 1 và trả về `0x1F600`.
- `text.indexOf("B")` là **3**, vì kết quả dùng chỉ số UTF-16.
- `text.substring(1, 3)` lấy cả cặp surrogate nên tạo đúng `😀`.
- `text.substring(1, 2)` chỉ lấy nửa đầu cặp surrogate, tạo một `String` dài 1 code unit nhưng không chứa code point emoji hoàn chỉnh.

### Chương trình Java 17 chạy độc lập

~~~java
// Utf16ApiDemo.java
public class Utf16ApiDemo {
    public static void main(String[] args) {
        String text = "A\uD83D\uDE00B";

        System.out.println("length = " + text.length());
        System.out.println("codePointCount = "
                + text.codePointCount(0, text.length()));

        for (int i = 0; i < text.length(); i++) {
            System.out.printf("charAt(%d) = U+%04X%n",
                    i, (int) text.charAt(i));
        }

        System.out.printf("codePointAt(1) = U+%04X%n",
                text.codePointAt(1));
        System.out.println("indexOf(B) = " + text.indexOf("B"));
        System.out.println("offsetByCodePoints(1, 1) = "
                + text.offsetByCodePoints(1, 1));

        String emoji = text.substring(1, 3);
        System.out.println("emoji slice equals original = "
                + emoji.equals("\uD83D\uDE00"));

        String half = text.substring(1, 2);
        System.out.println("half.length = " + half.length());
        System.out.println("half is high surrogate = "
                + Character.isHighSurrogate(half.charAt(0)));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 Utf16ApiDemo.java
java Utf16ApiDemo
~~~

Stdout chính xác:

~~~text
length = 4
codePointCount = 3
charAt(0) = U+0041
charAt(1) = U+D83D
charAt(2) = U+DE00
charAt(3) = U+0042
codePointAt(1) = U+1F600
indexOf(B) = 3
offsetByCodePoints(1, 1) = 3
emoji slice equals original = true
half.length = 1
half is high surrogate = true
~~~

Ở đây `offsetByCodePoints(1, 1)` tìm chỉ số UTF-16 sau **một code point** bắt đầu tại chỉ số 1: bỏ qua cả hai code unit của emoji và trả về 3. Phương thức này hữu ích khi cần xử lý Unicode theo code point mà vẫn tương tác với các API nhận chỉ số UTF-16.

## 3. `char`, `chars()`, `codePoints()`: tên gần nhau nhưng khác ý nghĩa

`char` là kiểu nguyên thủy **16 bit không dấu** trong Java. `String.charAt(i)` trả về **một** `char`; vì vậy nó có thể trả về một surrogate, thay vì một ký tự hiển thị hoàn chỉnh.

Hai phương thức stream quan trọng:

~~~java
String text = "A\uD83D\uDE00B";

text.chars();      // IntStream của các đơn vị UTF-16: 0x0041, 0xD83D, 0xDE00, 0x0042
text.codePoints(); // IntStream của code point:       0x0041, 0x1F600,       0x0042
~~~

Đây là đoạn minh họa gọi API, không phải một chương trình độc lập. `chars()` nâng từng `char` thành một giá trị `int`; `codePoints()` gộp hai surrogate hợp lệ thành một code point. Nếu một surrogate không có cặp hợp lệ, `codePoints()` vẫn có thể phát ra giá trị surrogate chưa ghép đó theo quy tắc API; đừng hiểu rằng thao tác này tự “sửa” chuỗi lỗi thành Unicode hợp lệ.

Khi cần chuyển một code point sang các code unit UTF-16 của Java, dùng `Character.toChars(codePoint)`; khi cần tính số `char` mà code point chiếm, dùng `Character.charCount(codePoint)` (1 hoặc 2 cho code point hợp lệ). Các phép tính này phục vụ **mô hình UTF-16 của API**, không mô tả số byte bên trong đối tượng `String`.

## 4. Biểu diễn nội bộ: lịch sử và Compact Strings

Ở nhiều phiên bản OpenJDK trước Java 9, nội dung `String` được giữ bằng mảng `char[]`. Từ Java 9, **JEP 254 — Compact Strings** chuyển sang mảng `byte[]` kèm thông tin mã hóa nội bộ (`coder`). Nếu mọi code unit nằm trong U+0000–U+00FF, biểu diễn **LATIN1** có thể dùng một byte/code unit; nếu có đơn vị ngoài vùng đó, biểu diễn **UTF16** cần hai byte/code unit trong mảng dữ liệu.

Ví dụ, `"café"` gồm 4 code unit đều thuộc Latin-1, còn `"Đ"` chứa U+0110 ngoài Latin-1. Trên HotSpot có bật Compact Strings, phần dữ liệu của hai chuỗi **có thể** dùng hai dạng lưu trữ khác nhau; `length()`, `charAt()` và các phép cắt theo chỉ số vẫn có ngữ nghĩa UTF-16.

| Mức quan sát | Điều được cam kết |
| --- | --- |
| **Public API** | `length()` đếm UTF-16 code units; `charAt()`, `substring()`, `codePointAt()` tuân theo API |
| **Chi tiết OpenJDK** | `byte[]`, `coder`, LATIN1/UTF16, tối ưu JIT và cách chia sẻ bộ nhớ có thể thay đổi |
| **Số byte trao đổi** | Được xác định bởi charset chỉ định cho `getBytes(charset)`, không bởi `coder` |

Không dùng reflection vào các trường `private` của `String` để giải quyết bài toán Unicode, và đừng suy ra kích thước heap từ `length()`. Bài **Compact Strings và các dạng LATIN1, UTF16** đi sâu vào quy tắc chọn coder, layout, tùy chọn VM và phép thử tương ứng.

## 5. Vì sao đổi biểu diễn nội bộ không làm đổi `String.length()`?

Một ví dụ then chốt:

~~~java
String latin = "café";  // 4 code unit
String wide = "Đ";      // 1 code unit
~~~

Trên OpenJDK/HotSpot có Compact Strings, `latin` thường có dữ liệu nội bộ dạng một byte/code unit, còn `wide` có thể cần hai byte/code unit. **Nhưng**:

~~~java
System.out.println(latin.length()); // 4
System.out.println(wide.length());  // 1
~~~

Kết quả `4` và `1` luôn dựa trên **số code unit UTF-16 theo API**. Tương tự, dù có tối ưu bộ nhớ, `charAt()` vẫn trả về một đơn vị UTF-16: runtime tự giải mã giá trị từ biểu diễn nội bộ trước khi đưa ra API.

Đừng hình dung rằng chương trình Java nhìn được trực tiếp từng byte đã nén thông qua `charAt()`. Nó nhìn **giá trị logic**; cách đọc dữ liệu vật lý là công việc của thư viện và JVM.

## 6. Byte tạo từ `getBytes(charset)` là một chuyện khác

`String.getBytes(StandardCharsets.UTF_8)` sinh dữ liệu để **xuất ra ngoài** theo UTF-8. `String.getBytes(StandardCharsets.UTF_16BE)` sinh byte theo UTF-16 big-endian. Đây là **hai phép encode**, không phải cửa sổ nhìn vào `byte[]` nội bộ.

Với `"A😀B"`:

| Cách nhìn | Kết quả |
| --- | --- |
| `String.length()` | 4 đơn vị UTF-16 |
| `codePointCount(...)` | 3 code point |
| `getBytes(UTF_8)` | 6 byte: `41 F0 9F 98 80 42` |
| `getBytes(UTF_16BE)` | 8 byte: `00 41 D8 3D DE 00 00 42` |
| `getBytes(UTF_16)` của Java | 10 byte: `FE FF` (BOM) + 8 byte UTF-16BE |
| Bộ nhớ `String` bên trong HotSpot | Không xác định chỉ từ các API trên; phụ thuộc cơ chế cài đặt và đối tượng |

### Chương trình Java 17 chạy độc lập

~~~java
// StringBytesDemo.java
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class StringBytesDemo {
    static void show(String name, byte[] bytes) {
        System.out.println(name + " = "
                + HexFormat.of().withUpperCase().formatHex(bytes)
                + " (" + bytes.length + " bytes)");
    }

    public static void main(String[] args) {
        String text = "A\uD83D\uDE00B";

        System.out.println("UTF-16 code units = " + text.length());
        show("UTF-8", text.getBytes(StandardCharsets.UTF_8));
        show("UTF-16BE", text.getBytes(StandardCharsets.UTF_16BE));
        show("UTF-16", text.getBytes(StandardCharsets.UTF_16));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 StringBytesDemo.java
java StringBytesDemo
~~~

Stdout:

~~~text
UTF-16 code units = 4
UTF-8 = 41F09F988042 (6 bytes)
UTF-16BE = 0041D83DDE000042 (8 bytes)
UTF-16 = FEFF0041D83DDE000042 (10 bytes)
~~~

Byte `FE FF` trong ví dụ cuối là **Byte Order Mark (BOM)** do `StandardCharsets.UTF_16` của Java tạo ra theo quy tắc encoder này. `UTF_16BE` không tự thêm BOM như ví dụ. Bộ byte **được xuất ra** thay đổi theo charset; ta không từ đó suy ra HotSpot đang lưu các byte vật lý của `String` với cùng thứ tự hoặc có BOM trong bộ nhớ.

Nếu phải gửi văn bản qua mạng hay ghi file, **luôn chỉ định charset theo giao ước**, ví dụ `StandardCharsets.UTF_8`. Đừng sử dụng trường nội bộ của `String` như định dạng trao đổi dữ liệu.

## 7. Code point cũng chưa chắc là một ký tự nhìn thấy

Ngoài khác biệt code unit/code point, Unicode còn có **grapheme cluster**: một cụm mà người đọc thường coi là một ký tự. Ví dụ:

- `"\u00E9"`: `é` ở dạng **một code point** U+00E9; `length() == 1`.
- `"e\u0301"`: chữ `e` + dấu sắc kết hợp U+0301, **hai code point** và **hai code unit**; hình hiển thị có thể giống `é`.
- Emoji gồm các thành phần nối bằng **zero-width joiner (ZWJ)** hoặc modifier màu da có thể là **nhiều code point**, nhưng một biểu tượng hiển thị.

Hai `String` nhìn giống nhau chưa chắc `equals()` trả về `true`. Java không tự chuẩn hóa Unicode trong `String.equals()`. Nếu bài toán yêu cầu so sánh văn bản đã chuẩn hóa, thực hiện bước riêng theo `java.text.Normalizer`, rồi xác định thêm quy tắc ngôn ngữ/locale khi cần. Việc chuẩn hóa **không** thuộc cơ chế Compact Strings.

### Ví dụ Java 17

~~~java
// UnicodeNormalizationDemo.java
import java.text.Normalizer;

public class UnicodeNormalizationDemo {
    public static void main(String[] args) {
        String composed = "\u00E9";   // é, một code point
        String decomposed = "e\u0301"; // e + dấu sắc kết hợp

        System.out.println("composed length = " + composed.length());
        System.out.println("decomposed length = " + decomposed.length());
        System.out.println("raw equals = " + composed.equals(decomposed));

        String normalized = Normalizer.normalize(
                decomposed, Normalizer.Form.NFC);
        System.out.println("NFC equals = " + composed.equals(normalized));
    }
}
~~~

Lệnh chạy:

~~~bash
javac --release 17 UnicodeNormalizationDemo.java
java UnicodeNormalizationDemo
~~~

Kết quả:

~~~text
composed length = 1
decomposed length = 2
raw equals = false
NFC equals = true
~~~

`Normalizer.Form.NFC` đưa tổ hợp kết hợp thích hợp về dạng tiền tổ hợp chuẩn. Nó **không** giải quyết mọi vấn đề đếm ký tự hiển thị, chia cụm emoji hay xử lý ngôn ngữ tự nhiên.

## 8. Những ảnh hưởng thực tế khi lập trình

### Cắt chuỗi không làm vỡ cặp surrogate

Nếu vòng lặp đi qua `charAt(i)`, có thể bạn đang xử lý **nửa code point**. Khi cần duyệt code point, nên dùng `text.codePoints()` hoặc bộ đôi `codePointAt(i)` và `Character.charCount(cp)`:

~~~java
// Duyệt từng code point, không cắt đôi surrogate pair.
// Đoạn minh họa, đặt trong một phương thức Java có biến String text.
for (int i = 0; i < text.length(); ) {
    int cp = text.codePointAt(i);
    // Xử lý code point cp tại đây.
    i += Character.charCount(cp);
}
~~~

Nếu muốn xử lý **grapheme cluster**, code point vẫn chưa đủ; cần giải pháp phân đoạn Unicode phù hợp phiên bản và mục tiêu, cùng các bài kiểm thử cho dấu kết hợp/emoji. Không cắt bằng `substring(0, n)` với `n` suy ra từ “số ký tự nhìn thấy” mà chưa chuyển đổi thành chỉ số UTF-16 an toàn.

### Tính kích thước lưu trữ hoặc giới hạn đầu vào

`String.length()` **không đo byte** của dữ liệu gửi qua HTTP hoặc file. Để kiểm tra giới hạn byte với charset cụ thể:

~~~java
int utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;
~~~

Đây là đoạn minh họa (cần import `StandardCharsets` và có biến `String text`). Với dữ liệu lớn, có thể dùng encoder/stream thay vì tạo cả mảng chỉ để đếm. Nếu hệ thống đặt giới hạn theo **code point** hoặc **grapheme**, phải triển khai đúng đơn vị mà yêu cầu quy định.

### Hiệu năng và bộ nhớ

Compact Strings có thể giảm phần dữ liệu trong bộ nhớ đối với nhiều chuỗi Latin-1. Nhưng phép tính đơn giản `length * 1` hoặc `length * 2` chỉ là **ước lượng phần backing storage** trong một cài đặt cụ thể, **không phải kích thước object thực**. Ngoài vùng chứa byte còn có object header, tham chiếu, thông tin phụ, căn chỉnh, cơ chế GC/JIT và chi phí tạm thời.

Nếu tối ưu hóa bộ nhớ trên một JVM cụ thể, hãy đo thực tế bằng **profiler/JOL** hoặc công cụ phân tích heap phù hợp; không cố dự đoán byte chính xác từ `String.length()` hay thông qua truy cập trường private. Với HotSpot hỗ trợ cờ này, `-XX:-CompactStrings` có thể dùng trong thí nghiệm để tắt Compact Strings, nhưng đây là **tùy chọn cài đặt**, không phải yêu cầu của Java SE.

Ví dụ thử trên HotSpot tương thích:

~~~bash
java Utf16ApiDemo
java -XX:-CompactStrings Utf16ApiDemo
~~~

**Output API phải giống nhau**, dù cách lưu trữ nội bộ và mức sử dụng bộ nhớ có thể khác. Không cần và không nên thay đổi cờ JVM để sửa logic xử lý Unicode của ứng dụng.

### Phân biệt với `char[]` hoặc `StringBuilder`

Gọi `text.toCharArray()` trả về một **mảng `char[]` công khai** chứa các code unit UTF-16, tức mỗi phần tử mảng là một `char` 16 bit. Điều đó **không chứng minh** `String` ban đầu đã lưu dữ liệu trong `char[]`. `StringBuilder` cũng cung cấp API dựa trên chỉ số `char`, nhưng cách nó cấp phát và lưu dữ liệu bên trong là chuyện triển khai; đừng suy ra biểu diễn của `String` từ một API có cùng kiểu trả về.

## 9. Lỗi thường gặp và cách nhận diện

1. **“Java `String` luôn lưu hai byte mỗi ký tự.”** Sai vì câu này trộn API UTF-16 với cài đặt. Java 9+ trên các HotSpot/OpenJDK thông dụng có thể lưu Latin-1 với một byte/code unit. Và một code point ngoài BMP còn cần hai code unit.
2. **“`String.length()` là số ký tự mắt nhìn thấy.”** Sai với surrogate pair, dấu kết hợp, cụm ZWJ và nhiều trường hợp Unicode khác.
3. **“`charAt(i)` luôn trả về một Unicode code point hoàn chỉnh.”** Sai: `char` chỉ có 16 bit và có thể là một high/low surrogate.
4. **“`getBytes()` cho biết byte trong heap.”** Sai: đây là thao tác **encode** ra charset được chỉ định hoặc mặc định; byte đầu ra không phải ảnh chụp cách JVM cất `String`.
5. **“UTF-16BE và `StandardCharsets.UTF_16` luôn tạo byte giống nhau.”** Sai: `UTF_16` có thể tạo BOM như ví dụ `FE FF`; phải phân biệt charset cụ thể.
6. **“Có chữ Unicode ngoài ASCII thì chắc chắn cần hai byte nội bộ cho mỗi code unit.”** Không hẳn: `é` thuộc Latin-1; `Đ` thì không. Phải xét **tập code unit của toàn chuỗi** và cài đặt đang dùng.
7. **“Cắt chuỗi theo chỉ số bất kỳ luôn an toàn.”** `substring` có thể cắt đôi surrogate pair. Nếu cần đơn vị code point hoặc grapheme, phải chuyển đổi/chia đoạn có chủ đích.
8. **“Hai chuỗi hiển thị như nhau thì `equals()` trả về true.”** Sai nếu chúng dùng các chuỗi code unit khác nhau, chẳng hạn NFC so với dạng có dấu kết hợp.
9. **“Có thể dựa vào private fields của `String` cho thuật toán.”** Những trường này không thuộc hợp đồng Java SE và có thể bị JVM thay đổi hoặc cơ chế module hạn chế.
10. **“Tắt Compact Strings sẽ đổi `length()` hoặc `charAt()`.”** Sai đối với triển khai tuân thủ Java SE: tối ưu hóa biểu diễn nội bộ phải bảo toàn hành vi API.

## 10. Tự kiểm tra kiến thức

**Câu 1.** Với `String s = "A\uD83D\uDE00B";`, `s.length()`, `s.codePointCount(0, s.length())` và số byte UTF-8 lần lượt là bao nhiêu?

**Đáp án:** `4` code unit, `3` code point, `6` byte. Emoji U+1F600 chiếm hai code unit UTF-16 nhưng bốn byte UTF-8.

**Câu 2.** Vì sao `"café"` có thể dùng đường Latin-1 Compact Strings, còn `"Đ"` lại cần biểu diễn UTF-16 trong cài đặt HotSpot điển hình?

**Đáp án:** Tất cả code unit của `"café"` nằm ở U+0000–U+00FF, kể cả `é` U+00E9; `Đ` U+0110 nằm ngoài miền ấy. Đây là điều kiện lựa chọn **biểu diễn bộ nhớ**, không đổi semantics của API.

**Câu 3.** `text.substring(1, 2)` với `text = "A\uD83D\uDE00B"` chứa gì?

**Đáp án:** Chỉ high surrogate `U+D83D`, không phải emoji hoàn chỉnh. Muốn lấy emoji, dùng `substring(1, 3)`.

**Câu 4.** Nếu mã hóa `"A😀B"` bằng `UTF_16BE`, tại sao có 8 byte? Nếu bằng `UTF_8` tại sao có 6 byte?

**Đáp án:** Mô hình UTF-16 có 4 code unit × 2 byte = 8 byte ở dạng UTF-16BE. UTF-8 dùng 1 byte cho `A`, 4 byte cho `😀` và 1 byte cho `B` nên tổng là 6. Đây là số byte **encode để trao đổi**, không phải kích thước object trên heap.

**Câu 5.** Có thể kết luận một `String` dùng bao nhiêu byte trong JVM chỉ nhờ `length()` hoặc `getBytes(UTF_8).length` không?

**Đáp án:** Không. Hai giá trị có ý nghĩa API/charset; kích thước thực tế còn phụ thuộc cài đặt, lựa chọn Compact Strings, header và các cơ chế quản lý bộ nhớ.

## Ghi nhớ

**Mô hình UTF-16 của API là hợp đồng ổn định; biểu diễn nội bộ là chi tiết JVM có thể tối ưu.** Hãy đọc `length()` và các chỉ số `String` theo **UTF-16 code units**; dùng `codePoints()`, `codePointAt()` và `offsetByCodePoints()` khi cần code point; dùng giải pháp phân đoạn Unicode riêng khi cần **ký tự người dùng nhìn thấy**; dùng `getBytes(charset)` để đo byte **truyền/lưu**, không để đo byte nội bộ của `String`.

Tài liệu tra cứu: [Java 17 `String` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html), [JEP 254 — Compact Strings](https://openjdk.org/jeps/254) và [Java 17 `Character` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-internals-002">Compact Strings và các dạng LATIN1, UTF16 trong OpenJDK</a>

<details>
<summary>Xem chi tiết</summary>

# Compact Strings và hai dạng LATIN1, UTF16 trong OpenJDK

Từ Java 9, các phiên bản OpenJDK dùng cơ chế **Compact Strings** (JEP 254) để giảm bộ nhớ lưu trữ nội dung của nhiều đối tượng `String`. Một chuỗi chỉ chứa các đơn vị mã nằm trong phạm vi Latin-1 có thể lưu bằng **1 byte cho mỗi đơn vị mã**; chuỗi chứa đơn vị mã ngoài phạm vi đó dùng dạng **UTF16, 2 byte cho mỗi đơn vị mã**. Đây là **chi tiết triển khai nội bộ** của OpenJDK/HotSpot, không thay đổi ý nghĩa của API `String`.

Bài này giải thích lý do ra đời, cách OpenJDK chọn dạng `LATIN1` hoặc `UTF16`, tác động đến bộ nhớ và tốc độ, cách tự kiểm tra ví dụ Java 17+, cũng như những nhầm lẫn khi so sánh Compact Strings với UTF-8 hoặc `String.length()`.

## 1. Bối cảnh: từ `char[]` tới `byte[]`

Trước Java 9, các bản OpenJDK phổ biến biểu diễn nội dung `String` bằng một mảng `char[]`. Mỗi `char` là một **đơn vị mã UTF-16** dài 16 bit (2 byte), kể cả khi chuỗi chỉ chứa ASCII như `"Hello"`. Với trường hợp ASCII hoặc Latin-1 chiếm tỷ trọng cao, phần lớn dung lượng 16 bit mỗi đơn vị mã không cần đến.

JEP 254 (triển khai trong JDK 9) chuyển nội dung sang một mảng `byte[]` kèm **một trường nhận diện cách mã hóa nội bộ**, thường được gọi là `coder`:

~~~text
                    String trong OpenJDK từ Java 9
                  ┌───────────────────────────────┐
                  │ value: byte[]                 │
                  │ coder: LATIN1 hoặc UTF16      │
                  │ các metadata nội bộ khác      │
                  └───────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
              coder = LATIN1            coder = UTF16
             1 byte/code unit          2 byte/code unit
             (nội dung phù hợp)        (nội dung ngoài Latin-1)
~~~

Đây là **sơ đồ ý tưởng**. Mảng `byte[]` là đối tượng riêng trên heap, ngoài dữ liệu nội dung còn có chi phí header, căn chỉnh bộ nhớ và tham chiếu. Sơ đồ không thể dùng để tính chính xác kích thước toàn bộ đối tượng.

**Lưu ý về phiên bản:** đây là cơ chế triển khai của OpenJDK từ Java 9, không phải quy định bắt buộc của Java Language Specification. Một JVM khác hoặc một bản Java tương lai có thể triển khai khác mà vẫn tuân thủ cùng API `String`.

## 2. Quy tắc chọn `LATIN1` hay `UTF16`

Trong OpenJDK khi Compact Strings được bật:

- **`LATIN1`:** mọi đơn vị mã UTF-16 của chuỗi đều nằm trong khoảng `U+0000`–`U+00FF`. Mỗi đơn vị mã có thể biểu diễn bằng một byte. Đây là tập giá trị tương ứng ISO-8859-1/Latin-1, bao gồm ASCII và các ký tự như `é` (`U+00E9`), `ñ` (`U+00F1`).
- **`UTF16`:** có ít nhất một đơn vị mã nằm ngoài `U+00FF`; toàn bộ nội dung của chuỗi được lưu theo biểu diễn UTF-16 nội bộ, hai byte trên mỗi đơn vị mã. Ví dụ `€` (`U+20AC`), `ệ` (`U+1EC7`), chữ Hán, ký tự emoji.
- Với ký tự ngoài BMP như `😀` (`U+1F600`), một Unicode code point cần một **cặp surrogate** gồm hai đơn vị mã UTF-16; dạng nội bộ `UTF16` dùng tổng cộng bốn byte cho cặp đó.

Điều kiện thuộc về **toàn chuỗi**. `"ABCé"` vẫn đủ điều kiện `LATIN1`, trong khi `"ABC€"` buộc sử dụng `UTF16` cho cả chuỗi, không phải ba ký tự đầu dùng Latin-1 và ký tự cuối dùng UTF-16 trong cùng một `String`.

### Bảng so sánh dễ nhớ

| Nội dung | `String.length()` | Có thể dùng `LATIN1` khi bật Compact Strings? | Số byte của riêng mảng nội dung theo dạng tương ứng |
| --- | ---: | --- | ---: |
| `"Hello"` | 5 | Có | 5 |
| `"café"` | 4 | Có | 4 |
| `"Euro €"` | 6 | Không, vì `€` lớn hơn `U+00FF` | 12 |
| `"Việt"` | 4 | Không, vì `ệ` là `U+1EC7` | 8 |
| `"A😀B"` | 4 | Không, vì emoji là cặp surrogate | 8 |

Cột cuối **chỉ biểu thị số byte nội dung lý thuyết trong mảng `value`**, giả sử chuỗi được tạo theo dạng nêu trong bảng. Nó không bao gồm kích thước đối tượng `String`, mảng `byte[]`, header, alignment, khả năng dùng chung dữ liệu hay kỹ thuật tối ưu khác của JVM.

**Hai nhầm lẫn quan trọng:**

1. `LATIN1` không đồng nghĩa chỉ có ASCII: `é` vẫn đủ điều kiện, dù UTF-8 của `é` dùng 2 byte.
2. `LATIN1` là phạm vi Unicode `U+0000`–`U+00FF`, **không phải Windows-1252**. Dấu nháy cong `’` là `U+2019`; dù Windows-1252 mã hóa được ký tự đó bằng một byte, OpenJDK Compact Strings vẫn không thể lưu nó trong dạng `LATIN1`.

## 3. `byte[] value` và `coder` hoạt động ra sao?

Trong mã nguồn `java.lang.String` của các phiên bản OpenJDK có Compact Strings, bạn có thể thấy các thành phần nội bộ tương đương:

~~~java
private final byte[] value;
private final byte coder;
static final boolean COMPACT_STRINGS;
~~~

Đây là **trích đoạn minh họa khai báo trường nội bộ**, không phải đoạn chương trình Java có thể biên dịch độc lập và cũng không phải public API.

- `value` chứa byte biểu diễn nội dung chuỗi.
- `coder` cho biết phải diễn giải `value` theo dạng `LATIN1` hay `UTF16`. Trong các bản OpenJDK được đề cập, giá trị nội bộ điển hình là `LATIN1 = 0` và `UTF16 = 1`; lập trình ứng dụng không nên phụ thuộc những hằng số nội bộ này.
- `COMPACT_STRINGS` là cờ nội bộ mà JIT có thể dùng để tối ưu nhánh thực thi. Khi chế độ nén bị tắt ở mức VM, đường thực thi UTF16 được sử dụng.

OpenJDK có các lớp trợ giúp nội bộ như `StringLatin1` và `StringUTF16` để cài đặt những thao tác trên từng dạng lưu trữ. Nhiều phép toán của `String` có đường xử lý riêng hoặc đường tối ưu tương ứng với `coder`. Những lớp này thuộc `java.lang` nhưng **không phải API công khai để ứng dụng phụ thuộc**.

Bạn có thể quan sát tên trường của JDK đang sử dụng bằng lệnh:

~~~bash
javap -private java.lang.String
~~~

Trong các phiên bản OpenJDK có cách tổ chức này, phần khai báo có thể thấy `byte[] value`, `byte coder` và `COMPACT_STRINGS`. Lệnh `javap` là công cụ xem cấu trúc class; nó **không xác định `coder` của một đối tượng `String` cụ thể**.

Không nên viết logic nghiệp vụ bằng reflection vào `String.value`/`String.coder`: các trường `private` có thể thay đổi theo JDK, Java Module System giới hạn quyền truy cập sâu, và thao tác sửa nội dung bên trong còn phá vỡ bất biến `String`.

## 4. Thí nghiệm chạy được trên Java 17+: tính điều kiện dùng Latin-1

Public API không cho biết trực tiếp `coder`, nhưng ta có thể tự kiểm tra **nội dung có đủ điều kiện** lưu bằng Latin-1 hay không. Điều này khác với việc đo trạng thái thực tế của bộ nhớ JVM.

Tạo tệp `CompactStringsDemo.java`:

~~~java
import java.nio.charset.StandardCharsets;

public class CompactStringsDemo {
    public static void main(String[] args) {
        inspect("ASCII", "Cafe");
        inspect("Latin1", "caf\u00E9");
        inspect("Vietnamese", "Vi\u1EC7t");
        inspect("Euro", "Euro \u20AC");
        inspect("Emoji", "A\uD83D\uDE00B");
    }

    private static void inspect(String label, String text) {
        int utf16Units = text.length();
        int codePoints = text.codePointCount(0, text.length());
        int utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;

        // chars() duyệt từng đơn vị mã UTF-16, không phải code point.
        boolean latin1Eligible = text.chars()
                .allMatch(codeUnit -> codeUnit <= 0xFF);

        System.out.printf(
                "%s: units=%d, codePoints=%d, utf8Bytes=%d, latin1Eligible=%s%n",
                label, utf16Units, codePoints, utf8Bytes, latin1Eligible
        );
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CompactStringsDemo.java
java CompactStringsDemo
~~~

Đầu ra:

~~~text
ASCII: units=4, codePoints=4, utf8Bytes=4, latin1Eligible=true
Latin1: units=4, codePoints=4, utf8Bytes=5, latin1Eligible=true
Vietnamese: units=4, codePoints=4, utf8Bytes=6, latin1Eligible=false
Euro: units=6, codePoints=6, utf8Bytes=8, latin1Eligible=false
Emoji: units=4, codePoints=3, utf8Bytes=6, latin1Eligible=false
~~~

Giải thích:

- `"Cafe"` gồm 4 ký tự ASCII: đủ điều kiện Latin-1 và cũng cần đúng 4 byte UTF-8.
- `"café"` gồm 4 đơn vị mã UTF-16, tất cả không vượt `U+00FF`, nên đủ điều kiện Latin-1; tuy nhiên UTF-8 của `é` chiếm **2 byte**, vì vậy toàn chuỗi có 5 byte UTF-8.
- `"Việt"` có `ệ` (`U+1EC7`), nằm ngoài Latin-1. `length()` là 4 nhưng khi mã hóa UTF-8 cần 6 byte.
- `"Euro €"` chứa ký hiệu euro `U+20AC`, nên không đủ điều kiện Latin-1 dù các ký tự đầu là ASCII.
- `"A😀B"` chứa 3 code point nhưng 4 đơn vị mã UTF-16: emoji chiếm 2 đơn vị mã và 4 byte UTF-8.

`text.chars()` trả về một `IntStream` của **đơn vị mã UTF-16**, nên ở đây kiểm tra `<= 0xFF` là phù hợp để diễn tả điều kiện nén Latin-1. Nếu dùng `codePoints()`, bạn đang duyệt theo code point; trong trường hợp này vẫn có thể suy luận điều kiện với chuỗi hợp lệ, nhưng cần hiểu sự khác nhau khi làm việc với surrogate đơn lẻ.

Biến `latin1Eligible` chỉ trả lời “nội dung có vừa Latin-1 không?”. Nếu chạy với Compact Strings **đã tắt**, biểu thức vẫn là `true` cho `"café"`, trong khi VM sẽ sử dụng dạng nội bộ `UTF16`. Không gọi đây là cách kiểm tra chính xác `String.coder`.

## 5. Thử tùy chọn JVM `-XX:-CompactStrings`

Với HotSpot/OpenJDK hỗ trợ cờ này, Compact Strings được bật mặc định từ Java 9. Có thể chạy chương trình trên ở hai chế độ:

~~~bash
java CompactStringsDemo
java -XX:-CompactStrings CompactStringsDemo
~~~

Ở chế độ thứ hai, HotSpot tắt cơ chế nén và sử dụng dạng nội bộ `UTF16` cho chuỗi. **Hai lần chạy vẫn phải in ra cùng nội dung** như ví dụ trên: cả `String.length()`, `codePointCount()`, byte UTF-8 và `latin1Eligible` đều xuất phát từ ký tự/Unicode, không phải trạng thái `coder` nội bộ.

Điều này cho thấy Compact Strings được thiết kế để **bảo toàn hành vi của public API**. Bạn có thể dùng cờ tắt để nghiên cứu hiệu ứng bộ nhớ/hiệu năng, nhưng không nên tắt nó mặc định nếu không có benchmark hoặc bằng chứng hồi quy trên workload thực tế.

Lưu ý cú pháp cờ VM: `-XX:-CompactStrings` phải đứng **trước tên class** khi gọi `java`, vì nó là tùy chọn của JVM, không phải đối số truyền vào `main(String[] args)`.

## 6. Phân biệt ba khái niệm: nội bộ, UTF-16 và UTF-8

Đây là điểm rất dễ gây hiểu nhầm khi học về Compact Strings.

| Câu hỏi | Cơ chế/API thích hợp |
| --- | --- |
| Có bao nhiêu đơn vị mã trong `String`? | `text.length()`: luôn đếm UTF-16 code units theo hợp đồng API |
| Có bao nhiêu Unicode code point? | `text.codePointCount(0, text.length())` |
| Có bao nhiêu byte UTF-8 khi xuất chuỗi? | `text.getBytes(StandardCharsets.UTF_8).length` |
| OpenJDK có thể lưu phần nội dung của chuỗi này ở dạng Latin-1? | Mọi đơn vị mã UTF-16 đều `<= U+00FF`, với Compact Strings được bật |
| Kích thước heap thật sự là bao nhiêu? | Đo trên VM cụ thể bằng heap profiler/công cụ layout, bao gồm overhead và tương tác với GC |

**Nội bộ `LATIN1` không đồng nghĩa dây truyền dùng ISO-8859-1.** Chẳng hạn một `String` Java chứa `"café"` có thể chiếm 4 byte ở mảng `value` nội bộ, nhưng:

~~~java
String text = "caf\u00E9";
int utf8Size = text.getBytes(
        java.nio.charset.StandardCharsets.UTF_8
).length; // 5
~~~

Đoạn mã trên minh họa một biểu thức Java trong phương thức; đầu ra ở giao thức UTF-8 vẫn là **5 byte**. Ngược lại, gọi `getBytes(StandardCharsets.ISO_8859_1)` là **yêu cầu mã hóa dữ liệu bên ngoài** sang ISO-8859-1, không phải hành động “lấy mảng `value` nội bộ” của `String`.

**Nội bộ `UTF16` không đồng nghĩa dữ liệu bên ngoài buộc phải dùng UTF-16.** `"A😀B"` có thể ở dạng `UTF16` trong heap, nhưng khi truyền HTTP body/ghi file qua UTF-8, kết quả vẫn là mã UTF-8 theo charset chỉ định. Không bao giờ suy ra kích thước UTF-8 trực tiếp từ `coder` hoặc `length()`.

## 7. Tác động đến bộ nhớ thực tế

### Tiết kiệm ở đâu?

Khi một chuỗi có `n` đơn vị mã UTF-16 và phù hợp Latin-1:

- Dạng `LATIN1` cần xấp xỉ `n` byte cho **phần dữ liệu ký tự**.
- Dạng `UTF16` cần xấp xỉ `2n` byte cho **phần dữ liệu ký tự**.
- Về riêng phần nội dung, dữ liệu dạng Latin-1 dùng khoảng **một nửa** dung lượng so với biểu diễn UTF-16 hai byte trên mỗi đơn vị mã.

Ví dụ một chuỗi 1.000 ký tự ASCII có thể dùng khoảng 1.000 byte cho nội dung thay vì khoảng 2.000 byte. Đây **không phải** tuyên bố rằng toàn đối tượng `String` hay tổng heap của ứng dụng sẽ giảm 50%. Tổng bộ nhớ còn phụ thuộc header, alignment, mảng, trường `coder`, tham chiếu, String pool, GC, deduplication và các thành phần khác.

### Khi nào lợi ích giảm?

Một chuỗi có một ký tự ngoài Latin-1, như `"report-2026-€"`, cần dùng dạng `UTF16` cho toàn bộ chuỗi trong mô hình Compact Strings này. Tỷ lệ các chuỗi như vậy, độ dài chuỗi, vòng đời đối tượng và lượng string được tái sử dụng sẽ ảnh hưởng lợi ích tổng thể.

Đối với ứng dụng đa ngôn ngữ, **không kết luận “tiếng Việt luôn dùng 2 byte mỗi ký tự”**. Một số ký tự tiếng Việt như `ê` (`U+00EA`) thuộc Latin-1, nhưng nhiều ký tự như `ệ` (`U+1EC7`) nằm ngoài. Coder được chọn trên cơ sở toàn chuỗi, và emoji còn có surrogate pair. Vì vậy cần phân tích dữ liệu thực tế.

### Tốc độ xử lý có luôn tăng không?

Không có bảo đảm mọi thao tác luôn nhanh hơn. Compact Strings có thể giảm băng thông bộ nhớ, giảm áp lực cache/GC khi phần lớn chuỗi phù hợp Latin-1. Đồng thời có thêm những đường xử lý/phân nhánh hoặc chuyển đổi giữa biểu diễn; HotSpot dùng nội tại (intrinsics) và tối ưu JIT để giảm overhead cho nhiều thao tác. Kết quả phụ thuộc thao tác và workload.

Để đánh giá:

1. Giữ nguyên ứng dụng/dữ liệu thử nghiệm; so sánh mặc định với `-XX:-CompactStrings` trên **cùng phiên bản JDK và cấu hình máy**.
2. Đo lượng cấp phát, live heap, GC, throughput và latency đại diện cho thực tế.
3. Warm-up, dùng công cụ benchmark đúng mục đích (ví dụ JMH cho microbenchmark) và tránh đo duy nhất thời gian của một vòng lặp thiếu kiểm soát.
4. Không suy ra lợi ích hiệu năng chỉ từ công thức `n` và `2n`, hoặc từ một ví dụ vài chuỗi ngắn.

## 8. Vì sao `String.length()` không đổi?

Theo API Java, `length()` luôn trả về số **đơn vị mã UTF-16** trong chuỗi. Nội bộ OpenJDK có thể lưu Latin-1 một byte trên mỗi đơn vị mã, nhưng Java vẫn phải trả về `length()` như trước JDK 9.

Ví dụ:

~~~java
String latin1 = "caf\u00E9";
String emoji = "A\uD83D\uDE00B";

System.out.println(latin1.length()); // 4
System.out.println(emoji.length());  // 4
System.out.println(emoji.codePointCount(0, emoji.length())); // 3
~~~

Đây là đoạn mã minh họa trong một phương thức, không phải class độc lập; các giá trị là `4`, `4` và `3`. Chế độ Compact Strings không đổi kết quả đó.

Với ký tự ngoài BMP, Java vẫn biểu diễn một code point bằng một **cặp surrogate ở lớp API UTF-16**. Dù cơ chế lưu trữ bên dưới thay đổi, các phương thức `charAt`, `substring`, `codePointAt`, `codePointCount` phải giữ đúng hợp đồng API. Lưu ý `substring` và chỉ số `charAt` dùng vị trí của **đơn vị mã UTF-16**; có thể cắt giữa surrogate pair nếu lập trình viên chọn chỉ số không phù hợp.

## 9. Hoạt động khi tạo và biến đổi chuỗi

Những thao tác như tạo chuỗi từ literal, ghép chuỗi, chuyển từ `StringBuilder`, đổi chữ hoa/thường hay trích chuỗi con có thể dẫn đến **một đối tượng `String` khác**. Biểu diễn nội bộ của đối tượng kết quả có thể được chọn theo **nội dung của kết quả**, độc lập với dạng của đối tượng đầu vào.

Ví dụ ở góc độ nội dung:

~~~java
String original = "ABC\u20AC";
String onlyAscii = original.substring(0, 3);

System.out.println(original); // ABC€
System.out.println(onlyAscii); // ABC
~~~

Đây là đoạn minh họa có thể đặt trong phương thức. Khi Compact Strings bật, một `String` mới chỉ chứa `"ABC"` đủ điều kiện Latin-1 dù `original` có ký tự `€`. Tuy nhiên, **không nên dựa vào chi tiết đối tượng có dùng chung/copy mảng nào hoặc đúng thời điểm chọn coder**: đó là quyết định của phiên bản triển khai. Mục tiêu quan trọng là các API trả về văn bản đúng.

`StringBuilder` và `StringBuffer` trong các bản OpenJDK hỗ trợ JEP 254 cũng có những tối ưu biểu diễn nội dung liên quan, nhưng chiến lược quản lý dung lượng/capacity của builder không đồng nhất với `String` bất biến. Tránh suy ra rằng phép `append` hay `toString()` luôn sao chép, luôn dùng chung mảng hoặc luôn có chi phí cố định.

## 10. Những nhầm lẫn thường gặp

**Nhầm 1 — “Java từ Java 9 đã đổi String sang UTF-8”.** Sai. Compact Strings sử dụng **LATIN1 hoặc UTF16** để lưu trữ nội bộ, không dùng UTF-8 làm hai dạng coder chính.

**Nhầm 2 — “Vì mảng là `byte[]` nên `String.length()` đếm byte”.** Sai. `length()` vẫn đếm UTF-16 code units, bất kể Java sử dụng dạng lưu trữ nội bộ nào.

**Nhầm 3 — “Mọi ký tự không phải ASCII đều dùng UTF16”.** Sai. `é`, `ñ` hoặc `ÿ` đều nằm trong phạm vi Latin-1. Trong khi đó, `€` và phần lớn các dấu Unicode ngoài `U+00FF` buộc dùng UTF16.

**Nhầm 4 — “Chỉ ký tự vượt Latin-1 mới chiếm 2 byte”.** Sai ở cấp **một đối tượng `String`**: khi một đơn vị mã không vừa Latin-1, cả chuỗi chuyển sang dạng UTF16.

**Nhầm 5 — “Có thể đọc `coder` để tối ưu logic ứng dụng”.** Trường này là nội bộ và có thể thay đổi. Không dùng reflection/`Unsafe` để viết logic phụ thuộc `coder`; xử lý chuỗi bằng public API.

**Nhầm 6 — “Tắt Compact Strings thì chương trình trả ra văn bản khác”.** Sai đối với hành vi hợp lệ của public API. Cờ tác động biểu diễn và có thể ảnh hưởng bộ nhớ/hiệu năng, không thay đổi nội dung hay cách đếm `length()`.

**Nhầm 7 — “String đủ điều kiện Latin-1 thì dữ liệu UTF-8 cũng chỉ có một byte/ký tự”.** Sai: `"café"` có 4 đơn vị mã và đủ điều kiện Latin-1, nhưng cần 5 byte khi mã hóa UTF-8.

**Nhầm 8 — “Tiết kiệm 50% dữ liệu ký tự tức tiết kiệm 50% toàn bộ heap”.** Sai. Tổng heap gồm nhiều đối tượng và overhead; cần đo thực tế.

## 11. Kiểm tra kiến thức

**Câu 1.** `"ABC\u00E9"` có dùng dạng `LATIN1` được không? Tại sao?

**Đáp án:** Có, vì cả bốn đơn vị mã UTF-16 đều nằm trong `U+0000`–`U+00FF`. Trong môi trường bật Compact Strings, nội dung đủ điều kiện nén còn 4 byte.

**Câu 2.** `"ABC\u20AC"` có thể lưu 3 ký tự đầu ở LATIN1 và ký tự cuối ở UTF16 trong cùng đối tượng `String` không?

**Đáp án:** Không theo mô hình JEP 254 của OpenJDK: `coder` chọn một dạng cho nội dung của toàn đối tượng. Có ký tự `€` ngoài Latin-1 nên cần dạng UTF16.

**Câu 3.** Chạy cùng chương trình Java với `-XX:-CompactStrings`. Giá trị `"A😀B".length()` có thay đổi không?

**Đáp án:** Không. Giá trị vẫn là `4`, gồm `A` (1), emoji (2 đơn vị mã UTF-16) và `B` (1).

**Câu 4.** Một dịch vụ gửi UTF-8 qua mạng. Có nên lấy số byte mảng nội bộ `String.value` làm `Content-Length`?

**Đáp án:** Không. Phải dùng số byte của **payload cuối cùng** được gửi. Compact Strings là chi tiết lưu trữ nội bộ; khi xuất UTF-8, chuỗi được mã hóa sang UTF-8 độc lập với coder bên trong.

**Câu 5.** Tại sao nên đánh giá bằng heap profiling thay vì dùng `text.length() * 1` hay `* 2` để báo lượng RAM mà `String` chiếm?

**Đáp án:** Các công thức đó chỉ mô tả gần đúng **phần dữ liệu** trong mảng lưu trữ, bỏ qua object header, array header, alignment, tham chiếu, khả năng tái sử dụng và nhiều tối ưu khác. Kích thước thực còn phụ thuộc cấu hình JVM.

## 12. Kết luận và cách áp dụng

Compact Strings là tối ưu lưu trữ của OpenJDK từ Java 9: `String` sử dụng `byte[] value` cùng `coder` để chọn **LATIN1 khi mọi đơn vị mã nằm trong `U+0000`–`U+00FF`**, hoặc **UTF16 khi có đơn vị mã ngoài phạm vi đó**. Trên nhiều workload chứa lượng lớn chuỗi Latin-1, phần dữ liệu ký tự có thể giảm gần một nửa so với cách lưu UTF-16 cố định. Nhưng đây là tối ưu phía dưới API; trong ứng dụng, hãy tiếp tục dùng `String.length()`, các API code point và mã hóa byte bằng `StandardCharsets.UTF_8` theo đúng ngữ nghĩa từng nhu cầu.

Khi tối ưu heap, nên đo dữ liệu/ứng dụng thực tế và chỉ cân nhắc `-XX:-CompactStrings` nếu phép đo cho thấy lý do cụ thể; khi triển khai định dạng file, HTTP hay cơ sở dữ liệu, hãy căn cứ **charset và định dạng đầu ra**, không căn cứ coder nội bộ.

Tài liệu tham khảo:

- [OpenJDK JEP 254 — Compact Strings](https://openjdk.org/jeps/254).
- [Oracle JDK 21 — Java HotSpot Virtual Machine Performance Enhancements](https://docs.oracle.com/en/java/javase/21/vm/java-hotspot-virtual-machine-performance-enhancements.html).
- [OpenJDK: mã nguồn `java.lang.String`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/String.java) và [`StringUTF16`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/StringUTF16.java).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-internals-003">Chi phí đối tượng, mảng lưu dữ liệu và chuỗi tạm</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí đối tượng, mảng lưu dữ liệu và chuỗi tạm trong Java String

## 1. Mục tiêu bài học

Khi nhìn thấy một biến `String`, ta thường nghĩ đến dãy ký tự mà nó hiển thị. Tuy nhiên, ở thời điểm chạy, một chuỗi còn có **đối tượng Java**, **vùng lưu nội dung**, metadata và những chi phí phát sinh trong quá trình nối, cắt, định dạng hoặc chuyển đổi. Nhiều chương trình chỉ gặp vấn đề khi các chuỗi được tạo với số lượng rất lớn hoặc khi thao tác trong vòng lặp nóng.

Bài này tập trung vào ba câu hỏi thực tế:

1. Một `String` chiếm bộ nhớ vì **đối tượng** và **dữ liệu ký tự** như thế nào?
2. Vì sao nhiều phiên bản JDK hiện đại có thể chứa cùng số ký tự mà tốn bộ nhớ khác nhau, và đâu là ranh giới giữa **API bảo đảm** với **chi tiết triển khai**?
3. Những phép toán nào tạo **chuỗi tạm**, khi nào chúng ảnh hưởng đến GC, và làm sao đo lường rồi tối ưu một cách có kiểm chứng?

Phạm vi minh họa là **Java 17+**, với những ghi chú rõ ràng khi nói về cách HotSpot/OpenJDK lưu trữ `String`. Không nên dùng một con số byte cố định làm “kích thước của mọi String” vì cấu hình JVM, kiến trúc, cách căn chỉnh và phiên bản JDK đều có thể thay đổi kết quả.

## 2. Phân biệt biến tham chiếu, đối tượng String và dữ liệu bên trong

Xét đoạn mã:

```java
String name = "Java";
```

Có ba khái niệm khác nhau:

- **Biến `name`**: chứa một tham chiếu đến đối tượng, không chứa trực tiếp toàn bộ bốn ký tự như một mảng ký tự nằm ngay trong biến.
- **Đối tượng `String`**: một đối tượng trên heap theo mô hình Java thông thường, có phần header và các trường nội bộ cần cho việc quản lý chuỗi.
- **Nội dung chuỗi**: dữ liệu mã hóa các ký tự/code unit, có thể được giữ trong mảng nội bộ hoặc cấu trúc khác tùy bản triển khai.

Trong OpenJDK/HotSpot hiện đại, dữ liệu nội dung của `String` thường nằm trong một mảng `byte[]` riêng. Từ đó, tổng chi phí có thể gồm cả đối tượng `String` lẫn đối tượng mảng lưu dữ liệu; song **tài liệu API `String` không cam kết layout field hay số byte cụ thể**.

Một số khác biệt cần giữ riêng:
- `name.length()` trả **số UTF-16 code unit**, không trả byte bộ nhớ.
- Số Unicode code point có thể khác `length()`, ví dụ emoji nằm ngoài BMP dùng hai UTF-16 code unit.
- `name.getBytes(StandardCharsets.UTF_8).length` trả số byte **của biểu diễn UTF-8 mới tạo**, không phải kích thước bộ nhớ của `String`.
- Các phép đo bộ nhớ heap còn tính overhead đối tượng, căn chỉnh và đôi khi những vùng/đối tượng liên quan có thể được chia sẻ.

Nếu chỉ gọi `length()`, bạn không thể suy ra một cách đáng tin rằng giá trị đó nhân với 2 chính là lượng byte cấp phát cho chuỗi.

## 3. Từ cấu trúc lưu trữ đến footprint của đối tượng

Trong nhiều OpenJDK trước Java 9, `String` giữ nội dung bằng mảng `char[]`. Với **Compact Strings** (JEP 254) từ Java 9, các bản OpenJDK/HotSpot thông dụng có thể lưu `byte[]` cùng `coder`: phần dữ liệu cần khoảng `n` byte khi toàn bộ code unit thuộc Latin-1, hoặc khoảng `2n` byte khi cần dạng UTF16, với `n = text.length()`.

Điểm quan trọng khi phân tích heap là **mảng dữ liệu và đối tượng `String` có overhead riêng**. Trên một JVM cụ thể, footprint còn phụ thuộc object/array headers, references, alignment, chia sẻ bộ đệm và liệu nhiều đối tượng đang giữ cùng dữ liệu. Do đó, con số `n` hay `2n` chỉ mô tả *phần nội dung* theo một chiến lược cài đặt; **không phải total heap cost**.

```text
String reference → String object → backing data (ví dụ byte[])
                       |                     |
                  header/fields         array header + data
```

Để học chính xác điều kiện chọn LATIN1/UTF16 và phân biệt với UTF-8 ngoài heap, xem bài **Compact Strings**. Bài này dùng cấu trúc đó làm nền cho `shallow size`, `retained size`, tốc độ cấp phát và áp lực GC, là những thước đo tác động trực tiếp đến vận hành.

## 4. Shallow size, retained size và heap dump

Ba khái niệm thường được dùng khi phân tích bộ nhớ Java:

| Khái niệm | Ý nghĩa | Với String |
| --- | --- | --- |
| Shallow size | Kích thước bộ nhớ của **chính đối tượng** | Thường chỉ tính đối tượng `String` và field/header của nó, chưa tính mảng được tham chiếu |
| Retained size | Phần bộ nhớ có thể được giải phóng nếu đối tượng không còn reachable, theo quan hệ giữ tham chiếu | Có thể gồm mảng dữ liệu do `String` giữ riêng, nhưng không nên cộng nhầm đối tượng đang được chia sẻ |
| Allocation rate | Tốc độ JVM tạo đối tượng/byte mới theo thời gian | Cho thấy tác động của các chuỗi trung gian dù chúng sống rất ngắn |

Ví dụ, một `String` mới chỉ được dùng để tạo log rồi bị bỏ ngay có thể không làm **live heap** tăng lâu dài, nhưng vẫn làm **allocation rate** tăng. Ngược lại, một cache giữ hàng triệu chuỗi nhỏ lâu dài có thể tăng retained heap, kéo dài thời gian GC hoặc làm thiếu bộ nhớ.

Các phép đo kích thước cũng phụ thuộc:
- Kiểu JVM, bitness, nén ordinary object pointers (Compressed Oops) và compressed class pointers.
- Alignment của đối tượng và header.
- Hình thức lưu trữ nội bộ ở phiên bản JDK cụ thể.
- Chuỗi có cùng tham chiếu, chuỗi khác đối tượng cùng nội dung, hay có phần dữ liệu được chia sẻ.
- GC và những tối ưu của runtime.

**Không cộng shallow size của một `String` với toàn bộ mảng chung cho từng đường tham chiếu**, nếu nhiều đối tượng chia sẻ cùng một phần dữ liệu: cách này dẫn tới đếm trùng.

## 5. Chuỗi bất biến: vì sao thao tác có thể tạo đối tượng mới?

`String` là immutable: sau khi tạo, nội dung của đối tượng không bị thay đổi bởi các phương thức `String` thông thường. Vì thế những thao tác **thay nội dung** thường trả một đối tượng `String` mới, ví dụ khi nội dung thực sự cần chỉnh.

```java
String value = "Java";
String result = value.concat(" 17");

System.out.println(value);  // Java
System.out.println(result); // Java 17
```

Các phương thức như `replace`, `toUpperCase`, `substring`, `strip` hoặc `repeat` có thể tạo biểu diễn chuỗi mới khi kết quả khác nguồn. Tuy nhiên **không được khẳng định mọi lời gọi luôn cấp phát đối tượng mới**: một số trường hợp không cần thay đổi có thể trả về chính chuỗi cũ, và JIT có thể loại bỏ cấp phát trung gian nếu chứng minh được.

Ví dụ:

```java
String source = "abcdef";
String cut = source.substring(1, 4); // "bcd"
```

Trong OpenJDK hiện đại, `substring` thường cần biểu diễn nội dung mới cho phần được cắt. Lịch sử JDK đã từng có cơ chế chia sẻ mảng nền giữa substring và chuỗi gốc; không áp dụng nguyên mẫu kinh nghiệm đó cho Java 17. Với ứng dụng, điều nên dựa vào là **hợp đồng API về nội dung trả về**, không dựa vào field nội bộ hoặc kỳ vọng danh tính đối tượng.

Ngay cả khi một thao tác tạo chuỗi tạm, GC sẽ thu hồi nó khi không còn reachable; vấn đề hiệu năng phụ thuộc **tần suất tạo**, kích thước, thời gian sống và khả năng tối ưu của JVM.

## 6. Pool, bản sao chuỗi và số lượng đối tượng

Hai literal cùng nội dung có cùng tham chiếu canonical, còn `new String(existing)` tạo một đối tượng `String` riêng, dù dữ liệu ký tự có thể được chia sẻ theo cài đặt:

```java
String canonical = "Java";
String sameLiteral = "Java";
String fresh = new String("Java");

System.out.println(canonical == sameLiteral); // true
System.out.println(canonical == fresh);       // false
System.out.println(canonical.equals(fresh));  // true
```

Về **footprint**, cần tách số **đối tượng `String`** khỏi số **mảng lưu nội dung**. `intern()` trả về đối tượng canonical; tối ưu *String deduplication* của một số GC có thể chia sẻ dữ liệu giữa những đối tượng vẫn khác tham chiếu. Hai cơ chế có chi phí, điều kiện áp dụng và ảnh hưởng đến vòng đời dữ liệu riêng; không nên intern tất cả ID ngẫu nhiên hay giả định dedup luôn bật.

Bài **Constant pool và Constable/ConstantDesc** giải thích hợp đồng interning; bài về **khác biệt giữa các JDK** trình bày lịch sử intern và dedup. Khi mục tiêu là tiết kiệm heap, hãy đo retained memory, số lượng bản sao thực và allocation rate thay vì đếm `new String` bằng mắt.

## 7. Những nguồn tạo chuỗi tạm phổ biến

### 7.1. Nối chuỗi bằng toán tử +

```java
String id = "42";
String message = "order-" + id + "-ready";
```

Về mặt ngôn ngữ, phép `+` tạo kết quả ghép chuỗi. Tuy nhiên **đừng giả định** ví dụ này tạo chính xác ba đối tượng `String` trung gian. Từ Java 9, trình biên dịch/runtime có thể sử dụng cơ chế nối dựa trên `invokedynamic` (JEP 280); JIT và constant folding còn có thể làm giảm cấp phát.

Một biểu thức chỉ chứa hằng số:

```java
String label = "Java" + " " + "17";
```

có thể được **gộp thành hằng số lúc biên dịch**. Vì vậy, đo benchmark cho chuỗi literal hằng không đại diện cho vòng lặp nối dữ liệu động.

### 7.2. Nối lặp từng bước

Cần cẩn trọng với cách viết sau, khi `part` biến đổi theo lượt lặp:

```java
String output = "";
for (String part : parts) {
    output += part;
}
```

Mỗi lượt về mặt ngữ nghĩa phải tạo kết quả ghép mới vì `String` immutable. Runtime có thể tối ưu một phần, nhưng kiểu tích lũy này thường tăng công việc sao chép nội dung lặp đi lặp lại, nhất là với chuỗi kết quả tăng dần.

Giải pháp có mục đích rõ hơn:

```java
StringBuilder builder = new StringBuilder();
for (String part : parts) {
    builder.append(part);
}
String output = builder.toString();
```

`StringBuilder` lưu trạng thái có thể thay đổi trong một bộ đệm và phát triển vùng chứa khi cần; việc `append` không yêu cầu tạo một `String` bất biến trung gian cho mỗi lượt. Cần nhớ bản thân `StringBuilder` và buffer của nó **cũng là đối tượng có chi phí**, và `toString()` tạo kết quả bất biến.

### 7.3. Chuyển kiểu và định dạng

Các thao tác như:

```java
String a = String.valueOf(12345);
String b = String.format(java.util.Locale.US, "%.2f", 12.5);
String c = "prefix" + java.util.UUID.randomUUID();
```

có thể dẫn tới cấp phát chuỗi, đối tượng hỗ trợ hoặc buffer nội bộ. `String.format` thường có xử lý định dạng phong phú hơn, phù hợp khi cần width/precision/locale; không nên thay bằng nối thô nếu làm mất yêu cầu định dạng chỉ vì muốn giảm cấp phát.

Với số lượng lời gọi lớn trong đường đi nóng, hãy đo sự khác biệt giữa `StringBuilder`, nối chuỗi `+` và formatter **trên dữ liệu và môi trường thực**, không suy luận thuần túy từ cú pháp.

### 7.4. split, replace, substring, strip và stream

Các pipeline có thể tạo thêm đối tượng hoặc mảng trung gian:

```java
String input = " red , blue , green ";
String[] tokens = input.split(",");
String first = tokens[0].strip();
String upper = first.toUpperCase(java.util.Locale.ROOT);
```

Có thể có mảng chứa phần tử, các `String` token, `String` sau chuẩn hóa và một số cấu trúc phục vụ thuật toán. Số lượng thực tế phụ thuộc đầu vào, phiên bản JDK và tối ưu. Không giả định `split` chỉ tạo một mảng mà không có chuỗi thành phần.

Nếu chỉ cần tìm một delimiter đơn và xử lý một phần nhỏ của văn bản, `indexOf`, `substring` hoặc một parser dựa trên chỉ số có thể tránh một số đối tượng. Nhưng parser tự viết cần kiểm thử các trường hợp biên và có thể làm mã khó duy trì. Chỉ tối ưu khi profiler chứng minh vị trí này đáng kể.

## 8. StringBuilder: quản lý buffer và capacity

`StringBuilder` là lớp mutable để xây dựng chuỗi theo từng phần. Hai khái niệm khác nhau:

- `length()`: số UTF-16 code unit **đang sử dụng**.
- `capacity()`: số UTF-16 code unit bộ đệm **có thể chứa trước khi cần mở rộng** theo đặc tả API.

```java
StringBuilder builder = new StringBuilder(128);
builder.append("id=").append(42);

System.out.println(builder.length()); // 5
System.out.println(builder.capacity() >= 128); // true
```

Khởi tạo capacity đủ lớn có thể giảm số lần mở rộng buffer cho một kết quả dự đoán được kích thước. Nhưng `capacity()` **không phải số byte heap**. Trong OpenJDK Java 17, vùng chứa của `StringBuilder` cũng có chi tiết triển khai tối ưu ký tự, nên lấy capacity nhân với 2 để suy ra byte sử dụng là cách tính không đáng tin.

Lạm dụng preallocation cũng có hại:

```java
StringBuilder huge = new StringBuilder(1_000_000);
huge.append("OK");
```

Đoạn trên yêu cầu bộ đệm ban đầu rất lớn dù dữ liệu chỉ có hai ký tự; nếu lặp lại nhiều lần, bộ nhớ và áp lực GC có thể tăng. Chỉ ước lượng capacity khi có cơ sở từ đặc điểm dữ liệu hoặc kết quả đo.

Sau khi `toString()` trả chuỗi kết quả, `StringBuilder` vẫn có thể bị thay đổi nhưng **chuỗi đã nhận không đổi**:

```java
StringBuilder builder = new StringBuilder("item=");
builder.append(42);
String snapshot = builder.toString();

builder.append("!");
System.out.println(snapshot); // item=42
System.out.println(builder);  // item=42!
```

`StringBuffer` tương tự về mục đích xây dựng chuỗi nhưng có các thao tác đồng bộ hóa; không tự động chọn nó cho code một luồng chỉ vì nghĩ rằng vùng lưu bộ nhớ nhỏ hơn.

## 9. Ví dụ chạy được với Java 17+

Tạo tệp `StringMemoryDemo.java`, lưu UTF-8:

```java
import java.nio.charset.StandardCharsets;
import java.util.List;

public class StringMemoryDemo {
    public static void main(String[] args) {
        String first = "alpha";
        String sameLiteral = "alpha";
        String freshObject = new String("alpha");

        System.out.println("same literal reference: "
                + (first == sameLiteral));
        System.out.println("fresh object reference: "
                + (first == freshObject));
        System.out.println("same text: "
                + first.equals(freshObject));

        String source = "abcdefgh";
        String part = source.substring(2, 5);
        System.out.println("substring: " + part);

        StringBuilder builder = new StringBuilder(32);
        builder.append("item=").append(42);
        String snapshot = builder.toString();
        builder.append("!");
        System.out.println("snapshot: " + snapshot);
        System.out.println("builder: " + builder);

        String ascii = "ABC";
        String emoji = new String(Character.toChars(0x1F600));
        System.out.println("ASCII length: " + ascii.length());
        System.out.println("emoji UTF-16 units: " + emoji.length());
        System.out.println("emoji code points: "
                + emoji.codePointCount(0, emoji.length()));
        System.out.println("ASCII UTF-8 bytes: "
                + ascii.getBytes(StandardCharsets.UTF_8).length);

        List<String> parts = List.of("A", "B", "C");
        String joined = String.join("-", parts);
        System.out.println("joined: " + joined);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 StringMemoryDemo.java
java StringMemoryDemo
```

Stdout chuẩn:

```text
same literal reference: true
fresh object reference: false
same text: true
substring: cde
snapshot: item=42
builder: item=42!
ASCII length: 3
emoji UTF-16 units: 2
emoji code points: 1
ASCII UTF-8 bytes: 3
joined: A-B-C
```

Ví dụ này **kiểm chứng ngữ nghĩa Java**, không đo trực tiếp số byte của mỗi `String`. Biểu thức `getBytes(UTF_8)` tạo một biểu diễn UTF-8 để đếm kích thước đầu ra; không dùng nó như công cụ đọc mảng nội bộ. Việc `new String("alpha")` cho tham chiếu khác là hành vi quan sát được theo API, nhưng không suy ra cần sao chép cùng một lượng byte dữ liệu ký tự bên dưới.

## 10. Tối ưu số lượng chuỗi và đối tượng tạm

### 10.1. Chọn đúng công cụ nối chuỗi

| Tình huống | Cách làm thường phù hợp | Ghi chú |
| --- | --- | --- |
| Ghép vài trường trong một biểu thức | `"id=" + id + ", status=" + status` | Cú pháp dễ đọc; JDK hiện đại tối ưu nối biểu thức |
| Tích lũy nhiều phần trong vòng lặp | `StringBuilder` + `append` | Giữ một buffer phát triển, giảm nhu cầu tạo kết quả bất biến mỗi vòng |
| Ghép collection với delimiter | `String.join(delimiter, values)` | Tránh tự xử lý delimiter đầu/cuối |
| Ghép nhiều chuỗi trong Stream | `Collectors.joining(delimiter)` | Gọn khi đã có pipeline Stream |
| Căn lề, số chữ số, locale | `String.format`, `Formatter` | Chọn vì đúng định dạng; đo nếu chạy với tần suất rất cao |
| Duyệt/parse đoạn nhỏ | `indexOf` và xử lý chỉ số nếu phù hợp | Có thể giảm mảng/token tạm; đánh đổi độ phức tạp mã |

Tối ưu phù hợp nhất thường là **tránh công việc thừa**: không lặp lại format một thông báo mà người dùng không xem, không gọi `split` khi chỉ cần kiểm tra một tiền tố, và không tạo cùng một chuỗi chuyển đổi trong từng lượt nếu giá trị thực sự không đổi.

### 10.2. Không tạo output khi không sử dụng

Ví dụ một đoạn log có điều kiện:

```java
// Chỉ minh họa cấu trúc điều kiện; logEnabled/log là API của ứng dụng.
if (logEnabled) {
    String message = "Tổng=" + total + ", mã=" + code;
    log(message);
}
```

Bằng cách kiểm tra điều kiện trước, ta tránh phải xây dựng thông báo trong nhánh không dùng tới. Với framework logging, hãy dùng các cơ chế placeholder/lazy logging mà framework cung cấp khi phù hợp; `String.format` trước khi gọi logger có thể vẫn làm công việc dù log bị vô hiệu hóa.

### 10.3. Tái sử dụng dữ liệu không đồng nghĩa intern tất cả

Nếu cùng một bộ từ khóa/nhãn cố định xuất hiện rất nhiều lần, tái sử dụng đối tượng có thể hữu ích. Nhưng `intern()` mọi ID, URL hoặc giá trị ngẫu nhiên không có tỷ lệ lặp cao có thể gây overhead. Khi cần cache, xác định rõ phạm vi sống và giới hạn kích thước; nếu không, tiết kiệm cấp phát ngắn hạn có thể biến thành tăng retained heap dài hạn.

### 10.4. StringBuilder dùng lại có giới hạn

Có thể tái sử dụng `StringBuilder` trong một luồng xử lý có kiểm soát, gọi `setLength(0)` để làm trống. Nhưng **capacity lớn vẫn được giữ** sau khi reset, nên một request bất thường có thể khiến buffer chiếm chỗ quá lâu. Ngoài ra `StringBuilder` không thread-safe; không chia sẻ builder mutable giữa nhiều luồng nếu không có đồng bộ thích hợp. Tránh giữ builder khổng lồ trong `ThreadLocal` hoặc cache vô hạn.

## 11. Đo lường thay vì dự đoán số đối tượng

### 11.1. Những câu hỏi cần profiler trả lời

Khi nhận thấy GC hoạt động nhiều, độ trễ tăng hoặc heap cao, tìm:
- Loại đối tượng nào được **cấp phát nhiều nhất**, đặc biệt `String`, `byte[]` và các mảng liên quan?
- Call path nào tạo chúng? Có phải vòng lặp nối chuỗi, format, parse hay chuyển đổi không?
- Đó là **allocation rate** cao của đối tượng sống ngắn, hay **retained heap** cao do cache và tham chiếu lâu dài?
- Có nhiều chuỗi cùng nội dung được giữ lại không? Chúng có thật sự gây ảnh hưởng đáng kể đến ứng dụng?
- Sau khi sửa, throughput, latency, số lượng cấp phát và GC có cải thiện trên dữ liệu điển hình hay không?

Công cụ hữu ích có thể gồm **Java Flight Recorder (JFR)**, **Java Mission Control (JMC)**, **VisualVM**, **Eclipse Memory Analyzer (MAT)** và profiler phù hợp môi trường. JFR có các sự kiện/cách lấy mẫu allocation; heap dump cho phép xem retained heap và đường giữ tham chiếu. Kết quả phụ thuộc cấu hình ghi và công cụ, nên cần đọc đúng định nghĩa metric.

### 11.2. Quan sát allocation với JFR

Một ví dụ trên JDK có công cụ `jfr`:

```bash
java -XX:StartFlightRecording=filename=string-profile.jfr,settings=profile \
     -jar application.jar
```

Lệnh mang tính **minh họa** cho ứng dụng thực tế `application.jar`; phần xuống dòng ở ví dụ shell chỉ dùng được trong môi trường cho phép escape tương ứng, còn trên Windows PowerShell có thể viết lệnh cùng một dòng. Có thể xem file `.jfr` bằng JMC hoặc công cụ `jfr` phù hợp. Việc record có đúng sự kiện allocation mong muốn hay không phụ thuộc profile và cấu hình JFR.

Nếu cần số liệu đáng tin cho một thao tác nhỏ, tránh tự so sánh `Runtime.totalMemory() - freeMemory()` trước/sau **một lời gọi**. GC, JIT, escape analysis, cấp phát của framework và các luồng khác khiến kết quả rất nhiễu.

### 11.3. Benchmark có kiểm soát

Với phép nối/định dạng ở hot path, nên dùng **JMH** thay cho vòng lặp bấm giờ tự viết. Khi benchmark:
1. Đặt rõ giả thuyết: giảm allocation/op, tăng throughput, hay giảm độ trễ?
2. Dùng **đầu vào biến thiên có kiểm soát** để tránh hằng số hóa toàn bộ phép toán.
3. Warm-up để JVM tối ưu trước khi đo, chạy nhiều vòng/fork phù hợp.
4. Tiêu thụ kết quả để tránh dead-code elimination (JMH cung cấp cơ chế phù hợp).
5. Đo cả số byte cấp phát trên operation nếu mục tiêu là bộ nhớ, và xem tác động GC ở mức ứng dụng.

Một benchmark micro có thể chứng minh một thao tác cụ thể nhanh hơn trong cấu hình đo, nhưng không đủ để khẳng định mọi chương trình nên thay `+` bằng `StringBuilder`.

## 12. Cấu hình JVM và tối ưu hóa liên quan

### Compact Strings

OpenJDK/HotSpot Java 17 thông thường bật Compact Strings theo mặc định. Một số JVM cho phép điều khiển qua cờ triển khai như `-XX:-CompactStrings`; đây là tùy chọn **phụ thuộc VM**, không phải cú pháp Java chuẩn hay khuyến nghị tối ưu mặc định. Thay đổi cờ có thể làm tăng footprint hoặc thay đổi hiệu năng; chỉ dùng cho nghiên cứu/benchmark có kiểm soát.

### String deduplication

Một số garbage collector, điển hình các cấu hình G1, hỗ trợ **String deduplication** qua cơ chế/cờ JVM như `-XX:+UseStringDeduplication` trên những bản phát hành tương ứng. Ý tưởng là chia sẻ mảng lưu nội dung giữa các đối tượng `String` có nội dung giống nhau, trong khi **đối tượng String vẫn khác nhau**. Tối ưu này không giúp mọi chương trình và có overhead rà soát/hash; cần kiểm tra GC/JDK đang chạy và đo bằng công cụ trước khi bật.

### JIT và escape analysis

HotSpot có thể tối ưu một số phép nối chuỗi, nội tuyến phương thức và thậm chí loại bỏ những cấp phát không “thoát” khỏi phạm vi tính toán quan sát được. Vì vậy:
- Nguồn Java có từ `new` hoặc phép tạo chuỗi không đồng nghĩa runtime chắc chắn cấp phát riêng đối tượng ở heap trong mọi lần chạy.
- Benchmark quá đơn giản có thể cho kết quả bất thường vì JIT đã hằng số hóa hoặc loại bỏ phần công việc.
- Không nên thay đổi code chỉ dựa trên số đối tượng **suy luận** từ đoạn mã; phải kết hợp profiler với hiểu biết về runtime.

Các cờ và chi tiết thuật toán JVM có thể khác theo JDK, GC và vendor; chú ý phiên bản trước khi sao chép hướng dẫn tuning.

## 13. Những hiểu lầm và lỗi thường gặp

**1. “String dài N ký tự luôn chiếm 2N byte.”** Sai nếu dùng như phép tính tổng footprint. `N = length()` là code unit UTF-16; có overhead và Compact Strings. Dữ liệu trên heap không đồng nhất với byte UTF-8.

**2. “Unicode/emoji luôn có length bằng 1.”** Không đúng với code point ngoài BMP: một emoji có thể chiếm hai UTF-16 code unit, nên `length()` có thể là 2. Nếu cần đếm code point, dùng `codePointCount(...)`, đồng thời nhớ code point chưa chắc tương ứng một *grapheme cluster* hiển thị.

**3. “Mỗi dấu + luôn tạo thêm một String trung gian.”** Kết quả biểu thức là chuỗi, nhưng compiler/JIT có thể hằng số hóa, gộp công việc và giảm cấp phát. Vấn đề thực tế là nối tích lũy trong vòng lặp và tốc độ phân bổ đã đo.

**4. “StringBuilder không tốn bộ nhớ.”** Sai. Nó là đối tượng có buffer; mở rộng buffer hoặc cấp trước quá lớn đều có chi phí. `toString()` tạo kết quả bất biến theo API.

**5. “Substring Java 17 luôn giữ nguyên cả mảng lớn gốc.”** Đây là kinh nghiệm từ một số JDK cũ có layout khác; đừng dùng nó để kết luận cho Java 17. Phải đo heap của JVM thực tế nếu cần biết khả năng chia sẻ dữ liệu.

**6. “intern và String deduplication là một.”** `intern` canonical hóa tham chiếu String, còn deduplication (khi hỗ trợ) hướng tới chia sẻ bộ đệm của các String khác đối tượng.

**7. “Heap ít chuỗi đang sống nên không thể có vấn đề String.”** Một hệ thống có thể có live heap thấp nhưng allocation rate cao, dẫn đến GC nhiều và giảm throughput.

**8. “getBytes(UTF_8).length là kích thước String trên heap.”** Đây là byte của biểu diễn mã hóa được xuất ra, không gồm overhead object và cũng không phản ánh chắc chắn cách JVM lưu bên trong.

**9. “Cứ thêm cache sẽ tiết kiệm bộ nhớ.”** Cache có thể tăng retained heap và kéo dài vòng đời đối tượng. Chỉ cache giá trị dùng lại đủ nhiều, có giới hạn phù hợp.

**10. “Benchmark nối chuỗi trên một hằng số đủ để chọn tối ưu.”** Constant folding và tối ưu JIT có thể làm kết quả mất tính đại diện; cần JMH và profile ứng dụng thực.

## 14. Bài tập tự luyện

### Bài 1. Phân biệt length và UTF-8

```java
String ascii = "ABC";
String emoji = new String(Character.toChars(0x1F600));
```

Hỏi: `ascii.length()`, `emoji.length()` và `emoji.codePointCount(0, emoji.length())` bằng bao nhiêu? Có thể lấy các số đó nhân 2 để suy ra heap bytes không?

**Đáp án:** Lần lượt là **3**, **2**, **1**. Không thể suy ra heap bytes vì chúng là các phép đếm nội dung; heap còn phụ thuộc storage representation, overhead và JVM.

### Bài 2. Về danh tính đối tượng

```java
String x = "value";
String y = "value";
String z = new String("value");
```

Hỏi: `x == y`, `x == z`, `x.equals(z)`?

**Đáp án:** `true`, `false`, `true`. Hai literal dùng cùng chuỗi canonical; `new String(...)` tạo đối tượng riêng nhưng nội dung bằng nhau.

### Bài 3. Thay vòng lặp nối nhiều phần

Từ danh sách `parts` có kiểu `List<String>`, chuyển đoạn:

```java
String result = "";
for (String part : parts) {
    result += part;
}
```

thành cách xây chuỗi có một buffer mutable:

```java
StringBuilder builder = new StringBuilder();
for (String part : parts) {
    builder.append(part);
}
String result = builder.toString();
```

Giải thích: mã sau tránh nhu cầu tạo một kết quả `String` bất biến mới trên mỗi vòng xét theo cấu trúc thuật toán; chi phí thực tế vẫn cần profiler xác nhận.

### Bài 4. Kiểm tra chi phí của một pipeline

```java
String[] values = input.split(",");
String joined = String.join("|", values);
```

Hỏi: có thể chắc chắn chỉ cấp phát đúng hai đối tượng (một mảng và một chuỗi) không?

**Đáp án:** Không. Có thể có các token `String`, mảng và đối tượng nội bộ khác; số allocation thực tế còn chịu tối ưu JVM. Đo profiler nếu đây là hot path.

### Bài 5. Xác định phép đo đúng

Một dịch vụ tạo hàng triệu chuỗi tạm mỗi giây nhưng retained heap không tăng rõ ràng. Nên xem **shallow size** của một `String` hay **allocation rate, GC activity và call path**?

**Đáp án:** Ưu tiên allocation rate, GC và call path để tìm nơi tạo đối tượng ngắn hạn. Shallow size của một đối tượng đơn lẻ không phản ánh áp lực cấp phát theo thời gian.

## 15. Ghi nhớ

- **`String` có nhiều tầng chi phí:** đối tượng, dữ liệu nội bộ, overhead mảng, căn chỉnh và vòng đời tham chiếu; `length()` không phải kích thước heap.
- **Compact Strings** trong OpenJDK/HotSpot Java 9+ thường dùng `byte[]` với Latin-1 hoặc UTF-16 để giảm phần dữ liệu; đó là **chi tiết triển khai**.
- **Chuỗi immutable:** thao tác tạo nội dung mới có thể dẫn tới cấp phát `String` và mảng mới; compiler/JIT có thể tối ưu một phần, không được đếm đối tượng chỉ từ cú pháp.
- **Literal/intern** và **string deduplication** là hai kỹ thuật khác nhau, với tác động khác nhau lên tham chiếu và dữ liệu.
- **Nối trong vòng lặp:** cân nhắc `StringBuilder`; nối vài giá trị một biểu thức bằng `+` thường phù hợp và dễ đọc.
- **Tối ưu đúng mục tiêu:** phân biệt lượng heap còn sống với tốc độ cấp phát đối tượng tạm; đo bằng profiler, JFR, heap dump và JMH phù hợp.
- **Không tối ưu mù:** cấu hình VM, GC, bộ nhớ và thời gian chạy phải được kiểm tra trên workload thực.

Tài liệu tham khảo:
- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html)
- [OpenJDK JEP 254 — Compact Strings](https://openjdk.org/jeps/254)
- [OpenJDK JEP 280 — Indify String Concatenation](https://openjdk.org/jeps/280)
- [OpenJDK JEP 192 — String Deduplication in G1](https://openjdk.org/jeps/192)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-internals-004">Bộ nhớ đệm hashCode là chi tiết cài đặt</a>

<details>
<summary>Xem chi tiết</summary>

# Bộ nhớ đệm `hashCode` của `String` là chi tiết cài đặt

## 1. Ý chính: giá trị băm là hợp đồng API, cách lưu đệm là nội bộ

`String` trong Java là kiểu **bất biến** (*immutable*): sau khi một đối tượng `String` được tạo, dãy ký tự của đối tượng đó không thay đổi. Vì thế, giá trị `hashCode()` tính từ nội dung của cùng một `String` luôn ổn định.

Cần phân biệt hai điều:

1. **Hợp đồng được Java công khai:** `String.hashCode()` phải tính hash theo công thức đã ghi trong tài liệu API. Hai chuỗi có nội dung bằng nhau theo `equals` phải có cùng hash; chuỗi rỗng có hash bằng `0`. Chương trình được phép dựa vào các tính chất này.
2. **Cách JDK tối ưu phép tính:** Một số triển khai, như OpenJDK 17, lưu kết quả hash đã tính trong các trường riêng của đối tượng để tránh phải quét lại ký tự. Cấu trúc trường, điều kiện cache, thời điểm lưu và cách đồng bộ là **chi tiết cài đặt**. Chương trình ứng dụng không nên phụ thuộc vào chúng.

Nói cách khác, kết quả `s.hashCode()` được xác định bởi *nội dung* của `s` và hợp đồng API, không phải bởi việc JVM có dùng bộ nhớ đệm hay không. Việc có cache cũng không biến `hashCode()` thành hàm định danh đối tượng hoặc hàm băm mật mã.

## 2. Công thức chính thức của `String.hashCode()`

Tài liệu Java định nghĩa hash cho chuỗi gồm `n` đơn vị ký tự `char` là:

```text
h = s[0] × 31^(n-1) + s[1] × 31^(n-2) + ... + s[n-1]
```

Phép tính sử dụng kiểu `int` 32-bit của Java. Viết dưới dạng vòng lặp tương đương:

```java
static int calculateHash(String text) {
    int h = 0;
    for (int i = 0; i < text.length(); i++) {
        h = 31 * h + text.charAt(i);
    }
    return h;
}
```

Đây là **phương thức minh họa** có thể đặt vào một lớp Java; nó thể hiện công thức, không mô phỏng cơ chế cache của `String`.

- `s.charAt(i)` truy cập một **UTF-16 code unit**; các ký tự ngoài BMP (ví dụ nhiều emoji) được biểu diễn bằng hai `char` và cả hai tham gia phép băm.
- `int` có thể tràn; Java dùng phép toán số nguyên 32-bit với phần bit thấp được giữ lại. Không thêm `long` hoặc cách xử lý overflow khác nếu muốn tính đúng hash chuẩn của `String`.
- Với chuỗi rỗng, không có lần lặp nào, nên kết quả là `0`.
- Công thức phụ thuộc vào thứ tự và giá trị các `char`, không phụ thuộc vào charset mã hóa file, encoding của terminal, địa chỉ đối tượng hay cách JVM lưu chuỗi trong bộ nhớ.
- Giá trị có thể âm vì kết quả được diễn giải như `int` có dấu. Hash không phải một số thứ tự hay ID duy nhất.

Ví dụ, `"Aa"` và `"BB"` có hash giống nhau:

```text
"Aa": 65 × 31 + 97 = 2112
"BB": 66 × 31 + 66 = 2112
```

Hai chuỗi này **khác nội dung** dù có cùng `hashCode()`. Đây là *va chạm hash* (*hash collision*), không phải lỗi của Java.

## 3. Cache hoạt động như thế nào trong một triển khai cụ thể?

Một cách triển khai tối ưu phổ biến của `String.hashCode()` là:

1. Nếu đã lưu được hash cho chính đối tượng `String` hiện tại, trả lại giá trị đã lưu.
2. Nếu chưa có kết quả được lưu, tính hash theo công thức chuẩn.
3. Có thể ghi kết quả vào một trường riêng để những lần gọi sau không phải duyệt lại chuỗi.

Vì `String` bất biến nên kết quả không cần bị vô hiệu hóa khi nội dung “thay đổi”: muốn tạo nội dung khác thì Java tạo ra một kết quả kiểu `String` khác (trừ những tối ưu trả lại cùng đối tượng khi nội dung không thay đổi). Thao tác như `concat`, `replace`, `substring` không cho phép sửa ký tự trực tiếp trong đối tượng gốc.

**Ví dụ cụ thể của OpenJDK 17:** mã nguồn lớp `java.lang.String` có các trường riêng `hash` (kiểu `int`) và `hashIsZero` (kiểu `boolean`). Khi cần, `hashCode()` có thể tính hash dựa trên cách mã hóa nội bộ rồi lưu kết quả. Nếu hash thật bằng `0`, cờ `hashIsZero` giúp phân biệt trường hợp “đã tính và bằng 0” với “chưa có giá trị hash được lưu” mà không cần quét lại chuỗi.

Chi tiết này nhằm giải thích *vì sao có thể tối ưu*, không phải lời hứa rằng mọi JDK, mọi bản phát hành hoặc mọi đường đi của JVM đều có hai trường mang đúng những tên đó. Trong các bản OpenJDK cũ hơn, xử lý hash bằng `0` từng khác; triển khai trong tương lai cũng có thể đổi.

**Lưu ý về hash bằng 0:** `0` hoàn toàn là kết quả hợp lệ, không tự động có nghĩa “chưa được cache”. Cả `""` lẫn chuỗi gồm một ký tự NUL (`"\0"`) đều có hash bằng `0`. Do đó, tuyệt đối không suy luận rằng những chuỗi có hash `0` luôn được tính lại hoặc luôn đã lưu vào bộ nhớ đệm.

Không nên viết mã reflection để đọc/sửa các trường riêng như `hash`, `hashIsZero` hoặc `value`. Chúng không thuộc API công khai, có thể bị module system ngăn truy cập và việc can thiệp có nguy cơ phá vỡ hành vi của các cấu trúc dùng khóa chuỗi.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu thành file **`StringHashCodeDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;

public class StringHashCodeDemo {
    private static int calculateHash(String text) {
        int h = 0;
        for (int i = 0; i < text.length(); i++) {
            h = 31 * h + text.charAt(i);
        }
        return h;
    }

    public static void main(String[] args) {
        String first = "Aa";
        String second = "BB";
        String equalCopy = new String(new char[] {'A', 'a'});

        System.out.println("Aa hash: " + first.hashCode());
        System.out.println("BB hash: " + second.hashCode());
        System.out.println("Aa equals BB: " + first.equals(second));
        System.out.println("Equal text, same hash: "
                + (first.hashCode() == equalCopy.hashCode()));
        System.out.println("Different objects: " + (first != equalCopy));

        System.out.println("Empty hash: " + "".hashCode());
        System.out.println("NUL hash: " + "\0".hashCode());
        System.out.println("Formula matches: "
                + (calculateHash(first) == first.hashCode()));

        Map<String, Integer> map = new HashMap<>();
        map.put(first, 1);
        map.put(second, 2);

        System.out.println("Map size: " + map.size());
        System.out.println("Map Aa: " + map.get("Aa"));
        System.out.println("Map BB: " + map.get("BB"));

        String changed = first.concat("!");
        System.out.println("Original: " + first);
        System.out.println("Concatenated: " + changed);
    }
}
```

Biên dịch và chạy:

```bash
javac StringHashCodeDemo.java
java StringHashCodeDemo
```

**Stdout chính xác** theo hợp đồng Java 17+:

```text
Aa hash: 2112
BB hash: 2112
Aa equals BB: false
Equal text, same hash: true
Different objects: true
Empty hash: 0
NUL hash: 0
Formula matches: true
Map size: 2
Map Aa: 1
Map BB: 2
Original: Aa
Concatenated: Aa!
```

Diễn giải:

- `first.hashCode()` và `second.hashCode()` đều bằng `2112` nhưng `first.equals(second)` là `false`: cùng hash không có nghĩa là cùng chuỗi.
- `equalCopy` được tạo từ một mảng `char` mới, nên là đối tượng khác `first` nhưng có cùng nội dung; vì thế hash của hai đối tượng bằng nhau.
- Chuỗi rỗng và chuỗi chứa ký tự NUL đều có hash bằng `0`. Output này **không cho biết** triển khai hiện tại có cache kết quả `0` hay không.
- `calculateHash(first)` cho kết quả giống `first.hashCode()` mà không dùng một trường cache nào. Điều này minh họa kết quả theo đặc tả, không kiểm tra cấu trúc dữ liệu nội bộ của JDK.
- `HashMap` vẫn giữ cả hai khóa `"Aa"` và `"BB"` vì ngoài hash, cơ chế so sánh khóa còn xét quan hệ bằng nhau khi cần.
- `concat("!")` tạo chuỗi có nội dung mới; biến `first` vẫn tham chiếu đến giá trị `"Aa"` ban đầu. Do đó, hash của đối tượng gốc không phải được “cập nhật để theo nội dung mới”.

**Không thể chứng minh cache bằng một đoạn code chỉ in giá trị `hashCode()`.** Một triển khai luôn tính lại hash và một triển khai có cache vẫn phải cho cùng các kết quả trên.

## 5. Chi phí tính toán và giới hạn của tối ưu hóa cache

Nếu tính lại công thức bằng cách đi qua `n` code unit thì số bước tỷ lệ với chiều dài chuỗi: **O(n)**. Với chiến lược cache, lần tính đầu có thể là O(n), còn lần đọc lại một giá trị đã lưu thường là **O(1)** về mặt số thao tác với dữ liệu chuỗi.

Đây là mô hình giải thích một tối ưu khả dĩ, **không phải cam kết độ phức tạp cho mọi lần gọi** của mọi triển khai `String.hashCode()`. Kết quả thực tế còn chịu ảnh hưởng của JIT, escape analysis, constant folding, độ dài chuỗi, việc đối tượng có dùng lại hay không và cách triển khai JVM.

| Tình huống | Điều có thể kỳ vọng về mặt ứng dụng |
| --- | --- |
| Gọi `hashCode()` nhiều lần trên cùng đối tượng `String` | Luôn nhận cùng giá trị; JDK có thể tận dụng giá trị đã tính. |
| Tạo rất nhiều đối tượng `String` bằng nhau về nội dung | Mỗi đối tượng vẫn có trạng thái nội bộ riêng; không được giả định mọi đối tượng chia sẻ cùng cache. |
| Hash bằng `0` | Kết quả là `0`; việc tránh tính lại tùy triển khai. |
| Thay đổi cách lưu chuỗi giữa các phiên bản JDK | Công thức và kết quả công khai phải giữ nguyên; kỹ thuật lưu byte và cache có thể khác. |
| Gọi `hashCode()` từ nhiều thread | Mọi lời gọi phải trả kết quả đúng; không được dựa vào việc hash chỉ được tính một lần. |

Khi đánh giá hiệu năng, cần đo **ứng dụng thật** bằng phương pháp benchmark phù hợp như JMH. Không nên kết luận dựa trên `System.nanoTime()` quanh một hoặc vài lần gọi `hashCode()`, vì mã có thể được JIT tối ưu, loại bỏ phép tính hoặc thay đổi theo warm-up.

## 6. Thread safety: vì sao trường cache không nhất thiết phải là `final`?

`String` có nội dung bất biến, nhưng một triển khai có thể **lưu trạng thái tính toán nội bộ** vào trường cache sau khi khởi tạo đối tượng. “Bất biến” của `String` ở cấp API nói về nội dung ký tự quan sát được, không bắt buộc tất cả bit trong bộ nhớ đối tượng đều bất biến sau constructor.

Trong OpenJDK 17, phần tối ưu hash được thiết kế để các lần gọi đồng thời có thể đọc hoặc ghi cache mà không cần khóa tường minh. Nếu hai thread cùng lần đầu gọi `hashCode()`, một số phép tính có thể bị lặp lại. Do phép tính băm dựa trên nội dung bất biến, kết quả công khai vẫn như nhau. Mã nguồn OpenJDK mô tả trường hợp này là một kiểu *benign data race* với những ràng buộc cụ thể để giữ kết quả đúng.

Nhận định trên chỉ áp dụng cho thiết kế được kiểm soát trong `String`, **không phải khuyến nghị chung** rằng lập trình viên có thể thoải mái đặt các trường cache có data race trong đối tượng do mình viết. Với lớp mutable hoặc phép tính có side effect, cache thiếu đồng bộ có thể dẫn đến lỗi hoặc dữ liệu không nhất quán.

Ứng dụng nhiều thread có thể gọi `hashCode()` trên cùng một `String` bình thường; không cần tự khóa chuỗi để bảo vệ cache nội bộ. Tuy nhiên, **không được dùng số lần tính hash, số lần ghi trường cache hoặc trạng thái cache làm cơ chế giao tiếp giữa các thread**.

## 7. Liên hệ với `equals`, `HashMap` và bộ nhớ

Hợp đồng liên quan:

- Nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` phải là `true`.
- Nếu hai hash bằng nhau thì **không suy ra** `equals` là `true`.
- `String.equals` so sánh nội dung; toán tử `==` so sánh tham chiếu. Tối ưu cache hash không thay đổi hai quy tắc này.
- `HashMap` và `HashSet` dùng hash để hỗ trợ tìm bucket/vị trí tra cứu và dùng quan hệ bằng nhau để phân biệt khóa khi cần. `HashMap` còn có thể trộn bit hash trong triển khai riêng; cấu trúc bucket không phải một phần của hợp đồng `String.hashCode()`.
- `String.hashCode()` **không dùng để mã hóa an toàn**, kiểm tra tính toàn vẹn chống sửa đổi hay lưu mật khẩu. Hash 32-bit dễ va chạm và dự đoán được; nếu cần mật mã, dùng API và thuật toán bảo mật phù hợp.

Một số chi tiết riêng tư của OpenJDK còn liên quan đến *Compact Strings*: từ JDK 9, nhiều chuỗi được lưu hiệu quả bằng mảng byte và thông tin biểu diễn Latin-1/UTF-16 thay vì luôn dùng mảng `char` như trước. Cách lưu này có thể ảnh hưởng chi phí bộ nhớ và đường tính toán, nhưng **không đổi công thức hash theo các UTF-16 code unit logic**. Vì vậy, không suy kích thước đối tượng hoặc số byte cache từ công thức hash; chúng phụ thuộc phiên bản JDK, JVM và cấu hình.

Có trường cache cũng không đồng nghĩa với việc mọi `String` lập tức tốn chi phí quét ký tự tính hash. Một triển khai có thể tính **lười** (*lazy*) khi hash được yêu cầu; việc có hay chưa có giá trị lưu là trạng thái nội bộ. Mã ứng dụng chỉ nên dựa vào dữ liệu công khai: chuỗi, kết quả `equals` và `hashCode()`.

## 8. Sai lầm thường gặp và cách tránh

**Sai lầm 1: “`String.hashCode()` nhanh O(1) trên mọi lần gọi vì Java luôn cache.”**

Điều đúng là một triển khai có thể cache và lần đọc cache thường rẻ. Đặc tả không yêu cầu cấu trúc cache cụ thể; không thể đảm bảo lần gọi đầu, hai thread cùng gọi hoặc mọi JVM đều chỉ phải làm một lượng việc cố định.

**Sai lầm 2: “Nếu hash bằng `0` thì chưa tính.”**

`0` là giá trị hash hợp lệ, ví dụ `""` và `"\0"`. Một số bản OpenJDK có cờ riêng đánh dấu hash đã tính bằng `0`. Không suy đoán cache chỉ từ giá trị trả về.

**Sai lầm 3: “Hai chuỗi có hash giống nhau là cùng nội dung.”**

`"Aa"` và `"BB"` đều có hash `2112` nhưng không bằng nhau. Khi cần kiểm tra nội dung, dùng `equals`; không thay bằng so sánh `hashCode()`.

**Sai lầm 4: “Muốn tối ưu, sửa trường `hash` bằng reflection.”**

Trường riêng thuộc triển khai nội bộ; can thiệp phá vỡ tính nhất quán và có thể không thực hiện được do kiểm soát truy cập của module system. Không phụ thuộc vào layout đối tượng.

**Sai lầm 5: “Cache hash đồng nghĩa String có thể bị đổi nội dung.”**

Cache chỉ ghi lại kết quả tính toán, không thay đổi các ký tự của `String`. Các thao tác tạo nội dung khác không chỉnh trực tiếp chuỗi gốc.

**Sai lầm 6: “Có thể nhìn thời gian một lần gọi để xác định chắc chắn cache.”**

JIT, inlining, constant folding, warm-up và benchmark sai cách có thể làm phép đo vô nghĩa. Không dùng thời gian đo thô như bằng chứng về cấu trúc nội bộ.

## 9. Quy tắc áp dụng trong mã Java

1. Dùng `String.hashCode()` bình thường khi API thu thập hoặc cấu trúc dữ liệu cần hash; không tự tính lại hoặc tự cache trừ khi có lý do nghiệp vụ/đo đạc rõ ràng.
2. Dùng `equals` để xác định nội dung bằng nhau; dùng `==` chỉ khi thật sự cần kiểm tra cùng tham chiếu.
3. Khi dùng `String` làm khóa `HashMap`/`HashSet`, có thể dựa vào tính bất biến và hợp đồng `equals`/`hashCode`, không dựa vào cơ chế cache của một bản JDK.
4. Chấp nhận hash collision như hiện tượng bình thường. Đừng sử dụng hash làm ID duy nhất, dấu vân tay mật mã hoặc thay thế toàn bộ dữ liệu gốc.
5. Khi đọc mã nguồn JDK để học tối ưu, phân biệt rõ **“OpenJDK phiên bản X thực hiện như thế nào”** với **“Java SE API cam kết điều gì”**.

## Tài liệu tham khảo

- [Java SE 17 — `String.hashCode()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#hashCode()) — nguồn chính thức của công thức và giá trị hash.
- [Java SE 17 — `Object.hashCode()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode()) — hợp đồng chung giữa hash và equality.
- [OpenJDK 17 — mã nguồn `java.lang.String`](https://github.com/openjdk/jdk17/blob/master/src/java.base/share/classes/java/lang/String.java) — ví dụ cụ thể về trường `hash`/`hashIsZero` và cách tối ưu; đây là phần triển khai, không phải API công khai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-internals-005">Constant pool và vai trò của Constable, ConstantDesc</a>

<details>
<summary>Xem chi tiết</summary>

# Constant pool và vai trò của `Constable`, `ConstantDesc` trong Java String

Khi đọc tài liệu về `String`, ta dễ gặp ba thuật ngữ rất giống nhau: **constant pool**, **String pool** và **constant descriptor**. Chúng liên hệ với nhau nhưng phục vụ những mục đích khác nhau:

- **Constant pool của class file**: bảng thông tin hằng và tham chiếu ký hiệu trong tệp `.class`, được JVM sử dụng khi nạp và chạy mã.
- **String pool / string interning**: cơ chế cho phép những chuỗi có cùng nội dung dùng chung một đối tượng `String` chuẩn hóa (*canonical*), ví dụ qua literal và `intern()`.
- **`Constable` và `ConstantDesc`**: API từ Java 12 để **mô tả một giá trị dưới dạng hằng có thể biểu diễn danh nghĩa (*nominally*)** và, khi cần, phân giải mô tả ấy trong một ngữ cảnh truy cập phù hợp.

Chúng **không phải ba tên gọi của cùng một vùng bộ nhớ**. Hiểu đúng sự khác biệt giúp tránh nhầm lẫn về việc literal được lưu ở đâu, vì sao `"abc" == "abc"` thường là `true` và tại sao `String` triển khai cả hai interface `Constable`, `ConstantDesc`.

## 1. Constant pool trong tệp `.class` là gì?

Một class file hợp lệ có cấu trúc bảng `constant_pool`. Đây là **bảng dữ liệu nhị phân**, chứa nhiều kiểu entry phục vụ bytecode và liên kết biểu tượng, chứ không phải một kho chỉ chứa chuỗi Java.

Một số entry điển hình:

| Loại entry trong class file | Vai trò |
| --- | --- |
| `CONSTANT_Utf8` | Lưu tên lớp, tên phương thức, descriptor, các chuỗi văn bản và dữ liệu ký hiệu khác theo dạng modified UTF-8 của JVM |
| `CONSTANT_String` | Biểu diễn một **hằng `String`**, tham chiếu đến một entry `CONSTANT_Utf8` chứa nội dung |
| `CONSTANT_Class` | Tham chiếu ký hiệu đến lớp hoặc interface |
| `CONSTANT_NameAndType` | Tên và descriptor của field hoặc method |
| `CONSTANT_Fieldref`, `CONSTANT_Methodref` | Tham chiếu ký hiệu đến field và method |
| `CONSTANT_Integer`, `CONSTANT_Long`, ... | Giá trị số được biểu diễn trực tiếp bằng các loại entry tương ứng |
| `CONSTANT_MethodHandle`, `CONSTANT_MethodType` | Thông tin method handle và method type |
| `CONSTANT_Dynamic`, `CONSTANT_InvokeDynamic` | Hỗ trợ hằng động và liên kết lời gọi động; **không phải cùng một loại entry** |

Khi viết:

```java
String text = "Java";
```

Trình biên dịch thường ghi một entry `CONSTANT_String` tham chiếu đến `CONSTANT_Utf8` chứa nội dung `Java`. Bytecode có thể dùng lệnh `ldc` để nạp tham chiếu tới đối tượng `String` tương ứng.

Điều này **không có nghĩa toàn bộ đối tượng `String`, gồm header và dữ liệu runtime, được nhét nguyên trong tệp `.class`**. Tệp `.class` chỉ chứa **biểu diễn nhị phân và ký hiệu** để JVM tạo/lấy đối tượng khi thực thi.

Một sơ đồ đơn giản:

```text
Java source:       String s = "Java";
                           |
                           v
Class file:       CONSTANT_String --> CONSTANT_Utf8("Java")
                           |
                      bytecode ldc
                           |
                           v
JVM runtime:      tham chiếu tới String canonical "Java"
```

Trong JVM còn có **run-time constant pool** gắn với mỗi class/interface được nạp: nó được dẫn xuất từ cấu trúc `constant_pool` trong class file và chứa dữ liệu/tham chiếu đã hoặc sẽ được JVM phân giải. Khái niệm run-time constant pool này cũng **khác với cơ chế intern chuỗi**.

## 2. String pool là gì? Vì sao literal thường dùng chung đối tượng?

Java quy định rằng các **string literal có cùng nội dung** biểu diễn cùng một đối tượng `String`. Chuỗi tạo bằng cách ghép **các biểu thức hằng ở thời điểm biên dịch** cũng có thể trở thành cùng literal đã được intern.

```java
String a = "Java";
String b = "Ja" + "va"; // biểu thức hằng, được gộp lúc biên dịch

System.out.println(a == b);       // true
System.out.println(a.equals(b));  // true
```

`==` so sánh **cùng tham chiếu** (cùng đối tượng); `equals` so sánh **nội dung chuỗi**. Nhờ cơ chế interning dành cho literal và biểu thức hằng, `a == b` là `true` trong ví dụ này.

Nhưng một chuỗi được tạo mới không mặc nhiên trỏ đến cùng đối tượng:

```java
String a = "Java";
String c = new String("Java");

System.out.println(a == c);          // false
System.out.println(a.equals(c));     // true
System.out.println(a == c.intern());  // true
```

- `new String("Java")` tạo một đối tượng `String` mới riêng biệt (dù cùng nội dung).
- `intern()` trả về tham chiếu đến đại diện canonical tương ứng với nội dung ấy.
- `equals` mới là lựa chọn thông thường để so sánh **giá trị văn bản**.

Tại đây, **String pool** là ý tưởng/cơ chế quản lý các tham chiếu `String` canonical trong JVM, chứ không phải một bảng entry `CONSTANT_String` độc lập ở file `.class`. Không nên suy ra địa chỉ vật lý chính xác của chuỗi hoặc cơ chế quản lý bộ nhớ chỉ từ từ “pool”. Vị trí lưu trữ và tối ưu hóa nội bộ có thể thay đổi theo phiên bản và triển khai JVM.

### Ghép chuỗi ở runtime có giống hằng lúc biên dịch không?

`"Ja" + "va"` được tính như biểu thức hằng. Ngược lại:

```java
String left = "Ja";
String runtime = left + "va";
```

`runtime` là kết quả **ghép ở runtime** vì `left` là biến cục bộ thông thường, không phải biến hằng. Không nên dựa vào `runtime == "Java"` để kiểm tra nội dung. Trình biên dịch/JVM có thể tối ưu hóa ghép chuỗi, nhưng **tính đúng đắn của chương trình phải dựa trên `equals`**. Có thể gọi `runtime.intern()` nếu thực sự cần tham chiếu canonical và hiểu chi phí của việc đó.

## 3. `Constable` — giá trị có thể tự mô tả như một hằng

Từ Java 12, package `java.lang.constant` cung cấp interface:

```java
public interface Constable {
    Optional<? extends ConstantDesc> describeConstable();
}
```

Một kiểu `Constable` có những giá trị **có thể mô tả dưới dạng một hằng có thể biểu diễn trong mô hình constant pool của class file**. `describeConstable()` tạo mô tả danh nghĩa khi khả thi.

Điều cần nhớ:

- `Constable` thể hiện **khả năng mô tả**, không có nghĩa đối tượng đang nằm trong String pool.
- Phương thức trả về `Optional` vì **không phải mọi instance của mọi kiểu `Constable` đều mô tả được**. Khi không thể tạo mô tả, nó có thể trả về `Optional.empty()`.
- Kết quả là một `ConstantDesc`, biểu diễn giá trị bằng API mô tả hằng, chứ không bắt buộc là đối tượng runtime ban đầu.

Ví dụ, một số kiểu có biểu diễn hằng quen thuộc như `String`, `Integer`, `Long`, `Float` và `Double` có thể tự đóng vai trò là descriptor; `Class` dùng `ClassDesc` làm mô tả danh nghĩa tương ứng. Các kiểu khác có thể cần descriptor phức tạp hơn.

**Không nhầm `Constable` với `final` hay `static final`**. `final` nói về khả năng gán lại biến/field; một object có thể triển khai `Constable` dù được tạo mới ở runtime và không nằm trong một field `final` nào.

## 4. `ConstantDesc` — mô tả danh nghĩa và phân giải hằng

Interface `ConstantDesc` thuộc cùng package và cung cấp phương thức chính:

```java
Object resolveConstantDesc(MethodHandles.Lookup lookup)
        throws ReflectiveOperationException;
```

Đây là chữ ký khái quát của interface; các kiểu triển khai có thể trả về kiểu cụ thể hơn.

Phương thức `resolveConstantDesc(lookup)` yêu cầu hệ thống **phân giải một mô tả** thành một giá trị runtime trong ngữ cảnh tra cứu/truy cập được chỉ định. Đối với descriptor biểu diễn lớp, phương thức hoặc hằng động, bước phân giải có thể cần truy cập metadata, class loader, bootstrap hoặc gặp lỗi truy cập.

Phân biệt vai trò:

| Khái niệm | Câu hỏi nó trả lời |
| --- | --- |
| `Constable.describeConstable()` | “Giá trị này có thể cung cấp một mô tả hằng danh nghĩa không?” |
| `ConstantDesc` | “Mô tả hằng được biểu diễn bằng API như thế nào?” |
| `ConstantDesc.resolveConstantDesc(lookup)` | “Từ mô tả này, có thể phân giải được giá trị runtime nào trong ngữ cảnh `lookup`?” |

**Không phải mọi `ConstantDesc` đều là entry đang tồn tại sẵn trong constant pool của một class file cụ thể.** API giúp mô tả các hằng tải được (*loadable constants*) và hỗ trợ các công cụ dựng bytecode, mô tả bootstrap, phân giải biểu tượng. Nó không xác nhận rằng JVM đã lưu một bản sao của mọi descriptor vào class file hay intern mọi giá trị được phân giải.

### Ví dụ `ClassDesc`

`ClassDesc` là một dạng `ConstantDesc` mô tả lớp bằng descriptor JVM:

```java
ClassDesc desc = ClassDesc.of("java.lang.String");

System.out.println(desc.descriptorString());
// Ljava/lang/String;

System.out.println(
        desc.resolveConstantDesc(MethodHandles.lookup()) == String.class);
// true
```

Hai dòng trên là **đoạn mã minh họa đặt trong phương thức** có khai báo hoặc xử lý `ReflectiveOperationException` và đã import `java.lang.constant.ClassDesc`, `java.lang.invoke.MethodHandles`.

`"Ljava/lang/String;"` là **mô tả tên kiểu** (JVM field descriptor), không phải đối tượng `Class<?>`. Việc phân giải `ClassDesc` mới có thể trả về đối tượng `Class` tương ứng. Với tên lớp không hợp lệ hoặc lớp không truy cập được trong ngữ cảnh phù hợp, việc phân giải có thể thất bại.

## 5. Tại sao `String` triển khai **cả hai interface**?

Từ Java 12, `String` triển khai cả `Constable` và `ConstantDesc`. Với chính `String`, mô tả danh nghĩa của một giá trị có thể chính là **giá trị chuỗi đó**.

Nói cách khác:

1. `String.describeConstable()` trả về `Optional` có chứa **chính chuỗi hiện tại**.
2. Vì `String` cũng là một `ConstantDesc`, giá trị chuỗi ấy được dùng làm descriptor.
3. `String.resolveConstantDesc(lookup)` đơn giản trả về **chính đối tượng chuỗi**; nó không cần tra cứu class như `ClassDesc`.

Thử với một chuỗi được tạo mới:

```java
String fresh = new String("Java");

ConstantDesc desc = fresh.describeConstable().orElseThrow();
Object resolved = desc.resolveConstantDesc(MethodHandles.lookup());

System.out.println(desc == fresh);     // true
System.out.println(resolved == fresh); // true
System.out.println(resolved == "Java"); // false
```

Đoạn này cần import `java.lang.constant.ConstantDesc`, `java.lang.invoke.MethodHandles` và được đặt trong phương thức khai báo/giải quyết `ReflectiveOperationException`.

Phép so sánh cuối là điểm then chốt: **một `String` là `Constable`/`ConstantDesc` không có nghĩa nó đã được intern**. `resolveConstantDesc` của `String` trả về chính instance mà ta đang cầm, kể cả nó được tạo bởi `new String(...)`. Để lấy chuỗi canonical, phải dùng `intern()` riêng khi phù hợp.

Đừng nhầm “có thể mô tả thành hằng” với “chương trình hiện tại đã lưu một bản của chuỗi này trong class file”.

## 6. Ví dụ Java 17+ chạy độc lập

Tạo file `StringConstantDemo.java` với nội dung:

```java
import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

public class StringConstantDemo {
    public static void main(String[] args)
            throws ReflectiveOperationException {

        String literal = "Java";
        String folded = "Ja" + "va";
        String fresh = new String("Java");

        System.out.println("literal == folded   : " + (literal == folded));
        System.out.println("literal == fresh    : " + (literal == fresh));
        System.out.println("literal.equals(fresh): "
                + literal.equals(fresh));
        System.out.println("literal == interned : "
                + (literal == fresh.intern()));

        Constable constable = fresh;
        Optional<? extends ConstantDesc> nominal =
                constable.describeConstable();

        System.out.println("descriptor present  : "
                + nominal.isPresent());

        ConstantDesc descriptor = nominal.orElseThrow();
        System.out.println("descriptor == fresh : "
                + (descriptor == fresh));

        Object resolved = descriptor.resolveConstantDesc(
                MethodHandles.lookup());

        System.out.println("resolved == fresh   : "
                + (resolved == fresh));
        System.out.println("resolved == literal : "
                + (resolved == literal));

        ClassDesc classDesc = ClassDesc.of("java.lang.String");
        System.out.println("class descriptor    : "
                + classDesc.descriptorString());
        System.out.println("resolved String.class: "
                + (classDesc.resolveConstantDesc(MethodHandles.lookup())
                        == String.class));
    }
}
```

Biên dịch và chạy:

```bash
javac StringConstantDemo.java
java StringConstantDemo
```

Stdout:

```text
literal == folded   : true
literal == fresh    : false
literal.equals(fresh): true
literal == interned : true
descriptor present  : true
descriptor == fresh : true
resolved == fresh   : true
resolved == literal : false
class descriptor    : Ljava/lang/String;
resolved String.class: true
```

Giải thích từng phần:

- `literal` và `folded` là cùng chuỗi interned vì `"Ja" + "va"` là biểu thức hằng ở thời điểm biên dịch.
- `fresh` do `new String("Java")` tạo ra nên khác tham chiếu với literal, dù `equals` trả `true`.
- `fresh.intern()` trả về tham chiếu canonical và bằng `literal`.
- `describeConstable()` của `String` trả về mô tả hiện diện; mô tả ấy chính là `fresh`.
- `resolveConstantDesc(...)` trên một `String` descriptor cũng trả về `fresh`, **không tự động intern**.
- `ClassDesc` mô tả tên lớp; sau khi phân giải, nó tương ứng với đối tượng `String.class`.

`main` khai báo `throws ReflectiveOperationException` vì các lệnh phân giải descriptor được phép ném checked exception theo API. Với code ứng dụng cần xử lý thất bại phân giải, hãy dùng `try/catch` hay khai báo ngoại lệ ở tầng phù hợp.

## 7. Tự quan sát constant pool bằng `javap`

Sau khi biên dịch file ở mục 6, dùng:

```bash
javap -v StringConstantDemo
```

Đây là công cụ JDK để xem metadata và bytecode của `.class`. Trong kết quả thường có phần:

```text
Constant pool:
  ...
  ... = String             #...            // Java
  ... = Utf8                               Java
  ...
```

Đoạn trên là **sơ đồ rút gọn của định dạng đầu ra**, không phải transcript có số index cố định. Số thứ tự entry, vị trí và cách tối ưu có thể khác theo trình biên dịch và phiên bản JDK. Để nhìn thấy lệnh bytecode, có thể dùng:

```bash
javap -c StringConstantDemo
```

Một lệnh `ldc` dùng entry tương ứng để nạp hằng chuỗi từ constant pool. **Đừng tìm đối tượng `fresh` ở dưới dạng một entry `String` mới chỉ vì nó xuất hiện qua `new String("Java")`**: đối tượng mới được tạo ở runtime, dù literal `Java` có mặt trong class file.

Ngoài chuỗi, class file còn có các entry mô tả tên lớp, method, field và các biểu thức hằng khác. Constant pool **phục vụ liên kết và thực thi bytecode**, chứ không cung cấp API để ứng dụng Java duyệt tất cả đối tượng `String` đang được intern.

### Mối quan hệ với `CONSTANT_Dynamic`

Từ Java 11, JVM có entry `CONSTANT_Dynamic` để hỗ trợ hằng được phân giải động thông qua bootstrap method. Trong API `java.lang.constant`, `DynamicConstantDesc` là một dạng descriptor để mô tả loại hằng động đó theo hướng danh nghĩa.

Không cần dùng `DynamicConstantDesc` chỉ để thao tác chuỗi thường ngày, và **`CONSTANT_Dynamic` không đồng nghĩa với “mọi chuỗi được tạo lúc runtime”**. Chuỗi nối hay `new String(...)` thông thường không tự trở thành một constant dynamic.

## 8. Sơ đồ phân biệt ba tầng

```text
        Java source
  String s = "Java";
            |
            v
  [1] Class-file constant_pool
      CONSTANT_String --> CONSTANT_Utf8("Java")
            |
            | class loading / execution / ldc
            v
  [2] JVM String interning
      canonical String object: "Java"
            |
            | String API (có thể gọi trên cả String không intern)
            v
  [3] Constable / ConstantDesc
      string.describeConstable() -> Optional chứa string
      descriptor.resolveConstantDesc(lookup) -> chính string
```

Sơ đồ cho biết **mối liên hệ**, không khẳng định ba tầng dùng chung một vùng nhớ. Tầng 1 là cấu trúc thông tin hằng trong class file; tầng 2 là quy tắc canonical reference đối với literal và `intern`; tầng 3 là interface Java cho biểu diễn/phân giải hằng dưới dạng danh nghĩa.

Đối với `String`, một giá trị có thể được tạo mà **không xuất phát trực tiếp từ literal** nhưng vẫn là `Constable` và `ConstantDesc`, vì đây là tính chất của kiểu `String` chứ không phải nhãn chỉ dành cho những instance nằm trong pool.

## 9. Những lỗi phổ biến

1. **Đồng nhất constant pool với String pool.** Constant pool của `.class` chứa rất nhiều loại entry; String pool liên quan đến tham chiếu canonical của các đối tượng `String` runtime.
2. **Cho rằng mọi `String` đều được intern.** `new String(...)` tạo instance khác. `String` có tính bất biến không đồng nghĩa với tự động canonical hóa tất cả giá trị.
3. **Dùng `==` để kiểm tra nội dung hai chuỗi.** `==` chỉ so sánh tham chiếu; dùng `equals` hoặc `Objects.equals` khi có thể có `null`.
4. **Cho rằng `String implements Constable` nghĩa là `String` luôn là compile-time constant.** Sai: kiểu hỗ trợ API mô tả hằng, không cam kết cách biểu thức được tính hoặc nơi instance được lưu.
5. **Cho rằng `describeConstable()` bắt buộc luôn có giá trị với mọi loại `Constable`.** Kết quả là `Optional`; một số kiểu/instance không mô tả được và có thể trả rỗng. Riêng `String` trả descriptor của chính nó.
6. **Cho rằng `resolveConstantDesc` luôn tạo đối tượng mới.** Với `String`, nó trả lại chính đối tượng hiện có; với các kiểu descriptor khác, hành vi phân giải phụ thuộc ý nghĩa descriptor.
7. **Cho rằng `ClassDesc` là `Class<?>`.** `ClassDesc` mô tả kiểu bằng dạng danh nghĩa; `resolveConstantDesc` mới tạo/thu được `Class<?>` trong ngữ cảnh phù hợp.
8. **Cho rằng `CONSTANT_Utf8` luôn là một chuỗi literal Java.** Nó cũng lưu tên, descriptor và metadata ký hiệu; chỉ `CONSTANT_String` dùng nội dung liên quan để biểu diễn hằng `String`.
9. **Cố suy ra vị trí vật lý cụ thể của bộ nhớ từ từ “pool”.** Cấu trúc đặc tả và cách cài đặt JVM không phải một điều; hãy phân biệt bảo đảm ngôn ngữ với chi tiết của một JVM phiên bản cụ thể.
10. **Intern mọi chuỗi để tăng tốc một cách máy móc.** `intern()` có chi phí quản lý và tác động bộ nhớ; chỉ dùng khi có lý do đo đạc hoặc thiết kế rõ ràng.

## 10. Tự luyện tập

**Câu 1.** Với `String a = "x"; String b = "x";`, `a == b` là gì?

**Đáp án:** `true` vì hai literal có cùng nội dung dùng chung tham chiếu `String` canonical.

**Câu 2.** Với `String a = "x"; String b = new String("x");`, `a.equals(b)` và `a == b`?

**Đáp án:** `equals` là `true`, `==` là `false`. `b.intern() == a` là `true`.

**Câu 3.** `fresh.describeConstable()` có đồng nghĩa với `fresh.intern()` không?

**Đáp án:** Không. Với `String`, `describeConstable()` cung cấp descriptor là chính `fresh`; `intern()` tìm/trả về đối tượng chuỗi canonical theo nội dung.

**Câu 4.** `ClassDesc.of("java.lang.String")` có phải là `String.class` không?

**Đáp án:** Không. `ClassDesc` là một descriptor danh nghĩa. Khi phân giải thành công bằng `resolveConstantDesc(lookup)`, nó có thể trả về đối tượng `Class` tương ứng với `String.class`.

**Câu 5.** Có thể dùng `Constable` để kiểm tra một `String` được khai báo với `final` hay được đưa trực tiếp vào `.class` không?

**Đáp án:** Không. `Constable` mô tả khả năng của kiểu/giá trị trong API constant descriptor; nó không cung cấp nguồn gốc literal, thuộc tính `final` của biến hay nơi đối tượng đang nằm trong bộ nhớ.

**Câu 6.** Vì sao dùng `javap -v` và `==` trả lời hai câu hỏi khác nhau?

**Đáp án:** `javap -v` kiểm tra **entry và bytecode của class file**; `==` so sánh **tham chiếu object ở runtime**. Đây là hai tầng quan sát khác nhau.

## 11. Ghi nhớ và tài liệu chính thức

- **Class-file constant pool**: bảng hằng và tham chiếu ký hiệu phục vụ bytecode, gồm `CONSTANT_String` và nhiều loại entry khác.
- **String pool / `intern()`**: bảo đảm tham chiếu canonical cho cùng nội dung chuỗi theo hợp đồng của Java.
- **`Constable`**: một instance có thể cung cấp biểu diễn danh nghĩa bằng `describeConstable()`.
- **`ConstantDesc`**: mô tả giá trị hằng và hỗ trợ `resolveConstantDesc(lookup)`.
- **`String`**: vừa là `Constable` vừa là `ConstantDesc`, nhưng **không phải mọi instance `String` đều được intern**.

Tài liệu:
- [`String` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html)
- [`Constable` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/constant/Constable.html)
- [`ConstantDesc` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/constant/ConstantDesc.html)
- [`ClassDesc` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/constant/ClassDesc.html)
- [JVM Specification `4.4 — The Constant Pool](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.4)
- [JVM Specification `5.1 — The Run-Time Constant Pool](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.1)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-internals-006">Phép nối qua StringConcatFactory và invokedynamic</a>

<details>
<summary>Xem chi tiết</summary>

# Phép nối chuỗi qua `StringConcatFactory` và `invokedynamic`

## 1. Bức tranh tổng quan

Java cho phép nối chuỗi rất tự nhiên bằng toán tử `+`:

```java
String name = "Java";
int version = 17;
String description = "Language: " + name + ", version=" + version;
```

Đây là đoạn mã minh họa trong thân phương thức. Giá trị cuối cùng là `Language: Java, version=17`. Cú pháp `+` là một phần **ngôn ngữ Java**, còn cách trình biên dịch tạo bytecode để thực thi phép nối là một quyết định triển khai.

Từ **Java 9**, theo hướng thay đổi của **JEP 280 (Indify String Concatenation)**, trình biên dịch `javac` thường biểu diễn các phép nối `String` động bằng lệnh bytecode **`invokedynamic`**. Khi liên kết lời gọi đó, JVM sử dụng bootstrap method thuộc lớp **`java.lang.invoke.StringConcatFactory`** để xây dựng cơ chế nối phù hợp với kiểu đối số và hằng số của phép nối.

Các khái niệm cần tách biệt:

- `String` vẫn **bất biến (immutable)**: biểu thức nối tạo giá trị chuỗi kết quả theo ngữ nghĩa ngôn ngữ.
- `+` vẫn là cú pháp Java; lập trình viên không cần gọi `StringConcatFactory`.
- `invokedynamic` là **lệnh bytecode** liên kết lời gọi thông qua một bootstrap method, không phải câu lệnh Java mới hay việc tìm tên hàm bằng reflection mỗi lần chạy.
- `StringConcatFactory` là **cơ sở hạ tầng runtime** cho việc xây dựng lời gọi nối chuỗi; không phải định dạng dữ liệu hay kho lưu trữ chuỗi.

Với trường hợp chỉ gồm các hằng chuỗi, `javac` có thể gộp giá trị ngay khi biên dịch và không cần `invokedynamic` cho chính phép nối đó. Vì vậy cần nhìn bytecode của **trường hợp cụ thể**, thay vì khẳng định mọi dấu `+` đều trở thành `invokedynamic`.

## 2. Trước Java 9: phép nối thường thành `StringBuilder`

Một ví dụ đơn giản:

```java
public class Contrast {
    public static String render(String name, int n) {
        return "Name=" + name + ", count=" + n;
    }
}
```

Khi biên dịch mã này bằng `javac --release 8`, bytecode có thể chứa chuỗi thao tác tương đương:

```text
new           java/lang/StringBuilder
invokespecial java/lang/StringBuilder.<init>
invokevirtual java/lang/StringBuilder.append
invokevirtual java/lang/StringBuilder.append
invokevirtual java/lang/StringBuilder.append
invokevirtual java/lang/StringBuilder.append
invokevirtual java/lang/StringBuilder.toString
```

Trong kết quả thực tế, bytecode còn chứa các lệnh nạp đối số và có thể chọn overload `append(String)`, `append(int)`. Đoạn trên là **lược trích những thao tác chính**, không phải toàn bộ bản disassembly nguyên văn.

Cách cũ giúp nối hiệu quả hơn việc tạo một chuỗi riêng cho mỗi cặp toán hạng. Nhưng nó cũng ràng buộc chiến lược nối vào bytecode do compiler phát ra. Muốn thay đổi toàn bộ chiến lược, JVM/JIT phải nhận diện và tối ưu mẫu bytecode `StringBuilder` tương ứng.

## 3. Từ Java 9: `invokedynamic` tách ý nghĩa nối khỏi chiến lược thực thi

Cũng với lớp `Contrast` trên, khi biên dịch `javac --release 17`, `javap -c` cho thấy một lệnh dạng:

```text
invokedynamic #... // makeConcatWithConstants:(Ljava/lang/String;I)Ljava/lang/String;
```

Đây là **trích đoạn mô tả dạng output**; số index constant pool phụ thuộc file class được biên dịch. Tham số `String` và `int` trong chữ ký cho biết lời gọi xử lý các giá trị động loại đó; các phần như `Name=` và `, count=` có thể được đưa vào recipe làm hằng. Khi quan sát lệnh này, không có nghĩa Java đã bỏ bất biến của `String` hay chuyển thành nối chuỗi ở thời điểm biên dịch: nó chỉ thay đổi **cách gọi logic nối ở runtime**.

Luồng khái quát:

```text
Mã Java: "Name=" + name + ", count=" + n
          |
          v
javac (target Java 9+)
          |
          v
Class file: invokedynamic + BootstrapMethods + recipe/metadata
          |
          v
JVM liên kết invokedynamic với StringConcatFactory
          |
          v
CallSite / MethodHandle có kiểu phù hợp
          |
          v
Runtime thực thi phép nối với đối số hiện tại
          |
          v
String kết quả
```

**JVM có thể tối ưu và JIT có thể inline** lời gọi được liên kết. Từ một instruction `invokedynamic` trong class file, không thể kết luận chính xác mỗi lần chạy tạo bao nhiêu đối tượng, cấp phát bao nhiêu mảng byte hay chạy bao nhiêu lệnh máy: đó là chuyện tối ưu runtime của JVM cụ thể.

## 4. Bootstrap method và cơ chế liên kết call site

`invokedynamic` có entry trong constant pool, mô tả tên lời gọi, kiểu hàm (tham số và kiểu trả về) cùng thông tin về **bootstrap method**. Khi JVM cần liên kết instruction, bootstrap method được gọi với ngữ cảnh liên kết và các hằng static liên quan, rồi trả về một `CallSite` có target phù hợp.

Trong tình huống nối chuỗi được `javac` tạo ra, bootstrap phổ biến là:

- `StringConcatFactory.makeConcat(...)`: cung cấp dạng nối dựa trên các đối số động.
- `StringConcatFactory.makeConcatWithConstants(...)`: nhận thêm một **recipe** và các hằng bootstrap, nhờ đó có thể trộn văn bản cố định với các giá trị động.

Các phương thức này thuộc API `java.lang.invoke`. `javac` thường chọn `makeConcatWithConstants` trong các bản JDK hiện đại; chi tiết cụ thể có thể thay đổi theo phiên bản compiler, lựa chọn target, ngữ cảnh mã và các tối ưu.

Ở dạng `makeConcatWithConstants`, **recipe** có thể chứa hai marker điều khiển:

| Marker trong recipe | Ý nghĩa |
| --- | --- |
| `\u0001` | Đặt một giá trị từ các đối số động của lời gọi nối |
| `\u0002` | Đặt một hằng từ các static bootstrap constants |
| Ký tự khác | Đoạn văn bản cố định được đưa trực tiếp vào kết quả |

Ví dụ, recipe được `javap -v` in gần như `Xin chào \u0001 \u0001 / \u0001` là cấu trúc ghép chuỗi với ba giá trị động ở ba vị trí. Marker `\u0001` trong disassembly là ký hiệu cho **một ký tự điều khiển**, không phải sáu ký tự văn bản cần xuất hiện trong output người dùng. Không nên tự xây dựng recipe bằng cách nối các đoạn tùy ý rồi kỳ vọng mọi implementation có chiến lược giống nhau.

**Liên kết call site** không đồng nghĩa “phân tích lại biểu thức `+` từ mã nguồn ở mỗi lần thực thi”. JVM liên kết instruction với target; các lần thực thi sau gọi target đó theo cơ chế của JVM, còn compiler runtime có thể tối ưu thêm. Thời điểm liên kết thường là khi site được sử dụng lần đầu, song mã nên dựa vào **ngữ nghĩa Java**, không dựa vào một lịch liên kết tuyệt đối.

## 5. Ví dụ chạy được với Java 17+

Tạo file `ConcatIndyDemo.java`:

```java
public class ConcatIndyDemo {
    static String mark(String name, String value) {
        System.out.println("eval " + name);
        return value;
    }

    public static void main(String[] args) {
        String who = "Java";
        int version = 17;
        Object absent = null;
        String message = "Xin chào " + who + " " + version + " / " + absent;
        System.out.println("message = " + message);

        String order = mark("left", "A") + mark("right", "B") + 7;
        System.out.println("order = " + order);

        String folded = "foo" + "bar";
        System.out.println("folded = " + folded);

        String explicit = new StringBuilder()
                .append(who)
                .append("!")
                .toString();
        System.out.println("builder = " + explicit);
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 ConcatIndyDemo.java
java ConcatIndyDemo
```

Kết quả chuẩn đã được đối chiếu khi chạy chương trình:

```text
message = Xin chào Java 17 / null
eval left
eval right
order = AB7
folded = foobar
builder = Java!
```

Giải thích các chi tiết đáng chú ý:

1. Giá trị `null` trong biểu thức nối chuỗi được chuyển thành văn bản `"null"` theo ngữ nghĩa string conversion của Java. Không thể đồng nhất việc nối bằng `+` với gọi trực tiếp `someString.concat(null)`, vì phương thức `concat` nhận đối số `null` có thể gây `NullPointerException`.
2. Hai lời gọi `mark` được đánh giá **từ trái sang phải**, mỗi lời gọi một lần. Đổi chiến lược bytecode sang `invokedynamic` không được phép đảo hiệu ứng phụ của toán hạng. Vì vậy console in `eval left` rồi `eval right`.
3. `mark("left", "A") + mark("right", "B") + 7` cho `AB7` vì khi phép cộng đã ở chế độ nối chuỗi, số `7` được chuyển thành ký tự theo quy tắc nối. Literal số `7` có thể được compiler đưa vào recipe thay vì thành đối số động riêng.
4. `"foo" + "bar"` là **biểu thức hằng**; `javac` gộp thành `"foobar"` ngay trong class file. Phần in `"folded = " + folded` trong ví dụ vẫn có thể là một site `invokedynamic` riêng vì `folded` là local variable thường, không phải một hằng compile-time variable được khai báo `final`.
5. Đoạn `new StringBuilder(...).append(...).toString()` là cách xây dựng chuỗi tường minh. Nó vẫn hợp lệ ở Java mới; compiler không buộc lập trình viên viết nó để nối một biểu thức `+` thông thường.

## 6. Dùng `javap` để tự nhìn chứng cứ bytecode

Sau khi biên dịch, chạy:

```bash
javap -c -v ConcatIndyDemo
```

Trong kết quả, tập trung vào ba vị trí:

**A. Lệnh trong phần `Code`:** Một lệnh có dạng:

```text
invokedynamic #... // makeConcatWithConstants:(Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;
```

Đây là phép nối `message` của ví dụ, có các đối số động dạng `String`, `int`, `String` (Javac có thể chuyển `Object absent` thành `String` trước khi gọi site). Các chuỗi cố định `Xin chào` và dấu phân cách có thể nằm trong recipe.

**B. Phần `BootstrapMethods`:** Trong `javap -v`, ta có thể thấy bootstrap handle trỏ đến:

```text
REF_invokeStatic java/lang/invoke/StringConcatFactory.makeConcatWithConstants
```

Cùng với các method arguments dạng recipe, ví dụ `Xin chào \u0001 \u0001 / \u0001`. Thứ tự và constant-pool indices được JVM/class file xác định và có thể khác nếu thay mã hoặc compiler.

**C. Biểu thức hằng:** Đối với `String folded = "foo" + "bar";`, `javap` có thể hiển thị:

```text
ldc #... // String foobar
```

`ldc` nạp một hằng từ constant pool. Đây là bằng chứng phép nối *hai chuỗi hằng* được giải quyết tại compile time; không có site nối riêng cho biểu thức đó. Nếu nhìn thấy `invokedynamic` ở dòng in `folded`, đó là phép nối khác: `"folded = " + folded`.

Để so sánh rõ cơ chế cũ/mới, tạo file `Contrast.java` như ở phần 2 rồi biên dịch thành **hai thư mục output khác nhau**:

```bash
javac --release 8 -d out8 Contrast.java
javac --release 17 -d out17 Contrast.java
javap -classpath out8 -c Contrast
javap -classpath out17 -c Contrast
```

Tạo sẵn các thư mục `out8` và `out17` nếu hệ thống biên dịch yêu cầu. Với compiler `javac` đã kiểm thử, bản target 8 dùng `StringBuilder.append`, còn target 17 dùng `invokedynamic`. Lệnh `--release 8` biên dịch một class tương thích API/bytecode Java 8; nó không bắt buộc bạn thực sự phải cài JDK 8 để quan sát output, miễn JDK hiện tại còn hỗ trợ release đó.

**Quan trọng:** `javap` cho thấy chiến lược do trình biên dịch phát ra ở class file. Nó **không phải phép đo thời gian thực thi** và không hiển thị toàn bộ mã máy mà JIT chọn sau khi chương trình chạy nóng.

## 7. Ngữ nghĩa biểu thức `+` không đổi

Dù bytecode được tạo theo kiểu nào, chương trình Java vẫn phải tuân thủ ngữ nghĩa của toán tử `+`:

### Phân biệt cộng số và nối chuỗi

```java
String a = 1 + 2 + "x";    // "3x"
String b = "x" + 1 + 2;    // "x12"
String c = "x" + (1 + 2);  // "x3"
```

Toán tử `+` kết hợp từ trái sang phải. Trước khi xuất hiện toán hạng `String`, phép `1 + 2` là cộng số. Khi đã là nối chuỗi, các giá trị tiếp theo được chuyển thành văn bản. Dùng dấu ngoặc để tránh lỗi logic thay vì trông cậy vào chi tiết tối ưu bytecode.

### Thứ tự và ngoại lệ của toán hạng

```java
String s = first() + second() + third();
```

Ba lời gọi (nếu trả về kiểu phù hợp) phải được đánh giá theo thứ tự Java quy định; các tác dụng phụ và ngoại lệ quan sát được không được phép bị thay đổi chỉ vì compiler dùng `invokedynamic`. Không thể “tối ưu” bằng cách bỏ một lời gọi có tác dụng phụ.

### Hằng gộp trước, runtime chỉ xử lý phần cần thiết

```java
String compileTime = "ab" + "cd"; // "abcd"
String runtime = prefix + "cd";   // phụ thuộc prefix
```

Với biểu thức hoàn toàn là hằng, compiler có thể gộp chuỗi. Với `prefix` là biến có giá trị runtime, cần thực hiện phép nối khi chạy (dưới dạng `invokedynamic` hoặc chiến lược khác của compiler).

### `null` và chuyển kiểu thành String

Khi nối một đối tượng (kể cả `null`), Java áp dụng string conversion, về mặt kết quả thông thường tương đương với `String.valueOf(value)`. Với object không null, có thể sử dụng `toString()` của object; phương thức đó có thể chứa logic hoặc tác dụng phụ. Kết quả và thứ tự đánh giá vẫn bị ràng buộc bởi ngữ nghĩa Java; không nên tự đoán rằng mọi object đã bị chuyển thành `String` ngay tại thời điểm biên dịch.

## 8. Hiệu năng: khi nào dùng `+`, khi nào dùng `StringBuilder`?

**Với một biểu thức nối chuỗi rõ ràng**, cách viết:

```java
String line = "user=" + user + ", count=" + count;
```

thường là lựa chọn sáng sủa. Trên Java hiện đại, `javac`/JVM có khả năng xây dựng và tối ưu toàn bộ phép nối, nên **không cần tự thay mọi dấu `+` bằng `StringBuilder`** vì tin rằng nó chắc chắn nhanh hơn. Sự tối ưu cụ thể tùy compiler, JVM, dữ liệu đầu vào và mức độ code chạy nóng.

**Với việc tích lũy chuỗi qua nhiều vòng lặp**, mỗi phép `+=` trên `String` có thể tạo một chuỗi trung gian mới, làm sao chép lặp đi lặp lại và có chi phí lớn khi dữ liệu tăng. Ví dụ cần xem xét:

```java
String output = "";
for (String item : items) {
    output += item;
}
```

Trong trường hợp tập hợp nhiều phần tử, một giải pháp rõ mục đích hơn thường là:

```java
StringBuilder builder = new StringBuilder();
for (String item : items) {
    builder.append(item);
}
String output = builder.toString();
```

Đây là **hai đoạn mã minh họa trong thân phương thức**, giả sử có biến `items` kiểu `Iterable<String>` hoặc một collection thích hợp. Không thể tuyên bố mọi vòng lặp `+=` đều chậm cùng một mức vì JVM có thể tối ưu trong một số trường hợp. Tuy nhiên, `StringBuilder` biểu đạt trực tiếp việc tích lũy nội dung và tránh phụ thuộc vào việc compiler/JIT có nhận ra cơ hội tối ưu toàn chuỗi qua nhiều lần lặp hay không.

Nếu có nhu cầu benchmark, hãy đo trong môi trường đại diện bằng công cụ microbenchmark như JMH; tránh lấy một lần gọi `System.nanoTime()` rồi suy luận toàn bộ hiệu năng. JIT warmup, dead-code elimination, escape analysis, GC và định dạng kết quả đều có thể ảnh hưởng.

**Không suy luận rằng `invokedynamic` luôn không cấp phát**. Các lớp runtime như `StringConcatFactory` cho phép JVM chọn chiến lược hiệu quả, nhưng chi tiết cấp phát/compact-string/JIT không phải cam kết cố định của cú pháp `+`.

## 9. Sai lầm thường gặp

1. **“Mọi phép cộng chuỗi đều gọi `StringBuilder`”:** nhận định này không còn đúng mặc định với `javac` Java 9+. Hãy kiểm tra bytecode khi cần.
2. **“Mọi dấu `+` đều thành `invokedynamic`”:** biểu thức hằng có thể được gộp sẵn; toán tử `+` số học là phép cộng số; compiler có thể dùng cơ chế khác.
3. **“`invokedynamic` nghĩa là gọi reflection mỗi lần”:** đây là lệnh bytecode có liên kết `CallSite`, không phải dò method theo tên trong mã nguồn mỗi lần nối.
4. **“`StringConcatFactory` làm `String` mutable”:** chuỗi kết quả vẫn có ngữ nghĩa `String` bất biến.
5. **“Thay `+` bằng `StringBuilder` chắc chắn nhanh hơn”:** phụ thuộc ngữ cảnh, compiler, JVM và workload; một biểu thức nối đơn giản thường nên ưu tiên dễ đọc.
6. **“`StringBuilder` không còn cần thiết”:** nó vẫn phù hợp cho xây dựng chuỗi gia tăng, đặc biệt trong vòng lặp.
7. **“Bytecode `invokedynamic` chứng minh runtime không cấp phát”:** bytecode và mã máy tối ưu là hai tầng khác nhau.
8. **“Có thể đảo thứ tự tính các toán hạng để tối ưu”:** Java yêu cầu giữ đúng thứ tự đánh giá, hiệu ứng phụ và ngoại lệ.
9. **“`null` trong `+` giống gọi `String.concat(null)`”:** phép nối có string conversion còn `concat` nhận tham số `null` có thể ném exception.
10. **“Đọc một output `javap` là đủ để khẳng định hành vi mọi JDK”:** output chịu ảnh hưởng version compiler, release target và hình dạng biểu thức. Hãy nêu rõ môi trường kiểm tra.

## 10. Quy trình tự kiểm tra một phép nối chuỗi

1. Xác định ý nghĩa biểu thức Java: đây là cộng số hay nối chuỗi, có `null`, phương thức có tác dụng phụ hay các phần hằng nào?
2. Biên dịch với release target bạn thực sự quan tâm, ví dụ `javac --release 17`.
3. Dùng `javap -c -v` để tìm `invokedynamic`, entry `BootstrapMethods`, bootstrap handle và recipe. Nếu thấy `ldc` với chuỗi đã gộp, đó có thể là constant folding.
4. Thực thi chương trình và kiểm tra **nội dung kết quả, thứ tự hiệu ứng phụ và cách xử lý `null`**; đừng chỉ nhìn lệnh bytecode.
5. Nếu mục tiêu là tối ưu hiệu năng, đánh giá công việc thực tế và benchmark nghiêm túc trước khi thay cấu trúc mã.

**Ghi nhớ:** `+` là cú pháp nối chuỗi ở cấp ngôn ngữ. Từ Java 9, `javac` thường chuyển phép nối động thành `invokedynamic`; JVM dùng `StringConcatFactory` làm bootstrap để liên kết một lời gọi có thể tối ưu. Cơ chế nội bộ thay đổi nhưng **ngữ nghĩa chuỗi bất biến, thứ tự đánh giá và kết quả phép nối phải được giữ nguyên**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-internals-007">Chi tiết substring, intern và tối ưu có thể khác giữa phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Chi tiết substring, intern và tối ưu String có thể khác giữa các phiên bản JDK

## 1. Phân biệt hợp đồng API và chi tiết cài đặt

`String` là một lớp cốt lõi của Java: bất biến (immutable), biểu diễn một dãy đơn vị mã UTF-16 và cung cấp các thao tác như `substring()`, `equals()` và `intern()`. Tuy vậy, cách JVM bố trí bộ nhớ, tái sử dụng vùng dữ liệu ký tự, lưu bảng chuỗi intern hay loại bỏ bản sao trùng lặp đã thay đổi nhiều lần qua các phiên bản JDK.

Khi học hoặc tối ưu hóa `String`, phải tách **hai tầng**:

- **Hợp đồng ngôn ngữ/API:** `substring(begin, end)` lấy đúng đoạn ký tự; `equals` so sánh nội dung; `intern` trả về đại diện chuẩn (canonical) của nội dung trong pool. Chương trình phải dựa vào các quy tắc này.
- **Cơ chế cài đặt:** `String` dùng `char[]` hay `byte[]`, một substring có chia sẻ backing array không, pool lưu ở vùng bộ nhớ nào, JIT/GC có loại bỏ bản sao hay không. Đây là các lựa chọn của phiên bản JDK, nhà cung cấp JVM và cấu hình runtime; không nên coi là API bất biến.

Một tối ưu hợp lý trên JDK cũ có thể đã lỗi thời trên JDK mới; ngược lại, kết quả đúng của chương trình không được phụ thuộc vào đoán định về backing array, kích thước đối tượng hoặc số lần cấp phát nội bộ.

| Vấn đề | Bảo đảm ổn định để viết chương trình | Chi tiết phụ thuộc JDK/JVM |
| --- | --- | --- |
| `substring` | Trả về nội dung từ chỉ số đầu (bao gồm) tới cuối (không bao gồm) | Có copy dữ liệu hay chia sẻ vùng lưu trữ; chi phí cấp phát |
| `String.intern` | Với chuỗi bằng nhau theo `equals`, kết quả `intern()` là cùng tham chiếu chuẩn | Cấu trúc bảng pool, vị trí lưu trữ, GC, thời gian tìm kiếm |
| `String` immutable | Nội dung chuỗi không thể bị sửa sau khi tạo | Các đối tượng có dùng chung mảng dữ liệu nội bộ không |
| `length` và `charAt` | Hoạt động theo đơn vị mã UTF-16 `char` | Chuỗi có lưu bằng mảng byte compact hay mảng char |
| Tối ưu String | Nội dung và hành vi API không đổi | Deduplication, compact storage, JIT, GC, chiến lược nối chuỗi |

## 2. substring: cùng một kết quả, nhiều chiến lược lưu trữ

Hai overload thường dùng:

```java
String substring(int beginIndex)
String substring(int beginIndex, int endIndex)
```

`beginIndex` được tính vào kết quả; `endIndex` **không** được tính vào. Các chỉ số được đo bằng đơn vị `char` UTF-16, không phải Unicode code point.

```java
String source = "abcdef";
String part = source.substring(2, 5);

System.out.println(part);                    // cde
System.out.println(part.length());           // 3
System.out.println(part.equals("cde"));      // true
```

Phạm vi bất hợp lệ như `beginIndex < 0`, `endIndex > source.length()` hoặc `beginIndex > endIndex` gây `IndexOutOfBoundsException`. API quy định **nội dung đoạn chuỗi**; nó không hứa mỗi lần gọi phải tạo một đối tượng `String` và mảng backing độc lập.

### Lịch sử: chia sẻ mảng ở JDK cũ

Trong các cài đặt OpenJDK/HotSpot đời cũ **trước JDK 7u6**, `String` từng sử dụng một mảng `char[]` cùng thông tin `offset`/`count`. Một `substring` nhỏ có thể tạo đối tượng String mới nhưng **chia sẻ cùng mảng ký tự lớn** với chuỗi gốc. Lợi thế là tránh copy dữ liệu; đánh đổi là nguy cơ giữ sống mảng lớn chỉ vì còn một substring ngắn.

Ví dụ tình huống lịch sử:

```text
Chuỗi gốc rất dài:     [........ hàng triệu ký tự ........]
                                |
Substring chỉ lấy:             "id"

Cài đặt chia sẻ mảng đời cũ:
" id " nhỏ vẫn tham chiếu backing char[] lớn.
Khi chuỗi gốc không còn dùng, backing array lớn có thể vẫn
chưa được giải phóng vì substring còn sống.
```

Điểm dễ nhầm: đây thường gọi là **khả năng giữ bộ nhớ ngoài ý muốn (memory retention)**, không có nghĩa `String` bị thay đổi nội dung hay JVM vi phạm tính immutable.

Từ **JDK 7u6**, cách cài đặt phổ biến đã thay đổi: substring có thể tạo vùng lưu trữ ký tự phù hợp với đoạn cần giữ, thay cho việc luôn chia sẻ cả mảng lớn theo `offset`/`count`. Vì thế, mẹo cũ `new String(large.substring(...))` để buộc copy/thoát tham chiếu tới backing array **không nên được áp dụng máy móc trên JDK hiện đại**. Constructor `String(String original)` hiện không bảo đảm một backing copy mới tách biệt; String bất biến cho phép chia sẻ dữ liệu khi có lợi.

**Kết luận về thiết kế:** dùng `substring` để lấy đoạn theo nhu cầu. Khi thực sự lo về memory retention hoặc throughput, kiểm tra **đúng JDK/JVM đang chạy** bằng heap profiler và benchmark thay vì áp dụng quy tắc “substring luôn O(1)” hay “substring luôn copy” cho mọi phiên bản. Với tác vụ cắt rất nhiều chuỗi, chi phí cấp phát vẫn cần được đo.

### UTF-16 và nguy cơ cắt giữa surrogate pair

Cho dù cài đặt bên trong dùng compact bytes, API `substring` vẫn dùng **chỉ số UTF-16**. Ví dụ emoji `🚀` là một Unicode code point nhưng cần hai `char`:

```java
String value = "A🚀B";
System.out.println(value.length());                    // 4
System.out.println(value.codePointCount(0, value.length())); // 3
System.out.println(value.substring(1, 3));             // 🚀
```

Cắt `substring(1, 2)` sẽ chỉ lấy một nửa cặp surrogate; giá trị Java String vẫn có thể chứa đơn vị mã lẻ, nhưng có thể không hiển thị như ký tự mong muốn. Khi xử lý theo code point, tính chỉ số bằng `offsetByCodePoints` hoặc xử lý bằng `codePoints()`; đừng nhầm một `char` là một ký tự người dùng nhìn thấy.

## 3. intern(): hợp đồng ổn định, nơi giữ pool thay đổi

`String.intern()` trả một đối tượng canonical theo nội dung; với hai String `a.equals(b)` là `true`, `a.intern() == b.intern()` cũng là `true`. Literal và biểu thức String hằng lúc biên dịch được intern. Cách JVM lưu và quản lý các đối tượng canonical có thể đổi mà vẫn phải giữ hợp đồng đó.

```java
String literal = "java";
String created = new String("java");
System.out.println(created == literal);          // false
System.out.println(created.equals(literal));      // true
System.out.println(created.intern() == literal);  // true
```

Trong lịch sử **HotSpot**, vùng lưu String interned từng gắn với **PermGen** trên JDK 6 trở về trước. **Từ JDK 7**, các đối tượng String được intern nằm trên **Java heap thông thường**; JDK 8 loại bỏ PermGen và dùng **Metaspace** cho metadata, chứ không chuyển các `String` ứng dụng vào Metaspace. Các bản JDK/GC khác nhau có thể đổi cấu trúc bảng tra pool, thời gian sống hoặc chính sách thu gom.

Điều đó **không** có nghĩa intern miễn phí hay các đối tượng được intern sẽ sống mãi. Với tập giá trị ít lặp, như UUID, session ID hay URL ngẫu nhiên, đưa mọi đầu vào vào pool có thể làm tăng chi phí quản lý. Hãy kiểm tra độ trùng, vòng đời dữ liệu và profile heap trước khi dùng làm tối ưu.

Bài **Constant pool và vai trò của Constable/ConstantDesc** giải thích chi tiết khác biệt giữa String pool, constant pool trong class file và API mô tả hằng. Phần này tập trung vào **sự dịch chuyển cài đặt và hệ quả khi nâng JDK**.

## 4. String layout thay đổi qua các đời JDK

| Giai đoạn triển khai OpenJDK/HotSpot | Cách lưu nội dung điển hình | Hệ quả cần kiểm tra khi nâng cấp |
| --- | --- | --- |
| Nhiều bản trước Java 9 | `char[]` theo đơn vị UTF-16 | Không mặc định áp dụng footprint của JDK hiện đại |
| Java 9+ có Compact Strings (JEP 254) | `byte[]` với dạng LATIN1 hoặc UTF16 | Chuỗi có toàn code unit thuộc U+0000–U+00FF có thể tiết kiệm byte dữ liệu |

Ví dụ `"Xin chào"` gồm ký tự `à` thuộc Latin-1, nên **có thể** đủ điều kiện compact dù là tiếng Việt. Một ký tự ngoài U+00FF làm cả String dùng dạng UTF16 trong chiến lược này. Tuy nhiên `length()` vẫn đếm UTF-16 code units, còn `getBytes(StandardCharsets.UTF_8)` vẫn cho byte UTF-8 của dữ liệu trao đổi.

Đây là chuyện triển khai, không phải yêu cầu Java SE cho mọi JVM. Không truy cập trường `value`/`coder` bằng reflection để suy đoán tính đúng của ứng dụng. Nếu cần phân tích cụ thể cách chọn coder, tác động của `-XX:-CompactStrings` và chi phí bộ nhớ, xem bài **Compact Strings** và bài **Chi phí đối tượng, mảng lưu dữ liệu và chuỗi tạm**.

## 5. String Deduplication: chia sẻ dữ liệu, không trộn đối tượng

**JEP 192 — String Deduplication in G1**, có từ JDK 8u20 ở HotSpot với GC G1, nhằm giảm bộ nhớ khi nhiều đối tượng String khác nhau chứa cùng nội dung. Dưới điều kiện thích hợp, GC có thể giúp các String **chia sẻ cùng vùng dữ liệu backing** mà vẫn giữ **hai đối tượng `String` riêng**.

```text
Đối tượng String A ----\
                        > dữ liệu "AB" được chia sẻ (khi GC dedup)
Đối tượng String B ----/

A và B vẫn là hai tham chiếu đối tượng khác nhau.
```

Đây **không** phải `intern()`:

| Cơ chế | Chuẩn hóa chính đối tượng String? | Mục đích chính | Điều Java code quan sát được |
| --- | --- | --- | --- |
| `intern()` | Có: trả về một đối tượng canonical cho nội dung | Chuẩn hóa identity khi gọi rõ ràng | Hai kết quả `intern` của chuỗi bằng nhau so sánh `==` ra `true` |
| String Deduplication của GC | Không: có thể chia sẻ **mảng nội dung** nhưng giữ các String riêng | Giảm số vùng dữ liệu trùng lặp | `equals` vẫn đúng; `==` giữa hai đối tượng riêng vẫn `false` |

Trong HotSpot/JDK có hỗ trợ, có thể thử tùy chọn sau tại **môi trường đo đạc**:

```bash
java -XX:+UseG1GC -XX:+UseStringDeduplication YourApp
```

Hiệu quả phụ thuộc số lượng nội dung lặp lại, vòng đời đối tượng, GC, áp lực bộ nhớ và thời gian xử lý. Không có bảo đảm rằng một String cụ thể chắc chắn được dedup ngay hoặc rằng ứng dụng luôn nhanh hơn khi bật tùy chọn. Một số cờ và GC được hỗ trợ có thể thay đổi theo JDK; hãy kiểm tra tài liệu `java`/`-XX` của phiên bản runtime đích trước khi dùng trong môi trường thật.

**Không dùng `a == b` để đo deduplication.** Trường hợp hai `String` được tạo riêng, identity vẫn khác nhau kể cả khi GC đã chia sẻ backing data. Kiểm tra bằng chỉ số heap/GC hoặc profiler, không dựa vào Java reference equality.

## 6. Những tối ưu khác cũng phụ thuộc phiên bản JVM

Ngoài `substring`, `intern`, compact storage và deduplication, hành vi cấp phát/hiệu năng còn chịu ảnh hưởng của:

- **JIT và escape analysis:** trình biên dịch runtime có thể thay đổi, tối ưu hoặc loại bớt các cấp phát không quan sát được, nhưng không bảo đảm mọi lời gọi `new String` hay phép nối đều được loại bỏ.
- **Ghép chuỗi bằng `+`:** bộ biên dịch/JVM có thể dùng chiến lược khác nhau; từ Java 9, JEP 280 đưa cơ chế dựa trên `invokedynamic` cho bytecode nối chuỗi của `javac`. Không suy ra mã nguồn `a + b` luôn có cùng số `StringBuilder` hay số cấp phát trên mọi JDK.
- **GC và kích thước heap:** thuật toán quản lý/di chuyển đối tượng, kiểu collector và hành vi thu hồi tạo ra thời gian chạy/footprint khác nhau ngay cả khi source code giống hệt.
- **Phiên bản Unicode và quy tắc xử lý chữ:** việc nâng JDK có thể cập nhật dữ liệu Unicode trong các API liên quan; nếu logic phụ thuộc thuộc tính ký tự hay quy tắc case mapping, kiểm thử lại với bộ dữ liệu thực.

Từng thay đổi có thể tối ưu nhiều workload nhưng không có “một cờ tốt nhất” áp dụng mọi ứng dụng. Tránh đánh giá chỉ từ một lần chạy trên máy phát triển.

## 7. Ví dụ Java 17+ có kết quả ổn định giữa các cài đặt

Lưu vào tệp `StringVersionDemo.java`:

```java
public class StringVersionDemo {

    public static void main(String[] args) {
        String parent = "abcdef";
        String piece = parent.substring(2, 5);

        System.out.println("substring = " + piece);
        System.out.println("substring length = " + piece.length());
        System.out.println("substring content = " + piece.equals("cde"));

        String literal = "java";
        String created = new String("java");
        System.out.println("new equals literal = " + created.equals(literal));
        System.out.println("new == literal = " + (created == literal));
        System.out.println("intern == literal = " + (created.intern() == literal));

        String folded = "ja" + "va";
        System.out.println("constant == literal = " + (folded == literal));

        String runtime = new StringBuilder()
                .append("ja")
                .append("va")
                .toString();
        System.out.println("runtime equals literal = " + runtime.equals(literal));
        System.out.println("runtime intern == literal = " + (runtime.intern() == literal));

        String first = new String(new char[] {'A', 'B'});
        String second = new String(new char[] {'A', 'B'});
        System.out.println("equal objects = " + first.equals(second));
        System.out.println("different references = " + (first != second));
        System.out.println("interned references = " + (first.intern() == second.intern()));

        String rocket = "\uD83D\uDE80";
        System.out.println("rocket UTF16 units = " + rocket.length());
        System.out.println("rocket code points = "
                + rocket.codePointCount(0, rocket.length()));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 StringVersionDemo.java
java StringVersionDemo
```

Kết quả:

```text
substring = cde
substring length = 3
substring content = true
new equals literal = true
new == literal = false
intern == literal = true
constant == literal = true
runtime equals literal = true
runtime intern == literal = true
equal objects = true
different references = true
interned references = true
rocket UTF16 units = 2
rocket code points = 1
```

Chương trình kiểm tra **hợp đồng quan sát được** thay vì kích thước backing array:

- `substring` trả đúng nội dung, không khẳng định mảng dữ liệu có được copy hay không.
- `new String("java")` tạo tham chiếu khác literal nhưng cùng nội dung.
- `intern` trả về một đại diện canonical; cả hai lần gọi `intern` trên các String cùng nội dung cho cùng tham chiếu.
- Hai `String` khác nhau từ hai mảng ký tự riêng có nội dung `"AB"` vẫn là hai đối tượng; dedup của GC không thay đổi phép kiểm tra identity của chúng.
- `length()` vẫn tính theo UTF-16 bất kể có dùng Compact Strings bên trong hay không.

Không nên tạo bài kiểm tra unit test có assertion về địa chỉ, trường `value` nội bộ, số byte chiếm dụng hoặc tốc độ của `substring`. Các đặc điểm này cần benchmark/profiler và có thể thay đổi qua patch version.

## 8. Cách kiểm tra tối ưu trên đúng JDK đang chạy

### Bước 1: ghi rõ môi trường

Kiểm tra JDK và JVM hiện dùng:

```bash
java -version
javac -version
```

Ghi lại nhà cung cấp, bản cập nhật, hệ điều hành, kiến trúc CPU, heap size, collector và các cờ JVM liên quan. Nói “Java 17” thôi chưa đủ để chứng minh hai tiến trình có cùng footprint và chiến lược tối ưu.

### Bước 2: đo vấn đề thật

Trước khi tối ưu, xác định thước đo:

- Nếu quan tâm **heap footprint**, dùng heap dump/profiler để thấy số lượng `String`, kích thước dữ liệu và những đối tượng còn tham chiếu.
- Nếu quan tâm **allocation rate/GC**, dùng Java Flight Recorder (JFR), công cụ giám sát GC hoặc heap/allocation profiler phù hợp.
- Nếu quan tâm **throughput/latency** của việc cắt và ghép chuỗi, dùng benchmark có warm-up và dữ liệu đại diện; [JMH](https://github.com/openjdk/jmh) là công cụ chuyên dụng của OpenJDK. Đừng dựa vào `System.nanoTime()` quanh một phép `substring` đơn lẻ để đưa kết luận.
- Nếu quan tâm **intern/dedup**, đo số giá trị lặp lại, thời gian sống và chi phí pool/GC. Không suy ra lượng bộ nhớ tiết kiệm từ `==`.

### Bước 3: so sánh một yếu tố mỗi lần

Chạy cùng workload với phiên bản JDK/cờ JVM dự kiến, cố định dữ liệu và tài nguyên, đo nhiều lần; cân nhắc chi phí GC và tác dụng phụ. Ví dụ, chỉ thử bật `-XX:+UseStringDeduplication` khi runtime hỗ trợ và đo được khối lượng chuỗi trùng lặp đáng kể. Đừng tự ý dùng cờ cũ như `-XX:MaxPermSize` trên JDK đã loại bỏ PermGen.

### Bước 4: ưu tiên tính đúng trước micro-optimization

Khi code rõ ràng và đúng phạm vi dữ liệu, thường nên giữ `substring`, `equals` và cách tạo chuỗi phổ biến. Chỉ thêm cache, intern hay cấu hình JVM sau khi có bằng chứng về bottleneck và kiểm thử hồi quy. Hiệu năng cũng có thể thay đổi khi nâng patch JDK, thay collector hoặc thay số lượng dữ liệu thực tế.

## 9. Các nhận định dễ sai

| Khẳng định | Đánh giá và cách hiểu đúng |
| --- | --- |
| “`substring` luôn tạo view dùng chung mảng gốc.” | Sai với nhiều JDK hiện đại; đó là hành vi của một số triển khai cũ trước JDK 7u6. |
| “`substring` luôn phải copy và tốn O(n) trên mọi JVM.” | Không phải bảo đảm API; hiệu năng, trường hợp biên và tối ưu JIT phụ thuộc cài đặt. |
| “`new String(s)` luôn tạo backing array mới, nên phải gọi sau `substring`.” | Không đúng cho JDK hiện đại; tránh dùng mẹo lịch sử không còn phù hợp. |
| “Mọi String intern đều ở PermGen.” | Sai từ JDK 7 trong HotSpot; đối tượng intern nằm trên heap thông thường. |
| “JDK 8 Metaspace là nơi lưu String pool.” | Sai; Metaspace thay một phần vai trò metadata của PermGen, không có nghĩa chuỗi Java nằm trong Metaspace. |
| “`intern` làm `equals` nhanh hơn cho mọi chuỗi.” | Không đúng; có chi phí duy trì pool và không thay thế quy tắc so sánh nội dung. |
| “String Deduplication làm `a == b` thành true.” | Sai; nó có thể chia sẻ dữ liệu nội bộ nhưng không hợp nhất các đối tượng String. |
| “JDK 9+ luôn dùng UTF-8 cho String vì có Compact Strings.” | Sai; cơ chế compact sử dụng Latin-1 hoặc UTF-16 trong triển khai tương ứng, không phải mã hóa UTF-8 của API. |
| “Mọi ký tự có dấu phải dùng hai byte ở Compact Strings.” | Sai; ký tự thuộc Latin-1 như `é` có thể dùng một byte trong dạng compact. |
| “Đổi JDK không ảnh hưởng tối ưu bộ nhớ.” | Sai; layout, JIT, GC và flags thay đổi. Cần đo lại nếu việc tối ưu quan trọng. |

## 10. Bài tập tự kiểm tra

**Bài 1.** Có thể viết code dựa vào giả định `big.substring(0, 3)` luôn giữ tham chiếu tới toàn bộ mảng dữ liệu của `big` không?

**Đáp án:** Không. Đó là chi tiết của một số JDK cũ. Hợp đồng API chỉ bảo đảm nội dung đoạn con và xử lý chỉ số hợp lệ.

**Bài 2.** Vì sao đoạn sau in `true` rồi `false`?

```java
String literal = "abc";
String copy = new String("abc");

System.out.println(literal.equals(copy));
System.out.println(literal == copy);
```

**Đáp án:** `equals` so sánh nội dung còn `==` so sánh identity; `new String(...)` tạo đối tượng String mới.

**Bài 3.** `a.intern() == b.intern()` cho biết điều gì nếu `a.equals(b)` là `true`?

**Đáp án:** Hai chuỗi cùng nội dung được đưa về cùng đại diện canonical trong pool. Điều đó không chứng minh chúng vốn là một đối tượng trước khi gọi `intern`.

**Bài 4.** Hai String `a` và `b` khác đối tượng nhưng có nội dung giống nhau. Sau String Deduplication của GC, `a == b` có chuyển thành `true` không?

**Đáp án:** Không. Deduplication có thể dùng chung dữ liệu nội bộ, còn đối tượng String vẫn giữ identity riêng.

**Bài 5.** Vì sao `"café"` có thể được lưu gọn ở JVM hỗ trợ Compact Strings, còn `"漢字"` cần dạng khác?

**Đáp án:** Các ký tự trong `"café"` nằm trong Latin-1; `"漢字"` có ký tự ngoài Latin-1. Đây là chiến lược lưu dữ liệu của JEP 254, không đổi ý nghĩa Unicode/UTF-16 mà API `String` cung cấp.

## Tài liệu tham khảo

- [Java SE 17 — API `String`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): hợp đồng `substring`, `intern`, tính immutable và mô hình UTF-16.
- [Oracle — Java Virtual Machine Enhancements in JDK 7](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/enhancements-7.html): sự thay đổi vị trí interned strings trong HotSpot từ JDK 7.
- [Oracle — JDK 9 Enhancements, JEP 254 Compact Strings](https://www.oracle.com/java/technologies/javase/9-enhancements.html): bố trí `byte[]` + coder Latin-1/UTF-16 và tùy chọn `CompactStrings`.
- [OpenJDK — JEP 192 String Deduplication in G1](https://openjdk.org/jeps/192): chia sẻ dữ liệu String trùng lặp và mục tiêu giảm sử dụng heap từ JDK 8u20.
- [OpenJDK — JEP 280 Indify String Concatenation](https://openjdk.org/jeps/280): cài đặt nối chuỗi thay đổi từ Java 9.
- [Aleksey Shipilëv — java.lang.String Catechism (2014)](https://2014.jokerconf.com/presentations/shipilev-string.pdf): phân tích trực quan lịch sử `substring` và chi phí bộ nhớ của cách chia sẻ mảng trước JDK 7u6.

**Nguyên tắc áp dụng:** viết Java dựa vào ngữ nghĩa chuẩn của `String`; đo `substring`, `intern` và các tối ưu bộ nhớ bằng **JDK/JVM thực sự triển khai ứng dụng**. Các mẹo về backing array, pool, cấp phát hoặc GC luôn cần được đặt trong ngữ cảnh phiên bản cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)
