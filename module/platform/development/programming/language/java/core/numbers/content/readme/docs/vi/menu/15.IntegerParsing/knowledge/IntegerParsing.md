<a id="back-to-top"></a>

# Phân tích số nguyên

## Menu
- [parseInt, parseLong và các phương thức parse tương ứng](#integer-parsing-001)
- [valueOf(String) khác parse về kiểu kết quả](#integer-parsing-002)
- [Radix, dấu cộng hoặc trừ và phạm vi hợp lệ](#integer-parsing-003)
- [parseUnsignedInt và parseUnsignedLong](#integer-parsing-004)
- [decode và các tiền tố cơ số](#integer-parsing-005)
- [Vì sao parseInt không xử lý literal 0x hoặc dấu gạch dưới như trình biên dịch](#integer-parsing-006)
- [NumberFormatException do ký tự sai hoặc vượt miền giá trị](#integer-parsing-007)
- [Phân tích đoạn CharSequence mà không tạo String con khi API hỗ trợ](#integer-parsing-008)
- [Integer.getInteger và Long.getLong đọc system property, không phải parse thông thường](#integer-parsing-009)
- [Kiểm tra toàn bộ đầu vào và chính sách xử lý khoảng trắng](#integer-parsing-010)
- [Chọn BigInteger cho chuỗi số vượt giới hạn long](#integer-parsing-011)

## <a id="integer-parsing-001">parseInt, parseLong và các phương thức parse tương ứng</a>

<details>
<summary>Xem chi tiết</summary>

# parseInt, parseLong và các phương thức parse số nguyên tương ứng trong Java

Khi dữ liệu từ form, tệp cấu hình hoặc CSV chứa `"125"`, giá trị đang là `String`. Muốn cộng, so sánh hay lưu thành số, chương trình cần **parse** chuỗi thành primitive phù hợp, đồng thời kiểm tra cú pháp và phạm vi giá trị. Bài nhập môn này tập trung vào các parser **có dấu** của `Byte`, `Short`, `Integer` và `Long`; những biến thể về wrapper, unsigned, prefix và `BigInteger` được triển khai sâu ở các bài kế tiếp.

## 1. Bốn parser signed chính và kiểu trả về

| API | Kiểu primitive | Phạm vi |
| --- | --- | --- |
| `Byte.parseByte("127")` | `byte` | -128 đến 127 |
| `Short.parseShort("32767")` | `short` | -32768 đến 32767 |
| `Integer.parseInt("42")` | `int` | -2147483648 đến 2147483647 |
| `Long.parseLong("3000000000")` | `long` | -9223372036854775808 đến 9223372036854775807 |

Ví dụ trong một phương thức Java:

```java
byte flag = Byte.parseByte("100");
short temperature = Short.parseShort("-120");
int count = Integer.parseInt("125");
long total = Long.parseLong("3000000000");
```

Bốn lời gọi trên trả về **primitive**. Phương thức `Integer.valueOf("42")` trả về wrapper `Integer`, không phải `int`; bài kế tiếp giải thích boxing/unboxing. Đừng chọn parser nhỏ chỉ vì biến nhận rộng hơn:

```java
long valid = Long.parseLong("3000000000");
// long invalid = Integer.parseInt("3000000000");
// Integer.parseInt vẫn ném NumberFormatException trước khi gán cho long.
```

**Chọn kiểu từ miền nghiệp vụ trước khi parse.** Nếu trường có thể lớn hơn `int` nhưng vừa `long`, dùng `Long.parseLong`. Nếu vượt `long`, chọn `BigInteger` khi cần giá trị số học chính xác. Nếu là mã định danh cần giữ số 0 đầu, hãy giữ `String`.

## 2. Cú pháp chuỗi và ký tự không hợp lệ

Các overload chỉ nhận một `String` mặc định parse **decimal**, có thể có một dấu cộng/trừ ASCII ở đầu rồi đến ít nhất một chữ số hợp lệ. Ví dụ:

```java
int a = Integer.parseInt("+42"); // 42
int b = Integer.parseInt("-42"); // -42
int c = Integer.parseInt("-0");  // 0
int d = Integer.parseInt("077"); // 77, không tự chuyển sang hệ 8
```

Parser kiểm tra toàn bộ chuỗi và miền giá trị. Bảng sau cho các input không hợp lệ với `Integer.parseInt(String)`:

| Input | Lý do từ chối |
| --- | --- |
| `""`, `"+"`, `"-"` | Không có chữ số |
| `"12x"`, `"4 2"` | Ký tự không hợp lệ giữa chuỗi |
| `"1.5"`, `"1e3"` | Không phải chuỗi số nguyên decimal |
| `" 42 "` | Không tự bỏ khoảng trắng |
| `"1_000"`, `"42L"` | Ký hiệu literal thuộc mã Java, không thuộc parser chuỗi |
| `"0x2A"` | Không tự nhận prefix hex |
| `"2147483648"` | Vượt `Integer.MAX_VALUE` |

Java có thể chấp nhận một số chữ số Unicode qua `Character.digit`. Nếu giao thức yêu cầu ASCII `0..9`, kiểm tra ASCII riêng. Việc gọi `strip()` trước parser chỉ phù hợp khi chính sách dữ liệu cho phép bỏ khoảng trắng ngoài. Các bài sau phân tích đầy đủ Unicode, `trim`/`strip`, lỗi `NumberFormatException` và literal so với String.

## 3. Cơ số là tham số, không phải prefix

Các parser có overload `parse...(String, int radix)` hỗ trợ radix từ 2 đến 36:

```java
int binary = Integer.parseInt("101101", 2); // 45
int octal = Integer.parseInt("77", 8);      // 63
int hex = Integer.parseInt("FF", 16);      // 255
long limit = Long.parseLong("7FFFFFFFFFFFFFFF",16); // Long.MAX_VALUE
```

`Integer.parseInt("0xFF", 16)` ném `NumberFormatException`: `x` không phải chữ số hex hợp lệ trong API này. Nếu định dạng input quy định prefix hex hoặc số 0 đầu mang nghĩa octal, hãy đọc bài `decode` để chọn parser thích hợp. Radix không tăng kích thước số nguyên mà kiểu đích có thể chứa.

## 4. Lỗi parse khác tràn phép toán

`NumberFormatException` phát sinh với chuỗi sai, radix sai hoặc số vượt miền. Parser từ chối thay vì âm thầm tràn vòng như một phép cộng kiểu `int`:

```java
int max = Integer.parseInt("2147483647");  // Thành công.
// Integer.parseInt("2147483648");         // NumberFormatException.
int wrapped = Integer.MAX_VALUE + 1;       // Integer.MIN_VALUE.
```

Khi nhận dữ liệu từ người dùng, không nên bắt mọi exception rồi trả 0, vì `"0"` là giá trị hợp lệ. Có thể bắt riêng `NumberFormatException` và trả trạng thái lỗi hoặc thông báo validation. Một số nghiệp vụ như số lượng dương còn cần kiểm tra `n > 0` sau khi parse thành công.

## 5. Chương trình Java 17 chạy độc lập

Lưu thành `SignedParsingBasics.java`:

```java
public class SignedParsingBasics {
    static void invalid(String text) {
        try {
            Integer.parseInt(text);
            System.out.println(text + "=UNEXPECTED");
        } catch (NumberFormatException ex) {
            System.out.println(text + "=INVALID");
        }
    }

    public static void main(String[] args) {
        System.out.println("byte=" + Byte.parseByte("127"));
        System.out.println("short=" + Short.parseShort("-32768"));
        System.out.println("int=" + Integer.parseInt("+42"));
        System.out.println("long=" + Long.parseLong("3000000000"));
        System.out.println("binary=" + Integer.parseInt("101101", 2));
        System.out.println("hex=" + Integer.parseInt("FF", 16));
        System.out.println("decimalZero=" + Integer.parseInt("077"));
        invalid("2147483648");
        invalid("1_000");
        invalid(" 42 ");
    }
}
```

```bash
javac --release 17 -encoding UTF-8 SignedParsingBasics.java
java SignedParsingBasics
```

Kết quả:

```text
byte=127
short=-32768
int=42
long=3000000000
binary=45
hex=255
decimalZero=77
2147483648=INVALID
1_000=INVALID
 42 =INVALID
```

Ba dòng `INVALID` do chương trình tự bắt đúng loại exception; chúng không phụ thuộc thông điệp ngoại lệ của JDK.

## 6. Tự kiểm tra

1. Vì sao `Integer.parseInt("3000000000")` lỗi, còn `Long.parseLong("3000000000")` chạy được?
2. `Byte.parseByte("128")` có tự tràn vòng thành -128 không?
3. `Integer.parseInt("077")` cho 77 hay 63? Vì sao?
4. Phân biệt `Integer.parseInt("FF",16)` và `Integer.parseInt("0xFF",16)`.
5. Có nên chuyển mã định danh `"00042"` thành số nguyên để lưu không?

**Ghi nhớ:** chọn primitive theo miền cần hỗ trợ; xác định radix, dấu, chữ số, giới hạn và cách báo lỗi. Các bài kế tiếp đào sâu từng khía cạnh thay vì coi một phương thức parse là đủ cho mọi định dạng.

**Tài liệu chuẩn:** [Integer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html), [Long](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html), [Byte](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Byte.html), [Short](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Short.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-002">valueOf(String) khác parse về kiểu kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# valueOf(String) khác parse về kiểu kết quả trong Java

## 1. Mục tiêu của bài học

Khi phân tích một chuỗi chữ số thành số nguyên Java, hai cách viết dưới đây trông như nhau:

```java
int number = Integer.parseInt("42");
Integer boxed = Integer.valueOf("42");
```

Cả hai đều đọc ký tự `"42"` thành giá trị số bốn mươi hai, nhưng **kiểu trả về ở thời điểm biên dịch khác nhau**:

- `Integer.parseInt(String)` trả **primitive `int`**.
- `Integer.valueOf(String)` trả **đối tượng wrapper `Integer`**.

Đây là trọng tâm của bài **Java Core — Numbers / Phân tích số nguyên**. Với `Long`, `Short` và `Byte`, các cặp phương thức tương ứng cũng có khác biệt kiểu trả về như vậy. Chọn phương thức không chỉ là sở thích đặt tên: nó ảnh hưởng đến kiểu biến, autoboxing/unboxing, lời gọi phương thức quá tải, API generic/collection, xử lý `null` và cách đọc code.

Sau bài này, bạn có thể:

1. Đọc chữ ký phương thức để phân biệt `parseInt`/`parseLong`/`parseShort`/`parseByte` và `valueOf(String)`.
2. Hiểu vì sao `println` có thể in giống nhau dù kiểu kết quả khác.
3. Chọn cách parse phù hợp cho tính toán primitive hoặc API cần wrapper.
4. Nhận diện khi compiler tự boxing/unboxing và nguy cơ `NullPointerException`.
5. Biết rằng `valueOf(String)` **không nới lỏng** cú pháp chuỗi, phạm vi số, radix hay xử lý ngoại lệ so với phương thức parse tương ứng.
6. Dùng đúng overload `valueOf(primitive)`, `valueOf(String, radix)` và phân biệt `decode`, `parseUnsignedInt`.
7. Viết mã Java 17+ chạy được và dự đoán stdout đúng.

**Nguyên tắc nhanh:** *parse* thường đọc chuỗi thành **giá trị primitive**; *valueOf(String)* đọc cùng nội dung thành **wrapper của giá trị đó**. Đừng kết luận phương thức sau tạo ra một kiểu số mới hoặc tự đổi định dạng dữ liệu.

## 2. Bảng so sánh phương thức trên bốn họ số nguyên

| Nhóm | Parse chuỗi | Kiểu kết quả | valueOf chuỗi | Kiểu kết quả |
| --- | --- | --- | --- | --- |
| `byte` / `Byte` | `Byte.parseByte("127")` | `byte` | `Byte.valueOf("127")` | `Byte` |
| `short` / `Short` | `Short.parseShort("32767")` | `short` | `Short.valueOf("32767")` | `Short` |
| `int` / `Integer` | `Integer.parseInt("42")` | `int` | `Integer.valueOf("42")` | `Integer` |
| `long` / `Long` | `Long.parseLong("3000000000")` | `long` | `Long.valueOf("3000000000")` | `Long` |

Dạng tham số `String` ở bảng trên không bao gồm trường hợp `null`, và giả định chuỗi hợp lệ trong miền kiểu đích. Cả hai cách parse đều có thể ném `NumberFormatException` khi dữ liệu không hợp lệ.

### 2.1. Chữ ký phương thức quyết định kiểu kết quả

Các chữ ký tiêu biểu:

```java
// Tên và kiểu trả về minh họa; đây không phải các khai báo tự viết.
static int Integer.parseInt(String s)
static Integer Integer.valueOf(String s)

static long Long.parseLong(String s)
static Long Long.valueOf(String s)

static short Short.parseShort(String s)
static Short Short.valueOf(String s)

static byte Byte.parseByte(String s)
static Byte Byte.valueOf(String s)
```

Đoạn trên là **cách ghi chữ ký để học**, không phải source code Java hợp lệ đặt trong class. Trình biên dịch biết kiểu trả về từ API tương ứng, không dựa trên chuỗi đầu vào thực tế là bao nhiêu.

Ví dụ `"42"` hoặc `"-42"` đều không làm đổi kiểu trả về; `Integer.parseInt` vẫn trả `int`, còn `Integer.valueOf` vẫn trả `Integer`.

### 2.2. Phân biệt kiểu với văn bản in ra

```java
int primitive = Integer.parseInt("42");
Integer wrapper = Integer.valueOf("42");

System.out.println(primitive); // 42
System.out.println(wrapper);   // 42
```

Hai dòng cùng in `42` vì `println` có overload và cơ chế chuyển sang chuỗi phù hợp. Không thể suy từ stdout rằng `primitive` và `wrapper` có cùng **kiểu compile-time**.

`primitive` lưu một giá trị `int`; `wrapper` là một tham chiếu đến một đối tượng `Integer` mang giá trị `42`. Wrapper là immutable và có thể tham gia các API object-oriented như `equals`, `compareTo` hoặc generic collections.

## 3. Giá trị phân tích gần như giống nhau, kiểu trả về khác nhau

Về ý nghĩa nội dung, `Integer.valueOf(String)` đọc chuỗi như `Integer.parseInt(String)` ở radix 10, sau đó trả về một `Integer` chứa giá trị đã đọc. Đọc tài liệu API có thể hình dung:

```java
Integer result = Integer.valueOf(Integer.parseInt("42"));
```

Về giá trị số, cách viết này tương đương `Integer.valueOf("42")` đối với chuỗi hợp lệ. **Không** hiểu đây là cam kết số lượng đối tượng được cấp phát hoặc identity của đối tượng trong mọi lần gọi: Java/JVM có thể cache wrapper thường dùng.

Với chuỗi không hợp lệ, cả `Integer.parseInt(s)` và `Integer.valueOf(s)` đều **không thể tạo kết quả `int`/`Integer` hợp lệ** và đều ném `NumberFormatException`. `valueOf(String)` không phải một phương thức “parse an toàn trả null” hoặc “bỏ qua lỗi”.

### 3.1. Wrapper không tự có giá trị null khi parse lỗi

```java
// Integer.parseInt("abc");  // NumberFormatException
// Integer.valueOf("abc");   // NumberFormatException
```

Hai lời gọi trên được **comment lại cố ý** để ví dụ minh họa không làm chương trình dừng. Trên một API yêu cầu trả `null` khi dữ liệu sai, bạn phải chủ động thiết kế wrapper method và chính sách lỗi, chẳng hạn trả `OptionalInt`, `Optional<Integer>` hoặc kết quả có trạng thái lỗi.

Không sử dụng `null` để thay thế bất kỳ giá trị không hợp lệ nào một cách tùy tiện, vì `null` có thể làm chương trình ném `NullPointerException` khi được unbox về primitive.

## 4. Boxing và unboxing có thể che giấu sự khác biệt

Java hỗ trợ **autoboxing** từ primitive sang wrapper và **unboxing** từ wrapper về primitive trong các ngữ cảnh được phép.

```java
int a = Integer.parseInt("42");     // int -> int
Integer b = Integer.valueOf("42"); // Integer -> Integer

Integer c = Integer.parseInt("42"); // int -> Integer: boxing
int d = Integer.valueOf("42");     // Integer -> int: unboxing
```

Cả bốn dòng đều có thể biên dịch. Nhưng sự tiện lợi này làm bạn dễ bỏ qua kiểu trả về thực tế:

| Phía phải | Phía trái | Chuyển đổi |
| --- | --- | --- |
| `parseInt(...)` = `int` | `int` | Không boxing |
| `parseInt(...)` = `int` | `Integer` | Boxing |
| `valueOf(...)` = `Integer` | `Integer` | Không unboxing |
| `valueOf(...)` = `Integer` | `int` | Unboxing |

Đừng giải thích `int d = Integer.valueOf("42")` là bằng chứng `valueOf` trả `int`. Chính compiler đã áp dụng unboxing trong phép gán.

### 4.1. Tại sao nên chọn đúng API ngay từ đầu?

Nếu mục tiêu là một vòng lặp tính tổng theo kiểu `int`, `parseInt` truyền tải ý định trực tiếp. Nếu muốn truyền đối tượng vào API `List<Integer>`, `Map<String,Integer>` hoặc một method nhận `Integer`, `valueOf` làm rõ kiểu wrapper được yêu cầu.

Không cần tối ưu mù quáng: JIT có thể tối ưu những phép boxing trong một số ngữ cảnh, và `Integer.valueOf` có thể tái sử dụng object. **Điều quan trọng đầu tiên là kiểu dữ liệu, ngữ nghĩa null, miền giá trị và tính dễ đọc**, thay vì giả định mọi lời gọi `valueOf` chắc chắn tạo allocation mới.

### 4.2. Null là vấn đề của wrapper, không phải một kết quả parse hợp lệ

```java
Integer boxed = null;
// int unboxed = boxed; // NullPointerException lúc chạy
```

Không có giá trị `null` kiểu `int`. Khi một API nhận `Integer` có thể trả `null` để biểu thị “chưa có dữ liệu”, bạn phải kiểm tra trước unboxing:

```java
Integer optional = null;
int fallback = optional == null ? 0 : optional.intValue();
```

Việc dùng `0` làm fallback chỉ phù hợp nếu `0` mang nghĩa mà nghiệp vụ cho phép. Nếu `0` là một giá trị có ý nghĩa riêng, cần giữ trạng thái thiếu dữ liệu ở mô hình dữ liệu thay vì tự động dùng zero.

## 5. Khác biệt có thể quan sát ở quá tải phương thức

Khi code có overload `accept(int)` và `accept(Integer)`, kiểu kết quả của `parseInt`/`valueOf` có thể quyết định phương thức nào được chọn ngay lúc biên dịch.

Lưu thành tệp **`ParseReturnTypes.java`**:

```java
public class ParseReturnTypes {
    static String describe(int value) {
        return "primitive int: " + value;
    }

    static String describe(Integer value) {
        return "wrapper Integer: " + value;
    }

    public static void main(String[] args) {
        int raw = Integer.parseInt("42");
        Integer boxed = Integer.valueOf("42");

        System.out.println(describe(raw));
        System.out.println(describe(boxed));
        System.out.println(raw == boxed);
        System.out.println(boxed.getClass().getSimpleName());
    }
}
```

Chạy:

```bash
javac --release 17 ParseReturnTypes.java
java ParseReturnTypes
```

**Stdout chính xác:**

```text
primitive int: 42
wrapper Integer: 42
true
Integer
```

Diễn giải:

1. `describe(raw)` chọn overload `describe(int)`.
2. `describe(boxed)` chọn overload `describe(Integer)`.
3. Với biểu thức `raw == boxed`, `boxed` được unbox thành `int` để so sánh giá trị primitive; kết quả true.
4. `boxed.getClass().getSimpleName()` trả tên lớp wrapper `Integer`.

**Lưu ý:** nếu `boxed == null`, biểu thức so sánh `raw == boxed` có thể ném `NullPointerException` lúc unboxing. Ví dụ hiện tại không có trường hợp đó vì `Integer.valueOf("42")` trả đối tượng hợp lệ.

## 6. Kiểu trả về ảnh hưởng generic collections

Generic type arguments trong Java không được là primitive:

```java
// List<int> invalid;        // không hợp lệ
java.util.List<Integer> values = new java.util.ArrayList<>();

values.add(Integer.parseInt("12")); // int được boxing thành Integer
values.add(Integer.valueOf("34"));  // đã là Integer
```

Hai lời gọi `values.add` đều có thể chạy. Nhưng:

- Với `parseInt`, một bước boxing có thể diễn ra.
- Với `valueOf`, API nhận thẳng một `Integer`.
- Khi `values.get(0)` được gán sang `int`, có thể xảy ra unboxing.

`valueOf(String)` không “tốt hơn” trong mọi trường hợp. Chọn một cách nhất quán theo vai trò kiểu dữ liệu. Nếu đang làm API có thể thiếu dữ liệu, kiểu `Integer` giúp diễn tả nullability nhưng cũng buộc bạn phải xác định rõ cách xử lý `null`.

### 6.1. `Integer` là object, đừng so identity bằng `==`

```java
Integer x = Integer.valueOf("42");
Integer y = Integer.valueOf("42");

boolean equalValue = x.equals(y); // true
```

`x.equals(y)` so **giá trị `Integer`**. `x == y` nếu cả hai có kiểu `Integer` là phép so **tham chiếu object** và có thể chịu ảnh hưởng bởi cơ chế cache của `valueOf`. Java bảo đảm cache một miền giá trị nhỏ chuẩn đối với một số wrapper; nhưng code ứng dụng không nên phụ thuộc vào identity object để kết luận hai số có cùng giá trị.

Điều đó không mâu thuẫn với `raw == boxed` ở ví dụ trước: khi một toán hạng là primitive `int`, Java áp dụng unboxing/numeric equality. Kiểu của **cả hai** toán hạng quyết định ý nghĩa của toán tử.

## 7. Cùng quy tắc cú pháp chuỗi, cùng miền giá trị

### 7.1. Chuỗi hợp lệ trong hệ thập phân

`Integer.parseInt(String)` và `Integer.valueOf(String)` chấp nhận chuỗi biểu diễn số nguyên **signed decimal** hợp lệ. Ví dụ:

| Input | Kết quả số | Ý nghĩa |
| --- | ---: | --- |
| `"42"` | 42 | Thập phân thông thường |
| `"+42"` | 42 | Dấu cộng ASCII đầu chuỗi được chấp nhận |
| `"-42"` | −42 | Dấu trừ ASCII |
| `"0"` | 0 | Số không |
| `"-0"` | 0 | Số nguyên không có signed zero riêng |
| `"00042"` | 42 | Số 0 đầu chuỗi không tự làm chuyển sang octal |
| `"2147483647"` | 2147483647 | `Integer.MAX_VALUE` |
| `"-2147483648"` | −2147483648 | `Integer.MIN_VALUE` |

Java API cho phép ký tự chữ số được `Character.digit(char,radix)` nhận diện ở radix tương ứng, không chỉ ASCII trong mọi ngữ cảnh. Nhưng API không tự làm bước quốc tế hóa/chuẩn hóa mọi cách viết số theo locale; nếu đầu vào từ người dùng, cần xác định charset, chữ số chấp nhận và ràng buộc bảo mật phù hợp.

### 7.2. Chuỗi không hợp lệ

Những input sau không phải cách viết chuẩn được `Integer.parseInt(s)` hoặc `Integer.valueOf(s)` chấp nhận:

| Input | Vì sao sai |
| --- | --- |
| `""` | Chuỗi trống |
| `" "`, `" 42 "` | Có khoảng trắng; phương thức không tự trim |
| `"42.0"` | Có dấu chấm, không còn là chuỗi số nguyên |
| `"1e3"` | Ký hiệu mũ thuộc số thực, không phải signed integer string |
| `"1_000"` | Dấu gạch dưới chỉ được Java source grammar chấp nhận ở vị trí phù hợp trong literal, không phải ký tự được parser chuỗi này chấp nhận |
| `"0xFF"` | Prefix hex không được tự nhận dạng ở overload radix mặc định |
| `"+"`, `"-"` | Chỉ có dấu, thiếu chữ số |
| `"2147483648"` | Vượt `Integer.MAX_VALUE` |
| `"-2147483649"` | Nhỏ hơn `Integer.MIN_VALUE` |

`Integer.parseInt(null)` và `Integer.valueOf((String)null)` cũng không tạo giá trị hợp lệ; với overload `String`, JDK báo `NumberFormatException`. Với `null` và nhiều overload khác nhau, cần chú ý **chữ ký được chọn** và ngoại lệ được tài liệu hóa cho chữ ký đó.

### 7.3. Chuỗi literal và source literal khác nhau

```java
int a = 1_000;                 // hợp lệ: source code integer literal
// int b = Integer.parseInt("1_000"); // runtime NumberFormatException
```

`0xFF` viết trong mã nguồn Java là một **integer literal hệ 16**. Nhưng `"0xFF"` là **chuỗi chứa ký tự** `0`, `x`, `F`, `F` và không tự được parse theo source literal grammar.

**Đừng giải quyết bằng cách đổi `parseInt` sang `valueOf`:** hai cách có cùng giới hạn cơ bản đối với chuỗi đầu vào. Hãy chọn overload radix hoặc phương thức `decode` có hợp đồng phù hợp.

## 8. Overload có radix: `parseInt(String,int)` và `valueOf(String,int)`

Cả hai họ API cho phép chỉ định hệ cơ số `radix`:

```java
int primitiveHex = Integer.parseInt("FF", 16);       // 255
Integer boxedHex = Integer.valueOf("FF", 16);       // Integer(255)

int primitiveBinary = Integer.parseInt("101010", 2); // 42
Integer boxedBinary = Integer.valueOf("101010", 2); // Integer(42)
```

Sự khác biệt **vẫn chỉ là kiểu trả về**, còn quy tắc chuyển chuỗi trong radix tương ứng là như nhau.

Các API chấp nhận radix hợp lệ từ `Character.MIN_RADIX` (2) đến `Character.MAX_RADIX` (36). Với radix 16, ký tự `A`–`F` biểu thị chữ số 10–15 (không phân biệt hoa/thường trong phạm vi tương ứng). Dấu `+`/`-` ở đầu chuỗi được cho phép với signed parsing; dấu nằm giữa chuỗi thì không.

Các điểm cần chú ý:

- `Integer.parseInt("FF",16)` → 255.
- `Integer.parseInt("-FF",16)` → −255.
- `Integer.valueOf("FF",16)` → wrapper `Integer` giá trị 255.
- `Integer.parseInt("0xFF",16)` **không** tự bỏ tiền tố `0x`.
- `Integer.parseInt("010")` mặc định radix 10 → 10; không suy từ số 0 đầu chuỗi là octal.
- Radix không hợp lệ, ký tự không đúng radix hoặc kết quả ngoài miền `int` đều dẫn tới `NumberFormatException` với overload `String`.

### 8.1. `decode(String)` có nhiệm vụ khác

`Integer.decode("0xFF")` nhận tiền tố `0x` để đọc hex, `Integer.decode("#FF")` cũng có quy ước hex, còn số bắt đầu bằng `0` có thể được hiểu theo octal. Ví dụ:

```java
System.out.println(Integer.parseInt("010")); // 10
System.out.println(Integer.decode("010"));   // 8
System.out.println(Integer.decode("0xFF"));  // 255
```

`decode` trả **`Integer`**, nhưng không đồng nghĩa `valueOf(String)`: khác **ngữ pháp chuỗi chấp nhận**. Tùy yêu cầu dữ liệu, dùng radix tường minh thường ít gây bất ngờ hơn việc tự suy hệ từ prefix hoặc số 0 đầu chuỗi.

`Long.decode`, `Short.decode`, `Byte.decode` cũng có ý nghĩa tương ứng trong phạm vi kiểu của mỗi lớp. Nếu hệ thống nhận mã số theo format giao thức riêng, nên chỉ rõ quy ước (base, prefix, dấu, miền giá trị) thay vì trộn nhiều cách parse ngẫu nhiên.

## 9. Phạm vi giá trị của `Byte`, `Short`, `Integer` và `Long`

Cùng một chuỗi chỉ được nhận nếu giá trị nằm trong **miền của kiểu parse**:

| API | Miền signed |
| --- | --- |
| `Byte.parseByte` / `Byte.valueOf` | −128 đến 127 |
| `Short.parseShort` / `Short.valueOf` | −32768 đến 32767 |
| `Integer.parseInt` / `Integer.valueOf` | −2147483648 đến 2147483647 |
| `Long.parseLong` / `Long.valueOf` | −9223372036854775808 đến 9223372036854775807 |

`Byte.parseByte("128")` ném `NumberFormatException`; `Byte.valueOf("128")` cũng vậy. `Integer.parseInt("3000000000")` vượt phạm vi `int`, nhưng `Long.parseLong("3000000000")` hợp lệ và trả `long`. Với `Long.valueOf("3000000000")`, giá trị đó được bọc vào `Long`.

Đừng giải quyết lỗi miền giá trị bằng cách parse trước qua kiểu quá nhỏ rồi ép sang kiểu lớn: khi `Integer.parseInt` đã báo lỗi vì chuỗi ngoài miền, việc cast kết quả (vốn không được tạo ra) chẳng có tác dụng. **Chọn kiểu phù hợp trước khi parse.**

### 9.1. Trường hợp đặc biệt: phân tích không dấu

`Integer.parseUnsignedInt(String)` đọc giá trị unsigned 32-bit theo dạng chuỗi, nhưng vẫn trả **primitive `int`** chứa cùng mẫu bit 32-bit.

```java
int bits = Integer.parseUnsignedInt("4294967295");
System.out.println(bits);                           // -1
System.out.println(Integer.toUnsignedString(bits)); // 4294967295
```

Giá trị `-1` ở dòng đầu là cách **đọc signed** của bit pattern toàn 1; dòng sau diễn giải **cùng bit pattern** dưới dạng unsigned. Không có primitive `unsigned int` riêng được tạo.

`Integer.valueOf("4294967295")` không thể thay cho `parseUnsignedInt` vì nó dùng signed parsing thông thường và giá trị vượt miền `int` signed. Nếu cần wrapper cho kết quả unsigned bits, viết:

```java
Integer bitsObject = Integer.valueOf(
        Integer.parseUnsignedInt("4294967295"));
```

Đối với 64 bit, `Long.parseUnsignedLong` có ý nghĩa tương tự và trả `long`. Hãy xác định rõ contract của hệ thống trước khi hiển thị hoặc so sánh signed/unsigned.

## 10. Chương trình hoàn chỉnh: giá trị số, wrapper và phạm vi

Lưu thành **`IntegerParsingTypes.java`**:

```java
public class IntegerParsingTypes {
    public static void main(String[] args) {
        int primitive = Integer.parseInt("42");
        Integer boxed = Integer.valueOf("42");

        System.out.println(primitive);
        System.out.println(boxed.getClass().getSimpleName());
        System.out.println(primitive == boxed);

        long large = Long.parseLong("3000000000");
        Long boxedLong = Long.valueOf("3000000000");
        System.out.println(large);
        System.out.println(boxedLong.getClass().getSimpleName());

        System.out.println(Byte.parseByte("127"));
        System.out.println(Short.valueOf("32767"));

        System.out.println(Integer.valueOf("42")
                .equals(Long.valueOf("42")));
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac --release 17 IntegerParsingTypes.java
java IntegerParsingTypes
```

**Stdout chính xác:**

```text
42
Integer
true
3000000000
Long
127
32767
false
```

Phân tích:

1. `primitive` là `int`, `boxed` là `Integer`; cùng một lượng số học `42`.
2. So sánh `primitive == boxed` sẽ unbox `boxed` thành `int`.
3. `Long.parseLong`/`Long.valueOf` có kiểu trả về `long`/`Long`, phù hợp khi `3000000000` vượt miền `int`.
4. `Byte.parseByte` trả `byte`; `Short.valueOf` trả wrapper `Short`.
5. `Integer.valueOf("42").equals(Long.valueOf("42"))` false vì `Integer.equals` chỉ nhận cùng lớp `Integer` với cùng giá trị; việc cùng số 42 không có nghĩa mọi wrapper loại khác nhau đều `equals`.

## 11. Chương trình hoàn chỉnh: radix, decode và unsigned

Lưu thành **`ParsingRadix.java`**:

```java
public class ParsingRadix {
    public static void main(String[] args) {
        System.out.println(Integer.parseInt("FF", 16));
        System.out.println(Integer.valueOf("FF", 16));
        System.out.println(Integer.parseInt("-FF", 16));

        System.out.println(Integer.parseInt("010"));
        System.out.println(Integer.decode("010"));
        System.out.println(Integer.decode("0xFF"));

        int unsignedBits =
                Integer.parseUnsignedInt("4294967295");
        System.out.println(unsignedBits);
        System.out.println(
                Integer.toUnsignedString(unsignedBits));

        System.out.println(Integer.parseInt("+42"));
        System.out.println(Integer.valueOf("-0"));
    }
}
```

Chạy:

```bash
javac --release 17 ParsingRadix.java
java ParsingRadix
```

**Stdout chính xác:**

```text
255
255
-255
10
8
255
-1
4294967295
42
0
```

Phương thức `decode` khác `parseInt` trong cách nhận diện tiền tố và số 0 đầu. Phương thức `parseUnsignedInt` khác `parseInt` trong miền dữ liệu được chấp nhận, **không** đổi kiểu primitive của kết quả. Mọi phép `println` ở đây in giá trị chứ không tự cho thấy kiểu `int` hoặc `Integer` của biểu thức; muốn xác minh kiểu hãy đọc chữ ký hoặc khai báo biến.

## 12. Kiểm tra lỗi parse đúng cách

Với dữ liệu từ nhập liệu, file, API hoặc database, `NumberFormatException` là một phần của hợp đồng. Mã mẫu nên xác định chính sách khi gặp input không hợp lệ, thay vì mặc định trả `0` mà không thông báo.

Lưu thành **`SafeIntegerParsing.java`**:

```java
public class SafeIntegerParsing {
    static void show(String raw) {
        try {
            int result = Integer.parseInt(raw);
            System.out.println("ok: " + result);
        } catch (NumberFormatException ex) {
            System.out.println("invalid: [" + raw + "]");
        }
    }

    public static void main(String[] args) {
        show("42");
        show(" 42 ");
        show("42.0");
        show("1_000");
        show("2147483648");
        show("");
    }
}
```

Chạy:

```bash
javac --release 17 SafeIntegerParsing.java
java SafeIntegerParsing
```

**Stdout chính xác:**

```text
ok: 42
invalid: [ 42 ]
invalid: [42.0]
invalid: [1_000]
invalid: [2147483648]
invalid: []
```

Có thể thay `Integer.parseInt(raw)` bằng `Integer.valueOf(raw)` rồi gán kết quả cho `Integer`; **các input sai vẫn sai**. Việc catch ngoại lệ trong ví dụ nhằm làm rõ ranh giới input hợp lệ/không hợp lệ, không phải khuyến nghị âm thầm nuốt lỗi trong ứng dụng thực tế.

### 12.1. Khi nào nên `trim` hoặc `strip`?

Nếu hợp đồng **cho phép khoảng trắng ở hai đầu**, chuẩn hóa một cách có chủ ý trước khi parse:

```java
String input = " 42 ";
int number = Integer.parseInt(input.strip()); // 42
```

Nếu hợp đồng cấm khoảng trắng, **đừng chuẩn hóa rồi lặng lẽ nhận input sai**. `strip` xử lý Unicode whitespace theo `Character.isWhitespace`, không đồng nghĩa mọi ký tự trông như khoảng trắng đều bị xóa. `trim` có quy tắc khác; chọn cách dựa trên định dạng dữ liệu.

Nếu chuỗi có hàng nghìn phân cách, ký hiệu tiền tệ hoặc số được định dạng theo locale, `parseInt`/`valueOf` không phải parser định dạng tổng quát. Cần định nghĩa format hoặc dùng công cụ thích hợp có kiểm soát.

### 12.2. Tránh dùng exception cho mọi dòng trong dữ liệu khối lớn mà không đánh giá

Với nguồn dữ liệu sạch đã validate, gọi parse trực tiếp là hợp lý. Với dữ liệu ngoài có thể lỗi nhiều, dùng một lớp validation và trả kết quả rõ trạng thái; cân nhắc chi phí tạo exception nếu parse hàng triệu bản ghi lỗi. Tuy nhiên, **đừng tự viết parser số tùy tiện** chỉ để tránh ngoại lệ khi không thật sự cần hiệu năng riêng.

## 13. Overload `valueOf` không chỉ nhận String

Một nguyên nhân gây nhầm là tên `valueOf` có nhiều overload:

```java
Integer fromNumber = Integer.valueOf(42);   // valueOf(int) -> Integer
Integer fromText = Integer.valueOf("42");  // valueOf(String) -> Integer
Integer fromHex = Integer.valueOf("2A", 16); // valueOf(String,int) -> Integer
```

Các phương thức trên có **đầu vào khác nhau** nhưng đều trả `Integer`.

`Integer.valueOf(42)` không parse ký tự nào: nó nhận sẵn primitive `int` để tạo/lấy wrapper. `Integer.valueOf("42")` parse `String`. `Integer.valueOf("2A",16)` parse chuỗi theo radix 16.

Đừng viết `Integer.parseInt(42)` vì `parseInt` cần chuỗi (hoặc overload `CharSequence` kèm chỉ số/radix), không có overload đơn giản nhận một `int`.

### 13.1. Phương thức trên CharSequence

Trong Java 17, `Integer.parseInt(CharSequence s, int beginIndex, int endIndex, int radix)` và `Long.parseLong(CharSequence,beginIndex,endIndex,radix)` cho phép parse một đoạn `CharSequence` mà không nhất thiết phải tạo `String` con theo cách thủ công.

Ví dụ:

```java
CharSequence line = "id=0042;";
int id = Integer.parseInt(line, 3, 7, 10); // 42
```

Đoạn trên là mảnh code; `beginIndex` được tính từ 0 và `endIndex` là **exclusive**. `"id=0042;"` có bốn chữ số ở chỉ số 3..6. Cần kiểm soát index và bảo đảm input không bị thay đổi đồng thời nếu `CharSequence` mutable. Đây vẫn là **parse trả primitive `int`**; không có overload tương ứng `Integer.valueOf(CharSequence,begin,end,radix)` trong Java 17.

## 14. So sánh `valueOf` với constructor wrapper cũ

Trong Java 17, constructor `new Integer(String)` và `new Integer(int)` đã được **deprecated for removal**. Đối với các ứng dụng mới, ưu tiên:

- `Integer.parseInt("42")` khi cần `int`.
- `Integer.valueOf("42")` khi cần `Integer` từ `String`.
- `Integer.valueOf(42)` khi đã có một `int` muốn dùng dưới dạng wrapper.

`valueOf` có thể tái sử dụng object được cache, trong khi constructor cũ tạo object mới, nhưng **đừng dùng điều này để viết logic dựa trên object identity**. Mục tiêu là API rõ ràng và tuân thủ khuyến nghị JDK chứ không phải tối ưu tùy tiện bằng `==` trên wrapper.

### 14.1. Vì sao không dùng `new Integer("42")` để né cache?

Một số code cũ cố ý tạo hai object khác nhau để `a == b` trả false. Đây không phải cơ chế kiểm tra giá trị đúng: `==` ở hai reference kiểm tra identity; `equals` mới là kiểm tra cùng giá trị trong cùng wrapper class. Việc tạo object mới chỉ để điều khiển `==` làm code phụ thuộc chi tiết không cần thiết.

## 15. Mở rộng để tránh kết luận sai về mọi lớp parse/valueOf

**Trong chủ đề số nguyên**, `Byte`, `Short`, `Integer`, `Long` có các cặp `parse...` trả primitive và `valueOf(String)` trả wrapper. Tuy nhiên, không được áp dụng một cách máy móc rằng “mọi lớp Java đều có `parseX` và `valueOf(String)` cùng quy tắc”.

Ví dụ:

- `Double.parseDouble(String)` trả primitive `double`; `Double.valueOf(String)` trả wrapper `Double`.
- `Float.parseFloat(String)` trả primitive `float`; `Float.valueOf(String)` trả wrapper `Float`.
- `Boolean.parseBoolean(String)` trả primitive `boolean` và `Boolean.valueOf(String)` trả `Boolean`, **nhưng quy tắc chuỗi khác số nguyên**: chỉ `"true"` (không phân biệt chữ hoa/thường) được coi true, các chuỗi khác false, không ném `NumberFormatException` như integer parser.
- `BigInteger` không có `parseBigInteger(String)` hay `valueOf(String)` tương ứng; thông dụng là `new BigInteger(String)`, còn `BigInteger.valueOf(long)` nhận primitive `long`.
- `BigDecimal` có constructor từ chuỗi để giữ số thập phân chính xác theo định dạng hỗ trợ; `BigDecimal.valueOf(long)` hay `valueOf(double)` có hợp đồng khác, không phải overload `valueOf(String)`.

Phải đọc **chữ ký và tài liệu của đúng lớp/overload**. Tên “parse” hoặc “valueOf” là một dấu hiệu hữu ích, không thay thế type checking.

## 16. Những lỗi thường gặp khi review code

1. **“`Integer.valueOf("12")` trả `int`.”** Sai: trả `Integer`. Unboxing ở phép gán có thể khiến ta tưởng ngược.
2. **“`parseInt` không thêm được vào `List<Integer>`.”** Sai: compiler có thể autobox. Nhưng `List<int>` không hợp lệ.
3. **“`valueOf(String)` tự trả null nếu chuỗi sai.”** Sai: ném `NumberFormatException`.
4. **“`valueOf` hiểu `"0xFF"` nhưng `parseInt` không hiểu.”** Sai với overload radix 10; dùng `decode` hoặc truyền `"FF",16` khi phù hợp.
5. **“Chuỗi `"1_000"` hợp lệ vì literal `1_000` hợp lệ.”** Sai: Java source literal và nội dung `String` có ngữ pháp khác nhau.
6. **“`Integer.parseInt("010")` hiểu octal.”** Sai: mặc định decimal 10; `Integer.decode("010")` mới có quy ước octal.
7. **“Nếu là `Integer` thì mọi số đều có thể là null.”** Lớp wrapper có thể nhận null **ở biến tham chiếu**, nhưng `valueOf("42")` không trả null; null thường đến từ một nguồn khác.
8. **“`Integer.valueOf("42") == Integer.valueOf("42")` luôn là cách so số đúng.”** Không nên dùng reference identity. Dùng `equals` cho cùng wrapper hoặc so primitive.
9. **“Trộn `Integer` và `Long` cùng giá trị sẽ `equals` true.”** Sai: wrapper equality phân biệt class.
10. **“Sau khi parse int quá lớn, cast `long` sẽ sửa.”** Sai: lỗi xảy ra trong `parseInt` trước khi có kết quả.
11. **“`parseUnsignedInt` trả unsigned primitive riêng.”** Sai: vẫn là `int` với bit được diễn giải unsigned theo API đi kèm.
12. **“Bất kỳ khoảng trắng nào đều tự được bỏ.”** Sai: `parseInt` và `valueOf` không tự trim.
13. **“`Integer.parseInt(null)` mặc định trả 0.”** Sai: không có giá trị hợp lệ; overload `String` ném `NumberFormatException`.
14. **“`Integer.valueOf(42)` và `Integer.valueOf("42")` cùng parse chuỗi.”** Sai: overload nhận `int` không parse, overload nhận `String` có parse.
15. **“`Integer.parseInt` có thể đọc số thập phân `"42.0"` rồi bỏ phần lẻ.”** Sai: `"42.0"` không là chuỗi integer hợp lệ; cast `double` và parse integer là hai thao tác khác nhau.

## 17. Bài tập có đáp án giải thích

### Bài 1 — Trả kiểu gì?

Chọn kiểu compile-time của `Integer.parseInt("7")`, `Integer.valueOf("7")`, `Long.parseLong("7")` và `Long.valueOf("7")`.

**Đáp án:** lần lượt `int`, `Integer`, `long`, `Long`. Chữ số trong chuỗi không quyết định kiểu trả về.

### Bài 2 — Boxing nằm ở đâu?

Tại sao `Integer x = Integer.parseInt("10");` vẫn hợp lệ?

**Đáp án:** `parseInt` trả `int`; compiler áp dụng boxing để gán vào `Integer`. `parseInt` không đổi chữ ký vì vế trái là wrapper.

### Bài 3 — Unboxing nằm ở đâu?

Tại sao `int x = Integer.valueOf("10");` hợp lệ?

**Đáp án:** `valueOf(String)` trả `Integer`; compiler unbox sang `int` khi gán. Nếu nhận `null` từ nguồn khác khi unbox, chương trình có thể ném NPE; `valueOf("10")` không phải nguồn null đó.

### Bài 4 — Sai cú pháp chuỗi

`Integer.parseInt("1_000")` và `Integer.valueOf("1_000")` có chấp nhận không?

**Đáp án:** không. Gạch dưới thuộc cú pháp literal trong Java source, không thuộc dạng chuỗi số nguyên mà hai phương thức này nhận.

### Bài 5 — Hex và radix

Làm sao lấy giá trị 255 từ chuỗi `"FF"` bằng cả parse và valueOf?

**Đáp án:** `int p = Integer.parseInt("FF",16);` và `Integer v = Integer.valueOf("FF",16);`. Cả hai cho giá trị số 255, khác kiểu kết quả.

### Bài 6 — Decode

`Integer.parseInt("010")` và `Integer.decode("010")` trả giá trị gì?

**Đáp án:** `10` và wrapper `Integer` giá trị `8`, vì `decode` nhận diện số 0 đầu theo octal, còn `parseInt` mặc định decimal.

### Bài 7 — Miền giá trị

`Byte.valueOf("128")`, `Integer.parseInt("3000000000")` và `Long.parseLong("3000000000")` diễn ra thế nào?

**Đáp án:** hai lời gọi đầu ném `NumberFormatException` vì vượt miền; lời gọi thứ ba hợp lệ và trả `long` 3000000000.

### Bài 8 — So wrapper

`Integer.valueOf("42").equals(Long.valueOf("42"))` trả gì?

**Đáp án:** false vì khác wrapper class, dù có cùng lượng toán học. Nếu thật sự cần numeric equality xuyên kiểu, phải định nghĩa chính sách chuyển kiểu và miền dữ liệu riêng.

### Bài 9 — Unsigned

`Integer.parseUnsignedInt("4294967295")` trả kiểu gì và in giá trị signed nào?

**Đáp án:** trả `int` có bit toàn 1; `System.out.println` với `int` in `-1`. `Integer.toUnsignedString(bits)` in `4294967295`.

### Bài 10 — Null

Nếu `String text = null;`, `Integer.parseInt(text)` và `Integer.valueOf(text)` có trả 0/null không?

**Đáp án:** không; overload nhận `String` ném `NumberFormatException`. `Integer.valueOf((String)null)` khác với việc một biến `Integer` đã có giá trị `null`.

### Bài 11 — Overload method

Cho hai phương thức `show(int)` và `show(Integer)`. `show(Integer.parseInt("9"))` chọn phương thức nào, và `show(Integer.valueOf("9"))` chọn phương thức nào?

**Đáp án:** đầu chọn `show(int)`, sau chọn `show(Integer)`. Kiểu biểu thức ở call site quyết định overload phù hợp.

### Bài 12 — Thiết kế API

Một method tính tổng triệu số nguyên và một method trả dữ liệu có trạng thái chưa nhập, nên dùng kiểu nào?

**Đáp án:** tổng tính toán có thể dùng primitive `int`/`long` tùy miền; trường có trạng thái thiếu phải có mô hình riêng như `Integer` nullable, `OptionalInt` hoặc record trạng thái. Không thể quyết định chỉ dựa trên tốc độ mà bỏ qua yêu cầu null/error.

### Bài 13 — Decimal và octal

Tại sao `Integer.valueOf("010")` không giống `Integer.decode("010")`?

**Đáp án:** `valueOf(String)` áp dụng signed decimal giống `parseInt(String)`, cho 10; `decode` hiểu prefix số 0 theo quy tắc riêng nên cho 8.

### Bài 14 — Chuyển sang wrapper có giải quyết giá trị vượt miền không?

Giá trị `"2147483648"` vượt `Integer.MAX_VALUE`. `Integer.valueOf("2147483648")` có thể chứa nó vì là object không?

**Đáp án:** không. `Integer` bọc chính miền giá trị của `int`. Dùng `Long.parseLong`/`Long.valueOf` hoặc `BigInteger` nếu miền dữ liệu yêu cầu, không suy rằng wrapper tăng độ rộng số.

## 18. Checklist và tài liệu chuẩn

- [ ] Tôi nhớ đúng `parseInt(String) -> int` và `valueOf(String) -> Integer`.
- [ ] Tôi đối chiếu được `parseLong/valueOf`, `parseShort/valueOf` và `parseByte/valueOf`.
- [ ] Tôi xác định được nơi boxing/unboxing diễn ra khi chọn kiểu biến đích.
- [ ] Tôi biết wrapper có thể được dùng trong generic collections nhưng `List<int>` không hợp lệ.
- [ ] Tôi không dùng `==` giữa hai reference wrapper để kiểm tra cùng giá trị.
- [ ] Tôi hiểu cả parse và valueOf đều có thể ném `NumberFormatException` trên input sai.
- [ ] Tôi kiểm tra signed range trước khi chọn `byte`, `short`, `int` hay `long`.
- [ ] Tôi dùng đúng overload radix khi chuỗi không phải hệ thập phân.
- [ ] Tôi không nhầm `decode` với `parseInt`/`valueOf(String)`.
- [ ] Tôi biết parseUnsignedInt vẫn trả `int` và cần API unsigned để hiển thị/so sánh tương ứng.
- [ ] Tôi phân biệt Java source literal có underscore/prefix với chuỗi cần parser.
- [ ] Tôi biết `valueOf(int)` không giống `valueOf(String)` về đầu vào.
- [ ] Tôi đã chạy cả ba chương trình Java 17+ và thử thêm input rỗng, khoảng trắng, ngoài miền, sai radix.
- [ ] Tôi ghi rõ hợp đồng parse lỗi và xử lý `null` trước khi unbox.

**Tài liệu Java SE 17 chính thức:**

- [Integer API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): `parseInt`, `valueOf`, `decode`, `parseUnsignedInt`, `toUnsignedString`.
- [Long API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html): `parseLong`, `valueOf`, `parseUnsignedLong`.
- [Byte API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Byte.html): `parseByte`, `valueOf`.
- [Short API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Short.html): `parseShort`, `valueOf`.
- [Character API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): `digit` và khái niệm radix.
- [JLS 17, `5.1.7–`5.1.8 Boxing and Unboxing Conversions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7): quy tắc boxing/unboxing.
- [BigInteger API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): chuyển chuỗi thành số nguyên tùy độ chính xác khi vượt miền primitive.

**Kết luận:** khi đọc một chuỗi số nguyên, `parse...` và `valueOf(String)` phần lớn cùng cách hiểu **chuỗi số**, nhưng khác **kiểu trả về**. Hãy ưu tiên primitive khi API cần giá trị primitive, dùng wrapper khi mô hình cần object/generic/nullable, và nhớ rằng lỗi cú pháp, phạm vi, radix và chính sách xử lý dữ liệu không được `valueOf` tự động “giải quyết”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-003">Radix, dấu cộng hoặc trừ và phạm vi hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Radix, dấu cộng hoặc trừ và phạm vi hợp lệ khi phân tích số nguyên trong Java

Khi nhận một số nguyên dưới dạng `String`, Java phải trả lời ba câu hỏi độc lập: **chuỗi dùng hệ cơ số nào (radix)?**, **dấu đứng ở đâu và có hợp lệ không?**, **giá trị toán học có nằm trong phạm vi của kiểu đích không?** Đọc đúng các ký tự vẫn chưa đủ: một chuỗi chỉ gồm chữ số hợp lệ có thể bị từ chối vì tràn phạm vi. Bài này tập trung vào `Integer.parseInt`, `Long.parseLong` và các lựa chọn phù hợp khi dữ liệu không còn là số nguyên có dấu thông thường.

## 1. Radix là gì?

Radix (cơ số) quyết định tập chữ số được phép dùng và giá trị của từng vị trí. Với chuỗi các chữ số \(d_k\dots d_1d_0\) trong cơ số \(r\), phần trị tuyệt đối là:

\[
|n|=\sum_{i=0}^{k} d_i r^i,\qquad 0\le d_i<r.
\]

Ví dụ, `"1A"` ở hệ 16 có giá trị \(1\times16+10=26\); `"101101"` ở hệ 2 có giá trị \(32+8+4+1=45\). Cùng một chuỗi `"10"` có giá trị 2 trong hệ 2, 10 trong hệ 10, và 16 trong hệ 16.

Trong các phương thức parse có tham số `radix`, Java hỗ trợ cơ số **từ 2 đến 36**, tương ứng `Character.MIN_RADIX` và `Character.MAX_RADIX`. Chữ số `0`–`9` mang giá trị 0–9; chữ cái `a`–`z` hoặc `A`–`Z` mang giá trị 10–35 khi cơ số đủ lớn. Do đó `F` hợp lệ ở hệ 16, còn `G` không hợp lệ ở hệ 16 (nhưng hợp lệ ở hệ 17 trở lên).

| Radix | Ký tự điển hình | Ví dụ và giá trị thập phân |
| --- | --- | --- |
| 2 (binary) | `0`, `1` | `101101` → 45 |
| 8 (octal) | `0`–`7` | `17` → 15 |
| 10 (decimal) | `0`–`9` | `17` → 17 |
| 16 (hexadecimal) | `0`–`9`, `A`–`F` | `7F` → 127 |
| 36 | `0`–`9`, `A`–`Z` | `Z` → 35 |

API cơ bản:

```java
int decimal = Integer.parseInt("255");       // mặc định radix 10
int binary  = Integer.parseInt("11111111", 2); // 255
int hex     = Integer.parseInt("ff", 16);      // 255
long big    = Long.parseLong("7fffffffffffffff", 16); // Long.MAX_VALUE
```

`Integer.parseInt(String)` **luôn dùng hệ 10**. `Integer.parseInt(String, int)` dùng chính xác cơ số được truyền; Java không tự suy ra radix từ tiền tố hay từ số 0 đứng đầu.

## 2. Dấu cộng, dấu trừ và cú pháp chuỗi

Với cách viết ASCII thông thường, dạng hợp lệ của `parseInt`/`parseLong` là:

```text
[+|-]? <ít nhất một chữ số hợp lệ trong radix>
```

- Dấu `+` hoặc `-` là **tùy chọn** và chỉ được đứng ở ký tự đầu tiên. `"+7F"` (radix 16) cho 127; `"-7F"` cho -127; `"-"` và `"+"` đơn lẻ không hợp lệ.
- Dấu chỉ tác động lên giá trị của toàn bộ dãy chữ số. `"-101"` (radix 2) là **-5**, không phải một mẫu bit two's complement tự động được diễn giải thành số âm.
- `"0"`, `"+0"` và `"-0"` đều phân tích thành giá trị số 0. Số 0 đứng đầu được chấp nhận nhưng **không biến chuỗi thành hệ 8**: `Integer.parseInt("010")` cho 10.
- Khoảng trắng, dấu phẩy, dấu chấm, dấu gạch dưới và ký tự thừa không được tự bỏ qua: `" 42"`, `"42 "`, `"1_000"`, `"1,000"` và `"42L"` đều không hợp lệ với `parseInt`.
- `Integer.parseInt("0xFF", 16)` và `Integer.parseInt("0b1010", 2)` đều thất bại vì `x`/`b` trong các chuỗi này không phải chữ số hợp lệ tại vị trí tương ứng. Nếu tự chọn radix, chỉ truyền phần chữ số như `"FF"` và `"1010"`.
- Chữ cái thường/hoa tương đương ở các radix chứa chữ cái: `"aF"` và `"Af"` trong hệ 16 đều là 175.
- Radix ngoài đoạn 2–36, chữ số có giá trị lớn hơn hoặc bằng radix, chuỗi rỗng và `null` đều dẫn đến `NumberFormatException` ở `Integer.parseInt(String, int)`.

**Chi tiết Unicode:** `parseInt` dùng logic chữ số dựa trên `Character.digit(char, radix)`, vì thế một số ký tự chữ số Unicode ngoài ASCII cũng có thể được chấp nhận. Nếu giao thức dữ liệu chỉ cho phép `[0-9A-Za-z]` hoặc chỉ chữ số ASCII, cần **kiểm tra định dạng riêng trước khi parse**. Không suy diễn rằng `parseInt` đồng nghĩa với một bộ kiểm tra ASCII nghiêm ngặt.

### Bảng các tình huống thường gây nhầm lẫn

| Lời gọi | Kết quả | Giải thích |
| --- | --- | --- |
| `Integer.parseInt("+101", 2)` | `5` | Dấu cộng hợp lệ ở đầu |
| `Integer.parseInt("-101", 2)` | `-5` | Dấu âm áp lên trị số |
| `Integer.parseInt("077")` | `77` | Mặc định vẫn là hệ 10 |
| `Integer.parseInt("077", 8)` | `63` | Đã yêu cầu hệ 8 |
| `Integer.parseInt("7f", 16)` | `127` | `f` = 15 |
| `Integer.parseInt("2", 2)` | Lỗi | `2` không phải chữ số hệ 2 |
| `Integer.parseInt("0xFF", 16)` | Lỗi | Không tự nhận tiền tố `0x` |
| `Integer.parseInt("--5")` | Lỗi | Chỉ cho phép một dấu ở đầu |
| `Integer.parseInt("1_000")` | Lỗi | Dấu `_` không được parse từ chuỗi |
| `Integer.parseInt(" 5 ")` | Lỗi | Không tự `trim()` |

Không nên vô điều kiện gọi `trim()`/`strip()` chỉ để “làm cho parse thành công”. Hãy quyết định rõ hợp đồng đầu vào: với dữ liệu người dùng có thể cho phép khoảng trắng ở hai đầu; với mã định danh hoặc dữ liệu máy cần giữ chặt định dạng gốc.

## 3. Phạm vi giá trị: đúng chữ số vẫn có thể bị từ chối

`int` và `long` của Java là số nguyên **có dấu**. Radix chỉ thay đổi cách biểu diễn, **không thay đổi phạm vi chứa được**.

| Kiểu | Độ rộng | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | --- | --- | --- |
| `byte` | 8 bit | -128 | 127 |
| `short` | 16 bit | -32 768 | 32 767 |
| `int` | 32 bit | -2 147 483 648 | 2 147 483 647 |
| `long` | 64 bit | -9 223 372 036 854 775 808 | 9 223 372 036 854 775 807 |

Về toán học, một kiểu số nguyên có dấu rộng \(w\) bit có miền \([-2^{w-1},\,2^{w-1}-1]\). Do đó **biên âm có trị tuyệt đối lớn hơn biên dương 1 đơn vị**.

Những ví dụ then chốt của `Integer.parseInt`:

```java
Integer.parseInt("2147483647");       // hợp lệ: Integer.MAX_VALUE
Integer.parseInt("-2147483648");      // hợp lệ: Integer.MIN_VALUE

Integer.parseInt("2147483648");       // NumberFormatException
Integer.parseInt("-2147483649");      // NumberFormatException

Integer.parseInt("7fffffff", 16);     //  2147483647, hợp lệ
Integer.parseInt("-80000000", 16);    // -2147483648, hợp lệ
Integer.parseInt("80000000", 16);     // NumberFormatException
Integer.parseInt("-80000001", 16);    // NumberFormatException
```

`"80000000"` trong hệ 16 có giá trị **dương** 2 147 483 648, vượt `int` có dấu. Java không tự bọc vòng (wrap) thành -2 147 483 648 khi gọi `parseInt`; đây là lỗi phạm vi. Tương tự, chuỗi 64-bit lớn hơn `Long.MAX_VALUE` không thể parse bằng `Long.parseLong` thông thường dù các chữ số hoàn toàn hợp lệ.

Về cơ chế, parser phải kiểm tra tràn trong lúc nhân giá trị tích lũy với radix rồi thêm chữ số tiếp theo. JDK có thể tích lũy ở phía âm để xử lý an toàn cả `Integer.MIN_VALUE`, vì giá trị `2147483648` không biểu diễn được bằng `int` dương. Đây là lý do không nên tự viết parser bằng phép tính `int result = result * radix + digit` rồi chỉ kiểm tra phạm vi ở cuối: phép tính có thể đã tràn trước khi kiểm tra.

Đối với `byte` và `short`, dùng `Byte.parseByte`/`Short.parseShort`; chúng áp dụng miền tương ứng. Parse vào `int` rồi ép kiểu `(byte)` hoặc `(short)` có thể âm thầm mất dữ liệu:

```java
Byte.parseByte("128");  // NumberFormatException: quá 127
byte lost = (byte) Integer.parseInt("128"); // -128 vì bị thu hẹp kiểu
```

Khi cần từ chối dữ liệu ngoài phạm vi, hãy dùng parser đúng kiểu hoặc kiểm tra biên trước khi chuyển kiểu.

## 4. Phân biệt chuỗi runtime, literal Java và tự nhận diện tiền tố

Ba cơ chế thường bị lẫn:

1. **Literal trong mã nguồn** do trình biên dịch Java đọc: `10` (thập phân), `010` (bát phân), `0x10` (thập lục phân), `0b10` (nhị phân). Từ Java 7, literal còn có thể chứa `_` ở các vị trí hợp lệ, ví dụ `1_000`.
2. **`Integer.parseInt(text, radix)`** đọc một chuỗi runtime với radix đã định. Nó không áp dụng cú pháp literal: `"010"` với radix 10 là 10 và `"1_000"` không hợp lệ.
3. **`Integer.decode(text)`** nhận diện một số tiền tố theo quy tắc riêng: `0x`/`0X` hoặc `#` cho hệ 16, số `0` đứng đầu (với nhiều hơn một chữ số) cho hệ 8, còn lại dùng hệ 10. Có thể có `+`/`-` **trước tiền tố**, ví dụ `"-0x2A"` → -42; đặt dấu sau tiền tố như `"0x-2A"` không hợp lệ.

Ví dụ:

```java
Integer.parseInt("010");     // 10
Integer.parseInt("010", 8);  // 8
Integer.decode("010");       // 8
Integer.decode("0x10");      // 16
Integer.decode("#10");       // 16
Integer.decode("-0x2A");     // -42
Integer.decode("08");        // NumberFormatException: 8 không thuộc hệ 8
```

Chọn `decode` khi **đặc tả đầu vào thực sự cho phép** kiểu tiền tố trên. Với dữ liệu cần hệ 10 nhất quán, nên dùng `parseInt(text, 10)` để tránh việc `"010"` bất ngờ mang nghĩa hệ 8. `decode` cũng chịu giới hạn `int` có dấu; nó không giải quyết lỗi tràn.

Một khác biệt tinh tế: `0xFFFFFFFF` viết trực tiếp như một literal `int` có thể biểu diễn mẫu bit -1 theo quy tắc literal của Java, nhưng `Integer.parseInt("FFFFFFFF", 16)` vẫn từ chối vì đây là chuỗi biểu diễn **giá trị dương** 4 294 967 295, vượt `Integer.MAX_VALUE`.

## 5. Khi chuỗi biểu diễn một giá trị unsigned hoặc số lớn

Trong Java, kiểu nguyên thủy `int` và `long` vẫn lưu theo dạng có dấu. Nếu đặc tả dữ liệu là số nguyên **không dấu** (unsigned), dùng API parse unsigned tương ứng, rồi diễn giải kết quả cho đúng:

```java
int bits = Integer.parseUnsignedInt("FFFFFFFF", 16);
// bits == -1 nếu nhìn như int có dấu, nhưng bit pattern là 0xFFFFFFFF

long unsignedValue = Integer.toUnsignedLong(bits); // 4294967295
String decimal = Integer.toUnsignedString(bits);   // "4294967295"
String hex = Integer.toUnsignedString(bits, 16);    // "ffffffff"
```

`Integer.parseUnsignedInt` cho miền số học 0 đến \(2^{32}-1\), còn `Long.parseUnsignedLong` cho 0 đến \(2^{64}-1\). Giá trị trả về vẫn là `int`/`long` nguyên thủy, vì vậy các giá trị có bit dấu bật sẽ hiển thị như số âm nếu dùng `System.out.println(bits)` hoặc các phép toán signed thông thường.

`Integer.parseUnsignedInt("-1", 10)` không hợp lệ: dấu âm không phù hợp với miền unsigned. Sau khi parse, dùng các API có chữ `Unsigned` khi cần so sánh, chia hoặc định dạng theo ngữ nghĩa unsigned (`Integer.compareUnsigned`, `Integer.divideUnsigned`, `Integer.toUnsignedString`...). Với `long` unsigned vượt `Long.MAX_VALUE`, muốn xử lý như một số dương lớn trong tính toán thường dùng `BigInteger`, chẳng hạn:

```java
BigInteger maxUnsignedLong =
        new BigInteger("FFFFFFFFFFFFFFFF", 16); // 18446744073709551615
```

Khi số có thể lớn hơn miền `int`, `long` hoặc `unsigned long`, dùng `java.math.BigInteger(String value, int radix)`. `BigInteger` vẫn yêu cầu chuỗi chữ số hợp lệ với radix và dấu ở đầu; nó không tự chấp nhận `"0x"` khi đã truyền radix 16. Không có giới hạn cố định 32/64 bit như kiểu nguyên thủy, nhưng vẫn phải để ý giới hạn bộ nhớ/tài nguyên với đầu vào quá dài.

### Chọn API theo hợp đồng đầu vào

| Đầu vào cần đọc | API nên dùng | Điều cần ghi nhớ |
| --- | --- | --- |
| Số có dấu hệ 10 trong miền 32 bit | `Integer.parseInt(text)` | Không chấp nhận tiền tố hoặc phân cách |
| Số có dấu hệ 2–36 trong miền 32 bit | `Integer.parseInt(text, radix)` | Dấu đầu chuỗi, kiểm tra radix và tràn |
| Số có dấu trong miền 64 bit | `Long.parseLong(text, radix)` | Giới hạn của `long` |
| Văn bản cho phép `0x`, `#`, số 0 đầu | `Integer.decode(text)` | `010` trở thành hệ 8 |
| Số không dấu 32 bit | `Integer.parseUnsignedInt(text, radix)` | Kết quả là `int` chứa bit pattern |
| Số không dấu 64 bit | `Long.parseUnsignedLong(text, radix)` | Kết quả là `long` chứa bit pattern |
| Số nguyên tùy độ lớn | `new BigInteger(text, radix)` | Không giới hạn cố định 32/64 bit |

## 6. Chương trình Java 17+ hoàn chỉnh có thể chạy

Lưu thành **`RadixParsingDemo.java`**:

```java
import java.math.BigInteger;

public class RadixParsingDemo {
    public static void main(String[] args) {
        show("101101", 2);
        show("+7f", 16);
        show("-80000000", 16);
        show("80000000", 16);
        show("2", 2);

        System.out.println("decode -0x2A = " + Integer.decode("-0x2A"));
        System.out.println("parseInt 010 = " + Integer.parseInt("010"));
        System.out.println("decode 010 = " + Integer.decode("010"));

        int bits = Integer.parseUnsignedInt("FFFFFFFF", 16);
        System.out.println("uint bits = " + bits);
        System.out.println("uint value = " + Integer.toUnsignedLong(bits));

        BigInteger beyondInt = new BigInteger("100000000", 16);
        System.out.println("big = " + beyondInt);
        System.out.println("format = " + Integer.toString(255, 16));
    }

    private static void show(String input, int radix) {
        try {
            int value = Integer.parseInt(input, radix);
            System.out.println("\"" + input + "\" base " + radix + " -> " + value);
        } catch (NumberFormatException ex) {
            // Thông báo tự định nghĩa để ví dụ không phụ thuộc cách JDK diễn đạt lỗi.
            System.out.println("\"" + input + "\" base " + radix + " -> INVALID");
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 RadixParsingDemo.java
java RadixParsingDemo
```

Stdout kỳ vọng:

```text
"101101" base 2 -> 45
"+7f" base 16 -> 127
"-80000000" base 16 -> -2147483648
"80000000" base 16 -> INVALID
"2" base 2 -> INVALID
decode -0x2A = -42
parseInt 010 = 10
decode 010 = 8
uint bits = -1
uint value = 4294967295
big = 4294967296
format = ff
```

Trong ví dụ, `show()` phân biệt dữ liệu hợp lệ với dữ liệu sai chữ số hoặc vượt phạm vi bằng cách bắt `NumberFormatException`. Cả `"80000000"` (sai phạm vi signed int) và `"2"` trong hệ 2 (sai chữ số) đều bị từ chối; nếu ứng dụng cần thông báo chính xác nguyên nhân, phải kiểm tra thêm định dạng, radix và phạm vi theo quy tắc nghiệp vụ. Không nên dựa vào nguyên văn thông báo lỗi của JDK để phân loại vì thông báo có thể khác giữa các phiên bản.

## 7. Thiết kế lớp nhập liệu tin cậy

Để xử lý đầu vào từ biểu mẫu, CSV, giao thức mạng hoặc tệp cấu hình:

1. **Xác định hợp đồng trước:** hệ cơ số cố định hay có tiền tố? Có cho phép dấu `+`? Có chấp nhận âm? Có chấp nhận số 0 đầu, khoảng trắng, chữ số Unicode, phân cách hàng nghìn không? Có giới hạn độ dài không?
2. **Chuẩn hóa có chủ đích:** chỉ `strip()` khoảng trắng khi hợp đồng cho phép. Nếu phải chấp nhận ký hiệu như `0x`, hãy nhận diện, bỏ tiền tố và chọn radix theo quy tắc của giao thức; hoặc dùng `decode` nếu quy tắc của nó trùng khớp.
3. **Kiểm tra radix và cú pháp:** radix thuộc 2–36. Nếu chỉ chấp nhận ASCII hex có dấu, ví dụ có thể kiểm tra chuỗi bằng `[+-]?[0-9A-Fa-f]+`; với radix khác cần kiểm tra giá trị từng chữ số tương ứng (hoặc để parser kiểm tra).
4. **Dùng parser đúng miền số:** parse vào `int`/`long` khi miền ứng dụng phù hợp; dùng unsigned API cho đại lượng không dấu; dùng `BigInteger` khi cần biểu diễn vượt 64 bit.
5. **Xử lý lỗi rõ ràng:** bắt `NumberFormatException` ở biên nhập liệu, thông báo giá trị không hợp lệ cho người dùng hoặc trả lỗi có cấu trúc. Không âm thầm đổi lỗi thành `0` vì sẽ lẫn với dữ liệu `"0"` thật.
6. **Giới hạn tài nguyên:** với đầu vào không tin cậy, giới hạn độ dài trước khi parse, nhất là trước khi tạo `BigInteger`; tránh cấp phát/kiểm tra khối dữ liệu khổng lồ không cần thiết.

Một helper giữ nguyên chính sách “ASCII hex có thể có dấu, không có tiền tố”, rồi ủy quyền kiểm tra phạm vi cho `parseInt`:

```java
static int parseAsciiHexSignedInt(String text) {
    if (text == null || !text.matches("[+-]?[0-9A-Fa-f]+")) {
        throw new IllegalArgumentException("Yêu cầu chuỗi hex ASCII có thể có dấu");
    }
    return Integer.parseInt(text, 16);
}
```

Đoạn helper này **không phải một chương trình chạy độc lập**: đặt nó bên trong một class phù hợp. Regex kiểm tra **cú pháp ASCII**, còn `Integer.parseInt` kiểm tra **giá trị có vừa `int` signed hay không**. `IllegalArgumentException` do helper ném ra dành cho cú pháp; `NumberFormatException` của parser vẫn dùng cho tràn phạm vi. Vì `String.matches` sẽ biên dịch regex mỗi lần gọi, với đường xử lý lặp thường xuyên có thể khai báo trước một `Pattern` và dùng `matcher(text).matches()`.

Nếu hợp đồng đầu vào cho phép Unicode digits theo Java, không nên dùng regex ASCII như trên. Nếu hợp đồng bắt buộc radix 10 và giới hạn 0–100, kiểm tra phạm vi nghiệp vụ sau khi parse (`0 <= value && value <= 100`) ngoài phạm vi kỹ thuật của `int`.

## 8. Các lỗi thường gặp và cách tự kiểm tra

- **Nhầm radix với phạm vi:** đổi từ radix 10 sang 16 không làm `int` chứa được số lớn hơn; nó chỉ thay cách viết.
- **Nhầm dấu âm với bit dấu:** `"-FF"` là -255, còn `"FFFFFFFF"` là số dương 4 294 967 295 về mặt văn bản; muốn đọc mẫu bit unsigned, dùng `parseUnsignedInt`.
- **Tưởng tiền tố luôn được hiểu:** `parseInt("0xFF", 16)` không hợp lệ, nhưng `decode("0xFF")` hợp lệ.
- **Áp dụng luật literal cho String:** `1_000` hợp lệ trong mã Java, còn `"1_000"` không thể parse trực tiếp.
- **Coi mọi số có số 0 đầu là octal:** chỉ cách diễn giải như `decode` (và một số literal) có quy tắc đó; `parseInt("010")` vẫn là 10.
- **Cắt dữ liệu qua cast:** `(byte) 128` thành -128; đây không phải kiểm tra phạm vi.
- **Tin rằng chữ số đúng luôn parse được:** `"2147483648"` chỉ chứa ký tự hợp lệ hệ 10 nhưng vẫn vượt `int`.
- **Bắt `Exception` quá rộng hoặc dùng mặc định 0:** đánh mất nguyên nhân và làm hỏng ý nghĩa dữ liệu.

Tự đối chiếu nhanh trước khi gọi parser: **radix ∈ [2,36] → dấu (nếu có) chỉ đứng đầu → còn ít nhất một chữ số → mọi chữ số thuộc radix → giá trị nằm trong miền kiểu đích**. Sau đó mới quyết định số nguyên đang là signed, unsigned hay cần `BigInteger`. Chuỗi đầu vào, cách hiển thị bit và miền giá trị là ba vấn đề khác nhau; tách chúng giúp tránh hầu hết lỗi phân tích số nguyên trong Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-004">parseUnsignedInt và parseUnsignedLong</a>

<details>
<summary>Xem chi tiết</summary>

# `parseUnsignedInt` và `parseUnsignedLong` trong Java

## 1. Mục tiêu: đọc chuỗi biểu diễn số nguyên **không dấu**

Java có primitive `int` (32 bit) và `long` (64 bit) **có dấu**, nhưng các giao thức nhị phân, dữ liệu hệ thống, checksum, mã định danh hoặc trường bit có thể mô tả cùng 32/64 bit dưới dạng **số nguyên không dấu** (*unsigned integer*).

Hai phương thức:

- `Integer.parseUnsignedInt(String s)`
- `Long.parseUnsignedLong(String s)`

giúp **phân tích một chuỗi ký tự chứa giá trị không dấu** rồi đưa **mẫu bit tương ứng** vào primitive `int` hoặc `long`. Chúng cũng có overload nhận **cơ số** (`radix`) và overload đọc một **đoạn `CharSequence`** theo chỉ số.

Điều đặc biệt: Java **không có primitive riêng `uint` hoặc `ulong`**. Vì vậy giá trị toán học không dấu ở nửa trên của miền sẽ được lưu trong `int`/`long` dưới dạng số **âm khi diễn giải có dấu**. Đây là hành vi có chủ đích, không có nghĩa quá trình parse đã thất bại.

Ví dụ:

~~~java
int bits32 = Integer.parseUnsignedInt("4294967295");
long bits64 = Long.parseUnsignedLong("18446744073709551615");

System.out.println(bits32);                    // -1
System.out.println(bits64);                    // -1
System.out.println(Integer.toUnsignedString(bits32)); // 4294967295
System.out.println(Long.toUnsignedString(bits64));    // 18446744073709551615
~~~

Đây là **mã minh họa**, chưa phải một chương trình Java độc lập. Hai giá trị âm `-1` chỉ là cách **diễn giải có dấu** của những bit toàn 1. Các hàm `toUnsignedString` hiển thị cùng bit dưới dạng giá trị không dấu như văn bản.

**Ý chính:** `parseUnsigned...` phân tích **chuỗi không dấu**, không tự tạo kiểu số nguyên không dấu mới. Để so sánh, chia, lấy dư hoặc in theo nghĩa unsigned, phải tiếp tục sử dụng các API unsigned phù hợp.

## 2. Miền biểu diễn và ý nghĩa của mẫu bit

| Primitive lưu kết quả | Độ rộng | Miền signed thường gặp | Miền giá trị unsigned mà parser chấp nhận |
|---|---:|---|---|
| `int` | 32 bit | `-2147483648` đến `2147483647` | `0` đến `4294967295` (`2^32 - 1`) |
| `long` | 64 bit | `-9223372036854775808` đến `9223372036854775807` | `0` đến `18446744073709551615` (`2^64 - 1`) |

Các ngưỡng quan trọng:

- `Integer.MAX_VALUE = 2147483647`: giá trị dương signed lớn nhất của `int`.
- `2147483648`: giá trị unsigned đầu tiên vượt miền dương signed `int`, lưu được bằng mẫu bit `0x80000000` và khi in signed sẽ là `-2147483648`.
- `4294967295`: unsigned 32 bit lớn nhất, mẫu bit `0xFFFFFFFF`, khi in signed là `-1`.
- `Long.MAX_VALUE = 9223372036854775807`.
- `9223372036854775808`: giá trị unsigned đầu tiên vượt miền dương signed `long`, mẫu bit `0x8000000000000000` và khi in signed là `Long.MIN_VALUE`.
- `18446744073709551615`: unsigned 64 bit lớn nhất, mẫu bit toàn 1, khi in signed là `-1L`.

Khi parser nhận một giá trị `u` trong miền unsigned của độ rộng `N`:

\[
\text{stored signed} =
\begin{cases}
u & 0 \le u < 2^{N-1}\\
u - 2^N & 2^{N-1} \le u < 2^N
\end{cases}
\]

Công thức dùng để giải thích hai cách diễn giải **cùng một mẫu bit**; đây không phải quy tắc “Java đã tự động trừ đi `2^N` trong giao thức”. Giá trị âm mà bạn thấy là biểu diễn signed của kết quả.

Nếu chuỗi đầu vào là `"4294967296"` hoặc `"18446744073709551616"`, nó **vượt miền unsigned** tương ứng và parser ném `NumberFormatException`. Parser **không lấy phần dư modulo `2^N` để im lặng nhận chuỗi vượt miền**.

## 3. Các overload và kiểu trả về

### `Integer.parseUnsignedInt`

~~~java
static int Integer.parseUnsignedInt(String s)
static int Integer.parseUnsignedInt(String s, int radix)
static int Integer.parseUnsignedInt(
        CharSequence s, int beginIndex, int endIndex, int radix)
~~~

Đây là **chữ ký API rút gọn** để đọc, không phải các khai báo bạn cần tự viết trong Java.

- Không có tham số `radix`: sử dụng **cơ số 10**.
- Có `radix`: diễn giải chữ số trong **cơ số chỉ định**.
- Có `CharSequence` và chỉ số: chỉ phân tích đoạn **`[beginIndex, endIndex)`**, tức lấy `beginIndex` nhưng **không lấy** ký tự ở `endIndex`.
- Kết quả là primitive **`int`**, không phải `long` hoặc đối tượng unsigned riêng.

### `Long.parseUnsignedLong`

~~~java
static long Long.parseUnsignedLong(String s)
static long Long.parseUnsignedLong(String s, int radix)
static long Long.parseUnsignedLong(
        CharSequence s, int beginIndex, int endIndex, int radix)
~~~

Tương tự, kết quả là primitive **`long`** với mẫu 64 bit tương ứng.

Hai overload `CharSequence` có trong các JDK hiện đại và **sử dụng được với Java 17+**. Chúng hữu ích khi bạn cần trích số từ bản ghi chứa nhiều trường mà không muốn tạo `substring` trung gian chỉ để gọi parser. Dữ liệu nguồn vẫn phải hợp lệ trong phạm vi chỉ số; nếu `CharSequence` bị sửa đồng thời khi parse, không nên giả định hành vi ổn định.

### API đối chiếu

| Phương thức | Miền chuỗi chấp nhận | Trả về |
|---|---|---|
| `Integer.parseInt` | Số nguyên signed 32 bit | `int` |
| `Integer.parseUnsignedInt` | Số nguyên unsigned 32 bit | `int` |
| `Long.parseLong` | Số nguyên signed 64 bit | `long` |
| `Long.parseUnsignedLong` | Số nguyên unsigned 64 bit | `long` |

Một chuỗi giá trị 4.000.000.000 là hợp lệ cho `Integer.parseUnsignedInt` nhưng không hợp lệ cho `Integer.parseInt`, vì vượt `Integer.MAX_VALUE`. **Cùng kiểu trả về không có nghĩa hai parser kiểm tra cùng miền đầu vào.**

## 4. Ví dụ Java 17+: biên signed và unsigned

Tạo tệp `UnsignedParsingBasicsDemo.java`:

~~~java
public class UnsignedParsingBasicsDemo {
    public static void main(String[] args) {
        int intMaxBits = Integer.parseUnsignedInt("4294967295");
        int intHighBits = Integer.parseUnsignedInt("2147483648");
        long longMaxBits =
                Long.parseUnsignedLong("18446744073709551615");
        long longHighBits =
                Long.parseUnsignedLong("9223372036854775808");

        System.out.println("intMaxSigned=" + intMaxBits);
        System.out.println("intMaxUnsigned="
                + Integer.toUnsignedString(intMaxBits));
        System.out.println("intHighSigned=" + intHighBits);
        System.out.println("intHighUnsigned="
                + Integer.toUnsignedString(intHighBits));

        System.out.println("longMaxSigned=" + longMaxBits);
        System.out.println("longMaxUnsigned="
                + Long.toUnsignedString(longMaxBits));
        System.out.println("longHighSigned=" + longHighBits);
        System.out.println("longHighUnsigned="
                + Long.toUnsignedString(longHighBits));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac UnsignedParsingBasicsDemo.java
java UnsignedParsingBasicsDemo
~~~

Đầu ra:

~~~text
intMaxSigned=-1
intMaxUnsigned=4294967295
intHighSigned=-2147483648
intHighUnsigned=2147483648
longMaxSigned=-1
longMaxUnsigned=18446744073709551615
longHighSigned=-9223372036854775808
longHighUnsigned=9223372036854775808
~~~

**Giải thích:** cùng một biến `intMaxBits` có thể được hiển thị là `-1` hoặc `4294967295`, tùy chọn cách diễn giải signed/unsigned khi chuyển thành văn bản. Không có thao tác nào “sửa lại” bit trong biến: `Integer.toUnsignedString` chỉ dùng **quy tắc định dạng không dấu**.

## 5. Cơ số từ 2 đến 36 và các ký tự chữ số

Khi chỉ định `radix`, Java hỗ trợ các cơ số hợp lệ từ `Character.MIN_RADIX` (2) đến `Character.MAX_RADIX` (36). Những ký tự `0..9` được dùng cho các giá trị 0..9; ở cơ số lớn hơn 10, `A..Z` hoặc `a..z` có thể biểu diễn giá trị từ 10 đến 35 theo các quy tắc phân tích chữ số của Java.

Một vài ví dụ:

~~~java
int binary = Integer.parseUnsignedInt("1010", 2);  // 10
int hex = Integer.parseUnsignedInt("ff", 16);      // 255
int base36 = Integer.parseUnsignedInt("Z", 36);    // 35
long maxHex = Long.parseUnsignedLong(
        "FFFFFFFFFFFFFFFF", 16); // mẫu bit 64 bit toàn 1, signed -1
~~~

Đây là đoạn minh họa. Khi in dưới dạng unsigned:

~~~java
Integer.toUnsignedString(hex, 16);    // "ff"
Long.toUnsignedString(maxHex, 16);   // "ffffffffffffffff"
~~~

Cơ số `10` là mặc định cho overload chỉ có `String`. **Không nhầm radix với tiền tố văn bản**: `parseUnsignedInt("0xFF", 16)` không tự bỏ `0x`. Nếu chuỗi giao thức có tiền tố `0x`, bạn phải kiểm tra và bỏ tiền tố trước, rồi parse phần chữ số ở cơ số 16.

Tương tự, `0b` không tự được nhận diện. `Integer.parseUnsignedInt("0007")` trong cơ số 10 trả 7; các số 0 đứng đầu **không tự chuyển chuỗi thành cơ số 8**.

### Chữ số Unicode và ràng buộc giao thức

Parser sử dụng quy tắc nhận diện chữ số theo Java/Unicode; không nên mặc định tất cả đầu vào hợp lệ **chỉ gồm ASCII** trong mọi cơ số. Nếu giao thức mạng hoặc định dạng bảo mật đòi đúng dãy ASCII `0-9` hoặc `0-9A-Fa-f`, phải **kiểm tra tập ký tự được phép** trước khi parse, không chỉ dựa vào `NumberFormatException`.

Điều này khác với việc cú pháp **literal Java trong mã nguồn** chấp nhận những dạng như `0xFF`, `0b1010` hoặc dấu gạch dưới `1_000`. Parser đang xử lý **chuỗi runtime**, không phân tích literal Java.

## 6. Đầu vào hợp lệ, dấu cộng và những trường hợp phải từ chối

Với parser unsigned, chuỗi có thể có **dấu cộng `+` đứng đầu** và các chữ số hợp lệ, nhưng **không chấp nhận dấu trừ `-` như giá trị âm**.

Một số ví dụ cơ số 10:

| Chuỗi `String` | `parseUnsignedInt` | Lý do |
|---|---|---|
| `"0"` | `0` | Cận dưới |
| `"42"` | `42` | Số dương trong miền |
| `"+42"` | `42` | Dấu `+` đầu hợp lệ |
| `"0007"` | `7` | Các số 0 đầu |
| `"4294967295"` | `-1` khi in signed | Sát cận trên unsigned 32 bit |
| `"4294967296"` | Ném `NumberFormatException` | Vượt miền unsigned 32 bit |
| `"-1"` | Ném `NumberFormatException` | Dấu trừ không được chấp nhận |
| `" 42"` | Ném `NumberFormatException` | Không tự trim khoảng trắng |
| `"4_2"` | Ném `NumberFormatException` | Dấu gạch dưới trong chuỗi runtime không hợp lệ |
| `"0xFF"` ở radix 10 | Ném `NumberFormatException` | Không tự hiểu tiền tố cơ số |

Với `Long.parseUnsignedLong`, chuỗi `"18446744073709551615"` là hợp lệ, còn `"18446744073709551616"` vượt cận trên và ném `NumberFormatException`.

**Phân biệt lỗi dữ liệu với lỗi chương trình:**

- `NumberFormatException` biểu thị chuỗi không phân tích được thành giá trị hợp lệ trong miền và cơ số chỉ định.
- Cơ số ngoài miền 2..36 làm thao tác không hợp lệ; đừng gửi radix từ người dùng vào API mà không kiểm tra.
- Với overload đoạn `CharSequence`, chỉ số ngoài biên hoặc đoạn không hợp lệ là lỗi của phạm vi đọc, cần kiểm soát riêng.
- Đừng dựa vào **nội dung thông điệp exception** làm giao thức xử lý lỗi: các phiên bản JDK có thể mô tả lỗi khác nhau.

Nên quyết định rõ liệu ứng dụng có cho phép khoảng trắng đầu/cuối, dấu cộng, số 0 thừa, chữ cái hex viết hoa/thường hoặc các dạng chữ số Unicode. Parser không thay thế **quy tắc định dạng dữ liệu của giao thức**.

## 7. Ví dụ Java 17+: bắt lỗi parse và xác thực miền

Tạo tệp `UnsignedInputValidationDemo.java`:

~~~java
public class UnsignedInputValidationDemo {
    static void checkInt(String source) {
        try {
            int bits = Integer.parseUnsignedInt(source);
            System.out.println("int[" + source + "]="
                    + Integer.toUnsignedString(bits));
        } catch (NumberFormatException ex) {
            System.out.println("int[" + source + "]=INVALID");
        }
    }

    static void checkLong(String source) {
        try {
            long bits = Long.parseUnsignedLong(source);
            System.out.println("long[" + source + "]="
                    + Long.toUnsignedString(bits));
        } catch (NumberFormatException ex) {
            System.out.println("long[" + source + "]=INVALID");
        }
    }

    public static void main(String[] args) {
        checkInt("4294967295");
        checkInt("4294967296");
        checkInt("-1");
        checkInt("+42");
        checkInt(" 42");
        checkInt("4_2");
        checkInt("0xFF");

        checkLong("18446744073709551615");
        checkLong("18446744073709551616");
        checkLong("-1");
    }
}
~~~

~~~bash
javac UnsignedInputValidationDemo.java
java UnsignedInputValidationDemo
~~~

Đầu ra:

~~~text
int[4294967295]=4294967295
int[4294967296]=INVALID
int[-1]=INVALID
int[+42]=42
int[ 42]=INVALID
int[4_2]=INVALID
int[0xFF]=INVALID
long[18446744073709551615]=18446744073709551615
long[18446744073709551616]=INVALID
long[-1]=INVALID
~~~

Ví dụ bắt đúng `NumberFormatException` rồi hiển thị **giá trị unsigned** hoặc trạng thái `INVALID`. Ứng dụng thực tế nên cung cấp lỗi có ngữ cảnh như trường nào sai, miền nào cho phép, và có thể hạn chế độ dài/tập ký tự trước khi parse để tránh đầu vào không phù hợp với giao thức.

Không nên nuốt mọi exception bằng `catch (Exception)` và trả `0`: điều đó trộn lẫn số 0 hợp lệ với chuỗi hỏng, che khuất lỗi và khiến khó chẩn đoán.

## 8. Dùng overload `CharSequence` cho một phần chuỗi

Từ Java 9, các overload sau cho phép parse trực tiếp **một khoảng chỉ số** của chuỗi:

~~~java
Integer.parseUnsignedInt(text, beginIndex, endIndex, radix)
Long.parseUnsignedLong(text, beginIndex, endIndex, radix)
~~~

Dữ liệu được xem là dãy ký tự `text[beginIndex .. endIndex-1]`. Ví dụ, một bản ghi lưu hex 16 bit trong chuỗi `"id=FFFF;"` có phần số bắt đầu từ chỉ số 3 và kết thúc **trước** chỉ số 7.

Tạo tệp `UnsignedRadixSliceDemo.java`:

~~~java
public class UnsignedRadixSliceDemo {
    public static void main(String[] args) {
        int binary = Integer.parseUnsignedInt("1010", 2);
        int hex = Integer.parseUnsignedInt("ff", 16);
        int base36 = Integer.parseUnsignedInt("Z", 36);

        String record = "id=FFFF;";
        int sliced = Integer.parseUnsignedInt(record, 3, 7, 16);

        long longHex =
                Long.parseUnsignedLong("FFFFFFFFFFFFFFFF", 16);

        System.out.println("binary=" + binary);
        System.out.println("hex=" + hex);
        System.out.println("base36=" + base36);
        System.out.println("slice=" + sliced);
        System.out.println("hexBack="
                + Integer.toUnsignedString(hex, 16));
        System.out.println("longHex="
                + Long.toUnsignedString(longHex, 16));
    }
}
~~~

~~~bash
javac UnsignedRadixSliceDemo.java
java UnsignedRadixSliceDemo
~~~

Đầu ra:

~~~text
binary=10
hex=255
base36=35
slice=65535
hexBack=ff
longHex=ffffffffffffffff
~~~

Cần **tự xác minh ranh giới trường** `beginIndex`/`endIndex` trước khi dùng với bản ghi biến độ dài. Nếu chuỗi có dấu phân cách, không lấy vị trí cố định khi định dạng có thể thay đổi. Việc dùng overload đoạn giúp tránh tạo thêm `substring` trong một số tình huống, nhưng **không bảo đảm** nhanh hơn cho mọi đầu vào/JVM; đo đạc nếu hiệu năng là yêu cầu thực sự.

## 9. Parse unsigned không đồng nghĩa toán tử số học thành unsigned

Sau khi parse, biến vẫn là `int` hoặc `long` signed. Vì vậy:

~~~java
int a = Integer.parseUnsignedInt("4294967295");
int b = 2;

System.out.println(a);           // -1
System.out.println(a > b);       // false: phép so sánh signed!
System.out.println(a / b);       // 0: phép chia signed (-1 / 2)
~~~

Đây là đoạn minh họa. Nếu mong ý nghĩa “4.294.967.295 lớn hơn 2”, `a > b` không phù hợp. Java cung cấp các API unsigned:

| Mục tiêu | Dạng 32 bit | Dạng 64 bit |
|---|---|---|
| So sánh | `Integer.compareUnsigned(a,b)` | `Long.compareUnsigned(a,b)` |
| Chia | `Integer.divideUnsigned(a,b)` | `Long.divideUnsigned(a,b)` |
| Lấy dư | `Integer.remainderUnsigned(a,b)` | `Long.remainderUnsigned(a,b)` |
| Hiển thị thập phân | `Integer.toUnsignedString(a)` | `Long.toUnsignedString(a)` |
| Hiển thị cơ số khác | `Integer.toUnsignedString(a,radix)` | `Long.toUnsignedString(a,radix)` |

Với mẫu 32 bit, `Integer.toUnsignedLong(a)` chuyển **giá trị unsigned 32 bit** thành `long` không âm, vì toàn bộ khoảng 0..4294967295 vừa trong `long`.

Với mẫu 64 bit, **không thể lưu tất cả các giá trị unsigned 64 bit dưới dạng số dương trong primitive `long`**. Nếu bài toán cần độ lớn dương chính xác của những số vượt `Long.MAX_VALUE`, dùng `BigInteger` hoặc biểu diễn chuỗi unsigned.

### Ví dụ Java 17+: so sánh, chia và lấy dư không dấu

Tạo tệp `UnsignedArithmeticDemo.java`:

~~~java
public class UnsignedArithmeticDemo {
    public static void main(String[] args) {
        int a = Integer.parseUnsignedInt("4294967295");
        int two = 2;

        System.out.println("signedValue=" + a);
        System.out.println("unsignedValue="
                + Integer.toUnsignedString(a));
        System.out.println("signedCompare=" + (a > two));
        System.out.println("unsignedCompare="
                + (Integer.compareUnsigned(a, two) > 0));
        System.out.println("unsignedDivision="
                + Integer.divideUnsigned(a, two));
        System.out.println("unsignedRemainder="
                + Integer.remainderUnsigned(a, two));
        System.out.println("widenedUnsigned="
                + Integer.toUnsignedLong(a));

        long big = Long.parseUnsignedLong("18446744073709551615");
        System.out.println("bigUnsigned="
                + Long.toUnsignedString(big));
        System.out.println("bigCompare="
                + (Long.compareUnsigned(big, 1L) > 0));
        System.out.println("bigDivision="
                + Long.toUnsignedString(Long.divideUnsigned(big, 2L)));
        System.out.println("bigRemainder="
                + Long.toUnsignedString(Long.remainderUnsigned(big, 2L)));
    }
}
~~~

~~~bash
javac UnsignedArithmeticDemo.java
java UnsignedArithmeticDemo
~~~

Đầu ra:

~~~text
signedValue=-1
unsignedValue=4294967295
signedCompare=false
unsignedCompare=true
unsignedDivision=2147483647
unsignedRemainder=1
widenedUnsigned=4294967295
bigUnsigned=18446744073709551615
bigCompare=true
bigDivision=9223372036854775807
bigRemainder=1
~~~

Chỉ có **cách diễn giải mẫu bit** thay đổi, không phải kiểu primitive. Chú ý `Integer.divideUnsigned` trả **`int`** và `Long.divideUnsigned` trả **`long`**: nếu thương unsigned lại vượt miền signed, in trực tiếp bằng `println` vẫn có thể trông âm; nên dùng `toUnsignedString` nếu cần hiển thị đúng.

**Cộng, trừ, nhân trong `int`/`long`** vẫn tính theo độ rộng cố định modulo `2^32`/`2^64` khi overflow theo cách Java quy định. Parse unsigned không ngăn wraparound. Nếu cần kiểm tra miền unsigned mà không được cuốn vòng, phải xây dựng logic kiểm tra riêng hoặc chọn kiểu số rộng đủ.

## 10. `BigInteger` khi cần giá trị toán học chính xác ngoài miền signed

Với unsigned 32 bit, cách dễ hiểu để có giá trị **không âm trong primitive** là:

~~~java
int bits = Integer.parseUnsignedInt("4294967295");
long unsignedMagnitude = Integer.toUnsignedLong(bits); // 4294967295
~~~

Với unsigned 64 bit, hãy dùng `BigInteger` nếu chương trình cần tính **độ lớn toán học chính xác** của 18.446.744.073.709.551.615:

~~~java
String text = "18446744073709551615";
long bits = Long.parseUnsignedLong(text);
java.math.BigInteger magnitude =
        new java.math.BigInteger(Long.toUnsignedString(bits));
~~~

Đây là đoạn minh họa. `new BigInteger(text)` cũng phân tích được chuỗi thập phân unsigned lớn đó vì `BigInteger` không giới hạn ở 64 bit. Nhưng `Long.parseUnsignedLong` vẫn hữu ích nếu mục tiêu trước hết là giữ **64 bit** để trao đổi trong cấu trúc/protocol Java.

### Ví dụ Java 17+: đối chiếu giá trị chính xác và wraparound

Tạo tệp `UnsignedBigIntegerDemo.java`:

~~~java
import java.math.BigInteger;

public class UnsignedBigIntegerDemo {
    public static void main(String[] args) {
        String maxUnsigned = "18446744073709551615";

        long bits = Long.parseUnsignedLong(maxUnsigned);
        BigInteger exact = new BigInteger(maxUnsigned);
        BigInteger recovered =
                new BigInteger(Long.toUnsignedString(bits));

        System.out.println("storedSigned=" + bits);
        System.out.println("recovered=" + recovered);
        System.out.println("matches=" + exact.equals(recovered));
        System.out.println("exactPlusOne="
                + exact.add(BigInteger.ONE));
        System.out.println("longPlusOneUnsigned="
                + Long.toUnsignedString(bits + 1L));
    }
}
~~~

~~~bash
javac UnsignedBigIntegerDemo.java
java UnsignedBigIntegerDemo
~~~

Đầu ra:

~~~text
storedSigned=-1
recovered=18446744073709551615
matches=true
exactPlusOne=18446744073709551616
longPlusOneUnsigned=0
~~~

Dòng `longPlusOneUnsigned=0` là **wraparound**: mẫu bit 64 bit toàn 1 cộng 1 trở thành 64 bit toàn 0. `BigInteger` tiếp tục biểu diễn kết quả tăng thêm 1 chính xác. Lựa chọn nào đúng tùy giao thức: checksum/bit field có thể cố ý làm modulo `2^64`, trong khi tính tiền/số lượng/bản ghi phải có chính sách không làm mất giá trị.

## 11. Chuyển đổi qua lại và round-trip đúng nghĩa

Nếu một trường được định nghĩa **unsigned 32 bit** hoặc **unsigned 64 bit**, hãy thiết kế luồng xử lý rõ ràng:

1. **Đầu vào từ chuỗi:** parse bằng `Integer.parseUnsignedInt` / `Long.parseUnsignedLong` với radix đã định.
2. **Lưu trữ:** giữ mẫu bit trong `int` / `long`.
3. **Tính toán:** dùng các API signed/unsigned theo đúng ngữ nghĩa mong muốn.
4. **Xuất văn bản:** dùng `Integer.toUnsignedString` / `Long.toUnsignedString` với radix mong muốn.
5. **Kiểm tra:** round-trip chuỗi → mẫu bit → chuỗi → mẫu bit có giữ nguyên giá trị/số bit hay không.

Ví dụ:

~~~java
int bits = Integer.parseUnsignedInt("FFFFFFFF", 16);
String out = Integer.toUnsignedString(bits, 16); // "ffffffff"
int parsedAgain = Integer.parseUnsignedInt(out, 16);
boolean sameBits = bits == parsedAgain; // true
~~~

Round-trip ở đây giữ giá trị **bit**, nhưng **không giữ nguyên kiểu chữ hoa/thường của chuỗi ban đầu**. Nếu giao thức yêu cầu hex uppercase hoặc đệm đúng 8/16 chữ số, hãy định dạng riêng; `toUnsignedString` không tự chèn số 0 đứng đầu và thường xuất chữ cái thường theo radix.

**Kiểm thử biên bắt buộc:** `0`, `1`, signed max, unsigned min nằm trên signed max, unsigned max, chuỗi vượt cận trên đúng 1, các cơ số 2/10/16, prefix, leading plus, khoảng trắng, ký tự không hợp lệ.

## 12. Lỗi phổ biến và cách tránh

| Nhầm lẫn | Sự thật |
|---|---|
| “`parseUnsignedInt` trả về kiểu `uint`.” | Java không có primitive `uint`; kết quả vẫn là `int`. |
| “Chuỗi `4294967295` parse ra `-1` là bị mất dữ liệu.” | Không: mẫu 32 bit đúng, chỉ đang hiển thị signed. |
| “`parseUnsignedLong` trả về `BigInteger` khi quá `Long.MAX_VALUE`.” | Không: trả `long` giữ mẫu 64 bit; cần `BigInteger` nếu muốn độ lớn dương rộng hơn signed. |
| “`parseUnsignedInt("4294967296")` sẽ thành 0.” | Không: parser từ chối giá trị vượt miền unsigned bằng `NumberFormatException`. |
| “Unsigned parser tự cho phép dấu trừ vì kết quả có thể âm.” | Không: `"-1"` bị từ chối; số âm khi in là cách hiểu signed của mẫu bit. |
| “Có thể đưa `"0xFF"` vào radix 16 trực tiếp.” | Không: phải xử lý tiền tố `0x` trước khi parse. |
| “Gạch dưới trong literal Java cũng dùng được trong String parse.” | Không: `"1_000"` không phải chuỗi chữ số hợp lệ của parser. |
| “`parseUnsignedInt` sẽ tự trim khoảng trắng.” | Không: cần chính sách normalize/trim ngoài parser nếu cho phép. |
| “Dùng toán tử `>` sau khi parse là so sánh unsigned.” | Không: `int`/`long` vẫn so sánh signed; dùng `compareUnsigned`. |
| “Dùng `/` là chia không dấu vì số được parse unsigned.” | Không: dùng `divideUnsigned` khi cần nghĩa unsigned. |
| “Unsigned parser tự ngăn overflow khi cộng.” | Không: nó chỉ kiểm tra giới hạn **lúc parse**; các phép tính tiếp theo cần chính sách riêng. |
| “`Integer.toUnsignedString` phải trả chuỗi y hệt đầu vào.” | Không: giá trị/bit giữ, nhưng format có thể thay đổi, ví dụ hex uppercase thành lowercase. |

## 13. Checklist tích hợp vào ứng dụng thực tế

1. **Xác định độ rộng và ngữ nghĩa:** trường là signed hay unsigned, 32 hay 64 bit?
2. **Xác định radix:** 10, 16 hoặc cơ số khác; chuỗi có prefix không?
3. **Quy định dạng đầu vào:** có cho phép dấu `+`, leading zero, khoảng trắng, chữ số Unicode không? Nếu không, kiểm tra trước.
4. **Dùng đúng parser:** `Integer.parseUnsignedInt` hoặc `Long.parseUnsignedLong`; đừng dùng `parseInt`/`parseLong` nếu giá trị giao thức có thể vượt signed max.
5. **Bắt `NumberFormatException` tại ranh giới đầu vào:** biến thành lỗi cụ thể của trường dữ liệu, không âm thầm thay 0.
6. **Lưu giữ rõ nghĩa unsigned:** tên biến như `unsignedBits` hoặc mô tả cấu trúc; người đọc không được nhầm `-1` signed với lỗi.
7. **Dùng so sánh/chia/lấy dư unsigned khi cần:** `compareUnsigned`, `divideUnsigned`, `remainderUnsigned`.
8. **Dùng hàm xuất chuỗi phù hợp:** `toUnsignedString` thay vì in raw signed khi hiển thị số không dấu.
9. **Kiểm soát overflow phép tính về sau:** sử dụng `BigInteger` khi không được mất độ lớn toán học; hoặc document modulo bit-width nếu đó là chủ đích giao thức.
10. **Thử biên, radix và round-trip:** tối thiểu có unsigned max, unsigned max + 1, giá trị với bit dấu bật, ký tự sai và mọi định dạng được phép.

## 14. Bài tập tự kiểm tra

1. Vì sao `Integer.parseUnsignedInt("4294967295")` trả `int` có thể in là `-1` nhưng vẫn thành công?
2. Có thể dùng `Integer.parseInt("4294967295")` thay thế không? Vì sao?
3. `Long.parseUnsignedLong("18446744073709551615")` trả kiểu nào, và khi in signed cho số nào?
4. Chuỗi `"4294967296"` có được parser unsigned 32 bit tự lấy modulo không?
5. `Integer.parseUnsignedInt("FF", 16)` trả bao nhiêu? Chuỗi `"0xFF"` với cùng radix có được parse trực tiếp không?
6. `Integer.parseUnsignedInt("+42")` và `Integer.parseUnsignedInt("-42")` khác nhau thế nào?
7. Vì sao `Integer.parseUnsignedInt("4294967295") > 0` cho `false` dù giá trị unsigned là số dương lớn?
8. Nếu cần chia unsigned 32-bit max cho 2, nên gọi API nào?
9. Nếu cần cộng 1 vào unsigned 64-bit max **mà vẫn giữ kết quả toán học chính xác**, chọn kiểu nào?
10. Vì sao `toUnsignedString(bits, 16)` có thể không tái tạo nguyên vẹn cách viết hoa/thường của chuỗi hex đầu vào?

**Gợi ý đáp án:** (1) Mẫu 32 bit toàn 1 là unsigned max nhưng signed `-1`. (2) Không, vượt `Integer.MAX_VALUE`. (3) Primitive `long`, in signed `-1`. (4) Không, ném `NumberFormatException`. (5) 255; `"0xFF"` cần bỏ prefix. (6) `+42` hợp lệ, `-42` không hợp lệ. (7) Toán tử `>` trên `int` là signed; dùng `Integer.compareUnsigned`. (8) `Integer.divideUnsigned(bits, 2)`. (9) `BigInteger`. (10) Hàm trả biểu diễn số normalized theo radix, không giữ định dạng văn bản đầu vào.

**Ghi nhớ:** `parseUnsignedInt` và `parseUnsignedLong` kiểm tra **miền chuỗi không dấu** nhưng vẫn trả về primitive có dấu `int`/`long` **giữ nguyên mẫu bit**. Khi muốn hiển thị đúng độ lớn unsigned, dùng `toUnsignedString`; khi cần so sánh, chia hoặc lấy dư unsigned, dùng các API tương ứng. Nếu phải tính toán chính xác vượt 64 bit không dấu, dùng `BigInteger`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-005">decode và các tiền tố cơ số</a>

<details>
<summary>Xem chi tiết</summary>

# decode và các tiền tố cơ số trong Java

## 1. Khi chuỗi số có tiền tố, Java đọc theo cơ số nào?

Java cho phép viết số nguyên theo hệ thập phân, nhị phân, bát phân và thập lục phân. Nhưng có sự khác biệt quan trọng giữa **literal viết trong mã Java** và **chuỗi ký tự được phân tích khi chương trình đang chạy**.

Ví dụ, trong mã nguồn:

```java
int decimal = 42;
int binary = 0b101010;
int octal = 052;
int hex = 0x2A;
// Cả bốn biến đều có giá trị int 42.
```

Để đọc một chuỗi như `"0x2A"` hoặc `"052"` tại runtime, Java có phương thức `Integer.decode(String)`. Nó nhận diện **một số tiền tố cơ số** và chuyển thành số nguyên có dấu.

```java
int a = Integer.decode("42");   // 42, thập phân
int b = Integer.decode("052");  // 42, bát phân: 5*8+2
int c = Integer.decode("0x2A"); // 42, thập lục phân
int d = Integer.decode("#2A");  // 42, cú pháp hex khác
```

**Cảnh báo đầu tiên:** `Integer.decode("010")` cho **8**, không phải 10, vì chuỗi bắt đầu bằng `0` nên được hiểu là bát phân. Trong khi đó, `Integer.parseInt("010")` cho **10** vì `parseInt(String)` mặc định là thập phân.

**Cảnh báo thứ hai:** mặc dù literal `0b101010` hợp lệ trong mã Java, `Integer.decode("0b101010")` **không hỗ trợ** tiền tố nhị phân `0b`. Muốn đọc chuỗi nhị phân, cần dùng `Integer.parseInt("101010", 2)` hoặc một hàm riêng có xử lý tiền tố.

Bài học tập trung vào **cơ chế `decode`, tiền tố hợp lệ, sự khác biệt với `parseInt`, giới hạn kiểu số và cách xử lý đầu vào an toàn**.

## 2. Những phương thức `decode` sẵn có

Các wrapper số nguyên có phương thức tĩnh `decode(String)`:

| Phương thức | Trả về | Miền giá trị có dấu |
|---|---|---|
| `Byte.decode(text)` | `Byte` | -128 đến 127 |
| `Short.decode(text)` | `Short` | -32_768 đến 32_767 |
| `Integer.decode(text)` | `Integer` | -2_147_483_648 đến 2_147_483_647 |
| `Long.decode(text)` | `Long` | -9_223_372_036_854_775_808 đến 9_223_372_036_854_775_807 |

Đây là **các phương thức `static`**: gọi bằng tên lớp như `Integer.decode("0xFF")`, không cần tạo một đối tượng `Integer` trước.

Giá trị trả về là **wrapper**, không phải primitive; tuy nhiên Java có thể tự **unbox** khi bạn gán kết quả vào biến primitive:

```java
Integer wrapper = Integer.decode("0x2A"); // Integer, giá trị 42
int primitive = Integer.decode("0x2A");   // unboxing -> int 42
long large = Long.decode("0x100000000"); // 4294967296L
```

`decode` **không hỗ trợ `Float` hoặc `Double`** theo cùng ý nghĩa phân tích chuỗi nguyên với tiền tố cơ số. Nếu cần đọc số thực, dùng API số thực phù hợp như `Double.parseDouble`; nếu cần phân tích một chuỗi giá trị số nguyên có tiền tố rồi chuyển thành `double`, hai bước ấy có ngữ nghĩa khác nhau và phải được quyết định rõ.

## 3. Bảng tiền tố được `decode` hỗ trợ

`Integer.decode` và `Long.decode` tuân theo các hình thức thông dụng:

| Dạng chuỗi | Cơ số | Ví dụ | Kết quả thập phân |
|---|---:|---|---:|
| Không có tiền tố (và không bắt đầu bằng số 0 theo quy tắc bát phân) | 10 | `"42"` | 42 |
| `0x` | 16 | `"0x2A"` | 42 |
| `0X` | 16 | `"0X2a"` | 42 |
| `#` | 16 | `"#2A"` | 42 |
| Bắt đầu bằng `0`, sau đó là chữ số | 8 | `"052"` | 42 |
| Tiền tố nhị phân `0b`, `0B` | Không được hỗ trợ | `"0b101010"` | `NumberFormatException` |

Tại sao `"052"` là 42?

\[
5\times 8^1 + 2\times 8^0 = 40+2=42
\]

Tại sao `"0x2A"` là 42?

\[
2\times 16^1 + 10\times 16^0 = 32+10=42
\]

Chữ cái `A` đến `F` (hoặc chữ thường `a` đến `f`) biểu thị các chữ số hex 10 đến 15. Ký tự `#` trong `"#2A"` cũng là **tiền tố hex**, không phải một toán tử số học mới.

**Số 0 đứng đầu có ý nghĩa cú pháp.** Ví dụ:

- `Integer.decode("0")` → 0.
- `Integer.decode("00")` → 0.
- `Integer.decode("010")` → 8.
- `Integer.decode("077")` → 63.
- `Integer.decode("08")` → lỗi, vì chữ số 8 không hợp lệ trong hệ bát phân.
- `Integer.decode("09")` → lỗi tương tự.

Vì thế, nếu một đầu vào thực sự là **số thập phân** và có thể có số 0 đứng đầu (ví dụ `"008"`), đừng dùng `decode` một cách mặc định.

## 4. Dấu cộng, dấu trừ và vị trí hợp lệ của chúng

`decode` hỗ trợ **dấu `+` hoặc `-` đứng trước tiền tố**, nếu có:

```java
int a = Integer.decode("-42");      // -42
int b = Integer.decode("+42");      // 42
int c = Integer.decode("-052");     // -42 (bát phân)
int d = Integer.decode("-0x2A");    // -42
int e = Integer.decode("+#2A");     // 42
int f = Integer.decode("-0X2a");    // -42
```

Quy tắc cốt lõi: **dấu đứng đầu toàn bộ chuỗi**, rồi mới tới phần `0x`, `0X`, `#` hoặc `0`.

Các chuỗi sau không hợp lệ:

```java
// Integer.decode("0x-2A"); // sai: dấu ở sau tiền tố
// Integer.decode("#-2A");  // sai
// Integer.decode("--42");  // sai
// Integer.decode("++42");  // sai
```

Dấu chỉ được xuất hiện một lần và phải đứng ở vị trí đầu. Việc tách dấu và tiền tố theo đúng thứ tự giúp các chuỗi âm dạng hex/bát phân có nghĩa nhất quán với các giá trị âm ở hệ thập phân.

### Giá trị âm thấp nhất của `int` vẫn đọc được

`Integer.MIN_VALUE` bằng -2_147_483_648. Trong hex, độ lớn tương ứng là `0x80000000`. Chuỗi có dấu âm:

```java
int min = Integer.decode("-0x80000000");
// -2147483648
```

là hợp lệ. Nhưng:

```java
// Integer.decode("0x80000000"); // NumberFormatException
```

không hợp lệ vì nó biểu thị **giá trị dương** 2_147_483_648, vượt `Integer.MAX_VALUE`. `decode` đọc **số nguyên có dấu**, không tự coi mọi mẫu 32 bit là một giá trị unsigned.

## 5. So sánh `decode`, `parseInt` và `parseUnsignedInt`

Đây là phần quan trọng nhất khi chọn API.

| Mục đích | API phù hợp | Cách hiểu đầu vào |
|---|---|---|
| Đọc chuỗi có tiền tố `0x`, `0X`, `#` hoặc `0` bát phân | `Integer.decode(text)` | Tự quyết định 10/8/16 từ tiền tố |
| Đọc số thập phân, kể cả số 0 đầu | `Integer.parseInt(text)` | Cố định cơ số 10 |
| Đọc một chuỗi chữ số theo cơ số chỉ định | `Integer.parseInt(digits, radix)` | Cơ số do tham số `radix` quyết định |
| Đọc mẫu bit `int` như số không dấu | `Integer.parseUnsignedInt(digits, radix)` | Cơ số chỉ định và miền 0..2³²−1 |
| Đọc số nguyên signed 64 bit theo tiền tố | `Long.decode(text)` | 10/8/16 có dấu |
| Đọc số nguyên không dấu 64 bit | `Long.parseUnsignedLong(digits, radix)` | Miền 0..2⁶⁴−1, lưu trong mẫu bit `long` |

Ví dụ cùng một chuỗi:

```java
int a = Integer.decode("010"); // 8, bát phân
int b = Integer.parseInt("010"); // 10, thập phân
int c = Integer.parseInt("010", 8); // 8, cơ số chỉ định
int d = Integer.parseInt("1010", 2); // 10, nhị phân
```

Ví dụ chuỗi hex:

```java
int a = Integer.decode("0xFF"); // 255
int b = Integer.parseInt("FF", 16); // 255
// Integer.parseInt("0xFF", 16); // lỗi: parseInt không tự bỏ "0x"
```

**Nguyên tắc chọn API:** Nếu giao thức quy định input là **decimal**, dùng `parseInt` hoặc `parseLong`. Nếu giao thức quy định input **có tiền tố đa cơ số theo quy tắc `decode`**, dùng `decode`. Nếu cơ số được quy định bên ngoài, dùng overload `parseInt(text, radix)` thay vì suy luận từ số 0 đứng đầu.

## 6. Ví dụ Java 17+ đầu tiên: giải mã tất cả dạng cơ số

Tạo tệp `DecodePrefixesDemo.java`:

```java
public class DecodePrefixesDemo {
    public static void main(String[] args) {
        System.out.println("42 -> " + Integer.decode("42"));
        System.out.println("052 -> " + Integer.decode("052"));
        System.out.println("0x2A -> " + Integer.decode("0x2A"));
        System.out.println("0X2a -> " + Integer.decode("0X2a"));
        System.out.println("#2A -> " + Integer.decode("#2A"));
        System.out.println("-052 -> " + Integer.decode("-052"));
        System.out.println("-0x2A -> " + Integer.decode("-0x2A"));
        System.out.println("+0X2a -> " + Integer.decode("+0X2a"));
        System.out.println("010 via decode -> "
                + Integer.decode("010"));
        System.out.println("010 via parseInt -> "
                + Integer.parseInt("010"));
        System.out.println("binary via radix -> "
                + Integer.parseInt("101010", 2));
        System.out.println("long hex -> "
                + Long.decode("0x100000000"));
        System.out.println("short hex -> "
                + Short.decode("0x7FFF"));
        System.out.println("byte octal -> "
                + Byte.decode("0177"));
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac DecodePrefixesDemo.java
java DecodePrefixesDemo
```

Kết quả chính xác:

```text
42 -> 42
052 -> 42
0x2A -> 42
0X2a -> 42
#2A -> 42
-052 -> -42
-0x2A -> -42
+0X2a -> 42
010 via decode -> 8
010 via parseInt -> 10
binary via radix -> 42
long hex -> 4294967296
short hex -> 32767
byte octal -> 127
```

Ở ba dòng cuối, kết quả vẫn phải nằm trong miền kiểu trả về. Ví dụ `Byte.decode("0177")` đọc bát phân 177 và nhận 127 trong miền `byte` có dấu; `Byte.decode("0200")` sẽ không thành -128 bằng wraparound mà báo lỗi vì giá trị dương 128 **ngoài miền `byte`**.

## 7. `decode` không hiểu mọi cú pháp literal Java

Các literal trong **mã nguồn Java** do compiler xử lý. Chuỗi dữ liệu đưa vào `decode` được kiểm tra bằng ngữ pháp riêng của phương thức; nó không phải trình biên dịch Java thu nhỏ.

Bảng đối chiếu:

| Trong mã nguồn Java | Có hợp lệ? | Chuỗi tương ứng đưa cho `Integer.decode` |
|---|---|---|
| `0b1010` | Có: nhị phân | `"0b1010"` → lỗi |
| `0xFF` | Có: hex | `"0xFF"` → 255 |
| `077` | Có: bát phân | `"077"` → 63 |
| `1_000` | Có: dấu gạch dưới phân nhóm chữ số | `"1_000"` → lỗi |
| `123L` | Có: literal `long` | `"123L"` → lỗi |
| `0xFFL` | Có: literal `long` hex | `"0xFFL"` → lỗi |
| `10` | Có: thập phân | `"10"` → 10 |

Dấu gạch dưới `_` **được compiler cho phép trong literal theo quy tắc nhất định**, nhưng không được chấp nhận trong chuỗi phân tích bằng `Integer.decode`, `Integer.parseInt` hoặc `Long.parseLong`.

Tương tự, hậu tố `L`/`l` là cú pháp literal Java, **không phải chữ số hợp lệ** trong chuỗi cho `Long.decode`. Nên viết `Long.decode("123")` thay vì `Long.decode("123L")`.

### Cú pháp nhị phân phải xử lý riêng

Nếu đầu vào được quy định là một dãy chữ số nhị phân:

```java
int value = Integer.parseInt("101010", 2); // 42
```

Nếu đầu vào được quy định là `"0b101010"`, bạn cần kiểm tra và bỏ tiền tố trước khi parse:

```java
String text = "0b101010";
if (text.startsWith("0b") || text.startsWith("0B")) {
    int value = Integer.parseInt(text.substring(2), 2); // 42
}
```

Đây là **đoạn mã minh họa**, không phải chương trình đầy đủ. Nó chỉ chấp nhận tiền tố nhị phân ở đầu chuỗi dương; với dữ liệu có dấu như `"-0b1010"`, phải xác định quy ước dấu và thiết kế bộ phân tích tương ứng.

Đừng coi `decode` tự hỗ trợ nhị phân chỉ vì các literal Java có `0b`.

## 8. Giới hạn miền: `decode` kiểm tra số signed, không wraparound

Các phương thức `Byte.decode`, `Short.decode`, `Integer.decode` và `Long.decode` trả wrapper số nguyên **có dấu**. Giá trị ngoài miền gây `NumberFormatException`.

```java
int max = Integer.decode("2147483647");
int min = Integer.decode("-2147483648");

// Integer.decode("2147483648");  // quá lớn
// Integer.decode("-2147483649"); // quá nhỏ
// Integer.decode("0xFFFFFFFF"); // dương 4294967295, ngoài miền int
```

Với `Long`:

```java
long max = Long.decode("9223372036854775807");
long min = Long.decode("-9223372036854775808");

// Long.decode("9223372036854775808");  // ngoài miền long
```

Với `Byte` và `Short`:

```java
byte maxByte = Byte.decode("127");
byte minByte = Byte.decode("-0x80");
short maxShort = Short.decode("0x7FFF");

// Byte.decode("128");       // ngoài miền byte
// Short.decode("0x8000");   // dương 32768, ngoài miền short
```

Nếu mục tiêu là chuyển một **bit pattern** 32 bit thành `int` có thể âm, đừng dùng `Integer.decode("0xFFFFFFFF")`. Nó không nhận 4_294_967_295 như số signed hợp lệ. Thay vào đó, dùng parse không dấu theo đúng cơ số (và bỏ tiền tố `0x` trước khi parse), rồi làm việc với các bit ở kết quả.

## 9. Đọc số nguyên không dấu và giữ mẫu bit

Java `int` và `long` là các kiểu primitive có dấu, nhưng thư viện có API **đọc và hiển thị giá trị không dấu** bằng chính các mẫu bit 32/64 bit:

```java
int bits = Integer.parseUnsignedInt("FFFFFFFF", 16);
System.out.println(bits); // -1 khi in kiểu int có dấu
System.out.println(Integer.toUnsignedString(bits)); // 4294967295
```

Bit pattern `0xFFFFFFFF` có thể được biểu diễn bằng `int` -1, nhưng khi diễn giải như số không dấu, nó bằng 4_294_967_295. **Giá trị trả về vẫn có kiểu `int`**, không tạo kiểu `unsigned int`.

Tương tự:

```java
long bits = Long.parseUnsignedLong("FFFFFFFFFFFFFFFF", 16);
System.out.println(bits); // -1 khi in kiểu long có dấu
System.out.println(Long.toUnsignedString(bits)); // 18446744073709551615
```

### Chương trình hoàn chỉnh: đọc mẫu hex không dấu

Tạo tệp `UnsignedDecodeDemo.java`:

```java
public class UnsignedDecodeDemo {
    public static void main(String[] args) {
        int signedMin = Integer.decode("-0x80000000");
        System.out.println("signed min: " + signedMin);

        int unsignedBits =
                Integer.parseUnsignedInt("80000000", 16);
        System.out.println("signed view: " + unsignedBits);
        System.out.println("unsigned view: "
                + Integer.toUnsignedString(unsignedBits));
        System.out.println("as unsigned long: "
                + Integer.toUnsignedLong(unsignedBits));

        int allOnes =
                Integer.parseUnsignedInt("FFFFFFFF", 16);
        System.out.println("all bits signed: " + allOnes);
        System.out.println("all bits unsigned: "
                + Integer.toUnsignedString(allOnes));

        long longBits =
                Long.parseUnsignedLong("FFFFFFFFFFFFFFFF", 16);
        System.out.println("long signed: " + longBits);
        System.out.println("long unsigned: "
                + Long.toUnsignedString(longBits));
    }
}
```

Chạy:

```bash
javac UnsignedDecodeDemo.java
java UnsignedDecodeDemo
```

Kết quả:

```text
signed min: -2147483648
signed view: -2147483648
unsigned view: 2147483648
as unsigned long: 2147483648
all bits signed: -1
all bits unsigned: 4294967295
long signed: -1
long unsigned: 18446744073709551615
```

**Bẫy quan trọng:** `Integer.parseUnsignedInt("0xFFFFFFFF", 16)` vẫn không hợp lệ, vì `parseUnsignedInt` nhận các **chữ số theo cơ số** đã được chỉ định chứ không tự bóc tiền tố. Hãy xác minh định dạng input, bỏ `0x` khi cần, rồi parse.

## 10. `NumberFormatException` và dữ liệu không hợp lệ

`decode` có thể ném `NumberFormatException` khi gặp đầu vào sai cú pháp hoặc ngoài miền. Những ví dụ dưới đây không hợp lệ với `Integer.decode`:

| Chuỗi | Lý do |
|---|---|
| `"08"` | Bắt đầu bằng 0 nên đọc bát phân; chữ số 8 không hợp lệ |
| `"09"` | Lỗi bát phân tương tự |
| `"0b1010"` | Không hỗ trợ tiền tố `0b` |
| `"0x"` | Thiếu chữ số hex |
| `"#"` | Thiếu chữ số hex |
| `"0x-10"` | Dấu âm sai vị trí |
| `"1_000"` | Dấu `_` không hợp lệ trong chuỗi parse |
| `"42L"` | Hậu tố literal `L` không được hỗ trợ |
| `" 42"` | Có khoảng trắng ở đầu; `decode` không tự trim |
| `"42 "` | Có khoảng trắng ở cuối |
| `""` | Không có chữ số |
| `"2147483648"` | Ngoài miền `int` có dấu |

Khi nhận input không đáng tin, nên **bắt ngoại lệ tại ranh giới nhập liệu**, thay vì để lỗi propagate không kiểm soát hoặc tự ép một giá trị giả định.

### Ví dụ Java 17+: phân loại các chuỗi hợp lệ và không hợp lệ

Tạo tệp `DecodeValidationDemo.java`:

```java
public class DecodeValidationDemo {
    private static void check(String text) {
        try {
            int value = Integer.decode(text);
            System.out.println(text + " -> " + value);
        } catch (NumberFormatException e) {
            System.out.println(text + " -> invalid");
        }
    }

    public static void main(String[] args) {
        check("0x10");
        check("010");
        check("08");
        check("0b1010");
        check("0x");
        check("42L");
        check("1_000");
        check(" 42");
        check("-0x80000000");
        check("0x80000000");
    }
}
```

Biên dịch và chạy:

```bash
javac DecodeValidationDemo.java
java DecodeValidationDemo
```

Kết quả:

```text
0x10 -> 16
010 -> 8
08 -> invalid
0b1010 -> invalid
0x -> invalid
42L -> invalid
1_000 -> invalid
 42 -> invalid
-0x80000000 -> -2147483648
0x80000000 -> invalid
```

Lưu ý dòng `" 42"` chứa một **dấu cách ngay đầu chuỗi**. Vì phương thức `check` in nguyên chuỗi đó trước dấu `->`, trong output có **một dấu cách đầu dòng**. Đây là hành vi in chuỗi thực tế; nó không được tự cắt bỏ khi kiểm tra output.

## 11. Dùng `decode` an toàn theo hợp đồng đầu vào

Để tránh nhầm lẫn và hành vi khó đoán, trước khi chọn `decode` hãy xác định:

1. **Có được phép tiền tố cơ số hay không?** Nếu có, những tiền tố nào là hợp lệ?
2. **Số 0 đứng đầu là mã định danh hay giá trị số?** Ví dụ `"008"` có thể là một mã có ý nghĩa văn bản; parse thành số sẽ mất các số 0 đầu.
3. **Giá trị có dấu hay không dấu?** `decode` chỉ dành cho kiểu số signed; nếu đọc bit pattern nguyên vẹn, cần API unsigned phù hợp.
4. **Kiểu đích có đủ miền?** `Byte/Short/Integer/Long.decode` kiểm tra miền khác nhau.
5. **Có cần chấp nhận khoảng trắng hoặc format từ người dùng?** `decode` không tự trim; nếu chính sách cho phép, hãy chuẩn hóa có kiểm tra trước.
6. **Có cho phép tiền tố nhị phân?** `decode` không chấp nhận `0b`; nếu cần, viết parser riêng.
7. **Cần báo lỗi ra sao?** Bắt `NumberFormatException` tại lớp nhập liệu và trả thông báo rõ, không coi dữ liệu sai là 0 theo mặc định.

Ví dụ với đầu vào **có chủ đích cho phép khoảng trắng đầu/cuối**:

```java
static int decodeTrimmed(String input) {
    if (input == null) {
        throw new IllegalArgumentException("value is required");
    }
    String text = input.trim();
    if (text.isEmpty()) {
        throw new IllegalArgumentException("value is empty");
    }
    return Integer.decode(text);
}
```

Đây là **một phương thức minh họa**, cần đặt trong class Java. Việc dùng `trim()` là **quyết định chính sách nhập liệu**, không phải hành vi có sẵn của `decode`. Với giao thức máy đọc các chuỗi có định dạng chặt chẽ, có thể muốn từ chối khoảng trắng thay vì tự sửa.

Nếu dữ liệu là số decimal do người dùng nhập vào, một API cụ thể hơn có thể là:

```java
static int parseDecimal(String input) {
    if (input == null) {
        throw new IllegalArgumentException("value is required");
    }
    return Integer.parseInt(input.trim());
}
```

Dùng `parseInt` ở đây vì đầu vào **được định nghĩa là decimal**, không phải chuỗi tiền tố tùy ý.

## 12. Phân biệt giá trị số với mã định danh

Không phải mọi chuỗi toàn chữ số đều nên parse thành số. Các mã sau có thể **phải được giữ dưới dạng `String`**:

- Mã khách hàng: `"000123"`.
- Mã sản phẩm: `"008"`.
- Mã bưu chính hoặc mã vùng tùy quốc gia: có thể bắt đầu bằng 0.
- Mã cấu hình có độ dài cố định, ví dụ `"0016"`.
- Một chuỗi nhị phân được yêu cầu đủ 16 bit với những số 0 đứng đầu.

Nếu bạn gọi `Integer.decode("000123")`, Java **diễn giải chuỗi là số bát phân**, hoàn toàn không giữ lại mã gốc. Thậm chí `"008"` gây lỗi. Nếu trường dữ liệu là định danh, **giữ `String`** và kiểm tra độ dài/ký tự hợp lệ, không dùng `decode`.

Đây là nguyên tắc mô hình hóa dữ liệu: **dữ liệu trông giống số chưa chắc có ngữ nghĩa số học**.

## 13. `Integer.getInteger` khác `Integer.decode`

Một cặp API tên giống nhau dễ gây nhầm:

- `Integer.decode("0x10")`: **phân tích chính chuỗi đã đưa vào**, nhận 16.
- `Integer.getInteger("name")`: đọc **Java system property** có tên `name`, rồi phân tích giá trị property đó theo quy tắc cơ số thích hợp. Nó **không** có nghĩa đọc một chuỗi số nguyên trực tiếp.

Ví dụ minh họa:

```java
System.setProperty("app.port", "0x1F90");
Integer port = Integer.getInteger("app.port"); // 8080
Integer fromText = Integer.decode("0x1F90");   // 8080
```

Đoạn code này đặt trong phương thức. `Integer.getInteger` có thể trả `null` khi property không tồn tại hoặc không phân tích hợp lệ; `decode` không phải lời gọi đọc system property.

Tương tự `Long.getLong` dành cho system property, không phải một biến thể thay thế `Long.decode` để parse các chuỗi số bất kỳ.

**An toàn khi dùng property:** system property có thể do môi trường thực thi cấu hình; luôn xác định giá trị mặc định và chính sách xử lý lỗi. Không nhầm giữa **tên thuộc tính** và **chuỗi số** khi gọi API.

## 14. `BigInteger` và dữ liệu vượt giới hạn `long`

Nếu giá trị thực sự có thể lớn hơn miền `long`, `Long.decode` không phù hợp. Java có `java.math.BigInteger` để xử lý số nguyên độ lớn tùy ý.

```java
import java.math.BigInteger;

BigInteger decimal = new BigInteger("18446744073709551615");
BigInteger hex = new BigInteger("FFFFFFFFFFFFFFFF", 16);
```

Đây là **đoạn mã minh họa**, cần đặt trong class/phương thức và khai báo import thích hợp. Cả hai đối tượng có cùng giá trị toán học là 18_446_744_073_709_551_615.

Khác với `decode`, constructor `BigInteger(String, radix)` nhận **dãy chữ số ứng với cơ số đã cho** chứ không tự chấp nhận `0x`:

```java
BigInteger value = new BigInteger("FF", 16); // 255
// new BigInteger("0xFF", 16); // NumberFormatException
```

Nếu protocol quy định các chuỗi có `0x` hay `0b` và số lượng chữ số không cố định, bạn có thể xây dựng một parser **chỉ chấp nhận đúng các tiền tố đã được liệt kê** rồi gọi constructor với `radix` phù hợp. Không nên tùy tiện chấp nhận mọi ký tự giống literal Java vì nó làm tăng độ phức tạp xác thực.

Lưu ý: `BigInteger` không có các phép cộng toán tử `+` giữa hai đối tượng; sử dụng `add`, `subtract` và `multiply`.

## 15. Xây dựng parser tùy biến khi cần cả `0b`

Giả sử yêu cầu dữ liệu là:

- Chuỗi thập phân không tiền tố.
- Hex với `0x` hoặc `0X`.
- Nhị phân với `0b` hoặc `0B`.
- Không hỗ trợ bát phân bằng 0 đứng đầu.
- Cho phép dấu `+` hoặc `-` đứng trước.
- Số ngoài miền `int` là lỗi.

Đây là **một hợp đồng khác `Integer.decode`**, nên hãy xây dựng parser riêng.

Tạo file `ExplicitRadixParserDemo.java`:

```java
public class ExplicitRadixParserDemo {
    static int parseFlexibleInt(String input) {
        if (input == null || input.isEmpty()) {
            throw new NumberFormatException("empty input");
        }

        int position = 0;
        boolean negative = false;

        char first = input.charAt(0);
        if (first == '-' || first == '+') {
            negative = first == '-';
            position = 1;
        }
        if (position == input.length()) {
            throw new NumberFormatException("missing digits");
        }

        int radix = 10;
        if (input.startsWith("0b", position)
                || input.startsWith("0B", position)) {
            radix = 2;
            position += 2;
        } else if (input.startsWith("0x", position)
                || input.startsWith("0X", position)) {
            radix = 16;
            position += 2;
        }
        if (position == input.length()) {
            throw new NumberFormatException("missing digits");
        }

        // Để parseInt kiểm tra chữ số và giới hạn signed int.
        String digits = input.substring(position);
        return Integer.parseInt((negative ? "-" : "") + digits, radix);
    }

    public static void main(String[] args) {
        System.out.println(parseFlexibleInt("42"));
        System.out.println(parseFlexibleInt("010"));
        System.out.println(parseFlexibleInt("0b101010"));
        System.out.println(parseFlexibleInt("-0B101010"));
        System.out.println(parseFlexibleInt("+0x2A"));
        System.out.println(parseFlexibleInt("-0x80000000"));
    }
}
```

Chạy:

```bash
javac ExplicitRadixParserDemo.java
java ExplicitRadixParserDemo
```

Kết quả:

```text
42
10
42
-42
42
-2147483648
```

Parser trên có hành vi **cố ý khác `decode`**: `"010"` được đọc theo decimal thành 10, thay vì bát phân thành 8. Nó không tự trim, không chấp nhận dấu gạch dưới hoặc hậu tố `L`, và luôn kiểm tra miền `int` bằng `Integer.parseInt`.

### Những điểm cần mở rộng nếu dùng trong production

- Hạn chế chiều dài input để tránh tài nguyên bị tiêu tốn quá mức.
- Ràng buộc loại prefix phù hợp giao thức; nếu cần `#`, bổ sung nhánh rõ ràng.
- Quy định có cho phép dấu `+` hay số `0` đứng đầu hay không.
- Chọn kiểu `long`/`BigInteger` nếu phạm vi cần lớn hơn.
- Báo lỗi phân biệt “ký tự sai”, “thiếu chữ số” và “ngoài miền” khi API yêu cầu.
- Kiểm thử chuỗi rỗng, `null`, tiền tố không có chữ số, giá trị MIN/MAX và số dài.

Không cần viết parser tùy biến nếu `parseInt` hoặc `decode` sẵn có đã **phù hợp chính xác hợp đồng đầu vào**.

## 16. Những lỗi thường gặp và cách sửa

| Lỗi hoặc nhầm lẫn | Nguyên nhân | Cách sửa |
|---|---|---|
| `decode("010")` ra 8 thay vì 10 | Số 0 đầu kích hoạt bát phân | Dùng `parseInt("010")` khi input là decimal |
| `decode("08")` ném lỗi | 8 không hợp lệ trong hệ bát phân | Dùng `parseInt("08")` nếu muốn decimal |
| `decode("0b1010")` không hoạt động | `decode` không hỗ trợ tiền tố nhị phân | `parseInt("1010", 2)` hoặc parser riêng |
| `parseInt("0xFF",16)` lỗi | `parseInt` nhận chữ số, không bóc tiền tố | Dùng `parseInt("FF",16)` hoặc `decode("0xFF")` |
| `decode("1_000")` lỗi | `_` là cú pháp literal, không phải cú pháp chuỗi parse | Dùng chuỗi chữ số hợp lệ |
| `decode("42L")` lỗi | Hậu tố `L` chỉ dùng ở literal Java | `Long.decode("42")` |
| `decode(" 42")` lỗi | Không tự bỏ khoảng trắng | Trim sau khi kiểm tra chính sách |
| `decode("0x-1")` lỗi | Dấu phải trước prefix | Viết `"-0x1"` |
| `decode("0xFFFFFFFF")` lỗi | Vượt miền `int` có dấu | Dùng unsigned parser sau khi bỏ `0x` |
| `Byte.decode("0200")` lỗi | Bát phân 200 bằng 128, ngoài miền `byte` | Chọn kiểu rộng hoặc xác định signed/unsigned |
| `getInteger("123")` không trả 123 như mong đợi | Tìm system property tên `123` | Dùng `parseInt`/`decode` |
| Parse mã `"000123"` và mất số 0 đầu | Đối xử mã định danh như số học | Giữ `String` |
| Chấp nhận mọi dạng prefix theo suy luận | Giao thức chưa định nghĩa radix | Thiết kế hợp đồng parser rõ ràng |

## 17. Bài tập tự kiểm tra

**Câu 1.** `Integer.decode("010")` và `Integer.parseInt("010")` lần lượt bằng bao nhiêu?  
**Đáp án:** 8 và 10; `decode` thấy bát phân còn `parseInt(String)` dùng decimal.

**Câu 2.** `Integer.decode("0x2A")` và `Integer.decode("#2A")` có giống nhau không?  
**Đáp án:** Có, đều bằng 42 vì đều dùng cơ số 16.

**Câu 3.** `Integer.decode("0b101010")` có cho 42 không?  
**Đáp án:** Không, nó ném `NumberFormatException`. Dùng `Integer.parseInt("101010", 2)`.

**Câu 4.** Vì sao `Integer.decode("08")` lỗi trong khi `Integer.parseInt("08")` trả 8?  
**Đáp án:** Dấu 0 đầu khiến `decode` hiểu là bát phân, nơi chữ số 8 không hợp lệ; `parseInt` mặc định decimal.

**Câu 5.** Dấu âm trong chuỗi hex nên viết ở đâu?  
**Đáp án:** Trước tiền tố: `"-0x2A"`, không phải `"0x-2A"`.

**Câu 6.** Có thể đọc `Integer.MIN_VALUE` bằng dạng hex không?  
**Đáp án:** Có: `Integer.decode("-0x80000000")` trả -2_147_483_648.

**Câu 7.** `Integer.decode("0xFFFFFFFF")` có trả -1 không?  
**Đáp án:** Không, chuỗi biểu thị số dương hex ngoài miền `int` có dấu. Dùng `Integer.parseUnsignedInt("FFFFFFFF",16)` để lấy mẫu bit, rồi đọc không dấu bằng `toUnsignedString`.

**Câu 8.** Vì sao `Integer.parseInt("FF",16)` được còn `Integer.parseInt("0xFF",16)` không được?  
**Đáp án:** Overload `parseInt(text, radix)` nhận **các chữ số** theo cơ số, không tự xử lý tiền tố.

**Câu 9.** Nếu cột dữ liệu chứa mã `"008"`, có nên dùng `decode` để lưu vào hệ thống không?  
**Đáp án:** Thường không. Đây có thể là mã định danh cần giữ nguyên số 0 đầu; dùng `String`.

**Câu 10.** `Long.decode("0x100000000")` bằng bao nhiêu?  
**Đáp án:** 4_294_967_296, nằm trong miền `long` có dấu.

**Câu 11.** Muốn đọc số nguyên quá lớn cho `long` từ chuỗi hex `"FFFFFFFFFFFFFFFF"` như **giá trị toán học dương**, dùng gì?  
**Đáp án:** `new BigInteger("FFFFFFFFFFFFFFFF", 16)`. Nếu chỉ cần mẫu bit không dấu 64 bit, dùng `Long.parseUnsignedLong("FFFFFFFFFFFFFFFF",16)` kèm API hiển thị/so sánh không dấu thích hợp.

**Câu 12.** `Integer.getInteger("app.port")` khác `Integer.decode("0x1F90")` ở đâu?  
**Đáp án:** Phương thức đầu đọc **giá trị system property** có tên `app.port` rồi parse; phương thức sau parse trực tiếp **chuỗi đối số**.

## 18. Checklist lựa chọn API và nguồn tham khảo

Trước khi triển khai parser cho chuỗi số nguyên:

1. Xác định trường dữ liệu là **số** hay **mã định danh**.
2. Định nghĩa **cơ số** và tiền tố hợp lệ ở API/giao thức.
3. Xác định cách xử lý **dấu**, số 0 đầu và khoảng trắng.
4. Chọn kiểu đích `byte`, `short`, `int`, `long`, `BigInteger` và giới hạn miền.
5. Quyết định đầu vào là **signed value** hay **unsigned bit pattern**.
6. Sử dụng `decode` chỉ khi quy tắc `0x`/`0X`/`#`/`0` bát phân thực sự được chấp nhận.
7. Dùng `parseInt(text)` hoặc `parseLong(text)` cho decimal; dùng `parseInt(text, radix)` khi cơ số được chỉ định.
8. Bắt `NumberFormatException` và chuyển thành lỗi phù hợp ở ranh giới dữ liệu.
9. Kiểm thử số MIN/MAX, các prefix hợp lệ/sai, input dài, dấu sai vị trí và khoảng trắng.
10. Không dùng khả năng parse được một chuỗi để suy ra dữ liệu phù hợp nghiệp vụ nếu còn ràng buộc khác.

**Ghi nhớ:** `Integer.decode` và `Long.decode` hỗ trợ **decimal**, **octal khi chuỗi bắt đầu bằng 0**, và **hex với `0x`, `0X`, `#`**; dấu `+/-` đứng **trước tiền tố**. Chúng **không** hiểu tiền tố `0b`, dấu `_` hay hậu tố `L` trong chuỗi runtime. Nếu yêu cầu là decimal, ưu tiên `parseInt`; nếu là bit pattern unsigned hoặc phạm vi số rất lớn, dùng API phù hợp hơn.

Tài liệu chuẩn:
- [Java SE 17 — Integer.decode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#decode(java.lang.String))
- [Java SE 17 — Long.decode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html#decode(java.lang.String))
- [Java SE 17 — Integer.parseInt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#parseInt(java.lang.String,int))
- [Java SE 17 — BigInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-006">Vì sao parseInt không xử lý literal 0x hoặc dấu gạch dưới như trình biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `Integer.parseInt` không xử lý tiền tố `0x` hay dấu gạch dưới như trình biên dịch Java?

Các ký hiệu `0x`, `0b`, `_` giữa chữ số hoặc hậu tố `L` là một phần **cú pháp literal số trong mã nguồn Java**. Chúng được trình biên dịch xử lý khi đọc chương trình. Ngược lại, `Integer.parseInt(String)` là API chạy **lúc runtime**, nhận một chuỗi và chuyển nó thành giá trị `int` theo **quy tắc cú pháp riêng**. Phương thức này **không phải một trình biên dịch Java thu nhỏ**.

```java
int sourceLiteral = 0x2A;                         // 42: Java compiler hiểu 0x.
int sourceUnderscore = 1_000;                     // 1000: Java compiler hiểu _.
int parsedHex = Integer.parseInt("2A", 16);       // 42: radix được truyền riêng.

// Integer.parseInt("0x2A", 16); // NumberFormatException.
// Integer.parseInt("1_000");    // NumberFormatException.
```

**Ý chính:** Những ký tự nằm trong một `String` là **dữ liệu**, không được Java tự quét lại như token của ngôn ngữ. Nếu input dạng `"0x2A"`, bạn phải chọn API hiểu tiền tố ấy, chẳng hạn `Integer.decode`, hoặc tự xác thực và bỏ tiền tố trước khi gọi `parseInt`. Nếu input chứa underscore, phải định nghĩa **quy tắc hợp lệ** rồi chuẩn hóa; không phải mọi cách bỏ underscore đều đúng với cú pháp literal.

## 1. Hai giai đoạn xử lý hoàn toàn khác nhau

### 1.1. Literal trong source: được xử lý trước khi chương trình chạy

Khi Java compiler đọc mã:

```java
int decimal = 42;
int hex = 0x2A;
int binary = 0b101010;
int octal = 052;
int separated = 1_000_000;
```

Nó phân tích **token literal** theo cú pháp ngôn ngữ Java. Các cách viết có thể tạo cùng giá trị số học, nhưng compiler nhận ra cấu trúc: cơ số từ tiền tố hoặc chữ số đầu, vị trí underscore hợp lệ, hậu tố `L`/`l` của kiểu `long`, và miền giá trị. Sau biên dịch, chương trình làm việc với các **giá trị số**, không phải giữ nguyên cú pháp văn bản `0x...` trong biến `int`.

Ví dụ `0x2A` là một **integer literal** hệ 16, `0b101010` là literal hệ 2, `052` là literal hệ 8 vì số 0 đứng đầu, còn `42` là literal thập phân. `1_000_000` dùng dấu gạch dưới để người đọc thấy nhóm chữ số, nhưng dấu này không có giá trị số học.

Không có chuyện một biến `int` tự ghi nhớ rằng nó được khai báo bằng thập phân hay hexadecimal. Cách **hiển thị** kết quả lại là quyết định khác, ví dụ `Integer.toString(value)` hay `Integer.toHexString(value)`.

### 1.2. `String` lúc runtime: được parser xử lý theo hợp đồng của parser

```java
String text = "0x2A";
String other = "1_000";

// Đặt trong dấu ngoặc kép: đây là nội dung String, không phải integer literal.
```

Compiler chỉ nhận ra đây là các **string literal**. Nó tạo giá trị chuỗi, **không phân tích nội dung bên trong thành token số Java**. Khi `Integer.parseInt(text)` được gọi, JVM đã ở runtime; phương thức kiểm tra ký tự theo cú pháp parser của nó.

Đây là khác biệt nền tảng: cùng chuỗi ký tự nhìn thấy được có thể mang hai ý nghĩa theo hai ngữ cảnh **token của source code** và **dữ liệu chuỗi**.

| Dòng mã / input | Ai xử lý? | Kết quả |
| --- | --- | --- |
| `int x = 0x2A;` | Java compiler | `x == 42` |
| `Integer.parseInt("2A", 16)` | `parseInt` tại runtime | Trả `42` |
| `Integer.parseInt("0x2A", 16)` | `parseInt` tại runtime | Ném `NumberFormatException` |
| `int x = 1_000;` | Java compiler | `x == 1000` |
| `Integer.parseInt("1_000")` | `parseInt` tại runtime | Ném `NumberFormatException` |
| `int x = 010;` | Java compiler | `x == 8` vì literal bát phân |
| `Integer.parseInt("010")` | `parseInt` tại runtime | Trả `10` vì mặc định radix 10 |

Đừng đánh đồng **khác biệt cú pháp** với **khác biệt giá trị**. `0x2A` và `42` tạo cùng `int`; `parseInt` chỉ không chấp nhận toàn bộ cách viết literal như input.

## 2. Hợp đồng thực tế của `Integer.parseInt`

Trong Java SE 17, các overload cốt lõi gồm:

```java
int a = Integer.parseInt("42");      // Mặc định radix = 10.
int b = Integer.parseInt("2A", 16);   // Hex digits, radix = 16.
int c = Integer.parseInt("101010", 2); // Binary digits, radix = 2.
int d = Integer.parseInt("-2A", 16);  // -42, dấu trừ được phép.
```

`Integer.parseInt(String s, int radix)` nhận:

1. Một dấu ASCII `+` hoặc `-` **ở đầu**, tùy chọn.
2. Theo sau là ít nhất một chữ số hợp lệ với `radix`.
3. Không có các ký tự trang trí như `_`, `0x`, `0b`, hậu tố `L`, khoảng trắng chưa chuẩn hóa.
4. Giá trị số sau dấu phải nằm trong miền `int` có dấu, từ `Integer.MIN_VALUE` đến `Integer.MAX_VALUE`.
5. `radix` trong khoảng `Character.MIN_RADIX` (2) đến `Character.MAX_RADIX` (36).

Một ký tự chữ số được xác định theo `Character.digit(character, radix)`; ngoài `0..9` và các chữ cái tương ứng, Java có thể chấp nhận một số ký tự chữ số Unicode được API `Character` nhận diện. Nếu định dạng đầu vào của ứng dụng **chỉ cho ASCII**, bạn phải đặt ràng buộc riêng; không mặc nhiên coi parser là bộ kiểm tra ASCII.

### Phương thức không đoán cơ số từ chuỗi

```java
System.out.println(Integer.parseInt("010"));     // 10.
System.out.println(Integer.parseInt("010", 10)); // 10.
System.out.println(Integer.parseInt("010", 8));  // 8.
```

Tham số `radix` giải quyết **cơ số**, không phải **tiền tố**. Ví dụ radix 16 nghĩa là ký tự `A` có giá trị 10, chứ không có nghĩa chuỗi được phép bắt đầu bằng `0x`.

```java
System.out.println(Integer.parseInt("FF", 16)); // 255.

// Integer.parseInt("0xFF", 16); // NumberFormatException vì chữ 'x'.
// Integer.parseInt("0b101", 2); // NumberFormatException vì chữ 'b'.
```

Trong `"0xFF"`, ký tự `0` có thể là chữ số hệ 16, nhưng ký tự `x` không phải chữ số hợp lệ của radix 16. Parser không có quy tắc “xem hai ký tự đầu là prefix rồi bỏ qua”.

### Dấu âm và dấu dương là ngoại lệ được parser nhận biết

```java
System.out.println(Integer.parseInt("-42"));     // -42.
System.out.println(Integer.parseInt("+42"));     // 42.
System.out.println(Integer.parseInt("-2A", 16)); // -42.
```

Việc chấp nhận dấu đầu **không đồng nghĩa** parser chấp nhận toàn bộ cú pháp unary expression Java. `Integer.parseInt("2+3")` không tính biểu thức; `Integer.parseInt("--42")` không hợp lệ; `Integer.parseInt("-0x2A",16)` cũng không hợp lệ do tiền tố `0x` vẫn còn.

### Lỗi được phát hiện lúc runtime

Dữ liệu sai cú pháp hoặc vượt miền làm `parseInt` ném `NumberFormatException`, một lớp con của `IllegalArgumentException`:

```java
// Tất cả đều có lỗi:
Integer.parseInt("1_000");
Integer.parseInt("0x2A");
Integer.parseInt("3.14");
Integer.parseInt("42L");
Integer.parseInt("2147483648");
Integer.parseInt(" 42");
```

Đây là **những lời gọi độc lập được cố ý minh họa lỗi**, không phải đoạn chương trình chạy liên tiếp an toàn. Nếu muốn chương trình tiếp tục, bắt exception ở ranh giới input và báo lỗi có ngữ cảnh.

## 3. Cú pháp dấu gạch dưới trong numeric literal của compiler

Kể từ Java 7, một hoặc nhiều dấu `_` được cho phép **giữa các chữ số** của một literal số với các giới hạn do grammar quy định. Chúng giúp nhìn các nhóm chữ số dễ hơn, không làm đổi giá trị.

```java
int million = 1_000_000;
int flags = 0b1010_0101;
int color = 0xCA_FE;
long big = 9_223_372_036_854_775_807L;
```

Các ví dụ trên đều là **source code Java hợp lệ**. Compiler biết `_` là ký hiệu phân nhóm nên bỏ qua nó khi xác định giá trị.

Tuy nhiên, không phải chuỗi có underscore ở đâu cũng là literal hợp lệ:

```java
// int a = _1000;  // Không phải integer literal hợp lệ; có thể được hiểu như tên.
// int b = 1000_;  // Không hợp lệ: underscore ở cuối chữ số.
// int c = 0x_FF;  // Không hợp lệ: ngay sau prefix, trước chữ số đầu.
// int d = 0b_101; // Không hợp lệ: ngay sau prefix.
// long e = 100_L; // Không hợp lệ: underscore ngay trước hậu tố L.
```

Lưu ý `100__0` **là literal hợp lệ**: Java cho phép nhiều underscore liên tiếp **nằm giữa hai chữ số**. Vì vậy một validator chỉ cho đúng **một** underscore giữa hai nhóm chữ số là một **định dạng input tùy chỉnh**, không hoàn toàn bằng toàn bộ grammar literal Java.

`Integer.parseInt("1_000")` không dùng grammar literal, nên nó sẽ ném `NumberFormatException` ngay cả khi `int n = 1_000;` biên dịch thành công.

### Vì sao không nên cứ `replace("_", "")` vô điều kiện?

```java
String input = "1_000";
int n = Integer.parseInt(input.replace("_", "")); // 1000.
```

Đoạn này **có thể đúng nếu nghiệp vụ đã quy định underscore chỉ được xuất hiện giữa các chữ số hợp lệ**. Nhưng thao tác thay thế mù quáng cũng biến:

```text
_100 -> 100
100_ -> 100
1__0 -> 10
```

Hai ví dụ đầu **không phải cách viết literal số hợp lệ**; ví dụ thứ ba có thể hợp lệ trong source Java nhưng có thể vi phạm **chính sách input hẹp hơn** của ứng dụng. Nếu chỉ dùng `replace` thì tất cả đều được nhận mà không phân biệt cú pháp ban đầu.

**Nguyên tắc:** trước khi chuẩn hóa, **xác thực toàn bộ chuỗi** với một quy tắc rõ ràng. Sau đó mới bỏ underscore và gọi `parseInt`. Không dùng `replace` như bước “sửa lỗi” im lặng để nhận input chưa được kiểm tra.

## 4. Phân biệt tiền tố `0x`, `0b` và số 0 đầu

Trong Java source:

| Literal | Cơ số | Giá trị thập phân |
| --- | ---: | ---: |
| `42` | 10 | 42 |
| `0x2A` | 16 | 42 |
| `0X2a` | 16 | 42 |
| `0b101010` | 2 | 42 |
| `0B101010` | 2 | 42 |
| `052` | 8 | 42 |
| `010` | 8 | 8 |
| `000` | 8 | 0 |

Compiler xác định loại literal từ **cú pháp token nguồn**. `Integer.parseInt` thì nhận **radix** truyền vào và các chữ số tương ứng, không suy cơ số theo token source.

```java
System.out.println(Integer.parseInt("052"));    // 52.
System.out.println(Integer.parseInt("052", 8)); // 42.
System.out.println(Integer.parseInt("0b101010", 2)); // Ném NumberFormatException.
```

Dòng cuối là minh họa chủ động một trường hợp ném exception; khi chạy chương trình thật, không gọi trực tiếp nếu muốn các dòng sau vẫn thực thi.

### Bẫy `010` và `09`

```java
int octalTen = 012;           // 10 decimal.
int octalEight = 010;         // 8 decimal.
int parsed = Integer.parseInt("010"); // 10 decimal.
```

Trong source Java, `09` là literal **không hợp lệ**, vì `9` không phải chữ số bát phân sau tiền tố `0`. Nhưng `Integer.parseInt("09")` trả **9** vì parser mặc định dùng thập phân.

Đây là lý do **không nên mặc định coi chuỗi do người dùng nhập có cùng quy tắc số 0 đầu với mã nguồn Java**. Nhiều hệ thống nhập mã bưu điện, số hóa đơn, chuỗi phiên bản hoặc số điện thoại; có những dữ liệu không nên được parse thành số ngay từ đầu vì số 0 đầu có thể mang ý nghĩa định danh.

## 5. Khi nào nên dùng `Integer.decode`?

Nếu input của ứng dụng **cố ý hỗ trợ một số dạng tiền tố**, JDK có `Integer.decode(String)`:

```java
System.out.println(Integer.decode("42"));    // 42.
System.out.println(Integer.decode("0x2A"));  // 42.
System.out.println(Integer.decode("0X2A"));  // 42.
System.out.println(Integer.decode("#2A"));   // 42.
System.out.println(Integer.decode("052"));   // 42.
System.out.println(Integer.decode("-0x2A")); // -42.
```

`decode` hỗ trợ:
- Không có tiền tố: thập phân.
- `0x` hoặc `0X` hoặc `#`: hệ 16.
- `0` đầu một chuỗi nhiều chữ số: hệ 8.
- Dấu `+` hoặc `-` đầu chuỗi, nếu có.

Nhưng `Integer.decode` **không phải trình phân tích tất cả numeric literals Java**:

```java
// Integer.decode("0b101010"); // NumberFormatException.
// Integer.decode("1_000");    // NumberFormatException.
// Integer.decode("42L");      // NumberFormatException.
// Integer.decode("09");       // NumberFormatException (09 bị hiểu octal).
```

**Đặc biệt:** `decode("09")` bị hiểu là octal và thất bại, khác `parseInt("09")` là 9. Nếu dữ liệu do người dùng nhập thường có số 0 đầu nhưng phải hiểu **hệ 10**, không dùng `decode` như lựa chọn mặc định.

### `decode` và dấu âm trong hệ 16

```java
System.out.println(Integer.decode("-0x2A")); // -42.
```

`decode` hiểu dấu âm trước prefix. `parseInt("-2A",16)` cũng hiểu dấu âm nhưng **không** nhận prefix `0x`. Nếu bạn đã chuẩn hóa input bằng cách tách prefix, hãy giữ dấu ở vị trí hợp lệ.

### API liên quan tới kiểu lớn

- `Long.parseLong(text, radix)` và `Long.decode(text)` tương ứng dành cho `long`.
- `Byte.decode(text)` và `Short.decode(text)` có hỗ trợ cú pháp prefix tương tự, nhưng miền giá trị hẹp hơn.
- `new BigInteger(text, radix)` cho phép số nguyên nhiều chữ số hơn `long`, nhưng input radix vẫn cần xử lý tiền tố theo hợp đồng của constructor.
- `Integer.parseUnsignedInt(text, radix)` dành cho giá trị **không dấu 32 bit** theo diễn giải `int` không dấu, không tự nhận `0x` hoặc `_`.

Hãy chọn API theo **miền giá trị và định dạng đầu vào**, không chỉ theo tên phương thức.

## 6. Bẫy nâng cao: literal hexadecimal và giới hạn `int` có dấu

Một khác biệt khác không giải quyết chỉ bằng việc bỏ `0x` và underscore: **quy tắc về số nguyên không dấu trong cú pháp literal hệ 16 và parser số nguyên có dấu**.

```java
int allBitsSet = 0xFFFF_FFFF; // Hợp lệ; giá trị int là -1.
System.out.println(allBitsSet); // -1.
```

`0xFFFF_FFFF` viết dưới dạng literal hệ 16 biểu diễn mẫu bit 32 bit toàn `1`. Giá trị `int` có dấu tương ứng là **−1**.

Nếu dùng parser `int` có dấu:

```java
// Integer.parseInt("FFFFFFFF", 16); // NumberFormatException:
// 4294967295 > Integer.MAX_VALUE.
```

`Integer.parseInt` không mặc định xem chuỗi như mẫu bit không dấu 32 bit. Nếu dữ liệu của bạn **định nghĩa hex là biểu diễn unsigned 32-bit**, dùng:

```java
int bits = Integer.parseUnsignedInt("FFFFFFFF", 16);
System.out.println(bits);                          // -1 (xem như signed int).
System.out.println(Integer.toUnsignedString(bits)); // 4294967295.
```

`parseUnsignedInt` trả về kiểu `int` do Java không có kiểu `uint` primitive, nhưng có thể diễn giải bit theo unsigned khi dùng `Integer.toUnsignedString`, `Integer.compareUnsigned`, `Integer.divideUnsigned`...

**Đừng đánh đồng ba ý nghĩa khác nhau:**
1. Số nguyên signed có giá trị trong `[-2^31, 2^31-1]`.
2. Mẫu bit 32 bit, có thể được in theo hệ 16.
3. Giá trị unsigned 32 bit trong `[0, 2^32-1]`.

`parseInt` xử lý trường hợp 1; `parseUnsignedInt` hỗ trợ trường hợp 3 và trả lại cùng mẫu bit trong `int`. Compiler xử lý literal số có quy tắc riêng cho trường hợp 2. `Integer.decode("0xFFFFFFFF")` cũng không phải giải pháp cho ý định unsigned 32 bit; nó dùng giới hạn có dấu.

### Không lẫn dấu trừ với literal âm

Trong ngữ pháp Java, một dấu `-` trước numeric literal về bản chất là **toán tử unary minus**, ngoại trừ cách compiler quy định riêng cho giá trị min của kiểu nguyên. `-0x2A` trong source có nghĩa lấy âm của literal 42. `Integer.parseInt("-2A",16)` thì coi dấu âm là một phần cú pháp string đầu vào. Hai đường dẫn có thể cho cùng giá trị nhưng không dùng cùng cơ chế.

Nếu cần parse các biểu thức như `"-0x2A"`, `"1 + 2"`, `"(0b101) << 3"`, bạn đang thiết kế **ngôn ngữ/biểu thức đầu vào** chứ không chỉ parse một số nguyên. Không đưa toàn bộ input không tin cậy vào trình chạy mã Java.

## 7. Kiểm tra lỗi đầu vào: cú pháp, radix và overflow

`Integer.parseInt` thường ném `NumberFormatException` nếu:

- Chuỗi rỗng, không có chữ số sau dấu, hoặc chữ số không hợp lệ với radix.
- Gặp tiền tố `0x`/`0b` hay underscore không được hợp đồng `parseInt` hỗ trợ.
- Gặp khoảng trắng trước/sau hoặc ký tự thừa không được xử lý trước.
- Giá trị ngoài miền `int` có dấu.
- Radix ngoài `2..36`.

```java
// Các lời gọi dưới đây cố ý minh họa lỗi:
Integer.parseInt("");
Integer.parseInt(" 42 ");
Integer.parseInt("12.0");
Integer.parseInt("42L");
Integer.parseInt("999999999999999");
Integer.parseInt("2", 2);
Integer.parseInt("10", 1);
```

Đoạn trên **không phải ví dụ runnable liên tục** vì lệnh đầu đã ném exception. Ví dụ hoàn chỉnh ở phần 11 trình bày helper chuyển kết quả thất bại thành chữ `INVALID` để chương trình vẫn chạy hết.

### Vì sao `trim` không giải quyết mọi khác biệt?

`Integer.parseInt(" 42 ")` thất bại, trong khi `Integer.parseInt(" 42 ".trim())` trả 42. Đây là **chính sách chuẩn hóa khoảng trắng** do ứng dụng chọn. Nhưng `trim` không chuyển `0x` thành chữ số hợp lệ, không xóa underscore và không bỏ hậu tố literal; nó chỉ xử lý một nhóm ký tự khoảng trắng theo định nghĩa của `String.trim`. `String.strip()` có hành vi Unicode whitespace khác `trim()`.

Nếu dữ liệu cần khớp chính xác với một chuẩn trao đổi, không nên “tự sửa” các chuỗi không hợp lệ mà không thông báo. Một số giao thức yêu cầu từ chối khoảng trắng để tránh vấn đề diễn giải không nhất quán giữa các hệ thống.

### Số 0 đầu có thể mang ý nghĩa của dữ liệu, không phải cú pháp số

Chuỗi `"00042"` đôi khi là một **định danh**. Nếu parse thành `42`, bạn mất ba số 0 đầu. Nếu cần lưu mã dạng này, giữ `String`, kiểm tra độ dài và tập ký tự theo quy tắc mã thay vì dùng `parseInt`.

## 8. Khi ứng dụng muốn cho nhập hex có underscore: xác thực rồi chuẩn hóa

Nếu hợp đồng của hệ thống định nghĩa **chuỗi số hex có prefix `0x` hoặc `0X`**, có thể kèm dấu và underscore phân cách **theo một quy tắc chủ động**, hãy xây parser riêng trước khi gọi `Integer.parseInt`.

Ví dụ bên dưới cố tình chỉ chấp nhận **một hoặc nhiều chữ số hex theo ASCII** với **không quá một underscore giữa hai nhóm chữ số**. Đây là **chính sách input hẹp hơn** grammar Java (Java có thể cho nhiều underscore liên tiếp giữa chữ số).

```java
static int parseHexInput(String input) {
    if (input == null ||
            !input.matches("[+-]?0[xX][0-9a-fA-F]+(?:_[0-9a-fA-F]+)*")) {
        throw new NumberFormatException("Invalid hex input");
    }
    boolean hasSign = input.startsWith("-") || input.startsWith("+");
    String sign = input.startsWith("-") ? "-" : "";
    String digits = input.substring(hasSign ? 3 : 2).replace("_", "");
    return Integer.parseInt(sign + digits, 16);
}
```

Ví dụ:

```java
System.out.println(parseHexInput("0x2A"));  // 42.
System.out.println(parseHexInput("+0x2_A")); // 42.
System.out.println(parseHexInput("-0x2A")); // -42.
// parseHexInput("0x_2A");    // NumberFormatException.
// parseHexInput("0x2__A");   // NumberFormatException theo policy này.
// parseHexInput("0xFFFFFFFF"); // NumberFormatException do vượt miền signed int.
```

**Quan trọng:** phương thức ví dụ không cố mô phỏng hết compiler Java. Nó diễn đạt một **định dạng nhập số hex có dấu, có underscore hạn chế và kết quả signed int**. Nếu cần hỗ trợ hex theo mẫu bit unsigned 32 bit, phải dùng `parseUnsignedInt` và thiết kế lại chính sách dấu; không thể chỉ thay tên phương thức.

Nếu ứng dụng chỉ cần hex đơn giản không underscore, `Integer.decode` đủ tiện và dễ đọc hơn. Nếu chỉ cần các chữ số hex **không có prefix**, `Integer.parseInt(text,16)` trực tiếp là phù hợp nhất.

### Nên dùng regex nào?

Validator có thể chấp nhận:
- Chữ số theo radix và ASCII/Unicode tùy hợp đồng.
- Có hay không prefix, dấu và underscore.
- Có hay không khoảng trắng.
- Miền số signed hay unsigned.

**Không nên cố dùng một regex khổng lồ để bao quát toàn bộ Java numeric literal** nếu nghiệp vụ chỉ cần một kiểu input cụ thể. Càng hỗ trợ nhiều cú pháp, khả năng mơ hồ càng tăng (ví dụ `010` có nên là octal hay decimal?).

## 9. Phân biệt compile-time error và runtime exception

Với mã nguồn Java:

```java
int valid = 0x2A;
int formatted = 1_000;

// int invalid = 0x_2A; // Sai vị trí underscore: lỗi biên dịch.
// int invalidOctal = 09; // Sai digit octal: lỗi biên dịch.
```

Sai cú pháp literal được phát hiện ở **compile time**. Nhưng biểu thức:

```java
String incoming = "0x_2A";
int parsed = Integer.parseInt(incoming, 16);
```

vẫn **biên dịch thành công**, vì compiler chỉ kiểm tra rằng `incoming` là `String` và có overload phù hợp cho `parseInt`. Khi chạy đến lời gọi, parser thấy input không hợp lệ và ném `NumberFormatException`.

Ngay cả input trong một string literal **cố định** như `Integer.parseInt("0x2A",16)` cũng không bị trình biên dịch báo lỗi cú pháp số: nó là lời gọi method hợp lệ về kiểu và sẽ thất bại **lúc chạy**.

Điều này có ý nghĩa khi xử lý dữ liệu từ file CSV/JSON, biến môi trường, giao diện người dùng, request API hoặc command-line arguments. Bạn phải kiểm tra và quản lý lỗi runtime ở **ranh giới tin cậy**, không dựa vào việc mã đã biên dịch.

## 10. Lựa chọn API theo yêu cầu đầu vào

| Input hợp đồng | Ví dụ | API hoặc chiến lược phù hợp |
| --- | --- | --- |
| Chữ số thập phân signed đơn giản | `"123"`, `"-42"` | `Integer.parseInt(text)` |
| Chữ số của radix xác định, **không prefix** | `"2A"` với radix 16 | `Integer.parseInt(text,16)` |
| Tiền tố hex, số 0 đầu kiểu octal theo quy tắc decode | `"0x2A"`, `"052"` | `Integer.decode(text)` |
| Hex có prefix và underscore, signed int | `"0x2_A"` | Validator + bỏ ký tự format + `parseInt` |
| Binary có `0b` prefix | `"0b101010"` | Nhận diện/bỏ prefix có kiểm tra + `parseInt(digits,2)` |
| Số nguyên `long` với miền rộng hơn | `"4294967296"` | `Long.parseLong` hoặc `Long.decode` khi đúng format |
| Số nguyên rất lớn | `"1234567890123456789012345"` | `BigInteger` với radix và normalization rõ |
| Hex như mẫu bit unsigned 32-bit | `"FFFFFFFF"` | `Integer.parseUnsignedInt(...,16)` |
| Các mã giữ số 0 đầu, không thực hiện số học | `"00042"` | Giữ `String`, validate schema |
| Toàn bộ biểu thức số Java có toán tử | `"1 + 0x2A"` | Parser biểu thức chuyên dụng, không dùng `parseInt` |
| Dữ liệu có thể sai hoặc bị tấn công | Input bất kỳ | Validate định dạng, radix, miền; bắt lỗi ở ranh giới |

Một lựa chọn sai hay gặp là **dùng `decode` cho mọi dữ liệu số** chỉ vì nó hiểu `0x`. Nếu input có `"010"` và doanh nghiệp hiểu là mười, `decode` sẽ trả tám. Hãy quyết định **chính sách số 0 đầu** trước khi chọn API.

## 11. Chương trình Java 17+ chạy độc lập

Lưu thành `ParseVsLiteralDemo.java`:

```java
public class ParseVsLiteralDemo {
    static String parse(String text, int radix) {
        try {
            return Integer.toString(Integer.parseInt(text, radix));
        } catch (NumberFormatException ex) {
            return "INVALID";
        }
    }

    static String decode(String text) {
        try {
            return Integer.toString(Integer.decode(text));
        } catch (NumberFormatException ex) {
            return "INVALID";
        }
    }

    public static void main(String[] args) {
        int hex = 0x2A;
        int binary = 0b101010;
        int separated = 1_000_000;
        int octal = 010;
        System.out.println("Literals: " + hex + ", " + binary
                + ", " + separated + ", " + octal);

        System.out.println("parseInt(2A,16): " + parse("2A", 16));
        System.out.println("parseInt(0x2A,16): " + parse("0x2A", 16));
        System.out.println("decode(0x2A): " + decode("0x2A"));

        System.out.println("parseInt(101010,2): " + parse("101010", 2));
        System.out.println("parseInt(0b101010,2): "
                + parse("0b101010", 2));

        System.out.println("parseInt(1_000): " + parse("1_000", 10));
        System.out.println("parseInt(1_000 after removing underscore): "
                + parse("1_000".replace("_", ""), 10));

        System.out.println("parseInt(010): " + parse("010", 10));
        System.out.println("decode(010): " + decode("010"));
        System.out.println("parseInt(09): " + parse("09", 10));
        System.out.println("decode(09): " + decode("09"));

        System.out.println("parseInt(-2A,16): " + parse("-2A", 16));
        System.out.println("decode(-0x2A): " + decode("-0x2A"));
        System.out.println("parseInt(42L): " + parse("42L", 10));
        System.out.println("parseInt(space42): " + parse(" 42", 10));
        System.out.println("parseInt(2147483648): "
                + parse("2147483648", 10));

        int bits = Integer.parseUnsignedInt("FFFFFFFF", 16);
        System.out.println("unsigned bits: " + bits);
        System.out.println("unsigned decimal: "
                + Integer.toUnsignedString(bits));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ParseVsLiteralDemo.java
java ParseVsLiteralDemo
```

Stdout:

```text
Literals: 42, 42, 1000000, 8
parseInt(2A,16): 42
parseInt(0x2A,16): INVALID
decode(0x2A): 42
parseInt(101010,2): 42
parseInt(0b101010,2): INVALID
parseInt(1_000): INVALID
parseInt(1_000 after removing underscore): 1000
parseInt(010): 10
decode(010): 8
parseInt(09): 9
decode(09): INVALID
parseInt(-2A,16): -42
decode(-0x2A): -42
parseInt(42L): INVALID
parseInt(space42): INVALID
parseInt(2147483648): INVALID
unsigned bits: -1
unsigned decimal: 4294967295
```

Tất cả dòng `INVALID` được tạo bởi helper **cố ý bắt `NumberFormatException`**, không phải một cách diễn đạt cú pháp của `parseInt`. Trong ứng dụng thực tế, lỗi cần chứa ngữ cảnh field/line/input policy và được xử lý phù hợp, thay vì âm thầm biến dữ liệu hỏng thành một số hợp lệ.

Đoạn ví dụ cho thấy đồng thời:
- Compiler nhận `0x`, `0b` và underscore trong numeric literal.
- `parseInt` cần **digits không prefix** và radix tường minh.
- `decode` hiểu một số prefix nhưng có chính sách octal riêng.
- `parseUnsignedInt` diễn giải toàn bộ 32 bit khi input không dấu vượt `Integer.MAX_VALUE`.

## 12. Kiểm thử parser đầu vào theo hợp đồng

Khi xây API nhập số, đừng chỉ kiểm thử các số nhỏ hợp lệ. Tối thiểu cần:

| Nhóm | Ví dụ | Câu hỏi cần trả lời |
| --- | --- | --- |
| Decimal bình thường | `"42"`, `"-42"`, `"+42"` | Có hỗ trợ dấu không? |
| Tiền tố hex | `"0x2A"`, `"0X2A"`, `"#2A"` | Parser nào nhận? |
| Binary có prefix | `"0b1010"` | Có tự bỏ `0b` không? |
| Số 0 đầu | `"010"`, `"09"`, `"00042"` | Hệ 8, 10 hay là định danh? |
| Underscore hợp lệ | `"1_000"`, `"0xCA_FE"` | Có cho phân cách nhóm không? |
| Underscore ở biên | `"_100"`, `"100_"`, `"0x_FF"` | Có bị từ chối không? |
| Underscore lặp | `"1__0"` | Java literal cho phép giữa digits; input policy có cho không? |
| Hậu tố `L` | `"42L"` | Có cần bỏ suffix không, kiểu đích là gì? |
| Khoảng trắng | `" 42 "`, tab, newline | Trim, strip hay từ chối? |
| Vượt giới hạn | `"2147483648"`, `"-2147483649"` | Signed overflow được báo ra sao? |
| Unsigned 32-bit | `"FFFFFFFF"` | Có cần giữ mẫu bit và in không dấu? |
| Radix không hợp lệ | `1`, `37` | Parser có ném `NumberFormatException` không? |
| Chuỗi rỗng/không có chữ số | `""`, `"-"`, `"0x"` | Có thông báo lỗi dễ hiểu không? |
| Ký tự ngoài ASCII | Unicode digits, dấu trừ kiểu typography | Có policy rõ không? |

Nếu xây parser tự định nghĩa, luôn test **tính nhất quán giữa validate và parse**: một chuỗi đã qua validator không được bị diễn giải thành một cơ số hay giá trị khác dự kiến; một input không hợp lệ phải được từ chối thay vì “sửa cho chạy”.

### Nên xử lý dữ liệu không tin cậy ra sao?

Một luồng xử lý minh bạch là:

1. Ghi rõ **schema** của input: signed/unsigned, radix, prefix, dấu, underscore, khoảng trắng, độ dài và miền số.
2. Kiểm tra null, rỗng và giới hạn độ dài trước khi xử lý chuỗi quá lớn.
3. Kiểm tra cú pháp toàn bộ chuỗi, không chỉ kiểm tra “có chứa một chữ số”.
4. Chuẩn hóa **chỉ những ký hiệu được cho phép**, chẳng hạn prefix và underscore hợp lệ.
5. Gọi parser cho radix và miền giá trị đúng.
6. Bắt `NumberFormatException` tại ranh giới và báo lỗi theo schema; không bắt mọi exception rồi trả 0.
7. Kiểm thử các số sát `Integer.MIN_VALUE`/`Integer.MAX_VALUE`, trường hợp unsigned và input biên.

Giới hạn độ dài phù hợp đặc biệt quan trọng nếu parser nhận chuỗi cực dài từ request hoặc tập dữ liệu lớn. Trong mọi trường hợp, không dùng runtime evaluation của mã Java do người dùng cung cấp để “được cùng cú pháp literal”.

## 13. Các nhầm lẫn thường gặp

| Nhầm lẫn | Thực tế | Cách tránh |
| --- | --- | --- |
| “`parseInt` đọc được bất kỳ literal `int` Java nào” | Parser có grammar khác compiler | Viết chính xác input format và chọn API |
| “`radix=16` sẽ tự hiểu `0x`” | `x` không phải digit radix 16 | Bỏ prefix có xác thực hoặc dùng `decode` |
| “`parseInt("010")` giống literal `010`” | Parser decimal cho 10; literal octal cho 8 | Cố định radix/leading-zero policy |
| “`decode` luôn tốt hơn `parseInt`” | `decode("010") == 8`, `decode("09")` lỗi | Chỉ dùng nếu cần chính sách prefix/oct |
| “Compiler cho underscore thì parser cũng nhận” | `parseInt("1_000")` lỗi | Validate + chuẩn hóa có chủ đích |
| “Bỏ hết underscore luôn đúng” | Có thể nhận chuỗi không hợp lệ như `_100` | Validator chạy trước replace |
| “`parseInt("42L")` hiểu hậu tố long” | Hậu tố `L` là cú pháp nguồn | Chọn `Long.parseLong("42")` nếu cần `long` |
| “Không parse được hex 32 bit nghĩa là literal sai” | `0xFFFFFFFF` có thể là literal int mẫu bit hợp lệ | Phân biệt signed parser và unsigned pattern |
| “`String` chứa mã số được compiler phân tích như token Java” | String chỉ là dữ liệu | Runtime parser riêng, không đánh giá mã |
| “Mọi ký tự chữ số phải là ASCII” | `Character.digit` có hỗ trợ Unicode trong nhiều trường hợp | Đặt policy ASCII riêng nếu schema yêu cầu |
| “`trim` đủ để parse mọi dạng số” | Không xử lý prefix/underscore/suffix | Parse theo định dạng đã định nghĩa |
| “Thất bại `parseInt` là lỗi biên dịch” | `NumberFormatException` xảy ra runtime | Bắt/lan truyền lỗi ở ranh giới |
| “Tự viết regex có thể dễ dàng mô phỏng toàn bộ numeric literal Java” | Grammar có trường hợp biên và ngữ nghĩa width/suffix phức tạp | Chỉ nhận cú pháp thực sự cần dùng |

## 14. Câu hỏi tự kiểm tra

1. Vì sao `int x = 0x2A;` biên dịch nhưng `Integer.parseInt("0x2A",16)` ném exception? `Integer.parseInt("2A",16)` khác gì?
2. Với `0b101010` và `"0b101010"`, token nguồn và nội dung String được xử lý bởi thành phần nào?
3. `Integer.parseInt("010")`, `Integer.parseInt("010",8)` và literal `010` có giá trị bao nhiêu?
4. Vì sao literal `09` lỗi biên dịch nhưng `Integer.parseInt("09")` trả 9?
5. `Integer.decode("09")` trả gì? Có nên dùng `decode` khi input thường bắt đầu bằng 0 nhưng phải là decimal?
6. Vì sao `Integer.parseInt("1_000")` thất bại? Nếu bỏ underscore, phải kiểm tra những vị trí nào trước?
7. Literal `1__0` có hợp lệ với Java không? Một API input có bắt buộc phải chấp nhận nó không?
8. `Integer.parseInt("-2A",16)` và `Integer.decode("-0x2A")` cùng trả giá trị nào? API nào chấp nhận prefix?
9. Vì sao `int x = 0xFFFF_FFFF;` hợp lệ và là −1, còn `Integer.parseInt("FFFFFFFF",16)` lỗi? `parseUnsignedInt` giải quyết trường hợp nào?
10. Khi nào cần giữ `"00042"` dưới dạng `String` thay vì chuyển sang số?
11. Nếu nhận chuỗi `"42L"` từ file, đó là một numeric literal Java hay đơn thuần một giá trị văn bản? Hợp đồng input nên quyết định điều gì?
12. Nêu sự khác biệt giữa compile-time syntax error trong literal và runtime `NumberFormatException` khi parse.
13. Nếu cần parser theo **toàn bộ** cú pháp biểu thức Java, `Integer.parseInt` có đủ không? Tại sao không nên chạy trực tiếp source code của user?

## 15. Tài liệu chính thức

- [Java Language Specification `3.10.1 — Integer Literals (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.1): grammar literal thập phân, hexadecimal, octal, binary, underscore và hậu tố `L`.
- [Java SE 17 — `Integer.parseInt`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#parseInt(java.lang.String,int)): yêu cầu radix, dấu, chữ số hợp lệ, miền signed.
- [Java SE 17 — `Integer.decode`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#decode(java.lang.String)): nhận tiền tố `0x`/`0X`/`#`, số 0 đầu octal, dấu.
- [Java SE 17 — `Integer.parseUnsignedInt`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#parseUnsignedInt(java.lang.String,int)): phân tích biểu diễn số nguyên không dấu 32 bit.
- [Java SE 17 — `Character.digit`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html#digit(char,int)): cách xác định chữ số tương thích với radix.

**Ghi nhớ:** Compiler Java xử lý `0x`, `0b`, số 0 đầu, underscore và suffix như **cú pháp của numeric literal trong source**. `Integer.parseInt` xử lý **dữ liệu String ở runtime**: dấu đầu tùy chọn, chữ số theo radix, không tự hiểu prefix/underscore/suffix. Dùng `Integer.decode` nếu thật sự muốn prefix hex/octal, dùng `parseUnsignedInt` cho mẫu 32-bit không dấu, hoặc xây validator + normalization có chủ đích. Điều quan trọng là **định nghĩa rõ schema của input**, không kỳ vọng parser hoạt động như compiler.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-007">NumberFormatException do ký tự sai hoặc vượt miền giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# NumberFormatException do ký tự sai hoặc vượt miền giá trị trong Java

Khi chuyển văn bản sang số nguyên bằng `Integer.parseInt`, `Long.parseLong` hoặc những API tương tự, Java có thể ném **`NumberFormatException`**. Hai nhóm nguyên nhân quan trọng nhất là:

1. **Chuỗi không phù hợp cú pháp số nguyên** của API/radix được chọn: chứa ký tự không hợp lệ, dấu ở sai vị trí, dấu thập phân, khoảng trắng chưa xử lý, tiền tố chưa được API nhận diện…
2. **Chuỗi biểu diễn số vượt miền giá trị** của kiểu đích: ví dụ `2147483648` là một số thập phân hợp lệ về cú pháp nhưng lớn hơn `Integer.MAX_VALUE`.

Điểm cốt lõi: **`parseInt` kiểm tra cú pháp *và* miền biểu diễn**. Khi lỗi, phương thức không trả số gần đúng, không bỏ qua phần đuôi và không “tràn vòng” để cho ra giá trị `int` khác. Bài học thuộc nhóm **Phân tích số nguyên** và tập trung vào cách chẩn đoán, phòng tránh và xử lý đúng các trường hợp này trên Java 17+.

## 1. `NumberFormatException` xảy ra ở đâu?

`NumberFormatException` là một nhánh của `IllegalArgumentException`, thuộc nhóm **unchecked exception**. Vì vậy Java không bắt buộc caller khai báo `throws` hoặc `catch`, nhưng dữ liệu đầu vào không tin cậy vẫn cần chính sách xử lý rõ ràng.

~~~java
int quantity = Integer.parseInt("123"); // 123
long balance = Long.parseLong("123");  // 123L
~~~

Những API thường gặp:

| API | Kiểu kết quả | Khi nào gặp NumberFormatException? |
| --- | --- | --- |
| `Integer.parseInt(text)` | `int` có dấu, hệ 10 | Cú pháp sai hoặc ngoài miền 32-bit |
| `Integer.parseInt(text, radix)` | `int` có dấu, hệ 2–36 | Ký tự không thuộc cơ số, radix không hợp lệ, hoặc ngoài miền `int` |
| `Long.parseLong(text)` | `long` có dấu, hệ 10 | Cú pháp sai hoặc ngoài miền 64-bit |
| `Integer.parseUnsignedInt(text)` | `int` chứa bit của số không dấu 32-bit | Sai cú pháp hoặc ngoài miền 0 đến 2³² − 1 |
| `Long.parseUnsignedLong(text)` | `long` chứa bit của số không dấu 64-bit | Sai cú pháp hoặc ngoài miền 0 đến 2⁶⁴ − 1 |
| `Integer.decode(text)` | `Integer` | Sai cú pháp, tiền tố không hợp lệ hoặc vượt miền kiểu đích |
| `new BigInteger(text)` | `BigInteger` | Cú pháp sai; **không bị chặn bởi giới hạn `int`/`long`** |

`Integer.valueOf(String)` và `Long.valueOf(String)` cũng dựa trên parsing có kiểm tra cú pháp/miền tương ứng. `valueOf` trả wrapper, trong khi `parseInt` / `parseLong` trả primitive; **cả hai không mặc nhiên biến chuỗi lỗi thành null hoặc 0**.

Đừng nhầm `NumberFormatException` với `ArithmeticException` của phép chia số nguyên cho 0 hay `NullPointerException` do unboxing wrapper null. Chúng có điểm phát sinh và chiến lược xử lý khác nhau.

## 2. Những ký tự khiến `Integer.parseInt` thất bại

`Integer.parseInt(text)` mặc định hiểu **chuỗi số nguyên thập phân**, với một dấu `+` hoặc `-` tùy chọn ở **đầu chuỗi**, theo sau là ít nhất một chữ số hợp lệ. Hàm phân tích toàn bộ chuỗi, không chỉ phần tiền tố chứa số.

| Chuỗi | Kết quả khi gọi `Integer.parseInt` | Giải thích |
| --- | --- | --- |
| `"42"` | `42` | Hợp lệ |
| `"+42"` | `42` | Dấu cộng đứng đầu được phép |
| `"-42"` | `-42` | Dấu trừ ASCII đứng đầu được phép |
| `"0012"` | `12` | Số 0 ở đầu không làm `parseInt` tự đổi sang hệ 8 |
| `""` | Ngoại lệ | Không có chữ số |
| `"+"` hoặc `"-"` | Ngoại lệ | Chỉ có dấu, thiếu chữ số |
| `"12x"`, `"1 2"` | Ngoại lệ | Chứa ký tự không phải chữ số ở cơ số 10 |
| `"1_000"` | Ngoại lệ | Dấu gạch dưới không thuộc cú pháp runtime parser |
| `"12.0"`, `"1e3"` | Ngoại lệ | Đây không phải cú pháp số nguyên của `parseInt` |
| `" 42 "` hoặc `"42\n"` | Ngoại lệ | Có khoảng trắng/ký tự xuống dòng |
| `"0x2A"` | Ngoại lệ | `parseInt` hệ 10 không tự xử lý tiền tố `0x` |
| `"−42"` | Ngoại lệ | Dấu trừ Unicode U+2212 không phải dấu trừ ASCII `-` |

**Đặc biệt về dấu gạch dưới:** mã nguồn Java chấp nhận `1_000` là một *integer literal* hợp lệ trong trình biên dịch, nhưng chuỗi runtime `"1_000"` đưa vào `parseInt` **không hợp lệ**. Đừng nhầm cú pháp literal viết trong source code với cú pháp dữ liệu văn bản được API phân tích.

**Đặc biệt về Unicode:** parsing Java sử dụng quy tắc nhận diện chữ số dựa trên `Character.digit`, vì thế một số chữ số Unicode (ví dụ các chữ số fullwidth) có thể được chấp nhận. Nếu định dạng đầu vào của một giao thức buộc phải là ASCII `0–9`, hãy xác minh quy tắc ASCII một cách tường minh, **không** dựa vào việc `parseInt` sẽ từ chối mọi chữ số ngoài ASCII. Ngược lại, dấu trừ Unicode U+2212 không được coi như dấu `-` ASCII.

## 3. Chuỗi đúng cú pháp vẫn có thể vượt miền kiểu số

`int` có dấu của Java là kiểu 32-bit, có miền:

- `Integer.MIN_VALUE = -2147483648`
- `Integer.MAX_VALUE = 2147483647`

Vì vậy:

~~~java
int max = Integer.parseInt("2147483647");  // Hợp lệ
int min = Integer.parseInt("-2147483648"); // Hợp lệ

// Integer.parseInt("2147483648");  // NumberFormatException
// Integer.parseInt("-2147483649"); // NumberFormatException
~~~

`"2147483648"` là chuỗi gồm toàn chữ số hợp lệ. Nó không được chấp nhận vì **giá trị toán học không nằm trong miền `int`**, chứ không phải do “có ký tự lạ”.

Với `long` có dấu, giới hạn tương ứng là:

- `Long.MIN_VALUE = -9223372036854775808`
- `Long.MAX_VALUE = 9223372036854775807`

~~~java
long maxLong = Long.parseLong("9223372036854775807");

// Long.parseLong("9223372036854775808"); // NumberFormatException
~~~

Số `2147483648` có thể parse thành `long`, còn số vượt `Long.MAX_VALUE` có thể được biểu diễn bằng `BigInteger`:

~~~java
long largerThanInt = Long.parseLong("2147483648");
java.math.BigInteger largerThanLong =
        new java.math.BigInteger("9223372036854775808");
~~~

**Một kiểu lớn hơn chỉ giải quyết lỗi vượt miền, không giải quyết chuỗi có ký tự sai.** `new BigInteger("12x")` vẫn ném `NumberFormatException`.

Cũng phải phân biệt **parse** và **phép toán đã parse xong**: `Integer.parseInt("2147483647") + 1` có thể overflow theo quy tắc số nguyên `int`, trong khi `Integer.parseInt("2147483648")` ném ngoại lệ ngay lúc parse. Nếu muốn phát hiện overflow khi cộng, dùng `Math.addExact` hoặc nâng kiểu dữ liệu hợp lý.

## 4. Radix (cơ số) và ký tự hợp lệ

`Integer.parseInt(text, radix)` chấp nhận cơ số từ `Character.MIN_RADIX` = 2 tới `Character.MAX_RADIX` = 36. Một ký tự có giá trị chữ số phải nhỏ hơn radix; với radix 16, chữ `A`–`F` biểu thị 10–15 (không phân biệt hoa/thường trong trường hợp ASCII).

~~~java
int decimalFromBinary = Integer.parseInt("1010", 2); // 10
int decimalFromHex = Integer.parseInt("2A", 16);     // 42

// Integer.parseInt("102", 2);    // NumberFormatException: digit 2 không thuộc hệ 2
// Integer.parseInt("0x2A", 16);  // NumberFormatException: parseInt không bỏ tiền tố 0x
~~~

**Radix và tiền tố là hai cơ chế riêng:**

| Cách gọi | Kết quả | Ghi chú |
| --- | --- | --- |
| `Integer.parseInt("2A", 16)` | `42` | Hệ 16 không kèm tiền tố |
| `Integer.parseInt("0x2A", 16)` | Ngoại lệ | `parseInt` không tự loại `0x` |
| `Integer.decode("0x2A")` | `42` | `decode` nhận tiền tố hexadecimal |
| `Integer.decode("#2A")` | `42` | Một dạng tiền tố hexadecimal khác |
| `Integer.parseInt("010")` | `10` | Hệ 10 mặc định |
| `Integer.decode("010")` | `8` | `decode` nhận số 0 dẫn đầu theo dạng octal |

`Integer.decode` còn hỗ trợ dạng có dấu đặt trước tiền tố, chẳng hạn `"-0x2A"`. Đừng dùng `decode` cho một chuỗi mã định danh có số 0 ở đầu nếu hợp đồng dữ liệu nói rằng mọi giá trị đều là thập phân; hành vi hệ 8 có thể rất bất ngờ.

Nếu input từ giao thức cho phép tiền tố `0x` và radix 16, bạn có thể phân tích tiền tố theo quy tắc của giao thức rồi gọi `parseInt` trên phần chữ số, hoặc dùng `decode` khi đúng tất cả cú pháp mà API ấy định nghĩa. Luôn kiểm tra *miền giá trị* sau khi đã xác định cú pháp/radix.

## 5. Parse số nguyên không dấu: kết quả bit có thể âm khi nhìn như signed

`Integer.parseUnsignedInt` cho phép phân tích miền không dấu 32-bit, từ 0 tới 4294967295. Nhưng kiểu trả về vẫn là **`int` 32-bit**, không có kiểu primitive `uint` riêng trong Java.

~~~java
int bits = Integer.parseUnsignedInt("4294967295");
System.out.println(bits);                       // -1 (in như int có dấu)
System.out.println(Integer.toUnsignedLong(bits)); // 4294967295
~~~

`bits == -1` ở đây không đồng nghĩa quá trình parse thất bại: toàn bộ bit của giá trị unsigned lớn nhất đều bằng 1, nên khi diễn giải dưới dạng signed `int` thì là -1. Dùng `Integer.toUnsignedLong` hoặc `Integer.toUnsignedString` để hiển thị theo ngữ nghĩa unsigned.

~~~java
// Integer.parseUnsignedInt("4294967296"); // NumberFormatException
// Integer.parseUnsignedInt("-1");         // NumberFormatException
~~~

Đối với `Long.parseUnsignedLong`, miền unsigned là 0 tới 18446744073709551615, nhưng kết quả vẫn có kiểu `long`; khi cần in dạng unsigned, dùng `Long.toUnsignedString`. Nếu cần giữ giá trị dương lớn hơn `Long.MAX_VALUE` như một số nguyên toán học có dấu dương trong đối tượng, `BigInteger` thường giúp diễn đạt ý định rõ hơn.

**Không sửa lỗi tràn `parseInt` bằng cách chuyển sang unsigned nếu miền dữ liệu thực sự là số nguyên có dấu.** Chọn đúng API theo đặc tả dữ liệu, không theo mong muốn tạm thời “parse cho qua”.

## 6. Khoảng trắng, normalizing và dữ liệu bên ngoài

`Integer.parseInt(" 42 ")` thất bại do chứa khoảng trắng. Nếu hợp đồng đầu vào cho phép bỏ khoảng trắng ở **đầu và cuối**, có thể làm rõ ở ranh giới nhập:

~~~java
String raw = " 42 ";
String normalized = raw.strip();
int parsed = Integer.parseInt(normalized); // 42
~~~

`strip` dựa trên `Character.isWhitespace`; `trim` và `strip` không nhận diện cùng tập ký tự. Không nên khẳng định một lệnh `strip` giải quyết được mọi kiểu khoảng trắng của mọi chuẩn dữ liệu.

**Chỉ normalize nếu đặc tả cho phép.** Với mã định danh, token chữ ký hoặc dữ liệu tài chính, loại bỏ dấu `+`, số 0 đầu, phân cách nhóm hoặc ký tự “trông giống nhau” có thể làm thay đổi ý nghĩa. Ví dụ, nếu hệ thống cần giữ mã `"0012"` là mã khác với `"12"`, việc chuyển sang `int` đã mất thông tin về số chữ số ban đầu.

Đừng thay `parseInt` bằng `replaceAll("[^0-9]", "")` để “chữa chuỗi bẩn”: biến `"-25"` thành `"25"`, `"1,234"` thành `"1234"` hoặc một giá trị có ký tự lạ thành số khác là **thay dữ liệu âm thầm**, dễ gây sai nghiệp vụ và tạo lỗ hổng kiểm tra.

Nếu dữ liệu chấp nhận định dạng bản địa hóa có dấu phân cách nhóm/thập phân, hãy xác định cú pháp bằng một parser thích hợp (`NumberFormat` có cơ chế phân tích và chính sách riêng). Lưu ý một số parser dạng locale có thể chấp nhận **một phần chuỗi**; cần kiểm tra vị trí parse kết thúc nếu hợp đồng đòi tiêu thụ toàn bộ đầu vào. `Integer.parseInt` bản thân đòi chuỗi hợp lệ toàn phần.

## 7. Kiểm tra lỗi đúng vị trí: không nuốt `NumberFormatException`

Giả sử một cột CSV phải chứa số lượng nguyên `int` dương. Cách xử lý ranh giới đầu vào có thể là:

~~~java
static int parsePositiveQuantity(String raw) {
    if (raw == null) {
        throw new IllegalArgumentException("quantity is required");
    }
    String normalized = raw.strip();
    try {
        int value = Integer.parseInt(normalized);
        if (value <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return value;
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
                "quantity must be a valid 32-bit integer", ex);
    }
}
~~~

Đây là **một phương thức minh họa**, cần đặt bên trong lớp Java; không phải chương trình độc lập. Cấu trúc trên phân biệt:

- **Thiếu trường:** `null` → lỗi “required” rõ ràng.
- **Sai cú pháp hoặc vượt miền:** `NumberFormatException` → chuyển thành lỗi validation có ngữ cảnh và giữ `cause` để chẩn đoán.
- **Đúng số nguyên nhưng sai quy tắc nghiệp vụ:** `0` hoặc giá trị âm → lỗi “must be positive”.

Không dùng một `catch (Exception)` quá rộng quanh cả luồng xử lý rồi mặc định trả 0. Khi nhận input không tin cậy, bạn có thể trả kết quả xác thực thành công/thất bại hoặc thông báo lỗi tương ứng cho người dùng; còn lỗi hệ thống thật phải được phân biệt.

Tránh hiển thị nguyên văn thông báo lỗi cho dữ liệu chứa thông tin nhạy cảm, token hoặc trường cực dài; hãy dùng thông báo ổn định như “Giá trị số nguyên không hợp lệ” và ghi log có kiểm soát. Văn bản `NumberFormatException.getMessage()` không phải giao thức máy đọc ổn định giữa mọi API/phiên bản JDK.

## 8. Tại sao pre-validation bằng regex không đủ?

Bạn có thể kiểm tra cú pháp ASCII decimal có dấu bằng biểu thức chính quy:

~~~java
static boolean looksLikeAsciiInteger(String text) {
    return text != null && text.matches("[+-]?[0-9]+");
}
~~~

Nhưng regex này **chỉ kiểm tra mẫu ký tự**, không kiểm tra miền kiểu số. Chuỗi `"999999999999999999999999999999"` vẫn vượt giới hạn `int`/`long` dù khớp regex.

Ngược lại, `parseInt` có thể nhận một số chữ số Unicode mà regex ASCII trên từ chối. Nếu hệ thống yêu cầu chuẩn ASCII chặt chẽ, đó là chủ ý có thể đúng. Nếu dữ liệu phải chấp nhận chữ số Unicode hợp lệ, chọn quy tắc xác thực khác.

**Nguyên tắc:** kiểm tra cú pháp theo hợp đồng dữ liệu trước (nếu thực sự cần), nhưng vẫn phải gọi parser phù hợp và **xử lý `NumberFormatException`** vì lỗi miền giá trị vẫn có thể xảy ra. Một lời gọi parse với `try/catch` hẹp thường đơn giản hơn việc tự viết bộ kiểm tra giá trị ở mọi vị trí.

## 9. Ví dụ Java 17+ hoàn chỉnh: phân biệt lỗi ký tự và lỗi miền

Lưu thành tệp `ParseFailuresDemo.java`:

~~~java
import java.math.BigInteger;

public class ParseFailuresDemo {
    static void checkInt(String input) {
        try {
            System.out.println("parseInt(" + String.valueOf(input)
                    + ") = " + Integer.parseInt(input));
        } catch (NumberFormatException ex) {
            System.out.println("parseInt(" + String.valueOf(input)
                    + ") = NumberFormatException");
        }
    }

    public static void main(String[] args) {
        checkInt("2147483647");
        checkInt("2147483648");
        checkInt("-2147483648");
        checkInt("-2147483649");
        checkInt("12x");
        checkInt("1_000");
        checkInt(" 42 ");
        checkInt("12.0");
        checkInt("");
        checkInt(null);
        checkInt("+42");
        checkInt("0x2A");

        System.out.println("binary 1010 = "
                + Integer.parseInt("1010", 2));
        try {
            Integer.parseInt("102", 2);
        } catch (NumberFormatException ex) {
            System.out.println("binary 102 = NumberFormatException");
        }
        System.out.println("hex 2A = "
                + Integer.parseInt("2A", 16));
        System.out.println("decode 0x2A = "
                + Integer.decode("0x2A"));
        System.out.println("parseInt 010 = "
                + Integer.parseInt("010"));
        System.out.println("decode 010 = "
                + Integer.decode("010"));

        int unsigned = Integer.parseUnsignedInt("4294967295");
        System.out.println("unsigned bits as int = " + unsigned);
        System.out.println("unsigned as long = "
                + Integer.toUnsignedLong(unsigned));
        try {
            Integer.parseUnsignedInt("4294967296");
        } catch (NumberFormatException ex) {
            System.out.println("unsigned 4294967296 = NumberFormatException");
        }
        try {
            Long.parseLong("9223372036854775808");
        } catch (NumberFormatException ex) {
            System.out.println("long overflow = NumberFormatException");
        }
        System.out.println("BigInteger = "
                + new BigInteger("9223372036854775808"));
        System.out.println("strip then parse = "
                + Integer.parseInt(" 42 ".strip()));
    }
}
~~~

Lệnh biên dịch/chạy:

~~~bash
javac ParseFailuresDemo.java
java ParseFailuresDemo
~~~

Stdout dự kiến (đã xử lý ngoại lệ trong chương trình nên không in stack trace):

~~~text
parseInt(2147483647) = 2147483647
parseInt(2147483648) = NumberFormatException
parseInt(-2147483648) = -2147483648
parseInt(-2147483649) = NumberFormatException
parseInt(12x) = NumberFormatException
parseInt(1_000) = NumberFormatException
parseInt( 42 ) = NumberFormatException
parseInt(12.0) = NumberFormatException
parseInt() = NumberFormatException
parseInt(null) = NumberFormatException
parseInt(+42) = 42
parseInt(0x2A) = NumberFormatException
binary 1010 = 10
binary 102 = NumberFormatException
hex 2A = 42
decode 0x2A = 42
parseInt 010 = 10
decode 010 = 8
unsigned bits as int = -1
unsigned as long = 4294967295
unsigned 4294967296 = NumberFormatException
long overflow = NumberFormatException
BigInteger = 9223372036854775808
strip then parse = 42
~~~

Các dòng đầu cho thấy parser **không phân biệt lỗi cú pháp với lỗi vượt miền bằng loại exception**: cả hai đều thuộc `NumberFormatException`. Caller có thể dựa vào hợp đồng dữ liệu để hiển thị lời nhắc phù hợp, nhưng không nên suy ra nguyên nhân bằng cách parse chuỗi thông báo ngoại lệ của JDK.

## 10. Khi không muốn exception làm luồng chương trình dừng

Có hai chiến lược phổ biến và đều cần thể hiện rõ mục đích:

**Chiến lược A — báo lỗi ngay (fail fast).** Dùng trong dữ liệu bắt buộc chính xác hoặc thông tin cấu hình hệ thống.

~~~java
static int requirePort(String value) {
    int port = Integer.parseInt(value);
    if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("port outside 1..65535");
    }
    return port;
}
~~~

Nếu chuỗi `value` sai hoặc vượt miền, lỗi vẫn là `NumberFormatException` ngay ở bước parse; nếu số `int` hợp lệ nhưng nằm ngoài miền cổng, ứng dụng ném `IllegalArgumentException`. Việc phân biệt này giúp sửa đúng nguồn lỗi.

**Chiến lược B — kết quả parse có thể thất bại.** Dùng khi dữ liệu đầu vào bình thường có thể thiếu/sai và caller phải tự quyết định.

~~~java
static java.util.OptionalInt tryParseInt(String raw) {
    if (raw == null) {
        return java.util.OptionalInt.empty();
    }
    try {
        return java.util.OptionalInt.of(Integer.parseInt(raw));
    } catch (NumberFormatException ex) {
        return java.util.OptionalInt.empty();
    }
}
~~~

Đây là phương thức minh họa trong một class. `OptionalInt.empty()` báo thất bại mà không giả vờ kết quả là 0. **Đổi lại, chiến lược này gộp “ký tự sai” và “vượt miền” thành một trạng thái thất bại**. Nếu giao diện cần chỉ ra lỗi chính xác, thiết kế một kiểu kết quả có thông tin lỗi hoặc xác thực đầu vào ở lớp ứng dụng.

Với luồng đọc hàng triệu giá trị, đừng dùng ngoại lệ lặp đi lặp lại như một bộ tokenizer thay cho thiết kế dữ liệu; hãy cân nhắc parser có hợp đồng trả trạng thái và đo hiệu năng thực tế. Dù tối ưu thế nào, vẫn phải kiểm tra miền biểu diễn.

## 11. Những nhầm lẫn cần tránh

1. **“Chuỗi toàn chữ số thì parse chắc chắn được.”** Sai: `2147483648` không chứa ký tự lạ nhưng vượt miền `int`.
2. **“`parseInt` sẽ tự overflow như phép cộng `int`.”** Sai: nó kiểm tra miền và ném `NumberFormatException`.
3. **“`parseInt` hiểu cú pháp literal Java.”** Sai: dấu gạch dưới, hậu tố `L` và tiền tố `0x` không tự hợp lệ trong `parseInt(text)`.
4. **“Tăng radix để đọc được `0xFF`.”** Không: `parseInt("0xFF", 16)` vẫn chứa `x` không hợp lệ; dùng `"FF"` cùng radix 16 hoặc `Integer.decode("0xFF")` nếu đúng hợp đồng.
5. **“`parseInt("010")` là bát phân.”** Sai: `parseInt` mặc định hệ 10, khác `Integer.decode`.
6. **“Dùng `strip` xong thì mọi ký tự ‘lạ’ đều hết.”** Sai: `strip` chỉ xử lý tập ký tự whitespace được nhận diện ở hai đầu.
7. **“Ép về `long`, `BigInteger` sẽ làm chuỗi có ký tự lỗi hợp lệ.”** Sai: kiểu lớn hơn giải quyết miền giá trị, không giải quyết sai cú pháp.
8. **“Số không dấu phải luôn in ra một số dương khi giữ trong `int`.”** Sai: bit unsigned lớn có thể hiện thành số âm dưới diễn giải signed.
9. **“Regex hợp lệ chứng minh không thể có NumberFormatException.”** Sai: regex cơ bản thường không kiểm tra giới hạn số.
10. **“Catch ngoại lệ rồi trả `0` là an toàn.”** Sai: làm mất khả năng phân biệt dữ liệu 0 hợp lệ và parse thất bại.
11. **“Thông báo `NumberFormatException` là giao diện API ổn định để phân loại nguyên nhân.”** Không nên parse message: nó có thể thay đổi và không phải contract nghiệp vụ.
12. **“Số 0 đứng đầu không có ý nghĩa.”** Với parse thành `int` bạn có thể đã làm mất ý nghĩa định dạng của mã định danh. Nếu cần giữ chuỗi như `0012`, lưu dưới dạng chuỗi thay vì ép sang số.

## 12. Checklist khi xử lý chuỗi số nguyên từ bên ngoài

1. Xác định **mục đích**: số lượng, ID, mã bưu chính, port hay bit unsigned? Không phải dữ liệu toàn chữ số nào cũng nên lưu dưới dạng số.
2. Định nghĩa **cú pháp đầu vào**: dấu cộng/trừ, cơ số, tiền tố `0x`, Unicode digit, khoảng trắng, ký tự nhóm số có được phép không?
3. Chọn **kiểu và miền**: `int`, `long`, unsigned hoặc `BigInteger`; nêu miền giá trị của nghiệp vụ nếu hẹp hơn kiểu.
4. Normalizing **chỉ theo hợp đồng**; không tự xóa mọi ký tự “không phải chữ số”.
5. Parse bằng API đúng, bắt `NumberFormatException` ở **ranh giới dữ liệu** nơi có đủ thông tin để xử lý.
6. Phân biệt lỗi **sai cú pháp**, **vượt miền kiểu**, **ngoài miền nghiệp vụ** và **thiếu dữ liệu** trong thông báo hoặc kết quả validation nếu cần.
7. Không che thất bại bằng `0` hoặc một số sentinel có thể là dữ liệu hợp lệ; log có kiểm soát, giữ nguyên đầu vào gốc khi cần chẩn đoán.

## 13. Tự kiểm tra

**Câu 1.** Vì sao `Integer.parseInt("2147483648")` ném `NumberFormatException` dù toàn ký tự đều là chữ số?

**Đáp án:** Giá trị toán học 2147483648 lớn hơn `Integer.MAX_VALUE`. Parser xác minh cả cú pháp và miền kiểu.

**Câu 2.** `Integer.parseInt("1_000")` có hợp lệ vì Java cho phép viết literal `1_000` không?

**Đáp án:** Không. Dấu gạch dưới là cú pháp literal trong source Java, không thuộc ngữ pháp chuỗi runtime của `parseInt`.

**Câu 3.** Với hệ nhị phân, `Integer.parseInt("102", 2)` lỗi vì điều gì? Cách đổi chuỗi `"1010"` sang thập phân?

**Đáp án:** Chữ số 2 không hợp lệ trong radix 2. `Integer.parseInt("1010", 2)` trả 10.

**Câu 4.** `Integer.parseInt("010")` và `Integer.decode("010")` có bằng nhau không?

**Đáp án:** Không: lần lượt trả 10 và 8 theo quy tắc cơ số của mỗi API.

**Câu 5.** Nếu `Integer.parseUnsignedInt("4294967295")` in `-1`, có phải parser sai không?

**Đáp án:** Không. Bit pattern unsigned 32-bit lớn nhất, khi đọc như signed `int` là -1. Dùng `Integer.toUnsignedLong` hoặc `Integer.toUnsignedString` để xem giá trị không dấu.

**Câu 6.** Một chuỗi số hợp lệ vượt cả `Long.MAX_VALUE` cần xử lý thế nào? Lúc nào `BigInteger` vẫn không giúp được?

**Đáp án:** Chọn `BigInteger` nếu nghiệp vụ cho phép số lớn tùy ý. `BigInteger` vẫn ném `NumberFormatException` khi chuỗi có cú pháp số nguyên không hợp lệ.

## Tài liệu tham khảo

- [Java SE 21 — Integer.parseInt, parseUnsignedInt và decode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html).
- [Java SE 21 — Long.parseLong và parseUnsignedLong](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Long.html).
- [Java SE 21 — NumberFormatException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/NumberFormatException.html).
- [Java SE 21 — BigInteger](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html).
- [Java Language Specification `3.10.1 — Integer Literals](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html#jls-3.10.1): phân biệt cú pháp literal trong source với parser chuỗi runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-008">Phân tích đoạn CharSequence mà không tạo String con khi API hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân tích đoạn CharSequence mà không tạo String con khi API hỗ trợ

> **Java core – numbers · Phân tích số nguyên**  
> **Mục tiêu:** Parse trực tiếp một đoạn ký tự trong `String`, `StringBuilder`, `StringBuffer` hay `CharBuffer` khi cần đọc `int`/`long` có dấu hoặc không dấu; xác định đúng chỉ số đầu/cuối và radix; tránh tạo `substring` không cần thiết; xử lý ngoại lệ, overflow và giới hạn hiệu năng một cách chính xác trong Java 17+.

## 1. Tình huống thực tế: chỉ cần một con số nằm giữa chuỗi dài

Giả sử bộ xử lý nhận một dòng:

```text
id=12345;delta=-42;flags=7F
```

Mục tiêu là đọc `12345` thành `int`, `-42` thành `int` và `7F` thành số nguyên hệ 16.

Cách nhiều người viết:

```java
String line = "id=12345;delta=-42;flags=7F";
int idStart = line.indexOf("id=") + 3;
int idEnd = line.indexOf(';', idStart);

int id = Integer.parseInt(line.substring(idStart, idEnd));
```

Cách này đúng với đầu vào đã được kiểm soát. Nhưng `substring(idStart, idEnd)` tạo một đối tượng `String` biểu diễn đoạn con, trong khi việc parse thực chất chỉ cần **đọc ký tự từ vị trí đầu đến vị trí cuối**.

Nếu API được gọi hỗ trợ trực tiếp một vùng `CharSequence`, có thể viết:

```java
int id = Integer.parseInt(line, idStart, idEnd, 10);
```

Cú pháp sau đọc ngay khoảng `[idStart, idEnd)` của chuỗi ban đầu và không yêu cầu chương trình tạo `String` con bằng `substring`. Đây là lợi thế đáng cân nhắc trong parser phải xử lý nhiều trường số hoặc lượng bản ghi lớn.

**Phân biệt quan trọng:** “Không tự tạo `String` con” **không đồng nghĩa** “toàn bộ thao tác được bảo đảm không cấp phát bộ nhớ” hay “chắc chắn nhanh hơn trong mọi trường hợp”. Mục tiêu là tránh một bước chuyển đổi trung gian **không cần thiết**; hiệu năng thực tế còn phụ thuộc vào đầu vào, JVM, JIT, xử lý lỗi và cách xác định các chỉ số.

## 2. CharSequence là gì và vì sao có thể parse trực tiếp?

`CharSequence` là interface đại diện cho một **dãy ký tự có thể truy cập bằng chỉ số**. Một số kiểu quen thuộc triển khai interface này:

| Kiểu | Đặc điểm thường gặp | Ý nghĩa khi parse |
| --- | --- | --- |
| `String` | Chuỗi bất biến | Có thể parse một vùng trực tiếp, không gọi `substring` |
| `StringBuilder` | Bộ đệm ký tự có thể thay đổi | Parse trực tiếp dữ liệu đang tích lũy, không cần `toString()` |
| `StringBuffer` | Bộ đệm ký tự có thể thay đổi với cơ chế đồng bộ của lớp | Cũng truyền được như `CharSequence`; phải xét việc sửa dữ liệu đồng thời |
| `CharBuffer` | Vùng truy cập ký tự dạng buffer | Có thể parse từ view của buffer nếu chỉ số phù hợp |
| Implement custom `CharSequence` | Quy tắc truy cập do lớp tự định nghĩa | Cần đảm bảo `length()`, `charAt()` và dữ liệu nhất quán |

Các phương thức cơ bản của interface gồm `length()`, `charAt(index)` và `subSequence(start,end)`. Overload parse theo vùng có thể truy cập ký tự trực tiếp qua interface mà người gọi **không phải** lấy một `String` con để chuyển vào API chỉ nhận `String`.

Tuy nhiên, `CharSequence` có thể là **mutable**. Tài liệu JDK nêu rõ các overload parse theo vùng **không có biện pháp tự bảo vệ** khi dãy ký tự bị sửa trong lúc đang parse. Vì vậy, các bộ đệm chia sẻ giữa nhiều luồng cần kiểm soát việc thay đổi dữ liệu theo hợp đồng đồng bộ thích hợp; không giả định API parser sẽ tự tạo bản sao an toàn.

## 3. Bốn overload phù hợp trong Integer và Long

Java 9 bổ sung các overload parse một khoảng `CharSequence`; tất cả đều có trong Java 17+:

| Phương thức | Loại số | Kiểu trả về |
| --- | --- | --- |
| `Integer.parseInt(CharSequence, int, int, int)` | `int` có dấu | `int` |
| `Integer.parseUnsignedInt(CharSequence, int, int, int)` | 32 bit diễn giải không dấu | `int` (cùng 32 bit) |
| `Long.parseLong(CharSequence, int, int, int)` | `long` có dấu | `long` |
| `Long.parseUnsignedLong(CharSequence, int, int, int)` | 64 bit diễn giải không dấu | `long` (cùng 64 bit) |

Bốn tham số cùng một thứ tự:

```java
Integer.parseInt(sequence, beginIndex, endIndex, radix);
Long.parseLong(sequence, beginIndex, endIndex, radix);
Integer.parseUnsignedInt(sequence, beginIndex, endIndex, radix);
Long.parseUnsignedLong(sequence, beginIndex, endIndex, radix);
```

- `sequence`: dãy ký tự gốc; có thể là `StringBuilder` chứ không nhất thiết là `String`.
- `beginIndex`: chỉ số **bắt đầu, có tính vào đoạn** (*inclusive*).
- `endIndex`: chỉ số **kết thúc, không tính vào đoạn** (*exclusive*).
- `radix`: cơ số dùng để diễn giải chữ số; Java hỗ trợ từ `Character.MIN_RADIX = 2` đến `Character.MAX_RADIX = 36`.

Không có overload ba tham số `(CharSequence, begin, end)` tự mặc định radix 10 trong nhóm trên. Khi parse theo vùng, hãy truyền **đủ bốn tham số** và viết `10` cho số thập phân.

**Lưu ý loại API:** Các phương thức `Byte.parseByte`, `Short.parseShort`, `Integer.valueOf`, `Long.valueOf` hoặc constructor `BigInteger` **không tự có cùng overload bốn tham số** chỉ vì `Integer.parseInt` có. Phải kiểm tra chữ ký chính xác của lớp/phương thức trước khi chuyển đổi code.

## 4. Chỉ số [beginIndex, endIndex): ví dụ từng vị trí

Ví dụ:

```text
Chuỗi:  id=12345;delta=-42
Index:  0123456789...
        ^  ^    ^
        |  |    |
        |  3    8
        0

id=       ở [0,3)
12345     ở [3,8)
;         ở index 8
```

Mã Java:

```java
StringBuilder line = new StringBuilder("id=12345;delta=-42");
int from = 3;
int to = 8;

int id = Integer.parseInt(line, from, to, 10);
System.out.println(id); // 12345
```

Với `endIndex` loại trừ, dấu chấm phẩy tại vị trí 8 **không** bị đưa vào parser. Nếu vô tình gọi `Integer.parseInt(line, 3, 9, 10)`, đoạn sẽ bao gồm `"12345;"` và việc parse thất bại vì `;` không phải chữ số thập phân.

### Quy tắc xác định chỉ số

Một khoảng hợp lệ có:

\[
0 \le beginIndex \le endIndex \le sequence.length()
\]

- `beginIndex == endIndex`: khoảng rỗng; chỉ số **hợp lệ**, nhưng không có chữ số để parse nên `NumberFormatException`.
- `beginIndex < 0`: phạm vi không hợp lệ, `IndexOutOfBoundsException`.
- `beginIndex > endIndex`: phạm vi không hợp lệ, `IndexOutOfBoundsException`.
- `endIndex > sequence.length()`: phạm vi không hợp lệ, `IndexOutOfBoundsException`.

Đừng trộn chỉ số từ **mảng byte UTF-8**, offset của token hay số lượng **Unicode code point** với chỉ số được dùng bởi `CharSequence.charAt`. Trong các chuỗi Java thông dụng, các chỉ số này tính theo **đơn vị mã UTF-16**; ký tự ngoài BMP có thể chiếm hai đơn vị `char`. Với token số ASCII, mỗi chữ số chiếm một `char`, nhưng vị trí đầu của token vẫn phải lấy từ đúng nguồn `CharSequence` đang parse.

## 5. Parse trực tiếp từ StringBuilder: không cần toString hoặc substring

Khi một dòng đang được xây bằng `StringBuilder`, cách cũ có thể là:

```java
StringBuilder buffer = new StringBuilder("count=12345;");
int start = 6;
int end = 11;

// Tạo chuỗi từ đoạn rồi parse:
// int count = Integer.parseInt(buffer.substring(start, end));
```

Cách trực tiếp:

```java
int count = Integer.parseInt(buffer, start, end, 10);
System.out.println(count); // 12345
```

**Không cần viết** `buffer.toString().substring(start,end)`. Cách đó tạo một `String` toàn bộ rồi lại tạo chuỗi đoạn con — không đạt mục tiêu tận dụng overload theo vùng.

Không cần gọi `subSequence(start,end)` chỉ để đưa cho parser; overload đã nhận trực tiếp các chỉ số và `CharSequence`. Với kiểu `CharSequence` custom hoặc view đặc biệt, việc gọi `subSequence` có thể có đặc tính cấp phát riêng; hãy dựa vào contract triển khai thay vì đoán.

## 6. Signed int và long: dấu thuộc chính khoảng được parse

Các chuỗi số thập phân có thể có dấu ASCII `+` hoặc `-` **ở đầu đoạn được parse**:

```java
StringBuilder text = new StringBuilder("delta=-42;next=+17");

int delta = Integer.parseInt(text, 6, 9, 10); // -42
int next = Integer.parseInt(text, 15, 18, 10); // +17
```

Tại sao phải chú ý khoảng? Ví dụ nếu `beginIndex` bỏ qua dấu `-` thì parse ra giá trị **42**, sai ý nghĩa bản ghi, dù Java không ném ngoại lệ:

```java
int wrongMeaning = Integer.parseInt(text, 7, 9, 10); // 42
```

Nếu chỉ có `+` hoặc `-` mà không có chữ số, parse thất bại với `NumberFormatException`. Dấu ở **giữa** các chữ số cũng không hợp lệ.

Giới hạn miền:

```java
StringBuilder minimum = new StringBuilder("value=-2147483648");
int min = Integer.parseInt(minimum, 6, minimum.length(), 10);
System.out.println(min == Integer.MIN_VALUE); // true
```

Đây là giá trị biên âm của `int`. Nếu chuỗi số vượt miền từ `Integer.MIN_VALUE` đến `Integer.MAX_VALUE` thì parser **từ chối**, không làm tràn âm thầm như phép ép kiểu nguyên thu hẹp.

Với `long`:

```java
StringBuilder text = new StringBuilder("n=-9223372036854775808");
long min = Long.parseLong(text, 2, text.length(), 10);
System.out.println(min == Long.MIN_VALUE); // true
```

Nếu cần parse số nguyên lớn hơn miền `long`, sử dụng chiến lược số nguyên độ chính xác tùy ý thích hợp như `BigInteger`, nhưng **không có cùng overload phạm vi** trong `BigInteger` tương ứng với bốn phương thức này.

## 7. Cơ số khác 10: hexadecimal, binary và dấu

Truyền `radix` rõ ràng:

```java
StringBuilder line = new StringBuilder("hex=7F;bits=101011");

int hex = Integer.parseInt(line, 4, 6, 16);  // 127
int bits = Integer.parseInt(line, 12, 18, 2); // 43
```

Các ký tự được chấp nhận phải là chữ số hợp lệ theo `Character.digit(char, radix)`, ngoài dấu ASCII đầu đoạn trong signed parser. Điều này có nghĩa parser được JDK hỗ trợ cho nhiều radix và có thể chấp nhận một số chữ số Unicode, chứ **không tự giới hạn toàn bộ token về ASCII**.

Nếu giao thức của bạn yêu cầu **chỉ ký tự ASCII**, hãy validate quy tắc đó riêng. Không dùng việc `parseInt` thành công làm bằng chứng token hoàn toàn tuân thủ bộ ký tự ASCII của một giao thức.

### Không tự nhận tiền tố 0x

Với radix 16, đoạn được parse phải là các chữ số theo cơ số 16 (có thể có dấu đầu hợp lệ), ví dụ `"FF"` hoặc `"-FF"`, không tự cho phép chuỗi có tiền tố `"0x"`:

```java
StringBuilder s = new StringBuilder("value=0xFF");
// Integer.parseInt(s, 6, 10, 16); // NumberFormatException
int value = Integer.parseInt(s, 8, 10, 16); // 255
```

Ở đây `[8,10)` bỏ qua tiền tố `0x`. Khi một chuẩn giao thức cho phép nhiều prefix, bạn cần xác định/loại prefix ở bước phân tích token.

`Integer.decode(String)` hỗ trợ quy tắc prefix riêng, nhưng **không cung cấp overload `(CharSequence,begin,end,radix)`**. Nếu chọn cách parse vùng, người viết parser tự quyết định radix và phạm vi chữ số.

### Dấu gạch dưới và hậu tố L không phải cú pháp chuỗi số

`1_000` và `123L` có thể có ý nghĩa trong *numeric literal của mã nguồn Java* (tùy vị trí), nhưng không có nghĩa `Integer.parseInt`/`Long.parseLong` chấp nhận dấu `_` hoặc hậu tố `L` trong chuỗi decimal thông thường:

```java
// Integer.parseInt(new StringBuilder("1_000"), 0, 5, 10); // NFE
// Long.parseLong(new StringBuilder("123L"), 0, 4, 10);     // NFE
```

Hãy định nghĩa rõ cú pháp **dữ liệu runtime** thay vì lấy nguyên quy tắc numeric literal của mã nguồn Java.

## 8. Unsigned parse: số lớn hơn MAX_VALUE có thể hợp lệ theo 32/64 bit

Hai overload không dấu:

```java
Integer.parseUnsignedInt(seq, from, to, radix);
Long.parseUnsignedLong(seq, from, to, radix);
```

cho phép đọc các giá trị từ `0` đến `2^32−1` hoặc `0` đến `2^64−1`. Nhưng Java vẫn dùng kiểu kết quả là **`int` 32 bit** hoặc **`long` 64 bit**, không có primitive unsigned riêng.

Ví dụ giá trị lớn nhất:

```java
StringBuilder u32Text = new StringBuilder("u=4294967295");
int bits32 = Integer.parseUnsignedInt(
        u32Text, 2, u32Text.length(), 10);

System.out.println(bits32);                      // -1
System.out.println(Integer.toUnsignedString(bits32)); // 4294967295
```

Lưu ý `System.out.println(bits32)` đọc các bit theo signed `int` nên in `-1`; điều đó **không chứng tỏ parser không dấu bị lỗi**. Cùng mẫu `0xFFFFFFFF` có nghĩa `4294967295` theo unsigned và `-1` theo signed.

Với 64 bit:

```java
StringBuilder u64Text =
        new StringBuilder("u=18446744073709551615");

long bits64 = Long.parseUnsignedLong(
        u64Text, 2, u64Text.length(), 10);

System.out.println(bits64);                    // -1
System.out.println(Long.toUnsignedString(bits64));
// 18446744073709551615
```

Unsigned parser không nhận số âm như một signed integer âm: dấu `-` ở đầu đoạn không phải giá trị unsigned hợp lệ. Dấu cộng ASCII đầu đoạn có thể được chấp nhận, nhưng đừng coi token `"-1"` là cách viết thay thế cho unsigned max.

Khi xử lý bit-pattern unsigned, các API `Integer.compareUnsigned`, `Long.compareUnsigned`, `Integer.divideUnsigned` và tương đương có thể cần thiết. **Đừng dùng so sánh signed `bits32 < 0` để kết luận giá trị unsigned là âm.**

## 9. Cơ chế xử lý lỗi: ba loại ngoại lệ cần tách biệt

Với overload `parseInt(CharSequence,int,int,int)` và các overload tương ứng của `Long`:

| Trường hợp | Ngoại lệ thường được xác định bởi API | Ví dụ |
| --- | --- | --- |
| `CharSequence` bằng `null` | `NullPointerException` | `Integer.parseInt(null,0,1,10)` |
| Chỉ số ngoài phạm vi hoặc đảo thứ tự | `IndexOutOfBoundsException` | `beginIndex=-1` |
| Khoảng rỗng, ký tự sai, radix sai, giá trị vượt miền | `NumberFormatException` | `[0,0)`; `"12x"`; radix 1; `"2147483648"` khi đọc signed int |

**Đặc biệt:** overloaded method dùng `String` khác overload dùng `CharSequence` về cách tài liệu quy định trường hợp `null`. Không đồng nhất chúng nếu API của bạn cần chuyển exception thành lỗi có mã riêng.

Một ví dụ minh họa:

```java
StringBuilder buffer = new StringBuilder("x=12A");

try {
    Integer.parseInt(buffer, 2, 5, 10);
} catch (NumberFormatException ex) {
    System.out.println("Invalid decimal token");
}
```

Nếu cả biên token lẫn dữ liệu có thể sai, hãy cân nhắc:

- Kiểm tra `beginIndex` và `endIndex` riêng để phát hiện parser nội bộ xác định sai token.
- Xử lý `NumberFormatException` như **dữ liệu số không hợp lệ** hoặc **ngoài miền**.
- Xử lý `NullPointerException` như thiếu dãy ký tự đầu vào nếu nghiệp vụ cho phép nguồn null.
- Không bắt mọi `RuntimeException` rồi trả 0: lỗi parser và số 0 thực là hai tình huống không nên trộn làm một.

Tránh in toàn bộ chuỗi chứa dữ liệu nhạy cảm vào log lỗi. Có thể ghi tên trường, chỉ số hoặc thông điệp khái quát thay vì log nguyên bản ghi.

## 10. Có cần trim khoảng trắng không?

Parser số nguyên không tự cắt khoảng trắng bao quanh token:

```java
StringBuilder data = new StringBuilder("n= 42 ");
 // Integer.parseInt(data, 2, data.length(), 10); // NFE
```

Nếu định dạng dữ liệu cho phép khoảng trắng ở hai đầu, ta có thể **dịch hai biên** trước khi parse mà không tạo `String` con.

Ví dụ cho khoảng trắng ASCII đơn giản:

```java
static int parseTrimmedAscii(CharSequence text, int from, int to) {
    while (from < to && text.charAt(from) == ' ') {
        from++;
    }
    while (to > from && text.charAt(to - 1) == ' ') {
        to--;
    }
    return Integer.parseInt(text, from, to, 10);
}
```

Đây là **phương thức minh họa**, chưa tự validate tất cả chỉ số; giả định caller truyền `0 <= from <= to <= text.length()`. Nó chỉ loại khoảng trắng ASCII `' '` theo quy ước cụ thể, không tự xử lý mọi Unicode whitespace. Nếu hợp đồng cho phép tab, CR/LF hoặc khoảng trắng Unicode khác, hãy bổ sung chính sách tường minh, ví dụ kiểm tra code point hoặc hàm phù hợp.

**Không tự ý trim** nếu giao thức yêu cầu token không có khoảng trắng; trong parser chính xác, việc nhận token sai có thể nghiêm trọng hơn tiết kiệm một lần cấp phát.

## 11. Không thay Parse API bằng một bộ parser thủ công nếu chưa cần

Một vòng lặp tính số trực tiếp qua `charAt` có vẻ đơn giản:

```java
int result = 0;
for (int i = from; i < to; i++) {
    result = result * 10 + (text.charAt(i) - '0');
}
```

Nhưng đoạn code minh họa này **không an toàn để đưa vào production**:

- không kiểm tra mọi ký tự có nằm trong `'0'..'9'` hay không;
- không hỗ trợ dấu `+`/`-`;
- không kiểm tra overflow khi nhân 10 và cộng chữ số;
- không kiểm tra trường hợp rỗng hoặc radix khác;
- không thực hiện quy tắc về `Character.digit` theo API Java;
- có thể chấp nhận đầu vào sai rồi âm thầm trả số không đáng tin.

Bốn overload có sẵn xử lý đúng ngữ nghĩa của parser chuẩn. Chỉ tự viết parser tối ưu chuyên biệt khi có **hợp đồng dữ liệu rõ ràng**, test overflow và đo benchmark trên workload cụ thể.

Nếu dữ liệu nằm trong `byte[]` UTF-8 thay vì `CharSequence`, các overload này **không parse trực tiếp byte array**. Khi đó cần decode sang ký tự hoặc dùng parser byte chuyên dụng đã kiểm định; không ép kiểu giả để truyền `byte[]` vào `CharSequence`.

## 12. Hiệu năng: giảm String trung gian nhưng không hứa zero allocation

So sánh về ý tưởng:

```java
StringBuilder builder = new StringBuilder("qty=12345;");
int from = 4, to = 9;

// Cách có chuỗi trung gian:
int a = Integer.parseInt(builder.substring(from, to));

// Cách dùng đoạn của CharSequence:
int b = Integer.parseInt(builder, from, to, 10);
```

Cả hai trả `12345`. Cách thứ hai **không yêu cầu gọi `substring`**; phương thức nhận trực tiếp dãy ký tự và biên. Điều này có thể tránh tạo đối tượng chuỗi con từ code gọi và hỗ trợ xử lý lặp trong parser.

Tuy vậy:

1. API không cam kết một số lượng cấp phát cụ thể dưới mọi implementation hoặc nhánh lỗi.
2. JIT có thể tối ưu các đối tượng trung gian trong một số workload; bản cũ chưa chắc luôn chậm hơn.
3. Việc tìm delimiter bằng `indexOf`/scan vẫn có chi phí và thuật toán có thể dominate thời gian parse.
4. Parser chạy trên `StringBuilder` mutable chia sẻ giữa threads cần bảo đảm tính nhất quán; “ít copy” không đồng nghĩa “thread-safe”.
5. Việc tạo `String` con đôi khi lại hợp lý khi cần giữ token lâu dài sau khi buffer gốc bị ghi đè hoặc tái sử dụng.

Muốn biết cải thiện bao nhiêu, dùng **JMH** hoặc benchmark Java được thiết kế đúng, kiểm tra allocation rate và throughput với dữ liệu thực. Tránh tự đo một vòng lặp nhỏ bằng `System.nanoTime()` rồi kết luận tuyệt đối vì dead-code elimination, warming, GC và JIT có thể làm kết quả sai lệch.

## 13. Xác định đúng phạm vi token: nội dung ngoài đoạn vẫn còn nguyên

Parser theo vùng chỉ đọc phạm vi bạn truyền. Ví dụ:

```java
StringBuilder line = new StringBuilder("A=123;B=oops;");
int n = Integer.parseInt(line, 2, 5, 10);
System.out.println(n); // 123
```

Chuỗi `"oops"` ở bên ngoài đoạn **không cần là số hợp lệ** vì parser không đọc tới đó. Đây là tính chất hữu ích khi có nhiều trường khác loại trong cùng bản ghi.

Ngược lại, nếu vị trí biên được tính sai, parser có thể:

- lấy cả dấu phân cách và ném `NumberFormatException`;
- bỏ dấu âm và đọc thành số dương sai về ngữ nghĩa;
- lấy sang ký tự trường kế tiếp;
- parse một đoạn chữ số **hợp lệ nhưng không phải token intended**, nguy hiểm hơn lỗi parse rõ ràng.

Vì vậy phần **tokenization** (tìm vị trí bắt đầu/kết thúc trường) phải được thiết kế và kiểm thử độc lập với phần **numeric conversion** (đổi token hợp lệ thành số). Một overload parse tốt không thay thế được parser của cả giao thức.

## 14. Chương trình Java 17+ hoàn chỉnh

Tạo tệp **`CharSequenceRangeParsingDemo.java`**:

```java
public class CharSequenceRangeParsingDemo {

    private static int beginAfter(StringBuilder source, String field) {
        int keyIndex = source.indexOf(field);
        if (keyIndex < 0) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return keyIndex + field.length();
    }

    private static int endOfField(StringBuilder source, int begin) {
        int separator = source.indexOf(";", begin);
        return separator >= 0 ? separator : source.length();
    }

    public static void main(String[] args) {
        StringBuilder record = new StringBuilder(
                "id=12345;delta=-42;hex=7F;u32=4294967295;"
              + "u64=18446744073709551615");

        int start = beginAfter(record, "id=");
        int end = endOfField(record, start);
        int id = Integer.parseInt(record, start, end, 10);
        System.out.println("id = " + id);

        start = beginAfter(record, "delta=");
        end = endOfField(record, start);
        int delta = Integer.parseInt(record, start, end, 10);
        System.out.println("delta = " + delta);

        start = beginAfter(record, "hex=");
        end = endOfField(record, start);
        int hex = Integer.parseInt(record, start, end, 16);
        System.out.println("hex = " + hex);

        start = beginAfter(record, "u32=");
        end = endOfField(record, start);
        int u32 = Integer.parseUnsignedInt(record, start, end, 10);
        System.out.println("u32 signed view = " + u32);
        System.out.println("u32 unsigned view = "
                + Integer.toUnsignedString(u32));

        start = beginAfter(record, "u64=");
        end = endOfField(record, start);
        long u64 = Long.parseUnsignedLong(record, start, end, 10);
        System.out.println("u64 signed view = " + u64);
        System.out.println("u64 unsigned view = "
                + Long.toUnsignedString(u64));

        StringBuilder signedLong =
                new StringBuilder("n=-9223372036854775808");
        long min = Long.parseLong(
                signedLong, 2, signedLong.length(), 10);
        System.out.println("long minimum = " + min);

        try {
            Integer.parseInt(new StringBuilder("x=12A"), 2, 5, 10);
        } catch (NumberFormatException ex) {
            System.out.println("bad digits: rejected");
        }

        try {
            Integer.parseInt(new StringBuilder("x=1"), -1, 3, 10);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("bad range: rejected");
        }

        try {
            Integer.parseInt(
                    new StringBuilder("n=2147483648"), 2, 12, 10);
        } catch (NumberFormatException ex) {
            System.out.println("signed int overflow: rejected");
        }

        StringBuilder extra = new StringBuilder("A=123;B=oops;");
        int first = Integer.parseInt(extra, 2, 5, 10);
        System.out.println("first field = " + first);
    }
}
```

Lưu tệp bằng đúng tên lớp, sau đó biên dịch và chạy:

```bash
javac CharSequenceRangeParsingDemo.java
java CharSequenceRangeParsingDemo
```

**Kết quả chuẩn:**

```text
id = 12345
delta = -42
hex = 127
u32 signed view = -1
u32 unsigned view = 4294967295
u64 signed view = -1
u64 unsigned view = 18446744073709551615
long minimum = -9223372036854775808
bad digits: rejected
bad range: rejected
signed int overflow: rejected
first field = 123
```

**Phân tích:**

- `id`, `delta`, `hex` được parse từ các vùng trong một `StringBuilder` mà không tạo `String` con cho từng trường số.
- `u32`, `u64` minh họa cùng bit pattern có thể in ra `-1` khi đọc signed, nhưng có giá trị unsigned max khi dùng `toUnsignedString`.
- `Long.parseLong` chấp nhận `Long.MIN_VALUE` chính xác, không yêu cầu đổi qua `double` hay `BigInteger`.
- Ký tự không hợp lệ gây `NumberFormatException`; biên chỉ số sai gây `IndexOutOfBoundsException`; signed overflow cũng gây `NumberFormatException`.
- Trường `B=oops` nằm ngoài phạm vi parse của trường `A` nên không ảnh hưởng đến kết quả `123`.

**Phạm vi minh họa:** Hàm `beginAfter` ở ví dụ dùng `StringBuilder.indexOf` để đơn giản hóa. Nó **không phải parser key/value production hoàn chỉnh**: nếu tên trường bị lặp, trùng một phần hoặc xuất hiện bên trong giá trị khác, cách tìm đơn thuần có thể chọn sai. Ứng dụng thực tế phải token hóa theo delimiter và kiểm tra tên trường đầy đủ.

## 15. Lỗi phổ biến khi tối ưu parse số nguyên

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`parseInt` chỉ nhận String” | Java 9+ có overload nhận `CharSequence` và phạm vi |
| “Chỉ cần truyền `sequence, from, to`” | Overload này yêu cầu thêm `radix` |
| “`endIndex` là vị trí ký tự cuối cùng” | `endIndex` là **exclusive**, không bao gồm ký tự đó |
| “`beginIndex == endIndex` là chỉ số sai” | Phạm vi rỗng có thể hợp lệ về chỉ số nhưng không parse được số |
| “Parser tự bỏ khoảng trắng” | Phải loại bỏ hoặc từ chối theo đặc tả của dữ liệu |
| “`radix=16` tự hiểu tiền tố 0x” | Không; phải xác định prefix và phạm vi chữ số riêng |
| “Chuỗi numeric runtime hiểu dấu gạch dưới như Java literal” | `"1_000"` thường không được parse bằng overload này |
| “Unsigned parse sẽ trả số dương với kiểu int/long” | Vẫn trả bit pattern của `int`/`long`, có thể in ra giá trị signed âm |
| “StringBuilder truyền vào sẽ được parser snapshot an toàn” | JDK không tự bảo vệ khi CharSequence bị sửa trong lúc parse |
| “Không gọi substring là zero allocation” | Chỉ tránh bước tạo chuỗi con ở phía caller; không bảo đảm cấp phát bằng 0 |
| “Bắt NumberFormatException đủ cho mọi lỗi” | Null và chỉ số sai là các loại ngoại lệ riêng |
| “Tính đúng token tự đến từ parser số” | Vẫn phải xác định begin/end đúng với giao thức |
| “Nhận thành công nghĩa là token chỉ gồm ASCII” | JDK có quy tắc `Character.digit`, có thể chấp nhận một số chữ số ngoài ASCII |
| “Mọi lớp wrapper parse đều có overload giống nhau” | Chữ ký của từng API khác nhau; kiểm tra Javadoc |

## 16. Bài tập tự kiểm tra

**Bài 1.** Với `StringBuilder s = new StringBuilder("x=987;y=1");`, viết lệnh parse `987` không tạo `String` con.

**Bài 2.** `Integer.parseInt(s, 2, 5, 10)` đọc những chỉ số nào?

**Bài 3.** Với `StringBuilder s = new StringBuilder("n=-42;");`, kết quả của `Integer.parseInt(s, 2, 5, 10)` và `Integer.parseInt(s, 3, 5, 10)` lần lượt là gì?

**Bài 4.** Vì sao `Integer.parseInt(new StringBuilder("h=0xFF"), 2, 6, 16)` thất bại? Cách sửa không tạo `String` con?

**Bài 5.** Với `StringBuilder s = new StringBuilder("u=4294967295");`, `Integer.parseUnsignedInt(s, 2, s.length(), 10)` trả kiểu gì, và in ra thế nào để được `4294967295`?

**Bài 6.** Một token có dấu cách đầu/cuối. Có thể gọi trực tiếp `parseInt` trên toàn phạm vi rồi mong parser tự trim không?

**Bài 7.** Trường hợp nào là `IndexOutOfBoundsException`: `begin=-1` hay đoạn hợp lệ nhưng chứa chữ `A` khi radix 10? Trường hợp còn lại là lỗi gì?

**Bài 8.** Vì sao dùng `Integer.parseInt(builder,from,to,10)` chưa tự chứng minh chương trình chạy nhanh hơn phiên bản dùng `substring`?

**Bài 9.** `Long.parseUnsignedLong` đọc `18446744073709551615` thành `long` có mẫu bit nào? Khi in thông thường và khi dùng `Long.toUnsignedString` khác thế nào?

**Bài 10.** Có an toàn nếu thread A sửa `StringBuilder` trong lúc thread B gọi `Integer.parseInt(builder,...)` không?

### Đáp án và giải thích

1. `Integer.parseInt(s, 2, 5, 10)`. Phạm vi `[2,5)` là `987`.
2. Đọc chỉ số `2, 3, 4`; không đọc ký tự tại chỉ số 5.
3. Lần lượt `-42` và `42`; phiên bản bắt đầu ở chỉ số 3 đã bỏ dấu `-`, gây sai ý nghĩa.
4. Token `"0xFF"` chứa `x` không phải digit radix 16; sửa bằng `Integer.parseInt(s, 4, 6, 16)` để đọc `FF`.
5. Trả primitive `int` với 32 bit đều bằng 1, khi in signed là `-1`; dùng `Integer.toUnsignedString(result)` để in `4294967295`.
6. Không. Cần xác định chính sách có chấp nhận whitespace không; nếu có thì điều chỉnh biên trước khi parse.
7. `begin=-1` gây `IndexOutOfBoundsException`; ký tự `A` không hợp radix 10 gây `NumberFormatException`.
8. JIT, cách xác định token, xử lý lỗi và đặc tính dữ liệu đều ảnh hưởng. Phương pháp theo vùng giảm yêu cầu tạo `String` con tại caller, nhưng muốn kết luận hiệu năng phải benchmark phù hợp.
9. Mẫu 64 bit đều là 1, giá trị signed `long=-1`; `Long.toUnsignedString(result)` in `18446744073709551615`.
10. Không có bảo đảm snapshot/nhất quán do parser tự cung cấp. Cần đồng bộ, không sửa trong quá trình parse hoặc chuyển quyền sở hữu bộ đệm theo giao thức phù hợp.

## 17. Checklist trước khi dùng overload CharSequence

1. **Xác định đầu vào thực tế**: `String`, `StringBuilder`, `StringBuffer`, `CharBuffer` hay một implementation custom?
2. **Chọn đúng API**: `Integer` hay `Long`, signed hay unsigned? Có đúng overload `(CharSequence,begin,end,radix)` không?
3. **Giữ đúng biên**: `0 <= begin <= end <= length`, `end` exclusive, dấu âm/dương đầu token không bị bỏ.
4. **Chỉ số tính theo ký tự của nguồn**: đừng truyền byte offset của UTF-8 vào `CharSequence`.
5. **Chọn radix và chính sách prefix** rõ ràng; không mặc định parser radix 16 chấp nhận `0x`.
6. **Định nghĩa whitespace và ký tự hợp lệ**: cần ASCII-only hay chấp nhận các digit Unicode theo `Character.digit`?
7. **Rà soát miền số**: `int`, `long` có dấu/không dấu; giá trị ngoài miền là lỗi parse, không âm thầm wrap khi parse signed.
8. **Xử lý exception**: null, chỉ số sai, dữ liệu/radix/overflow sai là các loại lỗi khác nhau.
9. **Bảo đảm tính nhất quán**: tránh sửa mutable `CharSequence` đồng thời khi parse.
10. **Kiểm chứng hiệu năng** bằng benchmark nếu đây là tối ưu hot path; không hứa zero allocation hoặc lợi ích tuyệt đối.
11. **Giữ token khi cần**: nếu logic phải lưu lại giá trị văn bản sau khi buffer tái sử dụng, copy token theo yêu cầu vòng đời dữ liệu là hợp lý.
12. **Kiểm thử dữ liệu biên**: chuỗi rỗng, dấu không chữ số, ±MAX/MIN, unsigned max, token có dấu phân cách, prefix, khoảng trắng, radix lỗi và chỉ số ngoài phạm vi.

## Tài liệu API

- [Java 17 – Integer: parseInt và parseUnsignedInt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html)
- [Java 17 – Long: parseLong và parseUnsignedLong](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html)

**Ghi nhớ:** Khi đã có `CharSequence` và biết `beginIndex/endIndex` của một trường số, hãy ưu tiên overload parse trực tiếp trên **đoạn ký tự** nếu API hỗ trợ. Cách này giúp tránh tạo `String` con không cần thiết, nhưng vẫn phải đảm bảo **đúng chỉ số, radix, miền giá trị, signedness, tính nhất quán của dữ liệu và ngữ nghĩa lỗi**. Tiết kiệm cấp phát chỉ có ích khi giá trị được phân tích **đúng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-009">Integer.getInteger và Long.getLong đọc system property, không phải parse thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Integer.getInteger và Long.getLong: đọc system property, không phải parse thông thường

## 1. Điểm dễ nhầm nhất

Tên `Integer.getInteger(String)` và `Long.getLong(String)` khiến nhiều người nghĩ chúng là cách chuyển một chuỗi số thành `Integer` hay `Long`. **Đó là cách hiểu sai.**

Hai phương thức này nhận **tên của một Java system property**; chúng tìm **giá trị chuỗi được gán cho tên property đó**, sau đó giải mã giá trị tìm được thành số nguyên. Ngược lại, `Integer.parseInt(String)` và `Long.parseLong(String)` xử lý **chính nội dung chuỗi được truyền vào**.

~~~java
// Mã minh họa đặt trong một phương thức:
System.setProperty("app.retries", "5");

Integer retries = Integer.getInteger("app.retries");  // 5
int direct = Integer.parseInt("5");                    // 5

// Integer.getInteger("5") KHÔNG có nghĩa là parse số 5:
// Nó tìm system property có TÊN là "5".
~~~

Phân biệt hai vai trò của chuỗi:

- **Property key/name**: `"app.retries"` — tên khóa dùng để tìm cấu hình.
- **Property value**: `"5"` — văn bản chứa số, đọc được bằng `System.getProperty("app.retries")`.

Đối với `getInteger`/`getLong`, bước tra cứu **luôn xuất hiện trước bước giải mã số**. Không có property phù hợp thì mặc định kết quả `null` hoặc giá trị fallback do lập trình viên chỉ định.

## 2. Bảng phân biệt các API dễ lẫn

| API | Chuỗi tham số được hiểu là | Kiểu trả về | Phản ứng khi không có/sai dữ liệu |
| --- | --- | --- | --- |
| `Integer.parseInt("42")` | Chính văn bản số `"42"` | `int` | Ném `NumberFormatException` khi chuỗi không phải số nguyên hợp lệ |
| `Long.parseLong("42")` | Chính văn bản số `"42"` | `long` | Ném `NumberFormatException` nếu không parse được |
| `Integer.valueOf("42")` | Chính văn bản số `"42"` | `Integer` | Ném `NumberFormatException` khi không hợp lệ |
| `Long.valueOf("42")` | Chính văn bản số `"42"` | `Long` | Ném `NumberFormatException` khi không hợp lệ |
| `Integer.decode("0x2A")` | Chuỗi biểu diễn số có thể mang tiền tố radix | `Integer` | Ném `NumberFormatException` nếu không hợp lệ |
| `Long.decode("0x2A")` | Chuỗi biểu diễn số có thể mang tiền tố radix | `Long` | Ném `NumberFormatException` nếu không hợp lệ |
| `System.getProperty("app.port")` | **Tên** system property | `String` hoặc `null` | Trả về chuỗi property; không tự parse |
| `Integer.getInteger("app.port")` | **Tên** system property | `Integer` hoặc `null` | Không có/không giải mã được → `null` |
| `Long.getLong("app.limit")` | **Tên** system property | `Long` hoặc `null` | Không có/không giải mã được → `null` |

Các phương thức đọc property còn có overload nhận **giá trị mặc định**:

~~~java
Integer count = Integer.getInteger("app.count", 10);
Long limit = Long.getLong("app.limit", 1_000L);
~~~

Nếu property **không tồn tại hoặc không được giải mã thành số hợp lệ**, các overload này trả về wrapper chứa giá trị mặc định. Chúng không ghi mặc định đó vào danh sách system properties.

**Ghi chú quan trọng:** trong môi trường có cơ chế bảo vệ quyền truy cập property, thao tác đọc có thể phát sinh `SecurityException`. Giá trị mặc định không phải một cơ chế vượt qua hoặc vô hiệu hóa giới hạn truy cập.

## 3. System property là gì và lấy từ đâu?

Java system properties là tập hợp các cặp **khóa/giá trị chuỗi** thuộc tiến trình JVM. Ví dụ khóa `"java.version"` hoặc những khóa ứng dụng tự đặt như `"app.worker.count"`.

Có thể cung cấp giá trị khi khởi động JVM với tùy chọn `-Dkey=value`:

~~~bash
java -Dapp.retries=5 -Dapp.limit=0x100 MyApplication
~~~

Tại thời điểm chạy, ứng dụng có thể đọc:

~~~java
String text = System.getProperty("app.retries");
Integer retries = Integer.getInteger("app.retries");
Long limit = Long.getLong("app.limit");
~~~

Trong ví dụ lệnh trên:

- `text` là chuỗi `"5"`.
- `retries` là wrapper `Integer` mang giá trị `5`.
- `limit` là wrapper `Long` mang giá trị `256L` vì văn bản `0x100` được giải mã theo hệ 16.

Trong mã, ứng dụng còn có thể thay đổi property bằng `System.setProperty(key, value)` hoặc xóa bằng `System.clearProperty(key)`. System properties thường được chia sẻ trong **một tiến trình JVM**, không phải kho lưu trữ cấu hình trên hệ điều hành.

### System property khác environment variable

`System.getProperty("APP_PORT")` và `System.getenv("APP_PORT")` là **hai nguồn dữ liệu khác nhau**:

- `System.getProperty` đọc bảng Java system properties, thường từ `-D` hoặc do mã Java thiết lập.
- `System.getenv` đọc các biến môi trường tiến trình do hệ điều hành cấp.
- `Integer.getInteger` và `Long.getLong` **không tự đọc environment variable**.

~~~java
// Minh họa: hai lệnh đọc hai không gian khóa khác nhau.
String property = System.getProperty("APP_PORT");
String environment = System.getenv("APP_PORT");
~~~

Có thể thiết kế ứng dụng cho phép cả hai nguồn, nhưng phải triển khai chính sách ưu tiên/kiểm tra minh bạch; Java không tự động đổi khóa environment thành system property cho hai phương thức `getInteger`/`getLong`.

## 4. Cơ chế của Integer.getInteger

Về cách sử dụng, có thể hình dung `Integer.getInteger(name)` tương đương luồng xử lý sau:

1. Nhận chuỗi `name` là **tên khóa**.
2. Đọc `System.getProperty(name)`.
3. Nếu không nhận được giá trị chuỗi hợp lệ, trả `null`.
4. Nếu có chuỗi, giải mã số bằng quy tắc giống `Integer.decode(String)`.
5. Nếu giải mã được số trong miền `int`, trả `Integer` tương ứng; nếu sai định dạng hoặc ngoài miền, trả `null`.

Đây là **mô hình giải thích hành vi công khai**, không phải cam kết về từng câu lệnh trong triển khai nội bộ JDK.

~~~java
System.setProperty("demo.threads", "8");
Integer threads = Integer.getInteger("demo.threads");

System.out.println(threads); // 8
~~~

Nếu không có property mang tên `"demo.threads"`:

~~~java
System.clearProperty("demo.threads");
Integer threads = Integer.getInteger("demo.threads");
System.out.println(threads); // null
~~~

`null` nói rằng lời gọi không cung cấp một giá trị số `Integer`; nó **không phải số 0**. Trên thực tế, `null` có thể do property không tồn tại, tên không hợp lệ hoặc giá trị chuỗi không giải mã được. Chỉ nhìn kết quả `null` không đủ để xác định nguyên nhân nếu chưa kiểm tra property gốc và trạng thái truy cập.

## 5. Cơ chế của Long.getLong

`Long.getLong(name)` cũng đọc **property có tên `name`**, nhưng phân tích thành số nguyên `long` 64 bit có dấu thay vì `int` 32 bit:

~~~java
System.setProperty("demo.max.id", "9223372036854775807");

Long id = Long.getLong("demo.max.id");
System.out.println(id); // 9223372036854775807
~~~

Con số trên đúng bằng `Long.MAX_VALUE`; `Integer.getInteger("demo.max.id")` sẽ không biểu diễn được giá trị đó và trả `null`.

| Đặc điểm | `Integer.getInteger` | `Long.getLong` |
| --- | --- | --- |
| Kiểu trả về | `Integer` | `Long` |
| Miền số | −2³¹ đến 2³¹−1 | −2⁶³ đến 2⁶³−1 |
| Phương thức giải mã cùng quy tắc | `Integer.decode` | `Long.decode` |
| Không có/sai property | `null` hoặc default | `null` hoặc default |
| Có đọc môi trường trực tiếp? | Không | Không |

Tên `Long.getLong` không có nghĩa “đọc một Long từ biến tên bất kỳ” hay “đổi giá trị của biến `String` truyền vào thành long”. Nó dùng chính **giá trị của tham số `name` như một khóa tra cứu**.

## 6. Quy tắc giải mã: thập phân, hexadecimal, octal

`getInteger` và `getLong` giải mã **giá trị property** theo quy tắc `Integer.decode`/`Long.decode`. Những chuỗi phổ biến:

| Property value | Cách hiểu của `decode` | Giá trị thập phân |
| --- | --- | --- |
| `"42"` | Thập phân | 42 |
| `"+42"` | Thập phân, dấu cộng | 42 |
| `"-42"` | Thập phân, dấu trừ | −42 |
| `"0x2A"` hoặc `"0X2A"` | Hexadecimal | 42 |
| `"#2A"` | Hexadecimal | 42 |
| `"052"` | Octal vì có số 0 dẫn đầu | 42 |
| `"-0x2A"` | Hexadecimal âm | −42 |
| `"0"` | Số không | 0 |

Nhớ rằng **số 0 ở đầu có ý nghĩa**:

~~~java
System.setProperty("demo.mode", "010");
System.out.println(Integer.getInteger("demo.mode")); // 8
System.out.println(Integer.parseInt("010"));         // 10
~~~

`Integer.getInteger` đọc giá trị property `"010"` rồi decode thành **octal 8**. `Integer.parseInt("010")` mặc định dùng **radix 10** nên nhận **10**.

Đây là khác biệt dễ tạo lỗi cấu hình: người viết cấu hình có thể tưởng `010` là mười, nhưng người đọc bằng `getInteger` nhận tám. Khi tài liệu cấu hình quy định giá trị decimal, cách đọc `System.getProperty` rồi `parseInt` có thể phù hợp hơn.

### Tiền tố nào không được decode hỗ trợ?

`Integer.decode`/`Long.decode` không nhận tiền tố `0b`/`0B` của **literal nhị phân trong mã nguồn Java**. Những chuỗi như `"0b1010"` thường làm `getInteger`/`getLong` trả `null` hoặc fallback.

Ngoài ra, dấu gạch dưới `_` trong **literal Java source** như `1_000` là cú pháp của trình biên dịch, không mặc nhiên hợp lệ khi đưa vào chuỗi cấu hình `"1_000"`. Không dựa vào hỗ trợ của Java literal để suy ra khả năng parse của các API trên.

Ví dụ sai:

~~~java
System.setProperty("demo.binary", "0b1010");
Integer value = Integer.getInteger("demo.binary", -1);
System.out.println(value); // -1: 0b không được decode hỗ trợ
~~~

Nếu cấu hình thực sự quy định hệ 2, dùng `Integer.parseInt(text, 2)` với văn bản chữ số `"1010"`, hoặc thiết kế bước xử lý rõ ràng cho tiền tố `0b` trước khi parse.

### Dấu, khoảng trắng và định dạng

Các phương thức `decode` cho phép dấu `+` hoặc `-` ở **đầu chuỗi**, trước tiền tố radix khi có. Không thêm dấu sau tiền tố, chẳng hạn `"0x-2A"`.

Chúng không tự tiện cắt mọi khoảng trắng. Giá trị như `" 42 "` không phải chuỗi hợp lệ đối với `decode`; `getInteger` trả `null` hoặc fallback. Nếu hợp đồng cấu hình cho phép whitespace, hãy đọc chuỗi và xử lý tường minh, ví dụ `text.trim()`, sau đó parse.

## 7. Khi thiếu property và khi nội dung không hợp lệ

Ba trạng thái có thể rất khác nhau trong cấu hình:

1. Property **không tồn tại**.
2. Property **tồn tại nhưng chuỗi rỗng hoặc sai định dạng**.
3. Property **tồn tại nhưng số vượt miền kiểu đích**.

Với `Integer.getInteger(name)`, kết quả của cả ba trường hợp có thể là `null`. Với overload có giá trị mặc định, cả ba trả về cùng fallback, trừ khi xảy ra lỗi bảo mật khi đọc property.

~~~java
System.clearProperty("demo.missing");
System.setProperty("demo.invalid", "abc");
System.setProperty("demo.too.big", "2147483648");

System.out.println(Integer.getInteger("demo.missing"));       // null
System.out.println(Integer.getInteger("demo.invalid"));       // null
System.out.println(Integer.getInteger("demo.too.big"));       // null
System.out.println(Integer.getInteger("demo.missing", 7));    // 7
System.out.println(Integer.getInteger("demo.invalid", 7));    // 7
System.out.println(Integer.getInteger("demo.too.big", 7));    // 7
~~~

`2147483648` lớn hơn `Integer.MAX_VALUE` đúng một đơn vị, nhưng vẫn nằm trong miền `long`:

~~~java
System.out.println(Long.getLong("demo.too.big")); // 2147483648
~~~

**Điều cần lưu ý:** `getInteger`/`getLong` có thể khiến dữ liệu cấu hình **không hợp lệ bị che giấu** bởi fallback. Với cấu hình bắt buộc và quan trọng (ví dụ giới hạn tài nguyên), việc tùy tiện chọn default có thể che khuất lỗi vận hành.

## 8. Overload và giá trị mặc định

`Integer` có các dạng:

~~~java
Integer getInteger(String name)
Integer getInteger(String name, int defaultValue)
Integer getInteger(String name, Integer defaultValue)
~~~

`Long` có các dạng tương ứng:

~~~java
Long getLong(String name)
Long getLong(String name, long defaultValue)
Long getLong(String name, Long defaultValue)
~~~

Chữ ký trên để tham khảo API (không phải khai báo method để copy vào lớp ứng dụng).

Các ví dụ:

~~~java
System.clearProperty("app.timeout");
System.out.println(Integer.getInteger("app.timeout", 30)); // 30
System.out.println(Long.getLong("app.timeout", 60L));      // 60

Integer fallback = Integer.valueOf(15);
Integer timeout = Integer.getInteger("app.timeout", fallback);
System.out.println(timeout); // 15
~~~

Dạng nhận wrapper cho phép truyền một giá trị mặc định kiểu wrapper; về mặt kết quả, có thể cho `null` nếu tham số default wrapper là `null`:

~~~java
Integer noDefault = Integer.getInteger("app.timeout", (Integer) null);
System.out.println(noDefault); // null
~~~

Trong trường hợp này, khi property không có hoặc không giải mã được, một fallback null vẫn cho ra null. Cần chủ động chọn kiểu overload, đặc biệt khi truyền literal `null` — dùng ép kiểu để thể hiện ý định hoặc chọn phương thức không có tham số default.

### Nếu muốn phát hiện lỗi cấu hình thay vì âm thầm lấy default

~~~java
static int requiredDecimalProperty(String name) {
    String text = System.getProperty(name);
    if (text == null) {
        throw new IllegalArgumentException("Missing system property: " + name);
    }
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
                "Invalid decimal integer property: " + name, ex);
    }
}
~~~

Đây là **phương thức minh họa bên trong một lớp**, biên dịch được trên Java 17. Nó quy định chỉ nhận **số thập phân**; khác với `Integer.getInteger`, chuỗi `"010"` sẽ được hiểu là 10 và `"0x10"` bị từ chối. Đừng thêm `trim()` một cách ngầm định nếu hệ thống muốn phát hiện whitespace không hợp lệ.

Hàm này **không** tự kiểm tra giới hạn nghiệp vụ như “số luồng phải lớn hơn 0”; việc đó cần thêm validation sau khi parse.

## 9. Chương trình Java 17+ chạy độc lập

Tạo tệp `SystemPropertyNumberDemo.java`:

~~~java
public class SystemPropertyNumberDemo {
    public static void main(String[] args) {
        System.setProperty("lesson.count", "42");
        System.setProperty("lesson.hex", "0x2A");
        System.setProperty("lesson.octal", "010");
        System.setProperty("lesson.binary", "0b1010");
        System.setProperty("lesson.invalid", "abc");
        System.setProperty("lesson.outOfInt", "2147483648");
        System.setProperty("lesson.maxLong", "9223372036854775807");
        System.clearProperty("lesson.missing");

        System.out.println("getInteger decimal=" +
                Integer.getInteger("lesson.count"));
        System.out.println("getInteger hex=" +
                Integer.getInteger("lesson.hex"));
        System.out.println("getInteger octal=" +
                Integer.getInteger("lesson.octal"));
        System.out.println("parseInt octal text=" +
                Integer.parseInt(System.getProperty("lesson.octal")));
        System.out.println("getLong max=" +
                Long.getLong("lesson.maxLong"));

        System.out.println("missing=" +
                Integer.getInteger("lesson.missing"));
        System.out.println("missing default=" +
                Integer.getInteger("lesson.missing", 99));
        System.out.println("invalid default=" +
                Integer.getInteger("lesson.invalid", 7));
        System.out.println("binary default=" +
                Integer.getInteger("lesson.binary", -1));
        System.out.println("int overflow default=" +
                Integer.getInteger("lesson.outOfInt", -1));
        System.out.println("same number as long=" +
                Long.getLong("lesson.outOfInt"));

        System.out.println("parse direct=" + Integer.parseInt("42"));
        System.out.println("raw property=" +
                System.getProperty("lesson.count"));

        try {
            Integer.parseInt("abc");
        } catch (NumberFormatException ex) {
            System.out.println("parse invalid: NumberFormatException");
        }
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 SystemPropertyNumberDemo.java
java SystemPropertyNumberDemo
~~~

Kết quả stdout:

~~~text
getInteger decimal=42
getInteger hex=42
getInteger octal=8
parseInt octal text=10
getLong max=9223372036854775807
missing=null
missing default=99
invalid default=7
binary default=-1
int overflow default=-1
same number as long=2147483648
parse direct=42
raw property=42
parse invalid: NumberFormatException
~~~

Giải thích:

- Các property được đặt bằng `System.setProperty` trong chính chương trình nên kết quả độc lập với cấu hình `-D` bên ngoài cho những khóa đó.
- Số `"010"` được `getInteger` giải mã theo octal thành 8; `parseInt` theo decimal thành 10.
- `"0b1010"` không thuộc tiền tố `decode` nên trở về fallback −1.
- `"2147483648"` vượt miền `int` nhưng vẫn hợp lệ với `long`.
- `Integer.parseInt("abc")` phát sinh `NumberFormatException`; `getInteger` với property có value `"abc"` lại trả default.
- `System.getProperty` trả chuỗi gốc; không ép kiểu và không thực hiện bước giải mã.

## 10. Dùng -D khi triển khai chương trình thực

Với chương trình ứng dụng có class `ServerApp` đã được biên dịch, có thể cấu hình từ command line:

~~~bash
java -Dserver.port=8080 -Dserver.workers=4 ServerApp
~~~

Đoạn mã ứng dụng:

~~~java
int port = Integer.getInteger("server.port", 8080);
int workers = Integer.getInteger("server.workers", 4);
~~~

Ở đây Java **autounbox** kết quả wrapper từ `getInteger` vào `int`. Với overload có primitive default, trường hợp property không tồn tại/sai sẽ cho wrapper chứa default trước khi unbox, vì vậy không xuất hiện null trong đường đi thông thường. Tuy nhiên, nếu hạn chế quyền truy cập property thì vẫn có thể có ngoại lệ.

Không nhầm vị trí của tham số JVM:

~~~bash
java -Dserver.port=8080 ServerApp
~~~

`-Dserver.port=8080` là tùy chọn của **JVM trước tên class**. Viết `java ServerApp -Dserver.port=8080` sẽ truyền chuỗi đó vào `main(String[] args)` như một tham số chương trình, không tự thiết lập system property tương ứng.

### Mối quan hệ với môi trường triển khai

Trên hệ thống container/service, có thể có biến môi trường tên `SERVER_PORT`, file cấu hình, tham số dòng lệnh ứng dụng, hoặc system property `server.port`. Đây là các nguồn cấu hình độc lập. Nếu yêu cầu triển khai phân cấp ưu tiên, chẳng hạn “`-D` ưu tiên hơn environment”, phải viết logic xử lý rõ ràng.

Không nên đưa mật khẩu, token hoặc thông tin nhạy cảm vào system property một cách tùy tiện chỉ vì tiện sử dụng. Thuộc tính JVM có thể lộ qua công cụ chẩn đoán, logging hoặc danh sách tham số tiến trình tùy môi trường; cần tuân thủ cơ chế lưu trữ bí mật phù hợp.

## 11. Chuỗi chứa số: parseInt, parseLong, decode hay getInteger?

Một tiêu chí thực tế:

| Nhu cầu | API đề xuất |
| --- | --- |
| Người dùng nhập chuỗi decimal `"123"` | `Integer.parseInt(text)` hoặc `Long.parseLong(text)` |
| Chuỗi decimal nhưng cần wrapper | `Integer.valueOf(text)` hoặc `Long.valueOf(text)` |
| Chuỗi số theo radix có tiền tố `0x`, `#`, số 0 đầu chuỗi | `Integer.decode(text)` hoặc `Long.decode(text)` |
| Chuỗi chữ số với radix chỉ định rõ | `Integer.parseInt(text, radix)` hoặc `Long.parseLong(text, radix)` |
| Đọc property JVM và chấp nhận quy tắc `decode`, fallback | `Integer.getInteger(name, default)` hoặc `Long.getLong(name, default)` |
| Đọc property JVM nhưng bắt buộc decimal, cần báo lỗi chính xác | `System.getProperty(name)`, kiểm tra rồi `parseInt`/`parseLong` |
| Đọc biến môi trường | `System.getenv(name)`, sau đó kiểm tra/parse nếu cần |

Một cách nghĩ an toàn:

- Có **giá trị text** cần chuyển sang số → chọn một phương thức **parse/decode**.
- Có **tên property JVM** muốn đọc cấu hình dạng số → chọn `getInteger`/`getLong` khi phù hợp hợp đồng decode/fallback.
- Có **tên environment variable** → đọc qua `System.getenv` trước, không dùng `getInteger` để tra.

## 12. Cẩn trọng với parsing số và giới hạn miền

### Số vượt miền kiểu đích

~~~java
System.setProperty("app.large", "9223372036854775807");
System.out.println(Long.getLong("app.large"));     // 9223372036854775807
System.out.println(Integer.getInteger("app.large")); // null
~~~

Nếu value vượt cả miền `long`, `Long.getLong` cũng không thể giải mã; khi không có default nó trả `null`. Phần tử cấu hình cần giá trị lớn hơn `long` có thể dùng `BigInteger` sau khi đọc text, với validation riêng.

### Null không phải số 0

~~~java
System.clearProperty("app.items");
Integer items = Integer.getInteger("app.items");

System.out.println(items == null); // true
~~~

Nếu dùng `int count = Integer.getInteger("app.items");`, unboxing giá trị `null` sẽ ném `NullPointerException`. Đây là lỗi thường gặp khi dùng overload không có default với biến primitive.

Nếu “property vắng” khác “giá trị zero”, hãy giữ `Integer` nullable và xử lý hai trường hợp tường minh, hoặc ném ngoại lệ cấu hình có thông báo.

### Không tự động bỏ qua whitespace

~~~java
System.setProperty("app.spaces", " 7 ");
System.out.println(Integer.getInteger("app.spaces", -1)); // -1
~~~

Nếu người dùng có thể nhập khoảng trắng, đọc text và áp dụng chính sách chuẩn hóa **có chủ đích**. Lỗi sai định dạng cũng nên được log có kiểm soát để người vận hành biết nguyên nhân thay vì mất dấu trong một default “có vẻ hợp lệ”.

## 13. Chính sách cấu hình: optional, required và valid range

Đối với một system property định nghĩa kích thước pool worker, ta thường cần ít nhất ba bước:

1. **Đọc** giá trị từ nguồn dự kiến.
2. **Parse** với quy tắc định dạng được công bố.
3. **Validate** miền nghiệp vụ, ví dụ worker count từ 1 đến 128.

Một hàm Java 17 minh họa:

~~~java
static int workerCount() {
    String raw = System.getProperty("app.workers");
    if (raw == null) {
        return 4; // Mặc định khi khóa không có
    }

    final int count;
    try {
        count = Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
                "app.workers must be a decimal integer", ex);
    }

    if (count < 1 || count > 128) {
        throw new IllegalArgumentException(
                "app.workers must be in 1..128");
    }
    return count;
}
~~~

Điểm khác biệt với `Integer.getInteger("app.workers", 4)`:

- **Property vắng** → dùng 4.
- **Property sai định dạng** → báo lỗi thay vì im lặng dùng 4.
- **Property có giá trị 0, âm hoặc quá lớn theo nghiệp vụ** → báo lỗi.
- `"010"` được parse thành decimal 10, không phải octal 8.

Việc chọn fallback hay fail-fast tùy mức độ bắt buộc của cấu hình. Với thông số ảnh hưởng tài nguyên, tính an toàn hoặc dữ liệu quan trọng, **fail-fast** thường giúp phát hiện lỗi triển khai sớm.

## 14. Những lỗi thường gặp

| Nhầm lẫn | Hậu quả | Sửa đúng |
| --- | --- | --- |
| `Integer.getInteger("123")` để parse số 123 | Tra property tên `"123"`, thường nhận `null` | `Integer.parseInt("123")` |
| `Long.getLong("9223372036854775807")` để parse số | Tra property có tên nguyên chuỗi số | `Long.parseLong(text)` |
| Nghĩ `getInteger` tự đọc environment variable | Bỏ qua `System.getenv` | Đọc môi trường trước nếu đó là nguồn dữ liệu |
| Nghĩ `getInteger` luôn dùng decimal | Property `"010"` thành octal 8 | `System.getProperty` + `parseInt` khi cần decimal |
| Property `"0b1010"` được decode như binary literal | Nhận null/default | Parse radix 2 sau khi xử lý định dạng |
| Property `"1_000"` được decode như Java literal | Nhận null/default | Định nghĩa và thực hiện chuẩn hóa/parse riêng |
| Property sai định dạng nhưng fallback âm thầm | Che giấu cấu hình sai | Đọc chuỗi rồi fail-fast |
| Gán kết quả `getInteger(name)` vào `int` mà không kiểm tra | `NullPointerException` nếu không có/sai property | Dùng overload default hoặc null guard |
| `Integer.getInteger` đọc số vượt miền int | Null/default dù văn bản là số hợp lệ kiểu long | Dùng `Long.getLong` hoặc parse kiểu phù hợp |
| Nghĩ fallback sẽ ghi property mới | Nhầm trạng thái cấu hình | Fallback chỉ là giá trị kết quả |
| Đặt `-D` sau class trong lệnh `java` | Không đặt JVM property | Đặt `-Dkey=value` trước tên class/JAR |
| Dùng getInteger cho mọi kiểu cấu hình không validation | Chấp nhận số âm/zero ngoài hợp đồng | Validate miền nghiệp vụ sau khi parse |

## 15. Bài tập tự kiểm tra

**1.** Cho:

~~~java
System.setProperty("demo.number", "25");
System.out.println(Integer.getInteger("demo.number"));
System.out.println(Integer.getInteger("25"));
~~~

Dòng đầu in bao nhiêu? Dòng thứ hai có chắc in 25 không?

**Đáp án:** dòng đầu in `25`. Dòng thứ hai tra property **tên** `"25"`; kết quả có thể là `null` nếu property đó không tồn tại, không được phép suy ra sẽ in 25.

**2.** Property `demo.limit=0x20` được đọc bằng `Long.getLong("demo.limit")` thành bao nhiêu?

**Đáp án:** `32L`, vì `0x20` là hexadecimal.

**3.** Tại sao `Integer.getInteger("mode", -1)` có thể trả −1 dù property `mode` tồn tại?

**Đáp án:** Property value có thể sai định dạng hoặc vượt miền `int`, khiến decode thất bại và overload dùng fallback.

**4.** Vì sao `Integer.getInteger` đọc `"010"` khác `Integer.parseInt("010")`?

**Đáp án:** getInteger dùng quy tắc `Integer.decode` của **property value**, nên nhận octal 8; parseInt mặc định decimal nên nhận 10.

**5.** Muốn ứng dụng báo lỗi nếu `app.workers` có value `"abc"` nhưng dùng default khi property không tồn tại: nên dùng gì?

**Đáp án:** `System.getProperty("app.workers")`; nếu trả null thì dùng default, còn nếu có chuỗi thì `Integer.parseInt` trong `try/catch` và validation nghiệp vụ.

**6.** `Long.getLong` có đọc được biến môi trường `MAX_COUNT` không?

**Đáp án:** Không tự động. `Long.getLong("MAX_COUNT")` tìm **system property** tên đó. Muốn đọc environment variable, dùng `System.getenv("MAX_COUNT")` rồi parse.

## 16. Ghi nhớ

- **`Integer.getInteger(name)` và `Long.getLong(name)` nhận *tên system property*, không nhận trực tiếp một chuỗi số để parse.**
- Chúng đọc property value rồi decode theo quy tắc decimal/hex/octal, khác parse decimal mặc định.
- Không có property hoặc value không hợp lệ → trả `null` hoặc default do overload; cần kiểm tra null khi unboxing.
- `System.getProperty`, `System.getenv` và `Integer.parseInt`/`Long.parseLong` giải quyết **những bài toán khác nhau**.
- Khi cấu hình quan trọng, tách rõ đọc nguồn → parse → validate → xử lý lỗi, thay vì vô tình che lỗi bằng fallback.

Tài liệu chính thức (Java 17):

- [Integer.getInteger / Integer.decode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): quy tắc đọc system property và giải mã giá trị.
- [Long.getLong / Long.decode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html): tương tự cho `long`.
- [System.getProperty](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#getProperty(java.lang.String)) và [System.getenv](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#getenv(java.lang.String)): phân biệt nguồn cấu hình.
- [Java launcher](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): tùy chọn `-Dproperty=value`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-010">Kiểm tra toàn bộ đầu vào và chính sách xử lý khoảng trắng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra toàn bộ đầu vào và chính sách xử lý khoảng trắng khi parse số nguyên trong Java

Một chuỗi nhìn giống số không nhất thiết là **đầu vào số nguyên hợp lệ**. Ví dụ `"42"`, `" 42 "`, `"42abc"`, `"4 2"` hay `"１２３"` có thể được xử lý khác nhau tùy parser, bộ ký tự hợp lệ và **chính sách xử lý khoảng trắng**. Khi dữ liệu đến từ người dùng, file cấu hình hoặc giao thức, cần trả lời hai câu hỏi trước khi gọi `parseInt` hoặc `parseLong`:

1. **Parser có kiểm tra hết chuỗi không?** Hay chỉ lấy một tiền tố giống số rồi bỏ qua phần đuôi?
2. **Chuỗi được phép chứa những khoảng trắng nào, ở vị trí nào?** Từ chối mọi khoảng trắng, chỉ bỏ khoảng trắng ở hai đầu, hay chấp nhận dấu phân nhóm/định dạng theo locale?

Trong Java, **`Integer.parseInt` và `Long.parseLong` kiểm tra toàn bộ chuỗi số nguyên được truyền vào**. Chúng không tự bỏ khoảng trắng. Điều này tạo nền tảng tốt cho parser nghiêm ngặt, nhưng **chưa tự xác định toàn bộ chính sách nghiệp vụ**: chẳng hạn parser Java có thể nhận chữ số Unicode, còn giao thức bên ngoài chỉ cho phép ASCII.

```java
int a = Integer.parseInt("42");       // 42.
// int b = Integer.parseInt(" 42 ");  // NumberFormatException.
// int c = Integer.parseInt("42abc"); // NumberFormatException.
int d = Integer.parseInt(" 42 ".strip()); // 42: CHỦ ĐỘNG bỏ ngoài.
```

Đoạn mã trên là **mảnh minh họa** có thể đặt trong phương thức; hai dòng dẫn tới ngoại lệ đã được chú thích. Không nên coi việc `strip()` luôn là tốt: trong một số giao thức, việc có thêm dấu cách hoặc newline đã là lỗi dữ liệu và cần được phát hiện.

## 1. “Kiểm tra toàn bộ” có nghĩa là gì?

Giả sử ứng dụng nhận `"123abc"`. Có hai hành vi rất khác nhau:

- **Full-string parsing:** chỉ chấp nhận khi **toàn bộ phần đầu vào được chỉ định** phù hợp cú pháp số nguyên, không dư ký tự. Chuỗi `"123abc"` bị từ chối.
- **Prefix parsing:** chấp nhận tiền tố `"123"`, trả giá trị 123 và dừng trước `a`, có thể bỏ qua phần `"abc"` nếu bên gọi không kiểm tra vị trí dừng.

Các phương thức `Integer.parseInt(String)`, `Long.parseLong(String)`, `Byte.parseByte(String)`, `Short.parseShort(String)` và phiên bản có radix dùng quy tắc **đầu vào số nguyên phải được parse hết**. Ví dụ:

```java
int value = Integer.parseInt("123"); // 123.
// Integer.parseInt("123abc");      // Ném NumberFormatException.
// Long.parseLong("123.0");         // Ném NumberFormatException.
```

Cú pháp `parseInt` có dấu `+` hoặc `-` đầu chuỗi, tiếp theo là các chữ số được chấp nhận trong cơ số; ký tự lạ, dấu thập phân, phần hậu tố hay ký tự thừa sẽ làm parser thất bại. **Giới hạn phạm vi** cũng được kiểm tra: `Integer.parseInt("2147483648")` ném `NumberFormatException` dù chuỗi chỉ có các chữ số thập phân.

**Phân biệt phạm vi kiểm tra:** `Integer.parseInt(CharSequence s, int begin, int end, int radix)` chỉ kiểm tra **toàn bộ đoạn `[begin,end)` đã chỉ định**, chứ không tự kiểm tra các ký tự ngoài đoạn. Nếu chỉ định đoạn `"123"` trong chuỗi `"123abc"`, gọi đó là “parse hết chuỗi nguồn” sẽ sai. Khi sử dụng parser theo slice, trách nhiệm bảo đảm slice đúng boundaries thuộc về bên gọi.

## 2. Ba chính sách xử lý khoảng trắng thường gặp

| Chính sách | Ví dụ chấp nhận | Ví dụ từ chối | Khi nào hợp lý |
| --- | --- | --- | --- |
| **STRICT**: không chuẩn hóa | `"42"`, `"-42"` | `" 42"`, `"42\n"` | File machine-readable hoặc protocol yêu cầu cú pháp chính xác |
| **OUTER-STRIP**: bỏ khoảng trắng ngoài | `" 42 "`, `"\t-42\n"` | `"4 2"` | Dữ liệu từ form/CLI cho phép người dùng thừa dấu cách hai đầu |
| **LOCALE-AWARE**: theo quy tắc văn hóa | Ví dụ `"1,234"` trong US locale | Tùy locale và cấu hình | Chuỗi được người dùng nhập theo ngôn ngữ/vùng và có phân nhóm số |

**Không tự động gộp các chính sách.** Nếu hệ thống cần kiểm soát byte-level hoặc tuân thủ schema, một giá trị `"42\n"` có thể phải bị từ chối để phát hiện lỗi đóng gói. Nếu ứng dụng yêu cầu thân thiện với người dùng, bỏ khoảng trắng ngoài có thể phù hợp, nhưng phải được ghi rõ trong hợp đồng nhập liệu.

Chính sách OUTER-STRIP không có nghĩa loại mọi khoảng trắng:

```java
" 42 ".strip().equals("42"); // true
"4 2".strip().equals("42");  // false: khoảng trắng giữa vẫn còn
```

Trong `String`, `strip`, `stripLeading`, `stripTrailing` **chỉ tác động biên**, không tự xóa hoặc nối các ký tự ở giữa. Dùng `replaceAll("\\s+", "")` một cách vô điều kiện có thể biến dữ liệu `"4 2"` thành `"42"` và che khuất input sai — không nên làm trừ khi schema thật sự yêu cầu xóa separator.

## 3. `parseInt` và `parseLong` không bỏ khoảng trắng

```java
int a = Integer.parseInt("42"); // 42.
// Integer.parseInt(" 42");     // NumberFormatException.
// Integer.parseInt("42 ");     // NumberFormatException.
// Integer.parseInt("\t42");   // NumberFormatException.
```

Bản thân `parseInt` không hiểu ký tự space/tab/newline như thành phần hợp lệ của một chữ số. Dấu `+` hoặc `-` được chấp nhận ở **đầu phần chuỗi số** (`"+42"`, `"-42"`), không có khoảng trắng chen giữa dấu và chữ số:

```java
int positive = Integer.parseInt("+42"); // 42.
// Integer.parseInt("+ 42");             // NumberFormatException.
// Integer.parseInt("4 2");              // NumberFormatException.
```

Nếu dữ liệu cho phép khoảng trắng hai đầu, gọi `strip()` **trước** khi parse:

```java
String raw = "  -42\t";
int n = Integer.parseInt(raw.strip()); // -42
```

Nhưng cần phân biệt **đã chuẩn hóa chuỗi rồi parse hết** với **parse hết dữ liệu thô**. Sau `strip`, một số ký tự của dữ liệu gốc đã cố ý bị bỏ qua; đừng dùng câu “parser kiểm tra mọi ký tự gốc” nếu bạn đã chọn phép chuẩn hóa đầu vào.

## 4. `trim()` khác `strip()` ra sao?

Trong Java 17:

- **`String.trim()`** loại các ký tự có mã `<= U+0020` ở đầu/cuối. Đây là quy tắc lịch sử dựa trên code unit, không phải toàn bộ Unicode whitespace.
- **`String.strip()`** (Java 11+) dựa vào `Character.isWhitespace` theo Unicode/Java, xử lý được nhiều dấu cách không phải ASCII, ví dụ **EM SPACE U+2003**.
- **`stripLeading()`** và **`stripTrailing()`** chỉ bỏ một phía theo cùng quy tắc `isWhitespace`.
- **`isBlank()`** giúp biết chuỗi rỗng hoặc chỉ có các code point được coi là whitespace bởi `Character.isWhitespace`, chứ không kiểm tra nó có phải số hợp lệ.

Ví dụ cụ thể:

```java
String surrounded = "\u200342\u2003"; // EM SPACE ở hai đầu.
String trimmed = surrounded.trim();    // Vẫn còn EM SPACE.
String stripped = surrounded.strip();  // "42".
int number = Integer.parseInt(stripped); // 42.
```

`strip` **không đồng nghĩa loại toàn bộ ký tự Unicode nhìn giống khoảng trắng**. Chẳng hạn `NO-BREAK SPACE U+00A0` không được `Character.isWhitespace` coi là whitespace. Vì thế:

```java
String nbsp = "\u00A042\u00A0";
boolean unchanged = nbsp.strip().equals(nbsp); // true.
```

Đừng mặc định `strip()` “làm sạch mọi dấu cách”. Nếu dữ liệu có thể chứa NBSP, NARROW NO-BREAK SPACE hoặc ký tự vô hình khác, hãy **quyết định có cho phép chúng hay không**, xác thực theo danh sách ký tự hoặc quy tắc mã hóa được thiết kế cụ thể. Dùng normalize tùy tiện có thể làm hai chuỗi vốn khác nhau trở nên giống nhau, phá vỡ hợp đồng định dạng.

## 5. Kiểm tra toàn bộ chuỗi bằng regex ASCII trước khi parse

Nếu giao thức yêu cầu số thập phân **chỉ gồm chữ số ASCII**, một mẫu đơn giản là:

```regex
[+-]?[0-9]+
```

- `[+-]?`: cho phép tối đa một dấu cộng hoặc trừ đầu chuỗi (nếu schema cho phép).
- `[0-9]+`: yêu cầu ít nhất một chữ số ASCII.
- Gọi **`Matcher.matches()`**, không phải `find()`, để yêu cầu *toàn bộ chuỗi* khớp mẫu.

Ví dụ phương thức trong một lớp Java:

```java
private static final java.util.regex.Pattern ASCII_INTEGER =
        java.util.regex.Pattern.compile("[+-]?[0-9]+");

static int parseAsciiStrict(String text) {
    java.util.Objects.requireNonNull(text, "text");
    if (!ASCII_INTEGER.matcher(text).matches()) {
        throw new IllegalArgumentException("Invalid ASCII integer");
    }
    return Integer.parseInt(text); // Vẫn cần kiểm tra phạm vi int.
}
```

Đây là **hai thành phần khai báo ở cấp lớp**, không phải một file chạy độc lập. Regex bảo đảm **đúng cú pháp ASCII**, còn `Integer.parseInt` bảo đảm **đúng miền signed int**. `"2147483648"` khớp regex nhưng bị từ chối khi parse vì vượt phạm vi.

Dùng `[0-9]` nếu bạn thật sự muốn ASCII. Một số cú pháp regex như `\\d` có ngữ nghĩa chịu ảnh hưởng cờ và chế độ Unicode của regex engine; ghi trực tiếp `[0-9]` thường rõ hơn cho protocol ASCII.

Với parser strict không cho phép dấu `+` hoặc `-`, đổi mẫu thành `[0-9]+` và kiểm tra giá trị không âm nếu cần. Đối với schema cho số âm nhưng **không cho `+`**, dùng `-?[0-9]+`. Với mục tiêu canonical (ví dụ không cho phép `"0007"`), phải thêm quy tắc kiểm tra riêng; regex trên **cho phép số 0 đầu**.

## 6. Parser từ chối mọi whitespace và parser chấp nhận whitespace ngoài

Có thể hiện thực hai chính sách bằng cách tách lớp kiểm tra và bước chuẩn hóa:

```java
static int parseAsciiWithOuterWhitespace(String text) {
    java.util.Objects.requireNonNull(text, "text");
    return parseAsciiStrict(text.strip());
}
```

Lưu ý đây là **phương thức trong cùng lớp** có `parseAsciiStrict` đã định nghĩa. Nó chấp nhận `" 42 "` hay `"\t-42\n"`, nhưng từ chối `"4 2"` và `"4\n2"`: `strip` không xóa ký tự nội bộ.

Một ví dụ chuẩn hóa có chủ đích:

```text
Input raw:       "  -42  "
Policy:          allow Unicode whitespace at both ends via strip()
Normalized:      "-42"
ASCII grammar:   [+-]?[0-9]+   → pass
Integer range:   -2^31..2^31-1 → pass
Parsed int:      -42
```

**Vị trí kiểm tra rất quan trọng.** Nếu bạn kiểm tra regex trước `strip()`, input có khoảng trắng ngoài sẽ bị từ chối. Nếu bạn `strip()` trước, input đó được chấp nhận đúng như policy. Hai thứ tự không tương đương, nên lựa chọn phải được ghi rõ trong tài liệu API.

## 7. Unicode digits: `parseInt` không đồng nghĩa ASCII-only

Java có thể xem nhiều ký tự chữ số Unicode là chữ số hợp lệ dựa trên `Character.digit`, dù chúng không phải `'0'` đến `'9'` ASCII. Ví dụ:

```java
int value = Integer.parseInt("１２３"); // 123.
```

`"１２３"` ở đây chứa **FULLWIDTH DIGIT ONE/TWO/THREE**, được Java 17 parse thành 123. Nếu hệ thống có quy tắc ASCII-only (ví dụ một trường dữ liệu kỹ thuật yêu cầu các byte `0x30`–`0x39`), thì **chỉ gọi `parseInt` không đủ để xác thực hợp đồng**.

Sử dụng `[0-9]` với `matches()` như ví dụ phía trên để từ chối các chữ số không phải ASCII. Ngược lại, trong ứng dụng quốc tế hóa, bạn có thể cố ý chấp nhận chữ số Unicode — khi đó nên giải thích rõ miền ký tự chấp nhận và kiểm thử từng loại đầu vào.

Không được đánh đồng **Unicode whitespace** với **Unicode digits**: `strip()` xử lý một số ký tự ở ngoài, còn `parseInt` xử lý chữ số bên trong; chúng là hai quy tắc độc lập.

## 8. Sự nguy hiểm của parser chỉ đọc tiền tố: `NumberFormat`

Các parser số theo locale có thể chấp nhận đầu vào **chỉ một phần**. Ví dụ với `java.text.NumberFormat` và `ParsePosition`:

```java
String text = "123abc";
java.text.ParsePosition pos = new java.text.ParsePosition(0);
Number n = java.text.NumberFormat
        .getIntegerInstance(java.util.Locale.US)
        .parse(text, pos);
// n = 123; pos.getIndex() = 3; "abc" chưa được tiêu thụ.
```

`NumberFormat` hữu ích khi xử lý biểu diễn số theo locale (dấu phân nhóm, ký hiệu địa phương), nhưng **không mặc định là bộ kiểm tra toàn bộ input cho một giao thức chặt chẽ**.

Nếu cố ý dùng `ParsePosition`, cần kiểm tra cả:

```java
boolean parsedSomething = n != null;
boolean consumedAll = parsedSomething && pos.getIndex() == text.length();
```

Nếu `pos.getIndex()` nhỏ hơn `text.length()`, còn ký tự chưa được dùng. Khi định nghĩa một **số nguyên** theo locale, còn cần xem xét cách parser xử lý dấu thập phân, dấu phân nhóm, phép làm tròn và giới hạn kiểu trả về. `getIntegerInstance` không có nghĩa parser tự bảo đảm signed `int` 32 bit và không thay thế `Integer.parseInt`.

**Nhớ rằng:** `String.matches` hay `Matcher.matches()` kiểm tra toàn chuỗi; `Matcher.find()`, `NumberFormat.parse` hay một regex `find` có thể chỉ tìm **một phần**. Đừng dùng kết quả “đã đọc được số 123” như bằng chứng “chuỗi gốc đúng toàn bộ”.

## 9. `parseInt(CharSequence, begin, end, radix)`: đúng đoạn, không đúng cả nguồn

Từ các bản Java hiện đại (bao gồm Java 17), bạn có thể parse một phần `CharSequence`:

```java
CharSequence message = new StringBuilder("id=42;tail");
int result = Integer.parseInt(message, 3, 5, 10); // 42.
```

`beginIndex` (3) là vị trí bắt đầu **inclusive**, `endIndex` (5) là vị trí kết thúc **exclusive**. Nó tiêu thụ `"42"` đúng đoạn và không đòi hỏi `";tail"` phải là số. Đây là hành vi **có chủ đích để parse field con**, không phải lỗi của API.

Nếu nguồn dự kiến **chỉ có một trường số nguyên**, hãy dùng overload nhận toàn chuỗi và/hoặc tự kiểm tra delimiter/độ dài ngoài slice. Nếu một bản ghi gồm nhiều trường, việc parse đúng slice chưa đủ: cần **parser của toàn bản ghi** xác minh prefix, separator, số trường, vị trí end và phần dư.

Đây là điểm khác biệt giữa **full-field validation** và **full-record validation**. Một field có thể đúng cú pháp số mà bản ghi chứa nó vẫn không đúng schema.

## 10. Bảng các loại khoảng trắng và ký tự dễ nhầm

| Ký tự/chuỗi ví dụ | `trim()` ở biên | `strip()` ở biên | `parseInt` trực tiếp | Lưu ý |
| --- | --- | --- | --- | --- |
| `" 42 "` (SPACE U+0020) | Bỏ | Bỏ | Lỗi | Khoảng trắng ASCII thông dụng |
| `"\t42\n"` (TAB/LF) | Bỏ | Bỏ | Lỗi | Newline ngoài có thể là lỗi framing |
| `"\u200342\u2003"` (EM SPACE) | Không bỏ | Bỏ | Lỗi | Unicode `Character.isWhitespace` nhận |
| `"\u00A042\u00A0"` (NBSP) | Không bỏ | Không bỏ | Lỗi | Không phải whitespace theo `isWhitespace` |
| `"4 2"` | Không thay phần giữa | Không thay phần giữa | Lỗi | Không tự nối thành 42 |
| `"42abc"` | Không | Không | Lỗi | Dư ký tự; parser full-string từ chối |
| `"42\u200B"` (ZERO WIDTH SPACE) | Không bỏ | Không bỏ | Lỗi | Ký tự vô hình không thuộc chính sách mặc định |
| `"１２３"` (full-width digits) | Không liên quan | Không liên quan | **123** | Nếu ASCII-only, phải kiểm tra riêng |
| `"0007"` | Không liên quan | Không liên quan | **7** | Parse mất biểu diễn chuỗi gốc có số 0 đầu |

Bảng mô tả **Java 17** theo các ký tự được chỉ rõ. Với dữ liệu Unicode, nên kiểm tra **code point** của chuỗi khi debug thay vì tin vào biểu diễn nhìn bằng mắt; nhiều ký tự trông giống space hoặc chữ số ASCII nhưng có mã khác.

## 11. Chương trình Java 17+ có thể chạy độc lập

Lưu file **`CompleteIntegerInputDemo.java`**:

```java
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class CompleteIntegerInputDemo {
    private static final Pattern ASCII_INTEGER =
            Pattern.compile("[+-]?[0-9]+");

    static int parseAsciiStrict(String text) {
        Objects.requireNonNull(text, "text");
        if (!ASCII_INTEGER.matcher(text).matches()) {
            throw new IllegalArgumentException("Invalid ASCII integer");
        }
        return Integer.parseInt(text);
    }

    static int parseAsciiWithOuterWhitespace(String text) {
        Objects.requireNonNull(text, "text");
        return parseAsciiStrict(text.strip());
    }

    static String errorName(Runnable call) {
        try {
            call.run();
            return "OK";
        } catch (RuntimeException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        System.out.println("parsePlus=" + Integer.parseInt("+42"));
        System.out.println("parseFullLength=" + Integer.parseInt("123"));
        System.out.println("strictPlus=" + parseAsciiStrict("+42"));
        System.out.println("outerStrictError="
                + errorName(() -> parseAsciiStrict(" 42 ")));
        System.out.println("outerAccepted=" + parseAsciiWithOuterWhitespace(" 42 "));
        System.out.println("outerTabsAccepted="
                + parseAsciiWithOuterWhitespace("\t-42\n"));
        System.out.println("internalWhitespaceError="
                + errorName(() -> parseAsciiWithOuterWhitespace("4 2")));
        System.out.println("newlineInsideError="
                + errorName(() -> parseAsciiWithOuterWhitespace("4\n2")));

        String emSpaces = "\u200342\u2003";
        System.out.println("unicodeStripAccepted="
                + parseAsciiWithOuterWhitespace(emSpaces));
        System.out.println("unicodeTrimUnchanged="
                + emSpaces.trim().equals(emSpaces));

        String nbsp = "\u00A042\u00A0";
        System.out.println("nbspStripUnchanged="
                + nbsp.strip().equals(nbsp));
        System.out.println("nbspRejected="
                + errorName(() -> parseAsciiWithOuterWhitespace(nbsp)));

        System.out.println("parseUnicodeDigits="
                + Integer.parseInt("１２３"));
        System.out.println("asciiUnicodeDigitsError="
                + errorName(() -> parseAsciiStrict("１２３")));
        System.out.println("unicodeDigitsWithStrip="
                + Integer.parseInt(" \u2003１２３\u2003 ".strip()));

        System.out.println("intOverflowError="
                + errorName(() -> parseAsciiStrict("2147483648")));
        System.out.println("longMax="
                + Long.parseLong("9223372036854775807"));
        System.out.println("longOverflowError="
                + errorName(() -> Long.parseLong("9223372036854775808")));
        System.out.println("emptyError="
                + errorName(() -> parseAsciiStrict("")));
        System.out.println("spacesOnlyError="
                + errorName(() -> parseAsciiWithOuterWhitespace(" \t ")));
        System.out.println("plusOnlyError="
                + errorName(() -> parseAsciiStrict("+")));
        System.out.println("nullError="
                + errorName(() -> parseAsciiStrict(null)));
        System.out.println("underscoreError="
                + errorName(() -> parseAsciiStrict("1_000")));
        System.out.println("prefixError="
                + errorName(() -> parseAsciiStrict("0x10")));

        String whole = "123abc";
        ParsePosition pos = new ParsePosition(0);
        Number parsed = NumberFormat.getIntegerInstance(Locale.US)
                .parse(whole, pos);
        System.out.println("localeParsedPrefix=" + parsed);
        System.out.println("localeConsumed=" + pos.getIndex());
        System.out.println("localeFullConsumed="
                + (pos.getIndex() == whole.length()));
        System.out.println("leadingZeroValue=" + parseAsciiStrict("0007"));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 CompleteIntegerInputDemo.java
java CompleteIntegerInputDemo
```

Kết quả stdout chính xác (đã kiểm chứng):

```text
parsePlus=42
parseFullLength=123
strictPlus=42
outerStrictError=IllegalArgumentException
outerAccepted=42
outerTabsAccepted=-42
internalWhitespaceError=IllegalArgumentException
newlineInsideError=IllegalArgumentException
unicodeStripAccepted=42
unicodeTrimUnchanged=true
nbspStripUnchanged=true
nbspRejected=IllegalArgumentException
parseUnicodeDigits=123
asciiUnicodeDigitsError=IllegalArgumentException
unicodeDigitsWithStrip=123
intOverflowError=NumberFormatException
longMax=9223372036854775807
longOverflowError=NumberFormatException
emptyError=IllegalArgumentException
spacesOnlyError=IllegalArgumentException
plusOnlyError=IllegalArgumentException
nullError=NullPointerException
underscoreError=IllegalArgumentException
prefixError=IllegalArgumentException
localeParsedPrefix=123
localeConsumed=3
localeFullConsumed=false
leadingZeroValue=7
```

Các nhóm kết quả minh họa:

- `strictPlus`, `outerStrictError`, `outerAccepted` và `outerTabsAccepted`: xác định chính sách STRICT hoặc OUTER-STRIP một cách tường minh.
- `internalWhitespaceError`, `newlineInsideError`: bỏ khoảng trắng **ngoài** không xóa ký tự **bên trong**.
- `unicodeStripAccepted` với `unicodeTrimUnchanged`: `strip` loại được EM SPACE ở biên, còn `trim` không.
- `nbspStripUnchanged`, `nbspRejected`: NBSP không được `strip` loại bỏ, nên không trở thành đầu vào số hợp lệ trong policy này.
- `parseUnicodeDigits=123` so với `asciiUnicodeDigitsError`: `Integer.parseInt` chấp nhận chữ số full-width, nhưng regex `[0-9]` từ chối chúng.
- `intOverflowError`, `longOverflowError`: cú pháp chữ số có thể đúng nhưng giá trị vượt **dải kiểu đích**.
- `emptyError`, `spacesOnlyError`, `plusOnlyError`, `nullError`: cần xử lý dữ liệu rỗng, chỉ có dấu, chỉ có khoảng trắng và `null` riêng biệt.
- `localeParsedPrefix=123`, `localeConsumed=3`, `localeFullConsumed=false`: parser theo locale chỉ đọc **tiền tố** của `"123abc"`; bắt buộc kiểm tra vị trí tiêu thụ nếu chính sách yêu cầu cả chuỗi.
- `leadingZeroValue=7`: parse số nguyên thành công nhưng **không giữ số 0 đầu của chuỗi**.

Hàm `errorName` của chương trình cố ý bắt `RuntimeException` để **in tên ngoại lệ cho demo**, không phải mẫu thiết kế xử lý lỗi cho sản phẩm. Trong ứng dụng thật, bắt ngoại lệ ở mức chính xác như `NumberFormatException` hoặc ngoại lệ do validator của bạn quy định, không âm thầm gom mọi lỗi runtime thành “input sai”.

## 12. Tách lỗi cú pháp, lỗi dải và lỗi nghiệp vụ

Trong một parser nghiêm ngặt, ít nhất cần phân biệt ba mức:

1. **Cú pháp**: ký tự lạ, khoảng trắng sai chính sách, dấu không hợp lệ, số rỗng. Ví dụ `"4 2"`, `"0x10"` (nếu chỉ cho decimal).
2. **Dải kiểu**: chữ số hợp lệ nhưng ngoài signed int/long. Ví dụ `"2147483648"` với `Integer.parseInt`.
3. **Giới hạn nghiệp vụ**: một `int` parse thành công nhưng sai quy tắc. Ví dụ số tuổi 200, số lượng âm, kích thước file vượt quota.

Trong code mẫu, `parseAsciiStrict` ném `IllegalArgumentException` với lỗi **cú pháp regex**, còn `Integer.parseInt` có thể ném `NumberFormatException` nếu **ngoài dải**. Vì `NumberFormatException` là lớp con của `IllegalArgumentException`, người gọi bắt `IllegalArgumentException` chung sẽ làm mất phân biệt giữa hai nguồn lỗi nếu không có quy ước khác.

Một kiến trúc sạch hơn cho API công khai là trả về một kiểu kết quả có mã lỗi hoặc ném ngoại lệ được tài liệu hóa. Ví dụ:

```text
ParseResult:
- OK(value)
- MISSING
- INVALID_SYNTAX
- OUT_OF_RANGE
- DOMAIN_VIOLATION
```

Đây là **mô hình kết quả**, không phải API có sẵn của JDK. Có thể hiện thực bằng enum + record/sealed interface hoặc bằng exception class của ứng dụng nếu cần phân biệt lỗi rõ ràng. Không đưa thông báo nội bộ như stack trace hoặc toàn bộ dữ liệu đầu vào nhạy cảm thẳng tới log công khai.

## 13. `null`, chuỗi trống và chính sách dữ liệu thiếu

Không nên đánh đồng các tình huống:

| Dữ liệu | Gợi ý phân loại |
| --- | --- |
| `null` | Thiếu input hoặc vi phạm hợp đồng không-null |
| `""` | Có chuỗi nhưng rỗng |
| `" "` | Chỉ có khoảng trắng (policy-dependent) |
| `"0"` | Số 0 hợp lệ |
| `"-0"` | Parser signed int trả 0, chuỗi biểu diễn khác |
| `"+" ` | Có dấu nhưng không có chữ số |
| `"0000"` | Số 0 hợp lệ nếu cho phép số 0 đầu |

`Objects.requireNonNull` có thể dùng khi API đòi hỏi đầu vào không-null; khi đó **`NullPointerException`** là tín hiệu vi phạm hợp đồng. Nếu `null` là dữ liệu thiếu hợp lệ của form/API, nên kiểm tra và trả kết quả `MISSING` thay vì để phương thức khác vô tình ném NPE.

`String.isBlank()` có ích để phát hiện chuỗi rỗng/toàn whitespace theo `Character.isWhitespace`, nhưng **`"0"` không blank** và **NBSP không được `isWhitespace` coi là whitespace**. Không thay `isBlank` bằng `parseInt` hay ngược lại: chúng giải quyết hai câu hỏi khác nhau.

## 14. Không tự xóa dấu phân nhóm, dấu chấm và các ký tự nội bộ

Các chuỗi như `"1,000"`, `"1.000"` hoặc `"1 000"` có ý nghĩa rất khác nhau theo locale: dấu phẩy có thể phân tách nghìn hoặc thập phân; dấu chấm tương tự. Nếu dữ liệu là giao thức máy đọc với schema decimal ASCII, cách an toàn là **từ chối** tất cả separator không được cho phép.

Nếu giao diện người dùng cần locale-aware parsing, hãy chọn **`Locale` cụ thể** và sử dụng bộ parser định dạng phù hợp, sau đó kiểm tra parser đã đọc **toàn bộ input**, kiểm tra kiểu/dải kết quả, và bảo đảm quy tắc thập phân/nhóm số đúng ý định.

Ví dụ nguy hiểm:

```java
// Không nên bỏ dấu phân nhóm mù quáng:
String raw = "1,5";
// raw.replace(",", "") thành "15" nhưng người dùng có thể định nhập 1.5.
```

Việc `replaceAll("[ ,._]", "")` trên mọi ký tự có thể làm hỏng nghĩa số, vượt qua kiểm tra schema hoặc che giấu dữ liệu sai. Chỉ chuẩn hóa **đúng những ký tự** mà hợp đồng rõ ràng cho phép.

## 15. Giới hạn độ dài và an toàn với input không tin cậy

Một chuỗi có thể gồm hàng triệu chữ số, dù mục tiêu chỉ là `int`. Parser sẽ báo vượt dải, nhưng vẫn có chi phí đọc/kiểm tra. Với endpoint công khai hoặc dữ liệu không tin cậy, nên:

- Đặt **giới hạn số byte/ký tự trước khi đọc hoặc nhận dữ liệu lớn** tùy giao thức và tầng truyền tải.
- Sau khi nhận `String`, kiểm tra `null`, tổng độ dài và chính sách whitespace, rồi mới regex/parse.
- Đặt giới hạn kích thước **phần đã chuẩn hóa** nếu schema quy định số chữ số tối đa. Đừng nhầm số lượng chữ số với tính đúng dải: chuỗi có nhiều số 0 đầu vẫn có thể parse thành số nhỏ.
- Tránh regex quá phức tạp dễ dẫn tới **catastrophic backtracking**. Mẫu `[+-]?[0-9]+` đơn giản và không có các nhóm lặp lồng nhau.
- Xác thực giá trị sau parse theo miền nghiệp vụ (âm/dương, giới hạn đếm, đơn vị đo, quota).
- Không log nguyên văn input không tin cậy nếu có thể chứa thông tin nhạy cảm hoặc ký tự điều khiển gây hỏng log.

Nếu schema yêu cầu chính xác `n` chữ số, hoặc cấm leading zero, hãy biểu diễn bằng validator riêng. Không suy rằng `Integer.parseInt` kiểm tra được những quy tắc định dạng đó.

## 16. Những lỗi triển khai phổ biến

| Lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Tin parser “đọc được 123” nghĩa `"123abc"` hợp lệ | Parser prefix có thể bỏ phần dư | Xác nhận `ParsePosition.getIndex() == input.length()` hoặc dùng full-string parser |
| Gọi `strip()` mặc định ở mọi API | Che lỗi khoảng trắng của giao thức strict | Chọn policy rõ: STRICT hoặc OUTER-STRIP |
| Nghĩ `strip()` loại mọi khoảng trắng | NBSP vẫn có thể tồn tại | Kiểm tra code point và policy Unicode |
| Dùng `trim()` khi mong hỗ trợ Unicode whitespace | EM SPACE ở đầu/cuối có thể còn lại | Dùng `strip()` khi đúng contract |
| Thay mọi `" "` trong chuỗi | Biến `"4 2"` thành `"42"` | Chỉ bỏ khoảng trắng ngoài khi được cho phép |
| Gọi `Matcher.find()` thay cho `matches()` | Một phần chuỗi hợp lệ đủ để pass | Dùng `matches()` để kiểm tra toàn bộ |
| Dùng `parseInt` làm validator ASCII-only | Chữ số Unicode có thể được chấp nhận | Regex ASCII `[0-9]` hoặc kiểm tra code point |
| Chỉ regex rồi nhận `"2147483648"` như `int` | Cú pháp đúng nhưng vượt dải 32 bit | Gọi `Integer.parseInt` để kiểm tra dải |
| Xem `null`, `""`, `"0"` như nhau | Mất phân biệt dữ liệu thiếu và 0 hợp lệ | Thiết kế kết quả parse có mã lỗi |
| Dùng locale của máy mặc định để parse giao thức | Kết quả thay đổi theo môi trường | Đặt locale theo schema hoặc dùng parser decimal ASCII |
| Chỉ kiểm tra field số trong một record | Record có thể còn ký tự hoặc trường không hợp lệ | Validate toàn bộ cấu trúc record và vị trí field |
| Cho phép chuỗi số bất kỳ quá dài | Tốn tài nguyên hoặc vượt quota | Đặt giới hạn input từ sớm |
| Parse mã định danh thành số nguyên | Mất số 0 đầu và ký tự định dạng | Giữ `String` cho identifier |

## 17. Bộ test tối thiểu cho hai chính sách

| Input | STRICT ASCII | OUTER-STRIP ASCII | Ghi chú |
| --- | --- | --- | --- |
| `"42"` | 42 | 42 | Giá trị đơn giản |
| `"+42"` | 42 | 42 | Nếu cho phép dấu `+` |
| `"-42"` | -42 | -42 | Nếu cho phép số âm |
| `" 42 "` | Lỗi | 42 | Phân biệt policy |
| `"\t42\n"` | Lỗi | 42 | Tab/newline ở ngoài |
| `"4 2"` | Lỗi | Lỗi | Khoảng trắng giữa |
| `"\u200342\u2003"` | Lỗi | 42 | EM SPACE được `strip` loại |
| `"\u00A042\u00A0"` | Lỗi | Lỗi | NBSP không được `strip` loại |
| `"１２３"` | Lỗi | Lỗi | ASCII-only từ chối full-width digits |
| `"0007"` | 7 | 7 | Nếu chưa cấm leading zeros |
| `"2147483647"` | 2147483647 | 2147483647 | Biên trên int |
| `"2147483648"` | Lỗi dải | Lỗi dải | Vượt biên trên |
| `"1_000"` | Lỗi | Lỗi | `_` không thuộc chuỗi số |
| `"0x10"` | Lỗi | Lỗi | Prefix hex ngoài cú pháp decimal |
| `""`, `" "`, `null` | Lỗi/thiếu | Lỗi/thiếu | Không được coi là số 0 |

Đối với `long`, thử cả `Long.MIN_VALUE`, `Long.MAX_VALUE` và hai chuỗi nằm ngay bên ngoài dải; với unsigned, thử `2^32-1` hoặc `2^64-1` tùy API. Nếu policy cho phép locale-specific grouping, thêm các test phân nhóm đúng/sai và dấu thập phân theo **locale đã khóa**, đồng thời kiểm tra tiêu thụ toàn bộ.

## 18. Câu hỏi tự kiểm tra

1. **`Integer.parseInt("123abc")` trả 123 hay báo lỗi?** Báo `NumberFormatException` vì chuỗi đầu vào không phải số nguyên hợp lệ toàn bộ.
2. **`Integer.parseInt(" 42 ")` có tự bỏ khoảng trắng không?** Không. Gọi `strip()` trước nếu hợp đồng cho phép.
3. **`" 42 ".strip()` và `"4 2".strip()` cho cùng kết quả không?** Không; `strip` chỉ loại khoảng trắng đầu/cuối, không loại khoảng trắng giữa.
4. **`trim()` và `strip()` khác nhau với EM SPACE U+2003 ra sao?** `trim` không bỏ, `strip` bỏ ở biên.
5. **`strip()` có bỏ NO-BREAK SPACE U+00A0 không?** Không, vì nó không được `Character.isWhitespace` nhận.
6. **Vì sao `Integer.parseInt("１２３")` có thể thành công?** Java dùng quy tắc nhận chữ số Unicode; nếu cần ASCII-only, phải kiểm tra riêng.
7. **Regex `[+-]?[0-9]+` đủ để bảo đảm kết quả vừa `int` không?** Không; cần `Integer.parseInt` để kiểm tra giới hạn kiểu.
8. **`NumberFormat` đọc được 123 từ `"123abc"` thì có nghĩa toàn chuỗi hợp lệ không?** Không; phải kiểm tra vị trí tiêu thụ của parser.
9. **`parseInt(CharSequence, 0, 3, 10)` kiểm tra phần sau chỉ số 3 không?** Không; nó chỉ parse đoạn được chỉ định.
10. **Có nên chuyển `"0007"` thành số 7 nếu đó là mã định danh?** Thường không; mã định danh cần giữ định dạng ban đầu, nên thường giữ dưới dạng chuỗi.

**Kết luận:** parsing số nguyên an toàn là sự phối hợp của **cú pháp toàn chuỗi**, **quy tắc khoảng trắng**, **bộ ký tự cho phép**, **giới hạn dải số** và **ràng buộc nghiệp vụ**. Java `parseInt`/`parseLong` tiêu thụ toàn bộ chuỗi số nguyên đã nhận nhưng không tự `strip`; các API theo locale có thể parse một tiền tố nếu không kiểm tra vị trí dừng. Hãy chọn STRICT hoặc OUTER-STRIP có chủ đích, xác thực ASCII/Unicode đúng đặc tả, và kiểm thử đủ các trường hợp dư ký tự, khoảng trắng, null, Unicode và overflow.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-parsing-011">Chọn BigInteger cho chuỗi số vượt giới hạn long</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn BigInteger cho chuỗi số vượt giới hạn long trong Java

## 1. Khi nào cần BigInteger?

Nếu đầu vào là **chuỗi biểu diễn số nguyên thập phân** và giá trị có thể vượt giới hạn kiểu **long**, hãy dùng **java.math.BigInteger** khi cần giữ nguyên **giá trị số học chính xác**.

Miền của long là:

- **Long.MIN_VALUE = −9223372036854775808** (−2^63).
- **Long.MAX_VALUE = 9223372036854775807** (2^63−1).

Ví dụ chuỗi **"9223372036854775808"** chỉ lớn hơn Long.MAX_VALUE đúng 1 đơn vị nhưng đã không thể biểu diễn như số dương trong long. Chuỗi **"-9223372036854775809"** lại nhỏ hơn Long.MIN_VALUE đúng 1 đơn vị. Cả hai đều có thể được biểu diễn chính xác bằng BigInteger.

~~~java
import java.math.BigInteger;

BigInteger aboveMax = new BigInteger("9223372036854775808");
BigInteger belowMin = new BigInteger("-9223372036854775809");
~~~

Hai dòng là đoạn mã minh họa, dùng trong một phương thức/class Java. **BigInteger** biểu diễn số nguyên có dấu với độ chính xác tùy ý (arbitrary precision), chịu các giới hạn tài nguyên và triển khai, thay vì giới hạn ở 64 bit.

**Điểm quyết định không nằm ở độ dài chuỗi đơn thuần**, mà ở **miền giá trị cần hỗ trợ** và **cách sử dụng dữ liệu sau đó**. Một chuỗi dài 30 chữ số bắt đầu bằng nhiều số 0 vẫn có thể là số nhỏ; ngược lại, số dương 19 chữ số đã có thể vượt Long.MAX_VALUE.

## 2. Phân biệt ba bài toán trước khi chọn kiểu

| Tình huống | Cách biểu diễn thường phù hợp | Lý do |
| --- | --- | --- |
| Số nguyên luôn nằm trong miền signed 64 bit | **long/Long** | Gọn, nhanh, có phép toán nguyên thủy |
| Số nguyên có thể vượt signed long và cần tính toán chính xác | **BigInteger** | Không làm tràn miền long theo phép toán thông thường |
| Mã định danh chỉ chứa chữ số, có thể có số 0 đầu, không dùng để tính | **String** | Giữ nguyên toàn bộ ký tự, kể cả số 0 đầu và định dạng |
| Số có phần thập phân cần độ chính xác thập phân | **BigDecimal** | BigInteger chỉ biểu diễn **số nguyên** |
| Chuỗi số không dấu 64 bit nhưng cần giá trị dương đúng đến 2^64−1 | **BigInteger** | long vẫn dùng bit dấu và không chứa được toàn miền dưới dạng số dương |
| Dữ liệu chỉ cần biểu diễn mẫu bit unsigned 64 bit và API được thiết kế cho nó | **long** cùng các API unsigned | Phải thống nhất quy ước unsigned, không coi long là số dương |

Nếu đây là số tài khoản, số điện thoại, mã bưu chính hoặc ID **không có phép toán số học**, giữ dạng String thường đúng hơn. Chuyển **"000123"** sang BigInteger rồi in lại sẽ nhận **"123"**: giá trị số không đổi, nhưng định danh có thể đã sai.

Nếu bài toán thực sự là cộng, trừ, nhân, chia, kiểm tra nguyên tố, lũy thừa nguyên hoặc xử lý số nguyên rất lớn, BigInteger là lựa chọn trực tiếp.

## 3. Vì sao Long.parseLong thất bại và không nên ép số qua double?

~~~java
long maximum = Long.parseLong("9223372036854775807"); // Hợp lệ

// Khi thực thi sẽ ném NumberFormatException:
// long tooLarge = Long.parseLong("9223372036854775808");
~~~

**Long.parseLong** ném **NumberFormatException** khi chuỗi không đúng cú pháp *hoặc* khi giá trị vượt phạm vi long. Nếu cần phân biệt lỗi cú pháp với vượt miền, hãy xác thực chuỗi theo quy ước đầu vào rồi so sánh giá trị BigInteger với hai biên long.

Không chuyển qua **double** để né giới hạn long: double có thể biểu diễn số rất lớn về *độ lớn* nhưng không thể biểu diễn chính xác mọi số nguyên khi độ lớn tăng. Số nguyên liền kề có thể làm tròn thành cùng một double, vì significand double chỉ có 53 bit chính xác.

~~~java
double a = 9007199254740992.0; // 2^53
double b = 9007199254740993.0; // làm tròn về cùng double như a
boolean equal = (a == b);       // true
~~~

Nếu chuỗi đã đi qua hệ thống ngoài Java, ví dụ JSON consumer dùng JavaScript Number, giá trị có thể đã mất độ chính xác **trước** khi vào Java. Đối với số lớn cần chính xác xuyên hệ thống, truyền chuỗi thập phân hoặc dùng kiểu số lớn được hai phía thỏa thuận.

## 4. Tạo BigInteger trực tiếp từ String

Thêm import:

~~~java
import java.math.BigInteger;
~~~

Constructor phổ biến nhất là **new BigInteger(String val)**, giải mã giá trị nguyên thập phân có dấu:

~~~java
BigInteger zero = new BigInteger("0");
BigInteger positive = new BigInteger("+123456789012345678901234567890");
BigInteger negative = new BigInteger("-123456789012345678901234567890");
BigInteger withZeros = new BigInteger("00000099"); // giá trị số 99
~~~

Chuỗi đầu vào có thể có dấu **+** hoặc **−** ở đầu; dấu không được đứng riêng. Constructor sẽ từ chối cú pháp không hợp lệ bằng NumberFormatException.

Ví dụ các chuỗi **không nên đưa thẳng vào constructor thập phân**:

| Chuỗi | Vấn đề |
| --- | --- |
| **""** | Không có chữ số |
| **"+"**, **"-"** | Chỉ có dấu |
| **"12.0"** | Có phần thập phân, không phải số nguyên |
| **"1_000"** | Dấu gạch dưới chỉ là cú pháp literal trong mã nguồn Java, không phải cú pháp thập phân của chuỗi |
| **" 123 "** | Có khoảng trắng ở đầu/cuối; constructor không tự trim |
| **"0xFF"** | Prefix 0x không tự được nhận diện bởi constructor thập phân |

Nếu nghiệp vụ cho phép khoảng trắng ngoài chuỗi, có thể gọi **strip()** trước khi parse. Nhưng phải quyết định quy tắc này ở ranh giới nhập liệu; không tự động xóa ký tự khi chúng có ý nghĩa.

### Chọn cơ số với constructor hai tham số

**new BigInteger(String val, int radix)** cho phép đọc cơ số từ **Character.MIN_RADIX = 2** tới **Character.MAX_RADIX = 36**:

~~~java
BigInteger binary = new BigInteger("101010", 2); // 42
BigInteger hex = new BigInteger("FF", 16);        // 255
BigInteger negativeHex = new BigInteger("-FF", 16); // -255
~~~

Khi dùng radix 16, truyền **"FF"**, không truyền **"0xFF"** vào constructor rồi mong nó tự loại bỏ prefix. Nếu định dạng đầu vào quy định prefix **0x**, hãy xác thực và loại bỏ đúng prefix theo giao thức trước khi gọi constructor.

**BigInteger.toString()** cho dạng thập phân chuẩn hóa; **toString(16)** cho chuỗi hexadecimal (mặc định chữ thường, không có prefix "0x"). Giá trị âm có dấu trừ:

~~~java
BigInteger value = new BigInteger("9223372036854775808");
String hex = value.toString(16);   // "8000000000000000"
BigInteger parsedBack = new BigInteger(hex, 16);
boolean same = parsedBack.equals(value); // true
~~~

## 5. Kiểm tra đầu vào trước khi parse số quá lớn

Constructor BigInteger báo lỗi cú pháp, nhưng đối với đầu vào không tin cậy, ứng dụng thường cần quy định **giới hạn độ dài** và **bộ chữ số được chấp nhận** để ngăn tiêu thụ tài nguyên quá mức.

Ví dụ, một hợp đồng chỉ chấp nhận **chữ số ASCII 0..9**, một dấu tùy chọn ở đầu và tối đa N chữ số. Hàm sau xác thực theo đúng hợp đồng đó:

~~~java
static BigInteger parseDecimal(String text, int maxDigits) {
    if (text == null || maxDigits < 1) {
        throw new IllegalArgumentException("Invalid input or limit");
    }

    int start = text.startsWith("+") || text.startsWith("-") ? 1 : 0;
    int count = text.length() - start;

    if (count < 1 || count > maxDigits) {
        throw new IllegalArgumentException("Invalid digit count");
    }

    for (int i = start; i < text.length(); i++) {
        char digit = text.charAt(i);
        if (digit < '0' || digit > '9') {
            throw new IllegalArgumentException("Expected ASCII decimal digits");
        }
    }

    return new BigInteger(text);
}
~~~

Đây là **phương thức thành viên đặt bên trong class**, cần import java.math.BigInteger. Hàm sẽ:

1. Từ chối null hoặc maxDigits không hợp lệ.
2. Cho phép một dấu **+/-** tùy chọn, không tính vào hạn mức chữ số.
3. Từ chối chuỗi không có chữ số, quá dài hoặc chứa ký tự khác ASCII 0..9.
4. Chỉ tạo BigInteger khi cú pháp đã đạt điều kiện.

Hàm này có chủ đích **không nhận khoảng trắng, dấu phân nhóm, dấu gạch dưới, chữ số Unicode ngoài ASCII, exponent notation**. Nếu sản phẩm cần các định dạng đó, hãy định nghĩa parser tương ứng thay vì lặng lẽ chấp nhận.

Hạn mức **maxDigits** không phải hằng cố định cho mọi ứng dụng: đặt theo hợp đồng API và sức chịu tải. Một số gồm hàng triệu chữ số vừa tốn chi phí parse/lưu, vừa có thể khiến các phép nhân hoặc lũy thừa về sau tốn CPU/bộ nhớ lớn. Giới hạn tài nguyên vẫn cần thiết dù BigInteger không bị giới hạn 64 bit.

## 6. So sánh một BigInteger có nằm trong miền long không

Không dùng **longValue()** để kiểm tra liệu số có vừa long hay không: phương thức đó có thể **cắt bỏ bit cao** mà không báo lỗi.

Dùng **compareTo** với hai đầu miền:

~~~java
static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

static boolean fitsLong(BigInteger value) {
    return value.compareTo(LONG_MIN) >= 0
            && value.compareTo(LONG_MAX) <= 0;
}
~~~

Đây là các trường/phương thức đặt trong class. **BigInteger.compareTo** trả về số âm, 0 hoặc số dương tùy quan hệ lớn/nhỏ/bằng; không so sánh BigInteger bằng toán tử **<** hay **>**.

Nếu chắc chắn giá trị nằm trong miền long, chuyển chính xác bằng **longValueExact()**:

~~~java
BigInteger ok = new BigInteger("1234567890123");
long n = ok.longValueExact(); // 1234567890123L
~~~

Nếu vượt miền, **longValueExact()** ném **ArithmeticException** thay vì tạo kết quả sai. Các phương thức **intValueExact()**, **shortValueExact()** và **byteValueExact()** cung cấp hành vi tương tự cho miền nhỏ hơn.

### Tại sao longValue() có thể nguy hiểm?

~~~java
BigInteger above = new BigInteger("9223372036854775808");
long truncated = above.longValue(); // -9223372036854775808
// above.longValueExact() -> ArithmeticException
~~~

**9223372036854775808 = 2^63**. Khi chuyển theo **longValue()**, 64 bit thấp được diễn giải là một long có dấu, trở thành **Long.MIN_VALUE**. Dữ liệu bị đổi dấu nhưng không có exception.

Nguyên tắc thực hành: dùng **longValueExact()** tại ranh giới API cần long, và chỉ dùng **longValue()** khi chủ động muốn lấy các bit thấp theo ngữ nghĩa được tài liệu mô tả.

## 7. Thực hiện phép toán chính xác với BigInteger

Khác kiểu nguyên thủy, BigInteger là **đối tượng bất biến** (immutable). Mỗi phép toán tạo/trả đối tượng kết quả; không cập nhật giá trị toán học của đối tượng cũ.

| Bài toán | API BigInteger |
| --- | --- |
| Cộng, trừ | **add**, **subtract** |
| Nhân | **multiply** |
| Chia nguyên | **divide** |
| Số dư theo phép chia | **remainder** |
| Thương và dư cùng lúc | **divideAndRemainder** |
| So sánh | **compareTo**, **equals** |
| Trị tuyệt đối, đổi dấu | **abs**, **negate** |
| Lũy thừa nguyên không âm | **pow(int exponent)** |
| Dịch bit | **shiftLeft**, **shiftRight** |
| Kiểm tra số bit | **bitLength**, **testBit** |

Ví dụ:

~~~java
BigInteger a = new BigInteger("9223372036854775808");
BigInteger b = a.add(BigInteger.TWO);           // 9223372036854775810
BigInteger product = a.multiply(BigInteger.valueOf(3));
// 27670116110564327424

BigInteger[] result = a.divideAndRemainder(BigInteger.valueOf(10));
// result[0] = 922337203685477580, result[1] = 8
~~~

Các hằng **BigInteger.ZERO**, **ONE**, **TEN** dùng được trong Java 17; **BigInteger.TWO** cũng có từ Java 9. Tránh viết **a + b** khi a, b là BigInteger vì không có operator overloading cho dấu cộng trên đối tượng.

**BigInteger.divide** là chia nguyên, làm tròn thương về 0. Nếu cần số thập phân của phép chia, hãy sử dụng **BigDecimal** hoặc định dạng phân số theo hợp đồng. Với số âm, **remainder** và **mod** không phải luôn thay thế được nhau: **mod** yêu cầu modulus dương và trả giá trị không âm.

## 8. Chuỗi unsigned 64 bit: hai miền khác nhau

Miền số nguyên unsigned 64 bit là **0 .. 18446744073709551615** (2^64−1), lớn hơn miền long dương tối đa.

~~~java
String digits = "18446744073709551615";
BigInteger unsignedMax = new BigInteger(digits); // dương, chính xác
long bits = Long.parseUnsignedLong(digits);      // -1 theo long có dấu
String recovered = Long.toUnsignedString(bits);  // "18446744073709551615"
~~~

**Long.parseUnsignedLong** phù hợp khi cần lưu **mẫu bit 64 bit** và dùng các API **compareUnsigned**, **divideUnsigned**, **remainderUnsigned**, **toUnsignedString** đúng quy ước. Nếu nghiệp vụ cần so sánh, cộng/trừ/tính toán một **số nguyên dương không dấu** với những giá trị có thể vượt 2^64−1, BigInteger thường trực tiếp và ít nhầm lẫn hơn.

Đừng lấy **Long.parseUnsignedLong** rồi so sánh bằng toán tử **>** thông thường: giá trị long bên trong có thể âm do bit dấu. Phải giữ nhất quán signed-vs-unsigned.

## 9. Khi dữ liệu đến từ API, JSON, CSV và database

**CSV/text:** nhập như String, xác thực cú pháp/độ dài, parse BigInteger. Khi xuất, gọi **toString()**. Nếu cần giữ zero đầu hoặc cách viết nguyên gốc, lưu thêm **chuỗi gốc**, vì BigInteger chuẩn hóa biểu diễn số.

**JSON:** theo chuẩn JSON, token số không đảm bảo mọi consumer lưu chính xác số nguyên cực lớn. Nhiều ứng dụng JavaScript dùng **Number** và chỉ biểu diễn chính xác các số nguyên trong miền an toàn **±(2^53−1)**. Nên có hợp đồng truyền số lớn dưới dạng **chuỗi**, hoặc dùng cơ chế parser/BigInt phù hợp được thống nhất. Không mặc nhiên gửi BigInteger như JSON number rồi cho rằng mọi client sẽ đọc chính xác.

**Database:** chọn kiểu cột hỗ trợ miền dữ liệu và thư viện ánh xạ tương ứng, như một kiểu **DECIMAL/NUMERIC** với precision đủ lớn nếu hệ quản trị hỗ trợ. Không giả sử kiểu BIGINT SQL hỗ trợ BigInteger không giới hạn, vì nhiều hệ quản trị dùng BIGINT 64 bit. Kiểm tra precision, scale, overflow/truncation và driver theo cơ sở dữ liệu thực tế.

**Message queue/gRPC:** cần có định dạng giao tiếp cụ thể. Một trường int64 cố định không thể biểu diễn số dương trên 2^63−1 theo signed semantics. Có thể dùng chuỗi decimal hoặc biểu diễn byte có quy ước rõ nếu hệ thống yêu cầu.

## 10. Chương trình đầy đủ Java 17+ và kết quả thực tế

Tạo tệp **BigIntegerTextDemo.java**:

~~~java
import java.math.BigInteger;

public class BigIntegerTextDemo {
    static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    static BigInteger parseDecimal(String text, int maxDigits) {
        if (text == null || maxDigits < 1) {
            throw new IllegalArgumentException("Invalid input or limit");
        }

        int start = text.startsWith("+") || text.startsWith("-") ? 1 : 0;
        int count = text.length() - start;

        if (count < 1 || count > maxDigits) {
            throw new IllegalArgumentException("Invalid digit count");
        }

        for (int i = start; i < text.length(); i++) {
            char digit = text.charAt(i);
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException("Expected ASCII decimal digits");
            }
        }
        return new BigInteger(text);
    }

    static boolean fitsLong(BigInteger value) {
        return value.compareTo(LONG_MIN) >= 0
                && value.compareTo(LONG_MAX) <= 0;
    }

    public static void main(String[] args) {
        BigInteger above = parseDecimal("9223372036854775808", 24);
        BigInteger below = parseDecimal("-9223372036854775809", 24);
        BigInteger unsignedMax =
                parseDecimal("18446744073709551615", 24);

        System.out.println("Long.MAX_VALUE: " + Long.MAX_VALUE);
        System.out.println("First above long: " + above);
        System.out.println("Above fits long: " + fitsLong(above));
        System.out.println("Below fits long: " + fitsLong(below));
        System.out.println("Unsigned 64 max: " + unsignedMax);
        System.out.println("Unsigned max fits signed long: "
                + fitsLong(unsignedMax));

        System.out.println("Leading zeros: "
                + parseDecimal("+0009223372036854775808", 24));
        System.out.println("Above + 2: " + above.add(BigInteger.TWO));
        System.out.println("Above * 3: "
                + above.multiply(BigInteger.valueOf(3)));

        System.out.println("Above in hex: " + above.toString(16));
        System.out.println("Round trip: "
                + new BigInteger(above.toString(16), 16).equals(above));
        System.out.println("Truncated longValue: " + above.longValue());

        try {
            System.out.println(above.longValueExact());
        } catch (ArithmeticException e) {
            System.out.println("longValueExact: out of range");
        }

        try {
            System.out.println(Long.parseLong("9223372036854775808"));
        } catch (NumberFormatException e) {
            System.out.println("parseLong: out of range");
        }

        try {
            parseDecimal("1_000", 24);
        } catch (IllegalArgumentException e) {
            System.out.println("strict decimal: invalid syntax");
        }

        System.out.println("Matches MAX + ONE: "
                + above.equals(LONG_MAX.add(BigInteger.ONE)));
        System.out.println("Unsigned parse as long: "
                + Long.parseUnsignedLong("18446744073709551615"));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 BigIntegerTextDemo.java
java BigIntegerTextDemo
~~~

Kết quả stdout:

~~~text
Long.MAX_VALUE: 9223372036854775807
First above long: 9223372036854775808
Above fits long: false
Below fits long: false
Unsigned 64 max: 18446744073709551615
Unsigned max fits signed long: false
Leading zeros: 9223372036854775808
Above + 2: 9223372036854775810
Above * 3: 27670116110564327424
Above in hex: 8000000000000000
Round trip: true
Truncated longValue: -9223372036854775808
longValueExact: out of range
parseLong: out of range
strict decimal: invalid syntax
Matches MAX + ONE: true
Unsigned parse as long: -1
~~~

**Ý nghĩa chính:**

- Số dương **2^63** không vừa signed long nhưng BigInteger giữ nguyên.
- Khi chuyển ngược về long, **longValue()** lấy 64 bit thấp và có thể thành số âm; **longValueExact()** sẽ báo lỗi.
- Số nguyên **2^64−1** có thể lưu dưới dạng BigInteger dương, nhưng Long.parseUnsignedLong trả về mẫu bit mà long signed đọc là −1.
- Chuỗi có dấu cộng và zero đầu được parse thành **giá trị số chuẩn hóa**, không giữ hình thức chuỗi.
- Các phép toán add/multiply vẫn chính xác trong miền minh họa.

## 11. Hiệu năng và an toàn tài nguyên

BigInteger hỗ trợ số nguyên rất lớn, nhưng **không miễn phí**:

- Parse chuỗi dài tạo các cấu trúc dữ liệu lớn và cần thời gian CPU.
- Phép nhân, chia, lũy thừa và modular exponentiation có chi phí tăng theo độ dài số.
- Nếu một yêu cầu mạng có thể gửi chuỗi hàng triệu chữ số, cần giới hạn **số chữ số và tài nguyên** trước khi tạo hoặc tính trên BigInteger.
- Nếu mọi giá trị được chứng minh nằm trong long, primitive long thường đơn giản và hiệu quả hơn.
- BigInteger là immutable và thread-safe theo cách dùng giá trị bất biến, nhưng vẫn phải quản lý cập nhật tham chiếu chia sẻ đúng quy tắc đồng thời.

Không tối ưu bằng cách **cắt chuỗi 19 chữ số đầu rồi parse long**: cách đó làm mất dữ liệu và có thể xử lý sai dấu, zero đầu, hoặc số có nhiều chữ số. Nếu cần chính xác, parse đúng giá trị hoặc từ chối với thông báo có căn cứ.

## 12. Những lỗi phổ biến

| Sai lầm | Hệ quả | Cách làm đúng |
| --- | --- | --- |
| Dùng Long.parseLong rồi nghĩ nó tự mở rộng khi tràn | NumberFormatException | Parse BigInteger nếu miền có thể vượt long |
| Dùng double để chứa số nguyên cực lớn | Có thể mất các chữ số thấp | Dùng BigInteger khi yêu cầu chính xác nguyên |
| Dùng BigInteger.longValue() để kiểm tra có vừa long | Cắt bit cao, có thể đổi dấu | compareTo với biên hoặc longValueExact() |
| Cho rằng độ dài > 19 chữ số luôn vượt long | Zero đầu có thể làm giá trị nhỏ | Parse/chuẩn hóa rồi so sánh miền |
| Cho rằng 19 chữ số luôn vừa long | 9223372036854775808 đã vượt Long.MAX_VALUE | So sánh với hai biên chính xác |
| Cho rằng parseUnsignedLong cho long dương vượt MAX_VALUE | Kết quả là mẫu bit long có dấu | Dùng API unsigned nhất quán hoặc BigInteger |
| Dùng toán tử + cho hai BigInteger | Không biên dịch | Dùng a.add(b) |
| Không gán giá trị trả về từ phép cộng | Đối tượng cũ không đổi | Gán result = a.add(b) |
| Cho rằng BigInteger giữ zero đầu | toString chuẩn hóa dạng số | Giữ bản String gốc nếu cần |
| Parse số khổng lồ không giới hạn từ client | Rủi ro tốn CPU và bộ nhớ | Giới hạn độ dài và phạm vi xử lý |
| Dùng BigInteger cho số thập phân có dấu phẩy | NumberFormatException | Xem xét BigDecimal |
| Coi SQL BIGINT đồng nghĩa BigInteger | Có thể bị giới hạn 64 bit | Kiểm tra kiểu cột và precision |

## 13. Bài tập tự kiểm tra

**Bài 1.** Vì sao **"9223372036854775808"** không parse được bằng Long.parseLong nhưng parse được bằng BigInteger?

*Đáp án:* Giá trị bằng **2^63**, vượt cực đại signed long **2^63−1**; BigInteger không bị giới hạn ở 64 bit.

**Bài 2.** Nếu chuyển BigInteger biểu diễn **2^63** bằng longValue(), kết quả có dấu là bao nhiêu?

*Đáp án:* **−9223372036854775808** do 64 bit thấp được diễn giải theo long có dấu. **longValueExact()** phải ném ArithmeticException.

**Bài 3.** Chuỗi **"00000000000000000000000042"** có nhất thiết lớn hơn Long.MAX_VALUE không?

*Đáp án:* Không; giá trị nguyên là 42. Số lượng chữ số nguyên bản không đủ để xác định miền khi có zero đầu.

**Bài 4.** Tại sao một mã định danh **"00042"** có thể không nên chuyển sang BigInteger?

*Đáp án:* Nếu các số 0 đầu là một phần định danh, BigInteger sẽ chuẩn hóa thành "42" và làm mất dạng gốc.

**Bài 5.** Nếu ứng dụng dùng unsigned 64 bit và nhận chuỗi **"18446744073709551615"**, nên chọn cách nào để tính toán tiếp trên một **giá trị dương**?

*Đáp án:* BigInteger biểu diễn số dương đó trực tiếp. Nếu lưu long làm mẫu bit, phải dùng nhất quán API unsigned; toán tử signed có thể cho kết quả âm.

**Bài 6.** Làm sao trả về long khi một BigInteger vừa miền, đồng thời từ chối khi không vừa?

*Đáp án:* Dùng **longValueExact()**, có thể kiểm tra trước với **compareTo(Long.MIN_VALUE/MAX_VALUE)** đã chuyển thành BigInteger.

**Bài 7.** Vì sao phải giới hạn số chữ số đầu vào dù BigInteger có độ chính xác tùy ý?

*Đáp án:* Parse, lưu trữ và tính toán số cực lớn vẫn có chi phí CPU/bộ nhớ; giới hạn ngăn việc dùng tài nguyên không kiểm soát.

## 14. Tài liệu chính thức

- [BigInteger Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): constructor theo String/radix, toString, compareTo, phép toán bất biến và longValueExact.
- [Long Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html): giới hạn signed 64 bit, parseLong, parseUnsignedLong, toUnsignedString.
- [BigDecimal Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): khi dữ liệu có phần thập phân.
- [Java Language Specification SE 17 — Integer Types and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.1): miền và biểu diễn primitive int/long.


</details>

- [Quay lại đầu trang](#back-to-top)
