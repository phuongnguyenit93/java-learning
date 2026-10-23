<a id="back-to-top"></a>

# Các kiểu chuẩn

## Menu
- [String.equals() so sánh nội dung và không phụ thuộc danh tính](#standard-types-001)
- [Lớp bao số nguyên khác kiểu không mặc nhiên bằng nhau](#standard-types-002)
- [Float và Double: NaN, +0.0 và -0.0](#standard-types-003)
- [Phân biệt == trên số thực với equals() của lớp bao](#standard-types-004)
- [BigDecimal.equals() xét cả giá trị và scale](#standard-types-005)
- [BigDecimal.compareTo() có thể bằng 0 khi equals() là false](#standard-types-006)
- [Enum giữ danh tính từng hằng và không cho ghi đè equals() hoặc hashCode()](#standard-types-007)
- [Đối tượng giá trị của java.time và yêu cầu đọc đúng hợp đồng từng kiểu](#standard-types-008)
- [Không suy rộng quy tắc của một lớp chuẩn cho mọi lớp khác](#standard-types-009)

## <a id="standard-types-001">String.equals() so sánh nội dung và không phụ thuộc danh tính</a>

<details>
<summary>Xem chi tiết</summary>

# `String.equals()` so sánh nội dung và không phụ thuộc danh tính

## 1. Phân biệt giá trị văn bản và danh tính tham chiếu

Trong Java, biến `String` chứa **tham chiếu** đến một đối tượng chuỗi. Vì thế, hai phép kiểm tra sau trả lời hai câu hỏi khác nhau:

- **`a == b`** với hai tham chiếu: hai biến có trỏ tới **cùng một đối tượng** không? Đây là kiểm tra danh tính tham chiếu (*reference identity*), không phải phép so sánh nội dung.
- **`a.equals(b)`**: hai đối tượng có cùng **nội dung `String`** không, bất kể chúng nằm ở hai đối tượng riêng?

Hai `String` khác đối tượng vẫn có thể `a == b` là `false` và `a.equals(b)` là `true`. Ngược lại, nếu `a == b` và `a` không `null`, `a.equals(b)` tất nhiên đúng vì đó là cùng đối tượng.

`String` là một lớp `final` với giá trị bất biến (*immutable*). Mã khác không thể kế thừa `String` để thay đổi cách `equals` hoạt động, và nội dung của một đối tượng `String` không thay đổi sau khi tạo. Đây là lý do nó thích hợp làm khóa của `HashMap` hoặc phần tử trong `HashSet`.

**Phạm vi của `equals`:** hai đối tượng có cùng nội dung chuỗi không đồng nghĩa chúng biểu thị cùng người, cùng tài khoản, cùng bản ghi hay cùng đối tượng trong mô hình nghiệp vụ. `equals` chỉ xác nhận sự bằng nhau theo định nghĩa giá trị của lớp.

## 2. Hợp đồng và cơ chế của `String.equals(Object)`

Phương thức `String` ghi đè phương thức từ `Object`:

```java
public boolean equals(Object anObject)
```

Đây là **chữ ký API**, không phải một chương trình chạy độc lập.

Về mặt ngữ nghĩa, `String.equals` trả `true` khi đối số là một `String` có **cùng dãy đơn vị mã UTF-16** theo đúng thứ tự. Điều này tương đương với cùng chiều dài `length()` và cùng giá trị `charAt(i)` tại mọi chỉ số hợp lệ. Với đối số `null` hoặc đối tượng không phải `String`, kết quả là `false`.

JDK được phép tối ưu cách kiểm tra, ví dụ xử lý cùng tham chiếu, kiểm tra chiều dài, so sánh dữ liệu trong bộ nhớ hoặc dùng biểu diễn compact; **không nên giả định một thuật toán vòng lặp duy nhất** từ mô tả hợp đồng.

| Biểu thức | Kết quả | Giải thích |
| --- | --- | --- |
| `"java".equals("java")` | `true` | Cùng nội dung |
| `"java".equals("Java")` | `false` | Phân biệt chữ hoa/thường |
| `"java".equals("java ")` | `false` | Có dấu cách ở cuối |
| `"".equals("")` | `true` | Cùng chuỗi rỗng |
| `"java".equals(null)` | `false` | `null` không phải `String` |
| `"java".equals(new StringBuilder("java"))` | `false` | Khác kiểu đối tượng, dù ký tự hiển thị giống |

`String.equals` không tự động xóa khoảng trắng, bỏ phân biệt chữ hoa, áp dụng bộ quy tắc ngôn ngữ theo locale hoặc chuẩn hóa Unicode. Nếu cần cách so sánh khác, ứng dụng phải định nghĩa rõ quy tắc đó.

## 3. String Pool và lý do `==` đôi lúc có vẻ hoạt động

Các literal chuỗi Java dùng cơ chế *interning*. Những literal cùng giá trị như `"hello"` thường trỏ đến đối tượng canonical từ String Pool. Vì vậy, kiểm tra `==` giữa hai literal giống nhau có thể trả `true` và gây hiểu lầm rằng `==` dùng để so nội dung.

```java
String a = "hello";
String b = "hello";
String c = new String("hello");

System.out.println(a == b);       // true
System.out.println(a == c);       // false
System.out.println(a.equals(c));  // true
```

Đây là **mảnh mã minh họa**. `a` và `b` nhận cùng đối tượng chuỗi được intern; `new String("hello")` tạo đối tượng mới có nội dung giống nhau, nên `a == c` sai còn `a.equals(c)` đúng.

Các tình huống quan trọng:

1. Hai literal hằng cùng nội dung được intern theo quy tắc chuỗi literal; `"he" + "llo"` cũng là biểu thức hằng có thể được trình biên dịch xử lý thành literal `"hello"`.
2. Phép nối dữ liệu từ biến tại **runtime** có thể tạo đối tượng chuỗi khác. Không dùng kết quả `==` với literal để suy ra quy tắc cho dữ liệu từ file, mạng hay người dùng.
3. `String.intern()` cung cấp tham chiếu canonical nhưng **không cần gọi nó để `equals` đúng**. Interning là cơ chế chia sẻ tham chiếu, không phải điều kiện bắt buộc cho equality nội dung.
4. `==` vẫn có chỗ dùng: kiểm tra tham chiếu giống nhau hoặc kiểm tra với `null`. Khi câu hỏi là “hai chuỗi có cùng chữ không?”, chọn `equals`.

## 4. Ví dụ Java 17+ chạy hoàn chỉnh

Tạo tệp `StringEqualityDemo.java`:

```java
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StringEqualityDemo {
    public static void main(String[] args) {
        String literalA = "java";
        String literalB = "java";
        String separate = new String("java");
        String alias = literalA;

        System.out.println("literal identity: " + (literalA == literalB));
        System.out.println("separate identity: " + (literalA == separate));
        System.out.println("alias identity: " + (literalA == alias));
        System.out.println("content equal: " + literalA.equals(separate));
        System.out.println("case sensitive: " + "Java".equals(separate));
        System.out.println("ignore case: " + "Java".equalsIgnoreCase(separate));

        Set<String> set = new HashSet<>();
        set.add(literalA);
        set.add(separate);
        System.out.println("set size: " + set.size());

        Map<String, Integer> values = new HashMap<>();
        values.put(new String("ticket-17"), 42);
        System.out.println("map lookup: " + values.get(new String("ticket-17")));

        StringBuilder builder = new StringBuilder("java");
        System.out.println("builder equals: " + literalA.equals(builder));
        System.out.println("builder content: " + literalA.contentEquals(builder));

        String composed = "\u00E9";
        String decomposed = "e\u0301";
        System.out.println("unicode equals: " + composed.equals(decomposed));
        String normalized = Normalizer.normalize(
                decomposed, Normalizer.Form.NFC);
        System.out.println("normalized equals: " + composed.equals(normalized));

        System.out.println("same hash: "
                + ("FB".hashCode() == "Ea".hashCode()));
        System.out.println("same content: " + "FB".equals("Ea"));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 StringEqualityDemo.java
java StringEqualityDemo
```

Stdout:

```text
literal identity: true
separate identity: false
alias identity: true
content equal: true
case sensitive: false
ignore case: true
set size: 1
map lookup: 42
builder equals: false
builder content: true
unicode equals: false
normalized equals: true
same hash: true
same content: false
```

Giải thích:

- **Ba dòng đầu:** hai literal dùng chung đối tượng interned; `new String` tạo đối tượng riêng; `alias` chỉ là tham chiếu tới đối tượng cũ.
- **`content equal`:** hai đối tượng `String` khác nhau vẫn bằng nhau về nội dung. `equals` phân biệt chữ hoa trong khi `equalsIgnoreCase` dùng quy tắc bỏ qua hoa/thường của Java.
- **`set size` và `map lookup`:** `HashSet` loại trùng và `HashMap` tra cứu khóa dựa trên equality nội dung, không yêu cầu đầu vào có cùng tham chiếu.
- **`builder equals` và `builder content`:** `StringBuilder` không phải `String`. `String.equals(builder)` trả `false`; `String.contentEquals(builder)` lại có thể so nội dung một `CharSequence`.
- **Unicode:** `é` dạng tổ hợp sẵn và dạng `e` cộng dấu kết hợp có thể trông giống nhau nhưng chứa dãy UTF-16 khác nhau; chuẩn hóa NFC trước khi so sánh giúp chúng tương đương.
- **Hash:** `"FB"` và `"Ea"` có cùng mã hash theo thuật toán `String.hashCode` nhưng `equals` vẫn `false`. Không suy diễn nội dung chỉ từ hash code.

## 5. `String.equals` và `hashCode` trong collection

`String` cài cả `equals` và `hashCode`. Hợp đồng chung của Java yêu cầu: khi `a.equals(b)` trả `true` thì **`a.hashCode() == b.hashCode()`**. Vì đối tượng `String` bất biến, thông tin dùng tính hash không thay đổi. Đây là một nền tảng khiến chuỗi hoạt động ổn định như khóa hash.

Khi `HashMap` có khóa `new String("A-001")`, lệnh `get(new String("A-001"))` vẫn lấy được giá trị đã lưu vì hai khóa có cùng nội dung và cùng hash. `HashSet` cũng loại trùng theo quan hệ bằng nhau, dù hai giá trị được tạo qua hai lệnh `new` khác nhau.

Nhưng **cùng hash không chứng minh bằng nhau**. Va chạm là điều có thể xảy ra, như ví dụ `"FB"` và `"Ea"`. Hash giúp định vị vị trí tra cứu; equality giúp phân biệt những khóa cụ thể. Không thay `a.equals(b)` bằng phép so sánh `hashCode` vì kết quả có thể sai.

Ngược lại, dùng `==` để tìm mã đơn hàng, khóa cache hoặc dữ liệu API có thể thất bại do một giá trị bằng nội dung nhưng đến từ đối tượng mới. Câu hỏi nghiệp vụ thường là **cùng mã**, không phải **cùng địa chỉ tham chiếu**.

## 6. `null`, các phương thức liên quan và cách chọn

### Tránh `NullPointerException`

Nếu `input` có thể bằng `null` thì `input.equals("ok")` sẽ ném `NullPointerException`, bởi phương thức không thể được gọi trên tham chiếu `null`. Hai cách thông dụng:

```java
String input = null;
boolean a = "ok".equals(input);                   // false
boolean b = java.util.Objects.equals(input, "ok"); // false
boolean c = java.util.Objects.equals(null, null);  // true
```

Đây là **mảnh mã minh họa**. `"ok".equals(input)` an toàn vì đối tượng gọi luôn tồn tại. `Objects.equals(a,b)` xử lý cả hai toán hạng có thể null và trả `true` nếu cả hai đều null. Vì vậy cần xem yêu cầu nghiệp vụ có coi “hai giá trị chưa biết” là bằng nhau không.

### Các phép so sánh khác

| API | Đặc điểm | Khi dùng |
| --- | --- | --- |
| `a == b` | So cùng tham chiếu | Kiểm tra identity hoặc null |
| `a.equals(b)` | So chính xác nội dung hai `String` | So giá trị chuỗi theo mặc định |
| `Objects.equals(a,b)` | So bằng nhau an toàn với null; dùng `equals` khi có giá trị | Hai phía có thể null |
| `a.equalsIgnoreCase(b)` | Bỏ qua khác biệt hoa/thường theo quy tắc của Java, không phụ thuộc locale | Khi yêu cầu chỉ cần so tương đương theo quy tắc đó |
| `a.contentEquals(cs)` | So nội dung `String` với một `CharSequence` | So với `StringBuilder`, `StringBuffer`... khi phù hợp |
| `a.compareTo(b)` | So thứ tự từ điển theo đơn vị mã UTF-16; trả 0 nếu chuỗi cùng nội dung | Sắp xếp thứ tự tự nhiên của `String` |
| `Collator` | So sánh theo quy tắc của một locale và mức độ phân biệt | Sắp xếp văn bản theo ngôn ngữ |

**`equalsIgnoreCase` không phải chuẩn hóa văn bản phổ quát.** Nó không tự cắt khoảng trắng, chuẩn hóa Unicode hoặc đảm bảo quy tắc so sánh của từng ngôn ngữ. Nếu muốn so tên người theo thứ tự/sự tương đương theo locale, nên cân nhắc `Collator` và các quy tắc dữ liệu riêng. Với mật khẩu, token hoặc chữ ký, không tự bỏ qua khác biệt ký tự khi giao thức không yêu cầu.

Một điểm dễ nhầm: `StringBuilder` kế thừa `Object.equals` theo ngữ nghĩa danh tính, do vậy hai `StringBuilder` chứa cùng ký tự không mặc nhiên bằng nhau. Muốn so nội dung với `String`, dùng `contentEquals` hoặc chuyển builder thành `String` rồi dùng `equals`.

## 7. Unicode: nhìn giống nhau không đảm bảo `equals`

Một văn bản có thể có nhiều cách biểu diễn Unicode. Chữ `é` chẳng hạn có thể là ký tự ghép sẵn U+00E9 hoặc chữ `e` U+0065 theo sau bởi dấu kết hợp U+0301. Hai cách có thể hiển thị gần như giống nhau, nhưng `String.equals` so từng **đơn vị mã UTF-16**, nên vẫn thấy khác biệt.

`String.length()` đếm đơn vị mã UTF-16, không nhất thiết đếm số ký tự mà người đọc nhìn thấy hay số Unicode code point. Ký tự ngoài BMP có thể gồm một cặp surrogate, còn chữ có dấu kết hợp có thể có nhiều code point. Do đó, so chính xác nội dung không đồng nghĩa so cách hiển thị.

Khi nghiệp vụ muốn coi hai biểu diễn Unicode tương đương, có thể chuẩn hóa cùng một dạng, chẳng hạn NFC:

```java
import java.text.Normalizer;

String normalizedA = Normalizer.normalize(inputA, Normalizer.Form.NFC);
String normalizedB = Normalizer.normalize(inputB, Normalizer.Form.NFC);
boolean same = normalizedA.equals(normalizedB);
```

Đây là **mảnh mã minh họa**, giả định `inputA` và `inputB` đã có giá trị. Chuẩn hóa NFC xử lý tương đương chuẩn tắc Unicode; nó **không tự xử lý mọi khác biệt** về chữ hoa, khoảng trắng, ký tự giống hình từ các bảng chữ cái khác nhau, hay các yêu cầu so sánh theo tiếng Việt.

Đối với khóa dữ liệu và định danh, hãy xác định chính sách chuẩn hóa **trước khi ghi và truy vấn**, để mọi phía dùng cùng cách diễn giải. Đối với mật khẩu hoặc mã bảo mật, tuân thủ chính sách giao thức; không tự áp dụng chuẩn hóa hoặc bỏ phân biệt chữ hoa nếu điều đó làm thay đổi bí mật được kỳ vọng.

## 8. Những lỗi phổ biến trong thực tế

**Lỗi: dùng `==` cho văn bản nhận từ bên ngoài.** Hai tham chiếu khác nhau có thể có nội dung giống nhau; so bằng `equals` hoặc một quy tắc chuẩn hóa được định nghĩa rõ.

**Lỗi: gọi `equals` trên biến có thể null.** Nếu cần so với chuỗi hằng, dùng `"constant".equals(value)`; nếu cả hai biến có thể null, dùng `Objects.equals` khi cách xử lý hai null phù hợp nghiệp vụ.

**Lỗi: nhầm kết quả String Pool với quy tắc chung.** Hai literal bằng nhau có thể cùng đối tượng, nhưng dữ liệu nối lúc chạy hoặc đọc từ API không nhất thiết như vậy. Kiểm thử nên bao gồm hai `new String("same")` để quan sát `== false` và `equals true`.

**Lỗi: cho rằng `equals` không phân biệt hoa/thường hoặc khoảng trắng.** `"Java".equals("java")` và `"x".equals("x ")` đều `false`. Nếu cần bỏ khoảng trắng hoặc biến đổi chữ, áp dụng `strip()` hay biến đổi có kiểm soát theo `Locale.ROOT`/locale thích hợp; nhớ rằng quy tắc chữ hoa có thể phức tạp với Unicode.

**Lỗi: cho rằng cùng cách hiển thị thì cùng chuỗi.** Dấu kết hợp và các dạng chuẩn hóa khác nhau có thể khiến `equals` sai. Cần kiểm thử bằng ký tự Unicode thực tế của miền dữ liệu, nhất là tên, văn bản đa ngữ và chuỗi sao chép từ nhiều nguồn.

**Lỗi: kiểm tra bằng hash thay vì equality.** Hai hash trùng không có nghĩa hai chuỗi có cùng nội dung. `HashMap`/`HashSet` xử lý va chạm bằng hợp đồng equals/hashCode; mã nghiệp vụ cũng cần kiểm tra giá trị đúng nghĩa.

## 9. Checklist để ghi nhớ và tự kiểm thử

1. Xác định đang hỏi **cùng đối tượng** (`==`) hay **cùng nội dung `String`** (`equals`).
2. Tạo hai chuỗi cùng nội dung bằng `new String` để chứng minh identity có thể khác.
3. So chuỗi literal, chuỗi rỗng, chữ hoa/thường, dấu cách, giá trị `null` và văn bản Unicode.
4. Kiểm tra `HashSet` gộp hai chuỗi bằng nhau; `HashMap` tra cứu được bằng khóa `String` khác tham chiếu.
5. Ghi nhớ `String.equals` chỉ trả true với đối số kiểu `String` có cùng nội dung UTF-16.
6. Với giá trị có thể null, chọn `"literal".equals(variable)` hoặc `Objects.equals` theo yêu cầu.
7. Không dùng `StringBuilder.equals` hay so mã hash để thay thế kiểm tra nội dung.
8. Nếu cần không phân biệt hoa, chuẩn hóa Unicode, bỏ khoảng trắng hoặc so theo locale, định nghĩa và áp dụng quy tắc tường minh.
9. Xác nhận phép so sánh có phục vụ đúng khái niệm **danh tính nghiệp vụ**: hai chuỗi giống nhau không nhất thiết là cùng thực thể.

**Điểm cốt lõi:** `String.equals(Object)` so sánh chính xác nội dung văn bản theo ngữ nghĩa của Java `String`, độc lập với việc hai biến có cùng tham chiếu hay không. `==` dùng để kiểm tra identity; String Pool chỉ giải thích vì sao `==` đôi lúc cũng trả `true` với hai literal giống nhau. Khi cần quy tắc tương đương rộng hơn, hãy xác định rõ việc chuẩn hóa và so sánh ngoài `String.equals`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="standard-types-002">Lớp bao số nguyên khác kiểu không mặc nhiên bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp bao số nguyên khác kiểu không mặc nhiên bằng nhau

## 1. Hai đối tượng có cùng số chưa chắc bằng nhau theo `equals()`

Java có các **lớp bao (wrapper classes)** cho các kiểu số nguyên nguyên thủy:

| Kiểu nguyên thủy | Lớp bao | Ví dụ tạo giá trị |
| --- | --- | --- |
| `byte` | `Byte` | `Byte.valueOf((byte) 42)` |
| `short` | `Short` | `Short.valueOf((short) 42)` |
| `int` | `Integer` | `Integer.valueOf(42)` |
| `long` | `Long` | `Long.valueOf(42L)` |

Bốn đối tượng đó đều biểu diễn giá trị toán học **42**, nhưng chúng thuộc **bốn lớp Java khác nhau**. Với các lớp bao số nguyên tiêu chuẩn, phương thức `equals(Object)` đòi hỏi đối tượng còn lại thuộc **đúng kiểu wrapper tương ứng** và có cùng giá trị được lưu. Vì vậy:

```java
Integer integer = 42;
Long longNumber = 42L;

System.out.println(integer.equals(longNumber)); // false
System.out.println(longNumber.equals(integer));  // false
```

Đây là **fragment minh họa**; chương trình chạy đầy đủ có ở phần sau. Điểm cần nắm: `equals()` của wrapper định nghĩa quan hệ bằng nhau **theo kiểu và giá trị**; nó không tự động chuyển cả hai bên về một kiểu số chung.

Đây là một lựa chọn có chủ ý về bản sắc của **đối tượng Java**, không đồng nghĩa phép so sánh số học `42 == 42L` sai. Khi bạn muốn so sánh **giá trị số**, cần nêu rõ phép chuyển đổi hay quy tắc so sánh.

## 2. Cơ chế của `equals()` trong các wrapper số nguyên

Về ý nghĩa, các phương thức triển khai giống quy tắc sau:

```java
// Minh họa ngữ nghĩa; KHÔNG phải mã nguồn triển khai thật:
boolean integerEquals(Object other, int value) {
    return other instanceof Integer that && value == that.intValue();
}

boolean longEquals(Object other, long value) {
    return other instanceof Long that && value == that.longValue();
}
```

Đây là **hai hàm mô phỏng hành vi**. Khi `Integer.equals` nhận `Long`, kiểm tra `instanceof Integer` không đạt; kết quả ngay lập tức là `false`. Khi `Long.equals` nhận `Integer`, kiểm tra `instanceof Long` cũng không đạt.

Điều này áp dụng tương tự cho `Byte` và `Short`. Các cặp sau có kết quả:

| Bên trái | Bên phải | `left.equals(right)` |
| --- | --- | --- |
| `Integer.valueOf(42)` | `Integer.valueOf(42)` | `true` |
| `Integer.valueOf(42)` | `Long.valueOf(42L)` | `false` |
| `Short.valueOf((short) 42)` | `Byte.valueOf((byte) 42)` | `false` |
| `Long.valueOf(42L)` | `Long.valueOf(42L)` | `true` |
| `Integer.valueOf(42)` | `null` | `false` |

Bảng thể hiện quan hệ đối xứng cho những ví dụ này: hai wrapper khác kiểu đều không bằng nhau theo cả hai chiều. Việc trả về `false` giữa hai lớp khác nhau **không vi phạm** hợp đồng `equals()`. Hợp đồng chỉ đòi hỏi tính phản xạ, đối xứng, bắc cầu, nhất quán và `x.equals(null) == false`; nó không đòi Java phải xem mọi cách biểu diễn cùng một số là một đối tượng logic.

`Objects.equals(a, b)` là công cụ so sánh có xử lý `null`: nếu cả hai cùng null thì `true`, một bên null thì `false`; các trường hợp còn lại ủy quyền cho `a.equals(b)`. **`Objects.equals` không tự chuyển kiểu wrapper.**

## 3. Ví dụ Java 17 chạy được

Tạo tệp `IntegerWrapperEqualityDemo.java`:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class IntegerWrapperEqualityDemo {

    // Chỉ chấp nhận bốn loại wrapper số nguyên.
    static boolean isIntegerWrapper(Number number) {
        return number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long;
    }

    // Quy tắc RIÊNG cho nghiệp vụ cần so giá trị số nguyên.
    // longValue() là chuyển đổi chính xác cho bốn wrapper trên.
    static boolean sameIntegerValue(Number a, Number b) {
        return isIntegerWrapper(a)
                && isIntegerWrapper(b)
                && a.longValue() == b.longValue();
    }

    public static void main(String[] args) {
        Integer i = Integer.valueOf(42);
        Long l = Long.valueOf(42L);
        Short s = Short.valueOf((short) 42);
        Byte b = Byte.valueOf((byte) 42);

        System.out.println("Integer.equals(Long): " + i.equals(l));
        System.out.println("Long.equals(Integer): " + l.equals(i));
        System.out.println("Integer.equals(Integer): "
                + i.equals(Integer.valueOf(42)));
        System.out.println("Short.equals(Byte): " + s.equals(b));
        System.out.println("Objects.equals(i, l): "
                + Objects.equals(i, l));

        Number left = i;
        Number right = l;
        System.out.println("Number references ==: " + (left == right));
        System.out.println("int vs long primitives ==: "
                + (i.intValue() == l.longValue()));
        System.out.println("boxed Integer vs primitive long ==: "
                + (i == 42L));

        System.out.println("sameIntegerValue(i, l): "
                + sameIntegerValue(i, l));
        System.out.println("sameIntegerValue(s, b): "
                + sameIntegerValue(s, b));
        System.out.println("sameIntegerValue(i, 43L): "
                + sameIntegerValue(i, Long.valueOf(43L)));
        System.out.println("sameIntegerValue(i, 42.0): "
                + sameIntegerValue(i, Double.valueOf(42.0)));

        System.out.println("Integer hash: " + i.hashCode());
        System.out.println("Long hash: " + l.hashCode());

        Set<Number> numbers = new HashSet<>();
        numbers.add(i);
        numbers.add(l);
        numbers.add(s);
        numbers.add(b);
        System.out.println("set size: " + numbers.size());
        System.out.println("contains Integer(42): "
                + numbers.contains(Integer.valueOf(42)));
        System.out.println("contains Long(42): "
                + numbers.contains(Long.valueOf(42L)));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac IntegerWrapperEqualityDemo.java
java IntegerWrapperEqualityDemo
```

Đầu ra chính xác:

```text
Integer.equals(Long): false
Long.equals(Integer): false
Integer.equals(Integer): true
Short.equals(Byte): false
Objects.equals(i, l): false
Number references ==: false
int vs long primitives ==: true
boxed Integer vs primitive long ==: true
sameIntegerValue(i, l): true
sameIntegerValue(s, b): true
sameIntegerValue(i, 43L): false
sameIntegerValue(i, 42.0): false
Integer hash: 42
Long hash: 42
set size: 4
contains Integer(42): true
contains Long(42): true
```

### Đọc kết quả theo từng lớp ngữ nghĩa

- `i.equals(l)` và `l.equals(i)` đều `false` vì một bên là `Integer`, bên kia là `Long`.
- `Objects.equals(i, l)` cũng `false`: phương thức này chỉ xử lý null-safe và dựa vào `i.equals(l)`.
- `left` và `right` được khai báo kiểu `Number`, nên phép `left == right` so sánh **tham chiếu** đến hai đối tượng khác nhau: `false`. Không có phép unboxing vì cả hai toán hạng ở đây là tham chiếu `Number`.
- `i.intValue() == l.longValue()` so sánh hai giá trị nguyên thủy; `int` được nâng lên `long` theo quy tắc thăng hạng số của Java, nên `42 == 42L` là `true`.
- `i == 42L` cũng `true` vì một toán hạng là số nguyên thủy `long`, Java unbox `i` thành `int` rồi thăng hạng để so sánh số nguyên thủy. Cú pháp `==` có thể cho kết quả khác nhau tùy **kiểu tĩnh của toán hạng**.
- `sameIntegerValue(i, l)` trả `true` vì ta chủ động định nghĩa phép so sánh theo `longValue()` cho bốn wrapper số nguyên.
- `Double.valueOf(42.0)` bị từ chối bởi `isIntegerWrapper`, dù giá trị toán học là 42. Đây là **phạm vi chính sách của helper**, không có nghĩa không thể so sánh số thực với số nguyên bằng API khác.
- Hai đối tượng `Integer(42)` và `Long(42)` cùng mã băm `42` nhưng vẫn không bằng nhau. **Hash bằng nhau không suy ra `equals()` đúng**.
- `HashSet<Number>` chứa đủ bốn wrapper khác kiểu và đều biểu diễn 42: `size() == 4`. Tập dùng `equals()`/`hashCode()` của từng đối tượng, **không dùng phép chuyển đổi số học tùy ý**.

## 4. Vì sao `==` không thể thay thế `equals()`?

`==` ở Java có hai ngữ nghĩa liên quan đến ví dụ này:

1. **Hai tham chiếu có kiểu tương thích để so sánh tham chiếu**: kiểm tra có cùng trỏ vào một đối tượng trong bộ nhớ hay không.
2. **Hai toán hạng thuộc ngữ cảnh so sánh số nguyên thủy**, có thể thông qua unboxing: kiểm tra giá trị số sau các quy tắc chuyển đổi số của ngôn ngữ.

Ví dụ riêng từng trường hợp:

```java
Integer boxedInt = Integer.valueOf(42);
long primitiveLong = 42L;
boolean numeric = boxedInt == primitiveLong; // true: unbox + widen

Number a = boxedInt;
Number b = Long.valueOf(42L);
boolean sameReference = (a == b); // false: so sánh tham chiếu
```

Hai dòng đầu dùng `==` với số nguyên thủy, hai dòng sau với tham chiếu. Đoạn code là **fragment**, không phải chương trình độc lập.

**Cảnh báo khi nhìn hai biến có kiểu `Integer` và `Long`:** biểu thức `integerObject == longObject` có thể bị trình biên dịch từ chối vì hai kiểu tham chiếu này không tương thích với phép so sánh tham chiếu; Java không có quy tắc “thấy hai wrapper khác loại thì luôn tự unbox”. Đừng suy diễn từ ví dụ `boxedInt == primitiveLong` sang mọi tổ hợp wrapper.

### Bộ nhớ đệm (cache) không phải hợp đồng bằng nhau

`Integer.valueOf` bảo đảm tái sử dụng đối tượng cho ít nhất dải `-128..127`. Điều này có thể làm `==` giữa hai biến `Integer` đôi lúc trả `true` vì chúng trỏ cùng một đối tượng. Ngoài dải đảm bảo, runtime có thể có chính sách cache rộng hơn; không được dùng kết quả `==` để suy luận hai số nguyên boxed có cùng giá trị.

```java
Integer first = Integer.valueOf(42);
Integer second = Integer.valueOf(42);

first == second;      // true trong dải được bảo đảm cache
first.equals(second); // true theo giá trị và kiểu
```

Fragment này dùng để minh họa; nếu mục tiêu là so sánh hai `Integer` theo giá trị, dùng `equals()` hoặc `Objects.equals()` khi cần null-safe.

## 5. Khi cần so sánh số nguyên khác wrapper theo giá trị

### Chuyển đổi rõ ràng trong miền giá trị an toàn

Cả bốn wrapper `Byte`, `Short`, `Integer`, `Long` đều biểu diễn giá trị thuộc miền của `long`. Vì vậy chuyển `longValue()` từ **chính bốn loại này** là chính xác và không mất thông tin:

```java
static boolean sameIntegerValue(Number a, Number b) {
    boolean supportedA = a instanceof Byte || a instanceof Short
            || a instanceof Integer || a instanceof Long;
    boolean supportedB = b instanceof Byte || b instanceof Short
            || b instanceof Integer || b instanceof Long;
    return supportedA && supportedB && a.longValue() == b.longValue();
}
```

Đoạn này là **helper minh họa**, không phải tệp Java chạy độc lập. Điều kiện lọc kiểu trước phép chuyển đổi đóng vai trò quan trọng: `Number` còn có các lớp khác như `Float`, `Double`, `BigInteger`, `BigDecimal`. Với chúng, `longValue()` có thể **cắt phần thập phân**, mất độ chính xác hoặc chỉ giữ phần thấp của giá trị nằm ngoài miền `long`.

Nếu dữ liệu bao gồm `BigInteger` lớn hơn giới hạn `long`, không nên dùng `longValue()` để quyết định hai giá trị bằng nhau. Cần chọn biểu diễn chung đủ chính xác theo miền nghiệp vụ, chẳng hạn chuyển các wrapper số nguyên và `BigInteger` về `BigInteger`, hoặc thiết kế một kiểu giá trị riêng.

### Không tự động đổi chính sách của `equals()`

Một ứng dụng có thể định nghĩa helper `sameIntegerValue` trả `true` với `Integer(42)` và `Long(42)`. Điều này **không làm thay đổi** kết quả `Integer.equals(Long)` hoặc `Long.equals(Integer)` vốn vẫn `false`.

Không thể “sửa” `Integer.equals()` hoặc `Long.equals()` trong thư viện chuẩn chỉ bằng cách dùng helper. Nếu nghiệp vụ muốn coi các kiểu nguồn có cùng số là một khóa, hãy **chuẩn hóa về một kiểu khóa chung** trước khi lưu và so sánh.

Ví dụ nếu miền chỉ cho phép số trong `long`, ta có thể dùng `Long` làm khóa:

```java
Long fromInteger = Long.valueOf(Integer.valueOf(42).longValue());
Long fromLong = Long.valueOf(Long.valueOf(42L).longValue());

boolean equalKeys = fromInteger.equals(fromLong); // true
```

Đây là fragment minh họa. Chuẩn hóa cả hai bên thành `Long` giúp ý nghĩa “cùng giá trị nguyên trong miền long” nhất quán. Trong hệ thống thật, chính sách chuẩn hóa nên áp dụng tại ranh giới dữ liệu (đọc input, tạo key, ghi collection), tránh lúc thì so sánh theo wrapper gốc, lúc thì dùng helper giá trị.

## 6. Ảnh hưởng lên `HashSet`, `HashMap` và các collection

`HashSet` dựa trên quan hệ `equals()` của phần tử và mã băm tương ứng; `HashMap` dùng quan hệ này cho khóa. Khi thêm `Integer.valueOf(42)` và `Long.valueOf(42L)` vào `HashSet<Number>`, chúng được xem là **hai phần tử khác nhau**.

```java
Set<Number> set = new java.util.HashSet<>();
set.add(Integer.valueOf(42));
set.add(Long.valueOf(42L));
System.out.println(set.size()); // 2
```

Đây là fragment: cần `import java.util.Set;` hoặc viết tên kiểu đầy đủ. `hashCode()` của cả `Integer(42)` và `Long(42)` đều trả `42` trong ví dụ, nhưng collision không khiến hai phần tử bị coi bằng nhau. Collection còn phải dùng `equals()` để phân biệt đối tượng.

Hệ quả thực tế: mã dùng `Map<Number, String>` có thể lưu hai entry riêng cho `Integer(42)` và `Long(42)`, ngay cả khi chúng trùng mã băm và trông giống một mã khách hàng. Nếu ý định là “mọi ID nguyên đều cùng hệ quy chiếu”, nên chọn **một kiểu khóa cố định**, thí dụ `Map<Long, String>`, và kiểm tra/chuyển đổi mọi ID trước khi đưa vào map.

Không sửa `hashCode()` đơn lẻ để cố gộp các wrapper khác kiểu. Với Java collection dựa trên hash, cùng mã băm là điều kiện cần cho cặp bằng nhau, nhưng **không đủ để biến hai đối tượng thành bằng nhau**.

## 7. Các trường hợp dễ nhầm và lưu ý về kiểu

### a. `Number` là lớp cha chung, không định nghĩa “equals theo số học”

Các wrapper `Byte`, `Short`, `Integer`, `Long` là con của `Number`, nhưng việc gán chúng cho tham chiếu `Number` **không khiến chúng có chung thuật toán `equals()` theo giá trị số**.

```java
Number n1 = Integer.valueOf(42);
Number n2 = Long.valueOf(42L);

System.out.println(n1.equals(n2)); // false
```

Việc gọi phương thức động vẫn chạy implementation của đối tượng thực tế là `Integer` hoặc `Long`.

### b. Phân biệt `Character` và `Boolean`

`Character` bao ký tự nguyên thủy `char`, có mã ký tự là số nguyên không dấu theo nghĩa giá trị UTF-16 code unit, nhưng **không kế thừa `Number`** và không cùng `equals()` với `Integer` hay `Long`. `Boolean` cũng không phải wrapper số nguyên. Không đưa chúng vào helper `sameIntegerValue(Number, Number)` mà không có quy tắc nghiệp vụ rõ ràng.

### c. Xử lý null

`Integer.equals(null)` trả `false`; nhưng gọi `nullableInteger.equals(anything)` khi `nullableInteger == null` sẽ gây `NullPointerException`. Dùng `Objects.equals(a, b)` nếu cần null-safe mà vẫn muốn **giữ chính sách kiểu của wrapper**:

```java
java.util.Objects.equals(Integer.valueOf(42), Long.valueOf(42L)); // false
java.util.Objects.equals(null, null);                       // true
java.util.Objects.equals(Integer.valueOf(42), null);        // false
```

Đây là fragment minh họa các biểu thức. Nếu muốn helper so sánh theo **số học** chấp nhận null theo một chính sách riêng, phải khai báo rõ: null bằng null hay không, null có được dùng làm khóa hay không; đừng mặc định `Objects.equals` sẽ đổi semantics wrapper.

### d. Tràn số và miền hỗ trợ

So sánh bằng cách ép cả hai về `int` có thể cắt mất thông tin của `Long` lớn hơn `Integer.MAX_VALUE`. Phương thức `longValue()` an toàn cho bốn wrapper nguyên được nêu, nhưng không an toàn tổng quát cho mọi `Number`, đặc biệt `BigInteger`/`BigDecimal`/`Double`.

Nếu chọn `Long` làm kiểu chuẩn hóa, hãy đảm bảo đầu vào nằm trong phạm vi `long`. Nếu chọn `BigInteger`, dùng chuyển đổi **chính xác** từ các kiểu nguyên tiêu chuẩn; còn số thực/decimal có phần thập phân cần quy tắc riêng. Việc làm tròn hay cắt phần thập phân phải là quyết định nghiệp vụ được ghi rõ.

### e. So sánh trong `TreeSet`/`TreeMap` dùng `Comparator`

Khác với các hash collection, collection sắp xếp xác định sự trùng khóa bằng kết quả `compare(a, b) == 0` của comparator (hoặc natural ordering). Một comparator tự viết so sánh các `Number` theo `longValue()` có thể coi `Integer(42)` và `Long(42)` tương đương dù `equals()` cho `false`. Điều này làm thứ tự **không nhất quán với equals**, có thể tạo hành vi khác `HashSet` và vi phạm kỳ vọng của các API `Set`.

Hãy chọn và tài liệu hóa một chính sách khóa nhất quán cho collection; nếu phải định nghĩa comparator xuyên kiểu, kiểm tra tính phản xạ của `compare(x,x)==0`, tính đối dấu, tính bắc cầu, và tính tương thích với mục đích sử dụng.

## 8. Checklist thực hành

1. **Hỏi “bằng nhau” là gì:** cùng wrapper và giá trị, hay cùng giá trị nguyên bất kể wrapper? Đây là hai quan hệ khác nhau, đều có thể hợp lệ trong ngữ cảnh riêng.
2. **Đọc kiểu tĩnh trước khi dùng `==`:** hai tham chiếu so sánh danh tính; một wrapper và một primitive có thể kích hoạt unboxing + promotion; hai wrapper khác kiểu không tự luôn so sánh theo số học.
3. **Nếu cần so sánh wrapper theo hợp đồng Java:** sử dụng `equals()`, hoặc `Objects.equals()` khi có thể null. Kết quả `Integer(42)` với `Long(42)` là `false`.
4. **Nếu cần so sánh số nguyên xuyên kiểu:** kiểm soát tập kiểu hỗ trợ và chuyển sang biểu diễn chung không mất dữ liệu, như `long` cho `Byte/Short/Integer/Long`.
5. **Nếu dùng làm khóa collection:** chuẩn hóa đầu vào về một kiểu khóa trước khi thêm/tìm; đừng dựa vào hash giống nhau hoặc ép kiểu ngầm.
6. **Thử các ca biên:** `null`, số âm, `Long.MIN_VALUE`/`Long.MAX_VALUE`, dữ liệu ngoài miền `int`, và các `Number` không phải wrapper nguyên.

### Tự kiểm tra

**Câu 1:** `Integer.valueOf(7).equals(Long.valueOf(7L))` là gì? — `false` vì hai đối tượng khác lớp.

**Câu 2:** Hai wrapper khác kiểu có cùng `hashCode()` thì có bị `HashSet` gộp thành một phần tử không? — Không; `HashSet` còn kiểm tra quan hệ bằng nhau.

**Câu 3:** `Integer.valueOf(7) == 7L` cho kết quả gì? — `true`: có một toán hạng nguyên thủy nên wrapper được unbox, rồi so sánh số học. Nhưng không được suy diễn rằng mọi phép `==` giữa hai wrapper đều có ngữ nghĩa đó.

**Câu 4:** Vì sao không dùng `Number.longValue()` trực tiếp cho mọi `Number`? — Vì `Double` có phần lẻ và `BigInteger` có thể vượt phạm vi `long`; phép chuyển đổi có thể mất thông tin, làm hai giá trị khác nhau bị xem như bằng nhau.

## Kết luận

**Cùng giá trị số không có nghĩa cùng đối tượng logic trong Java.** `Byte`, `Short`, `Integer` và `Long` áp dụng `equals()` theo **đúng loại wrapper cộng với giá trị lưu trữ**, nên `Integer(42).equals(Long(42L))` là `false`. Khi bài toán cần so sánh số nguyên xuyên kiểu, hãy nói rõ miền và chuyển đổi chính xác sang một biểu diễn chung. Giữ chính sách đó nhất quán ở vị trí so sánh, chuẩn hóa dữ liệu và khi chọn khóa cho collection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="standard-types-003">Float và Double: NaN, +0.0 và -0.0</a>

<details>
<summary>Xem chi tiết</summary>

# Float và Double trong Java: NaN, +0.0 và -0.0

## 1. Tại sao quan trọng với hợp đồng đối tượng?

Java có hai kiểu dấu chấm động nguyên thủy là **float** (32 bit) và **double** (64 bit), cùng hai lớp bao tương ứng **Float** và **Double**. Ngay cả khi hai giá trị mang ý nghĩa số học giống nhau, phép so sánh **nguyên thủy bằng ==** có thể khác với phương thức **equals(Object)** của lớp bao, đặc biệt ở **NaN** và **số không có dấu**.

- **NaN (Not a Number):** hai giá trị NaN so bằng == trên số thực nguyên thủy cho kết quả false, nhưng các lớp bao Float/Double coi các NaN là bằng nhau theo equals.
- **+0.0 và -0.0:** số thực nguyên thủy coi chúng bằng nhau theo ==, nhưng lớp bao phân biệt chúng trong equals.
- Phương thức compare/compareTo của Float/Double được thiết kế nhất quán với equals lớp bao, chứ không chỉ dùng toán tử < hoặc > trên số thực.
- Các phương thức hashCode của lớp bao tuân theo đúng quy tắc equals, vì HashSet/HashMap phụ thuộc vào hợp đồng equals–hashCode.

Vì vậy, khi tự viết equals và hashCode cho đối tượng có trường float/double, cần quyết định **quan hệ bằng nhau mong muốn** và xử lý cả NaN lẫn signed zero. Các ví dụ bên dưới dùng Java 17+.

## 2. NaN: giá trị đặc biệt và toán tử số thực nguyên thủy

NaN biểu thị một kết quả không phải số thực xác định theo IEEE 754. Ví dụ:

~~~java
double a = 0.0d / 0.0d;
double b = Math.sqrt(-1.0d);
float c = 0.0f / 0.0f;
~~~

Những phép tính dấu chấm động trên tạo NaN; chúng không ném ArithmeticException như phép chia số nguyên cho 0. Hằng sẵn có: **Double.NaN** và **Float.NaN**.

Với một giá trị double n = Double.NaN:

| Biểu thức | Kết quả |
| --- | --- |
| n == n | false |
| n != n | true |
| n < 1.0 | false |
| n > 1.0 | false |
| n <= 1.0 | false |
| n >= 1.0 | false |
| Double.isNaN(n) | true |

Bất kỳ so sánh thứ tự nguyên thủy nào có NaN cũng trả false. Đây là lý do các biểu thức phủ định của phép so sánh không đủ để suy ra thứ tự khi dữ liệu có thể chứa NaN.

**Nhận biết NaN đúng mục đích:**

~~~java
boolean isDoubleNaN = Double.isNaN(a);
boolean isFloatNaN = Float.isNaN(c);
~~~

Có thể dùng a != a để nhận biết NaN ở số nguyên thủy, nhưng Double.isNaN/Float.isNaN truyền đạt chủ ý rõ ràng hơn. NaN không phải vô cực; dùng Double.isInfinite/Float.isInfinite khi cần xác định Infinity hoặc -Infinity.

## 3. Số không có dấu: +0.0 và -0.0

IEEE 754 có hai biểu diễn số không, khác nhau ở bit dấu:

- **+0.0**: bit dấu 0.
- **-0.0**: bit dấu 1.

Với các toán hạng số thực nguyên thủy, hai giá trị này được coi là bằng nhau:

~~~java
System.out.println(+0.0d == -0.0d); // true
System.out.println(1.0d / +0.0d);    // Infinity
System.out.println(1.0d / -0.0d);    // -Infinity
~~~

Chúng bằng nhau theo == nhưng phép chia có thể cho vô cực khác dấu. Đây là lý do phần biểu diễn dấu không phải chi tiết vô nghĩa trong mọi ứng dụng.

Có thể quan sát bit bằng các API:

~~~java
Double.doubleToRawLongBits(+0.0d); // 0L
Double.doubleToRawLongBits(-0.0d); // Long.MIN_VALUE
Float.floatToRawIntBits(+0.0f);   // 0
Float.floatToRawIntBits(-0.0f);   // Integer.MIN_VALUE
~~~

Các đoạn này minh họa biểu thức trong một ngữ cảnh Java; chưa phải một lớp chương trình đầy đủ. Hai phương thức có chữ Raw giữ nguyên bit của biểu diễn. Phương thức không có chữ Raw cũng giữ khác biệt signed zero, nhưng chuẩn hóa các mẫu bit NaN.

## 4. Float.equals() và Double.equals() có quy tắc riêng

Hợp đồng của lớp bao là:

- **Double.equals(Object)** chỉ true khi đối số cũng là Double và hai giá trị có cùng bit theo **Double.doubleToLongBits**.
- **Float.equals(Object)** chỉ true khi đối số cũng là Float và hai giá trị có cùng bit theo **Float.floatToIntBits**.

Hai hàm chuyển bit **chuẩn hóa mọi giá trị NaN** thành một biểu diễn NaN quy ước. Vì vậy các NaN khác payload vẫn được equals coi là bằng nhau. Riêng +0.0 và -0.0 có bit dấu khác nên **không bằng nhau theo wrapper equals**.

| Cặp số cùng kiểu | == trên nguyên thủy | equals của lớp bao |
| --- | --- | --- |
| 1.5 và 1.5 | true | true |
| NaN và NaN | false | true |
| +0.0 và -0.0 | true | false |
| +0.0 và +0.0 | true | true |
| -0.0 và -0.0 | true | true |
| Infinity và Infinity | true | true |
| Infinity và -Infinity | false | false |

Ví dụ:

~~~java
double n1 = Double.NaN;
double n2 = 0.0d / 0.0d;
System.out.println(n1 == n2); // false
System.out.println(Double.valueOf(n1).equals(Double.valueOf(n2))); // true

double pz = +0.0d;
double nz = -0.0d;
System.out.println(pz == nz); // true
System.out.println(Double.valueOf(pz).equals(Double.valueOf(nz))); // false
~~~

Cần phân biệt thêm **== giữa hai tham chiếu lớp bao**: nếu cả hai toán hạng là đối tượng Double (hoặc Float), == so sánh **danh tính đối tượng**, không phải giá trị. Nếu một toán hạng nguyên thủy, Java có thể thực hiện unboxing, và unboxing giá trị null gây NullPointerException. Khi đọc biểu thức, luôn xem chính xác kiểu của hai vế.

**Float.equals không thể coi Double là bằng nhau**, và ngược lại, kể cả hai đối tượng có cách hiển thị gần giống nhau. Đây là hai lớp khác kiểu.

## 5. So sánh thứ tự: compare và compareTo

**Double.compare(x, y)** và **Float.compare(x, y)** dùng một thứ tự đầy đủ tương thích với equals lớp bao:

1. Mọi NaN cùng kiểu được coi tương đương nhau khi so sánh: compare(NaN, NaN) bằng 0.
2. NaN đứng lớn hơn mọi giá trị số, kể cả +Infinity.
3. -0.0 đứng nhỏ hơn +0.0.
4. Với số thông thường và các vô cực, thứ tự số học tự nhiên được duy trì.

Ví dụ:

~~~java
Double.compare(Double.NaN, Double.NaN);             // 0
Double.compare(Double.NaN, Double.POSITIVE_INFINITY); // 1
Double.compare(-0.0d, +0.0d);                        // -1
Float.compare(-0.0f, +0.0f);                        // -1
~~~

Các hàm compare trả số âm, 0 hoặc số dương; với các cặp ghi ở đây, kết quả Java cụ thể lần lượt là 0, 1, -1, -1. CompareTo của lớp bao dùng cùng quy tắc.

Để sắp xếp Double/Float, ưu tiên các comparator rõ ràng:

~~~java
// Chỉ là khai báo minh họa, yêu cầu import java.util.Comparator.
Comparator<Double> doubleComparator = Double::compare;
Comparator<Float> floatComparator = Float::compare;
~~~

**Lỗi thường gặp:**

~~~java
// Sai: mất phần thập phân, lỗi với NaN và signed zero.
Comparator<Double> bad = (a, b) -> (int) (a - b);

// Cũng không phù hợp với wrapper equals khi chứa NaN/signed zero.
Comparator<Double> bad2 = (a, b) -> a < b ? -1 : a > b ? 1 : 0;
~~~

Bộ so sánh thứ hai có thể trả 0 dù một bên NaN và bên còn lại là số bình thường; hoặc trả 0 cho hai signed zero. Với **TreeSet** hoặc **TreeMap**, comparator quyết định xem hai khóa có cùng vị trí logic hay không qua kết quả compare == 0, nên comparator khác quy tắc equals có thể gây hành vi ngạc nhiên so với HashSet/HashMap.

## 6. HashCode của Float và Double: tính tương thích bắt buộc

Hai đối tượng bằng nhau theo equals phải có cùng hashCode. Các phương thức **Double.hashCode(double)** và **Float.hashCode(float)** được thiết kế tương thích với quy tắc lớp bao:

- Mọi NaN cùng kiểu được chuẩn hóa nên có hash giống nhau.
- +0.0 và -0.0 có bit dấu khác nên hash khác nhau.
- Không yêu cầu hai giá trị không bằng nhau phải có hash khác nhau: va chạm băm vẫn hợp lệ.

Một số kết quả cụ thể:

~~~java
Double.hashCode(+0.0d); // 0
Double.hashCode(-0.0d); // Integer.MIN_VALUE
Float.hashCode(+0.0f);  // 0
Float.hashCode(-0.0f);  // Integer.MIN_VALUE
~~~

Điều này nhất quán với Double.equals/Float.equals vì các phương thức equals **phân biệt** signed zero.

Ví dụ bộ sưu tập:

~~~java
// Đoạn minh họa trong ngữ cảnh đã import Set và HashSet.
Set<Double> values = new HashSet<>();
values.add(Double.NaN);
values.add(Double.NaN); // Trùng theo Double.equals.
values.add(+0.0d);
values.add(-0.0d);       // Khác theo Double.equals.
System.out.println(values.size()); // 3
~~~

HashSet có một phần tử NaN và hai phần tử số không khác dấu. Nếu thay bằng HashSet<Float> thì nguyên tắc tương tự.

**Không nên** dùng Double.doubleToRawLongBits hoặc Float.floatToRawIntBits trực tiếp làm nền để tự băm đối tượng mà equals lại gộp mọi NaN; các NaN mang bit thô khác nhau có thể tạo hash khác nhau dù equals true. Các hàm doubleToLongBits/floatToIntBits chuẩn hóa NaN và phù hợp hơn nếu thực hiện quy tắc lớp bao.

## 7. Chương trình Java 17+ có thể biên dịch/chạy

Lưu đúng tên **FloatingEqualityDemo.java**:

~~~java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FloatingEqualityDemo {
    public static void main(String[] args) {
        double nanA = Double.NaN;
        double nanB = 0.0d / 0.0d;
        double positiveZero = +0.0d;
        double negativeZero = -0.0d;

        System.out.println("double NaN == NaN: " + (nanA == nanB));
        System.out.println("Double.equals NaN: "
                + Double.valueOf(nanA).equals(Double.valueOf(nanB)));
        System.out.println("Double.compare NaN: "
                + Double.compare(nanA, nanB));
        System.out.println("Double.isNaN: " + Double.isNaN(nanA));
        System.out.println("NaN hashes equal: "
                + (Double.hashCode(nanA) == Double.hashCode(nanB)));

        System.out.println("double +0 == -0: "
                + (positiveZero == negativeZero));
        System.out.println("Double.equals zeros: "
                + Double.valueOf(positiveZero)
                        .equals(Double.valueOf(negativeZero)));
        System.out.println("Double.compare(-0,+0): "
                + Double.compare(negativeZero, positiveZero));
        System.out.println("Zero hashes differ: "
                + (Double.hashCode(positiveZero)
                        != Double.hashCode(negativeZero)));

        Set<Double> doubles = new HashSet<>();
        doubles.add(nanA);
        doubles.add(nanB);
        doubles.add(positiveZero);
        doubles.add(negativeZero);
        System.out.println("HashSet<Double> size: " + doubles.size());

        System.out.println("Arrays.equals double NaNs: "
                + Arrays.equals(new double[] {nanA},
                        new double[] {nanB}));
        System.out.println("Arrays.equals double zeros: "
                + Arrays.equals(new double[] {positiveZero},
                        new double[] {negativeZero}));

        float floatNanA = Float.NaN;
        float floatNanB = 0.0f / 0.0f;
        float floatPositiveZero = +0.0f;
        float floatNegativeZero = -0.0f;

        System.out.println("float NaN == NaN: "
                + (floatNanA == floatNanB));
        System.out.println("Float.equals NaN: "
                + Float.valueOf(floatNanA)
                        .equals(Float.valueOf(floatNanB)));
        System.out.println("Float.compare NaN: "
                + Float.compare(floatNanA, floatNanB));
        System.out.println("float +0 == -0: "
                + (floatPositiveZero == floatNegativeZero));
        System.out.println("Float.equals zeros: "
                + Float.valueOf(floatPositiveZero)
                        .equals(Float.valueOf(floatNegativeZero)));
        System.out.println("Float.compare(-0,+0): "
                + Float.compare(floatNegativeZero, floatPositiveZero));
        System.out.println("Float zero hashes differ: "
                + (Float.hashCode(floatPositiveZero)
                        != Float.hashCode(floatNegativeZero)));
    }
}
~~~

Chạy từ thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 FloatingEqualityDemo.java
java FloatingEqualityDemo
~~~

**Stdout dự kiến chính xác:**

~~~text
double NaN == NaN: false
Double.equals NaN: true
Double.compare NaN: 0
Double.isNaN: true
NaN hashes equal: true
double +0 == -0: true
Double.equals zeros: false
Double.compare(-0,+0): -1
Zero hashes differ: true
HashSet<Double> size: 3
Arrays.equals double NaNs: true
Arrays.equals double zeros: false
float NaN == NaN: false
Float.equals NaN: true
Float.compare NaN: 0
float +0 == -0: true
Float.equals zeros: false
Float.compare(-0,+0): -1
Float zero hashes differ: true
~~~

Giải thích:

1. Hai NaN không bằng nhau theo == nguyên thủy, nhưng bằng nhau theo wrapper equals, có compare bằng 0 và hash giống nhau.
2. Hai signed zero bằng nhau theo == nhưng khác theo wrapper equals, compare và hash.
3. HashSet<Double> chỉ chứa một NaN (bất kể cách tạo) và hai số không khác dấu.
4. **Arrays.equals(double[], double[])** và bản float[] sử dụng quy tắc so sánh phần tử phù hợp với các bit chuẩn hóa: các NaN tương đương nhau, hai signed zero khác nhau. Không thể hiểu rằng mọi trường hợp so sánh mảng số thực đều áp dụng == nguyên thủy.

Chương trình này là ví dụ độc lập; các khối mã ngắn ở những phần trước chỉ minh họa cách hoạt động của API.

## 8. Viết equals/hashCode cho lớp có trường double hoặc float

Nếu lớp **Sample** có trường double value và muốn quan hệ bằng nhau giống Double.equals, nên dùng cặp:

~~~java
// Đặt trong lớp Sample, giả định lớp final và có trường double value.
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Sample other)) {
        return false;
    }
    return Double.compare(value, other.value) == 0;
}

@Override
public int hashCode() {
    return Double.hashCode(value);
}
~~~

Đây là trích đoạn trong lớp, không phải chương trình chạy độc lập. Với float, dùng **Float.compare** và **Float.hashCode** tương ứng. Kiểm tra cùng tham chiếu, null và loại đối tượng vẫn quan trọng; với hệ phân cấp có kế thừa, còn cần xử lý quan hệ đối xứng/bắc cầu khi mở rộng equals.

**Vì sao không dùng == trong equals rồi Double.hashCode trong hashCode?** Nếu equals xét primitive value == other.value, hai đối tượng chứa +0.0 và -0.0 sẽ bằng nhau nhưng nhận hashCode khác nhau. Như vậy vi phạm điều kiện **equals true kéo theo hash bằng nhau**.

Với NaN, phép == luôn false, nên một cài đặt ngây thơ thiếu bước cùng tham chiếu còn có thể khiến một đối tượng chứa NaN **không bằng chính nó**, vi phạm phản xạ của equals.

### 8.1. Khi nghiệp vụ muốn gộp +0.0 và -0.0

Không phải mọi lớp giá trị bắt buộc phải giữ chính sách signed zero của Double.equals. Nếu bài toán coi +0.0 và -0.0 cùng một giá trị, cần chuẩn hóa **trước cả so sánh lẫn băm**:

~~~java
// Các phương thức minh họa bên trong lớp Measurement final
// có trường double value không đổi.
private static double normalizeZero(double x) {
    return x == 0.0d ? 0.0d : x;
}

@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (!(obj instanceof Measurement other)) {
        return false;
    }
    return Double.doubleToLongBits(normalizeZero(value))
            == Double.doubleToLongBits(normalizeZero(other.value));
}

@Override
public int hashCode() {
    return Double.hashCode(normalizeZero(value));
}
~~~

Quy tắc tùy chỉnh này gộp hai signed zero và vẫn coi mọi NaN là bằng nhau. Việc chuẩn hóa ở **cả hai phương thức** giữ đúng hợp đồng hash. Nếu cần sắp xếp những đối tượng Measurement này, comparator cũng phải dùng cùng quy tắc nếu muốn compare == 0 tương đương với equals.

Nếu dùng một **record** có component float/double, Java sinh ra equals/hashCode theo quy tắc phân biệt signed zero và coi NaN tương đương. Khi cần equality tùy biến, không được chỉ thay đổi equals mà bỏ quên hashCode.

### 8.2. Tránh dùng so sánh gần đúng làm equals

Công thức kiểu trị tuyệt đối của hiệu nhỏ hơn epsilon thường hữu ích để kiểm tra *gần bằng nhau* trong tính toán. Nhưng nếu dùng trực tiếp làm Object.equals, nó có thể vi phạm **tính bắc cầu**: a gần b, b gần c nhưng a không gần c. Cách kiểm tra gần đúng cũng cần xử lý NaN/vô cực và tràn số thích hợp.

Hãy tạo phương thức riêng như **approximatelyEqual(a, b, tolerance)** cho nhu cầu dung sai, hoặc định nghĩa phép lượng tử hóa thành các nhóm giá trị có quan hệ bằng nhau và hash phù hợp nếu thực sự cần đối tượng làm khóa băm.

## 9. Cách chọn API theo nhu cầu

| Nhu cầu | Công cụ phù hợp |
| --- | --- |
| Xác định có phải NaN | Double.isNaN / Float.isNaN |
| Kiểm tra nguyên thủy theo toán tử IEEE | ==, !=, <, > với float/double |
| So sánh đối tượng wrapper | Double.equals / Float.equals |
| Xếp thứ tự tương thích với wrapper equals | Double.compare / Float.compare |
| Băm phù hợp wrapper | Double.hashCode / Float.hashCode |
| So sánh mảng float[]/double[] | Arrays.equals |
| Băm mảng theo phần tử | Arrays.hashCode |
| Phân biệt bit dấu số không | doubleToRawLongBits / floatToRawIntBits |
| Gộp signed zero theo yêu cầu nghiệp vụ | Chuẩn hóa thống nhất trong equals, hashCode và comparator |
| Kiểm tra số thực gần nhau theo dung sai | Phương thức riêng, không tùy tiện thay cho Object.equals |

## 10. Những nhầm lẫn cần tránh

**“Hai NaN là hai giá trị khác nhau trong mọi ngữ cảnh.”** Không đúng: == nguyên thủy trả false, nhưng wrapper equals trả true cho mọi NaN cùng kiểu và compare trả 0.

**“+0.0 và -0.0 bằng nhau nên hai Double phải equals.”** Không đúng: == nguyên thủy trả true, Double.equals phân biệt dấu để tạo ra quy tắc đối tượng ổn định với hash và thứ tự.

**“Nếu Double.compare(x, y) == 0 thì x == y trên số nguyên thủy.”** Không đúng với NaN. Cặp compare == 0 tương đương quan hệ wrapper equals của cùng kiểu.

**“Nếu khác nhau theo equals thì hashCode phải khác.”** Không đúng: collision được phép. Nhưng hai giá trị bằng nhau theo equals bắt buộc có cùng hash.

**“Tự băm bit thô sẽ luôn đúng.”** Không đúng nếu equals gộp các NaN khác payload hoặc gộp signed zero. Chỉ dùng một cách băm nhất quán với quan hệ bằng nhau đã chọn.

**“Bỏ kiểm tra cùng tham chiếu không gây sao vì chỉ có trường số.”** Nguy hiểm nếu equals dùng == với NaN, làm mất tính phản xạ.

**“Dùng Comparator tùy ý không ảnh hưởng collection.”** Không đúng đối với TreeSet/TreeMap: compare == 0 quyết định đồng nhất khóa trong tập/sơ đồ có thứ tự, đôi khi khác với equals.

## 11. Checklist kiểm thử

Khi kiểm thử lớp chứa float/double, nên kiểm tra tất cả tình huống:

1. **Tự so sánh:** x.equals(x) luôn true, kể cả khi x chứa NaN.
2. **Hai đối tượng có NaN:** kết quả theo quy tắc đã công bố; nếu dùng wrapper semantics thì true.
3. **Hai signed zero:** kết quả theo quy tắc đã công bố; wrapper semantics phân biệt, custom normalized semantics thì gộp.
4. **Tính băm:** mọi cặp equals true đều phải có hashCode bằng nhau.
5. **So sánh thứ tự:** nếu có comparator/Comparable, xem compare == 0 có phản ánh cùng quan hệ bằng nhau như thiết kế không.
6. **Collection:** thêm NaN và hai signed zero vào HashSet/TreeSet rồi kiểm tra số phần tử phù hợp quy tắc so sánh.
7. **Giữ nguyên dữ liệu:** equals/hashCode không cho kết quả ngẫu nhiên khi trạng thái so sánh không đổi.
8. **Phân biệt kiểu:** Float.equals(Double) luôn false; không suy luận bằng nhau từ chuỗi in ra giống nhau.
9. **Số thường, vô cực, null:** kiểm tra trường hợp không có NaN hoặc signed zero vẫn tuân thủ hợp đồng.
10. **Đối tượng bất biến:** tránh thay đổi trường dùng trong equals/hashCode sau khi dùng làm khóa HashMap/HashSet.

**Ghi nhớ:** với số thực nguyên thủy, NaN không bằng NaN và +0.0 bằng -0.0 theo ==; với wrapper Float/Double, **NaN bằng NaN còn hai signed zero khác nhau**. Thiết kế equals, hashCode và comparator theo cùng một quy tắc để bộ sưu tập và phép so sánh hoạt động nhất quán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="standard-types-004">Phân biệt == trên số thực với equals() của lớp bao</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `==` trên số thực với `equals()` của lớp bao `Double` và `Float`

## 1. Đang so sánh giá trị nguyên thủy hay đối tượng?

Trong Java, `double` và `float` là **kiểu nguyên thủy**; `Double` và `Float` là **lớp bao** (wrapper). Hai biểu thức sau trông giống nhau nhưng dựa vào quy tắc khác nhau:

```java
double a = 1.5;
double b = 1.5;
boolean primitiveEqual = (a == b);           // true
boolean wrapperEqual = Double.valueOf(a)
        .equals(Double.valueOf(b));          // true
```

Đây là **đoạn mã minh họa**, không phải chương trình Java độc lập. Kết quả trên tình cờ cùng là `true`; cách đánh giá biểu thức mới là điểm cần phân biệt:

- **`a == b` với hai toán hạng số nguyên thủy:** Java thực hiện phép so sánh số theo quy tắc số thực của IEEE 754; với `float` và `double` trộn nhau, phép nâng kiểu số (`binary numeric promotion`) được áp dụng trước khi so sánh.
- **`x.equals(y)` với `x` là `Double` hoặc `Float`:** gọi phương thức trên đối tượng `x` và kiểm tra giá trị theo quy tắc của lớp bao, bao gồm xử lý đặc biệt cho `NaN` và `+0.0`/`-0.0`. Đối số phải thuộc **đúng lớp bao** (`Double` đối với `Double.equals`, `Float` đối với `Float.equals`).
- **`x == y` với hai biến tham chiếu `Double` (hoặc hai `Float`):** so sánh **cùng tham chiếu đối tượng hay không**, không dùng `equals()` và không tự bỏ hộp khi cả hai toán hạng đều là tham chiếu.

Điều này dẫn tới ba câu hỏi khác nhau: hai **giá trị nguyên thủy** có bằng nhau theo `==` không; hai **đối tượng bao** có bằng nhau theo `equals()` không; và hai **tham chiếu** có trỏ đúng cùng đối tượng không.

## 2. `NaN`: `==` là `false`, nhưng `Double.equals()` có thể là `true`

`NaN` (Not a Number) đại diện kết quả không xác định của một số phép toán số thực như `0.0 / 0.0`. Với toán tử `==` trên số thực nguyên thủy:

```java
double x = Double.NaN;
System.out.println(x == x);                         // false
System.out.println(Double.valueOf(x).equals(
        Double.valueOf(Double.NaN)));               // true
```

Theo quy tắc so sánh số thực, **bất cứ so sánh bằng `==` nào có toán hạng `NaN` đều trả về `false`**, kể cả so sánh một biến với chính nó. Muốn phát hiện NaN, dùng `Double.isNaN(value)` hoặc `Float.isNaN(value)`.

Trong khi đó, `Double.equals(Object)` quy định **hai giá trị `Double` mang NaN được coi là bằng nhau**, kể cả các mẫu bit NaN khác nhau. `Float.equals(Object)` cũng coi các `Float` NaN bằng nhau.

Đây là lựa chọn có chủ ý để lớp bao dùng được với hợp đồng `equals()` (đặc biệt tính phản xạ) và các tập hợp/bảng băm. `Double.doubleToLongBits` và `Float.floatToIntBits` chuẩn hóa các mẫu bit NaN cho mục đích so sánh này.

## 3. Số không có dấu: `+0.0 == -0.0` nhưng wrapper `equals()` là `false`

IEEE 754 biểu diễn **hai dấu cho số không**. Khi so sánh số nguyên thủy:

```java
double positive = +0.0;
double negative = -0.0;
System.out.println(positive == negative);           // true
System.out.println(Double.valueOf(positive)
        .equals(Double.valueOf(negative)));         // false
```

Với `==`, `+0.0` và `-0.0` được coi là bằng nhau. Với `Double.equals()`, hai biểu diễn có **bit dấu khác nhau** nên không bằng nhau. `Float.equals()` áp dụng cách phân biệt tương tự cho `+0.0f` và `-0.0f`.

Đừng nhầm quy tắc này với `NaN`: `Double.equals()` **gộp tất cả NaN thành cùng giá trị bằng nhau**, nhưng **phân biệt dấu của số không**. Quy tắc đối với `Float` là tương tự.

## 4. Bảng đối chiếu hành vi

Giả sử mỗi phép `equals()` dùng hai đối tượng cùng lớp bao (`Double` hoặc `Float`) chứa các giá trị tương ứng:

| Trường hợp | `==` giữa giá trị nguyên thủy | `equals()` giữa lớp bao cùng kiểu |
| --- | --- | --- |
| `1.5` và `1.5` | `true` | `true` |
| `1.5` và `2.0` | `false` | `false` |
| `Double.NaN` và `Double.NaN` | `false` | `true` |
| `+0.0` và `-0.0` | `true` | `false` |
| `Double.POSITIVE_INFINITY` và chính nó | `true` | `true` |
| `Float.NaN` và `Float.NaN` | `false` | `true` |
| `+0.0f` và `-0.0f` | `true` | `false` |

Không có phép so sánh nào trong bảng này sử dụng `==` giữa **hai tham chiếu wrapper**: nếu đổi toán hạng thành `Double`/`Double`, ý nghĩa `==` chuyển sang đồng nhất đối tượng.

## 5. Chương trình Java 17+ chạy độc lập

Lưu toàn bộ đoạn mã vào tệp **`FloatingEqualityDemo.java`**:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class FloatingEqualityDemo {
    public static void main(String[] args) {
        double plusZero = +0.0;
        double minusZero = -0.0;
        double nan1 = Double.NaN;
        double nan2 = 0.0 / 0.0;

        Double boxedPlusZero = Double.valueOf(plusZero);
        Double boxedMinusZero = Double.valueOf(minusZero);
        Double boxedNaN1 = Double.valueOf(nan1);
        Double boxedNaN2 = Double.valueOf(nan2);

        System.out.println("double zeros ==: " + (plusZero == minusZero));
        System.out.println("Double.equals zeros: "
                + boxedPlusZero.equals(boxedMinusZero));
        System.out.println("double NaN ==: " + (nan1 == nan2));
        System.out.println("Double.equals NaN: "
                + boxedNaN1.equals(boxedNaN2));

        float floatPlusZero = +0.0f;
        float floatMinusZero = -0.0f;
        System.out.println("float zeros ==: "
                + (floatPlusZero == floatMinusZero));
        System.out.println("Float.equals zeros: "
                + Float.valueOf(floatPlusZero).equals(Float.valueOf(floatMinusZero)));
        System.out.println("float NaN ==: " + (Float.NaN == Float.NaN));
        System.out.println("Float.equals NaN: "
                + Float.valueOf(Float.NaN).equals(Float.valueOf(Float.NaN)));

        System.out.println("primitive double/float ==: " + (1.0d == 1.0f));
        System.out.println("cross-type wrapper equals: "
                + Double.valueOf(1.0).equals(Float.valueOf(1.0f)));
        System.out.println("Objects.equals same Double: "
                + Objects.equals(Double.valueOf(1.5), Double.valueOf(1.5)));

        Set<Double> values = new HashSet<>();
        values.add(boxedNaN1);
        values.add(boxedNaN2);
        values.add(boxedPlusZero);
        values.add(boxedMinusZero);
        System.out.println("HashSet<Double> size: " + values.size());

        System.out.println("decimal ==: " + (0.1 + 0.2 == 0.3));
        System.out.println("decimal close: "
                + (Math.abs((0.1 + 0.2) - 0.3) < 1e-9));

        Double missing = null;
        System.out.println("Objects.equals(null, null): "
                + Objects.equals(missing, null));
        try {
            System.out.println(missing == 0.0);
        } catch (NullPointerException ex) {
            System.out.println("null Double == double: NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac FloatingEqualityDemo.java
java FloatingEqualityDemo
```

Kết quả:

```text
double zeros ==: true
Double.equals zeros: false
double NaN ==: false
Double.equals NaN: true
float zeros ==: true
Float.equals zeros: false
float NaN ==: false
Float.equals NaN: true
primitive double/float ==: true
cross-type wrapper equals: false
Objects.equals same Double: true
HashSet<Double> size: 3
decimal ==: false
decimal close: true
Objects.equals(null, null): true
null Double == double: NullPointerException
```

`HashSet<Double>` lưu ba phần tử vì **hai NaN được coi là trùng nhau theo `equals()`**, còn `+0.0` và `-0.0` là hai giá trị khác nhau trong hợp đồng lớp `Double`. Lớp bao bảo đảm `hashCode()` tương ứng với `equals()`, vì vậy phép tra cứu trong bộ sưu tập dựa trên băm vẫn nhất quán.

Ví dụ `decimal close: true` minh họa so sánh xấp xỉ với một sai số nhỏ trong **tình huống số hữu hạn có độ lớn nhỏ**; `1e-9` chỉ là ngưỡng mẫu, không phải giá trị đúng cho mọi bài toán.

## 6. Ba bẫy khi dùng `==` với lớp bao

### a. Hai wrapper: so sánh tham chiếu

```java
Double first = Double.valueOf(1.25);
Double second = Double.valueOf(1.25);
boolean sameObject = (first == second);  // không nên giả định true/false
boolean sameValue = first.equals(second); // true
```

Vì cả `first` và `second` là `Double`, phép `==` trên đây kiểm tra **cùng đối tượng**. Không nên phụ thuộc vào việc factory/autoboxing có tái sử dụng một đối tượng hay không. Tương tự đối với `Float`.

### b. Một wrapper và một nguyên thủy: Java tự tháo hộp

```java
Double boxed = Double.valueOf(1.25);
double primitive = 1.25;
boolean equal = (boxed == primitive);     // true, boxed được unbox
```

Khi một vế là `Double` và vế kia là `double`, biểu thức so sánh số cần giá trị nguyên thủy; Java thực hiện **unboxing** đối với vế lớp bao. Nếu `boxed == null`, unboxing sẽ ném `NullPointerException`; `null` không tự được coi là `0.0`.

### c. `Double.equals(Float)` luôn là `false`

```java
System.out.println(1.0d == 1.0f); // true: hai nguyên thủy, nâng kiểu
System.out.println(Double.valueOf(1.0).equals(Float.valueOf(1.0f)));
// false: Double.equals chỉ chấp nhận đối tượng Double
```

`Double.equals(Object)` không thực hiện chuyển `Float` thành `Double` trước khi so sánh, và `Float.equals(Object)` cũng không tự chuyển từ `Double`. Trong khi đó, `==` trên hai số nguyên thủy khác kiểu dùng quy tắc nâng kiểu số. Với các số không biểu diễn chính xác như `0.1f`, nâng kiểu vẫn có thể dẫn tới kết quả khác mong đợi do sai số biểu diễn.

## 7. So sánh gần đúng và so sánh dùng làm khóa

Phép so sánh `==` trên số nguyên thủy hỏi **hai giá trị biểu diễn có bằng nhau theo quy tắc số thực Java không**. Nó không hiểu sai số từ phép tính trước đó. Ví dụ `0.1 + 0.2` và `0.3` khác nhau một lượng nhỏ do biểu diễn nhị phân nên `==` trả `false`.

Nếu yêu cầu nghiệp vụ là **gần bằng** trong tính toán số học, có thể xét một ngưỡng sai số phù hợp:

```java
boolean close = Math.abs(a - b) <= tolerance;
```

Đoạn trên giả định `a`, `b` là các số hữu hạn; nếu có `NaN`, vô cực, nhiều bậc độ lớn khác nhau hoặc cả giá trị sát số không, cần thiết kế kiểm tra riêng (ví dụ phân biệt các trường hợp đặc biệt, kết hợp ngưỡng tuyệt đối/tương đối). Không dùng ngưỡng tùy ý để thay thế `equals()`/`hashCode()` cho khóa bảng băm: phép gần bằng thông thường có thể **không bắc cầu** (A gần B, B gần C nhưng A không gần C), vi phạm yêu cầu của quan hệ bằng nhau.

Ngược lại, khi dùng `Double`/`Float` trong `HashMap`, `HashSet` hoặc so sánh đối tượng giá trị, `equals()` có các quy tắc ổn định và tương thích với hash: `NaN` bằng NaN, `+0.0` khác `-0.0`. Với các ứng dụng cần thứ tự sắp xếp, `Double.compare(a, b)` và `Float.compare(a, b)` đưa ra thứ tự đầy đủ hơn so với `==`/`<` trên số nguyên thủy, bao gồm thứ tự đối với NaN và hai loại zero; không diễn giải `Double.compare` đơn thuần là `a == b`.

`Objects.equals(x, y)` giúp so sánh **hai tham chiếu có thể `null`** mà không tự gây NPE: hai `null` trả `true`, một `null` trả `false`, còn khi khác `null`, nó dựa vào `x.equals(y)`. Nếu cả hai là lớp `Double`, các quy tắc NaN/zero nói trên vẫn áp dụng.

## 8. Lỗi phổ biến và cách kiểm tra nhanh

| Cách hiểu/viết sai | Cách xử lý |
| --- | --- |
| Dùng `==` giữa hai `Double` rồi cho rằng so sánh giá trị | Chọn `equals()` hoặc `Objects.equals()` khi cần giá trị wrapper |
| Nghĩ `Double.NaN == Double.NaN` là `true` | `NaN` không bằng chính nó dưới `==` nguyên thủy; dùng `Double.isNaN` để kiểm tra NaN |
| Nghĩ `+0.0` và `-0.0` luôn được coi là một | Phân biệt: `==` trên số nguyên thủy là `true`, wrapper `equals()` là `false` |
| Bỏ qua unboxing `Double` có thể `null` | Kiểm tra `null` trước phép so sánh số hoặc dùng `Objects.equals()` theo đúng ngữ nghĩa cần thiết |
| Trộn `Double` và `Float` trong `equals()` mong chúng tự quy về một kiểu | Chuyển về cùng kiểu và xác định rõ quy tắc so sánh |
| So sánh kết quả tính dấu phẩy động bằng `==` khi nghiệp vụ yêu cầu gần bằng | Chọn tiêu chí sai số theo miền giá trị, đơn vị và điều kiện biên; xử lý NaN/vô cực riêng |

## 9. Tự kiểm tra kiến thức

1. `double d = Double.NaN; d == d` trả gì? **`false`**, vì `NaN` không bằng chính nó dưới phép `==` số thực.
2. `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` trả gì? **`true`**, vì `Double.equals()` coi hai NaN bằng nhau.
3. `+0.0d == -0.0d` và `Double.valueOf(+0.0d).equals(Double.valueOf(-0.0d))` lần lượt là gì? **`true` và `false`**.
4. `Double x = null; x == 0.0` có trả `false` không? **Không**; Java unbox `x` và ném `NullPointerException`.
5. Hai biến `Double` chứa `1.0`, dùng `x == y` có phải luôn `true`? **Không**; `==` giữa hai tham chiếu hỏi có cùng đối tượng hay không.

**Ghi nhớ:** `==` trên `double`/`float` so sánh giá trị số thực nguyên thủy; `Double.equals`/`Float.equals` là quan hệ bằng nhau của đối tượng lớp bao, **coi NaN bằng NaN nhưng phân biệt `+0.0` với `-0.0`**. Và khi cả hai toán hạng là wrapper, `==` lại so sánh **tham chiếu**, không thay thế `equals()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="standard-types-005">BigDecimal.equals() xét cả giá trị và scale</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.equals() xét cả giá trị và scale

## Mục tiêu học tập

Trong Java, hai `BigDecimal` có thể biểu diễn **cùng giá trị số học** nhưng **không bằng nhau theo `equals(Object)`**. Nguyên nhân là `BigDecimal.equals()` xét **cả giá trị và `scale`**, trong khi `compareTo()` xác định thứ tự theo giá trị số học. Sự khác biệt này ảnh hưởng trực tiếp tới `hashCode()`, `HashSet`, `HashMap`, `TreeSet`, việc kiểm tra kết quả phép tính và thiết kế đối tượng chứa tiền hoặc số thập phân.

Sau bài này, bạn có thể giải thích và dự đoán chính xác kết quả của `equals`, `compareTo`, `hashCode`; chọn phép so sánh đúng mục đích; xử lý những thay đổi do `stripTrailingZeros()` và `setScale()`; kiểm tra trường hợp số không, mảng/collection chứa `BigDecimal`; và thiết kế khóa dùng quy tắc bằng nhau theo giá trị số nếu ứng dụng cần.

## 1. Một BigDecimal gồm unscaled value và scale

Một số thập phân `BigDecimal` được biểu diễn bằng một **số nguyên không chia tỷ lệ** (`unscaledValue`, kiểu `BigInteger`) và một **scale** (kiểu `int`). Giá trị số học của nó là:

```text
giá trị = unscaledValue × 10^(-scale)
```

Ví dụ:

| Biểu diễn | `unscaledValue()` | `scale()` | Giá trị số học |
| --- | ---: | ---: | ---: |
| `new BigDecimal("2")` | `2` | `0` | `2` |
| `new BigDecimal("2.0")` | `20` | `1` | `2` |
| `new BigDecimal("2.00")` | `200` | `2` | `2` |
| `new BigDecimal("0.00")` | `0` | `2` | `0` |
| `new BigDecimal("1E+3")` | `1` | `-3` | `1000` |

Vì vậy, **scale không chỉ nói về phần thập phân được hiển thị**: nó là một thành phần trong biểu diễn số. Scale **có thể âm**; ví dụ `1E+3` có `scale() == -3`. Hai `BigDecimal` khác scale có thể cùng giá trị số học.

Giá trị `2.0` và `2.00` thể hiện khác biệt rõ ràng:

```text
2.0  = 20  × 10^(-1)
2.00 = 200 × 10^(-2)

Giá trị số học giống nhau: 2
Biểu diễn (unscaled value, scale) khác nhau:
(20, 1) so với (200, 2)
```

Phân biệt **biểu diễn** và **giá trị số học** là chìa khóa của bài học.

## 2. `equals()` so sánh biểu diễn; `compareTo()` so sánh giá trị số

Theo hợp đồng của `BigDecimal`:

- `a.equals(b)` trả về `true` khi `b` là `BigDecimal` có **cùng giá trị số học và cùng scale**. Ví dụ `2.0` bằng `2.0`, nhưng **không bằng** `2.00` theo `equals`.
- `a.compareTo(b) == 0` xác nhận **hai giá trị số học bằng nhau**, kể cả khác scale. `2.0` và `2.00` có `compareTo` bằng `0`.
- `compareTo` trả số âm, `0`, hoặc số dương tùy giá trị `a` nhỏ hơn, bằng hoặc lớn hơn `b`. Không nên dựa vào độ lớn chính xác của số âm/dương trả về.
- `a.equals(null)` trả `false`. `a.compareTo(null)` ném `NullPointerException`; đối số cần là `BigDecimal` không `null`.
- `equals` chỉ công nhận đối tượng `BigDecimal` thích hợp; không coi `String "2.0"` hoặc `Integer 2` là bằng một `BigDecimal`.

Đặt cạnh nhau:

| So sánh | Kết quả | Giải thích |
| --- | --- | --- |
| `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` | `false` | Scale lần lượt `1` và `2` |
| `new BigDecimal("2.0").compareTo(new BigDecimal("2.00")) == 0` | `true` | Cùng giá trị số `2` |
| `new BigDecimal("2").equals(new BigDecimal("2.0"))` | `false` | Scale `0` so với `1` |
| `new BigDecimal("0.0").equals(new BigDecimal("0.00"))` | `false` | Hai số không nhưng scale khác |
| `new BigDecimal("0.00").compareTo(BigDecimal.ZERO) == 0` | `true` | Hai giá trị đều là số không |
| `new BigDecimal("2.0").equals(new BigDecimal("2.0"))` | `true` | Cùng biểu diễn theo quy tắc `equals` |

Đây là trường hợp đặc biệt được tài liệu Java mô tả là **natural ordering không nhất quán với equals**: hai giá trị có `compareTo == 0` chưa chắc có `equals == true`. Không tự động thay thế một phương thức bằng phương thức còn lại.

## 3. Ví dụ chạy được: nhìn thấy đầy đủ hai quan hệ bằng nhau

Lưu chương trình Java 17+ vào **`BigDecimalEqualityDemo.java`**:

```java
import java.math.BigDecimal;

public class BigDecimalEqualityDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");
        BigDecimal c = new BigDecimal("2.0");

        System.out.println("a: " + a.toPlainString());
        System.out.println("b: " + b.toPlainString());
        System.out.println("a scale: " + a.scale());
        System.out.println("b scale: " + b.scale());
        System.out.println("a unscaled: " + a.unscaledValue());
        System.out.println("b unscaled: " + b.unscaledValue());

        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("a.compareTo(b) == 0: "
                + (a.compareTo(b) == 0));
        System.out.println("a.equals(c): " + a.equals(c));

        BigDecimal zeroOne = new BigDecimal("0.0");
        BigDecimal zeroTwo = new BigDecimal("0.00");
        System.out.println("zeroOne.equals(zeroTwo): "
                + zeroOne.equals(zeroTwo));
        System.out.println("zeroOne.compareTo(zeroTwo) == 0: "
                + (zeroOne.compareTo(zeroTwo) == 0));

        BigDecimal difference = b.subtract(a);
        System.out.println("difference: "
                + difference.toPlainString());
        System.out.println("difference.equals(ZERO): "
                + difference.equals(BigDecimal.ZERO));
        System.out.println("difference.compareTo(ZERO) == 0: "
                + (difference.compareTo(BigDecimal.ZERO) == 0));
    }
}
```

Biên dịch và chạy:

```bash
javac BigDecimalEqualityDemo.java
java BigDecimalEqualityDemo
```

**Đầu ra chính xác:**

```text
a: 2.0
b: 2.00
a scale: 1
b scale: 2
a unscaled: 20
b unscaled: 200
a.equals(b): false
a.compareTo(b) == 0: true
a.equals(c): true
zeroOne.equals(zeroTwo): false
zeroOne.compareTo(zeroTwo) == 0: true
difference: 0.00
difference.equals(ZERO): false
difference.compareTo(ZERO) == 0: true
```

Phân tích hai chi tiết quan trọng:

1. `b.subtract(a)` là phép trừ `2.00 - 2.0`. Kết quả có **giá trị số học 0**, nhưng scale là `2` nên `toPlainString()` hiển thị `0.00`. `BigDecimal.ZERO` có scale `0`; bởi thế `difference.equals(BigDecimal.ZERO)` trả `false`.
2. Muốn hỏi “kết quả có bằng **số không** không?”, dùng `difference.signum() == 0` hoặc `difference.compareTo(BigDecimal.ZERO) == 0`. `signum()` trả `-1`, `0` hoặc `1` theo dấu số học, không phụ thuộc scale.

**Không hiểu sai:** `equals` của `BigDecimal` không phải “lỗi”. Nó có một **hợp đồng cụ thể**, trong đó scale có ý nghĩa. Lỗi xuất hiện khi ứng dụng cần bằng nhau theo giá trị số nhưng lại dùng `equals` mà không nhận ra sự khác biệt.

## 4. Vì sao `hashCode()` cũng phân biệt scale?

`BigDecimal.hashCode()` nhất quán với `BigDecimal.equals()`: nếu hai `BigDecimal` bằng nhau theo `equals` thì hash của chúng phải bằng nhau. Vì `equals` coi `2.0` và `2.00` là **khác nhau**, mã băm của chúng **được phép** khác nhau; triển khai Java tạo ra hash khác nhau cho cặp này.

Có thể nghĩ theo mô hình:

```text
equals(BigDecimal):
  tham gia so sánh: giá trị số học + scale

hashCode(BigDecimal):
  phải tương thích với mọi thành phần quyết định equals
```

**Không suy luận chiều ngược:** `a.hashCode() == b.hashCode()` không chứng minh `a.equals(b)`. Va chạm mã băm là hợp lệ.

Sự khác biệt này tác động đến các cấu trúc như `HashSet` hoặc `HashMap` vì chúng dùng cả `hashCode` và `equals` khi xác định phần tử/khóa tương ứng. Nếu đặt `new BigDecimal("2.0")` làm khóa rồi tra cứu bằng `new BigDecimal("2.00")`, chúng không phải cùng một khóa theo ngữ nghĩa của `HashMap`.

### 4.1. `HashSet` và `TreeSet` không đưa ra cùng một kết quả

`HashSet` dựa vào `equals/hashCode`; `TreeSet` với thứ tự tự nhiên dựa vào `compareTo` để nhận biết hai phần tử tương đương trong quá trình sắp xếp/tìm kiếm.

Lưu vào **`BigDecimalCollectionsDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class BigDecimalCollectionsDemo {
    public static void main(String[] args) {
        BigDecimal twoOneDecimal = new BigDecimal("2.0");
        BigDecimal twoTwoDecimals = new BigDecimal("2.00");
        BigDecimal twoThreeDecimals = new BigDecimal("2.000");

        Set<BigDecimal> hashSet = new HashSet<>();
        hashSet.add(twoOneDecimal);
        hashSet.add(twoTwoDecimals);

        Set<BigDecimal> treeSet = new TreeSet<>();
        treeSet.add(twoOneDecimal);
        treeSet.add(twoTwoDecimals);

        System.out.println("hashSet size: " + hashSet.size());
        System.out.println("treeSet size: " + treeSet.size());
        System.out.println("hashSet contains 2.000: "
                + hashSet.contains(twoThreeDecimals));
        System.out.println("treeSet contains 2.000: "
                + treeSet.contains(twoThreeDecimals));
        System.out.println("2.0 equals 2.00: "
                + twoOneDecimal.equals(twoTwoDecimals));
        System.out.println("2.0 compares as 2.00: "
                + (twoOneDecimal.compareTo(twoTwoDecimals) == 0));
    }
}
```

Chạy:

```bash
javac BigDecimalCollectionsDemo.java
java BigDecimalCollectionsDemo
```

**Đầu ra chính xác:**

```text
hashSet size: 2
treeSet size: 1
hashSet contains 2.000: false
treeSet contains 2.000: true
2.0 equals 2.00: false
2.0 compares as 2.00: true
```

**Vì sao?**

- `hashSet.add(2.0)` và `hashSet.add(2.00)` giữ hai phần tử khác nhau: `equals` trả `false`.
- `treeSet.add(2.0)` rồi `treeSet.add(2.00)` chỉ có một phần tử theo thứ tự tự nhiên: `compareTo` trả `0`.
- `treeSet.contains(2.000)` cũng trả `true` vì giá trị số tương đương theo bộ so sánh của cây, dù `2.000` không `equals` với phần tử `2.0` bên trong.

Điều này là một đặc điểm đã được ghi nhận của `BigDecimal`. Theo hợp đồng chung của `Set`, việc bộ so sánh không nhất quán với `equals` có thể tạo ra hành vi gây bất ngờ. `TreeMap` với thứ tự tự nhiên có sự khác biệt tương tự so với `HashMap`.

**Quy tắc sử dụng:** Trước khi chọn `HashSet`, `TreeSet`, `HashMap` hoặc `TreeMap`, hãy quyết định ứng dụng coi “cùng số nhưng khác scale” là **một** hay **hai** giá trị khác nhau. Đừng đổi loại collection rồi giả định các khóa sẽ được xử lý y hệt.

## 5. Khi nào cần `equals`, khi nào cần `compareTo`?

Chọn theo **ý nghĩa cần kiểm tra**:

| Nhu cầu | Cách làm phù hợp | Lý do |
| --- | --- | --- |
| Hai `BigDecimal` có cùng biểu diễn số và scale? | `a.equals(b)` | Scale là một phần điều kiện |
| Hai `BigDecimal` bằng nhau về giá trị số? | `a.compareTo(b) == 0` | Bỏ qua khác biệt scale |
| Kiểm tra giá trị số bằng 0? | `a.signum() == 0` | Không bị ảnh hưởng bởi `0`, `0.0`, `0.00` |
| Kiểm tra số lớn hơn ngưỡng? | `a.compareTo(threshold) > 0` | So thứ tự số |
| Kiểm tra số nhỏ hơn hoặc bằng ngưỡng? | `a.compareTo(threshold) <= 0` | So thứ tự số |
| Tra cứu khóa `HashMap<BigDecimal, ...>`? | Chuẩn hóa khóa từ đầu hoặc thống nhất scale | `HashMap` dùng `equals/hashCode` |
| So sánh một giá trị chỉ để hiển thị bao nhiêu chữ số thập phân? | Đọc `scale()`, kết hợp định dạng phù hợp | Bằng nhau về số không nói lên yêu cầu hiển thị |

Ví dụ:

```java
BigDecimal actual = new BigDecimal("100.00");
BigDecimal expected = new BigDecimal("100.0");

// Kiểm tra giá trị toán học:
boolean sameAmount = actual.compareTo(expected) == 0; // true

// Kiểm tra biểu diễn gồm cả scale:
boolean sameRepresentation = actual.equals(expected); // false
```

Đây là **đoạn minh họa** để nhúng trong một phương thức hoặc một chương trình có `import java.math.BigDecimal;`.

**Lưu ý `null`:** `actual.compareTo(expected)` đòi cả hai tham chiếu hợp lệ. Nếu dữ liệu có thể `null`, hãy quyết định rõ `null` có ý nghĩa gì trong miền nghiệp vụ, rồi kiểm tra trước. Không mặc định `null` là số không.

## 6. `stripTrailingZeros()`: chuẩn hóa để so sánh theo giá trị

`stripTrailingZeros()` trả về một `BigDecimal` có cùng giá trị số học sau khi loại các chữ số `0` không cần thiết ở cuối phần unscaled decimal representation. Ví dụ:

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.stripTrailingZeros()); // 2
System.out.println(b.stripTrailingZeros()); // 2
```

Hai kết quả đã chuẩn hóa có cùng biểu diễn theo `equals`. Trong nhiều trường hợp, có thể chuẩn hóa ngay tại biên hệ thống nếu cần **một đại diện duy nhất cho mỗi giá trị số học**.

Tuy nhiên, có **hai điều quan trọng**:

1. Chuẩn hóa có thể đổi `scale`, kể cả về **số âm**. `new BigDecimal("1000.00").stripTrailingZeros()` có thể được in là `1E+3` với scale `-3`, thay vì `1000.00`. Khi cần chuỗi thập phân không dùng lũy thừa, dùng `toPlainString()`; khi cần số chữ số cố định, chọn quy tắc `setScale`/định dạng riêng.
2. `new BigDecimal("0.000").stripTrailingZeros()` chuẩn hóa thành `BigDecimal.ZERO` (scale `0`). Điều này hữu ích khi chuẩn hóa số không nhưng có thể bỏ thông tin về độ chính xác/scale ban đầu.

Vì thế, **không sử dụng `stripTrailingZeros` nếu scale gốc mang ý nghĩa cần giữ**, chẳng hạn số chữ số thập phân được người dùng nhập hoặc quy định hiển thị trong một giao thức dữ liệu.

### 6.1. Ví dụ chuẩn hóa có thể chạy

**Tệp `BigDecimalNormalizationDemo.java`:**

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalNormalizationDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        BigDecimal normalizedA = a.stripTrailingZeros();
        BigDecimal normalizedB = b.stripTrailingZeros();

        System.out.println("normalized equal: "
                + normalizedA.equals(normalizedB));
        System.out.println("normalized a: " + normalizedA.toString());
        System.out.println("normalized a scale: "
                + normalizedA.scale());

        BigDecimal large =
                new BigDecimal("1000.00").stripTrailingZeros();
        System.out.println("large normalized: " + large.toString());
        System.out.println("large normalized scale: "
                + large.scale());
        System.out.println("large plain: " + large.toPlainString());

        BigDecimal zero =
                new BigDecimal("0.000").stripTrailingZeros();
        System.out.println("normalized zero scale: " + zero.scale());

        BigDecimal aligned =
                new BigDecimal("2.0").setScale(2);
        System.out.println("aligned: " + aligned.toPlainString());
        System.out.println("aligned equals b: " + aligned.equals(b));

        BigDecimal rounded =
                new BigDecimal("2.005").setScale(
                        2, RoundingMode.HALF_UP);
        System.out.println("rounded: " + rounded.toPlainString());
    }
}
```

```bash
javac BigDecimalNormalizationDemo.java
java BigDecimalNormalizationDemo
```

**Đầu ra chính xác:**

```text
normalized equal: true
normalized a: 2
normalized a scale: 0
large normalized: 1E+3
large normalized scale: -3
large plain: 1000
normalized zero scale: 0
aligned: 2.00
aligned equals b: true
rounded: 2.01
```

`stripTrailingZeros()` giữ **giá trị số** nhưng có thể đổi biểu diễn. `setScale(2)` của `2.0` thêm một chữ số 0 thập phân mà không đổi giá trị, nên `2.00.equals(2.00)` là `true`. Ngược lại, đưa `2.005` về scale `2` buộc làm tròn: ví dụ dùng `RoundingMode.HALF_UP` để có `2.01`.

**Cẩn thận với `setScale` không có chế độ làm tròn:** `new BigDecimal("2.005").setScale(2)` gây `ArithmeticException` vì cần loại bỏ phần giá trị khác 0 mà không được làm tròn. Đây là đặc điểm nhằm tránh âm thầm mất dữ liệu. Chỉ đặt scale mục tiêu sau khi thống nhất chính sách làm tròn theo nghiệp vụ.

## 7. Đối tượng chứa BigDecimal: chọn chính sách equals/hashCode nhất quán

Vấn đề không chỉ xuất hiện khi so trực tiếp hai `BigDecimal`. Nếu một lớp nghiệp vụ có trường `BigDecimal amount`, bạn phải quyết định **scale có tham gia định nghĩa bằng nhau của lớp chứa hay không**.

### Phương án A: scale có ý nghĩa

Nếu muốn phân biệt `2.0` và `2.00` (ví dụ giữ biểu diễn gốc là một phần dữ liệu), có thể để `equals` của lớp chứa dùng `amount.equals(other.amount)`, còn `hashCode` dùng `amount.hashCode()` hoặc `Objects.hash(...)` với trường tương ứng. Khi đó các khóa ở hai scale khác nhau sẽ được coi là khác nhau.

### Phương án B: chỉ giá trị số học có ý nghĩa

Nếu muốn `2.0` và `2.00` là **một khóa**, `equals` của lớp chứa có thể dùng `compareTo() == 0`. Nhưng **hashCode phải chuẩn hóa cùng quan hệ đó**.

Cách sai điển hình:

```java
// Sai nếu equals dùng compareTo == 0:
@Override
public int hashCode() {
    return amount.hashCode();
}
```

Vì `amount.hashCode()` vẫn xét scale, hai đối tượng được lớp chứa coi là bằng nhau có thể có hash khác nhau. Điều này vi phạm hợp đồng `equals/hashCode`.

Một cách đúng là dùng giá trị đã `stripTrailingZeros()` cho phần hash.

### 7.1. Chương trình hoàn chỉnh: khóa bằng nhau theo giá trị số

**Tệp `NumericAmountKeyDemo.java`:**

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class NumericAmountKeyDemo {
    static final class AmountKey {
        private final BigDecimal amount;

        AmountKey(BigDecimal amount) {
            this.amount = Objects.requireNonNull(amount);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AmountKey other)) {
                return false;
            }
            return amount.compareTo(other.amount) == 0;
        }

        @Override
        public int hashCode() {
            return amount.stripTrailingZeros().hashCode();
        }
    }

    public static void main(String[] args) {
        AmountKey first = new AmountKey(new BigDecimal("2.0"));
        AmountKey second = new AmountKey(new BigDecimal("2.00"));
        AmountKey third = new AmountKey(new BigDecimal("2.000"));
        AmountKey different =
                new AmountKey(new BigDecimal("3.00"));

        System.out.println("first.equals(second): "
                + first.equals(second));
        System.out.println("hashes equal: "
                + (first.hashCode() == second.hashCode()));
        System.out.println("first.equals(different): "
                + first.equals(different));

        Set<AmountKey> amounts = new HashSet<>();
        amounts.add(first);
        amounts.add(second);
        System.out.println("set size: " + amounts.size());
        System.out.println("set contains third: "
                + amounts.contains(third));
    }
}
```

```bash
javac NumericAmountKeyDemo.java
java NumericAmountKeyDemo
```

**Đầu ra chính xác:**

```text
first.equals(second): true
hashes equal: true
first.equals(different): false
set size: 1
set contains third: true
```

Ở đây `AmountKey` xác định bình đẳng **theo giá trị số**, không theo scale. Hai số bằng nhau theo `compareTo` được `stripTrailingZeros` đưa về cùng biểu diễn chuẩn, nên mã băm có cùng kết quả. Vì `BigDecimal` bất biến và trường `amount` là `final`, khóa không thay đổi giá trị sau khi được tạo.

**Lưu ý nghiệp vụ:** Nếu khóa tiền tệ cần gồm cả **mã đơn vị tiền** (VD: VND, USD), chỉ so `BigDecimal` là chưa đủ: `2 USD` không bằng `2 VND`. Khi đó, quy tắc `equals` và `hashCode` phải kết hợp cả số tiền theo chính sách đã chọn **và** đơn vị tiền.

## 8. Scale là dữ liệu hữu ích, không phải “rác” cần loại bỏ

Nhiều hệ thống thực sự muốn giữ scale:

- Một đầu vào `2.00` có thể ngụ ý người nhập cung cấp số đến **hai chữ số thập phân**.
- Hệ thống kế toán hoặc quy tắc giá có thể yêu cầu hiển thị đúng **hai chữ số sau dấu thập phân**.
- Kết quả của phép tính `BigDecimal` có thể giữ scale theo quy tắc của phép toán, nên hai kết quả bằng nhau về số học chưa chắc cùng scale.
- Khi cần trao đổi dữ liệu, `toString()` và `toPlainString()` có cách biểu diễn khác nhau; lựa chọn định dạng/scale cần dựa trên giao thức, không dựa vào `equals`.

Đừng áp dụng “chuẩn hóa mọi `BigDecimal`” một cách máy móc. Hãy tách **giá trị toán học** khỏi **quy định độ chính xác, làm tròn và hiển thị**.

Trong phép tính và kiểm thử, luôn tự hỏi đang cần kiểm tra điều nào:

```text
Câu hỏi A: Số này có bằng số kia?
    → compareTo == 0

Câu hỏi B: Hai BigDecimal có cùng scale/biểu diễn?
    → equals

Câu hỏi C: Số này có bằng không?
    → signum == 0

Câu hỏi D: Cần chính xác hai chữ số thập phân?
    → chính sách setScale/rounding/format của nghiệp vụ
```

`equals` và `compareTo` không có nhiệm vụ thay thế cho phép làm tròn hay định dạng.

## 9. Bẫy từ cách khởi tạo BigDecimal

Khác biệt giữa constructor từ chuỗi và từ số `double` có thể khiến bài toán so sánh khó hiểu hơn.

```java
BigDecimal fromText = new BigDecimal("0.1");
BigDecimal fromDouble = new BigDecimal(0.1);
BigDecimal fromValueOf = BigDecimal.valueOf(0.1);
```

- `fromText` biểu diễn chính xác số thập phân `0.1` (scale `1`).
- Số dấu phẩy động nhị phân `double 0.1` không thể biểu diễn chính xác `0.1` thập phân; `new BigDecimal(0.1)` tạo ra giá trị chính xác của **giá trị nhị phân `double` ấy**, không phải đúng `0.1`.
- `BigDecimal.valueOf(0.1)` sử dụng biểu diễn chuỗi chuẩn của `double` để tạo `BigDecimal`, nên trường hợp này có giá trị và scale phù hợp với `new BigDecimal("0.1")`.

Ví dụ kiểm tra ngắn (đặt trong phương thức `main` có import `BigDecimal`):

```java
System.out.println(fromText.equals(fromDouble));  // false
System.out.println(fromText.compareTo(fromDouble) == 0); // false
System.out.println(fromText.equals(fromValueOf)); // true
```

**Điểm phân biệt:** Trường hợp `2.0` và `2.00` khác nhau **chỉ bởi scale** nhưng cùng giá trị số. Còn `new BigDecimal(0.1)` với `new BigDecimal("0.1")` có thể **khác cả giá trị số**, nên `compareTo` cũng không trả `0`. Khi dữ liệu tiền/giá có nguồn gốc từ số thập phân, ưu tiên tạo từ chuỗi thập phân hoặc một luồng chuyển đổi decimal đã được kiểm soát.

## 10. Lỗi phổ biến và cách sửa

| Cách viết hoặc giả định | Vấn đề | Hướng xử lý |
| --- | --- | --- |
| `actual.equals(new BigDecimal("0"))` để hỏi kết quả số có bằng không | `0.00` không `equals` với `0` | Dùng `actual.signum() == 0` hoặc `compareTo(ZERO) == 0` |
| `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` phải `true` | `equals` xét scale | Dùng `compareTo == 0` nếu chỉ cần giá trị |
| `HashMap<BigDecimal, V>` sẽ coi `2.0` và `2.00` là cùng khóa | HashMap dùng `equals/hashCode` | Chuẩn hóa khóa hoặc dùng wrapper nhất quán |
| Dùng `TreeSet<BigDecimal>` rồi kỳ vọng hành vi như `HashSet` | TreeSet dùng `compareTo` | Chọn cấu trúc theo định nghĩa bằng nhau mong muốn |
| Wrapper `equals` dùng `compareTo` nhưng `hashCode` dùng `amount.hashCode()` | Có thể vi phạm hợp đồng | Hash giá trị được chuẩn hóa |
| Gọi `stripTrailingZeros()` và kỳ vọng scale luôn không âm | Kết quả có thể `1E+3` với scale `-3` | Xác định rõ chính sách chuẩn hóa và định dạng |
| `setScale(2)` luôn an toàn | Nếu cần làm tròn sẽ ném ngoại lệ | Chọn `RoundingMode` theo yêu cầu |
| `new BigDecimal(0.1)` tương đương số thập phân `0.1` | Đầu vào `double` có sai số biểu diễn | Tạo từ chuỗi thập phân hoặc quy trình chuyển đổi phù hợp |

## 11. Tự kiểm tra

**Câu 1.** Cho:

```java
BigDecimal x = new BigDecimal("5.0");
BigDecimal y = new BigDecimal("5.00");
```

Dự đoán `x.equals(y)`, `x.compareTo(y) == 0` và `x.scale()`, `y.scale()`.

**Đáp án:** `false`, `true`; scale lần lượt `1` và `2`.

**Câu 2.** Cho `BigDecimal result = new BigDecimal("0.000");`. Kiểm tra nào diễn đạt rõ ràng rằng giá trị số học bằng không?

**Đáp án:** `result.signum() == 0` (hoặc `result.compareTo(BigDecimal.ZERO) == 0`). `result.equals(BigDecimal.ZERO)` trả `false` vì scale khác.

**Câu 3.** `new BigDecimal("1000.00").stripTrailingZeros()` có thể có scale nào và hiển thị ra sao qua `toString()`?

**Đáp án:** Scale `-3` và chuỗi `1E+3`. `toPlainString()` cho `1000`, nhưng không giữ biểu diễn `1000.00` ban đầu.

**Câu 4.** Hai khóa bọc `BigDecimal` dùng `compareTo == 0` trong `equals`. Vì sao không thể mặc định trả `amount.hashCode()`?

**Đáp án:** Vì hai số cùng giá trị khác scale có thể có `BigDecimal.hashCode()` khác nhau. Wrapper coi chúng bằng nhau nhưng hash lại khác, vi phạm hợp đồng; cần dùng hash đã chuẩn hóa hoặc chính sách khác nhất quán.

**Câu 5.** Sau khi thêm `2.0` và `2.00` vào `HashSet<BigDecimal>` và `TreeSet<BigDecimal>` (thứ tự tự nhiên), số phần tử ở mỗi set là bao nhiêu?

**Đáp án:** `HashSet` có `2`, `TreeSet` có `1`, vì hai cấu trúc sử dụng hai quan hệ tương đương khác nhau cho trường hợp này.

**Câu 6.** Nếu nghiệp vụ **phải giữ đúng 2 chữ số thập phân** cho một số tiền, dùng `stripTrailingZeros()` trên dữ liệu nguồn có phù hợp không?

**Đáp án:** Không phải lựa chọn mặc định. Nó có thể làm mất scale cần giữ. Hãy xác định và áp dụng quy tắc scale, làm tròn và hiển thị theo nghiệp vụ.

## Tổng kết

**`BigDecimal.equals()` xét cả giá trị số học và scale; `BigDecimal.compareTo() == 0` xét giá trị số học.** Vì vậy `2.0` và `2.00` so bằng `equals` là `false` nhưng so bằng `compareTo` là `true`. `hashCode` tương thích với `equals` nên các cấu trúc hash có thể coi chúng là hai khóa khác nhau; cấu trúc sorted theo `compareTo` lại coi chúng tương đương. Khi muốn so giá trị số, dùng `compareTo` hoặc `signum`; khi muốn lưu khóa số học với hash, chuẩn hóa hay xây wrapper có `equals/hashCode` đồng bộ. Luôn cân nhắc scale có phải dữ liệu quan trọng của miền nghiệp vụ trước khi thay đổi nó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="standard-types-006">BigDecimal.compareTo() có thể bằng 0 khi equals() là false</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `BigDecimal.compareTo()` có thể bằng 0 khi `equals()` là `false`?

## 1. Hai câu hỏi khác nhau: bằng nhau về số học hay bằng nhau theo đối tượng?

`java.math.BigDecimal` lưu số thập phân với độ chính xác tùy ý. Đặc điểm quan trọng là mỗi `BigDecimal` gồm **giá trị nguyên không chia tỷ lệ** (`unscaledValue`) và **scale**. Giá trị toán học được tính bởi:

```text
value = unscaledValue × 10^(-scale)
```

Ví dụ:

| Biểu diễn | `unscaledValue()` | `scale()` | Giá trị toán học |
| --- | ---: | ---: | ---: |
| `new BigDecimal("2.0")` | 20 | 1 | 2 |
| `new BigDecimal("2.00")` | 200 | 2 | 2 |
| `new BigDecimal("2")` | 2 | 0 | 2 |
| `new BigDecimal("2E+1")` | 2 | -1 | 20 |

Vì vậy, hai `BigDecimal` có thể biểu thị **cùng một số** nhưng khác **scale**. Java cố ý dùng hai quy tắc khác nhau:

- **`a.compareTo(b) == 0`**: bằng nhau về **giá trị số học**; các số 2.0 và 2.00 được xem là ngang nhau.
- **`a.equals(b)`**: bằng nhau theo hợp đồng `BigDecimal.equals(Object)`; **cả giá trị số học và scale** phải bằng nhau.

Đây là hành vi được định nghĩa trong API `BigDecimal`, không phải lỗi làm tròn hay sai số dấu phẩy động. `BigDecimal` lưu chính xác các chữ số thập phân từ chuỗi đầu vào.

**Kết luận trực tiếp:** `new BigDecimal("2.0").compareTo(new BigDecimal("2.00")) == 0` là `true`, nhưng `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` là `false`.

## 2. Chữ ký và hợp đồng của từng phương thức

```java
int compareTo(BigDecimal val)
boolean equals(Object obj)
int hashCode()
```

Các khai báo trên là **chữ ký minh họa**; đây không phải chương trình Java độc lập.

### `compareTo(BigDecimal)` so sánh thứ tự số học

Giá trị trả về là:

- **Âm** nếu số nhận lời gọi nhỏ hơn đối số.
- **0** nếu hai số ngang nhau về giá trị toán học.
- **Dương** nếu số nhận lời gọi lớn hơn đối số.

Không kiểm tra cụ thể `-1` hoặc `1`; API chỉ bảo đảm dấu của kết quả. Với `BigDecimal`, `compareTo` bỏ qua sự khác nhau không làm thay đổi giá trị số học, chẳng hạn các chữ số 0 ở cuối phần thập phân.

### `equals(Object)` đòi thêm scale bằng nhau

Đối với hai `BigDecimal`, `equals` xét cả **giá trị và scale**. Bởi vậy `2.0`, `2.00` và `2` là ba đại diện khác nhau theo `equals`, dù `compareTo` đều cho kết quả 0 giữa từng cặp.

`equals` cũng không coi một `BigDecimal` bằng một kiểu số khác như `Integer` hoặc `Double` dù chúng biểu thị cùng giá trị số học. `compareTo` là phương thức so sánh **với `BigDecimal`**, không phải công cụ so sánh tùy ý giữa các lớp `Number`.

### `hashCode()` phải tương thích với equals

Nếu `a.equals(b)` là `true`, chúng bắt buộc có cùng mã băm. Nhưng các `BigDecimal` **cùng giá trị số học mà khác scale** có thể có hash khác nhau. Vì `equals` đã coi chúng khác nhau, điều đó hoàn toàn hợp lệ.

Không được dùng `compareTo == 0` để tự kết luận hai đối tượng là cùng khóa trong `HashMap`, vì `HashMap` sử dụng hợp đồng `equals` và `hashCode`, không sử dụng thứ tự tự nhiên từ `compareTo`.

## 3. Ví dụ Java 17+: nhìn thấy rõ sự khác biệt

Tạo tệp `BigDecimalEqualityDemo.java`:

```java
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BigDecimalEqualityDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        System.out.println(a.scale());
        System.out.println(b.scale());
        System.out.println(a.compareTo(b) == 0);
        System.out.println(a.equals(b));

        Set<BigDecimal> hashSet = new HashSet<>();
        hashSet.add(a);
        hashSet.add(b);
        System.out.println(hashSet.size());

        Set<BigDecimal> treeSet = new TreeSet<>();
        treeSet.add(a);
        treeSet.add(b);
        System.out.println(treeSet.size());

        Map<BigDecimal, String> hashMap = new HashMap<>();
        hashMap.put(a, "A");
        hashMap.put(b, "B");
        System.out.println(hashMap.size());
        System.out.println(hashMap.get(new BigDecimal("2.000")));

        Map<BigDecimal, String> treeMap = new TreeMap<>();
        treeMap.put(a, "A");
        treeMap.put(b, "B");
        System.out.println(treeMap.size());
        System.out.println(treeMap.get(new BigDecimal("2.000")));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac BigDecimalEqualityDemo.java
java BigDecimalEqualityDemo
```

Kết quả:

```text
1
2
true
false
2
1
2
null
1
B
```

Diễn giải lần lượt:

1. `a.scale()` là 1; `b.scale()` là 2.
2. `a.compareTo(b) == 0` là `true` vì cả hai biểu thị số 2.
3. `a.equals(b)` là `false` vì scale khác nhau.
4. `HashSet` có **2 phần tử**: cấu trúc băm phân biệt chúng bằng hợp đồng `equals/hashCode`.
5. `TreeSet` có **1 phần tử**: với thứ tự tự nhiên, `compareTo == 0` được dùng để xác định các phần tử trùng nhau trong cây.
6. `HashMap` có **2 khóa**. Khóa `2.000` (scale 3) không `equals` với khóa scale 1 hay 2 đã thêm, nên `get` trả `null`.
7. `TreeMap` có **1 khóa theo thứ tự tự nhiên**: thêm `b` cập nhật giá trị `"B"` cho khóa tương đương với `a` theo `compareTo`. Tra cứu bằng `2.000` vẫn lấy `"B"`.

Đây là khác biệt quan trọng khi cùng một tập dữ liệu đi qua các cấu trúc collection khác nhau.

## 4. Vì sao HashSet và TreeSet có thể cho kết quả khác?

`HashSet` xác định một phần tử đã tồn tại hay chưa bằng cơ chế băm và equality (cùng tham chiếu hoặc `equals`). `HashMap` có cùng cơ sở nhận dạng **khóa**.

Ngược lại, `TreeSet` và `TreeMap` duy trì thứ tự bằng `Comparable.compareTo` hoặc `Comparator` được cung cấp. Chúng coi hai phần tử hoặc khóa là **tương đương trong thứ tự** khi phép so sánh trả 0.

Với `BigDecimal`:

```text
a = 2.0 (scale 1)         b = 2.00 (scale 2)
        |                          |
        +---- compareTo == 0 ------+     cùng giá trị số học
        |                          |
        +---- equals == false -----+     khác biểu diễn/scale

HashSet / HashMap: nhận hai khóa/phần tử khác nhau
TreeSet / TreeMap: nhận là cùng khóa/phần tử theo thứ tự tự nhiên
```

`BigDecimal` là ví dụ nổi bật về **natural ordering inconsistent with equals**: thứ tự tự nhiên của nó không nhất quán với `equals`. Thông thường, API `Comparable` khuyến nghị bảo đảm `compareTo(a, b) == 0` tương đương `a.equals(b)`, nhưng đây không phải điều bắt buộc mà mọi lớp `Comparable` đều thực hiện. Với `BigDecimal`, sự khác biệt được tài liệu Java công bố rõ.

Hệ quả: một `TreeSet<BigDecimal>` dùng natural ordering có thể không tuân thủ ngữ nghĩa bình đẳng tổng quát mà bạn kỳ vọng từ `Set`, bởi phần tử “trùng” được quyết định bằng `compareTo` thay vì `equals`. Mã sử dụng phải biết tiêu chí tương đương của collection.

Nếu yêu cầu là **mỗi giá trị số học chỉ xuất hiện một lần**, `TreeSet` với thứ tự tự nhiên có thể phù hợp. Nếu yêu cầu là **giữ phân biệt scale**, `HashSet` dùng `equals` mặc định có ý nghĩa khác; hoặc cần định nghĩa một comparator tương thích với `equals` nếu vẫn muốn duy trì thứ tự.

Ví dụ comparator phân biệt scale sau khi so sánh giá trị số học:

```java
Comparator<BigDecimal> byValueThenScale =
        Comparator.<BigDecimal>naturalOrder()
                .thenComparingInt(BigDecimal::scale);
```

Đây là **đoạn mã minh họa**, cần `import java.math.BigDecimal;` và `import java.util.Comparator;`. Với các `BigDecimal` hữu hạn, comparator này trả 0 khi cả giá trị số học và scale bằng nhau, tương ứng với `BigDecimal.equals`. Khi dùng với `TreeSet<>(byValueThenScale)`, hai phần tử `2.0` và `2.00` được lưu riêng.

## 5. Tại sao việc giữ scale lại có ý nghĩa?

Scale không phải lúc nào cũng là chi tiết trình bày vô nghĩa. Nó có thể biểu thị độ chính xác hoặc quy ước về số chữ số thập phân:

- `2.0` và `2.00` là cùng một số, nhưng có thể đến từ hai phép đo hoặc hai quy tắc hiển thị khác nhau.
- Trong tính toán decimal, lựa chọn scale có thể ảnh hưởng đến **scale của kết quả**, cách chia số và nhu cầu làm tròn.
- Trong biểu diễn tiền tệ, dữ liệu có thể yêu cầu đúng hai chữ số thập phân; `2.00` phù hợp hơn `2` nếu định dạng lưu trữ yêu cầu scale 2.

Ví dụ, phép chia exact có thể bảo toàn sự khác nhau về scale:

```java
new BigDecimal("2.0").divide(new BigDecimal("3"),
                            2, RoundingMode.HALF_UP);
```

Đoạn minh họa trên cần `import java.math.BigDecimal;` và `import java.math.RoundingMode;`, trả giá trị toán học 0.67 với scale 2. Thông số scale và rounding mode được chỉ định rõ để tránh phụ thuộc ngầm vào độ chính xác đầu vào.

Lưu ý **scale không tự chứng minh ý nghĩa nghiệp vụ**. Nếu hệ thống coi 2.0 và 2.00 là cùng một giá trị, cần định nghĩa rõ quy tắc đó ở API hoặc tại ranh giới dữ liệu, không lấy `equals` mặc định làm tiêu chí khi nó không khớp.

## 6. Làm thế nào nếu muốn bình đẳng theo giá trị số học?

Trước khi dùng `BigDecimal` làm khóa hoặc phần tử tập hợp, hãy chọn rõ **quan hệ tương đương**. Hai biểu diễn `2.0` và `2.00` có cùng giá trị số học nhưng khác nhau theo `equals`. Có ba cách xử lý theo ba mục tiêu, không nên trộn lẫn:

### Cách A — Chỉ cần hỏi hai số có cùng giá trị hay không

~~~java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");
boolean numericallyEqual = a.compareTo(b) == 0; // true
~~~

Đây là phép so trực tiếp, **không thay đổi** `equals` hay `hashCode`. Khi dùng hai số chưa chuẩn hóa làm khóa `HashMap`, chúng vẫn là hai khóa riêng. `compareTo(null)` ném `NullPointerException`; xử lý `null` trước theo quy định dữ liệu.

### Cách B — Muốn một khóa băm cho mỗi giá trị số học

Chuẩn hóa tại **cả thời điểm lưu lẫn truy vấn**, trước khi đưa số vào `HashMap` hoặc `HashSet`:

~~~java
BigDecimal first = new BigDecimal("2.0").stripTrailingZeros();
BigDecimal second = new BigDecimal("2.00").stripTrailingZeros();
Set<BigDecimal> values = new HashSet<>();
values.add(first);
values.add(second);
System.out.println(values.size()); // 1
~~~

Đoạn này dùng các import `java.math.BigDecimal`, `java.util.Set` và `java.util.HashSet`; có thể đặt trong `main`. Cả hai giá trị chuẩn hóa đều có scale 0. Nếu tự viết một khóa mà `equals` dùng `compareTo == 0`, **`hashCode` cũng phải dựa trên đại diện đã chuẩn hóa**, không dùng trực tiếp `BigDecimal.hashCode` của giá trị có scale tùy ý.

Cần nhớ rằng `stripTrailingZeros()` có thể tạo **scale âm**: `1000.00` thành `1E+3`, và chuẩn hóa `0.000` thành số không scale 0. Do đó, phương án này **không phù hợp nếu scale ban đầu là dữ liệu nghiệp vụ cần giữ**.

### Cách C — Miền nghiệp vụ quy định scale cố định

Ví dụ ứng dụng quy định đúng hai chữ số thập phân:

~~~java
BigDecimal amount = new BigDecimal("2.0")
        .setScale(2, RoundingMode.UNNECESSARY); // 2.00
~~~

Cần import `java.math.RoundingMode`. Chế độ `UNNECESSARY` làm chương trình ném `ArithmeticException` nếu đầu vào như `2.001` không thể chuyển sang scale 2 mà không làm tròn. Nếu được phép làm tròn, phải chọn `RoundingMode` theo chính sách nghiệp vụ, không mặc định dùng `HALF_UP`.

**Lựa chọn nhanh:** so hai số riêng lẻ → `compareTo`; khử trùng theo giá trị trong hash collection → chuẩn hóa khóa nhất quán; cần giữ đúng độ chính xác/biểu diễn quy định → scale cố định và kiểm soát làm tròn. Bài 3 và 4 ở trên đã có chương trình hoàn chỉnh để kiểm chứng sự khác nhau giữa cấu trúc băm và cấu trúc sắp xếp.

## 7. Lỗi thường gặp khi làm việc với BigDecimal

| Lỗi | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| Dùng `equals` để hỏi “hai số có cùng giá trị toán học?” | `equals` còn so scale | Dùng `compareTo(...) == 0` |
| Dùng `compareTo == 0` rồi giả định `HashSet` sẽ gộp phần tử | HashSet dựa vào `equals/hashCode` | Chuẩn hóa trước khi thêm hoặc chọn collection/phép so sánh phù hợp |
| Xem `compareTo == 0` là dấu hiệu `hashCode` phải bằng nhau | Hợp đồng hash chỉ yêu cầu giống nhau nếu `equals == true` | Hiểu đúng hai điều kiện bình đẳng khác nhau |
| Dùng `==` so sánh hai đối tượng `BigDecimal` | `==` kiểm tra cùng tham chiếu | Chọn `equals` hoặc `compareTo` theo mục đích |
| Tạo decimal từ `new BigDecimal(0.1)` và kỳ vọng đúng 0.1 | `0.1` dạng `double` là xấp xỉ nhị phân | Ưu tiên `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)` |
| Gọi `stripTrailingZeros` rồi kỳ vọng `toString()` giữ nguyên dạng decimal | Có thể chuyển sang ký pháp khoa học, scale âm | Dùng `toPlainString` để hiển thị số không có mũ khi thích hợp |
| Đổi tiêu chí equality giữa các phần của hệ thống | Khóa tra cứu và kết quả tập hợp khác nhau | Ghi rõ chính sách scale, làm tròn và chuẩn hóa tại ranh giới |

Lưu ý `BigDecimal` bất biến: `setScale` và `stripTrailingZeros` trả về kết quả `BigDecimal`, không sửa đối tượng ban đầu. Nếu muốn dùng kết quả chuẩn hóa, phải gán lại biến hoặc lưu giá trị trả về.

## 8. Checklist trước khi dùng BigDecimal làm khóa hoặc phần tử set

1. Xác định tiêu chí “trùng”: **cùng giá trị số học** hay **cùng giá trị và scale**?
2. Nếu chỉ cần so sánh hai số theo nghiệp vụ, dùng `compareTo` và kiểm tra **kết quả bằng 0**.
3. Nếu dùng `HashMap<BigDecimal, ...>` hoặc `HashSet<BigDecimal>`, nhớ rằng scale là một phần của equality; chuẩn hóa đầu vào nếu mục đích là gộp theo số học.
4. Nếu dùng `TreeMap`/`TreeSet`, hiểu comparator hoặc natural ordering quyết định khi nào hai khóa/phần tử là tương đương. Nếu cần phân biệt scale, hãy bổ sung tiêu chí scale vào comparator.
5. Với dữ liệu có quy tắc thập phân cố định, quy định scale và rounding mode một cách tường minh, nhất là trước khi lưu và tra cứu khóa.
6. Khi chuẩn hóa bằng `stripTrailingZeros`, kiểm tra trường hợp **0** và các số có nhiều số 0 ở cuối như `1000.00`, vì kết quả có thể có scale âm.
7. Viết kiểm thử cả `2.0`, `2.00`, `2`, `0.000` và trường hợp bắt buộc làm tròn. Kiểm thử khác biệt giữa `HashSet` và `TreeSet` nếu hệ thống sử dụng cả hai.

**Tự kiểm tra:**

- `new BigDecimal("5.0").compareTo(new BigDecimal("5.00"))` trả gì? **0**.
- `new BigDecimal("5.0").equals(new BigDecimal("5.00"))` trả gì? **`false`**, do scale 1 và 2.
- Thêm hai đối tượng ấy vào `HashSet` thì có bao nhiêu phần tử? **2**.
- Thêm chúng vào `TreeSet` dùng thứ tự tự nhiên thì có bao nhiêu phần tử? **1**.
- Làm sao để chúng bằng nhau theo `equals` khi dùng làm khóa hash theo giá trị số học? **Chuẩn hóa cùng một quy tắc**, ví dụ dùng `stripTrailingZeros()` trước khi thêm và tra cứu.

**Ghi nhớ:** Với `BigDecimal`, **`compareTo == 0` nói về giá trị số học**, còn **`equals == true` nói về giá trị số học cùng scale**. Sự khác biệt này quyết định cách `HashMap`, `HashSet`, `TreeMap` và `TreeSet` nhận diện giá trị trùng nhau; chọn tiêu chí nhất quán với nhu cầu của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="standard-types-007">Enum giữ danh tính từng hằng và không cho ghi đè equals() hoặc hashCode()</a>

<details>
<summary>Xem chi tiết</summary>

# Enum giữ danh tính từng hằng và không cho ghi đè `equals()` hoặc `hashCode()`

## 1. Kết quả cần hiểu

Trong Java, mỗi **hằng enum** đại diện cho một đối tượng riêng biệt do Java tạo và quản lý. Trong phạm vi một lớp enum đã được nạp, cùng một tên hằng luôn cho lại **chính đối tượng hằng đó**. Vì thế quan hệ bằng nhau của enum là **bằng nhau theo danh tính** (identity equality), không phải tự động so các trường dữ liệu mà hằng mang theo.

Hai điểm cốt lõi:

1. `java.lang.Enum.equals(Object)` được khai báo `final` và chỉ trả `true` khi hai tham chiếu trỏ đến cùng một hằng.
2. `java.lang.Enum.hashCode()` cũng là `final`, cho mã hash gắn với danh tính hằng. Lớp enum do ta khai báo **không thể override** một trong hai phương thức này.

Chính vì vậy, so sánh hai giá trị của **cùng một kiểu enum** bằng `==` là cách thể hiện đơn giản, rõ nghĩa và an toàn về mặt kiểu; `equals()` cũng đúng với hai hằng vì nó dùng cùng nguyên tắc danh tính.

**Phạm vi:** Bài này nói về `enum` của Java, không phải một lớp Java thông thường chỉ định nghĩa các hằng `static final`. Một lớp thường có thể tự override `equals`/`hashCode` theo giá trị; lớp enum thì không thể thay đổi hai phương thức đó.

## 2. Mỗi tên hằng là một thể hiện cố định

Ví dụ:

```java
enum State {
    NEW, RUNNING, DONE
}
```

`State.NEW`, `State.RUNNING` và `State.DONE` là ba đối tượng hằng khác nhau của `State`. Không có cơ chế tạo thêm một hằng `State.NEW` mới bằng `new State()` ở mã ứng dụng. Mỗi lần truy cập `State.NEW`, gọi `State.valueOf("NEW")` hoặc tìm được phần tử `NEW` từ `State.values()`, bạn nhận về cùng tham chiếu hằng trong enum tương ứng.

Các phương thức liên quan:

| API | Ý nghĩa |
| --- | --- |
| `State.NEW` | Truy cập trực tiếp đối tượng hằng `NEW`. |
| `State.valueOf("NEW")` | Trả về đối tượng hằng có **tên khai báo** chính xác `NEW`. |
| `State.values()` | Trả một **mảng mới** chứa tham chiếu tới các đối tượng hằng theo thứ tự khai báo; không tạo đối tượng hằng mới. |
| `state.name()` | Trả tên hằng được khai báo; đây là phương thức `final` của `Enum`. |
| `state.ordinal()` | Vị trí khai báo, bắt đầu từ 0; không phải mã định danh nghiệp vụ bền vững. |
| `state.getDeclaringClass()` | Trả lớp enum khai báo hằng, kể cả với hằng có thân lớp riêng. |

`valueOf` phân biệt chính xác tên, bao gồm chữ hoa và chữ thường; tên không tồn tại gây `IllegalArgumentException`. `name()` và `ordinal()` không thay thế cho danh tính: tên chỉ có ý nghĩa trong **phạm vi kiểu enum**, và thay đổi thứ tự khai báo sẽ thay đổi `ordinal()`.

### “Một thể hiện” trong phạm vi nào?

Mỗi hằng là thể hiện cố định của **kiểu enum ở lần nạp lớp tương ứng**. Không nên diễn giải điều này thành “chỉ có một đối tượng hằng duy nhất trên mọi JVM, mọi tiến trình hoặc mọi class loader”. Java xác định danh tính kiểu theo cả lớp và bộ nạp lớp; các môi trường nạp lớp độc lập không chia sẻ cùng đối tượng hằng trong bộ nhớ.

Trong chương trình Java thông thường, bạn không cần trực tiếp quản lý chi tiết này: `MyEnum.X` và `MyEnum.valueOf("X")` trong cùng kiểu đang dùng trả về cùng hằng.

## 3. `Enum.equals()` là phép so danh tính và có `final`

Logic của `Enum.equals(Object)` tương đương:

```java
public final boolean equals(Object other) {
    return this == other;
}
```

Đây là **mô tả logic** của phương thức có trong `java.lang.Enum`, không phải đoạn mã có thể chép vào một `enum` để override. Từ khóa `final` ngăn override ở tất cả enum cụ thể. Không thể thay đổi nghĩa của `equals()` chỉ vì hằng có thêm thuộc tính hoặc có thân lớp riêng.

So với `Object.equals()` mặc định, tiêu chí danh tính giống nhau; khác biệt thiết kế cần nhớ là `Enum` **chủ động khóa hành vi bằng `final`**. Với lớp thông thường, lập trình viên có thể định nghĩa so giá trị; còn với enum, người dùng chỉ có thể định nghĩa **phương thức so sánh theo ngữ nghĩa khác với tên riêng**, nếu bài toán cần.

Giả sử:

```java
enum Priority {
    LOW(1), MEDIUM(1), HIGH(2);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
```

`Priority.LOW.weight()` và `Priority.MEDIUM.weight()` cùng bằng `1`, nhưng:

- `Priority.LOW == Priority.MEDIUM` là `false`;
- `Priority.LOW.equals(Priority.MEDIUM)` cũng là `false`;
- `Priority.LOW.weight() == Priority.MEDIUM.weight()` là `true`.

Hai hằng có cùng thuộc tính không trở thành cùng hằng. **Không được suy từ “cùng dữ liệu” ra “cùng enum”**.

## 4. Vì sao không thể override `equals` và `hashCode`?

Theo định nghĩa của `java.lang.Enum`, cả hai phương thức đều `final`. Java kiểm tra điều này **khi biên dịch**, nên không phải một cảnh báo về phong cách lập trình.

Ví dụ **cố ý sai, không biên dịch**:

```java
enum InvalidStatus {
    A, B;

    @Override
    public boolean equals(Object other) {
        return true;
    }

    @Override
    public int hashCode() {
        return 1;
    }
}
```

Trình biên dịch sẽ từ chối các phương thức override vì `Enum.equals(Object)` và `Enum.hashCode()` đã là `final`. Ngay cả khi cố thay đổi tên tham số, dùng thân lớp riêng cho hằng, hoặc bỏ `@Override`, ta vẫn không được override một phương thức `final` với cùng chữ ký.

Nguyên nhân thiết kế: **danh tính hằng phải là bất biến của kiểu enum**. Nếu lập trình viên tự biến `LOW` và `MEDIUM` thành “bằng nhau” khi cùng `weight`, một phép so sánh kiểu enum sẽ không còn xác định duy nhất hằng nào đang được nói tới. Java giữ một quy tắc nhất quán, được hỗ trợ trong `switch`, `EnumSet`, `EnumMap` và các cơ chế làm việc với hằng enum.

Một enum vẫn có thể cài đặt các phương thức riêng, chẳng hạn `sameWeight(Priority other)`, `hasCode(int code)` hoặc `matches(String input)` để biểu diễn **quan hệ nghiệp vụ bổ sung**. Những phương thức ấy không thay đổi `equals()` của enum.

## 5. `Enum.hashCode()` cũng gắn với danh tính

Hợp đồng chung giữa `equals()` và `hashCode()` trong Java yêu cầu:

```text
a.equals(b) == true  ⇒  a.hashCode() == b.hashCode()
```

Vì hai biến trỏ tới cùng hằng enum luôn trỏ đúng **một đối tượng**, chúng có cùng mã hash. Hai hằng khác nhau luôn không bằng nhau qua `equals`, nhưng **mã hash của chúng vẫn có thể trùng**; mã hash chỉ là một `int` và không phải khóa duy nhất.

Đặc điểm cần nhớ:

- Không thể override `hashCode()` của enum, kể cả khi cần hash theo `weight`, `code` hoặc `name`.
- Giá trị `hashCode()` **không được bảo đảm bằng `ordinal()`, tên hằng, mã nghiệp vụ hoặc địa chỉ bộ nhớ**.
- Không dựa vào con số hash cụ thể để lưu bền vững, truyền giữa tiến trình hay làm ID cố định. Java không cam kết cùng con số hash giữa các lần chạy.
- `System.identityHashCode(enumValue)` cũng dựa trên cơ chế identity; đây không phải cách tạo ID duy nhất tuyệt đối và không thay đổi phép so sánh enum.
- Muốn lấy mã nghiệp vụ ổn định? Định nghĩa trường riêng như `code` và phương thức `code()`, rồi lưu hoặc truyền **mã đã thiết kế**, thay vì `hashCode()`.

Một câu dễ nhầm là “hai hằng enum khác nhau nên hash của chúng chắc chắn khác nhau”. **Sai**: khác danh tính không bảo đảm khác hash. `HashMap` và `HashSet` vẫn cần xử lý collision.

## 6. Ví dụ Java 17+ chạy được

Tạo tệp **`EnumIdentityDemo.java`**:

```java
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EnumIdentityDemo {

    enum Priority {
        LOW(1), MEDIUM(1), HIGH(2);

        private final int weight;

        Priority(int weight) {
            this.weight = weight;
        }

        int weight() {
            return weight;
        }
    }

    enum Other {
        LOW
    }

    public static void main(String[] args) {
        Priority a = Priority.LOW;
        Priority alias = Priority.valueOf("LOW");
        Priority different = Priority.MEDIUM;

        System.out.println("a == alias: " + (a == alias));
        System.out.println("a.equals(alias): " + a.equals(alias));
        System.out.println("a == different: " + (a == different));
        System.out.println("a.equals(different): " + a.equals(different));
        System.out.println("same weight: "
                + (a.weight() == different.weight()));
        System.out.println("same name other type: "
                + a.name().equals(Other.LOW.name()));
        System.out.println("cross enum equals: " + a.equals(Other.LOW));
        System.out.println("same hash for alias: "
                + (a.hashCode() == alias.hashCode()));

        Set<Priority> hashes = new HashSet<>();
        hashes.add(a);
        hashes.add(alias);
        hashes.add(different);
        System.out.println("HashSet size: " + hashes.size());

        EnumMap<Priority, String> names =
                new EnumMap<>(Priority.class);
        names.put(a, "low");
        System.out.println("EnumMap lookup alias: " + names.get(alias));

        EnumSet<Priority> states =
                EnumSet.of(Priority.LOW, Priority.HIGH);
        System.out.println("EnumSet contains alias: "
                + states.contains(alias));

        Priority absent = null;
        System.out.println("null == a: " + (absent == a));
        System.out.println("Objects.equals(null, null): "
                + Objects.equals(null, null));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac EnumIdentityDemo.java
java EnumIdentityDemo
```

Kết quả:

```text
a == alias: true
a.equals(alias): true
a == different: false
a.equals(different): false
same weight: true
same name other type: true
cross enum equals: false
same hash for alias: true
HashSet size: 2
EnumMap lookup alias: low
EnumSet contains alias: true
null == a: false
Objects.equals(null, null): true
```

**Giải thích theo nhóm:**

1. `a` và `alias` là đúng hằng `Priority.LOW`. Lời gọi `valueOf("LOW")` tra hằng hiện hữu chứ không tạo đối tượng mới. Vì thế `==`, `equals` và kết quả `hashCode()` đều nhất quán.
2. `different` là `Priority.MEDIUM`, một hằng khác. Dù `weight` của `LOW` và `MEDIUM` đều bằng `1`, cả `a == different` và `a.equals(different)` đều là `false`.
3. Hai enum `Priority` và `Other` đều có hằng tên `LOW`, nên `name()` của chúng bằng nhau theo chuỗi. Tuy nhiên `a.equals(Other.LOW)` là `false`, vì đó là hai đối tượng hằng từ hai kiểu enum khác nhau.
4. `HashSet` thêm ba tham chiếu `a`, `alias` và `different` nhưng chỉ có **hai đối tượng enum khác nhau**, nên kích thước bằng `2`.
5. `EnumMap` cho phép truy vấn lại giá trị bằng tham chiếu `alias`. `EnumSet` chứa cùng hằng `LOW` nên `contains(alias)` là `true`.
6. `absent == a` kiểm tra an toàn khi `absent` là null. `Objects.equals(null, null)` trả `true` theo quy tắc của tiện ích null-safe; đây không phải phép so sánh hai hằng enum.

Không in hoặc dự đoán con số `a.hashCode()` trong kết quả vì con số này không phải dữ liệu nghiệp vụ ổn định.

## 7. Chọn `==` hay `equals()` khi làm việc với enum

Với hai tham chiếu enum của cùng một kiểu, cả `==` và `equals()` đều trả lời đúng câu hỏi “có phải cùng một hằng hay không?”.

| Tình huống | Khuyến nghị | Lý do |
| --- | --- | --- |
| Hai biến kiểu `Priority` | `a == b` | Diễn đạt trực tiếp identity, an toàn khi một biến null. |
| So với một hằng cụ thể | `status == Priority.LOW` | Ngắn gọn, không có rủi ro `NullPointerException`. |
| Biến có thể null | `status == null` hoặc `status == Priority.LOW` | `==` hỗ trợ null mà không gọi phương thức. |
| Gọi `status.equals(Priority.LOW)` khi status null | Tránh | Gọi phương thức trên null sẽ ném `NullPointerException`. |
| So enum với tham chiếu `Object` bất kỳ | `status.equals(obj)` nếu status khác null, hoặc `Objects.equals(status, obj)` | `equals` nhận `Object` và giữ quy tắc identity. |
| So trường dữ liệu của hai hằng | `a.weight() == b.weight()` hoặc phương thức nghiệp vụ có tên rõ | Đây là quan hệ khác với “cùng hằng”. |

Một lợi ích bổ sung của `==`: khi so hai biểu thức có **kiểu enum cụ thể không tương thích** (ví dụ `Priority.LOW == Other.LOW`), trình biên dịch Java từ chối phép so sánh đó. Trong khi đó `Priority.LOW.equals(Other.LOW)` vẫn biên dịch vì `equals` nhận `Object`, rồi trả `false` khi chạy.

Cũng có thể viết `Priority.LOW.equals(status)` để tránh null ở phía nhận lời gọi; tuy nhiên với enum, `status == Priority.LOW` thường dễ đọc hơn.

### Trường hợp null và `switch`

Một biến enum có thể null vì **biến tham chiếu** có thể null; bản thân hằng enum không phải null. Trong Java 17, dùng `switch` thông thường trên một biến enum null sẽ ném `NullPointerException`. Nếu null mang ý nghĩa nghiệp vụ, kiểm tra null trước khi switch hoặc quy định không chấp nhận null ở biên đầu vào.

## 8. Hằng có trạng thái và thân lớp riêng vẫn giữ identity

Enum có thể khai báo constructor, trường và phương thức:

```java
enum Mode {
    FAST {
        @Override
        String description() {
            return "fast mode";
        }
    },
    SAFE {
        @Override
        String description() {
            return "safe mode";
        }
    };

    abstract String description();
}
```

Đây là đoạn khai báo enum độc lập có thể biên dịch ở cấp package (nếu đặt trong tệp phù hợp, không cần `public`). Hai hằng có thể mang **thân lớp riêng** để override một phương thức do bạn định nghĩa như `description()`. Tuy nhiên, không thể override `Enum.equals()` hoặc `Enum.hashCode()` dù viết code trong thân `FAST` hay `SAFE`, bởi chúng là `final`.

Với enum có thân lớp riêng cho từng hằng, `getClass()` trên hằng có thể trả lớp triển khai riêng cho hằng đó. `getDeclaringClass()` mới là API thích hợp để lấy lớp enum mà hằng thuộc về. Đây là một lý do không tự suy đoán quan hệ bằng nhau từ `getClass()` hay các trường; Java đã khóa identity của enum.

Các trường enum có thể tham chiếu tới cấu trúc mutable nếu lập trình viên thiết kế như vậy. Khi trạng thái nội bộ thay đổi, **danh tính hằng, `equals()` và `hashCode()` vẫn không đổi theo trạng thái ấy**. Dù vậy, vì enum thường được chia sẻ toàn ứng dụng, nên ưu tiên thuộc tính bất biến (`final`, không lộ đối tượng mutable) để tránh lỗi trạng thái và vấn đề luồng.

## 9. Sử dụng enum trong collection và lưu dữ liệu

### `HashSet` / `HashMap`

Enum có `equals`/`hashCode` hợp lệ ngay từ đầu. Dùng enum làm key hoặc phần tử trong các collection dựa trên hash không cần override thêm. Thêm cùng hằng hai lần vào một `HashSet` chỉ tạo một phần tử; hai hằng khác nhau vẫn là các phần tử khác nhau, kể cả khi chúng có cùng thuộc tính.

### `EnumSet` và `EnumMap`

Nếu phần tử/key luôn thuộc **một kiểu enum**, cân nhắc:

- `EnumSet<Priority>`: tập hợp chuyên biệt cho các hằng `Priority`; có thể dùng `EnumSet.of(...)`, `EnumSet.noneOf(Priority.class)`.
- `EnumMap<Priority, V>`: ánh xạ key là các hằng `Priority`; tạo bằng `new EnumMap<>(Priority.class)`.

Hai cấu trúc này tối ưu cho miền hằng enum hữu hạn, sử dụng cấu trúc nội bộ dựa trên các giá trị enum/ordinal. Không cần và không thể định nghĩa hash tùy chỉnh để chúng nhận diện hai hằng có cùng thuộc tính là một.

### Lưu trữ và giao tiếp giữa các hệ thống

Khi cần lưu/truyền giá trị enum, không lưu `hashCode()` hoặc mặc định xem `ordinal()` là ID nghiệp vụ lâu dài. `ordinal()` phụ thuộc thứ tự khai báo, nên thêm hoặc sắp lại hằng có thể thay đổi số thứ tự.

Có thể lưu `name()` nếu hệ thống quy định tên enum là hợp đồng dữ liệu ổn định và quản lý việc đổi tên; hoặc tốt hơn, định nghĩa mã nghiệp vụ tường minh như `"LOW"`, `"MEDIUM"` và phương thức chuyển đổi có kiểm soát. Dù một hằng có mã nghiệp vụ trùng với hằng khác, **identity của chúng vẫn không trùng**.

Khi Java serialize/deserialize enum theo cơ chế chuẩn, giá trị enum được xử lý đặc biệt để khôi phục **hằng được khai báo** theo tên và kiểu, thay vì tạo một bản sao đối tượng enum mới theo cách thông thường. Tuy nhiên, việc hằng có tồn tại và tên có khớp giữa các phiên bản ứng dụng hay không vẫn cần được quản lý.

## 10. Các hiểu nhầm thường gặp

| Hiểu nhầm | Thực tế |
| --- | --- |
| “Hai hằng có các trường bằng nhau thì `equals()` bằng nhau.” | Sai; `equals()` của enum chỉ dựa trên cùng đối tượng hằng. |
| “Có thể override `equals` hoặc `hashCode` trong enum nếu cần.” | Sai; cả hai phương thức của `Enum` là `final`. |
| “`values()` tạo lại tất cả đối tượng hằng.” | Sai; nó trả mảng mới gồm tham chiếu các hằng sẵn có. |
| “Hai enum khác kiểu nhưng cùng `name()` là bằng nhau.” | Sai; tên giống nhau không biến hai đối tượng thành một. |
| “`ordinal()` chính là `hashCode()`.” | Sai; chúng có mục đích và hợp đồng khác nhau. |
| “Hai hằng khác nhau thì mã hash bắt buộc khác nhau.” | Sai; collision hợp lệ. |
| “Mọi enum chỉ có thể chứa tên hằng, không có dữ liệu hoặc phương thức.” | Sai; enum có constructor, trường, phương thức và thân lớp riêng cho hằng. |
| “Enum không thể được dùng trong `HashMap` khi không override hash.” | Sai; `Enum` đã cung cấp `equals/hashCode` final hợp lệ. |
| “`status.equals(X)` an toàn nếu status null.” | Sai; lời gọi ném `NullPointerException`. Dùng `status == X`. |
| “Nên dùng `hashCode()` của enum làm ID trong database.” | Sai; hash không là định danh nghiệp vụ và không được bảo đảm ổn định qua lần chạy. |

## 11. Khi nào cần so theo giá trị thay vì hằng enum?

Ví dụ, `Priority.LOW` và `Priority.MEDIUM` cùng `weight = 1` nhưng là hai lựa chọn riêng. Nếu chương trình cần gom nhóm theo trọng số, hãy biểu đạt điều ấy **bằng một phép so riêng**:

```java
static boolean hasSameWeight(Priority a, Priority b) {
    if (a == null || b == null) {
        return a == b;
    }
    return a.weight() == b.weight();
}
```

Đây là phương thức minh họa cần đặt trong lớp có truy cập tới enum `Priority` và phương thức `weight()`. Nó cố ý định nghĩa quan hệ **cùng trọng số**, khác với quan hệ **cùng hằng**. Với `a == b == null` về mặt diễn đạt, trường hợp cả hai null được hàm trên coi bằng nhau; tùy nghiệp vụ có thể quy định khác.

Nếu mô hình dữ liệu cần **mỗi giá trị khác nhau là một đối tượng có equality theo dữ liệu**, và các giá trị có thể phát sinh lúc chạy, enum thường không phù hợp. Dùng lớp bất biến hoặc `record` với hợp đồng `equals()`/`hashCode()` theo thành phần, hoặc thiết kế một khóa giá trị riêng. Không cố làm hai hằng enum khác nhau trở thành bằng nhau qua `equals()`.

## 12. Tự kiểm tra

**Câu 1.** Trong enum `Priority { LOW(1), MEDIUM(1) }`, hai hằng có cùng trọng số. `LOW.equals(MEDIUM)` có trả `true` không?

**Đáp án:** Không. Chúng là hai đối tượng hằng riêng, bất kể trường dữ liệu bằng nhau.

**Câu 2.** `Priority.valueOf("LOW") == Priority.LOW` trả gì?

**Đáp án:** `true`. `valueOf` trả hằng enum được khai báo, không tạo bản sao.

**Câu 3.** Vì sao không viết `@Override public int hashCode()` trong enum?

**Đáp án:** `Enum.hashCode()` là `final`; trình biên dịch cấm override.

**Câu 4.** Hai hằng của hai kiểu enum khác nhau đều tên `READY` có bằng nhau qua `equals` không?

**Đáp án:** Không. `name()` có thể giống nhau nhưng đối tượng enum không phải cùng hằng.

**Câu 5.** Khi `status` có thể null, nên dùng `status == State.NEW` hay `status.equals(State.NEW)`?

**Đáp án:** `status == State.NEW`; nó an toàn với null và thể hiện trực tiếp so danh tính enum.

**Câu 6.** Có thể tin rằng `Priority.LOW.hashCode()` luôn giữ một số cố định trên mọi lần chạy JVM không?

**Đáp án:** Không. Chỉ nên dựa vào hợp đồng equality/hash trong quá trình sử dụng bình thường; không coi hash là mã định danh ổn định.

**Ghi nhớ:** Enum có tập hợp hằng hữu hạn và mỗi hằng có **danh tính riêng**. `Enum.equals()` và `Enum.hashCode()` được khai báo **`final`**, nên không thể đổi thành so giá trị. Dùng `==` để nhận diện hằng; khi cần so các thuộc tính, hãy định nghĩa một phép so nghiệp vụ riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="standard-types-008">Đối tượng giá trị của java.time và yêu cầu đọc đúng hợp đồng từng kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng giá trị của java.time và yêu cầu đọc đúng hợp đồng từng kiểu

## 1. Vì sao không thể đánh đồng mọi phép “bằng nhau” trong java.time?

Các kiểu quen thuộc trong **java.time** như **LocalDate**, **LocalTime**, **LocalDateTime**, **Instant**, **OffsetDateTime**, **ZonedDateTime**, **Duration**, **Period**, **YearMonth** được thiết kế để mô tả **giá trị thời gian**. Nhiều kiểu trong số này là **value-based classes**: hai instance bằng nhau theo hợp đồng của kiểu có thể được sử dụng thay thế nhau. Không dựa vào danh tính object bằng toán tử **==**, **System.identityHashCode** hay dùng một instance làm khóa đồng bộ **synchronized**. Hãy dùng **equals** và **hashCode** theo đúng tài liệu của kiểu.

Điều khó nhất không nằm ở việc gọi equals, mà là xác định **“cùng giá trị” nghĩa là gì**:

- **Cùng ngày lịch**: 2026-10-23 có bằng 2026-10-23 không? **LocalDate.equals** giải quyết.
- **Cùng thời điểm tuyệt đối**: 09:00 tại UTC+07 có phải 02:00 UTC không? **Instant.equals** hoặc phép so dòng thời gian **isEqual** trên kiểu có offset/zone giải quyết.
- **Cùng biểu diễn ngày giờ và offset**: 09:00+07 có bằng 02:00Z không? **OffsetDateTime.equals** trả false, dù hai giá trị chỉ cùng một thời điểm.
- **Cùng vùng múi giờ có tên**: **Europe/Paris** và **UTC** không phải cùng **ZoneId**, kể cả khi một mốc thời gian cụ thể có thể có cùng offset.
- **Cùng khoảng thời lượng** hay **cùng số lượng đơn vị lịch**: **Duration** tính trên giây/nanogiây, **Period** tính trên năm/tháng/ngày; chúng là hai mô hình khác nhau.

**Quy tắc chung từ Object** vẫn áp dụng:

1. **Phản xạ:** a.equals(a) là true.
2. **Đối xứng:** a.equals(b) có kết quả như b.equals(a).
3. **Bắc cầu:** a bằng b và b bằng c thì a bằng c.
4. **Nhất quán:** cùng trạng thái giá trị thì kết quả không thay đổi.
5. **Khác null:** a.equals(null) là false.
6. **Liên hệ hash:** nếu a.equals(b) true thì a.hashCode() phải bằng b.hashCode(); hash bằng nhau chưa đủ để kết luận equals.

Một so sánh như **isEqual**, **isBefore** hoặc **isAfter** có thể trả về kết quả khác **equals** mà hoàn toàn không vi phạm hợp đồng. Chúng trả lời **câu hỏi khác**.

## 2. Bảng đọc hợp đồng theo từng loại giá trị

| Kiểu java.time | Giá trị mà equals xem xét | Điều không nên suy diễn |
| --- | --- | --- |
| **LocalDate** | Ngày ISO: năm, tháng, ngày | Không gắn múi giờ và không xác định riêng một Instant |
| **LocalTime** | Giờ, phút, giây, nano trong ngày | Không phải thời điểm toàn cầu |
| **LocalDateTime** | Ngày và giờ địa phương, đến nano | Không có offset/zone, nên không đủ để nhận diện một thời điểm |
| **Instant** | Vị trí trên dòng thời gian UTC (giây epoch và nano) | Không lưu múi giờ địa phương hay định dạng người dùng |
| **OffsetDateTime** | Ngày-giờ địa phương **và UTC offset** | Cùng Instant chưa chắc equals |
| **OffsetTime** | Giờ địa phương **và UTC offset** | Không mang ngày; **isEqual** dựa trên giờ tính tương ứng theo UTC trong ngày chuẩn |
| **ZonedDateTime** | Ngày-giờ địa phương, offset và **ZoneId** | Cùng Instant chưa chắc equals; cùng offset chưa chắc cùng zone |
| **ZoneId** | ID múi giờ (ví dụ Europe/Paris) | Hai ID có quy tắc/offset hiện tại giống nhau vẫn có thể khác giá trị |
| **ZoneOffset** | Độ lệch cố định so với UTC, tính bằng giây | Không mô tả toàn bộ quy tắc chuyển giờ mùa hè của region |
| **Duration** | Độ dài theo giây và nanogiây | Không phải số ngày lịch/tháng lịch |
| **Period** | Số năm, tháng và ngày theo hệ ISO | Không đại diện cho một số giây cố định |
| **Year**, **YearMonth**, **MonthDay** | Các thành phần lịch đúng như tên gọi | Không tự đại diện cho ngày-giờ đầy đủ |

**Đọc đúng từng kiểu trước khi chọn làm khóa.** Với **LocalDateTime**, một lần tăng đồng hồ địa phương lúc đổi giờ có thể tạo các biểu diễn giống nhau tại hai Instant khác nhau. Với **OffsetDateTime**, offset là một phần của equals. Với **ZonedDateTime**, ZoneId cũng là một phần của equals. Với **Instant**, biểu diễn theo múi giờ đã bị loại bỏ, chỉ còn mốc tuyệt đối.

Phép so sánh **equals** của các kiểu này không tự động chuyển đổi một kiểu thành kiểu khác: **Instant** và **OffsetDateTime** có thể nói về cùng thời điểm nhưng không phải hai giá trị cùng kiểu theo Object.equals. Muốn so cùng thời điểm, chuyển thành **Instant** trước khi so.

## 3. Value-based: dùng giá trị, không phụ thuộc danh tính

Java có thể tạo ra nhiều instance mang cùng giá trị. Với các lớp value-based, **không có bảo đảm về danh tính object** mà chương trình nên dựa vào. Vì vậy:

~~~java
LocalDate a = LocalDate.of(2026, 10, 23);
LocalDate b = LocalDate.parse("2026-10-23");

// Đúng: so giá trị ngày.
boolean sameDate = a.equals(b);

// Sai về mặt ý nghĩa: == hỏi danh tính tham chiếu;
// không phải tiêu chí so giá trị java.time.
// boolean sameDate = (a == b);
~~~

Đây chỉ là **đoạn mã minh họa** đặt trong phương thức có import **java.time.LocalDate**, không phải file độc lập. Dù **==** tình cờ true trong một trường hợp nào đó, chương trình vẫn không được dựa vào điều này.

### Ví dụ đầy đủ: các đối tượng cùng giá trị trong tập hợp hash

Tạo file **BasicTimeValueDemo.java**:

~~~java
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

public class BasicTimeValueDemo {
    public static void main(String[] args) {
        LocalDate first = LocalDate.of(2026, 10, 23);
        LocalDate second = LocalDate.parse("2026-10-23");

        System.out.println("date equals: " + first.equals(second));
        System.out.println("date hash equals: " +
            (first.hashCode() == second.hashCode()));

        Set<LocalDate> dates = new HashSet<>();
        dates.add(first);
        dates.add(second);
        System.out.println("unique dates: " + dates.size());

        LocalDateTime midnight =
            LocalDateTime.of(2026, 10, 23, 0, 0);
        System.out.println("date equals date-time: " +
            first.equals(midnight));

        Instant absolute = Instant.parse("2026-10-23T02:30:00Z");
        OffsetDateTime fromOffset =
            OffsetDateTime.parse("2026-10-23T09:30:00+07:00");
        System.out.println("same instant: " +
            absolute.equals(fromOffset.toInstant()));
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac BasicTimeValueDemo.java
java BasicTimeValueDemo
~~~

Kết quả chính xác:

~~~text
date equals: true
date hash equals: true
unique dates: 1
date equals date-time: false
same instant: true
~~~

**Giải thích:** LocalDate.of và LocalDate.parse tạo cùng ngày ISO; equals true, hashCode bằng nhau và HashSet chỉ lưu một giá trị logic. Nhưng **LocalDate** không thể bằng **LocalDateTime** bằng Object.equals, dù LocalDateTime diễn tả nửa đêm của chính ngày đó. **OffsetDateTime.toInstant()** chuyển ngày-giờ có offset sang mốc tuyệt đối, nên so với Instant.parse trả true.

Các lớp java.time chuẩn đã cài đặt equals/hashCode phù hợp. Khi dùng trực tiếp làm khóa HashMap hoặc phần tử HashSet, thường **không cần tự cài lại equals/hashCode cho chúng**.

## 4. Cùng Instant nhưng không equals: OffsetDateTime

**OffsetDateTime** mang cả **local date-time** và **ZoneOffset**. Hai biểu diễn sau cùng chỉ một thời điểm:

~~~text
2026-10-23T09:00:00+07:00
2026-10-23T02:00:00Z
~~~

Trong phép **equals**, chúng khác nhau: giờ địa phương khác và offset khác. Nhưng **isEqual** trả true khi hai giá trị biểu diễn cùng mốc trên dòng thời gian. **toInstant()** cũng cho hai Instant bằng nhau.

Tạo file **OffsetEqualityDemo.java**:

~~~java
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public class OffsetEqualityDemo {
    public static void main(String[] args) {
        OffsetDateTime vietnam =
            OffsetDateTime.parse("2026-10-23T09:00:00+07:00");
        OffsetDateTime utc =
            OffsetDateTime.parse("2026-10-23T02:00:00Z");

        System.out.println("equals: " + vietnam.equals(utc));
        System.out.println("isEqual: " + vietnam.isEqual(utc));
        System.out.println("instant equals: " +
            vietnam.toInstant().equals(utc.toInstant()));
        System.out.println("compareTo zero: " +
            (vietnam.compareTo(utc) == 0));

        Set<OffsetDateTime> offsets = new HashSet<>();
        offsets.add(vietnam);
        offsets.add(utc);

        Set<Instant> instants = new HashSet<>();
        instants.add(vietnam.toInstant());
        instants.add(utc.toInstant());

        System.out.println("offset keys: " + offsets.size());
        System.out.println("instant keys: " + instants.size());
    }
}
~~~

Lệnh:

~~~bash
javac OffsetEqualityDemo.java
java OffsetEqualityDemo
~~~

Kết quả:

~~~text
equals: false
isEqual: true
instant equals: true
compareTo zero: false
offset keys: 2
instant keys: 1
~~~

**Điểm quan trọng về compareTo:** **OffsetDateTime.compareTo** trước hết so mốc trên dòng thời gian; nếu mốc giống nhau, nó xét thêm ngày-giờ địa phương để giữ thứ tự **nhất quán với equals**. Vì vậy, **compareTo == 0** không đồng nghĩa với **isEqual** trong trường hợp hai offset khác nhau. Nếu chỉ muốn hỏi “cùng mốc tuyệt đối?”, dùng **isEqual** hoặc so **toInstant()**.

Nếu dùng **HashSet<OffsetDateTime>**, cả hai biểu diễn được giữ vì equals false. Nếu nghiệp vụ muốn loại trùng theo mốc sự kiện tuyệt đối, dùng **HashSet<Instant>** hoặc khóa được chuyển đổi về Instant.

## 5. ZonedDateTime: cùng lúc vẫn có thể khác ZoneId

**ZonedDateTime** chứa đủ **LocalDateTime + ZoneOffset + ZoneId**. ZoneId thường là tên vùng có quy tắc lịch sử và chuyển giờ, ví dụ **Europe/Paris**. Hai ZonedDateTime có cùng Instant nhưng khác ZoneId không được coi bằng nhau theo equals.

Ví dụ đầy đủ **ZonedEqualityDemo.java**:

~~~java
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class ZonedEqualityDemo {
    public static void main(String[] args) {
        ZonedDateTime paris = ZonedDateTime.of(
            2026, 1, 15, 9, 0, 0, 0, ZoneId.of("Europe/Paris")
        );

        ZonedDateTime sameInstantUtc =
            paris.withZoneSameInstant(ZoneId.of("UTC"));

        ZonedDateTime sameLocalUtc =
            paris.withZoneSameLocal(ZoneId.of("UTC"));

        System.out.println("instant version equals: " +
            paris.equals(sameInstantUtc));
        System.out.println("instant version isEqual: " +
            paris.isEqual(sameInstantUtc));
        System.out.println("instant version compares zero: " +
            (paris.compareTo(sameInstantUtc) == 0));

        System.out.println("local version same wall time: " +
            paris.toLocalDateTime()
                 .equals(sameLocalUtc.toLocalDateTime()));
        System.out.println("local version same instant: " +
            paris.toInstant().equals(sameLocalUtc.toInstant()));
    }
}
~~~

Chạy:

~~~bash
javac ZonedEqualityDemo.java
java ZonedEqualityDemo
~~~

Kết quả:

~~~text
instant version equals: false
instant version isEqual: true
instant version compares zero: false
local version same wall time: true
local version same instant: false
~~~

**withZoneSameInstant(zone)** chuyển vùng múi giờ nhưng **giữ nguyên thời điểm tuyệt đối**, điều chỉnh đồng hồ địa phương. **withZoneSameLocal(zone)** giữ **ngày-giờ nhìn trên đồng hồ** trong khả năng có thể, rồi xác định lại offset/mốc tuyệt đối theo zone mới. Trường hợp không có chuyển đổi DST như giờ UTC ở ví dụ thì ngày-giờ địa phương được giữ chính xác. Hai phương thức phục vụ **hai yêu cầu khác nhau**.

**ZonedDateTime.compareTo** cũng đưa các tiêu chí bổ sung vào thứ tự để kết quả bằng 0 nhất quán với equals; cùng Instant không bảo đảm compareTo trả 0. Không dùng compareTo == 0 thay **isEqual** khi tiêu chí nghiệp vụ chỉ là cùng thời điểm.

### ZoneId và ZoneOffset là hai loại thông tin

- **ZoneOffset** là một độ lệch cố định, ví dụ +07:00, +01:00 hoặc Z.
- **ZoneId** có thể là một vùng như Europe/Paris, có quy tắc offset thay đổi theo thời gian, hoặc một ID mang ý nghĩa offset cố định.
- Hai ZoneId có thể có cùng offset tại một Instant nhưng **khác ID** và khác chính sách thời gian ở những mốc khác.

Ví dụ:

~~~java
ZoneId named = ZoneId.of("UTC");
ZoneId numeric = ZoneOffset.UTC;

// named.equals(numeric) là false: "UTC" khác ID "Z".
// named.getRules().getOffset(Instant.EPOCH)
//     .equals(ZoneOffset.UTC) là true.
~~~

Đây là **đoạn minh họa**, cần import **java.time.ZoneId**, **java.time.ZoneOffset**, **java.time.Instant** nếu đặt vào file Java riêng.

Nếu nghiệp vụ cần lưu **múi giờ do người dùng chọn** để hiển thị hoặc lịch họp tương lai, giữ **ZoneId**. Nếu chỉ cần so thứ tự/cùng mốc sự kiện đã xảy ra, **Instant** thường là khóa phù hợp hơn.

## 6. LocalDate, LocalTime, LocalDateTime không tự mang thời điểm tuyệt đối

**LocalDate** biểu diễn ngày trên lịch ISO; **LocalTime** biểu diễn giờ trong một ngày; **LocalDateTime** kết hợp hai phần. Không kiểu nào tự chứa ZoneId hay offset.

Ví dụ **2026-10-23T09:00** có thể được hiểu là 09:00 tại Hà Nội, Tokyo hoặc London, tương ứng các Instant khác nhau. Vì vậy:

~~~java
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class LocalTimeMeaningDemo {
    public static void main(String[] args) {
        LocalDateTime wall =
            LocalDateTime.parse("2026-10-23T09:00:00");

        Instant vietnam = wall
            .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toInstant();

        Instant tokyo = wall
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toInstant();

        System.out.println("same local datetime: " +
            wall.equals(LocalDateTime.parse("2026-10-23T09:00:00")));
        System.out.println("same instant: " + vietnam.equals(tokyo));
    }
}
~~~

Lưu vào file **LocalTimeMeaningDemo.java**, chạy:

~~~bash
javac LocalTimeMeaningDemo.java
java LocalTimeMeaningDemo
~~~

Kết quả:

~~~text
same local datetime: true
same instant: false
~~~

**Cẩn trọng vào thời điểm chuyển DST:** Một LocalDateTime có thể **không tồn tại** tại một zone (giờ đồng hồ bị nhảy qua) hoặc **xuất hiện hai lần** (giờ đồng hồ bị lặp lại). **LocalDateTime.atZone(zone)** có quy tắc mặc định để giải quyết gap/overlap; quy tắc mặc định đó chưa chắc phù hợp nghiệp vụ. Nếu yêu cầu phân biệt hai lần diễn ra cùng giờ đồng hồ, cần lưu offset hoặc Instant đã được xác định rõ.

**Không dùng LocalDateTime.equals để nhận diện duy nhất một sự kiện toàn cầu** trừ khi miền dữ liệu đã cố ý quy định local date-time và zone theo một chính sách duy nhất, nhất quán.

## 7. isEqual trên kiểu theo lịch và khác hệ lịch

Ngoài khác biệt về offset/zone, java.time còn có các hệ lịch (chronology) khác ngoài ISO. **LocalDate** là ngày ISO, trong khi **JapaneseDate**, **HijrahDate**, **ThaiBuddhistDate** thuộc các chronology khác nhau.

Với giao diện **ChronoLocalDate**, phương thức **isEqual(ChronoLocalDate)** hỏi liệu hai giá trị đại diện cùng ngày trên dòng ngày (epoch day) hay không. Trong khi đó equals vẫn bao gồm **ngữ nghĩa hệ lịch** của kiểu cụ thể.

Ví dụ **ChronologyEqualityDemo.java**:

~~~java
import java.time.LocalDate;
import java.time.chrono.JapaneseDate;

public class ChronologyEqualityDemo {
    public static void main(String[] args) {
        LocalDate iso = LocalDate.of(2026, 10, 23);
        JapaneseDate japanese = JapaneseDate.from(iso);

        System.out.println("equals: " + iso.equals(japanese));
        System.out.println("isEqual: " + iso.isEqual(japanese));
        System.out.println("same epoch day: " +
            (iso.toEpochDay() == japanese.toEpochDay()));
    }
}
~~~

Chạy:

~~~bash
javac ChronologyEqualityDemo.java
java ChronologyEqualityDemo
~~~

Kết quả:

~~~text
equals: false
isEqual: true
same epoch day: true
~~~

**Ý nghĩa:** Hai giá trị cùng ngày vật lý nhưng biểu diễn trong **hai hệ lịch khác nhau**. Muốn tìm cùng ngày bất kể chronology, so **toEpochDay()**, hoặc dùng **ChronoLocalDate.isEqual**. Muốn giữ giá trị theo đúng chronology thì không đổi equals thành “cùng epoch day” một cách tùy tiện.

Nếu dựng khóa Map hoặc Set từ nhiều chronology, hãy quyết định trước muốn phân nhóm theo **cùng chronology + ngày** hay **cùng epoch day**. Với mục tiêu thứ hai, dùng khóa canonical dạng **long epochDay** sẽ rõ ràng và nhất quán hơn.

## 8. Duration và Period: hai hợp đồng cho hai khái niệm khác nhau

**Duration** biểu diễn lượng thời gian theo giây/nanogiây; **Period** biểu diễn các thành phần lịch năm, tháng, ngày theo ISO. Phép equals phản ánh **cấu trúc giá trị** của mỗi loại:

- **Duration.ofMinutes(60)** bằng **Duration.ofHours(1)** vì cả hai biểu diễn cùng 3600 giây.
- **Period.ofMonths(12)** **không equals** **Period.ofYears(1)**: một giá trị có 0 năm/12 tháng, giá trị kia có 1 năm/0 tháng. **normalized()** có thể chuẩn hóa phần tháng về năm.
- **Period.ofDays(1)** không thể mặc định coi tương đương **Duration.ofDays(1)** vì một ngày lịch đi qua vùng đổi giờ có thể dài 23 hoặc 25 giờ; Duration một ngày là 86400 giây.

Tạo file **DurationPeriodDemo.java**:

~~~java
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DurationPeriodDemo {
    public static void main(String[] args) {
        System.out.println("duration equal: " +
            Duration.ofMinutes(60).equals(Duration.ofHours(1)));

        Period months = Period.ofMonths(12);
        Period year = Period.ofYears(1);
        System.out.println("period equal: " + months.equals(year));
        System.out.println("normalized equal: " +
            months.normalized().equals(year));

        ZonedDateTime start = ZonedDateTime.of(
            2026, 3, 28, 12, 0, 0, 0, ZoneId.of("Europe/Paris")
        );

        ZonedDateTime calendarDay = start.plus(Period.ofDays(1));
        ZonedDateTime fixedDay = start.plus(Duration.ofDays(1));

        System.out.println("calendar day hour: " + calendarDay.getHour());
        System.out.println("fixed day hour: " + fixedDay.getHour());
        System.out.println("calendar elapsed hours: " +
            Duration.between(start.toInstant(),
                             calendarDay.toInstant()).toHours());
        System.out.println("fixed elapsed hours: " +
            Duration.between(start.toInstant(),
                             fixedDay.toInstant()).toHours());
    }
}
~~~

Chạy:

~~~bash
javac DurationPeriodDemo.java
java DurationPeriodDemo
~~~

Kết quả với quy tắc múi giờ Europe/Paris cho ngày chuyển sang giờ mùa hè năm 2026:

~~~text
duration equal: true
period equal: false
normalized equal: true
calendar day hour: 12
fixed day hour: 13
calendar elapsed hours: 23
fixed elapsed hours: 24
~~~

Ở Paris, ngày 29/03/2026 chuyển sang giờ mùa hè. Cộng **Period.ofDays(1)** đi tới 12 giờ trưa ngày lịch kế tiếp, với thời gian thực trôi qua là 23 giờ. Cộng **Duration.ofDays(1)** giữ 24 giờ tuyệt đối, dẫn đến 13 giờ địa phương ngày kế tiếp. Kết quả không mâu thuẫn: hai đối tượng đại diện **hai loại đại lượng khác nhau**.

**Không dùng Period để biểu diễn timeout, thời lượng phiên chạy hoặc số giây xử lý.** Dùng Duration cho thời lượng máy đo được. Dùng Period cho chính sách năm/tháng/ngày lịch, ví dụ “gia hạn một tháng” hoặc “ngày tiếp theo tại địa phương”. **Không đổi Duration sang Period hoặc ngược lại** nếu chưa cung cấp đầy đủ điểm bắt đầu, zone và quy tắc nghiệp vụ.

## 9. Độ chính xác, chuẩn hóa và biểu diễn văn bản

Các giá trị thời gian thường có phần nano. **Instant.equals** và **LocalDateTime.equals** xem xét phần nano chính xác; hai mốc cách nhau 1 nanogiây không bằng nhau.

~~~java
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class PrecisionDemo {
    public static void main(String[] args) {
        Instant base = Instant.parse("2026-10-23T00:00:00Z");
        Instant later = base.plusNanos(1);

        System.out.println("raw equals: " + base.equals(later));
        System.out.println("same whole second: " +
            base.truncatedTo(ChronoUnit.SECONDS)
                .equals(later.truncatedTo(ChronoUnit.SECONDS)));
    }
}
~~~

Lưu file **PrecisionDemo.java** và chạy:

~~~bash
javac PrecisionDemo.java
java PrecisionDemo
~~~

Kết quả:

~~~text
raw equals: false
same whole second: true
~~~

**Ràng buộc thiết kế:** Nếu dữ liệu từ database chỉ lưu tới mili giây, còn nguồn khác tới nano giây, phải định nghĩa trước **độ chính xác chuẩn**. Hãy chuẩn hóa giá trị **trước khi lưu và trước khi làm khóa**, với quy tắc cắt hoặc làm tròn phù hợp. Không viết equals tùy biến coi hai mốc “gần nhau” là bằng nhau theo ngưỡng sai số, ví dụ cách nhau dưới 1 giây: quan hệ kiểu đó có thể **không bắc cầu**.

### Định dạng chuỗi không phải giá trị

**toString()**, **DateTimeFormatter** và **parse** liên quan tới biểu diễn văn bản. Hai chuỗi khác nhau có thể biểu diễn cùng Instant, ví dụ có offset khác nhau. Nếu muốn so **Instant**, parse đúng kiểu rồi chuẩn hóa. Không so chuỗi đã định dạng như một cách thay thế cho equals, đặc biệt khi định dạng có thể bỏ qua offset, zone hoặc phần giây.

Không suy ra **equals** từ kết quả **format(...)** trừ khi quy tắc nghiệp vụ cố ý chọn một biểu diễn văn bản chuẩn duy nhất, không mất dữ liệu cần thiết.

## 10. Chọn khóa cho HashMap, HashSet và record

Các kiểu java.time bất biến thường là **khóa tốt** vì equals/hashCode của chúng ổn định theo giá trị. Nhưng chọn **đúng kiểu khóa** quyết định thế nào là một phần tử trùng.

| Bài toán | Khóa phù hợp | Vì sao |
| --- | --- | --- |
| Sự kiện toàn cầu cùng thời điểm | **Instant** | Bỏ qua offset và zone hiển thị |
| Ngày nghỉ địa phương không kèm giờ | **LocalDate** cùng quy ước lịch/zone | Giá trị là ngày, không phải Instant |
| Bản ghi giữ nguyên offset ban đầu | **OffsetDateTime** | Offset là thành phần của giá trị |
| Lịch họp phải nhớ zone được chọn | **ZonedDateTime** hoặc cấu trúc chứa **LocalDateTime + ZoneId + chính sách DST** | Phải phân biệt quy tắc vùng và cách diễn giải giờ địa phương |
| Khoảng thời gian máy đo được | **Duration** | Theo giây và nano |
| Chu kỳ tính theo ngày/tháng/năm lịch | **Period** | Theo đơn vị lịch |
| Thống kê theo tháng | **YearMonth** | Chỉ cần năm và tháng |

### Ví dụ đầy đủ: khóa sự kiện dùng Instant chuẩn hóa

Tạo file **EventKeyDemo.java**:

~~~java
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class EventKeyDemo {
    record EventKey(String resourceId, Instant startedAt) {}

    public static void main(String[] args) {
        OffsetDateTime local =
            OffsetDateTime.parse("2026-10-23T09:00:00+07:00");
        OffsetDateTime utc =
            OffsetDateTime.parse("2026-10-23T02:00:00Z");

        EventKey first = new EventKey("room-7", local.toInstant());
        EventKey second = new EventKey("room-7", utc.toInstant());

        Map<EventKey, String> events = new HashMap<>();
        events.put(first, "confirmed");

        System.out.println("same key: " + first.equals(second));
        System.out.println("lookup: " + events.get(second));
        System.out.println("size: " + events.size());
    }
}
~~~

Chạy:

~~~bash
javac EventKeyDemo.java
java EventKeyDemo
~~~

Kết quả:

~~~text
same key: true
lookup: confirmed
size: 1
~~~

Record **EventKey** tự sinh equals/hashCode theo các component, trong đó **String** và **Instant** đều có hợp đồng giá trị ổn định. Hai OffsetDateTime khác biểu diễn được chuẩn hóa thành cùng Instant, nên hai khóa bằng nhau.

**Tuy nhiên, nếu business cần phân biệt offset ban đầu**, khóa chỉ chứa Instant đã cố ý **làm mất thông tin đó**. Lúc này cần lưu riêng offset, zone hoặc biểu diễn ban đầu. Việc chuẩn hóa phải theo đúng tiêu chí của bài toán, không phải lúc nào cũng chuyển hết sang UTC là đủ.

### Trường hợp record chứa java.time

Record có component **LocalDate**, **Instant**, **ZoneId**, **Duration**... thường dùng được equals/hashCode sinh tự động. Nhưng phải tự hỏi record đang mô tả **cùng bản ghi dữ liệu** hay **cùng thời điểm**. Ví dụ record gồm **Instant** và **ZoneId** sẽ coi hai record có cùng Instant nhưng zone khác nhau là **khác giá trị**, vì tất cả component đều tham gia so sánh.

## 11. Các sai lầm thường gặp

### 11.1. Dùng == thay equals

Không dựa vào identity với value-based classes. Kể cả khi hai biến tình cờ trỏ cùng object, **==** không biểu diễn tiêu chí tương đương theo hợp đồng thời gian.

### 11.2. Xem isEqual và equals là đồng nghĩa

**OffsetDateTime** và **ZonedDateTime** có thể **isEqual true** nhưng **equals false**. Một bên xét cùng Instant, bên kia xét đầy đủ giá trị chứa offset/zone. Khi chọn cấu trúc dữ liệu, HashMap/HashSet dùng **equals/hashCode**, không âm thầm dùng isEqual.

### 11.3. Tưởng mọi compareTo == 0 chỉ dựa vào Instant

**OffsetDateTime.compareTo** và **ZonedDateTime.compareTo** giữ thứ tự nhất quán với equals; nếu hai giá trị biểu diễn cùng Instant nhưng có offset/zone khác nhau, compareTo có thể khác 0. Đừng dùng TreeSet theo thứ tự tự nhiên để suy ra nó loại trùng chỉ theo cùng Instant.

### 11.4. Xem LocalDateTime là Instant

LocalDateTime không lưu zone, nên không thể biết mốc UTC nếu chưa xác định zone/offset và xử lý gap/overlap. Một ngày-giờ địa phương không đủ cho việc định danh sự kiện tuyệt đối xuyên múi giờ.

### 11.5. Dùng ZoneOffset thay ZoneId khi cần quy tắc tương lai

Offset +07:00 chỉ là độ lệch cố định. Một vùng có thể đổi offset theo mùa hoặc theo quyết định pháp luật. Với lịch hẹn tương lai theo giờ địa phương của vùng, lưu ZoneId và chính sách giải quyết DST; khi cần mốc tuyệt đối, xác định Instant theo quy tắc thời gian tương ứng.

### 11.6. Cho rằng Period một ngày luôn bằng Duration 24 giờ

Qua DST, ngày lịch có thể dài 23 hoặc 25 giờ. Tùy yêu cầu, chọn cộng bằng Period hoặc Duration.

### 11.7. Bỏ qua nano khi gom dữ liệu

Một số nguồn lưu thời gian tới giây, mili hoặc micro, nguồn khác tới nano. Equals vẫn phân biệt phần nano; nếu muốn gom theo giây cần **chuẩn hóa rõ ràng** trước.

### 11.8. Tự cài equals theo sai tiêu chí

Đối với kiểu domain chứa Instant, OffsetDateTime hay ZoneId, **record equals tự động** có thể là đúng nếu mọi component đều thuộc giá trị. Nếu domain muốn so khác, ví dụ chỉ theo eventId, cần mô hình khóa domain riêng. Tránh sửa equals dựa vào phép “xấp xỉ thời gian” không bắc cầu.

## 12. Checklist kiểm tra khi làm việc với java.time

1. **Xác định chính xác loại đại lượng:** ngày, giờ địa phương, mốc tuyệt đối, offset, zone, thời lượng hay kỳ lịch.
2. **Đọc hợp đồng của đúng kiểu:** những trường nào tham gia equals/hashCode? Có API khác như isEqual hoặc toInstant đáp ứng tiêu chí thật sự không?
3. **Kiểm tra phân biệt kiểu:** LocalDate khác LocalDateTime; Instant khác OffsetDateTime; Duration khác Period; dữ liệu cùng ý nghĩa trừu tượng chưa chắc là cùng giá trị kiểu Java.
4. **Định nghĩa khóa trước khi đưa vào HashMap/HashSet:** cùng bản ghi có cần giữ offset, ZoneId, chronology, độ chính xác nano và zone do người dùng chọn không?
5. **Xử lý zone và DST:** một local date-time không tồn tại hoặc tồn tại hai lần thì quy tắc chọn Instant là gì? Có cần lưu offset được chọn không?
6. **Thống nhất precision và chronology:** nếu dùng nhiều nguồn/hệ lịch, chuyển đổi về khóa canonical có chủ đích.
7. **Kiểm tra bằng dữ liệu đối chứng:** hai OffsetDateTime cùng Instant khác offset; hai ZonedDateTime cùng Instant khác zone; hai LocalDateTime cùng giờ ở hai vùng; Duration và Period qua ngày DST; hai Instant lệch 1 nano.
8. **Xác minh hợp đồng hash:** các khóa equals true phải có hash bằng nhau, nhưng hai hash bằng nhau không chứng minh equals true.

**Ghi nhớ cốt lõi:** Các kiểu **java.time là giá trị theo những hợp đồng khác nhau**. **equals/hashCode** trả lời liệu hai object có cùng **giá trị của đúng kiểu đó** hay không. **isEqual** trên kiểu phù hợp hoặc **toInstant()** trả lời liệu chúng có cùng **mốc trên dòng thời gian** hay không. **LocalDateTime** chưa có mốc tuyệt đối; **Period** không phải **Duration**; **ZoneId** không phải **ZoneOffset**. Chọn kiểu và phép so khớp với ý nghĩa nghiệp vụ trước khi gom nhóm, tra cứu hay loại trùng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="standard-types-009">Không suy rộng quy tắc của một lớp chuẩn cho mọi lớp khác</a>

<details>
<summary>Xem chi tiết</summary>

# Không suy rộng quy tắc của một lớp chuẩn cho mọi lớp khác

## 1. Nguyên tắc

Trong Java, **hợp đồng chung** của `equals()`, `hashCode()`, `compareTo()` không đồng nghĩa mọi lớp chuẩn có cùng cách định nghĩa giá trị. Quan sát `String.equals()` so nội dung rồi áp dụng cho `StringBuilder`, mảng, `BigDecimal` hay kiểu thời gian sẽ dễ sai.

| Phép/API | Điều có thể khẳng định | Điều phải tra theo từng kiểu |
|---|---|---|
| `==` (tham chiếu) | Cùng object hoặc đều `null` | Literal/cache có tái dùng instance không |
| `equals()` | Phản xạ, đối xứng, bắc cầu, nhất quán, khác `null` | Các trường hoặc khía cạnh tạo equality |
| `hashCode()` | Nếu equals đúng thì hash phải bằng | Cách băm, va chạm, trường tham gia |
| `Comparable` | Kết quả âm/0/dương biểu thị trước/ngang/sau | Lớp có natural order không, `0` có tương đương equals không |
| `Comparator` | Định nghĩa thứ tự do bên gọi chọn | Nhiều giá trị khác nhau có thể so bằng `0` |
| Collection | Theo đúng hợp đồng API cụ thể | Quy tắc trùng, `null`, thứ tự, factory |

**Hash trùng không chứng minh bằng nhau**; `compareTo() == 0` cũng không mặc nhiên đồng nghĩa `equals() == true`. Dù đều là kiểu chuẩn, từng lớp có thể cố ý chọn tiêu chí khác.

## 2. `String`, `StringBuilder` và lớp tự viết

`String.equals` so chuỗi ký tự; `StringBuilder` thừa hưởng `Object.equals` nên so identity. Một lớp tự viết không ghi đè `equals` cũng vậy.

~~~java
String a = new String("Java");
String b = new String("Java");
StringBuilder x = new StringBuilder("Java");
StringBuilder y = new StringBuilder("Java");
System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
System.out.println(x.equals(y)); // false
~~~

Đây là đoạn minh họa đặt trong `main`. Sự khác nhau đến từ **hợp đồng lớp**, không phải cả hai đều lưu văn bản. `String.equals` phân biệt case; `"Java".equalsIgnoreCase("JAVA")` trả `true` nhưng `"Java".equals("JAVA")` trả `false`. `String.CASE_INSENSITIVE_ORDER` còn có thể gom các chuỗi này trong một `TreeSet`. Không dùng `==` để so nội dung dựa trên quan sát về string interning.

## 3. `BigDecimal` và wrapper số

~~~java
BigDecimal n1 = new BigDecimal("2.0");
BigDecimal n2 = new BigDecimal("2.00");
System.out.println(n1.equals(n2));       // false
System.out.println(n1.compareTo(n2) == 0); // true
~~~

Cần `java.math.BigDecimal`. `BigDecimal.equals` xét **scale** (1 so với 2), còn `compareTo` xét giá trị số. Vì vậy `HashSet<BigDecimal>` có thể giữ cả hai, trong khi `TreeSet<BigDecimal>` với natural order chỉ giữ một. `String.compareTo == 0` nhất quán với `String.equals` nhưng **không nên suy quy tắc đó sang BigDecimal**.

Tương tự, `Integer.valueOf(10).equals(Long.valueOf(10L))` trả `false`, dù cùng số 10, vì `Integer.equals` yêu cầu đúng kiểu wrapper. Với `Double`, `Double.NaN == Double.NaN` là `false` ở primitive, nhưng hai `Double` chứa `NaN` bằng nhau qua `Double.equals`. `Double.equals(-0.0, +0.0)` không phải cú pháp API; gọi qua hai wrapper thì trả `false`, trong khi `-0.0 == +0.0` trên primitive là `true`.

Không suy từ so sánh số học sang `equals` của object. Khi thiết kế dữ liệu tiền tệ, hãy quyết định có tính scale hay chỉ lượng số, rồi dùng một tiêu chí nhất quán trong key và collection.

## 4. Mảng, record và collection

Mảng Java mặc định không ghi đè `Object.equals`: hai `int[]` khác instance có cùng phần tử vẫn không bằng nhau qua `array.equals`. Dùng `Arrays.equals` để so từng phần tử của mảng một chiều, và `Arrays.deepEquals` khi cần so sâu mảng `Object[]` có phần tử lồng.

`record` tự sinh `equals`/`hashCode` dựa trên component, nhưng **component là array vẫn so identity**; record không biến array thành deep equality.

Các collection khác nhau:

- `List.equals` xét từng phần tử **theo thứ tự**; một `ArrayList` có thể bằng `List.of` cùng chuỗi phần tử.
- `Set.equals` xét **cùng phần tử**, không phụ thuộc thứ tự duyệt; `HashSet` có thể bằng `LinkedHashSet` cùng tập phần tử.
- `HashSet` dùng `equals/hashCode` để nhận biết phần tử trùng; `TreeSet` dùng `compareTo` hoặc comparator trả `0` để nhận biết khóa trùng.
- `HashSet.add` phần tử trùng trả `false`, nhưng factory `Set.of` với phần tử trùng **ném `IllegalArgumentException`**. `List.of` cho phép phần tử trùng. `HashSet` chứa được một `null`; factory `Set.of` từ chối `null`.

Ví dụ comparator chỉ theo độ dài khiến `"hi"` và `"ok"` ngang hàng trong `TreeSet`, dù `String.equals` giữa chúng trả `false`. Muốn giữ cả hai, dùng comparator có tiêu chí phụ hoặc collection khác.

## 5. Date-time: cùng thời điểm khác equality

`OffsetDateTime.parse("2026-01-01T10:00:00+07:00")` và `OffsetDateTime.parse("2026-01-01T03:00:00Z")` đại diện cùng instant trên dòng thời gian.

`isEqual()` giữa chúng là `true`; `equals()` là `false`, vì `equals` xét giá trị biểu diễn gồm offset. Nếu nghiệp vụ hỏi **cùng instant**, dùng `isEqual` hoặc so `Instant`. Nếu hỏi **cùng giá trị OffsetDateTime**, dùng `equals`. Không dùng hợp đồng của `LocalDate` hay `Instant` để tự suy hành vi của `OffsetDateTime`/`ZonedDateTime`.

## 6. Chương trình Java 17 chạy được

Lưu thành file **`StandardContractsDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class StandardContractsDemo {
    record Box(int[] values) {}

    public static void main(String[] args) {
        String s1 = new String("Java");
        String s2 = new String("Java");
        System.out.println("String identity: " + (s1 == s2));
        System.out.println("String equals: " + s1.equals(s2));
        System.out.println("Builder equals: "
                + new StringBuilder("Java")
                .equals(new StringBuilder("Java")));

        System.out.println("Integer vs Long: "
                + Integer.valueOf(10).equals(Long.valueOf(10L)));
        System.out.println("Primitive NaN: "
                + (Double.NaN == Double.NaN));
        System.out.println("Wrapper NaN: "
                + Double.valueOf(Double.NaN)
                .equals(Double.valueOf(Double.NaN)));

        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");
        System.out.println("Decimal equals: " + a.equals(b));
        System.out.println("Decimal compare zero: "
                + (a.compareTo(b) == 0));
        System.out.println("HashSet decimals: "
                + new HashSet<>(List.of(a, b)).size());
        System.out.println("TreeSet decimals: "
                + new TreeSet<>(List.of(a, b)).size());

        int[] x = {1, 2};
        int[] y = {1, 2};
        System.out.println("Array equals: " + x.equals(y));
        System.out.println("Arrays.equals: " + Arrays.equals(x, y));
        System.out.println("Record array equals: "
                + new Box(x).equals(new Box(y)));

        List<String> listA = List.of("a", "b");
        List<String> listB = new ArrayList<>(List.of("a", "b"));
        Set<String> setA = new HashSet<>(List.of("a", "b"));
        Set<String> setB = new LinkedHashSet<>(List.of("b", "a"));
        System.out.println("List across classes: "
                + listA.equals(listB));
        System.out.println("Set across classes: "
                + setA.equals(setB));

        TreeSet<String> byLength = new TreeSet<>(
                Comparator.comparingInt(String::length));
        byLength.add("hi");
        byLength.add("ok");
        System.out.println("TreeSet length only: " + byLength.size());

        System.out.println("String hash collision: "
                + ("Aa".hashCode() == "BB".hashCode()));
        System.out.println("Collision equals: "
                + "Aa".equals("BB"));

        OffsetDateTime hcm = OffsetDateTime.parse(
                "2026-01-01T10:00:00+07:00");
        OffsetDateTime utc = OffsetDateTime.parse(
                "2026-01-01T03:00:00Z");
        System.out.println("Date same instant: " + hcm.isEqual(utc));
        System.out.println("Date equals: " + hcm.equals(utc));

        try {
            Set.of("x", "x");
        } catch (IllegalArgumentException expected) {
            System.out.println("Set.of duplicate: rejected");
        }
    }
}
~~~

Chạy với JDK 17:

~~~bash
javac StandardContractsDemo.java
java StandardContractsDemo
~~~

Đầu ra:

~~~text
String identity: false
String equals: true
Builder equals: false
Integer vs Long: false
Primitive NaN: false
Wrapper NaN: true
Decimal equals: false
Decimal compare zero: true
HashSet decimals: 2
TreeSet decimals: 1
Array equals: false
Arrays.equals: true
Record array equals: false
List across classes: true
Set across classes: true
TreeSet length only: 1
String hash collision: true
Collision equals: false
Date same instant: true
Date equals: false
Set.of duplicate: rejected
~~~

Đầu ra chỉ dùng boolean/kích thước; không dựa vào thứ tự duyệt không được bảo đảm của `HashSet`. `"Aa"` và `"BB"` là một va chạm hash có thật nhưng hai chuỗi không bằng nhau; collision **không** làm hai phần tử logic trở thành một.

## 7. Cách áp dụng và kiểm chứng

Khi gặp lớp chuẩn mới, hãy **đọc Javadoc của đúng lớp** thay vì sao chép quy tắc từ lớp quen thuộc:

1. Xác định `equals` đang dùng identity, giá trị, kiểu runtime, ID hay trường nào; kiểm tra `null`.
2. Kiểm tra `hashCode` cùng dữ liệu quyết định equality. Nếu object đã làm khóa hash collection, tránh thay đổi trường tham gia equality/hash.
3. Kiểm tra lớp có `Comparable` không và `compareTo == 0` có nhất quán với `equals` không. Nếu truyền comparator, xét tiêu chí phá hòa.
4. Kiểm tra collection/factory: `HashSet`, `TreeSet`, `List.of`, `Set.of` có hợp đồng khác nhau về trùng, `null`, thứ tự.
5. Thử cặp khác instance cùng dữ liệu; hai kiểu số; `BigDecimal` khác scale; array lồng; `NaN`; cùng instant khác offset; hash trùng nhưng giá trị khác.
6. Chọn quan hệ đúng với **yêu cầu nghiệp vụ**: so số bất kể scale có thể cần `compareTo`; so instant cần `isEqual`/`Instant`; so nội dung mảng cần `Arrays.equals`.

## 8. Lỗi thường gặp

| Giả định sai | Hệ quả | Cách sửa |
|---|---|---|
| Có cùng trường thì object tự bằng nhau | Lớp không override `equals` vẫn dùng identity | Xem hợp đồng lớp, tự định nghĩa value object nếu cần |
| Mọi kiểu văn bản giống `String` | `StringBuilder.equals` vẫn dùng identity | Dùng phép so nội dung thích hợp |
| Số giống nhau thì wrapper luôn equals | `Integer` khác `Long`, BigDecimal xét scale | Chọn kiểu và chính sách so sánh |
| `compareTo == 0` đồng nghĩa `equals` | Sai với BigDecimal/comparator tùy chỉnh | Kiểm tra nghĩa comparator |
| Hash bằng nhau là object bằng nhau | Collision bị hiểu thành trùng | So equality, không kết luận từ hash |
| Record tự so sâu array | Hai array nội dung giống nhau vẫn khác identity | Thiết kế lại component hoặc override đúng |
| Tất cả set khử trùng giống nhau | Comparator hoặc factory làm kết quả khác | Đọc API cụ thể |
| Cùng instant thì date-time `equals` | Khác offset có thể `equals false` | So instant khi đó là mục tiêu |

## 9. Câu hỏi tự kiểm tra

**1.** Hai `StringBuilder("A")` cùng nội dung nhưng `equals false`: có mâu thuẫn với `String.equals` không?  
**Đáp án:** Không. Hai lớp có hợp đồng khác nhau.

**2.** Vì sao `HashSet<BigDecimal>` giữ `2.0` và `2.00`, còn `TreeSet<BigDecimal>` chỉ giữ một?  
**Đáp án:** `equals` xét scale; `compareTo` xét lượng số.

**3.** Hai mảng cùng phần tử nhưng `array.equals` sai; nên dùng gì?  
**Đáp án:** `Arrays.equals` hoặc `Arrays.deepEquals` phù hợp với cấu trúc.

**4.** `TreeSet` comparator chỉ xét độ dài có giữ cả `"hi"` và `"ok"` không?  
**Đáp án:** Không; comparator trả `0`.

**5.** `OffsetDateTime.isEqual` đúng nhưng `equals` sai có hợp lệ không?  
**Đáp án:** Có; cùng instant khác biểu diễn offset.

**Ghi nhớ:** Bất biến chung của `Object` là nền tảng, còn **nghĩa cụ thể của equality, hash, ordering và collection behavior phải được kiểm tra ở mỗi lớp/API**.


</details>

- [Quay lại đầu trang](#back-to-top)
