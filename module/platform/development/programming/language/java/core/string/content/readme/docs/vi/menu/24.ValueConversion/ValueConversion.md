<a id="back-to-top"></a>

# Chuyển đổi giá trị

## Menu
- [String.valueOf với primitive và Object](#value-conversion-001)
- [Phân biệt valueOf(Object) với valueOf(char&#91;&#93;) khi truyền null](#value-conversion-002)
- [copyValueOf và tạo chuỗi từ mảng ký tự](#value-conversion-003)
- [toString của String và của đối tượng khác](#value-conversion-004)
- [Integer.parseInt, parseLong và cơ số](#value-conversion-005)
- [BigInteger, BigDecimal và giới hạn biểu diễn qua số thực](#value-conversion-006)
- [NumberFormat khi phân tích số theo locale](#value-conversion-007)
- [Kiểm tra toàn chuỗi và xử lý dữ liệu không hợp lệ](#value-conversion-008)

## <a id="value-conversion-001">String.valueOf với primitive và Object</a>

<details>
<summary>Xem chi tiết</summary>

# String.valueOf với primitive và Object trong Java

## 1. Mục tiêu bài học

`String.valueOf(...)` là nhóm **phương thức tĩnh** của `java.lang.String` dùng để tạo biểu diễn dạng chuỗi từ một giá trị. Nó đặc biệt hữu ích khi chuyển `boolean`, `char`, các kiểu số nguyên, số thực hoặc một đối tượng sang `String` để hiển thị, ghép thông báo hay đưa vào một API nhận chuỗi.

Sau bài này, bạn sẽ:
- Phân biệt các overload `valueOf` dành cho **primitive**, `Object` và `char[]`.
- Hiểu khi nào giá trị được biểu diễn bằng chuỗi có sẵn, khi nào `toString()` được gọi.
- Giải thích vì sao `String.valueOf((Object) null)` trả `"null"`, còn `String.valueOf((char[]) null)` gây lỗi.
- Biết cách biểu diễn mảng và dữ liệu số khi `valueOf` không đáp ứng định dạng mong muốn.
- Chọn đúng giữa `String.valueOf`, `Objects.toString`, `Integer.toString` và `String.format` theo mục đích.

## 2. Các overload của String.valueOf

Những khai báo thường gặp trong Java 17:

```java
public static String valueOf(boolean b);
public static String valueOf(char c);
public static String valueOf(int i);
public static String valueOf(long l);
public static String valueOf(float f);
public static String valueOf(double d);

public static String valueOf(Object obj);
public static String valueOf(char[] data);
public static String valueOf(char[] data, int offset, int count);
```

Java chọn overload dựa trên **kiểu biểu thức lúc biên dịch** cùng các quy tắc phân giải lời gọi phương thức. Sau đó mỗi overload tạo nội dung chuỗi tương ứng.

| Giá trị đầu vào | Overload điển hình | Ví dụ | Kết quả |
| --- | --- | --- | --- |
| `boolean` | `valueOf(boolean)` | `String.valueOf(true)` | `"true"` |
| `char` | `valueOf(char)` | `String.valueOf('J')` | `"J"` |
| `byte`, `short` | `valueOf(int)` qua mở rộng kiểu | `String.valueOf((byte) 7)` | `"7"` |
| `int` | `valueOf(int)` | `String.valueOf(-42)` | `"-42"` |
| `long` | `valueOf(long)` | `String.valueOf(5000000000L)` | `"5000000000"` |
| `float` | `valueOf(float)` | `String.valueOf(1.25f)` | `"1.25"` |
| `double` | `valueOf(double)` | `String.valueOf(2.5d)` | `"2.5"` |
| `Object` | `valueOf(Object)` | `String.valueOf((Object) null)` | `"null"` |
| `char[]` | `valueOf(char[])` | `String.valueOf(new char[]{'A','B'})` | `"AB"` |

**Không có overload dành riêng cho `byte` và `short`**; đối số primitive tương ứng có thể mở rộng sang `int`. Các kiểu wrapper như `Integer`, `Boolean` hoặc `Double` là **đối tượng** khi bạn truyền một biến có kiểu wrapper; Java có thể chọn overload `Object` thông qua chuyển đổi tham chiếu, và biểu diễn đầu ra thông thường vẫn giống dạng chuỗi của giá trị.

Một trường hợp riêng là **mảng ký tự `char[]`**. Đây không phải kiểu primitive đơn lẻ `char` và được xử lý bằng overload chuyên biệt.

## 3. Chuyển các kiểu primitive

### Boolean: chỉ có "true" và "false"

```java
String yes = String.valueOf(true);    // "true"
String no = String.valueOf(false);    // "false"
```

Đây là hai chuỗi chữ thường theo quy tắc của Java, không phụ thuộc locale. `String.valueOf(boolean)` không dịch sang “Có/Không” hay “Đúng/Sai”; việc bản địa hóa phải thực hiện riêng.

### Char: tạo chuỗi từ một ký tự

```java
char initial = 'K';
String text = String.valueOf(initial); // "K"
```

Một `char` trong Java là một **đơn vị mã UTF-16**, không phải lúc nào cũng đại diện cho một ký tự Unicode hoàn chỉnh. Những ký tự nằm ngoài Basic Multilingual Plane (ví dụ nhiều emoji) cần cặp surrogate; nếu có một code point Unicode, có thể dùng `Character.toChars(codePoint)` rồi tạo chuỗi từ mảng `char[]`.

```java
int codePoint = 0x1F600;
String emoji = String.valueOf(Character.toChars(codePoint));
// Một chuỗi chứa emoji U+1F600
```

### Số nguyên: dạng thập phân có dấu khi cần

```java
String quantity = String.valueOf(125);       // "125"
String negative = String.valueOf(-12L);      // "-12"
String small = String.valueOf((short) 8);    // "8"
```

Định dạng mặc định của `valueOf` cho số nguyên là **hệ thập phân**, không tự thêm dấu phân cách hàng nghìn hoặc số 0 ở đầu. Nếu cần cơ số khác, dùng API phù hợp:

```java
String hex = Integer.toHexString(255); // "ff"
String binary = Integer.toBinaryString(5); // "101"
String base16 = Integer.toString(255, 16); // "ff"
```

### Số thực: biểu diễn mặc định, không phải khuôn mẫu hiển thị cố định

```java
String a = String.valueOf(1.25f); // "1.25"
String b = String.valueOf(2.0d);  // "2.0"
String c = String.valueOf(Double.NaN); // "NaN"
String d = String.valueOf(Double.POSITIVE_INFINITY); // "Infinity"
```

Các overload cho `float` và `double` theo quy tắc biểu diễn chuỗi tương ứng của `Float` và `Double`. Tùy giá trị, kết quả có thể dùng ký pháp khoa học, và số chữ số sau dấu thập phân không phải một số cố định.

Đừng dùng `String.valueOf(12.5)` khi yêu cầu là luôn đúng **hai chữ số thập phân** hoặc dấu nhóm hàng nghìn theo ngôn ngữ. Khi đó dùng `String.format(Locale.US, "%.2f", 12.5)` để được `"12.50"` hoặc `NumberFormat` cho giao diện có locale.

`String.valueOf` chỉ quyết định **biểu diễn**, không làm thay đổi độ chính xác của giá trị `float`/`double` và không giải quyết sai số số học dấu chấm động.

## 4. `String.valueOf(Object)`: biểu diễn đối tượng và xử lý null

Khi đối số có **kiểu tĩnh `Object`**, Java chọn `valueOf(Object)`. Overload này trả chữ `"null"` nếu tham chiếu bằng `null`; nếu khác `null`, nó gọi `toString()` theo lớp thực tế của đối tượng:

```java
Object count = Integer.valueOf(42);
Object missing = null;
System.out.println(String.valueOf(count));   // 42
System.out.println(String.valueOf(missing)); // null
```

Vì kết quả phụ thuộc `toString()`, một đối tượng có thể in mô tả tùy chỉnh hoặc dạng mặc định như `Type@hexHash`. Đây là cách biểu diễn văn bản, không phải serialization hay ép kiểu thành `String`. Gọi `obj.toString()` trực tiếp trên `null` sẽ ném `NullPointerException`.

**Bẫy overload:** `String.valueOf(null)` với **literal null không ép kiểu** chọn overload `char[]` cụ thể hơn `Object`, nên ném `NullPointerException`. Viết `String.valueOf((Object) null)` nếu muốn chữ `"null"`. Bài riêng về `null` của chương giải thích cơ chế phân giải overload theo kiểu tĩnh cùng các trường hợp ép kiểu.

## 5. Overload `char[]`: lấy nội dung ký tự

Khác `Object`, `char[]` được `String.valueOf` xử lý như **dữ liệu văn bản**:

```java
char[] chars = {'J', 'a', 'v', 'a'};
String entire = String.valueOf(chars);       // "Java"
String middle = String.valueOf(chars, 1, 2); // "av"
```

Với overload ba tham số, `offset` là chỉ số bắt đầu, `count` là **số phần tử `char`** lấy từ mảng. Mảng `null` gây `NullPointerException`; phạm vi sai gây `IndexOutOfBoundsException`. Chuỗi đã tạo không thay đổi khi sau đó sửa mảng nguồn. Dữ liệu `char[]` là các đơn vị UTF-16, vì vậy muốn cắt theo Unicode code point phải xác định ranh giới đúng.

`String.valueOf((Object) chars)` chuyển sang overload `Object` và in biểu diễn đối tượng mảng như `[C@...`, **không** phải `"Java"`. Khi tập trung vào thao tác sao chép và lấy đoạn mảng, `String.copyValueOf(char[])` và `new String(char[])` cũng có hợp đồng tạo nội dung chuỗi tương ứng.

## 6. Mảng khác không tự chuyển thành danh sách phần tử

`valueOf(char[])` là overload đặc biệt. Với mảng như `int[]`, `double[]` hoặc `String[]`, lời gọi `valueOf` thông thường nhận `Object`, nên kết quả mặc định mô tả **đối tượng mảng**, không liệt kê phần tử:

```java
int[] ids = {1, 2, 3};
System.out.println(String.valueOf(ids).startsWith("[I@")); // true
System.out.println(java.util.Arrays.toString(ids));        // [1, 2, 3]

int[][] grid = {{1, 2}, {3, 4}};
System.out.println(java.util.Arrays.deepToString(grid));   // [[1, 2], [3, 4]]
```

Dùng `Arrays.toString` để quan sát phần tử mảng một chiều và `Arrays.deepToString` cho mảng đối tượng lồng nhau. Đó là quyết định **định dạng đầu ra**, khác với `valueOf` chỉ dựa trên `toString()` của đối tượng truyền vào.

## 7. Chương trình Java 17+ hoàn chỉnh

Lưu mã sau vào tệp `ValueOfDemo.java` bằng UTF-8:

```java
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public class ValueOfDemo {
    private record Product(String code, int quantity) {
        @Override
        public String toString() {
            return code + " x " + quantity;
        }
    }

    public static void main(String[] args) {
        System.out.println("boolean: " + String.valueOf(true));
        System.out.println("char: " + String.valueOf('J'));
        System.out.println("int: " + String.valueOf(-42));
        System.out.println("long: " + String.valueOf(5000000000L));
        System.out.println("float: " + String.valueOf(1.25f));
        System.out.println("double: " + String.valueOf(2.5));

        Product p = new Product("P01", 3);
        System.out.println("object: " + String.valueOf(p));

        Object missing = null;
        System.out.println("null object: " + String.valueOf(missing));
        System.out.println("fallback: " +
                Objects.toString(missing, "(trống)"));

        char[] data = {'J', 'a', 'v', 'a'};
        System.out.println("char array: " + String.valueOf(data));
        System.out.println("char slice: " +
                String.valueOf(data, 1, 2));

        String copy = String.valueOf(data);
        data[0] = 'Z';
        System.out.println("copy preserved: " + copy);

        int[] ids = {1, 2, 3};
        System.out.println("array items: " + Arrays.toString(ids));

        System.out.println("formatted: " +
                String.format(Locale.US, "%.2f", 2.5));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 ValueOfDemo.java
java ValueOfDemo
```

Stdout chính xác:

```text
boolean: true
char: J
int: -42
long: 5000000000
float: 1.25
double: 2.5
object: P01 x 3
null object: null
fallback: (trống)
char array: Java
char slice: av
copy preserved: Java
array items: [1, 2, 3]
formatted: 2.50
```

Các dòng minh họa cùng một họ phương thức nhưng nhiều kiểu đầu vào. Ví dụ đối tượng `Product` dùng phương thức `toString()` riêng; `char[]` tạo chuỗi từ nội dung ký tự; `int[]` được in đúng phần tử bằng `Arrays.toString`; `String.format` tạo hai chữ số thập phân, trái với `String.valueOf(2.5)`.

## 8. Chọn giữa `valueOf`, `toString` và `Objects.toString`

| Cách gọi | Đối tượng không null | Đối tượng null |
| --- | --- | --- |
| `String.valueOf(obj)` khi `obj` có kiểu tĩnh `Object` | Dùng `obj.toString()` | Chuỗi `"null"` |
| `obj.toString()` | Gọi trực tiếp `toString()` | `NullPointerException` |
| `Objects.toString(obj)` | Dùng `toString()` | Chuỗi `"null"` |
| `Objects.toString(obj, fallback)` | Dùng `toString()` | Dùng `fallback` (có thể là null) |

Nếu muốn một thông điệp dự phòng có chủ ý, `Objects.toString(value, "(trống)")` rõ nghĩa hơn chữ `"null"`. Với biến đã là `String`, dùng trực tiếp biến thường đơn giản nhất; `String.toString()` trả chính đối tượng chuỗi đó. Bài riêng về `toString()` giải thích chi tiết override, mảng và biểu diễn của từng lớp.

`String.valueOf(obj)` **không tương đương ép kiểu `(String) obj`**: phép ép kiểu yêu cầu đối tượng runtime thật sự là `String` tương thích, trong khi `valueOf` tạo biểu diễn văn bản của đối tượng bất kỳ.

## 9. valueOf trong phép nối chuỗi và StringBuilder

Khi nối chuỗi với toán tử `+`, Java sử dụng quy tắc chuyển đổi sang chuỗi tương ứng với kiểu đối số. Trong nhiều tình huống, bạn **không cần viết `String.valueOf(...)` tường minh**:

```java
int count = 3;
String a = "Số lượng: " + count;
String b = "Số lượng: " + String.valueOf(count);
// Cả a và b đều là "Số lượng: 3"
```

Trình biên dịch/runtime có thể tối ưu phép nối chuỗi; đừng giả định mã bytecode của mọi phiên bản Java đều gọi đúng một overload `String.valueOf`. Điều cần hiểu ở mức ngôn ngữ là **quy tắc kết quả**, không phụ thuộc một cách cài đặt bytecode cụ thể.

Với nhiều bước ghép trong vòng lặp, `StringBuilder` hữu ích:

```java
StringBuilder builder = new StringBuilder();
builder.append("ID=").append(12).append(", active=").append(true);
String result = builder.toString();
// "ID=12, active=true"
```

`StringBuilder.append(...)` cũng có overload cho primitive và Object. Không cần chuyển từng giá trị bằng `String.valueOf` trước rồi mới `append`, trừ khi bạn muốn kiểm soát biểu diễn cụ thể.

## 10. Khi nào cần formatter hoặc converter chuyên biệt?

### Giá trị số có yêu cầu trình bày

```java
import java.util.Locale;

double amount = 1234.5;

String raw = String.valueOf(amount); // "1234.5"
String fixed = String.format(Locale.US, "%,.2f", amount);
// "1,234.50"
```

`String.valueOf` không tự phân nhóm hàng nghìn, không tự chọn đơn vị tiền tệ và không theo locale. Với số tiền, thường cần `BigDecimal` để tính toán chính xác và `NumberFormat` hoặc một format policy rõ ràng để hiển thị.

### Chuyển String ngược lại thành primitive

`String.valueOf(42)` chuyển **int → String**, còn `Integer.parseInt("42")` chuyển **String → int**; đây là hai hướng khác nhau.

```java
String text = String.valueOf(42); // "42"
int back = Integer.parseInt(text); // 42
```

Nếu dữ liệu không hợp lệ, `Integer.parseInt(...)` có thể ném `NumberFormatException`. `valueOf` không kiểm tra xem một chuỗi bất kỳ có phải là số hợp lệ không, vì bản thân nó đang chuyển từ một giá trị có kiểu sang văn bản.

### Chuyển String thành đối tượng nghiệp vụ

Đối với đối tượng có `toString()` tùy biến, không mặc nhiên có phép đảo `String.valueOf(object)` thành lại object. Muốn chuyển ngược, cần parser/factory thích hợp hoặc định dạng serialization có quy tắc rõ ràng.

## 11. Những lỗi phổ biến

**1. Gọi `String.valueOf(null)` và kỳ vọng `"null"`.** Vì có overload `char[]`, biểu thức literal null có thể chọn overload đó và ném `NullPointerException`. Viết `String.valueOf((Object) null)` hoặc dùng biến `Object` nếu mục tiêu là văn bản `"null"`.

**2. Cho rằng mọi mảng đều được in phần tử.** `char[]` có overload riêng, còn `int[]`/`String[]` dùng `Object.toString()`. Hãy chọn `Arrays.toString` hoặc `Arrays.deepToString` khi cần nhìn thấy phần tử.

**3. Dùng `toString()` trực tiếp khi dữ liệu có thể null.** `String.valueOf((Object) ref)` an toàn trước null theo nghĩa trả `"null"`; `ref.toString()` có thể ném `NullPointerException`.

**4. Cho rằng `String.valueOf(double)` luôn có hai chữ số thập phân.** Số thực được biểu diễn theo quy tắc mặc định. Muốn fixed precision, dấu nhóm hoặc locale, cần formatter.

**5. Dùng `String.valueOf(obj)` như thao tác ép kiểu.** Phương thức trả String biểu diễn đối tượng; nó không trả về đối tượng ban đầu được ép thành String.

**6. Để `toString()` của đối tượng chứa dữ liệu không mong muốn.** `valueOf(Object)` tuân theo `toString()` và có thể in tên lớp + mã băm, hoặc nội dung nhạy cảm do lớp tự định nghĩa. Hãy thiết kế `toString()` có chủ đích và thận trọng khi log.

**7. Trộn biểu diễn kỹ thuật và biểu diễn cho người dùng.** `true/false`, dấu thập phân và cách hiển thị object của `valueOf` không tự thích ứng ngôn ngữ hay quy tắc giao diện.

**8. Nhầm tham số `count` với chỉ số cuối khi cắt `char[]`.** `String.valueOf(chars, 1, 2)` lấy **2 ký tự**, bắt đầu từ index `1`.

## 12. Bài tập tự luyện

### Bài 1. Dự đoán kết quả

```java
String a = String.valueOf(false);
String b = String.valueOf((byte) -5);
String c = String.valueOf(10.0d);
String d = String.valueOf('Z');
```

Đáp án: `a = "false"`, `b = "-5"`, `c = "10.0"`, `d = "Z"`.

### Bài 2. Phân biệt null

Xét các lời gọi sau (không thực hiện đồng thời nếu dòng có thể ném ngoại lệ):

```java
Object x = null;
char[] y = null;

String a = String.valueOf(x);
String b = String.valueOf((Object) null);
// String c = String.valueOf(y);
// String d = String.valueOf(null);
```

Đáp án: `a` và `b` là chuỗi `"null"`. Các lời gọi ở hai dòng cuối, nếu bỏ comment, có thể ném `NullPointerException` vì overload `char[]` được chọn.

### Bài 3. Cắt mảng ký tự

Cho `char[] chars = {'s','t','r','i','n','g'};`. Tạo chuỗi `"rin"` mà không cần vòng lặp.

```java
char[] chars = {'s', 't', 'r', 'i', 'n', 'g'};
String answer = String.valueOf(chars, 2, 3);
// "rin"
```

### Bài 4. In nội dung mảng số nguyên

Vì sao `String.valueOf(new int[]{10, 20})` không trả `"[10, 20]"`?

Đáp án: không có overload `int[]`; mảng được chuyển qua `Object` và biểu diễn mặc định không liệt kê phần tử. Dùng:

```java
String result = java.util.Arrays.toString(new int[]{10, 20});
// "[10, 20]"
```

### Bài 5. Định dạng số thực

Yêu cầu hiển thị `2.5` thành `"2.50"` trên mọi máy, cách nào phù hợp?

```java
String result = String.format(
        java.util.Locale.US, "%.2f", 2.5);
// "2.50"
```

Lý do: `valueOf(double)` không có tham số số chữ số thập phân, còn formatter có.

## 13. Ghi nhớ

- **`String.valueOf(...)` là phương thức tĩnh**, trả chuỗi biểu diễn giá trị đầu vào.
- Có overload cho **boolean, char, int, long, float, double, Object, char[]** và một đoạn `char[]`.
- **`byte`/`short` primitive** có thể đi qua `valueOf(int)` bằng mở rộng kiểu.
- **`String.valueOf(Object)`**: khác null thì dùng `toString()`; null thì trả chuỗi chữ `"null"`.
- **Literal `null` không ép kiểu** có thể chọn `char[]` và gây `NullPointerException`. Khi cần overload Object, hãy chỉ rõ kiểu.
- **`char[]`** được chuyển từ các ký tự; **mảng khác** không tự in phần tử. Dùng `Arrays.toString`/`deepToString` khi thích hợp.
- **`valueOf` không phải công cụ format theo locale, serialize dữ liệu hay parse chuỗi.** Chọn `String.format`, `NumberFormat`, `Integer.parseInt` hoặc serializer phù hợp khi có yêu cầu đó.

Tài liệu tham khảo: [Java SE 17 — String.valueOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#valueOf(java.lang.Object)), [Java SE 17 — Object.toString](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#toString()), [Java SE 17 — Arrays](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html), [Java SE 17 — Objects.toString](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#toString(java.lang.Object,java.lang.String)).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-conversion-002">Phân biệt valueOf(Object) với valueOf(char&#91;&#93;) khi truyền null</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `String.valueOf(Object)` và `String.valueOf(char[])` khi truyền `null`

Cùng một lời gọi `String.valueOf`, cùng dữ liệu `null`, nhưng **kiểu đối số ở thời điểm biên dịch** quyết định overload nào được gọi. Đây là một bẫy dễ gặp trong Java:

```java
String a = String.valueOf((Object) null); // "null"
String b = String.valueOf((char[]) null); // NullPointerException
String c = String.valueOf(null);          // NullPointerException
```

Trong ba câu lệnh trên, `a` được gán thành công. Hai câu lệnh còn lại biên dịch được nhưng **ném `NullPointerException` ở runtime**. Điều này xuất phát từ *overload resolution* của Java chứ không phải do giá trị `null` thay đổi.

## 1. Hai overload quan trọng của `String.valueOf`

`String` có nhiều phương thức static tên `valueOf` để đổi dữ liệu thành chuỗi. Khi truyền `null`, hai overload nhận kiểu tham chiếu dưới đây là trọng tâm:

```java
public static String valueOf(Object obj);
public static String valueOf(char[] data);
```

Các overload dành cho `boolean`, `char`, `int`, `long`, `float` và `double` nhận **kiểu nguyên thủy**. Giá trị `null` không thể chuyển sang kiểu nguyên thủy để gọi trực tiếp, nên chúng không cạnh tranh khi đối số là literal `null`.

| Overload | Với đối số khác `null` | Với `null` |
| --- | --- | --- |
| `valueOf(Object obj)` | Trả về kết quả `obj.toString()` | Trả về **chuỗi gồm bốn ký tự `"null"`** |
| `valueOf(char[] data)` | Tạo chuỗi gồm nội dung các ký tự của mảng | Ném **`NullPointerException`** |

### `valueOf(Object)` xử lý `null` đặc biệt

Về mặt ý nghĩa, overload `Object` hoạt động tương tự:

```java
static String objectToText(Object obj) {
    return (obj == null) ? "null" : obj.toString();
}
```

Đây là **mã minh họa hành vi**, không phải bản sao mã nguồn JDK. Khi đối số là `null`, kết quả là đối tượng `String` mang nội dung chữ `null`, **không phải tham chiếu `null`**.

```java
String result = String.valueOf((Object) null);
System.out.println(result.equals("null")); // true
System.out.println(result == null);        // false
```

### `valueOf(char[])` cần một mảng thật

Overload nhận `char[]` dùng các phần tử của mảng ký tự để tạo một `String` mới:

```java
char[] letters = {'J', 'a', 'v', 'a'};
String text = String.valueOf(letters);
System.out.println(text); // Java
```

Nếu `data` là `null`, không có mảng để đọc. Phương thức **không tự chuyển `null` thành chữ `"null"`**, mà ném `NullPointerException`. Truyền mảng rỗng `new char[0]` thì hợp lệ và tạo chuỗi rỗng `""`: mảng rỗng khác với tham chiếu mảng `null`.

## 2. Vì sao `String.valueOf(null)` gọi overload `char[]`?

Java quyết định phương thức nào được gọi dựa trên **kiểu của biểu thức đối số tại thời điểm biên dịch**, theo các quy tắc lựa chọn overload của ngôn ngữ Java.

Với lời gọi:

```java
String.valueOf(null)
```

Literal `null` có thể được dùng ở vị trí đối số của phương thức nhận **kiểu tham chiếu**. Trong hai overload có thể áp dụng:

```java
String.valueOf(Object obj);
String.valueOf(char[] data);
```

`char[]` là một **kiểu tham chiếu con của `Object`**: mọi mảng Java đều là đối tượng và có thể được gán cho biến kiểu `Object`. Vì vậy `valueOf(char[])` được xem là **cụ thể hơn (more specific)** so với `valueOf(Object)`.

Java chọn phương thức cụ thể nhất trong các overload áp dụng được. Vì thế:

```text
Đối số: null literal
    |
    +-- valueOf(Object) : áp dụng được
    |
    +-- valueOf(char[]) : áp dụng được, cụ thể hơn Object
                              |
                              +--> được chọn lúc biên dịch
                                    |
                                    +--> thực thi với mảng null
                                              |
                                              +--> NullPointerException
```

**Không có lỗi “ambiguous method call”** ở đây vì Java có một lựa chọn cụ thể hơn. Cũng **không có bước thử `Object` sau khi overload `char[]` ném lỗi**: overload đã được xác định trước khi thực thi.

Lưu ý: `char[]` không phải kiểu con của `String`. Java không có overload `String.valueOf(String)`; việc ép kiểu `(String) null` sẽ đi qua `Object`. Nếu một API tự định nghĩa có hai overload nhận các kiểu tham chiếu không liên quan và đều áp dụng được, literal `null` có thể khiến lời gọi trở nên mơ hồ; đây là câu chuyện khác với cặp `Object` và `char[]`.

## 3. So sánh mọi cách truyền `null` thường gặp

| Biểu thức Java | Overload được chọn khi biên dịch | Kết quả |
| --- | --- | --- |
| `String.valueOf(null)` | `char[]` | `NullPointerException` |
| `String.valueOf((Object) null)` | `Object` | `"null"` |
| `String.valueOf((char[]) null)` | `char[]` | `NullPointerException` |
| `String.valueOf((String) null)` | `Object` | `"null"` |
| `String.valueOf((int[]) null)` | `Object` | `"null"` |
| `Object o = null; String.valueOf(o)` | `Object` | `"null"` |
| `char[] chars = null; String.valueOf(chars)` | `char[]` | `NullPointerException` |
| `String s = null; String.valueOf(s)` | `Object` | `"null"` |

`(Object) null` là **ép kiểu tường minh**: ta nói với trình biên dịch rằng biểu thức truyền vào mang kiểu `Object`, nên overload `char[]` không còn áp dụng được.

`(char[]) null` ép kiểu theo chiều ngược lại: bắt trình biên dịch coi đối số là `char[]`, vì vậy chọn overload `char[]` và gặp lỗi khi dùng mảng null.

`(String) null` có kiểu tĩnh là `String`, vốn là con của `Object` nhưng **không** phải con của `char[]`. Do đó nó được xử lý như đối số `Object`.

Tương tự, `int[]` là một loại mảng nhưng **không thể** chuyển sang `char[]`; vì thế `String.valueOf((int[]) null)` cũng gọi overload `Object`.

**Quan trọng:** Java không dò kiểu của một đối tượng ở runtime để đổi từ overload `Object` sang `char[]`. Chọn overload khác với cơ chế *override* (đa hình phương thức instance).

## 4. Ví dụ Java 17+ chạy độc lập

Tạo file `ValueOfNullOverloadsDemo.java`:

```java
import java.util.function.Supplier;

public class ValueOfNullOverloadsDemo {

    private static void check(String label, Supplier<String> action) {
        try {
            System.out.println(label + " -> " + action.get());
        } catch (NullPointerException ex) {
            System.out.println(label + " -> NullPointerException");
        }
    }

    public static void main(String[] args) {
        check("literal", () -> String.valueOf(null));
        check("(Object)null", () -> String.valueOf((Object) null));
        check("(char[])null", () -> String.valueOf((char[]) null));
        check("(String)null", () -> String.valueOf((String) null));

        Object obj = null;
        char[] chars = null;
        String string = null;

        check("Object variable", () -> String.valueOf(obj));
        check("char[] variable", () -> String.valueOf(chars));
        check("String variable", () -> String.valueOf(string));

        char[] data = {'J', 'a', 'v', 'a'};
        System.out.println("char[] content -> " + String.valueOf(data));

        // Không in mã băm đối tượng vì nó có thể đổi giữa các lần chạy.
        String objectText = String.valueOf((Object) data);
        System.out.println("Object-char[] uses array identity -> "
                + objectText.startsWith("[C@"));
    }
}
```

Dùng JDK 17 hoặc mới hơn để chạy:

```bash
javac ValueOfNullOverloadsDemo.java
java ValueOfNullOverloadsDemo
```

Kết quả stdout:

```text
literal -> NullPointerException
(Object)null -> null
(char[])null -> NullPointerException
(String)null -> null
Object variable -> null
char[] variable -> NullPointerException
String variable -> null
char[] content -> Java
Object-char[] uses array identity -> true
```

Hàm `check` chủ động bắt `NullPointerException` **chỉ để minh họa và cho phép chương trình tiếp tục chạy** qua mọi trường hợp. Trong chương trình thực tế, hãy kiểm tra dữ liệu trước khi gọi nếu `null` có thể xuất hiện; không cần dùng exception như một nhánh xử lý thông thường.

Không đưa nội dung `ex.getMessage()` hay stack trace vào stdout vì thông báo lỗi cụ thể có thể thay đổi tùy phiên bản JDK. Ví dụ giữ kết quả rõ ràng và ổn định bằng cách in tên loại exception.

## 5. Điều gì xảy ra với mảng `char[]` không phải `null`?

Sự khác biệt giữa hai overload tồn tại **ngay cả khi mảng có nội dung**:

```java
char[] data = {'O', 'K'};

String byChars = String.valueOf(data);          // overload char[]
String byObject = String.valueOf((Object) data); // overload Object

System.out.println(byChars);                    // OK
System.out.println(byObject.startsWith("[C@")); // true
```

Khi gọi `valueOf(char[])`, Java chuyển **các phần tử ký tự** thành chuỗi. Khi gọi `valueOf(Object)` với một mảng ký tự, Java gọi `toString()` của chính **đối tượng mảng**. Mảng Java mặc định không ghi đè `Object.toString()` để liệt kê các phần tử, nên thường được biểu diễn bằng tên kiểu và mã nhận dạng, ví dụ `[C@...`, trong đó `[C` là tên kiểu JVM cho `char[]` và phần sau có thể thay đổi.

Vì vậy, ép kiểu `(Object) data` là cách **chọn overload `Object`** chứ không phải cách chuyển nội dung `char[]` thành chuỗi. Nó chỉ hữu ích trong ngữ cảnh cần dùng quy tắc chuyển đổi kiểu `Object`, đặc biệt khi muốn `null` được biểu diễn thành chữ `"null"`.

Ngoài ra, `valueOf(char[])` tạo nội dung chuỗi độc lập với mảng ban đầu:

```java
char[] data = {'O', 'K'};
String text = String.valueOf(data);
data[0] = 'N';
System.out.println(text); // OK
```

Mảng có thể thay đổi, còn `String` là immutable; sửa mảng sau khi tạo chuỗi không thay đổi `text`.

## 6. Vì sao biến kiểu `Object` không tự gọi `char[]`?

Xét:

```java
char[] source = {'H', 'i'};
Object asObject = source;

String s1 = String.valueOf(source);   // char[] overload
String s2 = String.valueOf(asObject); // Object overload
```

`source` và `asObject` trỏ tới **cùng một đối tượng mảng**, nhưng trình biên dịch thấy kiểu của chúng lần lượt là `char[]` và `Object`. Vì vậy, `s1` chứa `"Hi"`; `s2` chứa biểu diễn mặc định của đối tượng mảng như `[C@...`.

Với `null` cũng vậy:

```java
char[] chars = null;
Object obj = chars;

String a = String.valueOf(obj);   // "null"
String b = String.valueOf(chars); // NullPointerException
```

Sự khác biệt xuất hiện **do kiểu tĩnh của biểu thức**. Không nên đoán overload theo kiểu mà biến có thể trỏ tới khi chạy.

Một hàm nhận đối số kiểu `Object` sẽ luôn gọi phiên bản `valueOf(Object)` cho lời gọi bên trong:

```java
static String convert(Object value) {
    return String.valueOf(value);
}
```

Đây là **đoạn minh họa cần đặt trong một class**, không phải chương trình độc lập. Gọi `convert(null)` trả về `"null"`. Gọi `convert(new char[] {'A', 'B'})` lại không tạo `"AB"`, bởi overload `Object` xử lý mảng như một đối tượng.

## 7. Cách xử lý an toàn trong code thực tế

### Mục tiêu A: mọi `null` đều thành chuỗi `"null"`

Nếu đối số có thể mang nhiều kiểu và muốn quy ước `null` thành văn bản `"null"`, gọi overload `Object` rõ ràng:

```java
String result = String.valueOf((Object) null);
```

Với biến tĩnh `Object value`, chỉ cần `String.valueOf(value)`. Tuy nhiên nếu giá trị **không phải null** và lại là mảng, `Object.toString()` không nhất thiết trả về nội dung các phần tử. Hãy chọn theo đúng mục đích.

### Mục tiêu B: cần nội dung `char[]` và có thể gặp `null`

Kiểm tra mảng trước khi gọi overload `char[]`:

```java
static String safeChars(char[] chars) {
    return chars == null ? "null" : String.valueOf(chars);
}
```

Hàm trên cần được khai báo bên trong một class. Nó trả về chuỗi `"null"` khi mảng không tồn tại, và trả về **nội dung ký tự** khi mảng có dữ liệu. Nếu `null` cần có nghĩa “không có giá trị” thay vì chữ `"null"`, hãy dùng quy ước phù hợp, ví dụ:

```java
static String displayChars(char[] chars) {
    return chars == null ? "<missing>" : String.valueOf(chars);
}
```

`"<missing>"` chỉ là placeholder do chương trình định nghĩa; không phải giá trị được Java tự động tạo.

### Mục tiêu C: cần chuỗi mặc định cho đối tượng bất kỳ

`java.util.Objects.toString(obj, nullDefault)` cho phép chỉ định văn bản thay `null`:

```java
import java.util.Objects;

// Đoạn minh họa bên trong một phương thức.
Object obj = null;
String result = Objects.toString(obj, "<none>");
System.out.println(result); // <none>
```

Nếu `obj` thực tế trỏ tới `char[]` không null, `Objects.toString` vẫn gọi `toString()` của đối tượng mảng, **không** tự chuyển từng ký tự thành một `String`. Phương thức này giúp xử lý `null` theo kiểu `Object`, không thay thế cho `String.valueOf(char[])`.

### Mục tiêu D: muốn ký tự `"null"` được in nhưng không cần chọn overload

Nếu chỉ cần in thông tin chẩn đoán, có thể viết:

```java
System.out.println("value=" + null);
// value=null
```

Đây là quy tắc **nối chuỗi (`String` concatenation)** của Java, không phải lời gọi `String.valueOf(null)` trực tiếp ở mã nguồn. Không nên suy luận từ biểu thức nối chuỗi rằng `String.valueOf(null)` cũng sẽ trả về `"null"`.

## 8. Những hiểu nhầm thường gặp

1. **“`String.valueOf(null)` luôn trả về `"null"`.”** Sai: lời gọi với null literal chọn `valueOf(char[])` rồi ném `NullPointerException`.
2. **“Java chọn overload theo dữ liệu runtime.”** Sai trong trường hợp này: lựa chọn overload dựa vào **kiểu tĩnh** tại thời điểm biên dịch. Vì thế biến `Object` và biến `char[]` có thể tạo kết quả khác nhau dù cùng trỏ tới một mảng.
3. **“Đó là lỗi mơ hồ giữa `Object` và `char[]`.”** Sai: `char[]` là con của `Object`, nên overload `char[]` cụ thể hơn và được chọn.
4. **“Ép kiểu `(Object)` rồi vẫn nhận nội dung mảng.”** Không đúng với mảng `char[]` không null: chuyển sang overload `Object` khiến `toString()` mặc định của mảng được dùng.
5. **“`char[]` rỗng tương đương `null`.”** Sai: `new char[0]` là mảng hợp lệ và cho chuỗi rỗng; `(char[]) null` không phải mảng và gây `NullPointerException`.
6. **“Kết quả `"null"` là Java null.”** Sai: đây là một `String` có bốn ký tự `n`, `u`, `l`, `l`; đối tượng chuỗi tồn tại bình thường.
7. **“Nên bắt `NullPointerException` để sửa dữ liệu.”** Thường không cần. Nếu `null` được phép xuất hiện, kiểm tra bằng `if` hoặc toán tử ba ngôi và quyết định ý nghĩa thích hợp cho ứng dụng.
8. **“Dùng `Objects.toString` sẽ đổi mọi mảng ra nội dung.”** Không đúng: với đối tượng mảng không null, nó dùng `toString()` của đối tượng đó.

## 9. Bài luyện tập

**Câu 1.** Ba biểu thức dưới đây trả về gì?

```java
String.valueOf((Object) null);
String.valueOf((char[]) null);
String.valueOf(null);
```

Đáp án lần lượt: `"null"`; `NullPointerException`; `NullPointerException`.

**Câu 2.** Với:

```java
Object a = null;
char[] b = null;
```

Lời gọi `String.valueOf(a)` và `String.valueOf(b)` khác nhau thế nào?

Đáp án: `a` có kiểu tĩnh `Object` nên nhận chuỗi `"null"`. `b` có kiểu tĩnh `char[]` nên ném `NullPointerException`.

**Câu 3.** Vì sao `String.valueOf((String) null)` trả về `"null"`?

Đáp án: `String` là con của `Object` nhưng không tương thích với `char[]`; chỉ overload `Object` có thể nhận đối số này.

**Câu 4.** Đổi một biến `char[] chars` có thể null thành văn bản và bảo toàn nội dung ký tự khi mảng không null.

Đáp án:

```java
String result = chars == null ? "<empty>" : String.valueOf(chars);
```

Ở đây `"<empty>"` là giá trị thay thế theo quy tắc của ứng dụng, không có liên quan trực tiếp tới lựa chọn overload.

**Câu 5.** Với `Object data = new char[] {'A', 'B'};`, `String.valueOf(data)` có trả về `"AB"` không?

Đáp án: không. Overload nhận `Object` được chọn theo kiểu tĩnh `Object` của `data`. Nó dùng `toString()` của đối tượng mảng nên cho biểu diễn kiểu `[C@...`, không phải các ký tự nối lại.

**Ghi nhớ:** `String.valueOf(null)` nguy hiểm vì `char[]` là overload cụ thể hơn `Object`. Muốn chọn xử lý `null` theo `Object`, ép kiểu `(Object) null` hoặc truyền biến mang kiểu `Object`. Muốn lấy nội dung của mảng ký tự, dùng `valueOf(char[])` với mảng **khác null** và kiểm tra null rõ ràng.

## Tài liệu chính thức

- [`String.valueOf(Object)` và `String.valueOf(char[])` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html)
- [JLS §15.12.2 — Compile-Time Step 2: Determine Method Signature](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12.2)
- [JLS §15.12.2.5 — Choosing the Most Specific Method](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12.2.5)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-conversion-003">copyValueOf và tạo chuỗi từ mảng ký tự</a>

<details>
<summary>Xem chi tiết</summary>

# String.copyValueOf và tạo chuỗi từ mảng ký tự trong Java

## 1. Bài toán: chuyển `char[]` thành `String`

Trong Java, `char[]` là **mảng các phần tử `char` có thể thay đổi**, còn `String` biểu diễn một chuỗi văn bản **bất biến (immutable)**. Khi xử lý văn bản bằng mảng ký tự, bạn thường cần tạo một `String` để trả về từ phương thức, so sánh, lưu trữ hoặc truyền cho API nhận chuỗi.

Ví dụ:

```java
char[] letters = {'J', 'a', 'v', 'a'};
String text = String.copyValueOf(letters);

System.out.println(text); // Java
```

`String.copyValueOf` là phương thức **static** của `java.lang.String`, chuyển một mảng ký tự hoặc một phần mảng thành chuỗi. Nó đặc biệt hữu ích khi đã có dữ liệu dạng `char[]` và muốn lấy đúng nội dung ký tự, thay vì biểu diễn của chính đối tượng mảng.

Bài học tập trung vào hai overload của `copyValueOf`, cách tạo chuỗi từ mảng, ngữ nghĩa sao chép, chọn phạm vi con và những lưu ý về Unicode/UTF-16.

## 2. Hai overload của String.copyValueOf

API cung cấp hai chữ ký:

```java
public static String copyValueOf(char[] data)
public static String copyValueOf(char[] data, int offset, int count)
```

| Tham số | Ý nghĩa |
| --- | --- |
| `data` | Mảng `char[]` chứa dữ liệu nguồn |
| `offset` | Chỉ số bắt đầu lấy trong mảng (đếm từ **0**) |
| `count` | **Số lượng** phần tử `char` cần lấy; không phải chỉ số kết thúc |

**Overload một tham số** lấy toàn bộ mảng:

```java
char[] data = {'H', 'e', 'l', 'l', 'o'};
String result = String.copyValueOf(data);

System.out.println(result); // Hello
```

**Overload ba tham số** lấy một đoạn liên tiếp từ `offset`, gồm `count` phần tử:

```java
char[] data = {'H', 'e', 'l', 'l', 'o', '-', 'J', 'a', 'v', 'a'};

String left = String.copyValueOf(data, 0, 5);
String right = String.copyValueOf(data, 6, 4);

System.out.println(left);  // Hello
System.out.println(right); // Java
```

Với `String.copyValueOf(data, 6, 4)`, các phần tử được lấy có chỉ số **6, 7, 8, 9**, tức đoạn nửa mở `[6, 10)`. Đối số thứ ba **không phải endIndex**. Nhầm `count` với vị trí kết thúc sẽ khiến bạn lấy sai đoạn hoặc gây ngoại lệ vượt phạm vi.

Cách tư duy rõ ràng là:

```text
data = ['H', 'e', 'l', 'l', 'o', '-', 'J', 'a', 'v', 'a']
index    0    1    2    3    4    5    6    7    8    9

copyValueOf(data, 6, 4)
                 |  +---- lấy 4 đơn vị char
                 +------- bắt đầu tại index 6

Kết quả: "Java"
```

## 3. Sao chép giá trị, không giữ một view đến mảng

Mảng `char[]` có thể được sửa sau khi tạo `String`. Chuỗi nhận được từ `copyValueOf` phải **không bị ảnh hưởng** bởi những lần thay đổi mảng sau đó.

```java
char[] source = {'c', 'o', 'd', 'e'};
String frozen = String.copyValueOf(source);

source[0] = 'm';

System.out.println(new String(source)); // mode
System.out.println(frozen);             // code
```

Điều này đúng vì `String` bất biến và API cam kết kết quả không thay đổi nếu mảng ký tự nguồn thay đổi. Không nên coi `String.copyValueOf(data)` là một **view có liên kết sống** với mảng.

Khi dùng overload chọn đoạn, ý nghĩa vẫn như vậy:

```java
char[] buffer = {'<', 'O', 'K', '>'};
String token = String.copyValueOf(buffer, 1, 2);

buffer[1] = 'N';

System.out.println(token);              // OK
System.out.println(new String(buffer)); // <NK>
```

Chiều ngược lại cũng độc lập về mặt giá trị: `String.toCharArray()` tạo mảng ký tự mới từ chuỗi. Sửa mảng đó không sửa `String`:

```java
String word = "Java";
char[] copy = word.toCharArray();

copy[0] = 'L';

System.out.println(word);             // Java
System.out.println(new String(copy)); // Lava
```

**Vì sao có ích?** Khi một bộ đệm ký tự được tái sử dụng để đọc dữ liệu tiếp theo, `copyValueOf` giúp lưu lại nội dung của lần đọc hiện tại dưới dạng `String` ổn định.

Lưu ý: API bảo đảm **ngữ nghĩa độc lập của kết quả**, chứ không yêu cầu bạn suy đoán chi tiết lưu trữ nội bộ của `String` trong từng phiên bản JDK.

## 4. copyValueOf, valueOf và new String(char[])

Có nhiều cách hợp lệ để tạo chuỗi từ mảng ký tự:

```java
char[] letters = {'J', 'a', 'v', 'a'};

String a = String.copyValueOf(letters);
String b = String.valueOf(letters);
String c = new String(letters);

System.out.println(a);                   // Java
System.out.println(a.equals(b));         // true
System.out.println(a.equals(c));         // true
```

Trong API `String`, `copyValueOf(char[])` là cách viết tương đương về chức năng với `valueOf(char[])`. Tương tự, overload `copyValueOf(char[], offset, count)` tương đương với `valueOf(char[], offset, count)` về chuỗi tạo ra.

| Cách viết | Kết quả với `char[]` | Khi nào nên dùng |
| --- | --- | --- |
| `String.copyValueOf(data)` | Chuỗi chứa toàn bộ mảng | Nhấn mạnh chuyển và giữ nội dung của mảng |
| `String.copyValueOf(data, offset, count)` | Chuỗi chứa đoạn mảng | Đã có offset và số `char` cần lấy |
| `String.valueOf(data)` khi `data` có kiểu tĩnh `char[]` | Chuỗi chứa toàn bộ mảng | Cách viết ngắn gọn, phổ biến |
| `String.valueOf(data, offset, count)` | Chuỗi chứa đoạn mảng | Tương đương overload chọn đoạn của `copyValueOf` |
| `new String(data)` | Chuỗi chứa toàn bộ mảng | Cách dùng constructor |
| `new String(data, offset, count)` | Chuỗi chứa đoạn mảng | Cách dùng constructor cho đoạn mảng |

Các cách trên cho cùng **nội dung** khi dùng cùng các ký tự và đoạn mảng. Không nên dùng toán tử `==` để suy ra chúng là cùng đối tượng. Với `String`, so sánh nội dung bằng `equals`.

### `valueOf(char[])` và `valueOf(Object)` khác nhau

`String.valueOf(data)` với biến có kiểu tĩnh `char[]` lấy **nội dung các ký tự**; ép thành `Object` chuyển sang biểu diễn đối tượng mảng:

```java
char[] data = {'H', 'i'};
System.out.println(String.valueOf(data));                // Hi
System.out.println(String.valueOf((Object) data)
        .startsWith("[C@"));                              // true
```

Nếu dữ liệu đến dưới kiểu `Object`, phải kiểm tra `instanceof char[]` trước khi chọn cách chuyển đổi; không ép kiểu vô điều kiện. Chi tiết phân giải overload và `null` nằm trong bài về `String.valueOf(Object)` so với `String.valueOf(char[])`.

### Phân biệt với Arrays.toString

`Arrays.toString(char[])` tạo biểu diễn hiển thị các **phần tử mảng**, có dấu ngoặc vuông, dấu phẩy và dấu cách; nó không ghép các ký tự thành chuỗi liền mạch:

```java
char[] data = {'H', 'i'};

System.out.println(String.copyValueOf(data)); // Hi
System.out.println(java.util.Arrays.toString(data)); // [H, i]
```

Hai cách đều có ích nhưng phục vụ mục đích khác nhau: cái đầu tạo văn bản, cái sau in cấu trúc mảng để gỡ lỗi.

## 5. offset và count: chọn đúng phạm vi

Giả sử `data.length == n`. Một phạm vi hợp lệ cần thỏa tất cả điều kiện:

- `offset >= 0`.
- `count >= 0`.
- `offset <= n`.
- `count <= n - offset`.

Nói cách khác, khoảng `[offset, offset + count)` phải nằm bên trong mảng. Viết điều kiện `count <= n - offset` giúp tránh việc tính `offset + count` bị tràn số nguyên trong tình huống giá trị cực đoan.

```java
char[] data = {'a', 'b', 'c', 'd'};

System.out.println(String.copyValueOf(data, 1, 2)); // bc
System.out.println(String.copyValueOf(data, 4, 0).isEmpty()); // true
```

Lấy `count == 0` trong phạm vi hợp lệ cho ra chuỗi rỗng `""`; `offset == data.length` được phép **nếu count bằng 0**. Ngược lại, chỉ số âm hoặc yêu cầu vượt giới hạn sẽ phát sinh ngoại lệ.

Nếu cần lấy từ vị trí `from` (bao gồm) đến `toExclusive` (không bao gồm), hãy tính `count = toExclusive - from`:

```java
char[] data = {'a', 'b', 'c', 'd', 'e'};
int from = 1;
int toExclusive = 4;

String result = String.copyValueOf(data, from, toExclusive - from);
System.out.println(result); // bcd
```

Cách này nhất quán với quy ước phạm vi nửa mở trong nhiều API Java. Tránh truyền thẳng `toExclusive` làm `count` nếu đoạn không bắt đầu từ 0.

## 6. null, vùng không hợp lệ và các ngoại lệ

Không có chuỗi hợp lệ nào được trả về khi truyền mảng `null` cho `copyValueOf`.

```java
char[] missing = null;

try {
    String.copyValueOf(missing);
} catch (NullPointerException e) {
    System.out.println("Mảng nguồn là null");
}
```

Một lời gọi với `offset` hoặc `count` không hợp lệ phát sinh `IndexOutOfBoundsException` (các phiên bản JDK có thể dùng lớp con cụ thể):

```java
char[] data = {'J', 'a', 'v', 'a'};

try {
    String.copyValueOf(data, 3, 2); // cần 2 char từ index 3
} catch (IndexOutOfBoundsException e) {
    System.out.println("Đoạn mảng vượt giới hạn");
}
```

Đừng nhầm với overload nhận `Object` của `String.valueOf`. Gọi `String.valueOf((Object) null)` cho chuỗi văn bản `"null"`, trong khi `String.copyValueOf((char[]) null)` ném `NullPointerException`. Đây là những hợp đồng khác nhau.

Khi dữ liệu đến từ người dùng hoặc buffer có `offset`/`count` tính toán, hãy kiểm tra bất biến phạm vi ở ranh giới hệ thống. Nếu dữ liệu bắt buộc có mảng, việc ném ngoại lệ có thể là lựa chọn phù hợp; nếu `null` có ý nghĩa nghiệp vụ, hãy xử lý rõ trước khi gọi:

```java
static String safelyCopy(char[] data) {
    return data == null ? "" : String.copyValueOf(data);
}
```

Mẫu này chọn chuỗi rỗng khi nguồn `null` **theo quyết định của ứng dụng**, không phải hành vi mặc định của API.

## 7. char[] là các đơn vị UTF-16, không phải mọi "ký tự nhìn thấy"

`char` trong Java là đơn vị mã UTF-16 16-bit. Nhiều ký tự Unicode thường gặp, như `'A'`, chiếm một `char`. Nhưng ký tự nằm ngoài Basic Multilingual Plane (BMP), chẳng hạn emoji `🚀` (U+1F680), được biểu diễn bằng **cặp surrogate gồm hai `char`**.

`copyValueOf(data, offset, count)` đếm theo **phần tử `char`**, không theo số Unicode code point và cũng không theo ký tự người dùng nhìn thấy (grapheme cluster).

```java
char[] symbols = {'A', '\uD83D', '\uDE80', 'B'};
String text = String.copyValueOf(symbols);

System.out.println(text.length()); // 4 đơn vị UTF-16
System.out.println(text.codePointCount(0, text.length())); // 3 code point

String rocket = String.copyValueOf(symbols, 1, 2);
System.out.println(rocket); // 🚀
System.out.println(rocket.length()); // 2
```

Nếu cắt một phần tử ở giữa cặp surrogate, kết quả có thể chứa một surrogate không ghép đôi; chuỗi Java vẫn có thể lưu đơn vị mã đó nhưng quá trình xử lý/hiển thị Unicode về sau có thể gặp vấn đề.

Khi offset/count xuất phát từ vị trí code point, cần tính sang chỉ số UTF-16 đúng cách trước khi dùng `copyValueOf`. Có thể bắt đầu bằng một `String` và dùng `offsetByCodePoints` để xác định ranh giới:

```java
String source = "A🚀B";
int begin = source.offsetByCodePoints(0, 1);
int end = source.offsetByCodePoints(begin, 1);
char[] units = source.toCharArray();

String rocket = String.copyValueOf(units, begin, end - begin);
System.out.println(rocket); // 🚀
```

Ở đây `begin == 1`, `end == 3`, nên `count == 2`. Đây là hai đơn vị `char` biểu diễn một code point, hoàn toàn phù hợp với hợp đồng của `copyValueOf`.

**Không nhầm với mã hóa:** `copyValueOf` không đọc byte và không áp dụng `UTF-8` hay `Charset`; đầu vào là các đơn vị `char` đã có sẵn. Khi đầu vào là `byte[]`, cần giải mã với charset phù hợp (ví dụ `new String(bytes, StandardCharsets.UTF_8)`), không dùng `copyValueOf`.

## 8. Ví dụ Java 17+ hoàn chỉnh

Lưu mã sau vào tệp **`CopyValueOfDemo.java`**:

```java
public class CopyValueOfDemo {

    public static void main(String[] args) {
        char[] letters = {'J', 'a', 'v', 'a', ' ', '1', '7'};

        String whole = String.copyValueOf(letters);
        String word = String.copyValueOf(letters, 0, 4);
        String tail = String.copyValueOf(letters, 5, 2);
        String byValueOf = String.valueOf(letters);
        String byConstructor = new String(letters);

        System.out.println("whole = " + whole);
        System.out.println("word = " + word);
        System.out.println("tail = " + tail);
        System.out.println("same text = "
                + (whole.equals(byValueOf) && whole.equals(byConstructor)));

        letters[0] = 'X';
        letters[5] = '2';

        System.out.println("array now = " + new String(letters));
        System.out.println("old string = " + whole);

        char[] roundTrip = word.toCharArray();
        roundTrip[0] = 'L';
        System.out.println("word remains = " + word);

        System.out.println("empty tail = "
                + String.copyValueOf(letters, letters.length, 0).isEmpty());

        System.out.println("object overload uses contents = "
                + String.valueOf((Object) letters).equals(new String(letters)));

        char[] symbols = {'A', '\uD83D', '\uDE80', 'B'};
        String line = String.copyValueOf(symbols);
        String rocket = String.copyValueOf(symbols, 1, 2);

        System.out.println("UTF16 units = " + line.length());
        System.out.println("code points = "
                + line.codePointCount(0, line.length()));
        System.out.println("rocket units = " + rocket.length());

        try {
            String.copyValueOf((char[]) null);
        } catch (NullPointerException e) {
            System.out.println("null -> NullPointerException");
        }

        try {
            String.copyValueOf(letters, 6, 2);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("bad range -> IndexOutOfBoundsException");
        }
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 CopyValueOfDemo.java
java CopyValueOfDemo
```

Kết quả chuẩn:

```text
whole = Java 17
word = Java
tail = 17
same text = true
array now = Xava 27
old string = Java 17
word remains = Java
empty tail = true
object overload uses contents = false
UTF16 units = 4
code points = 3
rocket units = 2
null -> NullPointerException
bad range -> IndexOutOfBoundsException
```

Ý nghĩa của các dòng quan trọng:

- `whole` vẫn là `"Java 17"` sau khi `letters` đã thành `"Xava 27"`: chuỗi đã tạo không liên kết sống với mảng.
- `same text = true` xác nhận ba cách tạo chuỗi có cùng **nội dung**, không kết luận về việc chúng là cùng đối tượng.
- `empty tail = true` cho thấy `count = 0` hợp lệ tại cuối mảng.
- `object overload uses contents = false` cho thấy ép `char[]` thành `Object` làm thay đổi overload được chọn khi gọi `String.valueOf`.
- `UTF16 units = 4` nhưng `code points = 3` minh họa cặp surrogate; `rocket units = 2` là chiều dài theo `char` của một emoji.
- Hai ngoại lệ cuối kiểm tra riêng lỗi `null` và phạm vi không hợp lệ.

## 9. Khi nào nên chọn copyValueOf?

Sử dụng `String.copyValueOf(char[])` khi đã có một mảng ký tự chứa dữ liệu cần chuyển thành chuỗi, và `String.copyValueOf(char[], offset, count)` khi chỉ muốn giữ một đoạn mà không cần trước đó tự tạo mảng con.

Ví dụ với bộ đệm được sử dụng trong quá trình đọc:

```java
char[] buffer = new char[8];
// Giả sử API đọc đã điền 3 ký tự đầu: 'c', 'a', 't'
buffer[0] = 'c';
buffer[1] = 'a';
buffer[2] = 't';
int length = 3;

String snapshot = String.copyValueOf(buffer, 0, length);
// snapshot là "cat"; phần còn lại của buffer không được ghép vào chuỗi
```

Khi chỉ một phần mảng chứa dữ liệu có ý nghĩa, overload ba tham số giúp tránh đưa các phần tử chưa dùng (ví dụ `'\0'`) vào `String`.

Các lưu ý khi lựa chọn:

- Về **nội dung kết quả**, `String.valueOf(char[])` và `String.copyValueOf(char[])` tương đương. Có thể chọn cách viết quen thuộc của codebase.
- Không dùng `String.copyValueOf` để ghép nhiều mảng, chuyển kiểu đối tượng bất kỳ hay giải mã `byte[]`. Nếu cần ghép nhiều đoạn, `StringBuilder` hoặc cách ghép phù hợp thường diễn đạt mục đích rõ hơn.
- Việc tạo `String` mới từ mảng có chi phí xử lý và lưu trữ phụ thuộc độ dài; khi lặp với dữ liệu lớn, nên đo đạc trường hợp thực tế. Không suy đoán chi tiết nội bộ của từng JDK.
- Nếu mảng chứa bí mật cần xóa khỏi bộ nhớ ngay sau sử dụng, việc chuyển thành `String` bất biến có thể làm khó kiểm soát vòng đời dữ liệu; cân nhắc yêu cầu bảo mật trước khi tạo chuỗi.
- Với văn bản Unicode, kiểm tra ranh giới UTF-16 và chuẩn hóa riêng nếu bài toán cần so sánh Unicode tương đương; `copyValueOf` không tự chuẩn hóa dấu hay code point.

## 10. Những lỗi phổ biến

1. **Dùng `charArray.toString()` và mong nhận nội dung.** Phương thức đó biểu diễn đối tượng mảng, không ghép ký tự. Dùng `String.copyValueOf(charArray)`.
2. **Nhầm `count` là endIndex.** `copyValueOf(data, 2, 5)` nghĩa là lấy **năm ký tự từ chỉ số 2**, không phải lấy các chỉ số 2, 3, 4.
3. **Giữ tham chiếu đến mảng rồi tưởng thay đổi mảng sẽ đổi chuỗi.** `String` là immutable; snapshot đã tạo không đổi.
4. **Không kiểm tra `null` hoặc phạm vi.** `copyValueOf(null)` gây `NullPointerException`; đoạn ngoài mảng gây `IndexOutOfBoundsException`.
5. **Ép mảng thành `Object` khi gọi `String.valueOf`.** Overload `valueOf(Object)` không ghép các phần tử `char[]` thành văn bản.
6. **Dùng `Arrays.toString(char[])` khi cần chuỗi liền mạch.** Nó được dùng để hiển thị cấu trúc mảng, ví dụ `[H, i]`.
7. **Giả định `char` tương ứng một Unicode code point.** Một ký tự ngoài BMP thường chiếm hai đơn vị UTF-16; phạm vi có thể cắt nhầm cặp surrogate.
8. **Nhầm số ký tự hợp lệ của buffer với `buffer.length`.** Nếu buffer chỉ chứa một phần dữ liệu, phải truyền đúng `offset` và `count`.
9. **So sánh chuỗi bằng `==` thay vì `equals`.** Kết quả tạo từ nhiều API có thể là các đối tượng khác nhau nhưng có cùng văn bản.

## 11. Bài tập tự kiểm tra

**Bài 1.** Với đoạn mã sau, kết quả là gì?

```java
char[] data = {'A', 'B', 'C', 'D', 'E'};
System.out.println(String.copyValueOf(data, 1, 3));
```

**Đáp án:** `BCD`. Bắt đầu tại chỉ số 1 và lấy ba phần tử.

**Bài 2.** Vì sao `String.copyValueOf(data, 2, 4)` gây ngoại lệ khi `data.length == 5`?

**Đáp án:** Phạm vi dự kiến là `[2, 6)`, vượt ra ngoài `[0, 5)` của mảng. `count` cần nhỏ hơn hoặc bằng `5 - 2 = 3`.

**Bài 3.** Sau khi chạy mã sau, `s` có đổi thành `"Xbc"` không?

```java
char[] data = {'a', 'b', 'c'};
String s = String.copyValueOf(data);
data[0] = 'X';
```

**Đáp án:** Không. `s` vẫn là `"abc"` vì thay đổi mảng nguồn sau đó không ảnh hưởng `String` đã tạo.

**Bài 4.** `String.copyValueOf(data, 0, 1)` có chắc lấy được toàn bộ emoji ở đầu mảng không?

**Đáp án:** Không chắc. Emoji nằm ngoài BMP thường gồm hai `char` UTF-16. Cần xác định ranh giới code point chính xác rồi mới cắt theo `char`.

**Bài 5.** Tại sao `String.valueOf((Object) data)` không nên dùng để chuyển `char[]` thành văn bản?

**Đáp án:** Nó chọn overload `valueOf(Object)` và lấy biểu diễn đối tượng mảng. Muốn ghép từng phần tử `char`, dùng `String.valueOf(data)` với kiểu tĩnh `char[]`, `String.copyValueOf(data)` hoặc `new String(data)`.

## Tài liệu tham khảo

- [Java SE 17 — String API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): các overload `copyValueOf`, `valueOf`, constructor từ `char[]`, `toCharArray`, và quy tắc immutability.
- [Java SE 17 — Character API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): UTF-16 code units, code points và surrogate pairs.

**Ghi nhớ:** `copyValueOf` tạo `String` từ toàn bộ hoặc một đoạn mảng `char[]`, `offset` là vị trí bắt đầu, `count` là số **đơn vị UTF-16 `char`** được lấy, và nội dung chuỗi không thay đổi khi mảng nguồn bị sửa về sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-conversion-004">toString của String và của đối tượng khác</a>

<details>
<summary>Xem chi tiết</summary>

# toString() của String và của đối tượng khác trong Java

Trong Java, **toString()** là phương thức dùng để lấy cách biểu diễn một đối tượng dưới dạng **String**. Mọi lớp đều thừa hưởng phương thức này từ **java.lang.Object**, nhưng từng kiểu có thể định nghĩa lại cách biểu diễn của mình. Điểm đặc biệt là **String đã là chuỗi văn bản**, vì vậy **String.toString() trả về chính đối tượng String hiện tại**, trong khi nhiều đối tượng khác phải tạo hoặc trả về một biểu diễn chuỗi phù hợp.

Hiểu rõ sự khác nhau giúp đọc kết quả println, nối chuỗi, log, debug; tránh hiểu nhầm dấu @ trong biểu diễn mặc định; xử lý null đúng cách; và nhận ra tại sao toString() của mảng không in các phần tử như mong đợi.

## 1. toString() được khai báo ở đâu và có ý nghĩa gì?

Lớp **Object** định nghĩa phương thức public String toString(). Vì mọi lớp Java đều kế thừa Object (trực tiếp hoặc gián tiếp), các lớp như String, Integer, ArrayList, các lớp nghiệp vụ tự viết và cả đối tượng mảng đều có thể được chuyển sang cách biểu diễn dạng String thông qua toString().

Cách hoạt động cụ thể phụ thuộc vào lớp thực tế của đối tượng:

- **Object không ghi đè toString():** phương thức mặc định trả về chuỗi gồm tên lớp, dấu @ và giá trị hash code ở dạng thập lục phân.
- **Lớp ghi đè toString():** phương thức có thể trả về tên, thuộc tính, trạng thái hay thông tin dễ đọc, tùy định nghĩa của lớp.
- **String:** ghi đè toString() để **return this**, tức trả lại chính tham chiếu đến chuỗi ban đầu; không tạo thêm chuỗi nội dung tương đương.
- **Kiểu nguyên thủy** như int, long, boolean không phải Object và **không có phương thức thể hiện intVariable.toString()**; muốn tạo chuỗi có thể dùng String.valueOf(value) hoặc Integer.toString(value) cho kiểu phù hợp.

toString() được dùng để trình bày hoặc quan sát đối tượng. Nó **không phải phương thức tự động tuần tự hóa** dữ liệu thành JSON, XML hoặc dạng dữ liệu đủ để tái tạo đối tượng. Một kết quả toString() cũng không bảo đảm tính duy nhất, tính bất biến về định dạng, hoặc tính phù hợp để gửi cho máy khác phân tích.

## 2. String.toString(): trả về chính String đó

Trong Java, String là lớp final, không thể có lớp con sửa hành vi toString(). Phương thức **String.toString() trả về this**; vì vậy nếu biến s đang tham chiếu một String khác null, biểu thức s.toString() có cùng tham chiếu và cùng nội dung với s.

Ví dụ tệp **StringToStringDemo.java** (chạy với Java 17+):

~~~java
public class StringToStringDemo {
    public static void main(String[] args) {
        String text = new String("Java");
        String converted = text.toString();

        System.out.println("content: " + converted);
        System.out.println("same reference: " + (text == converted));
        System.out.println("same content: " + text.equals(converted));

        Object objectRef = text;
        System.out.println("via Object: " + objectRef.toString());
        System.out.println("same via Object: " + (objectRef.toString() == text));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 StringToStringDemo.java
java StringToStringDemo
~~~

Kết quả:

~~~text
content: Java
same reference: true
same content: true
via Object: Java
same via Object: true
~~~

**Vì sao đều đúng?**

1. **new String("Java")** cố ý tạo một đối tượng String mới để ví dụ không dựa vào cơ chế String pool.
2. **text.toString()** trả lại chính text; vì vậy toán tử **==** cho kết quả true giữa hai tham chiếu.
3. **equals()** so sánh nội dung chuỗi và cũng cho true, nhưng đây là phép kiểm tra khác với ==.
4. Dù biến **objectRef** có kiểu khai báo là Object, đối tượng thực tế vẫn là String. Java dùng **dynamic dispatch** để gọi String.toString() đã ghi đè, nên nhận lại đúng chuỗi "Java".

Không cần gọi toString() mỗi lần đã có String. Ví dụ, viết **String result = text;** thường diễn đạt ý định rõ hơn **String result = text.toString();**. Việc gọi toString() trên String không làm sạch, chuyển đổi mã hóa, chuẩn hóa Unicode, bỏ khoảng trắng hay tạo bản sao.

**Chú ý null:** kết luận trả về this chỉ áp dụng khi tham chiếu String **khác null**. Nếu **String s = null**, gọi **s.toString()** sẽ ném NullPointerException trước khi có một chuỗi nào được trả về.

## 3. Object.toString() mặc định: tên lớp, @ và hash

Nếu một lớp không ghi đè toString(), nó sử dụng cách triển khai kế thừa của **Object**. Mẫu kết quả về mặt khái niệm:

~~~text
ten.goi.Lop@maHashHe16
~~~

Theo hợp đồng của Object.toString(), giá trị được tạo như khi ghép:

~~~java
getClass().getName() + "@" + Integer.toHexString(hashCode())
~~~

Chẳng hạn, một phiên chạy có thể hiển thị **java.lang.Object@5acf9800**. Phần sau dấu @ phụ thuộc hashCode của đối tượng, nên **không được cố định chuỗi này trong kỳ vọng output**.

Tệp **ObjectToStringDemo.java**:

~~~java
public class ObjectToStringDemo {
    public static void main(String[] args) {
        Object obj = new Object();
        String result = obj.toString();

        System.out.println(result.startsWith("java.lang.Object@"));
        System.out.println(result.equals(
                obj.getClass().getName() + "@"
                        + Integer.toHexString(obj.hashCode())
        ));
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 ObjectToStringDemo.java
java ObjectToStringDemo
~~~

Output chính xác:

~~~text
true
true
~~~

Ở đây tránh in trực tiếp mã hash thay đổi giữa các đối tượng/lần chạy; thay vào đó kiểm tra hai thuộc tính bảo đảm bởi triển khai mặc định.

**Hash code không phải địa chỉ bộ nhớ đáng tin cậy.** Dấu @ và đoạn hex của Object.toString() không chứng minh đối tượng đang ở vị trí nào trong RAM; giá trị hashCode không được thiết kế làm ID nghiệp vụ duy nhất. Hai đối tượng khác nhau cũng có thể có cùng hash code.

Ngoài ra, một lớp có thể ghi đè **hashCode()** nhưng không ghi đè **toString()**; khi đó Object.toString() sẽ sử dụng kết quả hashCode của lớp đó, nên không nên suy luận rằng phần hex nhất thiết là mã định danh mặc định của JVM.

## 4. Lớp tự viết: ghi đè toString() để nội dung dễ đọc

Phần lớn các lớp nghiệp vụ cần cách hiển thị có ý nghĩa hơn tên lớp và mã hash. Ta có thể ghi đè toString() bằng **@Override** và trả về String mô tả những thuộc tính hữu ích.

Tạo tệp **OrderToStringDemo.java**:

~~~java
public class OrderToStringDemo {
    static final class Order {
        private final int id;
        private final String status;

        Order(int id, String status) {
            this.id = id;
            this.status = status;
        }

        @Override
        public String toString() {
            return "Order{id=" + id + ", status='" + status + "'}";
        }
    }

    public static void main(String[] args) {
        Order order = new Order(42, "PAID");

        System.out.println(order.toString());
        System.out.println(order);
        System.out.println("Current: " + order);
    }
}
~~~

~~~bash
javac -encoding UTF-8 OrderToStringDemo.java
java OrderToStringDemo
~~~

~~~text
Order{id=42, status='PAID'}
Order{id=42, status='PAID'}
Current: Order{id=42, status='PAID'}
~~~

Trong ví dụ:

- **@Override** giúp trình biên dịch kiểm tra chữ ký phương thức đang ghi đè một phương thức của lớp cha.
- Phương thức ghi đè là **public String toString()**, không nhận tham số và trả về String.
- **System.out.println(order)** có thể sử dụng cách biểu diễn chuỗi của order; phần nối chuỗi trong **"Current: " + order** cũng sử dụng cách biểu diễn này.
- toString() ở đây **tạo ra cách biểu diễn văn bản** của Order. Đừng dựa vào việc nó luôn tạo đối tượng String mới hay luôn dùng đúng format này khi lớp được sửa ở phiên bản khác.

Nếu lớp chứa thông tin nhạy cảm như mật khẩu, token, khóa API, số định danh riêng tư, **không đưa những giá trị đó vào toString()** vì phương thức dễ được gọi ngầm khi ghi log hoặc xử lý lỗi. Với đối tượng phức tạp, nên ưu tiên nội dung ngắn, dễ hiểu và tránh gọi dịch vụ ngoài hay tính toán tốn kém bên trong toString().

### Phương thức được chọn theo lớp thực tế

Nếu một biến khai báo kiểu Object đang trỏ tới Order, gọi biến.toString() vẫn chạy **Order.toString()**. Cơ chế này gọi là **đa hình**, không phụ thuộc việc biến được khai báo kiểu Object hay Order. Java không cần ép kiểu về Order để tìm phương thức ghi đè phù hợp.

**Lưu ý với record:** trong Java 17, một record chưa tự định nghĩa toString() được compiler cung cấp cách biểu diễn dựa trên tên record và các thành phần của nó. Ví dụ **record Point(int x, int y)** có thể in **Point[x=2, y=3]**. Điều này tiện khi debug, nhưng vẫn không biến toString() thành định dạng trao đổi dữ liệu ổn định.

## 5. String.valueOf(Object), Objects.toString và xử lý null

Dễ nhầm **x.toString()** với **String.valueOf(x)**. Cả hai có thể tạo cách biểu diễn chuỗi, nhưng khác nhau đáng kể khi tham chiếu **null**:

| Cách viết | x khác null | x là null |
| --- | --- | --- |
| x.toString() | Gọi toString() của lớp thực tế. | **NullPointerException**. |
| String.valueOf((Object) x) | Gọi x.toString(). | Trả về chuỗi **"null"**. |
| Objects.toString(x) | Gọi x.toString(). | Trả về chuỗi **"null"**. |
| Objects.toString(x, "không có") | Gọi x.toString(). | Trả về chuỗi dự phòng **"không có"**. |

Lý do viết **(Object)** trong bảng: String.valueOf có nhiều overload như nhận Object, char[] và kiểu nguyên thủy; ép kiểu Object làm rõ ta đang nói đến **String.valueOf(Object)**.

Tệp **NullToStringDemo.java**:

~~~java
import java.util.Objects;

public class NullToStringDemo {
    public static void main(String[] args) {
        Object present = Integer.valueOf(25);
        Object missing = null;

        System.out.println(String.valueOf(present));
        System.out.println(String.valueOf(missing));
        System.out.println(Objects.toString(missing, "(empty)"));

        try {
            System.out.println(missing.toString());
        } catch (NullPointerException ex) {
            System.out.println("toString on null: NullPointerException");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 NullToStringDemo.java
java NullToStringDemo
~~~

~~~text
25
null
(empty)
toString on null: NullPointerException
~~~

Đây là output gồm **bốn dòng**. Dòng thứ hai có nội dung văn bản "null", không có nghĩa biến missing đã biến thành một String.

**Cẩn thận với lời gọi String.valueOf(null) không ép kiểu:** do Java chọn overload phù hợp nhất, biểu thức với literal null có thể chọn overload **char[]** thay vì Object, dẫn đến lỗi khi chạy. Khi muốn đúng hành vi tạo chuỗi "null", hãy chỉ rõ **String.valueOf((Object) null)** hoặc sử dụng **Objects.toString(null)**.

String.valueOf còn có overload riêng cho boolean, char, int, long, float, double...; với kiểu nguyên thủy, nó tạo cách biểu diễn của giá trị nguyên thủy mà không cần tạo trước một wrapper object một cách tường minh. Với đối tượng, hành vi phụ thuộc vào toString() của lớp thực tế.

## 6. toString() của mảng khác với in nội dung phần tử

Trong Java, **mảng là đối tượng**, nhưng các lớp mảng không cung cấp kiểu toString liệt kê phần tử như người mới học thường kỳ vọng. Gọi **numbers.toString()** với một int[] dùng cách biểu diễn mặc định, thường bắt đầu bằng **[I@** (tên lớp mảng int ở dạng JVM) và thêm hash code.

Muốn quan sát nội dung mảng, dùng các tiện ích trong **java.util.Arrays**:

- **Arrays.toString(array)** cho mảng một chiều.
- **Arrays.deepToString(objectArray)** cho mảng nhiều chiều hoặc chứa các mảng con, chẳng hạn int[][].
- Với mảng **char[]**, overload **String.valueOf(char[])** đặc biệt tạo String từ các ký tự; nó khác String.valueOf((Object) charArray).

Ví dụ tệp **ArrayToStringDemo.java**:

~~~java
import java.util.Arrays;

public class ArrayToStringDemo {
    public static void main(String[] args) {
        int[] numbers = {2, 4, 6};
        int[][] matrix = {{1, 2}, {3, 4}};
        char[] letters = {'J', 'a', 'v', 'a'};

        System.out.println(numbers.toString().startsWith("[I@"));
        System.out.println(Arrays.toString(numbers));
        System.out.println(Arrays.deepToString(matrix));
        System.out.println(String.valueOf(letters));
        System.out.println(String.valueOf((Object) letters)
                .startsWith("[C@"));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ArrayToStringDemo.java
java ArrayToStringDemo
~~~

~~~text
true
[2, 4, 6]
[[1, 2], [3, 4]]
Java
true
~~~

Dòng đầu xác nhận kiểu tên mảng int mà không đoán mã hash. Dòng cuối chứng minh sự khác nhau giữa hai overload của String.valueOf: truyền trực tiếp char[] tạo String từ ký tự, còn ép sang Object buộc gọi toString() của đối tượng mảng.

Nếu có **Object[]** chứa mảng con, Arrays.toString có thể vẫn cho cách biểu diễn dạng tên lớp và hash của mảng con; Arrays.deepToString mới hiển thị các phần tử lồng nhau phù hợp. Với mảng tự tham chiếu, deepToString có cách xử lý chống đệ quy vô hạn theo hợp đồng API.

## 7. toString() của đối tượng có thể thay đổi trạng thái khác gì String?

Đối tượng có thể **mutable**, ví dụ StringBuilder. Gọi StringBuilder.toString() tạo một String biểu diễn **nội dung tại thời điểm gọi**; chuỗi kết quả là bất biến, nên những lần sửa StringBuilder sau đó không thay đổi snapshot đã lấy.

Tệp **BuilderToStringDemo.java**:

~~~java
public class BuilderToStringDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder("v1");
        String snapshot = builder.toString();

        builder.append("-updated");

        System.out.println("builder: " + builder);
        System.out.println("snapshot: " + snapshot);
        System.out.println("snapshot type: " + snapshot.getClass().getSimpleName());
    }
}
~~~

~~~bash
javac -encoding UTF-8 BuilderToStringDemo.java
java BuilderToStringDemo
~~~

~~~text
builder: v1-updated
snapshot: v1
snapshot type: String
~~~

**So sánh hai trường hợp:** String.toString() trả lại **cùng đối tượng String**, vì String đã bất biến và chính là dạng chuỗi mong muốn. StringBuilder.toString() trả về **String chứa nội dung hiện tại** của bộ dựng chuỗi; snapshot không tiếp tục cập nhật khi builder thay đổi. Không nên viết logic dựa vào việc đối tượng String do StringBuilder tạo ra có tham chiếu mới trong mọi hoàn cảnh; điều quan trọng theo API là **giá trị chuỗi được bảo toàn sau khi builder thay đổi**.

Nhiều lớp thư viện khác cũng cung cấp toString() dễ đọc, ví dụ các lớp số như Integer và các collection. Tuy vậy, định dạng chính xác phụ thuộc từng lớp; một collection có thể sử dụng toString() của các phần tử, nên phần tử hiển thị thế nào vẫn do lớp của chúng quyết định.

## 8. toString() được gọi ngầm ở đâu?

Java và các thư viện thường chuyển đối tượng sang văn bản mà ta không gọi trực tiếp phương thức toString():

- **System.out.println(object)**: các overload in ra có cơ chế chuyển đối tượng thành văn bản. Nếu object là null, println(Object) in **null** thay vì gọi phương thức trên tham chiếu null.
- **"value = " + object**: toán tử nối chuỗi chuyển toán hạng đối tượng sang cách biểu diễn String; null trở thành văn bản **"null"**.
- **String.valueOf(object)** và **Objects.toString(object)**: gọi toString() nếu đối tượng không null.
- **StringBuilder.append(Object)**: thường dùng String.valueOf(Object) rồi nối văn bản vào builder.
- Một số cách định dạng hay logger có thể sử dụng toString(), nhưng cơ chế riêng của từng thư viện có thể khác; không khẳng định mọi logger luôn gọi toString() theo cùng cách.

Do đó, override toString() hợp lý giúp debug, nhưng cũng có thể khiến việc in đối tượng **tốn kém**, phát sinh ngoại lệ hoặc vô tình lộ dữ liệu. Tránh đưa mật khẩu, access token, dữ liệu cá nhân và khối văn bản quá dài vào giá trị toString() mặc định của mô hình nghiệp vụ.

Nếu cần **format dành cho người dùng**, **dữ liệu lưu trữ**, hoặc **dữ liệu truyền qua API**, nên viết phương thức/serializer dành riêng cho mục đích đó. toString() chủ yếu là cách biểu diễn văn bản của đối tượng, không bảo đảm hợp đồng serialize ổn định.

## 9. Những nhầm lẫn thường gặp

| Nhầm lẫn | Giải thích đúng |
| --- | --- |
| String.toString() tạo một bản sao String mới. | String.toString() trả về **this**, tức cùng đối tượng. |
| Mọi đối tượng dùng Object.toString() mặc định. | Lớp có thể ghi đè toString(); Java gọi theo **kiểu đối tượng thực tế**. |
| Phần hex trong ClassName@hex là địa chỉ bộ nhớ. | Nó là biểu diễn hex của **hashCode()**, không phải địa chỉ RAM được bảo đảm. |
| Gọi toString() trên tham chiếu null trả về "null". | Gọi phương thức trên null gây **NullPointerException**; muốn null-safe hãy dùng String.valueOf((Object) x) hoặc Objects.toString(x). |
| String.valueOf(null) luôn tương đương String.valueOf((Object) null). | Có overload char[]; cần ép kiểu để tránh chọn sai overload khi truyền literal null. |
| array.toString() in tất cả phần tử. | Mảng thường dùng biểu diễn kiểu và hash; sử dụng Arrays.toString/deepToString. |
| Nối đối tượng vào String chỉ gọi Object.toString(). | Phương thức được chọn theo **lớp thực tế**, có thể là override của lớp con. |
| toString() luôn phải trả về tên lớp và các trường. | Đó là **thiết kế** của lớp; API chỉ yêu cầu trả về một String, nội dung có thể khác. |
| toString() là định dạng chuẩn cho JSON hay lưu trữ. | Không có bảo đảm đó; dùng serializer hoặc định dạng được định nghĩa riêng. |
| Override toString() có thể in cả secret cho dễ debug. | Lời gọi ngầm rất phổ biến; phải tránh để lộ thông tin nhạy cảm trong log. |

## 10. Checklist khi thiết kế và sử dụng toString()

1. **Đã có String?** Có thể sử dụng trực tiếp, vì gọi toString() không thay đổi tham chiếu hay nội dung.
2. **Đối tượng tùy biến cần dễ đọc?** Cân nhắc ghi đè public String toString(), thêm @Override và chọn các trường ngắn gọn, không nhạy cảm.
3. **Biến có thể null?** Dùng String.valueOf((Object) value), Objects.toString(value) hoặc Objects.toString(value, fallback) khi phù hợp.
4. **Đang xử lý array?** Xem Arrays.toString, Arrays.deepToString hoặc String.valueOf(char[]) tùy loại mảng.
5. **Đang chuyển StringBuilder hay đối tượng mutable?** Xem String trả về là biểu diễn/snapshot tại thời điểm gọi.
6. **Cần dữ liệu để so sánh, định danh hay lưu trữ?** Không mặc định toString() là hợp đồng dữ liệu; dùng equals/hashCode, khóa định danh hoặc serializer riêng tùy mục tiêu.

Quy tắc nhớ nhanh: **String.toString() giữ nguyên chính String; Object.toString() mặc định mô tả lớp và hash; các lớp khác có thể ghi đè để tạo biểu diễn có ý nghĩa.** Khi Java cần hiển thị đối tượng, hành vi phụ thuộc lớp thực tế và chính sách xử lý null của API gọi đến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-conversion-005">Integer.parseInt, parseLong và cơ số</a>

<details>
<summary>Xem chi tiết</summary>

# Integer.parseInt, Long.parseLong và cơ số trong Java

## 1. Từ chuỗi ký tự đến giá trị số nguyên

Dữ liệu nhập qua biểu mẫu, tệp cấu hình, lệnh terminal hay API thường là `String`. Chuỗi `"123"` chứa ba ký tự, còn số `123` là một giá trị nguyên có thể cộng, trừ, so sánh hoặc dùng làm chỉ số. Trong Java:

- `Integer.parseInt(String s)` đọc chuỗi số **có dấu, cơ số 10** và trả về `int`.
- `Long.parseLong(String s)` cũng đọc chuỗi số có dấu, cơ số 10 nhưng trả về `long`, có miền giá trị rộng hơn.
- `Integer.parseInt(String s, int radix)` và `Long.parseLong(String s, int radix)` đọc chuỗi theo **cơ số do người gọi lựa chọn**.

```java
int count = Integer.parseInt("123");
long distance = Long.parseLong("9000000000");
int binary = Integer.parseInt("101101", 2);

System.out.println(count);    // 123
System.out.println(distance); // 9000000000
System.out.println(binary);   // 45
```

Hai hàm trả về giá trị số nguyên thủy (`int` hoặc `long`), không giữ định dạng ban đầu như số `0` ở đầu hay việc viết chữ hoa/thường trong chuỗi hex. Khi `System.out.println` in một `int` hay `long` theo cách thông thường, kết quả được biểu diễn ở hệ thập phân, dù chuỗi ban đầu được parse ở cơ số khác.

## 2. Cơ số là gì?

Cơ số (`radix`) quy định có bao nhiêu giá trị chữ số và giá trị của mỗi vị trí. Ví dụ chuỗi `101101` ở cơ số 2 biểu thị:

\[
1\cdot 2^5 + 0\cdot 2^4 + 1\cdot 2^3 + 1\cdot 2^2 + 0\cdot 2^1 + 1\cdot 2^0 = 45.
\]

Nếu coi `"101101"` ở cơ số 10 thì giá trị khác hoàn toàn. Vì vậy, **biết chuỗi thôi chưa đủ; cần biết cơ số của chuỗi đó**.

Java cho phép `radix` từ `Character.MIN_RADIX` = 2 đến `Character.MAX_RADIX` = 36:

| Cơ số | Tên thường gặp | Chuỗi đầu vào | Lời gọi | Giá trị số |
| ---: | --- | --- | --- | ---: |
| 2 | Nhị phân | `"101101"` | `Integer.parseInt("101101", 2)` | 45 |
| 8 | Bát phân | `"+77"` | `Integer.parseInt("+77", 8)` | 63 |
| 10 | Thập phân | `"77"` | `Integer.parseInt("77", 10)` | 77 |
| 16 | Thập lục phân | `"7F"` | `Integer.parseInt("7F", 16)` | 127 |
| 36 | Cơ số 36 | `"z"` | `Integer.parseInt("z", 36)` | 35 |

Các cơ số đến 10 dùng chữ số `0`–`9` thích hợp. Ở cơ số lớn hơn 10, các chữ cái từ `a` đến `z` biểu diễn các giá trị từ 10 đến 35. Khi parse, chữ hoa và chữ thường tương đương: `Integer.parseInt("FF", 16)` và `Integer.parseInt("ff", 16)` đều là 255.

Một chữ số phải **nhỏ hơn cơ số**. Ví dụ `"19"` không hợp lệ ở cơ số 8 vì `9` không phải chữ số bát phân. Cơ số không hợp lệ như 1 hay 37 cũng dẫn đến `NumberFormatException`.

**Không truyền `radix` thì mặc định là 10**: `Integer.parseInt("010")` trả về 10, không tự chuyển sang hệ 8.

## 3. Dấu cộng, dấu trừ và chữ số

Một chuỗi nguyên có dấu có thể chứa `+` hoặc `-` ngay đầu:

```java
System.out.println(Integer.parseInt("42"));      // 42
System.out.println(Integer.parseInt("+42"));     // 42
System.out.println(Integer.parseInt("-42"));     // -42
System.out.println(Integer.parseInt("-0"));      // 0
System.out.println(Integer.parseInt("-1A", 16)); // -26
```

Trong `"-1A"` ở cơ số 16, phần chữ số `1A` biểu thị 26 ở hệ 10, dấu trừ biến giá trị thành -26. Ký tự dấu không được đứng giữa hoặc cuối số; `"+"` hoặc `"-"` đứng đơn độc cũng không phải đầu vào hợp lệ.

Với các số dương, dấu `+` là tùy chọn. Chuỗi `"-0"` sau khi parse là số nguyên `0`, vì kiểu `int` và `long` không có giá trị âm 0 riêng biệt.

Đối với số 16, các cách viết `"f"`, `"F"`, `"0f"` đều mô tả cùng giá trị 15, nhưng **cách viết của chuỗi đầu vào không được lưu trong giá trị số**.

## 4. Tiền tố 0x/0b và dấu gạch dưới không tự được hiểu

Trong **mã nguồn Java**, lập trình viên có thể viết các literal sau:

```java
int a = 0xFF;
int b = 0b1010;
int c = 1_000;
```

Nhưng chuỗi runtime `"0xFF"`, `"0b1010"`, `"1_000"` không được `parseInt` tự chấp nhận. Đây là khác biệt giữa **ngữ pháp literal số trong mã nguồn** và **định dạng văn bản đầu vào của API parse**.

Đúng:

```java
int a = Integer.parseInt("FF", 16);    // 255
int b = Integer.parseInt("1010", 2);  // 10
int c = Integer.parseInt("1000", 10); // 1000
```

Sai nếu gọi trực tiếp (các lời gọi minh họa dưới đây sẽ ném ngoại lệ):

```java
Integer.parseInt("0xFF", 16);
Integer.parseInt("0b1010", 2);
Integer.parseInt("1_000", 10);
```

Nếu hợp đồng dữ liệu cho phép tiền tố `0x`, hãy **kiểm tra tiền tố rồi loại nó trước khi parse**:

```java
String input = "0xFF";
if (input.startsWith("0x") || input.startsWith("0X")) {
    input = input.substring(2);
}
int result = Integer.parseInt(input, 16);
System.out.println(result); // 255
```

Đây là đoạn minh họa cho **đầu vào hex không âm**. Nếu dữ liệu có thể là `"-0xFF"`, phải thiết kế quy tắc xử lý dấu và tiền tố riêng, tránh cắt sai chuỗi. Java có `Integer.decode`/`Long.decode` nhận một số dạng tiền tố (và quy ước số 0 đầu cho bát phân), nhưng chúng có hợp đồng khác `parseInt(s, radix)`; không đổi API chỉ để tự suy đoán định dạng mà người dùng chưa yêu cầu.

## 5. int và long có giới hạn khác nhau

`int` là số nguyên có dấu 32 bit; `long` là số nguyên có dấu 64 bit:

| Kiểu | Nhỏ nhất | Lớn nhất |
| --- | ---: | ---: |
| `int` | -2,147,483,648 | 2,147,483,647 |
| `long` | -9,223,372,036,854,775,808 | 9,223,372,036,854,775,807 |

Ví dụ:

```java
long validLong = Long.parseLong("9000000000");
System.out.println(validLong); // 9000000000
```

Chuỗi `"9000000000"` vượt giới hạn `int` nhưng nằm trong `long`. Nếu dùng `Integer.parseInt("9000000000")` sẽ nhận `NumberFormatException`, không phải kết quả cắt bớt các chữ số.

Ngay cả khi tất cả ký tự đều là chữ số hợp lệ, **giá trị vẫn phải nằm trong phạm vi kiểu đích**:

```java
int maxInt = Integer.parseInt("7fffffff", 16);
int minInt = Integer.parseInt("-80000000", 16);

long maxLong = Long.parseLong("7fffffffffffffff", 16);
long minLong = Long.parseLong("-8000000000000000", 16);
```

Kết quả lần lượt là `Integer.MAX_VALUE`, `Integer.MIN_VALUE`, `Long.MAX_VALUE` và `Long.MIN_VALUE`. Ngược lại, `Integer.parseInt("80000000", 16)` lỗi vì đó là giá trị dương 2,147,483,648, vượt `Integer.MAX_VALUE`.

`"ffffffff"` ở hệ 16 là giá trị dương 4,294,967,295 nếu không có dấu âm, vì vậy `Integer.parseInt("ffffffff", 16)` cũng lỗi; `parseInt` không tự diễn giải chuỗi này thành bit pattern `-1`.

### Khi long vẫn chưa đủ

`Long.parseLong` cũng kiểm tra miền có dấu 64 bit. Với số nguyên lớn hơn `Long.MAX_VALUE`, dùng `java.math.BigInteger` khi phù hợp:

```java
import java.math.BigInteger;

BigInteger huge = new BigInteger("FFFFFFFFFFFFFFFF", 16);
System.out.println(huge); // 18446744073709551615
```

Đây là đoạn minh họa dùng `import` riêng. `BigInteger` là đối tượng số nguyên độ lớn tùy ý, có các phương thức tính toán khác với toán tử của kiểu nguyên thủy.

## 6. NumberFormatException: nguyên nhân và cách xử lý

`Integer.parseInt` và `Long.parseLong` ném `NumberFormatException` nếu chuỗi không thể được diễn giải thành giá trị kiểu đích theo cơ số yêu cầu.

Các lỗi phổ biến:

| Đầu vào | Lời gọi | Vì sao lỗi |
| --- | --- | --- |
| `""` | `Integer.parseInt("")` | Không có chữ số. |
| `"+"` | `Integer.parseInt("+")` | Chỉ có dấu, không có chữ số. |
| `" 42 "` | `Integer.parseInt(" 42 ")` | Không tự xóa khoảng trắng. |
| `"12.5"` | `Integer.parseInt("12.5")` | Không nhận phần thập phân. |
| `"19"` | `Integer.parseInt("19", 8)` | Chữ số `9` không hợp lệ ở cơ số 8. |
| `"0xFF"` | `Integer.parseInt("0xFF", 16)` | Không tự hiểu `0x` là tiền tố. |
| `"1_000"` | `Integer.parseInt("1_000")` | Dấu gạch dưới không được chấp nhận từ chuỗi. |
| `"2147483648"` | `Integer.parseInt("2147483648")` | Tràn miền `int`. |
| `"10"` | `Integer.parseInt("10", 1)` | Cơ số ngoài khoảng 2–36. |

Ví dụ bắt lỗi ở ranh giới nhập liệu:

```java
String raw = "2147483648";

try {
    int value = Integer.parseInt(raw);
    System.out.println(value);
} catch (NumberFormatException ex) {
    System.out.println("Không phải số int hợp lệ");
}
```

Đầu ra là `Không phải số int hợp lệ`. Đây không phải cơ chế tự động biến đầu vào sai thành 0. Một chuỗi vượt miền `int` bị từ chối rõ ràng, giúp ứng dụng tránh âm thầm dùng giá trị khác với ý định.

`null` cũng không phải một giá trị số. Trước khi xử lý từ biểu mẫu hoặc API, nên phân biệt **thiếu trường**, **chuỗi rỗng**, **chuỗi không hợp lệ**, **số ngoài miền** và **số hợp lệ** theo nhu cầu nghiệp vụ.

Nếu dữ liệu quy định có thể có khoảng trắng thừa, có thể dùng `raw.strip()` (Java 11+) trước khi parse; còn nếu khoảng trắng là lỗi định dạng, nên báo lỗi thay vì tự sửa. Không viết `catch (NumberFormatException e) { return 0; }` một cách tùy tiện vì sẽ đánh đồng “0 hợp lệ” với “giá trị nhập sai”.

## 7. Chương trình Java 17+ chạy độc lập

Lưu vào tệp `ParseRadixDemo.java`:

```java
import java.math.BigInteger;

public class ParseRadixDemo {
    private static void expectFailure(String label, String input, int radix) {
        try {
            Integer.parseInt(input, radix);
            System.out.println(label + ": unexpectedly accepted");
        } catch (NumberFormatException ex) {
            System.out.println(label + ": NumberFormatException");
        }
    }

    public static void main(String[] args) {
        System.out.println("decimal int: " + Integer.parseInt("120"));
        System.out.println("decimal long: " + Long.parseLong("9000000000"));
        System.out.println("binary: " + Integer.parseInt("101101", 2));
        System.out.println("octal: " + Integer.parseInt("+77", 8));
        System.out.println("hex: " + Integer.parseInt("7F", 16));
        System.out.println("hex signed: " + Integer.parseInt("-1A", 16));
        System.out.println("base36: " + Integer.parseInt("z", 36));
        System.out.println("int min hex: " +
                Integer.parseInt("-80000000", 16));
        System.out.println("long max hex: " +
                Long.parseLong("7fffffffffffffff", 16));
        System.out.println("long min hex: " +
                Long.parseLong("-8000000000000000", 16));

        System.out.println("hex prefix removed: " +
                Integer.parseInt("0xFF".substring(2), 16));

        String source = "prefix7fSuffix";
        System.out.println("slice: " +
                Integer.parseInt(source, 6, 8, 16));
        System.out.println("back to hex: " +
                Integer.toString(255, 16));

        System.out.println("unsigned int hex: " +
                Integer.toUnsignedString(-1, 16));
        System.out.println("unsigned long parsed: " +
                Long.toUnsignedString(
                        Long.parseUnsignedLong("ffffffffffffffff", 16)));

        expectFailure("prefix direct", "0xFF", 16);
        expectFailure("int overflow", "2147483648", 10);
        expectFailure("bad digit", "19", 8);
        expectFailure("spaces", " 42 ", 10);
        expectFailure("underscore", "1_000", 10);
        expectFailure("radix", "10", 1);

        // Các Unicode escapes là chữ số Arabic-Indic 1, 2, 3.
        System.out.println("unicode digit: " +
                Integer.parseInt("\u0661\u0662\u0663", 10));

        System.out.println("big integer: " +
                new BigInteger("FFFFFFFFFFFFFFFF", 16));
    }
}
```

Biên dịch và chạy:

```bash
javac ParseRadixDemo.java
java ParseRadixDemo
```

Đầu ra:

```text
decimal int: 120
decimal long: 9000000000
binary: 45
octal: 63
hex: 127
hex signed: -26
base36: 35
int min hex: -2147483648
long max hex: 9223372036854775807
long min hex: -9223372036854775808
hex prefix removed: 255
slice: 127
back to hex: ff
unsigned int hex: ffffffff
unsigned long parsed: 18446744073709551615
prefix direct: NumberFormatException
int overflow: NumberFormatException
bad digit: NumberFormatException
spaces: NumberFormatException
underscore: NumberFormatException
radix: NumberFormatException
unicode digit: 123
big integer: 18446744073709551615
```

Trong chương trình, những chuỗi sai được truyền vào `expectFailure` để bắt ngoại lệ và tiếp tục chạy các ví dụ sau. Còn những chuỗi hợp lệ được chuyển thành giá trị và in ra, cho thấy một số có thể được đọc từ nhiều cơ số khác nhau nhưng vẫn biểu diễn như số nguyên bình thường.

Đặc biệt, `Integer.parseInt(source, 6, 8, 16)` đọc đoạn có chỉ số từ 6 (bao gồm) đến 8 (không bao gồm) của `"prefix7fSuffix"`, tức `"7f"` ở hệ 16 có giá trị 127.

## 8. Overload đọc một đoạn CharSequence

Từ Java 9, ngoài việc parse cả chuỗi, Java hỗ trợ các overload:

```text
Integer.parseInt(CharSequence s, int beginIndex, int endIndex, int radix)
Long.parseLong(CharSequence s, int beginIndex, int endIndex, int radix)
```

Ví dụ:

```java
String input = "id=7F;status=ok";
int value = Integer.parseInt(input, 3, 5, 16);
System.out.println(value); // 127
```

Đoạn `[3, 5)` là `"7F"`. Cách này có thể tránh tạo `substring` trung gian khi xử lý một phần bộ đệm văn bản hoặc `CharSequence`. Nhưng với chuỗi nhỏ, `substring(...)` rồi parse cũng hoàn toàn phù hợp nếu cách đó dễ đọc hơn.

Khi sử dụng chỉ số, cần phân biệt **chỉ số ngoài phạm vi** hoặc khoảng chỉ số sai (có thể gây `IndexOutOfBoundsException`) với **nội dung đoạn không phải số hợp lệ** (gây `NumberFormatException`). Nếu đầu vào không đáng tin cậy, phải kiểm tra cả hai khả năng.

## 9. Parse số không dấu: khác với parse có dấu

Một số định dạng dữ liệu lưu các số nguyên dưới dạng **bit pattern không dấu**. Đó là trường hợp riêng với `parseInt`/`parseLong` thông thường.

- `Integer.parseUnsignedInt(s, radix)` chấp nhận giá trị từ 0 đến \(2^{32}-1\).
- `Long.parseUnsignedLong(s, radix)` chấp nhận giá trị từ 0 đến \(2^{64}-1\).
- `Integer.toUnsignedString(value, radix)` và `Long.toUnsignedString(value, radix)` hiển thị bit pattern theo cách hiểu không dấu.

Ví dụ:

```java
int bits = Integer.parseUnsignedInt("ffffffff", 16);
System.out.println(bits); // -1 nếu in như signed int
System.out.println(Integer.toUnsignedString(bits));
// 4294967295

long longBits = Long.parseUnsignedLong("ffffffffffffffff", 16);
System.out.println(Long.toUnsignedString(longBits));
// 18446744073709551615
```

Java vẫn lưu `bits` trong kiểu `int` và `longBits` trong `long`; không có kiểu nguyên thủy `uint` hoặc `ulong`. Khi in hay tính toán có dấu, bit pattern trên có thể xuất hiện dưới dạng số âm. Nếu ứng dụng muốn xử lý không dấu, cần dùng các phép so sánh/chia và định dạng unsigned tương ứng, hoặc chọn `BigInteger` khi cần xử lý số lớn theo cách thông thường.

**Không dùng `parseUnsignedInt` chỉ để né một lỗi dữ liệu.** Chỉ dùng khi hợp đồng đầu vào thật sự quy định giá trị không dấu.

## 10. Chuyển ngược từ số sang chuỗi theo cơ số

Hướng `parseInt`/`parseLong` là **chuỗi → số**. Hướng ngược lại là `Integer.toString(value, radix)` hoặc `Long.toString(value, radix)`:

```java
int number = 255;

System.out.println(Integer.toString(number));     // 255
System.out.println(Integer.toString(number, 2));  // 11111111
System.out.println(Integer.toString(number, 8));  // 377
System.out.println(Integer.toString(number, 16)); // ff
System.out.println(Integer.toString(number, 36)); // 73
```

Với số âm, cần hiểu sự khác nhau giữa **giá trị có dấu** và **bit pattern**:

```java
System.out.println(Integer.toString(-1, 16));          // -1
System.out.println(Integer.toHexString(-1));           // ffffffff
System.out.println(Integer.toUnsignedString(-1, 16));  // ffffffff
```

`toString(-1, 16)` giữ cách hiểu số âm có dấu. `toHexString(-1)` và `toUnsignedString(-1, 16)` thể hiện 32 bit như một giá trị không dấu.

Phép chuyển số rồi chuyển ngược **không bảo toàn hình thức chuỗi đầu vào**:

```java
int n = Integer.parseInt("000A", 16);
System.out.println(Integer.toString(n, 16)); // a
```

Các số `0` dẫn đầu và chữ `A` viết hoa đã biến mất. Nếu đó là thông tin nghiệp vụ (mã định danh, trường số có độ rộng cố định), hãy giữ chuỗi gốc hoặc áp dụng format riêng.

## 11. Chữ số Unicode và kiểm tra đầu vào

Việc phân tích chữ số của Java sử dụng cơ chế xác định chữ số theo `Character.digit`. Một số chữ số Unicode ngoài dải ASCII cũng được chấp nhận, ví dụ:

```java
int n = Integer.parseInt("\u0661\u0662\u0663", 10);
System.out.println(n); // 123
```

Dãy escape tương ứng với ba chữ số Arabic-Indic 1, 2, 3. Điều này **không có nghĩa** mọi ký tự số trên thế giới đều được parser chấp nhận, hay parser tự xử lý dấu phân nhóm và dấu thập phân trong một locale.

Nếu định dạng giao thức bắt buộc chỉ chứa chữ số ASCII từ `'0'` đến `'9'`, cần kiểm tra chính sách đó riêng trước khi parse. Ngược lại, với nhập liệu quốc tế, không mặc định chỉ có chữ số ASCII hợp lệ. `Integer.parseInt` và `Long.parseLong` phân tích số nguyên theo cơ số, **không thay thế bộ phân tích số được địa phương hóa**.

## 12. Parse số và kiểm tra quy tắc nghiệp vụ

Một chuỗi parse thành `int` hợp lệ vẫn có thể không hợp lệ đối với ứng dụng. Ví dụ số cổng TCP phải nằm trong khoảng được hệ thống quy định, không phải mọi giá trị `int` đều là số cổng hợp lệ.

```java
static int parsePort(String raw) {
    final int port;
    try {
        port = Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
                "Cổng phải là số nguyên hợp lệ", ex);
    }

    if (port < 1 || port > 65535) {
        throw new IllegalArgumentException(
                "Cổng phải nằm trong khoảng 1..65535");
    }
    return port;
}
```

Đây là **phương thức minh họa** đặt trong một lớp Java; nó không phải chương trình độc lập. Mã kiểm tra cú pháp/miền `int` qua `parseInt`, sau đó kiểm tra quy tắc nghiệp vụ của cổng trong đoạn 1–65535.

Ngoài ra, phân biệt `Integer.parseInt` (trả kiểu nguyên thủy `int`) với `Integer.valueOf` (trả đối tượng `Integer`), và `Long.parseLong` (trả `long`) với `Long.valueOf` (trả `Long`). Các overload chuỗi/cơ số có cùng ý nghĩa phân tích số nhưng khác loại kết quả. Nếu chỉ cần phép tính số nguyên thủy, `parseInt`/`parseLong` diễn đạt trực tiếp mục đích hơn.

## 13. Bài tập tự kiểm tra

Hãy dự đoán trước khi chạy:

| Biểu thức | Kết quả |
| --- | --- |
| `Integer.parseInt("0012")` | `12` |
| `Integer.parseInt("010", 10)` | `10` |
| `Integer.parseInt("010", 2)` | `2` |
| `Integer.parseInt("+7f", 16)` | `127` |
| `Integer.parseInt("Z", 36)` | `35` |
| `Integer.parseInt("19", 8)` | `NumberFormatException` |
| `Integer.parseInt("2147483648")` | `NumberFormatException` |
| `Long.parseLong("2147483648")` | `2147483648` |
| `Integer.parseInt("0x10", 16)` | `NumberFormatException` |
| `Integer.toString(31, 16)` | `"1f"` |
| `Integer.toString(-31, 16)` | `"-1f"` |

Thử tự xây dựng chức năng nhận hai chuỗi đầu vào: số dạng văn bản và một lựa chọn cơ số 2, 8, 10 hoặc 16. Parse thành `int`, bắt `NumberFormatException` để báo lỗi rõ ràng, sau đó in kết quả bằng `Integer.toString(value, 10)`. Kiểm thử số âm, chữ số sai cơ số, chuỗi trống, khoảng trắng, tiền tố `0x` và tràn miền. Sau đó mở rộng sang `long` để so sánh.

## 14. Ghi nhớ

- `Integer.parseInt(s)` và `Long.parseLong(s)` đọc số nguyên **có dấu theo hệ 10**; kiểu trả về lần lượt là `int` và `long`.
- Overload nhận `radix` hỗ trợ **cơ số 2–36**, giúp diễn giải nhị phân, bát phân, thập lục phân và các cơ số khác.
- Chữ số phải hợp lệ theo cơ số, và **giá trị phải nằm trong miền kiểu đích**; sai cú pháp, cơ số hoặc vượt phạm vi gây `NumberFormatException`.
- Dấu `+`, `-` đầu chuỗi được hỗ trợ; tiền tố `0x`/`0b`, gạch dưới, khoảng trắng, dấu phân nhóm hoặc phần thập phân không được parse trực tiếp.
- `Long.parseLong` có miền rộng hơn `Integer.parseInt`; số nguyên vượt `long` cần `BigInteger` hoặc mô hình dữ liệu khác.
- Với **bit pattern không dấu**, dùng các API `parseUnsignedInt`/`parseUnsignedLong` tương ứng; đừng lẫn ý nghĩa unsigned với signed.
- `Integer.toString(value, radix)` và `Long.toString(value, radix)` chuyển số trở lại chuỗi nhưng không phục hồi định dạng nguyên bản như các số `0` ở đầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-conversion-006">BigInteger, BigDecimal và giới hạn biểu diễn qua số thực</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger, BigDecimal và giới hạn biểu diễn qua số thực trong Java

## 1. Vì sao phải quan tâm đến độ chính xác khi chuyển từ String?

Khi nhận dữ liệu dưới dạng `String`, ví dụ mã giao dịch `"9007199254740993"` hoặc số tiền `"10.005"`, việc chuyển qua `double` hoặc `float` có thể **mất thông tin trước khi chương trình bắt đầu tính toán**.

Trong Java:

- `BigInteger` biểu diễn **số nguyên có độ lớn tùy ý** về mặt API; giới hạn thực tế do bộ nhớ, kích thước mảng và chi phí tính toán. Nó giữ chính xác các chữ số nguyên mà `int`, `long` hoặc `double` không giữ nổi.
- `BigDecimal` biểu diễn **giá trị thập phân chính xác** bằng một số nguyên không chia tỷ lệ (unscaled value) và một `scale`; rất hữu ích khi chuỗi đầu vào là số tiền, tỷ lệ hoặc số đo thập phân cần quy tắc làm tròn rõ ràng.
- `float` và `double` là **số thực dấu chấm động nhị phân**, chỉ biểu diễn chính xác một tập hữu hạn các giá trị. Nhiều phân số thập phân quen thuộc như `0.1` không có biểu diễn hữu hạn trong hệ nhị phân, dù giá trị in ra có thể trông như `0.1`.

**Nguyên tắc xuyên suốt:** Nếu chuỗi nguồn mang số thập phân cần chính xác, hãy parse **trực tiếp chuỗi** sang `BigDecimal`; nếu mang số nguyên lớn, parse trực tiếp sang `BigInteger`. Tránh vòng `String → double → BigDecimal` hoặc `String → double → BigInteger` vì thông tin mất tại bước `double` sẽ không tự quay lại.

## 2. Mô hình biểu diễn: nguyên lớn, thập phân chính xác và số thực nhị phân

### 2.1. BigInteger: chính xác ở miền số nguyên

`BigInteger` lưu số nguyên với độ chính xác tùy ý và cung cấp các phép `add`, `subtract`, `multiply`, `divide`, `mod`, `pow`, v.v. Ví dụ:

```java
BigInteger population = new BigInteger("9007199254740993");
BigInteger next = population.add(BigInteger.ONE);

System.out.println(next); // 9007199254740994
```

`new BigInteger("9007199254740993")` đọc đầy đủ mọi chữ số của chuỗi. `BigInteger` là kiểu **bất biến (immutable)**: `add` trả về đối tượng kết quả mới; giá trị `population` không bị thay đổi. Với chuỗi số nguyên có dấu `-`, constructor đọc dấu âm đúng cách; với các cơ số khác nhau có thể dùng `new BigInteger(text, radix)`.

### 2.2. BigDecimal: giá trị thập phân = unscaled × 10^(-scale)

`BigDecimal` mô tả giá trị theo công thức:

```text
value = unscaledValue × 10^(-scale)

"123.4500" → unscaledValue = 1234500, scale = 4
"1.23"    → unscaledValue = 123,     scale = 2
"1E+3"    → unscaledValue = 1,       scale = -3
```

Ví dụ:

```java
BigDecimal amount = new BigDecimal("123.4500");

System.out.println(amount.unscaledValue()); // 1234500
System.out.println(amount.scale());         // 4
System.out.println(amount.toPlainString()); // 123.4500
```

`scale` không phải số chữ số có nghĩa (precision). Hai giá trị `new BigDecimal("1.0")` và `new BigDecimal("1.00")` so sánh bằng nhau về **giá trị số học**, nhưng có scale khác nhau.

### 2.3. double: hữu hạn bit và quy tắc làm tròn nhị phân

`double` dùng định dạng IEEE 754 binary64 thông dụng, có **53 bit độ chính xác phần trị** (gồm bit ẩn). Vì vậy, nó biểu diễn **chính xác mọi số nguyên có trị tuyệt đối không vượt quá `2^53 = 9_007_199_254_740_992`**. Khi vượt ngưỡng, **không phải mọi số nguyên liên tiếp** đều còn biểu diễn chính xác; không có nghĩa tất cả số lớn hơn ngưỡng đều sai.

`float` có 24 bit precision của phần trị, nên giới hạn bảo đảm cho mọi số nguyên liên tiếp là `2^24 = 16_777_216`. Cả hai còn có giới hạn **phạm vi** biểu diễn; độ chính xác và phạm vi là hai khái niệm khác nhau.

Ví dụ:

```java
BigInteger original = new BigInteger("9007199254740993");
double rounded = original.doubleValue();

System.out.println(original);                            // 9007199254740993
System.out.println(BigDecimal.valueOf(rounded)
        .toPlainString());                                 // 9007199254740992
```

Giá trị đã đổi thành số nguyên kề bên sau khi qua `double`. Ép trở lại `BigInteger` hoặc `BigDecimal` chỉ lưu **giá trị đã bị làm tròn**; không biết số ban đầu là gì.

## 3. Parse trực tiếp từ String để bảo toàn dữ liệu

### 3.1. Chuỗi số nguyên → BigInteger

```java
String rawId = "9007199254740993";
BigInteger exactId = new BigInteger(rawId);

System.out.println(exactId);  // 9007199254740993
System.out.println(exactId.add(BigInteger.ONE));
// 9007199254740994
```

Với hệ thập lục phân:

```java
BigInteger hex = new BigInteger("FF", 16);
System.out.println(hex); // 255
```

Constructor `BigInteger(String)` đòi cú pháp số nguyên hợp lệ; `"1.5"`, `"1e3"` hoặc chuỗi có khoảng trắng thừa không phải dạng số nguyên thập phân hợp lệ của constructor đó. Có thể chuẩn hóa `strip()` nếu giao thức dữ liệu cho phép khoảng trắng ở hai đầu; không tự loại dấu phân nhóm hoặc dấu phân cách hàng nghìn khi chưa định nghĩa định dạng.

### 3.2. Chuỗi thập phân → BigDecimal

```java
String rawMoney = "0.10";
BigDecimal money = new BigDecimal(rawMoney);

System.out.println(money.toPlainString()); // 0.10
System.out.println(money.scale());         // 2
```

`BigDecimal(String)` giữ **giá trị thập phân được biểu diễn trong văn bản** và scale tương ứng. Không cần đi qua `Double.parseDouble(rawMoney)`. Với `"00123.4500"`, giá trị số học được lưu chính xác, nhưng thông tin **định dạng ban đầu** như các số `0` đứng trước không được giữ nguyên trong `BigDecimal`; nếu phải khôi phục chính xác chuỗi gốc, hãy lưu riêng chuỗi đầu vào.

Một số trường hợp hợp lệ và không hợp lệ:

| Chuỗi | Cách xử lý |
| --- | --- |
| `"123456789012345678901234567890"` | `new BigInteger(text)` giữ đúng số nguyên lớn |
| `"-100.0250"` | `new BigDecimal(text)` giữ chính xác số thập phân và scale |
| `"1.23E+4"` | `new BigDecimal(text)` chấp nhận ký pháp khoa học |
| `"1,25"` | Không phải cú pháp decimal dùng dấu chấm mà constructor mong đợi; cần parser theo locale nếu dữ liệu thật sự có dấu phẩy thập phân |
| `"NaN"` hoặc `"Infinity"` | Không phải số thập phân hữu hạn của `BigDecimal(String)` |
| `" 42 "` | Cần quyết định có `strip()` hai đầu hay coi là dữ liệu không hợp lệ |

`NumberFormatException` là ngoại lệ thường gặp khi chuỗi không hợp lệ. Với dữ liệu người dùng, xử lý rõ ràng thay vì mặc định các chuỗi đều parse thành công.

## 4. Sự khác nhau giữa new BigDecimal(0.1) và BigDecimal.valueOf(0.1)

Ví dụ kinh điển:

```java
System.out.println(new BigDecimal(0.1));
// 0.1000000000000000055511151231257827021181583404541015625

System.out.println(BigDecimal.valueOf(0.1));
// 0.1

System.out.println(new BigDecimal("0.1"));
// 0.1
```

Ba trường hợp khác nhau:

1. `new BigDecimal(0.1)` nhận **giá trị binary64 của literal `0.1`**, tức giá trị nhị phân gần `0.1` nhất. Constructor biểu diễn **chính xác giá trị double đã nhận**, nên phần đuôi dài lộ ra.
2. `BigDecimal.valueOf(0.1)` dựa trên biểu diễn thập phân mà `Double.toString(0.1)` trả về, ở đây là `"0.1"`; vì vậy kết quả dễ đọc, giá trị decimal là `0.1`.
3. `new BigDecimal("0.1")` đọc thẳng văn bản thập phân, không có giai đoạn qua `double`. Đây là lựa chọn đúng khi cần bảo toàn chính xác số thập phân từ dữ liệu nguồn.

**Cảnh báo về `valueOf(double)`:** Nó không thể khôi phục thông tin đã mất ở phép tính double trước đó:

```java
double sum = 0.1 + 0.2;

System.out.println(sum);                       // 0.30000000000000004
System.out.println(BigDecimal.valueOf(sum));   // 0.30000000000000004
System.out.println(new BigDecimal("0.1")
        .add(new BigDecimal("0.2")));           // 0.3
```

Do đó `BigDecimal.valueOf(double)` hữu ích khi **bắt buộc** giao tiếp với một API trả `double`, nhưng không đồng nghĩa “chuyển double thành số thập phân ban đầu một cách hoàn hảo”. Nếu đầu vào gốc là `String`, hãy parse thẳng.

## 5. Chuyển đổi chính xác, chuyển đổi bị cắt và chuyển đổi mất mát

### 5.1. BigInteger → int/long

`BigInteger.intValue()` và `BigInteger.longValue()` có thể **mất các bit cao** khi giá trị không vừa kiểu đích, không nhất thiết ném ngoại lệ. Khi nghiệp vụ đòi nguyên vẹn, dùng các phiên bản `intValueExact()` và `longValueExact()`:

```java
BigInteger small = new BigInteger("123");
System.out.println(small.intValueExact()); // 123

BigInteger oversized = new BigInteger("9223372036854775808");
// oversized.longValueExact() ném ArithmeticException
```

Giá trị `9223372036854775808` lớn hơn `Long.MAX_VALUE` đúng 1. Dùng `longValueExact()` để phát hiện vấn đề trước khi ghi vào bảng dữ liệu hoặc gọi API chỉ nhận `long`. Tương tự, không dùng `Integer.parseInt(...)` hay `Long.parseLong(...)` cho chuỗi có thể vượt miền tương ứng; chúng ném `NumberFormatException` khi tràn giới hạn.

### 5.2. BigDecimal → BigInteger: mất phần lẻ

`BigDecimal.toBigInteger()` **cắt bỏ phần thập phân về phía 0**, không làm tròn theo `HALF_UP` và không ném lỗi vì có phần lẻ:

```java
System.out.println(new BigDecimal("99.9").toBigInteger()); // 99
System.out.println(new BigDecimal("-99.9").toBigInteger()); // -99
```

Ngược lại, `toBigIntegerExact()` ném `ArithmeticException` nếu phần thập phân khác 0. Ví dụ `new BigDecimal("99.0").toBigIntegerExact()` hợp lệ vì phần lẻ bằng 0, nhưng `new BigDecimal("99.9").toBigIntegerExact()` không hợp lệ.

Muốn làm tròn trước khi lấy số nguyên thì chọn chính sách rõ ràng:

```java
BigInteger rounded = new BigDecimal("99.9")
        .setScale(0, RoundingMode.HALF_UP)
        .toBigIntegerExact();
System.out.println(rounded); // 100
```

### 5.3. BigDecimal → int/long

`intValue()`, `longValue()` có thể bỏ phần lẻ và/hoặc mất bit do vượt phạm vi. `intValueExact()`, `longValueExact()` ném `ArithmeticException` nếu có phần lẻ không bằng 0 hoặc phần nguyên không vừa kiểu đích. Dùng phiên bản `Exact` tại ranh giới của các API đòi kiểu nguyên cố định.

### 5.4. BigInteger/BigDecimal → float/double

`doubleValue()` và `floatValue()` là chuyển đổi **có thể mất độ chính xác**. Nếu độ lớn vượt phạm vi số thực nhị phân, kết quả có thể là `Infinity` hoặc `-Infinity`; nếu giá trị quá nhỏ (với `BigDecimal`), chuyển đổi có thể làm tròn thành `0.0`. Không dùng `doubleValue()` cho giá trị định danh, số tiền hoặc phép so sánh bắt buộc chính xác.

Không có bảo đảm rằng `new BigDecimal(...).doubleValue()` rồi `BigDecimal.valueOf(...)` sẽ giữ nguyên `BigDecimal` ban đầu.

## 6. Ví dụ Java 17+ chạy độc lập

Tạo file **`BigNumbersFromStringDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class BigNumbersFromStringDemo {
    public static void main(String[] args) {
        BigInteger id = new BigInteger("9007199254740993");
        System.out.println("integer exact = " + id);
        System.out.println("double roundtrip = "
                + BigDecimal.valueOf(id.doubleValue()).toPlainString());

        System.out.println("new BigDecimal(0.1) = " + new BigDecimal(0.1));
        System.out.println("valueOf(0.1) = " + BigDecimal.valueOf(0.1));

        double binarySum = 0.1 + 0.2;
        BigDecimal decimalSum = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("double sum = " + binarySum);
        System.out.println("decimal sum = " + decimalSum);

        BigDecimal rounded = new BigDecimal("10.005")
                .setScale(2, RoundingMode.HALF_UP);
        System.out.println("round = " + rounded);

        BigDecimal third = BigDecimal.ONE.divide(
                new BigDecimal("3"), 4, RoundingMode.HALF_UP);
        System.out.println("third = " + third);

        BigDecimal one = new BigDecimal("1.0");
        BigDecimal anotherOne = new BigDecimal("1.00");
        System.out.println("equals = " + one.equals(anotherOne));
        System.out.println("compareTo = " + one.compareTo(anotherOne));

        BigDecimal fractional = new BigDecimal("99.9");
        System.out.println("truncated = " + fractional.toBigInteger());
        try {
            fractional.toBigIntegerExact();
        } catch (ArithmeticException ex) {
            System.out.println("fraction rejected");
        }

        System.out.println("plain = " + new BigDecimal("1000.00")
                .stripTrailingZeros().toPlainString());
        System.out.println("scientific = " + new BigDecimal("1000.00")
                .stripTrailingZeros().toString());
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac --release 17 BigNumbersFromStringDemo.java
java BigNumbersFromStringDemo
```

Stdout mong đợi (đã kiểm tra các biểu thức bằng JDK, dùng các API có trên Java 17):

```text
integer exact = 9007199254740993
double roundtrip = 9007199254740992
new BigDecimal(0.1) = 0.1000000000000000055511151231257827021181583404541015625
valueOf(0.1) = 0.1
double sum = 0.30000000000000004
decimal sum = 0.3
round = 10.01
third = 0.3333
equals = false
compareTo = 0
truncated = 99
fraction rejected
plain = 1000
scientific = 1E+3
```

Giải thích:

- `id` được đọc chính xác từ `String`. Chuyển qua `doubleValue()` làm số `9007199254740993` thành `9007199254740992`: sai khác đúng 1 đơn vị.
- `new BigDecimal(0.1)` diễn tả chính xác giá trị **binary64** xấp xỉ `0.1`; `valueOf(0.1)` chọn chuỗi thập phân ngắn tương ứng. `new BigDecimal("0.1")` không đi qua binary64.
- Phép cộng hai `BigDecimal` tạo từ chuỗi cho `0.3`; phép cộng double cho `0.30000000000000004`.
- `setScale(2, HALF_UP)` làm tròn `10.005` thành `10.01`. `1 / 3` cần scale và rounding vì không có biểu diễn thập phân hữu hạn chính xác.
- `equals` so cả giá trị và scale, nên `1.0` khác `1.00` theo `equals`. `compareTo` so giá trị số học, trả `0`.
- `toBigInteger` cắt phần lẻ; `toBigIntegerExact` bảo vệ khỏi việc bỏ lẻ âm thầm.
- `stripTrailingZeros()` có thể làm scale âm nên `toString()` in dạng `1E+3`; `toPlainString()` in `1000`.

## 7. Scale, precision và làm tròn trong BigDecimal

### 7.1. Scale khác precision

`scale` biểu thị vị trí dấu thập phân theo hệ số 10. `precision()` biểu thị **số chữ số có nghĩa** của unscaled value. Ví dụ:

```java
BigDecimal x = new BigDecimal("12.3400");
System.out.println(x.scale());     // 4
System.out.println(x.precision()); // 6
```

Độ chính xác toán học và số chữ số hiển thị là hai vấn đề riêng: `"1.00"` và `"1.0"` bằng nhau về giá trị, nhưng scale khác nhau.

### 7.2. Chọn chính sách làm tròn một cách tường minh

`setScale(targetScale, mode)` thay đổi scale và có thể làm tròn:

```java
BigDecimal price = new BigDecimal("10.005");

System.out.println(price.setScale(2, RoundingMode.HALF_UP));  // 10.01
System.out.println(price.setScale(2, RoundingMode.HALF_EVEN)); // 10.00
```

`HALF_UP` tăng độ lớn khi gặp trường hợp đúng nửa (ví dụ số dương 10.005 ở scale 2), còn `HALF_EVEN` chọn chữ số cuối chẵn trong trường hợp đúng nửa. Chính sách nào đúng phụ thuộc quy định tính toán của hệ thống, không có một kiểu làm tròn luôn đúng cho mọi loại tiền và thuế.

Nếu yêu cầu tuyệt đối **không được làm tròn**, dùng `RoundingMode.UNNECESSARY`. Khi buộc phải bỏ một chữ số có nghĩa, phép toán sẽ ném `ArithmeticException`.

### 7.3. Phép chia không kết thúc

`new BigDecimal("1").divide(new BigDecimal("3"))` ném `ArithmeticException` vì `1/3` không có biểu diễn thập phân hữu hạn. Chỉ rõ scale + rounding, hoặc dùng `MathContext`:

```java
BigDecimal a = new BigDecimal("1");
BigDecimal b = new BigDecimal("3");

System.out.println(a.divide(b, 2, RoundingMode.HALF_UP)); // 0.33

MathContext mc = new MathContext(5, RoundingMode.HALF_EVEN);
System.out.println(a.divide(b, mc)); // 0.33333
```

`MathContext(5, ...)` giới hạn **5 chữ số có nghĩa (precision)**, không phải 5 chữ số sau dấu thập phân (scale). Nhớ `import java.math.MathContext;` khi sử dụng đoạn mã này trong một lớp riêng.

### 7.4. So sánh giá trị và khóa trong collection

`BigDecimal.equals` xét cả value lẫn scale, còn `compareTo` chỉ xét thứ tự số học:

```java
BigDecimal x = new BigDecimal("1.0");
BigDecimal y = new BigDecimal("1.00");

System.out.println(x.equals(y));        // false
System.out.println(x.compareTo(y) == 0); // true
```

Vì vậy `HashSet<BigDecimal>` và `TreeSet<BigDecimal>` có thể cho kết quả khác về việc coi hai giá trị này là trùng, do quy tắc so bằng nhau khác nhau. Cần xác định chính sách chuẩn hóa scale và chính sách so sánh trước khi dùng trong map, set hoặc khóa nghiệp vụ.

## 8. Chuyển số chính xác trở lại String

| API | Kết quả / ý nghĩa |
| --- | --- |
| `bigInteger.toString()` | Văn bản số nguyên thập phân chính xác |
| `bigInteger.toString(16)` | Văn bản ở hệ 16, không làm mất giá trị |
| `bigDecimal.toString()` | Dạng chuẩn của đối tượng, có thể chứa ký pháp khoa học `E` |
| `bigDecimal.toPlainString()` | Dạng thập phân không dùng số mũ |
| `bigDecimal.toEngineeringString()` | Có thể dùng lũy thừa của 10 với số mũ bội của 3 |
| `bigDecimal.stripTrailingZeros()` | Loại số `0` thừa ở cuối; có thể thay đổi scale và cách `toString()` hiển thị |

Ví dụ:

```java
BigDecimal raw = new BigDecimal("1000.00");

System.out.println(raw.toPlainString()); // 1000.00
System.out.println(raw.stripTrailingZeros().toString()); // 1E+3
System.out.println(raw.stripTrailingZeros().toPlainString()); // 1000
```

Nếu dữ liệu đầu vào yêu cầu **định dạng hiển thị cố định hai chữ số phần lẻ**, có thể dùng `setScale(2, roundingMode)` trước khi xuất, nhưng chỉ sau khi xác định quy tắc làm tròn. Không đồng nhất việc giữ **giá trị số học** với việc giữ **nguyên văn chuỗi đầu vào** (dấu `+`, chữ số `0` dẫn đầu, ký pháp `E`, dấu phân nhóm). Cần lưu chuỗi gốc nếu những thông tin đó là bắt buộc.

## 9. Các luồng chuyển đổi nên dùng và nên tránh

### 9.1. Đúng: String → BigInteger/BigDecimal → phép toán chính xác

```text
"9007199254740993" --BigInteger(String)--> 9007199254740993
"0.1" --BigDecimal(String)--> decimal 0.1
"0.2" --BigDecimal(String)--> decimal 0.2
decimal 0.1 + decimal 0.2 --> decimal 0.3
```

Không có bước ép qua `float` hoặc `double`. Dùng `BigDecimal` cho số tiền cần đơn vị thập phân, `BigInteger` cho số nguyên lớn không có phần lẻ.

### 9.2. Có thể mất mát: String → double → BigDecimal

```text
"9007199254740993"
    -> Double.parseDouble(...)
    -> 9007199254740992 (đã làm tròn)
    -> BigDecimal.valueOf(...) = 9007199254740992
```

Ngay cả khi sau đó chuyển thành `BigInteger`, số ban đầu vẫn đã mất. Nếu đã có `double` từ hệ thống ngoài, phải chấp nhận và mô tả độ chính xác đầu vào của hệ thống đó; `BigDecimal` không “sửa” được lịch sử tính toán.

### 9.3. Có thể mất mát: BigDecimal → BigInteger, long hoặc double

- `toBigInteger()` bỏ phần lẻ.
- `longValue()` có thể cắt phần lẻ hoặc làm mất bit.
- `doubleValue()` có thể làm tròn, tràn thành Infinity, hoặc mất giá trị rất nhỏ.
- Dùng `toBigIntegerExact()` / `longValueExact()` khi cần từ chối đầu vào không bảo toàn được.

### 9.4. Khi bắt buộc làm việc với float/double

Một số thư viện khoa học và API legacy nhận `double`; khi chuyển qua, hãy kiểm tra phạm vi, hữu hạn (`Double.isFinite(value)`) và độ chính xác chấp nhận được. `BigDecimal.valueOf(Double.NaN)` / `BigDecimal.valueOf(Double.POSITIVE_INFINITY)` không thể tạo đối tượng BigDecimal hữu hạn và ném `NumberFormatException`.

Nếu tài chính yêu cầu chính xác tuyệt đối theo đơn vị nhỏ nhất, cân nhắc mô hình **số nguyên đơn vị nhỏ** (`BigInteger` hoặc `long` đủ phạm vi) hoặc `BigDecimal` với scale và rounding được quy định thống nhất.

## 10. Sai lầm thường gặp

1. **Parse sang double trước rồi tạo BigDecimal.** `new BigDecimal(Double.parseDouble(text))` có thể chứa giá trị gần đúng nhị phân; `BigDecimal.valueOf(Double.parseDouble(text))` cũng không khôi phục được tất cả chữ số nếu double đã làm tròn. Dùng `new BigDecimal(text)`.
2. **Dùng BigDecimal.valueOf(0.1) rồi kết luận phép cộng double là chính xác.** `valueOf` chỉ chuyển đổi biểu diễn của giá trị double đã có; `valueOf(0.1 + 0.2)` vẫn ra `0.30000000000000004`.
3. **Dùng double cho ID/số đếm lớn.** Khi số nguyên vượt ngưỡng biểu diễn liên tiếp chính xác, hai giá trị khác nhau có thể thành cùng một double. Giữ dạng `String` hoặc `BigInteger`.
4. **Dùng BigInteger để biểu diễn phần lẻ.** `new BigInteger("12.34")` gây `NumberFormatException`. Dùng `BigDecimal` nếu cần phần lẻ.
5. **Tin rằng `BigInteger.longValue()` luôn chính xác.** Nếu tràn phạm vi, phép đổi có thể mất bit. Dùng `longValueExact()` khi mất mát là lỗi.
6. **Tin rằng `BigDecimal.toBigInteger()` làm tròn.** Nó cắt phần lẻ hướng về 0. Chọn `setScale(0, mode)` trước, hoặc dùng `toBigIntegerExact()`.
7. **Gọi `divide` mà không xác định quy tắc chia.** Với phân số vô hạn như `1/3`, mặc định có thể ném `ArithmeticException`. Chỉ rõ `scale`/`RoundingMode` hoặc `MathContext`.
8. **Nhầm `equals` và `compareTo`.** `1.0` và `1.00` có cùng giá trị số học nhưng `equals` trả false. Cẩn thận khi dùng làm khóa collection.
9. **Nhầm precision với scale.** `MathContext(5)` không có nghĩa giữ 5 chữ số thập phân; đó là 5 chữ số có nghĩa.
10. **Mong BigDecimal giữ nguyên định dạng đầu vào.** `"0001.2300"` có thể giữ scale thập phân nhưng không giữ số `0` dẫn đầu. Lưu riêng chuỗi gốc nếu cần khôi phục ký tự.
11. **Không xét dữ liệu không hợp lệ.** Chuỗi có khoảng trắng, dấu phẩy locale, NaN hoặc số mũ sai có thể gây `NumberFormatException`. Định nghĩa cú pháp hợp lệ từ đầu.
12. **Cho rằng BigInteger/BigDecimal là “vô hạn” về bộ nhớ hay tốc độ.** Chúng vẫn tiêu tốn tài nguyên theo độ dài số và độ phức tạp phép toán; kiểm soát kích thước input, nhất là khi nhận dữ liệu không tin cậy.

## 11. Bài tập tự kiểm tra

**Bài 1 — Giữ một số nguyên lớn từ chuỗi.**

Đầu vào `"9007199254740993"` cần cộng thêm `1` mà không mất chữ số. Viết:

```java
BigInteger result = new BigInteger("9007199254740993")
        .add(BigInteger.ONE);
System.out.println(result); // 9007199254740994
```

Không ép qua `double` ở bất kỳ bước nào.

**Bài 2 — Tính tổng số tiền thập phân.**

Hai chuỗi `"0.10"` và `"0.20"`, kết quả mong muốn là `0.30`:

```java
BigDecimal a = new BigDecimal("0.10");
BigDecimal b = new BigDecimal("0.20");

BigDecimal total = a.add(b);
System.out.println(total.toPlainString()); // 0.30
```

Scale của kết quả cộng thông thường phù hợp với scale lớn nhất của hai toán hạng trong ví dụ. Nếu cần format hai chữ số sau dấu phẩy cho những đầu vào khác, đặt chính sách scale/rounding rõ ràng.

**Bài 3 — Từ chối làm mất phần lẻ.**

Đầu vào `"42.1"` cần chuyển sang số nguyên nhưng phải báo lỗi nếu có phần lẻ:

```java
BigDecimal input = new BigDecimal("42.1");
// input.toBigIntegerExact() sẽ ném ArithmeticException.
```

Không chọn `toBigInteger()` vì nó âm thầm trả `42`.

**Bài 4 — So sánh hai số tiền.**

Tại sao `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` trả false nhưng `compareTo` trả 0? Vì `equals` xét cả scale, trong khi `compareTo` xét giá trị số học. Chọn một tiêu chí phù hợp nghiệp vụ và giữ nhất quán.

**Bài 5 — Phát hiện vòng chuyển đổi sai.**

```java
String source = "9007199254740993";
double d = Double.parseDouble(source);
BigDecimal x = BigDecimal.valueOf(d);
```

`x` không thể bằng chính xác giá trị trong `source`: `double` đã làm tròn thành `9007199254740992`. Cách sửa là `new BigDecimal(source)` hoặc `new BigInteger(source)` nếu dữ liệu là số nguyên.

## 12. Ghi nhớ

`BigInteger` dành cho **số nguyên chính xác vượt phạm vi kiểu nguyên cơ bản**; `BigDecimal` dành cho **giá trị thập phân chính xác và chính sách scale/rounding rõ ràng**. Khi nguồn là `String`, parse trực tiếp sang kiểu mong muốn. `float`/`double` có giới hạn bit, miền giá trị và có thể mất chữ số; đi qua chúng có thể khiến chuyển đổi về BigInteger/BigDecimal không thể bảo toàn dữ liệu ban đầu. Trước khi chuyển đổi có nguy cơ mất mát, hãy dùng các phép `Exact` hoặc quyết định làm tròn tường minh.

**Tài liệu chính thức:** [Java SE 17 — BigInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html), [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html), [Java SE 17 — RoundingMode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html), [Java SE 17 — Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-conversion-007">NumberFormat khi phân tích số theo locale</a>

<details>
<summary>Xem chi tiết</summary>

# NumberFormat khi phân tích số theo Locale trong Java

## 1. Vì sao không thể phân tích mọi số bằng một kiểu dấu cố định?

Cách viết số phụ thuộc vào **quy ước vùng/ngôn ngữ (locale)**. Cùng một giá trị một nghìn hai trăm ba mươi bốn phẩy năm có thể xuất hiện dưới các dạng khác nhau:

| Locale | Văn bản thường thấy | Ý nghĩa |
|---|---|---|
| `Locale.US` | `1,234.50` | Dấu phẩy phân nhóm nghìn, dấu chấm thập phân |
| `Locale.GERMANY` | `1.234,50` | Dấu chấm phân nhóm nghìn, dấu phẩy thập phân |

Nếu chương trình cố định dấu chấm là dấu thập phân và xóa mọi dấu phẩy trước khi gọi `Double.parseDouble()`, nó có thể hiểu sai dữ liệu nhập từ Đức. Ngược lại, thay toàn bộ dấu chấm bằng dấu phẩy cũng không giải quyết được mọi trường hợp: nhiều locale dùng khoảng trắng, khoảng trắng không ngắt, các ký hiệu chữ số và quy tắc phân nhóm khác nhau.

Java cung cấp `java.text.NumberFormat` để **định dạng** giá trị số thành văn bản và **phân tích (parse)** văn bản số theo locale. Bài này tập trung vào chiều phân tích với `NumberFormat.parse()`, đồng thời giải thích các giới hạn về độ chính xác, kiểm tra tính hợp lệ và lựa chọn kiểu kết quả.

Ví dụ mở đầu:

~~~java
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public class FirstParse {
    public static void main(String[] args) throws ParseException {
        NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
        NumberFormat de = NumberFormat.getNumberInstance(Locale.GERMANY);

        Number a = us.parse("1,234.50");
        Number b = de.parse("1.234,50");

        System.out.println(a.doubleValue()); // 1234.5
        System.out.println(b.doubleValue()); // 1234.5
    }
}
~~~

Hai chuỗi đầu vào khác nhau nhưng được phân tích thành giá trị số giống nhau khi sử dụng đúng locale.

## 2. Các lớp và phương thức cần biết

Các thành phần chính trong thư viện chuẩn:

- `java.util.Locale` mô tả quy ước địa phương hóa. `Locale.US` và `Locale.GERMANY` là hai hằng tiện dụng; `Locale.forLanguageTag("vi-VN")` tạo locale theo thẻ ngôn ngữ.
- `java.text.NumberFormat` là API định dạng/phân tích số tổng quát. Nó trừu tượng hóa ký hiệu thập phân, phân nhóm và một số quy tắc tùy locale.
- `java.text.DecimalFormat` là triển khai thông dụng của `NumberFormat` cho các mẫu số thập phân; cho phép chỉ định pattern và bật `setParseBigDecimal(true)`.
- `java.text.DecimalFormatSymbols` lưu các ký hiệu dùng trong mẫu như dấu thập phân và dấu phân nhóm.
- `java.text.ParsePosition` lưu vị trí bắt đầu, vị trí đã phân tích xong và vị trí lỗi; hữu ích để kiểm tra toàn bộ chuỗi.
- `java.text.ParseException` xuất hiện ở overload `parse(String)` nếu phương thức không thể phân tích một số từ vị trí bắt đầu.

Hai overload tiêu biểu:

~~~java
Number parse(String source) throws ParseException
Number parse(String source, ParsePosition position)
~~~

Khác biệt quan trọng: overload nhận `ParsePosition` trả về `null` nếu không thể phân tích bắt đầu từ vị trí chỉ định, thay vì ném `ParseException` cho trường hợp parse thất bại thông thường.

Các factory phương thức thường dùng:

~~~java
NumberFormat number = NumberFormat.getNumberInstance(Locale.US);
NumberFormat integer = NumberFormat.getIntegerInstance(Locale.US);
NumberFormat percent = NumberFormat.getPercentInstance(Locale.US);
NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
~~~

Chọn đúng factory cho **loại văn bản đầu vào**. Ví dụ dấu phần trăm và ký hiệu tiền không phải chỉ là dấu trang trí: kiểu formatter tương ứng có thể áp dụng quy tắc phân tích và tỷ lệ khác nhau. Với một số thập phân thông thường, hãy bắt đầu bằng `getNumberInstance(locale)`.

`NumberFormat.getInstance(locale)` cũng thường được dùng để lấy formatter số tổng quát; `getNumberInstance(locale)` nêu rõ ý định hơn trong bài toán này.

## 3. Quy trình parse một số theo locale

Một luồng dữ liệu thường có các bước:

1. Xác định **locale của chính dữ liệu nhập**, không tự suy ra chỉ từ vị trí máy chủ.
2. Chọn formatter tương ứng, ví dụ `getNumberInstance(Locale.GERMANY)` cho văn bản kiểu Đức.
3. Gọi `parse(text)` để thu được một đối tượng `Number`.
4. Kiểm tra xem toàn bộ input có được tiêu thụ hay không nếu đầu vào cần hợp lệ tuyệt đối.
5. Chọn kiểu số thích hợp (`long`, `double`, `BigDecimal`) dựa trên yêu cầu độ chính xác, khoảng giá trị và phép tính tiếp theo.

Ví dụ:

~~~java
String input = "1.234,50";
Locale locale = Locale.GERMANY;

NumberFormat parser = NumberFormat.getNumberInstance(locale);
Number parsed = parser.parse(input);

double value = parsed.doubleValue(); // 1234.5
~~~

Đừng dùng kết quả hiển thị mặc định của đối tượng `Number` để đoán locale của dữ liệu gốc. Dữ liệu đã parse trở thành giá trị số; quy tắc hiển thị lại là quyết định riêng của chương trình.

### Locale của người nhập và locale của môi trường

Nếu biểu mẫu yêu cầu nhập số theo chuẩn Đức, cấu hình máy chủ đang chạy ở US **không làm cho chuỗi `1.234,50` tự trở thành số US**. Nên lưu hoặc truyền locale đi kèm nguồn dữ liệu, hay quy định rõ định dạng đầu vào trong hợp đồng API.

Để kiểm thử ổn định:

~~~java
NumberFormat parser = NumberFormat.getNumberInstance(Locale.US);
~~~

Cách trên đáng tin cậy hơn việc gọi `NumberFormat.getNumberInstance()` không có tham số rồi kỳ vọng cùng kết quả trên mọi máy. Phiên bản không tham số sử dụng locale mặc định phù hợp với mục đích định dạng.

## 4. Kết quả của parse() là Number, không cố định Double

`NumberFormat.parse(...)` trả về kiểu trừu tượng `Number`. Triển khai có thể trả đối tượng số nguyên hoặc số thực tùy giá trị và cấu hình. Không nên mặc định rằng kiểu kết quả luôn là `Double`:

~~~java
NumberFormat parser = NumberFormat.getNumberInstance(Locale.US);

Number whole = parser.parse("1234");
Number fraction = parser.parse("1234.50");

// Không nên làm:
// Double d = (Double) whole;
~~~

Nếu cần một giá trị `double`, dùng phương thức chung:

~~~java
double x = fraction.doubleValue();
~~~

Nếu cần số nguyên:

~~~java
long n = whole.longValue();
~~~

Tuy nhiên, **`longValue()` không phải trình kiểm tra số nguyên hợp lệ**: nó có thể cắt phần thập phân khi giá trị vốn là số thực, và chuyển kiểu có thể làm mất thông tin khi giá trị vượt phạm vi hoặc không biểu diễn chính xác. Phải kiểm tra yêu cầu nghiệp vụ trước khi chuyển kiểu, đặc biệt với tiền, số lượng, định danh hoặc các giá trị đòi hỏi độ chính xác tuyệt đối.

Đối với đầu vào thập phân chính xác, xem phần `DecimalFormat` và `BigDecimal` bên dưới.

## 5. Điểm dễ bỏ sót: parse(String) có thể chỉ đọc tiền tố hợp lệ

`NumberFormat.parse(String)` **không bảo đảm đã sử dụng toàn bộ chuỗi**. Nếu parser tìm được một tiền tố có dạng số, phần còn lại có thể bị bỏ qua.

Trên formatter `Locale.US` thông dụng:

~~~java
NumberFormat us = NumberFormat.getNumberInstance(Locale.US);

System.out.println(us.parse("1,234.50kg"));
// 1234.5
~~~

Đây là kết quả hợp lý của **quá trình phân tích tiền tố**; nhưng nếu một ô nhập liệu bắt buộc chỉ chứa số, việc chấp nhận `kg` ở cuối là lỗi kiểm tra dữ liệu.

Tình huống nguy hiểm hơn:

~~~java
String input = "1,234.50andMore";
Number parsed = us.parse(input);
// Có thể đọc thành giá trị của tiền tố 1,234.50,
// trong khi phần "andMore" chưa được tiêu thụ.
~~~

Không nên chỉ dựa vào việc `parse(String)` không ném `ParseException` để kết luận văn bản hoàn toàn hợp lệ.

## 6. Dùng ParsePosition để kiểm tra toàn bộ chuỗi

`ParsePosition` cung cấp:

- `getIndex()`: chỉ số đầu tiên sau đoạn đã phân tích thành công.
- `getErrorIndex()`: vị trí lỗi được ghi nhận, hoặc thường là `-1` khi không có lỗi parse tại điểm bắt đầu.
- `setIndex(...)`: thiết lập vị trí bắt đầu parse nếu thực sự muốn đọc từ giữa một chuỗi.

Mẫu kiểm tra toàn bộ đầu vào:

~~~java
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class StrictConsumption {
    static Number parseEntire(String input, Locale locale) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input must not be empty");
        }

        NumberFormat parser = NumberFormat.getNumberInstance(locale);
        ParsePosition position = new ParsePosition(0);
        Number result = parser.parse(input, position);

        if (result == null || position.getIndex() != input.length()) {
            throw new IllegalArgumentException(
                    "Invalid number at index " + position.getIndex()
                            + ": " + input);
        }

        return result;
    }

    public static void main(String[] args) {
        System.out.println(
                parseEntire("1,234.50", Locale.US).doubleValue());
        // 1234.5

        try {
            parseEntire("1,234.50kg", Locale.US);
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected trailing text");
        }
        // Rejected trailing text
    }
}
~~~

Tại đây, `"1,234.50kg"` có độ dài 10 ký tự, trong khi parser chỉ tiến đến chỉ số 8 sau khi đọc phần số; điều kiện `position.getIndex() != input.length()` phát hiện phần dư.

**Giới hạn quan trọng:** tiêu thụ toàn bộ chuỗi **không tương đương với kiểm tra ngữ pháp số nghiêm ngặt**. Nhiều triển khai `NumberFormat` chấp nhận các dạng phân nhóm hoặc dấu ít chặt chẽ hơn yêu cầu nghiệp vụ. Chẳng hạn, một chuỗi nhóm chữ số sai chuẩn có thể vẫn được parser đọc thành một số. Nếu giao diện yêu cầu chính xác mẫu phân nhóm, số chữ số thập phân, chỉ cho phép số dương hoặc không cho phép ký tự đặc biệt, cần **kiểm tra bổ sung theo quy tắc nghiệp vụ** trước/sau parse.

Không nên gọi cách kiểm tra bằng `ParsePosition` là “strict parser tuyệt đối”; nó chỉ bảo đảm parser đã tiến qua toàn bộ input.

### Có nên trim() trước khi parse?

Điều này tùy hợp đồng đầu vào:

~~~java
String normalized = input.trim();
~~~

`trim()` có thể phù hợp nếu người dùng được phép nhập khoảng trắng ngoài cùng; nhưng với dữ liệu máy hoặc những định dạng cần bảo toàn từng ký tự, hành động đó có thể che giấu lỗi. `String.strip()` cũng có quy tắc Unicode riêng; không phải mọi khoảng trắng không ngắt dùng để phân nhóm đều được xử lý giống nhau.

Xác định trước: khoảng trắng đầu/cuối có hợp lệ không, ký hiệu phân nhóm nào được phép, số âm và phần thập phân có hợp lệ không. Sau đó thực hiện đúng chính sách, không tùy tiện xóa mọi dấu hoặc mọi khoảng trắng.

## 7. DecimalFormat: nhận BigDecimal khi cần độ chính xác thập phân

Với các phép tính tài chính hay lượng tiền, việc chuyển chuỗi thập phân qua `double` có thể gây sai số nhị phân. `DecimalFormat` cho phép yêu cầu kết quả parse là `BigDecimal` bằng `setParseBigDecimal(true)`.

Ví dụ:

~~~java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Locale;

public class BigDecimalParse {
    public static void main(String[] args) throws ParseException {
        DecimalFormat parser = new DecimalFormat(
                "#,##0.###",
                DecimalFormatSymbols.getInstance(Locale.US)
        );
        parser.setParseBigDecimal(true);

        BigDecimal amount = (BigDecimal) parser.parse("1,234.50");
        System.out.println(amount); // 1234.50
        System.out.println(amount.add(new BigDecimal("0.10")));
        // 1234.60
    }
}
~~~

Trong ví dụ này:

1. `DecimalFormatSymbols.getInstance(Locale.US)` cung cấp dấu thập phân và phân nhóm của US.
2. Pattern `#,##0.###` nói rõ hình thức số cơ bản và có tối đa ba chữ số thập phân khi **format** theo mẫu đó.
3. `setParseBigDecimal(true)` giúp giữ giá trị thập phân dưới dạng `BigDecimal` thay vì buộc qua `double`.
4. `new BigDecimal("0.10")` biểu diễn chính xác số thập phân 0.10, phù hợp hơn `new BigDecimal(0.10)` cho loại bài toán này.

**Không hiểu lầm pattern là validator chặt:** số dấu `#` sau dấu thập phân trong pattern chủ yếu điều khiển cách **định dạng**; parser có thể nhận số chữ số thập phân khác. Phải kiểm tra scale và các điều kiện đầu vào nếu yêu cầu chính xác tối đa 2 hoặc 3 chữ số.

Một hàm parse đầy đủ với `BigDecimal`:

~~~java
static BigDecimal parseDecimalEntire(String text, Locale locale) {
    if (text == null || text.isEmpty()) {
        throw new IllegalArgumentException("Empty decimal input");
    }

    DecimalFormat format = new DecimalFormat(
            "#,##0.################",
            DecimalFormatSymbols.getInstance(locale)
    );
    format.setParseBigDecimal(true);

    ParsePosition pos = new ParsePosition(0);
    Number parsed = format.parse(text, pos);

    if (!(parsed instanceof BigDecimal)
            || pos.getIndex() != text.length()) {
        throw new IllegalArgumentException("Invalid decimal: " + text);
    }
    return (BigDecimal) parsed;
}
~~~

Các yêu cầu tính tiền thường cần thêm `setScale(scale, roundingMode)` hoặc phép kiểm tra scale trước khi tính; đó là quy tắc nghiệp vụ độc lập với việc hiểu dấu chấm/dấu phẩy của locale.

Lưu ý: các giá trị đặc biệt như `NaN` hoặc vô cực có cách xử lý riêng tùy formatter; chúng không có biểu diễn `BigDecimal` thông thường. Với bài toán yêu cầu **số thập phân hữu hạn**, cần loại trừ giá trị đặc biệt và kiểm tra kết quả thực tế.

## 8. Định dạng và phân tích là hai chiều khác nhau

`format(number)`: giá trị số → văn bản theo locale.

`parse(text)`: văn bản → giá trị số theo locale.

Ví dụ:

~~~java
NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
us.setMinimumFractionDigits(2);
us.setMaximumFractionDigits(2);

String rendered = us.format(1234.5);
System.out.println(rendered); // 1,234.50

Number parsed = us.parse(rendered);
System.out.println(parsed.doubleValue()); // 1234.5
~~~

Các tùy chọn `setMinimumFractionDigits`/`setMaximumFractionDigits` làm thay đổi **cách render**. Chúng **không phải cam kết** rằng parse chỉ chấp nhận văn bản có đúng hai chữ số sau dấu thập phân.

Vì vậy, ý tưởng “format theo locale rồi parse lại phải khôi phục nguyên số” chỉ đúng trong những điều kiện cụ thể: formatter có thể làm tròn, rút bớt chữ số hoặc áp dụng kiểu biểu diễn khác. Ví dụ nếu formatter hiển thị tối đa hai chữ số thập phân, số `1.23456` có thể được in thành `1.23` rồi parse lại thành `1.23`, làm mất thông tin ban đầu.

Nếu số được lưu và trao đổi giữa các hệ thống, đừng chọn định dạng giao diện có dấu phân nhóm để làm biểu diễn máy mặc định. Hãy dùng định dạng máy ổn định đã được hợp đồng dữ liệu quy định (ví dụ kiểu số JSON hợp lệ hoặc chuỗi thập phân có quy tắc rõ ràng), và chỉ sử dụng `NumberFormat` khi cần giao tiếp với người dùng theo locale.

## 9. NumberFormat khác Integer.parseInt và Double.parseDouble thế nào?

| Khía cạnh | `NumberFormat.parse(text)` | `Integer.parseInt(text)` / `Double.parseDouble(text)` |
|---|---|---|
| Mục đích | Phân tích số theo quy ước locale | Chuyển chuỗi theo cú pháp số Java quy định |
| Dấu phân nhóm | Có thể hiểu theo locale | Không chấp nhận nhóm hàng nghìn theo locale |
| Dấu thập phân | Phụ thuộc locale | `Double.parseDouble` dùng cú pháp dấu chấm thập phân |
| Kiểu trả về | `Number` (đa hình) | `int` hoặc `double` |
| Lỗi khi đầu vào sai | `ParseException` hoặc kết quả `null` tùy overload, có thể parse tiền tố | `NumberFormatException` |
| Kiểm soát tiêu thụ toàn chuỗi | Cần `ParsePosition` nếu yêu cầu | Các parser nguyên thủy không chấp nhận văn bản thừa ngoài cú pháp hợp lệ |
| Độ chính xác tiền tệ | Có thể dùng `DecimalFormat` + `BigDecimal` | `Double.parseDouble` cho số nhị phân dấu chấm động |

Ví dụ:

~~~java
double machineValue = Double.parseDouble("1234.50"); // 1234.5

NumberFormat de = NumberFormat.getNumberInstance(Locale.GERMANY);
double userValue = de.parse("1.234,50").doubleValue(); // 1234.5
~~~

Dùng `parseInt`/`parseDouble` khi chuỗi đến từ nguồn **đã có cú pháp máy được định nghĩa rõ**, không phải số do người dùng ở các locale khác nhau nhập theo thói quen.

## 10. Các trường hợp đầu vào cần chủ động quyết định

### 10.1. Khoảng trắng và ký tự phân nhóm

Một số locale dùng khoảng trắng hoặc các biến thể Unicode của dấu cách làm dấu phân nhóm. `"1 234,50"`, `"1 234,50"` (dấu cách không ngắt) và `"1 234,50"` (dấu cách không ngắt hẹp) **không nhất thiết tương đương** đối với mọi parser.

Đừng tự xóa toàn bộ whitespace nếu bạn chưa quy định dạng nào hợp lệ. Việc đó có thể biến chuỗi chứa khoảng trắng sai vị trí thành một số tưởng như đúng. Nếu phải chấp nhận nhiều định dạng đầu vào, hãy xác định quy tắc chuyển đổi và kiểm tra chúng bằng tập dữ liệu thực.

### 10.2. Số âm, dấu cộng và ngoặc

Một số định dạng biểu diễn âm bằng dấu trừ, một số pattern tài chính dùng ngoặc. `NumberFormat.getNumberInstance(locale)` không hứa chấp nhận mọi kiểu ký hiệu âm. Với dữ liệu kiểu kế toán, phải dùng formatter/pattern tương thích với định dạng đó và thêm kiểm tra.

### 10.3. Phần trăm

`NumberFormat.getPercentInstance(locale)` có thể chuyển một văn bản phần trăm sang **giá trị tỷ lệ**. Ví dụ khái niệm `50%` thường tương ứng `0.5`, không phải `50`. Không dùng percent formatter như bộ đọc số nguyên chung; hãy xác định dữ liệu đầu vào thể hiện “50 phần trăm” hay “50 đơn vị” trước.

### 10.4. Tiền tệ

`NumberFormat.getCurrencyInstance(locale)` hỗ trợ biểu diễn tiền theo locale nhưng bản thân kết quả `Number` **không mang theo đơn vị tiền tệ**. Nếu ứng dụng có USD, EUR, VND cùng tồn tại, cần lưu mã tiền riêng, kiểm tra đơn vị được nhập và có thể phải dùng hệ thống parse tiền tệ chuyên biệt. Đừng đoán đơn vị chỉ từ độ lớn của giá trị.

### 10.5. Ký hiệu khoa học

Không mặc định mọi pattern của `NumberFormat` đều nhận mọi dạng `1.2e3` giống `Double.parseDouble()`. Với dữ liệu khoa học, chọn parser và pattern hỗ trợ đầu vào đó rồi kiểm chứng trên các phiên bản JDK cần hỗ trợ. Nếu giao thức quy định dạng máy có chữ `e`, một parser máy cố định có thể thích hợp hơn.

### 10.6. Dữ liệu âm thầm sai vì phân nhóm lỏng

Việc dùng `ParsePosition` chỉ đảm bảo parser không bỏ lại ký tự. Nó không đủ để xác nhận đúng vị trí dấu phân nhóm trong mọi trường hợp. Nếu chính sách chỉ nhận dạng US với nhóm ba chữ số, có thể kết hợp một bước kiểm tra hình thức cụ thể trước khi parse, chẳng hạn:

~~~java
boolean validUsForm = text.matches(
        "[+-]?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?"
);
~~~

Regex này **chỉ minh họa chính sách ASCII/US được định nghĩa hẹp**: cho phép số nguyên có hoặc không có nhóm ba chữ số, phần thập phân tùy chọn, không cho khoảng trắng, số khoa học hoặc số bắt đầu bằng dấu chấm. Nó không phải validator cho mọi locale. Sau bước kiểm tra hình thức, vẫn cần parse và kiểm tra giới hạn giá trị theo nghiệp vụ.

## 11. Ví dụ Java 17+ hoàn chỉnh và có kiểm tra đầu vào

Tạo tệp **`LocaleNumberParsingDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleNumberParsingDemo {
    static Number parseEntire(String input, Locale locale) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Empty input");
        }

        NumberFormat format = NumberFormat.getNumberInstance(locale);
        ParsePosition position = new ParsePosition(0);
        Number value = format.parse(input, position);

        if (value == null || position.getIndex() != input.length()) {
            throw new IllegalArgumentException("Invalid input: " + input);
        }
        return value;
    }

    static BigDecimal parseBigDecimalEntire(
            String input, Locale locale) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Empty input");
        }

        DecimalFormat format = new DecimalFormat(
                "#,##0.################",
                DecimalFormatSymbols.getInstance(locale)
        );
        format.setParseBigDecimal(true);

        ParsePosition position = new ParsePosition(0);
        Number value = format.parse(input, position);
        if (!(value instanceof BigDecimal)
                || position.getIndex() != input.length()) {
            throw new IllegalArgumentException("Invalid input: " + input);
        }
        return (BigDecimal) value;
    }

    public static void main(String[] args) {
        Number us = parseEntire("1,234.50", Locale.US);
        Number de = parseEntire("1.234,50", Locale.GERMANY);

        System.out.println("US: " + us.doubleValue());
        System.out.println("DE: " + de.doubleValue());

        try {
            parseEntire("1,234.50kg", Locale.US);
        } catch (IllegalArgumentException e) {
            System.out.println("Trailing text rejected");
        }

        BigDecimal amount = parseBigDecimalEntire(
                "1,234.50", Locale.US);
        BigDecimal fee = new BigDecimal("0.10");

        System.out.println("Decimal: " + amount);
        System.out.println("Sum: " + amount.add(fee));

        NumberFormat display = NumberFormat.getNumberInstance(Locale.US);
        display.setMinimumFractionDigits(2);
        display.setMaximumFractionDigits(2);
        System.out.println("Formatted: " + display.format(1234.5));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LocaleNumberParsingDemo.java
java LocaleNumberParsingDemo
~~~

Đầu ra mong đợi trên JDK chuẩn với các locale được chỉ định:

~~~text
US: 1234.5
DE: 1234.5
Trailing text rejected
Decimal: 1234.50
Sum: 1234.60
Formatted: 1,234.50
~~~

Giải thích:

1. `parseEntire(...)` nhận chuỗi theo đúng locale và dùng `ParsePosition` kiểm tra không còn đuôi chưa được đọc.
2. Hai chuỗi `1,234.50` và `1.234,50` cho cùng giá trị sau khi áp dụng lần lượt US và Germany.
3. Chuỗi chứa `kg` bị từ chối dù phần tiền tố giống một số hợp lệ.
4. `parseBigDecimalEntire(...)` cấu hình `DecimalFormat` trả `BigDecimal`, giữ biểu diễn thập phân `1234.50` thay vì làm tròn thành một biểu diễn `double`.
5. Phép cộng với `new BigDecimal("0.10")` cho `1234.60` chính xác ở mức thập phân.
6. `display.format(...)` chuyển số về văn bản dành cho giao diện US, có dấu phẩy phân nhóm và hai chữ số thập phân. Đây là bước hiển thị, không phải phép kiểm tra đầu vào.

Ví dụ chủ động tạo instance formatter trong mỗi lời gọi hàm. Điều này cũng tránh vấn đề chia sẻ formatter mutable giữa nhiều luồng.

## 12. Thread-safety và chi phí tạo formatter

Các triển khai `NumberFormat` như `DecimalFormat` có trạng thái thay đổi được (`setGroupingUsed`, `setMinimumFractionDigits`, `setParseBigDecimal` và nhiều cấu hình khác). **Không giả định một instance được chia sẻ có thể bị nhiều luồng cùng parse/format an toàn.** Tài liệu Java khuyến nghị tạo instance riêng theo luồng hoặc có đồng bộ phù hợp.

Ví dụ an toàn, đơn giản:

~~~java
static Number parseUs(String input) throws java.text.ParseException {
    NumberFormat parser = NumberFormat.getNumberInstance(Locale.US);
    return parser.parse(input);
}
~~~

Với ứng dụng hiệu năng cao, có thể cân nhắc chiến lược tái sử dụng theo luồng hoặc thư viện chuyên dụng tùy yêu cầu, nhưng phải kiểm soát cấu hình và tránh để thay đổi trạng thái rò rỉ giữa các request. Chỉ tối ưu sau khi đo nhu cầu thực tế.

Dữ liệu locale có thể phát triển theo JDK và nhà cung cấp dữ liệu địa phương hóa (ví dụ CLDR). Với các locale có dấu phân nhóm đặc biệt hoặc thay đổi quy ước, nên viết test trên phiên bản JDK thực tế được hỗ trợ và dữ liệu tương ứng. Không suy diễn tất cả locale đều dùng chính xác ký hiệu của US/Germany.

## 13. Những lỗi phổ biến

**Lỗi 1 — Dùng `Double.parseDouble("1.234,50")` cho dữ liệu Đức.** Cú pháp số máy của `Double.parseDouble` không phải cú pháp số theo locale Đức. **Cách làm:** dùng `NumberFormat.getNumberInstance(Locale.GERMANY)`.

**Lỗi 2 — Xóa mọi dấu chấm/dấu phẩy rồi parse.** Dễ chuyển sai `1.234,50` thành một giá trị khác. **Cách làm:** biết trước locale; dùng parser phù hợp và xác minh đầu vào.

**Lỗi 3 — Thấy `parse(String)` thành công thì cho rằng toàn chuỗi hợp lệ.** Parser có thể chấp nhận tiền tố số, bỏ lại chữ ở cuối. **Cách làm:** dùng `ParsePosition` và kiểm tra `getIndex() == input.length()`.

**Lỗi 4 — Tin rằng ParsePosition tự thực hiện kiểm tra định dạng nghiêm ngặt.** Nó chỉ báo parser đã tiêu thụ đến đâu, không bảo đảm mọi vị trí dấu phân nhóm hợp lệ theo chính sách. **Cách làm:** nếu cần, kiểm tra thêm mẫu dữ liệu cụ thể và ràng buộc nghiệp vụ.

**Lỗi 5 — Ép kết quả parse trực tiếp sang Double.** `NumberFormat.parse` trả `Number`; kiểu triển khai không cố định. **Cách làm:** dùng API `Number` hoặc cấu hình `DecimalFormat` trả `BigDecimal` khi phù hợp.

**Lỗi 6 — Dùng double để lưu tiền rồi nghĩ format hai số lẻ khắc phục sai số.** Định dạng chỉ ảnh hưởng cách in. **Cách làm:** dùng kiểu giá trị thập phân phù hợp như `BigDecimal` và quy tắc làm tròn rõ ràng.

**Lỗi 7 — Dùng `setMaximumFractionDigits(2)` để giới hạn đầu vào hai số lẻ.** Thuộc tính này chủ yếu quản lý format. **Cách làm:** kiểm tra số chữ số thập phân, scale và chính sách trước/sau parse.

**Lỗi 8 — Dùng một NumberFormat mutable toàn cục cho nhiều luồng.** Có thể gây dữ liệu sai khi đồng thời thay đổi hoặc sử dụng formatter. **Cách làm:** tạo instance riêng hoặc đồng bộ theo thiết kế.

**Lỗi 9 — Nhầm số định dạng theo locale với giao thức trao đổi dữ liệu.** Chuỗi hiển thị cho người dùng có thể không được parser của hệ thống khác hiểu đúng. **Cách làm:** định nghĩa biểu diễn máy riêng và rõ ràng.

## 14. Checklist tự kiểm tra

Trước khi dùng `NumberFormat` trong chương trình thực tế, hãy trả lời:

1. **Input đến từ locale nào?** Có được xác định từ cấu hình người dùng, trường nhập hay hợp đồng dữ liệu không?
2. **Giá trị cần là loại số nào?** Số nguyên, thực xấp xỉ hay `BigDecimal` chính xác?
3. **Có chấp nhận tiền tố hợp lệ và hậu tố ký tự không?** Nếu không, dùng `ParsePosition` để kiểm tra toàn bộ input.
4. **Cần đúng định dạng phân nhóm hay chỉ cần hiểu được số?** Với quy định chặt, cần bước kiểm tra ngữ pháp riêng.
5. **Có giới hạn min/max, số chữ số thập phân, số âm hoặc ký hiệu tiền tệ không?** Đừng giao hết cho parser.
6. **Có nhiều luồng cùng dùng formatter không?** Tránh chia sẻ instance mutable thiếu đồng bộ.
7. **Kết quả format có được dùng để lưu/trao đổi máy không?** Nếu có, dùng một hợp đồng biểu diễn dữ liệu độc lập với locale hiển thị.

## 15. Ghi nhớ

`NumberFormat` giúp Java đọc **văn bản số theo locale**, xử lý sự khác biệt giữa dấu thập phân và phân nhóm một cách có ngữ cảnh. Bắt đầu với `getNumberInstance(locale)` và `parse(...)` khi cần chuyển dữ liệu người dùng thành `Number`. Nếu yêu cầu **toàn bộ văn bản phải hợp lệ**, dùng `ParsePosition` để phát hiện phần đuôi chưa được phân tích và thêm kiểm tra quy tắc nghiệp vụ. Nếu cần giữ **độ chính xác thập phân**, dùng `DecimalFormat` với `setParseBigDecimal(true)` hoặc parser số thập phân phù hợp. Phân biệt rõ **parse để hiểu dữ liệu**, **validate để chấp nhận dữ liệu**, và **format để trình bày dữ liệu**: ba trách nhiệm liên quan nhưng không thay thế nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-conversion-008">Kiểm tra toàn chuỗi và xử lý dữ liệu không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra toàn chuỗi và xử lý dữ liệu không hợp lệ khi chuyển đổi String trong Java

## 1. Vì sao kiểm tra toàn chuỗi quan trọng?

Một giá trị nhận từ bàn phím, biểu mẫu, file, cấu hình hoặc API ban đầu thường là `String`. Trước khi chuyển thành `int`, `long`, `BigDecimal`, `boolean` hay `LocalDate`, chương trình cần xác định **toàn bộ chuỗi** có thuộc định dạng hợp lệ được yêu cầu hay không. Không nên chỉ tìm thấy **một đoạn con trông có vẻ hợp lệ** rồi coi cả dữ liệu đầu vào là đúng.

Ví dụ, chuỗi `"order=42x"` có chứa `42`, nhưng không phải một số nguyên thập phân thuần túy. Nếu trường nhập liệu yêu cầu số lượng, giá trị như `"42kg"`, `"12 3"`, `"7;DELETE"` hoặc `"19.95 USD"` phải được đánh giá theo **quy ước của cả trường**, không tự ý tách phần số rồi bỏ qua phần còn lại.

Kiểm tra đầy đủ gồm hai tầng:

1. **Định dạng và chính sách nhập liệu:** Có cho phép khoảng trắng đầu/cuối, dấu `+`/`-`, số 0 đứng đầu, dấu phẩy thập phân, số mũ, Unicode digit hoặc chuỗi rỗng hay không?
2. **Khả năng chuyển đổi thực sự:** Giá trị có nằm trong phạm vi kiểu đích, hợp lệ về lịch hoặc thỏa các ràng buộc nghiệp vụ không?

Regex có thể xác nhận cú pháp, nhưng **không thay thế hoàn toàn bước parse và kiểm tra phạm vi**. Ngược lại, một số API parse có quy ước rộng hơn chính sách của ứng dụng; vì vậy thành công ở bước parse cũng chưa luôn đồng nghĩa dữ liệu đáp ứng yêu cầu.

## 2. `matches()` kiểm tra toàn chuỗi, `find()` chỉ tìm một đoạn

Trong `java.util.regex`:

- `pattern.matcher(input).matches()` chỉ trả `true` nếu **toàn bộ vùng đầu vào của Matcher** phù hợp với regex.
- `pattern.matcher(input).find()` chỉ cần tìm được **một đoạn con** khớp regex, không nhất thiết bao phủ toàn chuỗi.
- `input.matches(regex)` có hành vi kiểm tra toàn bộ chuỗi tương đương khi áp dụng regex đó qua `Pattern`; với mẫu sử dụng nhiều lần nên biên dịch `Pattern` một lần.

Ví dụ:

```java
import java.util.regex.Pattern;

Pattern digits = Pattern.compile("[0-9]+");
System.out.println(digits.matcher("42").matches());      // true
System.out.println(digits.matcher("id=42x").matches());   // false
System.out.println(digits.matcher("id=42x").find());      // true
```

Đây là đoạn minh họa trong một phương thức hoặc môi trường JShell, không phải một chương trình độc lập. Regex `[0-9]+` ở đây chỉ chấp nhận chữ số ASCII `0`–`9`. Việc `find()` trả `true` với `"id=42x"` **không xác nhận đầu vào là một số**.

Với `Matcher.matches()`, không cần thêm `^...$` để ép kiểm tra toàn chuỗi. Ngoài ra, `^` và `$` còn có những quy tắc về ranh giới dòng và cờ regex; dùng `matches()` sẽ thể hiện trực tiếp ý định “toàn chuỗi”. Nếu đã thay đổi `Matcher.region(...)`, `matches()` kiểm tra toàn **region đang chọn**, không nhất thiết là toàn chuỗi gốc; các ví dụ ở đây luôn dùng `Matcher` mới trên toàn input.

## 3. Một quy trình chuyển đổi dữ liệu đáng tin cậy

Quy trình khuyến nghị cho đầu vào `String` là:

1. **Phân biệt thiếu dữ liệu:** Xác định `null` có nghĩa “không gửi”, `""` có nghĩa “trống”, hay cả hai đều không hợp lệ. Không gọi `strip()` trực tiếp khi `input == null`.
2. **Chuẩn hóa có kiểm soát:** Chỉ loại khoảng trắng ở hai đầu nếu hợp đồng dữ liệu cho phép. Java 11+ có `strip()` theo `Character.isWhitespace`; `trim()` có cơ chế cũ khác, và không phải mọi ký tự khoảng trắng Unicode đều bị `strip()` loại. Không âm thầm loại ký tự ở giữa nếu chưa được cho phép.
3. **Xác nhận toàn chuỗi đúng cú pháp:** Ví dụ số nguyên `[+-]?[0-9]+`, ngày ISO `[0-9]{4}-[0-9]{2}-[0-9]{2}`, hoặc tập giá trị `true`/`false`.
4. **Parse đúng kiểu:** Dùng `Integer.parseInt`, `Long.parseLong`, `new BigDecimal`, `LocalDate.parse`... và bắt đúng ngoại lệ có thể xảy ra. Regex không kiểm tra được tràn `int` hoặc ngày 29/02 của năm không nhuận.
5. **Kiểm tra nghiệp vụ:** Ví dụ tuổi nằm trong phạm vi hợp lệ, số tiền không âm, ngày bắt đầu không sau ngày kết thúc. Cú pháp đúng không đồng nghĩa dữ liệu phù hợp về nghiệp vụ.
6. **Trả kết quả có trạng thái rõ ràng:** Không biến mọi dữ liệu lỗi thành giá trị `0`, `false` hoặc ngày hiện tại. Với ví dụ đơn giản có thể dùng `Optional<T>`; ứng dụng cần thông báo riêng nên dùng result type hoặc mã lỗi phân biệt thiếu/định dạng/phạm vi.

**Thứ tự quan trọng:** Khi các bước chuẩn hóa hoặc parse có thể thay đổi dữ liệu, cần ghi rõ hợp đồng. Ví dụ với mã định danh `"00123"`, chuyển sang `int` sẽ mất hai số 0 đầu; nếu các số 0 là một phần của định danh thì nên giữ dạng `String`, không ép kiểu số chỉ vì nó chỉ chứa chữ số.

## 4. Ví dụ Java 17+ hoàn chỉnh: số, boolean và ngày

Lưu file `StrictConversionDemo.java`:

```java
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

public class StrictConversionDemo {
    // Chính sách: chữ số ASCII, có thể có dấu +/-.
    private static final Pattern INTEGER =
            Pattern.compile("[+-]?[0-9]+");

    // Chính sách: số thập phân dùng dấu chấm, không cho 00.5 hay số mũ.
    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");

    // Yêu cầu ngày ISO đúng 10 ký tự, rồi mới kiểm tra ngày có thật.
    private static final Pattern DATE =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    static Optional<Integer> integer(String input) {
        if (input == null) return Optional.empty();
        String value = input.strip();
        if (!INTEGER.matcher(value).matches()) return Optional.empty();

        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            // Ví dụ số vượt phạm vi int, dù cú pháp toàn chuỗi hợp lệ.
            return Optional.empty();
        }
    }

    static Optional<BigDecimal> decimal(String input) {
        if (input == null) return Optional.empty();
        String value = input.strip();
        if (!DECIMAL.matcher(value).matches()) return Optional.empty();

        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    static Optional<Boolean> booleanValue(String input) {
        if (input == null) return Optional.empty();
        String value = input.strip();
        if ("true".equalsIgnoreCase(value)) return Optional.of(true);
        if ("false".equalsIgnoreCase(value)) return Optional.of(false);
        return Optional.empty();
    }

    static Optional<LocalDate> date(String input) {
        if (input == null) return Optional.empty();
        String value = input.strip();
        if (!DATE.matcher(value).matches()) return Optional.empty();

        try {
            return Optional.of(
                    LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    public static void main(String[] args) {
        System.out.println("Integer 42: "
                + integer(" 42 ").map(Object::toString).orElse("INVALID"));
        System.out.println("Integer 12x: "
                + integer("12x").map(Object::toString).orElse("INVALID"));
        System.out.println("Integer overflow: "
                + integer("2147483648").map(Object::toString).orElse("INVALID"));
        System.out.println("Integer empty: "
                + integer("   ").map(Object::toString).orElse("INVALID"));

        System.out.println("Decimal 19.95: "
                + decimal("19.95").map(Object::toString).orElse("INVALID"));
        System.out.println("Decimal suffix: "
                + decimal("19.95 USD").map(Object::toString).orElse("INVALID"));
        System.out.println("Decimal leading zero: "
                + decimal("00.5").map(Object::toString).orElse("INVALID"));

        System.out.println("Boolean TRUE: "
                + booleanValue("TRUE").map(Object::toString).orElse("INVALID"));
        System.out.println("Boolean yes: "
                + booleanValue("yes").map(Object::toString).orElse("INVALID"));

        System.out.println("Date leap: "
                + date("2024-02-29").map(Object::toString).orElse("INVALID"));
        System.out.println("Date invalid: "
                + date("2023-02-29").map(Object::toString).orElse("INVALID"));

        Pattern digits = Pattern.compile("[0-9]+");
        System.out.println("Partial find: "
                + digits.matcher("id=42x").find());
        System.out.println("Whole matches: "
                + digits.matcher("id=42x").matches());
    }
}
```

Biên dịch và chạy:

```bash
javac StrictConversionDemo.java
java StrictConversionDemo
```

Đầu ra:

```text
Integer 42: 42
Integer 12x: INVALID
Integer overflow: INVALID
Integer empty: INVALID
Decimal 19.95: 19.95
Decimal suffix: INVALID
Decimal leading zero: INVALID
Boolean TRUE: true
Boolean yes: INVALID
Date leap: 2024-02-29
Date invalid: INVALID
Partial find: true
Whole matches: false
```

Các kết quả minh họa hai lớp kiểm tra. `"2147483648"` vượt `Integer.MAX_VALUE` (2.147.483.647) dù phù hợp regex số nguyên. `"2023-02-29"` có hình thức `yyyy-MM-dd` nhưng ngày đó không tồn tại; `LocalDate.parse` với `ISO_LOCAL_DATE` kiểm tra tính hợp lệ của lịch. `"00.5"` có thể được một số bộ parse nhận, nhưng chính sách `DECIMAL` của **bài này** chủ động từ chối số 0 thừa ở đầu.

Các hàm trả `Optional.empty()` khi không parse được để dễ minh họa; chúng **không phân biệt lý do lỗi**. Với giao diện người dùng hoặc API thực tế, nên trả một kết quả có loại lỗi như `REQUIRED`, `BAD_FORMAT`, `OUT_OF_RANGE`, `INVALID_DATE` để hiển thị thông báo phù hợp.

## 5. Một số API có hành vi dễ gây nhầm

### Số nguyên: parse kiểm tra toàn giá trị và phạm vi

`Integer.parseInt("123")` trả `123`. `Integer.parseInt("12abc")` ném `NumberFormatException`; `Integer.parseInt("2147483648")` cũng ném ngoại lệ vì tràn phạm vi. `Long.parseLong` có nguyên tắc tương tự nhưng phạm vi `long` khác.

Không phải lúc nào cần regex trước parse: nếu chỉ cần đúng **cú pháp mà API parse hỗ trợ** và chấp nhận báo lỗi bằng ngoại lệ, có thể trực tiếp gọi parse trong `try/catch`. Thêm bước kiểm tra toàn chuỗi hữu ích khi **chính sách đầu vào chặt hơn** hành vi mặc định của parser hoặc khi cần thông báo lỗi chi tiết. Ví dụ, parser số nguyên Java có thể nhận một số chữ số Unicode mà `[0-9]` ASCII chủ động từ chối.

### Boolean: `parseBoolean` không phân biệt sai định dạng với `false`

`Boolean.parseBoolean("true")` trả `true`; `Boolean.parseBoolean("FALSE")`, `Boolean.parseBoolean("yes")` và `Boolean.parseBoolean(null)` đều trả `false`. Đây là API thuận tiện khi muốn coi mọi thứ khác `true` là `false`, nhưng **không phù hợp nếu `"yes"` phải bị báo dữ liệu không hợp lệ**. Khi cần đúng hai giá trị `true`/`false`, hãy kiểm tra rõ từng giá trị như phương thức `booleanValue`.

### Số thực: `Double.parseDouble` có thể chấp nhận dạng đặc biệt

`Double.parseDouble` hỗ trợ ký pháp số mũ và một số giá trị đặc biệt như `NaN` hoặc `Infinity`. Vì vậy, **parse thành công không có nghĩa đây là số hữu hạn theo chính sách của ứng dụng**. Nếu dùng `double` và yêu cầu giá trị hữu hạn, kiểm tra thêm `Double.isFinite(value)`. Với số tiền cần độ chính xác thập phân, `BigDecimal` thường thích hợp hơn `double`; nhưng chính sách định dạng, phạm vi, dấu thập phân và số chữ số sau dấu chấm vẫn cần được xác định riêng.

### Ngày: đúng định dạng chưa đủ để có ngày hợp lệ

Regex `[0-9]{4}-[0-9]{2}-[0-9]{2}` chấp nhận hình thức `"2023-02-29"`, nhưng điều này không đảm bảo ngày tồn tại. Dùng `java.time.LocalDate` và formatter có chế độ kiểm tra nghiêm ngặt, chẳng hạn `DateTimeFormatter.ISO_LOCAL_DATE`. Với mẫu tùy chỉnh, cần hiểu các ký hiệu như `uuuu` so với `yyyy` và `ResolverStyle.STRICT`: một định dạng tùy chỉnh có thể có hành vi resolve khác với kỳ vọng nếu chỉ ghép mẫu mà không kiểm tra kiểu lịch. Ngày được parse đúng vẫn có thể vi phạm ràng buộc nghiệp vụ, ví dụ ngày sinh nằm trong tương lai.

## 6. Dữ liệu có dấu phân cách và locale

Một số dữ liệu cần quy tắc theo **locale**, ví dụ `1.234,56` tại nhiều vùng dùng dấu phẩy làm dấu thập phân, nhưng `1,234.56` tại một số vùng khác. `new BigDecimal(String)` nhận biểu diễn số chuẩn theo cú pháp của nó; nó không tự suy đoán locale hoặc tự loại đơn vị `"USD"`.

Nếu ứng dụng thật sự cho phép cách viết số theo locale, có thể dùng `NumberFormat`/`DecimalFormat` với `Locale` chỉ định và `ParsePosition` để xác nhận parser đã tiêu thụ **đến hết đầu vào**. Ví dụ về nguyên tắc:

```java
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
String input = "1.234,56";
ParsePosition position = new ParsePosition(0);
Number number = format.parse(input, position);

boolean fullyConsumed =
        number != null && position.getIndex() == input.length();
```

Đoạn mã trên là **minh họa trong một khối lệnh**, không phải chương trình chạy độc lập. `fullyConsumed` kiểm tra parser có bỏ sót hậu tố hay không; **nó chưa tự bảo đảm chính sách định dạng chặt chẽ**, vì `NumberFormat` có thể linh hoạt về nhóm chữ số, dấu và một số chi tiết khác. Tùy ứng dụng, phải cấu hình parser phù hợp hoặc kết hợp kiểm tra cú pháp được chỉ định và chuyển đổi giá trị. Không tự xóa tất cả dấu `.`, `,` khi chưa biết chắc dấu nào là dấu nhóm và dấu nào là dấu thập phân.

Nếu định dạng là mã định danh hoặc mã bưu chính, đừng chuyển sang số rồi serialize lại vì có thể làm mất số 0 đứng đầu hoặc thay cách biểu diễn. Lưu dạng `String` và kiểm tra toàn chuỗi theo hợp đồng là lựa chọn đúng hơn.

## 7. Xử lý lỗi: báo đúng lý do, giữ đúng dữ liệu

Ứng dụng thực tế có thể phân biệt:

| Trường hợp | Dữ liệu ví dụ | Kết quả xử lý phù hợp |
| --- | --- | --- |
| Không được gửi | `null` | Lỗi `REQUIRED` nếu trường bắt buộc; nếu tùy chọn thì trạng thái “không có giá trị”. |
| Chỉ có khoảng trắng | `"   "` | Lỗi rỗng sau chuẩn hóa nếu chính sách cho phép `strip()`. |
| Sai cú pháp | `"12x"` | Báo đúng định dạng được hỗ trợ, không cắt lấy `12`. |
| Tràn miền kiểu | `"2147483648"` cho `int` | Báo ngoài phạm vi, không ép thành `int` bằng phép cắt hoặc chuyển kiểu không kiểm soát. |
| Sai ràng buộc nghiệp vụ | `"-5"` khi số lượng phải dương | Parse có thể thành công nhưng validation nghiệp vụ phải từ chối. |
| Ngày không tồn tại | `"2023-02-29"` | Báo ngày không hợp lệ; không tự sửa thành ngày khác trừ khi hợp đồng cho phép. |
| Không thuộc tập boolean | `"yes"` khi chỉ nhận `true/false` | Báo sai lựa chọn, không coi `yes` là `false`. |
| Có phần dư | `"19.95 USD"` khi trường yêu cầu số tiền thuần túy | Từ chối hoặc chuyển sang parser đơn vị có quy tắc rõ ràng. |

**Không xử lý bằng giá trị mặc định âm thầm:** Nếu `Integer.parseInt` thất bại, việc trả `0` mà không có trạng thái lỗi có thể khiến ứng dụng nhầm dữ liệu hỏng với giá trị 0 hợp lệ. Tránh đặt `catch (Exception ex)` rộng quanh cả chuỗi xử lý; bắt ngoại lệ dự kiến như `NumberFormatException` hoặc `DateTimeParseException`, rồi chuyển sang lỗi đầu vào thích hợp ở ranh giới hệ thống.

**Không ghi đè dữ liệu chưa hợp lệ:** Khi hiển thị lỗi cho người dùng, nên giữ lại input ban đầu để họ hiểu và sửa, đồng thời có thể lưu bản đã chuẩn hóa riêng khi chuyển đổi thành công. Khi ghi log, tránh đưa toàn bộ dữ liệu nhạy cảm hoặc giá trị không đáng tin trực tiếp vào log mà không cân nhắc.

**Không dựa vào kiểm tra client:** Nếu đầu vào đi vào hệ thống backend, phải kiểm tra theo hợp đồng ở điểm xử lý tin cậy, kể cả khi giao diện đã có validation. Regex dùng để kiểm tra định dạng không thay thế tham số hóa SQL, kiểm tra quyền truy cập, giới hạn kích thước input hoặc xử lý lỗi nghiệp vụ.

## 8. Lưu ý với Unicode, khoảng trắng và dữ liệu dài

- **Chính sách chữ số:** `[0-9]` chỉ cho phép các chữ số ASCII. Nếu muốn nhận chữ số từ nhiều hệ chữ viết, phải xác định quy tắc Unicode và hành vi parser tiếp theo; đừng giả sử regex và API parse luôn chấp nhận đúng cùng tập ký tự.
- **Khoảng trắng:** `strip()` xử lý các ký tự theo `Character.isWhitespace` nhưng không loại mọi ký tự khoảng trắng, ví dụ có những khoảng trắng không ngắt dòng. Nếu đầu vào phải tuân theo giao thức với khoảng trắng ASCII cụ thể, hãy định nghĩa chính sách tương ứng.
- **Số rất dài:** Regex đúng về hình thức không kiểm tra được giới hạn `int`/`long`. Cần kiểm tra trước kích thước tối đa, bắt lỗi parse, và không dùng `BigInteger`/`BigDecimal` vô giới hạn đối với dữ liệu không đáng tin nếu hệ thống có giới hạn tài nguyên.
- **Thông báo lỗi:** Nên diễn đạt bằng yêu cầu cụ thể (“Chỉ nhập số nguyên từ 1 đến 100”), thay vì hiển thị stack trace hoặc thông điệp nội bộ của `NumberFormatException`.
- **Regex dùng nhiều lần:** Tái sử dụng `Pattern` đã biên dịch, như trong ví dụ, thay vì biên dịch lại ở mỗi bản ghi. Tránh regex có backtracking tốn kém khi xử lý dữ liệu lớn hoặc không đáng tin.
- **Kiểm thử ranh giới:** Thử `null`, chuỗi rỗng, khoảng trắng, số âm, dấu cộng, chữ số Unicode, dấu phân cách, số 0 đầu, số tràn, ngày nhuận, dữ liệu có hậu tố và kích thước tối đa.

## 9. Câu hỏi thực hành

1. Vì sao `Pattern.compile("[0-9]+").matcher("id=42x").find()` trả `true` nhưng không thể dùng để xác nhận cả input là một số nguyên?
2. Nếu `"2147483648"` khớp `[0-9]+`, tại sao `Integer.parseInt` vẫn ném `NumberFormatException`?
3. Vì sao dùng `Boolean.parseBoolean("yes")` có thể gây sai dữ liệu nếu hệ thống yêu cầu nhập rõ `true` hoặc `false`?
4. Với ngày `"2023-02-29"`, regex xác nhận được điều gì và `LocalDate.parse` kiểm tra thêm điều gì?
5. Nếu người dùng nhập `"00123"` làm mã thành viên, có nên đổi thành `int` trước khi lưu không? Vì sao?
6. Khi parse số theo locale, vì sao chỉ parse được một đoạn đầu của input chưa được coi là dữ liệu hợp lệ?

**Ghi nhớ:** Chuyển đổi `String` an toàn yêu cầu **kiểm tra toàn chuỗi theo chính sách**, **parse đúng kiểu và phạm vi**, **kiểm tra nghiệp vụ**, rồi **trả lỗi rõ ràng** nếu bất kỳ bước nào thất bại. Không được đánh đồng “tìm thấy một đoạn phù hợp” với “cả dữ liệu đầu vào hợp lệ”.

</details>

- [Quay lại đầu trang](#back-to-top)
