<a id="back-to-top"></a>

# Kiểu số nguyên

## Menu
- [byte, short, int và long](#integer-types-001)
- [Kích thước bit và miền giá trị của từng kiểu](#integer-types-002)
- [Biểu diễn số âm bằng bù hai](#integer-types-003)
- [MIN_VALUE, MAX_VALUE, SIZE và BYTES](#integer-types-004)
- [char là số nguyên không dấu biểu diễn đơn vị mã UTF-16](#integer-types-005)
- [Phân biệt char số với giá trị chữ số được viết dưới dạng ký tự](#integer-types-006)
- [Giá trị mặc định của field và yêu cầu gán biến cục bộ](#integer-types-007)
- [Vì sao boolean không phải kiểu số](#integer-types-008)
- [Chọn int hay long cho số đếm, chỉ số và kích thước](#integer-types-009)
- [Giới hạn của kiểu cố định so với số nguyên toán học](#integer-types-010)

## <a id="integer-types-001">byte, short, int và long</a>

<details>
<summary>Xem chi tiết</summary>

# `byte`, `short`, `int` và `long` trong Java

Java có bốn kiểu số nguyên nguyên thủy (*primitive integer types*) có dấu: **`byte` (8 bit), `short` (16 bit), `int` (32 bit), `long` (64 bit)**. Chúng lưu số nguyên chính xác **trong miền giá trị của từng kiểu**, nhưng có thể gặp tràn số khi kết quả phép toán vượt miền. Để dùng đúng, cần hiểu giới hạn, literal, chuyển kiểu, cơ chế nâng kiểu khi tính toán và các phép chia số nguyên.

## 1. Độ rộng, phạm vi và bản chất của bốn kiểu

| Kiểu | Độ rộng | Nhỏ nhất | Lớn nhất | Wrapper |
| --- | ---: | ---: | ---: | --- |
| `byte` | 8 bit | -128 | 127 | `Byte` |
| `short` | 16 bit | -32.768 | 32.767 | `Short` |
| `int` | 32 bit | -2.147.483.648 | 2.147.483.647 | `Integer` |
| `long` | 64 bit | -9.223.372.036.854.775.808 | 9.223.372.036.854.775.807 | `Long` |

Kiểu số nguyên có dấu gồm `n` bit có miền `-2^(n-1)` đến `2^(n-1)-1`. Chẳng hạn, `byte` có miền từ `-2^7 = -128` đến `2^7-1 = 127`. Phần âm có thêm một giá trị vì miền không âm phải chứa cả số 0.

Java quy định bốn kiểu này dùng biểu diễn **bù hai (two's complement)** cho các phép toán nguyên có dấu. Giá trị `-1` có tất cả bit bằng 1 ở độ rộng đang dùng. Số bit của từng kiểu **không thay đổi** giữa máy 32 bit và 64 bit. Trong các kiểu nguyên có dấu, `int` thường là lựa chọn mặc định cho biến đếm và tính toán thông thường; `long` dùng khi cần miền lớn hơn.

Các hằng của wrapper giúp tra giới hạn an toàn: `Byte.MIN_VALUE`, `Short.MAX_VALUE`, `Integer.MIN_VALUE`, `Long.MAX_VALUE`. Không nên nhầm độ rộng giá trị với dung lượng thực tế của một object trong bộ nhớ: object header, căn chỉnh, boxing và cấu trúc collection có chi phí bổ sung. Với `int[]` hoặc `byte[]`, dữ liệu là primitive nhưng mảng vẫn có phần quản lý của JVM.

Cần phân biệt `char`: dù có 16 bit, nó biểu diễn một đơn vị mã UTF-16 **không dấu**, không thuộc bốn kiểu số nguyên có dấu ở đây. `boolean` cũng không phải kiểu số nguyên.

## 2. Khai báo biến, giá trị mặc định và literal

```java
byte level = 25;
short year = 2026;
int users = 100_000_000;
long count = 3_000_000_000L;
long smallCount = 100;  // literal int 100 được mở rộng chính xác thành long
```

Literal số nguyên **thập phân** không có hậu tố thường là `int` nếu nằm trong miền `int`. Nếu số lớn hơn `Integer.MAX_VALUE` và cần kiểu `long`, thêm `L` viết hoa:

```java
long ok = 5_000_000_000L;
// long wrong = 5_000_000_000; // Lỗi: literal thập phân vượt miền int
```

Dùng `L` thay vì `l` thường để tránh nhìn nhầm chữ `l` với số `1`. Dấu `_` bên trong các chữ số giúp dễ đọc mà không thay đổi giá trị; không được tùy tiện đặt ở đầu/cuối literal hoặc ngay trước hậu tố.

Java còn có literal nhị phân `0b1010`, bát phân `012` và thập lục phân `0x0A`; cả ba ví dụ đều có giá trị **10**. Riêng `012` là bát phân: số 0 ở đầu literal nguyên có ý nghĩa cú pháp, không phải cách viết thập phân bình thường.

Field primitive và phần tử mảng primitive được khởi tạo mặc định bằng **0** (`long` có giá trị 0L). Biến cục bộ trong phương thức **không được tự động gán 0 để có thể đọc ngay**; Java yêu cầu nó đã được gán chắc chắn trước khi sử dụng.

Với hằng `final`, trình biên dịch có thể áp dụng quy tắc hằng số tại thời điểm biên dịch. Ví dụ:

```java
byte first = 127;          // Hợp lệ: hằng int 127 nằm trong miền byte
final int LIMIT = 100;
byte second = LIMIT;       // Hợp lệ: constant expression trong miền byte
int variable = 100;
// byte third = variable; // Không hợp lệ: int thông thường không tự thu về byte
// byte fourth = 128;    // Không hợp lệ: ngoài miền byte
```

Đây là **quy tắc gán hằng số có điều kiện**, không phải mọi chuyển kiểu `int → byte` đều tự động.

## 3. Mở rộng kiểu (widening): không mất số nguyên trong bốn kiểu

Chuỗi mở rộng điển hình là:

```text
byte → short → int → long
```

Mọi giá trị của kiểu bên trái đều biểu diễn được bởi kiểu bên phải:

```java
byte original = -100;
short a = original;
int b = a;
long c = b;        // c vẫn bằng -100
```

Chuyển mở rộng giữa **`byte`, `short`, `int` và `long`** là ngầm định và giữ chính xác giá trị. Tuy nhiên, đừng suy diễn rằng chuyển một `long` lớn sang `double` cũng luôn giữ chính xác mọi chữ số nguyên: độ chính xác phần trị của số dấu phẩy động hữu hạn.

## 4. Thu hẹp kiểu (narrowing): cast có thể đổi giá trị

Muốn gán từ kiểu lớn về kiểu nhỏ, Java thường yêu cầu ép kiểu tường minh:

```java
int number = 130;
byte small = (byte) number; // -126

long huge = 3_000_000_000L;
int changed = (int) huge;   // -1294967296
```

Với các chuyển kiểu số nguyên thu hẹp, Java giữ **các bit thấp**, bỏ các bit cao rồi diễn giải kết quả theo kiểu đích có dấu. Ví dụ 130 có tám bit thấp là `1000_0010`, nên khi đọc theo `byte` có dấu, giá trị nhận được là -126. Cast **không làm tròn**, **không kẹp tại `MIN_VALUE`/`MAX_VALUE`**, và **không tự báo lỗi** nếu số vượt miền đích.

Nếu cần chuyển `long` sang `int` và phát hiện giá trị nằm ngoài miền, dùng `Math.toIntExact(value)`: nó ném `ArithmeticException` khi không thể biểu diễn trong `int`. Với `int` sang `byte` hoặc `short`, hãy kiểm tra giới hạn kiểu đích trước khi cast.

## 5. Quy tắc nâng kiểu trong biểu thức số học

Trong nhiều phép toán hai ngôi như `+`, `-`, `*`, `/`, `%`, các toán hạng `byte` hoặc `short` được nâng lên **`int`** trước khi tính (*binary numeric promotion*). Khi một toán hạng là `long` và toán hạng còn lại là kiểu số nguyên trong bốn kiểu này, phép toán được thực hiện theo `long`.

```java
byte a = 10;
byte b = 20;
int sum = a + b;            // Đúng: kiểu biểu thức là int
// byte wrong = a + b;      // Lỗi biên dịch: int không tự thu về byte
byte smallSum = (byte) (a + b);

int x = 1_000_000;
long wrongProduct = x * x;          // Phép nhân int tràn trước khi gán long
long correctProduct = (long) x * x; // 1_000_000_000_000L
```

**Kiểu của biến nhận không quyết định kiểu trung gian của biểu thức**. `long wrongProduct = x * x` vẫn nhân theo `int`. Ép một toán hạng thành `long` **trước phép tính** mới giúp biểu thức nhân theo `long`; sau đó vẫn phải bảo đảm kết quả nằm trong giới hạn `long`.

Các phép toán đơn ngôi `+`, `-`, `~` nâng `byte`/`short` lên `int`. Với dịch bit `<<`, `>>`, `>>>`, toán hạng trái sau nâng kiểu quyết định kiểu kết quả (`int` hoặc `long`).

### Tại sao `byte counter += 1` lại biên dịch?

```java
byte counter = 127;
counter += 1;             // Hợp lệ, kết quả sẽ là -128
// counter = counter + 1;  // Lỗi: counter + 1 có kiểu int
```

Phép gán rút gọn `counter += 1` bao gồm chuyển kiểu về `byte` một cách ngầm định (tương tự `counter = (byte) (counter + 1)`, trừ việc biểu thức bên trái chỉ được đánh giá một lần). Điều đó thuận tiện về cú pháp nhưng **không bảo vệ khỏi overflow**.

## 6. Tràn số (overflow) và phép toán an toàn

Nếu kết quả toán học vượt miền giá trị, phép cộng/trừ/nhân trên kiểu số nguyên nguyên thủy có kết quả theo độ rộng bit cố định. Ví dụ:

- `Integer.MAX_VALUE + 1` cho `Integer.MIN_VALUE`.
- `Long.MAX_VALUE + 1L` cho `Long.MIN_VALUE`.
- `Math.abs(Integer.MIN_VALUE)` vẫn cho `Integer.MIN_VALUE` vì không thể biểu diễn giá trị đối dương tương ứng trong `int`.

Các phép cộng, trừ, nhân thông thường **không tự ném ngoại lệ khi tràn**. Nếu cần phát hiện thay vì nhận kết quả đã quấn vòng, dùng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`, `Math.incrementExact`, `Math.decrementExact`, `Math.negateExact` hoặc kiểm tra miền. Chúng ném `ArithmeticException` với các trường hợp overflow mà API xử lý. `Math.toIntExact` hữu ích riêng cho đổi `long` sang `int`.

Muốn tính với số nguyên vượt cả 64 bit, sử dụng `java.math.BigInteger`. `BigInteger` không có độ rộng cố định như `long` nhưng vẫn chịu giới hạn bộ nhớ và thời gian xử lý.

## 7. Phép chia nguyên, phép lấy dư và chia cho 0

Phép chia hai số nguyên cho kết quả số nguyên, **cắt phần phân số về phía 0**:

- `7 / 3 == 2`.
- `-7 / 3 == -2`, không phải -3.
- `7 % 3 == 1`.
- `-7 % 3 == -1` (số dư khác 0 cùng dấu với số bị chia).

Nếu mẫu số là 0, `x / 0` và `x % 0` với các toán hạng kiểu nguyên gây `ArithmeticException`. Trường hợp đặc biệt `Integer.MIN_VALUE / -1` trả về `Integer.MIN_VALUE` theo quy tắc kết quả số nguyên cố định, không tự ném ngoại lệ.

Khi nghiệp vụ cần phép chia **làm tròn về âm vô cực**, `Math.floorDiv` và `Math.floorMod` có quy tắc khác `/`, `%` khi số âm tham gia. Những API này không thay thế cho kiểm tra overflow tổng quát.

## 8. Có kiểu nguyên không dấu (unsigned) không?

Bốn primitive đang xét đều **có dấu**. Tuy nhiên, Java có API giúp **diễn giải các bit như giá trị không dấu** trong giao thức hoặc file nhị phân:

- `Byte.toUnsignedInt(b)`: đọc mẫu 8 bit của `byte` như số từ 0 đến 255, trả về `int`.
- `Short.toUnsignedInt(s)`: đọc mẫu 16 bit như số 0..65535.
- `Integer.toUnsignedLong(i)`: đọc mẫu 32 bit như số 0..4294967295 dưới dạng `long`.
- `Integer.compareUnsigned`, `Integer.divideUnsigned`, `Long.compareUnsigned` và `Long.toUnsignedString`: hỗ trợ thao tác/biểu diễn theo ngữ nghĩa không dấu.

Ví dụ, `Integer.toUnsignedLong(-1)` bằng `4_294_967_295L` do 32 bit của -1 đều bằng 1. Biến `int` ban đầu **vẫn là -1**; đây là sự thay đổi cách diễn giải bit, không phải một kiểu `unsigned int` primitive mới.

## 9. Ví dụ hoàn chỉnh chạy trên Java 17+

Lưu tệp `IntegerTypesDemo.java`:

```java
public class IntegerTypesDemo {
    public static void main(String[] args) {
        byte b = 127;
        short s = 30_000;
        int i = 2_000_000_000;
        long l = 3_000_000_000L;

        System.out.println("byte: " + Byte.MIN_VALUE + " .. " + Byte.MAX_VALUE);
        System.out.println("short: " + Short.MIN_VALUE + " .. " + Short.MAX_VALUE);
        System.out.println("int: " + Integer.MIN_VALUE + " .. " + Integer.MAX_VALUE);
        System.out.println("long: " + Long.MIN_VALUE + " .. " + Long.MAX_VALUE);
        System.out.println("values: " + b + ", " + s + ", " + i + ", " + l);

        byte a = 10, c = 20;
        int sum = a + c;
        System.out.println("byte + byte -> int: " + sum);

        System.out.println("int overflow: " + (Integer.MAX_VALUE + 1));
        System.out.println("promote before add: " + ((long) Integer.MAX_VALUE + 1));
        System.out.println("narrowing long -> int: " + (int) 3_000_000_000L);

        System.out.println("division: " + (-7 / 3) + ", remainder: " + (-7 % 3));
        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException ex) {
            System.out.println("addExact: overflow detected");
        }
        System.out.println("unsigned view of -1: " + Integer.toUnsignedLong(-1));
    }
}
```

Biên dịch/chạy:

```bash
javac IntegerTypesDemo.java
java IntegerTypesDemo
```

Đầu ra:

```text
byte: -128 .. 127
short: -32768 .. 32767
int: -2147483648 .. 2147483647
long: -9223372036854775808 .. 9223372036854775807
values: 127, 30000, 2000000000, 3000000000
byte + byte -> int: 30
int overflow: -2147483648
promote before add: 2147483648
narrowing long -> int: -1294967296
division: -2, remainder: -1
addExact: overflow detected
unsigned view of -1: 4294967295
```

**Giải thích:** Tổng hai `byte` được xử lý theo `int`; cộng `Integer.MAX_VALUE + 1` tràn miền `int`; nâng một toán hạng lên `long` trước khi cộng giữ được `2147483648`; cast `3_000_000_000L` về `int` bỏ bit cao; chia âm cắt phần dư thập phân về 0; `Math.addExact` chủ động phát hiện overflow; `Integer.toUnsignedLong` chỉ đổi cách hiểu bit.

## 10. Tiện ích của wrapper và boxing

| Việc cần làm | API | Lưu ý |
| --- | --- | --- |
| Đọc giới hạn | `Integer.MIN_VALUE`, `Long.MAX_VALUE` | Tránh số cố định khó kiểm chứng |
| Đổi chuỗi thành số | `Integer.parseInt("42")`, `Long.parseLong("3000000000")` | `NumberFormatException` nếu sai định dạng hoặc vượt miền |
| Đổi số thành chuỗi | `Integer.toString(x)`, `Long.toString(y)` | Không mở rộng phạm vi dữ liệu |
| So sánh | `Integer.compare(x, y)`, `Long.compare(x, y)` | An toàn hơn cách so sánh dựa vào dấu của `x-y` khi phép trừ có thể tràn |
| Kiểm tra phép tính | `Math.addExact`, `Math.multiplyExact`, `Math.toIntExact` | Phát hiện một số lỗi overflow |
| Chuyển cách hiểu bit | `Byte.toUnsignedInt`, `Integer.toUnsignedLong` | Hữu ích cho binary/network protocol |

`Byte`, `Short`, `Integer` và `Long` là **wrapper reference types**, khác với `byte`, `short`, `int` và `long` primitive. `List<Integer>` chứa wrapper, có boxing/unboxing. Wrapper có thể là `null`, và **unboxing `null` gây `NullPointerException`**. Đối với mảng `int[]`, phần tử là giá trị primitive, không phải object `Integer` riêng lẻ.

## 11. Chọn kiểu phù hợp

| Nhu cầu | Kiểu gợi ý | Vì sao |
| --- | --- | --- |
| Số lượng, bộ đếm, vòng lặp, chỉ số mảng, số học thông thường | `int` | Đủ cho phần lớn số nguyên thông thường và phù hợp cơ chế nâng kiểu |
| Timestamp, khoảng thời gian theo mili giây, bộ đếm rất lớn | `long` | Miền rộng hơn `int`; vẫn cần kiểm tra tràn phép tính |
| Một byte dữ liệu trong file, buffer hoặc giao thức | `byte`/`byte[]` | Phù hợp đơn vị dữ liệu 8 bit; có thể diễn giải unsigned khi cần |
| Dữ liệu nguồn thực sự yêu cầu số nguyên 16 bit có dấu | `short` | Thể hiện chính xác giới hạn/định dạng 16 bit |
| Số nguyên vượt miền 64 bit | `BigInteger` | Không giới hạn ở 64 bit cố định |

Đừng dùng `byte` hoặc `short` chỉ vì nghĩ phép toán sẽ hiệu quả hơn; nhiều toán tử Java vẫn nâng chúng lên `int`. Nếu dữ liệu nguồn cho phép tới 255, một `byte` có dấu **không** biểu diễn trực tiếp mọi giá trị 0..255 theo ngữ nghĩa có dấu. Hãy xác định đơn vị, miền giá trị và cách chuyển đổi trước khi chọn kiểu.

## 12. Lỗi thường gặp và cách tránh

1. **Nhầm `int` là 64 bit trên máy 64 bit:** Java giữ độ rộng cố định 32/64 bit.
2. **Quên `L` với literal thập phân lớn:** `long x = 3_000_000_000;` không hợp lệ; cần `3_000_000_000L`.
3. **Gán `byte + byte` về `byte`:** kết quả biểu thức là `int`; phải đổi khai báo đích hoặc cast sau khi kiểm tra miền.
4. **Ép kiểu sau khi biểu thức đã tràn:** `long r = (long) (x * y);` không phục hồi bit mất; nâng toán hạng trước phép nhân.
5. **Cho rằng cast tự báo lỗi/kẹp giới hạn:** `(byte) 130` bằng `-126`, không phải 127.
6. **Cho rằng cộng nguyên tự ném overflow:** cộng/trừ/nhân thường quấn vòng; dùng các hàm `Exact` khi cần.
7. **Chia số nguyên và mong ra số lẻ:** `1 / 2` bằng 0; cần dùng phép toán kiểu số thực hoặc biểu diễn số thập phân theo yêu cầu khác.
8. **Cho rằng `-7 / 3` bằng -3:** phép chia nguyên `/` cắt về 0 nên bằng -2.
9. **Dùng `x - y` để lấy dấu so sánh:** hiệu có thể tràn; dùng phép so sánh hoặc `Integer.compare`.
10. **Cho rằng `byte` là số nguyên 0..255:** đó là cách diễn giải không dấu; `byte` primitive có dấu là -128..127.

## 13. Tự kiểm tra

- `short` có chứa 40.000 không? **Không**, vì tối đa là 32.767.
- `long id = 5_000_000_000;` có biên dịch không? **Không**; literal thập phân phải có `L`.
- `byte a = 5, b = 6;` thì `a + b` có kiểu gì? **`int`**.
- `int x = 1_500_000_000; long y = x * 2;` có cho 3 tỷ không? **Không**, biểu thức nhân kiểu `int` đã tràn. Dùng `x * 2L`.
- `Math.addExact(Integer.MAX_VALUE, 1)` có trả một `int` bị quấn vòng không? **Không**; nó ném `ArithmeticException`.
- `Integer.toUnsignedLong(-1)` là bao nhiêu? **4_294_967_295L**, trong khi biến `int` đầu vào vẫn là -1.

**Ghi nhớ:** `byte`/`short`/`int`/`long` đều lưu chính xác số nguyên **trong phạm vi của mình**. Khác biệt cốt lõi là **8 / 16 / 32 / 64 bit**. Trước khi tính toán, xác định kiểu của **literal và toán hạng**; trước khi thu hẹp, kiểm tra phạm vi; khi cần phát hiện tràn, dùng API kiểm tra phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-types-002">Kích thước bit và miền giá trị của từng kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Kích thước bit và miền giá trị của từng kiểu số nguyên trong Java

## 1. Vì sao phải biết độ rộng bit và miền giá trị?

Mỗi kiểu số nguyên nguyên thủy (*primitive integral type*) trong Java có **độ rộng bit cố định**. Độ rộng đó quyết định có bao nhiêu mẫu bit khác nhau, và cách Java diễn giải các bit quyết định **giá trị nhỏ nhất/lớn nhất** có thể lưu.

Ví dụ, 8 bit có \(2^8 = 256\) tổ hợp. Nếu dùng kiểu có dấu `byte`, Java dành các tổ hợp ấy cho các giá trị từ **-128 đến 127**, chứ không phải từ 0 đến 255. Hiểu quy tắc này giúp:

- Chọn kiểu phù hợp cho dữ liệu, tránh vô tình vượt miền.
- Giải thích vì sao phép toán có thể **tràn số** (*overflow*) và cho kết quả bất ngờ.
- Hiểu chuyển kiểu (*casting*), phép toán bit và dữ liệu nhị phân.
- Phân biệt một **kiểu nguyên thủy** với đối tượng lớp bao (*wrapper*).

Bài học này xét các kiểu nguyên thủy tích phân `byte`, `short`, `int`, `long` và `char`. Bốn kiểu đầu là số nguyên **có dấu**; `char` là một giá trị tích phân **không dấu** dùng làm đơn vị mã UTF-16. `boolean` chỉ nhận `true/false` nên không thuộc bảng số nguyên.

## 2. Bảng độ rộng và miền giá trị

| Kiểu | Số bit | Số byte | Giá trị nhỏ nhất | Giá trị lớn nhất | Số giá trị khác nhau |
|---|---:|---:|---:|---:|---:|
| `byte` | 8 | 1 | -128 | 127 | \(2^8 = 256\) |
| `short` | 16 | 2 | -32_768 | 32_767 | \(2^{16} = 65\,536\) |
| `int` | 32 | 4 | -2_147_483_648 | 2_147_483_647 | \(2^{32}\) |
| `long` | 64 | 8 | -9_223_372_036_854_775_808 | 9_223_372_036_854_775_807 | \(2^{64}\) |
| `char` | 16 | 2 | 0 (U+0000) | 65_535 (U+FFFF) | \(2^{16} = 65\,536\) |

**Đây là độ rộng được đặc tả bởi Java**, giống nhau trên JVM 32 bit hoặc 64 bit. Một `int` của Java luôn có 32 bit, không phụ thuộc kích thước `int` trong ngôn ngữ C trên nền tảng khác.

Độ rộng tính theo bit của kiểu nguyên thủy cũng **không đồng nghĩa** với kích thước một đối tượng `Integer` hoặc `Long` trên heap. Đối tượng có header, căn chỉnh và overhead khác; không thể lấy 32 bit để kết luận đối tượng `Integer` dùng 4 byte bộ nhớ.

Trong Java, 1 byte gồm 8 bit. Các lớp bao công bố hằng số tương ứng: `Byte.SIZE/BYTES`, `Short.SIZE/BYTES`, `Integer.SIZE/BYTES`, `Long.SIZE/BYTES`, `Character.SIZE/BYTES`; miền giá trị nằm ở `MIN_VALUE` và `MAX_VALUE`. Các hằng số này hữu ích để tra cứu, nhưng điều cốt lõi là hiểu **cách tính và ý nghĩa** của chúng.

## 3. Suy ra miền giá trị từ số bit

### 3.1. Kiểu có dấu: số bù hai (*two's complement*)

Với kiểu có dấu rộng **n bit** (`byte`, `short`, `int`, `long`), Java dùng biểu diễn số bù hai. Miền giá trị là:

\[
\boxed{-2^{n-1} \le x \le 2^{n-1}-1}
\]

- **Giới hạn dưới**: \(-2^{n-1}\).
- **Giới hạn trên**: \(2^{n-1}-1\).
- **Số giá trị biểu diễn được**: \(2^n\), kể cả số 0.

Áp dụng cho `byte` (\(n=8\)): \(-2^7=-128\) tới \(2^7-1=127\).

Áp dụng cho `short` (\(n=16\)): \(-2^{15}=-32\,768\) tới \(2^{15}-1=32\,767\).

Áp dụng cho `int` (\(n=32\)): \(-2^{31}\) tới \(2^{31}-1\).

Áp dụng cho `long` (\(n=64\)): \(-2^{63}\) tới \(2^{63}-1\).

**Tại sao trị tuyệt đối giới hạn âm lớn hơn giới hạn dương một đơn vị?** Vì 0 chiếm một giá trị trong tập không âm. Ví dụ với `byte` có 128 giá trị âm (-128 đến -1) và 128 giá trị không âm (0 đến 127). Do đó không có giá trị `byte` dương +128 để đối xứng với -128.

### 3.2. Nhìn mẫu bit 8 bit

Với `byte`, một số ví dụ:

| Mẫu bit (8 bit) | Cách hiểu `byte` có dấu |
|---|---:|
| `0000 0000` | 0 |
| `0000 0001` | 1 |
| `0111 1111` | 127 |
| `1000 0000` | -128 |
| `1111 1111` | -1 |

Bit cao nhất có trọng số \(-2^7\) trong biểu diễn bù hai 8 bit, các bit còn lại có trọng số \(2^6,\ldots,2^0\). Vì vậy `1000 0000` là -128; `1111 1111` là \(-128+64+32+16+8+4+2+1=-1\).

Cách nhớ thực hành: đổi dấu theo bù hai bằng **đảo các bit rồi cộng 1, trong đúng độ rộng n bit**. Ví dụ, 8 bit của +1 là `0000 0001`; đảo bit thành `1111 1110`, cộng 1 được `1111 1111` tức -1.

Trong Java, kết quả của các biểu thức bit trên `byte` thường được **thăng cấp lên `int`**, nên khi thực hành đảo bit `~` phải xét độ rộng và phép ép kiểu. Không suy diễn `~((byte)1)` sẽ tự giữ đúng 8 bit: biểu thức đó có kiểu `int`.

### 3.3. `char`: 16 bit nhưng không có dấu

`char` có 16 bit, nhưng miền giá trị là:

\[
0 \le \texttt{char} \le 2^{16}-1 = 65535
\]

Nó đại diện một **UTF-16 code unit**, không phải số nguyên có dấu 16 bit như `short`. Do đó hai kiểu cùng rộng 16 bit nhưng có miền khác nhau:

- `short`: -32_768 tới 32_767.
- `char`: 0 tới 65_535.

Một ký tự Unicode nằm ngoài Basic Multilingual Plane (BMP), chẳng hạn emoji, cần **cặp surrogate gồm hai `char`**. Vì vậy một Unicode code point không nhất thiết khớp một `char`. Đếm ký tự theo code point có thể dùng `String.codePointCount(...)` hoặc `String.codePoints()`, thay vì mặc định `String.length()` là số ký tự hiển thị.

## 4. Chạy Java để quan sát

Tạo file `PrimitiveRangeDemo.java`:

```java
public class PrimitiveRangeDemo {
    public static void main(String[] args) {
        System.out.printf("byte: %d..%d (%d bits)%n",
                Byte.MIN_VALUE, Byte.MAX_VALUE, Byte.SIZE);
        System.out.printf("short: %d..%d (%d bits)%n",
                Short.MIN_VALUE, Short.MAX_VALUE, Short.SIZE);
        System.out.printf("int: %d..%d (%d bits)%n",
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.SIZE);
        System.out.printf("long: %d..%d (%d bits)%n",
                Long.MIN_VALUE, Long.MAX_VALUE, Long.SIZE);
        System.out.printf("char: %d..%d (%d bits)%n",
                (int) Character.MIN_VALUE, (int) Character.MAX_VALUE,
                Character.SIZE);

        System.out.println("int bytes: " + Integer.BYTES);
        System.out.println("char bytes: " + Character.BYTES);
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac PrimitiveRangeDemo.java
java PrimitiveRangeDemo
```

Kết quả:

```text
byte: -128..127 (8 bits)
short: -32768..32767 (16 bits)
int: -2147483648..2147483647 (32 bits)
long: -9223372036854775808..9223372036854775807 (64 bits)
char: 0..65535 (16 bits)
int bytes: 4
char bytes: 2
```

Hai phép ép `(int)` ở dòng `char` nhằm in **giá trị số** của code unit. Nếu in `Character.MIN_VALUE` trực tiếp như ký tự, bạn có thể nhận một ký tự điều khiển không nhìn thấy.

## 5. Khi vượt miền giá trị: tràn và ép kiểu

### 5.1. Tràn số trong phép tính

Một `int` chỉ lưu được đến 2_147_483_647. Khi cộng thêm 1 bằng phép cộng `int`, kết quả quay về -2_147_483_648 theo số học modulo \(2^{32}\). Phép cộng số nguyên thông thường **không tự ném ngoại lệ**.

Cần phân biệt hai thời điểm mở rộng sang `long`:

- `(long) (max + 1)`: phép cộng **đã chạy bằng `int` và tràn** trước khi đổi kiểu.
- `(long) max + 1`: `max` được đổi thành `long` **trước phép cộng**, nên đủ miền biểu diễn.

### 5.2. Thu hẹp kiểu (*narrowing conversion*)

Ép `int` 128 sang `byte` giữ 8 bit thấp của giá trị, diễn giải lại theo miền có dấu 8 bit và cho **-128**. Ép kiểu trực tiếp không kiểm tra miền để ném lỗi.

Với gán hằng số, trình biên dịch có thể cho phép gán trực tiếp `byte b = 127;` vì 127 nằm trong miền `byte`; nhưng `byte b = 128;` là lỗi biên dịch vì vượt miền, trừ khi bạn chủ động ép kiểu.

### 5.3. Ví dụ có thể chạy

Tạo file `BoundaryDemo.java`:

```java
public class BoundaryDemo {
    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;

        System.out.println("int max + 1 = " + (max + 1));
        System.out.println("widen after = " + (long) (max + 1));
        System.out.println("widen before = " + ((long) max + 1));
        System.out.println("(byte) 128 = " + (byte) 128);
        System.out.println("byte max + 1 = " + (Byte.MAX_VALUE + 1));

        try {
            System.out.println(Math.addExact(max, 1));
        } catch (ArithmeticException e) {
            System.out.println("addExact: overflow");
        }

        long outsideInt = 2_147_483_648L;
        try {
            System.out.println(Math.toIntExact(outsideInt));
        } catch (ArithmeticException e) {
            System.out.println("toIntExact: out of int range");
        }
    }
}
```

Chạy:

```bash
javac BoundaryDemo.java
java BoundaryDemo
```

Kết quả:

```text
int max + 1 = -2147483648
widen after = -2147483648
widen before = 2147483648
(byte) 128 = -128
byte max + 1 = 128
addExact: overflow
toIntExact: out of int range
```

`Byte.MAX_VALUE + 1` in ra 128 vì toán tử cộng **thăng cấp `byte` thành `int`**, nên kết quả phép cộng có kiểu `int`. Tràn 8 bit chỉ xuất hiện nếu ép kết quả về `byte` hoặc tính trực tiếp trong một kiểu bị thu hẹp.

Các phương thức `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`, `Math.negateExact` và `Math.toIntExact` giúp phát hiện trường hợp tràn/ngoài miền liên quan. Chúng ném `ArithmeticException` ở trường hợp không biểu diễn được.

## 6. Ký hiệu số nguyên và các giới hạn cần nhớ

### 6.1. Literal `int` và `long`

Mặc định, literal số nguyên thập phân không có hậu tố là `int` khi hợp lệ; thêm `L` để viết `long`:

```java
int population = 2_000_000_000;
long bigger = 3_000_000_000L;
long maximum = Long.MAX_VALUE;
```

Viết `3_000_000_000` không có hậu tố `L` gây lỗi vì vượt phạm vi literal `int`, ngay cả khi vế trái khai báo `long`. Gạch dưới trong số chỉ giúp dễ đọc; không đổi giá trị hay kiểu.

Hai trị âm thấp nhất `-2147483648` và `-9223372036854775808L` có quy tắc từ vựng đặc biệt trong Java cho toán tử trừ đơn ngôi; để tránh nhầm lẫn, ưu tiên `Integer.MIN_VALUE` và `Long.MIN_VALUE` trong mã ứng dụng.

### 6.2. Một số bẫy ở biên

- `Math.abs(Integer.MIN_VALUE)` vẫn bằng `Integer.MIN_VALUE`, tức số âm, vì +2_147_483_648 vượt miền `int`. Tương tự cho `Long.MIN_VALUE`.
- `-Integer.MIN_VALUE` vẫn bằng `Integer.MIN_VALUE` do tràn khi đổi dấu.
- `Integer.MIN_VALUE / -1` trả lại `Integer.MIN_VALUE` vì thương dương không thể biểu diễn trong `int`.
- Chuyển biểu thức sang `long` **sau khi phép tính tràn** không khôi phục được kết quả ban đầu.
- `SIZE/BYTES` thể hiện độ rộng **giá trị nguyên thủy**, không cho biết tổng bộ nhớ mảng, wrapper object hay cấu trúc dữ liệu (còn phụ thuộc overhead, căn chỉnh và VM).

Nếu số nguyên có thể vượt miền `long`, dùng `java.math.BigInteger`. Nó không có một độ rộng cố định kiểu 64 bit, nên không có miền MIN/MAX cố định tương tự các kiểu nguyên thủy.

## 7. Có kiểu `unsigned int` và `unsigned long` không?

Java **không có kiểu nguyên thủy riêng tên `unsigned int` hay `unsigned long`**. Tuy vậy, Java hỗ trợ các phép toán *diễn giải mẫu bit* dưới góc nhìn không dấu:

- 32 bit không dấu có miền **0 đến 4_294_967_295**, tức \(0\) đến \(2^{32}-1\).
- 64 bit không dấu có miền **0 đến 18_446_744_073_709_551_615**, tức \(0\) đến \(2^{64}-1\); giá trị cao nhất này vượt miền `long` có dấu.
- Các API hỗ trợ gồm `Integer.compareUnsigned`, `Integer.divideUnsigned`, `Integer.toUnsignedLong`, `Integer.toUnsignedString`, cùng các phương thức `Long.compareUnsigned`, `Long.divideUnsigned`, `Long.toUnsignedString`.

Ví dụ:

```java
System.out.println(Integer.toUnsignedLong(-1));
System.out.println(Integer.toUnsignedString(-1));
```

Cả hai dòng in ra `4294967295`. Mẫu bit `0xFFFF_FFFF` có thể hiểu là -1 nếu diễn giải theo `int` có dấu, hoặc 4_294_967_295 nếu diễn giải theo 32 bit không dấu. Các API unsigned **không thay đổi độ rộng 32 bit hoặc 64 bit** của giá trị lưu trong biến.

Riêng `char` vốn là kiểu không dấu 16 bit, dùng cho code unit UTF-16; đừng dùng `char` để thay thế số nguyên không dấu thông thường chỉ vì cùng độ rộng `short`.

## 8. Chọn kiểu cho dữ liệu và kiểm tra đầu vào

**`byte`** phù hợp khi cần biểu diễn các giá trị trong -128..127 hoặc thao tác byte nhị phân. Khi muốn giá trị 0..255 từ một byte nhị phân, xem `Byte.toUnsignedInt(...)` hoặc `b & 0xFF`.

**`short`** phù hợp khi một API, định dạng tệp hoặc giao thức quy định chính xác 16 bit có dấu; trong tính toán nghiệp vụ thông thường, `int` đơn giản và phổ biến hơn.

**`int`** là lựa chọn thường gặp cho chỉ số, biến đếm và số nguyên 32 bit có giới hạn rõ ràng. Với chỉ số mảng Java, `int` là kiểu cơ bản hay dùng; kích thước mảng còn bị giới hạn bởi JVM/bộ nhớ, không chỉ bởi `Integer.MAX_VALUE`.

**`long`** dùng khi cần miền lớn hơn, như số đếm lớn, mili giây từ mốc thời gian hoặc lượng dữ liệu có thể vượt 32 bit. Dù vậy, `long` cũng hữu hạn: nếu phép tính có nguy cơ vượt 64 bit, cân nhắc `BigInteger` hoặc quy tắc kiểm tra tràn.

**`char`** dùng khi làm việc ở mức đơn vị mã UTF-16; nếu yêu cầu xử lý **Unicode code point**, chọn API duyệt code point để tránh chia đôi surrogate pair.

Khi nhận dữ liệu từ bên ngoài rồi ép sang kiểu hẹp, kiểm tra giá trị trước khi đổi kiểu:

```java
static byte requireByteRange(int value) {
    if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
        throw new IllegalArgumentException("outside byte range");
    }
    return (byte) value;
}
```

Đây là **đoạn phương thức minh họa** (đặt trong một class để dùng), không phải chương trình có `main` độc lập. Điểm chính là phải kiểm tra **trước** khi ép kiểu: nếu ép trước, dữ liệu có thể đã mất bit quan trọng.

## 9. Tự kiểm tra kiến thức

1. **8 bit có bao nhiêu mẫu bit?** — \(2^8=256\) mẫu.
2. **Vì sao `byte` có miền -128..127 thay vì -127..127?** — Theo số bù hai, 8 bit mã hóa 128 giá trị âm và 128 giá trị không âm; số 0 đã chiếm một mẫu.
3. **`short` và `char` cùng 16 bit, miền có giống nhau không?** — Không. `short` có dấu: -32_768..32_767; `char` không dấu: 0..65_535.
4. **`int` 32 bit có thể chứa giá trị 3_000_000_000 không?** — Không dưới cách diễn giải có dấu. Có thể dùng `long` hoặc diễn giải mẫu bit unsigned bằng API tương ứng.
5. **Vì sao `long result = Integer.MAX_VALUE + 1;` vẫn nhận kết quả âm?** — Hai toán hạng được cộng bằng `int` trước, gây tràn; kết quả âm sau đó mới được mở rộng sang `long`. Dùng `long result = (long) Integer.MAX_VALUE + 1;`.
6. **Một emoji có bắt buộc chỉ chiếm 1 `char` vì `char` là 16 bit?** — Không. Unicode code point ngoài BMP thường cần hai code unit UTF-16, tức hai `char`.
7. **`long` có bảo đảm mọi số nguyên đều lưu được?** — Không. Miền của `long` có dấu chỉ từ \(-2^{63}\) đến \(2^{63}-1\); số lớn hơn cần cách biểu diễn khác như `BigInteger`.

**Cốt lõi:** Độ rộng **n bit** cho \(2^n\) mẫu bit; với kiểu **có dấu**, miền là \([-2^{n-1},2^{n-1}-1]\); với `char` **không dấu 16 bit**, miền là \([0,2^{16}-1]\). Luôn xác định **kiểu thực sự tham gia phép tính** trước khi suy luận về tràn số hoặc chuyển kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-types-003">Biểu diễn số âm bằng bù hai</a>

<details>
<summary>Xem chi tiết</summary>

# Biểu diễn số âm bằng bù hai (two's complement) trong Java

> **Java core – numbers · Kiểu số nguyên**  
> **Mục tiêu:** Đọc được mẫu bit của số nguyên âm; tự mã hóa/giải mã bù hai; hiểu vì sao Java dịch bit, mở rộng dấu và tràn số như vậy; áp dụng đúng khi xử lý byte, mask và dữ liệu nhị phân.

## 1. Vì sao cần biểu diễn số âm bằng bit?

Máy tính lưu bit `0` và `1`. Với cùng 8 bit, mẫu `11111011` không tự mang ý nghĩa “−5” hay “251”: **kiểu dữ liệu và quy ước diễn giải** quyết định ý nghĩa của nó. Nếu coi 8 bit là số nguyên **không dấu**, mẫu này bằng 251; nếu coi là số nguyên **có dấu theo bù hai**, nó bằng −5.

Java sử dụng biểu diễn bù hai cho các kiểu nguyên có dấu `byte`, `short`, `int` và `long`. Nhờ đó, cùng mạch cộng nhị phân có thể xử lý cả số dương và âm theo phép toán modulo một lũy thừa của hai. Bù hai cũng giải thích được mẫu bit của số âm, cách lan truyền dấu khi mở rộng kiểu và kết quả khi dịch bit.

Bài này chỉ xét **số nguyên độ rộng cố định**. `float`/`double` sử dụng quy tắc dấu phẩy động IEEE 754, **không** biểu diễn phần số âm của chúng theo bù hai. `char` là mã đơn vị UTF-16 16 bit không dấu; nó cũng không phải kiểu số nguyên âm.

## 2. Độ rộng bit, bit dấu và miền giá trị

Với **n bit**, có đúng `2^n` mẫu bit khác nhau. Trong bù hai:

- Bit bên trái cùng (bit cao nhất, **MSB**) là **bit dấu**: bằng `0` thì giá trị không âm; bằng `1` thì giá trị âm.
- Khoảng giá trị là **−2^(n−1) đến 2^(n−1) − 1**. Số âm ít nhất hơn số dương nhiều nhất một đơn vị vì có giá trị `0`.
- Bit bên phải cùng (bit thấp nhất, **LSB**) là bit số `0`; chỉ số bit tăng dần từ phải sang trái.

| Kiểu Java | Độ rộng | Miền giá trị | Bit dấu |
| --- | ---: | --- | ---: |
| `byte` | 8 | −128 đến 127 | 7 |
| `short` | 16 | −32.768 đến 32.767 | 15 |
| `int` | 32 | −2.147.483.648 đến 2.147.483.647 | 31 |
| `long` | 64 | −9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 | 63 |

Ví dụ 8 bit:

```text
Bit index:      7 6 5 4 3 2 1 0
                ^             ^
             bit dấu         LSB

 0000 0000 =    0
 0000 0001 =   +1
 0111 1111 = +127
 1000 0000 = -128
 1111 1110 =   -2
 1111 1111 =   -1
```

**Không phải** “bit đầu là dấu, 7 bit còn lại là trị tuyệt đối”. Đó là một cách biểu diễn khác tên *sign–magnitude*. Trong bù hai, toàn bộ 8 bit cùng tham gia giá trị theo quy tắc riêng; `1000 0000` là **−128**, không phải “−0”. Vì thế bù hai có một biểu diễn duy nhất cho số không ở độ rộng cố định.

## 3. Từ số dương sang số âm: đảo bit rồi cộng 1

Để tìm biểu diễn **−x** với `x > 0` và `x` nằm trong phạm vi biểu diễn phù hợp của độ rộng đang xét:

1. Viết `+x` bằng **đủ n bit**, thêm `0` bên trái nếu cần.
2. **Đảo tất cả n bit**: `0 ↔ 1` (bù một / one's complement).
3. **Cộng 1**, giữ n bit thấp nếu có bit nhớ vượt ra khỏi n bit.

Ví dụ `−5` trong **8 bit**:

```text
 +5:                   0000 0101
 Đảo từng bit:         1111 1010
 Cộng 1:             + 0000 0001
                       ---------
 -5:                   1111 1011
```

Mẫu `11111011` là `0xFB` trong hệ 16. Nếu diễn giải **không dấu**, nó bằng `251`; nếu diễn giải **có dấu bù hai**, nó bằng `−5`. Chuyển từ 251 sang −5 ở đây là **đổi cách diễn giải cùng mẫu 8 bit**, không phải thay bit.

Ta cũng có thể thấy `2^8 − 5 = 256 − 5 = 251`. Tổng quát, đối với số âm `−x` trong n bit, mẫu bit không dấu tương ứng là `2^n − x` (với `1 ≤ x ≤ 2^(n−1)`).

Ví dụ `−13` trong 8 bit:

```text
 +13:       0000 1101
 Đảo bit:   1111 0010
 Cộng 1:    1111 0011   = 0xF3 = 243 nếu đọc không dấu
 Đọc có dấu: 243 - 256 = -13
```

**Cẩn thận với độ rộng:** thao tác đảo bit bằng toán tử `~` trong Java trên `int` có hiệu lực 32 bit; muốn minh họa một mẫu 8 bit, lấy 8 bit thấp bằng mask `& 0xFF`. Phương pháp “đảo rồi cộng 1” được trình bày ở đây trên **độ rộng đã chọn**, không phải tự động áp dụng theo số lượng chữ số trong cách in ra màn hình.

## 4. Giải mã một mẫu bit bù hai

Cho mẫu n bit, gọi `U` là giá trị nếu đọc **không dấu** (0 đến `2^n − 1`). Giá trị **có dấu** `S` là:

```text
Nếu bit dấu = 0: S = U
Nếu bit dấu = 1: S = U - 2^n
```

Ví dụ 8 bit `1110 0111`:

- Không dấu: `128 + 64 + 32 + 4 + 2 + 1 = 231`.
- Bit dấu = `1` nên có dấu: `231 − 256 = −25`.
- Kiểm tra ngược: `+25 = 0001 1001`; đảo `1110 0110`; cộng 1 được `1110 0111`.

Một cách tương đương là quy tắc **trọng số của bit cao nhất âm**:

```text
Với 8 bit:
b7 b6 b5 b4 b3 b2 b1 b0
 |
 └── trọng số -128, các bit còn lại có trọng số +64,+32,+16,+8,+4,+2,+1

1110 0111 = -128 + 64 + 32 + 4 + 2 + 1 = -25
```

Hai cách giải mã luôn cho cùng một giá trị. Cách `U − 2^n` đặc biệt tiện khi đọc byte từ tệp hoặc từ mạng dưới dạng số không dấu.

## 5. Vì sao cộng, trừ và đổi dấu có thể dùng cùng biểu diễn?

Các phép cộng, trừ trên nguyên độ rộng cố định giữ **n bit thấp** của kết quả, tương đương cộng/trừ theo **modulo `2^n`**. Ví dụ 8 bit:

```text
    0000 0101  (+5)
  + 1111 1011  (-5)
  -----------
  1 0000 0000  (9 bit)
    0000 0000  (giữ 8 bit thấp) = 0
```

Đó là lý do `x + (−x) = 0` ở tầng mẫu bit, kể cả khi phép tính tạm có bit nhớ ngoài độ rộng. Trong Java, hệ thức `~x + 1` cho cùng mẫu bit với `-x` khi `x` là một `int` (tương tự với `long`). Do đó:

```java
int x = 5;
int negative = ~x + 1; // -5
int bitwiseNot = ~x;   // -6; vì ~x = -x - 1
```

Tuy nhiên, bit nhớ ra ngoài độ rộng **không phải** tiêu chí duy nhất cho **tràn số có dấu**. Ví dụ cộng hai số dương 8 bit `127 + 1` được mẫu `1000 0000`, tức `−128` nếu diễn giải có dấu. Phép toán toán học là 128 nhưng 128 nằm ngoài miền có dấu 8 bit.

### Khi nào tràn số?

Với hai số có dấu cùng độ rộng, cộng bị tràn có dấu nếu **hai toán hạng cùng dấu** nhưng kết quả có **dấu ngược lại**. Trong Java, cộng `int` hoặc `long` thông thường không ném lỗi khi tràn; kết quả giữ bit thấp theo độ rộng của kiểu.

Lưu ý đặc biệt với `byte`/`short`: trong biểu thức như `(byte) (127 + 1)`, phép cộng `127 + 1` diễn ra bằng **`int`** và cho 128, sau đó phép ép hẹp thành `byte` mới lấy 8 bit thấp để ra `−128`. Do đó không nên gọi đây là “phép cộng 8 bit trực tiếp của Java”.

Dùng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`, `Math.negateExact` cho `int`/`long` khi muốn **phát hiện tràn** bằng `ArithmeticException`. Dùng `BigInteger` nếu phép toán thực sự cần số nguyên lớn hơn miền `long`.

## 6. Giá trị âm nhỏ nhất và tính bất đối xứng

Với 8 bit:

```text
 0111 1111 = +127   (MAX)
 1000 0000 = -128   (MIN)
```

Không tồn tại `+128` trong `byte`. Nếu đảo bit của `1000 0000` rồi cộng 1 trong đúng 8 bit, ta nhận lại `1000 0000`: `−(−128)` có giá trị toán học +128 nhưng không nằm trong miền biểu diễn, nên mẫu bit quay lại −128.

Tương tự:

```java
int min = Integer.MIN_VALUE;
System.out.println(min);             // -2147483648
System.out.println(-min);            // -2147483648 (tràn)
System.out.println(Math.abs(min));   // -2147483648 (vẫn âm)
```

Đây là **trường hợp biên đặc biệt**, không phải tính chất rằng “phủ định số âm luôn trả lại chính nó”. `Math.negateExact(Integer.MIN_VALUE)` ném `ArithmeticException` để báo không thể biểu diễn giá trị dương tương ứng bằng `int`.

Nếu cần giá trị dương tương ứng của `Integer.MIN_VALUE`, hãy **mở rộng trước**: `long positive = -(long) Integer.MIN_VALUE;` cho `2147483648L`. Với `Long.MIN_VALUE`, `long` không còn kiểu primitive nguyên lớn hơn; có thể dùng `BigInteger.valueOf(Long.MIN_VALUE).negate()`.

## 7. Mở rộng dấu khi đổi kiểu: vì sao `byte -5` thành `int -5`?

Khi đổi một số nguyên có dấu từ loại hẹp sang loại rộng hơn, Java **mở rộng dấu** (*sign extension*): sao chép bit dấu vào các bit cao vừa thêm để giữ nguyên giá trị.

```text
byte -5 (8 bit):   1111 1011

int -5 (32 bit):   1111 1111 1111 1111 1111 1111 1111 1011
                   ^^^^^^^^^^^^^^^^^^^^^^^^
                   các bit mới đều là 1 vì số gốc âm

Hex:
byte pattern:      FB
int -5:            FFFFFFFB
```

Ví dụ:

```java
byte signedByte = (byte) 0xFB; // -5
int signedInt = signedByte;    // -5, do mở rộng dấu
int unsigned = signedByte & 0xFF; // 251, giữ đúng 8 bit thấp
int viaApi = Byte.toUnsignedInt(signedByte); // 251
```

**Mask `0xFF`** có tám bit thấp bằng 1. Sau khi `signedByte` được nâng lên `int`, phép `& 0xFF` xóa 24 bit cao, giữ mẫu byte nguyên vẹn và diễn giải nó như giá trị 0..255. Đây là thao tác nền tảng khi parse giao thức nhị phân có các **octet không dấu**.

Cần phân biệt:

- `byte → int`: **mở rộng dấu**, bảo toàn giá trị có dấu.
- `byte & 0xFF`: **lấy 8 bit thấp**, cho cách diễn giải không dấu.
- `int → byte`: **narrowing cast**, chỉ giữ 8 bit thấp rồi diễn giải chúng theo bù hai; các bit cao bị bỏ đi. Ví dụ `(byte) 255 == -1`.
- `char → int`: chuyển mã `char` **không dấu** sang số nguyên dương, không mở rộng dấu như `short` âm. `char` và `short` cùng 16 bit nhưng khác cách diễn giải.

## 8. Dịch bit trên số âm: `>>` khác `>>>`

Các phép dịch chỉ làm việc với **mẫu bit**, sau đó kiểu Java quyết định cách diễn giải kết quả. Ba toán tử quan trọng:

| Toán tử | Hành vi | Ý nghĩa thường gặp |
| --- | --- | --- |
| `<<` | Dịch trái, các bit thấp mới điền `0`; bit cao bị bỏ | Nhân với `2^k` trong phạm vi có thể biểu diễn; có thể tràn |
| `>>` | Dịch phải **số học**, điền bit dấu từ trái | Giữ dấu; với số âm tương tự làm tròn phép chia cho `2^k` về −∞ |
| `>>>` | Dịch phải **logic**, điền `0` từ trái | Xử lý mẫu bit như số không dấu ở độ rộng `int`/`long` |

Với `int x = -5`:

```text
x (int):  11111111 11111111 11111111 11111011   = -5
x >> 1:  11111111 11111111 11111111 11111101   = -3
x >>> 1: 01111111 11111111 11111111 11111101   = 2147483645
```

Trong Java, phép chia nguyên `-5 / 2` trả **−2** vì phép chia nguyên làm tròn về 0, trong khi `-5 >> 1` trả **−3** (dịch phải số học làm tròn về −∞ với số âm). **Không thay** `x / 2` bằng `x >> 1` một cách máy móc nếu `x` có thể âm.

Cẩn thận với `byte`, `short` và `char`: trước khi thực hiện `<<`, `>>` hoặc `>>>`, toán hạng trái được **promote thành `int`**. Ví dụ:

```java
byte b = -5;
int promotedShift = b >>> 1;        // 2147483645 (dịch 32 bit của -5)
int unsignedByteShift = (b & 0xFF) >>> 1; // 125 (dịch giá trị byte không dấu 251)
```

Một số chi tiết về số lượng dịch: với `int` chỉ 5 bit thấp của số lượng dịch được dùng (thực tế modulo 32); với `long` là 6 bit thấp (modulo 64). Vì vậy `1 << 32` bằng `1`, không phải 0; `1L << 32` có kết quả khác vì toán hạng trái là `long`.

## 9. Toán tử bit và hệ 16 khi đọc bù hai

Các toán tử `&` (AND), `|` (OR), `^` (XOR), `~` (NOT) giúp thao tác bit. Hệ 16 thuận tiện vì **một chữ số hex tương ứng đúng 4 bit**.

```text
8 bit:     1111 1011  = 0xFB
32 bit:    11111111 11111111 11111111 11111011 = 0xFFFFFFFB

Java:
Integer.toHexString(-5)    -> "fffffffb"
Integer.toBinaryString(-5) -> 32 ký tự bit, bắt đầu bằng '1'
```

`Integer.toBinaryString` cho số âm trả đủ 32 bit bù hai; cho số không âm nó không tự thêm `0` dẫn đầu để đủ 32 bit. `Long.toBinaryString` cũng tương tự với 64 bit. Khi muốn in **đúng 8 bit thấp**, dùng mask và tự thêm `0` đầu chuỗi.

**Chú ý về literal hex:** `int raw = 0xFFFFFFFF;` mang mẫu 32 bit toàn `1`, được đọc có dấu là `-1`. Nếu cùng mẫu 32 bit được diễn giải theo **không dấu**, giá trị toán học là `4_294_967_295`; biến `int` vẫn lưu đúng bit đó.

Các API `Integer.toUnsignedString(int)`, `Integer.toUnsignedLong(int)`, `Integer.parseUnsignedInt(String)` và `Integer.compareUnsigned` xử lý **cách diễn giải không dấu** của 32 bit. Java không có primitive `unsigned int` riêng; các API này không đổi kiểu `int` thành một kiểu số mới.

## 10. Chương trình Java 17+ chạy độc lập

Tạo tệp **`TwosComplementDemo.java`** với nội dung đầy đủ sau:

```java
public class TwosComplementDemo {
    // Lấy 8 bit thấp rồi thêm số 0 vào đầu để hiển thị đủ 8 bit.
    private static String bits8(int value) {
        String binary = Integer.toBinaryString(value & 0xFF);
        return String.format("%8s", binary).replace(' ', '0');
    }

    public static void main(String[] args) {
        byte minusFive = -5;

        System.out.println("+5   : " + bits8(5));
        System.out.println("~5+1 : " + bits8(~5 + 1));
        System.out.println("-5   : " + bits8(minusFive));

        System.out.println("signed byte: " + minusFive);
        System.out.println("unsigned byte: " + Byte.toUnsignedInt(minusFive));
        System.out.println("sign-extended int hex: "
                + Integer.toHexString(minusFive));

        System.out.println("-5 >> 1 = " + (-5 >> 1));
        System.out.println("-5 >>> 1 = " + (-5 >>> 1));
        System.out.println("8-bit unsigned >>> 1 = "
                + ((minusFive & 0xFF) >>> 1));

        System.out.println("(byte) 128 = " + (byte) 128);
        System.out.println("int MAX + 1 = " + (Integer.MAX_VALUE + 1));
        System.out.println("-int MIN = " + (-Integer.MIN_VALUE));
        System.out.println("unsigned int(-1) = "
                + Integer.toUnsignedString(-1));

        try {
            Math.negateExact(Integer.MIN_VALUE);
        } catch (ArithmeticException ex) {
            System.out.println("negateExact(MIN): overflow detected");
        }
    }
}
```

Chạy ở thư mục chứa tệp:

```bash
javac TwosComplementDemo.java
java TwosComplementDemo
```

**Kết quả chuẩn:**

```text
+5   : 00000101
~5+1 : 11111011
-5   : 11111011
signed byte: -5
unsigned byte: 251
sign-extended int hex: fffffffb
-5 >> 1 = -3
-5 >>> 1 = 2147483645
8-bit unsigned >>> 1 = 125
(byte) 128 = -128
int MAX + 1 = -2147483648
-int MIN = -2147483648
unsigned int(-1) = 4294967295
negateExact(MIN): overflow detected
```

**Đọc kết quả theo từng cơ chế:**

1. `bits8(~5 + 1)` dùng kết quả `int` rồi mask 8 bit; mẫu cuối `11111011` khớp `byte -5`.
2. Cùng mẫu `11111011` đọc theo signed byte thành `−5`, theo unsigned byte thành `251`.
3. Khi ép từ `byte` âm sang `int`, các bit cao thêm `1`; vì thế hiển thị hex là `fffffffb`.
4. `>>` giữ bit dấu, `>>>` điền `0` ở 32 bit; mask trước khi dịch tạo cách dịch logic của 8 bit.
5. Ép `(byte) 128` trả `−128` vì `128` có bit thấp `10000000`.
6. `int MAX + 1` và `-int MIN` đều minh họa miền 32 bit có dấu; `negateExact` giúp phát hiện trường hợp thứ hai.

## 11. Áp dụng khi đọc dữ liệu nhị phân

Giả sử giao thức quy định **một octet không dấu** chứa độ dài 0..255. Một byte có bit `1000 0001` được lưu vào Java `byte` sẽ có giá trị `−127`, nhưng độ dài theo giao thức là **129**.

```java
byte lengthOctet = (byte) 0x81;
int length = Byte.toUnsignedInt(lengthOctet); // 129
```

Ngược lại, nếu giao thức quy định số nguyên **có dấu 8 bit theo bù hai**, giá trị `−127` mới chính xác. Không có “đổi sang không dấu” nào là đúng cho mọi dữ liệu; phải đọc **đặc tả giao thức**, xác định bit-width và signedness, rồi mới diễn giải.

Khi ghép hai octet thành số 16 bit không dấu ở thứ tự *big-endian*:

```java
byte high = (byte) 0xAB;
byte low = (byte) 0xCD;
int unsigned16 = ((high & 0xFF) << 8) | (low & 0xFF);
// unsigned16 = 43981 (0xABCD)
```

Mask từng `byte` **trước** khi ghép tránh dấu âm lan lên các bit cao. Nếu thiếu mask của `low`, phép OR với `int` đã mở rộng dấu có thể làm sai nhiều bit của kết quả. Nếu muốn diễn giải `0xABCD` như số nguyên **có dấu 16 bit**, giá trị sẽ là `43981 − 65536 = −21555`; có thể dùng `(short) unsigned16`.

## 12. Sai lầm phổ biến và cách tự kiểm tra

| Hiểu nhầm | Điều đúng cần nhớ |
| --- | --- |
| Bit đầu là dấu, phần còn lại là độ lớn | Đó là sign–magnitude; bù hai giải mã theo trọng số bit cao âm hoặc `U−2^n` |
| `11111111` luôn bằng −1 | Chỉ khi được diễn giải **có dấu 8 bit**; dạng không dấu là 255 |
| `~5` bằng `−5` | `~5` là −6; `~5 + 1` mới là −5 |
| `int result = 127 + 1` tạo ra `byte -128` | Biểu thức `int` là 128; chỉ khi ép hẹp xuống `byte` mới thành −128 |
| `-Integer.MIN_VALUE` ra số dương | Nó tràn và vẫn bằng `Integer.MIN_VALUE` |
| `-5 >> 1` và `-5 / 2` giống nhau | Dịch phải số học ra −3; chia nguyên ra −2 |
| `byteValue >>> 1` dịch 8 bit | `byte` được promote thành `int` rồi dịch **32 bit** |
| `Integer.toBinaryString(x)` luôn trả đúng 32 ký tự | Số không âm có thể được bỏ `0` dẫn đầu |
| Java có primitive `unsigned int` | Không; dùng `int` và API unsigned để diễn giải mẫu 32 bit |
| `double` cũng lưu số âm theo bù hai | `double` dùng IEEE 754, bài này chỉ mô tả số nguyên có dấu |

Để xử lý bit đúng, luôn xác định **ba thông tin** trước: (1) độ rộng 8/16/32/64 bit, (2) signed hay unsigned, (3) thao tác đang thực hiện **trước hay sau** promotion/cast. Với thao tác nhị phân, hãy viết ví dụ ở hệ 2 hoặc hex và kiểm tra kết quả với mask đúng độ rộng.

## 13. Bài tập tự kiểm tra

**Bài 1.** Biểu diễn `−18` bằng 8 bit bù hai.  
**Bài 2.** Diễn giải `1101 0000` như unsigned và signed 8 bit.  
**Bài 3.** Dự đoán kết quả của `(byte) 255` và `((byte) 255) & 0xFF`.  
**Bài 4.** Vì sao `Integer.MIN_VALUE` là một số âm không có trị dương tương ứng trong `int`?  
**Bài 5.** Giải thích vì sao `byte b = -2; int x = b >>> 1;` không tương đương dịch phải logic 8 bit của `11111110`.

**Đáp án và lời giải:**

1. `+18 = 0001 0010` → đảo `1110 1101` → cộng 1 `1110 1110`, tức `0xEE`.
2. Unsigned: `0xD0 = 208`. Signed: `208 − 256 = −48`.
3. `(byte)255` giữ tám bit thấp `1111 1111` nên là `−1`; sau mask `& 0xFF` kết quả `int 255`.
4. Miền `int` là `−2^31 .. 2^31−1`, thiếu giá trị `+2^31`. Khi phủ định trong 32 bit, bit pattern `1000...0000` quay về chính nó; `Math.negateExact` phát hiện tràn.
5. `b` được nâng lên `int -2` (32 bit có 31 bit `1` và bit cuối `0`); `>>> 1` cho `2147483647`. Muốn dịch mẫu **8 bit** như unsigned `254`, làm `(b & 0xFF) >>> 1` để được `127`.

**Điểm cốt lõi:** Bù hai là cách **diễn giải và tính toán các mẫu bit cố định**. Nắm được quy tắc `U−2^n`, phép `đảo bit + 1`, mở rộng dấu và giữ bit thấp sẽ giúp bạn giải thích phần lớn các kết quả số nguyên âm, cast và dịch bit trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-types-004">MIN_VALUE, MAX_VALUE, SIZE và BYTES</a>

<details>
<summary>Xem chi tiết</summary>

# MIN_VALUE, MAX_VALUE, SIZE và BYTES của các kiểu số nguyên trong Java

## 1. Bốn hằng số dùng để làm gì?

Khi làm việc với số nguyên, cần biết **miền giá trị** mà kiểu dữ liệu biểu diễn được và **độ rộng cố định** của kiểu đó. Java công bố bốn thông tin này qua các lớp bao (*wrapper class*) trong `java.lang`:

- `MIN_VALUE`: giá trị nhỏ nhất biểu diễn được.
- `MAX_VALUE`: giá trị lớn nhất biểu diễn được.
- `SIZE`: số **bit** của kiểu nguyên thủy tương ứng.
- `BYTES`: số **byte** của kiểu nguyên thủy tương ứng, với 1 byte = 8 bit.

Các tên hằng số thuộc lớp bao, chẳng hạn `Integer.MIN_VALUE`, **không thuộc từ khóa kiểu nguyên thủy** `int`. Đây là các trường `public static final` có thể truy cập mà không tạo đối tượng. Các kích thước dưới đây được ngôn ngữ Java quy định, độc lập hệ điều hành và kiến trúc CPU.

## 2. Bảng tra cứu đầy đủ

| Kiểu nguyên thủy | Lớp bao | `MIN_VALUE` | `MAX_VALUE` | `SIZE` (bit) | `BYTES` (byte) |
|---|---|---:|---:|---:|---:|
| `byte` | `Byte` | -128 | 127 | 8 | 1 |
| `short` | `Short` | -32_768 | 32_767 | 16 | 2 |
| `int` | `Integer` | -2_147_483_648 | 2_147_483_647 | 32 | 4 |
| `long` | `Long` | -9_223_372_036_854_775_808 | 9_223_372_036_854_775_807 | 64 | 8 |
| `char` | `Character` | U+0000 (giá trị số 0) | U+FFFF (giá trị số 65_535) | 16 | 2 |

Bốn kiểu `byte`, `short`, `int` và `long` là **số nguyên có dấu**. Với kiểu có độ rộng `n` bit:

- Nhỏ nhất: \(-2^{n-1}\).
- Lớn nhất: \(2^{n-1}-1\).
- Tổng số mẫu bit: \(2^n\); số lượng giá trị âm nhiều hơn số lượng giá trị dương đúng một đơn vị vì có số 0.

`char` cũng là một kiểu nguyên thủy tích phân trong Java nhưng **không có dấu**: nó lưu một *UTF-16 code unit* từ 0 đến 65_535, chủ yếu để xử lý ký tự. Một ký tự Unicode ngoài BMP có thể cần **hai `char`** (surrogate pair), vì vậy `Character.SIZE = 16` không có nghĩa một ký tự Unicode luôn dài 16 bit.

`boolean` không có bộ hằng số `MIN_VALUE/MAX_VALUE/SIZE/BYTES` tương ứng; không suy ra kích thước bộ nhớ của `boolean` từ bảng này.

## 3. Đọc hằng số trực tiếp trong chương trình

Lưu tệp `IntegerMetadataDemo.java`:

```java
public class IntegerMetadataDemo {
    public static void main(String[] args) {
        System.out.printf("byte: [%d, %d], SIZE=%d, BYTES=%d%n",
                Byte.MIN_VALUE, Byte.MAX_VALUE, Byte.SIZE, Byte.BYTES);
        System.out.printf("short: [%d, %d], SIZE=%d, BYTES=%d%n",
                Short.MIN_VALUE, Short.MAX_VALUE, Short.SIZE, Short.BYTES);
        System.out.printf("int: [%d, %d], SIZE=%d, BYTES=%d%n",
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.SIZE, Integer.BYTES);
        System.out.printf("long: [%d, %d], SIZE=%d, BYTES=%d%n",
                Long.MIN_VALUE, Long.MAX_VALUE, Long.SIZE, Long.BYTES);
        System.out.printf("char: [%d, %d], SIZE=%d, BYTES=%d%n",
                (int) Character.MIN_VALUE, (int) Character.MAX_VALUE,
                Character.SIZE, Character.BYTES);
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac IntegerMetadataDemo.java
java IntegerMetadataDemo
```

Kết quả:

```text
byte: [-128, 127], SIZE=8, BYTES=1
short: [-32768, 32767], SIZE=16, BYTES=2
int: [-2147483648, 2147483647], SIZE=32, BYTES=4
long: [-9223372036854775808, 9223372036854775807], SIZE=64, BYTES=8
char: [0, 65535], SIZE=16, BYTES=2
```

Hai phép ép `(int)` ở dòng `char` giúp in **mã số code unit** thay vì ký tự không nhìn thấy. Với các kiểu có dấu, `MIN_VALUE/MAX_VALUE` là giá trị số; `SIZE/BYTES` mô tả độ rộng kiểu nguyên thủy, không phải kích thước đối tượng `Integer`, `Long`, v.v.

## 4. Khi nào dùng từng hằng số?

### 4.1. `MIN_VALUE` và `MAX_VALUE`: kiểm tra miền giá trị

Giả sử dữ liệu đầu vào là `long` nhưng API đích chỉ nhận `int`. Trước khi ép kiểu, kiểm tra:

```java
boolean fitsInt = value >= Integer.MIN_VALUE
        && value <= Integer.MAX_VALUE;
```

Nếu không nằm trong miền đó, phép ép `(int) value` sẽ **bỏ các bit cao** và có thể đổi thành một giá trị khác, không tự báo lỗi. Java còn cung cấp `Math.toIntExact(long)` để chuyển có kiểm tra, ném `ArithmeticException` khi ngoài miền `int`.

Đối với phép cộng, trừ, nhân số nguyên có dấu, dùng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact` khi muốn phát hiện tràn số thay vì nhận kết quả vòng lại (*wraparound*).

Lưu tệp `SafeIntegerDemo.java`:

```java
public class SafeIntegerDemo {
    public static void main(String[] args) {
        long value = 2_147_483_648L; // int MAX_VALUE + 1

        boolean fitsInt = value >= Integer.MIN_VALUE
                && value <= Integer.MAX_VALUE;
        System.out.println("fits int: " + fitsInt);
        System.out.println("cast (int): " + (int) value);

        try {
            Math.toIntExact(value);
        } catch (ArithmeticException e) {
            System.out.println("toIntExact: overflow");
        }

        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException e) {
            System.out.println("addExact: overflow");
        }
    }
}
```

Chạy:

```bash
javac SafeIntegerDemo.java
java SafeIntegerDemo
```

Kết quả chính xác:

```text
fits int: false
cast (int): -2147483648
toIntExact: overflow
addExact: overflow
```

**Lưu ý:** kiểm tra miền bằng `MIN_VALUE/MAX_VALUE` phù hợp khi giá trị đầu vào đang ở kiểu rộng hơn. Nếu đã tính toán tràn bên trong `int` rồi mới so với `Integer.MAX_VALUE` thì đã quá muộn: biến chỉ còn chứa kết quả sau tràn.

### 4.2. `SIZE`: làm việc với bit

`Integer.SIZE` luôn là 32, `Long.SIZE` luôn là 64. Các hằng số hữu ích cho thuật toán bit, mặt nạ, giao thức nhị phân và biểu diễn bit:

```java
int bitCount = Integer.SIZE;  // 32
int highBitMask = 1 << (Integer.SIZE - 1); // bit dấu của int
long twoTo32 = 1L << Integer.SIZE; // 4294967296L
```

Đừng viết `1 << 32` để lấy \(2^{32}\): toán hạng trái `int` khiến Java chỉ dùng 5 bit thấp của khoảng dịch; `1 << 32` tương đương `1 << 0` và bằng **1**. Dùng `1L` khi phép dịch phải tính trên `long`.

### 4.3. `BYTES`: xác định độ rộng dữ liệu nhị phân

`Integer.BYTES` bằng 4 và `Long.BYTES` bằng 8. Ví dụ: một bản ghi nhị phân gồm một `int` và một `long` có tổng độ rộng phần dữ liệu là:

```java
int recordBytes = Integer.BYTES + Long.BYTES; // 12
```

Khi truyền dữ liệu, cần quy định thêm **thứ tự byte** (*endianness*). Chẳng hạn `ByteBuffer` mặc định dùng big-endian, có thể đổi qua `order(ByteOrder.LITTLE_ENDIAN)`. `BYTES` chỉ nói số byte, không nói thứ tự byte và **không bao gồm** header, padding, bộ nhớ đối tượng hoặc overhead của collection.

## 5. Những bẫy thường gặp

### 5.1. Tràn số không tự phát sinh ngoại lệ

```java
int wrapped = Integer.MAX_VALUE + 1; // -2147483648
long wrappedLong = Long.MAX_VALUE + 1; // Long.MIN_VALUE
```

Phép cộng trên số nguyên có dấu của Java lấy kết quả theo độ rộng cố định; muốn báo lỗi dùng phương thức `Exact` hoặc chuyển sang `BigInteger` nếu cần số nguyên không bị giới hạn ở 64 bit.

### 5.2. `MIN_VALUE` không đối xứng với `MAX_VALUE`

Vì số âm nhiều hơn số dương một giá trị:

```java
System.out.println(-Integer.MIN_VALUE);
System.out.println(Math.abs(Integer.MIN_VALUE));
```

Cả hai dòng đều in `-2147483648` do kết quả dương không biểu diễn được trong `int`. Tương tự, `Integer.MIN_VALUE / -1` trả về `Integer.MIN_VALUE` (trường hợp tràn đặc biệt của phép chia nguyên). Dùng `Math.negateExact` nếu cần bắt lỗi khi đổi dấu; với giá trị tuyệt đối của số cực tiểu, dùng kiểu rộng hơn hoặc `BigInteger`.

### 5.3. Hằng số thuộc lớp bao, không biểu thị dung lượng đối tượng

`Integer.BYTES == 4` mô tả **giá trị `int` 32 bit**. Nó không cam kết một đối tượng `new Integer(...)` chiếm đúng 4 byte; object header, căn chỉnh và cơ chế VM làm dung lượng khác đi. Trong Java hiện đại cũng nên dùng `Integer.valueOf(...)` hoặc autoboxing, tránh constructor wrapper đã bị deprecated.

### 5.4. Ép kiểu, thăng cấp số học và hằng `long`

- Trong biểu thức số học, `byte`, `short` và `char` thường được thăng cấp thành `int`: `Byte.MAX_VALUE + 1` có kiểu `int` và bằng 128.
- `(byte) 128` cho kết quả -128 vì giữ lại 8 bit thấp.
- Hằng số `long` nên có hậu tố `L`: `3_000_000_000L`; dấu gạch dưới chỉ tăng khả năng đọc, không thay đổi giá trị.
- Với số nguyên không dấu lưu trong các bit của `int/long`, xem các API như `Integer.compareUnsigned`, `Integer.toUnsignedLong`, `Long.compareUnsigned`. Đừng suy ra miền không dấu từ `Integer.MIN_VALUE/MAX_VALUE` vì hai hằng số này là miền **có dấu**.

### 5.5. Đừng nhầm `Integer.MIN_VALUE` với `Double.MIN_VALUE`

`Integer.MIN_VALUE` là **số âm nhỏ nhất** của `int`. Ngược lại, `Float.MIN_VALUE` và `Double.MIN_VALUE` là **số dương khác 0 nhỏ nhất** có thể biểu diễn (giá trị dưới chuẩn), không phải số âm thấp nhất. Với số thực, phải xét thêm `MAX_VALUE`, dấu âm, vô cực, NaN và độ chính xác; không áp dụng máy móc bảng số nguyên.

## 6. Cách chọn và sử dụng trong thực tế

1. Chọn `int` cho số nguyên thông dụng nằm trong miền 32 bit; dùng `long` cho số lớn hơn hoặc thời gian/đếm có thể vượt giới hạn `int`.
2. Khi nhận dữ liệu lớn rồi chuyển sang kiểu hẹp, xác nhận miền bằng `MIN_VALUE/MAX_VALUE` hoặc `Math.toIntExact`.
3. Khi tính toán mà overflow gây lỗi nghiệp vụ, dùng các phép toán `Math.*Exact` và xử lý ngoại lệ thích hợp.
4. Khi thao tác bit hoặc định dạng nhị phân, lấy `SIZE/BYTES` từ wrapper để bộc lộ rõ độ rộng; chỉ rõ endianness của định dạng dữ liệu.
5. Với số lớn hơn giới hạn `long`, dùng `java.math.BigInteger`; `BigInteger` không có bộ `MIN_VALUE/MAX_VALUE/SIZE/BYTES` cố định tương ứng vì độ dài số có thể tăng.

## 7. Tự kiểm tra

**Câu 1.** `Short.SIZE` và `Short.BYTES` lần lượt bằng bao nhiêu?  
**Đáp án:** 16 bit và 2 byte.

**Câu 2.** Vì sao `Integer.MIN_VALUE` bằng -2_147_483_648 trong khi `Integer.MAX_VALUE` chỉ bằng 2_147_483_647?  
**Đáp án:** một trong 32 bit dành cho dấu; miền signed 32-bit là \([-2^{31},2^{31}-1]\), gồm cả 0.

**Câu 3.** Ép `2_147_483_648L` sang `int` có ném ngoại lệ không?  
**Đáp án:** không; ép trực tiếp cho -2_147_483_648. `Math.toIntExact` sẽ ném `ArithmeticException`.

**Câu 4.** `Character.SIZE = 16` có đảm bảo mọi ký tự Unicode nằm trong một `char` không?  
**Đáp án:** không; một code point ngoài BMP cần hai UTF-16 code unit, tức hai `char`.

**Câu 5.** Có thể dùng `Integer.BYTES` để đo bộ nhớ của `Integer` object không?  
**Đáp án:** không; nó là độ rộng giá trị nguyên thủy `int`, không phải kích thước object trong heap.

**Ghi nhớ:** `MIN_VALUE/MAX_VALUE` trả lời **giá trị nào biểu diễn được?**; `SIZE/BYTES` trả lời **kiểu nguyên thủy rộng bao nhiêu bit/byte?**. Đây là hai nhóm hằng số khác mục đích, nhưng thường dùng cùng nhau để xử lý số nguyên an toàn và dữ liệu nhị phân đúng định dạng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-types-005">char là số nguyên không dấu biểu diễn đơn vị mã UTF-16</a>

<details>
<summary>Xem chi tiết</summary>

# `char` là số nguyên không dấu biểu diễn đơn vị mã UTF-16

## 1. Vì sao `char` thuộc nhóm kiểu số nguyên?

Trong Java, `char` là một **kiểu nguyên thủy (*primitive*) 16 bit không dấu**, có miền giá trị nguyên **0 đến 65.535** (tương ứng `0x0000` đến `0xFFFF`). Đặc tả ngôn ngữ xếp `char` vào nhóm *integral types* cùng `byte`, `short`, `int` và `long`. `Character.SIZE` bằng `16`, `Character.MIN_VALUE` bằng `'\u0000'` (giá trị nguyên 0), còn `Character.MAX_VALUE` bằng `'\uFFFF'` (giá trị nguyên 65.535).

Tuy nhiên, mục đích chính của `char` **không phải lưu một số đếm**. Mỗi giá trị `char` được hiểu là **một đơn vị mã (*code unit*) UTF-16**. Nó có thể biểu diễn ký tự thường gặp như `'A'`, `'7'`, `'中'` hoặc một phần của cặp thay thế (*surrogate pair*). Đặc điểm “số nguyên không dấu” giải thích cách Java lưu, so sánh và thực hiện phép toán với kiểu này; đặc điểm “đơn vị mã UTF-16” giải thích **nó biểu diễn phần nào của văn bản Unicode**.

Ba khái niệm phải phân biệt:

- **Giá trị số của `char`:** một số nguyên từ 0 đến 65.535.
- **Đơn vị mã UTF-16:** một phần tử 16 bit của chuỗi UTF-16; chính là thứ `char` biểu diễn.
- **Ký tự Unicode theo mã điểm (*code point*):** một giá trị Unicode từ `U+0000` đến `U+10FFFF`, trừ khoảng surrogate không phải *Unicode scalar value*. Nhiều mã điểm cần **hai** đơn vị mã UTF-16, nên không thể chứa trọn trong một `char`.

Chẳng hạn, `'A'` vừa là một `char` mang giá trị nguyên 65, vừa là một đơn vị mã UTF-16 biểu diễn mã điểm `U+0041`. Trong khi đó, biểu tượng 😀 (`U+1F600`) cần **hai** giá trị `char`: `0xD83D` và `0xDE00`. Do đó, khẳng định “một `char` luôn là một ký tự Unicode hoàn chỉnh” là **sai**.

## 2. Miền giá trị và hành vi số học

### 2.1. Không dấu khác gì `byte`, `short`?

`byte` và `short` là các số nguyên **có dấu** 8 và 16 bit, có thể nhận giá trị âm. `char` cũng có 16 bit nhưng **không dấu**:

| Kiểu | Số bit | Giá trị nhỏ nhất | Giá trị lớn nhất | Mục đích chính |
|---|---:|---:|---:|---|
| `byte` | 8 | −128 | 127 | Số nguyên nhỏ, dữ liệu byte |
| `short` | 16 | −32.768 | 32.767 | Số nguyên có dấu 16 bit |
| `char` | 16 | 0 | 65.535 | Đơn vị mã UTF-16 |
| `int` | 32 | −2.147.483.648 | 2.147.483.647 | Số nguyên thông thường |

Điều này có nghĩa `char` **không tự biểu diễn số âm**. Không thể gán biến `char` từ giá trị `-1` bằng phép gán thu hẹp ngầm định. Khi ép kiểu `(char) -1`, kết quả có giá trị số `65535` (giữ 16 bit thấp), không phải một `char` âm. Việc ép kiểu như vậy là chuyển đổi số học, không tạo ra một ký tự văn bản hợp lệ theo nhu cầu sử dụng.

### 2.2. `char` tham gia phép toán số như thế nào?

Trong đa số phép toán số học, Java thực hiện *binary numeric promotion*, chuyển `char` thành `int` trước khi tính:

~~~java
char letter = 'A';
int code = letter;           // widening conversion: 65
int nextCode = letter + 1;   // phép cộng có kiểu int: 66
char next = (char) (letter + 1); // ép lại char: 'B'
~~~

Đoạn trên là **minh họa** (không phải chương trình chạy độc lập). Chú ý `char next = letter + 1;` **không biên dịch** nếu `letter` là biến `char`: biểu thức `letter + 1` có kiểu `int` và phải chuyển đổi thu hẹp một cách tường minh. Đừng suy từ việc `char` là kiểu số nguyên rằng mọi kết quả phép cộng vẫn có kiểu `char`.

Một biểu thức hằng có thể là ngoại lệ của quy tắc gán: `char capitalA = 65;` hoặc `char capitalA = '\u0041';` đều hợp lệ vì giá trị hằng nằm trong miền của `char`. Nhưng `int number = 65; char letter = number;` không hợp lệ nếu không ép kiểu, dù giá trị lúc chạy vẫn là 65.

Khi so sánh `char` bằng `<`, `>`, `==`, Java so sánh **giá trị số/đơn vị mã**, không thực hiện quy tắc sắp xếp từ điển của một ngôn ngữ. Ví dụ, `'A' < 'a'` là `true` vì 65 nhỏ hơn 97; điều đó không có nghĩa đây là cách sắp xếp tên người, văn bản tiếng Việt hoặc chuỗi theo `Locale`. Với sắp xếp ngôn ngữ tự nhiên, xem `Collator` thay cho phép so sánh từng `char`.

### 2.3. Ví dụ độc lập: nhìn thấy giá trị số

Tạo tệp `CharIntegralDemo.java`:

~~~java
public class CharIntegralDemo {
    public static void main(String[] args) {
        char letter = 'A';
        char digit = '7';
        char han = '\u4E2D'; // U+4E2D: chữ 中
        char max = Character.MAX_VALUE;

        System.out.println("bits=" + Character.SIZE);
        System.out.println("range=" + (int) Character.MIN_VALUE
                + ".." + (int) max);
        System.out.println("A=" + (int) letter);
        System.out.println("Han=" + (int) han);
        System.out.println("digitValue=" + (digit - '0'));
        System.out.println("A+1=" + (letter + 1));
        System.out.println("next=" + (char) (letter + 1));
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac CharIntegralDemo.java
java CharIntegralDemo
~~~

Đầu ra chính xác:

~~~text
bits=16
range=0..65535
A=65
Han=20013
digitValue=7
A+1=66
next=B
~~~

Giải thích:

1. `Character.SIZE` cho biết kích thước logic 16 bit của `char`; không có nghĩa mỗi ký tự trong file luôn chiếm đúng hai byte. Mã hóa văn bản trên đĩa có thể là UTF-8 hoặc mã hóa khác.
2. `'A'` có giá trị nguyên 65; `'\u4E2D'` có giá trị nguyên 20.013.
3. `'7'` có giá trị mã 55, **không phải** số nguyên 7. Phép `'7' - '0'` cho 7 vì chữ số ASCII liên tiếp; kỹ thuật này chỉ nên dùng khi đầu vào chắc chắn là `'0'` đến `'9'`.
4. `letter + 1` có kiểu `int` nên in `66`; ép về `char` mới in `B`.

## 3. UTF-16: đơn vị mã, mã điểm và cặp surrogate

### 3.1. Unicode code point không đồng nhất với `char`

Unicode gán **mã điểm (*code point*)** cho các thành phần văn bản; cách mã hóa **UTF-16** dùng một hoặc hai đơn vị mã 16 bit cho mỗi *Unicode scalar value*.

- Trong **BMP** (*Basic Multilingual Plane*, `U+0000`–`U+FFFF`), các mã điểm **ngoài vùng surrogate** được biểu diễn bằng **một** đơn vị mã, tương ứng một `char`. Ví dụ: `U+0041` (`A`), `U+00E9` (`é`), `U+4E2D` (`中`).
- Các mã điểm thuộc **supplementary planes**, từ `U+10000` đến `U+10FFFF`, cần **hai** đơn vị mã UTF-16: một *high surrogate* và một *low surrogate*. Ví dụ: 😀 `U+1F600` = `D83D DE00`.
- Vùng `U+D800`–`U+DFFF` được dành riêng cho surrogate. Đây là các giá trị **hợp lệ của kiểu `char`**, nhưng bản thân mỗi giá trị **không phải một Unicode scalar value độc lập**.

| Phạm vi đơn vị mã | Vai trò |
|---|---|
| `0000`–`D7FF` | Một mã điểm BMP ngoài surrogate |
| `D800`–`DBFF` | **High surrogate**: nửa đầu của cặp |
| `DC00`–`DFFF` | **Low surrogate**: nửa sau của cặp |
| `E000`–`FFFF` | Một mã điểm BMP ngoài surrogate |

Chữ “đơn vị mã” mô tả cấu trúc biểu diễn. Một phần tử `char` giữ mã `0xD83D` hoàn toàn hợp lệ về mặt kiểu Java, nhưng không đại diện riêng lẻ cho biểu tượng 😀. Muốn làm việc với mã điểm `U+1F600`, nên dùng `int`.

### 3.2. Cơ chế ghép hai đơn vị mã

Đối với mã điểm `U` từ `0x10000` đến `0x10FFFF`, đặt `U' = U - 0x10000`:

- High surrogate = `0xD800 + (U' >>> 10)`.
- Low surrogate = `0xDC00 + (U' & 0x3FF)`.

Với `U+1F600`, `U' = 0xF600`, high surrogate `0xD83D` và low surrogate `0xDE00`. Thực tế, dùng API `Character.toChars(codePoint)` để tạo cặp và `Character.toCodePoint(high, low)` để khôi phục mã điểm; không nên tự viết công thức trừ khi đang xây dựng thư viện mã hóa.

Java cung cấp `Character.isHighSurrogate(char)`, `Character.isLowSurrogate(char)`, `Character.isSurrogatePair(char, char)`, `Character.isSupplementaryCodePoint(int)` và `Character.charCount(int)` để kiểm tra và thao tác đúng.

### 3.3. Vì sao một ký tự biểu tượng làm `String.length()` tăng 2?

Trong Java, `String.length()` trả về **số đơn vị mã UTF-16**, không phải số mã điểm và càng không bảo đảm bằng số ký tự hiển thị. `String.charAt(index)` lấy **một** đơn vị mã; `String.codePointAt(index)` lấy mã điểm bắt đầu ở chỉ số UTF-16 tương ứng và ghép cặp surrogate hợp lệ khi có thể.

Ví dụ độc lập, tạo tệp `Utf16PairDemo.java`:

~~~java
public class Utf16PairDemo {
    public static void main(String[] args) {
        String smile = new String(Character.toChars(0x1F600));
        String text = "A" + smile + "Z";

        System.out.println("units=" + text.length());
        System.out.println("points="
                + text.codePointCount(0, text.length()));
        System.out.printf("pair=%04X %04X%n",
                (int) text.charAt(1), (int) text.charAt(2));
        System.out.printf("pointAt1=U+%X%n", text.codePointAt(1));
        System.out.println("validPair=" + Character.isSurrogatePair(
                text.charAt(1), text.charAt(2)));
        System.out.println("pointUnits="
                + Character.charCount(text.codePointAt(1)));
        System.out.println("sameSmile="
                + smile.equals(text.substring(1, 3)));
    }
}
~~~

~~~bash
javac Utf16PairDemo.java
java Utf16PairDemo
~~~

Đầu ra chính xác:

~~~text
units=4
points=3
pair=D83D DE00
pointAt1=U+1F600
validPair=true
pointUnits=2
sameSmile=true
~~~

Chuỗi có ba mã điểm `A`, 😀, `Z` nhưng bốn đơn vị mã: `0041 D83D DE00 005A`. Chỉ số `1` và `2` cùng thuộc **một** mã điểm 😀. `text.substring(1, 3)` lấy đủ hai đơn vị mã, còn `substring(1, 2)` chỉ lấy high surrogate và tạo chuỗi không chứa biểu tượng hoàn chỉnh.

Đặc biệt, `charAt(2)` trả **low surrogate**, trong khi `codePointAt(2)`, do bắt đầu ngay ở low surrogate mà không nhìn ngược để ghép với high surrogate trước đó, trả giá trị low surrogate như một `int`. Vì vậy, phải truyền **chỉ số bắt đầu của mã điểm** khi sử dụng `codePointAt`.

## 4. Duyệt chuỗi đúng khi có ký tự ngoài BMP

Vòng lặp phổ biến sau đọc từng **đơn vị mã**:

~~~java
for (int i = 0; i < text.length(); i++) {
    char unit = text.charAt(i);
    // Chỉ thích hợp khi chủ đích là xử lý UTF-16 code unit.
}
~~~

Đây là **đoạn mã minh họa**, giả sử biến `text` đã được khai báo. Nếu mục đích là xử lý từng **mã điểm Unicode**, tăng chỉ số bằng `Character.charCount(codePoint)`:

~~~java
for (int i = 0; i < text.length();) {
    int codePoint = text.codePointAt(i);
    // Xử lý codePoint dưới dạng int.
    i += Character.charCount(codePoint);
}
~~~

Đây cũng là **đoạn minh họa**. Với dữ liệu chuỗi thông thường, có thể dùng `text.codePoints()` để nhận `IntStream`, song cần hiểu quy tắc đối với surrogate lẻ được giải thích ở phần tiếp theo.

### Ví dụ độc lập: lặp theo code point

Tạo tệp `CodePointIterationDemo.java`:

~~~java
public class CodePointIterationDemo {
    public static void main(String[] args) {
        String text = "A"
                + new String(Character.toChars(0x1F642))
                + "\u00E9";

        System.out.println("units=" + text.length());
        System.out.println("points=" + text.codePoints().count());

        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            System.out.printf("index=%d codePoint=U+%X%n", i, cp);
            i += Character.charCount(cp);
        }
    }
}
~~~

~~~bash
javac CodePointIterationDemo.java
java CodePointIterationDemo
~~~

Đầu ra chính xác:

~~~text
units=4
points=3
index=0 codePoint=U+41
index=1 codePoint=U+1F642
index=3 codePoint=U+E9
~~~

`U+1F642` là mã điểm ngoài BMP nên chiếm hai đơn vị mã ở chỉ số UTF-16 `1` và `2`; `é` (dạng đơn mã điểm `U+00E9`) bắt đầu ở chỉ số `3`. Nếu cần di chuyển theo mã điểm thay vì tự tính số đơn vị mã, Java còn cung cấp `String.offsetByCodePoints`.

Không được lấy `text.length()` làm số “ký tự con người nhìn thấy”, chẳng hạn để kiểm tra tên hiển thị tối đa 20 ký tự: `length()` đếm đơn vị mã UTF-16, `codePointCount` đếm mã điểm, còn **grapheme cluster** mới gần với một ký tự cảm nhận của người dùng.

## 5. Một code point chưa chắc là một ký tự hiển thị

Ngay cả khi đã chuyển từ `char` sang mã điểm, vấn đề đếm ký tự văn bản vẫn chưa kết thúc:

- Chữ `é` có thể là **một mã điểm** `U+00E9`, hoặc hai mã điểm `U+0065` (`e`) và `U+0301` (dấu sắc kết hợp).
- Một số biểu tượng emoji được tạo từ nhiều mã điểm kết hợp với dấu nối `U+200D` (*zero-width joiner*), biến thể hoặc thành phần khác, nhưng hiển thị như một biểu tượng.
- Văn bản tiếng Việt và các ngôn ngữ khác có thể có dấu tổ hợp, quy tắc biên ký tự và cách sắp xếp phụ thuộc ngôn ngữ.

Do đó:

| Câu hỏi | Khái niệm/API thích hợp |
|---|---|
| Có bao nhiêu đơn vị UTF-16? | `String.length()` |
| Lấy đơn vị mã tại chỉ số UTF-16? | `String.charAt(index)` |
| Mã điểm bắt đầu ở vị trí UTF-16 này là gì? | `String.codePointAt(index)` |
| Có bao nhiêu mã điểm theo cách đếm UTF-16? | `String.codePointCount(0, length)` |
| Duyệt mã điểm | `String.codePoints()` hoặc `codePointAt` + `Character.charCount` |
| Đếm phần tử văn bản hiển thị, tách theo cụm tự vị? | Thuật toán phân đoạn *extended grapheme cluster* phù hợp chuẩn Unicode và phiên bản dữ liệu áp dụng |
| So sánh/sắp xếp chuỗi theo ngôn ngữ? | Quy tắc `Collator`/`Locale`, không dựa trên thứ tự mã số `char` |

Chuẩn hóa Unicode, ví dụ NFC/NFD qua `java.text.Normalizer`, có thể cần thiết để xử lý chuỗi tương đương ở mức biểu diễn. Tuy nhiên, **chuẩn hóa** và **phân đoạn grapheme** là hai thao tác khác nhau: chuẩn hóa không làm cho phép đếm `char` đột nhiên trở thành phép đếm ký tự hiển thị.

## 6. Unicode escape, char literal và dữ liệu từ bên ngoài

### 6.1. Viết literal `char`

Một literal `char` dùng **dấu nháy đơn** và đúng một đơn vị mã UTF-16, ví dụ `'A'`, `'7'`, `'\n'`, `'\u4E2D'`. Literal chuỗi dùng dấu nháy kép, ví dụ `"A"`, `"ABC"`. Biểu tượng ngoài BMP như 😀 không thể nằm trong một literal `char` độc lập vì cần hai đơn vị mã; hãy dùng `String` và `Character.toChars(0x1F600)` khi cần xây chuỗi bằng mã điểm.

Các escape quen thuộc như `'\n'`, `'\t'`, `'\\'` và `'\''` đều là literal `char` hợp lệ. Lưu ý Java xử lý **Unicode escape `\uXXXX` ở giai đoạn rất sớm**, trước khi phân tích token. Vì thế cách viết trực tiếp escape Unicode của ký tự xuống dòng `U+000A` trong source Java có thể làm dòng nguồn bị tách bất ngờ; khi cần ký tự xuống dòng, dùng `'\n'` thay cho escape Unicode của nó.

Một `char` chỉ là giá trị đơn vị mã, không nói toàn bộ văn bản sẽ được lưu trên file bằng UTF-16. Khi ghi file, gửi mạng hoặc chuyển chuỗi thành `byte[]`, hãy chọn charset rõ ràng, chẳng hạn `StandardCharsets.UTF_8`. Không lấy một `char` rồi cho rằng cast sang `byte` luôn giữ được ký tự; cast có thể mất các bit cao và hoàn toàn không phải phép mã hóa UTF-8.

### 6.2. Có thể tồn tại surrogate đứng lẻ

Java `String` có thể chứa một high surrogate hoặc low surrogate **không ghép cặp**. Đây là chuỗi có đơn vị mã UTF-16 nhưng **không phải một chuỗi Unicode scalar value hợp lệ đầy đủ**. Trong trường hợp đó:

- `String.length()` vẫn đếm đơn vị mã, kể cả surrogate lẻ.
- `String.codePointAt` và `String.codePoints()` ghép **cặp hợp lệ**; với surrogate không ghép cặp, chúng có thể trả chính giá trị surrogate dưới dạng `int`.
- `Character.isValidCodePoint(int)` chỉ kiểm tra số có nằm trong miền code point `U+0000`–`U+10FFFF`; không có nghĩa đã loại trừ giá trị surrogate. Kiểm tra *Unicode scalar value* cần loại trừ khoảng `U+D800`–`U+DFFF`.
- Khi trao đổi dữ liệu giữa hệ thống hoặc xuất ra charset khác, cần một chính sách xử lý đầu vào không hợp lệ thay vì ngầm định mọi `String` đều chỉ chứa mã điểm hợp lệ.

Ví dụ độc lập, tạo tệp `UnpairedSurrogateDemo.java`:

~~~java
public class UnpairedSurrogateDemo {
    public static void main(String[] args) {
        char high = (char) 0xD83D;
        String broken = String.valueOf(high);
        int cp = broken.codePointAt(0);

        System.out.println("units=" + broken.length());
        System.out.printf("returned=U+%X%n", cp);
        System.out.println("high=" + Character.isHighSurrogate(high));
        System.out.println("validCodePoint="
                + Character.isValidCodePoint(cp));
        System.out.println("scalar=" + isScalarValue(cp));
    }

    private static boolean isScalarValue(int cp) {
        return cp >= 0 && cp <= 0x10FFFF
                && !(cp >= 0xD800 && cp <= 0xDFFF);
    }
}
~~~

~~~bash
javac UnpairedSurrogateDemo.java
java UnpairedSurrogateDemo
~~~

Đầu ra chính xác:

~~~text
units=1
returned=U+D83D
high=true
validCodePoint=true
scalar=false
~~~

Ví dụ cho thấy sự khác nhau giữa **miền mã điểm mà API chấp nhận** và **một mã điểm scalar hợp lệ để biểu diễn Unicode**. Hãy dùng kiểu `int` cho mã điểm và kiểm tra cặp surrogate khi nguồn dữ liệu có thể bị hỏng.

## 7. Nên dùng `char` khi nào, và khi nào không?

**Dùng `char`** khi chương trình thật sự cần xử lý **một đơn vị mã UTF-16**, tương tác với `charAt`, bộ đệm `char[]`, API ký tự tương ứng hoặc các ký hiệu ASCII đơn giản trong một giao thức đã giới hạn ký tự. Ví dụ, kiểm tra dấu phân cách `','` hay `':'` là một tình huống hợp lý.

**Dùng `int` cho mã điểm** khi bài toán cần duyệt, phân loại hoặc biến đổi văn bản Unicode vượt BMP; kết hợp `codePointAt`/`codePoints` với các phương thức `Character` nhận tham số `int`, chẳng hạn `Character.isLetter(int codePoint)`. Một số phép xử lý Unicode có API overload nhận `char` nhưng sẽ bỏ lỡ ký tự ngoài BMP: khi cần phủ toàn Unicode, chọn overload `int`.

**Dùng `String` cho văn bản** như tên người, chuỗi nội dung, email, mã định danh hoặc một biểu tượng bất kỳ. `String` có thể chứa nhiều đơn vị mã và vẫn cần thao tác đúng theo yêu cầu: duyệt code point, chuẩn hóa, phân đoạn grapheme, so sánh theo locale.

**Dùng `int`, `long`, `BigInteger` cho đại lượng số nguyên** thông thường thay vì tận dụng `char` chỉ vì nó không dấu. `char` có tính chất số học, nhưng tên kiểu và API của nó diễn đạt ý nghĩa **đơn vị mã văn bản**, không phải số lượng phi âm tổng quát.

### Bốn lỗi phổ biến

1. **“`char` chứa được mọi ký tự Unicode vì có 16 bit.”** Không đúng: Unicode có mã điểm ngoài BMP cần hai đơn vị UTF-16.
2. **“`String.length()` là số ký tự người dùng nhìn thấy.”** Không đúng: đó là số đơn vị mã UTF-16; số code point hoặc grapheme cluster có thể khác.
3. **“`charAt(i)` luôn trả một ký tự đầy đủ.”** Không đúng: nó có thể trả high/low surrogate. Khi duyệt mã điểm, hãy tăng `i` theo `Character.charCount(cp)`.
4. **“`char` là số không dấu nên dùng thay cho `unsigned short` ở mọi bài toán.”** Không phù hợp: kiểu `char` gắn với code unit; nếu xử lý trường số không dấu 16 bit của giao thức, thường giải mã sang `int` để tính toán và thể hiện rõ miền 0..65535.

## 8. Tự kiểm tra kiến thức

1. `char` có bao nhiêu bit, và miền số nguyên của nó là gì?
2. Vì sao `char letter = 'A'; int n = letter + 1;` hợp lệ nhưng `char next = letter + 1;` lại không?
3. Vì sao `"A😀Z".length()` bằng `4`, nhưng số Unicode code point trong chuỗi là `3`?
4. `String.charAt` khác `String.codePointAt` ở đâu? Vì sao mã điểm nên được giữ trong `int`?
5. Một chuỗi có high surrogate đứng lẻ có thể tồn tại trong Java không? `Character.isValidCodePoint` có bảo đảm không còn surrogate lẻ không?
6. Khi người dùng giới hạn độ dài tên hiển thị theo ký tự cảm nhận, nên đếm UTF-16 code unit, code point hay grapheme cluster?

**Đáp án gợi ý:** (1) 16 bit, 0–65.535; (2) phép cộng được nâng sang `int`, thu hẹp cần explicit cast; (3) emoji nằm ngoài BMP chiếm 2 code unit; (4) `charAt` đọc một code unit, `codePointAt` ghép surrogate hợp lệ để đọc code point, `int` chứa được trên `U+FFFF`; (5) có thể có surrogate lẻ, `isValidCodePoint` kiểm tra miền số nên chưa đủ để loại surrogate; (6) chọn phép phân đoạn grapheme cluster phù hợp Unicode và yêu cầu sản phẩm.

**Ghi nhớ:** `char` **là một số nguyên không dấu 16 bit về cơ chế của Java**, đồng thời **là một đơn vị mã UTF-16 về ý nghĩa văn bản**. Với Unicode hiện đại, một mã điểm có thể cần hai `char` và một ký tự hiển thị có thể cần nhiều mã điểm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-types-006">Phân biệt char số với giá trị chữ số được viết dưới dạng ký tự</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ký tự chữ số (`char`) với giá trị số trong Java

Trong Java, **ký tự trông giống một chữ số** và **giá trị số của chữ số đó** là hai thứ khác nhau. Đây là điểm quan trọng khi xử lý dữ liệu nhập, chuyển đổi chuỗi thành số, tính toán và kiểm tra ký tự.

## 1. `'7'`, `7` và `"7"` khác nhau thế nào?

| Cách viết | Kiểu Java | Ý nghĩa |
| --- | --- | --- |
| `'7'` | `char` | Một ký tự chữ số, có giá trị đơn vị mã UTF-16 là **55** (U+0037) |
| `7` | `int` | Số nguyên có giá trị **7**, dùng trực tiếp để tính toán |
| `"7"` | `String` | Chuỗi chứa một ký tự `7`, phải chuyển đổi khi muốn dùng như số |
| `'0'` | `char` | Ký tự `0`, đơn vị mã UTF-16 bằng **48** (U+0030) |

`char` là kiểu nguyên thủy biểu diễn **một đơn vị mã UTF-16 không dấu 16 bit** (0–65.535). Vì vậy Java cho phép đưa `char` vào biểu thức số học, nhưng số thu được từ chuyển kiểu trực tiếp là **mã của ký tự**, không đương nhiên là chữ số mà con người nhìn thấy. Với các ký tự ASCII, `'0'` đến `'9'` lần lượt có mã thập phân 48 đến 57.

Ví dụ, hai khai báo sau đều hợp lệ nhưng khác nghĩa:

```java
char character = '7'; // Ký tự '7': mã UTF-16 là 55.
int number = 7;       // Giá trị số nguyên thực sự là 7.

int codeUnit = character; // Widening conversion: codeUnit == 55.
int digit = character - '0'; // Hiệu hai mã ký tự: 55 - 48 == 7.
```

Đoạn trên là mã minh họa để đặt trong một phương thức Java. **`(int) character` hoặc `int codeUnit = character` không có nghĩa là phân tích ký tự thành số 7.** Phép trừ `'0'` mới thực hiện ánh xạ từ ký tự ASCII sang giá trị chữ số.

## 2. Vì sao phép trừ `'0'` hoạt động?

Các chữ số ASCII được xếp **liên tiếp** trong bảng mã:

| Ký tự | Mã thập phân | Biểu thức | Giá trị số |
| --- | ---: | --- | ---: |
| `'0'` | 48 | `'0' - '0'` | 0 |
| `'1'` | 49 | `'1' - '0'` | 1 |
| `'7'` | 55 | `'7' - '0'` | 7 |
| `'9'` | 57 | `'9' - '0'` | 9 |

Trong Java, khi dùng toán tử số học như `+` hoặc `-`, toán hạng kiểu `char` được **nâng kiểu số học lên `int`**. Do đó, `'7' - '0'` có kiểu kết quả `int` và giá trị 7.

**Chỉ dùng phép trừ này khi đã biết ký tự nằm trong khoảng ASCII `'0'..'9'`.** Với chữ cái, dấu câu hoặc chữ số thuộc hệ chữ khác, hiệu mã ký tự không phải cách chuyển đổi chữ số tổng quát.

```java
static int asciiDigit(char c) {
    if (c < '0' || c > '9') {
        throw new IllegalArgumentException("Not an ASCII digit: " + c);
    }
    return c - '0';
}
```

Hàm này là một phương thức cần đặt bên trong lớp Java. Nếu truyền `'7'`, kết quả là `7`. Nếu truyền `'A'` hoặc chữ số Ả Rập `'٧'`, hàm chủ động báo lỗi vì ký tự không thuộc tập chữ số ASCII đã cho phép.

## 3. `char` trong tính toán và khi in ra

Hãy phân biệt **in ký tự**, **in giá trị mã** và **thực hiện phép tính**:

```java
char c = '7';

System.out.println(c);              // 7 (in dạng ký tự)
System.out.println((int) c);        // 55 (giá trị mã UTF-16)
System.out.println(c + 1);          // 56 (phép cộng số nguyên)
System.out.println((char) (c + 1)); // 8 (ép kết quả 56 về ký tự)
System.out.println(c - '0');        // 7 (giá trị chữ số)
System.out.println("7" + 1);        // 71 (nối chuỗi)
```

Đây là các câu lệnh minh họa đặt trong `main`. Với `println(c)`, Java chọn overload nhận `char` và hiển thị hình dạng ký tự. Với `println(c + 1)`, biểu thức đã được tính thành `int`, nên in ra `56`. Khi có `String` ở toán tử `+`, Java có thể thực hiện **nối chuỗi**: `"7" + 1` tạo chuỗi `"71"`, không phải số 8.

Hai tình huống dễ nhầm:

- `'7' + '2'` là phép cộng hai mã: `55 + 50 = 105`, **không** phải số 9 hay số 72.
- `(char) 7` tạo ký tự điều khiển U+0007, **không** tạo ký tự `'7'`. Ngược lại, `(char) ('0' + 7)` tạo `'7'` vì nằm trong dải chữ số ASCII.

## 4. Chương trình Java 17+ chạy độc lập

Lưu nội dung sau vào **`DigitVsCharDemo.java`**:

```java
public class DigitVsCharDemo {
    static int asciiDigit(char c) {
        if (c < '0' || c > '9') {
            throw new IllegalArgumentException("Not an ASCII digit: " + c);
        }
        return c - '0';
    }

    public static void main(String[] args) {
        char symbol = '7';
        int rawCodeUnit = symbol;
        int digit = asciiDigit(symbol);

        System.out.println("char=" + symbol);
        System.out.println("UTF-16 code unit=" + rawCodeUnit);
        System.out.println("decimal digit=" + digit);
        System.out.println("add 1=" + (symbol + 1));
        System.out.println("next char=" + (char) (symbol + 1));
        System.out.println("text concat=" + ("7" + 1));
        System.out.println("Unicode decimal digit=" + Character.digit('\u0667', 10));
        System.out.println("hex A=" + Character.digit('A', 16));
        System.out.println("digit 7 -> char=" + Character.forDigit(7, 10));
        System.out.println("two digits=" + (asciiDigit('4') * 10 + asciiDigit('2')));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 DigitVsCharDemo.java
java DigitVsCharDemo
```

Kết quả chuẩn trên stdout:

```text
char=7
UTF-16 code unit=55
decimal digit=7
add 1=56
next char=8
text concat=71
Unicode decimal digit=7
hex A=10
digit 7 -> char=7
two digits=42
```

Dòng cuối cho thấy cách ghép **giá trị số** của hai ký tự `'4'` và `'2'` thành số `42`: chữ số hàng chục nhân 10 rồi cộng chữ số hàng đơn vị. Đây là số học trên `int`, không phải ghép ký tự.

## 5. Khi dữ liệu có chữ số Unicode

Không phải mọi ký tự được hiển thị là chữ số `7` đều có cùng mã với `'7'`. Chẳng hạn:

- `'7'`: chữ số ASCII, mã U+0037.
- `'٧'`: chữ số Arabic-Indic seven, mã U+0667.
- `'７'`: chữ số fullwidth seven, mã U+FF17.

Cả ba có thể biểu thị **chữ số thập phân 7**, nhưng `c - '0'` chỉ đúng cho trường hợp ASCII. Nếu xử lý chữ số Unicode, dùng API `Character` có quy tắc rõ ràng:

```java
char ascii = '7';
char arabicIndic = '٧';
char letter = 'A';

System.out.println(Character.isDigit(ascii));            // true
System.out.println(Character.isDigit(arabicIndic));      // true
System.out.println(Character.digit(arabicIndic, 10));    // 7
System.out.println(Character.digit(letter, 10));         // -1
System.out.println(Character.digit(letter, 16));         // 10
```

Đoạn này đặt trong `main`. `Character.digit(c, radix)` trả giá trị chữ số theo **cơ số** chỉ định hoặc `-1` nếu không hợp lệ. Ví dụ chữ `A` có giá trị 10 trong hệ cơ số 16 nhưng không phải chữ số hợp lệ ở cơ số 10. Các radix được hỗ trợ từ `Character.MIN_RADIX` (2) đến `Character.MAX_RADIX` (36).

Dùng kết hợp kiểm tra và chuyển đổi khi nhận đúng **một ký tự chữ số thập phân**:

```java
static int decimalDigit(char c) {
    int value = Character.digit(c, 10);
    if (!Character.isDigit(c) || value < 0) {
        throw new IllegalArgumentException("Not a decimal digit");
    }
    return value;
}
```

Đây là phương thức đặt bên trong lớp Java. `Character.isDigit(c)` kiểm tra tính chất chữ số thập phân theo Unicode; `Character.digit(c, 10)` lấy giá trị cần dùng để tính toán. Phép `c - '0'` chỉ kiểm tra quan hệ giữa mã, nên không thay thế được cặp API này khi đầu vào cho phép nhiều hệ chữ.

**Giới hạn của `char`:** Một số ký tự Unicode nằm ngoài Basic Multilingual Plane (BMP), cần **hai đơn vị mã UTF-16** (cặp surrogate). `charAt(index)` chỉ lấy một đơn vị mã; lúc này dùng `String.codePointAt(index)` và overload `Character.digit(int codePoint, int radix)` / `Character.isDigit(int codePoint)`.

```java
String s = "𝟠"; // MATHEMATICAL DOUBLE-STRUCK DIGIT EIGHT (U+1D7E0).
System.out.println(s.length());                       // 2: hai đơn vị UTF-16.
System.out.println(Character.digit(s.charAt(0), 10)); // -1: chỉ một surrogate.
System.out.println(Character.digit(s.codePointAt(0), 10)); // 8.
```

Ví dụ này đặt trong `main`; việc duyệt cả chuỗi theo code point nên tăng chỉ số bằng `Character.charCount(codePoint)` hoặc sử dụng `String.codePoints()`, thay vì mặc định mỗi `char` là một ký tự Unicode hoàn chỉnh.

## 6. Chuyển đổi theo chiều ngược: số thành ký tự chữ số

Khi đã có **giá trị số** từ 0 đến 9 và cần ký tự ASCII:

```java
int n = 7;
char c1 = (char) ('0' + n);             // '7', với điều kiện 0 <= n <= 9.
char c2 = Character.forDigit(n, 10);    // '7'.
String text = Integer.toString(n);     // "7" (String).
```

`Character.forDigit(digit, radix)` còn dùng được cho chữ số của các hệ cơ số khác (ví dụ `Character.forDigit(10, 16)` trả về `'a'`). API trả ký tự NUL `'\0'` khi đối số chữ số/cơ số không hợp lệ, vì vậy cần kiểm tra điều kiện đầu vào khi tránh kết quả không mong muốn. Không dùng `(char) ('0' + n)` cho `n = 12`: kết quả là `'<'`, **không** phải chuỗi `"12"`. Với số có nhiều chữ số, dùng `Integer.toString(n)` hoặc `String.valueOf(n)`.

## 7. Một ký tự chữ số khác với một chuỗi nhiều chữ số

- Với **một ký tự ASCII** `'7'`, sau kiểm tra phạm vi, dùng `c - '0'`.
- Với **một code point chữ số Unicode**, dùng `Character.digit(codePoint, 10)` và xử lý trường hợp `-1`.
- Với **một chuỗi biểu diễn số nguyên**, ví dụ `"2048"`, dùng `Integer.parseInt(text)` hoặc kiểu số phù hợp. Chuỗi có thể chứa dấu và nhiều chữ số; `char` riêng lẻ không đại diện cho toàn bộ số nguyên đó.

Ví dụ:

```java
String input = "2048";
int parsed = Integer.parseInt(input);
System.out.println(parsed + 2); // 2050
```

Đặt trong `main`. `Integer.parseInt` ném `NumberFormatException` khi chuỗi không có cú pháp số nguyên hợp lệ hoặc giá trị vượt phạm vi `int`. Đầu vào `null` cũng không chuyển đổi được. Nếu cần dữ liệu lớn hơn giới hạn `int`, cân nhắc `Long.parseLong` hoặc `BigInteger`.

Lưu ý riêng về `Character.getNumericValue`: API này có phạm vi **giá trị số Unicode rộng hơn chữ số thập phân**, chẳng hạn có thể trả 10 với `'A'` hoặc giá trị của ký tự số La Mã. Không dùng riêng `getNumericValue` để quyết định một ký tự có phải chữ số thập phân hay không; hãy chọn `Character.isDigit` / `Character.digit(..., 10)` theo yêu cầu đầu vào.

## 8. Các lỗi thường gặp và cách tránh

| Viết nhầm | Vấn đề | Cách làm đúng |
| --- | --- | --- |
| `int n = '7';` rồi kỳ vọng `n == 7` | `n` thật ra bằng 55 | Kiểm tra rồi dùng `'7' - '0'` hoặc `Character.digit('7', 10)` |
| `char c = (char) 7;` để lấy `'7'` | 7 là mã của ký tự điều khiển, không phải mã của chữ số | `(char) ('0' + 7)` sau kiểm tra phạm vi |
| `'7' + '2'` để cộng 7 và 2 | Java cộng **mã ký tự** thành 105 | `('7' - '0') + ('2' - '0')` thành 9 |
| `c - '0'` trên mọi ký tự Unicode | Chỉ đúng cho dải ASCII `'0'..'9'` | Dùng `Character.digit` khi cho phép Unicode |
| `Character.getNumericValue(c) >= 0` nghĩa là chữ số thập phân | Một số chữ cái/ký hiệu số cũng có numeric value | Dùng `Character.isDigit(c)` và cơ số 10 |
| `s.charAt(0)` để đọc mọi Unicode digit | Ký tự ngoài BMP có thể gồm hai `char` | Dùng code point và overload nhận `int` |
| Dùng `"7" + 1` để tính `8` | Nối chuỗi cho ra `"71"` | Chuyển sang số trước khi cộng |

**Tự kiểm tra:**

1. `(int) '5'` bằng bao nhiêu? **53**, vì là mã của `'5'`.
2. `'5' - '0'` bằng bao nhiêu? **5**, vì đã chuẩn hóa theo mã chữ số ASCII `'0'`.
3. `'9' + 1` có in thành ký tự `':'` không? **Không**, biểu thức có kiểu `int` và giá trị **58**; phải ép sang `char` mới thành `':'`.
4. Muốn đọc chữ số `'٧'` từ đầu vào Unicode? **`Character.digit('٧', 10)`** trả `7`.
5. Muốn đổi số nguyên `27` thành văn bản? **`Integer.toString(27)`** trả chuỗi `"27"`, không thể chứa cả hai chữ số trong một `char`.

Điểm cần nhớ khi làm bài về **kiểu số nguyên**: `char` tham gia được vào phép toán số học vì lưu đơn vị mã 16 bit, nhưng **ý nghĩa chữ số** là một bước diễn giải riêng. Với ASCII, hiệu so với `'0'` là cách đơn giản; với Unicode, nên dùng `Character`; với chuỗi nhiều chữ số, nên dùng API phân tích số nguyên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-types-007">Giá trị mặc định của field và yêu cầu gán biến cục bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị mặc định của field và yêu cầu gán biến cục bộ trong Java

Trong Java, **field của đối tượng và field `static` luôn có giá trị ban đầu**, dù người viết chưa gán tường minh. Ngược lại, **biến cục bộ phải được gán chắc chắn trước khi đọc** (*definite assignment*). Hai quy tắc này cùng tồn tại nhưng áp dụng ở hai phạm vi khác nhau. Hiểu đúng chúng giúp phân biệt lỗi biên dịch với giá trị xuất hiện lúc chương trình chạy, đặc biệt khi làm việc với kiểu số nguyên.

## 1. Phân biệt field, biến cục bộ, tham số và phần tử mảng

| Vị trí khai báo | Ví dụ | Có giá trị mặc định khi không gán tường minh? | Quy tắc khi đọc |
| --- | --- | --- | --- |
| Field của đối tượng (*instance field*) | `class A { int count; }` | Có: `0` | Đọc được sau khi tạo đối tượng; giá trị có thể bị thay bởi initializer/constructor |
| Field của lớp (*static field*) | `static long total;` | Có: `0L` | Đọc được; dùng chung theo lớp, không riêng cho từng đối tượng |
| Biến cục bộ trong method, constructor hoặc block | `void run() { int n; }` | **Không** | Trình biên dịch phải chứng minh biến được gán trước mọi lần đọc |
| Tham số method/constructor | `void run(int n)` | Không áp dụng mặc định tại nơi khai báo | Giá trị đến từ lời gọi; có thể đọc ngay trong thân method |
| Phần tử của mảng mới tạo | `new int[3]` | Có: từng phần tử là `0` | Có thể đọc phần tử ngay sau khi tạo mảng |
| Biến cục bộ tham chiếu tới mảng | `int[] a;` | **Không** | Phải gán cho `a` trước khi dùng, dù phần tử của mảng được mặc định nếu mảng đã được tạo |

Một biến chỉ được coi là *local* vì nó được khai báo trong thân method, constructor hoặc block; không phải vì nó mang tên `int` hay được khai báo bằng `final`. Kiểu dữ liệu không quyết định quy tắc gán trước khi đọc: vị trí khai báo mới là yếu tố then chốt.

## 2. Giá trị mặc định của mọi loại field

Khi một đối tượng được tạo, các instance field của nó nhận giá trị mặc định trước khi chạy các phần khởi tạo tường minh. Khi một lớp được chuẩn bị khởi tạo, các static field có giá trị mặc định trước khi chạy static initializer. Phần tử của mảng mới tạo cũng được khởi tạo mặc định.

| Kiểu | Giá trị mặc định | Lưu ý |
| --- | --- | --- |
| `byte` | `0` | Số nguyên 8 bit có dấu |
| `short` | `0` | Số nguyên 16 bit có dấu |
| `int` | `0` | Số nguyên 32 bit có dấu |
| `long` | `0L` | Số nguyên 64 bit có dấu; cách viết `0L` cho thấy rõ kiểu `long` |
| `float` | `0.0f` | Số thực 32 bit |
| `double` | `0.0d` | Số thực 64 bit |
| `char` | `'\u0000'` | Ký tự NUL (mã số 0), khác `'0'` và dấu cách |
| `boolean` | `false` | Không phải `0` |
| Mọi kiểu tham chiếu, gồm `String`, `Integer`, object, array | `null` | Chưa tham chiếu tới đối tượng nào; **không phải** chuỗi rỗng hay mảng rỗng |

Các giá trị trong bảng áp dụng cho field **chưa được khởi tạo tường minh**, không phải cam kết field sẽ giữ giá trị đó mãi. Ví dụ `int quantity = 15;` có giá trị sử dụng là `15` sau khi phần khởi tạo field hoàn tất.

### Ví dụ đầy đủ: field, biến cục bộ đã gán và phần tử mảng

Tạo tệp `DefaultValuesDemo.java`:

```java
public class DefaultValuesDemo {
    private static long total;  // static field: 0
    private int count;          // instance field: 0
    private boolean active;     // instance field: false
    private char marker;        // instance field: '\u0000'
    private String label;       // reference field: null

    public static void main(String[] args) {
        DefaultValuesDemo demo = new DefaultValuesDemo();
        int localCount = 7;       // local: được gán trước khi đọc
        int[] scores = new int[2]; // phần tử mảng tự nhận 0

        System.out.println("total=" + total);
        System.out.println("count=" + demo.count);
        System.out.println("active=" + demo.active);
        System.out.println("markerCode=" + (int) demo.marker);
        System.out.println("label=" + demo.label);
        System.out.println("localCount=" + localCount);
        System.out.println("scores=" + scores[0] + "," + scores[1]);
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac DefaultValuesDemo.java
java DefaultValuesDemo
```

Kết quả chuẩn:

```text
total=0
count=0
active=false
markerCode=0
label=null
localCount=7
scores=0,0
```

Phép ép `(int) demo.marker` hiển thị mã số `0`, vì ký tự NUL thường không nhìn thấy nếu in trực tiếp. Phép nối chuỗi với tham chiếu `null` tạo ra chữ `null`; **gọi method trên `null` thì khác** và có thể gây `NullPointerException`.

## 3. Biến cục bộ: khai báo không đồng nghĩa đã gán

Java cho phép **khai báo** biến cục bộ trước rồi **gán** sau, nhưng không cho đọc trước khi biến được gán chắc chắn trên mọi đường đi dẫn tới lệnh đọc.

```java
class InvalidLocal {
    static void show() {
        int value;
        System.out.println(value); // LỖI BIÊN DỊCH
    }
}
```

Đây là đoạn **minh họa lỗi**, không phải chương trình để chạy. `javac` thường báo `variable value might not have been initialized`. Không có bước nào ở đây in `0`: trình biên dịch từ chối mã trước khi chạy.

Cách đúng là gán trước lần đọc:

```java
class ValidLocal {
    static void show() {
        int value;
        value = 42;
        System.out.println(value);
    }
}
```

`int value = 42;` là cách gộp khai báo và gán vào một dòng, nhưng **không bắt buộc phải viết cùng dòng**. Đọc cũng xảy ra ngầm trong biểu thức:

```java
class InvalidIncrement {
    static void show() {
        int count;
        // count++;      // sai: cần đọc count trước khi cộng
        // count += 1;    // sai: cũng cần đọc giá trị cũ
        // count = count + 1; // sai: vế phải đọc count
        count = 0;        // gán lần đầu, hợp lệ
        count++;          // đọc và tăng sau khi đã gán, hợp lệ
    }
}
```

Một biến cục bộ `Integer number;` cũng **không tự nhận `null`**; muốn cho phép nó mang giá trị `null` lúc đầu thì phải viết `Integer number = null;`. Tương tự, `int[] values;` không tạo mảng và cũng không khởi tạo biến tham chiếu đó.

## 4. Definite assignment phụ thuộc đường đi của chương trình

Trình biên dịch phân tích các luồng điều khiển có thể đi đến chỗ đọc biến, không chỉ kiểm tra có dấu `=` ở một dòng đứng trước hay không.

### Có `if` / `else`: mọi nhánh dẫn tới chỗ đọc đều gán

Tệp `DefiniteAssignmentDemo.java`:

```java
public class DefiniteAssignmentDemo {
    static int score(boolean passed) {
        int result;
        if (passed) {
            result = 100;
        } else {
            result = 0;
        }
        return result; // cả hai nhánh đều đã gán
    }

    public static void main(String[] args) {
        System.out.println("passed=" + score(true));
        System.out.println("failed=" + score(false));
    }
}
```

```bash
javac DefiniteAssignmentDemo.java
java DefiniteAssignmentDemo
```

```text
passed=100
failed=0
```

Nếu bỏ `else`, biến có thể chưa được gán khi điều kiện sai:

```java
class MissingBranch {
    static int score(boolean passed) {
        int result;
        if (passed) {
            result = 100;
        }
        return result; // LỖI BIÊN DỊCH: có đường đi chưa gán
    }
}
```

Có thể sửa bằng cách gán ban đầu (`int result = 0;`), gán ở nhánh `else`, hoặc `return` sớm tại nhánh không gán. Chỉ cần bảo đảm **mọi đường đi thực sự tới chỗ đọc** đều đã gán; không nhất thiết mọi nhánh phải gán nếu nhánh đó kết thúc method trước khi đọc.

### Vòng lặp: thân vòng có thể không chạy

```java
class LoopCase {
    static void show(boolean shouldRun) {
        int last;
        while (shouldRun) {
            last = 10;
            break;
        }
        // System.out.println(last); // sai: vòng while có thể không chạy
    }
}
```

Nếu cần dùng `last` sau vòng lặp, hãy cấp một giá trị ban đầu hợp lý hoặc tổ chức lại luồng điều khiển. Việc người lập trình “biết” điều kiện thường đúng không thay thế được chứng minh *definite assignment* của trình biên dịch.

### Tham số và biến cục bộ được gán `null`

```java
class AssignedDoesNotMeanNonNull {
    static void inspect(String text) {
        String local = text; // đã được gán, kể cả khi text là null
        // local.length() sẽ ném NullPointerException nếu local == null
        System.out.println(local == null ? "missing" : local.length());
    }
}
```

*Definite assignment* kiểm tra “đã có phép gán hợp lệ chưa”, **không** chứng minh một tham chiếu khác `null` hay một giá trị đáp ứng ràng buộc nghiệp vụ. Điều đó cần kiểm tra riêng, chẳng hạn `if (local != null)` hoặc `Objects.requireNonNull(...)` khi phù hợp.

## 5. Field có mặc định nhưng có thể được gán sau đó

Với một instance field kiểu `int`:

```java
class Counter {
    int count; // mặc định là 0

    Counter() {
        count = 5; // constructor đổi giá trị thành 5
    }
}
```

Một đối tượng `new Counter()` bình thường sẽ có `count == 5` sau khi constructor hoàn tất. Default `0` là trạng thái khởi tạo ban đầu; initializer và constructor có thể thay nó.

Ở mức khái quát, thứ tự liên quan là:

1. Static field nhận mặc định, rồi static initializer/khởi tạo tường minh của lớp chạy theo thứ tự mã nguồn khi lớp được khởi tạo.
2. Mỗi đối tượng mới nhận mặc định cho instance field; initializer/instance initialization block và phần thân constructor sau đó có thể gán giá trị khác theo thứ tự khởi tạo Java.
3. Phần tử mảng mới tạo nhận mặc định theo kiểu phần tử, rồi chương trình có thể sửa từng phần tử.

**Chú ý về thứ tự initializer:** một phương thức được gọi trong initializer có thể quan sát field khai báo phía sau trước khi initializer tường minh của field ấy chạy.

Tệp `InitializationOrderDemo.java`:

```java
public class InitializationOrderDemo {
    private int observed = readLater();
    private int later = 5;

    private int readLater() {
        return later;
    }

    public static void main(String[] args) {
        InitializationOrderDemo demo = new InitializationOrderDemo();
        System.out.println("observed=" + demo.observed);
        System.out.println("later=" + demo.later);
    }
}
```

```bash
javac InitializationOrderDemo.java
java InitializationOrderDemo
```

```text
observed=0
later=5
```

Ở thời điểm `readLater()` được gọi, `later` mới giữ mặc định `0`; lệnh `private int later = 5;` được thực thi sau đó. Ví dụ này **không** nói rằng mọi field sẽ vẫn bằng `0` sau khi constructor hoàn tất.

## 6. Trường hợp `final`: giá trị mặc định không bỏ qua nghĩa vụ gán

Một `final` field được gán ngay khi khai báo thì không cần gán thêm:

```java
class Config {
    final int retries = 3;
}
```

Nếu `final` field không có initializer (*blank final field*), chương trình phải bảo đảm gán theo đúng quy tắc Java: instance blank final được gán đúng một lần trên mỗi đường khởi tạo constructor hoàn tất bình thường; static blank final được gán khi lớp khởi tạo, ví dụ qua static initializer. Không được coi mặc định `0` là phép gán thay thế nghĩa vụ này.

Tệp `FinalFieldDemo.java`:

```java
public class FinalFieldDemo {
    private final int id;
    private static final int LIMIT;

    static {
        LIMIT = 10;
    }

    FinalFieldDemo(int id) {
        this.id = id;
    }

    public static void main(String[] args) {
        FinalFieldDemo demo = new FinalFieldDemo(7);
        System.out.println("id=" + demo.id);
        System.out.println("limit=" + LIMIT);
    }
}
```

```bash
javac FinalFieldDemo.java
java FinalFieldDemo
```

```text
id=7
limit=10
```

Một biến cục bộ `final` có thể khai báo rồi gán một lần: `final int n; n = 4;` là hợp lệ, nhưng `n = 5;` tiếp theo là lỗi biên dịch. Trong `if`/`else`, mỗi nhánh có thể gán cho cùng một biến `final` nếu mỗi đường chạy tới điểm dùng chỉ gán đúng một lần. `final` không có nghĩa “tự có mặc định”.

## 7. Mối liên hệ với `var` và kiểu tham chiếu bao số

Từ Java 10, `var` suy luận kiểu của **biến cục bộ có initializer**:

```java
class InferredLocal {
    static void show() {
        var count = 10;              // suy luận int
        var numbers = new int[2];    // suy luận int[]
        System.out.println(count + numbers[0]); // 10
    }
}
```

Không thể viết `var count;` (không có initializer để suy luận kiểu), `var unknown = null;` (không suy luận được kiểu từ `null`) hay `var` để khai báo field thông thường. Đây là yêu cầu riêng của `var`, bổ sung cho quy tắc gán chắc chắn của mọi biến cục bộ.

Phân biệt primitive field với wrapper field:

```java
class BoxedValues {
    int primitive;        // 0
    Integer boxed;        // null

    int unsafe() {
        return boxed;     // biên dịch được nhưng unboxing null gây NPE khi chạy
    }
}
```

`Integer` là kiểu tham chiếu nên mặc định là `null`, dù nó biểu diễn số nguyên. Không nên coi default của `Integer` là `0`; kiểm tra `null` hoặc gán giá trị trước khi unboxing nếu cần.

## 8. Những nhầm lẫn thường gặp

| Nhận định | Đúng hay sai? | Giải thích |
| --- | --- | --- |
| “Mọi biến `int` đều mặc định bằng `0`.” | Sai | Chỉ field/phần tử mảng thuộc diện khởi tạo mặc định; biến cục bộ chưa gán không được đọc |
| “`int x;` trong method rồi `print(x)` sẽ in `0`.” | Sai | Đây là lỗi biên dịch vì `x` chưa được gán chắc chắn |
| “`static int total;` có thể đọc ngay với giá trị `0`.” | Đúng | Static field có khởi tạo mặc định; initializer khác có thể thay đổi sau đó |
| “`String title;` là field thì tự thành chuỗi rỗng.” | Sai | Mặc định là `null`, không phải `""` |
| “`new int[4]` có bốn phần tử bằng `0`.” | Đúng | Phần tử mảng được khởi tạo mặc định |
| “`int[] numbers;` trong method là mảng rỗng.” | Sai | Đây chỉ là biến cục bộ chưa được gán tham chiếu mảng |
| “Đã gán `null` tức là chưa qua definite assignment.” | Sai | `null` cũng là một giá trị được gán hợp lệ cho kiểu tham chiếu |
| “Dùng `++` hoặc `+=` là cách gán đầu tiên cho local.” | Sai | Toán tử phải đọc giá trị cũ trước, nên local phải được gán từ trước |

### Cách quyết định nhanh khi đọc code

1. Xác định biến được khai báo **ở đâu**: field, local, tham số hay phần tử mảng.
2. Nếu là field/phần tử mảng mới, tìm giá trị mặc định theo kiểu; sau đó kiểm tra initializer/constructor/những lệnh gán có thể thay đổi nó.
3. Nếu là biến cục bộ, tìm mọi đường đi đến **lần đọc đầu tiên** và xác minh mỗi đường đã có phép gán.
4. Nếu có `final`, kiểm tra thêm ràng buộc chỉ được gán theo quy tắc một lần và mọi đường khởi tạo bắt buộc.
5. Nếu là kiểu tham chiếu, phân biệt **đã được gán** với **khác `null`**; nếu là wrapper như `Integer`, lưu ý unboxing.

Điểm cần ghi nhớ là: **giá trị mặc định của field và phần tử mảng là hành vi khởi tạo của Java; yêu cầu gán biến cục bộ trước khi đọc là quy tắc kiểm tra của trình biên dịch**. Vì vậy, cùng câu lệnh `int x;` có thể hợp lệ để đọc `x` khi `x` là field, nhưng chưa đủ để đọc `x` nếu `x` là biến cục bộ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-types-008">Vì sao boolean không phải kiểu số</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `boolean` không phải kiểu số trong Java?

Trong Java, `boolean` là **kiểu dữ liệu nguyên thủy (primitive type) dành riêng cho giá trị logic**: chỉ có hai giá trị `true` và `false`. Nó không thuộc nhóm **kiểu số (numeric types)**, kể cả nhóm **kiểu số nguyên (integral types)**. Vì vậy, Java không hiểu `true` là số `1`, `false` là số `0`, cũng không tự coi một số khác `0` là điều kiện đúng.

## 1. Phân loại kiểu nguyên thủy: vị trí của `boolean`

Java có tám kiểu nguyên thủy, chia theo ý nghĩa và quy tắc ngôn ngữ:

| Nhóm | Kiểu | Giá trị/ý nghĩa |
| --- | --- | --- |
| Số nguyên (*integral*) | `byte`, `short`, `int`, `long` | Các số nguyên có dấu, với các độ rộng lần lượt 8, 16, 32 và 64 bit. |
| Số nguyên (*integral*) | `char` | Giá trị không dấu 16 bit, thường biểu diễn một đơn vị mã UTF-16; có thể tham gia một số phép toán số. |
| Số thực (*floating-point*) | `float`, `double` | Các giá trị dấu phẩy động theo quy tắc IEEE 754 của Java. |
| Logic | `boolean` | Chỉ `true` hoặc `false`; **không phải số**. |

Điểm dễ nhầm: tên nhóm “số nguyên” trong ngữ cảnh *Java Language Specification* có cả `char`, dù ta thường dùng `char` cho ký tự. Ngược lại, `boolean` **không** thuộc nhóm này. Khả năng chứa hai trạng thái không đồng nghĩa với khả năng chứa hai số nguyên.

Ví dụ, `int count = 0;` nói về **số lượng**, còn `boolean empty = true;` nói về **một mệnh đề đúng/sai**. Hai biến có thể liên hệ qua biểu thức `count == 0`, nhưng vẫn có kiểu và phép toán khác nhau.

## 2. Vì sao Java tách `boolean` khỏi số?

**Khác miền giá trị.** Một giá trị số mô tả lượng có thể tính toán, so sánh lớn/nhỏ, cộng, trừ, nhân, chia. Một giá trị `boolean` mô tả kết quả của điều kiện hoặc trạng thái logic. Không có ý nghĩa ngôn ngữ cho `true + 1` hay `false < true`.

**Kiểm tra kiểu ngay khi biên dịch.** Java yêu cầu biểu thức trong `if`, `while` và các điều kiện tương tự có kiểu `boolean` (có thể tự mở hộp từ `Boolean`). Java không có quy tắc “*truthy/falsy*” cho số như một số ngôn ngữ khác. Quy tắc này ngăn những lỗi như vô tình viết `if (count)` khi thật ra cần `if (count > 0)`.

**Tường minh tại ranh giới dữ liệu.** Khi một API hoặc hệ thống cũ quy ước `0`/`1` biểu thị tắt/bật, mã Java cần ánh xạ rõ ràng giữa số và logic. Cách đó khiến ý nghĩa các trường hợp ngoài `0` và `1` phải được quyết định, tránh giả định âm thầm.

Đây là **quy tắc của hệ thống kiểu Java**, không phải kết luận rằng máy tính không thể lưu trạng thái đúng/sai bằng các bit.

## 3. Những gán, ép kiểu và điều kiện nào hợp lệ?

Các câu lệnh sau **không biên dịch được** trong Java (mỗi dòng là ví dụ riêng về lỗi kiểu):

```java
boolean active = 1;        // Lỗi: int không chuyển thành boolean.
int value = true;          // Lỗi: boolean không chuyển thành int.
boolean flag = (boolean) 1; // Lỗi: không tồn tại phép cast int -> boolean.
int n = (int) false;       // Lỗi: không tồn tại phép cast boolean -> int.
if (1) {                   // Lỗi: điều kiện if phải là boolean.
    System.out.println("run");
}
boolean result = true + false; // Lỗi: phép cộng không áp dụng cho boolean.
```

Không có chuyển đổi số `byte`/`short`/`int`/`long`/`float`/`double` ↔ `boolean`, dù dùng gán ngầm, cast tường minh hay *numeric promotion*. **Autoboxing** cũng không biến `boolean` thành `Integer` hoặc `Number`.

Muốn suy ra một trạng thái từ số, hãy viết **một phép so sánh tạo `boolean`**:

```java
int count = 3;
boolean hasItems = count > 0; // true
boolean isZero = count == 0;  // false

if (count > 0) {
    System.out.println("Có phần tử");
}
```

Ở đây, hai toán hạng của `>` và `==` là số, nhưng **kết quả so sánh** là `boolean`. Với cờ có hai trạng thái, dùng `if (hasItems)` thay vì so sánh thừa `if (hasItems == true)`.

Nếu phải biểu diễn logic bằng `0`/`1` để giao tiếp với hệ thống bên ngoài, hãy **mã hóa/giải mã** tường minh:

```java
int encoded = hasItems ? 1 : 0; // boolean -> int theo quy ước của ứng dụng
boolean decoded = encoded == 1; // int -> boolean bằng phép so sánh
```

Hai dòng này dùng toán tử điều kiện và so sánh; **không** phải chuyển kiểu `boolean` ↔ `int`. Quy ước `0`/`1` là do ứng dụng hoặc giao thức đặt ra, không phải giá trị số tự có của `false`/`true`.

## 4. Toán tử được phép: phân biệt logic với số

| Phép toán | Với `boolean` | Với kiểu số |
| --- | --- | --- |
| `!` | Phủ định logic, ví dụ `!true` là `false`. | Không dùng cho số. |
| `&&`, `||` | AND/OR có **đoản mạch** (*short-circuit*). | Không dùng trực tiếp cho hai số. |
| `&`, `\|`, `^` | AND/OR/XOR logic, **luôn đánh giá cả hai vế**. | Là các toán tử theo bit cho các kiểu số nguyên phù hợp. |
| `==`, `!=` | So sánh hai giá trị logic. | So sánh các toán hạng số phù hợp. |
| `+`, `-`, `*`, `/`, `%` | Không áp dụng cho `boolean`. | Các phép toán số. |
| `<`, `<=`, `>`, `>=` | Không áp dụng cho `boolean`. | So sánh thứ tự số, trả về `boolean`. |
| `~`, `<<`, `>>`, `>>>` | Không áp dụng cho `boolean`. | Các toán tử theo bit/dịch bit cho kiểu số nguyên. |

*Chú ý:* Phép `+` trong `"active=" + true` là **nối chuỗi**, không phải phép cộng số trên `boolean`.

Ví dụ về đoản mạch:

```java
int denominator = 0;
boolean canDivide = denominator != 0 && 10 / denominator > 1;
System.out.println(canDivide); // false; vế chia không được đánh giá
```

Nếu thay `&&` bằng `&` trong ví dụ trên, vế phải vẫn được đánh giá và gây `ArithmeticException` do chia cho `0`. Sự khác nhau này xuất phát từ **cách đánh giá toán tử logic**, không liên quan đến chuyển `boolean` thành số.

## 5. JVM có thể dùng 0/1, nhưng ngôn ngữ vẫn xem `boolean` là logic

Ở tầng triển khai, máy ảo Java có thể biểu diễn giá trị logic bằng bit hoặc byte trong bộ nhớ; các lệnh bytecode xử lý biểu thức `boolean` trên *operand stack* thường sử dụng biểu diễn dựa trên `int`. Trình biên dịch có thể phát sinh hằng `0` cho `false` và `1` cho `true` ở tầng bytecode.

Nhưng cần tách ba tầng:

1. **Mã nguồn Java:** quy tắc kiểu chỉ cho phép `true`/`false`, không có cast giữa `boolean` và `int`.
2. **Bytecode/JVM:** biểu diễn và tập lệnh vận hành theo đặc tả JVM; một số hoạt động trên boolean dựa vào lệnh số nguyên.
3. **Bộ nhớ/hệ điều hành/hệ thống bên ngoài:** cách đóng gói bit, byte hay `0`/`1` có thể tùy triển khai hoặc giao thức.

Không suy ra từ bytecode rằng `boolean` “thực chất là `int`” trong Java. Cũng **không được giả định** mọi biến `boolean` chiếm đúng 1 bit hay 1 byte trong bộ nhớ: Java không định nghĩa kích thước lưu trữ cố định của `boolean` như đối với độ rộng giá trị của các kiểu số nguyên.

Giá trị mặc định của **trường** kiểu `boolean` và phần tử của `boolean[]` là `false`. Biến cục bộ không tự được gán giá trị mặc định để có thể đọc tùy ý; trước khi đọc, nó phải được gán chắc chắn (*definite assignment*).

## 6. `Boolean` và `Number` cũng là hai nhánh khác nhau

`boolean` là kiểu nguyên thủy; `java.lang.Boolean` là lớp bọc (*wrapper*) cho nó. `Boolean` **không kế thừa `java.lang.Number`**, trong khi các lớp bọc số như `Integer`, `Long`, `Double` là những lớp con của `Number`.

```java
Boolean boxed = Boolean.TRUE; // Hợp lệ: Boolean chứa giá trị logic.
boolean unboxed = boxed;      // Hợp lệ: tự mở hộp.
Number amount = Integer.valueOf(1); // Hợp lệ.
 // Number wrong = Boolean.TRUE;    // Không biên dịch: Boolean không là Number.
```

`Boolean.parseBoolean("true")` trả về `true` (không phân biệt hoa/thường cho chữ `true`); `Boolean.parseBoolean("1")` trả về `false`. Đây là thao tác đọc **chuỗi theo quy tắc của API**, không phải phép chuyển số `1` sang `true`.

Lưu ý thêm: biến `Boolean` là tham chiếu nên có thể là `null`; tự mở hộp một giá trị `null` sẽ gây `NullPointerException`. Dùng `boolean` nguyên thủy cho cờ hai trạng thái nếu không cần biểu thị trạng thái `null`.

## 7. Ví dụ Java 17+ chạy độc lập

Tạo tệp **`BooleanNumberDemo.java`**:

```java
public class BooleanNumberDemo {
    public static void main(String[] args) {
        int pending = 0;
        boolean hasPending = pending > 0;
        boolean enabled = true;
        boolean shouldRun = enabled && hasPending;

        int flagForLegacyApi = enabled ? 1 : 0;
        boolean fromLegacyApi = decodeZeroOne(flagForLegacyApi);

        System.out.println("hasPending=" + hasPending);
        System.out.println("shouldRun=" + shouldRun);
        System.out.println("flagForLegacyApi=" + flagForLegacyApi);
        System.out.println("fromLegacyApi=" + fromLegacyApi);
    }

    private static boolean decodeZeroOne(int value) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("Flag must be 0 or 1");
        }
        return value == 1;
    }
}
```

Biên dịch và chạy:

```bash
javac BooleanNumberDemo.java
java BooleanNumberDemo
```

Kết quả:

```text
hasPending=false
shouldRun=false
flagForLegacyApi=1
fromLegacyApi=true
```

Đọc lần lượt: `pending > 0` tạo một giá trị logic; `&&` kết hợp hai điều kiện; `enabled ? 1 : 0` mã hóa cờ thành số theo quy ước; `decodeZeroOne` kiểm tra đầu vào đúng miền `{0, 1}` trước khi biến nó thành điều kiện logic bằng `==`. Nếu đầu vào là `2` hoặc `-1`, hàm chủ động báo lỗi thay vì ngầm coi mọi số khác `0` là `true`.

## 8. Những nhầm lẫn cần tránh

- **“Vì boolean chỉ có hai giá trị nên nó là số nguyên 1 bit.”** Sai về loại dữ liệu: nó có hai giá trị logic; kích thước lưu trữ là vấn đề triển khai.
- **“`true` có nghĩa là `1` trong Java.”** Sai ở mã nguồn Java. Một giao thức hay đoạn bytecode có thể dùng `1` để biểu diễn `true`, nhưng điều đó không tạo ra phép chuyển kiểu Java.
- **“Có thể viết `if (n)` vì `n` là số.”** Sai. Hãy phát biểu điều kiện rõ ràng, ví dụ `if (n != 0)` hoặc `if (n > 0)`, tùy ý nghĩa.
- **“Không thể dùng `&`/`\|`/`^` với boolean.”** Sai. Các toán tử này có dạng áp dụng cho boolean, nhưng không đoản mạch như `&&`/`||`.
- **“`Boolean` là một kiểu `Number`.”** Sai. `Boolean` là wrapper của kiểu logic, đứng ngoài hệ phân cấp `Number`.
- **“Chuyển chuỗi `"1"` qua `Boolean.parseBoolean` sẽ được `true`.”** Sai. Phải tự xác định quy ước chuyển đổi hoặc kiểm tra đầu vào.

**Ghi nhớ:** `boolean` dùng để biểu diễn một mệnh đề **đúng/sai**; các kiểu số dùng để biểu diễn **lượng**. Dùng **so sánh** để tạo logic từ số, dùng **quy ước mã hóa tường minh** khi phải chuyển logic sang số cho hệ thống bên ngoài; không dựa vào biểu diễn ở JVM để vượt qua quy tắc kiểu của Java.

Nguồn đặc tả tham khảo: [Java Language Specification, §4.2 (Primitive Types and Values)](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2) và [Java Virtual Machine Specification, §2.3.4 (The boolean Type)](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.3.4).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-types-009">Chọn int hay long cho số đếm, chỉ số và kích thước</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn `int` hay `long` cho số đếm, chỉ số và kích thước trong Java

Trong Java, khi làm việc với **số lượng phần tử**, **chỉ số truy cập** hoặc **kích thước dữ liệu**, câu hỏi cần trả lời không chỉ là “số này có lớn không?”. Cần biết **miền giá trị lớn nhất có thể xảy ra**, **phép tính trung gian**, và **kiểu mà API Java yêu cầu**. Một mảng luôn được đánh chỉ số bằng `int`, nhưng tổng số phần tử xử lý qua nhiều mảng hoặc kích thước tệp có thể cần `long`. Chọn đúng kiểu giúp kết quả chính xác, giới hạn được kiểm tra rõ ràng và API dễ sử dụng.

## 1. Giới hạn thực tế của hai kiểu

| Thuộc tính | `int` | `long` |
| --- | --- | --- |
| Số bit biểu diễn | 32 bit, có dấu | 64 bit, có dấu |
| Giá trị nhỏ nhất | −2.147.483.648 | −9.223.372.036.854.775.808 |
| Giá trị lớn nhất | 2.147.483.647 | 9.223.372.036.854.775.807 |
| Hằng giới hạn | `Integer.MIN_VALUE`, `Integer.MAX_VALUE` | `Long.MIN_VALUE`, `Long.MAX_VALUE` |
| Literal | `100` (mặc định `int`) | `100L` (hậu tố `L`) |
| Ví dụ phù hợp | Chỉ số mảng/list, số lượng có cận trên vừa 32 bit | Tổng tích lũy lớn, byte offset, file size, thời gian/độ dài lớn khi API dùng `long` |

Cả hai đều là kiểu nguyên thủy **có dấu**. Trong bài toán số đếm và kích thước, thường chỉ cho phép giá trị không âm, nhưng việc miền âm không được dùng **không tự chuyển `int` thành kiểu 32 bit không dấu**. Giới hạn số đếm `int` vẫn là `2_147_483_647`, không phải `4_294_967_295`.

`long` có miền lớn hơn `int`, nhưng **vẫn hữu hạn**. Trong cả hai kiểu, phép `+`, `-`, `*` thông thường có thể tràn mà không ném ngoại lệ. `long` không tự giải quyết các phép tính vượt `Long.MAX_VALUE`.

**Nguyên tắc đầu tiên:** xác định giá trị lớn nhất **của từng biến và kết quả trung gian**, rồi chọn kiểu vừa miền; khi phụ thuộc một API cụ thể, phải tôn trọng kiểu tham số/giá trị trả về của API đó.

## 2. Chọn theo ý nghĩa của số: index, count, size, offset

Bốn từ thường gặp không hoàn toàn đồng nghĩa:

- **Index (chỉ số):** vị trí truy cập trong một cấu trúc, thường nằm trong `0..size-1`.
- **Count (số đếm):** số lần xảy ra/số bản ghi/số đối tượng đã xử lý; có thể tăng dần qua nhiều batch hoặc nhiều nguồn.
- **Size/length (kích thước/độ dài):** số phần tử của tập hợp, số byte của tệp, hay số đơn vị đo khác; phải xác định **đơn vị**.
- **Offset (độ dời):** vị trí tương đối so với đầu dữ liệu; một offset trong một mảng có thể là `int`, còn offset trong một tệp hàng chục GB cần `long`.

Ví dụ xử lý tuần tự 500 tệp, mỗi tệp chứa 10 triệu bản ghi: mỗi tệp có thể đọc từng phần tử bằng `int`, nhưng **tổng 5 tỷ bản ghi** vượt `Integer.MAX_VALUE` và nên tích lũy trong `long`.

### Bảng quyết định theo API và miền giá trị

| Tình huống | Kiểu nên dùng | Vì sao |
| --- | --- | --- |
| `array[index]`, vòng `for` trên mảng | `int` cho `index` | Java yêu cầu chỉ số mảng kiểu tương thích `int`; `array.length` trả `int` |
| `List.get(index)`, `List.size()` | `int` cho index/size API | Chữ ký API `get(int)` và `size()` |
| Số phần tử của cấu trúc nhỏ, có cận trên rõ và vừa `int` | `int` | Ngắn gọn, đúng miền yêu cầu |
| Tổng số phần tử qua nhiều list, batch, tệp | `long` | Tổng có thể vượt kích thước của từng list |
| `Stream.count()`, `Files.size(path)` | `long` | API trả `long` |
| Offset byte trong tệp lớn, số byte đã đọc/ghi | `long` | Byte offset có thể vượt 2 GiB |
| Số lượng quá `Long.MAX_VALUE` hoặc phép tính tổ hợp cực lớn | `BigInteger` | Vượt khả năng `long` |
| Bộ đếm cập nhật đồng thời từ nhiều luồng | `AtomicInteger`/`AtomicLong` hoặc `LongAdder` theo miền và yêu cầu | Cần xử lý concurrency riêng; chỉ đổi kiểu không tạo tính nguyên tử |

Lưu ý `Collection.size()` có kiểu trả về `int`. Hợp đồng `Collection` cho phép trả `Integer.MAX_VALUE` khi chứa nhiều hơn giới hạn đó; do vậy `size()` **không phải một API tổng quát để đo chính xác mọi tập hợp siêu lớn**. Hầu hết mảng và collection Java phổ thông đã có ràng buộc dung lượng riêng theo triển khai hoặc bộ nhớ.

## 3. Chỉ số mảng và `List`: dùng `int` vì hợp đồng API

```java
int[] prices = {120, 240, 360};

for (int i = 0; i < prices.length; i++) {
    System.out.println("prices[" + i + "] = " + prices[i]);
}
```

Biến `i` là `int` vì `prices.length` có kiểu `int` và phép truy cập mảng sử dụng chỉ số nguyên tương thích với `int`. `List.get(int index)` cũng sử dụng `int`.

Không thể dùng `long` trực tiếp làm chỉ số mảng:

```java
int[] values = {5, 10};
long index = 1L;
// int value = values[index]; // Không biên dịch: long không phải kiểu chỉ số mảng hợp lệ.
int value = values[Math.toIntExact(index)]; // Hợp lệ khi index nằm trong miền int.
```

`Math.toIntExact` chỉ kiểm tra `long` có vừa miền `int` hay không; **chưa kiểm tra index hợp lệ với mảng**. Vẫn cần `0 <= index && index < values.length` trước khi truy cập. Nếu index bằng 100 nhưng mảng có 2 phần tử, chuyển kiểu thành công nhưng truy cập vẫn ném `ArrayIndexOutOfBoundsException`.

Một mảng Java không trở thành mảng có hơn 2 tỷ chỉ số chỉ vì biến chỉ số được đổi thành `long`. `array.length` có kiểu `int`; kích thước mảng khả dụng còn bị giới hạn bởi triển khai JVM, bộ nhớ và overhead. Nếu dữ liệu cần nhiều phần tử hơn mức có thể lưu trong một mảng, hãy cân nhắc **chia đoạn (segmentation)**, xử lý bằng luồng/batch, lưu trữ trên đĩa hoặc cấu trúc chuyên biệt có API chỉ số `long`.

### Vòng lặp và biên an toàn

Dạng thông thường sau đúng cho mảng, kể cả khi rỗng:

```java
for (int i = 0; i < array.length; i++) {
    use(array[i]); // use(...) chỉ là lời gọi minh họa, không phải chương trình độc lập.
}
```

Điều kiện `i < array.length` kết thúc trước khi index vượt biên. Tránh `i <= array.length` vì phần tử cuối có index `length - 1`. Khi `array.length == 0`, không có index hợp lệ; `length - 1` bằng `-1` và không nên dùng để truy cập.

Không viết các vòng lặp kết thúc bằng ngưỡng `Integer.MAX_VALUE` với điều kiện `i <= Integer.MAX_VALUE` mà tăng `i++`: sau giá trị lớn nhất, `int` tràn về số âm và có thể khiến vòng lặp chạy mãi. Nếu thực sự cần duyệt bộ đếm dài hơn miền `int`, hãy dùng `long` và xác định rõ điều kiện dừng.

## 4. Tổng số đếm và kích thước có thể cần `long` dù đầu vào là `int`

Một batch có 10.000 phần tử có thể dùng `int batchSize`. Nhưng cộng số phần tử qua 500.000 batch có thể vượt `int`. Hãy xác định kiểu **của phép cộng**, không chỉ kiểu biến nhận kết quả.

### Sai lầm: gán sang `long` sau khi đã tràn

```java
int previous = 2_000_000_000;
int incoming = 500_000_000;

long wrong = previous + incoming;         // -1794967296: cộng int trước.
long correct = (long) previous + incoming; // 2500000000: cộng long.
```

Biểu thức `previous + incoming` có kiểu `int`, nên phép cộng tràn **trước khi** được gán cho `long`. Ép ít nhất một toán hạng sang `long` từ đầu; khi bộ đếm cần tăng nhiều lần, khai báo biến tích lũy là `long` và cộng trực tiếp:

```java
long totalRecords = 0;
for (int batchRecords : new int[] {1_500_000_000, 1_500_000_000}) {
    totalRecords += batchRecords;
}
// totalRecords = 3000000000
```

`totalRecords += batchRecords` dùng phép cộng `long` vì toán hạng trái là `long`. Để phát hiện tràn của chính `long` thay vì chấp nhận vòng số, dùng `Math.addExact(totalRecords, batchRecords)`.

### Sai lầm tương tự với phép nhân kích thước

```java
int rows = 70_000;
int columns = 70_000;

long wrongCells = rows * columns;             // 605032704: int đã tràn.
long correctCells = (long) rows * columns;    // 4900000000.
long checkedCells = Math.multiplyExact((long) rows, columns);
```

Trong tính dung lượng, `rows * columns`, `itemCount * bytesPerItem`, `width * height * channels` đều cần kiểm tra **kết quả trung gian**. Chẳng hạn nếu `width`, `height` là `int`, biểu thức `(long) (width * height)` vẫn quá muộn. Nếu kết quả tính bằng `long` vẫn có nguy cơ vượt giới hạn, dùng `Math.multiplyExact(long, long)` hoặc mô hình `BigInteger` phù hợp.

## 5. Chuyển đổi giữa `long` và `int` tại ranh giới API

Chuyển đổi mở rộng `int -> long` bảo toàn mọi giá trị `int`. Java thực hiện ngầm:

```java
int count = 1_000_000;
long total = count; // Không mất dữ liệu.
```

Ngược lại, chuyển hẹp `long -> int` **có thể mất dữ liệu**. Java yêu cầu cast tường minh và chỉ giữ 32 bit thấp của giá trị `long`; kết quả có thể âm hoặc nhỏ hơn rất nhiều:

```java
long size = 3_000_000_000L;
int silentlyWrong = (int) size;
System.out.println(silentlyWrong); // -1294967296
```

Dùng `Math.toIntExact(long)` khi một API yêu cầu `int` và kết quả phải được kiểm tra:

```java
long fileBytes = 3_000_000_000L;
try {
    int bytesAsInt = Math.toIntExact(fileBytes);
    System.out.println(bytesAsInt);
} catch (ArithmeticException ex) {
    System.out.println("Size does not fit int");
}
```

`Math.toIntExact` ném `ArithmeticException` nếu nằm ngoài miền `int`. Hàm này **không kiểm tra** liệu kích thước có không âm, có phù hợp giới hạn bộ nhớ, hay có nằm trong biên của một mảng/list cụ thể.

Nếu nguồn là kích thước tệp lớn nhưng thao tác yêu cầu `int` phần tử mỗi lần, cách đúng thường là **chia thành nhiều chunk** kích thước `int` hợp lệ, thay vì ép cả kích thước xuống `int`. Ví dụ dùng biến `long remainingBytes` để theo dõi tổng số byte còn lại, nhưng mỗi lần đọc một buffer với `int chunkSize`.

## 6. Kiểu trả về của Java API giúp định hướng quyết định

### Các API thường trả `int`

- `array.length`: số phần tử mảng.
- `String.length()`: số **đơn vị mã UTF-16**, không đồng nhất với số ký tự người dùng nhìn thấy.
- `List.size()`, `Collection.size()`, `Map.size()`: số lượng theo hợp đồng của API, kiểu `int`.
- `List.get(int)`, `String.charAt(int)`: tham số chỉ số `int`.

### Các API thường trả `long`

- `java.nio.file.Files.size(Path)`: kích thước tệp tính bằng byte.
- `java.io.File.length()`: độ dài bằng byte theo hợp đồng `File`.
- `java.util.stream.Stream.count()`: số phần tử của stream; giá trị trả về là `long`.
- `java.nio.channels.SeekableByteChannel.position()` và `size()`: vị trí/kích thước theo byte dưới dạng `long`.

Không đổi kiểu trả về của API chỉ vì “thường dữ liệu nhỏ”. Nếu `Files.size(path)` trả `long`, hãy giữ giá trị trong `long` cho đến khi có một ranh giới API thật sự yêu cầu kiểu khác.

Ví dụ:

```java
// Ví dụ sử dụng API (không phải chương trình độc lập):
long bytes = java.nio.file.Files.size(path); // path là Path đã được tạo.
long matches = records.stream().filter(record -> matchesRule(record)).count();
// records/matchesRule được định nghĩa trong ứng dụng.
```

Dòng `Files.size` có thể ném `IOException` và cần xử lý phù hợp. `Stream.count()` trả `long` ngay cả khi stream chỉ có vài phần tử. Đừng nhầm số **phần tử** trong stream với tổng **byte** hoặc tổng **kích thước** của các phần tử.

## 7. Hiệu năng và bộ nhớ: không mặc định dùng `long` cho mọi thứ

Với mảng các giá trị primitive, mỗi phần tử `int` mang 32 bit còn `long` mang 64 bit; mảng `long[]` thường cần khoảng gấp đôi phần lưu **phần tử** so với `int[]` có cùng độ dài. Bố cục đối tượng, padding, header và tổ chức bộ nhớ thực tế do JVM quyết định nên không suy ra tổng RAM chỉ bằng phép nhân 4 hoặc 8 byte.

Chọn `int` cho chỉ số mảng/list giữ đúng API, tránh cast liên tục và rõ ràng về miền giá trị. Chọn `long` cho tổng số lượng/dung lượng lớn giúp tránh tràn. Không nên vì tiết kiệm vài byte mà chọn `int` khi cận trên có thể vượt 2,1 tỷ; cũng không nên dùng `long` để làm chỉ số cho API vốn chỉ hỗ trợ `int`.

Với `Integer` và `Long` (wrapper), ngoài phạm vi giá trị còn có **boxing, unboxing, null và danh tính đối tượng**. Không so sánh hai wrapper bằng `==` để suy ra chúng có cùng giá trị số học; khi kiểm tra giá trị dùng `equals` hoặc chuyển về primitive có kiểm soát null.

### Bộ đếm trong môi trường đa luồng

Hai câu lệnh `count++` trên `int` và `long` đều không tự bảo đảm phép tăng nguyên tử khi nhiều luồng cùng sửa. Theo tình huống, dùng `AtomicInteger`, `AtomicLong`, `LongAdder` hoặc đồng bộ hóa. Chọn `AtomicLong` khi miền giá trị cần `long` và cần các thao tác nguyên tử rõ ràng; `LongAdder` thường phù hợp bộ đếm cập nhật tần suất cao nhưng `sum()` không phải snapshot nguyên tử trong lúc có cập nhật đồng thời. Chúng **không tự phát hiện overflow**: vẫn phải có chính sách giới hạn.

## 8. Ví dụ hoàn chỉnh Java 17+

Chương trình sau cho thấy việc phối hợp `int` cho chỉ số và `long` cho tổng số đếm, cùng cách phát hiện chuyển hẹp không an toàn.

Tạo tệp `IntOrLongDemo.java`:

```java
import java.util.List;

public class IntOrLongDemo {
    public static void main(String[] args) {
        int[] samples = {5, 10, 15};
        long total = 0;

        for (int i = 0; i < samples.length; i++) {
            total += samples[i];
        }
        System.out.println("Array length: " + samples.length);
        System.out.println("Array sum: " + total);

        int previous = 2_000_000_000;
        int incoming = 500_000_000;
        long wrong = previous + incoming;
        long correct = (long) previous + incoming;
        System.out.println("Overflow before widening: " + wrong);
        System.out.println("Widen before adding: " + correct);

        int rows = 70_000;
        int columns = 70_000;
        long cells = Math.multiplyExact((long) rows, columns);
        System.out.println("Grid cells: " + cells);

        List<String> words = List.of("Java", "int", "long");
        System.out.println("List size: " + words.size());
        System.out.println("Stream count: " + words.stream().count());

        long requestedIndex = 2L;
        int index = Math.toIntExact(requestedIndex);
        if (index >= 0 && index < samples.length) {
            System.out.println("Sample at index 2: " + samples[index]);
        }

        try {
            Math.toIntExact(3_000_000_000L);
            System.out.println("Unexpected: conversion succeeded");
        } catch (ArithmeticException ex) {
            System.out.println("Long value does not fit int");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac IntOrLongDemo.java
java IntOrLongDemo
```

Kết quả chính xác:

```text
Array length: 3
Array sum: 30
Overflow before widening: -1794967296
Widen before adding: 2500000000
Grid cells: 4900000000
List size: 3
Stream count: 3
Sample at index 2: 15
Long value does not fit int
```

Giải thích: `samples.length` và `index` dùng `int` để truy cập mảng; `total` là `long` để tích lũy an toàn hơn khi tổng tăng lớn. `wrong` cho thấy việc gán kết quả tràn sang `long` không sửa được lỗi. `Math.multiplyExact` giúp phát hiện tràn phép nhân nếu kết quả vượt khả năng `long`; `Math.toIntExact` ngăn chuyển hẹp âm thầm.

## 9. Quy trình lựa chọn trong dự án

1. **Xác định đại lượng và đơn vị:** số phần tử, số bản ghi tích lũy, số byte, thời lượng hay index? Không dùng tên biến mơ hồ như `size` nếu đang đo nhiều đơn vị khác nhau; chọn `fileSizeBytes`, `totalRecords`, `recordIndex`.
2. **Tính cận trên của giá trị lẫn biểu thức trung gian:** số lượng theo batch có thể nhỏ nhưng tổng hoặc tích có thể lớn. Với đầu vào ngoài hệ thống, phải xác thực cận trên, không mặc định “sẽ không bao giờ lớn”.
3. **Xác nhận hợp đồng API:** chỉ số mảng và `List.get` cần `int`; `Files.size` và `Stream.count` trả `long`. Hãy giữ đúng kiểu gốc của API khi có thể.
4. **Chọn kiểu lưu và kiểu tính:** `int` cho chỉ số trong cấu trúc hỗ trợ index `int`; `long` cho tổng hoặc offset có thể vượt `int`. Ép toán hạng sang `long` **trước** phép cộng/nhân nếu cần.
5. **Đặt ranh giới chuyển đổi có kiểm tra:** dùng `Math.toIntExact`, kiểm tra không âm, kiểm tra `index < length` hoặc chia thành chunk; dùng `Math.addExact`/`multiplyExact` nếu tràn là lỗi nghiệp vụ.
6. **Kiểm tra khi dữ liệu tăng hoặc đa luồng:** chọn primitive, atomic counter hay `BigInteger` dựa vào yêu cầu về miền, concurrency và tính nguyên tử; kiểm thử các giá trị gần `Integer.MAX_VALUE` và `Long.MAX_VALUE`.

### Những lỗi thường gặp

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| `long total = intA + intB;` | Phép cộng đã tràn trước khi gán | `long total = (long) intA + intB;` |
| `long bytes = intCount * intBytesPerItem;` | Phép nhân chạy với `int` | `Math.multiplyExact((long) intCount, intBytesPerItem)` |
| `(int) fileSizeBytes` | Cắt bit, kết quả có thể âm | Giữ `long`, chia chunk; chỉ dùng `Math.toIntExact` khi chắc vừa |
| Dùng `long` trực tiếp cho `array[index]` | Không biên dịch | Dùng `int` và kiểm tra miền index |
| Xem `array.length` là dung lượng RAM hoặc byte | Nhầm số phần tử với byte | Ghi rõ đơn vị; tính byte có kiểm tra tràn và overhead |
| Dùng `<= array.length` | Truy cập index bằng độ dài, vượt biên | Dùng `< array.length` |
| Dùng `int` cho tổng bản ghi qua thời gian dài | Có thể overflow dù từng batch nhỏ | Dùng `long`/kiểm tra với `addExact` |
| Dùng `long` để mong mảng/list thông thường chứa tùy ý dữ liệu lớn | API và bộ nhớ vẫn giới hạn | Phân đoạn, streaming, lưu trữ ngoài bộ nhớ |

## 10. Câu hỏi tự kiểm tra

- Một `List` hiện có 500.000 phần tử, nhưng cần tính tổng số phần tử được xử lý trong nhiều năm. Kiểu nào nên dùng cho `listIndex` và `totalProcessed`? Vì sao?
- Với `int rows = 70_000`, `int cols = 70_000`, khác biệt giữa `long total = rows * cols` và `long total = (long) rows * cols` là gì?
- Vì sao `Math.toIntExact(longIndex)` chưa bảo đảm `array[index]` an toàn?
- Khi `Files.size(path)` trả 5.000.000.000 byte, có nên ép sang `int` để tạo một buffer không? Nếu không, làm thế nào để xử lý từng phần?
- Trong tác vụ nhiều luồng, đổi `int` thành `long` có ngăn được race condition không? Khi nào nên dùng `AtomicLong` hoặc `LongAdder`?

**Kết luận áp dụng:** dùng `int` khi miền giá trị đã được giới hạn và API truy cập yêu cầu `int`; dùng `long` khi tổng, kích thước hay offset có thể vượt miền `int`. Luôn kiểm tra **kiểu của phép toán trước khi gán**, giới hạn dữ liệu, và chuyển đổi giữa hai kiểu tại ranh giới API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-types-010">Giới hạn của kiểu cố định so với số nguyên toán học</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của kiểu cố định so với số nguyên toán học trong Java

## 1. Vấn đề cốt lõi: cùng gọi là “số nguyên” nhưng khác miền giá trị

Trong toán học, tập số nguyên `ℤ = {…, -2, -1, 0, 1, 2, …}` **không có số lớn nhất hoặc nhỏ nhất**. Nếu `a` và `b` là số nguyên, thì `a + b`, `a - b` và `a × b` vẫn là số nguyên toán học; kết quả có thể lớn tùy ý.

Trong Java, `byte`, `short`, `int` và `long` là các **kiểu số nguyên có độ rộng cố định**. Mỗi kiểu chỉ biểu diễn được một tập hữu hạn các giá trị. Phép tính bằng các kiểu này có thể cho kết quả vượt khỏi miền biểu diễn, dù kết quả toán học hoàn toàn hợp lệ.

Ví dụ:

- Toán học: `2_147_483_647 + 1 = 2_147_483_648`.
- Java với `int`: `Integer.MAX_VALUE + 1` có kết quả `-2_147_483_648` do **tràn số nguyên** (*integer overflow*).
- Java với `long`: `(long) Integer.MAX_VALUE + 1` cho đúng kết quả toán học `2_147_483_648`, vì giá trị này còn nằm trong miền `long`.

Đây là giới hạn của **kiểu dữ liệu được chọn và phép toán đang thực hiện**, không có nghĩa phép cộng nguyên trong toán học có điểm kết thúc.

## 2. Miền biểu diễn của từng kiểu nguyên thủy

Java định nghĩa độ rộng giá trị và miền của các kiểu số nguyên, độc lập với cách máy tính cụ thể sắp xếp bộ nhớ:

| Kiểu | Độ rộng | Miền giá trị | Ghi chú |
| --- | ---: | --- | --- |
| `byte` | 8 bit | `-128` đến `127` | Có dấu. |
| `short` | 16 bit | `-32_768` đến `32_767` | Có dấu. |
| `int` | 32 bit | `-2_147_483_648` đến `2_147_483_647` | Kiểu nguyên mặc định của nhiều biểu thức. |
| `long` | 64 bit | `-9_223_372_036_854_775_808` đến `9_223_372_036_854_775_807` | Có dấu; hậu tố literal `L`. |
| `char` | 16 bit | `0` đến `65_535` (`\u0000` đến `\uffff`) | Không dấu, biểu diễn một đơn vị mã UTF-16; thuộc nhóm *integral* trong đặc tả Java. |

Với kiểu số nguyên có dấu độ rộng `w` bit, các giá trị nằm trong khoảng:

```text
MIN = -2^(w - 1)
MAX =  2^(w - 1) - 1
```

Ví dụ `int` có `w = 32`, nên `MIN = -2^31` và `MAX = 2^31 - 1`. Phần âm có nhiều hơn phần dương **một giá trị** vì số `0` chiếm một vị trí ở phía không âm.

**Lưu ý phân loại:** `char` là kiểu *integral* nhưng không phải kiểu số nguyên có dấu. `boolean` hoàn toàn không phải kiểu số. Trong bài này, các quy tắc tràn số tập trung vào `byte`, `short`, `int` và `long`, đồng thời nhắc đến `char` khi cần hiểu chuyển kiểu.

Các hằng `Byte.MIN_VALUE`, `Short.MAX_VALUE`, `Integer.MIN_VALUE`, `Long.MAX_VALUE` giúp truy cập giới hạn của từng kiểu mà không phải tự gõ các số dài.

## 3. Tại sao phép toán Java có thể “quay vòng” giá trị?

Hãy hình dung mỗi độ rộng `w` chỉ có `2^w` mẫu bit có thể biểu diễn. Đối với `int` 32 bit, phép cộng, trừ, nhân thông thường giữ lại kết quả theo modulo `2^32`; mẫu bit cuối cùng được diễn giải thành một giá trị có dấu theo biểu diễn bù hai (*two's complement*).

Với kết quả toán học `r`, có thể hình dung phép tính `int` như sau:

1. Chọn phần dư không âm `u = r mod 2^32`, nằm trong `0 .. 2^32 - 1`.
2. Nếu `u <= 2^31 - 1` thì giá trị `int` là `u`.
3. Nếu `u >= 2^31` thì giá trị `int` là `u - 2^32`.

Bởi vậy:

```text
2147483647 + 1 = 2147483648    (kết quả toán học)
2147483648 mod 4294967296
    = 2147483648                 (mẫu bit 0x80000000)
Đọc dưới dạng int có dấu:
2147483648 - 4294967296
    = -2147483648
```

Dòng code sau **hợp lệ** và cho kết quả âm:

```java
int max = Integer.MAX_VALUE;
int wrapped = max + 1;
System.out.println(wrapped); // -2147483648
```

Java quy định hành vi tràn số trong các phép toán `int`/`long` thông thường; chương trình **không tự ném ngoại lệ** khi cộng, trừ hay nhân bị tràn. Đây là hành vi xác định theo ngôn ngữ, không phải kết quả ngẫu nhiên hoặc *undefined behavior*.

Tính chất “quay vòng” tương tự xảy ra tại giới hạn nhỏ nhất: `Integer.MIN_VALUE - 1` cho `Integer.MAX_VALUE`. Với toán hạng `long`, mô hình tương ứng dùng modulo `2^64`.

### Tràn số không chỉ xuất hiện khi cộng

```java
int a = 50_000;
int b = 50_000;
int wrong = a * b; // -1794967296; kết quả toán học là 2500000000.
```

Lấy `2_500_000_000 - 4_294_967_296 = -1_794_967_296`. Kết quả sai về *ý nghĩa toán học* nếu bạn mong phép nhân giữ nguyên độ chính xác, nhưng vẫn đúng với ngữ nghĩa phép nhân `int` cố định.

Một giới hạn ít thấy hơn là số nhỏ nhất không có số đối nằm trong cùng kiểu:

```java
int min = Integer.MIN_VALUE;
System.out.println(-min);          // -2147483648 (vẫn tràn)
System.out.println(Math.abs(min)); // -2147483648
```

Về toán học, đối của `-2_147_483_648` là `+2_147_483_648`, nhưng `int` không chứa được giá trị dương đó. Vì thế, không được mặc định rằng `Math.abs(x) >= 0` với **mọi** `int`/`long`; trường hợp `MIN_VALUE` là ngoại lệ.

## 4. Kiểu của biểu thức quyết định nơi phép tính diễn ra

Một biến nhận có kiểu rộng hơn **không thể khôi phục** kết quả đã tràn trong biểu thức hẹp.

```java
int x = 50_000;
int y = 50_000;

long tooLate = x * y;          // -1794967296: tính bằng int trước, rồi đổi sang long.
long correct = (long) x * y;   // 2500000000: một toán hạng là long từ trước khi nhân.
long alsoCorrect = x * 50_000L; // 2500000000: literal long.
```

Quy tắc cần nhớ:

- Với nhiều toán tử số nhị phân như `+`, `-`, `*`, `/`, `%`, Java áp dụng *binary numeric promotion*: nếu một toán hạng là `long` (và không có `float`/`double` tham gia) thì tính bằng `long`; nếu không, các toán hạng nguyên hẹp hơn được nâng lên `int`.
- Các phép cộng/nhân trên `byte`, `short`, `char` thông thường cho biểu thức kiểu `int`. Chẳng hạn, `byte r = (byte) (a + b)` cần cast nếu muốn lưu kết quả vào `byte` mà biểu thức không thỏa ngoại lệ gán hằng số khi biên dịch.
- Chuyển đổi **sau phép tính** chỉ đổi kiểu cho kết quả đã tính; muốn nới miền tính toán, chuyển kiểu **một toán hạng trước phép tính**.
- Nếu chính kết quả toán học vượt cả giới hạn `long`, nâng lên `long` vẫn không giải quyết được; cần biểu diễn số nguyên với độ chính xác tùy ý.

### Literal số nguyên cũng có kiểu và giới hạn

```java
long ok = 3_000_000_000L; // Hậu tố L tạo literal long.
// long error = 3_000_000_000; // Lỗi biên dịch: literal thập phân vượt miền int.
```

Ngoài ra, `2147483648` không phải một literal `int` dương hợp lệ trong các ngữ cảnh thông thường; Java có quy tắc cú pháp đặc biệt cho literal xuất hiện ngay sau toán tử một ngôi `-` để viết `-2147483648`. Để dễ đọc và tránh nhầm lẫn trong code thực tế, ưu tiên `Integer.MIN_VALUE`/`Integer.MAX_VALUE` hoặc dùng literal có hậu tố `L` khi cần giá trị lớn.

## 5. Tràn số, chia số nguyên và ép kiểu hẹp là ba việc khác nhau

### 5.1. Phép chia nguyên làm tròn về 0

Khi cả hai toán hạng là số nguyên, phép chia `/` thực hiện chia nguyên và bỏ phần lẻ, **làm tròn về 0**, không cho ra số thực:

```java
System.out.println(7 / 2);   // 3
System.out.println(-7 / 2);  // -3
System.out.println(7 % 2);   // 1
System.out.println(-7 % 2);  // -1
```

Kết quả `7 / 2 = 3` ở đây là do ngữ nghĩa chia nguyên, **không phải** tràn số.

Chia nguyên cho 0 gây `ArithmeticException`. Đặc biệt, `Integer.MIN_VALUE / -1` trả về `Integer.MIN_VALUE`: thương toán học vượt miền `int`, nhưng đặc tả Java quy định trường hợp này **không ném ngoại lệ**. `Long.MIN_VALUE / -1L` tương tự. Khi bài toán cần phát hiện tất cả tràn số, đừng bỏ sót phép chia đặc biệt này.

### 5.2. Ép kiểu hẹp có thể làm mất bit

Chuyển `long` sang `int` có thể bỏ các bit cao, làm thay đổi giá trị:

```java
long large = 2_147_483_648L;
int narrowed = (int) large; // -2147483648
```

Ở đây không có phép cộng bị tràn; **chính chuyển đổi hẹp** làm mất khả năng biểu diễn giá trị ban đầu. Chuyển đổi `int` sang `byte`/`short`/`char` cũng có thể làm mất thông tin.

Ngược lại, nâng từ `int` sang `long` giữ nguyên chính xác mọi giá trị `int` vì toàn bộ miền `int` nằm trong miền `long`. Nhưng nâng lên `float` hoặc `double` là chuyện khác: kiểu dấu phẩy động có thể làm tròn một số nguyên lớn, dù độ lớn của số vẫn nằm trong miền biểu diễn.

### 5.3. Đừng nhầm với vấn đề của floating-point

Đối với `float`/`double`, giới hạn biểu diễn còn liên quan tới số mũ, độ chính xác và `Infinity`/`NaN`. Phép cộng nguyên tràn làm “quay vòng” theo độ rộng cố định; phép toán dấu phẩy động không dùng cùng quy tắc đó. Khi bài toán đòi hỏi đếm chính xác hay tính số nguyên lớn, không nên mặc nhiên đổi sang `double` như cách chữa tràn số.

## 6. Ba chiến lược xử lý theo yêu cầu thực tế

### Chiến lược A — Chọn kiểu nguyên thủy đủ rộng và nâng trước khi tính

Nếu đã chứng minh được kết quả nằm trong miền `long`, dùng `long` cho biểu thức:

```java
int totalUsers = 1_500_000;
int bytesPerUser = 4_096;
long totalBytes = (long) totalUsers * bytesPerUser;
```

Kết quả là `6_144_000_000`, vượt `int` nhưng nằm trong `long`. Cast ở vế phải trước phép nhân là điều quan trọng. Với dữ liệu ngoài dự tính, vẫn phải kiểm tra miền tổng thể nếu kết quả có khả năng vượt `long`.

### Chiến lược B — Dùng phép toán có kiểm tra tràn

Trong Java, lớp `Math` có các phương thức phát hiện tràn cho phép toán phù hợp: `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`, `Math.negateExact`, `Math.incrementExact`, `Math.decrementExact`, cùng `Math.toIntExact` để chuyển `long` xuống `int` có kiểm tra. Những phương thức này ném `ArithmeticException` khi phép toán được kiểm tra không biểu diễn được kết quả.

```java
int oldBalance = Integer.MAX_VALUE;
try {
    int updated = Math.addExact(oldBalance, 1);
    System.out.println(updated);
} catch (ArithmeticException ex) {
    System.out.println("Giá trị int không đủ chứa kết quả");
}
```

Cách này phù hợp nếu **tràn số là lỗi nghiệp vụ**: chẳng hạn số lượng, giới hạn bộ đếm, chỉ số hoặc dữ liệu bắt buộc nằm trong kiểu đã chọn.

Phép `Math.floorDiv` phục vụ quy tắc làm tròn phép chia, **không phải** một cơ chế kiểm tra tất cả các trường hợp tràn. Nếu dùng trực tiếp phép chia và cần tính chính xác, phải xử lý riêng `MIN_VALUE / -1` và phép chia cho 0.

### Chiến lược C — Dùng `BigInteger` khi không biết trước giới hạn

`java.math.BigInteger` lưu số nguyên có độ chính xác tùy ý (*arbitrary precision*). Nó **không phải** kiểu nguyên thủy, có kích thước lưu trữ tăng theo độ lớn và chịu giới hạn bộ nhớ/tài nguyên thực tế, nhưng không bị giới hạn 32 hoặc 64 bit như `int`/`long`.

```java
import java.math.BigInteger;

BigInteger a = BigInteger.valueOf(Long.MAX_VALUE);
BigInteger b = a.add(BigInteger.ONE);
System.out.println(b); // 9223372036854775808
```

`BigInteger` là bất biến (*immutable*): `a.add(...)` trả về một đối tượng kết quả mới, không sửa `a`. Dùng `add`, `subtract`, `multiply`, `divide`, `remainder`, `compareTo` thay cho các toán tử số thông thường.

**Rất quan trọng:** hãy chuyển sang `BigInteger` **trước khi** biểu thức nguyên thủy bị tràn:

```java
long a = Long.MAX_VALUE;
BigInteger correct = BigInteger.valueOf(a).add(BigInteger.ONE);

// Sai mục đích nếu muốn giữ kết quả toán học:
// BigInteger tooLate = BigInteger.valueOf(a + 1); // a + 1 đã tràn long.
```

Tạo số nguyên lớn vượt miền `long` từ chuỗi thập phân bằng `new BigInteger("123456789012345678901234567890")`. Khi quay lại kiểu nguyên thủy, `intValueExact()` và `longValueExact()` giúp phát hiện giá trị vượt miền; `intValue()` và `longValue()` có thể làm mất dữ liệu.

| Nhu cầu | Lựa chọn | Lý do |
| --- | --- | --- |
| Giá trị nhỏ, miền chắc chắn hữu hạn | `int` | Đơn giản, phép toán trực tiếp, hiệu quả. |
| Kết quả có thể vượt `int`, vẫn nằm trong `long` | `long` và nâng trước phép toán | Giữ tính chính xác trong miền 64 bit. |
| Tràn số phải bị phát hiện | Các phương thức `Math.*Exact` hoặc kiểm tra miền chuyên biệt | Báo lỗi thay vì giữ kết quả quay vòng. |
| Số nguyên vượt 64 bit hoặc độ lớn đầu vào không xác định | `BigInteger` | Độ chính xác tùy ý trong giới hạn tài nguyên. |
| Giao thức/thuật toán cố ý dùng phép toán modulo `2^w` | Phép toán nguyên thủy với thiết kế rõ ràng | Kết quả quay vòng có thể là hành vi mong muốn. |

Không có một kiểu đúng cho mọi tình huống: phải xác định **miền dữ liệu đầu vào**, **giới hạn kết quả**, **yêu cầu phát hiện lỗi** và **chi phí tính toán** trước khi lựa chọn.

## 7. Ví dụ hoàn chỉnh Java 17+ có thể chạy

Tạo file **`FixedWidthVsMathInteger.java`**:

```java
import java.math.BigInteger;

public class FixedWidthVsMathInteger {
    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;
        System.out.println("int max = " + max);
        System.out.println("max + 1 as int = " + (max + 1));
        System.out.println("max + 1 as long = " + ((long) max + 1));

        int x = 50_000;
        int y = 50_000;
        long afterOverflow = x * y;
        long beforeOperation = (long) x * y;
        System.out.println("int multiplication then long = " + afterOverflow);
        System.out.println("long multiplication = " + beforeOperation);

        try {
            Math.addExact(max, 1);
        } catch (ArithmeticException ex) {
            System.out.println("Math.addExact detected overflow");
        }

        long large = 2_147_483_648L;
        System.out.println("narrowing long to int = " + (int) large);

        BigInteger beyondLong = BigInteger.valueOf(Long.MAX_VALUE)
                .add(BigInteger.ONE);
        System.out.println("BigInteger beyond long = " + beyondLong);
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```bash
javac FixedWidthVsMathInteger.java
java FixedWidthVsMathInteger
```

Kết quả chuẩn:

```text
int max = 2147483647
max + 1 as int = -2147483648
max + 1 as long = 2147483648
int multiplication then long = -1794967296
long multiplication = 2500000000
Math.addExact detected overflow
narrowing long to int = -2147483648
BigInteger beyond long = 9223372036854775808
```

Đọc ví dụ theo trình tự: `int` quay vòng tại giới hạn; `long` cho phép giữ kết quả khi nâng toán hạng trước phép tính; `Math.addExact` phát hiện tràn; cast hẹp có thể mất dữ liệu; `BigInteger` biểu diễn giá trị vượt cả `long`.

## 8. Các lỗi thường gặp khi so sánh với toán học

1. **Cho rằng “kết quả số nguyên luôn chính xác”.** Điều đó chỉ đúng khi kết quả nằm trong miền kiểu đang tính và không có chuyển kiểu gây mất dữ liệu.
2. **Gán kết quả vào `long` sau phép nhân `int`.** Biểu thức vẫn tính bằng `int`. Hãy chuyển một toán hạng sang `long` từ trước.
3. **Dùng `Math.abs(Integer.MIN_VALUE)` để chắc chắn có số dương.** Biểu thức trả lại giá trị âm nhỏ nhất do không có số đối dương trong `int`.
4. **Tin rằng Java sẽ tự cảnh báo/ném lỗi khi phép cộng số nguyên tràn.** Các toán tử số nguyên thông thường không tự kiểm tra tràn; hãy chọn API `*Exact` nếu cần.
5. **Xem `BigInteger.valueOf(x + y)` như một phép cộng chính xác tùy ý.** Nếu `x + y` thực hiện bằng kiểu nguyên thủy, có thể tràn trước khi vào `BigInteger`.
6. **Dùng `double` để thay số nguyên lớn cần chính xác tuyệt đối.** `double` có số chữ số chính xác hữu hạn nên có thể không phân biệt được các số nguyên lớn liên tiếp.
7. **Xem ép kiểu hẹp và tràn phép toán là cùng một thao tác.** Cả hai có thể thay đổi giá trị, nhưng nguyên nhân và vị trí phát sinh khác nhau; cần kiểm tra riêng.
8. **Giả định mọi phép chia đều giữ lại một số nguyên toán học.** Phép chia nguyên làm tròn về 0; phép chia cho 0 ném ngoại lệ; `MIN_VALUE / -1` là trường hợp đặc biệt vượt miền.

## 9. Cách suy nghĩ khi thiết kế chương trình

Với mỗi phép tính số nguyên, hãy tự hỏi:

- **Phép tính toán học là gì?** Tính được cận trên/cận dưới từ miền đầu vào không?
- **Java thực hiện phép tính ở kiểu nào?** `int`, `long`, hay `BigInteger`? Việc nâng kiểu xảy ra trước hay sau?
- **Kết quả có thể vượt miền không?** Nếu có, ứng dụng cần *báo lỗi*, *đổi sang kiểu rộng hơn*, *dùng độ chính xác tùy ý*, hay *cố ý chấp nhận modulo*?
- **Có bước chuyển kiểu hẹp hay chuyển định dạng nào không?** Chuyển `long` thành `int`, `BigInteger` về kiểu nguyên thủy hoặc chuyển qua số thực cần quy tắc riêng.

**Kết luận:** Số nguyên toán học không bị chặn bởi độ dài biểu diễn; `int` và `long` trong Java có miền hữu hạn. Hãy phân biệt *kết quả toán học* với *giá trị có thể lưu trong kiểu Java*, chọn kiểu trước khi tính, và chủ động kiểm soát tràn số nếu kết quả chính xác là yêu cầu của bài toán.

**Tài liệu đặc tả:** [Java Language Specification §4.2 (Primitive Types and Values)](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2), [§5.1.3 (Narrowing Primitive Conversion)](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.3), [§15.17–15.18 (Multiplicative and Additive Operators)](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.17), và [Java SE 21 BigInteger API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html).


</details>

- [Quay lại đầu trang](#back-to-top)
