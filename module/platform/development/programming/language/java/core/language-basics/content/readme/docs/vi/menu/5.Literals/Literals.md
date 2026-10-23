<a id="back-to-top"></a>

# Literal

## Menu
- [Literal số nguyên thập phân, nhị phân, bát phân và thập lục phân](#literals-001)
- [Hậu tố L và kiểu mặc định của literal số nguyên](#literals-002)
- [Literal số thực, ký hiệu số mũ và hậu tố F hoặc D](#literals-003)
- [Dấu gạch dưới trong literal số và vị trí không hợp lệ](#literals-004)
- [Literal boolean](#literals-005)
- [Literal ký tự và literal chuỗi](#literals-006)
- [Escape sequence cho xuống dòng, dấu nháy và dấu gạch chéo ngược](#literals-007)
- [Text block: cú pháp mở đóng và xử lý thụt lề](#literals-008)
- [Literal null và những nơi không thể gán null](#literals-009)

## <a id="literals-001">Literal số nguyên thập phân, nhị phân, bát phân và thập lục phân</a>

<details>
<summary>Xem chi tiết</summary>

# Literal số nguyên thập phân, nhị phân, bát phân và thập lục phân trong Java

> **Java core — language-basics / Literal**  
> **Môi trường:** JDK 17 trở lên, không cần thư viện ngoài.  
> **Mục tiêu:** Viết và đọc đúng literal số nguyên ở cơ số 10, 2, 8, 16; hiểu tiền tố, hậu tố `L`, dấu gạch dưới `_`, giới hạn `int`/`long` và cách Java diễn giải các bit thành số có dấu. Tự biên dịch, chạy chương trình và phân biệt literal trong source với chuỗi số được nhập lúc chạy.

## 1. Literal số nguyên là gì?

**Integer literal** là cách viết trực tiếp một giá trị số nguyên **trong mã nguồn Java**. Chẳng hạn:

~~~java
int students = 42;
long population = 8_000_000_000L;
~~~

`42` và `8_000_000_000L` là các literal; `students` và `population` là tên biến; `int` và `long` là các từ khóa kiểu dữ liệu.

Cùng một giá trị nguyên có thể được viết bằng nhiều cơ số. Java 17 hỗ trợ bốn hệ ký số nguyên phổ biến:

| Cơ số | Cách viết literal | Các chữ số hợp lệ | Ví dụ biểu diễn giá trị 42 |
| --- | --- | --- | --- |
| **Thập phân (10)** | Không cần tiền tố | `0–9` | `42` |
| **Nhị phân (2)** | `0b` hoặc `0B` | `0`, `1` | `0b101010` |
| **Bát phân (8)** | Bắt đầu bằng `0` và có thêm chữ số bát phân | `0–7` | `052` |
| **Thập lục phân (16)** | `0x` hoặc `0X` | `0–9`, `a–f`, `A–F` | `0x2A` |

Ở ví dụ trên, bốn literal đều mang giá trị **42** khi được gán vào `int`. Tên cơ số mô tả **cách viết hằng số trong source**, không tạo ra bốn kiểu biến khác nhau.

**Cẩn thận với số 0 đứng đầu:** `010` có giá trị **8**, không phải 10. Số `09` lại là lỗi vì `9` không phải chữ số bát phân. Khi muốn biểu diễn số thập phân 10, viết `10` — không viết `010`.

## 2. Quy tắc quy đổi cơ số

Giá trị của một số theo cơ số `b` là tổng của các chữ số nhân với lũy thừa của `b` tương ứng vị trí:

\[
(d_n \ldots d_1d_0)_b=\sum_{i=0}^{n} d_i b^i
\]

Ví dụ với cùng số 42:

| Literal | Cách tính | Kết quả thập phân |
| --- | --- | ---: |
| `42` | `4 × 10 + 2` | 42 |
| `0b101010` | `1×32 + 0×16 + 1×8 + 0×4 + 1×2 + 0×1` | 42 |
| `052` | `5×8 + 2` | 42 |
| `0x2A` | `2×16 + A(10)` | 42 |

Từ `A` đến `F` trong hệ 16 đại diện lần lượt cho giá trị 10 đến 15. Trong hệ 2, `1010` là mười; trong hệ 8, `52` là bốn mươi hai; đừng đọc tất cả các chuỗi chữ số như số thập phân.

**Khi chọn cách viết:** cơ số 10 dễ đọc cho số lượng; cơ số 2 hữu ích cho bit flag; cơ số 8 hay xuất hiện ở một số ngữ cảnh quyền truy cập/tương thích cũ; cơ số 16 giúp nhìn nhóm bit gọn hơn, ví dụ mặt nạ `0xFF`. Hãy chọn cách viết rõ ý đồ thay vì chỉ vì có thể viết nhiều cơ số.

## 3. Chương trình Java 17+ chạy được: IntegerLiteralsDemo.java

Tạo file **IntegerLiteralsDemo.java** với nội dung:

~~~java
public class IntegerLiteralsDemo {
    public static void main(String[] args) {
        int decimal = 42;
        int binary = 0b101010;
        int octal = 052;
        int hexadecimal = 0x2A;

        System.out.println("values=" + decimal + "," + binary
                + "," + octal + "," + hexadecimal);
        System.out.println("equal=" + (decimal == binary
                && binary == octal && octal == hexadecimal));

        System.out.println("leadingZero010=" + 010);
        System.out.println("grouped=" + 1_000_000);

        System.out.println("binaryOf42=" + Integer.toBinaryString(decimal));
        System.out.println("octalOf42=" + Integer.toOctalString(decimal));
        System.out.println("hexOf42=" + Integer.toHexString(decimal));

        int allBits = 0xFFFF_FFFF;
        long positiveLong = 0xFFFF_FFFFL;
        System.out.println("intBits=" + allBits);
        System.out.println("longBits=" + positiveLong);
        System.out.println("unsignedIntBits=" + Integer.toUnsignedLong(allBits));

        System.out.println("parseHex=" + Integer.parseInt("2A", 16));
    }
}
~~~

**Biên dịch và chạy** trong Bash, PowerShell hoặc Command Prompt tại thư mục chứa file:

~~~bash
javac -encoding UTF-8 IntegerLiteralsDemo.java
java IntegerLiteralsDemo
~~~

Nếu biên dịch thành công, `javac` thường không in gì. Lệnh `java` cho **đúng các dòng sau**:

~~~text
values=42,42,42,42
equal=true
leadingZero010=8
grouped=1000000
binaryOf42=101010
octalOf42=52
hexOf42=2a
intBits=-1
longBits=4294967295
unsignedIntBits=4294967295
parseHex=42
~~~

**Giải thích ba kết quả quan trọng:**

1. `42`, `0b101010`, `052` và `0x2A` có cách viết khác nhau nhưng cùng giá trị; `==` giữa các `int` so sánh bằng số.
2. `Integer.toBinaryString`, `toOctalString` và `toHexString` trả về **chuỗi ký tự** biểu diễn số trong cơ số được chọn; `toHexString` dùng chữ thường cho `a–f`. Chúng **không đổi kiểu của biến `decimal`**.
3. `0xFFFF_FFFF` có mẫu bit 32 bit toàn số 1; khi biểu diễn dưới dạng `int` có dấu, nó là **-1**. Thêm hậu tố `L` tạo literal `long` với cùng các chữ số hex, nên giá trị dương là **4.294.967.295**. `Integer.toUnsignedLong(allBits)` cũng cho cách nhìn không dấu của 32 bit ấy.

### Lần theo đường đi từ source tới output

~~~mermaid
flowchart LR
    A["Literal 0b101010, 052, 0x2A"] --> B["Compiler hiểu giá trị int: 42"]
    B --> C["Biến int trong chương trình"]
    C --> D["println: hiển thị thập phân 42"]
    C --> E["toBinaryString: chuỗi 101010"]
    C --> F["toHexString: chuỗi 2a"]
~~~

Lúc in bằng cách nối chuỗi, `println` không tự nhớ giá trị từng được viết trong source bằng cơ số nào. Muốn hiển thị ở cơ số khác, dùng phương thức chuyển **giá trị thành chuỗi** tương ứng.

## 4. Kiểu số nguyên khi viết theo cơ số

Cơ số quyết định **cách ghi chữ số**, còn hậu tố quyết định **kiểu literal**: không có `L` thường là `int` (32 bit), có `L` là `long` (64 bit). Ví dụ `42`, `0b101010` và `0x2A` đều là `int` mang giá trị 42; `0x2AL` là `long` cùng giá trị.

~~~java
int count = 42;
long sameCount = 0x2AL;
long veryLarge = 4_000_000_000L;
// long invalid = 4_000_000_000; // Decimal int literal vượt phạm vi.
~~~

Đừng suy ra kiểu literal từ biến nhận: `long x = 42;` vẫn bắt đầu từ literal `int` và chuyển mở rộng sang `long`. Với mẫu bit có bit dấu, cách hiểu `int`/`long` được minh họa trực tiếp ở mục 7; phần giới hạn số, hậu tố `L`, phép toán tràn và ép kiểu được phân tích riêng trong bài **Hậu tố L và kiểu mặc định của literal số nguyên**.

## 5. Nhóm chữ số trong từng cơ số bằng dấu `_`

Bạn có thể viết `1_000_000`, `0b1010_0101`, `07_52` hoặc `0xCA_FE` để dễ đọc. `_` không đổi giá trị, cơ số hay kiểu của literal. Không đặt nó sát tiền tố hoặc ở cuối phần số: `0b_1010`, `0x_FF`, `123_L`, `100_` đều không hợp lệ.

Các quy tắc chi tiết, trường hợp nhiều dấu `_` liên tiếp, dấu chấm và số mũ thuộc bài **Dấu gạch dưới trong literal số và vị trí không hợp lệ**.

## 6. Dấu trừ và giá trị nhỏ nhất

Dấu `-` trước một số là **toán tử một ngôi**, không thuộc bản thân token literal: `-5` được phân tích thành toán tử `-` và literal `5`. Hai dạng thập phân `-2147483648` và `-9223372036854775808L` là các trường hợp đặc biệt Java cho phép khi literal làm toán hạng của dấu trừ; bỏ dấu trừ ở các giá trị đó thì literal dương vượt giới hạn `int`/`long`.

Mục 7 tiếp tục giải thích vì sao **mẫu bit** trong literal nhị phân/bát phân/hex có thể biểu diễn số âm mà không cần viết dấu trừ. Bài về hậu tố `L` đào sâu quy tắc giới hạn và biểu thức số nguyên.

## 7. Cách hiểu mẫu bit đối với literal nhị phân/bát phân/hex

Với literal không phải thập phân, Java cho phép thể hiện **mẫu bit** chiếm trọn 32 bit của `int` hoặc 64 bit của `long`; vì Java `int`/`long` là kiểu có dấu theo biểu diễn bù hai (*two's complement*), mẫu có bit dấu bằng 1 được hiểu là số âm.

| Mẫu nguồn Java | Kiểu literal | Giá trị nếu in thập phân có dấu |
| --- | --- | ---: |
| `0x7FFF_FFFF` | `int` | 2.147.483.647 |
| `0x8000_0000` | `int` | -2.147.483.648 |
| `0xFFFF_FFFF` | `int` | -1 |
| `0xFFFF_FFFFL` | `long` | 4.294.967.295 |
| `0x8000_0000_0000_0000L` | `long` | -9.223.372.036.854.775.808 |

Ví dụ `0b1111_1111` chỉ có 8 bit 1 nhưng được đưa vào kiểu `int` 32 bit nên có giá trị **255**, không phải `-1`; muốn biểu diễn `-1` bằng 32 bit 1 thì phải có đủ 32 bit 1 hoặc viết `0xFFFF_FFFF`.

**Không có kiểu primitive `unsigned int` trong Java 17.** Khi cần giải thích 32 bit như số không dấu, dùng API như `Integer.toUnsignedLong`, `Integer.compareUnsigned` hoặc các phép dịch/bitmask thích hợp. Mục đích là diễn giải các bit, không tạo một primitive mới.

## 8. Literal trong mã nguồn khác với parse chuỗi lúc chạy

Đây là hai việc khác nhau:

- Viết `int color = 0xFF;` trong source: **compiler** nhận diện tiền tố `0x` và tạo giá trị số 255.
- Gọi `Integer.parseInt("FF", 16)` lúc chạy: API Java đọc chuỗi `"FF"` theo **radix 16** và trả `255`.
- Gọi `Integer.decode("0xFF")`: API nhận một số kiểu tiền tố và trả về `Integer` có giá trị `255`.
- Gọi `Integer.parseInt("0xFF", 16)`: **lỗi `NumberFormatException`**, vì `parseInt` với radix 16 không nhận tiền tố `0x` trong chuỗi.

Ví dụ:

~~~java
int fromLiteral = 0b1010;                // 10, do compiler hiểu.
int fromString = Integer.parseInt("1010", 2); // 10, do API parse.
int fromHexString = Integer.parseInt("2A", 16); // 42.
int fromPrefix = Integer.decode("0x2A"); // 42.
~~~

Radix truyền cho `parseInt` có thể từ **2 đến 36**. Đây là API đọc chuỗi, không mở rộng số cơ số mà bạn có thể đặt làm integer literal trực tiếp trong Java source.

Không tự nhét `0b`, `0x` hoặc dấu gạch dưới vào chuỗi parse rồi kỳ vọng chúng hoạt động như cú pháp literal của compiler. Với dữ liệu người dùng, phải chuẩn hóa/kiểm tra theo hợp đồng đầu vào một cách rõ ràng.

## 9. Những lỗi thường gặp

| Đoạn mã / hiện tượng | Vấn đề | Cách sửa |
| --- | --- | --- |
| `int n = 09;` | Số có `0` đầu bị hiểu theo hệ bát phân, chữ số 9 không hợp lệ | Nếu muốn số chín thập phân, viết `9` |
| `int n = 010;` cho ra 8 | Không phải lỗi biên dịch: `010` là bát phân | Viết `10` nếu muốn mười |
| `int x = 0b102;` | `2` không là chữ số cơ số 2 | Chỉ dùng `0`, `1` |
| `int x = 0x1G;` | `G` không phải chữ số hex | Dùng `0–9`, `A–F` |
| `int x = 0x_FF;` | Dấu gạch dưới sai vị trí | Viết `0xFF` hoặc `0xF_F` |
| `long n = 8_000_000_000;` | Literal mặc định `int` quá lớn | Dùng `8_000_000_000L` |
| `int n = 0x1_0000_0000;` | Literal không hậu tố vượt 32 bit | Dùng `long n = 0x1_0000_0000L;` |
| `int n = 0xFFFF_FFFF;` in -1 | Không phải lỗi: mẫu 32 bit toàn 1 mang dấu âm | Dùng `Integer.toUnsignedLong(n)` nếu cần nhìn kiểu unsigned |
| `parseInt("1_000")` lỗi | Chuỗi runtime không theo cú pháp numeric literal | Nhập chuỗi chữ số thuần hoặc xử lý định dạng rõ ràng |

**Cần phân biệt:** lỗi do **cú pháp literal** được compiler phát hiện khi biên dịch; lỗi `NumberFormatException` của `parseInt` chỉ xảy ra khi **chương trình đang chạy**.

## 10. Bài tập thực hành

**Bài 1 — Cùng một giá trị.** Viết giá trị thập phân 255 bằng literal nhị phân, bát phân và hex. Dùng `println` để chứng minh cả ba cho `255`.

*Gợi ý:* `0b1111_1111`, `0377`, `0xFF`.

**Bài 2 — Nhận diện cơ số.** Dự đoán giá trị của `10`, `010`, `0b10` và `0x10`.

*Đáp án:* lần lượt **10**, **8**, **2**, **16**.

**Bài 3 — Giới hạn kiểu.** Vì sao `long n = 8_000_000_000;` không hợp lệ, còn `long n = 8_000_000_000L;` lại hợp lệ? Thử thêm hậu tố `L` cho literal nhị phân và hex.

**Bài 4 — Bit dấu.** Dự đoán đầu ra của `System.out.println(0xFFFF_FFFF);` và `System.out.println(0xFFFF_FFFFL);`. Giải thích vì sao cùng chữ số hex lại ra hai số khác nhau.

**Bài 5 — Dấu `_`.** Phân loại `1_000`, `1__000`, `0b1010_0001`, `0x_FF`, `123_L` và `10_` thành hợp lệ/không hợp lệ. Đừng vội kết luận hai dấu gạch dưới liên tiếp luôn sai.

**Bài 6 — Literal so với chuỗi.** Trong `IntegerLiteralsDemo`, so sánh `0x2A` với `Integer.parseInt("2A", 16)`. Điều gì xảy ra khi viết `Integer.parseInt("0x2A", 16)`? Thử phương thức `Integer.decode`.

## 11. Checklist cuối bài

- [ ] Nhớ **không tiền tố → cơ số 10**, `0b`/`0B` → cơ số 2, `0` đứng đầu số nhiều chữ số bát phân → cơ số 8, `0x`/`0X` → cơ số 16.
- [ ] Biết `09` sai và `010` có giá trị 8.
- [ ] Biết hậu tố `L` quyết định literal `long`; biến đích `long` không tự sửa literal `int` quá lớn.
- [ ] Đặt `_` **giữa các chữ số**, không ngay sau tiền tố hay sát hậu tố.
- [ ] Hiểu dấu âm là **toán tử một ngôi** và biết hai ca giới hạn âm nhỏ nhất.
- [ ] Phân biệt **mẫu bit có dấu** với cách nhìn 32 bit không dấu qua `Integer.toUnsignedLong`.
- [ ] Biết literal trong source khác việc `parseInt` giải mã một chuỗi lúc chạy.

**Tổng kết:** Bốn cơ số là **bốn cách ghi giá trị nguyên** trong chương trình Java. Đọc đúng tiền tố, hậu tố, dấu phân nhóm và giới hạn kiểu sẽ giúp tránh lỗi nhập nhằng như `010`, literal quá lớn và mẫu bit hex mang giá trị âm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="literals-002">Hậu tố L và kiểu mặc định của literal số nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Hậu tố `L` và kiểu mặc định của literal số nguyên trong Java

## 1. Literal số nguyên có kiểu gì?

**Integer literal** là cách viết trực tiếp một giá trị số nguyên trong mã Java, ví dụ `10`, `1_000`, `0xFF` hay `42L`. Trong **Java 17+**, kiểu của literal số nguyên được xác định ngay khi compiler phân tích biểu thức, **không phải chờ xem biến bên trái có kiểu gì**.

Quy tắc quan trọng:

- **Không có hậu tố `L` hoặc `l`**: literal số nguyên có kiểu **`int`** (32 bit).
- **Có hậu tố `L` hoặc `l`**: literal số nguyên có kiểu **`long`** (64 bit).
- Nên viết **`L` hoa**: `1L`, `100_000L` thay vì `1l`, vì chữ `l` thường dễ nhầm với số `1`.
- Chọn kiểu **`long` cho biến nhận** không tự chuyển một literal quá lớn không có `L` thành literal `long`.

~~~java
int a = 42;              // Literal 42 có kiểu int
long b = 42;             // Hợp lệ: int được widening sang long
long c = 42L;            // Literal 42L có kiểu long
long d = 3_000_000_000L; // Hợp lệ: có L, vượt phạm vi int
// long bad = 3_000_000_000; // Lỗi compile: literal int quá lớn
~~~

Ở dòng `long b = 42;`, **bản thân `42` vẫn là literal `int`**. Sau đó Java cho phép chuyển mở rộng (*widening primitive conversion*) từ `int` sang `long` khi gán. Cần phân biệt **kiểu của literal** và **kiểu của biến**.

## 2. Phạm vi giá trị của int và long

| Kiểu | Kích thước | Giá trị nhỏ nhất | Giá trị lớn nhất |
|---|---:|---:|---:|
| `int` | 32 bit | −2.147.483.648 | 2.147.483.647 |
| `long` | 64 bit | −9.223.372.036.854.775.808 | 9.223.372.036.854.775.807 |

Các giá trị có thể truy cập bằng `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `Long.MIN_VALUE` và `Long.MAX_VALUE`.

~~~java
int largestInt = 2_147_483_647;
long firstBeyondInt = 2_147_483_648L;
long largestLong = 9_223_372_036_854_775_807L;
~~~

**Tại sao dòng thứ hai cần `L`?** Vì `2_147_483_648` không có hậu tố bị phân tích như một decimal `int` literal, quá phạm vi của `int`, nên chương trình bị từ chối ở bước biên dịch. Biến đích có kiểu `long` không chữa được literal đã sai.

**`L` không làm `long` vô hạn.** Viết một giá trị dương lớn hơn `Long.MAX_VALUE`, kể cả có `L`, cũng bị compiler từ chối:

~~~java
// long tooBig = 9_223_372_036_854_775_808L; // Sai
~~~

Nếu thật sự cần số nguyên có độ lớn vượt `long`, hãy dùng `java.math.BigInteger` và khởi tạo từ chuỗi; không có hậu tố integer literal nào tự biến nó thành `BigInteger`.

## 3. Số âm và hai giá trị MIN_VALUE đặc biệt

Dấu `-` là **toán tử một ngôi** (*unary minus*), không phải ký tự thuộc một integer literal dương. Ví dụ `-5L` được hiểu là toán tử `-` áp dụng lên literal `5L`.

Java dành trường hợp đặc biệt cho giới hạn âm của `int` và `long` khi literal decimal tương ứng nằm ngay dưới toán tử unary minus:

~~~java
int minInt = -2147483648;                 // Hợp lệ
long minLong = -9223372036854775808L;    // Hợp lệ

// int invalid = 2147483648;            // Sai, literal int dương quá lớn
// long invalidLong = 9223372036854775808L; // Sai, literal long dương quá lớn
~~~

**Vì sao có sự bất đối xứng?** Miền số nguyên có dấu kiểu bù hai (*two's complement*) có một giá trị âm nhiều hơn số giá trị dương. Nhờ quy tắc về toán tử `-`, Java cho phép viết trực tiếp `Integer.MIN_VALUE` và `Long.MIN_VALUE`.

Không áp dụng ngoại lệ này cho phép cộng thông thường hoặc biến đích `long`. Ví dụ `long x = 2147483648;` vẫn là lỗi. Nếu chỉ cần giá trị nhỏ nhất, viết `Integer.MIN_VALUE` hoặc `Long.MIN_VALUE` thường dễ đọc hơn.

## 4. Kiểu mặc định không chỉ áp dụng cho hệ thập phân

Integer literal Java có nhiều cách viết:

| Cách viết | Literal | Giá trị theo hệ 10 | Kiểu khi không có hậu tố |
|---|---|---:|---|
| Thập phân | `26` | 26 | `int` |
| Thập lục phân | `0x1A` hoặc `0X1A` | 26 | `int` |
| Nhị phân | `0b11010` hoặc `0B11010` | 26 | `int` |
| Bát phân | `032` | 26 | `int` |
| Thập phân có hậu tố | `26L` | 26 | `long` |
| Thập lục phân có hậu tố | `0x1AL` | 26 | `long` |

**Lưu ý số 0 đầu:** `010` là literal **bát phân**, mang giá trị **8**, không phải 10. Viết `08` là sai vì chữ số 8 không hợp lệ trong hệ bát phân.

### Khi literal khác hệ thập phân tràn phần dấu

Với literal **thập lục phân, nhị phân hoặc bát phân**, Java cho phép biểu diễn trực tiếp mẫu bit trong độ rộng `int` hoặc `long`. Mẫu bit có bit dấu bằng 1 được hiểu thành số âm theo biểu diễn bù hai.

~~~java
int intBits = 0xFFFF_FFFF;
long longBits = 0xFFFF_FFFFL;
long allLongBits = 0xFFFF_FFFF_FFFF_FFFFL;

System.out.println(intBits);      // -1
System.out.println(longBits);     // 4294967295
System.out.println(allLongBits);  // -1
~~~

**Điểm tinh tế:** cùng các chữ số `0xFFFF_FFFF`, literal không hậu tố là `int` 32 bit (giá trị `-1`), còn `0xFFFF_FFFFL` là `long` 64 bit (giá trị `4_294_967_295`). Vì độ rộng đã đổi, bit cao nhất của `long` này không phải bit dấu.

Không khái quát sai rằng “literal hex luôn dương” hoặc “chỉ decimal mới có thể âm”.

## 5. Dấu gạch dưới `_` giúp dễ đọc, không đổi kiểu

Java cho phép dùng dấu gạch dưới **giữa các chữ số** của nhiều dạng literal:

~~~java
int population = 1_000_000;
long distance = 12_345_678_901L;
int mask = 0b1010_1100;
long colorCode = 0xAB_CD_EFL;
~~~

`1_000_000` vẫn là `int`; `12_345_678_901L` vẫn là `long`. Gạch dưới không làm tăng giới hạn kiểu hoặc thay thế hậu tố `L`.

Ví dụ cú pháp **không hợp lệ**:

~~~java
// int a = _123;          // Đây là tên định danh _123, không phải số 123
// int b = 123_;          // Không đặt _ ở cuối literal
// int c = 0x_FF;         // Không đặt _ ngay sau tiền tố 0x
// long d = 123_L;        // Không đặt _ ngay trước hậu tố L
~~~

Để giữ mã dễ đọc, nhóm chữ số theo ý nghĩa (`1_000_000L`, `0xFF_00_00_00L`) thay vì đặt dấu `_` tùy ý.

## 6. Kiểu của biểu thức được quyết định trước khi gán

Đây là một trong những lỗi Java phổ biến nhất khi học hậu tố `L`.

~~~java
long wrong = 2_000_000_000 + 2_000_000_000;
long correct = 2_000_000_000L + 2_000_000_000;

System.out.println(wrong);   // -294967296
System.out.println(correct); // 4000000000
~~~

Vì cả hai toán hạng đầu tiên là literal `int`, biểu thức `int + int` được tính bằng **32 bit** và tràn trước khi chuyển sang `long`. Java vẫn cho phép gán kết quả `int` đã tràn sang `long`; nó **không tính lại phép cộng bằng 64 bit**.

Ở dòng `correct`, một toán hạng có hậu tố `L`, nên phép cộng dùng `long` do **binary numeric promotion**. Kết quả 4 tỷ nằm trong phạm vi `long`.

Hai cách đúng cho phép tính này:

~~~java
long bySuffix = 2_000_000_000L + 2_000_000_000;
long byCast = (long) 2_000_000_000 + 2_000_000_000;
~~~

**Vị trí ép kiểu quyết định kết quả:**

~~~java
long stillWrong = (long) (2_000_000_000 + 2_000_000_000);
// Đã tràn int bên trong ngoặc rồi mới chuyển sang long.
~~~

Với phép nhân, nguyên tắc tương tự:

~~~java
long wrongProduct = 1_500_000_000 * 3;
long rightProduct = 1_500_000_000L * 3;
// rightProduct = 4500000000; wrongProduct đã tràn int.
~~~

Khi xử lý số lượng lớn hoặc tiền bạc, đừng nghĩ “khai báo biến `long` là đủ”. Cần kiểm soát **kiểu của phép tính** ngay từ toán hạng.

## 7. Ví dụ Java 17 hoàn chỉnh và kết quả chạy

Tạo tệp **`IntegerLiteralDemo.java`** với nội dung:

~~~java
public final class IntegerLiteralDemo {
    private IntegerLiteralDemo() {}

    static String overload(int value) {
        return "int: " + value;
    }

    static String overload(long value) {
        return "long: " + value;
    }

    public static void main(String[] args) {
        int defaultInt = 42;
        long widened = 42;
        long explicitLong = 42L;
        long bigLong = 3_000_000_000L;

        int hexInt = 0xFFFF_FFFF;
        long hexLong = 0xFFFF_FFFFL;
        int octal = 017;
        int binary = 0b1010;

        long overflowBeforeWiden = 2_000_000_000 + 2_000_000_000;
        long promoteFirst = 2_000_000_000L + 2_000_000_000;
        long castFirst = (long) 2_000_000_000 + 2_000_000_000;

        int minInt = -2147483648;
        long minLong = -9223372036854775808L;

        byte small = 127; // int constant được thu hẹp hợp lệ
        var inferredInt = 7;
        var inferredLong = 7L;

        System.out.println("int default: " + defaultInt);
        System.out.println("widened into long: " + widened);
        System.out.println("explicit long: " + explicitLong);
        System.out.println("big long: " + bigLong);
        System.out.println("hex int: " + hexInt);
        System.out.println("hex long: " + hexLong);
        System.out.println("octal: " + octal);
        System.out.println("binary: " + binary);
        System.out.println("overflow before widen: " + overflowBeforeWiden);
        System.out.println("long before add: " + promoteFirst);
        System.out.println("cast before add: " + castFirst);
        System.out.println("minimum int: " + minInt);
        System.out.println("minimum long: " + minLong);
        System.out.println("byte from int literal: " + small);
        System.out.println(overload(inferredInt));
        System.out.println(overload(inferredLong));
    }
}
~~~

Mở terminal trong thư mục chứa file và dùng **JDK 17 hoặc mới hơn**.

**Bash (Linux/macOS):**

~~~bash
java -version
javac -version
javac --release 17 -encoding UTF-8 IntegerLiteralDemo.java
java -cp . IntegerLiteralDemo
~~~

**PowerShell (Windows):**

~~~powershell
java -version
javac -version
javac --release 17 -encoding UTF-8 .\IntegerLiteralDemo.java
java -cp . IntegerLiteralDemo
~~~

**Kết quả dự kiến:**

~~~text
int default: 42
widened into long: 42
explicit long: 42
big long: 3000000000
hex int: -1
hex long: 4294967295
octal: 15
binary: 10
overflow before widen: -294967296
long before add: 4000000000
cast before add: 4000000000
minimum int: -2147483648
minimum long: -9223372036854775808
byte from int literal: 127
int: 7
long: 7
~~~

Ba điểm nên tự kiểm chứng:

1. `42` và `42L` có thể in **cùng một giá trị**, nhưng mang **kiểu khác nhau**.
2. `0xFFFF_FFFF` khác `0xFFFF_FFFFL` do độ rộng `int`/ `long`.
3. Hai overload `overload(int)` và `overload(long)` cho thấy `var inferredInt = 7` có kiểu `int`, còn `var inferredLong = 7L` có kiểu `long`.

Ví dụ được thiết kế có thể chạy độc lập; đầu ra trên là **kỳ vọng cần đối chiếu sau khi tự chạy**, không phải một khẳng định đã thực thi chương trình trên máy của bạn.

## 8. Vì sao byte hoặc short có thể nhận literal không có hậu tố?

Dù integer literal không hậu tố có kiểu `int`, compiler cho phép **narrowing conversion đặc biệt của biểu thức hằng** trong một số ngữ cảnh gán/khởi tạo, nếu giá trị nằm trong phạm vi kiểu đích:

~~~java
byte a = 127;      // Hợp lệ dù 127 là int literal
short b = 30_000;  // Hợp lệ dù 30000 là int literal
char c = 65;       // Hợp lệ: ký tự 'A'

// byte invalid = 128;       // Lỗi: ngoài phạm vi byte
// short invalid2 = 40_000;  // Lỗi: ngoài phạm vi short
// byte invalid3 = 127L;     // Lỗi: không tự thu hẹp long literal như vậy
~~~

Điều này **không thay đổi kiểu mặc định của literal**: `127` vẫn là `int`. Quy tắc chuyển kiểu khi gán giải thích vì sao đoạn đầu hợp lệ.

Với biến hoặc biểu thức không phải hằng thích hợp, compiler không nhất thiết cho phép thu hẹp:

~~~java
int source = 127;
// byte target = source; // Lỗi vì source là int variable
byte target = (byte) source; // Ép kiểu tường minh; chỉ dùng khi đã kiểm tra miền
~~~

Không dùng cast để “che” một giá trị ngoài phạm vi: cast số nguyên quá lớn xuống kiểu nhỏ có thể cắt bớt bit và làm sai dữ liệu.

## 9. Các lỗi thường gặp và cách sửa đúng

| Tình huống | Vì sao sai hoặc bất ngờ? | Cách sửa |
|---|---|---|
| `long n = 3_000_000_000;` | Decimal int literal quá lớn dù biến đích là long | `3_000_000_000L` |
| `int n = 3_000_000_000L;` | Long literal không tự thu hẹp thành int | Chọn `long` nếu dữ liệu thực sự lớn |
| `long sum = 2_000_000_000 + 2_000_000_000;` | Phép cộng int tràn trước khi gán | Dùng `L` ở một toán hạng, hoặc cast trước phép tính |
| `long n = 9223372036854775808L;` | Vượt `Long.MAX_VALUE` | Chọn miền dữ liệu hợp lệ hoặc `BigInteger` |
| `int n = 08;` | `0` đầu mở đầu cách viết bát phân | Viết `8` cho hệ thập phân |
| `long n = 0xFFFF_FFFF;` | Literal int có bit dấu 1, thành `-1` trước khi mở rộng | `0xFFFF_FFFFL` nếu muốn 4.294.967.295 |
| `int n = 123_L;` | Dấu `_` không được ngay trước hậu tố | `123L` hoặc `1_23L` |
| Dùng `1l` | Hợp lệ nhưng dễ đọc nhầm `l` và `1` | Dùng `1L` |
| `var value = 1;` rồi mong long | `var` suy ra int từ initializer | `var value = 1L;` |

**Phân biệt hai giai đoạn:** một literal nằm ngoài phạm vi hợp lệ thường gây **lỗi biên dịch**; nhưng phép toán trên các literal `int` hợp lệ vẫn có thể **tràn lúc đánh giá** mà không tự ném exception. Với phép cộng hoặc nhân cần phát hiện tràn, xem `Math.addExact` và `Math.multiplyExact` theo yêu cầu dữ liệu.

## 10. Bài tập thực hành kèm đáp án

### Bài A — xác định kiểu và giá trị

Phân loại `100`, `100L`, `0b1010`, `0xFFL`, `010`.

**Đáp án:** `100` là int 100; `100L` là long 100; `0b1010` là int 10; `0xFFL` là long 255; `010` là int 8 (hệ bát phân).

### Bài B — lỗi dù đích là long

~~~java
long population = 4_000_000_000;
~~~

**Đáp án:** lỗi compile vì literal `4_000_000_000` không có hậu tố và vượt miền `int`. Sửa thành `long population = 4_000_000_000L;`.

### Bài C — dự đoán phép nhân

~~~java
long x = 1_500_000_000 * 3;
long y = 1_500_000_000L * 3;
~~~

**Đáp án:** `x = 205032704` (phép nhân int 4.500.000.000 bị tràn theo modulo 2³², sau đó mở rộng sang long); `y = 4500000000`. Nếu phép tính cần đúng 4,5 tỷ, phải làm phép nhân bằng `long` trước.

### Bài D — phân biệt cùng mẫu hex

~~~java
int a = 0xFFFF_FFFF;
long b = 0xFFFF_FFFFL;
~~~

**Đáp án:** `a == -1`, `b == 4294967295L`. Cùng các chữ số hex nhưng khác **độ rộng và bit dấu** của kiểu literal.

### Bài E — sửa ba dòng không hợp lệ

~~~java
// long a = 2147483648;
// int b = 2147483648L;
// long c = 9223372036854775808L;
~~~

**Đáp án gợi ý:** với `a`, thêm `L`. Với `b`, đổi biến sang `long` nếu cần giá trị đó. Với `c`, số vượt miền `long`, nên phải thay đổi yêu cầu dữ liệu, dùng `BigInteger` hoặc xử lý đầu vào ngoài phạm vi — **không có hậu tố số nguyên lớn hơn `L` trong Java**.

## 11. Checklist và kết luận

- [ ] Hiểu literal số nguyên **không `L` có kiểu `int`**, có `L` có kiểu `long`.
- [ ] Không nhầm kiểu của literal với kiểu biến bên trái phép gán.
- [ ] Nhớ giới hạn `int` 32 bit và `long` 64 bit.
- [ ] Biết cách viết `Integer.MIN_VALUE` và `Long.MIN_VALUE` bằng toán tử âm với literal tương ứng.
- [ ] Nhận ra decimal, hex, binary, octal và ảnh hưởng của bit dấu.
- [ ] Dùng `L` hoa; dùng `_` giữa chữ số chỉ để dễ đọc.
- [ ] Biết phép cộng/nhân `int` có thể tràn **trước khi gán sang long**.
- [ ] Hiểu `var x = 1` suy ra int, còn `var x = 1L` suy ra long.
- [ ] Không nhầm quy tắc thu hẹp hằng `byte b = 127;` với kiểu literal byte.
- [ ] Biên dịch và chạy `IntegerLiteralDemo.java`, đối chiếu toàn bộ output.

**Kết luận:** Trong Java, hậu tố **`L` quyết định literal số nguyên có kiểu `long` thay vì `int`**. Kiểu được xác định **trước** khi áp dụng phép gán hoặc phép toán. Viết `L` đúng chỗ không chỉ giúp biểu diễn các số vượt `int`, mà còn đảm bảo những biểu thức cần độ rộng 64 bit được tính bằng `long` thay vì tràn `int` rồi mới được chuyển kiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="literals-003">Literal số thực, ký hiệu số mũ và hậu tố F hoặc D</a>

<details>
<summary>Xem chi tiết</summary>

# Literal số thực, ký hiệu số mũ và hậu tố F hoặc D trong Java 17+

**Floating-point literal** là giá trị số thực viết trực tiếp trong mã Java, chẳng hạn `3.14`, `2e3` hoặc `0.5F`. Nó khác literal số nguyên `2` về **kiểu**, cách biểu diễn và phép tính.

Trong Java 17, literal số thực **không có hậu tố** mặc định là `double`. Muốn ghi rõ một literal thuộc kiểu `float`, thêm hậu tố `F` hoặc `f`; hậu tố `D` hoặc `d` xác nhận kiểu `double` nhưng **không bắt buộc**.

## 1. Nhận diện cú pháp

| Literal | Kiểu | Giá trị toán học | Ghi chú |
| --- | --- | --- | --- |
| `3.14` | `double` | 3,14 | Không có hậu tố |
| `3.14D` | `double` | 3,14 | `D` viết tường minh |
| `3.14f` | `float` | 3,14 | `f`/`F` chỉ định kiểu float |
| `2e3` | `double` | 2 × 10³ = 2000 | `e`/`E` là ký hiệu số mũ thập phân |
| `6.02E-3` | `double` | 6,02 × 10⁻³ | Số mũ âm |
| `1.25e+2F` | `float` | 125 | Số mũ có dấu `+`, hậu tố `F` |
| `.5` | `double` | 0,5 | Được phép bỏ chữ số trước dấu chấm |
| `5.` | `double` | 5,0 | Được phép bỏ chữ số sau dấu chấm |
| `1_000.5` | `double` | 1000,5 | `_` giúp nhóm chữ số tại vị trí hợp lệ |
| `5` | `int` | 5 | Literal **số nguyên**, không phải số thực |

Ký hiệu `e`/**`E`** ở đây có nghĩa “nhân với `10` lũy thừa số nguyên”, **không phải hằng số Euler**.

Cấu trúc điển hình:

~~~text
phần định trị [e hoặc E] [dấu + hoặc -] số mũ [F/f/D/d]
      1.25        e          -             3        F
~~~

Ví dụ `1.25e-3F` là literal kiểu `float` có giá trị toán học `0.00125`, được làm tròn tới độ chính xác của `float`. Nếu dùng ký hiệu `e/E` thì phần số mũ phải có chữ số; `1e` và `1e+` **không hợp lệ**.

Java cũng có literal số thực hệ 16 như `0x1.8p1` (bằng `3.0`, kiểu `double`): `p/P` biểu diễn số mũ **cơ số 2**, không phải `e/E`. Đây là cú pháp riêng, không cần dùng để hiểu ví dụ thập phân bên dưới.

## 2. Ví dụ Java 17 chạy được

Tạo tệp **`FloatingLiteralDemo.java`** (tên tệp khớp với `public class`):

~~~java
public class FloatingLiteralDemo {
    public static void main(String[] args) {
        double regular = 12.5;
        double large = 1.25e3;
        double small = 6.02E-3;
        float withF = 2.5F;
        double withD = 2.5D;
        double leadingDot = .5;
        double trailingDot = 5.;
        double hex = 0x1.8p1;

        double fromIntegerDivision = 5 / 2;
        double fromFloatingDivision = 5 / 2.0;

        System.out.println("regular=" + regular);
        System.out.println("large=" + large);
        System.out.println("small=" + small);
        System.out.println("withF=" + withF);
        System.out.println("withD=" + withD);
        System.out.println("leadingDot=" + leadingDot);
        System.out.println("trailingDot=" + trailingDot);
        System.out.println("hex=" + hex);
        System.out.println("integerDivision=" + fromIntegerDivision);
        System.out.println("floatingDivision=" + fromFloatingDivision);
        System.out.println("exactEquality=" + (0.1 + 0.2 == 0.3));
    }
}
~~~

Mở PowerShell (Windows) hoặc terminal Bash (Linux) tại thư mục chứa tệp, rồi dùng **cùng các lệnh**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 FloatingLiteralDemo.java
java FloatingLiteralDemo
~~~

**Kết quả dự kiến:**

~~~text
regular=12.5
large=1250.0
small=0.00602
withF=2.5
withD=2.5
leadingDot=0.5
trailingDot=5.0
hex=3.0
integerDivision=2.0
floatingDivision=2.5
exactEquality=false
~~~

**Tại sao?** `1.25e3` bằng `1250.0` dù lệnh in không nhất thiết giữ nguyên dạng số mũ. `5 / 2` dùng phép chia hai `int`, kết quả nguyên `2` rồi mới đổi thành `double` `2.0`; ngược lại, `5 / 2.0` thực hiện chia số thực, cho `2.5`. Phép so sánh cuối là `false` vì một số phân số thập phân (như `0.1`) **không được biểu diễn chính xác** bằng số thực nhị phân IEEE 754.

## 3. `float` so với `double`: hậu tố quyết định kiểu literal

~~~java
double a = 1.5;  // Đúng: literal mặc định là double.
double b = 1.5D; // Đúng: thêm D tường minh.
float c = 1.5F;  // Đúng: literal kiểu float.
double d = 1.5F; // Đúng: float được mở rộng thành double.

// float e = 1.5; // Sai biên dịch: double không tự thu hẹp thành float.
~~~

| Tiêu chí | `float` | `double` |
| --- | --- | --- |
| Kích thước | 32 bit | 64 bit |
| Độ chính xác xấp xỉ | 6–7 chữ số thập phân có nghĩa | 15–16 chữ số thập phân có nghĩa |
| Hậu tố literal | `f` hoặc `F` | `d`/`D`, hoặc bỏ hậu tố |
| Ví dụ | `0.1F` | `0.1`, `0.1D` |

Chuyển một literal `double` sang `float` bằng cast, ví dụ `(float) 1.5`, có thể hợp lệ nhưng **có nguy cơ mất độ chính xác hoặc vượt miền**. Khi chủ ý ghi literal `float`, dùng `F` thường rõ nghĩa hơn.

Một literal âm như `-2.5F` được phân tích theo toán tử **đổi dấu** `-` áp dụng lên literal `2.5F`; dấu trừ không phải phần độc lập của token literal. Điều này giúp hiểu vì sao biểu thức và literal có thể có quy tắc riêng.

## 4. Giới hạn, quy tắc gạch dưới và sai lầm thường gặp

| Mã hoặc tình huống | Đúng/sai | Giải thích |
| --- | --- | --- |
| `double rate = 1e-3;` | Đúng | `0.001`, kiểu double |
| `float rate = 1e-3F;` | Đúng | Hậu tố `F`, kiểu float |
| `float rate = 1e-3;` | **Lỗi biên dịch** | Vế phải là double; không tự thu hẹp |
| `double n = 1e;` | **Lỗi biên dịch** | Thiếu số mũ |
| `double n = 1_.0;` | **Lỗi biên dịch** | Gạch dưới sát dấu chấm không hợp lệ |
| `double n = 1_000.0;` | Đúng | Nhóm chữ số hợp lệ |
| `double n = 1e400;` | **Lỗi biên dịch** | Literal double hữu hạn vượt miền biểu diễn |
| `float n = 1e40F;` | **Lỗi biên dịch** | Literal float hữu hạn vượt miền biểu diễn |
| `double n = 5 / 2;` | Đúng cú pháp, có thể sai ý định | Kết quả `2.0`, không phải `2.5` |

Một literal số thực **khác 0** mà nhỏ tới mức làm tròn thành 0 trong kiểu đã chọn cũng không hợp lệ khi viết trực tiếp; ví dụ `1e-400` vượt phía dưới độ lớn biểu diễn được của `double`. Nhưng các **phép tính lúc chạy** vẫn có thể tạo `Infinity`, `NaN`, số *subnormal* hoặc giá trị 0 do underflow; đó là vấn đề khác với kiểm tra literal lúc biên dịch.

### Khi nào không nên dùng `float`/`double`?

Các kiểu này biểu diễn số thực **xấp xỉ**, phù hợp tính toán khoa học, đồ họa, thống kê khi chấp nhận sai số số học. Với **tiền và phép tính yêu cầu thập phân chính xác**, nên cân nhắc `BigDecimal` (khởi tạo từ **chuỗi** khi cần giữ chính xác giá trị thập phân), hoặc đơn vị tiền nguyên theo yêu cầu bài toán.

Ví dụ `new java.math.BigDecimal("0.1")` giữ chính xác giá trị thập phân `0.1` theo mô hình `BigDecimal`, khác với `new BigDecimal(0.1)` vốn nhận giá trị `double` đã xấp xỉ.

## 5. Bài tập tự kiểm tra

1. **Xác định kiểu:** `7.5`, `7.5f`, `2e2`, `2e2D`.  
   *Đáp án:* Lần lượt `double`, `float`, `double`, `double`.
2. **Đổi sang thập phân thông thường:** `3.2e2` và `6.5E-2F`.  
   *Đáp án:* Giá trị toán học lần lượt `320` và `0.065`; literal thứ hai có kiểu `float`.
3. **Sửa lỗi:** `float x = 0.25;`.  
   *Đáp án:* `float x = 0.25F;`, hoặc chọn `double x = 0.25;`.
4. **Dự đoán:** `double x = 7 / 2;` và `double y = 7 / 2.0;`.  
   *Đáp án:* `x == 3.0`, `y == 3.5`.
5. **Thử độ chính xác:** chạy `System.out.println(0.1 + 0.2 == 0.3);`.  
   *Đáp án:* `false`; không dùng so sánh `==` một cách máy móc cho các kết quả tính toán số thực xấp xỉ.

**Ghi nhớ:** Trong Java 17, literal số thực mặc định thuộc `double`, hậu tố `F/f` đổi thành `float`, `D/d` chỉ ghi rõ `double`. Ký hiệu `e/E` nhân với lũy thừa của 10; kiểu của **toán hạng ngay tại phép tính** quyết định cách chia và độ chính xác, chứ không chỉ kiểu biến ở vế trái.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="literals-004">Dấu gạch dưới trong literal số và vị trí không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu gạch dưới trong literal số và vị trí không hợp lệ

> **Java core — language-basics / Literal**  
> **Yêu cầu:** JDK 17+; một chương trình Java chạy độc lập, không dùng thư viện ngoài.  
> **Mục tiêu:** Biết khi nào được viết dấu `_` trong numeric literal, nhận ra các vị trí bị cấm và phân biệt cú pháp literal tại thời điểm biên dịch với chuỗi được parse khi chương trình chạy.

## 1. Dấu `_` dùng để làm gì?

Java cho phép chèn dấu gạch dưới giữa các chữ số trong **literal số** để dễ đọc, chẳng hạn:

~~~java
int population = 1_000_000;
long distance = 9_000_000_000L;
double rate = 12.3_4;
~~~

Dấu `_` ở đây **không thay đổi giá trị hoặc kiểu dữ liệu**. `1_000_000` và `1000000` là cùng một giá trị `int`; `9_000_000_000L` vẫn là literal `long` vì có hậu tố `L`.

| Cách viết | Giá trị | Vì sao hợp lệ? |
| --- | ---: | --- |
| `1_000_000` | 1000000 | Nhóm các chữ số thập phân |
| `0b1111_0000` | 240 | Nhóm các bit sau tiền tố nhị phân `0b` |
| `05_2` | 42 | Nhóm chữ số của literal bát phân (bắt đầu bằng `0`) |
| `0xCA_FE` | 51966 | Nhóm chữ số hệ 16 |
| `1__000` | 1000 | **Nhiều** dấu `_` liên tiếp giữa hai chữ số vẫn hợp lệ |
| `12.3_4` | 12.34 | Nhóm chữ số ở phần thập phân của floating-point literal |
| `1.2e1_0` | 1.2 × 10¹⁰ | Dấu `_` nằm giữa các chữ số phần số mũ |

Quy tắc thực dụng: **chèn `_` ở giữa các chữ số của cùng một phần literal, không đặt nó tại ranh giới tiền tố, dấu chấm, ký hiệu số mũ hay hậu tố kiểu.**

## 2. Các vị trí không hợp lệ

| Viết sai | Nguyên nhân | Viết đúng nếu giữ ý định |
| --- | --- | --- |
| `123_` | Kết thúc literal bằng dấu `_` | `123` hoặc `1_23` |
| `0b_1010` | Ngay sau tiền tố `0b` | `0b1010` hoặc `0b10_10` |
| `0x_FF` | Ngay sau tiền tố `0x` | `0xFF` hoặc `0xF_F` |
| `1_.25` | Ngay trước dấu chấm thập phân | `1.25` |
| `1._25` | Ngay sau dấu chấm thập phân | `1.25` hoặc `1.2_5` |
| `1e_10` | Ngay sau ký hiệu `e` của số mũ | `1e10` hoặc `1e1_0` |
| `1e+_10` | Ngay sau dấu `+` thuộc số mũ | `1e+10` |
| `123_L` | Ngay trước hậu tố kiểu `L` | `123L` hoặc `1_23L` |
| `12_f` | Ngay trước hậu tố `f` | `12f` hoặc `1_2f` |

Ngoài ra, `_123` là một **identifier có thể hợp lệ**, **không phải** literal số 123. Viết `int n = _123;` sẽ tìm **biến tên `_123`**, chứ không đưa số 123 vào `n`. Dấu `_` đứng **một mình** lại không được dùng làm identifier trong Java 17.

**Phân biệt quan trọng:** `1__000` **hợp lệ**, không nên suy diễn rằng cứ có hai dấu gạch dưới liền nhau là sai. Vị trí tương đối với **chữ số, dấu phân cách và hậu tố** mới là điều quyết định.

## 3. Áp dụng cho những loại literal số nào?

Cú pháp dấu `_` dùng được cho **integer literal** và **floating-point literal** ở những vị trí hợp lệ:

- Số nguyên: thập phân `4_200`, nhị phân `0b1010_0101`, bát phân `07_52`, thập lục phân `0xAB_CD`, `long` như `1_000L`.
- Số thực: `3.14_15`, `1_2.5_0f`, hoặc số mũ `6.02e2_3`.
- Số thực hex cũng có quy tắc riêng về chữ số và dấu mũ `p`, ví dụ `0x1.8p1_0` là literal `double` hợp lệ (giá trị 1536.0).

**Không được chèn `_` tùy tiện vào một tiền tố/cú pháp rồi nghĩ rằng compiler sẽ tự bỏ nó.** Ví dụ `0x_FF` và `1_.0` không là cách viết số hợp lệ.

Chú ý rằng `-1_000` có **toán tử dấu trừ** đứng trước literal `1_000`; dấu `_` vẫn chỉ phục vụ nhóm chữ số. Dấu `L`, `f`, `d` xác định loại literal theo quy tắc tương ứng, không phải chữ số để kẹp thêm `_` sát vào.

## 4. Chương trình Java 17+ chạy được

Tạo file **NumericUnderscoreDemo.java** và chép toàn bộ mã:

~~~java
public class NumericUnderscoreDemo {
    public static void main(String[] args) {
        int decimal = 4_2;
        int binary = 0b1111_0000;
        int octal = 05_2;
        int hex = 0xCA_FE;
        int repeated = 1__000;
        long large = 9_000_000_000L;
        double fraction = 12.3_4;

        System.out.println("decimal=" + decimal);
        System.out.println("binary=" + binary);
        System.out.println("octal=" + octal);
        System.out.println("hex=" + hex);
        System.out.println("repeated=" + repeated);
        System.out.println("large=" + large);
        System.out.println("fraction=" + fraction);
        System.out.println("same42=" + (decimal == octal));

        try {
            Integer.parseInt("1_000");
            System.out.println("parse=OK");
        } catch (NumberFormatException exception) {
            System.out.println("parse=ERROR");
        }
    }
}
~~~

Trong terminal (Bash, PowerShell hoặc Command Prompt), từ thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 NumericUnderscoreDemo.java
java NumericUnderscoreDemo
~~~

**Đầu ra mong đợi:**

~~~text
decimal=42
binary=240
octal=42
hex=51966
repeated=1000
large=9000000000
fraction=12.34
same42=true
parse=ERROR
~~~

Ở đây `05_2` là số **bát phân**: \(5 \times 8 + 2 = 42\). `0xCA_FE` là số hex `CAFE` (51966 thập phân). Cả hai chỉ là **cách ghi giá trị trong mã nguồn**, không phải chuỗi đặc biệt được in nguyên dạng.

## 5. Compiler và chuỗi đầu vào không dùng cùng quy tắc

Đây là điểm phân biệt thiết yếu:

~~~java
int fromSource = 1_000;             // Hợp lệ: compiler hiểu literal Java.
int fromText = Integer.parseInt("1000"); // Hợp lệ: parse chuỗi toàn chữ số.
~~~

Nhưng:

~~~java
Integer.parseInt("1_000"); // Ném NumberFormatException khi chạy.
~~~

`Integer.parseInt` không tự loại `_` như compiler Java xử lý ở numeric literal. Nếu ứng dụng muốn người dùng nhập `1_000`, bạn phải định nghĩa **quy tắc kiểm tra định dạng**, rồi xử lý đầu vào trước khi chuyển kiểu. Đừng chỉ xóa mọi dấu `_` mà không kiểm tra, vì như vậy các chuỗi sai như `_123` hoặc `123_` có thể bị biến thành số hợp lệ ngoài ý định.

Tương tự, literal `1_000L` chứa hậu tố `L` của **source code**, nhưng `Long.parseLong("1_000L")` không chấp nhận chuỗi ấy.

## 6. Các bẫy cần kiểm tra

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Có `_` thì kiểu số thành String.” | Sai. Numeric literal vẫn có kiểu số tương ứng. |
| “`1__000` luôn sai.” | Sai. Dấu `_` liên tiếp giữa chữ số được cho phép. |
| “`0x_FF` tương đương `0xFF`.” | Sai. Không được chèn `_` ngay sau tiền tố. |
| “Xuống dòng hoặc khoảng trắng cạnh `_` cũng được.” | Sai khi làm vỡ một literal: `1_ 000` không còn là numeric literal hợp lệ. |
| “`123_L` là `long` hợp lệ.” | Sai. Hậu tố `L` phải theo sau phần chữ số đúng cú pháp. |
| “`parseInt("1_000")` hoạt động như compiler.” | Sai. `parseInt` không nhận dấu `_` làm nhóm chữ số. |

**Thử nghiệm có chủ đích:** chọn một ví dụ sai ở bảng mục 2, đặt vào một dòng riêng của `NumericUnderscoreDemo.java`, rồi chạy `javac` để quan sát lỗi. Sau đó sửa lại và biên dịch lần nữa. Không thêm toàn bộ các ví dụ sai vào chương trình chính, vì chương trình sẽ không thể biên dịch.

## 7. Bài tập ngắn

**Bài 1 — Đúng hay sai?** Phân loại các literal `1_234`, `1__234`, `1_`, `0b10_01`, `0b_1001`, `0xA_B`, `0x_AB`, `3.1_4` và `3._14`. Nêu *vị trí* quyết định kết quả, không chỉ ghi “sai cú pháp”.

**Bài 2 — Giữ nguyên giá trị.** Viết lại `1000000000L`, `0b11110000` và `0xCAFEBABE` sao cho dễ đọc bằng `_` mà giá trị không đổi. Thử in từng giá trị để tự kiểm tra.

**Bài 3 — Source so với runtime.** Vì sao `Integer.parseInt("1000")` thành công còn `Integer.parseInt("1_000")` thất bại? Nếu làm công cụ đọc cấu hình chấp nhận `_`, bạn sẽ kiểm tra vị trí gạch dưới như thế nào?

**Bài 4 — Debug.** Sửa hai literal sai `1e_10` và `9_000_L`, giải thích vì sao ký hiệu exponent và suffix không phải vị trí được phép đặt dấu `_`.

### Ghi nhớ

**Dấu `_` là ký hiệu giúp con người đọc numeric literal rõ hơn, không mang giá trị và không tự mở rộng cú pháp parser cho chuỗi runtime.** Đặt nó **giữa chữ số hợp lệ**, có thể nhiều dấu liên tiếp; **không** đặt ở đầu/cuối literal hoặc sát tiền tố, dấu chấm, ký hiệu số mũ hay hậu tố kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="literals-005">Literal boolean</a>

<details>
<summary>Xem chi tiết</summary>

# Literal boolean trong Java 17+

## 1. Literal boolean là gì?

**Boolean literal** là giá trị đúng hoặc sai được viết trực tiếp trong mã nguồn Java. Java chỉ có **hai boolean literals**:

- `true`: giá trị logic đúng.
- `false`: giá trị logic sai.

Cả hai có kiểu nguyên thủy **`boolean`**. Chúng phải được viết **chính xác bằng chữ thường**. `True`, `FALSE` hay `TRUE` không phải boolean literals.

~~~java
boolean available = true;
boolean soldOut = false;
~~~

Trong đoạn trên, `boolean` là từ khóa chỉ kiểu; `available` và `soldOut` là các định danh; `true` và `false` là **literal**, không phải từ khóa hoặc tên biến.

Đặc biệt, `true` và `false` không được dùng làm tên định danh:

~~~java
// int true = 1;  // Lỗi biên dịch: true không thể là tên biến
// int false = 0; // Lỗi biên dịch
~~~

**Đừng nhầm literal với chuỗi ký tự:** `"true"` và `"false"` có kiểu `String`, không phải `boolean`.

## 2. Kiểu boolean chỉ có hai giá trị

Một biến kiểu nguyên thủy `boolean` chỉ có thể nhận `true` hoặc `false`, hoặc một biểu thức trả về `boolean`:

~~~java
boolean connected = true;
boolean ready = connected;
boolean adult = 20 >= 18;
boolean eligible = connected && adult;
~~~

Các khai báo sau **không hợp lệ**:

~~~java
// boolean a = 1;       // int không tự chuyển thành boolean
// boolean b = 0;       // int không tự chuyển thành boolean
// boolean c = "true";  // String không tự chuyển thành boolean
// boolean d = null;    // null không gán được cho primitive boolean
~~~

Khác với một số ngôn ngữ khác, Java **không có quy tắc tự xem 0 là false và số khác 0 là true**. Vì vậy:

~~~java
// if (1) { } // Không biên dịch: điều kiện if phải có kiểu boolean
if (true) {
    System.out.println("Nhanh dung");
}
~~~

Đoạn `if (true)` in `Nhanh dung`; `if (false)` có thân hợp lệ nhưng không thực thi thân khi chạy.

## 3. Boolean literals và biểu thức boolean

Literal cung cấp giá trị trực tiếp; còn biểu thức có thể **tính ra** giá trị boolean từ toán hạng khác.

| Biểu thức | Kiểu | Kết quả | Lý do |
|---|---|---|---|
| `true` | `boolean` | `true` | Literal |
| `false` | `boolean` | `false` | Literal |
| `5 > 3` | `boolean` | `true` | So sánh số |
| `10 == 7` | `boolean` | `false` | Hai giá trị khác nhau |
| `!false` | `boolean` | `true` | Phủ định |
| `true && false` | `boolean` | `false` | Phép AND |
| `true \|\| false` | `boolean` | `true` | Phép OR |
| `true ^ true` | `boolean` | `false` | XOR: hai giá trị giống nhau |

Biến cũng có thể giữ kết quả biểu thức:

~~~java
int score = 75;
boolean passed = score >= 50;
boolean excellent = score >= 90;

System.out.println(passed);    // true
System.out.println(excellent); // false
~~~

Không phải mọi giá trị `boolean` đều phải được viết thành literal: một phép so sánh, method như `String.isBlank()` hoặc lời gọi method do bạn viết cũng có thể sinh ra `boolean`.

## 4. Các phép toán logic trên boolean

Ba toán tử dùng thường xuyên nhất:

- `!` (NOT) đảo `true` thành `false` và ngược lại.
- `&&` (AND) chỉ trả `true` nếu **cả hai vế** là `true`.
- `||` (OR) trả `true` nếu **ít nhất một vế** là `true`.

| `a` | `b` | `!a` | `a && b` | `a \|\| b` | `a ^ b` |
|---|---|---|---|---|---|
| false | false | true | false | false | false |
| false | true | true | false | true | true |
| true | false | false | false | true | true |
| true | true | false | true | true | false |

`^` là phép **XOR** trên hai boolean: đúng khi **hai toán hạng khác nhau**.

Khi kết hợp nhiều điều kiện, dùng ngoặc để diễn đạt ý định rõ ràng:

~~~java
boolean authenticated = true;
boolean active = true;
boolean blocked = false;

boolean canEnter = authenticated && active && !blocked;
System.out.println(canEnter); // true
~~~

Tránh những biểu thức quá dài, khó kiểm tra. Đặt tên biến trung gian giúp phần logic sát yêu cầu và dễ đọc hơn.

## 5. `&&` và `||` có tính ngắn mạch

**Short-circuit evaluation** nghĩa là Java bỏ qua vế phải khi vế trái đã đủ quyết định kết quả:

- `false && something()`: không gọi `something()` vì kết quả chắc chắn là `false`.
- `true || something()`: không gọi `something()` vì kết quả chắc chắn là `true`.

Một lợi ích là kiểm tra an toàn trước khi truy cập giá trị có thể null:

~~~java
String name = null;
boolean hasName = name != null && !name.isBlank();
System.out.println(hasName); // false, không ném lỗi
~~~

Sau khi `name != null` là `false`, Java **không đánh giá** `name.isBlank()`.

Trên hai toán hạng boolean, `&` và `|` cũng thực hiện AND/OR, nhưng **đánh giá cả hai vế**, không ngắn mạch. Vì vậy thay `&&` thành `&` trong ví dụ null ở trên có thể gây `NullPointerException`.

### Tự nhìn thấy vế phải bị bỏ qua

~~~java
static boolean trace(String message, boolean result) {
    System.out.println(message);
    return result;
}

// Trong một method:
boolean first = false && trace("RIGHT_1", true);
boolean second = true || trace("RIGHT_2", false);
System.out.println(first);  // false
System.out.println(second); // true
~~~

Hai chuỗi `RIGHT_1` và `RIGHT_2` **không được in** vì các lời gọi `trace` ở vế phải không diễn ra. Điều này quan trọng khi vế phải là lời gọi method tốn tài nguyên hoặc có tác dụng phụ.

**Khuyến nghị:** đừng phụ thuộc vào tác dụng phụ của vế phải `&&`/`||` khi logic cần nó **luôn chạy**. Hãy tách những hành động bắt buộc thành câu lệnh riêng.

## 6. Ví dụ Java 17 chạy được

Tạo tệp **`BooleanLiteralDemo.java`** với toàn bộ nội dung sau:

~~~java
public final class BooleanLiteralDemo {
    private BooleanLiteralDemo() {}

    static final class Settings {
        boolean enabled; // Field primitive có giá trị mặc định false
    }

    static boolean canAccess(
            boolean loggedIn, boolean verified, boolean blocked) {
        return loggedIn && verified && !blocked;
    }

    static boolean hasVisibleText(String text) {
        return text != null && !text.isBlank();
    }

    static boolean trace(String label, boolean value) {
        System.out.println("evaluated: " + label);
        return value;
    }

    public static void main(String[] args) {
        boolean enabled = true;
        boolean paused = false;

        System.out.println("literal true: " + enabled);
        System.out.println("literal false: " + paused);
        System.out.println("negation: " + !enabled);
        System.out.println("comparison: " + (5 > 3));
        System.out.println("allow: " + canAccess(true, true, false));
        System.out.println("blocked: " + canAccess(true, true, true));

        System.out.println("null valid: " + hasVisibleText(null));
        System.out.println("blank valid: " + hasVisibleText("   "));
        System.out.println("name valid: " + hasVisibleText("Lan"));

        boolean skippedAnd = false && trace("AND right", true);
        boolean skippedOr = true || trace("OR right", false);
        System.out.println("short AND: " + skippedAnd);
        System.out.println("short OR: " + skippedOr);

        System.out.println("parse TRUE: "
                + Boolean.parseBoolean("TRUE"));
        System.out.println("parse yes: "
                + Boolean.parseBoolean("yes"));
        System.out.println("default field: " + new Settings().enabled);
    }
}
~~~

Biên dịch và chạy bằng **JDK 17 hoặc mới hơn**, từ thư mục chứa file.

**Bash (Linux/macOS):**

~~~bash
java -version
javac -version
javac --release 17 -encoding UTF-8 BooleanLiteralDemo.java
java -cp . BooleanLiteralDemo
~~~

**PowerShell (Windows):**

~~~powershell
java -version
javac -version
javac --release 17 -encoding UTF-8 .\BooleanLiteralDemo.java
java -cp . BooleanLiteralDemo
~~~

**Đầu ra kỳ vọng:**

~~~text
literal true: true
literal false: false
negation: false
comparison: true
allow: true
blocked: false
null valid: false
blank valid: false
name valid: true
short AND: false
short OR: true
parse TRUE: true
parse yes: false
default field: false
~~~

Hãy chú ý: **không có dòng `evaluated:` nào** vì hai lời gọi `trace` đều nằm trong vế phải được ngắn mạch bỏ qua. Kết quả của `hasVisibleText(null)` cũng an toàn nhờ `&&`.

Đầu ra trên là giá trị dự kiến để người học tự chạy đối chiếu. Không cần Maven, Gradle hoặc thư viện bên ngoài.

## 7. Boolean literal khác String và Boolean wrapper

Ba cách viết này có ý nghĩa khác nhau:

~~~java
boolean primitive = true;
String text = "true";
Boolean wrapper = Boolean.TRUE;
~~~

| Giá trị | Kiểu | Ý nghĩa |
|---|---|---|
| `true` | `boolean` | Boolean literal |
| `"true"` | `String` | Chuỗi bốn ký tự |
| `Boolean.TRUE` | `Boolean` | Hằng field chứa wrapper object |
| `Boolean.parseBoolean("true")` | `boolean` | Kết quả chuyển đổi chuỗi |

Với đầu vào dạng chuỗi, có thể dùng `Boolean.parseBoolean(text)`. Cần nắm rõ hợp đồng của API:

~~~java
System.out.println(Boolean.parseBoolean("true"));  // true
System.out.println(Boolean.parseBoolean("TRUE"));  // true
System.out.println(Boolean.parseBoolean("false")); // false
System.out.println(Boolean.parseBoolean("yes"));   // false
System.out.println(Boolean.parseBoolean(null));    // false
~~~

`parseBoolean` chỉ trả `true` nếu chuỗi biểu diễn `"true"` theo so sánh không phân biệt chữ hoa/thường. Nó **không báo lỗi** khi gặp chuỗi khác, mà trả `false`. Nếu ứng dụng bắt buộc người dùng nhập chính xác `true` hoặc `false`, cần kiểm tra input hợp lệ **trước hoặc ngoài** lời gọi `parseBoolean`.

Lớp wrapper `Boolean` là **kiểu tham chiếu**, nên một biến `Boolean` có thể giữ `null`; primitive `boolean` thì không:

~~~java
Boolean optional = null;
// boolean invalid = null; // Không biên dịch
~~~

Nếu dùng `if (optional)`, Java cố **unbox** `Boolean` sang `boolean`; khi `optional` là `null`, lệnh này ném `NullPointerException`. Nếu chỉ muốn kiểm tra giá trị là true, có thể dùng:

~~~java
Boolean optional = null;
boolean on = Boolean.TRUE.equals(optional); // false
~~~

Không tự xem `null` là “literal boolean thứ ba”: kiểu nguyên thủy `boolean` chỉ có `true` và `false`.

## 8. Giá trị mặc định và biến cục bộ

**Field** kiểu primitive `boolean` được Java khởi tạo mặc định thành `false` khi đối tượng được tạo (hoặc trong quá trình chuẩn bị class đối với static field).

~~~java
class Flags {
    boolean active;
}

Flags flags = new Flags();
System.out.println(flags.active); // false
~~~

Tuy nhiên, **biến cục bộ trong phương thức** phải được gán chắc chắn trước khi đọc:

~~~java
// Trong một method:
// boolean ready;
// System.out.println(ready); // Lỗi biên dịch: có thể chưa khởi tạo
~~~

Vì vậy cần phân biệt:

- Literal `false` là giá trị viết trong source.
- Giá trị mặc định `false` của field là quy tắc khởi tạo của Java.
- Biến cục bộ `boolean` không tự cho phép đọc “false mặc định” khi chưa gán.

## 9. Những lỗi dễ mắc khi dùng boolean literals

| Mã hoặc hành vi | Vấn đề | Cách viết đúng |
|---|---|---|
| `boolean ready = True;` | `True` không phải literal; có thể lỗi tên chưa khai báo | `boolean ready = true;` |
| `boolean ready = 1;` | Java không coi `1` là true | Dùng `true` hoặc điều kiện tạo boolean |
| `if (1) { }` | Điều kiện không có kiểu boolean | `if (count > 0) { }` |
| `boolean ready = "false";` | Chuỗi khác literal boolean | Chuyển đổi/validate input |
| `boolean ready = null;` | Primitive không nhận null | Dùng true/false phù hợp |
| `if (flag = true)` | **Biên dịch được**, nhưng là phép gán, làm điều kiện thành true | `if (flag)` khi muốn kiểm tra giá trị |
| `if (flag == true)` | Thường dư thừa | `if (flag)` |
| `if (flag == false)` | Có thể thay bằng cách đọc dễ hơn | `if (!flag)` |
| `Boolean.parseBoolean("yes")` cho kết quả false | API không hiểu “yes” là true | Validation nếu chỉ chấp nhận true/false |
| `Boolean b = null; if (b)` | NPE lúc unbox | Xử lý null có chủ đích |

### Lỗi nguy hiểm: gán trong điều kiện

~~~java
boolean approved = false;
if (approved = true) {
    System.out.println("APPROVED");
}
System.out.println(approved); // true
~~~

Đoạn này **hợp lệ** vì biểu thức gán `approved = true` có giá trị boolean là `true`; nhưng nó **thay đổi biến** và có thể làm sai logic nghiệp vụ.

Nếu chỉ muốn kiểm tra trạng thái hiện có, viết `if (approved)`. Khi đọc lại code, chú ý phân biệt toán tử gán `=` với so sánh `==`.

## 10. Khi nào nên dùng literal trực tiếp?

Hai literal hữu ích khi chúng diễn tả đúng ý định:

~~~java
boolean featureEnabled = false; // Tạm vô hiệu hóa một tính năng
boolean hasPermission = true;   // Cho phép theo một điều kiện đã kiểm chứng
~~~

Tuy nhiên, không dùng `true` hoặc `false` thay cho những phép kiểm tra bắt buộc:

~~~java
// Sai về nghiệp vụ nếu không kiểm tra thực tế:
boolean authenticated = true;

// Tốt hơn: kết quả từ lời gọi kiểm chứng có nghĩa
// boolean authenticated = verifyCredentials(user, password);
~~~

Đối với method trả boolean, đặt tên thể hiện một câu hỏi rõ ràng:

~~~java
static boolean isAdult(int age) {
    return age >= 18;
}

static boolean isEven(int n) {
    return n % 2 == 0;
}
~~~

Trong biểu thức `return age >= 18;`, không cần viết dạng dài:

~~~java
// Không cần thiết:
if (age >= 18) {
    return true;
} else {
    return false;
}
~~~

Trực tiếp trả biểu thức boolean ngắn hơn và thường rõ ý hơn. Tuy vậy, nếu từng nhánh có lý do nghiệp vụ khác nhau hoặc cần xử lý thêm, `if/else` vẫn có thể phù hợp.

## 11. Bài tập có đáp án

### Bài A — phân loại literal

Cho `true`, `false`, `"true"`, `Boolean.TRUE`, `True`.

**Đáp án:** chỉ `true` và `false` là boolean literals; `"true"` là String literal; `Boolean.TRUE` là field của wrapper class; `True` không phải literal và chỉ hợp lệ nếu có một định danh tương ứng được khai báo.

### Bài B — dự đoán biểu thức

~~~java
boolean a = true;
boolean b = false;
System.out.println(a && b);
System.out.println(a || b);
System.out.println(!a);
System.out.println(a ^ b);
~~~

**Đáp án:**

~~~text
false
true
false
true
~~~

### Bài C — ngắn mạch bảo vệ null

~~~java
String value = null;
boolean ok = value != null && value.length() > 2;
System.out.println(ok);
~~~

**Đáp án:** in `false`, không ném `NullPointerException`, vì vế phải `&&` không được đánh giá. Nếu thay bằng boolean `&`, vế phải bị đánh giá và lời gọi `length()` gây lỗi.

### Bài D — kết quả parseBoolean

Dự đoán `Boolean.parseBoolean("TRUE")`, `Boolean.parseBoolean("1")`, `Boolean.parseBoolean("false")`.

**Đáp án:** lần lượt `true`, `false`, `false`. API không mặc định coi `1` là true.

### Bài E — nhận diện lỗi logic

~~~java
boolean paid = false;
if (paid = true) {
    System.out.println("SHIP");
}
~~~

**Đáp án:** in `SHIP` vì phép gán làm `paid` thành `true`. Nếu muốn chỉ giao hàng khi biến đã true từ trước, sửa thành `if (paid)` (và cần quy trình xác minh thanh toán thật).

## 12. Checklist tự đánh giá

- [ ] Biết chỉ có hai boolean literals trong Java: `true` và `false`.
- [ ] Viết đúng chữ thường và hiểu chúng **không phải từ khóa**.
- [ ] Không nhầm `boolean`, `Boolean`, `"true"` và literal `true`.
- [ ] Hiểu phép so sánh và toán tử logic tạo biểu thức kiểu `boolean`.
- [ ] Dùng `!`, `&&`, `||`, `^` chính xác.
- [ ] Giải thích được vì sao `&&`/`||` không phải lúc nào cũng đánh giá vế phải.
- [ ] Biết `Boolean.parseBoolean` không tự báo lỗi với chuỗi lạ.
- [ ] Biết field boolean mặc định false nhưng local variable phải được gán trước khi đọc.
- [ ] Tránh viết `if (flag = true)` khi muốn kiểm tra giá trị.
- [ ] Biên dịch/chạy `BooleanLiteralDemo.java` trên JDK 17+ và đối chiếu kết quả.

**Kết luận:** `true` và `false` là hai literal kiểu `boolean` dùng để biểu diễn đúng/sai trong mã Java. Kiểu boolean giúp diễn tả điều kiện một cách tường minh; Java không tự đổi số nguyên hoặc chuỗi thành boolean. Khi sử dụng literals trong biểu thức, cần hiểu các toán tử logic, tính ngắn mạch và khác biệt giữa `boolean` primitive với `Boolean` wrapper để tránh lỗi điều kiện và xử lý null sai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="literals-006">Literal ký tự và literal chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Literal ký tự và literal chuỗi trong Java 17+

Khi viết `'A'` và `"A"`, bạn đang tạo **hai loại giá trị khác nhau**: `'A'` là literal ký tự có kiểu `char`, còn `"A"` là literal chuỗi có kiểu `String`. Nháy đơn, nháy kép, escape và số ký tự bên trong quyết định đoạn mã có hợp lệ hay không.

Bài này giúp bạn khai báo, in, so sánh và xử lý các literal ấy bằng Java 17+, đồng thời tránh nhầm giữa **một đơn vị mã UTF-16** và **một ký tự Unicode đầy đủ**.

## 1. Phân biệt `char` và `String`

| Nội dung | Literal ký tự | Literal chuỗi |
| --- | --- | --- |
| Cú pháp | `'A'` | `"A"` |
| Kiểu Java | `char`, kiểu nguyên thủy (*primitive*) 16 bit | `String`, kiểu tham chiếu bất biến (*immutable*) |
| Số đơn vị mã được biểu diễn | Đúng một đơn vị mã UTF-16 | Có thể là 0, 1 hoặc nhiều đơn vị mã UTF-16 |
| Rỗng | `''` **không hợp lệ** | `""` hợp lệ |
| Dấu xuống dòng | `'\n'` | `"\n"` |
| Ghép nối | Không có phương thức `concat` trên `char` | `"Ja" + "va"` cho `"Java"` |
| Đọc độ dài | Không gọi `.length()` trên `char` | `"Java".length()` trả 4 |

Ví dụ:

~~~java
char grade = 'A';
String gradeText = "A";
String empty = "";
String message = "Xin chao!";
~~~

Dù `grade` và `gradeText` cùng hiển thị chữ A, chúng **không cùng kiểu**. `char` lưu một đơn vị mã (giá trị số nguyên không dấu 16 bit); `String` biểu diễn một dãy các đơn vị mã và cung cấp nhiều phương thức thao tác chuỗi.

Lưu ý: một ký tự Unicode như emoji `🚀` thường cần **hai đơn vị mã UTF-16** (surrogate pair). Do đó `"🚀"` hợp lệ, nhưng không thể chứa trọn emoji ấy trong một literal `char` đơn lẻ.

## 2. Escape và ranh giới `char`/`String`

Trong cả character literal và string literal, dấu gạch chéo ngược bắt đầu một escape. Một escape phù hợp có thể biểu diễn **một đơn vị mã** mà ta không gõ trực tiếp:

| Source Java | Giá trị khi chạy | Kiểu ví dụ |
| --- | --- | --- |
| `'\n'` | Một char LF, mã 10 | `char` |
| `"\n"` | Chuỗi dài 1 đơn vị UTF-16 chứa LF | `String` |
| `'\\'` | Một char dấu backslash | `char` |
| `"\\n"` | Chuỗi gồm backslash và chữ n, độ dài 2 | `String` |
| `'\''` | Một char dấu nháy đơn | `char` |
| `"Say \"hi\""` | Chuỗi chứa hai dấu nháy kép | `String` |

Điều cần kiểm tra là **kiểu và độ dài giá trị sau khi diễn giải literal**, chứ không đếm ký tự gõ trong source. Escape `\t`, `\r`, `\b`, `\f` cũng có công dụng riêng; ví dụ `"\t"` chứa một code unit tab.

Unicode escape như `\u0041` là một cơ chế khác: Java xử lý Unicode escape **trước bước phân tích token** kể cả ở vị trí trông như comment. Do đó không dùng Unicode escape của LF để chèn newline vào string literal thông thường; dùng `"\n"` hoặc `'\n'`. Các ví dụ xử lý backslash, dấu nháy, đường dẫn và nhiều lớp escape sẽ được phân tích kỹ trong bài chuyên về escape sequence.


## 3. Chương trình Java 17+ chạy được

Lưu tệp UTF-8 tên **`CharacterStringDemo.java`**:

~~~java
public class CharacterStringDemo {
    public static void main(String[] args) {
        char first = 'J';
        char newline = '\n';
        char apostrophe = '\'';

        String language = "Java";
        String greeting = "Xin chao, " + language + "!";
        String twoLines = "dong 1\ndong 2";
        String rocket = "🚀";

        String block = """
                Alpha
                Beta
                """;

        System.out.println("char=" + first);
        System.out.println("newlineCode=" + (int) newline);
        System.out.println("apostrophe=" + apostrophe);
        System.out.println("greeting=" + greeting);
        System.out.println("lines=" + twoLines.replace("\n", "|"));
        System.out.println("length=" + language.length());
        System.out.println("rocketUnits=" + rocket.length());
        System.out.println(
                "rocketPoints=" + rocket.codePointCount(0, rocket.length()));
        System.out.println("block=" + block.replace("\n", "|"));
        System.out.println("equals=" + language.equals("Java"));
    }
}
~~~

Mở terminal trong thư mục chứa file (Windows PowerShell hoặc Linux Bash):

~~~bash
java -version
javac -version
javac -encoding UTF-8 CharacterStringDemo.java
java CharacterStringDemo
~~~

**Expected output:**

~~~text
char=J
newlineCode=10
apostrophe='
greeting=Xin chao, Java!
lines=dong 1|dong 2
length=4
rocketUnits=2
rocketPoints=1
block=Alpha|Beta|
equals=true
~~~

Giải thích kết quả đáng chú ý:

1. `(int) newline` chuyển giá trị `char` của LF sang số nguyên `10`.
2. `twoLines` thật sự chứa dấu xuống dòng; `replace("\n", "|")` giúp nhìn thấy ranh giới hai dòng khi in.
3. `rocket.length() == 2` vì emoji U+1F680 chiếm hai đơn vị mã UTF-16. `codePointCount(...) == 1` vì đó là một Unicode code point.
4. Text block `""" ... """` hỗ trợ từ Java 15, nên dùng được trong Java 17. Literal này chứa xuống dòng và loại bỏ phần thụt lề chung; do có xuống dòng ở cuối, phép `replace` cho `Alpha|Beta|`.
5. `equals` so sánh **nội dung chuỗi**, không dựa vào việc hai biến tham chiếu cùng một đối tượng.

Bạn có thể thử `java CharacterStringDemo.java` trên JDK 17+ để chạy trực tiếp tệp nguồn mà không cần gọi `javac` riêng. Kết quả chương trình vẫn như trên.

## 4. Khi cần chuỗi nhiều dòng: text block

Java 17 hỗ trợ text block `"""` (ổn định từ Java 15), vẫn cho ra **`String`**, không phải một kiểu ký tự mới. Dấu mở `"""` phải có dấu xuống dòng theo sau; compiler loại thụt lề chung để trình bày source thuận tiện:

~~~java
String normal = "A\nB";
String multi = """
        A
        B
        """;
~~~

Hai chuỗi trên không hoàn toàn bằng nhau: `multi` có **newline cuối**, còn `normal` thì không. Dấu đóng ngay sau `B` thay vì ở dòng riêng là một cách bỏ newline cuối. Text block có thể chứa dấu nháy kép thông thường và escape, nhưng dấu cách đầu/cuối còn chịu quy tắc xử lý thụt lề và khoảng trắng riêng. Khi cần nội dung chính xác, hãy kiểm tra bằng `length()` hoặc biểu diễn newline hiện rõ; các thao tác `\s`, nối dòng và vị trí dấu đóng được đi sâu trong bài text block.


## 5. Các lỗi thường gặp

| Đoạn mã | Kết quả | Cách sửa |
| --- | --- | --- |
| `char c = 'A';` | Hợp lệ | Một literal `char` |
| `String s = "A";` | Hợp lệ | Một literal `String` |
| `char c = '';` | Lỗi biên dịch | `char` phải chứa một đơn vị mã; nếu cần rỗng, dùng `String s = "";` |
| `char c = 'AB';` | Lỗi biên dịch | Dùng `String s = "AB";` |
| `String s = 'A';` | Lỗi kiểu dữ liệu | Dùng `String s = "A";` |
| `char c = "A";` | Lỗi kiểu dữ liệu | Dùng `char c = 'A';` |
| `String s = "C:\temp";` | Có thể sai ý định: `\t` là tab | Dùng `"C:\\temp"` |
| `String s = "a\nb";` | Hợp lệ: chứa xuống dòng lúc chạy | Nếu muốn thấy `\n` dưới dạng chữ, dùng `"a\\nb"` |
| `String s = "a" + "b";` | Hợp lệ | Cho kết quả nội dung `ab` |

**So sánh chuỗi:** tránh dùng `==` để kiểm tra nội dung hai `String` bất kỳ. `==` so sánh tham chiếu; `equals` so sánh nội dung. Trong ví dụ đang chạy, `language.equals("Java")` cho `true`.

**Bất biến:** `String` không thay đổi nội dung sau khi tạo. Ví dụ gọi `language.concat(" 17")` trả về **chuỗi mới**, không sửa giá trị `language` nếu bạn không gán kết quả.

## 6. Bài tập ngắn

**Bài 1.** Chọn kiểu đúng cho một chữ `K` và một chuỗi `"K"`; giải thích vì sao `'KK'` không hợp lệ.

*Đáp án:* `char c = 'K';` và `String s = "K";`. `'KK'` có hai đơn vị mã, không phải một `char`.

**Bài 2.** Dự đoán `"Hi\nJava".length()` và `"Hi\\nJava".length()`.

*Đáp án:* `7` và `8`. Chuỗi thứ nhất chứa một ký tự LF; chuỗi thứ hai chứa hai ký tự backslash và chữ `n`.

**Bài 3.** Trong `CharacterStringDemo`, thay `language = "Java"` bằng `"Kotlin"`. Giá trị `greeting` và `equals` sẽ thế nào?

*Đáp án:* `Xin chao, Kotlin!` và `false`.

**Bài 4.** Viết literal chứa chính xác dòng văn bản `C:\work\java`.

*Đáp án:* `"C:\\work\\java"`.

**Bài 5.** Vì sao `"🚀".length()` bằng 2 nhưng `codePointCount` bằng 1?

*Đáp án:* Một code point ngoài BMP được mã hóa bằng **hai UTF-16 code unit**. `String.length()` đếm code unit; `codePointCount` đếm code point (không nhất thiết bằng số *grapheme cluster* mà mắt người thấy).

## Ghi nhớ

- **`'A'` → `char`** (một đơn vị mã UTF-16); **`"A"` → `String`** (dãy ký tự, có thể rỗng).
- Dùng `\n`, `\t`, `\`, `\"`, `\'` đúng chỗ; phân biệt **escape của string** và **Unicode escape xử lý trước token**.
- `String` là immutable; so sánh nội dung bằng `equals`; `length()` đếm UTF-16 code unit.
- **Text block** `""" ... """` cũng là một `String`, hữu ích cho nội dung nhiều dòng trên Java 17+.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="literals-007">Escape sequence cho xuống dòng, dấu nháy và dấu gạch chéo ngược</a>

<details>
<summary>Xem chi tiết</summary>

# Escape sequence cho xuống dòng, dấu nháy và dấu gạch chéo ngược trong Java

## 1. Escape sequence là gì?

Trong **string literal** (`"..." `) và **character literal** (`'...'`) của Java, dấu gạch chéo ngược `\\` mở đầu một **escape sequence**: cách viết một ký tự đặc biệt bằng tổ hợp ký tự trong mã nguồn.

Ví dụ:

~~~java
System.out.println("Dong mot\nDong hai");
~~~

Trong file nguồn, `\n` được viết bằng hai ký tự `\\` và `n`, nhưng **khi Java tạo chuỗi**, chúng biểu diễn **một ký tự xuống dòng** (line feed, LF, U+000A). Đầu ra:

~~~text
Dong mot
Dong hai
~~~

Điều này khác chuỗi `"Dong mot\\nDong hai"`, trong đó `\\` đã được escape để trở thành **dấu gạch chéo ngược thật**, rồi theo sau là chữ `n`.

**Ba escape cần nắm chắc trong bài:**

- `\n`: xuống dòng LF.
- `\"`: đưa dấu nháy kép `"` vào một chuỗi được bao bởi nháy kép.
- `\\`: đưa một dấu gạch chéo ngược `\\` vào chuỗi hoặc ký tự.

Các escape được xử lý khi phân tích **literal trong mã nguồn**, không phải là quy tắc tự động áp dụng cho mọi dữ liệu nhận từ bàn phím, file hoặc API.

## 2. Bảng tra cứu ngắn

| Cú pháp trong mã Java | Ký tự/ý nghĩa | Trường hợp sử dụng |
|---|---|---|
| `\n` | Line feed, LF (U+000A) | Tạo dòng mới trong chuỗi |
| `\r` | Carriage return, CR (U+000D) | Giao thức hoặc dữ liệu cần CR |
| `\t` | Tab ngang | Thụt/căn bằng ký tự tab |
| `\"` | Dấu nháy kép `"` | Viết nháy kép trong string literal |
| `\'` | Dấu nháy đơn `'` | Viết nháy đơn trong char literal |
| `\\` | Một dấu gạch chéo ngược `\\` | Đường dẫn, regex, biểu diễn escape dưới dạng chữ |
| `\b` | Backspace | Ký tự điều khiển, ít dùng để định dạng văn bản |
| `\f` | Form feed | Ký tự điều khiển, ít dùng |

Tất cả ví dụ thuộc **Java 17+**. Những ký tự như `\\`, `"`, `'` trong bảng là ký hiệu mô tả; để viết chúng trong mã Java, phải chú ý dấu phân cách và escape của từng loại literal.

Java không hỗ trợ mọi kết hợp bắt đầu bằng `\\`: chẳng hạn `\q` không phải escape hợp lệ trong string literal thông thường.

## 3. Xuống dòng: `\\n` khác với `\\\\n`

Xét hai giá trị:

~~~java
String withNewline = "A\nB";
String literalBackslashN = "A\\nB";

System.out.println(withNewline);
System.out.println(literalBackslashN);
~~~

Đầu ra:

~~~text
A
B
A\nB
~~~

Có thể tự kiểm chứng bằng độ dài:

~~~java
System.out.println("\n".length());   // 1
System.out.println("\\n".length());  // 2
System.out.println("A\nB".length()); // 3
System.out.println("A\\nB".length());// 4
~~~

Giải thích: `"A\nB"` gồm `A`, **một** ký tự LF, `B`; `"A\\nB"` gồm `A`, `\\`, `n`, `B`.

### `print` khác `println`

~~~java
System.out.print("A\nB");
System.out.println("C");
~~~

Kết quả:

~~~text
A
BC
~~~

`print` không tự thêm dấu kết thúc dòng sau giá trị; chỉ có `\n` nằm trong chuỗi. `println` in phần nội dung và thêm dấu kết thúc dòng theo môi trường.

**Phân biệt LF với xuống dòng theo hệ điều hành:**

- `\n` luôn là ký tự LF, bất kể chương trình chạy trên Windows, Linux hay macOS.
- `System.lineSeparator()` cho ký tự/chuỗi phân tách dòng theo nền tảng.
- `System.out.println(...)` thêm dấu xuống dòng theo cơ chế của `PrintStream`, thường phù hợp với nền tảng.
- `System.out.printf("A%nB%n");` dùng `%n` của định dạng để xuống dòng theo nền tảng.

Nếu giao thức/tệp dữ liệu yêu cầu **LF chính xác**, hãy dùng `\n`; nếu mong đầu ra văn bản theo nền tảng, `println` hoặc `%n` thường thích hợp. Đừng tự giả định mọi tình huống đều cần `\r\n`.

## 4. Dấu nháy kép: `\\\"`

String literal Java dùng dấu nháy kép làm dấu **mở và đóng**. Muốn chứa dấu nháy kép thật bên trong chuỗi, phải escape:

~~~java
String message = "Lan noi: \"Xin chao!\"";
System.out.println(message);
~~~

Đầu ra:

~~~text
Lan noi: "Xin chao!"
~~~

Không có escape, đoạn sau gây lỗi biên dịch do dấu nháy kết thúc chuỗi quá sớm:

~~~java
// Sai: String bad = "Lan noi: "Xin chao!"";
~~~

Dấu nháy đơn **không bắt buộc escape trong string literal** được bao bằng nháy kép:

~~~java
String text = "It's Java";
System.out.println(text); // It's Java
~~~

Đừng thêm dấu `\\` không cần thiết khắp chuỗi, vì một số tổ hợp escape không hợp lệ hoặc khiến nội dung thật khác mong muốn.

## 5. Dấu nháy đơn và char literal: `\\'`

Character literal dùng dấu nháy đơn `'...'` và phải biểu diễn **một giá trị `char`**, tức một đơn vị mã UTF-16.

~~~java
char quote = '\'';
char doubleQuote = '"';
char newline = '\n';

System.out.println(quote);       // '
System.out.println(doubleQuote);// "
System.out.println((int) newline); // 10
~~~

Vì dấu `'` là dấu phân cách của character literal, bạn cần `'\''` để biểu diễn chính dấu nháy đơn. Dấu nháy kép có thể viết trực tiếp trong char literal như `'"'`.

Không nhầm:

- `'\n'` là **một char** mang mã 10.
- `"\n"` là **một String** có độ dài 1.
- `'\\'` là **một char** mang dấu gạch chéo ngược.
- `"\\n"` là **một String** gồm hai ký tự gạch chéo ngược và `n`.

Những đoạn như `char invalid = 'ab';` hoặc `char invalid = '';` không hợp lệ: character literal không được chứa hai ký tự thông thường hoặc rỗng.

## 6. Dấu gạch chéo ngược: `\\\\`

Trong Java source, dấu `\\` mở đầu escape. Vì vậy để có **một dấu gạch chéo ngược thật** trong chuỗi hoặc ký tự, viết **hai dấu** `\\`:

~~~java
String separator = "\\";
char slash = '\\';
System.out.println(separator); // \
System.out.println(slash);     // \
~~~

Ví dụ đường dẫn Windows:

~~~java
String path = "C:\\Users\\Lan\\notes.txt";
System.out.println(path);
~~~

Đầu ra:

~~~text
C:\Users\Lan\notes.txt
~~~

**Hai bẫy phổ biến:**

1. `"C:\Users\Lan"` chứa `\U` và `\L` không hợp lệ trong Java string literal; compiler báo lỗi *illegal escape character*.
2. `"C:\new\temp"` **có thể biên dịch** nhưng sai nội dung đường dẫn: `\n` thành xuống dòng, `\t` thành tab.

Tốt nhất khi thao tác đường dẫn thực tế là dùng `java.nio.file.Path`/ `Paths`, thay vì nối đường dẫn phụ thuộc hệ điều hành bằng tay:

~~~java
java.nio.file.Path file =
        java.nio.file.Path.of("data", "notes.txt");
System.out.println(file.getFileName()); // notes.txt
~~~

Với API nhận đường dẫn, cách viết `Path.of("data", "notes.txt")` tránh phải chèn `\\` chỉ để nối hai thành phần. Đây là ví dụ về đường dẫn tương đối; file không nhất thiết phải tồn tại.

## 7. Chương trình Java 17 chạy được và kết quả rõ ràng

Tạo tệp **`EscapeDemo.java`**:

~~~java
public final class EscapeDemo {
    private EscapeDemo() {}

    public static void main(String[] args) {
        String multiLine = "Dong 1\nDong 2";
        String quoted = "Lan noi: \"Xin chao!\"";
        String path = "C:\\Users\\Lan\\notes.txt";
        String visibleEscape = "\\n";
        char singleQuote = '\'';
        char backslash = '\\';

        System.out.println("1) newline:");
        System.out.println(multiLine);
        System.out.println("2) quote: " + quoted);
        System.out.println("3) path: " + path);
        System.out.println("4) literal: " + visibleEscape);
        System.out.println("5) char: " + singleQuote + " / " + backslash);
        System.out.println("6) lengths: "
                + "\n".length() + " vs " + "\\n".length());
        System.out.println("7) newline code: " + (int) '\n');
        System.out.println("8) equals: "
                + multiLine.equals("Dong 1\nDong 2"));
    }
}
~~~

Mở terminal trong thư mục chứa file; cần **JDK 17+**.

**Bash (Linux/macOS):**

~~~bash
java -version
javac -version
javac --release 17 -encoding UTF-8 EscapeDemo.java
java -cp . EscapeDemo
~~~

**PowerShell (Windows):**

~~~powershell
java -version
javac -version
javac --release 17 -encoding UTF-8 .\EscapeDemo.java
java -cp . EscapeDemo
~~~

Đầu ra kỳ vọng:

~~~text
1) newline:
Dong 1
Dong 2
2) quote: Lan noi: "Xin chao!"
3) path: C:\Users\Lan\notes.txt
4) literal: \n
5) char: ' / \
6) lengths: 1 vs 2
7) newline code: 10
8) equals: true
~~~

Hai dòng `Dong 1` và `Dong 2` xuất hiện tách biệt vì `multiLine` chứa **LF thật**. Dòng `4) literal: \n` hiển thị đúng hai ký tự `\\` và `n` vì biến `visibleEscape` có giá trị `"\\n"`.

Các lệnh ví dụ được thiết kế chạy độc lập; kết quả trên là **expected output** để người học tự đối chiếu, không phụ thuộc thư viện ngoài.

## 8. Escape trong chuỗi không phải phép thay thế cho mọi dữ liệu

Nếu ứng dụng đọc một chuỗi có **hai ký tự** `\\` và `n` từ file hoặc đầu vào người dùng, Java **không tự chuyển** chúng thành LF. Escape sequence được diễn giải khi phân tích literal trong **source code**.

Ví dụ:

~~~java
String typed = "\\n";
String actual = "\n";

System.out.println(typed.equals(actual)); // false
~~~

Trong một API nhận chuỗi, muốn hỗ trợ cú pháp escape từ dữ liệu đầu vào thì cần có **quy tắc parse riêng** cho định dạng ấy (JSON, properties, ngôn ngữ template, v.v.). Mỗi định dạng có quy tắc escape khác nhau: không lấy cú pháp Java áp đặt nguyên xi cho mọi trường hợp.

### Nhiều lớp escape

Khi chuỗi Java được dùng làm **regex**, backslash có thể phải đi qua **hai lớp cú pháp**: string literal của Java và biểu thức chính quy. Ví dụ:

~~~java
String regex = "\\d+";
System.out.println("123".matches(regex)); // true
~~~

Trong source Java, `"\\d+"` tạo chuỗi thật là `\d+`; sau đó regex engine diễn giải `\d` là lớp ký tự chữ số. Viết `"\d+"` trực tiếp trong Java source là **illegal escape character** vì `\d` không phải escape của string literal Java.

**Ranh giới bài học:** chỉ cần nhận ra dấu `\\` có thể mang vai trò khác nhau ở mỗi lớp cú pháp; học đầy đủ regex/JSON là chủ đề khác.

## 9. Những lỗi thường gặp

| Code / triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|
| `String s = "A\nB";` nhưng muốn thấy `A\nB` trên một dòng | `\n` tạo LF thật | `"A\\nB"` |
| `String s = "C:\Users\Lan";` | `\U` và `\L` không hợp lệ | `"C:\\Users\\Lan"` |
| `String s = "A\qB";` | `\q` không phải escape hợp lệ | Bỏ `\\` hoặc dùng `\\q` nếu cần ký tự `\\` thật |
| `String s = "Lan noi: "Hi"";` | Dấu nháy kép không được escape | `"Lan noi: \"Hi\""` |
| `char c = ''';` | Dấu nháy đơn đóng literal sớm | `char c = '\'';` |
| `String s = "A\nB";` cho length 3 | Hiểu nhầm `\n` gồm 2 ký tự khi chạy | LF là **1 char** |
| `String s = "\\n";` cho length 2 | Nhầm `\\n` với LF | Giá trị gồm `\\` và `n` |
| `System.out.print("A\nB")` không xuống dòng sau B | `print` không thêm dấu dòng cuối | Dùng `println` hoặc thêm ký tự cần thiết |

Trong những lỗi dạng *illegal escape character*, trước tiên hãy xác định ký tự **ngay sau dấu `\\`** ở vị trí bị báo; đó thường là phần mã nguồn không hợp lệ.

## 10. Những lưu ý quan trọng cho Java 17+

**Không gõ xuống dòng thật bên trong string literal thông thường:**

~~~java
// Sai nếu viết như dưới đây thành hai dòng source:
// String s = "A
// B";
~~~

Muốn xuống dòng, dùng `\n` trong string literal hoặc sử dụng **text block** (`"""`) khi nội dung thực sự nhiều dòng. Trong text block, các escape như `\n`, `\"`, `\\` vẫn có quy tắc riêng của Java; tuy nhiên text block có thêm cách xử lý xuống dòng và thụt lề, không nên dùng chỉ để thay cho một dấu `\n` đơn giản.

**Unicode escape khác escape trong string literal:** Java xử lý Unicode escapes kiểu `\\uXXXX` ở giai đoạn đọc nguồn đặc biệt, **trước khi nhận diện token**; vì vậy không đồng nhất `\n` với mã Unicode escape của LF. Để ghi một ký tự xuống dòng trong string literal, **ưu tiên `\n`** vì dễ đọc và tránh rắc rối từ giai đoạn xử lý Unicode escape.

**Không dùng `\r` thay thế `\n` một cách tùy tiện.** `\r` là carriage return; cách terminal hiển thị có thể khác tùy môi trường. Để tạo CRLF trong định dạng yêu cầu cặp ký tự này, dùng `\r\n`.

**Đừng nhầm dấu gạch chéo xuôi và ngược:** `/` là dấu slash thông thường, không phải mở đầu escape trong Java string literal. `"https://example.com"` không cần viết `\\` trước dấu `/`.

## 11. Bài tập có đáp án

### Bài A — dự đoán độ dài

~~~java
System.out.println("A\nB".length());
System.out.println("A\\nB".length());
System.out.println("\\".length());
~~~

**Đáp án:**

~~~text
3
4
1
~~~

### Bài B — in dấu nháy

Hãy tạo chuỗi in ra: `Co ay noi: "Java!"`.

**Đáp án:**

~~~java
System.out.println("Co ay noi: \"Java!\"");
~~~

### Bài C — đường dẫn Windows

Hãy viết một string literal có giá trị `C:\data\notes.txt` (dấu gạch chéo ngược thật).

**Đáp án:**

~~~java
String path = "C:\\data\\notes.txt";
~~~

### Bài D — dấu nháy đơn kiểu char

Muốn khai báo ký tự dấu nháy đơn, viết gì?

**Đáp án:** `char apostrophe = '\'';`. Không thể viết `char apostrophe = ''';` vì dấu nháy đơn là ký tự phân cách char literal.

### Bài E — phân biệt Java escape và dữ liệu

Hai ký tự `\\` và `n` được đọc từ một file văn bản. Biến `String` có tự chứa LF không?

**Đáp án:** Không. Chúng vẫn là hai ký tự riêng trừ khi định dạng của file/API và code đọc file có cơ chế diễn giải escape. Không gọi `String.replace` mù quáng với dữ liệu chưa hiểu quy tắc.

## 12. Checklist hoàn thành

- [ ] Giải thích được tại sao `\n` tạo LF và `\\n` tạo hai ký tự `\\` + `n`.
- [ ] Biết dùng `\"` để đưa dấu nháy kép vào string literal.
- [ ] Biết dùng `\'` cho dấu nháy đơn trong char literal.
- [ ] Biết dùng `\\` để biểu diễn dấu gạch chéo ngược.
- [ ] Phân biệt `print`, `println`, `\n` và `%n`.
- [ ] Viết đúng literal cho đường dẫn có dấu `\\`, tránh `\U`/ `\q` không hợp lệ.
- [ ] Biết `"\n".length() == 1` và `"\\n".length() == 2`.
- [ ] Không nhầm escape trong source Java với dữ liệu string nhận từ bên ngoài.
- [ ] Biên dịch và chạy `EscapeDemo.java` trên JDK 17+, đối chiếu từng dòng output.

**Kết luận:** Trong Java, dấu `\\` là ký hiệu mở đầu escape khi nằm trong string/char literal. `\n` tạo LF thật, `\"` tạo dấu nháy kép và `\\` tạo một dấu gạch chéo ngược. Muốn **in nguyên văn ký hiệu escape** như `\n`, hãy escape chính dấu gạch chéo ngược thành `\\n`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="literals-008">Text block: cú pháp mở đóng và xử lý thụt lề</a>

<details>
<summary>Xem chi tiết</summary>

# Text block: cú pháp mở, đóng và xử lý thụt lề trong Java

> **Java core — language-basics / Literal**  
> **Yêu cầu:** JDK 17+; text block là tính năng ổn định từ Java 15, không cần bật preview.  
> **Mục tiêu:** Viết đúng dấu mở/đóng `"""`; xác định khi nào chuỗi chứa newline cuối; giải thích xử lý *incidental indentation*; biết dùng escape `\s` và nối dòng; tự biên dịch và kiểm tra giá trị chuỗi.

## 1. Text block vẫn là `String`

**Text block** là cú pháp literal nhiều dòng, thích hợp cho JSON, HTML hoặc câu truy vấn dài. Nó tạo ra một `String` thông thường, không phải một kiểu dữ liệu riêng.

~~~java
String ordinary = "Hello\nJava\n";
String block = """
        Hello
        Java
        """;
System.out.println(ordinary.equals(block)); // true
~~~

Text block thường giúp mã dễ đọc hơn cách ghép nhiều chuỗi với `+ "\n"`, nhưng **không đảm bảo bảo toàn mọi dấu cách nguyên xi từ mã nguồn**. Java có quy tắc chuẩn hóa dấu xuống dòng và loại bỏ thụt lề phụ.

## 2. Cú pháp của delimiter mở và đóng

Cấu trúc điển hình:

~~~java
String message = """
        Dong thu nhat
        Dong thu hai
        """;
~~~

- **Dấu mở** gồm ba dấu nháy kép `"""`, sau đó có thể có khoảng trắng và **bắt buộc phải có line terminator**. Nội dung bắt đầu ở dòng **tiếp theo**; dấu xuống dòng bắt buộc ngay sau delimiter mở không trở thành ký tự đầu tiên của giá trị.
- **Dấu đóng** cũng gồm `"""`, nhưng có thể nằm trên **dòng riêng** hoặc **cùng dòng với phần nội dung cuối**.
- Dấu `;` kết thúc câu lệnh khai báo biến; nó không phải thành phần của giá trị String.
- Không cần escape dấu nháy kép `"` đơn lẻ bên trong text block như trong chuỗi thường, miễn không vô tình tạo một delimiter kết thúc.

**Cú pháp cố ý sai** (để trong comment, không chạy):

~~~java
// String bad = """Hello""";  // Thiếu line terminator sau delimiter mở.
~~~

Text block không phải string template: tên biến đặt trong nội dung không tự được thay bằng giá trị biến. Nếu muốn chèn dữ liệu động, dùng phép ghép, `String.format` hoặc API khác thích hợp.

## 3. Dấu đóng quyết định có newline ở cuối hay không

So sánh:

~~~java
String withNewline = """
        A
        B
        """;

String noNewline = """
        A
        B""";
~~~

| Cách đặt delimiter đóng | Giá trị Java tương đương | Điểm cần nhớ |
| --- | --- | --- |
| Dòng riêng sau B | `"A\nB\n"` | Newline sau B **thuộc chuỗi** |
| Sát B trên cùng dòng | `"A\nB"` | Không có newline cuối |

Đây là khác biệt **dữ liệu thực**, không chỉ khác biệt hình thức mã nguồn. Nếu phần mềm xuất JSON hay tạo văn bản kiểm thử mà không muốn newline cuối, nên chủ động chọn vị trí delimiter đóng hoặc định dạng lại chuỗi theo đúng yêu cầu.

**Không dùng `strip()` bừa bãi** để “sửa” newline thừa vì phương thức này còn bỏ khoảng trắng đầu/cuối vốn có thể mang ý nghĩa.

## 4. Quy tắc xử lý thụt lề phụ (*incidental indentation*)

Khi code được đặt trong method hay class, các dòng text block thường phải thụt theo định dạng Java. Java **loại phần thụt lề chung mang tính trình bày**, còn mức thụt sâu hơn được giữ.

~~~java
String list = """
        root
          child
        """;
~~~

Chuỗi `list` có giá trị tương đương:

~~~text
root
  child
~~~

Ngoài ra, nó có **newline ở cuối** do delimiter đóng nằm trên dòng riêng.

Quy tắc thực hành:

1. Để dấu đóng cùng mức với dòng nội dung có mức thụt lề cơ sở nếu bạn muốn loại bỏ phần thụt do code.
2. Dòng nào được thụt sâu hơn mức chung thì vẫn giữ **phần chênh lệch**. Ở ví dụ, `child` giữ hai dấu cách.
3. **Đặt dấu đóng lùi sang trái** có thể khiến Java loại bỏ ít thụt lề hơn và giữ thêm khoảng trắng ở đầu tất cả dòng.
4. Nên dùng space nhất quán thay vì trộn tab và space vì nhìn giống nhau trong editor không có nghĩa dấu thụt thực giống nhau.
5. Dòng trống và dấu cách cuối dòng cũng chịu quy tắc xử lý riêng; đừng mặc định mọi ký tự trắng gõ trong source đều được giữ.

Nói chính xác hơn, Java sử dụng các dòng không trống và vị trí dấu đóng để xác định mức *incidental indentation*. Đây không phải thao tác tùy ý “xóa mọi khoảng trắng đầu dòng”.

## 5. Thứ tự xử lý newline và escape

Java chuẩn hóa các line terminator trong nội dung text block về **LF** (`\n`), kể cả source lưu theo kiểu CRLF. Tiếp đó, nó loại bỏ thụt lề phụ và khoảng trắng cuối dòng thích hợp; **sau cùng** mới diễn giải escape.

| Cú pháp nội dung | Ý nghĩa |
| --- | --- |
| `\n` | Ký tự xuống dòng theo escape |
| `\t` | Ký tự tab theo escape |
| `\s` | Một dấu cách; đặc biệt hữu ích để **giữ khoảng trắng cuối dòng** |
| `\` ngay trước line terminator | **Line continuation**: bỏ dấu kết thúc dòng đó, ghép hai phần nội dung |

Ví dụ:

~~~java
String spaces = """
        end\s\s
        """;

String joined = """
        pre\
        fix
        """;
~~~

Giá trị của `spaces` là `"end  \n"` (hai dấu cách trước newline). Giá trị của `joined` là `"prefix\n"`: dấu `\` nối `pre` và `fix`, nhưng newline sau `fix` vẫn còn.

**Khác nhau:** `\n` trong source tạo một ký tự LF trong giá trị; còn khi in bản thân hai ký tự dấu gạch chéo ngược và chữ `n`, bạn phải escape phù hợp, ví dụ chuỗi thường `"\\n"`.

## 6. Một chương trình Java 17+ kiểm chứng chính xác

Tạo file **TextBlockDemo.java**:

~~~java
public class TextBlockDemo {
    private static String visible(String value) {
        return value.replace(" ", "_").replace("\n", "|");
    }

    public static void main(String[] args) {
        String indented = """
                first
                  second
                """;

        String noFinalNewline = """
                one
                two""";

        String keptSpaces = """
                end\s\s
                """;

        String joined = """
                pre\
                fix
                """;

        System.out.println("indented=" + visible(indented));
        System.out.println("noFinalNewline=" + visible(noFinalNewline));
        System.out.println("keptSpaces=" + visible(keptSpaces));
        System.out.println("joined=" + visible(joined));
        System.out.println("sameType=" + indented.getClass().getSimpleName());
    }
}
~~~

Chạy từ thư mục có file trên Bash, PowerShell hoặc Command Prompt:

~~~bash
javac -encoding UTF-8 TextBlockDemo.java
java TextBlockDemo
~~~

**Expected output:**

~~~text
indented=first|__second|
noFinalNewline=one|two
keptSpaces=end__|
joined=prefix|
sameType=String
~~~

Hàm `visible` chỉ chuyển dấu cách thành `_` và newline thành `|` để thấy chúng rõ ràng trên console; nó **không sửa chuỗi gốc**.

Giải thích từng dòng:

- `indented` giữ hai dấu cách trước `second` và một newline cuối.
- `noFinalNewline` không có newline sau `two` vì dấu đóng đứng sát phần nội dung cuối.
- `keptSpaces` có hai dấu cách thật nhờ hai `\s`, ngay trước newline cuối.
- `joined` nối `pre` với `fix`, không còn newline ở giữa; newline cuối vẫn còn.
- `sameType` khẳng định đối tượng được tạo là `String`.

## 7. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Compiler báo delimiter mở không hợp lệ | Viết nội dung ngay sau `"""` mở | Đặt line terminator sau dấu mở |
| Dữ liệu có `\n` cuối ngoài ý muốn | Dấu đóng ở dòng riêng | Đặt dấu đóng ngay sau nội dung cuối nếu muốn bỏ newline đó |
| Dấu cách cuối dòng bị mất | Khoảng trắng cuối dòng được loại bỏ khi chuẩn hóa text block | Dùng `\s` để giữ khoảng trắng có chủ đích |
| Dữ liệu giữ nhiều khoảng trắng đầu dòng | Dấu đóng lùi trái làm giảm mức incidental indentation được loại bỏ | Căn lại các dòng và dấu đóng |
| Chuỗi từ Windows khác newline của tệp gốc | Java chuẩn hóa CRLF thành LF bên trong text block | Kiểm tra dữ liệu đầu ra thay vì dựa trên dòng mã nguồn |
| Text block tự nhiên không chèn biến | Text block là String literal, không phải template | Dùng định dạng/ghép chuỗi có chủ đích |
| Có ba dấu nháy kép liên tiếp trong nội dung | Có thể kết thúc text block sớm | Escape hoặc cấu trúc lại đoạn chứa dấu nháy |

Nếu yêu cầu bảo toàn **từng byte** của tài liệu bên ngoài, text block không phải lựa chọn mặc định: hãy cân nhắc đọc resource/file và kiểm soát encoding/line endings.

## 8. Bài tập ngắn

**Bài 1 — Newline cuối:** Viết hai text block chứa `Hello`: một đóng ở dòng riêng, một đóng sát `Hello`. So sánh `length()` và đầu ra qua `visible`.

**Bài 2 — Thụt lề:** Viết hai dòng `root` và `  child`, thay đổi vị trí dấu đóng sang trái rồi quan sát số dấu cách còn lại bằng `visible`.

**Bài 3 — Dấu cách cuối:** So sánh hai dấu cách thật ở cuối dòng source với hai escape `\s\s`. Chúng được giữ giống nhau không?

**Bài 4 — Nối dòng:** Sửa `pre` và `fix` thành `foo` và `bar`, thử có/không có dấu `\` trước line terminator. Đoán giá trị có newline nào.

**Bài 5 — JSON:** Viết JSON nhiều dòng trong text block và in ra. Kiểm tra mức thụt lề của nội dung, dấu nháy kép, newline cuối trước khi dùng làm dữ liệu thực.

### Ghi nhớ

**Text block là `String`**, dùng ba dấu nháy kép; dấu mở bắt buộc theo sau bởi xuống dòng. **Vị trí dấu đóng quyết định newline cuối.** Java chuẩn hóa newline, loại bỏ thụt lề phụ rồi mới diễn giải escape như `\s` và dấu `\` nối dòng. Khi cần chính xác từng dấu cách hoặc newline, hãy hiển thị chúng bằng một hàm như `visible`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="literals-009">Literal null và những nơi không thể gán null</a>

<details>
<summary>Xem chi tiết</summary>

# Literal `null` và những nơi không thể gán `null` — Java 17+

`null` là một **literal** của Java, biểu thị việc một tham chiếu **không trỏ tới đối tượng hay mảng nào**. Đây không phải chuỗi `"null"`, không phải số `0` và cũng không phải giá trị mặc định của mọi kiểu dữ liệu.

Quy tắc chính: **`null` có thể gán cho biến kiểu tham chiếu, nhưng không thể gán trực tiếp cho biến kiểu nguyên thủy (*primitive*).** Việc gán hợp lệ về cú pháp chưa chắc bảo đảm an toàn khi dùng giá trị sau đó.

## 1. `null` gán được cho kiểu nào?

| Kiểu đích | Ví dụ | Hợp lệ trên Java 17? | Vì sao |
| --- | --- | --- | --- |
| Lớp | `String text = null;` | Có | `String` là kiểu tham chiếu |
| Lớp bao (*wrapper*) | `Integer number = null;` | Có | `Integer` là đối tượng, khác `int` |
| Mảng | `int[] numbers = null;` | Có | Bản thân mảng là kiểu tham chiếu |
| Interface | `Runnable task = null;` | Có | Biến giữ tham chiếu đến đối tượng thực thi interface |
| Enum hoặc record | `Status s = null;` khi `Status` là enum | Có | Đều là kiểu tham chiếu |
| Kiểu tổng quát tham chiếu | `java.util.List<String> list = null;` | Có | Biến giữ tham chiếu danh sách |
| Nguyên thủy | `int n = null;` | **Không** | `int` cần giá trị số nguyên |
| Nguyên thủy | `boolean ok = null;` | **Không** | `boolean` cần `true` hoặc `false` |
| Nguyên thủy | `char c = null;` | **Không** | `char` cần giá trị mã ký tự |
| Biến cục bộ dùng `var` | `var x = null;` | **Không** | Compiler không suy luận được kiểu từ initializer `null` đơn lẻ |

Các kiểu nguyên thủy còn lại `byte`, `short`, `long`, `float`, `double` cũng **không nhận `null` trực tiếp**. `void` không phải kiểu biến lưu giá trị; phương thức `void` không được dùng `return null;`.

**Chi tiết ngôn ngữ:** literal `null` là giá trị duy nhất của *null type*. Null type không có tên để bạn khai báo biến trực tiếp. Java cho phép chuyển giá trị này sang kiểu tham chiếu phù hợp, nhưng không sang kiểu nguyên thủy.

## 2. Ví dụ Java 17 chạy được

Tạo file UTF-8 tên **`NullLiteralDemo.java`**:

~~~java
import java.util.Objects;

public class NullLiteralDemo {
    static String displayName(String name) {
        return name == null ? "(khong co ten)" : name;
    }

    static int lengthOrZero(String text) {
        return text == null ? 0 : text.length();
    }

    public static void main(String[] args) {
        String name = null;
        Integer boxedCount = null;
        int[] numbers = null;
        String[] names = {"Mai", null};

        System.out.println("nameIsNull=" + (name == null));
        System.out.println("boxedIsNull=" + (boxedCount == null));
        System.out.println("arrayIsNull=" + (numbers == null));
        System.out.println("elementIsNull=" + (names[1] == null));

        System.out.println("display=" + displayName(name));
        System.out.println("lengthOrZero=" + lengthOrZero(name));
        System.out.println("stringNull=" + "null".equals(name));
        System.out.println("objectsEquals=" + Objects.equals(name, null));

        try {
            System.out.println("length=" + name.length());
        } catch (NullPointerException ex) {
            System.out.println("dereference=NullPointerException");
        }

        try {
            int count = boxedCount; // Tu dong unboxing Integer -> int.
            System.out.println("count=" + count);
        } catch (NullPointerException ex) {
            System.out.println("unboxing=NullPointerException");
        }
    }
}
~~~

Mở **Windows PowerShell** hoặc **Linux/Bash** tại thư mục chứa tệp:

~~~bash
java -version
javac -version
javac -encoding UTF-8 NullLiteralDemo.java
java NullLiteralDemo
~~~

**Expected output:**

~~~text
nameIsNull=true
boxedIsNull=true
arrayIsNull=true
elementIsNull=true
display=(khong co ten)
lengthOrZero=0
stringNull=false
objectsEquals=true
dereference=NullPointerException
unboxing=NullPointerException
~~~

Giải thích:

- `String`, `Integer`, `int[]` đều là kiểu tham chiếu, nên biến có thể chứa `null`.
- Mảng `names` **không null**, nhưng **phần tử** `names[1]` có thể null vì phần tử có kiểu `String`.
- `name.length()` biên dịch hợp lệ nhưng gọi phương thức trên tham chiếu null sẽ ném `NullPointerException` lúc chạy.
- `int count = boxedCount;` cũng biên dịch hợp lệ: compiler chèn phép *unboxing* từ `Integer` sang `int`. Nhưng vì wrapper đang null, thao tác unboxing ném `NullPointerException`.
- `Objects.equals(name, null)` so sánh an toàn khi một hoặc hai đối số là null; `"null".equals(name)` so sánh chuỗi literal với tham chiếu null nên trả `false`.

Trong ví dụ, `try/catch` được dùng chỉ để **minh họa hai lỗi runtime** mà không làm chương trình ngừng ngay. Trong ứng dụng thực, nên kiểm tra và xử lý dữ liệu đầu vào trước khi truy cập hoặc unboxing.

## 3. Lỗi **biên dịch** khác lỗi **khi chạy**

Các dòng dưới đây **cố ý sai**, chỉ dùng để học, không thêm vào chương trình đang chạy:

~~~java
// int quantity = null;       // Sai: null khong chuyen sang int.
// boolean enabled = null;    // Sai: null khong chuyen sang boolean.
// char letter = null;        // Sai: null khong chuyen sang char.
// var unknown = null;        // Sai: khong suy luan duoc kieu.
// int[] values = {null};     // Sai: phan tu int khong nhan null.
~~~

Ngược lại, các dòng sau **hợp lệ về kiểu**:

~~~java
Integer quantity = null;
Boolean enabled = null;
Character letter = null;
String inferred = null;
Integer[] values = {null};
~~~

Phân biệt hai loại mảng:

~~~java
int[] a = null;           // Hop le: tham chieu mang chua null.
int[] b = new int[2];     // Phan tu primitive co gia tri 0.
String[] c = new String[2]; // Phan tu tham chieu mac dinh null.
~~~

Ở đây `a.length` sẽ ném `NullPointerException` vì **bản thân a là null**. `b[0]` bằng `0`; `c[0]` bằng `null`. Việc dùng cùng từ “giá trị mặc định” không có nghĩa các kiểu đều nhận cùng một giá trị.

## 4. `null` không giống giá trị rỗng hoặc giá trị mặc định

| Giá trị | Ý nghĩa |
| --- | --- |
| `null` | Không có tham chiếu đối tượng/mảng |
| `""` | Một `String` tồn tại, có độ dài 0 |
| `"null"` | Một `String` gồm bốn chữ `n-u-l-l` |
| `0` | Giá trị số nguyên 0 |
| `false` | Giá trị boolean false |
| `new int[0]` | Một đối tượng mảng có độ dài 0, **không null** |

Dùng `text == null` khi cần biết có tham chiếu hay không. Dùng `text.isEmpty()` khi đã biết `text` không null và muốn kiểm tra chuỗi rỗng. Có thể kết hợp:

~~~java
if (text == null || text.isEmpty()) {
    System.out.println("Thieu noi dung");
}
~~~

Toán tử `||` có **short-circuit**: nếu `text == null` là true, Java không gọi `text.isEmpty()` ở vế phải, nhờ đó tránh `NullPointerException`.

Khi so sánh nội dung `String`, dùng `Objects.equals(a, b)` để xử lý cả trường hợp null, hoặc `"OK".equals(value)` khi literal chắc chắn không null. Toán tử `==` giữa hai biến kiểu tham chiếu so sánh **cùng tham chiếu hay không**, không phải nội dung.

## 5. Dùng `null` trong trả về, tham số và generic

~~~java
static String findName(boolean found) {
    return found ? "Mai" : null; // Hop le: tra ve String.
}

static int findCount() {
    return 0; // Khong the return null cho int.
}

static void show(String value) {
    System.out.println(value == null ? "(empty)" : value);
}
~~~

- Có thể gọi `show(null)` vì tham số có kiểu tham chiếu `String`.
- `return null;` hợp lệ khi kiểu trả về là kiểu tham chiếu, ví dụ `String`, nhưng không hợp lệ với `int` hoặc phương thức `void`.
- Với generic, `java.util.List<String> items = null;` hợp lệ; nhưng gọi `items.size()` sẽ ném `NullPointerException`.
- Cũng cần phân biệt **tham chiếu danh sách null** với **danh sách tồn tại có phần tử null**. Ví dụ `new java.util.ArrayList<String>()` là danh sách tồn tại; một số API chấp nhận phần tử null, còn `List.of(null)` sẽ ném `NullPointerException`.

Nếu không muốn trả null để báo “không tìm thấy”, có thể cân nhắc `Optional<String>` với `Optional.empty()` hoặc `Optional.ofNullable(value)`. `Optional` là một lựa chọn API, không tự thay đổi quy tắc của literal `null`.

## 6. Bài tập tự kiểm tra

**Bài 1:** Trong `String s = null;` và `String s = "";`, cách nào có thể gọi `s.length()` ngay?

*Đáp án:* Chuỗi rỗng `""`: kết quả 0. Với `null`, lời gọi ném `NullPointerException`.

**Bài 2:** Vì sao `Integer number = null;` biên dịch, còn `int number = null;` không biên dịch?

*Đáp án:* `Integer` là kiểu tham chiếu; `int` là nguyên thủy. Nếu unbox một `Integer` đang null, lỗi phát sinh **khi chạy**.

**Bài 3:** `String[] names = {null};` có hợp lệ không? Còn `char[] chars = {null};`?

*Đáp án:* Cái đầu hợp lệ vì phần tử là `String`; cái sau không vì phần tử `char` là primitive.

**Bài 4:** Sửa `var data = null;` mà vẫn muốn giữ chỗ cho giá trị `String` sau này.

*Đáp án:* Dùng `String data = null;` để nêu rõ kiểu; `var` không suy luận được kiểu từ `null`.

**Bài 5:** Dự đoán `"null".equals((String) null)` và `Objects.equals(null, null)`.

*Đáp án:* Lần lượt `false` và `true`.

## Checklist ghi nhớ

- [ ] `null` là literal, **không phải** số, false hay chuỗi `"null"`.
- [ ] `null` gán được cho kiểu tham chiếu, **không** gán trực tiếp cho primitive.
- [ ] `var x = null` không suy luận được kiểu trên Java 17.
- [ ] Bản thân mảng có thể null; phần tử của mảng primitive thì không.
- [ ] Gán `null` hợp lệ không có nghĩa truy cập phương thức hoặc unboxing là an toàn.
- [ ] Phân biệt lỗi biên dịch với `NullPointerException` lúc chạy.
- [ ] Có thể dùng kiểm tra `== null`, short-circuit `||` và `Objects.equals` khi phù hợp.

**Kết luận:** `null` biểu thị **không có tham chiếu**. Java cho phép nó ở những vị trí cần kiểu tham chiếu, nhưng từ chối trong nơi yêu cầu giá trị primitive. Khi code đã biên dịch, hãy kiểm tra null trước khi dereference hoặc unbox để tránh lỗi runtime.


</details>

- [Quay lại đầu trang](#back-to-top)
