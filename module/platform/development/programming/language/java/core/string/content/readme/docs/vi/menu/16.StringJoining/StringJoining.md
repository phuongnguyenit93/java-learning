<a id="back-to-top"></a>

# Ghép nhiều phần

## Menu
- [String.join với mảng hoặc Iterable](#string-joining-001)
- [Dấu phân cách và phần tử rỗng](#string-joining-002)
- [Chính sách null của delimiter, tập phần tử và từng phần tử](#string-joining-003)
- [StringJoiner với delimiter, prefix và suffix](#string-joining-004)
- [StringJoiner.add, merge và setEmptyValue](#string-joining-005)
- [Collectors.joining trong xử lý Stream](#string-joining-006)
- [So sánh join, StringJoiner, StringBuilder và phép nối +](#string-joining-007)

## <a id="string-joining-001">String.join với mảng hoặc Iterable</a>

<details>
<summary>Xem chi tiết</summary>

# String.join với mảng hoặc Iterable

## 1. Mục tiêu bài học

Khi có nhiều đoạn văn bản như tên cột, thẻ, đường dẫn logic hoặc các thành phần thông báo, ta thường muốn ghép chúng thành **một chuỗi** và chèn cùng một dấu phân cách giữa các phần tử. `String.join(...)` giải quyết đúng tình huống đó: nhận một `delimiter` và nhiều phần tử kiểu `CharSequence`, rồi trả về một `String` mới.

Sau bài này, bạn có thể:
- Chọn đúng overload `String.join` cho **mảng**, **danh sách/Iterable** hoặc các tham số rời.
- Dự đoán kết quả khi danh sách rỗng, có một phần tử, có `null` hoặc dấu phân cách rỗng.
- Biết khi nào cần xử lý dữ liệu trước khi ghép và khi nào chuyển sang `StringJoiner` hoặc `Collectors.joining`.

## 2. Hai overload cần biết

Từ Java 8, lớp `java.lang.String` cung cấp hai phương thức tĩnh:

```java
public static String join(CharSequence delimiter, CharSequence... elements);

public static String join(
        CharSequence delimiter,
        Iterable<? extends CharSequence> elements);
```

Trong đó:
- `delimiter` là chuỗi/dãy ký tự chèn **giữa hai phần tử liên tiếp**, không tự thêm vào đầu hoặc cuối.
- `elements` là các phần tử cần ghép, theo đúng thứ tự được truyền vào hoặc thứ tự mà `Iterable` duyệt qua.
- Giá trị trả về là `String` mới; thao tác không thay đổi mảng, collection hay bản thân các phần tử.
- `CharSequence` là giao diện mà `String`, `StringBuilder` và `StringBuffer` triển khai. Vì thế tham số không bắt buộc phải có kiểu chính xác là `String`.

| Dữ liệu đầu vào | Cách gọi | Overload được chọn |
| --- | --- | --- |
| Các phần tử rời | `String.join("-", "A", "B", "C")` | `CharSequence...` |
| `String[]` | `String.join("-", array)` | `CharSequence...`, truyền cả mảng |
| `List<String>` | `String.join("-", list)` | `Iterable<? extends CharSequence>` |
| `Set<String>` | `String.join("-", set)` | `Iterable<? extends CharSequence>` |
| `Iterable<StringBuilder>` | `String.join("-", items)` | `Iterable<? extends CharSequence>` |

**Phân biệt cú pháp:** dấu `...` trong khai báo Java là *varargs*; trong lời gọi, bạn có thể truyền các phần tử rời hoặc một mảng đối tượng tương thích. Với `List`, `Set` hay một `Iterable` tự tạo, Java dùng overload thứ hai.

## 3. Ví dụ hoàn chỉnh: mảng, Iterable và varargs

Tạo tệp `JoinDemo.java` với mã sau (Java 17+):

```java
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JoinDemo {
    public static void main(String[] args) {
        String[] words = {"Java", "Core", "String"};
        List<String> colors = List.of("đỏ", "xanh", "vàng");
        Set<String> ordered = new LinkedHashSet<>(List.of("A", "B", "C"));

        System.out.println("array: " + String.join(" / ", words));
        System.out.println("list: " + String.join(", ", colors));
        System.out.println("set: " + String.join(" -> ", ordered));
        System.out.println("varargs: " +
                String.join(".", "java", "lang", "String"));
        System.out.println("empty: [" +
                String.join(", ", new String[0]) + "]");
        System.out.println("one: " +
                String.join(" | ", new String[]{"solo"}));
        System.out.println("null item: " +
                String.join(", ", Arrays.asList("A", null, "C")));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 JoinDemo.java
java JoinDemo
```

Kết quả chuẩn:

```text
array: Java / Core / String
list: đỏ, xanh, vàng
set: A -> B -> C
varargs: java.lang.String
empty: []
one: solo
null item: A, null, C
```

Các điểm cần quan sát:
1. Mảng `words` có ba phần tử nên dấu ` / ` xuất hiện **hai lần**.
2. `colors` là `List<String>`, được xử lý thông qua `Iterable` mà không cần chuyển thành mảng.
3. `LinkedHashSet` giữ thứ tự chèn `A, B, C` nên kết quả trên có thứ tự xác định; `HashSet` nói chung không hứa hẹn cùng thứ tự đó.
4. Lời gọi `String.join(".", "java", "lang", "String")` sử dụng varargs trực tiếp.
5. Mảng rỗng trả chuỗi rỗng; ký hiệu `[]` xuất hiện chỉ vì ta tự ghép vào lệnh `println` để dễ quan sát.
6. Phần tử `null` trong danh sách được chuyển thành văn bản `"null"`; đây không phải thao tác bỏ qua phần tử.

## 4. Cơ chế ghép và quy tắc kết quả

Xét lời gọi:

```java
String result = String.join(" | ", "A", "B", "C");
```

Ta có thể hình dung quá trình là `A` + `" | "` + `B` + `" | "` + `C`, tạo `"A | B | C"`. Dấu phân cách được chèn **n − 1 lần** khi có `n >= 1` phần tử. `String.join` sử dụng cơ chế ghép nội bộ của thư viện Java, nên ta không cần tự nối lặp `result += ...`.

| Trường hợp | Ví dụ | Giá trị trả về |
| --- | --- | --- |
| Không phần tử | `String.join(",", new String[0])` | `""` |
| Một phần tử | `String.join(",", "A")` | `"A"` |
| Nhiều phần tử | `String.join(",", "A", "B")` | `"A,B"` |
| Dấu phân cách rỗng | `String.join("", "A", "B")` | `"AB"` |
| Dấu phân cách nhiều ký tự | `String.join(" -> ", "A", "B")` | `"A -> B"` |
| Phần tử rỗng | `String.join(",", "A", "", "B")` | `"A,,B"` |
| Phần tử `null` | `String.join(",", "A", (CharSequence) null)` | `"A,null"` |

**Lưu ý:** `String.join` **không** tự cắt khoảng trắng, lọc phần tử rỗng, loại trùng, sắp xếp hoặc escape ký tự phân cách bên trong từng phần tử. Ví dụ, `String.join(",", "a,b", "c")` trả `"a,b,c"`, không giữ lại thông tin nhóm ban đầu khi tách bằng dấu phẩy đơn thuần.

### Phân biệt `null` ở từng vị trí

- `delimiter == null`: ném `NullPointerException`.
- Cả mảng `elements` hoặc cả `Iterable` là `null`: ném `NullPointerException`.
- **Một phần tử bên trong** mảng/`Iterable` là `null`: giá trị đó được thể hiện bằng chuỗi ký tự `"null"` trong kết quả.

Ví dụ phân biệt hai tình huống khác nhau:

```java
String[] values = {"A", null, "C"};
System.out.println(String.join("-", values)); // A-null-C

String[] missingArray = null;
// String.join("-", missingArray); // NullPointerException
```

Đừng nhầm ngoại lệ xảy ra **trước lúc join** với hành vi của `join`: `List.of("A", null)` tự ném `NullPointerException` vì `List.of` không cho phép phần tử `null`. Nếu muốn kiểm thử một danh sách chứa `null`, có thể dùng `Arrays.asList("A", null)`.

## 5. Khi làm việc với mảng

`String[]` là lựa chọn trực tiếp nhất:

```java
String[] paths = {"api", "v1", "users"};
String route = String.join("/", paths); // "api/v1/users"
```

`String.join` không tự chuẩn hóa đường dẫn hay thêm dấu `/` ở đầu. Nếu cần một đường dẫn hệ thống thực sự, hãy cân nhắc API đường dẫn như `Path` thay vì xây đường dẫn hệ điều hành bằng phép ghép chuỗi.

Mảng kiểu nguyên thủy, chẳng hạn `int[]`, **không** tương thích với `CharSequence...`. Ví dụ `String.join(",", new int[]{1, 2})` không biên dịch. Muốn ghép số, cần chuyển số thành chuỗi:

```java
import java.util.stream.Collectors;
import java.util.stream.IntStream;

String ids = IntStream.of(7, 12, 30)
        .mapToObj(String::valueOf)
        .collect(Collectors.joining("-"));

System.out.println(ids); // 7-12-30
```

Ví dụ này là **đoạn mã đặt trong một phương thức hoặc chương trình có sẵn**; muốn chạy độc lập, hãy đặt các câu lệnh vào `main` và thêm những import được hiển thị.

## 6. Khi làm việc với Iterable

`Iterable<? extends CharSequence>` cho phép dùng `List<String>`, `Set<String>` và các collection/đối tượng có khả năng duyệt khác. Không cần gọi `toArray()` chỉ để ghép phần tử.

```java
import java.util.List;

List<String> roles = List.of("reader", "editor", "admin");
String description = String.join(" | ", roles);
// "reader | editor | admin"
```

Với `List`, thứ tự danh sách là thứ tự ghép. Với `Set`, thứ tự phụ thuộc vào loại set:
- `LinkedHashSet` giữ thứ tự chèn.
- `TreeSet` duyệt theo thứ tự sắp xếp của nó.
- `HashSet` không bảo đảm thứ tự duyệt; không dùng khi đầu ra bắt buộc ổn định.

Một đối tượng chỉ khai báo kiểu `Iterable<String>` vẫn truyền thẳng được cho `String.join`, vì overload thứ hai yêu cầu `Iterable<? extends CharSequence>`. Các phần tử được duyệt để tạo kết quả; `String.join` không đòi danh sách phải hỗ trợ truy cập theo chỉ số.

### Iterable chứa CharSequence khác String

```java
import java.util.List;

List<StringBuilder> builders = List.of(
        new StringBuilder("first"),
        new StringBuilder("second")
);

String joined = String.join(" + ", builders);
// "first + second"
```

Ở thời điểm ghép, nội dung mỗi `StringBuilder` được biểu diễn thành chuỗi. Chuỗi `joined` đã tạo ra sẽ không tự thay đổi nếu sau đó bạn sửa những `StringBuilder` nguồn.

## 7. Làm sạch dữ liệu trước khi join

Trong dữ liệu thực tế, bạn có thể cần bỏ `null`, trim khoảng trắng và loại mục rỗng. `String.join` không có tham số bật các hành vi này; hãy xử lý nguồn trước:

```java
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

List<String> raw = Arrays.asList("  Java  ", "", null, "  Core ");
List<String> cleaned = raw.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .toList();

System.out.println(String.join(", ", cleaned)); // Java, Core
```

Đây là đoạn mã minh họa dành cho `main` hoặc một phương thức thích hợp; `Stream.toList()` có từ Java 16, nên chạy được với Java 17+. Không nên mặc định loại phần tử rỗng hoặc `null` trong mọi bài toán: đôi khi vị trí trống trong dữ liệu có ý nghĩa và cần được giữ lại.

Nếu cần kiểm soát phân cách theo từng điều kiện, escape định dạng hoặc chuẩn hóa văn bản theo quy tắc nghiệp vụ, hãy xử lý rõ ràng trước khi gọi `join`.

## 8. Chọn String.join, StringJoiner hay Collectors.joining

| Công cụ | Tình huống phù hợp | Đặc điểm |
| --- | --- | --- |
| `String.join` | Đã có mảng, `Iterable` hoặc vài chuỗi rời | Ngắn gọn; chỉ chọn delimiter và các phần tử |
| `StringJoiner` | Thêm dần các phần tử; muốn prefix/suffix | Có `add`, delimiter, prefix, suffix |
| `Collectors.joining` | Đang xử lý `Stream<String>` | Kết hợp tự nhiên với `filter`, `map`; có overload prefix/suffix |

Ví dụ khi cần dấu ngoặc bao quanh:

```java
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

StringJoiner joiner = new StringJoiner(", ", "[", "]");
joiner.add("A").add("B");
System.out.println(joiner); // [A, B]

String collected = List.of("A", "B").stream()
        .collect(Collectors.joining(", ", "[", "]"));
System.out.println(collected); // [A, B]
```

`String.join` chỉ chèn dấu phân cách; `String.join(", ", items)` tự nó không sinh tiền tố `[` hoặc hậu tố `]`. Cũng không cần chuyển `List` thành `Stream` chỉ để gọi `Collectors.joining` khi `String.join` đã giải quyết đủ yêu cầu.

## 9. Sai lầm thường gặp và cách tránh

1. **Dùng nhầm kiểu phần tử.** `String.join(",", List.of(1, 2))` không tương thích vì `Integer` không triển khai `CharSequence`. Chuyển phần tử sang `String` trước.
2. **Cho rằng `null` bị bỏ qua.** Phần tử `null` trở thành chữ `"null"`; nếu không muốn vậy, lọc trước.
3. **Cho rằng `String.join` tự bỏ khoảng trắng.** `" A "` vẫn chứa hai khoảng trắng; trim riêng khi cần.
4. **Cho rằng mọi `Set` đều có thứ tự ổn định.** Chọn cấu trúc bảo toàn thứ tự hoặc sắp xếp chủ động nếu kết quả cần tái lập.
5. **Nhầm dấu phân cách thành biểu thức chính quy.** `String.join` chèn delimiter nguyên văn, không diễn giải `.`, `|` hoặc `*` như regex.
6. **Dùng join để xuất CSV hoàn chỉnh.** Giá trị có dấu phẩy, dấu nháy kép hoặc xuống dòng cần quy tắc CSV; `String.join(",", fields)` không tự quote/escape.
7. **Nối chuỗi lặp bằng `+=` để thay join.** Với một collection có sẵn, `String.join` thể hiện mục đích rõ ràng và tránh tự quản lý dấu phân cách đầu/cuối.
8. **Truyền `null` không rõ kiểu cho overload.** Các overload đều nhận kiểu tham chiếu; hãy khai báo dữ liệu có kiểu rõ ràng, kiểm tra `null` trước khi gọi.

## 10. Bài tự luyện

**Bài 1.** Có `String[] tags = {"java", "core", "api"};`. Tạo chuỗi `"java | core | api"` mà không dùng vòng lặp.

```java
String[] tags = {"java", "core", "api"};
String result = String.join(" | ", tags);
```

**Bài 2.** Có `List<String> letters = List.of("x", "y", "z");`. Dự đoán kết quả của `String.join("", letters)` và `String.join("/", List.<String>of())`.

Đáp án: `"xyz"` và `""`. Dấu phân cách rỗng không tạo ký tự giữa các phần tử; iterable rỗng cho chuỗi rỗng.

**Bài 3.** Với `Arrays.asList("A", "", null, "B")` và delimiter `"-"`, dự đoán kết quả nếu không lọc dữ liệu.

Đáp án: `"A--null-B"`. Phần tử rỗng vẫn chiếm một vị trí, còn `null` được chuyển thành chữ `"null"`.

**Bài 4.** Muốn xuất nhiều trường có thể chứa dấu phẩy và xuống dòng theo định dạng CSV chuẩn, có nên chỉ dùng `String.join(",", fields)`?

Đáp án: Không đủ. Cần cơ chế quote/escape CSV phù hợp (hoặc thư viện CSV) trước khi phát hành dữ liệu; `String.join` chỉ thực hiện ghép ký tự.

## 11. Ghi nhớ

- **Mảng `String[]` hoặc `CharSequence[]`:** dùng overload varargs, ví dụ `String.join(", ", array)`.
- **`List<String>`, `Set<String>` hay `Iterable<? extends CharSequence>`:** truyền trực tiếp, ví dụ `String.join(", ", list)`.
- **Không phần tử:** nhận `""`. **Phần tử `null`:** nhận chữ `"null"`. **Delimiter hoặc nguồn `null`:** `NullPointerException`.
- **Thứ tự:** theo mảng hoặc thứ tự duyệt của `Iterable`; không tự sắp xếp.
- **Chuẩn hóa, lọc, thêm prefix/suffix hay xuất định dạng có escape:** cần bước xử lý khác hoặc công cụ phù hợp.

Tài liệu API tham khảo: [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html), [Java SE 17 — StringJoiner](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/StringJoiner.html), [Java SE 17 — Collectors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-joining-002">Dấu phân cách và phần tử rỗng</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu phân cách và phần tử rỗng khi ghép nhiều chuỗi trong Java

## 1. Vấn đề cần giải quyết

Khi ghép nhiều phần tử thành một chuỗi, thông thường ta đặt **dấu phân cách** (*delimiter/separator*) giữa hai phần tử liên tiếp. Nhưng điều gì xảy ra nếu một phần tử là chuỗi rỗng `""`, nằm ở đầu, giữa hoặc cuối danh sách? Nếu toàn bộ danh sách không có phần tử thì kết quả ra sao? Và có nên tự động bỏ các phần tử rỗng trước khi ghép?

Các câu hỏi này quan trọng trong việc tạo dòng dữ liệu, nối tham số, hiển thị danh sách, xây chuỗi log hoặc trao đổi thông tin với hệ thống khác. Một dấu phân cách thừa hoặc thiếu có thể làm thay đổi **số lượng và vị trí của trường dữ liệu**.

Xét ba chuỗi đầu vào:

```java
String a = "Java";
String b = "";
String c = "String";

String result = String.join("|", a, b, c);
System.out.println(result); // Java||String
```

Hai dấu `|` liền nhau **không phải lỗi**: chúng thể hiện rằng có **một phần tử rỗng ở giữa**. Nếu bỏ `b` trước khi ghép, kết quả `Java|String` mang cấu trúc khác.

Nguyên tắc nền tảng: **phần tử rỗng không đồng nghĩa với phần tử không tồn tại**.

## 2. `String.join` đặt dấu phân cách như thế nào?

Từ Java 8, `String.join` có hai overload phổ biến:

```java
String.join(CharSequence delimiter, CharSequence... elements);
String.join(CharSequence delimiter,
            Iterable<? extends CharSequence> elements);
```

Đây là **chữ ký API để tham khảo**, không phải khai báo chương trình độc lập. `delimiter` là chuỗi phân cách, còn `elements` là dãy phần tử theo thứ tự do bạn cung cấp hoặc thứ tự mà `Iterable` duyệt được.

Với `n` phần tử, nếu `n >= 1`, quy tắc ghép là:

```text
element[0] + delimiter + element[1] + ... + delimiter + element[n-1]
```

Nói cách khác, có **n − 1 lần chèn delimiter giữa các phần tử**, không tự chèn thêm ở đầu hay cuối. Khi `n = 0`, kết quả là chuỗi rỗng `""`.

```java
System.out.println(String.join(", ", "A", "B", "C")); // A, B, C
System.out.println(String.join(" / ", "A"));         // A
System.out.println(String.join(", ", new String[0]).isEmpty()); // true
```

**Lưu ý:** Dấu phân cách có thể là một hoặc nhiều ký tự, thậm chí chuỗi rỗng `""`. Dấu phân cách được sao chép dưới dạng văn bản **literal**; `String.join` không dùng biểu thức chính quy (*regex*) để hiểu delimiter.

### Bảng hành vi của phần tử rỗng

Trong bảng sau, dấu `<...>` chỉ để **nhìn thấy ranh giới kết quả**, không phải ký tự do `String.join` tự thêm.

| Các phần tử đầu vào | Delimiter | Kết quả nhìn trong dấu `<...>` | Ý nghĩa |
| --- | --- | --- | --- |
| Không có phần tử: `[]` | `","` | `<>` | Không có gì để ghép |
| Một phần tử: `["A"]` | `","` | `<A>` | Không cần dấu phân cách |
| Một phần tử rỗng: `[""]` | `","` | `<>` | Có phần tử, nhưng nội dung rỗng |
| Hai phần tử rỗng: `["", ""]` | `","` | `<,>` | Delimiter nằm giữa hai chuỗi rỗng |
| `["A", "", "B"]` | `","` | `<A,,B>` | Giữ phần tử rỗng ở giữa |
| `["", "A"]` | `","` | `<,A>` | Delimiter ở đầu **do phần tử đầu rỗng** |
| `["A", ""]` | `","` | `<A,>` | Delimiter ở cuối **do phần tử cuối rỗng** |
| `["", "A", ""]` | `","` | `<,A,>` | Giữ cả hai phần tử rỗng hai đầu |
| `["A", "", "B"]` | `""` | `<AB>` | Không có ký tự nào thể hiện ranh giới |

**Phân biệt:** `String.join(",", new String[0])` và `String.join(",", "")` đều trả về `""`. Chỉ nhìn chuỗi kết quả **không thể phân biệt danh sách rỗng với danh sách có đúng một phần tử rỗng**. Đây là hạn chế thông tin của cách mã hóa dạng nối đơn giản, không phải lỗi API.

## 3. Ba khái niệm khác nhau: không có phần tử, phần tử rỗng và `null`

Trong Java, ba trường hợp sau không giống nhau:

- **Không có phần tử:** danh sách/array dài 0; ví dụ `List.of()`.
- **Có một phần tử rỗng:** danh sách dài 1, chứa `""`. Đây là một giá trị `String` hợp lệ với `length() == 0`.
- **Có một phần tử `null`:** danh sách dài 1, chứa tham chiếu `null`; nó không phải đối tượng `String` và không phải chuỗi `"null"`.

Theo hợp đồng của `String.join`, nếu **một phần tử** là `null`, API sẽ thêm văn bản `"null"` vào kết quả:

```java
String joined = String.join("|", "A", null, "B");
System.out.println(joined); // A|null|B
```

Điều này tạo nên điểm cần cân nhắc: phần tử tham chiếu `null` và phần tử **literal `"null"`** sẽ cho cùng cách biểu diễn sau khi nối. Nếu dữ liệu cần phân biệt giá trị vắng mặt và chuỗi bốn ký tự `"null"`, bạn phải có **quy tắc biểu diễn riêng** (ví dụ định dạng dữ liệu có hỗ trợ giá trị null), không dựa vào `String.join`.

Bên cạnh đó:

```java
String.join(",", "A", null);                // "A,null"
String.join(",", "A", "");                  // "A,"
String.join(",", new String[0]);           // ""
String.join(",", new String[]{null});      // "null"
```

Các dòng trên là **biểu thức minh họa**. Tên `"null"` được viết ra khi **element null** xuất hiện; nhưng truyền **delimiter null** hoặc chính mảng/`Iterable` `elements == null` sẽ ném `NullPointerException`. Đừng nhầm “phần tử null được chuyển thành văn bản” với “mọi đối số null đều được chấp nhận”.

## 4. Dấu phân cách ở vị trí đầu/cuối có thể hoàn toàn đúng

Một tình huống phổ biến là muốn nối ba trường theo thứ tự, trong đó trường đầu hoặc cuối được phép rỗng:

```java
String first = "";
String middle = "java";
String last = "";

String line = String.join("|", first, middle, last);
System.out.println("<" + line + ">"); // <|java|>
```

Đầu dòng có `|` vì **trước trường `middle` còn một trường rỗng**. Cuối dòng có `|` vì **sau `middle` còn một trường rỗng**. Đây không phải “delimiter được chèn thừa”.

Nếu yêu cầu dữ liệu là giữ nguyên cả ba vị trí, việc tự ý xóa dấu đầu/cuối bằng `strip()` hoặc logic cắt ký tự có thể phá vỡ thông tin. `strip()` cũng không được thiết kế để loại bỏ delimiter; nó loại bỏ một số khoảng trắng đầu/cuối theo `Character.isWhitespace`.

Trái lại, trong trường hợp **hiển thị một danh sách tên** mà phần tử rỗng vốn là dữ liệu không có ý nghĩa, bạn có thể chọn loại chúng **trước khi gọi join**. Đây phải là một quyết định theo hợp đồng bài toán, không phải hành vi mặc định mà ta gán cho API.

## 5. Khi nào giữ phần tử rỗng, khi nào lọc bỏ?

Hãy định nghĩa rõ ràng mục tiêu dữ liệu:

| Mục tiêu | Chính sách đối với `""` | Ví dụ |
| --- | --- | --- |
| Dữ liệu dạng cột, mỗi vị trí ứng với một trường | **Giữ** phần tử rỗng | `["A", "", "B"]` → `A||B` |
| Tạo nhãn hiển thị từ các phần **tùy chọn** | Có thể bỏ `""` nếu phần bỏ trống không mang ý nghĩa | `["Lan", "", "Nguyen"]` → `Lan Nguyen` |
| Biểu diễn số ô trống trong bản ghi cố định | **Giữ** cả rỗng ở đầu và cuối | `["", "A", ""]` → `|A|` |
| Tạo đường dẫn, URL, câu lệnh giao thức | Theo **đặc tả tương ứng**, không tự lọc | Dấu phân cách có thể mang ngữ nghĩa |
| Log hoặc thông điệp để con người đọc | Quyết định rõ liệu muốn giữ trường hay chỉ giữ giá trị có nội dung | Không được gây nhầm mất trường |

Ví dụ lọc **chỉ chuỗi rỗng**:

```java
import java.util.List;
import java.util.stream.Collectors;

List<String> parts = List.of("Lan", "", "Nguyen");

String keepAll = String.join(" ", parts);
String skipEmpty = parts.stream()
        .filter(s -> !s.isEmpty())
        .collect(Collectors.joining(" "));

System.out.println("<" + keepAll + ">");  // <Lan  Nguyen>
System.out.println("<" + skipEmpty + ">"); // <Lan Nguyen>
```

Đây là **đoạn minh họa**; khi dùng `List.of`, phần tử không được là `null`. Lọc bằng `!s.isEmpty()` chỉ loại `""`, **không** loại chuỗi `" "` hoặc `"\t"`. Nếu yêu cầu là bỏ những phần tử toàn khoảng trắng, có thể xem xét `!s.isBlank()` (Java 11+), nhưng `isBlank` và `isEmpty` có ngữ nghĩa khác nhau và không nên tráo đổi tùy tiện.

### Với `null`: xác định chính sách rõ ràng

Nếu `null` nghĩa là “không có phần cần hiển thị”, có thể chủ động bỏ chúng:

```java
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

List<String> items = Arrays.asList("A", null, "", "B");
String joined = items.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.joining("|"));

System.out.println(joined); // A||B
```

Ở đây lọc `null` nhưng **giữ** phần tử rỗng, nên vẫn có hai delimiter liên tiếp. Nếu cũng muốn bỏ rỗng, phải thêm chính sách lọc thích hợp **sau khi** đã loại `null` để tránh `NullPointerException`.

Việc bỏ `null` thích hợp với một số danh sách hiển thị, nhưng có thể **không đúng** với bản ghi có số trường cố định; đối với trường bắt buộc, nên **báo lỗi hoặc dùng format có null riêng**.

## 6. `StringJoiner`: kiểm soát delimiter, prefix, suffix và giá trị khi chưa có phần tử

`java.util.StringJoiner` là công cụ mutable phục vụ **tích lũy** các phần tử, chèn delimiter giữa chúng và (nếu cần) thêm tiền tố/hậu tố.

```java
import java.util.StringJoiner;

StringJoiner joiner = new StringJoiner(", ", "[", "]");
joiner.add("A").add("").add("B");
System.out.println(joiner); // [A, , B]
```

`StringJoiner.add("")` vẫn được tính là **đã thêm một phần tử**. Nó không bỏ qua chuỗi rỗng. Tương tự, `add(null)` thêm chữ `"null"`.

### Chưa thêm gì khác với đã thêm `""`

```java
StringJoiner joiner = new StringJoiner(", ", "[", "]");

System.out.println(joiner); // []
joiner.setEmptyValue("[none]");
System.out.println(joiner); // [none]

joiner.add("");
System.out.println(joiner); // []
joiner.add("B");
System.out.println(joiner); // [, B]
```

Trước `add(...)`, `StringJoiner` chưa có phần tử và dùng `prefix + suffix`, hoặc **empty value** do `setEmptyValue(...)` đặt ra. Sau `add("")`, nó **không còn được xem là empty** dù nội dung phần tử là `""`: nó trở lại biểu diễn thông thường là `prefix + "" + suffix`, ở đây là `[]`. Khi thêm `B`, delimiter xuất hiện giữa phần tử rỗng và `B`.

**Lưu ý:** Nếu chưa có phần tử, `new StringJoiner(",")` không prefix/suffix trả về `""`. Nếu muốn hiển thị khác giữa danh sách không có phần tử và danh sách có phần tử rỗng, dùng cơ chế `setEmptyValue` hoặc lưu riêng **số lượng phần tử**, nhưng không thể mong chuỗi kết quả của `String.join` tự giữ metadata đó.

`StringJoiner` hữu ích khi số phần tử chưa biết trước hoặc thêm dần trong vòng lặp. Với danh sách có sẵn, `String.join` thường đơn giản hơn.

## 7. `Collectors.joining`: ghép từ Stream mà vẫn giữ phần tử rỗng

Đối với `Stream<String>`, Java cung cấp các collector:

```java
import java.util.stream.Collectors;
import java.util.stream.Stream;

String result = Stream.of("A", "", "B")
        .collect(Collectors.joining("|"));

System.out.println(result); // A||B
```

Các dạng thường dùng:

| API | Ý nghĩa |
| --- | --- |
| `Collectors.joining()` | Ghép trực tiếp, không có delimiter |
| `Collectors.joining(delimiter)` | Chèn delimiter giữa các phần tử |
| `Collectors.joining(delimiter, prefix, suffix)` | Chèn delimiter và thêm prefix/suffix cho kết quả |

Một stream **không có phần tử** với `Collectors.joining("|")` cho `""`; stream có **một phần tử `""`** cũng cho `""`. Với collector có prefix/suffix, biểu diễn rỗng theo `StringJoiner`: ví dụ `Collectors.joining(", ", "[", "]")` trên stream rỗng trả về `"[]"`.

Nếu cần bỏ phần tử rỗng, **lọc trước khi collect**:

```java
String display = Stream.of("A", "", "B")
        .filter(s -> !s.isEmpty())
        .collect(Collectors.joining("|"));

System.out.println(display); // A|B
```

Trước khi dùng pipeline với các phần tử có thể `null`, hãy xác định chính sách và bổ sung `filter(Objects::nonNull)` khi phù hợp. `Collectors.joining` không tự mang ý nghĩa “lọc giá trị thiếu”; thông thường, `null` được biểu diễn như chữ `"null"` khi đi qua `StringJoiner`.

## 8. Chương trình Java 17 hoàn chỉnh

Lưu nội dung sau vào tệp `JoinEmptyDemo.java`. Các dấu `<...>` được in ra **chỉ để nhìn rõ** khi chuỗi bắt đầu/kết thúc bằng delimiter hoặc hoàn toàn rỗng.

```java
import java.util.Arrays;
import java.util.Collections;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JoinEmptyDemo {
    public static void main(String[] args) {
        String[] fields = {"", "java", "", "core", ""};
        String joined = String.join("|", fields);
        String[] restored = joined.split(Pattern.quote("|"), -1);
        String[] defaultSplit = joined.split(Pattern.quote("|"));

        System.out.println("joined=<" + joined + ">");
        System.out.println("restored count=" + restored.length);
        System.out.println("restored same=" + Arrays.equals(fields, restored));
        System.out.println("first empty=" + restored[0].isEmpty());
        System.out.println("middle empty=" + restored[2].isEmpty());
        System.out.println("last empty=" + restored[4].isEmpty());
        System.out.println("default split count=" + defaultSplit.length);

        System.out.println("empty collection=<"
                + String.join(",", Collections.<String>emptyList()) + ">");
        System.out.println("one empty=<" + String.join(",", "") + ">");
        System.out.println("two empty=<" + String.join(",", "", "") + ">");
        System.out.println("empty separator=<"
                + String.join("", "A", "", "B") + ">");
        System.out.println("null item=<"
                + String.join("|", "A", null, "B") + ">");

        StringJoiner sj = new StringJoiner(", ", "[", "]");
        System.out.println("joiner no elements=" + sj);
        sj.setEmptyValue("[none]");
        System.out.println("joiner custom empty=" + sj);
        sj.add("");
        System.out.println("joiner one empty=" + sj);
        sj.add("B");
        System.out.println("joiner two elements=" + sj);

        String collected = Stream.of("a", "", "b")
                .collect(Collectors.joining("|"));
        System.out.println("stream=<" + collected + ">");
    }
}
```

Biên dịch/chạy bằng **JDK 17 hoặc mới hơn**:

```bash
javac JoinEmptyDemo.java
java JoinEmptyDemo
```

Kết quả đã kiểm tra với Java 21 (đồng thời tương thích Java 17):

```text
joined=<|java||core|>
restored count=5
restored same=true
first empty=true
middle empty=true
last empty=true
default split count=4
empty collection=<>
one empty=<>
two empty=<,>
empty separator=<AB>
null item=<A|null|B>
joiner no elements=[]
joiner custom empty=[none]
joiner one empty=[]
joiner two elements=[, B]
stream=<a||b>
```

**Cách đọc kết quả:**

- `joined=<|java||core|>` chứng minh phần tử rỗng ở đầu, giữa và cuối được giữ.
- `restored count=5` và `restored same=true` chứng minh ở **trường hợp dữ liệu này** dùng `split(..., -1)` khôi phục đủ năm phần tử.
- `default split count=4` cho thấy `split(regex)` không giữ phần tử rỗng **cuối** theo mặc định.
- `empty collection=<>` và `one empty=<>` cho ra cùng chuỗi; số lượng phần tử ban đầu đã không còn nhận biết được.
- `joiner custom empty=[none]` nhưng sau `add("")` trở thành `[]` vì joiner đã có một phần tử dù rỗng.

## 9. Ghép rồi tách lại: vì sao phần tử rỗng dễ bị mất?

Nếu bạn dùng delimiter để **mã hóa nhiều trường**, việc giải mã bằng `String.split` phải được hiểu rõ.

```java
String text = "A,,B,";
String[] withoutLimit = text.split(",");
String[] keepTrailing = text.split(",", -1);

System.out.println(withoutLimit.length); // 3
System.out.println(keepTrailing.length); // 4
```

`split(regex)` tương đương `split(regex, 0)`: Java loại bỏ **các phần tử rỗng ở cuối** của mảng kết quả. `split(regex, -1)` giữ các phần tử rỗng ở cuối, vì `limit` âm cho phép áp dụng mẫu không giới hạn và không loại bỏ phần tử rỗng kết thúc.

Ở giữa chuỗi, phần tử rỗng vẫn có thể giữ lại:

```java
String[] middle = "A,,B".split(",", -1);
// Nội dung logic: ["A", "", "B"]
```

### Delimiter của `join` là văn bản; delimiter của `split` là regex

Đây là khác biệt đặc biệt quan trọng:

```java
String joined = String.join("|", "A", "", "B"); // A||B
String[] parts = joined.split(
        java.util.regex.Pattern.quote("|"), -1);
```

`String.join("|", ...)` coi `|` là delimiter nguyên văn. Nhưng `split("|")` không hiểu `|` theo nghĩa đó: trong regex, `|` là toán tử lựa chọn (*alternation*). Dùng `Pattern.quote("|")` hoặc biểu thức regex escape phù hợp để tách **đúng delimiter literal**. Nếu delimiter là dữ liệu cấu hình động như `".", "|", "||"`, `Pattern.quote(delimiter)` thường giúp tránh lỗi do ký tự đặc biệt regex.

### `split(..., -1)` vẫn không bảo đảm hoàn nguyên mọi đầu vào

Ngay cả khi đã giữ phần tử rỗng cuối, `join` + `split` **không phải phép mã hóa hoàn nguyên tổng quát**:

1. **Danh sách rỗng và danh sách có một chuỗi rỗng cùng ra `""`.** Tách `""` theo delimiter không rỗng với `limit=-1` cho một chuỗi rỗng, không thể biết ban đầu có 0 hay 1 phần tử.
2. **Phần tử tự chứa delimiter sẽ gây nhập nhằng.** Ví dụ `["A,B", "C"]` và `["A", "B", "C"]` đều ghép với dấu `,` thành `"A,B,C"`.
3. **`null` và literal `"null"` mất sự khác biệt** vì `String.join` chuyển cả hai thành chữ `"null"`.
4. **Delimiter rỗng `""` không giữ ranh giới.** `["AB"]` và `["A", "B"]` đều có thể trở thành `"AB"` nếu nối bằng delimiter rỗng.

Do đó `String.join` rất tốt cho **biểu diễn đơn giản**, nhưng không phải serializer mang đủ thông tin trường, escaping, null và ranh giới.

## 10. Không tự dùng `String.join` như CSV/TSV serializer tổng quát

CSV có những quy tắc riêng liên quan đến delimiter, dấu ngoặc kép, escape, dòng mới và chính sách đối với các trường rỗng. Nếu trường dữ liệu chứa dấu phẩy, ngoặc kép hoặc ký tự xuống dòng, chỉ `String.join(",", fields)` **không đủ** để xuất CSV đúng trong mọi trường hợp.

Ví dụ:

```java
String[] fields = {"Nguyen, Lan", "Java"};
System.out.println(String.join(",", fields));
// Nguyen, Lan,Java
```

Nếu chỉ `split(",")` sau đó, kết quả sẽ có **ba đoạn** thay vì hai trường ban đầu. Với CSV đúng chuẩn, trường có dấu phẩy thường phải được quote, và dấu quote bên trong trường cũng phải được escape theo định dạng tương ứng. Nên dùng thư viện/parser CSV thích hợp khi dữ liệu cần lưu trữ hoặc truyền hệ thống. Các định dạng như JSON cũng có quy tắc rõ về mảng rỗng, `null`, chuỗi rỗng và escaping.

**Lựa chọn thực tế:**

- **Nhóm nhãn hiển thị:** `String.join`, có thể lọc phần tử rỗng theo nhu cầu hiển thị.
- **Ghép nội dung log đơn giản:** `StringJoiner` hoặc `Collectors.joining`; bảo đảm không mất trường quan trọng khi debug.
- **Dòng bản ghi cố định và delimiter không xuất hiện trong trường:** giữ đủ số trường, dùng delimiter literal, `split(Pattern.quote(delimiter), -1)` khi tách; vẫn phải xử lý trường hợp danh sách 0/1 rỗng.
- **Dữ liệu cấu trúc tổng quát:** định dạng/serializer có escaping, null và độ dài trường rõ ràng.

## 11. Lỗi thường gặp

1. **Tưởng `String.join` tự bỏ `""`.** Không: mỗi phần tử rỗng vẫn chiếm một vị trí và làm xuất hiện delimiter theo số lượng phần tử.
2. **Tưởng delimiter ở đầu/cuối là do join thêm sai.** Nếu phần tử đầu/cuối rỗng, delimiter có thể xuất hiện ở biên kết quả một cách hoàn toàn đúng.
3. **Không phân biệt danh sách rỗng và một phần tử rỗng.** Hai trường hợp đều có thể mã hóa thành `""`, nhưng một bên có 0 phần tử, bên kia có 1 phần tử.
4. **Nhầm phần tử `null` với delimiter `null`.** Phần tử null được chuyển thành chữ `"null"`; delimiter null hoặc tập phần tử null ném `NullPointerException`.
5. **Gọi `split(",")` rồi mất các trường rỗng cuối.** Nếu cần giữ chúng, dùng `split(",", -1)`.
6. **Dùng `split("|")` để tách dấu `|` literal.** `split` nhận regex; hãy dùng `Pattern.quote("|")`.
7. **Lọc rỗng trong dữ liệu có vị trí cột cố định.** Việc lọc khiến các giá trị sau bị dịch cột; không được đánh đổi cấu trúc chỉ để chuỗi nhìn gọn hơn.
8. **Đánh đồng `isEmpty()` với `isBlank()`.** `""` rỗng; `" "` không rỗng nhưng có thể blank. Chính sách loại bỏ của chúng khác nhau.
9. **Dùng `join` rồi `split` như cơ chế mã hóa không mất mát.** Dữ liệu có delimiter, null hoặc danh sách 0/1 rỗng làm đảo chiều không còn đáng tin.
10. **Tự tạo CSV bằng cách nối dấu phẩy mà không escape.** CSV/TSV hay giao thức dữ liệu có thể yêu cầu quote, escape và quy tắc về dòng mới; hãy dùng công cụ đúng định dạng.

## 12. Bài tập tự kiểm tra

Hãy tính hai chuỗi sau mà **không chạy code**:

```java
String first = String.join("-", "", "A", "", "B", "");
String second = String.join("-", "A", "B");
```

Đáp án:

```text
first  = -A--B-
second = A-B
```

Có năm phần tử trong `first` nên có **bốn dấu `-` phân cách**, kể cả khi các phần tử ngoài cùng rỗng. `second` có hai phần tử nên chỉ có một dấu phân cách.

Tiếp theo, với `String value = "A||B|"`, hỏi:

- `value.split(java.util.regex.Pattern.quote("|"))` có **3** phần tử (`"A"`, `""`, `"B"`) vì phần tử rỗng cuối bị bỏ.
- `value.split(java.util.regex.Pattern.quote("|"), -1)` có **4** phần tử, gồm phần tử rỗng cuối.
- Không thể biết chỉ từ một chuỗi `"A||B|"` liệu dữ liệu ban đầu đã được tạo bằng một cách mã hóa với escaping hay không; muốn đảm bảo tái tạo toàn bộ đầu vào cần một hợp đồng biểu diễn phù hợp.

**Ghi nhớ:** `String.join` chèn dấu phân cách **giữa các phần tử**, không mặc định bỏ chuỗi rỗng. Hãy quyết định rõ chính sách giữ/lọc `""` và `null` trước khi ghép; nếu muốn tách lại, nhớ regex của `split` và `limit=-1`, nhưng đừng xem nối chuỗi đơn giản là một định dạng dữ liệu hoàn nguyên.

### Tài liệu API Java 17

- [`String.join` và `String.split` — Oracle Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`StringJoiner` — Oracle Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/StringJoiner.html)
- [`Pattern.quote` và giới hạn của `split` — Oracle Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-joining-003">Chính sách null của delimiter, tập phần tử và từng phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Chính sách `null` của delimiter, tập phần tử và từng phần tử khi ghép chuỗi Java

## 1. Vì sao phải phân biệt ba loại `null`?

Khi ghép nhiều phần thành một chuỗi, Java cho phép dùng `String.join(...)` với **delimiter** (chuỗi phân cách) và một **tập phần tử** dưới dạng varargs hoặc `Iterable`. Có ba chỗ độc lập có thể mang giá trị `null`:

1. **Delimiter `null`:** chuỗi dùng để ngăn cách các phần tử không tồn tại.
2. **Tập phần tử `null`:** chính mảng varargs hoặc đối tượng `Iterable` không tồn tại.
3. **Một phần tử `null`:** tập phần tử có tồn tại nhưng một mục bên trong bằng `null`.

Ba trường hợp **không có cùng hành vi**. Theo API của Java 17:

| Vị trí `null` | Ví dụ | Kết quả |
| --- | --- | --- |
| Delimiter | `String.join(null, "A", "B")` | Ném `NullPointerException`. |
| Mảng varargs `elements` | `String.join(",", (CharSequence[]) null)` | Ném `NullPointerException`. |
| `Iterable elements` | `String.join(",", (Iterable<? extends CharSequence>) null)` | Ném `NullPointerException`. |
| Một phần tử trong varargs | `String.join(",", "A", null, "B")` | `"A,null,B"`. |
| Một phần tử trong `Iterable` | `String.join(",", Arrays.asList("A", null, "B"))` | `"A,null,B"`. |

**Quy tắc ngắn gọn:** `String.join` yêu cầu **delimiter và tập phần tử không được `null`**, nhưng nếu **một phần tử riêng lẻ** là `null`, nó sẽ được biểu diễn bằng chuỗi ký tự `"null"`.

Điều này rất quan trọng khi xuất dữ liệu, tạo log, xây dựng câu lệnh cấu hình, hoặc chuyển danh sách thành chuỗi. Chuỗi chữ `"null"` không nhất thiết có cùng ý nghĩa với giá trị `null` ban đầu của ứng dụng.

## 2. Hai overload của `String.join`

Từ Java 8, `String` có hai overload:

```java
public static String join(
        CharSequence delimiter,
        CharSequence... elements)

public static String join(
        CharSequence delimiter,
        Iterable<? extends CharSequence> elements)
```

### 2.1. Overload varargs

```java
String result = String.join(" | ", "Java", "Core", "String");
System.out.println(result); // Java | Core | String
```

`elements` được biểu diễn bằng một mảng `CharSequence[]` khi lời gọi được xử lý. Thông thường người lập trình không phải tự tạo mảng; chỉ cần liệt kê các đối số.

### 2.2. Overload `Iterable`

```java
import java.util.List;

List<String> words = List.of("Java", "Core", "String");
String result = String.join(" | ", words);

System.out.println(result); // Java | Core | String
```

Đây là một `Iterable` có ba phần tử. Hợp đồng xử lý `null` của các **phần tử** vẫn giống overload varargs: một mục bằng `null` được thêm dưới dạng chữ `"null"`.

Chú ý: `List.of(...)` **không chấp nhận phần tử `null`**. Nếu cần minh họa một `Iterable` thực sự chứa `null`, có thể dùng `Arrays.asList(...)` hoặc một collection cho phép `null`:

```java
import java.util.Arrays;
import java.util.List;

List<String> words = Arrays.asList("Java", null, "String");
System.out.println(String.join(" | ", words));
// Java | null | String
```

**Phân biệt rõ:** lỗi khi cố tạo `List.of("A", null)` là do **`List.of`**, không phải do `String.join`. `String.join` chấp nhận mục `null` nếu collection đã chứa nó.

## 3. Trường hợp thứ nhất: delimiter bằng `null`

Delimiter là nội dung được chèn **giữa các phần tử liên tiếp**, ví dụ `","`, `"; "` hoặc `" | "`. Nếu delimiter bằng `null`, `String.join` ném `NullPointerException`:

```java
String separator = null;

// Ném NullPointerException:
String value = String.join(separator, "A", "B");
```

Đặc điểm:
- `null` không được tự hiểu là chuỗi rỗng.
- `null` không được tự hiểu là dấu phẩy.
- Không nên dùng biểu diễn chữ `"null"` làm delimiter dự phòng nếu nghiệp vụ không quy định như vậy.
- **Delimiter rỗng `""` là hợp lệ** và khác delimiter `null`.

Ví dụ delimiter rỗng:

```java
System.out.println(String.join("", "A", "B", "C")); // ABC
```

### Chọn chính sách cho delimiter từ người dùng

Nếu việc thiếu delimiter là lỗi của đầu vào, nên **fail fast**:

```java
import java.util.Objects;

static String joinRequiredDelimiter(
        CharSequence delimiter,
        Iterable<? extends CharSequence> parts) {
    Objects.requireNonNull(delimiter, "delimiter");
    Objects.requireNonNull(parts, "parts");
    return String.join(delimiter, parts);
}
```

Đoạn trên là **phương thức hỗ trợ đặt trong lớp Java**, không phải file chạy độc lập. Khi delimiter hoặc tập phần tử là `null`, thông báo lỗi sẽ chỉ ra thành phần vi phạm hợp đồng.

Nếu sản phẩm thực sự muốn coi delimiter thiếu là “không có ký tự phân cách”, hãy chuẩn hóa **có chủ đích** trước:

```java
String effectiveDelimiter =
        delimiter == null ? "" : delimiter.toString();

String result = String.join(effectiveDelimiter, "A", "B");
```

Khi `delimiter == null`, kết quả là `"AB"`. Đây là **chính sách do ứng dụng chọn**, không phải hành vi mặc định của `String.join`.

## 4. Trường hợp thứ hai: toàn bộ tập phần tử bằng `null`

Tập phần tử có thể được truyền dưới dạng `CharSequence[]` hoặc `Iterable<? extends CharSequence>`. Nếu bản thân đối tượng tập hợp không tồn tại, `String.join` không có nguồn dữ liệu để duyệt và ném `NullPointerException`.

### 4.1. Mảng varargs `null`

```java
CharSequence[] parts = null;

// Ném NullPointerException:
String result = String.join(",", parts);
```

### 4.2. `Iterable` bằng `null`

```java
Iterable<String> parts = null;

// Ném NullPointerException:
String result = String.join(",", parts);
```

Nhưng **tập phần tử rỗng** hoàn toàn hợp lệ:

```java
System.out.println("[" + String.join(",") + "]"); // []
System.out.println("[" + String.join(",", java.util.List.<String>of()) + "]");
// []
```

Hai dòng trên in `[]` do chúng ta chủ động thêm dấu ngoặc vuông; kết quả của `String.join` thực tế là **chuỗi rỗng `""`**, không phải chuỗi `"[]"`.

| Đầu vào | Ví dụ | Kết quả |
| --- | --- | --- |
| Tập `null` | `(CharSequence[]) null` | `NullPointerException` |
| Tập có 0 phần tử | `new CharSequence[0]` | `""` |
| Tập có 1 phần tử rỗng | `new CharSequence[]{""}` | `""` |
| Tập có 1 phần tử `null` | `new CharSequence[]{null}` | `"null"` |

**Quan trọng:** tập rỗng và tập chỉ có một chuỗi rỗng cho cùng văn bản `""` sau khi join. Vì thế `String.join` **không phải một định dạng lưu trữ có khả năng khôi phục đầy đủ danh sách gốc** trong mọi tình huống.

### Chính sách cho tập `null`

Ba lựa chọn phổ biến:

- **Báo lỗi:** input bắt buộc phải có; dùng `Objects.requireNonNull(parts)`.
- **Coi như tập rỗng:** chỉ khi hợp đồng nghiệp vụ cho phép. Ví dụ chuyển `null` thành `List.of()` trước khi join.
- **Phân biệt “chưa có dữ liệu” và “danh sách rỗng”:** không biến cả hai thành `""` nếu ứng dụng cần giữ thông tin khác biệt. Có thể dùng kiểu dữ liệu hoặc trường trạng thái riêng.

Ví dụ coi danh sách `null` là rỗng một cách tường minh:

```java
static String joinOrEmpty(
        String delimiter,
        java.util.List<String> parts) {
    java.util.Objects.requireNonNull(delimiter, "delimiter");
    return String.join(
            delimiter,
            parts == null ? java.util.List.<String>of() : parts);
}
```

Hàm vẫn báo lỗi nếu delimiter `null`, nhưng cho phép không có `parts` và trả về chuỗi rỗng. Đó là **một thiết kế tùy chọn**; không nên áp dụng mặc định khi dữ liệu bắt buộc.

## 5. Trường hợp thứ ba: từng phần tử có thể bằng `null`

Khi một mục trong tập là `null`, `String.join` thêm **bốn ký tự** `n`, `u`, `l`, `l`:

```java
String result = String.join("|", "A", null, "B");
System.out.println(result); // A|null|B
```

Tương tự với `Iterable`:

```java
java.util.List<String> parts =
        java.util.Arrays.asList("A", null, "B");

System.out.println(String.join("|", parts)); // A|null|B
```

Điều đó **không** tương đương với một trong các hành vi sau:
- Bỏ phần tử `null` → `"A|B"`.
- Thay phần tử `null` thành chuỗi rỗng → `"A||B"`.
- Ném ngoại lệ ngay khi gặp phần tử `null`.
- Chèn một giá trị nullable có thể khôi phục nguyên trạng sau khi split.

Nếu `"null"` cũng có thể là chuỗi hợp lệ do người dùng nhập, kết quả join **không cho phép phân biệt**:

```java
String a = String.join(",", "A", (CharSequence) null);
String b = String.join(",", "A", "null");

System.out.println(a);          // A,null
System.out.println(b);          // A,null
System.out.println(a.equals(b)); // true
```

**Đây là mất thông tin**, không phải bug của `String.join`. Khi hệ thống cần phân biệt giá trị vắng mặt và chuỗi văn bản `"null"`, hãy dùng một cách mã hóa có quy tắc riêng (ví dụ JSON với `null` đúng kiểu và chuỗi `"null"` được đặt trong dấu nháy), thay vì lấy kết quả join làm định dạng lưu trữ chính.

### Điểm chú ý về overload khi truyền literal `null`

Do `String.join` có cả overload varargs và `Iterable`, lời gọi dùng `null` không chỉ rõ kiểu như:

```java
// Không nên dùng vì dễ mơ hồ overload:
// String.join(",", null);
```

Có thể dẫn đến vấn đề lựa chọn overload hoặc diễn giải `null` là cả mảng/tập thay vì một phần tử. **Hãy khai báo hoặc ép kiểu rõ ý định**:

```java
System.out.println(String.join("|", (CharSequence) null));
// null  (một phần tử null được chuyển thành chữ "null")

// Ném NullPointerException: toàn bộ mảng varargs null.
// String.join("|", (CharSequence[]) null);

// Ném NullPointerException: toàn bộ Iterable null.
// String.join("|", (Iterable<? extends CharSequence>) null);
```

Điều khác biệt là **kiểu tại vị trí đối số** và overload Java chọn, không chỉ hình thức viết chữ `null`.

## 6. Phân biệt phần tử rỗng, phần tử `null` và phần tử có dấu phân cách

Xét delimiter `","`:

| Danh sách đầu vào | Kết quả `String.join(",", ...)` | Nhận xét |
| --- | --- | --- |
| `["A", "B"]` | `"A,B"` | Bình thường |
| `["A", null, "B"]` | `"A,null,B"` | Giá trị `null` trở thành chữ `"null"` |
| `["A", "", "B"]` | `"A,,B"` | Vị trí rỗng vẫn được giữ lại |
| `["A", "null", "B"]` | `"A,null,B"` | Không phân biệt được với phần tử `null` |
| `["A,B", "C"]` | `"A,B,C"` | Dữ liệu chứa delimiter, không tự escape |
| `[]` | `""` | Không có phần tử |
| `[""]` | `""` | Một phần tử rỗng, cùng kết quả với tập rỗng |

`String.join` **không phải một trình mã hóa CSV**. Nó không tự thêm dấu nháy cho giá trị chứa dấu phân cách, không escape dấu nháy, và cũng không tạo cách biểu diễn nullable có thể khôi phục an toàn. Nếu làm CSV, JSON, TSV hoặc dữ liệu trao đổi theo chuẩn cụ thể, hãy dùng bộ mã hóa đúng định dạng đó.

Ví dụ dấu phẩy nằm trong dữ liệu:

```java
System.out.println(String.join(",", "New York, NY", "USA"));
// New York, NY,USA
```

Không thể tách lại kết quả này thành chính xác hai phần ban đầu chỉ bằng `split(",")`. Chọn delimiter khác đôi khi giảm va chạm nhưng **không thay thế escape/quoting**.

## 7. Bốn chính sách phổ biến đối với phần tử `null`

Nếu nghiệp vụ có yêu cầu khác mặc định của `String.join`, bạn cần xử lý phần tử **trước khi ghép**.

Dùng một danh sách có `null` thật sự:

```java
java.util.List<String> parts =
        java.util.Arrays.asList("A", null, "B");
```

### Chính sách A — Giữ mặc định: chữ `"null"`

```java
String output = String.join("|", parts);
System.out.println(output); // A|null|B
```

Phù hợp với một số log hoặc mục đích đọc thủ công, nơi bạn chấp nhận `null` chỉ là biểu diễn văn bản, không cần khôi phục kiểu dữ liệu ban đầu.

### Chính sách B — Bỏ các mục `null`

```java
import java.util.Objects;
import java.util.stream.Collectors;

String output = parts.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.joining("|"));

System.out.println(output); // A|B
```

**Tác động:** số vị trí trong chuỗi xuất ra giảm. Nếu các phần tử là cột dữ liệu cố định (ví dụ mã đơn hàng, ngày, người nhận), bỏ một mục có thể làm lệch vị trí của các cột sau.

### Chính sách C — Thay `null` bằng chuỗi rỗng

```java
import java.util.stream.Collectors;

String output = parts.stream()
        .map(x -> x == null ? "" : x)
        .collect(Collectors.joining("|"));

System.out.println(output); // A||B
```

Cách này giữ vị trí phần tử giữa hai delimiter, nhưng không phân biệt `null` với một chuỗi rỗng vốn tồn tại trong dữ liệu gốc.

### Chính sách D — Thay bằng placeholder có ý nghĩa

```java
import java.util.stream.Collectors;

String output = parts.stream()
        .map(x -> x == null ? "<missing>" : x)
        .collect(Collectors.joining("|"));

System.out.println(output); // A|<missing>|B
```

Placeholder chỉ thích hợp nếu hợp đồng nghiệp vụ xác định rõ nó, và nếu chuỗi `"<missing>"` có thể xuất hiện trong dữ liệu thật thì vẫn cần cơ chế escape/phân biệt.

### Chính sách E — Cấm phần tử `null` (fail fast)

```java
import java.util.Objects;
import java.util.stream.Collectors;

String output = parts.stream()
        .map(x -> Objects.requireNonNull(x, "part"))
        .collect(Collectors.joining("|"));
```

Ở danh sách `["A", null, "B"]`, đoạn này ném `NullPointerException` với thông báo `"part"`. Cách xử lý phù hợp khi **mỗi phần tử bắt buộc có dữ liệu** và bạn muốn phát hiện lỗi tại ranh giới nghiệp vụ, thay vì âm thầm tạo văn bản `"null"`.

**Không có chính sách nào tốt nhất trong mọi tình huống.** Hãy quyết định rõ có cần giữ số lượng phần tử, vị trí cột, thông tin `null`, hoặc khả năng phục hồi giá trị gốc hay không.

## 8. Chương trình Java 17+ chạy độc lập

Lưu nội dung sau vào file `JoinNullPolicyDemo.java`:

```java
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class JoinNullPolicyDemo {
    private static void showResult(String label, Supplier<String> action) {
        try {
            System.out.println(label + " = " + action.get());
        } catch (NullPointerException ex) {
            System.out.println(label + " = NullPointerException");
        }
    }

    public static void main(String[] args) {
        List<String> parts = Arrays.asList("A", null, "B");

        System.out.println("varargs = ["
                + String.join("|", "A", null, "B") + "]");
        System.out.println("iterable = ["
                + String.join("|", parts) + "]");
        System.out.println("single null = ["
                + String.join("|", (CharSequence) null) + "]");
        System.out.println("empty varargs = ["
                + String.join("|") + "]");
        System.out.println("empty iterable = ["
                + String.join("|", List.<String>of()) + "]");
        System.out.println("empty member = ["
                + String.join("|", "A", "", "B") + "]");

        showResult("null delimiter",
                () -> String.join((CharSequence) null, "A", "B"));
        showResult("null array",
                () -> String.join("|", (CharSequence[]) null));
        showResult("null iterable",
                () -> String.join("|",
                        (Iterable<? extends CharSequence>) null));

        System.out.println("joiner = ["
                + new StringJoiner("|").add(null).add("B") + "]");

        String skip = parts.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining("|"));
        System.out.println("skip null = [" + skip + "]");

        String asEmpty = parts.stream()
                .map(x -> x == null ? "" : x)
                .collect(Collectors.joining("|"));
        System.out.println("null as empty = [" + asEmpty + "]");

        String placeholder = parts.stream()
                .map(x -> x == null ? "<missing>" : x)
                .collect(Collectors.joining("|"));
        System.out.println("placeholder = [" + placeholder + "]");
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 JoinNullPolicyDemo.java
java JoinNullPolicyDemo
```

Đầu ra:

```text
varargs = [A|null|B]
iterable = [A|null|B]
single null = [null]
empty varargs = []
empty iterable = []
empty member = [A||B]
null delimiter = NullPointerException
null array = NullPointerException
null iterable = NullPointerException
joiner = [null|B]
skip null = [A|B]
null as empty = [A||B]
placeholder = [A|<missing>|B]
```

**Giải thích kết quả:**
- Varargs và `Iterable` cùng chèn chữ `"null"` cho một phần tử `null`.
- Một `null` được ép kiểu `CharSequence` là **một phần tử**, nên nhận `"null"`.
- Tập rỗng tạo chuỗi rỗng, nhưng phần tử rỗng ở giữa vẫn giữ hai delimiter.
- Delimiter, mảng varargs, hoặc đối tượng `Iterable` đều không được bằng `null`.
- `StringJoiner.add(null)` cũng thêm văn bản `"null"`.
- Các chính sách `skip`, `asEmpty` và `placeholder` biến đổi danh sách theo cách khác nhau **trước khi nối**.

Chương trình chủ động bắt `NullPointerException` ở từng thí nghiệm để chứng minh hành vi, chứ không khuyến khích dùng `try/catch` thay thế xác thực đầu vào trong mọi chương trình thực tế.

## 9. `StringJoiner` và `Collectors.joining` liên quan thế nào?

`StringJoiner` là công cụ bổ sung hữu ích nếu cần thêm phần tử dần dần hoặc muốn prefix/suffix:

```java
import java.util.StringJoiner;

StringJoiner joiner = new StringJoiner(", ", "[", "]");
joiner.add("A");
joiner.add(null);
joiner.add("B");

System.out.println(joiner.toString()); // [A, null, B]
```

Với `StringJoiner`:
- Delimiter truyền vào constructor không được `null`; nếu `null` thì ném `NullPointerException`.
- `add((CharSequence) null)` chèn văn bản `"null"`.
- Constructor ba tham số còn yêu cầu `prefix` và `suffix` không `null`.
- Nếu chưa thêm phần tử, biểu diễn mặc định gồm `prefix + suffix`; có thể cấu hình `setEmptyValue` nếu cần.

Ví dụ phân biệt rỗng và một phần tử rỗng:

```java
StringJoiner empty = new StringJoiner(",", "[", "]");
System.out.println(empty); // []

StringJoiner oneBlank = new StringJoiner(",", "[", "]");
oneBlank.add("");
System.out.println(oneBlank); // []
```

Dù biểu diễn ngoài giống nhau, trạng thái “đã thêm một phần tử” khác nhau. Khi cần khả năng khôi phục chính xác cấu trúc dữ liệu, không nên xem `StringJoiner` hay `String.join` là một bộ mã hóa cấu trúc.

`Collectors.joining(delimiter)` thường được dùng sau `stream()`. Nó giúp ứng dụng **lọc**, **ánh xạ** hoặc **kiểm tra** đầu vào trước khi nối:

```java
String result = parts.stream()
        .filter(java.util.Objects::nonNull)
        .collect(java.util.stream.Collectors.joining(","));
```

Nếu `parts` bằng `null` thì gọi `parts.stream()` đã gây `NullPointerException` trước khi `Collectors.joining` có cơ hội xử lý. Nếu bạn cần coi danh sách `null` là rỗng, việc lựa chọn đó phải diễn ra **trước lệnh `stream()`**.

Không nên suy luận rằng chỉ vì dùng Stream thì các quy tắc về `null` tự thay đổi theo ý muốn. Chính sách rõ ràng nhất là áp dụng `filter`/`map`/validation tường minh theo hợp đồng ứng dụng.

## 10. Thiết kế API có chính sách `null` rõ ràng

Một thiết kế ghép chuỗi bền vững thường xác định trước ba câu hỏi:

**(A) Delimiter có bắt buộc không?** Nếu có, yêu cầu không `null`. Nếu không, quyết định giá trị mặc định rõ ràng, chẳng hạn `""` hoặc `", "`.

**(B) Tập phần tử có bắt buộc không?** Phân biệt *thiếu danh sách* (`null`), *có danh sách nhưng rỗng*, và *có phần tử rỗng* nếu nghiệp vụ cần sự khác nhau.

**(C) `null` ở từng phần tử có ý nghĩa gì?** Phải chọn: lỗi đầu vào, bỏ mục, giữ vị trí với chuỗi rỗng, placeholder, hay biểu diễn kiểu dữ liệu có thể khôi phục.

Ví dụ API **cấm `null` ở mọi cấp**, thích hợp cho trường cấu hình cần dữ liệu đầy đủ:

```java
static String strictJoin(
        CharSequence delimiter,
        Iterable<? extends CharSequence> elements) {
    java.util.Objects.requireNonNull(delimiter, "delimiter");
    java.util.Objects.requireNonNull(elements, "elements");

    java.util.StringJoiner joiner =
            new java.util.StringJoiner(delimiter);

    for (CharSequence element : elements) {
        joiner.add(java.util.Objects.requireNonNull(
                element, "element"));
    }
    return joiner.toString();
}
```

Đây là **phương thức hỗ trợ**, cần đặt bên trong một lớp Java. Phương thức khác với `String.join` mặc định: nó **báo lỗi** nếu có phần tử `null`, thay vì chuyển thành chữ `"null"`.

Một lựa chọn khác là **bỏ `null`**:

```java
static String skipNulls(
        CharSequence delimiter,
        Iterable<? extends CharSequence> elements) {
    java.util.Objects.requireNonNull(delimiter, "delimiter");
    java.util.Objects.requireNonNull(elements, "elements");

    java.util.StringJoiner joiner =
            new java.util.StringJoiner(delimiter);
    for (CharSequence element : elements) {
        if (element != null) {
            joiner.add(element);
        }
    }
    return joiner.toString();
}
```

Ví dụ đầu vào `["A", null, "B"]` tạo `"A,B"` khi delimiter là `","`. Nếu có các trường dữ liệu theo vị trí cố định, **cân nhắc hậu quả bỏ cột** trước khi chọn chính sách này.

## 11. Lưu ý khi xử lý dữ liệu ngoài ứng dụng

Các bài toán thường gặp:

| Tình huống | Chính sách nên cân nhắc | Vì sao? |
| --- | --- | --- |
| Tạo log chỉ để đọc | Placeholder cho `null` hoặc giữ mặc định `"null"`, nếu được chấp nhận | Dễ nhìn nhưng không bảo đảm khôi phục kiểu dữ liệu. |
| Ghép nhãn người dùng | Có thể lọc `null` hoặc thay bằng văn bản phù hợp | Tránh xuất chữ `"null"` khó hiểu cho người dùng. |
| Dữ liệu cột theo vị trí | Không âm thầm bỏ `null` | Bỏ một mục sẽ làm thay đổi vị trí các cột. |
| Cấu hình mà mọi phần bắt buộc | Fail fast khi thiếu delimiter/tập/phần tử | Phát hiện thiếu dữ liệu sớm. |
| Xuất CSV/JSON | Dùng bộ mã hóa định dạng phù hợp | Cần xử lý escape, ký tự phân cách, dấu nháy và `null` đúng kiểu. |
| Chuỗi cần phân tích ngược về danh sách | Dùng schema/encoding có thể khôi phục | `String.join` không giữ đủ thông tin để đảo ngược mọi trường hợp. |

Một chuỗi như `"A,null,B"` không cho biết phần tử giữa vốn là Java `null` hay chuỗi `"null"`. Tương tự, `"A,,B"` có thể chứa một phần tử rỗng, nhưng nếu delimiter nằm trong dữ liệu thật, việc phân tách bằng `split` cũng không tự bảo đảm dựng lại đúng danh sách ban đầu.

Nếu đầu ra được dùng để xây dựng SQL, câu lệnh shell, CSV hoặc đường dẫn, **đừng xem nối chuỗi là cơ chế escape hoặc bảo vệ injection**. Hãy dùng API có tham số hoặc bộ mã hóa chuyên dụng theo ngữ cảnh.

## 12. Bài tập tự kiểm tra

### Bài 1 — Ba loại `null`

```java
String.join(",", (CharSequence) null);
String.join(",", (CharSequence[]) null);
String.join(",", (Iterable<? extends CharSequence>) null);
```

**Đáp án:** Lời gọi thứ nhất tạo chuỗi `"null"`; lời gọi thứ hai và thứ ba ném `NullPointerException`. Ép kiểu thể hiện **một phần tử** khác **toàn bộ tập phần tử**.

### Bài 2 — Delimiter rỗng so với `null`

```java
System.out.println(String.join("", "A", "B"));
// String.join(null, "A", "B");
```

**Đáp án:** Dòng đầu in `AB`. Dòng bị chú thích nếu chạy sẽ ném `NullPointerException` vì delimiter `null` không hợp lệ.

### Bài 3 — `List.of` và `Arrays.asList`

```java
java.util.List<String> a =
        java.util.Arrays.asList("A", null, "B");
// java.util.List<String> b = java.util.List.of("A", null, "B");
```

**Đáp án:** `Arrays.asList` tạo danh sách chứa phần tử `null`; `List.of` không cho phép phần tử `null` và sẽ ném `NullPointerException` khi khởi tạo. Đây là hợp đồng của collection factory, không phải của `String.join`.

### Bài 4 — Bỏ phần tử hay giữ vị trí?

Đầu vào theo cột `["id-1", null, "ACTIVE"]`. Nếu bỏ `null` trước khi join bằng `";"`, đầu ra là gì, và rủi ro gì?

**Đáp án:** `"id-1;ACTIVE"`. Vị trí “cột thứ hai” biến mất, có thể khiến bên nhận gán nhầm `ACTIVE` vào cột khác. Nếu định dạng cần giữ số cột, hãy dùng schema và encoding có quy tắc rõ ràng; không tự bỏ mục.

### Bài 5 — Chuỗi `"null"` có phân biệt được Java `null` không?

```java
String a = String.join(",", "A", (CharSequence) null);
String b = String.join(",", "A", "null");
System.out.println(a.equals(b));
```

**Đáp án:** `true`. Kết quả văn bản giống nhau. `String.join` không bảo toàn kiểu nullable gốc.

### Bài 6 — Chọn chính sách cho UI

Một ứng dụng ghép tên hiển thị từ các mục do người dùng nhập. Nếu thiếu một mục, có nên mặc định hiển thị chữ `"null"` không?

**Đáp án:** Không nên tự động mặc định. Xác định yêu cầu giao diện: bỏ mục bị thiếu, dùng placeholder dễ hiểu hoặc báo lỗi. Nếu ứng dụng cần lưu và khôi phục dữ liệu gốc, lưu một cấu trúc có thông tin nullable thay vì dựa vào chuỗi join.

## 13. Tóm tắt

- `String.join` có hai overload chính: **varargs `CharSequence...`** và **`Iterable<? extends CharSequence>`**.
- **Delimiter `null` → `NullPointerException`.**
- **Cả mảng varargs hoặc đối tượng `Iterable` bằng `null` → `NullPointerException`.**
- **Một phần tử riêng lẻ bằng `null` → thêm chữ `"null"`**, không bỏ phần tử và không tự ném lỗi.
- **Tập rỗng → chuỗi rỗng**; phần tử rỗng vẫn tham gia ghép và có thể tạo hai delimiter liên tiếp.
- `StringJoiner.add(null)` cũng nối chữ `"null"`; `Collectors.joining` cho phép áp dụng chính sách `filter`/`map` tường minh trước khi ghép.
- Phân biệt `null` và chuỗi `"null"`, cũng như giá trị chứa delimiter, nếu cần khôi phục dữ liệu.
- Hãy định nghĩa chính sách `null` riêng cho **delimiter**, **tập phần tử**, và **từng phần tử** theo hợp đồng nghiệp vụ, thay vì chỉ dựa vào kết quả mặc định.

## Tài liệu tham khảo

- [Java SE 17 — `String.join(CharSequence, CharSequence...)` và `String.join(CharSequence, Iterable)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — `StringJoiner`: constructor và `add(CharSequence)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/StringJoiner.html)
- [Java SE 17 — `Collectors.joining`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html)
- [Java SE 17 — `List.of` và `Arrays.asList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-joining-004">StringJoiner với delimiter, prefix và suffix</a>

<details>
<summary>Xem chi tiết</summary>

# `StringJoiner` với `delimiter`, `prefix` và `suffix` trong Java

Khi cần ghép nhiều giá trị thành một chuỗi, ta thường muốn đặt **dấu phân cách ở giữa các phần tử** và đôi khi thêm **tiền tố**/**hậu tố** bao quanh toàn bộ kết quả. Ví dụ, từ các phần tử `"Lan"`, `"Mai"` và `"An"`, đầu ra mong muốn có thể là `"[Lan, Mai, An]"`.

`java.util.StringJoiner` được thiết kế đúng cho trường hợp này. Thay vì tự theo dõi «đã thêm phần tử đầu tiên chưa?» để chèn dấu phẩy, ta khai báo quy tắc ghép một lần rồi lần lượt gọi `add(...)`. Đặc biệt, ba khái niệm **delimiter**, **prefix** và **suffix** có ba vai trò hoàn toàn khác nhau:

- **`delimiter` (dấu phân cách):** xuất hiện **giữa** các phần tử, không tự xuất hiện ở đầu hay cuối danh sách.
- **`prefix` (tiền tố):** đứng **trước toàn bộ** nội dung các phần tử.
- **`suffix` (hậu tố):** đứng **sau toàn bộ** nội dung các phần tử.

Một `StringJoiner` là đối tượng *mutable* dùng để tích lũy nội dung; kết quả từ `toString()` là một `String` để dùng hoặc lưu lại.

## 1. Khởi tạo `StringJoiner`

Hai constructor thường dùng:

```java
StringJoiner joiner = new StringJoiner(", ");
StringJoiner wrapped = new StringJoiner(", ", "[", "]");
```

Constructor đầy đủ có chữ ký:

```java
StringJoiner(CharSequence delimiter, CharSequence prefix, CharSequence suffix)
```

Constructor chỉ nhận delimiter tương đương việc dùng tiền tố và hậu tố là chuỗi rỗng. Các tham số cấu hình này **không được `null`**: truyền `null` cho delimiter, prefix hoặc suffix khiến constructor ném `NullPointerException`.

Ví dụ:

```text
new StringJoiner(", ")         -> "A, B, C"
new StringJoiner(", ", "[", "]") -> "[A, B, C]"
new StringJoiner(" | ", "{", "}") -> "{A | B | C}"
```

Đây là hình dung kết quả **sau khi** gọi `add("A")`, `add("B")` và `add("C")` lên từng joiner tương ứng.

Không phải `StringJoiner` tự biết danh sách phần tử. Nó chỉ biết cách nối các chuỗi được bạn truyền lần lượt qua `add(...)`.

### Ví dụ 1: cấu hình một lần, thêm nhiều phần tử

Tạo tệp `StringJoinerBasicsDemo.java`:

```java
import java.util.StringJoiner;

public class StringJoinerBasicsDemo {
    public static void main(String[] args) {
        StringJoiner joiner = new StringJoiner(" | ", "{", "}");

        System.out.println(joiner);

        joiner.add("alpha")
              .add("beta")
              .add("gamma");

        System.out.println(joiner);
        System.out.println(joiner.length());
        System.out.println(joiner.toString().equals("{alpha | beta | gamma}"));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac StringJoinerBasicsDemo.java
java StringJoinerBasicsDemo
```

Kết quả:

```text
{}
{alpha | beta | gamma}
22
true
```

Khi chưa thêm phần tử nào, `toString()` trả `prefix + suffix`, ở đây là `{}`. Sau ba lần `add`, delimiter `" | "` chỉ xuất hiện **giữa** các phần tử; dấu `{` và `}` chỉ xuất hiện mỗi thứ một lần bao quanh toàn bộ nội dung.

`length()` trả số UTF-16 code unit của **biểu diễn kết quả hiện tại**, tương đương `toString().length()`. Nó không trả về số phần tử đã gọi `add`.

## 2. Diễn biến trước và sau mỗi lần `add`

Với cấu hình:

```java
StringJoiner joiner = new StringJoiner(", ", "[", "]");
```

Các bước được hiểu như sau:

| Thời điểm | Nội dung của `joiner.toString()` |
| --- | --- |
| Chưa gọi `add` | `[]` |
| Sau `add("A")` | `[A]` |
| Sau `add("B")` | `[A, B]` |
| Sau `add("C")` | `[A, B, C]` |

**Dấu phân cách không xuất hiện trước phần tử đầu tiên hay tự đứng sau phần tử cuối.** Đây chính là lợi ích so với cách viết nối chuỗi dễ thừa hoặc thiếu dấu:

```java
StringBuilder builder = new StringBuilder("[");
for (String name : names) {
    builder.append(name).append(", ");
}
builder.append("]");
```

Đoạn trên chỉ là minh họa và giả định biến `names` đã tồn tại. Nếu danh sách có phần tử, nó có thể tạo dư `", "` trước dấu `]`. `StringJoiner` tự giải quyết việc đặt delimiter **giữa** các phần tử; bạn không cần xóa delimiter cuối hoặc quản lý biến cờ «đã có phần tử».

Để hiểu đúng, hãy tưởng tượng `StringJoiner` lưu nội dung các phần tử theo thứ tự thêm, rồi tạo ra chuỗi với delimiter và phần bao ngoài khi được yêu cầu. **Không nên phụ thuộc vào cấu trúc bộ nhớ nội bộ của JDK**; những gì chương trình dựa vào là hợp đồng API và kết quả `toString()`.

## 3. Phân biệt bộ bao ngoài với nội dung từng phần tử

`prefix` và `suffix` bao **toàn bộ chuỗi**. Chúng không bọc lần lượt từng giá trị được thêm bằng `add`. Bốn cấu hình dưới đây đều nhận cùng hai phần tử `"A"` rồi `"B"`:

| Delimiter | Prefix | Suffix | Kết quả |
| --- | --- | --- | --- |
| `", "` | `""` | `""` | `A, B` |
| `" / "` | `"["` | `"]"` | `[A / B]` |
| `":"` | `"user="` | `";"` | `user=A:B;` |
| `""` | `"<"` | `">"` | `<AB>` |

Nếu yêu cầu là `[A], [B]`, phải tạo phần tử đã được bọc, chẳng hạn `joiner.add("[" + item + "]")` với delimiter `", "`. Dùng prefix/suffix `"["` và `"]"` sẽ tạo `[A, B]`, biểu diễn một nhóm duy nhất.

**Các wrapper là văn bản nguyên dạng.** `StringJoiner` không kiểm tra cặp dấu ngoặc đúng cú pháp, không phân tích delimiter, không escape dấu phẩy hoặc quote bên trong phần tử. Bạn có thể tạo nhãn văn bản thông thường bằng prefix, nhưng nếu cần xuất CSV, JSON, SQL hoặc URL hợp lệ hãy dùng API hoặc bộ mã hóa đúng định dạng.

## 4. Trạng thái chưa có phần tử

Khi chưa gọi `add`, một joiner với prefix/suffix trả về `prefix + suffix`. Ví dụ `new StringJoiner(", ", "[", "]")` trả `"[]"` khi gọi `toString()` trước lần `add` đầu tiên. Có thể dùng `setEmptyValue(...)` để thay **toàn bộ** biểu diễn khi chưa có phần tử; giá trị đó không bị bọc thêm bởi prefix/suffix.

```java
StringJoiner names = new StringJoiner(" / ", "<", ">");
System.out.println(names); // <>

names.setEmptyValue("(none)");
System.out.println(names); // (none)

names.add("");
System.out.println(names); // <>

names.add("Mai");
System.out.println(names); // < / Mai>
```

`add("")` vẫn ghi nhận **một phần tử**. Dù không có ký tự trong phần tử đầu, sau lần `add` đó kết quả không còn sử dụng emptyValue; khi thêm `"Mai"`, delimiter xuất hiện ở ranh giới hai phần tử. Cần phân biệt “chưa có phần tử” với “đã có một phần tử rỗng”, nhất là khi biểu diễn trường dữ liệu theo vị trí.

Bài `StringJoiner.add, merge và setEmptyValue` phân tích sâu những thay đổi trạng thái, cách `merge` xử lý nguồn rỗng hoặc đã có `add("")`, và sự khác biệt giữa `merge(other)` với `add(other.toString())`. Trong bài này, hãy tập trung vào **hình dạng đầu ra** do delimiter, prefix và suffix xác định.

## 5. Chương trình hoàn chỉnh: ghép nhãn hiển thị

Lưu thành `JoinerEnvelopeDemo.java` và chạy bằng JDK 17 trở lên:

```java
import java.util.List;
import java.util.StringJoiner;

public class JoinerEnvelopeDemo {
    public static void main(String[] args) {
        StringJoiner filters = new StringJoiner(" ; ", "Filters: [", "]");
        System.out.println(filters);

        filters.add("status=OPEN").add("level=HIGH");
        System.out.println(filters);

        StringJoiner names = new StringJoiner(" / ", "<", ">");
        names.setEmptyValue("(none)");
        System.out.println(names);
        names.add("").add("Mai");
        System.out.println(names);

        List<String> tags = List.of("java", "core");
        StringJoiner decoratedTags = new StringJoiner(", ");
        for (String tag : tags) {
            decoratedTags.add("[" + tag + "]");
        }
        System.out.println(decoratedTags);
    }
}
```

```bash
javac -encoding UTF-8 JoinerEnvelopeDemo.java
java JoinerEnvelopeDemo
```

Kết quả:

```text
Filters: []
Filters: [status=OPEN ; level=HIGH]
(none)
< / Mai>
[java], [core]
```

`Filters: []` là biểu diễn của **joiner chưa có phần tử** với prefix/suffix có sẵn. Sau hai lần `add`, delimiter `" ; "` xuất hiện đúng một lần ở giữa. Dòng `< / Mai>` cho thấy delimiter có thể nằm gần đầu chuỗi khi **phần tử đầu là chuỗi rỗng**; phần bao ngoài vẫn chỉ được chèn một lần. Dòng cuối bọc từng tag trong phần tử truyền cho `add`, không dùng wrapper của toàn bộ joiner.

## 6. Giới hạn và cách chọn công cụ

- Constructor yêu cầu delimiter, prefix và suffix **khác `null`**, nhưng `""` hợp lệ. Đối số `add(null)` được biểu diễn bằng chữ `"null"`; muốn bỏ null thì phải lọc trước.
- `StringJoiner.length()` trả số code unit UTF-16 trong văn bản hiển thị, gồm wrapper và delimiter (hoặc emptyValue khi chưa có phần tử); đây không phải số phần tử hay số byte UTF-8.
- `StringJoiner` có thể thay đổi sau `add`, nhưng một `String` đã được trả từ `toString()` không thay đổi khi joiner nhận phần tử mới.
- Khi có mảng/`Iterable` hoàn chỉnh và chỉ cần delimiter, `String.join` ngắn hơn. Khi dữ liệu đã ở `Stream` với `map`/`filter`, dùng `Collectors.joining(delimiter, prefix, suffix)` thường hợp lý. Chọn `StringJoiner` khi cần thêm từng phần và kiểm soát wrapper theo tiến trình.

## 7. Tự kiểm tra

**Bài 1.** Cho:

```java
StringJoiner j = new StringJoiner("|", "(", ")");
j.add("A").add("B").add("C");
```

Kết quả `j.toString()` là `"(A|B|C)"`. Ba phần tử tạo hai delimiter; prefix và suffix chỉ xuất hiện một lần.

**Bài 2.** Với `new StringJoiner(":", "user=", ";")`, trước `add` kết quả là `"user=;"`, sau `add("A").add("B")` là `"user=A:B;"`. Prefix/suffix là văn bản tùy chọn, không nhất thiết là cặp dấu ngoặc.

**Bài 3.** Một joiner `new StringJoiner("-", "[", "]")` được đặt `setEmptyValue("EMPTY")`, rồi lần lượt gọi `add("")` và `add("X")`. Ba lần in trước và sau các lệnh cho:

```text
EMPTY
[]
[-X]
```

`add("")` khiến joiner không còn ở trạng thái chưa có phần tử; `-` được chèn trước phần tử `"X"` tiếp theo.

## 8. Ghi nhớ

`delimiter` nằm **giữa các phần tử**; `prefix` và `suffix` nằm **ngoài toàn bộ kết quả**. Khi chưa có phần tử, kết quả mặc định là prefix + suffix, trừ khi có `setEmptyValue`. Một phần tử `""` vẫn được tính là đã thêm; việc lọc, escape và xác thực nội dung là trách nhiệm riêng của ứng dụng.

Tài liệu tham khảo: [Oracle Java SE 17 — StringJoiner](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/StringJoiner.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-joining-005">StringJoiner.add, merge và setEmptyValue</a>

<details>
<summary>Xem chi tiết</summary>

# StringJoiner.add, merge và setEmptyValue trong Java

Khi cần ghép nhiều phần văn bản thành một chuỗi có dấu phân cách, Java cung cấp `java.util.StringJoiner`. Thay vì tự quản lý điều kiện “có phải phần tử cuối không?”, bạn cung cấp **delimiter** (dấu phân cách), và tùy chọn **prefix** (tiền tố) cùng **suffix** (hậu tố). Mỗi lần `add(...)`, joiner ghi nhận thêm một phần tử. `merge(...)` gộp nội dung của joiner khác, còn `setEmptyValue(...)` quy định cách biểu diễn joiner **chưa có phần tử**.

Ba phương thức có quan hệ chặt chẽ nhưng khác mục đích:

- `add(CharSequence)`: thêm **một phần tử**; có thể thêm chuỗi rỗng và giá trị `null` có quy tắc riêng.
- `merge(StringJoiner)`: thêm **nội dung bên trong** một joiner khác nếu joiner đó không rỗng, nhưng **không mang prefix/suffix của nguồn sang**.
- `setEmptyValue(CharSequence)`: đổi chuỗi hiển thị **chỉ khi chưa từng thêm phần tử**; không phải `clear()` và không xóa nội dung đã có.

Hiểu đúng thế nào là “rỗng” trong `StringJoiner` là điểm mấu chốt để không tạo ra dấu phân cách thừa, không nhập nhầm `"null"`, và không dùng sai `merge`.

## 1. Tạo một StringJoiner: delimiter, prefix, suffix

Lớp `StringJoiner` thuộc package `java.util`:

```java
import java.util.StringJoiner;

StringJoiner plain = new StringJoiner(", ");
StringJoiner decorated = new StringJoiner(", ", "[", "]");
```

Đây là đoạn mã minh họa để đặt trong một phương thức Java. Hai constructor thông dụng:

| Constructor | Ý nghĩa | Ví dụ kết quả sau khi thêm A, B |
|---|---|---|
| `new StringJoiner(delimiter)` | Chỉ có dấu phân cách, prefix/suffix rỗng | `A, B` |
| `new StringJoiner(delimiter, prefix, suffix)` | Dấu phân cách, tiền tố, hậu tố tùy chọn | `[A, B]` |

`delimiter` chỉ xuất hiện **giữa các phần tử**; không tự được thêm trước phần tử đầu tiên hoặc sau phần tử cuối cùng. `prefix` và `suffix` bao quanh **nội dung kết quả** khi joiner đã có phần tử. Nếu chưa thêm phần tử và chưa gọi `setEmptyValue`, `toString()` trả về `prefix + suffix` (với constructor một tham số, hai chuỗi này rỗng nên kết quả là `""`).

Ví dụ:

```text
new StringJoiner(", ")             chưa add gì → ""
new StringJoiner(", ", "[", "]")   chưa add gì → "[]"
new StringJoiner(" / ", "<", ">")  chưa add gì → "<>"
```

**Không nhầm prefix/suffix với phần tử.** Trong `new StringJoiner(",", "[", "]")`, các dấu ngoặc vuông chỉ là vỏ hiển thị của kết quả; chúng không được tính là hai phần tử của joiner. Đối số constructor `delimiter`, `prefix`, `suffix` đều phải khác `null`; truyền null dẫn đến `NullPointerException`.

### Ví dụ 1: ghép các phần và gọi chain

Lưu thành **`StringJoinerAddDemo.java`**:

```java
import java.util.StringJoiner;

public class StringJoinerAddDemo {
    public static void main(String[] args) {
        StringJoiner names = new StringJoiner(", ", "[", "]");

        System.out.println("initial: " + names);

        names.add("An").add("Binh").add("Chi");

        System.out.println("joined: " + names);
        System.out.println("length: " + names.length());
        System.out.println("content: " + names.toString());

        StringJoiner plain = new StringJoiner(" | ");
        plain.add("Java").add("Core");
        System.out.println("plain: " + plain);
    }
}
```

Biên dịch và chạy bằng JDK 17+ trong thư mục chứa tệp:

```bash
javac StringJoinerAddDemo.java
java StringJoinerAddDemo
```

**Stdout:**

```text
initial: []
joined: [An, Binh, Chi]
length: 15
content: [An, Binh, Chi]
plain: Java | Core
```

Đếm `length()`: `[` là 1, `An` là 2, `, ` là 2, `Binh` là 4, `, ` là 2, `Chi` là 3 và `]` là 1; tổng **15 code unit UTF-16**. `StringJoiner.length()` đo độ dài **chuỗi kết quả**, gồm prefix, các phần tử, delimiters và suffix, không phải số phần tử. API không có `size()` dành cho số phần tử.

`add(...)` trả về **chính joiner** để viết chuỗi lệnh fluent; nó không tạo một `StringJoiner` mới sau mỗi lần thêm.

## 2. `add(CharSequence)`: đúng một phần tử mỗi lần gọi

Chữ ký chính:

```java
public StringJoiner add(CharSequence newElement)
```

Ví dụ `joiner.add("red").add("green")` chứa hai phần tử. Khi tạo kết quả, joiner chèn đúng một delimiter giữa hai phần tử này. Một phần tử có thể là `String`, `StringBuilder` hoặc giá trị triển khai `CharSequence` khác. Với đầu vào có thể thay đổi, nên coi dữ liệu được **thêm theo nội dung tại thời điểm gọi**, không mong việc sửa đối tượng nguồn sau đó sẽ tự động cập nhật phần đã thêm.

### Chuỗi rỗng `""` vẫn là một phần tử

`joiner.add("")` **đánh dấu joiner đã có phần tử**, mặc dù ký tự hiển thị được thêm vào có độ dài 0. Nếu tiếp tục thêm phần tử khác, dấu phân cách xuất hiện ở giữa:

```java
StringJoiner j = new StringJoiner("|", "<", ">");
j.add("").add("A");
```

Kết quả `j.toString()` là `"<|A>"`, không phải `"<A>"`. Bởi vì phần tử thứ nhất là chuỗi rỗng, phần tử thứ hai là `"A"`, và delimiter `|` nằm giữa chúng.

Điều này dẫn đến khác biệt quan trọng:

- **Chưa gọi `add` lần nào:** joiner được coi là rỗng theo trạng thái nội bộ.
- **Đã gọi `add("")`:** joiner **không còn rỗng**, dù nếu không có prefix/suffix thì `toString()` vẫn có thể trông giống chuỗi rỗng.

Đừng dùng duy nhất `toString().isEmpty()` làm phép đo đáng tin cho “chưa có phần tử”; hai trạng thái không tương đương.

### Đối số null trở thành văn bản `"null"`

Theo hợp đồng `add(CharSequence)`, nếu `newElement == null` thì nó thêm bốn ký tự `n`, `u`, `l`, `l`. Nó **không** tự bỏ qua phần tử và cũng không tự ném `NullPointerException`.

**`StringJoinerEdgeDemo.java`**

```java
import java.util.StringJoiner;

public class StringJoinerEdgeDemo {
    public static void main(String[] args) {
        StringJoiner first = new StringJoiner("|", "<", ">");
        System.out.println("empty: " + first);
        first.add("");
        System.out.println("after empty string: " + first);
        first.add("A");
        System.out.println("after A: " + first);

        StringJoiner second = new StringJoiner(",", "[", "]");
        second.add(null).add("");
        System.out.println("null and empty: " + second);
    }
}
```

```bash
javac StringJoinerEdgeDemo.java
java StringJoinerEdgeDemo
```

**Stdout:**

```text
empty: <>
after empty string: <>
after A: <|A>
null and empty: [null,]
```

Tình huống `add(null)` thường không phải điều mà nghiệp vụ mong muốn. Nếu muốn **bỏ qua phần tử null**, hãy lọc rõ ràng trước khi gọi:

```java
if (value != null) {
    joiner.add(value);
}
```

Nếu muốn thay null bằng nhãn khác, hãy chủ động dùng `Objects.toString(value, "(missing)")` hoặc quy tắc chuyển đổi thích hợp. Đừng dựa vào `StringJoiner` để suy đoán chính sách xử lý dữ liệu thiếu.

## 3. `setEmptyValue(...)`: trạng thái chưa có phần tử

Chữ ký:

```java
public StringJoiner setEmptyValue(CharSequence emptyValue)
```

Nếu chưa có phần tử, `setEmptyValue` thay chuỗi được trả về bởi `toString()` và giá trị được dùng cho `length()`. Đây là **toàn bộ giá trị hiển thị** cho trạng thái rỗng, **không tự bọc thêm prefix/suffix**.

Ví dụ một joiner có vỏ `[`, `]` mặc định hiển thị `[]`. Nếu gọi `setEmptyValue("(none)")`, chuỗi hiển thị thành `(none)`, **không phải** `[(none)]`.

Khi đã thêm một phần tử, `setEmptyValue` không còn ảnh hưởng đến kết quả hiển thị của joiner hiện tại. Kể cả phần tử đó là `""`, joiner vẫn được coi là đã có phần tử.

### Ví dụ 2: trước và sau khi thêm phần tử

**`StringJoinerEmptyValueDemo.java`**

```java
import java.util.StringJoiner;

public class StringJoinerEmptyValueDemo {
    public static void main(String[] args) {
        StringJoiner tags = new StringJoiner(", ", "[", "]");

        System.out.println("default empty: " + tags);

        tags.setEmptyValue("(none)");
        System.out.println("custom empty: " + tags);
        System.out.println("empty length: " + tags.length());

        tags.add("");
        System.out.println("after add empty: " + tags);
        System.out.println("after add empty length: " + tags.length());

        tags.setEmptyValue("(changed)");
        System.out.println("after setting again: " + tags);

        tags.add("Java");
        System.out.println("after Java: " + tags);
    }
}
```

```bash
javac StringJoinerEmptyValueDemo.java
java StringJoinerEmptyValueDemo
```

**Stdout:**

```text
default empty: []
custom empty: (none)
empty length: 6
after add empty: []
after add empty length: 2
after setting again: []
after Java: [, Java]
```

Tại bước `add("")`, joiner chuyển từ “chưa có phần tử” sang “đã có một phần tử rỗng”. Kết quả `[]` trông giống trạng thái mặc định nhưng **không còn áp dụng emptyValue**, và độ dài của kết quả là 2. Sau khi thêm `"Java"`, delimiter `", "` nằm giữa hai phần tử, cho `"[, Java]"`.

`setEmptyValue(...)` trả về cùng joiner nên có thể chain với `add(...)`:

```java
String result = new StringJoiner(", ", "[", "]")
        .setEmptyValue("(none)")
        .add("A")
        .add("B")
        .toString(); // "[A, B]"
```

Đây là đoạn mã minh họa. Gọi `setEmptyValue(null)` là không hợp lệ và ném `NullPointerException`, khác với `add(null)`. Không có `StringJoiner.clear()` hay `reset()` để biến một joiner đã thêm phần tử quay lại trạng thái chưa thêm; hãy tạo joiner mới nếu cần một lần ghép mới.

### Khi nào đặt emptyValue?

- Danh sách bộ lọc chưa có điều kiện: `"(none)"` hoặc chuỗi rỗng theo chuẩn UI.
- Chuỗi hiển thị của tập phần tử: mặc định `"[]"` nếu có prefix/suffix là ngoặc vuông.
- Kết quả ghép dòng log không có phần tử: có thể dùng `"N/A"` nếu hợp đồng dữ liệu quy định rõ.

Điểm quyết định là **ý nghĩa của danh sách không có phần tử**. Không dùng `setEmptyValue` thay cho kiểm tra phần tử rỗng hoặc phần tử null.

## 4. `merge(StringJoiner)`: nội dung nguồn được ghép như một phần

Chữ ký:

```java
public StringJoiner merge(StringJoiner other)
```

`merge(other)` lấy **nội dung bên trong** joiner `other` để thêm vào joiner nhận lời gọi. Nó bỏ qua **prefix và suffix của nguồn**. Nếu `other` là joiner **chưa có phần tử**, lời gọi **không làm thay đổi nội dung** joiner nhận, dù `other.setEmptyValue(...)` có giá trị tùy chỉnh.

Nếu `other` đã có phần tử, phần văn bản ghép của các phần tử nguồn được thêm vào **như một phần tử tổng hợp duy nhất** của joiner đích. Vì vậy:

- Delimiter của **joiner nguồn** giữa những phần tử nguồn **được giữ nguyên trong nội dung nhập**.
- Delimiter của **joiner đích** được dùng để ngăn phần tổng hợp này với các phần đã có của đích.
- Prefix/suffix của **nguồn không được sao chép**.
- Prefix/suffix của **đích vẫn được dùng** khi dựng chuỗi kết quả.
- Lời gọi trả về **chính joiner đích**; có thể tiếp tục `add()` hoặc `merge()`.

Điểm dễ nhầm: `merge` **không** duyệt từng phần tử nguồn rồi thay mọi delimiter nguồn bằng delimiter đích. Với nguồn `"B+C"` và đích dùng `" | "`, nội dung nhập vẫn có dấu `+`.

### Ví dụ 3: merge với delimiter và vỏ khác nhau

**`StringJoinerMergeDemo.java`**

```java
import java.util.StringJoiner;

public class StringJoinerMergeDemo {
    public static void main(String[] args) {
        StringJoiner target =
                new StringJoiner(" | ", "<", ">").add("A");

        StringJoiner source =
                new StringJoiner("+", "[", "]").add("B").add("C");

        System.out.println("target before: " + target);
        System.out.println("source before: " + source);

        target.merge(source);
        System.out.println("target merged: " + target);
        System.out.println("source visible: " + source);

        target.add("D");
        System.out.println("target after D: " + target);
    }
}
```

```bash
javac StringJoinerMergeDemo.java
java StringJoinerMergeDemo
```

**Stdout:**

```text
target before: <A>
source before: [B+C]
target merged: <A | B+C>
source visible: [B+C]
target after D: <A | B+C | D>
```

Giải thích: nguồn có hai phần tử, biểu diễn nội bộ không có vỏ là `"B+C"`. Khi merge, joiner đích thêm **một phần tử tổng hợp** `"B+C"` sau `"A"`, dùng delimiter đích `" | "`, còn prefix/suffix nguồn `[`, `]` không xuất hiện trong đích. Sau `add("D")`, đích có thêm một phần tử độc lập nên thêm tiếp delimiter đích.

**Không suy luận số phần tử từ kết quả hiển thị:** dấu `+` trong `"B+C"` lúc này là một phần của **một nội dung được merge** vào đích, không phải dấu phân cách do đích quản lý giữa hai phần tử tách biệt. `StringJoiner` không có API công khai trả danh sách phần tử để phân tích lại cấu trúc ban đầu.

### Merge không phải phép nối hai chuỗi bao gồm cả vỏ

Giả sử:

```java
StringJoiner a = new StringJoiner(",", "{", "}").add("X");
StringJoiner b = new StringJoiner(";", "[", "]").add("Y");
a.merge(b);
```

Kết quả của `a.toString()` là `"{X,Y}"`, không phải `"{X,[Y]}"`. Nếu thật sự muốn giữ toàn bộ kết quả trang trí của nguồn như một **phần tử văn bản**, dùng `a.add(b.toString())`. Hai thao tác này có ý nghĩa khác nhau:

- `a.merge(b)`: nhập **nội dung bên trong** joiner nguồn.
- `a.add(b.toString())`: thêm **chuỗi hiển thị đầy đủ**, bao gồm prefix/suffix nguồn, làm một phần tử mới.

Cả hai là đoạn mã minh họa; hãy chọn theo cấu trúc đầu ra muốn tạo.

## 5. Merge joiner chưa có phần tử và joiner đã add("")

Trường hợp biên này thường gây kết quả bất ngờ. Theo hợp đồng của `merge`, “other empty” nghĩa là **chưa có phần tử nào được add**, không phải “toString() hiển thị chuỗi rỗng”. EmptyValue tùy chỉnh chỉ có nhiệm vụ hiển thị và **không trở thành nội dung để merge**.

Ví dụ:

- Source chưa add gì, `source.setEmptyValue("SOURCE_EMPTY")`: `target.merge(source)` không làm thay đổi target.
- Source đã gọi `source.add("")`: source **không còn empty**, và merge sẽ thêm một phần tử có nội dung rỗng vào target.
- Nếu target đang chưa có phần tử nhưng có `setEmptyValue(...)`, merge một phần tử rỗng cũng khiến target **không còn dùng emptyValue**.

### Ví dụ 4: sự khác nhau xuất hiện khi thêm phần tử tiếp theo

**`StringJoinerMergeEmptyDemo.java`**

```java
import java.util.StringJoiner;

public class StringJoinerMergeEmptyDemo {
    public static void main(String[] args) {
        StringJoiner target = new StringJoiner(",", "{", "}")
                .setEmptyValue("TARGET_EMPTY");

        StringJoiner source = new StringJoiner(";", "[", "]")
                .setEmptyValue("SOURCE_EMPTY");

        System.out.println("target initially: " + target);
        System.out.println("source initially: " + source);

        target.merge(source);
        System.out.println("merge untouched: " + target);

        source.add("");
        System.out.println("source with empty member: " + source);
        target.merge(source);
        System.out.println("merge empty member: " + target);

        target.add("X");
        System.out.println("after X: " + target);
    }
}
```

```bash
javac StringJoinerMergeEmptyDemo.java
java StringJoinerMergeEmptyDemo
```

**Stdout:**

```text
target initially: TARGET_EMPTY
source initially: SOURCE_EMPTY
merge untouched: TARGET_EMPTY
source with empty member: []
merge empty member: {}
after X: {,X}
```

Dòng cuối `"{,X}"` chứng minh target đã có **một phần tử rỗng được merge**, dù nhìn tại bước `"{}"` rất giống trạng thái chưa có phần tử. Đừng kiểm tra “đã có phần tử chưa?” bằng cách so `toString()` với prefix+suffix; cùng một văn bản hiển thị có thể tương ứng với hai trạng thái logic khác nhau.

## 6. Dùng `add` hay `merge`, và sự khác biệt về ý định

| Nhu cầu | Lựa chọn | Vì sao |
|---|---|---|
| Thêm một giá trị đơn lẻ `"A"` | `add("A")` | Ghi nhận đúng một phần tử |
| Thêm một phần tử có thể chứa dấu phân cách | `add(value)` | Không tách value thành nhiều phần |
| Nhập phần nội dung đã được ghép từ một joiner khác | `merge(other)` | Giữ delimiter bên trong source và bỏ vỏ source |
| Muốn lấy nguyên đầu ra nguồn gồm cả prefix/suffix | `add(other.toString())` | Xem toàn bộ chuỗi nguồn là một phần tử |
| Muốn chuyển từng phần tử riêng từ một tập dữ liệu | Lặp `add(value)` trên chính dữ liệu đầu vào | Không dựa vào văn bản ghép đã mất cấu trúc phần tử |
| Muốn biểu diễn danh sách chưa có phần tử | `setEmptyValue(text)` | Chỉ thay cách hiển thị trạng thái rỗng |
| Muốn bỏ qua phần tử null | Tự lọc trước `add` | `add(null)` mặc định thêm chuỗi `"null"` |
| Muốn thoát khỏi joiner cũ và bắt đầu từ đầu | Tạo `new StringJoiner(...)` | Không có phương thức `clear()` |

Một ví dụ có ích là gom nhiều nhóm giá trị. Nếu các nhóm dùng **cùng delimiter**, merge thường tạo đầu ra dễ đọc như cộng các danh sách. Nếu các nhóm dùng **delimiter khác nhau**, biểu diễn nguồn được giữ trong phần đã merge, nên phải cân nhắc dữ liệu đầu ra có còn đúng hợp đồng hay không.

## 7. Ví dụ tổng hợp: ghép nhiều nhóm dữ liệu

Giả sử một chương trình tạo dòng báo cáo gồm các nhóm từ khóa. Mỗi nhóm dùng delimiter `", "`, báo cáo tổng dùng delimiter `" / "`. Khi merge nhóm, văn bản ghép của từng nhóm trở thành một phần tử của báo cáo tổng.

**`StringJoinerReportDemo.java`**

```java
import java.util.StringJoiner;

public class StringJoinerReportDemo {
    public static void main(String[] args) {
        StringJoiner basics = new StringJoiner(", ")
                .add("String")
                .add("StringBuilder");

        StringJoiner collections = new StringJoiner(", ")
                .add("List")
                .add("Map");

        StringJoiner result = new StringJoiner(" / ", "Topics: ", ".")
                .setEmptyValue("No topics");

        result.merge(basics);
        result.merge(collections);

        System.out.println("result: " + result);
        System.out.println("basics: " + basics);
        System.out.println("collections: " + collections);

        StringJoiner empty = new StringJoiner(", ")
                .setEmptyValue("Nothing here");

        StringJoiner otherReport = new StringJoiner(" / ", "Topics: ", ".")
                .setEmptyValue("No topics");
        otherReport.merge(empty);

        System.out.println("empty report: " + otherReport);
    }
}
```

```bash
javac StringJoinerReportDemo.java
java StringJoinerReportDemo
```

**Stdout:**

```text
result: Topics: String, StringBuilder / List, Map.
basics: String, StringBuilder
collections: List, Map
empty report: No topics
```

Dấu `, ` nằm **bên trong** mỗi nhóm và ` / ` ngăn **các nhóm** trong báo cáo. Prefix `"Topics: "` và suffix `"."` là của joiner `result`, không phải của hai joiner nguồn. `empty` có `setEmptyValue("Nothing here")` nhưng chưa có phần tử, nên merge không đưa `"Nothing here"` vào báo cáo và báo cáo vẫn dùng emptyValue riêng là `"No topics"`.

Ví dụ cho thấy `merge` có thể dùng để ghép những **đoạn đã nối**, nhưng không thay cho mô hình dữ liệu phân nhóm khi bạn còn cần truy vấn lại từng phần tử riêng lẻ.

## 8. Các trường hợp biên quan trọng khác

### 8.1. Delimiter, prefix, suffix và emptyValue có thể là chuỗi rỗng

`new StringJoiner("")` là hợp lệ: thêm `"A"` rồi `"B"` tạo `"AB"`, không có ký tự phân cách. `new StringJoiner(",", "", "")` cũng hợp lệ. `setEmptyValue("")` là hợp lệ và thường được dùng khi giao diện muốn không hiển thị gì với danh sách trống.

Điểm khác biệt là **chuỗi rỗng `""`** và **`null`**: constructor bắt buộc delimiter/prefix/suffix khác null; `setEmptyValue(null)` ném `NullPointerException`, trong khi `add(null)` thêm chữ `"null"`. `merge(null)` cũng không hợp lệ.

### 8.2. Phần tử có sẵn delimiter không được escape

Nếu delimiter là `", "` và phần tử là `"An, Binh"`, joiner coi `"An, Binh"` là **một phần tử**, không phân tách hay escape nội dung đó:

```java
StringJoiner j = new StringJoiner(", ", "[", "]");
j.add("An, Binh").add("Chi");
// toString(): "[An, Binh, Chi]"
```

Trong ví dụ, không thể suy ra từ đầu ra rằng joiner có **hai** hay **ba** phần tử. `StringJoiner` **không phải** thư viện tạo CSV chuẩn: nó không tự đặt dấu nháy kép, escape dấu phân cách, dấu nháy hoặc newline. Nếu cần dữ liệu có thể parse ngược không nhập nhằng, hãy có định dạng và bộ escape/parse tương ứng.

### 8.3. Prefix và suffix không nhất thiết là cặp dấu ngoặc

`new StringJoiner(":", "id=", ";")` có thể tạo `"id=A:B;"` sau khi thêm `"A"` và `"B"`. Joiner không kiểm tra ngữ nghĩa của các chuỗi trang trí; bạn tự quyết định xem định dạng có hợp lệ với giao thức hay không.

### 8.4. `toString()` là chuỗi bất biến tại thời điểm gọi

`StringJoiner` mutable. Sau `String snapshot = joiner.toString();`, việc tiếp tục `add(...)` sẽ thay đổi joiner, **không thay đổi** đối tượng `String` snapshot đã nhận. Vì thế nên lưu snapshot nếu muốn ghi log hoặc truyền sang API cần một giá trị cố định; tránh trông chờ `snapshot` cập nhật theo joiner.

### 8.5. `length()` là số code unit UTF-16, không phải số phần tử

`StringJoiner.length()` trả độ dài của chuỗi hiển thị trong trạng thái hiện tại. Nếu joiner còn rỗng và có emptyValue, nó dùng **độ dài emptyValue**. Khi đã có phần tử, nó tính theo prefix, nội dung, delimiters và suffix.

Ví dụ emoji 😊 là một Unicode code point ngoài BMP, dài **hai code unit UTF-16**:

**`StringJoinerUnicodeDemo.java`**

```java
import java.util.StringJoiner;

public class StringJoinerUnicodeDemo {
    public static void main(String[] args) {
        String smile = "\uD83D\uDE0A";
        StringJoiner j = new StringJoiner("|", "(", ")")
                .add(smile)
                .add("A");

        String text = j.toString();
        System.out.println("units: " + j.length());
        System.out.println("code points: "
                + text.codePointCount(0, text.length()));
        System.out.println("contains smile: " + text.contains(smile));
    }
}
```

```bash
javac StringJoinerUnicodeDemo.java
java StringJoinerUnicodeDemo
```

**Stdout:**

```text
units: 6
code points: 5
contains smile: true
```

`(`, `|`, `A`, `)` mỗi ký tự chiếm một code unit, emoji chiếm hai: tổng 6 code unit nhưng 5 code point. `StringJoiner` ghép dãy ký tự, **không tự đếm grapheme cluster** hay chuẩn hóa Unicode.

### 8.6. Chia sẻ joiner giữa nhiều luồng

`StringJoiner` là đối tượng có thể thay đổi, **không cung cấp bảo đảm đồng bộ hóa nội bộ** như một container thread-safe. Nếu nhiều luồng cùng `add`/`merge` trên **cùng instance**, phải quản lý chia sẻ và đồng bộ hóa hoặc cho mỗi luồng dùng joiner riêng rồi kết hợp kết quả theo cơ chế kiểm soát. `merge` cũng không biến nhiều thao tác nghiệp vụ thành một giao dịch đồng bộ.

## 9. Lựa chọn giữa StringJoiner, String.join, Collectors.joining và StringBuilder

Các công cụ này liên quan nhưng khác cấp độ điều khiển:

| Công cụ | Ví dụ | Phù hợp khi |
|---|---|---|
| `StringJoiner` | `new StringJoiner(", ", "[", "]").add("A").add("B")` | Muốn thêm dần, đặt prefix/suffix, merge các kết quả ghép và tùy chỉnh emptyValue |
| `String.join(...)` | `String.join(", ", "A", "B")` | Có sẵn các phần tử, muốn ghép nhanh bằng delimiter |
| `Collectors.joining(...)` | `stream.collect(Collectors.joining(", ", "[", "]"))` | Ghép các `CharSequence` trong pipeline Stream |
| `StringBuilder` | `builder.append(...)` | Cần tự kiểm soát toàn bộ trình tự dựng/chỉnh sửa chuỗi, không chỉ ghép phần tử bằng delimiter |

`String.join` không có API thêm dần `add` hoặc `merge` trên chính một đối tượng `StringJoiner` của bạn; `Collectors.joining` cung cấp kết quả cuối thông qua collector, đặc biệt phù hợp với Stream. Nếu cần một biểu diễn rỗng riêng, `StringJoiner.setEmptyValue` tiện lợi, còn với `String.join` hoặc collector có thể phải xử lý quy tắc rỗng ở tầng bao ngoài.

**Ví dụ `JoiningAlternativesDemo.java`:**

```java
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class JoiningAlternativesDemo {
    public static void main(String[] args) {
        List<String> values = List.of("A", "B", "C");

        StringJoiner joiner = new StringJoiner(";", "<", ">");
        values.forEach(joiner::add);

        String direct = String.join(";", values);
        String collected = values.stream()
                .collect(Collectors.joining(";", "<", ">"));

        System.out.println("joiner: " + joiner);
        System.out.println("String.join: " + direct);
        System.out.println("collector: " + collected);
    }
}
```

```bash
javac JoiningAlternativesDemo.java
java JoiningAlternativesDemo
```

**Stdout:**

```text
joiner: <A;B;C>
String.join: A;B;C
collector: <A;B;C>
```

Chọn công cụ theo yêu cầu thực tế; không nhất thiết bọc một `StringJoiner` trong mọi thao tác nối hai ba phần tử. `StringBuilder` hữu ích nếu cần xen lẫn logic cấu trúc, escape, format và thao tác chỉnh sửa phức tạp hơn.

## 10. Hiệu năng và dữ liệu đầu vào

`StringJoiner` giúp tránh quản lý thủ công dấu phân cách, giảm lỗi logic khi danh sách có 0, 1 hay nhiều phần tử. Nhưng đừng coi nó là giải pháp tự động tối ưu mọi bài toán. Khi lượng dữ liệu rất lớn, hãy đo với workload đại diện; định dạng có thể cần serializer hay writer streaming thay vì gom tất cả thành một chuỗi trong bộ nhớ.

Các lưu ý:

1. **Đừng ghép thủ công bằng `String += item + delimiter` trong vòng lặp dài** khi có thể dùng `StringJoiner` hoặc một builder phù hợp. Ghép nối kiểu này dễ tạo logic xóa delimiter cuối hoặc phụ thuộc tối ưu hóa của JVM.
2. **Chọn chiến lược null rõ ràng** trước khi gọi `add`, đặc biệt khi dữ liệu đến từ CSDL, file, HTTP hoặc người dùng.
3. **Đừng dựa vào thứ tự từ một collection không có thứ tự xác định.** `StringJoiner` giữ thứ tự bạn gọi `add`; nó không tự sắp xếp nguồn.
4. **Nếu merge nhiều nhóm**, delimiter trong các nhóm được giữ nguyên. Cần thống nhất cấu trúc nếu đầu ra dùng trong giao thức máy đọc.
5. **Lưu ý độ dài UTF-16**: giới hạn kích thước byte của tệp hoặc mạng phải đo trên chuỗi đã mã hóa theo charset, không dùng `length()` làm số byte.
6. **Không dùng `setEmptyValue` làm cơ chế reset.** Nếu muốn tạo một bản ghi mới, khởi tạo joiner mới.
7. **Không ghép văn bản không đáng tin vào cú pháp đặc biệt mà không escape.** `StringJoiner` chỉ nối, không chống injection hay bảo đảm định dạng JSON/CSV/SQL hợp lệ.

## 11. Các lỗi thường gặp

| Hiểu nhầm | Hậu quả | Cách sửa |
|---|---|---|
| `add(null)` bỏ qua phần tử | Đầu ra chứa chữ `"null"` | Lọc null hoặc thay bằng nhãn theo hợp đồng |
| `add("")` tương đương chưa add | Delimiter xuất hiện khi thêm phần sau; emptyValue không còn hiệu lực | Phân biệt phần tử rỗng và danh sách rỗng |
| `setEmptyValue("(none)")` vẫn thêm prefix/suffix | Hiển thị khác dự kiến | EmptyValue là toàn bộ kết quả khi chưa add |
| `setEmptyValue` xóa hết phần đã thêm | Nội dung thực tế không đổi | Tạo joiner mới để reset |
| `merge` chép cả prefix/suffix nguồn | Có thể chèn hoặc bỏ vỏ không mong muốn | Dùng `merge` cho nội dung; `add(other.toString())` cho cả vỏ |
| `merge` thay delimiter nguồn bằng delimiter đích | Kết quả có các dấu phân cách khác nhau | Hiểu source body được thêm như một phần tổng hợp |
| Merge joiner rỗng nhưng có emptyValue sẽ ghép emptyValue | Đầu ra không có nhãn nguồn | EmptyValue không phải một phần tử |
| `length()` là số phần tử | Đếm sai, nhất là với prefix/emoji | `length()` là độ dài chuỗi kết quả theo UTF-16 |
| Dấu phân cách bên trong một phần tử tự được escape | CSV/log không parse lại chính xác | Tự escape hoặc dùng serializer thích hợp |
| Dùng `toString()` làm tham chiếu sống tới joiner | Snapshot không tự cập nhật | Gọi lại khi cần giá trị mới |
| Coi StringJoiner là thread-safe | Race khi cùng chỉnh sửa instance | Quản lý quyền sở hữu hoặc đồng bộ ngoài |
| Chuyển đầu vào null cho `setEmptyValue`/`merge` | Gặp `NullPointerException` | Xác thực các đối số cần khác null |

## 12. Tự kiểm tra kiến thức

Hãy đoán kết quả chương trình sau trước khi chạy:

**`StringJoinerSelfCheck.java`**

```java
import java.util.StringJoiner;

public class StringJoinerSelfCheck {
    public static void main(String[] args) {
        StringJoiner left = new StringJoiner("|", "[", "]")
                .setEmptyValue("EMPTY");

        StringJoiner right = new StringJoiner("-", "{", "}")
                .setEmptyValue("NO ITEMS");

        System.out.println(left);
        left.merge(right);
        System.out.println(left);

        right.add("A").add("B");
        left.merge(right);
        System.out.println(left);

        left.add("");
        System.out.println(left);

        left.setEmptyValue("CHANGED");
        System.out.println(left);

        System.out.println(right);
    }
}
```

```bash
javac StringJoinerSelfCheck.java
java StringJoinerSelfCheck
```

**Stdout:**

```text
EMPTY
EMPTY
[A-B]
[A-B|]
[A-B|]
{A-B}
```

Giải thích:

1. `left` chưa từng add nên `toString()` là `"EMPTY"`, bỏ qua vỏ `[` và `]` ở trạng thái rỗng tùy chỉnh.
2. `right` chưa add nên `left.merge(right)` không thêm gì; `left` vẫn rỗng.
3. Sau `right.add("A").add("B")`, nội dung của `right` bên trong vỏ là `"A-B"`. `left.merge(right)` nhập phần đó thành một phần tử, hiển thị `"[A-B]"`.
4. `left.add("")` thêm phần tử thứ hai rỗng, nên thêm delimiter đích `|` vào chuỗi kết quả: `"[A-B|]"`.
5. `setEmptyValue("CHANGED")` không tác động vì `left` đã có phần tử.
6. `right` vẫn hiển thị nội dung `"{A-B}"`; nó không mất các phần tử chỉ vì được merge vào `left`.

**Ghi nhớ:** `add` thêm một phần tử; `merge` nhập **nội dung của joiner khác** (không gồm vỏ nguồn), và `setEmptyValue` chỉ định **chuỗi hiển thị khi chưa có phần tử**. Đặt chính sách rõ cho null, chuỗi rỗng, delimiter trong dữ liệu và danh sách chưa có phần tử sẽ giúp việc ghép nhiều phần bằng `StringJoiner` đúng và dễ bảo trì.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-joining-006">Collectors.joining trong xử lý Stream</a>

<details>
<summary>Xem chi tiết</summary>

# Collectors.joining trong xử lý Stream

`Collectors.joining` là collector dùng ở bước cuối của một `Stream` để **ghép các phần tử dạng văn bản thành một `String`**. Nó đặc biệt hữu ích khi dữ liệu phải đi qua các thao tác `filter`, `map` hoặc `sorted` trước khi kết hợp: thay vì tự duyệt, nối chuỗi và quản lý dấu phân cách, ta mô tả toàn bộ quá trình thành một pipeline.

Bài này tập trung vào việc ghép nhiều phần bằng `Collectors.joining`, cách kiểm soát dấu phân cách và phần bao ngoài, cũng như các tình huống dễ cho kết quả ngoài dự kiến.

## 1. Cách dùng cơ bản

Các phương thức `joining` nằm trong `java.util.stream.Collectors`. Ba overload được dùng nhiều nhất:

| Biểu thức | Kết quả | Ý nghĩa |
| --- | --- | --- |
| `Collectors.joining()` | `"ABC"` | Ghép liên tiếp, không thêm dấu phân cách. |
| `Collectors.joining(", ")` | `"A, B, C"` | Chèn `", "` **giữa** hai phần tử liên tiếp. |
| `Collectors.joining(", ", "[", "]")` | `"[A, B, C]"` | Thêm dấu phân cách, đồng thời bao **toàn bộ kết quả** bằng tiền tố và hậu tố. |

Ví dụ với dữ liệu có thứ tự:

```java
List<String> parts = List.of("A", "B", "C");

String text = parts.stream()
        .collect(Collectors.joining(" - "));
// text = "A - B - C"
```

Đây là **đoạn minh họa**: để chạy độc lập cần đặt nó trong phương thức Java và import `java.util.List`, `java.util.stream.Collectors`. Chương trình hoàn chỉnh có ở mục 4.

Phần tử trong stream cần tương thích với `CharSequence` (thường là `String`). Vì vậy có thể thu thập trực tiếp `Stream<String>` hoặc `Stream<CharSequence>`; với `Stream<Integer>` hay stream đối tượng, cần chuyển thành chuỗi trước khi thu thập.

## 2. Hiểu đúng các tham số

### 2.1. `joining()`: nối sát nhau

`Stream.of("Ja", "va", " 17").collect(Collectors.joining())` tạo `"Java 17"`. Collector không tự thêm khoảng trắng, dấu phẩy hoặc ký tự xuống dòng.

### 2.2. `joining(delimiter)`: dấu ngăn cách ở giữa

Với `joining(" | ")`, dấu `" | "` xuất hiện giữa các phần tử, không tự xuất hiện ở đầu hoặc cuối. Dấu phân cách là **văn bản thông thường**, không phải biểu thức chính quy (regex); chẳng hạn `joining(".")` chèn dấu chấm thật, không có ý nghĩa “mọi ký tự” như dấu chấm trong regex.

Để ghép thành nhiều dòng, dùng `joining("\n")`. Kết quả chứa ký tự xuống dòng giữa các phần tử. Nếu một phần tử vốn đã chứa `\n`, ký tự đó vẫn được giữ; `joining` không tự chuẩn hóa nội dung.

### 2.3. `joining(delimiter, prefix, suffix)`: bao kết quả một lần

`Stream.of("x", "y").collect(Collectors.joining(", ", "(", ")"))` trả về `"(x, y)"`.

- `delimiter` được chèn **giữa** các phần tử.
- `prefix` được chèn **một lần trước toàn bộ** chuỗi.
- `suffix` được chèn **một lần sau toàn bộ** chuỗi.

Vì vậy `joining(", ", "[", "]")` tạo dạng danh sách `[a, b]`; nó không tạo `[a], [b]`. Nếu cần định dạng từng phần tử, hãy định dạng bằng `map` trước khi `collect`.

## 3. Cơ chế trong Stream pipeline

Hình dung pipeline sau:

```java
String result = Stream.of("  Mai ", "", " Nam ", "  Lan ")
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.joining(" / "));
// result = "Mai / Nam / Lan"
```

Diễn biến logic:

1. `Stream.of(...)` cung cấp các phần tử nguồn theo thứ tự đã khai báo.
2. `map(String::trim)` tạo dạng đã loại khoảng trắng hai đầu của **từng** chuỗi.
3. `filter(s -> !s.isEmpty())` bỏ phần tử rỗng sau khi trim.
4. `collect(Collectors.joining(" / "))` là **terminal operation**: stream được tiêu thụ, các chuỗi còn lại được ghép thành một kết quả.

`map` và `filter` là intermediate operations; chúng chỉ thực sự được thực thi khi terminal operation được gọi. `joining` tự xử lý việc chèn delimiter và tích lũy thành một giá trị cuối, nên không cần nối thủ công bằng `reduce((a, b) -> a + ", " + b)`.

Sau khi `collect` chạy, stream đó không được dùng lại cho một terminal operation khác; muốn tính kết quả khác thì tạo stream mới từ nguồn. Danh sách nguồn không bị thay đổi bởi pipeline trên.

## 4. Chương trình Java 17+ chạy độc lập

Lưu đúng tên tệp `JoiningDemo.java` (sử dụng UTF-8):

```java
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JoiningDemo {
    record Member(String team, String name) {}

    public static void main(String[] args) {
        List<String> fruits = List.of("táo", "cam", "lê");

        System.out.println(fruits.stream()
                .collect(Collectors.joining()));
        System.out.println(fruits.stream()
                .collect(Collectors.joining(" | ")));
        System.out.println(fruits.stream()
                .collect(Collectors.joining(", ", "[", "]")));

        System.out.println(Stream.<String>empty()
                .collect(Collectors.joining(", ", "[", "]")));
        System.out.println(Stream.of("", "")
                .collect(Collectors.joining(",")));

        List<String> rawNames =
                Arrays.asList("  An  ", null, "", " Bình ", "  Chi ");

        String cleanNames = rawNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" / "));
        System.out.println(cleanNames);

        String numbers = Stream.of(10, 20, 30)
                .map(i -> Integer.toString(i))
                .collect(Collectors.joining(" + ", "(", ")"));
        System.out.println(numbers);

        List<Member> members = List.of(
                new Member("Backend", "An"),
                new Member("Frontend", "Bình"),
                new Member("Backend", "Chi")
        );

        Map<String, String> namesByTeam = members.stream()
                .collect(Collectors.groupingBy(
                        Member::team,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                Member::name,
                                Collectors.joining(", ")
                        )
                ));

        namesByTeam.forEach((team, names) ->
                System.out.println(team + ": " + names));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 JoiningDemo.java
java JoiningDemo
```

Kết quả chuẩn (dòng thứ năm chỉ có **một dấu phẩy**):

```text
táocamlê
táo | cam | lê
[táo, cam, lê]
[]
,
An / Bình / Chi
(10 + 20 + 30)
Backend: An, Chi
Frontend: Bình
```

**Giải thích các trường hợp đáng chú ý:**

- Ba lệnh đầu khác nhau duy nhất ở overload của `joining`: nối sát nhau; thêm dấu ngăn cách; hoặc thêm cả cặp ngoặc vuông.
- Stream rỗng với `joining(", ", "[", "]")` vẫn trả về `"[]"`. Các dấu bao không bị bỏ đi chỉ vì không có phần tử.
- Stream có **hai chuỗi rỗng** không phải stream rỗng: giữa hai phần tử vẫn có một delimiter `","`.
- `Objects::nonNull` được đặt **trước** `String::trim` để không gọi phương thức trên tham chiếu `null`. Sau đó `filter` bỏ chuỗi rỗng.
- Các giá trị `Integer` được chuyển thành `String` bằng `map` trước khi đưa vào collector.
- `Collectors.mapping(Member::name, Collectors.joining(", "))` dùng `joining` như **downstream collector** của `groupingBy`. Từng nhóm có một chuỗi tên riêng; `LinkedHashMap` giữ thứ tự xuất hiện đầu tiên của các nhóm trong ví dụ tuần tự này.

## 5. Các trường hợp biên cần biết

| Đầu vào hoặc thao tác | Kết quả / hành vi cần nhớ |
| --- | --- |
| `Stream.<String>empty().collect(joining())` | Chuỗi rỗng `""`. |
| `Stream.<String>empty().collect(joining(",", "{", "}"))` | `"{}"`: prefix và suffix vẫn được thêm. |
| `Stream.of("a").collect(joining(","))` | `"a"`: không có delimiter vì chỉ một phần tử. |
| `Stream.of("", "").collect(joining("-"))` | `"-"`: hai phần tử rỗng có một delimiter ở giữa. |
| `Stream.of("a", "b").collect(joining(""))` | `"ab"`: delimiter rỗng tương tự nối sát. |
| `Stream.of("a", null, "b").collect(joining(","))` | `"a,null,b"`: `null` được biểu diễn bằng văn bản `"null"`. |
| `Stream.of("a", "b").collect(joining(",", "[", "]"))` | `"[a,b]"`, không có dấu phẩy ở cuối. |

Các biểu thức trong bảng dùng `Collectors.joining` theo dạng viết rút gọn `joining` để dễ nhìn. Khi viết mã thực tế, có thể gọi đầy đủ `Collectors.joining(...)` hoặc thêm `import static java.util.stream.Collectors.joining;`.

### 5.1. Xử lý `null` theo ý nghĩa nghiệp vụ

`Collectors.joining` không loại bỏ phần tử `null`. Khi phần tử null đi vào collector, văn bản `"null"` có thể xuất hiện trong kết quả. Chọn cách xử lý **trước** khi joining:

```java
// Bỏ giá trị null:
String a = Stream.of("An", null, "Bình")
        .filter(Objects::nonNull)
        .collect(Collectors.joining(", "));
// "An, Bình"

// Thay giá trị null bằng nhãn chủ động:
String b = Stream.of("An", null, "Bình")
        .map(s -> s == null ? "(chưa có)" : s)
        .collect(Collectors.joining(", "));
// "An, (chưa có), Bình"
```

Đừng dùng `map(String::trim)` hoặc `map(Object::toString)` khi nguồn có thể có `null` mà chưa lọc hay xử lý null.

### 5.2. Phần tử rỗng, khoảng trắng và delimiter

`joining` không tự cắt khoảng trắng, loại chuỗi rỗng, bỏ trùng hay sắp xếp. Ví dụ dữ liệu `" An "` và `"Bình "` với `joining(", ")` vẫn giữ nguyên khoảng trắng trong phần tử. Khi muốn chuẩn hóa, thứ tự thường là **lọc null → chuẩn hóa bằng map → lọc rỗng → ghép**.

Nếu một phần tử có sẵn ký tự phân cách (ví dụ tên `"An, Bình"`), `joining(",")` vẫn giữ nguyên dấu phẩy trong tên. Nó không escape hay quote cho CSV. Khi xuất CSV/TSV hoặc định dạng yêu cầu escape, cần xử lý quy tắc định dạng riêng cho **từng giá trị** trước khi ghép; không thể mặc định đầu ra `joining(",")` là CSV hợp lệ.

### 5.3. Thứ tự phần tử

`joining` nối theo **encounter order**, nếu stream có thứ tự xác định. `List.stream()` và `Stream.of(...)` là những nguồn thường giữ thứ tự này. Nếu bắt đầu từ một nguồn không đảm bảo thứ tự (ví dụ `HashSet`), hoặc chủ động dùng `unordered()`, đừng giả định chuỗi được ghép theo thứ tự cụ thể.

Khi phải có thứ tự rõ ràng, hãy sử dụng nguồn có thứ tự hoặc gọi `sorted()` / `sorted(comparator)` thích hợp trước `collect`. Với stream song song từ nguồn có encounter order, `joining` vẫn thu kết quả theo encounter order, nhưng thường không mang lại lợi ích tốc độ trên tập dữ liệu nhỏ; không cần tự thay thế collector bằng một `StringBuilder` dùng chung giữa các thread.

## 6. Ghép dữ liệu sau `map` và trong collector khác

`joining` nhận những phần tử đã trở thành văn bản. Nhờ đó ta có thể kiểm soát cách hiển thị **từng** đối tượng trước khi thêm delimiter:

```java
record Product(String code, int quantity) {}

List<Product> products = List.of(
        new Product("P01", 2),
        new Product("P02", 5)
);

String summary = products.stream()
        .map(p -> p.code() + " x" + p.quantity())
        .collect(Collectors.joining("; ", "Đơn hàng: ", "."));
// "Đơn hàng: P01 x2; P02 x5."
```

Đây là đoạn minh họa đặt trong ngữ cảnh có `List` và `Collectors` đã import. `map` tạo một chuỗi biểu diễn cho mỗi `Product`; prefix `"Đơn hàng: "` và suffix `"."` bao cả chuỗi kết quả, không áp dụng riêng cho từng sản phẩm.

Trong `groupingBy`, collector phía sau có thể là `Collectors.mapping(..., Collectors.joining(...))`, như chương trình ở mục 4. Ý tưởng là: **gom nhóm đối tượng → lấy thuộc tính cần hiển thị trong mỗi nhóm → nối thuộc tính trong nhóm**. Điều này khác với gọi `joining` một lần trên toàn bộ stream, vốn chỉ tạo một `String` chung.

## 7. Chọn giữa `Collectors.joining` và cách ghép khác

| Công cụ | Dùng khi nào |
| --- | --- |
| `stream.collect(Collectors.joining(...))` | Đã có pipeline `Stream` với `filter`/`map`/`sorted`; cần một chuỗi tổng hợp cuối. |
| `String.join(delimiter, elements)` | Đã có các chuỗi trong `Iterable` hoặc các đối số chuỗi; không cần pipeline stream. |
| `StringJoiner` | Muốn thêm phần tử từng bước thủ công, nhưng vẫn cần delimiter, prefix và suffix. |
| `StringBuilder` | Có luồng xử lý hoặc bố cục nối chuỗi tùy biến, nhiều điều kiện chèn nội dung, hoặc cần điều khiển từng thao tác append. |

Cả `String.join` và `Collectors.joining` đều hỗ trợ delimiter, nhưng `String.join` không nhận sẵn chuỗi thao tác `filter`/`map` trong một terminal collector. So với tự `reduce` chuỗi bằng phép cộng, `Collectors.joining` diễn đạt trực tiếp mục tiêu ghép văn bản và sử dụng bộ tích lũy phù hợp.

Kết quả của `joining` luôn là một `String` mới về mặt giá trị logic; không sửa các `String` nguồn. Việc thu toàn bộ stream thành một chuỗi cần giữ nội dung kết quả trong bộ nhớ. Nếu dữ liệu khổng lồ và yêu cầu ghi liên tục ra tệp/socket, cân nhắc ghi tuần tự vào `Writer`/`BufferedWriter` thay vì tích lũy toàn bộ văn bản vào một `String`.

## 8. Các lỗi thường gặp và cách sửa

**Lỗi 1 — Ghép stream số nguyên trực tiếp**

```java
// Không biên dịch: joining cần phần tử phù hợp CharSequence.
// Stream.of(1, 2, 3).collect(Collectors.joining(", "));

// Đúng:
String result = Stream.of(1, 2, 3)
        .map(String::valueOf)
        .collect(Collectors.joining(", "));
// "1, 2, 3"
```

**Lỗi 2 — Mong prefix/suffix bao từng phần tử**

`joining(", ", "[", "]")` ghép thành `"[a, b]"`. Nếu muốn `"[a], [b]"`, hãy dùng:

```java
String result = Stream.of("a", "b")
        .map(s -> "[" + s + "]")
        .collect(Collectors.joining(", "));
// "[a], [b]"
```

**Lỗi 3 — Có dấu phân cách dư vì dữ liệu đầu vào rỗng**

Với `Stream.of("a", "", "b")`, `joining(",")` cho `"a,,b"`, bởi phần tử rỗng vẫn được tính. Lọc bằng `filter(s -> !s.isEmpty())` nếu nghiệp vụ muốn bỏ nó; với chuỗi toàn khoảng trắng, có thể cần `trim` hoặc `strip` trước khi lọc.

**Lỗi 4 — Kết quả thay đổi thứ tự giữa các lần chạy**

Kiểm tra thứ tự của nguồn; một `HashSet` không bảo đảm encounter order. Nếu cần kết quả lặp lại có thứ tự, dùng `List` hoặc sắp xếp rõ ràng trước khi joining.

**Lỗi 5 — Xem delimiter là cú pháp định dạng dữ liệu**

`joining` chỉ chèn văn bản, không tự escape dấu ngoặc kép, dấu phẩy, xuống dòng trong một giá trị hay chống SQL injection. Khi tạo CSV, JSON, SQL hoặc HTML, dùng cách mã hóa/thư viện phù hợp với định dạng đầu ra; đặc biệt không dựng câu lệnh SQL chứa dữ liệu người dùng bằng `joining`.

## 9. Tự kiểm tra hiểu bài

Giả sử:

```java
List<String> words = List.of(" java ", "", "stream", " java ");
String output = words.stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .distinct()
        .collect(Collectors.joining(" + ", "<", ">"));
```

`output` bằng `"<java + stream>"`: `trim` loại khoảng trắng ngoài, `filter` bỏ chuỗi rỗng, `distinct` giữ lần xuất hiện đầu tiên của mỗi chuỗi theo encounter order, sau đó `joining` chèn dấu `" + "` và bao toàn bộ bằng `<`, `>`.

Khi đọc một pipeline `Collectors.joining`, hãy lần lượt xác định **các phần tử còn lại sau thao tác trung gian**, **thứ tự của chúng**, **delimiter**, rồi **prefix/suffix**. Bốn yếu tố này quyết định phần lớn kết quả ghép chuỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-joining-007">So sánh join, StringJoiner, StringBuilder và phép nối +</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh String.join(), StringJoiner, StringBuilder và phép nối `+` trong Java

## 1. Bốn cách ghép chuỗi giải quyết những nhu cầu khác nhau

Trong Java, `String` là **bất biến (immutable)**: sau khi một chuỗi được tạo, nội dung của nó không thay đổi. Việc ghép chuỗi tạo ra giá trị `String` mới; cách tổ chức thao tác ghép sẽ ảnh hưởng đến mức độ rõ ràng, khả năng kiểm soát dấu phân cách và lượng công việc khi ghép nhiều phần.

- **Phép `+`**: diễn đạt phép nối trực tiếp, rất tiện cho một biểu thức ngắn hoặc một vài thành phần cố định.
- **`String.join()`**: ghép một dãy phần tử bằng **cùng một delimiter**; phù hợp khi đã có mảng hoặc `Iterable` và chỉ cần chuỗi kết quả.
- **`StringJoiner`**: tích lũy các phần tử với delimiter và hỗ trợ **prefix/suffix**, giá trị khi rỗng và `merge()`; phù hợp khi thêm từng phần tử theo thời gian.
- **`StringBuilder`**: bộ đệm ký tự có thể thay đổi, cho phép `append()`, `insert()`, `delete()`, `replace()` và tự xây dựng quy tắc định dạng; phù hợp khi ghép trong vòng lặp hoặc cần kiểm soát chi tiết.

Ví dụ cùng dữ liệu `["Java", "Spring", "SQL"]`:

~~~java
String a = "Java" + " | " + "Spring" + " | " + "SQL";
String b = String.join(" | ", "Java", "Spring", "SQL");

StringJoiner joiner = new StringJoiner(" | ", "[", "]");
joiner.add("Java").add("Spring").add("SQL");
String c = joiner.toString(); // [Java | Spring | SQL]

StringBuilder builder = new StringBuilder();
builder.append("Java").append(" | ").append("Spring").append(" | ").append("SQL");
String d = builder.toString();
~~~

`a`, `b` và `d` có cùng nội dung `Java | Spring | SQL`. `c` khác vì được yêu cầu thêm ngoặc vuông. Các cách này không phải bốn kiểu dữ liệu tương đương: `+` là toán tử; `String.join()` trả về `String`; `StringJoiner` và `StringBuilder` là các đối tượng giữ trạng thái trong quá trình tạo kết quả.

## 2. Phép nối `+`: rõ ràng cho biểu thức ngắn

Khi **một toán hạng là `String`**, toán tử `+` thực hiện nối chuỗi. Các toán hạng còn lại được chuyển thành biểu diễn chuỗi theo quy tắc chuyển đổi chuỗi của Java. Với các hằng chuỗi, trình biên dịch có thể gộp ngay khi biên dịch (constant folding):

~~~java
String fixed = "Hello" + " " + "Java"; // Hằng "Hello Java"
String name = "Lan";
String greeting = "Hello, " + name + "!"; // Ghép lúc chạy
~~~

Cần phân biệt phép cộng số và phép nối chuỗi, vì biểu thức được đánh giá từ trái sang phải:

~~~java
System.out.println(1 + 2 + " người"); // 3 người
System.out.println("Số người: " + 1 + 2); // Số người: 12
System.out.println("Số người: " + (1 + 2)); // Số người: 3
~~~

**Không kết luận rằng mỗi dấu `+` luôn tạo một `StringBuilder` riêng hoặc luôn chậm.** Trình biên dịch/JVM tối ưu phép ghép một biểu thức; từ Java 9, các biểu thức nối chuỗi thông thường thường dùng cơ chế `invokedynamic` với `StringConcatFactory`. Chi tiết thực thi phụ thuộc trình biên dịch và JVM. Vì vậy, viết `"Xin chào " + name` là hợp lý và dễ đọc.

Điểm cần tránh là **cập nhật chuỗi đang lớn lên trong vòng lặp**:

~~~java
String result = "";
for (String item : items) {
    result += item;
}
~~~

Mỗi lần cập nhật về mặt ngữ nghĩa cần một giá trị `String` mới để lưu kết quả dài hơn; việc lặp lại sao chép phần đã ghép có thể gây nhiều cấp phát và tổng công việc gần bậc hai khi số lượng/độ dài phần tử tăng. Với khối lượng đủ lớn, `StringBuilder` phù hợp hơn. Đây là hướng dẫn về mẫu sử dụng, không phải khẳng định mọi `+` đều kém hiệu năng trong mọi JVM.

Ngoài ra, Java ghép giá trị `null` vào chuỗi thành chữ `"null"` (ví dụ `"x=" + (Object) null` thành `x=null`). Nếu muốn bỏ qua hoặc thay giá trị vắng mặt, hãy xử lý `null` trước.

## 3. `String.join()`: ghép một tập phần tử với delimiter chung

Các overload phổ biến:

~~~java
String.join(CharSequence delimiter, CharSequence... elements)
String.join(CharSequence delimiter, Iterable<? extends CharSequence> elements)
~~~

Ví dụ:

~~~java
String csvLike = String.join(",", "A", "B", "C");
// A,B,C

List<String> paths = List.of("users", "42", "orders");
String path = String.join("/", paths);
// users/42/orders

String empty = String.join(", ", List.<String>of());
// "" (chuỗi rỗng)
~~~

`String.join()` **không tự thêm delimiter ở đầu hoặc cuối** và **không tự lọc phần tử rỗng hay null**. Delimiter chỉ được đặt *giữa* các phần tử:

~~~java
System.out.println(String.join("-", "A", "", "C"));    // A--C
System.out.println(String.join(",", "A", null, "C")); // A,null,C
~~~

Những quy tắc quan trọng:

- Delimiter `null` gây `NullPointerException`; đối số mảng/`Iterable` chứa danh sách phần tử cũng phải là tham chiếu hợp lệ.
- Một **phần tử** `null` được biểu diễn bằng chuỗi `"null"`, chứ không tự biến mất.
- Một dãy có 0 phần tử cho kết quả `""`. Một dãy có một phần tử cho đúng biểu diễn phần tử đó, không có delimiter.
- Phương thức trả về một `String` mới; không làm thay đổi mảng/danh sách đầu vào.
- Tên `csvLike` ở ví dụ chỉ mô tả dấu phẩy: đây **không phải bộ ghi CSV chuẩn**, vì `String.join()` không quote/escape dấu phẩy, dấu nháy hay xuống dòng nằm trong dữ liệu.

Nếu cần loại bỏ chuỗi trắng/null trước khi nối, hãy chuẩn hóa đầu vào. Ví dụ với Stream:

~~~java
List<String> raw = Arrays.asList("Java", null, " ", "Spring");
String result = raw.stream()
        .filter(s -> s != null && !s.isBlank())
        .collect(Collectors.joining(", "));
// Java, Spring
~~~

`Collectors.joining()` thích hợp khi dữ liệu đã đi qua chuỗi thao tác `Stream` (lọc, ánh xạ, sắp xếp). Còn với một mảng hoặc `List` đã sẵn sàng, `String.join()` thường là lựa chọn ít mã nhất.

## 4. `StringJoiner`: thêm từng phần tử, tự quản lý dấu phân cách

`StringJoiner` thuộc `java.util`. Có hai constructor:

~~~java
new StringJoiner(delimiter)
new StringJoiner(delimiter, prefix, suffix)
~~~

Ví dụ:

~~~java
StringJoiner j = new StringJoiner(", ", "[", "]");
j.add("red");
j.add("green");
j.add("blue");
System.out.println(j); // [red, green, blue]
~~~

`StringJoiner` thêm delimiter **giữa các lần `add()`**; người viết không phải kiểm tra xem đây có phải phần tử đầu tiên. `add()` trả về chính joiner để có thể gọi nối tiếp. `toString()` tạo ra chuỗi kết quả ở trạng thái hiện tại; các lần `add()` tiếp theo vẫn làm thay đổi joiner.

### 4.1. Khi chưa có phần tử và `setEmptyValue()`

Với prefix và suffix, khi chưa `add()`, biểu diễn mặc định là `prefix + suffix`. Có thể thay bằng `setEmptyValue()`:

~~~java
StringJoiner j = new StringJoiner(", ", "[", "]");
System.out.println(j); // []

j.setEmptyValue("(không có phần tử)");
System.out.println(j); // (không có phần tử)

j.add("A");
System.out.println(j); // [A]
~~~

`setEmptyValue()` chỉ ảnh hưởng **trạng thái chưa được thêm phần tử**. Sau khi `add()`, kể cả `add("")`, joiner không còn được xem là chưa thêm phần tử.

### 4.2. `merge()` khi kết hợp các nhóm

`merge(other)` thêm nội dung các phần tử của `other` như một nhóm, dùng delimiter của joiner đích ở ranh giới. Prefix/suffix của `other` không được chèn nguyên vào kết quả:

~~~java
StringJoiner left = new StringJoiner(" | ", "<", ">");
left.add("A").add("B");

StringJoiner right = new StringJoiner(", ", "(", ")");
right.add("C").add("D");

left.merge(right);
System.out.println(left); // <A | B | C, D>
System.out.println(right); // (C, D)
~~~

Trong phần nội dung nhóm nguồn, dấu `", "` được giữ nguyên; vì vậy kết quả có dấu cách sau dấu phẩy. `merge()` không xóa dữ liệu của `right`; joiner nguồn vẫn sử dụng được.

Khi sử dụng `merge()`, cần hiểu delimiter bên trong nhóm nguồn và delimiter phân nhóm của đích có thể khác nhau. Đừng giả định merge sẽ đổi mọi dấu phân cách của nguồn thành dấu phân cách đích.

### 4.3. Những giới hạn cần nhớ

- `StringJoiner` tập trung vào `add()`, delimiter, prefix/suffix, empty value và merge; nó **không phải trình sửa ký tự tùy ý** như `StringBuilder`.
- Gọi `add(null)` thêm chữ `"null"`; `null` không tự bị loại bỏ.
- `length()` trả về độ dài biểu diễn hiện tại (bao gồm prefix/suffix nếu có), không phải số phần tử đã thêm.
- Nếu bài toán chỉ là `String.join(", ", list)`, tạo joiner thủ công có thể khiến mã dài thêm không cần thiết.

## 5. `StringBuilder`: kiểm soát từng thao tác tạo chuỗi

`StringBuilder` thuộc `java.lang`, chứa một dãy ký tự có thể thay đổi. `append()` có nhiều overload cho chuỗi, số, boolean, ký tự, mảng ký tự và đối tượng. Nó không tự biết cần đặt delimiter ở đâu:

~~~java
List<String> names = List.of("An", "Bình", "Chi");
StringBuilder sb = new StringBuilder();

for (String name : names) {
    if (sb.length() > 0) {
        sb.append(", ");
    }
    sb.append(name);
}

String output = sb.toString(); // An, Bình, Chi
~~~

Mẫu trên đúng khi không có phần tử rỗng. Nếu phần tử có thể là `""`, dùng chỉ số hoặc biến `first` sẽ an toàn hơn cho mục đích đếm phần tử, vì `sb.length() == 0` không đồng nghĩa “chưa xử lý phần tử nào”:

~~~java
StringBuilder sb = new StringBuilder();
boolean first = true;

for (String item : List.of("", "B")) {
    if (!first) {
        sb.append("|");
    }
    sb.append(item);
    first = false;
}
System.out.println(sb); // |B
~~~

Trường hợp này `String.join("|", List.of("", "B"))` cũng trả `"|B"` và ngắn hơn; mẫu `StringBuilder` hữu ích khi mỗi phần tử còn cần định dạng hoặc thao tác bổ sung.

Các thao tác bổ sung mà `String.join()`/`StringJoiner` không trực tiếp cung cấp:

~~~java
StringBuilder sb = new StringBuilder("abc");
sb.append("de");          // abcde
sb.insert(3, "-");        // abc-de
sb.replace(0, 3, "XYZ");  // XYZ-de
sb.deleteCharAt(3);       // XYZde
sb.reverse();             // edZYX
String result = sb.toString();
~~~

Phân biệt:

- `length()` là số ký tự hiện có.
- `capacity()` là dung lượng bộ đệm hiện được cấp để lưu ký tự; thường lớn hơn hoặc bằng `length()`, và có thể tự tăng.
- `new StringBuilder(estimatedCapacity)` có thể giảm lần mở rộng bộ đệm nếu có ước lượng đáng tin. Đó là tối ưu tùy tình huống, không bắt buộc.
- `toString()` tạo `String` kết quả; nên gọi khi cần kết quả cuối thay vì lặp lại trong từng vòng ghép.
- `append((String) null)` thêm chữ `"null"`; nếu muốn bỏ qua `null`, phải kiểm tra trước.

`StringBuilder` **không đồng bộ hóa các thao tác để dùng chung một instance cho nhiều luồng**. `StringJoiner` cũng không nên được dùng chung rồi cùng sửa từ nhiều luồng nếu không có phối hợp an toàn. `StringBuffer` là lựa chọn có các phương thức được đồng bộ hóa, nhưng điều đó không tự bảo đảm toàn bộ quy trình ghép nhiều bước là một thao tác nguyên tử; nhiều trường hợp tốt hơn là mỗi luồng dùng builder riêng.

## 6. Ví dụ hoàn chỉnh Java 17+

Lưu chương trình dưới tên **`StringJoiningDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class StringJoiningDemo {
    public static void main(String[] args) {
        List<String> tags = List.of("Java", "Spring", "SQL");

        String viaPlus = tags.get(0) + " | "
                + tags.get(1) + " | " + tags.get(2);

        String viaJoin = String.join(" | ", tags);

        StringJoiner joiner = new StringJoiner(" | ", "[", "]");
        for (String tag : tags) {
            joiner.add(tag);
        }
        String viaJoiner = joiner.toString();

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(tags.get(i));
        }
        String viaBuilder = builder.toString();

        System.out.println("plus   : " + viaPlus);
        System.out.println("join   : " + viaJoin);
        System.out.println("joiner : " + viaJoiner);
        System.out.println("builder: " + viaBuilder);

        StringJoiner empty = new StringJoiner(", ", "[", "]");
        empty.setEmptyValue("(trống)");
        System.out.println("empty  : " + empty);

        List<String> raw = Arrays.asList("A", null, "", "B");
        String filtered = raw.stream()
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining(", "));
        System.out.println("filter : " + filtered);
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac StringJoiningDemo.java
java StringJoiningDemo
~~~

Đầu ra:

~~~text
plus   : Java | Spring | SQL
join   : Java | Spring | SQL
joiner : [Java | Spring | SQL]
builder: Java | Spring | SQL
empty  : (trống)
filter : A, B
~~~

Giải thích:

1. `+` nối ba phần tử biết trước vị trí; mã sẽ kém linh hoạt nếu danh sách thay đổi độ dài.
2. `String.join()` nhận trực tiếp `List`, tự chèn `" | "` giữa các mục và không thêm dấu ở hai đầu.
3. `StringJoiner` nhận từng phần tử, đồng thời bao kết quả bằng `[` và `]`.
4. `StringBuilder` chèn delimiter khi `i > 0`, rồi chuyển bộ đệm thành `String`.
5. `setEmptyValue()` xử lý joiner chưa có mục; ví dụ Stream chủ động loại bỏ null và chuỗi rỗng trước khi ghép.

## 7. So sánh theo yêu cầu sử dụng

| Tiêu chí | Toán tử `+` | `String.join()` | `StringJoiner` | `StringBuilder` |
|---|---|---|---|---|
| Loại API | Toán tử biểu thức | Phương thức tĩnh trả `String` | Đối tượng tích lũy | Bộ đệm ký tự thay đổi |
| Hợp với | Một vài phần đã biết | Mảng/`Iterable` có delimiter chung | Thêm dần, prefix/suffix, merge | Vòng lặp, định dạng phức tạp, sửa ký tự |
| Tự chèn delimiter | Không | Có | Có | Không |
| Prefix/suffix tự động | Không | Không | Có | Phải tự viết |
| Xử lý kết quả rỗng | Tùy biểu thức | `""` khi 0 phần tử | Prefix+suffix hoặc `setEmptyValue()` | `""` nếu builder rỗng |
| Chèn/xóa/sửa ký tự tùy ý | Không | Không | Không | Có |
| Đầu ra cuối | `String` | `String` | `toString()` | `toString()` |

### 7.1. Khía cạnh hiệu năng và bộ nhớ

Giả sử có `n` phần tử với tổng độ dài ký tự `L` và delimiter độ dài `d`. Một thuật toán ghép tốt cần xử lý xấp xỉ `L + (n - 1) × d` ký tự đầu ra (chưa tính prefix/suffix). Do đó:

- **Một lần `String.join()` hoặc dùng `StringJoiner` rồi gọi `toString()`** thường thích hợp cho nhiều phần tử cần dấu phân cách; tránh công việc tự kiểm tra phần tử đầu tiên.
- **Một `StringBuilder` được tái sử dụng xuyên suốt vòng lặp** có thể ghép tuyến tính theo tổng lượng dữ liệu với chi phí mở rộng bộ đệm được phân bổ qua nhiều lần `append()`.
- **Một biểu thức `+` đơn lẻ** có thể được JVM tối ưu tốt; chọn dựa trên độ rõ ràng trước khi tối ưu sớm.
- **`result += piece` trong vòng lặp dài** là dấu hiệu cần xem lại vì liên tục hình thành `String` trung gian. Đừng suy diễn chỉ từ số dấu `+` trong source code.

Các đặc điểm cấp phát, chi phí thực tế và ngưỡng chênh lệch phụ thuộc phiên bản JDK, JVM, JIT, dữ liệu và cách viết mã. Nếu hiệu năng quan trọng, đo với **JMH**, có warm-up và dữ liệu đại diện; tránh kết luận từ phép đo thủ công chỉ chạy một lần hoặc từ việc in log trong vòng đo.

### 7.2. Chọn nhanh qua tình huống

- Tạo thông báo `"Xin chào, " + userName` → **`+`** vì ngắn, trực quan.
- Tạo danh sách `a,b,c` từ một `List<String>` có sẵn → **`String.join(",", list)`**.
- Tạo chuỗi dạng `[a, b, c]` bằng cách `add()` từng mục → **`StringJoiner(", ", "[", "]")`**.
- Tạo báo cáo nhiều dòng với số, điều kiện, đoạn văn và chèn/sửa vị trí → **`StringBuilder`**.
- Ghép dữ liệu sau `filter()`/`map()` của Stream → **`Collectors.joining(delimiter)`** thường gọn nhất.

## 8. Những lỗi và ngộ nhận thường gặp

**1. Ghép hai phần tử bằng delimiter nhưng tự thêm delimiter sau mọi phần tử.**

~~~java
StringBuilder sb = new StringBuilder();
for (String s : List.of("A", "B")) {
    sb.append(s).append(","); // A,B, (dư dấu phẩy)
}
~~~

Nếu chỉ cần nối danh sách: `String.join(",", List.of("A", "B"))` trả `A,B`. Nếu dùng builder, thêm delimiter **trước các phần tử từ thứ hai** hoặc dựa vào chỉ số.

**2. Cho rằng `join()` lọc null/rỗng.** Thực tế `String.join("-", "A", "", "C")` thành `A--C`; `String.join("-", "A", null, "C")` thành `A-null-C`. Hãy quyết định trước liệu muốn giữ, bỏ qua hay thay thế các giá trị này.

**3. Cho rằng `StringJoiner.setEmptyValue()` có hiệu lực ngay cả sau `add("")`.** Một chuỗi rỗng vẫn là một phần tử đã được thêm; joiner không còn ở trạng thái rỗng theo nghĩa API.

**4. Nhầm `StringBuilder.length()` với số phần tử.** Nó đếm ký tự; khi phần tử đầu tiên là chuỗi rỗng, `length() == 0` vẫn đúng. Dùng biến `first` hoặc chỉ số để quản lý dấu phân cách nếu dữ liệu có thể có chuỗi rỗng.

**5. Nhầm `String.join()` với encoder định dạng dữ liệu.** Đầu vào `"Ho, Ten"` chứa dấu phẩy vẫn giữ dấu phẩy đó trong đầu ra. Với CSV, JSON, SQL hay URL, phải dùng formatter/encoder/API phù hợp; riêng SQL cần truy vấn tham số thay vì tự nối input.

**6. Dùng `+` để tích lũy lượng dữ liệu lớn và cho rằng cách đó luôn nhanh như một biểu thức đơn lẻ.** Tối ưu một biểu thức nối không đồng nghĩa JVM loại bỏ được mọi `String` trung gian giữa các lần gán `+=` trong vòng lặp.

**7. Dùng chung một `StringBuilder` mutable giữa nhiều luồng.** Nếu các luồng sửa cùng đối tượng mà không có đồng bộ phù hợp, nội dung có thể sai. Thường nên tạo bộ đệm trong phạm vi một phương thức/tác vụ.

## 9. Ghi nhớ khi áp dụng

Hãy bắt đầu từ **hình dạng đầu vào** và **quy tắc đầu ra**. Với vài phần cố định, dùng `+`. Với một dãy đã có và một delimiter, dùng `String.join()`. Với quá trình thêm dần cần prefix/suffix, dùng `StringJoiner`. Với việc xây dựng chuỗi phức tạp hoặc cập nhật lặp nhiều lần, dùng `StringBuilder`. Sau đó xác định rõ cách xử lý null, chuỗi rỗng, escaping và phạm vi sử dụng giữa các luồng; các yếu tố này ảnh hưởng tính đúng đắn hơn nhiều so với việc chọn API ngắn nhất.


</details>

- [Quay lại đầu trang](#back-to-top)
