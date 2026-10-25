<a id="back-to-top"></a>

# Tổng quan

## Menu
- [String là gì và vai trò biểu diễn văn bản trong Java](#string-overview-001)
- [Quan hệ giữa String, char&#91;&#93;, CharSequence và đối tượng](#string-overview-002)
- [Phân biệt nội dung chuỗi với biến tham chiếu đến chuỗi](#string-overview-003)
- [Chuỗi rỗng, chuỗi trắng, null và chuỗi chứa chữ null](#string-overview-004)
- [Vì sao String là lớp final và có tính bất biến](#string-overview-005)
- [Các nhóm thao tác: đọc, so sánh, tìm kiếm, biến đổi và chuyển mã](#string-overview-006)
- [Chọn String, StringBuilder hoặc StringBuffer theo nhu cầu](#string-overview-007)
- [Kiến thức nền và trình tự học Java Core String](#string-overview-008)

## <a id="string-overview-001">String là gì và vai trò biểu diễn văn bản trong Java</a>

<details>
<summary>Xem chi tiết</summary>

# String là gì và vai trò biểu diễn văn bản trong Java

## 1. String là gì?

**String** là lớp **java.lang.String** dùng để lưu trữ và thao tác với văn bản trong Java. Một giá trị String là một đối tượng biểu diễn **chuỗi các đơn vị mã UTF-16** (UTF-16 code units), ví dụ tên người dùng, thông báo, đường dẫn, dữ liệu văn bản nhận từ API hoặc nội dung cần ghi vào tệp.

~~~java
String greeting = "Xin chào";
String language = "Java";
String sentence = greeting + ", " + language + "!";
~~~

Đây là **đoạn mã minh họa** có thể đặt bên trong một phương thức; biến **sentence** chứa văn bản “Xin chào, Java!”. Lớp String nằm trong gói **java.lang** nên không cần import.

Các đặc điểm nền tảng:

- **Kiểu tham chiếu:** String là một lớp, không phải kiểu nguyên thủy như **int**, **boolean** hay **char**. Biến String có thể tham chiếu đến đối tượng String hoặc có giá trị **null**.
- **Bất biến (immutable):** sau khi một đối tượng String được tạo ra, nội dung của nó không đổi. Các thao tác như **replace**, **concat**, **toUpperCase** hay **strip** trả về giá trị String kết quả; chúng không sửa đối tượng ban đầu.
- **Lớp final:** không thể kế thừa String để thay đổi hành vi của nó.
- **Biểu diễn UTF-16 ở mức API:** nhiều phương thức như **length()**, **charAt()** và **substring()** làm việc theo vị trí đơn vị mã UTF-16. Một ký tự người dùng nhìn thấy trên màn hình không nhất thiết tương ứng đúng một vị trí.
- **Có giá trị rỗng:** chuỗi **""** là một String hợp lệ có độ dài bằng 0. Nó khác **null**, tức không có đối tượng String được tham chiếu.

Vai trò của String là **mô hình dữ liệu văn bản** trong chương trình: nhận và hiển thị dữ liệu nhập, truyền thông điệp giữa các lớp, xử lý định dạng, kết hợp với thư viện I/O, cơ sở dữ liệu, HTTP, JSON và các API xử lý ngôn ngữ. String chỉ là kiểu dữ liệu biểu diễn văn bản; nó không tự xác định bộ mã của tệp hay làm sạch đầu vào.

## 2. Khởi tạo String, literal và string pool

Cách thông dụng nhất là dùng **string literal** trong dấu ngoặc kép:

~~~java
String first = "Java";
String second = "Java";
String third = new String("Java");
~~~

Literal có thể được tái sử dụng qua **string pool** của JVM. Trong ví dụ này, **first** và **second** cùng tham chiếu một đối tượng literal đã intern; **new String("Java")** tạo một đối tượng mới có cùng nội dung. Tuy nhiên, khi kiểm tra **nội dung**, luôn dùng **equals()** thay vì dựa vào địa chỉ đối tượng.

Một số cách hình thành chuỗi khác:

- **Ghép:** **"Xin " + "chào"**, hoặc ghép với biến bằng toán tử **+**.
- **Từ mảng ký tự:** **new String(charArray)** tạo String từ các phần tử của mảng; thay đổi mảng sau đó không sửa String đã tạo.
- **Từ byte:** **new String(bytes, StandardCharsets.UTF_8)** dùng bộ giải mã UTF-8 cụ thể; không xem mỗi byte là một ký tự.
- **Từ giá trị khác:** **String.valueOf(42)** cho ra “42”; **String.valueOf((Object) null)** cho ra chữ “null”, khác với tham chiếu null.
- **Text block (Java 15+):** cú pháp ba dấu ngoặc kép cho văn bản nhiều dòng, phù hợp với JSON, SQL mẫu hoặc thông điệp dài. Các quy tắc xuống dòng và thụt lề của text block cần được tính đến khi so sánh kết quả.

~~~java
String message = """
        Xin chào,
        Java!
        """;
~~~

Đoạn mã text block trên là ví dụ khai báo, không phải chương trình hoàn chỉnh. Nội dung nhận được có dấu xuống dòng ở cuối dòng “Java!” do cách viết literal.

**String.intern()** trả về tham chiếu chuẩn trong pool cho cùng nội dung; đây là cơ chế tối ưu/canonicalize có chủ đích, không phải cách so sánh chuỗi thông thường. Không sử dụng **==** với kỳ vọng mọi chuỗi có cùng văn bản luôn chia sẻ một tham chiếu.

## 3. Bản đồ khái niệm: học đúng trọng tâm

Bài mở đầu này giúp nhìn thấy vai trò của String trong chương trình và thử một ví dụ tổng hợp. Mỗi vấn đề dưới đây có bài riêng để đào sâu, nên ở đây chỉ xác định sự khác nhau cốt yếu:

| Câu hỏi thực tế | Định hướng đầu tiên | Nội dung học sâu |
| --- | --- | --- |
| Một giá trị văn bản khác mảng ký tự ra sao? | `String` bất biến; `char[]` có thể sửa; `CharSequence` là giao diện đọc. | Quan hệ String, char[], CharSequence và đối tượng |
| Hai biến cùng ghi `"Java"` có nhất thiết cùng đối tượng? | Phân biệt tham chiếu với nội dung; dùng `equals()` để so nội dung. | Nội dung chuỗi và biến tham chiếu |
| `null`, `""`, dấu cách và chữ `"null"` có giống nhau không? | Đó là những trạng thái dữ liệu khác nhau; `isEmpty()` không đồng nghĩa `isBlank()`. | Chuỗi rỗng, chuỗi trắng, null |
| Vì sao một lời gọi `replace()` không đổi chuỗi gốc? | Đối tượng String bất biến; phép biến đổi trả về một giá trị String. | Lớp final và tính bất biến |
| Tìm, cắt, so sánh, thay thế hoặc chuyển byte thế nào? | Chọn API theo mục đích và theo đơn vị ký tự/charset. | Các nhóm thao tác String |
| Khi nào dùng StringBuilder/StringBuffer? | Khi cần dựng văn bản theo nhiều bước; xem thêm vấn đề chia sẻ giữa các luồng. | Chọn String, StringBuilder hoặc StringBuffer |

**Ba ranh giới quan trọng:** (1) biến tham chiếu có thể gán lại dù đối tượng String bất biến; (2) `length()` đếm đơn vị mã UTF-16, không luôn bằng số ký tự hiển thị; (3) `String` biểu diễn văn bản, còn tệp/HTTP truyền byte phải có charset phù hợp, thường là UTF-8.

Chẳng hạn `"Java".equals(candidate)` so nội dung và xử lý được `candidate == null`; `"a.b".replace(".", "-")` thay dấu chấm literal, còn `replaceAll` nhận regex. Các lựa chọn ấy sẽ được thực hành có hệ thống ở bài thao tác String.

**Khi ra khỏi bộ nhớ:** đọc/ghi file, JSON, SQL và HTML cần API đúng ngữ cảnh. Charset, serialize, query parameter và escaping không phải chức năng tự động của String. Dữ liệu nhạy cảm trong String cũng không thể chủ động xóa nội dung đối tượng tại chỗ.

## 4. Ví dụ Java 17+ chạy được

Lưu mã sau vào tệp **StringOverviewDemo.java**, bảo đảm tên tệp trùng tên lớp **public**.

~~~java
import java.nio.charset.StandardCharsets;

public class StringOverviewDemo {
    public static void main(String[] args) {
        String raw = "  Xin chào Java  ";
        String cleaned = raw.strip();

        System.out.println(cleaned);
        System.out.println(cleaned.length());
        System.out.println("Java".equals("java"));

        String literal = "Java";
        String copy = new String("Java");
        System.out.println(literal == copy);
        System.out.println(literal.equals(copy));

        String emoji = "😊";
        System.out.println(emoji.length());
        System.out.println(emoji.codePointCount(0, emoji.length()));

        System.out.println("a.b".replace(".", "-"));
        System.out.println("a.b".replaceAll("\\.", "-"));

        byte[] encoded = "Việt".getBytes(StandardCharsets.UTF_8);
        System.out.println(new String(encoded, StandardCharsets.UTF_8));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 StringOverviewDemo.java
java StringOverviewDemo
~~~

Kết quả stdout (mỗi kết quả một dòng):

~~~text
Xin chào Java
13
false
false
true
2
1
a-b
a-b
Việt
~~~

Giải thích: **strip()** bỏ hai dấu cách hai đầu; độ dài phần còn lại là 13 code unit. Hai chuỗi khác chữ hoa/thường không bằng nhau qua **equals()**. **new String(...)** tạo một đối tượng khác literal dù nội dung bằng nhau. Emoji dùng hai code unit nhưng là một code point. **replace** và **replaceAll** đều thay đúng dấu chấm khi regex được escape. Byte UTF-8 mã hóa rồi giải mã trở về văn bản gốc.

## 5. Tự kiểm tra kiến thức

1. **Vì sao String bất biến nhưng vẫn có thể viết s = s + "!"?** Vì phép gán đổi tham chiếu sang kết quả mới; không sửa đối tượng ban đầu.
2. **Khi nào dùng equals thay cho ==?** Khi cần so nội dung văn bản, bất kể đối tượng được tạo như thế nào.
3. **"".isEmpty() và " ".isBlank() trả về gì?** Cả hai đều **true**, nhưng áp dụng cho hai tình huống khác nhau; **" ".isEmpty()** là **false**.
4. **Vì sao "😊".length() trả về 2?** Emoji này được biểu diễn bằng hai code unit UTF-16, dù chỉ gồm một Unicode code point.
5. **Dùng gì để ghép nhiều nhãn qua vòng lặp?** Thường dùng **StringBuilder**; khi đã có tập các phần tử, **String.join** cũng rất thuận tiện.
6. **Vì sao nên chỉ định StandardCharsets.UTF_8 khi đọc/ghi?** Để encoding nhất quán và không phụ thuộc charset mặc định của hệ thống.

**Ghi nhớ:** String là kiểu dữ liệu trung tâm cho văn bản trong Java, có tính bất biến và API được thiết kế chủ yếu quanh UTF-16. Hiểu đúng sự khác nhau giữa **nội dung và tham chiếu**, **code unit và ký tự hiển thị**, **String và byte đã mã hóa** sẽ giúp tránh phần lớn lỗi xử lý văn bản.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-overview-002">Quan hệ giữa String, char&#91;&#93;, CharSequence và đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa `String`, `char[]`, `CharSequence` và `Object` trong Java

## 1. Mục tiêu và bức tranh tổng thể

Trong Java, cùng một nội dung văn bản có thể xuất hiện dưới nhiều kiểu: đối tượng `String`, mảng `char[]`, một đối tượng triển khai `CharSequence`, hoặc một tham chiếu `Object`. Những kiểu này **không đồng nghĩa**, dù đôi khi cùng biểu diễn các ký tự giống nhau. Để sử dụng đúng cần phân biệt ba câu hỏi:

1. **Kiểu và quan hệ kế thừa:** gán một giá trị cho biến kiểu nào là hợp lệ?
2. **Khả năng thay đổi và sở hữu dữ liệu:** sửa đối tượng/mảng ban đầu có làm nội dung đang được quan sát thay đổi không?
3. **Ngữ nghĩa thao tác:** các phương thức `equals`, `toString`, `charAt`, `length` và chuyển đổi sẽ xử lý dữ liệu như thế nào?

Sơ đồ kiểu (Java 17+):

```text
java.lang.Object
  ├── java.lang.String (final)
  │      └── implements CharSequence, Comparable<String>, Serializable, ...
  ├── java.lang.StringBuilder (mutable, implements CharSequence)
  ├── java.lang.StringBuffer  (mutable, implements CharSequence)
  ├── java.nio.CharBuffer     (implements CharSequence)
  └── char[]                  (kiểu mảng; là Object, KHÔNG implements CharSequence)

java.lang.CharSequence là interface (hợp đồng đọc dãy char),
không phải lớp cha của Object và không phải kiểu mảng.
```

Mọi `String` và mọi mảng `char[]` đều là đối tượng Java, nên có thể được giữ trong một biến `Object`. Nhưng `char[]` **không** phải là `String` hay `CharSequence`; giữa chúng không có quan hệ gán kiểu trực tiếp. `String` vừa là một `Object` vừa là một `CharSequence` vì lớp này triển khai interface đó.

| Kiểu | Bản chất | Thay đổi nội dung qua chính giá trị đó? | Quan hệ nổi bật |
| --- | --- | --- | --- |
| `String` | Lớp `final` chứa chuỗi ký tự bất biến | Không | `extends Object`; `implements CharSequence` |
| `char[]` | Mảng phần tử `char` nguyên thủy | Có, bằng cách gán `array[i]` | Là `Object`; không triển khai `CharSequence` |
| `CharSequence` | Interface để đọc dãy `char` | Interface không có thao tác sửa; đối tượng thực tế *có thể* thay đổi | `String`, `StringBuilder`, `StringBuffer`, `CharBuffer` triển khai |
| `Object` | Lớp gốc của đối tượng Java | Không quy định nội dung văn bản | Chỉ cung cấp hợp đồng đối tượng chung |

**Phân biệt biến với đối tượng:** `String name = "A"; name = "B";` là **gán lại tham chiếu** của biến `name`, không sửa nội dung đối tượng `String("A")`.

## 2. `String` là một `Object` và triển khai `CharSequence`

Khai báo rút gọn của Java 17 là:

```java
public final class String
        implements java.io.Serializable,
                   Comparable<String>,
                   CharSequence,
                   java.lang.constant.Constable,
                   java.lang.constant.ConstantDesc {
    // Nội dung phương thức được lược bỏ.
}
```

Đây chỉ là **chữ ký minh họa**, không phải lớp tự biên dịch: các phương thức/thuộc tính cài đặt đã bị lược bỏ. `String` kế thừa `Object` theo quy tắc của Java ngay cả khi khai báo không ghi `extends Object`.

Một đối tượng `String` có thể được nhìn qua nhiều kiểu tham chiếu:

```java
String text = "Hello";
CharSequence sequence = text; // upcast: hợp lệ
Object object = text;         // upcast: hợp lệ

System.out.println(sequence.length());     // 5
System.out.println(object instanceof String); // true
```

Khi kiểu tĩnh là `CharSequence`, chương trình chỉ được gọi các phương thức có trong hợp đồng `CharSequence` (cộng các phương thức của `Object`), dù đối tượng thực tế là `String`. Khi kiểu tĩnh là `Object`, `object.length()` không biên dịch vì `Object` không khai báo `length()`. Cần kiểm tra rồi ép kiểu:

```java
if (object instanceof String actual) {
    System.out.println(actual.length()); // 5
}
```

Một phép ép kiểu `(String) object` chỉ đúng khi đối tượng thực tế là `String` (hoặc `null`). Nếu `object` đang chứa `char[]` hay `StringBuilder`, ép sang `String` gây `ClassCastException`. Thao tác ép kiểu không thực hiện chuyển đổi nội dung.

## 3. `char[]`: mảng có thể sửa, không tự trở thành chuỗi

`char` là kiểu nguyên thủy 16 bit biểu diễn **đơn vị mã UTF-16**. `char[]` là **đối tượng mảng**, chứa nhiều giá trị `char`, có thuộc tính `length` (không phải phương thức `length()`), và cho phép sửa phần tử.

```java
char[] letters = {'J', 'a', 'v', 'a'};
letters[0] = 'K';
System.out.println(letters.length); // 4
```

`String` không cung cấp cú pháp gán `text[0] = 'K'` và không có phương thức thay đổi vị trí ký tự tại chỗ. Các thao tác như `replace`, `substring` hoặc `concat` tạo ra kết quả kiểu `String`, không sửa giá trị chuỗi hiện có; việc JVM có thể tái sử dụng đối tượng ở một số tình huống không làm thay đổi tính bất biến.

Chuyển đổi `char[]` sang `String` tạo nội dung văn bản từ **giá trị phần tử tại thời điểm chuyển đổi**:

```java
char[] letters = {'J', 'a', 'v', 'a'};
String text = new String(letters);
letters[0] = 'K';
System.out.println(text);                // Java
System.out.println(new String(letters)); // Kava
```

Constructor `new String(char[])` **sao chép nội dung**. `String.valueOf(char[])` và `String.copyValueOf(char[])` cũng tạo chuỗi từ các ký tự của mảng. Thay đổi mảng nguồn sau đó không làm thay đổi `String` đã tạo. Theo chiều ngược lại, `text.toCharArray()` trả về mảng mới có thể sửa, không làm biến đổi chuỗi gốc:

```java
String text = "Java";
char[] copy = text.toCharArray();
copy[0] = 'K';
System.out.println(text);        // Java
System.out.println(new String(copy)); // Kava
```

Để lấy một đoạn ký tự vào mảng có sẵn, dùng `text.getChars(srcBegin, srcEnd, destination, dstBegin)` và bảo đảm đủ chỗ đích. Khi cần xử lý nhiều lần theo kiểu mảng, cân nhắc chi phí tạo bản sao và yêu cầu sở hữu dữ liệu.

**Lưu ý về bộ nhớ:** mô hình API của `String` diễn đạt một dãy đơn vị mã UTF-16; không suy ra Java bắt buộc lưu `String` bằng một `char[]` bên trong. Các JDK hiện đại có thể dùng cách lưu trữ tối ưu khác, và đó là chi tiết triển khai, không phải quan hệ kiểu Java.

## 4. `CharSequence`: hợp đồng đọc chuỗi ký tự, không cam kết bất biến

`CharSequence` cung cấp giao diện chung để thư viện có thể nhận văn bản mà không buộc người gọi phải tạo `String` ngay từ đầu. Các thao tác cốt lõi:

| API | Ý nghĩa |
| --- | --- |
| `length()` | Số đơn vị `char` (UTF-16) trong dãy |
| `charAt(index)` | Đọc một đơn vị `char` tại chỉ số từ 0 |
| `subSequence(start, end)` | Lấy đoạn `[start, end)`; kiểu trả về được khai báo là `CharSequence` |
| `toString()` | Biểu diễn dãy tại thời điểm gọi dưới dạng `String` |
| `isEmpty()` | Kiểm tra `length() == 0` |
| `chars()` | `IntStream` của các đơn vị UTF-16 |
| `codePoints()` | `IntStream` các Unicode code point, ghép cặp surrogate hợp lệ |

Những phương thức trên hỗ trợ **đọc**, nhưng interface không bảo đảm đối tượng bên dưới là bất biến. `String` bất biến; `StringBuilder` và `StringBuffer` có thể thay đổi; `CharBuffer` có thể phản ánh mảng dữ liệu nền và trạng thái vị trí/giới hạn. `StringBuffer` đồng bộ hóa nhiều phương thức riêng lẻ, nhưng điều đó không khiến hợp đồng chung `CharSequence` trở thành bất biến hoặc bảo đảm an toàn cho mọi chuỗi thao tác đa luồng.

Chẳng hạn:

```java
StringBuilder builder = new StringBuilder("Java");
CharSequence sequence = builder;
builder.append(" 17");
System.out.println(sequence); // Java 17
```

Tham chiếu `sequence` nhìn thấy thay đổi vì cả hai tham chiếu đang trỏ tới **cùng đối tượng `StringBuilder`**. Ngược lại, nếu cần lưu một bản chụp nội dung bất biến, có thể chuyển kiểu sang `String`, ví dụ `String snapshot = builder.toString();`. Phải xác định rõ thời điểm tạo snapshot và tránh thay đổi đồng thời khi đọc một nguồn mutable.

`char[]` không triển khai `CharSequence`, nên đoạn dưới là ví dụ **cố ý sai kiểu**:

```java
char[] raw = {'H', 'i'};
// CharSequence cs = raw; // LỖI BIÊN DỊCH: char[] không implements CharSequence
CharSequence copied = new String(raw); // Bản sao bất biến
```

Nếu cần một view có thể thấy thay đổi của mảng, dùng `java.nio.CharBuffer.wrap(raw)`. View này được hỗ trợ bởi chính mảng nguồn: sửa mảng có thể quan sát qua buffer, và vị trí/giới hạn của buffer cũng ảnh hưởng `CharSequence.length()`, `charAt()`, `subSequence()` khi làm việc với `CharBuffer`.

## 5. Ví dụ hoàn chỉnh: sao chép, upcast và quan sát giá trị

Lưu thành `StringRelationsDemo.java`:

```java
public class StringRelationsDemo {
    public static void main(String[] args) {
        char[] source = {'J', 'a', 'v', 'a'};
        String text = new String(source);
        CharSequence sequence = text;
        Object asObject = text;

        source[0] = 'K';
        System.out.println(text);
        System.out.println(new String(source));
        System.out.println(sequence.length());
        System.out.println(sequence.charAt(1));
        System.out.println(sequence.subSequence(1, 3));
        System.out.println(asObject instanceof String);

        char[] snapshot = text.toCharArray();
        snapshot[0] = 'X';
        System.out.println(text);

        Object arrayAsObject = source;
        System.out.println(arrayAsObject instanceof String);
        System.out.println(String.valueOf(source));
        System.out.println(
                String.valueOf((Object) source).startsWith("[C@"));
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 StringRelationsDemo.java
java StringRelationsDemo
```

Kết quả chuẩn:

```text
Java
Kava
4
a
av
true
Java
false
Kava
true
```

Giải thích:
- Hai dòng đầu cho thấy `new String(source)` đã chụp nội dung ban đầu: thay mảng nguồn thành `Kava` không ảnh hưởng `text`.
- `sequence` vẫn trỏ đến `String("Java")`, nên `length()` là 4, `charAt(1)` là `a`, `subSequence(1,3)` là `av`.
- `asObject` chứa một `String` thực, trong khi `arrayAsObject` chứa một đối tượng `char[]`; cả hai là `Object` nhưng kết quả `instanceof String` khác nhau.
- `String.valueOf(source)` chọn overload nhận `char[]`, nên trả văn bản `Kava`. Ép `source` thành `Object` chọn overload `valueOf(Object)`: mảng dùng `Object.toString()` có dạng bắt đầu bằng `[C@`, sau đó là phần định danh không ổn định. Chỉ kiểm tra tiền tố để tránh phụ thuộc vào mã băm.

## 6. Viết API tổng quát nhận `CharSequence`

Một hàm chỉ cần đọc các ký tự có thể nhận `CharSequence` để làm việc với `String`, `StringBuilder` hoặc `CharBuffer`. Ví dụ sau là **chương trình độc lập**; lưu thành `SequenceApiDemo.java`:

```java
import java.nio.CharBuffer;

public class SequenceApiDemo {
    static String prefix(CharSequence input, int count) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        int end = Math.min(count, input.length());
        return input.subSequence(0, end).toString();
    }

    public static void main(String[] args) {
        CharSequence immutable = "Hello";
        StringBuilder builder = new StringBuilder("Builder");
        char[] raw = {'C', 'h', 'a', 'r'};
        CharSequence view = CharBuffer.wrap(raw);

        System.out.println(prefix(immutable, 2));
        System.out.println(prefix(builder, 3));
        System.out.println(prefix(view, 2));

        builder.append("!");
        raw[0] = 'X';
        System.out.println(builder);
        System.out.println(view);
    }
}
```

```bash
javac -encoding UTF-8 SequenceApiDemo.java
java SequenceApiDemo
```

```text
He
Bui
Ch
Builder!
Xhar
```

Ở đây `prefix` dùng hợp đồng chung của `CharSequence`; kết quả trả về `String`. `CharBuffer.wrap(raw)` **không tạo bản sao**, nên dòng cuối thấy ký tự `X` vừa sửa. Nếu yêu cầu đóng băng đầu vào tại một thời điểm, hãy tạo một `String` từ nội dung trước khi lưu trữ; nếu cần bảo đảm nhất quán khi nhiều luồng cùng sửa nguồn, phải phối hợp đồng bộ ở bên ngoài.

### Khi nào nên nhận `String`, khi nào nên nhận `CharSequence`?

- Dùng `String` khi API cần chuỗi bất biến để lưu lâu dài, làm khóa, so sánh theo quy tắc `String`, hoặc cần các phương thức chỉ có ở `String` như `replace`, `split`, `matches`.
- Dùng `CharSequence` khi thuật toán chỉ cần đọc `length`, `charAt`, `subSequence` hoặc luồng ký tự và muốn chấp nhận nhiều cách biểu diễn. Tài liệu API nên nêu rõ liệu hàm có giữ tham chiếu đầu vào sau khi trả về hay không.
- Dùng `char[]` khi thực sự cần thao tác tại chỗ với phần tử `char`, giao tiếp với API chấp nhận mảng hoặc chủ động quản lý bản sao. Không mặc định mảng nhanh hơn: hiệu năng phụ thuộc thao tác và mức cấp phát.
- Dùng `StringBuilder` để xây dựng văn bản thay đổi nhiều lần trong cùng ngữ cảnh; gọi `toString()` khi cần kết quả bất biến.

## 7. `Object`, `toString()`, overload và các kiểu so sánh

Vì `String` là `Object`, `Object ref = "Java";` là hợp lệ; tuy nhiên **kiểu tĩnh** của tham chiếu quyết định phương thức nào có thể gọi trực tiếp. `Object.toString()` là phương thức đa hình: `String.toString()` trả lại nội dung chuỗi; `StringBuilder.toString()` trả một chuỗi biểu diễn nội dung tại thời điểm gọi. Mảng `char[]` không ghi đè `Object.toString()` để in ký tự người đọc mong đợi.

Các overload dễ gây nhầm:

```java
char[] data = {'O', 'K'};
System.out.println(String.valueOf(data));          // OK
System.out.println(String.valueOf((Object) data)); // dạng [C@..., không phải "OK"
System.out.println(data);                          // OK, do println(char[])
System.out.println((Object) data);                 // dạng [C@..., do println(Object)
```

Đoạn trên là **snippet giải thích overload**, không định nghĩa một lớp `main` riêng. Không so sánh toàn bộ dạng `[C@...` với một chuỗi cố định: phần sau ký tự `@` phụ thuộc đối tượng/chạy chương trình.

Quan hệ kiểu không quyết định cách so sánh nội dung:

| Biểu thức | Kiểm tra | Khuyến nghị |
| --- | --- | --- |
| `a == b` với hai tham chiếu | Cùng đối tượng hay không | Dùng khi quan tâm identity |
| `stringA.equals(stringB)` | Nội dung của hai `String` | Dùng cho bình đẳng chuỗi |
| `Arrays.equals(charArrayA, charArrayB)` | Từng phần tử của hai mảng | Dùng cho bình đẳng mảng |
| `charArrayA.equals(charArrayB)` | Identity của mảng (kế thừa `Object`) | Không dùng để so nội dung |
| `string.contentEquals(sequence)` | Nội dung `String` so với một `CharSequence` | Dùng khi cần so khác lớp triển khai |

`CharSequence` **không quy định lại hợp đồng `equals`/`hashCode`** của `Object`. Vì thế không nên cho rằng hai `CharSequence` có nội dung giống nhau sẽ `equals` nhau, đặc biệt khi thuộc các lớp khác nhau. Một tập hợp `HashSet<CharSequence>` hoặc `Map<CharSequence, ...>` chỉ phù hợp khi đã chủ động chọn và kiểm soát quy tắc bình đẳng, bất biến của khóa. Với khóa văn bản, `String` thường an toàn và dễ dự đoán hơn.

Ví dụ độc lập, lưu `EqualityDemo.java`:

```java
import java.util.Arrays;

public class EqualityDemo {
    public static void main(String[] args) {
        String a = new String(new char[] {'h', 'i'});
        String b = new String(new char[] {'h', 'i'});
        char[] x = {'h', 'i'};
        char[] y = {'h', 'i'};
        CharSequence builder = new StringBuilder("hi");

        System.out.println(a.equals(b));
        System.out.println(a == b);
        System.out.println(Arrays.equals(x, y));
        System.out.println(x.equals(y));
        System.out.println(a.equals(builder));
        System.out.println(a.contentEquals(builder));
    }
}
```

```bash
javac -encoding UTF-8 EqualityDemo.java
java EqualityDemo
```

```text
true
false
true
false
false
true
```

`a` và `b` có nội dung bằng nhau nhưng là hai đối tượng `String` được tạo riêng. Hai mảng `x`, `y` bằng nhau về phần tử nhưng không trỏ đến cùng một mảng. `a.equals(builder)` là `false` vì đối số không phải `String`, trong khi `contentEquals` so các ký tự nên trả `true`. Với `String` literal, JVM có thể dùng string pool; vì vậy không dùng ví dụ `"hi" == "hi"` để suy ra quy tắc so sánh nội dung chung.

## 8. Unicode: `char`, ký tự Unicode và độ dài chuỗi không luôn trùng nhau

`char` của Java có kích thước 16 bit, đại diện một **đơn vị mã UTF-16**. Một Unicode code point nằm ngoài Basic Multilingual Plane (BMP) cần **hai** `char` (một cặp surrogate). Do đó:
- `String.length()`, `CharSequence.length()` và độ dài mảng `char[]` cùng đếm **đơn vị UTF-16** trong dãy tương ứng, không nhất thiết là số Unicode code point hoặc số ký tự người đọc cảm nhận.
- `charAt(i)` trả một đơn vị UTF-16; nó có thể là nửa của cặp surrogate.
- `codePointCount`, `codePointAt`, `offsetByCodePoints` và `codePoints()` hữu ích khi cần xử lý theo code point. Một *grapheme cluster* (ký tự hiển thị) vẫn có thể gồm nhiều code point, nên đếm code point không đồng nghĩa đếm ký tự người dùng nhìn thấy.

Ví dụ độc lập, lưu `UnicodeDemo.java` (tệp mã hóa UTF-8):

```java
public class UnicodeDemo {
    public static void main(String[] args) {
        String value = "A🙂B";
        System.out.println(value.length());
        System.out.println(value.toCharArray().length);
        System.out.println(value.codePointCount(0, value.length()));
        System.out.println(value.codePoints().count());
        System.out.println(value.offsetByCodePoints(0, 2));
    }
}
```

```bash
javac -encoding UTF-8 UnicodeDemo.java
java UnicodeDemo
```

```text
4
4
3
3
3
```

Chuỗi gồm `A` (một `char`), emoji `🙂` (hai `char`), và `B` (một `char`): tổng cộng bốn đơn vị UTF-16 nhưng ba code point. Chỉ số UTF-16 ngay sau hai code point đầu là 3. Nếu cắt bằng `substring`/`subSequence` giữa hai nửa surrogate, bạn có thể tạo dãy UTF-16 không hợp lệ; khi xử lý code point, hãy tính biên bằng `offsetByCodePoints` hoặc API Unicode phù hợp.

## 9. Lỗi thường gặp và cách xử lý

1. **Cho rằng `char[]` là `CharSequence`:** ép kiểu trực tiếp không tạo adapter; dùng `new String(array)` để sao chép hoặc `CharBuffer.wrap(array)` khi chủ ý chia sẻ mảng.
2. **Dùng `==` hay `char[].equals` để so nội dung:** chọn `String.equals`, `Arrays.equals` hoặc `String.contentEquals` tùy loại dữ liệu và quy tắc mong muốn.
3. **Tin rằng mọi `CharSequence` đều bất biến:** cần xem lớp triển khai và việc lưu tham chiếu. `StringBuilder`/`CharBuffer` có thể đổi nội dung trong lúc đọc.
4. **In mảng qua `Object.toString()` rồi tưởng đó là văn bản:** `String.valueOf((Object) array)` không chọn overload `char[]`; dùng `new String(array)` hoặc `String.valueOf(array)`.
5. **Nhầm `array.length` với `string.length()`:** mảng dùng thuộc tính, `String`/`CharSequence` dùng phương thức.
6. **Ép `Object` sang `String` khi thực tế chứa mảng:** kiểm tra bằng `instanceof` rồi xử lý theo loại; ép kiểu khác chuyển đổi.
7. **Coi `charAt` là một ký tự Unicode hoàn chỉnh:** với ký tự ngoài BMP, phải làm việc theo code point hoặc grapheme cluster tùy yêu cầu.
8. **Dùng `String` để ráp nhiều đoạn trong vòng lặp lớn:** `String` bất biến nên phép nối lặp có thể tạo nhiều đối tượng trung gian; `StringBuilder` thường phù hợp khi tích lũy văn bản.
9. **Dùng một `CharSequence` mutable làm khóa map/set:** biến đổi nội dung hay khác biệt `equals`/`hashCode` giữa lớp triển khai có thể gây kết quả khó dự đoán; thường nên chuẩn hóa thành `String` khi chọn khóa văn bản.
10. **Giả định `char[]` chứa bí mật thì có thể xóa sạch mọi bản sao:** có thể ghi đè mảng mình sở hữu, nhưng Java/JVM/API có thể tạo bản sao ở nơi khác. Không hứa bảo đảm xóa khỏi toàn bộ bộ nhớ chỉ bằng một lần `Arrays.fill`.

## 10. Quy tắc lựa chọn nhanh và tự kiểm tra

Hãy xác định yêu cầu trước khi chọn kiểu:

- **Cần một giá trị văn bản ổn định:** dùng `String`.
- **Cần tham số chỉ đọc để chấp nhận nhiều nguồn văn bản:** dùng `CharSequence`; quyết định rõ liệu dữ liệu có được chụp thành `String` khi lưu hay không.
- **Cần sửa từng phần tử dữ liệu UTF-16:** dùng `char[]` hoặc một buffer phù hợp.
- **Cần xây dựng/nối liên tục:** dùng `StringBuilder`, rồi lấy `String` khi hoàn tất.
- **Chỉ có tham chiếu `Object`:** kiểm tra kiểu thực tế và chuyển đổi có chủ đích; không kỳ vọng `Object.toString()` của mọi loại đều trả nội dung văn bản.

Câu hỏi ôn tập:
1. Vì sao thay `charArray[0]` sau `new String(charArray)` không làm đổi `String`, còn với `CharBuffer.wrap(charArray)` thì có thể quan sát thấy thay đổi?
2. Vì sao `CharSequence s = new StringBuilder("x");` là hợp lệ, còn `CharSequence s = new char[] {'x'};` không hợp lệ?
3. `String.valueOf(array)` khác `String.valueOf((Object) array)` ở bước chọn overload nào?
4. Vì sao `string.equals(builder)` và `string.contentEquals(builder)` có thể trả kết quả khác nhau?
5. Khi `text.length() == 4`, có thể kết luận chuỗi có đúng bốn Unicode code point hay bốn ký tự hiển thị không? Vì sao?

Nắm được **quan hệ kiểu**, **tính bất biến/sao chép**, **hợp đồng `CharSequence`** và **đơn vị UTF-16** sẽ giúp thiết kế API đúng, tránh lỗi ép kiểu/so sánh và chọn cấu trúc dữ liệu phù hợp.

## Tài liệu API chính thức (Java SE 17)

- [`String` — Oracle Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`CharSequence` — Oracle Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/CharSequence.html)
- [`CharBuffer` — Oracle Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/CharBuffer.html)
- [`Arrays` — Oracle Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-overview-003">Phân biệt nội dung chuỗi với biến tham chiếu đến chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt nội dung chuỗi với biến tham chiếu đến chuỗi

Trong Java, khi viết `String name = "Java";`, cần phân biệt **nội dung chuỗi** (`"Java"`), **đối tượng `String`** biểu diễn nội dung đó và **biến tham chiếu** (`name`) dùng để truy cập đối tượng. Ba khái niệm liên quan nhưng không đồng nhất. Nắm được sự khác biệt này giúp giải thích phép gán, tính bất biến của `String`, so sánh bằng `==` và `equals()`, cũng như các trường hợp dễ gây lỗi khi xử lý chuỗi.

## 1. Nội dung, đối tượng và biến tham chiếu

Xét:

```java
String text = "Java";
```

- `String` là kiểu tham chiếu (`reference type`), không phải kiểu nguyên thủy như `int` hay `boolean`.
- `"Java"` là một *string literal*: biểu thức cung cấp tham chiếu tới một đối tượng `String` có nội dung là bốn ký tự `J`, `a`, `v`, `a`. Java quản lý các literal và chuỗi được `intern()` trong một vùng dùng chung gọi là *string pool*.
- `text` là biến kiểu `String`. Giá trị của biến này là **một tham chiếu tới đối tượng** (hoặc `null`); không nên hiểu biến tự chứa một bản sao độc lập của toàn bộ nội dung `"Java"`.
- Nội dung chuỗi là dãy ký tự mà đối tượng `String` biểu diễn. Nội dung ấy có thể được hỏi bằng các phương thức như `length()`, `charAt()` hoặc `equals()`.

Có thể hình dung quan hệ ở mức khái niệm:

```text
Biến text --tham chiếu--> Đối tượng String
                         Nội dung: "Java"
```

Sơ đồ nhằm minh họa quan hệ tham chiếu, **không phải** địa chỉ bộ nhớ cụ thể, cấu trúc vùng nhớ hay cách JVM phải triển khai nội bộ. Java không cung cấp địa chỉ bộ nhớ đối tượng thông qua biến `String`.

| Khía cạnh | Nội dung chuỗi | Biến tham chiếu `String` |
|---|---|---|
| Câu hỏi cần trả lời | Chuỗi biểu diễn những ký tự nào? | Biến đang trỏ đến đối tượng nào (hay `null`)? |
| Ví dụ | `"Java"` | `text` trong `String text = "Java";` |
| So sánh thường dùng | `equals()` | `==` kiểm tra hai tham chiếu có cùng đối tượng |
| Có thể thay đổi bằng phép gán? | Không sửa được nội dung của một `String` hiện hữu | Có, biến có thể được gán tham chiếu khác |
| Có thể bằng `null`? | Không: `null` không phải một chuỗi có nội dung | Có: biến tham chiếu có thể chứa `null` |

## 2. Gán tham chiếu không tạo bản sao chuỗi

```java
String first = "Java";
String alias = first;
```

Sau phép gán `alias = first`, hai biến đang **cùng tham chiếu đến một đối tượng `String`**. Phép gán giữa hai biến tham chiếu không tạo ra một đối tượng mới và cũng không sao chép các ký tự sang một đối tượng khác.

```text
first -----+
           +----> String có nội dung "Java"
alias -----+
```

Nếu thực hiện `first = "Kotlin";`, Java chỉ thay giá trị tham chiếu được giữ trong biến `first`. Biến `alias` vẫn tham chiếu đến chuỗi `"Java"`:

```text
first ----------> String có nội dung "Kotlin"
alias ----------> String có nội dung "Java"
```

Điều này khác với ý tưởng “đổi nội dung trong đối tượng ban đầu”. Phép gán lại một biến không tự thay đổi tham chiếu của các biến khác.

## 3. `String` bất biến: đổi giá trị biến khác với sửa nội dung đối tượng

Đối tượng `String` là **immutable**: sau khi được tạo, nội dung của chính đối tượng đó không thể bị thay đổi qua API của `String`. Các thao tác như `concat()`, `replace()`, `substring()` hoặc nối chuỗi có thể trả về một tham chiếu tới chuỗi có nội dung mong muốn; chúng không chỉnh sửa các ký tự bên trong đối tượng gốc. Một thao tác có thể trả về chính đối tượng cũ khi phù hợp (chẳng hạn nối với chuỗi rỗng), vì vậy không nên khẳng định mọi lời gọi đều tạo đối tượng mới.

```java
String a = "Java";
String b = a;

a = a.concat(" Core");

System.out.println(a); // Java Core
System.out.println(b); // Java
```

Ban đầu, `a` và `b` cùng trỏ đến `"Java"`. `concat(" Core")` cho kết quả là chuỗi `"Java Core"`; phép gán đặt tham chiếu kết quả vào `a`. `b` vẫn trỏ đến đối tượng `"Java"` ban đầu. Tương tự, `a += " Core"` không “sửa tại chỗ” đối tượng `String` gốc.

Điểm thực hành: khi muốn giữ kết quả của các thao tác trả về chuỗi, hãy sử dụng giá trị được trả về, ví dụ `text = text.trim();`. Chỉ gọi `text.trim();` mà bỏ qua kết quả sẽ không cập nhật biến `text`.

## 4. So sánh: `==` hỏi “cùng đối tượng?”, `equals()` hỏi “cùng nội dung?”

Với hai biến kiểu `String`:

- `a == b` là **so sánh tham chiếu**: `true` nếu cả hai đang trỏ đến cùng một đối tượng, hoặc cả hai cùng là `null`.
- `a.equals(b)` là **so sánh nội dung** theo định nghĩa của `String.equals`: `true` khi `b` là chuỗi có cùng dãy ký tự; phép so sánh có phân biệt chữ hoa/chữ thường. Nếu `a` là `null` thì lời gọi `a.equals(b)` ném `NullPointerException`.
- Có thể có **cùng nội dung nhưng khác đối tượng**. Ngược lại, nếu hai tham chiếu cùng trỏ đến một đối tượng `String` thì nội dung hiển nhiên giống nhau.

Ví dụ hoàn chỉnh, chạy được trên Java 17+:

**Tệp `StringReferenceDemo.java`**

```java
public class StringReferenceDemo {
    public static void main(String[] args) {
        String first = "Java";
        String alias = first;
        String separate = new String("Java");
        String sameLiteral = "Java";

        System.out.println("first == alias: " + (first == alias));
        System.out.println("first == separate: " + (first == separate));
        System.out.println("first.equals(separate): " + first.equals(separate));
        System.out.println("first == sameLiteral: " + (first == sameLiteral));

        first = first.concat(" Core");
        System.out.println("first: " + first);
        System.out.println("alias: " + alias);
        System.out.println("separate: " + separate);
        System.out.println("first == alias sau gan lai: " + (first == alias));
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```bash
javac StringReferenceDemo.java
java StringReferenceDemo
```

**Đầu ra:**

```text
first == alias: true
first == separate: false
first.equals(separate): true
first == sameLiteral: true
first: Java Core
alias: Java
separate: Java
first == alias sau gan lai: false
```

Diễn giải từng điểm quan trọng:

1. `alias = first` sao chép tham chiếu nên `first == alias` là `true`.
2. `new String("Java")` chủ động tạo một đối tượng `String` mới, khác đối tượng từ literal `"Java"`. Hai đối tượng vẫn cùng nội dung nên `==` là `false` và `equals()` là `true`.
3. Hai lần sử dụng literal `"Java"` dùng chung đối tượng đã intern, nên `first == sameLiteral` là `true` tại thời điểm so sánh.
4. Sau `first = first.concat(" Core")`, `first` giữ tham chiếu tới kết quả có nội dung `"Java Core"`; `alias` và `separate` vẫn có nội dung `"Java"`. Vì `first` đã được gán lại, `first == alias` là `false`.

**Quy tắc sử dụng:** nếu yêu cầu nghiệp vụ là kiểm tra hai chuỗi có cùng văn bản hay không (tên, mã, dữ liệu nhập, trạng thái biểu diễn bằng chuỗi), thường dùng `equals()`; không lấy kết quả `==` làm tiêu chí so sánh nội dung.

## 5. String pool: vì sao đôi khi `==` trả về `true`?

Java lưu các string literal và những chuỗi được intern để có thể tái sử dụng cùng một đối tượng. Cơ chế này giúp giải thích hiện tượng hai literal có cùng nội dung thường có cùng tham chiếu. Trong khi đó, `new String("Java")` tạo đối tượng mới cho biểu thức `new`.

Trình biên dịch còn có thể tính trước **biểu thức hằng** bằng phép nối các literal. Ngược lại, nối chuỗi từ dữ liệu được xác định lúc chạy tạo kết quả theo luồng thực thi; đừng dựa vào tham chiếu trùng nhau để kết luận hai nội dung bằng nhau.

**Tệp `StringPoolDemo.java`**

```java
public class StringPoolDemo {
    public static void main(String[] args) {
        String literal = "Java";
        String folded = "Ja" + "va";
        String prefix = new String("Ja");
        String computed = prefix + "va";

        System.out.println(literal == folded);
        System.out.println(literal.equals(computed));
        System.out.println(literal == computed);
        System.out.println(literal == computed.intern());
    }
}
```

```bash
javac StringPoolDemo.java
java StringPoolDemo
```

**Đầu ra với Java 17+:**

```text
true
true
false
true
```

- `"Ja" + "va"` là biểu thức hằng và được gộp thành literal `"Java"` khi biên dịch: `literal == folded` là `true`.
- `prefix` là giá trị có đối tượng được tạo lúc chạy bằng `new`. `computed` có cùng ký tự với `literal`, vì vậy `equals()` là `true`, nhưng kết quả phép nối lúc chạy không tự động trở thành cùng đối tượng đã intern với literal.
- `computed.intern()` trả về tham chiếu chuẩn tương ứng trong pool; vì literal `"Java"` đã tồn tại nên so sánh tham chiếu với `literal` là `true`.

Cơ chế pool giải thích sự khác nhau giữa các biểu thức, **không phải** lý do dùng `==` để so nội dung. Trong mã ứng dụng, việc tạo `new String(...)` chỉ nhằm kiểm tra sự khác biệt tham chiếu thường là thừa; không cần cố ép các chuỗi vào pool để so sánh đúng.

## 6. `null`, chuỗi rỗng và so sánh an toàn

`null` có nghĩa là biến **không tham chiếu đến một đối tượng nào**. Nó khác chuỗi rỗng `""`: chuỗi rỗng là một đối tượng `String` hợp lệ, có độ dài `0`.

```java
String missing = null;
String empty = "";

System.out.println(missing == null); // true
System.out.println(empty == null);   // false
System.out.println(empty.length());  // 0
```

Gọi `missing.length()` hay `missing.equals("Java")` sẽ ném `NullPointerException` vì không có đối tượng để gọi phương thức.

Hai cách so sánh nội dung an toàn khi có thể có `null`:

- Với một literal chắc chắn không null, đảo phía gọi: `"Java".equals(candidate)`; nếu `candidate` là `null`, kết quả là `false`.
- Với hai biến đều có thể null, dùng `Objects.equals(left, right)`. Phương thức trả về `true` khi cả hai `null` hoặc khi hai giá trị không null bằng nhau theo `equals()`.

**Tệp `SafeStringCompareDemo.java`**

```java
import java.util.Objects;

public class SafeStringCompareDemo {
    public static void main(String[] args) {
        String candidate = null;

        System.out.println(candidate == null);
        System.out.println("Java".equals(candidate));
        System.out.println(Objects.equals(candidate, null));
        System.out.println("".equals(candidate));
    }
}
```

```bash
javac SafeStringCompareDemo.java
java SafeStringCompareDemo
```

**Đầu ra:**

```text
true
false
true
false
```

Lưu ý: `Objects.equals(null, null)` là `true` vì hai giá trị cùng không có tham chiếu; điều đó **không** có nghĩa `null` là một chuỗi chứa văn bản.

## 7. Lỗi thường gặp và cách tránh

| Lỗi hoặc hiểu nhầm | Vì sao sai? | Cách làm đúng |
|---|---|---|
| `if (input == "yes")` để kiểm tra văn bản người dùng nhập | `input` có thể trỏ tới đối tượng khác literal dù nội dung là `"yes"` | `"yes".equals(input)` |
| Cho rằng `String copy = original;` tạo bản sao nội dung | Phép gán sao chép giá trị tham chiếu | Hiểu rằng cả hai cùng trỏ một đối tượng; `String` bất biến nên không cần sao chép phòng ngừa việc sửa nội dung |
| Cho rằng `text = text + "!"` thay đổi đối tượng cũ | `text` được gán tham chiếu tới kết quả nối | Phân biệt gán lại biến với sửa đối tượng |
| Gọi `text.trim();` rồi mong `text` thay đổi | `trim()` trả về kết quả, không sửa `String` gốc | `text = text.trim();` |
| Thấy `"a" == "a"` là `true` và suy ra `==` luôn so ký tự | Hai literal có thể được dùng chung nhờ string pool | Dùng `equals()` hoặc `Objects.equals()` cho nội dung |
| Gọi `text.equals(other)` khi `text` có thể null | Truy cập phương thức qua tham chiếu `null` gây `NullPointerException` | Kiểm tra null, đảo phía literal, hoặc dùng `Objects.equals()` |
| Xem `null` và `""` là một | `null` không trỏ đến đối tượng, `""` là chuỗi hợp lệ với 0 ký tự | Xử lý riêng theo yêu cầu nghiệp vụ |

## 8. Tự kiểm tra

Dự đoán kết quả trước khi chạy đoạn mã sau:

```java
String x = "cat";
String y = x;
String z = new String("cat");

x = "dog";

System.out.println(y);
System.out.println(x == y);
System.out.println(y.equals(z));
System.out.println(y == z);
```

**Đáp án theo thứ tự:** `cat`, `false`, `true`, `false`.

Giải thích: `y` giữ tham chiếu ban đầu tới `"cat"`; gán `"dog"` cho `x` không ảnh hưởng `y`. `z` là đối tượng khác nhưng có cùng nội dung với `y`.

Khi đọc bất kỳ đoạn mã liên quan đến `String`, hãy tự hỏi theo đúng thứ tự: **(1) Biến hiện trỏ đến đối tượng nào? (2) Nội dung của đối tượng đó là gì? (3) Phép toán đang kiểm tra tham chiếu, kiểm tra nội dung, hay đang gán lại tham chiếu?** Ba câu hỏi này giúp phân biệt rõ “chuỗi chứa gì” với “biến đang trỏ đến đâu”, kể cả khi string pool hoặc phép nối chuỗi xuất hiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-overview-004">Chuỗi rỗng, chuỗi trắng, null và chuỗi chứa chữ null</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi rỗng, chuỗi trắng, `null` và chuỗi `"null"` trong Java

Trong Java, một biến kiểu `String` có thể không tham chiếu đến đối tượng nào (`null`), tham chiếu đến chuỗi không có ký tự (`""`), đến chuỗi chỉ chứa khoảng trắng (`"   "`), hoặc đến một chuỗi có bốn chữ cái `"null"`. Đây là bốn trường hợp khác nhau về **dữ liệu và cách xử lý**. Phân biệt đúng giúp kiểm tra đầu vào, thiết kế API, xử lý cấu hình và ghi log chính xác.

## 1. Hiểu từng trường hợp

### 1.1. Tham chiếu `null`: không có đối tượng `String`

`null` là literal đặc biệt biểu thị việc biến tham chiếu chưa trỏ tới đối tượng. Nó **không phải là một chuỗi**.

```java
String name = null;
System.out.println(name == null); // true
// name.length(); // NullPointerException nếu chạy
```

Biểu thức `name == null` kiểm tra trạng thái tham chiếu. Không thể gọi trực tiếp `length()`, `isEmpty()`, `isBlank()` hay `strip()` trên tham chiếu `null`.

### 1.2. Chuỗi rỗng `""`: có đối tượng, độ dài bằng 0

```java
String empty = "";
System.out.println(empty.length());  // 0
System.out.println(empty.isEmpty()); // true
System.out.println(empty.isBlank()); // true
```

`""` là giá trị chuỗi hợp lệ không chứa ký tự nào. Nó khác `null`. Một chuỗi rỗng cũng được `isBlank()` coi là blank vì không có ký tự nào khác whitespace.

### 1.3. Chuỗi trắng (blank): chỉ gồm các ký tự whitespace

Ví dụ `" "`, `"   "`, `"\t"`, `"\n"` hay `" \t\n "`. Chúng chứa ký tự thật, nên thường **không rỗng**, nhưng `isBlank()` trả về `true` nếu mọi ký tự đều được Java nhận diện là whitespace.

```java
String spaces = " \t";
System.out.println(spaces.length());  // 2
System.out.println(spaces.isEmpty()); // false
System.out.println(spaces.isBlank()); // true
```

Lưu ý: “Nhìn có vẻ là khoảng trắng” không đồng nghĩa với `Character.isWhitespace`. Chẳng hạn NO-BREAK SPACE (`\u00A0`) không được `String.isBlank()` coi là whitespace, trong khi EM SPACE (`\u2003`) được coi là whitespace. Vì vậy, kiểm tra blank không phải phép kiểm tra tổng quát mọi ký tự vô hình hay mọi dấu cách Unicode.

### 1.4. Chuỗi chứa chữ `"null"`: bốn ký tự thông thường

```java
String word = "null";
System.out.println(word == null);       // false
System.out.println(word.length());      // 4
System.out.println(word.isBlank());     // false
System.out.println("null".equals(word)); // true
```

Đây là văn bản gồm `'n'`, `'u'`, `'l'`, `'l'`. Java không tự biến chuỗi `"null"` thành tham chiếu `null`. Ví dụ `"NULL"` hoặc `" Null "` cũng là các chuỗi khác `"null"` khi so sánh bằng `equals`.

## 2. Bảng so sánh nhanh

Các hàng dưới đây là trạng thái độc lập của biến `s`; cột “bằng chữ null” dùng `"null".equals(s)`.

| Giá trị của `s` | `s == null` | `s != null && s.isEmpty()` | `s != null && s.isBlank()` | `"null".equals(s)` |
| --- | --- | --- | --- | --- |
| `null` | `true` | `false` | `false` | `false` |
| `""` | `false` | `true` | `true` | `false` |
| `" \t"` | `false` | `false` | `true` | `false` |
| `"null"` | `false` | `false` | `false` | `true` |
| `"Java"` | `false` | `false` | `false` | `false` |

**Điểm mấu chốt:** `isEmpty()` chỉ kiểm tra độ dài 0; `isBlank()` chấp nhận cả độ dài 0 lẫn chuỗi chỉ gồm whitespace. Cả hai đều cần bảo vệ khỏi `null`.

## 3. Những API cần biết

| Công cụ | Câu hỏi trả lời | Đặc điểm |
| --- | --- | --- |
| `s == null` | Có thiếu đối tượng chuỗi không? | An toàn cả khi `s` bằng `null` |
| `s.isEmpty()` | Có đúng 0 ký tự không? | `NullPointerException` khi `s == null` |
| `s.isBlank()` | Có rỗng hoặc chỉ gồm whitespace không? | Có từ Java 11; `NullPointerException` khi `s == null` |
| `s.trim()` | Bỏ các ký tự ở hai đầu có mã ≤ U+0020? | Quy tắc lịch sử, không tương đương mọi whitespace Unicode |
| `s.strip()` | Bỏ whitespace ở hai đầu theo `Character.isWhitespace`? | Có từ Java 11; thường phù hợp hơn `trim()` khi cần Unicode |
| `"null".equals(s)` | Giá trị có đúng chữ `"null"` không? | An toàn khi `s == null`; phân biệt hoa/thường |
| `Objects.equals(s, "null")` | Hai giá trị có bằng nhau, kể cả `null`? | So sánh `null` an toàn, cần `java.util.Objects` |

Dùng toán tử `||` theo cơ chế *short-circuit* để tránh gọi phương thức trên `null`:

```java
boolean missingOrBlank = s == null || s.isBlank();
boolean hasText = s != null && !s.isBlank();
```

Hai dòng này là **đoạn mã minh họa** khi đã có biến `String s`. Dòng đầu kiểm tra “không có văn bản hữu ích” theo định nghĩa `isBlank`; dòng thứ hai kiểm tra có ít nhất một ký tự không phải whitespace. Nếu nghiệp vụ cần phân biệt “không gửi trường” và “gửi trường rỗng”, đừng gom các trạng thái ấy vào một điều kiện.

## 4. Ví dụ Java 17 có thể biên dịch và chạy

Lưu nội dung sau vào tệp `StringStates.java`:

```java
public class StringStates {
    private static void show(String label, String s) {
        System.out.printf(
                "%s -> null=%b, empty=%b, blank=%b, textNull=%b%n",
                label,
                s == null,
                s != null && s.isEmpty(),
                s != null && s.isBlank(),
                "null".equals(s)
        );
    }

    public static void main(String[] args) {
        show("missing", null);
        show("empty", "");
        show("blank", " \t");
        show("literal", "null");
        show("normal", "Java");

        String emSpace = "\u2003";
        String nbsp = "\u00A0";
        System.out.printf(
                "EM SPACE -> blank=%b, trimLength=%d, stripLength=%d%n",
                emSpace.isBlank(),
                emSpace.trim().length(),
                emSpace.strip().length()
        );
        System.out.printf(
                "NBSP -> blank=%b, stripLength=%d%n",
                nbsp.isBlank(),
                nbsp.strip().length()
        );
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac StringStates.java
java StringStates
```

Kết quả chuẩn:

```text
missing -> null=true, empty=false, blank=false, textNull=false
empty -> null=false, empty=true, blank=true, textNull=false
blank -> null=false, empty=false, blank=true, textNull=false
literal -> null=false, empty=false, blank=false, textNull=true
normal -> null=false, empty=false, blank=false, textNull=false
EM SPACE -> blank=true, trimLength=1, stripLength=0
NBSP -> blank=false, stripLength=1
```

Ở đây các điều kiện có `s != null && ...` bảo vệ lời gọi phương thức. `EM SPACE` cho thấy `strip()` xử lý whitespace Unicode khác `trim()`. `NBSP` cho thấy `isBlank()` không tự nhận diện mọi ký tự trông giống khoảng trắng.

## 5. Ứng dụng: phân loại dữ liệu đầu vào mà không đánh mất ý nghĩa

Giả sử một trường văn bản được nhận từ API hoặc biểu mẫu:

- `null`: có thể có nghĩa “không được cung cấp”, tùy hợp đồng API.
- `""`: đã có một giá trị chuỗi nhưng độ dài bằng 0; có thể thể hiện ý định xóa dữ liệu.
- `"   "`: đã gửi ký tự nhưng không có nội dung chữ hữu ích.
- `"null"`: có thể là văn bản người dùng thực sự muốn nhập, **không tự coi là không có dữ liệu**.
- `"  Minh  "`: có chữ với khoảng trắng ở đầu/cuối; có thể cần chuẩn hóa trước khi lưu.

Có thể dùng phương thức phân loại sau **bên trong một lớp Java**:

```java
enum TextKind { MISSING, EMPTY, BLANK, LITERAL_NULL, TEXT }

static TextKind classify(String s) {
    if (s == null) {
        return TextKind.MISSING;
    }
    if (s.isEmpty()) {
        return TextKind.EMPTY;
    }
    if (s.isBlank()) {
        return TextKind.BLANK;
    }
    if ("null".equals(s)) {
        return TextKind.LITERAL_NULL;
    }
    return TextKind.TEXT;
}
```

**Thứ tự kiểm tra có ý nghĩa:** một chuỗi rỗng cũng là blank theo `isBlank()`, nên phải kiểm tra `isEmpty()` trước nếu muốn tách hai nhóm. Bước kiểm tra chữ `"null"` chỉ hữu ích khi bài toán cần nhận biết chính xác văn bản này; đừng biến nó thành quy tắc xóa dữ liệu một cách mặc định.

Nếu nghiệp vụ **chủ động quyết định** mọi chuỗi thiếu/rỗng/trắng đều được biểu diễn bằng `null`, có thể dùng đoạn phương thức sau (đặt trong một lớp):

```java
static String normalizeOptionalText(String s) {
    if (s == null) {
        return null;
    }
    String stripped = s.strip();
    return stripped.isEmpty() ? null : stripped;
}
```

Ví dụ: `normalizeOptionalText(null)`, `normalizeOptionalText("")` và `normalizeOptionalText("  \t ")` đều trả về `null`; `normalizeOptionalText("  Java  ")` trả về `"Java"`; `normalizeOptionalText("null")` vẫn trả về `"null"`. Đây là **lựa chọn theo yêu cầu nghiệp vụ**: không dùng nó khi phải giữ khác biệt giữa trường vắng mặt, trường rỗng và trường chỉ có khoảng trắng. Phương thức cũng tuân theo quy tắc Unicode của `strip()`, nên NBSP không tự bị loại bỏ.

## 6. Vì sao log hoặc chuyển kiểu có thể gây nhầm lẫn?

Java có thể tạo ra **chuỗi** `"null"` từ tham chiếu `null` trong phép nối chuỗi hoặc chuyển sang văn bản:

```java
String absent = null;
String literal = "null";

System.out.println("value=" + absent);                 // value=null
System.out.println("value=" + literal);                // value=null
System.out.println(String.valueOf((Object) absent));   // null
System.out.println(java.util.Objects.toString(absent, "<missing>")); // <missing>
```

Hai dòng đầu có đầu ra giống hệt nhau, dù đầu vào khác nhau. `String.valueOf((Object) absent)` trả về chuỗi `"null"` (không phải tham chiếu `null`). Ép kiểu `(Object)` trong ví dụ làm rõ overload muốn gọi; tránh nhầm với các overload như `valueOf(char[])`. Muốn log phân biệt, ghi trạng thái riêng (`absent == null`) hoặc dùng một dấu thay thế nhất quán như `"<missing>"`. Nếu cần truyền dữ liệu qua JSON, hãy giữ rõ sự khác nhau giữa giá trị `null`, chuỗi `"null"`, chuỗi `""` và thuộc tính vắng mặt theo hợp đồng dữ liệu.

## 7. Các lỗi thường gặp

1. **Gọi phương thức trực tiếp trên `null`:** `s.isEmpty()`, `s.isBlank()`, `s.trim()` gây `NullPointerException` nếu `s == null`. Kiểm tra tham chiếu trước khi gọi.
2. **Dùng `isEmpty()` để chặn chuỗi trắng:** `"   ".isEmpty()` trả về `false`. Nếu muốn chặn đầu vào chỉ chứa whitespace, dùng `isBlank()` trên giá trị khác `null`.
3. **Dùng `s.equals("null")` khi `s` có thể `null`:** đổi thành `"null".equals(s)` hoặc `Objects.equals(s, "null")`.
4. **So sánh chuỗi bằng `==`:** `==` so sánh tham chiếu đối tượng (ngoại trừ cách dùng với `null`), không phải nội dung chữ; dùng `equals` cho nội dung. Không dựa vào string pool để kiểm tra.
5. **Dùng `trim().isEmpty()` mà cho rằng giống `isBlank()`:** chúng có quy tắc whitespace khác nhau; Unicode có những trường hợp cho kết quả khác.
6. **Tự coi chữ `"null"` là dữ liệu thiếu:** chỉ được chuyển đổi khi có quy ước rõ ràng; nếu không sẽ làm mất văn bản hợp lệ của người dùng.
7. **Chuẩn hóa quá sớm:** gộp mọi trạng thái vào `null` hoặc `""` có thể làm mất thông tin phân biệt “không gửi”, “xóa trống” và “chỉ gửi khoảng trắng”.

## 8. Cách chọn điều kiện cho đúng yêu cầu

| Yêu cầu | Điều kiện hoặc cách làm |
| --- | --- |
| Chỉ phát hiện không có tham chiếu | `s == null` |
| Chỉ phát hiện chuỗi có đúng 0 ký tự | `s != null && s.isEmpty()` |
| Phát hiện chuỗi trắng, kể cả rỗng (không tính `null`) | `s != null && s.isBlank()` |
| Phát hiện `null` hoặc chuỗi rỗng | `s == null || s.isEmpty()` |
| Phát hiện `null` hoặc chuỗi blank | `s == null || s.isBlank()` |
| Phát hiện chính xác văn bản `"null"` | `"null".equals(s)` |
| Cần giữ được cả bốn trạng thái riêng biệt | Kiểm tra theo thứ tự: `null` → `isEmpty()` → `isBlank()` → `"null".equals(s)` |
| Muốn cắt whitespace đầu/cuối theo Java 11+ | `s == null ? null : s.strip()`; cân nhắc quy tắc Unicode |

**Tự kiểm tra:** Dự đoán `isEmpty()` và `isBlank()` cho `""`, `"  "`, `"abc"`; sau đó thử `null`, `"null"` và ký tự `\u00A0`. Nếu kết quả gây ngạc nhiên, hãy kiểm tra trước xem bạn đang nói về **tham chiếu**, **độ dài**, **whitespace theo Java** hay **nội dung văn bản**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-overview-005">Vì sao String là lớp final và có tính bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `String` là lớp `final` và có tính bất biến trong Java?

`String` là kiểu tham chiếu dùng để biểu diễn chuỗi ký tự trong Java. Khai báo của lớp trong Java 17+ có dạng `public final class String ...`. Tài liệu Java xác định rõ: **giá trị của một đối tượng `String` không thể thay đổi sau khi đối tượng được tạo** (*immutable*). Hai đặc điểm `final` và *immutable* bổ trợ nhau để chuỗi có thể được chia sẻ an toàn, có hành vi ổn định và được tối ưu hiệu quả.

Sau bài này, bạn cần phân biệt được `final` của lớp, `final` của biến tham chiếu và tính bất biến của đối tượng; giải thích tại sao các thao tác trên chuỗi không sửa chuỗi gốc; và vận dụng điều đó khi so sánh chuỗi, sử dụng String Pool, làm khóa `HashMap` hoặc xử lý chuỗi trong chương trình đa luồng.

## 1. `final` và immutable là hai khái niệm khác nhau

| Khái niệm | Điều được đảm bảo | Điều **không** tự động được đảm bảo |
| --- | --- | --- |
| `final class` | Không thể khai báo lớp con kế thừa lớp đó. | Đối tượng của lớp không nhất thiết bất biến. |
| `final` trên biến tham chiếu | Không thể gán lại biến để trỏ đến đối tượng khác sau khi khởi tạo. | Đối tượng mà biến trỏ tới vẫn có thể thay đổi nếu loại của nó cho phép. |
| Đối tượng immutable | Trạng thái có thể quan sát của đối tượng không đổi sau khi tạo. | Bản thân lớp không bắt buộc phải khai báo `final` trong mọi thiết kế. |

**`String` là `final`** nghĩa là đoạn `class MyString extends String` không hợp lệ: trình biên dịch báo lỗi vì không thể kế thừa lớp final. Đây là một ràng buộc ở cấp **kiểu dữ liệu**.

**`String` là immutable** nghĩa là sau khi tạo `String s = "Java";`, nội dung của đối tượng `"Java"` không thể bị thay thành `"Kava"` qua API thông thường. Đây là một cam kết về **trạng thái đối tượng**.

Ví dụ sau minh họa rằng `final class` một mình không khiến đối tượng bất biến (đoạn định nghĩa lớp, không có hàm `main`):

```java
final class Counter {
    private int value;

    void increment() {
        value++; // Trạng thái của Counter vẫn thay đổi.
    }

    int value() {
        return value;
    }
}
```

Ngược lại, dù không viết `final String s`, đối tượng `String` vẫn bất biến:

```java
String s = "Java";
s = "Kotlin"; // Hợp lệ: biến s được gán sang một đối tượng String khác.
```

Nếu khai báo `final String s = "Java";` thì `s = "Kotlin";` là lỗi biên dịch do **gán lại tham chiếu**. Nó không làm đối tượng `"Java"` “bất biến hơn” vì đối tượng đó vốn đã bất biến. Tương tự, `final StringBuilder builder = new StringBuilder("A");` vẫn cho phép `builder.append("B")`, bởi `StringBuilder` có trạng thái thay đổi được.

## 2. `String` giữ tính bất biến bằng cách nào?

Một lớp muốn cung cấp tính bất biến cần bảo vệ trạng thái nội tại và không cung cấp đường sửa trạng thái đó từ bên ngoài. `String` thực hiện điều này thông qua hợp đồng API và cách cài đặt của thư viện Java:

1. **Không cung cấp phương thức sửa nội dung tại chỗ.** Không có `setCharAt`, `append` hay `delete` trên `String`. Các phương thức `concat`, `replace`, `substring`, `toUpperCase`, `toLowerCase`… trả về kết quả dạng `String`; chúng không đổi nội dung của đối tượng nhận lời gọi.
2. **Không để mã gọi can thiệp vào bộ nhớ chứa nội dung chuỗi.** Chẳng hạn, hàm tạo `String(char[])` sao chép nội dung mảng truyền vào. `toCharArray()` trả về một mảng mới; sửa mảng đầu vào hay đầu ra đó không sửa được chuỗi.
3. **Chặn kế thừa bằng `final`.** Không có lớp con nào có thể thêm trạng thái biến đổi hoặc ghi đè hành vi để phá vỡ hợp đồng của `String` khi đối tượng được dùng qua kiểu `String`.
4. **Giữ trạng thái nội tại dưới quyền kiểm soát của thư viện/JVM.** Ở các JDK hiện đại, biểu diễn nội bộ có thể được tối ưu mà không làm thay đổi ý nghĩa của API công khai.

`final` **không tự tạo ra tính bất biến**; thiết kế không có đường sửa giá trị mới là nền tảng. Tuy vậy, `final` giúp Java đảm bảo mọi đối tượng có kiểu `String` đều tuân theo đúng hợp đồng của lớp, tránh việc mã phía ngoài tạo một lớp con có hành vi bất ngờ.

### Biểu diễn nội bộ không phải hợp đồng công khai

Nhiều tài liệu Java 8 mô tả `String` chứa mảng `char[]`. Kể từ Java 9, OpenJDK áp dụng **Compact Strings** (JEP 254): nội dung có thể lưu bằng `byte[]` cùng thông tin mã hóa Latin-1 hoặc UTF-16 để giảm bộ nhớ khi thích hợp. Đây là chi tiết cài đặt; **không nên suy luận tính bất biến chỉ từ việc tồn tại `final byte[]`**. Một biến `final` trỏ đến mảng vẫn có thể trỏ tới mảng có các phần tử bị sửa nếu quyền truy cập không được bảo vệ. Tính bất biến của `String` đến từ toàn bộ thiết kế và hợp đồng của nó.

## 3. Thực nghiệm: thao tác tạo kết quả mới, chuỗi gốc giữ nguyên

Tạo tệp `StringImmutabilityDemo.java`:

```java
import java.util.Locale;

public class StringImmutabilityDemo {
    public static void main(String[] args) {
        String source = "Java";
        String extended = source.concat(" 17");

        System.out.println("source = " + source);
        System.out.println("extended = " + extended);
        System.out.println("same object = " + (source == extended));

        String lower = source.toLowerCase(Locale.ROOT);
        System.out.println("lower = " + lower);
        System.out.println("source after toLowerCase = " + source);

        char[] input = {'J', 'a', 'v', 'a'};
        String copied = new String(input);
        input[0] = 'X';
        System.out.println("copied after input changed = " + copied);

        char[] output = copied.toCharArray();
        output[0] = 'Y';
        System.out.println("copied after output changed = " + copied);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac StringImmutabilityDemo.java
java StringImmutabilityDemo
```

Kết quả:

```text
source = Java
extended = Java 17
same object = false
lower = java
source after toLowerCase = Java
copied after input changed = Java
copied after output changed = Java
```

Diễn giải:

- `source.concat(" 17")` trả về chuỗi có nội dung khác. `source` vẫn là `"Java"`, còn `extended` chứa `"Java 17"`; vì khác nội dung, chúng chắc chắn không phải cùng một đối tượng.
- `toLowerCase(Locale.ROOT)` trả về kết quả chữ thường mà không sửa `source`. Với các phương thức có kết quả **không đổi** (ví dụ nối chuỗi rỗng), cài đặt **có thể trả lại chính đối tượng hiện tại**. Vì thế không được khẳng định “mỗi lệnh trên `String` luôn cấp phát một đối tượng mới”.
- Thay đổi `input` sau hàm tạo, hay thay đổi mảng do `toCharArray()` trả về, đều không ảnh hưởng đến `copied`. Đây là ví dụ trực tiếp về việc không để lộ trạng thái nội bộ có thể sửa.

Câu lệnh `s += "!"` cũng cần được hiểu đúng: nó tương đương về kết quả với `s = s + "!"`, nghĩa là biểu thức tạo giá trị chuỗi đã nối rồi **gán lại biến `s`**; đối tượng chuỗi cũ không bị thay đổi. Nếu nhiều biến cùng tham chiếu chuỗi cũ, chúng vẫn thấy nội dung cũ.

## 4. Vì sao Java thiết kế `String` vừa `final` vừa immutable?

### 4.1. Chia sẻ chuỗi và String Pool an toàn

Java sử dụng **String Pool** để tái sử dụng các đối tượng biểu diễn cùng hằng chuỗi, đặc biệt là các literal như `"java"`. Nếu một bên có thể sửa nội dung của một đối tượng đã được chia sẻ, những nơi khác dùng chung đối tượng đó sẽ bị ảnh hưởng ngoài ý muốn. Tính bất biến khiến việc chia sẻ trở nên an toàn.

Ví dụ hoàn chỉnh, lưu dưới tên `StringPoolDemo.java`:

```java
public class StringPoolDemo {
    public static void main(String[] args) {
        String a = "java";
        String b = "java";
        String c = new String("java");
        String canonical = c.intern();

        System.out.println("a == b: " + (a == b));
        System.out.println("a == c: " + (a == c));
        System.out.println("a.equals(c): " + a.equals(c));
        System.out.println("a == canonical: " + (a == canonical));
    }
}
```

Chạy:

```bash
javac StringPoolDemo.java
java StringPoolDemo
```

Kết quả:

```text
a == b: true
a == c: false
a.equals(c): true
a == canonical: true
```

- Hai literal `"java"` trong ví dụ cùng trỏ đến đối tượng chuỗi đã được intern.
- `new String("java")` tạo một đối tượng `String` khác có **cùng nội dung**.
- `intern()` trả về tham chiếu chuẩn của chuỗi tương ứng trong pool.
- Dùng `equals()` để kiểm tra **nội dung**; `==` so sánh **tham chiếu**. Đừng dùng `==` để so sánh nội dung chuỗi lấy từ dữ liệu người dùng, file hay API.

Lưu ý: biểu thức nối **hằng chuỗi lúc biên dịch** có thể trở thành một literal; nối từ dữ liệu ở thời điểm chạy có cơ chế tạo kết quả khác. Không suy đoán kết quả `==` chỉ dựa vào cách viết biểu thức nối.

### 4.2. Khóa `HashMap` và `HashSet` có ý nghĩa ổn định

Cấu trúc băm dùng `hashCode()` và `equals()` để tổ chức và tra cứu phần tử. Khi một `String` được dùng làm khóa, nội dung không đổi nên giá trị băm và kết quả so sánh theo nội dung của khóa cũng ổn định. Nhờ đó, một khóa không “đổi chỗ” về mặt logic sau khi đưa vào bảng băm.

Ví dụ nhỏ trong một phương thức Java:

```java
Map<String, Integer> counts = new HashMap<>();
String key = "user:42";
counts.put(key, 1);

key = key.concat(":active"); // Gán biến key sang chuỗi khác.
System.out.println(counts.get("user:42"));    // 1
System.out.println(counts.get(key));          // null
```

Đoạn này cần `import java.util.Map;` và `import java.util.HashMap;` nếu đặt trong một lớp độc lập. Bản thân khóa `"user:42"` trong `counts` vẫn nguyên vẹn. Việc lưu mã băm để tái sử dụng trong một số cài đặt `String` cũng khả thi vì nội dung không đổi; cách cache cụ thể là chi tiết triển khai.

Với **một đối tượng khóa có trường dùng trong `equals()/hashCode()` bị sửa sau khi đưa vào `HashMap`**, phép tra cứu có thể thất bại. `String` tránh hẳn nhóm lỗi này ở mức nội dung chuỗi.

### 4.3. Dễ chia sẻ giữa các luồng

Nhiều luồng có thể đồng thời **đọc và dùng chung cùng một đối tượng `String`** mà không cần khóa để bảo vệ việc sửa nội dung: không luồng nào có thể đổi nội dung đối tượng đó qua API bình thường. Điều này giúp giảm số tình huống race condition liên quan đến trạng thái của đối tượng.

Nhưng **immutable không đồng nghĩa toàn bộ chương trình tự động thread-safe**. Ví dụ, nếu hai luồng cùng gán lại một trường `String currentName`, việc công bố giá trị mới, tính nhìn thấy giữa luồng và các thao tác nhiều bước vẫn có thể cần `volatile`, `synchronized` hoặc cơ chế đồng bộ thích hợp. Tính bất biến chỉ đảm bảo nội dung của **từng đối tượng `String`** không bị sửa.

### 4.4. Duy trì hợp đồng API và hạn chế thay đổi dữ liệu ngoài ý muốn

`String` thường biểu diễn tên tệp, đường dẫn, khóa cấu hình, URL, định danh hoặc tham số đã được kiểm tra. Một hàm nhận `String` có thể tin rằng **nội dung chính đối tượng ấy** không bị caller lặng lẽ sửa sau khi truyền vào. Nếu cho phép lớp con thay đổi cách hành xử hoặc cho phép sửa trạng thái, các giả định của API, mã băm và nơi chia sẻ tham chiếu đều có thể bị phá vỡ.

Lợi ích này không thay thế việc kiểm tra đầu vào, phân quyền hoặc đồng bộ khi có dữ liệu mới được gán vào biến. Tính bất biến cũng không tự làm cho giá trị người dùng nhập trở nên an toàn.

## 5. Khi làm việc với `String`, nên chọn API nào?

| Nhu cầu | Phù hợp | Lý do |
| --- | --- | --- |
| Giá trị văn bản ổn định, truyền giữa các hàm, key của map | `String` | Dễ chia sẻ, không có phép sửa nội dung tại chỗ. |
| Tạo một vài biểu thức nối đơn giản | Toán tử `+` với `String` | Rõ ràng, trình biên dịch/JVM có thể tối ưu; không cần tự tối ưu sớm. |
| Ghép nhiều phần trong vòng lặp hoặc dựng văn bản từng bước | `StringBuilder` | Có `append`, `insert`, `delete` thay đổi vùng đệm; thường tránh tạo nhiều giá trị trung gian. |
| Bộ đệm văn bản mutable dùng chung giữa các luồng và cần phương thức đồng bộ | `StringBuffer` trong ngữ cảnh thích hợp | Các phương thức chính có đồng bộ; thao tác ghép nhiều bước vẫn cần xét tính nguyên tử. |

Ví dụ dựng chuỗi qua vòng lặp bằng `StringBuilder`:

```java
StringBuilder builder = new StringBuilder();
for (int i = 1; i <= 3; i++) {
    if (i > 1) {
        builder.append(", ");
    }
    builder.append(i);
}
String result = builder.toString(); // "1, 2, 3"
```

`StringBuilder` thay đổi trạng thái trong lúc dựng kết quả. `toString()` trả về `String` bất biến để dùng hoặc chia sẻ tiếp. `StringBuilder` không tự bảo đảm an toàn khi nhiều luồng cùng sửa một instance.

## 6. Các ngộ nhận và lỗi phổ biến

1. **“`final` là đủ để một đối tượng bất biến.”** Sai: ví dụ `Counter` ở trên là lớp `final` nhưng có trường `value` được tăng.
2. **“`final String s` có nghĩa là nội dung mới bất biến.”** Sai: mọi `String` đều bất biến; `final` trên `s` chỉ cấm gán lại tham chiếu.
3. **“`replace()` hoặc `toUpperCase()` sẽ sửa biến gốc.”** Sai: phải nhận kết quả nếu muốn sử dụng, ví dụ `s = s.replace("a", "b");`. Gọi mà bỏ giá trị trả về không thay chuỗi đang giữ.
4. **“Mọi phương thức xử lý `String` đều tạo instance mới.”** Sai: phương thức có thể trả về chính instance nếu kết quả không khác (và literal được pool chia sẻ).
5. **“Hai chuỗi bằng nội dung thì `==` luôn `true`.”** Sai: `==` hỏi có cùng tham chiếu không; `equals()` mới so sánh nội dung theo API của `String`.
6. **“String Pool là nguyên nhân khiến `String` bất biến.”** Quan hệ thực tế đi theo hướng ngược lại: tính bất biến **cho phép** pool chia sẻ đối tượng mà không sợ bên dùng này sửa dữ liệu bên dùng kia.
7. **“Có `String` thì không cần quan tâm đồng bộ.”** Sai với những biến hoặc cấu trúc dữ liệu tham chiếu đến `String` bị nhiều luồng cập nhật.
8. **“Độ dài `String` luôn bằng số ký tự người dùng nhìn thấy.”** `length()` đếm **UTF-16 code units**; một ký tự Unicode ngoài BMP có thể chiếm hai đơn vị. Dùng `codePointCount(0, s.length())` khi cần đếm code point; ký tự hiển thị với dấu ghép vẫn là vấn đề riêng.
9. **“Mật khẩu luôn nên lưu trong `String`.”** Vì nội dung `String` không thể xóa tại chỗ, ứng dụng xử lý bí mật nhạy cảm có thể ưu tiên `char[]` hoặc `byte[]` khi giao thức/thư viện cho phép xóa vùng đệm sau sử dụng. Việc đó vẫn phải đi kèm quản lý vòng đời dữ liệu và cơ chế bảo mật phù hợp.

## 7. Cách tự kiểm tra mức độ hiểu

Xét đoạn mã:

```java
String x = "Hi";
String y = x;
x = x.concat(" Java");

System.out.println(x);
System.out.println(y);
System.out.println(x == y);
```

Kết quả là:

```text
Hi Java
Hi
false
```

Giải thích: lúc đầu `x` và `y` cùng tham chiếu `"Hi"`. Phép nối tạo giá trị `"Hi Java"`; chỉ biến `x` được gán tham chiếu kết quả. `y` tiếp tục giữ chuỗi `"Hi"` và đối tượng cũ không thay đổi.

**Ghi nhớ ba tầng:** `final class String` chặn kế thừa; tính bất biến bảo vệ nội dung từng đối tượng; còn `final String variable` chỉ bảo vệ việc gán lại một biến tham chiếu. Thiết kế kết hợp này giúp `String` chia sẻ an toàn trong pool và giữa các luồng, giữ khóa băm ổn định và khiến luồng dữ liệu văn bản dễ suy luận hơn.

## Tài liệu tham khảo

- [Java SE 21 API — `java.lang.String`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html): khai báo `final`, đặc tính bất biến, hàm tạo, `equals`, `intern` và các API chuỗi.
- [OpenJDK JEP 254 — Compact Strings](https://openjdk.org/jeps/254): thay đổi biểu diễn nội bộ từ Java 9; không thay đổi giao diện hoặc hợp đồng bất biến.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-overview-006">Các nhóm thao tác: đọc, so sánh, tìm kiếm, biến đổi và chuyển mã</a>

<details>
<summary>Xem chi tiết</summary>

# Các nhóm thao tác với String trong Java: đọc, so sánh, tìm kiếm, biến đổi và chuyển mã

Trong Java, `String` là kiểu tham chiếu biểu diễn văn bản. Năm nhóm thao tác thường gặp gồm: **đọc/trích xuất**, **so sánh**, **tìm kiếm**, **biến đổi** và **chuyển đổi/mã hóa**. Mỗi nhóm giải quyết một mục đích khác nhau; chọn đúng API giúp code dễ hiểu và tránh lỗi với Unicode, biểu thức chính quy hoặc dữ liệu từ hệ thống ngoài.

## 1. Quy tắc nền tảng trước khi thao tác

1. **`String` bất biến (immutable):** `replace()`, `strip()`, `substring()`, `toUpperCase()` trả về kết quả nhưng không sửa chuỗi gốc. Phải gán `s = s.strip();` nếu muốn lưu giá trị sau biến đổi. Nếu không có thay đổi, một phương thức có thể trả về cùng đối tượng; không dựa vào danh tính tham chiếu để quyết định nội dung.
2. **Chỉ số bắt đầu từ 0, biên cuối không bao gồm:** `"Java".substring(1, 3)` là `"av"`. Giá trị chỉ số không hợp lệ gây `StringIndexOutOfBoundsException`.
3. **Đơn vị chỉ số của String là UTF-16 code unit:** `"🚀".length() == 2` dù emoji là một Unicode code point. `charAt()` có thể trả về nửa surrogate pair; `codePoints()` phù hợp hơn khi muốn duyệt code point. Một ký tự hiển thị cũng có thể gồm nhiều code point, ví dụ chữ cộng dấu kết hợp.
4. **`null` khác `""`:** `null` không có đối tượng; chuỗi rỗng có `length() == 0`. Gọi `length()` hoặc bất cứ instance method nào trên null gây `NullPointerException`.
5. **Văn bản khác byte:** `String` chứa văn bản, `byte[]` chứa dữ liệu byte. Chuyển qua lại cần chọn charset, thông thường `StandardCharsets.UTF_8`, ở cả hai đầu.

## 2. Đọc và trích xuất

| Nhu cầu | Phương thức | Ghi nhớ |
|---|---|---|
| Đo độ dài | `length()` | Số UTF-16 code unit |
| Chuỗi rỗng | `isEmpty()` | `true` chỉ khi độ dài bằng 0 |
| Rỗng hoặc toàn whitespace | `isBlank()` | Dùng quy tắc whitespace của `Character.isWhitespace` |
| Đọc một code unit | `charAt(index)` | Trả về `char` |
| Đọc một code point | `codePointAt(index)` | Trả về `int`, index vẫn tính theo UTF-16 |
| Đếm code point | `codePointCount(begin, end)` | Hai biên là chỉ số UTF-16 |
| Duyệt code point | `codePoints()` | Trả về `IntStream` |
| Lấy đoạn | `substring(begin, end)` | Lấy từ begin đến trước end |
| Lấy dạng `CharSequence` | `subSequence(begin, end)` | Dùng khi API nhận `CharSequence` |
| Sao chép các `char` | `toCharArray()`, `getChars(...)` | Vẫn theo code unit UTF-16 |
| Duyệt từng dòng | `lines()` | Trả về `Stream<String>` |

~~~java
String s = "Java 🚀";
System.out.println(s.length());                      // 7
System.out.println(s.charAt(0));                     // J
System.out.println(s.substring(0, 4));              // Java
System.out.println(s.codePointCount(0, s.length())); // 6
s.codePoints().forEach(cp -> System.out.print(Character.toString(cp)));
System.out.println();                                // Java 🚀
~~~

Muốn cắt theo **số code point**, dùng `offsetByCodePoints()` để tính vị trí UTF-16 rồi mới `substring()`:

~~~java
String s = "A🚀B";
int end = s.offsetByCodePoints(0, 2);
System.out.println(s.substring(0, end)); // A🚀
~~~

`s.substring(0, 2)` trong trường hợp này cắt giữa hai code unit của 🚀 và tạo chuỗi chứa surrogate lẻ. Nếu phải thao tác theo **grapheme cluster** (ký tự người dùng nhìn thấy), chỉ tính code point vẫn chưa đủ; cần bộ phân đoạn văn bản phù hợp.

## 3. So sánh: nội dung, chữ hoa và thứ tự

- `a.equals(b)`: so nội dung chính xác theo code unit và có phân biệt hoa/thường.
- `a.equalsIgnoreCase(b)`: so không phân biệt chữ hoa/thường theo quy tắc Unicode của Java, không thay thế đối chiếu ngôn ngữ tự nhiên đầy đủ.
- `a.compareTo(b)`: so thứ tự từ điển theo UTF-16, trả **âm / 0 / dương**; không mặc định chỉ có `-1, 0, 1`. `compareToIgnoreCase(b)` bỏ qua hoa/thường.
- `a.contentEquals(charSequence)`: so nội dung với một `CharSequence` như `StringBuilder`.
- `a.regionMatches(...)`: so hai vùng con, có biến thể bỏ qua hoa/thường.
- `Objects.equals(a, b)`: so an toàn khi tham chiếu có thể null.

~~~java
import java.util.Objects;

String a = new String("Java");
String b = "Java";
System.out.println(a == b);                                // false
System.out.println(a.equals(b));                           // true
System.out.println("Java".equalsIgnoreCase("JAVA"));       // true
System.out.println("Java".compareTo("Kotlin"));            // -1
System.out.println(Objects.equals(null, null));           // true
System.out.println("Java".contentEquals(
        new StringBuilder("Java")));                      // true
~~~

**Lỗi phổ biến:** `==` so danh tính tham chiếu, **không** so nội dung. String pool có thể làm hai literal trỏ cùng đối tượng, khiến ví dụ `"abc" == "abc"` có kết quả true nhưng không bảo đảm cho chuỗi tạo lúc chạy. Dùng `"expected".equals(candidate)` hoặc `Objects.equals(...)` khi có khả năng null.

Khi cần **sắp xếp tên hiển thị theo một ngôn ngữ** như tiếng Việt, dùng `java.text.Collator` với locale phù hợp thay vì dựa vào `compareTo()`:

~~~java
import java.text.Collator;
import java.util.Locale;

Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN"));
int result = collator.compare("An", "Ánh");
// result âm, bằng 0 hoặc dương theo quy tắc collation của locale.
~~~

Kết quả collation phụ thuộc dữ liệu locale, cấu hình mức phân biệt dấu/hoa thường (`strength`) và môi trường; không suy ra thứ tự theo ngôn ngữ từ giá trị Unicode code point.

## 4. Tìm kiếm chuỗi con và biểu thức chính quy

| Phương thức | Kết quả | Khi dùng |
|---|---|---|
| `contains(part)` | `boolean` | Có chuỗi con literal hay không |
| `startsWith(prefix)`, `endsWith(suffix)` | `boolean` | Kiểm tra tiền tố/hậu tố |
| `indexOf(part)` | Chỉ số xuất hiện đầu hoặc `-1` | Tìm vị trí (UTF-16) |
| `indexOf(part, fromIndex)` | Chỉ số hoặc `-1` | Tìm tiếp từ một vị trí |
| `lastIndexOf(part)` | Chỉ số xuất hiện cuối hoặc `-1` | Tìm từ cuối |
| `matches(regex)` | `boolean` | **Cả chuỗi** khớp regex |
| `Pattern` + `Matcher.find()` | `boolean`/nhóm/vị trí | Một **đoạn con** khớp regex |

~~~java
String text = "Java 17, Java 21";
System.out.println(text.contains("Java"));            // true
System.out.println(text.indexOf("Java"));             // 0
System.out.println(text.indexOf("Java", 1));          // 9
System.out.println(text.lastIndexOf("Java"));         // 9
System.out.println(text.startsWith("Java 17"));       // true
System.out.println("ABC123".matches("[A-Z]+\\d+"));   // true
System.out.println("x123y".matches("\\d+"));          // false
~~~

`matches()` yêu cầu regex khớp **toàn bộ** chuỗi. Để tìm nhiều đoạn con, dùng `find()`:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

Matcher m = Pattern.compile("\\d+").matcher("A12 B345");
while (m.find()) {
    System.out.println(m.group() + " tại " + m.start());
}
// 12 tại 1
// 345 tại 5
~~~

Trong Java source, regex `\d+` cần ghi bằng chuỗi `"\\d+"` vì backslash phải được escape ở tầng Java string. Dấu `.` trong regex khớp một ký tự theo quy tắc regex, không phải dấu chấm literal. Nếu người dùng nhập văn bản cần tìm, dùng `Pattern.quote(userText)` để tạo mẫu regex literal. Nếu chỉ tìm literal, `contains()`/`indexOf()` thường gọn và rõ hơn regex.

## 5. Biến đổi nội dung

### 5.1. Ghép, nối và lặp

`+` hoặc `concat()` nối chuỗi. `String.join(delimiter, elements...)` ghép các phần có dấu phân tách. `repeat(n)` lặp `n` lần (n âm gây `IllegalArgumentException`). Với nhiều phép nối trong vòng lặp lớn, `StringBuilder.append()` giảm việc tạo chuỗi trung gian; `StringBuilder` thay đổi được, nên không chia sẻ để nhiều luồng cùng sửa nếu không đồng bộ.

~~~java
System.out.println("Java".concat(" 17"));                // Java 17
System.out.println(String.join(" | ", "đọc", "tìm", "đổi")); // đọc | tìm | đổi
System.out.println("ha".repeat(3));                     // hahaha

StringBuilder builder = new StringBuilder();
for (int i = 1; i <= 3; i++) {
    if (i > 1) builder.append(", ");
    builder.append(i);
}
System.out.println(builder); // 1, 2, 3
~~~

### 5.2. Thay thế literal khác regex

- `replace(char, char)` và `replace(CharSequence, CharSequence)`: thay các giá trị **literal**.
- `replaceAll(regex, replacement)`: thay tất cả phần khớp **regex**. Replacement có cú pháp tham chiếu nhóm như `$1`.
- `replaceFirst(regex, replacement)`: thay phần khớp đầu tiên.

~~~java
System.out.println("a.b".replace(".", "-"));           // a-b
System.out.println("a.b".replaceAll(".", "-"));        // ---
System.out.println("a12b34".replaceAll("\\d+", "#"));  // a#b#
System.out.println("1 2 3".replaceFirst("\\d", "X"));  // X 2 3
~~~

Khi replacement đến từ người dùng, dùng `Matcher.quoteReplacement(replacement)` để tránh `$` hoặc backslash bị diễn giải như cú pháp. Với dữ liệu literal, chọn `replace(old, new)` để giảm lỗi.

### 5.3. Khoảng trắng, chữ hoa/thường và Unicode

`strip()`, `stripLeading()`, `stripTrailing()` bỏ whitespace theo `Character.isWhitespace`; `trim()` chỉ bỏ các code unit ở đầu/cuối có giá trị từ U+0000 tới U+0020, vì vậy có trường hợp khác `strip()`.

`toUpperCase(locale)` và `toLowerCase(locale)` dùng quy tắc locale. Khi biểu diễn key máy không phụ thuộc ngôn ngữ mà đặc tả cho phép case folding đơn giản, sử dụng `Locale.ROOT` tường minh. Với văn bản hiển thị, dùng locale thích hợp. Việc đổi hoa/thường có thể đổi số code point hoặc độ dài chuỗi.

`java.text.Normalizer` dùng để chuẩn hóa biểu diễn Unicode. Hai chuỗi nhìn giống nhau có thể khác về code point, ví dụ ký tự `é` dựng sẵn và `e` cộng dấu sắc kết hợp:

~~~java
import java.text.Normalizer;
import java.util.Locale;

String raw = "  Java  ";
System.out.println("[" + raw.trim() + "]");              // [Java]
System.out.println("[" + raw.strip() + "]");             // [Java]
System.out.println("java".toUpperCase(Locale.ROOT));     // JAVA

String composed = "\u00E9";
String decomposed = "e\u0301";
System.out.println(composed.equals(decomposed));          // false
System.out.println(composed.equals(
        Normalizer.normalize(decomposed, Normalizer.Form.NFC))); // true
~~~

NFC chuẩn hóa các biểu diễn tương đương theo Unicode, nhưng không đồng nhất mọi từ trông giống nhau. Với văn bản nhiều dòng, Java còn có `indent()`, `stripIndent()`, `translateEscapes()`; cần đọc quy tắc dấu xuống dòng/escape trước khi áp dụng.

## 6. Chuyển đổi kiểu dữ liệu, charset và các dạng mã hóa

### 6.1. Giá trị Java ↔ String

`String.valueOf(42)` tạo `"42"`; `String.valueOf(true)` tạo `"true"`; `String.valueOf((Object)null)` tạo **chuỗi ký tự** `"null"`, khác tham chiếu null.

`Integer.parseInt()`, `Long.parseLong()`, `Double.parseDouble()` chuyển chuỗi thành số và có thể ném `NumberFormatException`. `Boolean.parseBoolean()` trả true khi bằng `"true"` không phân biệt hoa/thường, những giá trị còn lại trả false.

~~~java
import java.util.Locale;

int count = Integer.parseInt("42");
String number = String.valueOf(count);
String code = String.format(Locale.ROOT, "ITEM-%04d", count);
System.out.println(number); // 42
System.out.println(code);   // ITEM-0042
~~~

`String.format(Locale, format, args)` cho phép xác định locale rõ ràng, rất hữu ích khi văn bản gửi qua hệ thống phải ổn định. `"ITEM-%04d".formatted(42)` là cú pháp gọn nhưng dùng locale mặc định. `Integer.parseInt("12.5")` không hợp lệ. `Double.parseDouble` dùng dấu thập phân theo cú pháp Java, không tự hiểu định dạng dấu phẩy của địa phương; với dữ liệu định dạng theo locale, cân nhắc `NumberFormat` và xác thực toàn bộ đầu vào.

### 6.2. Văn bản ↔ byte: charset

Encoding ánh xạ Unicode thành byte; decoding chuyển byte lại thành văn bản. Hai chiều phải thống nhất charset. Thông thường chọn `StandardCharsets.UTF_8` để tránh phụ thuộc charset mặc định của JVM/hệ điều hành.

~~~java
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

String message = "Xin chào 🚀";
byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
System.out.println(bytes.length);                       // 14
System.out.println(message.equals(restored));          // true
System.out.println(HexFormat.of().formatHex(
        "A".getBytes(StandardCharsets.UTF_8)));         // 41
~~~

Độ dài byte khác độ dài `String`: `à` là 2 byte trong UTF-8, 🚀 là 4 byte UTF-8 nhưng 2 code unit UTF-16. Giải mã byte UTF-8 bằng charset không phù hợp có thể gây lỗi hiển thị. Một số API chuyển đổi tiện dụng thay dữ liệu sai bằng ký tự thay thế; để **từ chối** đầu vào mã hóa không hợp lệ, tạo `CharsetDecoder`/`CharsetEncoder` với `CodingErrorAction.REPORT` và xử lý `CharacterCodingException`.

### 6.3. Base64, hex và URL encoding có mục đích khác charset

| Bài toán | API | Ý nghĩa |
|---|---|---|
| Unicode ↔ byte | `getBytes(UTF_8)`, `new String(bytes, UTF_8)` | Charset encoding/decoding |
| Byte ↔ Base64 | `Base64.getEncoder()/getDecoder()` | Biểu diễn byte bằng ký tự ASCII |
| Byte ↔ hex | `HexFormat` (Java 17+) | Biểu diễn một byte bằng hai chữ số hexadecimal |
| Giá trị form/query parameter | `URLEncoder.encode(text, UTF_8)` / `URLDecoder.decode(...)` | Quy tắc `application/x-www-form-urlencoded`; dấu cách thành `+` |
| Unicode code point ↔ text | `codePointAt()`, `Character.toString(int)` | Thao tác Unicode, không phải charset byte |

~~~java
import java.nio.charset.StandardCharsets;
import java.util.Base64;

byte[] data = "Java".getBytes(StandardCharsets.UTF_8);
String encoded = Base64.getEncoder().encodeToString(data);
String decoded = new String(Base64.getDecoder().decode(encoded),
                            StandardCharsets.UTF_8);
System.out.println(encoded); // SmF2YQ==
System.out.println(decoded); // Java
~~~

Base64, hex và URL encoding **không bảo mật dữ liệu** vì chúng có thể đảo ngược. `URLEncoder` mã hóa theo quy tắc form, không phải công cụ mã hóa nguyên URL hay mọi path segment một cách tùy ý.

## 7. Ví dụ tổng hợp: file chạy độc lập trên Java 17+

Lưu đoạn dưới đây ở file UTF-8 `StringOperationsDemo.java`. Các đoạn code trước đó minh họa riêng từng API và có thể cần đặt trong class/phương thức nếu chạy độc lập.

~~~java
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringOperationsDemo {
    public static void main(String[] args) {
        String data = "  Java Java 🚀  ";

        // Đọc
        System.out.println("len=" + data.length());
        System.out.println("strip=[" + data.strip() + "]");
        System.out.println("charAt2=" + data.charAt(2));
        System.out.println("substring=" + data.substring(2, 6));
        System.out.println("codePointsEmoji=" +
                "🚀".codePointCount(0, "🚀".length()));

        // So sánh
        System.out.println("equals=" + "Java".equals(new String("Java")));
        System.out.println("compareTo=" + "Java".compareTo("Kotlin"));

        // Tìm kiếm
        System.out.println("indexOf=" + data.indexOf("Java"));
        System.out.println("lastIndexOf=" + data.lastIndexOf("Java"));
        System.out.println("contains=" + data.contains("Java"));

        // Biến đổi
        System.out.println("replace=" + data.strip().replace("Java", "Core"));
        System.out.println("replaceAll=" + "a12b34".replaceAll("\\d+", "#"));
        System.out.println("upper=" + "java".toUpperCase(Locale.ROOT));
        Matcher m = Pattern.compile("\\d+").matcher("A12 B345");
        while (m.find()) {
            System.out.println("match=" + m.group() + "@" + m.start());
        }

        // Chuyển mã
        byte[] utf8 = "Xin chào 🚀".getBytes(StandardCharsets.UTF_8);
        System.out.println("utf8Bytes=" + utf8.length);
        System.out.println("decode=" + new String(utf8, StandardCharsets.UTF_8));
        String nfc = Normalizer.normalize("e\u0301", Normalizer.Form.NFC);
        System.out.println("nfcEqual=" + nfc.equals("\u00E9"));
    }
}
~~~

Lệnh biên dịch/chạy:

~~~bash
javac -encoding UTF-8 StringOperationsDemo.java
java StringOperationsDemo
~~~

Kết quả tiêu chuẩn:

~~~text
len=16
strip=[Java Java 🚀]
charAt2=J
substring=Java
codePointsEmoji=1
equals=true
compareTo=-1
indexOf=2
lastIndexOf=7
contains=true
replace=Core Core 🚀
replaceAll=a#b#
upper=JAVA
match=12@1
match=345@5
utf8Bytes=14
decode=Xin chào 🚀
nfcEqual=true
~~~

`javac -encoding UTF-8` xác định encoding **mã nguồn**, còn `StandardCharsets.UTF_8` xác định encoding **dữ liệu chương trình**. Hai mục đích riêng biệt.

## 8. Bảng chọn API và lỗi thường gặp

| Khi cần… | Chọn… | Tránh… |
|---|---|---|
| Kiểm tra rỗng/trắng | `isEmpty()`, `isBlank()` | Bỏ qua `null` |
| So văn bản chính xác | `equals()` | Dùng `==` vì string pool |
| So có thể null | `Objects.equals()` | Gọi instance method trên null |
| Sắp xếp hiển thị tiếng Việt | `Collator` và locale | Mong `compareTo()` sắp theo ngôn ngữ |
| Tìm literal | `contains()`, `indexOf()` | Regex không cần thiết |
| Tìm đoạn theo mẫu | `Pattern` + `Matcher.find()` | Nhầm `matches()` tìm đoạn |
| Thay thế literal | `replace()` | Nhầm `replaceAll()` xử lý `.` như dấu chấm |
| Bỏ khoảng trắng đầu/cuối | `strip()` | Nghĩ `trim()` luôn tương đương |
| Ghép trong vòng lặp | `StringBuilder` | Nối `+` liên tục tạo nhiều chuỗi |
| Cắt theo code point | `offsetByCodePoints()` | Cắt đôi surrogate pair |
| So Unicode tương đương chuẩn | `Normalizer` NFC khi cần | Chỉ nhìn hình dạng để kết luận `equals` |
| Đổi hoa/thường key máy | `Locale.ROOT` khi phù hợp đặc tả | Phụ thuộc locale mặc định |
| String ↔ byte | UTF-8 được chỉ định rõ | Nhầm `length()` với số byte |
| Biểu diễn byte bằng chữ ASCII | Base64/hex | Coi đó là kỹ thuật bảo mật |

### Checklist ứng dụng

1. Xác định nhu cầu: đọc, so nội dung, so thứ tự theo locale, tìm literal, regex, biến đổi hay encoding.
2. Xác định đơn vị đang thao tác: UTF-16 code unit, code point, grapheme cluster hoặc byte. Các đơn vị này khác nhau.
3. Xử lý null, trường hợp chuỗi rỗng/whitespace, chỉ số sai, parse số lỗi và input regex đặc biệt.
4. Ưu tiên API trực tiếp và rõ nghĩa: `equals()` cho nội dung, `replace()` cho literal, `Matcher.find()` cho đoạn khớp regex, UTF-8 tường minh khi truyền byte.
5. Thử các dữ liệu biên: tiếng Việt có dấu, emoji, ký tự kết hợp, nhiều dòng, chữ hoa/thường, charset sai và dữ liệu byte không hợp lệ.

Khi hiểu rõ năm nhóm, bạn sẽ chọn được cách xử lý văn bản có ý nghĩa rõ ràng, chính xác trước những trường hợp Unicode khó và dễ bảo trì khi chương trình lớn lên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-overview-007">Chọn String, StringBuilder hoặc StringBuffer theo nhu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn String, StringBuilder hoặc StringBuffer theo nhu cầu

Trong Java, cả ba kiểu đều liên quan đến chuỗi ký tự, nhưng khác nhau về **khả năng thay đổi nội dung**, **chi phí khi nối nhiều lần** và **đồng bộ hóa khi có nhiều luồng**. Việc chọn loại phù hợp phải dựa vào vòng đời dữ liệu và cách sử dụng thực tế.

## 1. Bảng so sánh nhanh

| Tiêu chí | String | StringBuilder | StringBuffer |
|---|---|---|---|
| Nội dung đối tượng thay đổi được? | Không (immutable) | Có (mutable) | Có (mutable) |
| Nối chuỗi thường dùng | +, concat(), formatted() | append(), insert() | append(), insert() |
| Chỉnh sửa tại chỗ | Không | Có | Có |
| Bảo đảm khi nhiều luồng cùng chỉnh sửa một đối tượng | Không có thao tác chỉnh sửa nội dung; có thể chia sẻ giá trị bất biến | Không đồng bộ hóa | Các phương thức thao tác chính được synchronized |
| Trường hợp phù hợp | Giá trị văn bản đã hoàn chỉnh, ít phép nối | Xây dựng văn bản lặp lại trong một luồng | Thực sự phải cùng sửa một bộ đệm từ nhiều luồng và cách đồng bộ từng phương thức đáp ứng yêu cầu |

Quy tắc thực hành: **String cho giá trị thông thường, StringBuilder cho quá trình lắp ráp nhiều bước, StringBuffer khi thật sự cần bộ đệm mutable được đồng bộ hóa**. Một ứng dụng chạy nhiều luồng vẫn có thể dùng StringBuilder nếu mỗi luồng sở hữu một instance riêng.

## 2. String: dữ liệu bất biến

String là immutable: sau khi tạo, nội dung của một **đối tượng String** không thay đổi. concat(), replace(), substring(), toUpperCase() trả về giá trị String; chúng không sửa đối tượng gốc. Biến có thể trỏ sang chuỗi khác, nhưng đó là thay tham chiếu.

~~~java
String original = "Java";
String extended = original.concat(" 17");

System.out.println(original); // Java
System.out.println(extended); // Java 17
~~~

Gọi original.concat(" 17") rồi bỏ qua kết quả sẽ không làm original có thêm " 17". Muốn cập nhật biến, dùng original = original.concat(" 17") hoặc tạo biến mới.

Tính bất biến giúp chia sẻ **nội dung String** an toàn giữa các luồng. Tuy nhiên, nếu các luồng cùng ghi và đọc một trường tham chiếu String hoặc cấu trúc bao quanh nó, quy tắc hiển thị và đồng bộ của Java vẫn áp dụng; tính bất biến không tự làm toàn bộ chương trình thread-safe.

Chọn String khi:

- Biểu diễn tên, mã định danh, câu thông báo, giá trị được truyền giữa các phương thức.
- Ghép ít đoạn bằng biểu thức rõ ràng như "Hello, " + name.
- Trả về giá trị đã hoàn chỉnh và không muốn đối tượng bị sửa nội dung sau đó.
- So sánh nội dung bằng equals(), equalsIgnoreCase() hay phương thức phù hợp.

**Không kết luận rằng mọi phép + đều kém hiệu năng.** Trình biên dịch và JVM hiện đại có tối ưu cho phép nối trong một biểu thức. Từ Java 9, lời gọi nối thường được tạo bằng invokedynamic; cách tối ưu cụ thể tùy phiên bản và ngữ cảnh. Một biểu thức nối ngắn thường dễ đọc hơn việc tự tạo bộ đệm.

Điểm cần tránh là **cộng dồn String lặp đi lặp lại** trong quá trình xây dựng kết quả dài: mỗi lần tạo một giá trị String mới có thể cần sao chép nội dung tích lũy. Với các chuỗi lớn hoặc vòng lặp nhiều lần, hãy cân nhắc StringBuilder.

## 3. StringBuilder: lắp ráp văn bản qua nhiều bước

StringBuilder có bộ đệm mutable. append(), insert(), delete(), replace(), reverse() thay đổi trạng thái cùng đối tượng, giúp giảm nhu cầu tạo String trung gian khi thêm nhiều phần.

~~~java
StringBuilder builder = new StringBuilder("Java");
builder.append(" ").append(17);
builder.insert(4, " Core");
System.out.println(builder); // Java Core 17
~~~

toString() tạo một **String kết quả ổn định**. Nếu builder được chỉnh sửa sau đó, String đã nhận từ toString() không đổi.

~~~java
String snapshot = builder.toString();
builder.append("!");
System.out.println(snapshot); // Java Core 17
System.out.println(builder);  // Java Core 17!
~~~

Khi tạo chuỗi có dấu phân cách qua nhiều lượt, đặt builder **ngoài** vòng lặp:

~~~java
StringBuilder line = new StringBuilder();
for (int i = 1; i <= 3; i++) {
    if (i > 1) {
        line.append(", ");
    }
    line.append(i);
}
System.out.println(line.toString()); // 1, 2, 3
~~~

Một bộ đệm được tái sử dụng thường phù hợp hơn việc thực hiện result = result + fragment qua nhiều vòng lặp. Kết luận về hiệu năng phải dựa trên workload thực tế: kích thước dữ liệu, tối ưu JIT và cách triển khai có thể làm kết quả khác nhau.

**length() và capacity():** length() là độ dài nội dung hiện có theo đơn vị mã UTF-16; capacity() là dung lượng bộ đệm đang cấp phát theo cùng đơn vị, có thể lớn hơn length(). Khi ước lượng được kích thước, new StringBuilder(expectedCapacity) có thể giảm số lần mở rộng bộ đệm; không cần dùng nó cho mọi trường hợp. Một ký tự Unicode ngoài BMP có thể chiếm hai đơn vị mã UTF-16, nên length() không nhất thiết bằng số ký tự người dùng nhìn thấy.

StringBuilder **không đồng bộ hóa** các thao tác. Nếu nhiều luồng cùng chỉnh sửa một instance không có cơ chế phối hợp, kết quả có thể sai. Dùng builder cục bộ trong mỗi thread/task là cách phổ biến để tránh chia sẻ trạng thái mutable.

## 4. StringBuffer: bộ đệm có đồng bộ hóa phương thức

StringBuffer cũng có append(), insert(), delete(), replace() và toString(), nhưng các phương thức thao tác chính được đồng bộ hóa trên đối tượng. Vì vậy các lời gọi append() riêng lẻ vào cùng StringBuffer không giẫm lên nhau như khi nhiều luồng cùng sửa StringBuilder không bảo vệ.

~~~java
StringBuffer buffer = new StringBuffer("Log");
buffer.append(": ").append("ready");
System.out.println(buffer); // Log: ready
~~~

Ví dụ chạy độc lập với hai luồng cùng nối vào một bộ đệm:

~~~java
public class SharedBufferDemo {
    public static void main(String[] args) throws InterruptedException {
        StringBuffer shared = new StringBuffer();

        Runnable task = () -> {
            for (int i = 0; i < 1_000; i++) {
                shared.append("x");
            }
        };

        Thread first = new Thread(task);
        Thread second = new Thread(task);
        first.start();
        second.start();

        first.join();
        second.join();
        System.out.println(shared.length());
    }
}
~~~

Lưu tệp **SharedBufferDemo.java**, biên dịch và chạy bằng JDK 17+:

~~~bash
javac SharedBufferDemo.java
java SharedBufferDemo
~~~

Kết quả:

~~~text
2000
~~~

Mỗi luồng thêm 1.000 ký tự "x"; join() bảo đảm cả hai kết thúc trước khi đọc độ dài. Thứ tự thao tác giữa các luồng không được xác định, nhưng độ dài cuối là 2.000 trong ví dụ này.

**Cảnh báo về tính nguyên tử:** Một phương thức synchronized chỉ bảo vệ **một lời gọi phương thức**. Nếu bài toán yêu cầu kiểm tra điều kiện rồi thêm ký tự như một thao tác duy nhất, đoạn sau không bảo đảm tính nguyên tử khi nhiều luồng cùng làm:

~~~java
if (shared.length() < 2_000) {
    shared.append("x");
}
~~~

Cả length() và append() đều được đồng bộ riêng, nhưng một luồng khác có thể chen vào giữa. Khi bắt buộc giữ nguyên tử cho cả nhóm, dùng cùng monitor:

~~~java
synchronized (shared) {
    if (shared.length() < 2_000) {
        shared.append("x");
    }
}
~~~

Những nơi khác thực hiện cùng quy tắc kiểm tra/cập nhật phải phối hợp trên cùng khóa. Với quy trình nhiều bước phức tạp, cân nhắc giảm hoặc loại bỏ việc dùng chung bộ đệm, thay vì mặc định StringBuffer giải quyết tất cả vấn đề đồng thời.

**Không chọn StringBuffer chỉ vì ứng dụng là đa luồng.** Nếu dữ liệu được tạo riêng trong mỗi tác vụ, StringBuilder không cần chi phí đồng bộ; nếu cần dữ liệu tổng hợp phức tạp từ nhiều luồng, có thể thiết kế kết quả cục bộ rồi hợp nhất theo một bước điều phối.

## 5. Chương trình so sánh hoàn chỉnh (Java 17+)

Ví dụ này độc lập với SharedBufferDemo ở trên. Lưu thành **StringChoiceDemo.java**:

~~~java
public class StringChoiceDemo {
    public static void main(String[] args) {
        String original = "Java";
        String changed = original.concat(" 17");
        System.out.println("original=" + original);
        System.out.println("changed=" + changed);

        StringBuilder builder = new StringBuilder("Java");
        builder.append(" ").append(17);
        System.out.println("builder=" + builder);
        builder.insert(4, " Core");

        String snapshot = builder.toString();
        builder.append("!");
        System.out.println("snapshot=" + snapshot);
        System.out.println("builder now=" + builder);
        System.out.println("content equals="
                + snapshot.equals("Java Core 17"));

        StringBuffer buffer = new StringBuffer("A");
        buffer.append("B").append("C");
        System.out.println("buffer=" + buffer);

        StringBuilder numbers = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            if (i > 1) {
                numbers.append(", ");
            }
            numbers.append(i);
        }
        System.out.println("joined=" + numbers);
    }
}
~~~

~~~bash
javac StringChoiceDemo.java
java StringChoiceDemo
~~~

Đầu ra chính xác:

~~~text
original=Java
changed=Java 17
builder=Java 17
snapshot=Java Core 17
builder now=Java Core 17!
content equals=true
buffer=ABC
joined=1, 2, 3
~~~

original không đổi sau concat(); builder thay đổi tại chỗ qua append()/insert(); snapshot giữ giá trị String riêng sau khi builder sửa tiếp; buffer minh họa API của StringBuffer. Ví dụ thứ hai chỉ cho thấy thao tác của StringBuffer, còn SharedBufferDemo minh họa dùng chung giữa nhiều luồng.

## 6. Áp dụng theo tình huống

| Nhu cầu | Nên bắt đầu bằng | Lý do |
|---|---|---|
| Tạo thông báo "Xin chào" + tên một lần | String | Đơn giản, dễ đọc |
| Lưu/trao đổi giá trị văn bản hoàn chỉnh | String | Nội dung bất biến |
| Ghép hàng nghìn trường dữ liệu trong một luồng | StringBuilder | Tái sử dụng một bộ đệm khi nối nhiều lần |
| Mỗi tác vụ song song tự tạo một đoạn báo cáo | StringBuilder riêng trong mỗi tác vụ | Không có mutable state chia sẻ |
| Nhiều luồng cùng append vào cùng một bộ đệm | StringBuffer, nếu cần chia sẻ thật sự | Đồng bộ hóa từng lời gọi append |
| Kiểm tra rồi cập nhật cùng bộ đệm ở nhiều luồng | Khóa bao cả nhóm thao tác hoặc thiết kế khác | Đồng bộ từng phương thức chưa đủ |
| Nối các từ bằng dấu phẩy | String.join() hoặc StringJoiner | Diễn đạt trực tiếp dấu phân cách |
| Tạo JSON/SQL/HTML có escape hoặc tham số | API chuyên dụng phù hợp | Xử lý cấu trúc, ký tự đặc biệt hoặc tham số an toàn hơn việc ghép tay |

Ví dụ nối với dấu phân cách khi đã có các chuỗi hoàn chỉnh:

~~~java
String names = String.join(", ", "Lan", "Minh", "An");
System.out.println(names); // Lan, Minh, An
~~~

Nếu dữ liệu ở stream, Collectors.joining(", ") cũng giúp thể hiện rõ nhu cầu nối các phần tử. Không nhất thiết phải tự quản lý dấu phân cách bằng StringBuilder cho trường hợp đơn giản.

## 7. Sai lầm thường gặp

1. **Quên nhận giá trị trả về của String.** s.replace("a", "o") không sửa s. Hãy gán s = s.replace("a", "o") hoặc dùng giá trị mới.
2. **Dùng + để tích lũy trong vòng lặp lớn.** Dùng StringBuilder được tạo bên ngoài vòng lặp khi phải ghép nhiều phần.
3. **Tin rằng mọi dấu + đều chậm.** Cách nối trong một biểu thức được JVM tối ưu theo phiên bản/ngữ cảnh; đừng làm mã phức tạp khi chưa có nhu cầu.
4. **Chọn StringBuffer cho builder chỉ dùng ở một luồng.** Đồng bộ hóa khi không cần có thể tăng chi phí.
5. **Cho rằng StringBuffer bảo đảm nguyên tử cho nhiều phương thức liên tiếp.** Kiểm tra và cập nhật hoặc các chuỗi thao tác phối hợp cần khóa bao trọn.
6. **So sánh StringBuilder bằng equals() như String.** StringBuilder không ghi đè equals() để so sánh nội dung kiểu String; có thể dùng a.toString().equals(b.toString()).
7. **Nhầm StringBuilder/StringBuffer với String.** Chúng triển khai CharSequence, nhưng API yêu cầu String cụ thể cần toString().
8. **Nhầm capacity() với length().** Dung lượng dự trữ khác với số đơn vị UTF-16 đã sử dụng.
9. **Đưa cùng StringBuilder cho nhiều thread mà không phối hợp.** Hãy giữ riêng theo tác vụ hoặc thiết kế đồng bộ thích hợp.
10. **Benchmark bằng một vòng lặp ngắn.** Warm-up, JIT, GC và khả năng loại bỏ phép tính làm số đo sai lệch. Nếu tối ưu là mục tiêu thực tế, dùng JMH với workload có ý nghĩa và kết quả được tiêu thụ.

## 8. Cây quyết định đơn giản

1. Văn bản đã hoàn chỉnh hoặc chỉ ghép một vài đoạn? **Dùng String**.
2. Cần nối/chèn/xóa lặp đi lặp lại để tạo kết quả? **Dùng StringBuilder**.
3. Bộ đệm có thật sự được nhiều luồng cùng sửa? **Xét StringBuffer** hoặc một thiết kế giảm chia sẻ mutable state.
4. Nhiều bước phải là một thao tác nguyên tử? **Thiết kế khóa bao toàn bộ bước**; StringBuffer không tự đủ.
5. Xây dựng xong và cần trả dữ liệu ổn định? **Gọi toString()** để nhận String.

Điểm cốt lõi: **String là giá trị bất biến; StringBuilder là bộ đệm xây dựng chuỗi mặc định; StringBuffer là bộ đệm có đồng bộ hóa từng phương thức**. Chọn theo cách sử dụng và phạm vi chia sẻ, không chỉ theo tên API hoặc giả định hiệu năng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-overview-008">Kiến thức nền và trình tự học Java Core String</a>

<details>
<summary>Xem chi tiết</summary>

# Kiến thức nền và trình tự học Java Core String

## 1. Mục tiêu và vị trí của chủ đề

`String` là kiểu dữ liệu dùng để biểu diễn **chuỗi ký tự** trong Java. Đây là một lớp thuộc gói `java.lang`, được nhập tự động. Học `String` không chỉ là ghi nhớ các phương thức: cần hiểu sự khác nhau giữa **giá trị và tham chiếu**, tính **bất biến**, cách Java biểu diễn **Unicode**, cũng như cách xử lý văn bản an toàn và hiệu quả.

Sau bài học, người học cần có thể:

- Tạo chuỗi, đọc và biến đổi nội dung mà không nhầm lẫn về tính bất biến.
- So sánh chuỗi theo nội dung, phân biệt `==` với `equals()` và xử lý trường hợp `null`.
- Tìm kiếm, cắt, nối, tách, thay thế, định dạng và chuẩn hóa văn bản.
- Giải thích `String Pool`, `StringBuilder`, mã hóa ký tự và các lỗi Unicode thường gặp.
- Chọn API phù hợp trong tình huống thực tế và kiểm chứng bằng chương trình Java tự chạy.

**Điều kiện đầu vào:** đã học biến, kiểu nguyên thủy, toán tử, `if`, vòng lặp, phương thức, mảng và kiến thức cơ bản về lớp/đối tượng. Nền tảng ví dụ là **Java 17+**; một số API hiện đại như `strip()`, `isBlank()`, `lines()`, `repeat()` có từ Java 11. Lộ trình dưới đây đi từ căn bản đến ứng dụng rồi mới tối ưu.

## 2. Lộ trình đọc các bài trong chương

Mục tiêu của bài này là **biết học gì trước, luyện gì sau và chứng minh đã hiểu bằng cách nào**. Các bài chuyên đề giữ phần giải thích chi tiết và code thực hành; khi cần tra cứu một hành vi cụ thể, hãy quay lại đúng bài đó thay vì học thuộc toàn bộ API trong một lượt.

| Thứ tự | Bài nên đọc | Sau bài này, tự kiểm tra được gì? |
| --- | --- | --- |
| 1 | String là gì và vai trò biểu diễn văn bản trong Java | Giải thích String là đối tượng, dùng cho văn bản và không tự quyết định charset hay độ an toàn của dữ liệu. |
| 2 | Quan hệ giữa String, char[], CharSequence và đối tượng | Phân biệt upcast, copy, mutable view và phép chuyển kiểu. |
| 3 | Phân biệt nội dung chuỗi với biến tham chiếu đến chuỗi | Vẽ lại sơ đồ alias, giải thích `==`, `equals()` và string pool. |
| 4 | Chuỗi rỗng, chuỗi trắng, null và chuỗi chứa chữ null | Viết điều kiện null-safe mà không làm mất bốn trạng thái đầu vào. |
| 5 | Vì sao String là lớp final và có tính bất biến | Giải thích `final class`, `final` trên biến và immutable bằng ví dụ độc lập. |
| 6 | Các nhóm thao tác: đọc, so sánh, tìm kiếm, biến đổi và chuyển mã | Chọn `substring`, `indexOf`, `replace`, regex, Unicode và UTF-8 theo yêu cầu. |
| 7 | Chọn String, StringBuilder hoặc StringBuffer theo nhu cầu | Biết khi nào cần bộ đệm mutable, và giới hạn đồng bộ từng lời gọi. |
| 8 | Kiến thức nền và trình tự học Java Core String (bài này) | Lập kế hoạch luyện tập, kiểm tra trường hợp biên và hoàn thành mini project. |

**Dấu hiệu cần quay lại bài trước:** chưa phân biệt `null` với chuỗi rỗng; nhầm `==` với so nội dung; tưởng `StringBuilder` là `String`; chưa giải thích được tại sao emoji có `length() == 2`; hoặc chưa tách được encoding byte khỏi thao tác trên chuỗi. Mỗi trường hợp đều có bài học chính ở trên.

Trước khi bắt đầu chặng mới, hãy tự chạy ít nhất một chương trình `main()` và thử một giá trị biên (như `null`, chuỗi rỗng, emoji hoặc dấu phân cách ở cuối). Ghi lại cả kết quả dự đoán và kết quả thực tế để phát hiện giả định sai.

## 3. Trình tự học đề xuất: từ nền tảng đến ứng dụng

Không cần học mọi API trong một lần. Mỗi chặng nên kết thúc bằng một sản phẩm chạy được và một bộ trường hợp biên tự viết.

| Chặng | Kiến thức chính | Bài thực hành để qua chặng |
| --- | --- | --- |
| 0. Ôn Java nền | Primitive/reference, `char`, array, `null`, vòng lặp, method, exception | Giải thích vì sao `String` là đối tượng dù có cú pháp literal |
| 1. Tạo và so sánh | Literal, `new`, immutable, `equals()`, `==`, pool | Viết chương trình phân biệt cùng giá trị và cùng tham chiếu; thử `null` |
| 2. Thao tác cơ bản | `length()`, `substring()`, tìm kiếm, `strip()`, thay thế | Chuẩn hóa tiêu đề, trích phần trước/sau một dấu phân cách |
| 3. Tách và dựng chuỗi | `split()`, `join()`, regex căn bản, `StringBuilder` | Xử lý danh sách thẻ ngăn cách bằng dấu phẩy, giữ ô rỗng khi cần |
| 4. Unicode và locale | UTF-16, code point, charset, normalization, case folding ở mức phù hợp | Đo độ dài chuỗi chứa tiếng Việt, emoji; thử round trip UTF-8 |
| 5. Ứng dụng và kiểm thử | Input validation, yêu cầu null/blank, trường hợp biên, hiệu năng | Tự xây một bộ hàm làm sạch văn bản có kiểm thử nhiều trường hợp |
| 6. Đào sâu theo nhu cầu | `Pattern`/`Matcher`, `Collator`, `CharsetDecoder`, tối ưu hóa khi có số đo | Giải một bài toán thực tế có quy tắc rõ ràng và giải thích lựa chọn API |

**Cách học mỗi chặng:** đọc khái niệm → tự viết ví dụ 10–30 dòng → dự đoán kết quả → chạy và giải thích chênh lệch → sửa bài theo trường hợp biên → tự tóm tắt lúc nào nên/không nên dùng API đó. Chỉ chuyển chặng khi giải thích được kết quả, không chỉ khi chương trình chạy.

### Gợi ý bài tập theo độ khó

**Cơ bản:**

1. Viết `isBlankOrNull(String s)` trả `true` khi `s == null` hoặc `s.isBlank()`. Thử `null`, `""`, `" "` và `" Java "`.
2. Cho `"  java core  "`, tạo kết quả `"JAVA CORE"` bằng cách bỏ khoảng trắng hai đầu và chuyển chữ hoa; chứng minh chuỗi gốc vẫn giữ nguyên.
3. Viết hàm kiểm tra tên file kết thúc bằng `".java"` (phân biệt chữ hoa/thường), và tự quyết định cách xử lý `null`.

**Trung bình:**

4. Phân tích `"a, b,,c,"` thành các trường; bảo toàn trường trống cuối; loại bỏ khoảng trắng quanh mỗi trường mà không đánh mất vị trí rỗng.
5. Cho câu văn, đếm số lần xuất hiện của một từ hoặc đoạn con theo quy tắc **đã xác định** về phân biệt chữ hoa và ranh giới từ; kiểm tra trường hợp chuỗi tìm kiếm rỗng.
6. Tạo slug đơn giản cho tiêu đề theo bộ quy tắc do bạn tự định nghĩa: `strip()`, hạ chữ, đổi nhóm khoảng trắng thành `-`; lưu ý slug cho tiếng Việt có dấu cần quy định riêng, không giả định luôn phải bỏ dấu.

**Nâng cao:**

7. Đếm UTF-16 code units, code points và số byte UTF-8 của `"A😀é"`; giải thích vì sao ba con số có thể khác nhau.
8. So sánh `"é"` với `"e\u0301"` trước và sau `Normalizer.Form.NFC`; giải thích khác biệt giữa *hiển thị giống nhau* và *bằng nhau theo equals*.
9. Xây hàm nhận dữ liệu tên người dùng: yêu cầu rõ ràng về `null`/blank, có giữ dấu hay không, cách xử lý Unicode, độ dài đo theo đơn vị nào. Viết ít nhất 6 trường hợp kiểm thử.

**Mini project cuối chủ đề:** xây thư viện nhỏ `TextUtils` (các phương thức `normalizeSpaces`, `safeEquals`, `splitPreservingEmpty`, `utf8ByteLength`). Viết JUnit tests nếu đã học kiểm thử; nếu chưa, tạo `main()` in expected/actual. Phải công bố contract cho mọi hàm (đầu vào `null`, chuỗi rỗng, dữ liệu lỗi, quy tắc Unicode) và kiểm thử đúng contract.

## 4. Cách đọc tài liệu và tự kiểm chứng

- Tra cứu **Javadoc `java.lang.String` của đúng phiên bản JDK đang dùng**: chữ ký, kết quả, ngoại lệ, behavior với `null` và các phiên bản phát hành.
- Dùng `jshell` cho thử nghiệm ngắn, hoặc tạo một tệp `.java` có `public static void main(String[] args)` để quan sát hành vi nhất quán.
- Dùng `javac -version` và `java -version` để biết compiler/runtime; ưu tiên hai phiên bản tương thích. Nếu làm việc với Java 17, tránh sử dụng API chỉ được bổ sung ở phiên bản mới hơn.
- Khi ví dụ gắn với locale, charset hay regex, nêu rõ giả định đầu vào; tránh kết luận từ một chuỗi ASCII đơn giản.
- Khi gặp lỗi, thử tạo **minimum reproducible example**: chuỗi vào ngắn nhất, output mong đợi và output thực tế. Kiểm tra độ dài, chỉ số, regex, ký tự escape và `null` trước khi suy đoán do JVM.

Tài liệu chuẩn nên tra cứu: [Java SE 17: String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html), [StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html), [StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html), [Normalizer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html) và [Pattern](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html).

## 5. Tiêu chí hoàn thành chủ đề

Tự trả lời được các câu sau **không cần chạy thử trước**:

1. Vì sao `"Java" == new String("Java")` là `false`, trong khi `equals()` là `true`?
2. Vì sao `text.replace("a", "b");` không thay đổi biến `text`?
3. `substring(1, 3)` lấy những vị trí nào và ngoại lệ gì xảy ra nếu chỉ số vượt biên?
4. Khác nhau giữa `null`, `""`, `"   "`, `isEmpty()` và `isBlank()`?
5. Vì sao `"😀".length()` bằng `2`? Khi nào cần `codePoints()`?
6. Khi nào dùng `replace()`, khi nào dùng `replaceAll()`, và vì sao regex cần escape?
7. Vì sao nên truyền `StandardCharsets.UTF_8` khi chuyển giữa `String` và `byte[]`?
8. Trường hợp nào `StringBuilder` hợp lý hơn phép nối `+`?
9. Vì sao chuẩn hóa Unicode hoặc so sánh theo locale cần quy tắc nghiệp vụ?
10. Bạn sẽ kiểm thử những trường hợp biên nào trước khi dùng hàm xử lý văn bản trong chương trình thật?

**Hoàn thành thực chất** khi viết được các hàm của mini project, giải thích được output, xử lý được `null`/rỗng/Unicode và kiểm tra được chuỗi đầu vào khác ví dụ mẫu. Khi đó mới nên mở rộng sang regex nâng cao, thao tác file văn bản, định dạng địa phương hóa và các API xử lý dữ liệu lớn.

</details>

- [Quay lại đầu trang](#back-to-top)
