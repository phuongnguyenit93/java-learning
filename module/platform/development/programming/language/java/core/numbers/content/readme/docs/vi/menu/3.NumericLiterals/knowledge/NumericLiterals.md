<a id="back-to-top"></a>

# Literal và cơ số

## Menu
- [Literal số nguyên và kiểu int mặc định](#numeric-literals-001)
- [Hậu tố L cho long và thời điểm literal vượt phạm vi](#numeric-literals-002)
- [Thập phân, nhị phân, bát phân và thập lục phân](#numeric-literals-003)
- [Bẫy số 0 ở đầu literal bát phân](#numeric-literals-004)
- [Dấu gạch dưới trong literal và các vị trí không hợp lệ](#numeric-literals-005)
- [Literal số thực, ký hiệu mũ và hậu tố F, D](#numeric-literals-006)
- [Literal dấu phẩy động thập lục phân](#numeric-literals-007)
- [Dấu âm là toán tử chứ không phải một phần của literal](#numeric-literals-008)
- [Literal, biểu thức hằng và khác biệt với phân tích String](#numeric-literals-009)
- [Đọc mẫu bit của literal hex có bit dấu được bật](#numeric-literals-010)

## <a id="numeric-literals-001">Literal số nguyên và kiểu int mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Literal số nguyên và kiểu `int` mặc định trong Java

**Literal số nguyên** (*integer literal*) là số nguyên được viết trực tiếp trong mã nguồn Java: `42`, `0b101010`, `052`, `0x2A`. Các cách viết này có cơ số khác nhau nhưng cùng biểu diễn giá trị thập phân 42. **Theo mặc định literal số nguyên không có hậu tố `L` có kiểu `int`; có hậu tố `L` (hoặc `l`) thì có kiểu `long`.** Cơ số không quyết định kiểu. Phạm vi và ý nghĩa của literal thập phân và phi thập phân có vài điểm khác nhau mà người học cần nắm vững.

Bài học nói về *literal và kiểu của biểu thức lúc biên dịch*, không phải *giá trị mặc định của field* hay *phân tích chuỗi số lúc chạy*.

## 1. Bốn hệ cơ số của literal số nguyên

| Dạng | Ký hiệu đầu | Chữ số hợp lệ | Ví dụ | Giá trị thập phân |
| --- | --- | --- | --- | --- |
| Decimal (thập phân, cơ số 10) | Không có tiền tố đặc biệt; số thông thường | `0` đến `9` | `42` | 42 |
| Binary (nhị phân, cơ số 2) | `0b` hoặc `0B` | `0`, `1` | `0b101010` | 42 |
| Octal (bát phân, cơ số 8) | Số `0` ở đầu một literal có nhiều chữ số | `0` đến `7` | `052` | 42 |
| Hexadecimal (thập lục phân, cơ số 16) | `0x` hoặc `0X` | `0`–`9` và `A`–`F` (không phân biệt hoa/thường) | `0x2A` | 42 |

```java
int decimal = 42;      // 42
int binary = 0b101010; // 32 + 8 + 2 = 42
int octal = 052;       // 5 * 8 + 2 = 42
int hex = 0x2A;        // 2 * 16 + 10 = 42
```

Đây là **đoạn minh họa phép gán**, không phải một class độc lập. Sau khi biên dịch, các biến đều có giá trị nguyên 42. Chương trình không giữ lại “cơ số đã gõ” trong giá trị `int`. Vì vậy `System.out.println(0x2A)` sẽ in `42` theo mặc định, không tự in `0x2A`.

Cơ số 2 hữu ích khi nhìn mặt nạ bit, cơ số 16 giúp nhóm bit cho byte và cờ trạng thái; cơ số 10 thuận tiện cho số lượng thông thường. Cơ số 8 ít dùng hơn vì `0` đứng đầu dễ tạo bẫy.

## 2. Kiểu `int` mặc định và hậu tố `L` cho `long`

Cả bốn cách viết `7`, `0b111`, `07`, `0x7` đều có **kiểu `int`**. Thêm `L` vào cuối tạo literal **kiểu `long`**: `7L`, `0b111L`, `07L`, `0x7L`.

| Literal | Kiểu | Giá trị |
| --- | --- | --- |
| `100`, `0b1100100`, `0144`, `0x64` | `int` | 100 |
| `100L`, `0b1100100L`, `0144L`, `0x64L` | `long` | 100 |
| `0xFFFF_FFFF` | `int` | -1 |
| `0xFFFF_FFFFL` | `long` | 4294967295 |

`l` chữ thường cũng hợp lệ, nhưng nên dùng **`L` hoa** để tránh nhầm với `1`.

Một literal `int` có thể được *mở rộng* để gán vào `long` khi giá trị của nó hợp lệ:

```java
long a = 100;              // literal int rồi mở rộng thành long
long b = 100L;             // literal long ngay từ đầu
long c = 2_147_483_648L;   // cần hậu tố L vì lớn hơn int tối đa
// long bad = 2_147_483_648; // lỗi biên dịch: literal decimal int quá lớn
```

**Kiểu của biến đích không thay đổi quá trình phân tích literal.** `long bad = 3000000000;` không hợp lệ, dù biến `bad` có kiểu `long`. Phải viết `3_000_000_000L`. Hậu tố `L` thuộc chính literal, không tự áp dụng cho một phép cộng hay phép nhân đã thực hiện trước đó.

Một cách chứng minh kiểu literal là dùng method overload:

```java
static String category(int value) {
    return "int";
}
static String category(long value) {
    return "long";
}
// category(42)  -> "int"
// category(42L) -> "long"
```

Hai method là **đoạn khai báo bên trong class**, không độc lập. `42` chọn overload `int`; `42L` chọn overload `long`.

## 3. Phạm vi của literal thập phân và dấu âm đơn ngôi

Primitive `int` có phạm vi `-2_147_483_648` đến `2_147_483_647`; primitive `long` có phạm vi `-9_223_372_036_854_775_808L` đến `9_223_372_036_854_775_807L`.

Literal **thập phân** không hậu tố thông thường nhận các trị từ `0` đến `2_147_483_647`. Java dành ngoại lệ cho token `2147483648` khi nó là toán hạng của phép âm đơn ngôi `-`, để cho phép viết `-2147483648`, tức `Integer.MIN_VALUE`. Ngoại lệ tương tự cho `9223372036854775808L` trong `-9223372036854775808L`.

```java
int max = 2_147_483_647;
int min = -2_147_483_648;
long maxLong = 9_223_372_036_854_775_807L;
long minLong = -9_223_372_036_854_775_808L;

// int badInt = 2_147_483_648;  // lỗi nếu không là toán hạng của dấu âm
// long badLong = 9_223_372_036_854_775_808L; // lỗi nếu không là toán hạng của dấu âm
```

Về cú pháp, `-42` gồm **toán tử `-`** và **literal `42`**, chứ dấu âm không nằm trong token literal. Đây là lý do trường hợp tối thiểu của kiểu nguyên cần một ngoại lệ ngôn ngữ. Nếu mục tiêu là ghi rõ giới hạn, có thể dùng `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `Long.MIN_VALUE` và `Long.MAX_VALUE`.

## 4. Literal hex/nhị phân được diễn giải như mẫu bit có dấu

Các literal phi thập phân (nhị phân, bát phân, hex) thể hiện **mẫu bit**. Literal không `L` phải vừa trong độ rộng 32 bit cho `int`; có `L` là độ rộng 64 bit cho `long`. Khi bit dấu bật, diễn giải bù hai (*two's complement*) cho một giá trị âm.

| Literal | Kiểu | Giá trị |
| --- | --- | --- |
| `0x7FFF_FFFF` | `int` | 2147483647 |
| `0x8000_0000` | `int` | -2147483648 |
| `0xFFFF_FFFF` | `int` | -1 |
| `0b11111111_11111111_11111111_11111111` | `int` | -1 |
| `0xFFFF_FFFFL` | `long` | 4294967295 |
| `0x1_0000_0000L` | `long` | 4294967296 |

Ví dụ:

```java
int allBitsOne = 0xFFFF_FFFF;   // -1: cả 32 bit bằng 1
int signBit = 0x8000_0000;     // -2147483648
long positive = 0xFFFF_FFFFL;  // 4294967295 (trong 64 bit)
// int invalid = 0x1_0000_0000; // lỗi: cần 33 bit
```

**Đừng coi `0xFFFF_FFFF` là một số `int` dương bằng 4294967295.** Nó là mẫu bit 32 bit có dấu nên có giá trị `-1`. Nếu cần dùng giá trị dương tương ứng, thêm `L` vào literal trước khi tính.

Một trường hợp khác: `-0x8000_0000` vẫn bằng `-2147483648` khi biểu thức mang kiểu `int`, do phép đổi dấu giá trị nhỏ nhất của `int` tràn và quay lại chính nó. Viết `0x8000_0000L` nếu muốn giá trị dương 2147483648L.

## 5. Tách chữ số bằng gạch dưới

Từ Java 7, có thể đặt `_` **giữa các chữ số** của literal số nguyên để đọc dễ hơn. Gạch dưới không thay đổi giá trị, cơ số hay kiểu.

```java
int count = 1_000_000;
int bits = 0b1010_1100;
int rgb = 0x00FF_80A0;
long distance = 9_000_000_000L;
```

Các ví dụ dưới đây là **minh họa lỗi biên dịch khi bỏ dấu chú thích**:

```java
// int a = _100;   // '_' không đứng trước chữ số đầu
// int b = 100_;   // '_' không đứng sau chữ số cuối
// int c = 0x_FF;  // '_' không đứng sát tiền tố 0x
// int d = 0b_101; // '_' không đứng sát tiền tố 0b
// long e = 123_L; // '_' không đứng trước hậu tố L
```

Cách nhóm hữu ích: nhóm ba chữ số thập phân (`1_000_000`), nhóm bốn bit (`0b1100_1010`), nhóm hai hoặc bốn chữ số hex (`0xCA_FE`). Ký tự `_` trong tên biến là quy tắc khác; ở đây chỉ nói về ký tự nằm trong token số.

## 6. Bẫy số 0 đứng đầu: bát phân, không phải số đệm

Trong Java, `010` là literal **cơ số 8**, nên bằng `8` thập phân. `077` bằng `63`. Các chữ số `8` và `9` không hợp lệ sau số 0 dẫn vào một literal bát phân.

```java
int a = 010; // 8
int b = 077; // 63
int c = 10;  // 10
// int invalid08 = 08; // lỗi biên dịch
// int invalid09 = 09; // lỗi biên dịch
```

Phân biệt số viết trong **mã nguồn** với chuỗi được **parse lúc runtime**:

| Biểu thức | Kết quả | Vì sao |
| --- | --- | --- |
| `010` | `8` | Literal bát phân |
| `Integer.parseInt("010")` | `10` | `parseInt` mặc định dùng cơ số 10 |
| `Integer.parseInt("010", 8)` | `8` | Truyền cơ số 8 tường minh |
| `Integer.parseInt("2A", 16)` | `42` | Parse hai chữ số hex |
| `Integer.decode("010")` | `8` | `decode` hiểu tiền tố 0 là bát phân |
| `Integer.decode("0x2A")` | `42` | `decode` hiểu tiền tố 0x là hex |

Lưu ý: `Integer.parseInt("0x2A", 16)` không chấp nhận tiền tố `0x`; `Integer.decode("0x2A")` phù hợp hơn nếu chuỗi đã chứa tiền tố cơ số. Không nhầm lẫn các hàm phân tích chuỗi này với ngữ pháp literal của trình biên dịch.

## 7. Phép toán, tràn số và chuyển kiểu

Với hai toán hạng `int`, phép cộng số nguyên thường được thực hiện **bằng kiểu `int`**; chuyển kết quả vào biến `long` chỉ diễn ra sau đó:

```java
long overflowed = 2_147_483_647 + 1; // int tràn trước -> -2147483648L
long correct = 2_147_483_647L + 1;  // long + int -> 2147483648L
```

Tương tự, `long product = 100_000 * 100_000;` thực hiện phép nhân `int` trước khi mở rộng, có thể tràn; nên dùng `100_000L * 100_000` nếu cần tích chính xác kiểu `long`.

“Literal mặc định là `int`” **không ngăn** gán một hằng `int` nằm trong miền giá trị cho `byte`, `short` hay `char`. Java cho phép chuyển hẹp trong ngữ cảnh gán một số **biểu thức hằng tại thời điểm biên dịch**:

```java
byte small = 127;      // hợp lệ: giá trị int 127 vừa byte
short sh = 30_000;     // hợp lệ: vừa short
char letter = 65;      // hợp lệ: ký tự 'A'
byte sum = 1 + 2;      // hợp lệ: constant expression bằng 3
long widened = 100;    // hợp lệ: mở rộng int -> long

// byte tooBig = 128;  // lỗi: không vừa byte
// short tooBigShort = 40_000; // lỗi: không vừa short
```

Biến `int` thông thường khác với biểu thức hằng:

```java
int local = 10;
// byte wrong = local; // lỗi: không tự chuyển hẹp từ biến int

final int fixed = 10; // constant variable, initializer là hằng biểu thức
byte ok = fixed;       // hợp lệ vì giá trị nằm trong phạm vi byte
byte narrowed = (byte) 0xFF; // -1: ép kiểu tường minh cắt bớt bit
```

Với `byte`, nếu cần diễn giải bit như số **không dấu** từ 0 đến 255, `Byte.toUnsignedInt(narrowed)` cho `255`. Đây là thao tác đọc giá trị bit, không tạo ra kiểu primitive `unsigned byte` trong Java.

## 8. Ví dụ Java 17+ chạy được hoàn chỉnh

Tạo tệp **`IntegerLiteralDemo.java`**:

```java
public class IntegerLiteralDemo {
    static String kind(int value) {
        return "int";
    }

    static String kind(long value) {
        return "long";
    }

    public static void main(String[] args) {
        int decimal = 42;
        int binary = 0b101010;
        int octal = 052;
        int hexadecimal = 0x2A;

        System.out.println("decimal=" + decimal);
        System.out.println("binary=" + binary);
        System.out.println("octal=" + octal);
        System.out.println("hexadecimal=" + hexadecimal);
        System.out.println("42 type=" + kind(42));
        System.out.println("42L type=" + kind(42L));
        System.out.println("octal010=" + 010);
        System.out.println("parse010=" + Integer.parseInt("010"));
        System.out.println("parse2A=" + Integer.parseInt("2A", 16));
        System.out.println("allBitsInt=" + 0xFFFF_FFFF);
        System.out.println("allBitsLong=" + 0xFFFF_FFFFL);
        System.out.println("minIntHex=" + 0x8000_0000);

        long overflowed = 2_147_483_647 + 1;
        long correct = 2_147_483_647L + 1;
        System.out.println("overflowed=" + overflowed);
        System.out.println("correct=" + correct);

        byte narrow = (byte) 0xFF;
        System.out.println("narrow=" + narrow);
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac IntegerLiteralDemo.java
java IntegerLiteralDemo
```

Đầu ra chính xác:

```text
decimal=42
binary=42
octal=42
hexadecimal=42
42 type=int
42L type=long
octal010=8
parse010=10
parse2A=42
allBitsInt=-1
allBitsLong=4294967295
minIntHex=-2147483648
overflowed=-2147483648
correct=2147483648
narrow=-1
```

Nhận xét từ output: các tiền tố cơ số khác nhau không đổi giá trị 42; hậu tố `L` đổi kiểu literal và ảnh hưởng overload; toàn bộ bit 1 của `int` được diễn giải thành -1; phép cộng `int` có thể tràn trước khi gán cho `long`.

## 9. Tình huống sai thường gặp và quy trình đọc literal

| Viết sai hoặc hiểu sai | Vấn đề | Cách sửa |
| --- | --- | --- |
| `long x = 3000000000;` | Literal thập phân không `L` vượt `int` | `long x = 3_000_000_000L;` |
| `int x = 09;` | 9 không thuộc hệ bát phân | `int x = 9;` |
| `int mask = 0x1_0000_0000;` | Cần hơn 32 bit để biểu diễn | Dùng `long mask = 0x1_0000_0000L;` |
| Mong `0xFFFF_FFFF` bằng 4294967295 | Đây là mẫu `int` 32 bit có dấu | Dùng `0xFFFF_FFFFL` nếu cần giá trị dương |
| `long n = 2_147_483_647 + 1;` cho âm | Tràn trong phép cộng `int` trước khi mở rộng | `long n = 2_147_483_647L + 1;` |
| `byte b = 128;` | Hằng `int` vượt miền kiểu `byte` | Chọn kiểu phù hợp hoặc kiểm tra lại dữ liệu |
| `0x_FF` báo lỗi | Gạch dưới đứng sát tiền tố | `0xFF` hoặc `0xF_F` |
| Nhầm `"010"` với `010` | Chuỗi dùng API parse, literal do compiler phân tích | Chỉ rõ cơ số khi parse chuỗi |

Khi gặp một literal, hãy kiểm tra theo thứ tự:

1. **Tiền tố:** `0b` → nhị phân, `0x` → hex, `0` dẫn trước các chữ số → bát phân, dạng còn lại → thập phân.
2. **Hậu tố:** có `L` → `long`; không → `int`, lưu ý giới hạn và ngoại lệ `-2147483648`.
3. **Giá trị bit:** literal phi thập phân có bit dấu bật sẽ biểu diễn số âm trong `int`/`long`.
4. **Ngữ cảnh biểu thức:** phép tính `int` có thể tràn trước khi giá trị được gán vào `long`.
5. **Kiểu đích:** hằng có thể chuyển hẹp có điều kiện sang `byte`/`short`/`char`, còn biến `int` không đương nhiên làm được.

**Cốt lõi:** `42`, `0b101010`, `052` và `0x2A` đều là literal `int` mang giá trị 42. Cần `L` để tạo literal `long`; tiền tố cơ số quy định cách đọc chữ số, còn bit dấu, quy tắc kiểu và biểu thức quyết định kết quả cuối cùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-literals-002">Hậu tố L cho long và thời điểm literal vượt phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

# Hậu tố `L` cho `long` và thời điểm integer literal vượt phạm vi trong Java

Khi viết `long value = 3_000_000_000;`, nhiều người tưởng rằng trình biên dịch sẽ nhìn kiểu biến `long` rồi tự động chấp nhận số này. Thực tế, Java **xác định kiểu của literal trước khi xét phép gán**. Với literal số nguyên hệ thập phân, nếu không có hậu tố `L`, giá trị thường phải nằm trong phạm vi `int`. Vì `3_000_000_000` lớn hơn `Integer.MAX_VALUE`, đoạn mã trên không biên dịch được. Cách đúng là:

```java
long value = 3_000_000_000L; // long literal, giá trị 3 tỷ.
```

Bài này phân biệt ba hiện tượng dễ nhầm: **literal không hợp lệ ngay ở thời điểm biên dịch**, **kết quả biểu thức `int` tràn rồi mới được chuyển sang `long`**, và **phép tính `long` vẫn có thể tràn ở thời điểm chạy**.

## 1. Literal số nguyên là gì? Hậu tố `L` thay đổi điều gì?

Literal số nguyên là giá trị nguyên viết trực tiếp trong mã nguồn, chẳng hạn `42`, `3_000_000_000L`, `0xFF` hoặc `0b1010`. Java có bốn cách viết số nguyên: thập phân (decimal), thập lục phân (hexadecimal, `0x`/`0X`), nhị phân (binary, `0b`/`0B`) và bát phân (octal, thường bắt đầu bằng `0`, chẳng hạn `017`).

**Hậu tố `L` hoặc `l` làm literal có kiểu `long`**. Nên dùng chữ hoa `L`, vì chữ thường `l` dễ nhầm với chữ số `1`. Không có hậu tố, literal số nguyên sẽ có kiểu `int` nếu hợp lệ theo quy tắc phạm vi và ngoại lệ ở ranh giới âm được giải thích bên dưới.

| Viết trong Java | Kiểu của literal | Ý nghĩa |
| --- | --- | --- |
| `42` | `int` | Số nguyên 42 |
| `42L` | `long` | Số nguyên 42 thuộc kiểu 64 bit |
| `2_147_483_647` | `int` | Giá trị dương lớn nhất của `int` |
| `2_147_483_648L` | `long` | Vượt `int`, vẫn nằm trong phạm vi `long` |
| `0xFFL` | `long` | Giá trị 255 viết ở hệ 16 |
| `0b1010L` | `long` | Giá trị 10 viết ở hệ 2 |
| `017L` | `long` | Giá trị 15 viết ở hệ 8 |
| `1.0` | `double` | Literal số thực, không phải literal số nguyên |

Dấu gạch dưới `_` trong literal giúp đọc nhóm chữ số nhưng **không ảnh hưởng giá trị hoặc kiểu**: `3_000_000_000L` tương đương `3000000000L`. Không đặt `_` trước/sau dãy chữ số, ngay sau tiền tố `0x`/`0b`, hoặc giữa chữ số và `L`: `3_000L` hợp lệ, còn `3_000_L` thì không.

## 2. Phạm vi `int`, `long` và cách Java nhận literal thập phân

| Kiểu | Độ rộng | Nhỏ nhất | Lớn nhất |
| --- | ---: | ---: | ---: |
| `int` | 32 bit có dấu | `-2_147_483_648` | `2_147_483_647` |
| `long` | 64 bit có dấu | `-9_223_372_036_854_775_808L` | `9_223_372_036_854_775_807L` |

Với số nguyên **thập phân**:

- Literal không hậu tố `L` có giới hạn dương bình thường là `2_147_483_647` (`Integer.MAX_VALUE`). Ví dụ `2_147_483_648` đứng một mình sẽ không hợp lệ.
- Literal có hậu tố `L` có giới hạn dương bình thường là `9_223_372_036_854_775_807L` (`Long.MAX_VALUE`). `9_223_372_036_854_775_808L` đứng một mình không hợp lệ.
- Có một **ngoại lệ đặc biệt cho hai giá trị biên âm**: Java cho phép chữ số thập phân `2147483648` không có `L` **chỉ khi là toán hạng của dấu trừ đơn ngôi** trong `-2147483648`, để biểu diễn `Integer.MIN_VALUE`. Tương tự, `-9223372036854775808L` biểu diễn `Long.MIN_VALUE`. Đây không phải là cơ chế chuyển kiểu tự động khi literal dương vượt giới hạn.

Dấu `-` là **toán tử trừ đơn ngôi** tác động lên literal, không phải một phần của cú pháp literal. Bởi vậy các trường hợp ở biên cần xét cả biểu thức và ngữ cảnh, không chỉ đếm chữ số.

```java
int maxInt = 2_147_483_647;
int minInt = -2_147_483_648;             // Ngoại lệ biên âm: hợp lệ.
long aboveInt = 2_147_483_648L;          // Phải dùng L.
long maxLong = 9_223_372_036_854_775_807L;
long minLong = -9_223_372_036_854_775_808L; // Biên âm long: hợp lệ.
```

Đoạn trên là các khai báo hợp lệ có thể đặt trong một phương thức Java. **Không thể tăng giới hạn của `long` chỉ bằng việc thêm `L`**: một giá trị lớn hơn `Long.MAX_VALUE` vẫn không trở thành `long` hợp lệ.

## 3. Vì sao biến `long` không cứu được literal `int` quá lớn?

Xét đoạn không hợp lệ dưới đây (chỉ để minh họa lỗi, không đưa vào chương trình chạy):

```java
long total = 3_000_000_000; // LỖI BIÊN DỊCH: integer literal quá lớn cho int.
```

Trình biên dịch xử lý **kiểu và tính hợp lệ của literal trong biểu thức trước**, sau đó mới xét liệu kết quả có thể gán cho biến hay không. Kiểu đích `long` cho phép *widening primitive conversion* từ một giá trị `int` **đã hợp lệ**, nhưng không thay đổi cách phân tích một literal `int` quá phạm vi.

```java
long small = 123;             // Hợp lệ: int 123 mở rộng thành long.
long large = 3_000_000_000L;  // Hợp lệ: bản thân literal đã là long.
```

Không có quy tắc “literal thập phân không `L` sẽ tự thành `long` nếu lớn hơn `int`”. Khi số viết trực tiếp vượt `int`, hãy khai báo literal là `long` ngay tại chỗ.

Các ví dụ dưới đây đều **không biên dịch**, nhưng vì lý do khác nhau:

```java
long a = 2_147_483_648;           // Decimal int literal vượt phạm vi.
long b = 9_223_372_036_854_775_808L; // Decimal long literal vượt phạm vi.
int c = 3_000_000_000L;          // Literal long hợp lệ nhưng không gán thẳng vào int.
```

Ở dòng `c`, lỗi là phép gán `long` sang `int` có thể mất dữ liệu; dấu `L` giải quyết **kiểu của literal**, không cấp quyền tự động chuyển hẹp kiểu.

## 4. Lưu ý riêng cho literal nhị phân, bát phân và thập lục phân

Literal **không phải thập phân** còn có quy tắc sử dụng mẫu bit. Ví dụ `0xFFFF_FFFF` hợp lệ khi không có `L`: toàn bộ 32 bit đều bằng 1 và được diễn giải thành số `int` có dấu **-1**. Trong khi đó `0xFFFF_FFFFL` dùng `long` 64 bit, có 32 bit thấp bằng 1, nên giá trị là **4_294_967_295**.

```java
int minViaBits = 0x8000_0000;    // -2147483648 (bit dấu của int bật).
int minusOne = 0xFFFF_FFFF;      // -1 (toàn bộ 32 bit đều bật).
long positive = 0xFFFF_FFFFL;    // 4294967295 (là long 64 bit).
long minLongBits = 0x8000_0000_0000_0000L; // Long.MIN_VALUE.
long allLongBits = 0xFFFF_FFFF_FFFF_FFFFL;  // -1L.
```

Đây là mã hợp lệ có thể đặt trong một phương thức Java. Với literal không thập phân, **cùng dãy chữ số** có thể mang ý nghĩa số có dấu khác nhau tùy kiểu `int` 32 bit hay `long` 64 bit. Các mẫu quá 32 bit không có `L` vẫn là lỗi, ví dụ `0x1_0000_0000`; thêm `L` vào mẫu phù hợp 64 bit mới được.

Đừng suy từ việc `0xFFFF_FFFF` được chấp nhận rằng `4_294_967_295` cũng là `int` hợp lệ. Hai cách ghi dùng các quy tắc kiểm tra khác nhau: số thập phân diễn tả độ lớn, còn các literal nhị phân/bát phân/thập lục phân có thể diễn tả mẫu bit có dấu.

## 5. Hai loại tràn cần phân biệt: literal quá phạm vi và phép tính tràn

**A. Vượt phạm vi ngay khi viết literal: lỗi biên dịch.** Ví dụ `long n = 3_000_000_000;` thất bại trước khi chạy. Hậu tố `L` khiến số đó có kiểu `long`, nên có thể dùng.

**B. Các literal riêng lẻ hợp lệ nhưng phép toán tràn: có thể biên dịch và chạy ra giá trị sai.** Ví dụ:

```java
long wrong = 2_000_000_000 + 1_000_000_000;   // -1294967296
long right = 2_000_000_000L + 1_000_000_000; // 3000000000
```

Ở dòng đầu, cả hai toán hạng là `int`; phép cộng được thực hiện trong `int`, kết quả vượt `Integer.MAX_VALUE`, nên tràn về `-1_294_967_296`. Java chỉ chuyển **kết quả đã tràn** sang `long` khi gán. Ở dòng thứ hai, một toán hạng có kiểu `long`, toán hạng còn lại được nâng lên `long` và phép cộng được tính trong phạm vi 64 bit.

**Thêm `L` hoặc ép kiểu phải xảy ra trước phép toán có nguy cơ tràn.** Ép kiểu sau phép tính không thể khôi phục các bit đã mất:

```java
long wrongProduct = 100_000 * 100_000;          // int overflow -> 1410065408L
long alsoWrong = (long) (100_000 * 100_000);    // Ép kiểu sau khi đã tràn.
long rightProduct = 100_000L * 100_000;         // 10000000000L
long alsoRight = ((long) 100_000) * 100_000;     // Ép kiểu trước khi nhân.
```

Trong các phép toán số học nhị phân thường gặp, nếu ít nhất một toán hạng thuộc `long` (và không có `float`/`double`), kết quả phép toán được tính theo `long`. Nhưng **`long` cũng có giới hạn**: `Long.MAX_VALUE + 1L` tràn về `Long.MIN_VALUE`. Phép toán số nguyên `+`, `-`, `*` bình thường không tự ném ngoại lệ khi tràn.

Lưu ý biểu thức hằng `int` cũng có thể tràn khi được tính ở thời điểm biên dịch. Ví dụ `int wrapped = Integer.MAX_VALUE + 1;` có giá trị `Integer.MIN_VALUE`. “Được compiler tính trước” không đồng nghĩa “compiler sẽ bắt lỗi tràn số học”. Riêng phép chia cho 0 là một tình huống khác với tràn cộng/nhân.

## 6. Chương trình chạy độc lập (Java 17 trở lên)

Lưu đúng tên tệp **`LongLiteralDemo.java`**:

```java
public class LongLiteralDemo {
    public static void main(String[] args) {
        long largeLiteral = 3_000_000_000L;
        int intMin = -2_147_483_648;
        long longMin = -9_223_372_036_854_775_808L;

        long tooLate = 2_000_000_000 + 1_000_000_000;
        long promotedEarly = 2_000_000_000L + 1_000_000_000;
        long productTooLate = 100_000 * 100_000;
        long productCorrect = 100_000L * 100_000;

        int allIntBits = 0xFFFF_FFFF;
        long sameHexDigitsWithL = 0xFFFF_FFFFL;

        System.out.println("largeLiteral=" + largeLiteral);
        System.out.println("intMin=" + intMin);
        System.out.println("longMin=" + longMin);
        System.out.println("tooLate=" + tooLate);
        System.out.println("promotedEarly=" + promotedEarly);
        System.out.println("productTooLate=" + productTooLate);
        System.out.println("productCorrect=" + productCorrect);
        System.out.println("allIntBits=" + allIntBits);
        System.out.println("sameHexDigitsWithL=" + sameHexDigitsWithL);
        System.out.println("parsed=" + Long.parseLong("3000000000"));
        System.out.println("longOverflow=" + (Long.MAX_VALUE + 1L));

        try {
            Math.multiplyExact(100_000, 100_000);
        } catch (ArithmeticException ex) {
            System.out.println("multiplyExact=overflow detected");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 LongLiteralDemo.java
java LongLiteralDemo
```

Kết quả stdout chính xác:

```text
largeLiteral=3000000000
intMin=-2147483648
longMin=-9223372036854775808
tooLate=-1294967296
promotedEarly=3000000000
productTooLate=1410065408
productCorrect=10000000000
allIntBits=-1
sameHexDigitsWithL=4294967295
parsed=3000000000
longOverflow=-9223372036854775808
multiplyExact=overflow detected
```

Chương trình minh họa ba điều: một literal thập phân 3 tỷ cần `L`; phép cộng/nhân thực hiện bằng `int` vẫn có thể tràn trước khi kết quả được gán sang `long`; và với cùng 8 chữ số thập lục phân `FFFFFFFF`, thêm `L` đổi cách diễn giải bit theo độ rộng 64 thay vì 32. `Math.multiplyExact` thể hiện lựa chọn khác: yêu cầu Java **ném `ArithmeticException`** khi phép nhân nguyên bị tràn.

## 7. Muốn phát hiện tràn thay vì nhận giá trị quấn vòng?

Dùng nhóm phương thức có kiểm tra của lớp `Math`:

```java
int safeSum = Math.addExact(1_000_000_000, 1_000_000_000);
long safeProduct = Math.multiplyExact(100_000L, 100_000L);
int checkedNarrowing = Math.toIntExact(2_000_000_000L);
```

Các khai báo trên hợp lệ trong một phương thức Java; kết quả lần lượt là `2_000_000_000`, `10_000_000_000`, `2_000_000_000`. `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact` và `Math.toIntExact` ném `ArithmeticException` khi tràn hoặc khi chuyển hẹp vượt phạm vi.

Một điểm cần nhớ: `Math.addExact` có overload `int` và `long`. Nếu muốn cộng hai giá trị mà kết quả có thể lớn hơn `int`, dùng ít nhất một toán hạng kiểu `long` để chọn phép toán `long`:

```java
long total = Math.addExact(2_000_000_000L, 1_000_000_000L);
```

Với số lớn hơn phạm vi `long`, cần chuyển sang `java.math.BigInteger` thay vì cố dùng literal `long` quá giới hạn:

```java
java.math.BigInteger veryLarge =
        new java.math.BigInteger("9223372036854775808");
```

Đây là số nguyên tùy độ chính xác, được khởi tạo từ chuỗi; nó **không** phải một `long` literal.

## 8. Khi số đến từ chuỗi, `L` có còn tác dụng?

Hậu tố `L` là **cú pháp mã nguồn Java**, không phải thành phần của dữ liệu số dạng văn bản. Nếu đầu vào là chuỗi `"3000000000"`, hãy dùng API phân tích phù hợp:

```java
long number = Long.parseLong("3000000000"); // 3000000000L.
```

`Integer.parseInt("3000000000")` ném `NumberFormatException` vì vượt `int`. `Long.parseLong("3000000000L")` cũng ném `NumberFormatException`, vì ký tự `L` không phải chữ số thập phân hợp lệ trong chuỗi. Tương tự, dấu phân cách `_` được phép trong literal mã nguồn như `3_000L`, nhưng `Long.parseLong("3_000")` không chấp nhận.

Để phân tích văn bản hệ 16 hoặc hệ 2, cung cấp radix tương ứng, chẳng hạn `Long.parseLong("FF", 16)` trả 255. API xử lý chuỗi còn phải đối phó với đầu vào sai định dạng hoặc vượt phạm vi; không nên coi việc thêm `L` vào cuối chuỗi là một bước chuyển kiểu.

## 9. Những lỗi phổ biến và cách sửa

| Trường hợp | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `long n = 3000000000;` | Literal thập phân không `L` vượt `int` | `long n = 3000000000L;` |
| `long n = 2147483648;` | Kiểu biến không quyết định kiểu literal | `long n = 2147483648L;` |
| `long n = 9223372036854775808L;` | Vượt `Long.MAX_VALUE` | Dùng `BigInteger` nếu cần số dương đó |
| `int n = 3000000000L;` | Literal hợp lệ kiểu `long`, nhưng đích là `int` | Chọn `long` làm kiểu biến; chỉ chuyển hẹp khi đã kiểm tra |
| `long x = 100000 * 100000;` | Phép nhân `int` tràn trước khi gán | `100000L * 100000` |
| `long x = (long) (100000 * 100000);` | Ép kiểu kết quả đã tràn | Ép một toán hạng trước khi nhân |
| `long x = Long.MAX_VALUE + 1L;` | Phép cộng `long` vẫn có thể tràn | `Math.addExact` để phát hiện; `BigInteger` nếu cần giá trị lớn hơn |
| `Long.parseLong("123L")` | Hậu tố `L` chỉ thuộc cú pháp mã nguồn | `Long.parseLong("123")` |
| `0xFFFF_FFFF` bị hiểu là số dương | Mẫu 32 bit có bit dấu 1 nên là `int` âm | Dùng `0xFFFF_FFFFL` nếu mong muốn 4_294_967_295 |
| `3_000_L` | Gạch dưới đứng giữa chữ số và hậu tố | `3_000L` |

## 10. Tự kiểm tra

1. **`long a = 5;` có hợp lệ không?** Có. `5` là literal `int` hợp lệ, được mở rộng sang `long`.
2. **`long a = 2_147_483_648;` có hợp lệ không?** Không. Bản thân literal thập phân không `L` vượt giới hạn `int`.
3. **`long a = -2_147_483_648;` có hợp lệ không?** Có. Java cho phép trường hợp đặc biệt biểu diễn `Integer.MIN_VALUE`, rồi mở rộng kết quả sang `long`.
4. **`long a = 2_147_483_647 + 1;` cho giá trị gì?** `-2_147_483_648`, vì phép cộng `int` tràn trước khi gán. Nếu muốn `2_147_483_648`, viết `2_147_483_647L + 1`.
5. **`int x = 0xFFFF_FFFF;` có hợp lệ không?** Có, `x == -1`. **`long y = 0xFFFF_FFFFL;`** có giá trị `4_294_967_295`.
6. **Khi nào `L` không đủ?** Khi giá trị vượt cả `long`, khi kết quả phép toán `long` có thể tràn, hoặc khi muốn phát hiện tràn bằng ngoại lệ. Khi đó dùng `BigInteger` hoặc phép toán kiểm tra của `Math` theo nhu cầu.

**Ghi nhớ:** `L` quyết định **kiểu của literal số nguyên ngay khi mã nguồn được phân tích**. Kiểu biến nhận kết quả không sửa được một literal vốn đã quá phạm vi, và phép gán sang `long` không khôi phục được phép tính `int` đã tràn. Hãy chọn kiểu đủ rộng **trước phép toán**, và sử dụng kiểm tra tràn khi tính chính xác là yêu cầu bắt buộc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-literals-003">Thập phân, nhị phân, bát phân và thập lục phân</a>

<details>
<summary>Xem chi tiết</summary>

# Thập phân, nhị phân, bát phân và thập lục phân trong Java

## 1. Một giá trị, bốn cách biểu diễn

Một số nguyên là **giá trị**, còn hệ cơ số (*numeral system*) là **cách viết giá trị** đó. Ví dụ, số nguyên bốn mươi hai có thể viết:

| Hệ cơ số | Cơ số | Chữ số được phép | Cách viết số 42 trong toán học | Literal nguyên trong Java |
|---|---:|---|---|---|
| Thập phân (*decimal*) | 10 | 0–9 | \(42_{10}\) | `42` |
| Nhị phân (*binary*) | 2 | 0, 1 | \(101010_2\) | `0b101010` |
| Bát phân (*octal*) | 8 | 0–7 | \(52_8\) | `052` |
| Thập lục phân (*hexadecimal*) | 16 | 0–9, A–F | \(2A_{16}\) | `0x2A` |

Các literal `42`, `0b101010`, `052` và `0x2A` đều có cùng **giá trị nguyên 42**. Chọn cơ số khác không làm biến `int` to hơn, nhanh hơn hoặc chính xác hơn: nó chỉ thay đổi cách mã nguồn biểu diễn một số.

Khi đọc một literal, phân biệt **tiền tố** (cho biết hệ cơ số), **phần chữ số**, **dấu gạch dưới** (cho dễ đọc) và **hậu tố `L`** (cho biết kiểu `long`). Những thành phần này không có cùng vai trò.

## 2. Nguyên lý của hệ giá trị theo vị trí

Với cơ số \(b\), mỗi vị trí từ phải sang trái mang trọng số \(b^0, b^1, b^2,\ldots\). Một chuỗi chữ số \(d_nd_{n-1}\dots d_0\) biểu thị:

\[
d_n b^n + d_{n-1}b^{n-1}+\cdots+d_1 b+d_0.
\]

Mọi chữ số \(d_i\) phải thỏa \(0 \le d_i < b\). Riêng hệ 16 dùng thêm A = 10, B = 11, C = 12, D = 13, E = 14, F = 15; chữ hoa và chữ thường đều được chấp nhận.

Áp dụng với số 42:

- Thập phân `42` = \(4\times10+2=42\).
- Nhị phân `101010` = \(1\times32+0\times16+1\times8+0\times4+1\times2+0=42\).
- Bát phân `52` = \(5\times8+2=42\).
- Hex `2A` = \(2\times16+10=42\).

Từ đó thấy rõ: không thể suy ra giá trị chỉ bằng nhìn chuỗi `10` mà bỏ qua cơ số. `10` trong hệ 2 bằng 2; trong hệ 8 bằng 8; trong hệ 10 bằng 10; trong hệ 16 bằng 16.

## 3. Cú pháp literal nguyên của Java

### 3.1. Thập phân — mặc định

Literal không có tiền tố đặc biệt và bắt đầu bằng chữ số 1–9 được hiểu là thập phân; chữ số `0` đứng một mình cũng hợp lệ:

```java
int ten = 10;
int thousand = 1_000;
long large = 3_000_000_000L;
int zero = 0;
```

Thêm hậu tố `L` hoặc `l` để chỉ literal kiểu `long`; nên ưu tiên **`L` viết hoa**, dễ phân biệt với chữ số 1.

### 3.2. Nhị phân — tiền tố `0b` hoặc `0B`

Hỗ trợ từ Java 7, chỉ dùng 0 và 1:

```java
int flags = 0b1010;       // 10 thập phân
int eight = 0B1000;       // 8 thập phân
long wide = 0b1_0000L;   // 16 thập phân
```

`0b102` **không hợp lệ** vì chữ số 2 không có trong cơ số 2. Không có tiền tố nhị phân nào ngầm nói rằng literal là `byte`: `0b1111_1111` là literal `int` mang giá trị 255; `(byte) 0b1111_1111` mới tạo giá trị `byte` -1.

### 3.3. Bát phân — tiền tố **chữ số 0**

Java viết bát phân bằng **chữ số 0 đặt đầu literal**, theo cú pháp lịch sử:

```java
int tenInOctal = 012;  // 1*8 + 2 = 10 thập phân
int permission = 0755; // 7*64 + 5*8 + 5 = 493 thập phân
int eight = 010;       // 8 thập phân, KHÔNG phải 10
```

Đây là bẫy hay gặp: `010` **không bằng** `10`. Viết `08` hoặc `09` sẽ **lỗi biên dịch**, vì 8 và 9 không phải chữ số bát phân.

Java **không** dùng tiền tố `0o` kiểu Python để biểu diễn bát phân. Nếu muốn số 10 thập phân, viết `10`; nếu cần diễn đạt bát phân rõ ràng trong văn bản, ghi \(12_8\), còn trong Java dùng `012`.

### 3.4. Thập lục phân — tiền tố `0x` hoặc `0X`

Hex dùng chữ số 0–9 và A–F (không phân biệt hoa thường):

```java
int answer = 0x2A;          // 42 thập phân
int byteMask = 0xFF;        // 255 thập phân
int color = 0x12_AB_34;     // 1_223_476 thập phân
long wide = 0X1_0000_0000L; // 4_294_967_296 thập phân
```

Hex thuận tiện khi thao tác byte hoặc bit: 1 chữ số hex tương ứng **4 bit**, 2 chữ số hex tương ứng **8 bit**. Ví dụ `0xFF` = `0b1111_1111` = 255.

`0xG1` không hợp lệ vì G không phải chữ số hex. Khi viết literal hex nguyên, có thể dùng các chữ cái A–F ở cả hai dạng; Java nhận `0x2a` và `0X2A` như nhau.

## 4. Dấu gạch dưới, hậu tố và kiểu của literal

Java cho phép dấu gạch dưới **giữa các chữ số** nhằm tăng khả năng đọc:

```java
int population = 2_147_483_647;
int bits = 0b1010_0101;
int code = 0xCAFE_BABE;
long id = 0xFFFF_FFFF_FFFF_FFFFL;
```

Dấu gạch dưới không thay đổi giá trị. Các dạng sau **không hợp lệ**:

```java
// int bad1 = _123;      // '_' không được đứng đầu literal
// int bad2 = 123_;      // '_' không được đứng cuối literal
// int bad3 = 0x_FF;     // '_' không được đứng ngay sau tiền tố
// int bad4 = 0b_1010;   // '_' không được đứng ngay sau tiền tố
// long bad5 = 123_L;    // '_' không được đứng trước hậu tố L
```

Các dòng này đều là comment để chương trình mẫu không gặp lỗi biên dịch. Thêm hậu tố `L` cho literal nguyên để chọn kiểu `long`; tiền tố `0b`, `0`, `0x` chỉ chọn **cơ số**, không chọn `byte`/`short` hay `long`.

Với literal nguyên chưa có `L`, kiểu là `int`; có `L`, kiểu là `long`, trong phạm vi cách viết hợp lệ. Java cho phép vài hằng số nguyên thập phân đặc biệt dưới dạng toán tử trừ đơn ngôi với trị nhỏ nhất như `-2147483648`; thường dùng `Integer.MIN_VALUE` và `Long.MIN_VALUE` để khỏi nhầm giới hạn.

**Lưu ý khi gán biến nhỏ:** Java có thể cho phép `byte b = 0x7F;` vì đây là biểu thức hằng `int` mà giá trị 127 vừa `byte`. Điều đó không có nghĩa `0x7F` thuộc kiểu `byte`. `byte b = 0xFF;` lỗi biên dịch vì 255 ngoài miền `byte`, trừ khi ép kiểu chủ động.

## 5. Chương trình chứng minh bốn cách viết là cùng một số

Lưu tệp `RadixLiteralDemo.java`:

```java
public class RadixLiteralDemo {
    public static void main(String[] args) {
        int decimal = 42;
        int binary = 0b101010;
        int octal = 052;
        int hex = 0x2A;

        System.out.println(decimal == binary
                && binary == octal
                && octal == hex);
        System.out.println("decimal: " + decimal);
        System.out.println("binary: " + Integer.toBinaryString(decimal));
        System.out.println("octal: " + Integer.toOctalString(decimal));
        System.out.println("hex: " + Integer.toHexString(decimal));
        System.out.println("010 in Java = " + 010);
        System.out.println("0xFF = " + 0xFF);
        System.out.println("0b11111111 = " + 0b11111111);
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac RadixLiteralDemo.java
java RadixLiteralDemo
```

Kết quả chính xác:

```text
true
decimal: 42
binary: 101010
octal: 52
hex: 2a
010 in Java = 8
0xFF = 255
0b11111111 = 255
```

`Integer.toBinaryString`, `toOctalString` và `toHexString` **trả về chuỗi chữ số**, không tự thêm tiền tố nguồn `0b`/`0`/`0x`. Chuỗi hex mặc định dùng a–f chữ thường.

## 6. Liên hệ hệ cơ số với bit và số có dấu

### 6.1. Các nhóm bit đổi sang hex hoặc bát phân

Vì \(16=2^4\), chia biểu diễn nhị phân thành **nhóm 4 bit từ phải sang trái** để đổi sang hex:

```text
binary:       0010 1010
hex digits:      2    A
hex:             0x2A
```

Vì \(8=2^3\), chia nhị phân thành **nhóm 3 bit từ phải sang trái** để đổi sang bát phân:

```text
binary:     101 010
oct digits:   5   2
octal:        052
```

Nếu nhóm bên trái chưa đủ 4 hoặc 3 bit, chỉ cần thêm số 0 ở **bên trái phần biểu diễn số dương** để dễ đọc, không làm thay đổi giá trị.

### 6.2. Cơ số không làm mất quy tắc số nguyên có dấu

`int` luôn có 32 bit, `long` luôn có 64 bit. Cách biểu diễn số nguyên có dấu trong Java là **bù hai** (*two's complement*).

Vì vậy, một literal không thập phân đủ 32 bit với bit cao nhất là 1 có thể mang giá trị `int` **âm**:

```java
int allOnes = 0xFFFF_FFFF;        // -1 dưới dạng int 32 bit
int lowest = 0x8000_0000;        // Integer.MIN_VALUE
long positive = 0xFFFF_FFFFL;    // 4294967295L (64 bit)
long longAllOnes = 0xFFFF_FFFF_FFFF_FFFFL; // -1 dưới dạng long
```

Sự khác nhau giữa `0xFFFF_FFFF` và `0xFFFF_FFFFL` là **hậu tố `L`**: cùng những chữ số hex nhưng được biểu diễn thành giá trị kiểu `int` hoặc `long`. Với `int`, 32 bit tất cả bằng 1 là -1; với `long`, cùng mẫu 32 bit ấy được mở rộng bằng các bit 0 phía trước, nên là số dương 4_294_967_295.

Đặc biệt, `0xFFFF_FFFF` **không mang giá trị dương 4_294_967_295** nếu lưu dưới dạng `int` có dấu. Nếu cần diễn giải 32 bit không dấu, có thể dùng `Integer.toUnsignedLong(int)` hoặc `Integer.toUnsignedString(int)`.

### 6.3. Một chuỗi bit có nhiều cách diễn giải

Ví dụ 8 bit `1111_1111` có thể là:

- **255** nếu diễn giải theo 8 bit không dấu.
- **-1** nếu diễn giải theo kiểu `byte` có dấu và dùng bù hai.

Trong Java, literal `0b1111_1111` trước tiên là `int` 255. Phép ép `(byte) 0b1111_1111` giữ 8 bit thấp rồi hiểu chúng như `byte` có dấu, cho ra -1.

Đừng nhầm chuyện này với việc **đổi cơ số**. Đổi từ `0xFF` sang `0b1111_1111` chỉ đổi cách viết cùng số 255; ép sang `byte` có thể đổi giá trị do thay đổi **miền kiểu dữ liệu**.

## 7. Đổi số thành chuỗi và đọc chuỗi theo cơ số

Literal dùng khi viết **mã nguồn**. Dữ liệu đọc từ người dùng, file hoặc mạng là **chuỗi lúc chạy**, cần API chuyển đổi. Java cung cấp:

| Nhu cầu | API phổ biến | Ví dụ kết quả |
|---|---|---|
| Số nguyên → nhị phân | `Integer.toBinaryString(42)` | `"101010"` |
| Số nguyên → bát phân | `Integer.toOctalString(42)` | `"52"` |
| Số nguyên → hex | `Integer.toHexString(42)` | `"2a"` |
| Số nguyên → chuỗi theo cơ số tùy chọn | `Integer.toString(42, 16)` | `"2a"` |
| Chuỗi nhị phân → `int` | `Integer.parseInt("101010", 2)` | 42 |
| Chuỗi bát phân → `int` | `Integer.parseInt("52", 8)` | 42 |
| Chuỗi hex → `int` | `Integer.parseInt("2A", 16)` | 42 |
| Chuỗi thập phân → `int` | `Integer.parseInt("42", 10)` | 42 |

`Integer.parseInt(String, int)` và `Integer.toString(int, int)` hỗ trợ hệ cơ số **2 đến 36**. Với cơ số lớn hơn 10, Java dùng các chữ cái a–z cho các chữ số bổ sung, khi cần.

**Bẫy tiền tố:** `Integer.parseInt("0x2A", 16)` sẽ ném `NumberFormatException` vì `parseInt` yêu cầu **các chữ số thuộc cơ số** (có thể kèm dấu) và không bỏ tiền tố `0x` như cú pháp nguồn Java. `Integer.decode("0x2A")` có thể nhận tiền tố `0x`; `Integer.decode("052")` hiểu theo bát phân, còn `Integer.decode("42")` hiểu thập phân. `Integer.decode` không nhận tiền tố nhị phân `0b`.

Không dùng `Integer.parseInt("052")` mặc định để kỳ vọng ra 42: overload không có đối số cơ số sẽ đọc **thập phân**, nên kết quả là 52.

### Ví dụ chạy được: parse và định dạng

Lưu `RadixConversionDemo.java`:

```java
public class RadixConversionDemo {
    public static void main(String[] args) {
        int fromBinary = Integer.parseInt("101010", 2);
        int fromOctal = Integer.parseInt("52", 8);
        int fromHex = Integer.parseInt("2A", 16);

        System.out.println(fromBinary + ", " + fromOctal + ", " + fromHex);
        System.out.println(Integer.toString(42, 16));
        System.out.println(Integer.decode("0x2A"));
        System.out.println(Integer.parseInt("052"));
        System.out.println(Integer.toUnsignedLong(0xFFFF_FFFF));

        try {
            Integer.parseInt("0x2A", 16);
        } catch (NumberFormatException e) {
            System.out.println("parseInt: invalid prefix");
        }
    }
}
```

Chạy:

```bash
javac RadixConversionDemo.java
java RadixConversionDemo
```

Kết quả:

```text
42, 42, 42
2a
42
52
4294967295
parseInt: invalid prefix
```

Nếu số vượt miền `int`, dùng `Long.parseLong` khi vừa `long`; với số lớn hơn nữa, dùng `new java.math.BigInteger("FF...", 16)` và chủ động truyền đúng cơ số. `BigInteger(String, radix)` cũng nhận chuỗi chữ số theo cơ số, không tự dùng các tiền tố literal Java.

## 8. Cách in giá trị số theo cơ số

`System.out.println(number)` in số nguyên theo **thập phân**. Khi muốn in nhị phân/bát phân/hex, dùng các phương thức chuyển đổi ở trên hoặc `printf`.

```java
int n = 42;
System.out.printf("dec=%d, oct=%o, hex=%x, HEX=%X%n", n, n, n, n);
```

Kết quả:

```text
dec=42, oct=52, hex=2a, HEX=2A
```

Trong `printf`, `%d` là thập phân, `%o` là bát phân, `%x`/`%X` là hex. **`%b` KHÔNG có nghĩa là nhị phân** trong `Formatter` của Java; nó là định dạng boolean. Để in binary, dùng `Integer.toBinaryString(n)` hoặc `Long.toBinaryString(n)`.

Khi in số âm với `toBinaryString`/`toHexString`, kết quả phản ánh **mẫu bit bù hai không dấu** của kiểu tương ứng; ví dụ `Integer.toHexString(-1)` trả `"ffffffff"`, không phải `"-1"`. Nếu cần biểu diễn có dấu theo một cơ số, dùng `Integer.toString(value, radix)`: `Integer.toString(-1, 16)` trả `"-1"`.

Cũng cần phân biệt **hiển thị text** và **mã hóa nhị phân**. Chuỗi `"FF"` chứa hai ký tự F, không tự trở thành một byte `0xFF`. Phải parse theo cơ số rồi chọn cách ghi byte phù hợp.

## 9. Những lỗi điển hình và cách xử lý

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `010` cho 8 thay vì 10 | Số 0 đầu tiên biến literal thành bát phân | Viết `10` nếu muốn mười thập phân |
| `08` không biên dịch | Số 8 không thuộc cơ số 8 | Bỏ số 0 đầu hoặc sửa chữ số |
| `0o12` không biên dịch | Java không có tiền tố `0o` | Dùng `012` |
| `0b102` không biên dịch | 2 không hợp lệ trong hệ 2 | Chỉ viết 0, 1 |
| `0xGG` không biên dịch | G không thuộc các chữ số hex | Chỉ viết 0–9, A–F |
| `0x_FF` không biên dịch | Gạch dưới sát tiền tố | Viết `0xFF` hoặc `0xF_F` |
| `0xFFFF_FFFF` thành -1 | Mẫu bit int 32 bit có dấu | Sử dụng `0xFFFF_FFFFL` hoặc API unsigned khi phù hợp |
| `Integer.parseInt("FF")` báo lỗi | Mặc định parse thập phân | Truyền cơ số 16 |
| `Integer.parseInt("0xFF",16)` báo lỗi | `parseInt` không nhận `0x` | Dùng `"FF"` với radix 16 hoặc `Integer.decode` |
| `printf("%b", 42)` in `true` | `%b` là boolean formatter | Dùng `Integer.toBinaryString(42)` |

Một số nguyên có thể lưu cùng các bit cho dù bạn đã viết literal ở hệ nào. Nếu thao tác bitmask, **hex dễ nhìn theo byte**; nếu cần quan sát từng bit, **binary trực quan**; bát phân thường thấy trong định dạng/quy ước cũ; thập phân dễ đọc với dữ liệu nghiệp vụ. Chọn cách viết phù hợp người đọc và định dạng dữ liệu.

## 10. Bài tập tự kiểm tra

**Bài 1.** `0b1111`, `017`, `15`, `0xF` có bằng nhau không?  
**Đáp án:** Có, cả bốn đều bằng 15 thập phân.

**Bài 2.** `010` và `10` có cùng giá trị không?  
**Đáp án:** Không; `010` = 8 thập phân, `10` = 10 thập phân.

**Bài 3.** Chuyển `0x3C` sang nhị phân và thập phân.  
**Đáp án:** `0b0011_1100` (có thể bỏ các số 0 đầu là `0b11_1100`), bằng \(3\times16+12=60\).

**Bài 4.** Vì sao `Integer.toBinaryString(-1)` xuất ra 32 chữ số `1`, còn `Integer.toString(-1, 2)` xuất `"-1"`?  
**Đáp án:** Phương thức thứ nhất trình bày **mẫu bit bù hai** của `int` 32 bit; phương thức thứ hai biểu diễn **giá trị số có dấu** trong cơ số 2.

**Bài 5.** Dùng API nào để parse chuỗi `"11111111"` thành giá trị số 255?  
**Đáp án:** `Integer.parseInt("11111111", 2)`. Nếu ép tiếp sang `byte`, giá trị trở thành -1 vì miền kiểu `byte`.

**Bài 6.** `0xFFFF_FFFFL` có khác `0xFFFF_FFFF` không?  
**Đáp án:** Có; dạng `L` là `long` dương 4_294_967_295, dạng không hậu tố là `int` -1.

**Bài 7.** Vì sao `Integer.parseInt("052")` trả 52 nhưng literal `052` trong mã nguồn là 42?  
**Đáp án:** `parseInt` không truyền radix mặc định dùng **thập phân**, còn literal có số 0 đứng đầu dùng **bát phân**.

**Ghi nhớ:** Cơ số quyết định cách *viết/đọc chữ số*; kiểu dữ liệu quyết định *miền số và độ rộng bit*. Trong Java: **thập phân** viết thường, **nhị phân** bắt đầu `0b`, **bát phân** bắt đầu `0`, **hex** bắt đầu `0x`. Khi xử lý chuỗi từ bên ngoài, truyền cơ số rõ ràng và kiểm tra miền giá trị trước khi ép kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-literals-004">Bẫy số 0 ở đầu literal bát phân</a>

<details>
<summary>Xem chi tiết</summary>

# Bẫy số 0 ở đầu literal bát phân trong Java

Trong Java, **một số nguyên viết trực tiếp trong mã nguồn với chữ số `0` ở đầu có thể là literal hệ bát phân (octal, cơ số 8)**. Vì vậy, `010` trong mã Java **không phải số thập phân 10**: nó có giá trị **8**. Ngược lại, `08` không phải cách viết hợp lệ của một literal số nguyên Java, vì chữ số `8` không tồn tại trong hệ bát phân.

Đây là một bẫy dễ bỏ qua khi người lập trình thêm số 0 để căn chỉnh, sao chép một mã có số 0 đầu vào biểu thức số học, hoặc nhầm lẫn giữa **cú pháp literal trong source code** và **chuỗi số đọc lúc chạy**. Bài học này tập trung vào nguyên nhân, lỗi thường gặp và cách xử lý chính xác trong Java.

## 1. Literal số nguyên là gì và số 0 đầu có nghĩa gì?

**Literal** là cách viết trực tiếp một giá trị trong mã nguồn. Ví dụ:

```java
int decimal = 10;  // Hệ thập phân: 10
int octal = 010;   // Hệ bát phân: 8
```

Hai biến đều có kiểu `int`, nhưng giữ **hai giá trị khác nhau**. Trình biên dịch Java diễn giải ký hiệu literal **ngay khi biên dịch**, trước khi chương trình chạy.

Các cú pháp số nguyên thường gặp:

| Cách viết trong Java | Hệ cơ số | Giá trị hệ thập phân | Nhận xét |
| --- | ---: | ---: | --- |
| `10` | 10 | 10 | Literal thập phân thông thường |
| `010` | 8 | 8 | Số `0` đầu kích hoạt cách viết bát phân |
| `012` | 8 | 10 | `1 × 8 + 2 = 10` |
| `077` | 8 | 63 | `7 × 8 + 7 = 63` |
| `0123` | 8 | 83 | `1 × 64 + 2 × 8 + 3 = 83` |
| `0` | Giá trị 0 | 0 | Một số 0 đơn lẻ không gây bẫy chuyển giá trị |
| `000` | 8 | 0 | Có thể viết được nhưng dễ gây hiểu nhầm |
| `0b1010` | 2 | 10 | Nhị phân có tiền tố `0b`/`0B` |
| `0xA` | 16 | 10 | Thập lục phân có tiền tố `0x`/`0X` |
| `010L` | 8 | 8 | Literal bát phân kiểu `long` |

**Quy tắc nhận diện:** một literal **nguyên** không có `0x`/`0X` hoặc `0b`/`0B`, bắt đầu bằng `0` và còn chữ số phía sau, được đọc theo cú pháp bát phân. Các chữ số hợp lệ của hệ bát phân chỉ là `0` đến `7`.

Java không có tiền tố `0o` cho literal bát phân như một số ngôn ngữ khác. Viết `0o12` trong Java là lỗi biên dịch. Cách viết bát phân thông dụng trong source Java là `012`, nhưng khi mục đích chỉ là giá trị thập phân, hãy viết `10`.

## 2. Tại sao 010 lại bằng 8?

Ở hệ thập phân, mỗi vị trí là một lũy thừa của 10. Ở hệ bát phân, mỗi vị trí là một lũy thừa của 8. Chữ số `0` phía trước đánh dấu cách diễn giải literal, không thêm một hàng thập phân.

Với `010`:

```text
Literal Java: 010
Chữ số bát phân: 1 0
Giá trị thập phân: 1 × 8¹ + 0 × 8⁰ = 8
```

Với `0123`:

```text
Literal Java: 0123
Chữ số bát phân: 1 2 3
Giá trị thập phân: 1 × 8² + 2 × 8¹ + 3 × 8⁰
                  = 64 + 16 + 3
                  = 83
```

Do đó, `010 == 8` là `true` nhưng `010 == 10` là `false`. Điều này không phải lỗi của toán tử so sánh và không liên quan đến tràn số. Trình biên dịch đã gán cho literal `010` giá trị 8 từ đầu.

Một nhầm lẫn có thể len vào ứng dụng:

```java
int retries = 03;      // Thực tế là 3; vô tình chưa thấy lỗi.
int timeout = 010;     // Thực tế là 8, không phải 10.
int limit = 012;       // Thực tế là 10, không phải 12.
```

Các dòng trên là ví dụ khai báo độc lập. Chính sự trùng hợp ở `03` khiến kiểu viết số 0 đầu có thể qua code review mà không bị phát hiện, trước khi xuất hiện giá trị `010` hoặc `012` cho kết quả khác mong đợi.

## 3. Lỗi biên dịch: 08 và 09 không hợp lệ trong literal số nguyên

Hệ bát phân chỉ sử dụng tám chữ số `0, 1, 2, 3, 4, 5, 6, 7`. Các literal số nguyên sau **không biên dịch được**:

```java
// Ví dụ lỗi có chủ đích; KHÔNG đưa vào file demo hợp lệ.
int august = 08;  // LỖI: chữ số 8 không hợp lệ trong literal bát phân.
int year = 09;    // LỖI: chữ số 9 không hợp lệ trong literal bát phân.
int value = 018;  // LỖI: có chữ số 8 trong literal bát phân.
```

**Sửa đúng:** nếu ý định là số nguyên thập phân tám, chín, mười tám, viết:

```java
int august = 8;
int year = 9;
int value = 18;
```

Đừng sửa lỗi bằng cách đổi `08` sang `008`; chữ số `8` vẫn không hợp lệ. Đừng mặc định sửa bằng cách chuyển kiểu sang `long`: `08L` vẫn không hợp lệ vì hậu tố `L` chỉ xác định **kiểu**, không thay đổi cơ số.

### Ngoại lệ quan trọng: 08.0 là literal số thực hợp lệ

Bẫy trên áp dụng cho **literal số nguyên**. Khi có dấu chấm thập phân hoặc cú pháp số thực thích hợp, `08.0` được phân tích là literal dấu phẩy động thập phân và hợp lệ:

```java
double decimalFloating = 08.0; // Hợp lệ: 8.0 (double)
float decimalFloat = 08.0f;    // Hợp lệ: 8.0f (float)
```

Việc `08.0` biên dịch được không có nghĩa `08` là literal nguyên hợp lệ. Cũng không có nghĩa nên thêm `.0` để “sửa” mọi biểu thức: làm như vậy đổi kiểu và có thể thay đổi semantics tính toán (ví dụ chia nguyên so với chia thực). Nếu muốn số nguyên tám, dùng `8`.

## 4. Số 0 đầu xuất hiện ở đâu, và chỗ nào không gây hiểu theo bát phân?

Cần phân biệt bốn ngữ cảnh sau:

| Ngữ cảnh | Cách viết | Giá trị hoặc hành vi |
| --- | --- | --- |
| Literal số nguyên trong mã nguồn | `int x = 010;` | `x == 8` |
| Chuỗi ký tự trong mã nguồn | `String s = "010";` | Ba ký tự `0`, `1`, `0` |
| Chuyển chuỗi sang số nguyên thập phân | `Integer.parseInt("010")` | `10`, vì radix mặc định là 10 |
| Chuyển chuỗi với radix 8 | `Integer.parseInt("010", 8)` | `8` |
| Dùng parser tự phát hiện tiền tố | `Integer.decode("010")` | `8`, vì `decode` nhận biết số 0 đầu là bát phân |

**Đây là điểm mấu chốt:** **`010`** là ký pháp số nguyên được trình biên dịch xử lý, còn **`"010"`** chỉ là chuỗi. Không có một quy tắc “mọi chuỗi bắt đầu bằng 0 đều là bát phân” trong Java.

Ví dụ hợp lệ:

```java
String text = "010";
int decimal = Integer.parseInt(text);      // 10
int decimalExplicit = Integer.parseInt(text, 10); // 10
int octal = Integer.parseInt(text, 8);     // 8
int decoded = Integer.decode(text);        // 8
```

`Integer.parseInt` **không tự dò cơ số**. `Integer.decode` thì có cơ chế phát hiện ký hiệu `0x`/`0X`, `#` cho hex và số 0 đầu cho bát phân. Nếu người dùng nhập `"08"`, `Integer.parseInt("08")` trả 8, nhưng `Integer.decode("08")` ném `NumberFormatException` vì `decode` diễn giải đầu vào theo bát phân. `Long.parseLong` và `Long.decode` cũng có sự phân biệt tương ứng.

Khi đầu vào luôn là **số thập phân**, gọi `Integer.parseInt(text, 10)` hoặc `Long.parseLong(text, 10)` sẽ rõ ý định và dễ bảo trì hơn dùng `decode`. Chỉ dùng `decode` khi đặc tả thực sự yêu cầu người dùng được nhập nhiều cơ số với các tiền tố quy ước.

## 5. Cần giữ số 0 đầu: mã định danh hay số lượng?

Thử xét hai dữ liệu đều viết là `0010`:

- **Số lượng 10 sản phẩm:** giá trị toán học là mười; kiểu `int` và literal `10` là phù hợp. Khi cần hiển thị đủ bốn chữ số, định dạng lại ở **lớp hiển thị**, chẳng hạn `String.format("%04d", 10)`.
- **Mã vé `"0010"`:** dãy ký tự, không phải số lượng. Dùng `String` để giữ đầy đủ số 0 đầu. Chuyển mã sang `int` làm mất hình thức nhận diện; không thể suy ra có bao nhiêu số 0 ban đầu nếu chưa có quy định chiều dài.

```java
String ticketCode = "0010"; // Giữ nguyên định danh.
int count = 10;            // Giá trị số học.
String countLabel = String.format(
        java.util.Locale.ROOT, "%04d", count); // "0010"
```

Ví dụ trên là mảnh mã Java. **Chọn `String`** cho mã bưu chính, mã khách hàng, số điện thoại, mã đơn hàng, ID có số 0 đầu hoặc chuỗi mà các ký tự mang ý nghĩa riêng. Đừng dùng `int postalCode = 01234;`: ngoài việc làm mất định dạng gốc, giá trị đó còn bị diễn giải thành bát phân và tương đương **668 thập phân**, không phải 1234.

Nếu trường nhập là số lượng có thể cho phép người dùng gõ `"0010"`, hãy quyết định rõ có chấp nhận số 0 đệm hay không: `Integer.parseInt("0010", 10)` nhận và trả 10. Nếu chính sách nhập yêu cầu dạng chuẩn không có số 0 thừa, phải xác thực cú pháp trước khi parse; parser thập phân không tự áp đặt quy tắc nghiệp vụ đó.

## 6. Số 0 đầu trong literal không phải quy tắc chung của JSON, CSV hay cơ sở dữ liệu

Bẫy này thuộc **cú pháp mã nguồn Java**. Không được suy rộng rằng mọi dữ liệu bên ngoài mang chữ số `0` đầu đều phải đọc là bát phân:

- Trong **CSV**, giá trị `0010` là dữ liệu văn bản ban đầu; cách chuyển sang kiểu số phụ thuộc thư viện nhập, schema và radix được chỉ định.
- Trong **JSON chuẩn**, number `010` không hợp lệ vì ngữ pháp JSON không cho số nguyên có số 0 dư ở đầu. Dùng `10` nếu là số lượng, hoặc `"0010"` nếu phải bảo toàn mã.
- Trong **cơ sở dữ liệu**, một trường `VARCHAR` giữ chuỗi theo cách lưu trữ; trường số nguyên lưu giá trị số. Cách truyền dữ liệu qua driver, SQL dialect và cách format kết quả có thể khác nhau; đừng suy ra rằng DB sẽ diễn giải số 0 đầu giống literal Java.
- Trong **cấu hình**, cách hiểu `010` tùy định dạng và thư viện parser. Ví dụ, các phiên bản/quy tắc YAML khác nhau từng có khác biệt về số 0 đầu. Hãy cấu hình rõ kiểu và quy tắc đọc, tránh giả định.

Khi nhận giá trị từ API hoặc file, tốt nhất là **xác định schema**: trường là số lượng thập phân, mã định danh hay một literal có cơ số cho phép chọn? Một lần parse sai ở biên hệ thống có thể lặng lẽ biến dữ liệu hợp lệ thành một con số khác.

## 7. Phép tính, số âm và kiểu long vẫn chịu quy tắc literal

Hậu tố `L` trên literal nguyên chỉ thay đổi kiểu từ `int` sang `long` (khi thích hợp), không thay cơ số:

```java
long expectedDecimal = 10L;
long octalLong = 010L;       // 8L
```

Dấu trừ là **toán tử một ngôi** áp dụng lên giá trị literal: `-010` là âm tám (`-8`), không phải âm mười. Tương tự, `+010` có giá trị tám.

```java
int a = 010 + 1;       // 9, vì 010 == 8
int b = 10 + 1;        // 11
boolean same = 010 == 8;  // true
int negative = -010;   // -8
```

Khi kết quả bị lệch, hãy xem **literal ở đầu vào** trước khi nghi ngờ phép cộng, phép trừ hay ép kiểu. In ra số bằng `System.out.println` thường cho biểu diễn thập phân của **giá trị đã lưu**, nên chương trình `System.out.println(010);` in `8`.

Literal `0` đơn lẻ có giá trị 0 và không tạo sai khác số học; `00`, `000` vẫn có giá trị 0. Nhưng thói quen thêm số 0 đầu là rủi ro vì các chữ số khác 0 có thể đổi nghĩa hoặc làm mã không biên dịch.

## 8. Ví dụ Java 17+ hoàn chỉnh để tự kiểm chứng

Tạo file **`OctalLeadingZeroDemo.java`** với nội dung sau. Chương trình chỉ dùng các literal **hợp lệ**; những dòng `08`/`09` sai cú pháp ở phần trước đã được tách ra khỏi chương trình chạy được.

```java
import java.util.Locale;

public class OctalLeadingZeroDemo {
    public static void main(String[] args) {
        int decimalTen = 10;
        int octalTen = 010;
        int octalForTen = 012;
        long octalLong = 010L;

        System.out.println("decimal-10=" + decimalTen);
        System.out.println("octal-010=" + octalTen);
        System.out.println("octal-012=" + octalForTen);
        System.out.println("octal-long=" + octalLong);
        System.out.println("binary-1010=" + 0b1010);
        System.out.println("hex-a=" + 0xA);
        System.out.println("negative-octal=" + (-010));
        System.out.println("decimal-floating=" + 08.0);

        String input = "010";
        System.out.println("parse-decimal=" + Integer.parseInt(input));
        System.out.println("parse-octal=" + Integer.parseInt(input, 8));
        System.out.println("decode-octal=" + Integer.decode(input));
        System.out.println("octal-format=" + Integer.toOctalString(10));

        String ticketCode = "0010";
        System.out.println("ticket-code=" + ticketCode);
        System.out.println("padded-count=" +
                String.format(Locale.ROOT, "%04d", 10));

        try {
            Integer.decode("08");
        } catch (NumberFormatException ex) {
            System.out.println("decode-08=NumberFormatException");
        }
    }
}
```

Biên dịch/chạy bằng JDK 17 hoặc phiên bản mới hơn:

```bash
javac OctalLeadingZeroDemo.java
java OctalLeadingZeroDemo
```

Kết quả stdout:

```text
decimal-10=10
octal-010=8
octal-012=10
octal-long=8
binary-1010=10
hex-a=10
negative-octal=-8
decimal-floating=8.0
parse-decimal=10
parse-octal=8
decode-octal=8
octal-format=12
ticket-code=0010
padded-count=0010
decode-08=NumberFormatException
```

Giải thích các điểm dễ lẫn:

1. `010` và `010L` có giá trị 8 vì là bát phân; hậu tố `L` chỉ làm kết quả mang kiểu `long`.
2. `012`, `0b1010`, `0xA` và `10` đều có giá trị thập phân 10 dù ký pháp khác nhau.
3. `08.0` là literal `double` hợp lệ và in `8.0`; `08` nguyên thì không hợp lệ.
4. Cùng chuỗi `"010"`, `parseInt` mặc định ra 10 còn `decode` ra 8.
5. `Integer.toOctalString(10)` cho chuỗi `"12"` vì 10 thập phân tương đương 12 bát phân. Chuỗi kết quả không tự thêm số 0 đầu theo cú pháp literal Java.
6. Mã `"0010"` giữ đúng ký tự; phần định dạng `%04d` tạo chuỗi có số 0 đệm cho giá trị số mười, nhưng không đổi giá trị số nguyên trong bộ nhớ.

## 9. Phân biệt lỗi khi biên dịch và lỗi khi chạy

| Mã hoặc thao tác | Thời điểm phát hiện | Nguyên nhân |
| --- | --- | --- |
| `int x = 08;` | Biên dịch | Chữ số 8 không hợp lệ trong literal nguyên bát phân |
| `int x = 09;` | Biên dịch | Chữ số 9 không hợp lệ trong literal nguyên bát phân |
| `int x = 010;` | Không lỗi; có thể **sai logic** khi chạy | Hợp lệ nhưng giá trị thực là 8 |
| `Integer.parseInt("08", 10)` | Không lỗi; trả 8 | Parse chuỗi với cơ số thập phân |
| `Integer.parseInt("08", 8)` | Runtime: `NumberFormatException` | Đầu vào có chữ số không thuộc radix 8 |
| `Integer.decode("08")` | Runtime: `NumberFormatException` | Tự nhận dạng cơ số 8 do số 0 đầu |
| `Integer.parseInt("0010", 10)` | Không lỗi; trả 10 | Phân tích số và bỏ các số 0 thừa về mặt giá trị |
| `String code = "0010";` | Không lỗi; giữ `"0010"` | Dữ liệu chuỗi, không phải literal số nguyên |

Điều nguy hiểm nhất là trường hợp **không báo lỗi**: `010` hợp lệ nên unit test chỉ kiểm tra biên dịch, hoặc test data chỉ dùng `01` đến `07`, có thể không phát hiện sai lệch. Đây là lỗi **ngữ nghĩa** của code.

## 10. Cách phòng tránh trong code thật

### 10.1. Viết literal theo ý nghĩa muốn thể hiện

- Nếu ý định là số thập phân, **không viết số 0 ở đầu**: `8`, `10`, `123`.
- Nếu ý định là giá trị bit/permission hoặc cơ số đặc biệt, có thể dùng ký pháp phù hợp và thêm chú thích về ý nghĩa; đối với bitmask, `0b...` hoặc `0x...` thường dễ đọc hơn.
- Nếu ý định là **định dạng có số 0 đệm**, dùng `String` và định dạng ở nơi nhập/xuất; không dùng literal nguyên bắt đầu bằng 0.
- Khi đọc chuỗi số, **chỉ rõ radix** nếu đầu vào có thể bị hiểu nhầm: `Integer.parseInt(text, 10)`.

### 10.2. Kiểm tra dữ liệu đầu vào và radix

Một parser đơn giản cho **số đếm không âm, dạng thập phân chuẩn, không cho số 0 thừa** có thể viết như sau:

```java
static int parseCanonicalCount(String text) {
    if (text == null || !text.matches("0|[1-9][0-9]*")) {
        throw new IllegalArgumentException("Invalid decimal count");
    }
    return Integer.parseInt(text, 10);
}
```

Đây là phương thức minh họa, đặt bên trong một class Java. Theo hợp đồng này, `"0"`, `"8"`, `"10"` hợp lệ, còn `"08"`, `"010"`, `"-1"`, chuỗi trống hoặc giá trị chứa khoảng trắng bị từ chối. Một chuỗi toàn chữ số quá lớn vẫn có thể khiến `Integer.parseInt` ném `NumberFormatException` do vượt miền `int`; có thể bắt và chuyển thành lỗi validation thống nhất tại biên API.

Nếu yêu cầu nghiệp vụ **cho phép số 0 đầu**, không áp dụng regex này: dùng parser thập phân với radix 10, hoặc giữ chuỗi nếu đó là mã. Kiểm tra dữ liệu phải dựa trên đặc tả, không mặc định một chính sách cho tất cả trường.

### 10.3. Test đặc biệt tại các mốc 07, 08, 09, 010

Trong review và kiểm thử, chú ý những vùng dữ liệu tuần tự có padding, đặc biệt ngày, tháng, phiên bản và mã từ `01` đến `12`. Ví dụ, ghi `int month = 010;` với ý định tháng 10 sẽ thực sự lưu 8; còn `int month = 08;` không biên dịch. Kiểm thử tháng 1–7 không thể chứng minh cách viết đó đúng cho tháng 8–12.

Các mẫu tìm kiếm số 0 đầu trong source có thể hỗ trợ review, nhưng cần đọc ngữ cảnh: số 0 trong **chuỗi**, số thực `08.0`, các literal `0x...`/`0b...`, các giá trị 0 và các comment không nên bị đánh đồng với lỗi. Static analysis và code review cần nêu rõ ý định của literal trước khi sửa.

### 10.4. Đừng tự động “sửa” dữ liệu mã thành số

Nếu người dùng gửi `"00123"`, ứng dụng cần biết đây là **mã** hay **số lượng** trước khi parse. Chuyển thành số 123 rồi ghi lại chuỗi `"123"` có thể làm mất tính định danh, kể cả khi không hề dùng octal. Ngược lại, một phép tính đếm không nên giữ số 0 đầu chỉ để “giống màn hình”; trình bày tách biệt khỏi giá trị.

## 11. Câu hỏi tự kiểm tra

1. Tính giá trị thập phân của `010`, `012`, `077`, `0123` mà không chạy Java. Giải thích bằng tổng các lũy thừa của 8.
2. Trong bốn biểu thức `08`, `08.0`, `010L`, `0o10`, biểu thức nào là literal Java hợp lệ? Biểu thức nào không biên dịch được và vì sao?
3. Cho `String s = "010";`, dự đoán kết quả `Integer.parseInt(s)`, `Integer.parseInt(s, 8)`, `Integer.decode(s)`. Trường hợp nào phù hợp để xử lý một chuỗi số lượng nhập theo hệ thập phân?
4. Vì sao `int month = 010;` là bẫy khó thấy hơn `int month = 08;`? Test hoặc công cụ nào có thể giúp phát hiện?
5. Một mã khách hàng `"000123"` phải giữ nguyên khi gửi qua API và lưu vào database. Bạn chọn kiểu Java nào, loại cột/schema nào và định dạng JSON nào? Vì sao dùng `int` có thể làm mất thông tin?
6. Sửa chương trình sau để giữ ý định “giới hạn mười” mà không đổi sang kiểu thực:

```java
int limit = 010;
System.out.println(limit + 2);
```

Đáp án: đổi literal thành `10`, khi đó chương trình in `12`. Mã ban đầu in `10` vì `010` là 8.

## 12. Ghi nhớ

- **Trong mã nguồn Java, `010` là literal nguyên bát phân có giá trị 8**, còn `10` là literal thập phân có giá trị 10.
- **`08` và `09` không phải literal số nguyên hợp lệ**, nhưng `08.0` là literal số thực thập phân hợp lệ.
- **`"010"` là chuỗi**, không được tự diễn giải thành bát phân. `Integer.parseInt("010")` trả 10; `Integer.decode("010")` trả 8.
- Giữ số 0 đầu bằng **chuỗi/định dạng**, không bằng cách thêm số 0 vào literal nguyên.
- Khi review code, kiểm tra ý nghĩa của dữ liệu, đặc biệt các giá trị tuần tự có padding, và viết rõ radix ở biên nhập liệu khi cần.

Chỉ một chữ số `0` được đặt sai chỗ có thể biến giá trị thập phân dự định thành giá trị bát phân mà Java vẫn biên dịch thành công. Cách phòng tránh hiệu quả nhất là tách **giá trị số học**, **cơ số biểu diễn** và **dạng văn bản của dữ liệu** ngay từ thiết kế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-literals-005">Dấu gạch dưới trong literal và các vị trí không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu gạch dưới trong literal và các vị trí không hợp lệ trong Java

## 1. Mục đích: đọc một con số dài dễ hơn mà không đổi giá trị

Từ Java 7, mã nguồn Java cho phép dùng dấu gạch dưới `_` làm **ký tự phân nhóm chữ số** bên trong *numeric literal* (literal số nguyên hoặc số thực). Dấu `_` có ý nghĩa về **cách viết mã nguồn**, không phải một phép toán hay một chữ số góp phần vào giá trị số.

Ví dụ:

```java
int population = 12_345_678;
long accountId = 1_234_567_890_123L;
double ratio = 3.141_592_653_589_793;
```

Các biểu thức trên có cùng giá trị với phiên bản bỏ dấu gạch dưới:

```java
int population = 12345678;
long accountId = 1234567890123L;
double ratio = 3.141592653589793;
```

**Nguyên tắc quan trọng:** Gạch dưới được phép **nằm giữa các chữ số của cùng một phần số**. Nó không được phép thay thế chữ số, tách các ký hiệu tiền tố cơ số, đứng sát dấu thập phân, ký hiệu số mũ hoặc hậu tố kiểu.

Một *literal* là cách viết trực tiếp giá trị trong **mã nguồn** (như `1_000`). Nó khác với chuỗi dữ liệu `"1_000"` đang được xử lý lúc chương trình chạy. Đây là nguồn gốc của nhiều lỗi khi làm việc với `Integer.parseInt`.

## 2. Các cơ số số nguyên cho phép dùng `_` ở đâu?

Java có bốn cách viết *integer literal* cơ bản:

| Cơ số | Cách viết | Ví dụ hợp lệ | Giá trị thập phân |
| --- | --- | --- | ---: |
| 10 (decimal) | Chữ số `0..9`, số nhiều chữ số dương thường không bắt đầu bằng `0` | `1_234_567` | 1234567 |
| 2 (binary) | Tiền tố `0b`/`0B` rồi đến `0`, `1` | `0b1010_0101` | 165 |
| 8 (octal) | Số nguyên bắt đầu bằng `0`, chữ số `0..7` | `0_52` | 42 |
| 16 (hexadecimal) | Tiền tố `0x`/`0X`, các chữ số `0..9`, `A..F` | `0xCA_FE` | 51966 |

Các ví dụ khác đều hợp lệ:

```java
int a = 12_34;             // decimal
int b = 1__000;            // Nhiều dấu _ liên tiếp giữa các chữ số vẫn hợp lệ.
int c = 0b1111_0000;       // binary
int d = 0xA_B_C_D;         // hexadecimal: A..F cũng là chữ số trong hệ 16.
int e = 05_2;              // octal = 42.
int f = 0_52;              // octal = 42; _ ngay sau chữ số 0 đầu là hợp lệ.
long g = 9_876_543_210L;   // L là hậu tố long, không có _ sát L.
```

Quy tắc có chi tiết dễ nhầm:

- Trong số **nhị phân và thập lục phân**, hai ký tự `0b`/`0B` và `0x`/`0X` là **tiền tố**, không thuộc các chữ số của giá trị số. Sau tiền tố phải bắt đầu bằng một chữ số hợp lệ: `0b_1010` và `0x_FF` là **sai**.
- Trong số **bát phân**, chữ số `0` đầu tiên là **một chữ số của literal**, vì vậy `0_52` và `0__52` đều **đúng**. Đây không giống tiền tố `0x`/`0b`.
- Trong số **thập phân**, có thể bắt đầu nhóm bằng chữ số khác `0`, rồi dùng `_` ở giữa, ví dụ `1_000_000`. Dấu `_` không khiến `0_52` trở thành số thập phân; chuỗi đó vẫn là octal.
- Với literal số nguyên có hậu tố `L`/`l` (khuyến nghị `L` vì dễ phân biệt với `1`), dấu `_` phải nằm **trước chữ số cuối cùng**, không được đứng giữa chữ số cuối và hậu tố.

**Độ rộng kiểu vẫn không đổi.** `1_000_000` vẫn là literal `int`; `1_000_000L` là `long`. Dấu gạch dưới không làm số vượt giới hạn kiểu trở nên hợp lệ và không thay đổi ý nghĩa của tiền tố cơ số.

## 3. Dấu gạch dưới trong literal dấu phẩy động

Literal `float`/`double` có thể chứa các phần khác nhau: phần nguyên, dấu thập phân `.`, phần phân số, phần số mũ (nếu có), và hậu tố kiểu `F`/`f`/`D`/`d` (nếu có).

Ví dụ hợp lệ:

```java
double a = 12_345.67_89;
float b = 3.14_15F;
double c = 1.2_5e1_0;
double d = 1_2.5_0e+1_0;
double e = 0x1.fp1_0;
double f = 0xA_B.C_Dp+1_0;
```

Cách đọc:

- `12_345.67_89` có gạch dưới **trong** phần nguyên và phần phân số; không có dấu `_` nằm cạnh dấu chấm.
- `1.2_5e1_0` có gạch dưới trong các chữ số của phần phân số và **trong** các chữ số của số mũ; chữ `e` không bị kẹp sát bởi `_`.
- `0x1.fp1_0` là **hexadecimal floating-point literal**. Trong hệ này, phần trị (significand) dùng các chữ số hệ 16, còn số mũ sau `p`/`P` là số mũ của 2 và dùng chữ số thập phân. Dấu `_` trong `1_0` của số mũ là hợp lệ.
- `F`/`f` chọn `float`; `D`/`d` chọn `double` (không có hậu tố, một literal dấu phẩy động thông thường có kiểu `double`). Không đặt gạch dưới sát các hậu tố.

Không được đặt gạch dưới **ngay trước hoặc sau** `.`, `e`/`E`, `p`/`P`, dấu `+`/`-` của số mũ, hoặc các hậu tố kiểu. Các vùng chữ số riêng có thể chứa gạch dưới **ở giữa các chữ số**.

Ví dụ `1.25e1_0` hợp lệ; `1.25e_10` không hợp lệ; `1.25e+1_0` hợp lệ; `1.25e+_10` không hợp lệ.

**Phân biệt số nguyên thập lục phân với số thực thập lục phân:** `0xA_B` là literal nguyên; `0x1.fp1_0` là literal dấu phẩy động và bắt buộc có phần số mũ `p`/`P`. Trong cả hai, không được đặt `_` ngay sau `0x`.

## 4. Những vị trí không hợp lệ: bảng tra cứu nhanh

Các đoạn ở cột “Sai” là **mảnh mã cố ý không hợp lệ**, không nên đưa trực tiếp vào chương trình đang biên dịch.

| Vị trí sai | Sai | Đúng | Vì sao |
| --- | --- | --- | --- |
| Trước chữ số đầu | `_123` (khi muốn viết literal) | `123` | `_123` được phân tích như **tên định danh** chứ không phải literal số. |
| Sau chữ số cuối | `123_` | `12_3` | Phần số không được kết thúc bằng `_`. |
| Giữa `0` và `x` | `0_xFF` | `0xFF` | Không được chèn dấu vào tiền tố hệ 16. |
| Ngay sau `0x` | `0x_FF` | `0xF_F` | Sau tiền tố phải có chữ số hệ 16. |
| Giữa `0` và `b` | `0_b101` | `0b101` | Không được chèn dấu vào tiền tố hệ 2. |
| Ngay sau `0b` | `0b_101` | `0b1_01` | Sau tiền tố phải có chữ số nhị phân. |
| Trước hậu tố số nguyên | `1_000_L` | `1_000L` | Không có chữ số sau `_` trước `L`. |
| Trước dấu thập phân | `3_.1415` | `3.141_5` | `_` không đứng sát `.`. |
| Sau dấu thập phân | `3._1415` | `3.14_15` | `_` không được mở đầu phần phân số. |
| Trước ký hiệu số mũ | `1_e2` | `1e2` | Gạch dưới không kết thúc phần chữ số trước `e`. |
| Sau ký hiệu số mũ | `1e_2` | `1e2` | Gạch dưới không bắt đầu dãy chữ số số mũ. |
| Sau dấu số mũ | `1e+_2` | `1e+2` | Dấu `+`/`-` phải đi liền chữ số số mũ. |
| Cuối phần số mũ | `1e2_` | `1e2_0` | Dãy chữ số không được kết thúc bằng `_`. |
| Trước hậu tố `F`/`D` | `3.14_F`, `2.0_D` | `3.14F`, `2.0D` | Hậu tố không phải chữ số. |
| Trước `p` hệ 16 | `0x1.f_p4` | `0x1.fp4` | `_` không sát ký hiệu số mũ nhị phân. |
| Sau `p` hệ 16 | `0x1.fp_4` | `0x1.fp4` | Phần số mũ phải bắt đầu bằng chữ số, có thể có dấu. |

Một cách tự kiểm tra: chỉ dùng `_` để phân cách các chữ số trong **một nhóm chữ số liên tục**. Nếu dấu `_` sẽ chạm vào `.`, ký tự chữ thể hiện **cấu trúc** (`x`, `b`, `e`, `p`, `L`, `F`, `D`) hoặc dấu `+`/`-` của số mũ, hãy viết lại.

Cần phân biệt hai trường hợp: `0xA_B` **hợp lệ** vì `A` và `B` là chữ số hệ 16; `0xA_p1` **không hợp lệ** vì `p` là ký hiệu số mũ, không phải một chữ số hệ 16.

## 5. Có thể dùng nhiều dấu `_` liền nhau không?

**Có**, nếu cả chuỗi gạch dưới vẫn nằm *giữa hai chữ số thuộc cùng thành phần số*. Quy tắc Java không giới hạn mỗi chỗ đúng một dấu `_`.

```java
int number = 1________0;      // Hợp lệ: giá trị 10.
int hex = 0xA___B;             // Hợp lệ: giá trị 171.
int octal = 0__7;              // Hợp lệ: giá trị 7 (octal).
double fraction = 1.2___5;     // Hợp lệ: giá trị 1.25.
```

Nhưng thực tế nên phân nhóm hợp lý, ví dụ `1_000_000`, `0xCAFE_BABE` hoặc `0b1111_0000`; `1________0` tuy hợp lệ vẫn khó đọc.

Với quy tắc kiểm tra vị trí, một chuỗi gạch dưới có **các chữ số hợp lệ ở cả hai phía** là dấu hiệu cần, nhưng phải nhớ hai phía thuộc **cùng phần chữ số**: không được nhầm tiền tố cơ số, dấu thập phân và ký hiệu số mũ thành chữ số.

## 6. Khác biệt giữa cú pháp mã nguồn và dữ liệu chuỗi lúc chạy

Dấu `_` được *trình biên dịch* công nhận trong một literal. Khi chạy, giá trị lưu trong biến là **giá trị số**, không mang theo các dấu phân nhóm trong cách viết ban đầu.

```java
int amount = 1_000;
System.out.println(amount); // In 1000, không in 1_000.
```

Tuy nhiên, các hàm parse chuỗi thông dụng **không áp dụng cú pháp literal của trình biên dịch**:

```java
Integer.parseInt("1000");      // 1000.
Integer.parseInt("1_000");     // Ném NumberFormatException.
Double.parseDouble("3.1415");  // 3.1415.
Double.parseDouble("3.14_15"); // Ném NumberFormatException.
```

Do đó, nếu nhận input từ API, tệp cấu hình hoặc biểu mẫu có gạch dưới, bạn phải xác định **định dạng dữ liệu** có cho phép dấu phân nhóm này hay không. Nếu có, hãy kiểm tra dữ liệu đầu vào theo một quy tắc rõ ràng trước khi chuyển sang số. Không nên mù quáng xóa mọi `_`, vì điều đó có thể biến chuỗi không hợp lệ như `"_100"` hoặc `"10_"` thành dữ liệu được chấp nhận ngoài ý muốn.

Một cách minh họa cho **định dạng thập phân không dấu, cho phép một hoặc nhiều dấu `_` giữa các chữ số**:

```java
static int parseGroupedDecimal(String input) {
    if (!input.matches("[0-9]+(?:_+[0-9]+)*")) {
        throw new IllegalArgumentException("Invalid decimal grouping");
    }
    return Integer.parseInt(input.replace("_", ""));
}
```

Đây là **quy tắc của ứng dụng** (chỉ hỗ trợ số nguyên thập phân không dấu), không phải một parser đầy đủ cho mọi Java literal. Dữ liệu sau khi bỏ dấu phân cách còn có thể vượt miền `int`, khi đó `Integer.parseInt` vẫn ném `NumberFormatException`. Nếu nhận số thập phân quá lớn, cân nhắc `BigInteger` và kiểm soát độ dài input.

Ngoài numeric literal, dấu `_` còn có thể xuất hiện trong tên định danh (nếu tuân theo quy tắc đặt tên) hoặc chuỗi ký tự; các trường hợp đó không tự mang nghĩa “phân nhóm chữ số”. Riêng **một dấu `_` đứng một mình** đã là từ khóa dành riêng từ Java 9, không được dùng làm tên biến độc lập.

## 7. Ví dụ Java 17+ chạy được

Lưu trong tệp **`UnderscoreLiteralDemo.java`**:

```java
public class UnderscoreLiteralDemo {
    public static void main(String[] args) {
        int decimal = 1_234_567;
        int repeated = 1__000;
        int octal = 0_52;
        int hex = 0xCA_FE;
        int binary = 0b1010_0101;
        double fraction = 3.14_15;
        double scientific = 1.25e1_0;
        double hexadecimalFloat = 0x1.fp1_0;
        long id = 1_234_567_890L;

        System.out.println("decimal=" + decimal);
        System.out.println("repeated=" + repeated);
        System.out.println("octal=" + octal);
        System.out.println("hex=" + hex);
        System.out.println("binary=" + binary);
        System.out.println("fraction=" + fraction);
        System.out.println("scientific valid=" + (scientific == 12_500_000_000.0));
        System.out.println("hex float=" + hexadecimalFloat);
        System.out.println("long=" + id);

        try {
            Integer.parseInt("1_000");
        } catch (NumberFormatException ex) {
            System.out.println("String 1_000 cannot be parsed as int directly");
        }
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac UnderscoreLiteralDemo.java
java UnderscoreLiteralDemo
```

Kết quả:

```text
decimal=1234567
repeated=1000
octal=42
hex=51966
binary=165
fraction=3.1415
scientific valid=true
hex float=1984.0
long=1234567890
String 1_000 cannot be parsed as int directly
```

Bạn có thể tự kiểm tra lỗi biên dịch bằng cách **thêm từng dòng sau vào trong `main` rồi biên dịch lại**. Các dòng được comment để ví dụ hoàn chỉnh ở trên vẫn chạy được:

```java
// int invalid1 = 123_;
// int invalid2 = 0x_FF;
// int invalid3 = 0b_1010;
// long invalid4 = 1_000_L;
// double invalid5 = 3_.1415;
// double invalid6 = 3._1415;
// double invalid7 = 1e_3;
// double invalid8 = 1e+_3;
// double invalid9 = 0x1.fp_4;
```

Các dòng này minh họa **lỗi cú pháp literal được phát hiện tại thời điểm biên dịch**, không phải `NumberFormatException` lúc chạy. Ngược lại, `Integer.parseInt("1_000")` nằm trong mã nguồn hoàn toàn hợp lệ; nó lỗi khi phương thức xử lý chuỗi tại runtime.

## 8. Quy ước viết mã cho dễ đọc

Dấu `_` có ích nhất khi nhóm chữ số có ý nghĩa rõ ràng:

- Số thập phân theo nhóm ba chữ số từ phải qua trái: `1_000`, `25_000_000`.
- Số nhị phân theo byte hoặc nibble: `0b1100_1010`, `0b1111_0000_1010_0101`.
- Số thập lục phân theo byte/word: `0xFF_00_7F`, `0xCAFE_BABE`.
- Số thực có nhiều chữ số có thể phân nhóm phần nguyên, phần phân số hoặc phần số mũ độc lập, nhưng đừng đặt dấu sát ký hiệu phân cách.

Không nhất thiết phải thêm `_` cho số rất ngắn như `12` hoặc `0xFF`. Tránh nhóm tùy tiện làm mất ý nghĩa, ví dụ `12_34_56` trong bối cảnh cả dự án đang nhóm chữ số thập phân theo ba.

**Tóm lại:** Trong Java, `_` giúp con người đọc numeric literal mà **không đổi giá trị số hay kiểu dữ liệu**. Chỉ đặt nó giữa các chữ số của cùng một phần số; nhớ ngoại lệ quan trọng của số bát phân `0_52` là hợp lệ, trong khi `0x_FF` và `0b_101` không hợp lệ. Việc parse dữ liệu chuỗi là một việc riêng, cần xử lý theo định dạng đầu vào.

**Tài liệu tham khảo:** [Java Language Specification §3.10.1 — Integer Literals](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html#jls-3.10.1), [§3.10.2 — Floating-Point Literals](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html#jls-3.10.2), và [Oracle — Underscores in Numeric Literals](https://docs.oracle.com/javase/8/docs/technotes/guides/language/underscores-literals.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-literals-006">Literal số thực, ký hiệu mũ và hậu tố F, D</a>

<details>
<summary>Xem chi tiết</summary>

# Literal số thực, ký hiệu mũ và hậu tố F, D trong Java

## 1. Mục tiêu của bài học

Trong Java, **floating-point literal** là cách viết trực tiếp một giá trị số thực trong mã nguồn. Bài này thuộc nhóm **Literal và cơ số** của Java Core — Numbers. Sau khi học, bạn cần:

- Đọc và viết đúng các dạng literal thập phân như `3.14`, `.5`, `10.`, `6.02e23`.
- Hiểu ký hiệu mũ `e`/`E` là **lũy thừa cơ số 10**, trong khi `p`/`P` của literal thập lục phân biểu diễn **lũy thừa cơ số 2**.
- Biết `f`/`F` tạo literal kiểu `float`; `d`/`D` hoặc không có hậu tố tạo literal kiểu `double`.
- Phân biệt **kiểu của literal**, **kiểu của biến nhận giá trị**, **ép kiểu** và **độ chính xác lưu trữ**.
- Nhận diện quy tắc dấu, dấu chấm, chữ số, dấu gạch dưới, trường hợp sai cú pháp và literal vượt giới hạn biểu diễn.
- Tự đối chiếu Java Language Specification (JLS) và chạy ví dụ bằng JDK 17 trở lên.

**Điểm cốt lõi:** một literal được phân tích và gán kiểu ngay khi biên dịch. Dấu phẩy động trong chương trình Java dùng dấu chấm `.`, không dùng dấu phẩy `,` theo cách ghi số của một số ngôn ngữ tự nhiên.

## 2. Literal số thực là gì?

Hãy bắt đầu bằng những khai báo sau:

```java
int whole = 12;
double fraction = 12.0;
double scientific = 1.2e1;
float small = 12.0F;
```

Cả bốn biến có thể biểu diễn lượng mười hai, nhưng **kiểu và cú pháp nguồn không giống nhau**:

| Cách viết | Loại token/kiểu | Ý nghĩa |
| --- | --- | --- |
| `12` | Literal số nguyên kiểu `int` | Mười hai |
| `12L` | Literal số nguyên kiểu `long` | Mười hai, vẫn là số nguyên |
| `12.0` | Literal số thực kiểu `double` | Mười hai dưới dạng floating point |
| `12.` | Literal số thực kiểu `double` | Tương đương `12.0` |
| `.5` | Literal số thực kiểu `double` | Một nửa |
| `12e0` | Literal số thực kiểu `double` | 12 × 10⁰ |
| `12f`, `12.0F` | Literal số thực kiểu `float` | Mười hai dưới dạng `float` |
| `12d`, `12.0D` | Literal số thực kiểu `double` | Hậu tố `D` ghi rõ kiểu mặc định |
| `0x1.8p3` | Literal số thực kiểu `double`, dạng hex | (1 + 8/16) × 2³ = 12 |

Trong Java, số thực primitive gồm hai kiểu:

- `float`: khuôn dạng IEEE 754 **binary32**, có khoảng 24 bit significand (xấp xỉ 6–9 chữ số thập phân có nghĩa khi biểu diễn/khôi phục).
- `double`: khuôn dạng IEEE 754 **binary64**, có khoảng 53 bit significand (xấp xỉ 15–17 chữ số thập phân có nghĩa khi biểu diễn/khôi phục).

Đó là độ chính xác **nhị phân hữu hạn**, không phải số lượng chữ số sau dấu chấm. Không nên kết luận rằng literal có nhiều chữ số thập phân sẽ được giữ chính xác nguyên vẹn trong `double` hay `float`.

## 3. Bốn dạng cú pháp literal số thực thập phân

Theo JLS `3.10.2, literal thập phân phải có **ít nhất một chữ số**, và phải có **dấu chấm, phần mũ hoặc hậu tố kiểu số thực**. Dưới đây là bốn cách viết tiêu biểu:

| Dạng | Ví dụ hợp lệ | Giải thích |
| --- | --- | --- |
| Chữ số rồi dấu chấm, có hoặc không có phần lẻ | `1.`, `1.25`, `1.25e2` | Có `.` nên là floating-point literal |
| Dấu chấm rồi chữ số | `.25`, `.25F`, `.25e+2` | Được phép bỏ số 0 trước dấu chấm |
| Chữ số rồi phần mũ | `1e3`, `12E-2`, `10e0D` | `e`/`E` đủ để biến thành literal số thực |
| Chữ số rồi hậu tố `F/f/D/d` | `1f`, `20F`, `3d` | Hậu tố nhận diện kiểu ngay cả khi không có `.` |

Cụ thể, các literal sau đều hợp lệ:

```java
double a = 7.0;
double b = 7.;
double c = .7;
double d = 7e2;
double e = 7.0E-2;
float f = 7f;
float g = .7F;
double h = 7D;
```

Ngược lại, `7` là literal số nguyên `int`; việc gán nó vào biến `double` là **widening conversion**, không biến token `7` thành floating-point literal. Đây là khác biệt quan trọng khi phân tích overload, biểu thức và giá trị trung gian.

**Không dùng dấu phẩy trong literal:** `3,14` không tạo ra số thực “ba phẩy mười bốn”. Trong cú pháp Java, dấu phẩy có vai trò phân cách ở một số cấu trúc. Viết `3.14` trong mã nguồn; chỉ dùng định dạng địa phương khi xử lý chuỗi nhập/xuất.

## 4. Ký hiệu mũ thập phân: e và E

Dạng tổng quát dễ nhớ:

```text
phần_trị_số e hoặc E dấu_mũ_tùy_chọn chữ_số_mũ
```

Về toán học, `aeb` có nghĩa là **a × 10ᵇ** khi `a`, `b` là phần trị số và số mũ tương ứng. Chữ `e` là ký hiệu cú pháp, **không phải** hằng số Euler `e ≈ 2.71828...`.

| Literal | Phép tính toán học | Giá trị |
| --- | --- | --- |
| `1e3` | 1 × 10³ | 1000 |
| `2.5E2` | 2.5 × 10² | 250 |
| `6.25e-2` | 6.25 × 10⁻² | 0.0625 |
| `1E+2` | 1 × 10² | 100 |
| `7e0` | 7 × 10⁰ | 7 |
| `.75e+1` | 0.75 × 10¹ | 7.5 |

- `e` và `E` tương đương; `1e3` và `1E3` có cùng kiểu `double` và cùng giá trị.
- Sau `e`/`E` **bắt buộc phải có số mũ nguyên viết bằng chữ số thập phân**.
- Dấu `+`/`-` ngay sau `e`/`E` là phần hợp lệ của phần mũ: `1e+3`, `1e-3`.
- `1e`, `1e+`, `1e-` không có đủ phần mũ và không hợp lệ.
- `1e2.5` không hợp lệ dưới dạng một literal; phần số mũ phải là số nguyên.
- Số mũ không làm literal đổi thành `BigDecimal`; `6.02e23` vẫn là `double` nếu không có hậu tố `F`.

**Lưu ý khi đọc log:** Java có thể tự in `1.0E7` thay vì một chuỗi gồm toàn chữ số. Cách `Double.toString`/`Float.toString` chọn biểu diễn chuỗi là chuyện **xuất giá trị**, tách biệt cú pháp literal nguồn `1e7`.

## 5. Hậu tố F/f và D/d quyết định kiểu của literal

Quy tắc chuẩn của ngôn ngữ:

| Hậu tố | Kiểu literal | Ví dụ |
| --- | --- | --- |
| `f` hoặc `F` | `float` | `3.14f`, `5F`, `2e-3F` |
| `d` hoặc `D` | `double` | `3.14d`, `5D`, `2e-3D` |
| Không có hậu tố | `double` | `3.14`, `5e0`, `0x1p1` |

Hậu tố có thể viết hoa/thường. Nên ưu tiên `F` và `D` khi cần nói rõ kiểu; `D` thường có thể bỏ vì `double` đã là mặc định.

### 5.1. Kiểu literal khác kiểu biến nhận

```java
float a = 1.25F;       // float -> float
double b = 1.25;       // double -> double
double c = 1.25F;      // float -> double, widening
double d = 1.25D;      // double -> double
double e = 1;          // int -> double, widening
```

Trong `double c = 1.25F;`, **giá trị được tạo theo độ chính xác `float` trước**, rồi mới chuyển rộng sang `double`. Chuyển rộng không khôi phục những chữ số đã mất trong `float`.

Ví dụ sau **không biên dịch**:

```java
float wrong = 1.25;
```

Vì `1.25` không có hậu tố nên là literal `double`; Java không tự thu hẹp từ `double` sang `float` trong phép gán như vậy. Sửa thành `float correct = 1.25F;`. Có thể ép kiểu tường minh `(float) 1.25` khi phù hợp, nhưng không nên thay thế hậu tố chỉ để làm mất cảnh báo về chuyển hẹp.

Trường hợp tương tự:

```java
float rate = 1e2F;  // hợp lệ, float
double distance = 1e2; // hợp lệ, double
// float bad = 1e2;    // lỗi biên dịch: double -> float
```

### 5.2. Vì sao 0.1F và 0.1 không “giống hệt” nhau?

`0.1F` được làm tròn sang biểu diễn binary32; `0.1` được làm tròn sang binary64. Khi `float` được mở rộng thành `double` để so sánh hoặc gán, số đã làm tròn ở binary32 vẫn giữ giá trị đó. Do đó **một literal có hậu tố khác có thể dẫn đến khác biệt giá trị**, ngay cả khi chúng có cùng các chữ số thập phân trong mã nguồn.

Chỉ dùng `D` khi nó giúp người đọc hoặc một công cụ sinh mã thấy rõ ý định. Đừng chọn `F` chỉ để tiết kiệm bộ nhớ nếu chưa đánh giá sai số cho bài toán.

### 5.3. F/D không phải ký hiệu chuyển kiểu hay đơn vị

`1.5F` là **một literal `float`**; `(float) 1.5` là **một cast** trên literal `double`. `L` biểu thị literal `long` nguyên, không được gắn vào số thực: `1.5L` hoặc `1e3L` không hợp lệ. Java cũng không có hậu tố built-in kiểu `M` để tạo `BigDecimal` như một số ngôn ngữ khác.

## 6. Literal số thực thập lục phân và ký hiệu p/P

Ngoài số thực thập phân, Java còn cho phép **hexadecimal floating-point literal**. Dạng này bắt đầu bằng `0x` hoặc `0X`, dùng chữ số thập lục phân `0–9` và `A–F/a–f` cho **significand** (phần trị số), rồi **bắt buộc có phần mũ `p`/`P`**.

Khác biệt mấu chốt:

- `1.5e2` = 1.5 × **10²** = 150.
- `0x1.8p2` = (1 + 8/16) × **2²** = 6.
- `0x1.fp2` = (1 + 15/16) × **2²** = 7.75.

Dù hệ chữ số phần trị số là **cơ số 16**, phần mũ `p` vẫn là **lũy thừa cơ số 2**. Các chữ số viết sau `p`/`P` là chữ số **thập phân** của số mũ, không phải hexadecimal.

| Literal hex | Diễn giải | Giá trị thập phân |
| --- | --- | --- |
| `0x1p0` | 1 × 2⁰ | 1 |
| `0x1p3` | 1 × 2³ | 8 |
| `0x1.8p1` | (1 + 8/16) × 2¹ | 3 |
| `0x1.fp2` | (1 + 15/16) × 2² | 7.75 |
| `0x.8p0` | (8/16) × 2⁰ | 0.5 |
| `0x1p-3F` | 1 × 2⁻³, kiểu `float` | 0.125 |
| `0X1.0P+2D` | 1 × 2², kiểu `double` | 4 |

Được phép viết `0x1.p3` hoặc `0x1p3` (sau `0x1` có thể có hoặc không có dấu chấm), miễn là có chữ số significand và có `p`/`P` với số mũ hợp lệ.

**Tại sao dùng hex?** Các giá trị nhị phân như `0x1.8p1` có thể mô tả chính xác bit và lũy thừa 2, thuận lợi khi đọc tài liệu IEEE 754, kiểm tra bit hoặc cần giá trị nhị phân xác định. Chẳng hạn `0x1.0p-3` biểu diễn chính xác `0.125` trong binary32/binary64. Dùng số thực thập phân khi ý nghĩa nghiệp vụ theo hệ 10 dễ đọc hơn.

**Phân biệt với số nguyên hex:** `0x10` là literal nguyên `int` bằng 16; `0x10p0` là literal số thực `double` bằng 16. `0x1.8` không hợp lệ như một floating-point literal vì thiếu `p` và số mũ.

## 7. Dấu âm, dấu dương và ký tự gạch dưới

### 7.1. Dấu trước số và dấu trong phần mũ

Trong `-1.25F`, dấu `-` đứng trước literal là **toán tử một ngôi** tác động lên literal dương `1.25F`; bản thân token floating-point literal không bao gồm dấu âm phía trước. Ngược lại, dấu `-` ở `1e-3` hoặc `0x1p-3` **nằm trong phần số mũ** của literal.

Ví dụ:

```java
double negative = -1.5;
double thousandth = 1e-3;
double tinyPowerTwo = 0x1p-3;
double positive = +.5;
```

Các biến trên lần lượt có giá trị `-1.5`, `0.001`, `0.125` và `0.5`. Không đặt dấu `-` ở sau hậu tố: `1.5F-` không tạo literal âm.

### 7.2. Gạch dưới giữa các chữ số

Dấu `_` cho phép nhóm chữ số trong **phần nguyên, phần lẻ, phần mũ**, không thay đổi giá trị số:

```java
double a = 1_234.5_6;
double b = 1.2_5e+1_0;
float c = 0x1.8_0p+1F;
```

Các dạng này hợp lệ. `_` phải ở **giữa các chữ số của cùng một thành phần**. Những kiểu viết sau không hợp lệ:

```java
// double a = _1.0;    // gạch dưới ở đầu
// double b = 1_.0;    // ngay trước dấu chấm
// double c = 1._0;    // ngay sau dấu chấm
// double d = 1e_3;    // ngay sau e
// double e = 1e+_3;   // ngay sau dấu mũ
// float  f = 1.0_F;   // ngay trước hậu tố
// double g = 0x_1p0;  // ngay sau tiền tố 0x
```

Một literal có thể có nhiều dấu `_` liên tiếp **nếu chúng nằm giữa các chữ số**; dù hợp lệ, hãy ưu tiên nhóm rõ ràng, dễ đọc.

## 8. Độ chính xác và việc biên dịch literal

Khi `javac` gặp một literal hợp lệ, nó chuyển các chữ số nguồn thành giá trị khả biểu diễn của kiểu `float` hoặc `double` theo quy tắc của Java. Hai biểu diễn nguồn trông có vẻ cùng lượng toán học không có nghĩa mọi chữ số đều được giữ nguyên trong binary32/binary64.

### 8.1. 0.1 là trường hợp kinh điển

`0.1` trong hệ thập phân không biểu diễn chính xác bằng một số nhị phân hữu hạn. Do đó:

- `double d = 0.1;` tạo giá trị binary64 gần `0.1`.
- `float f = 0.1F;` tạo giá trị binary32 gần `0.1`.
- `double widened = f;` mở rộng **chính giá trị binary32 đã làm tròn**, không làm nó bằng giá trị binary64 của literal `0.1`.

Điều đáng chú ý là `System.out.println(0.1F)` vẫn có thể in `0.1`: thuật toán chuyển floating point sang chuỗi chọn dạng thập phân ngắn thể hiện lại giá trị đó; nó **không chứng minh** giá trị lưu chính xác bằng 1/10.

### 8.2. Hex có thể biểu diễn chính xác các phân số nhị phân

`0x1p-1` đúng bằng `0.5` và `0x1p-3` đúng bằng `0.125`. Các giá trị là bội/lũy thừa 2 phù hợp miền exponent và precision có thể biểu diễn chính xác ở dạng IEEE 754 nhị phân.

Không suy ra mọi literal hex đều không mất precision. Nếu significand có quá nhiều bit hoặc số mũ đưa giá trị ngoài miền biểu diễn, việc làm tròn hoặc lỗi biên dịch vẫn xảy ra.

### 8.3. Dùng BigDecimal khi yêu cầu thập phân chính xác

Nếu nghiệp vụ cần chính xác số thập phân như `0.1`, có thể tạo:

```java
java.math.BigDecimal price = new java.math.BigDecimal("0.1");
```

Đây là **lời gọi constructor từ một `String`**, không phải một BigDecimal literal. Dùng `new BigDecimal(0.1)` mang giá trị nhị phân gần đúng của `double` vào đối tượng, nên không thay thế được cách tạo từ chuỗi nếu yêu cầu đúng `0.1` trong hệ thập phân.

## 9. Ranh giới: overflow, underflow và số đặc biệt

### 9.1. Literal quá lớn hoặc quá nhỏ

Java phân biệt giữa **floating-point literal ngoài miền biểu diễn** và **phép toán runtime sinh giá trị đặc biệt**.

- Một literal **khác 0** quá lớn đến mức chuyển theo rounding thành vô cực gây **lỗi biên dịch**.
- Một literal **khác 0** quá nhỏ đến mức chuyển theo rounding thành `0.0` cũng gây **lỗi biên dịch**.
- Nếu kết quả nhỏ nhưng vẫn là một **subnormal khác 0**, literal vẫn có thể hợp lệ.

Ví dụ sau cố ý **không biên dịch**:

```java
// double tooLarge = 1e400;   // tràn thành Infinity khi chuyển literal
// float tooLargeF = 1e40F;   // tràn miền float
// double tooTiny = 1e-400;   // literal khác 0 nhưng làm tròn thành zero
// float tooTinyF = 1e-50F;   // tương tự với float
```

Các comment trên để minh họa lỗi nếu bỏ dấu `//`; không nên xóa comment rồi ghép với các ví dụ chạy được.

Những giới hạn tham khảo: `Float.MAX_VALUE` là số `float` hữu hạn dương lớn nhất; `Double.MAX_VALUE` là số `double` hữu hạn dương lớn nhất. `Float.MIN_VALUE` và `Double.MIN_VALUE` là **số dương khác 0 nhỏ nhất (subnormal)** của mỗi kiểu, **không phải số âm nhỏ nhất**. `MIN_NORMAL` là số dương normal nhỏ nhất.

Ví dụ `1.4e-45F` là một dạng thập phân làm tròn thành `Float.MIN_VALUE`, còn `0x1p-149F` biểu diễn chính xác cùng giá trị nhỏ nhất này. Với `double`, `0x1p-1074` là giá trị `Double.MIN_VALUE`.

### 9.2. NaN và Infinity không phải literal từ khóa

Java **không** có cú pháp floating-point literal tên `NaN` hay `Infinity`. Sử dụng:

```java
double positiveInfinity = Double.POSITIVE_INFINITY;
double negativeInfinity = Double.NEGATIVE_INFINITY;
double notANumber = Double.NaN;

boolean ok = Double.isNaN(notANumber);
```

Phép toán runtime như `1.0 / 0.0` có thể cho `Infinity` và `0.0 / 0.0` có thể cho `NaN` theo phép toán số thực. Khác với `int` chia cho 0, phép chia số thực với 0 không mặc định ném `ArithmeticException`.

### 9.3. Dấu của số 0

`0.0` và `-0.0` được so sánh bằng `==` là `true`, nhưng IEEE 754 có hai mẫu bit khác nhau. Ví dụ `1.0 / 0.0` tạo dương vô cực, `1.0 / -0.0` tạo âm vô cực. Đây là lý do không nên coi `-0.0` hoàn toàn tương đương `0.0` trong mọi phép tính số thực.

## 10. Ví dụ hoàn chỉnh: đọc literal, phần mũ và hậu tố

Lưu toàn bộ nội dung thành **`FloatingLiterals.java`**:

```java
public class FloatingLiterals {
    public static void main(String[] args) {
        double decimal = 12.5;
        double leadingDot = .75;
        double scientific = 1.2e3;
        float asFloat = 1.25F;
        double hexadecimal = 0x1.8p1;
        float binaryExponent = 0x1p-3F;

        System.out.println(decimal);
        System.out.println(leadingDot);
        System.out.println(scientific);
        System.out.println(asFloat);
        System.out.println(hexadecimal);
        System.out.println(binaryExponent);
        System.out.println(Double.toHexString(hexadecimal));
        System.out.println(0.1F == 0.1);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac --release 17 FloatingLiterals.java
java FloatingLiterals
```

**Stdout chính xác:**

```text
12.5
0.75
1200.0
1.25
3.0
0.125
0x1.8p1
false
```

Giải thích:

1. `12.5` và `.75` dùng cú pháp thập phân; `1.2e3` = 1200.0.
2. `1.25F` có kiểu `float` và được `println` in dưới dạng `1.25`.
3. `0x1.8p1` = (1 + 8/16) × 2 = `3.0`; `0x1p-3F` = 1/8 = `0.125`.
4. `Double.toHexString` tạo chuỗi biểu diễn hex của giá trị `double`; với `3.0`, kết quả `0x1.8p1`.
5. `0.1F == 0.1` là `false` vì toán hạng `float` được chuyển thành `double` để so sánh nhưng vẫn mang sai số làm tròn của binary32; hai toán hạng không chứa cùng giá trị.

## 11. Ví dụ hoàn chỉnh: precision, zero và giá trị đặc biệt

Lưu thành **`LiteralPrecision.java`**:

```java
public class LiteralPrecision {
    public static void main(String[] args) {
        double decimal = 0.1;
        float single = 0.1F;
        double widened = single;

        System.out.println(decimal == widened);
        System.out.println(widened);
        System.out.println(0x1.0p-1 == 0.5);
        System.out.println(Double.doubleToRawLongBits(-0.0)
                == Double.doubleToRawLongBits(0.0));
        System.out.println(Double.isInfinite(1.0 / 0.0));
    }
}
```

Chạy:

```bash
javac --release 17 LiteralPrecision.java
java LiteralPrecision
```

**Stdout chính xác:**

```text
false
0.10000000149011612
true
false
true
```

Diễn giải: chuyển `0.1F` sang `double` hiển thị rõ giá trị binary32 gần đúng; `0x1.0p-1` và `0.5` cùng đúng 1/2; zero dương/âm khác mẫu bit; phép chia `double` dương cho 0.0 tạo vô cực nên `Double.isInfinite` trả về `true`.

## 12. Những ví dụ sai cần nhận ra khi đọc mã

Các dòng sau là **mã cố ý sai** để nhận diện lỗi, không phải đoạn Java độc lập có thể biên dịch:

```java
// double a = 1e;         // phần mũ thiếu chữ số
// double b = 1e+;        // phần mũ thiếu chữ số sau dấu
// double c = 1e2.5;      // mũ không phải số nguyên
// double d = 0x1.8;      // hex float bắt buộc có p/P
// double e = 0x1.8e2;    // e là chữ số hex, không thay cho mũ p
// float  f = 1.25;       // literal double không tự gán vào float
// double g = 1.5L;       // L chỉ dành cho literal nguyên
// double h = 1e3L;       // không thể gắn L sau số thực
// double i = 1.0_M;      // underscore sai chỗ, M không phải hậu tố số thực
// double j = 1e400;      // literal khác 0 tràn thành Infinity
// float  k = 1e-50F;     // literal khác 0 underflow thành zero
```

Một lỗi đọc mã khác: tưởng `2e3F` nghĩa là 2 × 10³ rồi “đổi sang float ở cuối”. Trên thực tế, **toàn bộ token `2e3F`** là literal `float`, và giá trị của nó đã tuân theo quy tắc làm tròn binary32 khi được tạo.

## 13. Bảng quyết định nhanh

| Muốn biểu diễn | Viết | Tránh nhầm |
| --- | --- | --- |
| Số `double` theo thập phân | `2.5` hoặc `2.5D` | `2.5` mặc định đã là `double` |
| Số `float` theo thập phân | `2.5F` | `float x = 2.5;` không hợp lệ |
| Lũy thừa cơ số 10 | `2.5e3` | `e` không phải hằng số Euler |
| Lũy thừa cơ số 10 cho `float` | `2.5e3F` | Phần mũ không thay đổi quy tắc kiểu |
| Biểu diễn nhị phân bằng hex | `0x1.4p2` | Mũ hex dùng `p`, nghĩa là 2² |
| Hex số thực kiểu `float` | `0x1p-3F` | `0x1p-3` không hậu tố là `double` |
| Số nguyên hệ 16 | `0x10` | Đây là `int`, không phải hex float |
| Số thập phân tiền tệ chính xác | `new BigDecimal("2.50")` | Java không có `BigDecimal` literal |
| Vô cực/NaN | `Double.POSITIVE_INFINITY`, `Double.NaN` | `Infinity`/`NaN` không phải token literal |

## 14. Bài luyện tập và đáp án giải thích

**Bài 1 — Xác định kiểu.** Với `3`, `3F`, `3D`, `3.0`, `3e0`, `0x3p0`, `0x3`, hãy tự chia thành `int`/`float`/`double` trước khi xem đáp án.

Đáp án: `3` và `0x3` là `int`; `3F` là `float`; `3D`, `3.0`, `3e0` và `0x3p0` là `double`.

**Bài 2 — Tính giá trị mũ.** Tính bằng tay `1.5e2`, `1.5e-2`, `0x1.8p2` và `0x1p-4`.

Đáp án lần lượt: `150.0`, `0.015`, `6.0` và `0.0625`. Hai dạng đầu nhân lũy thừa 10; hai dạng sau nhân lũy thừa 2.

**Bài 3 — Tìm lỗi.** Các đoạn `float x = 2.5;`, `double y = 1e;`, `double z = 0x1.8;` lỗi ở đâu?

Đáp án: `2.5` mặc định là `double` và không thể tự chuyển hẹp sang `float`; `1e` thiếu số mũ; hex float `0x1.8` thiếu mũ `p`. Cách sửa tương ứng: `2.5F`, `1e0` và `0x1.8p0`.

**Bài 4 — Phân biệt precision.** Giải thích vì sao `double d = 0.1F;` và `double e = 0.1;` có thể khác nhau mặc dù hai biến đều có kiểu `double`.

Đáp án: kiểu **literal** quyết định bước làm tròn đầu tiên. `0.1F` đã làm tròn theo binary32 rồi mới mở rộng; `0.1` làm tròn trực tiếp theo binary64.

**Bài 5 — Đọc dấu.** Trong `-0x1p-3F`, có bao nhiêu dấu âm và vai trò từng dấu?

Đáp án: hai dấu. Dấu đầu là toán tử âm một ngôi tác động lên literal; dấu sau `p` là dấu của số mũ `-3`. Kết quả là `-0.125F`.

**Bài 6 — Phân biệt lỗi lúc biên dịch và runtime.** Tại sao `double x = 1e400;` lỗi biên dịch trong khi `double x = 1.0 / 0.0;` có thể tạo vô cực lúc chạy?

Đáp án: một **literal khác 0** được làm tròn thành vô cực là lỗi biên dịch theo JLS, còn phép chia floating point có quy tắc tạo giá trị IEEE 754 đặc biệt.

## 15. Checklist đã nắm vững

- [ ] Tôi phân biệt `1`, `1.0`, `1e0`, `1F` và `1D` về loại literal.
- [ ] Tôi giải thích được `e/E` nhân lũy thừa 10, `p/P` nhân lũy thừa 2.
- [ ] Tôi tự đổi được `0x1.8p1` sang `3.0` và hiểu vì sao hex float luôn cần phần mũ.
- [ ] Tôi biết literal không hậu tố là `double`; `F/f` là `float`; `D/d` xác nhận `double`.
- [ ] Tôi giải thích được vì sao `float x = 1.2;` lỗi dù giá trị có vẻ nhỏ.
- [ ] Tôi viết được underscore đúng chỗ, phân biệt dấu âm một ngôi với dấu của số mũ.
- [ ] Tôi không nhầm cách hiển thị `0.1F` với độ chính xác lưu trữ.
- [ ] Tôi biết literal quá lớn/quá nhỏ có thể lỗi **biên dịch**, còn `NaN`/`Infinity` là hằng số/API hoặc kết quả phép toán.
- [ ] Tôi chạy được hai ví dụ hoàn chỉnh và tự xác minh stdout.

**Tài liệu tiêu chuẩn để đối chiếu:** [Java Language Specification 17, `3.10.2 Floating-Point Literals](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.2); [JLS 17, `4.2.3 Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3); [Java SE 17, Float API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html); [Java SE 17, Double API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-literals-007">Literal dấu phẩy động thập lục phân</a>

<details>
<summary>Xem chi tiết</summary>

# Literal dấu phẩy động thập lục phân trong Java

> **Java core – numbers / Literal và cơ số**  
> Mục tiêu: đọc và tự viết hexadecimal floating-point literals, tính giá trị bằng tay, phân biệt chúng với literal số nguyên hệ 16 và số thực thập phân, hiểu lợi ích của cách viết theo lũy thừa của 2 khi kiểm tra `float`/`double`.

## 1. Khái niệm và động cơ

Java có **literal dấu phẩy động thập lục phân** (*hexadecimal floating-point literal*): viết một giá trị `float` hoặc `double` trong mã nguồn bằng **phần trị hệ 16** và **số mũ hệ 2**. Ví dụ:

```java
double a = 0x1.8p1;   // 3.0
double b = 0x1.0p-2;  // 0.25
float  c = 0x1.Ap2f;  // 6.5f
```

Điểm quan trọng nằm ở chữ **`p`**: nó tách phần trị (*significand*) khỏi số mũ nhị phân. Mặc dù phần trị viết bằng chữ số hệ 16, số mũ sau `p` biểu thị **lũy thừa của 2**, không phải lũy thừa của 16. Cú pháp này được Java hỗ trợ từ Java 5 và vẫn dùng được với Java 17+.

Vì `float` và `double` lưu số theo dạng dấu phẩy động **nhị phân** (IEEE 754), cách ghi hex này đặc biệt hữu ích để:

- mô tả một giá trị nhị phân chính xác và có thể tái lập trong mã nguồn;
- viết các giới hạn, hằng số dạng `2^k` và các giá trị sát nhau ở mức bit;
- kiểm tra phép làm tròn và sự khác nhau giữa giá trị `double` với biểu diễn thập phân trên màn hình;
- đọc kết quả của `Double.toHexString` và `Float.toHexString` khi gỡ lỗi tính toán.

Đây vẫn là literal cho **`float`/`double`**; không liên quan đến bù hai của số nguyên âm, không phải cú pháp `BigDecimal` và không tự tạo một loại số chính xác vô hạn.

## 2. Cú pháp: `0x...p...` và hậu tố kiểu

Một dạng tổng quát dễ nhớ:

```text
[+|-]  0x<hex-significand> p<decimal-exponent> [f|F|d|D]
       └── số hệ 16 ────┘   └──── số mũ 10 chữ số, tính theo cơ số 2 ────┘

Ví dụ: 0x1.8p+1
       |  |  | |
       |  |  | +-- số mũ +1, nghĩa là nhân 2^1
       |  |  +---- p: dấu mở đầu phần số mũ nhị phân
       |  +------- 1.8: phần trị hex, tương đương 1 + 8/16
       +---------- 0x: nhận diện hệ 16
```

Các thành phần:

1. **Tiền tố** `0x` hoặc `0X`: báo hiệu phần trị hệ 16.
2. **Phần trị (*significand*)** gồm chữ số `0–9` và `A–F`/`a–f`; có thể có dấu chấm `.` làm dấu phân cách phần nguyên và phần lẻ trong hệ 16. Cần ít nhất một chữ số hex.
3. **`p` hoặc `P` là bắt buộc** trong literal floating-point hex; ngay sau nó là số mũ thập phân có thể có dấu `+`/`-`. Các chữ số của số mũ phải là `0–9`.
4. **Hậu tố** `f`/`F` làm literal mang kiểu `float`; `d`/`D` hoặc không có hậu tố làm literal mang kiểu `double`.
5. Dấu `-` đầu biểu thức là **toán tử âm đơn ngôi**, không phải một phần chữ số của literal. Điều này ít khi ảnh hưởng đến sử dụng thông thường nhưng quan trọng trong cách Java phân tích cú pháp.

Các cách viết hợp lệ:

```java
double one       = 0x1p0;        // 1.0: không cần dấu chấm
double half      = 0x.8p0;       // 0.5: không cần chữ số trước dấu chấm
double two       = 0x1.p1;       // 2.0: không cần chữ số sau dấu chấm
double six       = 0X1.8P+2D;    // 6.0: viết hoa cũng hợp lệ
float eighth     = 0x1p-3F;     // 0.125f
double sixteen   = 0x1_0p0;     // 16.0: dấu _ nằm giữa các chữ số
```

Chữ `E`/`e` ở phần trị **là chữ số hệ 16 có giá trị 14**, không phải ký hiệu số mũ. Trong `0x1.ep3`, `e` thuộc phần trị, còn `p3` mới là số mũ. Tương tự, hậu tố `F`/`f` chỉ được hiểu là hậu tố kiểu khi nằm **sau toàn bộ số mũ**; `f` nằm trước `p` là chữ số hệ 16 có giá trị 15.

## 3. Công thức tính giá trị

Viết phần trị hệ 16 là `H` và số mũ sau `p` là số nguyên `E`. Giá trị toán học trước khi làm tròn về `float`/`double` là:

\[
\boxed{\text{Giá trị} = H_{16}\times 2^E}
\]

Ví dụ `0x1.8p1`:

- `0x1.8` = `1 + 8/16 = 1.5` (trong hệ thập phân).
- `p1` nghĩa là nhân `2^1 = 2`.
- Kết quả `1.5 × 2 = 3.0`.

Với phần trị có nhiều chữ số sau dấu chấm, chữ số ngay sau dấu chấm mang trọng số `16^(-1)`, chữ số thứ hai mang trọng số `16^(-2)`, tiếp tục như vậy.

Ví dụ `0xA.Cp-2`:

\[
0xA.C=10+\frac{12}{16}=10.75
\]

\[
0xA.Cp-2=10.75\times 2^{-2}=10.75/4=2.6875
\]

Bảng đối chiếu:

| Literal | Phần trị đổi sang thập phân | Hệ số `2^E` | Giá trị |
| --- | ---: | ---: | ---: |
| `0x1p0` | 1 | 1 | 1.0 |
| `0x1.8p1` | 1.5 | 2 | 3.0 |
| `0x1.8p-1` | 1.5 | 0.5 | 0.75 |
| `0x1p-4` | 1 | 1/16 | 0.0625 |
| `0xFp-4` | 15 | 1/16 | 0.9375 |
| `0x1.Ap2` | 1.625 | 4 | 6.5 |
| `0xA.Cp-2` | 10.75 | 1/4 | 2.6875 |
| `0x1.2p3` | 1.125 | 8 | 9.0 |
| `0x1_0.8p+2` | 16.5 | 4 | 66.0 |

**Không nhầm với số mũ của cơ số 16.** Nếu `p1` nghĩa là nhân 16, `0x1.8p1` sẽ thành 24; Java quy định nó bằng **3** vì `p1` là nhân 2.

## 4. Tại sao hệ 16 lại đi kèm lũy thừa của 2?

Một chữ số hex tương ứng đúng **4 bit nhị phân**:

```text
hex:   1   .   8
bits:  0001 . 1000
           = 1 + 1/2 = 1.5

0x1.8p1:
1.1 (nhị phân) × 2^1 = 11.0 (nhị phân) = 3.0
```

Ví dụ khác: số `10.625` trong hệ 10 là `1010.101` trong hệ 2. Ta có thể viết:

```text
10.625 (thập phân)
= 1010.101 (nhị phân)
= 0xA.Ap0  (A + A/16 = 10 + 10/16)
= 0x1.54p3 (1 + 5/16 + 4/256) × 8
```

Cả `0xA.Ap0` và `0x1.54p3` cùng biểu diễn số `10.625`. Dạng `0x1.54p3` có phần trị bắt đầu bằng `1`, gần với cách hiển thị **chuẩn hóa** mà API Java thường tạo cho số dấu phẩy động hữu hạn bình thường (*normal*).

Đối với các số có thể biểu diễn bằng hữu hạn chữ số nhị phân, hệ 16 rất thuận tiện: ví dụ `1/8 = 2^(-3) = 0x1p-3`. Ngược lại, `0.1` trong hệ 10 không thể biểu diễn bằng hữu hạn chữ số nhị phân; muốn ghi **giá trị `double` gần 0.1**, cần một mẫu nhị phân đã được làm tròn.

## 5. Phân biệt literal số nguyên hex và literal floating-point hex

`0x...` tự nó **chưa đủ** để là literal dấu phẩy động. Phần `pE` tạo khác biệt:

| Mã nguồn | Kiểu của literal | Giá trị |
| --- | --- | --- |
| `0x10` | `int` | 16 |
| `0x10L` | `long` | 16 |
| `0x10p0` | `double` | 16.0 |
| `0x10p0f` | `float` | 16.0f |
| `0x1Ap0` | `double` | 26.0 |
| `0x1e3` | `int` | 483 (`0x1E3`) |
| `0x1e3p0` | `double` | 483.0 |
| `0x1.8p1` | `double` | 3.0 |

Điểm tinh tế: `double value = 0x10;` có thể biên dịch vì Java chuyển `int 16` sang `double 16.0` khi gán. **Bản thân `0x10` vẫn là integer literal**. Còn `0x10p0` từ đầu đã là floating-point literal.

Hai cú pháp sai thường gặp:

```java
// KHÔNG biên dịch: phần trị có dấu chấm nhưng thiếu số mũ p...
// double wrong1 = 0x1.8;

// KHÔNG biên dịch: e không thay thế được p trong literal hex
// double wrong2 = 0x1.8e2;

// KHÔNG biên dịch: gán double literal sang float không qua phép chuyển kiểu
// float wrong3 = 0x1.8p1;
```

Cách đúng là `0x1.8p0` cho `1.5`; `0x1.8p1` cho `3.0`; và `0x1.8p1f` cho `float 3.0f`. Khi muốn `float` hãy ghi hậu tố `f`/`F` hoặc thực hiện chuyển kiểu có chủ đích, chú ý khả năng làm tròn.

## 6. Hậu tố, dấu gạch dưới và số mũ âm

Cú pháp hậu tố có cùng ý nghĩa như với literal dấu phẩy động thập phân:

```java
double d1 = 0x1.4p2;   // 5.0, double mặc định
double d2 = 0x1.4p2D;  // 5.0, ghi rõ double
float f1 = 0x1.4p2f;   // 5.0f, float
float f2 = 0X1.4P+2F;  // 5.0f, chữ hoa hợp lệ
```

Số mũ `p-4` có nghĩa nhân với `2^(-4)`, **không** phải “phần trị âm”; dấu của toàn giá trị vẫn phụ thuộc dấu ở đầu biểu thức.

```java
double x = 0x1p-4;   // +0.0625
double y = -0x1p-4;  // -0.0625
```

Dấu `_` được dùng để nhóm chữ số như trong các numeric literal khác, nhưng phải đặt **giữa các chữ số** của phần trị hoặc của số mũ, không đặt ở vị trí tùy ý.

```java
double valid = 0x1_0.8p1_0; // 16.5 × 2^10 = 16896.0

// SAI cú pháp: dấu _ không được đặt ngay sau 0x, bên cạnh
// dấu chấm, ngay sau p hoặc ngay trước hậu tố:
// double a = 0x_1.8p0;
// double b = 0x1_.8p0;
// double c = 0x1.8p_2;
// float  d = 0x1.8p0_f;
```

Đối với cả hai dạng chữ hoa/thường, chữ số của phần số mũ vẫn là **chữ số thập phân**. Ví dụ `0x1p10` mang số mũ mười (nhân `2^10`), **không** phải số mũ 16 vì `10` trông giống hai chữ số hex.

## 7. Quan hệ với IEEE 754 và khả năng biểu diễn chính xác

Một giá trị `double` bình thường hữu hạn theo IEEE 754 có 1 bit dấu, 11 bit số mũ và 52 bit phần phân số (tổng 64 bit). Đối với giá trị **normal** khác 0, phần trị hiệu dụng là `1.<fraction bits>` trong hệ 2 (bit 1 dẫn đầu không lưu tường minh). `float` có tổng 32 bit: 1 bit dấu, 8 bit số mũ và 23 bit phần phân số.

Đây là lý do literal hex thuận tiện: chuỗi chữ số hex mô tả trực tiếp các nhóm bit của phần trị nhị phân. Ví dụ, trong `double`:

```java
double exact = 0x1.8p1;                  // 3.0, đúng tuyệt đối
double oneTenth = 0x1.999999999999ap-4; // giá trị double gần 0.1
```

Giá trị `oneTenth` có cách hiển thị thập phân ngắn thường gặp là `0.1`, nhưng **giá trị nhị phân lưu bên trong không bằng chính xác 1/10**. Cú pháp literal hex ở đây mô tả rõ số `double` đã được làm tròn.

Đừng hiểu rằng “mọi literal hex tự động chính xác tuyệt đối”. Nếu phần trị cung cấp nhiều bit hơn độ chính xác `float`/`double`, hoặc giá trị quá sát một điểm giữa hai số có thể biểu diễn, compiler vẫn áp dụng quy tắc làm tròn của kiểu đích. Ưu điểm của hex là cách ghi tương thích trực tiếp với cơ số 2, đặc biệt khi ghi **giá trị vốn đã biểu diễn được** trong `double`/`float`.

Các giới hạn điển hình có thể ghi bằng hex:

```java
double maxFinite      = 0x1.fffffffffffffp1023; // Double.MAX_VALUE
double minNormal      = 0x1.0p-1022;           // Double.MIN_NORMAL
double minSubnormal   = 0x1p-1074;            // Double.MIN_VALUE

float maxFiniteFloat  = 0x1.fffffep127f;      // Float.MAX_VALUE
float minNormalFloat  = 0x1.0p-126f;          // Float.MIN_NORMAL
float minSubFloat     = 0x1p-149f;            // Float.MIN_VALUE
```

**Phân biệt `MIN_VALUE` và số âm nhỏ nhất:** trong `Double` và `Float`, `MIN_VALUE` là **số dương hữu hạn nhỏ nhất khác 0**, tức số *subnormal*; nó không phải giá trị âm lớn về độ lớn. Số âm hữu hạn có độ lớn lớn nhất là `-Double.MAX_VALUE` hoặc `-Float.MAX_VALUE`.

Literal hữu hạn cũng không thể chỉ cần tăng số mũ vô hạn: các literal khác 0 mà phép làm tròn khiến vượt thành vô cực hoặc thành 0 sẽ bị Java báo lỗi biên dịch. Khi cần tạo `NaN` hoặc các giá trị vô cực, dùng các hằng như `Double.NaN`, `Double.POSITIVE_INFINITY`, `Float.NEGATIVE_INFINITY`; đó không phải các hexadecimal floating-point literals.

## 8. `Double.toHexString`, `Float.toHexString` và parse

Java có API để **xuất dạng hex của giá trị đã lưu**:

```java
Double.toHexString(3.0);        // "0x1.8p1"
Float.toHexString(3.0f);        // "0x1.8p1"
Double.toHexString(0.1);        // "0x1.999999999999ap-4"
Double.toHexString(-0.0);       // "-0x0.0p0"
```

Không phải mọi giá trị sẽ hiện đúng như dạng bạn gõ. Ví dụ `0xA.Ap0` và `0x1.54p3` đều biểu diễn `10.625`; `Double.toHexString(10.625)` trả dạng chuẩn hóa `"0x1.54p3"`.

- `Double.toHexString` và `Float.toHexString` trả **chuỗi ký tự**, hữu ích khi đối chiếu các giá trị nhị phân.
- `Double.parseDouble`/`Double.valueOf` và `Float.parseFloat`/`Float.valueOf` có thể đọc dạng chuỗi floating-point hex hợp lệ, ví dụ `"0x1.8p1"`.
- Khi kiểm tra bit thô, `Double.doubleToRawLongBits` và `Float.floatToRawIntBits` có vai trò khác: chúng trả **các bit mã hóa IEEE 754** dưới dạng `long`/`int`. Các bit thô này bao gồm cả bit dấu, số mũ thiên lệch và fraction; không phải chính chuỗi hexadecimal literal của giá trị.

Thử nghiệm:

```java
String representation = Double.toHexString(0.1);
double parsed = Double.parseDouble(representation);
System.out.println(representation);                   // 0x1.999999999999ap-4
System.out.println(parsed == 0.1);                    // true
```

Phép so sánh trên có ý nghĩa: sau khi phân tích chuỗi hex, `parsed` và literal decimal `0.1` tạo cùng một **giá trị `double` đã được làm tròn**. Nó **không** chứng minh `double` lưu phân số toán học 1/10 một cách tuyệt đối chính xác.

Các trường hợp `NaN` và vô cực xuất thành chuỗi đặc biệt như `"NaN"`, `"Infinity"` (không phải `0x...p...`); dấu `−0.0` được giữ khi xuất hex.

## 9. Chương trình Java 17+ chạy độc lập

Tạo file **`HexFloatLiteralDemo.java`**:

```java
public class HexFloatLiteralDemo {
    public static void main(String[] args) {
        double a = 0x1.8p1;
        double b = 0xA.Cp-2;
        float c = 0x1.Ap2f;

        int integerHex = 0x10;
        double floatingHex = 0x10p0;
        double normal = 0xA.Ap0;

        System.out.println("0x1.8p1 = " + a);
        System.out.println("0xA.Cp-2 = " + b);
        System.out.println("0x1.Ap2f = " + c);
        System.out.println("integer 0x10 = " + integerHex);
        System.out.println("floating 0x10p0 = " + floatingHex);
        System.out.println("toHexString(10.625) = "
                + Double.toHexString(normal));

        double decimal = 0.1;
        String exactHex = Double.toHexString(decimal);
        double parsed = Double.parseDouble(exactHex);

        System.out.println("decimal double 0.1 as hex = " + exactHex);
        System.out.println("parsed equals original = " + (parsed == decimal));
        System.out.println("negative zero as hex = "
                + Double.toHexString(-0.0));

        System.out.println("Double.MIN_VALUE as hex = "
                + Double.toHexString(Double.MIN_VALUE));
        System.out.println("Float.MIN_VALUE as hex = "
                + Float.toHexString(Float.MIN_VALUE));
    }
}
```

Từ thư mục chứa file:

```bash
javac HexFloatLiteralDemo.java
java HexFloatLiteralDemo
```

**Kết quả dự kiến với Java 17+:**

```text
0x1.8p1 = 3.0
0xA.Cp-2 = 2.6875
0x1.Ap2f = 6.5
integer 0x10 = 16
floating 0x10p0 = 16.0
toHexString(10.625) = 0x1.54p3
decimal double 0.1 as hex = 0x1.999999999999ap-4
parsed equals original = true
negative zero as hex = -0x0.0p0
Double.MIN_VALUE as hex = 0x0.0000000000001p-1022
Float.MIN_VALUE as hex = 0x0.000002p-126
```

Phân tích kết quả:

1. Ba literal đầu biểu diễn lần lượt `3`, `2.6875` và `6.5`; hậu tố `f` của `c` lựa chọn kiểu `float`.
2. `0x10` và `0x10p0` có cùng độ lớn, nhưng kiểu literal khác nhau.
3. `Double.toHexString(10.625)` đưa phần trị về dạng chuẩn hóa `1.54 × 2^3`.
4. Chuỗi cho `double 0.1` thể hiện giá trị nhị phân gần nhất, không phải phân số 1/10 chính xác.
5. `Double.MIN_VALUE`, `Float.MIN_VALUE` là các số **subnormal**. Cách xuất hex của API có phần trị dạng `0.000...` và số mũ tối thiểu cho số bình thường, nên **khác hình thức** literal ngắn `0x1p-1074` hoặc `0x1p-149f` nhưng cùng giá trị. Định dạng này không có nghĩa các hằng ở trên sai.

## 10. Quy trình đọc và chuyển một giá trị sang hex floating-point

Giả sử cần biểu diễn giá trị toán học `13.25` bằng cách chắc chắn mô tả theo cơ số hai.

**Bước 1: Đổi phần nguyên và phần lẻ sang nhị phân.**

- `13 = 1101₂`.
- `0.25 = 0.01₂`.
- `13.25 = 1101.01₂`.

**Bước 2: Nhóm bit theo bốn bit để lấy chữ số hex.**

`1101.0100₂ = D.4₁₆`. Vậy biểu diễn chưa chuẩn hóa là `0xD.4p0`.

**Bước 3: Chuyển về phần trị gần dạng chuẩn hóa `1.x × 2^E` (nếu muốn).**

`1101.01₂ = 1.10101₂ × 2³`. Nhóm fraction thành `1010 1000...₂`, tức `A8₁₆`. Ta có:

```text
13.25₁₀ = 1101.01₂
         = 0xD.4p0
         = 0x1.A8p3
         = (1 + 10/16 + 8/256) × 8
         = (1.65625) × 8
         = 13.25
```

`Double.toHexString(13.25)` trả `"0x1.a8p3"`. Trong dạng chuẩn hóa, số mũ `3` chỉ số vị trí dịch dấu phẩy nhị phân, **không** phụ thuộc bao nhiêu chữ số hex của phần trị.

**Bước 4: Chọn kiểu và hậu tố.** Nếu API cần `double`, `0x1.A8p3` đủ. Nếu cần `float`, viết `0x1.A8p3f`. Vì 13.25 có biểu diễn nhị phân hữu hạn và đủ ngắn, cả hai biểu diễn được chính xác.

## 11. Dùng literal hex ở đâu, tránh ở đâu?

**Nên dùng** khi đề bài gắn với bit, độ chính xác nhị phân hoặc các hằng đặc biệt:

- kiểm thử phép toán bit ở mức `float`/`double` và so sánh với giá trị IEEE 754 dự kiến;
- kiểm thử ranh giới trên/dưới giữa normal và subnormal;
- tái tạo một giá trị `double` đã nhìn thấy từ `Double.toHexString`;
- biểu diễn các số dễ viết bằng lũy thừa 2 như `0x1p-10` (1/1024) hoặc `0x1p20` (1,048,576).

**Ưu tiên literal thập phân** khi giá trị mang ý nghĩa nghiệp vụ theo hệ 10, như cân nặng hiển thị, đơn giá và tỉ lệ phần trăm trong tài liệu mà người đọc mong đợi số thập phân. Nếu bài toán yêu cầu **tính tiền thập phân chính xác và quy tắc làm tròn**, dùng `BigDecimal` từ chuỗi (ví dụ `new BigDecimal("0.1")`) thay vì cố gắng giải quyết bằng `double` viết hex. Literal hex giúp biểu diễn chính xác **một giá trị nhị phân của float/double**, không biến IEEE 754 thành số thập phân chính xác vô hạn.

**Tính dễ đọc:** tên hằng và lời giải thích lý do lựa chọn giúp người đọc hiểu tại sao viết `0x1.0p-1022` thay cho một chuỗi thập phân dài. Với số thông dụng như 3.0, cú pháp `3.0` thường đơn giản hơn `0x1.8p1` nếu không nghiên cứu biểu diễn bit.

## 12. Lỗi thường gặp

| Sai lầm | Hậu quả / cách sửa |
| --- | --- |
| Nghĩ `p3` là nhân `16^3` | Phải nhân `2^3` |
| Bỏ `p` vì đã có `0x` | Nếu phần trị có dấu chấm, literal hex floating-point phải có số mũ `p` |
| Dùng `e` làm số mũ hex | Trong phần trị hex, `e` là chữ số 14; phải dùng `p` hoặc `P` |
| Nghĩ `0x1e3` là số thực `1×10³` | Đó là integer literal hệ 16 có giá trị 483 |
| Gán `0x1p0` cho `float` mà không có hậu tố | Literal mặc định `double`; viết `0x1p0f` |
| Dùng dấu `_` cạnh `0x`, `.`, `p`, `f` | Sai quy tắc đặt underscore; chỉ nhóm chữ số |
| Tưởng literal hex không bao giờ làm tròn | Nếu quá nhiều bit hoặc vượt độ chính xác kiểu đích vẫn có làm tròn |
| Tưởng `0x1.999...p-4` là giá trị toán học 1/10 chính xác | Đây là `double` nhị phân gần 0.1 |
| Nhầm `Double.MIN_VALUE` là số âm lớn nhất | Nó là số dương subnormal nhỏ nhất khác 0 |
| Nhầm `Double.toHexString` với raw bits | Chuỗi hex là dạng số `significand × 2^exponent`, không phải 16 chữ số hex của toàn bộ 64 bit |

## 13. Bài tập tự kiểm tra và đáp án

**Bài 1.** Tính `0x1.Cp2` bằng tay.  
**Bài 2.** Phân biệt giá trị và loại literal của `0x10`, `0x10p1` và `0x10p1f`.  
**Bài 3.** Biểu diễn `0.375` thành hexadecimal floating-point literal.  
**Bài 4.** `0x1ep1` có phải giá trị `1 × 10¹` không? Tính kết quả.  
**Bài 5.** `Double.toHexString(0.1)` trả `0x1.999999999999ap-4`. Điều này chứng minh và không chứng minh điều gì?  
**Bài 6.** Viết literal `float` cho `5.5` và literal `double` cho `2^20`.

**Lời giải:**

1. `C₁₆ = 12`, nên `0x1.C = 1+12/16 = 1.75`; nhân `2²=4` cho `7.0`.
2. `0x10` là `int 16`; `0x10p1` là `double 32.0`; `0x10p1f` là `float 32.0f`.
3. `0.375 = 3/8 = 0.011₂ = 0x0.6p0`; cũng có thể viết `0x1.8p-2`.
4. Không. `e` là chữ số 14 nên `0x1e = 30`; `p1` nhân 2, kết quả **60.0**.
5. Nó cho biết chính xác **giá trị nhị phân đã được lưu** cho literal `double 0.1`. Nó không chứng minh rằng `double` lưu chính xác phân số toán học `1/10`.
6. `5.5 = 0x1.6p2f` vì `1.375 × 4 = 5.5`; `2^20 = 0x1p20` (kiểu `double`).

**Ghi nhớ:** Chữ số **hex** ở phần trị, chữ **`p`** đi với số mũ **cơ số 2**, hậu tố **`f`/`F`** để chọn `float` và mặc định là **`double`**. Cách viết này đặc biệt mạnh khi muốn hiểu hoặc tái tạo các giá trị dấu phẩy động nhị phân một cách rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-literals-008">Dấu âm là toán tử chứ không phải một phần của literal</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu âm là toán tử chứ không phải một phần của literal

## 1. Phân biệt literal với dấu âm trong mã nguồn Java

Khi viết `-42`, người đọc thường gọi cả cụm là “literal số âm”. Đây là cách nói thuận tiện trong đời thường, nhưng **không chính xác ở cấp độ cú pháp Java**:

- `42` là một **integer literal** (literal số nguyên) có giá trị 42.
- `-` là **toán tử âm một ngôi** (*unary minus operator*).
- `-42` là **biểu thức** áp dụng toán tử `-` lên literal `42`; kết quả của biểu thức bằng −42.

Điểm quan trọng là **dấu trừ nằm ngoài token literal**. Khi Java phân tích mã nguồn, phần chữ số của số và toán tử `-` được nhận diện riêng; sau đó quy tắc biểu thức quyết định toán tử áp dụng vào toán hạng nào.

Ví dụ:

~~~java
int positive = 42;    // 42 là decimal integer literal
int negative = -42;   // toán tử - áp dụng lên literal 42
int difference = 10 - 3; // phép trừ hai ngôi, kết quả 7
int another = 10 - -3;   // trừ một số âm, kết quả 13
~~~

Đây là **đoạn minh họa**; muốn chạy độc lập phải đặt trong class và method thích hợp. Cùng một ký hiệu `-` có thể đảm nhiệm **hai vai trò**:

| Dạng | Vai trò `-` | Diễn giải |
|---|---|---|
| `-42` | Toán tử âm **một ngôi** | Lấy giá trị đối của 42 |
| `-value` | Toán tử âm **một ngôi** | Lấy giá trị đối của biến/biểu thức `value` |
| `left - right` | Phép trừ **hai ngôi** | Lấy `left` trừ `right` |
| `10 - -3` | Một phép trừ hai ngôi và một phép âm một ngôi | `10 - (-3) = 13` |
| `-(a + b)` | Phép âm một ngôi áp dụng lên biểu thức trong ngoặc | Lấy số đối của tổng |

Không cần nói “literal âm” để giải thích đúng chương trình: **literal có chữ số, còn dấu âm là toán tử trong biểu thức**.

## 2. Java tách token và tạo biểu thức như thế nào?

Quá trình hiểu `int x = -123;` có thể hình dung như sau:

1. **Phân tích từ vựng (lexical analysis):** nhận ra `int`, `x`, `=`, toán tử `-`, literal `123` và dấu `;`.
2. **Phân tích cú pháp (parsing):** xác định `-123` là một *unary expression*, khác với `x - 123` là một *binary expression*.
3. **Kiểm tra kiểu/đánh giá:** tìm kiểu toán hạng, thực hiện phép âm và gán kết quả hợp lệ vào biến `int`.

Literal số nguyên trong Java có nhiều cách viết: thập phân như `123`, nhị phân như `0b1111011`, bát phân như `0173`, hoặc thập lục phân như `0x7B`. **Dấu `-` đứng trước biểu thức, không thuộc cách ghi cơ số**:

~~~java
int decimal = -123;
int binary = -0b1111011;
int octal = -0173;
int hexadecimal = -0x7B;
~~~

Bốn biến trên đều có giá trị số nguyên **−123**, dù bốn literal không dấu dùng các cơ số khác nhau.

### Có thể dùng dấu cộng một ngôi

Java còn hỗ trợ toán tử `+` một ngôi. Ví dụ `+42` áp dụng *unary plus* lên literal `42` và vẫn cho giá trị 42; dấu `+` cũng **không thuộc literal**. Toán tử `+` một ngôi vẫn thực hiện *unary numeric promotion*, giống như toán tử âm một ngôi; vì thế không nên giả định nó giữ nguyên kiểu `byte`, `short` hay `char` trong mọi biểu thức.

### Toán tử âm và dấu trừ có thứ tự ưu tiên khác nhau

`-number` là toán tử một ngôi; `left - right` là phép trừ hai ngôi. Toán tử một ngôi liên kết chặt hơn các phép cộng/trừ hai ngôi trong biểu thức thông thường:

~~~java
int a = 2;
int b = 3;
int first = -a * b;      // (-a) * b, kết quả -6
int second = a - -b;     // a - (-b), kết quả 5
int third = -(a + b);    // -(2 + 3), kết quả -5
~~~

Đây là **đoạn minh họa**, không phải class độc lập. Ngoặc giúp tránh hiểu lầm, đặc biệt ở những biểu thức nhiều toán tử. Java **không có toán tử lũy thừa `^`**: ký hiệu `^` là XOR bit, nên `-2 ^ 2` không có nghĩa “âm hai bình phương”.

## 3. Bài toán ranh giới: `Integer.MIN_VALUE`

Một câu hỏi thú vị: `int` nhận giá trị từ **−2.147.483.648** đến **2.147.483.647**, vậy mã `-2147483648` được biên dịch thế nào nếu literal thập phân không có dấu `2147483648` vượt giá trị dương tối đa?

Java có **quy tắc đặc biệt trong cú pháp literal số nguyên thập phân**:

- Literal thập phân `2147483648` được **cho phép đúng trong ngữ cảnh làm toán hạng của toán tử âm một ngôi**.
- Do đó, biểu thức `-2147483648` hợp lệ và có giá trị `Integer.MIN_VALUE`.
- Literal `2147483648` **đứng riêng** là lỗi biên dịch; không thể gán nó vào `long` rồi kỳ vọng Java tự xử lý vì literal không có hậu tố `L`.
- Nếu cần số dương 2.147.483.648, viết `2147483648L` để đó là literal `long`.

Ví dụ hợp lệ:

~~~java
int min = -2147483648;
long aboveIntMax = 2147483648L;
int viaConstant = Integer.MIN_VALUE;
~~~

Ví dụ sau **cố ý không biên dịch**; chỉ minh họa lỗi và **không** nên đặt vào chương trình chạy:

~~~java
int invalid = 2147483648; // literal decimal int ngoài phạm vi ở ngữ cảnh này
long alsoInvalid = 2147483648; // thiếu hậu tố L cho literal
~~~

Quy tắc tương tự áp dụng cho `long` ở biên âm:

- `long min = -9223372036854775808L;` **hợp lệ**, bằng `Long.MIN_VALUE`.
- Literal thập phân `9223372036854775808L` chỉ được dùng trong ngữ cảnh toán hạng của toán tử âm một ngôi; đứng riêng là lỗi.
- `long beyond = 9223372036854775808L;` **không hợp lệ**. Nếu cần số nguyên dương lớn hơn `Long.MAX_VALUE`, dùng `BigInteger`.

Một chi tiết cần nhớ: phép **âm một ngôi không tự tạo ra kiểu rộng hơn**. `int value = -2147483648;` hợp lệ theo quy tắc ranh giới trên; nhưng phủ định `Integer.MIN_VALUE` **khi đang chạy** là một phép toán `int` thông thường và có thể tràn số. Hai tình huống cần phân biệt rõ.

## 4. Literal ở các cơ số khác có quy tắc bit riêng

Các literal nguyên **nhị phân, bát phân và thập lục phân** có thể mô tả mẫu bit có bit cao nhất bằng 1. Khi được dùng với kiểu `int` hoặc `long`, mẫu bit đó được hiểu theo cách biểu diễn bù hai (*two's complement*) của Java; do vậy một literal viết **không có dấu âm ở trước** vẫn có thể mang giá trị số nguyên âm.

Ví dụ:

~~~java
int hexMin = 0x8000_0000;    // Integer.MIN_VALUE
int allBits = 0xFFFF_FFFF;   // -1
long longMin = 0x8000_0000_0000_0000L; // Long.MIN_VALUE
~~~

Đây là **đoạn minh họa**. Không nên kết luận từ việc nhìn thấy chữ số `8` hoặc `F` rằng literal phải là “số dương”, hay từ việc không có `-` rằng giá trị runtime chắc chắn không âm.

Đặc biệt, `-0x80000000` vẫn cho `Integer.MIN_VALUE` do phép âm trên giá trị `int` nhỏ nhất tràn theo quy tắc số nguyên cố định, **không** tạo thành số dương 2.147.483.648. Nếu cần giá trị dương đó, viết `0x8000_0000L` hoặc `2147483648L`.

Tóm lại, phải tách **hình thức viết literal**, **kiểu của literal**, **mẫu bit**, và **toán tử áp dụng lên nó**. Cách biểu diễn bit không làm `-` trở thành một phần của token literal.

## 5. Ví dụ Java 17+ chạy độc lập: literal và toán tử dấu âm

Tạo tệp `MinusLiteralDemo.java`:

~~~java
public class MinusLiteralDemo {
    public static void main(String[] args) {
        int positive = 42;
        int negative = -42;
        int minInt = -2147483648;
        long bigPositive = 2147483648L;
        long minLong = -9223372036854775808L;

        int decimal = -123;
        int binary = -0b1111011;
        int octal = -0173;
        int hex = -0x7B;

        System.out.println("positive=" + positive);
        System.out.println("negative=" + negative);
        System.out.println("minInt=" + minInt);
        System.out.println("bigPositive=" + bigPositive);
        System.out.println("minLong=" + minLong);
        System.out.println("sameValue="
                + (decimal == binary && binary == octal && octal == hex));
        System.out.println("hexBits=" + 0x8000_0000);
        System.out.println("parseText=" + Integer.parseInt("-42"));
    }
}
~~~

Biên dịch/chạy với JDK 17 hoặc mới hơn:

~~~bash
javac MinusLiteralDemo.java
java MinusLiteralDemo
~~~

Kết quả chuẩn:

~~~text
positive=42
negative=-42
minInt=-2147483648
bigPositive=2147483648
minLong=-9223372036854775808
sameValue=true
hexBits=-2147483648
parseText=-42
~~~

Hai loại “dấu trừ” cũng xuất hiện ở các **ngữ cảnh khác nhau**: trong mã `-42` là toán tử một ngôi; trong chuỗi ký tự `"-42"`, dấu `-` chỉ là ký tự dữ liệu cho đến khi `Integer.parseInt` đọc chuỗi và diễn giải nó theo cú pháp số có dấu. Quy tắc tách token Java không áp dụng trực tiếp cho các ký tự bên trong chuỗi.

## 6. Âm một ngôi có thể làm thay đổi kiểu biểu thức

Trong Java, toán tử âm một ngôi thực hiện **unary numeric promotion**:

- Với `byte`, `short` và `char`, toán hạng được nâng lên `int`; kết quả của `-operand` có kiểu `int`.
- Với `int`, kết quả vẫn có kiểu `int`.
- Với `long`, `float`, `double`, kết quả có cùng kiểu tương ứng sau quy tắc promotion.

Vì vậy, ví dụ sau **không biên dịch** do gán một kết quả `int` về biến `byte` khi dùng biến thông thường:

~~~java
byte count = 7;
byte invalid = -count; // lỗi: biểu thức -count có kiểu int
~~~

Nếu `count` là biến mutable trong ví dụ, dù giá trị là 7 vẫn phải ép kiểu rõ ràng: `byte negated = (byte) -count;`. Còn `byte literalValue = -7;` **hợp lệ** vì `-7` là *constant expression* kiểu `int` có giá trị nằm trong phạm vi `byte`, được cho phép bởi quy tắc thu hẹp hằng số tại phép gán. Đây là khác biệt giữa **giá trị hằng khi biên dịch** và **phép toán trên biến khi chạy**.

`char` không dấu 16 bit nhưng `-letter` không biến thành “`char` âm”: nó có kiểu `int` và có thể nhận giá trị âm nếu mã của `letter` dương. Điều này phản ánh quy tắc promotion, không đổi bản chất của kiểu `char`.

### Ví dụ Java 17+ chạy độc lập: promotion và phép trừ

Tạo tệp `UnaryMinusPromotionDemo.java`:

~~~java
public class UnaryMinusPromotionDemo {
    public static void main(String[] args) {
        byte small = 7;
        char letter = 'A';

        int negativeByte = -small;
        int negativeCharCode = -letter;
        byte constantByte = -7;

        System.out.println("negativeByte=" + negativeByte);
        System.out.println("negativeCharCode=" + negativeCharCode);
        System.out.println("constantByte=" + constantByte);
        System.out.println("subtractNegative=" + (10 - -3));
        System.out.println("negativeGroup=" + -(2 + 3));
        System.out.println("signedResult=" + (-3 * 4));
    }
}
~~~

~~~bash
javac UnaryMinusPromotionDemo.java
java UnaryMinusPromotionDemo
~~~

Đầu ra:

~~~text
negativeByte=-7
negativeCharCode=-65
constantByte=-7
subtractNegative=13
negativeGroup=-5
signedResult=-12
~~~

Trong câu lệnh có `10 - -3`, dấu trừ thứ nhất là toán tử hai ngôi, dấu trừ thứ hai là toán tử một ngôi. Dấu cách/ngoặc làm ý định sáng rõ: tránh ghép các dấu `-` mơ hồ thành toán tử tăng/giảm như `--` trong các trường hợp khác.

## 7. Phủ định giá trị nhỏ nhất: tràn số là rủi ro thật

Kiểu `int` không đối xứng quanh 0: phía âm có **thêm một giá trị** (`Integer.MIN_VALUE = -2147483648`). Giá trị dương `2147483648` **không nằm trong miền `int`**. Bởi vậy:

~~~java
int min = Integer.MIN_VALUE;
int stillMin = -min; // tràn int: kết quả vẫn là Integer.MIN_VALUE
~~~

Ví dụ là đoạn minh họa. Phép âm số nguyên primitive **không tự ném exception** khi overflow. Kết quả `-Integer.MIN_VALUE` vẫn là `Integer.MIN_VALUE` do số học bù hai với độ rộng cố định. Tương tự, `-Long.MIN_VALUE` vẫn là `Long.MIN_VALUE`.

Có ba cách xử lý tùy bài toán:

1. **Cần phát hiện tràn:** gọi `Math.negateExact(int)` hoặc `Math.negateExact(long)`; sẽ ném `ArithmeticException` với giá trị nhỏ nhất.
2. **Cần giá trị đối chính xác rộng hơn `int`:** chuyển sang `long` **trước** khi đổi dấu, ví dụ `-(long) Integer.MIN_VALUE` có giá trị `2147483648L`.
3. **Cần giá trị đối chính xác rộng hơn `long`:** dùng `BigInteger.valueOf(Long.MIN_VALUE).negate()`, kết quả vượt miền `long`.

Dù viết `long after = -Integer.MIN_VALUE;`, phép âm vẫn có kiểu `int` và **đã tràn trước khi gán sang long**. Phải viết `long before = -(long) Integer.MIN_VALUE;`. Cũng cần cẩn thận với `Math.abs(Integer.MIN_VALUE)`: kết quả vẫn âm theo hợp đồng API; không coi `abs` là cách luôn chuyển thành số dương được.

### Ví dụ Java 17+ chạy độc lập: xử lý tràn

Tạo tệp `NegationOverflowDemo.java`:

~~~java
import java.math.BigInteger;

public class NegationOverflowDemo {
    public static void main(String[] args) {
        int min = Integer.MIN_VALUE;

        System.out.println("wrapped=" + (-min));
        System.out.println("lateWiden=" + (long) (-min));
        System.out.println("earlyWiden=" + (-(long) min));
        System.out.println("absMin=" + Math.abs(min));

        try {
            Math.negateExact(min);
        } catch (ArithmeticException ex) {
            System.out.println("negateExact=overflow");
        }

        BigInteger exact = BigInteger.valueOf(Long.MIN_VALUE).negate();
        System.out.println("bigInteger=" + exact);
    }
}
~~~

~~~bash
javac NegationOverflowDemo.java
java NegationOverflowDemo
~~~

Đầu ra chính xác:

~~~text
wrapped=-2147483648
lateWiden=-2147483648
earlyWiden=2147483648
absMin=-2147483648
negateExact=overflow
bigInteger=9223372036854775808
~~~

Mấu chốt nằm ở **kiểu của toán hạng tại thời điểm thực hiện toán tử âm**. Mở rộng kiểu sau khi kết quả đã tràn không sửa được phép toán trước đó.

## 8. Dấu âm với số thực và số 0 âm

Với `float` và `double`, phép âm một ngôi hoạt động theo ngữ nghĩa dấu phẩy động IEEE 754 mà Java hỗ trợ. Ví dụ `-1.25` là toán tử âm áp dụng lên literal `double` `1.25`; `-1.25f` áp dụng lên literal `float` `1.25f`. `-1.25e2` cho `-125.0`.

Một điều dễ bỏ qua: **floating-point có cả `+0.0` và `-0.0`**, trong khi kiểu số nguyên `int`/`long` chỉ có một giá trị 0. Vì vậy:

- `0.0 == -0.0` là `true` theo phép so sánh số học.
- `1.0 / 0.0` cho `Infinity`, nhưng `1.0 / -0.0` cho `-Infinity`.
- `-Double.NaN` vẫn là `NaN`; không xem NaN là số có thể so sánh bằng phép `==` thông thường.

Ví dụ Java 17+ độc lập, tạo tệp `FloatingMinusDemo.java`:

~~~java
public class FloatingMinusDemo {
    public static void main(String[] args) {
        double negativeZero = -0.0;

        System.out.println("negativeValue=" + (-1.25e2));
        System.out.println("zeroEqual=" + (0.0 == negativeZero));
        System.out.println("positiveInfinity=" + (1.0 / 0.0));
        System.out.println("negativeInfinity=" + (1.0 / negativeZero));
        System.out.println("negatedNaN=" + Double.isNaN(-Double.NaN));
        System.out.println("integerZero=" + (-0));
    }
}
~~~

~~~bash
javac FloatingMinusDemo.java
java FloatingMinusDemo
~~~

Đầu ra:

~~~text
negativeValue=-125.0
zeroEqual=true
positiveInfinity=Infinity
negativeInfinity=-Infinity
negatedNaN=true
integerZero=0
~~~

Đây là ví dụ cho thấy một dấu `-` đứng trước literal có thể ảnh hưởng tới biểu diễn giá trị và phép toán phía sau. Đừng suy luận rằng “phủ định số 0 luôn không để lại khác biệt” trong tính toán floating-point.

## 9. Khi dùng `BigInteger` và `BigDecimal`

Toán tử `-` một ngôi **không áp dụng trực tiếp cho đối tượng** `BigInteger` hoặc `BigDecimal`. Các lớp này cung cấp phương thức `negate()` và `abs()`.

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;

public class BigNumberNegationDemo {
    public static void main(String[] args) {
        BigInteger count = new BigInteger("9223372036854775808");
        BigDecimal money = new BigDecimal("19.90");

        System.out.println("count=" + count.negate());
        System.out.println("money=" + money.negate().toPlainString());
    }
}
~~~

~~~bash
javac BigNumberNegationDemo.java
java BigNumberNegationDemo
~~~

Đầu ra:

~~~text
count=-9223372036854775808
money=-19.90
~~~

Dấu `-` trong chuỗi đầu vào (ví dụ `new BigDecimal("-19.90")`) là **nội dung văn bản được parser của lớp số giải thích**, khác với toán tử `-` của ngôn ngữ Java đứng ngoài biểu thức. `BigInteger.negate()` cho số đối chính xác trong giới hạn tài nguyên; `BigDecimal.negate()` giữ ngữ nghĩa số thập phân, gồm cả scale thích hợp của giá trị gốc.

## 10. Các lỗi nhầm thường gặp

| Nhầm lẫn | Cách hiểu đúng |
|---|---|
| “`-42` là một token literal duy nhất.” | `-` là unary operator; `42` là decimal integer literal. |
| “Không có `-` thì literal luôn là số dương.” | Literal nhị phân/bát phân/hex có mẫu bit cao bằng 1 có thể biểu diễn giá trị âm, ví dụ `0xFFFF_FFFF` là `-1` với `int`. |
| “`2147483648` không bao giờ xuất hiện trong source Java hợp lệ.” | Nó được dùng **đặc biệt** làm toán hạng cho unary minus trong `-2147483648`. |
| “Gán `-Integer.MIN_VALUE` vào `long` là an toàn.” | Phép âm tính ở `int` trước rồi mới mở rộng; cần cast `(long)` trước phép âm. |
| “`-byteValue` vẫn là `byte`.” | Unary numeric promotion tạo ra `int` với `byte`, `short`, `char`. |
| “`-0` và `-0.0` luôn như nhau.” | Số nguyên có một số 0; floating-point có hai giá trị 0 mang dấu khác nhau. |
| “Có thể dùng `-bigInteger`.” | Dùng `bigInteger.negate()`; toán tử một ngôi chỉ áp dụng trực tiếp cho kiểu số nguyên thủy. |
| “Mã text `"-42"` chứa toán tử unary minus.” | Trong chuỗi chỉ có ký tự; phép parse số là bước riêng. |

## 11. Tự kiểm tra

1. Trong `int a = -15;`, token literal là `-15` hay `15`? Ký hiệu `-` đóng vai trò gì?
2. Vì sao `int min = -2147483648;` hợp lệ, nhưng `int invalid = 2147483648;` không hợp lệ?
3. `0xFFFF_FFFF` là literal không có ký hiệu `-`; tại sao giá trị `int` của nó lại là `-1`?
4. Kiểu của `-small` là gì khi `small` được khai báo `byte`?
5. Vì sao `long x = -Integer.MIN_VALUE;` vẫn cho số âm, trong khi `long y = -(long) Integer.MIN_VALUE;` cho số dương?
6. Tại sao `0.0 == -0.0` là `true` mà phép chia `1.0 / 0.0` khác `1.0 / -0.0`?
7. Khi cần phủ định một số vượt miền `long`, nên chọn phương thức nào của `BigInteger`?

**Gợi ý đáp án:** (1) `15` là literal; `-` là unary minus. (2) Java có ngoại lệ cú pháp cho literal thập phân 2.147.483.648 làm toán hạng của unary minus. (3) Mẫu bit 32 bit toàn 1 tương ứng −1 trong biểu diễn số nguyên bù hai. (4) `int`. (5) Phép âm `int` tràn trước khi mở rộng; phải chuyển sang `long` trước. (6) `double` giữ dấu của zero, ảnh hưởng tới chia cho zero. (7) `BigInteger.negate()`.

**Ghi nhớ:** Trong mã Java, **dấu âm là toán tử của biểu thức, không phải một phần của literal số**. Nắm rõ điều này giúp hiểu thứ tự đánh giá biểu thức, ngữ nghĩa kiểu số, quy tắc ranh giới `Integer.MIN_VALUE`/`Long.MIN_VALUE` và tràn số khi phủ định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-literals-009">Literal, biểu thức hằng và khác biệt với phân tích String</a>

<details>
<summary>Xem chi tiết</summary>

# Literal, biểu thức hằng và khác biệt với phân tích `String` trong Java

Khi viết `int n = 42;`, Java đọc `42` trực tiếp như một **literal trong mã nguồn**. Khi viết `int n = Integer.parseInt("42");`, Java nhận một **chuỗi ký tự** rồi thực hiện **phân tích cú pháp lúc chương trình chạy**. Còn `int n = 40 + 2;` là một **biểu thức hằng** mà trình biên dịch có thể đánh giá. Cả ba có thể cho giá trị 42, nhưng cú pháp hợp lệ, thời điểm kiểm tra lỗi, quy tắc ép kiểu và khả năng tràn số khác nhau.

Bài này phân biệt ba cơ chế trong Java 17+ để đọc đúng literal ở các cơ số, hiểu constant expression, và chuyển dữ liệu văn bản thành số an toàn.

## 1. Ba cơ chế, ba thời điểm xử lý

| Cơ chế | Ví dụ | Được xử lý khi nào? | Điểm cần nhớ |
| --- | --- | --- | --- |
| **Literal** trong mã nguồn | `0x2A`, `42L`, `1.5e2` | Trình biên dịch phân tích cú pháp mã nguồn | Tiền tố/hậu tố là cú pháp ngôn ngữ, không phải ký tự lưu trong giá trị |
| **Biểu thức hằng lúc biên dịch** (*constant expression*) | `20 + 22`, `3 * (4 + 2)` | Giá trị được xác định theo quy tắc JLS lúc biên dịch | Chỉ một tập toán tử, literal và biến hằng hợp lệ; lời gọi phương thức thông thường không thuộc tập này |
| **Phân tích `String` thành số** | `Integer.parseInt("42")` | Lúc chạy (runtime) | Phương thức nhận chuỗi, radix và quy tắc parser riêng; đầu vào sai có thể ném `NumberFormatException` |

Một số ví dụ cùng kết quả:

```java
int fromLiteral = 42;
int fromExpression = 40 + 2;
int fromText = Integer.parseInt("42");
```

Dòng thứ nhất và thứ hai có thể được kiểm tra và xác định mà không cần dữ liệu runtime. Dòng thứ ba **cần gọi phương thức** ngay cả khi đối số là chuỗi literal. `"42"` là literal kiểu `String`, **không phải** literal số nguyên.

> **Cách phân biệt nhanh:** dấu ngoặc kép tạo `String`; tiền tố `0x`, `0b`, hậu tố `L`/`F` và dấu gạch dưới trong numeric literal tuân theo luật của mã nguồn. Không nên giả định rằng parser cho `String` sẽ chấp nhận tất cả cú pháp literal.

## 2. Literal số nguyên: cơ số, dấu, kiểu và giới hạn

### 2.1. Các cách viết cùng một giá trị

```java
int decimal = 42;       // Cơ số 10
int binary  = 0b101010; // Cơ số 2
int octal   = 052;      // Cơ số 8
int hex     = 0x2A;     // Cơ số 16
```

Bốn biến đều có giá trị toán học **42**. Cơ số chỉ ảnh hưởng **cách viết trong mã nguồn**. Sau khi biên dịch, toán tử cộng/trừ/so sánh làm việc trên giá trị và kiểu, không mang theo “cơ số gốc”. Muốn hiển thị lại ở cơ số khác, dùng `Integer.toBinaryString`, `Integer.toOctalString`, `Integer.toHexString` hoặc `Integer.toString(value, radix)`.

| Cách viết literal | Cơ số | Giá trị thập phân | Ghi chú |
| --- | ---: | ---: | --- |
| `10` | 10 | 10 | Dạng thông thường |
| `0b1010` hoặc `0B1010` | 2 | 10 | Chỉ chữ số `0` và `1` |
| `012` | 8 | 10 | Bắt đầu bằng `0`; rất dễ nhầm với số thập phân |
| `0xA` hoặc `0XA` | 16 | 10 | Các chữ số `0-9` và `a-f`/`A-F` |
| `10L` | 10 | 10 | Literal kiểu `long` |

**Bẫy số 0 đứng đầu:** trong mã Java, `010` là **số bát phân 8**, còn chuỗi `"010"` không mang cơ số cố định; parser quyết định ý nghĩa của chuỗi (xem phần 4). Literal `08` hoặc `09` là lỗi biên dịch, vì 8 và 9 không thuộc cơ số 8.

### 2.2. Literal `int` và `long`

Literal nguyên thập phân không có hậu tố `L` dùng kiểu `int`, nếu nằm trong miền cho phép (ngoại lệ cú pháp đặc biệt cho `-2147483648`). Hậu tố `L` hoặc `l` cho kiểu `long`; nên viết `L` để không nhầm chữ `l` với số `1`.

```java
int count = 2_000_000;
long largeCount = 3_000_000_000L;

// long invalid = 3_000_000_000; // Lỗi biên dịch: literal thập phân vượt int.
// long alsoValid = 3_000_000_000L;
```

Với số nguyên, viết `-42` là **toán tử dấu âm** đặt trước literal `42`, chứ dấu âm không phải ký tự bên trong numeric literal. Java có quy tắc riêng để viết giá trị tối thiểu:

```java
int minInt = -2147483648;
long minLong = -9223372036854775808L;
```

Trong ngữ cảnh đó, literal thập phân dương có độ lớn bằng `2^31` (hoặc `2^63` với `L`) chỉ hợp lệ làm toán hạng của unary minus theo quy tắc ngôn ngữ. Không thể dùng cùng chuỗi chữ số thập phân không dấu ấy như một giá trị dương kiểu tương ứng.

Với literal hệ 16, 8 và 2, Java cũng cho phép mẫu bit toàn 1 như `0xFFFF_FFFF` làm một `int` có giá trị **−1**. Đừng suy luận rằng một literal chỉ có chữ số hex dương nhất định cho số nguyên có dấu dương: **độ rộng và dấu** của kiểu quyết định cách diễn giải.

### 2.3. Dấu gạch dưới giúp đọc số nhưng không thuộc giá trị

Java cho phép dấu `_` **giữa các chữ số** trong numeric literal:

```java
int population = 1_234_567;
long flags = 0b1111_0000L;
double amount = 1_234.50;
```

Chúng không làm thay đổi giá trị. Dấu gạch dưới **không được** đặt đầu/cuối literal, ngay cạnh dấu chấm thập phân, trước/sau hậu tố kiểu, hoặc ở những vị trí khiến nó không nằm giữa chữ số hợp lệ. Chẳng hạn `1_.0`, `1._0`, `42_L`, `_42` (lúc này được đọc như tên định danh) và `42_` không phải cách viết numeric literal hợp lệ theo ý muốn.

**`"1_234"` là dữ liệu văn bản khác:** dấu gạch dưới có thật trong chuỗi và `Integer.parseInt("1_234")` sẽ ném `NumberFormatException`. Parser không tự hiểu quy tắc trình bày numeric literal của trình biên dịch.

## 3. Literal dấu phẩy động và ảnh hưởng của biểu diễn

`double` là kiểu mặc định cho literal có dấu chấm hoặc số mũ thập phân; hậu tố `f`/`F` chọn `float`; `d`/`D` có thể ghi rõ `double`:

```java
double a = 1.5;
double b = 15e-1;
double c = 1.5D;
float d = 1.5f;
double e = 0x1.8p1; // Literal floating-point hệ 16: 1.5 * 2^1 = 3.0.
```

Literal floating-point hệ 16 bắt đầu bằng `0x` và dùng số mũ `p`/`P` theo lũy thừa của **2**, ví dụ `0x1.8p1` bằng 3.0. Ký hiệu này hữu ích khi cần mô tả chính xác các bit/chữ số nhị phân; số mũ `e` dành cho literal floating-point thập phân.

`0.1` trong mã là literal `double` nhưng giá trị toán học 1/10 không có biểu diễn hữu hạn ở binary64; giá trị lưu là số nhị phân gần nhất. Việc biểu thức `0.1 + 0.2` có kết quả in ra `0.30000000000000004` là vấn đề **biểu diễn chấm động**, khác với vấn đề parser chấp nhận cú pháp chuỗi nào.

Không có numeric literal Java tương ứng trực tiếp với ký hiệu `NaN` hoặc `Infinity`; dùng `Double.NaN` và `Double.POSITIVE_INFINITY`. Ngược lại, `Double.parseDouble("NaN")` hoặc `Double.parseDouble("Infinity")` có thể phân tích các chuỗi đặc biệt đó. Bởi vậy, tập văn bản parser chấp nhận không hoàn toàn trùng tập numeric literal của ngôn ngữ.

Nếu cần phép tính thập phân chính xác theo văn bản đầu vào, cân nhắc `BigDecimal` từ chuỗi: `new BigDecimal("0.1")` giữ đúng 0.1 thập phân; `new BigDecimal(0.1)` nhận xấp xỉ đã nằm trong `double` và có thể tạo số thập phân dài ngoài ý muốn.

## 4. Phân tích `String`: radix, tiền tố, dấu và ngoại lệ

### 4.1. `parseInt` dùng cơ số 10 theo mặc định

```java
int a = Integer.parseInt("42");      // 42
int b = Integer.parseInt("+42");     // 42
int c = Integer.parseInt("-42");     // -42
int d = Integer.parseInt("010");     // 10, không phải octal.
int e = Integer.parseInt("FF", 16);  // 255
int f = Integer.parseInt("1010", 2); // 10
```

`Integer.parseInt(String)` phân tích **cơ số 10**; `Integer.parseInt(String, int radix)` dùng cơ số chỉ định trong khoảng `Character.MIN_RADIX` (2) đến `Character.MAX_RADIX` (36). Cơ số 16 chấp nhận ký tự chữ số hex mà không yêu cầu `0x`. Với cơ số lớn hơn 10, chữ cái biểu diễn các chữ số có giá trị từ 10 trở lên.

**Các trường hợp dễ nhầm:**

| Đầu vào và API | Kết quả |
| --- | --- |
| `Integer.parseInt("010")` | 10 (decimal) |
| `Integer.parseInt("010", 8)` | 8 (octal) |
| `Integer.parseInt("FF", 16)` | 255 |
| `Integer.parseInt("0xFF")` | `NumberFormatException` |
| `Integer.parseInt("0xFF", 16)` | `NumberFormatException` do tiền tố `0x` không hợp lệ với `parseInt` |
| `Integer.parseInt("1_000")` | `NumberFormatException` |
| `Integer.parseInt(" 42 ")` | `NumberFormatException` vì có khoảng trắng |
| `Integer.parseInt("2147483648")` | `NumberFormatException` vì vượt miền `int` |
| `Long.parseLong("2147483648")` | 2147483648 dưới dạng `long` |

Dấu `+`/`-` là hợp lệ ở đầu chuỗi theo quy tắc parser. Không được lặng lẽ bỏ qua `NumberFormatException`: đó thường là tín hiệu đầu vào không đúng định dạng hoặc vượt giới hạn cho phép.

### 4.2. `Integer.decode` hỗ trợ tiền tố radix, nhưng cần ý thức về số 0 đầu

`Integer.decode(String)` nhận dạng các tiền tố `0x`/`0X`/`#` là hệ 16, số `0` đứng đầu là hệ 8, còn lại là hệ 10. Nó có thể nhận dấu ở **trước** tiền tố. Dạng tương tự tồn tại ở `Long.decode`.

```java
Integer.parseInt("010");    // 10
Integer.decode("010");      // 8
Integer.decode("0xFF");     // 255
Integer.decode("#FF");      // 255
Integer.decode("-0xA");     // -10
```

**Không dùng `decode` bừa bãi cho mã số từ người dùng** nếu định dạng nghiệp vụ luôn là thập phân, vì chuỗi `"010"` có thể chỉ là mã hoặc số 10 mà lại bị hiểu thành octal 8. Hãy quyết định radix của dữ liệu bằng **hợp đồng đầu vào**, không dựa vào sự tiện lợi của parser.

### 4.3. Các parser khác có hợp đồng khác

- `Long.parseLong(String)`/`Long.parseLong(String, radix)`: giống hướng xử lý của `Integer.parseInt`, nhưng miền `long` lớn hơn; không tự nhận `0x` khi dùng `parseLong`.
- `Integer.parseUnsignedInt(String)`: phân tích số nguyên **không dấu 32 bit** trong miền 0 đến 4.294.967.295 nhưng trả về mẫu bit kiểu `int`. Dùng `Integer.toUnsignedLong` hoặc `Integer.toUnsignedString` để thao tác/hiển thị theo nghĩa không dấu; Java vẫn không có primitive `unsigned int` riêng.
- `Double.parseDouble(String)` và `Float.parseFloat(String)`: cho cú pháp dấu phẩy động, số mũ, các dạng hex floating-point, và một số chuỗi đặc biệt như `NaN`/`Infinity`. Chúng không áp dụng dấu gạch dưới dùng riêng cho numeric literal trong mã nguồn.
- `new BigInteger(String, radix)`: phân tích số nguyên độ lớn tùy ý trong radix chỉ định; `new BigDecimal(String)`: phân tích số thập phân chính xác theo cú pháp `BigDecimal`. Không tự động cho rằng chúng hiểu mọi tiền tố Java literal hoặc mọi ký tự phân nhóm địa phương.

Với dữ liệu nhập từ người dùng, `parseInt` không loại bỏ khoảng trắng; có thể dùng `strip()` một cách **chủ động** khi hợp đồng cho phép. `Double.parseDouble` xử lý một số khoảng trắng theo quy tắc riêng của nó, nên không nên dựa vào hành vi whitespace giống nhau giữa tất cả parser. Quy định rõ có cho phép khoảng trắng, dấu `+` và chữ số ngoài ASCII hay không; đối với nguồn cần chuẩn hóa chặt (API, mã định danh), có thể kiểm tra cú pháp trước khi parse.

### 4.4. Cơ số khi chuyển số thành chuỗi

```java
int number = 255;
String hex = Integer.toString(number, 16);  // "ff"
String binary = Integer.toBinaryString(number); // "11111111"
int restored = Integer.parseInt(hex, 16);   // 255
```

`Integer.toHexString`/`toBinaryString` có hành vi dựa trên biểu diễn bit đối với số âm; ví dụ `Integer.toHexString(-1)` tạo `"ffffffff"`. Chuỗi đó có ý nghĩa là 4.294.967.295 nếu diễn giải số **không dấu 32 bit**, nên `Integer.parseInt("ffffffff", 16)` ném `NumberFormatException` vì vượt `Integer.MAX_VALUE`. Để khôi phục bit theo nghĩa không dấu, dùng `Integer.parseUnsignedInt("ffffffff", 16)` hoặc logic tương ứng với yêu cầu.

## 5. Biểu thức hằng lúc biên dịch là gì?

**Compile-time constant expression** là biểu thức mà Java Language Specification cho phép xác định giá trị trong lúc biên dịch, dựa trên literal, một số toán tử và **constant variable**. Không phải biểu thức nào có kết quả luôn giống nhau cũng là constant expression theo định nghĩa ngôn ngữ.

Ví dụ:

```java
int a = 2 * (3 + 4);             // Constant expression: 14.
String label = "Java " + "17";  // Ghép chuỗi constant expression.

final int base = 10;
int b = base + 4;                // base là constant variable, b = 14.

int c = Integer.parseInt("14"); // Gọi phương thức tại runtime: không phải constant expression.
int d = Math.abs(-14);          // Gọi phương thức: không phải constant expression.
```

Một **constant variable** là biến `final` kiểu primitive hoặc `String` và được khởi tạo bằng constant expression. Biến `final` không đương nhiên là constant variable:

```java
final int fixed = 20;                    // Constant variable.
final int parsed = Integer.parseInt("20"); // final nhưng không phải constant variable.
final Integer boxed = 20;               // Wrapper Integer không là constant variable.
```

Dù lập trình viên nhìn thấy `Integer.parseInt("20")` luôn trả 20 trong trường hợp này, **lời gọi phương thức không được phép** trong compile-time constant expression theo quy tắc của Java. “Compiler có thể tối ưu code” cũng không đồng nghĩa với “ngôn ngữ coi biểu thức đó là hằng lúc biên dịch”.

### 5.1. Thu hẹp kiểu đối với biểu thức hằng

Java cho phép một số phép chuyển từ constant expression kiểu `int` sang `byte`, `short` hoặc `char` **không cần cast**, nếu giá trị xác định tại biên dịch nằm trong miền đích:

```java
byte total = 100 + 20; // Hợp lệ: hằng 120 vừa byte.
char letter = 65;      // Hợp lệ: giá trị 65 vừa char.

final int offset = 20;
byte alsoFine = 100 + offset; // Hợp lệ: constant variable.

int runtimeOffset = 20;
// byte notAllowed = 100 + runtimeOffset; // Lỗi biên dịch: biểu thức int không hằng.
// byte tooBig = 100 + 30;               // Lỗi biên dịch: 130 không vừa byte.
```

Dạng thường gặp gây thắc mắc:

```java
byte x = 10;
byte y = 20;
// byte z = x + y; // Lỗi: phép cộng số nguyên promote các toán hạng byte lên int.
int z = x + y;     // Hợp lệ.
```

Việc hai biến `byte` chứa giá trị nhỏ **không khiến phép `x + y` có kiểu `byte`**. Trình biên dịch kiểm tra **kiểu của biểu thức** và quy tắc chuyển đổi, không chỉ kiểm tra giá trị có vẻ nhỏ tại thời điểm đọc mã.

### 5.2. Hằng ở `case` và annotation

Với dạng `switch` theo số nguyên (Java 17), nhãn `case` phải là constant expression phù hợp:

```java
final int code = 10;
int input = 11;
switch (input) {
    case code + 1 -> System.out.println("matched"); // Hợp lệ.
    default -> System.out.println("other");
}

// case Integer.parseInt("11") -> ... // Không hợp lệ: method call không là constant expression.
```

Nhiều phần tử của annotation cũng yêu cầu giá trị dạng compile-time constant (hoặc loại biểu thức đặc biệt phù hợp với kiểu phần tử annotation). Khi cần API runtime thay vì hằng trong source, hãy chuyển logic sang chỗ phù hợp; không thể biến `parseInt` thành constant expression chỉ bằng cách đánh dấu kết quả `final`.

### 5.3. Tràn số xảy ra cả trong biểu thức hằng

Java không tự mở rộng `int` sang `long` vì biến đích là `long`. Một constant expression nguyên tính toán với kiểu `int` có thể tràn, rồi **kết quả đã tràn** mới được gán:

```java
long wrong = 1_000_000 * 3_000;   // -1294967296: nhân int tràn.
long correct = 1_000_000L * 3_000; // 3000000000: nhân long.
```

Vì biểu thức có kiểu `int`, trình biên dịch có thể tính trước kết quả theo quy tắc phép toán `int`, bao gồm tràn; việc biên dịch thành công **không bảo đảm kết quả toán học mong muốn**. `Math.multiplyExact` có thể phát hiện overflow ở phép tính runtime; nếu tích cần kiểu `long`, hãy bảo đảm ít nhất một toán hạng `long` từ **trước phép nhân**.

## 6. Vì sao parser lúc chạy và constant expression cần xử lý lỗi khác nhau?

Các lỗi thuộc hai nhóm khác nhau:

| Tình huống | Giai đoạn/phản ứng | Cách xử lý |
| --- | --- | --- |
| `int x = 0b102;` | Không biên dịch: chữ số không thuộc cơ số 2 | Sửa literal trong mã nguồn |
| `int x = 08;` | Không biên dịch: literal bát phân sai | Dùng `8` nếu muốn thập phân |
| `long x = 3000000000;` | Không biên dịch: literal thập phân không `L` vượt `int` | Thêm `L` |
| `byte b = 130;` | Không biên dịch: constant expression không vừa kiểu | Đổi kiểu/cận trên hoặc xử lý dữ liệu |
| `Integer.parseInt("0xFF")` | Runtime `NumberFormatException` | Đặt radix với chuỗi không có tiền tố, hoặc dùng `decode` có chủ đích |
| `Integer.parseInt("2147483648")` | Runtime `NumberFormatException` do overflow | Dùng `Long.parseLong` nếu miền nghiệp vụ cho phép |
| `Integer.parseInt(userInput)` với chuỗi sai | Runtime `NumberFormatException` | Validate theo hợp đồng, catch ở ranh giới nhập |
| `new BigDecimal("0.1")` | Runtime tạo số thập phân chính xác | Chọn số chữ số/scale và chính sách làm tròn khi cần |

Không thể “bắt” lỗi cú pháp literal trong `try/catch`, vì chương trình còn chưa biên dịch thành công. Ngược lại, phân tích `String` là thao tác runtime nên phải kiểm tra nguồn đầu vào, đặc biệt ở API, CLI, tệp dữ liệu và form nhập liệu.

### 6.1. Parse input có kiểm soát

```java
static int parseNonNegativeCount(String input) {
    if (input == null || !input.matches("[0-9]+")) {
        throw new IllegalArgumentException("Count must contain decimal digits");
    }
    int count = Integer.parseInt(input); // Có thể ném NumberFormatException khi vượt int.
    return count;
}
```

Ví dụ minh họa trên **chủ động giới hạn chữ số ASCII 0–9, không có dấu, không có whitespace**. Regex chấp nhận `"0007"` như số 7; nghiệp vụ có thể cho phép hoặc cấm số 0 đầu. Nếu cần hỗ trợ mọi chữ số Unicode hoặc dấu cộng/trừ, thay đổi kiểm tra theo hợp đồng. Một regex đúng về hình thức **không bảo đảm số nằm trong miền `int`**; vẫn cần xử lý `NumberFormatException` khi overflow.

Không nên tự “làm sạch” tùy tiện bằng cách xóa tất cả dấu `_`, dấu phẩy, dấu chấm hay khoảng trắng: `"1,234"` có thể là số phân nhóm hàng nghìn ở locale này nhưng là dấu thập phân ở locale khác. Đối với số nhập theo locale, dùng định dạng/parsing theo `NumberFormat` và xác định rõ quy tắc locale; đối với giao thức máy, thường nên dùng dạng số chuẩn và radix tường minh.

## 7. Ví dụ Java 17+ chạy độc lập

Lưu tệp dưới tên `LiteralConstantParsingDemo.java`:

```java
import java.math.BigDecimal;

public class LiteralConstantParsingDemo {
    public static void main(String[] args) {
        int binary = 0b101010;
        int octal = 052;
        int hex = 0x2A;
        System.out.println("Three radices: " + binary + ", " + octal + ", " + hex);

        final int base = 10;
        byte folded = 100 + base;
        System.out.println("Constant expression to byte: " + folded);

        System.out.println("Literal 010: " + 010);
        System.out.println("parseInt 010: " + Integer.parseInt("010"));
        System.out.println("decode 010: " + Integer.decode("010"));
        System.out.println("parseInt FF radix 16: " + Integer.parseInt("FF", 16));
        System.out.println("decode 0xFF: " + Integer.decode("0xFF"));

        long wrong = 1_000_000 * 3_000;
        long correct = 1_000_000L * 3_000;
        System.out.println("Overflow before long: " + wrong);
        System.out.println("Long multiplication: " + correct);

        System.out.println("Hex float: " + 0x1.8p1);
        System.out.println("Exact decimal sum: "
                + new BigDecimal("0.1").add(new BigDecimal("0.2")));

        try {
            Integer.parseInt("1_000");
        } catch (NumberFormatException ex) {
            System.out.println("Underscore in parsed String: invalid");
        }

        try {
            Integer.parseInt("2147483648");
        } catch (NumberFormatException ex) {
            System.out.println("Beyond int: invalid");
        }

        System.out.println("Long parser: " + Long.parseLong("2147483648"));
    }
}
```

Chạy trong thư mục chứa tệp với JDK 17 trở lên:

```bash
javac LiteralConstantParsingDemo.java
java LiteralConstantParsingDemo
```

Kết quả:

```text
Three radices: 42, 42, 42
Constant expression to byte: 110
Literal 010: 8
parseInt 010: 10
decode 010: 8
parseInt FF radix 16: 255
decode 0xFF: 255
Overflow before long: -1294967296
Long multiplication: 3000000000
Hex float: 3.0
Exact decimal sum: 0.3
Underscore in parsed String: invalid
Beyond int: invalid
Long parser: 2147483648
```

Ở đây, giá trị 42 từ ba radix literal giống nhau, còn chuỗi `"010"` có thể được hiểu là 10 hoặc 8 tùy parser. `byte folded` hợp lệ nhờ compile-time constant expression. Phép nhân `int` cho kết quả đã tràn khi gán vào `long`; có `L` thì biểu thức dùng `long` ngay từ đầu. Hai ngoại lệ `NumberFormatException` được bắt vì chúng là lỗi **runtime parsing**.

## 8. Checklist khi đọc và viết số trong Java

1. **Số được viết trong mã hay đến từ dữ liệu ngoài?** Nếu nằm trong mã Java, dùng quy tắc literal. Nếu nhận chuỗi, dùng parser có hợp đồng rõ.
2. **Radix có rõ ràng không?** Literal `0b`/`0x` hoặc số 0 đầu có ý nghĩa trong source; `parseInt` mặc định decimal, `decode` có tiền tố và octal. Chọn parser theo định dạng dữ liệu.
3. **Cú pháp có phụ thuộc biên dịch?** Dấu `_`, hậu tố `L`/`F`, literal hex float là một phần ngữ pháp mã nguồn; chuỗi parser chấp nhận theo luật riêng.
4. **Đây có thật là compile-time constant expression?** Literal và phép toán cho phép có thể là hằng; lời gọi `parseInt` không phải hằng dù argument là `"42"`. `final` chỉ là một điều kiện cần cho constant variable.
5. **Miền giá trị và kiểu phép toán có phù hợp?** Dùng `L` khi số literal thập phân lớn hoặc phép toán cần `long`; đừng gán sau khi biểu thức `int` tràn.
6. **Đầu vào runtime sai sẽ được xử lý ở đâu?** Quy định dấu, whitespace, radix, nhóm chữ số, locale và cách báo lỗi. Bắt `NumberFormatException` tại ranh giới nhận dữ liệu; không che giấu lỗi bằng giá trị mặc định không được thiết kế.
7. **Có cần decimal chính xác hay số nguyên vượt `long` không?** Dùng `BigDecimal(String)`/`BigInteger` theo hợp đồng dữ liệu thay vì cố gắng ép mọi thứ qua `double` hoặc `int`.

### Câu hỏi tự kiểm tra

- Tại sao `010` trong Java có giá trị 8 nhưng `Integer.parseInt("010")` có giá trị 10? `Integer.decode("010")` cho giá trị nào?
- Tại sao `byte b = 100 + 20;` biên dịch, nhưng `int x = 20; byte b = 100 + x;` lại không?
- Tại sao `final int x = Integer.parseInt("5");` chưa khiến `x` là constant variable theo định nghĩa Java?
- Vì sao `long total = 1_000_000 * 3_000;` cho giá trị khác `long total = 1_000_000L * 3_000;`?
- `"0xFF"` hợp lệ với `Integer.parseInt(..., 16)` hay `Integer.decode(...)`? Đối với API chỉ nhận số thập phân, chọn cách nào để không hiểu nhầm chuỗi `"010"`?
- Vì sao một chuỗi số nhập hợp lệ về **cú pháp** vẫn có thể ném `NumberFormatException`?

**Nguyên tắc áp dụng:** literal thuộc cú pháp Java tại compile time; constant expression là khái niệm hẹp hơn “phép tính luôn cho kết quả như nhau”; parsing `String` là hợp đồng runtime với quy tắc radix, cú pháp và kiểm tra miền riêng. Giữ ba cơ chế tách bạch sẽ tránh lỗi về cơ số, ép kiểu, tràn số và dữ liệu nhập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-literals-010">Đọc mẫu bit của literal hex có bit dấu được bật</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc mẫu bit của literal hex khi bit dấu được bật trong Java

Với Java, literal thập lục phân như `0x8000_0000` thường khiến người mới học ngạc nhiên: khi in ra dưới dạng `int`, kết quả là **-2_147_483_648** chứ không phải số dương 2_147_483_648. Tương tự, `0xFFFF_FFFF` có giá trị **-1** nếu là `int`; nhưng `0xFFFF_FFFFL` có giá trị **4_294_967_295** khi là `long`.

Không có sự thay đổi tùy ý của các chữ số hex. Điều cần xác định là **mẫu bit có bao nhiêu bit, bit cao nhất của kiểu có được bật hay không, và ta đang đọc mẫu bit đó như số nguyên có dấu hay không dấu**.

## 1. Từ chữ số hex đến mẫu bit

Hệ thập lục phân có 16 ký hiệu `0`–`9` và `A`–`F`. Mỗi chữ số hex tương ứng **4 bit**. Ví dụ:

| Hex | Nhị phân 4 bit | Giá trị hex |
| --- | --- | ---: |
| `0` | `0000` | 0 |
| `7` | `0111` | 7 |
| `8` | `1000` | 8 |
| `F` | `1111` | 15 |

Mỗi chữ số hex ghép vào mẫu bit theo thứ tự từ trái sang phải. Ví dụ, `0x8000_0000` tương ứng mẫu `10000000 00000000 00000000 00000000` (32 bit): chỉ bit ở vị trí cao nhất được bật. `0xFFFF_FFFF` biểu diễn 32 bit bằng 1.

Tiền tố `0x` (hoặc `0X`) chỉ cho trình biên dịch biết literal dùng **cơ số 16**; chữ `L` cuối literal chọn kiểu `long`. Dấu gạch dưới `_` chỉ giúp nhóm chữ số cho dễ đọc: `0xFFFF_FFFF` và `0xFFFFFFFF` hoàn toàn tương đương.

## 2. Phạm vi của `int` và `long`: bit nào là bit dấu?

Java dùng kiểu nguyên có dấu `int` **32 bit** và `long` **64 bit**, với biểu diễn **bù hai (two's complement)**. Vị trí bit được đánh số từ 0 ở phía phải.

| Kiểu | Tổng số bit | Bit dấu | Mẫu có bit dấu = 0 | Mẫu có bit dấu = 1 |
| --- | ---: | ---: | --- | --- |
| `int` | 32 | bit 31 | Giá trị không âm | Giá trị âm |
| `long` | 64 | bit 63 | Giá trị không âm | Giá trị âm |

- `0x7FFF_FFFF`: bit 31 bằng 0, giá trị `int` bằng **2_147_483_647**.
- `0x8000_0000`: bit 31 bằng 1, giá trị `int` bằng **-2_147_483_648** (`Integer.MIN_VALUE`).
- `0xFFFF_FFFF`: mọi bit đều bằng 1, giá trị `int` bằng **-1**.
- `0x7FFF_FFFF_FFFF_FFFFL`: bit 63 bằng 0, giá trị `long` bằng **9_223_372_036_854_775_807**.
- `0x8000_0000_0000_0000L`: bit 63 bằng 1, giá trị `long` bằng **-9_223_372_036_854_775_808** (`Long.MIN_VALUE`).
- `0xFFFF_FFFF_FFFF_FFFFL`: mọi bit đều bằng 1, giá trị `long` bằng **-1**.

**Quan trọng:** một chữ số hex bắt đầu bằng `8` hoặc `F` **không tự động** biến cả literal thành số âm. Chẳng hạn `0x8` là `int` dương 8, `0xFF` là `int` dương 255, vì bit dấu ở vị trí 31 vẫn bằng 0. Chỉ khi bit dấu của **độ rộng kiểu thực tế** được bật thì diễn giải có dấu mới là âm. Đối với `int`, 8 chữ số hex có thể phủ hết 32 bit; đối với `long`, 16 chữ số hex có thể phủ hết 64 bit.

## 3. Đọc số có dấu từ mẫu bit bù hai

Hãy tưởng tượng cùng một mẫu bit có giá trị **không dấu** `U` với độ rộng `w` bit. Giá trị **có dấu** là:

```text
signed = U                  nếu bit dấu = 0
signed = U - 2^w            nếu bit dấu = 1
```

Với `int` (`w = 32`):

1. `0x8000_0000` có giá trị không dấu `U = 2^31 = 2_147_483_648`. Vì bit 31 bằng 1, giá trị có dấu là `2_147_483_648 - 4_294_967_296 = -2_147_483_648`.
2. `0xFFFF_FFFF` có giá trị không dấu `U = 2^32 - 1 = 4_294_967_295`. Giá trị có dấu là `4_294_967_295 - 4_294_967_296 = -1`.
3. `0xF000_0000` có giá trị không dấu `4_026_531_840`, còn giá trị có dấu là **-268_435_456**.

Với `long` (`w = 64`), `0x8000_0000_0000_0000L` là `-2^63`, còn `0xFFFF_FFFF_FFFF_FFFFL` là `-1L`. Nếu diễn giải mẫu toàn bit 1 dưới dạng **không dấu 64 bit**, giá trị của nó là `2^64 - 1 = 18_446_744_073_709_551_615`; giá trị này không chứa được trong `long` dương có dấu.

Đây là **hai cách diễn giải cùng mẫu bit**. Java không cần đảo bit hoặc sửa literal để in ra số âm: cách đọc là hệ quả của định nghĩa kiểu `int` và `long`.

## 4. Vì sao literal hex có thể bật bit dấu mà vẫn hợp lệ?

Ở literal **thập phân**, `2_147_483_648` không có hậu tố `L` thường vượt phạm vi `int` và gây lỗi biên dịch. Ở literal **không thập phân** như hex, Java cho phép mô tả trực tiếp mẫu 32 bit của `int`, kể cả bit cao nhất bằng 1. Vì vậy đoạn sau hợp lệ:

```java
int a = 0x8000_0000; // -2147483648
int b = 0xFFFF_FFFF; // -1
```

Hai dòng trên là **mảnh mã** có thể đặt trong phương thức. Không nên suy rằng `0xFFFF_FFFF` lớn hơn `Integer.MAX_VALUE` sẽ làm Java tự nâng thành `long`: literal không `L` vẫn thuộc `int`, và diễn giải thành số âm theo mẫu bit. Tuy nhiên, mẫu đòi hỏi hơn 32 bit **có nghĩa** không thể dùng làm `int` literal, chẳng hạn `0x1_0000_0000` (không có `L`) không hợp lệ; dạng `0x1_0000_0000L` là `long` hợp lệ.

Với `long`, literal hex có hậu tố `L` có thể mô tả mẫu 64 bit, kể cả bật bit 63:

```java
long c = 0x8000_0000_0000_0000L; // Long.MIN_VALUE
long d = 0xFFFF_FFFF_FFFF_FFFFL; // -1L
```

Chữ `L` xác định **độ rộng 64 bit**, không có nghĩa “giá trị chắc chắn dương”. Nếu bit 63 bằng 1, một `long` vẫn là số âm.

## 5. Cùng chữ số hex nhưng thêm `L` có thể đổi kết quả

Một tình huống quan trọng là so sánh **`0xFFFF_FFFF`** với **`0xFFFF_FFFFL`**:

| Biểu thức | Kiểu literal | Mẫu bit của kiểu | Giá trị có dấu |
| --- | --- | --- | ---: |
| `0xFFFF_FFFF` | `int` | 32 bit 1 | -1 |
| `0xFFFF_FFFFL` | `long` | 32 bit thấp 1, **32 bit cao 0** | 4_294_967_295 |
| `0x8000_0000` | `int` | chỉ bit 31 bằng 1 | -2_147_483_648 |
| `0x8000_0000L` | `long` | chỉ bit 31 bằng 1; bit 63 bằng 0 | 2_147_483_648 |

Đây chính là lý do cần nhìn **kiểu literal** trước khi kết luận một chữ số hex biểu diễn số âm hay dương. Chẳng hạn `0x8000_0000` có bit 31 bật nên âm đối với `int`. Nhưng `0x8000_0000L` là `long`: bit 31 chỉ là một bit giá trị, còn bit dấu 63 bằng 0, nên dương.

## 6. Gán `int` âm vào `long`: mở rộng dấu và mở rộng bằng 0

Khi chuyển `int` âm sang `long`, Java dùng **mở rộng dấu (sign extension)**: giữ nguyên giá trị số học bằng cách điền thêm bit 1 ở phía cao. Vì vậy:

```java
int bits = 0xFFFF_FFFF;                 // -1
long signed = bits;                     // -1L
long unsigned = Integer.toUnsignedLong(bits); // 4294967295L
long masked = bits & 0xFFFF_FFFFL;      // 4294967295L
```

Các khai báo này là mảnh mã. Với `signed`, mẫu `int` gồm 32 bit 1 được sign-extend thành `0xFFFF_FFFF_FFFF_FFFF` (64 bit 1), vẫn mang giá trị `-1L`. Với `unsigned` và `masked`, mục tiêu là **diễn giải 32 bit gốc như số không dấu**, tức điền 32 bit cao bằng 0 để có `0x0000_0000_FFFF_FFFF` = `4_294_967_295L`.

Hai cách chuyển quan trọng khi giữ nguyên thông tin mẫu bit:

- `Integer.toUnsignedLong(bits)`: thể hiện rõ ý định coi 32 bit của `int` như một giá trị không dấu từ 0 tới `2^32 - 1`.
- `bits & 0xFFFF_FFFFL`: thực hiện phép AND với một mặt nạ **`long`**, khiến phép toán ở 64 bit và chỉ giữ 32 bit thấp. Nếu dùng `bits & 0xFFFF_FFFF` (hai toán hạng đều `int`) thì vẫn nhận `int` có dấu; phép gán tiếp sang `long` lại mở rộng dấu.

Đừng nhầm **`long signed = 0xFFFF_FFFF;`** với **`long unsigned = 0xFFFF_FFFFL;`**. Trong trường hợp đầu, literal là `int` -1 rồi được mở rộng dấu thành `long` -1; trong trường hợp sau, bản thân literal là `long` dương `4_294_967_295L`.

## 7. Chương trình hoàn chỉnh chạy trên Java 17+

Tạo tệp **`HexSignBitDemo.java`** với nội dung sau:

```java
public class HexSignBitDemo {
    public static void main(String[] args) {
        int largestPositive = 0x7FFF_FFFF;
        int intSignBit = 0x8000_0000;
        int intAllOnes = 0xFFFF_FFFF;

        long widened = intAllOnes;
        long zeroExtended = Integer.toUnsignedLong(intAllOnes);
        long longFromSameDigits = 0xFFFF_FFFFL;
        long longSignBit = 0x8000_0000_0000_0000L;
        long longAllOnes = 0xFFFF_FFFF_FFFF_FFFFL;

        System.out.println("max positive int=" + largestPositive);
        System.out.println("int sign bit=" + intSignBit);
        System.out.println("int all ones=" + intAllOnes);
        System.out.println("int hex=" + String.format("%08X", intSignBit));
        System.out.println("int binary=" + Integer.toBinaryString(intSignBit));
        System.out.println("widened=" + widened);
        System.out.println("widened hex=" + Long.toHexString(widened));
        System.out.println("zero extended=" + zeroExtended);
        System.out.println("zero extended hex=" + Long.toHexString(zeroExtended));
        System.out.println("same digits with L=" + longFromSameDigits);
        System.out.println("long sign bit=" + longSignBit);
        System.out.println("long all ones=" + longAllOnes);
        System.out.println("long all ones unsigned=" + Long.toUnsignedString(longAllOnes));
        System.out.println("unsigned int text=" + Integer.toUnsignedString(intAllOnes));
        System.out.println("unsigned mask=" + (intSignBit & 0xFFFF_FFFFL));
        System.out.println("signed right shift=" + (intSignBit >> 31));
        System.out.println("unsigned right shift=" + (intSignBit >>> 31));
        System.out.println("parse unsigned hex=" + Integer.toUnsignedLong(Integer.parseUnsignedInt("FFFFFFFF", 16)));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac --release 17 -encoding UTF-8 HexSignBitDemo.java
java HexSignBitDemo
```

Kết quả stdout:

```text
max positive int=2147483647
int sign bit=-2147483648
int all ones=-1
int hex=80000000
int binary=10000000000000000000000000000000
widened=-1
widened hex=ffffffffffffffff
zero extended=4294967295
zero extended hex=ffffffff
same digits with L=4294967295
long sign bit=-9223372036854775808
long all ones=-1
long all ones unsigned=18446744073709551615
unsigned int text=4294967295
unsigned mask=2147483648
signed right shift=-1
unsigned right shift=1
parse unsigned hex=4294967295
```

Các dòng đáng chú ý:

- `int hex=80000000` và `int binary=1000...` cho thấy **mẫu bit 32 bit**; dòng `int sign bit=-2147483648` cho thấy **giá trị có dấu** của chính mẫu đó.
- `widened hex=ffffffffffffffff` minh họa mở rộng dấu của `int` -1 sang `long`; `zero extended hex=ffffffff` hiển thị không có chữ số 0 dẫn đầu, nhưng giá trị tương đương mẫu 64 bit `00000000ffffffff`.
- `same digits with L=4294967295` cho thấy khi literal được khai báo là `long` ngay từ đầu, bit dấu 63 không được bật.
- `long all ones=-1` nhưng `long all ones unsigned=18446744073709551615` là hai cách hiển thị có dấu và không dấu của **cùng 64 bit toàn 1**.
- `unsigned mask=2147483648` minh họa mặt nạ `long` giữ giá trị không dấu của `0x8000_0000`.
- `signed right shift=-1` và `unsigned right shift=1` cho thấy khác biệt giữa `>>` và `>>>` khi dịch bit của số âm.

## 8. In ra mẫu bit thay vì chỉ xem giá trị thập phân

`System.out.println(value)` sử dụng giá trị `int`/`long` **có dấu**. Khi cần quan sát mẫu bit, dùng:

```java
int x = 0x8000_0000;
System.out.println(Integer.toHexString(x));    // "80000000"
System.out.println(Integer.toBinaryString(x)); // "10000000000000000000000000000000"
System.out.printf("%08X%n", x);                // 80000000
```

Đây là các câu lệnh đặt trong `main`. `Integer.toHexString` và `Integer.toBinaryString` cho chuỗi biểu diễn mẫu bit theo cơ số tương ứng; `printf("%08X%n", x)` yêu cầu ít nhất 8 ký tự hex, thêm các số 0 dẫn đầu nếu thiếu. Với `long`, có thể dùng `Long.toHexString(value)` hoặc `%016X` nếu muốn hiển thị đủ 16 chữ số.

Phân biệt **hình thức trình bày** và **giá trị lưu**: `Integer.toHexString(-1)` là `"ffffffff"`, không có dấu trừ, nhưng `Integer.toString(-1)` là `"-1"`. Chỉ vì chuỗi hex không có dấu `-` không có nghĩa số `int` hiện tại dương.

## 9. Dịch phải có dấu và dịch phải không dấu

Khi bit dấu đã bật, hai toán tử dịch phải có ý nghĩa khác nhau:

```java
int x = 0x8000_0000;
int arithmetic = x >> 31; // -1: điền thêm 1 ở phía trái.
int logical = x >>> 31;   // 1: điền thêm 0 ở phía trái.
```

`>>` là **dịch phải giữ dấu (arithmetic shift)**; `>>>` là **dịch phải không giữ dấu (logical shift)**. Hai toán tử này đều làm việc với mẫu bit, còn cách biểu diễn kết quả vẫn phụ thuộc kiểu `int` hoặc `long` của toán hạng. Chúng không thay đổi kiểu của biến thành kiểu “unsigned”; Java vẫn lưu `int`/`long` có dấu.

Để kiểm tra bit dấu của `int`, có thể viết `(x & 0x8000_0000) != 0` hoặc đơn giản `x < 0`. Với `long`, dùng mặt nạ `0x8000_0000_0000_0000L`. Đừng dùng `x > 0` để phát hiện xem bất kỳ bit nào được bật: ví dụ `-1` có **mọi bit bật** nhưng lại nhỏ hơn 0.

## 10. Đọc hex từ chuỗi và giữ cách diễn giải không dấu

Khi giá trị hex nằm trong văn bản, phải phân biệt cách phân tích có dấu/không dấu:

```java
int signedBits = Integer.parseUnsignedInt("FFFFFFFF", 16);
String unsignedText = Integer.toUnsignedString(signedBits); // "4294967295"
long unsignedValue = Integer.toUnsignedLong(signedBits);   // 4294967295L
long parsedLong = Long.parseLong("FFFFFFFF", 16);           // 4294967295L
```

Đoạn trên là mảnh mã hợp lệ trong một phương thức. `Integer.parseUnsignedInt` cho phép phân tích mẫu thuộc khoảng không dấu 32 bit, nhưng **kết quả lưu trong `int`**; vì vậy `signedBits` có giá trị số học có dấu `-1`. Dùng `Integer.toUnsignedString` hoặc `Integer.toUnsignedLong` khi muốn diễn giải thành không dấu.

`Integer.parseInt("FFFFFFFF", 16)` sẽ báo `NumberFormatException` do giá trị hex dương tương ứng vượt `Integer.MAX_VALUE` trong cách parse có dấu. Trong khi đó, `Long.parseLong("FFFFFFFF", 16)` thành công vì giá trị đó vẫn nằm trong phạm vi dương của `long`.

Đối với mẫu không dấu **64 bit** có giá trị lớn hơn `Long.MAX_VALUE`, `Long.parseUnsignedLong("FFFFFFFFFFFFFFFF", 16)` trả về mẫu bit nằm trong `long` (biểu diễn có dấu là `-1L`); `Long.toUnsignedString(-1L)` hiển thị giá trị không dấu `18446744073709551615`. Nếu muốn một **đối tượng số nguyên dương thật sự** lưu giá trị đó để tính toán tùy độ lớn, dùng `new java.math.BigInteger("FFFFFFFFFFFFFFFF", 16)`.

## 11. Những nhầm lẫn phổ biến

| Nhầm lẫn | Thực tế | Cách đọc đúng |
| --- | --- | --- |
| `0x8` âm vì hex bắt đầu bằng 8 | `int` vẫn có bit 31 bằng 0 | `0x8 == 8` |
| `0x8000_0000` là số dương 2_147_483_648 | `int` có bit 31 bằng 1 | `-2_147_483_648` |
| `0xFFFF_FFFF` không thể là `int` vì “quá lớn” | Literal hex có thể diễn tả 32 bit toàn 1 | `int` bằng `-1` |
| Thêm `L` luôn biến mẫu hex thành số dương | `long` cũng có bit dấu 63 | `0xFFFF_FFFF_FFFF_FFFFL == -1L` |
| `long x = 0xFFFF_FFFF;` thành 4_294_967_295 | Literal có kiểu `int` âm rồi được mở rộng dấu | `x == -1L` |
| `x & 0xFFFF_FFFF` làm `int` thành số dương không dấu | AND trên hai `int` vẫn cho `int` | Dùng `Integer.toUnsignedLong(x)` hoặc mặt nạ `0xFFFF_FFFFL` |
| `Integer.toHexString(-1)` không có dấu trừ nên số dương | Chuỗi hex thể hiện mẫu bit | `"ffffffff"` là mẫu của `int` -1 |
| `>>` và `>>>` luôn giống nhau | Với bit dấu bật, cách điền bit phía trái khác nhau | `0x80000000 >> 31 == -1`, nhưng `>>> 31 == 1` |

## 12. Tự kiểm tra

1. **`int x = 0x7FFF_FFFF;`** có giá trị gì? **2_147_483_647**, vì bit 31 chưa được bật.
2. **`int x = 0xFFFF_FFFF;`** có giá trị gì? **-1**, vì đó là 32 bit toàn 1 ở dạng bù hai.
3. **`long x = 0xFFFF_FFFF;`** và **`long y = 0xFFFF_FFFFL;`** có bằng nhau không? **Không**: `x == -1L`, `y == 4_294_967_295L`.
4. **`0x8000_0000`** và **`0x8000_0000L`** có cùng giá trị số học không? **Không**: lần lượt là `-2_147_483_648` và `2_147_483_648L`.
5. **Muốn đọc `int x = 0xFFFF_FFFF;` như số không dấu, phải làm gì?** Dùng `Integer.toUnsignedLong(x)` để nhận `4_294_967_295L`, hoặc `Integer.toUnsignedString(x)` để nhận văn bản `"4294967295"`.
6. **`0x8000_0000_0000_0000L` âm hay dương?** **Âm**, bằng `Long.MIN_VALUE`, vì bit 63 đã bật.

**Kết luận:** khi gặp literal hex có bit dấu bật, hãy xác định **kiểu literal (32 hay 64 bit)**, tìm **bit dấu ở vị trí 31 hay 63**, rồi diễn giải mẫu bit theo **bù hai**. Nếu cần giá trị không dấu, dùng API unsigned hoặc mở rộng bằng 0; nếu gán một `int` âm sang `long`, nhớ rằng Java sẽ **mở rộng dấu**, không tự biến mẫu bit thành giá trị dương.


</details>

- [Quay lại đầu trang](#back-to-top)
