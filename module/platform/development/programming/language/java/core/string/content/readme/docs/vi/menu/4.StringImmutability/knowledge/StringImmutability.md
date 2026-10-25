<a id="back-to-top"></a>

# Tính bất biến

## Menu
- [Giá trị String không đổi sau khi đối tượng được tạo](#string-immutability-001)
- [Phân biệt final của biến với bất biến của đối tượng](#string-immutability-002)
- [Phương thức biến đổi trả kết quả thay vì sửa chuỗi nguồn](#string-immutability-003)
- [Vì sao bỏ qua giá trị trả về dễ làm mất kết quả](#string-immutability-004)
- [Gán lại tham chiếu không làm thay đổi chuỗi được chia sẻ](#string-immutability-005)
- [Sao chép đầu vào char&#91;&#93; và tính độc lập của toCharArray](#string-immutability-006)
- [Lợi ích khi dùng làm khóa, chia sẻ dữ liệu và xử lý đồng thời](#string-immutability-007)
- [Chuỗi bất biến không làm mọi biến hoặc cấu trúc chứa nó an toàn đa luồng](#string-immutability-008)

## <a id="string-immutability-001">Giá trị String không đổi sau khi đối tượng được tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị `String` không đổi sau khi đối tượng được tạo (tính bất biến trong Java)

## 1. Tính bất biến (*immutability*) là gì?

Trong Java, một đối tượng `String` là **bất biến**: sau khi được tạo, nội dung chuỗi của chính đối tượng đó không thể thay đổi. Mọi thao tác muốn biểu diễn nội dung khác phải trả về/tham chiếu đến một giá trị `String` khác; nó không sửa các ký tự đã có trong đối tượng gốc.

Điều này khác với một **biến tham chiếu**. Biến kiểu `String` (nếu không khai báo `final`) vẫn có thể được gán để trỏ sang đối tượng khác. Chẳng hạn:

```java
String text = "Java";
text = text + " 17";
```

Sau phép gán, biến `text` chứa `"Java 17"`. Đối tượng chuỗi `"Java"` trước đó **không bị biến đổi**. Đó là điểm quan trọng nhất của bài: **đổi giá trị biến** không có nghĩa là **sửa nội dung đối tượng**.

Có thể hình dung như sau:

```text
Ban đầu:       text ───────> String "Java"

Tạo giá trị mới:              String "Java 17"
                String "Java" (vẫn nguyên vẹn)

Gán lại:       text ───────> String "Java 17"
                            (đối tượng "Java" không bị sửa)
```

Sơ đồ mô tả ý nghĩa tham chiếu, không khẳng định cách JVM cấp phát/thu hồi từng đối tượng cụ thể.

## 2. Đối tượng không đổi, nhưng biến có thể trỏ nơi khác

Một biến khác có thể cùng tham chiếu đến chuỗi ban đầu:

```java
String original = "Hello";
String alias = original;

original = original.concat(" Java");

// original -> "Hello Java"
// alias    -> "Hello"
```

`concat(" Java")` trả về chuỗi có nội dung mới. Phép gán lại chỉ thay đổi tham chiếu của `original`; `alias` vẫn tham chiếu chuỗi `"Hello"`. Nếu phương thức thực sự sửa đối tượng gốc, `alias` cũng sẽ nhìn thấy sự thay đổi; với `String`, điều đó không xảy ra.

Câu nói “`String` không thể đổi” cần được hiểu chính xác là **nội dung của một đối tượng `String` cụ thể không thể đổi**, không có nghĩa biến `String` phải giữ mãi một giá trị.

### `final String` khác với tính bất biến của `String`

```java
final String language = "Java";
// language = "Kotlin"; // LỖI BIÊN DỊCH: không thể gán lại biến final
```

`final` ngăn biến `language` bị gán lại sau khi đã khởi tạo; còn tính bất biến của `String` ngăn chính đối tượng bị sửa nội dung. Kể cả khi bỏ `final`, bản thân `String` vẫn bất biến. Ngược lại, khai báo `final StringBuilder builder` chỉ cố định **tham chiếu** `builder`, không ngăn `builder.append(...)` sửa nội dung của `StringBuilder`.

## 3. Những phương thức trông như “sửa” chuỗi thực ra làm gì?

Nhiều phương thức của `String` trả về giá trị để sử dụng, nhưng không thay đổi đối tượng gọi phương thức.

| Phép gọi | Kết quả biểu diễn | Đối tượng chuỗi ban đầu |
| --- | --- | --- |
| `"cat".concat("s")` | `"cats"` | Vẫn là `"cat"` |
| `"cat".replace('c', 'b')` | `"bat"` | Vẫn là `"cat"` |
| `"java".toUpperCase(java.util.Locale.ROOT)` | `"JAVA"` | Vẫn là `"java"` |
| `"  abc  ".strip()` | `"abc"` | Vẫn là `"  abc  "` |
| `"hello".substring(1, 4)` | `"ell"` | Vẫn là `"hello"` |

**Phải giữ kết quả nếu muốn sử dụng nội dung mới:**

```java
String name = "java";
name.toUpperCase(java.util.Locale.ROOT);
System.out.println(name); // java

name = name.toUpperCase(java.util.Locale.ROOT);
System.out.println(name); // JAVA
```

Lệnh đầu chỉ gọi hàm mà không dùng giá trị trả về; không có tác dụng sửa `name`. Lệnh sau gán lại biến.

**Đừng hiểu nhầm rằng mỗi lần gọi phương thức đều bắt buộc tạo một đối tượng mới.** Một số phương thức có thể trả lại chính đối tượng hiện tại khi kết quả không đổi, ví dụ `"abc".concat("")`. Điều được đảm bảo là **không làm thay đổi nội dung của đối tượng `String` gốc**; việc có cấp phát đối tượng mới hay tái sử dụng đối tượng hiện hữu là vấn đề khác. Trong lập trình ứng dụng, đừng dùng phép so sánh tham chiếu `==` để suy luận nội dung từ các thao tác này.

## 4. Ví dụ đầy đủ chạy bằng Java 17

Lưu chương trình sau vào tệp `StringImmutabilityDemo.java`:

```java
import java.util.Locale;

public class StringImmutabilityDemo {
    public static void main(String[] args) {
        String original = "Java";
        String alias = original;
        String extended = original.concat(" 17");

        System.out.println("original = " + original);
        System.out.println("alias = " + alias);
        System.out.println("extended = " + extended);

        String upper = original.toUpperCase(Locale.ROOT);
        System.out.println("upper = " + upper);
        System.out.println("original after method = " + original);

        original = extended;
        System.out.println("original after assignment = " + original);
        System.out.println("alias after assignment = " + alias);

        char[] source = {'J', 'a', 'v', 'a'};
        String fromArray = new String(source);
        source[0] = 'K';
        System.out.println("source array = " + new String(source));
        System.out.println("string from array = " + fromArray);

        char[] exported = fromArray.toCharArray();
        exported[0] = 'Z';
        System.out.println("exported array = " + new String(exported));
        System.out.println("string after export = " + fromArray);

        StringBuilder builder = new StringBuilder("Go");
        String snapshot = builder.toString();
        builder.append("!");
        System.out.println("builder = " + builder);
        System.out.println("snapshot = " + snapshot);

        String literal = "same";
        String created = new String("same");
        System.out.println("same content = " + literal.equals(created));
        System.out.println("same reference = " + (literal == created));
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac StringImmutabilityDemo.java
java StringImmutabilityDemo
```

Kết quả in ra:

```text
original = Java
alias = Java
extended = Java 17
upper = JAVA
original after method = Java
original after assignment = Java 17
alias after assignment = Java
source array = Kava
string from array = Java
exported array = Zava
string after export = Java
builder = Go!
snapshot = Go
same content = true
same reference = false
```

Ví dụ minh họa bốn điểm: thay đổi tham chiếu không sửa chuỗi cũ; phương thức `String` trả về kết quả; mảng ký tự bên ngoài không thể sửa nội dung `String` đã tạo; `StringBuilder` thay đổi được nhưng `String` lấy từ `toString()` vẫn là một giá trị bất biến.

## 5. Vì sao mảng `char[]` không thể sửa một `String`?

Mảng ký tự là cấu trúc **mutable**: có thể gán `chars[0] = 'X'`. Nhưng khi tạo `String` từ mảng `char[]`, Java lấy nội dung của mảng để tạo chuỗi và **không để các thay đổi về sau của mảng ảnh hưởng đến chuỗi**.

```java
char[] chars = {'a', 'b', 'c'};
String s = new String(chars);
chars[0] = 'X';

System.out.println(new String(chars)); // Xbc
System.out.println(s);                 // abc
```

Tương tự, `s.toCharArray()` tạo mảng riêng để bạn sửa nếu cần. Việc sửa mảng trả về sẽ không làm `s` đổi nội dung. `String` cũng không cung cấp API công khai để người dùng gán trực tiếp ký tự tại một vị trí như `s.setCharAt(...)`; phương thức kiểu này thuộc các đối tượng mutable như `StringBuilder`.

Chi tiết triển khai bên trong `String` thay đổi giữa các phiên bản JDK (chẳng hạn `String` hiện đại có thể lưu dữ liệu theo dạng compact strings). Người học nên dựa vào **hợp đồng bất biến của API**, không dựa vào trường nội bộ hay giả định `String` luôn lưu bằng một mảng `char[]`. Không dùng reflection/`Unsafe` để can thiệp vào biểu diễn nội bộ: điều đó phá vỡ giả định của chương trình và không phải cách sử dụng `String` được hỗ trợ.

## 6. Tính bất biến và string pool

Java có cơ chế *string pool* để chia sẻ các chuỗi có cùng nội dung trong những trường hợp phù hợp. Đặc biệt, chuỗi literal và các biểu thức hằng kiểu chuỗi được intern:

```java
String a = "Java";
String b = "Ja" + "va"; // biểu thức hằng được gộp khi biên dịch
System.out.println(a == b);      // true
System.out.println(a.equals(b)); // true
```

Ở đây `a == b` là `true` vì hai tham chiếu đến cùng literal đã được intern. **Không suy rộng** kết quả này cho mọi chuỗi có cùng nội dung. Ví dụ:

```java
String a = "Java";
String b = new String("Java");

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
System.out.println(a == b.intern()); // true
```

`new String("Java")` được dùng ở đây **chỉ để minh họa** sự khác nhau giữa tham chiếu và giá trị; trong ứng dụng thông thường không cần tạo đối tượng thừa như vậy.

String pool có thể chia sẻ an toàn một đối tượng `String` vì không ai có thể sửa nội dung của nó: các nơi dùng chung sẽ luôn thấy cùng dãy ký tự. Muốn so sánh **nội dung**, dùng `equals()`; `==` chỉ phù hợp khi bạn thực sự cần so sánh **cùng đối tượng** (hoặc kiểm tra với `null`).

## 7. Khi cần sửa nhiều lần: dùng `StringBuilder`

`StringBuilder` là đối tượng **có thể thay đổi**. Các thao tác như `append()`, `insert()`, `delete()`, `replace()` và `setCharAt()` cập nhật trạng thái của builder, thay vì tạo một đối tượng `String` bất biến sau mỗi bước.

Ví dụ chương trình độc lập `BuildMessage.java`:

```java
public class BuildMessage {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("Java");
        String before = builder.toString();

        builder.append(" 17");
        builder.setCharAt(0, 'j');

        String after = builder.toString();
        System.out.println("before = " + before);
        System.out.println("builder = " + builder);
        System.out.println("after = " + after);
    }
}
```

Chạy:

```bash
javac BuildMessage.java
java BuildMessage
```

Kết quả:

```text
before = Java
builder = java 17
after = java 17
```

`before` không thay đổi sau khi builder được sửa. `toString()` cung cấp một giá trị `String` bất biến tại thời điểm gọi; sửa builder sau đó không làm các chuỗi đã trả về đổi nội dung.

### So sánh lựa chọn khi ghép chuỗi

| Trường hợp | Cách phù hợp | Lý do |
| --- | --- | --- |
| Ghép vài đoạn đơn giản | `first + " " + last` | Dễ đọc; compiler/JVM có thể tối ưu |
| Ghép nhiều đoạn trong vòng lặp | `StringBuilder` rồi `toString()` | Tránh lặp lại việc hình thành các giá trị chuỗi trung gian không cần thiết |
| Cần chuỗi ổn định để lưu/chia sẻ | `String` | Nội dung đối tượng không đổi |
| Một đối tượng builder dùng từ nhiều thread | Thiết kế đồng bộ thích hợp hoặc tách mỗi thread một builder | `StringBuilder` không bảo đảm thread-safe khi cùng bị sửa từ nhiều thread |

Không nên tuyên bố mọi phép `+` đều “chậm”; Java có tối ưu ghép chuỗi. Điều cần nhận biết là `String` không cung cấp cơ chế sửa tại chỗ, nên khi tích lũy dữ liệu qua nhiều bước, builder có thể phù hợp hơn.

## 8. Tác dụng của tính bất biến

### 8.1. Chia sẻ nội dung an toàn

Nhiều đoạn code hoặc nhiều thread có thể giữ tham chiếu đến **cùng một** `String` mà không lo một nơi sửa nội dung khiến nơi khác quan sát thấy ký tự mới. Tính chất này hữu ích khi truyền tham số, trả kết quả, lưu cấu hình và chia sẻ hằng số.

Điều đó **không tự động làm mọi chương trình chứa `String` trở nên thread-safe**. Nếu nhiều thread cùng đọc/ghi một **biến tham chiếu** kiểu `String`, vẫn phải xử lý đúng vấn đề chia sẻ trạng thái, khả năng nhìn thấy giá trị và đồng bộ khi cần. Sự an toàn về **nội dung đối tượng** và sự an toàn khi **cập nhật biến** là hai vấn đề khác nhau.

### 8.2. Làm khóa cho `HashMap` hoặc phần tử `HashSet`

`String.equals()` và `String.hashCode()` phụ thuộc vào nội dung chuỗi. Vì nội dung không thể đổi, một `String` đã dùng làm khóa không tự thay đổi cách so sánh/hash của nó trong khi đang nằm trong cấu trúc dữ liệu.

```java
java.util.Map<String, Integer> scores = new java.util.HashMap<>();
String key = "Java";
scores.put(key, 10);

key = key + " 17";

System.out.println(scores.get("Java"));    // 10
System.out.println(scores.get("Java 17")); // null
```

Gán lại `key` không đổi khóa `"Java"` đã được đưa vào map. Tính bất biến giúp ngăn lỗi thường gặp khi đối tượng khóa mutable bị thay đổi nội dung sau khi chèn.

### 8.3. Dễ hiểu luồng dữ liệu hơn

Một phương thức nhận `String` có thể tin rằng mã bên ngoài không sửa các ký tự của chính đối tượng được truyền vào trong lúc phương thức xử lý. Nếu cần văn bản khác, code trả về hoặc nhận một giá trị mới, làm hành vi rõ ràng và dễ kiểm thử.

Tuy nhiên, bất biến **không đồng nghĩa với bí mật hay an toàn tuyệt đối**: ví dụ, dữ liệu nhạy cảm trong `String` không thể chủ động ghi đè ký tự theo cách ghi đè một mảng có thể sửa; thời điểm đối tượng được thu gom là do JVM quyết định. Với dữ liệu nhạy cảm cần chính sách xóa khỏi bộ nhớ, cân nhắc thiết kế và API phù hợp, thay vì dựa vào khả năng “xóa nội dung String”.

## 9. Những nhầm lẫn thường gặp

1. **“`s += "x"` đã sửa đối tượng `s`.”** Sai: biểu thức tạo ra giá trị chuỗi với nội dung mới và gán lại biến `s`. Alias tham chiếu chuỗi cũ vẫn nhìn thấy nội dung cũ.
2. **“Gọi `s.replace(...)` là đủ.”** Sai nếu bạn cần kết quả. `replace` trả về chuỗi kết quả; phải gán vào biến hoặc truyền giá trị trả về cho bước tiếp theo.
3. **“`String` immutable vì tôi khai báo `final`.”** Không đúng: `String` vốn bất biến dù tham chiếu không `final`. `final` chỉ cấm gán lại biến.
4. **“`==` và `equals` đều so sánh chữ.”** `==` so sánh tham chiếu; `equals` so sánh nội dung. Pool có thể làm một số cặp `==` trả về true, nhưng không phải quy tắc so sánh chuỗi.
5. **“Tất cả phương thức `String` luôn cấp phát một đối tượng mới.”** Không chắc. Nếu không thay đổi kết quả, API hoặc JVM có thể trả về cùng tham chiếu hoặc dùng lại đối tượng đã có; bất biến chỉ đòi hỏi không sửa chuỗi gốc.
6. **“Có thể sửa `String` bằng cách thay đổi mảng truyền vào constructor.”** Không: thay đổi mảng bên ngoài sau khi tạo `String` không thay đổi nội dung chuỗi.
7. **“Builder và String giống nhau.”** Không: `StringBuilder` mutable, có thể bị sửa qua cùng một tham chiếu; `String` immutable, các giá trị lấy từ builder vẫn giữ nguyên.
8. **“Vì nội dung String thread-safe nên gán lại biến chung cũng thread-safe.”** Sai: tính bất biến không thay cho các quy tắc về đồng bộ và visibility của Java Memory Model.

## 10. Tự kiểm tra kiến thức

Xét đoạn mã sau trong thân một phương thức Java:

```java
String a = "cat";
String b = a;

a.replace('c', 'b');
a = a + "s";

System.out.println(a);
System.out.println(b);
```

Kết quả là:

```text
cats
cat
```

Giải thích: `replace` không thay đổi `a` vì kết quả trả về bị bỏ qua. Phép `a + "s"` tạo giá trị `"cats"` và `a` được gán lại. `b` vẫn tham chiếu giá trị `"cat"` ban đầu.

Thử sửa dòng thứ ba thành `a = a.replace('c', 'b');`. Khi đó `a` cuối cùng là `"bats"`, còn `b` vẫn là `"cat"`.

**Ghi nhớ:** Tính bất biến nói về **nội dung của một đối tượng `String` sau khi được tạo**. Mỗi khi thấy chuỗi “thay đổi”, hãy hỏi: *đã sửa đối tượng ban đầu, hay chỉ tạo/nhận giá trị khác rồi gán lại biến?* Với `String` trong Java, đó là trường hợp thứ hai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-immutability-002">Phân biệt final của biến với bất biến của đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `final` của biến với tính bất biến của đối tượng trong Java

Một nhầm lẫn rất phổ biến khi học Java là cho rằng thêm `final` vào biến sẽ khiến **đối tượng** mà biến tham chiếu đến trở nên bất biến. Trên thực tế, `final` của **biến** và *immutability* của **đối tượng** kiểm soát hai việc khác nhau:

- **`final` của biến**: sau khi biến được gán giá trị hợp lệ, bạn không thể gán lại biến đó. Với biến tham chiếu, điều này cố định **tham chiếu** được lưu trong biến.
- **Đối tượng immutable (bất biến)**: trạng thái quan sát được của chính đối tượng không thay đổi sau khi tạo, bất kể có bao nhiêu biến cùng tham chiếu đến nó.

Hãy ghi nhớ câu ngắn: **`final` giữ ổn định giá trị của biến; immutable giữ ổn định trạng thái của đối tượng.**

## 1. Hai tầng cần phân biệt: biến và đối tượng

Xét lệnh:

```java
final StringBuilder builder = new StringBuilder("Java");
```

Có hai thành phần:

1. **Biến tham chiếu `builder`** chứa một tham chiếu đến đối tượng `StringBuilder`. Từ khóa `final` cấm gán một tham chiếu khác vào biến này.
2. **Đối tượng `StringBuilder`** tồn tại riêng. Nó là loại đối tượng *mutable*: phương thức `append()`, `insert()`, `delete()`… có thể thay đổi nội dung.

Vì vậy:

```java
builder.append(" 17"); // Hợp lệ: đối tượng đang được tham chiếu thay đổi.
// builder = new StringBuilder("Kotlin"); // Lỗi biên dịch: không thể gán lại biến final.
```

Trái lại:

```java
String language = "Java"; // Không khai báo final.
language = language.concat(" 17"); // Hợp lệ: biến trỏ đến kết quả mới.
```

Đối tượng `String` có nội dung `"Java"` vẫn **bất biến**, dù biến `language` không có `final` và có thể được gán lại.

| Trường hợp | Có thể gán lại biến? | Có thể sửa nội dung đối tượng qua API của nó? |
| --- | --- | --- |
| `int x = 1` | Có | Không có đối tượng: `int` là primitive. |
| `final int x = 1` | Không | Không có đối tượng: `int` là primitive. |
| `String s = "A"` | Có | Không, vì `String` bất biến. |
| `final String s = "A"` | Không | Không, vì `String` bất biến. |
| `StringBuilder b = new StringBuilder("A")` | Có | Có, bằng `append()`, `setCharAt()`… |
| `final StringBuilder b = new StringBuilder("A")` | Không | Có, tương tự trường hợp trên. |
| `final int[] a = {1, 2}` | Không thể cho `a` trỏ tới mảng khác | Có, ví dụ `a[0] = 9`. |

Điểm cần chú ý: dấu `=` trong `s = "B"` thay đổi **giá trị biến `s`**; lời gọi `b.append("B")` thay đổi **đối tượng mà `b` đang tham chiếu**. Đó là hai thao tác có bản chất khác nhau.

## 2. `final` trên kiểu nguyên thủy (primitive)

Với `int`, `long`, `double`, `boolean`, `char`… biến giữ trực tiếp giá trị primitive. Khi khai báo `final`, biến không thể nhận giá trị mới:

```java
final int maxRetries = 3;
System.out.println(maxRetries); // 3

// maxRetries = 4; // Lỗi biên dịch.
// maxRetries++;    // Lỗi biên dịch: phép tăng cũng phải gán giá trị mới.
```

Một biến `final` chỉ được **gán một lần theo từng đường chạy hợp lệ**. Nó có thể được khai báo trước rồi gán sau, miễn trình biên dịch chứng minh việc gán diễn ra đúng một lần:

```java
final int port;
boolean isProduction = true;

if (isProduction) {
    port = 443;
} else {
    port = 8080;
}

System.out.println(port); // 443
```

Đây là ví dụ đoạn mã đặt trong phương thức; kết quả phụ thuộc giá trị `isProduction` đã đặt là `true`. Các trường `final` trong lớp cũng có quy tắc khởi tạo riêng: instance field thường được khởi tạo tại nơi khai báo hoặc trong constructor của mọi đường khởi tạo; `static final` được khởi tạo theo quy tắc trường tĩnh. Không nên diễn đạt `final` là “luôn là compile-time constant”: chỉ một số biểu thức primitive/`String` `final` thỏa điều kiện *constant variable* của Java Language Specification.

## 3. `final` trên biến tham chiếu: không đổi đích trỏ, vẫn có thể đổi trạng thái

Tham chiếu `final` không thể trỏ sang đối tượng mới, nhưng đối tượng hiện tại vẫn có thể sửa được nếu loại của nó cho phép.

### Mảng

```java
final int[] scores = {10, 20};
scores[0] = 99;            // Hợp lệ.
System.out.println(scores[0]); // 99

// scores = new int[] {1, 2}; // Lỗi biên dịch: gán lại biến scores.
```

Bản thân **mảng Java là mutable**: `final` trên biến không đóng băng các phần tử.

### Collection và đối tượng tùy biến

```java
final java.util.List<String> names = new java.util.ArrayList<>();
names.add("An");           // Hợp lệ: sửa trạng thái ArrayList.
names.add("Binh");
System.out.println(names); // [An, Binh]

// names = new java.util.ArrayList<>(); // Lỗi biên dịch.
```

Nếu hai biến cùng trỏ đến một đối tượng mutable, việc sửa qua biến này sẽ được quan sát qua biến kia:

```java
final StringBuilder original = new StringBuilder("A");
StringBuilder alias = original;

alias.append("B");
System.out.println(original); // AB
```

`final` **không loại bỏ aliasing** (nhiều tham chiếu tới cùng một đối tượng) và không ngăn thay đổi trạng thái thông qua alias.

## 4. Đối tượng immutable: có thể đổi tham chiếu, không thể sửa giá trị cũ

`String` là ví dụ điển hình của một lớp có hợp đồng bất biến. Không có thao tác công khai kiểu `append()` hoặc `setCharAt()` thay đổi trực tiếp nội dung của một instance `String`. Các lời gọi như `concat()`, `replace()` hoặc `toUpperCase()` trả về kết quả mới về **giá trị**; chuỗi ban đầu vẫn giữ nguyên. Cài đặt có thể trả về chính instance cũ khi kết quả không thay đổi, nên “immutable” không đồng nghĩa “mọi thao tác đều cấp phát instance mới”.

```java
String title = "Java";
String oldTitle = title;

title = title + " 17";

System.out.println(title);    // Java 17
System.out.println(oldTitle); // Java
```

Ở đây, `title` được gán sang kết quả `"Java 17"`, còn `oldTitle` vẫn tham chiếu tới đối tượng `"Java"`. Không có thao tác nào “sửa chuỗi Java thành Java 17”.

Nếu thêm `final` vào `title`:

```java
final String title = "Java";
String another = title.concat(" 17"); // Hợp lệ, title vẫn là "Java".
// title = another; // Lỗi biên dịch vì title là final.
```

Tính bất biến vẫn như cũ; điểm mới duy nhất là không thể gán lại biến `title`.

## 5. Ví dụ Java 17+ chạy độc lập: đối chiếu cả bốn tình huống

Lưu mã sau vào tệp `FinalVsImmutableDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class FinalVsImmutableDemo {
    public static void main(String[] args) {
        // (1) final + primitive: không thể gán lại biến.
        final int limit = 3;
        System.out.println("limit = " + limit);

        // (2) final + mutable object: không gán lại được tham chiếu,
        // nhưng nội dung đối tượng vẫn thay đổi được.
        final StringBuilder buffer = new StringBuilder("Ja");
        buffer.append("va");
        System.out.println("buffer = " + buffer);

        final int[] scores = {1, 2};
        scores[0] = 9;
        System.out.println("scores[0] = " + scores[0]);

        final List<String> names = new ArrayList<>(List.of("An"));
        names.add("Binh");
        System.out.println("names = " + names);

        // (3) Tham chiếu KHÔNG final + immutable String:
        // biến đổi đích trỏ, đối tượng chuỗi cũ không đổi.
        String first = "Java";
        String oldFirst = first;
        first = first.concat(" 17");
        System.out.println("first = " + first);
        System.out.println("oldFirst = " + oldFirst);

        // (4) final + immutable String: cả hai bảo đảm cùng tồn tại.
        final String greeting = "Hi";
        String newGreeting = greeting.concat("!");
        System.out.println("greeting = " + greeting);
        System.out.println("newGreeting = " + newGreeting);
    }
}
```

Biên dịch và chạy:

```bash
javac FinalVsImmutableDemo.java
java FinalVsImmutableDemo
```

Kết quả chính xác:

```text
limit = 3
buffer = Java
scores[0] = 9
names = [An, Binh]
first = Java 17
oldFirst = Java
greeting = Hi
newGreeting = Hi!
```

**Diễn giải:** những dòng cập nhật `StringBuilder`, mảng và `ArrayList` thay đổi nội dung đối tượng dù biến là `final`; phép nối `String` không thay đổi instance cũ dù biến `first` có thể được gán lại. Nếu bỏ dấu chú thích ở các phép gán lại biến `final` trong các ví dụ trước, lỗi xảy ra **lúc biên dịch**, không phải lúc chạy.

## 6. `final` ở những vị trí khác không có cùng ý nghĩa với immutable

### Tham số `final`

```java
static void addTag(final StringBuilder target) {
    target.append("-ok"); // Hợp lệ: sửa đối tượng.
    // target = new StringBuilder(); // Lỗi biên dịch: gán lại tham số.
}
```

`final` cấm thay đổi **biến tham số bên trong phương thức**. Java vẫn truyền **giá trị của tham chiếu** vào tham số; caller và callee có thể trỏ tới cùng đối tượng mutable. `final` không biến lời gọi thành deep copy hay chặn cập nhật đối tượng.

### Trường `final` trong lớp

```java
class MessageBuffer {
    private final StringBuilder content = new StringBuilder();

    void write(String text) {
        content.append(text); // Hợp lệ.
    }

    String read() {
        return content.toString();
    }
}
```

Trường `content` không được gán lại sau khởi tạo, nhưng trạng thái đối tượng `StringBuilder` vẫn thay đổi. Vì thế **“mọi field đều `final`” không chứng minh cả lớp là immutable**.

### `final class`

```java
final class MutableCounter {
    private int value;

    void increment() {
        value++; // Vẫn sửa được state.
    }

    int get() {
        return value;
    }
}
```

`final class` cấm kế thừa, không cấm thay đổi trạng thái. Một thiết kế bất biến thường dùng `final class` để tránh lớp con phá vỡ hợp đồng, nhưng chỉ riêng từ khóa này chưa đủ để tạo tính bất biến. Tương tự, `final` trên **phương thức** cấm override phương thức đó chứ không làm phương thức thành read-only.

### Biến *effectively final*

Biến local không viết `final` nhưng được gán một lần và không bị gán lại có thể được trình biên dịch coi là *effectively final*, cho phép lambda/lớp nội bộ truy cập:

```java
String prefix = "Hello, ";
Runnable task = () -> System.out.println(prefix + "Java");
task.run(); // Hello, Java

// prefix = "Hi, "; // Nếu thêm lệnh gán lại này,
//                   // lambda ở trên không còn hợp lệ để biên dịch.
```

*Effectively final* là quy tắc về **giá trị của biến được capture**, không phải tính bất biến của đối tượng mà biến tham chiếu.

## 7. Cần gì để tạo một đối tượng thực sự bất biến?

Một lớp tự viết chỉ nên được gọi là immutable khi **trạng thái quan sát được sau khởi tạo không thể bị thay đổi** thông qua các đường truy cập mà lớp cung cấp, kể cả khi đối tượng được chia sẻ. Các nguyên tắc phổ biến:

1. Khởi tạo đầy đủ giá trị trong constructor hoặc phương thức tạo; không mở setter làm thay đổi trạng thái.
2. Dùng `private final` cho các trường trạng thái khi thích hợp: chặn thay thế giá trị trường sau khởi tạo, nhưng phải kiểm tra sâu hơn nếu trường trỏ tới object mutable.
3. Không làm rò rỉ tham chiếu tới bộ dữ liệu mutable nội bộ. **Sao chép phòng vệ** (*defensive copy*) khi nhận dữ liệu mutable và khi trả dữ liệu mutable.
4. Nếu trường chứa collection, xem xét tính mutable của **bản thân collection lẫn từng phần tử**; sao chép một danh sách không tự làm từng phần tử bất biến.
5. Ngăn lớp con làm suy yếu hợp đồng bất biến, chẳng hạn dùng `final class` hoặc một thiết kế kế thừa có kiểm soát; không để `this` thoát ra khi chưa khởi tạo xong.

Ví dụ thiết kế snapshot bất biến với các phần tử `String` (vốn bất biến). Tạo tệp `ImmutableRosterDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class ImmutableRosterDemo {
    static final class Roster {
        private final List<String> members;

        Roster(List<String> source) {
            this.members = List.copyOf(source);
        }

        List<String> members() {
            return members;
        }
    }

    public static void main(String[] args) {
        List<String> input = new ArrayList<>(List.of("An"));
        Roster roster = new Roster(input);

        input.add("Binh"); // Sửa nguồn sau khi tạo roster.
        System.out.println("input = " + input);
        System.out.println("roster = " + roster.members());

        try {
            roster.members().add("Chi");
        } catch (UnsupportedOperationException ex) {
            System.out.println("roster members cannot be modified");
        }

        System.out.println("roster after attempt = " + roster.members());
    }
}
```

Lệnh chạy:

```bash
javac ImmutableRosterDemo.java
java ImmutableRosterDemo
```

Kết quả:

```text
input = [An, Binh]
roster = [An]
roster members cannot be modified
roster after attempt = [An]
```

Tại sao `Roster` đạt tính bất biến trong ví dụ này?

- Trường `members` là `private final`: không thể gán lại một list khác sau khởi tạo.
- `List.copyOf(source)` tạo một danh sách không hỗ trợ chỉnh sửa cấu trúc, độc lập với thay đổi sau đó của `ArrayList` nguồn trong ví dụ. Nó không cho thêm, xóa hoặc thay thế phần tử thông qua API của list.
- Từng phần tử là `String` vốn bất biến, nên ở đây không có object mutable bên trong có thể bị thay đổi thông qua alias.
- Lớp `Roster` là `final` và không cung cấp setter hoặc đường truy cập nội bộ có thể ghi.

**Giới hạn quan trọng:** `List.copyOf` là sao chép **nông** (*shallow*) theo phần tử; nó không sao chép sâu các đối tượng đang nằm trong list. Ví dụ nếu sử dụng `List<StringBuilder>`, một bên giữ `StringBuilder` cũ vẫn có thể `append()` để thay đổi nội dung phần tử mà danh sách bản sao tham chiếu. Muốn bất biến sâu phải dùng các phần tử bất biến hoặc tạo bản sao phù hợp của những phần tử mutable. `List.copyOf` cũng không chấp nhận phần tử `null`.

### Phân biệt bản sao và view không cho sửa

Hai khái niệm dễ bị nhầm:

```java
List<String> source = new ArrayList<>(List.of("A"));
List<String> view = Collections.unmodifiableList(source);
List<String> snapshot = List.copyOf(source);

source.add("B");
System.out.println(view);     // [A, B]
System.out.println(snapshot); // [A]
```

Đoạn mã trên là **fragment** cần đặt trong phương thức, với các import `java.util.ArrayList`, `java.util.Collections` và `java.util.List`. `Collections.unmodifiableList(source)` tạo *view* không cho sửa **thông qua view**, nhưng nguồn `source` vẫn có thể sửa và view sẽ phản ánh thay đổi. Ngược lại, `List.copyOf(source)` cho danh sách snapshot không bị ảnh hưởng bởi các thay đổi cấu trúc về sau của `source` trong ví dụ trên.

Khi cần bảo vệ một mảng, có thể áp dụng cả hai hướng sao chép:

```java
final class SafeBytes {
    private final byte[] data;

    SafeBytes(byte[] input) {
        this.data = input.clone(); // Không giữ mảng mutable của caller.
    }

    byte[] copyData() {
        return data.clone();       // Không lộ mảng nội bộ để caller sửa.
    }
}
```

Nếu chỉ dùng `private final byte[] data = input;` mà giữ trực tiếp mảng ngoài, caller vẫn có thể sửa `input[0]` và làm thay đổi trạng thái đang được quan sát của `SafeBytes`.

## 8. Tác động đến chia sẻ và đa luồng

- **Biến tham chiếu `final` không khiến đối tượng tự an toàn đa luồng.** Chẳng hạn, hai luồng cùng gọi `append()` trên cùng một `StringBuilder` vẫn có vấn đề truy cập đồng thời; `final StringBuilder shared` không giải quyết race condition đó.
- **Đối tượng immutable có thể được nhiều luồng đọc chung một cách thuận tiện.** Ví dụ, sau khi tham chiếu được công bố đúng cách giữa các luồng, nhiều luồng có thể dùng một đối tượng `String` hoặc `Roster` bất biến mà không cần khóa chỉ để ngăn việc sửa *nội dung*.
- **Gán lại một biến chứa tham chiếu đến các đối tượng immutable vẫn là thay đổi trạng thái của nơi chứa biến.** Nếu nhiều luồng thay đổi một trường `String status`, còn cần xem xét cơ chế công bố và đồng bộ (`volatile`, `synchronized`, atomic reference…) tùy thao tác.
- Các trường `final` có một số đảm bảo về mô hình bộ nhớ Java khi đối tượng được xây dựng đúng, nhưng **không** có nghĩa mọi object reachable từ trường `final` cũng bất biến hoặc mọi thao tác sử dụng chúng đều an toàn đa luồng.

## 9. Những ngộ nhận cần tránh

| Nhận định | Kết luận |
| --- | --- |
| “Đối tượng gắn với biến `final` không thể sửa.” | **Sai.** `final` chỉ cấm gán lại biến; `ArrayList`, mảng, `StringBuilder` vẫn mutable. |
| “Biến không `final` thì đối tượng chắc chắn mutable.” | **Sai.** `String s = "A"` không `final`, nhưng đối tượng `String` vẫn immutable. |
| “Mọi field là `final` nên toàn bộ class immutable.” | **Sai.** Phần tử mảng, collection, và object mutable được tham chiếu vẫn có thể đổi. |
| “`final class` tức là immutable class.” | **Sai.** Nó chỉ cấm tạo subclass; lớp final vẫn có thể cung cấp setter. |
| “`static final List` là danh sách không thay đổi được.” | **Sai.** `static` và `final` ràng buộc trường, không chặn `add/remove` trên list mutable. |
| “`record` luôn bất biến sâu.” | **Sai.** Component của record không thể gán lại, nhưng component có kiểu mảng/list mutable vẫn có thể chứa trạng thái thay đổi; cần thiết kế sao chép phòng vệ. |
| “`Collections.unmodifiableList` tạo snapshot bất biến.” | **Sai.** Đây là view; sửa backing list sẽ xuất hiện trong view. |
| “Đối tượng immutable đồng nghĩa biến trỏ tới nó không bao giờ đổi.” | **Sai.** Một biến không `final` có thể được gán sang đối tượng immutable khác. |

## 10. Tự kiểm tra

**Câu 1.** Đoạn nào trong mã sau gây lỗi biên dịch?

```java
final StringBuilder b = new StringBuilder("X");
b.append("Y");
// b = new StringBuilder("Z");
```

Trả lời: dòng gán lại `b` (đang được chú thích) gây lỗi biên dịch nếu bỏ chú thích; `append("Y")` hợp lệ và đối tượng trở thành `"XY"`.

**Câu 2.** `name` không được khai báo `final` thì chuỗi có còn bất biến không?

```java
String name = "An";
String remembered = name;
name = "Binh";
System.out.println(remembered);
```

Kết quả là `An`. Hai đối tượng `String` giữ nguyên giá trị; chỉ biến `name` nhận tham chiếu mới.

**Câu 3.** Có cần `final` trên biến để dùng `String` làm khóa `HashMap` không?

Không. Tính bất biến của **chính đối tượng `String`** đã giữ ổn định nội dung dùng cho `equals()` và `hashCode()`; biến chứa tham chiếu khóa không nhất thiết là `final`. `final` có thể giúp chương trình dễ đọc hơn khi muốn đảm bảo biến không bị gán lại, nhưng đó là bảo đảm bổ sung ở cấp biến.

**Điểm cốt lõi:** khi đọc code, hãy luôn hỏi hai câu riêng: **(1) Biến này có được gán lại không? (2) Đối tượng mà nó trỏ tới có thể thay đổi trạng thái không?** Chỉ từ `final` trên biến, bạn trả lời được câu thứ nhất; để trả lời câu thứ hai, phải xem hợp đồng và thiết kế của loại đối tượng.

## Tài liệu tham khảo

- [Java Language Specification §4.12.4 — `final` Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.12.4) — ràng buộc phép gán và khái niệm effectively final.
- [Java SE 21 API — `String`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html) — tính bất biến và API thao tác chuỗi.
- [Java SE 21 API — `List.copyOf`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)) — snapshot không thể sửa cấu trúc và tính chất shallow của phần tử.
- [Java SE 21 API — `Collections.unmodifiableList`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)) — view không cho sửa và liên hệ với backing list.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-immutability-003">Phương thức biến đổi trả kết quả thay vì sửa chuỗi nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức biến đổi trả kết quả thay vì sửa chuỗi nguồn trong Java

## 1. Mục tiêu và ý tưởng cốt lõi

Trong Java, `String` là **immutable** (*bất biến*): nội dung của một đối tượng `String` không thể bị sửa sau khi tạo. Khi cần viết hoa, viết thường, bỏ khoảng trắng, thay văn bản, nối hoặc cắt chuỗi, các phương thức tương ứng **trả về giá trị kết quả** để người gọi sử dụng; chúng không sửa nội dung đối tượng nguồn.

Sau bài này, bạn cần giải thích và thực hành được:

1. Phân biệt **biến tham chiếu**, **đối tượng String** và **giá trị trả về** của phương thức.
2. Phát hiện lỗi gọi `strip()`, `replace()`, `toUpperCase()`… nhưng bỏ kết quả.
3. Biết khi nào **gán lại biến**, khi nào **giữ bản gốc và tạo biến kết quả**, khi nào **trả kết quả** từ phương thức của mình.
4. Áp dụng *method chaining* đúng thứ tự.
5. Đối chiếu `String` với `StringBuilder` và xử lý các trường hợp `null`, regex, Unicode, `Locale`, hiệu năng.

Ví dụ trong bài dùng Java 17+; cần biết biến, phép gán, đối tượng, lời gọi phương thức và kiểu giá trị trả về.

## 2. Bất biến không có nghĩa biến không thể gán lại

Xem đoạn mã:

~~~java
String source = "java";
String upper = source.toUpperCase();

System.out.println(source); // java
System.out.println(upper);  // JAVA
~~~

Đối tượng biểu diễn `"java"` **không đổi**. `toUpperCase()` trả kết quả `"JAVA"` và biến `upper` nhận kết quả này. Hai biến chứa các **giá trị** khác nhau, trong khi việc đọc lại `source` vẫn nhận `"java"`.

Ba khái niệm dễ nhầm:

- **Đối tượng String:** mang nội dung bất biến.
- **Biến tham chiếu:** có thể trỏ sang một đối tượng chuỗi khác bằng phép gán, trừ khi biến được khai báo `final`.
- **Giá trị trả về:** kết quả của thao tác. Nếu bỏ qua, hiệu ứng biến đổi mong đợi thường không xuất hiện ở biến nguồn.

Ví dụ minh họa:

~~~text
Ban đầu:
source ───────► "java"

source.toUpperCase() trả về:
                 "JAVA"

Không gán kết quả:
source ───────► "java"

Gán source = source.toUpperCase():
source ───────► "JAVA"
               (đối tượng "java" gốc không bị sửa)
~~~

**Điểm tinh tế:** trả kết quả `String` **không đồng nghĩa luôn tạo đối tượng mới**. Khi giá trị không cần thay đổi, một số phương thức có thể trả lại cùng tham chiếu. Tính bất biến bảo đảm **nội dung nguồn không đổi**; không bảo đảm số lần cấp phát. Vì thế, dùng `equals()` khi cần kiểm tra hai chuỗi có cùng nội dung; `==` dùng để so sánh danh tính tham chiếu, không phù hợp với phần lớn phép so sánh văn bản.

## 3. Lỗi điển hình: gọi phương thức rồi bỏ giá trị trả về

~~~java
String text = "  java string  ";
text.strip();
text.toUpperCase();
text.replace("java", "JAVA");

System.out.println("[" + text + "]");
// [  java string  ]
~~~

Đoạn mã biên dịch bình thường, không ném lỗi, nhưng `text` không đổi. Mỗi phép biến đổi đã trả về kết quả **không được sử dụng**.

Sửa bằng cách nhận kết quả:

~~~java
String text = "  java string  ";
text = text.strip();
text = text.toUpperCase();
text = text.replace("JAVA", "Java");

System.out.println("[" + text + "]");
// [Java STRING]
~~~

Ở đây `text = ...` cập nhật **tham chiếu lưu trong biến**, không làm sửa một đối tượng `String` tại chỗ.

Nếu muốn giữ dữ liệu nhập nguyên bản:

~~~java
String raw = "  java string  ";
String display = raw.strip().toUpperCase();

System.out.println("raw=[" + raw + "]");
// raw=[  java string  ]
System.out.println("display=[" + display + "]");
// display=[JAVA STRING]
~~~

Đặt tên `raw`/`display` giúp phân biệt bản gốc và bản xử lý. Việc giữ nguồn đặc biệt hữu ích khi cần giải thích, đối chiếu hoặc kiểm thử quy tắc biến đổi.

## 4. Các phương thức biến đổi trả về String

| Phương thức | Mục đích | Ví dụ kết quả |
| --- | --- | --- |
| `toUpperCase()`, `toLowerCase()` | Đổi chữ hoa/thường | `"Java".toUpperCase()` → `"JAVA"` |
| `strip()`, `stripLeading()`, `stripTrailing()` | Bỏ whitespace ở hai đầu / đầu / cuối | `" a ".strip()` → `"a"` |
| `trim()` | Bỏ ký tự đầu/cuối có mã `<= U+0020` theo đặc tả | `" a ".trim()` → `"a"` |
| `replace(old, new)` | Thay **literal** | `"a-b".replace("-", ":")` → `"a:b"` |
| `replaceAll(regex, replacement)` | Thay tất cả đoạn khớp **regex** | `"a1b2".replaceAll("\\d", "#")` → `"a#b#"` |
| `replaceFirst(regex, replacement)` | Thay phần khớp regex đầu tiên | `"a1b2".replaceFirst("\\d", "#")` → `"a#b2"` |
| `substring(begin, end)` | Trích đoạn, cận cuối không lấy | `"Java".substring(1, 3)` → `"av"` |
| `concat(other)` và phép `+` | Nối chuỗi | `"Java".concat(" 17")` → `"Java 17"` |
| `repeat(count)` | Lặp nội dung | `"ab".repeat(2)` → `"abab"` |
| `formatted(args...)`, `String.format(...)` | Dựng chuỗi từ định dạng | `"%s-%d".formatted("A", 2)` → `"A-2"` |
| `indent(n)`, `stripIndent()` | Biến đổi cách thụt lề của văn bản đa dòng | Kết quả có thể khác cả khoảng trắng lẫn dấu xuống dòng |

**Không phải mọi phương thức được gọi trên String đều trả String.** Ví dụ:

~~~java
String data = "a,b,c";
int length = data.length();
boolean exists = data.contains("a");
String[] pieces = data.split(",");
~~~

`length()` trả `int`, `contains()` trả `boolean` và `split()` trả `String[]`. Tất cả đều **không sửa** `data` nhưng có kiểu trả về khác nhau. Các phương thức như `indexOf()`, `isBlank()`, `matches()` hoặc `codePoints()` cũng có kiểu trả về riêng. Hãy đọc chữ ký trong Javadoc trước khi viết code.

## 5. Ba cách nhận kết quả đúng

### 5.1. Gán lại khi không còn cần biến chứa bản cũ

~~~java
String name = "  alice  ";
name = name.strip();
name = name.replace("alice", "Alice");
// name giờ chứa "Alice"
~~~

Giá trị chuỗi ban đầu không bị sửa. Điều thay đổi là **giá trị tham chiếu của biến** `name`.

### 5.2. Dùng biến mới để giữ cả dữ liệu thô và đã xử lý

~~~java
String input = "  alice  ";
String normalized = input.strip().replace("alice", "Alice");

// input: "  alice  "
// normalized: "Alice"
~~~

Cách này hữu ích khi mỗi phiên bản có vai trò khác nhau. Không tự động bỏ dấu, đổi case hoặc cắt chuỗi nếu nghiệp vụ chưa quy định vì có thể mất thông tin.

### 5.3. Trả kết quả khi viết phương thức của mình

**Sai:**

~~~java
static String normalizeWrong(String input) {
    input.strip();
    input.toLowerCase(java.util.Locale.ROOT);
    return input; // Trả về nguồn, không phải kết quả đã xử lý
}
~~~

**Đúng:**

~~~java
static String normalizeCorrect(String input) {
    return input.strip().toLowerCase(java.util.Locale.ROOT);
}
~~~

Ở ví dụ đúng, tham số `input` phải khác `null`. Quy tắc xử lý `null` nên được viết rõ trong hợp đồng hàm.

## 6. Method chaining: kết quả trước là đầu vào của bước sau

~~~java
String result = "  java core  "
        .strip()
        .replace("java", "Java")
        .toUpperCase();

System.out.println(result); // JAVA CORE
~~~

Quá trình:

~~~text
"  java core  "
       │ strip()
       ▼
"java core"
       │ replace("java", "Java")
       ▼
"Java core"
       │ toUpperCase()
       ▼
"JAVA CORE"
~~~

**Thứ tự của phép biến đổi có thể làm thay đổi kết quả**:

~~~java
String text = "Java";

String first = text.toLowerCase().replace("java", "core");
String second = text.replace("java", "core").toLowerCase();

System.out.println(first);  // core
System.out.println(second); // java
~~~

`replace("java", "core")` phân biệt chữ hoa/chữ thường; khi áp dụng trực tiếp trên `"Java"` nó không thay gì. Khi đổi thành chữ thường trước rồi thay mới tìm thấy đoạn cần thay.

**Quy tắc chaining:**

- Lời gọi kế tiếp phải tương thích **kiểu trả về** của lời gọi trước: `text.strip().replace(...)` đúng, nhưng `text.length().strip()` sai vì `length()` trả `int`.
- Dùng biến trung gian khi cần debug từng bước hoặc pipeline quá dài.
- Lệnh cuối phải được sử dụng (gán, `return`, truyền làm đối số…) nếu kết quả xử lý là mục tiêu của chương trình.
- Chaining là cách diễn đạt xử lý tuần tự, **không phải** cách làm cho đối tượng `String` trở nên mutable.

## 7. Đối chiếu với StringBuilder: mutable thật sự

`StringBuilder` khác `String` ở chỗ nó **thay đổi trạng thái bên trong** khi gọi `append()`, `insert()`, `delete()` hoặc `replace()`.

~~~java
String text = "A";
text.concat("B");                // Giá trị trả về bị bỏ
System.out.println(text);        // A

StringBuilder builder = new StringBuilder("A");
builder.append("B");             // Chính builder thay đổi
System.out.println(builder);     // AB
~~~

Với `StringBuilder`, `append()` vừa thay builder, vừa trả lại tham chiếu đến builder để gọi tiếp. Với `String`, `concat()` trả về kết quả nối, còn giá trị nguồn giữ nguyên.

Hai cách sau đều tạo ra giá trị `"ab"`, nhưng cơ chế khác nhau:

~~~java
String s = "a";
s = s + "b"; // Gán lại biến s bằng kết quả String

StringBuilder b = new StringBuilder("a");
b.append("b"); // Sửa nội dung đối tượng b
~~~

**`final` khác immutable:**

~~~java
final String fixed = "a";
String changed = fixed.replace("a", "b"); // Hợp lệ
// fixed = changed; // Lỗi biên dịch: không được gán lại fixed

final StringBuilder editable = new StringBuilder("a");
editable.append("b"); // Hợp lệ: đối tượng builder vẫn mutable
~~~

`final` ngăn **gán lại một biến tham chiếu**; không tự làm cho đối tượng mà biến trỏ tới trở thành bất biến.

## 8. Trường hợp biên và lỗi thực tế

### 8.1. Rỗng, trắng và null

~~~java
String empty = "";
String blank = "   ";

System.out.println(empty.isEmpty()); // true
System.out.println(blank.isEmpty()); // false
System.out.println(blank.isBlank()); // true
System.out.println(blank.strip().isEmpty()); // true
~~~

`null` không phải chuỗi rỗng: `null.strip()` ném `NullPointerException`. Hai chính sách ví dụ:

~~~java
static String normalizeNullable(String text) {
    return text == null ? null : text.strip();
}

static String normalizeRequired(String text) {
    java.util.Objects.requireNonNull(text, "text");
    return text.strip();
}
~~~

Chọn một hợp đồng theo nhu cầu: trả `null`, chuyển dữ liệu thiếu thành `""` hoặc từ chối `null`. Đừng thay đổi ý nghĩa dữ liệu một cách ngầm định.

### 8.2. Không thay được thì kết quả vẫn bằng nội dung cũ

~~~java
String original = "Hello";
String after = original.replace("x", "y");
System.out.println(original.equals(after)); // true
~~~

Không tìm thấy `"x"` nên nội dung kết quả bằng nguồn. Điều này không chứng tỏ hai biến chắc chắn cùng một tham chiếu; quy tắc ổn định là **nguồn không đổi**, không phải **mỗi lần luôn có đối tượng mới**.

### 8.3. Replace literal khác replaceAll regex

~~~java
String path = "a.b.c";

String a = path.replace(".", "-");        // a-b-c
String b = path.replaceAll("\\.", "-");   // a-b-c
~~~

`replace(".", "-")` tìm dấu chấm literal. `replaceAll` nhận biểu thức chính quy; ký hiệu `.` trong regex đại diện cho hầu hết ký tự, nên muốn khớp dấu chấm thật phải dùng `\.`, được viết trong mã nguồn Java là `"\\."`.

Chú ý giá trị `replacement` của `replaceAll` còn có cú pháp cho `$` và `\`; khi muốn sử dụng chuỗi thay thế literal đến từ dữ liệu bên ngoài, dùng `java.util.regex.Matcher.quoteReplacement(replacement)` phù hợp.

### 8.4. Locale và chuyển chữ hoa/thường

`toLowerCase()` và `toUpperCase()` không có tham số sử dụng **locale mặc định**. Khi xử lý mã kỹ thuật cố định, truyền `Locale.ROOT` để không phụ thuộc locale người dùng:

~~~java
String key = "CONTENT-TYPE";
String lower = key.toLowerCase(java.util.Locale.ROOT);
System.out.println(lower); // content-type
~~~

Với ngôn ngữ tự nhiên, dùng locale phù hợp quy tắc nghiệp vụ. Một số chuyển đổi case có thể **thay đổi độ dài**, vì vậy không giả định một ký tự đầu vào luôn cho một `char` đầu ra.

### 8.5. Unicode khi cắt chuỗi

`length()` đếm **đơn vị UTF-16**, không phải số ký tự người dùng nhìn thấy:

~~~java
String value = "A😀B";
System.out.println(value.length()); // 4
System.out.println(value.codePointCount(0, value.length())); // 3
~~~

Một emoji có thể chiếm hai `char`. `substring()` cắt theo chỉ số UTF-16; nếu cắt giữa một surrogate pair, kết quả có thể không biểu diễn một ký tự Unicode hoàn chỉnh. Khi cần xử lý code points, sử dụng API như `codePoints()` và `offsetByCodePoints()`. Ký tự *grapheme* người dùng nhìn thấy còn có thể gồm nhiều code point. Bất biến **không** thay cho xử lý Unicode đúng.

### 8.6. Phương thức có thể ném ngoại lệ

`substring(begin, end)` có thể ném `StringIndexOutOfBoundsException` khi chỉ số sai; `repeat(n)` với `n < 0` ném `IllegalArgumentException`. Một phép biến đổi thất bại không làm sửa chuỗi nguồn, nhưng bạn vẫn phải xác minh đầu vào.

## 9. Chương trình hoàn chỉnh có thể biên dịch và chạy (Java 17+)

Tạo file **`ImmutableStringDemo.java`**:

~~~java
import java.util.Locale;

public class ImmutableStringDemo {
    public static void main(String[] args) {
        String raw = "  java-core  ";

        raw.strip();
        raw.toUpperCase(Locale.ROOT);
        System.out.println("raw=[" + raw + "]");

        String output = raw.strip()
                .replace("-", " ")
                .toUpperCase(Locale.ROOT);
        System.out.println("output=[" + output + "]");
        System.out.println("raw again=[" + raw + "]");

        String title = " hello ";
        title = title.strip();
        title = title.replace("hello", "Hello");
        System.out.println("title=[" + title + "]");

        String token = "a1b2";
        String redacted = token.replaceAll("\\d", "#");
        System.out.println("redacted=[" + redacted + "]");
        System.out.println("token=[" + token + "]");

        StringBuilder builder = new StringBuilder("A");
        builder.append("B");
        System.out.println("builder=" + builder);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file:

~~~bash
javac ImmutableStringDemo.java
java ImmutableStringDemo
~~~

**Stdout chính xác:**

~~~text
raw=[  java-core  ]
output=[JAVA CORE]
raw again=[  java-core  ]
title=[Hello]
redacted=[a#b#]
token=[a1b2]
builder=AB
~~~

Giải thích output:

1. Hai lời gọi trên `raw` không gán kết quả nên chuỗi nguồn giữ khoảng trắng đầu/cuối và chữ thường.
2. `output` nhận kết quả của `strip()` → `replace("-", " ")` → `toUpperCase(Locale.ROOT)`, thành `"JAVA CORE"`. Lời in `raw again` chứng minh nguồn vẫn giữ nguyên.
3. `title` được **gán lại** hai lần nên biến giữ bản cuối là `"Hello"`.
4. `replaceAll("\\d", "#")` thay các chữ số trong kết quả `redacted`, trong khi `token` vẫn `"a1b2"`.
5. `append("B")` thay nội dung `builder`, chứng minh đối tượng mutable hoạt động khác `String`.

Hãy tự thay đầu vào, dự đoán toàn bộ stdout rồi chạy để kiểm chứng. Sự nhất quán giữa dự đoán và chạy thật quan trọng hơn thuộc tên phương thức.

## 10. Ảnh hưởng đến hiệu năng và thiết kế

Bất biến có thể khiến việc xây dựng chuỗi qua nhiều thao tác tạo ra các giá trị trung gian; cấp phát và tối ưu cụ thể phụ thuộc phương thức và compiler/JVM. Không suy luận rằng **mọi** lời gọi trả về một đối tượng mới, hoặc rằng dùng `String` luôn chậm.

Ví dụ nối chuỗi trong vòng lặp:

~~~java
String result = "";
for (int i = 0; i < 1000; i++) {
    result += i;
}
~~~

Mã chạy đúng về logic nhưng phép nối tích lũy có thể tạo nhiều giá trị trung gian. Thay vào đó, thường thích hợp dùng:

~~~java
StringBuilder builder = new StringBuilder();
for (int i = 0; i < 1000; i++) {
    builder.append(i);
}
String result = builder.toString();
~~~

Nguyên tắc chọn công cụ: dùng `String` để **lưu và trao đổi giá trị văn bản**, dùng `StringBuilder` khi **xây dựng văn bản qua nhiều lần bổ sung**, rồi chuyển về `String` bằng `toString()`. Đo hiệu năng nếu đây là điểm nghẽn thực tế; phép nối `+` ngắn không cần chuyển sang builder một cách máy móc.

Vì một đối tượng `String` không thể bị sửa nội dung, việc cùng đọc một chuỗi qua nhiều tham chiếu trở nên an toàn về mặt nội dung. Nhưng **biến tham chiếu có thể gán lại** dùng chung giữa các luồng vẫn cần quy tắc công bố và đồng bộ thích hợp; immutable không tự giải quyết mọi vấn đề concurrency.

## 11. Bài tập và cách tự kiểm tra

### Bài tập 1 — Dự đoán rồi chạy

~~~java
String a = "Hello";
a.replace("H", "J");
String b = a.toLowerCase();
System.out.println(a);
System.out.println(b);
~~~

Kết quả: `Hello` và `hello`. Chữ `H` không thay vì kết quả `replace()` bị bỏ. `b` nhận kết quả viết thường nên có nội dung khác `a`.

### Bài tập 2 — Sửa lỗi

Viết hàm trả `"java core"` từ `"  Java CORE  "` bằng `strip()` và `toLowerCase(Locale.ROOT)`. Viết thử phiên bản **sai do bỏ giá trị trả về**, sau đó sửa bằng `return input.strip().toLowerCase(Locale.ROOT);`.

### Bài tập 3 — Thứ tự khác, kết quả khác

Cho `String x = "JAVA";`. So sánh:

~~~java
x.toLowerCase(java.util.Locale.ROOT).replace("java", "core");
x.replace("java", "core").toLowerCase(java.util.Locale.ROOT);
~~~

Kết quả lần lượt là `"core"` và `"java"`. Giải thích việc `replace()` phân biệt chữ hoa/chữ thường.

### Bài tập 4 — Hợp đồng null

Tạo hàm `normalize(String s)` nhận chuỗi có thể `null`. Chọn **một** quy tắc được công bố rõ: trả `null`, trả `""` hoặc ném ngoại lệ. Thử `null`, `""`, `" "`, `" A "`.

### Bài tập 5 — Giữ đầu vào nguyên bản

Với `String raw = "  Cà phê Đắk Lắk  ";`, tạo biến `display` bằng `strip()`. Chứng minh `raw` vẫn chứa khoảng trắng. Không tự ý xóa dấu hay chuyển chữ thường vì đó là thay đổi ý nghĩa văn bản ngoài yêu cầu.

### Bài tập 6 — Mutable và immutable

Dùng `StringBuilder` ghép các số `1`, `2`, `3` với dấu phẩy; gọi `toString()` để giữ bản hoàn chỉnh. Sau đó thêm `4` vào builder, in cả hai để chứng minh bản `String` vừa tạo vẫn không thay đổi.

### Checklist khi review code

- Lời gọi trên `String` trả về kiểu gì: `String`, mảng, số, boolean hay stream?
- Giá trị kết quả đã được **gán, return, truyền tiếp hoặc sử dụng** chưa?
- Có muốn giữ bản gốc không? Nếu có, đặt tên riêng cho bản xử lý.
- Có điều kiện nào phụ thuộc thứ tự các bước hoặc phân biệt chữ hoa/thường?
- Có yêu cầu xử lý `null`, chuỗi trắng, Unicode hay `Locale` đặc biệt?
- Phương thức `replaceAll()` đang nhận **regex** hay người viết nhầm với chuỗi literal?
- Biến `final` có bị hiểu sai là làm cho mọi đối tượng mutable trở nên bất biến?
- Khi nối nhiều lần, có cần `StringBuilder` không?

**Quy tắc nhớ lâu:**

> Phương thức biến đổi trên `String` không sửa chuỗi nguồn. Hãy sử dụng giá trị trả về. Gán lại biến là thay **tham chiếu** mà biến giữ; không phải thay nội dung của đối tượng `String` gốc.

Hoàn thành bài khi bạn dự đoán được giá trị biến trước/sau lời gọi, phát hiện được thao tác bị bỏ kết quả, viết được hàm biến đổi giữ nguồn và phân biệt rõ `String` với `StringBuilder`.

## 12. Tài liệu chuẩn để tra cứu

- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): chữ ký và hợp đồng `strip()`, `replace()`, `substring()`, `toLowerCase()`, `concat()`, `repeat()`, `formatted()`…
- [Java SE 17 — StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): các thao tác thay đổi nội dung builder và `toString()`.
- [Java SE 17 — Locale](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html): ý nghĩa locale khi xử lý văn bản.
- [Java SE 17 — Matcher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Matcher.html): quy tắc replacement của regex và `quoteReplacement()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-immutability-004">Vì sao bỏ qua giá trị trả về dễ làm mất kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao bỏ qua giá trị trả về dễ làm mất kết quả khi làm việc với `String`?

## 1. Vấn đề: gọi phương thức thành công nhưng chuỗi vẫn như cũ

Hãy quan sát đoạn mã sau (đặt bên trong một phương thức Java):

```java
String text = "  java  ";
text.trim();
System.out.println("[" + text + "]");
```

**Kết quả:**

```text
[  java  ]
```

Nhiều người mới học dự đoán sẽ nhận được `[java]` vì đã gọi `trim()`. Thực tế, lời gọi `text.trim()` **đã tính ra kết quả loại bỏ khoảng trắng**, nhưng chương trình không lưu, truyền đi hoặc sử dụng kết quả trả về ấy. Biến `text` vì vậy vẫn tham chiếu đến chuỗi ban đầu `"  java  "`.

Đây là hệ quả trực tiếp của **tính bất biến (immutability)** của `String`: một đối tượng `String` đã được tạo ra thì nội dung của chính đối tượng đó không bị các phương thức như `trim()`, `replace()` hoặc `toUpperCase()` chỉnh sửa tại chỗ.

Bài học trọng tâm: **Gọi một phương thức trả về `String` không đồng nghĩa với cập nhật biến đang giữ chuỗi. Muốn nhận kết quả biến đổi, phải sử dụng giá trị trả về.**

## 2. Giá trị kết quả có thể bị bỏ ở hai phía của một lời gọi

Lỗi không chỉ xuất hiện ở `text.strip();`. Hàm xử lý có thể trả đúng giá trị, nhưng **bên gọi** lại bỏ kết quả:

```java
public class ReturnValueBoundaryDemo {
    static String clean(String value) {
        return value.strip();       // Hàm trả kết quả đã xử lý
    }

    public static void main(String[] args) {
        String raw = "  Java  ";
        clean(raw);                 // Bỏ kết quả: raw không thay đổi
        String display = clean(raw);
        System.out.println("raw=[" + raw + "]");
        System.out.println("display=[" + display + "]");
    }
}
```

Đoạn mã chạy trên JDK 17+ và in `raw=[  Java  ]` rồi `display=[Java]`.

Nếu cần cập nhật biến, dùng `raw = clean(raw);`; nếu cần giữ bản gốc, dùng biến khác. Dùng trực tiếp cũng là dùng kết quả hợp lệ, ví dụ `System.out.println(clean(raw));`. Không phải **mọi** giá trị trả về đều buộc phải gán; câu hỏi là kết quả có đi đến nơi tiêu thụ đúng với mục tiêu hay không.

Khi review, lần theo đường đi **đầu vào → giá trị trả về → người tiêu thụ**. Một phương thức có thể tính đúng nhưng bị bỏ mất ở caller; một caller có thể gán đúng nhưng phương thức tiện ích bên trong lại trả về đầu vào cũ. Đây là hai vị trí lỗi cần kiểm tra độc lập.

## 3. Ví dụ Java 17+ hoàn chỉnh

Lưu toàn bộ nội dung dưới đây vào tệp **`StringReturnValueDemo.java`**:

```java
import java.util.Locale;

public class StringReturnValueDemo {
    public static void main(String[] args) {
        String original = "  java core  ";

        // Những kết quả này đều bị bỏ qua.
        original.trim();
        original.toUpperCase(Locale.ROOT);
        original.replace("core", "string");

        System.out.println("Sau khi bỏ qua: [" + original + "]");

        // Lấy kết quả trả về của từng phép biến đổi.
        String normalized = original.trim()
                .toUpperCase(Locale.ROOT)
                .replace("CORE", "STRING");

        System.out.println("Kết quả được dùng: [" + normalized + "]");
        System.out.println("Chuỗi gốc: [" + original + "]");

        String word = "Java";
        word.concat(" 21");
        System.out.println("concat bị bỏ qua: " + word);

        word = word.concat(" 21");
        System.out.println("concat đã gán lại: " + word);
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 StringReturnValueDemo.java
java StringReturnValueDemo
```

**Stdout chính xác:**

```text
Sau khi bỏ qua: [  java core  ]
Kết quả được dùng: [JAVA STRING]
Chuỗi gốc: [  java core  ]
concat bị bỏ qua: Java
concat đã gán lại: Java 21
```

Giải thích từng giai đoạn:

1. Ba lời gọi `trim()`, `toUpperCase(Locale.ROOT)` và `replace(...)` đầu tiên đều tính toán kết quả nhưng không thay đổi biến `original`, nên khi in vẫn còn hai dấu cách ở mỗi đầu và vẫn là `java core` viết thường.
2. Chuỗi phương thức `original.trim().toUpperCase(Locale.ROOT).replace("CORE", "STRING")` **truyền giá trị trả về** của mỗi bước sang bước tiếp theo. Kết quả cuối là `"JAVA STRING"`, được lưu vào `normalized`.
3. `original` vẫn là `"  java core  "` vì tạo và lưu một kết quả biến đổi khác không sửa đối tượng gốc.
4. `word.concat(" 21")` bị bỏ qua nên `word` vẫn là `"Java"`. Sau `word = word.concat(" 21")`, biến `word` tham chiếu đến giá trị `"Java 21"`.

Dùng `Locale.ROOT` trong ví dụ giúp thao tác chữ hoa có quy tắc độc lập với ngôn ngữ mặc định của máy chạy. Điều này có ích cho dữ liệu kỹ thuật, mã định danh hoặc kết quả cần nhất quán; khi xử lý văn bản cho một ngôn ngữ cụ thể, có thể cần `Locale` phù hợp.

## 4. Chaining: vì sao chuỗi phương thức vẫn có thể mất kết quả?

Đây là **method chaining**: mỗi phương thức nhận đối tượng `String` được trả về ở bước trước.

```java
String input = "  Hello Java  ";
String result = input.strip().replace(" ", "-").toLowerCase(java.util.Locale.ROOT);
System.out.println(result); // hello-java
```

Nếu chỉ viết:

```java
input.strip().replace(" ", "-").toLowerCase(java.util.Locale.ROOT);
```

thì kết quả cuối vẫn **bị bỏ đi**: chaining không tự động ghi ngược kết quả vào `input`. Các kết quả trung gian có thể đã được dùng làm đối tượng nhận ở bước tiếp theo, nhưng bước cuối cần một nơi tiêu thụ giá trị, chẳng hạn phép gán, `return`, đối số truyền sang phương thức khác hoặc phép so sánh.

Để trả kết quả từ một phương thức xử lý:

```java
static String normalize(String source) {
    return source.strip().toLowerCase(java.util.Locale.ROOT);
}
```

Và **phía gọi** cũng phải sử dụng giá trị trả về:

```java
String normalized = normalize("  JAVA  "); // "java"
```

Chỉ gọi `normalize("  JAVA  ");` như một câu lệnh độc lập cũng sẽ bỏ qua chuỗi đã xử lý.

## 5. Tình huống lỗi thực tế và cách sửa

### Tình huống 1: chuẩn hóa đầu vào trước khi kiểm tra

**Sai:**

```java
String role = "  ADMIN  ";
role.strip();
if (role.equals("ADMIN")) {
    System.out.println("Quyền quản trị");
}
```

Điều kiện không đúng vì `role` vẫn là `"  ADMIN  "`.

**Đúng:**

```java
String role = "  ADMIN  ";
role = role.strip();
if (role.equals("ADMIN")) {
    System.out.println("Quyền quản trị");
}
```

Chú ý: đây chỉ là minh họa kiểm tra chuỗi; cơ chế phân quyền thực tế cần xác thực và kiểm soát quyền đúng thiết kế, không dựa riêng vào một chuỗi đầu vào.

### Tình huống 2: thay ký tự nhưng dùng giá trị cũ

**Sai:**

```java
String slug = "hello java";
slug.replace(" ", "-");
System.out.println(slug); // hello java
```

**Đúng:**

```java
String slug = "hello java";
slug = slug.replace(" ", "-");
System.out.println(slug); // hello-java
```

### Tình huống 3: phương thức tiện ích tính xong rồi người gọi bỏ qua

```java
static String clean(String value) {
    return value.strip();
}
```

**Sai ở phía gọi:**

```java
String value = "  hello  ";
clean(value); // Giá trị được trả về bị bỏ qua
```

**Đúng:**

```java
String value = "  hello  ";
value = clean(value);
```

Lỗi có thể xảy ra ở cả hai chỗ: bên trong phương thức quên `return` kết quả thích hợp, hoặc phương thức đã trả đúng nhưng người gọi không sử dụng.

## 6. Cách tự phát hiện lỗi khi đọc mã

Khi gặp `String` có vẻ đã được xử lý nhưng đầu ra vẫn chưa thay đổi, hãy đi theo trình tự:

1. **Tìm vị trí gọi phương thức**: `trim`, `strip`, `replace`, `substring`, `toUpperCase`, `concat` hoặc một hàm tiện ích trả về `String`.
2. **Xem giá trị trả về đi đâu**: có được gán vào biến, trả về bằng `return`, truyền vào phương thức khác hay sử dụng trong biểu thức không?
3. **Xem chương trình đang đọc biến nào**: có đang in hoặc kiểm tra biến gốc, trong khi kết quả thực tế được lưu ở một biến khác?
4. **Kiểm tra API có sửa tại chỗ hay không**: `String` bất biến, nhưng các kiểu như `StringBuilder`, `StringBuffer` và nhiều collection có hành vi mutable.
5. **Thêm kiểm thử nhỏ**: khẳng định kết quả trước và sau xử lý để bắt lỗi logic sớm.

Ví dụ kiểm thử không cần thư viện ngoài, đặt trong `main`:

```java
String source = "  Java  ";
String result = source.strip();

if (!"Java".equals(result)) {
    throw new AssertionError("Giá trị đã xử lý không đúng");
}
if (!"  Java  ".equals(source)) {
    throw new AssertionError("Chuỗi gốc đã bị thay đổi");
}
```

Các khẳng định này kiểm tra cả hai vế quan trọng: **kết quả trả về đúng** và **giá trị gốc vẫn nguyên vẹn**.

## 7. Câu hỏi tự kiểm tra

**Câu 1.** Mã sau in gì và vì sao?

```java
String s = "abc";
s.replace("a", "x");
System.out.println(s);
```

**Đáp án:** `abc`; `replace` trả về kết quả nhưng lời gọi bị bỏ qua.

**Câu 2.** Sửa một dòng để in `XBC`:

```java
String s = "abc";
s.toUpperCase(java.util.Locale.ROOT);
System.out.println(s.replace("A", "X"));
```

**Đáp án:** Đổi dòng thứ hai thành `s = s.toUpperCase(java.util.Locale.ROOT);`. Khi đó `s` là `"ABC"`; phương thức `replace` trên dòng cuối trả `"XBC"` và `println` trực tiếp sử dụng kết quả này.

**Câu 3.** Gọi `s.strip()` rồi bỏ giá trị trả về có luôn tạo một đối tượng mới không?

**Đáp án:** Không. Một số thao tác có thể trả cùng đối tượng khi không cần thay đổi. Nhưng bất kể kết quả được trả về trỏ đến đối tượng nào, lệnh gọi cũng **không thay đổi nội dung của đối tượng `String` gốc**.

**Câu 4.** Tại sao `builder.append("x");` có thể đổi kết quả khi in `builder`, còn `s.concat("x");` không đổi kết quả khi in `s`?

**Đáp án:** `StringBuilder` được thiết kế để biến đổi nội dung của chính đối tượng; `String` được thiết kế bất biến và `concat` trả về giá trị chuỗi thay vì sửa đối tượng nhận.

**Ghi nhớ:** Khi muốn biến đổi nội dung `String`, hãy xác định rõ **ai sẽ nhận giá trị trả về**. Có thể gán lại, lưu sang biến mới, `return`, truyền làm đối số hoặc dùng ngay trong biểu thức. Một giá trị tính ra đúng nhưng không được sử dụng sẽ không tạo ra tác dụng mong muốn ở biến ban đầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-immutability-005">Gán lại tham chiếu không làm thay đổi chuỗi được chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

# Gán lại tham chiếu không làm thay đổi chuỗi được chia sẻ

Trong Java, nhiều biến có thể cùng tham chiếu đến một đối tượng `String`. Khi **gán lại một biến** sang chuỗi khác, chỉ giá trị tham chiếu lưu trong biến đó thay đổi; các biến còn lại vẫn tham chiếu đến đối tượng cũ. Đây là hệ quả của cơ chế biến tham chiếu trong Java. Với `String`, tính **bất biến (immutability)** còn bảo đảm các thao tác xử lý chuỗi không sửa trực tiếp nội dung đối tượng ban đầu.

Bài này tập trung phân biệt hai hành động hay bị nhầm:

- **Gán lại tham chiếu:** `b = "New"`, `b = a + "!"`, hoặc `b = a.replace(...)`; `b` nhận một tham chiếu được tính ra ở vế phải. Không có lệnh nào trong số đó tự gán lại `a`.
- **Thay đổi trạng thái đối tượng:** một đối tượng mutable có thể bị sửa thông qua bất kỳ tham chiếu nào trỏ tới nó. `String` không hỗ trợ kiểu sửa nội dung tại chỗ này bằng API thông thường.

## 1. Hai biến có thể cùng trỏ đến một `String`

Xét:

```java
String first = "Java";
String second = first;
```

Dòng đầu tạo một biến tham chiếu `first` trỏ đến chuỗi `"Java"`. Dòng sau **sao chép giá trị tham chiếu** từ `first` sang `second`. Vì vậy hai biến trỏ đến cùng một đối tượng `String`:

```text
first  ────┐
           ├────────> String "Java"
second ────┘
```

Ở thời điểm này, `first == second` trả về `true` vì toán tử `==` so sánh xem hai biến có giữ cùng một tham chiếu đối tượng hay không.

Quan trọng: «hai biến chia sẻ chuỗi» nghĩa là **hai tham chiếu đang cùng trỏ tới một đối tượng**. Nó không có nghĩa là hai biến được gắn chặt để mọi phép gán vào biến này tự động cập nhật biến kia.

## 2. Gán lại chỉ đổi tham chiếu của biến nhận phép gán

Thêm một dòng:

```java
second = "Kotlin";
```

Sau phép gán, sơ đồ trở thành:

```text
first  ─────────────> String "Java"

second ─────────────> String "Kotlin"
```

Không có thao tác nào sửa `String "Java"` thành `"Kotlin"`. Biến `first` vẫn giữ tham chiếu cũ. Biến `second` nhận tham chiếu đến một chuỗi khác; quan hệ cùng trỏ trước đó chấm dứt.

Trong Java, **biến** chứa một giá trị tham chiếu; phép gán `second = ...` thay thế giá trị lưu trong chính `second`. Nó không đi theo tham chiếu để ghi đè nội dung đối tượng mà `second` từng trỏ đến, và cũng không tìm những biến khác để thay đổi chúng.

### Ví dụ Java 17+ chạy độc lập

Tạo tệp `ReferenceReassignmentDemo.java`:

```java
public class ReferenceReassignmentDemo {
    public static void main(String[] args) {
        String first = "Java";
        String second = first;

        System.out.println("Before:");
        System.out.println("first = " + first);
        System.out.println("second = " + second);
        System.out.println("same object = " + (first == second));

        second = "Kotlin";

        System.out.println("After:");
        System.out.println("first = " + first);
        System.out.println("second = " + second);
        System.out.println("same object = " + (first == second));
    }
}
```

Biên dịch và chạy:

```bash
javac ReferenceReassignmentDemo.java
java ReferenceReassignmentDemo
```

Kết quả:

```text
Before:
first = Java
second = Java
same object = true
After:
first = Java
second = Kotlin
same object = false
```

Ở nửa đầu, `second = first` tạo thêm tên tham chiếu tới cùng `String`. Ở nửa sau, `second = "Kotlin"` chỉ đổi biến `second`. `first` vẫn in `Java`.

## 3. Ngay cả khi gọi phương thức xử lý chuỗi, đối tượng nguồn vẫn không bị sửa

Một số lệnh trông giống «sửa chuỗi»:

```java
String original = "cat";
String alias = original;

alias = alias.toUpperCase();
alias = alias.replace("A", "O");
```

Kết quả là `original` vẫn là `"cat"` còn `alias` là `"COT"`. Lý do gồm **hai bước riêng**:

1. `toUpperCase()` hoặc `replace(...)` trả về một giá trị `String`, không sửa nội dung `String` nguồn.
2. Phép gán `alias = ...` thay thế tham chiếu trong biến `alias`; `original` vẫn giữ tham chiếu cũ.

Tạo tệp `ImmutableOperationDemo.java`:

```java
public class ImmutableOperationDemo {
    public static void main(String[] args) {
        String original = "cat";
        String alias = original;

        alias = alias.toUpperCase();
        alias = alias.replace("A", "O");

        System.out.println(original);
        System.out.println(alias);
        System.out.println(original.equals(alias));

        String appended = original.concat("!");
        System.out.println(original);
        System.out.println(appended);
    }
}
```

```bash
javac ImmutableOperationDemo.java
java ImmutableOperationDemo
```

Kết quả:

```text
cat
COT
false
cat
cat!
```

Các phương thức như `concat`, `replace`, `toUpperCase` hoặc `substring` không cung cấp phép sửa nội dung tại chỗ cho `String`. Một số phương thức có thể trả lại chính đối tượng cũ nếu kết quả không đổi; điều đó không làm thay đổi tính bất biến của chuỗi.

Đừng nhầm «biến vẫn mang tên cũ» với «đối tượng cũ đã bị sửa». Sau `alias = alias.toUpperCase()`, chính **biến alias** được gán lại; đối tượng `"cat"` không trở thành `"CAT"`.

## 4. Bất biến và gán lại tham chiếu là hai khái niệm khác nhau

Cần tách bạch:

| Hành động | Điều thay đổi | Điều không bị thay đổi |
| --- | --- | --- |
| `String b = a;` | Biến `b` giữ tham chiếu đang có trong `a` | Nội dung đối tượng `String` |
| `b = "Other";` | Tham chiếu trong biến `b` | Tham chiếu trong `a`, đối tượng cũ |
| `b = b.concat("!");` | Giá trị tham chiếu trong `b` sau khi có kết quả chuỗi | Nội dung `String` ban đầu |
| `a = null;` | Tham chiếu trong biến `a` thành `null` | Biến `b` và đối tượng mà `b` đang giữ |
| `mutable.append("!");` với `StringBuilder` | Nội dung đối tượng mutable | Không có bảo đảm bất biến như `String` |

Điểm đáng chú ý: **việc gán lại một biến không tự thay đổi biến khác cũng đúng đối với tham chiếu tới đối tượng mutable**. Khác biệt của mutable là một phép gọi phương thức qua một tham chiếu có thể **sửa chính đối tượng** mà những tham chiếu còn lại cũng quan sát được.

### Phản ví dụ với `StringBuilder`

Tạo tệp `MutableContrastDemo.java`:

```java
public class MutableContrastDemo {
    public static void main(String[] args) {
        StringBuilder first = new StringBuilder("Java");
        StringBuilder second = first;

        second.append(" 21");
        System.out.println(first);
        System.out.println(second);
        System.out.println(first == second);

        second = new StringBuilder("Kotlin");
        System.out.println(first);
        System.out.println(second);
        System.out.println(first == second);
    }
}
```

```bash
javac MutableContrastDemo.java
java MutableContrastDemo
```

Kết quả:

```text
Java 21
Java 21
true
Java 21
Kotlin
false
```

`second.append(" 21")` sửa nội dung của **cùng một `StringBuilder`** mà `first` cũng trỏ đến, nên cả hai in `Java 21`. Trái lại, `second = new StringBuilder("Kotlin")` chỉ đổi tham chiếu của `second`; `first` vẫn trỏ đến builder cũ. Ví dụ cho thấy phải phân biệt **gọi phương thức sửa đối tượng** với **gán lại biến**.

## 5. Điều gì xảy ra khi truyền một `String` vào phương thức?

Java dùng **truyền tham trị (pass-by-value)** cho mọi tham số. Với kiểu tham chiếu như `String`, giá trị được sao chép là *tham chiếu tới đối tượng*, không phải bản thân đối tượng và cũng không phải biến gốc của người gọi.

Xem chương trình `ParameterReassignmentDemo.java`:

```java
public class ParameterReassignmentDemo {
    static void renameLocally(String name) {
        name = "Changed";
        System.out.println("Inside = " + name);
    }

    static String makeUpperCase(String name) {
        return name.toUpperCase();
    }

    public static void main(String[] args) {
        String shared = "Java";
        String alias = shared;

        renameLocally(shared);
        System.out.println("Caller = " + shared);
        System.out.println("Alias = " + alias);

        String updated = makeUpperCase(shared);
        System.out.println("Updated = " + updated);
        System.out.println("Still shared = " + shared);
    }
}
```

```bash
javac ParameterReassignmentDemo.java
java ParameterReassignmentDemo
```

Kết quả:

```text
Inside = Changed
Caller = Java
Alias = Java
Updated = JAVA
Still shared = Java
```

Khi gọi `renameLocally(shared)`, tham số `name` là **một biến cục bộ khác** nhận bản sao giá trị tham chiếu trong `shared`. Lệnh `name = "Changed"` chỉ gán lại biến cục bộ đó. Người gọi không bị đổi `shared` và `alias`.

Nếu muốn người gọi nhận giá trị chuỗi đã biến đổi, một cách dễ hiểu là **trả về kết quả và gán rõ ràng**:

```java
String shared = "Java";
String result = shared.toUpperCase();
// shared vẫn là "Java", result là "JAVA".
```

Hoặc nếu muốn chính biến `shared` đổi giá trị:

```java
shared = shared.toUpperCase();
// Biến shared bây giờ trỏ đến "JAVA".
```

Cả hai đều không sửa nội dung đối tượng `"Java"` trước đó.

## 6. Một ngoại lệ dễ hiểu nhầm: thay đổi đối tượng chứa trường `String`

Giả sử hai biến trỏ tới cùng một đối tượng lớp `Message` có trường `text`. Nếu một biến cập nhật **trường text của đối tượng Message**, biến kia sẽ nhìn thấy giá trị trường mới khi đọc lại. Điều này không hề mâu thuẫn với tính bất biến của `String`: **đối tượng Message mutable**, còn hai chuỗi cũ và mới vẫn là những giá trị `String` bất biến.

Tạo tệp `SharedHolderDemo.java`:

```java
public class SharedHolderDemo {
    static class Message {
        String text;

        Message(String text) {
            this.text = text;
        }
    }

    public static void main(String[] args) {
        Message first = new Message("Original");
        Message second = first;
        String snapshot = first.text;

        second.text = "Updated";

        System.out.println(first.text);
        System.out.println(second.text);
        System.out.println(snapshot);
    }
}
```

```bash
javac SharedHolderDemo.java
java SharedHolderDemo
```

Kết quả:

```text
Updated
Updated
Original
```

`first` và `second` cùng trỏ tới một `Message`, vì vậy việc gán `second.text = "Updated"` làm **trường của đối tượng Message** trỏ đến chuỗi mới. `snapshot` là biến `String` đã nhận tham chiếu đến `"Original"` trước đó nên vẫn không đổi.

Phân biệt hai câu:

- «Gán lại một **biến tham chiếu** `second`» — không tự thay đổi biến `first`.
- «Gán lại **trường của đối tượng dùng chung** `second.text`» — thay đổi trạng thái của đối tượng `Message` mà `first` cũng nhìn thấy.

Tương tự, phần tử mảng, phần tử danh sách mutable hay trạng thái trong một đối tượng dùng chung có thể được cập nhật. Tính bất biến của `String` không khiến **đối tượng chứa nó** trở thành bất biến.

## 7. `final` có liên quan thế nào?

`final String name = "Java";` ngăn **gán lại biến name** sau khi biến đã được khởi tạo:

```java
final String name = "Java";
// name = "Kotlin"; // Lỗi biên dịch: không thể gán lại biến final.
```

Nhưng `final` không phải nguyên nhân làm `String` bất biến: ngay cả `String ordinary = "Java";` không có `final` thì đối tượng `String` vẫn bất biến.

Ngược lại, một tham chiếu `final` tới một đối tượng mutable vẫn có thể dùng để thay đổi **nội dung đối tượng**:

```java
final StringBuilder builder = new StringBuilder("Java");
builder.append(" 21"); // Hợp lệ: không gán lại biến builder.
```

Do đó:

- `final` hạn chế gán lại **một biến/trường tham chiếu**.
- `String` bất biến vì hợp đồng của **kiểu đối tượng** không cho sửa nội dung qua API thông thường.
- Tính bất biến của `String` và tính cố định tham chiếu nhờ `final` có thể cùng tồn tại, nhưng không thay thế nhau.

## 8. Lưu ý về `==`, `equals` và string pool

Một câu hỏi khác với chủ đề gán lại là «hai biến còn cùng trỏ tới đối tượng nào?». Ta có thể kiểm tra bằng `==` khi thực sự cần tìm hiểu **identity**, nhưng với nghiệp vụ cần so sánh văn bản nên dùng `equals`.

```java
String a = "Java";
String b = a;
b = new String("Java");

System.out.println(a == b);       // false
System.out.println(a.equals(b));   // true
```

Ở đây phép gán lại `b` làm hai biến không còn chia sẻ cùng đối tượng. Nội dung vẫn bằng nhau, nên `equals` đúng. Chuỗi literal được intern qua *string pool*, nhưng gọi `new String(...)` tạo một đối tượng `String` có danh tính khác. Sự tồn tại của pool không làm hai biến tự gắn kết khi một bên đổi tham chiếu.

Không viết logic nghiệp vụ theo giả định rằng mọi `String` có cùng chữ đều phải `== true`.

## 9. Những nhầm lẫn và lỗi thường gặp

**Nhầm 1 — «Hai biến cùng trỏ nên đổi `b` thì `a` phải đổi».** Sai. `b = ...` chỉ ghi giá trị tham chiếu mới vào biến `b`. Không tồn tại quan hệ đồng bộ phép gán giữa hai biến.

**Nhầm 2 — «`b = b + "!"` là sửa cùng đối tượng cũ».** Sai với `String`. Phép nối tạo ra giá trị chuỗi kết quả, rồi `b` được gán tham chiếu đến kết quả; chuỗi trước đó không bị sửa.

**Nhầm 3 — «Không chia sẻ tham chiếu thì phải dùng `new String(a)`».** Việc gán lại `a` sẽ không ảnh hưởng biến khác ngay cả khi cùng trỏ đến một `String`. Vì `String` bất biến, hầu như không cần tạo bản sao `new String(a)` để chống thay đổi ngoài ý muốn.

**Nhầm 4 — «Truyền tham chiếu nghĩa là phương thức có thể gán lại biến của người gọi».** Java truyền tham trị. Một hàm nhận bản sao giá trị tham chiếu; gán lại tham số chỉ thay đổi biến tham số của hàm.

**Nhầm 5 — «`final` và immutable là một».** `final` áp dụng cho khả năng gán lại biến hoặc trường; bất biến mô tả nội dung của một đối tượng. `final StringBuilder` vẫn có thể `append`.

**Nhầm 6 — «Đối tượng chứa `String` cũng tự động bất biến».** Không. Một trường kiểu `String` có thể được gán giá trị chuỗi khác trong một đối tượng mutable. Các tham chiếu tới chính đối tượng chứa sẽ nhìn thấy trạng thái trường mới.

**Nhầm 7 — «Biến không còn trỏ tới đối tượng cũ thì đối tượng cũ biến mất ngay».** Không nhất thiết. Nếu còn tham chiếu như `first`, đối tượng vẫn có thể được sử dụng. Khi không còn tham chiếu đến đối tượng, JVM mới có thể thu gom nó khi thích hợp; thời điểm thu gom không được bảo đảm.

## 10. Tự luyện tập: dự đoán trước khi chạy

### Bài 1: gán lại nhiều lần

```java
String a = "one";
String b = a;
String c = b;

b = "two";
c = c.concat("!");

System.out.println(a);
System.out.println(b);
System.out.println(c);
```

Kết quả:

```text
one
two
one!
```

Ba biến ban đầu có cùng tham chiếu. Sau đó mỗi phép gán chỉ làm thay đổi **biến ở vế trái**: `b` đổi sang `"two"`; `c` nhận kết quả nối từ `"one"` thành `"one!"`. `a` tiếp tục giữ `"one"`.

### Bài 2: biến đổi trong phương thức có được áp dụng không?

```java
static void update(String value) {
    value = value.replace("a", "o");
}
```

Nếu người gọi có `String text = "Java";` rồi gọi `update(text)`, giá trị `text` có đổi không? **Không.** Biến tham số `value` chỉ được gán lại trong phương thức. Muốn nhận kết quả, hãy đổi phương thức trả về `String` và để người gọi tự gán kết quả.

### Bài 3: đối tượng chứa dùng chung

```java
class Box {
    String value = "A";
}

Box x = new Box();
Box y = x;
String saved = x.value;

y.value = "B";
```

Sau đó `x.value` là `"B"`, `y.value` là `"B"`, nhưng `saved` vẫn là `"A"`. Trường của `Box` dùng chung đã được cập nhật; bản thân hai `String` không bị sửa.

## Kết luận cần ghi nhớ

**Gán lại tham chiếu là cập nhật một biến; không phải sửa đối tượng được tham chiếu, càng không phải cập nhật mọi biến đang trỏ đến đối tượng đó.** Với `String`, tính bất biến bảo đảm các phương thức xử lý chuỗi không làm thay đổi nội dung chuỗi ban đầu. Hãy hỏi chính xác «đang gán lại biến nào?» và «đang sửa trạng thái của đối tượng nào?» khi đọc hoặc viết code. Hai câu hỏi đó giúp tránh nhầm lẫn giữa tham chiếu, object identity và tính bất biến của `String`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-immutability-006">Sao chép đầu vào char&#91;&#93; và tính độc lập của toCharArray</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép đầu vào `char[]` và tính độc lập của `String.toCharArray()` trong Java

## 1. Hai chiều chuyển đổi, cùng một nguyên tắc: bảo vệ tính bất biến của `String`

Trong Java, `char[]` là mảng **có thể thay đổi**: phần tử `array[i]` có thể được gán lại. Ngược lại, `String` biểu diễn **giá trị văn bản bất biến**. Để hai kiểu này làm việc với nhau an toàn, API Java duy trì ranh giới sao chép dữ liệu ở cả hai chiều:

1. **`char[]` → `String`:** `new String(charArray)` lấy nội dung các phần tử mảng tại thời điểm tạo chuỗi. Sửa mảng nguồn **sau khi constructor hoàn tất** không làm thay đổi chuỗi đã tạo.
2. **`String` → `char[]`:** `string.toCharArray()` trả một **mảng mới** chứa các đơn vị ký tự của chuỗi. Sửa mảng nhận về không làm thay đổi `String` nguồn.
3. Các API như `String.valueOf(char[])`, `String.copyValueOf(char[])`, `new String(char[], offset, count)` và `String.getChars(...)` cũng có hợp đồng chuyển/copy dữ liệu tương ứng; cần phân biệt rõ nơi nào chứa bản sao và nơi nào vẫn chia sẻ mảng.

Hình dung theo quan hệ sở hữu nội dung, sau khi đã tạo `String`:

```text
char[] input = {'J','a','v','a'}  [MẢNG THAY ĐỔI ĐƯỢC]
        │
        │ new String(input): chụp giá trị các phần tử
        ▼
String text = "Java"             [GIÁ TRỊ BẤT BIẾN]
        │
        │ text.toCharArray(): tạo mảng mới
        ▼
char[] output = {'J','a','v','a'} [MẢNG THAY ĐỔI ĐƯỢC, ĐỘC LẬP]

input[0] = 'K'  → text vẫn "Java", output vẫn "Java"
output[0] = 'X' → text vẫn "Java", input vẫn "Kava"
```

Sơ đồ chỉ mô tả **hợp đồng về dữ liệu**. Nó không khẳng định `String` phải dùng `char[]` làm cấu trúc lưu trữ bên trong. Chi tiết biểu diễn bộ nhớ của `String` có thể thay đổi theo phiên bản hoặc triển khai JDK; điều đáng tin cậy là tính bất biến và sự độc lập quan sát được qua API.

## 2. `char[]` mutable; phép gán mảng thông thường không tạo bản sao

Một biến kiểu `char[]` giữ **tham chiếu tới một đối tượng mảng**. Gán tham chiếu sang biến khác không nhân đôi phần tử:

```java
char[] input = {'J', 'a', 'v', 'a'};
char[] alias = input; // Hai biến trỏ cùng một mảng.
alias[0] = 'K';

System.out.println(new String(input)); // Kava
System.out.println(input == alias);     // true
```

Đây là **snippet minh họa**, các dòng `System.out.println` đặt trong phương thức như `main`. `alias[0] = 'K'` thay đổi chính mảng mà `input` đang tham chiếu. Khác biệt cốt lõi là:

| Biểu thức | Có tạo bản sao dữ liệu? | Sau khi sửa mảng nguồn |
| --- | --- | --- |
| `char[] b = a;` | Không, chỉ sao chép tham chiếu | `b` quan sát thay đổi ở `a` |
| `char[] b = a.clone();` | Có, tạo mảng mới với các phần tử được sao chép | `b` giữ nội dung riêng |
| `char[] b = Arrays.copyOf(a, a.length);` | Có, tạo mảng mới | `b` giữ nội dung riêng |
| `String s = new String(a);` | Có, tạo giá trị chuỗi từ nội dung mảng | `s` không đổi |
| `char[] b = s.toCharArray();` | Có, trả mảng mới từ chuỗi | Sửa `b` không đổi `s` |

Đối với `char[]`, clone là sao chép đủ sâu để độc lập về **phần tử**, vì mỗi phần tử `char` là giá trị nguyên thủy, không phải tham chiếu tới đối tượng có thể thay đổi bên trong.

## 3. Constructor `new String(char[])` sao chép nội dung đầu vào

Cách trực tiếp để tạo chuỗi bất biến từ mảng ký tự là:

```java
char[] source = {'H', 'e', 'l', 'l', 'o'};
String text = new String(source);
source[0] = 'Y';

System.out.println(new String(source)); // Yello
System.out.println(text);               // Hello
```

Cách đọc đúng:
- Khi `new String(source)` hoàn tất, `text` có nội dung `"Hello"` và nội dung đó được bảo vệ trước sửa đổi tiếp theo của mảng nguồn.
- `source[0] = 'Y'` thay đổi phần tử thứ nhất của mảng nguồn; `text` không giữ một view mutable trỏ ngược tới mảng.
- Không cần `source.clone()` trước `new String(source)` chỉ để bảo vệ `String` khỏi thay đổi *sau đó*: constructor đã đáp ứng hợp đồng độc lập cần thiết. Việc chủ động clone chỉ có ý nghĩa khi ứng dụng còn cần sở hữu riêng **một mảng khác**.

Các overload có ý nghĩa tương tự:

```java
char[] chars = {'J', 'a', 'v', 'a'};

String a = new String(chars);
String b = String.valueOf(chars);
String c = String.copyValueOf(chars);
```

Cả ba đều tạo nội dung `"Java"` độc lập với các thay đổi tiếp theo của `chars`. `String.copyValueOf(char[])` có tác dụng tương đương `String.valueOf(char[])`; tên `copyValueOf` không có nghĩa rằng `valueOf(char[])` sẽ giữ mảng bằng tham chiếu.

**Điểm dễ nhầm về overload:** `String.valueOf(char[])` và `String.valueOf(Object)` là hai overload khác nhau. Ép mảng thành `Object` làm Java chọn overload `Object`, vốn dùng cách biểu diễn đối tượng của mảng:

```java
char[] chars = {'O', 'K'};
System.out.println(String.valueOf(chars));          // OK
System.out.println(String.valueOf((Object) chars)); // Dạng [C@..., không phải "OK"
```

Đoạn này là snippet minh họa, không phải file lớp độc lập. Dạng `[C@...` gồm ký hiệu kiểu mảng `char[]` và phần định danh không ổn định; không được dựa vào nội dung đầy đủ của nó.

## 4. `toCharArray()` luôn trao một mảng ký tự để người gọi sở hữu và sửa

Phương thức `String.toCharArray()` trả một `char[]` **mới** có nội dung tại thời điểm gọi. Người gọi có thể sửa, sắp xếp, điền lại hoặc chuyển mảng đi mà không thể sửa `String` nguồn qua mảng đó.

```java
String text = "Java";
char[] first = text.toCharArray();
char[] second = text.toCharArray();

first[0] = 'K';
second[1] = 'O';

System.out.println(text);               // Java
System.out.println(new String(first));  // Kava
System.out.println(new String(second)); // JOva
System.out.println(first == second);    // false
```

Hai lệnh `toCharArray()` trả hai mảng khác nhau. Cả hai đều không phải “cửa hậu” để thay đổi chuỗi gốc. Khi cần nội dung của `String` ở dạng `char[]`, đây là cách chuẩn để **chủ động tạo dữ liệu mutable**.

Không nên hiểu nhầm rằng `String` luôn chứa sẵn một `char[]` mà `toCharArray()` chỉ trả lại; giả định đó sẽ khiến người học viết code lệ thuộc cấu trúc nội bộ và đi ngược hợp đồng API.

### Chương trình chạy đầy đủ: hai chiều sao chép

Lưu dưới tên `CharArrayCopyDemo.java`:

```java
public class CharArrayCopyDemo {
    public static void main(String[] args) {
        char[] input = {'J', 'a', 'v', 'a'};
        char[] alias = input;

        String byConstructor = new String(input);
        String byValueOf = String.valueOf(input);
        String byCopyValueOf = String.copyValueOf(input);

        alias[0] = 'K'; // input cũng đổi: alias và input là một mảng.
        System.out.println(new String(input));
        System.out.println(byConstructor);
        System.out.println(byValueOf);
        System.out.println(byCopyValueOf);

        char[] extracted = byConstructor.toCharArray();
        extracted[0] = 'X';
        System.out.println(new String(extracted));
        System.out.println(byConstructor);

        char[] extractedAgain = byConstructor.toCharArray();
        System.out.println(extracted == extractedAgain);
        System.out.println(new String(extractedAgain));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 CharArrayCopyDemo.java
java CharArrayCopyDemo
```

Kết quả chuẩn:

```text
Kava
Java
Java
Java
Xava
Java
false
Java
```

Giải thích từ kết quả:
- `alias[0]` sửa mảng gốc; cả ba chuỗi đã tạo từ mảng vẫn là `Java`.
- `extracted[0]` chỉ sửa mảng đầu tiên do `toCharArray()` trả về.
- `byConstructor` không đổi và lần gọi `toCharArray()` tiếp theo mang lại nội dung gốc.
- `extracted == extractedAgain` là `false` vì đây là **hai mảng** được tạo bởi hai lần gọi riêng biệt.

## 5. Chỉ sao chép một đoạn mảng: `new String(char[], offset, count)`

Không phải lúc nào cả mảng nguồn cũng thuộc về phần văn bản cần lưu. Constructor `new String(char[] value, int offset, int count)` dùng **đoạn liên tiếp** bắt đầu tại chỉ số `offset` và gồm `count` phần tử `char`:

```java
char[] frame = {'[', 'J', 'a', 'v', 'a', ']'};
String inner = new String(frame, 1, 4);
frame[1] = 'X';

System.out.println(inner);              // Java
System.out.println(new String(frame));  // [Xava]
```

Khoảng được sao chép là `[offset, offset + count)`: điểm đầu tính, điểm cuối loại trừ. Với mảng ví dụ, phần tử ở chỉ số 1, 2, 3, 4 tạo nội dung `Java`, không chứa hai dấu ngoặc. Dữ liệu trong `inner` độc lập với cả mảng `frame`, kể cả các vị trí nằm trong đoạn đã copy.

Điều kiện hợp lệ: `offset >= 0`, `count >= 0` và `offset + count <= value.length` (diễn đạt theo điều kiện giới hạn chỉ số; triển khai kiểm tra theo cách tránh tràn số). Giá trị `count == 0` hợp lệ nếu `offset` ở phạm vi phù hợp, kể cả `offset == value.length`. Offset/count sai gây `IndexOutOfBoundsException`; truyền mảng `null` gây `NullPointerException`.

Phân biệt với `Arrays.copyOfRange(array, from, to)`:
- `new String(array, offset, count)` trả **`String`**, tham số thứ ba là **số lượng** phần tử.
- `Arrays.copyOfRange(array, from, to)` trả **`char[]`**, tham số thứ ba là **chỉ số kết thúc loại trừ**; với một số đầu vào có `to` vượt chiều dài mảng, phương thức còn có quy tắc đệm phần tử mặc định. Không đổi chỗ hai quy tắc.

## 6. Sao chép từ `String` vào mảng có sẵn: `getChars`

Nếu đã có mảng đích, `String.getChars(srcBegin, srcEnd, dst, dstBegin)` sao chép ký tự của chuỗi sang **mảng đó**. Phương thức không trả một mảng mới; `dst` là đối tượng mà người gọi cấp. Nó vẫn không tạo đường sửa ngược lại vào `String`.

Định nghĩa các tham số:
- `srcBegin`: chỉ số bắt đầu của chuỗi, tính vào đoạn.
- `srcEnd`: chỉ số kết thúc chuỗi, không tính vào đoạn.
- `dst`: mảng nhận dữ liệu.
- `dstBegin`: chỉ số bắt đầu ghi vào mảng đích.

Ví dụ đầy đủ, lưu `SliceCopyDemo.java`:

```java
public class SliceCopyDemo {
    public static void main(String[] args) {
        char[] frame = {'[', 'J', 'a', 'v', 'a', ']'};
        String inner = new String(frame, 1, 4);

        char[] destination = {'_', '_', '_', '_', '_', '_'};
        inner.getChars(1, 3, destination, 2); // Sao chép "av" vào vị trí 2, 3.

        frame[1] = 'X';
        destination[2] = 'Z';

        System.out.println(inner);
        System.out.println(new String(frame));
        System.out.println(new String(destination));
        System.out.println(new String(inner.toCharArray()));
    }
}
```

```bash
javac -encoding UTF-8 SliceCopyDemo.java
java SliceCopyDemo
```

```text
Java
[Xava]
__Zv__
Java
```

Vì `srcEnd` là chỉ số **loại trừ**, `getChars(1,3,...)` sao chép hai ký tự `a` và `v`, đặt vào `destination[2]` và `destination[3]`. Sau khi sửa đích thành `Z`, mảng `destination` in ra `__Zv__`; chuỗi `inner` vẫn là `Java`. Phải cấp mảng đích đủ lớn và sử dụng chỉ số hợp lệ, nếu không sẽ có ngoại lệ biên; `dst == null` cũng không hợp lệ.

Dùng `getChars` khi cần điền một phần dữ liệu vào một mảng do người gọi quản lý; dùng `toCharArray()` khi muốn nhận mảng mới chứa **toàn bộ** chuỗi.

## 7. Tính bất biến, bản chụp nội dung và thiết kế API phòng thủ

Giả sử một lớp nhận dữ liệu `char[]` của người gọi để lưu thành giá trị văn bản. Nếu lớp giữ thẳng tham chiếu mảng vào một trường `char[]`, bên gọi vẫn có thể sửa dữ liệu từ bên ngoài. Nếu lớp chỉ cần dữ liệu chuỗi, chuyển sang `String` trong constructor sẽ tạo giá trị văn bản bất biến; khi cần trả mảng mutable, hãy tạo mảng qua `toCharArray()`.

Lưu `ImmutableLabel.java`:

```java
import java.util.Objects;

public final class ImmutableLabel {
    private final String value;

    public ImmutableLabel(char[] input) {
        Objects.requireNonNull(input, "input");
        this.value = new String(input);
    }

    public String text() {
        return value;
    }

    public char[] characters() {
        return value.toCharArray();
    }

    public static void main(String[] args) {
        char[] source = {'H', 'e', 'l', 'l', 'o'};
        ImmutableLabel label = new ImmutableLabel(source);

        source[0] = 'Y';
        char[] exported = label.characters();
        exported[1] = 'X';

        System.out.println(new String(source));
        System.out.println(new String(exported));
        System.out.println(label.text());
    }
}
```

```bash
javac -encoding UTF-8 ImmutableLabel.java
java ImmutableLabel
```

```text
Yello
HXllo
Hello
```

Trường `value` là `final`, tức không thể gán lại tham chiếu sau constructor, đồng thời giá trị `String` mà nó tham chiếu bất biến. Dù người gọi sửa `source` hoặc mảng `exported`, nội dung `label.text()` vẫn là `Hello`. Đây là một dạng **defensive copy** tại ranh giới nhận/trả dữ liệu; trong ví dụ, hai thao tác sao chép do chính API `String` bảo đảm.

Nếu cần tiếp tục giữ mảng mutable ở bên trong một lớp (ví dụ vì thuật toán đòi hỏi sửa ký tự tại chỗ), có thể nhận `input.clone()` và khi trả mảng thì trả bản sao. Nhưng trường mảng `private final char[]` **không tự biến nội dung mảng thành bất biến**: `final` chỉ khóa tham chiếu của trường, không khóa `array[i]`.

### Vì sao thời điểm copy có ý nghĩa?

Một `String` được tạo từ mảng là **bản chụp giá trị vào thời điểm constructor đọc mảng**, không phải liên kết động. Sau thời điểm đó, việc sửa mảng không tác động chuỗi. Ngược lại, nếu một luồng khác đang **đồng thời thay đổi mảng trong lúc chương trình sao chép**, không nên giả định lần copy cho ra ảnh chụp nhất quán theo một thời điểm duy nhất: cần kiểm soát đồng bộ, quyền sở hữu dữ liệu hoặc tránh cập nhật chồng lấn. Tính bất biến của chuỗi sau khi tạo **không thay thế** cơ chế đồng bộ khi dữ liệu đầu vào đang bị sửa.

## 8. UTF-16: sao chép `char[]` không đồng nghĩa sao chép “ký tự hiển thị”

`char` là một **đơn vị mã UTF-16** (16 bit). `String.length()` và độ dài mảng `string.toCharArray().length` đều đo số đơn vị mã UTF-16, không nhất thiết là số Unicode code point hay số ký tự hiển thị (*grapheme cluster*).

Ví dụ, emoji `🙂` nằm ngoài BMP và được biểu diễn bằng **hai** phần tử `char` (cặp surrogate). `toCharArray()` giữ nguyên hai đơn vị mã đó theo đúng thứ tự; `new String(array)` xây dựng lại nội dung chuỗi tương ứng.

Lưu `UnicodeCopyDemo.java` ở mã hóa UTF-8:

```java
public class UnicodeCopyDemo {
    public static void main(String[] args) {
        String original = "A🙂B";
        char[] units = original.toCharArray();
        String roundTrip = new String(units);

        System.out.println(original.length());
        System.out.println(units.length);
        System.out.println(original.codePointCount(0, original.length()));
        System.out.println(roundTrip.equals(original));

        units[0] = 'X';
        System.out.println(original);
        System.out.println(new String(units));
    }
}
```

```bash
javac -encoding UTF-8 UnicodeCopyDemo.java
java UnicodeCopyDemo
```

```text
4
4
3
true
A🙂B
X🙂B
```

Ba Unicode code point (`A`, `🙂`, `B`) chiếm bốn `char`. Sao chép sang mảng và ngược lại bảo toàn chuỗi, nhưng nếu sửa riêng **một nửa** cặp surrogate, có thể tạo dãy UTF-16 không hợp lệ. Khi muốn xử lý Unicode theo code point, cần xem `codePoints()`, `codePointAt()`, `offsetByCodePoints()` hoặc API thích hợp; việc dùng `char[]` không tự giải quyết được ranh giới code point.

## 9. Chi phí sao chép, hiệu năng và dữ liệu nhạy cảm

Hai chiều chuyển đổi có chi phí tỉ lệ với kích thước dữ liệu về mặt thao tác sao chép. Nếu vòng lặp liên tục đổi `String` ↔ `char[]`, có thể tạo thêm đối tượng, tăng cấp phát và gây áp lực bộ thu gom rác. Trước khi tối ưu, hãy chọn mô hình dữ liệu theo công việc thực tế:
- Nếu chỉ cần đọc và lưu chuỗi, tiếp tục sử dụng `String`; đừng gọi `toCharArray()` chỉ vì muốn duyệt từng ký tự khi `charAt`/`codePoints` có thể phù hợp.
- Nếu cần biến đổi hàng loạt ký tự, tạo mảng một lần, xử lý rồi tạo `String` một lần khi xong (hoặc xem xét `StringBuilder` tùy thao tác).
- Nếu một API nhận `char[]` nhưng người gọi chỉ có `String`, `toCharArray()` là chuyển đổi chuẩn; nhận thức rằng nó tạo dữ liệu mới cần được quản lý.
- Nếu một API nhận `String` nhưng người gọi sở hữu `char[]`, `new String(chars)` thể hiện rõ ý định tạo giá trị văn bản độc lập.

Trong một số bối cảnh bảo mật, mật khẩu hoặc dữ liệu bí mật được giữ ở `char[]` nhằm cho phép ghi đè **mảng mà ứng dụng sở hữu** sau khi sử dụng, ví dụ `Arrays.fill(secret, '\0')`. Khi chuyển sang `String`, ứng dụng không có cơ chế API tương đương để xóa nội dung chuỗi bất biến theo yêu cầu; vì vậy không tạo `String` chỉ để tiện xử lý nếu cần kiểm soát vòng đời dữ liệu nhạy cảm. Ngay cả khi ghi đè mảng, cũng không thể khẳng định mọi bản sao trong JVM/hệ thống đã được xóa sạch.

## 10. Lỗi phổ biến và cách tự kiểm tra

| Nhầm lẫn | Hậu quả | Cách sửa |
| --- | --- | --- |
| `char[] alias = source` là sao chép nội dung | Sửa qua `alias` đổi `source` | Dùng `clone`/`Arrays.copyOf` khi muốn mảng mới |
| `new String(source)` còn chia sẻ `char[]` nguồn | Lo lắng sai về biến đổi chuỗi | Tin vào hợp đồng copy của constructor |
| `toCharArray()` trả mảng lưu trữ bên trong `String` | Cố dùng mảng để sửa chuỗi gốc | Hiểu đây là mảng mới, sửa độc lập |
| `String.valueOf(char[])` giữ nguyên mảng | Nhầm với một dạng view | Đây là chuyển đổi thành chuỗi độc lập |
| `String.valueOf((Object) chars)` giống `String.valueOf(chars)` | Nhận văn bản dạng `[C@...` | Truyền đúng kiểu `char[]` |
| `new String(arr, from, to)` xem `to` là chỉ số cuối | Chép sai phạm vi | Tham số thứ ba là `count` |
| `getChars` tạo mảng mới | Hiểu sai nơi dữ liệu được ghi | Tự cung cấp `dst` đủ dung lượng |
| `final char[]` là mảng bất biến | Lộ thay đổi phần tử qua alias | Sao chép mảng khi nhận/trả hoặc dùng `String` |
| `toCharArray().length` bằng số ký tự người dùng nhìn thấy | Cắt sai cặp surrogate hoặc grapheme | Xem đơn vị UTF-16/code point/grapheme |

### Tự kiểm tra bằng dự đoán output

Hãy dự đoán kết quả trước khi đọc giải thích:

```java
char[] data = {'a', 'b'};
String text = new String(data);
char[] out = text.toCharArray();
data[0] = 'x';
out[1] = 'y';

System.out.println(new String(data));
System.out.println(text);
System.out.println(new String(out));
```

Đây là snippet đặt trong `main`, không phải lớp độc lập. Kết quả là:

```text
xb
ab
ay
```

Ba giá trị khác nhau vì mảng nguồn, đối tượng `String` và mảng trả về từ `toCharArray()` giữ nội dung riêng. Sửa một trong hai mảng sau khi tạo chuỗi không lan truyền sang chuỗi hay mảng còn lại.

## 11. Ghi nhớ khi lập trình

- `new String(char[])`, `String.valueOf(char[])` và `String.copyValueOf(char[])` tạo chuỗi có nội dung **độc lập** với các thay đổi tiếp theo của mảng nguồn.
- `String.toCharArray()` tạo mảng **mới**; người gọi sửa mảng thoải mái mà không sửa chuỗi nguồn.
- `char[] b = a` chỉ là **alias**, không phải sao chép; `a.clone()` hoặc `Arrays.copyOf` mới tạo mảng riêng.
- `new String(chars, offset, count)` sao chép một đoạn theo **offset và số phần tử**; `getChars(begin, end, dst, dstBegin)` sao chép vào **mảng đích sẵn có** với điểm cuối loại trừ.
- Sao chép bảo toàn các đơn vị UTF-16, nhưng không biến một `char` thành khái niệm Unicode code point hay ký tự hiển thị.
- Tính độc lập của bản sao đặc biệt hữu ích khi xây dựng API bất biến, nhưng phải cân nhắc chi phí sao chép, thời điểm đồng bộ và dữ liệu nhạy cảm theo ngữ cảnh.

## Tài liệu API Java SE 17

- [`String(char[])`, `String(char[], int, int)`, `toCharArray()` và `getChars()` — Oracle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`Arrays.copyOf` — Oracle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html)
- [`Character` và mã Unicode — Oracle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-immutability-007">Lợi ích khi dùng làm khóa, chia sẻ dữ liệu và xử lý đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Lợi ích của tính bất biến khi dùng String làm khóa, chia sẻ dữ liệu và xử lý đồng thời

**String là một kiểu tham chiếu nhưng nội dung của một đối tượng String không thể thay đổi sau khi đối tượng được tạo.** Đây là tính *bất biến* (*immutability*). Những phương thức như concat(), replace(), substring(), toUpperCase() hay trim() không sửa đối tượng hiện tại; chúng trả về String kết quả, có thể là đối tượng mới hoặc chính đối tượng ban đầu nếu không cần thay đổi nội dung.

Tính bất biến tạo ra ba lợi ích nổi bật trong ứng dụng Java:

1. **Làm khóa ổn định** trong HashMap, HashSet, cache và các cấu trúc dựa trên equals()/hashCode().
2. **Chia sẻ dữ liệu dễ dự đoán** giữa phương thức, thành phần và luồng: nơi nhận không thể sửa nội dung String mà nơi gửi đang nắm giữ.
3. **Đọc và xử lý đồng thời thuận lợi hơn**: nhiều luồng có thể dùng cùng một giá trị String mà không phải khóa để bảo vệ chính nội dung của giá trị đó.

Lợi ích thứ ba có **giới hạn quan trọng**: String bất biến không làm cho biến tham chiếu, Map, List hoặc toàn bộ đối tượng chứa String trở thành an toàn khi nhiều luồng cùng thay đổi.

## 1. Tính ổn định nằm ở chính giá trị văn bản

Hai biến có thể cùng giữ tham chiếu tới một `String`; các thao tác biến đổi trả về giá trị để sử dụng mà không thay đổi nội dung chuỗi đã tạo. Nhờ vậy, nơi đang giữ một khóa hoặc bản báo cáo không bất ngờ thấy ký tự bị sửa từ một nơi khác. Biến hoặc trường chứa tham chiếu vẫn có thể được gán lại, vì thế lợi ích này có phạm vi là **giá trị String**, không bao gồm toàn bộ trạng thái mutable xung quanh nó.

## 2. Lợi ích khi dùng String làm khóa của HashMap và HashSet

Một khóa của HashMap cần có hành vi equals() và hashCode() ổn định khi nằm trong Map. HashMap sử dụng hashCode() để định vị vùng lưu trữ và dùng equals() khi cần xác định các khóa có nội dung tương đương. HashSet dựa trên nguyên tắc tương ứng để lưu các phần tử duy nhất.

Đối với String:

- equals() so sánh **nội dung**, không chỉ so sánh hai tham chiếu.
- Hai String có nội dung bằng nhau có cùng giá trị hashCode().
- Nội dung String không đổi, nên hashCode() và kết quả so sánh nội dung của một đối tượng không thay đổi theo thời gian.
- Có thể tạo một String khác nhưng nội dung bằng nhau để tra cứu khóa đã lưu mà không cần giữ đúng cùng một instance.

Ví dụ minh họa:

~~~java
import java.util.HashMap;
import java.util.Map;

public class StringKeyDemo {
    public static void main(String[] args) {
        Map<String, Integer> prices = new HashMap<>();
        String savedKey = "item:42";
        prices.put(savedKey, 150);

        String lookupKey = new StringBuilder("item:")
                .append(42)
                .toString();

        System.out.println(savedKey.equals(lookupKey));
        System.out.println(savedKey.hashCode() == lookupKey.hashCode());
        System.out.println(prices.get(lookupKey));

        String updatedKey = savedKey.replace("42", "43");
        System.out.println(savedKey);
        System.out.println(updatedKey);
        System.out.println(prices.get(savedKey));
        System.out.println(prices.get(updatedKey));
    }
}
~~~

Lưu thành **StringKeyDemo.java**, rồi chạy trên JDK 17+:

~~~bash
javac StringKeyDemo.java
java StringKeyDemo
~~~

Kết quả:

~~~text
true
true
150
item:42
item:43
150
null
~~~

**Giải thích:** lookupKey có nội dung bằng savedKey nên Map tìm được giá trị 150. replace() tạo giá trị updatedKey khác; savedKey vẫn là "item:42", vì vậy khóa đã đặt trong Map giữ nguyên. Không có bản ghi "item:43" nên lần tra cứu cuối trả về null.

### Nếu khóa có thể bị sửa thì sao?

Giả sử một lớp tự định nghĩa dùng trường id mutable trong equals() và hashCode(); đưa instance vào HashMap rồi sửa id sau đó có thể khiến việc tra cứu bằng khóa ấy hoặc một khóa tương đương không tìm được bản ghi như mong đợi. Nguyên nhân là giá trị băm tại lúc đưa khóa vào cấu trúc có thể khác với giá trị băm sau khi sửa.

String tránh được riêng nguy cơ **nội dung khóa bị sửa sau khi chèn**. Nhưng không có nghĩa tất cả khóa hợp lệ đều phải là String: một đối tượng bất biến khác với equals()/hashCode() nhất quán cũng có thể là khóa tốt.

**Không nhầm StringBuilder hay StringBuffer với String khi dùng làm khóa:** hai lớp bộ đệm không định nghĩa equals()/hashCode() để so sánh nội dung giống String. Việc cùng in ra một chuỗi không có nghĩa hai đối tượng builder/buffer là hai khóa tương đương. Nếu dữ liệu dùng để xây khóa đang ở StringBuilder, hãy hoàn thiện rồi gọi toString() để tạo khóa String.

### Những điều String không tự giải quyết khi làm khóa

- **Hash collision vẫn có thể xảy ra:** hai String khác nội dung đôi khi có cùng hashCode(); vì vậy phải dùng equals(), không được lấy riêng hashCode() làm chứng minh hai chuỗi bằng nhau.
- **Khóa phân biệt chữ hoa/chữ thường:** "USER:42" không bằng "user:42" theo String.equals(). Nếu hệ thống muốn bỏ qua chữ hoa/chữ thường, hãy chuẩn hóa theo quy tắc nghiệp vụ trước khi lưu và tra cứu; với mã định danh không phụ thuộc ngôn ngữ có thể dùng toLowerCase(Locale.ROOT).
- **Unicode có nhiều cách biểu diễn:** một số văn bản trông giống nhau vẫn khác dãy đơn vị mã; nếu yêu cầu chuẩn hóa Unicode, cần quy định cách chuẩn hóa nhất quán (chẳng hạn Normalizer) ở cả lúc ghi và đọc.
- **HashMap không tự thread-safe:** khóa String ổn định không cho phép nhiều luồng sửa chung một HashMap mà bỏ qua đồng bộ hóa. Với truy cập cập nhật đồng thời, dùng cơ chế phù hợp như ConcurrentHashMap, khóa hoặc quy tắc chỉ-đọc sau khi xây dựng.

## 3. Lợi ích khi chia sẻ dữ liệu giữa phương thức và thành phần

Một String có thể được chuyển qua tham số, trả về từ hàm, lưu vào đối tượng hoặc truyền sang một thành phần khác mà không lo thành phần nhận chỉnh sửa **nội dung của chính đối tượng String**. Vì vậy việc suy luận về một giá trị đã được tạo đơn giản hơn: những thao tác thay đổi văn bản sẽ tạo ra giá trị mới, không làm âm thầm biến đổi dữ liệu đang dùng ở nơi khác.

Ví dụ sau không thay đổi giá trị của caller:

~~~java
public class ShareStringDemo {
    static String addSuffix(String input) {
        return input.concat("-done");
    }

    public static void main(String[] args) {
        String original = "task";
        String next = addSuffix(original);

        System.out.println(original); // task
        System.out.println(next);     // task-done
    }
}
~~~

Lưu thành **ShareStringDemo.java**, chạy bằng javac ShareStringDemo.java và java ShareStringDemo. Hai dòng in ra lần lượt là task và task-done. Việc hàm nhận tham chiếu tới String không làm hàm có khả năng sửa giá trị gốc.

### Tạo một bản chụp nội dung từ bộ đệm mutable

Một chương trình có thể dựng chuỗi bằng StringBuilder rồi công bố kết quả String cho nơi khác. Sau khi xuất kết quả, những thay đổi sau đó ở builder không ảnh hưởng tới String đã tạo:

~~~java
StringBuilder draft = new StringBuilder("version 1");
String published = draft.toString();

draft.replace(0, draft.length(), "version 2");

System.out.println(published); // version 1
System.out.println(draft);     // version 2
~~~

Cách làm phổ biến là **xây dựng dữ liệu cục bộ bằng StringBuilder, chuyển thành String khi hoàn tất, sau đó chia sẻ String**. Đối với dữ liệu văn bản lớn, vẫn cần lưu ý chi phí bộ nhớ và sao chép; tính bất biến đem lại sự rõ ràng về trạng thái chứ không bảo đảm mọi cách dựng chuỗi đều nhanh nhất.

### Ranh giới của tính bất biến

Một lớp có trường String không nhất thiết là immutable nếu đối tượng đó có setter hoặc các trường mutable khác. Ví dụ:

~~~java
class Config {
    String name;

    void changeName(String next) {
        name = next;
    }
}
~~~

String mà name tham chiếu không thể bị sửa nội dung; tuy nhiên trường name của đối tượng Config vẫn bị thay thế. Nếu Config được chia sẻ giữa nhiều luồng và name bị cập nhật, phải xử lý quy tắc truy cập đồng thời tới **trường name**.

Tương tự, List<String> hay Map<String, String> vẫn là collection có thể thay đổi nếu chọn implementation mutable. Tính bất biến của phần tử/khóa/giá trị không tự bảo vệ thao tác thêm, xóa, thay thế trong collection.

## 4. Lợi ích khi xử lý đồng thời

Hãy phân biệt hai loại trạng thái:

- **Nội dung của String:** không có thao tác nào chỉnh sửa nội dung đối tượng đã tạo. Nhiều luồng có thể đọc và sử dụng cùng String mà không phải khóa chỉ để ngăn nội dung bị thay đổi.
- **Tham chiếu và các cấu trúc chứa String:** vẫn có thể bị cập nhật. Các thao tác trên trạng thái chia sẻ như thay thế trường, thêm phần tử vào List/Map hoặc kiểm tra-rồi-cập-nhật cần cơ chế đồng thời phù hợp.

### Ví dụ nhiều luồng cùng đọc một String

Chương trình sau cho hai luồng đọc cùng một giá trị String và tạo **kết quả riêng**. Không luồng nào có thể làm thay đổi nội dung shared.

~~~java
import java.util.Locale;

public class SharedStringThreadsDemo {
    public static void main(String[] args) throws InterruptedException {
        String shared = "Java";
        String[] outputs = new String[2];

        Thread upper = new Thread(
                () -> outputs[0] = shared.toUpperCase(Locale.ROOT));
        Thread suffix = new Thread(
                () -> outputs[1] = shared.concat(" 17"));

        upper.start();
        suffix.start();

        upper.join();
        suffix.join();

        System.out.println("shared=" + shared);
        System.out.println("upper=" + outputs[0]);
        System.out.println("suffix=" + outputs[1]);
    }
}
~~~

Lưu thành **SharedStringThreadsDemo.java**, chạy:

~~~bash
javac SharedStringThreadsDemo.java
java SharedStringThreadsDemo
~~~

Kết quả:

~~~text
shared=Java
upper=JAVA
suffix=Java 17
~~~

**Vì sao đúng?** shared được giữ nguyên; hai phương thức tạo ra kết quả String riêng. Mỗi luồng ghi vào phần tử khác nhau của mảng outputs, không tranh chấp cùng một ô. main gọi join() trước khi đọc kết quả, nên có quan hệ *happens-before* từ các thao tác trong luồng hoàn tất tới thao tác sau join() ở main. Ví dụ không chứng minh rằng mọi truy cập tới mọi mảng đều thread-safe: thiết kế này đúng vì các luồng ghi phần tử riêng và main chờ hoàn tất.

Không cần dùng synchronized để bảo vệ **nội dung shared**, nhưng vẫn cần cơ chế phối hợp khi có trạng thái khác được sửa đồng thời. Đó là lợi ích trực tiếp của dữ liệu bất biến: phạm vi phải bảo vệ bằng khóa giảm xuống.

### Giới hạn khi nhiều luồng cập nhật trạng thái

Ví dụ trên an toàn vì mỗi luồng ghi một phần tử kết quả riêng, rồi `main` gọi `join()` trước khi đọc. Đây là cách phối hợp luồng rõ ràng; nội dung `String` bất biến chỉ giúp các luồng không thể sửa cùng văn bản `shared`.

Nếu các luồng cùng thay đổi một **trường tham chiếu** kiểu `String` hoặc cùng ghi `Map`, `List`, vẫn phải chọn cơ chế công bố, đồng bộ và cập nhật phù hợp. `volatile String` có thể bảo đảm visibility cho trường, nhưng không làm phép `status = status + suffix` trở thành nguyên tử. Khi cần giữ cả hai cập nhật, xem xét `synchronized`, `AtomicReference.updateAndGet` hoặc API concurrent collection tương ứng. Bài về ranh giới an toàn đa luồng trong chương giải thích bằng ví dụ lost update và `happens-before`.

## 5. Bài tập tình huống: khóa cache và dữ liệu công bố

Giả sử ứng dụng tạo nhãn trạng thái cho nhiều công việc:

1. Mỗi tác vụ nhận một mã công việc dưới dạng String, ví dụ "job:17".
2. Map lưu kết quả theo mã công việc; các lần truy cập dùng String có nội dung bằng nhau để xác định cùng một khóa.
3. Nội dung báo cáo được xây dựng qua nhiều lần append trên StringBuilder **riêng** của tác vụ.
4. Khi xong, tác vụ gọi toString() và chuyển String kết quả sang bước tổng hợp.
5. Nếu nhiều luồng cần cùng cập nhật bộ Map, sử dụng một cơ chế đồng thời như ConcurrentHashMap hoặc khóa phù hợp; nếu Map chỉ được xây dựng trong một luồng rồi công bố để đọc, cần bảo đảm việc công bố được đồng bộ đúng cách.

Ưu điểm của cách thiết kế này là người đọc Map không phải lo một mã khóa String bỗng thay đổi nội dung. Người đọc kết quả cũng không thấy String báo cáo đã công bố bị thay đổi theo builder nội bộ của tác vụ. Các vấn đề còn phải giải quyết nằm ở **quyền sở hữu và đồng bộ hóa bộ Map**, không phải ở nội dung String.

Nếu nghiệp vụ yêu cầu khóa so sánh không phân biệt hoa/thường hoặc có bước chuẩn hóa, hãy biến đổi dữ liệu **trước khi đặt vào Map** và áp dụng chính quy tắc đó cho mọi lần tra cứu. Ví dụ nếu chọn lowercase cho mã định danh máy, hãy dùng một cách chuẩn hóa nhất quán như id.toLowerCase(Locale.ROOT). Immutable chỉ bảo đảm khóa không đổi **sau khi đã tạo**; nó không tự quyết định hai dạng văn bản nào phải được coi là tương đương.

## 6. Ghi nhớ và tự kiểm tra

**Chuỗi suy luận cần nhớ:**

- **Immutable String → nội dung ổn định → equals()/hashCode() ổn định → thuận lợi làm khóa.**
- **Immutable String → nơi nhận không thể sửa nội dung gốc → thuận lợi chia sẻ giá trị và giảm lỗi do aliasing.**
- **Immutable String → nhiều luồng đọc cùng nội dung mà không cần khóa để chống chỉnh sửa → thuận lợi cho xử lý đồng thời.**
- **Immutable String ≠ dữ liệu chứa nó tự thread-safe:** việc cập nhật tham chiếu, collection hoặc nhiều bước vẫn phải được phối hợp.

Hãy tự trả lời các câu hỏi sau trước khi áp dụng:

1. Nếu gọi name.replace("A", "B") mà không lưu kết quả, biến name có thay đổi không?
2. Vì sao HashMap có thể tra cứu bằng một String khác instance nhưng cùng nội dung với khóa ban đầu?
3. Nếu một luồng cập nhật trường status kiểu String và một luồng khác đọc nó, bất biến của String có đủ bảo đảm thấy cập nhật mới hay không?
4. Nếu hai tác vụ đều xây dựng báo cáo, tại sao mỗi tác vụ nên giữ StringBuilder riêng rồi công bố String thay vì sửa chung một StringBuilder?
5. Một Map<String, Integer> có an toàn khi nhiều luồng cùng put() chỉ vì khóa là String hay không?

**Đáp án ngắn:** (1) Không, replace() trả về giá trị. (2) Vì String.equals() so sánh nội dung và hai giá trị bằng nhau có cùng hashCode(). (3) Không, còn cần quan hệ đồng bộ/công bố phù hợp. (4) Tránh chia sẻ bộ đệm mutable và có kết quả đầu ra ổn định. (5) Không, cấu trúc Map cần cơ chế truy cập đồng thời phù hợp.

**Kết luận:** Tính bất biến của String đem lại khả năng làm khóa ổn định, chia sẻ giá trị không lo bị sửa nội dung, và sử dụng cùng dữ liệu trong nhiều luồng dễ an toàn hơn. Sự an toàn đó áp dụng cho **nội dung String**; các biến và cấu trúc mutable quanh nó vẫn cần thiết kế đồng bộ riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-immutability-008">Chuỗi bất biến không làm mọi biến hoặc cấu trúc chứa nó an toàn đa luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi bất biến không làm mọi biến hoặc cấu trúc chứa nó an toàn đa luồng

## 1. Vấn đề cần hiểu

`String` trong Java là **immutable** (bất biến): sau khi một đối tượng `String` được tạo, nội dung của chính đối tượng đó không thể thay đổi thông qua API công khai. Nhiều luồng có thể cùng đọc một đối tượng `String` đã được chia sẻ đúng cách mà không làm hỏng nội dung chuỗi.

Tuy nhiên, **`String` bất biến không đồng nghĩa mọi biến tham chiếu đến `String`, mọi thao tác trên biến đó hoặc mọi cấu trúc lưu `String` đều an toàn đa luồng**.

Ví dụ:

```java
String name = "Java";
name = name + " 17";
```

Biểu thức trên tạo giá trị chuỗi mới rồi **gán lại biến `name`**; nó không chỉnh sửa đối tượng `"Java"`. Nếu nhiều luồng cùng đọc và gán lại **một biến dùng chung**, vấn đề tranh chấp (race condition), mất cập nhật (lost update) và khả năng nhìn thấy dữ liệu (visibility) vẫn tồn tại.

Mục tiêu bài học:
- Phân biệt **đối tượng bất biến**, **biến tham chiếu có thể gán lại** và **cấu trúc chứa chuỗi có thể thay đổi**.
- Giải thích vì sao `shared = shared + text` không phải một phép cập nhật nguyên tử.
- Hiểu vai trò của `volatile`, `synchronized` và `AtomicReference<String>` theo Java Memory Model.
- Chọn giải pháp phù hợp khi chia sẻ trạng thái chuỗi giữa các luồng.

## 2. Đối tượng `String` và biến tham chiếu là hai thứ khác nhau

Xét:

```java
String a = "hello";
String b = a;
a = a.toUpperCase();

System.out.println(a); // HELLO
System.out.println(b); // hello
```

`a` và `b` ban đầu trỏ đến cùng nội dung `"hello"`. `toUpperCase()` trả một giá trị `String` mới trong ví dụ này, sau đó `a` được gán lại; `b` vẫn trỏ đến chuỗi ban đầu.

Cần tách ba mức độ:

| Thành phần | Bất biến không? | Điều gì có thể xảy ra khi đa luồng? |
| --- | --- | --- |
| Một **đối tượng `String`** | Có; nội dung không thay đổi. | Các luồng đọc chung không tranh chấp vì thao tác sửa nội dung. |
| Một **biến/field `String` dùng chung**, ví dụ `current` | Không nhất thiết; tham chiếu có thể được gán lại. | Đọc/ghi đồng thời có thể thiếu bảo đảm visibility và xảy ra lost update. |
| Một **container** như `ArrayList<String>`, `HashMap<String,String>` hoặc `StringBuilder` | Thường là mutable. | Các phép sửa container có thể race hoặc vi phạm hợp đồng thread safety. |

Một biến `final String greeting = "hello";` ngăn **gán lại biến đó** sau khi khởi tạo, nhưng `final` không tự biến một cấu trúc chứa biến thành đối tượng immutable, cũng không làm tất cả các field khác của đối tượng chứa trở nên thread-safe.

## 3. Vì sao ghép chuỗi vào biến chung có thể mất cập nhật?

Xét trạng thái dùng chung:

```java
class State {
    String text = "X";
}
```

Nếu hai luồng cùng thực hiện:

```java
state.text = state.text + "A"; // Luồng 1
state.text = state.text + "B"; // Luồng 2
```

Một phép `+=` hay phép gán kết hợp này phải thực hiện nhiều bước:

1. **Đọc** tham chiếu hiện tại từ `state.text`.
2. **Tính** một giá trị `String` mới bằng cách ghép hậu tố.
3. **Ghi** tham chiếu `String` mới trở lại `state.text`.

Trong một lịch thực thi có thể xảy ra:

| Thời điểm | Luồng 1 | Luồng 2 | Giá trị trong `state.text` |
| --- | --- | --- | --- |
| Ban đầu | — | — | `"X"` |
| 1 | Đọc `"X"` | — | `"X"` |
| 2 | — | Đọc `"X"` | `"X"` |
| 3 | Tạo và gán `"XA"` | — | `"XA"` |
| 4 | — | Tạo và gán `"XB"` từ bản đọc cũ | `"XB"` |

Kết quả cuối trong lịch trên là `"XB"`: cập nhật của luồng 1 **bị ghi đè**. Nếu thứ tự ghi đảo ngược, kết quả có thể là `"XA"`. Không có đối tượng `String` nào bị sửa đổi hoặc hỏng; lỗi nằm ở **quá trình cập nhật tham chiếu dùng chung**.

Ở đây có **data race** khi các luồng truy cập field không được đồng bộ và có ít nhất một truy cập ghi. Không nên dựa vào việc “chạy vài lần vẫn đúng” để kết luận chương trình an toàn: trình biên dịch, JVM và bộ xử lý được phép tối ưu trong phạm vi Java Memory Model khi thiếu quan hệ *happens-before* cần thiết.

## 4. Java 17+: ví dụ chạy độc lập, có kết quả xác định

Ví dụ cố ý buộc hai luồng cùng đọc giá trị cũ trước khi ghi, để lỗi lost update được tái hiện rõ ràng thay vì phụ thuộc may rủi vào lịch chạy.

Lưu thành file `ImmutableStringConcurrencyDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class ImmutableStringConcurrencyDemo {
    static final class Holder {
        String value = "X";
    }

    static void appendUnsafely(
            Holder shared, CountDownLatch bothRead, String suffix) {
        // Cả hai luồng đều đọc "X" trước bất kỳ phép ghi nào.
        String snapshot = shared.value;
        bothRead.countDown();

        try {
            bothRead.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Ghi đè bằng giá trị suy ra từ snapshot riêng.
        shared.value = snapshot + suffix;
    }

    public static void main(String[] args) throws InterruptedException {
        String original = "Hi";
        String updated = original.concat("!");
        System.out.println("original = " + original);
        System.out.println("updated = " + updated);

        Holder unsafe = new Holder();
        CountDownLatch bothRead = new CountDownLatch(2);

        Thread first = new Thread(
                () -> appendUnsafely(unsafe, bothRead, "A"));
        Thread second = new Thread(
                () -> appendUnsafely(unsafe, bothRead, "B"));

        first.start();
        second.start();
        first.join();
        second.join();

        // Giá trị là "XA" hoặc "XB", không chứa đủ hai cập nhật.
        System.out.println("unsafe length = " + unsafe.value.length());

        AtomicReference<String> safe = new AtomicReference<>("X");
        Thread third = new Thread(
                () -> safe.updateAndGet(s -> s + "A"));
        Thread fourth = new Thread(
                () -> safe.updateAndGet(s -> s + "B"));

        third.start();
        fourth.start();
        third.join();
        fourth.join();

        String result = safe.get();
        // "XAB" hoặc "XBA", luôn giữ được cả hai cập nhật.
        System.out.println("atomic length = " + result.length());
        System.out.println("contains A = " + result.contains("A"));
        System.out.println("contains B = " + result.contains("B"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ImmutableStringConcurrencyDemo.java
java ImmutableStringConcurrencyDemo
```

Đầu ra (độc lập với thứ tự cập nhật giữa các luồng, nếu không bị ngắt bất thường):

```text
original = Hi
updated = Hi!
unsafe length = 2
atomic length = 3
contains A = true
contains B = true
```

**Giải thích kết quả:**
- Lời gọi `concat("!")` tạo chuỗi `"Hi!"`, còn `original` tiếp tục là `"Hi"`.
- `CountDownLatch(2)` đảm bảo cả hai luồng đã đọc `"X"` trước khi bất kỳ luồng nào được đi tiếp đến phép gán. Vì mỗi luồng chỉ thêm một hậu tố vào cùng snapshot, giá trị cuối của `unsafe.value` có độ dài **2**, dù đã có hai lần “thêm”.
- `AtomicReference.updateAndGet` thực hiện một phép cập nhật nguyên tử theo hàm biến đổi. Nếu hai luồng cạnh tranh, một luồng có thể phải thử lại với giá trị vừa được luồng kia cập nhật. Vì vậy chuỗi cuối có độ dài **3** và chứa cả `A` lẫn `B`, tuy thứ tự `XAB` / `XBA` không xác định.
- `Thread.join()` giúp luồng `main` chờ các công việc kết thúc và nhìn thấy kết quả của chúng. `join()` **không** làm cho thao tác cập nhật dùng chung trong các luồng tự trở thành nguyên tử.
- Hàm đưa cho `updateAndGet` là `s -> s + suffix`, không có tác dụng phụ. Hàm cập nhật có thể bị gọi nhiều lần khi xảy ra cạnh tranh; đừng đặt thao tác gửi email, ghi file hoặc cập nhật hệ thống khác trong lambda này.

Đây là ví dụ minh họa **lost update**, không phải lời khuyên dùng biến chia sẻ không đồng bộ trong ứng dụng thực tế.

## 5. `volatile` giải quyết visibility, không tự giải quyết lost update

Khai báo:

```java
class Status {
    volatile String message = "starting";
}
```

Với `volatile`, lệnh ghi `message` và lần đọc `message` tương ứng có bảo đảm về khả năng nhìn thấy và thứ tự theo Java Memory Model. Các luồng đọc field `volatile` sẽ không bị giữ mãi giá trị cũ do cache/hoist theo cách vi phạm quy tắc này. Việc đọc/ghi **một tham chiếu** là thao tác nguyên tử, kể cả khi không khai báo `volatile`; điểm thêm vào của `volatile` là bảo đảm visibility và ordering.

Nếu chỉ có một luồng cập nhật thông báo còn các luồng khác đọc thông báo, mẫu dùng `volatile String` có thể phù hợp:

```java
final class Status {
    private volatile String message = "pending";

    void publish(String next) {
        message = next;
    }

    String current() {
        return message;
    }
}
```

Tuy nhiên:

```java
volatile String text = "X"; // Ví dụ một field trong lớp

void append(String suffix) {
    text = text + suffix;
}
```

**Vẫn có thể mất cập nhật.** `volatile` làm các lần đọc/ghi riêng lẻ có visibility, nhưng không gom phép **đọc → ghép → ghi** thành một giao dịch nguyên tử. Cả hai luồng vẫn có thể đọc cùng giá trị cũ rồi lần lượt ghi đè.

Ghi nhớ: **immutable** nói về trạng thái **của đối tượng**; **volatile** nói về visibility/ordering khi truy cập **field**; **atomicity** nói về tính không thể bị xen kẽ của **cả thao tác cập nhật**. Ba thuộc tính này khác nhau.

## 6. Giải pháp khi cần cập nhật chuỗi dùng chung

### 6.1. Đồng bộ bằng `synchronized`

Khi thao tác ghép cần bảo toàn mọi cập nhật, có thể bao toàn bộ đoạn đọc–tính–ghi trong cùng một khóa:

```java
final class SynchronizedText {
    private String value = "";

    public synchronized void append(String part) {
        value = value + part;
    }

    public synchronized String get() {
        return value;
    }
}
```

Hai luồng gọi `append("A")` và `append("B")` trên **cùng instance** sẽ không xen kẽ phần thân phương thức đồng bộ; chuỗi cuối chứa cả hai phần. Các phương thức `synchronized` trên cùng một đối tượng dùng chung monitor, đồng thời cung cấp quan hệ *happens-before* cho việc đọc/ghi được bảo vệ bởi monitor đó.

Nếu có nhiều field cần thay đổi nhất quán trong cùng một thao tác, `synchronized` hoặc một khóa phù hợp thường dễ biểu đạt toàn bộ bất biến hơn việc cập nhật từng field độc lập.

### 6.2. `AtomicReference<String>`

Khi cần cập nhật nguyên tử **một tham chiếu chuỗi**, không cần khóa ngoài cho toàn bộ thao tác:

```java
import java.util.concurrent.atomic.AtomicReference;

final class AtomicText {
    private final AtomicReference<String> value =
            new AtomicReference<>("");

    void append(String part) {
        value.updateAndGet(old -> old + part);
    }

    String get() {
        return value.get();
    }
}
```

`AtomicReference` sử dụng các thao tác nguyên tử như compare-and-set. Mỗi lần `updateAndGet` thành công đều được tính trên giá trị phù hợp với thứ tự cập nhật nguyên tử; khi cạnh tranh, lambda có thể chạy lại. Vì vậy **lambda nên thuần túy, không có tác dụng phụ**, và giá trị `part` nên ổn định trong phạm vi phép gọi.

Nếu nhiều tham chiếu cần cập nhật đồng thời theo một ràng buộc chung, một `AtomicReference` cho từng field **không đủ** để biến cả nhóm cập nhật thành nguyên tử. Có thể dùng khóa hoặc gom trạng thái vào một đối tượng giá trị bất biến rồi thay toàn bộ đối tượng qua một `AtomicReference<State>`.

### 6.3. Không chia sẻ trạng thái ghi được

Trong nhiều ứng dụng, giải pháp đơn giản nhất là mỗi luồng xử lý biến cục bộ riêng:

```java
String makeMessage(String name) {
    String result = "Hello, ";
    result = result + name;
    return result;
}
```

`result` là biến cục bộ mỗi lần gọi; nếu không đưa nó vào vùng trạng thái chia sẻ, các luồng không tranh chấp khi gán lại `result`. Việc dùng `String` bất biến giúp không xảy ra chỉnh sửa lén nội dung của một chuỗi đã được chuyển qua phương thức khác.

Đừng đồng bộ mọi thao tác đọc chuỗi chỉ vì chương trình đa luồng. Đồng bộ cần đặt đúng nơi **trạng thái mutable dùng chung** được đọc và ghi.

## 7. Container chứa `String` không tự trở thành thread-safe

Một tập hợp các phần tử bất biến vẫn có thể là một **đối tượng collection mutable**:

```java
import java.util.ArrayList;
import java.util.List;

List<String> names = new ArrayList<>();
names.add("An");
names.add("Bình");
```

Mỗi phần tử `String` là immutable, nhưng cấu trúc nội bộ của `ArrayList` (kích thước, mảng phần tử, việc thay đổi/duyệt) không được thiết kế cho các phép sửa đồng thời không đồng bộ. Nếu nhiều luồng cùng sửa `names`, tính bất biến của phần tử không bảo vệ cấu trúc danh sách.

Các tình huống tương tự:

| Cấu trúc | Tính chất cần nhớ | Khi chia sẻ nhiều luồng |
| --- | --- | --- |
| `ArrayList<String>` | Danh sách mutable, thao tác sửa không thread-safe theo mặc định. | Dùng khóa khi truy cập/sửa chung, hoặc chọn collection concurrent nếu bài toán phù hợp. |
| `HashMap<String, String>` | Key/value bất biến không bảo vệ bảng băm mutable. | Không sửa đồng thời mà thiếu đồng bộ; dùng `ConcurrentHashMap` cho truy cập/cập nhật concurrent. |
| `ConcurrentHashMap<String, String>` | Hỗ trợ các phép thao tác concurrent được tài liệu API bảo đảm. | Dùng `compute` / `merge` khi cần cập nhật một key dựa trên giá trị cũ. |
| `StringBuilder` | Bộ đệm ký tự mutable, **không** đồng bộ. | Dùng riêng từng luồng hoặc khóa quanh toàn bộ thao tác dùng chung. |
| `StringBuffer` | Nhiều phương thức có đồng bộ nội tại. | Một chuỗi nhiều lời gọi vẫn có thể cần đồng bộ bổ sung nếu phải nguyên tử ở mức nghiệp vụ. |
| `List.of("A", "B")` / `List.copyOf(...)` | Collection không cho phép thay đổi cấu trúc qua API collection. | Có thể chia sẻ để đọc sau khi công bố đúng cách; với phần tử mutable, đây không phải deep immutability. |

Ví dụ sai, vì hai bước `get` và `put` tách rời nhau:

```java
// map là ConcurrentHashMap<String, String>, key "log" đã tồn tại.
String old = map.get("log");
map.put("log", old + "A"); // Có thể ghi đè cập nhật của luồng khác.
```

Cách giữ phép cập nhật **một key** trong thao tác nguyên tử thích hợp:

```java
map.compute("log", (key, old) ->
        (old == null ? "" : old) + "A");
```

`ConcurrentHashMap.compute` đồng bộ logic cập nhật của key theo hợp đồng API; hãy giữ hàm ánh xạ ngắn gọn, tránh side effect và tránh thao tác đệ quy lên cùng map. Tương tự như `AtomicReference`, an toàn của thao tác collection được quyết định bởi **hợp đồng collection và cách dùng**, không chỉ bởi việc giá trị `String` bất biến.

### Wrapper và bất biến bề ngoài

`Collections.unmodifiableList(mutableList)` là **view không cho sửa qua wrapper**, nhưng danh sách gốc vẫn có thể bị sửa từ nơi khác. Wrapper không tự cung cấp đồng bộ hoặc snapshot. Ngược lại, `List.copyOf(source)` tạo một danh sách không thể sửa cấu trúc qua API và không phản ánh việc thêm/xóa ở danh sách nguồn; nó không sao chép sâu phần tử mutable.

Vì vậy “danh sách các `String` bất biến” và “danh sách bất biến” là hai nhận định khác nhau.

## 8. Công bố tham chiếu và Java Memory Model

Một tham chiếu tới `String` có thể được đọc qua nhiều luồng. Không thể từ việc chuỗi bất biến mà suy ra **mọi phép công bố hoặc thay thế tham chiếu** đều có quan hệ *happens-before*.

Ví dụ:

```java
class MessageBox {
    String latest = "initial";

    void set(String text) {
        latest = text;
    }

    String get() {
        return latest;
    }
}
```

Nếu một luồng gọi `set` trong lúc luồng khác gọi `get` mà không có đồng bộ khác, không có bảo đảm đủ mạnh rằng luồng đọc sẽ thấy **giá trị mới nhất** đúng thời điểm mong đợi. Bản thân nội dung đối tượng `String` vẫn bất biến, nhưng giao thức trao đổi cập nhật qua field `latest` còn thiếu.

Các cách tạo quan hệ đồng bộ/happens-before thường dùng:
- Dùng `volatile` khi cần công bố/thay thế **một tham chiếu** và không cần ghép cập nhật.
- Dùng khóa `synchronized`/`Lock` khi đọc và ghi một nhóm trạng thái theo bất biến chung.
- Dùng `AtomicReference` cho việc thay tham chiếu nguyên tử hoặc các phép cập nhật read–modify–write.
- Dùng collection/concurrent queue đúng hợp đồng hoặc cơ chế chuyển thông điệp nếu các luồng phải trao đổi dữ liệu.

Field `final` được khởi tạo đúng trong constructor mang lại bảo đảm đặc biệt cho quá trình khởi tạo theo Java Memory Model (giả định `this` không thoát ra trong lúc khởi tạo). Nhưng một đối tượng có field `final String` vẫn có thể chứa nhiều field mutable khác và **không tự nhiên an toàn đa luồng**.

## 9. Chọn cách thiết kế theo yêu cầu

| Yêu cầu | Hướng triển khai |
| --- | --- |
| Nhiều luồng chỉ đọc cùng một `String` đã được công bố đúng cách | Chia sẻ trực tiếp chuỗi; không cần khóa để bảo vệ nội dung bất biến. |
| Một luồng thay thông báo, nhiều luồng đọc thông báo mới | `volatile String` hoặc `AtomicReference<String>`, tùy nhu cầu API. |
| Nhiều luồng cùng “thêm” vào một biến chuỗi | `synchronized` hoặc `AtomicReference.updateAndGet`; tránh `+=` không đồng bộ. |
| Cập nhật nhiều field phải nhất quán với nhau | Khóa chung hoặc một đối tượng trạng thái bất biến được thay nguyên tử. |
| Nhiều luồng thao tác trên map chứa các `String` | `ConcurrentHashMap` với API cập nhật phù hợp; `get` rồi `put` có thể chưa đủ. |
| Ghép chuỗi dài, mỗi luồng có bộ đệm riêng | `StringBuilder` cục bộ; tạo `String` khi cần chia sẻ kết quả. |
| Cần danh sách chỉ đọc | Dùng bản sao immutable thích hợp và công bố đúng cách; phân biệt snapshot với unmodifiable view. |

**Lưu ý hiệu năng:** cập nhật chuỗi bằng `old + suffix` liên tục có thể tạo nhiều chuỗi trung gian do `String` bất biến. Với lượng nối lớn, nếu các luồng tạo dữ liệu độc lập, thường nên dùng `StringBuilder` cục bộ rồi kết hợp kết quả bằng cơ chế đồng bộ hợp lý. Đừng dùng một `StringBuilder` mutable chung mà không có đồng bộ chỉ để tránh tạo đối tượng.

## 10. Những khẳng định dễ nhầm

1. **“`String` immutable nên `shared += "x"` thread-safe.”** Sai: câu lệnh thay biến dùng chung qua nhiều bước.
2. **“Thêm `volatile` là mọi cập nhật an toàn.”** Sai: visibility không tự đảm bảo atomic read–modify–write.
3. **“`List<String>` luôn thread-safe vì phần tử là chuỗi.”** Sai: container mutable có trạng thái và quy tắc đồng bộ riêng.
4. **“`ConcurrentHashMap` khiến bất kỳ chuỗi thao tác `get` rồi `put` thành atomic.”** Sai: dùng `compute`/`merge` khi muốn cập nhật nguyên tử theo key.
5. **“Dùng `synchronized` khi đọc chuỗi là bắt buộc.”** Sai: đọc đối tượng chuỗi bất biến đã được công bố đúng cách không cần khóa riêng để bảo vệ nội dung; tuy nhiên giao thức trao đổi trạng thái xung quanh có thể cần khóa.
6. **“Có `final String` nên cả object an toàn đa luồng.”** Sai: field khác vẫn có thể mutable, và sự nhất quán giữa các field cần thiết kế riêng.

## 11. Bài tập áp dụng

**Bài 1.** Có một field dùng chung:

```java
volatile String log = "";

void add(String item) {
    log = log + item;
}
```

Hai luồng gọi `add("A")` và `add("B")`. Có bảo đảm `log` chứa cả hai phần không?

**Đáp án:** Không. Mỗi luồng có thể đọc cùng snapshot cũ trước khi gán; `volatile` không tạo atomicity cho cả phép ghép. Có thể thay bằng `AtomicReference<String>.updateAndGet` hoặc đồng bộ hóa toàn bộ thao tác.

**Bài 2.** Một dịch vụ công bố chuỗi trạng thái mới mà không cần ghép với trạng thái cũ; một luồng ghi, nhiều luồng đọc. Có cần dùng `AtomicReference.updateAndGet` không?

**Đáp án:** Không nhất thiết. Một field `volatile String status` cùng cách đọc/ghi đơn giản đã phù hợp với nhiều trường hợp công bố trạng thái độc lập. Nếu phải bảo đảm chuyển đổi có điều kiện dựa trên giá trị cũ, nên cân nhắc cơ chế atomic/locking.

**Bài 3.** Một `HashMap<String,String>` có key và value đều là `String`. Có thể vừa thay map từ nhiều luồng vừa duyệt trực tiếp không đồng bộ?

**Đáp án:** Không. Chuỗi bất biến không bảo vệ cấu trúc `HashMap` khi sửa đồng thời. Chọn `ConcurrentHashMap` hoặc đồng bộ ngoại vi theo nhu cầu nhất quán của thao tác.

**Bài 4.** Khi cần đọc một chuỗi đã tạo và chia sẻ qua thread-safe queue, phải khóa riêng cho `charAt` và `length` của chuỗi đó không?

**Đáp án:** Không cần khóa riêng để bảo vệ nội dung immutable của `String`. Việc chuyển chuỗi qua queue có hợp đồng concurrent thích hợp cung cấp cơ chế công bố/chuyển giao; còn các trạng thái mutable khác phải xét riêng.

## Tài liệu tham khảo

- [Java SE 17: `String`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17: `AtomicReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html)
- [Java SE 17: `ConcurrentHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [Java Language Specification §17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html)


</details>

- [Quay lại đầu trang](#back-to-top)
