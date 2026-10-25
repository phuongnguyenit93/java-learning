<a id="back-to-top"></a>

# Nối chuỗi

## Menu
- [Toán tử + và phép chuyển giá trị thành chuỗi](#string-concatenation-001)
- [Thứ tự kết hợp trái–phải của + với số và chuỗi](#string-concatenation-002)
- [Phân biệt ký tự cộng số với nối chuỗi](#string-concatenation-003)
- [concat và khác biệt về đối số null](#string-concatenation-004)
- [+= là tạo kết quả rồi gán lại tham chiếu](#string-concatenation-005)
- [Chuyển Object qua toString khi tham gia nối chuỗi](#string-concatenation-006)
- [Nối trong một biểu thức so với tích lũy qua vòng lặp](#string-concatenation-007)
- [Chi tiết biên dịch phép nối có thể thay đổi theo JDK](#string-concatenation-008)

## <a id="string-concatenation-001">Toán tử + và phép chuyển giá trị thành chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử `+` và phép chuyển giá trị thành chuỗi trong Java

## 1. Khi nào `+` là phép nối chuỗi?

Trong Java, ký hiệu `+` được dùng cho hai mục đích: **cộng số** và **nối chuỗi** (*string concatenation*). Khi một toán hạng của phép `+` có kiểu `String`, phép toán đó tạo giá trị `String` bằng cách nối biểu diễn văn bản của hai toán hạng theo thứ tự từ trái sang phải.

```java
String language = "Java";
int version = 17;

String message = language + " " + version;
System.out.println(message); // Java 17
```

Ở ví dụ này, `version` là một số nguyên `int` nhưng được chuyển thành văn bản `"17"` khi tham gia phép nối với `String`. Kết quả cuối cùng là một chuỗi, không phải phép cộng số học.

Cần phân biệt ba khái niệm:

- **Đối tượng `String`:** đại diện cho văn bản trong Java; nội dung không thể sửa tại chỗ.
- **Toán tử `+`:** tạo biểu thức nối chuỗi khi có toán hạng kiểu `String`; cũng có thể cộng số nếu các toán hạng phù hợp là kiểu số.
- **Chuyển thành chuỗi:** tạo biểu diễn văn bản của giá trị `int`, `double`, `boolean`, `char`, đối tượng hoặc `null` để tham gia phép nối.

Các đoạn mã ngắn trong bài được hiểu là viết **trong một phương thức Java**, trừ chương trình hoàn chỉnh có `class` và `main` ở phần sau.

## 2. Quy tắc phân biệt cộng số với nối chuỗi

### Trường hợp 1: cả hai toán hạng là số

```java
int result = 2 + 3;          // 5
double total = 2.5 + 1.5;   // 4.0
```

Đây là cộng số học. Các kiểu số nguyên thủy có thể trải qua **numeric promotion** theo quy tắc Java. Chưa xuất hiện `String`, nên chưa có nối chuỗi.

### Trường hợp 2: có một toán hạng là `String`

```java
String one = "Java" + 17;     // "Java17"
String two = 17 + "Java";     // "17Java"
String three = "score=" + 5; // "score=5"
```

Java thực hiện **string conversion** đối với toán hạng còn lại. Phép nối giữ nguyên thứ tự: `"Java" + 17` khác `17 + "Java"`.

Một giá trị số đặt trong dấu nháy kép đã là chuỗi:

```java
String digits = "17"; // String
int number = 17;      // int
```

`"17" + 3` trả `"173"`, trong khi `17 + 3` trả số `20`.

### Trường hợp 3: không phải mọi kiểu đều dùng được với `+`

Ví dụ hai đối tượng tùy ý không thể cộng với nhau chỉ vì chúng có phương thức `toString()`:

```java
Object a = new Object();
Object b = new Object();

// Object invalid = a + b; // Không biên dịch
String valid = "objects: " + a + ", " + b;
```

Ở dòng hợp lệ, các phép nối đều có toán hạng `String`. Java sẽ chuyển đối tượng thành biểu diễn văn bản; biểu diễn mặc định của `Object` thường là tên lớp kèm mã hash ở dạng hex, không phải mô tả có ý nghĩa của dữ liệu nghiệp vụ.

## 3. Thứ tự kết hợp từ trái sang phải: bẫy `"1" + 2 + 3`

Các phép `+` cùng độ ưu tiên kết hợp trái: `1 + 2 + "3"` tạo `"33"`, còn `"1" + 2 + 3` tạo `"123"`. Dấu ngoặc cho biết rõ phép cộng số nào phải làm trước:

```java
System.out.println("sum=" + 2 + 3);   // sum=23
System.out.println("sum=" + (2 + 3)); // sum=5
```

Phép nhân vẫn có độ ưu tiên cao hơn `+`. Bài **Thứ tự kết hợp trái–phải của + với số và chuỗi** triển khai bảng phân tích biểu thức, ví dụ có tác dụng phụ và bẫy tràn số; ở đây chỉ cần biết lúc nào phép `+` đang trả về số hay `String`.

## 4. Java chuyển các giá trị thành chuỗi như thế nào?

Đối với phép nối `String`, Java chuyển các toán hạng còn lại theo **quy tắc string conversion của ngôn ngữ**. Bảng dưới đây nêu các trường hợp thường gặp.

| Giá trị tham gia phép nối | Biểu diễn văn bản điển hình | Ví dụ |
|---|---|---|
| `int` | Chữ số thập phân có thể kèm dấu âm | `"n=" + 42` → `"n=42"` |
| `long` | Chữ số của số nguyên dài | `"n=" + 42L` → `"n=42"` |
| `double` | Biểu diễn thập phân của số dấu phẩy động | `"x=" + 2.5` → `"x=2.5"` |
| `boolean` | `"true"` hoặc `"false"` | `"ok=" + true` → `"ok=true"` |
| `char` | Ký tự được biểu diễn bởi giá trị `char` | `"c=" + 'A'` → `"c=A"` |
| `String` | Nội dung chuỗi hiện có | `"A" + "B"` → `"AB"` |
| Tham chiếu `null` | Văn bản `"null"` | `"x=" + (String) null` → `"x=null"` |
| Đối tượng thông thường | Dựa trên biểu diễn `toString()` | Có thể được tùy biến trong lớp |

Với các giá trị số nguyên, việc nối chuỗi **không làm phép cộng số** một lần nữa. Java chỉ lấy biểu diễn văn bản của giá trị ở thời điểm cần nối.

Đối với `float`/`double`, biểu diễn mặc định của toán tử nối không phải công cụ định dạng số dành cho người dùng theo vùng miền. Nếu cần dấu phân cách hàng nghìn, làm tròn theo số chữ số cố định hoặc quy tắc locale, hãy sử dụng API định dạng như `String.format(...)`, `NumberFormat` hoặc `DecimalFormat` theo yêu cầu cụ thể.

### Đối tượng: vai trò của `toString()`

Với một đối tượng không phải `null`, quy tắc string conversion có thể dựa trên phương thức `toString()` của đối tượng đó. Một lớp thường ghi đè `toString()` để cung cấp biểu diễn có ý nghĩa:

```java
class Book {
    private final String title;

    Book(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return "Book(" + title + ")";
    }
}
```

Khi dùng trong phương thức của lớp khác:

```java
Book book = new Book("Java");
System.out.println("item=" + book);
// item=Book(Java)
```

`toString()` là mô tả văn bản của đối tượng, không nhất thiết là dữ liệu đã được chuẩn hóa, không phải định dạng trao đổi JSON, và không nên mặc nhiên bao gồm thông tin nhạy cảm. Trong hệ thống thực tế, thiết kế `toString()` theo mục đích gỡ lỗi hoặc hiển thị phù hợp.

## 5. `null` trong phép nối chuỗi: không giống gọi phương thức trên `null`

Một tham chiếu `String` có thể mang giá trị `null`:

```java
String name = null;

System.out.println("name=" + name); // name=null
```

Phép nối biến `name` đang là `null` thành văn bản `"null"`. Kết quả là chuỗi thông thường `"name=null"`; đây **không phải** đối tượng `null`.

Tuy nhiên, điều đó không có nghĩa có thể gọi phương thức lên tham chiếu `null`:

```java
String name = null;

// name.concat("!"); // NullPointerException
// name.length();     // NullPointerException

System.out.println("name=" + name); // Hợp lệ
```

Cần phân biệt:

- **String conversion trong phép `+`:** có quy tắc riêng để biểu diễn tham chiếu `null` thành `"null"`.
- **Lời gọi phương thức `name.concat(...)`:** yêu cầu `name` tham chiếu đến đối tượng `String`; nếu `null`, ném `NullPointerException`.

Nếu `"null"` là đầu ra không mong muốn, phải xác định cách xử lý thiếu dữ liệu:

```java
String name = null;
String display = name == null ? "(chưa có)" : name;
System.out.println("Tên: " + display);
// Tên: (chưa có)
```

Đây là chính sách hiển thị do chương trình quyết định. Đừng nhầm chuỗi bốn ký tự `"null"` với tham chiếu `null` thật.

## 6. `char` có thể là ký tự hoặc tham gia phép cộng số

`char` là đơn vị mã UTF-16 có thể tham gia cộng số sau numeric promotion; khi nằm cạnh một `String`, nó được nối như văn bản:

```java
System.out.println('A' + 1);       // 66 (int)
System.out.println("A" + 1);       // A1
System.out.println("" + 'A' + 1);  // A1
```

Phép cộng mã của `char` không phải cách xử lý mọi Unicode code point. Bài **Phân biệt ký tự cộng số với nối chuỗi** phân tích chuyển kiểu, ký tự chữ số, dấu ngoặc và surrogate pair.

## 7. Các mảng không tự được nối theo từng phần tử

Giả sử có mảng số:

```java
int[] numbers = {1, 2, 3};
```

Biểu thức `"numbers=" + numbers` không cho ra `"numbers=[1, 2, 3]"` theo quy tắc chung. Mảng là một đối tượng; nếu không được chuyển thành mô tả riêng, biểu diễn mặc định thường chứa thông tin kiểu mảng và mã hash, nên không thích hợp làm đầu ra mong muốn. Phần mã hash có thể khác giữa các lượt chạy.

Hãy chọn API phù hợp với ý định:

```java
int[] numbers = {1, 2, 3};
String readable = "numbers=" + java.util.Arrays.toString(numbers);

System.out.println(readable); // numbers=[1, 2, 3]
```

Với mảng đa chiều, có thể cần `Arrays.deepToString(...)` để biểu diễn các mảng lồng nhau.

### Trường hợp `char[]` cũng cần chú ý

```java
char[] letters = {'J', 'a', 'v', 'a'};

String actualText = new String(letters);
System.out.println("letters=" + actualText); // letters=Java
```

Không nên giả định toán tử `+` sẽ tự ghép các phần tử của `char[]` theo cách `new String(letters)` làm. Có các overload của `String.valueOf` dành riêng cho `char[]`, nhưng phép nối qua tham chiếu mảng không phải một lời gọi tùy ý tới overload ấy.

## 8. Ví dụ Java 17+ hoàn chỉnh và stdout

Lưu vào tệp **`StringPlusConversionDemo.java`**:

```java
import java.util.Arrays;

public class StringPlusConversionDemo {
    public static void main(String[] args) {
        String language = "Java";
        int release = 17;
        double rating = 4.5;
        boolean supported = true;

        String summary = "summary=" + language + " " + release
                + ", rating=" + rating + ", supported=" + supported;
        System.out.println(summary);

        System.out.println("left=" + 2 + 3);
        System.out.println("right=" + (2 + 3));
        System.out.println(2 + 3 + " points");
        System.out.println("value=" + 2 + 3 * 4);

        char letter = 'A';
        System.out.println("letter=" + letter + 1);
        System.out.println("code=" + (letter + 1));
        System.out.println("next=" + (char) (letter + 1));

        String missing = null;
        System.out.println("missing=" + missing);
        System.out.println("valueOfMissing=" + String.valueOf(missing));

        int[] numbers = {1, 2, 3};
        System.out.println("numbers=" + Arrays.toString(numbers));

        char[] letters = {'J', 'a', 'v', 'a'};
        System.out.println("letters=" + new String(letters));

        System.out.println("joined=" + String.join(" | ", "A", "B", "C"));

        String source = "Hi";
        String modified = source + "!";
        System.out.println("source=" + source + ", modified=" + modified);

        String folded = "Ja" + "va";
        System.out.println("foldedSameLiteral=" + (folded == "Java"));
    }
}
```

Chạy bằng **JDK 17 trở lên** trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 StringPlusConversionDemo.java
java StringPlusConversionDemo
```

**Stdout chính xác:**

```text
summary=Java 17, rating=4.5, supported=true
left=23
right=5
5 points
value=212
letter=A1
code=66
next=B
missing=null
valueOfMissing=null
numbers=[1, 2, 3]
letters=Java
joined=A | B | C
source=Hi, modified=Hi!
foldedSameLiteral=true
```

Lý giải các kết quả trọng tâm:

1. `summary` chuyển `int`, `double`, `boolean` sang văn bản và nối theo thứ tự.
2. `"left=" + 2 + 3` nối lần lượt thành `"left=23"`, còn `"right=" + (2 + 3)` cộng số trong ngoặc trước, được `"right=5"`.
3. `2 + 3 + " points"` cộng hai số đầu thành `5` rồi nối `" points"`.
4. Phép nhân trong `"value=" + 2 + 3 * 4` có độ ưu tiên cao hơn `+`, nên `3 * 4 = 12`, cuối cùng tạo `"value=212"`.
5. Ký tự `'A'` cộng `1` cho giá trị số `66`, nhưng tham gia nối từ trái sang phải sau một chuỗi cho văn bản `"A1"`.
6. Tham chiếu `missing` là `null`; phép nối và `String.valueOf(missing)` đều có biểu diễn `"null"` trong trường hợp này.
7. `Arrays.toString` biểu diễn giá trị các phần tử mảng; `new String(letters)` tạo văn bản từ mảng `char`.
8. `source + "!"` tạo kết quả được lưu ở `modified`, không sửa nội dung của `source`.
9. `"Ja" + "va"` là **biểu thức hằng chuỗi** được xác định tại thời điểm biên dịch; giá trị chuỗi hằng được intern nên có cùng tham chiếu với literal `"Java"`.

## 9. `String.valueOf(...)`: chuyển giá trị thành chuỗi một cách tường minh

Khi cần tự chuyển một giá trị sang `String` trước khi xử lý tiếp, hãy dùng các overload `String.valueOf(...)` thích hợp.

```java
String number = String.valueOf(42);       // "42"
String yes = String.valueOf(true);        // "true"
String letter = String.valueOf('A');      // "A"
String pi = String.valueOf(3.5);          // "3.5"

Object missing = null;
String absent = String.valueOf(missing);  // "null"
```

Điểm đáng chú ý: `String.valueOf` có nhiều overload như `int`, `long`, `double`, `boolean`, `char`, `char[]` và `Object`. Trình biên dịch lựa chọn overload dựa trên **kiểu biểu thức đối số**.

Ví dụ khác biệt quan trọng với mảng ký tự:

```java
char[] chars = {'J', 'a', 'v', 'a'};

String a = String.valueOf(chars);          // "Java" (overload char[])
String b = String.valueOf((Object) chars); // Biểu diễn đối tượng mảng
```

`b` không được đảm bảo là `"Java"`; nó đi theo overload `Object` và có thể chứa kiểu `[C` cùng mã hash.

**Cẩn thận với `String.valueOf(null)` không có kiểu tường minh.** Vì có nhiều overload nhận kiểu tham chiếu, truyền literal `null` trần có thể chọn overload khác với ý định, đặc biệt là overload `char[]`. Khi cần biểu diễn tham chiếu đối tượng `null` thành `"null"`, hãy dùng đối số có kiểu rõ ràng:

```java
String value = String.valueOf((Object) null); // "null"
```

Nếu đối số thực sự là mảng `char[]` bằng `null`, gọi overload `valueOf(char[])` có thể ném `NullPointerException`. Vì thế kiểu tĩnh của đối số ảnh hưởng đáng kể đến lời gọi `String.valueOf`, dù toán tử `+` thường được dùng thuận tiện cho việc nối văn bản.

### Vì sao đôi khi dùng `String.valueOf` thay cho `+`?

Hai mục đích khác nhau:

- **Tạo một câu văn mới:** `"Số lượng: " + count` diễn đạt trực tiếp và dễ đọc.
- **Chuyển một giá trị thành chuỗi riêng biệt:** `String.valueOf(count)` làm rõ bạn cần một `String` để truyền cho API khác hoặc lưu trước.

Không cần chủ động viết `String.valueOf` cho **mọi** toán hạng khi đã dùng phép nối: Java vốn có quy tắc string conversion cho toán tử `+`.

## 10. Chuyển chuỗi thành số là hướng ngược lại

Khi `String` chứa chữ số, dùng toán tử `+` với số **không** tự phân tích chuỗi thành số:

```java
String text = "20";
System.out.println(text + 3); // 203
```

Muốn thực hiện phép cộng số học, phải chuyển văn bản bằng API phân tích dữ liệu:

```java
String text = "20";
int number = Integer.parseInt(text);

System.out.println(number + 3); // 23
```

Nếu văn bản không có định dạng số hợp lệ, `Integer.parseInt(...)` có thể ném `NumberFormatException`:

```java
// Integer.parseInt("twenty"); // NumberFormatException
```

Phân biệt rõ hai hướng:

| Hướng | API / thao tác | Ví dụ |
|---|---|---|
| **Giá trị → chuỗi** | Toán tử `+` với `String`, `String.valueOf(...)` | `42` → `"42"` |
| **Chuỗi → số** | `Integer.parseInt`, `Double.parseDouble`, ... | `"42"` → `42` |

Tự thêm `""` bằng `number + ""` có thể chuyển số thành chuỗi, nhưng `String.valueOf(number)` thể hiện rõ ý định hơn khi chỉ cần chuyển đổi. Đừng dùng `Integer.parseInt` chỉ để nối số với chữ.

## 11. `+=` và tính bất biến của `String`

`+=` trên biến `String` tạo giá trị nối rồi gán lại biến; chuỗi nguồn không bị sửa:

```java
String first = "Hi";
String alias = first;
first += "!";
System.out.println(alias); // Hi
```

Biến `final String` không thể dùng `+=` vì không được gán lại. Xem bài **+= là tạo kết quả rồi gán lại tham chiếu** để phân tích vế trái phức tạp và sự khác nhau giữa `s += 1 + 2` với `s = s + 1 + 2`.

## 12. Biểu thức hằng và phép nối lúc chạy

Biểu thức `"Ja" + "va"` là một **constant expression**, được xác định lúc biên dịch và có giá trị hằng đã intern. Ngược lại, khi kết quả phụ thuộc biến không phải constant variable, phép nối diễn ra lúc chạy. Dùng `equals()` để so nội dung; không dựa vào `==` dù một số kết quả hằng có cùng tham chiếu. Chi tiết bytecode thuộc bài **Chi tiết biên dịch phép nối có thể thay đổi theo JDK**.

## 13. Hiệu năng: khi nào dùng `+`, khi nào dùng `StringBuilder`?

Với một biểu thức ngắn như `"Hello, " + name`, `+` dễ đọc và Java hiện đại có thể tối ưu. Khi tích lũy liên tục qua vòng lặp, `StringBuilder.append(...)` thường phù hợp hơn vì tránh tạo lại tiền tố đã tích lũy ở mỗi lượt. Để nối danh sách có dấu phân cách, ưu tiên `String.join()` nếu thích hợp. Bài **Nối trong một biểu thức so với tích lũy qua vòng lặp** hướng dẫn chi tiết cách chọn API và kiểm chứng chi phí.

## 14. Thứ tự đánh giá và tác dụng phụ

Các toán hạng Java được đánh giá theo thứ tự từ trái sang phải, kể cả khi phép nối được tối ưu. Ví dụ:

```java
int n = 1;
String text = "n=" + n++ + ", after=" + n;
System.out.println(text); // n=1, after=2
```

Hạn chế tác dụng phụ trong biểu thức dài. Bài về **Thứ tự kết hợp trái–phải** phân biệt tính kết hợp, độ ưu tiên và thứ tự đánh giá.

## 15. Những lỗi thường gặp

| Ngộ nhận | Cách kiểm tra đúng |
|---|---|
| `"2" + 3` là phép cộng số | Kết quả là `"23"`; dùng `Integer.parseInt` khi muốn cộng số |
| `'A' + 1` tạo `"A1"` | Kết quả `int` là `66` |
| Mảng tự liệt kê phần tử khi nối | Dùng `Arrays.toString(...)` |
| `s + "B";` là statement hợp lệ | Phải sử dụng/gán kết quả; biểu thức `+` riêng lẻ không phải statement expression |
| Nối `null` và gọi method trên `null` giống nhau | `"x=" + ref` có thể in `"null"`; `ref.concat(...)` có thể ném NPE |

Điểm chung: cần xác định **kiểu của phép toán đang xét**, cách chuyển đổi và hợp đồng của API.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `"n=" + 2 + 3` và `"n=" + (2 + 3)` khác nhau thế nào? **Đáp án:** `"n=23"` so với `"n=5"`.

**Câu 2.** `"values=" + numbers` (với `numbers` là `int[]`) có liệt kê các phần tử không? **Đáp án:** Không; dùng `Arrays.toString(numbers)`.

**Câu 3.** `String.valueOf((Object) null)` cho gì? **Đáp án:** `"null"`; không tự áp dụng điều này cho overload `char[]` với đối số null.

**Câu 4.** Vì sao `"Hi" + name` thường đủ tốt cho lời chào ngắn? **Đáp án:** diễn đạt rõ ý định; chỉ cân nhắc builder cho quá trình tích lũy qua nhiều lượt.

## 17. Ghi nhớ

Toán tử `+` trong Java chỉ là **nối chuỗi** khi có toán hạng kiểu `String` ở phép toán đang xét; nếu cả hai là các toán hạng số phù hợp, đó là **cộng số**. Khi nối chuỗi, Java chuyển các giá trị thành văn bản theo quy tắc string conversion: số thành chữ số, `boolean` thành `"true"`/`"false"`, `char` thành ký tự tương ứng, tham chiếu `null` thành `"null"`, và đối tượng thông thường dựa vào biểu diễn văn bản của nó.

Chú ý **kết hợp trái**, **độ ưu tiên toán tử** và dấu ngoặc: `"sum=" + 2 + 3` ra `"sum=23"`, còn `"sum=" + (2 + 3)` ra `"sum=5"`. Sử dụng `String.valueOf` khi cần chuyển đổi tường minh, `Arrays.toString` cho mảng, `String.join` cho dữ liệu có dấu phân cách và `StringBuilder` khi xây dựng chuỗi nhiều bước. Bởi vì `String` bất biến, mỗi phép nối tạo giá trị kết quả để lưu hoặc sử dụng, không chỉnh sửa nội dung đối tượng chuỗi ban đầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-concatenation-002">Thứ tự kết hợp trái–phải của + với số và chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự kết hợp trái–phải của toán tử `+` với số và chuỗi trong Java

## 1. Mục tiêu bài học

Toán tử `+` trong Java có hai vai trò quan trọng:

- **Cộng số** khi các toán hạng thuộc loại số và phép cộng số được áp dụng.
- **Nối chuỗi** khi một trong hai toán hạng của phép `+` là `String`.

Các phép `+` đứng liên tiếp ở cùng mức ưu tiên có **tính kết hợp trái** (*left associativity*): Java phân nhóm từ trái sang phải, nếu không có dấu ngoặc hoặc toán tử có mức ưu tiên cao hơn làm thay đổi cách nhóm.

Vì vậy:

```java
System.out.println(1 + 2 + "3");  // 33
System.out.println("1" + 2 + 3);  // 123
```

Hai biểu thức chứa các ký hiệu tương tự nhưng kết quả khác nhau. Điều cần nắm là **mỗi dấu `+` được xử lý dựa trên kiểu kết quả ở vị trí đó trong biểu thức**, chứ không phải cứ nhìn thấy một `String` ở bất kỳ đâu là toàn bộ phép `+` tự động biến thành phép nối.

Sau bài này bạn sẽ:
1. Đọc đúng phép `+` theo thứ tự kết hợp trái–phải.
2. Phân biệt cộng số trước khi nối chuỗi và nối chuỗi ngay từ đầu.
3. Sử dụng dấu ngoặc để chủ động tạo kết quả mong muốn.
4. Tránh nhầm tính kết hợp với **độ ưu tiên toán tử** hoặc **thứ tự đánh giá toán hạng**.
5. Nhận biết các trường hợp dễ sai với `char`, `null`, `+=`, kiểu số và tràn số.

## 2. Quy tắc cốt lõi: một phép `+`, hai ngữ nghĩa

Trong biểu thức Java, phép cộng số áp dụng cho các toán hạng kiểu số phù hợp. Khi **một toán hạng của phép `+` là `String`**, phép toán đó là **string concatenation** (nối chuỗi). Giá trị còn lại được chuyển thành dạng chuỗi theo quy tắc chuyển đổi khi nối chuỗi.

Ví dụ:

| Biểu thức | Ý nghĩa tại dấu `+` | Kết quả |
| --- | --- | --- |
| `1 + 2` | Cộng hai số `int` | Số `3` |
| `1 + 2.5` | Cộng số, có chuyển đổi kiểu số | Số `3.5` |
| `"1" + 2` | Nối chuỗi `"1"` và số `2` | Chuỗi `"12"` |
| `1 + "2"` | Nối chuỗi `"1"` và `"2"` | Chuỗi `"12"` |
| `"Hi " + "Java"` | Nối hai chuỗi | Chuỗi `"Hi Java"` |

Khi một phép `+` đã tạo ra `String`, nếu kết quả đó lại đứng ở bên trái một phép `+` tiếp theo, phép tiếp theo thường tiếp tục **nối chuỗi**.

```java
String text = "A" + 1;      // "A1"
String longer = text + 2;   // "A12"
```

Điểm cần lưu ý: `"A" + 1 + 2` không cộng `1 + 2` trước. Các phép `+` cùng mức sẽ được nhóm từ trái sang phải, trừ khi dấu ngoặc hoặc toán tử ưu tiên cao hơn thay đổi việc nhóm.

## 3. Tính kết hợp trái: đọc từng bước

Xét hai biểu thức:

### Trường hợp A: cộng số trước rồi nối chuỗi

```java
String a = 1 + 2 + "3";
```

Với tính kết hợp trái, cách nhóm là:

```text
(1 + 2) + "3"
   3     + "3"
       "33"
```

Ở phép `+` đầu tiên, cả hai toán hạng đều là số nguyên nên kết quả là **số 3**. Phép tiếp theo có `String "3"` nên cho kết quả **chuỗi "33"**.

### Trường hợp B: nối chuỗi ngay từ đầu

```java
String b = "1" + 2 + 3;
```

Cách nhóm:

```text
("1" + 2) + 3
    "12"   + 3
       "123"
```

Dấu `+` đầu tiên tạo ra `String "12"`. Dấu `+` tiếp theo nối tiếp số `3` thành chuỗi `"123"`, **không** cộng `2 + 3`.

### Trường hợp C: chuỗi nằm ở giữa

```java
String c = 1 + "2" + 3;
```

Cách nhóm:

```text
(1 + "2") + 3
    "12"    + 3
        "123"
```

Bởi phép đầu tiên đã có toán hạng kiểu `String`, nó nối chuỗi ngay từ đầu. Kết quả cuối cũng là `"123"`, mặc dù `1` và `3` là số.

Bảng so sánh:

| Biểu thức | Cách hiểu | Kết quả chuỗi |
| --- | --- | --- |
| `1 + 2 + "3"` | `(1 + 2) + "3"` | `"33"` |
| `"1" + 2 + 3` | `("1" + 2) + 3` | `"123"` |
| `1 + "2" + 3` | `(1 + "2") + 3` | `"123"` |
| `1 + 2 + "3" + 4` | `((1 + 2) + "3") + 4` | `"334"` |
| `"1" + 2 + 3 + 4` | `((("1" + 2) + 3) + 4)` | `"1234"` |

**Cách dự đoán:** đi từ trái sang phải qua các phép `+` cùng mức, luôn theo dõi kiểu của **kết quả trung gian**: còn là số thì có thể tiếp tục cộng số; đã trở thành `String` thì phép `+` với giá trị tiếp theo nối chuỗi.

## 4. Dấu ngoặc quyết định phép cộng số

Muốn hiển thị **tổng** của hai số trong một chuỗi, hãy dùng dấu ngoặc:

```java
int a = 10;
int b = 20;

System.out.println("Tổng = " + a + b);     // Tổng = 1020
System.out.println("Tổng = " + (a + b));   // Tổng = 30
```

Biểu thức `"Tổng = " + a + b` được hiểu như `("Tổng = " + a) + b`, nên các số được chuyển lần lượt sang chuỗi.

Trong `"Tổng = " + (a + b)`, Java đánh giá `a + b` trước, tạo ra số `30`, sau đó nối số này vào chuỗi.

Dấu ngoặc cũng có thể tạo kết quả khác trong ví dụ ban đầu:

```java
System.out.println(1 + (2 + "3")); // 123
System.out.println("1" + (2 + 3)); // 15
```

Giải thích:
- `2 + "3"` → `"23"`, rồi `1 + "23"` → `"123"`.
- `2 + 3` → số `5`, rồi `"1" + 5` → `"15"`.

**Quy tắc thực hành:** khi muốn nối nhãn và kết quả một biểu thức số học, hãy dùng `"Nhãn: " + (biểuThứcSố)`. Đây là cách viết rõ nghĩa và tránh sai số do đọc nhầm chuỗi phép `+`.

## 5. Độ ưu tiên toán tử khác với tính kết hợp

Tính kết hợp trái áp dụng khi các toán tử `+` có **cùng mức ưu tiên**. Nó không vượt qua độ ưu tiên của toán tử khác.

Phép `*` có độ ưu tiên cao hơn `+`:

```java
System.out.println(1 + 2 * 3 + "4");  // 74
System.out.println("1" + 2 * 3);      // 16
```

Trong dòng đầu:
1. `2 * 3` → số `6`.
2. `1 + 6` → số `7`.
3. `7 + "4"` → chuỗi `"74"`.

Trong dòng hai, `2 * 3` → số `6`, rồi `"1" + 6` → chuỗi `"16"`.

Dấu ngoặc có thể thay đổi điều này:

```java
System.out.println((1 + 2) * 3 + "4"); // 94
System.out.println("1" + (2 + 3));     // 15
```

Điều này có nghĩa là không thể dùng quy tắc “đọc toàn bộ biểu thức bằng thứ tự xuất hiện” khi còn `*`, `/`, dấu ngoặc hoặc toán tử khác.

### Lưu ý với toán tử trừ

`+` có thể là phép nối chuỗi, nhưng `-` **không** có phiên bản tương tự dành cho `String`:

```java
// Không biên dịch:
// System.out.println("Kết quả: " + 10 - 3);
```

Java nhóm từ trái sang phải: `("Kết quả: " + 10) - 3`. Sau phép `+` đầu tiên, vế trái của `-` là `String`, nên phép trừ không hợp lệ.

Viết đúng:

```java
System.out.println("Kết quả: " + (10 - 3)); // Kết quả: 7
```

Dấu ngoặc vừa thể hiện ý định, vừa ngăn kiểu `String` xuất hiện quá sớm trong phép tính.

## 6. Phân biệt tính kết hợp với thứ tự đánh giá toán hạng

Hai khái niệm liên quan nhưng **không giống nhau**:

- **Associativity (tính kết hợp)** cho biết cách nhóm các toán tử cùng mức ưu tiên: `a + b + c` được nhóm như `(a + b) + c`.
- **Evaluation order (thứ tự đánh giá)** cho biết biểu thức con nào được đánh giá trước khi tính toán. Java bảo đảm đánh giá các toán hạng của toán tử nhị phân theo thứ tự từ trái sang phải.

Ví dụ:

```java
static int first() {
    System.out.print("A ");
    return 1;
}

static int second() {
    System.out.print("B ");
    return 2;
}

static String third() {
    System.out.print("C ");
    return "3";
}
```

Khi gọi trong một phương thức:

```java
String result = first() + second() + third();
System.out.println(result);
```

Kết quả:

```text
A B C 33
```

Ba phương thức được gọi theo thứ tự trái sang phải: `first`, `second`, `third`. Sau đó giá trị trả về tạo thành `1 + 2 + "3"` → `"33"`.

Chú ý: dù trình biên dịch hoặc JVM có thể tối ưu cách triển khai nối chuỗi, chương trình vẫn phải tuân thủ **hành vi quan sát được** của Java, bao gồm thứ tự và ngoại lệ của các biểu thức con theo đặc tả.

### Dấu ngoặc có thể thay đổi cách nhóm nhưng không có nghĩa mọi thứ đều đổi thứ tự gọi

`a + (b + c)` làm phép `b + c` được nhóm trước phép `+` bên ngoài, nhưng Java vẫn đánh giá toán hạng `a` trước khi đánh giá toán hạng bên phải `(b + c)`. Không nên nhầm **thứ tự phép toán được nhóm** với **thứ tự gọi hàm có tác dụng phụ**.

## 7. Chương trình Java 17+ chạy độc lập

Lưu thành file `PlusAssociativityDemo.java`:

```java
public class PlusAssociativityDemo {
    static int first() {
        System.out.print("A ");
        return 1;
    }

    static int second() {
        System.out.print("B ");
        return 2;
    }

    static String third() {
        System.out.print("C ");
        return "3";
    }

    public static void main(String[] args) {
        System.out.println("A = " + (1 + 2 + "3"));
        System.out.println("B = " + ("1" + 2 + 3));
        System.out.println("C = " + (1 + "2" + 3));

        System.out.println("sum wrong = " + 10 + 20);
        System.out.println("sum correct = " + (10 + 20));

        System.out.println("parentheses = "
                + ("1" + (2 + 3)));
        System.out.println("precedence = "
                + (1 + 2 * 3 + "4"));

        System.out.println("char as number = " + ('A' + 1));
        System.out.println("char in string = " + ('A' + "1"));

        String text = "x";
        text += 1 + 2;
        System.out.println("compound = " + text);

        String other = "x";
        other = other + 1 + 2;
        System.out.println("normal = " + other);

        System.out.print("evaluation = ");
        String evaluation = first() + second() + third();
        System.out.println(evaluation);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 PlusAssociativityDemo.java
java PlusAssociativityDemo
```

Kết quả chuẩn:

```text
A = 33
B = 123
C = 123
sum wrong = 1020
sum correct = 30
parentheses = 15
precedence = 74
char as number = 66
char in string = A1
compound = x3
normal = x12
evaluation = A B C 33
```

**Giải thích ba điểm dễ nhầm trong chương trình:**

1. `'A' + 1` là **cộng số**, vì `char` có thể được nâng kiểu sang `int` trong phép tính số: mã của `'A'` là `65` nên kết quả `66`. Còn `'A' + "1"` là nối chuỗi `"A1"`.
2. `text += 1 + 2` tạo `"x3"`, bởi biểu thức phía phải `1 + 2` được tính thành `3` trước khi nối với giá trị cũ của `text` trong phép gán kết hợp. Nhưng `other = other + 1 + 2` có các phép `+` cùng chuỗi bên phải, kết hợp trái thành `"x12"`.
3. `first()`, `second()`, `third()` đều được gọi theo thứ tự từ trái sang phải, rồi giá trị trả về cho kết quả `"33"`.

## 8. Trường hợp riêng: `char` kết hợp với `+`

Khi chưa có `String`, `char` được nâng thành số trước phép cộng; khi có `String`, nó được nối thành ký tự:

```java
System.out.println('A' + 'B' + "");  // 131
System.out.println("" + 'A' + 'B');  // AB
```

Các quy tắc thu hẹp hằng số, `char +=`, Unicode code point và lời gọi `println` được thực hành ở bài **Phân biệt ký tự cộng số với nối chuỗi**.

## 9. `null` và giá trị không phải chuỗi

Tham chiếu `String` bằng `null` vẫn được chuyển thành văn bản `"null"` khi tham gia phép nối: `"x=" + ((String) null)` cho `"x=null"`. Dấu `+` không tự bỏ giá trị thiếu. Với dữ liệu thực, quyết định thay bằng nhãn, bỏ qua, giữ trạng thái thiếu hay từ chối trước khi nối; bài **concat và khác biệt về đối số null** đào sâu hợp đồng các API.

## 10. `+=` không giống mọi cách viết có dấu `+`

Một câu hỏi dễ nhầm là:

```java
String a = "x";
a += 1 + 2;
```

Nhiều người đoán `"x12"` vì thấy chuỗi đứng bên trái. Nhưng phép `+=` là **gán kết hợp**; vế phải là biểu thức độc lập `1 + 2`, được tính thành **số `3`**. Sau đó Java nối số này vào giá trị cũ của `a`:

```text
a += (1 + 2)
a = "x" + 3
a = "x3"
```

Đối chiếu:

```java
String a = "x";
a += 1 + 2;         // "x3"

String b = "x";
b = b + 1 + 2;      // "x12"

String c = "x";
c = c + (1 + 2);    // "x3"
```

Điểm mấu chốt không phải một quy tắc đặc biệt chỉ dành cho `String`, mà là sự khác nhau giữa **biểu thức bên phải phép gán kết hợp** và **chuỗi các phép `+` bình thường**.

Việc một giá trị `String` bất biến cũng có nghĩa `a += ...` không chỉnh sửa nội dung chuỗi cũ, mà tạo kết quả chuỗi để gán lại biến `a`. Nếu thực hiện rất nhiều lần nối trong vòng lặp, hãy cân nhắc `StringBuilder`.

## 11. Tràn số xảy ra trước khi nối chuỗi

Nối chuỗi không làm cho phép cộng số đã diễn ra trước đó trở nên an toàn khỏi tràn số.

```java
int max = Integer.MAX_VALUE;

System.out.println(max + 1 + ""); // -2147483648
System.out.println("" + max + 1); // 21474836471
```

Giải thích:
- `max + 1 + ""` cộng `int` trước, nên kết quả `int` tràn từ `2147483647` thành `-2147483648`, sau đó được nối với chuỗi rỗng.
- `"" + max + 1` bắt đầu bằng nối chuỗi, cho văn bản `"2147483647"` rồi nối chữ số `1`; kết quả `"21474836471"` không phải phép tính số.

Nếu cần tính toán trong miền số lớn hơn, hãy nâng kiểu **trước khi cộng**, ví dụ:

```java
long result = (long) max + 1;
System.out.println("Result = " + result); // Result = 2147483648
```

Đừng dùng việc “nối chuỗi sớm” như một cách thay thế phép tính số: nó chỉ tạo văn bản, không giải quyết phép toán hay lỗi tràn số của nghiệp vụ.

## 12. Các lỗi thường gặp và cách khắc phục

| Sai lầm | Ví dụ dễ sai | Cách viết rõ ý |
| --- | --- | --- |
| Nghĩ mọi số trong biểu thức có chuỗi sẽ được cộng trước. | `"sum=" + a + b` | `"sum=" + (a + b)` khi cần tổng. |
| Nghĩ `+` luôn nối trái sang phải bất kể ưu tiên. | `"x" + 2 * 3` | Hiểu `*` thực hiện trước phép nối: `"x6"`. |
| Không biết khi nào kết quả trung gian thành `String`. | `1 + 2 + "3"` vs `"1" + 2 + 3` | Theo dõi kiểu sau mỗi dấu `+`. |
| Cố trừ ngay sau kết quả nối chuỗi. | `"x" + 10 - 3` | `"x" + (10 - 3)`. |
| Xem `char + char` là nối hai ký tự. | `'A' + 'B'` | `"" + 'A' + 'B'` hoặc `StringBuilder`. |
| Hiểu `+=` như mọi phép `+` cùng chuỗi thông thường. | `text += 1 + 2` | Nhớ vế phải `1 + 2` là phép cộng số trước. |
| Tin rằng nối chuỗi tránh được tràn số. | `max + 1 + ""` | Dùng `long` hoặc kiểu số phù hợp trước khi cộng. |
| Cho rằng phép nối làm `null` biến mất. | `"x" + nullable` | Chỉ rõ chính sách `null`. |
| Ghép chuỗi lặp lại quá nhiều lần trong vòng lặp. | `result = result + part` | Dùng `StringBuilder` khi xây dựng nội dung tăng dần. |

## 13. Hiệu năng và hành vi trình biên dịch

Thứ tự kết hợp và nội dung của `+` là quy tắc Java, không phụ thuộc hình dạng bytecode. Compiler có thể gộp hằng và JDK hiện đại có thể dùng `invokedynamic`, nhưng không được đổi hành vi quan sát được. Việc xây chuỗi nhiều lần trong vòng lặp là bài toán hiệu năng khác: xem bài **Nối trong một biểu thức so với tích lũy qua vòng lặp**.

## 14. Bài tập dự đoán kết quả

### Bài 1

```java
System.out.println(3 + 4 + "5");
System.out.println("3" + 4 + 5);
System.out.println(3 + "4" + 5);
```

**Đáp án:**

```text
75
345
345
```

Dòng đầu cộng số `3 + 4 = 7` rồi nối `"5"`. Hai dòng sau nối chuỗi ngay từ phép `+` đầu tiên.

### Bài 2

```java
System.out.println("total=" + 4 * 5 + 2);
System.out.println("total=" + (4 * 5 + 2));
```

**Đáp án:**

```text
total=202
total=22
```

Ở dòng đầu, `4 * 5` tạo số `20`, rồi phép `+` với chuỗi tạo `"total=20"` và nối số `2` thành `"total=202"`. Dòng thứ hai dùng ngoặc tính `4 * 5 + 2 = 22` trước.

### Bài 3

```java
System.out.println('A' + 'B');
System.out.println("" + 'A' + 'B');
```

**Đáp án:**

```text
131
AB
```

Hai `char` cộng kiểu số ở dòng đầu; dòng hai bắt đầu bằng chuỗi rỗng nên phép `+` chuyển sang nối chuỗi.

### Bài 4

```java
String x = "N";
x += 2 + 3;
System.out.println(x);

String y = "N";
y = y + 2 + 3;
System.out.println(y);
```

**Đáp án:**

```text
N5
N23
```

`x += 2 + 3` tính vế phải `2 + 3` trước; `y + 2 + 3` nối trái sang phải ngay từ `y`.

### Bài 5

```java
System.out.println(1 + (2 + "3"));
System.out.println("1" + (2 + 3));
```

**Đáp án:**

```text
123
15
```

Dấu ngoặc thay đổi biểu thức con tạo ra `String` hay số, từ đó thay đổi kết quả phép `+` bên ngoài.

### Bài 6: sửa lỗi

Đoạn sau không biên dịch:

```java
// String text = "Còn lại = " + 10 - 3;
```

**Cách sửa:**

```java
String text = "Còn lại = " + (10 - 3);
System.out.println(text); // Còn lại = 7
```

Dấu `+` với `String` tạo chuỗi; không thể dùng phép `-` số học trực tiếp trên kết quả đó.

## 15. Ghi nhớ

- `+` thực hiện **cộng số** khi hai toán hạng là số phù hợp; khi có toán hạng `String`, nó là **nối chuỗi**.
- Các phép `+` cùng mức ưu tiên **kết hợp trái**: `a + b + c` được nhóm `(a + b) + c`.
- `1 + 2 + "3"` → `"33"`, còn `"1" + 2 + 3` → `"123"`.
- Dấu ngoặc có thể buộc một phép cộng số diễn ra trước phép nối, ví dụ `"sum=" + (a + b)`.
- Độ ưu tiên (`*` trước `+`), tính kết hợp và thứ tự đánh giá các toán hạng là **ba khái niệm khác nhau**.
- `char + char` là phép cộng số nếu không có `String`; `+=` có ngữ nghĩa riêng của phép gán kết hợp.
- Một phép cộng số thực hiện **trước** phép nối chuỗi vẫn có thể tràn số.
- Với biểu thức có nhiều thành phần, hãy ưu tiên dấu ngoặc và tên biến trung gian để người đọc thấy rõ ý nghĩa.

## Tài liệu tham khảo

- [Java Language Specification `15.18 — Additive Operators`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18)
- [Java Language Specification `15.7 — Evaluation Order`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.7)
- [Java Language Specification `15.26 — Assignment Operators`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.26)
- [Java SE 17 — `StringBuilder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-concatenation-003">Phân biệt ký tự cộng số với nối chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ký tự cộng số với nối chuỗi trong Java

Trong Java, toán tử `+` có **hai vai trò**: thực hiện phép cộng số học hoặc nối chuỗi (`String concatenation`). Vì `char` lưu một đơn vị mã ký tự 16 bit có giá trị số, biểu thức `'A' + 1` **không** có nghĩa là ghép chữ `A` với chữ số `1`: Java nâng `char` thành `int` rồi cộng, nhận `66`. Ngược lại, `"A" + 1` có một toán hạng là `String` nên tạo chuỗi `"A1"`.

Sự khác biệt này rất quan trọng khi in log, xây dựng chuỗi, xử lý ký tự chữ số, dịch chuyển ký tự và đọc biểu thức có nhiều toán tử `+`. Chỉ cần thay dấu nháy đơn bằng nháy kép, thêm chuỗi rỗng hoặc đặt ngoặc khác vị trí, **kiểu kết quả và giá trị có thể đổi**.

## 1. Phân biệt `char`, `String` và `int`

| Biểu thức | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `'A'` | `char` | Một UTF-16 code unit tương ứng với ký tự `A` (giá trị `U+0041`, thập phân 65) |
| `"A"` | `String` | Một đối tượng chuỗi có nội dung là chữ `A` |
| `1` | `int` | Giá trị số nguyên một |
| `'1'` | `char` | Ký tự số `1` (giá trị `U+0031`, thập phân 49) |
| `"1"` | `String` | Chuỗi gồm ký tự `1` |

**Nháy đơn `'...'` không giống nháy kép `"..."`.**

- `'A'` là một giá trị nguyên thủy `char`, có thể tham gia phép toán số học.
- `"A"` là `String`, khiến toán tử `+` ở biểu thức nhị phân trở thành phép nối chuỗi.
- `'1'` mang giá trị mã ký tự của chữ số, **không phải số nguyên 1**.
- `"1"` là một chuỗi. Muốn đổi nội dung chuỗi chữ số thành số, có thể dùng `Integer.parseInt("1")` và xử lý lỗi đầu vào khi cần.

Với các ký tự quen thuộc trong bảng ASCII (cũng nằm trong Unicode), `'A'` có giá trị mã 65; `'B'` có giá trị mã 66; `'0'` có giá trị mã 48; `'1'` có giá trị mã 49.

## 2. Quy tắc của toán tử `+`

### Khi cộng các toán hạng số, `char` được nâng thành `int`

Nếu cả hai toán hạng là kiểu số nguyên thủy phù hợp với phép cộng số học, Java sử dụng **binary numeric promotion**. Với `char`, `byte` và `short`, giá trị được nâng thành ít nhất `int` trước khi tính toán.

Ví dụ:

```java
int a = 'A' + 1;    // 65 + 1 = 66
int b = 'A' + 'B';  // 65 + 66 = 131
int c = 1 + '2';   // 1 + 50 = 51
```

Kết quả biểu thức `'A' + 1` là một `int`, không phải `char`. Java **không tự chuyển mã 66 thành ký tự `B`** chỉ vì toán hạng đầu là `char`.

### Khi một toán hạng là `String`, `+` nối chuỗi

Nếu ít nhất một toán hạng của phép `+` nhị phân là `String`, Java thực hiện **nối chuỗi**. Toán hạng còn lại được chuyển thành biểu diễn văn bản phù hợp:

```java
String a = "A" + 1;   // "A1"
String b = 'A' + "1"; // "A1"
String c = "A" + 'B'; // "AB"
String d = "" + 42;   // "42"
```

Trong các ví dụ trên, toán hạng `char` được biểu diễn như **ký tự** khi tham gia phép nối với `String`: `'A' + "1"` cho `"A1"` chứ không cho `"651"`.

Hai kiểu hoạt động của `+` là **khác nhau về ngữ nghĩa**, không phải cùng một phép cộng rồi Java tự chọn cách in kết quả.

## 3. Ví dụ hoàn chỉnh: cùng chữ A nhưng kết quả khác hẳn

Tạo tệp `CharPlusDemo.java`:

```java
public class CharPlusDemo {
    public static void main(String[] args) {
        System.out.println('A');
        System.out.println('A' + 1);
        System.out.println("A" + 1);
        System.out.println('A' + "1");
        System.out.println('A' + 'B');
        System.out.println("" + 'A' + 'B');
        System.out.println(String.valueOf('A' + 1));
        System.out.println(String.valueOf((char) ('A' + 1)));
    }
}
```

Biên dịch và chạy trên Java 17 trở lên:

```bash
javac -encoding UTF-8 CharPlusDemo.java
java CharPlusDemo
```

Kết quả chính xác:

```text
A
66
A1
A1
131
AB
66
B
```

Từng dòng minh họa một quy tắc:

1. `println('A')` gọi overload có đối số `char` nên in ký tự `A`.
2. `'A' + 1` là phép cộng số: 65 + 1 = 66; `println` nhận kết quả số nguyên và in `66`.
3. `"A" + 1` có `String` nên nối thành `A1`.
4. `'A' + "1"` cũng có `String` nên nối thành `A1`.
5. `'A' + 'B'` là phép cộng số nguyên 65 + 66 = 131.
6. `"" + 'A' + 'B'` bắt đầu bằng nối chuỗi và tiếp tục nối hai ký tự thành `AB`.
7. `String.valueOf('A' + 1)` nhận giá trị `int` 66 nên trả chuỗi `"66"`.
8. `(char) ('A' + 1)` ép số 66 về kiểu `char`, tương ứng ký tự `B`; `String.valueOf(char)` trả `"B"`.

**Ghi nhớ:** chỉ nhìn thấy chữ `A` trong mã nguồn chưa đủ để biết chương trình sẽ in `A` hay `65`. Cần xác định **kiểu của cả biểu thức** được truyền cho `println`.

## 4. Thứ tự kết hợp từ trái sang phải và vai trò của dấu ngoặc

Thứ tự nhóm quyết định phép cộng mã ký tự xảy ra **trước hay sau** khi biểu thức trở thành `String`:

```java
System.out.println('A' + 1 + "!");      // 66!
System.out.println("!" + 'A' + 1);      // !A1
System.out.println("value=" + ('A' + 'B')); // value=131
```

Dùng ngoặc khi muốn tính mã số trước. Bài **Thứ tự kết hợp trái–phải của + với số và chuỗi** giải thích riêng tính kết hợp, độ ưu tiên và thứ tự đánh giá biểu thức; ở đây trọng tâm là **kiểu `int` của phép cộng `char`**.

## 5. Ký tự chữ số không phải giá trị số tương ứng

Một lỗi thường gặp là dùng `'5' + 2` khi muốn tính `5 + 2`. `'5'` lưu mã ký tự `U+0035` (thập phân 53), nên `'5' + 2` trả `55`, không phải `7`.

Tệp `DigitCharDemo.java`:

```java
public class DigitCharDemo {
    public static void main(String[] args) {
        char digitChar = '5';

        System.out.println("digitChar=" + digitChar);
        System.out.println("codeUnit=" + (int) digitChar);
        System.out.println("charPlusTwo=" + (digitChar + 2));
        System.out.println("numericValue=" + (digitChar - '0'));
        System.out.println("numericPlusTwo=" + (digitChar - '0' + 2));
        System.out.println("Character.digit=" + Character.digit(digitChar, 10));
        System.out.println("String.parse=" +
                Integer.parseInt(String.valueOf(digitChar)));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 DigitCharDemo.java
java DigitCharDemo
```

Đầu ra:

```text
digitChar=5
codeUnit=53
charPlusTwo=55
numericValue=5
numericPlusTwo=7
Character.digit=5
String.parse=5
```

**Vì sao `digitChar - '0'` cho 5?** Các ký tự chữ số ASCII `'0'` đến `'9'` có mã liên tiếp từ 48 đến 57, nên `'5' - '0'` là 53 − 48 = 5. Đây là công thức hữu ích **nếu bạn đã xác nhận ký tự thuộc `'0'..'9'`**.

`Character.digit(digitChar, 10)` là một lựa chọn rõ ràng hơn khi xử lý một ký tự chữ số được Java nhận diện theo cơ số 10; nó trả `-1` khi ký tự không hợp lệ ở cơ số đó. `Integer.parseInt(...)` phù hợp khi dữ liệu đầu vào là cả một chuỗi chữ số, có thể nhiều ký tự, và cần bắt lỗi định dạng.

Không nên tự động áp dụng `c - '0'` cho mọi ký tự Unicode giống chữ số: không phải các chữ số thuộc mọi hệ chữ viết đều có vị trí mã gần `'0'` ASCII.

## 6. Biến số `char` khi cộng và phép gán về `char`

`char` là kiểu nguyên thủy **16 bit không dấu** dùng để biểu diễn UTF-16 code unit. Khi cộng với một số nguyên, kết quả thông thường là `int`. Do đó, với một **biến** `char`:

```java
char c = 'A';
int number = c + 1;            // 66
char next = (char) (c + 1);    // 'B'
// char wrong = c + 1;         // Lỗi biên dịch: int không tự thu hẹp thành char
```

Đây là đoạn minh họa đặt trong một phương thức.

Tuy nhiên, Java có **ngoại lệ cho biểu thức hằng tại thời điểm biên dịch**. Một biểu thức hằng kiểu `int` có giá trị nằm trong dải `char` có thể được gán trực tiếp cho biến `char`:

```java
char fromConstant = 'A' + 1; // Hợp lệ: hằng số nguyên 66 nằm trong dải char

char letter = 'A';
// char fromVariable = letter + 1; // Không hợp lệ: kết quả là int
char fromCast = (char) (letter + 1); // Hợp lệ
```

Vì vậy, khẳng định “`char` cộng `int` **luôn** không gán được vào `char`” là chưa chính xác: phép cộng **vẫn có kiểu `int`**, nhưng quy tắc gán cho phép **thu hẹp ngầm một biểu thức hằng số nguyên nằm trong giới hạn `char`**. Còn `letter + 1` có chứa biến nên không phải biểu thức hằng theo quy tắc ấy.

### `+=` và `++` hoạt động như thế nào?

Với phép gán kết hợp, Java cho phép chuyển kết quả phép toán về kiểu của biến bên trái theo quy tắc riêng:

```java
char c = 'A';
c += 1; // B
c++;    // C
```

Điều này **không làm cho biểu thức `c + 1` có kiểu `char`**. Nó chỉ có nghĩa rằng trong phép gán kết hợp `c += 1`, Java thực hiện phép toán và chuyển kết quả về kiểu của `c` theo ngữ nghĩa của `+=`. Cần cẩn thận với giá trị lớn: phép chuyển về `char` có thể giữ lại 16 bit thấp của giá trị số, không bảo đảm tạo ra ký tự Unicode đúng ý định.

Tệp `CharArithmeticAssignment.java`:

```java
public class CharArithmeticAssignment {
    public static void main(String[] args) {
        char fromConstant = 'A' + 1;
        char fromVariable = 'A';
        int beforeCast = fromVariable + 1;
        char fromCast = (char) beforeCast;

        fromVariable += 1;
        fromVariable++;

        System.out.println("constant=" + fromConstant);
        System.out.println("intResult=" + beforeCast);
        System.out.println("cast=" + fromCast);
        System.out.println("compound=" + fromVariable);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CharArithmeticAssignment.java
java CharArithmeticAssignment
```

Kết quả:

```text
constant=B
intResult=66
cast=B
compound=C
```

Sử dụng phép cộng mã ký tự để dịch chữ **không phải** phương pháp xử lý văn bản tự nhiên tổng quát: bảng mã Unicode chứa nhiều khoảng không liên tiếp, nhiều chữ có dấu hoặc dạng ký tự bổ sung. Ngay cả việc dùng `c++` cho các ký tự ASCII cũng nên được giới hạn ở bài toán kỹ thuật có kiểm tra đầu vào và giới hạn.

## 7. Khi truyền vào `print` hoặc `println`, kiểu đối số quyết định cách in

Lớp `PrintStream` có nhiều overload, bao gồm `println(char)`, `println(int)` và `println(String)`. Java chọn phương thức tương ứng với **kiểu biểu thức** ở thời điểm biên dịch.

| Câu lệnh | Kiểu biểu thức đối số | Nội dung được in |
| --- | --- | --- |
| `System.out.println('A');` | `char` | `A` |
| `System.out.println((int) 'A');` | `int` | `65` |
| `System.out.println('A' + 1);` | `int` | `66` |
| `System.out.println((char) ('A' + 1));` | `char` | `B` |
| `System.out.println("A" + 1);` | `String` | `A1` |
| `System.out.println('A' + "1");` | `String` | `A1` |

Điều này giải thích tại sao cùng `'A'` có lúc in thành chữ `A`, lúc thành `65` hoặc thành số `66`.

Nếu bạn cần thể hiện **mã số** của một `char`, hãy ép sang `int` một cách tường minh: `(int) c`. Nếu cần thể hiện **ký tự sau khi tính toán**, cần quyết định xem phép chuyển số về `char` có thực sự hợp lệ cho bài toán hay không.

## 8. Ghép ký tự thành chuỗi: lựa chọn rõ ràng hơn

Khi muốn nối hai ký tự mà không cộng mã của chúng, có một số cách diễn đạt rõ ý định:

### Dùng một `String` làm toán hạng

```java
char first = 'J';
char second = 'a';

String joined = "" + first + second; // "Ja"
```

Cách này đơn giản và không cần chuyển các `char` thành số. Tuy nhiên, với mã dài hoặc nhiều vòng lặp, có thể chọn công cụ ghép chuỗi chuyên dụng hơn để làm rõ ý định.

### Dùng `StringBuilder`

```java
StringBuilder builder = new StringBuilder();
builder.append('J');
builder.append('a');
String joined = builder.toString(); // "Ja"
```

`StringBuilder.append(char)` thêm **ký tự được biểu diễn bởi code unit**, không cộng giá trị số. Đây là lựa chọn thường phù hợp khi xây dựng chuỗi theo nhiều bước hoặc trong vòng lặp. Không nên dựa vào việc Java thực thi một biểu thức nối chuỗi cụ thể bằng lớp nào bên trong; JDK có thể chọn chiến lược tối ưu khác nhau.

### Dùng `String.valueOf(char)`

```java
char letter = 'A';
String text = String.valueOf(letter); // "A"
```

Cần phân biệt `String.valueOf(letter)` (đối số là `char`) với `String.valueOf(letter + 1)` (đối số đã là `int`). Cách rõ ràng nhất để tạo văn bản từ một ký tự là truyền **chính ký tự** hoặc một mảng ký tự vào API tương ứng, không cộng mã số trừ khi bài toán thực sự yêu cầu.

## 9. `char` chỉ là một UTF-16 code unit, không phải mọi ký tự Unicode hoàn chỉnh

Trong các ví dụ trên, `'A'` và `'5'` đều là ký tự thuộc BMP nên chúng vừa bằng một `char` vừa bằng một Unicode code point. Nhưng không phải mọi ký tự Unicode đều được chứa trong một `char`. Ví dụ emoji 😀 (`U+1F600`) cần **một surrogate pair gồm hai `char`** trong UTF-16.

Vì thế:

- `char` tham gia phép cộng bằng giá trị **code unit**, không tự động là một Unicode code point hoàn chỉnh.
- Không thể lấy một Unicode code point ngoài BMP chỉ bằng cách lấy một `char` trong surrogate pair rồi cộng số.
- Nếu cần xử lý mã Unicode như một đơn vị, dùng `int` để lưu code point; các API như `String.codePointAt(...)` và `Character.toChars(int)` hỗ trợ làm việc với code point.

Tệp `CodePointVsChar.java`:

```java
public class CodePointVsChar {
    public static void main(String[] args) {
        String emoji = new String(Character.toChars(0x1F600));

        System.out.println("length=" + emoji.length());
        System.out.printf("firstUnit=U+%04X%n", (int) emoji.charAt(0));
        System.out.printf("codePoint=U+%X%n", emoji.codePointAt(0));
        System.out.println("concat=" + ("X" + emoji));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CodePointVsChar.java
java CodePointVsChar
```

Kết quả (console cần hỗ trợ hiển thị emoji để nhìn đúng dòng cuối):

```text
length=2
firstUnit=U+D83D
codePoint=U+1F600
concat=X😀
```

Khi nối `"X" + emoji`, Java nối toàn bộ nội dung `String`. Khi làm phép toán với `emoji.charAt(0)`, bạn chỉ đang thao tác với **một nửa của surrogate pair**, không phải toàn bộ emoji.

## 10. Các lỗi phổ biến và cách tránh

- `'1' + 2` cho `51`, vì `'1'` mang mã 49 chứ không phải số 1.
- `'A' + 'B'` cho `131` (`int`), trong khi `"" + 'A' + 'B'` cho `"AB"`.
- `String.valueOf('A' + 1)` nhận `int` và cho `"66"`; dùng `String.valueOf((char) ('A' + 1))` để tạo `"B"` khi phép chuyển có nghĩa.
- `char next = c + 1` không biên dịch nếu `c` là biến; ngoại lệ thu hẹp ngầm chỉ áp dụng cho **biểu thức hằng** `int` hợp lệ.
- Một `char` có thể chỉ là một nửa surrogate pair; không áp dụng mẹo cộng trừ ASCII cho Unicode tổng quát.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Đoạn sau in gì?

```java
System.out.println('A' + 2);
System.out.println("A" + 2);
System.out.println('A' + "2");
```

**Đáp án:** `67`, `A2`, `A2`. Chỉ dòng đầu là phép cộng mã ký tự với số nguyên.

**Câu 2.** Vì sao `'1' + '2'` không phải số 3 và cũng không phải chuỗi `"12"`?

**Đáp án:** Cả hai toán hạng là `char`, nên được nâng thành `int` và cộng các mã `49 + 50 = 99`. Muốn tính 1 + 2 phải chuyển chữ số thành số; muốn ghép thành `"12"` cần dùng `String`.

**Câu 3.** `"result=" + 'A' + 1` khác gì `"result=" + ('A' + 1)`?

**Đáp án:** Biểu thức đầu in `result=A1` vì nối chuỗi ngay từ `"result="`. Biểu thức sau in `result=66` vì phép cộng trong ngoặc được thực hiện trước khi nối.

**Câu 4.** `char c = 'A' + 1;` có hợp lệ không? Còn `char c = 'A'; char d = c + 1;` thì sao?

**Đáp án:** Trường hợp đầu hợp lệ vì `'A' + 1` là **biểu thức hằng `int`** có giá trị 66 nằm trong dải `char`. Trường hợp sau không hợp lệ vì `c + 1` phụ thuộc biến, kết quả kiểu `int` không được tự chuyển về `char` trong phép gán thông thường.

**Câu 5.** Khi muốn tạo chuỗi `"Hi"` từ `char a = 'H'; char b = 'i';`, viết thế nào?

**Đáp án:** Có thể viết `String result = "" + a + b;` hoặc `new StringBuilder().append(a).append(b).toString()`. Không dùng `a + b` vì đó là phép cộng số.

**Ghi nhớ:** `char + số` hoặc `char + char` là **phép cộng số** sau khi nâng kiểu thành `int`; `String + giá trị` là **nối chuỗi**. Muốn tính số, kiểm tra kiểu của toán hạng; muốn ghép ký tự, tạo một phép nối `String` rõ ràng; và khi có nhiều dấu `+`, hãy dùng ngoặc để biểu đạt đúng ý định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-concatenation-004">concat và khác biệt về đối số null</a>

<details>
<summary>Xem chi tiết</summary>

# String.concat và khác biệt khi đối số là null

Trong Java, có nhiều cách nối chuỗi, nhưng **String.concat(String str)** và toán tử **+** không xử lý **null** giống nhau. Đây là điểm dễ gây lỗi khi nối tên, mã, thông báo hoặc dữ liệu từ API mà một thành phần có thể chưa được cung cấp.

- **"Java".concat(" 17")** trả về String có nội dung **"Java 17"**.
- **"Java".concat(null)** ném **NullPointerException**.
- **"Java" + null** tạo String có nội dung **"Javanull"**: toán tử nối chuỗi chuyển tham chiếu null thành chữ **"null"**.
- Nếu **biến nhận lời gọi concat() là null**, ví dụ **prefix.concat(" 17")**, cũng ném **NullPointerException**.
- **String.concat() không sửa String gốc**: String bất biến, kết quả phải được sử dụng hoặc gán cho một biến.

Sự khác nhau không chỉ là vấn đề cú pháp; nó còn quyết định cách ứng dụng diễn đạt dữ liệu vắng mặt. Chữ "null" xuất hiện trong kết quả nối chuỗi thường là **dấu hiệu dữ liệu chưa được xử lý theo chính sách nghiệp vụ**, không mặc nhiên là nội dung người dùng muốn nhìn thấy.

## 1. Hiểu đúng chữ ký của String.concat()

Phương thức của lớp String có dạng:

~~~java
public String concat(String str)
~~~

Đây là **chữ ký API để giải thích**, không phải một phương thức bạn cần tự định nghĩa. **str** phải là tham chiếu tới một String hợp lệ; phương thức không có hợp đồng chuyển null thành văn bản.

Ví dụ cơ bản:

~~~java
String first = "Java";
String result = first.concat(" 17");

System.out.println(first);  // Java
System.out.println(result); // Java 17
~~~

Biến first vẫn tham chiếu tới String có nội dung "Java". concat() tạo/trả về giá trị kết quả; muốn dùng nội dung đã nối, phải giữ giá trị trả về.

Nếu tham số là String rỗng, nội dung không đổi:

~~~java
String value = "Java";
String result = value.concat("");

System.out.println(result);       // Java
System.out.println(value.equals(result)); // true
~~~

Theo API của String.concat(), nếu đối số có độ dài 0, phương thức trả về chính String nhận lời gọi. Tuy vậy, khi so sánh **nội dung**, hãy ưu tiên equals() thay vì dựa vào toán tử == để quyết định giá trị chuỗi bằng nhau.

### Điều gì xảy ra nếu đối số null?

~~~java
String text = "Hello";
String extra = null;

String result = text.concat(extra); // NullPointerException
~~~

Đây là **đoạn mã minh họa lỗi**. Vấn đề không phải chuỗi text không hợp lệ: receiver text là một String bình thường. Chính đối số extra không cung cấp String để nối; concat() không coi null là chuỗi rỗng và cũng không tự viết ra chữ "null".

**Nguyên tắc:** tham số của concat() phải là String không-null. Nếu nguồn dữ liệu nullable, hãy xử lý trước khi gọi.

## 2. So sánh với toán tử + và +=

Trong ngữ cảnh có một toán hạng là String, toán tử **+** dùng cơ chế nối chuỗi của Java. Các toán hạng không phải String được chuyển thành biểu diễn String; với **tham chiếu null**, biểu diễn là **"null"**.

~~~java
String first = "Java";
String missing = null;

System.out.println(first + missing); // Javanull
System.out.println(missing + first); // nullJava
System.out.println("Value: " + missing); // Value: null
~~~

Toán tử + không gọi **missing.concat(...)**. Vì vậy, việc biến missing null **không làm phép cộng chuỗi ở trên ném NullPointerException**.

Nhưng điều này không bảo đảm ứng dụng đã xử lý đúng dữ liệu. Ví dụ **"Hello null"** có thể gây hiểu nhầm nếu "null" vốn biểu thị không có tên khách hàng.

### += cũng sử dụng quy tắc nối bằng +

~~~java
String message = "Hello";
String name = null;

message += " " + name;
System.out.println(message); // Hello null
~~~

Biểu thức gán nối **+=** tương đương về ý nghĩa với tạo kết quả nối rồi gán lại biến. Nó không biến String thành mutable và không tự kiểm tra null.

So sánh:

| Biểu thức | Khi phần tham chiếu null | Kết quả |
|---|---|---|
| **"A".concat((String) null)** | null là đối số của concat | NullPointerException |
| **"A" + (String) null** | null tham gia phép nối chuỗi | "Anull" |
| **(String) null + "A"** | null tham gia phép nối chuỗi | "nullA" |
| **"A" + 42** | Số được chuyển thành String | "A42" |
| **"A".concat("42")** | Đối số là String hợp lệ | "A42" |
| **"A".concat("")** | Đối số là String rỗng | "A" |
| **"A" + ""** | Phép nối có một chuỗi rỗng | "A" |

Toán tử + chấp nhận nhiều kiểu giá trị khi nối với String; concat() nhận **String**. Viết **"A".concat(42)** không hợp lệ về kiểu ở thời điểm biên dịch, trong khi **"A" + 42** là hợp lệ.

### Không nhầm nối chuỗi với cộng số

~~~java
System.out.println("Total: " + 1 + 2);   // Total: 12
System.out.println("Total: " + (1 + 2)); // Total: 3
~~~

Biểu thức + được đánh giá theo thứ tự và kiểu toán hạng. Khi quá trình đã thành nối chuỗi, các toán hạng số tiếp theo được chuyển thành văn bản. Dùng ngoặc để yêu cầu phép cộng số học trước khi nối.

## 3. Phân biệt null ở vị trí receiver và null ở vị trí đối số

Hai biểu thức sau đều ném NullPointerException, nhưng không cùng nguyên nhân:

~~~java
String first = "Hello";
String missing = null;

first.concat(missing);       // Receiver tồn tại, đối số null
missing.concat(" World");    // Receiver null, không gọi được instance method
~~~

Trong dòng đầu, String.concat() cần một String đối số nhưng nhận null. Trong dòng thứ hai, lời gọi phương thức instance không có đối tượng nhận.

Điều này quan trọng khi bạn viết xử lý đầu vào:

~~~java
static String unsafeConcat(String left, String right) {
    return left.concat(right);
}
~~~

Đây là **phương thức minh họa**, cần đặt trong một lớp Java. Nó yêu cầu **cả left lẫn right đều không-null**; chỉ kiểm tra right là chưa đủ nếu left có thể null.

Ngược lại, với biểu thức:

~~~java
static String stringifyWithPlus(String left, String right) {
    return left + right;
}
~~~

Java có thể tạo một String kết quả khi một hoặc cả hai tham chiếu null, chẳng hạn **null + null** trong hai biến kiểu String cho ra chữ **"nullnull"**. Nhưng đó chỉ là quy tắc biểu diễn tham chiếu khi nối, không phải chính sách kiểm tra hợp lệ của dữ liệu.

**Cẩn trọng khi phân tích lỗi:** một chuỗi đã nối thành "null" không cho biết chắc phần tử ban đầu là null hay người dùng thật sự đã nhập bốn ký tự n-u-l-l. Muốn giữ được thông tin này, cần quản lý null trước bước chuyển thành văn bản.

## 4. Chương trình Java 17+ độc lập: quan sát mọi trường hợp

Lưu thành tệp **StringConcatNullDemo.java**:

~~~java
import java.util.Objects;

public class StringConcatNullDemo {
    public static void main(String[] args) {
        String left = "Java";
        String right = null;

        System.out.println("concat-ok=" + left.concat(" 17"));
        System.out.println("plus-right-null=" + (left + right));
        System.out.println("plus-left-null=" + (right + left));

        try {
            left.concat(right);
        } catch (NullPointerException ex) {
            System.out.println("concat-null-argument=NPE");
        }

        try {
            right.concat(" 17");
        } catch (NullPointerException ex) {
            System.out.println("concat-null-receiver=NPE");
        }

        StringBuilder builder = new StringBuilder("Java");
        builder.append((String) null);
        System.out.println("builder-append-null=" + builder);

        System.out.println("fallback-empty="
                + left.concat(Objects.toString(right, "")));
        System.out.println("explicit-value-of="
                + String.valueOf((Object) right));

        String original = "J";
        original.concat("ava");
        System.out.println("ignored-result=" + original);
    }
}
~~~

Biên dịch/chạy trên JDK 17+:

~~~bash
javac StringConcatNullDemo.java
java StringConcatNullDemo
~~~

Đầu ra chính xác:

~~~text
concat-ok=Java 17
plus-right-null=Javanull
plus-left-null=nullJava
concat-null-argument=NPE
concat-null-receiver=NPE
builder-append-null=Javanull
fallback-empty=Java
explicit-value-of=null
ignored-result=J
~~~

**Giải thích theo dòng:**

1. **concat-ok** là trường hợp cả receiver và tham số String đều hợp lệ.
2. **plus-right-null** và **plus-left-null** minh họa + chuyển tham chiếu null thành chữ "null", bất kể nó nằm trước hay sau chuỗi còn lại.
3. Hai dòng **...=NPE** xuất hiện vì hai lỗi NullPointerException được bắt riêng; chương trình không dừng giữa chừng.
4. **StringBuilder.append((String) null)** có hợp đồng riêng và nối chữ "null". Điều này không làm String.concat(null) trở nên hợp lệ.
5. **Objects.toString(right, "")** chuyển null thành chuỗi rỗng theo chính sách đã chỉ định, nên việc gọi concat() sau đó an toàn.
6. **String.valueOf((Object) right)** minh họa chuyển Object null thành chuỗi "null" một cách tường minh.
7. **ignored-result=J** chứng minh String.concat() không thay đổi đối tượng String đã tồn tại.

Việc in **NPE** là chữ do khối catch quyết định, không phải toàn bộ thông báo ngoại lệ tự sinh từ JVM. Nội dung message của NullPointerException có thể khác nhau; không nên dựa vào nó khi cần output ổn định.

## 5. Xử lý null có chủ đích: bốn chính sách phổ biến

Trước khi nối dữ liệu nullable, hãy lựa chọn rõ: **từ chối**, **coi như rỗng**, **dùng nhãn mặc định**, hay **giữ trạng thái vắng mặt**. Mỗi chính sách phù hợp với yêu cầu khác nhau.

### 5.1. Từ chối null nếu dữ liệu bắt buộc

Nếu cả hai phần phải có giá trị:

~~~java
import java.util.Objects;

public class StrictConcatDemo {
    static String joinRequired(String left, String right) {
        Objects.requireNonNull(left, "left is required");
        Objects.requireNonNull(right, "right is required");
        return left.concat(right);
    }

    public static void main(String[] args) {
        System.out.println(joinRequired("Java", " 17"));

        try {
            joinRequired("Java", null);
        } catch (NullPointerException ex) {
            System.out.println("Missing right");
        }
    }
}
~~~

Lưu **StrictConcatDemo.java**, chạy:

~~~bash
javac StrictConcatDemo.java
java StrictConcatDemo
~~~

Output:

~~~text
Java 17
Missing right
~~~

**Ưu điểm:** không âm thầm tạo chuỗi "Javanull" hoặc che giấu dữ liệu thiếu. Với đầu vào đến từ người dùng, hệ thống thường nên chuyển lỗi thành thông báo validation phù hợp thay vì để ngoại lệ không được xử lý lan ra giao diện.

### 5.2. Coi null là chuỗi rỗng

Nếu phần tùy chọn vắng mặt tương đương không thêm gì:

~~~java
static String appendOptional(String base, String extra) {
    if (base == null) {
        throw new IllegalArgumentException("base is required");
    }
    return base.concat(extra == null ? "" : extra);
}
~~~

Đây là **phương thức minh họa** để đặt bên trong lớp Java. Nếu base="Java" và extra=null, kết quả là "Java". Nếu extra=" 17", kết quả là "Java 17".

Một lựa chọn khác là **Objects.toString(extra, "")**, nhưng dùng toán tử ba ngôi thể hiện rõ rằng extra có kiểu String và null được đổi thành một String rỗng.

**Không đồng nhất null và chuỗi rỗng nếu nghiệp vụ phân biệt hai trạng thái này**, chẳng hạn trong cập nhật từng phần dữ liệu.

### 5.3. Dùng văn bản mặc định có ý nghĩa

Nếu giao diện yêu cầu một nhãn thay thế, ví dụ khách chưa có tên:

~~~java
String customerName = null;
String shownName = customerName == null ? "(unknown)" : customerName;
String greeting = "Hello, ".concat(shownName);

System.out.println(greeting); // Hello, (unknown)
~~~

Văn bản **"(unknown)"** là quyết định thiết kế, không phải quy tắc tự động của concat(). Một số ứng dụng chọn "Guest", "N/A" hoặc bỏ hẳn phần thông tin; cần thống nhất với yêu cầu sản phẩm và ngôn ngữ giao diện.

### 5.4. Giữ null như một trạng thái, chưa nối vội

Khi null mang nghĩa “chưa có dữ liệu”, có thể trả về null hoặc một kiểu thể hiện sự vắng mặt, chỉ nối khi các điều kiện thỏa:

~~~java
static String combineIfPresent(String left, String right) {
    if (left == null || right == null) {
        return null;
    }
    return left.concat(right);
}
~~~

Đây là **phương thức minh họa**. Nó khác chính sách chuyển null thành chuỗi rỗng: thiếu một trong hai phần làm cho **toàn bộ kết quả không có giá trị**, thay vì trả về phần còn lại.

Không có một nhánh xử lý null đúng cho mọi ứng dụng. Điều quan trọng là có **quy tắc minh bạch và nhất quán**.

## 6. Khác biệt giữa concat(), +, StringBuilder.append() và String.join()

Các API này phục vụ các tình huống nối chuỗi tương tự nhưng **không có hợp đồng null giống hệt nhau**:

| Cách nối | Đầu vào | Hành vi với null | Dùng khi |
|---|---|---|---|
| **left.concat(right)** | Cả hai cần là String | Receiver null hoặc đối số null → NullPointerException | Đã xác nhận các phần String bắt buộc hợp lệ |
| **left + right** với ngữ cảnh nối String | Nhiều kiểu giá trị | Tham chiếu null chuyển thành "null" | Nối vài phần ngắn, xây thông báo khi biểu diễn null được kiểm soát |
| **StringBuilder.append((String) value)** | Giá trị String | Đối số String null được thêm chữ "null" | Xây văn bản theo nhiều bước; cần xử lý null trước nếu không muốn chữ "null" |
| **String.join(", ", elements...)** | Dấu phân cách và các phần String | Phần tử null được thêm chữ "null"; delimiter null gây NullPointerException | Nối danh sách phần tử với dấu phân cách |
| **String.valueOf((Object) value)** | Object | Object null → "null" | Chuyển thành biểu diễn văn bản một cách tường minh |
| **Objects.toString(value, fallback)** | Object và chuỗi fallback | value null → dùng fallback | Lựa chọn chuỗi mặc định do ứng dụng quy định |

Ví dụ StringBuilder:

~~~java
StringBuilder sb = new StringBuilder("Hello");
String name = null;

sb.append(" ");
sb.append(name);
System.out.println(sb); // Hello null
~~~

**StringBuilder không đồng nghĩa với concat()** về chính sách null, dù cả hai đều có thể được dùng để ghép văn bản. Ngoài ra, StringBuilder là **mutable**: append() thay đổi nội dung bộ đệm, khác với String.concat() vốn trả về String mà không thay đổi đối tượng gốc.

**Lưu ý về overload:** StringBuilder có nhiều phương thức append() nhận các kiểu khác nhau. Ví dụ **append((char[]) null)** không có cùng hành vi với **append((String) null)** và có thể ném NullPointerException. Khi cần phân tích chính xác null, phải xét **kiểu tham số và overload được chọn**, không chỉ tên hàm.

### Tránh hiểu nhầm String.valueOf(null)

Phương thức String.valueOf() có nhiều overload, trong đó có **valueOf(Object)** và **valueOf(char[])**. Viết trực tiếp **String.valueOf(null)** có thể chọn overload **char[]** và ném NullPointerException, thay vì tạo chữ "null".

Do đó, khi muốn minh họa rõ quy tắc **Object null → "null"**, hãy dùng **String.valueOf((Object) null)**. Hoặc nếu mục tiêu là thay thế null thành rỗng/nhãn khác, dùng Objects.toString(value, fallback) và chọn fallback rõ ràng.

### String.join() không tự bỏ qua phần tử null

~~~java
System.out.println(String.join(", ", "A", null, "B")); // A, null, B
~~~

Nếu nghiệp vụ muốn **bỏ qua** phần tử null, phải lọc hoặc quy định cách xử lý trước khi gọi. Không nên đoán API nào cũng ngầm bỏ null để tiện trình bày.

## 7. So sánh với "null" và chuỗi rỗng: vấn đề dữ liệu, không chỉ cú pháp

Ba giá trị sau khác nhau:

~~~java
String missing = null;
String empty = "";
String literal = "null";
~~~

- **missing**: tham chiếu không trỏ đến String.
- **empty**: String tồn tại, dài 0.
- **literal**: String có bốn ký tự n, u, l, l.

Bởi vậy:

~~~java
System.out.println(missing == null);         // true
System.out.println("".equals(missing));      // false
System.out.println("null".equals(literal));  // true
System.out.println("null".equals(missing));  // false
~~~

Sau khi thực hiện **"prefix" + missing**, kết quả chứa bốn chữ "null" giống như khi nối **"prefix" + literal**. Nếu chỉ giữ String kết quả, thông tin ban đầu có phải null hay không đã bị mất.

Trong ứng dụng thực tế, điều này có thể dẫn đến lỗi như:

- **Tên hiển thị:** "Hello null" thay vì "Hello Guest".
- **Đường dẫn:** "/users/null" tạo địa chỉ trông hợp lệ nhưng dựa trên dữ liệu thiếu.
- **Khóa cache hoặc mã định danh:** giá trị thiếu và giá trị chữ "null" bị gom vào cùng một khóa sau khi chuyển thành String.
- **Log và thông báo:** văn bản chứa "null" không đủ để phân biệt tham chiếu null với giá trị người dùng nhập.

Nếu phải giữ ngữ nghĩa dữ liệu, hãy **kiểm tra null trước khi chuyển thành văn bản**, hoặc lưu cấu trúc dữ liệu gồm giá trị và trạng thái rõ ràng.

## 8. Có nên dùng concat() thay cho + để tối ưu hiệu năng?

`concat()` không mặc nhiên nhanh hơn `+`. Với một vài thành phần, `+` thường rõ ý và compiler/JVM hiện đại có thể tối ưu. Với quá trình tích lũy nhiều lượt, `StringBuilder` giúp xây dựng kết quả trong bộ đệm mutable. Bài **Nối trong một biểu thức so với tích lũy qua vòng lặp** giải thích lựa chọn và đo hiệu năng; tại đây nên ưu tiên **hợp đồng null** khi quyết định gọi `concat`.

## 9. Phân tích tình huống thực tế: xây lời chào từ tên tùy chọn

Giả sử API trả về tên khách có thể null:

~~~java
String nameFromApi = null;
~~~

Ba cách viết có ý nghĩa khác nhau:

~~~java
String unsafe = "Hello, " + nameFromApi;
// unsafe: "Hello, null"

String safeDisplay = "Hello, "
        + (nameFromApi == null ? "Guest" : nameFromApi);
// safeDisplay: "Hello, Guest"

// "Hello, ".concat(nameFromApi) sẽ ném NullPointerException.
~~~

Đây là **mã minh họa**, không phải ba phương án tương đương nghiệp vụ. Nếu người dùng có tên rỗng hoặc chỉ có whitespace, cả điều kiện **nameFromApi == null** vẫn chưa đủ. Có thể cần:

~~~java
static String greeting(String name) {
    String display = name == null || name.isBlank()
            ? "Guest"
            : name.strip();
    return "Hello, ".concat(display);
}
~~~

Hàm này định nghĩa rõ: null, rỗng hoặc chỉ whitespace đều thành "Guest"; tên có nội dung được strip() ở hai đầu. Nó phù hợp khi **hợp đồng UI cho phép** xử lý như vậy. Nếu khoảng trắng là thông tin quan trọng, không được tự strip.

Phương thức **greeting()** ở trên là minh họa và cần đặt trong một lớp Java khi biên dịch.

## 10. Lỗi phổ biến cần tránh

- `concat(null)` ném `NullPointerException`, còn `"A" + (String) null` cho `"Anull"`.
- `left.concat(right)` yêu cầu **cả receiver và đối số** khác `null`.
- `concat()` trả kết quả, không sửa chuỗi cũ; lời gọi mà bỏ kết quả không đổi giá trị biến.
- `concat(String)` không nhận trực tiếp `int` hay một đối tượng tùy ý, khác với `+` trong ngữ cảnh nối chuỗi.
- `String.valueOf(null)` phải xét overload: ép `(Object) null` khi cần đúng hợp đồng `valueOf(Object)`.
- Trước khi dựng hiển thị, khóa, URL, SQL hoặc log, xác định chính sách dữ liệu thiếu; dùng prepared statement cho SQL và serializer phù hợp cho dữ liệu cấu trúc.

**Trọng tâm:** null và chữ `"null"` là hai trạng thái khác nhau; cần quyết định trước khi tạo văn bản.

## 11. Bài tập và đáp án

Xét đoạn sau:

~~~java
String a = "A";
String b = null;
String c = "";
~~~

Hãy tự trả lời trước khi xem đáp án:

1. **a.concat(c)** trả về nội dung gì?
2. **a.concat(b)** tạo kết quả hay ném ngoại lệ?
3. **a + b** có nội dung gì?
4. **b + a** có nội dung gì?
5. **a + c** khác **a.concat(c)** về nội dung không?
6. Nếu gọi **b.concat(a)** thì có khác nguyên nhân lỗi ở câu 2 không?
7. Khi nào nên sử dụng **Objects.toString(b, "")**?
8. Nếu cần phân biệt null với chuỗi có nội dung "null", có nên nối bằng + ngay từ đầu không?

**Đáp án:**

1. **"A"**: nối String rỗng không thêm ký tự nào.
2. **NullPointerException**: đối số của concat() là null.
3. **"Anull"**: toán tử + chuyển tham chiếu null thành chữ "null".
4. **"nullA"**: null cũng được chuyển thành "null" khi ở phía trước.
5. **Không khác về nội dung**, đều là "A"; không nên dựa vào danh tính tham chiếu để so nội dung.
6. **Có**: câu 2 lỗi vì đối số null; b.concat(a) lỗi vì receiver null.
7. Khi **có chủ đích** muốn coi null là String rỗng trước khi nối, không phải khi nghiệp vụ cần bảo toàn trạng thái vắng mặt.
8. **Không nên**, nếu kết quả cần giữ thông tin null thật. Hãy quyết định chính sách hoặc giữ dữ liệu có cấu trúc trước khi biểu diễn thành văn bản.

**Ghi nhớ:** **concat(null) → NullPointerException; nối String bằng + với tham chiếu null → chữ "null"**. Đối với dữ liệu thật, hãy kiểm tra receiver, đối số và chính sách xử lý null trước khi nối; đồng thời sử dụng kết quả trả về của concat() vì String là bất biến.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-concatenation-005">+= là tạo kết quả rồi gán lại tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử += trong Java String: tạo kết quả rồi gán lại tham chiếu

## 1. Hiểu đúng mục tiêu

Khi viết:

~~~java
String name = "Java";
name += " Core";
~~~

giá trị của biến `name` trở thành `"Java Core"`. Nhưng **đối tượng chuỗi `"Java"` không bị sửa**. Java tính phép nối chuỗi, cho ra kết quả, rồi **gán kết quả trở lại biến tham chiếu `name`**.

Đây là điểm khác biệt quan trọng giữa việc *thay đổi giá trị của biến* và việc *thay đổi nội dung đối tượng*. `String` là **immutable**: nội dung của một đối tượng `String` không thay đổi sau khi tạo. Cú pháp `+=` chỉ làm phép nối và phép gán ngắn gọn hơn; nó **không phá vỡ tính bất biến**.

Sau bài này, bạn có thể:

1. Giải thích ý nghĩa của `s += part` trong trường hợp `s` là `String`.
2. Phân biệt `s += x`, `s = s + x`, `s.concat(x)` và `StringBuilder.append(x)`.
3. Dự đoán giá trị của các biến khác đang giữ **tham chiếu cũ** sau khi `s += ...`.
4. Hiểu cách `+=` chuyển các toán hạng thành văn bản, kể cả số, đối tượng và `null`.
5. Phân biệt thứ tự tính toán, dấu ngoặc và kết quả khi nối số.
6. Nhận biết lỗi dùng `+=` với biến `final` và cân nhắc `StringBuilder` khi nối nhiều lần.
7. Đọc và kiểm chứng kết quả bằng các chương trình Java 17+ chạy độc lập.

Kiến thức nền cần có: biến, tham chiếu đối tượng, dấu `=`, `+`, `String`, kiểu nguyên thủy và `final`.

## 2. += vừa nối chuỗi vừa gán kết quả

`+=` là **toán tử gán kết hợp** (*compound assignment operator*). Với biến `String` đơn giản, có thể hiểu:

~~~java
String s = "Hello";

s += " World";    // Viết gọn
// Tương đương về kết quả với:
// s = s + " World";
~~~

Kết quả cuối của `s` là `"Hello World"`. Thao tác có thể hình dung theo ba bước:

1. Đọc giá trị hiện tại mà biến `s` tham chiếu, ở đây là `"Hello"`.
2. Tính phép `s + " World"`, tạo giá trị nội dung `"Hello World"`.
3. Gán kết quả phép nối trở lại biến `s`.

Chú ý câu **“tương đương về kết quả”**: đối với biểu thức ở vế trái phức tạp có tác dụng phụ như `array[index++]`, Java đánh giá vế trái của phép gán kết hợp **một lần**. Vì vậy không thể máy móc thay mọi `left += value` bằng văn bản `left = left + value` nếu việc tính `left` có tác dụng phụ.

Sơ đồ tham chiếu đơn giản:

~~~text
Ban đầu:
s ─────────► String "Hello"

Sau s += " World":
s ─────────► String "Hello World"

Đối tượng mang nội dung "Hello" không bị sửa.
Nếu còn biến khác trỏ đến đối tượng cũ, biến ấy vẫn đọc "Hello".
~~~

Trong Java, `String` bất biến, dù trình biên dịch và JVM có thể tối ưu việc hình thành giá trị kết quả theo nhiều cách. Không nên suy đoán **mỗi phép nối luôn cấp phát chính xác bao nhiêu đối tượng trung gian**.

## 3. Ví dụ then chốt: biến khác không bị thay đổi

~~~java
String first = "Java";
String second = first;

first += " Core";

System.out.println(first);  // Java Core
System.out.println(second); // Java
~~~

Trước `+=`, cả `first` và `second` tham chiếu tới cùng giá trị `"Java"`. Sau phép gán, **chỉ `first` được gán lại**; `second` vẫn giữ tham chiếu ban đầu, nên in `"Java"`.

~~~text
Trước:
first  ───┐
          ├──► "Java"
second ───┘

Sau:
first  ──────► "Java Core"
second ──────► "Java"
~~~

Một cách kiểm tra ý nghĩa (ưu tiên kiểm tra bằng `equals()`):

~~~java
String original = "A";
String copyOfReference = original;
original += "B";

System.out.println("A".equals(copyOfReference)); // true
System.out.println("AB".equals(original));       // true
~~~

Đây chính là ý nghĩa **“tạo kết quả rồi gán lại tham chiếu”**. Phép gán không truyền ngược thay đổi sang các biến tham chiếu khác.

### Phân biệt với StringBuilder

~~~java
StringBuilder builder = new StringBuilder("A");
StringBuilder alias = builder;

builder.append("B");

System.out.println(builder); // AB
System.out.println(alias);   // AB
~~~

Hai biến `builder` và `alias` tham chiếu **cùng một đối tượng `StringBuilder` mutable**, nên thay đổi do `append()` đều quan sát được qua cả hai biến. `StringBuilder.append()` thay đổi builder; `String += ...` thay giá trị mà biến `String` giữ.

## 4. Cơ chế của String + và chuyển đổi sang văn bản

`s += value` dùng quy tắc nối `String`: số, `boolean`, `char` và đối tượng được chuyển thành văn bản. Giá trị `null` có thể trở thành chữ `"null"`:

```java
String label = "count=";
label += 42;
System.out.println(label); // count=42

String missing = null;
missing += "!";
System.out.println(missing); // null!
```

`+=` không định dạng số theo locale hoặc tự giải quyết dữ liệu thiếu. Đây là **chuyển đổi trong phép nối**; xem bài về `+` và `String.valueOf` để học bảng chuyển kiểu đầy đủ.

## 5. Số học, thứ tự toán tử và dấu ngoặc

`+` vừa có nghĩa **cộng số** vừa có nghĩa **nối chuỗi**, nên thứ tự tính toán có thể cho kết quả khác nhau.

~~~java
String first = "N";
first += 1 + 2;
System.out.println(first); // N3

String second = "N";
second += 1;
second += 2;
System.out.println(second); // N12
~~~

Ở ví dụ đầu, biểu thức ở bên phải `1 + 2` thực hiện cộng số trước, ra `3`, rồi mới được nối với `"N"`. Ví dụ thứ hai nối lần lượt từng số.

Tương tự:

~~~java
String a = "Result=";
a += (10 + 5);
System.out.println(a); // Result=15

String b = "Result=";
b = b + 10 + 5;
System.out.println(b); // Result=105
~~~

Biểu thức `b + 10 + 5` liên kết từ trái sang phải: bắt đầu bằng `String`, nên phép `+` tiếp theo thực hiện nối văn bản.

**Cách đọc an toàn:** Khi muốn cộng trước rồi chuyển sang văn bản, đặt phép số học trong dấu ngoặc. Khi muốn nối từng giá trị, thể hiện rõ dấu phân cách hoặc sử dụng formatter.

### += không nhất thiết giống gọi concat()

~~~java
String a = "ID";
a += 17;                // Hợp lệ: ID17

String b = "ID";
// b = b.concat(17);     // Không biên dịch: concat nhận String
b = b.concat("17");      // Hợp lệ: ID17
~~~

`concat(String)` yêu cầu đối số kiểu `String`; toán tử `+` với `String` có quy tắc chuyển đổi toán hạng linh hoạt hơn.

## 6. Sự khác biệt giữa += và các toán tử gán thường gặp

| Biểu thức | Cơ chế | Đối tượng String nguồn bị sửa? |
| --- | --- | --- |
| `s = "Hello"` | Gán tham chiếu tới chuỗi | Không |
| `s = s + "!"` | Tạo giá trị nối, gán kết quả | Không |
| `s += "!"` | Gán kết hợp: nối rồi gán lại | Không |
| `s.concat("!")` | Trả kết quả nối, **bỏ kết quả** | Không; `s` giữ nguyên |
| `s = s.concat("!")` | Nhận kết quả từ `concat()` | Không |
| `builder.append("!")` | Thay đổi nội dung `StringBuilder` | Không áp dụng cho `String`; builder mutable |

Ví dụ lỗi thường gặp:

~~~java
String s = "Hello";

s.concat("!");
System.out.println(s); // Hello

s += "!";
System.out.println(s); // Hello!
~~~

Điểm khác nằm ở việc **có gán kết quả hay không**. Khi gọi `concat()` mà bỏ kết quả, biến không đổi. `+=` bao gồm phép gán trong cú pháp của chính nó.

### Biến final không dùng được +=

~~~java
final String title = "Java";
// title += " Core"; // Lỗi biên dịch: không thể gán lại biến final
~~~

`final` ngăn việc gán lại biến. Dù bản thân `String` là immutable, `+=` vẫn là một phép **gán lại**, nên bị cấm trên biến `final`.

Ngược lại:

~~~java
final StringBuilder builder = new StringBuilder("Java");
builder.append(" Core"); // Hợp lệ: không gán lại biến builder
~~~

`final` không biến một đối tượng mutable thành immutable; nó chỉ cố định **tham chiếu** của biến.

## 7. String Pool và vấn đề dùng ==

`String` literal và biểu thức hằng có thể chia sẻ giá trị đã intern, nhưng `+=` thường tạo kết quả lúc chạy. `==` so tham chiếu; `equals()` so nội dung:

```java
String name = "Ja";
name += "va";
System.out.println(name.equals("Java")); // true
```

Danh tính kết quả không phải cơ sở để kiểm tra nội dung. Cơ chế intern/constant expression được giải thích riêng ở các bài về String pool và compiler.

## 8. Vế trái phức tạp: chỉ được đánh giá một lần

Đối với biến đơn giản, có thể đọc `s += x` như `s = s + x`. Tuy nhiên **quy tắc ngôn ngữ** của phép gán kết hợp là vế trái được đánh giá một lần.

~~~java
String[] values = {"A", "B"};
int index = 0;

values[index++] += "!";

System.out.println(index);     // 1
System.out.println(values[0]); // A!
System.out.println(values[1]); // B
~~~

`index++` chỉ thực hiện một lần, rồi Java nối vào phần tử `values[0]` và gán kết quả về đúng phần tử đó.

Không máy móc thay câu lệnh này thành:

~~~java
// KHÔNG tương đương, vì index++ có thể chạy hai lần:
// values[index++] = values[index++] + "!";
~~~

Ở dạng thay thế sai, biểu thức chỉ số có thể bị đánh giá hai lần và các phần tử khác nhau có thể bị đọc/ghi. Với field access, array access hoặc phương thức có tác dụng phụ trong vế trái, cần tuân theo quy tắc đánh giá cụ thể của Java.

### += với phần tử mảng String

~~~java
String[] labels = {"A", "B"};
String old = labels[0];

labels[0] += "1";

System.out.println(labels[0]); // A1
System.out.println(old);       // A
~~~

Phần tử mảng được **gán lại tham chiếu mới**. Đối tượng chuỗi cũ `"A"` vẫn bất biến; biến `old` tiếp tục giữ giá trị cũ.

## 9. Chi phí của nối lặp nhiều lần

Nối `result += part` lặp nhiều lần phải xây giá trị kết quả mới qua mỗi lượt; với dữ liệu tăng, `StringBuilder.append(part)` thường phù hợp hơn. Điều này không có nghĩa mọi `+=` ngắn đều đáng thay đổi:

```java
String greeting = "Hello";
greeting += ", Java"; // Rõ ý, hợp lệ
```

Bài **Nối trong một biểu thức so với tích lũy qua vòng lặp** phân tích mô hình chi phí và cách benchmark; ở bài này trọng tâm vẫn là **phép gán lại tham chiếu**.

## 10. Một chương trình hoàn chỉnh kiểm chứng Java 17+

Tạo file **`StringPlusEqualsDemo.java`**:

~~~java
public class StringPlusEqualsDemo {
    public static void main(String[] args) {
        String first = "Java";
        String alias = first;
        first += " Core";
        System.out.println("first=" + first);
        System.out.println("alias=" + alias);

        String missing = null;
        missing += "!";
        System.out.println("missing=" + missing);

        String number = "N";
        number += 1 + 2;
        System.out.println("number=" + number);

        String pieces = "N";
        pieces += 1;
        pieces += 2;
        System.out.println("pieces=" + pieces);

        String[] values = {"A", "B"};
        int index = 0;
        values[index++] += "!";
        System.out.println("index=" + index);
        System.out.println("values[0]=" + values[0]);
        System.out.println("values[1]=" + values[1]);

        StringBuilder builder = new StringBuilder("A");
        StringBuilder builderAlias = builder;
        builder.append("B");
        System.out.println("builder=" + builder);
        System.out.println("builderAlias=" + builderAlias);

        String text = "Ja";
        text += "va";
        System.out.println("equals=" + text.equals("Java"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 StringPlusEqualsDemo.java
java StringPlusEqualsDemo
~~~

**Stdout chính xác:**

~~~text
first=Java Core
alias=Java
missing=null!
number=N3
pieces=N12
index=1
values[0]=A!
values[1]=B
builder=AB
builderAlias=AB
equals=true
~~~

**Giải thích:**

1. `first` được gán kết quả nối, còn `alias` giữ chuỗi `"Java"`.
2. `missing` ban đầu `null` được chuyển thành đoạn văn bản `"null"`, nên có kết quả `"null!"`.
3. `number += 1 + 2` cộng số trước rồi nối `3`; `pieces` nối hai số riêng nên có `"N12"`.
4. `values[index++] += "!"` chỉ tăng `index` một lần và gán kết quả tại phần tử đầu tiên.
5. `StringBuilder.append()` sửa builder mutable nên `builderAlias` cũng đọc `"AB"`.
6. `equals()` xác nhận nội dung sau `+=` đúng, không phụ thuộc so sánh danh tính đối tượng.

Chương trình độc lập có một lớp public trùng tên file, chạy với Java 17 trở lên, không cần thư viện ngoài JDK.

## 11. Những ngộ nhận cần sửa

| Ngộ nhận | Sự thật |
| --- | --- |
| `s += x` sửa nội dung đối tượng `String` nguồn | Nó tạo giá trị kết quả rồi **gán lại biến** |
| Mọi biến cùng tham chiếu `String` đều thấy giá trị mới sau `+=` | Chỉ vế trái được gán lại; tham chiếu khác giữ giá trị cũ |
| `+=` luôn an toàn khi `s == null` | Có thể tạo văn bản `"null..."` ngoài ý muốn |
| `s += 1 + 2` luôn ra `"...12"` | `1 + 2` thực hiện cộng số trước, ra `3` |
| `s += value` luôn giống thay chữ bằng `s = s + value` | Với vế trái có tác dụng phụ, quy tắc đánh giá một lần rất quan trọng |
| Biến `final String` dùng `+=` được vì String bất biến | `+=` là gán lại, nên **bị lỗi biên dịch** |
| Gọi `s.concat(x)` rồi bỏ kết quả vẫn đổi `s` | Không: String bất biến, phải nhận kết quả |
| `==` đủ để so nội dung sau phép nối | Dùng `String.equals()` |
| Dùng `+=` trong vòng lặp lớn luôn tối ưu | Cân nhắc `StringBuilder`, đo hiệu năng khi cần |
| `StringBuilder.append()` giống `String +=` | `append()` sửa builder; `+=` trên String gán lại tham chiếu |
| `String` mới nào cũng chứa cùng tham chiếu như literal bằng nội dung | Không dựa vào pooling để suy luận danh tính kết quả runtime |

## 12. Bài tập tự luyện

### Bài 1 — Dự đoán hai biến

~~~java
String a = "Hello";
String b = a;
a += "!";
System.out.println(a);
System.out.println(b);
~~~

Dự đoán và giải thích vì sao một biến cho `"Hello!"`, biến còn lại cho `"Hello"`.

### Bài 2 — null khác lỗi phương thức

Chạy `String s = null; s += "X";`. So với `s.concat("X")` khi `s` còn `null`. Giải thích chuỗi văn bản `"nullX"` khác trạng thái thiếu dữ liệu và vì sao không nên chấp nhận nó tự động.

### Bài 3 — Dấu ngoặc số học

Viết ba biểu thức nối từ `"N"` với `1` và `2`: một biểu thức trả `"N3"`, một trả `"N12"`, một trả `"N1,2"`. Dùng dấu ngoặc hoặc dấu phân cách để thể hiện ý định.

### Bài 4 — final

Thử biên dịch:

~~~java
final String code = "A";
// code += "B";
~~~

Bỏ dấu chú thích của dòng thứ hai để quan sát lỗi compiler. Sau đó thử `final StringBuilder builder = new StringBuilder("A"); builder.append("B");` và giải thích sự khác biệt.

### Bài 5 — Mảng và tác dụng phụ

Cho `String[] a = {"X", "Y"}; int i = 0;`, thực hiện `a[i++] += "?";`. Dự đoán `i` và từng phần tử, sau đó giải thích vì sao một phép khai triển dùng hai lần `i++` sẽ làm sai ngữ nghĩa.

### Bài 6 — Nối số trong vòng lặp

Tạo chuỗi số từ `0` đến `9999` theo hai cách: nối `+=` trong vòng lặp và `StringBuilder.append()`. Xác minh **nội dung kết quả giống nhau** và so sánh thiết kế/độ phức tạp. Nếu benchmark, làm nóng JVM, lặp nhiều lần, tránh dùng một phép đo thời gian duy nhất làm kết luận.

### Bài 7 — Không bỏ kết quả

Cho:

~~~java
String title = "Java";
title.concat(" Core");
~~~

Sửa đoạn code để `title` có nội dung `"Java Core"` bằng hai cách: `title = title.concat(" Core");` và `title += " Core";`. Giải thích điểm chung của hai cách.

### Bài 8 — Giữ bản gốc

Tạo `String raw = "  Java  "; String display = raw;`. Cho `display += " Core";`. Kiểm tra `raw` giữ nguyên. Nếu muốn loại khoảng trắng ở `display`, cần áp dụng `strip()` cho kết quả rồi nhận lại giá trị trả về.

## 13. Checklist hoàn thành bài học

Trước khi dùng `+=` với `String`, tự hỏi:

- Biến vế trái có phải `String` và có thể **gán lại** không?
- Mình đang nối văn bản hay muốn làm phép số học trước? Có cần dấu ngoặc?
- Dữ liệu vế trái/đối số có thể là `null`? Có muốn xuất hiện chữ `"null"` không?
- Có biến nào khác đang giữ tham chiếu đến chuỗi gốc không?
- Có đang lặp nối rất nhiều lần? `StringBuilder` có rõ ràng/hiệu quả hơn không?
- Mình đang so kết quả bằng `equals()` thay vì dựa vào `==`?
- Nếu vế trái là một phần tử mảng hoặc field, việc đánh giá biểu thức có tác dụng phụ không?

**Điểm nhớ lâu:** với `String`, `s += part` nghĩa là **tính kết quả ghép văn bản rồi gán kết quả cho `s`**. Nội dung của đối tượng `String` cũ không đổi; các tham chiếu khác tới nó cũng không đổi. Đây là cơ chế bình thường của Java, không có thao tác sửa `String` tại chỗ.

## 14. Tài liệu chuẩn

- [Java Language Specification 17 — Compound Assignment Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.26.2): quy tắc `+=`, đánh giá vế trái một lần và phép gán.
- [Java Language Specification 17 — String Concatenation Operator](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18.1): phép `+` trên `String`, string conversion và biểu thức hằng.
- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): tính bất biến, `concat()`, `equals()` và API chuỗi.
- [Java SE 17 — StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): `append()`, mutable builder và `toString()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-concatenation-006">Chuyển Object qua toString khi tham gia nối chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển Object qua toString khi tham gia nối chuỗi trong Java

Trong Java, toán tử `+` không chỉ cộng các số. Khi **ít nhất một toán hạng là `String`**, nó thực hiện **nối chuỗi** (*string concatenation*). Nếu toán hạng còn lại là một đối tượng, Java áp dụng **string conversion** để lấy biểu diễn văn bản của giá trị đó; với đối tượng không phải `null`, hành vi thông thường được xác định thông qua phương thức `toString()`.

Ví dụ:

```java
Object value = Integer.valueOf(42);
String message = "Giá trị: " + value;
System.out.println(message); // Giá trị: 42
```

Ở đây `Integer` cung cấp `toString()`, nên phần được nối là văn bản `42`. Tuy nhiên, **không được hiểu rằng mọi `Object` tự động có biểu diễn dễ đọc**: nếu lớp không ghi đè `toString()`, kết quả thường có dạng `TenLop@maHex`; và với `null`, Java nối chuỗi văn bản `"null"` thay vì gọi phương thức trên tham chiếu rỗng.

Bài này tập trung vào **cách giá trị Object được chuyển thành văn bản khi tham gia phép nối**, không chỉ cách sử dụng toán tử `+`.

## 1. String conversion khi dùng toán tử +

Với biểu thức:

```java
String result = "Item: " + someObject;
```

nếu `someObject` có kiểu tham chiếu như `Book`, `Object`, `List<?>` hoặc một kiểu tự định nghĩa, Java cần một biểu diễn `String` của giá trị ở phía phải. Về mặt **mô hình ngữ nghĩa**:

1. Đánh giá các toán hạng của phép `+` theo quy tắc đánh giá biểu thức Java.
2. Nếu một toán hạng thuộc kiểu `String`, phép `+` là nối chuỗi; toán hạng còn lại được chuyển sang biểu diễn chuỗi thích hợp.
3. Với một **tham chiếu đối tượng khác `null`**, hành vi biểu diễn văn bản gắn với `toString()` của kiểu thực tế.
4. Với một **tham chiếu `null`**, kết quả string conversion là bốn chữ cái `"null"`.
5. Kết hợp các phần văn bản để tạo giá trị `String` của biểu thức nối.

**Lưu ý kỹ thuật:** Đây là quy tắc **quan sát được ở cấp ngôn ngữ**, không phải cam kết rằng bytecode phải gọi tường minh `String.valueOf(Object)` hay tạo `StringBuilder`. Trình biên dịch/JVM được phép sử dụng chiến lược tối ưu khác, miễn giữ đúng ngữ nghĩa Java.

Khi đọc:

```java
String line = "User=" + user;
```

có thể hình dung rằng Java cần biểu diễn `user` theo ý nghĩa của `String.valueOf(user)` nếu `user` là tham chiếu đối tượng. Nhưng đừng kết luận “Java nhất định biên dịch thành lời gọi `user.toString()` ở vị trí đó”: cơ chế thực thi cụ thể còn phụ thuộc compiler và JDK.

### Những giá trị nào được chuyển thành văn bản?

| Loại toán hạng | Ví dụ | Biểu diễn khi nối |
|---|---|---|
| `String` | `"abc"` | Nội dung chuỗi đó |
| Số nguyên primitive | `42` | Văn bản `"42"` |
| `boolean` | `true` | Văn bản `"true"` |
| `char` | `'A'` | Văn bản `"A"` |
| Đối tượng có `toString()` hữu ích | `new Book("Java")` | Giá trị do `Book.toString()` tạo |
| Đối tượng không ghi đè `toString()` | `new Object()` | Mẫu mặc định `TenLop@hexHash`, không phải nội dung nghiệp vụ |
| Tham chiếu `null` | `(Object) null` | `"null"` |
| Array như `int[]` | `new int[]{1,2}` | Thường là biểu diễn identity mặc định, **không** phải `"[1, 2]"` |

Quy tắc với primitive liên quan đến **chuyển đổi dữ liệu nguyên thủy thành văn bản**; primitive **không có** phương thức instance `toString()`. Chỉ giá trị kiểu tham chiếu có thể gọi `toString()`.

## 2. Vai trò của Object.toString()

Mọi lớp Java đều kế thừa hoặc có thể ghi đè `toString()` từ `java.lang.Object`. Chữ ký:

```java
public String toString()
```

Phiên bản mặc định của `Object` tạo một chuỗi đại khái theo dạng:

```text
Tên_lớp@giá_trị_hash_hệ_16
```

Ví dụ dạng `com.example.User@5e2de80c`. **Giá trị hash, tên lớp đầy đủ và số lần tạo đối tượng không nên được dùng làm output cố định trong bài kiểm thử.** Đây không phải định dạng dành cho người dùng cuối hoặc dữ liệu có tính ổn định.

Một lớp nên ghi đè `toString()` nếu ứng dụng cần biểu diễn văn bản có ý nghĩa trong log hoặc khi ghép vào thông báo.

```java
final class Book {
    private final String title;

    Book(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return "Book(" + title + ")";
    }
}
```

Đây là **định nghĩa lớp minh họa**. Với `new Book("Java")`:

```java
Book book = new Book("Java");
System.out.println("Item: " + book); // Item: Book(Java)
```

Phần văn bản `Book(Java)` do **`Book.toString()` quyết định**. Toán tử `+` không biết `title` là trường gì và không tự đọc các trường của đối tượng.

### Tính đa hình

Khi biến được khai báo kiểu cha `Object` nhưng thực sự trỏ đến một `Book`, `Book.toString()` vẫn được dùng:

```java
Object item = new Book("Java");
String line = "Item: " + item; // Item: Book(Java)
```

Chính **kiểu đối tượng lúc chạy** quyết định phương thức ghi đè nào được thực thi, không phải chỉ kiểu tĩnh `Object` của biến.

Nếu lớp không ghi đè `toString()`, Java sử dụng triển khai được kế thừa. Điều này giải thích vì sao đôi khi log chỉ hiện dạng `Book@...` thay vì trường `title` mong muốn.

## 3. Null: không giống gọi toString() trực tiếp

Đây là sự khác biệt dễ gây nhầm nhất:

```java
Object missing = null;

String a = "Value: " + missing;
String b = String.valueOf(missing);

System.out.println(a); // Value: null
System.out.println(b); // null
```

Hai biểu thức trên **không ném `NullPointerException`**. Với toán tử nối chuỗi, tham chiếu `null` được chuyển thành văn bản `"null"`; `String.valueOf(Object)` cũng xử lý `null` tương ứng.

Ngược lại:

```java
Object missing = null;
// missing.toString(); // Nếu thực thi: NullPointerException
```

**Không có object nào để gọi phương thức** khi receiver là `null`. Java không lén gọi `toString()` trên `null` lúc nối chuỗi.

Một bảng phân biệt:

| Biểu thức | Kết quả |
|---|---|
| `"x=" + (Object) null` | `"x=null"` |
| `String.valueOf((Object) null)` | `"null"` |
| `Objects.toString(null)` | `"null"` |
| `Objects.toString(null, "N/A")` | `"N/A"` |
| `((Object) null).toString()` | Ném `NullPointerException` |

Các ví dụ với `Objects` cần `import java.util.Objects;`. Lưu ý phương thức `Objects.toString(obj, nullDefault)` cho phép chọn chuỗi thay thế khi đối tượng `null` — phù hợp khi muốn hiển thị `"(chưa có)"` hoặc `"N/A"` thay vì chuỗi `"null"`.

### Cẩn thận với String.valueOf(null) không ép kiểu

Java có nhiều overload `String.valueOf(...)`, trong đó có `valueOf(Object)` và `valueOf(char[])`. Khi viết:

```java
// String.valueOf(null); // Có thể chọn overload char[] rồi ném NullPointerException
String result = String.valueOf((Object) null); // "null"
```

Dòng được chú thích chỉ để giải thích **overload resolution**. Việc ép kiểu `(Object) null` giúp chọn đúng overload xử lý tham chiếu đối tượng. Trong mã thông thường khi biến đã có kiểu tĩnh `Object`, `String.valueOf(variable)` sẽ chọn overload `Object`.

## 4. String.valueOf(Object) có giống toString() không?

`String.valueOf(Object obj)` cung cấp một điểm gọi dễ dùng để biến tham chiếu thành `String`. Với một object không null, hàm sử dụng biểu diễn do `obj.toString()` trả về; với `null`, hàm trả chuỗi `"null"`.

```java
Book book = new Book("Java");

String a = book.toString();
String b = String.valueOf(book);
String c = "" + book;

System.out.println(a.equals(b)); // true
System.out.println(b.equals(c)); // true
```

Ví dụ trên giả định định nghĩa `Book` ở mục 2. Tất cả có cùng **nội dung**, không nên dùng `==` để suy luận rằng các biến nhất thiết tham chiếu cùng một object `String`.

Cần phân biệt ba câu hỏi:

- **`toString()` trả văn bản gì?** Phụ thuộc cách lớp cài đặt.
- **`String.valueOf(...)` xử lý null và overload ra sao?** Phụ thuộc kiểu đối số đã được trình biên dịch chọn.
- **Toán tử `+` nối chuỗi tạo kết quả gì?** Theo quy tắc *string conversion* và *string concatenation* của Java.

Một `toString()` được thiết kế kém cũng có thể ném exception hoặc sinh văn bản không ổn định. `String.valueOf` và toán tử `+` **không tự xác thực** hay bảo đảm nội dung do `toString()` cung cấp phù hợp với nghiệp vụ.

## 5. Chương trình Java 17+ chạy độc lập

Ví dụ dưới đây tránh in giá trị identity-hash không dự đoán được của mảng, thay vào đó **so sánh hành vi** và dùng `Arrays.toString` để in dữ liệu mảng theo định dạng rõ ràng.

Lưu thành **`ObjectConcatDemo.java`**:

```java
import java.util.Arrays;

public class ObjectConcatDemo {
    static final class Book {
        private final String title;

        Book(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return "Book(" + title + ")";
        }
    }

    static final class Tracked {
        static int calls;

        @Override
        public String toString() {
            calls++;
            return "tracked";
        }
    }

    public static void main(String[] args) {
        Object book = new Book("Java");
        Object missing = null;

        System.out.println("item: " + book);
        System.out.println("missing: " + missing);
        System.out.println("valueOf null: " + String.valueOf(missing));
        System.out.println("valueOf matches: "
                + String.valueOf(book).equals("Book(Java)"));
        System.out.println("book content equals: "
                + book.equals("Book(Java)"));

        int[] numbers = {1, 2, 3};
        System.out.println("array readable: "
                + Arrays.toString(numbers));
        System.out.println("array concat is readable: "
                + ("Array: " + numbers).equals("Array: [1, 2, 3]"));

        Tracked tracked = new Tracked();
        System.out.println("tracked: " + tracked);
        System.out.println("toString calls: " + Tracked.calls);

        System.out.println("math1: " + ("sum=" + 7 + 3));
        System.out.println("math2: " + ("sum=" + (7 + 3)));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ObjectConcatDemo.java
java ObjectConcatDemo
```

Kết quả:

```text
item: Book(Java)
missing: null
valueOf null: null
valueOf matches: true
book content equals: false
array readable: [1, 2, 3]
array concat is readable: false
tracked: tracked
toString calls: 1
math1: sum=73
math2: sum=10
```

**Phân tích:**

1. `book` được khai báo là `Object` nhưng trỏ đến `Book`; phương thức `Book.toString()` trả `"Book(Java)"`.
2. `missing` là `null`, nên tham gia `"missing: " + missing` thành văn bản `"null"` và không ném exception.
3. `String.valueOf(missing)` trả `"null"`. Với `book`, `String.valueOf(book)` có nội dung bằng `"Book(Java)"`.
4. `book.equals("Book(Java)")` là `false` vì `Book` trong ví dụ không ghi đè `equals`; kế thừa cách so sánh identity của `Object`. **Biểu diễn text giống nhau không làm hai giá trị thuộc hai kiểu trở thành bằng nhau**.
5. Mảng `int[]` không tự chuyển thành `"[1, 2, 3]"` khi nối. `Arrays.toString(numbers)` mới là phương pháp tường minh để hiển thị nội dung mảng một chiều.
6. `Tracked.toString()` tăng biến đếm `calls` mỗi lần được gọi. Phép nối đơn giản `"tracked: " + tracked` tạo `tracked: tracked` và có **một lần gọi** trên đối tượng này trong chương trình.
7. `+` là toán tử **kết hợp trái** (*left-associative*). `"sum=" + 7 + 3` được hiểu như `("sum=" + 7) + 3` nên ra `sum=73`, còn `"sum=" + (7 + 3)` cộng số trước rồi thành `sum=10`.

Ví dụ cho thấy Java quyết định **khi nào `+` là phép cộng số và khi nào là nối chuỗi**, còn cách chuyển một `Object` thành văn bản là vấn đề riêng.

## 6. Mảng là trường hợp rất hay bị hiểu sai

Mảng Java cũng là đối tượng, nhưng `int[]`, `String[]` và mảng nhiều chiều **không mặc nhiên override `toString()` để hiển thị từng phần tử**. Vì thế:

```java
int[] scores = {7, 8, 9};
String message = "Scores: " + scores;
```

`message` **không** có dạng `"Scores: [7, 8, 9]"`. Nó sẽ chứa chuỗi biểu diễn đối tượng mảng, thường gồm tên kiểu JVM của mảng và mã hex liên quan identity; giá trị cụ thể không ổn định để kiểm thử bằng cách so sánh toàn bộ.

Nếu cần trình bày dữ liệu, dùng:

```java
import java.util.Arrays;

int[] scores = {7, 8, 9};
String formatted = "Scores: " + Arrays.toString(scores);
System.out.println(formatted); // Scores: [7, 8, 9]
```

Với mảng nhiều chiều:

```java
int[][] matrix = {{1, 2}, {3, 4}};
System.out.println("matrix: " + Arrays.deepToString(matrix));
// matrix: [[1, 2], [3, 4]]
```

Hai đoạn trên là **đoạn mã minh họa**: cần đặt trong method phù hợp và có import `java.util.Arrays`. Nếu giá trị là mảng đối tượng có cấu trúc lồng nhau, cân nhắc `Arrays.deepToString(...)`. Nó hữu ích cho debug, nhưng khi xuất định dạng trao đổi dữ liệu chính thức (JSON, CSV, v.v.) nên dùng thư viện/serializer có hợp đồng rõ ràng.

### Trường hợp đặc biệt: char[]

Có sự khác biệt dễ gây bất ngờ giữa **nối chuỗi** và **chọn overload của println/valueOf**:

```java
char[] letters = {'J', 'a', 'v', 'a'};

System.out.println(letters);               // Java
System.out.println(String.valueOf(letters)); // Java
System.out.println("prefix: " + letters);   // Thường là prefix: [C@...
```

`System.out.println(char[])` có overload riêng để in **các ký tự trong mảng**, và `String.valueOf(char[])` cũng có overload chuyên biệt. Nhưng trong `"prefix: " + letters`, array được tham gia **string conversion** theo quy tắc nối, không phải tự động gọi overload in `char[]`. Không nên trông chờ kết quả giống hai dòng đầu.

Đây là lý do việc chỉ nhìn kết quả `println(someValue)` có thể gây hiểu nhầm về **hành vi riêng của phép `"prefix: " + someValue`**: `println` có nhiều overload, còn toán tử `+` tuân theo quy tắc của ngôn ngữ.

## 7. toString() mặc định, equals() và hashCode() không phải một

Ba khái niệm cần tách biệt:

- **`toString()`**: trả biểu diễn văn bản của đối tượng.
- **`equals(Object)`**: định nghĩa tính bằng nhau của đối tượng (nếu lớp ghi đè).
- **`hashCode()`**: dùng trong các cấu trúc dựa trên hash và phải tuân theo hợp đồng với `equals`.

Việc ghi đè `toString()` **không tự làm thay đổi** `equals` hoặc `hashCode`. Nếu hai đối tượng có cùng `toString()`, không có nghĩa chúng bằng nhau, cũng không có nghĩa chúng là cùng tham chiếu.

Ví dụ:

```java
Book one = new Book("Java");
Book two = new Book("Java");

System.out.println(one.toString().equals(two.toString())); // true
System.out.println(one == two);                             // false
System.out.println(one.equals(two));                        // false
```

Đoạn ví dụ dùng lớp `Book` đã khai báo ở trên, **không** override `equals`. Nếu một domain entity cần so sánh giá trị, hãy tự thiết kế `equals`/`hashCode` hoặc dùng một kiểu có ngữ nghĩa giá trị rõ ràng như `record` khi phù hợp. Không dùng `toString()` để thay thế phép so sánh giá trị.

Tương tự, `Object.toString()` mặc định thường liên quan `hashCode()` trong phần văn bản sau dấu `@`, nhưng biểu diễn hex **không phải** ID duy nhất, không ổn định giữa các lần chạy và không nên dùng làm khóa nghiệp vụ.

## 8. Lúc nào toString() được gọi và có tác dụng phụ gì?

Một phương thức `toString()` có thể thực hiện công việc tùy ý: tính toán, đọc field, truy cập trạng thái hoặc thậm chí ném exception. Khi object tham gia nối chuỗi, **cách biểu diễn của nó được đánh giá trong quá trình thực thi phép nối**. Đây không phải việc Java “lưu sẵn” bản in của object khi tạo đối tượng.

Ví dụ, nếu một đối tượng thay đổi nội dung, các lần nối chuỗi diễn ra ở thời điểm khác nhau có thể sinh text khác nhau:

```java
final class Counter {
    int value;

    @Override
    public String toString() {
        return "Counter(" + value + ")";
    }
}
```

Trong một method:

```java
Counter counter = new Counter();
String before = "State: " + counter;
counter.value = 2;
String after = "State: " + counter;

System.out.println(before); // State: Counter(0)
System.out.println(after);  // State: Counter(2)
```

Việc dùng `toString()` chỉ để định dạng **không làm cho đối tượng bất biến**. Nếu `toString()` ném unchecked exception, thao tác tạo chuỗi cũng có thể thất bại. Điều này quan trọng khi ghi log hoặc thông báo lỗi: một `toString()` có side effect hay phụ thuộc dịch vụ ngoài có thể khiến chẩn đoán khó khăn.

**Khuyến nghị thiết kế:** `toString()` nên dễ hiểu, có chi phí hợp lý, không phụ thuộc dịch vụ ngoài khi có thể, và không vô tình tiết lộ password, access token, thông tin tài chính hay dữ liệu nhạy cảm. Ghi log bằng `"user=" + user` sẽ đưa kết quả `toString()` vào log; cần chủ động kiểm soát trường nào được xuất.

### Những vòng gọi đệ quy cần tránh

Nếu trong `toString()` lại nối chính `this`:

```java
@Override
public String toString() {
    return "Self: " + this; // Sai: liên tục gọi lại toString()
}
```

có thể xảy ra đệ quy vô hạn cho tới `StackOverflowError`. Thay vào đó, xây dựng text từ các field cụ thể hoặc kiểu dữ liệu đơn giản. Khi object graph có chu trình, một số cách ghép `toString()` qua các đối tượng liên kết cũng có thể gây đệ quy; cần cách in có nhận diện vòng lặp nếu dữ liệu có cấu trúc như vậy.

## 9. Thứ tự đánh giá, cộng số và nối chuỗi

Với `String` ở bên trái, `"sum=" + 7 + 3` cho `"sum=73"`; ngoặc trong `"sum=" + (7 + 3)` cho `"sum=10"`. Quy tắc này giống các phép nối khác và không làm `Object` nguồn biến thành `String`. Bài **Thứ tự kết hợp trái–phải** dành riêng cho phân tích cú pháp, còn ở đây chú ý **hàm `toString()` của đối tượng được gọi lúc nào và có tác dụng phụ gì**.

## 10. StringBuilder.append(Object) và các cách định dạng khác

Khi xây dựng text từ nhiều phần trong vòng lặp, thường sử dụng `StringBuilder`:

```java
Object book = new Book("Java");

String message = new StringBuilder()
        .append("Item: ")
        .append(book)
        .toString();
```

`StringBuilder.append(Object)` dùng biểu diễn chuỗi của đối tượng (theo hợp đồng `String.valueOf(Object)`), do đó ví dụ cũng sinh `"Item: Book(Java)"`. Với `null`, nó đưa văn bản `"null"` vào builder.

**Tuy nhiên `StringBuilder` có nhiều overload** như `append(char[])` và `append(int)`. Nếu truyền mảng `char[]` trực tiếp, overload `append(char[])` có thể được chọn và nối các ký tự; còn `append((Object) letters)` dùng hướng xử lý đối tượng. Vì thế, so sánh behavior luôn phải xét **kiểu tĩnh của biểu thức và overload nào được chọn**.

### Có phải nối chuỗi bằng + luôn tạo StringBuilder?

Không. Nhiều ví dụ và tài liệu JVM cũ mô tả compiler “dịch phép nối thành `StringBuilder`”, nhưng đó không phải quy tắc bất biến của ngôn ngữ. Trên các JDK hiện đại, compiler thường dùng cơ chế **`invokedynamic` với string-concat recipes** (JEP 280) và JVM có thể tối ưu thêm. Hợp đồng mà mã ứng dụng dựa vào là **giá trị và ngữ nghĩa của biểu thức nối**, không phải một bytecode hay cấu trúc builder cụ thể.

Có thể khảo sát bằng:

```bash
javac ObjectConcatDemo.java
javap -c -p ObjectConcatDemo
```

Trong bytecode của compiler/JDK phù hợp có thể thấy `invokedynamic` cho string concatenation. Đừng suy luận hiệu năng chỉ từ số lần nhìn thấy `new StringBuilder` trong một output: JVM có thể áp dụng nhiều tối ưu, và chi phí `toString()` do lớp tự định nghĩa đôi khi đáng kể hơn thao tác nối.

Nếu cần đo đạc, dùng benchmark nghiêm túc với JMH; không kết luận `+` luôn chậm hơn `StringBuilder` trong mọi ngữ cảnh.

### Khi nào nên định dạng rõ ràng thay vì nối Object?

- **Mảng:** `Arrays.toString` hoặc `Arrays.deepToString`.
- **Ngày/giờ:** dùng `java.time.format.DateTimeFormatter` khi cần format xác định.
- **Số tiền:** dùng `NumberFormat`/`DecimalFormat` cùng locale và quy tắc làm tròn phù hợp.
- **Dữ liệu JSON/XML:** sử dụng serializer đúng chuẩn, **không** coi `toString()` là JSON.
- **Thông báo cho người dùng:** dùng template/formatter rõ ràng và tránh đưa toàn bộ object nội bộ ra giao diện.
- **Log:** `toString()` có thể hữu ích khi dữ liệu không nhạy cảm, nhưng cần kiểm soát nội dung, chi phí và exception.

## 11. Các nhầm lẫn và lỗi thường gặp

| Nhầm lẫn | Vì sao sai | Cách hiểu/khắc phục |
|---|---|---|
| “Nối Object thì Java gọi `toString()` trên cả null.” | `null` không có instance để gọi method; phép nối tạo `"null"` theo string conversion | Phân biệt `"x=" + nullReference` với `nullReference.toString()` |
| “Mọi object tự động nối thành các giá trị field.” | `Object.toString()` mặc định thường chỉ in tên lớp và hash | Override `toString()` khi cần, hoặc định dạng trường tường minh |
| “`"Array: " + array` liệt kê phần tử.” | Mảng không có `toString()` hiển thị nội dung | Dùng `Arrays.toString`/`deepToString` |
| “`System.out.println(charArray)` và `"x=" + charArray` hoạt động giống nhau.” | `println` có overload `char[]`; toán tử nối dùng string conversion | Xác định API và kiểu tĩnh của đối số |
| “`String.valueOf(null)` luôn trả `"null"`.” | Overload `char[]` có thể được chọn cho literal `null` | Chỉ rõ `String.valueOf((Object) null)` hoặc dùng biến `Object` |
| “`toString()` cùng text thì `equals` phải true.” | Text representation không định nghĩa equality | Thiết kế `equals/hashCode` riêng |
| “Object được đổi thành String vĩnh viễn sau phép nối.” | `+` chỉ tạo giá trị chuỗi kết quả | Biến nguồn giữ nguyên kiểu và tham chiếu |
| “Kết quả `Object.toString()` mặc định là ID ổn định.” | Dạng `Class@hex` không phải khóa nghiệp vụ | Dùng ID do hệ thống quản lý |
| “`toString()` không thể gây lỗi.” | Override có thể throw hoặc đệ quy | Giữ `toString()` đơn giản và an toàn |
| “Java bắt buộc biên dịch nối chuỗi thành `new StringBuilder`.” | Chi tiết compiler/JDK có thể thay đổi | Dựa vào ngữ nghĩa JLS, không hardcode chiến lược bytecode |

## 12. Bài tập tự kiểm tra

### Bài 1: null và override

Cho:

```java
final class User {
    final String name;
    User(String name) { this.name = name; }

    @Override
    public String toString() {
        return "User(" + name + ")";
    }
}

Object a = new User("Lan");
Object b = null;

System.out.println("a=" + a);
System.out.println("b=" + b);
```

**Đáp án:**

```text
a=User(Lan)
b=null
```

Dòng thứ nhất dùng `User.toString()` qua đa hình; dòng thứ hai dùng string conversion của tham chiếu null.

### Bài 2: khác biệt phép cộng và phép nối

Dự đoán:

```java
Object number = Integer.valueOf(8);
System.out.println("n=" + number + 2);
System.out.println("n=" + (8 + 2));
```

**Đáp án:**

```text
n=82
n=10
```

Sau khi xuất hiện `String` phía trái, các phép `+` còn lại là nối văn bản. Biểu thức trong ngoặc trước hết thực hiện cộng hai số.

### Bài 3: mảng

Cho:

```java
String[] names = {"Lan", "Minh"};
String a = "names=" + names;
String b = "names=" + java.util.Arrays.toString(names);
```

Hỏi biểu thức nào hiển thị `names=[Lan, Minh]`? **Đáp án:** chỉ `b`; `a` biểu diễn đối tượng mảng chứ không tự nối các phần tử.

### Bài 4: giá trị đối tượng và giá trị String

Một lớp `Customer` override `toString()` trả `"VIP"`. Hai đối tượng khác nhau đều có `toString()` bằng `"VIP"`. Điều đó có làm `customer1 == customer2` đúng không? **Không.** Hai tham chiếu khác nhau vẫn khác identity. Việc hai `String` có nội dung giống nhau không thay đổi equality của hai đối tượng nguồn.

## 13. Tài liệu chính thức

- [Java Language Specification `15.18.1 — String Concatenation Operator +`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18.1): quy tắc nối chuỗi của toán tử `+`.
- [Java Language Specification `5.1.11 — String Conversion`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.11): xử lý giá trị primitive, tham chiếu đối tượng và null khi chuyển sang chuỗi.
- [Java SE 17 — `Object.toString()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#toString()): hành vi và mục đích của method.
- [Java SE 17 — `String.valueOf(Object)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#valueOf(java.lang.Object)): chuyển object hoặc null thành text.
- [JEP 280 — Indify String Concatenation](https://openjdk.org/jeps/280): minh họa lý do không nên đồng nhất phép nối Java với một chiến lược bytecode cụ thể.

**Ghi nhớ:** Khi một `Object` tham gia nối với `String` bằng `+`, Java thực hiện *string conversion*: với object khác null, biểu diễn phụ thuộc `toString()` của kiểu thực lúc chạy; với null, văn bản là `"null"`. **`toString()` chỉ là biểu diễn văn bản, không phải identity, equality hay serializer**, và với mảng hoặc dữ liệu nghiệp vụ nên định dạng tường minh khi cần đầu ra ổn định, dễ hiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-concatenation-007">Nối trong một biểu thức so với tích lũy qua vòng lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Nối chuỗi trong một biểu thức so với tích lũy qua vòng lặp trong Java

Trong Java, **cách nối chuỗi nên phụ thuộc vào cách dữ liệu được tạo ra**. Nếu chỉ ghép vài thành phần trong **một biểu thức**, toán tử `+` thường rõ ràng, dễ bảo trì và được trình biên dịch/JVM hiện đại tối ưu. Nếu **tích lũy văn bản sau nhiều lần lặp**, sử dụng `StringBuilder` hoặc một API ghép phù hợp thường tránh được việc liên tục tạo chuỗi trung gian và sao chép tiền tố đã có.

Ví dụ hai cách dưới đây đều có thể cho kết quả `"Java 17"`, nhưng chúng diễn đạt những công việc khác nhau:

```java
String oneExpression = "Java" + " " + 17; // Một biểu thức nối.

StringBuilder builder = new StringBuilder();
builder.append("Java");
builder.append(" ");
builder.append(17);
String accumulated = builder.toString();   // Tích lũy nhiều bước.
```

Không có quy tắc “**mọi phép `+` đều chậm**” hay “**luôn phải dùng `StringBuilder`**”. Cần phân biệt nơi mã nguồn tạo một biểu thức nối với nơi chương trình xây kết quả qua nhiều vòng lặp hoặc nhiều nhánh.

## 1. Cơ sở: `String` bất biến và kết quả của phép nối

Đối tượng `String` là *immutable*: giá trị văn bản bên trong một đối tượng đã tạo không thể bị sửa qua API thông thường. Do đó:

```java
String first = "Java";
String second = first + " 17";

System.out.println(first);  // Java
System.out.println(second); // Java 17
```

Biểu thức nối tạo ra **giá trị chuỗi kết quả**, không thêm ký tự vào đối tượng `first` đang tồn tại. Trong tình huống có giá trị mới khác giá trị cũ, cần có một đối tượng `String` biểu diễn kết quả mới. Cách JVM tạo kết quả có thể được tối ưu tùy JDK, nhưng tính bất biến của đối tượng là hợp đồng không đổi.

Phép gán cộng `+=` cũng không biến `String` thành kiểu mutable:

```java
String message = "A";
String oldMessage = message;

message += "B";  // Tương đương về kết quả với message = message + "B";

System.out.println(message);    // AB
System.out.println(oldMessage); // A
```

Biến `message` nhận tham chiếu tới giá trị mới; `oldMessage` tiếp tục giữ nội dung `"A"`. Nếu một vòng lặp thực hiện `message += item` nhiều lần, mỗi vòng về mặt ngữ nghĩa đang xây **giá trị kết quả kế tiếp** từ giá trị trước.

## 2. Nối vài thành phần trong **một biểu thức**: dùng `+` cho dễ đọc

Cú pháp `+` với ít nhất một toán hạng là `String` thực hiện nối văn bản:

```java
String user = "An";
int points = 12;
String label = "User: " + user + ", points: " + points;
// label = "User: An, points: 12"
```

Một biểu thức có nhiều phần vẫn là một đơn vị mã nguồn rõ ràng: JVM có thể xây kết quả mà không cần lặp qua một chuỗi tiền tố được mở rộng thủ công. Ví dụ thường phù hợp:

- Tạo thông điệp ngắn từ hai đến vài biến: `"Hello, " + name + "!"`.
- Tạo nhãn hiển thị: `"Score: " + score`.
- Ghép một vài thành phần cấu hình nếu cú pháp dễ hiểu và dữ liệu đã được kiểm tra.

Với các toán hạng là **hằng thời gian biên dịch**, trình biên dịch có thể nối thành một hằng chuỗi ngay khi biên dịch:

```java
String s = "Ja" + "va"; // Constant expression, giá trị "Java".
```

Đây là lý do không thể suy luận rằng `"Ja" + "va"` phải cấp phát các chuỗi trung gian tại mỗi lần chạy.

### Java hiện đại tối ưu phép nối như thế nào?

Trước Java 9, nhiều phép nối chuỗi được trình biên dịch triển khai tương đương với tạo và sử dụng `StringBuilder`. Từ **Java 9**, JEP 280 (*Indify String Concatenation*) đưa ra cơ chế tạo bytecode bằng `invokedynamic`, cho phép runtime chọn chiến lược nối phù hợp qua `java.lang.invoke.StringConcatFactory`.

Trong Java 17+, trình biên dịch thông thường có thể dùng cơ chế này cho biểu thức nối động, thay vì cố định một chuỗi lệnh tạo `StringBuilder` trực tiếp. Những tối ưu cụ thể phụ thuộc phiên bản JVM, loại giá trị và bối cảnh JIT. Tuy nhiên, điều quan trọng với người học là:

1. **Một biểu thức nối ngắn bằng `+` thường là lựa chọn tốt.** Mã rõ ràng và JVM có cơ hội tối ưu.
2. **Đừng tự viết `new StringBuilder().append(...).append(...).toString()` cho mọi dòng `+`** khi không có lý do rõ ràng. Mã có thể dài hơn mà không nhanh hơn trong điều kiện thực tế.
3. **Tối ưu cho một biểu thức không tự động giải quyết tích lũy qua nhiều vòng lặp.** Đó là cấu trúc thuật toán khác, cần xét riêng.

## 3. Tích lũy qua vòng lặp bằng `+=`: chi phí nằm ở tiền tố lặp lại

Xét đoạn mã:

```java
String result = "";

for (int i = 1; i <= 4; i++) {
    result += i;
}

System.out.println(result); // 1234
```

Vòng lặp tạo kết quả liên tiếp:

```text
Lần 1: ""     + "1" → "1"
Lần 2: "1"    + "2" → "12"
Lần 3: "12"   + "3" → "123"
Lần 4: "123"  + "4" → "1234"
```

Ở mỗi bước, phép nối cần tạo giá trị mới từ toàn bộ phần đã tích lũy và phần tiếp theo. **Nếu các lần nối lần lượt sao chép toàn bộ tiền tố**, tổng lượng dữ liệu được xử lý trong trường hợp `n` đoạn có độ dài gần bằng nhau tăng theo dãy:

```text
1 + 2 + 3 + ... + n = n(n + 1) / 2
```

Đây là **mô hình chi phí có thể tiến tới bậc O(n²)** theo số đoạn khi dùng nối tạo chuỗi mới qua mỗi vòng. Thời gian thực tế có thể khác do runtime tối ưu, độ dài các đoạn, chi phí số hóa dữ liệu và JIT. Không nên tuyên bố rằng Java 17 sẽ luôn tốn đúng bậc thời gian đó với mọi đoạn mã, nhưng mô hình cho thấy **vì sao cách lặp với `+=` là lựa chọn kém ổn định khi dữ liệu tăng lớn**.

Việc lặp tạo các kết quả trung gian cũng có thể tăng áp lực lên bộ cấp phát và garbage collector. Nếu chuỗi chỉ có ba phần và vòng lặp chạy rất ít lần, chi phí có thể không đáng kể; nếu dựng hàng nghìn mục, vấn đề trở nên quan trọng hơn.

## 4. `StringBuilder`: một vùng đệm mutable cho nhiều bước nối

`StringBuilder` là lớp có thể thay đổi nội dung. Nó hỗ trợ `append()`, `insert()`, `delete()`, `setCharAt()`, v.v. Thay vì liên tiếp tạo `String` trung gian làm kết quả tích lũy, mã có thể cập nhật **cùng một builder**:

```java
StringBuilder builder = new StringBuilder();

for (int i = 1; i <= 4; i++) {
    builder.append(i);
}

String result = builder.toString();
System.out.println(result); // 1234
```

Cơ chế cần hiểu:

- `new StringBuilder()` tạo một đối tượng giữ bộ đệm ký tự bên trong (chi tiết biểu diễn có thể thay đổi giữa các JDK).
- `append(value)` thêm biểu diễn văn bản của giá trị vào cuối nội dung hiện tại, và trả về chính builder để có thể gọi liên tiếp.
- Khi không đủ dung lượng, builder có thể mở rộng bộ đệm; việc mở rộng vẫn có chi phí, nhưng không yêu cầu tạo `String` kết quả sau **mỗi** lần thêm.
- `toString()` tạo giá trị `String` có nội dung tại thời điểm gọi, dùng để trả về, lưu hoặc chia sẻ với phần mềm khác.
- `StringBuilder` **không bảo đảm an toàn luồng** nếu nhiều luồng cùng sửa một instance.

Đối với mẫu thêm tuần tự các đoạn có tổng độ dài `m`, builder thường giúp tránh việc liên tục sao chép tiền tố ở mỗi vòng. Không phải mọi thao tác trên builder đều luôn có độ phức tạp tuyến tính: `insert()` hoặc `delete()` ở vị trí giữa có thể dịch chuyển dữ liệu; tạo quá ít dung lượng ban đầu cũng có thể gây cấp phát lại.

### Có thể nối liên tiếp bằng cách gọi chuỗi phương thức

```java
StringBuilder builder = new StringBuilder();
builder.append("Name: ")
       .append("An")
       .append(", score: ")
       .append(12);

String output = builder.toString();
// "Name: An, score: 12"
```

Cách gọi này thường hữu ích trong mã cần bổ sung nhiều phần có điều kiện hoặc lặp. Với **một biểu thức ngắn** `"Name: " + name + ", score: " + score` vẫn có thể rõ ràng hơn.

## 5. Chương trình Java 17+ hoàn chỉnh: cùng kết quả, cách tích lũy khác nhau

Tạo tệp `ConcatLoopDemo.java`:

```java
public class ConcatLoopDemo {
    private static String withPlusEquals(int count) {
        String result = "";

        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                result += ", ";
            }
            result += i;
        }
        return result;
    }

    private static String withBuilder(int count) {
        StringBuilder result = new StringBuilder();

        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                result.append(", ");
            }
            result.append(i);
        }
        return result.toString();
    }

    public static void main(String[] args) {
        String left = withPlusEquals(4);
        String right = withBuilder(4);

        System.out.println("plusEquals = " + left);
        System.out.println("builder = " + right);
        System.out.println("sameContent = " + left.equals(right));

        String oneExpression = "Items: " + 4 + ", ready: " + true;
        System.out.println(oneExpression);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ConcatLoopDemo.java
java ConcatLoopDemo
```

Kết quả chính xác:

```text
plusEquals = 1, 2, 3, 4
builder = 1, 2, 3, 4
sameContent = true
Items: 4, ready: true
```

Hai hàm trả về **nội dung như nhau**. Điều khác là mô hình tạo kết quả: `withPlusEquals()` liên tục nối vào một biến `String` qua nhiều lượt, còn `withBuilder()` bổ sung vào một đối tượng có thể thay đổi rồi gọi `toString()` ở cuối. Vì vậy, đối với tích lũy qua vòng lặp với dữ liệu tăng, `StringBuilder` thường là lựa chọn dễ dự đoán hơn về chi phí.

Không dùng chương trình này để “đo hiệu năng”: nó chỉ chứng minh **tính đúng đắn và sự tương đương đầu ra**. Sự khác biệt về thời gian chạy cần một phép đo benchmark phù hợp.

## 6. Toán tử `+` có những quy tắc dễ gây sai kết quả

Phép `+` trên `String` vẫn tuân theo thứ tự kết hợp trái và string conversion. Nếu biểu thức chứa phép tính số, cần ngoặc cho rõ ý:

```java
System.out.println("Sum = " + 1 + 2);   // Sum = 12
System.out.println("Sum = " + (1 + 2)); // Sum = 3
```

Khi đối số là `null`, phép nối có thể sinh chữ `"null"`; `concat(null)` lại có thể ném `NullPointerException`. Những quy tắc cú pháp và null này đã có bài riêng. Trong bài này, điều cần phân biệt là **một biểu thức ngắn** với **tích lũy tiền tố qua nhiều lần lặp**.

## 7. Dấu phân cách: đừng nối dư dấu cuối hoặc xây chuỗi bằng cách cắt bớt

Một lỗi thường gặp khi ghép danh sách là thêm dấu `, ` sau mọi phần tử rồi cắt hai ký tự ở cuối. Cách này cần xử lý danh sách rỗng và có thể dẫn đến truy cập sai chỉ số.

Thay vào đó, chỉ thêm dấu phân cách **trước phần tử thứ hai trở đi**:

```java
StringBuilder builder = new StringBuilder();
String[] tags = {"java", "core", "string"};

for (String tag : tags) {
    if (builder.length() > 0) {
        builder.append(", ");
    }
    builder.append(tag);
}

System.out.println(builder); // java, core, string
```

Đoạn này chỉ dùng được nguyên tắc `builder.length() > 0` khi các phần tử đầu tiên không phải chuỗi rỗng; với phần tử `""`, kết quả có thể thiếu dấu phân cách mong muốn. Một cách chắc chắn hơn là dùng **cờ `first`** hoặc **chỉ số vòng lặp**. Cách sau áp dụng đúng kể cả khi phần tử rỗng:

```java
String[] tags = {"", "java", "core"};
StringBuilder builder = new StringBuilder();

for (int i = 0; i < tags.length; i++) {
    if (i > 0) {
        builder.append(", ");
    }
    builder.append(tags[i]);
}

System.out.println("[" + builder + "]"); // [, java, core]
```

Đối với danh sách đã có, `String.join()` hoặc `StringJoiner` thường đơn giản hơn. Chúng có ngữ nghĩa dấu phân cách rõ ràng và tránh việc tự quản lý điều kiện phần tử đầu/cuối:

```java
java.util.List<String> tags =
        java.util.List.of("java", "core", "string");

String joined = String.join(", ", tags);
System.out.println(joined); // java, core, string
```

**Chọn API dựa trên cấu trúc dữ liệu**: `String.join()` khi đã có danh sách/varargs chuỗi; `StringJoiner` khi thêm các phần tử dần dần với delimiter; `StringBuilder` khi cần xây nhiều dạng nội dung tùy biến.

## 8. `StringJoiner`: tích lũy nhiều phần với delimiter, prefix và suffix

Tạo tệp `StringJoinerDemo.java`:

```java
import java.util.List;
import java.util.StringJoiner;

public class StringJoinerDemo {
    public static void main(String[] args) {
        List<String> tags = List.of("java", "core", "string");

        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (String tag : tags) {
            joiner.add(tag);
        }

        System.out.println("joiner = " + joiner);
        System.out.println("stringJoin = " + String.join(" | ", tags));

        StringJoiner empty = new StringJoiner(", ", "[", "]");
        System.out.println("emptyJoiner = " + empty);
    }
}
```

Biên dịch và chạy:

```bash
javac StringJoinerDemo.java
java StringJoinerDemo
```

Kết quả:

```text
joiner = [java, core, string]
stringJoin = java | core | string
emptyJoiner = []
```

`StringJoiner` hỗ trợ thêm phần tử dần qua `add()`, tự quản lý dấu phân cách giữa các phần tử và có thể thêm tiền tố/hậu tố. Đây là giải pháp thuận tiện khi yêu cầu chính là **nối danh sách**, thay vì xây một văn bản có cấu trúc phức tạp.

Nếu cần nối các giá trị từ stream, có thể dùng `Collectors.joining(", ")`. Tuy vậy, không cần chuyển một danh sách nhỏ sang stream chỉ để thay một lời gọi `String.join()` rõ ràng.

## 9. Khi nào nên cấp trước dung lượng `StringBuilder`?

Bạn có thể cung cấp dung lượng ban đầu khi đã biết **ước lượng hợp lý** số UTF-16 code units của kết quả:

```java
String[] chunks = {"abc", "def", "ghi"};
int estimatedLength = 0;

for (String chunk : chunks) {
    estimatedLength += chunk.length();
}

StringBuilder builder = new StringBuilder(estimatedLength);
for (String chunk : chunks) {
    builder.append(chunk);
}

System.out.println(builder.toString()); // abcdefghi
```

Ví dụ này là fragment đặt trong một phương thức; `estimatedLength` được tính đúng bằng tổng độ dài ba chuỗi. Đặt capacity ban đầu có thể giảm số lần phải mở rộng bộ đệm.

Cần phân biệt:

- **`capacity()`** của builder: dung lượng bộ đệm có thể chứa trước khi cần mở rộng.
- **`length()`** của builder: độ dài văn bản thực đang có, tính bằng UTF-16 code units.
- `new StringBuilder(100)` **không** tạo sẵn một chuỗi dài 100 ký tự; ban đầu `length() == 0`.
- `ensureCapacity(minimumCapacity)` có thể bảo đảm dung lượng tối thiểu khi cần, nhưng không thêm nội dung.
- Dung lượng ước lượng quá lớn sẽ tiêu thụ bộ nhớ không cần thiết; đừng cấp phát quá mức theo một phỏng đoán thiếu cơ sở.

Với dữ liệu rất lớn, phép tính tổng độ dài bằng `int` cần xét giới hạn và tránh tràn số nguyên. Với công việc thông thường, dùng builder mặc định thường đã đủ; chỉ tối ưu capacity khi việc đo đạc cho thấy có lợi.

## 10. `StringBuilder.toString()` tạo một ảnh chụp giá trị chuỗi

Khi lấy kết quả từ builder, `String` trả về không trở thành “cửa sổ sống” vào trạng thái của builder:

```java
StringBuilder builder = new StringBuilder("A");
String before = builder.toString();

builder.append("B");
String after = builder.toString();

System.out.println("before = " + before);
System.out.println("after = " + after);
```

Kết quả:

```text
before = A
after = AB
```

Biến `before` vẫn là `"A"` vì đối tượng `String` bất biến. Nếu cần tạo nhiều bản ghi snapshot ở các thời điểm khác nhau, gọi `toString()` ở mỗi thời điểm là hành vi đúng, nhưng cũng tạo thêm giá trị kết quả tương ứng.

Thông thường, nếu chỉ cần kết quả cuối cùng, nên **tích lũy hết rồi gọi `toString()` một lần**. Đừng gọi `toString()` mỗi vòng để rồi dùng kết quả đó làm chuỗi nguồn cho vòng lặp tiếp theo; như vậy sẽ làm mất lợi ích chính của bộ đệm tích lũy.

## 11. Unicode không thay đổi nguyên tắc lựa chọn, nhưng ảnh hưởng độ dài

`String.length()` và `StringBuilder.length()` tính theo **UTF-16 code units**. Một Unicode code point ngoài BMP như nhiều emoji có thể chiếm hai code units:

```java
String emoji = "\uD83D\uDE00"; // 😀
String one = "A" + emoji + "B";

StringBuilder builder = new StringBuilder();
builder.append("A").append(emoji).append("B");

System.out.println(one.equals(builder.toString())); // true
System.out.println(one.length());                    // 4
System.out.println(builder.length());                // 4
```

Tính đúng của phép nối không bị thay đổi: cả `+` và `append()` đều giữ nguyên dãy đơn vị mã của các đoạn nguồn trong ví dụ. Tuy nhiên, khi ước lượng capacity, giới hạn độ dài hoặc cắt văn bản, không được mặc định “số ký tự nhìn thấy” luôn bằng `length()`.

Một builder cũng có thể bị sửa theo chỉ số với `setCharAt()`, `delete()` hoặc `insert()`; việc cắt giữa surrogate pair có thể tạo văn bản Unicode không hợp lệ. Nếu chỉ cần nối nguyên vẹn các đoạn Unicode đã hợp lệ, các phép `append()` tuần tự thường đơn giản hơn nhiều.

## 12. Giới hạn, chia sẻ nhiều luồng và API liên quan

### Không chia sẻ một `StringBuilder` để sửa đồng thời mà thiếu đồng bộ

`StringBuilder` không đồng bộ hóa các phương thức của nó. Nếu hai luồng cùng gọi `append()` trên một instance, nội dung có thể bị race condition và không có bảo đảm đúng thứ tự hoặc toàn vẹn.

Nếu mỗi request hoặc mỗi tác vụ tạo builder **cục bộ riêng**, không có vấn đề chia sẻ đó. Với trường hợp thực sự cần thao tác trên bộ đệm chung giữa các luồng, cân nhắc đồng bộ thích hợp hoặc `StringBuffer` (các phương thức chính được đồng bộ hóa). Dù dùng `StringBuffer`, một quy trình gồm nhiều lời gọi vẫn cần kiểm tra tính nguyên tử ở cấp quy trình.

### `StringBuilder` không cần cho mọi đoạn mã có vòng lặp

Trường hợp này không phải tích lũy một chuỗi qua các lượt:

```java
for (int i = 0; i < 3; i++) {
    System.out.println("Item " + i);
}
```

Mỗi vòng chỉ cần một **thông điệp độc lập**, không dựng một kết quả lớn từ các thông điệp cũ. Dùng `"Item " + i` là rõ ràng và hoàn toàn hợp lý. Vấn đề đáng cân nhắc là khi **cùng một biến chuỗi kết quả** được nối rồi gán lại liên tục, ví dụ `result += item`.

### Tránh ghép chuỗi vô ích khi API đã có tham số cấu trúc

Ví dụ câu SQL có tham số, logging theo mẫu placeholder hoặc API xuất CSV/JSON có quy tắc escape riêng thường nên dùng khả năng cấu trúc của API đó. Không dùng nối chuỗi thủ công để thay thế cơ chế escape, tham số hóa hoặc serialize đúng chuẩn.

Lựa chọn `StringBuilder` chỉ giải quyết cách dựng chuỗi và chi phí bộ đệm; nó **không tự giải quyết** nguy cơ SQL injection, lỗi escaping JSON hay cách định dạng ngày/số theo locale.

## 13. So sánh hiệu năng đúng cách

Đừng kết luận “nhanh hơn X lần” chỉ từ một lần chạy `System.nanoTime()`. Kết quả vi mô bị ảnh hưởng bởi JIT warmup, dead-code elimination, cấp phát/GC, dữ liệu đầu vào, CPU và phiên bản JVM.

Nếu thực sự cần so sánh:

1. Định nghĩa công việc giống nhau: cùng dữ liệu đầu vào, số vòng, cùng kết quả đầu ra.
2. Tạo các phương án có cùng ngữ nghĩa; tránh trường hợp nhánh này xử lý thêm dữ liệu hoặc khác delimiter.
3. Dùng công cụ benchmark phù hợp như **JMH**, có warmup, measurement, fork và cơ chế tiêu thụ kết quả để tránh JVM loại bỏ phép tính.
4. Thử dữ liệu có kích thước thực tế (vài phần tử, hàng trăm, hàng nghìn…) và, khi liên quan, nhiều kiểu Unicode/độ dài đoạn.
5. Đo cả thời gian lẫn lượng cấp phát hoặc GC nếu đó là vấn đề hiệu năng cần tối ưu.
6. Kiểm tra kết quả trước và sau mọi thay đổi tối ưu.

Quy tắc thiết kế vẫn hữu ích ngay cả khi chưa benchmark: **`+` cho một biểu thức ngắn; `StringBuilder` cho một tiến trình tích lũy nhiều đoạn trong vòng lặp**. Khi khác biệt không quan trọng, mã đơn giản và dễ hiểu là ưu tiên.

## 14. Lỗi phổ biến

| Lỗi | Vì sao có vấn đề? | Hướng sửa |
| --- | --- | --- |
| “Mỗi `+` đều chậm, phải thay bằng `StringBuilder`.” | Java hiện đại tối ưu biểu thức nối, tự viết builder có thể chỉ tăng độ phức tạp mã. | Dùng `+` cho biểu thức nhỏ; đo khi thật sự cần. |
| “`result += part` sửa `String` hiện tại.” | `String` bất biến; biến nhận giá trị mới sau mỗi lượt nối. | Dùng builder khi cần tích lũy nhiều lần. |
| “JDK 17 tự động biến mọi vòng lặp `+=` thành một builder duy nhất.” | Không có bảo đảm tổng quát như vậy trong hợp đồng ngôn ngữ. | Chọn cấu trúc tích lũy tường minh khi cần hiệu quả ổn định. |
| “`"Sum: " + 1 + 2` in `Sum: 3`.” | Sau khi bắt đầu nối chuỗi, các toán hạng tiếp theo được nối theo thứ tự. | Viết `"Sum: " + (1 + 2)`. |
| “`new StringBuilder(100)` có sẵn 100 ký tự.” | Tham số constructor là **capacity**, không phải length. | Dùng `append()` để thêm nội dung. |
| “`StringBuilder` luôn an toàn đa luồng.” | `StringBuilder` mutable và không đồng bộ hóa. | Giữ builder cục bộ hoặc đồng bộ thích hợp. |
| “`String.join()` chỉ hữu ích khi vòng lặp lớn.” | Đây là API diễn đạt ý định ghép danh sách và quản lý delimiter. | Dùng khi đã có các thành phần. |
| “`builder.toString()` là một view mutable.” | Kết quả là `String` bất biến, không cập nhật khi builder đổi. | Chỉ gọi lại `toString()` nếu muốn snapshot mới. |
| “Biến `null` được bỏ qua khi nối.” | Phép nối chuỗi có thể tạo chữ `"null"`. | Kiểm tra và đặt chính sách null rõ ràng. |
| “Builder xử lý đúng escape JSON/CSV/SQL.” | Nó chỉ nối giá trị, không hiểu cú pháp hay an toàn của định dạng đích. | Dùng parser/serializer hoặc API tham số phù hợp. |

## 15. Tự kiểm tra

**Câu 1.** Đoạn mã dưới đây tích lũy qua vòng lặp hay chỉ nối một biểu thức trong từng vòng?

```java
for (int i = 0; i < 3; i++) {
    String line = "Index: " + i;
    System.out.println(line);
}
```

Đây là **mỗi vòng một biểu thức độc lập**, không có biến kết quả tích lũy dài thêm qua các lượt. Không bắt buộc chuyển sang `StringBuilder`.

**Câu 2.** Đoạn mã sau có lỗi gì khi số vòng lớn?

```java
String output = "";
for (String item : items) {
    output = output + item;
}
```

Mỗi vòng xây giá trị kết quả mới từ tiền tố cũ và `item`. Với nhiều đoạn, việc phải xử lý lại tiền tố làm phát sinh chi phí; dùng `StringBuilder.append(item)` trong vòng lặp rồi `toString()` ở cuối thường thích hợp hơn. Đây là fragment giả định biến `items` đã tồn tại.

**Câu 3.** Vì sao `"n=" + 1 + 2` không giống `"n=" + (1 + 2)`?

Biểu thức thứ nhất là `"n=12"`, còn biểu thức thứ hai là `"n=3"`. Cần dùng dấu ngoặc khi thực sự muốn cộng số trước khi nối chuỗi.

**Câu 4.** Nếu danh sách là `["", "A"]` và cần nối bằng dấu phẩy, kết quả mong muốn là `",A"`. Kiểm tra `builder.length() > 0` để quyết định thêm dấu phẩy đã đủ chưa?

Chưa. Sau khi thêm phần tử rỗng đầu tiên, builder vẫn có `length() == 0`, nên cách đó bỏ dấu phẩy. Dùng **chỉ số/cờ phần tử đầu tiên** hoặc `StringJoiner` để không phụ thuộc vào độ dài nội dung.

**Câu 5.** Một builder đã tạo chuỗi `"A"` qua `toString()`; sau đó `append("B")` vào builder. Chuỗi `"A"` có thành `"AB"` không?

Không. Kết quả của `toString()` là một đối tượng `String` bất biến. Muốn lấy nội dung mới, gọi `toString()` lần nữa sau khi đã `append()`.

## Kết luận

- Với **một biểu thức nối ngắn** (ví dụ `"Hello, " + name + "!"`), `+` dễ đọc và thường được Java hiện đại tối ưu tốt.
- Với **một chuỗi kết quả được tích lũy qua nhiều vòng lặp**, `StringBuilder` thường giúp tránh tạo lặp đi lặp lại các giá trị `String` trung gian và sao chép tiền tố.
- Nếu mục đích là nối **các phần tử với dấu phân cách**, xem xét `String.join()` hoặc `StringJoiner`.
- `String` bất biến, `StringBuilder` mutable; `toString()` cung cấp chuỗi kết quả bất biến.
- Xử lý rõ `null`, thứ tự cộng số/nối chuỗi, Unicode, trường hợp danh sách rỗng, và đo bằng benchmark có phương pháp trước khi kết luận hiệu năng.

## Tài liệu tham khảo

- [Java SE 21 API — `java.lang.String`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html): tính bất biến, nối chuỗi, `join()`.
- [Java SE 21 API — `java.lang.StringBuilder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StringBuilder.html): `append()`, `capacity()`, `ensureCapacity()`, `toString()`.
- [Java SE 21 API — `java.util.StringJoiner`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/StringJoiner.html): quy tắc delimiter, prefix và suffix.
- [OpenJDK JEP 280 — Indify String Concatenation](https://openjdk.org/jeps/280): cách Java 9+ tối ưu mô hình nối `String` động bằng `invokedynamic`.
- [OpenJDK JMH](https://github.com/openjdk/jmh): công cụ benchmark Java phù hợp với so sánh hiệu năng vi mô.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-concatenation-008">Chi tiết biên dịch phép nối có thể thay đổi theo JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Chi tiết biên dịch phép nối chuỗi có thể thay đổi theo JDK

## 1. Ý tưởng cốt lõi: cùng phép `+`, cách triển khai có thể khác

Trong Java, toán tử `+` được dùng để **nối chuỗi** khi ít nhất một toán hạng có kiểu `String`. Ví dụ:

```java
String name = "Lan";
int score = 9;
String message = "Name: " + name + ", score: " + score;
```

Kết quả `message` là `"Name: Lan, score: 9"`. Ý nghĩa của phép nối này do **ngôn ngữ Java** quy định; ứng dụng phải quan sát được nội dung đúng bất kể trình biên dịch hoặc JVM dùng chiến lược nội bộ nào.

Tuy nhiên, mã nguồn không chỉ rõ JVM phải ghép ký tự bằng lớp nào, tạo các buffer trung gian nào hoặc dùng lệnh bytecode nào. Các JDK và trình biên dịch có thể sinh bytecode khác nhau, và JVM có thể chọn cách thực thi/tối ưu khác nhau, miễn giữ đúng hành vi của Java. Vì vậy, câu nói **“`+` luôn được compiler biến thành `new StringBuilder().append(...).toString()`”** không đúng cho mọi phiên bản.

Phân biệt ba lớp:

1. **Mã nguồn Java:** phép `+`, thứ tự biểu thức, cách chuyển giá trị sang văn bản.
2. **Bytecode của file `.class`:** ví dụ chuỗi lệnh với `StringBuilder` hoặc lời gọi `invokedynamic`.
3. **Cách chạy thực tế trong JVM/JIT:** có thể tiếp tục tối ưu cấp phát, inlining hoặc kỹ thuật khác mà bytecode không mô tả trực tiếp.

Không nên lấy bytecode của một JDK làm “định nghĩa” của toán tử nối chuỗi trong ngôn ngữ.

## 2. Những quy tắc ngôn ngữ phải giữ nguyên

**Hợp đồng cố định:** các phép `+` cùng mức ưu tiên kết hợp trái; khi có toán hạng kiểu `String`, Java thực hiện string conversion (bao gồm tham chiếu `null` thành chữ `"null"`). Compiler và JVM phải giữ nguyên thứ tự đánh giá cùng những tác dụng phụ/ngoại lệ có thể quan sát được.

Với phép nối **không phải biểu thức hằng**, kết quả là một `String` theo ngữ nghĩa runtime mà chuỗi đầu vào không bị sửa; trình biên dịch và JIT được quyền tối ưu các đối tượng trung gian khi không đổi hành vi. Ví dụ:

```java
String original = "Java";
String alias = original;
original = original + " 17";
System.out.println(alias); // Java
```

Đây là nền tảng để so sánh bytecode Java 8 với cơ chế `invokedynamic` Java 9+, không cần dạy lại toàn bộ quy tắc cộng số/chuỗi.

## 3. Nối hằng ở thời điểm biên dịch: constant folding

Trước khi bàn về khác biệt Java 8 và Java 9+, cần nhận ra có những phép nối **không cần ghép tại runtime**.

```java
String direct = "Hello World";
String folded = "Hello" + " " + "World";

System.out.println(direct.equals(folded)); // true
System.out.println(direct == folded);      // true
```

`"Hello" + " " + "World"` là **biểu thức hằng** theo Java Language Specification. Compiler có thể biểu diễn kết quả trong constant pool của `.class`; các giá trị chuỗi hằng được intern nên tham chiếu của `direct` và `folded` bằng nhau.

Một biến `final` có thể tham gia biểu thức hằng nếu bản thân biến là **constant variable** (kiểu primitive hoặc `String`, `final`, khởi tạo bằng biểu thức hằng):

```java
final String prefix = "Hello";
String folded = prefix + " World";

System.out.println(folded == "Hello World"); // true
```

Nhưng `final` đơn thuần không đủ:

```java
final String prefix = new String("Hello");
String result = prefix + " World";

System.out.println(result.equals("Hello World")); // true
System.out.println(result == "Hello World");       // false
```

`new String(...)` không phải biểu thức hằng. Phép nối ở đây thực hiện lúc chạy và không tự intern thành literal. **Không suy ra mọi lệnh có dấu `+` đều sinh `invokedynamic` hoặc `StringBuilder`:** compiler có thể gộp biểu thức hằng từ trước.

## 4. Java 8: ví dụ điển hình dùng `StringBuilder`

Ở `javac` của JDK 8, phép nối chuỗi **không phải hằng** thường được hạ xuống (*lowering*) chuỗi lời gọi `StringBuilder`: tạo builder, `append` các phần rồi `toString`.

Mã nguồn minh họa:

```java
static String label(String name, int age) {
    return "Name=" + name + ", age=" + age;
}
```

Một dạng bytecode rút gọn thường thấy ở Java 8 (minh họa cấu trúc, không phải bản chép nguyên văn `javap`):

```text
new           java/lang/StringBuilder
dup
invokespecial java/lang/StringBuilder.<init>
ldc           "Name="
invokevirtual java/lang/StringBuilder.append
aload         ...
invokevirtual java/lang/StringBuilder.append
ldc           ", age="
invokevirtual java/lang/StringBuilder.append
iload         ...
invokevirtual java/lang/StringBuilder.append
invokevirtual java/lang/StringBuilder.toString
areturn
```

Mô hình này **hữu ích để hiểu lịch sử**, nhưng không phải cam kết mỗi dấu `+` luôn là một `append()`, mỗi lần chạy luôn có đúng một builder hay JIT không được khử cấp phát. Trình biên dịch và runtime có các cơ hội tối ưu riêng; chuỗi nhiều toán hạng trong một biểu thức có thể được compiler xử lý tập hợp.

Từ đó phát sinh lối giải thích quen thuộc: “nối `String` bằng `+` dùng `StringBuilder` ngầm”. Với Java hiện đại, cần cập nhật mô hình này.

## 5. Từ Java 9: `invokedynamic` và `StringConcatFactory`

Java 9 đưa vào **JEP 280 — Indify String Concatenation**, chuyển cơ chế mặc định của `javac` hiện đại cho nhiều phép nối không phải hằng sang việc phát sinh bytecode `invokedynamic`. Bytecode mô tả **điểm nối** (*call site*) và có thể liên kết qua bootstrap method của `java.lang.invoke.StringConcatFactory`, thay vì hard-code chuỗi lệnh `new StringBuilder` vào mã người dùng.

Với ví dụ `"Name=" + name + ", age=" + age`, bytecode do `javac` hiện đại sinh thường có một `invokedynamic` dành cho thao tác nối:

```text
aload         ...
iload         ...
invokedynamic ... // makeConcatWithConstants hoặc chiến lược tương đương
areturn
```

Đây là sơ đồ **minh họa**; thứ tự chỉ dẫn, method descriptor, recipe và tên bootstrap trong bản `javap -v` cụ thể tùy compiler, tùy mục tiêu biên dịch và các tối ưu của JDK đang dùng.

### Bootstrap method và cách thực thi

`StringConcatFactory` là API hỗ trợ liên kết `invokedynamic` cho thao tác nối chuỗi. Hai bootstrap method đáng biết là:

- `StringConcatFactory.makeConcat(...)`: nhận các đối số động để nối.
- `StringConcatFactory.makeConcatWithConstants(...)`: có thể mang theo **recipe** và các thành phần hằng, giúp mô tả cách kết hợp đối số động với văn bản hằng.

Khi call site được liên kết, một `CallSite` cung cấp `MethodHandle` thích hợp để thực hiện phép nối khi được gọi. JVM có thể chọn, tối ưu hoặc thay đổi triển khai bên dưới mà không đòi hỏi tất cả bytecode gọi phép nối phải đổi theo cùng một cách.

Ví dụ, recipe của `makeConcatWithConstants` có thể dùng ký hiệu đặc biệt cho đối số động và hằng. **Recipe là chi tiết liên kết**, không phải cú pháp phải viết tay khi sử dụng dấu `+` bình thường.

Điều quan trọng: **`invokedynamic` là cơ chế bytecode**, còn `StringConcatFactory` là một cơ chế trong thư viện nền hỗ trợ runtime. Việc mã Java dùng `+` không có nghĩa người lập trình bắt buộc tự gọi factory.

### Mục tiêu của JEP 280

Cơ chế mới cho phép cải tiến chiến lược nối chuỗi ở thư viện/runtime mà không cần mã ứng dụng liên tục đổi mã nguồn chỉ để nhận các cải tiến đó. Tuy nhiên, việc có `invokedynamic` **không bảo đảm một benchmark cụ thể sẽ nhanh hơn**, cũng không có nghĩa phép nối không tạo đối tượng nào.

Tham khảo chính thức: [JEP 280](https://openjdk.org/jeps/280) và [Java SE 21 — StringConcatFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/StringConcatFactory.html).

## 6. Một mã nguồn, nhiều bytecode: cách tự kiểm chứng

Lưu tệp `ConcatVersionDemo.java` dưới đây. Chương trình tương thích Java 8+, và tất nhiên có thể chạy bằng JDK 17+.

```java
public class ConcatVersionDemo {
    static String label(String name, int age) {
        return "Name=" + name + ", age=" + age;
    }

    public static void main(String[] args) {
        String name = "Lan";
        int age = 20;

        System.out.println(label(name, age));

        String literal = "Java";
        String folded = "Ja" + "va";
        final String constantPrefix = "Ja";
        String foldedWithFinal = constantPrefix + "va";

        String dynamicPrefix = "Ja";
        String runtime = dynamicPrefix + "va";

        System.out.println("folded == literal: " + (folded == literal));
        System.out.println("final folded == literal: "
                + (foldedWithFinal == literal));
        System.out.println("runtime equals literal: "
                + runtime.equals(literal));
        System.out.println("runtime == literal: " + (runtime == literal));

        System.out.println(1 + 2 + " apples");
        System.out.println("apples: " + 1 + 2);
        System.out.println("name=" + (String) null);
    }
}
```

Dùng đúng tên tệp `ConcatVersionDemo.java`. Chạy bằng JDK 17+:

```bash
javac ConcatVersionDemo.java
java ConcatVersionDemo
```

Kết quả chuẩn:

```text
Name=Lan, age=20
folded == literal: true
final folded == literal: true
runtime equals literal: true
runtime == literal: false
3 apples
apples: 12
name=null
```

Đây là kết quả theo ngữ nghĩa Java, độc lập với việc phiên bản JDK đang sinh `StringBuilder` hay `invokedynamic` cho phép nối runtime.

### Xem bytecode bằng `javap`

`javap` là công cụ đi kèm JDK để xem cấu trúc và bytecode của file `.class`:

```bash
javac -version
java -version
javac --release 8 ConcatVersionDemo.java
javap -c -p ConcatVersionDemo
javap -v ConcatVersionDemo
```

Nhìn vào bytecode của phương thức `label` và `main`. Với `javac` hiện đại khi xuất `--release 8`, ta thường thấy chuỗi lệnh `new StringBuilder`, `append` và `toString` cho nối chuỗi runtime. Tùy ví dụ, **biểu thức hằng** đã được compiler gộp và chỉ còn tải chuỗi hằng.

Bây giờ biên dịch lại cùng tệp nguồn sang mục tiêu Java 17:

```bash
javac --release 17 ConcatVersionDemo.java
javap -c -p ConcatVersionDemo
javap -v ConcatVersionDemo
```

Với `javac` JDK hiện đại, các phép nối runtime thường xuất hiện dạng `invokedynamic`, và ở phần `BootstrapMethods` của `javap -v` có thể thấy `StringConcatFactory.makeConcatWithConstants`. Hãy **kiểm tra output thực tế** thay vì tin vào một đoạn bytecode cố định trong tài liệu.

**Điều kiện thực hiện:** Các lệnh `--release 8` và `--release 17` ở trên dành cho JDK hỗ trợ cả hai mục tiêu, chẳng hạn JDK 17 hoặc JDK 21/24. Trên JDK 8 không có tùy chọn `--release`; khi đó cần công cụ biên dịch và cờ tương ứng với JDK 8. Nếu dự án dùng Maven/Gradle hoặc compiler khác, cần xác nhận cài đặt toolchain/target thực sự trước khi so sánh.

**Chú ý:** Hai lần `javac` liên tiếp sẽ ghi đè file `ConcatVersionDemo.class` trong cùng thư mục. Vì vậy, **quan sát `javap` ngay sau từng lần biên dịch**, hoặc biên dịch ra hai thư mục riêng khi cần so sánh song song. Lệnh `java ConcatVersionDemo` chạy class file hiện có tại thời điểm gọi, không tự chạy lại compiler.

### Phân biệt ba phiên bản

| Thuộc tính | Bạn đang kiểm tra điều gì? |
| --- | --- |
| `javac -version` | Phiên bản **trình biên dịch** đang chạy |
| `javac --release N` | Mục tiêu API, cú pháp và class-file mà compiler được yêu cầu xuất |
| `java -version` | Phiên bản **runtime/JVM** dùng để chạy |
| `javap -c -v ...` | Bytecode **thực sự được tạo** trong file `.class` |
| Hiệu năng khi chạy | Kết quả tương tác giữa chương trình, dữ liệu, JVM/JIT, GC và hệ thống |

Ví dụ, sử dụng `javac` từ JDK 24 để biên dịch `--release 8` **không biến compiler thành `javac` của JDK 8**. Nó yêu cầu xuất mã tương thích mức Java 8; chi tiết triển khai nội bộ vẫn tùy compiler đang dùng. Đồng thời, một class file nhắm Java 17 thông thường không chạy được trên runtime Java 8 vì class-file version và thư viện nền tương ứng.

## 7. Điều gì thay đổi và điều gì không?

| Khía cạnh | Bảo đảm / nhận định phù hợp |
| --- | --- |
| Nội dung kết quả `"A" + "B"` | Bằng `"AB"` |
| Thứ tự và kết quả các phép toán như `1 + 2 + "x"` | Theo ngữ nghĩa Java, không phụ thuộc công thức nối nội bộ |
| `String` đầu vào có bị sửa không? | Không; `String` bất biến |
| Biểu thức hằng có thể gộp khi biên dịch? | Có, theo quy tắc constant expression |
| Mọi phép nối phải dùng `StringBuilder`? | Không |
| Mọi phép nối phải dùng `invokedynamic`? | Không |
| `javac` Java 8 và Java 17 có thể sinh bytecode khác? | Có |
| Hai JVM chạy cùng bytecode có thể tối ưu khác? | Có, nếu vẫn giữ đúng ngữ nghĩa |
| `javap` có phản ánh mọi bước tối ưu JIT lúc chạy? | Không; nó hiển thị bytecode trong class file |
| Có thể đoán chính xác số object chỉ từ dấu `+`? | Không đáng tin nếu bỏ qua folding, các đối tượng trung gian và tối ưu runtime |

**Không nhầm constant pool của class file với String pool.** Constant pool trong `.class` là bảng mục dùng để biểu diễn hằng, tên, tham chiếu và các dữ liệu liên kết cần thiết cho bytecode. String pool là cơ chế quản lý các chuỗi đã intern ở runtime. Chúng có liên hệ với literal, nhưng không phải hai tên cho cùng một cấu trúc.

## 8. Khi nào nên dùng `+`, `StringBuilder`, hoặc API khác?

Sự khác nhau về bytecode **không** thay đổi hướng dẫn chọn API: `+` đủ rõ cho một biểu thức ngắn; `StringBuilder` thường phù hợp khi tích lũy qua nhiều vòng; `String.join`/ `StringJoiner` thuận tiện cho dấu phân cách. Xem bài **Nối trong một biểu thức so với tích lũy qua vòng lặp** để phân tích đầy đủ các tình huống đó.

## 9. Đo hiệu năng đúng và tránh phụ thuộc vào chi tiết JDK

Bytecode `invokedynamic` không trực tiếp chứng minh phép nối nhanh hơn hoặc không cấp phát: JIT, warm-up, escape analysis, GC, độ dài chuỗi và dữ liệu đầu vào đều ảnh hưởng kết quả. Nếu cần so sánh JDK/compiler, hãy kiểm tra `javac -version`, `java -version`, `--release` và `javap` trước, rồi dùng benchmark đại diện như JMH.

Không xây logic ứng dụng dựa vào hình dạng recipe, số object suy đoán từ `javap` hoặc cờ compiler nội bộ.

## 10. Các lỗi thường gặp

1. **Khẳng định compiler luôn chuyển `+` thành `StringBuilder`.** Điều này mô tả phổ biến `javac` Java 8 nhưng không bao quát Java 9+ và các biến thể compiler.
2. **Khẳng định mọi dấu `+` ở Java 17 đều dùng `invokedynamic`.** Biểu thức hằng có thể đã được gộp khi biên dịch; các phần còn lại phụ thuộc compiler và target.
3. **Lấy phiên bản `java -version` để suy ra phiên bản compiler.** Runtime và công cụ biên dịch có thể khác nhau; kiểm tra cả `javac -version` và `--release`.
4. **Cho rằng `--release 8` là chạy một compiler JDK 8 thật.** Không: đó là yêu cầu mục tiêu phát sinh mã/ API tương thích, khi compiler hỗ trợ.
5. **Đọc output `javap` như danh sách object thực sự được cấp phát.** JIT có thể tối ưu khi thực thi; `javap` chỉ nhìn bytecode đã ghi trong class file.
6. **Cho rằng `invokedynamic` nghĩa là hành vi nối chuỗi khác `+` cũ.** Bất kể cơ chế, chương trình hợp lệ phải tuân theo ngữ nghĩa Java.
7. **Tự viết `a + b + c` rồi nghĩ tất cả đều là cộng số.** Khi có `String`, các toán hạng tiếp theo nối thành văn bản; dùng ngoặc để làm rõ ý định.
8. **Viết `StringBuilder` trong mỗi vòng lặp nhưng vẫn tạo builder mới mỗi lượt.** Khi cần tích lũy, tạo và tái sử dụng builder ở phạm vi thích hợp.
9. **Benchmark `+` và builder không khống chế JDK, dữ liệu và warm-up.** Kết luận hiệu năng có thể sai; dùng công cụ đo phù hợp.
10. **Trộn lẫn class-file constant pool với runtime String pool.** Hai khái niệm phục vụ mục đích khác nhau.

## 11. Tự kiểm tra

Cho mã sau:

```java
final String a = "he";
String b = "he";
String x = a + "llo";
String y = b + "llo";

System.out.println(x == "hello");
System.out.println(y.equals("hello"));
System.out.println(y == "hello");
```

Kết quả theo ngữ nghĩa Java:

```text
true
true
false
```

`a` là constant variable nên `a + "llo"` là biểu thức hằng được intern. `b` không phải constant variable nên `b + "llo"` được nối lúc chạy và có nội dung bằng `"hello"` nhưng không cùng đối tượng literal. Thay JDK hoặc cách biên dịch có thể làm **bytecode nối `y` khác đi**, nhưng không được làm kết quả ba phép kiểm tra thay đổi trái với đặc tả.

**Ghi nhớ:** Ngữ nghĩa của `String` và toán tử `+` thuộc **ngôn ngữ Java**; cách compiler hạ phép nối thành `StringBuilder`, `invokedynamic` và cách runtime tối ưu chúng thuộc **triển khai JDK/JVM**. Hãy viết mã dựa vào ngữ nghĩa, quan sát thực tế bằng `javap` khi cần tìm hiểu, và đo hiệu năng trên môi trường mục tiêu.

### Tài liệu tham khảo

- [JEP 280 — Indify String Concatenation](https://openjdk.org/jeps/280)
- [Java SE 21 — StringConcatFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/StringConcatFactory.html)
- [Java Language Specification — `15.18.1`, String Concatenation Operator `+`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.18.1)


</details>

- [Quay lại đầu trang](#back-to-top)
