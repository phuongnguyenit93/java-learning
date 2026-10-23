<a id="back-to-top"></a>

# Tách chuỗi

## Menu
- [String.split nhận biểu thức chính quy](#string-splitting-001)
- [limit dương, bằng 0 và âm](#string-splitting-002)
- [Giữ hoặc bỏ các phần tử rỗng ở cuối](#string-splitting-003)
- [Dấu phân cách ở đầu, liên tiếp và ở cuối](#string-splitting-004)
- [Regex khớp độ rộng bằng 0 và khác biệt với dấu phân cách có độ rộng](#string-splitting-005)
- [Dùng Pattern.quote khi dấu phân cách là dữ liệu nguyên văn](#string-splitting-006)
- [splitWithDelimiters từ Java 21](#string-splitting-007)
- [Pattern.splitAsStream và xử lý kết quả lười](#string-splitting-008)
- [Vì sao split đơn giản không đủ để phân tích CSV có dấu nháy](#string-splitting-009)

## <a id="string-splitting-001">String.split nhận biểu thức chính quy</a>

<details>
<summary>Xem chi tiết</summary>

# `String.split(regex)`: regex là quy tắc tìm dấu phân cách

Trong Java, `String.split(...)` tìm **những đoạn khớp biểu thức chính quy (regex)** rồi tách chuỗi tại các đoạn đó. Tham số không nhất thiết là một ký tự phân cách nguyên văn. Vì vậy, `"a.b.c".split(".")` **không** cho `["a", "b", "c"]`: trong regex, `.` là wildcard khớp gần như mọi ký tự.

Bài này tập trung vào **cách viết regex** và **hai tầng escape của Java**. Các bài tiếp theo khai thác riêng `limit`, phần tử rỗng, zero-width, `Pattern.quote`, stream và dữ liệu CSV.

## 1. Hợp đồng của `String.split`

Hai overload:

```java
public String[] split(String regex)
public String[] split(String regex, int limit)
```

Cả hai trả `String[]` gồm những đoạn nằm **giữa các lần regex khớp**; đoạn khớp đóng vai trò dấu phân cách không được giữ thành token riêng. Overload một tham số tương đương `split(regex, 0)`: mặc định bỏ toàn bộ phần tử rỗng **ở cuối**. Nếu cần kiểm soát số phần hoặc giữ trường cuối rỗng, dùng overload có `limit`.

```java
String[] colors = "red,green,blue".split(",");
// ["red", "green", "blue"]
```

Dấu phẩy không phải metacharacter của Java regex, nên mẫu `","` cũng là dấu phẩy nguyên văn. Nhưng nhiều ký tự khác mang nghĩa cú pháp:

| Mục tiêu | Đối số trong source Java | Regex nhận được |
| --- | --- | --- |
| Dấu phẩy literal | `","` | `,` |
| Dấu chấm literal | `"\\."` | `\.` |
| Dấu gạch đứng literal | `"\\|"` | `\|` |
| Một hoặc nhiều whitespace | `"\\s+"` | `\s+` |
| Dấu phẩy hoặc chấm phẩy | `"[,;]"` | Character class |
| Một hay nhiều dấu hai chấm | `":+"` | `:+` |
| Dấu phân cách nguyên văn động | `Pattern.quote(delimiter)` | Khớp chính xác chuỗi cấu hình |

Các ký hiệu như `.`, `|`, `+`, `*`, `?`, `[ ]`, `( )`, `\` có vai trò đặc biệt ở những ngữ cảnh regex nhất định. Đừng đưa thẳng delimiter nhận từ bên ngoài vào `split` nếu mục tiêu là khớp nguyên văn.

## 2. Hai tầng escape của Java

Để khớp một **dấu chấm thật**, regex engine cần `\.`. Nhưng dấu `\` cũng là ký tự escape trong string literal Java; để chuỗi Java chứa `\` phải viết `\\`. Vậy mã Java cần `split("\\.")`.

- `split(".")`: Java hợp lệ, regex khớp nhiều ký tự thường, sai mục tiêu dấu chấm.
- `split("\\.")`: Java hợp lệ, regex khớp đúng ký tự `.`.
- `split("\.")`: Java source không hợp lệ vì escape `\.` của literal không tồn tại.
- `split("\\s+")`: Java truyền regex `\s+`, khớp một hoặc nhiều whitespace tùy chế độ regex.

Lưu ý: Java 15+ có **escape chuỗi literal** `\s` mang nghĩa một dấu cách. Vì vậy viết `split("\s+")` trong Java hiện đại sẽ tạo regex ` +` (một hay nhiều dấu cách) thay vì nhóm whitespace `\s+` mà người viết thường mong đợi.

## 3. Chạy ví dụ để kiểm tra regex

Lưu `RegexSplitDemo.java` rồi biên dịch bằng JDK 17+:

```java
import java.util.Arrays;
import java.util.regex.Pattern;

public class RegexSplitDemo {
    public static void main(String[] args) {
        String dots = "a.b.c";
        System.out.println(dots.split(".").length);
        System.out.println(Arrays.toString(dots.split("\\.")));

        String pipes = "red|green|blue";
        System.out.println(Arrays.toString(pipes.split("\\|")));
        System.out.println(Arrays.toString("red   green\tblue".split("\\s+")));
        System.out.println(Arrays.toString("a,b;c".split("[,;]")));

        String delimiter = "|+|";
        String input = "left|+|middle|+|right";
        System.out.println(Arrays.toString(
                input.split(Pattern.quote(delimiter))));
    }
}
```

```bash
javac --release 17 -encoding UTF-8 RegexSplitDemo.java
java RegexSplitDemo
```

```text
0
[a, b, c]
[red, green, blue]
[red, green, blue]
[a, b, c]
[left, middle, right]
```

Dòng đầu là `0` vì regex `.` khớp mọi ký tự trong chuỗi ví dụ và `limit=0` mặc định bỏ tất cả phần rỗng cuối. Đó là ví dụ **mẫu hợp lệ nhưng sai nghĩa dữ liệu**.

Với `"a,,b"`, `split(",")` giữ phần rỗng ở giữa, còn `split(",+")` xem cụm hai dấu phẩy là **một** lần khớp và không tạo cột rỗng ở giữa. Cách nào đúng tùy dữ liệu: nhóm whitespace giữa từ có thể gộp, còn hai dấu phẩy trong dữ liệu cột thường biểu thị một trường rỗng.

## 4. Giới hạn và cách xử lý lỗi

- Regex chưa đóng, ví dụ `"["`, khiến `split` ném `PatternSyntaxException`; không trả mảng rỗng để báo lỗi.
- `"a,b".split(null)` hoặc gọi `split` trên tham chiếu chuỗi nguồn `null` gây `NullPointerException`.
- Regex `\s` mặc định của Java không nhất thiết bao phủ mọi whitespace Unicode. Khi cần phạm vi Unicode rộng, cân nhắc `Pattern.UNICODE_CHARACTER_CLASS` và kiểm thử chính xác tập ký tự.
- Regex quá phức tạp từ đầu vào không tin cậy có thể gây backtracking tốn CPU. Khi delimiter là văn bản nguyên vẹn, `Pattern.quote` giúp tránh diễn giải nhầm thành cú pháp regex.
- `split("")` không phải bộ tách grapheme cluster Unicode; ký tự người dùng nhìn thấy có thể chứa nhiều code point hoặc UTF-16 code unit.
- `String.split(",")` không hiểu dấu nháy của CSV, dù có dùng `limit=-1`. Với CSV thực, cần parser hiểu trạng thái nháy và dòng nhiều dòng.

Khi cùng mẫu được dùng thường xuyên, `Pattern.compile(regex).split(input, limit)` cho phép tái sử dụng `Pattern`. Các quy tắc về `limit` tương tự `String.split`.

## 5. Tự kiểm tra

1. Vì sao `"api.example.org".split(".")` không tách theo dấu chấm? Viết hai cách sửa.
2. Regex nào tách `"one;two,three"` tại `;` hoặc `,`?
3. Vì sao `"a,,b".split(",+")` có ít token hơn `split(",")`?
4. Nếu dấu phân cách từ cấu hình có giá trị `"|+|"`, nên chuẩn bị regex như thế nào?

**Đáp án:** (1) `.` là wildcard; dùng `"\\."` hoặc `Pattern.quote(".")`. (2) `"[,;]"`. (3) `+` gộp các dấu liên tiếp; có thể làm mất trường rỗng. (4) Dùng `Pattern.quote(delimiter)`.

**Ghi nhớ:** Trước khi đếm token, phải biết **chính xác vùng nào trong đầu vào sẽ khớp regex**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-splitting-002">limit dương, bằng 0 và âm</a>

<details>
<summary>Xem chi tiết</summary>

# `String.split(regex, limit)`: dương, bằng 0 và âm

Tham số `limit` trả lời hai câu hỏi: **số phần kết quả được giới hạn ra sao** và **các phần rỗng cuối có bị bỏ hay không**. Ví dụ, dấu `=` đầu tiên có thể ngăn cách key với value chứa thêm nhiều dấu `=`; dữ liệu bảng lại cần bảo toàn các trường rỗng cuối.

## 1. Ba chế độ tách

| `limit` | Quy tắc | Phần rỗng cuối |
| --- | --- | --- |
| Số dương `n` | Áp dụng regex tối đa `n-1` lần, kết quả **không quá `n` phần tử** | Giữ nếu đã tạo ra |
| `0` | Tách tối đa | Bỏ **toàn bộ** phần tử rỗng ở cuối |
| Số âm (thường `-1`) | Tách tối đa | Giữ phần tử rỗng ở cuối |

`split(regex)` đồng nghĩa `split(regex, 0)`. Giá trị âm `-1` và `-5` cùng quy tắc; số âm không có nghĩa tách ngược từ phải sang trái. `limit=1` không thực hiện lần tách nào và trả lại toàn chuỗi trong một phần tử.

Xét `String s = "a:b::";`:

| Lời gọi | Nội dung kết quả | `length` |
| --- | --- | --- |
| `s.split(":", 1)` | `["a:b::"]` | 1 |
| `s.split(":", 2)` | `["a", "b::"]` | 2 |
| `s.split(":", 3)` | `["a", "b", ":"]` | 3 |
| `s.split(":", 4)` | `["a", "b", "", ""]` | 4 |
| `s.split(":", 10)` | `["a", "b", "", ""]` | 4 |
| `s.split(":", 0)` | `["a", "b"]` | 2 |
| `s.split(":", -1)` | `["a", "b", "", ""]` | 4 |

Với `limit=3`, chỉ có hai lần cắt. Phần cuối `":"` là **phần dư chưa bị tách**, không phải một ký tự dấu phân cách bị bỏ quên. `limit=10` chỉ đặt mức trần, không tự chèn thêm sáu phần trống.

## 2. Chương trình Java 17+ kiểm chứng mọi nhánh

Lưu `SplitLimitDemo.java`:

```java
import java.util.Arrays;
import java.util.stream.Collectors;

public class SplitLimitDemo {
    static void show(String label, String[] parts) {
        String visible = Arrays.stream(parts)
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println(label + " len=" + parts.length + " " + visible);
    }

    public static void main(String[] args) {
        String text = "a:b::";
        show("limit=1", text.split(":", 1));
        show("limit=2", text.split(":", 2));
        show("limit=3", text.split(":", 3));
        show("limit=4", text.split(":", 4));
        show("limit=0", text.split(":", 0));
        show("limit=-1", text.split(":", -1));

        show("empty", "".split(":", 0));
        show("delimiter only", ":".split(":", 0));
        show("key/value", "host=a=b".split("=", 2));
    }
}
```

```bash
javac --release 17 -encoding UTF-8 SplitLimitDemo.java
java SplitLimitDemo
```

```text
limit=1 len=1 ["a:b::"]
limit=2 len=2 ["a", "b::"]
limit=3 len=3 ["a", "b", ":"]
limit=4 len=4 ["a", "b", "", ""]
limit=0 len=2 ["a", "b"]
limit=-1 len=4 ["a", "b", "", ""]
empty len=1 [""]
delimiter only len=0 []
key/value len=2 ["host", "a=b"]
```

Hàm `show` cố ý in `length` và bọc token trong dấu nháy. `Arrays.toString` mặc định khó phân biệt mảng không phần tử với mảng chứa một chuỗi rỗng.

Hai trường hợp biên: `"".split(":", 0)` có **một** phần tử `""` vì regex không tìm thấy dấu `:` nào; `":".split(":", 0)` tạo các đoạn đều rỗng và chúng bị xóa hết ở cuối, nên có **0** phần tử.

## 3. Ứng dụng: chỉ tách key/value một lần

`limit=2` thường là lựa chọn đúng khi dấu phân cách chỉ có ý nghĩa ở lần xuất hiện đầu tiên:

```java
String line = "endpoint=https://example.org?q=a=b";
String[] pair = line.split("=", 2);
// ["endpoint", "https://example.org?q=a=b"]
```

Nếu dùng `split("=")`, value bị chia vụn theo những dấu `=` bên trong chính nó. Với `"enabled="`, `split("=", 2)` trả `["enabled", ""]`, còn mặc định chỉ `["enabled"]` vì bỏ trường rỗng cuối. Kiểm tra `pair.length == 2` trước khi truy cập `pair[1]`: đầu vào không có dấu `=` cho mảng chỉ một phần tử.

`limit=2` cũng áp dụng cho cấu hình `"level:WARN:retry-later"` khi dấu `:` đầu tiên phân tách khóa và toàn bộ thông điệp còn lại.

## 4. Giữ phần rỗng cuối và các giới hạn của `limit`

`"A,B,,".split(",")` chỉ cho `["A", "B"]`; `split(",", -1)` trả đủ `["A", "B", "", ""]`. Dù ở `limit=0`, phần rỗng **giữa** `"A,,B"` và phần rỗng **đầu** `",A,B"` vẫn được giữ nếu delimiter khớp độ dài dương. Bài về trường rỗng cuối dùng các trường hợp này để kiểm tra schema và khôi phục dữ liệu.

Cần phân biệt thêm:

- Regex khớp **độ rộng 0** ngay vị trí đầu chuỗi không tạo phần tử rỗng đầu; quy tắc này khác chuyện bỏ rỗng **cuối** theo `limit=0`.
- `limit` không biến regex thành dấu phân cách literal. Dấu chấm cần `"\\."` hoặc `Pattern.quote(".")`.
- `stream.limit(n)` sau `Pattern.splitAsStream` chỉ lấy tối đa `n` token, trong khi `split(input, n)` đưa **toàn phần dư** vào phần tử cuối.
- Không giá trị `limit` nào thay parser CSV có dấu nháy.

## 5. Tự kiểm tra

1. `"user=hello=world".split("=", 2)` giữ dấu `=` thứ hai không?
2. `"x:y::".split(":", 3)` có phần tử thứ ba nào?
3. `"x:y::".split(":", 0)` và `split(":", -1)` khác nhau thế nào?
4. `"a:b".split(":", 10)` có đúng mười phần không?
5. `"a,b".split(":", 0)` có trả mảng rỗng khi không có dấu `:` không?

**Đáp án:** (1) Có, trong `"hello=world"`. (2) `":"`. (3) Mảng hai phần so với mảng bốn phần có hai chuỗi rỗng cuối. (4) Không, hai phần. (5) Không, một phần tử `"a,b"`.

**Ghi nhớ:** `limit>0` là mức trần **số phần**, `0` xóa chuỗi rỗng cuối, còn số âm giữ chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-splitting-003">Giữ hoặc bỏ các phần tử rỗng ở cuối</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ hoặc bỏ trường rỗng cuối khi tách chuỗi

Với chuỗi `"Lan,Java,,"`, hai dấu phẩy cuối có thể thể hiện **hai cột đang rỗng**. Nếu bỏ chúng, bản ghi bốn cột bị biến thành bản ghi hai cột. Quy tắc `limit` đã được học ở bài trước; bài này tập trung vào **tác động với dữ liệu có cấu trúc**, cách kiểm tra số trường và việc phân biệt rỗng với trắng.

## 1. Không được nhầm trường rỗng với trường thiếu

| Đầu vào | `split(",")` | `split(",", -1)` |
| --- | --- | --- |
| `"Lan,Java,,"` | `["Lan", "Java"]` | `["Lan", "Java", "", ""]` |
| `"A,B,"` | `["A", "B"]` | `["A", "B", ""]` |
| `"A,,C,"` | `["A", "", "C"]` | `["A", "", "C", ""]` |
| `",,"` | Mảng độ dài 0 | Ba phần `""` |
| `""` | Một phần `""` | Một phần `""` |

`split(regex)` dùng `limit=0`, nên **mọi** phần rỗng cuối bị loại; `split(regex, -1)` giữ chúng. Những phần rỗng ở đầu và giữa vẫn tồn tại nếu không nằm trong chính đoạn đuôi bị loại.

Khi thử dữ liệu rỗng, in `parts.length` và chỉ số từng trường. `Arrays.toString` không thêm dấu nháy quanh chuỗi rỗng; nhìn `[]` thôi dễ nhầm mảng rỗng với mảng một phần tử rỗng.

## 2. Ví dụ kiểm tra schema bốn cột

Lưu thành `PreserveColumnsDemo.java` (JDK 17+):

```java
public class PreserveColumnsDemo {
    public static void main(String[] args) {
        String input = "Lan,Java,,";
        String[] defaultParts = input.split(",");
        String[] fields = input.split(",", -1);

        System.out.println("default=" + defaultParts.length);
        System.out.println("preserved=" + fields.length);
        for (int i = 0; i < fields.length; i++) {
            System.out.println(i + ": [" + fields[i] + "]");
        }
        if (fields.length != 4) {
            throw new IllegalArgumentException("Expected four columns");
        }

        String name = fields[0];
        String language = fields[1];
        String level = fields[2];
        String note = fields[3];
        System.out.println(name + ": " + language);
        System.out.println(level.isEmpty() && note.isEmpty());

        System.out.println(input.equals(String.join(",", fields)));
        System.out.println(input.equals(String.join(",", defaultParts)));
    }
}
```

```bash
javac --release 17 -encoding UTF-8 PreserveColumnsDemo.java
java PreserveColumnsDemo
```

```text
default=2
preserved=4
0: [Lan]
1: [Java]
2: []
3: []
Lan: Java
true
true
false
```

Với delimiter literal đơn ký tự `","`, `String.join(",", input.split(",", -1))` dựng lại nguyên chuỗi bao gồm các dấu cuối; bản `split(",")` đã làm mất chúng. Không suy rộng tính chất khôi phục nguyên văn cho mọi regex vì regex có thể khớp nhiều kiểu hoặc nhiều ký tự khác nhau.

## 3. `""` khác `" "` và khác trường không hiện diện

`"A, ,"` dùng `split(",", -1)` cho `["A", " ", ""]`. Trường thứ hai chứa **một dấu cách**, còn trường thứ ba có độ dài bằng 0:

```java
String[] fields = "A, ,".split(",", -1);
System.out.println(fields[1].isEmpty()); // false
System.out.println(fields[1].isBlank()); // true
System.out.println(fields[2].isEmpty()); // true
```

`strip()`, `trim()` hoặc `isBlank()` có thể hữu ích khi quy tắc dữ liệu cho phép chuẩn hóa khoảng trắng, nhưng không được áp dụng vô điều kiện vào dữ liệu cần bảo toàn giá trị gốc.

Bản ghi `"A,B"` có hai trường, còn `"A,B,"` có trường thứ ba tồn tại nhưng rỗng. Nếu định dạng yêu cầu ba cột, giữ chúng bằng `-1`, xác minh `parts.length==3`, sau đó mới kiểm tra từng giá trị bắt buộc. `split` không tự xác thực schema.

## 4. Chọn chính sách xử lý

Dùng `-1` khi cần đếm đúng số cột, ánh xạ đối tượng, khôi phục dữ liệu hoặc ghi nhận trường trống cuối. Dùng mặc định nếu định dạng cho phép **cố ý bỏ** phần rỗng cuối. Với dấu phân cách nguyên văn như `|` hay `.`, có thể kết hợp `Pattern.quote(delimiter)` cùng `-1`.

Cả hai cách đều chưa đủ để đọc **CSV có dấu nháy**: dấu phẩy bên trong trường được nháy cần parser CSV. Trường rỗng cuối chỉ là một trong nhiều yêu cầu của parser.

## Tự kiểm tra

1. `"x,y,,".split(",", -1)` có mấy phần và các phần cuối là gì?
2. Vì sao `",,".split(",")` có `length=0` còn `"".split(",")` có `length=1`?
3. Cần bốn cột từ `"id,name,note,"` thì chọn overload nào?
4. `"A, ,"` có hai trường cuối giống nhau không?

**Đáp án:** (1) Bốn phần, hai phần cuối là `""`. (2) Chuỗi toàn delimiter tạo toàn phần rỗng rồi bị xóa; chuỗi rỗng không chứa lần khớp `,` nào nên trả chính nó. (3) `split(",", -1)` và kiểm tra `length==4`. (4) Không, một trường chứa dấu cách, trường còn lại rỗng.

**Ghi nhớ:** **Giữ trường rỗng trước, kiểm tra schema sau.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-splitting-004">Dấu phân cách ở đầu, liên tiếp và ở cuối</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu phân cách ở đầu, liên tiếp và ở cuối

Khi xem dấu phân cách là **ranh giới giữa các trường**, vị trí của nó quyết định có phần tử rỗng hay không. `",a,b"` có phần trống ở đầu; `"a,,b"` có phần trống ở giữa; `"a,b,"` có phần trống cuối nếu chọn giữ lại.

## 1. Quan sát mọi vị trí

| Đầu vào | `split(",")` | `split(",", -1)` |
| --- | --- | --- |
| `",a,b"` | `["", "a", "b"]` | `["", "a", "b"]` |
| `"a,,b"` | `["a", "", "b"]` | `["a", "", "b"]` |
| `"a,b,,"` | `["a", "b"]` | `["a", "b", "", ""]` |
| `",,a,,"` | `["", "", "a"]` | `["", "", "a", "", ""]` |
| `",,"` | Mảng độ dài 0 | Ba phần `""` |
| `""` | Một phần `""` | Một phần `""` |

**Đầu chuỗi:** regex khớp đoạn có độ dài **dương** ngay tại vị trí 0 sinh phần tử rỗng trước nó. Regex có lần khớp **zero-width** ở vị trí 0 không sinh phần rỗng đầu; bài tiếp theo giải thích tường minh với lookahead/anchor.

**Giữa chuỗi:** mỗi dấu phẩy khớp riêng, nên hai dấu liền nhau tạo một khoảng trống `""` ở giữa. Việc xóa phần tử rỗng ở giữa có thể làm lệch cột dữ liệu.

**Cuối chuỗi:** mặc định `limit=0` bỏ mọi phần tử rỗng cuối; `limit=-1` giữ lại. Quy tắc rỗng **ở cuối** khác quy tắc khớp zero-width **ở đầu**.

## 2. Mỗi dấu riêng hay gộp cả cụm?

Để thấy sự khác nhau giữa `","` và `",+"`, lưu `SplitBoundariesDemo.java`:

```java
import java.util.Arrays;
import java.util.stream.Collectors;

public class SplitBoundariesDemo {
    private static void show(String label, String[] parts) {
        String visible = Arrays.stream(parts)
                .map(s -> "'" + s + "'")
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println(label + " n=" + parts.length + " " + visible);
    }

    public static void main(String[] args) {
        String input = ",a,,b,";
        show("individual", input.split(",", -1));
        show("default", input.split(","));
        show("grouped", input.split(",+", -1));
        show("first only", input.split(",", 2));
        show("zero width", "abc".split("(?=a)", -1));
    }
}
```

```bash
javac --release 17 -encoding UTF-8 SplitBoundariesDemo.java
java SplitBoundariesDemo
```

```text
individual n=5 ['', 'a', '', 'b', '']
default n=4 ['', 'a', '', 'b']
grouped n=4 ['', 'a', 'b', '']
first only n=2 ['', 'a,,b,']
zero width n=1 ['abc']
```

`","` xem **mỗi** dấu phẩy là một dấu phân cách; `",+"` xem **cụm dấu phẩy liền nhau** là một lần khớp. Với `",a,,b,"`, gộp cụm làm mất trường rỗng ở giữa. Chỉ dùng `+` nếu nhiều dấu liên tiếp **thực sự mang một ý nghĩa ngăn cách duy nhất**.

Chẳng hạn, `"  red   green  ".split("\\s+")` xem nhiều whitespace liền nhau là một ranh giới. Vì dấu cách đầu tạo lần khớp có độ dài dương tại vị trí đầu, vẫn có thể có chuỗi rỗng đầu; `limit=0` chỉ xóa rỗng cuối. `strip()` trước khi tách phù hợp nếu nghiệp vụ cho phép loại whitespace ngoài cùng, nhưng không phù hợp với định dạng yêu cầu bảo toàn khoảng trắng.

## 3. Khi regex xác định ranh giới khác một dấu đơn

- `"[,;]"` khớp từng dấu phẩy hoặc chấm phẩy; `"[,;]+"` gộp cả cụm gồm hai loại.
- `"::"` khớp chính xác hai dấu hai chấm; `":+"` khớp một hay nhiều dấu hai chấm.
- `Pattern.quote(delimiter)` dùng khi ký tự phân cách, ví dụ `"."` hoặc `"|"`, phải được hiểu nguyên văn.
- Với lookahead `"(?=:)"`, regex chỉ khớp **vị trí** trước dấu `:` mà không tiêu thụ ký tự. Khi ấy `:` vẫn nằm trong token; không thể áp dụng trực tiếp bảng của dấu phẩy tiêu thụ ký tự ở trên.

Một định dạng cột cố định thường cần từng dấu phân cách; một danh sách từ cách bởi nhiều whitespace thường cần gộp cụm. **Đặc tả dữ liệu** quyết định cách chọn regex.

## Tự kiểm tra

1. `",,x".split(",", -1)` có mấy phần rỗng ở đầu?
2. Vì sao `"x,,y".split(",+", -1)` có ít phần hơn `split(",", -1)`?
3. Có nên luôn `strip()` trước `split("\\s+")` không?
4. Dùng `split(",")` rồi loại **mọi** chuỗi rỗng có mô phỏng đúng `limit=0` không?

**Đáp án:** (1) Hai. (2) `,+` gộp hai dấu thành một ranh giới, không sinh trường giữa. (3) Chỉ khi whitespace ngoài cùng không là dữ liệu. (4) Không; `limit=0` chỉ loại rỗng cuối, không loại rỗng đầu/giữa.

**Ghi nhớ:** Hãy xác định **mỗi delimiter có là một ranh giới độc lập** hay **nhiều delimiter hợp thành một ranh giới**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-splitting-005">Regex khớp độ rộng bằng 0 và khác biệt với dấu phân cách có độ rộng</a>

<details>
<summary>Xem chi tiết</summary>

# Regex khớp độ rộng bằng 0 và dấu phân cách có độ rộng trong Java String.split()

## 1. Phân biệt hai kiểu khớp

Trong Java, **String.split(String regex)** tách chuỗi theo **biểu thức chính quy (regex)**. Bản chất của một lần regex khớp quyết định ranh giới và phần tử kết quả.

- **Khớp có độ rộng dương (positive-width match)** tiêu thụ một hoặc nhiều ký tự. Ví dụ, regex ":" khớp và tiêu thụ dấu hai chấm. Ký tự được tiêu thụ không xuất hiện trong các token.
- **Khớp độ rộng bằng 0 (zero-width match)** chỉ khớp một **vị trí giữa các ký tự**, không tiêu thụ ký tự nào. Ví dụ, "(?=:)" khớp ngay *trước* dấu hai chấm; "(?<=:)" khớp ngay *sau* dấu hai chấm. Dấu phân cách vẫn thuộc một token vì không bị tiêu thụ.

Các anchor "^" (đầu chuỗi), "$" (cuối chuỗi), lookahead "(?=...)" và lookbehind "(?<=...)" thường tạo ra khớp độ rộng 0. Đừng nhầm **độ dài biểu thức regex** với **độ rộng của lần khớp**: biểu thức "(?=:)" nhiều ký tự nhưng vẫn tiêu thụ 0 ký tự.

**Quy tắc quan trọng nhất của String.split:** nếu regex khớp **độ rộng 0 ở ngay đầu chuỗi**, lần khớp đó **không tạo phần tử rỗng đầu tiên**. Trái lại, nếu regex khớp **độ rộng dương ở đầu**, nó **tạo phần tử rỗng đầu tiên**. Hai hành vi này khác nhau ngay cả khi sử dụng cùng một giá trị **limit**.

## 2. Cùng chuỗi, khác regex

Xét đầu vào **":a:b"**:

~~~text
Ranh giới:  0   1   2   3   4
Ký tự:       :   a   :   b
~~~

| Regex | Đặc điểm khớp | Các token kết quả |
| --- | --- | --- |
| ":" | Tiêu thụ hai dấu ":" ở chỉ số ký tự 0 và 2 | ["", "a", "b"] |
| "(?=:)" | Lookahead độ rộng 0 trước hai dấu ":" | [":a", ":b"] |
| "(?<=:)" | Lookbehind độ rộng 0 sau hai dấu ":" | [":", "a:", "b"] |

**Với ":"**, dấu hai chấm tại chỉ số 0 bị tiêu thụ; đoạn trước nó là chuỗi rỗng, vì vậy token đầu là "". Dấu phân cách không còn trong kết quả.

**Với "(?=:)"**, regex chỉ kiểm tra ký tự tiếp theo có phải ":" hay không. Nó khớp tại ranh giới 0 và 2. Lần khớp độ rộng 0 tại ranh giới 0 không tạo token rỗng; ranh giới 2 tách ":a" và ":b". Hai dấu ":" vẫn ở đầu các token vì không bị tiêu thụ.

**Với "(?<=:)"**, regex khớp các ranh giới 1 và 3 ngay sau dấu ":". Vì vậy dấu hai chấm đứng cuối token phía trước: [":", "a:", "b"].

## 3. Chương trình Java 17+ chạy độc lập

Lưu đoạn sau thành **ZeroWidthSplitDemo.java**:

~~~java
import java.util.Arrays;
import java.util.stream.Collectors;

public class ZeroWidthSplitDemo {
    private static void show(String label, String[] parts) {
        // Bọc mỗi token trong dấu nháy để thấy rõ chuỗi rỗng.
        String visible = Arrays.stream(parts)
                .map(s -> "'" + s + "'")
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println(label + " -> " + visible
                + " (n=" + parts.length + ")");
    }

    public static void main(String[] args) {
        String input = ":a:b";
        show("positive ':'", input.split(":"));
        show("zero '(?=:)'", input.split("(?=:)"));
        show("zero '(?<=:)'", input.split("(?<=:)"));

        show("zero '^'", "abc".split("^", -1));
        show("zero '$'", "abc".split("$", -1));
        show("zero empty regex", "abc".split("", -1));

        show("positive default", "a,b,,".split(","));
        show("positive -1", "a,b,,".split(",", -1));
        show("positive 2", "a,b,,".split(",", 2));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ZeroWidthSplitDemo.java
java ZeroWidthSplitDemo
~~~

Kết quả chính xác:

~~~text
positive ':' -> ['', 'a', 'b'] (n=3)
zero '(?=:)' -> [':a', ':b'] (n=2)
zero '(?<=:)' -> [':', 'a:', 'b'] (n=3)
zero '^' -> ['abc'] (n=1)
zero '$' -> ['abc', ''] (n=2)
zero empty regex -> ['a', 'b', 'c', ''] (n=4)
positive default -> ['a', 'b'] (n=2)
positive -1 -> ['a', 'b', '', ''] (n=4)
positive 2 -> ['a', 'b,,'] (n=2)
~~~

Hàm **show** bọc từng token bằng dấu nháy đơn và in **n** (số phần tử). Cách này giúp phân biệt token rỗng với mảng rỗng; **Arrays.toString** mặc định không tự bọc nháy chuỗi và rất dễ gây hiểu nhầm khi các token là "".

## 4. Vì sao khớp độ rộng 0 vẫn tách được?

Có thể hình dung thuật toán:

1. Tìm lần khớp của regex theo thứ tự từ trái sang phải.
2. Đưa đoạn trước lần khớp vào một token, sau đó bắt đầu xem đoạn còn lại từ **cuối phần regex đã khớp**.
3. Nếu lần khớp tiêu thụ ký tự, những ký tự đó đóng vai trò dấu phân cách và bị bỏ. Nếu lần khớp có độ rộng 0, ranh giới vẫn có thể chia chuỗi nhưng **không xóa ký tự**.
4. Xử lý phần còn lại và các token rỗng cuối theo giá trị **limit**.

Riêng một lần khớp **độ rộng 0 ở index 0** không sinh token "". Nếu regex khớp **độ rộng dương ở index 0** thì token đầu "" vẫn được sinh. Các lần khớp độ rộng 0 ở giữa chuỗi vẫn tạo ranh giới như thường; Java xử lý tiến trình tìm khớp để tránh tìm mãi cùng một vị trí.

Các anchor ở hai đầu cho thấy sự khác biệt:

- **"abc".split("^", -1)** → ["abc"]. "^" khớp độ rộng 0 ở đầu, nên không có token "" đầu dù limit âm.
- **"abc".split("$", -1)** → ["abc", ""]. "$" khớp độ rộng 0 ở cuối, tạo token "" cuối và limit -1 giữ lại.
- **"abc".split("$")** → ["abc"]. limit mặc định 0 loại token rỗng ở cuối.
- **"abc".split("", -1)** → ["a", "b", "c", ""]. Regex rỗng có thể khớp nhiều ranh giới: không tạo "" đầu nhưng vẫn có thể tạo "" cuối khi limit âm.

## 5. Đừng nhầm với tham số limit

**String.split(regex)** tương đương **String.split(regex, 0)**. Tham số **limit** có tác động sau:

| limit | Ý nghĩa | "a,b,," với regex "," |
| --- | --- | --- |
| **0** (mặc định) | Tách không giới hạn, **bỏ các token rỗng ở cuối** | ["a", "b"] |
| **Âm**, ví dụ **-1** | Tách không giới hạn, **giữ token rỗng ở cuối** | ["a", "b", "", ""] |
| **Dương**, ví dụ **2** | Tách tối đa **limit - 1** lần, token cuối chứa nguyên phần còn lại | ["a", "b,,"] |
| **1** | Không tách, trả về nguyên chuỗi | ["a,b,,"] |

**Phân biệt rõ:** limit 0 chỉ bỏ các phần tử **rỗng ở cuối**. Nó không tự bỏ token rỗng **ở đầu hay ở giữa** do delimiter có độ rộng tạo ra. Vì vậy **":a:b".split(":")** là ["", "a", "b"] mặc dù limit mặc định bằng 0.

Trong khi đó, **":a:b".split("(?=:)")** không có token rỗng đầu **vì quy tắc khớp độ rộng 0 tại đầu**, chứ không phải vì limit 0 xóa token đó. Quy tắc tại đầu vẫn đúng nếu limit = -1.

Limit dương là **mức tối đa**, không phải lời hứa số token luôn đúng bằng limit: nếu không đủ lần khớp, mảng kết quả có thể ít phần tử hơn.

## 6. Chọn biểu thức theo nhu cầu

**Loại dấu phân cách khỏi kết quả:** dùng regex tiêu thụ ký tự, chẳng hạn:

~~~java
String[] words = "red:green:blue".split(":");
// ["red", "green", "blue"]
~~~

**Giữ dấu phân cách ở đầu token tiếp theo:** dùng lookahead:

~~~java
String[] words = "red:green:blue".split("(?=:)");
 // ["red", ":green", ":blue"]
~~~

**Giữ dấu phân cách ở cuối token phía trước:** dùng lookbehind:

~~~java
String[] words = "red:green:blue".split("(?<=:)");
 // ["red:", "green:", "blue"]
~~~

Ba đoạn code trên là các mảnh minh họa có thể đặt trong phương thức. Lookahead/lookbehind chỉ **kiểm tra ngữ cảnh**, nên dấu phân cách vẫn được giữ.

**Cẩn thận ký tự đặc biệt trong regex:** dấu chấm "." là regex khớp gần như mọi ký tự, không phải dấu chấm nguyên văn. Vì thế **"a.b.c".split(".")** không trả về ["a", "b", "c"]; với limit 0, kết quả là mảng không phần tử vì các token rỗng cuối bị loại. Tách theo dấu chấm thật bằng escape hoặc **Pattern.quote**:

~~~java
String[] a = "a.b.c".split("\\.");
String[] b = "a.b.c".split(java.util.regex.Pattern.quote("."));
// Cả hai đều có kết quả ["a", "b", "c"].
~~~

Cần phân biệt **escape trong chuỗi Java** và **escape trong regex**: chuỗi Java "\\." tạo regex khớp dấu chấm nguyên văn. Với dấu "|" nguyên văn, có thể dùng "\\|" hoặc **Pattern.quote("|")**.

## 7. Những lỗi thường gặp

1. **Cho rằng zero-width không thể tách:** nó vẫn xác định ranh giới, chỉ không xóa ký tự.
2. **Mong lookahead ở đầu sinh token rỗng:** lần khớp có độ rộng 0 tại index 0 được bỏ qua cho việc tạo token đầu.
3. **Tưởng limit mặc định loại tất cả token rỗng:** chỉ các token rỗng **ở cuối** mới bị loại.
4. **Nhầm "^" và "$" với ký tự phân cách:** đây là anchor vị trí; chúng không tiêu thụ dấu phân cách có ký tự.
5. **Dùng "." hoặc "|" chưa escape như ký tự nguyên văn:** đối số của split là regex, nên cần escape hoặc dùng Pattern.quote.
6. **Không kiểm tra số token khi in mảng:** các token "" khó nhìn thấy nếu không bọc nháy và in parts.length.

### Bài tập có đáp án

**Bài 1:** Chuỗi "::x" khi tách theo hai biểu thức sau có kết quả gì?

- **"::x".split(":")** → ["", "", "x"]. Hai lần khớp tiêu thụ hai dấu ":" liên tiếp; có token rỗng đầu và giữa.
- **"::x".split("(?=:)")** → [":", ":x"]. Lần khớp độ rộng 0 ở đầu không sinh token rỗng; lần khớp tại vị trí 1 tách chuỗi nhưng giữ dấu hai chấm.

**Bài 2:** Vì sao **"x:".split("(?<=:)", -1)** cho ["x:", ""]?

- Lookbehind tạo ranh giới độ rộng 0 **sau** dấu ":" ở cuối chuỗi. limit -1 giữ token rỗng cuối; nếu dùng limit mặc định 0, token này bị loại.

## 8. Ghi nhớ

Trước khi gọi **String.split**, hãy tự hỏi: **(1)** regex có tiêu thụ ký tự không, **(2)** lần khớp có độ rộng 0 tại ngay đầu chuỗi không, và **(3)** limit có giữ token rỗng ở cuối không. Ba câu hỏi giải thích trực tiếp sự khác nhau giữa zero-width regex và dấu phân cách có độ rộng trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-splitting-006">Dùng Pattern.quote khi dấu phân cách là dữ liệu nguyên văn</a>

<details>
<summary>Xem chi tiết</summary>

# `Pattern.quote()`: tách chuỗi bằng dấu phân cách nguyên văn

## 1. Vì sao cần `Pattern.quote`?

`String.split(String regex)` của Java luôn diễn giải tham số theo **biểu thức chính quy** (regex). Vì vậy, chuỗi `"."` không có nghĩa là dấu chấm: trong regex, `.` đại diện cho một ký tự bất kỳ (thông thường ngoại trừ ký tự kết thúc dòng). Tương tự, `|` là phép lựa chọn, `*`, `+`, `?` là các lượng từ, còn `\\` là ký tự escape. Nếu dấu phân cách do cấu hình, người dùng hoặc dữ liệu đầu vào cung cấp chứa các ký tự này, `split(delimiter)` có thể tách sai hoặc gây `PatternSyntaxException`.

`java.util.regex.Pattern.quote(String s)` chuyển chuỗi `s` thành **một regex khớp đúng toàn bộ chuỗi theo nghĩa nguyên văn**. Do đó, khi dấu phân cách là dữ liệu chứ không phải một quy luật regex, hãy viết:

```java
import java.util.regex.Pattern;

String[] parts = text.split(Pattern.quote(delimiter));
```

Ví dụ `"a.b".split(".")` cho mảng rỗng do `.` lần lượt khớp các ký tự và `split` mặc định loại các phần tử rỗng ở cuối. Trái lại, `"a.b".split(Pattern.quote("."))` cho `["a", "b"]`.

Bên trong regex, vùng trích dẫn thường có dạng `\\Q...\\E`, nhưng **không tự ghép thủ công** `"\\Q" + delimiter + "\\E"`: `Pattern.quote` còn xử lý đúng trường hợp dữ liệu chứa chính `\\E`.

## 2. Ví dụ Java 17+ chạy độc lập

Tạo file `LiteralDelimiterSplitDemo.java` với nội dung:

```java
import java.util.Arrays;
import java.util.regex.Pattern;

public class LiteralDelimiterSplitDemo {
    public static void main(String[] args) {
        // Dấu chấm: toán tử regex khác dấu chấm nguyên văn.
        String dotText = "a.b";
        System.out.println("Regex dot: "
                + Arrays.toString(dotText.split(".")));
        System.out.println("Literal dot: "
                + Arrays.toString(dotText.split(Pattern.quote("."))));

        // Dấu phân cách nhiều ký tự, có chứa toán tử regex.
        String delimiter = ".$.";
        String compositeText = "red.$.green.$.blue";
        System.out.println("Composite delimiter: "
                + Arrays.toString(compositeText.split(Pattern.quote(delimiter))));

        // Dấu gạch đứng cùng các giá trị limit.
        String pipeText = "a|b||";
        String pipeRegex = Pattern.quote("|");
        System.out.println("Default limit: "
                + Arrays.toString(pipeText.split(pipeRegex)));
        System.out.println("Keep trailing: "
                + Arrays.toString(pipeText.split(pipeRegex, -1)));
        System.out.println("At most two: "
                + Arrays.toString("a|b|c".split(pipeRegex, 2)));

        // Gạch chéo ngược: ký tự đặc biệt của cả Java literal và regex.
        String windowsPath = "C:\\temp\\docs";
        System.out.println("Backslash: "
                + Arrays.toString(windowsPath.split(Pattern.quote("\\"))));

        // Khi tách nhiều chuỗi bằng cùng một dấu phân cách.
        Pattern marker = Pattern.compile(Pattern.quote("<E>"));
        System.out.println("Compiled: "
                + Arrays.toString(marker.split("A<E>B<E>C")));
    }
}
```

Biên dịch và chạy:

```bash
javac LiteralDelimiterSplitDemo.java
java LiteralDelimiterSplitDemo
```

Đầu ra:

```text
Regex dot: []
Literal dot: [a, b]
Composite delimiter: [red, green, blue]
Default limit: [a, b]
Keep trailing: [a, b, , ]
At most two: [a, b|c]
Backslash: [C:, temp, docs]
Compiled: [A, B, C]
```

Ở dòng `Keep trailing`, mảng thực tế gồm bốn phần tử: `"a"`, `"b"`, `""`, `""`. `Arrays.toString` biểu diễn chuỗi rỗng bằng vị trí trống giữa các dấu phẩy. `String.split` không chèn thêm ký tự vào kết quả.

## 3. `limit` quyết định có giữ phần tử rỗng hay không

`Pattern.quote` chỉ làm cho dấu phân cách được so khớp **nguyên văn**; nó không thay đổi quy tắc `limit` của `String.split(regex, limit)`.

| Lệnh với chuỗi `"a|b||"` | Các phần tử thu được | Cơ chế |
| --- | --- | --- |
| `split(Pattern.quote("|"))` | `["a", "b"]` | Mặc định như `limit = 0`, bỏ các phần tử rỗng cuối. |
| `split(Pattern.quote("|"), 0)` | `["a", "b"]` | Cũng bỏ các phần tử rỗng cuối. |
| `split(Pattern.quote("|"), -1)` | `["a", "b", "", ""]` | Giữ đầy đủ các phần tử rỗng cuối. |
| `split(Pattern.quote("|"), 2)` | `["a", "b||"]` | Tách tối đa thành hai phần; phần cuối giữ phần còn lại. |

Khi `limit > 0`, có tối đa `limit - 1` lần tách. Khi `limit = 0` hoặc âm, phép tách có thể thực hiện nhiều lần; điểm khác nhau là `limit = 0` bỏ phần tử rỗng ở cuối, còn `limit < 0` giữ lại. Các phần tử rỗng **ở giữa** dấu phân cách liên tiếp vẫn có thể được giữ.

Nếu dữ liệu mang các cột có thể rỗng ở cuối, chẳng hạn `"id|name||"`, nên dùng `split(Pattern.quote("|"), -1)` để bảo toàn số trường; nếu dùng mặc định, có nguy cơ mất các trường cuối.

## 4. Các dấu phân cách dễ gặp lỗi

| Dữ liệu làm dấu phân cách | Cách gọi phù hợp | Vấn đề khi coi là regex |
| --- | --- | --- |
| Dấu chấm `.` | `split(Pattern.quote("."))` | `.` khớp ký tự bất kỳ. |
| Dấu gạch đứng `|` | `split(Pattern.quote("|"))` | `|` là phép lựa chọn. |
| Dấu sao `*` hoặc dấu cộng `+` | `split(Pattern.quote(delimiter))` | Lượng từ có thể gây lỗi cú pháp regex. |
| Chuỗi `.$.` | `split(Pattern.quote(".$."))` | Có nhiều toán tử regex cùng lúc. |
| Gạch chéo ngược `\\` | `split(Pattern.quote("\\"))` | Gạch chéo ngược còn là escape của regex. |
| Chuỗi `<END>` | `split(Pattern.quote("<END>"))` | Mẫu nguyên văn thể hiện đúng ý định dù dữ liệu thay đổi. |

Khi ký tự đặc biệt nằm trong **literal chuỗi Java**, còn có lớp escape của ngôn ngữ Java. Chẳng hạn, để biểu diễn **một** gạch chéo ngược trong chuỗi Java phải viết `"\\"`; để biến chính chuỗi này thành regex nguyên văn, dùng `Pattern.quote("\\")`. Escape của Java source và escape của regex là hai việc khác nhau.

`Pattern.quote` không thay đổi chữ hoa/thường, không bỏ khoảng trắng và không chuẩn hóa Unicode. Nó tìm đúng chuỗi phân cách được đưa vào. Nếu chuỗi đó không có trong văn bản, `split` thông thường trả về mảng một phần tử chứa nguyên văn bản.

## 5. Khi nào không dùng `Pattern.quote`?

Nếu mục tiêu là tách bằng **quy tắc**, ví dụ một hoặc nhiều ký tự trắng, hãy dùng regex đúng nghĩa:

```java
String line = "Java   regex\tbasics";
String[] words = line.split("\\s+");  // Một hoặc nhiều whitespace
```

Ở đây, `\\s+` là cú pháp regex chủ đích. Nếu gọi `Pattern.quote("\\s+")`, mẫu sẽ tìm đúng ba ký tự `\\`, `s`, `+` trong văn bản, không còn tìm khoảng trắng.

Hai cách dùng có mục đích khác nhau:

- **Dấu phân cách là một chuỗi cụ thể:** `text.split(Pattern.quote(delimiter))`.
- **Dấu phân cách mô tả một nhóm mẫu:** `text.split(regex)`, khi regex đã được tạo một cách có chủ đích.

Khi cùng một dấu phân cách nguyên văn được dùng nhiều lần, có thể biên dịch sẵn:

```java
Pattern literalDelimiter = Pattern.compile(Pattern.quote(delimiter));
String[] first = literalDelimiter.split(firstText, -1);
String[] second = literalDelimiter.split(secondText, -1);
```

Đây là đoạn minh họa sử dụng các biến `delimiter`, `firstText`, `secondText` do chương trình cung cấp, không phải file có thể chạy độc lập. `Pattern.split(input, limit)` có quy tắc `limit` tương tự `String.split(regex, limit)`. Biên dịch sẵn hữu ích khi dùng đi dùng lại một mẫu.

## 6. Lỗi thường gặp và giới hạn

**Tự escape vài ký tự riêng lẻ.** Chỉ thay `.` thành `\\.` sẽ bỏ sót `|`, `[`, `(`, `*` và các trường hợp khác. Việc tự xây dựng `\\Q...\\E` cũng có rủi ro nếu dữ liệu chứa `\\E`. `Pattern.quote` giải quyết cả chuỗi một cách nhất quán.

**Mặc định làm mất trường rỗng cuối.** `Pattern.quote` không sửa quy tắc này. Khi thứ tự hoặc số cột quan trọng, cần `limit = -1` và kiểm tra `array.length` trong chương trình, thay vì chỉ quan sát chuỗi `Arrays.toString`.

**Dấu phân cách rỗng hoặc `null`.** `Pattern.quote(null)` gây `NullPointerException`. Với `delimiter = ""`, mẫu nguyên văn có độ rộng bằng 0 nên việc tách trở thành tách tại các vị trí, khác ý nghĩa tách tại một dấu phân cách cụ thể. Nếu API chỉ chấp nhận dấu phân cách không rỗng, hãy kiểm tra `delimiter != null && !delimiter.isEmpty()`. Tránh `isBlank()` cho yêu cầu này: một dấu cách `" "` vẫn có thể là dấu phân cách hợp lệ.

**Lầm tưởng tách nguyên văn là phân tích CSV.** Với chuỗi `a,"b,c",d`, `split(Pattern.quote(","))` vẫn tách tại dấu phẩy bên trong cặp dấu nháy của `"b,c"`. CSV có quy tắc về dấu nháy, escape và xuống dòng; cần dùng trình phân tích CSV phù hợp nếu phải tuân thủ định dạng đó.

**Dùng nhầm cho phần thay thế regex.** `Pattern.quote` chỉ dành cho **pattern**. Khi chuỗi thay thế của `Matcher.replaceAll` chứa `$` hoặc `\\` và cần xử lý nguyên văn, API tương ứng là `Matcher.quoteReplacement`, có mục đích khác.

## 7. Tự kiểm tra

1. Để tách `"left.$.middle.$.right"` thành `["left", "middle", "right"]`, dùng `"left.$.middle.$.right".split(Pattern.quote(".$."))`.
2. Để giữ đủ bốn trường của `"name|email||"`, dùng `"name|email||".split(Pattern.quote("|"), -1)`.
3. Khi cấu hình chứa `"."`, `split(delimiter)` coi nó là toán tử regex; `split(Pattern.quote(delimiter))` khớp đúng dấu chấm.

**Điều cần nhớ:** Tham số đầu tiên của `String.split` là regex. Dấu phân cách đến từ **dữ liệu nguyên văn** thì dùng `Pattern.quote`; các phần tử rỗng ở cuối cần được bảo toàn thì thêm `limit = -1`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-splitting-007">splitWithDelimiters từ Java 21</a>

<details>
<summary>Xem chi tiết</summary>

# `String.splitWithDelimiters()` — tách chuỗi và giữ dấu phân cách (Java 21)

Khi cần chia một chuỗi thành các phần, `String.split(regex)` thường đủ dùng. Nhưng nếu muốn **giữ nguyên ký tự hoặc đoạn ký tự đã dùng để tách** (dấu phẩy, dấu hai chấm, khoảng trắng, dấu phân cách nhiều ký tự...), Java 21 cung cấp `String.splitWithDelimiters(regex, limit)`. Phương thức trả về cả phần nội dung và phần khớp biểu thức chính quy theo đúng thứ tự xuất hiện.

## 1. API và ý nghĩa

```java
public String[] splitWithDelimiters(String regex, int limit)
```

- `regex`: **biểu thức chính quy (regular expression)** mô tả dấu phân cách cần tìm; đây không mặc nhiên là chuỗi ký tự thông thường.
- `limit`: điều khiển số lần tách và việc giữ/bỏ những chuỗi rỗng ở cuối kết quả.
- Giá trị trả về: `String[]` lần lượt chứa **đoạn nội dung, dấu phân cách khớp regex, đoạn nội dung, dấu phân cách, ...**; dấu phân cách giữ chính xác những ký tự thật sự khớp.
- Phương thức có từ **Java 21**. Không có phiên bản `splitWithDelimiters(regex)` chỉ nhận một tham số như `split(regex)`; phải truyền `limit`.

Ví dụ, với `"red:::green::blue"` và regex `":+"`, kết quả là `["red", ":::", "green", "::", "blue"]`. Hai dấu phân cách `":::"` và `"::"` được giữ ở các phần tử riêng, do `:+` khớp **một hoặc nhiều** dấu hai chấm liên tiếp.

`String.splitWithDelimiters(regex, limit)` có cùng ý nghĩa với `Pattern.compile(regex).splitWithDelimiters(input, limit)`. Bản `Pattern` hữu ích khi phải tái sử dụng một regex nhiều lần.

## 2. Khác gì `split()`?

| Phép gọi với `s = "a::b:c"` | Kết quả |
| --- | --- |
| `s.split(":+", -1)` | `["a", "b", "c"]` |
| `s.splitWithDelimiters(":+", -1)` | `["a", "::", "b", ":", "c"]` |

`split()` bỏ các đoạn khớp regex; `splitWithDelimiters()` lưu chúng trong mảng. Vì vậy phương thức mới phù hợp khi muốn dựng lại nguyên văn chuỗi ban đầu, phân tích log, giữ nguyên dấu câu hoặc nhận biết từng loại ký tự phân cách.

Hình dung luồng xử lý:

```text
Chuỗi:        red ::: green :: blue
Đoạn trả về: [red][:::][green][::][blue]
Loại:        [NỘI DUNG][NGĂN CÁCH][NỘI DUNG][NGĂN CÁCH][NỘI DUNG]
```

Mỗi lần tìm thấy dấu phân cách, kết quả thêm phần nội dung **đứng trước** rồi đến chính phần khớp. Nếu không có dấu phân cách nào khớp, mảng chỉ có một phần tử là toàn bộ chuỗi đầu vào.

## 3. Ví dụ Java 21 chạy độc lập

Tạo file `SplitWithDelimitersDemo.java`:

```java
import java.util.Arrays;
import java.util.regex.Pattern;

public class SplitWithDelimitersDemo {
    public static void main(String[] args) {
        String input = "red:::green::blue";

        System.out.println("split       = "
                + Arrays.toString(input.split(":+", -1)));
        System.out.println("with        = "
                + Arrays.toString(input.splitWithDelimiters(":+", -1)));
        System.out.println("limit 2     = "
                + Arrays.toString(input.splitWithDelimiters(":+", 2)));

        String[] parts = input.splitWithDelimiters(":+", -1);
        System.out.println("round-trip  = "
                + input.equals(String.join("", parts)));

        // Dấu chấm theo nghĩa ký tự thật, không phải wildcard regex.
        System.out.println("literal dot = "
                + Arrays.toString(
                        "a.b.c".splitWithDelimiters(Pattern.quote("."), -1)));
    }
}
```

Biên dịch và chạy bằng JDK 21 trở lên:

```bash
javac --release 21 SplitWithDelimitersDemo.java
java SplitWithDelimitersDemo
```

Kết quả chuẩn:

```text
split       = [red, green, blue]
with        = [red, :::, green, ::, blue]
limit 2     = [red, :::, green::blue]
round-trip  = true
literal dot = [a, ., b, ., c]
```

`String.join("", parts)` nối tuần tự tất cả phần nội dung và dấu phân cách, không chèn thêm gì. Với regex tách thông thường, cách này giúp kiểm tra đã giữ được nguyên văn đầu vào. Kết quả `limit 2` cho thấy chỉ dấu `:::` đầu tiên được tách ra; phần `green::blue` còn lại giữ nguyên.

## 4. Hiểu chính xác tham số `limit`

`limit` **không phải số phần tử tối đa** của mảng, cũng không phải độ dài tối đa của từng phần tử.

| `limit` | Cách xử lý | Hệ quả |
| --- | --- | --- |
| `> 0` | Áp dụng regex tối đa `limit - 1` lần | Mảng dài tối đa `2 * limit - 1` phần tử; phần tử cuối chứa toàn bộ phần còn lại |
| `= 0` | Tách tối đa | Bỏ **các chuỗi rỗng ở cuối** mảng, gồm phần nội dung rỗng hoặc dấu phân cách rỗng |
| `< 0` | Tách tối đa | Giữ cả các chuỗi rỗng ở cuối |

Với chuỗi `"a,b,"`, regex `","`:

| Lệnh | Mảng trả về (dùng ký hiệu dấu ngoặc kép để nhìn rõ chuỗi rỗng) |
| --- | --- |
| `splitWithDelimiters(",", 1)` | `["a,b,"]` |
| `splitWithDelimiters(",", 2)` | `["a", ",", "b,"]` |
| `splitWithDelimiters(",", 0)` | `["a", ",", "b", ","]` |
| `splitWithDelimiters(",", -1)` | `["a", ",", "b", ",", ""]` |

Lưu ý ở `limit = 0`: **dấu phẩy cuối vẫn còn** vì đó là chuỗi `","`, không rỗng; chỉ phần nội dung `""` sau dấu phẩy bị bỏ. `limit = -1` sẽ giữ phần tử `""` để phân biệt kết thúc ngay sau dấu phân cách.

Với `limit = 1`, không thực hiện lần tách nào, nên kết quả là mảng một phần tử chứa cả chuỗi. Với `limit > 1`, số lần tách tối đa là `limit - 1`, mỗi lần tách có thể tạo thêm **hai phần tử** (nội dung và dấu phân cách); do đó giới hạn độ dài là `2 * limit - 1`, khác `String.split(regex, limit)`.

## 5. Regex quyết định dấu phân cách được giữ như thế nào

- Regex `","` chỉ khớp một dấu phẩy. Với `"a,,b"` và `limit = -1`, nhận `["a", ",", "", ",", "b"]`. Phần nội dung rỗng ở giữa là thông tin có ý nghĩa: hai dấu phẩy liên tiếp.
- Regex `",+"` khớp cả nhóm dấu phẩy liên tiếp. Cùng chuỗi `"a,,b"` sẽ thành `["a", ",,", "b"]`. Hãy chọn `","` hay `",+"` theo việc muốn phân biệt từng dấu riêng hay gộp cả cụm.
- Regex `"[,;]\\s*"` khớp một dấu phẩy **hoặc** chấm phẩy, sau đó là mọi khoảng trắng liền kề. `"milk, bread; eggs".splitWithDelimiters("[,;]\\s*", -1)` trả về `["milk", ", ", "bread", "; ", "eggs"]`. Khoảng trắng cũng nằm trong dấu phân cách nên không bị mất.
- Các ký tự `.`, `|`, `*`, `+`, `?`, `(`, `)`, `[`, `]`... có ý nghĩa đặc biệt trong regex. Để tách theo **ký tự dấu chấm thật**, dùng `"\\."` trong source Java, hoặc `Pattern.quote(".")`; truyền `"."` sẽ khớp gần như mọi ký tự đơn lẻ.

Nếu regex có thể khớp **chuỗi rỗng** (zero-width match), kết quả có các quy tắc biên cần chú ý. Cụ thể, một match **không rỗng** ngay đầu chuỗi sẽ tạo phần nội dung rỗng ở đầu; còn match **độ rộng bằng 0** ngay đầu chuỗi **không** tạo phần nội dung rỗng hoặc phần dấu phân cách rỗng mở đầu. Không nên mặc định mọi regex đều tạo cùng kiểu mảng như ví dụ chỉ dùng dấu phân cách thật.

## 6. Trường hợp biên quan trọng

| Input | Regex | `limit` | Kết quả |
| --- | --- | --- | --- |
| `",A"` | `","` | `-1` | `["", ",", "A"]` |
| `"A,"` | `","` | `-1` | `["A", ",", ""]` |
| `"A,"` | `","` | `0` | `["A", ","]` |
| `"A,,B"` | `","` | `-1` | `["A", ",", "", ",", "B"]` |
| `"ABC"` | `","` | `-1` | `["ABC"]` |

Các kết quả này minh họa sự khác nhau giữa **đoạn nội dung rỗng** và **dấu phân cách**. Với `limit = 0`, chỉ phần tử rỗng nằm **ở tận cuối** bị loại; phần nội dung rỗng giữa hai dấu phân cách vẫn được giữ.

## 7. Ứng dụng thực tế: xử lý token nhưng giữ dấu nối

Giả sử muốn tô màu từng phần của chuỗi mà vẫn giữ nguyên dấu phẩy, dấu chấm phẩy và khoảng trắng sau chúng:

```java
String input = "milk, bread; eggs";
String[] tokens = input.splitWithDelimiters("[,;]\\s*", -1);

for (int i = 0; i < tokens.length; i++) {
    boolean isDelimiter = (i % 2 == 1);
    System.out.printf("%s: <%s>%n",
            isDelimiter ? "DELIMITER" : "TEXT",
            tokens[i]);
}
```

Đây là **đoạn mã dùng bên trong phương thức hoặc JShell**, không phải một file Java độc lập. Kết quả:

```text
TEXT: <milk>
DELIMITER: <, >
TEXT: <bread>
DELIMITER: <; >
TEXT: <eggs>
```

Với cách tách giữ delimiter này, các phần tử ở chỉ số chẵn (`0, 2, 4...`) là phần nội dung, chỉ số lẻ (`1, 3, 5...`) là dấu phân cách. Khi regex có thể khớp chuỗi rỗng, phải xét thêm quy tắc zero-width; khi `limit = 0` làm mất phần nội dung rỗng cuối, mảng có thể kết thúc ngay sau một delimiter.

Các phần tử có thể dùng để dựng giao diện highlight, chuẩn hóa một số kiểu log, đếm hoặc nhận diện từng dấu phân cách. Nếu muốn giữ nguyên dữ liệu gốc để xử lý tiếp, thường chọn `limit = -1`.

## 8. Các lỗi thường gặp và cách chọn API

1. **Nhầm regex với chuỗi literal:** `"."` là ký tự đại diện trong regex; dùng `Pattern.quote(".")` hoặc `"\\."` khi tìm dấu chấm thực sự.
2. **Tưởng `limit = 2` nghĩa là hai phần tử:** phương thức có thể trả về ba phần tử `[trước, delimiter, còn_lại]`.
3. **Tưởng `limit = 0` xóa dấu phân cách cuối:** nó chỉ xóa **chuỗi rỗng** ở cuối, không xóa ký tự phân cách thật.
4. **Bỏ quên phần nội dung rỗng giữa hai delimiter:** `"a,,b"` khác `"a,b"` nếu quy tắc dữ liệu coi trường rỗng là quan trọng.
5. **Dùng regex phức tạp cho dữ liệu có cấu trúc:** phương thức này **không thay thế parser CSV chuẩn** (đặc biệt khi có chuỗi trong ngoặc kép, dấu phân cách được escape) hay parser cho cú pháp lồng nhau.
6. **Lặp lại regex nhiều lần trên lượng dữ liệu lớn:** có thể biên dịch một `Pattern` trước rồi dùng `pattern.splitWithDelimiters(input, limit)` để tái sử dụng; tránh regex có backtracking tốn kém.

**Chọn `split()`** khi chỉ cần các giá trị sau khi loại dấu phân cách. **Chọn `splitWithDelimiters()`** khi vị trí và nguyên văn dấu phân cách cũng là thông tin cần giữ.

## 9. Tự kiểm tra

- `"x|y".splitWithDelimiters("\\|", -1)` trả về gì? → `["x", "|", "y"]`.
- Vì sao `"x,,y".splitWithDelimiters(",", -1)` có phần tử `""`? → Hai lần khớp dấu phẩy liên tiếp, không có nội dung giữa chúng.
- Khi muốn giữ cả trường rỗng cuối chuỗi, chọn `limit` nào? → Giá trị âm, thường là `-1`.
- Với `limit = 3`, chiều dài mảng lớn nhất là bao nhiêu? → `2 * 3 - 1 = 5`.
- Khi không có dấu nào khớp, kết quả là gì? → Mảng gồm duy nhất chuỗi ban đầu.

**Tham khảo API chính thức:** [`String.splitWithDelimiters` trong Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#splitWithDelimiters(java.lang.String,int)) và [`Pattern.splitWithDelimiters` trong Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html#splitWithDelimiters(java.lang.CharSequence,int)).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-splitting-008">Pattern.splitAsStream và xử lý kết quả lười</a>

<details>
<summary>Xem chi tiết</summary>

# Pattern.splitAsStream() và xử lý kết quả lười trong Java

## 1. Mục tiêu và ý tưởng cốt lõi

Khi cần tách một chuỗi bằng **biểu thức chính quy (regex)** rồi lọc, chuyển đổi hoặc lấy một vài phần tử đầu tiên, Java cung cấp `Pattern.splitAsStream(CharSequence input)` trong `java.util.regex.Pattern` (từ Java 8). Phương thức trả về `Stream<String>` chứa các đoạn nằm giữa những lần khớp của regex.

Thay vì tạo trước một mảng toàn bộ token như `String.split(regex)`, API này cho phép kết nối ngay với các thao tác Stream:

- **Intermediate operations** như `map`, `filter`, `distinct`, `limit`, `skip` thường xử lý lười: xây dựng pipeline chưa khiến các token được duyệt.
- **Terminal operations** như `toList` (Java 16+), `collect`, `findFirst`, `anyMatch`, `forEach` mới kích hoạt việc duyệt.
- Với terminal operation có thể **dừng sớm** (ví dụ `findFirst` hoặc `limit(2).toList()`), pipeline có thể tránh xử lý những phần tử còn lại. Đây là lợi ích chính của sự lười (laziness); không nên hiểu rằng mọi trường hợp đều tiết kiệm bộ nhớ hoặc thời gian.

Cú pháp cơ bản:

```java
Pattern delimiter = Pattern.compile("\\s*,\\s*"); // Dấu phẩy, cho phép khoảng trắng hai bên
Stream<String> tokens = delimiter.splitAsStream("java, spring, kafka");
// Đến đây ta mới có pipeline; terminal operation bên dưới bắt đầu tiêu thụ stream.
List<String> result = tokens.filter(s -> !s.isBlank()).toList();
```

Regex `\\s*,\\s*` khớp dấu phẩy cùng khoảng trắng xung quanh. Kết quả là `[java, spring, kafka]`. Regex là **dấu phân tách**, không phải regex mô tả nội dung token; những phần khớp với dấu phân tách không xuất hiện trong stream.

## 2. Cách splitAsStream hoạt động

Với `Pattern.compile(",").splitAsStream("a,b,c")`, biểu thức `,` tìm hai vị trí phân tách và stream phát ra lần lượt `"a"`, `"b"`, `"c"`. Thứ tự các token là thứ tự trong input.

| Input | Regex | Các token theo thứ tự | Ý nghĩa |
| --- | --- | --- | --- |
| `"a,b,c"` | `","` | `["a", "b", "c"]` | Tách thông thường. |
| `"a,,b"` | `","` | `["a", "", "b"]` | Giữ token rỗng **ở giữa**. |
| `"a,b,,"` | `","` | `["a", "b"]` | Bỏ các token rỗng **ở cuối**. |
| `",a,b"` | `","` | `["", "a", "b"]` | Dấu phân tách có độ dài dương ở đầu tạo token rỗng đầu tiên. |
| `"abc"` | `","` | `["abc"]` | Không tìm thấy dấu phân tách: một token là toàn bộ input. |
| `"abc"` | `"(?=a)"` | `["abc"]` | Khớp độ dài 0 ngay đầu chuỗi **không** tạo token rỗng đầu tiên. |

**Quy tắc quan trọng:** `splitAsStream` loại bỏ các chuỗi rỗng ở cuối, tương tự cách tách với `limit = 0` của `Pattern.split(input, 0)`. Không được lẫn token rỗng ở giữa với token rỗng cuối. Nếu dữ liệu cần bảo toàn số cột trống ở cuối (chẳng hạn record phân cách bằng dấu `|`), hãy dùng `pattern.split(input, -1)`.

Các regex có độ dài khớp bằng 0 (zero-width match), ví dụ lookahead `(?=a)`, cần được kiểm tra cẩn thận khi dùng làm dấu phân tách. Khớp rỗng ở đầu khác khớp ký tự thật ở đầu; đặc điểm này ảnh hưởng số token.

**Không có tham số `limit` trong `splitAsStream`.** `stream.limit(n)` là giới hạn **số token được tiêu thụ**, còn `Pattern.split(input, n)` giới hạn **số lần áp dụng dấu phân tách** và để phần dư trong phần tử cuối. Ví dụ:

```java
Pattern p = Pattern.compile(",");
System.out.println(p.splitAsStream("a,b,c,d").limit(2).toList());
// [a, b]
System.out.println(Arrays.toString(p.split("a,b,c,d", 2)));
// [a, b,c,d]
```

Hai kết quả khác nhau; khi muốn giữ toàn bộ phần còn lại vào cột cuối, hãy chọn `split(input, n)`.

## 3. Ví dụ Java 17+ chạy độc lập

Tạo file **`SplitAsStreamDemo.java`**:

```java
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SplitAsStreamDemo {
    private static final Pattern COMMA = Pattern.compile("\\s*,\\s*");

    public static void main(String[] args) {
        String input = "java, spring,, kafka, ";

        // toList() tiêu thụ toàn bộ stream và tạo danh sách kết quả.
        List<String> raw = COMMA.splitAsStream(input).toList();
        System.out.println("So token: " + raw.size());
        System.out.println("Token: " + raw.stream()
                .map(s -> "[" + s + "]")
                .collect(Collectors.joining(" ")));

        // Bỏ token rỗng, viết hoa, rồi mới gom thành List.
        List<String> normalized = COMMA.splitAsStream(input)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .toList();
        System.out.println("Da loc: " + normalized);

        // Chỉ lấy tối đa hai token đầu tiên.
        List<String> firstTwo = COMMA.splitAsStream(input).limit(2).toList();
        System.out.println("Hai token dau: " + firstTwo);

        // findFirst() có thể dừng ngay khi gặp token phù hợp.
        String firstLong = COMMA.splitAsStream(input)
                .filter(s -> s.length() >= 5)
                .findFirst()
                .orElse("<khong co>");
        System.out.println("Token dai dau tien: " + firstLong);

        // Token "NaN" nằm sau hai giá trị cần lấy, không bị parse.
        List<Integer> numbers = Pattern.compile(",")
                .splitAsStream("10,20,NaN")
                .map(Integer::parseInt)
                .limit(2)
                .toList();
        System.out.println("Chi lay hai so: " + numbers);

        // Cần giữ các trường trống ở cuối? Dùng split(input, -1).
        System.out.println("split(..., -1): "
                + Arrays.toString(COMMA.split(input, -1)));
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac --release 17 SplitAsStreamDemo.java
java SplitAsStreamDemo
```

Kết quả stdout (đã kiểm tra bằng Java với chế độ biên dịch Java 17):

```text
So token: 4
Token: [java] [spring] [] [kafka]
Da loc: [JAVA, SPRING, KAFKA]
Hai token dau: [java, spring]
Token dai dau tien: spring
Chi lay hai so: [10, 20]
split(..., -1): [java, spring, , kafka, ]
```

Giải thích:

1. Input có hai dấu phẩy liên tiếp nên có một token rỗng **ở giữa**. Dấu phẩy cuối tạo phần rỗng **ở cuối** và phần này bị `splitAsStream` bỏ đi: còn bốn token.
2. `filter(s -> !s.isBlank())` bỏ chuỗi rỗng hoặc toàn khoảng trắng. `map(String::toUpperCase)` biến đổi token còn lại. Thứ tự `filter` trước `map` cũng giúp không biến đổi các token sẽ bị loại.
3. `limit(2)` chỉ cần hai phần tử đầu; `findFirst()` dừng sau khi tìm thấy `spring` có độ dài từ năm ký tự.
4. `map(Integer::parseInt).limit(2)` chỉ chuyển `10` và `20` thành số; vì chưa yêu cầu phần tử thứ ba nên chuỗi `NaN` không gây `NumberFormatException`. Nếu đổi terminal operation thành `toList()` mà bỏ `limit(2)` thì việc parse đến `NaN` sẽ ném ngoại lệ.
5. `split(input, -1)` giữ thêm một trường trống cuối, nên output kết thúc bằng dấu phẩy trước dấu `]`.

## 4. Pipeline lười: khi nào thực sự làm việc?

Một pipeline có ba thành phần:

```text
Pattern + CharSequence
        |
        v
splitAsStream(input)  -->  filter(...)  -->  map(...)  -->  limit(...)
                                                        |
                                                        v
                                                     toList()
```

Việc gọi `splitAsStream` và xâu chuỗi các intermediate operation chưa yêu cầu Stream trả toàn bộ kết quả. Java bắt đầu duyệt khi gặp terminal operation. Các phần tử thường đi qua nhiều bước xử lý theo nhu cầu của terminal operation, thay vì tạo sẵn một mảng token rồi mới lọc.

Ví dụ:

```java
Pattern p = Pattern.compile(";");
List<String> firstValid = p.splitAsStream(" ;  alice ; ; bob ; carol ")
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .limit(2)
        .toList();
System.out.println(firstValid); // [alice, bob]
```

Dù input chứa nhiều đoạn, kết quả chỉ cần hai token hợp lệ. Pipeline có thể phải xem **nhiều hơn hai token nguồn** vì các token rỗng không vượt qua `filter`; `limit(2)` đếm phần tử **sau khi lọc**. Đảo thứ tự hai bước sẽ đổi ý nghĩa:

- `filter(...).limit(2)`: lấy tối đa hai token **hợp lệ**.
- `limit(2).filter(...)`: chỉ xem hai token **đầu vào** rồi bỏ phần tử không hợp lệ; có thể thu được ít hơn hai token.

Dừng sớm là **khả năng của pipeline**, không bảo đảm rằng toàn bộ regex engine hay mỗi bước nội bộ chỉ làm đúng một lượng công việc cố định. Với `toList()`, `count()`, `collect(...)` hoặc `forEach(...)` thông thường, việc duyệt cần đến hết stream; riêng một số tối ưu của Stream có thể khiến callback không được gọi như người viết mong đợi. Không dựa vào side effect trong `map`, `filter` hoặc `peek` để thực hiện công việc bắt buộc.

**Stream là một lần sử dụng:** sau terminal operation, không tái dùng stream cũ. Khi cần hai cách xử lý khác nhau, gọi lại `pattern.splitAsStream(input)` để tạo hai stream; `Pattern` có thể tái sử dụng.

```java
Pattern p = Pattern.compile(",");
String input = "a,b,c";
long total = p.splitAsStream(input).count();
List<String> first = p.splitAsStream(input).limit(1).toList();
System.out.println(total + " " + first); // 3 [a]
```

## 5. Áp dụng trong chương trình

### 5.1. Chuẩn hóa danh sách nhãn

Giả sử người dùng nhập danh sách tag ngăn cách bởi dấu chấm phẩy, có khoảng trắng, ô rỗng và nhãn lặp:

```java
Pattern separator = Pattern.compile(";");
List<String> tags = separator.splitAsStream(" java ; spring ; ; java ; sql ; ")
        .map(String::strip)
        .filter(s -> !s.isBlank())
        .distinct()
        .toList();
System.out.println(tags); // [java, spring, sql]
```

`strip()` (Java 11+) loại bỏ khoảng trắng Unicode ở hai đầu theo `Character.isWhitespace`; khác `trim()` ở một số trường hợp Unicode. `distinct()` giữ lần xuất hiện đầu tiên theo thứ tự gặp đối với stream có thứ tự. Việc lọc token rỗng là lựa chọn của bài toán, **không tự động xảy ra cho token rỗng ở giữa**.

### 5.2. Lấy một phần dữ liệu và kiểm tra hợp lệ

```java
Pattern comma = Pattern.compile(",");
boolean hasAdmin = comma.splitAsStream("guest,editor,admin,viewer")
        .map(String::strip)
        .anyMatch("admin"::equals);
System.out.println(hasAdmin); // true
```

`anyMatch` trả về ngay khi đã biết kết quả là `true`; với dữ liệu đủ lớn và dấu phân tách phù hợp, điều này có thể tránh xử lý phần tử không cần thiết. Nếu không có token khớp, thao tác phải duyệt cho đến hết.

### 5.3. Regex và delimiter đúng ý nghĩa

`Pattern.compile(regex)` diễn giải ký tự đặc biệt như `.`, `|`, `*`, `+`, dấu ngoặc. Ví dụ dấu `.` trong regex đại diện cho một ký tự bất kỳ; để tách theo **dấu chấm thật**, dùng `"\\."` hoặc `Pattern.quote(".")`:

```java
Pattern dot = Pattern.compile(Pattern.quote("."));
System.out.println(dot.splitAsStream("api.example.com").toList());
// [api, example, com]
```

Khi delimiter do người dùng nhập như **văn bản thuần**, `Pattern.quote(userDelimiter)` thường an toàn hơn việc ghép trực tiếp delimiter vào regex. Khi muốn tách theo nhiều khoảng trắng, dùng regex `"\\s+"` và cân nhắc token rỗng đầu chuỗi nếu input bắt đầu bằng khoảng trắng.

## 6. So sánh cách tách chuỗi

| API | Kết quả | Đặc điểm và tình huống phù hợp |
| --- | --- | --- |
| `String.split(regex)` | `String[]` | Tiện cho thao tác đơn giản; tương ứng với `limit = 0`, bỏ trường rỗng ở cuối. |
| `Pattern.split(input, limit)` | `String[]` | Chủ động điều khiển `limit`. `limit < 0` giữ cả trường rỗng cuối; `limit > 0` giới hạn số lần tách. |
| `Pattern.splitAsStream(input)` | `Stream<String>` | Xâu chuỗi `filter/map/limit/findFirst`, hỗ trợ xử lý theo nhu cầu; không có tham số `limit` kiểu `split`. |
| `String.lines()` | `Stream<String>` | Thích hợp khi mục tiêu là lấy các dòng phân cách bằng LF/CR/CRLF (Java 11+), không cần tự viết regex dòng. |

Không mặc định rằng dùng stream luôn nhanh hơn mảng. Nếu cuối cùng vẫn `toList()` toàn bộ token thì chương trình phải duyệt toàn bộ dữ liệu và tạo List. Hãy chọn dựa trên việc cần dừng sớm, logic pipeline, yêu cầu giữ token rỗng và chi phí thực tế đã đo được.

## 7. Những lỗi thường gặp

1. **Nhầm regex với chuỗi ký tự thường.** `Pattern.compile(".")` sẽ khớp từng ký tự thay vì tách theo dấu chấm. Dùng `Pattern.quote(".")` hoặc `"\\."`.
2. **Cho rằng mọi token rỗng đều bị bỏ.** `splitAsStream("a,,b")` với dấu phẩy vẫn có token `""` ở giữa. Nếu muốn loại bỏ, dùng `filter(s -> !s.isEmpty())` hoặc `!s.isBlank()` tùy yêu cầu.
3. **Muốn giữ cột trống cuối nhưng dùng `splitAsStream`.** Vì API loại bỏ phần tử rỗng ở cuối, hãy chuyển sang `split(input, -1)` trước khi áp dụng quy tắc nghiệp vụ đòi bảo toàn vị trí cột.
4. **Lẫn `limit(n)` với `split(input, n)`.** Thao tác Stream giới hạn số token xuất ra; tham số của `split` còn gộp phần dữ liệu chưa tách vào token cuối.
5. **Cố sử dụng stream hai lần.** Stream đã chạy terminal operation không dùng lại được; tạo stream mới từ cùng `Pattern`.
6. **Sửa input dạng mutable trong khi stream đang thực thi.** API nhận `CharSequence`, có thể là `StringBuilder`. Nếu input thay đổi trong quá trình terminal operation, kết quả là **không được xác định** theo hợp đồng API. Có thể chụp `builder.toString()` trước rồi xử lý trên chuỗi bất biến.
7. **Nhầm dữ liệu CSV thực sự với tách dấu phẩy.** Dòng `"Nguyen, Van",25` có dấu phẩy bên trong trường được trích dẫn; `splitAsStream` với `,` không hiểu cú pháp CSV, dấu nháy hoặc escape. Dùng parser CSV đúng chuẩn khi cần.
8. **Dùng `peek`/`map` cho side effect thiết yếu.** Pipeline có thể dừng sớm hoặc được tối ưu; xử lý side effect một cách tường minh ở nơi được bảo đảm, đặc biệt khi mã có tác động ngoài chương trình.
9. **Biên dịch regex liên tục trong vòng lặp.** Nếu delimiter cố định và dùng nhiều lần, tạo một `Pattern` tái sử dụng. `Pattern` là bất biến và dùng lại được giữa nhiều lời gọi; `Matcher` riêng lẻ thì không nên dùng chung giữa các thread.
10. **Bỏ qua chi phí regex.** Pattern phức tạp có thể tốn CPU trên input dài. Với dữ liệu đến từ bên ngoài, kiểm tra regex, kích thước input và hiệu năng, nhất là khi sử dụng biểu thức có nguy cơ backtracking quá mức.

## 8. Bài tập tự kiểm tra

**Bài 1.** Dự đoán kết quả của hai dòng:

```java
Pattern p = Pattern.compile(",");
System.out.println(p.splitAsStream(",x,,y,").toList());
System.out.println(Arrays.toString(p.split(",x,,y,", -1)));
```

Đáp án: stream có các token `["", "x", "", "y"]`; mảng với `-1` có thêm `""` ở cuối. Khi in trực tiếp `List`/mảng, biểu diễn token trống bằng mắt thường dễ gây nhầm, nên có thể bọc từng phần bằng dấu ngoặc vuông khi debug.

**Bài 2.** Với `"  ;a; ;b;c"` và delimiter `";"`, hãy lấy hai token **không rỗng** đầu tiên sau khi `strip`. Đáp án:

```java
List<String> answer = Pattern.compile(";")
        .splitAsStream("  ;a; ;b;c")
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .limit(2)
        .toList();
System.out.println(answer); // [a, b]
```

**Bài 3.** Cần giữ chính xác ba cột của chuỗi `"a|b|"`, gồm cột cuối rỗng. Chọn `Pattern.compile(Pattern.quote("|")).split(input, -1)` để có `["a", "b", ""]`; dùng `splitAsStream` sẽ bỏ trường rỗng cuối.

## 9. Ghi nhớ

`Pattern.splitAsStream` phù hợp nhất khi **tách bằng regex rồi nối trực tiếp thao tác Stream**, đặc biệt khi chỉ cần một vài kết quả đầu. Nó duy trì thứ tự token, giữ phần rỗng ở giữa nhưng bỏ phần rỗng ở cuối. Kết hợp `filter/map/limit/findFirst` theo đúng thứ tự nghiệp vụ và chọn `Pattern.split(input, limit)` khi cần bảo toàn mọi cột hoặc gộp phần dữ liệu còn lại. Luôn phân biệt **tạo pipeline** với **terminal operation bắt đầu tiêu thụ**, và đừng suy diễn “lazy” thành cam kết hiệu năng trong mọi trường hợp.

**Tài liệu tham khảo:** [Java SE 17 — Pattern.splitAsStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html#splitAsStream(java.lang.CharSequence)), [Java SE 17 — Stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html), [Java SE 17 — String.split](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#split(java.lang.String,int)).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-splitting-009">Vì sao split đơn giản không đủ để phân tích CSV có dấu nháy</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `String.split()` đơn giản không đủ để phân tích CSV có dấu nháy

## 1. Vấn đề cốt lõi

CSV (Comma-Separated Values) thường dùng dấu phẩy để ngăn cách các trường, nhưng **dấu phẩy không phải lúc nào cũng là dấu phân cách**. Một trường được bao bởi dấu nháy kép (`"..."`) có thể chứa dấu phẩy, dấu xuống dòng và dấu nháy kép được mã hóa bằng hai dấu nháy liên tiếp (`""`). Vì vậy, không thể coi mọi dấu phẩy trong chuỗi là ranh giới trường.

Ví dụ, bản ghi:

~~~csv
101,"Nguyễn, An","Nói ""xin chào""",active
~~~

có đúng **4 trường**, lần lượt là:

1. `101`
2. `Nguyễn, An`
3. `Nói "xin chào"`
4. `active`

Nếu dùng `line.split(",")`, Java sẽ tách cả dấu phẩy nằm **bên trong** `"Nguyễn, An"`, khiến số trường và vị trí dữ liệu sai. `split` chỉ tìm vị trí khớp biểu thức chính quy; nó không theo dõi ngữ cảnh “đang ở trong hay ngoài dấu nháy”.

CSV không phải một định dạng có duy nhất một biến thể: RFC 4180 mô tả một quy ước phổ biến, còn dữ liệu thực tế có thể dùng dấu chấm phẩy, quy tắc khoảng trắng hoặc cách escape khác. Khi xử lý CSV cần biết **dialect** (quy ước định dạng) của nguồn dữ liệu.

## 2. Những tình huống `split` dễ xử lý sai

| Dòng CSV đầu vào | Các trường cần thu được | Vấn đề khi tách bằng dấu phẩy |
| --- | --- | --- |
| `a,b,c` | `a`, `b`, `c` | Trường hợp đơn giản: `split` hoạt động |
| `1,"Lan, An",3` | `1`, `Lan, An`, `3` | Dấu phẩy trong trường được nháy bị tách nhầm |
| `1,"a""b",3` | `1`, `a"b`, `3` | `split` không giải mã `""` thành `"` |
| `a,b,` | `a`, `b`, trường rỗng | `split(",")` mặc định bỏ trường rỗng ở cuối |
| `"Dòng 1\nDòng 2",ok` | Trường đầu chứa ký tự xuống dòng thật; trường sau là `ok` | Đọc từng dòng vật lý có thể cắt đôi một bản ghi |

Ở hàng cuối của bảng, `\n` dùng để **biểu diễn** một ký tự xuống dòng nằm trong cặp nháy; một tệp CSV thực có thể chứa dòng vật lý kế tiếp ngay giữa trường này.

### `split(",")` và `split(",", -1)` khác nhau thế nào?

Trong Java, `String.split(regex)` tương đương `String.split(regex, 0)`: các trường rỗng ở **cuối** kết quả bị loại bỏ. Dùng `limit = -1` giúp giữ chúng, nhưng **không** giải quyết dấu phẩy trong nháy hoặc giải mã dấu nháy kép.

~~~java
import java.util.Arrays;

public class SplitLimitDemo {
    public static void main(String[] args) {
        System.out.println(Arrays.toString("a,b,".split(",")));
        System.out.println(Arrays.toString("a,b,".split(",", -1)));

        String csv = "1,\"Lan, An\",3";
        String[] wrong = csv.split(",", -1);
        System.out.println(wrong.length);
        System.out.println(wrong[1]);
        System.out.println(wrong[2]);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 SplitLimitDemo.java
java SplitLimitDemo
~~~

Kết quả chính xác:

~~~text
[a, b]
[a, b, ]
4
"Lan
 An"
~~~

Giữ trường rỗng ở cuối là một phần của xử lý CSV, nhưng dùng `-1` vẫn tạo ra **4 mảnh** thay vì **3 trường** đối với `1,"Lan, An",3`. Cũng cần nhớ rằng đối số đầu tiên của `split` là **regex** chứ không phải chuỗi phân cách theo nghĩa đen; kể cả dùng `Pattern.quote(delimiter)` đúng cách cũng không khiến nó hiểu quy tắc nháy CSV.

## 3. Quy tắc nhận diện một trường CSV

Với biến thể phổ biến dùng dấu phẩy và dấu nháy kép:

- **Ngoài cặp nháy:** dấu phẩy kết thúc trường; dấu xuống dòng kết thúc bản ghi.
- **Bên trong cặp nháy:** dấu phẩy và dấu xuống dòng là **dữ liệu** thuộc cùng một trường.
- **Hai dấu nháy liền nhau** `""` ở bên trong trường được nháy biểu thị **một** dấu `"` trong giá trị.
- **Sau dấu nháy đóng:** với parser nghiêm ngặt, ký tự hợp lệ tiếp theo chỉ là dấu phẩy, ký tự kết thúc bản ghi hoặc hết dữ liệu. Dữ liệu như `"abc"x` là không hợp lệ.
- **Trường rỗng:** `a,,b` có trường thứ hai rỗng; `a,b,` có trường cuối rỗng; `""` là một trường được nháy nhưng có giá trị rỗng.
- **Kết thúc bản ghi:** `\r\n` thường dùng trong CSV kiểu RFC 4180; một số nguồn dùng `\n` hoặc `\r`. Parser phải phân biệt dấu xuống dòng bên ngoài nháy và bên trong nháy.

Một bộ phân tích hợp lệ phải **giữ trạng thái** theo từng ký tự hoặc dùng thư viện đã hiện thực các quy tắc đó; không thể quyết định chỉ từ chính ký tự dấu phẩy.

Có thể hình dung bốn trạng thái:

| Trạng thái | Ý nghĩa | Xử lý chính |
| --- | --- | --- |
| `START` | Đầu trường, chưa có nội dung | `"` mở trường có nháy; dấu phẩy tạo trường rỗng |
| `UNQUOTED` | Đang đọc trường không có nháy | Dấu phẩy hoặc hết bản ghi kết thúc trường; dấu `"` giữa trường là lỗi theo quy tắc nghiêm ngặt |
| `QUOTED` | Đang đọc trường trong nháy | Chấp nhận dấu phẩy, `\r` và `\n` như dữ liệu; `""` thêm một dấu `"` |
| `AFTER_QUOTE` | Đã đọc dấu nháy đóng | Chỉ cho phép dấu phẩy, hết bản ghi hoặc EOF |

## 4. Ví dụ Java 17+: phân tích đúng dấu nháy, xuống dòng và trường rỗng

Ví dụ dưới đây là **chương trình đầy đủ**, dùng bộ quét trạng thái cho một biến thể CSV cụ thể: dấu phân cách là `,`, dấu bao trường là `"`, escape dấu nháy bằng `""`, cho phép `CRLF`/`LF`/`CR` làm ranh giới bản ghi, và không bỏ qua khoảng trắng sau dấu nháy đóng. Mục đích là thấy rõ cơ chế; với tệp lớn hoặc dữ liệu không đáng tin cậy, nên dùng thư viện CSV đã được kiểm thử kỹ.

Lưu dưới tên `CsvExample.java`:

~~~java
import java.util.ArrayList;
import java.util.List;

public final class CsvExample {
    private enum State { START, UNQUOTED, QUOTED, AFTER_QUOTE }

    public static List<List<String>> parse(String input) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        State state = State.START;
        boolean lastWasRecordSeparator = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (state == State.QUOTED) {
                if (ch == '"') {
                    if (i + 1 < input.length()
                            && input.charAt(i + 1) == '"') {
                        field.append('"');
                        i++; // Hai dấu nháy trong CSV tạo một dấu nháy.
                    } else {
                        state = State.AFTER_QUOTE;
                    }
                } else {
                    field.append(ch); // Bao gồm dấu phẩy và xuống dòng.
                }
                lastWasRecordSeparator = false;
                continue;
            }

            if (ch == ',') {
                record.add(field.toString());
                field.setLength(0);
                state = State.START;
                lastWasRecordSeparator = false;
            } else if (ch == '\r' || ch == '\n') {
                record.add(field.toString());
                records.add(record);
                record = new ArrayList<>();
                field.setLength(0);
                state = State.START;
                lastWasRecordSeparator = true;

                // Ngoài nháy, CRLF chỉ kết thúc một bản ghi.
                if (ch == '\r' && i + 1 < input.length()
                        && input.charAt(i + 1) == '\n') {
                    i++;
                }
            } else if (state == State.AFTER_QUOTE) {
                throw new IllegalArgumentException(
                        "Ký tự sau dấu nháy đóng tại vị trí " + i);
            } else if (state == State.START && ch == '"') {
                state = State.QUOTED;
                lastWasRecordSeparator = false;
            } else if (ch == '"') {
                throw new IllegalArgumentException(
                        "Dấu nháy không hợp lệ tại vị trí " + i);
            } else {
                field.append(ch);
                state = State.UNQUOTED;
                lastWasRecordSeparator = false;
            }
        }

        if (state == State.QUOTED) {
            throw new IllegalArgumentException("Thiếu dấu nháy đóng");
        }

        // Không tạo bản ghi giả sau dấu xuống dòng kết thúc tệp.
        // Nhưng vẫn giữ trường rỗng cuối cùng nếu tệp kết thúc bằng ','.
        if (!input.isEmpty() && !lastWasRecordSeparator) {
            record.add(field.toString());
            records.add(record);
        }

        return records;
    }

    public static void main(String[] args) {
        String csv =
                "id,name,quote,note\r\n" +
                "1,\"Nguyễn, An\",\"Nói \"\"xin chào\"\"\","
                        + "\"Dòng 1\nDòng 2\"\r\n" +
                "2,Bình,,\r\n";

        List<List<String>> rows = parse(csv);
        System.out.println(rows.size());
        System.out.println(rows.get(1).get(1));
        System.out.println(rows.get(1).get(2));
        System.out.println(
                rows.get(1).get(3).replace("\n", "\\n"));
        System.out.println(rows.get(2).size());
        System.out.println(rows.get(2).get(3).isEmpty());
    }
}
~~~

Biên dịch và chạy (terminal có JDK 17 trở lên):

~~~bash
javac -encoding UTF-8 CsvExample.java
java CsvExample
~~~

`stdout`:

~~~text
3
Nguyễn, An
Nói "xin chào"
Dòng 1\nDòng 2
4
true
~~~

Giải thích kết quả:

1. Có **3 bản ghi logic**: một header và hai bản ghi dữ liệu. Trường `note` của bản ghi đầu chứa `\n` thật nhưng không tạo thêm bản ghi.
2. Trường `name` giữ nguyên dấu phẩy `Nguyễn, An`.
3. Hai dấu nháy kép `""` của CSV được giải mã thành một dấu nháy trong `Nói "xin chào"`.
4. Bản ghi thứ hai vẫn có **4 trường**, kể cả hai trường rỗng ở cuối.

Parser trên cố ý **nghiêm ngặt**: nó báo lỗi với nháy chưa đóng, nháy xuất hiện giữa trường không có nháy, hoặc ký tự bất hợp lệ ngay sau nháy đóng. Nó đọc toàn bộ `String` vào bộ nhớ, không phát hiện lỗi số cột không đồng nhất, không xử lý BOM, không cho tùy biến delimiter hay cách escape; đó là giới hạn rõ ràng của **ví dụ học cơ chế**, không phải một CSV parser sản xuất tổng quát.

## 5. Vì sao đọc từng dòng rồi `split` cũng sai?

Một **bản ghi CSV logic** có thể chiếm nhiều **dòng vật lý**:

~~~csv
id,comment,status
1,"Bắt đầu ở đây
và tiếp tục ở dòng tiếp theo",done
2,"Một dòng",pending
~~~

Ở đây chỉ có ba bản ghi (gồm header). Nếu gọi `BufferedReader.readLine()` rồi phân tích mỗi dòng như một bản ghi độc lập, giá trị `comment` của id `1` bị chặt đôi. `readLine()` bỏ ký tự xuống dòng, nên còn có nguy cơ làm mất ký tự gốc của trường nhiều dòng.

Để xử lý luồng lớn, parser phải đọc từ `Reader` và theo dõi trạng thái nháy **xuyên qua các lần đọc và các dòng vật lý**. Nếu dùng thư viện, ưu tiên API đọc từng bản ghi CSV logic. Cách “gom dòng cho đến khi đếm được số dấu nháy chẵn” cũng dễ sai nếu không xét quy tắc `""` và đầu vào lỗi.

## 6. Khi nào `split` đủ dùng và khi nào nên dùng thư viện?

`split` phù hợp nếu **đầu vào đã được bảo đảm** là văn bản phân tách đơn giản, không bao giờ chứa dấu phân cách trong giá trị, không dùng cú pháp nháy/escape CSV, không có bản ghi nhiều dòng, và cách xử lý trường rỗng đã được xác định. Đó là giả định về **định dạng đầu vào**, không thể suy ra chỉ vì tệp có đuôi `.csv`.

Với CSV do người dùng tải lên, CSV xuất từ Excel hoặc hệ thống khác, tệp có trường tự do như tên/địa chỉ/mô tả, hoặc dữ liệu cần độ tin cậy cao, hãy dùng thư viện chuyên dụng như **Apache Commons CSV** hoặc **uniVocity Parsers**. Cấu hình rõ delimiter, quote, escape, kiểu kết thúc bản ghi, header và chính sách báo lỗi theo nguồn dữ liệu; không mặc định mọi CSV đều giống nhau.

Khi đưa CSV vào ứng dụng thực tế, còn phải cân nhắc:

- **Encoding và BOM:** giải mã đúng charset (thường UTF-8); nếu có BOM ở đầu tệp, quyết định cách loại bỏ trước khi so khớp tên cột.
- **Header và số cột:** kiểm tra số cột dự kiến, tên header trùng/thiếu, bản ghi có số trường bất thường; parser đúng cú pháp chưa chắc bảo đảm dữ liệu đúng nghiệp vụ.
- **Rỗng và thiếu:** `,,`, `""` và trường vắng mặt do bản ghi thiếu cột có thể mang ý nghĩa khác nhau; phải quy định khi ánh xạ sang đối tượng Java.
- **Khoảng trắng:** `" abc "` giữ khoảng trắng bên trong nháy; tự động `trim()` có thể làm thay đổi dữ liệu. Một số dialect cho phép khoảng trắng sau nháy đóng, nhưng ví dụ Java ở trên không cho phép.
- **Dòng lỗi và kích thước:** chọn chính sách dừng/bỏ qua bản ghi lỗi, giới hạn độ dài trường và kích thước tệp để tránh dùng quá nhiều bộ nhớ.
- **Xuất CSV:** khi tạo CSV để mở bằng ứng dụng bảng tính, dữ liệu bắt đầu bằng `=`, `+`, `-`, `@` có thể bị hiểu là công thức. Đây là vấn đề an toàn ở **khâu xuất/nhập bảng tính**, ngoài việc parse CSV cho đúng.

## 7. Checklist tự kiểm tra

Một parser CSV cần được kiểm thử tối thiểu với các đầu vào:

| Đầu vào | Kết quả mong đợi |
| --- | --- |
| `a,b,c` | Một bản ghi, 3 trường |
| `a,,c` | Trường giữa rỗng |
| `a,b,` | Giữ trường cuối rỗng |
| `"a,b",c` | Trường đầu là `a,b` |
| `"a""b",c` | Trường đầu là `a"b` |
| `"a\nb",c` (xuống dòng thật trong nháy) | Một bản ghi; trường đầu chứa xuống dòng |
| `a,b\r\nc,d` | Hai bản ghi với CRLF |
| `"abc` | Báo lỗi nháy chưa đóng |
| `"abc"x,y` | Báo lỗi ký tự sau nháy đóng |
| `a"b,c` | Báo lỗi nháy giữa trường không nháy |

**Kết luận:** `String.split(",")` nhìn thấy dấu phẩy; CSV parser phải hiểu **ngữ cảnh của dấu phẩy** và **trạng thái nháy**. `split(",", -1)` chỉ sửa việc mất các trường rỗng cuối dòng, không thay thế được phân tích CSV có nháy, escape và bản ghi nhiều dòng.


</details>

- [Quay lại đầu trang](#back-to-top)
