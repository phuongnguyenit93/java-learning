<a id="back-to-top"></a>

# Tràn và kiểm tra biên

## Menu
- [Overflow số nguyên và hiện tượng quay vòng](#integer-overflow-001)
- [Vì sao phép cộng hoặc nhân tràn không tự ném ngoại lệ](#integer-overflow-002)
- [Kiểu của phép tính được xác định trước khi gán kết quả](#integer-overflow-003)
- [Nâng kiểu toán hạng trước phép nhân thay vì ép kiểu sau](#integer-overflow-004)
- [Math.addExact, subtractExact và multiplyExact](#integer-overflow-005)
- [incrementExact, decrementExact, negateExact và toIntExact](#integer-overflow-006)
- [Phép chia exact và các API bổ sung theo phiên bản JDK](#integer-overflow-007)
- [Math.abs với MIN_VALUE và lựa chọn absExact](#integer-overflow-008)
- [Kiểm tra giới hạn trước khi cấp phát hoặc tính kích thước](#integer-overflow-009)
- [Dùng BigInteger khi miền giá trị không có giới hạn phù hợp](#integer-overflow-010)

## <a id="integer-overflow-001">Overflow số nguyên và hiện tượng quay vòng</a>

<details>
<summary>Xem chi tiết</summary>

# Overflow số nguyên và hiện tượng quay vòng trong Java

Trong Java, **overflow (tràn số nguyên)** xảy ra khi kết quả toán học của một phép tính nằm ngoài phạm vi biểu diễn của kiểu số nguyên đang thực hiện phép tính. Với phép cộng, trừ và nhân thông thường trên `int` hoặc `long`, Java giữ lại mẫu bit có độ rộng cố định. Vì vậy giá trị có thể **quay vòng** từ giá trị dương lớn nhất sang giá trị âm nhỏ nhất, hoặc theo chiều ngược lại, mà **không có ngoại lệ tự động**.

Ví dụ tiêu biểu:

```java
int max = Integer.MAX_VALUE; // 2147483647
int wrapped = max + 1;       // -2147483648, không ném exception
```

Hai khai báo trên là mã minh họa đặt trong phương thức Java. Giá trị toán học đúng của `2_147_483_647 + 1` là `2_147_483_648`, nhưng một phép cộng `int` không thể chứa giá trị ấy. Muốn hiểu tại sao kết quả biến thành số âm, cần nhìn cả **giới hạn kiểu, mẫu bit bù hai và thứ tự chuyển kiểu**.

## 1. Phạm vi của các kiểu số nguyên

| Kiểu | Độ rộng | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | ---: | ---: | ---: |
| `byte` | 8 bit có dấu | -128 | 127 |
| `short` | 16 bit có dấu | -32_768 | 32_767 |
| `int` | 32 bit có dấu | -2_147_483_648 | 2_147_483_647 |
| `long` | 64 bit có dấu | -9_223_372_036_854_775_808 | 9_223_372_036_854_775_807 |

Các kiểu nguyên có dấu của Java dùng **biểu diễn bù hai (two's complement)**. Đối với một kiểu `w` bit, miền giá trị là từ `-2^(w-1)` đến `2^(w-1) - 1`. Một bit được dành cho ý nghĩa dấu; có nhiều hơn một giá trị âm so với giá trị dương.

Các hằng số thường dùng để kiểm tra ranh giới gồm `Byte.MIN_VALUE`, `Short.MAX_VALUE`, `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `Long.MIN_VALUE`, `Long.MAX_VALUE`. `char` cũng là kiểu nguyên thủy 16 bit nhưng **không dấu** (0–65_535), chủ yếu chứa đơn vị mã UTF-16; không được áp dụng nguyên xi phạm vi có dấu của `short` cho `char`.

**Phạm vi của toán hạng quyết định phép tính**, không phải tên biến nhận kết quả. Vì vậy phải hỏi “phép tính đang chạy ở `int` hay `long`?” trước khi xét biến đích.

## 2. Cơ chế quay vòng từ mẫu bit bù hai

Ở phép cộng, trừ, nhân nguyên thông thường, Java sử dụng phần thấp của kết quả biểu diễn bằng độ rộng đích; về mặt giá trị không dấu, đây là phép tính theo modulo `2^32` cho `int` và modulo `2^64` cho `long`. Sau đó cùng mẫu bit được diễn giải theo kiểu **có dấu**.

Ví dụ 32 bit tại ranh giới:

```text
Integer.MAX_VALUE =  2147483647 = 0x7FFFFFFF
                     +         1
------------------------------------------------
Mẫu 32 bit mới:                    0x80000000
Giá trị int có dấu:             -2147483648
```

Bit cao nhất (bit 31) chuyển từ `0` sang `1`. Với mẫu `0x8000_0000`, hệ bù hai diễn giải đó là `Integer.MIN_VALUE`. Bởi vậy:

```java
int a = Integer.MAX_VALUE + 1; // Integer.MIN_VALUE
int b = Integer.MIN_VALUE - 1; // Integer.MAX_VALUE
int c = -Integer.MIN_VALUE;    // Integer.MIN_VALUE
```

Đây là các khai báo minh họa hợp lệ trong phương thức. Trường hợp thứ ba đặc biệt: `int` không có giá trị dương `2_147_483_648`, nên đổi dấu `Integer.MIN_VALUE` bằng toán tử `-` lại nhận chính giá trị ban đầu.

Một cách mô hình hóa kết quả toán học `R` thành giá trị `int` là: giữ `R` theo modulo `2^32`, rồi nếu bit 31 bằng 1 thì trừ `2^32` để đọc dưới dạng có dấu. Với `long` thay `2^32` bằng `2^64`. Đây là cách **giải thích hành vi quấn vòng**, không có nghĩa Java tự biến `int` thành một kiểu unsigned.

Trong Java, hiện tượng này được quy định rõ cho số nguyên, chứ không phải một kết quả ngẫu nhiên phụ thuộc máy. Dù vậy, **hành vi được định nghĩa không có nghĩa kết quả đúng theo nghiệp vụ**: tổng tiền, số lượng, kích thước bộ đệm hoặc mốc thời gian bị âm bất ngờ vẫn là lỗi ứng dụng.

## 3. Overflow ở phép cộng, trừ, nhân và đổi dấu

| Phép tính | Kết quả toán học | Kết quả Java kiểu `int` |
| --- | ---: | ---: |
| `Integer.MAX_VALUE + 1` | 2_147_483_648 | -2_147_483_648 |
| `Integer.MIN_VALUE - 1` | -2_147_483_649 | 2_147_483_647 |
| `100_000 * 100_000` | 10_000_000_000 | 1_410_065_408 |
| `-Integer.MIN_VALUE` | 2_147_483_648 | -2_147_483_648 |

Với phép nhân, biểu thức `100_000 * 100_000` sử dụng hai `int`, vì vậy bản thân phép nhân cũng chạy trong `int`; giá trị đúng 10 tỷ nằm ngoài phạm vi 32 bit. Java giữ mẫu bit thấp, nên phép nhân cho `1_410_065_408`. Nếu ứng dụng lưu số lượng sản phẩm × đơn giá vào `int`, kết quả sẽ sai dù hai toán hạng riêng lẻ hoàn toàn hợp lệ.

Có hiện tượng tương tự ở `long`:

```java
long afterMax = Long.MAX_VALUE + 1L; // Long.MIN_VALUE
long beforeMin = Long.MIN_VALUE - 1L; // Long.MAX_VALUE
```

**`long` chỉ tăng phạm vi lên 64 bit**, không loại bỏ overflow vĩnh viễn. Đặc biệt `Math.abs(Integer.MIN_VALUE)` vẫn trả về `Integer.MIN_VALUE` (giá trị âm), vì không có `int` dương tương ứng; tương tự `Math.abs(Long.MIN_VALUE)` trả về `Long.MIN_VALUE`. Nếu cần bắt lỗi, Java 17 có `Math.negateExact` hoặc `Math.absExact` để ném `ArithmeticException` khi không biểu diễn được kết quả.

## 4. Gán vào `long` sau phép tính `int` không sửa được kết quả đã tràn

Một trường hợp hay gây lỗi:

```java
long wrong = 2_000_000_000 + 1_000_000_000;
long right = 2_000_000_000L + 1_000_000_000;
```

- Ở dòng `wrong`, **cả hai toán hạng đều là `int`**, phép cộng `int` tràn thành `-1_294_967_296`; sau đó Java mới mở rộng kết quả đó thành `long`. Kết quả vẫn là số âm.
- Ở dòng `right`, hậu tố `L` khiến một toán hạng là `long`. Toán hạng `int` còn lại được nâng lên `long` trước phép cộng, nên nhận đúng `3_000_000_000L`.

Kết quả còn phụ thuộc **vị trí ép kiểu**:

```java
long tooLate = (long) (100_000 * 100_000);  // 1410065408L
long correct1 = ((long) 100_000) * 100_000; // 10000000000L
long correct2 = 100_000L * 100_000;        // 10000000000L
```

Trong `tooLate`, cặp ngoặc buộc nhân bằng `int` trước; `(long)` chỉ nhận được kết quả đã tràn. Hai dòng `correct` chuyển ít nhất một toán hạng thành `long` **trước khi nhân**.

Phép cộng, trừ hoặc nhân hai `byte`/`short`/`char` thông thường còn được nâng kiểu lên **`int`**. Ví dụ, `byte a = 127; byte b = 1; int c = a + b;` cho `c == 128`, bởi phép cộng chạy ở `int`. Tuy nhiên, `byte a = 127; a++;` lại đưa `a` về `-128` do phép tăng và chuyển kết quả trở lại kiểu `byte`. Vì thế cần phân biệt overflow khi tính ở `int` với **chuyển hẹp** xuống kiểu nhỏ hơn.

## 5. Overflow số học, literal ngoài phạm vi và ép kiểu hẹp là ba tình huống khác

| Tình huống | Ví dụ | Điều xảy ra |
| --- | --- | --- |
| Literal thập phân không hợp lệ ngay trong mã nguồn | `long x = 3_000_000_000;` | **Lỗi biên dịch**: literal không có `L` vượt phạm vi `int` |
| Các toán hạng hợp lệ nhưng phép tính tràn | `int x = Integer.MAX_VALUE + 1;` | **Biên dịch được**, kết quả `Integer.MIN_VALUE` |
| Phép tính `int` tràn rồi mở rộng | `long x = 2_000_000_000 + 1_000_000_000;` | **Biên dịch được**, nhận giá trị `long` âm đã sai |
| Ép kiểu hẹp có mất bit | `byte x = (byte) 128;` | **Biên dịch được**, `x == -128` |
| Chuỗi số vượt phạm vi khi phân tích | `Integer.parseInt("2147483648")` | **Ném `NumberFormatException`**, không tự quay vòng |

Bảng này quan trọng khi chẩn đoán: nếu một literal trực tiếp vượt phạm vi thì chương trình **không chạy được**; nhưng khi hai số hợp lệ sinh ra tổng quá lớn thì phép toán nguyên thông thường có thể **chạy và cho giá trị sai**. Trong trường hợp ép hẹp tường minh, Java giữ các bit thấp theo độ rộng đích; đó là chuyển đổi kiểu có thể mất dữ liệu, không phải nguyên nhân giống hệt phép cộng tràn.

Một điểm khác: **biểu thức hằng** có thể được Java tính từ lúc biên dịch mà vẫn có kết quả quay vòng. Chẳng hạn `int wrapped = 2_147_483_647 + 1;` vẫn hợp lệ và bằng `-2_147_483_648`. Đừng suy rằng “được tính ở compile time” sẽ được báo lỗi overflow. Cách phát hiện tràn chủ động nằm ở bước kiểm tra hoặc các API có kiểm tra.

## 6. Ngoại lệ quan trọng: phép chia `MIN_VALUE / -1`

Phép chia số nguyên khác phép cộng, trừ, nhân. Thông thường chia cho **0** ném `ArithmeticException`. Tuy nhiên, Java có một trường hợp phép chia **vượt phạm vi nhưng không ném lỗi**:

```java
int q = Integer.MIN_VALUE / -1; // Integer.MIN_VALUE
long r = Long.MIN_VALUE / -1L;  // Long.MIN_VALUE
```

Giá trị toán học của `Integer.MIN_VALUE / -1` là `2_147_483_648`, không biểu diễn được bằng `int`; quy tắc Java trả lại chính `Integer.MIN_VALUE`. Tương tự cho `long`. Phần dư của trường hợp này là **0**.

Vì vậy, không nên nói “phép chia số nguyên luôn phát hiện overflow”. Khi tính số nguyên có dấu và đặc biệt khi có `MIN_VALUE`, phải kiểm tra `dividend == MIN_VALUE && divisor == -1` nếu yêu cầu kết quả toán học chính xác và được bảo đảm nằm trong phạm vi. Dùng kiểu rộng hơn (nếu đủ chỗ) hoặc `BigInteger` để biểu diễn thương toán học.

## 7. Chương trình Java 17+ có thể biên dịch và chạy độc lập

Tạo tệp **`IntegerOverflowDemo.java`**:

```java
import java.math.BigInteger;

public class IntegerOverflowDemo {
    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;
        int min = Integer.MIN_VALUE;
        long longMax = Long.MAX_VALUE;

        System.out.println("intMax=" + max);
        System.out.println("intMaxPlusOne=" + (max + 1));
        System.out.println("intMinMinusOne=" + (min - 1));
        System.out.println("negatedIntMin=" + (-min));
        System.out.println("productInInt=" + (100_000 * 100_000));
        System.out.println("lateWidening=" + (2_000_000_000 + 1_000_000_000));
        System.out.println("earlyLong=" + (2_000_000_000L + 1_000_000_000));
        System.out.println("longMaxPlusOne=" + (longMax + 1L));
        System.out.println("intMinDivMinusOne=" + (min / -1));
        System.out.println("narrowByte=" + ((byte) 128));
        System.out.println("wrappedBits=" + Integer.toHexString(max + 1));
        System.out.println("unsignedMinusOne=" + Integer.toUnsignedString(-1));
        System.out.println("unsignedCompare=" + Integer.compareUnsigned(-1, max));

        try {
            System.out.println(Math.addExact(max, 1));
        } catch (ArithmeticException ex) {
            System.out.println("addExact=overflow detected");
        }

        try {
            System.out.println(Math.multiplyExact(100_000, 100_000));
        } catch (ArithmeticException ex) {
            System.out.println("multiplyExact=overflow detected");
        }

        try {
            System.out.println(Math.negateExact(min));
        } catch (ArithmeticException ex) {
            System.out.println("negateExact=overflow detected");
        }

        try {
            System.out.println(Math.toIntExact(3_000_000_000L));
        } catch (ArithmeticException ex) {
            System.out.println("toIntExact=overflow detected");
        }

        BigInteger beyondLong =
                BigInteger.valueOf(longMax).add(BigInteger.ONE);
        System.out.println("beyondLong=" + beyondLong);
    }
}
```

Biên dịch rồi chạy:

```bash
javac --release 17 -encoding UTF-8 IntegerOverflowDemo.java
java IntegerOverflowDemo
```

Kết quả stdout (đã đối chiếu khi chạy):

```text
intMax=2147483647
intMaxPlusOne=-2147483648
intMinMinusOne=2147483647
negatedIntMin=-2147483648
productInInt=1410065408
lateWidening=-1294967296
earlyLong=3000000000
longMaxPlusOne=-9223372036854775808
intMinDivMinusOne=-2147483648
narrowByte=-128
wrappedBits=80000000
unsignedMinusOne=4294967295
unsignedCompare=1
addExact=overflow detected
multiplyExact=overflow detected
negateExact=overflow detected
toIntExact=overflow detected
beyondLong=9223372036854775808
```

Giải thích các kết quả chính:

- `intMaxPlusOne` và `intMinMinusOne` minh họa quay vòng qua hai biên 32 bit; `negatedIntMin` minh họa giá trị âm nhỏ nhất không có đối số dương tương ứng.
- `productInInt`, `lateWidening` và `earlyLong` cho thấy overflow xảy ra **trong kiểu của phép tính**, không phụ thuộc biến nhận.
- `longMaxPlusOne` khẳng định phép tính 64 bit cũng có biên; `intMinDivMinusOne` minh họa quy tắc đặc biệt của phép chia.
- `narrowByte=-128` là ví dụ **chuyển hẹp**; `wrappedBits=80000000` cho thấy mẫu 32 bit sau khi cộng `1` vào số lớn nhất.
- `unsignedMinusOne=4294967295` chỉ là cách **diễn giải không dấu** của mẫu `int` `-1`, không có thêm dung lượng lưu trữ; `unsignedCompare=1` nghĩa là `-1` được xem là lớn hơn `Integer.MAX_VALUE` **theo thứ tự không dấu**.
- Bốn phương thức có `Exact` chủ động ném `ArithmeticException` trong các trường hợp không biểu diễn được; `BigInteger` chứa được kết quả lớn hơn `Long.MAX_VALUE`.

## 8. Phát hiện overflow bằng các phương thức `Math.*Exact`

Khi yêu cầu nghiệp vụ là **phát hiện tràn thay vì nhận kết quả quấn vòng**, hãy dùng các phương thức có kiểm tra:

| API | Khi nào hữu ích |
| --- | --- |
| `Math.addExact(a, b)` | Phát hiện tràn khi cộng `int` hoặc `long` |
| `Math.subtractExact(a, b)` | Phát hiện tràn khi trừ `int` hoặc `long` |
| `Math.multiplyExact(a, b)` | Phát hiện tràn khi nhân `int` hoặc `long` |
| `Math.incrementExact(a)` / `decrementExact(a)` | Tăng/giảm biến đếm nhưng không âm thầm quay vòng |
| `Math.negateExact(a)` / `absExact(a)` | Xử lý dấu và trị tuyệt đối ở ranh giới âm nhỏ nhất |
| `Math.toIntExact(longValue)` | Đưa `long` sang `int` nhưng báo lỗi khi ngoài phạm vi |

Các phương thức trên ném **`ArithmeticException`** khi kết quả không biểu diễn được trong kiểu kết quả. Ví dụ:

```java
int count = 2_147_483_647;
int next = Math.incrementExact(count); // Ném ArithmeticException.
```

Đây là mảnh mã cần đặt trong phương thức. Nếu ứng dụng cần xử lý lỗi, đặt lời gọi trong `try/catch` hoặc để ngoại lệ được xử lý ở lớp phù hợp. Lưu ý `Math.addExact(int, int)` kiểm tra phạm vi **`int`**, kể cả khi kết quả được gán cho biến `long`. Muốn cộng trong 64 bit, hãy chuyển ít nhất một toán hạng sang `long` **trước khi gọi** overload kiểu `long`:

```java
long sum = Math.addExact(2_000_000_000L, 1_000_000_000L); // 3000000000
```

Các phương thức `Exact` chỉ hoạt động trên phép toán được gọi. Nếu một biểu thức con đã tràn trước đó, chúng **không thể khôi phục dữ liệu**:

```java
long wrong = Math.addExact((long) (Integer.MAX_VALUE + 1), 0L);
// Kết quả vẫn là -2147483648: tràn int xảy ra trước lời gọi.

long right = Math.addExact((long) Integer.MAX_VALUE, 1L);
// Kết quả chính xác: 2147483648.
```

## 9. Kiểm tra biên trước phép tính

Nếu không dùng các hàm `Exact`, đôi khi có thể kiểm tra biên trước khi cộng, ví dụ khi **`delta` không âm**:

```java
static int addNonNegativeExact(int current, int delta) {
    if (delta < 0) {
        throw new IllegalArgumentException("delta must be nonnegative");
    }
    if (current > Integer.MAX_VALUE - delta) {
        throw new ArithmeticException("int overflow");
    }
    return current + delta;
}
```

Đây là **phương thức** cần đặt bên trong một lớp Java. Vì `delta` đã được xác nhận không âm, `Integer.MAX_VALUE - delta` là an toàn trong phạm vi `int`; nếu `current` lớn hơn ngưỡng cho phép thì cộng sẽ tràn. Với phép cộng hai số bất kỳ có thể âm/dương, dùng `Math.addExact` thường ít lỗi hơn việc tự xây dựng nhiều điều kiện biên.

Các trường hợp thực tế nên coi overflow là lỗi thay vì tình huống quay vòng bình thường: tính tổng tiền, đếm số bản ghi, tính kích thước mảng/buffer, cộng số lượng tồn kho, tính tổng byte, kích thước file hoặc kiểm tra giới hạn đầu vào. Ví dụ, nếu mã tính `items * bytesPerItem` bằng `int` rồi dùng kết quả để cấp phát bộ nhớ, một tích tràn có thể biến thành số âm hoặc một số dương quá nhỏ. Dùng `Math.multiplyExact` và kiểm tra giới hạn thực tế trước khi cấp phát.

## 10. Khi nên dùng `long`, `BigInteger` hoặc unsigned API?

**Chọn `long`** khi kết quả có thể vượt phạm vi `int`, nhưng chứng minh được nó vẫn nằm trong phạm vi `long`. Điều kiện quan trọng là chuyển kiểu **trước phép toán**:

```java
int quantity = 100_000;
int unit = 100_000;
long total = (long) quantity * unit; // 10000000000
```

**Chọn `BigInteger`** khi kết quả có thể lớn hơn `Long.MAX_VALUE` hoặc nhỏ hơn `Long.MIN_VALUE`. Ví dụ:

```java
java.math.BigInteger result =
        java.math.BigInteger.valueOf(Long.MAX_VALUE)
                .add(java.math.BigInteger.ONE);
// result == 9223372036854775808.
```

`BigInteger` lưu số nguyên với độ chính xác tùy dung lượng bộ nhớ; phải dùng các phương thức như `add`, `subtract`, `multiply`, vì toán tử `+` và `*` không được nạp chồng cho đối tượng Java. Nếu chuyển ngược về `long` hoặc `int`, dùng `longValueExact()` / `intValueExact()` khi muốn phát hiện giá trị ngoài phạm vi.

**Dùng `Integer.toUnsignedString`, `Integer.toUnsignedLong`, `Integer.compareUnsigned`, `Long.toUnsignedString`** khi đặc tả đầu vào nói rõ dữ liệu là **mẫu bit unsigned** (ví dụ định dạng nhị phân hoặc giao thức). Các API này thay đổi cách **diễn giải, so sánh, chia hoặc hiển thị** mẫu bit, chứ không làm phép cộng `int` thông thường có khả năng chứa số có dấu lớn hơn. Không nên dùng unsigned chỉ nhằm “che” overflow của phép tính nghiệp vụ có dấu.

## 11. Dấu hiệu overflow trong chương trình thật

Hãy nghi ngờ overflow khi quan sát những hành vi như:

- Bộ đếm `int` đang tăng bỗng trở thành số âm hoặc số âm nhỏ nhất.
- Tổng hai số dương trả về số âm hoặc kết quả nhỏ hơn từng số hạng.
- Tích `quantity * price` khác kết quả kỳ vọng dù dữ liệu đầu vào đều hợp lệ.
- Biến `long` nhận kết quả âm từ biểu thức gồm **toàn `int`**.
- Một điều kiện phụ thuộc thứ tự tăng, ví dụ `next > current`, bất ngờ sai ở biên.
- `Math.abs` với giá trị nhỏ nhất vẫn trả về số âm.

Cần phân biệt với tình huống **thiết kế có chủ đích dựa vào modulo**, chẳng hạn một số hàm băm, thuật toán xử lý bit, bộ đếm vòng hoặc giao thức quy định số 32 bit. Khi modulo là yêu cầu thật sự, hãy ghi rõ độ rộng và mẫu bit mong muốn; khi tính toán chính xác là yêu cầu, hãy phát hiện overflow.

Trong kiểm thử, cần thử **ngay bên dưới biên, đúng tại biên và ngay sau phép toán gây vượt biên**. Ví dụ `Integer.MAX_VALUE - 1`, `Integer.MAX_VALUE`, `Integer.MAX_VALUE + 1`; đồng thời thử `Integer.MIN_VALUE`, phép đổi dấu, phép trừ 1, nhân số lớn và giá trị `Long.MAX_VALUE`. Không cần cố viết literal thập phân quá phạm vi để thử overflow: khi đó chỉ đang kiểm tra **lỗi biên dịch literal**, không kiểm tra hành vi chạy của phép toán.

## 12. Những nhầm lẫn phổ biến

| Nhầm lẫn | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| `int` tự ném lỗi khi cộng quá giới hạn | `+` thông thường dùng số nguyên độ rộng cố định và quay vòng | Dùng `Math.addExact` khi muốn phát hiện |
| `long x = a * b;` luôn an toàn nếu `x` là `long` | Nếu `a`, `b` là `int`, phép nhân có thể tràn ở `int` | `long x = (long) a * b;` |
| Ép `(long)` quanh biểu thức đã tràn sẽ sửa kết quả | Ép kiểu diễn ra sau phép toán bên trong | Ép một toán hạng từ trước |
| `long` không bao giờ overflow | `long` có độ rộng 64 bit hữu hạn | `Math.*Exact` hoặc `BigInteger` |
| `Math.abs(Integer.MIN_VALUE)` luôn không âm | Giá trị dương tương ứng vượt `int` | `Math.absExact`, `long` hoặc `BigInteger` theo yêu cầu |
| Chia cho `-1` luôn biểu diễn được | `MIN_VALUE / -1` vượt phạm vi nhưng trả lại `MIN_VALUE` | Kiểm tra trường hợp đặc biệt |
| `Integer.parseInt("2147483648")` sẽ quay vòng | Parse kiểm tra phạm vi và báo lỗi | Dùng `Long.parseLong` nếu phù hợp |
| Ép hẹp `(byte)128` giống lỗi cộng `int` | Đó là chuyển kiểu hẹp giữ bit thấp | Kiểm tra biên hoặc dùng API chuyển đổi chính xác |
| API unsigned làm `int` rộng hơn | API chỉ đổi cách đọc mẫu bit | Dùng kiểu đủ rộng để tính chính xác |

## 13. Câu hỏi tự kiểm tra

1. **`Integer.MAX_VALUE + 1` bằng gì?** `Integer.MIN_VALUE`, do mẫu 32 bit quay vòng sang bit dấu bằng 1.
2. **`Integer.MIN_VALUE - 1` bằng gì?** `Integer.MAX_VALUE`, do quay vòng qua biên dưới.
3. **`long total = 2_000_000_000 + 1_000_000_000;` bằng gì?** `-1_294_967_296L`, do phép cộng đã tràn ở `int` trước khi mở rộng.
4. **Làm sao nhận đúng `3_000_000_000`?** `2_000_000_000L + 1_000_000_000` hoặc chuyển một toán hạng sang `long` trước khi cộng.
5. **`long x = Long.MAX_VALUE + 1L;` bằng gì?** `Long.MIN_VALUE`; dùng `Math.addExact(Long.MAX_VALUE, 1L)` nếu cần ném lỗi.
6. **Có phải overflow nào cũng do toán tử `+` không?** Không: phép trừ, nhân, đổi dấu và trường hợp `MIN_VALUE / -1` đều có ranh giới cần xét.
7. **Một `int` có thể lưu chính xác `2_147_483_648` không?** Không; dùng `long`. Nếu yêu cầu giữ mẫu 32 bit như unsigned, phải thể hiện rõ cách diễn giải.
8. **Khi nào dùng `BigInteger`?** Khi dải kết quả yêu cầu vượt phạm vi `long` hoặc cần tính số nguyên tùy độ chính xác.

**Ghi nhớ:** trong Java, phép tính nguyên thông thường có thể **quay vòng modulo độ rộng bit mà không báo lỗi**. Để tránh sai số ngoài ý muốn, xác định kiểu của **từng toán hạng trước phép tính**, chọn `long` khi đủ phạm vi, dùng `Math.*Exact` hoặc kiểm tra biên khi cần phát hiện overflow, và dùng `BigInteger` nếu yêu cầu vượt cả `long`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-overflow-002">Vì sao phép cộng hoặc nhân tràn không tự ném ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao phép cộng hoặc nhân tràn không tự ném ngoại lệ trong Java?

Với các kiểu số nguyên nguyên thủy `int` và `long`, **phép cộng `+`, phép trừ `-` và phép nhân `*` thông thường không tự ném ngoại lệ khi kết quả vượt phạm vi kiểu**. Java quy định kết quả được giữ lại theo số bit của kiểu; những bit cao vượt quá độ rộng bị loại bỏ, và dãy bit còn lại được diễn giải như một số nguyên có dấu. Vì vậy, giá trị có thể “quay vòng” từ lớn nhất sang nhỏ nhất hoặc ngược lại.

Ví dụ:

```java
int max = Integer.MAX_VALUE;        //  2_147_483_647
int wrapped = max + 1;              // -2_147_483_648
long large = Long.MAX_VALUE + 1L;   // Long.MIN_VALUE
```

Ba dòng trên là mảnh mã hợp lệ bên trong một phương thức Java. Không có `ArithmeticException` ở các phép cộng này. Đây là **hành vi được Java định nghĩa**, không phải hiện tượng phụ thuộc ngẫu nhiên vào CPU hay JVM.

Bài học thuộc nhóm **Tràn và kiểm tra biên** của **Java core – numbers**. Trọng tâm là lý do Java không báo lỗi ở toán tử số học nguyên bình thường, cách tràn xảy ra, khi nào exception thực sự xuất hiện, và cách thiết kế phép tính phải đúng về mặt nghiệp vụ.

## 1. Miền giá trị và tính toán với số bit cố định

Java có các kiểu nguyên thủy mang giá trị nguyên với độ rộng xác định:

| Kiểu | Độ rộng | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | ---: | ---: | ---: |
| `byte` | 8 bit | -128 | 127 |
| `short` | 16 bit | -32.768 | 32.767 |
| `int` | 32 bit | -2.147.483.648 | 2.147.483.647 |
| `long` | 64 bit | -9.223.372.036.854.775.808 | 9.223.372.036.854.775.807 |

Các kiểu này có dấu. Số nguyên âm được biểu diễn theo **bù hai** (*two's complement*). Đối với phép cộng, trừ và nhân số nguyên, quy tắc giữ lại các bit thấp tương đương với thực hiện phép toán **theo modulo `2^32` đối với `int`**, hoặc **modulo `2^64` đối với `long`**, rồi diễn giải kết quả là số có dấu.

Có thể hình dung với 32 bit:

```text
Integer.MAX_VALUE  =  2_147_483_647
                   =  0x7FFF_FFFF

Cộng thêm 1:
                     0x7FFF_FFFF
                   + 0x0000_0001
                   = 0x8000_0000

0x8000_0000 dưới dạng int có dấu
                   = -2_147_483_648
                   = Integer.MIN_VALUE
```

Bit cao nhất là bit dấu trong biểu diễn bù hai. Sau khi cộng 1, dãy bit `1000...0000` biểu diễn số nguyên nhỏ nhất của `int`. Không có một “giá trị ngoài miền” được lưu trong biến `int`; phép toán đã tạo ra một kết quả **nằm trong miền bit của kiểu**, nhưng không còn bằng kết quả toán học mà người viết mong muốn.

### “Không có exception” là một phần của quy tắc ngôn ngữ

Java quy định hành vi tràn của các phép toán nguyên thông thường. Đây không phải tình trạng *undefined behavior* do trình biên dịch được quyền xử lý tùy ý. Cùng một biểu thức `Integer.MAX_VALUE + 1` sẽ có kết quả `Integer.MIN_VALUE` theo semantics Java.

Tuy nhiên, “hành vi xác định” **không đồng nghĩa** “kết quả có ý nghĩa với bài toán”. Một biến đếm số đơn hàng bất ngờ trở thành số âm vẫn là lỗi nghiêm trọng, dù JVM thực hiện đúng đặc tả.

## 2. Tại sao Java không kiểm tra tràn ở mỗi toán tử?

Về mặt thiết kế ngôn ngữ, các toán tử số học nguyên thông thường sử dụng quy tắc số nguyên độ rộng cố định. Phép toán không thực hiện phép kiểm tra giới hạn kèm theo để ném lỗi tràn. Việc tính toán theo dãy bit cố định tạo ra hành vi ổn định, tương thích với các phép toán bit và nhiều thuật toán làm việc với số nguyên.

Không nên diễn giải rằng “CPU không thể phát hiện tràn” hoặc “JVM luôn quá nhanh nên bỏ qua kiểm tra”. Một số kiến trúc phần cứng có cờ báo overflow, và Java có thư viện cho phép **yêu cầu kiểm tra tràn một cách tường minh**. Điểm quyết định là **ngữ nghĩa của toán tử Java `+`, `-`, `*` không yêu cầu phát sinh ngoại lệ khi tràn**, bất kể cách JVM biên dịch máy.

Do đó, hai hành vi sau cùng hợp lệ và có chủ đích:

```java
int a = Integer.MAX_VALUE;
int normal = a + 1;              // Trả Integer.MIN_VALUE.

int checked = Math.addExact(a, 1);
// Ném ArithmeticException vì kết quả toán học vượt miền int.
```

`Math.addExact` không thay đổi cơ chế của toán tử `+` trên toàn chương trình; nó là **một API khác** với hợp đồng phát hiện tràn. Người thiết kế phải chọn API hoặc mô hình dữ liệu phù hợp với ý nghĩa của phép tính.

## 3. Phép cộng, trừ và nhân đều có thể tràn mà không báo lỗi

### 3.1. Tràn khi cộng

```java
int next = Integer.MAX_VALUE + 1;
// next == Integer.MIN_VALUE

int belowMinimum = Integer.MIN_VALUE - 1;
// belowMinimum == Integer.MAX_VALUE
```

Ở ví dụ thứ hai là **tràn phía âm** (*underflow đối với miền số nguyên có dấu*), không phải hiện tượng underflow của số dấu phẩy động. Java không ném ngoại lệ cho phép trừ này.

### 3.2. Tràn khi nhân

Phép nhân có thể vượt miền ngay cả khi **cả hai toán hạng đều hợp lệ**:

```java
int records = 1_500_000_000;
int copies = 3;

int wrongTotal = records * copies; // 205_032_704
```

Kết quả toán học là `4_500_000_000`, nhưng `int` chỉ chứa được đến `2_147_483_647`. Vì:

```text
4_500_000_000 - 2^32
= 4_500_000_000 - 4_294_967_296
= 205_032_704
```

nên `wrongTotal` là `205_032_704`. Đây là ví dụ tràn số **mà kết quả vẫn dương và có vẻ hợp lý**. Không thể chỉ kiểm tra `result < 0` để phát hiện mọi phép nhân tràn.

Một ví dụ khác:

```java
int x = 50_000;
int square = x * x; // -1_794_967_296, không phải 2_500_000_000
```

Tràn có thể làm số dương thành âm hoặc thành một số dương sai. Cả hai đều có thể gây lỗi trong tính tiền, kích thước bộ đệm, tổng số lượng và kiểm tra giới hạn.

### 3.3. Đổi dấu và trường hợp số nguyên nhỏ nhất

Trong bù hai, miền âm của `int` có thêm một giá trị về độ lớn so với miền dương. Vì vậy:

```java
int min = Integer.MIN_VALUE;
int negated = -min;           // Vẫn là Integer.MIN_VALUE.
int absolute = Math.abs(min); // Vẫn là Integer.MIN_VALUE.
```

Giá trị toán học `2_147_483_648` không thể biểu diễn bằng `int`. Phép đổi dấu `-` thông thường không ném ngoại lệ. Khi phải phát hiện trường hợp này, dùng `Math.negateExact(min)` hoặc nâng kiểu trước khi đổi dấu:

```java
long positive = -(long) Integer.MIN_VALUE;
// 2_147_483_648L
```

Không nên áp dụng `Math.abs(int)` rồi mặc định kết quả không âm đối với mọi đầu vào.

## 4. Kiểu của biểu thức được xác định trước khi gán

Một bẫy đặc biệt phổ biến là nghĩ rằng gán kết quả vào biến `long` sẽ khiến mọi phép toán trước đó được tính như `long`. Java **xác định kiểu của biểu thức từ toán hạng trước khi phép tính xảy ra**.

```java
int amount = 1_500_000_000;

long tooLate = amount * 3;           // 205_032_704
long widenedFirst = (long) amount * 3; // 4_500_000_000
```

Ở `tooLate`, hai toán hạng của phép nhân là `int`, nên Java tính ra một kết quả `int` đã tràn; sau đó mới chuyển **kết quả sai** sang `long`. Biến `tooLate` lưu `205_032_704L`, không thể tự phục hồi `4_500_000_000L`.

Ở `widenedFirst`, phép ép `(long) amount` được thực hiện **trước phép nhân**. Một toán hạng `long` khiến phép toán được thực hiện ở `long`; trường hợp này nằm trong giới hạn 64 bit nên đúng.

Bẫy tương tự có thể xảy ra ngay với literal:

```java
long late = 2_000_000_000 * 2;     // -294_967_296L
long early = 2_000_000_000L * 2;  // 4_000_000_000L
```

Hậu tố `L` ở literal thay đổi **kiểu toán hạng**, không chỉ cách hiển thị. Nhưng `long` vẫn có giới hạn. Nếu kết quả toán học có thể vượt 64 bit, mở rộng từ `int` sang `long` chỉ trì hoãn vấn đề.

### Chú ý với byte, short, char và phép gán cộng

Trong biểu thức số học bình thường, các toán hạng `byte`, `short`, `char` thường được nâng lên `int`. Sau đó, phép gán hoặc ép kiểu trở lại kiểu nhỏ có thể loại bỏ bit cao:

```java
byte b = 127;
b++;                 // b trở thành -128 (ép về byte sau phép tăng).
// b = b + 1;        // Không biên dịch: biểu thức b + 1 có kiểu int.
b += 1;              // Hợp lệ: compound assignment có chuyển kiểu ngầm.
```

Đây là lý do `byte b = 127; b++;` quay về `-128` dù phép tính trung gian có thể được thực hiện ở `int`. Không có exception tự động khi kết quả bị thu hẹp trở lại.

### Chuỗi phép tính có thể tràn ở giữa

```java
int a = 1_500_000_000;
int b = 1_500_000_000;

long wrong = (a + b) / 2;        // Phép cộng int tràn trước.
long right = ((long) a + b) / 2; // Cộng long trước rồi chia.
```

Đừng chỉ nhìn giá trị đầu vào và kết quả cuối. Hãy kiểm tra **từng phép tính trung gian**: mỗi biểu thức con có kiểu gì, giới hạn nào và khi nào chuyển kiểu.

## 5. Exception nào thật sự có thể xảy ra trong số học nguyên?

Cần phân biệt **tràn số nguyên thông thường** với **chia cho 0** hoặc với lời gọi API kiểm tra.

| Phép toán | Khi vượt phạm vi hoặc gặp biên | Có tự ném `ArithmeticException` không? |
| --- | --- | --- |
| `int + int`, `long + long` | Tràn theo độ rộng kiểu | **Không** |
| `int - int`, `long - long` | Tràn theo độ rộng kiểu | **Không** |
| `int * int`, `long * long` | Tràn theo độ rộng kiểu | **Không** |
| `-Integer.MIN_VALUE` | Trả lại `Integer.MIN_VALUE` | **Không** |
| `Integer.MIN_VALUE / -1` | Thương nguyên tràn, trả `Integer.MIN_VALUE` | **Không** |
| `Integer.MIN_VALUE % -1` | Phần dư bằng `0` | **Không** |
| `a / 0` hoặc `a % 0` trên kiểu nguyên | Số chia bằng 0 | **Có**, khi được đánh giá |
| `Math.addExact`/`multiplyExact` | Kết quả toán học ngoài miền kiểu trả về | **Có** |
| `Math.toIntExact(long)` | Giá trị `long` ngoài miền `int` | **Có** |

**Trường hợp đặc biệt của phép chia:** phép chia `Integer.MIN_VALUE / -1` có thương toán học là `2_147_483_648`, vượt `int`, nhưng phép chia nguyên Java vẫn trả `Integer.MIN_VALUE` và không ném ngoại lệ; `Math.floorDiv` không phải một công cụ kiểm tra tràn. Nếu cần thương dương chính xác, nâng lên `long` **trước khi chia**:

```java
long exactQuotient = (long) Integer.MIN_VALUE / -1;
// 2_147_483_648L
```

Ngoài ra, số dấu phẩy động `double`/`float` có quy tắc khác: phép toán có thể tạo `Infinity` hoặc `NaN`; không thể áp dụng các kết luận tràn số nguyên từ bài này trực tiếp sang floating point.

## 6. Khi cần báo lỗi thay vì để toán tử quay vòng

Vì `+`, `-`, `*` nguyên thủy không tự kiểm tra overflow, ứng dụng cần chọn phương thức `Math.addExact`, `Math.subtractExact` hoặc `Math.multiplyExact` nếu muốn phép tính vượt miền ném `ArithmeticException`. Chẳng hạn `Math.multiplyExact(50_000, 50_000)` ném khi kết quả không vừa `int`, còn `Math.multiplyExact(50_000L, 50_000)` thành công với tích `long` 2.500.000.000.

Cần đặt kiểm tra **ở chính phép toán gây tràn**. Nếu tính `a * b` trước rồi truyền kết quả đã tràn vào `addExact`, API không phát hiện được lỗi trước đó. Các overload, trường hợp sát biên và chính sách xử lý exception được thực hành sâu trong bài riêng về ba phương thức `Exact`. Ở đây điều cốt lõi là phân biệt hợp đồng toán tử thường với API có kiểm tra, đồng thời kiểm tra miền nghiệp vụ riêng.

## 7. Khi kết quả cần vượt cả `long`

Nếu miền dữ liệu không thể giới hạn trong 64 bit, có thể chuyển **toán hạng** sang `BigInteger` trước khi cộng hoặc nhân: `BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)` cho chính xác 9223372036854775808. Ngược lại, `BigInteger.valueOf(Long.MAX_VALUE + 1)` giữ kết quả `long` đã tràn. `BigInteger` dùng các phương thức `add`, `multiply` và bị giới hạn bởi tài nguyên thực tế; bài cuối chương phân tích chi tiết việc lựa chọn, chuyển đổi và kiểm soát chi phí.

## 8. Chương trình Java 17+ chạy độc lập

Lưu chương trình sau thành **`OverflowDemo.java`**. Nó cho thấy phép cộng/nhân thông thường quay vòng, việc gán vào `long` quá muộn, checked arithmetic ném exception có chủ đích, phép đổi dấu ở giá trị nhỏ nhất, và cách `BigInteger` giữ được giá trị vượt `long`.

```java
import java.math.BigInteger;

public class OverflowDemo {
    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;
        System.out.println("int-wrap=" + (max + 1));
        System.out.println("long-wrap=" + (Long.MAX_VALUE + 1L));

        int quantity = 1_500_000_000;
        long late = quantity * 3;
        long early = (long) quantity * 3;
        System.out.println("multiply-late=" + late);
        System.out.println("multiply-early=" + early);

        try {
            Math.addExact(max, 1);
        } catch (ArithmeticException ex) {
            System.out.println("add-exact=overflow");
        }

        try {
            Math.multiplyExact(quantity, 3);
        } catch (ArithmeticException ex) {
            System.out.println("multiply-exact=overflow");
        }

        System.out.println("checked-wide=" +
                Math.multiplyExact((long) quantity, 3L));

        byte small = 127;
        small++;
        System.out.println("byte-wrap=" + small);

        System.out.println("negate-min=" + (-Integer.MIN_VALUE));
        System.out.println("abs-min=" + Math.abs(Integer.MIN_VALUE));
        System.out.println("min-div=" + (Integer.MIN_VALUE / -1));
        System.out.println("min-rem=" + (Integer.MIN_VALUE % -1));
        System.out.println("wide-div=" + ((long) Integer.MIN_VALUE / -1));

        try {
            Math.toIntExact(2_147_483_648L);
        } catch (ArithmeticException ex) {
            System.out.println("to-int-exact=overflow");
        }

        BigInteger big = BigInteger.valueOf(Long.MAX_VALUE)
                .add(BigInteger.ONE);
        System.out.println("big-integer=" + big);
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac OverflowDemo.java
java OverflowDemo
```

Stdout:

```text
int-wrap=-2147483648
long-wrap=-9223372036854775808
multiply-late=205032704
multiply-early=4500000000
add-exact=overflow
multiply-exact=overflow
checked-wide=4500000000
byte-wrap=-128
negate-min=-2147483648
abs-min=-2147483648
min-div=-2147483648
min-rem=0
wide-div=2147483648
to-int-exact=overflow
big-integer=9223372036854775808
```

Các phép toán tràn đầu tiên **không vào `catch`** vì không có exception để bắt. Chỉ các lời gọi có hợp đồng checked như `Math.addExact`, `Math.multiplyExact`, `Math.toIntExact` ném `ArithmeticException` ở những giá trị đã chọn. Một phép tính `long` phù hợp miền vẫn chạy được qua `Math.multiplyExact`.

## 9. Bẫy khi lập trình với dữ liệu thực

### 9.1. Kích thước cần cấp phát

Giả sử tính dung lượng `count * bytesPerRecord`. Nếu phép nhân `int` tràn và trở thành số âm/nhỏ hơn dự kiến, quá trình kiểm tra kích thước có thể bị vượt qua hoặc cấp phát sai. Trước khi tạo mảng, cần xác minh miền dữ liệu, tính kích thước bằng cách an toàn, và kiểm tra có thể biểu diễn bằng chỉ số mảng Java hay không.

```java
static int checkedArrayBytes(int count, int bytesPerItem) {
    if (count < 0 || bytesPerItem < 0) {
        throw new IllegalArgumentException("Values must be nonnegative");
    }
    return Math.multiplyExact(count, bytesPerItem);
}
```

Đây là một hàm minh họa: nó phát hiện tràn `int` và đầu vào âm. Việc mảng có thực sự cấp phát được hay không còn phụ thuộc giới hạn JVM/bộ nhớ và API nhận kích thước; kiểm tra tràn chỉ là **một điều kiện cần**, không bảo đảm cấp phát thành công.

### 9.2. Tính tiền bằng số nguyên đơn vị nhỏ nhất

Nếu lưu số cent trong `long`, việc tính `unitPriceCents * quantity` cần checked multiply khi dữ liệu có thể rất lớn:

```java
static long totalCents(long priceCents, int quantity) {
    if (priceCents < 0 || quantity < 0) {
        throw new IllegalArgumentException("Negative price/quantity");
    }
    return Math.multiplyExact(priceCents, (long) quantity);
}
```

Ví dụ giả định giá và số lượng không âm. Bài toán thực tế có thể có hoàn tiền hay điều chỉnh âm, nên ràng buộc miền phải do nghiệp vụ quyết định. Nếu giá trị vượt `long`, cân nhắc `BigInteger` hoặc cấu trúc nghiệp vụ khác; nếu dùng số thập phân, `BigDecimal` là một hướng thiết kế riêng.

### 9.3. Bộ đếm tăng liên tục

```java
int count = Integer.MAX_VALUE;
count++; // Trở thành Integer.MIN_VALUE.
```

Nếu biến là bộ đếm yêu cầu không giảm, việc quay vòng vi phạm bất biến. Dùng `Math.incrementExact(count)` để phát hiện, hoặc quy định bộ đếm thực sự dùng phép quay vòng và cách so sánh sau khi quay vòng.

Ngay cả `AtomicInteger.incrementAndGet()` cũng không mặc định ném ngoại lệ khi tràn: tính nguyên tử và **phát hiện tràn** là hai yêu cầu khác nhau. Với nhu cầu cập nhật nguyên tử **kèm phát hiện tràn**, có thể dùng hàm cập nhật với `Math.incrementExact` và kiểm thử quy tắc concurrency riêng.

### 9.4. Validation sau khi tràn có thể quá muộn

Một biểu thức như:

```java
int total = unitCount * unitSize;
if (total <= limit) {
    // ...
}
```

không chứng minh kích thước hợp lệ nếu `unitCount * unitSize` đã tràn. Việc so sánh có thể dựa trên kết quả sai. Cần xác thực đầu vào, chọn kiểu đủ lớn hoặc checked arithmetic **trước khi** dùng kết quả cho kiểm soát giới hạn.

## 10. Nhầm lẫn thường gặp

| Nhận định sai | Giải thích đúng |
| --- | --- |
| “Tràn `int` sẽ ném `ArithmeticException`.” | Các toán tử nguyên thông thường quay vòng theo số bit, không ném ngoại lệ do tràn. |
| “Java luôn bảo vệ số học nên không thể mất giá trị.” | An toàn bộ nhớ/kiểm tra chỉ số không thay thế kiểm tra kết quả toán học. |
| “Kết quả âm là cách phát hiện overflow.” | Nhân/cộng có thể tràn thành kết quả dương trông hợp lý. |
| “Gán sang `long` thì phép nhân sẽ là `long`.” | Kiểu của phép nhân dựa trên toán hạng; chuyển sau phép nhân có thể quá muộn. |
| “Cast sau phép tính sửa được kết quả.” | Giá trị đã tràn không thể khôi phục bằng cast. |
| “Dùng `long` là hết tràn.” | `long` cũng có biên ±9,22×10¹⁸; phép nhân có thể vượt biên. |
| “`Math.abs(x)` luôn không âm.” | `Math.abs(Integer.MIN_VALUE)` và tương đương `long` có ca biên tràn. |
| “`BigInteger.valueOf(a * b)` bảo vệ được phép nhân.” | Phải chuyển toán hạng trước khi thực hiện phép tính. |
| “`Math.addExact` xác minh mọi điều kiện nghiệp vụ.” | Nó chỉ phát hiện vượt miền kiểu; phải kiểm tra giới hạn và đơn vị riêng. |
| “Mọi `ArithmeticException` đều là overflow.” | Chia nguyên cho 0 cũng ném `ArithmeticException`; loại lỗi cần xét theo thao tác. |

### Tại sao không bật một cờ để mọi phép toán tự ném lỗi?

Trong Java thông thường, **không có chế độ ngôn ngữ mặc định** khiến toàn bộ toán tử `+`/`-`/`*` nguyên chuyển thành phép toán checked. Có thể chọn các phương thức `Math.*Exact`, xây abstraction, dùng static analysis hoặc thiết kế kiểu miền thích hợp. Khi yêu cầu đúng đắn đòi phát hiện overflow, cần đưa quyết định ấy vào code và test ở những điểm tính toán tương ứng.

## 11. Kiểm thử biên và checklist thiết kế

Một phép cộng/nhân trông đúng với số nhỏ chưa chứng minh được tính đúng đắn. Nên kiểm thử những vùng sau:

| Trường hợp | Điều cần xác minh |
| --- | --- |
| `Integer.MAX_VALUE + 1` | Phép `+` bình thường quay vòng; `Math.addExact` ném |
| `Integer.MIN_VALUE - 1` | Phép trừ bình thường quay vòng |
| `50_000 * 50_000` ở `int` | Nhân tràn dù toán hạng có vẻ nhỏ |
| `1_500_000_000 * 3` | Nhân tràn thành số dương sai |
| `(long) a * b` với kết quả nằm trong `long` | Nâng kiểu đúng thời điểm |
| `Long.MAX_VALUE + 1L` | `long` cũng có thể tràn |
| `-Integer.MIN_VALUE` và `Math.abs(Integer.MIN_VALUE)` | Ca biên đổi dấu không có số dương đối xứng |
| `Integer.MIN_VALUE / -1` | Thương tràn không ném, phần dư 0 |
| `a / 0`, `a % 0` | Phép chia nguyên cho 0 ném ngoại lệ |
| `Math.toIntExact((long) Integer.MAX_VALUE + 1)` | Thu hẹp có kiểm tra phải ném |
| `BigInteger.valueOf(a).multiply(BigInteger.valueOf(b))` | Chuyển toán hạng trước phép tính để giữ kết quả lớn |

**Checklist trước khi triển khai:**

1. Xác định miền đầu vào, giá trị lớn nhất/nhỏ nhất và các giá trị trung gian của từng biểu thức.
2. Quyết định overflow có được phép quay vòng theo thuật toán hay phải coi là lỗi.
3. Nếu phải phát hiện, sử dụng `Math.*Exact` hoặc phép tính trên `BigInteger` trước khi mất thông tin.
4. Kiểm soát chuyển kiểu: `(long) a * b` khác với `(long) (a * b)`.
5. Tách kiểm tra tràn khỏi kiểm tra nghiệp vụ như không âm, giới hạn cấp phát, đơn vị dữ liệu và quyền truy cập.
6. Thêm test tại `MIN_VALUE`, `MAX_VALUE`, tích gần giới hạn, và cả trường hợp tràn thành số **dương**.
7. Không dùng exception làm luồng điều khiển chính của dữ liệu bình thường nếu miền dữ liệu có thể xác định hợp lệ trước; chọn API và chính sách lỗi rõ ràng.

## 12. Câu hỏi tự luyện

1. Giải thích bằng bit vì sao `Integer.MAX_VALUE + 1` cho `Integer.MIN_VALUE`. Tại sao JVM không phải ném ngoại lệ?
2. Dự đoán `int x = 50_000 * 50_000;` và giải thích bằng cách lấy kết quả toán học theo modulo `2^32`.
3. Phân biệt `long a = 1_500_000_000 * 3;` và `long b = 1_500_000_000L * 3;`. Phép toán nào tính với `int`?
4. Viết `checkedTotalPriceCents(long unitPriceCents, int quantity)` có validation miền phù hợp và báo lỗi nếu phép nhân vượt `long`.
5. Vì sao phép `Math.abs(Integer.MIN_VALUE)` trả giá trị âm? Có hai cách nào để xử lý ý định lấy trị tuyệt đối: báo lỗi hoặc mở rộng kiểu?
6. Một ứng dụng sinh ID tuần tự bằng `AtomicInteger.incrementAndGet()`. Tính nguyên tử có đủ bảo đảm ID không trở thành âm ở biên `MAX_VALUE` không? Thiết kế cách kiểm tra.
7. Vì sao `BigInteger.valueOf(Long.MAX_VALUE * 2)` không cho kết quả đúng? Hãy viết cách tính giữ nguyên ý định.
8. Nêu khác biệt giữa overflow integer thông thường, chia số nguyên cho 0, và overflow được phát hiện bởi `Math.multiplyExact`: thao tác nào phát sinh exception, thao tác nào không?

## 13. Ghi nhớ

- **`+`, `-`, `*` trên số nguyên Java thông thường không ném ngoại lệ khi tràn**; kết quả được xác định theo độ rộng cố định của kiểu.
- Kết quả quay vòng có thể **âm hoặc dương**, nên dấu của kết quả không phải cách phát hiện tràn đáng tin cậy.
- **`Math.*Exact`** thực hiện phép toán hoặc chuyển kiểu với kiểm tra tràn và ném `ArithmeticException` khi không biểu diễn được.
- **Nâng kiểu trước phép toán**, không chỉ gán/ép kiểu sau khi kết quả đã bị tính sai.
- **`long` vẫn có giới hạn**; cần `BigInteger` khi yêu cầu toán học không nằm trong miền cố định.
- **Validation nghiệp vụ** và **checked arithmetic** bổ sung cho nhau: phép tính vừa phải đúng trong kiểu vừa phải có ý nghĩa trong miền dữ liệu.

Lý do Java không tự ném ngoại lệ khi cộng hoặc nhân tràn là vì **đó chính là quy tắc được định nghĩa cho các toán tử số học nguyên thông thường**. Khi một hệ thống không cho phép mất thông tin số học, người viết phải chủ động dùng kiểm tra tràn, kiểu đủ rộng và kiểm thử biên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-overflow-003">Kiểu của phép tính được xác định trước khi gán kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu của phép tính được xác định trước khi gán kết quả trong Java

## 1. Quy tắc quan trọng nhất

Trong Java, **kiểu của biểu thức và phép toán được xác định theo các toán hạng và quy tắc ngôn ngữ trước khi kết quả được gán cho biến đích**. Một biến đích có kiểu rộng hơn không thể làm phép tính đã xảy ra ở kiểu hẹp trở nên chính xác trở lại.

Ví dụ:

```java
int a = 50_000;
int b = 50_000;

long wrong = a * b;          // -1794967296, phép nhân được thực hiện bằng int.
long correct = (long) a * b; // 2500000000, phép nhân được thực hiện bằng long.
```

Về toán học, `50_000 × 50_000 = 2_500_000_000`. Giá trị này vượt `Integer.MAX_VALUE = 2_147_483_647` nhưng nằm trong miền `long`. Với `wrong`, phép nhân giữa **hai biến `int`** tạo ra kết quả `int` bị tràn rồi kết quả đó mới được chuyển sang `long` để gán. Với `correct`, một toán hạng được chuyển sang `long` **trước** khi nhân nên toàn bộ phép nhân dùng `long`.

Đừng nhầm hai câu hỏi:

1. **Phép toán diễn ra bằng kiểu nào?** Phụ thuộc kiểu của các toán hạng, toán tử và các quy tắc *numeric promotion*.
2. **Kết quả được lưu vào biến kiểu nào?** Phụ thuộc kiểu biến nhận và quy tắc chuyển đổi trong ngữ cảnh gán.

Kiểu của biến nhận chỉ được xét sau khi biểu thức bên phải có kết quả; nó **không mặc nhiên truyền ngược vào phép toán nhị phân đã được định kiểu**.

## 2. Từng bước Java đánh giá một phép gán số

Xét:

```java
int x = 50_000;
int y = 50_000;
long result = x * y;
```

Có thể hiểu tuần tự:

1. Trình biên dịch thấy `x` và `y` đều có kiểu `int`.
2. Toán tử `*` áp dụng *binary numeric promotion*: hai toán hạng vẫn là `int`, nên **kiểu biểu thức `x * y` là `int`**.
3. Khi thực thi, phép nhân `int` vượt giới hạn và giữ kết quả theo số học nguyên 32 bit của Java: `-1_794_967_296`.
4. Khi gán cho `result`, Java thực hiện chuyển đổi mở rộng `int → long`, giữ nguyên giá trị âm đã có.
5. Giá trị cuối cùng của `result` là `-1_794_967_296L`.

Với biểu thức `(long) x * y`, toán hạng trái được chuyển sang `long` ngay từ đầu. *Binary numeric promotion* chuyển toán hạng `y` sang `long`, do đó biểu thức nhân có kiểu `long` và nhận đúng `2_500_000_000L`.

**Điều chỉnh kiểu phải xảy ra trước phép toán có nguy cơ mất dữ liệu.**

## 3. Binary numeric promotion: quy tắc chọn kiểu cho phép toán

Các toán tử số nhị phân như `+`, `-`, `*`, `/`, `%` áp dụng quy tắc *binary numeric promotion*. Với toán hạng thuộc các kiểu số nguyên và số thực nguyên thủy, có thể đọc theo thứ tự ưu tiên:

| Kiểu xuất hiện trong hai toán hạng | Kiểu thường dùng để thực hiện phép toán |
| --- | --- |
| Có `double` | `double` |
| Không có `double`, có `float` | `float` |
| Không có `double`/`float`, có `long` | `long` |
| Còn lại: `byte`, `short`, `char`, `int` | `int` |

Ví dụ:

```java
byte small = 10;
short other = 20;
int sum = small + other;  // int, vì byte và short được nâng lên int.

int units = 7;
long multiplier = 3L;
long product = units * multiplier; // long.

float f = 1.5F;
int n = 2;
float floatingResult = f * n; // float.

double d = 1.25;
float another = 2.0F;
double wideFloating = d + another; // double.
```

**Hai chi tiết cần nhớ:**

- `char` cũng thuộc nhóm *integral* và được nâng lên `int` khi tham gia phần lớn phép số học thông thường, dù giá trị của nó không dấu.
- `boolean` không phải kiểu số, không tham gia *numeric promotion*. Phép `"total=" + 42` là **nối chuỗi** bằng `+`, là ngữ nghĩa khác với cộng hai số.

Bảng này mô tả phép toán số học thường gặp. Không nên áp dụng máy móc cho mọi toán tử: phép dịch bit `<<`, `>>`, `>>>` dùng **unary numeric promotion** cho từng toán hạng, và kiểu kết quả phụ thuộc kiểu đã nâng của toán hạng **bên trái**; phép so sánh số trả về `boolean`; toán tử điều kiện `?:` có những quy tắc định kiểu riêng. Điểm chung là từng biểu thức phải được phân tích theo toán tử của chính nó, thay vì dựa vào kiểu biến nhận cuối cùng.

## 4. Tràn số xảy ra trước khi mở rộng kiểu của kết quả

### 4.1. Phép cộng `int` vào `long`

```java
int max = Integer.MAX_VALUE;

long tooLate = max + 1;        // -2147483648
long onTime = (long) max + 1;  // 2147483648
long withLongLiteral = max + 1L; // 2147483648
```

Bên phải `max + 1` là phép cộng `int + int`; nó tràn tại `2_147_483_647 + 1`. Sau đó, chuyển sang `long` chỉ giữ lại giá trị âm đã tràn. Thêm `(long)` **vào toán hạng** hoặc dùng `1L` làm phép cộng diễn ra ở `long` ngay từ đầu.

### 4.2. Phép nhân với cast đặt sai vị trí

So sánh:

```java
int width = 50_000;
int height = 50_000;

long a = (long) (width * height); // Sai mục đích: int nhân trước, rồi cast.
long b = ((long) width) * height; // Đúng: long nhân từ đầu.
```

Dấu ngoặc trong `(long) (width * height)` khiến phép nhân diễn ra trước khi cast. Muốn tăng miền của phép toán, cần cast **một toán hạng**, không phải chỉ cast **kết quả biểu thức đã tính**.

### 4.3. Nâng lên `long` vẫn có giới hạn

Nếu kết quả thực sự vượt miền `long`, chỉ dùng `long` vẫn có thể tràn:

```java
long max = Long.MAX_VALUE;
long overflowed = max + 1L; // Long.MIN_VALUE.
```

Đừng dùng `double` như một bản thay thế mặc định cho số nguyên lớn cần tính chính xác: dù miền độ lớn của `double` lớn hơn `long`, nó có độ chính xác hữu hạn và có thể làm tròn các số nguyên lớn. Khi cần nguyên chính xác vượt 64 bit, dùng `BigInteger` và **chuyển từng toán hạng trước khi thực hiện phép tính lớn**.

## 5. Kiểu biểu thức còn quyết định phép chia nguyên và mất phần thập phân

Không chỉ tràn số: cùng nguyên tắc đánh giá trước khi gán còn gây ra kết quả khác khi chia.

```java
int numerator = 1;
int denominator = 2;

double wrong = numerator / denominator;           // 0.0
double correct = (double) numerator / denominator; // 0.5
double alsoCorrect = numerator / 2.0;             // 0.5
```

Trong `numerator / denominator`, cả hai toán hạng là `int`, nên Java **chia nguyên**, bỏ phần lẻ và nhận `0`; rồi mới mở rộng giá trị đó thành `0.0` khi gán cho `double`.

Với `(double) numerator / denominator`, phép chia đã chuyển sang kiểu `double` trước khi thực hiện nên kết quả là `0.5`. Điều này khác hẳn việc dùng `(double) (numerator / denominator)`, vì cast sau phép chia chỉ biến `0` thành `0.0`.

Tương tự, nếu bạn viết `float result = 1 / 2;`, kết quả là `0.0F`, không phải `0.5F`.

**Lưu ý:** phép chia số nguyên có mẫu bằng 0 gây `ArithmeticException`. Gán kết quả vào `double` không thể đổi ngoại lệ đã xảy ra trong phép chia `int / int` thành `Infinity`.

## 6. Các kiểu hẹp được nâng lên `int` dù biến nhận là `byte` hoặc `short`

Nhiều người nghĩ phép cộng hai `byte` tạo ra `byte`. Thực tế, khi dùng toán tử cộng thông thường, Java nâng chúng lên `int`:

```java
byte x = 10;
byte y = 20;
int sum = x + y; // Đúng: kiểu biểu thức là int.

// byte invalid = x + y; // Lỗi biên dịch: int không tự thu hẹp thành byte.
byte narrowed = (byte) (x + y); // Ép kiểu tường minh.
```

Cast có thể mất dữ liệu khi giá trị vượt miền `byte` ([-128, 127]):

```java
byte x = 100;
byte y = 30;
int exact = x + y;             // 130
byte narrowed = (byte) (x + y); // -126
```

Giá trị `130` không nằm trong miền `byte`; ép kiểu về `byte` giữ các bit thấp và diễn giải kết quả có dấu thành `-126`. Lưu kết quả vào `int` giữ được đúng `130`.

### Ngoại lệ: gán một biểu thức hằng khi biên dịch

Java cho phép một số phép gán từ **biểu thức hằng** kiểu `int` (và trong trường hợp quy định, kiểu hằng khác) sang `byte`/`short`/`char` mà không cần cast nếu giá trị hằng biểu diễn được trong kiểu đích:

```java
byte a = 10 + 20;      // Hợp lệ: biểu thức hằng int có giá trị 30.
byte b = 127;          // Hợp lệ: giá trị hằng trong miền byte.
// byte c = 100 + 30; // Lỗi: giá trị hằng 130 vượt miền byte.

int external = 20;
// byte d = 10 + external; // Lỗi: kết quả int, không phải biểu thức hằng.
```

Đây là **quy tắc chuyển đổi gán cho biểu thức hằng nằm trong miền**, không có nghĩa phép cộng `byte` bỗng được thực hiện bằng `byte`. Khi các toán hạng là biến thông thường và kết quả phụ thuộc runtime, đừng dựa vào ngoại lệ này.

## 7. Toán tử gán ghép và tăng/giảm: trường hợp cần đặc biệt chú ý

Các toán tử `+=`, `-=`, `*=`, `/=` và `%=` có quy tắc riêng, gồm **chuyển kiểu ngầm về kiểu bên trái**. Chẳng hạn `E1 += E2` về cơ bản tương đương với `E1 = (T) ((E1) + (E2))`, với `T` là kiểu của `E1`; tuy nhiên `E1` chỉ được đánh giá một lần.

```java
byte count = 100;

// count = count + 30; // Lỗi biên dịch: kết quả count + 30 là int.
count += 30;           // Hợp lệ: cộng dạng int, rồi ép hẹp lại byte.
System.out.println(count); // -126
```

Do đó, `count += 30` **không an toàn hơn** `count = (byte) (count + 30)` về mặt mất dữ liệu. Cú pháp ngắn hơn có thể che việc ép kiểu hẹp diễn ra âm thầm.

Các toán tử `++`, `--` cũng có quy tắc kiểu riêng và có thể áp dụng trên biến `byte`/`short`/`char`. Ví dụ, khi `byte value = 127; value++;`, biến `value` trở thành `-128` do vượt giới hạn. Đừng suy từ việc `value++` biên dịch được rằng `byte next = value + 1;` cũng phải biên dịch được.

## 8. Biểu thức con và thứ tự tính: một cast không cứu được toàn bộ biểu thức

Java định kiểu và đánh giá các biểu thức con theo cấu trúc cú pháp. Nếu một biểu thức con đã bị tràn ở `int`, chuyển một biểu thức *khác* sang `long` về sau không hồi phục dữ liệu.

```java
int a = 50_000, b = 50_000;
long factor = 2L;

long wrong = (a * b) * factor; // -3589934592
long correct = ((long) a * b) * factor; // 5000000000
```

Trong `(a * b) * factor`, `a * b` đã cho `-1_794_967_296` ở kiểu `int`. Khi nhân tiếp với `factor`, Java mới chuyển kết quả âm này sang `long`. Trong `correct`, phép nhân đầu dùng `long`, nên kết quả được giữ chính xác trước phép nhân tiếp theo.

Tương tự, `(long) (a * b)` cũng quá muộn; cần `(long) a * b` hoặc `a * (long) b` để mở rộng **trước phép nhân đầu**.

Với biểu thức phức tạp, nên tách các bước thành biến trung gian có kiểu rõ ràng:

```java
long area = (long) width * height;
long volume = area * depth;
```

Hai dòng trên giả định `width`, `height` và `depth` đã được khai báo; đó là đoạn minh họa, không phải chương trình độc lập. Cách tách này giúp xác định phép toán nào có thể vượt `long` và cần kiểm tra thêm.

## 9. Cách phát hiện tràn và giữ số nguyên chính xác

### 9.1. Dùng kiểu rộng phù hợp từ lúc thực hiện phép toán

Nếu chứng minh được kết quả nằm trong miền `long`, hãy **nâng một toán hạng** hoặc sử dụng literal `long` trước phép cộng/nhân:

```java
int quantity = 1_000_000;
int unitPrice = 25_000;
long total = (long) quantity * unitPrice; // 25000000000
```

Tất cả giá trị `int` đều chuyển chính xác sang `long`, nhưng **mỗi phép toán** vẫn cần kiểm soát phạm vi kết quả. Đặc biệt, `long` không phải kiểu nguyên không giới hạn.

### 9.2. Dùng phép toán kiểm tra tràn khi tràn là lỗi

Các hàm `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`, `Math.negateExact` và `Math.toIntExact` có thể phát hiện những phép toán/chuyển đổi được chúng hỗ trợ không biểu diễn được kết quả. Khi tràn, chúng ném `ArithmeticException`.

```java
int a = 50_000, b = 50_000;

try {
    int product = Math.multiplyExact(a, b);
    System.out.println(product);
} catch (ArithmeticException ex) {
    System.out.println("Phép nhân int vượt miền giá trị");
}
```

Không phải mọi biểu thức nguyên đều tự ném ngoại lệ khi tràn. Chẳng hạn `a * b` thông thường chỉ *wrap around*; muốn phát hiện cần dùng API kiểm tra hoặc kiểm tra biên trước.

Lưu ý `Math.multiplyExact((long) a, b)` trả về `long` và cũng kiểm tra tràn đối với kiểu `long`. Nếu phép tính nằm trong miền `long`, dùng `long` ngay từ đầu là đủ.

### 9.3. Dùng `BigInteger` khi 64 bit không đủ

`BigInteger` biểu diễn số nguyên độ chính xác tùy ý trong giới hạn bộ nhớ/tài nguyên, với các phép toán qua phương thức:

```java
import java.math.BigInteger;

BigInteger x = BigInteger.valueOf(Long.MAX_VALUE);
BigInteger y = x.add(BigInteger.ONE);
System.out.println(y); // 9223372036854775808
```

Quan trọng là chuyển **toán hạng** trước khi tính:

```java
long max = Long.MAX_VALUE;
BigInteger correct = BigInteger.valueOf(max).add(BigInteger.ONE);
// BigInteger tooLate = BigInteger.valueOf(max + 1); // max + 1 đã tràn long.
```

`BigInteger` là lớp bất biến: `add`/`multiply` trả đối tượng kết quả mới. Không dùng các toán tử `+`, `*` để cộng/nhân hai đối tượng `BigInteger`.

## 10. Ví dụ Java 17+ chạy độc lập

Tạo tệp **`ExpressionTypeBeforeAssignment.java`**:

```java
import java.math.BigInteger;

public class ExpressionTypeBeforeAssignment {
    public static void main(String[] args) {
        int a = 50_000;
        int b = 50_000;

        long tooLate = a * b;
        long widenedBeforeMultiply = (long) a * b;
        long castAfterMultiply = (long) (a * b);

        System.out.println("int product then long = " + tooLate);
        System.out.println("long before multiply = " + widenedBeforeMultiply);
        System.out.println("cast after multiply = " + castAfterMultiply);

        int max = Integer.MAX_VALUE;
        long sumTooLate = max + 1;
        long sumCorrect = max + 1L;
        System.out.println("int sum then long = " + sumTooLate);
        System.out.println("long sum = " + sumCorrect);

        double dividedAsInt = 1 / 2;
        double dividedAsDouble = 1 / 2.0;
        System.out.println("int division then double = " + dividedAsInt);
        System.out.println("double division = " + dividedAsDouble);

        byte count = 100;
        count += 30;
        System.out.println("byte compound assignment = " + count);

        try {
            Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            System.out.println("checked int multiplication overflow");
        }

        BigInteger precise = BigInteger.valueOf(a)
                .multiply(BigInteger.valueOf(b));
        System.out.println("BigInteger product = " + precise);
    }
}
```

Biên dịch và chạy:

```bash
javac ExpressionTypeBeforeAssignment.java
java ExpressionTypeBeforeAssignment
```

Kết quả:

```text
int product then long = -1794967296
long before multiply = 2500000000
cast after multiply = -1794967296
int sum then long = -2147483648
long sum = 2147483648
int division then double = 0.0
double division = 0.5
byte compound assignment = -126
checked int multiplication overflow
BigInteger product = 2500000000
```

Đây là chương trình đầy đủ, không cần thư viện bên ngoài JDK. Phần `tooLate`/`castAfterMultiply` chỉ khác vị trí chuyển kiểu so với `widenedBeforeMultiply` nhưng kết quả khác hẳn. Hai phép chia cũng chứng minh rõ: kiểu đích `double` không biến phép chia `int / int` thành phép chia số thực.

## 11. Lỗi thường gặp và cách tự kiểm tra

| Nhận định hay gặp | Vì sao sai | Cách đúng |
| --- | --- | --- |
| “Gán `int * int` vào `long` sẽ không tràn.” | Phép nhân `int` xảy ra trước khi gán. | `(long) a * b`. |
| “Cast toàn biểu thức sau cùng vẫn giữ được kết quả.” | Cast không thể hồi phục các bit đã mất. | Cast toán hạng trước phép tính gây tràn. |
| “Biến đích `double` làm `1 / 2` thành `0.5`.” | `1 / 2` là chia nguyên và cho `0` trước khi đổi sang `double`. | `1.0 / 2` hoặc `(double) 1 / 2`. |
| “Cộng hai `byte` luôn cho `byte`.” | Phép cộng nâng toán hạng lên `int`. | Nhận vào `int`, hoặc cast có kiểm tra miền. |
| “Phép `+=` không mất dữ liệu vì không cần cast.” | Gán ghép có ép kiểu hẹp ngầm. | Xét rõ phạm vi giá trị hoặc dùng kiểu rộng hơn. |
| “Chuyển một phép toán đã tràn sang `BigInteger` sẽ sửa đúng.” | `BigInteger.valueOf(a * b)` nhận giá trị nguyên thủy đã tràn. | `BigInteger.valueOf(a).multiply(BigInteger.valueOf(b))`. |
| “Bất cứ khi nào thấy toán tử `+` đều áp dụng bảng numeric promotion.” | `+` cũng có thể là nối chuỗi; các toán tử khác có quy tắc riêng. | Xác định toán tử và kiểu từng toán hạng/biểu thức con. |

Khi đọc một biểu thức dài, hãy xác định **kiểu của từng toán hạng**, **kiểu của từng phép toán**, **vị trí chuyển kiểu**, **giới hạn của kiểu đang tính** rồi mới nhìn tới **kiểu biến nhận kết quả**. Đặc biệt phải xét các cặp `int × int` gán `long`, `int / int` gán `double`, và phép gán ghép trên `byte`/`short`.

**Ghi nhớ:** *Kiểu biến nhận quyết định cách lưu kết quả đã tính; kiểu của biểu thức quyết định phép tính diễn ra thế nào.* Nếu cần giữ kết quả chính xác, chọn và chuyển kiểu **trước phép toán có nguy cơ tràn hoặc mất phần thập phân**, hoặc dùng phương thức kiểm tra tràn/kiểu số nguyên lớn.

**Tài liệu tham khảo:** [Java Language Specification §5.6 — Numeric Contexts](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.6), [§5.2 — Assignment Contexts](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.2), [§15.17 — Multiplicative Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.17), [§15.18 — Additive Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.18), [§15.26.2 — Compound Assignment Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.26.2).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-overflow-004">Nâng kiểu toán hạng trước phép nhân thay vì ép kiểu sau</a>

<details>
<summary>Xem chi tiết</summary>

# Nâng kiểu toán hạng trước phép nhân thay vì ép kiểu sau trong Java

Một lỗi phổ biến khi xử lý số nguyên Java là tính tích của hai giá trị `int`, rồi **gán kết quả cho `long`** với kỳ vọng Java sẽ tự giữ toàn bộ kết quả:

```java
int quantity = 50_000;
int unit = 50_000;
long total = quantity * unit;       // Sai về giá trị: -1794967296
```

Tích toán học là **2.500.000.000**, lớn hơn `Integer.MAX_VALUE` (2.147.483.647) nhưng vẫn nằm trong miền `long`. Tuy nhiên, **kiểu của biểu thức nhân được quyết định từ các toán hạng trước khi phép nhân diễn ra**. Cả hai toán hạng đều là `int` nên Java nhân bằng `int`, bị tràn, rồi mới mở rộng *kết quả sai* sang `long`.

Giải pháp là **nâng ít nhất một toán hạng lên `long` trước phép nhân**:

```java
long correct = (long) quantity * unit; // 2500000000
```

Sự khác biệt không nằm ở việc biến `total` có rộng hay không, mà ở **thời điểm chuyển kiểu** so với **thời điểm phép nhân được thực hiện**.

## 1. Java quyết định kiểu của phép nhân như thế nào?

Với biểu thức số học `a * b`, Java áp dụng **binary numeric promotion**:

| Kiểu hai toán hạng | Kiểu tính toán/kết quả của `*` | Ví dụ |
| --- | --- | --- |
| `byte` và `byte` | `int` | `byte` được nâng lên `int` |
| `short` và `short` | `int` | `short` được nâng lên `int` |
| `byte` và `int` | `int` | Ít nhất là `int` |
| `int` và `int` | `int` | Có thể tràn khi tích vượt miền `int` |
| `int` và `long` | `long` | Toán hạng `int` được nâng thành `long` |
| `long` và `long` | `long` | Vẫn có thể tràn nếu tích vượt miền `long` |
| Có toán hạng `float` hoặc `double` | Theo quy tắc nâng kiểu số thực | Phạm vi lớn hơn có thể kèm mất độ chính xác |

Bài này tập trung vào phép nhân **số nguyên có dấu**. Độ rộng của các kiểu liên quan là `byte` 8 bit, `short` 16 bit, `int` 32 bit và `long` 64 bit; các phép `byte * byte` và `short * short` được tính ít nhất theo `int`.

Kết quả toán học của `int * int` có thể cần tới 64 bit để biểu diễn đầy đủ. Java **không tự nâng `int * int` lên `long` chỉ vì kết quả quá lớn**. Với các toán tử số nguyên nguyên thủy thông thường, phép nhân tràn **không tự ném ngoại lệ**: bit cao vượt khỏi độ rộng bị bỏ và mẫu bit còn lại được diễn giải theo số có dấu.

Ví dụ:

- `50_000 * 50_000` bằng **2.500.000.000** về mặt toán học.
- Giá trị đó vượt `Integer.MAX_VALUE` nên phép nhân `int` tạo ra **-1.794.967.296** theo bề rộng 32 bit.
- `long total = ...` chỉ chuyển **-1.794.967.296** sang `long`. Các bit đã mất không thể phục hồi sau phép nhân.

Ở mức bit, kết quả phép nhân `int` về cơ bản giữ phần thấp 32 bit của tích; còn phép nhân `long` giữ phần thấp 64 bit. Hai hành vi đều cần được xem xét nếu bài toán phải tính chính xác.

## 2. So sánh trực tiếp ba cách viết

```java
int a = 50_000;
int b = 50_000;

long wrong1 = a * b;           // -1794967296: nhân int rồi gán long
long wrong2 = (long) (a * b);  // -1794967296: ép kiểu sau khi nhân int
long right1 = (long) a * b;    // 2500000000: a thành long trước khi nhân
long right2 = a * (long) b;    // 2500000000: b thành long trước khi nhân
long right3 = 1L * a * b;      // 2500000000: 1L khiến phép nhân đầu tiên là long
```

**Vì sao cách viết thứ hai vẫn sai?** Phép `(long) (a * b)` bắt Java tính toàn bộ biểu thức trong ngoặc `a * b` thành `int` trước, sau đó mới cast sang `long`. Cast mở rộng **giữ nguyên giá trị `int` bị tràn**, không tính lại phép nhân. Với `(long) a * b`, cast áp dụng cho `a` trước, nên `long * int` được tính theo `long`.

Có thể hình dung trình tự:

```text
Sai:  (long) (a * b)
       a:int × b:int  → tích:int đã tràn → cast long → giá trị sai dưới dạng long

Đúng: (long) a * b
       a:int → cast long  → a:long × b:int → tích:long (nếu vừa miền long)
```

Một quy tắc thực hành dễ nhớ: **muốn kết quả phép nhân nguyên được tính theo `long`, hãy đảm bảo kiểu `long` đã xuất hiện ở toán hạng của phép nhân cần bảo vệ**.

## 3. Toán tử `*` kết hợp trái: nâng kiểu phải đủ sớm

Với chuỗi phép nhân, `*` có tính kết hợp từ trái sang phải. Một toán hạng `long` xuất hiện **quá muộn** không cứu được phép nhân `int` đã diễn ra.

```java
int a = 50_000;
int b = 50_000;

long late = a * b * 1L;      // -1794967296
long early = a * 1L * b;     // 2500000000
long grouped = a * (b * 1L); // 2500000000
```

Vì `a * b * 1L` được nhóm như `(a * b) * 1L`, tích `a * b` bị tràn `int` **trước** khi toán hạng `1L` có mặt. Ngược lại, trong `a * 1L * b`, phép nhân đầu tiên đã thuộc `long`; kết quả trung gian `long` tiếp tục tham gia phép nhân tiếp theo.

Với ba hoặc nhiều toán hạng `int`:

```java
int x = 20_000, y = 20_000, z = 20;
long unsafe = x * y * z;        // Phép nhân thứ hai có thể tràn int
long safer = (long) x * y * z; // Mọi phép nhân trong chuỗi tính theo long
```

Từ “safer” ở đây chỉ có nghĩa **tránh tràn trung gian kiểu `int`**. Nếu tích cuối hoặc một tích trung gian vượt `Long.MAX_VALUE`/`Long.MIN_VALUE`, `long` cũng sẽ tràn. Cần kiểm tra miền dữ liệu thực tế.

Không chỉ phép nhân: `long answer = (long) (x * y + z);` cũng **cast sau toàn bộ biểu thức `int`**. Nếu `x * y` đã tràn, chuyển sang `long` không sửa được. Hãy đưa toán hạng sang `long` trước bước tính có nguy cơ tràn, chẳng hạn `long answer = (long) x * y + z;`.

## 4. Phép nhân trong lời gọi và giá trị trả về

Quy tắc nâng kiểu áp dụng cả trong đối số và giá trị trả về: chữ ký nhận/trả `long` không tự đổi phép nhân `int * int` thành `long`. Chẳng hạn, `static long wrongTotal(int a, int b) { return a * b; }` vẫn có thể trả tích đã tràn, còn `return (long) a * b;` tính tích trong `long` trước khi trả.

Vì vậy, hãy kiểm tra vị trí cast ngay tại **biểu thức nhân đầu tiên** trong lời gọi, chuỗi toán tử hoặc câu lệnh `return`, thay vì suy luận từ kiểu biến/parameter ở bên ngoài.

## 5. Những cách tạo phép nhân `long` đúng

Chọn một trong các cách sau tùy mục đích diễn đạt:

| Cách viết | Kiểu phép nhân | Khi dùng |
| --- | --- | --- |
| `(long) a * b` | `long` | Dễ đọc, thể hiện rõ việc nâng `a` trước phép nhân |
| `a * (long) b` | `long` | Tương đương, nâng `b` trước |
| `1L * a * b` | `long` từ đầu chuỗi | Có thể tiện khi nhiều toán hạng `int`, nhưng cast tường minh thường rõ hơn |
| `a * b * 1L` | Phép `a * b` đầu tiên là `int` | **Không an toàn** nếu `a * b` có thể tràn |
| `(long) (a * b)` | `int` rồi mới mở rộng | **Không sửa được** tích `int` đã tràn |
| `Math.multiplyFull(a, b)`, với hai `int` | Kết quả `long` chính xác | Không cần cast; Java 9+ |
| `Math.multiplyExact((long) a, b)` | `long`, phát hiện tràn | Tích được tính theo `long` với kiểm tra giới hạn |

Nếu có dữ liệu nguồn `long`, chỉ cần phép nhân `long * long` là chưa đủ để cam kết kết quả chính xác khi tích lớn. Dùng `Math.multiplyExact(long, long)` để phát hiện tràn hoặc `BigInteger` khi cần vượt 64 bit.

Không nên đổi tùy tiện sang `double` chỉ vì `double` biểu diễn được số rất lớn. `double` có 53 bit phần trị hiệu dụng, nên nhiều số nguyên lớn (và nhiều phép nhân) có thể bị làm tròn. Với bài toán đếm, tiền ở đơn vị nguyên nhỏ nhất hoặc mã số nguyên, cần kiểm soát độ chính xác số nguyên.

## 6. Dùng `Math.multiplyExact`, `Math.multiplyFull` và `BigInteger`

### `Math.multiplyExact`: phát hiện tràn số

`Math.multiplyExact(int, int)` trả `int` nếu tích vừa miền `int`; nếu không, ném `ArithmeticException`. `Math.multiplyExact(long, long)` trả `long` nếu tích vừa miền `long`; nếu không, cũng ném `ArithmeticException`.

```java
int a = 50_000, b = 50_000;

// Math.multiplyExact(a, b) sẽ ném ArithmeticException do tích vượt int.
long result = Math.multiplyExact((long) a, b); // 2500000000
```

Ở đây, việc cast **trước lời gọi** khiến overload có toán hạng `long` được chọn. Nếu viết `long result = Math.multiplyExact(a, b);`, overload `int,int` vẫn được chọn và sẽ ném khi tích vượt `int`. Kiểu biến nhận kết quả không thay đổi overload được chọn từ các đối số.

Cẩn thận với biểu thức truyền vào:

```java
int a = 50_000, b = 50_000;
long c = 2;

// Sai mục tiêu: a * b đã tràn trước khi phương thức được gọi.
long wrong = Math.multiplyExact(a * b, c);

// Đúng về thời điểm tính: a được mở rộng trước phép nhân.
// Math.multiplyExact còn kiểm tra tích kiểu long có vượt miền hay không.
long right = Math.multiplyExact(Math.multiplyExact((long) a, b), c);
```

`Math.multiplyExact` **không thể phát hiện một phép toán đã tràn trước lúc dữ liệu đến tay nó**.

### `Math.multiplyFull`: nhận tích chính xác của hai `int`

Vì tích của **hai giá trị `int` có dấu** luôn nằm trong phạm vi `long` (kể cả `Integer.MIN_VALUE * Integer.MIN_VALUE`), Java cung cấp `Math.multiplyFull(int, int)`, trả về **`long`** là tích chính xác.

```java
int a = 50_000, b = 50_000;
long exact = Math.multiplyFull(a, b); // 2500000000
```

Đây là API hữu ích khi rõ ràng hai toán hạng thuộc `int` và mong kết quả 64 bit. Với nhiều thừa số `int`, phải xét từng bước hoặc dùng chiến lược kiểm tra giới hạn tiếp theo.

### `BigInteger`: khi `long` cũng không đủ

Ví dụ `3_100_000_000L * 3_100_000_000L` có tích toán học **9.610.000.000.000.000.000**, vượt `Long.MAX_VALUE`. Ép kiểu `(long)` không giúp gì vì hai toán hạng đã là `long`.

```java
import java.math.BigInteger;

// Mã minh họa trong phương thức:
long a = 3_100_000_000L;
long b = 3_100_000_000L;

BigInteger exact = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
// 9610000000000000000
```

`BigInteger` tính số nguyên có độ lớn tùy ý trong giới hạn tài nguyên. Chuyển đầu vào bằng `BigInteger.valueOf(...)` **trước phép nhân**; đừng dùng `BigInteger.valueOf(a * b)` nếu `a * b` có thể đã tràn `long`.

## 7. Chương trình Java 17+ kiểm chứng cả phép nhân sai và đúng

Tạo tệp `MultiplyPromotionDemo.java`:

```java
public class MultiplyPromotionDemo {
    public static void main(String[] args) {
        int a = 50_000;
        int b = 50_000;

        System.out.println("int product: " + (a * b));
        System.out.println("cast after: " + ((long) (a * b)));
        System.out.println("cast before: " + ((long) a * b));
        System.out.println("long operand: " + (1L * a * b));
        System.out.println("cast late in chain: " + (a * b * 1L));
        System.out.println("cast early in chain: " + (a * 1L * b));

        try {
            Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            System.out.println("multiplyExact int: overflow");
        }

        System.out.println("multiplyExact long: " + Math.multiplyExact((long) a, b));
        System.out.println("multiplyFull: " + Math.multiplyFull(a, b));

        long large = 3_100_000_000L;
        System.out.println("long product overflow: " + (large * large));
        try {
            Math.multiplyExact(large, large);
        } catch (ArithmeticException ex) {
            System.out.println("multiplyExact long: overflow");
        }

        System.out.println("BigInteger product: "
            + java.math.BigInteger.valueOf(large)
                .multiply(java.math.BigInteger.valueOf(large)));
    }
}
```

Biên dịch/chạy:

```bash
javac MultiplyPromotionDemo.java
java MultiplyPromotionDemo
```

Kết quả stdout:

```text
int product: -1794967296
cast after: -1794967296
cast before: 2500000000
long operand: 2500000000
cast late in chain: -1794967296
cast early in chain: 2500000000
multiplyExact int: overflow
multiplyExact long: 2500000000
multiplyFull: 2500000000
long product overflow: -8836744073709551616
multiplyExact long: overflow
BigInteger product: 9610000000000000000
```

Chương trình chứng minh ba điều:

1. **Cast sau phép nhân chỉ mở rộng giá trị đã tràn:** hai dòng đầu và `cast late in chain` cùng in `-1794967296`.
2. **Nâng kiểu trước phép nhân giải quyết overflow trung gian `int` khi tích vừa `long`:** `cast before`, `long operand` và `cast early in chain` đều in `2500000000`.
3. **Mở rộng lên `long` vẫn có giới hạn:** ví dụ `large * large` bị tràn `long`; `Math.multiplyExact` phát hiện, `BigInteger` giữ kết quả toán học chính xác.

## 8. Kiểm tra biên trước khi chọn `int`, `long` hay `BigInteger`

Một cách thực tế để thiết kế phép nhân:

1. **Xác định miền đầu vào**: giá trị nhỏ nhất/lớn nhất có thể xuất hiện là bao nhiêu, có âm hay không?
2. **Xác định miền tích toán học**, không chỉ miền của từng toán hạng. Hai số đều vừa `int` vẫn có thể cho tích lớn hơn `int`.
3. **Chọn kiểu tính toán đủ rộng từ đầu**: `int * int` cần tối đa `long` để giữ tích chính xác; `long * long` có thể cần `BigInteger`.
4. **Nếu kiểu kết quả phải bị giới hạn**, dùng `Math.multiplyExact` và xử lý `ArithmeticException` theo nghiệp vụ.
5. **Kiểm tra các biểu thức trung gian** trong tích nhiều thừa số, phép cộng/nhân xen kẽ, lệnh gọi hàm và biểu thức ép kiểu.

Ví dụ với `quantity * unitPriceInCents`, cả số lượng và đơn giá lưu dạng `int` chưa bảo đảm **tổng tiền** nằm trong `int`. Nếu kết quả phải là `long`:

```java
int quantity = 50_000;
int unitPriceInCents = 50_000;

long totalCents = (long) quantity * unitPriceInCents; // 2500000000
```

Nếu kết quả dài hơn `long` có thể xảy ra vì dữ liệu nhập không giới hạn, cần thiết kế biên rõ ràng hoặc tính bằng `BigInteger`. Nếu dùng `long` mà muốn kiểm tra thay vì tràn im lặng, `Math.multiplyExact((long) quantity, unitPriceInCents)` là lựa chọn phù hợp.

**Lưu ý về tốc độ và độ rộng:** Không nhất thiết chuyển mọi phép nhân nhỏ thành `long`; cần chọn kiểu theo miền giá trị và yêu cầu dữ liệu. Điều cần tránh là chọn kiểu biến đích đủ rộng nhưng để **biểu thức trung gian** tính ở miền hẹp hơn.

## 9. Những nhầm lẫn thường gặp

1. **“`long result = intA * intB` chắc chắn đúng.”** Sai: `intA * intB` được tính theo `int` trước khi chuyển sang `long`.
2. **“`(long) (intA * intB)` khôi phục tích.”** Sai: cast áp dụng **sau** phép nhân trong ngoặc.
3. **“Chỉ cần thêm `1L` ở cuối chuỗi nhân.”** Sai: `a * b * 1L` đã tính `a * b` theo `int`.
4. **“Nếu cả hai toán hạng nằm trong miền `int` thì tích cũng vừa `int`.”** Sai: `50_000 * 50_000` vượt `Integer.MAX_VALUE`.
5. **“Overflow của `int` luôn ném ngoại lệ.”** Sai: phép `*` nguyên thủy thường quấn vòng, chỉ API kiểm tra như `Math.multiplyExact` báo lỗi.
6. **“Đổi một toán hạng sang `double` luôn tốt hơn `long`.”** Sai với tính toán nguyên chính xác: `double` có thể làm tròn số nguyên lớn.
7. **“`Math.multiplyExact(intA, intB)` trả được `long` vì biến nhận là `long`.”** Sai: overload `int,int` được chọn từ **kiểu đối số**.
8. **“`Math.multiplyExact(intA * intB, longC)` kiểm tra cả phép nhân đầu tiên.”** Sai: đối số `intA * intB` có thể tràn trước khi gọi hàm.
9. **“`long * long` không bao giờ tràn.”** Sai: `long` chỉ có 64 bit; tích của hai `long` có thể cần rộng hơn.
10. **“`BigInteger.valueOf(longA * longB)` tránh mất dữ liệu.”** Sai: phép nhân `long` xảy ra trước khi tạo `BigInteger`.

## 10. Tự kiểm tra

- **`int a = 70_000, b = 70_000; long x = a * b;` có chính xác `4_900_000_000` không?** Không, vì `int * int` tràn. Sửa thành `long x = (long) a * b;`.
- **`long x = (long) (a * b);` có tốt hơn không?** Không: vẫn nhân `int` trước.
- **`long x = a * b * 1L;` và `long y = 1L * a * b;` khác gì?** `x` có phép nhân đầu theo `int`; `y` có các phép nhân theo `long`.
- **`Math.multiplyFull(a, b)` trả về kiểu gì khi `a` và `b` là `int`?** `long`, và tích của hai `int` luôn vừa miền `long`.
- **Nếu hai toán hạng đã là `long` và tích có thể vượt `long`, làm gì?** `Math.multiplyExact(long,long)` để phát hiện tràn; `BigInteger` để biểu diễn kết quả nguyên chính xác vượt 64 bit.
- **Vì sao cast `(long)` ở ngoài dấu ngoặc không cứu được số đã tràn?** Vì quá trình tính toán bằng `int` đã bỏ các bit vượt phạm vi **trước khi cast**.

**Ghi nhớ:** Java quyết định kiểu của phép nhân từ **kiểu toán hạng**, rồi mới tính và gán. Với `int * int` có thể vượt 32 bit, hãy **nâng một toán hạng thành `long` trước dấu `*`**: `(long) a * b`. Cast sau `(long) (a * b)` chỉ mở rộng một kết quả `int` có thể đã sai. Khi `long` chưa đủ, cần phát hiện tràn hoặc dùng `BigInteger`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-overflow-005">Math.addExact, subtractExact và multiplyExact</a>

<details>
<summary>Xem chi tiết</summary>

# `Math.addExact`, `subtractExact` và `multiplyExact`: phát hiện tràn số nguyên trong Java

Trong Java, phép cộng `+`, trừ `-` và nhân `*` trên `int`/`long` **không tự báo lỗi khi kết quả vượt phạm vi**. Kết quả sẽ bị tràn theo biểu diễn số nguyên có độ rộng cố định. Điều này nguy hiểm khi tính tổng tiền theo đơn vị nhỏ nhất, số lượng tồn kho, kích thước bộ nhớ, bộ đếm, số điểm hoặc giới hạn giao dịch.

`java.lang.Math` cung cấp ba phương thức **kiểm tra tràn** (*exact arithmetic*):

- `Math.addExact(a, b)`: tính `a + b` và báo lỗi nếu kết quả không biểu diễn được bằng kiểu trả về.
- `Math.subtractExact(a, b)`: tính `a - b`, có kiểm tra tràn.
- `Math.multiplyExact(a, b)`: tính `a * b`, có kiểm tra tràn.

Khi tràn, cả ba đều **ném `ArithmeticException`**, thay vì trả giá trị đã quay vòng. Chúng không tự mở rộng kiểu của phép toán và không tạo số nguyên có độ dài vô hạn.

## 1. Tại sao cần phép toán có kiểm tra tràn?

`int` có 32 bit có dấu, biểu diễn từ `-2_147_483_648` đến `2_147_483_647`. `long` có 64 bit có dấu, từ `-9_223_372_036_854_775_808L` đến `9_223_372_036_854_775_807L`.

Ví dụ phép toán thường:

```java
int a = Integer.MAX_VALUE;
int unchecked = a + 1; // -2147483648: đã tràn int
```

`Integer.MAX_VALUE` là 2.147.483.647, nên cộng 1 về mặt toán học phải ra 2.147.483.648, không chứa vừa `int`. `+` không báo lỗi; kết quả `int` thành giá trị âm nhỏ nhất.

Dùng phép toán có kiểm tra:

```java
int a = Integer.MAX_VALUE;
int checked = Math.addExact(a, 1); // ArithmeticException lúc chạy
```

Nếu kết quả nằm trong miền của kiểu trả về, `addExact` trả **chính kết quả số học**. Nếu nằm ngoài miền, method ném exception; không có giá trị trả về thành công.

| Phép tính | Toán tử thông thường trên `int` | `Math.*Exact` trên `int` |
| --- | --- | --- |
| `40 + 2` | 42 | 42 |
| `Integer.MAX_VALUE + 1` | -2147483648 (tràn) | `ArithmeticException` |
| `Integer.MIN_VALUE - 1` | 2147483647 (tràn) | `ArithmeticException` |
| `100_000 * 100_000` | 1410065408 (tràn) | `ArithmeticException` |

**Giới hạn của việc kiểm tra:** các method phát hiện tràn **ở phép toán do chính method thực hiện**, không có khả năng suy ngược lỗi đã xảy ra trước khi chúng được gọi.

## 2. Cú pháp, overload và kiểu kết quả

Ba method là `static` trong `java.lang.Math`, nên có thể gọi bằng `Math.addExact(...)` mà không cần `import`.

| Phép toán | Chữ ký thường dùng | Kiểu trả về |
| --- | --- | --- |
| Cộng chính xác | `Math.addExact(int x, int y)` | `int` |
| Cộng chính xác | `Math.addExact(long x, long y)` | `long` |
| Trừ chính xác | `Math.subtractExact(int x, int y)` | `int` |
| Trừ chính xác | `Math.subtractExact(long x, long y)` | `long` |
| Nhân chính xác | `Math.multiplyExact(int x, int y)` | `int` |
| Nhân chính xác | `Math.multiplyExact(long x, long y)` | `long` |
| Nhân chính xác | `Math.multiplyExact(long x, int y)` | `long` |

Overload `multiplyExact(long, int)` có trong Java hiện đại (bao gồm Java 17). Khi `int` được truyền cho tham số `long`, Java có thể tự mở rộng kiểu như lời gọi method thông thường.

Ví dụ:

```java
int sum = Math.addExact(15, 27);        // 42, overload int
long sumLong = Math.addExact(15L, 27);  // 42L, overload long
int product = Math.multiplyExact(6, 7); // 42
long big = Math.multiplyExact(100_000L, 100_000); // 10000000000L
```

Lưu ý quan trọng: **kiểu của biến nhận kết quả không quyết định overload được chọn**.

```java
// Vẫn gọi multiplyExact(int, int), nên phép nhân tràn sẽ ném exception:
long wrong = Math.multiplyExact(100_000, 100_000);

// Chọn phép nhân có kết quả long ngay tại lời gọi:
long correct = Math.multiplyExact(100_000L, 100_000);
```

Đây là đoạn minh họa: dòng `wrong` biên dịch được nhưng ném exception lúc chạy nếu được thực thi. Chữ `L` hoặc ép kiểu phải xuất hiện **ở đối số trước khi gọi**; gán kết quả vào `long` sau lời gọi không cứu được phép tính `int`.

## 3. `Math.addExact`: cộng và phát hiện vượt biên

`addExact(x, y)` trả `x + y` khi kết quả nằm trong miền `int`/`long` được chọn; nếu không thì ném `ArithmeticException`.

### Ví dụ bình thường và sát biên

```java
int score = Math.addExact(80, 20); // 100
int max = Math.addExact(Integer.MAX_VALUE - 1, 1); // Integer.MAX_VALUE
int min = Math.addExact(Integer.MIN_VALUE, 0); // Integer.MIN_VALUE
```

Ở đây chạm chính xác giá trị lớn nhất/nhỏ nhất là **hợp lệ**. Method chỉ báo lỗi khi phép tính vượt phạm vi.

### Vượt biên dương và âm

```java
// ArithmeticException: 2147483647 + 1 nằm ngoài int
Math.addExact(Integer.MAX_VALUE, 1);

// ArithmeticException: -2147483648 + (-1) nằm ngoài int
Math.addExact(Integer.MIN_VALUE, -1);
```

Hai câu lệnh là đoạn **minh họa ném exception**, không phải ví dụ để chạy mà không có xử lý lỗi.

Nếu yêu cầu là **tính được kết quả lớn hơn miền int**, hãy chọn overload `long` ngay từ đầu:

```java
long expanded = Math.addExact((long) Integer.MAX_VALUE, 1);
// 2147483648L
```

Nhưng `long` vẫn có giới hạn. `Math.addExact(Long.MAX_VALUE, 1L)` cũng ném `ArithmeticException`.

## 4. `Math.subtractExact`: trừ hai số và trường hợp biên

`subtractExact(x, y)` kiểm tra kết quả toán học của `x - y`. Phép trừ có thể tràn ở cả hai chiều:

```java
int remaining = Math.subtractExact(100, 35); // 65
int min = Math.subtractExact(Integer.MIN_VALUE + 1, 1); // MIN_VALUE
```

Các trường hợp này ném `ArithmeticException`:

```java
// Tràn âm: MIN_VALUE - 1 < MIN_VALUE
Math.subtractExact(Integer.MIN_VALUE, 1);

// Tràn dương: MAX_VALUE - (-1) > MAX_VALUE
Math.subtractExact(Integer.MAX_VALUE, -1);
```

Dấu của toán hạng thứ hai dễ làm người học nhầm: **trừ số âm tương đương cộng số dương về mặt toán học**, nên `MAX_VALUE - (-1)` vượt trần `int`.

Với `long`:

```java
long safe = Math.subtractExact((long) Integer.MIN_VALUE, 1L);
// -2147483649L, hợp lệ vì vẫn nằm trong long
```

`Math.subtractExact(Long.MIN_VALUE, 1L)` ném `ArithmeticException`. Kiểm tra biên luôn tương ứng kiểu đã chọn, không phải phạm vi `int` một cách cố định.

## 5. `Math.multiplyExact`: nhân và phát hiện tràn

Phép nhân có thể vượt phạm vi ngay cả khi từng toán hạng riêng lẻ nhỏ hơn giới hạn. Ví dụ `100_000` vẫn là `int` hợp lệ, nhưng tích `100_000 * 100_000 = 10_000_000_000` vượt `int`.

```java
int normal = Math.multiplyExact(6, 7); // 42
int square = Math.multiplyExact(46_340, 46_340); // 2147395600

// Ném ArithmeticException:
Math.multiplyExact(100_000, 100_000);
```

`46_340 * 46_340 = 2_147_395_600` vẫn nằm trong miền `int`, vì vậy method trả kết quả bình thường. `46_341 * 46_341 = 2_147_488_281` vượt `Integer.MAX_VALUE` nên `Math.multiplyExact(46_341, 46_341)` ném exception.

### Tích âm và cặp giá trị đặc biệt

Với `int`, `Integer.MIN_VALUE * (-1)` không chứa vừa kiểu `int`: kết quả toán học là `2_147_483_648`.

```java
// Ném ArithmeticException:
Math.multiplyExact(Integer.MIN_VALUE, -1);
```

Tương tự, `Math.multiplyExact(Long.MIN_VALUE, -1L)` ném exception vì kết quả dương vượt `Long.MAX_VALUE`.

Các trường hợp có `0` luôn cho tích `0` và không tràn, bất kể toán hạng còn lại nằm đâu trong miền kiểu tương ứng:

```java
int zero = Math.multiplyExact(Integer.MAX_VALUE, 0); // 0
```

### Chọn `long` và hiểu đúng mục tiêu

```java
long largeProduct = Math.multiplyExact(100_000L, 100_000);
// 10000000000
```

Chỉ nâng lên `long` khi miền giá trị dự kiến thực sự phù hợp với `long`. Nếu tích có thể vượt 64 bit, cân nhắc `BigInteger`; không có overload `Math.multiplyExact(BigInteger, BigInteger)`.

## 6. Lỗi tinh vi: phép toán đã tràn trước khi đến `Exact`

Giả sử viết:

```java
int alreadyOverflowed = Integer.MAX_VALUE + 1; // -2147483648
int undetected = Math.addExact(alreadyOverflowed, 0);
// -2147483648: không có overflow mới trong phép addExact này
```

`Math.addExact` nhận đầu vào thực tế là `-2147483648` và `0`; bản thân phép cộng đó hợp lệ. Method không biết giá trị âm là kết quả của một phép tính bị tràn trước đó.

Một trường hợp sai khác:

```java
long wrong = Math.addExact(Integer.MAX_VALUE + 1, 0L);
// (-2147483648) + 0L -> -2147483648L: không báo lỗi
```

Phép `Integer.MAX_VALUE + 1` được đánh giá bằng `int` **trước** khi chuyển thành đối số `long`, nên đã tràn. Cách đúng:

```java
long correct = Math.addExact((long) Integer.MAX_VALUE + 1, 0L);
// 2147483648L
```

Hoặc ngắn hơn `Math.addExact((long) Integer.MAX_VALUE, 1L)`.

Tương tự, **không bọc một biểu thức đã cộng/nhân thông thường vào phương thức `Exact` rồi kỳ vọng nó kiểm tra toàn bộ chuỗi tính toán**:

```java
int a = 2_000_000_000;
int b = 2_000_000_000;
int result = Math.addExact(a + b, 0); // a + b đã tràn!
```

Nếu cần kiểm tra ngay phép cộng của `a` và `b`, phải viết `Math.addExact(a, b)`. Nếu cần nhiều bước đều được kiểm tra, dùng method tương ứng **ở từng bước**.

## 7. Xử lý `ArithmeticException` và thiết kế nghiệp vụ

### Bắt lỗi tại ranh giới phù hợp

`ArithmeticException` là *unchecked exception* (thuộc `RuntimeException`); Java không bắt buộc khai báo `throws` hay `catch`. Tuy nhiên với phép tính quan trọng, ứng dụng nên xác định cách xử lý:

```java
try {
    int updated = Math.addExact(Integer.MAX_VALUE, 1);
    System.out.println(updated);
} catch (ArithmeticException ex) {
    System.out.println("overflow detected");
}
```

Kết quả là một dòng `overflow detected`. Không nên dựa vào **chuỗi thông báo chi tiết của exception** để quyết định nghiệp vụ; chỉ nên dựa vào kiểu lỗi và bối cảnh.

Nếu lỗi tràn là dữ liệu đầu vào không hợp lệ, tầng nghiệp vụ có thể chuyển nó thành thông báo phù hợp hoặc từ chối yêu cầu. Nếu lỗi biểu thị vi phạm bất biến nội bộ, có thể để lỗi lan tới cơ chế xử lý tập trung và ghi log đúng mức độ.

### Đừng biến lỗi tràn thành giá trị hợp lệ giả

Những cách xử lý như `catch (ArithmeticException ex) { return 0; }` rất nguy hiểm nếu `0` cũng là kết quả hợp lệ: ta sẽ không còn phân biệt “không có gì” với “tính toán vượt miền”.

Chọn rõ chính sách khi tràn:

| Chính sách | Cách thực hiện | Khi nào phù hợp |
| --- | --- | --- |
| Từ chối phép tính | Gọi method `Exact`, chuyển lỗi tại biên nhập dữ liệu | Miền dữ liệu có giới hạn nghiêm ngặt |
| Tăng miền biểu diễn | Chọn phép toán `long` ngay trước khi tính | Đầu vào `int` nhưng kết quả có thể vượt `int` |
| Dùng số nguyên không giới hạn độ rộng cố định | `BigInteger` và các method `add`, `subtract`, `multiply` | Độ lớn có thể vượt cả `long` |
| Bão hòa ở giá trị biên (*saturating*) | Tự xây dựng chính sách với xử lý rõ ràng | Chỉ khi đặc tả nghiệp vụ yêu cầu clamp ở min/max |
| Cho phép quay vòng (*wraparound*) | Toán tử số nguyên thường | Chỉ khi mục đích là số học modulo/bit pattern |

Ba method `Exact` không tự thực hiện *clamping*, không trả `Optional`, và không hỗ trợ phép toán trên số thực `float`/`double` theo cùng bộ API.

## 8. So sánh `Exact` với `long` và `BigInteger`

Ba chiến lược giải quyết **ba nhu cầu khác nhau**:

- **`Math.*Exact`:** giữ kiểu `int`/`long`, nhưng phát hiện khi kết quả vượt giới hạn của chính kiểu đó.
- **`long`:** cho miền biểu diễn lớn hơn `int`; vẫn có thể tràn và vẫn cần `Math.*Exact` nếu muốn phát hiện tràn `long`.
- **`BigInteger`:** biểu diễn số nguyên độ lớn tùy ý trong giới hạn bộ nhớ; method cộng, trừ, nhân không bị giới hạn 32/64 bit. `BigInteger` bất biến, nên phép toán trả object mới.

Ví dụ:

```java
long product = Math.multiplyExact(100_000L, 100_000); // 10000000000L

java.math.BigInteger huge = java.math.BigInteger.valueOf(Long.MAX_VALUE)
        .multiply(java.math.BigInteger.TWO); // hơn miền long
```

`BigInteger.TWO` có trong Java hiện đại, gồm Java 17. Trong một hệ thống thanh toán thực tế, cần chọn cả **đơn vị đo, kiểu số và chính sách làm tròn**; `Math.*Exact` bảo vệ khỏi một loại lỗi tràn số nguyên nhưng không xác minh mọi quy tắc nghiệp vụ.

Cần phân biệt `Math.toIntExact(long)`: method này kiểm tra **chuyển một giá trị `long` về `int`**, không thực hiện phép cộng/trừ/nhân. Nó hữu ích sau khi chọn tính bằng `long` và muốn từ chối kết quả không vừa `int`.

## 9. Chương trình Java 17+ chạy được: kiểm chứng từng phép toán

Tạo file **`ExactArithmeticDemo.java`**:

```java
public class ExactArithmeticDemo {
    public static void main(String[] args) {
        System.out.println("add=" + Math.addExact(20, 22));
        System.out.println("subtract=" + Math.subtractExact(70, 28));
        System.out.println("multiply=" + Math.multiplyExact(6, 7));
        System.out.println("addMax=" + Math.addExact(Integer.MAX_VALUE - 1, 1));
        System.out.println("subtractMin=" + Math.subtractExact(Integer.MIN_VALUE + 1, 1));
        System.out.println("square=" + Math.multiplyExact(46_340, 46_340));

        System.out.println("uncheckedAdd=" + (Integer.MAX_VALUE + 1));
        System.out.println("uncheckedMultiply=" + (100_000 * 100_000));

        try {
            Math.addExact(Integer.MAX_VALUE, 1);
            System.out.println("checkedAdd=not-detected");
        } catch (ArithmeticException ex) {
            System.out.println("checkedAdd=detected");
        }

        try {
            Math.subtractExact(Integer.MIN_VALUE, 1);
            System.out.println("checkedSubtract=not-detected");
        } catch (ArithmeticException ex) {
            System.out.println("checkedSubtract=detected");
        }

        try {
            Math.multiplyExact(100_000, 100_000);
            System.out.println("checkedMultiply=not-detected");
        } catch (ArithmeticException ex) {
            System.out.println("checkedMultiply=detected");
        }

        long longAdd = Math.addExact((long) Integer.MAX_VALUE, 1L);
        long longMultiply = Math.multiplyExact(100_000L, 100_000);
        System.out.println("longAdd=" + longAdd);
        System.out.println("longMultiply=" + longMultiply);

        int wrapped = Integer.MAX_VALUE + 1;
        System.out.println("priorOverflow=" + Math.addExact(wrapped, 0));

        try {
            Math.multiplyExact(Long.MIN_VALUE, -1L);
            System.out.println("longOverflow=not-detected");
        } catch (ArithmeticException ex) {
            System.out.println("longOverflow=detected");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac ExactArithmeticDemo.java
java ExactArithmeticDemo
```

**Kết quả chuẩn** (chương trình chủ động bắt exception nên vẫn chạy đến hết):

```text
add=42
subtract=42
multiply=42
addMax=2147483647
subtractMin=-2147483648
square=2147395600
uncheckedAdd=-2147483648
uncheckedMultiply=1410065408
checkedAdd=detected
checkedSubtract=detected
checkedMultiply=detected
longAdd=2147483648
longMultiply=10000000000
priorOverflow=-2147483648
longOverflow=detected
```

Các dòng `checked*=detected` là kết quả từ khối `catch`, không phải thông báo tùy tiện của JVM. Dòng `priorOverflow` xác nhận sự khác biệt quan trọng: nếu đã tràn trước khi gọi `addExact` thì lần gọi kiểm tra sau đó không thể phát hiện lỗi cũ.

## 10. Tình huống áp dụng thực tế

### Cập nhật số lượng có kiểm soát

Nếu một kho đang có `stock` đơn vị và nhập thêm `received`, sử dụng:

```java
int newStock = Math.addExact(stock, received);
```

Đoạn này nằm trong ngữ cảnh đã khai báo biến `stock`, `received`; chưa phải chương trình độc lập. Khi tràn `int`, method ném `ArithmeticException`. Nếu logic nghiệp vụ không cho số lượng âm, cần **kiểm tra thêm ràng buộc `newStock >= 0`**; `addExact` không tự kiểm tra ràng buộc này.

### Tính tổng giá theo đơn vị nguyên

Nếu `unitPrice` và `quantity` có kiểu `int`, nhưng tích dự kiến có thể vượt `int`:

```java
long total = Math.multiplyExact((long) unitPrice, quantity);
```

Đây là phép nhân kiểm tra tràn **long**, vì một đối số đã được mở rộng trước khi gọi. Nếu miền kết quả còn lớn hơn `long`, hãy chọn mô hình số phù hợp (ví dụ `BigInteger`) từ đầu.

### Chuỗi phép tính nhiều bước

Ví dụ tính `(price * quantity) - discount` trong miền `long`:

```java
long subtotal = Math.multiplyExact(price, quantity);
long total = Math.subtractExact(subtotal, discount);
```

Ở đây giả định cả ba tham số `price`, `quantity`, `discount` đã được khai báo với kiểu `long`; đoạn code minh họa nằm trong method hiện có. Từng phép tính phải được kiểm tra riêng. Nếu `subtotal` vượt miền, exception xuất hiện ngay ở bước nhân.

**Kiểm tra tràn không thay thế kiểm tra miền nghiệp vụ**: số lượng không âm, giới hạn đơn hàng, phân quyền, tính nhất quán của dữ liệu hoặc quy tắc tiền tệ vẫn cần kiểm tra độc lập.

## 11. Những lỗi phổ biến và checklist

| Hiểu nhầm | Thực tế |
| --- | --- |
| “`long result = Math.addExact(intA, intB);` sẽ cộng theo `long`.” | Nếu hai đối số là `int`, overload `int` được chọn; có thể ném exception trước khi gán vào `long`. |
| “Bọc phép toán đã tính bên trong `addExact` sẽ phát hiện tràn trước đó.” | Không; hãy gọi phép kiểm tra đúng tại bước cần tính. |
| “`multiplyExact` luôn trả `long`.” | `int, int` trả `int`; overload `long` trả `long`. |
| “Có exception nghĩa là JVM dừng tức thì.” | Exception có thể được bắt/được xử lý; nếu không được xử lý, chương trình sẽ thoát theo quy tắc exception. |
| “Tràn chỉ xảy ra lúc cộng số dương.” | Tràn có thể ở cộng, trừ, nhân; với số dương lẫn số âm và ở biên âm/dương. |
| “`Math.addExact` kiểm tra số âm có hợp lệ nghiệp vụ không.” | Không; nó chỉ kiểm tra kết quả toán học có nằm trong phạm vi kiểu hay không. |
| “Chuyển sang `long` là không thể tràn.” | `long` vẫn giới hạn ở 64 bit; có overload `Exact` cho `long`. |
| “`Math.multiplyExact` xử lý `double` và `float`.” | Bộ overload ở đây dành cho số nguyên `int`/`long`, không phải số thực. |
| “Có thể dùng `Math.multiplyExact` trực tiếp với `BigInteger`.” | Không; dùng method `multiply` của `BigInteger`. |

Khi kiểm tra code, hãy trả lời theo trình tự:

1. **Kiểu toán hạng là gì?** Nhìn đối số thực tế, không chỉ biến đích. Nếu cần phép tính `long`, bảo đảm một hoặc các đối số phù hợp đã mang kiểu `long` trước khi tính.
2. **Phép tính nào cần được bảo vệ?** Thay `a + b` bằng `Math.addExact(a, b)` tại đúng bước; tương tự với trừ/nhân.
3. **Kết quả nào nằm ở biên?** Chạm `MAX_VALUE` hoặc `MIN_VALUE` vẫn hợp lệ; vượt biên thì ném `ArithmeticException`.
4. **Nếu exception xảy ra, ai xử lý?** Chọn chính sách nghiệp vụ rõ ràng; không âm thầm dùng giá trị giả có thể bị nhầm là hợp lệ.
5. **Nếu miền biểu diễn không đủ?** Dùng `long` hoặc `BigInteger` tùy nhu cầu, đồng thời tiếp tục kiểm tra quy tắc nghiệp vụ.

**Ghi nhớ:** `Math.addExact`, `Math.subtractExact` và `Math.multiplyExact` cho phép tính số nguyên **có kiểm tra tràn tại chính phép toán đó**. Chúng giúp phát hiện lỗi vốn dễ bị che giấu bởi kết quả quay vòng, nhưng muốn an toàn phải chọn đúng overload, bảo vệ mọi bước trung gian và xử lý `ArithmeticException` theo mục đích của chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-overflow-006">incrementExact, decrementExact, negateExact và toIntExact</a>

<details>
<summary>Xem chi tiết</summary>

# `incrementExact`, `decrementExact`, `negateExact` và `toIntExact` trong Java

## 1. Vấn đề: số nguyên có giới hạn nhưng phép toán thường không báo tràn

Java có hai kiểu số nguyên nguyên thủy thường dùng cho phép đếm và các giá trị lớn: `int` (32 bit có dấu) và `long` (64 bit có dấu). Mỗi kiểu chỉ biểu diễn được một miền hữu hạn:

| Kiểu | Giá trị nhỏ nhất | Giá trị lớn nhất |
|---|---:|---:|
| `int` | `-2_147_483_648` | `2_147_483_647` |
| `long` | `-9_223_372_036_854_775_808L` | `9_223_372_036_854_775_807L` |

Giả sử bộ đếm kiểu `int` đã đạt `Integer.MAX_VALUE`. Lệnh `counter++` **không tự ném lỗi** mà đưa giá trị về `Integer.MIN_VALUE`. Tương tự, `Integer.MIN_VALUE - 1` trở thành `Integer.MAX_VALUE` và `-Integer.MIN_VALUE` lại cho `Integer.MIN_VALUE`. Đây là hệ quả của số học số nguyên cố định theo biểu diễn bù hai (*two's complement*).

Trong những bài toán cần **phát hiện tràn hoặc thu hẹp kiểu có mất dữ liệu**, Java cung cấp các phương thức trong `java.lang.Math`:

- `Math.incrementExact`: tăng một đơn vị, báo lỗi nếu tràn.
- `Math.decrementExact`: giảm một đơn vị, báo lỗi nếu tràn.
- `Math.negateExact`: lấy số đối, báo lỗi nếu số đối không biểu diễn được.
- `Math.toIntExact`: chuyển `long` sang `int`, báo lỗi nếu giá trị nằm ngoài miền `int`.

Cả bốn phương thức đều ném **`ArithmeticException`** khi không thể tạo kết quả chính xác trong kiểu trả về. Chúng không âm thầm cuốn vòng hay cắt bỏ các bit như các thao tác nguyên thông thường.

## 2. Bảng API và điều kiện thất bại

| Phương thức | Kiểu tham số và kết quả | Ý nghĩa | Trường hợp ném `ArithmeticException` |
|---|---|---|---|
| `Math.incrementExact(int)` | `int → int` | Tính `value + 1` | `value == Integer.MAX_VALUE` |
| `Math.incrementExact(long)` | `long → long` | Tính `value + 1` | `value == Long.MAX_VALUE` |
| `Math.decrementExact(int)` | `int → int` | Tính `value - 1` | `value == Integer.MIN_VALUE` |
| `Math.decrementExact(long)` | `long → long` | Tính `value - 1` | `value == Long.MIN_VALUE` |
| `Math.negateExact(int)` | `int → int` | Tính `-value` | `value == Integer.MIN_VALUE` |
| `Math.negateExact(long)` | `long → long` | Tính `-value` | `value == Long.MIN_VALUE` |
| `Math.toIntExact(long)` | `long → int` | Chuyển sang `int` mà không mất giá trị | `value < Integer.MIN_VALUE` hoặc `value > Integer.MAX_VALUE` |

**Điểm cần nhớ về overload:** ba phương thức đầu có cả phiên bản `int` lẫn `long`; `toIntExact` chỉ nhận `long` và trả `int`. Java chọn overload dựa trên **kiểu biểu thức**. Ví dụ, `Math.incrementExact(5)` trả `int` còn `Math.incrementExact(5L)` trả `long`.

Các phương thức này là `static` trong `Math`. Có thể gọi trực tiếp như `Math.incrementExact(n)` mà không tạo đối tượng. Chúng trả một **giá trị mới** và không tự thay đổi biến đã truyền.

## 3. `incrementExact` — tăng một đơn vị có kiểm tra

### Khi nào cần?

Một bộ đếm lượt giao dịch, mã phiên nội bộ, chỉ số logic hoặc tổng số lần xử lý có thể phải bảo đảm **không bao giờ âm vì tràn số**. Với toán tử `++`:

~~~java
int count = Integer.MAX_VALUE;
count++;
// count == Integer.MIN_VALUE
~~~

Đây là đoạn mã minh họa; kết quả theo số học `int` có độ rộng cố định. Toán tử `++` không báo overflow.

Đổi sang:

~~~java
int count = Integer.MAX_VALUE;
count = Math.incrementExact(count);
// Ném ArithmeticException, phép gán chưa hoàn thành.
~~~

Đoạn này cũng là minh họa và **cố ý ném lỗi khi chạy**. Khi toán hạng đã là `Integer.MAX_VALUE`, `Math.incrementExact(count)` ném `ArithmeticException` trước khi trả về; vì thế phép gán `count = ...` không hoàn thành và `count` vẫn giữ giá trị cũ.

Đối với `long` cũng vậy: `Math.incrementExact(Long.MAX_VALUE)` ném lỗi, trong khi `Long.MAX_VALUE + 1L` cho `Long.MIN_VALUE`.

Các giá trị **không phải cực đại** tăng bình thường, gồm `Math.incrementExact(-1) == 0`, `Math.incrementExact(0) == 1` và `Math.incrementExact(Integer.MAX_VALUE - 1) == Integer.MAX_VALUE`.

### Lưu ý với hậu tố toán tử

`count++` là biểu thức tăng biến và có sự khác nhau giữa tiền tố/hậu tố khi được dùng trong biểu thức lớn. `Math.incrementExact(count)` **không tương đương toàn bộ về mặt cú pháp/side effect**: nó chỉ trả giá trị tăng, không tự sửa `count`. Khi muốn cập nhật biến, phải gán `count = Math.incrementExact(count)`.

## 4. `decrementExact` — giảm một đơn vị có kiểm tra

`Math.decrementExact(value)` tính giá trị `value - 1` và kiểm tra miền của kiểu trả về. Nó phù hợp cho bộ đếm còn lại, số lượng chỗ, quota hoặc số dư đơn vị nguyên khi cần phát hiện **tràn số nguyên**.

~~~java
int remaining = Integer.MIN_VALUE;
remaining--;
// remaining == Integer.MAX_VALUE: đã tràn.

int safeRemaining = Math.decrementExact(Integer.MIN_VALUE);
// Ném ArithmeticException.
~~~

Đoạn code trên minh họa hai cách; dòng sau **cố ý ném lỗi**, không phải chương trình mẫu đầy đủ.

Ngược lại, `Math.decrementExact(0)` cho `-1` hoàn toàn hợp lệ. **`decrementExact` không kiểm tra điều kiện nghiệp vụ** “số lượng không được âm”; nó chỉ kiểm tra **miền kỹ thuật của kiểu số**. Nếu quy tắc ứng dụng là `remaining >= 0`, bạn vẫn phải kiểm tra điều kiện đó riêng.

Tương tự, `Math.decrementExact(Long.MIN_VALUE)` ném `ArithmeticException`, trong khi `Long.MIN_VALUE - 1L` trả `Long.MAX_VALUE`.

## 5. `negateExact` — lấy số đối có kiểm tra

Trong toán học, số đối của `x` là `-x`. Nhưng miền `int` và `long` **không đối xứng quanh 0**:

- `Integer.MIN_VALUE = -2_147_483_648`; số dương `2_147_483_648` không thể lưu bằng `int`.
- `Long.MIN_VALUE = -9_223_372_036_854_775_808L`; số dương `9_223_372_036_854_775_808` không thể lưu bằng `long`.

Vì vậy:

~~~java
int wrapped = -Integer.MIN_VALUE;
// wrapped == Integer.MIN_VALUE, không ném lỗi.

int checked = Math.negateExact(Integer.MIN_VALUE);
// Ném ArithmeticException.
~~~

Đây là minh họa, dòng `checked` **cố ý ném lỗi**.

Với mọi số `int` khác `Integer.MIN_VALUE`, `Math.negateExact` tạo số đối chính xác; ví dụ `Math.negateExact(-7) == 7`, `Math.negateExact(7) == -7` và `Math.negateExact(0) == 0`. `Math.negateExact(Long.MIN_VALUE)` cũng ném lỗi.

Nếu cần số đối của `Integer.MIN_VALUE` dưới dạng `long`, hãy **mở rộng trước khi đổi dấu**:

~~~java
long exactPositive = -(long) Integer.MIN_VALUE;  // 2147483648
~~~

Trong khi đó, `(long) (-Integer.MIN_VALUE)` chỉ chuyển thành `long` **sau** khi phép đổi dấu kiểu `int` đã tràn. Với `Long.MIN_VALUE`, không có kiểu nguyên thủy rộng hơn `long`; có thể dùng `BigInteger.valueOf(Long.MIN_VALUE).negate()` để có kết quả chính xác.

`Math.negateExact` cũng không phải hàm lấy trị tuyệt đối. Nó **đổi dấu** cả số dương lẫn số âm; nếu cần trị tuyệt đối với kiểm tra overflow, cần thiết kế phép toán riêng phù hợp API/JDK sử dụng. Chỉ dùng `Math.abs(Integer.MIN_VALUE)` sẽ không giải quyết được trường hợp biên.

## 6. `toIntExact` — thu hẹp `long` sang `int` mà không mất dữ liệu

Ép kiểu thường từ `long` sang `int` chỉ giữ 32 bit thấp; giá trị quá lớn hoặc quá nhỏ có thể biến thành một số hoàn toàn khác mà **không có exception**:

~~~java
long value = 2_147_483_648L;
int truncated = (int) value;
// truncated == -2147483648
~~~

Nếu ứng dụng cần bảo đảm giá trị khi chuyển kiểu vẫn **bằng chính số nguyên gốc**, dùng:

~~~java
long value = 2_147_483_648L;
int checked = Math.toIntExact(value);
// Ném ArithmeticException.
~~~

`Math.toIntExact(2_147_483_647L)` thành công với giá trị `2_147_483_647`; `Math.toIntExact(-2_147_483_648L)` cũng thành công với giá trị `-2_147_483_648`. Hai biên là **bao gồm**. Chỉ giá trị nằm **ngoài** khoảng `[Integer.MIN_VALUE, Integer.MAX_VALUE]` mới gây exception.

### So sánh `(int)` và `toIntExact`

| Giá trị `long` | Kết quả `(int) value` | Kết quả `Math.toIntExact(value)` |
|---:|---:|---|
| `42L` | `42` | `42` |
| `Integer.MAX_VALUE` | `2147483647` | `2147483647` |
| `2147483648L` | `-2147483648` | **`ArithmeticException`** |
| `Integer.MIN_VALUE` | `-2147483648` | `-2147483648` |
| `-2147483649L` | `2147483647` | **`ArithmeticException`** |

Bảng cho thấy **thu hẹp kiểu** khác **phép toán số học**: `toIntExact` không tính cộng/trừ; nó bảo đảm chuyển đổi không làm mất giá trị nguyên.

**Không có `Math.toIntExact(double)`**. Nếu bắt đầu từ `double`, bạn phải xử lý riêng điều kiện số hữu hạn, phần thập phân, miền giá trị và mất độ chính xác floating-point. `toIntExact` chỉ là hợp đồng giữa hai kiểu **số nguyên** `long` và `int`.

## 7. Ví dụ Java 17+ chạy độc lập: kết quả hợp lệ

Tạo tệp `MathExactBasics.java`:

~~~java
public class MathExactBasics {
    public static void main(String[] args) {
        System.out.println("incrementInt="
                + Math.incrementExact(41));
        System.out.println("decrementInt="
                + Math.decrementExact(-41));
        System.out.println("negateInt="
                + Math.negateExact(-41));
        System.out.println("incrementLong="
                + Math.incrementExact(2_147_483_647L));
        System.out.println("negateLong="
                + Math.negateExact(-2_147_483_648L));
        System.out.println("maxAsInt="
                + Math.toIntExact(2_147_483_647L));
        System.out.println("minAsInt="
                + Math.toIntExact(-2_147_483_648L));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac MathExactBasics.java
java MathExactBasics
~~~

Đầu ra:

~~~text
incrementInt=42
decrementInt=-42
negateInt=41
incrementLong=2147483648
negateLong=2147483648
maxAsInt=2147483647
minAsInt=-2147483648
~~~

Hai dòng `incrementLong` và `negateLong` chứng minh rằng **kiểu toán hạng quyết định miền kết quả**: với toán hạng `long`, số dương `2147483648` vẫn hợp lệ. Hai dòng chuyển về `int` nằm đúng biên và không gây lỗi.

## 8. Ví dụ Java 17+ chạy độc lập: kiểm tra các biên lỗi

Tạo tệp `MathExactFailures.java`:

~~~java
public class MathExactFailures {
    private static void check(String label, Runnable operation) {
        try {
            operation.run();
            System.out.println(label + "=OK");
        } catch (ArithmeticException ex) {
            System.out.println(label + "=OVERFLOW");
        }
    }

    public static void main(String[] args) {
        check("incIntMax",
                () -> Math.incrementExact(Integer.MAX_VALUE));
        check("decIntMin",
                () -> Math.decrementExact(Integer.MIN_VALUE));
        check("negIntMin",
                () -> Math.negateExact(Integer.MIN_VALUE));

        check("incLongMax",
                () -> Math.incrementExact(Long.MAX_VALUE));
        check("decLongMin",
                () -> Math.decrementExact(Long.MIN_VALUE));
        check("negLongMin",
                () -> Math.negateExact(Long.MIN_VALUE));

        check("toIntTooLarge",
                () -> Math.toIntExact(2_147_483_648L));
        check("toIntTooSmall",
                () -> Math.toIntExact(-2_147_483_649L));
        check("toIntAtMin",
                () -> Math.toIntExact((long) Integer.MIN_VALUE));
    }
}
~~~

~~~bash
javac MathExactFailures.java
java MathExactFailures
~~~

Đầu ra:

~~~text
incIntMax=OVERFLOW
decIntMin=OVERFLOW
negIntMin=OVERFLOW
incLongMax=OVERFLOW
decLongMin=OVERFLOW
negLongMin=OVERFLOW
toIntTooLarge=OVERFLOW
toIntTooSmall=OVERFLOW
toIntAtMin=OK
~~~

Hàm `check` nhận một `Runnable` để thực thi lần lượt các phép toán; khi `ArithmeticException` xuất hiện, chương trình in `OVERFLOW` thay vì dựa vào **chuỗi thông điệp ngoại lệ** (vốn không nên được giả định là hợp đồng API cố định). Với Java, một lambda `() -> Math.incrementExact(value)` phù hợp `Runnable` vì lời gọi phương thức là một *statement expression* và giá trị trả về có thể bỏ qua.

Dòng cuối cũng xác nhận `toIntExact` chấp nhận **đúng biên âm**. Không phải cứ số âm là lỗi, cũng không phải mọi giá trị `long` đều nằm ngoài miền `int`.

## 9. Dùng trong mã ứng dụng: cập nhật và chuyển kiểu có kiểm soát

### 9.1. Cập nhật bộ đếm an toàn về miền số

Tạo tệp `CheckedCounterDemo.java`:

~~~java
public class CheckedCounterDemo {
    public static void main(String[] args) {
        int counter = Integer.MAX_VALUE;

        try {
            counter = Math.incrementExact(counter);
        } catch (ArithmeticException ex) {
            System.out.println("counter overflow");
        }

        System.out.println("counter=" + counter);

        long total = (long) Integer.MAX_VALUE;
        total = Math.incrementExact(total);
        System.out.println("total=" + total);

        try {
            int narrowed = Math.toIntExact(total);
            System.out.println("narrowed=" + narrowed);
        } catch (ArithmeticException ex) {
            System.out.println("cannot narrow to int");
        }
    }
}
~~~

~~~bash
javac CheckedCounterDemo.java
java CheckedCounterDemo
~~~

Đầu ra:

~~~text
counter overflow
counter=2147483647
total=2147483648
cannot narrow to int
~~~

Phép gán cho `counter` chỉ thực hiện sau khi `incrementExact` trả về thành công; vì phương thức ném lỗi, biến `counter` vẫn bằng `Integer.MAX_VALUE`. Ngược lại, phép tăng trên `long` thành công, nhưng kết quả vượt miền `int` nên `toIntExact` từ chối chuyển về `int`.

Mã trên minh họa **kiểm tra tràn số**, không tự bảo đảm các thuộc tính như giao dịch cơ sở dữ liệu hay cập nhật **nguyên tử giữa nhiều luồng**. Ví dụ, `counter = Math.incrementExact(counter)` là thao tác đọc–tính–ghi gồm nhiều bước; khi nhiều luồng cùng sửa một biến, cần cơ chế đồng bộ hoặc một API atomic phù hợp. Phép kiểm tra overflow và tính an toàn đồng thời là **hai yêu cầu khác nhau**.

### 9.2. Xử lý exception theo hợp đồng nghiệp vụ

Không nên luôn nuốt `ArithmeticException` và tiếp tục với giá trị mặc định không được xác định. Khi bộ đếm, lượng tồn kho hoặc dữ liệu giao tiếp có nguy cơ vượt miền, hãy quyết định rõ:

- **Từ chối thao tác:** thông báo giá trị không thể biểu diễn, giữ trạng thái cũ.
- **Chọn miền rộng hơn:** dùng `long` khi `int` không đủ và không có yêu cầu bắt buộc thu hẹp.
- **Dùng kiểu số nguyên độ lớn tùy ý:** `BigInteger` khi ngay cả `long` có thể không đủ.
- **Giữ hợp đồng kiểu `int` của API:** dùng `Math.toIntExact` ở biên chuyển đổi và xử lý lỗi có ngữ cảnh.

Cần tránh tự động “bão hòa” giá trị tại `MAX_VALUE` hoặc đặt lại thành 0 nếu nghiệp vụ không yêu cầu. Các phương thức `Exact` đưa ra tín hiệu lỗi để ứng dụng tự quyết định chính sách phù hợp.

### 9.3. Phép toán khác: khi nào cần `addExact` hoặc `multiplyExact`?

`incrementExact(x)` tương đương về ý nghĩa kiểm tra với `addExact(x, 1)`. `decrementExact(x)` tương đương với `subtractExact(x, 1)`. `negateExact(x)` tương đương về mục tiêu với việc lấy số đối có kiểm tra miền.

Nếu thao tác không phải ±1, hãy dùng phương thức phù hợp như `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact` thay vì cố ghép nhiều phép `incrementExact`/`decrementExact`. Ví dụ, thêm 100 đơn vị: `Math.addExact(total, 100)` rõ ràng và chỉ kiểm tra một phép cộng.

Các phương thức trên kiểm tra **overflow kỹ thuật**, không thay thế validation nghiệp vụ: `Math.incrementExact(-3)` trả `-2` hợp lệ về kiểu nhưng có thể không hợp lệ nếu đối tượng phải chứa số lượng không âm.

## 10. Những hiểu lầm và lỗi phổ biến

| Nhầm lẫn | Sự thật và cách sửa |
|---|---|
| “`counter++` sẽ báo lỗi ở `MAX_VALUE`.” | `++` cuốn vòng âm thầm; dùng `Math.incrementExact(counter)` nếu cần phát hiện. |
| “`decrementExact(0)` phải báo lỗi.” | Nó trả `-1`; kiểm tra điều kiện không âm là chuyện của nghiệp vụ. |
| “`-Integer.MIN_VALUE` tạo số dương lớn nhất.” | Kết quả `int` vẫn là `Integer.MIN_VALUE`; `Math.negateExact` báo overflow. |
| “`toIntExact` làm tròn số.” | Nó chỉ chuyển `long` → `int` mà không mất giá trị; không nhận `double`. |
| “Ép `(int) longValue` sẽ ném lỗi nếu vượt miền.” | Cast nguyên thủy có thể cắt bit; dùng `Math.toIntExact`. |
| “`incrementExact` tự thay đổi biến đầu vào.” | Phương thức trả giá trị mới; phải gán hoặc dùng giá trị trả về. |
| “Các phương thức `Exact` tự giải quyết race condition.” | Chúng kiểm tra số học, không tự đồng bộ truy cập trạng thái giữa các luồng. |
| “Số vừa `long` thì chắc chắn vừa `int`.” | `long` có miền rộng hơn; dùng `toIntExact` khi cần bảo đảm thu hẹp. |
| “Chỉ cần bắt exception rồi tiếp tục bằng số 0.” | Chính sách xử lý overflow phải do yêu cầu nghiệp vụ quyết định. |

## 11. Checklist áp dụng vào dự án

1. **Chọn kiểu gốc:** `int` hay `long` có đủ miền giá trị kỳ vọng và các giá trị trung gian không?
2. **Tìm phép toán ở biên:** bộ đếm tăng/giảm, số đối, chuyển `long` → `int`; chọn đúng `incrementExact`, `decrementExact`, `negateExact`, `toIntExact`.
3. **Xác định khi có lỗi:** ném tiếp exception, chuyển thành lỗi có ngữ cảnh, từ chối thao tác hay đổi kiểu dữ liệu?
4. **Giữ trạng thái nhất quán:** gán kết quả sau khi phép kiểm tra hoàn tất; nếu có nhiều bước cập nhật, xem xét transaction/synchronization.
5. **Thử biên:** `MIN_VALUE`, `MIN_VALUE + 1`, `-1`, `0`, `1`, `MAX_VALUE - 1`, `MAX_VALUE` và hai giá trị `long` nằm ngay ngoài miền `int`.
6. **Kiểm tra điều kiện nghiệp vụ riêng:** số lượng không âm, hạn mức, định dạng dữ liệu và quyền cập nhật không được bảo đảm chỉ bằng phương thức `Exact`.

## 12. Câu hỏi tự kiểm tra

1. Vì sao `Math.incrementExact(Integer.MAX_VALUE)` ném lỗi, còn `Integer.MAX_VALUE + 1` trả một số âm?
2. `Math.decrementExact(0)` trả bao nhiêu? Điều này có bảo đảm “tồn kho không âm” không?
3. Tại sao `Math.negateExact(Integer.MIN_VALUE)` ném lỗi, trong khi `Math.negateExact(Integer.MIN_VALUE + 1)` thành công?
4. Hai giá trị `2_147_483_647L` và `2_147_483_648L` có kết quả `Math.toIntExact` khác nhau thế nào?
5. Sự khác nhau giữa `(int) value` và `Math.toIntExact(value)` khi `value` là `long` vượt miền `int`?
6. Trong `counter = Math.incrementExact(counter)`, nếu phương thức ném ngoại lệ, giá trị của `counter` có bị gán mới không?
7. Vì sao mã trên vẫn cần đồng bộ nếu nhiều luồng cùng tăng `counter`?
8. Nếu tổng số giao dịch có thể vượt miền `long`, nên suy nghĩ đến kiểu nào thay vì cố tăng `long` mãi?

**Đáp án gợi ý:** (1) `incrementExact` kiểm tra overflow còn phép `+` thông thường không; (2) trả `-1`, phải kiểm tra quy tắc tồn kho riêng; (3) chỉ số nguyên nhỏ nhất không có số đối dương cùng miền, còn `MIN_VALUE + 1` có; (4) giá trị thứ nhất hợp lệ, thứ hai ném `ArithmeticException`; (5) cast có thể làm mất giá trị, `toIntExact` phát hiện; (6) không, gán chưa hoàn tất; (7) kiểm tra số học không bảo đảm tính nguyên tử của đọc–ghi; (8) cân nhắc `BigInteger`.

**Ghi nhớ:** `incrementExact`, `decrementExact` và `negateExact` giữ nguyên kiểu số nguyên nhưng **báo lỗi thay vì im lặng tràn**. `toIntExact` bảo đảm chuyển `long` sang `int` **không mất giá trị**. Dùng chúng tại các phép toán và biên chuyển đổi mà tính chính xác là yêu cầu của chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-overflow-007">Phép chia exact và các API bổ sung theo phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Phép chia exact và các API bổ sung theo phiên bản JDK

Java có các phép chia số nguyên `/` và `Math.floorDiv` từ lâu. Tuy nhiên, phép chia nguyên thông thường có một trường hợp tràn đặc biệt: `Integer.MIN_VALUE / -1` trả về chính `Integer.MIN_VALUE` thay vì báo lỗi. Từ **JDK 18**, lớp `Math` bổ sung `divideExact`, `floorDivExact`, `ceilDivExact` để phát hiện trường hợp tràn này. Cùng phiên bản đó có thêm `ceilDiv` và `ceilMod` phục vụ phép chia làm tròn lên.

Chữ **exact** ở đây có nghĩa là **phép chia không được âm thầm trả một thương đã tràn miền biểu diễn**, chứ **không có nghĩa hai số phải chia hết cho nhau**. Phân biệt điều này là trọng tâm khi chọn API và khi triển khai trên JDK 17 hoặc phiên bản mới hơn.

## 1. Tại sao phép chia nguyên cần kiểm tra tràn?

Các kiểu nguyên Java dùng dấu và có cận hữu hạn:

- `int`: `-2_147_483_648` đến `2_147_483_647`.
- `long`: `-9_223_372_036_854_775_808L` đến `9_223_372_036_854_775_807L`.

Miền âm có thêm một giá trị về độ lớn so với miền dương. Giá trị toán học của `-Integer.MIN_VALUE` là 2.147.483.648, vượt `int`. Vì vậy, trường hợp `Integer.MIN_VALUE / -1` có thương toán học nằm ngoài miền `int`. Tương tự với `Long.MIN_VALUE / -1L`.

```java
int min = Integer.MIN_VALUE;
System.out.println(min / -1); // -2147483648: kết quả đã tràn.
System.out.println(min % -1); // 0.
```

**Java không ném exception trong trường hợp tràn phép chia này.** Đó là hành vi được quy định cho toán tử `/`. Ngược lại, chia số nguyên cho **0** luôn ném `ArithmeticException`.

### Khác với tràn của cộng và nhân

Phép cộng/nhân cũng có thể tràn, nhưng nhiều tổ hợp giá trị có thể tạo overflow. Với phép chia nguyên có dấu, số chia khác 0, **trường hợp tràn thương duy nhất** là:

| Kiểu | Số bị chia | Số chia | Thương toán học | Thương thường của Java |
| --- | --- | --- | --- | --- |
| `int` | `Integer.MIN_VALUE` | `-1` | 2.147.483.648 | `Integer.MIN_VALUE` |
| `long` | `Long.MIN_VALUE` | `-1L` | 9.223.372.036.854.775.808 | `Long.MIN_VALUE` |

Mọi phép chia nguyên khác, với số chia khác 0, có thương được cắt phần lẻ theo hướng về 0 và vừa miền của cùng kiểu. Dù vậy, thương vẫn **bị bỏ phần dư**, vì phép chia nguyên không lưu số thập phân.

## 2. Phiên bản JDK quyết định API nào có thể sử dụng

Khi phát triển thư viện dùng chung, có hai yếu tố độc lập: JDK đang chạy và **API được phép dùng theo phiên bản mục tiêu**. Mã được biên dịch bằng JDK mới vẫn có thể không tương thích Java 17 nếu gọi phương thức chỉ xuất hiện từ JDK 18.

| API / nhóm API trong `Math` | Phiên bản bắt đầu | Ý nghĩa |
| --- | --- | --- |
| `addExact`, `subtractExact`, `multiplyExact` (overload `int,int` và `long,long`), `incrementExact`, `decrementExact`, `negateExact`, `toIntExact` | Java 8 | Kiểm tra overflow cho cộng/trừ/nhân/tăng/giảm/đổi dấu/chuyển `long` sang `int` |
| `floorDiv` và `floorMod` (các overload ban đầu) | Java 8 | Chia lấy thương làm tròn xuống, phần dư có dấu theo số chia |
| `multiplyFull(int,int)`, `multiplyHigh(long,long)`, `multiplyExact(long,int)` | Java 9 | Một số khả năng nhân và quan sát kết quả lớn hơn |
| `absExact(int)`, `absExact(long)` | Java 15 | Lấy trị tuyệt đối, ném exception khi gặp giá trị nhỏ nhất |
| **`divideExact(int,int)`, `divideExact(long,long)`** | **Java 18** | Chia theo quy tắc cắt về 0 và ném exception nếu thương tràn |
| **`floorDivExact(int,int)`, `floorDivExact(long,long)`** | **Java 18** | Chia làm tròn xuống, có kiểm tra tràn thương |
| **`ceilDivExact(int,int)`, `ceilDivExact(long,long)`** | **Java 18** | Chia làm tròn lên, có kiểm tra tràn thương |
| `ceilDiv`, `ceilMod` | Java 18 | Thương làm tròn lên và phần dư đi kèm; bản thường không kiểm tra tràn `MIN_VALUE / -1` |
| `unsignedMultiplyHigh(long,long)` | Java 18 | Lấy 64 bit cao của tích 128 bit theo nghĩa không dấu |

Bảng này nêu những API có ý nghĩa với chủ đề số học nguyên; nó không nhằm liệt kê toàn bộ phương thức của `Math`.

**Quy tắc tương thích:**

- Trên **Java 17**, `Math.divideExact`, `Math.floorDivExact`, `Math.ceilDivExact`, `Math.ceilDiv` và `Math.ceilMod` **chưa có**. Gọi chúng khi biên dịch `--release 17` sẽ báo lỗi không tìm thấy phương thức.
- Trên **Java 18+**, có thể dùng các phương thức mới, miễn môi trường biên dịch và runtime thực tế đáp ứng phiên bản đó.
- Với **Java 17**, có thể kiểm tra trường hợp tràn trước `/` hoặc `Math.floorDiv` bằng một hàm nhỏ (xem phần 6).
- Java 17 **đã có `Math.absExact`**, `Math.addExact`, `Math.multiplyExact` và `Math.toIntExact`. Không nên nhầm thời điểm xuất hiện của cả họ `Exact` là cùng một phiên bản.

Dùng lệnh sau để kiểm tra toolchain:

```bash
java -version
javac -version
javac --release 17 YourFile.java
```

`--release 17` giới hạn ngôn ngữ và API công khai ở Java 17, nên đây là phép thử phù hợp cho mã thư viện có yêu cầu tương thích Java 17. Nếu chạy ứng dụng Java 18+ API trên runtime cũ hơn, có thể gặp lỗi liên kết `NoSuchMethodError` (hoặc lỗi phiên bản class file, tùy cách biên dịch); không chỉ quan tâm mã có chạy được trên máy phát triển.

## 3. `Math.divideExact`: vẫn là phép chia lấy thương làm tròn về 0

Từ Java 18:

```java
System.out.println(Math.divideExact(7, 3));  // 2.
System.out.println(Math.divideExact(-7, 3)); // -2.
System.out.println(Math.divideExact(7, -3)); // -2.
```

Với các toán hạng bình thường, `divideExact(x,y)` cho cùng giá trị như `x / y`, tức **truncation toward zero**. Nếu phép chia có dư, cả hai đều trả thương nguyên đã cắt, **không ném exception chỉ vì có dư**.

Ví dụ `Math.divideExact(7, 3) == 2`, chứ không có lỗi “7 không chia hết cho 3”.

### Trường hợp exception

`Math.divideExact` ném `ArithmeticException` khi:

1. Số chia bằng **0**.
2. Thương bị **overflow**, tức cặp `MIN_VALUE` và `-1` của cùng kiểu.

```java
// Java 18+:
Math.divideExact(Integer.MIN_VALUE, -1); // ArithmeticException.
Math.divideExact(Long.MIN_VALUE, -1L);   // ArithmeticException.
Math.divideExact(9, 0);                  // ArithmeticException.
```

Lưu ý `Math.divideExact` **không** ném `ArithmeticException` cho `Math.divideExact(7,3)`. Không dựa vào tên phương thức để thay thế một kiểm tra **chia hết**.

### Muốn bắt buộc chia hết không có phần dư?

Một chính sách riêng có thể gồm **hai điều kiện**: thương không tràn **và** phần dư bằng 0.

```java
// Hàm minh họa Java 18+ (đặt bên trong một lớp):
static int divideWithoutRemainder(int dividend, int divisor) {
    int quotient = Math.divideExact(dividend, divisor);
    if (dividend % divisor != 0) {
        throw new ArithmeticException("Division has remainder");
    }
    return quotient;
}
```

Ví dụ `divideWithoutRemainder(12,4)` bằng 3; `divideWithoutRemainder(7,3)` ném exception do có dư; `divideWithoutRemainder(Integer.MIN_VALUE,-1)` ném exception do tràn. `Math.divideExact` đã kiểm tra số chia 0 trước phép `%`, nên không có phép modulo với 0.

Nếu bài toán chấp nhận kết quả dạng **phân số**, phải thay đổi mô hình dữ liệu (chẳng hạn giữ tử/mẫu nguyên với quy tắc chuẩn hóa riêng hoặc dùng số thập phân với chính sách làm tròn), không mong `divideExact` tạo ra 2,333… dưới dạng `int`.

## 4. Ba kiểu lấy thương: truncate, floor và ceiling

Các kiểu làm tròn thương tạo ra kết quả khác nhau khi phép chia có số dư:

| Phép toán | Quy tắc thương | Ví dụ `-7 / 3` | Ví dụ `7 / 3` |
| --- | --- | ---: | ---: |
| `x / y` hoặc `Math.divideExact(x,y)` | Cắt phần lẻ **về 0** | −2 | 2 |
| `Math.floorDiv(x,y)` hoặc `Math.floorDivExact(x,y)` | Làm tròn **xuống âm vô cùng** | −3 | 2 |
| `Math.ceilDiv(x,y)` hoặc `Math.ceilDivExact(x,y)` | Làm tròn **lên dương vô cùng** | −2 | 3 |

Các phương thức `Exact` trong bảng đều có từ **Java 18**; `floorDiv` có từ trước, còn `ceilDiv` cũng được bổ sung ở Java 18.

Các định nghĩa toán học, khi kết quả biểu diễn được:

- `trunc(x/y)`: lấy phần nguyên hướng về 0.
- `floor(x/y)`: số nguyên lớn nhất không vượt thương toán học.
- `ceil(x/y)`: số nguyên nhỏ nhất không nhỏ hơn thương toán học.

Ví dụ có dấu âm:

```java
// Java 18+:
System.out.println(Math.divideExact(-7, 3));   // -2.
System.out.println(Math.floorDivExact(-7, 3)); // -3.
System.out.println(Math.ceilDivExact(-7, 3));  // -2.

System.out.println(Math.divideExact(7, 3));   // 2.
System.out.println(Math.floorDivExact(7, 3)); // 2.
System.out.println(Math.ceilDivExact(7, 3));  // 3.
```

**Làm tròn thương và kiểm tra tràn là hai quyết định riêng.** `floorDiv` giải quyết hướng làm tròn, còn `floorDivExact` vừa dùng cùng hướng làm tròn vừa báo lỗi overflow. `ceilDiv` và `ceilDivExact` tương tự.

### Cặp phần dư phù hợp với từng kiểu thương

Để giữ quan hệ `x = quotient * y + remainder` theo số học:

| Hướng làm tròn | Thương | Phần dư đi kèm |
| --- | --- | --- |
| Truncate | `/` hoặc `divideExact` | `%` |
| Floor | `floorDiv` hoặc `floorDivExact` | `floorMod` |
| Ceiling | `ceilDiv` hoặc `ceilDivExact` | `ceilMod` |

Với `x=-7` và `y=3`:

- Truncate: thương −2, dư −1; vì `-7 = -2 * 3 + (-1)`.
- Floor: thương −3, dư 2; vì `-7 = -3 * 3 + 2`.
- Ceiling: thương −2, dư −1; cùng kết quả truncate ở trường hợp này.

Với `x=7` và `y=3`:

- Truncate: thương 2, dư 1.
- Floor: thương 2, dư 1.
- Ceiling: thương 3, dư −2; vì `7 = 3 * 3 + (-2)`.

`floorMod` có phần dư cùng dấu với **số chia** hoặc bằng 0; `ceilMod` có phần dư trái dấu với **số chia** hoặc bằng 0. Khi thực sự cần phần dư không âm, dùng `floorMod(x, m)` với **`m > 0`**. `ceilMod` không thay thế `floorMod` cho index vòng chuẩn.

**Ngoại lệ về miền biểu diễn:** nếu `x = MIN_VALUE` và `y = -1`, các phương thức không `Exact` có thể trả thương overflow. Các đẳng thức ở trên nên được hiểu theo toán học hoặc kiểm tra trong miền số phù hợp; tránh suy luận kết quả toán học chính xác từ một phép tính đã tràn.

## 5. Những phương thức Exact *không* cùng ngữ nghĩa

Chữ `Exact` trong `Math` thường chỉ nghĩa “phát hiện kết quả nguyên **vượt miền kiểu trả về**”, mỗi phương thức kiểm tra một phép toán tương ứng:

| Phương thức | Ví dụ thành công | Khi nào báo lỗi |
| --- | --- | --- |
| `Math.addExact(a,b)` | `addExact(2,3) == 5` | Tổng vượt `int`/`long` |
| `Math.subtractExact(a,b)` | `subtractExact(5,3) == 2` | Hiệu vượt miền |
| `Math.multiplyExact(a,b)` | `multiplyExact(3,4) == 12` | Tích vượt miền |
| `Math.negateExact(a)` | `negateExact(-7) == 7` | `a == MIN_VALUE` |
| `Math.absExact(a)` | `absExact(-7) == 7` | `a == MIN_VALUE` |
| `Math.incrementExact(a)` / `decrementExact(a)` | Tăng/giảm 1 | Chạm cận khi tăng/giảm |
| `Math.toIntExact(value)` | `toIntExact(42L) == 42` | Giá trị `long` không vừa `int` |
| `Math.divideExact(x,y)` | `divideExact(7,3) == 2` | Chia 0 hoặc **tràn thương** |
| `Math.floorDivExact(x,y)` | `floorDivExact(-7,3) == -3` | Chia 0 hoặc **tràn thương** |
| `Math.ceilDivExact(x,y)` | `ceilDivExact(7,3) == 3` | Chia 0 hoặc **tràn thương** |

Một số phương thức không `Exact` vẫn có thể lưu kết quả toán học chính xác trong kiểu **rộng hơn**. Chẳng hạn `Math.multiplyFull(int,int)` trả về tích của hai `int` trong `long`, vì tích của hai `int` luôn vừa `long`. `Math.multiplyHigh(long,long)` chỉ cung cấp 64 bit cao của tích rộng 128 bit, **không phải** toàn bộ tích dưới dạng một `long` chính xác.

Lưu ý `Math.abs(Integer.MIN_VALUE)` vẫn trả `Integer.MIN_VALUE`; `Math.absExact(Integer.MIN_VALUE)` mới phát hiện overflow. `Math.toIntExact` không kiểm tra số âm hay index ngoài mảng: nó chỉ kiểm tra miền `int`, không phải hợp lệ nghiệp vụ.

## 6. Java 17: xây dựng phép chia kiểm tra overflow khi chưa có `divideExact`

Nếu module đang dùng Java 17, đừng gọi phương thức Java 18 bằng cách “import thêm”. `Math` thuộc `java.base` của JDK và không thể chỉ bổ sung phiên bản API đó bằng một import.

Để có **hành vi kiểm tra tương đương** của `divideExact(int,int)` trên Java 17, xử lý cặp gây tràn trước toán tử `/`:

```java
// Đặt bên trong một class trong Java 17+:
static int divideChecked(int x, int y) {
    if (y == 0) {
        throw new ArithmeticException("/ by zero");
    }
    if (x == Integer.MIN_VALUE && y == -1) {
        throw new ArithmeticException("integer overflow");
    }
    return x / y;
}
```

Bản cho `long`:

```java
static long divideChecked(long x, long y) {
    if (y == 0L) {
        throw new ArithmeticException("/ by zero");
    }
    if (x == Long.MIN_VALUE && y == -1L) {
        throw new ArithmeticException("long overflow");
    }
    return x / y;
}
```

Các hàm trên có thể ném `ArithmeticException` như các phương thức Java 18, nhưng **nội dung message là do hàm tự đặt ra**, không phải cam kết về thông báo exception của JDK. Khi viết test nghiệp vụ, kiểm tra **loại lỗi/điều kiện**, không phụ thuộc một chuỗi thông báo bất biến.

### Java 17: kiểm tra thương floor thay vì truncate

Có thể kiểm tra tràn rồi gọi `Math.floorDiv`:

```java
static int floorDivChecked(int x, int y) {
    if (y == 0) {
        throw new ArithmeticException("/ by zero");
    }
    if (x == Integer.MIN_VALUE && y == -1) {
        throw new ArithmeticException("integer overflow");
    }
    return Math.floorDiv(x, y);
}
```

`Math.floorDiv` đã có trong Java 17; sau khi loại trừ trường hợp tràn, kết quả phù hợp ngữ nghĩa `floorDivExact` của Java 18.

Nếu yêu cầu **ceil division** trên Java 17, phải triển khai cẩn thận với cả dấu âm, không áp dụng bừa công thức `(a+b-1)/b` vì công thức này có thể tràn và sai với số âm.

Một cách tương thích với Java 17, dựa vào thương và số dư của phép chia thường, là:

```java
static int ceilDivChecked(int x, int y) {
    int quotient = divideChecked(x, y);
    int remainder = x % y;
    if (remainder != 0 && ((x ^ y) >= 0)) {
        return quotient + 1;
    }
    return quotient;
}
```

Trong phép toán trên `int`, điều kiện `(x ^ y) >= 0` kiểm tra hai số có **cùng dấu** (bit dấu của XOR là 0). Khi thương dương có phần dư, ceil cần cộng một; với thương âm, kết quả truncate đã là ceil. Cặp gây overflow đã được `divideChecked` loại trừ; `quotient + 1` ở nhánh này không vượt miền `int`. Đây là hàm minh họa một cách triển khai kiểm tra tràn, không phải API tiêu chuẩn Java 17.

Với mã mới yêu cầu Java 18+, dùng `Math.ceilDivExact` trực tiếp sẽ dễ đọc và dễ bảo trì hơn.

## 7. Ví dụ chạy độc lập trên Java 18+

Lưu chương trình dưới tên `ExactDivisionDemo18.java`:

```java
public class ExactDivisionDemo18 {
    public static void main(String[] args) {
        System.out.println("7 / 3 = " + (7 / 3));
        System.out.println("Math.divideExact(7,3) = "
                + Math.divideExact(7, 3));
        System.out.println("Math.divideExact(-7,3) = "
                + Math.divideExact(-7, 3));

        System.out.println("Math.floorDivExact(-7,3) = "
                + Math.floorDivExact(-7, 3));
        System.out.println("Math.ceilDivExact(7,3) = "
                + Math.ceilDivExact(7, 3));
        System.out.println("Math.ceilDivExact(-7,3) = "
                + Math.ceilDivExact(-7, 3));

        System.out.println("Math.floorMod(-7,3) = "
                + Math.floorMod(-7, 3));
        System.out.println("Math.ceilMod(7,3) = "
                + Math.ceilMod(7, 3));

        System.out.println("Long division = "
                + Math.divideExact(9_000_000_000L, 3L));

        try {
            Math.divideExact(Integer.MIN_VALUE, -1);
        } catch (ArithmeticException ex) {
            System.out.println("MIN_VALUE / -1: ArithmeticException");
        }

        try {
            Math.floorDivExact(1, 0);
        } catch (ArithmeticException ex) {
            System.out.println("Division by zero: ArithmeticException");
        }

        System.out.println("7 divisible by 3? " + (7 % 3 == 0));
        System.out.println("12 divisible by 4? " + (12 % 4 == 0));
    }
}
```

Chạy với JDK 18 hoặc mới hơn:

```bash
javac --release 18 ExactDivisionDemo18.java
java ExactDivisionDemo18
```

Stdout:

```text
7 / 3 = 2
Math.divideExact(7,3) = 2
Math.divideExact(-7,3) = -2
Math.floorDivExact(-7,3) = -3
Math.ceilDivExact(7,3) = 3
Math.ceilDivExact(-7,3) = -2
Math.floorMod(-7,3) = 2
Math.ceilMod(7,3) = -2
Long division = 3000000000
MIN_VALUE / -1: ArithmeticException
Division by zero: ArithmeticException
7 divisible by 3? false
12 divisible by 4? true
```

Hai dòng cuối nhấn mạnh rằng `Math.divideExact(7,3)` vẫn bằng 2 và **không bảo đảm phép chia hết**; chỉ kiểm tra `7 % 3 == 0` mới nói được rằng hai số chia hết trong miền số nguyên.

### Ví dụ chạy riêng trên Java 17

Lưu `ExactDivisionDemo17.java`:

```java
public class ExactDivisionDemo17 {
    static int divideChecked(int x, int y) {
        if (y == 0) throw new ArithmeticException("/ by zero");
        if (x == Integer.MIN_VALUE && y == -1) {
            throw new ArithmeticException("integer overflow");
        }
        return x / y;
    }

    static int floorDivChecked(int x, int y) {
        if (y == 0) throw new ArithmeticException("/ by zero");
        if (x == Integer.MIN_VALUE && y == -1) {
            throw new ArithmeticException("integer overflow");
        }
        return Math.floorDiv(x, y);
    }

    public static void main(String[] args) {
        System.out.println("Checked quotient: " + divideChecked(-7, 3));
        System.out.println("Checked floor: " + floorDivChecked(-7, 3));
        try {
            divideChecked(Integer.MIN_VALUE, -1);
        } catch (ArithmeticException ex) {
            System.out.println("Overflow detected");
        }
    }
}
```

```bash
javac --release 17 ExactDivisionDemo17.java
java ExactDivisionDemo17
```

Stdout:

```text
Checked quotient: -2
Checked floor: -3
Overflow detected
```

Đây là **hai tệp/chương trình độc lập** với hai mức yêu cầu JDK khác nhau. Nếu thực sự phải chạy trên Java 17, chọn ví dụ Java 17 và không gọi `divideExact`/`ceilDivExact`.

## 8. Kiểm thử biên và chọn API đúng trong thực tế

Một bộ test tối thiểu cho phép chia nguyên có kiểm tra overflow nên bao gồm:

| Đầu vào | Kết quả mong đợi của `divideExact` (Java 18+) | Điều cần xác thực |
| --- | --- | --- |
| `(7,3)` | 2 | Có dư nhưng **không** ném exception |
| `(-7,3)` | −2 | Làm tròn về 0 |
| `(7,-3)` | −2 | Làm tròn về 0 |
| `(-7,-3)` | 2 | Hai số cùng dấu |
| `(Integer.MIN_VALUE,-1)` | `ArithmeticException` | Overflow `int` |
| `(Long.MIN_VALUE,-1L)` | `ArithmeticException` | Overflow `long` |
| `(1,0)` | `ArithmeticException` | Chia cho 0 |
| `(0,3)` | 0 | Zero dividend |
| `(Integer.MIN_VALUE,1)` | `Integer.MIN_VALUE` | Giá trị nằm trong miền |

Với `floorDivExact` và `ceilDivExact`, bổ sung các test chia số âm còn dư: `(-7,3)` tương ứng −3/−2, và `(7,3)` tương ứng 2/3; kiểm tra cả số chia âm. Trên JDK 17, chạy cùng bộ trường hợp cho các hàm `divideChecked`, `floorDivChecked` hoặc `ceilDivChecked` tự xây dựng, và test việc biên dịch với `--release 17`.

### Đâu là “exact” mà nghiệp vụ thực sự cần?

Có ít nhất ba nhu cầu cần phân biệt:

1. **Không cho phép overflow:** dùng `Math.divideExact`, `floorDivExact` hoặc `ceilDivExact` từ Java 18+, hoặc kiểm tra trước khi chia trên Java 17.
2. **Không cho phép phần dư:** ngoài việc kiểm tra overflow, phải kiểm tra `x % y == 0`. Không có nghĩa vụ chia hết được suy ra chỉ từ tên `divideExact`.
3. **Cần kết quả toán học lớn hơn `long` hoặc phân số/thập phân chính xác:** dùng cấu trúc số lớn hoặc biểu diễn hợp đồng phù hợp, ví dụ `BigInteger` cho số nguyên lớn và `BigDecimal` cho giá trị thập phân với chính sách chia/làm tròn. `BigDecimal.divide` với yêu cầu exact có thể ném `ArithmeticException` khi khai triển thập phân không kết thúc; đây **là ngữ nghĩa khác** `Math.divideExact`.

Nếu dữ liệu không tin cậy đến từ bên ngoài, xử lý `ArithmeticException` ở ranh giới phù hợp và quyết định rõ lỗi nào nên báo ra ngoài. Không nên bắt toàn bộ exception rồi trả về 0, vì 0 là một kết quả hợp lệ của phép chia và có thể che giấu dữ liệu sai.

### Những lỗi hay gặp

- **Nhầm `divideExact` với “chia không dư”.** `divideExact(7,3)` trả 2, không ném lỗi.
- **Dùng API Java 18 trong dự án bắt buộc Java 17.** Kiểm tra với `javac --release 17`, không chỉ với JDK cài trên máy.
- **Dùng `Math.floorDiv` để kỳ vọng tự phát hiện overflow.** `floorDiv(MIN_VALUE,-1)` vẫn có thể trả giá trị tràn; Java 18+ có `floorDivExact`.
- **Dùng `Math.ceilDiv` và nghĩ rằng đó là checked division.** `ceilDiv` xử lý hướng làm tròn; `ceilDivExact` thêm kiểm tra overflow.
- **Đổi sang `double` rồi lấy `Math.floor` hoặc `Math.ceil` khi tính trên `long` rất lớn.** Chuyển `long` sang `double` có thể mất chính xác; ưu tiên API số nguyên.
- **Giả sử `Math.abs` có kiểm tra overflow.** `Math.abs(Integer.MIN_VALUE)` vẫn âm; `Math.absExact` từ Java 15 mới phát hiện.
- **Dùng công thức `(a+b-1)/b` để tính ceil division cho mọi đầu vào.** Công thức có thể tràn, và không xử lý chính xác nhiều trường hợp số âm.
- **Nhầm overload với kiểu trả về.** Khi dùng `int`/`long`, xem chữ ký phương thức thực tế; không tự động ép hẹp một kết quả `long` sang `int`.
- **So sánh thông báo lỗi exception như một giao thức.** Hãy xác nhận loại lỗi và ngữ nghĩa, vì message không phải là hợp đồng cố định của mọi JDK.

## 9. Tự kiểm tra

1. Tại sao `Integer.MIN_VALUE / -1` không ném exception nhưng `Math.divideExact(Integer.MIN_VALUE,-1)` lại ném? Java phiên bản nào bổ sung phương thức thứ hai?
2. `Math.divideExact(10,3)` có ném `ArithmeticException` không? Nếu muốn bắt buộc 10 chia hết cho 3, điều kiện nào còn thiếu?
3. Với `x=-7, y=3`, phân biệt thương của `/`, `floorDivExact` và `ceilDivExact`.
4. Vì sao `Math.ceilDiv(7,3)` bằng 3 nhưng `Math.ceilMod(7,3)` bằng −2? Kiểm tra quan hệ giữa thương và phần dư.
5. Đang phát triển thư viện chạy trên Java 17, vì sao dùng JDK 24 để build không mặc nhiên cho phép gọi `Math.divideExact` trong artifact tương thích Java 17?
6. Viết hàm kiểm tra chia nguyên Java 17 cho trường hợp `Long.MIN_VALUE/-1L` và trường hợp divisor 0.
7. Khi nào nên dùng `Math.toIntExact`, `Math.absExact` hoặc `Math.multiplyExact` thay vì `Math.divideExact`?

## 10. Tham khảo JDK API chính thức

- [Math — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): các overload có sẵn trước Java 18, gồm `floorDiv`, `floorMod`, `absExact`, `addExact`, `multiplyExact` và `toIntExact`.
- [Math — Java SE 18](https://docs.oracle.com/en/java/javase/18/docs/api/java.base/java/lang/Math.html): `Since: 18` của `divideExact`, `floorDivExact`, `ceilDivExact`, `ceilDiv`, `ceilMod`.
- [Java Language Specification `15.17.2 — Division Operator /](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17.2): quy tắc phép chia số nguyên và trường hợp overflow.

**Ghi nhớ:** **Java 18+** có `divideExact`/`floorDivExact`/`ceilDivExact` để **báo lỗi khi thương số nguyên tràn**. “Exact” không có nghĩa kết quả không có phần dư. **Java 17** vẫn triển khai được chính sách kiểm tra bằng cách bắt cặp `MIN_VALUE/-1` trước `/` hoặc `floorDiv`, và phải xác định rõ yêu cầu làm tròn, chia hết, xử lý chia 0 cũng như phiên bản JDK mục tiêu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-overflow-008">Math.abs với MIN_VALUE và lựa chọn absExact</a>

<details>
<summary>Xem chi tiết</summary>

# Math.abs với MIN_VALUE và lựa chọn absExact trong Java

`Math.abs(x)` có vẻ là cách đơn giản để lấy trị tuyệt đối, nhưng với **`Integer.MIN_VALUE`** và **`Long.MIN_VALUE`** nó không thể trả về số dương tương ứng trong cùng kiểu số nguyên. Điều bất ngờ là **`Math.abs` không ném ngoại lệ**: kết quả vẫn là chính `MIN_VALUE`, tức một số âm.

```java
System.out.println(Math.abs(Integer.MIN_VALUE)); // -2147483648
System.out.println(Math.abs(Long.MIN_VALUE));    // -9223372036854775808
```

Nếu một thuật toán dựa vào giả định “`Math.abs` luôn không âm”, lỗi có thể xuất hiện trong chỉ số mảng, kiểm tra khoảng cách, sắp xếp theo độ lớn, chọn bucket của bảng băm hoặc kiểm soát giới hạn. **`Math.absExact`** (có trong Java 17+) giúp phát hiện riêng trường hợp trị tuyệt đối không biểu diễn được bằng kiểu số nguyên đầu vào: nó **ném `ArithmeticException`** thay vì trả giá trị âm sai kỳ vọng.

Bài này thuộc mục **Tràn và kiểm tra biên** trong **Java core – numbers**, tập trung vào cơ chế hai's complement, sự khác nhau giữa `abs` và `absExact`, lựa chọn xử lý thích hợp và những bẫy khi làm phép toán trước khi lấy trị tuyệt đối.

## 1. Vì sao MIN_VALUE không có trị tuyệt đối dương trong cùng kiểu?

Các kiểu số nguyên có dấu Java sử dụng biểu diễn **bù hai** (*two's complement*) với độ rộng cố định. Miền giá trị bất đối xứng quanh 0:

| Kiểu | Giá trị nhỏ nhất | Giá trị lớn nhất | Trị tuyệt đối toán học của MIN_VALUE |
| --- | ---: | ---: | ---: |
| `int` 32 bit | `-2_147_483_648` | `2_147_483_647` | `2_147_483_648` |
| `long` 64 bit | `-9_223_372_036_854_775_808` | `9_223_372_036_854_775_807` | `9_223_372_036_854_775_808` |

Với số nguyên có dấu `n` bit, miền là từ `-2^(n-1)` đến `2^(n-1)-1`. Vì vậy trị tuyệt đối của `MIN_VALUE` bằng `2^(n-1)`, **vượt `MAX_VALUE` đúng một đơn vị**.

Ví dụ bit 32:

```text
Integer.MIN_VALUE = -2147483648
Bit của int       = 10000000 00000000 00000000 00000000
Đổi dấu toán học  = +2147483648 (vượt Integer.MAX_VALUE)
Kết quả 32 bit    = 10000000 00000000 00000000 00000000
Diễn giải int     = -2147483648
```

Các phép cộng, trừ và đổi dấu số nguyên Java theo kiểu nguyên thủy có hành vi được định nghĩa trên độ rộng bit cố định. Phép đổi dấu `-Integer.MIN_VALUE` cũng trả lại `Integer.MIN_VALUE`, không ném lỗi. `Math.abs(int)` dùng cùng miền giá trị và cũng **không tự động kiểm tra tràn**.

### Giá trị “gần MIN_VALUE” vẫn xử lý bình thường

```java
int a = Integer.MIN_VALUE + 1;
System.out.println(Math.abs(a)); // 2147483647
```

Chỉ riêng giá trị `Integer.MIN_VALUE` mới không có giá trị đối dương biểu diễn được trong `int`; tương tự, chỉ `Long.MIN_VALUE` là trường hợp đó đối với `long`. Các giá trị nguyên có dấu khác, kể cả `-MAX_VALUE`, đều có trị tuyệt đối nằm trong miền kiểu của chúng.

## 2. Math.abs thực sự cam kết điều gì?

Trong Java, lớp `java.lang.Math` có các overload `abs(int)`, `abs(long)`, `abs(float)`, `abs(double)`. Với **`int` và `long`**, kết quả là độ lớn không âm của đối số trong mọi trường hợp **ngoại trừ giá trị MIN_VALUE của chính kiểu đó**, khi phương thức trả lại MIN_VALUE.

```java
int normal = Math.abs(-42);                  // 42
int zero = Math.abs(0);                      // 0
int edge = Math.abs(Integer.MIN_VALUE);      // Integer.MIN_VALUE
long longEdge = Math.abs(Long.MIN_VALUE);    // Long.MIN_VALUE
```

Các dòng này là mảnh mã Java đặt trong một phương thức. Chúng biên dịch được và không phát sinh exception do giá trị MIN_VALUE.

Điều này có hệ quả rất quan trọng: **kết quả kiểu `int` từ `Math.abs(int)` không thể được xem là bất biến “`result >= 0`”** nếu đầu vào chưa loại trừ `Integer.MIN_VALUE`. Tương tự với `long`.

Một kiểm tra như sau có thể bắt lỗi sau khi nó đã xuất hiện, nhưng không cung cấp một kết quả đúng:

```java
int magnitude = Math.abs(value);
if (magnitude < 0) {
    throw new ArithmeticException("Magnitude does not fit in int");
}
```

Thay vì tự viết lại kiểm tra này khi muốn exception, Java đã có **`Math.absExact(value)`**.

## 3. Math.absExact: phát hiện tràn thay vì trả MIN_VALUE

Java cung cấp hai overload **`Math.absExact(int)`** và **`Math.absExact(long)`**. Cả hai đều có trong Java 17. Chúng trả trị tuyệt đối khi kết quả biểu diễn được trong kiểu đầu vào, và **ném `ArithmeticException`** nếu đầu vào đúng bằng `MIN_VALUE` của kiểu đó.

```java
int a = Math.absExact(-42);       // 42
long b = Math.absExact(-42L);     // 42L

// Math.absExact(Integer.MIN_VALUE) → ArithmeticException
// Math.absExact(Long.MIN_VALUE)    → ArithmeticException
```

So sánh các lựa chọn:

| Đầu vào | `Math.abs` | `Math.absExact` |
| --- | --- | --- |
| `-42` (`int`) | `42` | `42` |
| `0` (`int`) | `0` | `0` |
| `Integer.MAX_VALUE` | `2147483647` | `2147483647` |
| `Integer.MIN_VALUE + 1` | `2147483647` | `2147483647` |
| `Integer.MIN_VALUE` | `-2147483648` | Ném `ArithmeticException` |
| `Long.MIN_VALUE` | `-9223372036854775808` | Ném `ArithmeticException` |

Dùng `absExact` khi hợp đồng phép tính cần **trả một số nguyên không âm trong cùng kiểu hoặc báo lỗi**. Ví dụ: một API yêu cầu trả `int` là độ lớn của độ lệch, và `Integer.MIN_VALUE` được coi là đầu vào không hợp lệ.

Điểm cần hiểu: `Math.absExact` **không trả được `2147483648` dưới dạng `int`**. Nó báo rằng kết quả không biểu diễn được. Nếu bài toán thực sự cần lưu trị tuyệt đối đó, hãy chọn kiểu đủ rộng trước khi tính.

### Không có Math.absExact(float) hoặc Math.absExact(double)

`Math.absExact` dành cho kiểu nguyên `int` và `long`, không phải phương thức có overload cho `float`/`double`. Đối với số dấu phẩy động, các quy tắc như `NaN`, vô cực, `+0.0`/`-0.0` khác với overflow nguyên. Không dùng một phương thức nguyên để suy diễn semantics cho floating point.

## 4. Khi cần trị tuyệt đối thật sự của Integer.MIN_VALUE: nâng kiểu trước

Giá trị `2147483648` vượt `int` nhưng **nằm trong `long`**. Vì vậy, nếu nguồn là `int` mà đầu ra được phép là `long`, chuyển sang `long` **trước khi gọi `Math.abs`**:

```java
int value = Integer.MIN_VALUE;

long correct = Math.abs((long) value);
// correct == 2147483648L
```

Không hoán đổi thứ tự:

```java
int value = Integer.MIN_VALUE;

long tooLate = (long) Math.abs(value);
// tooLate == -2147483648L: Math.abs(int) đã tính sai kỳ vọng trước khi cast.
```

Hai cách này khác nhau ở **overload được chọn**:

- `Math.abs((long) value)`: đối số là `long`, gọi `Math.abs(long)`. `-2147483648L` có trị tuyệt đối vừa trong `long`.
- `(long) Math.abs(value)`: đối số ban đầu là `int`, gọi `Math.abs(int)`; việc mở rộng lên `long` sau đó chỉ giữ nguyên giá trị âm đã nhận.

Cùng một nguyên tắc áp dụng cho đổi dấu:

```java
long correct = -(long) Integer.MIN_VALUE; // 2147483648L
long tooLate = (long) (-Integer.MIN_VALUE); // -2147483648L
```

Việc chọn overload và thứ tự chuyển kiểu quyết định tính đúng đắn, không phải chỉ nhìn kiểu của biến nhận kết quả.

### Nên chọn Math.abs hay Math.absExact sau khi nâng kiểu?

Nếu mục tiêu của một phép xử lý `int` là **trả `long` đúng tuyệt đối**, `Math.abs((long) value)` đã đủ vì mọi giá trị `int` và trị tuyệt đối của chúng đều vừa `long`. `Math.absExact((long) value)` cũng đúng, nhưng trường hợp tràn của overload `long` không thể xảy ra khi đầu vào ban đầu chắc chắn là `int`.

Nếu mục tiêu **trả `int` và fail fast khi không vừa**, dùng `Math.absExact(value)`. Chọn theo hợp đồng trả về và cách ứng dụng xử lý dữ liệu vượt giới hạn.

## 5. Với Long.MIN_VALUE, long rộng hơn không còn đủ

Trị tuyệt đối của `Long.MIN_VALUE` là `9223372036854775808`, lớn hơn `Long.MAX_VALUE` đúng một đơn vị. Không có kiểu số nguyên nguyên thủy Java rộng hơn `long`.

Có ba hướng thiết kế tùy yêu cầu:

1. **Báo lỗi tràn:** `Math.absExact(longValue)` ném `ArithmeticException` khi nhận `Long.MIN_VALUE`.
2. **Biểu diễn độ lớn chính xác:** dùng `BigInteger` và **chuyển trước khi lấy trị tuyệt đối**.
3. **Xử lý dạng unsigned theo hợp đồng bit cụ thể:** có thể cần thao tác không dấu riêng, nhưng không coi kiểu `long` có dấu thành một số dương lớn hơn `Long.MAX_VALUE`.

Ví dụ đúng với `BigInteger`:

```java
import java.math.BigInteger;

// Đặt trong một phương thức Java:
long value = Long.MIN_VALUE;

BigInteger magnitude = BigInteger.valueOf(value).abs();
// 9223372036854775808
```

`BigInteger.valueOf(value)` nhận chính giá trị `long` có dấu rồi tạo đối tượng số nguyên; `BigInteger.abs()` có thể tạo ra trị tuyệt đối vượt 64 bit. Ngược lại, phương án sau là sai về thứ tự:

```java
long value = Long.MIN_VALUE;
BigInteger wrong = BigInteger.valueOf(Math.abs(value));
// wrong vẫn là -9223372036854775808.
```

Đưa giá trị đã xử lý sai vào một kiểu lớn hơn không khôi phục được ý định toán học ban đầu.

## 6. Bẫy nguy hiểm hơn: biểu thức đã tràn trước khi gọi absExact

`Math.absExact` chỉ kiểm tra **trị tuyệt đối của giá trị nó thực sự nhận được**. Phương thức không thể biết phép cộng/trừ/nhân tạo ra đối số đã bị tràn trước đó.

Xét bài toán tính khoảng cách tuyệt đối giữa hai `int`:

```java
int a = Integer.MAX_VALUE;
int b = -Integer.MAX_VALUE;

int wrong = Math.absExact(a - b); // Trả 2, KHÔNG ném ngoại lệ!
```

Phân tích:

```text
a                     =  2147483647
b                     = -2147483647
a - b về mặt toán học =  4294967294
a - b trong int       = -2            (tràn int)
Math.absExact(-2)     =  2            (không có overflow ở bước abs)
```

Kết quả `2` có vẻ hợp lệ và không có exception, nhưng khoảng cách toán học đúng phải là `4294967294`. **`absExact` không phải công cụ tự phát hiện mọi overflow xảy ra trước lời gọi**.

Vì khoảng cách giữa bất kỳ hai `int` nào đều nằm trong miền `long`, cách đúng là **nâng kiểu trước khi trừ**:

```java
long distance = Math.abs((long) a - b);
// distance == 4294967294L
```

Có thể dùng `Math.absExact((long) a - b)` khi muốn thể hiện chính sách checked arithmetic, nhưng ở đầu vào đều là `int`, hiệu tuyệt đối vẫn nằm trong giới hạn `long`.

### Với hai long bất kỳ, BigInteger phù hợp hơn

Hiệu toán học của `Long.MAX_VALUE` và `Long.MIN_VALUE` là `18446744073709551615`, vượt `long`. Cast hai toán hạng `long` thành `long` một lần nữa không giúp ích:

```java
BigInteger distance = BigInteger.valueOf(Long.MAX_VALUE)
        .subtract(BigInteger.valueOf(Long.MIN_VALUE))
        .abs();
// 18446744073709551615
```

Nếu đặc tả yêu cầu trả `long`, phải kiểm tra miền dữ liệu trước hoặc dùng phép trừ checked như `Math.subtractExact` rồi `Math.absExact`, sẵn sàng báo lỗi khi không biểu diễn được. Nếu đặc tả yêu cầu trả khoảng cách **toán học chính xác cho mọi cặp long**, `BigInteger` là hướng đúng.

## 7. Ví dụ thực tế: lấy trị tuyệt đối của hash rồi lấy chỉ số

Một mẫu code dễ gặp là:

```java
int hash = key.hashCode();
int index = Math.abs(hash) % capacity;
```

Nếu `hash == Integer.MIN_VALUE`, `Math.abs(hash)` vẫn âm. Với `capacity = 10`, kết quả `index` là **`-8`**, không nằm trong miền chỉ số `0..9`.

```java
int hash = Integer.MIN_VALUE;
int capacity = 10;

int brokenIndex = Math.abs(hash) % capacity; // -8
```

Có thể sửa bằng cách nâng kiểu trước khi `abs`, nhưng khi mục đích thực sự chỉ là **ánh xạ số nguyên, kể cả âm, vào chu kỳ dương**, `Math.floorMod(hash, capacity)` thể hiện yêu cầu trực tiếp hơn:

```java
int hash = Integer.MIN_VALUE;
int capacity = 10;

int index = Math.floorMod(hash, capacity); // 2
```

Khi `capacity > 0`, `Math.floorMod(hash, capacity)` nằm trong `0..capacity-1` đối với mọi `int hash`. Cần kiểm tra `capacity > 0`; nếu bằng 0 sẽ không có chỉ số hợp lệ. Kết quả `2` của `floorMod` **không bắt buộc giống** `Math.abs((long) hash) % capacity` (giá trị sau là 8). Chúng thể hiện **hai chính sách ánh xạ khác nhau**; chọn chính sách theo thuật toán, tính ổn định phân phối dữ liệu và hợp đồng hiện có.

### Vì sao không dùng absExact để “cứu” công thức hash?

```java
int index = Math.absExact(hash) % capacity;
```

Công thức này phát hiện `Integer.MIN_VALUE` rồi ném exception. Điều đó có thể phù hợp nếu quy tắc của ứng dụng muốn coi hash đặc biệt là lỗi. Nhưng thông thường hash có thể là **bất kỳ int nào**, và việc có một giá trị hash hợp lệ khiến ánh xạ index thất bại không phải điều mong muốn. Trong bài toán index chu kỳ dương, `floorMod` phù hợp hơn.

Một lựa chọn khác khi thuật toán làm việc trên **bit không dấu** là mask bit dấu với `hash & Integer.MAX_VALUE`; cách này cũng tạo giá trị không âm nhưng **chính sách ánh xạ khác** với `floorMod`. Không tự ý thay đổi hàm ánh xạ trong cấu trúc dữ liệu đã có vì có thể ảnh hưởng bucket hoặc tính tương thích với dữ liệu lưu trước đó.

## 8. Ví dụ thực tế: độ lệch, khoảng cách và kiểm tra giới hạn

Giả sử một dịch vụ cần xác định khoảng cách số lượng giữa hai bộ đếm:

```java
static long absoluteDifference(int current, int previous) {
    return Math.abs((long) current - previous);
}
```

Phương thức trên an toàn cho mọi cặp `int`. Nó cho kết quả `long` từ 0 đến `4294967295`, không làm mất giá trị do tràn `int`. Tuy vậy, kiểm tra một điều kiện nghiệp vụ như “chênh lệch không vượt 1000” còn cần so sánh với ngưỡng và xử lý input hợp lệ.

Nếu phương thức bắt buộc trả `int`, phải quyết định rõ:

```java
static int absoluteDifferenceExact(int current, int previous) {
    long distance = Math.abs((long) current - previous);
    return Math.toIntExact(distance);
}
```

`Math.toIntExact` ném `ArithmeticException` khi khoảng cách vượt `Integer.MAX_VALUE`; nó báo lỗi **ở biên trả về**, không để cast làm mất thông tin.

Với `long` là kiểu đầu vào và khoảng cách cần tuyệt đối đúng, dùng `BigInteger` như phần trước.

### Trị tuyệt đối trong phép tính và biểu thức trung gian

Ba lỗi thường gặp có cấu trúc giống nhau:

```java
// 1. Lấy abs quá muộn sau phép trừ int đã tràn:
long wrongDistance = Math.abs(a - b);

// 2. Cast quá muộn, sau khi Math.abs(int) đã trả MIN_VALUE:
long wrongMagnitude = (long) Math.abs(Integer.MIN_VALUE);

// 3. Chuyển sang BigInteger sau khi Math.abs(long) đã trả MIN_VALUE:
BigInteger wrongBigMagnitude =
        BigInteger.valueOf(Math.abs(Long.MIN_VALUE));
```

Phải đặt **kiểu đủ rộng hoặc phép kiểm tra đúng tại thời điểm phép toán có thể tràn**. Chỉ kiểm tra kết quả cuối không thay thế được kiểm soát các bước trung gian.

## 9. Có nên dùng phép if/ternary thay vì Math.absExact?

Một triển khai tự viết dễ thấy:

```java
static int naiveAbs(int value) {
    return value < 0 ? -value : value;
}
```

Nó **không giải quyết** ca `Integer.MIN_VALUE`. Nhánh `-value` cũng quay lại số âm nhỏ nhất mà không ném exception. Muốn giữ `int` và phát hiện overflow, `Math.absExact` làm rõ ý định hơn một nhánh đặc biệt tự viết.

Nếu phải tự tạo thông báo lỗi nghiệp vụ khác `ArithmeticException`, có thể kiểm tra đầu vào tường minh:

```java
static int checkedMagnitude(int value) {
    if (value == Integer.MIN_VALUE) {
        throw new IllegalArgumentException("Magnitude exceeds int range");
    }
    return Math.abs(value);
}
```

Đây là lựa chọn thiết kế **khác hợp đồng** với `Math.absExact`: loại exception và cách mô tả lỗi phụ thuộc API. Không cần viết lại nó nếu yêu cầu chỉ là overflow gây `ArithmeticException`.

Nếu bài toán chấp nhận trả kết quả trong `long`, mở rộng trước khi lấy abs thường gọn và giữ được cả trường hợp biên mà không phải báo lỗi.

## 10. Chương trình Java 17+ chạy độc lập

Lưu đoạn sau thành **`AbsMinValueDemo.java`**. Chương trình minh họa `Math.abs` trả lại MIN_VALUE, `Math.absExact` báo lỗi, phép nâng kiểu đúng thời điểm, `BigInteger`, bẫy hiệu đã tràn và cách tạo chỉ số modulo không âm.

```java
import java.math.BigInteger;

public class AbsMinValueDemo {
    public static void main(String[] args) {
        int minInt = Integer.MIN_VALUE;
        long minLong = Long.MIN_VALUE;

        System.out.println("abs-int-min=" + Math.abs(minInt));
        System.out.println("abs-long-min=" + Math.abs(minLong));
        System.out.println("abs-int-near-min=" +
                Math.absExact(minInt + 1));

        try {
            Math.absExact(minInt);
        } catch (ArithmeticException ex) {
            System.out.println("abs-exact-int=overflow");
        }

        try {
            Math.absExact(minLong);
        } catch (ArithmeticException ex) {
            System.out.println("abs-exact-long=overflow");
        }

        System.out.println("widen-before-abs=" +
                Math.abs((long) minInt));
        System.out.println("widen-after-abs=" +
                (long) Math.abs(minInt));
        System.out.println("big-long-magnitude=" +
                BigInteger.valueOf(minLong).abs());

        int a = Integer.MAX_VALUE;
        int b = -Integer.MAX_VALUE;
        System.out.println("incorrect-abs-diff=" +
                Math.absExact(a - b));
        System.out.println("correct-abs-diff=" +
                Math.abs((long) a - b));

        System.out.println("naive-hash-index=" +
                (Math.abs(minInt) % 10));
        System.out.println("floor-mod-index=" +
                Math.floorMod(minInt, 10));

        BigInteger bigDifference = BigInteger.valueOf(Long.MAX_VALUE)
                .subtract(BigInteger.valueOf(Long.MIN_VALUE))
                .abs();
        System.out.println("big-long-distance=" + bigDifference);
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac AbsMinValueDemo.java
java AbsMinValueDemo
```

Kết quả stdout:

```text
abs-int-min=-2147483648
abs-long-min=-9223372036854775808
abs-int-near-min=2147483647
abs-exact-int=overflow
abs-exact-long=overflow
widen-before-abs=2147483648
widen-after-abs=-2147483648
big-long-magnitude=9223372036854775808
incorrect-abs-diff=2
correct-abs-diff=4294967294
naive-hash-index=-8
floor-mod-index=2
big-long-distance=18446744073709551615
```

**Giải thích:**

- Hai dòng `abs-*-min` cho thấy `Math.abs` không tự ném exception trên số nguyên MIN_VALUE.
- Hai dòng `abs-exact-*` chỉ xuất hiện do khối `catch` bắt exception từ `Math.absExact`.
- `widen-before-abs` giữ đúng độ lớn của `Integer.MIN_VALUE`; `widen-after-abs` không sửa được kết quả `int` đã quay vòng.
- `incorrect-abs-diff=2` đặc biệt quan trọng: `absExact` không phát hiện phép trừ đã tràn trước lời gọi. Phải mở rộng trước khi trừ.
- Chỉ số `-8` từ `Math.abs(minInt) % 10` không hợp lệ cho mảng; `Math.floorMod(minInt, 10)` trả `2`.
- Hai phép tính `BigInteger` giữ được các trị tuyệt đối vượt phạm vi `long`.

## 11. Phân biệt các lỗi dễ nhầm

| Mã hoặc quyết định | Kết quả/vấn đề | Cách dùng phù hợp |
| --- | --- | --- |
| `Math.abs(Integer.MIN_VALUE)` | Trả `Integer.MIN_VALUE`, vẫn âm | Chỉ dùng khi ca biên được xử lý hoặc miền loại trừ MIN_VALUE |
| `Math.absExact(Integer.MIN_VALUE)` | Ném `ArithmeticException` | Khi kết quả phải vừa `int` hoặc phải báo overflow |
| `Math.abs((long) Integer.MIN_VALUE)` | Trả `2147483648L` | Khi cần độ lớn chính xác và chấp nhận `long` |
| `(long) Math.abs(Integer.MIN_VALUE)` | Vẫn âm `-2147483648L` | Tránh: chuyển kiểu quá muộn |
| `Math.abs(Long.MIN_VALUE)` | Trả `Long.MIN_VALUE` | Không dùng như bảo đảm kết quả không âm |
| `Math.absExact(Long.MIN_VALUE)` | Ném `ArithmeticException` | Khi `long` là giới hạn đầu ra |
| `BigInteger.valueOf(Long.MIN_VALUE).abs()` | Trả chính xác `9223372036854775808` | Khi cần độ lớn vượt `long` |
| `Math.absExact(a - b)` với hai `int` | Biểu thức `a-b` có thể đã tràn | Mở rộng trước khi trừ: `Math.abs((long)a-b)` |
| `Math.abs(hash) % capacity` | Có thể âm nếu hash là MIN_VALUE | Với capacity dương, xét `Math.floorMod(hash, capacity)` |
| `Math.absExact(hash) % capacity` | Có thể ném lỗi trên hash hợp lệ | Chọn chính sách mapping toàn miền, không mặc định coi MIN_VALUE là lỗi |

### Một chi tiết về Java floating point

`Math.abs(float)` và `Math.abs(double)` không gặp sự bất đối xứng miền nguyên như `int`/`long`. Với floating point, `Float.MIN_VALUE` và `Double.MIN_VALUE` là **số dương nhỏ nhất khác 0** có thể biểu diễn, không phải giá trị âm lớn nhất về độ lớn. `Math.abs(Double.NaN)` vẫn là NaN, và `Math.abs(-0.0d)` trả dương 0.0.

Vì vậy, khi thấy `MIN_VALUE` trong code, phải đọc đúng **kiểu**: `Integer.MIN_VALUE`/ `Long.MIN_VALUE` là số âm cực tiểu; `Double.MIN_VALUE`/ `Float.MIN_VALUE` có ý nghĩa khác hẳn. Nếu cần kiểm tra dữ liệu đo lường, dùng `Double.isFinite`, xử lý `NaN` hoặc chính sách số thực thích hợp.

## 12. Kiểm thử và bài tập tự luyện

1. Dự đoán kết quả `Math.abs(Integer.MIN_VALUE + 1)`, `Math.abs(Integer.MIN_VALUE)` và `Math.absExact(Integer.MIN_VALUE)`. Giải thích vì sao chỉ một trường hợp không có trị tuyệt đối dương trong `int`.
2. Viết phương thức `long magnitude(int value)` trả đúng trị tuyệt đối của mọi `int`. Thử `-42`, `0`, `Integer.MAX_VALUE`, `Integer.MIN_VALUE`.
3. Viết phương thức `int magnitudeExact(int value)` dùng `Math.absExact`. Kiểm tra ngoại lệ chỉ ở `Integer.MIN_VALUE`, đồng thời xác định kiểu exception.
4. Vì sao `(long) Math.abs(Integer.MIN_VALUE)` sai còn `Math.abs((long) Integer.MIN_VALUE)` đúng? Trình bày overload được chọn tại từng bước.
5. Cho `a=Integer.MAX_VALUE`, `b=-Integer.MAX_VALUE`. Dự đoán `Math.absExact(a-b)` và `Math.abs((long)a-b)`. Trình bày chỗ xảy ra overflow.
6. Viết hàm `BigInteger magnitude(long value)` đúng với `Long.MIN_VALUE`. Giải thích vì sao không được lấy `Math.abs(value)` trước khi gọi `BigInteger.valueOf`.
7. Với `capacity=10`, tính `Math.abs(Integer.MIN_VALUE) % capacity` và `Math.floorMod(Integer.MIN_VALUE, capacity)`. Hai cách khác nhau ở chính sách nào?
8. Thiết kế API tính độ lệch giữa hai giá trị `long`, yêu cầu **chính xác với mọi cặp đầu vào**. Chọn kiểu trả về và phép toán, rồi kiểm thử `Long.MIN_VALUE` và `Long.MAX_VALUE`.
9. Viết kiểm thử chứng minh một giá trị `int` khác `Integer.MIN_VALUE` luôn có trị tuyệt đối không âm trong `int`. Cần bao phủ những nhóm giá trị biên nào?
10. Giải thích sự khác nhau về ý nghĩa giữa `Integer.MIN_VALUE` và `Double.MIN_VALUE`. Tại sao nhầm hai constant này có thể dẫn tới kiểm tra biên sai?

## 13. Checklist chọn cách lấy trị tuyệt đối

- **Đầu vào là int/long?** Nhận diện MIN_VALUE và giới hạn biểu diễn của kiểu.
- **Cần giữ nguyên kiểu và báo lỗi nếu tràn?** Dùng `Math.absExact(int/long)`; xử lý `ArithmeticException` ở tầng thích hợp.
- **Nguồn là int, cần độ lớn toán học chính xác?** Cast sang `long` **trước** `Math.abs`.
- **Nguồn là long, cần độ lớn chính xác kể cả MIN_VALUE?** Đổi sang `BigInteger` trước khi gọi `abs()`.
- **Đối số là kết quả cộng/trừ/nhân?** Kiểm tra **overflow của biểu thức trung gian**, không chỉ gọi `absExact` sau đó.
- **Mục tiêu là index/bucket trong `0..n-1`?** Cân nhắc `Math.floorMod(x,n)` với `n>0`, theo đúng chính sách ánh xạ thuật toán.
- **Giá trị có thể là số thực?** Không áp dụng quy tắc MIN_VALUE của integer sang `float`/`double`; xác định cách xử lý NaN, infinity, signed zero.
- **Kiểm thử:** thêm `MIN_VALUE`, `MIN_VALUE+1`, `-1`, `0`, `1`, `MAX_VALUE` và những biểu thức trung gian có thể tràn.

**Điểm cốt lõi:** `Math.abs` trên `int`/`long` không cam kết mọi kết quả đều không âm; giá trị MIN_VALUE là ngoại lệ do miền bù hai bất đối xứng. `Math.absExact` báo lỗi khi trị tuyệt đối không vừa kiểu; khi cần giá trị chính xác vượt miền, hãy đổi sang kiểu đủ rộng **trước khi thực hiện phép tính**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-overflow-009">Kiểm tra giới hạn trước khi cấp phát hoặc tính kích thước</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra giới hạn trước khi cấp phát hoặc tính kích thước trong Java

Một kích thước trông có vẻ hợp lệ khi được lưu trong `int` vẫn có thể trở thành **số âm, số quá nhỏ, hoặc vượt xa ngân sách bộ nhớ** sau phép nhân/cộng. Vì Java quyết định kiểu tính toán từ kiểu toán hạng, sự cố thường xảy ra **trước** lệnh cấp phát:

```java
int elements = 1_000_000_000;
int bytesPerElement = 8;

int wrongBytes = elements * bytesPerElement;  // -589934592 (int overflow)
long exactBytes = (long) elements * bytesPerElement; // 8000000000
```

Tích toán học là 8.000.000.000 byte, vượt `Integer.MAX_VALUE`. Phép `int * int` chỉ giữ kết quả trong miền 32 bit nên `wrongBytes` thành `-589934592`. Nếu kiểm tra ngân sách bằng giá trị đã tràn, ứng dụng có thể **chấp nhận sai, cấp phát sai kích thước hoặc ném ngoại lệ muộn**.

Bài này tập trung vào cách xác định kích thước cần thiết một cách chính xác, kiểm tra giới hạn và chỉ sau đó mới chuyển kiểu/cấp phát, nhất là với **mảng, buffer, dữ liệu nhị phân, chuỗi và thông tin độ dài lấy từ bên ngoài**.

## 1. Vì sao phép tính kích thước dễ tràn?

Các biểu thức sau trông đơn giản nhưng đều có rủi ro:

- `count * bytesPerItem`: tính dung lượng payload từ số phần tử và kích thước mỗi phần tử.
- `headerBytes + payloadBytes`: tính tổng kích thước gói dữ liệu.
- `offset + length`: xác định vị trí kết thúc của một lát mảng hoặc buffer.
- `capacity * growthFactor`: tăng dung lượng cấu trúc dữ liệu.
- `rows * columns` hoặc `width * height * channels`: tính số phần tử của ma trận/ảnh.
- `(count + pageSize - 1) / pageSize`: làm tròn số trang lên; biểu thức cộng có thể tràn trước phép chia.

Các toán hạng `int` có thể đều hợp lệ và không âm nhưng **kết quả trung gian không còn nằm trong miền `int`**. Tương tự, hai giá trị `long` hợp lệ vẫn có thể cho tích vượt `Long.MAX_VALUE`.

| Biểu thức | Vấn đề | Cách tiếp cận |
| --- | --- | --- |
| `int bytes = count * 8` | `int` overflow trước khi kiểm tra | `long bytes = (long) count * 8` hoặc `Math.multiplyExact` |
| `long bytes = count * 8`, `count` là `int` | Phép nhân vẫn là `int` | Nâng toán hạng **trước** dấu nhân |
| `int total = header + payload` | Tổng có thể tràn | Tính bằng `long` hoặc dùng `Math.addExact` |
| `int end = offset + length` | Vị trí kết thúc có thể tràn | Kiểm tra bằng phép trừ an toàn sau xác minh số không âm |
| `int newCapacity = capacity * 2` | Gấp đôi có thể quấn vòng thành số âm | Kiểm tra trước khi nhân, đặt giới hạn tăng trưởng |
| `(int) largeCount` | Thu hẹp `long` có thể mất bit cao | Kiểm tra miền hoặc dùng `Math.toIntExact` |

**Đừng chỉ kiểm tra kết quả cuối** nếu trước đó đã có toán tử nguyên tràn. Khi bit đã mất, một điều kiện như `if (size <= maxBytes)` không thể khôi phục kích thước đúng.

## 2. Kiểm tra đầu vào và quy tắc “tính rộng, kiểm tra, mới thu hẹp”

Một quy trình an toàn gồm các bước:

1. **Xác định đơn vị và ngữ nghĩa**: `count` là số phần tử, `bytesPerElement` là số byte một phần tử, `maxBytes` là ngân sách payload; không so sánh byte với số phần tử.
2. **Kiểm tra điều kiện đầu vào**: số phần tử và offset không âm; kích thước một đơn vị dương; giới hạn nghiệp vụ hợp lệ. Dữ liệu từ file/network/user cần được xem là chưa tin cậy.
3. **Tính bằng kiểu phù hợp và phát hiện tràn**: mở rộng lên `long` **trước phép toán `int` có nguy cơ tràn**, hoặc dùng `Math.multiplyExact` / `Math.addExact`. Nếu `long` vẫn chưa đủ, cần giới hạn miền từ trước hoặc tính bằng `BigInteger`.
4. **Đối chiếu các ngưỡng độc lập**: giới hạn số phần tử, dung lượng payload, kích thước một allocation, dung lượng tổng đang sử dụng và giới hạn cấu hình ứng dụng.
5. **Chuyển sang kiểu API đích sau khi kiểm tra**: phần lớn độ dài mảng/`ByteBuffer` nhận `int`; dùng `Math.toIntExact` khi nhận kích thước `long` cần chắc chắn không vượt miền `int`.
6. **Cấp phát và xử lý lỗi tài nguyên**: kích thước hợp lệ không bảo đảm JVM còn đủ heap hoặc có thể cấp phát một khối liên tục lớn.

Chẳng hạn, nguyên tắc đúng để tính payload của `long[]` có `n` phần tử là kiểm tra tích `n * Long.BYTES` ở miền `long` và ngân sách **trước** khi gọi `new long[length]`. `Long.BYTES` bằng 8; ngân sách payload ở đây chưa tính overhead mảng, căn chỉnh hay chi phí các object khác.

### Nâng kiểu trước phép nhân, không cast kết quả đã tràn

```java
int elements = 1_000_000_000;

long wrong = (long) (elements * 8);  // -589934592
long right = (long) elements * 8;    // 8000000000
```

Trong `wrong`, phép `int * int` đã tràn trước khi cast. Trong `right`, toán hạng `elements` được mở rộng trước nên phép `long * int` tính theo `long`. Nhưng `long` vẫn có thể tràn với đầu vào rất lớn; vì vậy **nâng kiểu và kiểm tra giới hạn là hai yêu cầu bổ sung cho nhau**.

## 3. Kiểm tra overflow bằng API `Math`

### `Math.multiplyExact` và `Math.addExact`

`Math.multiplyExact` có các overload `int` và `long`; chúng ném `ArithmeticException` nếu kết quả vượt miền của kiểu tương ứng:

```java
int count = 1_000_000_000;

// Math.multiplyExact(count, 8); // ném ArithmeticException: vượt int
long payload = Math.multiplyExact((long) count, 8L); // 8000000000

long header = 128L;
long packetSize = Math.addExact(header, payload);    // 8000000128
```

Nếu cả hai đối số `Math.multiplyExact` là `int`, phép kiểm tra diễn ra **trong miền `int`**. Muốn tích `long`, hãy chuyển một toán hạng sang `long` trước lời gọi.

Đặc biệt, **không viết**:

```java
long wrong = Math.addExact((long) (count * 8), 128L);
```

Vì `count * 8` đã tràn trong ngoặc. `Math.addExact` chỉ kiểm tra phép **cộng** nhận đầu vào của nó; nó không biết phép nhân trước đó đã sai.

### `Math.toIntExact`

Các API như `new byte[length]`, `ByteBuffer.allocate(capacity)` và các chỉ số mảng yêu cầu `int`. Khi đang có `long length`, `Math.toIntExact(length)` sẽ ném `ArithmeticException` nếu giá trị không biểu diễn được trong `int`:

```java
long elementCount = (long) Integer.MAX_VALUE + 1L;
// int unsafe = (int) elementCount;   // Có thể quấn vòng
// int safe = Math.toIntExact(elementCount); // Ném ArithmeticException
```

**Giới hạn `int` chỉ là một điều kiện cần.** Một mảng có độ dài gần `Integer.MAX_VALUE` có thể vượt giới hạn kích thước mảng thực tế của JVM hoặc không đủ bộ nhớ. Không coi `Math.toIntExact` là bằng chứng rằng allocation sẽ thành công.

### `BigInteger` khi phép tính lớn hơn `long`

Nếu phép nhân `long * long` có thể vượt 64 bit nhưng vẫn phải tính chính xác để **so sánh với quota**, `BigInteger` tránh overflow:

```java
import java.math.BigInteger;

// Mảnh mã minh họa bên trong một phương thức:
long rows = 5_000_000_000L;
long cols = 5_000_000_000L;
BigInteger cells = BigInteger.valueOf(rows)
        .multiply(BigInteger.valueOf(cols));
boolean tooLarge = cells.compareTo(BigInteger.valueOf(1_000_000L)) > 0;
// tooLarge = true
```

Nên kiểm tra trực tiếp điều kiện số lượng theo giới hạn cấu hình nếu có thể, nhằm tránh thực hiện phép toán không cần thiết với dữ liệu không đáng tin cậy.

## 4. Giới hạn của mảng và buffer trong Java

Java yêu cầu **độ dài một chiều của mảng là `int` không âm**:

```java
int n = 8;
long[] values = new long[n];  // Mảng primitive có 8 phần tử
```

Lưu ý:

- `new int[-1]` với độ dài âm lúc chạy gây `NegativeArraySizeException`.
- Một kích thước không âm nhưng quá lớn có thể gây `OutOfMemoryError`; JVM còn có giới hạn chiều dài mảng thực tế, không nên giả định có thể cấp phát đúng `Integer.MAX_VALUE` phần tử.
- Ngay cả khi payload lý thuyết nằm trong ngân sách, **object header, căn chỉnh, bộ nhớ đang dùng, tham chiếu object, bộ sưu tập và heap hiện tại** đều ảnh hưởng khả năng cấp phát.
- `ByteBuffer.allocate(int capacity)` tạo buffer trong heap, còn `ByteBuffer.allocateDirect(int capacity)` yêu cầu vùng bộ nhớ trực tiếp; chúng có điều kiện tài nguyên khác nhau. Capacity âm là không hợp lệ và phải được kiểm tra trước. Cả hai vẫn dùng `int` làm capacity.
- Một `byte[]` có `n` phần tử nghĩa là `n` byte dữ liệu; đối với `long[]`, payload sơ bộ `n * 8` byte. Với `Object[]` hoặc object graph, không thể giả định chi phí cố định chỉ từ số phần tử vì bộ nhớ cho tham chiếu và các object được tham chiếu phụ thuộc JVM và cấu trúc dữ liệu.
- Với chuỗi, **số ký tự không đồng nghĩa số byte sau mã hóa**: `String.length()` đếm đơn vị mã UTF-16; UTF-8 có độ dài biến thiên. Nếu giới hạn được tính bằng byte để gửi/ghi dữ liệu, phải tính theo kết quả mã hóa hoặc API cung cấp kích thước thực tế.
- `int[][]` là **mảng các tham chiếu đến từng mảng hàng**; hãy kiểm tra cả số hàng, số cột, tổng phần tử và chi phí nhiều mảng, không chỉ tích `rows * columns`.

`OutOfMemoryError` phản ánh thất bại tài nguyên, **không nên dùng nó như cơ chế kiểm tra kích thước bình thường**. Ứng dụng cần đặt ngưỡng chủ động trước allocation, đồng thời vẫn thừa nhận allocation có thể thất bại vì áp lực bộ nhớ.

## 5. Kiểm tra `offset + length` không gây tràn

Xử lý một lát mảng hoặc buffer thường có ba giá trị `offset`, `length`, `capacity`. Điều kiện hợp lệ:

\[
0 \leq offset,\quad 0 \leq length,\quad offset + length \leq capacity.
\]

Viết trực tiếp `offset + length <= capacity` có thể nguy hiểm nếu phép cộng `int` tràn. Chẳng hạn `offset` gần `Integer.MAX_VALUE` và `length` dương làm tổng thành số âm, khiến một điều kiện sai được chấp nhận.

Sau khi xác minh tất cả không âm, dùng phép trừ:

```java
static boolean validSlice(int offset, int length, int capacity) {
    return offset >= 0
            && length >= 0
            && capacity >= 0
            && offset <= capacity
            && length <= capacity - offset;
}
```

Đây là đoạn phương thức minh họa, không phải class độc lập. Vì Java đánh giá `&&` theo thứ tự và ngừng khi điều kiện trước sai, `capacity - offset` chỉ thực hiện sau khi đã biết `0 <= offset <= capacity`, nên không gây overflow. Nếu đang làm việc với API Java có sẵn, cũng có thể dùng `Objects.checkFromIndexSize(fromIndex, size, length)` để kiểm tra lát chỉ số; API sẽ ném `IndexOutOfBoundsException` khi không hợp lệ.

Với các phép cộng kích thước bắt buộc như `header + payload`, hãy dùng `Math.addExact` thay vì tin rằng kết quả nguyên có dấu không tràn.

## 6. Chương trình Java 17+ kiểm tra trước khi cấp phát

Ví dụ dưới đây dùng **giới hạn số phần tử của ứng dụng** và **ngân sách payload**. Hai giới hạn này phải được chọn theo thực tế, không lấy từ giá trị không tin cậy. Chương trình chỉ cấp phát một mảng nhỏ để minh họa; các trường hợp kích thước quá lớn được kiểm tra bằng phép toán, **không thử tạo mảng khổng lồ**.

Lưu vào tệp `AllocationBoundsDemo.java`:

```java
public class AllocationBoundsDemo {
    private static int checkedLength(
            long elements,
            long bytesPerElement,
            long maxElements,
            long maxPayloadBytes) {

        if (elements < 0 || bytesPerElement <= 0
                || maxElements < 0 || maxPayloadBytes < 0) {
            throw new IllegalArgumentException("invalid length or limits");
        }
        if (elements > maxElements) {
            throw new IllegalArgumentException("element limit exceeded");
        }

        long payloadBytes = Math.multiplyExact(elements, bytesPerElement);
        if (payloadBytes > maxPayloadBytes) {
            throw new IllegalArgumentException("payload budget exceeded");
        }

        return Math.toIntExact(elements);
    }

    private static boolean validSlice(int offset, int length, int capacity) {
        return offset >= 0 && length >= 0 && capacity >= 0
                && offset <= capacity && length <= capacity - offset;
    }

    public static void main(String[] args) {
        int elements = 1_000_000_000;
        System.out.println("unchecked int bytes: " + (elements * 8));
        System.out.println("exact long bytes: "
                + Math.multiplyExact((long) elements, 8));

        try {
            Math.multiplyExact(elements, 8);
        } catch (ArithmeticException e) {
            System.out.println("int product overflow detected");
        }

        int length = checkedLength(8, 8, 1_000, 1_024);
        long[] values = new long[length];
        System.out.println("allocated array length: " + values.length);

        try {
            checkedLength(200, 8, 1_000, 1_024);
        } catch (IllegalArgumentException e) {
            System.out.println("budget rejection: " + e.getMessage());
        }

        try {
            checkedLength(-1, 8, 1_000, 1_024);
        } catch (IllegalArgumentException e) {
            System.out.println("negative length rejected");
        }

        try {
            Math.multiplyExact(Long.MAX_VALUE, 2L);
        } catch (ArithmeticException e) {
            System.out.println("long product overflow detected");
        }

        try {
            Math.toIntExact((long) Integer.MAX_VALUE + 1L);
        } catch (ArithmeticException e) {
            System.out.println("array index range exceeded");
        }

        System.out.println("valid slice: " + validSlice(8, 4, 16));
        System.out.println("invalid slice: "
                + validSlice(Integer.MAX_VALUE - 1, 10, Integer.MAX_VALUE));

        try {
            int negative = -1;
            int[] unused = new int[negative];
        } catch (NegativeArraySizeException e) {
            System.out.println("negative array size exception");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac AllocationBoundsDemo.java
java AllocationBoundsDemo
```

Kết quả stdout:

```text
unchecked int bytes: -589934592
exact long bytes: 8000000000
int product overflow detected
allocated array length: 8
budget rejection: payload budget exceeded
negative length rejected
long product overflow detected
array index range exceeded
valid slice: true
invalid slice: false
negative array size exception
```

**Giải thích các kiểm tra:**

- Tích `int` bị tràn âm dù dữ liệu đầu vào hợp lệ; `long` tính được giá trị 8 tỷ.
- `Math.multiplyExact(int, int)` báo overflow. `Math.multiplyExact(long, long)` cũng báo lỗi khi tích vượt `long`.
- Hàm `checkedLength` từ chối số lượng âm, số phần tử vượt quota và payload vượt ngân sách; `Math.toIntExact` bảo đảm kết quả có thể biểu diễn dưới dạng `int`.
- Với giới hạn minh họa 1.000 phần tử và 1.024 byte payload, 8 phần tử kiểu `long` dùng 64 byte payload nên cho phép cấp phát mảng nhỏ. 200 phần tử cần 1.600 byte payload nên bị từ chối.
- `validSlice` nhận lát `[8, 12)` trong capacity 16, đồng thời từ chối lát gần `Integer.MAX_VALUE` dù phép cộng trực tiếp có thể overflow.
- Giá trị độ dài âm khiến `new int[negative]` ném `NegativeArraySizeException`; ứng dụng thực tế nên chặn điều kiện này **trước** khi gọi `new`.

Hàm này là mô hình đơn giản cho **một mảng `long[]`**. Khi triển khai, thêm ngưỡng chính sách phù hợp với ứng dụng, có thể kiểm tra ngân sách tổng thể nhiều allocation, và cân nhắc byte overhead thực tế. Không coi `maxPayloadBytes` là số byte heap chắc chắn còn trống.

## 7. Mô hình kiểm tra giới hạn cho một số tình huống

### Trường hợp A: đọc chiều dài một thông điệp nhị phân

Giả sử một gói tin có `headerBytes` và `itemCount` mục dữ liệu, mỗi mục có độ dài cố định `itemBytes`. Quy trình nên là:

1. Giới hạn `itemCount` từ đầu vào về miền được phép, kiểm tra không âm.
2. Tính `payload = Math.multiplyExact((long) itemCount, itemBytes)` sau khi xác nhận kích thước mỗi mục hợp lệ.
3. Tính `packetBytes = Math.addExact(headerBytes, payload)`.
4. So sánh `packetBytes` với giới hạn gói tin cấu hình và lượng dữ liệu thực tế được cung cấp.
5. Chỉ sau khi hợp lệ mới chuyển kiểu cho API cần `int` và cấp phát buffer.

Không tin vào trường độ dài chỉ vì nó được lưu vừa trong `int`. Một trường length có thể **tự nó hợp lệ**, nhưng gây overflow khi nhân với đơn vị hoặc cộng header.

### Trường hợp B: bảng dữ liệu/ảnh nhiều chiều

Một ảnh có `height` hàng, `width` cột và `channels` kênh. Nếu lưu mỗi mẫu bằng một byte, số mẫu là `height * width * channels`. Nên kiểm tra:

- `height`, `width`, `channels` dương và nằm trong giới hạn định dạng.
- Tích trung gian không overflow; dùng `Math.multiplyExact` từ đầu với kiểu `long`.
- Tổng số byte nằm trong quota.
- Loại array/buffer định cấp phát đáp ứng số phần tử và giới hạn JVM; một mảng đơn lớn có thể không phù hợp, cần chia thành các khối nhỏ nếu ứng dụng cho phép.

Nếu dữ liệu là ảnh có stride, padding hoặc cấu trúc lồng nhau, `height * width * channels` mới là ước lượng payload thô; phép tính cấp phát phải phù hợp layout thật.

### Trường hợp C: tăng capacity

Một cấu trúc lưu trữ có thể thử `newCapacity = oldCapacity * 2`. Khi `oldCapacity` lớn, phép nhân `int` dễ tràn. Hãy giới hạn mục tiêu tối đa và kiểm tra trước phép nhân:

```java
int oldCapacity = 800;
int maximum = 1_000;
int newCapacity = oldCapacity > maximum / 2
        ? maximum
        : oldCapacity * 2; // 1000, không vượt maximum
```

Đây là ví dụ **khi `oldCapacity >= 0` và `maximum > 0` đã được xác minh**. Một chiến lược tăng trưởng thực tế phải bảo đảm `newCapacity >= minRequiredCapacity`; nếu không đáp ứng được, từ chối yêu cầu. Không tự động xem việc “kẹp” về maximum là thành công nếu dữ liệu vẫn cần nhiều hơn maximum.

### Trường hợp D: phân trang và chia nhóm

Với `count >= 0`, `pageSize > 0`, công thức thường gặp `(count + pageSize - 1) / pageSize` có thể overflow khi `count` gần `Integer.MAX_VALUE`. Thay bằng:

```java
int count = Integer.MAX_VALUE;
int pageSize = 1000;
int pages = count / pageSize + (count % pageSize == 0 ? 0 : 1);
```

Cách này không cộng trước phép chia và giữ số trang trong miền `int` khi `count` không âm, `pageSize` dương. Với số lượng tính bằng `long` hoặc yêu cầu phân trang rất lớn, vẫn phải xét riêng giới hạn của miền đó.

## 8. Các lớp lỗi cần phân biệt

| Vấn đề | Ví dụ | Hướng xử lý |
| --- | --- | --- |
| **Overflow số học** | `int count * 8` trả số âm | Nâng kiểu trước phép toán, `Math.multiplyExact` |
| **Narrowing mất dữ liệu** | `(int) longLength` với độ dài quá lớn | Kiểm tra miền, `Math.toIntExact` |
| **Length âm** | `new byte[-1]` | Kiểm tra đầu vào, `NegativeArraySizeException` là triệu chứng |
| **Vượt giới hạn JVM / thiếu bộ nhớ** | `new long[veryLarge]` | Quota thực tế, thiết kế streaming/chunking, không dựa vào `OutOfMemoryError` để validate |
| **Sai đơn vị** | So `elementCount` với quota tính bằng byte | Nêu rõ byte/element/character và đổi đơn vị đúng |
| **Sai phép kiểm tra slice** | `offset + length <= capacity` sau khi tổng tràn | Kiểm tra số không âm rồi dùng `length <= capacity - offset` |
| **Vượt quota tích lũy** | Nhiều mảng nhỏ cùng tồn tại | Theo dõi tổng sử dụng, giới hạn đồng thời và áp lực bộ nhớ |
| **Tin trường length do client đưa** | File/network báo độ dài quá lớn | Kiểm tra quota trước đọc toàn bộ và trước allocation |

## 9. Những nhầm lẫn thường gặp

1. **“Tất cả toán hạng đều không âm thì kích thước cũng không âm.”** Sai: phép nhân/cộng `int` có thể overflow sang giá trị âm.
2. **“Đích là `long` nên `int * int` tự động dùng 64 bit.”** Sai: phải chuyển toán hạng sang `long` **trước khi nhân**.
3. **“`(long) (count * unitSize)` sửa overflow.”** Sai: phép nhân `int` đã diễn ra.
4. **“`Math.multiplyExact(count, 8)` tự trả `long` nếu kết quả lớn.”** Sai: overload `int, int` ném lỗi khi vượt `int`; muốn kết quả `long` thì nâng toán hạng.
5. **“Có `long` thì không thể tràn.”** Sai: `long * long` vẫn có thể vượt 64 bit.
6. **“`Math.toIntExact` thành công thì cấp phát sẽ thành công.”** Sai: giới hạn JVM, heap, pressure, layout và quota vẫn áp dụng.
7. **“`new byte[negativeSize]` sẽ tự sửa thành mảng rỗng.”** Sai: ném `NegativeArraySizeException`.
8. **“`offset + length <= capacity` an toàn với mọi số nguyên.”** Sai: phép cộng có thể overflow; kiểm tra bằng phép trừ sau xác minh số không âm.
9. **“Giới hạn payload bằng số byte là đủ để giới hạn heap.”** Sai: còn overhead, alignment, object graph, tham chiếu và allocation đồng thời.
10. **“`String.length()` là số byte UTF-8.”** Sai: `String.length()` đếm đơn vị mã UTF-16, byte mã hóa thay đổi theo nội dung.
11. **“Thấy `OutOfMemoryError` rồi bắt lỗi là đủ để validate.”** Sai: lúc đó JVM có thể đã thiếu tài nguyên nghiêm trọng; cần **kiểm tra, giới hạn và thiết kế theo luồng** từ đầu.
12. **“Chỉ cần kiểm tra một lần ở lúc cấp phát.”** Chưa đủ: còn công thức cộng/nhân khi tăng capacity và việc giữ nhiều allocation đồng thời.

## 10. Tự kiểm tra

- **`int count = 1_000_000_000; long bytes = count * 8;` đúng 8 tỷ không?** Không. Phép nhân `int` tràn trước khi gán. Viết `(long) count * 8` hoặc `Math.multiplyExact((long) count, 8L)`.
- **Có phải mọi `long` hợp lệ đều chuyển thành `int` hợp lệ?** Không; `Math.toIntExact` báo lỗi khi giá trị ngoài miền 32 bit.
- **`Math.multiplyExact(Long.MAX_VALUE, 2L)` làm gì?** Ném `ArithmeticException` vì tích vượt phạm vi `long`.
- **Vì sao kiểm tra `offset <= capacity` và `length <= capacity - offset` tốt hơn cộng trực tiếp?** Với điều kiện không âm phù hợp, không tính một tổng có thể overflow.
- **Payload lý thuyết của 200 phần tử `long` là bao nhiêu?** `200 * 8 = 1600` byte, chưa tính overhead mảng.
- **Nếu byte quota là 1024 mà cần 1600 byte payload?** Từ chối **trước khi cấp phát**; không chờ `OutOfMemoryError`.
- **Vì sao `Integer.MAX_VALUE` không phải giới hạn mảng chắc chắn cấp phát được?** Khả năng cấp phát còn tùy JVM, bộ nhớ và overhead; giới hạn của ứng dụng thường phải nhỏ hơn.

**Ghi nhớ:** Khi tính kích thước, **xác nhận đầu vào → tính bằng kiểu đủ rộng và kiểm tra overflow → so với quota → chuyển về kiểu API → mới cấp phát**. Đừng dùng kích thước đã bị tràn để đánh giá an toàn. Kiểu `long` giúp tránh nhiều overflow `int`, còn `Math.*Exact`, giới hạn ứng dụng và kiểm tra tài nguyên giúp quản lý các trường hợp `long` vẫn chưa đủ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-overflow-010">Dùng BigInteger khi miền giá trị không có giới hạn phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng BigInteger khi miền giá trị không có giới hạn phù hợp

> **Java core – numbers · Tràn và kiểm tra biên**  
> Mục tiêu: nhận biết lúc `int`/`long` không đáp ứng miền dữ liệu, chuyển sang `BigInteger` trước khi tràn, tính toán số nguyên chính xác, chuyển đổi an toàn và kiểm soát rủi ro bộ nhớ/hiệu năng.

## 1. Khi nào `long` là chưa đủ?

Các kiểu nguyên primitive của Java có **miền giá trị cố định**:

| Kiểu | Miền có dấu | Độ rộng |
| --- | --- | --- |
| `byte` | −128 đến 127 | 8 bit |
| `short` | −32.768 đến 32.767 | 16 bit |
| `int` | −2.147.483.648 đến 2.147.483.647 | 32 bit |
| `long` | −9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 | 64 bit |

Khi tính tổng, tích hoặc lũy thừa vượt giới hạn, phép số học nguyên `int`/`long` thông thường không tự mở rộng số bit. Kết quả có thể **tràn (overflow)** và quay vòng theo biểu diễn bù hai.

Ví dụ:

```java
long max = Long.MAX_VALUE;
long overflowed = max + 1;
System.out.println(overflowed); // -9223372036854775808
```

Kết quả toán học mong muốn là **9.223.372.036.854.775.808**, nhưng giá trị này lớn hơn `Long.MAX_VALUE`. Nếu nghiệp vụ cần giữ đúng con số đó, `long` không còn phù hợp.

**`java.math.BigInteger` là lựa chọn để lưu và tính số nguyên chính xác vượt miền của các primitive.** “Độ chính xác tùy ý” (*arbitrary precision*) nghĩa là số bit có thể tăng theo độ lớn của số, thay vì bị chốt ở 32 hoặc 64 bit. Điều đó **không có nghĩa là vô hạn về tài nguyên**: dung lượng bộ nhớ, thời gian xử lý và giới hạn triển khai vẫn hữu hạn.

## 2. Đừng chờ tràn rồi mới chuyển sang BigInteger

Sai lầm hay gặp nhất là tính bằng `long` trước, sau đó mới “cứu” bằng cách tạo `BigInteger`:

```java
long a = Long.MAX_VALUE;
long bad = a + 1;                      // đã tràn
BigInteger tooLate = BigInteger.valueOf(bad); // giữ giá trị âm đã sai
```

Phải **chuyển trước phép toán có nguy cơ tràn**:

```java
import java.math.BigInteger;

long a = Long.MAX_VALUE;
BigInteger correct = BigInteger.valueOf(a).add(BigInteger.ONE);
System.out.println(correct); // 9223372036854775808
```

Với phép nhân, quy tắc hoàn toàn tương tự:

```java
long x = 5_000_000_000L;
long y = 5_000_000_000L;

// long bad = x * y; // sẽ tràn long
BigInteger product = BigInteger.valueOf(x)
        .multiply(BigInteger.valueOf(y));
// product = 25000000000000000000
```

Tránh `new BigInteger(String.valueOf(x * y))`: phần `x * y` đã chạy bằng `long` và có thể tràn. Chuyển **toán hạng** trước, không chuyển **kết quả đã hỏng**.

Nếu yêu cầu là “vẫn dùng primitive nhưng phải biết khi nào tràn”, dùng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact` và `Math.negateExact` với `int`/`long`; các phương thức này ném `ArithmeticException` khi phép toán không vừa kiểu đích. Chúng **phát hiện tràn** chứ không tạo thêm độ rộng bit.

## 3. Chọn BigInteger dựa trên miền giá trị và yêu cầu nghiệp vụ

Các tình huống thường đáng dùng `BigInteger`:

- **Bộ đếm, lượng giao dịch hoặc số lượng tổ hợp** có miền tăng trưởng mà `long` không đủ để cam kết an toàn.
- **Giai thừa, tổ hợp, lũy thừa nguyên**: giá trị tăng rất nhanh, ngay cả khi đầu vào không lớn.
- **Thuật toán số học nguyên** cần phép chia lấy thương, GCD, phần dư, lũy thừa modulo hoặc các số lớn.
- **Giá trị nguyên từ hệ thống bên ngoài** dài hơn 64 bit cần giữ nguyên mọi chữ số.
- **Bài toán miền giá trị mở**: chưa có cận trên khả dụng hoặc giới hạn kinh doanh không đủ để bảo đảm `long` an toàn.

Không nên thay mọi `int` và `long` bằng `BigInteger` theo thói quen. Một chỉ số mảng, số thứ tự nhỏ, timestamp ở miền đã xác định hay bộ đếm bảo đảm không vượt `long` thường phù hợp với primitive hơn. Ngược lại, **không chọn kiểu từ giá trị mẫu hiện tại**: cần xét giá trị lớn nhất có thể xuất hiện khi hệ thống vận hành thực tế.

**Ví dụ từ giai thừa:**

```text
20! = 2432902008176640000    (còn vừa long)
21! = 51090942171709440000   (vượt Long.MAX_VALUE)
```

Đây là bằng chứng rõ ràng rằng `long` không thích hợp nếu chương trình cần tính giai thừa cho `n >= 21` và giữ kết quả chính xác. Chọn `BigInteger` ngay từ đầu vòng lặp:

```java
BigInteger result = BigInteger.ONE;
for (int i = 2; i <= 21; i++) {
    result = result.multiply(BigInteger.valueOf(i));
}
System.out.println(result); // 51090942171709440000
```

Lưu ý `BigInteger` không tự ngăn chương trình nhận đầu vào quá lớn như hàng triệu chữ số hay mũ khổng lồ. Bên cạnh chọn kiểu đúng vẫn phải có **giới hạn đầu vào** và **ngân sách tài nguyên**.

## 4. Khởi tạo BigInteger đúng cách

`BigInteger` thuộc gói `java.math`, là lớp bất biến (*immutable*) kế thừa `Number`. Nó không có cú pháp literal riêng như `123L`.

### 4.1. Từ long đã nằm trong miền

```java
BigInteger small = BigInteger.valueOf(123L);
BigInteger negative = BigInteger.valueOf(-45L);
BigInteger zero = BigInteger.ZERO;
BigInteger one = BigInteger.ONE;
BigInteger ten = BigInteger.TEN;
```

Trong Java 17+ còn có `BigInteger.TWO`. Dùng các hằng chuẩn này khi phù hợp, tránh tạo lại đối tượng không cần thiết.

### 4.2. Từ chuỗi thập phân hoặc cơ số khác

Khi giá trị vượt miền `long`, cách dễ hiểu là truyền **chuỗi đầy đủ các chữ số**:

```java
BigInteger huge = new BigInteger("9223372036854775808");
BigInteger belowZero = new BigInteger("-999999999999999999999");
BigInteger hex = new BigInteger("FF", 16); // 255
BigInteger binary = new BigInteger("1010", 2); // 10
```

Constructor `new BigInteger(String, radix)` chấp nhận cơ số từ 2 đến 36. **Không** truyền tiền tố `0x` khi parse chuỗi cơ số 16 kiểu này:

```java
BigInteger hex = new BigInteger("FF", 16);   // đúng
// new BigInteger("0xFF", 16);               // NumberFormatException
```

Chuỗi không hợp lệ ném `NumberFormatException`; dữ liệu từ người dùng phải được validate và giới hạn độ dài trước khi parse.

### 4.3. Sai lầm: dùng literal long vượt miền

```java
// Không biên dịch: literal số nguyên này không vừa long
// BigInteger bad = BigInteger.valueOf(9223372036854775808L);

BigInteger good = new BigInteger("9223372036854775808");
```

Dấu phân cách `_` được cho phép trong **numeric literals của mã Java**, nhưng không được hiểu như dấu phân cách trong chuỗi thập phân đưa vào `new BigInteger("...")`.

## 5. BigInteger là immutable: phương thức trả về đối tượng mới

Khác với primitive có toán tử `+`, `-`, `*`, `/`, các phép tính thông thường trên `BigInteger` được gọi bằng **phương thức**:

| Nhu cầu | Phương thức `BigInteger` |
| --- | --- |
| Cộng | `a.add(b)` |
| Trừ | `a.subtract(b)` |
| Nhân | `a.multiply(b)` |
| Chia lấy thương nguyên | `a.divide(b)` |
| Chia lấy thương và số dư | `a.divideAndRemainder(b)` |
| Số dư theo phép chia nguyên | `a.remainder(b)` |
| Modulo với môđun dương | `a.mod(m)` |
| Đối dấu | `a.negate()` |
| Trị tuyệt đối | `a.abs()` |
| Lũy thừa nguyên | `a.pow(exponent)` |
| Ước số chung lớn nhất | `a.gcd(b)` |
| Dịch bit trái/phải | `a.shiftLeft(n)`, `a.shiftRight(n)` |

Vì **không thể sửa trực tiếp** đối tượng sau khi tạo, phải giữ giá trị trả về:

```java
BigInteger count = BigInteger.TEN;
count.add(BigInteger.ONE);         // trả về 11 nhưng bị bỏ
System.out.println(count);       // 10

count = count.add(BigInteger.ONE);
System.out.println(count);       // 11
```

Không thể viết `count++` hoặc `count += BigInteger.ONE`: Java không overload các toán tử số học cho `BigInteger`. Trong các phép tính liên tiếp, mã cần lưu kết quả một cách tường minh.

## 6. Phép chia và số dư: đặc biệt chú ý số âm

`BigInteger.divide()` trả **thương nguyên làm tròn về 0**, giống phép chia nguyên primitive của Java. Nếu số chia bằng 0, `ArithmeticException` được ném.

```java
BigInteger a = BigInteger.valueOf(-17);
BigInteger b = BigInteger.valueOf(5);

System.out.println(a.divide(b));    // -3
System.out.println(a.remainder(b)); // -2
System.out.println(a.mod(b));       // 3
```

Sự khác nhau giữa `remainder` và `mod`:

- `a.remainder(b)` có số dư theo phép chia lấy thương hướng về 0; số dư khác 0 có **cùng dấu với số bị chia**.
- `a.mod(m)` yêu cầu **môđun dương** và trả phần dư **không âm** từ 0 đến `m - 1`.
- Dùng `divideAndRemainder(b)` khi cần cả thương và số dư cùng một lần tính.

```java
BigInteger[] qr = a.divideAndRemainder(b);
System.out.println(qr[0]); // -3
System.out.println(qr[1]); // -2
```

Chọn quy tắc số dư phù hợp bài toán. Với vòng đệm chỉ số tuần hoàn hoặc số học modulo, `mod(positiveModulus)` thường hợp lý hơn `remainder` khi số đầu vào có thể âm.

## 7. So sánh, dấu, và chuyển đổi chính xác

### 7.1. Không dùng toán tử so sánh primitive cho BigInteger

```java
BigInteger a = new BigInteger("100000000000000000000");
BigInteger b = new BigInteger("99999999999999999999");

System.out.println(a.compareTo(b) > 0); // true
System.out.println(a.equals(b));        // false
```

`compareTo` trả giá trị âm, 0 hoặc dương tùy `a < b`, `a == b` hay `a > b`. `equals` so sánh giá trị số nguyên của hai `BigInteger`; khác với `BigDecimal`, `BigInteger` **không có scale** làm ảnh hưởng `equals`.

- `signum()`: trả `-1`, `0`, `1` tương ứng âm, bằng 0, dương.
- `min` và `max`: chọn giá trị nhỏ/lớn theo thứ tự.
- `bitLength()`: hỗ trợ ước lượng độ lớn nhị phân; đây không phải số chữ số thập phân.

Nếu có tham chiếu có thể `null`, xử lý `null` trước khi gọi các phương thức.

### 7.2. Phân biệt chuyển đổi mất dữ liệu và exact

Các phương thức kế thừa từ `Number`, như `intValue()` và `longValue()`, **không bảo đảm giữ nguyên giá trị** nếu giá trị vượt miền. Khi chuyển sang `long`, những bit cao có thể bị mất.

```java
BigInteger overLong = new BigInteger("9223372036854775808");
System.out.println(overLong.longValue()); // -9223372036854775808

// long bad = overLong.longValueExact(); // ném ArithmeticException
```

Nếu dữ liệu phải chính xác, ưu tiên `intValueExact()`, `longValueExact()`, `shortValueExact()`, `byteValueExact()`. Chúng ném `ArithmeticException` nếu giá trị không thể biểu diễn chính xác trong kiểu đích.

Các kiểm tra nên thực hiện tại **ranh giới API**: chuyển đối tượng BigInteger sang kiểu của database/JSON/SDK bên ngoài, trả kết quả cho module dùng `long`, hoặc ghi vào giao thức có giới hạn số bit. Dữ liệu đúng bên trong `BigInteger` vẫn có thể sai sau khi chuyển sang kiểu hẹp.

## 8. Tính toán với số lớn: tổ hợp và lũy thừa

`BigInteger` hữu ích khi số chữ số của kết quả tăng nhanh.

### 8.1. Lũy thừa nguyên

```java
BigInteger power = BigInteger.TWO.pow(100);
System.out.println(power);
// 1267650600228229401496703205376
```

`pow(exponent)` nhận số mũ kiểu `int` không âm. Nó tạo ra **số nguyên chính xác** nếu hoàn thành thành công, nhưng có thể dùng rất nhiều bộ nhớ với số mũ lớn. Các dạng `BigInteger` không biểu diễn phần thập phân: phép `divide` là chia nguyên, không phải chia chính xác cho một số hữu tỉ bất kỳ.

### 8.2. Đếm tổ hợp

Công thức `n!` tăng nhanh. Với hệ thống cần `n!`, `C(n,k)` hoặc các số đếm tổ hợp, các phép nhân trung gian cũng có thể vượt `long`.

Ví dụ cách tính `C(n,k)` mà không tạo ba giai thừa rất lớn:

```java
import java.math.BigInteger;

public static BigInteger choose(int n, int k) {
    if (n < 0 || k < 0 || k > n) {
        throw new IllegalArgumentException("Require 0 <= k <= n");
    }

    k = Math.min(k, n - k);
    BigInteger result = BigInteger.ONE;
    for (int i = 1; i <= k; i++) {
        // Mỗi bước chia hết theo tính chất của hệ số tổ hợp.
        result = result.multiply(BigInteger.valueOf((long) n - k + i))
                       .divide(BigInteger.valueOf(i));
    }
    return result;
}
```

Đây là **phương thức minh họa** cần đặt trong một lớp Java để chạy; nó không phải chương trình hoàn chỉnh. Kiểu `long` ở `(long) n - k + i` giúp phép tính tạo thừa số tránh tràn `int` trong các giá trị biên. Mỗi bước là số nguyên vì tích lũy công thức tổ hợp được chia hết cho `i`.

## 9. Kiểm tra overflow trước hay dùng BigInteger từ đầu?

Một số hệ thống đặt yêu cầu khác nhau:

| Yêu cầu | Hướng giải quyết |
| --- | --- |
| Biết chắc miền trong `int`/`long` | Dùng primitive, kiểm tra đầu vào nếu cần |
| Cần giữ primitive nhưng phải chặn phép toán vượt miền | Dùng `Math.*Exact`; xử lý `ArithmeticException` |
| Phép toán có thể vượt `long`, kết quả phải là số nguyên chính xác | Chuyển các toán hạng sang `BigInteger` **trước** phép tính |
| Kết quả lớn, nhưng cổng xuất chỉ nhận `long` | Giữ `BigInteger` ở lõi, kiểm tra biên/ `longValueExact()` tại cổng xuất |
| Số liệu có phần thập phân và cần làm tròn theo quy tắc nghiệp vụ | Dùng `BigDecimal`, không dùng `BigInteger` trực tiếp |

Ví dụ một phép cộng đòi **báo lỗi** khi vượt `long`:

```java
long total = Math.addExact(current, incoming);
```

Nếu cùng phép cộng đòi **lưu lại kết quả chính xác dù vượt `long`**:

```java
BigInteger total = BigInteger.valueOf(current)
        .add(BigInteger.valueOf(incoming));
```

Chọn theo **hợp đồng dữ liệu**. Đừng nuốt ngoại lệ từ `Math.addExact` rồi âm thầm ghi giá trị đã quay vòng; cũng đừng tự tiện trả một chuỗi BigInteger cho API vốn bắt buộc kiểu 64 bit mà chưa định nghĩa cách trao đổi giá trị.

## 10. BigInteger và biểu diễn nhị phân có dấu/không dấu

`BigInteger` là số nguyên **có dấu**. Bạn có thể parse chuỗi thập phân dương rất lớn, nhưng khi đọc **mảng byte**, cách diễn giải dấu phải được chọn rõ.

Ví dụ hai byte `FF 00`:

```java
byte[] bytes = {(byte) 0xFF, 0x00};

BigInteger signed = new BigInteger(bytes);
BigInteger unsigned = new BigInteger(1, bytes);

System.out.println(signed);   // -256
System.out.println(unsigned); // 65280
```

Constructor `new BigInteger(byte[])` xem các byte theo bù hai, thứ tự byte lớn trước (*big-endian*). Constructor `new BigInteger(signum, magnitude)` dùng một dấu riêng và phần độ lớn không dấu. **Không mặc định mọi byte[] là số dương** nếu byte cao nhất có bit dấu 1.

Khi xuất byte bằng `toByteArray()`, kết quả là biểu diễn bù hai theo thứ tự big-endian với số byte đủ để bảo toàn dấu; một số dương có bit cao nhất bằng 1 có thể cần byte `00` dẫn đầu. Nếu giao thức đòi chiều dài byte cố định hoặc unsigned, phải kiểm tra độ dài, dấu và padding trước khi ghi.

Với một mẫu 64 bit muốn diễn giải theo **unsigned long**:

```java
long bits = -1L; // 64 bit đều là 1
BigInteger unsigned64 =
        new BigInteger(Long.toUnsignedString(bits));
System.out.println(unsigned64); // 18446744073709551615
```

`BigInteger.valueOf(bits)` sẽ là `-1` vì nó giữ **giá trị signed long**, không đổi sang unsigned. Dùng `Long.toUnsignedString` (hoặc chuyển bằng xử lý bit đã kiểm chứng) khi đặc tả nguồn định nghĩa 64 bit không dấu.

## 11. Bộ nhớ, hiệu năng và giới hạn an toàn

Mỗi `BigInteger` là một đối tượng, chứa dữ liệu cho độ lớn số nguyên. Số càng nhiều bit thì các phép toán càng tiêu tốn thời gian và bộ nhớ. Phép nhân các số rất lớn, `pow` với mũ lớn, chuyển đổi sang chuỗi thập phân hoặc parse hàng triệu chữ số có thể tốn nhiều tài nguyên.

**Các nguyên tắc thực hành:**

1. Giới hạn độ dài chuỗi đầu vào, miền của số mũ, số vòng lặp và số chữ số kết quả **theo yêu cầu sản phẩm**.
2. Nếu cần số nguyên có nhiều chữ số nhưng lại phải chạy trong luồng xử lý thời gian thực, đo hiệu năng với cỡ dữ liệu thực tế.
3. Tránh tạo chuỗi kết quả cực dài chỉ để log; giới hạn log và kiểm tra thông tin cần thiết.
4. Với tính toán số lượng lớn, định rõ yêu cầu thời gian và bộ nhớ; không giả định `BigInteger` bảo đảm hoàn thành cho mọi giá trị.
5. Không dùng `BigInteger` làm phương án giải quyết bài toán cần **số thực**: `divide` vẫn là chia nguyên. Với tiền và số thập phân chính xác cần scale/rounding, chọn `BigDecimal`.

Đừng áp dụng “`BigInteger` luôn tốt hơn vì không tràn” cho mọi trường hợp. Nó giúp **giữ đúng giá trị nguyên** khi miền lớn nhưng cần thiết kế giới hạn chi phí rõ ràng.

## 12. Chương trình Java 17+ chạy độc lập

Tạo file **`BigIntegerOverflowDemo.java`**:

```java
import java.math.BigInteger;

public class BigIntegerOverflowDemo {
    public static void main(String[] args) {
        long max = Long.MAX_VALUE;
        System.out.println("long overflow = " + (max + 1));

        BigInteger safe = BigInteger.valueOf(max).add(BigInteger.ONE);
        System.out.println("long max + 1 = " + safe);

        BigInteger factorial = BigInteger.ONE;
        for (int i = 2; i <= 21; i++) {
            factorial = factorial.multiply(BigInteger.valueOf(i));
        }
        System.out.println("21! = " + factorial);
        System.out.println("21! exceeds long = "
                + (factorial.compareTo(BigInteger.valueOf(max)) > 0));

        try {
            factorial.longValueExact();
            System.out.println("21! fits long");
        } catch (ArithmeticException ex) {
            System.out.println("21! cannot fit in long");
        }

        BigInteger a = BigInteger.valueOf(-17);
        BigInteger b = BigInteger.valueOf(5);
        System.out.println("-17 / 5 = " + a.divide(b));
        System.out.println("-17 remainder 5 = " + a.remainder(b));
        System.out.println("-17 mod 5 = " + a.mod(b));

        byte[] bytes = {(byte) 0xFF, 0x00};
        System.out.println("FF00 signed = " + new BigInteger(bytes));
        System.out.println("FF00 unsigned = " + new BigInteger(1, bytes));

        BigInteger original = BigInteger.TEN;
        BigInteger changed = original.add(BigInteger.ONE);
        System.out.println("immutable: " + original + ", " + changed);
    }
}
```

Biên dịch và chạy tại thư mục chứa file:

```bash
javac BigIntegerOverflowDemo.java
java BigIntegerOverflowDemo
```

**Kết quả chuẩn:**

```text
long overflow = -9223372036854775808
long max + 1 = 9223372036854775808
21! = 51090942171709440000
21! exceeds long = true
21! cannot fit in long
-17 / 5 = -3
-17 remainder 5 = -2
-17 mod 5 = 3
FF00 signed = -256
FF00 unsigned = 65280
immutable: 10, 11
```

Chương trình cho thấy điểm khác biệt giữa **tràn primitive** và **tính số nguyên chính xác**, giữa **chuyển kiểu có thể mất dữ liệu** và **chuyển kiểu exact**, giữa **remainder** và **mod**, cũng như ý nghĩa dấu của byte và tính bất biến của `BigInteger`.

## 13. Các lỗi phổ biến

| Sai lầm | Hệ quả | Cách đúng |
| --- | --- | --- |
| Tính `long * long` rồi mới bọc vào BigInteger | Tích có thể đã tràn trước khi bọc | Chuyển toán hạng bằng `valueOf` trước `multiply` |
| Gõ số vượt `long` như literal có hậu tố `L` | Không biên dịch | Dùng `new BigInteger("...")` |
| Viết `value + BigInteger.ONE` | Không có overload toán tử `+` cho BigInteger | Dùng `value.add(BigInteger.ONE)` |
| Gọi `count.add(ONE)` mà không gán | Đối tượng cũ không đổi | `count = count.add(ONE)` |
| Dùng `longValue()` khi cần chính xác | Có thể mất bit cao và tạo số âm ngoài ý muốn | Dùng `longValueExact()` |
| Dùng `==` để so sánh hai BigInteger | So sánh tham chiếu, không phải giá trị | Dùng `equals` hoặc `compareTo` |
| Nghĩ `divide` giữ cả phần thập phân | Thương nguyên cắt phần lẻ | Dùng BigDecimal nếu thực sự cần số thập phân |
| Nghĩ `remainder` luôn không âm | Với số bị chia âm, phần dư có thể âm | Xác định rõ cần `remainder` hay `mod` |
| Đọc byte có bit cao là 1 mà mặc định unsigned | Giá trị có thể thành số âm | Xác định signedness, dùng constructor phù hợp |
| Tin rằng độ chính xác tùy ý là tài nguyên vô hạn | Có thể hết bộ nhớ, tốn CPU | Giới hạn đầu vào/mũ/đầu ra theo hệ thống |

## 14. Bài tập và lời giải

**Bài 1.** Vì sao `BigInteger.valueOf(Long.MAX_VALUE + 1)` **không** phải cách tính đúng `Long.MAX_VALUE + 1`?  
**Lời giải:** phép cộng chạy theo `long`, đã tràn thành `Long.MIN_VALUE`. Cách đúng: `BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)`.

**Bài 2.** Giai thừa `20!` và `21!` có vừa `long` không?  
**Lời giải:** `20! = 2432902008176640000` còn vừa; `21! = 51090942171709440000` vượt `Long.MAX_VALUE`. Chọn `BigInteger` nếu cần `21!` chính xác.

**Bài 3.** `new BigInteger("9223372036854775808").longValueExact()` làm gì?  
**Lời giải:** ném `ArithmeticException` vì số lớn hơn miền dương của `long`. Gọi `longValue()` lại có thể trả giá trị âm sai với ý định, nên `Exact` phù hợp khi hợp đồng yêu cầu giữ nguyên số.

**Bài 4.** Với `a = -17` và `b = 5`, `a.remainder(b)` và `a.mod(b)` bằng mấy?  
**Lời giải:** lần lượt **−2** và **3**; `mod` yêu cầu môđun dương và kết quả không âm.

**Bài 5.** Có thể dùng `BigInteger` thay `BigDecimal` để cộng chính xác `0.10 + 0.20` trực tiếp không?  
**Lời giải:** không theo nghĩa số thập phân trực tiếp; `BigInteger` biểu diễn **số nguyên**. Có thể quy đổi sang đơn vị nguyên nhỏ nhất nếu nghiệp vụ đã xác định thang đo, nhưng nếu cần phép tính thập phân có scale và rounding, sử dụng `BigDecimal` thường rõ ràng hơn.

**Bài 6.** Hai byte `FF 00` được đọc bằng `new BigInteger(bytes)` và `new BigInteger(1, bytes)` cho giá trị nào?  
**Lời giải:** lần lượt **−256** và **65280**; constructor đầu dùng bù hai có dấu, constructor sau đặt dấu dương và diễn giải magnitude không dấu.

**Bài 7.** Một hệ thống lưu số lượng sản phẩm tối đa 1 triệu và không thực hiện phép nhân gây tràn. Có cần `BigInteger` không?  
**Lời giải:** thường không; `int` có miền đủ lớn. Tính đúng phụ thuộc cả phép tính trung gian và ràng buộc dữ liệu, nên cần kiểm tra tổng/tích nếu nghiệp vụ sử dụng chúng.

## 15. Checklist trước khi quyết định dùng BigInteger

1. **Miền giá trị:** có cận dưới/trên đáng tin hay có thể vượt `long`?
2. **Phép tính trung gian:** ngay cả khi đầu vào vừa `long`, tích, giai thừa hoặc tổng nhiều giá trị có thể vượt ngưỡng không?
3. **Yêu cầu lỗi:** cần báo tràn bằng `Math.*Exact` hay cần tiếp tục tính với số nguyên lớn chính xác?
4. **Nguồn đầu vào:** là `long` đã hợp lệ, chuỗi chữ số rất dài, hay mảng byte cần xác định signedness?
5. **Cổng ra:** có thể lưu/xuất giá trị lớn hay sẽ phải chuyển về `int`/`long`? Nếu chuyển, có dùng `*ValueExact()` không?
6. **Ý nghĩa phép chia:** đang cần thương nguyên, số dư, modulo hay số thập phân chính xác?
7. **Chi phí:** đã có giới hạn độ dài đầu vào, số mũ, số vòng lặp và kích thước kết quả chưa?

**Ghi nhớ:** Khi `int`/`long` không đủ miền biểu diễn nhưng vẫn cần **số nguyên chính xác**, đưa toán hạng vào `BigInteger` **trước phép tính**, dùng phương thức thay toán tử số học, giữ giá trị trả về vì đối tượng bất biến, và kiểm tra biên khi chuyển ra kiểu hữu hạn. “Tùy ý độ chính xác” giải quyết bài toán **giới hạn số bit**, đồng thời đòi hỏi quản lý **giới hạn tài nguyên**.


</details>

- [Quay lại đầu trang](#back-to-top)
