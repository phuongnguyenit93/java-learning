<a id="back-to-top"></a>

# Biểu diễn và định dạng

## Menu
- [Integer.toString, Long.toString và radix](#numeric-formatting-001)
- [toBinaryString, toHexString và toUnsignedString](#numeric-formatting-002)
- [Float.toString, Double.toString và tính chất round-trip](#numeric-formatting-003)
- [toHexString cho dấu phẩy động](#numeric-formatting-004)
- [String.format, printf và Formatter](#numeric-formatting-005)
- [NumberFormat cho số thường, phần trăm và tiền tệ](#numeric-formatting-006)
- [DecimalFormat, pattern và DecimalFormatSymbols](#numeric-formatting-007)
- [Locale, dấu thập phân, nhóm chữ số và dấu âm](#numeric-formatting-008)
- [Phân biệt chữ số tối thiểu, tối đa và độ chính xác giá trị](#numeric-formatting-009)
- [RoundingMode của bộ định dạng](#numeric-formatting-010)
- [ParsePosition và kiểm tra toàn bộ chuỗi khi parse theo locale](#numeric-formatting-011)
- [setParseBigDecimal và giới hạn an toàn khi dùng formatter đồng thời](#numeric-formatting-012)
- [Không dùng chuỗi đã định dạng cho người đọc làm dạng trao đổi dữ liệu mặc định](#numeric-formatting-013)

## <a id="numeric-formatting-001">Integer.toString, Long.toString và radix</a>

<details>
<summary>Xem chi tiết</summary>

# `Integer.toString`, `Long.toString` và `radix` trong Java

Java lưu các giá trị `int` và `long` dưới dạng số nguyên nhị phân có dấu. Khi chương trình cần hiển thị ra màn hình, ghi log, trao đổi dữ liệu hoặc xuất văn bản theo hệ 2, 8, 10, 16..., ta phải **định dạng giá trị số thành `String`**. `Integer.toString` và `Long.toString` là hai API cơ bản thực hiện việc này.

```java
int number = 42;

System.out.println(Integer.toString(number));      // 42
System.out.println(Integer.toString(number, 2));   // 101010
System.out.println(Integer.toString(number, 8));   // 52
System.out.println(Integer.toString(number, 16));  // 2a
```

Cả bốn kết quả là các **chuỗi khác nhau biểu diễn cùng giá trị số nguyên 42**. Tham số `radix` quyết định **cơ số khi chuyển thành chữ số**, không biến đổi giá trị gốc của biến và không tạo thêm tiền tố như `0x`. Với số âm, còn có sự khác biệt quan trọng giữa định dạng **giá trị có dấu** bằng `toString(value, radix)` và định dạng **mẫu bit không dấu** bằng `toHexString`, `toBinaryString` hay `toUnsignedString`.

Bài này tập trung vào hợp đồng của hai phương thức, các cơ số hợp lệ, âm/dương và giá trị biên, mối quan hệ với parser, những cách định dạng có tiền tố/độ rộng, và các lỗi thường gặp khi lưu hoặc truyền số.

## 1. Giá trị số khác với cách viết số

Giả sử chương trình có:

```java
int a = 42;
int b = 0x2A;
int c = 0b101010;
int d = 052;
```

Trong Java source, compiler hiểu bốn **integer literal** theo cơ số 10, 16, 2 và 8. Nhưng tất cả biến ở đây đều chứa giá trị số học **42**. Một biến `int` **không lưu ký ức** rằng nó được gán bằng literal hex hay binary.

`Integer.toString(a, 16)`, `Integer.toString(b, 16)`, `Integer.toString(c, 16)` và `Integer.toString(d, 16)` đều trả `"2a"`. Phương thức không tìm lại cú pháp literal ban đầu: nó nhận **giá trị**, sau đó xây chuỗi chữ số theo cơ số yêu cầu.

### Các khái niệm cần phân biệt

| Khái niệm | Ví dụ | Giai đoạn và tác dụng |
| --- | --- | --- |
| Numeric literal | `0x2A`, `0b101010`, `42` | Cú pháp mã nguồn, do compiler phân tích |
| Primitive numeric value | `int value = 42;` | Giá trị số học được thao tác khi chạy |
| Numeric formatting | `Integer.toString(value, 16)` | Tạo `String` biểu diễn giá trị theo cơ số 16 |
| Numeric parsing | `Integer.parseInt("2a", 16)` | Đọc `String` theo cơ số và tạo giá trị `int` |
| Unsigned bit formatting | `Integer.toHexString(-1)` | Diễn giải mẫu 32 bit theo cách không dấu |

**Định dạng và phân tích là hai chiều khác nhau.** Không nên dùng `parseInt` khi mục tiêu là *hiển thị* số; cũng không dùng `toString` để *kiểm tra* một chuỗi đầu vào.

## 2. Các overload quan trọng

`Integer` và `Long` nằm trong `java.lang` nên không cần import riêng.

| API | Tham số | Kiểu trả về | Ý nghĩa |
| --- | --- | --- | --- |
| `Integer.toString(int value)` | Một `int` | `String` | Biểu diễn thập phân có dấu |
| `Integer.toString(int value, int radix)` | Giá trị và cơ số | `String` | Biểu diễn có dấu trong cơ số yêu cầu |
| `Long.toString(long value)` | Một `long` | `String` | Biểu diễn thập phân có dấu |
| `Long.toString(long value, int radix)` | Giá trị và cơ số | `String` | Biểu diễn có dấu trong cơ số yêu cầu |

```java
int count = -42;
long distance = 4_294_967_296L;

String a = Integer.toString(count);       // "-42"
String b = Integer.toString(count, 16);   // "-2a"
String c = Long.toString(distance);       // "4294967296"
String d = Long.toString(distance, 16);   // "100000000"
```

Phân biệt tên lớp theo kiểu dữ liệu: `Long.toString` nhận `long` và xử lý được miền rộng hơn `int`. Bạn **không** thể kỳ vọng gọi `Integer.toString((int) someLong)` giữ nguyên một giá trị `long` vượt giới hạn `int`, bởi thông tin có thể mất ở **phép cast trước lời gọi**.

Các phương thức trên là `static`: dùng `Integer.toString(...)` hoặc `Long.toString(...)`. Không cần tạo `new Integer(...)`/`new Long(...)` để định dạng.

### `String.valueOf` và nối chuỗi là gì?

`String.valueOf(42)`, `Integer.toString(42)` và `"" + 42` thường cho chuỗi `"42"` theo hệ 10. Nhưng:

- `String.valueOf(int)`/`String.valueOf(long)` không có tham số `radix`.
- `Integer.toString(value, radix)` diễn đạt rõ ý muốn chọn hệ cơ số khác.
- `Integer.valueOf(int)` lại **tạo/lấy đối tượng wrapper `Integer`**, không phải tạo chuỗi.
- `Integer.toString()` gọi trên **đối tượng wrapper** là phương thức instance không tham số; còn `Integer.toString(int)` là phương thức static. Đừng nhầm chữ ký.

Trong mã muốn hiển thị binary/hex một cách tường minh, chọn `toString(value, radix)` hoặc API chuyên biệt phù hợp ngữ nghĩa có dấu/không dấu.

## 3. `radix`: cơ số 2 đến 36

`radix` là cơ số dùng để diễn đạt một số bằng các chữ số. Với hai overload nhận radix của `Integer.toString` và `Long.toString`, các giá trị hợp lệ là:

```java
Character.MIN_RADIX // 2
Character.MAX_RADIX // 36
```

Java dùng các ký tự chữ số:

```text
0 1 2 3 4 5 6 7 8 9 a b c ... z
```

- Với radix 2: `0`, `1`.
- Với radix 8: `0` đến `7`.
- Với radix 10: `0` đến `9`.
- Với radix 16: `0` đến `9` và `a` đến `f`.
- Với radix 36: `0` đến `9` và `a` đến `z`.

Các chữ cái trong chuỗi do hai phương thức tạo ra là **chữ thường** (`a..z`).

```java
System.out.println(Integer.toString(42, 2));   // 101010
System.out.println(Integer.toString(42, 8));   // 52
System.out.println(Integer.toString(42, 10));  // 42
System.out.println(Integer.toString(42, 16));  // 2a
System.out.println(Integer.toString(35, 36));  // z
```

**Không có radix 1, 0, 37 hay −2** trong API này. Một điểm không hiển nhiên: `toString(value, radix)` **không ném `NumberFormatException` khi radix ngoài 2–36**; thay vào đó, nó **dùng thập phân (radix 10)** theo hợp đồng JDK.

```java
System.out.println(Integer.toString(255, 1));  // 255, fallback radix 10
System.out.println(Long.toString(255L, 37));   // 255, fallback radix 10
```

Vì cơ số sai có thể bị che khuất, một ứng dụng nhận `radix` từ người dùng nên **tự kiểm tra phạm vi**:

```java
static String formatStrictRadix(int value, int radix) {
    if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
        throw new IllegalArgumentException("radix must be from 2 to 36");
    }
    return Integer.toString(value, radix);
}
```

Đây là hàm minh họa cần đặt trong class. Nó xây dựng chính sách **báo lỗi rõ ràng khi radix sai**, chặt hơn lựa chọn fallback thập phân của `Integer.toString`.

### `radix` không làm đổi số

Một số nguyên `42` vẫn là `42` dù được hiển thị `101010`, `52` hay `2a`. Chỉ chuỗi kết quả khác. Muốn đưa chuỗi trở về giá trị, parser phải biết đúng cơ số tương ứng:

```java
int original = 42;
String encoded = Integer.toString(original, 16);  // "2a"
int decoded = Integer.parseInt(encoded, 16);      // 42
```

Nếu `Integer.parseInt("2a")` được gọi không có radix 16, nó sẽ ném `NumberFormatException` vì parser mặc định thập phân và ký tự `a` không hợp lệ trong radix 10.

## 4. Các trường hợp số 0, số dương và số âm

### Giá trị 0

```java
System.out.println(Integer.toString(0));       // "0"
System.out.println(Integer.toString(0, 2));    // "0"
System.out.println(Long.toString(0L, 36));     // "0"
```

Số 0 luôn có biểu diễn chuỗi `"0"` trong các radix hợp lệ; không có chuỗi rỗng hoặc dấu âm.

### Số âm: dấu `-` đi trước các chữ số

```java
System.out.println(Integer.toString(-42));      // -42
System.out.println(Integer.toString(-42, 2));   // -101010
System.out.println(Integer.toString(-42, 16));  // -2a
System.out.println(Long.toString(-42L, 36));    // -16
```

`Integer.toString(-42, 16)` trả **`"-2a"`**, không trả chuỗi mẫu bit 32-bit của số âm. Phần chữ số `2a` biểu diễn **độ lớn 42**, còn `-` diễn đạt dấu. Vì vậy hai chuỗi:

- `Integer.toString(-1, 16)` → `"-1"`;
- `Integer.toHexString(-1)` → `"ffffffff"`;

đều liên quan đến cùng giá trị `int -1` nhưng **khác quy ước hiển thị**.

### Giá trị biên `MIN_VALUE` có được xử lý đúng?

```java
System.out.println(Integer.toString(Integer.MIN_VALUE));
// -2147483648
System.out.println(Long.toString(Long.MIN_VALUE));
// -9223372036854775808
```

`Integer.MIN_VALUE` bằng `-2^31`, `Long.MIN_VALUE` bằng `-2^63`. Giá trị dương tương ứng không biểu diễn được trong cùng kiểu signed. Các phương thức `toString` của JDK **xử lý đúng** hai giá trị này. Không tự cài đặt formatter bằng cách lấy `Math.abs(value)` trước rồi đổi cơ số mà quên tràn:

```java
System.out.println(Math.abs(Integer.MIN_VALUE));
// -2147483648: vẫn âm vì overflow.
```

Nếu cần tự triển khai thuật toán chuyển chữ số, phải xử lý MIN_VALUE bằng kỹ thuật an toàn như làm việc trên giá trị âm hoặc mở rộng kiểu trước. Với nhu cầu ứng dụng bình thường, dùng phương thức JDK có sẵn đơn giản hơn.

## 5. So sánh định dạng signed với biểu diễn mẫu bit

`Integer.toString(value, radix)` và `Long.toString(value, radix)` chuyển **giá trị có dấu** thành chữ số theo cơ số đã chọn. Với số âm, chúng đặt dấu trừ trước phần độ lớn: `Integer.toString(-42,16)` là `"-2a"`. Trong khi đó, `Integer.toHexString(-42)` là `"ffffffd6"`: nó diễn giải **mẫu 32 bit không dấu** của cùng biến.

```java
System.out.println(Integer.toString(-1,16)); // -1
System.out.println(Integer.toHexString(-1)); // ffffffff
System.out.println(Long.toString(-1L,16));   // -1
```

Không dùng hai kiểu chuỗi này thay nhau khi cần round-trip. Chọn `toString(value, radix)` cho giá trị signed; chọn nhóm `toBinaryString`/`toHexString` và `toUnsignedString` khi thực sự cần nhìn bit pattern. Quy tắc sign extension, hiển thị full-width 32/64 bit, `parseUnsignedInt`/`parseUnsignedLong` và giới hạn `byte`/`short` là trọng tâm bài kế tiếp.

## 6. Biểu diễn unsigned là một hợp đồng riêng

Đối với `int -1`, `Integer.toUnsignedString(-1)` trả `"4294967295"`; với cơ số 16, `Integer.toUnsignedString(-1,16)` trả `"ffffffff"`. Mẫu bit vẫn giữ nguyên, chỉ cách đọc thay đổi. `Integer.parseUnsignedInt("ffffffff",16)` trả lại cùng bit pattern, mà nếu in như signed `int` sẽ thấy `-1`.

```java
String text = Integer.toUnsignedString(-1, 16);
int decodedBits = Integer.parseUnsignedInt(text, 16);
System.out.println(decodedBits == -1); // true
```

Đừng đưa chuỗi unsigned có giá trị lớn hơn `Integer.MAX_VALUE` vào `Integer.parseInt` theo signed. Tại ranh giới API, cần quy định rõ signedness, radix và chiều rộng bit. Bài `toBinaryString, toHexString và toUnsignedString` đi sâu về các quy tắc này.

## 7. Định dạng không tự có `0x`, `0b`, `#` hay hậu tố

`Integer.toString(42,16)` trả `"2a"` chứ không trả `"0x2a"`. `Long.toString(42L,2)` trả `"101010"` chứ không có `"0b"`. Formatter cũng **không tự thêm** underscore phân nhóm, chữ `L`, chữ hoa, ký hiệu `#` hoặc độ rộng cố định.

```java
String digits = Integer.toString(42, 16); // "2a"
String shown = "0x" + digits;             // "0x2a"
```

Nếu chuỗi đầu ra phục vụ **giao thức máy-máy**, tránh thêm tiền tố trang trí khi giao thức chỉ yêu cầu digits. Nếu phục vụ UI hoặc log, tiền tố có thể hữu ích để người đọc biết radix.

### Đặt dấu âm và tiền tố đúng vị trí

Cách ngây thơ:

```java
String wrong = "0x" + Integer.toString(-42,16);
// "0x-2a" — không phải cú pháp chuẩn mà Integer.decode chấp nhận.
```

Nếu bạn thiết kế một định dạng signed hex tương thích với `Integer.decode`, đặt dấu **trước** tiền tố:

```java
static String signedHexWithPrefix(int value) {
    String digits = Integer.toString(value, 16);
    if (digits.startsWith("-")) {
        return "-0x" + digits.substring(1);
    }
    return "0x" + digits;
}
```

```java
System.out.println(signedHexWithPrefix(-42)); // -0x2a
System.out.println(Integer.decode("-0x2a"));  // -42
```

Đây là phương thức minh họa đặt trong class. Cách dùng `substring(1)` trên dấu có sẵn tránh phải tính `-value`, vì `-Integer.MIN_VALUE` có thể tràn. Tuy nhiên nó áp dụng cho **giá trị signed**, không phải quy ước bitmask unsigned. Với bitmask, `"0x" + Integer.toHexString(value)` thường diễn đạt tốt hơn.

## 8. Viết chữ hoa, thêm số 0 đầu và độ rộng cố định

Các kết quả `toString(value,radix)` có dạng tối giản: không thêm số 0 đầu, và dùng chữ thường. Nếu một định dạng yêu cầu **ít nhất 8 chữ số hex**, bạn phải bổ sung quy tắc riêng.

```java
int value = 42;
String padded = String.format(java.util.Locale.ROOT, "%08x", value);
System.out.println(padded); // 0000002a
```

`String.format` dùng các quy tắc của `Formatter`, ở đây:

- `%x`: định dạng hexadecimal chữ thường.
- `%X`: định dạng hexadecimal chữ hoa.
- `8`: độ rộng tối thiểu 8 ký tự.
- `0`: điền chữ số 0 ở bên trái cho đủ độ rộng.
- `Locale.ROOT`: lựa chọn ổn định cho việc định dạng không phụ thuộc ngôn ngữ vùng.

```java
System.out.println(String.format(java.util.Locale.ROOT,"%08X", 42));
// 0000002A
```

**Bẫy:** `%08x` không có nghĩa “cắt xuống 8 ký tự”. Độ rộng là **tối thiểu**, không phải giới hạn tối đa. Nếu giá trị cần nhiều chữ số hơn, formatter không tự bỏ những chữ số quan trọng.

Ngoài ra, với **số nguyên âm** và `%x`, quy ước Formatter có thể dùng biểu diễn unsigned bit pattern. Không nhầm nó với `Integer.toString(-42,16)` dạng `"-2a"`.

### Nếu muốn chính xác 8 chữ số hex cho một `int`?

`Integer.toHexString(value)` sinh tối đa 8 chữ số hex cho mẫu 32 bit, nhưng thường không padding các số nhỏ. Có thể kết hợp `String.format("%08x", value)` khi muốn hiển thị toàn bộ word 32 bit. Với `long`, thường dùng `%016x` để hiển thị 16 chữ số hex của word 64 bit.

### Không làm mất số 0 đầu của dữ liệu định danh

Một chuỗi như `"00042"` có thể là mã hồ sơ, không đơn thuần là biểu diễn số học của 42. Chuyển nó bằng:

```java
int n = Integer.parseInt("00042");
String restored = Integer.toString(n); // "42", mất ba số 0 đầu.
```

Không có cách nào để `Integer.toString` suy ra độ rộng ban đầu của chuỗi vì biến số `int` đã **không lưu** số 0 đầu. Nếu cần giữ đúng định danh, giữ `String` và validate theo schema. Nếu độ rộng cố định là thuộc tính nghiệp vụ, định dạng lại với chiều rộng đã biết.

## 9. Parse và format có thể là hai chiều thuận nghịch, với điều kiện

Với một radix hợp lệ `2..36` và một giá trị `int`, cặp sau khôi phục giá trị gốc:

```java
int original = -42;
int radix = 16;
String text = Integer.toString(original, radix);
int restored = Integer.parseInt(text, radix);

System.out.println(text);                // -2a
System.out.println(restored == original); // true
```

Tương tự cho `long`:

```java
long original = 4_294_967_296L;
String text = Long.toString(original, 16); // "100000000"
long restored = Long.parseLong(text, 16);
```

Các cặp là `toString`/`parseInt` và `Long.toString`/`Long.parseLong`, **không phải** tự động thêm dấu `0x` hay ký tự phân nhóm. Nếu muốn format bit pattern unsigned, phải dùng cặp unsigned thích hợp.

### Những điều phá vỡ round trip

| Nguyên nhân | Ví dụ | Kết quả |
| --- | --- | --- |
| Parse sai radix | Format `42` radix 16 → `"2a"`, parse radix 10 | `NumberFormatException` |
| Tự thêm tiền tố rồi parseInt | `"0x2a"` với radix 16 | Parser không tự bỏ `0x` |
| Dùng bitstring unsigned và parser signed | `"ffffffff"` → `parseInt(...,16)` | Vượt miền `int` signed |
| Sử dụng cast thu hẹp trước format | `(int) 4_294_967_296L` | Mất giá trị trước khi `toString` chạy |
| Quên ký hiệu dấu | Đổi `"-2a"` thành `"2a"` | Giá trị âm bị đổi thành dương |
| Dùng radix sai ngoài phạm vi mà không validate | `toString(255,1)` | Silent fallback sang `"255"` hệ 10 |
| Cho rằng định dạng giữ khoảng trắng/zero đầu | `"00042"` → parse → `toString` | Chỉ còn `"42"` |

Một giao thức số nguyên nên **quy định radix, signedness, tiền tố, chữ hoa/chữ thường, độ rộng, quy tắc số 0 đầu và phạm vi giá trị**. Một phương thức formatter đơn lẻ không thay thế được tài liệu định dạng.

## 10. Lưu ý về Unicode digits và chữ hoa/chữ thường

`Integer.toString(value, radix)` và `Long.toString(value, radix)` dùng bảng chữ số ASCII dạng `0..9a..z`, phù hợp cho giao thức không phụ thuộc locale.

Trong khi đó `Integer.parseInt(text, radix)` và `Long.parseLong(text, radix)` sử dụng quy tắc chữ số dựa trên `Character.digit` nên có thể nhận một số ký tự chữ số Unicode được JDK hỗ trợ, cũng như chữ hoa `A..Z` với radix phù hợp. **Ngôn ngữ formatter chuẩn và tập input parser chấp nhận không nhất thiết giống hệt nhau.**

```java
System.out.println(Integer.toString(255,16)); // ff
System.out.println(Integer.parseInt("FF",16)); // 255
System.out.println(Integer.parseInt("ff",16)); // 255
```

Nếu giao thức bắt buộc **chỉ ASCII, chỉ lowercase**, đừng nghĩ `parseInt` tự làm validator cho cả schema. Hãy kiểm tra tập ký tự và chuẩn hóa theo chính sách của giao thức.

Việc dùng `toString` thông thường **không phụ thuộc `Locale`** để chọn ký tự chữ số. Nếu bạn cần hiển thị số theo quy tắc văn hóa như phân nhóm hàng nghìn hoặc dấu số thập phân địa phương, hãy dùng `NumberFormat`/`Formatter` đúng mục đích. `Integer.toString` có lợi khi cần chuỗi máy-máy đơn giản, nhất quán.

## 11. Trường hợp `Long.MIN_VALUE` và miền giá trị dài hơn `int`

`long` là số nguyên signed 64 bit, có miền `-9_223_372_036_854_775_808` tới `9_223_372_036_854_775_807`. Khi giá trị vượt `int`, chọn `Long.toString` để giữ nguyên giá trị.

```java
long large = 4_294_967_296L; // 2^32
System.out.println(Long.toString(large));    // 4294967296
System.out.println(Long.toString(large,16)); // 100000000
```

`Integer.toString` không chấp nhận `long` trực tiếp nếu chưa chuyển kiểu:

```java
// Integer.toString(large); // Lỗi biên dịch: long không tự thu hẹp thành int.
String wrong = Integer.toString((int) large); // "0": đã mất bit cao.
```

Với `Long.MIN_VALUE`, `Long.toString` bảo đảm trả chuỗi signed đúng `"-9223372036854775808"`. Cũng như `int`, không tự áp dụng `Math.abs(Long.MIN_VALUE)` rồi mong ra số dương cùng kiểu, vì `Math.abs(Long.MIN_VALUE)` vẫn âm do overflow.

Với số nguyên còn lớn hơn `long`, chọn `java.math.BigInteger`. `BigInteger.toString(int radix)` cũng hỗ trợ biểu diễn theo cơ số 2..36, nhưng có hợp đồng riêng; đừng ép về `longValue()` chỉ để dùng `Long.toString` vì khi đó có thể mất thông tin.

## 12. Chương trình độc lập Java 17+

Tạo tệp `RadixFormatDemo.java`:

```java
import java.util.Locale;

public class RadixFormatDemo {
    public static void main(String[] args) {
        int n = -42;
        long large = 4_294_967_296L;

        System.out.println("Integer default: " + Integer.toString(n));
        System.out.println("Long default: " + Long.toString(large));
        System.out.println("42 base 2: " + Integer.toString(42, 2));
        System.out.println("42 base 8: " + Integer.toString(42, 8));
        System.out.println("42 base 16: " + Integer.toString(42, 16));
        System.out.println("-42 base 16: " + Integer.toString(-42, 16));
        System.out.println("35 base 36: " + Integer.toString(35, 36));
        System.out.println("255 base 16: " + Integer.toString(255, 16));
        System.out.println("255 invalid radix 1: "
                + Integer.toString(255, 1));
        System.out.println("long 4294967296 base 16: "
                + Long.toString(large, 16));

        System.out.println("int -1 toHexString: "
                + Integer.toHexString(-1));
        System.out.println("int -1 toString base 16: "
                + Integer.toString(-1, 16));
        System.out.println("unsigned -1 base 10: "
                + Integer.toUnsignedString(-1));
        System.out.println("unsigned -1 base 16: "
                + Integer.toUnsignedString(-1, 16));
        System.out.println("unsigned long -1 base 16: "
                + Long.toUnsignedString(-1L, 16));

        System.out.println("zero base 36: " + Long.toString(0L, 36));
        System.out.println("Integer.MIN_VALUE base 10: "
                + Integer.toString(Integer.MIN_VALUE));
        System.out.println("Long.MIN_VALUE base 10: "
                + Long.toString(Long.MIN_VALUE));

        System.out.println("Signed parse radix 16: "
                + Integer.parseInt(Integer.toString(-42, 16), 16));
        System.out.println("Unsigned parse hex: "
                + Integer.parseUnsignedInt(
                        Integer.toUnsignedString(-1, 16), 16));
        System.out.println("Padded hex: "
                + String.format(Locale.ROOT, "%08x", 42));
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac --release 17 RadixFormatDemo.java
java RadixFormatDemo
```

Stdout đã được kiểm chứng:

```text
Integer default: -42
Long default: 4294967296
42 base 2: 101010
42 base 8: 52
42 base 16: 2a
-42 base 16: -2a
35 base 36: z
255 base 16: ff
255 invalid radix 1: 255
long 4294967296 base 16: 100000000
int -1 toHexString: ffffffff
int -1 toString base 16: -1
unsigned -1 base 10: 4294967295
unsigned -1 base 16: ffffffff
unsigned long -1 base 16: ffffffffffffffff
zero base 36: 0
Integer.MIN_VALUE base 10: -2147483648
Long.MIN_VALUE base 10: -9223372036854775808
Signed parse radix 16: -42
Unsigned parse hex: -1
Padded hex: 0000002a
```

Ví dụ thể hiện định dạng `int`/`long` ở các radix thông dụng, sự chuyển về radix 10 khi tham số sai, số âm signed so với mẫu bit hex không dấu, giá trị biên, round trip signed/unsigned và độ rộng hex cố định. Các chuỗi stdout phản ánh giá trị thực tế của JDK chứ không suy đoán từ ký hiệu `0x` trong source.

## 13. Một số tình huống áp dụng

### A. Hiển thị flags và bitmask

Khi debug trạng thái cờ, bạn thường quan tâm **bit pattern** hơn giá trị signed. Ví dụ:

```java
int flags = 0xA5000001;
String bitsHex = Integer.toHexString(flags); // a5000001
String allBitsHex = String.format(Locale.ROOT, "%08x", flags);
```

Nếu dùng `Integer.toString(flags,16)`, vì bit dấu bằng 1, chuỗi sẽ bắt đầu bằng dấu `-` cùng chữ số thể hiện độ lớn signed, không phải word hex 8 chữ số của mẫu bit. Hãy xác định rõ log cần **signed value** hay **raw bit pattern**.

### B. Xây mã định danh ngắn theo radix 36

Đối với **số nguyên không âm** đã có sẵn:

```java
int id = 35;
String compact = Integer.toString(id, 36); // "z"
```

Cơ số 36 có thể tạo chữ ngắn hơn thập phân với số lớn, nhưng đó chỉ là **cách biểu diễn**. Nó không tạo tính bảo mật, không tăng entropy và không bảo đảm mã duy nhất ngoài giá trị nguồn. Nếu phải giữ chữ hoa, độ rộng hoặc check digit, đó là quy tắc bổ sung của giao thức.

### C. Lưu giá trị số vào file hoặc JSON

Khi giao thức quy định số nguyên thập phân, `Integer.toString(value)` và `Long.toString(value)` sinh chuỗi ổn định không có dấu phân nhóm hàng nghìn. Nhưng trong JSON, kiểu **number** và **string** khác nhau: ghi `"42"` là dữ liệu chuỗi, còn ghi `42` là token số. Đừng quyết định sơ đồ dữ liệu chỉ dựa trên kết quả formatter.

Với số `long` vượt độ chính xác nguyên liên tiếp của JavaScript `Number` (2^53), một số hệ thống lựa chọn **truyền string decimal** để tránh mất chính xác ở phía nhận; đây là quyết định hợp đồng giữa các hệ, không phải thiếu sót của `Long.toString`.

### D. Đọc/ghi hex trên hai hệ thống

Nếu bên gửi dùng `Integer.toHexString` cho bitmask và bên nhận dùng `Integer.parseInt`, lỗi có thể xuất hiện khi bit cao nhất bằng 1. Với một mẫu 32-bit không dấu, dùng `Integer.parseUnsignedInt(text,16)` phía nhận; với long 64-bit, dùng `Long.parseUnsignedLong` khi đúng schema. Thêm tiền tố hoặc padding nếu giao thức yêu cầu, nhưng bên nhận phải hiểu đúng tiền tố và độ rộng.

## 14. Lỗi thường gặp

| Sai lầm | Vì sao sai | Hướng khắc phục |
| --- | --- | --- |
| Cho rằng `radix` thay đổi giá trị `int` | Nó chỉ thay cách biểu diễn thành chuỗi | Phân biệt numeric value và formatting |
| Tưởng `toString(value,16)` tự trả `"0x..."` | Nó chỉ sinh chữ số cùng dấu nếu âm | Thêm prefix có chủ đích hoặc dùng formatter |
| Mong kết quả hexadecimal viết hoa | `toString` dùng `a..z` | `toUpperCase(Locale.ROOT)` hoặc `%X` khi phù hợp |
| Dùng radix 1/37 và chờ exception | `toString` fallback radix 10 | Validate radix nếu input có thể sai |
| Nhầm `Integer.toString(-1,16)` và `Integer.toHexString(-1)` | `"-1"` khác `"ffffffff"` | Chọn signed vs bit-pattern format |
| Parse `"ffffffff"` bằng `parseInt(...,16)` | Vượt `Integer.MAX_VALUE` signed | `parseUnsignedInt(...,16)` |
| Parse `"2a"` bằng radix 10 | Chữ `a` không hợp lệ | Chuyển đúng radix 16 |
| Đọc `"0x2a"` bằng `parseInt(...,16)` | Parser không tự hiểu prefix | `decode` hoặc strip prefix sau validation |
| Dùng `Integer.toString((int) longValue)` | Cast có thể mất dữ liệu trước format | `Long.toString(longValue,radix)` |
| Tự dùng `Math.abs(MIN_VALUE)` để format âm | `abs` có thể overflow | Dùng API JDK đúng |
| Cho rằng `toString` tự padding để đủ độ rộng | Kết quả tối giản, không padding | `Formatter`/`String.format` |
| Dùng `toString` cho mã giữ số 0 đầu | Không thể phục hồi độ rộng gốc sau parse | Giữ `String` hoặc schema độ rộng |
| Cho rằng toString tạo chuỗi “bảo mật” | Radix 36 chỉ là biểu diễn | Thiết kế mã có bảo mật riêng |
| Tưởng mọi `String` do `toString` tạo ra đều có thể parse radix bất kỳ | Cần đúng radix và signedness | Lưu metadata cơ số trong giao thức |
| Nhầm `Integer.valueOf` với `Integer.toString` | Một hàm trả wrapper, một hàm trả `String` | Xem chữ ký và kiểu trả về |
| Dùng `Integer.parseInt` khi giá trị vượt `int` | Out of range sẽ báo lỗi | Chọn `Long.parseLong` hoặc `BigInteger` |

## 15. Tự kiểm tra

1. `Integer.toString(42,16)` trả chuỗi gì? Có tự thêm `0x` không?
2. `Integer.toString(-42,16)` và `Integer.toHexString(-42)` giống hay khác nhau? Giải thích theo signed value và bit pattern.
3. Vì sao `Integer.toString(255,1)` không ném exception mà trả `"255"`? Nếu một API cần báo lỗi radix sai, bạn nên làm gì?
4. `Long.toString(4_294_967_296L,16)` trả gì? Vì sao không thể dùng `Integer.toString((int) 4_294_967_296L,16)` để giữ cùng giá trị?
5. Hai lời gọi `Integer.toUnsignedString(-1)` và `Integer.toString(-1)` tạo kết quả nào? Mẫu bit ban đầu có thay đổi không?
6. Nếu nhận được `"ffffffff"` như word hex 32-bit, bạn nên dùng `parseInt` hay `parseUnsignedInt`? Vì sao?
7. Định dạng radix 36 của 35 là gì? Nó có tạo mã duy nhất, bí mật hoặc có độ dài cố định không?
8. Khi nào nên dùng `String.format(Locale.ROOT,"%08x", value)` thay vì `Integer.toString(value,16)`?
9. Vì sao `"00042"` không thể được khôi phục nguyên dạng bằng `Integer.parseInt` rồi `Integer.toString`? Tình huống nào cần giữ `String`?
10. Viết phép kiểm tra round trip signed từ `int` sang cơ số 2, 8, 10, 16 và trở lại `int`.
11. Nếu formatter dùng radix 16 nhưng parser mặc định radix 10, chuyện gì xảy ra với `"2a"`?
12. Nếu ứng dụng nhận `radix` từ input người dùng, vì sao nên validate trong `2..36` thay vì dựa vào fallback của `toString`?
13. Vì sao `Integer.toString(value,16)` trả chữ thường? Làm thế nào tạo uppercase mà không phụ thuộc locale?
14. Khi lưu một bitmask có thể âm trong signed `int`, format và parse thế nào để giữ nguyên 32 bit?

## 16. Tài liệu chính thức

- [Java SE 17 — `Integer.toString(int, int)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#toString(int,int)): radix 2..36, chữ số `0..9a..z`, dấu âm và fallback radix 10.
- [Java SE 17 — `Long.toString(long, int)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html#toString(long,int)): cùng nguyên tắc định dạng signed trên 64 bit.
- [Java SE 17 — `Integer.toUnsignedString`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#toUnsignedString(int,int)): diễn giải unsigned của 32 bit.
- [Java SE 17 — `Integer.toHexString`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#toHexString(int)): chuỗi hex không dấu của mẫu `int`.
- [Java SE 17 — `Integer.parseInt`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#parseInt(java.lang.String,int)): phân tích chuỗi có dấu theo radix.
- [Java SE 17 — `Formatter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Formatter.html): các quy tắc `%x`/`%X`, padding, width và định dạng văn bản.

**Kết luận:** `Integer.toString` và `Long.toString` là API chuyển **giá trị số nguyên** thành **chuỗi chữ số theo radix**; chúng không làm đổi giá trị, không tự thêm prefix, không padding và trả chữ thường ở cơ số trên 10. Với số âm, `toString(value,radix)` dùng **dấu trừ**, còn `toHexString`/`toBinaryString`/`toUnsignedString` phù hợp khi cần **biểu diễn mẫu bit theo nghĩa không dấu**. Hãy quy định rõ signedness, radix và quy tắc giao thức khi định dạng hoặc parse ngược, đồng thời validate radix để tránh fallback thập phân không mong muốn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-002">toBinaryString, toHexString và toUnsignedString</a>

<details>
<summary>Xem chi tiết</summary>

# `toBinaryString`, `toHexString` và `toUnsignedString` trong Java

Khi cần hiển thị hoặc kiểm tra **biểu diễn của số nguyên**, Java cung cấp các phương thức tĩnh trong `Integer` và `Long`. Ba tên quan trọng nhất là **`toBinaryString`**, **`toHexString`** và **`toUnsignedString`**:

- `Integer.toBinaryString(int)`: tạo chuỗi **nhị phân** (cơ số 2) từ **mẫu bit 32 bit** của một `int`.
- `Integer.toHexString(int)`: tạo chuỗi **thập lục phân** (cơ số 16), chữ `a`–`f` viết thường, từ cùng mẫu bit.
- `Integer.toUnsignedString(int)`: tạo chuỗi **thập phân không dấu** (cơ số 10 mặc định), coi cùng 32 bit là số trong khoảng `0` đến `4_294_967_295`.

Ví dụ cùng một giá trị `int`:

```java
int value = -1;

System.out.println(Integer.toBinaryString(value));
// 11111111111111111111111111111111

System.out.println(Integer.toHexString(value));
// ffffffff

System.out.println(Integer.toUnsignedString(value));
// 4294967295
```

`value` vẫn là **`int -1`**, không tự biến thành kiểu số nguyên không dấu. Những phương thức này **đổi cách trình bày bit thành chuỗi**; chúng không sửa giá trị biến, không thay đổi bit trong bộ nhớ và không tạo kiểu primitive `uint`.

## 1. Hiểu đúng signed và unsigned trước khi định dạng

Java có các kiểu số nguyên primitive `byte` (8 bit), `short` (16 bit), `int` (32 bit) và `long` (64 bit), đều có dấu theo cách hiểu số nguyên thông thường. Riêng `char` là giá trị nguyên không dấu 16 bit phục vụ đơn vị mã UTF-16.

Với `int`, cùng **32 bit** có thể được diễn giải:

- **Có dấu (signed)** theo bù hai (*two's complement*): `-2_147_483_648` tới `2_147_483_647`.
- **Không dấu (unsigned)**: `0` tới `4_294_967_295`.

Ví dụ mẫu bit toàn `1`:

```text
Bit 32 bit:       11111111111111111111111111111111
Hex:              ffffffff
Signed int:       -1
Unsigned 32-bit:  4294967295
```

Đây **không phải ba giá trị được lưu riêng**. Chúng là **ba cách trình bày cùng mẫu bit**. Biểu thức `int x = -1;` tiếp tục hoạt động theo số học có dấu nếu viết `x + 1`, `x < 0` hoặc `x / 2`. Nếu nghiệp vụ muốn **so sánh/chia theo unsigned**, Java có các API như `Integer.compareUnsigned`, `divideUnsigned` và `remainderUnsigned`; chỉ việc gọi `toUnsignedString` không đổi nghĩa các phép toán về sau.

### Bảng nhanh với `Integer`

| Giá trị `int` | `toBinaryString` | `toHexString` | `toUnsignedString` |
| --- | --- | --- | --- |
| `0` | `0` | `0` | `0` |
| `10` | `1010` | `a` | `10` |
| `42` | `101010` | `2a` | `42` |
| `255` | `11111111` | `ff` | `255` |
| `-1` | 32 ký tự `1` | `ffffffff` | `4294967295` |
| `Integer.MIN_VALUE` | `1` và 31 ký tự `0` | `80000000` | `2147483648` |

Đối với số `int` không âm, chuỗi nhị phân và hex thường **bỏ các số 0 dẫn đầu**. Với số âm, bit dấu là 1 và biểu diễn bù hai 32 bit được thể hiện đầy đủ theo chiều rộng kiểu `int`. Đây là lý do `Integer.toBinaryString(-1)` dài 32 ký tự, còn `Integer.toBinaryString(1)` chỉ là `"1"`.

## 2. `Integer.toBinaryString(int)`: đọc mẫu bit ở cơ số 2

Cú pháp:

```java
String bits = Integer.toBinaryString(42); // "101010"
```

Các chữ số trong chuỗi chỉ gồm `0` và `1`; **không có tiền tố `0b`** và không có dấu âm tách rời. Phương thức mô tả mẫu bit của `int` như giá trị 32 bit không dấu rồi ghi bằng cơ số 2.

```java
System.out.println(Integer.toBinaryString(10)); // 1010
System.out.println(Integer.toBinaryString(-1));
// 11111111111111111111111111111111
```

Vì `int` có 32 bit, một giá trị âm như `-1` tương đương mẫu bù hai toàn bit 1. **Không đọc** chuỗi `111...111` thành số có dấu âm bằng cách nhìn chữ số đầu tiên rồi thêm dấu `-`; đây là chuỗi số nhị phân không dấu đại diện cho 32 bit gốc.

Đặc biệt:

```java
String minBits = Integer.toBinaryString(Integer.MIN_VALUE);
// "10000000000000000000000000000000"
```

Bit cao nhất bằng 1 còn các bit khác bằng 0. Với ngữ nghĩa signed, mẫu này là `-2^31`; với ngữ nghĩa unsigned, là `2^31`.

### Muốn chuỗi luôn đủ 32 bit?

`toBinaryString` không thêm zero dẫn đầu cho số dương. Nếu cần bản 32 bit cố định:

```java
int x = 42;
String bits = Integer.toBinaryString(x);
String padded = "0".repeat(32 - bits.length()) + bits;
// "00000000000000000000000000101010"
```

Cách viết này phù hợp Java 17+ và với một `int` bất kỳ có chuỗi nhị phân tối đa 32 ký tự. Khi giao thức nhị phân cần **số byte** chứ không phải ký tự `0`/`1`, hãy ghi byte thực qua `ByteBuffer` hoặc API I/O tương ứng; chuỗi nhị phân là cách **hiển thị phục vụ đọc/debug**, không phải định dạng dữ liệu nhị phân có hiệu suất tốt.

## 3. `Integer.toHexString(int)`: hiển thị hex dễ đọc

Hex (cơ số 16) nhóm **mỗi 4 bit thành một chữ số** từ `0` tới `9` và `a` tới `f`:

```java
int x = 42;

System.out.println(Integer.toHexString(x));   // 2a
System.out.println(Integer.toHexString(255)); // ff
System.out.println(Integer.toHexString(-1));  // ffffffff
```

Một `int` có tối đa 8 ký tự hex, vì `32 / 4 = 8`. Hex gọn hơn binary nên thường phù hợp khi quan sát bit masks, mã màu và địa chỉ/word dữ liệu.

**Lưu ý cú pháp:** Chuỗi trả về **không chứa `0x`**, không chứa dấu phân cách và dùng `a`–`f` viết thường. `"2a"` là chuỗi được hàm trả về; nếu muốn nhãn `0x` trong giao diện, ghép rõ ràng:

```java
String shown = "0x" + Integer.toHexString(42); // "0x2a"
```

Không nhầm **chuỗi trả về từ API** với literal Java:

```java
int sourceLiteral = 0x2A;       // literal int 42 trong mã Java
String displayed = Integer.toHexString(sourceLiteral); // "2a"
```

Với bit pattern 32 bit âm, ví dụ `-1`, `Integer.toHexString(-1)` cho `"ffffffff"` vì nó biểu diễn bit dưới dạng không dấu, **không cho `"-1"`**. Nếu cần viết số có dấu trong cơ số 16, xem `Integer.toString(value, 16)` ở mục 5.

### Hiển thị đủ 8 chữ số hex

`Integer.toHexString(42)` trả `"2a"`, không tự pad thành `"0000002a"`. Nếu định dạng báo cáo yêu cầu đúng 8 chữ số, có thể viết:

```java
int x = 42;
String padded = String.format("%08x", x); // "0000002a"
```

`String.format` hỗ trợ độ rộng và zero-padding. Với dữ liệu dùng trong giao thức hoặc hash, **độ rộng và dạng chữ hoa/chữ thường phải được quy định nhất quán**. Trong UI, có thể dùng `%08X` để in hex chữ hoa nếu đúng yêu cầu.

## 4. `Integer.toUnsignedString(int)`: đọc 32 bit như số không dấu

`Integer.toUnsignedString(value)` mặc định biểu diễn `int` như giá trị **unsigned 32 bit trong cơ số 10**:

```java
int x = -1;
String signed = Integer.toString(x);         // "-1"
String unsigned = Integer.toUnsignedString(x); // "4294967295"
```

Cùng 32 bit có cách đọc khác nhau theo mục tiêu. Quy tắc toán học cho `int x`:

- Nếu `x >= 0`, giá trị unsigned bằng `x`.
- Nếu `x < 0`, giá trị unsigned tương ứng là `x + 2^32` về mặt toán học (phải tính bằng kiểu đủ rộng).

Có thể quan sát tương đương qua widening rồi mask:

```java
int x = -1;
long u = Integer.toUnsignedLong(x); // 4294967295L
long alsoU = ((long) x) & 0xFFFF_FFFFL; // 4294967295L
```

`Integer.toUnsignedLong` mở rộng theo cách hiểu không dấu. **Không dùng `(long) x` đơn thuần** nếu muốn giải mã unsigned vì cast `int → long` có dấu sẽ giữ `-1L` cho `x = -1`.

Không có primitive `uint` trong Java. Do đó `toUnsignedString` chỉ trả `String` để hiển thị/trao đổi, còn dữ liệu gốc vẫn là `int`. Khi cần làm phép toán số học unsigned, phải dùng đúng API, không chỉ thay cách in.

## 5. Khác biệt với `toString(value, radix)` và overload radix

Đây là chỗ rất dễ nhầm. So sánh:

```java
int x = -1;

String signedHex = Integer.toString(x, 16);           // "-1"
String unsignedHex = Integer.toUnsignedString(x, 16); // "ffffffff"
String hexBits = Integer.toHexString(x);              // "ffffffff"
```

- `Integer.toString(int, radix)` xem giá trị như **số có dấu**, nên khi âm thêm dấu `-` và định dạng độ lớn.
- `Integer.toUnsignedString(int, radix)` xem **cùng 32 bit là unsigned**, rồi chuyển sang cơ số được chọn.
- `Integer.toBinaryString(int)` tương đương trình bày unsigned cơ số 2 của mẫu `int`.
- `Integer.toHexString(int)` tương đương trình bày unsigned cơ số 16 của mẫu `int`.
- `Integer.toOctalString(int)` cũng có hành vi tương tự cho cơ số 8, dù không phải API trọng tâm của bài.

```java
System.out.println(Integer.toUnsignedString(10, 8));  // "12"
System.out.println(Integer.toUnsignedString(31, 36)); // "v"
```

Overload `Integer.toUnsignedString(int, int)` nhận radix. Nên chủ động dùng radix hợp lệ từ `Character.MIN_RADIX` (**2**) đến `Character.MAX_RADIX` (**36**). Java dùng chữ cái Latin thường cho các chữ số từ 10 đến 35. Nếu muốn hex, dùng radix 16; nếu muốn binary, dùng radix 2. Trong mã tự xử lý chuỗi nguồn, kiểm tra radix thay vì mặc nhiên mong API sẽ cảnh báo mọi radix không hợp lệ theo cách bạn nghĩ.

Bảng đối chiếu các API:

| Lời gọi với `x = -1` | Kết quả | Cách hiểu |
| --- | --- | --- |
| `Integer.toString(x)` | `"-1"` | Signed, thập phân |
| `Integer.toString(x, 16)` | `"-1"` | Signed, hex có dấu trừ |
| `Integer.toUnsignedString(x)` | `"4294967295"` | Unsigned, thập phân |
| `Integer.toUnsignedString(x, 2)` | 32 chữ `1` | Unsigned, binary |
| `Integer.toBinaryString(x)` | 32 chữ `1` | Bit pattern, binary |
| `Integer.toUnsignedString(x, 16)` | `"ffffffff"` | Unsigned, hex |
| `Integer.toHexString(x)` | `"ffffffff"` | Bit pattern, hex |

**Ý nghĩa chính:** Khi giá trị nằm trong phần âm của `int`, chữ số hiển thị phụ thuộc việc chọn ngữ nghĩa **signed** hay **unsigned**.

## 6. Phiên bản `Long` và độ rộng 64 bit

`Long` cũng cung cấp:

- `Long.toBinaryString(long)`;
- `Long.toHexString(long)`;
- `Long.toUnsignedString(long)`;
- `Long.toUnsignedString(long, int radix)`.

Cùng nguyên tắc như `Integer`, nhưng làm việc với **64 bit**. Số không dấu lớn nhất theo 64 bit là **`2^64 - 1 = 18_446_744_073_709_551_615`**, vượt `Long.MAX_VALUE`.

```java
long value = -1L;

System.out.println(Long.toHexString(value));
// ffffffffffffffff (16 ký tự f)

System.out.println(Long.toUnsignedString(value));
// 18446744073709551615
```

`Long.toBinaryString(-1L)` trả 64 chữ `1`. Với `Long.MIN_VALUE`, mẫu hex là `8000000000000000`; diễn giải signed là `-2^63`, còn unsigned là `2^63`.

**Không ép số nguyên không dấu 64 bit lớn vào một kiểu `long` khác để lấy giá trị dương**, vì primitive `long` không có miền dương đủ lớn. Nếu cần làm việc với giá trị toán học trong miền unsigned 64 bit dưới dạng số không âm, có thể dùng `BigInteger` hoặc các phép unsigned chuyên dụng `Long.compareUnsigned`, `divideUnsigned`, `remainderUnsigned`; chọn cách phù hợp bài toán.

## 7. Bẫy `byte`/`short`: sign extension trước khi format

Ba phương thức `Integer.toBinaryString`, `Integer.toHexString` và `Integer.toUnsignedString` nhận `int`, **không có overload trực tiếp cho `byte` hay `short`**. Khi truyền `byte`/`short`, Java mở rộng chúng lên `int` trước khi gọi phương thức.

Với giá trị âm, widening primitive bảo toàn **giá trị có dấu**, có thể được nhìn thấy như **sign extension** (nhân bit dấu lên phần cao):

```java
byte b = -1;

String unexpected = Integer.toBinaryString(b);
// 11111111111111111111111111111111 (32 bit, không phải 8)

String eightBits = Integer.toBinaryString(b & 0xFF);
// 11111111

int unsignedByte = Byte.toUnsignedInt(b); // 255
```

Nếu muốn biểu diễn đúng **8 bit** của `byte`, mask bằng `0xFF` trước khi format. Với `short`, dùng `0xFFFF` để giữ 16 bit:

```java
short s = -1;

String sHex = Integer.toHexString(s & 0xFFFF); // "ffff"
String sBits = Integer.toBinaryString(s & 0xFFFF);
// "1111111111111111"
```

Nếu muốn một giá trị unsigned dễ tính toán, `Byte.toUnsignedInt` và `Short.toUnsignedInt` diễn đạt rõ ý định. Cũng có thể dùng `Byte.toUnsignedInt(b)` rồi `Integer.toUnsignedString(...)`. Mask là cách phổ biến khi làm bit field, network protocol hoặc giải mã byte có dấu thành giá trị trong `0..255`.

### Chú ý chiều rộng đầu ra

`Integer.toBinaryString(b & 0xFF)` vẫn loại **zero dẫn đầu** cho giá trị như `5` (sẽ thành `"101"`). Nếu bắt buộc đúng 8 bit, kết hợp mask với padding:

```java
byte b = 5;
String raw = Integer.toBinaryString(b & 0xFF); // "101"
String padded = "0".repeat(8 - raw.length()) + raw; // "00000101"
```

Tương tự, muốn đúng 4 ký tự hex cho `short`, có thể dùng `String.format("%04x", s & 0xFFFF)`. Việc **mask** xử lý chiều rộng bit, còn việc **padding** xử lý độ dài chuỗi hiển thị; hai bước này có nhiệm vụ khác nhau.

## 8. Chuyển chuỗi unsigned trở lại mẫu bit: `parseUnsignedInt`, `parseUnsignedLong`

Nếu một chuỗi unsigned vượt `Integer.MAX_VALUE` nhưng vẫn thuộc miền unsigned 32 bit, `Integer.parseInt` sẽ từ chối nó. Dùng `Integer.parseUnsignedInt` để đọc **mẫu bit 32 bit**:

```java
int bits = Integer.parseUnsignedInt("4294967295");

System.out.println(bits);                      // -1, signed interpretation
System.out.println(Integer.toUnsignedString(bits)); // 4294967295
System.out.println(Integer.toHexString(bits)); // ffffffff
```

Hàm parser trả về **`int`**, không phải một loại `uint`. Điều quan trọng khi round-trip là giữ **mẫu bit**, dù biến `int` tạm thời có thể in signed là số âm.

Với cơ số 16:

```java
int b = Integer.parseUnsignedInt("ffffffff", 16); // bit pattern -1
String output = Integer.toHexString(b); // "ffffffff"
```

Các API `parseUnsignedInt` vẫn kiểm tra **chữ số hợp lệ và miền unsigned 32 bit**. Ví dụ chuỗi thập phân `"4294967296"` vượt miền không dấu sẽ gây `NumberFormatException`. Chúng không tự chấp nhận các prefix như `0x` trong overload cơ số 16, nên cần quy định/loại bỏ prefix theo hợp đồng đầu vào.

Với `long`, `Long.parseUnsignedLong("18446744073709551615")` trả mẫu bit tương ứng `-1L`, và `Long.toUnsignedString` hiển thị trở lại chuỗi số dương cực đại. Nếu cần lưu giá trị unsigned 64 bit dưới dạng số không âm cho phép toán tổng quát, cân nhắc `BigInteger`.

### Không nhầm parse số có dấu với parse mẫu bit

`Integer.parseInt("ffffffff", 16)` gây `NumberFormatException` do giá trị tích cực lớn hơn signed `int` (với chuỗi không có dấu trừ). `Integer.parseUnsignedInt("ffffffff", 16)` nhận vì nằm trong miền unsigned 32 bit, rồi cho mẫu `int` có dấu là `-1`. Đây là sự khác nhau về **miền phân tích**, không phải việc Java tự làm tràn mà không báo lỗi.

## 9. Ví dụ Java 17+ hoàn chỉnh

Tạo tệp `IntegerFormatsDemo.java`:

```java
public class IntegerFormatsDemo {
    public static void main(String[] args) {
        int number = 42;
        System.out.println("42 binary: " + Integer.toBinaryString(number));
        System.out.println("42 hex: " + Integer.toHexString(number));
        System.out.println("42 unsigned: " + Integer.toUnsignedString(number));

        System.out.println("-1 signed decimal: " + Integer.toString(-1));
        System.out.println("-1 unsigned decimal: " + Integer.toUnsignedString(-1));
        System.out.println("-1 binary: " + Integer.toBinaryString(-1));
        System.out.println("-1 hex: " + Integer.toHexString(-1));
        System.out.println("-1 signed hex: " + Integer.toString(-1, 16));
        System.out.println("-1 unsigned hex: " + Integer.toUnsignedString(-1, 16));

        int min = Integer.MIN_VALUE;
        System.out.println("MIN_VALUE unsigned: " + Integer.toUnsignedString(min));
        System.out.println("MIN_VALUE hex: " + Integer.toHexString(min));
        System.out.println("10 base 8: " + Integer.toUnsignedString(10, 8));
        System.out.println("31 base 36: " + Integer.toUnsignedString(31, 36));

        byte b = -1;
        System.out.println("byte -1 sign extended: " + Integer.toBinaryString(b));
        System.out.println("byte -1 masked: " + Integer.toBinaryString(b & 0xFF));
        System.out.println("byte -1 unsigned: " + Byte.toUnsignedInt(b));

        long big = -1L;
        System.out.println("long -1 unsigned: " + Long.toUnsignedString(big));
        System.out.println("long -1 hex: " + Long.toHexString(big));

        int parsed = Integer.parseUnsignedInt("4294967295");
        System.out.println("unsigned parsed bits: " + parsed);
        System.out.println("unsigned round trip: " + Integer.toUnsignedString(parsed));
        System.out.println("binary parse: " + Integer.parseUnsignedInt("11111111", 2));

        System.out.println("padded hex: " + String.format("%08x", number));
        System.out.println("padded binary: "
            + String.format("%8s", Integer.toBinaryString(number))
                .replace(' ', '0'));
    }
}
```

Biên dịch và chạy:

```bash
javac IntegerFormatsDemo.java
java IntegerFormatsDemo
```

Stdout đã kiểm chứng:

```text
42 binary: 101010
42 hex: 2a
42 unsigned: 42
-1 signed decimal: -1
-1 unsigned decimal: 4294967295
-1 binary: 11111111111111111111111111111111
-1 hex: ffffffff
-1 signed hex: -1
-1 unsigned hex: ffffffff
MIN_VALUE unsigned: 2147483648
MIN_VALUE hex: 80000000
10 base 8: 12
31 base 36: v
byte -1 sign extended: 11111111111111111111111111111111
byte -1 masked: 11111111
byte -1 unsigned: 255
long -1 unsigned: 18446744073709551615
long -1 hex: ffffffffffffffff
unsigned parsed bits: -1
unsigned round trip: 4294967295
binary parse: 255
padded hex: 0000002a
padded binary: 00101010
```

**Cách đọc chương trình:** Các dòng `42` minh họa cơ số; nhóm `-1` cho thấy cùng mẫu 32 bit có diễn giải signed/unsigned khác nhau; `MIN_VALUE` có bit cao nhất 1; nhóm `byte` giải thích sign extension và mặt nạ 8 bit; nhóm `long` hiển thị 64 bit; `parseUnsignedInt` round-trip về chuỗi unsigned; hai dòng cuối cho thấy định dạng **độ rộng cố định** không phải hành vi mặc định của `toBinaryString`/`toHexString`.

## 10. Những ứng dụng thực tế và cách chọn API

### Quan sát bit flags và masks

Khi kiểm tra một `int` gồm nhiều cờ bit, binary/hex giúp nhìn các vị trí bit đang bật:

```java
int flags = 0b1010_0101;
String hex = Integer.toHexString(flags);    // "a5"
String bits = Integer.toBinaryString(flags); // "10100101"
```

Để phân tích bit cụ thể, kết hợp toán tử `&`, `|`, `^`, `~`, `<<` và `>>>` trên giá trị nguyên. `toBinaryString` chỉ tạo **chuỗi hiển thị**, không thay thế phép toán bit.

### Giao diện hoặc log cần hiểu unsigned

Có hệ thống lưu số 32 bit trong `int` nhưng thực tế xem đó là **unsigned ID/counter**. Dùng `Integer.toUnsignedString` khi hiển thị để người dùng không nhìn thấy số âm không mong muốn. Tuy nhiên, nếu phép so sánh thứ tự của ID/counter cũng theo unsigned, dùng `Integer.compareUnsigned` thay vì `<` giữa hai `int`.

### Lưu trữ và giao thức nhị phân

`"ffffffff"` là **8 ký tự hex**, không phải bốn byte `0xFF`. Nếu giao thức cần thực sự gửi byte, phải chuyển bit pattern thành mảng byte (ví dụ `ByteBuffer.putInt` với byte order được chỉ định). `toHexString` hữu ích để debug nhưng tự nó **không quyết định endianness** (big-endian hay little-endian).

Ví dụ `0x12345678` có cùng chuỗi hex `"12345678"` dù khi serialize big-endian sẽ là các byte `12 34 56 78` và little-endian là `78 56 34 12`. Byte order được quyết định ở bước đọc/ghi byte, không bởi phương thức format số thành chuỗi.

### Chọn theo yêu cầu

| Cần gì? | API nên xem xét |
| --- | --- |
| Xem bit `int` theo nhị phân | `Integer.toBinaryString(value)` |
| Xem bit `int` bằng hex | `Integer.toHexString(value)` |
| Hiển thị `int` có dấu thập phân | `Integer.toString(value)` |
| Hiển thị `int` không dấu thập phân | `Integer.toUnsignedString(value)` |
| Hiển thị `int` không dấu với radix bất kỳ 2–36 | `Integer.toUnsignedString(value, radix)` |
| Làm việc với giá trị `long` | Các API `Long` tương ứng |
| Xem đúng 8 bit thấp của `byte` | Mask `value & 0xFF`, rồi format/pad |
| Parse lại unsigned 32/64 bit | `Integer.parseUnsignedInt` / `Long.parseUnsignedLong` |
| In hex luôn đủ chữ số | `String.format("%08x", intValue)` hoặc format thủ công |
| Xử lý giá trị unsigned trong phép tính | `compareUnsigned`, `divideUnsigned`, `remainderUnsigned`, hoặc kiểu rộng hơn |

Không nhất thiết mọi số âm đều phải in unsigned: với số dư nợ, nhiệt độ âm, độ lệch hay tọa độ có dấu, `Integer.toString` thường mới đúng ngữ nghĩa nghiệp vụ. Sử dụng `toUnsignedString` khi **dữ liệu thật sự mang ý nghĩa không dấu hoặc cần quan sát bit**.

## 11. Sai lầm thường gặp

1. **“`toBinaryString` trả integer nhị phân.”** Sai: trả `String`, không trả một giá trị primitive số mới.
2. **“`toHexString` luôn có tiền tố `0x`.”** Sai: kết quả chỉ gồm chữ số hex.
3. **“`toBinaryString(42)` luôn dài 32 ký tự.”** Sai: với số dương, zero đầu được bỏ; cần pad nếu giao thức yêu cầu.
4. **“`Integer.toBinaryString(-1)` trả `"-1"`.”** Sai: trả 32 bit `1` của số nguyên có dấu -1.
5. **“`toUnsignedString(-1)` biến `int` thành kiểu `uint`.”** Sai: biến vẫn là `int -1`, chỉ cách hiển thị đổi thành `4294967295`.
6. **“`Integer.toString(-1, 16)` giống `Integer.toHexString(-1)`.”** Sai: cái đầu trả `"-1"`, cái sau trả `"ffffffff"`.
7. **“Truyền `byte -1` vào `Integer.toBinaryString` sẽ trả 8 bit.”** Sai: `byte` được sign-extend thành `int`, nên cho 32 bit; mask `0xFF` để lấy 8 bit.
8. **“`String.format("%08x", x)` và `toHexString(x)` có cùng quy tắc padding.”** Sai: `%08x` yêu cầu độ rộng tối thiểu 8 và pad zero, còn `toHexString` không pad số dương.
9. **“Unsigned 64 bit luôn vừa `long` dưới dạng số dương.”** Sai: `2^64 - 1` vượt `Long.MAX_VALUE`; chỉ có thể giữ mẫu bit và diễn giải phù hợp.
10. **“`parseUnsignedInt` luôn trả số không âm khi `System.out.println(int)`.”** Sai: giá trị `int` có dấu có thể âm; dùng `toUnsignedString` khi in.
11. **“Chuỗi hex chứa ký tự `ff` là hai byte dữ liệu nhị phân.”** Sai: đó là hai ký tự; cần chuyển đổi thực sang byte nếu giao thức yêu cầu.
12. **“Đã gọi `toUnsignedString` thì toán tử `<` trên `int` sẽ so sánh không dấu.”** Sai: chỉ thao tác trên chuỗi hiển thị; phép so sánh `int` vẫn có dấu.
13. **“`Integer.parseInt("ffffffff", 16)` sẽ thành -1 vì chuỗi là mẫu bit.”** Sai: parser signed từ chuỗi không dấu trừ thấy số quá miền `int` và báo lỗi; dùng `parseUnsignedInt` để giải mã bit pattern.
14. **“Dùng hex luôn mô tả đúng thứ tự byte mạng.”** Sai: hex biểu diễn giá trị, còn endianness thuộc bước chuyển sang byte.

## 12. Câu hỏi tự kiểm tra

**1. `Integer.toBinaryString(10)` trả gì?** → `"1010"`.

**2. `Integer.toHexString(255)` trả gì?** → `"ff"` (không có `0x`).

**3. `Integer.toUnsignedString(-1)` cho giá trị nào?** → `"4294967295"`; biến gốc vẫn là `int -1`.

**4. Vì sao `Integer.toBinaryString(-1)` có 32 chữ `1`?** → `-1` biểu diễn bù hai bằng toàn bit 1 trong 32 bit.

**5. `Integer.toString(-1, 16)` khác `Integer.toHexString(-1)` như thế nào?** → Một bên là chuỗi số có dấu `"-1"`, bên kia là bit pattern hex `"ffffffff"`.

**6. Làm sao lấy chuỗi nhị phân của `byte -1` đúng 8 bit?** → Dùng `Integer.toBinaryString(b & 0xFF)`, với `b=-1` cho `"11111111"`; nếu `b` dương nhỏ, pad thêm zero.

**7. Làm sao in `int -1` theo unsigned 32 bit rồi parse lại giữ bit?** → `String s = Integer.toUnsignedString(-1); int raw = Integer.parseUnsignedInt(s);`; `raw` là mẫu `int -1`.

**8. `Long.toUnsignedString(-1L)` trả gì?** → `"18446744073709551615"`.

**9. `toHexString` có quyết định byte order khi ghi file không?** → Không; byte order được xác định ở bước serialize thành các byte.

**10. Khi nào không nên dùng `toUnsignedString`?** → Khi dữ liệu thật sự có ý nghĩa signed, ví dụ nợ âm hoặc nhiệt độ âm; hãy hiển thị theo ngữ nghĩa đúng.

**Ghi nhớ:** `toBinaryString` và `toHexString` biến **mẫu bit** `int`/`long` thành chuỗi cơ số 2/16, thường theo cách nhìn **không dấu**. `toUnsignedString` mặc định biểu diễn cùng mẫu bit ở **thập phân không dấu**, hoặc radix được chỉ định. Các hàm **không sửa bit, không tạo kiểu unsigned mới và không đổi ý nghĩa các phép toán khác**. Với `byte`/`short` âm, xử lý sign extension bằng mask trước khi format; với yêu cầu chiều rộng cố định, bổ sung padding.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-003">Float.toString, Double.toString và tính chất round-trip</a>

<details>
<summary>Xem chi tiết</summary>

# Float.toString, Double.toString và tính chất round-trip trong Java

> **Java core – numbers · Biểu diễn và định dạng**  
> **Mục tiêu:** Hiểu vì sao một giá trị `float`/`double` được in thành chuỗi thập phân ngắn nhưng vẫn đọc lại chính xác cùng giá trị nhị phân; phân biệt *round-trip* với biểu diễn thập phân toán học chính xác; xử lý số 0 có dấu, subnormal, Infinity, NaN và lựa chọn định dạng phù hợp khi lưu, trao đổi hoặc hiển thị dữ liệu.

## 1. Câu hỏi trung tâm: vì sao Java in 0.1 thay vì một dãy chữ số dài?

Hãy chạy:

```java
double value = 0.1;
System.out.println(Double.toString(value)); // 0.1
System.out.println(Double.toHexString(value));
// 0x1.999999999999ap-4
```

Giá trị `double` ở đây **không phải chính xác phân số toán học 1/10**. Nó là một trong các số nhị phân có thể biểu diễn được bằng IEEE 754 *binary64* và nằm rất gần 0.1.

Vậy tại sao `Double.toString(value)` chỉ trả `"0.1"` thay vì in ra toàn bộ phần thập phân của số nhị phân đã lưu? Vì mục tiêu của `toString` là tạo một **chuỗi thập phân gọn, đủ nhận diện giá trị floating-point ban đầu khi parse trở lại**, không phải xuất mọi chữ số của giá trị hữu tỉ nhị phân chính xác.

Chính xác hơn, với một giá trị hữu hạn `double`:

```java
double original = 0.1;
String text = Double.toString(original);
double recovered = Double.parseDouble(text);

System.out.println(text);                 // 0.1
System.out.println(recovered == original); // true
```

Chuỗi `"0.1"` mô tả số thực toán học 0.1. Khi `Double.parseDouble` làm tròn số thực ấy về `double` gần nhất, nó thu được **đúng giá trị binary64 đã lưu**.

**Phân biệt hai phát biểu:**

- “In ra rồi parse lại giữ được *giá trị `double` ban đầu*” — đây là tính chất *round-trip*.
- “Chuỗi in ra mô tả *chính xác mọi chữ số thập phân của giá trị nhị phân*” — **không phải** điều `Double.toString` hứa.

## 2. toString và parse tạo thành một cặp round-trip

Hai cặp API điển hình là:

```java
String floatText = Float.toString(floatValue);
float restoredFloat = Float.parseFloat(floatText);

String doubleText = Double.toString(doubleValue);
double restoredDouble = Double.parseDouble(doubleText);
```

*Round-trip* là hành trình:

```text
float bits (32 bit)
    └── Float.toString(float)
             └── văn bản decimal
                      └── Float.parseFloat(text)
                               └── giá trị float ban đầu

double bits (64 bit)
    └── Double.toString(double)
             └── văn bản decimal
                      └── Double.parseDouble(text)
                               └── giá trị double ban đầu
```

Với các giá trị **hữu hạn không phải NaN**, việc parse đúng chuỗi do `toString` tạo ra bằng **parser cùng kiểu** khôi phục giá trị dấu phẩy động ban đầu; điều này bao gồm các số normal, subnormal và cả hai số 0 có dấu.

Với `+Infinity` và `-Infinity`, chuỗi đặc biệt được parse lại thành đúng vô cực tương ứng.

Với **NaN**, `toString` trả `"NaN"` và parser tạo một NaN, nhưng **không được hứa bảo toàn dấu hay các bit payload của từng NaN**. Có nhiều mẫu bit IEEE 754 biểu diễn NaN và chúng bị gom lại thành một chuỗi chung.

### Kiểm tra round-trip theo raw bits

Với số hữu hạn, nên dùng các phép quan sát bit để kiểm tra chính xác:

```java
double before = -0.0;
String text = Double.toString(before); // "-0.0"
double after = Double.parseDouble(text);

boolean sameBits = Double.doubleToRawLongBits(before)
        == Double.doubleToRawLongBits(after);
System.out.println(sameBits); // true
```

`before == after` không đủ mạnh để xác nhận dấu zero, vì `+0.0 == -0.0` là `true` trong so sánh số học Java. Với `float`, dùng `Float.floatToRawIntBits` và `Float.intBitsToFloat` khi cần quan sát/mã hóa bit.

## 3. Quy tắc chữ số của toString trong Java 17

Tài liệu Java 17 mô tả việc in các chữ số thập phân sao cho **đủ để phân biệt giá trị đang in với các giá trị floating-point kề bên**, với số chữ số thêm vào được giữ ở mức cần thiết.

Với giá trị hữu hạn, khác 0, một biểu diễn thập phân phù hợp phải tròn về giá trị gốc khi parse theo quy tắc *round to nearest, ties to even*. Định dạng cũng bắt buộc có **ít nhất một chữ số phía sau dấu chấm thập phân**, kể cả khi giá trị là một số nguyên biểu diễn được.

Ví dụ:

```java
System.out.println(Float.toString(1.0f));  // 1.0
System.out.println(Double.toString(1.0)); // 1.0
```

`"1"` có thể được parse thành cùng giá trị số, nhưng định dạng `toString` của Java chọn `"1.0"`. Vì thế, gọi nó là “chuỗi ngắn nhất” nên hiểu là **biểu diễn thập phân ngắn theo quy tắc định dạng đã quy định, đủ để phân biệt giá trị**, không nhất thiết là chuỗi có ít ký tự nhất trong mọi ngữ pháp văn bản tùy ý.

**Không có số chữ số thập phân cố định** cho mọi `float` hay `double`. Mỗi giá trị có thể cần số chữ số khác nhau để phân biệt với giá trị có thể biểu diễn ngay bên cạnh. Đó là lý do đôi khi Java in `0.1`, đôi khi in một số dài hơn như `0.30000000000000004`.

**Cảnh báo phiên bản:** thuật toán chọn/định dạng chữ số của `toString` đã được cải tiến qua các phiên bản JDK. Khi kiểm thử đòi **đúng từng ký tự**, hãy dùng đúng phiên bản Java mục tiêu và tuân thủ hợp đồng API của phiên bản đó. Thuộc tính cần bảo vệ trong bài này là khả năng parse về **cùng giá trị nhị phân**, không phải coi mọi chuỗi của mọi JDK là bất biến theo thời gian.

## 4. Khi nào Java dùng dạng thập phân thông thường, khi nào dùng E?

Theo cách định dạng trong Java 17, với **độ lớn dương hữu hạn khác 0**:

- Từ `10^-3` (bao gồm) đến dưới `10^7`: hiển thị theo dạng decimal thông thường.
- Nhỏ hơn `10^-3` hoặc từ `10^7` trở lên: hiển thị bằng *computerized scientific notation*, có ký tự `E` viết hoa và số mũ thập phân.

Ví dụ:

| Giá trị đầu vào `double` | `Double.toString` |
| --- | --- |
| `0.0` | `"0.0"` |
| `-0.0` | `"-0.0"` |
| `1.0` | `"1.0"` |
| `0.001` | `"0.001"` |
| `0.0001` | `"1.0E-4"` |
| `1_000_000.0` | `"1000000.0"` |
| `10_000_000.0` | `"1.0E7"` |

Lưu ý:

- Chuỗi `"1.0E7"` và `"10000000.0"` mô tả cùng số thực toán học; đây là **lựa chọn định dạng**.
- Chuỗi `E7` có nghĩa **nhân 10^7**, không phải mũ nhị phân.
- `toString` không dùng dấu phân nhóm hàng nghìn, ký hiệu tiền tệ hay dấu phẩy thập phân phụ thuộc vùng.
- Dấu chấm thập phân của định dạng mặc định là dấu `.`, giúp chuỗi dễ dùng để trao đổi trong các hệ thống có cùng quy ước.

**Không dùng** `Float.toString` hoặc `Double.toString` như định dạng dành cho người dùng ở mọi quốc gia: giao diện bản địa hóa cần `NumberFormat` hoặc API định dạng với `Locale` được chỉ định.

## 5. Float và Double: cùng giá trị chữ viết nhưng khác bit

`float` và `double` có độ chính xác khác nhau:

| Kiểu | Định dạng IEEE 754 | Bit phần trị chính xác hiệu dụng (normal) |
| --- | --- | --- |
| `float` | binary32, 32 bit | 24 |
| `double` | binary64, 64 bit | 53 |

Xem:

```java
float f = 0.1f;
double d = 0.1;
double widened = f; // widening float -> double

System.out.println(Float.toString(f));    // 0.1
System.out.println(Double.toString(d));   // 0.1
System.out.println(Double.toString(widened));
// 0.10000000149011612
```

Tại sao `f` và `d` đều in `"0.1"`, nhưng `widened` khác?

1. `0.1f` được làm tròn đến một giá trị **binary32** gần 0.1.
2. `0.1` được làm tròn trực tiếp đến một giá trị **binary64** gần 0.1, chính xác hơn.
3. Khi chuyển `float f` lên `double`, giá trị binary32 được giữ chính xác trong binary64, **không tự trở thành giá trị của literal `double 0.1`**.
4. Chuỗi `"0.10000000149011612"` đủ để phân biệt giá trị double đã mở rộng ấy.

Điều này cũng giải thích một cạm bẫy:

```java
float f = 0.1f;
double fromFloatBits = (double) f;
double parsedAsDouble = Double.parseDouble(Float.toString(f));

System.out.println(fromFloatBits == parsedAsDouble); // false
```

`Float.toString(f)` bảo đảm round-trip **khi dùng `Float.parseFloat`**. Nếu bạn đọc chuỗi đó bằng `Double.parseDouble`, bạn đang parse một giá trị thập phân thành một **định dạng khác**, không phải giải mã lại trực tiếp mọi bit của `float`.

**Quy tắc:** Khi cần giữ giá trị `float`, lưu và parse với đúng kiểu `float`; khi cần giữ giá trị `double`, dùng cặp API `double`. Đừng âm thầm thay parser đích bằng kiểu khác rồi suy rằng mọi chuyển đổi vẫn giống nhau.

## 6. Phân biệt chuỗi round-trip và giá trị thập phân chính xác

Giá trị `double 0.1` thực ra là một số hữu tỉ nhị phân hữu hạn, có biểu diễn thập phân đầy đủ dài hơn `"0.1"`:

```java
import java.math.BigDecimal;

double d = 0.1;
System.out.println(Double.toString(d));
// 0.1

System.out.println(new BigDecimal(d));
// 0.1000000000000000055511151231257827021181583404541015625
```

Hai chuỗi khác nhau vì:

- `Double.toString(d)` chọn văn bản **gọn đủ để parse lại `d`**.
- `new BigDecimal(d)` xây dựng giá trị thập phân mô tả **chính xác giá trị binary64 hiện có**, có thể cần rất nhiều chữ số.

Tương tự:

```java
System.out.println(BigDecimal.valueOf(0.1)); // 0.1
```

`BigDecimal.valueOf(double)` sử dụng biểu diễn thập phân canonical của `double` (theo `Double.toString`) để tạo `BigDecimal`. Đây là lựa chọn chuyển đổi hợp lý trong nhiều tình huống, **nhưng không thể khôi phục ý định số học hay bit đã mất** ở các phép tính trước đó.

Nếu nguồn nghiệp vụ gốc là chuỗi decimal cần chính xác (như tiền), nên cân nhắc tạo:

```java
BigDecimal exact = new BigDecimal("0.1");
```

Không lấy một kết quả `double` đã tính xong rồi mặc định `BigDecimal.valueOf(result)` biến toàn bộ lịch sử phép tính thành chính xác vô hạn.

## 7. Subnormal và các biên: chuỗi có thể rất ngắn dù số rất nhỏ

Ví dụ:

```java
System.out.println(Float.toString(Float.MIN_VALUE));
// 1.4E-45
System.out.println(Double.toString(Double.MIN_VALUE));
// 4.9E-324
```

`Float.MIN_VALUE` và `Double.MIN_VALUE` là **số dương khác 0 nhỏ nhất** biểu diễn được bởi kiểu tương ứng, thuộc miền *subnormal*. Giá trị nhị phân chính xác của chúng:

\[
\mathrm{Float.MIN\_VALUE} = 2^{-149}
\]
\[
\mathrm{Double.MIN\_VALUE} = 2^{-1074}
\]

Chuỗi `"1.4E-45"` và `"4.9E-324"` không hiển thị đủ mọi chữ số của hai giá trị này dưới hệ thập phân. Chúng đủ để parser làm tròn về **đúng bit subnormal nhỏ nhất** của kiểu đích.

Các giá trị biên lớn nhất:

```java
System.out.println(Float.toString(Float.MAX_VALUE));
// 3.4028235E38
System.out.println(Double.toString(Double.MAX_VALUE));
// 1.7976931348623157E308
```

Điều quan trọng khi serialize:

- Không đặt một giới hạn cố định như “số thực phải có tối đa 6 chữ số sau dấu chấm” rồi kỳ vọng round-trip cho mọi `double`.
- Giá trị rất lớn/rất nhỏ cần **số mũ E**, parser nhận lại đúng chuỗi chuẩn nếu dùng API tương ứng.
- Các giá trị thuộc miền subnormal thường nhạy với việc làm tròn thêm; đừng định dạng ngắn hơn một cách tùy ý.

## 8. Số 0 có dấu: +0.0 và -0.0 phải được phân biệt nếu xét bit

IEEE 754 có hai biểu diễn số 0:

```java
double positiveZero = +0.0;
double negativeZero = -0.0;

System.out.println(positiveZero == negativeZero); // true
System.out.println(Double.toString(positiveZero)); // 0.0
System.out.println(Double.toString(negativeZero)); // -0.0
```

Toán tử `==` coi hai số 0 bằng nhau về **giá trị số học**, nhưng chuỗi `Double.toString` vẫn giữ dấu âm.

```java
double recovered = Double.parseDouble("-0.0");
System.out.println(Double.doubleToRawLongBits(recovered)
        == Double.doubleToRawLongBits(-0.0)); // true
```

Khi ký số dữ liệu, phân biệt biểu diễn bit hoặc kiểm thử exact, dấu `-0.0` có thể mang thông tin hữu ích. Nhưng trong nhiều phép so sánh toán học, hai số 0 tương đương về giá trị.

Tương tự với `float`:

```java
float negativeZero = -0.0f;
String text = Float.toString(negativeZero); // "-0.0"
float restored = Float.parseFloat(text);
```

Nếu cần bảo toàn **toàn bộ mẫu bit của các số không phải NaN**, dùng round-trip đúng kiểu và kiểm tra qua `floatToRawIntBits`/`doubleToRawLongBits`.

## 9. NaN và Infinity: trường hợp đặc biệt của round-trip

Ba chuỗi quan trọng:

| Giá trị | Chuỗi từ `Double.toString` / `Float.toString` |
| --- | --- |
| `Double.POSITIVE_INFINITY` | `"Infinity"` |
| `Double.NEGATIVE_INFINITY` | `"-Infinity"` |
| `Double.NaN` | `"NaN"` |

Ví dụ:

```java
double pos = Double.parseDouble("Infinity");
double neg = Double.parseDouble("-Infinity");
double nan = Double.parseDouble("NaN");

System.out.println(Double.isInfinite(pos)); // true
System.out.println(neg < 0.0);              // true
System.out.println(Double.isNaN(nan));      // true
```

### Vì sao NaN không có round-trip raw bits như số hữu hạn?

IEEE 754 có nhiều bit pattern NaN, với sign và payload khác nhau. Nhưng `Double.toString` trả cùng chữ `"NaN"` cho NaN, nên **văn bản này không mang thông tin về payload và bit dấu**. Khi parse lại, kết quả là một NaN, không phải một cam kết tái tạo đúng mọi raw bit của NaN ban đầu.

Ví dụ khái niệm:

```java
long raw = 0x7ff8000000000001L; // NaN có payload khác giá trị canonical
double customNan = Double.longBitsToDouble(raw);

String text = Double.toString(customNan); // "NaN"
double parsed = Double.parseDouble(text);

System.out.println(Double.isNaN(parsed)); // true
// Không suy ra Double.doubleToRawLongBits(parsed) == raw.
```

Nếu cần truyền tải hoặc ghi lại **mọi raw bit IEEE 754**, kể cả NaN payload, sử dụng mã hóa bit rõ ràng (ví dụ `Double.doubleToRawLongBits` và một dạng hex có chiều rộng cố định) cùng API ngược `Double.longBitsToDouble`. Cũng cần thống nhất thứ tự byte, signedness và quy tắc JSON/transport nếu trao đổi qua mạng.

**Lưu ý:** Chuẩn JSON thông thường **không có literal NaN hoặc Infinity**. Chuỗi `"NaN"` của Java phù hợp cho parser Java, nhưng việc nhét trực tiếp token `NaN` không có dấu ngoặc kép vào JSON chuẩn sẽ làm dữ liệu không hợp lệ.

## 10. toString, String.valueOf và println liên quan với nhau thế nào?

Các cách in phổ biến:

```java
double x = 12.5;
String a = Double.toString(x);
String b = String.valueOf(x);
System.out.println(x);
```

Với `double` primitive thông thường, `String.valueOf(double)` dùng cách chuyển tương ứng với `Double.toString` và `println(double)` cũng cung cấp chuỗi hiển thị theo ngữ nghĩa chuyển đổi của Java. Với wrapper `Double`, lời gọi `wrapper.toString()` trả chuỗi của giá trị bên trong.

```java
Double wrapper = Double.valueOf(12.5);
String text = wrapper.toString(); // "12.5"
```

Nhưng:

```java
Double maybe = null;
// String text = maybe.toString(); // NullPointerException
String text = String.valueOf(maybe); // "null"
```

Hai phương thức trên khác nhau khi tham chiếu đối tượng là `null`: `String.valueOf(Object)` có thể trả `"null"`. Chuỗi `"null"` **không** phải một số hợp lệ để `Double.parseDouble` đọc thành numeric value; thiết kế API phải biểu diễn null riêng.

Tương tự, khi `Float wrapper = null` thì `wrapper.toString()` cũng ném NPE, còn `String.valueOf(wrapper)` có thể trả `"null"`.

## 11. Round-trip không giống định dạng hiển thị có số chữ số cố định

Ví dụ một giao diện muốn in đúng 2 chữ số sau dấu chấm:

```java
import java.util.Locale;

double pi = Math.PI;

String display = String.format(Locale.ROOT, "%.2f", pi);
System.out.println(display); // 3.14

double parsed = Double.parseDouble(display);
System.out.println(parsed == pi); // false
```

Định dạng cố định hai chữ số đã **làm tròn thông tin** về độ chính xác, nên không thể khôi phục đúng `Math.PI` ban đầu.

So sánh với cách round-trip:

```java
String canonical = Double.toString(Math.PI);
System.out.println(canonical); // 3.141592653589793
System.out.println(Double.parseDouble(canonical) == Math.PI); // true
```

**Dùng đúng mục đích:**

| Mục tiêu | API thường phù hợp |
| --- | --- |
| Chuỗi Java có thể parse trở lại cùng `float` | `Float.toString` ↔ `Float.parseFloat` |
| Chuỗi Java có thể parse trở lại cùng `double` | `Double.toString` ↔ `Double.parseDouble` |
| In số cho con người, nhóm hàng nghìn, dấu thập phân theo vùng | `NumberFormat` với `Locale` đã chọn |
| Cố định 2 chữ số để hiển thị báo cáo | `String.format(Locale.ROOT, "%.2f", x)` hoặc format theo nghiệp vụ |
| Cần chính xác thập phân theo scale/rounding | `BigDecimal` và quy tắc tạo/round được định nghĩa |
| Bảo toàn bit IEEE 754 kể cả payload NaN | Raw bits + mã hóa nhị phân/hex được đặc tả rõ |

Tránh dùng `String.format("%.2f", x)` để serialize số thực cần exact round-trip. Ngược lại, `Double.toString` không tự đáp ứng yêu cầu xuất hóa đơn “luôn có đúng 2 số lẻ”.

## 12. Làm sao kiểm thử round-trip cho đúng?

### 12.1. Dùng bits cho số hữu hạn và signed zero

```java
static boolean doubleRoundTrips(double x) {
    if (Double.isNaN(x)) {
        return Double.isNaN(
                Double.parseDouble(Double.toString(x)));
    }
    double y = Double.parseDouble(Double.toString(x));
    return Double.doubleToRawLongBits(x)
            == Double.doubleToRawLongBits(y);
}
```

Đây là phương thức minh họa cần đặt trong một lớp. Nó kiểm tra bit cho mọi giá trị **không phải NaN**, kể cả hai số 0 và Infinity; đối với NaN, nó kiểm tra **giữ được trạng thái NaN**, không yêu cầu payload giống nhau.

Với `float`, mẫu kiểm tra tương tự:

```java
static boolean floatRoundTrips(float x) {
    if (Float.isNaN(x)) {
        return Float.isNaN(
                Float.parseFloat(Float.toString(x)));
    }
    float y = Float.parseFloat(Float.toString(x));
    return Float.floatToRawIntBits(x)
            == Float.floatToRawIntBits(y);
}
```

Nếu dùng `Double.doubleToLongBits` thay vì `Double.doubleToRawLongBits`, các mẫu NaN sẽ được **canonicalize**. Điều đó hữu ích trong một số phép so sánh giá trị nhưng **không phản ánh raw payload** ban đầu. Hãy xác định rõ bạn muốn kiểm tra “cùng giá trị floating-point” hay “cùng mẫu bit”.

### 12.2. Bộ dữ liệu test nên có gì?

- `+0.0`, `-0.0`;
- số normal như `1.0`, `0.1`, `Math.PI`;
- số subnormal như `Double.MIN_VALUE`, `Float.MIN_VALUE`;
- biên normal như `Float.MIN_NORMAL`, `Double.MIN_NORMAL`;
- biên hữu hạn lớn nhất như `Float.MAX_VALUE`, `Double.MAX_VALUE`;
- `Infinity` dương/âm và NaN;
- số lớn có ULP > 1 để kiểm tra mất thông tin khi chuyển qua loại dữ liệu khác.

Một kiểm thử tốt không chỉ nhìn chuỗi bằng mắt; nó **parse bằng đúng kiểu** và kiểm tra kết quả theo tiêu chí cần giữ.

## 13. Tránh nhầm giữa định dạng decimal và biểu diễn hex floating-point

Ngoài `toString` thập phân, Java còn có:

```java
System.out.println(Double.toHexString(0.1));
// 0x1.999999999999ap-4
System.out.println(Float.toHexString(0.1f));
// 0x1.99999ap-4
```

Đây là cách mô tả **giá trị nhị phân** bằng phần trị hệ 16 và số mũ **của 2** (ký tự `p`/`P`).

Ví dụ `0x1.8p1` biểu diễn `1.5 × 2^1 = 3.0`, không phải `1.8 × 10^1`.

`Double.parseDouble` và `Float.parseFloat` chấp nhận cú pháp hex floating-point hợp lệ, nên dạng `toHexString` cũng hữu ích khi cần ghi số học nhị phân chính xác ở dạng văn bản.

Nhưng `toHexString` vẫn in `"NaN"` đối với NaN; do đó nó **không phải** cách serializing payload NaN qua một chuỗi biểu diễn giá trị. Dùng API raw bits khi cần điều ấy.

**So sánh:**

- `Double.toString(0.1)` → `"0.1"`: dễ đọc dạng decimal, đủ cho round-trip.
- `Double.toHexString(0.1)` → `"0x1.999999999999ap-4"`: thể hiện giá trị nhị phân một cách tường minh.
- `Long.toHexString(Double.doubleToRawLongBits(0.1))` → chuỗi hex của **64 bit lưu trữ**, phản ánh bit dấu, exponent và fraction chứ không phải công thức trị số `0x...p...`.

Ba dạng có mục đích khác nhau. Không trộn chúng khi định nghĩa giao thức dữ liệu hoặc so sánh output.

## 14. Chương trình Java 17+ hoàn chỉnh

Tạo tệp **`FloatingPointRoundTripDemo.java`**:

```java
import java.util.Locale;

public class FloatingPointRoundTripDemo {
    private static boolean sameDoubleBits(double a, double b) {
        return Double.doubleToRawLongBits(a)
                == Double.doubleToRawLongBits(b);
    }

    private static boolean sameFloatBits(float a, float b) {
        return Float.floatToRawIntBits(a)
                == Float.floatToRawIntBits(b);
    }

    public static void main(String[] args) {
        float f = 0.1f;
        double d = 0.1;
        double widenedFloat = f;

        System.out.println("float text = " + Float.toString(f));
        System.out.println("double text = " + Double.toString(d));
        System.out.println("widened float = "
                + Double.toString(widenedFloat));
        System.out.println("double hex = " + Double.toHexString(d));

        float parsedF = Float.parseFloat(Float.toString(f));
        double parsedD = Double.parseDouble(Double.toString(d));
        System.out.println("float round-trip = "
                + sameFloatBits(f, parsedF));
        System.out.println("double round-trip = "
                + sameDoubleBits(d, parsedD));

        double parsedFloatTextAsDouble =
                Double.parseDouble(Float.toString(f));
        System.out.println("wrong target type = "
                + (widenedFloat == parsedFloatTextAsDouble));

        System.out.println("float minimum = "
                + Float.toString(Float.MIN_VALUE));
        System.out.println("double minimum = "
                + Double.toString(Double.MIN_VALUE));
        System.out.println("double maximum = "
                + Double.toString(Double.MAX_VALUE));

        System.out.println("plain lower = "
                + Double.toString(0.001));
        System.out.println("scientific lower = "
                + Double.toString(0.0001));
        System.out.println("plain upper = "
                + Double.toString(1_000_000.0));
        System.out.println("scientific upper = "
                + Double.toString(10_000_000.0));

        double negativeZero = -0.0;
        double restoredZero =
                Double.parseDouble(Double.toString(negativeZero));
        System.out.println("negative zero = "
                + Double.toString(negativeZero));
        System.out.println("negative zero round-trip = "
                + sameDoubleBits(negativeZero, restoredZero));
        System.out.println("signed zeros == = "
                + (+0.0 == -0.0));

        System.out.println("infinity = "
                + Double.toString(Double.POSITIVE_INFINITY));
        System.out.println("NaN parsed = "
                + Double.isNaN(
                        Double.parseDouble(Double.toString(Double.NaN))));

        String display = String.format(Locale.ROOT, "%.2f", Math.PI);
        System.out.println("display Pi = " + display);
        System.out.println("display round-trip = "
                + sameDoubleBits(Math.PI,
                        Double.parseDouble(display)));
    }
}
```

Chạy từ thư mục chứa tệp:

```bash
javac FloatingPointRoundTripDemo.java
java FloatingPointRoundTripDemo
```

**Kết quả dự kiến trên Java 17:**

```text
float text = 0.1
double text = 0.1
widened float = 0.10000000149011612
double hex = 0x1.999999999999ap-4
float round-trip = true
double round-trip = true
wrong target type = false
float minimum = 1.4E-45
double minimum = 4.9E-324
double maximum = 1.7976931348623157E308
plain lower = 0.001
scientific lower = 1.0E-4
plain upper = 1000000.0
scientific upper = 1.0E7
negative zero = -0.0
negative zero round-trip = true
signed zeros == = true
infinity = Infinity
NaN parsed = true
display Pi = 3.14
display round-trip = false
```

**Những điều chương trình xác minh:**

1. `0.1f` và `0.1` đều có biểu diễn decimal ngắn `0.1` nhưng là các giá trị nhị phân khác nhau; mở rộng `float` thành `double` làm sự khác biệt hiện rõ.
2. `toString` → `parseXxx` cùng kiểu giữ nguyên raw bits của `0.1f`/`0.1`.
3. Parse văn bản `Float.toString(f)` bằng `Double.parseDouble` có thể thu được một `double` khác với giá trị `double` chuyển từ `f`.
4. Các giá trị cực nhỏ/cực lớn có biểu diễn khoa học gọn và có thể parse ngược bằng parser tương ứng.
5. Chuỗi `"-0.0"` giữ bit dấu qua round-trip; so sánh số học `+0.0 == -0.0` vẫn true.
6. NaN được khôi phục theo **loại giá trị**, không cam kết payload; định dạng cố định hai chữ số mất độ chính xác của `Math.PI`.

## 15. Lỗi phổ biến cần tránh

| Hiểu lầm | Giải thích đúng |
| --- | --- |
| “`Double.toString(0.1)` ra 0.1 nghĩa là double lưu chính xác 1/10” | Chuỗi được chọn để parse lại cùng giá trị nhị phân; giá trị lưu vẫn là số gần 1/10 |
| “Float và Double cùng in 0.1 thì raw bits có cùng giá trị số” | Hai kiểu có độ chính xác khác nhau; widen `0.1f` cho `0.10000000149011612` |
| “Chuỗi round-trip có thể chứa số chữ số tùy ý và vẫn giữ nguyên” | Rút ngắn/làm tròn thêm có thể biến thành giá trị khác khi parse |
| “Round-trip nghĩa là giữ toàn bộ bit của mọi NaN” | Chuỗi `NaN` không bảo toàn payload/dấu NaN |
| “Dùng `==` kiểm tra round-trip là đủ cả với -0.0” | `+0.0 == -0.0`; cần so raw bits nếu quan tâm signed zero |
| “Double.toString luôn in số thập phân không có E” | Java dùng `E` khi độ lớn nằm ngoài khoảng hiển thị decimal thường |
| “String.format("%.2f") cho phép round-trip nguyên vẹn” | Format này giới hạn chữ số nên có thể mất độ chính xác |
| “Double.toHexString là raw 64-bit hex” | `0x...p...` biểu diễn giá trị nhị phân; raw bits cần `doubleToRawLongBits` |
| “String.valueOf(null Double) luôn giống gọi wrapper.toString” | `String.valueOf(Object)` trả `"null"`; gọi phương thức trên null gây NPE |
| “Tăng số chữ số in ra có thể sửa lỗi số học đã xảy ra” | Format không khôi phục thông tin mất do phép tính/làm tròn trước đó |
| “Locale không ảnh hưởng định dạng hiển thị” | `NumberFormat`/`String.format` có thể chịu locale; `Double.toString` là định dạng Java độc lập locale |
| “Chuỗi Infinity và NaN hợp lệ như số trong JSON chuẩn” | JSON chuẩn không có các literal này; giao thức phải định nghĩa cách xử lý |
| “Mọi JDK phải in giống từng ký tự từ mọi thuật toán chuyển số” | Khi cần so sánh literal output, phải biết hợp đồng/phiên bản JDK; mục tiêu chính là round-trip đúng giá trị |

## 16. Bài tập có lời giải

**Bài 1.** Tại sao `Double.toString(0.1)` trả `"0.1"` dù `new BigDecimal(0.1)` in nhiều chữ số hơn?  
**Lời giải:** `toString` tìm văn bản thập phân ngắn đủ để parse về cùng `double`; `new BigDecimal(double)` mô tả chính xác giá trị nhị phân đã lưu trong thập phân.

**Bài 2.** Với `float f = 0.1f;`, `Float.parseFloat(Float.toString(f))` có lấy lại giá trị `float` ban đầu không?  
**Lời giải:** Có. Đây là đúng cặp API bảo đảm round-trip cho `float`, bao gồm mẫu bit của các giá trị không phải NaN.

**Bài 3.** Vì sao `Double.parseDouble(Float.toString(0.1f))` không nhất thiết bằng `(double)0.1f`?  
**Lời giải:** Chuỗi `"0.1"` được chọn để parse **về `float`**. Khi parse trực tiếp thành `double`, nó thành giá trị binary64 gần 0.1 chứ không phải binary64 biểu diễn chính xác giá trị binary32 đã widen.

**Bài 4.** `Double.toString(-0.0)` in gì? `Double.parseDouble` có giữ dấu zero không?  
**Lời giải:** In `"-0.0"` và parse lại thành zero âm. Kiểm tra raw bits bằng `Double.doubleToRawLongBits`; phép `==` không phân biệt được ±0.

**Bài 5.** `Double.toString(Double.MIN_VALUE)` trả chuỗi nào và có phải in toàn bộ thập phân chính xác của `2^-1074` không?  
**Lời giải:** `"4.9E-324"` trên Java 17. Đó là chuỗi đủ để parse trở lại `Double.MIN_VALUE`, không phải mọi chữ số thập phân của giá trị hữu tỉ nhị phân chính xác.

**Bài 6.** Khi `Double.toString(Double.NaN)` trả `"NaN"`, có thể tái tạo mọi payload NaN ban đầu bằng `parseDouble` không?  
**Lời giải:** Không. Ta chỉ bảo đảm parse ra một NaN. Nếu phải bảo toàn payload, cần serializing raw bits.

**Bài 7.** Giải thích tại sao `String.format(Locale.ROOT, "%.2f", Math.PI)` không bảo đảm round-trip.  
**Lời giải:** Nó tạo `"3.14"`, đã bỏ các chữ số cần thiết để phân biệt `Math.PI` với các `double` lân cận. Parse lại thành một `double` khác.

**Bài 8.** Giá trị `10_000_000.0` được `Double.toString` in dạng nào trong Java 17?  
**Lời giải:** `"1.0E7"`, vì miền dùng dạng thập phân thông thường chỉ bao gồm độ lớn nhỏ hơn `10^7`.

**Bài 9.** API nào thích hợp để ghi nguyên mẫu bit của NaN có payload khác nhau?  
**Lời giải:** `Double.doubleToRawLongBits` hoặc `Float.floatToRawIntBits`, sau đó tự mã hóa bit theo định dạng dữ liệu; khi đọc dùng `Double.longBitsToDouble` hoặc `Float.intBitsToFloat`.

**Bài 10.** Có thể dùng `Double.toString` thay cho định dạng hóa đơn luôn đúng hai chữ số phần lẻ không?  
**Lời giải:** Không. Nó được tối ưu cho chuỗi giá trị floating-point có thể parse lại, không bảo đảm độ dài phần thập phân hay quy tắc làm tròn tài chính. Chọn `BigDecimal` và định dạng theo hợp đồng.

## 17. Checklist khi xuất/nhập float và double

1. **Xác định mục tiêu:** xuất để con người đọc, để lưu/trao đổi có round-trip, hay để bảo toàn raw bits?
2. **Dùng đúng cặp kiểu:** `Float.toString` ↔ `Float.parseFloat`; `Double.toString` ↔ `Double.parseDouble`.
3. **Đừng suy ngược từ văn bản:** một chuỗi `0.1` không có nghĩa binary floating-point lưu chính xác thập phân 0.1.
4. **Đừng dùng định dạng ít chữ số** nếu cần bảo toàn giá trị bit; đặc biệt với số có ULP nhỏ hoặc chuỗi đã tính toán phức tạp.
5. **Kiểm tra signed zero:** `-0.0` có thể cần được giữ; raw bit comparison đáng dùng khi test serialization.
6. **Xử lý NaN riêng:** round-trip văn bản giữ trạng thái NaN nhưng không cam kết payload; Infinity cũng cần được giao thức cho phép.
7. **Nhận biết scientific notation:** `E` là lũy thừa của 10; `p` trong `toHexString` là lũy thừa của 2.
8. **Kiểm tra locale/giao thức:** `toString` ổn định theo quy tắc Java, còn UI và JSON/CSV có ràng buộc định dạng riêng.
9. **Nếu cần decimal exact**, lưu từ nguồn `String` hoặc dùng `BigDecimal` theo quy tắc nghiệp vụ, không đổi kiểu sau khi `double` đã làm tròn để mong “lấy lại dữ liệu”.
10. **Kiểm thử biên:** ±0, subnormal, max, infinity, NaN và sự khác nhau giữa widening `float → double` và parse decimal thành `double`.

### Tài liệu Java 17

- [Float – toString, parseFloat, toHexString, floatToRawIntBits](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html)
- [Double – toString, parseDouble, toHexString, doubleToRawLongBits](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html)
- [BigDecimal – constructor từ double và valueOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)

**Ghi nhớ:** `Float.toString` và `Double.toString` chọn một văn bản thập phân **đủ để parse lại đúng giá trị floating-point**, không phải xuất đầy đủ thập phân toán học của bit pattern. Hãy dùng parser **cùng kiểu**, kiểm tra bit khi cần phân biệt số 0 có dấu, xử lý NaN riêng và chọn định dạng chuyên biệt nếu yêu cầu là giao diện, tiền hoặc bảo toàn raw bits.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-004">toHexString cho dấu phẩy động</a>

<details>
<summary>Xem chi tiết</summary>

# toHexString cho dấu phẩy động trong Java

Trong Java, **`Double.toHexString(double)`** và **`Float.toHexString(float)`** biểu diễn **giá trị dấu phẩy động** ở dạng **số thực hệ thập lục phân có số mũ cơ số 2**. Đây là cách hữu ích để quan sát chính xác những bit có nghĩa của một giá trị `float`/`double`, đặc biệt khi cần phân tích làm tròn, ULP, số subnormal và sự khác nhau giữa `0.1f` với `0.1d`.

```java
System.out.println(Double.toHexString(3.0));   // 0x1.8p1
System.out.println(Float.toHexString(1.0f));   // 0x1.0p0
System.out.println(Double.toHexString(0.1));   // 0x1.999999999999ap-4
```

Các lệnh trên là mảnh minh họa có thể đặt trong một phương thức Java. **Đừng nhầm `Double.toHexString` với `Integer.toHexString`**: hàm thứ nhất xuất ra **ký pháp số thực với dấu chấm và `p`**, còn hàm thứ hai xuất ra **chữ số hex của mẫu số nguyên**. `Double.toHexString` cũng **không phải** một phép lấy trực tiếp raw IEEE 754 bits hoặc chuỗi byte theo thứ tự endian.

## 1. Hai API và mục đích

| API | Đối số | Trả về | Ví dụ |
| --- | --- | --- | --- |
| `Double.toHexString(double d)` | Giá trị `double` 64 bit | `String` dạng hex floating-point | `Double.toHexString(1.0)` → `"0x1.0p0"` |
| `Float.toHexString(float f)` | Giá trị `float` 32 bit | `String` dạng hex floating-point | `Float.toHexString(1.0f)` → `"0x1.0p0"` |

Cả hai phương thức là **static**, dùng qua tên lớp `Double` hoặc `Float`. Không phải gọi trên đối tượng wrapper mới dùng được:

```java
double x = 3.0;
String formatted = Double.toHexString(x);  // "0x1.8p1"
```

Kết quả là **chuỗi để quan sát/ghi log/trao đổi dữ liệu dạng văn bản**, không làm thay đổi giá trị gốc. Phương thức không làm tròn thành một số chữ số thập phân theo locale; nó biểu diễn giá trị nhị phân đã lưu bằng ký pháp hex phù hợp.

Hình thức chuỗi dễ nhầm ở điểm: chữ số phần trị là **hex**, nhưng số mũ đứng sau `p` biểu thị **lũy thừa của 2**, không phải của 16 hay của 10. Dấu `p` là thành phần bắt buộc trong cú pháp hex floating-point được Java hỗ trợ.

## 2. Đọc chuỗi `0x1.8p1`

Một số hex floating-point hữu hạn normal có dạng đại ý:

```text
[-]0xH.FpE
    └─┬─┘ └┬┘
      │    └── E là số mũ viết bằng chữ số thập phân,
      │        nhưng chỉ lũy thừa của 2
      └─────── H.F là phần trị viết theo hệ 16
```

Đối với chuỗi `0x1.8p1`:

- `0x`: báo phần trị ở **cơ số 16**.
- `1.8`: phần trị = `1 + 8/16 = 1.5`.
- `p1`: nhân phần trị với `2^1 = 2`.
- Giá trị = `1.5 × 2 = 3.0`.

```text
0x1.8p1 = (1 + 8/16) × 2^1 = 3.0
0x1.0p0 = 1 × 2^0 = 1.0
0x1.0p-1 = 1 × 2^-1 = 0.5
0x1.0p3 = 1 × 2^3 = 8.0
0x1.fp3 = (1 + 15/16) × 2^3 = 15.5
```

Sau dấu chấm, **mỗi chữ số hex tương đương bốn bit nhị phân**, nên ký pháp rất thích hợp để xem một giá trị đã được biểu diễn sát các ngưỡng làm tròn hay chưa. Ví dụ `0x1.8` có phần `.8` tương đương `.1000` ở hệ nhị phân.

**Không đọc `p1` như `×10^1` hoặc `×16^1`**. Với các dấu phẩy động hex trong Java, `p` dùng số mũ cơ số 2; ký hiệu số mũ thập phân là `e` trong chuỗi thập phân như `1.5e2`.

## 3. Normal `double` và `float` được in khác nhau về số chữ số phần trị

Hai kiểu này đều theo biểu diễn nhị phân IEEE 754 nhưng có độ chính xác khác nhau:

| Thuộc tính | `float` | `double` |
| --- | ---: | ---: |
| Tổng số bit | 32 | 64 |
| Bit fraction được lưu | 23 | 52 |
| Bit significand hiệu dụng ở số normal | 24 | 53 |
| Số mũ không thiên lệch ở miền normal | -126 đến +127 | -1022 đến +1023 |
| ULP quanh `1.0` | `2^-23` | `2^-52` |

Vì một chữ số hex tương ứng bốn bit, phần fraction của `double` normal có thể cần tới **13 chữ số hex** để mô tả 52 bit. `float` normal cần nhiều nhất **6 chữ số hex** để mã hóa 23 bit fraction (bit thấp của chữ số cuối chứa phần đệm trong cách viết).

Ví dụ:

```java
System.out.println(Double.toHexString(0.1d));
// 0x1.999999999999ap-4

System.out.println(Float.toHexString(0.1f));
// 0x1.99999ap-4
```

Giá trị `0.1` trong thập phân không có biểu diễn nhị phân hữu hạn, nên hai kiểu làm tròn đến hai lưới biểu diễn khác nhau. Các chuỗi trên cho ta thấy rõ **các giá trị nhị phân lưu thực tế**; không phải hai cách “trang trí” khác nhau cho một giá trị chính xác `1/10`.

Các chữ số hex không phải một độ chính xác thập phân tùy chọn được ép theo số chữ số sau dấu chấm. `toHexString` tạo chuỗi xác định theo giá trị `float` hoặc `double` đã có, không tự tăng độ chính xác của biến.

## 4. Tại sao `0x1.0p0` không ghi đủ mọi chữ số 0 của fraction?

Java xuất **dạng ngắn gọn, có quy tắc**, thay vì luôn đệm đủ 6 hoặc 13 chữ số phần fraction.

```java
Double.toHexString(1.0);  // "0x1.0p0"
Double.toHexString(2.0);  // "0x1.0p1"
Double.toHexString(3.0);  // "0x1.8p1"
```

Ở `1.0`, fraction toàn zero, nên không cần ghi `0x1.0000000000000p0`. Java vẫn để **ít nhất một chữ số sau dấu chấm**, vì đây là định dạng của một số thực hex chứ không phải integer hex.

Trong miền số normal, chữ số đầu thường là `1` vì **significand chuẩn hóa** của số nhị phân có dạng `1.fraction`. Điều đó khác miền **subnormal**, nơi phần đầu được in `0.` và số mũ được giữ ở cận normal nhỏ nhất.

Lưu ý một giá trị có thể có **nhiều chuỗi toán học tương đương** trước khi chuẩn hóa, ví dụ `0x1.0p0` và `0x2.0p-1` cùng bằng 1. Hàm `toHexString` chọn định dạng được chuẩn hóa theo đặc tả API; không phải mọi chuỗi mà parser chấp nhận đều sẽ được in lại y nguyên.

## 5. Số normal: ví dụ biểu diễn các ngưỡng

Đối với `double`:

| Giá trị | `Double.toHexString` |
| --- | --- |
| `0.5` | `0x1.0p-1` |
| `1.0` | `0x1.0p0` |
| `2.0` | `0x1.0p1` |
| `3.0` | `0x1.8p1` |
| `Math.nextUp(1.0)` | `0x1.0000000000001p0` |
| `Double.MIN_NORMAL` | `0x1.0p-1022` |
| `Double.MAX_VALUE` | `0x1.fffffffffffffp1023` |

Với `float`:

| Giá trị | `Float.toHexString` |
| --- | --- |
| `1.0f` | `0x1.0p0` |
| `Math.nextUp(1.0f)` | `0x1.000002p0` |
| `Float.MIN_NORMAL` | `0x1.0p-126` |

Đặc biệt, `Double.MAX_VALUE` có phần trị ngay dưới 2 nhân với `2^1023`. Đó là số `double` **hữu hạn lớn nhất**, không phải Infinity; chuỗi hex `0x1.fffffffffffffp1023` giúp nhìn chính xác giới hạn fraction và mũ.

Khi `Math.nextUp(1.0)` thêm bước ULP nhỏ nhất phía trên, bit fraction cuối thay đổi:

```text
1.0                  0x1.0p0
nextUp(1.0)          0x1.0000000000001p0
Math.ulp(1.0)        0x1.0p-52
```

Hex floating-point là công cụ kiểm thử đặc biệt tốt ở các ranh giới **làm tròn**, **số mũ**, và các hàm số có sai số chỉ vài ULP mà cách in decimal mặc định đôi khi khó theo dõi.

## 6. Subnormal: vì sao xuất hiện `0x0....p-1022`?

Các số dương nhỏ nhất của `double` thuộc miền **subnormal**, tức dưới `Double.MIN_NORMAL`. Trong miền này không có bit `1` đầu ẩn của significand normal. Vì thế chuỗi `toHexString` có dạng **`0x0.fractionp-1022`**.

Ví dụ Java 17:

```java
System.out.println(Double.toHexString(Double.MIN_NORMAL));
// 0x1.0p-1022

System.out.println(Double.toHexString(Double.MIN_VALUE));
// 0x0.0000000000001p-1022
```

Ở đây:

- `Double.MIN_NORMAL = 2^-1022`: số dương **normal** nhỏ nhất.
- `Double.MIN_VALUE = 2^-1074`: số `double` **dương nhỏ nhất khác 0**, là subnormal.
- Trong chuỗi `0x0.0000000000001p-1022`, fraction cuối có giá trị `16^-13 = 2^-52`; nhân `2^-1022` cho `2^-1074`.

Với `float`, sự khác biệt tương tự nhưng số mũ và độ rộng khác:

```java
System.out.println(Float.toHexString(Float.MIN_NORMAL));
// 0x1.0p-126

System.out.println(Float.toHexString(Float.MIN_VALUE));
// 0x0.000002p-126
```

`Float.MIN_VALUE = 2^-149` và phần `0x0.000002` tương ứng `2^-23`; nhân thêm `2^-126` ra `2^-149`.

**Đừng nhầm `Double.MIN_VALUE` là số âm nhỏ nhất**. Đây là giá trị dương gần 0 nhất; số hữu hạn âm có độ lớn lớn nhất là `-Double.MAX_VALUE`.

## 7. `+0.0`, `-0.0` và dấu trong chuỗi hex

Java dùng IEEE 754 với **hai mẫu bit zero**:

```java
System.out.println(Double.toHexString(+0.0)); // 0x0.0p0
System.out.println(Double.toHexString(-0.0)); // -0x0.0p0
System.out.println(-0.0 == +0.0);             // true
```

Hai zero so sánh bằng nhau bằng toán tử `==`, nhưng khác **bit dấu**. `toHexString` giữ khác biệt bằng dấu `-` đầu chuỗi.

Điều này quan trọng nếu bạn cần phân biệt kết quả tại các biên hoặc xác minh **round-trip bit-level**. Parse lại `"-0x0.0p0"` bằng `Double.parseDouble` cho mẫu bit negative zero; không nên dựa vào so sánh `==` để kiểm tra hai mẫu zero.

Một cách kiểm tra rõ ràng:

```java
double original = -0.0;
double parsed = Double.parseDouble(Double.toHexString(original));
boolean sameBits = Double.doubleToRawLongBits(original)
        == Double.doubleToRawLongBits(parsed); // true
```

`Math.signum`, phép chia cho zero và các phép toán khác cũng có thể biểu hiện khác nhau với hai dấu zero. Không tự bỏ dấu âm khỏi kết quả hex nếu bạn muốn giữ nguyên ý nghĩa của giá trị dấu phẩy động.

## 8. `NaN` và Infinity không được in như số hex hữu hạn

Hai phương thức `toHexString` trả **tên giá trị đặc biệt** thay vì dựng một số `0x...`:

| Đầu vào | Chuỗi kết quả |
| --- | --- |
| `Double.NaN`, `Float.NaN` | `"NaN"` |
| `Double.POSITIVE_INFINITY`, `Float.POSITIVE_INFINITY` | `"Infinity"` |
| `Double.NEGATIVE_INFINITY`, `Float.NEGATIVE_INFINITY` | `"-Infinity"` |

Ví dụ:

```java
Double.toHexString(Double.NaN);               // "NaN"
Double.toHexString(Double.POSITIVE_INFINITY); // "Infinity"
Double.toHexString(Double.NEGATIVE_INFINITY); // "-Infinity"
```

`NaN` (Not a Number) có thể có **nhiều mẫu bit payload**, nhưng `toHexString` trả một chuỗi chung `"NaN"`. Vì vậy **không thể dùng kết quả `toHexString` để giữ nguyên payload NaN**. Khi cần chính xác từng bit của giá trị `NaN`, phải sử dụng API raw bits phù hợp và một định dạng truyền tải lưu toàn bộ bit.

`Double.parseDouble("NaN")` có thể tạo một mẫu NaN hợp lệ nhưng không biết payload của giá trị ban đầu. Tương tự, `Float.parseFloat("NaN")` không phục hồi payload `float` ban đầu. Phân biệt **round-trip về giá trị đặc biệt** với **round-trip bit-for-bit**.

## 9. Parse ngược: `Double.parseDouble` và `Float.parseFloat`

`Double.parseDouble` và `Float.parseFloat` chấp nhận cả ký pháp số thực thập phân và **hex floating-point**, miễn chuỗi đúng quy tắc parser:

```java
double d = Double.parseDouble("0x1.8p1");  // 3.0
float f = Float.parseFloat("0x1.8p1");    // 3.0f

double x = 0.1;
String hex = Double.toHexString(x);
double restored = Double.parseDouble(hex);
```

Đối với số **hữu hạn**, dạng chuỗi từ `Double.toHexString` chứa đủ thông tin để parse lại thành **cùng mẫu bit `double`** trong cùng quy tắc Java. Tương tự với `Float.toHexString` và `Float.parseFloat` cho `float`.

Chuỗi hex `"0x1.8p1"` khác chuỗi hex nguyên `"1A"`; không truyền nó vào `Integer.parseInt(...,16)` để mong lấy giá trị số thực. Đối với hex floating-point, **`p` và số mũ nhị phân** là thành phần cú pháp chính.

Ví dụ kiểm tra round-trip:

```java
double original = Math.PI;
String valueAsText = Double.toHexString(original);
double reconstructed = Double.parseDouble(valueAsText);

boolean sameRawBits =
        Double.doubleToRawLongBits(original)
        == Double.doubleToRawLongBits(reconstructed);
```

Trong trường hợp `Math.PI` hữu hạn, `sameRawBits` là `true`. Với `NaN`, payload là ngoại lệ cần xử lý riêng; với Infinity, chuỗi vẫn nhận diện đúng vô cực nhưng không chứa bất kỳ fraction số thực nào.

## 10. `toHexString` khác với `toString` và định dạng thập phân

| Cách hiển thị | Ví dụ với `double 0.1` | Mục đích |
| --- | --- | --- |
| `Double.toString(x)` | `0.1` | Biểu diễn thập phân ngắn, dễ đọc, hỗ trợ round-trip theo quy tắc Java |
| `Double.toHexString(x)` | `0x1.999999999999ap-4` | Lộ rõ giá trị nhị phân được lưu dưới dạng significand hex và mũ 2 |
| `String.format("%.2f", x)` | `0.10` | Trình bày số thập phân theo precision/locale, có thể làm tròn chuỗi đầu ra |
| `Double.doubleToRawLongBits(x)` | `long` 64 bit | Lấy mẫu bit dấu, exponent, fraction; không phải định dạng số thực text |

Đây là những nhiệm vụ khác nhau. `Double.toString(0.1)` in `0.1` vì đó là biểu diễn thập phân ngắn phù hợp cho giá trị `double`, **không chứng minh giá trị đã lưu bằng đúng phân số thập phân 1/10**.

Nếu mục tiêu chỉ là báo cáo giá/đơn vị cho người đọc, chuỗi hex có thể khó hiểu và không phù hợp. Nếu mục tiêu là **kiểm tra sai số số học, hiệu ứng làm tròn, khoảng cách ULP, normal/subnormal** thì chuỗi hex giúp tránh mơ hồ về phần trị nhị phân.

`String.format("%a", x)` cũng là một cách trình bày hex floating-point trong hệ thống formatter của Java, nhưng khác về API, cấu hình locale và precision. Khi cần một cách in ngắn gọn, nhất quán, không phụ thuộc vào format string, `Double.toHexString`/`Float.toHexString` là lựa chọn trực tiếp.

## 11. Phân biệt `Double.toHexString` và raw IEEE 754 bits

Đây là **hai cách biểu diễn khác nhau** cho cùng giá trị `1.0`:

```java
System.out.println(Double.toHexString(1.0));
// 0x1.0p0

System.out.println(Long.toHexString(Double.doubleToRawLongBits(1.0)));
// 3ff0000000000000
```

Chuỗi đầu là **giá trị toán học biểu diễn bằng phần trị hex nhân lũy thừa 2**. Chuỗi sau là **64 bit dữ liệu IEEE 754 dưới dạng một số nguyên `long` biểu diễn hex**, gồm cả bit dấu, exponent đã bias và fraction trong các vị trí bit cố định.

Tương tự với `float`:

```java
System.out.println(Float.toHexString(1.0f));
// 0x1.0p0

System.out.println(Integer.toHexString(Float.floatToRawIntBits(1.0f)));
// 3f800000
```

Một số người mới học thấy `toHexString` rồi tưởng nó in raw bit pattern. Dấu hiệu nhận biết:

- **Dạng giá trị dấu phẩy động:** thường `0x1.xxxxxpE`, `0x0.xxxpE`, `-0x0.0p0`, `NaN` hoặc `Infinity`.
- **Dạng raw bit pattern:** dùng `Long.toHexString`/`Integer.toHexString` trên giá trị trả về của `doubleToRawLongBits`/`floatToRawIntBits`, là chuỗi hex **không có `p`**.

`Double.doubleToLongBits` (không có `Raw`) **chuẩn hóa NaN** thành mẫu canonical; nếu bài toán cần quan sát payload, dùng `Double.doubleToRawLongBits`. Tương tự với `Float.floatToIntBits` so với `Float.floatToRawIntBits`.

Và không được nhầm **raw bits** với **thứ tự byte (endianness)** trên mạng hoặc trong tệp. Muốn serialization byte-for-byte, bạn còn phải chọn byte order và bố cục trường dữ liệu; chỉ tạo chuỗi `0x1.0p0` không quyết định bộ bốn/tám byte được truyền ra ngoài.

## 12. Ví dụ Java 17+ độc lập và stdout đã kiểm chứng

Lưu thành tệp **`FloatingHexDemo.java`**:

```java
public class FloatingHexDemo {
    private static void showDouble(String name, double x) {
        System.out.println(name + "=" + Double.toHexString(x));
    }

    private static void showFloat(String name, float x) {
        System.out.println(name + "=" + Float.toHexString(x));
    }

    public static void main(String[] args) {
        showDouble("dZero", 0.0);
        showDouble("dNegativeZero", -0.0);
        showDouble("dOne", 1.0);
        showDouble("dHalf", 0.5);
        showDouble("dThree", 3.0);
        showDouble("dPointOne", 0.1);
        showDouble("dMinNormal", Double.MIN_NORMAL);
        showDouble("dMinSubnormal", Double.MIN_VALUE);
        showDouble("dMaxFinite", Double.MAX_VALUE);
        showDouble("dNextUpOne", Math.nextUp(1.0));
        showDouble("dPositiveInfinity", Double.POSITIVE_INFINITY);
        showDouble("dNegativeInfinity", Double.NEGATIVE_INFINITY);
        showDouble("dNaN", Double.NaN);

        showFloat("fOne", 1.0f);
        showFloat("fPointOne", 0.1f);
        showFloat("fMinNormal", Float.MIN_NORMAL);
        showFloat("fMinSubnormal", Float.MIN_VALUE);
        showFloat("fNextUpOne", Math.nextUp(1.0f));
        showFloat("fNegativeZero", -0.0f);

        System.out.println("parseHexDouble="
                + Double.parseDouble("0x1.8p1"));
        System.out.println("parseHexFloat="
                + Float.parseFloat("0x1.8p1"));
        System.out.println("negativeZeroEqualsPositiveZero="
                + (-0.0 == 0.0));
        System.out.println("negativeZeroRoundTripBits="
                + (Double.doubleToRawLongBits(-0.0)
                == Double.doubleToRawLongBits(
                        Double.parseDouble(Double.toHexString(-0.0)))));

        String textDouble = Double.toHexString(Math.PI);
        double parsedDouble = Double.parseDouble(textDouble);
        System.out.println("doubleRoundTripBits="
                + (Double.doubleToRawLongBits(Math.PI)
                == Double.doubleToRawLongBits(parsedDouble)));

        String textFloat = Float.toHexString(0.1f);
        float parsedFloat = Float.parseFloat(textFloat);
        System.out.println("floatRoundTripBits="
                + (Float.floatToRawIntBits(0.1f)
                == Float.floatToRawIntBits(parsedFloat)));

        System.out.println("floatOneRawBits="
                + Integer.toHexString(Float.floatToRawIntBits(1.0f)));
        System.out.println("doubleOneRawBits="
                + Long.toHexString(Double.doubleToRawLongBits(1.0)));

        double nanPayload = Double.longBitsToDouble(0x7ff8000000000001L);
        String nanText = Double.toHexString(nanPayload);
        System.out.println("nanText=" + nanText);
        System.out.println("nanPayloadLost="
                + (Double.doubleToRawLongBits(nanPayload)
                != Double.doubleToRawLongBits(Double.parseDouble(nanText))));
    }
}
```

Biên dịch/chạy tại thư mục chứa tệp:

```bash
javac --release 17 -encoding UTF-8 FloatingHexDemo.java
java FloatingHexDemo
```

Stdout chính xác:

```text
dZero=0x0.0p0
dNegativeZero=-0x0.0p0
dOne=0x1.0p0
dHalf=0x1.0p-1
dThree=0x1.8p1
dPointOne=0x1.999999999999ap-4
dMinNormal=0x1.0p-1022
dMinSubnormal=0x0.0000000000001p-1022
dMaxFinite=0x1.fffffffffffffp1023
dNextUpOne=0x1.0000000000001p0
dPositiveInfinity=Infinity
dNegativeInfinity=-Infinity
dNaN=NaN
fOne=0x1.0p0
fPointOne=0x1.99999ap-4
fMinNormal=0x1.0p-126
fMinSubnormal=0x0.000002p-126
fNextUpOne=0x1.000002p0
fNegativeZero=-0x0.0p0
parseHexDouble=3.0
parseHexFloat=3.0
negativeZeroEqualsPositiveZero=true
negativeZeroRoundTripBits=true
doubleRoundTripBits=true
floatRoundTripBits=true
floatOneRawBits=3f800000
doubleOneRawBits=3ff0000000000000
nanText=NaN
nanPayloadLost=true
```

Phân tích kết quả:

1. **Nhóm `dZero` đến `dThree`:** minh họa dấu, phần trị hex và số mũ nhị phân; số 3 được biểu diễn `0x1.8p1`.
2. **`dPointOne` và `fPointOne`:** cùng ý tưởng `0.1` nhưng hai kiểu lưu các bit fraction khác nhau; `float` ít chính xác hơn `double`.
3. **`dMinNormal`, `dMinSubnormal`, `fMinNormal`, `fMinSubnormal`:** ranh giới normal/subnormal và dạng `0x0....pE`.
4. **`dNextUpOne`, `fNextUpOne`:** quan sát một bước lưới floating-point của mỗi kiểu.
5. **`dPositiveInfinity`, `dNegativeInfinity`, `dNaN`:** các trường hợp đặc biệt không xuất dạng hex significand.
6. **`parseHexDouble`, `parseHexFloat`:** `parseDouble` và `parseFloat` nhận chuỗi có ký hiệu `p` và trả giá trị 3.
7. **`negativeZeroRoundTripBits`, `doubleRoundTripBits`, `floatRoundTripBits`:** xác minh parse ngược giữ mẫu bit của các giá trị hữu hạn được thử, gồm dấu âm của zero.
8. **`floatOneRawBits`, `doubleOneRawBits`:** minh họa raw IEEE bits **khác** `toHexString`.
9. **`nanPayloadLost=true`:** chuỗi `"NaN"` không mã hóa payload của mẫu NaN được tạo ra từ raw bits.

Chương trình dùng hàm `showDouble`/`showFloat` để tránh lặp lời gọi `println`. Chỉ có một `public class FloatingHexDemo`, tên trùng file, không cần thư viện ngoài Java 17.

## 13. Ứng dụng khi debug ULP, số kề và lỗi làm tròn

Giả sử một thuật toán dự kiến cho kết quả `1.0`, nhưng thực tế trả giá trị `double` ngay kế trên:

```java
double exact = 1.0;
double actual = Math.nextUp(exact);

System.out.println(Double.toHexString(exact));  // 0x1.0p0
System.out.println(Double.toHexString(actual)); // 0x1.0000000000001p0
System.out.println(Double.toHexString(Math.ulp(exact))); // 0x1.0p-52
```

Chỉ một chữ số phần trị hex cuối được thay đổi. Điều đó cho phép nhận diện cụ thể một bước gần `1.0`, thay vì ghi chung “sai số nhỏ”. Nhưng cần cẩn thận: tại **ranh giới lũy thừa 2**, khoảng cách phía trên và phía dưới có thể khác nhau. `Math.ulp(1.0)` bằng `2^-52`, trong khi `1.0-Math.nextDown(1.0)` bằng `2^-53`. Đừng coi mọi số `double` ở gần nhau là nằm trên lưới có bước cố định toàn cục.

Khi so sánh output của hai thuật toán, `toHexString` giúp thấy sự khác biệt **mẫu phần trị được biểu diễn**. Dù vậy, **khác vài chữ số hex không tự xác định thuật toán nào đúng hơn**: cần một đáp án tham chiếu, thang sai số (tuyệt đối/tương đối/ULP) và điều kiện bài toán.

Trong kiểm thử biên, có thể lưu cả output decimal lẫn hex để vừa dễ đọc vừa dễ phân tích:

```java
double result = Math.nextUp(1.0);
System.out.println("decimal=" + result);
System.out.println("hex=" + Double.toHexString(result));
```

## 14. Ứng dụng khi trao đổi dữ liệu

Nếu API trao đổi **chuỗi văn bản** được quy định rõ là cú pháp Java hex floating-point, có thể dùng:

```java
String payload = Double.toHexString(0.1);
double received = Double.parseDouble(payload);
```

Đối với **giá trị hữu hạn**, phép parse lại có thể giữ chính xác mẫu bit `double` của giá trị ban đầu. Hãy ghi rõ trong tài liệu API rằng đây là **chuỗi số thực hex với `p`**, không phải unsigned integer hex hoặc dữ liệu 8 byte nhị phân.

Tuy nhiên, cần cân nhắc:

- **Ngôn ngữ/bên nhận:** nhiều ngôn ngữ hỗ trợ dạng hex floating-point tương đương, nhưng không phải mọi thư viện JSON, CSV hoặc parser số đều chấp nhận `0x1.8p1`.
- **Các giá trị đặc biệt:** `NaN`, `Infinity`, `-Infinity` cần quy ước riêng, nhất là trong chuẩn trao đổi JSON vốn không coi chúng là number hợp lệ.
- **Giá trị NaN với payload:** không round-trip bit-exact qua `"NaN"`.
- **Tính chuẩn hóa đầu vào:** parser có thể chấp nhận những chuỗi tương đương; nếu cần canonical representation để băm/ký dữ liệu, hãy định nghĩa rõ format và kiểm tra chuỗi canonical chứ không chỉ parse thành công.
- **An toàn:** kiểm tra giới hạn độ dài, kiểu hợp lệ và miền nghiệp vụ trước khi tin input. Parser Java báo `NumberFormatException` đối với chuỗi sai cú pháp nhưng không biết giới hạn logic ứng dụng.

Nếu hợp đồng yêu cầu gửi **đúng 32/64 bit raw** của một giá trị IEEE 754, hãy dùng `floatToRawIntBits`/`doubleToRawLongBits`, định nghĩa rõ byte order, rồi mã hóa thành byte hoặc hex bit pattern 8/16 chữ số. Đó là **một định dạng khác** với `toHexString`.

## 15. Lỗi phổ biến và cách khắc phục

| Nhầm lẫn | Thực tế | Cách làm đúng |
| --- | --- | --- |
| `Double.toHexString(3.0)` trả `"3"` hoặc `"0x3"` | Trả `"0x1.8p1"` | Nhớ phần trị hex và mũ cơ số 2 |
| Ký tự `p` là lũy thừa 10 | `pE` nghĩa nhân `2^E` | Diễn giải theo cơ số 2 |
| `Float.toHexString` có độ chính xác như `Double.toHexString` | Float có 24 bit significand, double có 53 | Giữ đúng kiểu cần kiểm tra |
| Hàm xuất ra raw IEEE bit pattern | Hàm biểu diễn **giá trị số thực**, không phải bit fields | Dùng `floatToRawIntBits`/`doubleToRawLongBits` |
| `Double.MIN_VALUE` là số âm nhỏ nhất | Đây là positive subnormal nhỏ nhất | Phân biệt `MIN_NORMAL`, `MIN_VALUE`, `MAX_VALUE` |
| Mọi giá trị có dạng `0x1.xxxpE` | Subnormal có dạng `0x0.xxxpE` | Kiểm tra ranh giới subnormal |
| `-0.0` và `+0.0` phải có cùng chuỗi | `toHexString` giữ dấu `-0.0` | Test cả hai mẫu zero |
| `NaN` trong hex chứa toàn bộ payload | Chuỗi trả `"NaN"` chung | Dùng raw bits nếu cần bit-exact |
| `Integer.parseInt("0x1.8p1",16)` đọc được float hex | Không phải cú pháp số nguyên | Dùng `Double.parseDouble`/`Float.parseFloat` |
| `0.1` in dưới dạng decimal thì lưu chính xác 1/10 | `double` làm tròn nhị phân | Dùng hex output để quan sát, hoặc `BigDecimal` cho decimal exact |
| Hex floating-point tự nói lên endian trong bộ nhớ | Endian áp dụng cho thứ tự byte của serialization | Định nghĩa byte order riêng |
| Thấy hai chuỗi hex khác thì biết ngay sai số thuật toán | Hex chỉ cho thấy khác biệt biểu diễn | So với đáp án tham chiếu và ngưỡng lỗi |

## 16. Câu hỏi tự kiểm tra

1. **`Double.toHexString(3.0)` trả gì?** `"0x1.8p1"` vì `(1+8/16)×2=3`.
2. **`0x1.0p-1` bằng mấy?** `0.5`.
3. **Số mũ `p5` nghĩa nhân với `2^5` hay `16^5`?** Nhân `2^5`.
4. **Vì sao `Double.toHexString(0.1)` nhiều chữ số hex còn `Double.toString(0.1)` chỉ in `0.1`?** Chuỗi hex thể hiện chính xác significand/mũ nhị phân được lưu; chuỗi decimal ngắn tối ưu cách hiển thị, không ngụ ý lưu chính xác 1/10.
5. **`Double.MIN_VALUE` xuất thành dạng normal `0x1...pE` không?** Không: `0x0.0000000000001p-1022`, vì đó là subnormal dương nhỏ nhất.
6. **`Float.MIN_VALUE` xuất gì?** `0x0.000002p-126`.
7. **`Double.toHexString(-0.0)` khác `Double.toHexString(+0.0)` ở đâu?** Dấu âm, `-0x0.0p0` so với `0x0.0p0`.
8. **`Double.toHexString(Double.NaN)` có lưu được payload NaN không?** Không, chỉ `"NaN"`; phải dùng raw bits nếu cần payload.
9. **Parse ngược `0x1.8p1` dùng gì?** `Double.parseDouble("0x1.8p1")` hoặc `Float.parseFloat(...)`, không phải `Integer.parseInt`.
10. **Muốn in cả 64 bit của `double 1.0` phải gọi API nào?** `Long.toHexString(Double.doubleToRawLongBits(1.0))`; kết quả là `"3ff0000000000000"`, không phải `"0x1.0p0"`.
11. **Tại `1.0`, `Math.nextUp(1.0)` xuất chuỗi gì?** `0x1.0000000000001p0`: một bước biểu diễn phía trên.
12. **Dùng `toHexString` để gửi binary wire format có đủ không?** Không. Đây là chuỗi văn bản; wire format nhị phân phải định nghĩa byte order, độ rộng và cách xử lý giá trị đặc biệt riêng.

**Ghi nhớ:** `Double.toHexString` và `Float.toHexString` biểu diễn **giá trị dấu phẩy động nhị phân** dưới dạng `0xH.FpE`, trong đó **fraction viết bằng hex, số mũ là lũy thừa của 2**. Dạng này đặc biệt hữu ích để debug precision, ULP, normal/subnormal và signed zero. Muốn parse ngược thì dùng `Double.parseDouble`/`Float.parseFloat`; muốn giữ đúng raw IEEE bit pattern hoặc payload NaN thì dùng các API **RawBits**, không nhầm chúng với `toHexString`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-005">String.format, printf và Formatter</a>

<details>
<summary>Xem chi tiết</summary>

# String.format, printf và Formatter trong Java

Trong Java, biểu diễn số trong bộ nhớ và **trình bày số thành văn bản** là hai vấn đề khác nhau. Cùng một giá trị `12345.678` có thể được hiển thị dưới dạng `12,345.68`, `12.345,68` hoặc `1.235e+04` tùy **mẫu định dạng, Locale và mục đích sử dụng**.

Ba API liên quan chặt chẽ nhưng không đồng nhất:

- **`String.format(...)`**: định dạng các đối số thành **một `String` mới**, không tự in ra console.
- **`System.out.printf(...)`** (hoặc `PrintStream.printf`): **định dạng và ghi ngay** vào luồng đầu ra; trả lại `PrintStream` để có thể gọi nối tiếp.
- **`java.util.Formatter`**: đối tượng định dạng có trạng thái, ghi vào một `Appendable` như `StringBuilder`, một luồng hoặc đích được hỗ trợ. Phù hợp khi muốn tái sử dụng bộ định dạng hoặc kiểm soát nơi nhận văn bản.

Cả ba dựa trên **cú pháp format string của `Formatter`**, như `%d`, `%.2f`, `%,.2f`, `%e`. Việc định dạng **không làm thay đổi giá trị số nguồn**, nhưng chuỗi kết quả có thể đã làm tròn hoặc thay ký hiệu dấu thập phân theo Locale.

## 1. Ba API khác nhau ở hành động nào?

### `String.format` tạo chuỗi

```java
import java.util.Locale;

double amount = 1234.567;
String text = String.format(Locale.US, "%,.2f", amount);
System.out.println(text); // 1,234.57
```

`text` chứa chuỗi `"1,234.57"`. Biến `amount` vẫn mang giá trị số cũ. `String.format` **không tự in**; việc in là câu lệnh `System.out.println` riêng.

Có hai overload quen thuộc:

```java
String a = String.format("%.2f", 1.5); // dùng Locale mặc định của môi trường
String b = String.format(Locale.US, "%.2f", 1.5); // dùng Locale.US
```

Nếu kết quả phải có dạng nhất quán trong log máy, xuất dữ liệu hoặc kiểm thử, **truyền Locale rõ ràng** thay vì dựa vào Locale mặc định thay đổi theo máy.

### `printf` định dạng và in ra luồng

```java
System.out.printf(Locale.US, "Total: %.2f%n", 1234.5);
```

Kết quả hiển thị:

```text
Total: 1234.50
```

`printf` không “tạo một String để bạn tự dùng” như `String.format`; nó ghi vào `System.out`. Trong `PrintStream`, `printf` trả lại chính luồng đó. Khi gọi nhiều lần, cần xác định xuống dòng bằng `%n`, `println` hoặc cấu trúc thông điệp thích hợp; `printf` không mặc định tự thêm newline.

### `Formatter` ghi vào một đích và có thể dùng nhiều lượt

```java
import java.util.Formatter;
import java.util.Locale;

StringBuilder buffer = new StringBuilder();
try (Formatter formatter = new Formatter(buffer, Locale.US)) {
    formatter.format("Price: %.2f", 12.5);
    formatter.format(" | Qty: %d", 3);
}
System.out.println(buffer); // Price: 12.50 | Qty: 3
```

`Formatter` thực hiện nhiều lệnh `format` trên cùng đích `StringBuilder`. Sau khi đóng formatter, `StringBuilder` còn chứa nội dung đã ghi và có thể đọc được. Nên đóng đối tượng do bạn tạo nếu nó sở hữu tài nguyên; với formatter ghi vào stream/file, cần chú ý cách đóng có thể ảnh hưởng đích. **Không chủ động đóng `System.out`** chỉ vì muốn ngừng định dạng.

## 2. Cấu trúc một format specifier

Mẫu tổng quát cho một conversion dùng đối số:

```text
%[argument_index$][flags][width][.precision]conversion
```

Các phần là tùy chọn, nhưng `%` mở đầu và **conversion** xác định cách biểu diễn. Ví dụ:

```text
%1$+08d
││ │ │ └─ d: số nguyên thập phân
││ │ └── 8: độ rộng tối thiểu
││ └──── + và 0: luôn in dấu; đệm zero
│└─────── 1$: dùng đối số thứ nhất
└──────── %: bắt đầu specifier
```

Trong `"%1$+08d"`, đối số thứ nhất là một số nguyên; với `42`, kết quả là `+0000042` (8 ký tự).

Các thành phần:

| Thành phần | Mục đích | Ví dụ |
| --- | --- | --- |
| `argument_index$` | Chọn đối số bắt đầu từ 1 thay vì theo thứ tự mặc định | `%2$s` |
| `flags` | Quy định dấu, đệm, căn lề, grouping, dạng thay thế... | `+`, `0`, `-`, `,`, `(`, `#` |
| `width` | Độ rộng **tối thiểu**; không tự cắt chuỗi dài hơn | `%8d` |
| `.precision` | Ý nghĩa tùy conversion: số chữ số phần lẻ hoặc chữ số có nghĩa, giới hạn chuỗi... | `%.2f`, `%.4g` |
| `conversion` | Kiểu hiển thị mong muốn | `d`, `f`, `e`, `g`, `x`, `s` |

**Không phải mọi flag và precision đều hợp lệ với mọi conversion.** Ví dụ `%.2d` không phải cách hợp lệ để yêu cầu “hai chữ số phần lẻ của số nguyên” và có thể ném `IllegalFormatPrecisionException`.

## 3. Các conversion số nguyên thường dùng

| Conversion | Ý nghĩa | Ví dụ theo Locale.US |
| --- | --- | --- |
| `%d` | Số nguyên hệ 10 | `String.format("%d", 42)` → `"42"` |
| `%x` | Số nguyên hệ 16, chữ thường | `String.format("%x", 255)` → `"ff"` |
| `%X` | Số nguyên hệ 16, chữ hoa | `String.format("%X", 255)` → `"FF"` |
| `%o` | Số nguyên hệ 8 | `String.format("%o", 8)` → `"10"` |
| `%#x` | Dạng hệ 16 có tiền tố | `String.format("%#x", 255)` → `"0xff"` |
| `%,d` | Hệ 10 có grouping theo Locale | `String.format(Locale.US, "%,d", 1234567)` → `"1,234,567"` |
| `%+d` | Luôn in dấu `+` hoặc `-` | `42` → `"+42"` |
| `%(d` | Số âm trong ngoặc | `-42` → `"(42)"` |
| `%05d` | Độ rộng ít nhất 5, đệm số 0 | `-42` → `"-0042"` |

`%d` phù hợp cho các kiểu nguyên, bao gồm wrapper tương ứng và `BigInteger`. Không thể truyền một `double` vào `%d` rồi hy vọng formatter tự cắt phần thập phân:

```java
// String.format("%d", 3.5); // IllegalFormatConversionException
String correct = String.format(Locale.US, "%.0f", 3.5); // "4"
```

Đây là **làm tròn khi hiển thị dưới dạng số thực**, không phải cast `double` thành `int`. Với đối số âm hoặc số rất lớn, quy tắc định dạng và ép kiểu có thể khác nhau rõ rệt.

### Căn lề, width và zero-padding

```java
String right = String.format(Locale.US, "|%6d|", 42);
// "|    42|": căn phải trong ít nhất 6 ký tự

String left = String.format(Locale.US, "|%-6d|", 42);
// "|42    |": căn trái trong ít nhất 6 ký tự

String zeroFilled = String.format(Locale.US, "%+08d", 42);
// "+0000042": có dấu và đệm zero
```

`width` chỉ là **chiều rộng tối thiểu**, nên chuỗi đầu ra dài hơn width vẫn được in đầy đủ; formatter không tự cắt bớt phần số. Nếu format gây khó đọc hoặc dùng để truyền dữ liệu có độ dài cố định, phải kiểm tra độ dài sau định dạng riêng theo contract.

## 4. Các conversion cho số thực `float`/`double` và `BigDecimal`

| Conversion | Ý nghĩa | Mẫu điển hình |
| --- | --- | --- |
| `%f` | Dạng dấu phẩy động thập phân thường | `%.2f` |
| `%e` hoặc `%E` | Ký hiệu khoa học với số mũ | `%.3e` |
| `%g` hoặc `%G` | Dạng tổng quát: dùng định dạng thường hoặc khoa học tùy giá trị/precision | `%.4g` |
| `%a` hoặc `%A` | Dạng số thực **hexadecimal floating point** | `%a` với `double` |
| `%,.2f` | Phân nhóm và hai chữ số thập phân theo Locale | `%,.2f` |
| `%(,.2f` | Phân nhóm; số âm trong ngoặc | `%(,.2f` |

Ví dụ cụ thể:

```java
String fixed = String.format(Locale.US, "%.2f", 12.345);
// "12.35"

String science = String.format(Locale.US, "%.3e", 12345.6);
// "1.235e+04"

String general = String.format(Locale.US, "%.4g", 12345.6);
// "1.235e+04"

String hexadecimal = String.format(Locale.US, "%a", 3.5);
// "0x1.cp1"
```

**Precision không có cùng ý nghĩa giữa `%f` và `%g`**:

- `%.2f` yêu cầu **2 chữ số sau dấu thập phân** của biểu diễn.
- `%.4g` yêu cầu **4 chữ số có nghĩa** trong biểu diễn tổng quát; formatter chọn dạng thường/khoa học phù hợp.
- `%.3e` yêu cầu **3 chữ số sau dấu thập phân của mantissa** trong dạng khoa học.

Các giá trị số thực đặc biệt như `Double.NaN` và `Double.POSITIVE_INFINITY` có thể được in dưới dạng `NaN` và `Infinity`, tùy hoa/thường theo conversion. `-0.0` cũng có thể giữ dấu khi in:

```java
System.out.println(String.format(Locale.US, "%.2f", -0.0));
// -0.00
```

`BigDecimal` dùng được với nhiều conversion thập phân (`%f`, `%e`, `%g`), nhưng `%a` dành cho biểu diễn hex của các kiểu dấu phẩy động nhị phân và **không hỗ trợ `BigDecimal` như một chuyển đổi tương đương**.

## 5. Rounding khi định dạng không đổi giá trị nguồn

`String.format` và `printf` có thể **làm tròn phần văn bản được hiển thị** khi precision yêu cầu ít chữ số hơn, nhưng không sửa giá trị lưu trong biến.

```java
double value = 12.3456;
String display = String.format(Locale.US, "%.2f", value);

System.out.println(display); // 12.35
System.out.println(value);   // 12.3456
```

Với số thập phân cần chính xác (ví dụ những khoản tiền đã chốt), có thể tạo `BigDecimal` từ chuỗi:

```java
java.math.BigDecimal exact = new java.math.BigDecimal("2.345");
String display = String.format(Locale.US, "%.2f", exact);
// "2.35"
```

Không nên tự suy ra rằng dùng `%.2f` cho mọi phép tính tiền là đủ: formatter **chỉ tạo văn bản**; việc xác định thời điểm chốt khoản tiền, scale, rounding mode và số liệu được ghi nhận là trách nhiệm của **nghiệp vụ**.

`Math.round`, `BigDecimal.setScale` và `String.format` không phải ba cách thay thế tùy ý cho một yêu cầu. Một bước định dạng có thể làm tròn chỉ **để in** mà không thay đổi giá trị tính toán tiếp theo. Nếu hợp đồng yêu cầu biến số đã được làm tròn chính thức, cần thao tác kiểu số thích hợp rồi lưu kết quả đã chốt.

### Chú ý sai số nhị phân

`double` không thể biểu diễn chính xác mọi số thập phân. `%.2f` có thể hiển thị một giá trị trông “đẹp”, nhưng đó không phải chứng cứ rằng số thập phân nguồn được biểu diễn và tính toán chính xác đến hai chữ số.

Không nên dùng định dạng rồi `Double.parseDouble(...)` chuỗi đã in để “sửa sai số” trong mọi tình huống: việc ấy có thể tạo thêm một phép làm tròn không được yêu cầu, và nếu chuỗi định dạng phụ thuộc Locale thì việc parse bằng `Double.parseDouble` còn có thể thất bại.

## 6. `Locale` ảnh hưởng trực tiếp định dạng số

`Locale` quy định nhiều đặc điểm biểu diễn văn bản, trong đó có dấu thập phân và dấu phân nhóm chữ số. So sánh:

```java
String us = String.format(Locale.US, "%,.2f", 12345.67);
String germany = String.format(Locale.GERMANY, "%,.2f", 12345.67);

// us:      "12,345.67"
// germany: "12.345,67"
```

Các chuỗi khác nhau dù **giá trị `double` như nhau**. `Locale` làm thay đổi **văn bản**, không thay đổi số học của biểu thức trước khi truyền cho formatter.

Vì thế:

- **Giao diện người dùng:** chọn Locale phù hợp với người dùng hoặc quy tắc sản phẩm.
- **Giao thức, CSV có schema, log máy và golden tests:** chọn Locale cố định, ví dụ `Locale.US` hoặc `Locale.ROOT` tùy contract.
- **Đọc lại chuỗi:** không mặc định `Double.parseDouble` sẽ hiểu dấu phẩy thập phân và phân nhóm. `Double.parseDouble("12.345,67")` không phải cách parse phù hợp.

Không nên tự thay dấu phẩy thành dấu chấm một cách máy móc: một chuỗi như `"1,234"` có thể mang nghĩa **một nghìn hai trăm ba mươi bốn** hoặc **một phẩy hai ba bốn**, tùy Locale và hợp đồng. Khi cần parse chuỗi do người dùng nhập, dùng parser được cấu hình Locale và kiểm tra **toàn bộ đầu vào**.

### Locale mặc định có thể thay đổi

`String.format("%.2f", value)` và `System.out.printf("%.2f", value)` sử dụng Locale phù hợp với môi trường hoặc cấu hình hiện hành; đầu ra có thể khác theo nơi chạy. Tránh dựa vào kết quả mặc định trong test snapshot hoặc định dạng dữ liệu liên hệ thống.

## 7. Argument index, tái sử dụng và thứ tự đối số

Thông thường conversion lấy đối số theo thứ tự:

```java
String text = String.format(Locale.US, "%s=%d", "count", 7);
// "count=7"
```

`%n$` cho phép chọn đối số thứ `n` (chỉ số bắt đầu từ **1**):

```java
String reversed = String.format(Locale.US, "%2$s=%1$04d", 7, "n");
// "n=0007"
```

Flag `<` **tái sử dụng đối số được dùng bởi format specifier trước đó**:

```java
String bothBases = String.format(Locale.US, "%1$d / %<x", 31);
// "31 / 1f"
```

Rất dễ nhầm giữa **đối số thứ hai** và **chỉ số bắt đầu từ 0**. `%2$s` là đối số thứ hai, không phải đối số ở vị trí 2 trong mảng Java.

Đừng dùng cú pháp chỉ số và flag `<` khi nó khiến mẫu trở nên khó kiểm toán. Với code quan trọng, tên biến rõ ràng và format đơn giản thường dễ bảo trì hơn so với một chuỗi ghép nhiều chỉ số thủ công.

## 8. `%s`, `%%`, `%n` và ký tự đặc biệt

Các conversion ngoài số vẫn rất cần thiết:

| Conversion | Công dụng | Ví dụ |
| --- | --- | --- |
| `%s` | Biểu diễn đối tượng thành chuỗi | `String.format("%s", "cat")` → `"cat"` |
| `%.3s` | Giới hạn chuỗi tối đa 3 ký tự | `"abcdef"` → `"abc"` |
| `%b` | Biểu diễn boolean-like | `true` → `"true"`; tham chiếu `null` → `"false"` |
| `%c` | Ký tự | `'A'` → `"A"` |
| `%%` | In dấu phần trăm literal | `"Discount: 10%%"` → `"Discount: 10%"` |
| `%n` | Ký tự xuống dòng theo nền tảng | Khác cách cố định viết `\n` trong chuỗi |

Ví dụ:

```java
String percent = String.format(Locale.US, "Discount: %d%%", 10);
// "Discount: 10%"

String row = String.format(Locale.US, "A%nB");
// có một xuống dòng phụ thuộc nền tảng giữa A và B
```

**`%n` không dùng đối số.** Với nội dung văn bản nhiều dòng có yêu cầu byte-for-byte cố định qua mọi hệ điều hành, chọn quy ước xuống dòng rõ ràng thay vì mặc định `%n`.

Khi đưa dữ liệu có thể chứa ký tự xuống dòng hoặc định dạng không tin cậy vào log, cũng cần cân nhắc escape và policy logging để tránh log injection; formatter không tự làm sạch toàn bộ dữ liệu.

## 9. Lỗi format string chỉ xuất hiện lúc chạy

Java kiểm tra cú pháp kiểu của lời gọi `String.format`, nhưng không chứng minh tại thời điểm compile rằng chuỗi format và các đối số **luôn tương thích**.

Một số exception của `java.util.IllegalFormatException`:

| Sai sót | Ví dụ | Exception điển hình |
| --- | --- | --- |
| Conversion sai với kiểu đối số | `String.format("%d", 1.25)` | `IllegalFormatConversionException` |
| Thiếu đối số | `String.format("%d %d", 1)` | `MissingFormatArgumentException` |
| Precision không hợp lệ | `String.format("%.2d", 7)` | `IllegalFormatPrecisionException` |
| Conversion không được hỗ trợ | `String.format("%q", 7)` | `UnknownFormatConversionException` |
| Flag không hợp lệ cho conversion | `String.format("%#s", "a")` trong trường hợp đối số không phải `Formattable` | `FormatFlagsConversionMismatchException` |

Các ví dụ trên là những trường hợp **cố tình gây lỗi** để giải thích. Không chạy chúng ngoài cơ chế kiểm thử/bắt lỗi.

```java
try {
    String.format(Locale.US, "%d", 1.25);
} catch (java.util.IllegalFormatException ex) {
    System.out.println("invalid conversion");
}
```

Khi xây dựng API, có thể tránh tạo format string từ dữ liệu không đáng tin cậy. Tách rõ mẫu format do chương trình định nghĩa và **nội dung** người dùng cung cấp:

```java
String userText = "%n%n";
String safeAsArgument = String.format(Locale.US, "Input: %s", userText);
// "Input: %n%n" — userText là dữ liệu %s, không trở thành format string
```

Điều này không thay thế escaping theo ngữ cảnh đầu ra (HTML/SQL/log). Nó chỉ giảm rủi ro khi dữ liệu đầu vào bị diễn giải thành chuỗi format.

### Chú ý `null` và đối số có kiểu khác

`String.format("%s", (Object) null)` có thể hiển thị `"null"`, nhưng `String.format("%d", (Object) null)` hiển thị `"null"` theo quy tắc Formatter của Java cho null, không phải bằng chứng có một số nguyên 0. Nếu nghiệp vụ cấm thiếu dữ liệu, kiểm tra đầu vào trước khi định dạng.

**Không** dùng `%f` để tự động chuyển mọi `Integer` sang số thực. Với `String.format("%.2f", 7)`, đối số là `Integer`, và formatter có thể ném `IllegalFormatConversionException`. Muốn định dạng số thực, truyền `7.0`, `Double.valueOf(7)` hoặc `BigDecimal` phù hợp.

## 10. Thiết kế đầu ra ổn định và rõ nghĩa

Có thể phân loại theo mục tiêu:

| Mục tiêu | Gợi ý |
| --- | --- |
| Chuỗi ngắn để hiển thị | `String.format(locale, pattern, args)` |
| In trực tiếp console | `System.out.printf(locale, pattern, args)` |
| Nhiều lần append vào một bộ đệm | `Formatter` với `StringBuilder` |
| Phần trăm và căn cột | `%%`, `width`, flag `-` |
| Báo cáo số có ngăn cách nhóm | `%,d`, `%,.2f` theo Locale |
| Trao đổi dữ liệu máy-máy | Cố định Locale/cú pháp; tốt hơn dùng serializer đúng schema thay vì format tùy ý |
| Giá trị tiền thập phân chính xác | Tính toán bằng kiểu/phương pháp phù hợp (thường `BigDecimal`), format chỉ tại bước hiển thị |

Một formatter có thể làm tròn **chuỗi được in** nhưng không biến số đo chưa chính xác thành dữ liệu chính xác. Nếu ứng dụng cần lưu giá trị đã chốt theo hợp đồng, chốt bằng thuật toán số và chế độ làm tròn phù hợp, sau đó mới dùng formatter để trình bày.

Không tạo formatter mới nhiều lần trong vòng lặp nóng chỉ vì tiện cú pháp nếu hiệu năng quan trọng; đo lường và cân nhắc `StringBuilder` hay cách ghép chuỗi thích hợp. Dù vậy, ưu tiên đầu tiên vẫn là **đúng định dạng, đúng Locale và rõ hợp đồng**.

## 11. Chương trình Java 17+ chạy độc lập

Tạo file **`NumberFormattingDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.Formatter;
import java.util.IllegalFormatException;
import java.util.Locale;

public class NumberFormattingDemo {
    public static void main(String[] args) {
        double amount = 12345.678;
        String text = String.format(Locale.US, "%,.2f", amount);
        System.out.println("formatted=" + text);
        System.out.println("original=" + amount);

        System.out.println("integer=" +
                String.format(Locale.US, "%d", 42));
        System.out.println("hex=" +
                String.format(Locale.US, "%#x", 255));
        System.out.println("octal=" +
                String.format(Locale.US, "%o", 8));
        System.out.println("signed=" +
                String.format(Locale.US, "%+08d", 42));
        System.out.println("negativePadded=" +
                String.format(Locale.US, "%05d", -42));
        System.out.println("leftAligned=" +
                String.format(Locale.US, "|%-6d|", 42));
        System.out.println("groupInteger=" +
                String.format(Locale.US, "%,d", 1234567));

        System.out.println("scientific=" +
                String.format(Locale.US, "%.3e", 12345.6));
        System.out.println("general=" +
                String.format(Locale.US, "%.4g", 12345.6));
        System.out.println("hexFloat=" +
                String.format(Locale.US, "%a", 3.5));
        System.out.println("negativeParen=" +
                String.format(Locale.US, "%(,.2f", -1234.5));
        System.out.println("negativeZero=" +
                String.format(Locale.US, "%.2f", -0.0));
        System.out.println("specialNaN=" +
                String.format(Locale.US, "%f", Double.NaN));
        System.out.println("specialInfinity=" +
                String.format(Locale.US, "%f", Double.POSITIVE_INFINITY));

        System.out.println("bigDecimal=" +
                String.format(Locale.US, "%.2f",
                        new BigDecimal("2.345")));
        System.out.println("germany=" +
                String.format(Locale.GERMANY, "%,.2f", 12345.67));
        System.out.println("positional=" +
                String.format(Locale.US, "%2$s=%1$04d", 7, "n"));
        System.out.println("reused=" +
                String.format(Locale.US, "%1$d / %<x", 31));
        System.out.println("percent=" +
                String.format(Locale.US, "Discount: %d%%", 10));

        StringBuilder buffer = new StringBuilder();
        try (Formatter formatter = new Formatter(buffer, Locale.US)) {
            formatter.format("item=%s", "pen");
            formatter.format(" qty=%d price=%.2f",
                    3, new BigDecimal("1.25"));
        }
        System.out.println("formatter=" + buffer);

        try {
            String.format(Locale.US, "%d", 1.25);
            System.out.println("invalidFormat=missed");
        } catch (IllegalFormatException ex) {
            System.out.println("invalidFormat=detected");
        }

        System.out.printf(Locale.US, "printfValue=%.2f%n", 2.5);
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac NumberFormattingDemo.java
java NumberFormattingDemo
```

Output với `Locale` được chỉ định và đúng các đối số trên:

```text
formatted=12,345.68
original=12345.678
integer=42
hex=0xff
octal=10
signed=+0000042
negativePadded=-0042
leftAligned=|42    |
groupInteger=1,234,567
scientific=1.235e+04
general=1.235e+04
hexFloat=0x1.cp1
negativeParen=(1,234.50)
negativeZero=-0.00
specialNaN=NaN
specialInfinity=Infinity
bigDecimal=2.35
germany=12.345,67
positional=n=0007
reused=31 / 1f
percent=Discount: 10%
formatter=item=pen qty=3 price=1.25
invalidFormat=detected
printfValue=2.50
```

Để chương trình in thêm một dòng qua `printf`, nó dùng `%n` (xuống dòng của hệ điều hành). Phần văn bản nhìn thấy giống nhau trên các nền tảng phổ biến; **byte của ký tự xuống dòng** có thể khác theo môi trường.

## 12. Lỗi phổ biến và checklist

| Nhận định | Thực tế |
| --- | --- |
| “`String.format` tự in kết quả.” | Sai: nó trả `String`; `printf` ghi vào stream. |
| “`printf` luôn tự xuống dòng.” | Sai: mẫu phải có `%n` hoặc phải in newline riêng. |
| “`%.2f` giúp biến `double` chứa chính xác hai chữ số decimal.” | Sai: định dạng chỉ tạo văn bản; giá trị nguồn không đổi. |
| “`%,.2f` luôn dùng dấu phẩy làm grouping và dấu chấm thập phân.” | Sai: Locale ảnh hưởng cách hiển thị. |
| “`%.4g` có bốn chữ số sau dấu thập phân.” | Sai: `g` dùng precision chữ số có nghĩa. |
| “`%8d` sẽ cắt mọi số dài hơn 8 ký tự.” | Sai: width là **tối thiểu**. |
| “`%d` tự chuyển một `double` thành `int`.” | Sai: có thể ném `IllegalFormatConversionException`. |
| “`%n` giống mọi nơi với ký tự `\n`.” | Sai: `%n` dùng newline nền tảng. |
| “`%2$s` tham chiếu đối số index 2 theo Java zero-based.” | Sai: index bắt đầu từ 1. |
| “`Formatter` chỉ là tên khác của `String.format`.” | Không: nó là đối tượng định dạng với đích và vòng đời sử dụng riêng. |
| “Format string sai sẽ luôn bị compiler bắt.” | Không: nhiều lỗi chỉ ném `IllegalFormatException` lúc chạy. |
| “In `NaN` hoặc `Infinity` nghĩa là parser đầu vào đã sai.” | Không: đây là những giá trị `float`/`double` hợp lệ về biểu diễn; xử lý theo policy. |

Trước khi format số, hãy tự kiểm tra:

1. **Mục tiêu là trả chuỗi, in ra luồng hay ghi bộ đệm?** Chọn `String.format`, `printf` hoặc `Formatter`.
2. **Đối số thực tế thuộc kiểu nào?** Chọn `%d` cho nguyên, `%f`/`%e`/`%g` cho số thực phù hợp, đừng đoán formatter tự ép mọi kiểu.
3. **Độ chính xác có nghĩa gì?** `%.2f` là hai chữ số sau dấu thập phân; `%.4g` là bốn chữ số có nghĩa.
4. **Đầu ra phụ thuộc Locale không?** Cố định Locale trong test, log máy hoặc trao đổi dữ liệu.
5. **Format có thể lỗi lúc chạy không?** Kiểm tra specifier, flag, số đối số và chuyển đổi; test các mẫu quan trọng.
6. **Định dạng có đang bị lẫn với nghiệp vụ làm tròn không?** Không dùng chuỗi đã format thay giá trị tính toán nếu hợp đồng không yêu cầu.
7. **Dữ liệu đặc biệt và âm zero được xử lý thế nào?** Cân nhắc `NaN`, `Infinity`, `-0.0` và giao thức bên ngoài trước khi xuất.

**Ghi nhớ:** `String.format` **trả chuỗi**, `printf` **in ra stream**, còn `Formatter` **định dạng vào một đích cụ thể**. Chúng chia sẻ ngữ pháp `%...` nhưng bạn vẫn phải chọn đúng **conversion, precision, width, flags và Locale**; đồng thời tách việc **trình bày số** khỏi việc **tính toán và làm tròn theo nghiệp vụ**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-006">NumberFormat cho số thường, phần trăm và tiền tệ</a>

<details>
<summary>Xem chi tiết</summary>

# NumberFormat cho số thường, phần trăm và tiền tệ trong Java

## 1. Vì sao cần NumberFormat?

Một giá trị số có thể được **biểu diễn khác nhau tùy ngôn ngữ và quốc gia**, dù giá trị toán học không đổi. Ví dụ **12345.67** trong giao diện Mỹ thường là **12,345.67**, còn trong giao diện Đức là **12.345,67**. Số phần trăm, giá tiền còn thêm quy tắc về dấu %, ký hiệu tiền tệ, chữ số thập phân và khoảng trắng.

Trong Java, **java.text.NumberFormat** là API định dạng và phân tích (*format/parse*) số theo **Locale**. Đây là một lớp trừu tượng, thường lấy phiên bản cụ thể qua các phương thức factory.

~~~java
import java.text.NumberFormat;
import java.util.Locale;

NumberFormat number = NumberFormat.getNumberInstance(Locale.US);
String shown = number.format(12345.67); // "12,345.67"
~~~

Đoạn trên là mã minh họa đặt trong phương thức Java. API không nhằm thay đổi **giá trị** đầu vào mà chuyển giá trị sang **chuỗi hiển thị**; khi phân tích chuỗi đầu vào, phải dùng cùng quy ước Locale phù hợp.

Mục tiêu bài học:

- Phân biệt **getNumberInstance**, **getIntegerInstance**, **getPercentInstance** và **getCurrencyInstance**.
- Hiểu vai trò của **Locale**, chữ số thập phân, phân nhóm và số chữ số được hiển thị.
- Biết tỷ lệ **0.125** có thể hiển thị thành **12.5%**, không phải **0.125%**.
- Biết format tiền tệ là **định dạng**, không phải **chuyển đổi tỷ giá**.
- Làm tròn có chủ đích, phân biệt **giá trị lưu trữ** và **chuỗi đã làm tròn**.
- Parse chuỗi theo Locale, dùng **ParsePosition**, **DecimalFormat.setParseBigDecimal(true)** khi cần số thập phân chính xác.
- Nhận diện sai lầm trong ứng dụng nhiều luồng, tiền tệ, dữ liệu máy và dữ liệu không đáng tin.

**Phạm vi:** ví dụ sử dụng Java 17+, ưu tiên **Locale.US** để stdout dễ kiểm chứng. Với Locale khác, ký hiệu và khoảng trắng có thể khác theo dữ liệu địa phương hóa của JDK.

## 2. Các factory method quan trọng

| API | Loại chuỗi cần hiển thị | Ví dụ ở Locale.US |
| --- | --- | --- |
| **NumberFormat.getNumberInstance(locale)** | Số thường | **12,345.67** |
| **NumberFormat.getInstance(locale)** | Tương đương định dạng số thường | **12,345.67** |
| **NumberFormat.getIntegerInstance(locale)** | Số nguyên, format phần thập phân về số nguyên | **12,346** từ 12345.6 |
| **NumberFormat.getPercentInstance(locale)** | Phần trăm | **12%** từ 0.12 |
| **NumberFormat.getCurrencyInstance(locale)** | Tiền tệ | **$1,234.56** từ 1234.56 |

Các factory trả về **NumberFormat**, một API bao quát chung, không nhất thiết trả đối tượng của đúng một subclass trên mọi JDK/provider. Trên các JDK phổ biến, những dạng số thường, phần trăm, tiền tệ thường là **DecimalFormat**; nếu cần API riêng của DecimalFormat, hãy kiểm tra **instanceof** trước khi ép kiểu.

Để hiển thị gọn số lớn, Java 12+ còn có **getCompactNumberInstance(locale, style)**, nhưng đây là chức năng bổ sung, không thay thế các ba dạng định dạng chính của bài.

### Không truyền Locale thì dùng Locale nào?

~~~java
NumberFormat implicit = NumberFormat.getNumberInstance();
NumberFormat explicit = NumberFormat.getNumberInstance(Locale.US);
~~~

Biến implicit dùng **Locale mặc định thuộc Locale.Category.FORMAT** của môi trường chạy. Locale này có thể phụ thuộc máy chủ, JVM, container hoặc cấu hình ứng dụng; vì vậy stdout và UI có thể khác nhau giữa các môi trường.

Trong sản phẩm đa ngôn ngữ, nên **truyền Locale đã xác định** theo lựa chọn người dùng hoặc cấu hình giao diện. Không giả định JVM của mọi máy đều có Locale.US, và không đặt một Locale mặc định toàn tiến trình chỉ để xử lý một request.

## 3. Format số thường

~~~java
NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
System.out.println(f.format(12345.67)); // "12,345.67"
~~~

Với Locale.US, dấu phẩy thường chia nhóm hàng nghìn, dấu chấm đánh dấu phần thập phân. Với Locale.GERMANY, hai vai trò đảo ngược: **12.345,67**.

Các phương thức điều chỉnh:

| API | Chức năng |
| --- | --- |
| **setGroupingUsed(true/false)** | Bật/tắt phân nhóm chữ số, không thay đổi số |
| **setMinimumIntegerDigits(n)** | Số chữ số nguyên tối thiểu, có thể thêm zero đầu |
| **setMaximumIntegerDigits(n)** | Số chữ số nguyên tối đa trong chuỗi format; phải hiểu nguy cơ cắt phần đầu |
| **setMinimumFractionDigits(n)** | Số chữ số thập phân hiển thị tối thiểu, thêm zero nếu cần |
| **setMaximumFractionDigits(n)** | Số chữ số thập phân hiển thị tối đa, có thể làm tròn |
| **setRoundingMode(mode)** | Quy tắc làm tròn khi cần bỏ bớt chữ số |

Ví dụ điều khiển hiển thị:

~~~java
NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
f.setGroupingUsed(true);
f.setMinimumFractionDigits(2);
f.setMaximumFractionDigits(2);
f.setRoundingMode(java.math.RoundingMode.HALF_UP);

System.out.println(f.format(12345.678)); // "12,345.68"
System.out.println(f.format(12));        // "12.00"
~~~

**setMinimumFractionDigits(2)** buộc luôn có ít nhất hai chữ số sau dấu thập phân. **setMaximumFractionDigits(2)** giới hạn tối đa hai chữ số; khi giá trị có chữ số tiếp theo, việc format có thể làm tròn theo RoundingMode. Bản thân giá trị **12345.678** truyền vào không bị sửa.

### Thứ tự thiết lập min/max

Giá trị minimum fraction digits không được lớn hơn maximum fraction digits. Trong các subclass chuẩn, nếu đặt min vượt max hiện hành, maximum có thể được tự điều chỉnh tương ứng; tương tự khi đặt max nhỏ hơn min. Do đó nên đặt **cả hai** theo cấu hình rõ ràng và kiểm tra getMinimumFractionDigits/getMaximumFractionDigits nếu tái sử dụng một formatter được truyền từ nơi khác.

**setGroupingUsed(false)** chủ yếu phù hợp với hiển thị muốn bỏ nhóm chữ số, nhưng chưa đủ để đảm bảo cú pháp dữ liệu máy độc lập Locale: dấu thập phân vẫn theo Locale.

## 4. Số nguyên: getIntegerInstance không có nghĩa đầu vào là int

~~~java
NumberFormat f = NumberFormat.getIntegerInstance(Locale.US);
String result = f.format(12345.6); // "12,346"
~~~

Formatter cho số nguyên **làm tròn biểu diễn đầu ra** theo quy tắc được cấu hình. Theo Java 17, factory integer mặc định dùng **RoundingMode.HALF_EVEN** khi format số thực; vì vậy đừng giả sử luôn cắt phần thập phân về 0.

Quan trọng:

- **getIntegerInstance** nhận cả double/long và các Number phù hợp; đây là **phong cách định dạng**, không phải phép kiểm tra kiểu dữ liệu đầu vào.
- Khi **parse**, formatter integer được cấu hình để chỉ phân tích phần nguyên (*parseIntegerOnly*). Đừng dùng nó để parse đầy đủ một chuỗi tiền hoặc số thập phân mà không kiểm tra.
- Nếu mục tiêu là ép kiểu số học, dùng chuyển đổi có kiểm tra giới hạn, không suy ra kiểu dữ liệu mới chỉ từ chuỗi đã format.

Đối với các trường hợp phải kiểm soát cách làm tròn số nguyên như RoundingMode.DOWN, UP, HALF_EVEN hoặc HALF_UP, chọn quy tắc theo yêu cầu nghiệp vụ và gọi **setRoundingMode** khi subclass hỗ trợ.

## 5. Percent: phần trăm nhân giá trị với 100 khi hiển thị

**getPercentInstance(locale)** thể hiện một tỷ lệ dưới dạng phần trăm. Giá trị đầu vào thường ở dạng **0..1** khi nói tới tỷ lệ từ 0% đến 100%:

| Giá trị số | Chuỗi phần trăm kỳ vọng ở Locale.US |
| ---: | --- |
| 0 | **0%** |
| 0.12 | **12%** |
| 0.5 | **50%** |
| 1 | **100%** |
| 1.25 | **125%** |

Nó không giới hạn giá trị đầu vào vào đoạn [0,1]. Tỷ lệ tăng trưởng 125% có thể hợp lệ theo bài toán; tỷ lệ âm cũng có thể biểu diễn được.

~~~java
NumberFormat pct = NumberFormat.getPercentInstance(Locale.US);
pct.setMinimumFractionDigits(1);
pct.setMaximumFractionDigits(1);
pct.setRoundingMode(java.math.RoundingMode.HALF_UP);

System.out.println(pct.format(0.1234)); // "12.3%"
System.out.println(pct.format(1.0));    // "100.0%"
~~~

**Bẫy phổ biến:** nếu dữ liệu đã lưu **12.5** với ý nghĩa **12.5%**, gọi **format(12.5)** sẽ hiển thị **1250%**. Cần chuyển về tỷ lệ **0.125** trước hoặc định nghĩa lại hợp đồng dữ liệu.

Cách xử lý đúng cần bắt đầu từ **nghĩa của trường dữ liệu**:

- Hợp đồng **fraction/rate**: 0.125 biểu thị 12.5%, dùng percent formatter trực tiếp.
- Hợp đồng **percentage points**: 12.5 biểu thị 12.5%; muốn dùng percent formatter phải chuyển thành 0.125 bằng phép chia 100, hoặc định dạng theo một cách rõ ràng khác.

### Làm tròn tỷ lệ

Nếu maximum fraction digits bằng 1, giá trị **0.12345** có thể hiển thị **12.3%** dù giá trị chính xác tương ứng là 12.345%. Chuỗi hiển thị không còn chứa mọi chữ số ban đầu. Không nên lưu lại tỷ lệ bằng cách parse chuỗi đã được làm tròn và coi đó là dữ liệu gốc.

### Parse chuỗi phần trăm

Một percent formatter thường phân tích **"12.5%"** thành giá trị tỷ lệ **0.125** theo quy tắc Locale tương ứng. Cần phân biệt thao tác này với parse số thường **"12.5"**. Cũng phải kiểm tra dấu %, khoảng trắng và cách ghi số được Locale sử dụng; không suy rằng mọi quốc gia cùng ghi ký hiệu % ở cùng vị trí.

## 6. Currency: định dạng tiền tệ không phải đổi ngoại tệ

~~~java
NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);
System.out.println(usd.format(1234.56)); // "$1,234.56"
~~~

Formatter tiền tệ sử dụng cách đặt ký hiệu, dấu nhóm, dấu thập phân và số chữ số theo thông tin của Locale/currency formatter.

Với Locale.US, tiền tệ mặc định thường là **USD** và hiển thị ký hiệu **$**. Ở Locale khác, ký hiệu có thể đứng **sau số**, cách số bằng dấu cách không ngắt dòng, hoặc dùng dạng tiền tệ khác theo cấu hình địa phương hóa. Không nối thủ công **"$" + formattedNumber** và giả sử đúng với mọi quốc gia.

### Rounding và số chữ số tiền tệ

Mỗi tiền tệ có thể có số chữ số thập phân thông dụng khác nhau (ví dụ USD thường 2; JPY thường 0). **Currency.getDefaultFractionDigits()** cung cấp thông tin theo đơn vị tiền tệ; tuy nhiên nó **không phải toàn bộ quy tắc làm tròn của giao dịch**. Thanh toán, thuế, tiền mặt, quy định pháp lý có thể có các quy tắc riêng.

Ví dụ khi muốn xuất USD với 2 chữ số, dùng:

~~~java
NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
money.setMinimumFractionDigits(2);
money.setMaximumFractionDigits(2);
money.setRoundingMode(java.math.RoundingMode.HALF_UP);

String shown = money.format(new java.math.BigDecimal("1234.567"));
// "$1,234.57"
~~~

**Không dùng chuỗi đã format làm nơi lưu giá trị tiền.** Trong ví dụ, giá trị BigDecimal vẫn là 1234.567, nhưng chuỗi hiển thị chỉ có 2 chữ số sau dấu thập phân.

### Currency.getInstance và setCurrency

~~~java
java.util.Currency euro = java.util.Currency.getInstance("EUR");
NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.US);
formatter.setCurrency(euro);
~~~

Lệnh **setCurrency(euro)** thay thông tin tiền tệ được dùng bởi formatter, **không thực hiện quy đổi tỷ giá từ USD sang EUR**. Giá trị 100 vẫn là 100 theo dữ liệu được truyền. Muốn quy đổi phải có tỷ giá, thời điểm, chính sách làm tròn và nghiệp vụ tương ứng.

**Lưu ý API quan trọng:** **NumberFormat.setCurrency(...) không tự cập nhật minimum/maximum fraction digits**. Nếu đổi Currency của formatter đã tạo, cần xem xét và thiết lập lại số chữ số khi cần, thay vì mong nó tự đổi theo đồng tiền mới.

### Mã ISO và ký hiệu không đồng nhất

**Currency.getCurrencyCode()** trả mã như **USD**, **EUR** hoặc **VND**. Ký hiệu **$** có thể mang nghĩa khác nhau ở nhiều quốc gia. Khi dữ liệu lưu chuyển qua dịch vụ/API, hãy truyền **mã tiền tệ ISO** như một trường riêng, không dùng duy nhất ký hiệu được hiển thị cho người dùng.

## 7. Locale chi phối ngữ nghĩa hiển thị và parse

`NumberFormat.getNumberInstance(Locale.US)` thường dùng dấu phẩy nhóm và dấu chấm thập phân; `Locale.GERMANY` thường dùng dấu chấm nhóm và dấu phẩy thập phân. Không sử dụng `Locale.getDefault()` cho dữ liệu có hợp đồng cố định hoặc test cần lặp lại. Với đầu vào có dấu phân cách theo vùng, `Double.parseDouble` không tự hiểu locale. Cách kiểm chứng dấu, ký tự số, dấu âm và grouping chuyên sâu nằm ở bài `Locale, dấu thập phân, nhóm chữ số và dấu âm`.

## 8. Chữ số và làm tròn khi format

`setMinimumFractionDigits`/`setMaximumFractionDigits`, `setMinimumIntegerDigits`, `setGroupingUsed` và `setRoundingMode` thay đổi cách in **giá trị**, không sửa số gốc. Ví dụ `getNumberInstance(Locale.US)` được đặt tối đa hai chữ số phần lẻ có thể in `12.35` từ nguồn `12.345` theo mode phù hợp, nhưng biến nguồn vẫn `12.345`. Số chữ số hiển thị khác độ chính xác nhị phân của `double` và khác scale của `BigDecimal`. Hai bài về giới hạn chữ số và `RoundingMode` trình bày chi tiết chính sách này.

## 9. Parse qua NumberFormat cần hợp đồng dữ liệu

`NumberFormat.parse(String)` có thể tiêu thụ một **tiền tố hợp lệ** và bỏ phần đuôi mà không ném `ParseException`. Khi một trường nhập chỉ chứa số, dùng `parse(text, new ParsePosition(0))` và kiểm tra `result != null` cùng `position.getIndex() == text.length()`; kiểm tra này chưa bảo đảm grouping luôn đúng yêu cầu nghiêm ngặt.

```java
java.text.ParsePosition pos = new java.text.ParsePosition(0);
java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
String input = "123abc";
Number n = nf.parse(input, pos);
System.out.println(n != null && pos.getIndex() == input.length()); // false
```

Bài `ParsePosition và kiểm tra toàn bộ chuỗi` đi sâu về vị trí lỗi, chỉ số UTF-16 và validation chặt.

## 10. Khi cần decimal chính xác

`NumberFormat.parse` trả về `Number`, có thể là `Long`, `Double` hoặc kiểu khác theo cấu hình. Khi cần parse trực tiếp thành `BigDecimal` hữu hạn từ chuỗi localized, `DecimalFormat.setParseBigDecimal(true)` là một lựa chọn; cần kiểm tra actual subtype vì token NaN/Infinity có thể trả `Double`. Không chuyển qua `doubleValue()` trước khi tạo decimal cần chính xác. Bài `setParseBigDecimal và giới hạn an toàn khi dùng formatter đồng thời` bàn kỹ về loại trả về, -0.0 và an toàn giữa các thread.

## 11. Chương trình Java 17+ có thể biên dịch/chạy

Tạo tệp **NumberFormatDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class NumberFormatDemo {
    static BigDecimal parseWhole(String text, DecimalFormat format) {
        ParsePosition pos = new ParsePosition(0);
        Number parsed = format.parse(text, pos);
        if (!(parsed instanceof BigDecimal)
                || pos.getIndex() != text.length()) {
            throw new IllegalArgumentException("Invalid localized number");
        }
        return (BigDecimal) parsed;
    }

    public static void main(String[] args) {
        NumberFormat number = NumberFormat.getNumberInstance(Locale.US);
        number.setGroupingUsed(true);
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        number.setRoundingMode(RoundingMode.HALF_UP);
        System.out.println("number: " + number.format(12345.678));

        NumberFormat noGrouping = NumberFormat.getNumberInstance(Locale.US);
        noGrouping.setGroupingUsed(false);
        noGrouping.setMaximumFractionDigits(0);
        System.out.println("no grouping: " + noGrouping.format(12345));

        NumberFormat integer = NumberFormat.getIntegerInstance(Locale.US);
        System.out.println("integer: " + integer.format(12345.6));

        NumberFormat percent = NumberFormat.getPercentInstance(Locale.US);
        percent.setMinimumFractionDigits(1);
        percent.setMaximumFractionDigits(1);
        percent.setRoundingMode(RoundingMode.HALF_UP);
        System.out.println("percent: " + percent.format(0.1234));
        System.out.println("percent one: " + percent.format(1.0));

        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
        money.setRoundingMode(RoundingMode.HALF_UP);
        System.out.println("currency: "
                + money.format(new BigDecimal("1234.567")));
        System.out.println("currency code: "
                + money.getCurrency().getCurrencyCode());

        NumberFormat candidate = NumberFormat.getNumberInstance(Locale.US);
        if (!(candidate instanceof DecimalFormat)) {
            throw new IllegalStateException("DecimalFormat is required");
        }
        DecimalFormat parser = (DecimalFormat) candidate;
        parser.setParseBigDecimal(true);

        BigDecimal parsed = parseWhole("1,234.50", parser);
        System.out.println("parsed exact: " + parsed.toPlainString());
        try {
            parseWhole("1,234.50 xyz", parser);
        } catch (IllegalArgumentException e) {
            System.out.println("trailing text: rejected");
        }

        BigDecimal precise = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("decimal sum: " + number.format(precise));

        NumberFormat german = NumberFormat.getNumberInstance(Locale.GERMANY);
        german.setMinimumFractionDigits(2);
        german.setMaximumFractionDigits(2);
        System.out.println("de number: " + german.format(12345.67));
    }
}
~~~

Chạy trên JDK 17 trở lên:

~~~bash
javac --release 17 NumberFormatDemo.java
java NumberFormatDemo
~~~

Stdout đã kiểm tra trên JDK với các Locale nêu trên:

~~~text
number: 12,345.68
no grouping: 12345
integer: 12,346
percent: 12.3%
percent one: 100.0%
currency: $1,234.57
currency code: USD
parsed exact: 1234.50
trailing text: rejected
decimal sum: 0.30
de number: 12.345,67
~~~

Các phần cần lý giải:

- **number**: phân nhóm hàng nghìn, giữ chính xác hai chữ số khi hiển thị và làm tròn HALF_UP từ 12345.678.
- **integer**: factory integer tạo chuỗi không có phần thập phân, nhưng nhận một double đầu vào.
- **percent**: tỷ lệ 0.1234 được nhân theo ngữ nghĩa phần trăm rồi hiển thị 12.3%.
- **currency**: bản thân BigDecimal("1234.567") không bị thay đổi; formatter xuất hai chữ số cho USD.
- **parsed exact**: DecimalFormat trả lại BigDecimal giữ 1234.50 (bao gồm scale phù hợp với chuỗi được parse trong trường hợp này).
- **trailing text**: parse có thể dừng trước ký tự cuối, và parseWhole chủ động từ chối vì không tiêu thụ hết chuỗi.
- **decimal sum**: BigDecimal("0.1") + BigDecimal("0.2") cho 0.3 chính xác; cấu hình number với minimum 2 chữ số xuất 0.30.
- **de number**: dấu nhóm và dấu thập phân được đảo vị trí so với Locale.US.

Với Locale đặc thù hoặc provider tùy biến, chính tả và khoảng trắng có thể khác. Khi viết bài kiểm thử thực tế, cố định Locale và hiểu dữ liệu địa phương hóa của runtime.

## 12. NumberFormat và tiền chính xác: trách nhiệm tách biệt

Một hệ thống tài chính thường cần phân biệt:

1. **Giá trị nghiệp vụ**: số thập phân chính xác, đơn vị tiền tệ, scale/precision, cách quy đổi khi có.
2. **Tính toán và làm tròn theo luật nghiệp vụ**: ví dụ làm tròn giá đơn vị, thuế, tổng hóa đơn theo thứ tự cụ thể.
3. **Hiển thị**: dùng NumberFormat/DecimalFormat phù hợp Locale để tạo chuỗi người dùng đọc được.
4. **Phân tích đầu vào từ giao diện**: parse theo Locale và kiểm tra có đúng đơn vị tiền tệ, miền và quy tắc đã đặt hay không.

Nếu tính toán bằng double, số thập phân thường không biểu diễn chính xác hoàn toàn; định dạng ra chuỗi đẹp không sửa sai số đã phát sinh. Với tiền, có thể dùng **BigDecimal từ chuỗi** và **RoundingMode** theo hợp đồng, rồi NumberFormat chỉ đảm nhận hiển thị.

Đừng nhầm hai thao tác:

- **format(new BigDecimal("1.235"))** thành **"1.24"** theo HALF_UP với hai chữ số: **làm tròn ở khâu trình bày**.
- **value.setScale(2, HALF_UP)**: tạo **BigDecimal giá trị mới** làm tròn theo quy tắc nghiệp vụ để sử dụng trong tính toán/ghi dữ liệu.

Hai thao tác có thể cho cùng số hiện trên màn hình, nhưng thời điểm áp dụng quy tắc trong hệ thống có thể rất khác. Mọi dữ liệu phải thỏa chính sách tiền tệ trước khi ghi sổ.

## 13. Formatter là đối tượng mutable

`NumberFormat` và `DecimalFormat` có nhiều setter, nên không chia sẻ một instance mutable giữa các request đồng thời nếu không đồng bộ phù hợp. Dùng instance cục bộ, cơ chế riêng theo thread hoặc đồng bộ được thiết kế rõ. Bài `setParseBigDecimal và giới hạn an toàn khi dùng formatter đồng thời` nêu mã và đánh đổi của từng cách.

## 14. Định dạng hiển thị khác định dạng truyền dữ liệu

Chuỗi in theo locale như `1,234.56` hoặc `1.234,56` phù hợp UI, nhưng một giao thức machine-to-machine phải quy định cú pháp và kiểu số tường minh. Đừng tự dùng `NumberFormat` để tạo JSON number/CSV canonical. Bài `Không dùng chuỗi đã định dạng cho người đọc làm dạng trao đổi dữ liệu mặc định` giải thích đầy đủ về schema và serializer.

## 15. Những lỗi thường gặp

| Sai lầm | Vì sao sai | Hướng khắc phục |
| --- | --- | --- |
| Dùng NumberFormat không có Locale cố định cho snapshot test | Máy chạy có Locale mặc định khác | Truyền Locale tường minh |
| Dùng getNumberInstance để hiển thị phần trăm | Không tự nhân tỷ lệ với 100/không thêm % | Dùng getPercentInstance nếu dữ liệu là tỷ lệ |
| Truyền 12.5 vào percent formatter khi muốn 12.5% | Sẽ thành 1250% | Đưa vào 0.125 hoặc thống nhất hợp đồng dữ liệu |
| Ghép ký hiệu "$" với số đã format | Sai vị trí/ký hiệu ở nhiều quốc gia | Dùng getCurrencyInstance(locale) |
| Gọi setCurrency và nghĩ đã chuyển tỷ giá | Nó chỉ thay metadata/biểu diễn | Quy đổi tiền trong nghiệp vụ riêng |
| Đổi Currency rồi nghĩ fraction digits tự đổi | setCurrency không cập nhật min/max fraction digits | Thiết lập lại số chữ số thích hợp |
| Mặc định rằng format không làm tròn | Giới hạn chữ số có thể làm tròn | Chọn RoundingMode và max digits rõ ràng |
| Dùng double để tính tiền chính xác thập phân | Sai số nhị phân có thể tích lũy | BigDecimal + quy tắc làm tròn |
| Parse chuỗi rồi bỏ qua phần còn lại | "123abc" có thể được parse một phần | Kiểm tra ParsePosition.getIndex() |
| Tin rằng ParsePosition đủ để xác thực mọi cú pháp | Parser có thể lenient với grouping | Bổ sung kiểm tra business grammar |
| Ép kết quả NumberFormat.parse sang BigDecimal | Kết quả có thể là Long/Double | DecimalFormat.setParseBigDecimal hoặc parser riêng |
| Chia sẻ một NumberFormat mutable cho mọi thread | Không bảo đảm thread safety | Một instance mỗi thread/request |
| Chuyển số thành chuỗi UI rồi lưu làm số gốc | Mất chữ số/scale và phụ thuộc Locale | Lưu dữ liệu số theo mô hình miền |
| Thay dấu chấm và dấu phẩy bằng String.replace | Có thể đảo nhầm decimal/grouping separator | Parse bằng Locale đúng |

## 16. Bài tập tự kiểm tra

**Bài 1:** **getPercentInstance(Locale.US).format(0.25)** có ý nghĩa gì? Nếu nhập 25 thì sao?

*Đáp án:* 0.25 biểu diễn 25%. Nhập 25 là 2500%, vì formatter coi tham số là tỷ lệ gốc và nhân với 100 ở bước định dạng.

**Bài 2:** **getNumberInstance(Locale.US)** và **getNumberInstance(Locale.GERMANY)** khác điều gì khi format 12345.67?

*Đáp án:* US dùng **12,345.67**, Đức thường dùng **12.345,67**. Cùng giá trị số nhưng khác cách trình bày.

**Bài 3:** Một giá trị BigDecimal("1234.567") được format USD thành "$1,234.57". BigDecimal ban đầu thay đổi không?

*Đáp án:* Không. Format tạo **chuỗi** hiển thị đã làm tròn; BigDecimal bất biến vẫn biểu diễn 1234.567.

**Bài 4:** Vì sao gọi setCurrency(EUR) trên currency formatter đang biểu diễn USD không có nghĩa đã đổi 100 USD thành EUR?

*Đáp án:* API chỉ đổi tiền tệ dùng cho nhãn/định dạng; nó không có tỷ giá hoặc phép tính quy đổi.

**Bài 5:** Tại sao NumberFormat.parse("1,234.50 extra") không đủ cho yêu cầu xác thực toàn bộ chuỗi?

*Đáp án:* Parser có thể dừng ở cuối phần số và bỏ phần dư. Dùng **ParsePosition** để kiểm tra đã đọc toàn bộ, sau đó vẫn kiểm tra cú pháp/miền theo nghiệp vụ.

**Bài 6:** Khi cần parse chữ số thập phân thành BigDecimal, dùng phương thức nào?

*Đáp án:* Trên DecimalFormat, gọi **setParseBigDecimal(true)**; kiểm tra kết quả parse và toàn bộ input. Không thể giả định mọi NumberFormat trả DecimalFormat hay parse mặc định luôn trả BigDecimal.

**Bài 7:** Trong server nhiều luồng, tại sao không nên sửa cấu hình một NumberFormat static mỗi request?

*Đáp án:* Formatter mutable và nhìn chung không đồng bộ hóa; các luồng có thể quan sát trạng thái đang bị sửa. Sử dụng instance riêng theo luồng/request.

**Bài 8:** Muốn ghi số vào JSON và CSV để máy khác đọc, có nên dùng NumberFormat theo locale người dùng làm cấu trúc truyền dữ liệu không?

*Đáp án:* Không nên. Dùng kiểu/chuỗi chuẩn do giao thức quy định và serializer phù hợp; NumberFormat dành cho trình bày theo Locale.

## 17. Tài liệu gốc

- [NumberFormat Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/NumberFormat.html): factory theo Locale, NumberFormat, percent, currency, parse, rounding và đồng bộ hóa.
- [DecimalFormat Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormat.html): ký hiệu định dạng, DecimalFormatSymbols, setParseBigDecimal, parsing.
- [ParsePosition Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/ParsePosition.html): vị trí parse, kiểm tra phần văn bản còn lại.
- [Currency Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Currency.html): mã tiền tệ và default fraction digits.
- [RoundingMode Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html) và [BigDecimal Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): cách làm tròn thập phân và mô hình số thập phân chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-007">DecimalFormat, pattern và DecimalFormatSymbols</a>

<details>
<summary>Xem chi tiết</summary>

# DecimalFormat, pattern và DecimalFormatSymbols trong Java

## 1. Mục tiêu: định dạng một giá trị số không đồng nghĩa thay đổi giá trị đó

Trong Java Core — Numbers, một giá trị số có thể phải xuất hiện khác nhau tùy quốc gia, hệ thống hoặc yêu cầu giao diện:

```text
Cùng giá trị số: 1234567.5

Theo quy ước Hoa Kỳ:     1,234,567.50
Theo quy ước Đức:       1.234.567,50
Định dạng đặc biệt:     1^234^567|50
```

`java.text.DecimalFormat` thực hiện phần lớn công việc định dạng kiểu này. Nó cho phép chọn số chữ số, nhóm hàng nghìn, dấu âm, hậu tố phần trăm, dạng khoa học hoặc tiền tệ. Chuỗi **pattern** mô tả bố cục, còn `java.text.DecimalFormatSymbols` quyết định những ký hiệu thực sự được dùng để hiển thị, chẳng hạn dấu phân cách thập phân và dấu tách nhóm.

**Điều quan trọng nhất:** định dạng một `double`/`long`/`BigDecimal` thành `String` **không tự sửa giá trị số đang lưu**. Phép format có thể làm tròn phần **hiển thị**, còn cách parse ngược một chuỗi thành số là một hoạt động riêng có hợp đồng và rủi ro riêng.

Sau bài này, bạn có thể:

1. Dùng `DecimalFormat` để định dạng số bằng những pattern như `#,##0.00` và `0.###`.
2. Giải thích ký hiệu `0`, `#`, `.`, `,`, `;`, `%`, `‰`, `E`, `¤` và cơ chế quote `'...'`.
3. Hiểu sự khác biệt giữa **pattern không bản địa hóa** và **các ký hiệu đã bản địa hóa**, biết dùng `applyPattern` và `applyLocalizedPattern` đúng ngữ cảnh.
4. Tạo `DecimalFormatSymbols` từ một `Locale` cụ thể và tự điều chỉnh dấu tách nhóm, dấu thập phân, dấu trừ, đơn vị tiền tệ nếu cần.
5. Kiểm soát làm tròn bằng `RoundingMode`, biết lợi ích của `BigDecimal` khi cần làm tròn thập phân chính xác.
6. Phân tích chuỗi với `parse`, `ParsePosition`, `setParseBigDecimal(true)` và hiểu nguy cơ **parse chỉ một tiền tố**.
7. Phân biệt định dạng trình bày, định dạng trao đổi dữ liệu, dữ liệu tiền tệ và input theo locale.
8. Biết tránh sử dụng chung một `DecimalFormat` mutable giữa các luồng mà không đồng bộ.

Các chương trình hoàn chỉnh sử dụng **Java 17+**; lệnh biên dịch/chạy và stdout được cung cấp để tự kiểm chứng.

## 2. Vai trò của NumberFormat, DecimalFormat, DecimalFormatSymbols và Locale

Bốn khái niệm liên quan nhưng không đồng nhất:

| Thành phần | Chức năng |
| --- | --- |
| `NumberFormat` | API tổng quát định dạng/phân tích số có quan tâm `Locale`; cung cấp factory cho số thường, số nguyên, tiền tệ, phần trăm |
| `DecimalFormat` | Implementation có **pattern** và các thuộc tính chi tiết: số chữ số, grouping, rounding, multiplier, phần âm, scientific notation |
| `DecimalFormatSymbols` | Bộ ký hiệu: decimal separator, grouping separator, zero digit, dấu âm, %, ‰, ký hiệu tiền tệ, NaN, Infinity... |
| `Locale` | Chọn quy ước ngôn ngữ/vùng; ảnh hưởng symbols, kiểu định dạng mặc định và một số dữ liệu liên quan |

### 2.1. Cách bắt đầu với NumberFormat

Nếu chỉ muốn số được định dạng theo `Locale`, thường nên dùng factory:

```java
import java.text.NumberFormat;
import java.util.Locale;

NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
String result = formatter.format(1234567.5);
```

Đây là một **mảnh mã minh họa**; đặt trong phương thức hoặc class để chạy. Dùng factory cho phép Java lấy định dạng phù hợp với locale, thay vì bạn tự phỏng đoán dấu phân cách.

**Không mặc định cast vô điều kiện** kết quả của `NumberFormat.getNumberInstance(locale)` thành `DecimalFormat`: nhiều provider thường trả `DecimalFormat`, nhưng API factory không bảo đảm luôn trả đúng subclass ấy. Nếu cần tính năng chỉ có trong `DecimalFormat`:

```java
NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
if (nf instanceof java.text.DecimalFormat df) {
    df.applyPattern("#,##0.00");
}
```

Đoạn dùng **pattern matching for instanceof** hợp lệ trong Java 17. Nếu API nghiệp vụ bắt buộc có đúng `DecimalFormat`, cũng có thể khởi tạo `new DecimalFormat(pattern, symbols)` với bộ symbols chọn tường minh, như trong những ví dụ tiếp theo.

### 2.2. Không ngầm phụ thuộc locale mặc định

`new DecimalFormat("#,##0.00")` lấy bộ ký hiệu của locale mặc định cho định dạng. Cùng chương trình có thể in `1,234.50` trên máy này và `1.234,50` trên máy khác. Để tài liệu, test và giao thức ổn định, chọn `Locale` hoặc `DecimalFormatSymbols` một cách rõ ràng.

Ví dụ:

```java
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

DecimalFormatSymbols us =
        DecimalFormatSymbols.getInstance(Locale.US);
DecimalFormat moneyLike = new DecimalFormat("#,##0.00", us);
```

Tên biến `moneyLike` chỉ nói **cách hiển thị hai số lẻ**. Pattern `#,##0.00` không tự gắn đơn vị tiền, mã ISO, luật thuế hoặc chính sách làm tròn tiền tệ.

## 3. Pattern là gì? Phân biệt ký tự cấu trúc và ký tự được in ra

`DecimalFormat` có **pattern không bản địa hóa** sử dụng các ký hiệu cấu trúc chuẩn. Pattern `#,##0.00` không có nghĩa lúc nào chuỗi kết quả cũng chứa dấu phẩy và dấu chấm y hệt; những vị trí này được thay bằng ký hiệu phù hợp của `DecimalFormatSymbols`.

Bảng ký hiệu quan trọng:

| Ký hiệu | Vai trò trong pattern không localized | Ví dụ ý nghĩa |
| --- | --- | --- |
| `0` | Chữ số **bắt buộc**, thêm số 0 khi thiếu | `0000` định dạng 12 thành `0012` |
| `#` | Chữ số **tùy chọn**, không thêm zero không cần thiết | `0.##` có tối đa 2 số lẻ |
| `.` | Vị trí dấu thập phân | Hiển thị `.`, `,` hoặc ký hiệu khác theo symbols |
| `,` | Vị trí chia nhóm phần nguyên | Hàng nghìn hoặc kích thước nhóm tùy pattern/cấu hình |
| `;` | Phân tách pattern cho số dương và số âm | `0.00;(0.00)` |
| `%` | Phần trăm; nhân 100 khi format, chia 100 khi parse theo multiplier | `0.0%` |
| `‰` | Phần nghìn (per mille); multiplier 1000 | `0.0‰` |
| `E` | Dạng khoa học/exponential notation | `0.00E0` |
| `¤` | Ký hiệu tiền tệ theo symbols/locale | `¤#,##0.00` |
| `¤¤` | Mã tiền tệ quốc tế (ví dụ USD) | `¤¤ #,##0.00` |
| `'` | Dùng để quote văn bản/ký hiệu đặc biệt | `'Total:' #,##0.00` |

Trong Java string literal, phần lớn các pattern ở bảng viết trực tiếp như `"#,##0.00"` hoặc `"0.0%"`. Ký hiệu `‰` là Unicode U+2030, có thể viết trong Java source dưới dạng `"\u2030"` nếu cần tránh phụ thuộc cách lưu file nguồn. Ký hiệu tiền tệ `¤` là U+00A4.

### 3.1. `0` và `#`: bắt buộc hay tùy chọn

Cho số `12.5`:

```text
Pattern        Kết quả với symbols Locale.US
0.00           12.50
0.##           12.5
0000.000       0012.500
```

Với `0.00`, định dạng hiển thị **đúng hai chữ số** sau dấu thập phân. Với `0.##`, có **tối đa hai chữ số**, không bắt buộc giữ zero ở cuối. Với `0000.000`, phần nguyên được đệm zero tới bốn chữ số và phần lẻ tới ba chữ số.

**Lưu ý:** `DecimalFormat` không phải cách lưu số thành chuỗi với độ chính xác vô hạn. Nếu format chỉ cho hai chữ số sau dấu thập phân, những chữ số còn lại không xuất hiện trong kết quả và có thể đã được **làm tròn ở bước biểu diễn chuỗi**.

### 3.2. Grouping và pattern `#,##0.00`

`#,##0.00` có ý nghĩa:

- Hiển thị nhóm hàng nghìn trong phần nguyên.
- Có ít nhất một chữ số nguyên.
- Có đúng hai chữ số phần lẻ.
- Dùng dấu phân cách do symbols quy định.

Ví dụ:

```java
DecimalFormat usFormatter =
        new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.US));

System.out.println(usFormatter.format(1234567.5));
// 1,234,567.50
```

Dấu phẩy **trong pattern** là placeholder cho grouping. Dấu phẩy **trong kết quả US** là glyph được `DecimalFormatSymbols` cung cấp. Hai việc này trùng ký tự trong `Locale.US` nhưng không bắt buộc trùng ở locale khác.

### 3.3. Group size và giới hạn chữ số

Bạn cũng có thể thay đổi thuộc tính bằng method:

```java
DecimalFormat df = new DecimalFormat("#,##0.00",
        DecimalFormatSymbols.getInstance(Locale.US));

df.setGroupingUsed(true);
df.setGroupingSize(4);
df.setMinimumFractionDigits(2);
df.setMaximumFractionDigits(2);
```

Với `setGroupingSize(4)`, các nhóm xuất hiện từ phải sang trái theo bốn chữ số trong phần nguyên. Cần hiểu đây là **độ rộng nhóm hiển thị**, không làm đổi giá trị số. Nếu tạo pattern với nhiều dấu phẩy grouping, quy tắc `DecimalFormat` không phải hệ thống tổng quát tùy ý cho mọi thứ bậc nhóm địa phương; tài liệu mô tả cách pattern được diễn giải và dấu grouping có ảnh hưởng chính là vùng cuối gần chữ số nguyên.

Đừng xem `setMaximumFractionDigits` là một bộ kiểm tra input: giới hạn chữ số hiển thị không tự bảo đảm người dùng chỉ nhập tối đa số chữ số đó.

## 4. Hai phần pattern: số dương và số âm

Pattern có thể chứa hai subpattern, ngăn bằng `;`:

```text
#,##0.00;(#,##0.00)
```

Ví dụ kết quả `Locale.US`:

```text
 1234.5 ->  1,234.50
-1234.5 -> (1,234.50)
```

Điều này phù hợp một số báo cáo kế toán muốn viết **số âm trong ngoặc**. Nếu không khai báo subpattern âm, Java thường dùng subpattern dương với dấu âm đứng trước.

Chú ý: theo hợp đồng `DecimalFormat`, subpattern âm chủ yếu quyết định **prefix/suffix âm**; cấu trúc chữ số tối thiểu/tối đa và phần phân nhóm của pattern dương được sử dụng chung. Vì vậy không nên kỳ vọng khai báo hai dãy chữ số hoàn toàn khác nhau cho positive/negative trong cùng pattern sẽ tạo hai bộ precision độc lập.

Khi parse lại số âm có ngoặc, phải sử dụng đúng một formatter và pattern phù hợp. Không dùng quy tắc tự cắt dấu trừ rồi gắn lại nếu dữ liệu có tiền tố/hậu tố đã được bản địa hóa.

## 5. Prefix, suffix và quote văn bản

Pattern có thể đính kèm từ hoặc ký hiệu:

```java
DecimalFormat df = new DecimalFormat(
        "'Total:' #,##0.00",
        DecimalFormatSymbols.getInstance(Locale.US));

System.out.println(df.format(1234.5));
// Total: 1,234.50
```

Cặp dấu quote `'...'` buộc Java hiểu `Total:` là chuỗi ký tự literal trong prefix, chứ không là ký hiệu pattern. Nếu cần một ký tự apostrophe literal trong phần prefix/suffix, thường viết hai dấu quote liên tiếp `''` theo quy tắc pattern.

**Đừng ghép đơn vị tiền tệ, giá trị và dấu phân cách bằng phép nối chuỗi tùy tiện** nếu hệ thống đa locale: một số locale đặt ký hiệu trước, số khác đặt sau; khoảng trắng và quy ước ký hiệu cũng có thể khác. Với nội dung tiền tệ, hãy dùng factory currency hoặc pattern có `¤` và currency metadata phù hợp.

## 6. Phần trăm và phần nghìn: thay đổi multiplier

Một lỗi thường gặp là nghĩ `0.0%` chỉ “thêm ký hiệu %”. Thực tế `%` còn **nhân giá trị với 100 khi format**.

```java
DecimalFormat percent = new DecimalFormat("0.0%",
        DecimalFormatSymbols.getInstance(Locale.US));

System.out.println(percent.format(0.256)); // 25.6%
```

Giá trị input `0.256` được biểu diễn thành `25.6%`, đúng vì `0.256×100=25.6`. Nếu input đã có giá trị 25.6 đại diện “25.6%”, đem format bằng pattern `0.0%` sẽ nhân thêm 100 lần; dữ liệu đầu vào phải dùng một quy ước thống nhất.

Ký hiệu `‰` (U+2030) thể hiện **per mille**, nhân giá trị với **1000** khi format:

```java
DecimalFormat perMille = new DecimalFormat(
        "0.0\u2030",
        DecimalFormatSymbols.getInstance(Locale.US));
```

Mảnh code này hiển thị giá trị `0.256` tương ứng `256.0‰` khi symbol per-mille theo bộ US là `‰`. Java source sử dụng Unicode escape để tệp có thể lưu pattern độc lập với bàn phím nhập ký hiệu.

Khi parse chuỗi có hậu tố phần trăm bằng formatter percent, **multiplier được áp dụng ngược lại**. Chẳng hạn `percent.parse("25.6%")` trả giá trị số gần `0.256` (kiểu cụ thể có thể là `Double` tùy parse config). Không tự chia 100 một lần nữa sau khi parser đã xử lý multiplier.

## 7. Scientific notation: `E` trong pattern

`E` phân tách phần significand và phần mũ thập phân của **chuỗi hiển thị**:

```java
DecimalFormat scientific = new DecimalFormat("0.00E0",
        DecimalFormatSymbols.getInstance(Locale.US));

System.out.println(scientific.format(12345));
// 1.23E4
```

`0.00E0` yêu cầu:

- Một chữ số bắt buộc trước dấu thập phân trong phần significand.
- Hai chữ số phần lẻ.
- Chữ `E` và ít nhất một chữ số phần mũ.

`DecimalFormat` có các quy tắc riêng cho kiểu scientific/engineering notation khi pattern có số lượng chữ số nguyên khác nhau, chẳng hạn `##0.###E0`. Không nên tự chia input cho 10ⁿ rồi nối chữ `"E"` khi Java đã hỗ trợ format exponent và ký hiệu exponent có thể được `DecimalFormatSymbols` tùy chỉnh.

**Lưu ý hai loại E:** chữ `e/E` trong `Double.parseDouble("1.23e4")` là cú pháp **đọc một chuỗi số**, còn `E` trong pattern `"0.00E0"` là **chỉ thị cách xuất chuỗi**. Không hoán đổi chúng như thể `DecimalFormat` đọc source literal của Java.

## 8. Ký hiệu tiền tệ: `¤`, `¤¤` và Currency

`DecimalFormat` hiểu:

- `¤` (U+00A4) là vị trí ký hiệu tiền tệ của `DecimalFormatSymbols`, ví dụ `$` cho USD trong ngữ cảnh Hoa Kỳ.
- `¤¤` là vị trí mã tiền tệ quốc tế, ví dụ `USD`.
- Pattern tiền tệ có thể sử dụng **monetary decimal separator** khác decimal separator thường nếu locale/symbols quy định như vậy.

Ví dụ:

```java
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.Locale;

DecimalFormatSymbols symbols =
        DecimalFormatSymbols.getInstance(Locale.US);
symbols.setCurrency(Currency.getInstance("USD"));

DecimalFormat formatter =
        new DecimalFormat("\u00A4#,##0.00", symbols);

String formatted = formatter.format(1234.5);
```

Đây là mảnh code trong phương thức Java. Với thông số `Locale.US` và USD theo JDK thông thường, kết quả có dạng **`$1,234.50`**. Với `¤¤`, kết quả biểu thị mã như `USD` thay vì ký hiệu đồng tiền ngắn.

**Không dùng `¤` như phép chuyển đổi tiền tệ.** Nếu input là 1234.5 đơn vị tiền X, việc đổi symbol thành USD **không** tính tỷ giá và **không** thay đổi số lượng tiền. Ứng dụng phải biết đồng tiền thực của giá trị và thực hiện chuyển đổi tỷ giá riêng nếu cần.

Đối với format tiền tệ theo locale mặc định, `NumberFormat.getCurrencyInstance(locale)` thường thích hợp hơn tự vẽ mọi pattern. Nhưng với yêu cầu mã ISO cố định, ký hiệu và số lẻ, hãy kiểm soát `Currency`, locale và rounding theo chuẩn nghiệp vụ.

## 9. DecimalFormatSymbols: tách mẫu số và glyph hiển thị

Lấy cùng pattern `#,##0.00` nhưng dùng ký hiệu cho hai locale:

```java
DecimalFormat us = new DecimalFormat("#,##0.00",
        DecimalFormatSymbols.getInstance(Locale.US));

DecimalFormat de = new DecimalFormat("#,##0.00",
        DecimalFormatSymbols.getInstance(Locale.GERMANY));
```

Với giá trị `1234567.5`:

```text
US: 1,234,567.50
DE: 1.234.567,50
```

Pattern **không đổi**. Bộ symbols khác nhau nên chuỗi kết quả đổi.

Các phương thức thường dùng của `DecimalFormatSymbols`:

| Method | Ý nghĩa |
| --- | --- |
| `getDecimalSeparator()` / `setDecimalSeparator(char)` | Dấu thập phân thường |
| `getGroupingSeparator()` / `setGroupingSeparator(char)` | Dấu tách nhóm |
| `getMinusSign()` / `setMinusSign(char)` | Dấu số âm |
| `getZeroDigit()` / `setZeroDigit(char)` | Chữ số zero gốc cho hệ chữ số localized |
| `getPercent()` / `setPercent(char)` | Dấu phần trăm |
| `getPerMill()` / `setPerMill(char)` | Dấu phần nghìn |
| `getCurrencySymbol()` / `setCurrencySymbol(String)` | Ký hiệu tiền tệ hiển thị |
| `getInternationalCurrencySymbol()` | Mã tiền tệ quốc tế |
| `getExponentSeparator()` / `setExponentSeparator(String)` | Ký hiệu phần mũ |
| `getNaN()` / `setNaN(String)` | Biểu diễn NaN |
| `getInfinity()` / `setInfinity(String)` | Biểu diễn vô cực |

Một số API còn có monetary grouping separator/monetary decimal separator. Nếu thay ký hiệu cho currency, hãy xem kỹ contract và sự nhất quán của nhiều thuộc tính liên quan thay vì chỉ đổi một dấu thập phân.

### 9.1. Tự định nghĩa dấu phân cách

```java
DecimalFormatSymbols custom =
        DecimalFormatSymbols.getInstance(Locale.US);
custom.setGroupingSeparator('^');
custom.setDecimalSeparator('|');

DecimalFormat df = new DecimalFormat("#,##0.00", custom);
System.out.println(df.format(1234567.5));
// 1^234^567|50
```

**Ý nghĩa:** `#,##0.00` vẫn dùng `,` và `.` làm **placeholders trong pattern**. Khi hiển thị, ký tự nhóm trở thành `^`, dấu thập phân trở thành `|`. Đây là cách kiểm chứng trực quan sự khác nhau giữa pattern và symbols.

Nếu bạn tùy chỉnh để cho **cùng một ký tự** làm dấu thập phân và dấu nhóm, hoặc dấu âm trùng chữ số, việc parse sẽ trở nên mơ hồ. `DecimalFormat`/`DecimalFormatSymbols` không tự suy ra ý định trong mọi cấu hình không hợp lý. Thiết kế bộ symbols sao cho có thể phân biệt được các thành phần.

## 10. applyPattern và applyLocalizedPattern không cùng ngữ nghĩa

`DecimalFormat.applyPattern(String)` nhận **pattern chuẩn không localized**: `.` là placeholder decimal, `,` là placeholder grouping. Nó vẫn dùng `DecimalFormatSymbols` hiện có để hiển thị glyph theo locale.

`DecimalFormat.applyLocalizedPattern(String)` nhận **pattern đã bản địa hóa**, trong đó ký hiệu decimal/grouping trong **chuỗi pattern đầu vào** là những ký hiệu phù hợp với bộ symbols của formatter.

Ví dụ với `Locale.GERMANY`:

```java
DecimalFormat df = new DecimalFormat("#,##0.00",
        DecimalFormatSymbols.getInstance(Locale.GERMANY));

df.applyPattern("#,##0.00");          // non-localized
df.applyLocalizedPattern("#.##0,00"); // localized theo ký hiệu Đức
```

Cả hai pattern trên mô tả cùng dạng định dạng và đều có thể cho chuỗi `1.234.567,50` khi format `1234567.5`. Nếu lấy `"#.##0,00"` rồi đưa vào `applyPattern`, Java **không** coi dấu `.` đó là nhóm theo cách đọc của người Đức; nó dùng quy tắc cấu trúc **không localized**, nên có thể lỗi hoặc cho nghĩa khác.

Hai method đi kèm là `toPattern()` và `toLocalizedPattern()` để lấy pattern ở hai dạng biểu diễn tương ứng. Điều này quan trọng khi **lưu cấu hình**: hãy ghi rõ file đang lưu pattern chuẩn hay pattern localized, không đổi locale khi đọc lại mà quên chuyển cú pháp.

### 10.1. Giao diện cho người dùng nhập pattern

Nếu giao diện cho người dùng Đức gõ `#.##0,00`, bạn có thể cần `applyLocalizedPattern`, vì người dùng đang viết theo ký hiệu Đức. Nếu file config nội bộ do hệ thống sinh ra dùng `#,##0.00`, hãy dùng `applyPattern` để tránh phụ thuộc cách viết địa phương. Phải xác định rõ nguồn pattern ngay từ API, không trộn hai chuẩn.

Không nhận pattern tùy ý từ bên ngoài rồi chạy vô hạn trong một hệ thống nhạy hiệu năng mà không có giới hạn độ dài/độ phức tạp, chính sách về ký hiệu và test các trường hợp biên. Một pattern hợp lệ có thể không đáp ứng yêu cầu nghiệp vụ về tiền tệ hoặc số chữ số; validation cần vượt qua bước “không ném exception”.

## 11. RoundingMode kết hợp với pattern

Pattern `0.00` quy định hai chữ số sau dấu thập phân; `setRoundingMode` quyết định hướng chọn số khi cần bỏ phần lẻ. Với `BigDecimal("2.345")`, `HALF_EVEN` cho `2.34`, còn `HALF_UP` cho `2.35`. Formatter chỉ xuất chuỗi, không thay đổi `BigDecimal` gốc. Cách phân loại đủ tám `RoundingMode`, số âm, `UNNECESSARY` và sự khác nhau giữa làm tròn hiển thị/tính toán thuộc bài `RoundingMode của bộ định dạng`.

## 12. Chương trình hoàn chỉnh A: các pattern phổ biến

Lưu thành **`DecimalFormatPatterns.java`**:

```java
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class DecimalFormatPatterns {
    public static void main(String[] args) {
        DecimalFormatSymbols us =
                DecimalFormatSymbols.getInstance(Locale.US);

        System.out.println(new DecimalFormat("#,##0.00", us)
                .format(1234567.5));
        System.out.println(new DecimalFormat("#,##0.##", us)
                .format(1234567.5));
        System.out.println(new DecimalFormat("0000.000", us)
                .format(12.5));
        System.out.println(new DecimalFormat("#,##0.00;(#,##0.00)", us)
                .format(-1234.5));
        System.out.println(new DecimalFormat("0.0%", us)
                .format(0.256));
        System.out.println(new DecimalFormat("0.00E0", us)
                .format(12345));
        System.out.println(new DecimalFormat("'Total:' #,##0.00", us)
                .format(1234.5));
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 DecimalFormatPatterns.java
java DecimalFormatPatterns
```

**Stdout chính xác** với symbols US đã chỉ định:

```text
1,234,567.50
1,234,567.5
0012.500
(1,234.50)
25.6%
1.23E4
Total: 1,234.50
```

Mỗi dòng cho thấy một đặc trưng: nhóm chữ số, phần lẻ tùy chọn, số 0 bắt buộc, pattern âm trong ngoặc, multiplier phần trăm, scientific notation và prefix được quote.

## 13. Chương trình hoàn chỉnh B: locale, symbols và localized pattern

Lưu thành **`DecimalFormatLocales.java`**:

```java
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class DecimalFormatLocales {
    public static void main(String[] args) {
        DecimalFormatSymbols us =
                DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormatSymbols de =
                DecimalFormatSymbols.getInstance(Locale.GERMANY);

        DecimalFormat usFormat = new DecimalFormat("#,##0.00", us);
        DecimalFormat deFormat = new DecimalFormat("#,##0.00", de);

        System.out.println(usFormat.format(1234567.5));
        System.out.println(deFormat.format(1234567.5));

        DecimalFormatSymbols custom =
                (DecimalFormatSymbols) us.clone();
        custom.setGroupingSeparator('^');
        custom.setDecimalSeparator('|');

        DecimalFormat customFormat =
                new DecimalFormat("#,##0.00", custom);
        System.out.println(customFormat.format(1234567.5));

        deFormat.applyLocalizedPattern("#.##0,00");
        System.out.println(deFormat.toPattern());
        System.out.println(deFormat.format(1234567.5));
    }
}
```

Chạy:

```bash
javac --release 17 DecimalFormatLocales.java
java DecimalFormatLocales
```

**Stdout chính xác:**

```text
1,234,567.50
1.234.567,50
1^234^567|50
#,##0.00
1.234.567,50
```

Với `Locale.GERMANY`, dấu nhóm và dấu thập phân được đổi chỗ trong **chuỗi xuất ra**. Pattern chuẩn `#,##0.00` không đổi; pattern localized `#.##0,00` được `applyLocalizedPattern` chuyển thành cấu trúc chuẩn tương ứng.

Dùng `clone()` để có bộ symbols riêng trước khi thay ký hiệu giúp tránh vô tình chỉnh một bộ đối tượng mà code khác có thể đang sử dụng. Tính mutable của formatter/symbols là lý do nên thiết kế scope và thread ownership rõ.

## 14. Chương trình hoàn chỉnh C: rounding bằng BigDecimal

Lưu thành **`DecimalFormatRounding.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class DecimalFormatRounding {
    public static void main(String[] args) {
        DecimalFormat df = new DecimalFormat("0.00",
                DecimalFormatSymbols.getInstance(Locale.US));

        System.out.println(df.getRoundingMode());
        System.out.println(df.format(new BigDecimal("2.345")));
        System.out.println(df.format(new BigDecimal("2.355")));

        df.setRoundingMode(RoundingMode.HALF_UP);
        System.out.println(df.format(new BigDecimal("2.345")));
        System.out.println(df.format(new BigDecimal("-2.345")));

        BigDecimal original = new BigDecimal("2.345");
        String display = df.format(original);
        System.out.println(display);
        System.out.println(original.toPlainString());
    }
}
```

Chạy:

```bash
javac --release 17 DecimalFormatRounding.java
java DecimalFormatRounding
```

**Stdout:**

```text
HALF_EVEN
2.34
2.36
2.35
-2.35
2.35
2.345
```

Hai dòng cuối đặc biệt hữu ích: `display` đã được làm tròn thành chuỗi `"2.35"` theo mode HALF_UP, nhưng `original` vẫn là `BigDecimal("2.345")`. Cần thao tác dữ liệu thực sự nếu muốn kết quả làm tròn được lưu.

### 14.1. Tác dụng của setRoundingMode và setMaximumFractionDigits

`DecimalFormat` có thuộc tính mutable. Nếu tạo formatter một lần rồi chỉnh `setMaximumFractionDigits(4)`, các lần `format` sau có thể in tối đa bốn chữ số lẻ. Khi người dùng đổi cách format, tránh dùng lại cùng đối tượng formatter để đồng thời phục vụ nhiều cấu hình khác nhau.

Nếu dùng `RoundingMode.UNNECESSARY`, thao tác format đòi loại bỏ chữ số có thể ném `ArithmeticException`. Chọn mode này khi **chính sách không cho phép rounding**, và xử lý exception thay vì âm thầm làm tròn.

## 15. Parse với DecimalFormat có giới hạn riêng

`DecimalFormat` đọc chuỗi theo pattern, symbols và locale hiện hành. `parse(String)` có thể chỉ lấy tiền tố hợp lệ; khi cần toàn chuỗi, dùng `ParsePosition` và so `getIndex()` với `text.length()`. Nếu muốn decimal hữu hạn chính xác, bật `setParseBigDecimal(true)` nhưng vẫn kiểm tra kiểu kết quả, vì một số giá trị đặc biệt có thể là `Double`. `setParseIntegerOnly(true)` ảnh hưởng parse và không có nghĩa định dạng output chỉ còn số nguyên.

Các bài `ParsePosition và kiểm tra toàn bộ chuỗi khi parse theo locale` và `setParseBigDecimal và giới hạn an toàn khi dùng formatter đồng thời` dạy đầy đủ ví dụ parse strict, edge cases và thread safety.

## 16. Đối chiếu nhanh khi cần parse một chuỗi localized

```java
java.text.DecimalFormat f = new java.text.DecimalFormat(
        "#,##0.00",
        java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
f.setParseBigDecimal(true);
java.text.ParsePosition p = new java.text.ParsePosition(0);
String text = "1,234.50";
Number result = f.parse(text, p);
System.out.println(result instanceof java.math.BigDecimal
        && p.getIndex() == text.length()); // true
```

Đoạn trên minh họa cách pattern và symbols đang được dùng ở **chiều parse**. Muốn ràng buộc placement grouping, miền giá trị hoặc chữ số cho phép nghiêm ngặt hơn, cần thêm validation theo schema; parse toàn chuỗi chỉ là điều kiện cần.

## 17. Format và parse không phải hai phép nghịch đảo hoàn hảo

Giả sử dùng `DecimalFormat("0.00")` để in giá trị:

```text
Giá trị nguồn: 2.345
Chuỗi hiển thị: "2.34" hoặc "2.35" tùy rounding
Giá trị parse lại: 2.34 hoặc 2.35
```

Parse lại không tái tạo mọi chữ số của số nguồn ban đầu, vì **format đã cắt/làm tròn số chữ số hiển thị**. Vậy:

```text
parse(format(x)) == x
```

**Không phải đẳng thức tổng quát**. Những nguyên nhân gồm:

1. Rounding và giới hạn chữ số ở pattern.
2. Grouping/decimal separator theo locale.
3. Multiplier phần trăm/phần nghìn.
4. Prefix/suffix tiền tệ hoặc số âm.
5. Chuỗi đặc biệt NaN/Infinity và signed zero.
6. Chính sách parse trả `Long`, `Double` hay `BigDecimal`, có thể làm đổi kiểu và khả năng giữ scale.

Để trao đổi dữ liệu máy–máy cần round-trip chính xác của `double`, thường dùng `Double.toString` và `Double.parseDouble` (với các ràng buộc schema và giá trị đặc biệt cần kiểm soát) thay vì định dạng trình bày `#,##0.00`.

Đối với decimal chính xác và tiền tệ, gửi dạng chuẩn theo schema được xác định rõ (chẳng hạn chuỗi decimal canonical cùng mã tiền tệ/scale), không dùng UI string tùy locale làm định dạng lưu trữ nội bộ.

## 18. Formatter có trạng thái thay đổi

`DecimalFormat` cho phép đổi pattern, symbols, multiplier, fraction digits và mode trong khi dùng; do đó không mặc định an toàn khi cùng instance bị nhiều thread truy cập đồng thời. Bài riêng về `setParseBigDecimal` và sử dụng formatter đồng thời hướng dẫn tạo instance cục bộ, `ThreadLocal` và đồng bộ ngoài.

## 19. Locale và định dạng nghiệp vụ

`DecimalFormatSymbols` làm thay đổi ký hiệu thập phân, phân nhóm, dấu âm và tiền tệ; không được đoán mọi locale sử dụng cùng grouping hoặc glyph. Các pattern và symbols phục vụ trình bày, không xác định đơn vị, loại tiền hoặc quy tắc tính thuế. Bài `Locale, dấu thập phân, nhóm chữ số và dấu âm` và bài wire-format cuối chương giải thích chính sách đầu vào/đầu ra chi tiết.

## 20. Những lỗi phổ biến và hướng xử lý

| Ngộ nhận/lỗi | Vì sao sai | Hướng đúng |
| --- | --- | --- |
| “`#,##0.00` luôn in dấu phẩy nhóm, dấu chấm lẻ” | Symbols tùy locale | Chọn `DecimalFormatSymbols` rõ ràng |
| “`#` và `0` giống nhau” | `0` ép xuất chữ số, `#` tùy chọn | Đặt min/max digits theo yêu cầu |
| “Format hai chữ số sẽ sửa giá trị nguồn” | `format` tạo chuỗi mới | Làm tròn dữ liệu riêng nếu nghiệp vụ yêu cầu |
| “`0.0%` chỉ gắn dấu phần trăm” | Multiplier 100 | Xác định input đang là fraction hay percent |
| “`¤` tự đổi tiền” | Symbol không tính tỷ giá | Quản lý currency và phép chuyển đổi riêng |
| “`applyPattern("#.##0,00")` đọc như ký hiệu Đức” | `applyPattern` nhận pattern chuẩn | Dùng `applyLocalizedPattern` với localized pattern |
| “`new DecimalFormat(pattern)` không phụ thuộc locale” | Constructor lấy symbols từ default locale | Chỉ định symbols/locale |
| “parse thành công nghĩa là cả chuỗi hợp lệ” | Parser có thể chỉ tiêu thụ tiền tố | Kiểm tra `ParsePosition` và input grammar |
| “`setMaximumFractionDigits` giới hạn số lẻ đầu vào” | Chủ yếu là quy tắc format | Validation đầu vào riêng |
| “`parse(String)` luôn trả `Double`” | `Number` có nhiều subtype | Kiểm tra/cấu hình `setParseBigDecimal` |
| “`parseBigDecimal` biến NaN thành decimal” | NaN/Infinity có xử lý đặc biệt | Chặn giá trị không hữu hạn theo schema |
| “`DecimalFormat` mặc định luôn HALF_UP” | Mặc định `HALF_EVEN` | Set `RoundingMode` tường minh |
| “Định dạng 2.345 double là tie decimal chính xác” | binary64 có rounding | Dùng `BigDecimal("2.345")` nếu minh họa decimal tie |
| “Một static final formatter dùng chung thread luôn an toàn” | Formatter mutable, không mặc định synchronized | Dùng instance riêng/đồng bộ |
| “UI string có thể làm format lưu trữ chung mọi quốc gia” | Locale, rounding, separators thay đổi | Chọn định dạng interchange chuẩn riêng |

## 21. Bài tập tự luyện có đáp án

### Bài 1 — Ý nghĩa của pattern

Pattern `#,##0.00` trong `Locale.US` in `1234.5` như thế nào?

**Đáp án:** `1,234.50`. Chữ `0` ở phần lẻ yêu cầu đủ hai chữ số; dấu phẩy nhóm hàng nghìn được hiển thị theo US.

### Bài 2 — `0` và `#`

Với `12.5` và symbols US, pattern `0000.000` và `0.##` khác nhau ra sao?

**Đáp án:** `0012.500` và `12.5`. Pattern thứ nhất đệm zero; thứ hai chỉ in số chữ số lẻ cần thiết (tối đa 2).

### Bài 3 — Positive/negative subpatterns

Với `#,##0.00;(#,##0.00)`, giá trị `-1234.5` được biểu diễn ra sao?

**Đáp án:** `(1,234.50)` trong Locale.US. Subpattern âm thay prefix/suffix âm thành dấu ngoặc.

### Bài 4 — Phần trăm

`new DecimalFormat("0.0%", us).format(0.256)` cho chuỗi nào? Vì sao?

**Đáp án:** `25.6%`. Pattern `%` nhân input với 100 và kèm ký hiệu phần trăm.

### Bài 5 — Scientific notation

`"0.00E0"` in `12345` thành chuỗi nào trong US?

**Đáp án:** `1.23E4`. Một chữ số trước decimal, hai chữ số sau, mũ thập phân.

### Bài 6 — Locale

Cùng giá trị `1234567.5` và cùng pattern chuẩn `#,##0.00`, kết quả US và Đức khác nhau thế nào?

**Đáp án:** US `1,234,567.50`; Đức `1.234.567,50`. Symbols đổi glyph, không đổi giá trị.

### Bài 7 — Custom symbols

Nếu grouping separator là `^` và decimal separator là `|`, pattern chuẩn `#,##0.00` in `1234567.5` thế nào?

**Đáp án:** `1^234^567|50`. Không phải sửa pattern thành `#^##0|00`; hai ký hiệu custom chỉ nằm trong output symbols.

### Bài 8 — applyPattern so với applyLocalizedPattern

Với bộ symbols Đức, chuỗi pattern `"#.##0,00"` nên đưa cho method nào?

**Đáp án:** `applyLocalizedPattern`. `applyPattern` cần chuẩn `"#,##0.00"`, độc lập ký hiệu địa phương.

### Bài 9 — Half-even và half-up

Với `BigDecimal("2.345")` và pattern `0.00`, `HALF_EVEN` và `HALF_UP` cho gì?

**Đáp án:** `2.34` và `2.35`. Với HALF_EVEN, trường hợp tie chọn chữ số cuối chẵn.

### Bài 10 — Format có làm mất dữ liệu nguồn?

Một `BigDecimal("2.345")` được format thành `"2.35"`. `toPlainString()` của BigDecimal ban đầu sau khi format là gì?

**Đáp án:** vẫn `2.345`. Formatter sinh chuỗi, không mutate giá trị `BigDecimal`.

### Bài 11 — Parse tiền tố

`df.parse("123abc", new ParsePosition(0))` trả `123` và vị trí 3. Có nên chấp nhận toàn bộ input không?

**Đáp án:** không nếu schema yêu cầu toàn bộ chuỗi là số. So `ParsePosition.getIndex()` với `input.length()` và kiểm tra thêm ngữ pháp nếu cần.

### Bài 12 — Number subtype

`DecimalFormat.parse("1,234.50")` luôn trả `Double` phải không?

**Đáp án:** không. `parse` khai báo trả `Number`; có thể trả subtype khác theo cài đặt/đầu vào, trong đó `setParseBigDecimal(true)` khiến số thập phân hữu hạn được trả dưới dạng `BigDecimal`.

### Bài 13 — setParseBigDecimal

Khi `setParseBigDecimal(true)` rồi parse `"1,234.50"` theo symbols US, giá trị hiển thị của `Number` là gì?

**Đáp án:** `1234.50`, kiểu `BigDecimal` trong trường hợp này. Điều này hữu ích khi cần giữ số decimal thay vì parse qua binary double.

### Bài 14 — Thread safety

Vì sao không nên dùng một đối tượng `static final DecimalFormat` chung cho hàng chục thread mà không đồng bộ?

**Đáp án:** `final` không khiến đối tượng mutable thành immutable; `DecimalFormat` không mặc định thread-safe/synchronized. Dùng scope riêng hoặc đồng bộ ngoài.

### Bài 15 — Tiền tệ

Pattern `¤#,##0.00` với USD được dùng để hiển thị một số tiền. Nó có tự đổi lượng EUR thành USD không?

**Đáp án:** không. Pattern chỉ chọn symbol/chuỗi và cách hiển thị, không có tỷ giá hay phép chuyển đơn vị.

### Bài 16 — Round-trip

Vì sao `parse(format(x))` có thể không bằng `x` sau khi format `x` với pattern `0.00`?

**Đáp án:** format đã làm tròn giới hạn phần lẻ, có thể bỏ mất thông tin của `x`. Parse chuỗi hiển thị không tự phục hồi những chữ số ban đầu.

## 22. Checklist khi dùng DecimalFormat trong dự án

- [ ] Tôi phân biệt **giá trị số** với **chuỗi format** và biết format không mutate input.
- [ ] Tôi dùng `NumberFormat` factory khi chỉ cần định dạng theo locale, không cast subclass vô điều kiện.
- [ ] Tôi biết `DecimalFormat` là class có pattern và các thuộc tính mutable.
- [ ] Tôi giải thích đúng `0`, `#`, `.`, `,`, `;`, `%`, `‰`, `E`, `¤` và quote `'`.
- [ ] Tôi biết pattern `#,##0.00` là **non-localized**, còn chuỗi kết quả lấy dấu theo `DecimalFormatSymbols`.
- [ ] Tôi dùng `DecimalFormatSymbols.getInstance(locale)` thay vì dựa vào môi trường ngẫu nhiên.
- [ ] Tôi phân biệt `applyPattern` và `applyLocalizedPattern` khi đọc cấu hình pattern.
- [ ] Tôi kiểm soát positive/negative subpattern và chính sách hiển thị số âm.
- [ ] Tôi hiểu multiplier phần trăm/phần nghìn, không nhân hoặc chia lần hai.
- [ ] Tôi biết ký hiệu `¤` gắn với currency metadata, không phải công thức tỷ giá.
- [ ] Tôi đặt `RoundingMode` theo yêu cầu, dùng `BigDecimal` cho trường hợp rounding thập phân cần chính xác.
- [ ] Tôi không coi `format` hai số lẻ là bước làm tròn và lưu dữ liệu nghiệp vụ.
- [ ] Tôi biết `parse` trả `Number` và có thể chỉ nhận một phần chuỗi.
- [ ] Tôi kiểm tra `ParsePosition`/độ dài, chọn `setParseBigDecimal` nếu phải giữ decimal.
- [ ] Tôi không dùng chung một instance mutable giữa nhiều thread mà không có đồng bộ.
- [ ] Tôi dùng format máy–máy có schema cố định thay vì dùng UI string theo locale.
- [ ] Tôi đã chạy bốn chương trình và kiểm thử số âm, zero, rounding tie, input rác và nhiều locale.

## 23. Tài liệu Java SE 17 chính thức

- [DecimalFormat API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormat.html) — patterns, positive/negative subpatterns, multipliers, rounding, parsing, symbols, thread safety.
- [DecimalFormatSymbols API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormatSymbols.html) — các dấu thập phân, grouping, minus, currency, percent, exponent, NaN/Infinity.
- [NumberFormat API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/NumberFormat.html) — factory methods và các thuộc tính format/parse.
- [ParsePosition API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/ParsePosition.html) — kiểm tra vị trí parser đã tiêu thụ dữ liệu.
- [Locale API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html) — chọn locale và convention văn hóa.
- [RoundingMode API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html) — quy tắc làm tròn.
- [BigDecimal API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html) — biểu diễn decimal chính xác theo nguồn chuỗi và scale.
- [Currency API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Currency.html) — metadata tiền tệ.
- [Oracle Java Tutorial — Customizing Formats](https://docs.oracle.com/javase/tutorial/i18n/format/decimalFormat.html) — các ví dụ pattern/symbols (tài liệu tutorial cũ; đối chiếu API Java SE 17 nếu có chi tiết phiên bản).

**Tóm lại:** `DecimalFormat` quyết định **mẫu trình bày**, `DecimalFormatSymbols` quyết định **những ký hiệu được hiển thị**, và `Locale` cung cấp **quy ước địa phương**. Luôn tách bước **tính/lưu giá trị**, **format hiển thị**, **parse dữ liệu đầu vào** và **kiểm tra nghiệp vụ**; khi đó việc đổi dấu phân cách, định dạng tiền, phần trăm hay rounding sẽ không vô tình làm thay đổi ý nghĩa số trong chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-008">Locale, dấu thập phân, nhóm chữ số và dấu âm</a>

<details>
<summary>Xem chi tiết</summary>

# Locale, dấu thập phân, nhóm chữ số và dấu âm trong Java

## 1. Vì sao cùng một con số lại có nhiều cách viết?

Giá trị số và cách **hiển thị** số là hai khái niệm khác nhau. Ví dụ, một giá trị toán học có độ lớn một triệu hai trăm ba mươi bốn nghìn năm trăm sáu mươi bảy và 89 phần trăm có thể được trình bày như sau:

| Locale | Ví dụ hiển thị số âm | Dấu thập phân | Dấu nhóm chữ số |
| --- | --- | --- | --- |
| `Locale.US` (tiếng Anh, Hoa Kỳ) | `-1,234,567.89` | `.` | `,` |
| `Locale.GERMANY` (tiếng Đức, Đức) | `-1.234.567,89` | `,` | `.` |
| `Locale.FRANCE` (tiếng Pháp, Pháp) | `-1 234 567,89` | `,` | Dấu cách hẹp không ngắt dòng trong nhiều JDK hiện nay |
| `Locale.forLanguageTag("vi-VN")` (tiếng Việt, Việt Nam) | `-1.234.567,89` trên cấu hình CLDR phổ biến | `,` | `.` |

Dòng tiếng Pháp ở trên sử dụng dấu cách **U+202F NARROW NO-BREAK SPACE** giữa các nhóm. Nó có thể trông giống khoảng trắng ASCII `U+0020` nhưng **khác ký tự**. Quy tắc và ký hiệu locale có thể thay đổi theo phiên bản dữ liệu địa phương hóa hoặc bộ cung cấp locale của môi trường chạy; không dùng các ví dụ minh họa này như định dạng bất biến cho mọi máy và mọi phiên bản JDK.

Trong Java, `double amount = -1234567.89;` chứa một giá trị nhị phân; biến `amount` **không tự mang dấu phân cách `,` hoặc `.` của quốc gia**. Dấu thập phân, dấu nhóm, chữ số và quy ước âm xuất hiện khi ta chuyển số thành chuỗi hoặc phân tích một chuỗi do người dùng nhập.

Bài học này tập trung vào bốn yếu tố:

1. **Locale:** bộ quy tắc ngôn ngữ/vùng áp dụng khi trình bày và đọc chuỗi số.
2. **Dấu thập phân:** tách phần nguyên và phần lẻ.
3. **Dấu nhóm chữ số:** giúp người đọc phân biệt hàng nghìn, triệu...; **không thay đổi giá trị số**.
4. **Dấu âm/định dạng âm:** dấu `-`, đôi khi ký tự âm khác hoặc ngoặc đơn, tùy ngữ cảnh và pattern.

Mục tiêu là tạo ứng dụng hiểu đúng đầu vào của người dùng, hiển thị đúng ngôn ngữ và **không nhầm chuỗi dành cho giao diện với chuỗi trao đổi dữ liệu máy**.

## 2. `Locale` là gì và nên được chọn như thế nào?

Lớp `java.util.Locale` mô tả lựa chọn ngôn ngữ, vùng và một số đặc trưng văn hóa dùng cho chức năng địa phương hóa. Nó **không** chứa giá trị số và **không** tự thay đổi cách Java lưu `int`, `double` hay `BigDecimal`.

```java
import java.util.Locale;

Locale us = Locale.US;
Locale german = Locale.GERMANY;
Locale french = Locale.FRANCE;
Locale vietnamese = Locale.forLanguageTag("vi-VN");
```

Locale của chương trình có thể đến từ cài đặt môi trường hoặc được chỉ định tường minh. Ví dụ:

```java
Locale configured = Locale.getDefault();
```

`Locale.getDefault()` phụ thuộc máy/JVM. Vì vậy không nên dùng locale mặc định trong giao thức hay test mà kết quả phải ổn định giữa nhiều môi trường.

### Quy tắc chọn locale

- **Hiển thị cho người dùng:** lấy locale của tài khoản/giao diện, hoặc locale người dùng chọn. Không mặc định mọi người ở Việt Nam đều nhập số theo cùng một chuẩn nếu yêu cầu ứng dụng khác.
- **Đọc chuỗi người dùng nhập:** dùng đúng locale/quy ước mà ô nhập đã thông báo.
- **Định dạng theo chuẩn giao tiếp giữa máy:** dùng định dạng độc lập locale theo hợp đồng, chẳng hạn chữ số ASCII với dấu thập phân `.` và không có dấu nhóm; không dùng locale của máy chủ.
- **Unit test:** truyền `Locale` rõ ràng thay vì dựa vào `Locale.getDefault()`.

Các ký hiệu ở từng locale đến từ dữ liệu địa phương hóa. Một số thay đổi giữa các phiên bản JDK và CLDR có thể ảnh hưởng kết quả. Hãy test chính môi trường đích nếu ứng dụng yêu cầu chính xác ký tự định dạng, nhất là khi xử lý tài liệu tài chính, nhập liệu nghiêm ngặt hoặc so khớp chuỗi.

## 3. `NumberFormat` — API định dạng số theo locale

Lớp `java.text.NumberFormat` cung cấp các factory như `getNumberInstance(locale)`, `getIntegerInstance(locale)`, `getPercentInstance(locale)` và `getCurrencyInstance(locale)`.

Ví dụ định dạng số thực:

```java
import java.text.NumberFormat;
import java.util.Locale;

NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
NumberFormat de = NumberFormat.getNumberInstance(Locale.GERMANY);

us.setMinimumFractionDigits(2);
us.setMaximumFractionDigits(2);
de.setMinimumFractionDigits(2);
de.setMaximumFractionDigits(2);

System.out.println(us.format(-1234567.89)); // -1,234,567.89
System.out.println(de.format(-1234567.89)); // -1.234.567,89
```

Đây là các câu lệnh **minh họa trong một phương thức**, chưa phải chương trình độc lập. Phần 10 có một file Java 17 hoàn chỉnh.

Những cấu hình phổ biến:

| API | Tác dụng |
| --- | --- |
| `NumberFormat.getNumberInstance(locale)` | Factory định dạng số thông thường |
| `setGroupingUsed(boolean)` | Bật/tắt dùng dấu nhóm chữ số |
| `setMinimumFractionDigits(int)` | Ít nhất bao nhiêu chữ số phần lẻ khi format |
| `setMaximumFractionDigits(int)` | Nhiều nhất bao nhiêu chữ số phần lẻ khi format |
| `setMinimumIntegerDigits(int)` | Số chữ số tối thiểu phần nguyên |
| `setRoundingMode(RoundingMode)` | Chọn quy tắc làm tròn khi định dạng, trên formatter hỗ trợ |
| `format(number)` | Biến số thành chuỗi trình bày |
| `parse(text)` | Đọc chuỗi theo định dạng được cấu hình, trả `Number` |

Nếu tắt grouping:

```java
NumberFormat formatter = NumberFormat.getNumberInstance(Locale.GERMANY);
formatter.setGroupingUsed(false);
formatter.setMinimumFractionDigits(2);
formatter.setMaximumFractionDigits(2);

System.out.println(formatter.format(1234.5)); // 1234,50
```

**Định dạng không sửa giá trị đầu vào.** `setMaximumFractionDigits(2)` chỉ quyết định chuỗi xuất hiện bao nhiêu chữ số lẻ; nó không làm biến `double` hay `BigDecimal` đang giữ bị thay đổi.

### Format khác toán tử `+` nối chuỗi

Biểu thức `"value=" + someDouble` sử dụng biểu diễn mặc định của kiểu Java, không trình bày theo locale người dùng. Muốn localized number, hãy gọi `NumberFormat.format` rõ ràng.

`System.out.printf`/`String.format` cũng có overload nhận `Locale`:

```java
String us = String.format(Locale.US, "%,.2f", 1234567.89);
String de = String.format(Locale.GERMANY, "%,.2f", 1234567.89);

// us = "1,234,567.89"
// de = "1.234.567,89"
```

Đối với formatter `printf`, dấu `,` trong format specifier `%,.2f` là **cờ grouping**, không có nghĩa ký tự phân nhóm trong kết quả luôn là dấu phẩy ASCII. Ký hiệu thực tế phụ thuộc `Locale`.

## 4. `DecimalFormat` và `DecimalFormatSymbols`

`NumberFormat` có API chung; `java.text.DecimalFormat` cho phép kiểm soát pattern và ký hiệu. Với các locale phổ biến, `NumberFormat.getNumberInstance(locale)` thường trả một `DecimalFormat`, nhưng không nên ép kiểu tùy tiện khi không biết chính xác triển khai trả về.

Để tự tạo formatter theo pattern định nghĩa rõ:

```java
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

DecimalFormatSymbols symbols =
        DecimalFormatSymbols.getInstance(Locale.GERMANY);

DecimalFormat fmt = new DecimalFormat("#,##0.00", symbols);
System.out.println(fmt.format(12345.678)); // 12.345,68
```

Ở đây **cú pháp pattern chuẩn** có dấu `,` biểu thị vị trí grouping và dấu `.` biểu thị ranh giới phần lẻ. Chúng **không** có nghĩa chuỗi đầu ra luôn dùng đúng hai ký tự đó; `DecimalFormatSymbols` chuyển chúng sang ký hiệu thực tế của locale.

### Pattern thường dùng

| Pattern | Ý nghĩa |
| --- | --- |
| `0` | Bắt buộc một chữ số nếu cần |
| `#` | Chữ số tùy chọn |
| `#,##0` | Có nhóm nghìn, ít nhất một chữ số phần nguyên |
| `#,##0.00` | Nhóm nghìn, đúng 2 chữ số phần lẻ khi định dạng |
| `#,##0.###` | Nhóm nghìn, tối đa 3 chữ số phần lẻ, không ép thêm số 0 |
| `#,##0.00;(#,##0.00)` | Pattern dương trước dấu `;`, pattern âm sau dấu `;` hiển thị trong ngoặc |

Ví dụ:

```java
DecimalFormat accounting = new DecimalFormat(
        "#,##0.00;(#,##0.00)",
        DecimalFormatSymbols.getInstance(Locale.US));

System.out.println(accounting.format(-1234.5)); // (1,234.50)
```

Nếu định nghĩa **negative subpattern**, pattern có thể tự áp dụng ngoặc hoặc dấu hiệu riêng cho giá trị âm. Đừng mặc định mọi chuỗi âm phải có dấu gạch ngang đứng đầu.

### Lấy ký tự phân cách và dấu âm

```java
DecimalFormatSymbols symbols =
        DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN"));

char decimal = symbols.getDecimalSeparator();
char grouping = symbols.getGroupingSeparator();
char minus = symbols.getMinusSign();
```

`getDecimalSeparator()`, `getGroupingSeparator()` và `getMinusSign()` trả các ký tự theo dữ liệu locale của JVM. Ví dụ trên nhiều JDK có dữ liệu CLDR hiện đại, locale `vi-VN` dùng dấu `,` cho thập phân và dấu `.` cho grouping.

Nếu cần debug ký tự ẩn hoặc dễ nhầm:

```java
System.out.printf(Locale.ROOT, "U+%04X%n", (int) grouping);
```

Cùng là **dấu âm**, ký tự U+002D HYPHEN-MINUS (`-`) và U+2212 MINUS SIGN (`−`) không phải cùng code point. Khi một số hiển thị không parse được, hãy xem ký hiệu thực tế thay vì chỉ nhìn mắt thường.

`DecimalFormatSymbols` có các setter như `setDecimalSeparator(char)`, `setGroupingSeparator(char)`, `setMinusSign(char)` để điều chỉnh biểu diễn **nếu ứng dụng có yêu cầu riêng**. Không thay thế mù quáng ký tự trong một chuỗi bất kỳ: dấu `.` có thể đang là thập phân trong một locale nhưng là dấu nhóm trong locale khác.

### `applyPattern` và `applyLocalizedPattern`

`DecimalFormat.applyPattern` nhận **cú pháp pattern chuẩn**; `applyLocalizedPattern` nhận pattern theo ký hiệu địa phương hóa. Trộn lẫn hai dạng có thể khiến dấu phân cách bị hiểu sai.

Nếu không cần pattern đặc thù, `NumberFormat.getNumberInstance(locale)` thường dễ bảo trì hơn tự tạo một bộ ký hiệu.

## 5. Dấu thập phân và dấu nhóm không thể suy đoán bằng cách đếm dấu

Xét các chuỗi:

| Chuỗi | Cách hiểu có thể có |
| --- | --- |
| `"1,234"` | Một nghìn hai trăm ba mươi bốn ở US, hoặc một phẩy hai ba bốn ở Germany |
| `"1.234"` | Một phẩy hai ba bốn ở US, hoặc một nghìn hai trăm ba mươi bốn ở Germany |
| `"1 234,50"` | Có thể biểu diễn grouping bằng dấu cách ở một số cách nhập, nhưng loại ký tự cách rất quan trọng |
| `"1 234,50"` | Dùng U+202F để nhóm; khác dấu cách ASCII |
| `"-1,234.50"` | Dạng US của một số âm |
| `"-1.234,50"` | Dạng Đức hoặc Việt Nam phổ biến của cùng giá trị âm |

Một hàm “chuẩn hóa” ngây thơ:

```java
text = text.replace(".", "").replace(",", ".");
```

chỉ tình cờ hoạt động với **một tập quy tắc đầu vào đã biết**, và sẽ biến `"1,234.50"` thành `"1.23450"` (sai khoảng một nghìn lần) nếu áp dụng không đúng locale.

Cách khác cũng sai là xóa mọi dấu cách, bởi trong Unicode có nhiều dạng khoảng trắng và dấu nhóm được dùng theo locale. Hơn nữa, chuỗi dữ liệu có thể chứa ký tự lạ cần **từ chối**, không nên im lặng biến đổi thành một giá trị số khác.

**Đừng suy đoán locale từ chuỗi nhập một cách tùy ý.** Hãy yêu cầu ứng dụng biết locale từ UI/config/schema; nếu hỗ trợ nhiều kiểu nhập, cần quy tắc xác định, mô tả mơ hồ và validation rõ ràng.

## 6. Parse số theo locale: `NumberFormat.parse` không giống `Double.parseDouble`

Parser primitive:

```java
Double.parseDouble("1.5"); // 1.5
```

thường dùng dấu `.` theo cú pháp Java, **không tự chuyển sang dấu `,` vì máy đang đặt locale Germany**. Chuỗi `"1,5"` không hợp lệ với `Double.parseDouble`.

Khi chuỗi đến từ giao diện người dùng kiểu Đức:

```java
import java.text.NumberFormat;
import java.util.Locale;

NumberFormat de = NumberFormat.getNumberInstance(Locale.GERMANY);
Number value = de.parse("1.234,50"); // 1234.5
```

`NumberFormat.parse` trả một `Number`, không mặc định là `Double` trong mọi trường hợp; kiểu thực tế phụ thuộc formatter và chuỗi. Giá trị `doubleValue()` có thể có sai số do biểu diễn nhị phân.

**Không trộn formatter của một locale với chuỗi của locale khác.** Ví dụ, formatter US gặp `"1.234,50"` có thể phân tích thành `1.234` rồi dừng ở dấu `,`/phần dư, thay vì ném lỗi. Nếu hệ thống chỉ kiểm tra “không ném exception”, nó có thể âm thầm nhận giá trị sai.

### Cảnh báo: có thể parse được *một phần* chuỗi

`NumberFormat.parse(String)` không bắt buộc tiêu thụ hết ký tự nguồn. Ví dụ với locale Germany:

```java
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

DecimalFormat parser =
        (DecimalFormat) NumberFormat.getNumberInstance(Locale.GERMANY);

ParsePosition position = new ParsePosition(0);
Number result = parser.parse("1.234,50xyz", position);

System.out.println(result);              // 1234.5
System.out.println(position.getIndex()); // 8
```

Giá trị `1234.5` nhìn có vẻ parse thành công, nhưng chuỗi có thêm `"xyz"` và **chưa được đọc hết**. Muốn coi toàn bộ chuỗi là một số hợp lệ, phải kiểm tra `position.getIndex() == text.length()` và trường hợp parser trả `null`.

Hàm kiểm tra này chỉ đảm bảo **parser đã tiêu thụ hết**, không đảm bảo cách đặt grouping hợp chuẩn. `DecimalFormat` có thể parse khá dễ dãi với nhóm chữ số sai hoặc ký hiệu ở vị trí không chuẩn; nếu đầu vào cần nghiêm ngặt, xem mục 8.

## 7. Parse decimal thành `BigDecimal` để không làm tròn sang binary floating point

Khi nguồn là giá trị decimal cần bảo toàn chữ số (ví dụ dữ liệu tài chính hoặc các con số trong báo cáo), hãy cân nhắc `BigDecimal`.

Với đầu vào **đã chuẩn hóa sang decimal ASCII**:

```java
import java.math.BigDecimal;

BigDecimal amount = new BigDecimal("1234.50");
// Bảo toàn chính xác giá trị decimal 1234.50 và scale = 2.
```

Với đầu vào localized, `DecimalFormat` có thể được yêu cầu trả `BigDecimal`:

```java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

DecimalFormat parser =
        (DecimalFormat) NumberFormat.getNumberInstance(Locale.GERMANY);

parser.setParseBigDecimal(true);
String text = "1.234,50";
ParsePosition position = new ParsePosition(0);
Number n = parser.parse(text, position);

if (!(n instanceof BigDecimal amount)
        || position.getIndex() != text.length()) {
    throw new IllegalArgumentException("Invalid decimal input");
}

// amount là BigDecimal(1234.50), không phải một double gần đúng.
```

Đoạn trên sử dụng **pattern matching `instanceof` của Java 17**, hợp lệ từ Java 16 trở lên. `setParseBigDecimal(true)` là API của `DecimalFormat`, không phải của lớp cơ sở `NumberFormat`.

Ngoại lệ cần biết: parser có thể trả các đối tượng biểu diễn giá trị đặc biệt như vô cực, `NaN` hoặc `-0.0` theo các quy tắc riêng dù bật `parseBigDecimal`. Vì vậy kiểm tra `n instanceof BigDecimal` là cần thiết nếu hợp đồng chỉ cho phép **decimal hữu hạn có giá trị số**.

Đối với số âm kiểu `-0.00`, `BigDecimal` không bảo toàn khái niệm IEEE-754 *negative zero* như `double`; nếu dấu âm trên số 0 mang ý nghĩa nghiệp vụ, cần chính sách biểu diễn khác hoặc giữ chuỗi nguồn.

## 8. Xác thực đầu vào chặt chẽ hơn: locale, grouping và chữ số

### 8.1. Một parser `BigDecimal` an toàn cho toàn bộ chuỗi

Lưu phương thức này trong một class, với các import tương ứng:

```java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

static BigDecimal parseLocalizedDecimal(
        String text, Locale locale) {

    if (text == null || locale == null) {
        throw new IllegalArgumentException("Missing input or locale");
    }

    DecimalFormat parser =
            (DecimalFormat) NumberFormat.getNumberInstance(locale);
    parser.setParseBigDecimal(true);

    ParsePosition pos = new ParsePosition(0);
    Number number = parser.parse(text, pos);

    if (!(number instanceof BigDecimal)
            || pos.getIndex() != text.length()) {
        throw new IllegalArgumentException("Invalid decimal: " + text);
    }

    return (BigDecimal) number;
}
```

Phương thức này:

- Không phụ thuộc locale mặc định: nhận `Locale` tường minh.
- Không âm thầm lấy giá trị `double` khi muốn decimal chính xác.
- Từ chối phần đuôi không parse được.
- Không chấp nhận `null` như một số.

**Giới hạn quan trọng:** nó chưa phải bộ parser **nghiêm ngặt về grouping**. Một số triển khai `DecimalFormat` vẫn có thể chấp nhận chuỗi có nhóm đặt sai vị trí; việc parser tiêu thụ hết chuỗi không chứng minh kiểu nhóm của chuỗi đó đúng mẫu dành cho người dùng.

### 8.2. Cần nghiêm ngặt dấu nhóm thì làm thế nào?

Nếu bài toán yêu cầu chỉ chấp nhận đúng mẫu (ví dụ `1.234,50` hợp lệ, nhưng `12.34,50` phải bị từ chối), hãy quy định chính xác cấu trúc nhóm của miền đầu vào. Các lựa chọn gồm:

1. **Bắt buộc định dạng nhập cố định** (ví dụ chỉ `1234.50` dạng ASCII không grouping) và parse bằng kiểu decimal chuẩn.
2. **Dùng regex được thiết kế theo một locale/định dạng cụ thể** để kiểm tra cấu trúc dấu nhóm, dấu âm và chữ số trước khi parse. Không dùng một regex duy nhất cho mọi locale.
3. **Dùng parser có chế độ strict phù hợp trên môi trường Java đích**, sau khi kiểm tra API/version, hoặc kiểm tra bổ sung theo hợp đồng ứng dụng.
4. **Nếu chấp nhận giá trị biểu diễn lại ở dạng canonical**, có thể format rồi so khớp theo quy tắc được công bố, nhưng cách này cũng cần xử lý leading zeros, trailing zeros, scale, negative zero, khoảng trắng và các dạng nhập tương đương.

Với ví dụ tiếng Đức đơn giản, quy tắc nhập có thể cho phép:

```text
1234,50
1.234,50
-1.234,50
```

nhưng từ chối:

```text
12.34,50       (nhóm không đúng 3 chữ số)
1,234.50       (đổi chỗ dấu decimal/grouping)
1.234,50xyz    (phần đuôi không phải số)
```

**Không nên quảng cáo `NumberFormat.parse` tự xác thực chặt cấu trúc**; parsing thường mang tính dung thứ để đọc đầu vào người dùng, không phải validator đầy đủ cho schema tài chính hoặc giao thức.

### 8.3. Unicode và các ký tự dễ nhầm

Các chuỗi có thể hiển thị gần giống nhau nhưng chứa những ký tự khác nhau:

- `-` U+002D so với `−` U+2212 cho dấu âm.
- ` ` U+0020 so với U+00A0 NO-BREAK SPACE, U+202F NARROW NO-BREAK SPACE cho dấu nhóm.
- `0`–`9` ASCII so với chữ số theo hệ chữ khác.
- `.` U+002E so với `,` U+002C; một số ký tự toàn chiều rộng có hình dạng tương tự.

Nếu cần kiểm tra vì sao cùng một chuỗi “trông giống nhau” mà parse ra khác, in code point thay vì chỉ so sánh ảnh hiển thị:

```java
String text = "1\u202F234,50";
text.codePoints().forEach(cp ->
        System.out.printf(Locale.ROOT, "U+%04X ", cp));
// U+0031 U+202F U+0032 U+0033 U+0034 U+002C U+0035 U+0030
```

`String.codePoints()` xử lý cả ký tự Unicode ngoài BMP; đây là cách quan sát ký tự chính xác hơn giả định mỗi chữ nhìn thấy là một ký tự `char`.

Không dùng `String.replace` theo kiểu “xóa mọi khoảng trắng, dấu phẩy, dấu chấm” để sửa các chuỗi không tin cậy. Nó có thể biến lỗi dữ liệu thành giá trị số khác mà không báo cho người dùng.

## 9. Dấu âm, ngoặc và số 0 âm

### 9.1. Dấu âm là thuộc tính của cách biểu diễn văn bản

Một số âm có thể hiển thị bằng dấu âm phía trước:

```text
-1,234.50
-1.234,50
```

hoặc, trong một số định dạng kế toán, bằng dấu ngoặc:

```text
(1,234.50)
```

`DecimalFormat` cho phép mô tả pattern âm rõ ràng:

```java
DecimalFormat accounting = new DecimalFormat(
        "#,##0.00;(#,##0.00)",
        DecimalFormatSymbols.getInstance(Locale.US));

System.out.println(accounting.format(-1234.5)); // (1,234.50)
```

Không thể suy ra giá trị âm chỉ bằng cách kiểm tra chuỗi có bắt đầu bằng `"-"` hay không. Pattern có thể dùng ngoặc, ký hiệu âm khác, thậm chí tiền tố/suffix tùy định dạng. Nếu người dùng có thể nhập dạng kế toán, parser phải hỗ trợ đúng hợp đồng.

### 9.2. Dấu âm ASCII không phải mọi ký tự trông như dấu trừ

Trong một số locale/cấu hình, ký tự U+2212 MINUS SIGN (`−`) có thể xuất hiện thay U+002D HYPHEN-MINUS (`-`). Hãy lấy `DecimalFormatSymbols.getMinusSign()` thay vì đoán. Cẩn thận khi copy số từ PDF, website hoặc tài liệu có dàn trang vì dấu âm có thể khác ký tự bàn phím thông thường.

### 9.3. `-0.0` khác `+0.0` ở dấu bit

Kiểu `double` có cả `+0.0` và `-0.0`:

```java
double negativeZero = -0.0;

System.out.println(negativeZero == 0.0); // true
System.out.println(1.0 / negativeZero);   // -Infinity
```

Định dạng theo locale có thể hiện dấu `-0,00` hoặc `-0.00` tùy cấu hình. Khi parsing vào `BigDecimal`, dấu âm của zero theo IEEE 754 không được bảo toàn như với `double`; cần ghi chú hoặc lưu riêng nếu nghiệp vụ yêu cầu phân biệt.

**Lưu ý về làm tròn khi format:** một giá trị âm rất nhỏ như `-0.0001` có thể được format ở độ chính xác hai chữ số phần lẻ thành dạng hiển thị `-0.00`. Chuỗi đó có thể khiến người dùng hiểu số đúng bằng 0; cần chọn độ chính xác hiển thị phù hợp với nghiệp vụ.

## 10. Demo Java 17 đầy đủ và đầu ra kiểm chứng

Lưu dưới tên `LocaleNumberDemo.java`:

```java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleNumberDemo {

    private static BigDecimal parseAll(String text, Locale locale) {
        DecimalFormat parser =
                (DecimalFormat) NumberFormat.getNumberInstance(locale);
        parser.setParseBigDecimal(true);

        ParsePosition pos = new ParsePosition(0);
        Number number = parser.parse(text, pos);

        if (!(number instanceof BigDecimal)
                || pos.getIndex() != text.length()) {
            throw new IllegalArgumentException("Invalid input");
        }
        return (BigDecimal) number;
    }

    public static void main(String[] args) {
        NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
        NumberFormat de = NumberFormat.getNumberInstance(Locale.GERMANY);

        for (NumberFormat format : new NumberFormat[] { us, de }) {
            format.setMinimumFractionDigits(2);
            format.setMaximumFractionDigits(2);
        }

        System.out.println("US: " + us.format(-1234567.89));
        System.out.println("DE: " + de.format(-1234567.89));

        BigDecimal parsed = parseAll("1.234,50", Locale.GERMANY);
        System.out.println("DE parsed: " + parsed.toPlainString());

        DecimalFormat accounting = new DecimalFormat(
                "#,##0.00;(#,##0.00)",
                DecimalFormatSymbols.getInstance(Locale.US));
        System.out.println("Accounting: " + accounting.format(-1234.5));

        String withJunk = "1.234,50xyz";
        DecimalFormat parser =
                (DecimalFormat) NumberFormat.getNumberInstance(Locale.GERMANY);
        ParsePosition pos = new ParsePosition(0);
        Number partial = parser.parse(withJunk, pos);
        System.out.println("Partial value: " + partial);
        System.out.println("Consumed: " + pos.getIndex() + "/"
                + withJunk.length());

        System.out.println("US format: "
                + String.format(Locale.US, "%,.2f", 1234567.89));
        System.out.println("DE format: "
                + String.format(Locale.GERMANY, "%,.2f", 1234567.89));
    }
}
```

Biên dịch và chạy:

```bash
javac LocaleNumberDemo.java
java LocaleNumberDemo
```

Đầu ra trên một JDK 17 với dữ liệu locale tiêu chuẩn thường gặp:

```text
US: -1,234,567.89
DE: -1.234.567,89
DE parsed: 1234.50
Accounting: (1,234.50)
Partial value: 1234.5
Consumed: 8/11
US format: 1,234,567.89
DE format: 1.234.567,89
```

Đầu ra minh họa dùng US/Germany vì các ký hiệu cơ bản rõ ràng; nếu dùng `Locale.FRANCE` hoặc locale khác, **không nên hardcode dấu cách, dấu âm hay grouping** vào test xuyên phiên bản mà chưa kiểm tra dữ liệu CLDR/JDK đích.

### Vì sao ví dụ dùng `parseAll` thay vì `parse` trực tiếp?

`ParsePosition` ghi lại vị trí parse đã đi đến đâu. Khi chuỗi `"1.234,50xyz"` có 11 ký tự, parser Germany có thể đọc đúng `"1.234,50"` với độ dài 8 và để lại `"xyz"`. Nếu chỉ lấy `Number` và không kiểm tra vị trí, đầu vào sai bị chấp nhận.

Hàm `parseAll` loại tình huống này. Tuy nhiên, như đã giải thích ở mục 8, việc tiêu thụ hết chuỗi **không** đồng nghĩa nhóm hàng nghìn được validate nghiêm ngặt.

## 11. Phân biệt định dạng hiển thị và dữ liệu trao đổi

### Chuỗi cho người dùng

Với giao diện, hãy để formatter nhận `Locale` phù hợp:

```java
String display = NumberFormat
        .getNumberInstance(Locale.forLanguageTag("vi-VN"))
        .format(1234567.89);
```

Chuỗi `display` dùng để **hiển thị**; không coi nó là định dạng ổn định để lưu DB hoặc làm chữ ký số.

### Chuỗi cho máy hoặc giao thức cố định

Nếu hợp đồng yêu cầu decimal ASCII với dấu `.` và không grouping, dùng biểu diễn độc lập locale được quy định:

```java
String decimal = new BigDecimal("1234567.89").toPlainString();
// "1234567.89"

String floating = Double.toString(1234567.89);
// Dùng cú pháp số thực Java với '.' chứ không dùng grouping địa phương.
```

Chú ý `Double.toString` là biểu diễn thập phân có thể round-trip về cùng giá trị `double`, không bảo đảm mọi chữ số decimal ban đầu được lưu chính xác. Nếu cần tiền hay decimal chính xác, giữ nguồn trong `BigDecimal` với scale/quy tắc làm tròn phù hợp.

Giao thức máy có thể có chuẩn riêng (ví dụ JSON number không chấp nhận các giá trị không hữu hạn như `NaN` và `Infinity` theo chuẩn JSON thông thường). Hãy tuân thủ **đúng grammar của giao thức**, không dùng chuỗi mà người dùng thấy trên màn hình thay cho serialization chuẩn.

### Không dựa vào locale mặc định của server

```java
// Dễ thay đổi tùy máy chạy:
String unpredictable = String.format("%,.2f", 1234.5);

// Ổn định hơn khi giao diện đã biết locale:
String explicit = String.format(Locale.US, "%,.2f", 1234.5);
```

Nếu phần mềm chạy trên nhiều container/server, sử dụng locale mặc định khác nhau có thể dẫn đến báo cáo, test snapshot, CSV và các chuỗi lưu trữ sai khác mà không có thay đổi logic nghiệp vụ.

CSV còn có dấu phân tách cột riêng. Nếu định dạng số sử dụng dấu `,` làm dấu thập phân hoặc grouping, cần quoting và parsing CSV đúng chuẩn; đừng tự xây CSV bằng cách nối thẳng các chuỗi số localized bằng dấu phẩy.

## 12. Sai lầm thường gặp

1. **Dùng `Double.parseDouble("1,5")` rồi kỳ vọng kết quả `1.5`.** API này không phụ thuộc locale, thường yêu cầu dấu `.` theo cú pháp số thực Java. Dùng `NumberFormat` với locale phù hợp cho dữ liệu giao diện.
2. **Nhìn thấy `"1,234"` và suy ra chắc là `1234`.** Ở một số locale dấu phẩy là decimal. Cần có locale hoặc hợp đồng đầu vào.
3. **Thay mọi dấu `.` bằng rỗng rồi `,` bằng `.`.** Dễ làm sai chuỗi theo US hoặc chuỗi có dấu nhóm khác.
4. **Không đặt `Locale` khi format trong ứng dụng đa quốc gia.** Kết quả phụ thuộc máy chạy thay vì lựa chọn người dùng.
5. **Dùng `NumberFormat.parse` nhưng không xác nhận đọc hết chuỗi.** Parser có thể trả số hợp lệ từ một tiền tố, bỏ lại ký tự rác.
6. **Coi parse hết chuỗi là đã validate grouping.** `DecimalFormat` có thể nhận cách nhóm không đúng; cần quy tắc kiểm tra định dạng nếu nghiệp vụ yêu cầu nghiêm ngặt.
7. **Bỏ qua dấu cách Unicode.** U+202F và U+0020 nhìn tương tự nhưng không phải cùng ký tự.
8. **Coi mọi dấu âm là dấu `-` ASCII đứng đầu.** Locale hoặc pattern kế toán có thể dùng U+2212 hoặc ngoặc.
9. **Tự viết `"."` và `","` trong mẫu output mà không hiểu pattern.** Trong `DecimalFormat` pattern chuẩn, ký hiệu là placeholder; ký tự thực tế do `DecimalFormatSymbols` quyết định.
10. **Nghĩ format hai chữ số lẻ làm thay đổi dữ liệu số.** Format làm tròn **chuỗi hiển thị**, không đổi đối tượng `BigDecimal` hoặc giá trị số gốc.
11. **Dùng `double` để lưu decimal tiền chỉ vì đã parse theo locale.** Vẫn có vấn đề chính xác nhị phân. Dùng `BigDecimal` khi cần decimal exact.
12. **Giữ formatter `DecimalFormat` dùng chung giữa nhiều thread và sửa cấu hình trong lúc format.** `NumberFormat`/`DecimalFormat` là mutable và không được bảo đảm thread-safe; dùng instance riêng hoặc đồng bộ hóa thích hợp.
13. **So sánh bằng chuỗi đã format.** Hai giá trị khác nhau có thể cùng format thành một chuỗi sau làm tròn; dùng so sánh số và quy tắc tolerance/rounding theo nghiệp vụ.
14. **Dùng kết quả format localized để tạo khóa, checksum hoặc giao thức API.** Locale có thể thay đổi cách viết; dùng canonical representation rõ ràng.
15. **Bỏ qua dấu âm khi giá trị gần 0.** `-0.0` và số âm nhỏ bị format thành `-0,00` có thể mang thông tin dấu; cần chính sách hiển thị phù hợp.
16. **Cho rằng `NumberFormat` format/parse luôn đối xứng tuyệt đối cho mọi số.** Việc giới hạn chữ số lẻ, làm tròn, grouping và parser dễ dãi có thể khiến round-trip không phục hồi nguyên vẹn giá trị ban đầu.

## 13. Bài tập tự kiểm tra

**Câu 1.** Vì sao `"1.234,56"` có thể có nghĩa một nghìn hai trăm ba mươi bốn phẩy năm sáu trong `Locale.GERMANY`, nhưng không phải cùng ý nghĩa trong `Locale.US`?

**Đáp án:** Germany dùng dấu `.` làm grouping và dấu `,` làm decimal; US dùng ngược lại. Không thể suy ra giá trị chính xác chỉ từ chuỗi nếu chưa biết locale.

**Câu 2.** Muốn hiển thị `1234567.89` theo tiếng Đức với hai chữ số lẻ và nhóm nghìn, dùng API nào?

**Đáp án:** `NumberFormat.getNumberInstance(Locale.GERMANY)`, cấu hình `setMinimumFractionDigits(2)` và `setMaximumFractionDigits(2)`, rồi `format`. Kết quả minh họa: `"1.234.567,89"`.

**Câu 3.** Vì sao `NumberFormat.getNumberInstance(Locale.GERMANY).parse("1.234,50xyz")` có thể không ném exception?

**Đáp án:** Parser có thể chấp nhận một tiền tố đúng và dừng ở phần đuôi. Dùng `ParsePosition`, yêu cầu `pos.getIndex() == text.length()`, và kiểm tra kết quả không `null`.

**Câu 4.** Sau khi parse toàn bộ chuỗi thành `BigDecimal` bằng `DecimalFormat.setParseBigDecimal(true)`, có chắc dấu nhóm đúng cấu trúc chưa?

**Đáp án:** Chưa. Parse tiêu thụ hết không đồng nghĩa validation nghiêm ngặt grouping. Nếu cần kiểm tra đúng ba chữ số mỗi nhóm, định nghĩa validator tương ứng với locale/quy ước input.

**Câu 5.** Tại sao `new BigDecimal("1234.50")` phù hợp hơn `Double.parseDouble("1234.50")` nếu nghiệp vụ phải giữ đúng decimal scale và giá trị?

**Đáp án:** `BigDecimal(String)` giữ đúng số thập phân và scale của chuỗi; `double` là nhị phân gần đúng, không phải mọi decimal đều biểu diễn chính xác.

**Câu 6.** `"1 234,50"` và `"1 234,50"` có khác nhau không, dù nhìn khá giống?

**Đáp án:** Có thể khác: ký tự nhóm ở chuỗi đầu là U+202F, trong chuỗi thứ hai là khoảng trắng ASCII U+0020. Cần quan sát code point và cấu hình locale/parser, không đoán bằng mắt.

**Câu 7.** Vì sao định dạng âm `"(" + number + ")"` không nên được parse bằng cách chỉ tìm dấu `-` ở đầu?

**Đáp án:** Dạng kế toán dùng ngoặc biểu thị âm mà không có dấu `-` đầu chuỗi. Formatter/parser phải dùng cùng pattern và hợp đồng dữ liệu.

**Câu 8.** Có nên gọi `NumberFormat.format` theo locale người dùng để tạo chuỗi gửi trong REST API hay lưu số vào cơ sở dữ liệu không?

**Đáp án:** Thường không. Cần dùng schema và serialization chuẩn, độc lập locale. Chuỗi localized chỉ dành cho hiển thị hoặc trường nhập văn bản mà hợp đồng ghi rõ locale.

**Câu 9.** `NumberFormat` có thể được lưu làm một singleton mutable dùng cho tất cả request và đổi locale/precision theo từng request không?

**Đáp án:** Không nên. `NumberFormat`/`DecimalFormat` mutable, không có bảo đảm thread-safety. Tạo formatter riêng cho tác vụ hoặc quản lý truy cập và cấu hình cẩn thận.

**Ghi nhớ:** Locale quyết định cách số được viết và đọc cho con người: ký hiệu thập phân, nhóm chữ số, dấu âm và có thể cả bộ chữ số. Dùng `NumberFormat`/`DecimalFormat` với `Locale` tường minh; giữ số trong kiểu phù hợp; validate chuỗi nhập đủ chặt; đừng nhầm biểu diễn hiển thị với định dạng lưu trữ hay trao đổi máy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-009">Phân biệt chữ số tối thiểu, tối đa và độ chính xác giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt chữ số tối thiểu, tối đa và độ chính xác giá trị trong Java

Một hệ thống có thể yêu cầu in **tối thiểu hai chữ số sau dấu thập phân**, in **không quá bốn chữ số** hoặc tính toán với **ba chữ số có nghĩa**. Ba khái niệm này không đồng nhất. Java có các API để kiểm soát **chuỗi hiển thị** như `DecimalFormat`, `String.format`, `printf`, và những API tạo **giá trị thập phân đã làm tròn** như `BigDecimal.setScale` hay `MathContext`.

Điều quan trọng nhất: **định dạng ít chữ số không làm giảm hay tăng độ chính xác của giá trị gốc đang được lưu**. Ngược lại, một giá trị `double` đã chứa sai số nhị phân vẫn sai số như cũ ngay cả khi in ra `0.30`. Bài Knowledge này thuộc phần **Biểu diễn và định dạng** trong Java Core, tập trung vào minimum/maximum digits, precision, scale, width và cách tránh nhầm lẫn khi lưu/xuất dữ liệu.

## 1. Khái niệm cốt lõi

| Khái niệm | Mục tiêu | Ví dụ |
| --- | --- | --- |
| Minimum fraction digits | Tối thiểu bao nhiêu chữ số *sau dấu thập phân* khi in | `1.2` → `1.20` |
| Maximum fraction digits | Tối đa bao nhiêu chữ số sau dấu thập phân khi in | `1.234567` → `1.2346` khi max = 4 |
| Minimum integer digits | Đệm đủ chữ số *phần nguyên* khi in | `12.5` → `0012.50` |
| Maximum integer digits | Giới hạn số chữ số nguyên khi định dạng | Có thể **bỏ các chữ số cao**, rất nguy hiểm |
| Formatter width | Độ rộng tối thiểu **toàn chuỗi** gồm dấu, số 0 và khoảng trắng | `%8.2f` cho `"    1.20"` |
| Formatter precision | Số chữ số sau dấu thập phân của `%f` hoặc số chữ số có nghĩa của `%g` | `%.2f` khác `%.2g` |
| `BigDecimal.scale()` | Số mũ thập phân trong giá trị `unscaledValue × 10^(-scale)` | `12.3456` có scale 4 |
| `BigDecimal.precision()` | Số chữ số thập phân có nghĩa của unscaled value | `12.3456` có precision 6 |
| `MathContext.precision` | Giới hạn chữ số có nghĩa của **kết quả phép toán BigDecimal** | Làm tròn `12.3456` thành `12.3` khi precision 3 |

Các thuộc tính format quyết định hình thức xuất ra; độ chính xác số học còn phụ thuộc kiểu dữ liệu, biểu diễn đầu vào, thứ tự các phép tính và chính sách làm tròn. Một biểu thức chứa 10 chữ số sau dấu phẩy không tự bảo đảm 10 chữ số đó đều đáng tin về mặt đo lường.

## 2. `DecimalFormat`: ký tự `0` bắt buộc, `#` tùy chọn

`java.text.DecimalFormat` tạo chuỗi biểu diễn số theo một pattern. Hai ký hiệu cơ bản:

- `0`: vị trí chữ số bắt buộc; có thể in thêm số 0 nếu thiếu.
- `#`: vị trí tùy chọn; không cần in số 0 thừa.
- `.`: dấu phân cách thập phân ở biểu thức pattern.
- `,`: xác định nhóm chữ số, ký tự thực tế tùy `Locale` và `DecimalFormatSymbols`.

Ví dụ:

~~~java
DecimalFormatSymbols symbols =
        DecimalFormatSymbols.getInstance(Locale.ROOT);
DecimalFormat formatter = new DecimalFormat("0.00##", symbols);
formatter.setRoundingMode(RoundingMode.HALF_EVEN);

System.out.println(formatter.format(1.2));      // 1.20
System.out.println(formatter.format(1.234567)); // 1.2346
System.out.println(formatter.format(12));       // 12.00
~~~

Pattern `0.00##` có **min = 2** và **max = 4** chữ số phần thập phân. Số `1.2` được đệm thêm số 0 để in `1.20`, còn `1.234567` được làm tròn thành **chuỗi** `1.2346`.

Đọc các giới hạn ngay từ formatter:

~~~java
int minimum = formatter.getMinimumFractionDigits(); // 2
int maximum = formatter.getMaximumFractionDigits(); // 4
~~~

Pattern `0.0#` có min 1, max 2:

~~~java
DecimalFormat f = new DecimalFormat("0.0#", symbols);
f.format(1);     // "1.0"
f.format(1.23);  // "1.23"
f.format(1.234); // "1.23"
~~~

**Maximum khác minimum:** khi thiếu số lẻ, các vị trí `0` buộc xuất hiện; khi có ít chữ số lẻ, các vị trí `#` có thể bị bỏ. Nếu cần làm tròn phần dư, formatter tuân theo `RoundingMode` đã cấu hình. Nó không làm biến nguồn thay đổi.

## 3. Setter minimum/maximum fraction digits dùng khi nào?

Ngoài pattern, ta có thể cấu hình:

~~~java
DecimalFormat f = new DecimalFormat("0", symbols);
f.setMinimumFractionDigits(2);
f.setMaximumFractionDigits(4);
f.setRoundingMode(RoundingMode.HALF_EVEN);
// f.format(1.2)      -> "1.20"
// f.format(1.234567) -> "1.2346"
~~~

`getMinimumFractionDigits` và `getMaximumFractionDigits` giúp kiểm tra cấu hình cuối. `NumberFormat` duy trì tính nhất quán giữa minimum và maximum; khi đặt giá trị vượt ràng buộc còn lại, setter có thể điều chỉnh cả giới hạn đối diện. Vì vậy nếu cấu hình được thay đổi động, **đọc lại cả min lẫn max** để kiểm tra.

`DecimalFormat` là mutable và không bảo đảm an toàn khi chia sẻ cùng instance để dùng đồng thời giữa các thread mà không đồng bộ. Có thể tạo instance riêng cho mỗi khu vực sử dụng hoặc quản lý vòng đời phù hợp.

**Chú ý:** “max = 2” không có nghĩa kiểu `double` đã được lưu chính xác đến hai chữ số. Nó chỉ tạo biểu diễn trông như có nhiều nhất hai chữ số sau dấu thập phân.

## 4. Integer digits: đệm số 0 có thể đúng, cắt chữ số cao rất nguy hiểm

`minimumIntegerDigits` quy định số vị trí tối thiểu phần nguyên:

~~~java
DecimalFormat padded = new DecimalFormat("0000.00", symbols);
System.out.println(padded.format(12.5)); // 0012.50
~~~

Trong ví dụ, phần nguyên `12` được đệm thêm hai số 0. Đây là trình bày, không chuyển kiểu số thành một object mang 4 chữ số nguyên cố định. Nếu `0012` là mã định danh và các số 0 đầu mang ý nghĩa, nên lưu mã **dạng chuỗi**, không chỉ lưu `int 12`.

`maximumIntegerDigits` là một bẫy khác. Khi bị giới hạn, `DecimalFormat` có thể **loại bỏ các chữ số có trọng số cao**:

~~~java
DecimalFormat limited = new DecimalFormat("0", symbols);
limited.setMaximumIntegerDigits(3);
System.out.println(limited.format(12345)); // 345
~~~

Không dùng `setMaximumIntegerDigits(3)` để “làm tròn” hay “thu gọn” số lớn. Nếu màn hình quá hẹp, hãy xử lý layout hoặc dùng một quy ước rõ ràng (ví dụ biểu diễn theo nghìn kèm đơn vị), không âm thầm biến `12345` thành `345`.

Maximum integer digits cũng không dùng để kiểm tra overflow hay xác thực đầu vào. Hợp đồng giới hạn giá trị phải được kiểm tra trên **giá trị số gốc**, không phải chuỗi rút gọn.

## 5. `Formatter` width khác precision

`String.format` và `System.out.printf` dùng `java.util.Formatter`. Cú pháp cơ bản là `%[flags][width][.precision]conversion`:

| Format | Ý nghĩa | Kết quả ví dụ |
| --- | --- | --- |
| `%.2f` | Chính xác hai chữ số **sau dấu thập phân trong chuỗi** | `1.23456` → `"1.23"` |
| `%8.2f` | Tối thiểu 8 ký tự, 2 chữ số lẻ | `1.2` → `"    1.20"` |
| `%.3g` | Ba chữ số có nghĩa của chuỗi số thực (định dạng tổng quát) | `12345.678` → `"1.23e+04"` |
| `%d` | Số nguyên | Không dùng dot-precision như `%f` |

~~~java
String width = String.format(Locale.ROOT, "%8.2f", 1.2);
String fixed = String.format(Locale.ROOT, "%.2f", 1.23456);
String significant = String.format(Locale.ROOT, "%.3g", 12345.678);
~~~

Kết quả là `"    1.20"`, `"1.23"`, `"1.23e+04"` tương ứng.

**Width** là độ rộng tối thiểu theo ký tự của toàn trường in, kể cả dấu, số lẻ và khoảng trắng; nếu chuỗi số dài hơn width, formatter có thể in dài hơn chứ không mặc định cắt dữ liệu.

**Precision của `%f`** là chữ số sau dấu thập phân; **precision của `%g`** là số chữ số có nghĩa trong cách trình bày. Không dùng chúng thay thế cho nhau. Đặc biệt `%.2f` không hề nói rằng giá trị gốc `1.23456` đã bị gán lại thành số `1.23`.

Các format dùng `Locale` có thể chọn dấu chấm hoặc dấu phẩy theo vùng. Khi nội dung cần máy đọc với kết quả ổn định, hãy chỉ định một locale thích hợp như `Locale.ROOT`. Với giao diện cho người dùng, dùng locale đúng ngôn ngữ/thị trường.

## 6. Độ chính xác lưu trữ của `float` và `double`

`float`/`double` là dấu phẩy động nhị phân IEEE 754 với độ chính xác hữu hạn: binary32 có 24 bit precision phần trị, binary64 có 53 bit. Một số decimal như `0.1`, `0.2` không biểu diễn được chính xác bằng số lượng bit hữu hạn này.

~~~java
double sum = 0.1 + 0.2;

System.out.println(sum); // 0.30000000000000004
System.out.println(
        String.format(Locale.ROOT, "%.2f", sum)); // 0.30
System.out.println(sum); // vẫn 0.30000000000000004
~~~

`String.format` tạo `String` mới và **không sửa giá trị `double` đang lưu**. Vì vậy các so sánh, tổng, thuật toán downstream vẫn sử dụng giá trị gốc. Số chữ số hiển thị nhiều hay ít không tự sửa được sai số được tạo ra bởi biểu diễn nhị phân và thứ tự tính toán.

`Double.toString` tạo biểu diễn thập phân ngắn phù hợp round-trip theo hợp đồng API, chứ không in mọi chữ số thập phân của giá trị binary64 đang lưu. Có thể dùng `new BigDecimal(double)` để khảo sát khai triển decimal chính xác của giá trị bit, nhưng đó không phải cùng khái niệm với số decimal mà người dùng dự định nhập.

Khi số thực đi từ hệ thống này sang hệ thống khác, xuất dữ liệu với giới hạn chữ số tùy tiện có thể làm mất thông tin. Để round-trip một giá trị `double`, dùng `Double.toString` kết hợp parser phù hợp hoặc một giao thức có đảm bảo tương đương; đừng tự chọn `%.2f` chỉ vì thấy dễ đọc.

## 7. `BigDecimal.precision()` và `scale()` không phải min/max digits

`BigDecimal` lưu số decimal dựa trên unscaled integer và scale: `value = unscaledValue × 10^(-scale)`.

Với:

~~~java
BigDecimal number = new BigDecimal("12.3456");
System.out.println(number.precision()); // 6
System.out.println(number.scale());     // 4
~~~

- **`precision() = 6`**: sáu chữ số có nghĩa của giá trị unscaled theo quy tắc `BigDecimal`.
- **`scale() = 4`**: số mũ decimal cần dùng để diễn giải unscaled value thành `12.3456`.

Scale không luôn là “số chữ số sau dấu thập phân” theo nghĩa chuỗi: scale có thể bằng **0 hoặc âm**, ví dụ sau khi chuẩn hóa số có số 0 đuôi. Precision không phải là sai số thực tế của phép đo hoặc số chữ số cần trình bày. Các thuộc tính này nằm trong **giá trị BigDecimal**, còn `DecimalFormat` quản lý **chuỗi khi in**.

Nếu đo lường ghi “độ chính xác ±0.01”, đó là thông tin về thiết bị và sai số/độ bất định của phép đo; không tự suy ra từ `precision()`. Cần mô hình hóa ràng buộc này trong miền nghiệp vụ.

## 8. `setScale` so với `MathContext` precision

Một `BigDecimal` có thể được làm tròn và lưu thành giá trị mới theo hai tiêu chí:

~~~java
BigDecimal value = new BigDecimal("12.3456");

BigDecimal byFractionPosition =
        value.setScale(2, RoundingMode.HALF_UP); // 12.35

BigDecimal bySignificantDigits =
        value.round(new MathContext(3, RoundingMode.HALF_UP)); // 12.3
~~~

**`setScale(2, ...)`** giữ hai vị trí lẻ theo quy tắc làm tròn. **`MathContext(3, ...)`** giữ ba chữ số có nghĩa của kết quả, tính cả phần nguyên. Với số có độ lớn khác, vị trí làm tròn tương ứng sẽ thay đổi.

Đây là **thao tác lên giá trị**, khác `DecimalFormat.format`:

~~~java
BigDecimal original = new BigDecimal("12.3456");
DecimalFormat f = new DecimalFormat("0.00", symbols);
f.setRoundingMode(RoundingMode.HALF_UP);
String shown = f.format(original); // "12.35"
// original vẫn là 12.3456
~~~

`BigDecimal` immutable: `round` và `setScale` trả một object mới, object nguồn không bị thay đổi. Muốn ghi vào sổ sách giá trị có scale 2, phải **lưu object kết quả** chứ không chỉ format object gốc.

Quy tắc `RoundingMode.HALF_EVEN` và `HALF_UP` không luôn cho kết quả giống nhau ở điểm giữa. Nếu nghiệp vụ cần tiền tệ, thuế hoặc phân bổ, hãy xác định rõ thời điểm làm tròn và rounding mode. Với `RoundingMode.UNNECESSARY`, một phép chuyển cần làm tròn có thể ném `ArithmeticException` thay vì âm thầm làm mất thông tin.

## 9. Chương trình Java 17+ chạy độc lập và stdout chính xác

Tạo tệp `DigitFormatDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class DigitFormatDemo {
    public static void main(String[] args) {
        DecimalFormatSymbols symbols =
                DecimalFormatSymbols.getInstance(Locale.ROOT);
        DecimalFormat range = new DecimalFormat("0.00##", symbols);
        range.setRoundingMode(RoundingMode.HALF_EVEN);

        System.out.println("min fraction = "
                + range.getMinimumFractionDigits());
        System.out.println("max fraction = "
                + range.getMaximumFractionDigits());
        System.out.println("range 1.2 = " + range.format(1.2));
        System.out.println("range 1.234567 = "
                + range.format(1.234567));
        System.out.println("range 12 = " + range.format(12));

        DecimalFormat padded = new DecimalFormat("0000.00", symbols);
        System.out.println("padded 12.5 = " + padded.format(12.5));

        DecimalFormat optional = new DecimalFormat("0.0#", symbols);
        System.out.println("optional 1 = " + optional.format(1));
        System.out.println("optional 1.23 = " + optional.format(1.23));
        System.out.println("optional 1.234 = "
                + optional.format(1.234));

        DecimalFormat group = new DecimalFormat("#,##0.###", symbols);
        System.out.println("group 12345.6789 = "
                + group.format(12345.6789));

        System.out.println("width = |"
                + String.format(Locale.ROOT, "%8.2f", 1.2)
                + "|");
        System.out.println("f 1.23456 = "
                + String.format(Locale.ROOT, "%.2f", 1.23456));
        System.out.println("g 12345.678 = "
                + String.format(Locale.ROOT, "%.3g", 12345.678));

        BigDecimal original = new BigDecimal("12.3456");
        System.out.println("decimal original = " + original);
        System.out.println("formatted decimal = " + range.format(original));
        System.out.println("decimal after format = " + original);
        System.out.println("setScale(2) = "
                + original.setScale(2, RoundingMode.HALF_UP));
        System.out.println("MathContext(3) = "
                + original.round(new MathContext(3, RoundingMode.HALF_UP)));
        System.out.println("decimal precision = " + original.precision());
        System.out.println("decimal scale = " + original.scale());

        double sum = 0.1 + 0.2;
        System.out.println("double raw = " + sum);
        System.out.println("double display = "
                + String.format(Locale.ROOT, "%.2f", sum));
        System.out.println("double still raw = " + sum);

        DecimalFormat limited = new DecimalFormat("0", symbols);
        limited.setMaximumIntegerDigits(3);
        System.out.println("max integer 3 of 12345 = "
                + limited.format(12345));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DigitFormatDemo.java
java DigitFormatDemo
~~~

Kết quả:

~~~text
min fraction = 2
max fraction = 4
range 1.2 = 1.20
range 1.234567 = 1.2346
range 12 = 12.00
padded 12.5 = 0012.50
optional 1 = 1.0
optional 1.23 = 1.23
optional 1.234 = 1.23
group 12345.6789 = 12,345.679
width = |    1.20|
f 1.23456 = 1.23
g 12345.678 = 1.23e+04
decimal original = 12.3456
formatted decimal = 12.3456
decimal after format = 12.3456
setScale(2) = 12.35
MathContext(3) = 12.3
decimal precision = 6
decimal scale = 4
double raw = 0.30000000000000004
double display = 0.30
double still raw = 0.30000000000000004
max integer 3 of 12345 = 345
~~~

Hai dòng đầu cho thấy cấu hình min/max thực tế. Các dòng `range`, `optional` và `group` minh họa formatter. `width` và `g` phân biệt các loại precision/width. Các dòng `BigDecimal` cho thấy formatter không thay đổi object, còn `setScale`/`MathContext` tạo kết quả riêng. Cặp `double raw`/`double display` chứng minh việc chỉ rút ngắn bản in không đổi dữ liệu gốc.

## 10. Format rồi parse có phải đang làm tròn giá trị không?

Giả sử:

~~~java
double original = 1.234567;
String displayed = String.format(Locale.ROOT, "%.2f", original);
// displayed = "1.23"
double reparsed = Double.parseDouble(displayed);
// reparsed biểu diễn gần 1.23, khác original
~~~

Nếu bạn **gán `reparsed` vào biến**, chương trình thực sự có giá trị mới, nhưng đó là hậu quả của việc tự **format rồi parse lại**, không phải formatter làm thay đổi biến cũ. Cách này còn có nguy cơ:

- Làm mất thông tin vì nhiều giá trị gốc trở thành một chuỗi rút ngắn.
- Đưa vào phụ thuộc locale, nhóm chữ số và dấu thập phân.
- Vẫn có sai số binary64 ở giá trị được parse lại.
- Thay đổi thuật toán nếu áp dụng làm tròn theo chuỗi sau mỗi bước tính.

Đừng dùng mẹo này như phương pháp tổng quát để cải thiện độ chính xác. Nếu yêu cầu là lượng tử hóa số decimal, dùng kiểu decimal và chính sách tính toán; nếu yêu cầu là hiển thị, chỉ format ở bước xuất.

## 11. Tình huống ứng dụng và quyết định đúng

**Hiển thị điểm số:** Điểm gốc `9.5`, báo cáo cần hai chữ số sau dấu thập phân. `DecimalFormat("0.00")` hiển thị `9.50`. Số 0 thêm không chứng minh phép chấm chính xác hơn, cũng không thay đổi điểm gốc.

**Giao diện cảm biến:** Số đo `12.3456` nhưng màn hình chỉ đủ hiển thị `12.35`. Formatter có thể dùng maximum fraction digits = 2. Hệ thống cần giữ giá trị thô và độ bất định của thiết bị nếu mục tiêu là phân tích kỹ thuật.

**Hóa đơn:** Dữ liệu decimal `12.3456` phải được làm tròn thành `12.35` trước khi lưu. Chọn `BigDecimal#setScale(2, roundingMode)` và lưu **giá trị kết quả**, không chỉ format nguồn. Quy tắc tài chính cần nói rõ làm tròn tại dòng, tại tổng hay tại lúc thanh toán.

**Mã đơn hàng:** Mã `0012` cần giữ số 0 đầu để so khớp. Lưu `String` thay vì chỉ lưu `int 12` rồi cố phục hồi bằng minimumIntegerDigits.

**Số lớn:** `setMaximumIntegerDigits(3)` biến `12345` thành bản in `345`. Nếu cần bản xem ngắn, dùng cách viết rõ đơn vị (ví dụ 12.3 nghìn) và quy tắc giao diện cụ thể, không cắt bit có nghĩa mà không báo.

**Trao đổi dữ liệu:** Nếu bên nhận phải có đúng dữ liệu số `double`, ưu tiên định dạng round-trip có bảo đảm. Nếu bên nhận yêu cầu một decimal amount với scale bắt buộc, xác định quy tắc `BigDecimal`/serialization riêng. Một chuỗi hiển thị hai chữ số thập phân không thay thế hai hợp đồng ấy.

## 12. Lỗi thường gặp

1. **Cho rằng minimum fraction digits = 2 khiến giá trị chính xác hơn.** Nó chỉ thêm số 0 vào chuỗi nếu cần.
2. **Nhầm maximum fraction digits với precision của kiểu `double`.** Chuỗi ngắn không sửa giá trị lưu.
3. **Nhầm `%.2f` với `%.2g`.** `%f` kiểm soát chữ số phần lẻ; `%g` kiểm soát chữ số có nghĩa của cách in.
4. **Nhầm `%8.2f` là tám chữ số.** Tám là độ rộng tối thiểu tính bằng ký tự.
5. **Tự đặt maximum integer digits quá nhỏ.** Có thể bỏ chữ số đầu, gây hiển thị số sai về giá trị.
6. **Nhầm `BigDecimal.scale()` với `precision()`.** Một thuộc tính là số mũ decimal, thuộc tính kia đếm chữ số có nghĩa của unscaled value.
7. **Cho rằng `MathContext(3)` là ba số lẻ.** Nó chỉ ba chữ số có nghĩa tổng cộng.
8. **Gọi `BigDecimal#setScale` nhưng không nhận kết quả.** `BigDecimal` bất biến.
9. **Không chỉ định quy tắc làm tròn ở điểm quan trọng.** `HALF_EVEN` và `HALF_UP` có thể khác nhau.
10. **Dùng formatter chung đa luồng một cách tùy tiện.** `DecimalFormat` mutable và không thread-safe theo cách dùng chung không bảo vệ.
11. **Dùng format/parse để “sửa” sai số.** Có thể loại bỏ thông tin, lại tạo một `double` xấp xỉ.
12. **Không kiểm soát locale.** Dấu phân cách decimal khác nhau có thể phá giao thức hoặc kiểm thử.
13. **Tưởng precision lưu là chất lượng phép đo.** Độ bất định của phép đo không tự được biểu diễn bởi số chữ số của object.

## 13. Tự kiểm tra

**1.** `DecimalFormat("0.00##")` in `1.2` thế nào, và phần lẻ có giới hạn ra sao?

**Đáp án:** `1.20`; minimum = 2, maximum = 4. Nếu cần nhiều hơn 4 số lẻ để biểu diễn thì formatter làm tròn khi tạo chuỗi.

**2.** `String.format(Locale.ROOT, "%8.2f", 1.2)` chứa tám chữ số hay tám ký tự?

**Đáp án:** Tám **ký tự** tối thiểu, kết quả gồm bốn dấu cách và `1.20`.

**3.** Vì sao `%.3g` khác `%.3f`?

**Đáp án:** `%g` tập trung vào ba chữ số có nghĩa, còn `%f` vào ba chữ số sau dấu thập phân.

**4.** `new BigDecimal("12.3456")` có precision và scale bằng bao nhiêu?

**Đáp án:** Precision = 6 và scale = 4. Định dạng ít chữ số không thay đổi thuộc tính của object.

**5.** So sánh `setScale(2, HALF_UP)` và `round(new MathContext(3, HALF_UP))` trên `12.3456`.

**Đáp án:** Kết quả `12.35` và `12.3`. Một cái giữ 2 vị trí thập phân, một cái giữ 3 chữ số có nghĩa.

**6.** Vì sao `setMaximumIntegerDigits(3)` không phù hợp để thu gọn `12345`?

**Đáp án:** Có thể in thành `345`, bỏ các chữ số có trọng số cao khiến người dùng tưởng là một số khác.

**7.** `String.format("%.2f", 0.1 + 0.2)` có làm phép cộng trở nên chính xác `0.3` không?

**Đáp án:** Không. Nó chỉ in `0.30`; giá trị `double` gốc vẫn `0.30000000000000004`.

## Tài liệu tham khảo

- [Java SE 21 — DecimalFormat](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/DecimalFormat.html): pattern `0`/`#`, các giới hạn chữ số, cách làm tròn.
- [Java SE 21 — NumberFormat](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/NumberFormat.html): minimum/maximum integer/fraction digits.
- [Java SE 21 — Formatter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Formatter.html): width và precision của các conversion `%f` và `%g`.
- [Java SE 21 — BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html): `precision`, `scale`, `setScale`.
- [Java SE 21 — MathContext](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/MathContext.html): precision là số chữ số có nghĩa của phép toán.
- [JLS `4.2.3 — Floating-Point Types](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-010">RoundingMode của bộ định dạng</a>

<details>
<summary>Xem chi tiết</summary>

# RoundingMode của bộ định dạng trong Java

Khi hiển thị một số thành chuỗi, ứng dụng thường cần giới hạn số chữ số thập phân: giá tiền có hai chữ số sau dấu thập phân, tỷ lệ có ba chữ số hoặc giá trị trong báo cáo cần cùng độ chính xác. Nếu giá trị có nhiều chữ số hơn giới hạn hiển thị, **bộ định dạng phải quyết định làm tròn theo hướng nào**. Trong Java, `java.math.RoundingMode` mô tả quy tắc đó; `java.text.DecimalFormat` cho phép cấu hình bằng `setRoundingMode(...)`.

Bài này giải thích cách `RoundingMode` chi phối **kết quả định dạng** của `DecimalFormat`, đặc biệt khi gặp số đúng giữa hai mức làm tròn (*tie*) và số âm. Phép làm tròn trong tính toán bằng `BigDecimal` được đối chiếu để tránh nhầm mục đích.

## 1. Làm tròn xảy ra ở bước nào?

Có ba khái niệm khác nhau:

1. **Giá trị gốc:** ví dụ `BigDecimal("12.345")` vẫn biểu diễn giá trị `12.345`.
2. **Độ chính xác cần hiển thị:** mẫu `0.00` hoặc `setMaximumFractionDigits(2)` yêu cầu không hiển thị quá hai chữ số thập phân.
3. **Quy tắc chọn giá trị gần đúng:** `RoundingMode.HALF_UP`, `HALF_EVEN`, `FLOOR`... cho biết phải xử lý phần bị loại bỏ ra sao.

Ví dụ, cùng giá trị `12.345` với hai chữ số sau dấu thập phân:

| Bộ định dạng | Quy tắc | Chuỗi kết quả |
|---|---|---|
| `0.00` | `HALF_UP` | `12.35` |
| `0.00` | `HALF_EVEN` | `12.34` |
| `0.00` | `DOWN` | `12.34` |

`DecimalFormat.format(...)` trả về **`String`**; nó không sửa đối tượng `BigDecimal` và không cập nhật biến số đầu vào. Nếu cần làm tròn giá trị để tiếp tục tính toán, hãy dùng API số học phù hợp như `BigDecimal.setScale(...)` với quy tắc đã chọn.

### Cách thiết lập

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

DecimalFormat formatter = new DecimalFormat(
        "#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
formatter.setRoundingMode(RoundingMode.HALF_UP);

String text = formatter.format(new BigDecimal("1234.565"));
// text = "1,234.57"
```

Đây là **đoạn mã minh họa** để đặt trong phương thức hoặc JShell (không phải một tệp Java độc lập). Mẫu `#,##0.00` bật phân nhóm hàng nghìn và luôn in đúng hai chữ số thập phân; bộ ký hiệu `Locale.US` dùng dấu `.` cho phần thập phân và dấu `,` cho hàng nghìn. `HALF_UP` chọn phía xa số 0 khi giá trị nằm đúng giữa hai mức làm tròn.

`DecimalFormat` mặc định dùng `RoundingMode.HALF_EVEN`, nhưng nên đặt tường minh để người đọc mã nhận ra quy tắc nghiệp vụ và để tránh phụ thuộc vào mặc định.

## 2. Tám chế độ của `RoundingMode`

Giả sử định dạng bắt buộc **hai chữ số thập phân**, và giá trị đầu vào là `2.345` hoặc `-2.345`. Cả hai đều nằm đúng giữa hai mức gần nhất vì chữ số thứ ba là `5` và không có phần còn lại.

| Chế độ | Ý nghĩa | `2.345` | `-2.345` |
|---|---|---:|---:|
| `UP` | Ra xa 0 nếu phải bỏ phần khác 0 | `2.35` | `-2.35` |
| `DOWN` | Về phía 0; bỏ phần vượt quá độ chính xác | `2.34` | `-2.34` |
| `CEILING` | Về phía dương vô cực (`+∞`) | `2.35` | `-2.34` |
| `FLOOR` | Về phía âm vô cực (`-∞`) | `2.34` | `-2.35` |
| `HALF_UP` | Chọn gần nhất; nếu hòa, ra xa 0 | `2.35` | `-2.35` |
| `HALF_DOWN` | Chọn gần nhất; nếu hòa, về phía 0 | `2.34` | `-2.34` |
| `HALF_EVEN` | Chọn gần nhất; nếu hòa, chữ số giữ lại cuối cùng phải chẵn | `2.34` | `-2.34` |
| `UNNECESSARY` | Không cho phép làm tròn làm thay đổi giá trị | Ném `ArithmeticException` | Ném `ArithmeticException` |

**Hai nhóm quy tắc cần phân biệt:**

- `UP`, `DOWN`, `CEILING` và `FLOOR` quyết định **hướng** khi có phần khác 0 bị loại, không cần xét liệu phần đó lớn hay nhỏ hơn `0.5` đơn vị ở chữ số cuối.
- `HALF_UP`, `HALF_DOWN` và `HALF_EVEN` chọn **giá trị gần nhất** trước; chỉ khi khoảng cách tới hai giá trị kề nhau bằng nhau, quy tắc xử lý *tie* mới khác nhau.

Ví dụ `2.346` khi in hai chữ số thập phân sẽ thành `2.35` với cả `HALF_UP`, `HALF_DOWN` và `HALF_EVEN`: đây không phải trường hợp hòa, vì `2.35` gần hơn `2.34`.

### Vì sao `HALF_EVEN` không có nghĩa là “luôn làm tròn xuống”?

Khi gặp hòa, `HALF_EVEN` chọn kết quả có **chữ số cuối được giữ lại là số chẵn**:

- `2.345` → `2.34`, vì chữ số hàng phần trăm là `4` (chẵn).
- `2.355` → `2.36`, vì chữ số hàng phần trăm là `6` (chẵn).
- `-2.345` → `-2.34`, cũng theo quy tắc chữ số chẵn.

Cơ chế này còn được gọi là *round half to even* hoặc *banker's rounding*. Trong nhiều chuỗi làm tròn với dữ liệu phân bố phù hợp, nó giúp giảm xu hướng lệch cộng dồn do luôn xử lý hòa về một phía. Điều đó không biến nó thành quy tắc bắt buộc cho mọi hệ thống tiền tệ: hệ thống cần áp dụng chính sách làm tròn được quy định cho từng nghiệp vụ.

### Đặc biệt với số âm

`CEILING` và `FLOOR` không đồng nghĩa với “tăng” và “giảm” phần trị tuyệt đối:

- `CEILING(-2.345)` ở hai chữ số → `-2.34`, vì `-2.34` **lớn hơn** `-2.35`.
- `FLOOR(-2.345)` → `-2.35`, vì `-2.35` **nhỏ hơn** `-2.34`.
- `UP` luôn hướng **ra xa 0**; `DOWN` luôn hướng **về 0**, với cả dấu dương lẫn dấu âm.

Đây là nguyên nhân phổ biến khiến việc kiểm thử chỉ với số dương bỏ sót lỗi.

## 3. Chương trình Java 17+ chạy độc lập

Lưu tệp dưới tên `RoundingModeFormatterDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class RoundingModeFormatterDemo {
    private static String display(String value, RoundingMode mode) {
        DecimalFormat formatter = new DecimalFormat("0.00",
                DecimalFormatSymbols.getInstance(Locale.US));
        formatter.setRoundingMode(mode);
        return formatter.format(new BigDecimal(value));
    }

    public static void main(String[] args) {
        for (RoundingMode mode : new RoundingMode[] {
                RoundingMode.UP, RoundingMode.DOWN, RoundingMode.CEILING,
                RoundingMode.FLOOR, RoundingMode.HALF_UP, RoundingMode.HALF_DOWN,
                RoundingMode.HALF_EVEN
        }) {
            System.out.println(mode + ": " + display("2.345", mode)
                    + " | " + display("-2.345", mode));
        }
        System.out.println("HALF_EVEN 2.355: "
                + display("2.355", RoundingMode.HALF_EVEN));
        try {
            System.out.println(display("2.345", RoundingMode.UNNECESSARY));
        } catch (ArithmeticException ex) {
            System.out.println("UNNECESSARY: ArithmeticException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 RoundingModeFormatterDemo.java
java RoundingModeFormatterDemo
```

Kết quả chuẩn trong môi trường Java đã hỗ trợ các API này (ví dụ Java 17):

```text
UP: 2.35 | -2.35
DOWN: 2.34 | -2.34
CEILING: 2.35 | -2.34
FLOOR: 2.34 | -2.35
HALF_UP: 2.35 | -2.35
HALF_DOWN: 2.34 | -2.34
HALF_EVEN: 2.34 | -2.34
HALF_EVEN 2.355: 2.36
UNNECESSARY: ArithmeticException
```

**Giải thích chương trình:**

1. `new BigDecimal(value)` nhận chuỗi thập phân để tạo đầu vào đúng theo số viết ra, giúp thử các giá trị hòa một cách chính xác.
2. `new DecimalFormat("0.00", ...)` giới hạn kết quả về hai chữ số thập phân và cố định ký hiệu hiển thị.
3. `setRoundingMode(mode)` thay đổi cách chọn kết quả khi cần loại bớt chữ số.
4. Vòng lặp thử các chế độ theo cùng dữ liệu dương và âm. `UNNECESSARY` được thử riêng, vì đây là chế độ xác thực việc **không phải làm tròn**, và nó có thể ném ngoại lệ.

## 4. Quan hệ giữa mẫu định dạng, số chữ số và `RoundingMode`

`RoundingMode` **không tự quy định** làm tròn tới hàng đơn vị, một chữ số hay hai chữ số thập phân. Vị trí làm tròn được quyết định bởi cấu hình chữ số của bộ định dạng.

```java
DecimalFormat f = new DecimalFormat("0.##",
        DecimalFormatSymbols.getInstance(Locale.US));
f.setRoundingMode(RoundingMode.HALF_UP);

f.format(new BigDecimal("2.345")); // "2.35"
f.format(new BigDecimal("2.3"));   // "2.3"
f.format(new BigDecimal("2"));     // "2"
```

Mẫu `0.##` cho phép **tối đa** hai chữ số sau dấu thập phân; dấu `#` không buộc in các số 0 ở cuối. Nếu muốn luôn in đủ hai chữ số, dùng `0.00`. Đây là sự khác biệt về **trình bày**, không phải hai chế độ làm tròn khác nhau.

Có thể cấu hình bằng phương thức thay cho mẫu:

```java
DecimalFormat f = new DecimalFormat("#,##0.###",
        DecimalFormatSymbols.getInstance(Locale.US));
f.setMinimumFractionDigits(2);
f.setMaximumFractionDigits(2);
f.setRoundingMode(RoundingMode.HALF_UP);

f.format(new BigDecimal("1234.565")); // "1,234.57"
f.format(new BigDecimal("1234.5"));   // "1,234.50"
```

- `setMaximumFractionDigits(2)`: giới hạn số chữ số thập phân hiển thị ở mức tối đa hai; đây là cấu hình khiến phép làm tròn có thể phát sinh.
- `setMinimumFractionDigits(2)`: yêu cầu in ít nhất hai chữ số, kể cả khi phải bổ sung số 0.
- `getRoundingMode()`: đọc quy tắc hiện tại của bộ định dạng.
- `setRoundingMode(...)`: đặt quy tắc khi có chữ số phải loại bỏ.

Với một giá trị đã biểu diễn đúng ở độ chính xác cần hiển thị (ví dụ `2.34` in hai chữ số), các chế độ sẽ cho cùng giá trị kết quả; việc bổ sung số 0 (`2.3` → `2.30`) không phải là làm tròn thay đổi giá trị.

## 5. `UNNECESSARY`: phát hiện thay vì tự động làm tròn

`RoundingMode.UNNECESSARY` phù hợp khi hợp đồng dữ liệu yêu cầu đầu vào **đã đúng độ chính xác**, và bất kỳ việc loại phần khác 0 nào cũng phải được báo lỗi.

```java
DecimalFormat strict = new DecimalFormat("0.00",
        DecimalFormatSymbols.getInstance(Locale.US));
strict.setRoundingMode(RoundingMode.UNNECESSARY);

strict.format(new BigDecimal("2.34"));  // "2.34"
strict.format(new BigDecimal("2.340")); // "2.34": bỏ số 0 không đổi giá trị
strict.format(new BigDecimal("2.345")); // ArithmeticException
```

Đoạn này minh họa các lệnh gọi riêng lẻ; nếu chạy tuần tự và không bắt ngoại lệ, lệnh cuối sẽ dừng luồng thực thi. Quan trọng là `UNNECESSARY` **không cấm mọi thao tác thay đổi chuỗi ký tự**: nó cấm làm tròn làm thay đổi **giá trị số**. Vì vậy việc loại các số 0 thập phân dư thừa vẫn hợp lệ.

Nếu giá trị đến từ nhập liệu hoặc API, hãy kiểm tra yêu cầu về scale/độ chính xác ngay tại ranh giới nhập dữ liệu, trả lỗi nghiệp vụ rõ ràng; không nên dùng `UNNECESSARY` như cách thay thế cho toàn bộ quy trình xác thực đầu vào.

## 6. Định dạng khác với làm tròn phục vụ phép tính

Có hai luồng xử lý hợp lý, tùy mục đích.

**Chỉ hiển thị số**:

```java
BigDecimal original = new BigDecimal("19.995");
DecimalFormat view = new DecimalFormat("0.00",
        DecimalFormatSymbols.getInstance(Locale.US));
view.setRoundingMode(RoundingMode.HALF_UP);

String label = view.format(original); // "20.00"
// original vẫn là 19.995
```

**Tính tiếp với giá trị đã làm tròn**:

```java
BigDecimal original = new BigDecimal("19.995");
BigDecimal rounded = original.setScale(2, RoundingMode.HALF_UP);
// rounded = 20.00 (BigDecimal, scale = 2)
```

Cả `format(...)` và `setScale(...)` đều có thể dùng `RoundingMode`, nhưng kết quả khác loại: một bên là `String` phục vụ biểu diễn, bên kia là `BigDecimal` phục vụ số học. `BigDecimal` bất biến: `setScale(...)` trả đối tượng mới thay vì sửa `original`.

Nếu tính tiền, thuế, chiết khấu hoặc đối soát, phải xác định **thời điểm làm tròn** theo quy tắc nghiệp vụ (từng dòng, từng giao dịch hay ở tổng cuối). Cộng các chuỗi đã định dạng hoặc định dạng trước mỗi bước rồi chuyển ngược lại thành số có thể làm mất thông tin và tạo sai lệch. `RoundingMode` chỉ quyết định **cách** làm tròn khi đã đến bước cần làm tròn; nó không tự quyết định **khi nào** làm tròn.

## 7. Đầu vào `double`, `BigDecimal` và lỗi do biểu diễn nhị phân

Phần lớn phân số thập phân không được biểu diễn chính xác bằng `double`. Khi kiểm thử những trường hợp nhạy cảm như đúng điểm giữa, dùng giá trị `double` có thể khiến kết quả khó suy luận chỉ bằng cách nhìn ký tự thập phân trong mã nguồn.

```java
BigDecimal exact = new BigDecimal("2.345"); // Chính xác là 2.345
BigDecimal fromDouble = new BigDecimal(2.345); // Lấy đúng giá trị nhị phân của double
```

Hai cách tạo `BigDecimal` này không bảo đảm cùng giá trị số. Để mô tả một giá trị thập phân nghiệp vụ, ưu tiên nhận `BigDecimal` từ chuỗi thập phân, từ dữ liệu decimal có kiểm soát, hoặc dùng `BigDecimal.valueOf(double)` khi buộc chuyển từ `double` (hiểu rõ rằng quá trình tính toán bằng `double` trước đó có thể đã mất độ chính xác).

Khi gọi `DecimalFormat.format(double)`, hãy nhớ đang định dạng **giá trị `double` thực tế**, không phải một “số thập phân lý tưởng” mà người đọc hình dung từ literal. Với bài kiểm thử quy tắc tie và các con số tiền tệ, đầu vào `BigDecimal` từ chuỗi sẽ giúp thể hiện ý định chính xác hơn.

## 8. Chọn chế độ theo yêu cầu sử dụng

Không có một chế độ tốt nhất cho mọi bài toán. Hãy chọn theo quy tắc của dữ liệu và người tiêu thụ kết quả.

| Tình huống | Lựa chọn cần cân nhắc | Vì sao |
|---|---|---|
| Báo cáo thống kê nhiều phép làm tròn | `HALF_EVEN` | Có thể giảm xu hướng sai lệch khi nhiều giá trị ở điểm hòa |
| Quy định nghiệp vụ yêu cầu hòa thì ra xa 0 | `HALF_UP` | Quy tắc dễ giải thích cho cả số dương lẫn âm |
| Cắt bớt chữ số, luôn hướng về 0 | `DOWN` | Thể hiện rõ ý định cắt bỏ phần vượt quá độ chính xác |
| Tìm giới hạn làm tròn theo hướng lên/xuống trục số | `CEILING` / `FLOOR` | Hướng được định nghĩa theo `+∞` / `-∞` |
| Dữ liệu bắt buộc đã đúng độ chính xác | `UNNECESSARY` | Lỗi rõ ràng nếu phải làm tròn làm đổi giá trị |

Các tình huống về tiền tệ cần đặc biệt thận trọng: quy tắc làm tròn, đơn vị tiền, số chữ số và vị trí áp dụng phải theo đặc tả sản phẩm, quy định pháp lý hoặc hợp đồng. Không suy ra rằng cứ dùng `NumberFormat.getCurrencyInstance(...)` là tự động đáp ứng mọi luật làm tròn tiền.

Một bộ định dạng tiền tệ theo locale vẫn có thể cấu hình `RoundingMode`:

```java
import java.text.NumberFormat;

NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);
usd.setRoundingMode(RoundingMode.HALF_UP);

String amount = usd.format(new BigDecimal("1234.565"));
// Với ký hiệu tiền tệ và quy tắc của Locale.US: "$1,234.57"
```

`NumberFormat` là lớp cơ sở. Các bộ định dạng số thập phân thông dụng hỗ trợ `setRoundingMode(...)`, nhưng API của lớp cơ sở cho phép một số triển khai ném `UnsupportedOperationException`. Nếu cần dựa vào hành vi hoặc cấu hình riêng của `DecimalFormat`, hãy làm việc với đối tượng `DecimalFormat` cụ thể.

## 9. Lỗi thường gặp và cách tránh

**Lỗi 1 — Chỉ đặt `RoundingMode` mà không giới hạn chữ số.** Nếu bộ định dạng vẫn hiển thị đủ độ chính xác đầu vào, quy tắc làm tròn có thể không phát huy tác dụng. Hãy kiểm tra mẫu định dạng, `getMinimumFractionDigits()` và `getMaximumFractionDigits()`.

**Lỗi 2 — Nhầm `DOWN` với `FLOOR`, hoặc `UP` với `CEILING`.** Với số âm, cặp chế độ này cho kết quả khác nhau. Hãy có test cho cả `2.345` và `-2.345`.

**Lỗi 3 — Cho rằng `HALF_UP` luôn làm tròn lên.** `HALF_UP` chọn kết quả gần nhất và chỉ ra xa số 0 khi đúng điểm hòa. Với `2.341` và hai chữ số, `HALF_UP` cho `2.34`, không phải `2.35`.

**Lỗi 4 — Cho rằng `HALF_EVEN` luôn làm tròn xuống.** `2.345` → `2.34`, nhưng `2.355` → `2.36`.

**Lỗi 5 — Không xác định locale khi so sánh chuỗi.** Dấu phân cách thập phân và hàng nghìn tùy `DecimalFormatSymbols`/`Locale`. Với test cần so sánh stdout cố định, hãy truyền `Locale.US`; với giao diện thực tế, dùng locale được yêu cầu.

**Lỗi 6 — Tưởng rằng chuỗi đã định dạng thay đổi số gốc.** `format()` chỉ tạo chuỗi hiển thị. Nếu nghiệp vụ thực sự cần giá trị đã làm tròn, phải lưu rõ kết quả số học (`BigDecimal.setScale(...)`) tại bước phù hợp.

**Lỗi 7 — Dùng một `DecimalFormat` dùng chung cho nhiều luồng.** `DecimalFormat` là đối tượng có trạng thái thay đổi được và không được bảo đảm an toàn khi nhiều luồng truy cập đồng thời mà không đồng bộ. Thông thường hãy tạo bộ định dạng theo phạm vi lời gọi/tác vụ, hoặc thiết kế chiến lược sử dụng và đồng bộ phù hợp.

**Lỗi 8 — Tin rằng hiển thị hai chữ số đảm bảo mọi phép tính chính xác hai chữ số.** Bước định dạng cuối không thay thế chính sách độ chính xác của toàn bộ chuỗi tính toán. Hãy kiểm tra scale, vị trí làm tròn và cách cộng tổng theo yêu cầu nghiệp vụ.

## 10. Bài tập tự kiểm tra

Giả sử dùng `new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US))`, định dạng các giá trị `BigDecimal` tạo từ **chuỗi**. Hãy đoán kết quả trước khi chạy:

1. `1.225` với `HALF_EVEN`.
2. `1.235` với `HALF_EVEN`.
3. `-1.225` với `CEILING`.
4. `-1.225` với `FLOOR`.
5. `2.300` với `UNNECESSARY`.
6. `2.301` với `UNNECESSARY`.

**Đáp án và lý do:**

1. `1.22`: giữa `1.22` và `1.23`, chọn chữ số cuối chẵn (`2`).
2. `1.24`: giữa `1.23` và `1.24`, chọn chữ số cuối chẵn (`4`).
3. `-1.22`: hướng về dương vô cực.
4. `-1.23`: hướng về âm vô cực.
5. `2.30`: chỉ bỏ số 0 thừa, không thay đổi giá trị.
6. Ném `ArithmeticException`: muốn về hai chữ số phải làm tròn bỏ phần khác 0.

Khi kiểm thử chức năng định dạng số, tối thiểu nên có trường hợp số dương, số âm, ở đúng điểm hòa, gần nhưng không ở điểm hòa, giá trị đã đúng độ chính xác và giá trị không được phép làm tròn.

## Ghi nhớ

- **Độ chính xác hiển thị** được quyết định bởi mẫu `DecimalFormat` hoặc các phương thức điều chỉnh số chữ số; **hướng làm tròn** do `RoundingMode` quyết định.
- `HALF_UP`, `HALF_DOWN`, `HALF_EVEN` khác nhau **chỉ ở trường hợp hòa**; với các giá trị khác, chúng cùng chọn giá trị gần nhất.
- `UP`/`DOWN` xét hướng so với **0**; `CEILING`/`FLOOR` xét hướng theo **trục số**.
- `DecimalFormat.format(...)` tạo `String` và giữ nguyên giá trị đầu vào; `BigDecimal.setScale(...)` trả về một giá trị số mới.
- Với kết quả cần chính xác về dấu và quy tắc làm tròn, dùng giá trị thập phân có kiểm soát và test cả trường hợp âm lẫn điểm hòa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-011">ParsePosition và kiểm tra toàn bộ chuỗi khi parse theo locale</a>

<details>
<summary>Xem chi tiết</summary>

# ParsePosition và kiểm tra toàn bộ chuỗi khi parse theo locale trong Java

## 1. Mục tiêu: parse được một tiền tố chưa chắc dữ liệu hợp lệ

Khi nhận một chuỗi số theo quy ước vùng miền (locale), `NumberFormat` hoặc `DecimalFormat` có thể **đọc thành công một phần đầu chuỗi rồi dừng lại**. Một kết quả kiểu `Number` khác `null` **không đảm bảo toàn bộ chuỗi đầu vào đã được tiêu thụ**.

Ví dụ với quy ước Đức, dấu chấm phân cách hàng nghìn và dấu phẩy là dấu thập phân:

~~~text
Đầu vào:   "1.234,56 kg"
            └──────┘└─┘
              Số     Phần còn dư

Kết quả số: 1234.56
Đã đọc:      8 ký tự trên tổng 11 ký tự
~~~

Nếu phần mềm nhận trường dữ liệu “chỉ được chứa một số”, việc chấp nhận `1234.56` và lờ `" kg"` là lỗi validation. Công cụ cốt lõi để phát hiện trường hợp này là `java.text.ParsePosition`.

Sau bài học, bạn sẽ biết cách:

- Dùng `NumberFormat.parse(String, ParsePosition)` và `DecimalFormat` để parse theo `Locale`.
- Phân biệt kết quả parse thành công một **prefix** với việc tiêu thụ **toàn bộ chuỗi**.
- Hiểu đúng `getIndex()`, `getErrorIndex()`, `null`, điểm bắt đầu và trạng thái của `ParsePosition`.
- Tạo hàm parse chặt chẽ bằng Java 17: xác nhận kiểu số, toàn chuỗi và miền nghiệp vụ.
- Phân biệt giới hạn của kiểm tra toàn chuỗi với kiểm tra cú pháp grouping, ký tự khoảng trắng, `NaN`/`Infinity`, độ chính xác và các giá trị ngoài miền.

## 2. Locale thay đổi ý nghĩa dấu phân cách số

Java cung cấp các cách biểu diễn số khác nhau theo `Locale`. Ví dụ phổ biến:

| Locale | Chuỗi biểu diễn một nghìn hai trăm ba mươi tư phẩy năm sáu |
| --- | --- |
| `Locale.US` | `"1,234.56"` |
| `Locale.GERMANY` | `"1.234,56"` |

Cùng một ký tự `,` hoặc `.` có thể được hiểu là dấu thập phân, dấu nhóm hoặc không phù hợp với cách biểu diễn dự kiến. Vì vậy không được dựa vào locale mặc định của hệ điều hành khi định dạng dữ liệu trao đổi có hợp đồng cố định.

~~~java
NumberFormat german = NumberFormat.getNumberInstance(Locale.GERMANY);
NumberFormat american = NumberFormat.getNumberInstance(Locale.US);
~~~

Đoạn này giả định đã import `java.text.NumberFormat` và `java.util.Locale`, và được đặt trong một phương thức.

Các yếu tố cần phân biệt:

- **Locale của dữ liệu:** cách chuỗi được tạo hoặc người dùng nhập, ví dụ tiếng Đức hay tiếng Mỹ.
- **Locale mặc định của tiến trình:** có thể thay đổi theo máy, môi trường triển khai hoặc mã khác.
- **Hợp đồng định dạng:** ngoài locale, ứng dụng còn phải quy định có cho phép grouping, khoảng trắng, dấu âm, số mũ hoặc hậu tố đơn vị hay không.

`NumberFormat.getNumberInstance(Locale.GERMANY)` giúp đọc số theo locale Đức; nó **không** tự biến parser thành bộ xác thực chặt chẽ của tất cả quy tắc nghiệp vụ.

## 3. NumberFormat.parse(String) và parse(String, ParsePosition)

`NumberFormat` có hai cách dùng quan trọng.

**Cách tiện lợi:**

~~~java
NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
Number value = format.parse("1.234,56 kg"); // Có thể nhận một số từ tiền tố.
~~~

Đây là đoạn minh họa bên trong phương thức có khai báo `throws ParseException` hoặc `try/catch`. Overload `parse(String)` có thể ném `ParseException` khi không parse được từ vị trí bắt đầu, nhưng **không có hợp đồng bắt buộc phải tiêu thụ hết chuỗi**. Đừng xem việc lời gọi không ném exception là “whole-input validation”.

**Cách quan sát vị trí:**

~~~java
NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
ParsePosition pos = new ParsePosition(0);
Number value = format.parse("1.234,56 kg", pos);

System.out.println(value);          // 1234.56
System.out.println(pos.getIndex()); // 8
~~~

Ở overload có `ParsePosition`:

- Giá trị trả về là một `Number` nếu parse được phần bắt đầu được phép.
- Có thể trả `null` khi không nhận diện được số.
- `pos.getIndex()` phản ánh vị trí tiếp theo sau phần đã parse thành công.
- `pos.getErrorIndex()` có thể cho thông tin lỗi khi parse thất bại; **không** thay thế phép kiểm tra đã tiêu thụ hết đầu vào.

Với một form chỉ chứa số, logic cơ bản phải đồng thời kiểm tra **`value != null`** và **`pos.getIndex() == input.length()`**.

## 4. ParsePosition lưu những gì?

`java.text.ParsePosition` là một đối tượng trạng thái có thể thay đổi, với hai thông tin chính:

| API | Ý nghĩa |
| --- | --- |
| `new ParsePosition(startIndex)` | Bắt đầu parse tại chỉ số UTF-16 được chỉ định |
| `getIndex()` | Chỉ số bắt đầu/hiện tại; khi parse thành công, là vị trí tiếp theo sau phần đã tiêu thụ |
| `setIndex(index)` | Chọn vị trí bắt đầu mới |
| `getErrorIndex()` | Vị trí báo lỗi khi parser cung cấp; có thể là −1 nếu không có thông tin lỗi |
| `setErrorIndex(index)` | Cho phép thay đổi trạng thái chỉ số lỗi |

`ParsePosition` không giữ chuỗi nguồn và cũng không xác định sẵn locale; locale được cấu hình ở `NumberFormat`/`DecimalFormat`.

### Quy ước chỉ số của Java String

Các chỉ số vị trí dùng theo đơn vị **UTF-16 code unit**, cùng hệ chỉ số với `String.length()`. Do đó, điều kiện so sánh cuối chuỗi là:

~~~java
pos.getIndex() == input.length()
~~~

Không dùng số lượng code point Unicode (`codePointCount`) hoặc số byte UTF-8 để so với `ParsePosition`. Nếu đầu vào có ký tự bổ sung ngoài BMP, số UTF-16 code unit và số code point có thể khác nhau; `String.length()` vẫn là đơn vị phù hợp cho chỉ số của parser.

### ParsePosition không phải là Boolean success

Một ví dụ có thể parse được tiền tố nhưng vẫn có ký tự không hợp lệ về sau:

~~~java
String text = "1.234,56 kg";
ParsePosition pos = new ParsePosition(0);
Number parsed = NumberFormat.getNumberInstance(Locale.GERMANY).parse(text, pos);

boolean hasNumber = parsed != null;               // true
boolean consumedAll = pos.getIndex() == text.length(); // false
~~~

Nếu chỉ dựa vào `hasNumber`, chương trình nhận sai dữ liệu. Nếu chỉ kiểm tra `getErrorIndex() == -1`, chương trình cũng có nguy cơ nhận sai: không có lỗi được ghi nhận khi parser **chủ động dừng sau một tiền tố hợp lệ** không có nghĩa phần còn lại đã được xác thực.

## 5. Công thức kiểm tra toàn bộ chuỗi

Với dữ liệu phải bắt đầu từ ký tự đầu tiên và không cho phép phần dư, mẫu cơ bản:

~~~java
static Number parseEntire(String text, Locale locale) {
    if (text == null || text.isEmpty()) {
        throw new IllegalArgumentException("Missing numeric text");
    }

    NumberFormat parser = NumberFormat.getNumberInstance(locale);
    ParsePosition pos = new ParsePosition(0);
    Number number = parser.parse(text, pos);

    if (number == null || pos.getIndex() != text.length()) {
        throw new IllegalArgumentException("Invalid or trailing characters");
    }
    return number;
}
~~~

Đây là **phương thức minh họa** đặt trong lớp Java, cần import `NumberFormat`, `ParsePosition` và `Locale`.

Điều kiện gồm hai phần bắt buộc:

1. `number != null` — một giá trị đã được parse.
2. `pos.getIndex() == text.length()` — parser đã đọc tới đúng cuối chuỗi.

Không cần tự tăng chỉ số. Không tự cắt phần hậu tố trước khi so sánh, vì như thế đã làm mất bằng chứng rằng chuỗi đầu vào chứa dữ liệu dư.

**Định nghĩa “toàn chuỗi” phải được quyết định trước.** Nếu ứng dụng cho phép khoảng trắng ngoài số, có thể dùng `trim()` theo hợp đồng trước khi parse rồi so sánh với **độ dài chuỗi đã chuẩn hóa**. Nếu không cho phép khoảng trắng, giữ nguyên `text` và yêu cầu tất cả ký tự đều được tiêu thụ.

## 6. Phân biệt parse thất bại, prefix success và whole-input success

Với bộ parse số Đức:

| Text | Kết quả dự kiến về prefix | Toàn chuỗi được đọc? | Kết luận khi field chỉ cho phép số |
| --- | --- | --- | --- |
| `"1.234,56"` | 1234.56 | Có | Chấp nhận nếu đáp ứng nghiệp vụ |
| `"1.234,56 kg"` | 1234.56 | Không | Từ chối phần dư |
| `"12,5abc"` | 12.5 | Không | Từ chối phần dư |
| `"abc"` | Không parse được từ đầu | Không | Từ chối |
| `""` | Không có số | Không | Từ chối |
| `"-5"` | −5 | Có | Còn phải kiểm tra miền giá trị |
| `"1.234,56"` theo locale US | Có thể bị hiểu khác hoặc dừng sớm | Không suy ra được ý định | Dùng locale đúng hợp đồng |

Bảng cố ý không biến “parse được một số” thành “hợp lệ”. Một chuỗi có thể được tiêu thụ hết nhưng vẫn **không đúng miền giá trị**, hoặc bị parser chấp nhận theo quy tắc grouping tương đối linh hoạt.

## 7. NumberFormat trả Number: lưu ý kiểu và độ chính xác

`NumberFormat.parse` trả kiểu trừu tượng `Number`. Tùy định dạng và giá trị, kết quả có thể được biểu diễn bằng wrapper số nguyên hoặc dấu phẩy động. Đừng giả định mọi kết quả luôn là `Double` và ép kiểu:

~~~java
Number value = NumberFormat.getNumberInstance(Locale.GERMANY)
        .parse("123", new ParsePosition(0));
double asDouble = value.doubleValue(); // lấy giá trị theo kiểu double
~~~

Ví dụ trên minh họa lấy giá trị; bản thân nó **chưa kiểm tra toàn chuỗi**. Mặt khác, `doubleValue()` có thể làm mất độ chính xác đối với một số nguyên lớn hoặc giá trị thập phân có nhiều chữ số.

Với tiền hoặc dữ liệu decimal cần giữ chữ số, trong JDK chuẩn, `NumberFormat.getNumberInstance(Locale.GERMANY)` trả một `DecimalFormat`. Có thể cấu hình:

~~~java
DecimalFormat parser =
        (DecimalFormat) NumberFormat.getNumberInstance(Locale.GERMANY);
parser.setParseBigDecimal(true);
~~~

Khi parse số thập phân thông thường, `DecimalFormat` có thể trả `BigDecimal`, giúp tránh bước chuyển qua binary64 trước khi kiểm tra và sử dụng kết quả decimal. Tuy nhiên **không được ép mù mọi kết quả về `BigDecimal`**: các giá trị đặc biệt như NaN/Infinity có thể được biểu diễn bằng kiểu khác theo hợp đồng `DecimalFormat`. Kiểm tra `instanceof BigDecimal` trước khi sử dụng.

Chỉ sử dụng ép kiểu `(DecimalFormat)` khi bạn biết implementation đang cung cấp `DecimalFormat` (các locale số phổ biến của JDK chuẩn). Trong môi trường tích hợp provider tùy biến, có thể kiểm tra `instanceof` và từ chối kiểu formatter không được hỗ trợ để tránh `ClassCastException`.

## 8. Ví dụ parser decimal đầy đủ: toàn chuỗi, miền số và kiểu kết quả

Giả sử ứng dụng nhận số lượng decimal theo quy ước Đức, cho phép:

- Giá trị từ **0 đến 10.000**, gồm hai biên.
- Grouping và dấu phẩy thập phân của locale Đức.
- Không nhận ký tự dư hay đơn vị ở cuối.
- Không chấp nhận NaN/Infinity.
- Giữ biểu diễn bằng `BigDecimal` để kiểm tra decimal và tránh làm tròn binary64 trước khi validation.

Hàm áp dụng:

~~~java
static BigDecimal parseGermanAmount(String input) {
    if (input == null || input.isEmpty()) {
        throw new IllegalArgumentException("Missing number");
    }

    NumberFormat base = NumberFormat.getNumberInstance(Locale.GERMANY);
    if (!(base instanceof DecimalFormat parser)) {
        throw new IllegalStateException("DecimalFormat is required");
    }
    parser.setParseBigDecimal(true);

    ParsePosition pos = new ParsePosition(0);
    Number result = parser.parse(input, pos);

    if (!(result instanceof BigDecimal value)
            || pos.getIndex() != input.length()) {
        throw new IllegalArgumentException("Invalid number or trailing text");
    }

    if (value.signum() < 0
            || value.compareTo(new BigDecimal("10000")) > 0) {
        throw new IllegalArgumentException("Out of allowed range");
    }
    return value;
}
~~~

Đây là **phương thức thành viên** để đặt trong lớp Java 17+, cần import `BigDecimal`, `DecimalFormat`, `NumberFormat`, `ParsePosition` và `Locale`.

Các điểm quan trọng:

1. Tham số `input` không được null hoặc rỗng.
2. Tạo parser **theo locale xác định**, không phụ thuộc locale mặc định.
3. Bật `setParseBigDecimal(true)` trước khi parse.
4. Bắt đầu từ index **0**.
5. Yêu cầu trả về `BigDecimal` và vị trí cuối bằng `input.length()`.
6. Chỉ sau đó mới kiểm tra miền decimal `[0, 10000]`, bằng `signum` và `compareTo`.

`value.compareTo(new BigDecimal("10000"))` so sánh giá trị số học, không dựa vào scale của `BigDecimal`. `new BigDecimal("10000")` nhận chính xác số decimal 10000 theo chuỗi.

Việc tạo `NumberFormat` mới mỗi lời gọi tránh chia sẻ một `DecimalFormat` có thể thay đổi trạng thái giữa nhiều luồng. `NumberFormat` không nên được chia sẻ giữa các thread nếu không có cơ chế đồng bộ riêng theo hướng dẫn của Java API.

## 9. Chương trình Java 17+ hoàn chỉnh

Lưu thành tệp `LocaleParsePositionDemo.java`:

~~~java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleParsePositionDemo {
    static BigDecimal parseGermanAmount(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Missing number");
        }

        NumberFormat base = NumberFormat.getNumberInstance(Locale.GERMANY);
        if (!(base instanceof DecimalFormat parser)) {
            throw new IllegalStateException("DecimalFormat is required");
        }
        parser.setParseBigDecimal(true);

        ParsePosition pos = new ParsePosition(0);
        Number result = parser.parse(input, pos);

        if (!(result instanceof BigDecimal value)
                || pos.getIndex() != input.length()) {
            throw new IllegalArgumentException("Invalid or trailing text");
        }
        if (value.signum() < 0
                || value.compareTo(new BigDecimal("10000")) > 0) {
            throw new IllegalArgumentException("Outside [0, 10000]");
        }
        return value;
    }

    public static void main(String[] args) {
        NumberFormat german = NumberFormat.getNumberInstance(Locale.GERMANY);
        String withSuffix = "1.234,56 kg";
        ParsePosition pos = new ParsePosition(0);
        Number prefix = german.parse(withSuffix, pos);

        System.out.println("prefix=" + prefix);
        System.out.println("consumed=" + pos.getIndex() + "/" + withSuffix.length());
        System.out.println("full=" + (pos.getIndex() == withSuffix.length()));

        System.out.println("strict=" + parseGermanAmount("1.234,56"));
        System.out.println("plain=" + parseGermanAmount("1234,56"));
        System.out.println("zero=" + parseGermanAmount("0"));

        String[] invalid = {
            "1.234,56 kg", "12,5abc", "abc", "", "-5", "20.000,00"
        };
        for (String text : invalid) {
            try {
                parseGermanAmount(text);
                System.out.println("accepted=" + text);
            } catch (IllegalArgumentException ex) {
                System.out.println("rejected=" + text);
            }
        }

        ParsePosition badPos = new ParsePosition(0);
        Number bad = german.parse("abc", badPos);
        System.out.println("failed=" + (bad == null));
        System.out.println("errorIndex=" + badPos.getErrorIndex());
    }
}
~~~

Biên dịch/chạy trên JDK 17 trở lên:

~~~bash
javac --release 17 LocaleParsePositionDemo.java
java LocaleParsePositionDemo
~~~

Stdout dự kiến trên nhà cung cấp dữ liệu locale tiêu chuẩn của JDK 17+:

~~~text
prefix=1234.56
consumed=8/11
full=false
strict=1234.56
plain=1234.56
zero=0
rejected=1.234,56 kg
rejected=12,5abc
rejected=abc
rejected=
rejected=-5
rejected=20.000,00
failed=true
errorIndex=0
~~~

Giải thích:

- `NumberFormat.parse(withSuffix, pos)` nhận một số từ phần đầu `"1.234,56"` và dừng tại index 8, trước `" kg"`.
- `parseGermanAmount` dùng `setParseBigDecimal(true)` và bắt buộc parser tiêu thụ **toàn bộ chuỗi**, nên từ chối hậu tố, đơn vị và ký tự thừa.
- `"abc"` không tạo giá trị từ vị trí 0; `getErrorIndex()` được hiển thị để quan sát, nhưng không thay cho `value != null` và so sánh vị trí.
- Giá trị âm `"-5"` và `"20.000,00"` được parse thành số nhưng ngoài miền nghiệp vụ `[0, 10000]`, nên bị từ chối.
- Chuỗi rỗng bị loại trước khi parse.

Nếu JVM sử dụng nhà cung cấp locale được tùy biến, các biểu diễn/thuộc tính locale có thể khác bộ dữ liệu tiêu chuẩn; hệ thống cần cố định môi trường và kiểm thử quy tắc locale thực tế khi yêu cầu tương thích chặt chẽ.

## 10. getErrorIndex() dùng khi nào?

`ParsePosition.getErrorIndex()` có giá trị khi parser **không thể nhận diện một số** và muốn chỉ ra vị trí nghi ngờ gây lỗi. Ví dụ parser số Đức gặp `"abc"` ở index 0 thì kết quả có thể `null` và error index được đặt 0.

Nhưng hãy phân biệt:

- **Không parse được từ đầu:** `value == null`. Index thành công có thể giữ nguyên, và `getErrorIndex()` có thể chỉ ra nơi lỗi.
- **Parse được một tiền tố:** `value != null`, `index < length`. Parser không nhất thiết coi phần còn lại là lỗi; `errorIndex` có thể vẫn bằng −1.
- **Đã parse hết:** `value != null` và `index == length`. Đây mới là yêu cầu cơ bản của validation “whole input”.

Không nên viết:

~~~java
// Không đủ để đảm bảo toàn chuỗi hợp lệ:
boolean valid = pos.getErrorIndex() == -1;
~~~

Không nên kiểm tra `getErrorIndex() >= 0` thay cho `pos.getIndex() != text.length()`. `getErrorIndex()` không phải thông báo lỗi lexical toàn cục hoặc bộ kiểm tra quy tắc nghiệp vụ.

Nếu muốn báo lỗi vị trí phần dư, với parse prefix thành công hãy dùng:

~~~java
int firstUnconsumed = pos.getIndex();
if (firstUnconsumed < input.length()) {
    // Có thể báo: ký tự không được chấp nhận bắt đầu tại firstUnconsumed.
}
~~~

Đây là đoạn minh họa sau khi đã parse, chưa phải một chương trình độc lập. Cần phân biệt rõ index UTF-16 với index hiển thị trong giao diện người dùng có thể dùng code point/grapheme cluster.

## 11. Bắt đầu ở index khác 0, tái sử dụng ParsePosition và kiểm tra kết thúc

`ParsePosition` cho phép parse một con số bắt đầu **giữa** chuỗi. Điều đó hữu ích khi ứng dụng có giao thức gồm phần tiền tố cố định:

~~~java
String text = "qty=1.234,56";
ParsePosition pos = new ParsePosition(4);

Number n = NumberFormat.getNumberInstance(Locale.GERMANY).parse(text, pos);
System.out.println(n);              // 1234.56
System.out.println(pos.getIndex()); // 12
~~~

Trong trường hợp này, bạn đã **chủ động cho phép bỏ qua bốn ký tự đầu** `"qty="`. Việc `pos.getIndex() == text.length()` chỉ chứng minh đã tiêu thụ từ vị trí bắt đầu tới hết chuỗi; nó không xác nhận tiền tố đúng. Cần kiểm tra riêng `text.startsWith("qty=")` (hoặc grammar của giao thức) và chọn index 4 một cách có chủ đích.

Với một field đơn thuần chỉ chứa số, **luôn bắt đầu từ index 0**; không nên chọn index khác rồi gọi đó là “parse toàn chuỗi”.

### ParsePosition là đối tượng trạng thái

Nếu dùng lại cùng một instance `ParsePosition`, lần parse sau có thể **bắt đầu từ index cũ**. Một số trạng thái lỗi cũng có thể còn tồn tại hoặc thay đổi tùy kết quả parser. Mẫu dễ kiểm soát nhất là tạo một `new ParsePosition(0)` cho **mỗi chuỗi đầu vào**.

~~~java
ParsePosition first = new ParsePosition(0);
ParsePosition second = new ParsePosition(0);
~~~

Không chia sẻ cùng một `ParsePosition` có thể thay đổi giữa các thread nếu không tự kiểm soát đồng bộ.

## 12. Kiểm tra toàn chuỗi vẫn chưa phải validation cú pháp thật chặt

`NumberFormat`/`DecimalFormat` được thiết kế để xử lý định dạng số theo locale; trong nhiều cấu hình, parser có thể **linh hoạt** với grouping hoặc những cách viết mà ứng dụng không muốn nhận. Điều kiện `index == length` chỉ đảm bảo parser tiêu thụ hết ký tự theo **ngữ pháp mà parser đang chấp nhận**.

Ví dụ yêu cầu dữ liệu tiền tệ có đúng mẫu “1.234,56”, không chấp nhận mọi vị trí grouping, có thể cần thêm một bước kiểm tra định dạng.

Các lựa chọn:

1. **Hợp đồng số theo locale tương đối linh hoạt:** parse với `NumberFormat`, kiểm tra whole-input, type và range. Cách này phù hợp khi chấp nhận các biến thể hợp lệ theo parser.
2. **Không cho phép dấu grouping:** tạo `DecimalFormat`, đặt `setGroupingUsed(false)` và kiểm tra whole-input; vẫn nên kiểm thử các dạng đặc biệt được chấp nhận.
3. **Yêu cầu grouping đúng vị trí và cú pháp chính xác:** xác thực grammar bằng quy tắc riêng đã được kiểm thử theo locale; `DecimalFormat` parse sau đó chỉ là bước chuyển giá trị. Đừng trông chờ `index == length` một mình bảo đảm cấu trúc nhóm hàng nghìn.
4. **Dữ liệu trao đổi giữa máy móc:** tốt hơn dùng một format **bất biến theo locale**, như decimal ASCII với dấu `.` và quy tắc rõ ràng, thay vì tùy thuộc ngôn ngữ hiển thị của người dùng.

### Vì sao không chỉ round-trip format rồi so với input?

Một ý tưởng là parse rồi `format(value)` và so chuỗi đầu vào. Phương pháp này có thể **từ chối cả chuỗi hợp lệ** vì formatter quyết định số chữ số thập phân, grouping, zero đầu, dấu cộng và rounding theo pattern. Nếu muốn định dạng đúng tuyệt đối, phải xác định rõ **một dạng canonical duy nhất** và xây bộ kiểm tra phù hợp; không thể mặc định mọi chuỗi người dùng cần giống output formatter.

## 13. Phân biệt NumberFormat với Double.parseDouble

| Khía cạnh | `NumberFormat` theo `Locale` | `Double.parseDouble` |
| --- | --- | --- |
| Dấu thập phân | Tùy locale, ví dụ Đức dùng `,` | Cú pháp Java dùng `.` |
| Dấu nhóm | Có thể xử lý theo locale | Không hỗ trợ grouping kiểu `1,234.56` |
| Hậu tố không hợp lệ | Có thể parse một tiền tố rồi dừng | Thường ném `NumberFormatException` nếu chuỗi không đúng grammar |
| Cơ chế kiểm tra toàn chuỗi | `ParsePosition` và `getIndex()` | Parser xác thực cú pháp toàn chuỗi theo grammar Java |
| Giá trị trả về | `Number`; có thể cấu hình `DecimalFormat` dùng `BigDecimal` | Primitive `double` |
| NaN/Infinity | Có thể xử lý theo ký hiệu locale và implementation | `"NaN"`, `"Infinity"` theo cú pháp Java |
| Chính xác decimal | Dùng `setParseBigDecimal(true)` với `DecimalFormat` | Theo binary64, có làm tròn |

Chọn API dựa trên **định dạng nguồn**. Nếu người dùng nhập số theo locale, `NumberFormat` là công cụ phù hợp nhưng cần validation đầy đủ. Nếu hệ thống nhận protocol decimal với dấu chấm thống nhất, `Double.parseDouble` hoặc `BigDecimal(String)` có thể đơn giản hơn; tuy nhiên vẫn phải kiểm tra dữ liệu nghiệp vụ (miền giá trị, NaN/Infinity, độ chính xác).

## 14. Lưu ý về số đặc biệt, số âm và BigDecimal

`DecimalFormat.setParseBigDecimal(true)` không đồng nghĩa **mọi** kết quả đều là `BigDecimal`. Các giá trị không phải số bình thường, đặc biệt NaN/Infinity theo locale, có thể được trả dưới dạng `Double`. Bởi vậy đoạn:

~~~java
if (!(result instanceof BigDecimal amount)) {
    throw new IllegalArgumentException("Expected a finite decimal value");
}
~~~

là kiểm tra có chủ đích nếu hợp đồng chỉ cho phép decimal hữu hạn. Nó cũng tránh lỗi ép kiểu `ClassCastException`.

Mặt khác, `BigDecimal` hỗ trợ số âm, zero và những số decimal rất nhỏ; việc chọn miền (chẳng hạn ≥0) là **một bước kiểm tra khác**. Các yêu cầu như “hai chữ số thập phân đúng”, “không quá ba chữ số phần lẻ”, “không được số âm” hoặc “phải thuộc `[0, 10000]`” không tự xuất hiện từ cấu hình locale.

Với tiền hoặc tỷ lệ cần độ chính xác decimal, lưu `BigDecimal` và sử dụng `compareTo` để so miền. Không chuyển sang `double` chỉ để kiểm tra rồi lại chuyển về `BigDecimal`: quá trình đó có thể làm mất chữ số.

## 15. Trimming, whitespace và ký tự Unicode

Một form cho người dùng có thể gửi `" 1.234,56 "`. Có ít nhất hai thiết kế hợp lý:

- **Strict raw input:** không chấp nhận khoảng trắng đầu/cuối; giữ nguyên text và buộc parser tiêu thụ hết.
- **Normalize rồi strict:** nếu hợp đồng cho phép whitespace biên, chuẩn hóa đầu vào trước (ví dụ `strip()` hoặc `trim()` tùy quy tắc ký tự) rồi parse đầy đủ chuỗi **đã chuẩn hóa**.

Không tự gọi `trim()` chỉ vì tiện: với giao thức, chữ ký số hoặc dữ liệu lưu trữ, thay đổi đầu vào có thể không được phép. `String.strip()` và `String.trim()` không xử lý mọi loại dấu cách Unicode theo cùng một cách; dấu cách không ngắt được dùng làm grouping trong một số locale có thể cần xử lý đặc biệt.

Cũng cần phân biệt:

- **Khoảng trắng biên:** phần trước/sau toàn bộ số.
- **Grouping separator:** ký tự phân cách nhóm chữ số hợp lệ của locale, có thể trông giống dấu cách.
- **Đơn vị ở cuối:** ví dụ `" kg"`, không nên bỏ qua trong field chỉ nhận số.
- **Ký tự Unicode giống chữ số:** không tự chuẩn hóa thành ASCII nếu hệ thống chưa quy định.

Với validation toàn chuỗi, chuỗi sau bước chuẩn hóa (nếu có) là chuỗi dùng để tính `length()`; không lấy độ dài văn bản gốc đem so với vị trí trên chuỗi đã sửa.

## 16. Hiệu năng, tính luồng và bảo mật dữ liệu đầu vào

`NumberFormat`/`DecimalFormat` là các đối tượng có trạng thái cấu hình (pattern, symbol, grouping, parseBigDecimal). Khi chạy song song:

- Không chia sẻ một formatter mutable cho nhiều luồng mà không có biện pháp đồng bộ phù hợp.
- Có thể tạo formatter **mới** cho mỗi request, hoặc dùng cơ chế cung cấp formatter riêng theo thread nếu đo đạc cho thấy cần tối ưu.
- `ParsePosition` cũng mutable; tạo mới cho mỗi lần parse sẽ tránh index và error index bị dùng lại ngoài ý muốn.

Đối với dữ liệu bên ngoài:

- Đặt giới hạn độ dài chuỗi đầu vào hợp lý để tránh chi phí parse quá mức.
- Không chấp nhận dữ liệu dài bất thường chỉ vì parser trả được prefix ở đầu.
- Định nghĩa rõ locale, dấu phân cách, cho phép dấu âm và miền giá trị.
- Nếu thông báo lỗi chứa text của người dùng, escape/ghi log theo quy định để tránh log injection và lộ dữ liệu nhạy cảm.
- Với tài chính và dữ liệu cần precision, không ép thành `double` nếu không cần.

## 17. Các lỗi thường gặp

| Lỗi | Hậu quả | Sửa đúng |
| --- | --- | --- |
| Chỉ dùng `NumberFormat.parse(text)` | Có thể bỏ qua hậu tố | `parse(text, new ParsePosition(0))` rồi kiểm tra index |
| Chỉ kiểm tra `number != null` | Prefix hợp lệ vẫn lọt | Cần thêm `index == text.length()` |
| Chỉ kiểm tra `getErrorIndex() == -1` | Không phát hiện mọi ký tự còn dư | Kiểm tra vị trí tiêu thụ |
| Dùng `new ParsePosition(4)` cho field chỉ chứa số | Có thể bỏ qua 4 ký tự đầu | Bắt đầu từ index 0 |
| Dùng `parsePosition` cũ mà không reset | Lần sau bắt đầu sai vị trí | Tạo mới cho mỗi lần parse |
| So với `String.codePointCount` hoặc byte length | Sai hệ chỉ số | Dùng `String.length()` |
| Parse theo locale mặc định của server | Máy khác hiểu dấu khác | Chỉ định `Locale` rõ ràng |
| Ép mọi `Number` thành `Double` | Có thể `ClassCastException` | Dùng `Number`/`instanceof` và chuyển có kiểm soát |
| Ép mọi kết quả `DecimalFormat` thành `BigDecimal` | NaN/Infinity có thể dùng kiểu khác | Kiểm tra `instanceof BigDecimal` |
| Nghĩ full consumption chứng minh grouping đúng | Parser có thể chấp nhận dạng nhóm linh hoạt | Thêm validation lexical/pattern |
| Nghĩ parse được là đúng miền | Âm, quá lớn vẫn parse được | Kiểm tra miền nghiệp vụ |
| Dùng `doubleValue()` cho tiền decimal | Có thể mất độ chính xác | Dùng `BigDecimal` trực tiếp |
| Formatter dùng chung giữa thread | Trạng thái mutable, dễ lỗi | Tạo riêng hoặc bảo vệ đúng |
| Tự động `trim()` rồi không đổi độ dài so sánh | So sánh sai text và index | So với length của chuỗi thực sự parse |

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `parse("1.234,56 kg")` có thể thành công theo locale Đức nhưng dữ liệu chưa hợp lệ?

**Đáp án:** Parser đọc được tiền tố `"1.234,56"` thành số 1234.56, nhưng không nhất thiết tiêu thụ `" kg"`. Một field chỉ chứa số phải kiểm tra `ParsePosition.getIndex() == text.length()`.

**Câu 2.** Nếu `getErrorIndex() == -1`, có chắc chuỗi đã parse hết không?

**Đáp án:** Không. Khi parse được tiền tố, parser có thể dừng mà không ghi lỗi; cần kiểm tra `getIndex()`.

**Câu 3.** `NumberFormat.parse(String, ParsePosition)` trả `null`, có thể chấp nhận nếu index bằng length không?

**Đáp án:** Không. Điều kiện nhận là **cả** `result != null` và `index == length`. Nếu `null`, không có số hợp lệ.

**Câu 4.** ParsePosition được tạo bằng `new ParsePosition(4)` có phù hợp để kiểm tra toàn chuỗi của field số không?

**Đáp án:** Không. Nó bỏ qua phần đầu. Chỉ dùng vị trí khác 0 khi prefix được grammar của ứng dụng kiểm tra riêng.

**Câu 5.** Tại sao `DecimalFormat.setParseBigDecimal(true)` vẫn cần kiểm tra `instanceof BigDecimal`?

**Đáp án:** Giá trị đặc biệt như NaN hoặc Infinity có thể không tạo `BigDecimal`. Nếu hợp đồng chỉ nhận số decimal hữu hạn, phải từ chối chúng và tránh ép kiểu sai.

**Câu 6.** Tại sao whole-input parse không tự bảo đảm phân nhóm hàng nghìn “đẹp và đúng chuẩn”?

**Đáp án:** `NumberFormat` có thể chấp nhận một số hình thức grouping linh hoạt. `index == length` chỉ chứng minh đã tiêu thụ toàn bộ theo parser, không chứng minh chuỗi tuân thủ một mẫu nhập liệu chặt chẽ do ứng dụng tự đặt.

**Câu 7.** Nếu chuẩn hóa chuỗi trước khi parse, phải dùng độ dài nào để so với `getIndex()`?

**Đáp án:** `length()` của **chính chuỗi được đưa vào parser**, không dùng độ dài chuỗi thô ban đầu.

## 19. Tóm tắt và tài liệu tham khảo

Điểm cần nhớ:

- `NumberFormat` theo locale được thiết kế để đọc chuỗi số **theo quy ước vùng miền**, nhưng việc parse một số không đảm bảo toàn bộ input hợp lệ.
- **Bộ đôi kiểm tra bắt buộc** cho field chỉ chứa số: `result != null` và `pos.getIndex() == text.length()`, với `new ParsePosition(0)`.
- `getErrorIndex()` giúp tìm lỗi parse, **không** thay thế việc kiểm tra vị trí kết thúc.
- Kiểm tra toàn chuỗi chỉ là **một lớp validation**; còn cần kiểm tra kiểu, miền giá trị, độ chính xác và những ràng buộc định dạng khác.
- Dữ liệu decimal cần chính xác có thể dùng `DecimalFormat.setParseBigDecimal(true)` và kiểm tra `instanceof BigDecimal`.
- Hãy quy định locale, grouping, whitespace và tính hợp lệ của dấu phân cách theo hợp đồng đầu vào, thay vì phó mặc cho locale mặc định.

Nguồn tra cứu chính thức (Java 17):

- [NumberFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/NumberFormat.html): `parse(String)`, `parse(String, ParsePosition)`, formatter theo `Locale` và lưu ý về thread safety.
- [DecimalFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormat.html): `setParseBigDecimal`, pattern, grouping và decimal symbols.
- [ParsePosition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/ParsePosition.html): `getIndex()`, `setIndex()`, `getErrorIndex()`.
- [Locale](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html): mô hình locale trong API Java.
- [BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): số decimal chính xác và `compareTo()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-012">setParseBigDecimal và giới hạn an toàn khi dùng formatter đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# `setParseBigDecimal` và giới hạn an toàn khi dùng formatter đồng thời trong Java

`java.text.DecimalFormat` là một formatter/parser số thực linh hoạt của Java: nó hỗ trợ **ký hiệu thập phân, phân nhóm hàng nghìn, định dạng theo mẫu và locale**. Tuy nhiên, hai vấn đề cần xử lý cẩn thận khi dùng với dữ liệu quan trọng:

1. **Kiểu và độ chính xác của kết quả parse.** `DecimalFormat#setParseBigDecimal(true)` yêu cầu parser trả `BigDecimal` cho **các số hữu hạn bình thường**, nhờ đó tránh ép kết quả parse trước hết qua `double`. Điều này đặc biệt hữu ích cho dữ liệu tiền tệ hoặc số thập phân dài.
2. **An toàn đồng thời (thread safety).** `DecimalFormat` và `NumberFormat` dạng dùng phổ biến **không bảo đảm thread-safe**. Không được chia sẻ một instance mutable để nhiều luồng cùng `format`, `parse` hoặc thay đổi cấu hình mà không có cơ chế đồng bộ phù hợp.

Ví dụ nhập chuỗi theo chuẩn dấu phân cách của Hoa Kỳ:

```java
java.text.DecimalFormatSymbols symbols =
    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US);

java.text.DecimalFormat df =
    new java.text.DecimalFormat("#,##0.00", symbols);

df.setParseBigDecimal(true);

Number result = df.parse("1,234.50");
// Kiểu thực tế của result: BigDecimal; giá trị thập phân: 1234.50.
```

Đây là **đoạn mã minh họa** trong một phương thức có khai báo/nhận `ParseException` thích hợp. Khi dùng `new DecimalFormat(..., symbols)`, ta quy định rõ ý nghĩa dấu phẩy và dấu chấm thay vì phụ thuộc locale mặc định của môi trường.

**Quan trọng:** Bật `setParseBigDecimal(true)` **không làm formatter trở thành thread-safe**, **không bảo đảm mọi chuỗi đầu vào hợp lệ về nghiệp vụ** và **không biến NaN/vô cực thành `BigDecimal`**. Những giới hạn này độc lập với nhau.

## 1. Phân biệt định dạng (`format`) và phân tích (`parse`)

`DecimalFormat` có hai hướng sử dụng:

- **`format(number)`**: nhận một giá trị số và tạo `String` theo mẫu ký tự, số chữ số thập phân, dấu phân cách, locale và quy tắc làm tròn.
- **`parse(text)`**: đọc văn bản đã nhập thành `Number` theo ký hiệu của formatter. `Number` là kiểu cha; giá trị thực tế có thể thuộc nhiều class khác nhau.

```java
java.text.DecimalFormat df = new java.text.DecimalFormat(
    "#,##0.00",
    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US)
);

String display = df.format(1234.5); // "1,234.50"
Number parsed = df.parse("1,234.50");
```

Kiểu khai báo của `parse(String)` là **`Number`**, không phải `BigDecimal` hay `Double`. Vì vậy nếu muốn thao tác an toàn dựa trên kiểu cụ thể, cần biết **cấu hình parser** và kiểm tra giá trị trước khi cast.

**Đừng suy ra rằng mẫu `#,##0.00` tự ép parser chỉ được nhận đúng hai chữ số thập phân.** Mẫu chủ yếu điều khiển cách format và một số quy tắc parse; parse còn có thể nhận những chuỗi không đúng chính sách nhập của ứng dụng. Nếu cần ràng buộc định dạng cố định, phải kiểm tra đầu vào riêng.

## 2. `setParseBigDecimal(true)` thay đổi điều gì?

`DecimalFormat#setParseBigDecimal(boolean newValue)` điều khiển cách chuyển chuỗi số thành `Number`:

- Khi **`true`**: với giá trị số hữu hạn phân tích thông thường, phương thức trả **`java.math.BigDecimal`**. Giá trị được xây từ chữ số thập phân đã nhận, thay vì bắt buộc đi qua một `double`.
- Khi **`false`** (thông thường là mặc định): parser có thể trả **`Long`** nếu giá trị số nguyên có thể biểu diễn dưới dạng `long`, hoặc **`Double`** khi giá trị không phù hợp kiểu `Long`/mang phần lẻ. Không nên mặc định mọi kết quả đều là `Double`.
- **Ngoại lệ quan trọng:** `NaN` và `±Infinity` vẫn được biểu diễn bằng **`Double`**, ngay cả khi `setParseBigDecimal(true)`.

```java
java.text.DecimalFormat df = new java.text.DecimalFormat(
    "#,##0.00",
    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US)
);

df.setParseBigDecimal(true);
Number precise = df.parse("1,234.50"); // BigDecimal 1234.50

df.setParseBigDecimal(false);
Number ordinary = df.parse("42");      // Long 42 trên JDK theo quy tắc parse này
```

Vì khai báo return type `Number` giữ nguyên, **không thể ép kiểu `BigDecimal` một cách mù quáng chỉ dựa trên lời gọi `parse`**. Cần biết instance nào đã được cấu hình và kiểm tra các giá trị đặc biệt.

### Vì sao `BigDecimal` hữu ích?

`double` chỉ có khoảng 15–17 chữ số thập phân có ý nghĩa, nên các chữ số dài có thể bị làm tròn khi chuyển qua binary floating point. `BigDecimal` biểu diễn số **thập phân** bằng phần nguyên độ chính xác tùy ý và scale.

Ví dụ chuỗi:

```text
9,007,199,254,740,993.00
```

`9007199254740993` là số nguyên lớn hơn `2^53`, không thể biểu diễn **chính xác** bằng `double`. Khi bật `setParseBigDecimal(true)`, parser trả một `BigDecimal` với nội dung thập phân **`9007199254740993.00`**. Nếu chuyển dữ liệu không nguyên rất lớn qua `double`, việc làm tròn có thể làm đổi những chữ số cuối.

**Không nhầm:** Bật chế độ `BigDecimal` chỉ giữ độ chính xác của **các chữ số parser thực sự đọc được**. Nếu dữ liệu đã được làm tròn ở hệ thống gửi, hoặc đầu vào sai định dạng, parser không thể khôi phục chữ số gốc đã mất.

## 3. `setParseBigDecimal` không phải bộ lọc NaN và vô cực

`BigDecimal` không có các giá trị `NaN`, `+Infinity` hoặc `-Infinity` như `double`. Vì thế `DecimalFormat` sử dụng `Double` cho các giá trị đặc biệt này ngay cả khi chế độ parse `BigDecimal` được bật.

```java
df.setParseBigDecimal(true);

Number nan = df.parse(df.getDecimalFormatSymbols().getNaN());
Number infinity = df.parse(df.getDecimalFormatSymbols().getInfinity());

// nan và infinity có kiểu thực tế Double, không phải BigDecimal.
```

Các chuỗi ký hiệu NaN/vô cực phụ thuộc cấu hình `DecimalFormatSymbols`/locale. Nếu parser được tạo từ locale khác, không nên mặc định các ký hiệu luôn được viết đúng là `"NaN"` và `"Infinity"` bằng chữ ASCII.

Với một API bắt buộc trả thập phân hữu hạn, có thể xác thực:

```java
Number number = df.parse(text);
if (!(number instanceof java.math.BigDecimal)) {
    throw new IllegalArgumentException("finite decimal required");
}
java.math.BigDecimal value = (java.math.BigDecimal) number;
```

Đây là đoạn mã minh họa cần đặt trong phương thức có xử lý `ParseException`. Điều kiện `instanceof` giúp **không cast nhầm `Double.NaN` hoặc vô cực sang `BigDecimal`**.

Ngoài việc chọn kiểu số, ứng dụng còn cần xác định điều gì được phép về **độ lớn, dấu, số chữ số và đơn vị đo**. Parse thành `BigDecimal` không đồng nghĩa với việc dữ liệu đã được nghiệp vụ xác thực.

## 4. Mất dấu của `-0.0` và những khác biệt về Number

Một góc cạnh tinh tế: kiểu dấu phẩy động có **`+0.0` và `-0.0`** với bit dấu khác nhau, nhưng `BigDecimal` **không lưu dấu zero âm riêng biệt**.

```java
df.setParseBigDecimal(true);
Number zero = df.parse("-0");
// BigDecimal có giá trị 0, không giữ dấu âm của zero.
```

Khi `setParseBigDecimal(false)` và `isParseIntegerOnly()` là `false`, parser có thể trả **`Double(-0.0)`**, giúp giữ dấu zero trong trường hợp này.

Do đó, không thể xem chế độ `BigDecimal` là “luôn bảo toàn mọi khía cạnh biểu diễn của chuỗi”. Nó bảo toàn **giá trị thập phân** cho số hữu hạn nhưng không giữ mọi dấu hiệu văn bản, kiểu dấu phẩy động hay thông tin trình bày ban đầu.

Nếu cần **giữ chính xác chuỗi mà người dùng đã nhập** để hiển thị lại hoặc kiểm toán, lưu riêng chuỗi gốc cùng giá trị `BigDecimal` đã parse. Giá trị số được phân tích không nhất thiết giữ dấu cộng đầu, chữ số 0 dẫn đầu, cách nhóm dấu phẩy, các ký hiệu locale hoặc sự khác biệt `-0`/`0`.

## 5. Cẩn thận với `setParseIntegerOnly` và các cài đặt khác

`setParseBigDecimal(true)` **không ghi đè mọi tùy chọn parser**. Ví dụ, `NumberFormat#setParseIntegerOnly(true)` làm parser chỉ nhận phần nguyên trong nhiều cấu hình:

```java
java.text.DecimalFormat df = new java.text.DecimalFormat(
    "#,##0.00",
    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US)
);
df.setParseBigDecimal(true);
df.setParseIntegerOnly(true);

Number value = df.parse("12.75");
// Parser có thể dừng tại dấu thập phân và chỉ nhận phần nguyên.
```

Không nên xem việc bật `parseBigDecimal` là bảo đảm giữ mọi phần thập phân nếu những cài đặt khác ngăn parser đọc chúng. Ngược lại, `setRoundingMode` điều khiển **làm tròn khi format**; nó không tự biến dữ liệu nhập thập phân thành giá trị đã làm tròn trong quá trình parse.

Các cài đặt của `DecimalFormat` như pattern, symbols, grouping, `setParseIntegerOnly`, `setParseBigDecimal`, `setMaximumFractionDigits` và `setRoundingMode` đều nên được xác định **trước khi formatter được dùng** theo nhiệm vụ. Thay đổi chúng trên một instance chia sẻ giữa các luồng sẽ tạo rủi ro logic và thread safety.

## 6. `parse(String)` có thể chấp nhận phần đầu chuỗi

Một lỗi dữ liệu nguy hiểm: `DecimalFormat.parse(String)` không nhất thiết buộc **toàn bộ chuỗi đầu vào** phải được tiêu thụ. Nó có thể đọc phần đầu hợp lệ rồi để lại hậu tố mà không ném lỗi.

```java
Number parsed = df.parse("1,234.50oops");
// Có thể parse được 1234.50, dù phần "oops" còn lại.
```

Lời gọi parse thành công **không đủ** chứng minh chuỗi đã phù hợp hợp đồng nhập.

Nếu phải nhận trọn vẹn đầu vào, dùng **`ParsePosition`**:

```java
static java.math.BigDecimal parseAll(
        java.text.DecimalFormat format, String input) {

    java.text.ParsePosition position = new java.text.ParsePosition(0);
    Number result = format.parse(input, position);

    if (result == null
            || position.getIndex() != input.length()
            || !(result instanceof java.math.BigDecimal)) {
        throw new IllegalArgumentException("invalid decimal");
    }
    return (java.math.BigDecimal) result;
}
```

Hàm yêu cầu caller dùng formatter đã bật `setParseBigDecimal(true)`. Nó kiểm tra ba việc: parse không thất bại ngay, **tiêu thụ hết chuỗi**, và kết quả thực tế là `BigDecimal` (loại NaN/vô cực). `ParsePosition` tránh ngoại lệ `ParseException` do parser trả null cùng vị trí lỗi, nhưng đoạn mã chủ động ném `IllegalArgumentException` nếu không đạt hợp đồng.

**Giới hạn:** Tiêu thụ toàn bộ chuỗi **chưa đảm bảo nhóm dấu phẩy đặt đúng mọi vị trí hoặc đúng số chữ số phần thập phân theo chính sách ứng dụng**. Nếu chỉ cho phép hai chữ số thập phân hoặc định dạng nghiêm ngặt, bổ sung bước xác thực chuỗi đầu vào/format grammar tương ứng. Đừng chỉ tin vào pattern formatter như một bộ validator hoàn chỉnh.

### Khi nào nên dùng `BigDecimal(String)` thay vì `DecimalFormat`?

Nếu đầu vào là định dạng máy **không phụ thuộc locale** như `"1234.50"`, hãy cân nhắc `new BigDecimal("1234.50")`:

```java
java.math.BigDecimal value = new java.math.BigDecimal("1234.50");
```

Constructor này phân tích chuỗi thập phân theo cú pháp của `BigDecimal`, không tự hiểu dấu phân nhóm kiểu `"1,234.50"`. `DecimalFormat` phù hợp hơn khi cần nhập/xuất số theo **locale của người dùng**; `BigDecimal(String)` phù hợp khi có **định dạng decimal canonical** ở API hoặc file cấu hình.

## 7. DecimalFormat không thread-safe: vấn đề nằm ở đâu?

`DecimalFormat` là một đối tượng **mutable**, lưu pattern, ký hiệu, chế độ parse và trạng thái liên quan. Tài liệu Java yêu cầu nếu nhiều luồng cùng truy cập một formatter, ứng dụng phải **đồng bộ bên ngoài**.

Ví dụ không an toàn về mặt thiết kế:

```java
// KHÔNG nên dùng chung như thế này:
static final java.text.DecimalFormat SHARED =
    new java.text.DecimalFormat("#,##0.00");

// Thread A:
String output = SHARED.format(1234.5);

// Thread B:
Number number = SHARED.parse("1,234.50");
```

Đây là các mảnh mã minh họa, không phải file độc lập. `static final` chỉ làm tham chiếu không thể gán sang formatter khác; **không** làm đối tượng `DecimalFormat` bất biến hoặc biến các phương thức thành thread-safe.

Rủi ro khi truy cập cùng lúc không đồng bộ gồm **kết quả parse/format sai, trạng thái nội bộ bị ảnh hưởng hoặc hành vi không xác định ổn định**. Không nên tạo một “ví dụ race” và hứa cứ chạy là tái hiện, vì lỗi đồng thời có thể phụ thuộc lịch chạy, JDK và tải hệ thống.

Việc nhiều thread **chỉ gọi format** cũng không được coi mặc định là an toàn. Một formatter có thể sử dụng trạng thái nội bộ khi làm việc; không dựa vào cảm giác rằng “chỉ đọc” thì thread-safe. Khi vừa format/parse vừa thay đổi pattern, symbols hoặc `setParseBigDecimal` trên một instance chung, nguy cơ càng rõ.

## 8. Ba cách sử dụng formatter an toàn giữa nhiều luồng

### Cách A — tạo instance cục bộ: đơn giản và dễ đúng

```java
static java.math.BigDecimal parseAmount(String input) {
    java.text.DecimalFormat formatter = makeFormatter();
    return parseAll(formatter, input);
}

static java.text.DecimalFormat makeFormatter() {
    var symbols = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US);
    var formatter = new java.text.DecimalFormat("#,##0.00", symbols);
    formatter.setParseBigDecimal(true);
    return formatter;
}
```

Mã minh họa trong một class có các phương thức tương ứng. Mỗi lần gọi có một formatter riêng, không có đối tượng mutable dùng chung giữa threads. Cách này phù hợp khi **độ rõ ràng và an toàn quan trọng hơn chi phí khởi tạo** hoặc số lần gọi vừa phải.

**Ưu điểm:** không cần lock; cấu hình không rò giữa lời gọi. **Nhược điểm:** có chi phí tạo formatter và symbols mỗi lần. Nếu đây là điểm nóng về hiệu năng, hãy đo trước khi tối ưu.

### Cách B — `ThreadLocal<DecimalFormat>`: formatter riêng theo thread

```java
private static final ThreadLocal<java.text.DecimalFormat> LOCAL =
    ThreadLocal.withInitial(() -> {
        var symbols =
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US);
        var formatter = new java.text.DecimalFormat("#,##0.00", symbols);
        formatter.setParseBigDecimal(true);
        return formatter;
    });

static java.math.BigDecimal parseLocal(String input) {
    return parseAll(LOCAL.get(), input);
}
```

Mỗi luồng nhận một `DecimalFormat` riêng. Cần **không chuyển formatter từ một luồng sang luồng khác** và không thay đổi tùy chọn ở một lời gọi rồi giả định lần sau sẽ tự reset. Nếu sử dụng thread pool dài hạn và đối tượng trong `ThreadLocal` có vòng đời lớn, cân nhắc `LOCAL.remove()` khi phạm vi dùng kết thúc, hoặc đơn giản dùng formatter cục bộ để quản lý vòng đời rõ ràng hơn.

`ThreadLocal` chỉ ngăn *cùng instance* được chia sẻ ngẫu nhiên nếu sử dụng đúng, **không tự biến một formatter mutable thành immutable**. Việc phụ thuộc trạng thái của formatter cục bộ do thread trước trong pool để lại cũng có thể gây lỗi ngữ nghĩa.

### Cách C — đồng bộ bên ngoài khi thật sự phải dùng chung

```java
private static final java.text.DecimalFormat LOCKED = makeFormatter();

static java.math.BigDecimal parseLocked(String input) {
    synchronized (LOCKED) {
        return parseAll(LOCKED, input);
    }
}

static String formatLocked(java.math.BigDecimal amount) {
    synchronized (LOCKED) {
        return LOCKED.format(amount);
    }
}
```

Mọi truy cập vào cùng instance, **kể cả đọc/đổi cấu hình và lời gọi parse/format**, phải phối hợp sử dụng cùng một lock. Chỉ khóa một phương thức mà bỏ sót các đường dùng khác thì chưa giải quyết được vấn đề.

**Ưu điểm:** phù hợp khi bắt buộc chia sẻ một formatter. **Nhược điểm:** có lock contention, các luồng phải chờ; dễ mắc lỗi nếu có code khác dùng formatter ngoài lock. Tránh công khai instance `LOCKED` cho mã ngoài có thể gọi trực tiếp.

**Khuyến nghị thực hành:** Nếu không có lý do đặc biệt để chia sẻ, **tạo formatter riêng mỗi lần hoặc riêng mỗi thread** thường dễ lý giải hơn dùng một formatter chung có lock.

## 9. Locale, parsing và độ chính xác là ba quyết định độc lập

### Locale xác định dấu thập phân và dấu phân nhóm

`DecimalFormatSymbols` xác định ký hiệu dùng để đọc và hiển thị số. Ví dụ pattern `#,##0.00` với `Locale.US` hiển thị `1234.50` thành `"1,234.50"`, dấu **chấm** làm dấu thập phân, dấu **phẩy** phân nhóm. Trong các locale khác, ký hiệu có thể khác, kể cả ký tự khoảng trắng đặc biệt.

```java
var symbols = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US);
var df = new java.text.DecimalFormat("#,##0.00", symbols);
df.setParseBigDecimal(true);
```

**Đừng tạo formatter bằng locale mặc định ở nơi gửi rồi parse bằng giả định dấu phân cách US ở nơi nhận.** Định dạng giao tiếp máy nên được định nghĩa độc lập với UI locale; dữ liệu nhập từ người dùng cần gắn đúng locale và chính sách phân tích.

### Parse `BigDecimal` không quyết định làm tròn khi hiển thị

```java
var df = new java.text.DecimalFormat(
    "#,##0.00",
    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US)
);
df.setParseBigDecimal(true);
df.setRoundingMode(java.math.RoundingMode.HALF_UP);

java.math.BigDecimal amount = new java.math.BigDecimal("1.005");

String shown = df.format(amount); // "1.01"
```

`amount` vẫn là `1.005`; formatter chỉ tạo **chuỗi đã làm tròn cho hiển thị**. Nếu cần lưu giá trị đã làm tròn thành đúng hai chữ số thập phân, dùng API của `BigDecimal`, ví dụ `amount.setScale(2, RoundingMode.HALF_UP)`, và xác định rõ chính sách nghiệp vụ.

Đừng lấy `df.format(value)` rồi `df.parse(...)` làm cách làm tròn số học cốt lõi: quá trình round-trip qua string/locale làm tăng tính phức tạp và có thể mất thông tin (ví dụ số chữ số thập phân, negative zero hoặc biểu diễn chuỗi khác). Hãy tách **tính toán số** và **định dạng hiển thị**.

## 10. Chương trình Java 17+ chạy độc lập

Tạo tệp `BigDecimalParseDemo.java`:

```java
public class BigDecimalParseDemo {
    static java.text.DecimalFormat makeFormatter() {
        var symbols =
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US);
        var format = new java.text.DecimalFormat("#,##0.00", symbols);
        format.setParseBigDecimal(true);
        return format;
    }

    static java.math.BigDecimal parseAll(
            java.text.DecimalFormat format, String s) {

        java.text.ParsePosition pos = new java.text.ParsePosition(0);
        Number number = format.parse(s, pos);

        if (number == null
                || pos.getIndex() != s.length()
                || !(number instanceof java.math.BigDecimal)) {
            throw new IllegalArgumentException("invalid decimal");
        }
        return (java.math.BigDecimal) number;
    }

    public static void main(String[] args) throws Exception {
        var df = makeFormatter();

        Number precise = df.parse("9,007,199,254,740,993.00");
        System.out.println("precise type: "
            + precise.getClass().getSimpleName());
        System.out.println("precise value: "
            + ((java.math.BigDecimal) precise).toPlainString());

        Number cents = df.parse("1,234.50");
        System.out.println("cents: "
            + ((java.math.BigDecimal) cents).toPlainString());

        System.out.println("negative zero: "
            + df.parse("-0").getClass().getSimpleName()
            + " " + df.parse("-0"));

        df.setParseBigDecimal(false);
        Number ordinary = df.parse("9,007,199,254,740,993.00");
        System.out.println("ordinary type: "
            + ordinary.getClass().getSimpleName());
        System.out.println("ordinary value: " + ordinary);
        System.out.println("integer type: "
            + df.parse("42").getClass().getSimpleName());

        df.setParseBigDecimal(true);
        System.out.println("NaN type: "
            + df.parse("NaN").getClass().getSimpleName());
        System.out.println("Infinity type: "
            + df.parse(df.getDecimalFormatSymbols().getInfinity())
                .getClass().getSimpleName());

        java.text.ParsePosition pos = new java.text.ParsePosition(0);
        Number prefix = df.parse("1,234.50oops", pos);
        System.out.println("prefix parsed: " + prefix
            + ", consumed: " + pos.getIndex());

        System.out.println("strict valid: "
            + parseAll(df, "1,234.50").toPlainString());

        try {
            parseAll(df, "1,234.50oops");
        } catch (IllegalArgumentException ex) {
            System.out.println("strict trailing: rejected");
        }
        try {
            parseAll(df, "NaN");
        } catch (IllegalArgumentException ex) {
            System.out.println("strict NaN: rejected");
        }

        df.setRoundingMode(java.math.RoundingMode.HALF_UP);
        System.out.println("formatted: "
            + df.format(new java.math.BigDecimal("1.005")));
        System.out.println("input unchanged: "
            + new java.math.BigDecimal("1.005").toPlainString());
    }
}
```

Biên dịch/chạy:

```bash
javac BigDecimalParseDemo.java
java BigDecimalParseDemo
```

Kết quả stdout đã kiểm chứng bằng JDK:

```text
precise type: BigDecimal
precise value: 9007199254740993.00
cents: 1234.50
negative zero: BigDecimal 0
ordinary type: Long
ordinary value: 9007199254740993
integer type: Long
NaN type: Double
Infinity type: Double
prefix parsed: 1234.50, consumed: 8
strict valid: 1234.50
strict trailing: rejected
strict NaN: rejected
formatted: 1.01
input unchanged: 1.005
```

**Giải thích output:**

1. Parse BigDecimal giữ `9007199254740993.00` và `1234.50` ở dạng thập phân. `-0` trở thành `BigDecimal 0`.
2. Khi tắt `parseBigDecimal`, giá trị nguyên có thể trả `Long`, không phải lúc nào cũng trả `Double`. Với giá trị có phần lẻ không thể trả `Long`, parser có thể trả `Double` và gây sai số nhị phân.
3. NaN và vô cực vẫn là `Double`; chế độ parse BigDecimal không biến chúng thành đối tượng thập phân.
4. Parser đã tiêu thụ 8 ký tự của `"1,234.50oops"` và để lại hậu tố; `parseAll` kiểm tra toàn bộ đầu vào để từ chối.
5. `RoundingMode.HALF_UP` ảnh hưởng chuỗi **format** `1.005 → "1.01"`, không thay đổi giá trị `BigDecimal` gốc.

Ví dụ tạo một formatter **cục bộ cho phương thức main**, không đưa cùng instance vào nhiều thread. Đây là cách dùng an toàn về chia sẻ trạng thái ở phạm vi ví dụ; trong dịch vụ nhiều luồng, áp dụng mẫu của mục 8.

## 11. Checklist thiết kế API parse/format có độ tin cậy

- **Định nghĩa định dạng đầu vào:** chuỗi từ người dùng theo locale nào, hay chuỗi canonical cho hệ thống máy?
- **Quyết định kiểu kết quả:** `BigDecimal` cho số thập phân hữu hạn cần độ chính xác, hay `Double` khi chấp nhận số thực nhị phân?
- **Kiểm tra đầy đủ chuỗi:** dùng `ParsePosition` và đối chiếu `getIndex() == input.length()`; bổ sung validation nghiêm ngặt nếu yêu cầu dấu nhóm/số chữ số cụ thể.
- **Từ chối giá trị đặc biệt nếu không hợp lệ:** `NaN` và `Infinity` vẫn có thể trả `Double` dù `setParseBigDecimal(true)`.
- **Xác định quy tắc làm tròn riêng:** `RoundingMode` cho format/`BigDecimal.setScale` cho kết quả số học theo nghiệp vụ.
- **Tách formatter khỏi các luồng:** instance cục bộ, `ThreadLocal` dùng đúng, hoặc đồng bộ toàn bộ truy cập cùng một instance.
- **Tránh cấu hình mutable thay đổi giữa các lời gọi:** không để thread A đổi locale/pattern/parse mode trong khi thread B đang parse.
- **Kiểm thử đầu vào biên:** chuỗi dài, phần lẻ nhiều chữ số, âm, signed zero, NaN, vô cực, dấu nhóm sai, chuỗi có hậu tố, số lớn hơn độ chính xác của `double`.
- **Giám sát hiệu năng sau khi đúng:** khởi tạo formatter mỗi lần có chi phí, nhưng dùng shared instance không đồng bộ là cách tối ưu sai. Chỉ chọn phương án khác sau khi đo thực tế.

Độ an toàn **đồng thời** và độ chính xác **số học** là hai bài toán độc lập: bật `setParseBigDecimal` giúp tránh làm tròn qua `double` khi parse số hữu hạn, nhưng không thể loại data race. Ngược lại, khóa formatter không tự làm dữ liệu thập phân chính xác nếu vẫn parse ra `Double`.

## 12. Sai lầm thường gặp

1. **“Bật `setParseBigDecimal(true)` thì `parse` luôn trả `BigDecimal`.”** Sai: NaN/vô cực vẫn có thể là `Double`.
2. **“Tắt `setParseBigDecimal` luôn trả `Double`.”** Sai: giá trị nguyên vừa miền có thể trả `Long`.
3. **“`parse(String)` thành công thì đã đọc hết chuỗi.”** Sai: parser có thể chỉ nhận prefix; dùng `ParsePosition` nếu yêu cầu đầy đủ.
4. **“Đúng pattern `#,##0.00` thì chuỗi nhập chắc chắn có đúng hai chữ số phần lẻ.”** Sai: formatter pattern không thay thế validation theo nghiệp vụ.
5. **“`BigDecimal` tự lưu dấu của zero âm.”** Sai: `BigDecimal` không có signed zero như `double`.
6. **“`parseBigDecimal` giữ nguyên ký tự dấu phân nhóm và locale input trong object kết quả.”** Sai: đó là parse thành giá trị số; muốn lưu văn bản gốc phải lưu riêng.
7. **“`setRoundingMode` khiến parser parse các chữ số vào `BigDecimal` đã làm tròn.”** Sai: `setRoundingMode` dùng khi định dạng; muốn làm tròn dữ liệu số dùng `BigDecimal.setScale` rõ ràng.
8. **“`static final DecimalFormat` an toàn cho nhiều luồng.”** Sai: final chỉ giữ tham chiếu, không biến đối tượng mutable thành thread-safe.
9. **“Nếu các luồng chỉ gọi `format` thì chia sẻ formatter mặc nhiên an toàn.”** Sai: không có bảo đảm thread-safe chung.
10. **“`ThreadLocal` luôn phù hợp và không cần quản lý trạng thái.”** Sai: thread pool dài hạn có thể giữ instance và trạng thái qua các nhiệm vụ; nên tránh rò cấu hình/vòng đời.
11. **“Đồng bộ chỉ riêng lời gọi `parse`, còn `setParseBigDecimal` thì khỏi cần lock.”** Sai: mọi truy cập vào cùng formatter chia sẻ phải phối hợp theo cùng cơ chế.
12. **“`new BigDecimal(double)` có thể khôi phục chính xác chuỗi thập phân người dùng nhập ban đầu.”** Sai: số `double` đã làm tròn nhị phân không chứa đủ thông tin nguồn.
13. **“Dùng một formatter singleton sẽ chắc chắn nhanh nhất.”** Sai: đồng bộ gây tranh chấp; chi phí khởi tạo hoặc thread confinement phải được benchmark.
14. **“Dùng `DecimalFormat` để serialize mọi số sang format máy ổn định mà không chỉ định locale.”** Sai: locale mặc định có thể thay đổi và ảnh hưởng ký tự số.

## 13. Tự kiểm tra

**1. `DecimalFormat.parse(String)` được khai báo trả kiểu gì?** → `Number`.

**2. Bật `setParseBigDecimal(true)` làm gì?** → Parse các giá trị số hữu hạn thông thường thành `BigDecimal`, tránh đường chuyển qua `double`.

**3. Có trường hợp chế độ đó vẫn trả `Double` không?** → Có: `NaN` và `Infinity`.

**4. Nếu tắt `setParseBigDecimal`, kết quả luôn là `Double`?** → Không; parser có thể trả `Long` cho giá trị nguyên.

**5. `parse("1,234.50oops")` trả số có nghĩa chuỗi hợp lệ hoàn toàn không?** → Không. Cần `ParsePosition` và kiểm tra đã tiêu thụ hết chuỗi.

**6. Pattern hai số lẻ đảm bảo validate đúng hai số lẻ của chuỗi input không?** → Không; cần xác thực định dạng độc lập nếu đây là điều kiện bắt buộc.

**7. Parse `"-0"` thành `BigDecimal` có giữ signed zero không?** → Không; giá trị BigDecimal bằng 0.

**8. Có thể dùng một `static final DecimalFormat` từ nhiều luồng mà không đồng bộ không?** → Không có bảo đảm thread-safe; dùng instance riêng hoặc khóa đầy đủ.

**9. Cách đơn giản nhất để tránh chia sẻ formatter mutable giữa threads là gì?** → Tạo formatter cục bộ cho từng lời gọi.

**10. `setRoundingMode(HALF_UP)` có thay đổi object `BigDecimal("1.005")` sau `format` không?** → Không; chỉ chuỗi hiển thị bị làm tròn theo formatter.

**Ghi nhớ:** `setParseBigDecimal(true)` giúp `DecimalFormat` trả **`BigDecimal` cho số thập phân hữu hạn được parse**, nhưng không áp dụng cho `NaN`/vô cực, không tự kiểm tra toàn bộ chuỗi và không bảo vệ khỏi mọi mất mát biểu diễn gốc. `DecimalFormat` là **mutable và không thread-safe**: mỗi luồng/lần gọi cần formatter riêng hoặc mọi truy cập chung phải được đồng bộ. Hãy phân tách **xác thực input, parse chính xác, format làm tròn và quản lý đồng thời** thành bốn trách nhiệm rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-formatting-013">Không dùng chuỗi đã định dạng cho người đọc làm dạng trao đổi dữ liệu mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng chuỗi đã định dạng cho người đọc làm dạng trao đổi dữ liệu mặc định

Trong Java, một **giá trị số** và một **chuỗi trình bày giá trị số** không phải cùng một loại dữ liệu. Khi hiển thị, ứng dụng có thể dùng dấu phân nhóm hàng nghìn, dấu thập phân theo ngôn ngữ, ký hiệu tiền tệ, số chữ số làm tròn hay đơn vị đo để con người dễ đọc. Nhưng nếu mặc định dùng chính chuỗi **đã định dạng cho người đọc** để lưu file, truyền API hoặc tích hợp với hệ thống khác, dữ liệu có thể **bị đọc sai, không parse được hoặc mất độ chính xác**.

```java
double amount = 1_234_567.89;

System.out.println(String.format(
        java.util.Locale.US, "%,.2f", amount));
// 1,234,567.89

System.out.println(String.format(
        java.util.Locale.GERMANY, "%,.2f", amount));
// 1.234.567,89

System.out.println(Double.toString(amount));
// 1234567.89
```

Ba chuỗi biểu diễn cùng một `double` đầu vào, nhưng hai chuỗi đầu phục vụ **con người ở các locale khác nhau**. Chuỗi thứ ba là cách biểu diễn số `double` ổn định theo quy tắc Java, không phụ thuộc locale, tiện hơn cho một số hợp đồng trao đổi văn bản đã được **quy định rõ**.

**Quy tắc chính:** dữ liệu trao đổi giữa chương trình phải có **schema và định dạng máy** được định nghĩa tường minh. Định dạng trình bày giao diện (human display format) không nên trở thành wire format mặc định chỉ vì người dùng nhìn thấy nó.

## 1. Phân biệt value, presentation và wire representation

Ba tầng thường gặp:

| Tầng | Câu hỏi cần trả lời | Ví dụ |
| --- | --- | --- |
| **Giá trị nội bộ** | Hệ thống đang tính trên kiểu và giá trị nào? | `long 1234567L`, `double 1234.5`, `BigDecimal("1234.50")` |
| **Trình bày cho người đọc** | Người dùng ở locale nào, muốn bao nhiêu chữ số, đơn vị gì? | `"1.234,50"`, `"1,234.50"`, `"1 234,50 €"` |
| **Dữ liệu trao đổi** | Bên nhận sẽ đọc chính xác theo schema nào? | JSON number `1234.5`, JSON string `"1234.50"` với quy tắc decimal, hoặc binary int64 |

**Định dạng cho người đọc** là một phép biến đổi từ giá trị thành chuỗi để hiển thị. Nó có thể làm tròn, thêm dấu phân nhóm, thay ký hiệu decimal hoặc đưa thêm chữ. Một khi đã làm tròn cho UI, việc parse ngược chưa chắc phục hồi giá trị trước khi làm tròn.

**Định dạng trao đổi** cần xác định rõ tập ký tự, kiểu dữ liệu, signedness, đơn vị, cách biểu diễn phần lẻ, phiên bản schema, cách biểu diễn giá trị đặc biệt và quy tắc validation. Đây là hợp đồng giữa bên phát và bên nhận, **không phải** hiệu ứng phụ của cài đặt locale trên máy.

### Ví dụ điển hình

Ứng dụng có giá trị `1234.5`:

```text
Internal double:             1234.5
Hiển thị English (US):       1,234.50
Hiển thị German (Germany):   1.234,50
Wire decimal text (ví dụ):   1234.5
```

Nếu một API nhận chuỗi `"1.234,50"` nhưng giả định đó là số decimal dùng dấu chấm, nó có thể từ chối, cắt một phần chuỗi hoặc diễn giải sai. Nếu format cho UI vô tình trở thành dữ liệu lưu trữ, chỉ cần đổi locale của server hoặc người dùng đã đủ tạo lỗi tương thích.

## 2. Dấu phân nhóm và dấu thập phân không có nghĩa toàn cầu

Các ký tự dấu phẩy và dấu chấm có vai trò khác nhau tùy locale.

| Giá trị | Ví dụ US | Ví dụ Germany |
| ---: | --- | --- |
| 1234.5 với 2 chữ số lẻ | `1,234.50` | `1.234,50` |
| 1234567.89 với 2 chữ số lẻ | `1,234,567.89` | `1.234.567,89` |
| 1000 nếu phân nhóm | `1,000` | `1.000` |

Chuỗi `"1,234"` **mơ hồ** nếu không có locale: có thể được hiểu là một nghìn hai trăm ba mươi tư trong hệ US, hoặc một phẩy hai ba bốn trong hệ dùng dấu phẩy thập phân. Chuỗi `"1.234"` cũng có tình huống ngược lại.

Không chỉ có dấu chấm/phẩy: một số locale dùng khoảng trắng, khoảng trắng không ngắt dòng hoặc ký tự Unicode riêng làm dấu phân nhóm. Tiền tệ còn có ký hiệu và quy tắc vị trí ký hiệu khác nhau. Vì vậy, regex đơn giản để bỏ dấu phẩy hoặc dấu chấm **không phải một parser quốc tế an toàn**.

### Định dạng bằng `String.format`

```java
double value = 1_234_567.89;

String us = String.format(
        java.util.Locale.US, "%,.2f", value);
String de = String.format(
        java.util.Locale.GERMANY, "%,.2f", value);
```

Trong định dạng kiểu `%,.2f`:
- `,` là **cờ grouping** của `Formatter`; ký tự phân nhóm thực tế phụ thuộc locale.
- `.2` yêu cầu hai chữ số sau dấu thập phân trong chuỗi hiển thị.
- `f` yêu cầu định dạng số thực dạng fixed-point.
- `Locale.US` hoặc `Locale.GERMANY` quyết định ký tự hiển thị.

Mẫu `%,.2f` **không có nghĩa đầu ra luôn dùng dấu phẩy phân nhóm và dấu chấm thập phân**. Nó diễn đạt chính sách trình bày thông qua locale.

Đối với giao thức, chỉ dùng `String.format` nếu giao thức **quy định đúng** locale, số chữ số, quy tắc làm tròn, grouping và cách parse. Không tự dùng output của formatter UI làm nội dung mặc định cho JSON, CSV hay cơ sở dữ liệu.

## 3. Tác hại của việc parse chuỗi trình bày bằng parser máy

`Double.parseDouble` hiểu cú pháp số thực của Java theo hợp đồng riêng, **không** nhận định dạng phân nhóm số theo locale:

```java
double original = 1_234_567.89;
String shown = "1.234.567,89";

// Double.parseDouble(shown); // NumberFormatException.
double recovered = Double.parseDouble(
        Double.toString(original)); // Hoạt động.
```

`Double.parseDouble` không phải bộ parser tất cả các kiểu hiển thị địa phương. `Integer.parseInt("1,000")` cũng không hoạt động dù `"1,000"` trông rất bình thường với người dùng US.

Cần phân biệt:
- **`Double.parseDouble`/`Integer.parseInt`**: parser giá trị số theo các cú pháp riêng của Java, không tự nhận grouping locale.
- **`NumberFormat.parse`/`DecimalFormat.parse`**: parser theo quy tắc trình bày và locale; phù hợp khi nhận **input dành cho con người** với quy tắc cụ thể.
- **Parser của chuẩn dữ liệu**: ví dụ JSON parser, CSV reader + codec field, binary decoder; phải tuân theo **schema dữ liệu trao đổi**.

Nếu dữ liệu thực sự do người dùng gõ theo locale, việc dùng `NumberFormat` có thể hợp lý. Nhưng kết quả parse nên được **chuẩn hóa thành kiểu số** trước khi đi tiếp tới lớp xử lý và lưu trữ.

## 4. `NumberFormat.parse` có thể đọc được tiền tố hợp lệ rồi dừng

Một bẫy ít được để ý: parser định dạng cho người đọc có thể **chấp nhận một phần đầu chuỗi** mà không tiêu thụ hết dữ liệu. Chẳng hạn:

```java
java.text.NumberFormat german =
        java.text.NumberFormat.getNumberInstance(
                java.util.Locale.GERMANY);

java.text.ParsePosition position = new java.text.ParsePosition(0);
String input = "1.234,50junk";
Number number = german.parse(input, position);

System.out.println(number);              // 1234.5
System.out.println(position.getIndex()); // 8
System.out.println(input.length());      // 12
```

Ở đây chuỗi có phần cuối `"junk"`. Một kết quả `Number` khác null **không đồng nghĩa toàn bộ input đã hợp lệ**. Muốn validator chặt chẽ khi nhận dữ liệu do người dùng nhập, cần kiểm tra:

```java
boolean allConsumed = number != null
        && position.getIndex() == input.length();
```

Dù tiêu thụ hết chuỗi, còn phải kiểm tra miền giá trị, kiểu số trả về, ký tự cho phép, giới hạn độ dài và điều kiện nghiệp vụ. `NumberFormat` có thể dùng các chế độ parse khác nhau như `setParseIntegerOnly` hoặc `DecimalFormat.setParseBigDecimal`; kiểu `Number` trả về không nhất thiết là `BigDecimal` hay `Double` duy nhất.

**Không sử dụng parser trình bày địa phương làm bộ kiểm tra wire format không khai báo locale.** Khi parsing phụ thuộc cấu hình, hai máy/phiên bản hoặc hai người dùng có thể hiểu cùng chuỗi theo cách khác nhau.

## 5. Xác định wire format bằng schema rõ ràng

Một schema trao đổi số nguyên/số thực thường cần quy định:

1. **Loại giá trị:** signed 32-bit, signed 64-bit, unsigned, decimal cố định, binary float, phần trăm, hay định danh?
2. **Kiểu của trường:** JSON number, JSON string, CSV text field, binary integer/float, database numeric column?
3. **Cú pháp:** dấu thập phân `.` hay `,`, exponent có được phép không, có grouping không, có dấu `+` không?
4. **Độ chính xác và miền:** giá trị tối thiểu/tối đa, có cho số thập phân vô hạn hay độ phân giải cố định không?
5. **Làm tròn:** cho phép làm tròn ở lớp nào, theo `RoundingMode` nào nếu dùng decimal?
6. **Giá trị đặc biệt:** null, thiếu field, NaN, ±Infinity, ±0 được biểu diễn thế nào?
7. **Đơn vị:** giá trị là đồng, xu, giây, mili giây, mét, độ hay một đại lượng không đơn vị?
8. **Tương thích phiên bản:** khi schema đổi đơn vị, cách parse, kiểu hoặc ký hiệu, làm sao client cũ nhận ra?
9. **Giới hạn bảo mật:** độ dài đầu vào, độ lớn số mũ, ký tự Unicode, validation và thông báo lỗi.

Ví dụ một hợp đồng text decimal đơn giản có thể viết:

```text
Field: amount_decimal
Type on wire: JSON string
Grammar: optional ASCII '-' followed by digits, optional '.' and digits
Grouping: forbidden
Locale: fixed, not taken from client/server default
Precision/range: agreed by application schema
Example: "1234.50"
Java receiver: new BigDecimal(value) after validation
```

Đây là **định dạng do hệ thống tự quy định**, không phải chuẩn chung bắt buộc của mọi API. Điều quan trọng là cả hai phía hiểu cùng hợp đồng.

Một định dạng khác có thể dùng JSON number `1234.5` trực tiếp. Nó phù hợp nếu tất cả bên nhận có thể bảo toàn miền và độ chính xác cần thiết. Không có một cách biểu diễn tối ưu cho mọi nhu cầu.

## 6. `toString` và `parseXxx`: lựa chọn tốt hơn formatter UI khi hợp đồng phù hợp

Đối với số nguyên có dấu:

```java
int i = -42;
long l = 9_000_000_000L;

String encodedInt = Integer.toString(i);  // "-42"
String encodedLong = Long.toString(l);    // "9000000000"

int decodedInt = Integer.parseInt(encodedInt);
long decodedLong = Long.parseLong(encodedLong);
```

Các API này không tự thêm grouping, ký hiệu tiền tệ hay thay dấu thập phân theo default locale. Chúng có thể dùng trong **schema decimal ASCII có dấu** cho `int`/`long` nếu bên nhận đồng ý về cú pháp và miền.

Với cơ số khác 10:

```java
String hex = Integer.toString(255, 16);  // "ff"
int recovered = Integer.parseInt(hex, 16); // 255
```

`radix` cũng phải là một phần hợp đồng. `Integer.toString(value,16)` không tự tạo `0x`; parser `parseInt(text,16)` không tự bỏ `0x`. Không đưa chuỗi với tiền tố UI vào parser không hỗ trợ tiền tố ấy.

### Signed và unsigned là hai quy ước riêng

`Integer.toUnsignedString(-1)` trả `"4294967295"`, còn `Integer.toString(-1)` trả `"-1"`. Chuỗi khác dù mẫu bit `int` giống nhau. Nếu wire schema là unsigned 32 bit, bên nhận cần phân tích với `Integer.parseUnsignedInt` (hoặc mô hình số rộng hơn) và biết đây là giá trị unsigned.

Với `long`, `Long.toUnsignedString` và `Long.parseUnsignedLong` diễn đạt bit 64 theo nghĩa không dấu. Đừng mô tả số âm dạng hex “ffffffff” rồi gọi parser signed mặc định, vì có thể vượt miền và thất bại.

### `Double.toString` là formatter cho giá trị binary64, không phải tiền tệ

`Double.toString(double)` trả một biểu diễn số thực decimal **không phụ thuộc locale** cho giá trị binary64. Với số hữu hạn, chuỗi được thiết kế để parse bằng `Double.parseDouble` và nhận lại cùng giá trị `double`.

```java
double sample = 0.1 + 0.2;
String text = Double.toString(sample);

System.out.println(text); // 0.30000000000000004
System.out.println(Double.parseDouble(text) == sample); // true
```

Điều đó bảo toàn **giá trị nhị phân hiện có**, không chứng minh `0.1 + 0.2` là phép tính thập phân chính xác. `Double.toString` còn có dạng chuỗi `"NaN"`, `"Infinity"`, `"-Infinity"` cho giá trị đặc biệt, **không hợp lệ làm JSON number theo chuẩn JSON**.

Vì vậy, trong ứng dụng trao đổi JSON, dùng một **JSON serializer** đúng chuẩn, với chính sách xử lý số không hữu hạn; không nối chuỗi tùy tiện từ `Double.toString` để dựng JSON.

## 7. `BigDecimal`: giữ decimal có ý nghĩa nghiệp vụ

Khi dữ liệu mang nghĩa số thập phân chính xác (ví dụ lượng tiền, thuế suất, số lượng tính theo đơn vị nhỏ nhất), `BigDecimal` là một lựa chọn phổ biến. Nó khác `double` ở chỗ có thể biểu diễn chính xác số decimal như `0.1` khi được tạo từ **chuỗi decimal**.

```java
java.math.BigDecimal a = new java.math.BigDecimal("0.1");
java.math.BigDecimal b = new java.math.BigDecimal("0.2");

System.out.println(a.add(b).toPlainString()); // 0.3
```

Không xây `BigDecimal` từ chuỗi đã thêm grouping/đổi dấu thập phân:

```java
// new BigDecimal("1.234,50"); // NumberFormatException.

java.math.BigDecimal amount =
        new java.math.BigDecimal("1234.50");

System.out.println(amount.toPlainString()); // 1234.50
System.out.println(amount.scale());         // 2
```

`BigDecimal.toPlainString()` không dùng notation số mũ; `BigDecimal.toString()` có thể chọn cách biểu diễn exponent khi phù hợp:

```java
java.math.BigDecimal x = new java.math.BigDecimal("1E+7");
System.out.println(x.toString());      // 1E+7
System.out.println(x.toPlainString()); // 10000000
```

### Chọn `toString` hay `toPlainString`?

- `toString()` là biểu diễn chuẩn của `BigDecimal` có thể dùng exponent, được thiết kế cho các nhu cầu biểu diễn/parse và giữ thông tin scale theo định nghĩa API.
- `toPlainString()` phù hợp với schema chỉ nhận decimal digits và dấu chấm, **không cho exponent**; nhưng dạng plain có thể dài đáng kể khi số có exponent rất lớn.
- `stripTrailingZeros()`, `setScale(...)` và các phép làm tròn có thể thay đổi scale hoặc giá trị, nên chỉ áp dụng khi **schema quy định**. Không tùy ý strip `"1234.50"` thành `"1234.5"` nếu số chữ số lẻ/scale mang ý nghĩa.

Nếu yêu cầu giữ **scale** qua round trip, chú ý `toPlainString()` với các `BigDecimal` có scale âm có thể dựng lại cùng giá trị số nhưng scale khác khi parse. Cần dùng `BigDecimal.toString` hoặc lưu cả scale/representation khi đây là thành phần của hợp đồng. Đừng dùng `toPlainString` như cách “giữ mọi metadata số” phổ quát.

### `new BigDecimal(double)` khác `new BigDecimal(String)`

`new BigDecimal(0.1)` diễn đạt chính xác giá trị **xấp xỉ nhị phân** của `double 0.1`, vì thế chuỗi decimal của nó có thể rất dài. Nếu đầu vào thực sự là số thập phân do user gõ, ưu tiên **giữ text gốc hợp lệ** và tạo bằng `new BigDecimal("0.1")` thay vì cho nó đi qua `double` trước.

`BigDecimal.valueOf(0.1)` dùng biểu diễn chuỗi chuẩn của `double` nên thường nhận `BigDecimal("0.1")`, nhưng nó **không thể phục hồi số chữ số hoặc sai số bị mất trước đó** ở dữ liệu gốc. Cần xem hợp đồng nguồn và xử lý rounding rõ ràng.

## 8. JSON: numeric token không giống chuỗi hiển thị

JSON phân biệt **number** và **string**:

```json
{
  "quantity": 12,
  "ratio": 0.125,
  "amountDecimal": "1234.50",
  "accountCode": "00042"
}
```

Đây là ví dụ một **schema có chủ đích**:
- `quantity` là numeric integer.
- `ratio` là JSON number có cú pháp thập phân của JSON, không có grouping locale.
- `amountDecimal` là JSON **string** chứa decimal text chính xác theo hợp đồng.
- `accountCode` là string vì các số 0 đầu là một phần định danh.

Với JSON, numeric token dùng dấu chấm `.` cho phần fraction theo grammar chuẩn. Chuỗi `"1.234,50"` có thể xuất hiện như một **string** nhưng **không trở thành JSON number chỉ vì đặt trong JSON**.

### NaN và Infinity

JSON không có numeric token chuẩn cho `NaN`, `Infinity` hoặc `-Infinity`. Bởi vậy:

```java
double value = Double.NaN;
System.out.println(Double.toString(value)); // NaN
```

Chuỗi `NaN` đó **không phải** JSON number hợp lệ. Khi serialize giá trị chấm động, phải quy định xử lý: từ chối, dùng null với hợp đồng riêng, hoặc dùng một enum/string trạng thái được schema mô tả. Đừng ghi `{"value": NaN}` rồi nghĩ mọi JSON parser đều đọc được.

### Giới hạn của phía nhận

Một giá trị `long` hợp lệ có thể vượt miền **số nguyên liên tiếp chính xác** của JavaScript `Number`, tức lớn hơn `2^53` về độ lớn. Ví dụ:

```java
long id = 9_007_199_254_740_993L;
System.out.println(Long.toString(id)); // 9007199254740993
System.out.println((double) id);       // 9.007199254740992E15
```

`double` không biểu diễn chính xác số nguyên này; nhiều client xử lý JSON number bằng binary64 có cùng rủi ro. Với **ID hoặc số nguyên lớn cần chính xác tuyệt đối**, có thể quy định truyền **decimal string** và parse vào `long`/`BigInteger` phù hợp ở phía nhận, thay vì JSON number tùy tiện.

Tuy nhiên, **không phải mọi số JSON phải chuyển sang string**. Numeric token vẫn là lựa chọn tự nhiên cho các số trong miền/độ chính xác đã kiểm soát, miễn hai phía dùng model tương thích.

### Tránh tự nối chuỗi JSON

Không ghép:

```java
// KHÔNG nên làm một JSON serializer tổng quát như vậy:
String json = "{\\"value\\":" + Double.toString(value) + "}";
```

Đoạn trên không kiểm tra NaN/Infinity, không xử lý object string escaping, field optional, schema hay các quy tắc serialization khác. Hãy dùng thư viện JSON phù hợp của dự án để serialize **giá trị có kiểu**, sau khi chính sách precision/finite/null đã được xác định.

## 9. CSV và các file văn bản: cần schema cho từng cột

CSV là ví dụ rõ về nguy cơ nhầm display với data. Giả sử dấu phân tách cột là dấu phẩy:

```text
id,quantity,price
42,3,1234.50
```

Một chuỗi tiền tệ hiển thị kiểu US `"1,234.50"` nếu viết thẳng vào cột price sẽ chứa **dấu phẩy có vai trò phân tách cột**. Nếu thực sự muốn lưu text có dấu phẩy, cần **CSV escaping/quoting** theo quy tắc CSV được chọn:

```text
id,quantity,price_display
42,3,"1,234.50"
```

Nhưng CSV quoting chỉ bảo vệ **cấu trúc hàng/cột**, nó **không bảo đảm** bên nhận sẽ hiểu decimal format US. Một schema ổn định thường quy định riêng rằng cột `price` có định dạng decimal với dấu chấm, không grouping và đơn vị rõ.

Nếu ứng dụng cần export CSV cho **người dùng mở trong spreadsheet**, có thể cần format theo locale và công cụ nhận. Đó là **xuất báo cáo phục vụ con người**, khác **CSV integration feed** giữa các dịch vụ. Không nên dùng cùng một export mode cho cả hai nếu hợp đồng không tương thích.

### Ký tự phân tách và ký tự số có thể xung đột

Ở locale dùng dấu phẩy thập phân, CSV dùng dấu phẩy làm delimiter càng dễ bị mơ hồ. Có nơi dùng dấu chấm phẩy làm delimiter trong file văn bản cho spreadsheet. Không tự suy rằng mọi tệp `.csv` đều có quy ước giống nhau; cần biết schema, delimiter, encoding, quoting và cách parse decimal.

### Những dữ liệu trông như số nhưng không nên chuyển sang numeric

Mã bưu chính, số chứng từ, số điện thoại, số tài khoản và ID có số 0 đầu là **chuỗi định danh**, không phải đối tượng để cộng trừ. Nếu đưa `"00042"` qua `Integer.parseInt` rồi `Integer.toString` sẽ chỉ còn `"42"`. Đừng mất thông tin vì cho rằng “ký tự toàn số thì phải lưu số”.

## 10. Lưu trữ database và thông điệp giữa dịch vụ

Cùng một nguyên tắc áp dụng cho database:

- Nếu column có ý nghĩa **số nguyên**, dùng kiểu nguyên phù hợp với miền.
- Nếu column là số decimal cần chính xác, dùng `DECIMAL`/`NUMERIC` với precision/scale rõ, và Java `BigDecimal` nếu driver/API thích hợp.
- Nếu column là mã định danh, dùng text hoặc kiểu chuyên biệt đúng ngữ nghĩa.
- Nếu giao diện muốn hiển thị tiền tệ, chỉ **format tại lớp trình bày** khi biết locale/ngôn ngữ/quy tắc.

Không nên lưu chuỗi `"1.234,50 €"` làm giá trị duy nhất của cột tiền chỉ vì UI hiển thị như vậy: truy vấn sắp xếp, lọc, tính tổng, đổi đơn vị và đổi locale sau này sẽ khó khăn hơn. Dữ liệu chuẩn hóa nên tách **giá trị, currency code, đơn vị và metadata** theo schema.

Nếu dùng JDBC, hãy dùng các setter/getter có kiểu (`setLong`, `setBigDecimal`...) phù hợp với schema/driver, thay vì `setString` một chuỗi đã format cho người đọc rồi hy vọng database nhận biết đúng.

### Tách rõ luồng dữ liệu

```text
Input của người dùng
       |
       v
Validation + parse theo locale/chính sách nhập
       |
       v
Giá trị có kiểu: int / long / BigDecimal / double
       |                         |
       |                         +--> Codec wire theo schema -> API/file/DB
       |
       +--> Formatter theo locale -> UI/report
```

Không nên cho nhánh **Formatter theo locale** chảy ngược thành nhánh **Codec wire** mà không có hợp đồng parse tương ứng và kiểm tra chính xác.

## 11. Chương trình Java 17+ chạy độc lập

Lưu tệp `HumanVsWireDemo.java`:

```java
import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class HumanVsWireDemo {
    static String parseDoubleResult(String text) {
        try {
            return Double.toString(Double.parseDouble(text));
        } catch (NumberFormatException ex) {
            return "INVALID";
        }
    }

    public static void main(String[] args) {
        double amount = 1_234_567.89;

        System.out.println("Human US: "
                + String.format(Locale.US, "%,.2f", amount));
        System.out.println("Human DE: "
                + String.format(Locale.GERMANY, "%,.2f", amount));
        System.out.println("Machine double: "
                + Double.toString(amount));

        System.out.println("Parse human DE as double: "
                + parseDoubleResult("1.234.567,89"));
        System.out.println("Parse machine: "
                + parseDoubleResult(Double.toString(amount)));

        NumberFormat german =
                NumberFormat.getNumberInstance(Locale.GERMANY);
        ParsePosition pos = new ParsePosition(0);
        String untrusted = "1.234,50junk";
        Number parsed = german.parse(untrusted, pos);
        System.out.println("Lenient locale parse: " + parsed
                + "; consumed=" + pos.getIndex()
                + "/" + untrusted.length());

        BigDecimal exact = new BigDecimal("1234.50");
        System.out.println("Exact decimal plain: "
                + exact.toPlainString());
        System.out.println("Decimal scale: " + exact.scale());

        System.out.println("Scientific BigDecimal: "
                + new BigDecimal("1E+7").toString());
        System.out.println("Plain BigDecimal: "
                + new BigDecimal("1E+7").toPlainString());

        System.out.println("Exact decimal 0.1+0.2: "
                + new BigDecimal("0.1")
                    .add(new BigDecimal("0.2")).toPlainString());
        System.out.println("Double 0.1+0.2: "
                + Double.toString(0.1 + 0.2));

        long bigId = 9_007_199_254_740_993L;
        System.out.println("ID as long string: "
                + Long.toString(bigId));
        System.out.println("ID as double: "
                + Double.toString((double) bigId));

        System.out.println("NaN is finite? "
                + Double.isFinite(Double.NaN));
        System.out.println("Infinity is finite? "
                + Double.isFinite(Double.POSITIVE_INFINITY));
        System.out.println("German decimal code point: "
                + (int) DecimalFormatSymbols
                    .getInstance(Locale.GERMANY)
                    .getDecimalSeparator());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 HumanVsWireDemo.java
java HumanVsWireDemo
```

Stdout trên JDK 17+ với dữ liệu locale tương ứng:

```text
Human US: 1,234,567.89
Human DE: 1.234.567,89
Machine double: 1234567.89
Parse human DE as double: INVALID
Parse machine: 1234567.89
Lenient locale parse: 1234.5; consumed=8/12
Exact decimal plain: 1234.50
Decimal scale: 2
Scientific BigDecimal: 1E+7
Plain BigDecimal: 10000000
Exact decimal 0.1+0.2: 0.3
Double 0.1+0.2: 0.30000000000000004
ID as long string: 9007199254740993
ID as double: 9.007199254740992E15
NaN is finite? false
Infinity is finite? false
German decimal code point: 44
```

`44` là mã số Unicode/ASCII của ký tự dấu phẩy (`,`). Dòng `Lenient locale parse` cho thấy parser đã đọc `"1.234,50"` nhưng **không tiêu thụ `"junk"`**; đây là lý do cần kiểm tra `ParsePosition` nếu biến nó thành validation.

Các kết quả định dạng locale hiển thị như ví dụ phụ thuộc vào **dữ liệu locale của JDK**; với giao thức lâu dài, càng không nên để cấu hình locale trở thành hợp đồng wire. Các kết quả `Double.toString`, `Long.toString` và `BigDecimal` minh họa chuỗi số không phải số đã định dạng cho UI.

## 12. Thiết kế parser / serializer theo ranh giới trách nhiệm

### Tại nơi người dùng nhập

1. Xác định ngôn ngữ/locale của người dùng và loại ô nhập.
2. Hiển thị gợi ý rõ về dấu thập phân, dấu phân nhóm và đơn vị.
3. Parse bằng bộ parser dành cho input đó; kiểm tra toàn bộ chuỗi đã tiêu thụ.
4. Xác thực miền, precision, scale, kiểu và null.
5. Biến kết quả thành **giá trị có kiểu** để tầng nghiệp vụ sử dụng.

### Tại nơi hệ thống xuất API/file

1. Chọn JSON/CSV/protobuf/SQL/... theo giao thức thật, không chọn output trực tiếp của `NumberFormat` theo cảm tính.
2. Ghi schema rõ số nguyên hay decimal, đơn vị, cách xử lý null và số đặc biệt.
3. Dùng serializer/codec tiêu chuẩn hoặc một codec được kiểm thử chặt chẽ.
4. Bảo toàn chính xác theo yêu cầu của phía nhận; chú ý giới hạn IEEE binary64 cho integer lớn.
5. Validate khi nhận ngược; **không tin** rằng mọi chuỗi đến từ hệ thống khác đều hợp lệ.
6. Kiểm thử round trip và tương thích chéo các locale/JDK/đối tác.

### Tại nơi hiển thị

1. Nhận **giá trị đã có kiểu** từ nghiệp vụ hoặc API.
2. Chọn locale người dùng, kiểu tiền tệ, quy tắc rounding và số chữ số.
3. Format thành một chuỗi để đọc, không sử dụng nó làm nguồn dữ liệu tính toán.
4. Nếu UI cần người dùng chỉnh sửa, parse trở lại **qua lớp input** với validation, không đưa chuỗi display xuyên qua các service như một “numeric value”.

Các bước tách riêng này cũng giúp test dễ hơn: codec có test tính đúng chuẩn, logic nghiệp vụ có test số học, còn UI format có test locale mà không ảnh hưởng wire protocol.

## 13. Các trường hợp đặc biệt cần ghi hợp đồng rõ

### 13.1. Làm tròn để hiển thị

`String.format(Locale.US,"%.2f",value)` có thể rút số chữ số hiển thị xuống hai chữ số phần lẻ. Nếu giá trị gốc có nhiều chữ số hơn, chuỗi hiển thị đã **mất thông tin**. Không parse lại nó để lưu thay giá trị gốc trừ khi nghiệp vụ thực sự muốn làm tròn tại bước đó.

Ví dụ giá trị `1234.567` hiển thị thành `"1,234.57"` với nhóm và 2 chữ số lẻ; parse ngược `1234.57` không thể hồi phục `1234.567`.

### 13.2. Giá trị âm không, Infinity và NaN

Trong `double` có `-0.0` và `+0.0`, cùng `NaN`/`±Infinity`. Một formatter UI có thể xử lý hoặc hiển thị chúng theo chính sách địa phương. Với JSON number chuẩn, các giá trị NaN/Infinity không hợp lệ; còn signed zero có thể cần kiểm tra nghĩa ở phía nhận. Nếu các trạng thái đặc biệt quan trọng, **schema phải định nghĩa riêng**, không chỉ dùng kết quả `toString`.

### 13.3. Precision và exponent

Một JSON number có thể chứa ký pháp exponent, nhưng bên nhận có thể chuyển nó sang binary64, `BigDecimal` hoặc kiểu số khác. Dùng `BigDecimal.toPlainString` khi hợp đồng cấm exponent, nhưng phải giới hạn độ lớn/độ dài để tránh sinh chuỗi khổng lồ từ giá trị exponent không tin cậy.

### 13.4. Đơn vị và phần trăm

Chuỗi UI `"12.5%"` là biểu diễn cho người đọc: số thật trong hệ thống có thể là `0.125`, `12.5` hoặc một giá trị có đơn vị “percent” tùy hợp đồng. Một phép xóa ký tự `%` không xác định được cách chuẩn hóa. Tương tự, `"1,2 km"` cần xử lý đơn vị, decimal locale và quy tắc đổi km sang m, không chỉ gọi `Double.parseDouble`.

### 13.5. Kiểu số nguyên rất lớn

Mã định danh `9007199254740993` vừa `long` Java nhưng vượt khoảng liên tiếp chính xác của JavaScript `Number`. Một schema có thể giữ nó như JSON string decimal. Với ứng dụng chỉ Java-to-Java sử dụng parser long chính xác và miền có kiểm soát, numeric token cũng có thể phù hợp. **Lựa chọn dựa trên toàn bộ hệ sinh thái bên nhận**, không chỉ code Java phía gửi.

## 14. Bộ test nên có khi xây định dạng trao đổi

| Nhóm test | Input/góc cạnh | Kỳ vọng |
| --- | --- | --- |
| Locale US/DE | `1234.5` | UI format khác, wire format không tùy tiện thay đổi |
| Parse human | `"1.234,50"` | Chỉ parser locale phù hợp mới nhận theo policy |
| Parse machine | `"1234.50"` | Codec decimal nhận đúng, không grouping |
| Grouping lạ | `"1,23,4.5"` | Reject nếu schema không cho grouping |
| Có dữ liệu thừa | `"1.234,50junk"` | Full-consumption validation phải từ chối |
| Precision decimal | `"0.1"` cộng `"0.2"` | BigDecimal đúng `0.3` |
| Trailing zeros/scale | `"1234.50"` | Giữ scale nếu schema yêu cầu |
| Exponent | `"1E+7"` | Định nghĩa có cho exponent không |
| Số nguyên lớn | `"9007199254740993"` | Bảo toàn trên mọi client yêu cầu exact |
| Non-finite | NaN, Infinity | Không sinh JSON numeric token không hợp lệ |
| Âm zero | `-0.0` | Xác định có cần phân biệt dấu không |
| Định danh | `"00042"` | Không làm mất số 0 đầu |
| CSV quoting | `"1,234.50"` | Trường không bị tách sai cột |
| Ký tự Unicode | NBSP, chữ số Unicode | Policy chấp nhận/từ chối rõ ràng |
| Giá trị quá dài | Nhiều nghìn chữ số/số mũ lớn | Giới hạn tài nguyên và thông báo lỗi |
| Default locale bị đổi | Locale JVM khác giữa các host | Wire payload không đổi với cùng giá trị/schema |

Các bài kiểm thử cần bao gồm cả **giá trị qua lại** và **cú pháp**. Nếu một parser quá dễ dãi tự bỏ qua ký tự lạ, round-trip các giá trị hợp lệ vẫn có thể qua hết trong khi input độc hại được chấp nhận.

## 15. Các sai lầm thường gặp

| Sai lầm | Hệ quả | Cách làm phù hợp |
| --- | --- | --- |
| Dùng `NumberFormat.getInstance().format(x)` rồi gửi API | Phụ thuộc default locale | Xác định wire codec và schema riêng |
| Parse output locale bằng `Double.parseDouble` | Gặp grouping/decimal comma sẽ lỗi | Parse UI bằng parser theo locale; wire bằng parser ổn định |
| Dùng `NumberFormat.parse` làm strict validator | Có thể chỉ đọc đầu chuỗi | Kiểm tra `ParsePosition` đã đi hết |
| Bỏ mọi dấu phẩy/chấm để “chuẩn hóa” | Có thể biến thập phân thành số lớn hơn | Validate đúng locale/grammar trước |
| Gửi giá trị tiền bằng `String.format("%.2f")` | Làm tròn và mất dữ liệu gốc | BigDecimal + policy rounding đúng thời điểm |
| Cho rằng `Double.toString` giữ decimal gốc hoàn hảo | Chỉ bảo toàn giá trị binary64 hiện có | Giữ input decimal dưới dạng BigDecimal/String nếu cần |
| Tạo `BigDecimal` từ một `double` đã mất thông tin | Không khôi phục số decimal ban đầu | Parse từ text decimal đúng schema |
| Gửi `NaN` thành JSON number | Không phải JSON chuẩn | Policy non-finite rõ ràng trước serialize |
| Gửi ID long lớn dưới JSON number mà bỏ qua client JS | Có thể mất độ chính xác | Decimal string theo schema khi cần |
| Đổi `"00042"` thành số để “gọn” | Mất số 0 đầu | Giữ mã định danh ở kiểu String |
| Dùng `toPlainString` cho exponent rất lớn không giới hạn | Chuỗi có thể quá lớn | Giới hạn miền/độ dài khi encode |
| Dùng output `String.format` để lưu DB numeric | Chuỗi có thể bị diễn giải sai | JDBC setter theo kiểu số và schema |
| Coi CSV quoting là giải pháp parse decimal | Chỉ bảo vệ cấu trúc trường | Quy định codec cột numeric |
| Thay đổi locale JVM rồi API response đổi theo | Phá tương thích và test | Không dùng locale mặc định ở wire layer |
| Không ghi đơn vị và scale | Bên nhận tính sai ý nghĩa số | Schema có đơn vị/currency/scale rõ ràng |
| Dùng cùng formatter cho UI và integration | Trộn hai loại hợp đồng | Tách formatter trình bày khỏi serializer |

## 16. Câu hỏi tự kiểm tra

1. Vì sao `"1,234.50"` và `"1.234,50"` có thể hiển thị cùng giá trị nhưng không nên dùng làm một wire format không khai báo locale?
2. `Double.parseDouble("1.234.567,89")` cho kết quả gì? Muốn nhận input người dùng Đức, API nào liên quan?
3. Vì sao dùng `NumberFormat.parse` mà không kiểm tra `ParsePosition` có thể bỏ lọt `"1.234,50junk"`?
4. `Integer.toString`/`Long.toString` và `NumberFormat` khác nhau ở mục tiêu nào?
5. `Double.toString(0.1+0.2)` có bảo đảm kết quả đúng 0.3 theo số thập phân hay chỉ bảo toàn giá trị `double` hiện có?
6. Vì sao `new BigDecimal("1234.50")` có lợi hơn việc parse chuỗi hiển thị với grouping rồi đưa qua `double` nếu cần exact decimal?
7. `BigDecimal.toString` và `toPlainString` khác nhau khi giá trị được tạo từ `"1E+7"` như thế nào?
8. Vì sao `NaN` là một trạng thái `double` hợp lệ nhưng không thể ghi trực tiếp làm JSON numeric token chuẩn?
9. Một ID kiểu `long` bằng `9007199254740993` có vấn đề gì khi client dùng JavaScript `Number`?
10. CSV có dấu phẩy ngăn cột: nếu một field chứa `"1,234.50"`, bước nào giữ được cấu trúc cột? Bước nào định nghĩa ý nghĩa numeric của field?
11. Vì sao mã `"00042"` thường cần giữ dưới dạng `String` dù toàn chữ số?
12. Tầng UI, tầng nghiệp vụ và tầng wire nên mỗi tầng giữ loại giá trị nào?
13. Trong schema decimal string, phải ghi những điều gì về ký tự dấu, radix, exponent, grouping, precision/scale và đơn vị?
14. Nếu một formatter làm tròn 1234.567 thành `"1,234.57"`, parse lại có thể phục hồi số gốc không?
15. Làm sao test rằng thay đổi `Locale.getDefault()` không làm đổi JSON/CSV integration payload theo cùng schema?

## 17. Tài liệu tham khảo chính thức

- [Java SE 17 — `NumberFormat`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/NumberFormat.html): định dạng và parse số theo locale, các tùy chỉnh grouping/precision.
- [Java SE 17 — `DecimalFormat`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormat.html): pattern trình bày decimal và tính chất parser.
- [Java SE 17 — `ParsePosition`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/ParsePosition.html): vị trí parser đã tiêu thụ và thông tin lỗi.
- [Java SE 17 — `Double`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): `toString`, `parseDouble`, non-finite và quy tắc binary64.
- [Java SE 17 — `BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `toString`, `toPlainString`, scale, decimal exact.
- [RFC 8259 — The JavaScript Object Notation (JSON) Data Interchange Format](https://www.rfc-editor.org/rfc/rfc8259): JSON number grammar, interoperability, non-finite values không được phép.
- [Java SE 17 — `Formatter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Formatter.html): ký hiệu grouping, precision và locale trong output trình bày.

**Kết luận:** Chuỗi định dạng theo locale thích hợp cho **UI và báo cáo con người**, nhưng không phải wire format đáng tin cậy mặc định. Hãy trao đổi **giá trị có kiểu** thông qua **schema/serializer ổn định**; với số thập phân cần chính xác, xác định decimal text và BigDecimal; với `long` lớn, cân nhắc client JS; với floating-point, quy định chính sách NaN/Infinity. Chỉ tại lớp hiển thị mới chọn cách phân nhóm, tiền tệ, ký hiệu thập phân, đơn vị và làm tròn phù hợp người đọc.

</details>

- [Quay lại đầu trang](#back-to-top)
