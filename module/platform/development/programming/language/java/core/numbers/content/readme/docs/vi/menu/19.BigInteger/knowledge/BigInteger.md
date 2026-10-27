<a id="back-to-top"></a>

# BigInteger

## Menu
- [Số nguyên độ chính xác tùy ý và tính bất biến](#big-integer-001)
- [Tạo từ String, radix, long hoặc mảng byte](#big-integer-002)
- [Biểu diễn byte bù hai có dấu và constructor sign-magnitude](#big-integer-003)
- [ZERO, ONE, TWO, TEN và valueOf](#big-integer-004)
- [add, subtract, multiply, divide và remainder](#big-integer-005)
- [divideAndRemainder và kiểm tra chia hết](#big-integer-006)
- [pow, sqrt, sqrtAndRemainder và gcd](#big-integer-007)
- [mod, modPow, modInverse và các điều kiện đầu vào](#big-integer-008)
- [Phép bit trên số nguyên có độ dài mở rộng](#big-integer-009)
- [bitLength, bitCount, testBit, setBit và shift](#big-integer-010)
- [compareTo, equals, signum, abs và negate](#big-integer-011)
- [intValue, longValue khác intValueExact, longValueExact](#big-integer-012)
- [Chuyển sang byte&#91;&#93; và vấn đề byte dấu bổ sung](#big-integer-013)
- [Kiểm tra số nguyên tố xác suất và ý nghĩa certainty](#big-integer-014)
- [Giới hạn tài nguyên dù không có độ rộng cố định như long](#big-integer-015)

## <a id="big-integer-001">Số nguyên độ chính xác tùy ý và tính bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger: Số nguyên độ chính xác tùy ý và tính bất biến trong Java

`java.math.BigInteger` là lớp biểu diễn **số nguyên có dấu với độ chính xác tùy ý** (*arbitrary-precision signed integer*) trong Java. Khác với `int` có 32 bit và `long` có 64 bit, `BigInteger` không buộc giá trị vào độ rộng cố định đó. Khi cộng, nhân hoặc dịch bit để tạo một số lớn hơn, thư viện có thể mở rộng biểu diễn nội bộ thay vì tự động cho kết quả tràn theo bù hai 32/64 bit.

Đồng thời, `BigInteger` là **bất biến** (*immutable*): sau khi một đối tượng được tạo, **giá trị của chính đối tượng đó không đổi**. Các phép toán như `add`, `multiply`, `subtract`, `pow` tạo/trả một đối tượng đại diện cho kết quả; biến tham chiếu phải được gán lại nếu muốn tiếp tục dùng kết quả mới.

```java
import java.math.BigInteger;

BigInteger original = BigInteger.valueOf(Integer.MAX_VALUE);
BigInteger next = original.add(BigInteger.ONE);

System.out.println(original); // 2147483647
System.out.println(next);     // 2147483648
```

Ở đây `original` **không** bị sửa. Kết quả `next` vượt `Integer.MAX_VALUE` mà vẫn chính xác. Tuy nhiên “tùy ý” không có nghĩa vô hạn tài nguyên: bộ nhớ, kích thước đối tượng, thời gian tính toán và các giới hạn triển khai vẫn tồn tại.

## 1. Vì sao cần `BigInteger` khi Java đã có `int` và `long`?

Các primitive nguyên có miền giá trị cố định:

| Kiểu | Số bit | Miền giá trị có dấu |
| --- | ---: | --- |
| `byte` | 8 | −128 đến 127 |
| `short` | 16 | −32.768 đến 32.767 |
| `int` | 32 | −2.147.483.648 đến 2.147.483.647 |
| `long` | 64 | −9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 |
| `BigInteger` | Không có một độ rộng 32/64 bit cố định | Số nguyên âm, 0 và dương rất lớn, giới hạn bởi triển khai/tài nguyên |

Với phép cộng `int` thông thường, khi kết quả vượt miền, Java giữ kết quả theo quy tắc tràn số nguyên:

```java
int a = Integer.MAX_VALUE;
System.out.println(a + 1); // -2147483648: overflow.
```

`BigInteger` tránh tràn **theo giới hạn primitive** bằng cách dùng cấu trúc biểu diễn có thể mở rộng:

```java
BigInteger a = BigInteger.valueOf(Integer.MAX_VALUE);
System.out.println(a.add(BigInteger.ONE)); // 2147483648.
```

Một số lớn vượt cả `long` cũng được biểu diễn bằng `BigInteger`:

```java
BigInteger beyondLong =
        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
System.out.println(beyondLong); // 9223372036854775808.
```

**Quan trọng:** `BigInteger` vẫn là **số nguyên**. Nó không có phần thập phân; `1 / 2` bằng 0 theo phép chia nguyên của lớp này. Nếu bài toán đòi hỏi thập phân chính xác và scale, xem `BigDecimal`. Nếu chỉ cần số đếm trong miền `int`/`long`, không nên mặc định chọn `BigInteger` bởi nó có thêm chi phí bộ nhớ, đối tượng và phép toán.

## 2. Bản chất và quan hệ kiểu của `BigInteger`

`BigInteger` thuộc package `java.math`; khác `Integer` và `Long` trong `java.lang`, bạn cần `import java.math.BigInteger;` hoặc dùng tên đầy đủ.

Nó là một **lớp tham chiếu**:
- Kế thừa `Number`, do đó có `intValue()`, `longValue()`, `floatValue()` và `doubleValue()`.
- Triển khai `Comparable<BigInteger>`, nên có `compareTo`.
- Là **immutable** và có API toán học trên đối tượng.
- Không có toán tử cộng/trừ/nhân/chia được overload trên các đối tượng như ở một số ngôn ngữ khác.

```java
BigInteger x = BigInteger.valueOf(12);
BigInteger y = BigInteger.valueOf(5);

// BigInteger z = x + y; // Không biên dịch.
BigInteger z = x.add(y); // 17.
```

Đừng nhầm `BigInteger` với `Integer`:
- `Integer` là wrapper bất biến của primitive `int` 32 bit.
- `BigInteger` lưu một số nguyên không bị giới hạn về chính xác ở 32 bit và có nhiều phương thức tính toán.
- `BigInteger` không phải subtype của `Integer`, và `BigInteger` cũng không tự unbox thành `int`.

Việc hai kiểu cùng kế thừa `Number` không đồng nghĩa có thể ép tham chiếu tùy ý giữa chúng.

## 3. Tạo đối tượng `BigInteger` đúng cách

### 3.1. Từ `long` với `valueOf`

```java
BigInteger a = BigInteger.valueOf(42);
BigInteger b = BigInteger.valueOf(-17);
BigInteger c = BigInteger.valueOf(Long.MAX_VALUE);
```

`BigInteger.valueOf(long)` nhận **giá trị `long`**. Nó phù hợp khi đầu vào vốn nằm trong miền 64 bit.

```java
// BigInteger wrong = BigInteger.valueOf(99999999999999999999999L);
// Literal long quá lớn: lỗi biên dịch, BigInteger không thể cứu trước đó.
```

Nếu giá trị **vượt `long`**, tạo trực tiếp từ chuỗi chữ số có kiểm soát.

### 3.2. Từ chuỗi decimal bằng constructor

```java
BigInteger population =
        new BigInteger("123456789012345678901234567890");

BigInteger negative = new BigInteger("-12345678901234567890");
BigInteger zero = new BigInteger("0");
```

`new BigInteger(String)` mặc định parse thập phân, có thể xử lý dấu `+` hoặc `-` đầu chuỗi hợp lệ. Nếu văn bản không hợp lệ, constructor ném `NumberFormatException`:

```java
// new BigInteger("12.5");      // Sai: có phần thập phân.
// new BigInteger("1_000");     // Sai: underscore không được nhận như literal.
// new BigInteger(" 100 ");     // Sai: có khoảng trắng chưa xử lý.
// new BigInteger("0xFF");      // Sai: không tự đoán tiền tố hex.
```

Các lời gọi trên được **chú thích** vì cố ý minh họa lỗi, không phải mã cần chạy liên tiếp.

### 3.3. Từ chuỗi ở cơ số 2–36

```java
BigInteger hex = new BigInteger("ff", 16);     // 255.
BigInteger binary = new BigInteger("1010", 2); // 10.
BigInteger signed = new BigInteger("-ff", 16); // -255.
```

Tham số `radix` nêu cơ số. Constructor `BigInteger(String,int)` không mặc định hiểu `0x`/`0b`; chuỗi phải chứa chữ số phù hợp cơ số và dấu hợp lệ. `toString(int radix)` thực hiện chiều ngược lại:

```java
System.out.println(hex.toString(16)); // ff.
System.out.println(binary.toString(2)); // 1010.
```

Nếu đầu vào là một **mã định danh** có số 0 đầu cần giữ nguyên, đừng tự chuyển sang `BigInteger` khi không cần số học: giá trị nguyên không lưu lại độ rộng hay cách nhóm chữ số của chuỗi gốc.

### 3.4. Các hằng số hữu ích

JDK cung cấp:

```java
BigInteger.ZERO
BigInteger.ONE
BigInteger.TWO
BigInteger.TEN
```

Trong Java 17, các hằng trên có sẵn (ví dụ `BigInteger.TWO` từ Java 9). Chúng là các **đối tượng bất biến có thể dùng chung**.

```java
BigInteger count = BigInteger.ZERO;
count = count.add(BigInteger.ONE);
```

Dùng `BigInteger.ZERO` giúp rõ nghĩa hơn `new BigInteger("0")`. Tuy nhiên không giả định các kết quả phép toán khác nhau luôn là cùng object khi bằng giá trị; dùng `equals` cho so sánh giá trị.

## 4. Tính bất biến: các phép toán không sửa đối tượng gốc

Một ví dụ sai thường gặp:

```java
BigInteger total = BigInteger.ZERO;
total.add(BigInteger.TEN);
System.out.println(total); // 0, không phải 10!
```

`add` đã tạo/trả **kết quả**, nhưng chương trình không lưu kết quả. Để cập nhật biến `total`, gán lại:

```java
BigInteger total = BigInteger.ZERO;
total = total.add(BigInteger.TEN);
System.out.println(total); // 10.
```

Điều này không làm đối tượng cũ đổi giá trị. Java chỉ đổi **tham chiếu đang nằm trong biến `total`**.

### Hình dung bằng hai biến cùng tham chiếu

```java
BigInteger a = new BigInteger("100");
BigInteger alias = a;
a = a.add(BigInteger.ONE);

System.out.println(a);     // 101.
System.out.println(alias); // 100.
```

Ban đầu hai biến `a` và `alias` tham chiếu cùng một `BigInteger(100)`. Sau phép gán lại, `a` tham chiếu kết quả 101; `alias` vẫn thấy đối tượng giá trị 100 không đổi.

### Lợi ích của immutable

1. **Ít hiệu ứng phụ:** hàm nhận `BigInteger` không thể làm thay giá trị nội tại của đối tượng mà caller đang giữ.
2. **Dễ chia sẻ:** cùng một giá trị có thể được nhiều nơi giữ tham chiếu mà không bị sửa trạng thái số.
3. **Thuận tiện làm key:** `BigInteger` cung cấp `equals` và `hashCode` theo giá trị, phù hợp cho `HashMap` hoặc `HashSet`.
4. **Đơn giản hóa luồng đồng thời:** một đối tượng `BigInteger` đã được công bố an toàn có thể được nhiều luồng đọc mà không cần khóa để ngăn sửa giá trị đó.
5. **Dễ lập luận:** biểu thức `a.add(b)` trả kết quả và giữ `a`, `b` nguyên vẹn.

**Giới hạn của nhận định về thread safety:** tính bất biến của `BigInteger` **không làm phép gán lại một biến dùng chung trở thành atomic**. Chẳng hạn `counter = counter.add(ONE)` trên một biến dùng chung có thể mất cập nhật khi nhiều luồng đồng thời đọc/gán. Với bộ đếm đồng thời, cần đồng bộ phù hợp hoặc công cụ atomic cho bài toán tương ứng.

### Không có `++` và `+=` trên `BigInteger`

```java
BigInteger n = BigInteger.ZERO;
// n++;       // Không biên dịch.
// n += 1;    // Không biên dịch.
n = n.add(BigInteger.ONE); // Đúng.
```

Việc cần tạo giá trị mới là một phần **mô hình API**, không phải một chi tiết riêng của `add`. `multiply`, `subtract`, `negate`, `abs`, `shiftLeft`... đều không biến đối tượng nhận lời gọi thành giá trị khác.

## 5. Các phép toán số học thường dùng

| Phương thức | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `a.add(b)` | `a + b` | `12 + 5 = 17` |
| `a.subtract(b)` | `a - b` | `12 - 5 = 7` |
| `a.multiply(b)` | `a × b` | `12 × 5 = 60` |
| `a.divide(b)` | Thương nguyên, cắt về 0 | `12 / 5 = 2` |
| `a.remainder(b)` | Phần dư theo phép chia nguyên | `12 % 5 = 2` |
| `a.divideAndRemainder(b)` | Trả mảng hai phần tử [thương, dư] | `[2, 2]` |
| `a.pow(n)` | Lũy thừa với số mũ nguyên `int` không âm | `12^2 = 144` |
| `a.negate()` | Đổi dấu | `-12` |
| `a.abs()` | Giá trị tuyệt đối | `12` |
| `a.gcd(b)` | Ước chung lớn nhất không âm | `gcd(54,24) = 6` |
| `a.signum()` | Dấu của số | −1, 0 hoặc 1 |
| `a.min(b)` / `a.max(b)` | Giá trị nhỏ/lớn hơn | So sánh hai BigInteger |

```java
BigInteger a = BigInteger.valueOf(12);
BigInteger b = BigInteger.valueOf(5);

System.out.println(a.add(b));      // 17
System.out.println(a.subtract(b)); // 7
System.out.println(a.multiply(b)); // 60
System.out.println(a.divide(b));   // 2
System.out.println(a.remainder(b)); // 2
```

Các phép toán trên **không phải overload toán tử Java**. Ví dụ `a.multiply(b)` trả một `BigInteger`, không thay `a`. `divide` và `remainder` ném `ArithmeticException` khi số chia bằng 0.

### Phép cộng vào biến kết quả trong vòng lặp

```java
BigInteger sum = BigInteger.ZERO;

for (int i = 1; i <= 100; i++) {
    sum = sum.add(BigInteger.valueOf(i));
}
System.out.println(sum); // 5050.
```

Nếu bỏ `sum =` ở bên trái, kết quả sẽ vẫn là 0. Đây là lỗi **bỏ quên reassignment** do quen với đối tượng mutable.

## 6. Chia nguyên, `remainder` và `mod` khác nhau

`BigInteger.divide(b)` lấy thương nguyên **cắt về 0**; `remainder(b)` là phần dư tương ứng và có thể âm. Với `a=-17,b=5`, thương bằng `-3` và dư bằng `-2`, thỏa `a=q*b+r`. Nếu cần một phần dư không âm, `mod(m)` chọn đại diện trong `[0,m)` nhưng **đòi `m>0`**. Các phép chia cho zero báo `ArithmeticException`. Cách lấy đồng thời thương và dư, kiểm tra chia hết sẽ được thực hành riêng.

## 7. Lũy thừa, dịch bit, GCD và modular arithmetic

`pow(int)` tính lũy thừa nguyên với số mũ không âm; `sqrt()` lấy căn bậc hai nguyên của giá trị không âm; `gcd(b)` lấy ước chung lớn nhất không âm. `shiftLeft(n)` làm việc với bit của số nguyên có dấu, khác biểu diễn một word 32/64 bit cố định. Trong số học đồng dư, `modPow(e,m)` tính lũy thừa modulo hiệu quả, còn `modInverse(m)` chỉ tồn tại khi giá trị và modulus nguyên tố cùng nhau. Đây là những API có các điều kiện đầu vào riêng, được giải thích cùng bài tập cụ thể ở các bài chuyên sâu.

## 8. Chuyển đổi sang `int`, `long` và bẫy mất dữ liệu

`intValue()` và `longValue()` có thể **cắt bit cao**, chẳng hạn `BigInteger.ONE.shiftLeft(32).intValue()` bằng 0. Nếu hợp đồng API cần giữ đúng giá trị, dùng `intValueExact()` hoặc `longValueExact()` và xử lý `ArithmeticException` khi không vừa miền signed tương ứng. Chuyển kiểu nên đặt ở ranh giới mà ứng dụng thật sự cần primitive, không thực hiện sớm trong phép tính lớn.

## 9. So sánh, equality và thứ tự

`a.compareTo(b)` xác định quan hệ nhỏ/bằng/lớn từ dấu số trả về; `a.equals(b)` so cùng giá trị `BigInteger`; `a == b` chỉ so tham chiếu. `signum()` trả -1, 0 hoặc 1; `abs()` và `negate()` thao tác độ lớn hoặc số đối và vẫn giữ đối tượng nguồn bất biến. Những quy tắc này quan trọng cho mã sắp xếp, kiểm tra biên và khóa collection.

## 10. Hai cách hiểu khác nhau của mảng byte

`new BigInteger(bytes)` giải mã **signed two's complement big-endian**, nên một byte `FF` là `-1`. `new BigInteger(1,bytes)` nhận magnitude unsigned với dấu dương riêng, nên byte `FF` là `+255`. `toByteArray()` xuất signed two's complement tối thiểu; số dương 128 cần `00 80` để giữ bit dấu 0. Khi giao thức đòi fixed-width hoặc unsigned, cần định rõ width, padding và kiểm tra miền thay vì bỏ byte đầu tùy tiện.

## 11. Bit operations không có giới hạn 32/64 bit mặc định

`and`, `or`, `xor`, `not`, `shiftLeft`, `shiftRight`, `testBit` và `setBit` tuân mô hình bù hai **mở rộng dấu**. Vì thế `BigInteger.valueOf(5).not()` là -6 thay vì giá trị 250 của phép đảo **8 bit unsigned**. Nếu phải mô phỏng word có độ rộng `w`, cần đặt mask `2^w - 1` phù hợp. Chi tiết bitLength, bitCount và thao tác vị trí bit được tách thành bài học riêng.

## 12. Giới hạn thật của “độ chính xác tùy ý”

“Arbitrary precision” nghĩa là **không bị buộc vào độ rộng primitive cố định**, không có nghĩa hệ thống có bộ nhớ hay thời gian vô hạn.

Các giới hạn thực tế gồm:

1. **Bộ nhớ heap:** một số với hàng triệu chữ số cần bộ nhớ lớn để lưu và thao tác.
2. **Thời gian tính toán:** nhân/chia/GCD/modPow trên số có hàng triệu bit tốn nhiều hơn phép toán primitive.
3. **Giới hạn kích thước và implementation:** JDK có các giới hạn nội bộ cho số bit hoặc độ dài mảng; không thể tạo tùy ý đến vô tận.
4. **Đầu vào không tin cậy:** chuỗi số dài khổng lồ, số mũ `pow` lớn hoặc lời gọi xây số ngẫu nhiên có thể gây tiêu thụ tài nguyên ngoài dự kiến.
5. **Chuyển đổi ra kiểu cố định:** khi gọi `intValue`, `longValue` hay `doubleValue`, bạn lại chịu giới hạn của kiểu đích.
6. **Truyền tải và định dạng:** một số rất lớn thành chuỗi decimal có thể dài hàng MB, kéo theo phí ghi log, JSON, CSV hay database.

**Không đặt `BigInteger` vào vòng lặp nóng chỉ vì “an toàn hơn int” nếu bài toán chỉ cần số nhỏ.** Đối với phép cộng hàng triệu phần tử trong miền `long`, `long` có thể đơn giản và hiệu quả hơn. Khi cần phát hiện tràn primitive, có thể dùng `Math.addExact`/`multiplyExact` hoặc nâng cấp lên `BigInteger` theo yêu cầu cụ thể.

### Lưu ý với thuật toán giai thừa

Giai thừa tăng rất nhanh. `20!` nằm trong miền `long`, nhưng `21!` đã vượt miền `long` signed. Với `BigInteger`:

```java
static BigInteger factorial(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n must be nonnegative");
    }
    BigInteger result = BigInteger.ONE;
    for (int i = 2; i <= n; i++) {
        result = result.multiply(BigInteger.valueOf(i));
    }
    return result;
}
```

Đây là **phương thức minh họa**, cần đặt trong class. Nó vẫn cần **giới hạn `n`** khi nhận input từ người dùng để tránh yêu cầu tài nguyên quá lớn. `BigInteger` cho độ chính xác, không tự tạo quota tính toán.

### “Exact arithmetic” không đồng nghĩa “kết quả mọi phép toán có thể biểu diễn”

Cộng, trừ, nhân số nguyên phù hợp trong miền được JDK hỗ trợ đều là phép toán chính xác, không có sai số làm tròn dấu phẩy động. Nhưng phép **chia nguyên** vẫn bỏ phần phân số vì kiểu kết quả là số nguyên. `BigInteger.ONE.divide(BigInteger.TWO)` trả 0; muốn biểu diễn `1/2` phải chọn phân số gồm tử/mẫu BigInteger hoặc một mô hình decimal có chính sách rounding.

## 13. Ngẫu nhiên, số nguyên tố và mật mã: cần thêm hợp đồng

`BigInteger` có một số API sinh số lớn/ngẫu nhiên và làm việc với số nguyên tố, chẳng hạn `probablePrime`, `isProbablePrime`, `nextProbablePrime` và các phép modular. Đây là API toán học rất hữu ích, nhưng cần hiểu phạm vi:

- `isProbablePrime(certainty)` liên quan đến kiểm tra nguyên tố xác suất với mức chắc chắn theo hợp đồng API, **không** nên tùy tiện đọc là phép chứng minh toán học tuyệt đối.
- Nếu tạo tham số cho mật mã, nguồn ngẫu nhiên và cách chọn tham số phải đúng chuẩn; `java.util.Random` không phải lựa chọn mặc định cho bí mật.
- `BigInteger` không cam kết mọi thao tác là **constant-time**; đừng tự chế primitive mật mã rồi cho rằng “số học BigInteger chính xác” đồng nghĩa an toàn trước side-channel.
- Với khóa, chữ ký, mã hóa, hãy dùng các API `java.security`/`javax.crypto` và chuẩn giao thức phù hợp thay vì tự ghép những phép BigInteger riêng.

Trong bài học nhập môn, xem modular arithmetic và nguyên tố như **khả năng bổ sung**, không làm lu mờ hai thuộc tính chính: **độ chính xác tùy ý** và **tính bất biến**.

## 14. Xử lý đầu vào và dữ liệu trao đổi

Khi nhận số nguyên rất lớn từ người dùng, file hoặc API:

```java
String text = "123456789012345678901234567890";
BigInteger value = new BigInteger(text);
```

Nên có quy trình:

1. Xác định schema: signed/unsigned, radix, cho phép dấu +/− hay không, có nhóm chữ số hay không.
2. Giới hạn độ dài chuỗi và miền giá trị **trước** khi tạo đối tượng rất lớn.
3. Không suy đoán prefix `0x` hoặc underscore trong runtime `String`: `new BigInteger("0xFF")` không tự parse hex như compiler.
4. Bắt `NumberFormatException` ở ranh giới dữ liệu và trả lỗi có ngữ cảnh.
5. Định nghĩa cách serialize: decimal string, radix cụ thể, hoặc bytes signed/unsigned, với độ rộng và byte order rõ ràng.
6. Nếu bên nhận dùng JavaScript `Number`, lưu ý các số nguyên vượt `2^53` có thể mất chính xác; **decimal string theo schema** thường phù hợp cho ID lớn và số cần chính xác tuyệt đối.
7. Không gửi kết quả `doubleValue` rồi xem đó là bản sao hoàn hảo của BigInteger ban đầu.

`BigInteger.toString()` tạo chuỗi thập phân không có grouping locale:

```java
BigInteger big = new BigInteger("12345678901234567890");
String wireText = big.toString(); // "12345678901234567890".
```

Đây là cách biểu diễn tốt nếu giao thức **đã quy định decimal string**. Nếu schema dùng JSON number, việc dùng một thư viện JSON có kiểu thích hợp cũng có thể hợp lý, nhưng cần đánh giá toàn bộ bên nhận và yêu cầu precision. Không tự đánh đồng `toString` với một serializer JSON đầy đủ.

## 15. Chương trình Java 17+ chạy độc lập

Lưu tệp `BigIntegerBasicsDemo.java`:

```java
import java.math.BigInteger;
import java.util.Arrays;

public class BigIntegerBasicsDemo {
    static BigInteger factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be nonnegative");
        }
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    public static void main(String[] args) {
        int wrapped = Integer.MAX_VALUE + 1;
        BigInteger base = BigInteger.valueOf(Integer.MAX_VALUE);
        BigInteger next = base.add(BigInteger.ONE);

        System.out.println("int overflow: " + wrapped);
        System.out.println("BigInteger result: " + next);
        System.out.println("Original unchanged: " + base);
        System.out.println("2^100: " + BigInteger.ONE.shiftLeft(100));
        System.out.println("20!: " + factorial(20));

        BigInteger negative = BigInteger.valueOf(-17);
        BigInteger five = BigInteger.valueOf(5);
        System.out.println("divide (-17,5): " + negative.divide(five));
        System.out.println("remainder (-17,5): " + negative.remainder(five));
        System.out.println("mod (-17,5): " + negative.mod(five));
        System.out.println("gcd(54,24): "
                + BigInteger.valueOf(54).gcd(BigInteger.valueOf(24)));
        System.out.println("2^10: " + BigInteger.TWO.pow(10));
        System.out.println("sqrt(1000): "
                + new BigInteger("1000").sqrt());

        System.out.println("hex ff: " + new BigInteger("ff", 16));
        System.out.println("255 in hex: "
                + BigInteger.valueOf(255).toString(16));

        System.out.println("negative one bytes: "
                + Arrays.toString(BigInteger.valueOf(-1).toByteArray()));
        System.out.println("unsigned ff byte: "
                + new BigInteger(1, new byte[] {(byte) 0xff}));

        System.out.println("intValue overflow: " + next.intValue());
        try {
            next.intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("intValueExact: rejected");
        }
        System.out.println("long max + 1: "
                + BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        System.out.println("equals same value: "
                + new BigInteger("42").equals(BigInteger.valueOf(42)));
        System.out.println("compare 99 vs 100: "
                + BigInteger.valueOf(99).compareTo(BigInteger.valueOf(100)));
        System.out.println("modPow(3,13,7): "
                + BigInteger.valueOf(3).modPow(
                        BigInteger.valueOf(13), BigInteger.valueOf(7)));
        System.out.println("signum(-17): " + negative.signum());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 BigIntegerBasicsDemo.java
java BigIntegerBasicsDemo
```

Stdout:

```text
int overflow: -2147483648
BigInteger result: 2147483648
Original unchanged: 2147483647
2^100: 1267650600228229401496703205376
20!: 2432902008176640000
divide (-17,5): -3
remainder (-17,5): -2
mod (-17,5): 3
gcd(54,24): 6
2^10: 1024
sqrt(1000): 31
hex ff: 255
255 in hex: ff
negative one bytes: [-1]
unsigned ff byte: 255
intValue overflow: -2147483648
intValueExact: rejected
long max + 1: 9223372036854775808
equals same value: true
compare 99 vs 100: -1
modPow(3,13,7): 3
signum(-17): -1
```

Các dòng minh họa lần lượt: primitive overflow; cộng BigInteger không sửa đối tượng gốc; số vượt 64 bit; tính toán chính xác; chia nguyên và sự khác nhau giữa `remainder`/`mod`; chuyển radix; signed/unsigned byte; ép xuống `int` không kiểm tra so với `intValueExact`; và các phép modular.

## 16. Chọn `int`, `long`, `BigInteger` hay `BigDecimal`?

| Nhu cầu | Kiểu thường phù hợp | Điều cần lưu ý |
| --- | --- | --- |
| Chỉ số mảng, số đếm vừa 32 bit | `int` | Truy cập API tự nhiên, nhẹ; kiểm tra overflow khi cần |
| Timestamp, số lượng, ID số vừa 64 bit | `long` | Độ rộng cố định; tránh làm mất precision ở client binary64 |
| Số nguyên rất lớn, giai thừa, lũy thừa, tính toán exact | `BigInteger` | Immutable; phép toán qua method và tốn tài nguyên tùy độ lớn |
| Số thập phân cần tính chính xác, tiền, giá trị có scale | `BigDecimal` | Cần chính sách rounding/scale |
| Đo lường liên tục cho phép sai số binary64 | `double` | Không biểu diễn chính xác mọi phân số decimal |
| Kết quả tính gần đúng với trọng tâm bộ nhớ thấp | `float` nếu sai số chấp nhận được | Độ chính xác thấp hơn double |
| Bộ đếm thay đổi đồng thời trong miền int/long | `AtomicInteger`/`AtomicLong` hoặc đồng bộ | Immutable BigInteger không tự tạo atomic update |
| Số vượt mọi primitive và cần truyền chính xác | `BigInteger` + schema text/binary | Quy định signedness, radix, độ rộng, client support |

Đừng chọn `BigInteger` chỉ vì input có vẻ “nhiều chữ số” nếu thực chất là mã định danh không dùng số học: nhiều loại dữ liệu cần giữ ký tự `0` đầu, dấu phân cách hoặc định dạng gốc, nên `String` có thể đúng ngữ nghĩa hơn.

## 17. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Dùng `+`, `-`, `*` trên hai `BigInteger` | Không biên dịch | `add`, `subtract`, `multiply` |
| Gọi `a.add(b)` mà không lưu kết quả | `a` không đổi | `a = a.add(b)` hoặc dùng biến kết quả |
| Cho rằng `BigInteger` làm mọi phép tính thành “exact decimal” | Chỉ là kiểu số **nguyên** | Dùng `BigDecimal` hoặc Rational khi có phân số |
| Chuyển số lớn về `long` trước khi tính | Có thể mất bit trước khi dùng `BigInteger` | Tạo trực tiếp từ chuỗi hoặc giữ BigInteger |
| Dùng `intValue()`/`longValue()` như phép chuyển có kiểm tra | Có thể mất bit/đổi dấu | `intValueExact()`/`longValueExact()` |
| Tin `doubleValue()` không mất chữ số | Binary64 có 53 bit significand | Giữ BigInteger nếu cần đủ chữ số |
| Dùng `a == b` để so sánh hai BigInteger | So object identity, không phải giá trị | `a.equals(b)` |
| Dùng `a > b` | Không có toán tử trên object | `a.compareTo(b) > 0` |
| Nhầm `remainder` với `mod` cho số âm | Một kết quả có thể âm, một kết quả không âm | Đọc hợp đồng và chọn đúng API |
| Gọi `mod` với modulus 0 hoặc âm | `ArithmeticException` | Kiểm tra modulus dương |
| Tưởng `divide` tạo phần số sau dấu phẩy | Chia nguyên, cắt phần lẻ | Chọn mô hình phân số hoặc BigDecimal |
| Tưởng `BigInteger.valueOf` nhận số bất kỳ ngoài long | Phương thức nhận `long` | `new BigInteger(decimalText)` |
| Parse `"0xFF"` trực tiếp với constructor decimal | `NumberFormatException` | `new BigInteger("FF",16)` hoặc normalize |
| Dùng `new BigInteger(new byte[]{(byte)0xff})` để lấy 255 | Trả −1 vì signed two's complement | `new BigInteger(1,magnitude)` |
| Tưởng mọi mảng byte tạo ra là fixed width | `toByteArray` có độ dài theo signed representation | Codec padding/width theo schema |
| Tin biến cập nhật `counter = counter.add(ONE)` là atomic | Nhiều luồng có thể ghi đè kết quả | Cơ chế đồng bộ/atomic update phù hợp |
| Dùng exponent hoặc input độ dài không giới hạn | Có thể cạn CPU/heap | Áp dụng quota và validate input |
| Tự dựng mật mã chỉ bằng `modPow` | Thiếu chuẩn bảo mật, random, side-channel | Dùng thư viện crypto và giao thức đã kiểm chứng |

## 18. Câu hỏi tự kiểm tra

1. Vì sao `Integer.MAX_VALUE + 1` cho số âm, còn `BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)` cho 2147483648?
2. `BigInteger` “độ chính xác tùy ý” có nghĩa mọi phép toán đều miễn giới hạn bộ nhớ và thời gian không?
3. `BigInteger a = BigInteger.ZERO; a.add(ONE);` làm `a` thay đổi không? Viết lại để `a` thực sự tham chiếu giá trị 1.
4. Hai biến cùng trỏ đến `BigInteger("100")`; sau khi một biến gán kết quả `add(ONE)`, biến kia còn thấy giá trị gì? Vì sao?
5. Vì sao `BigInteger` không dùng `+` như `int`? Những phương thức tương đương là gì?
6. `new BigInteger("ff",16)` và `new BigInteger("0xff")` khác nhau như thế nào?
7. So sánh `divide`, `remainder` và `mod` khi số bị chia là −17 và số chia/modulus là 5.
8. Khi nào `intValue`/`longValue` có thể làm mất dữ liệu? Vì sao các phiên bản `Exact` an toàn hơn?
9. Mảng một byte `0xff` sẽ trở thành giá trị gì nếu gọi `new BigInteger(bytes)`? Nếu muốn số dương 255 thì làm sao?
10. Vì sao `BigInteger.valueOf(Long.MAX_VALUE).add(ONE)` hợp lệ nhưng `BigInteger.valueOf(9223372036854775808L)` không phải literal `long` hợp lệ?
11. Khi nào nên dùng `equals` và khi nào dùng `compareTo` với `BigInteger`? Vì sao không dùng `==` để so giá trị?
12. Vì sao `BigInteger.ONE.divide(BigInteger.TWO)` không cho 0.5? Cách nào biểu diễn phân số chính xác?
13. Chia sẻ một `BigInteger` cho nhiều luồng có an toàn không? Điều gì vẫn không an toàn khi nhiều luồng **cập nhật biến tham chiếu chung**?
14. Khi truyền một BigInteger rất lớn cho client JavaScript sử dụng `Number`, điều gì có thể xảy ra? Schema trao đổi nên quyết định gì?
15. Vì sao số mũ `pow` cực lớn vẫn cần giới hạn input dù dùng arbitrary precision?
16. Khi nào dùng `BigInteger.modPow` thay vì tính `pow` rồi `mod`? Điều đó có tự động tạo một hệ mật mã an toàn không?

## 19. Tài liệu chính thức

- [Java SE 17 — `java.math.BigInteger`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): API tạo giá trị, số học, tính bất biến, exact conversion, modular arithmetic, byte representation và giới hạn triển khai.
- [Java SE 17 — `java.lang.Number`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Number.html): các phương thức chuyển đổi `intValue`, `longValue`, `floatValue`, `doubleValue` và khả năng mất thông tin.
- [Java SE 17 — `java.math.BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): số thập phân chính xác với scale và chính sách rounding, dùng khi dữ liệu không còn thuần số nguyên.
- [Java Language Specification `4.2.1–`4.2.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2): miền primitive nguyên và phép toán nguyên.
- [Java SE 17 — `Math` exact methods](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `addExact`, `multiplyExact` và lựa chọn checked arithmetic khi vẫn dùng primitive.

**Ghi nhớ:** `BigInteger` biểu diễn **số nguyên có dấu với độ chính xác tùy ý trong giới hạn tài nguyên**, và là **immutable**. `add`, `multiply`, `pow` và các phương thức khác trả giá trị mới; muốn dùng kết quả phải nhận/gán lại. Mọi chuyển đổi xuống `int`/`long` hoặc `double` là một ranh giới có thể mất dữ liệu; chọn `...ValueExact()` khi cần bảo toàn. Phân biệt `remainder` và `mod`, chú ý signed bytes và thiết kế schema dữ liệu, và đừng lầm tính bất biến của giá trị số với việc cập nhật một biến chung là atomic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-002">Tạo từ String, radix, long hoặc mảng byte</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger: Tạo từ String, radix, long hoặc mảng byte

> **Java core – numbers · BigInteger**  
> **Mục tiêu:** Chọn đúng cách khởi tạo `java.math.BigInteger` từ văn bản thập phân, văn bản có cơ số, primitive `long`, dữ liệu nhị phân `byte[]` có dấu và dữ liệu nhị phân không dấu; đọc đúng big-endian/bù hai, xử lý dấu, đầu vào lỗi, độ rộng byte và kiểm tra chuyển đổi ngược.

## 1. BigInteger giải quyết vấn đề gì?

Các kiểu nguyên primitive trong Java có độ rộng cố định:

| Kiểu | Độ rộng | Miền giá trị |
| --- | ---: | --- |
| `byte` | 8 bit | −128 đến 127 |
| `short` | 16 bit | −32.768 đến 32.767 |
| `int` | 32 bit | −2³¹ đến 2³¹−1 |
| `long` | 64 bit | −2⁶³ đến 2⁶³−1 |

`BigInteger` là lớp **số nguyên độ chính xác tùy ý** (*arbitrary-precision integer*) trong `java.math`. Nó không giới hạn cố định ở 32 hoặc 64 bit như `int`/`long`; các giá trị rất lớn được xử lý bằng nhiều bit nội bộ, trong phạm vi tài nguyên và giới hạn triển khai của JVM.

```java
import java.math.BigInteger;

BigInteger x = new BigInteger("123456789012345678901234567890");
BigInteger y = BigInteger.valueOf(10L);
BigInteger sum = x.add(y);

System.out.println(sum);
// 123456789012345678901234567900
```

**Không có toán tử `+` cho hai đối tượng `BigInteger`.** Sử dụng `add`, `subtract`, `multiply`, `divide` và các phương thức khác. `BigInteger` là **immutable**: phép `add` trả một giá trị mới thay vì sửa x.

Một BigInteger có thể được xây dựng bằng nhiều con đường. Việc chọn API phải dựa vào **kiểu và cách mã hóa dữ liệu nguồn**, không chỉ giá trị muốn nhận:

| Dữ liệu hiện có | API nên dùng | Điểm cần hiểu |
| --- | --- | --- |
| Chuỗi số thập phân | `new BigInteger(text)` | Cho phép dấu `+`/`-` đầu chuỗi |
| Chuỗi số cơ số 2–36 | `new BigInteger(text, radix)` | Cần chỉ rõ cơ số, không tự hiểu `0x` |
| Primitive `long` | `BigInteger.valueOf(number)` | Giữ nguyên giá trị có dấu của long |
| `byte[]` đã mã hóa signed two's complement | `new BigInteger(bytes)` | Big-endian, bit dấu của byte đầu có ý nghĩa |
| `byte[]` chứa độ lớn **unsigned** | `new BigInteger(1, bytes)` | Signum dương, không coi bit cao là dấu |
| Độ lớn và dấu được truyền riêng | `new BigInteger(signum, magnitude)` | Signum −1, 0, +1; magnitude là big-endian unsigned |
| Chỉ một lát cắt trong `byte[]` | Constructor có `off,len` (Java 9+) | Không cần tự tạo mảng con để gọi constructor |

**Câu hỏi quyết định cho byte array:** “Đây là số nguyên signed theo bù hai, hay một chuỗi byte unsigned biểu diễn độ lớn?” Hai cách hiểu này có thể cho kết quả **trái dấu** từ chính các byte giống nhau.

## 2. Tạo từ String thập phân

Constructor thông dụng nhất:

```java
BigInteger decimal = new BigInteger("123456789012345678901234567890");
BigInteger negative = new BigInteger("-255");
BigInteger positive = new BigInteger("+255");

System.out.println(decimal);  // 123456789012345678901234567890
System.out.println(negative); // -255
System.out.println(positive); // 255
```

`new BigInteger(String)` đọc chuỗi **decimal radix 10** theo mặc định. Chuỗi gồm một dấu đầu `+` hoặc `-` tùy chọn, theo sau bởi ít nhất một chữ số hợp lệ. Không truyền chuỗi rỗng hoặc chỉ có dấu.

Các chuỗi sau là các trường hợp cần phân biệt:

| Đầu vào | Kết quả |
| --- | --- |
| `"123"` | 123 |
| `"+123"` | 123 |
| `"-123"` | −123 |
| `"000123"` | 123, bỏ các số 0 đầu |
| `"-0"` | 0, không có trạng thái “âm zero” như float |
| `""` | `NumberFormatException` |
| `"+"` | `NumberFormatException` |
| `"12.0"` | `NumberFormatException` |
| `" 123 "` | `NumberFormatException` vì có ký tự thừa |
| `"1_000"` | `NumberFormatException` với quy tắc parser chuẩn |

**BigInteger chỉ biểu diễn số nguyên.** Nó không âm thầm cắt phần lẻ của chuỗi decimal hay chấp nhận dấu chấm như `BigDecimal`. Nếu nguồn là `"12.5"` và nghiệp vụ thực sự cho phép phần lẻ, hãy dùng kiểu/luồng chuyển đổi được thiết kế cho decimal có scale, không ép dữ liệu thành BigInteger bằng cách tự bỏ ký tự.

### Chữ số Unicode

Quy tắc ánh xạ ký tự thành chữ số dùng `Character.digit(char, radix)`, có thể chấp nhận một số ký tự chữ số Unicode. Nếu giao thức chỉ cho phép **ASCII `0..9`**, cần **validate riêng** trước hoặc cùng lúc với parse. Thành công của `new BigInteger(text)` không tự chứng minh token chứa toàn ASCII.

## 3. Tạo từ String theo radix (cơ số)

Dùng constructor:

```java
BigInteger binary = new BigInteger("101101", 2); // 45
BigInteger octal = new BigInteger("77", 8);      // 63
BigInteger hex = new BigInteger("FF", 16);       // 255
BigInteger base36 = new BigInteger("ZZ", 36);    // 1295
```

`radix` phải nằm trong khoảng **2 đến 36**. Các chữ số thập phân và chữ cái hợp lệ được quy đổi theo `Character.digit`. Chữ `A`/`a` là 10 trong cơ số 16, `F`/`f` là 15, `Z`/`z` là 35 trong cơ số 36.

Ví dụ có dấu:

```java
System.out.println(new BigInteger("-FF", 16));  // -255
System.out.println(new BigInteger("+100", 2));  // 4
System.out.println(new BigInteger("000101", 2)); // 5
```

**Sai lầm dễ gặp: tiền tố không tự được loại bỏ.**

```java
// new BigInteger("0xFF", 16); // NumberFormatException
// new BigInteger("0b101", 2); // NumberFormatException
```

Đây là **dữ liệu chuỗi runtime**, không phải literal Java trong mã nguồn. Với radix 16, hãy đưa chuỗi chữ số `"FF"` (và dấu nếu có), **không đưa `"0x"`**. Nếu giao thức cho phép tiền tố, tách và validate prefix trước khi truyền chuỗi chữ số cho constructor.

Ví dụ parse định dạng có tiền tố cố định:

```java
static BigInteger parsePrefixedHex(String text) {
    if (text == null || !text.startsWith("0x")) {
        throw new IllegalArgumentException("Expected 0x prefix");
    }
    return new BigInteger(text.substring(2), 16);
}
```

Đây là **phương thức minh họa**, cần đặt trong một class. Nó cố ý chỉ chấp nhận `0x` chữ thường ở đầu và số **không có dấu** sau prefix; nếu cần nhận `-0xFF` hoặc `0XFF`, phải định nghĩa cú pháp ấy và kiểm tra riêng. Đừng dùng một thao tác `replace` xóa `0x` ở mọi vị trí vì có thể chấp nhận chuỗi sai.

### toString(radix) là hướng chuyển đổi ngược

```java
BigInteger n = new BigInteger("255");
System.out.println(n.toString(2));  // 11111111
System.out.println(n.toString(16)); // ff
System.out.println(n.toString(10)); // 255
```

`toString(radix)` thường dùng chữ cái **thường** cho cơ số lớn hơn 10 và không thêm `0x`/`0b`. Với radix hợp lệ, có thể dùng hai đầu của một cặp round-trip:

```java
BigInteger original = new BigInteger("-12345678901234567890");
String encoded = original.toString(16);
BigInteger restored = new BigInteger(encoded, 16);

System.out.println(original.equals(restored)); // true
```

Dùng `equals` để so sánh **giá trị** đối tượng, không dùng `==` để kiểm tra hai biến có cùng số hay không.

## 4. Tạo từ long: dùng BigInteger.valueOf(long)

Nếu đầu vào đã là primitive `long`, dùng static factory:

```java
long id = 9_000_000_000L;
BigInteger bigId = BigInteger.valueOf(id);

System.out.println(bigId); // 9000000000
```

Không có constructor public `BigInteger(long)` tương ứng:

```java
// BigInteger wrong = new BigInteger(123L); // Không biên dịch
BigInteger correct = BigInteger.valueOf(123L);
```

Phương thức `valueOf` bảo toàn **giá trị có dấu** của `long`, bao gồm biên âm và dương:

```java
BigInteger low = BigInteger.valueOf(Long.MIN_VALUE);
BigInteger high = BigInteger.valueOf(Long.MAX_VALUE);

System.out.println(low); // -9223372036854775808
System.out.println(high); // 9223372036854775807
```

Nếu đầu vào là `int`, Java tự mở rộng int sang long khi gọi `BigInteger.valueOf`:

```java
int count = 12;
BigInteger n = BigInteger.valueOf(count);
```

Phương thức không “đoán” giá trị ban đầu đã nằm ngoài miền long: nếu một phép toán `long` bị tràn **trước** khi gọi, `valueOf` chỉ nhận giá trị sau tràn.

```java
long a = Long.MAX_VALUE;
long wrong = a + 1; // overflow thành Long.MIN_VALUE

BigInteger alreadyLost = BigInteger.valueOf(wrong);
BigInteger exact = BigInteger.valueOf(a).add(BigInteger.ONE);

System.out.println(alreadyLost); // -9223372036854775808
System.out.println(exact);       // 9223372036854775808
```

**Giữ dữ liệu chính xác ngay từ đầu:** với số nguyên literal ngoài miền `long`, tạo từ **chuỗi decimal** chứ không ép literal vào `long` rồi mới chuyển. Đối với phép tính có thể tràn, chuyển các toán hạng sang BigInteger **trước khi thực hiện phép cộng/nhân**.

### valueOf không phải parser chuỗi

`BigInteger.valueOf("123")` **không tồn tại**. Dùng:

```java
BigInteger fromText = new BigInteger("123");
BigInteger fromPrimitive = BigInteger.valueOf(123L);
```

Khác với `Integer.valueOf(String)` hoặc `Long.valueOf(String)`, BigInteger dùng constructor từ `String` để parse.

## 5. Tạo từ byte[]: Java mặc định hiểu signed two's complement

Constructor:

```java
BigInteger n = new BigInteger(byteArray);
```

giả định mảng biểu diễn **số nguyên theo bù hai có dấu** (*two's complement*) theo thứ tự **big-endian**: phần tử ở chỉ số 0 là byte có trọng số lớn nhất.

Ví dụ một byte:

```text
Byte hex    8 bit             BigInteger(byte[])
---------------------------------------------------
0x00        00000000                0
0x01        00000001                1
0x7F        01111111              127
0x80        10000000             -128
0xFF        11111111               -1
```

Trong Java:

```java
byte[] bytes = {(byte) 0xFF};
BigInteger signed = new BigInteger(bytes);
System.out.println(signed); // -1
```

**Tại sao `0xFF` lại thành −1 chứ không phải 255?** Bit cao nhất của byte đầu là bit dấu trong biểu diễn hai's complement dài 8 bit. Mẫu toàn bit 1 mô tả −1.

Nhiều byte có cùng quy tắc:

```java
BigInteger positive128 = new BigInteger(
        new byte[] {0x00, (byte) 0x80});
BigInteger negative256 = new BigInteger(
        new byte[] {(byte) 0xFF, 0x00});

System.out.println(positive128); // 128
System.out.println(negative256); // -256
```

Với `[00,80]`, byte `00` đầu tiên giữ bit dấu bằng 0; giá trị là 128. Với `[FF,00]`, mẫu 16 bit `11111111 00000000` là −256 theo bù hai.

**Đừng bỏ byte `00` đầu trong bản mã signed nếu byte kế tiếp có bit cao là 1.** Bỏ `00` khỏi `[00,FF]` biến số 255 thành −1.

## 6. Byte array không dấu: constructor (signum, magnitude)

Nhiều giao thức gửi **độ lớn unsigned** dưới dạng mảng byte: một octet `0xFF` có nghĩa 255 chứ không phải −1. Khi đó dùng:

```java
byte[] unsignedMagnitude = {(byte) 0xFF};
BigInteger n = new BigInteger(1, unsignedMagnitude);
System.out.println(n); // 255
```

Constructor `BigInteger(int signum, byte[] magnitude)` nhận:

- `signum = 1`: giá trị không âm/dương với độ lớn mảng byte;
- `signum = -1`: giá trị âm với độ lớn mảng byte;
- `signum = 0`: chỉ hợp lệ khi toàn bộ magnitude biểu diễn **0**.

Mảng `magnitude` chứa **độ lớn nguyên không dấu** big-endian. Các giá trị `byte` trong Java có thể âm khi cast hoặc in trực tiếp, nhưng constructor đọc **mẫu 8 bit** của từng byte như octet thuộc phần độ lớn.

Ví dụ:

```java
byte[] mag = {(byte) 0xFF};

System.out.println(new BigInteger(mag));    // -1: signed two's complement
System.out.println(new BigInteger(1, mag)); // 255: positive magnitude
System.out.println(new BigInteger(-1, mag));// -255: negative magnitude
```

Đây là **ba cách đọc khác nhau** từ cùng byte `FF`. Hãy đọc contract của định dạng nguồn trước khi chọn constructor.

### Signum sai gây lỗi

```java
// new BigInteger(0, new byte[] {1});
// NumberFormatException: signum 0 nhưng magnitude khác 0

// new BigInteger(2, new byte[] {1});
// NumberFormatException: signum không thuộc -1,0,1
```

Mảng độ lớn rỗng hoặc toàn số 0 biểu diễn giá trị **0** kể cả khi signum truyền vào là +1 hoặc −1:

```java
BigInteger zero = new BigInteger(1, new byte[0]);
System.out.println(zero);        // 0
System.out.println(zero.signum());// 0
```

BigInteger không có âm zero hay dương zero dưới dạng giá trị nguyên riêng biệt.

### Không nhầm signum với byte dấu

Tham số đầu tiên trong `new BigInteger(1, bytes)` là **chính dấu** do caller cung cấp; nó không lấy từ bit cao của dữ liệu byte. Nếu giao thức của bạn đã mã hóa dấu trong mảng byte bù hai, **đừng** thêm signum=1 tùy tiện vì sẽ làm số âm thành số dương.

## 7. Cấu trúc big-endian: byte có trọng số cao nhất nằm trước

Big-endian với byte unsigned `{0x01,0x00}`:

\[
1\times256 + 0=256
\]

```java
byte[] bytes = {0x01, 0x00};
System.out.println(new BigInteger(1, bytes)); // 256
```

Nếu nguồn của bạn là **little-endian**, byte thấp nhất nằm trước. Không được truyền thẳng mảng little-endian vào các constructor BigInteger rồi kỳ vọng đúng trị số.

Ví dụ số `0x0102 = 258`:

```java
byte[] bigEndian = {0x01, 0x02};
byte[] littleEndian = {0x02, 0x01};

System.out.println(new BigInteger(1, bigEndian));     // 258
System.out.println(new BigInteger(1, littleEndian)); // 513
```

Nếu giao thức little-endian, chuyển thứ tự byte **theo đúng đặc tả** hoặc dùng một bộ đọc byte theo endianness đã chọn rồi chuyển sang BigInteger. Không nên làm lật byte vô điều kiện: một số trường đã là big-endian, một số trường chỉ có một byte, một số trường dùng signed two's complement.

### ByteBuffer và toByteArray

`ByteBuffer` có cấu hình thứ tự byte riêng cho các phép đọc primitive đa byte. Nhưng `BigInteger(byte[])` **không nhận ByteBuffer và không tự đọc `ByteOrder`** của buffer. Chỉ khi đã có `byte[]` đúng thứ tự mong muốn mới truyền vào constructor thích hợp.

Khi đọc từ buffer với vị trí/giới hạn, kiểm tra byte nào thuộc token, không vô tình đưa header/checksum vào số.

## 8. Java 9+: constructor đọc một đoạn của byte[]

Java 9 bổ sung hai constructor có offset/length, đều có trong Java 17:

```java
new BigInteger(byte[] val, int off, int len);
new BigInteger(int signum, byte[] magnitude, int off, int len);
```

- `off`: chỉ số phần tử bắt đầu trong mảng (zero-based).
- `len`: **số byte cần đọc**, **không phải chỉ số kết thúc**.
- Hai phiên bản đều coi các byte được chọn theo **big-endian**.
- Phiên bản không signum đọc **two's complement signed**.
- Phiên bản có signum đọc **unsigned magnitude** có dấu ngoài mảng.

Ví dụ một buffer có byte header/trailer:

```java
byte[] packet = {0x55, 0x00, (byte) 0x80, 0x66};

// Lấy 2 byte ở [1,3): 00 80, signed = 128
BigInteger value = new BigInteger(packet, 1, 2);

// Lấy đúng 1 byte ở [2,3): 80, unsigned magnitude = 128
BigInteger positive = new BigInteger(1, packet, 2, 1);

System.out.println(value);    // 128
System.out.println(positive); // 128
```

Nếu dùng signed constructor với `off=2,len=1`, chỉ có byte `80` và kết quả là **−128**:

```java
System.out.println(new BigInteger(packet, 2, 1)); // -128
```

Đây là sai lầm **rất khó phát hiện nếu chỉ thử dữ liệu có bit đầu bằng 0**.

Chỉ số vượt miền gây `IndexOutOfBoundsException`. Với dữ liệu bù hai, khoảng rỗng không mô tả một số hợp lệ và có thể gây `NumberFormatException`; với constructor signum/magnitude, độ lớn rỗng là số 0 hợp lệ.

Một constructor đọc subarray giúp tránh việc **người gọi phải tạo mảng con** chỉ để parse. Nó **không cam kết zero-allocation nội bộ**, và không bảo đảm an toàn nếu mảng bị sửa đồng thời trong quá trình constructor đọc: yêu cầu caller giữ dữ liệu ổn định trong cuộc gọi.

## 9. toByteArray(): mã hóa ngược bằng bù hai, không phải magnitude unsigned

Phương thức `value.toByteArray()` trả byte **big-endian signed two's complement tối thiểu**. `BigInteger.valueOf(255).toByteArray()` là `00 FF`, còn `BigInteger.valueOf(-1).toByteArray()` là `FF`. Byte `00` bảo vệ dấu của số dương có bit cao bật; mảng này không phải magnitude unsigned. Vòng `new BigInteger(value.toByteArray())` khôi phục **giá trị số** chính xác, nhưng không cam kết khôi phục độ dài input gốc.

## 10. Dữ liệu unsigned có chiều rộng cố định: cần hợp đồng rõ ràng

Với một trường hai byte unsigned `FF FF`, `new BigInteger(bytes)` cho -1, còn `new BigInteger(1,bytes)` cho 65535. Khi **xuất** unsigned N byte, phải kiểm tra số không âm và vừa `0..2^(8N)-1`, loại đúng byte dấu `00` khi có, sau đó đệm `00` ở bên trái đến N byte. Không được cắt dữ liệu mang giá trị hoặc quên trường hợp zero; các codec signed/unsigned fixed-width và round trip chi tiết được thực hành ở bài chuyên về `toByteArray`.

## 11. Cảnh giác dữ liệu ASCII/UTF-8: byte chuỗi KHÔNG phải giá trị số

Nếu có văn bản số `"123"`, có thể nghĩ:

```java
byte[] textBytes = "123".getBytes(
        java.nio.charset.StandardCharsets.UTF_8);
BigInteger wrong = new BigInteger(textBytes);
```

Nhưng byte UTF-8 của ba ký tự `'1','2','3'` là `0x31,0x32,0x33`. `new BigInteger(textBytes)` sẽ diễn giải **ba byte này như số nguyên nhị phân big-endian**, không thực hiện phân tích decimal từ chuỗi.

Cách đúng:

```java
BigInteger fromDecimal = new BigInteger("123");
```

Ngược lại, nếu byte array đến từ một protocol truyền **giá trị số nhị phân**, không biến nó thành `new String(bytes, UTF_8)` rồi parse decimal: byte `0xFF` hoặc nhiều byte điều khiển không phải chữ số văn bản hợp lệ.

**Quy tắc lựa chọn:** xác định đầu vào là **văn bản biểu diễn số** hay **mẫu bit biểu diễn số**. Không thể thay lẫn hai cách này chỉ vì cả hai cuối cùng đều nằm trong `byte[]` ở tầng mạng.

## 12. Xử lý lỗi khi tạo BigInteger

Các lỗi cần phân biệt theo loại nguồn:

| Nguồn và tình huống | Lỗi / cách xử lý |
| --- | --- |
| `new BigInteger("12X")` radix 10 | `NumberFormatException` |
| `new BigInteger("FF", 1)` radix ngoài 2..36 | `NumberFormatException` |
| `new BigInteger("")` | `NumberFormatException` |
| `new BigInteger("0xFF",16)` | `NumberFormatException` |
| `new BigInteger(new byte[0])` | `NumberFormatException` |
| `new BigInteger(0,new byte[]{1})` | `NumberFormatException` |
| `new BigInteger(2,new byte[]{1})` | `NumberFormatException` |
| Constructor byte[] với off/len vượt phạm vi | `IndexOutOfBoundsException` |
| Tham số đối tượng `null` | `NullPointerException` |
| Giá trị quá lớn vượt khả năng triển khai/tài nguyên | Có thể lỗi giới hạn/`ArithmeticException` hoặc lỗi tài nguyên; không coi là vô hạn |

Ví dụ:

```java
try {
    BigInteger n = new BigInteger("0xFF", 16);
} catch (NumberFormatException ex) {
    System.out.println("Invalid numeric token");
}
```

Đừng bắt tất cả `RuntimeException` và trả `BigInteger.ZERO`. Giá trị 0 hoàn toàn hợp lệ, nên **không được** dùng như mã thay thế chung cho token sai nếu nghiệp vụ cần phân biệt dữ liệu trống/sai với số 0 thật.

Nếu đầu vào đến từ người dùng hoặc mạng, cân nhắc giới hạn số chữ số/byte trước khi parse để bảo vệ CPU/bộ nhớ, xác nhận radix, sign, endianness và độ dài của trường.

## 13. BigInteger không biến mọi phép chuyển đổi ngược thành an toàn

Tạo BigInteger từ `long` giữ đúng giá trị; nhưng đọc ngược về `long` có thể mất dữ liệu nếu số BigInteger vượt miền long:

```java
BigInteger huge = BigInteger.valueOf(Long.MAX_VALUE)
        .add(BigInteger.ONE);

System.out.println(huge);             // 9223372036854775808
System.out.println(huge.longValue()); // -9223372036854775808
```

`longValue()` giữ các bit thấp phù hợp theo quy tắc chuyển đổi, **không tự ném lỗi**. Nếu cần bảo toàn giá trị hoặc báo lỗi, dùng:

```java
try {
    long checked = huge.longValueExact();
} catch (ArithmeticException ex) {
    System.out.println("Value outside long range");
}
```

Tương tự có `intValueExact()`, `shortValueExact()`, `byteValueExact()`. Khi đầu vào BigInteger được tạo từ String rất dài hoặc unsigned field vượt phạm vi signed, **đừng** tự đổi về `int`/`long` chỉ vì kiểu nghiệp vụ trước đây dùng primitive.

Một BigInteger có thể chứa giá trị unsigned 64-bit từ mảng 8 byte `FF FF FF FF FF FF FF FF`, tương đương `18446744073709551615`, nhưng `longValue()` của nó là −1. Đây là **cùng bit thấp diễn giải signed**, không phải lỗi parser.

## 14. Immutable, equals và các hằng thường dùng

Các BigInteger bất biến. Ví dụ:

```java
BigInteger a = new BigInteger("123");
BigInteger b = a.add(BigInteger.ONE);

System.out.println(a); // 123
System.out.println(b); // 124
```

Các hằng có sẵn: `BigInteger.ZERO`, `BigInteger.ONE`, `BigInteger.TWO` (có từ Java 9) và `BigInteger.TEN`. Chúng phù hợp để thể hiện giá trị cố định mà không phải parse chuỗi nhiều lần:

```java
BigInteger zero = BigInteger.ZERO;
BigInteger power = BigInteger.TWO.pow(16);
System.out.println(power); // 65536
```

Dùng `equals` để kiểm tra giá trị:

```java
BigInteger x = new BigInteger("255");
BigInteger y = new BigInteger(1, new byte[]{(byte) 0xFF});

System.out.println(x.equals(y)); // true
```

Toán tử `==` so sánh **tham chiếu đối tượng**, không phải nội dung số. `BigInteger.valueOf` có thể tái sử dụng đối tượng cho một số giá trị; **không dựa vào identity/cache** để kiểm tra hai số bằng nhau.

## 15. Demo Java 17+ đầy đủ, đã kiểm chứng stdout

Tạo tệp **`BigIntegerConstructionDemo.java`**:

```java
import java.math.BigInteger;

public class BigIntegerConstructionDemo {
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }

    public static void main(String[] args) {
        BigInteger decimal =
                new BigInteger("123456789012345678901234567890");
        BigInteger negative = new BigInteger("-255");
        BigInteger hexNumber = new BigInteger("FF", 16);
        BigInteger binary = new BigInteger("101101", 2);
        BigInteger base36 = new BigInteger("ZZ", 36);
        BigInteger fromLong = BigInteger.valueOf(Long.MIN_VALUE);

        System.out.println("decimal = " + decimal);
        System.out.println("negative = " + negative);
        System.out.println("hex = " + hexNumber);
        System.out.println("binary = " + binary);
        System.out.println("base36 = " + base36);
        System.out.println("long minimum = " + fromLong);
        System.out.println("255 to hex = "
                + hexNumber.toString(16));

        byte[] oneByte = {(byte) 0xff};
        BigInteger signed = new BigInteger(oneByte);
        BigInteger unsigned = new BigInteger(1, oneByte);
        BigInteger prefixed = new BigInteger(
                new byte[] {0x00, (byte) 0xff});
        BigInteger negativeBytes = new BigInteger(
                new byte[] {(byte) 0xff, 0x00});

        System.out.println("0xff signed = " + signed);
        System.out.println("0xff magnitude = " + unsigned);
        System.out.println("00ff signed = " + prefixed);
        System.out.println("ff00 signed = " + negativeBytes);
        System.out.println("255 encoded = "
                + hex(unsigned.toByteArray()));
        System.out.println("-1 encoded = "
                + hex(signed.toByteArray()));
        System.out.println("0 encoded = "
                + hex(BigInteger.ZERO.toByteArray()));

        byte[] packet = {0x55, 0x00, (byte) 0x80, 0x66};
        BigInteger rangeSigned =
                new BigInteger(packet, 1, 2);
        BigInteger rangeMagnitude =
                new BigInteger(1, packet, 2, 1);
        System.out.println("range signed = " + rangeSigned);
        System.out.println("range magnitude = " + rangeMagnitude);
        System.out.println("bytes round-trip = "
                + unsigned.equals(
                        new BigInteger(unsigned.toByteArray())));
        System.out.println("empty magnitude = "
                + new BigInteger(1, new byte[0]));

        try {
            new BigInteger("0xFF", 16);
        } catch (NumberFormatException ex) {
            System.out.println("prefix: rejected");
        }
        try {
            new BigInteger("1_000");
        } catch (NumberFormatException ex) {
            System.out.println("underscore: rejected");
        }
        try {
            new BigInteger("FF", 1);
        } catch (NumberFormatException ex) {
            System.out.println("radix: rejected");
        }
        try {
            new BigInteger(new byte[0]);
        } catch (NumberFormatException ex) {
            System.out.println("empty signed bytes: rejected");
        }
        try {
            new BigInteger(0, new byte[] {1});
        } catch (NumberFormatException ex) {
            System.out.println("signum mismatch: rejected");
        }
        try {
            new BigInteger(packet, 3, 2);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("range: rejected");
        }
        try {
            BigInteger.valueOf(Long.MAX_VALUE)
                    .add(BigInteger.ONE).longValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("long exact: rejected");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 BigIntegerConstructionDemo.java
java BigIntegerConstructionDemo
```

**Stdout đã đối chiếu bằng một chương trình biên dịch với `javac --release 17`:**

```text
decimal = 123456789012345678901234567890
negative = -255
hex = 255
binary = 45
base36 = 1295
long minimum = -9223372036854775808
255 to hex = ff
0xff signed = -1
0xff magnitude = 255
00ff signed = 255
ff00 signed = -256
255 encoded = 00ff
-1 encoded = ff
0 encoded = 00
range signed = 128
range magnitude = 128
bytes round-trip = true
empty magnitude = 0
prefix: rejected
underscore: rejected
radix: rejected
empty signed bytes: rejected
signum mismatch: rejected
range: rejected
long exact: rejected
```

### Cách đọc kết quả

1. `new BigInteger("...")` giữ số nguyên decimal dài, `new BigInteger("FF",16)` nhận 255, `valueOf(Long.MIN_VALUE)` giữ đúng biên âm long.
2. Cùng byte `FF` được đọc **−1** khi dùng signed constructor, nhưng **255** khi dùng signum +1.
3. `00 FF` là bản mã signed dương của 255; `FF 00` biểu diễn signed −256.
4. `toByteArray()` không hứa một byte cho 255; nó cần thêm `00` đầu để giữ dấu dương.
5. Các constructor có `off,len` cho phép đọc một vùng byte mà không cần `Arrays.copyOfRange` ở phía caller.
6. Những chuỗi chứa prefix/underscore, radix sai, mảng signed rỗng, signum sai, phạm vi sai và chuyển đổi long không an toàn đều có cách báo lỗi riêng.

## 16. Lỗi phổ biến cần tránh

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| “BigInteger có constructor nhận long” | Dùng `BigInteger.valueOf(long)` |
| “valueOf nhận chuỗi như Integer.valueOf” | BigInteger parse chuỗi bằng `new BigInteger(String)` |
| “BigInteger(String) tự nhận `0xFF`” | Constructor mặc định radix 10; với hex truyền `"FF",16` |
| “`new BigInteger("1_000")` giống literal Java `1_000`” | Parser runtime không hiểu dấu gạch dưới như cú pháp mã nguồn |
| “Tạo từ String thì không thể vượt giới hạn long” | Chuỗi decimal có thể dài hơn miền long nhiều lần |
| “Giá trị byte 0xFF luôn là 255” | `BigInteger(byte[])` diễn giải signed `FF` thành −1 |
| “Mảng byte theo little-endian truyền trực tiếp là đúng” | Constructor BigInteger dùng big-endian |
| “`new BigInteger(1,bytes)` vẫn suy dấu từ bit cao” | Signum=1 buộc cách đọc **độ lớn dương** |
| “`toByteArray` trả unsigned magnitude cố định N byte” | Nó trả signed two's complement, độ dài biến thiên |
| “Byte `00` đầu luôn thừa” | Có thể là byte cần thiết giữ sign dương khi byte kế có bit cao 1 |
| “Mảng signed rỗng và magnitude rỗng có cùng hành vi” | Signed rỗng bị từ chối; magnitude rỗng có thể cho zero |
| “Offset,length nghĩa là hai vị trí đầu/cuối” | `len` là số byte, không phải endIndex |
| “BigInteger có nghĩa là tài nguyên vô hạn” | Vẫn chịu giới hạn bộ nhớ, CPU và hỗ trợ JVM |
| “big.longValue() luôn an toàn” | Có thể cắt bit; dùng `longValueExact()` nếu cần toàn vẹn |
| “So sánh BigInteger bằng ==” | Dùng `equals` hoặc `compareTo` theo giá trị |
| “Có thể tạo BigInteger từ byte UTF-8 của chuỗi số” | Byte mã hóa ký tự không đồng nghĩa byte biểu diễn số |
| “Đổi số sang BigInteger sau khi tràn sẽ sửa tràn” | Phải chuyển toán hạng **trước** phép tính có thể tràn |

## 17. Bài tập có lời giải

**Bài 1.** Tạo BigInteger từ số decimal `"987654321098765432109876543210"` mà không đi qua long.  
**Lời giải:** `new BigInteger("987654321098765432109876543210")`; không có bước chuyển sang primitive.

**Bài 2.** Giá trị của `new BigInteger("101101",2)`?  
**Lời giải:** `32+8+4+1=45`. Chuỗi nhị phân không cần tiền tố `0b`.

**Bài 3.** Giá trị của `new BigInteger("FF",16)`? Vì sao `"0xFF"` khác?  
**Lời giải:** 255. Constructor radix 16 không tự bỏ prefix `0x`; `new BigInteger("0xFF",16)` ném `NumberFormatException`.

**Bài 4.** Tạo từ `Long.MIN_VALUE` thế nào?  
**Lời giải:** `BigInteger.valueOf(Long.MIN_VALUE)`. Không cần dựng chuỗi, không có constructor `BigInteger(long)`.

**Bài 5.** Hai biểu thức `new BigInteger(new byte[]{(byte)0x80})` và `new BigInteger(1,new byte[]{(byte)0x80})` bằng bao nhiêu?  
**Lời giải:** −128 và +128. Mẫu bit `80` là signed two's complement âm nhưng magnitude unsigned là 128.

**Bài 6.** Vì sao `new BigInteger(new byte[]{0x00,(byte)0xFF})` là 255?  
**Lời giải:** Byte 00 đầu có bit dấu 0, mẫu 16 bit `00FF` là +255. Bỏ 00 còn `FF` sẽ bị hiểu là −1 dưới signed constructor.

**Bài 7.** Byte mảng `{0x01,0x00}` big-endian unsigned biểu diễn giá trị nào?  
**Lời giải:** `0x0100 = 256`. Dùng `new BigInteger(1,bytes)` nếu contract dữ liệu là unsigned magnitude.

**Bài 8.** `new BigInteger(0,new byte[]{1})` có giá trị là 0 không?  
**Lời giải:** Không; ném `NumberFormatException` vì signum=0 không khớp với magnitude khác 0.

**Bài 9.** Với `byte[] packet={0x55,0x00,(byte)0x80,0x66}`, `new BigInteger(packet,1,2)` lấy những byte nào?  
**Lời giải:** Byte ở chỉ số 1 và 2 (`00 80`), kết quả 128; `len=2` là chiều dài, không phải chỉ số kết thúc.

**Bài 10.** `BigInteger.valueOf(255).toByteArray()` có chắc chỉ chứa một byte `FF` không?  
**Lời giải:** Không; nó trả `00 FF` ở dạng signed, vì `FF` đơn lẻ biểu diễn −1.

**Bài 11.** Một trường mạng hai byte có `FF FF` và được định nghĩa là unsigned. Dùng constructor nào và giá trị nào?  
**Lời giải:** `new BigInteger(1,new byte[]{(byte)0xFF,(byte)0xFF})`, cho 65535. Signed constructor sẽ cho −1.

**Bài 12.** `new BigInteger("-0")` và `new BigInteger(1,new byte[0])` có giá trị khác nhau không?  
**Lời giải:** Không. Cả hai là số nguyên zero; BigInteger không có ±0 như floating-point.

**Bài 13.** Muốn chuyển BigInteger rất lớn về long mà **không mất dữ liệu** thì làm gì?  
**Lời giải:** Dùng `longValueExact()`. Nếu không biểu diễn được trong long, phương thức ném `ArithmeticException` thay vì âm thầm bỏ bit.

**Bài 14.** Vì sao `new BigInteger("123".getBytes(UTF_8))` không tương đương `new BigInteger("123")`?  
**Lời giải:** Cái đầu đọc mẫu byte `31 32 33` như số nhị phân; cái sau parse ba chữ số decimal thành 123.

**Bài 15.** Chương trình lấy `BigInteger.valueOf(a+b)` với a,b là long lớn. Khi nào có thể sai?  
**Lời giải:** Nếu `a+b` tràn **trước khi được chuyển sang BigInteger**; sửa thành `BigInteger.valueOf(a).add(BigInteger.valueOf(b))`.

## 18. Checklist chọn cách tạo BigInteger

1. **Nhận diện định dạng nguồn:** text decimal, text radix khác, primitive long, byte array signed hay unsigned?
2. **Với String:** quyết định radix 2–36, chính sách dấu, khoảng trắng, prefix, ASCII/Unicode digits và giới hạn độ dài.
3. **Với long:** dùng `valueOf(long)`, nhưng bảo đảm phép toán trước đó chưa overflow.
4. **Với byte[] signed:** dùng `new BigInteger(bytes)` và hiểu big-endian two's complement.
5. **Với byte[] unsigned:** dùng `new BigInteger(1,bytes)` hoặc signum theo hợp đồng, không suy dấu từ byte đầu.
6. **Với byte subarray:** constructor `(off,len)` từ Java 9+, chú ý `len` là chiều dài và input phải ổn định khi được đọc.
7. **Khi lưu ngược ra byte[]:** `toByteArray()` là signed, độ dài biến thiên; cần quy tắc riêng nếu protocol unsigned/fixed-width.
8. **Không trộn ASCII bytes và binary bytes**: byte mã hóa ký tự `'1'` không phải số nguyên 1.
9. **Kiểm tra lỗi riêng:** parse/radix/signed byte rỗng, signum sai, index sai, null, vượt giới hạn tài nguyên.
10. **Khi chuyển về primitive:** dùng các phương thức `*ValueExact` nếu nghiệp vụ không được mất thông tin.
11. **So sánh bằng `equals`**, không dùng `==` cho hai BigInteger.
12. **Dùng bài test biên:** 0, −1, 127, 128, 255, 256, long min/max, mảng trống, byte đầu `80`/`FF`, little-/big-endian và số vượt chiều rộng trường.

### Tài liệu tham khảo

- [BigInteger — Javadoc Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html)
- [Character.digit — Javadoc Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html#digit(char,int))
- [ByteBuffer và ByteOrder — Javadoc Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)

**Ghi nhớ:** `BigInteger` có thể khởi tạo từ số lớn thập phân, văn bản ở cơ số 2–36, `long` hoặc `byte[]`. Với mảng byte, phải phân biệt **signed two's complement** với **unsigned sign-magnitude**, giữ đúng **big-endian** và xác định byte dấu. Đây là yếu tố quyết định chương trình hiểu đúng giá trị, không chỉ là khác biệt cú pháp constructor.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-003">Biểu diễn byte bù hai có dấu và constructor sign-magnitude</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger: Biểu diễn byte bù hai có dấu và constructor sign-magnitude

Trong Java Core, `java.math.BigInteger` là kiểu **số nguyên có dấu với độ chính xác tùy ý**. Khi chuyển giữa `BigInteger` và mảng `byte[]`, cần đặc biệt phân biệt **hai cách giải mã hoàn toàn khác nhau**:

- **Bù hai có dấu (signed two's complement)**: `new BigInteger(byte[] val)` đọc mảng byte như *một số nguyên có dấu*; bit cao nhất của byte đầu tiên là **bit dấu**.
- **Dấu và độ lớn (sign–magnitude)**: `new BigInteger(int signum, byte[] magnitude)` nhận **dấu riêng** (`-1`, `0`, `1`) và coi *toàn bộ mảng byte là độ lớn không dấu*.

Cùng một byte có thể cho hai kết quả trái ngược:

```java
byte[] input = {(byte) 0x80};

BigInteger signed = new BigInteger(input);     // -128
BigInteger unsigned = new BigInteger(1, input); // +128
```

**Vì sao?** Mẫu 8 bit `1000_0000` có giá trị **-128** khi hiểu là số nguyên signed two's complement 8 bit; cùng mẫu đó có giá trị **128** khi hiểu như một *magnitude không dấu* và cung cấp dấu dương bên ngoài.

Đây là kiến thức rất quan trọng khi xử lý **khóa mật mã, giao thức nhị phân, hash, số nguyên độ dài biến đổi và serialization**. Chỉ thiếu một byte `00` bảo vệ dấu cũng đủ làm một số nguyên dương lớn biến thành âm.

## 1. BigInteger lưu số nguyên, không cố định 8/16/32/64 bit

`BigInteger` lưu số nguyên với dấu và độ dài tùy thuộc trị tuyệt đối, không giống `byte`, `short`, `int` hoặc `long` có độ rộng cố định. Với đầu vào dạng chuỗi:

```java
import java.math.BigInteger;

BigInteger p = new BigInteger("255");     // +255
BigInteger n = new BigInteger("-255");    // -255
BigInteger h = new BigInteger("ff", 16);  // +255
```

Các đoạn khai báo ở trên là **mảnh mã** đặt trong một lớp/phương thức phù hợp, không phải chương trình chạy độc lập. Khả năng biểu diễn số lớn **không có nghĩa các constructor byte đều coi dữ liệu là không dấu**. Constructor khác nhau mang **hợp đồng biểu diễn khác nhau**, dù kết quả đều là `BigInteger`.

| Constructor/API | Ý nghĩa | Giá trị ví dụ |
| --- | --- | --- |
| `new BigInteger(byte[] bytes)` | Byte big-endian, **signed two's complement** | `[FF]` → -1 |
| `new BigInteger(1, byte[] magnitude)` | Byte big-endian, **magnitude không dấu**, ép dấu dương | `[FF]` → +255 |
| `new BigInteger(-1, byte[] magnitude)` | Magnitude không dấu, ép dấu âm | `[FF]` → -255 |
| `new BigInteger("ff", 16)` | Parse chuỗi số có cơ số 16 | +255 |
| `big.toByteArray()` | Mảng byte big-endian, **signed two's complement tối thiểu** | +255 → `[00 FF]` |
| `big.signum()` | Trả dấu của **giá trị đã tạo** | -255 → -1 |

`new BigInteger("ff",16)` và `new BigInteger(new byte[]{(byte)0xff})` **không có cùng kết quả**. Cái đầu đọc chữ số hex theo giá trị dương, cái sau đọc byte bit cao bật như số bù hai âm.

## 2. Hai định dạng đều dùng byte lớn trước (big-endian)

Các constructor mảng byte của `BigInteger` dùng **big-endian**: byte **quan trọng nhất ở đầu mảng**. Ví dụ mảng:

```text
Byte 0   Byte 1
0x01     0x00
```

biểu diễn độ lớn `0x0100 = 256`, không phải `0x0001 = 1`. Quy tắc thứ tự byte áp dụng cho cả **signed two's complement** lẫn **sign–magnitude**; sự khác biệt nằm ở **cách hiểu bit dấu**, không phải cách đảo byte.

Với dữ liệu nhị phân little-endian (chẳng hạn một định dạng file hoặc giao thức tự định nghĩa), **không được truyền thẳng** mảng little-endian vào `BigInteger` rồi mong đúng. Cần đảo vị trí byte thành big-endian theo hợp đồng định dạng, đồng thời vẫn phải chọn cách diễn giải signed hay unsigned thích hợp.

Lưu ý: Big-endian nói về **vị trí các byte có ý nghĩa**, không phải cách các ký tự hex được in trên màn hình. Cũng không phụ thuộc CPU của máy đang chạy little-endian hay big-endian: constructor đã quy định thứ tự mảng byte.

## 3. Bù hai có dấu (two's complement) hoạt động ra sao?

Với một mảng `n` byte, ta có độ rộng `w = 8n` bit. Trong **two's complement**:

- Nếu bit cao nhất bằng 0, giá trị là không âm và được đọc như số nguyên nhị phân thông thường.
- Nếu bit cao nhất bằng 1, giá trị là **âm**, bằng giá trị unsigned của toàn bộ mẫu bit **trừ `2^w`**.

Công thức cho mẫu bit có độ rộng cố định:

```text
U = giá trị unsigned của toàn bộ w bit

signedValue =
    U            nếu bit cao nhất = 0
    U - 2^w      nếu bit cao nhất = 1
```

Ví dụ một byte (`w=8`, `2^8=256`):

| Byte hex | Bit nhị phân | Unsigned | Signed two's complement |
| --- | --- | ---: | ---: |
| `00` | `0000_0000` | 0 | 0 |
| `01` | `0000_0001` | 1 | 1 |
| `7F` | `0111_1111` | 127 | 127 |
| `80` | `1000_0000` | 128 | **-128** |
| `FF` | `1111_1111` | 255 | **-1** |

Với hai byte `FF 7F`, trị unsigned là `65407`; `65407 - 65536 = -129`. Vì vậy:

```java
new BigInteger(new byte[]{(byte) 0xff, 0x7f}); // -129
```

Đây là mảng **16 bit bù hai có dấu**. Chỉ cần nhớ bit đầu tiên và số byte là đủ xác định miền có dấu của mảng: với một byte `FF` cho -1, với hai byte `00 FF` lại cho +255.

## 4. Constructor `BigInteger(byte[] val)`: dữ liệu được hiểu là signed

Chữ ký:

```java
public BigInteger(byte[] val)
```

Nhận một mảng byte **big-endian signed two's complement**. Ví dụ:

```java
BigInteger a = new BigInteger(new byte[]{0x7f});          // +127
BigInteger b = new BigInteger(new byte[]{(byte) 0x80});   // -128
BigInteger c = new BigInteger(new byte[]{(byte) 0xff});   // -1
BigInteger d = new BigInteger(new byte[]{0, (byte) 0x80}); // +128
```

Hai byte `00 80` khác mảng `80`: tiền tố `00` khiến **bit dấu toàn mảng bằng 0**, bảo đảm số dương. Byte `80` ở vị trí sau vẫn đóng góp đúng 128 cho độ lớn.

Nếu `val` rỗng (`new byte[0]`), constructor **ném `NumberFormatException`**: signed two's complement không thể biểu diễn một giá trị từ zero byte đầu vào theo hợp đồng này. Muốn tạo số 0, dùng `BigInteger.ZERO` hoặc mảng chứa một byte `00`.

Các giá trị `byte` trong Java tự chúng có dấu khi đọc như số nguyên đơn lẻ (`(byte)0xff == -1`). Tuy nhiên, khi tạo `BigInteger` từ mảng, Java ghép các **mẫu 8 bit** theo thứ tự, rồi diễn giải **dấu của toàn số**. Không được cộng các giá trị `byte` có dấu từng phần để mô phỏng kết quả mảng bù hai.

## 5. Constructor `BigInteger(int signum, byte[] magnitude)`: dấu riêng, độ lớn không dấu

Chữ ký:

```java
public BigInteger(int signum, byte[] magnitude)
```

Trong đó:

- **`signum`** chỉ có thể là `-1` (âm), `0` (zero), `1` (dương).
- **`magnitude`** là mảng byte big-endian chứa **độ lớn không dấu**, ngay cả khi bit cao nhất của byte đầu tiên bằng 1.
- Giá trị được tạo về mặt toán học là **`signum × magnitudeValue`** (với các điều kiện nhất quán đối với zero).

```java
byte[] bits = {(byte)0xff};

BigInteger positive = new BigInteger(1, bits);  // +255
BigInteger negative = new BigInteger(-1, bits); // -255
```

Đây chính là cách tạo số **dương** từ một mảng byte có bit đầu bật mà **không cần thêm byte `00`** vào input.

```java
BigInteger p = new BigInteger(1, new byte[]{(byte)0x80}); // +128
BigInteger n = new BigInteger(-1, new byte[]{(byte)0x80}); // -128
```

**Cảnh báo:** `signum = -1` **không có nghĩa** mảng `magnitude` cũng phải là dạng bù hai của số âm. Với magnitude `FF`, dấu -1 tạo **-255**, không phải -1. Dữ liệu đầu vào phải đúng chuẩn sign–magnitude đã thỏa thuận.

## 6. Zero và quy tắc nhất quán của `signum`

Giá trị zero có độ lớn 0, dù bạn biểu diễn bằng `byte[]{0}`, mảng nhiều byte `00`, hoặc thậm chí mảng magnitude rỗng theo overload sign–magnitude.

| `signum` | Magnitude | Kết quả |
| ---: | --- | --- |
| `0` | `[00]` | 0 |
| `0` | `[00, 00]` | 0 |
| `+1` | `[00]` hoặc rỗng | 0 |
| `-1` | `[00]` | 0 |
| `0` | `[01]` | **`NumberFormatException`** |
| `2` hoặc `-2` | Bất kỳ magnitude | **`NumberFormatException`** |

Quy tắc quan trọng: **không thể yêu cầu dấu 0 nhưng magnitude khác 0**; `new BigInteger(0,new byte[]{1})` bị từ chối. Nếu magnitude thực sự bằng 0 nhưng signum là -1, kết quả BigInteger vẫn là **zero chuẩn hóa**, không có “negative zero” số nguyên.

```java
BigInteger zero = new BigInteger(-1, new byte[]{0, 0});
System.out.println(zero.signum()); // 0
```

Điều này khác floating-point `-0.0`: số nguyên `BigInteger` **không có hai giá trị zero khác dấu**. Khi dùng API sign–magnitude, cần kiểm tra việc gán dấu 0 nhất quán với magnitude để tránh tạo ra ngoại lệ không mong muốn.

Phân biệt thêm: `new BigInteger(new byte[0])` **lỗi** vì constructor signed cần ít nhất một byte; trong khi `new BigInteger(1,new byte[0])` cho giá trị **0**, như chương trình QA bên dưới.

## 7. Bảng đối chiếu từ cùng một mảng byte

| Mảng byte (hex) | `new BigInteger(bytes)` | `new BigInteger(1,bytes)` | `new BigInteger(-1,bytes)` |
| --- | ---: | ---: | ---: |
| `00` | 0 | 0 | 0 |
| `01` | 1 | 1 | -1 |
| `7F` | 127 | 127 | -127 |
| `80` | **-128** | **128** | -128 |
| `FF` | **-1** | **255** | **-255** |
| `00 80` | 128 | 128 | -128 |
| `FF 7F` | -129 | 65407 | -65407 |

Dòng `FF 7F` là ví dụ quan trọng: dưới cách đọc **signed 16 bit** nó là -129; dưới cách đọc **magnitude** nó là `0xFF7F = 65407`, và dấu ngoài quyết định +65407 hoặc -65407. Việc hai constructor cho kết quả **cùng âm** trong trường hợp `80` và signum -1 chỉ là một trùng hợp của giá trị cụ thể, không làm chúng tương đương tổng quát.

Nếu đầu vào đến từ mạng/giao thức, bắt buộc biết rõ trường đó dùng **signed two's complement** hay **unsigned magnitude**. Không thể suy ra cách đọc đúng chỉ từ tên biến `bytes`, số byte hoặc việc chúng được in thành chữ số hex.

## 8. `BigInteger.toByteArray()`: signed two's complement tối thiểu

Để xuất lại `BigInteger` về byte, dùng:

```java
byte[] encoded = bigInteger.toByteArray();
```

API này trả về mảng **big-endian signed two's complement**, **số byte tối thiểu cần thiết** để giữ giá trị và dấu. Nó **không** trả magnitude unsigned đơn thuần và **không** giữ nguyên số byte đã nhập vào constructor trước đó.

Ví dụ:

| BigInteger value | `HexFormat.of().formatHex(value.toByteArray())` | Giải thích |
| ---: | --- | --- |
| 0 | `00` | Zero cần một byte `00` |
| +1 | `01` | Số dương nhỏ |
| +127 | `7f` | Bit cao = 0 |
| **+128** | **`0080`** | Thêm `00` để không bị hiểu -128 |
| **+255** | **`00ff`** | Thêm `00` bảo vệ dấu |
| -1 | `ff` | Bù hai một byte |
| -128 | `80` | Giá trị âm hợp lệ với một byte |
| **-129** | **`ff7f`** | Cần byte `ff` giữ dấu âm |

`toByteArray` đảm bảo *độ dài tối thiểu*, nên đừng coi `value.toByteArray().length == 4` hay 8 là bất biến chỉ vì giá trị được dùng trong một protocol 32/64 bit.

Ví dụ quan trọng:

```java
BigInteger positive = new BigInteger(1, new byte[]{(byte)0xff});
byte[] encoded = positive.toByteArray();
// encoded gồm hai byte [00 FF], không phải một byte [FF].
```

Nếu truyền `encoded` cho `new BigInteger(encoded)`, ta nhận lại +255 chính xác. Nếu tự ý xóa `00` rồi dùng signed constructor, ta lại nhận -1.

## 9. Vì sao cần byte `00` đầu với số dương?

Với signed two's complement, bit cao nhất của byte đầu tiên **quyết định dấu**. Số dương 128 có mẫu nhị phân `1000_0000`; nếu chỉ có một byte thì mẫu bit đó là **-128**. Để giữ dấu dương trong định dạng signed, thêm byte `00` phía trước:

```text
Magnitude 128:              80
Signed two's complement: 00 80  => +128
Signed two's complement:    80  => -128

Magnitude 255:              FF
Signed two's complement: 00 FF  => +255
Signed two's complement:    FF  => -1
```

Byte `00` đầu không phải padding vô nghĩa trong signed encoding; nó có thể là **điều kiện để đọc đúng dấu**. Nhưng khi dữ liệu thực sự là **unsigned magnitude**, byte `00` bảo vệ dấu có thể không được mong đợi; vì thế cùng một mảng được mã hóa theo hai contract khác nhau.

Không nên nghĩ `0x80` “mặc định là -128 trong mọi ngữ cảnh”. Nó là mẫu bit; **ý nghĩa số học tùy cách diễn giải**. Cách diễn giải đó phải được chọn khi tạo `BigInteger`.

## 10. Byte `FF` đầu với số âm và sign extension

Hai's complement có quy tắc **mở rộng dấu** (*sign extension*): để tăng độ rộng mà vẫn giữ một giá trị âm, bổ sung các byte `FF` ở bên trái; với giá trị không âm, bổ sung `00`.

```text
-1 ở 8 bit:          FF
-1 ở 16 bit:      FF FF
-1 ở 32 bit: FF FF FF FF

+255 ở 16 bit:      00 FF
+255 ở 32 bit: 00 00 00 FF
```

`BigInteger.toByteArray()` bỏ các byte mở rộng dấu **thừa**, nhưng giữ byte tối thiểu còn đủ thể hiện dấu. Ví dụ -1 được biểu diễn bằng một byte `FF`, không phải mảng vô hạn byte `FF`. -129 cần hai byte `FF 7F`, vì một byte signed không đủ chứa -129.

Với một hệ thống gửi đúng 4 byte cho signed 32 bit, mảng `FF` phải được **mở rộng thành `FF FF FF FF`** nếu muốn truyền -1. Ngược lại, nếu giao thức dùng unsigned 32 bit, bạn thường cần **zero-extension**; truyền `FF FF FF FF` trong signed BigInteger constructor sẽ cho -1 thay vì unsigned 4294967295.

## 11. `signum` trong constructor không đồng nghĩa với `signum()` của kết quả

`new BigInteger(signum,magnitude)` nhận một **tham số dấu được yêu cầu**. Sau khi tạo, `result.signum()` trả dấu của **giá trị thực sự**, luôn là -1, 0 hoặc 1.

Ví dụ:

```java
BigInteger zero = new BigInteger(-1, new byte[]{0});
System.out.println(zero.signum()); // 0

BigInteger negative = new BigInteger(-1, new byte[]{1});
System.out.println(negative.signum()); // -1
```

Không nên gán `signum` dựa trên một byte có dấu như `(byte)0x80 == -128`, vì constructor chỉ nhận đúng -1, 0, 1; -128 là một **giá trị byte**, không phải dấu hợp lệ của `BigInteger`.

Nếu bạn đang chuyển dữ liệu từ hệ thống có trường `sign` riêng và mảng `magnitude`, hãy ánh xạ `sign` sang -1/0/1 theo format, kiểm tra mismatch zero, rồi gọi constructor. Không dùng bit đầu mảng magnitude để suy đoán dấu nếu contract đã có dấu riêng.

## 12. Các overload có offset và length

Java 17 có overload nhận **một đoạn của mảng**, tránh việc bắt buộc tạo bản sao bằng `Arrays.copyOfRange`:

```java
BigInteger signedPart =
        new BigInteger(byte[] val, int off, int len);

BigInteger magnitudePart =
        new BigInteger(int signum, byte[] magnitude, int off, int len);
```

Hai dòng trên là **chữ ký ý niệm**, không phải cú pháp khai báo biến hợp lệ — chúng dùng để liệt kê hình thức constructor.

Ví dụ runnable khi đặt trong phương thức:

```java
byte[] data = {(byte)0x7e, 0x00, (byte)0x80, 0x7f};

BigInteger signed = new BigInteger(data, 1, 2);
// Đọc [00,80] thành +128 (signed two's complement).

BigInteger magnitude = new BigInteger(1, data, 2, 1);
// Đọc [80] thành +128 (positive magnitude).
```

`off` là **chỉ số bắt đầu**, `len` là **số byte cần đọc**, không phải chỉ số kết thúc. Nếu `off` và `len` vượt mảng, API ném lỗi chỉ số/đoạn. Với signed constructor, đoạn rỗng không hợp lệ vì không có byte biểu diễn; với magnitude constructor, độ lớn rỗng có thể tạo zero nếu dấu hợp lệ.

Giống nguyên lý ở phần đầu, cả hai overload vẫn sử dụng **big-endian** trong phạm vi đoạn đã chọn. Việc truyền slice không thay đổi quy tắc signed hay magnitude, nên cần kiểm tra đúng trường dữ liệu của bản ghi.

## 13. `BigInteger("ff",16)` khác `BigInteger(byte[]{FF})` thế nào?

Một chuỗi chữ số hex có **ngữ nghĩa số học**, không phải một mảng byte signed:

```java
BigInteger byText = new BigInteger("ff", 16); // +255

byte[] oneByte = {(byte)0xff};
BigInteger bySigned = new BigInteger(oneByte); // -1
BigInteger byMagnitude = new BigInteger(1, oneByte); // +255
```

Vì `"ff"` không có dấu `-`, constructor chuỗi hiểu là độ lớn 255. Với constructor mảng signed, byte `FF` là -1. Với sign–magnitude, dấu 1 làm byte `FF` biểu diễn +255.

Đặc biệt trong hệ thống lưu hash, nếu bạn chuyển một mảng byte sang **chuỗi hex** rồi dùng `new BigInteger(hex,16)`, kết quả sẽ là **dương** (trừ khi chuỗi có dấu âm); nếu dùng `new BigInteger(bytes)` trực tiếp, kết quả có thể âm. Đây là lý do phổ biến khiến cùng một digest có hai biểu diễn số nguyên khác nhau.

Nếu format thực chất là **bytes không dấu** chứ không phải số nguyên có dấu, phương thức rõ ý nghĩa là `new BigInteger(1,bytes)`. Dù vậy, hash/identifier có thể cần **giữ nguyên độ dài byte và zero đầu**, trong khi `BigInteger` biểu diễn **giá trị số** và có thể chuẩn hóa mất zero padding khi serialize lại.

## 14. BigInteger là giá trị số, không phải kho byte giữ nguyên định dạng

Giả sử input là ba byte:

```text
00 00 FF
```

Nếu gọi:

```java
BigInteger value =
        new BigInteger(1, new byte[]{0, 0, (byte)0xff});
// value = 255.
```

thì `value.toByteArray()` trả **`00 FF`**, không phải `00 00 FF`. Không có cách nào từ riêng giá trị số 255 biết input ban đầu có bao nhiêu byte `00` đệm trước đó.

Vì vậy, nếu protocol có **trường cố định 16/32 byte**, việc lưu duy nhất dưới dạng `BigInteger` là chưa đủ cho round-trip *byte-for-byte*. Bạn phải lưu **độ dài định dạng** hoặc quy tắc pad/cắt byte, và tái tạo đúng số byte lúc xuất. Nếu format dùng big-endian unsigned 32 byte, cần **zero-pad**, không phải dấu-extend `FF`. Nếu format dùng signed two's complement fixed-width, cần **sign-extend**.

Đừng bỏ byte `00` đầu một cách vô điều kiện: với mảng signed, đó có thể là byte thiết yếu để phân biệt +255 với -1. Chỉ bỏ khi bạn đã chủ động đổi từ signed representation sang **unsigned magnitude** và đích cũng hiểu theo contract đó.

## 15. Cẩn thận khi convert qua primitive `byte`, `int`, `long`

Java `byte` là signed trong phạm vi -128..127. Dù một byte `FF` tương ứng 255 khi xem unsigned, phép đọc trực tiếp:

```java
byte raw = (byte)0xff;
System.out.println(raw);                    // -1
System.out.println(Byte.toUnsignedInt(raw)); // 255
```

`Byte.toUnsignedInt` chỉ chuyển **một byte** thành giá trị 0..255 để quan sát/xử lý; nó không biến `new BigInteger(byte[])` thành unsigned constructor.

Ngoài ra, `BigInteger.intValue()` và `longValue()` có thể **mất các bit cao** với giá trị lớn. Ví dụ:

```java
BigInteger big = new BigInteger(1, new byte[]{
        0x01, 0x00, 0x00, 0x00, 0x00
}); // 2^32.
int lossy = big.intValue(); // 0 vì chỉ giữ 32 bit thấp.
```

Nếu cần chuyển sang primitive **mà không chấp nhận mất dữ liệu**, dùng `intValueExact()` hoặc `longValueExact()` và xử lý `ArithmeticException` khi giá trị ngoài dải. Không nên “sửa” một số âm xuất hiện sai do constructor signed bằng cách ép sang `long`/mask sau đó: trước tiên phải xác định lại **cách diễn giải byte có đúng format hay không**.

## 16. Fixed-width signed encoding: phải tự kiểm tra dải

`BigInteger.toByteArray()` trả **độ dài tối thiểu**, không thể tự bảo đảm tạo đúng 2, 4, 8 hay 32 byte. Với yêu cầu **signed two's complement w byte**, dải chính xác là:

```text
min = -2^(8*w-1)
max =  2^(8*w-1)-1
```

Chẳng hạn signed **một byte** chỉ chứa -128..127. +128 cần ít nhất **hai byte `00 80`**. Không được cắt chuỗi `00 80` thành `80` rồi giải mã signed vì sẽ thành -128.

Một phương thức mẫu (đặt trong lớp có import `java.util.Arrays`, `java.math.BigInteger`) để ghi đúng **w byte signed**:

```java
static byte[] toFixedSigned(BigInteger value, int width) {
    if (width <= 0 || width > Integer.MAX_VALUE / 8) {
        throw new IllegalArgumentException("invalid width");
    }
    int bits = width * 8;
    BigInteger bound = BigInteger.ONE.shiftLeft(bits - 1);
    if (value.compareTo(bound.negate()) < 0
            || value.compareTo(bound.subtract(BigInteger.ONE)) > 0) {
        throw new ArithmeticException("out of signed range");
    }
    byte[] minimal = value.toByteArray();
    byte[] result = new byte[width];
    Arrays.fill(result, value.signum() < 0 ? (byte) 0xff : (byte) 0x00);
    System.arraycopy(minimal, 0, result,
            width - minimal.length, minimal.length);
    return result;
}
```

Ở đây việc kiểm tra dải **trước** bảo đảm mảng `minimal` không dài hơn `width` trong trường hợp hợp lệ; việc copy không cắt byte có nghĩa. Ví dụ:

```text
toFixedSigned(+128, 2) → 00 80
toFixedSigned(-1,   2) → FF FF
toFixedSigned(0,    2) → 00 00
toFixedSigned(+128, 1) → ArithmeticException
```

Đây là **mảnh hàm hỗ trợ**, không được gọi trong chương trình runnable ở phần sau; các ví dụ dự kiến có thể được đưa thành unit test theo contract signed fixed-width của giao thức. Với input không tin cậy, nên đặt **giới hạn độ dài hợp lý** ngoài việc chặn width <= 0 để tránh cấp phát mảng khổng lồ.

Nếu định dạng đích **unsigned**, không dùng bộ kiểm tra dải signed này; unsigned w byte có dải `0..2^(8*w)-1`, phần đệm phải là `00`, và constructor giải mã nên là `new BigInteger(1,bytes)`.

## 17. Zero, sign byte và các sai lầm khi serialize unsigned

Bài toán thường gặp: một giao thức nói rõ trường payload là **unsigned 256-bit integer**. Khi đọc:

```java
byte[] payload = {(byte)0x80, 0x00}; // Ví dụ thu nhỏ.
BigInteger value = new BigInteger(1, payload); // +32768.
```

Nếu sai sót dùng `new BigInteger(payload)`, byte đầu `0x80` đặt bit dấu 1 và kết quả là **-32768** theo signed 16-bit. Do đó **constructor có dấu** là không phù hợp với contract unsigned.

Khi ghi unsigned ra mảng độ rộng cố định, có hai quy tắc quan trọng:

1. **Giá trị phải không âm và vừa đủ w byte không dấu.** Không âm phải kiểm tra `value.signum() >= 0`, giới hạn lớn nhất là `2^(8w)-1`.
2. **Không được nhầm byte `00` đầu của `toByteArray()` với một byte dữ liệu payload bắt buộc.** Khi giá trị dương có bit cao bật, `toByteArray()` có thể thêm `00` để bảo vệ dấu. Trong unsigned fixed-width payload, bạn cần bỏ byte bảo vệ dấu **nếu và chỉ nếu** đã xác nhận giá trị và số byte hợp lệ, rồi zero-pad tới đúng độ dài.

Ví dụ:

```java
BigInteger v = BigInteger.valueOf(255);
byte[] signed = v.toByteArray(); // [00, FF].
byte[] unsignedOneByte = {(byte)0xff}; // [FF], theo contract unsigned.
BigInteger restored = new BigInteger(1, unsignedOneByte); // +255.
```

Nếu `restored` được tạo bằng signed constructor thì lại thành -1. Tính nhất quán **encoder và decoder** là thiết yếu; “byte giống nhau” không đủ để chứng minh “giá trị giống nhau”.

## 18. Chương trình Java 17+ độc lập, kiểm tra đầy đủ hành vi chính

Lưu thành **`BigIntegerBytesDemo.java`**:

```java
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;

public class BigIntegerBytesDemo {
    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    static String typeOfError(Runnable action) {
        try {
            action.run();
            return "NO_ERROR";
        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        byte[] highBit = {(byte) 0x80};
        byte[] allOnes = {(byte) 0xff};
        System.out.println("signed80=" + new BigInteger(highBit));
        System.out.println("positive80=" + new BigInteger(1, highBit));
        System.out.println("signedFF=" + new BigInteger(allOnes));
        System.out.println("positiveFF=" + new BigInteger(1, allOnes));
        System.out.println("negativeMagnitudeFF=" + new BigInteger(-1, allOnes));
        System.out.println("signed0080=" + new BigInteger(new byte[]{0, (byte) 0x80}));
        System.out.println("signedFF7F=" + new BigInteger(new byte[]{(byte) 0xff, 0x7f}));
        System.out.println("unsignedJavaByte=" + Byte.toUnsignedInt(allOnes[0]));

        System.out.println("int128Bytes=" + hex(BigInteger.valueOf(128).toByteArray()));
        System.out.println("int255Bytes=" + hex(BigInteger.valueOf(255).toByteArray()));
        System.out.println("int127Bytes=" + hex(BigInteger.valueOf(127).toByteArray()));
        System.out.println("intMinus128Bytes=" + hex(BigInteger.valueOf(-128).toByteArray()));
        System.out.println("intMinus129Bytes=" + hex(BigInteger.valueOf(-129).toByteArray()));
        System.out.println("intMinus1Bytes=" + hex(BigInteger.valueOf(-1).toByteArray()));
        System.out.println("intZeroBytes=" + hex(BigInteger.ZERO.toByteArray()));
        System.out.println("positiveInputLeadingZeros="
                + new BigInteger(1, new byte[]{0, 0, (byte) 0xff}));
        System.out.println("normalizedLeadingZeros="
                + hex(new BigInteger(1, new byte[]{0, 0, (byte) 0xff}).toByteArray()));

        System.out.println("signZeroMagnitudeZero="
                + new BigInteger(0, new byte[]{0, 0}));
        System.out.println("signMinusOneMagnitudeZero="
                + new BigInteger(-1, new byte[]{0, 0}));
        System.out.println("signZeroNonzeroError=" + typeOfError(
                () -> new BigInteger(0, new byte[]{1})));
        System.out.println("signOutOfRangeError=" + typeOfError(
                () -> new BigInteger(2, new byte[]{1})));
        System.out.println("signedEmptyError=" + typeOfError(
                () -> new BigInteger(new byte[0])));
        System.out.println("magnitudeEmptyZero=" + new BigInteger(1, new byte[0]));

        byte[] mixed = {(byte) 0x7e, (byte) 0x00, (byte) 0x80, (byte) 0x7f};
        System.out.println("signedSlice=" + new BigInteger(mixed, 1, 2));
        System.out.println("magnitudeSlice=" + new BigInteger(1, mixed, 2, 1));
        System.out.println("hexStringPositive=" + new BigInteger("ff", 16));

        BigInteger[] numbers = {
                BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(128),
                BigInteger.valueOf(-129), BigInteger.ONE.shiftLeft(100).negate()
        };
        boolean roundTrip = true;
        for (BigInteger n : numbers) {
            roundTrip &= n.equals(new BigInteger(n.toByteArray()));
        }
        System.out.println("signedRoundTrip=" + roundTrip);
        System.out.println("signumOfNegative="
                + BigInteger.valueOf(-129).signum());
        System.out.println("signumOfZero=" + BigInteger.ZERO.signum());
        System.out.println("sourceArrayUnchanged="
                + Arrays.equals(highBit, new byte[]{(byte)0x80}));
    }
}
```

Chạy trên Java 17:

```bash
javac --release 17 -encoding UTF-8 BigIntegerBytesDemo.java
java BigIntegerBytesDemo
```

Stdout chính xác:

```text
signed80=-128
positive80=128
signedFF=-1
positiveFF=255
negativeMagnitudeFF=-255
signed0080=128
signedFF7F=-129
unsignedJavaByte=255
int128Bytes=0080
int255Bytes=00ff
int127Bytes=7f
intMinus128Bytes=80
intMinus129Bytes=ff7f
intMinus1Bytes=ff
intZeroBytes=00
positiveInputLeadingZeros=255
normalizedLeadingZeros=00ff
signZeroMagnitudeZero=0
signMinusOneMagnitudeZero=0
signZeroNonzeroError=NumberFormatException
signOutOfRangeError=NumberFormatException
signedEmptyError=NumberFormatException
magnitudeEmptyZero=0
signedSlice=128
magnitudeSlice=128
hexStringPositive=255
signedRoundTrip=true
signumOfNegative=-1
signumOfZero=0
sourceArrayUnchanged=true
```

Các nhóm kết quả cần chú ý:

1. **`signed80=-128` vs `positive80=128`** và **`signedFF=-1` vs `positiveFF=255`** chứng minh khác biệt cốt lõi giữa hai constructor.
2. **`negativeMagnitudeFF=-255`** cho thấy sign–magnitude với dấu -1 **không** diễn giải lại byte `FF` thành -1 trước khi đổi dấu.
3. **`signed0080=128`, `signedFF7F=-129`** minh họa byte dẫn đầu quyết định dấu trong two's complement.
4. **`int128Bytes=0080`, `int255Bytes=00ff`** cho thấy `toByteArray()` thêm `00` để bảo vệ dấu; các dòng `intMinus...` cho biết các giá trị âm và sign extension tối thiểu.
5. **`positiveInputLeadingZeros=255`, `normalizedLeadingZeros=00ff`** cho thấy `BigInteger` không nhớ độ dài/padding ban đầu.
6. **`signZeroMagnitudeZero=0`, `signMinusOneMagnitudeZero=0`** cho thấy zero được chuẩn hóa; các dòng `...Error=NumberFormatException` minh họa dấu không hợp lệ hoặc mâu thuẫn với magnitude.
7. **`signedEmptyError` vs `magnitudeEmptyZero`** phân biệt mảng rỗng với hai constructor.
8. **`signedSlice` và `magnitudeSlice`** minh họa lựa chọn một đoạn dữ liệu trong mảng; **`signedRoundTrip=true`** xác minh cặp `toByteArray` → signed constructor giữ lại giá trị `BigInteger` của các ví dụ.
9. **`signumOfNegative=-1`**, **`signumOfZero=0`** cho thấy `signum()` lấy dấu giá trị thực, không sao chép đơn giản tham số signum ban đầu.

Chương trình dùng `HexFormat` (có trong Java 17) để in byte theo hex, tránh nhầm các `byte` có giá trị âm khi in số thập phân bằng `println`.

## 19. Hai phép round-trip khác nhau: theo giá trị và theo bytes

**Round-trip theo giá trị** (value-preserving) với signed constructor:

```java
BigInteger n = new BigInteger("-129");
BigInteger restored = new BigInteger(n.toByteArray());
// n.equals(restored) == true.
```

Đây là đường đi đúng theo hợp đồng của `BigInteger.toByteArray()`, vì cả hai đầu đều dùng signed two's complement big-endian.

**Round-trip theo bytes** (byte-for-byte) lại khác. Chẳng hạn:

```java
byte[] input = {0, 0, (byte) 0xff};
BigInteger value = new BigInteger(1, input);
byte[] output = value.toByteArray();
// output là [00, FF], ngắn hơn input [00, 00, FF].
```

Ta vẫn có **giá trị +255**, nhưng `input` và `output` khác độ dài. Đây **không phải lỗi BigInteger**: lớp biểu diễn giá trị số, không lưu lại cách pad đã dùng ở input.

Nếu cần giữ nguyên bytes của một giao thức, **lưu raw byte[] và metadata format** cùng với BigInteger hoặc áp dụng encoder fixed-width chuyên dụng. Khi so sánh tính đúng, tách điều kiện “đúng giá trị” khỏi điều kiện “đúng byte string”.

## 20. Những lỗi phổ biến và cách sửa

| Nhầm lẫn | Hệ quả | Giải pháp |
| --- | --- | --- |
| Tưởng `new BigInteger(bytes)` luôn đọc unsigned | Byte đầu `80`/ `FF` tạo số âm | Dùng `new BigInteger(1,bytes)` khi contract là unsigned |
| Tưởng `new BigInteger(-1,bytes)` nhận bytes bù hai âm | Magnitude `FF` cho -255, không phải -1 | Truyền **độ lớn không dấu** và dấu riêng |
| Bỏ byte `00` đầu từ signed representation | +255 có thể bị đọc thành -1 | Chỉ bỏ nếu đã đổi sang unsigned format |
| Giữ nguyên `toByteArray()` để gửi đúng 32 byte | Kết quả có độ dài biến thiên | Mã hóa fixed-width với kiểm tra dải/padding |
| Nghĩ `byte` Java âm thì mảng magnitude sẽ âm | Constructor sign–magnitude đọc **mẫu bit unsigned** | Dùng đúng constructor, không cộng giá trị byte có dấu |
| Không biết input big-endian hay little-endian | Mọi byte có thể bị đọc sai thứ tự | Quy định và chuyển byte order đúng |
| Tưởng `signum=0` luôn được chấp nhận | Magnitude khác zero gây NumberFormatException | Kiểm tra tính nhất quán dấu/magnitude |
| Tưởng `signum=-1` với magnitude zero tạo -0 | BigInteger chỉ có zero, không có negative zero | Dựa vào `result.signum()` sau khi tạo |
| Truyền `new byte[0]` cho signed constructor | `NumberFormatException` | Dùng `BigInteger.ZERO` hoặc `{0}` |
| Nghĩ `new BigInteger("ff",16)` giống signed byte `FF` | Text hex là +255, signed byte FF là -1 | Phân biệt parse chuỗi với parse binary |
| Ép `BigInteger` lớn bằng `intValue()` | Có thể mất các bit cao, trả sai số | Dùng `intValueExact()` nếu cần kiểm tra |
| Kiểm thử chỉ với byte đầu dưới `80` | Bỏ sót lỗi sai dấu | Luôn test `7F`, `80`, `FF`, `00 80`, `FF 7F` |

## 21. Câu hỏi tự kiểm tra

1. **`new BigInteger(new byte[]{(byte)0x80})` cho giá trị gì?** -128, vì mảng một byte signed two's complement có bit dấu bằng 1.
2. **Làm thế nào đọc đúng +128 từ mảng `{(byte)0x80}`?** Dùng `new BigInteger(1,bytes)` hoặc truyền signed mảng `{0,(byte)0x80}`.
3. **`new BigInteger(new byte[]{(byte)0xff})` cho gì?** -1.
4. **`new BigInteger(-1,new byte[]{(byte)0xff})` cho gì?** -255, vì mảng được đọc là magnitude 255 và dấu riêng là âm.
5. **Vì sao `BigInteger.valueOf(255).toByteArray()` có hai byte `00 FF`?** Byte `00` giữ bit dấu 0 để signed constructor đọc được +255.
6. **Tại sao `BigInteger.valueOf(-129).toByteArray()` là `FF 7F`?** Một byte signed không đủ lưu -129; cần biểu diễn bù hai 16 bit.
7. **`BigInteger.ZERO.toByteArray()` là mảng rỗng hay `[00]`?** `[00]`; có ít nhất một byte để biểu diễn zero theo signed encoding.
8. **`new BigInteger(0,new byte[]{1})` hợp lệ không?** Không. Nó ném NumberFormatException vì dấu 0 mâu thuẫn magnitude khác 0.
9. **`new BigInteger(-1,new byte[]{0})` có tạo negative zero không?** Không. Giá trị là 0 và `signum()` trả 0.
10. **Constructor signed và sign–magnitude có dùng thứ tự byte khác nhau không?** Không, cả hai đều big-endian; khác ở cách hiểu dấu.
11. **`new BigInteger("ff",16)` có giống `new BigInteger(new byte[]{(byte)0xff})`?** Không. Cái đầu +255, cái sau -1.
12. **Vì sao parse `[00 00 FF]` rồi `toByteArray()` không giữ ba byte?** BigInteger giữ giá trị số; `toByteArray` xuất signed representation tối thiểu.
13. **Nếu giao thức yêu cầu unsigned 32 byte, có nên dùng thẳng `toByteArray()`?** Không. Phải kiểm tra giá trị không âm và nằm trong 256 bit, loại byte dấu bảo vệ nếu cần rồi zero-pad chính xác 32 byte.
14. **Để đọc unsigned hash/digest có bit đầu bật, chọn constructor nào?** `new BigInteger(1,hashBytes)` khi muốn diễn giải hash như độ lớn không dấu, đồng thời giữ bytes/độ dài riêng nếu cần round-trip.

**Ghi nhớ:** `new BigInteger(byte[])` nhận **signed two's complement big-endian**; `new BigInteger(signum,byte[])` nhận **dấu riêng và magnitude unsigned big-endian**. `toByteArray()` luôn trả **signed two's complement tối thiểu**, có thể thêm `00` để giữ số dương hoặc bớt padding so với dữ liệu gốc. Khi làm việc với binary protocol, hãy xác định **signed/unsigned, width và byte order** ngay từ đầu; chọn đúng constructor, kiểm tra biên và chỉ dùng encoder fixed-width nếu định dạng yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-004">ZERO, ONE, TWO, TEN và valueOf</a>

<details>
<summary>Xem chi tiết</summary>

# `BigInteger.ZERO`, `ONE`, `TWO`, `TEN` và `valueOf` trong Java

## 1. Tổng quan: số nguyên lớn và cách tạo giá trị

`java.math.BigInteger` biểu diễn **số nguyên có dấu với độ chính xác tùy ý** (*arbitrary-precision integer*). Khác với `int` (32 bit) và `long` (64 bit), số chữ số/bit của một `BigInteger` không bị cố định ở 32 hoặc 64 bit. Giới hạn thực tế đến từ bộ nhớ, phạm vi mà API/JVM hỗ trợ và tài nguyên tính toán.

Một `BigInteger` là **đối tượng bất biến** (*immutable*). Mọi thao tác như `add`, `multiply`, `pow` trả về giá trị kết quả mà không sửa giá trị ban đầu.

Để không phải tạo lại các số nguyên rất thường dùng, Java cung cấp sẵn các hằng `public static final`:

```java
import java.math.BigInteger;

BigInteger zero = BigInteger.ZERO; // 0
BigInteger one  = BigInteger.ONE;  // 1
BigInteger two  = BigInteger.TWO;  // 2
BigInteger ten  = BigInteger.TEN;  // 10
```

Với một số nguyên `long` khác, dùng:

```java
BigInteger n = BigInteger.valueOf(123456789L);
```

**Hai nguyên tắc cốt lõi:**

1. Dùng các hằng `ZERO`, `ONE`, `TWO`, `TEN` khi đúng giá trị và làm mã dễ đọc; dùng `valueOf(long)` khi đầu vào đã là một giá trị nguyên vừa miền `long`.
2. Không dùng toán tử `==` để kiểm tra **hai đối tượng `BigInteger` có bằng nhau về giá trị không**. Dùng `equals`, `compareTo` hoặc `signum` theo ý nghĩa bài toán.

Bài này tập trung vào **các hằng số và factory `valueOf`**, đồng thời trình bày đủ các phép thao tác và bẫy cần biết để dùng chúng chính xác.

## 2. Bốn hằng số có sẵn

| Hằng số | Giá trị toán học | Ứng dụng điển hình |
| --- | ---: | --- |
| `BigInteger.ZERO` | 0 | Khởi tạo tổng, kết quả rỗng, phần tử đơn vị của phép cộng, kiểm tra số 0 |
| `BigInteger.ONE` | 1 | Phần tử đơn vị của phép nhân, bắt đầu tích, tăng/giảm một đơn vị |
| `BigInteger.TWO` | 2 | Thuật toán nhị phân, tính lũy thừa cơ số hai, diễn đạt hệ số 2 |
| `BigInteger.TEN` | 10 | Lũy thừa 10, tính toán số nguyên theo cơ số thập phân, quy đổi đơn vị |

Ví dụ trong một phương thức Java:

```java
System.out.println(BigInteger.ZERO); // 0
System.out.println(BigInteger.ONE);  // 1
System.out.println(BigInteger.TWO);  // 2
System.out.println(BigInteger.TEN);  // 10
```

**Lưu ý về phiên bản:** `BigInteger.TWO` xuất hiện từ **Java 9**. Trong Java 17 trở lên, cả bốn hằng đều dùng được. Nếu buộc hỗ trợ Java 8, thay `TWO` bằng `BigInteger.valueOf(2)` hoặc biểu thức tương đương.

Mỗi hằng là một tham chiếu `public static final` tới giá trị `BigInteger` bất biến. `final` ở đây ngăn gán lại trường tĩnh; đặc tính **bất biến của chính `BigInteger`** ngăn sửa giá trị số bên trong.

Không được hiểu `ZERO`, `ONE` hay `TEN` giống biến toàn cục có thể cộng trừ trực tiếp:

```java
BigInteger sum = BigInteger.ZERO;
sum = sum.add(BigInteger.ONE); // sum mang giá trị 1

System.out.println(sum);             // 1
System.out.println(BigInteger.ZERO); // 0: hằng ban đầu không đổi
```

Biểu thức `sum.add(BigInteger.ONE)` tạo ra hoặc trả về một đối tượng biểu diễn kết quả tùy tối ưu của thư viện; dù cách triển khai nào, **giá trị `sum` ban đầu không bị sửa** cho đến khi ta gán lại biến `sum`.

### Hằng `ZERO` có giá trị khác tham chiếu `null`

```java
BigInteger count = BigInteger.ZERO; // Số 0 hợp lệ
BigInteger unknown = null;          // Không có đối tượng
```

`ZERO` là một giá trị số thật; `null` không phải số 0 và không gọi được phương thức trên nó. Ví dụ `unknown.add(BigInteger.ONE)` sẽ ném `NullPointerException`. Dùng `BigInteger.ZERO` làm mặc định chỉ khi nghiệp vụ cho phép coi dữ liệu thiếu là 0.

## 3. `BigInteger.valueOf(long)` là gì?

Chữ ký API:

```java
public static BigInteger valueOf(long val)
```

Phương thức nhận **một giá trị nguyên kiểu `long`**, rồi trả về `BigInteger` mang **chính xác giá trị đó**:

```java
BigInteger a = BigInteger.valueOf(0L);
BigInteger b = BigInteger.valueOf(42L);
BigInteger c = BigInteger.valueOf(-17L);
BigInteger d = BigInteger.valueOf(Long.MIN_VALUE);
```

`valueOf(long)` không ném overflow vì đầu vào đã là một `long` hợp lệ. **Mọi giá trị `long`**, kể cả `Long.MIN_VALUE` và `Long.MAX_VALUE`, đều biểu diễn chính xác bằng `BigInteger`.

Ví dụ:

```java
BigInteger min = BigInteger.valueOf(Long.MIN_VALUE);
BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);

System.out.println(min); // -9223372036854775808
System.out.println(max); // 9223372036854775807
```

Lời gọi với literal `int` hợp lệ nhờ **widening primitive conversion** từ `int` sang `long`:

```java
BigInteger x = BigInteger.valueOf(100); // int literal được chuyển sang long
```

Không có overload `BigInteger.valueOf(double)` trong API này. Các ví dụ sau **không** được phép:

```java
// BigInteger.valueOf(3.14);  // Lỗi biên dịch: double không chuyển ngầm sang long
// BigInteger.valueOf("42");  // Lỗi biên dịch: String không phải long
```

Với chuỗi, dùng constructor `new BigInteger("42")`. Với decimal có phần lẻ hoặc đầu vào dấu phẩy động, cần chọn **chính sách làm tròn và tính chính xác** tường minh trước khi đổi sang số nguyên.

### Dùng `valueOf` để tận dụng tái sử dụng đối tượng

Tài liệu `valueOf` khuyến nghị static factory thay cho constructor nhận `long` vì nó **có thể tái sử dụng những `BigInteger` thường gặp**. Điều đó có nghĩa JVM/thư viện được tự do tối ưu cấp phát khi thích hợp.

Nhưng **không có nghĩa hai lần `BigInteger.valueOf(100)` luôn phải trả cùng tham chiếu trên mọi triển khai**. Điều cần dựa vào là **giá trị** của kết quả:

```java
BigInteger a = BigInteger.valueOf(100);
BigInteger b = BigInteger.valueOf(100);

System.out.println(a.equals(b)); // true
```

Không dựa vào `a == b`. Việc factory trả đúng instance của `BigInteger.ZERO`, `ONE` hoặc instance cache khác không phải hợp đồng so sánh giá trị nghiệp vụ.

### Khi nào nên dùng hằng, khi nào dùng `valueOf`?

- Nếu viết thuật toán với giá trị *cố định* `0`, `1`, `2`, `10`, các hằng thường sáng rõ và tránh lời gọi factory thừa.
- Nếu đầu vào là `long` (tham số hàm, dữ liệu từ parser nguyên có giới hạn hoặc phép tính không overflow), `BigInteger.valueOf(value)` là lựa chọn chuẩn.
- Nếu đầu vào là số nguyên thập phân **có thể vượt `long`**, phải parse từ chuỗi hoặc tính trực tiếp bằng `BigInteger` — **không** ép tạm về `long` rồi `valueOf`.
- Nếu cần kết quả sau phép toán `BigInteger`, dùng các phương thức `add`, `multiply`, `pow`... thay vì quay lại `long`.

## 4. Điều `valueOf` không làm được: mở rộng dữ liệu đã overflow

Cạm bẫy lớn nhất là làm toán bằng `long` trước rồi mới chuyển sang `BigInteger`:

```java
long max = Long.MAX_VALUE;
long overflowed = max + 1L;           // Tràn số nguyên long: trở thành Long.MIN_VALUE
BigInteger wrong = BigInteger.valueOf(overflowed);

System.out.println(wrong); // -9223372036854775808
```

`BigInteger.valueOf` đã thực hiện **đúng** việc chuyển giá trị `long` nhận được. Nó không biết giá trị trước phép cộng từng là `Long.MAX_VALUE`, và không khôi phục được bit đã mất.

Cách đúng là **nâng lên `BigInteger` trước khi phép cộng vượt miền**:

```java
BigInteger correct =
        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

System.out.println(correct); // 9223372036854775808
```

Nói cách khác:

\[
\operatorname{valueOf}(\text{phép toán long đã overflow})
\ne
\text{phép toán BigInteger trực tiếp}.
\]

Vấn đề tương tự xảy ra với phép nhân và lũy thừa:

```java
long a = 4_000_000_000L;
long b = 4_000_000_000L;

// long product = a * b; // Vượt Long.MAX_VALUE; overflow
BigInteger product = BigInteger.valueOf(a)
        .multiply(BigInteger.valueOf(b));

System.out.println(product); // 16000000000000000000
```

Không có “overflow theo miền `long`” trong kết quả `BigInteger` hợp lệ; giới hạn vẫn tồn tại ở tài nguyên và hạn chế triển khai, nhưng không cố định 64 bit.

## 5. Số vượt miền `long`: dùng constructor từ chuỗi

`BigInteger.valueOf` chỉ nhận một `long`. Với số nguyên lớn hơn miền `long`, dùng:

```java
BigInteger huge = new BigInteger("100000000000000000000000000000000000000");
```

Constructor này đọc chuỗi thập phân (cơ số 10) với dấu tùy chọn và không ép qua `double` hoặc `long`.

Ví dụ:

```java
BigInteger beyondLong = new BigInteger("9223372036854775808");

System.out.println(beyondLong);
// 9223372036854775808
```

Với hệ cơ số khác, dùng `new BigInteger(text, radix)`:

```java
BigInteger hex = new BigInteger("FF", 16);
BigInteger binary = new BigInteger("1010", 2);

System.out.println(hex);    // 255
System.out.println(binary); // 10
```

`radix` phải trong phạm vi được hỗ trợ, thường là `Character.MIN_RADIX` (2) tới `Character.MAX_RADIX` (36). Dữ liệu sai cú pháp hoặc không hợp lệ theo radix gây `NumberFormatException`.

### Phân biệt parse và chuyển kiểu

| Đầu vào | API phù hợp | Chú ý |
| --- | --- | --- |
| `long` | `BigInteger.valueOf(long)` | Giữ chính xác toàn miền `long` |
| `int` | `BigInteger.valueOf(intValue)` | Java nâng `int` sang `long` |
| Chuỗi số nguyên thập phân | `new BigInteger(text)` | Không cần thông qua `long` |
| Chuỗi số nguyên hệ 16 | `new BigInteger(text, 16)` | Ví dụ `"FF"` → 255 |
| Giá trị `BigInteger` đã có | Dùng trực tiếp | Không tạo lại nếu không cần |
| Số decimal như `"1.5"` | `BigDecimal` trước, rồi chính sách nguyên hóa nếu cần | `new BigInteger("1.5")` không hợp lệ |
| `double` | Không dùng `BigInteger.valueOf(double)` | Cần xét mất chính xác và quy tắc làm tròn |

Nhập `"9223372036854775808"` bằng `Long.parseLong` sẽ thất bại vì vượt miền `long`, dù `BigInteger` vẫn đọc được. Đây là lý do không dùng `Long.parseLong` làm “bước đệm” cho dữ liệu nguyên lớn.

## 6. Bất biến và phép tính với `ZERO`, `ONE`, `TWO`, `TEN`

Các phương thức số học của `BigInteger` trả về kết quả mới về **mặt giá trị**, không sửa toán hạng:

```java
BigInteger x = BigInteger.TEN;
BigInteger y = x.add(BigInteger.ONE);

System.out.println(x); // 10
System.out.println(y); // 11
```

Một số phép thường dùng:

| Phép toán | Mã Java | Kết quả |
| --- | --- | --- |
| `7 + 1` | `BigInteger.valueOf(7).add(BigInteger.ONE)` | `8` |
| `7 - 1` | `BigInteger.valueOf(7).subtract(BigInteger.ONE)` | `6` |
| `7 × 2` | `BigInteger.valueOf(7).multiply(BigInteger.TWO)` | `14` |
| `7 × 10` | `BigInteger.valueOf(7).multiply(BigInteger.TEN)` | `70` |
| `2^10` | `BigInteger.TWO.pow(10)` | `1024` |
| `10^6` | `BigInteger.TEN.pow(6)` | `1000000` |
| Dịch trái 5 bit từ 1 | `BigInteger.ONE.shiftLeft(5)` | `32` |
| Đổi dấu của 10 | `BigInteger.TEN.negate()` | `-10` |
| Trị tuyệt đối của -10 | `BigInteger.TEN.negate().abs()` | `10` |

`ZERO` là phần tử trung hòa của phép cộng: \(n+0=n\). `ONE` là phần tử trung hòa của phép nhân: \(n\times1=n\). Nhưng không nên suy từ điều này ra đảm bảo **cùng tham chiếu** khi thư viện tối ưu trả về một trong hai toán hạng; quan hệ về số phải kiểm tra qua `equals`.

Ví dụ tích lũy:

```java
BigInteger sum = BigInteger.ZERO;

for (long value : new long[] { 1, 2, 3, 4 }) {
    sum = sum.add(BigInteger.valueOf(value));
}

System.out.println(sum); // 10
```

Cần có phép gán lại `sum = sum.add(...)`. Nếu chỉ gọi `sum.add(...)` mà không nhận kết quả, giá trị lưu trong `sum` không đổi.

### Lũy thừa và trường hợp `0^0`

`BigInteger.pow(int exponent)` nhận số mũ kiểu `int` **không âm**:

```java
System.out.println(BigInteger.TWO.pow(10)); // 1024
System.out.println(BigInteger.TEN.pow(6));  // 1000000
System.out.println(BigInteger.ZERO.pow(0)); // 1
```

Theo quy ước của API, `pow(0)` trả `ONE`, kể cả khi cơ số bằng `ZERO`. Đây là **quy ước lập trình của phép lũy thừa số nguyên**; trong các ngữ cảnh toán học, biểu thức `0^0` có thể được quy định khác hoặc để không xác định. Đừng áp dụng quy ước API mà không kiểm tra mô hình toán học của ứng dụng.

Nếu `exponent < 0`, `BigInteger.pow` ném `ArithmeticException`: không thể biểu diễn `2^-1` dưới dạng số nguyên `BigInteger`. Nếu số mũ quá lớn, phép tính có thể tốn nhiều bộ nhớ/thời gian; phải validate giới hạn công việc với đầu vào không tin cậy.

## 7. So sánh giá trị: `equals`, `compareTo`, `signum` thay vì `==`

**`BigInteger` là đối tượng tham chiếu**, không phải primitive wrapper có unboxing tự động sang `long`.

```java
BigInteger a = BigInteger.valueOf(10);
BigInteger b = BigInteger.TEN;

boolean equal = a.equals(b); // true
```

Đối với hai tham chiếu `BigInteger`:

- `a == b` kiểm tra **cùng đối tượng**.
- `a.equals(b)` kiểm tra **cùng giá trị số nguyên**.
- `a.compareTo(b)` trả số âm, 0, hoặc số dương tương ứng `a < b`, `a = b`, `a > b`.
- `a.signum()` trả `-1`, `0` hoặc `1` tùy dấu giá trị.

Ví dụ:

```java
BigInteger a = BigInteger.valueOf(10);
BigInteger b = BigInteger.TEN;

System.out.println(a.equals(b));     // true
System.out.println(a.compareTo(b));  // 0
System.out.println(a.signum());      // 1
```

Không sử dụng `==` ngay cả khi một lần thử cho thấy `BigInteger.valueOf(10) == BigInteger.TEN` là `true` trong JVM đang chạy. Cache/reuse là lựa chọn triển khai, **không phải định nghĩa bằng nhau của số nguyên**.

### Kiểm tra 0 đúng cách

```java
BigInteger n = BigInteger.valueOf(0);

boolean isZero = n.signum() == 0;
boolean alsoZero = n.equals(BigInteger.ZERO);
```

Cả hai đều kiểm tra **giá trị** nếu `n` không `null`. Với `n == null`, gọi `signum()` hoặc `equals()` sẽ ném `NullPointerException`. Nếu phải xử lý nullable data:

```java
import java.util.Objects;

boolean zeroIfPresent = Objects.equals(n, BigInteger.ZERO);
```

Điều kiện này trả `false` khi `n == null`, và `true` khi `n` có giá trị số 0. Nếu nghiệp vụ cần phân biệt missing với zero, hãy kiểm tra `n == null` riêng.

### So sánh với primitive

Không có autounboxing từ `BigInteger` thành `long`:

```java
BigInteger n = BigInteger.TEN;

// boolean wrong = (n == 10); // Không biên dịch: BigInteger và int không tương thích

boolean right = n.equals(BigInteger.valueOf(10));
```

Khi cần so sánh một `BigInteger` với `long`, tạo `BigInteger.valueOf(long)` để so giá trị, hoặc dùng các phép kiểm tra miền/chuyển đổi chính xác thích hợp. Không tự ép `BigInteger` sang `double` rồi so nếu số rất lớn.

## 8. `valueOf` và tối ưu cấp phát: không nhầm với cache wrapper

Mục đích của `valueOf` là tạo một giá trị chính xác và **cho phép thư viện tái sử dụng đối tượng** thường gặp. Không được suy luận rằng `valueOf` luôn tạo mới hoặc luôn trả một instance duy nhất cho mọi số.

Ví dụ:

```java
BigInteger x = BigInteger.valueOf(7);
BigInteger y = BigInteger.valueOf(7);

System.out.println(x.equals(y)); // true
// Không xây dựng logic nghiệp vụ trên x == y.
```

Một vài lưu ý:

1. `BigInteger.ZERO`, `ONE`, `TWO`, `TEN` là **các hằng có sẵn** để truy cập và biểu đạt ý nghĩa, không phải một cơ chế *interning* hoàn chỉnh cho tất cả số.
2. `BigInteger.valueOf(long)` có thể tối ưu tạo đối tượng; không thể dựa vào `==` để suy ra hai số khác nhau.
3. Cũng không nên dùng một `BigInteger` làm **monitor/khóa đồng bộ** với kỳ vọng mỗi lần gọi `valueOf` tạo một khóa độc lập: có thể chia sẻ instance. Hãy dùng một `new Object()` riêng do ứng dụng kiểm soát khi cần khóa.
4. Do `BigInteger` là bất biến, có thể chia sẻ hằng trong nhiều nơi mà không lo các phép `add`/`multiply` làm thay đổi chúng.
5. Hiệu năng của `BigInteger` khác phép toán `long`: số bit lớn hơn làm tăng công việc tính toán và có thể phát sinh đối tượng/áp lực GC. Sử dụng khi cần miền vượt primitive hoặc khi API yêu cầu.

Chọn `BigInteger` vì **tính đúng đắn và miền giá trị**, không vì tin rằng tham chiếu `valueOf` luôn cố định.

## 9. Chuyển từ `BigInteger` về primitive và vấn đề mất dữ liệu

`BigInteger` kế thừa `Number`, có các phương thức lấy primitive:

```java
BigInteger n = BigInteger.valueOf(42);

int asInt = n.intValue();
long asLong = n.longValue();
double asDouble = n.doubleValue();
```

Nhưng các phương thức `intValue()`/`longValue()` **không bảo đảm phát hiện vượt miền**; chúng có thể bỏ các bit cao khi chuyển sang kiểu số nguyên hẹp. `doubleValue()` còn có thể làm tròn hoặc trả vô cực nếu độ lớn quá lớn.

Ví dụ:

```java
BigInteger beyondLong =
        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

long truncated = beyondLong.longValue();
System.out.println(truncated); // -9223372036854775808
```

Nếu cần **chắc chắn nguyên vẹn**, dùng:

```java
long exact = BigInteger.valueOf(42).longValueExact();
int exactInt = BigInteger.valueOf(42).intValueExact();
```

Với số vượt miền, các phương thức `longValueExact()`/`intValueExact()` ném `ArithmeticException`:

```java
BigInteger tooBig = BigInteger.TWO.pow(80);

// tooBig.longValueExact(); // ArithmeticException
```

Nên bắt hoặc để ngoại lệ phát ra theo hợp đồng của phương thức, thay vì âm thầm mất dữ liệu. Các API exact này đặc biệt quan trọng khi dữ liệu nguyên lớn phải đi vào giao thức hoặc DB chỉ hỗ trợ 64 bit.

**Một chiều chuyển đổi an toàn không bảo đảm chiều ngược lại an toàn:** `BigInteger.valueOf(long)` luôn chính xác, nhưng `someBigInteger.longValue()` có thể làm mất thông tin.

## 10. Demo Java 17 hoàn chỉnh

Lưu chương trình dưới tên `BigIntegerConstantsDemo.java`:

```java
import java.math.BigInteger;

public class BigIntegerConstantsDemo {
    public static void main(String[] args) {
        System.out.println("ZERO = " + BigInteger.ZERO);
        System.out.println("ONE = " + BigInteger.ONE);
        System.out.println("TWO = " + BigInteger.TWO);
        System.out.println("TEN = " + BigInteger.TEN);

        BigInteger fromLong = BigInteger.valueOf(-17L);
        BigInteger fromString = new BigInteger("9223372036854775808");

        System.out.println("valueOf(-17) = " + fromLong);
        System.out.println("string > long max = " + fromString);

        BigInteger next = BigInteger.valueOf(Long.MAX_VALUE)
                .add(BigInteger.ONE);
        System.out.println("max long + ONE = " + next);

        BigInteger ten = BigInteger.TEN;
        BigInteger eleven = ten.add(BigInteger.ONE);
        System.out.println("TEN after add = " + ten);
        System.out.println("TEN + ONE = " + eleven);

        System.out.println("TWO^10 = " + BigInteger.TWO.pow(10));
        System.out.println("TEN^6 = " + BigInteger.TEN.pow(6));
        System.out.println("TWO^100 = " + BigInteger.TWO.pow(100));
        System.out.println("ZERO^0 = " + BigInteger.ZERO.pow(0));

        BigInteger anotherZero = BigInteger.valueOf(0);
        System.out.println("zero equals = "
                + BigInteger.ZERO.equals(anotherZero));
        System.out.println("zero signum = " + anotherZero.signum());
        System.out.println("compare ZERO to ONE = "
                + BigInteger.ZERO.compareTo(BigInteger.ONE));

        System.out.println("hex FF = " + new BigInteger("FF", 16));

        try {
            BigInteger.TWO.pow(80).longValueExact();
            System.out.println("Unexpected successful narrowing");
        } catch (ArithmeticException ex) {
            System.out.println("longValueExact: ArithmeticException");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac BigIntegerConstantsDemo.java
java BigIntegerConstantsDemo
```

Đầu ra:

```text
ZERO = 0
ONE = 1
TWO = 2
TEN = 10
valueOf(-17) = -17
string > long max = 9223372036854775808
max long + ONE = 9223372036854775808
TEN after add = 10
TEN + ONE = 11
TWO^10 = 1024
TEN^6 = 1000000
TWO^100 = 1267650600228229401496703205376
ZERO^0 = 1
zero equals = true
zero signum = 0
compare ZERO to ONE = -1
hex FF = 255
longValueExact: ArithmeticException
```

Đoạn demo không đưa ra giả định về `==` của các đối tượng `BigInteger`, không ép chuỗi số lớn qua `long`, và sử dụng `longValueExact()` để nhận biết dữ liệu không vừa miền khi chuyển ngược về primitive.

## 11. Tình huống thực tế: hằng số giúp code diễn đạt ý nghĩa

### Tính tổng không overflow

```java
BigInteger total = BigInteger.ZERO;

for (long item : new long[] { Long.MAX_VALUE, 1L, 2L }) {
    total = total.add(BigInteger.valueOf(item));
}

System.out.println(total); // 9223372036854775810
```

Ở đây phép cộng **được thực hiện hoàn toàn dưới dạng `BigInteger`**. Dùng `long total` để tích lũy trước rồi chuyển sau sẽ overflow.

### Đếm số cách theo quy luật nhân đôi

```java
BigInteger count = BigInteger.ONE;

for (int i = 0; i < 10; i++) {
    count = count.multiply(BigInteger.TWO);
}

System.out.println(count); // 1024
```

Với n nguyên không âm, `BigInteger.TWO.pow(n)` thường diễn đạt ngắn gọn hơn vòng lặp. Nhưng nhớ `pow` nhận số mũ kiểu `int`; nếu `n` cực lớn, kết quả có thể tốn tài nguyên đáng kể.

### Tạo lũy thừa cơ số 10 chính xác

```java
BigInteger million = BigInteger.TEN.pow(6);
System.out.println(million); // 1000000
```

Không nên thay bằng `(long) Math.pow(10, n)` cho mọi `n` khi muốn **số nguyên chính xác không giới hạn**: `Math.pow` làm việc với `double`, nên có miền/độ chính xác của số thực và phép ép kiểu có thể mất dữ liệu.

### Chuyển một giá trị nguyên theo đơn vị nhỏ nhất

Nếu có tổng số lượng dưới dạng `BigInteger`, có thể nhân chính xác với `TEN`, `TWO` hoặc các hằng khác:

```java
BigInteger units = BigInteger.valueOf(500);
BigInteger subunits = units.multiply(BigInteger.TEN);

System.out.println(subunits); // 5000
```

Với đơn vị đo hay tiền tệ, hệ số quy đổi phải đến từ quy định nghiệp vụ. `TEN` chỉ là số 10, không tự nói “đơn vị tiền nhỏ nhất” hay “đơn vị đo”.

## 12. Những lỗi phổ biến

| Lỗi | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| `BigInteger.valueOf("123")` | `valueOf` nhận `long`, không nhận `String` | `new BigInteger("123")` |
| Tạo số vượt `long` bằng `valueOf` | Không thể truyền nguyên giá trị vượt miền `long` | Parse từ chuỗi hoặc tính bằng `BigInteger` trước |
| `BigInteger.valueOf(Long.MAX_VALUE + 1L)` | Phép cộng tràn khi còn là `long` | `BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)` |
| `a == b` với hai `BigInteger` | So tham chiếu chứ không so giá trị | `a.equals(b)` hoặc `a.compareTo(b) == 0` |
| Nghĩ `ZERO` là `null` | `ZERO` là đối tượng mang số 0 | Xử lý trạng thái thiếu dữ liệu riêng |
| `sum.add(n);` không gán lại | `BigInteger` bất biến | `sum = sum.add(n);` |
| `new BigInteger("1.5")` | Constructor cần số nguyên theo radix | Dùng `BigDecimal` nếu có phần lẻ |
| Dùng `longValue()` cho giá trị bất kỳ | Có thể bỏ bit cao | `longValueExact()` để báo lỗi |
| Dùng `doubleValue()` khi cần giá trị nguyên chính xác | `double` có thể làm tròn nguyên lớn | Giữ `BigInteger` hoặc kiểm tra miền/độ chính xác |
| Dựa vào identity do `valueOf` cache | Tái sử dụng đối tượng không phải quy tắc bằng nhau | So sánh giá trị |
| Dùng `Math.pow` rồi ép sang `long` | Số thực có thể mất chữ số/tràn miền | `BigInteger.pow` với số mũ nguyên không âm |
| Mong toán tử `+`, `*` hoạt động với `BigInteger` | Đây không phải primitive số có hỗ trợ toán tử tương ứng | Gọi `add`, `multiply` |
| Dùng `BigInteger.TWO` trong Java 8 | `TWO` chưa có ở Java 8 | Java 9+, hoặc `valueOf(2)` |
| Coi số nguyên lớn là vô hạn tài nguyên | `BigInteger` vẫn có giới hạn bộ nhớ/thời gian | Kiểm tra kích thước đầu vào/số mũ |

### Thêm hai lưu ý về hiệu năng và API

**Không dùng `BigInteger` một cách máy móc cho mọi bộ đếm nhỏ.** Nếu miền giá trị đã biết nằm trọn trong `long` và phép tính có kiểm tra overflow phù hợp, primitive thường gọn và nhanh hơn. Dùng `BigInteger` khi cần miền rộng, tính chính xác vượt giới hạn primitive hoặc giao thức/API yêu cầu.

**Không dùng `BigInteger` làm monitor đồng bộ chia sẻ dựa vào factory.** Do `valueOf` có thể tái sử dụng instance, một đoạn mã khác có thể nhận cùng object; dùng khóa riêng do ứng dụng sở hữu nếu cần đồng bộ.

## 13. Bài tập và đáp án

**Câu 1.** Trong Java 17, bốn hằng `BigInteger.ZERO`, `ONE`, `TWO`, `TEN` biểu diễn số nào? Hằng nào chưa có ở Java 8?

**Đáp án:** Lần lượt 0, 1, 2, 10. `BigInteger.TWO` có từ Java 9 nên không dùng trực tiếp được trên Java 8.

**Câu 2.** Cho:

```java
BigInteger n = BigInteger.TEN;
n.add(BigInteger.ONE);
System.out.println(n);
```

Kết quả là gì, vì sao?

**Đáp án:** `10`. `BigInteger` bất biến; lời gọi `add` trả kết quả nhưng không sửa `n`. Muốn `11`, gán `n = n.add(BigInteger.ONE)`.

**Câu 3.** Làm sao tính chính xác `Long.MAX_VALUE + 1` bằng `BigInteger`?

**Đáp án:**

```java
BigInteger result =
        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
```

Không thực hiện phép cộng kiểu `long` trước khi gọi `valueOf`.

**Câu 4.** `BigInteger.valueOf(10).equals(BigInteger.TEN)` là `true` hay `false`? Có cần so `==` để chắc chắn không?

**Đáp án:** `true`. `equals` so sánh giá trị nguyên; `==` không cần thiết và không phù hợp để xác định numeric equality.

**Câu 5.** Muốn đọc số `"123456789012345678901234567890"` thành `BigInteger`, dùng API nào?

**Đáp án:** `new BigInteger("123456789012345678901234567890")`; không dùng `Long.parseLong` hay `BigInteger.valueOf(String)`.

**Câu 6.** `BigInteger.TWO.pow(10)` và `BigInteger.TEN.pow(6)` trả gì?

**Đáp án:** Lần lượt `1024` và `1000000`, đều là đối tượng `BigInteger` mang giá trị nguyên chính xác.

**Câu 7.** `BigInteger.ZERO.pow(0)` trả gì theo API Java? Có được áp dụng không điều kiện vào mọi bài toán toán học không?

**Đáp án:** Trả `BigInteger.ONE` (giá trị 1) theo quy ước API. Một số ngữ cảnh toán học xem `0^0` là biểu thức không xác định; phải theo định nghĩa của bài toán.

**Câu 8.** Vì sao `BigInteger.TWO.pow(80).longValue()` không thích hợp nếu ứng dụng cần đủ giá trị? Phương thức thay thế là gì?

**Đáp án:** Giá trị `2^80` vượt miền `long`; `longValue()` có thể bỏ bit cao, gây mất dữ liệu. `longValueExact()` ném `ArithmeticException` để báo rằng không thể chuyển chính xác.

**Câu 9.** Một chương trình luôn gọi `BigInteger.valueOf(7)` rồi so tham chiếu để xác định “hai ID bằng nhau”. Sai ở đâu?

**Đáp án:** Giá trị số là ID có thể dùng được, nhưng so tham chiếu dựa vào tái sử dụng đối tượng là sai thiết kế. So bằng `equals`, hoặc định nghĩa một lớp ID với hợp đồng so sánh giá trị phù hợp.

**Câu 10.** `new BigInteger("FF", 16)` cho gì? `new BigInteger("FF")` có tương đương không?

**Đáp án:** Cái đầu cho `255` theo hệ 16. Cái sau dùng hệ 10 mặc định, nên `"FF"` không hợp lệ và ném `NumberFormatException`.

**Ghi nhớ:** `BigInteger.ZERO`, `ONE`, `TWO`, `TEN` là các hằng số nguyên bất biến dễ đọc; `BigInteger.valueOf(long)` tạo giá trị chính xác từ `long` và cho phép tối ưu tái sử dụng. Đừng thực hiện phép toán `long` có thể overflow trước khi chuyển; đừng so `BigInteger` bằng `==`; và giữ dữ liệu dưới dạng `BigInteger` khi cần phân biệt các số nguyên vượt giới hạn primitive.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-005">add, subtract, multiply, divide và remainder</a>

<details>
<summary>Xem chi tiết</summary>

# `BigInteger`: `add`, `subtract`, `multiply`, `divide` và `remainder`

`java.math.BigInteger` biểu diễn số nguyên **có độ chính xác tùy ý**. Khi phép cộng, trừ, nhân vượt giới hạn `int` hoặc `long`, `BigInteger` vẫn giữ được kết quả nguyên chính xác, miễn còn đủ bộ nhớ và tài nguyên tính toán. Đây là kiểu số **nguyên**, khác với `BigDecimal` (thập phân) và `double` (dấu phẩy động gần đúng).

| Phương thức | Phép toán | Kết quả |
|---|---|---|
| `a.add(b)` | `a + b` | Tổng nguyên chính xác |
| `a.subtract(b)` | `a - b` | Hiệu nguyên chính xác |
| `a.multiply(b)` | `a × b` | Tích nguyên chính xác |
| `a.divide(b)` | Chia nguyên, làm tròn thương về 0 | Thương `BigInteger` |
| `a.remainder(b)` | Phần dư ứng với thương nguyên | Dư `BigInteger` |

Cả năm phương thức nhận đối số kiểu `BigInteger` và trả về `BigInteger`. `divide` và `remainder` không chấp nhận số chia bằng 0. Phần quan trọng nhất khi học hai phép cuối là **`a = q × b + r`**, trong đó `q = a.divide(b)`, `r = a.remainder(b)` và `b != 0`.

## 1. Tạo giá trị và hiểu tính bất biến

```java
import java.math.BigInteger;

BigInteger a = new BigInteger("12345678901234567890");
BigInteger b = BigInteger.valueOf(97);
BigInteger negative = new BigInteger("-17");
BigInteger zero = BigInteger.ZERO;
BigInteger one = BigInteger.ONE;
```

Đây là các câu lệnh minh họa dùng bên trong phương thức hoặc JShell, chưa phải tệp Java chạy độc lập.

- `new BigInteger("...")` phân tích chuỗi số nguyên, phù hợp cả khi số vượt phạm vi `long`.
- `BigInteger.valueOf(long)` dùng khi đầu vào đã nằm trong miền số `long`. Viết literal lớn hơn miền `long` rồi truyền vào `valueOf` sẽ không biên dịch.
- `BigInteger.ZERO`, `ONE`, `TWO`, `TEN` là các hằng tiện dụng.
- Chuỗi `"12.34"` không phải số nguyên hợp lệ cho `new BigInteger(...)` và gây `NumberFormatException`.
- `BigInteger` bất biến: kết quả phép toán không thay đổi số ban đầu. Không nên suy luận rằng kết quả luôn là instance mới vì thư viện có thể tái sử dụng đối tượng trong một số trường hợp.

```java
BigInteger x = BigInteger.valueOf(100);
BigInteger y = BigInteger.valueOf(5);

x.add(y);                 // Tính 105 nhưng bỏ kết quả.
System.out.println(x);    // 100
x = x.add(y);
System.out.println(x);    // 105
```

Java không hỗ trợ `x + y`, `x - y`, `x * y`, `x / y` hoặc `x % y` khi hai biến là `BigInteger`: phải gọi phương thức. Hai đối tượng `BigInteger` so sánh giá trị bằng `equals` hoặc thứ tự bằng `compareTo`; `==` chỉ so sánh tham chiếu.

## 2. `add`: cộng số nguyên

```java
BigInteger large = new BigInteger("999999999999999999999");
BigInteger sum = large.add(BigInteger.ONE);
// sum = 1000000000000000000000
```

`add` cộng chính xác theo quy tắc dấu: `5 + (-8) = -3`, `(-5) + (-8) = -13` và cộng 0 giữ nguyên giá trị số. Nó không bị tràn như `int`/`long` chỉ vì vượt giới hạn độ rộng cố định.

Tích lũy phải tái gán vì đối tượng bất biến:

```java
BigInteger total = BigInteger.ZERO;
for (int i = 1; i <= 100; i++) {
    total = total.add(BigInteger.valueOf(i));
}
// total có giá trị 5050
```

Nếu chỉ gọi `total.add(...)`, giá trị `total` không đổi. Để kiểm tra trong Java, dùng `total.equals(BigInteger.valueOf(5050))`.

## 3. `subtract`: trừ có dấu

```java
BigInteger a = BigInteger.valueOf(25);
BigInteger b = BigInteger.valueOf(40);

BigInteger difference = a.subtract(b); // -15
```

Phép trừ trả đúng giá trị `a - b`, kể cả khi âm. Nếu muốn **khoảng cách không âm**, có thể dùng `a.subtract(b).abs()`; nếu cần **chênh lệch có dấu**, giữ nguyên kết quả. Các tính chất:

- `a.subtract(b)` bằng `a.add(b.negate())` theo giá trị.
- `a.subtract(BigInteger.ZERO)` bằng `a`.
- `a.subtract(a)` bằng 0.
- `a.subtract(b).add(b)` trở lại `a`.

`BigInteger` không tự quy định số dư tài khoản, tồn kho hoặc điểm số có được âm hay không. Các ràng buộc như thế thuộc logic nghiệp vụ.

## 4. `multiply`: nhân số nguyên lớn

```java
BigInteger factor = new BigInteger("100000000000000000000");
BigInteger product = factor.multiply(BigInteger.valueOf(12));
// product = 1200000000000000000000
```

Nhân hai số cùng dấu cho kết quả dương; khác dấu cho kết quả âm; nếu có thừa số 0 thì tích bằng 0. Với số nguyên đầu vào hợp lệ, kết quả toán học được tính chính xác và không làm tròn.

```java
BigInteger factorial = BigInteger.ONE;
for (int i = 2; i <= 6; i++) {
    factorial = factorial.multiply(BigInteger.valueOf(i));
}
// factorial = 720
```

Lỗi dễ gặp là tạo `BigInteger.valueOf(x * y)` khi `x` và `y` là `long`: phép `x*y` đã tính theo kiểu `long` **trước khi** chuyển sang `BigInteger` và có thể tràn. Hãy dùng `BigInteger.valueOf(x).multiply(BigInteger.valueOf(y))`.

Dù không bị giới hạn 64 bit, phép nhân số cực lớn vẫn có chi phí CPU/RAM. Với đầu vào từ hệ thống bên ngoài, giới hạn độ dài số theo yêu cầu ứng dụng.

## 5. `divide`: lấy thương nguyên, cắt về 0

`a.divide(b)` không trả phân số hoặc thập phân. Nó trả thương nguyên bằng cách **cắt phần phân số theo hướng về 0**, còn gọi là *truncation toward zero*.

| `a` | `b` | `a.divide(b)` | Diễn giải |
|---:|---:|---:|---|
| 17 | 5 | 3 | 17 / 5 = 3,4 |
| -17 | 5 | -3 | -17 / 5 = -3,4; cắt về 0 |
| 17 | -5 | -3 | 17 / -5 = -3,4 |
| -17 | -5 | 3 | Hai dấu âm triệt tiêu |
| 15 | 5 | 3 | Chia hết |
| 0 | 5 | 0 | Tử số bằng 0 |

**Không nhầm chia về 0 với chia sàn.** `BigInteger.valueOf(-17).divide(BigInteger.valueOf(5))` cho `-3`, chứ không phải `-4`. Với số dương, hai cách đôi khi cùng kết quả nên kiểm thử chỉ với số dương sẽ bỏ sót lỗi.

```java
BigInteger numerator = BigInteger.valueOf(7);
BigInteger denominator = BigInteger.valueOf(2);
BigInteger quotient = numerator.divide(denominator);
// quotient = 3, không phải 3.5
```

Nếu muốn tính tỷ lệ thập phân, dùng `BigDecimal` với độ chính xác, scale và quy tắc làm tròn rõ ràng, hoặc biểu diễn phân số bằng cặp tử số/mẫu số. Chia `BigInteger` không tự động biến sang `BigDecimal`.

### Chia cho 0

`a.divide(BigInteger.ZERO)` ném `ArithmeticException`. Nếu số chia đến từ người dùng, kiểm tra `b.signum() == 0` hay `b.equals(BigInteger.ZERO)` và báo lỗi thích hợp. Phép chia nguyên này không tự tạo `Infinity` hoặc `NaN` như một số phép chia dấu phẩy động.

## 6. `remainder`: phần dư theo phép chia nguyên

Đặt:

\[
q = a.divide(b),\qquad r = a.remainder(b),\qquad b \ne 0.
\]

Thì:

\[
a = q \times b + r,\qquad |r| < |b|.
\]

Ngoài ra, `r == 0` hoặc phần dư `r` có **cùng dấu với số bị chia `a`**. Dấu của số chia không quyết định dấu phần dư.

| `a` | `b` | `divide` | `remainder` | Đẳng thức |
|---:|---:|---:|---:|---|
| 17 | 5 | 3 | 2 | `17 = 3*5+2` |
| -17 | 5 | -3 | -2 | `-17 = (-3)*5+(-2)` |
| 17 | -5 | -3 | 2 | `17 = (-3)*(-5)+2` |
| -17 | -5 | 3 | -2 | `-17 = 3*(-5)+(-2)` |
| 15 | 5 | 3 | 0 | `15 = 3*5` |
| 3 | 5 | 0 | 3 | `3 = 0*5+3` |

```java
BigInteger a = BigInteger.valueOf(-17);
BigInteger b = BigInteger.valueOf(5);
BigInteger q = a.divide(b);     // -3
BigInteger r = a.remainder(b);  // -2
boolean valid = q.multiply(b).add(r).equals(a); // true
```

Vì vậy, **`remainder` có thể trả kết quả âm** nếu số bị chia âm; đây không phải lúc nào cũng là kết quả không âm như phần dư trong số học mô-đun. `remainder(BigInteger.ZERO)` ném `ArithmeticException`.

### `remainder` khác `mod`

`BigInteger.mod(m)` dành cho mô-đun **dương** và luôn trả phần dư không âm trong `[0, m)`. Trong khi đó `remainder(m)` cho phép `m` âm miễn khác 0, và dấu phần dư theo số bị chia:

```java
BigInteger a = BigInteger.valueOf(-17);
BigInteger m = BigInteger.valueOf(5);

a.remainder(m); // -2
a.mod(m);       // 3
```

Gọi `a.mod(BigInteger.valueOf(-5))` hoặc `a.mod(BigInteger.ZERO)` sẽ ném `ArithmeticException` vì mô-đun không dương. Phải phân biệt hai kết quả khi tính chỉ số chu kỳ, lịch quay vòng hoặc phép đồng dư.

Quan trọng: `a.divide(b) * b + a.remainder(b) = a` theo giá trị, nhưng không thể thay `remainder` bằng `mod` và giữ nguyên thương trong mọi trường hợp. Với `a=-17`, `b=5`, cặp thương/dư theo `divide` và `remainder` là `(-3, -2)`; để dùng phần dư mô-đun `3`, thương tương ứng phải là `-4`: `-17 = (-4)*5+3`.

## 7. `divideAndRemainder`: lấy thương và dư đồng thời

Nếu cần cả hai giá trị cho cùng một phép chia, dùng:

```java
BigInteger a = new BigInteger("12345678901234567890");
BigInteger b = BigInteger.valueOf(97);

BigInteger[] qr = a.divideAndRemainder(b);
BigInteger quotient = qr[0];
BigInteger remainder = qr[1];
boolean valid = quotient.multiply(b).add(remainder).equals(a);
```

Mảng luôn chứa **hai phần tử**: `qr[0]` là thương bằng `divide(b)`, `qr[1]` là dư bằng `remainder(b)`. Khi cần cả hai, cách gọi này thường hiệu quả hơn thực hiện hai phép chia riêng rẽ. Nếu `b` bằng 0 thì cũng ném `ArithmeticException`.

Ví dụ chia thời lượng **không âm** thành phút và giây:

```java
BigInteger seconds = BigInteger.valueOf(125);
BigInteger[] qr = seconds.divideAndRemainder(BigInteger.valueOf(60));
// qr[0] = 2 phút; qr[1] = 5 giây
```

Với thời lượng âm, phải thiết kế quy tắc biểu diễn cụ thể vì phần dư có thể âm.

## 8. Chương trình Java 17+ chạy độc lập

Lưu vào tệp **`BigIntegerArithmeticDemo.java`**:

```java
import java.math.BigInteger;

public class BigIntegerArithmeticDemo {
    public static void main(String[] args) {
        BigInteger a = new BigInteger("12345678901234567890");
        BigInteger b = BigInteger.valueOf(97);

        System.out.println("add: " + a.add(b));
        System.out.println("subtract: " + a.subtract(b));
        System.out.println("multiply: " + a.multiply(b));
        System.out.println("divide: " + a.divide(b));
        System.out.println("remainder: " + a.remainder(b));

        BigInteger[] pair = a.divideAndRemainder(b);
        System.out.println("q*r + remainder == a: "
                + pair[0].multiply(b).add(pair[1]).equals(a));
        System.out.println("a unchanged: " + a);

        BigInteger minus17 = BigInteger.valueOf(-17);
        BigInteger plus5 = BigInteger.valueOf(5);
        BigInteger minus5 = BigInteger.valueOf(-5);
        System.out.println("-17 / 5 = " + minus17.divide(plus5));
        System.out.println("-17 remainder 5 = " + minus17.remainder(plus5));
        System.out.println("-17 mod 5 = " + minus17.mod(plus5));
        System.out.println("17 / -5 = " + BigInteger.valueOf(17).divide(minus5));
        System.out.println("17 remainder -5 = "
                + BigInteger.valueOf(17).remainder(minus5));
        System.out.println("-17 / -5 = " + minus17.divide(minus5));
        System.out.println("-17 remainder -5 = " + minus17.remainder(minus5));

        try {
            a.divide(BigInteger.ZERO);
        } catch (ArithmeticException ex) {
            System.out.println("division by zero: ArithmeticException");
        }
        try {
            minus17.mod(minus5);
        } catch (ArithmeticException ex) {
            System.out.println("nonpositive modulus: ArithmeticException");
        }
    }
}
```

Lệnh biên dịch/chạy với JDK 17+:

```bash
javac -encoding UTF-8 BigIntegerArithmeticDemo.java
java BigIntegerArithmeticDemo
```

Kết quả stdout đã xác minh bằng cách biên dịch và chạy Java:

```text
add: 12345678901234567987
subtract: 12345678901234567793
multiply: 1197530853419753085330
divide: 127275040218913071
remainder: 3
q*r + remainder == a: true
a unchanged: 12345678901234567890
-17 / 5 = -3
-17 remainder 5 = -2
-17 mod 5 = 3
17 / -5 = -3
17 remainder -5 = 2
-17 / -5 = 3
-17 remainder -5 = -2
division by zero: ArithmeticException
nonpositive modulus: ArithmeticException
```

Dòng `q*r + remainder == a: true` kiểm chứng phương trình phép chia nguyên. Dòng `a unchanged` thể hiện `BigInteger` bất biến. Bốn tổ hợp dấu cho thấy thương lấy về 0 và phần dư theo dấu **số bị chia**. Hai khối `try/catch` trình bày lỗi chia cho 0 và mô-đun không dương mà không khiến chương trình dừng.

## 9. Kiểm thử bằng tính chất đại số

Với những biến `BigInteger a`, `b`, ta có thể kiểm tra:

```java
a.add(b).subtract(b).equals(a);               // true
a.subtract(b).add(b).equals(a);               // true
a.multiply(BigInteger.ONE).equals(a);         // true
a.multiply(BigInteger.ZERO).equals(BigInteger.ZERO); // true
```

Nếu `b.signum() != 0`, gọi `BigInteger[] qr = a.divideAndRemainder(b);` rồi kiểm tra:

```java
qr[0].multiply(b).add(qr[1]).equals(a);
qr[1].abs().compareTo(b.abs()) < 0;
qr[1].signum() == 0 || qr[1].signum() == a.signum();
```

Đây là các biểu thức kiểm thử minh họa, giả định các biến đã tồn tại. Dùng `compareTo` để so sánh những số lớn mà không cần ép xuống `long`. Nên kiểm thử chia hết, không chia hết, số âm ở cả hai vị trí, số 0, số rất lớn và chia cho 0.

## 10. Lỗi phổ biến

1. **Sử dụng toán tử nguyên thủy:** `a + b` và `a % b` không hợp lệ khi `a`, `b` là `BigInteger`. Dùng `add`, `remainder`.
2. **Không nhận kết quả:** `a.add(b)` không chỉnh sửa `a`; dùng `a = a.add(b)` hoặc biến mới.
3. **Tràn trước khi chuyển kiểu:** `BigInteger.valueOf(x * y)` có thể nhận giá trị đã tràn kiểu `long`. Hãy chuyển từng toán hạng trước rồi `multiply`.
4. **Hiểu nhầm `divide`:** `7/2` là `3` dưới dạng `BigInteger`, không phải `3.5`.
5. **Nhầm hướng làm tròn:** `-17 / 5` cho `-3`, không phải `-4`.
6. **Cho rằng phần dư luôn không âm:** `remainder` của `-17` với `5` là `-2`; `mod(5)` mới cho `3`.
7. **Bỏ qua số chia 0:** cả `divide`, `remainder` và `divideAndRemainder` đều ném `ArithmeticException`.
8. **Dùng `==` để so giá trị:** dùng `equals`; `==` kiểm tra cùng tham chiếu.
9. **Ép về số nhỏ:** `longValue()` có thể làm mất các bit bậc cao khi số lớn; dùng `longValueExact()` để yêu cầu chuyển chính xác (ném `ArithmeticException` nếu không thể).
10. **Không giới hạn kích thước đầu vào:** số nguyên tùy ý độ dài vẫn có chi phí CPU/RAM; ứng dụng cần chính sách xử lý phù hợp.

## 11. Bài tập có lời giải

| Biểu thức Java | Kết quả |
|---|---|
| `BigInteger.valueOf(8).add(BigInteger.valueOf(-3))` | `5` |
| `BigInteger.valueOf(8).subtract(BigInteger.valueOf(13))` | `-5` |
| `BigInteger.valueOf(-4).multiply(BigInteger.valueOf(-6))` | `24` |
| `BigInteger.valueOf(-23).divide(BigInteger.valueOf(7))` | `-3` |
| `BigInteger.valueOf(-23).remainder(BigInteger.valueOf(7))` | `-2` |
| `BigInteger.valueOf(-23).mod(BigInteger.valueOf(7))` | `5` |
| `BigInteger.ZERO.remainder(BigInteger.valueOf(17))` | `0` |
| `BigInteger.ONE.divide(BigInteger.ZERO)` | `ArithmeticException` |

Giải thích trường hợp `-23`: `-23 = (-3)*7 + (-2)` với thương chia về 0. Nếu chọn phần dư mô-đun không âm là `5`, thương tương ứng phải là `-4` để có `-23 = (-4)*7 + 5`.

## Ghi nhớ

`BigInteger` lưu số nguyên tùy ý độ dài, các giá trị **bất biến**. `add`, `subtract` và `multiply` cho kết quả nguyên chính xác. `divide` trả thương nguyên cắt về 0, `remainder` có thể âm theo dấu số bị chia, và cặp thương–phần dư thỏa `a = q*b + r` khi `b != 0`. Dùng `mod(m)` nếu muốn phần dư không âm với `m > 0`. Nếu cần cả thương lẫn dư, `divideAndRemainder` trả `[quotient, remainder]`. Luôn kiểm tra số chia 0 và dùng `equals` để so sánh giá trị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-006">divideAndRemainder và kiểm tra chia hết</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger.divideAndRemainder và kiểm tra chia hết trong Java

## 1. Mục tiêu và bài toán

Java cung cấp kiểu `java.math.BigInteger` để làm việc với số nguyên có độ lớn vượt miền `int` và `long`. Khi cần xác định một số lớn có chia hết cho số khác hay không, phép chia nguyên và phép lấy **số dư** là công cụ chính. Phương thức `divideAndRemainder(BigInteger divisor)` trả về **cả thương lẫn số dư** trong một lần gọi.

Sau bài học, bạn sẽ:

- Biết cách sử dụng `divideAndRemainder`, `divide` và `remainder` đúng hợp đồng.
- Đọc được `BigInteger[]` trả về: `result[0]` là thương, `result[1]` là số dư.
- Kiểm tra chia hết chính xác bằng điều kiện số dư bằng `BigInteger.ZERO`.
- Hiểu quy tắc với số âm: thương cắt về **0**, số dư cùng dấu với số bị chia nếu khác 0.
- Phân biệt `remainder` với `mod`: cùng làm việc với số dư, nhưng **quy tắc dấu và miền đối số không giống nhau**.
- Xử lý trường hợp chia cho 0, phép chia trong các bài toán đếm/lập lịch, dữ liệu nhập từ chuỗi và các phép tính trên số rất lớn.
- Biết khi nào nên lấy cả thương và số dư, và khi nào chỉ cần kiểm tra chia hết.

Ví dụ dùng **Java 17+**. Không cần thư viện bên ngoài hoặc tính năng preview.

## 2. BigInteger là gì và tại sao không dùng long?

`long` có dấu, chứa được các giá trị từ `-9223372036854775808` đến `9223372036854775807`. Nếu số cần tính vượt miền này, phép toán nguyên primitive có thể bị tràn, cho kết quả không còn đúng.

`BigInteger` biểu diễn số nguyên với độ chính xác tùy ý, chịu giới hạn bộ nhớ và khả năng xử lý thực tế của JVM. Nó là một **lớp bất biến (immutable)**: các phép toán trả về kết quả mới hoặc một instance hợp lệ khác, không sửa số nguồn.

~~~java
import java.math.BigInteger;

// Các lệnh sau đặt trong phương thức:
BigInteger huge = new BigInteger("123456789012345678901234567890");
BigInteger divisor = BigInteger.valueOf(9L);

BigInteger[] result = huge.divideAndRemainder(divisor);
System.out.println(result[0]); // Thương
System.out.println(result[1]); // Số dư
~~~

Chú ý cách tạo số lớn: ưu tiên `new BigInteger("...")` từ **chuỗi ký tự** khi giá trị vượt miền `long`. Không viết một literal nguyên quá lớn rồi đưa vào `BigInteger.valueOf(...)` vì literal đã không hợp lệ ở thời điểm biên dịch.

~~~java
BigInteger a = new BigInteger("1000000000000000000000000");
BigInteger b = BigInteger.valueOf(8);
~~~

Hai giá trị trên biểu diễn chính xác số nguyên, không chịu lỗi làm tròn nhị phân như `double`.

## 3. Quy tắc chia nguyên: a = q × b + r

Với hai số nguyên `a` và `b`, trong đó `b != 0`, Java định nghĩa thương và số dư thỏa:

\[
a = q \times b + r
\]

Trong BigInteger:

\[
q = a.\operatorname{divide}(b),\qquad
r = a.\operatorname{remainder}(b)
\]

Ngoài đẳng thức quan trọng trên, phép chia BigInteger có các tính chất:

1. **Thương `q` được cắt về 0** (*truncation toward zero*), không phải luôn làm tròn xuống theo `floor`.
2. **`|r| < |b|`**: độ lớn số dư luôn nhỏ hơn độ lớn của số chia khi `b` khác 0.
3. Nếu `r != 0`, dấu của `r` giống dấu của `a` (số bị chia).
4. Nếu `a` chia hết cho `b`, thì `r = 0`.

Ví dụ `17 / 5`:

\[
17 = 3\times5 + 2.
\]

Trong Java:

~~~java
BigInteger a = BigInteger.valueOf(17);
BigInteger b = BigInteger.valueOf(5);

BigInteger[] qr = a.divideAndRemainder(b);
System.out.println(qr[0]); // 3
System.out.println(qr[1]); // 2
~~~

Một phép chia nguyên có thể có **thương bằng 0** nhưng số dư vẫn khác 0: `3 / 5` có thương 0, dư 3. Vì vậy tuyệt đối không dùng `q.equals(BigInteger.ZERO)` để kết luận có chia hết.

## 4. Cách sử dụng BigInteger.divideAndRemainder

Chữ ký phương thức:

~~~java
public BigInteger[] divideAndRemainder(BigInteger val)
~~~

Phương thức là instance method của `BigInteger`. Nó trả một **mảng gồm hai phần tử** theo thứ tự cố định:

| Chỉ số | Ý nghĩa | Tương ứng |
| --- | --- | --- |
| `result[0]` | Thương nguyên | `a.divide(b)` |
| `result[1]` | Số dư nguyên | `a.remainder(b)` |

Ví dụ:

~~~java
BigInteger amount = new BigInteger("12345");
BigInteger groupSize = BigInteger.valueOf(100);

BigInteger[] qr = amount.divideAndRemainder(groupSize);

BigInteger fullGroups = qr[0];
BigInteger leftover = qr[1];

System.out.println(fullGroups); // 123
System.out.println(leftover);   // 45
~~~

Bạn cũng có thể xác minh phép chia:

~~~java
BigInteger reconstructed = fullGroups.multiply(groupSize)
        .add(leftover);

System.out.println(reconstructed.equals(amount)); // true
~~~

**Vì sao dùng `divideAndRemainder`?** Khi cần **cả thương lẫn số dư**, phương thức thể hiện mục đích trực tiếp và API cho phép tính hai kết quả cùng nhau. Nếu chỉ cần thương hoặc chỉ cần số dư, các phương thức `divide` và `remainder` thường diễn đạt mục đích rõ hơn. Không nên khẳng định rằng `divideAndRemainder` luôn nhanh hơn trong mọi JVM/dữ liệu mà không có đo kiểm phù hợp.

## 5. Kiểm tra chia hết bằng số dư bằng 0

Số nguyên `a` chia hết cho số nguyên khác 0 `b` khi và chỉ khi:

\[
a\bmod b=0
\]

Trong ngữ cảnh Java, nên phát biểu thực thi bằng `a.remainder(b).signum() == 0` hoặc `BigInteger.ZERO.equals(a.remainder(b))`. Cách kiểm tra này không mất độ chính xác, kể cả với số có hàng trăm hoặc hàng nghìn chữ số.

~~~java
BigInteger value = new BigInteger("1000000000000000000000000");
BigInteger divisor = BigInteger.valueOf(8);

boolean divisible = value.remainder(divisor).signum() == 0;
System.out.println(divisible); // true
~~~

Với kết quả `divideAndRemainder`:

~~~java
BigInteger[] qr = value.divideAndRemainder(divisor);
boolean divisible = qr[1].equals(BigInteger.ZERO);

System.out.println(divisible); // true
~~~

**Không dùng toán tử `%` với `BigInteger`:** đây là đối tượng, không phải primitive. `a % b` là cú pháp không hợp lệ nếu `a` và `b` là `BigInteger`. Thay vào đó, gọi `a.remainder(b)`.

**Không dùng `==` để kiểm tra số dư bằng BigInteger.ZERO:** `==` so sánh reference, không phải giá trị số. `equals` hoặc `signum() == 0` mới đúng mục đích.

### Hàm kiểm tra chia hết an toàn

~~~java
static boolean isDivisible(BigInteger dividend, BigInteger divisor) {
    if (dividend == null || divisor == null) {
        throw new IllegalArgumentException("Arguments must not be null");
    }
    if (divisor.signum() == 0) {
        throw new IllegalArgumentException("Divisor must be nonzero");
    }
    return dividend.remainder(divisor).signum() == 0;
}
~~~

Đây là **phương thức ở cấp lớp**; để chạy độc lập, hãy đặt nó trong một class Java và gọi từ `main`. Hàm trên cố ý báo lỗi khi đầu vào `null` hoặc chia cho 0, thay vì đánh đồng trường hợp dữ liệu sai với kết quả “không chia hết”.

## 6. Chia hết không phụ thuộc dấu của số chia

Đối với số nguyên, dấu của số chia không làm thay đổi tính chia hết:

- 20 chia hết cho 5.
- 20 chia hết cho −5.
- −20 chia hết cho 5.
- −20 chia hết cho −5.

~~~java
BigInteger twenty = BigInteger.valueOf(20);
BigInteger minusFive = BigInteger.valueOf(-5);

BigInteger[] qr = twenty.divideAndRemainder(minusFive);

System.out.println(qr[0]);                       // -4
System.out.println(qr[1]);                       // 0
System.out.println(qr[1].signum() == 0);          // true
~~~

Với số chia `-5`, thương là `-4`, số dư bằng 0 và đẳng thức `20 = (-4) × (-5) + 0` vẫn đúng.

Để so sánh số dư của các phép chia có dấu khác nhau, cần hiểu rõ hành vi `remainder`. Một số dư không bằng 0 có thể là số âm; **điều kiện chia hết vẫn chỉ là số dư bằng 0**, không quan tâm dấu của thương hay số chia.

## 7. Chia số âm: thương cắt về 0, dư theo số bị chia

Bảng dưới dùng `divideAndRemainder` với các số nhỏ để thấy rõ quy tắc dấu:

| `a` | `b` | Thương `q` | Số dư `r` | Kiểm chứng |
| ---: | ---: | ---: | ---: | --- |
| 17 | 5 | 3 | 2 | `17 = 3 × 5 + 2` |
| −17 | 5 | −3 | −2 | `−17 = −3 × 5 − 2` |
| 17 | −5 | −3 | 2 | `17 = −3 × (−5) + 2` |
| −17 | −5 | 3 | −2 | `−17 = 3 × (−5) − 2` |
| 15 | −5 | −3 | 0 | `15 = −3 × (−5)` |
| 3 | 5 | 0 | 3 | `3 = 0 × 5 + 3` |

Ví dụ chạy:

~~~java
BigInteger a = BigInteger.valueOf(-17);
BigInteger b = BigInteger.valueOf(5);

BigInteger[] qr = a.divideAndRemainder(b);
System.out.println(qr[0]); // -3
System.out.println(qr[1]); // -2
~~~

Lưu ý `floor(-17 / 5)` theo số thực là −4, nhưng phép chia nguyên BigInteger trả −3, vì nó **cắt về 0**. Không tự thay thế phép chia BigInteger bằng công thức floor nếu bài toán cần đúng hợp đồng Java.

**Remainder âm không phải lỗi:** đó là kết quả hợp lệ của phép `remainder` với số bị chia âm. Nếu bài toán cần một phần dư không âm (ví dụ chỉ số vòng tròn hoặc phép toán đồng dư), hãy cân nhắc `mod` với modulus dương; xem phần tiếp theo.

## 8. Phân biệt remainder và mod

`BigInteger` cung cấp hai phương thức nhìn giống nhau:

- `a.remainder(b)`: phép lấy số dư đi kèm **thương cắt về 0**. Số chia `b` có thể âm, miễn khác 0. Số dư khác 0 mang dấu của **a**.
- `a.mod(m)`: cho **số dư không âm** với **modulus phải dương** (`m > 0`). Nếu `m <= 0`, Java ném `ArithmeticException`.

| Biểu thức | Kết quả |
| --- | ---: |
| `BigInteger.valueOf(17).remainder(BigInteger.valueOf(5))` | 2 |
| `BigInteger.valueOf(-17).remainder(BigInteger.valueOf(5))` | −2 |
| `BigInteger.valueOf(-17).mod(BigInteger.valueOf(5))` | 3 |
| `BigInteger.valueOf(17).remainder(BigInteger.valueOf(-5))` | 2 |
| `BigInteger.valueOf(-17).remainder(BigInteger.valueOf(-5))` | −2 |
| `BigInteger.valueOf(17).mod(BigInteger.valueOf(-5))` | **ArithmeticException** |

~~~java
BigInteger n = BigInteger.valueOf(-17);
BigInteger five = BigInteger.valueOf(5);

System.out.println(n.remainder(five)); // -2
System.out.println(n.mod(five));       // 3
~~~

Hai kết quả `−2` và `3` tương đương nhau **theo modulo 5**, nhưng không bằng nhau dưới dạng số nguyên. Vì `-17 = -4 × 5 + 3`, kết quả `mod(5)` chính là đại diện không âm 3.

### Khi nào dùng phương thức nào?

- **Kiểm tra chia hết:** `remainder(divisor).signum() == 0` là cách đơn giản, kể cả khi divisor âm. `mod` cũng kiểm tra được với divisor dương, nhưng không nhận số chia âm.
- **Muốn cả thương và dư:** `divideAndRemainder` trả cặp tuân quy tắc cắt về 0.
- **Chỉ số xoay vòng, lớp đồng dư, thuật toán số học modulo:** dùng `mod(m)` khi xác định `m > 0` để nhận kết quả trong `[0, m)`.
- **Cần thương kiểu floor với divisor dương:** `divideAndRemainder` không trực tiếp có cùng quy tắc khi dividend âm; phải tính theo yêu cầu toán học riêng.

Đừng dùng `remainder` và `mod` thay thế lẫn nhau chỉ vì các ví dụ số dương thường cho cùng kết quả.

## 9. Chia cho 0: một lỗi bắt buộc xử lý

Phép chia cho 0 không xác định. Với `BigInteger`, các lời gọi sau ném `ArithmeticException`:

~~~java
BigInteger ten = BigInteger.TEN;
BigInteger zero = BigInteger.ZERO;

// ten.divide(zero);             // ArithmeticException
// ten.remainder(zero);          // ArithmeticException
// ten.divideAndRemainder(zero); // ArithmeticException
// ten.mod(zero);                // ArithmeticException (modulus phải dương)
~~~

Nếu chuỗi dữ liệu đầu vào có thể chứa `"0"` cho số chia, nên kiểm tra sớm để báo lỗi có ngữ cảnh, thay vì để exception của phép toán xảy ra sâu trong xử lý:

~~~java
static BigInteger[] divideNonZero(BigInteger a, BigInteger b) {
    if (a == null || b == null) {
        throw new IllegalArgumentException("Missing operands");
    }
    if (b.signum() == 0) {
        throw new IllegalArgumentException("Division by zero");
    }
    return a.divideAndRemainder(b);
}
~~~

Đây là phương thức minh họa ở cấp lớp. **Không** trả một cặp thương/dư `[0, 0]` khi divisor bằng 0; như vậy là tạo kết quả giả và che lỗi nghiệp vụ.

Riêng số bị chia bằng 0 là hợp lệ nếu số chia khác 0: `0 / 7` có thương 0 và dư 0. Vì vậy `0` chia hết cho **mọi số nguyên khác 0**, còn `0 / 0` không được phép.

## 10. Chương trình Java 17+ hoàn chỉnh

Lưu vào file `BigIntegerDivisionDemo.java`:

~~~java
import java.math.BigInteger;

public class BigIntegerDivisionDemo {
    static boolean isDivisible(BigInteger a, BigInteger b) {
        if (a == null || b == null || b.signum() == 0) {
            throw new IllegalArgumentException("Invalid operands");
        }
        return a.remainder(b).signum() == 0;
    }

    static void showDivision(long a, long b) {
        BigInteger left = BigInteger.valueOf(a);
        BigInteger right = BigInteger.valueOf(b);
        BigInteger[] qr = left.divideAndRemainder(right);

        System.out.println(a + "/" + b +
                " => q=" + qr[0] + ", r=" + qr[1] +
                ", divisible=" + qr[1].equals(BigInteger.ZERO));
    }

    public static void main(String[] args) {
        showDivision(17, 5);
        showDivision(-17, 5);
        showDivision(17, -5);
        showDivision(-17, -5);
        showDivision(15, -5);
        showDivision(0, 7);

        BigInteger huge = new BigInteger("1000000000000000000000000");
        BigInteger eight = BigInteger.valueOf(8);
        BigInteger[] parts = huge.divideAndRemainder(eight);

        System.out.println("huge quotient=" + parts[0]);
        System.out.println("huge remainder=" + parts[1]);
        System.out.println("huge divisible=" + isDivisible(huge, eight));

        BigInteger negative = BigInteger.valueOf(-17);
        BigInteger five = BigInteger.valueOf(5);
        System.out.println("remainder=" + negative.remainder(five));
        System.out.println("mod=" + negative.mod(five));

        BigInteger reconstructed = parts[0].multiply(eight).add(parts[1]);
        System.out.println("reconstructed=" + reconstructed.equals(huge));

        try {
            huge.divideAndRemainder(BigInteger.ZERO);
        } catch (ArithmeticException ex) {
            System.out.println("divide by zero: ArithmeticException");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 BigIntegerDivisionDemo.java
java BigIntegerDivisionDemo
~~~

Kết quả stdout:

~~~text
17/5 => q=3, r=2, divisible=false
-17/5 => q=-3, r=-2, divisible=false
17/-5 => q=-3, r=2, divisible=false
-17/-5 => q=3, r=-2, divisible=false
15/-5 => q=-3, r=0, divisible=true
0/7 => q=0, r=0, divisible=true
huge quotient=125000000000000000000000
huge remainder=0
huge divisible=true
remainder=-2
mod=3
reconstructed=true
divide by zero: ArithmeticException
~~~

Giải thích:

1. Bốn trường hợp của `±17 / ±5` có thương được cắt về 0 và số dư theo dấu của số bị chia.
2. `15 / -5` có số dư 0 nên chia hết, dù số chia âm.
3. `0 / 7` cũng chia hết, nhưng phép chia cho 0 là không hợp lệ.
4. `10²⁴ / 8` có thương nguyên chính xác `125000000000000000000000`. Số vượt miền `long` nhưng `BigInteger` vẫn xử lý được.
5. `-17.remainder(5)` theo nghĩa toán tử phương thức cho `-2`; `-17.mod(5)` cho `3` không âm.
6. `q.multiply(b).add(r).equals(a)` là phép đối chiếu đẳng thức thương–dư.
7. Chia cho `BigInteger.ZERO` ném `ArithmeticException`, được bắt và xử lý trong ví dụ.

## 11. Kiểm tra dữ liệu chuỗi trước khi thực hiện phép chia

Trong API và file dữ liệu, số thường được nhận dưới dạng chuỗi. `new BigInteger(text)` parse số nguyên thập phân hợp lệ; nếu không hợp lệ sẽ ném `NumberFormatException`. Một hàm cơ bản:

~~~java
static BigInteger parseRequiredInteger(String text, String fieldName) {
    if (text == null || text.isEmpty()) {
        throw new IllegalArgumentException(fieldName + " is required");
    }
    try {
        return new BigInteger(text);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
                fieldName + " must be a decimal integer", ex);
    }
}
~~~

Đây là method minh họa đặt trong một lớp. Cách viết này nhận số nguyên theo cú pháp `BigInteger(String)`, không nhận số thực hay ký hiệu dấu phân cách grouping như `"1,000"`.

Tùy hợp đồng, ứng dụng có thể:

- Cho phép dấu `-` hoặc dấu `+` của số nguyên.
- Quy định whitespace được bỏ qua bằng bước chuẩn hóa rõ ràng.
- Dùng `new BigInteger(text, radix)` cho biểu diễn hệ 2, hệ 16 hoặc radix hợp lệ.
- Giới hạn số ký tự đầu vào để bảo vệ tài nguyên, vì `BigInteger` rất lớn có chi phí CPU/bộ nhớ.
- Kiểm tra số chia khác 0 và các giới hạn nghiệp vụ trước khi tính.

Không nên dùng `Double.parseDouble` để đọc một số nguyên lớn rồi cast/round; `double` có thể đã làm mất chữ số nguyên. Chuỗi decimal ban đầu nên được parse trực tiếp thành `BigInteger`.

## 12. BigInteger.ZERO, equals và signum

Ba cách kiểm tra số dư bằng 0 phổ biến:

~~~java
BigInteger remainder = BigInteger.valueOf(25)
        .remainder(BigInteger.valueOf(5));

boolean a = remainder.equals(BigInteger.ZERO);
boolean b = BigInteger.ZERO.equals(remainder);
boolean c = remainder.signum() == 0;

System.out.println(a && b && c); // true
~~~

Tất cả đều đúng khi `remainder` là một `BigInteger` khác null. Trong phép `divideAndRemainder` thành công, cả hai phần tử mảng không phải null. Với một nguồn có thể null từ bên ngoài, `BigInteger.ZERO.equals(value)` là cách so sánh an toàn trước null; `value.signum()` cần null guard.

**Sai lầm phổ biến:**

~~~java
// Sai mục đích: == so sánh identity của đối tượng BigInteger
// if (remainder == BigInteger.ZERO) { ... }
~~~

Một kết quả tình cờ cùng reference với `BigInteger.ZERO` không phải hợp đồng để xác định hai số có cùng giá trị. Hãy dùng `equals` hoặc `signum`.

### Vì sao signum hữu ích?

`signum()` trả:

- `-1` khi số âm;
- `0` khi số bằng 0;
- `1` khi số dương.

Nó kiểm tra dấu trên các số nguyên rất lớn mà không cần đổi sang `int` hay `long`. Một số dư bằng 0 sẽ có `signum()==0` bất kể số bị chia/số chia ban đầu có dấu gì.

## 13. So sánh cách dùng divideAndRemainder, divide và remainder

Cả ba API đều thực hiện phép chia nguyên, nhưng mục đích biểu đạt khác nhau.

~~~java
BigInteger a = new BigInteger("12345");
BigInteger b = BigInteger.valueOf(100);

// Cần cả hai:
BigInteger[] qr = a.divideAndRemainder(b);
BigInteger quotient = qr[0];
BigInteger remainder = qr[1];

// Chỉ cần thương:
BigInteger qOnly = a.divide(b);

// Chỉ cần dư:
BigInteger rOnly = a.remainder(b);
~~~

| Nhu cầu | API nên cân nhắc |
| --- | --- |
| Chia thành các nhóm đầy và số còn lại | `divideAndRemainder` |
| Lấy thương nguyên | `divide` |
| Kiểm tra chia hết | `remainder` rồi kiểm tra zero |
| Lấy số dư không âm với modulus dương | `mod` |
| Kiểm tra đồng dư trong mô hình modulo m > 0 | `mod` hoặc `subtract` + `mod` |

**Khi cần cả thương lẫn dư, hãy gọi `divideAndRemainder` một lần** để mã ngắn và thể hiện ý đồ. Khi chỉ muốn kiểm tra chia hết, chỉ lấy remainder là đủ; không cần tính hoặc giữ thương.

Không tự convert `BigInteger` sang `long` chỉ để dùng `%` primitive. Nếu giá trị vượt miền `long`, `longValue()` có thể cắt mất bit cao và cho dữ liệu sai. Khi bắt buộc chuyển kiểu, cân nhắc `longValueExact()` để phát hiện giá trị ngoài miền bằng `ArithmeticException`.

## 14. Chia thành các lô hàng hoặc nhóm

Giả sử có số lượng vật phẩm `items` và mỗi thùng chứa tối đa `capacity` món. Cần biết số thùng **đầy** và số vật phẩm **còn lại**:

~~~java
BigInteger items = new BigInteger("12345");
BigInteger capacity = BigInteger.valueOf(100);
BigInteger[] qr = items.divideAndRemainder(capacity);

BigInteger fullBoxes = qr[0];
BigInteger leftoverItems = qr[1];

System.out.println(fullBoxes);    // 123
System.out.println(leftoverItems); // 45
~~~

Nếu cần số thùng **tổng cộng**, bao gồm thùng cuối chưa đầy, và các giá trị đều **không âm** với `capacity > 0`:

~~~java
BigInteger boxesNeeded = qr[1].signum() == 0
        ? qr[0]
        : qr[0].add(BigInteger.ONE);

System.out.println(boxesNeeded); // 124
~~~

Mẫu này tương đương phép chia lấy **ceil** với `items >= 0` và `capacity > 0`. Không áp dụng nguyên xi cho số âm, nơi thương BigInteger cắt về 0 và khái niệm “thùng hàng” không còn phù hợp. Việc kiểm tra miền nghiệp vụ nên diễn ra trước khi tính.

## 15. Áp dụng cho chu kỳ, phân trang, hệ thống batch

Một tình huống thường gặp khác là phân chia một tổng công việc thành từng batch có kích thước cố định.

Ví dụ:

~~~java
BigInteger records = new BigInteger("1001");
BigInteger batchSize = BigInteger.valueOf(100);

BigInteger[] qr = records.divideAndRemainder(batchSize);
System.out.println("full batches=" + qr[0]); // full batches=10
System.out.println("remainder=" + qr[1]);    // remainder=1
~~~

Nếu câu hỏi là “có thể chia đều thành các batch cùng kích thước không?”, chỉ cần:

~~~java
boolean evenBatches = qr[1].signum() == 0;
System.out.println(evenBatches); // false
~~~

Khi làm phân trang hoặc đánh số vị trí từ 0, hãy chú ý yêu cầu rõ ràng của chỉ số (có âm hay không, kích thước trang có dương hay không). `remainder` âm với chỉ số âm có thể khiến việc truy cập mảng thất bại. Với yêu cầu **chỉ số vòng tròn không âm**, `mod` với modulus dương là cách phù hợp hơn.

## 16. Kiểm tra chia hết cho một số cụ thể

### Chẵn hay lẻ

Kiểm tra tính chẵn bằng số dư 2:

~~~java
BigInteger huge = new BigInteger("1000000000000000000000000");
boolean even = huge.remainder(BigInteger.TWO).signum() == 0;

System.out.println(even); // true
~~~

`BigInteger.TWO` có sẵn trong Java 17. Với phép toán bit, `!huge.testBit(0)` cũng kiểm tra bit thấp nhất bằng 0, nhưng sử dụng `remainder(BigInteger.TWO)` bộc lộ ý nghĩa chia hết dễ đọc cho người mới học.

### Chia hết cho một tích hoặc bội

Giả sử muốn kiểm tra `n` chia hết cho `12`:

~~~java
BigInteger n = new BigInteger("123456");
BigInteger twelve = BigInteger.valueOf(12);

System.out.println(n.remainder(twelve).signum() == 0); // true
~~~

Không nên tự thay một phép chia cho 12 bằng nhiều điều kiện số dư nếu không có lý do rõ ràng: một tiêu chí trực tiếp bằng `remainder(12)` là đầy đủ và chính xác. Các quy tắc toán học như “chia hết cho 3 và 4 thì chia hết cho 12” dựa trên quan hệ bội nguyên tố cùng nhau; nên chứng minh trước khi tối ưu theo chúng.

### Số bị chia bằng 0

~~~java
System.out.println(BigInteger.ZERO.remainder(BigInteger.TEN)); // 0
~~~

Số 0 chia hết cho mọi số khác 0. Nhưng **không thể chia cho 0**. Quy tắc này cần phân biệt trong validation, nhất là khi người dùng nhập một “ước số” vào form.

## 17. Những giới hạn và lỗi cần chú ý

| Trường hợp | Điều cần biết |
| --- | --- |
| `a.divideAndRemainder(BigInteger.ZERO)` | Ném `ArithmeticException` |
| `a.remainder(BigInteger.ZERO)` | Ném `ArithmeticException` |
| `a.mod(BigInteger.ZERO)` | Ném `ArithmeticException` vì modulus không dương |
| `a.mod(negative)` | Ném `ArithmeticException`, dù `remainder(negative)` hợp lệ |
| `a.divideAndRemainder(null)` | Đầu vào null không phải divisor hợp lệ; cần null guard |
| `result[0] == 0` | Thương bằng 0 không chứng minh phép chia hết |
| `result[1].signum() == 0` | Đúng điều kiện chia hết với divisor khác 0 |
| `result[1] == BigInteger.ZERO` | So sánh object identity, không dùng làm kiểm tra giá trị |
| `result[1].intValue()` | Có thể mất dữ liệu với số dư lớn; không cần thiết để kiểm tra zero |
| `BigInteger.valueOf(veryLargeLiteral)` | Literal/`long` có miền hữu hạn; parse chuỗi trực tiếp |
| `BigDecimal.divide` | Là phép chia decimal với quy tắc scale/rounding khác; không thay cho phép chia nguyên BigInteger |
| `a.mod(m)` và `a.remainder(m)` | Khác kết quả khi `a` âm và `m` dương |
| `a.divide(b)` so với `Math.floorDiv` | BigInteger cắt thương về 0; floor division primitive có quy tắc khác với số âm |

### BigInteger là immutable

Các lời gọi như `a.divide(b)`, `a.remainder(b)` hay `a.divideAndRemainder(b)` **không sửa `a` hoặc `b`**. Mảng trả về là một container bạn có thể gán lại phần tử, nhưng các đối tượng `BigInteger` trong mảng vẫn bất biến.

Ví dụ:

~~~java
BigInteger a = BigInteger.valueOf(17);
BigInteger b = BigInteger.valueOf(5);
BigInteger[] qr = a.divideAndRemainder(b);

qr[0] = BigInteger.ZERO; // Gán lại mảng, không sửa a hoặc b.
System.out.println(a);   // 17
~~~

Đừng suy rằng vì mảng có thể thay đổi nên bản thân số nguồn đã được thay đổi.

## 18. Kiểm chứng bằng tính chất toán học

Một cách kiểm thử tốt là đối chiếu **đồng thời** cả thương và số dư với định nghĩa. Với `b != 0`:

\[
a = q b + r,\qquad |r|<|b|
\]

và nếu `r != 0`, `sign(r) = sign(a)`.

Ví dụ phương thức kiểm thử:

~~~java
static boolean validQuotientRemainder(
        BigInteger a, BigInteger b, BigInteger[] qr) {
    if (a == null || b == null || b.signum() == 0
            || qr == null || qr.length != 2
            || qr[0] == null || qr[1] == null) {
        return false;
    }

    BigInteger q = qr[0];
    BigInteger r = qr[1];

    boolean equation = q.multiply(b).add(r).equals(a);
    boolean bounded = r.abs().compareTo(b.abs()) < 0;
    boolean signCorrect = r.signum() == 0
            || r.signum() == a.signum();

    return equation && bounded && signCorrect;
}
~~~

Đây là **phương thức minh họa cấp lớp**, có thể bổ sung vào các bài kiểm thử tự động. Các tính chất trên mạnh hơn việc chỉ kiểm tra một vài ví dụ dương và giúp phát hiện những giả định sai khi mở rộng thuật toán.

Có thể thử nhiều bộ đầu vào:

- Cả `a` và `b` dương.
- `a` âm, `b` dương.
- `a` dương, `b` âm.
- Cả hai âm.
- `a` bằng 0, `b` khác 0.
- `|a| < |b|`, thương bằng 0.
- `a` là bội của `b`, số dư bằng 0.
- Số có hàng trăm chữ số.
- `b` bằng 0: kiểm tra ngoại lệ, không tìm thương/dư.

## 19. Các câu hỏi tự kiểm tra

**Câu 1.** `BigInteger.valueOf(17).divideAndRemainder(BigInteger.valueOf(5))` trả gì?

**Đáp án:** Mảng `[3, 2]`, trong đó thương 3 và dư 2. Vì dư khác 0 nên 17 không chia hết cho 5.

**Câu 2.** `-17` chia cho `5` trong BigInteger có thương và dư nào?

**Đáp án:** Thương `-3`, dư `-2`; `−17 = (−3) × 5 + (−2)`. Không dùng `floor(-17/5)` để suy thương.

**Câu 3.** Kiểm tra chia hết bằng `qr[0].equals(BigInteger.ZERO)` có đúng không?

**Đáp án:** Không. Đó là kiểm tra thương bằng 0. Chia hết khi **`qr[1].signum() == 0`**, với divisor khác 0.

**Câu 4.** Tại sao `BigInteger.valueOf(-17).remainder(BigInteger.valueOf(5))` bằng `-2`, còn `mod(BigInteger.valueOf(5))` bằng `3`?

**Đáp án:** `remainder` giữ quy tắc thương cắt về 0 và dư có dấu của số bị chia; `mod` chuẩn hóa kết quả về `[0, m)` khi `m > 0`.

**Câu 5.** `BigInteger.ZERO` có chia hết cho `BigInteger.valueOf(7)` không?

**Đáp án:** Có, vì `0 = 0 × 7 + 0`. Nhưng phép chia `0 / 0` không được định nghĩa.

**Câu 6.** Khi cần số thùng đầy **và** số món còn lại, nên dùng phương thức nào?

**Đáp án:** `divideAndRemainder`; phần tử `[0]` là thương/số thùng đầy, `[1]` là số còn lại. Kiểm tra số lượng không âm và sức chứa dương trước khi dùng công thức thùng hàng.

**Câu 7.** Nếu chỉ kiểm tra chia hết với một số rất lớn, có nên dùng `longValue()` để lấy `%` không?

**Đáp án:** Không. Chuyển `BigInteger` ngoài miền `long` có thể mất phần bit cao. Dùng `remainder` trực tiếp để đảm bảo chính xác.

**Câu 8.** Tại sao `BigInteger.mod` không cho modulus âm?

**Đáp án:** Hợp đồng của `mod` yêu cầu modulus **dương**, để số dư không âm trong `[0, m)`. Muốn lấy dư với divisor âm theo phép chia nguyên, dùng `remainder`.

## 20. Tóm tắt và tài liệu tham khảo

- **`divideAndRemainder(divisor)`** trả cặp `[quotient, remainder]`; cả hai chính xác trên `BigInteger`.
- Với divisor khác 0: `a = q.multiply(b).add(r)`, `|r| < |b|`; thương **cắt về 0**, dư mang dấu số bị chia nếu khác 0.
- **Kiểm tra chia hết:** `a.remainder(b).signum() == 0`, sau khi xác thực `b != 0`.
- Dùng **`equals` hoặc `signum`**, không dùng `==` cho giá trị của `BigInteger`.
- **`remainder` khác `mod`** khi số bị chia âm; `mod` yêu cầu modulus dương.
- **Chia cho 0 ném `ArithmeticException`**; đừng biến lỗi thành kết quả giả.
- Với số vượt miền `long`, tạo `BigInteger` từ chuỗi và tránh đổi sang primitive chỉ để tính toán.

Tài liệu chính thức:

- [BigInteger — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): `divideAndRemainder`, `divide`, `remainder`, `mod`, `ZERO`, `signum` và quy tắc ngoại lệ.
- [Java Language Specification 17 — Integer Division](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17.2): cơ sở đối chiếu quy tắc thương nguyên cắt về 0.
- [Java Language Specification 17 — Remainder Operator](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17.3): quan hệ thương/dư trong toán tử primitive để hiểu dấu số dư.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-007">pow, sqrt, sqrtAndRemainder và gcd</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger.pow, sqrt, sqrtAndRemainder và gcd trong Java

## 1. Mục tiêu bài học

**java.math.BigInteger** là lớp số nguyên có độ chính xác tùy ý trong Java. Thay vì bị giới hạn ở 32 bit của int hoặc 64 bit của long, nó có thể biểu diễn và tính toán số nguyên rất lớn trong giới hạn bộ nhớ và khả năng xử lý của môi trường.

Bài này tập trung vào bốn phương thức:

- **pow(int exponent)**: tính lũy thừa nguyên với số mũ nguyên **không âm**.
- **sqrt()**: lấy **căn bậc hai nguyên**, tức phần nguyên dưới của căn bậc hai số không âm.
- **sqrtAndRemainder()**: đồng thời lấy **căn bậc hai nguyên** và **phần dư** so với bình phương căn nguyên.
- **gcd(BigInteger val)**: ước chung lớn nhất (greatest common divisor) của hai số nguyên, kể cả đầu vào âm hoặc 0.

Điểm chung là **kết quả nguyên chính xác**, không cần đổi sang double và không phải chấp nhận mất chữ số do số dấu phẩy động. Bạn sẽ học định nghĩa, giới hạn đầu vào, trường hợp biên, cách kết hợp các phương thức, tối ưu có căn cứ và kiểm thử bằng chương trình Java 17+.

## 2. Ôn lại BigInteger: bất biến và tính toán chính xác

Tạo BigInteger bằng chuỗi thập phân hoặc một giá trị long:

~~~java
import java.math.BigInteger;

BigInteger a = new BigInteger("123456789012345678901234567890");
BigInteger b = BigInteger.valueOf(42);
BigInteger sum = a.add(b);
~~~

Các phép tính **không sửa** a và b. BigInteger là kiểu đối tượng **immutable**: mỗi phương thức trả về một giá trị kết quả theo quy tắc API, còn toán hạng ban đầu không đổi. Với các hằng hay dùng, sử dụng **BigInteger.ZERO**, **ONE**, **TWO**, **TEN** (TWO có từ Java 9).

Đừng viết:

~~~java
// Không hợp lệ: BigInteger không có phép cộng toán tử + giữa các đối tượng.
// BigInteger invalid = a + b;
~~~

Hãy dùng a.add(b), a.multiply(b), a.pow(...). Đặc tính bất biến cũng có nghĩa:

~~~java
BigInteger x = BigInteger.valueOf(3);
x.pow(4);                 // kết quả bị bỏ qua
System.out.println(x);   // vẫn là 3
BigInteger y = x.pow(4);  // 81
~~~

### Vì sao không chuyển qua double?

Dạng double chỉ có khoảng **53 bit chính xác** trong significand; các số nguyên lớn không luôn được biểu diễn đúng. Ví dụ một số có hàng trăm chữ số có thể vượt hẳn miền hữu hạn của double. Khi muốn căn bậc hai nguyên hoặc gcd **chính xác** trên số lớn, hãy giữ dữ liệu ở BigInteger, không đi qua Math.sqrt(double).

## 3. So sánh nhanh bốn API

| Phương thức | Input | Output | Ý nghĩa toán học | Trường hợp cần lưu ý |
| --- | --- | --- | --- | --- |
| **a.pow(e)** | e kiểu **int**, e ≥ 0 | BigInteger | a^e chính xác | e âm → ArithmeticException |
| **n.sqrt()** | n ≥ 0 | BigInteger | floor(√n) | n âm → ArithmeticException |
| **n.sqrtAndRemainder()** | n ≥ 0 | BigInteger[2] | [s, n−s²], s=floor(√n) | n âm → ArithmeticException |
| **a.gcd(b)** | a, b là BigInteger | BigInteger | gcd(|a|,|b|) không âm | gcd(0,0) = 0 |

**pow** đã có từ các phiên bản Java rất sớm. **sqrt** và **sqrtAndRemainder** được giới thiệu từ **Java 9**; trong Java 17 chúng có sẵn. **gcd** cũng có sẵn trong Java 17.

Chữ **floor** nghĩa là lấy số nguyên lớn nhất không vượt giá trị thực. Với n=200, √200 xấp xỉ 14.142..., nên kết quả **sqrt() bằng 14**, không phải số thập phân 14.142.

## 4. pow(int exponent): lũy thừa số nguyên chính xác

Với BigInteger a và số mũ e không âm:

\[
a^e = \underbrace{a\times a\times\dots\times a}_{e\text{ thừa số}}
\]

Phương thức:

~~~java
BigInteger base = BigInteger.valueOf(12);
BigInteger result = base.pow(5); // 248832
~~~

Kết quả là BigInteger chính xác. Không có overflow 32/64 bit trong phép nhân nội bộ theo cách int hoặc long thường gặp; tuy nhiên nếu kết quả quá lớn so với bộ nhớ hay giới hạn triển khai, thao tác vẫn có thể không thực hiện được.

### Số mũ là int, không phải BigInteger

Chữ ký là **BigInteger pow(int exponent)**:

~~~java
BigInteger x = BigInteger.valueOf(7);
BigInteger y = x.pow(4); // 2401
~~~

Không có overload **pow(BigInteger)** với ý nghĩa lũy thừa tổng quát. Nếu bạn có một BigInteger làm số mũ, phải thiết kế lại thuật toán hoặc chuyển về int với kiểm tra **intValueExact()** nếu thật sự thỏa giới hạn. Không biến một số mũ cực lớn thành int bằng intValue() rồi cho rằng kết quả vẫn đúng: thao tác đó có thể mất bit.

### Dấu âm theo tính chẵn lẻ của số mũ

\[
(-3)^4=81,\qquad (-3)^5=-243
\]

~~~java
BigInteger minusThree = BigInteger.valueOf(-3);
BigInteger even = minusThree.pow(4); // 81
BigInteger odd = minusThree.pow(5);  // -243
~~~

Đối với cơ số âm, số mũ chẵn tạo kết quả không âm, số mũ lẻ tạo kết quả âm. Với cơ số dương, kết quả dương khi số mũ dương.

### Các trường hợp 0 và số mũ 0

Trong API BigInteger:

~~~java
BigInteger a = BigInteger.ZERO.pow(0); // 1
BigInteger b = BigInteger.ZERO.pow(5); // 0
BigInteger c = BigInteger.TEN.pow(0);  // 1
~~~

Theo quy ước của phép lũy thừa nguyên trong API, **0.pow(0) trả BigInteger.ONE**. Đừng nhầm quy ước lập trình này với việc tìm giới hạn giải tích tại (0,0): đó là câu hỏi toán học khác.

### Số mũ âm là không hợp lệ

~~~java
BigInteger base = BigInteger.valueOf(2);
// base.pow(-1) -> ArithmeticException
~~~

Đối với số nguyên, **2^−1=1/2** không còn thuộc tập số nguyên. BigInteger.pow không trả phân số hoặc số thập phân, và **ném ArithmeticException khi exponent < 0**. Nếu bài toán cần số mũ âm, hãy thiết kế biểu diễn phân số/BigDecimal hoặc thuật toán cụ thể; không ép kiểu ngầm.

### pow khác Math.pow

| Đặc điểm | BigInteger.pow | Math.pow |
| --- | --- | --- |
| Cơ số | BigInteger | double |
| Số mũ | int không âm | double |
| Kết quả | BigInteger nguyên chính xác | double, có thể làm tròn |
| Số cực lớn | Không bị chặn bởi 64 bit, vẫn có giới hạn tài nguyên | Có thể overflow thành Infinity |
| Số mũ âm | Không hỗ trợ; ArithmeticException | Có hỗ trợ theo ngữ nghĩa double |

Ví dụ **BigInteger.TEN.pow(20)** cho chính xác **100000000000000000000**. Dùng **Math.pow(10,20)** trả một double có biểu diễn riêng, và khi áp dụng sang số nguyên lớn nói chung không có bảo đảm giữ mọi chữ số.

### Không tự dùng vòng lặp nhân ngây thơ cho kết quả khổng lồ

Có thể viết vòng lặp nhân để minh họa toán học, nhưng BigInteger.pow đã được tối ưu theo đặc điểm kiểu. Một số mũ rất lớn có thể tạo kết quả có hàng triệu bit hoặc hơn. Với **input không đáng tin cậy**, kiểm tra số mũ và giới hạn độ dài kết quả **trước** khi chạy; không dựa vào việc BigInteger có độ chính xác tùy ý để bỏ giới hạn tài nguyên.

## 5. sqrt(): căn bậc hai nguyên, không phải căn thập phân

Với n là BigInteger không âm, đặt:

\[
s=\lfloor\sqrt n\rfloor
\]

Khi đó s là **số nguyên không âm lớn nhất thỏa s² ≤ n**. Định nghĩa tương đương:

\[
s^2\le n <(s+1)^2
\]

Ví dụ:

| n | √n trong toán học | n.sqrt() |
| ---: | ---: | ---: |
| 0 | 0 | 0 |
| 1 | 1 | 1 |
| 2 | xấp xỉ 1.414... | 1 |
| 15 | xấp xỉ 3.873... | 3 |
| 16 | 4 | 4 |
| 17 | xấp xỉ 4.123... | 4 |
| 81 | 9 | 9 |
| 200 | xấp xỉ 14.142... | 14 |

~~~java
BigInteger n = BigInteger.valueOf(200);
BigInteger floorRoot = n.sqrt(); // 14
~~~

**sqrt() không trả double**, không làm tròn đến số nguyên gần nhất, cũng không lấy ceiling. Nó lấy **floor** của căn bậc hai không âm.

### Tại sao phải dùng BigInteger.sqrt thay vì Math.sqrt(double)?

Nếu n có hàng chục hoặc hàng trăm chữ số, việc chuyển sang double có thể **làm tròn n**, thậm chí thành **Infinity**. Áp dụng Math.sqrt lên giá trị đã đổi kiểu rồi cast kết quả về long/int không thể khôi phục thông tin đã mất.

Ngay cả khi double còn hữu hạn, phép làm tròn có thể khiến căn của một số rất gần số chính phương trở thành một số nguyên **không đúng**. BigInteger.sqrt tính đúng kết quả nguyên của **chính n**, không dựa vào xấp xỉ double làm kết quả đầu ra.

### sqrt của số âm

~~~java
BigInteger negative = BigInteger.valueOf(-4);
// negative.sqrt() -> ArithmeticException
~~~

BigInteger là số nguyên thực có dấu, không phải số phức. Với **n < 0**, sqrt() ném **ArithmeticException**. Hãy kiểm tra **n.signum() >= 0** nếu giao diện cần lỗi có thông điệp riêng.

### Kiểm tra số chính phương

Số chính phương là số dạng k² với k nguyên. Với n ≥ 0:

~~~java
static boolean isPerfectSquare(BigInteger n) {
    if (n.signum() < 0) {
        return false;
    }
    BigInteger s = n.sqrt();
    return s.multiply(s).equals(n);
}
~~~

Đây là phương thức minh họa cần đặt bên trong class và import **java.math.BigInteger**. Với n=81, s=9, phép so sánh trả true. Với n=200, s=14, 14²=196 khác 200, trả false.

Một cách có thể tận dụng API tiếp theo là dùng **sqrtAndRemainder()[1].signum() == 0** trên n không âm.

## 6. sqrtAndRemainder(): lấy đồng thời căn nguyên và phần dư

Với n ≥ 0:

\[
s=\lfloor\sqrt n\rfloor,\qquad r=n-s^2
\]

**BigInteger.sqrtAndRemainder()** trả **mảng BigInteger[] gồm chính xác hai phần tử**:

- **result[0]** là s, căn bậc hai nguyên.
- **result[1]** là r, phần dư khi trừ đi s².

~~~java
BigInteger n = BigInteger.valueOf(200);
BigInteger[] pair = n.sqrtAndRemainder();

BigInteger s = pair[0]; // 14
BigInteger r = pair[1]; // 4
~~~

Phải gọi **r = n−s²**, **không phải** phép chia lấy dư như **n % s**. Ví dụ 200 chia 14 dư 4 trùng ngẫu nhiên trong ví dụ này, nhưng về tổng quát hai định nghĩa không đồng nhất.

Lấy n=99 để thấy rõ:

~~~java
BigInteger[] pair = BigInteger.valueOf(99).sqrtAndRemainder();
// s = 9, r = 99-81 = 18
// Nhưng 99 % 9 = 0. Hai loại "remainder" khác nhau.
~~~

### Các bất biến toán học quan trọng

Do s=floor(√n):

\[
s^2\le n <(s+1)^2=s^2+2s+1
\]

Suy ra:

\[
n=s^2+r,\qquad 0\le r<2s+1
\]

Vì r nguyên, có thể viết **0 ≤ r ≤ 2s**.

Dùng bất biến này để kiểm tra kết quả:

~~~java
BigInteger[] qr = n.sqrtAndRemainder();
BigInteger s = qr[0];
BigInteger r = qr[1];

boolean exactReconstruction = s.pow(2).add(r).equals(n);
boolean validRange = r.signum() >= 0
        && r.compareTo(s.shiftLeft(1).add(BigInteger.ONE)) < 0;
~~~

Đoạn code minh họa nằm trong method với **n ≥ 0** đã khai báo; **shiftLeft(1)** nhân s với 2. Mọi bước đều là BigInteger, không lo tràn int/long trong phép nhân/tổng.

### Khi nào dùng sqrtAndRemainder thay sqrt?

- Cần đồng thời **căn nguyên** và **phần dư**.
- Cần kiểm tra **số chính phương** (r=0).
- Cần một bất biến đầu ra để kiểm thử thuật toán số nguyên.
- Muốn biết n nằm **cách bình phương nguyên lớn nhất không vượt n bao xa**.

Ví dụ:

| n | s = floor(√n) | r = n−s² | n là số chính phương? |
| ---: | ---: | ---: | --- |
| 0 | 0 | 0 | Có |
| 1 | 1 | 0 | Có |
| 2 | 1 | 1 | Không |
| 81 | 9 | 0 | Có |
| 99 | 9 | 18 | Không |
| 200 | 14 | 4 | Không |

sqrtAndRemainder() cũng **ném ArithmeticException cho số âm**. Nếu chỉ cần s, dùng sqrt() là trực tiếp và rõ ràng hơn.

## 7. gcd(BigInteger): ước chung lớn nhất

**gcd** là viết tắt của **greatest common divisor**, tiếng Việt là **ước chung lớn nhất**. Đối với hai số nguyên a, b không cùng bằng 0, gcd(a,b) là số nguyên dương lớn nhất chia hết cả hai.

Ví dụ:

~~~java
BigInteger a = BigInteger.valueOf(54);
BigInteger b = BigInteger.valueOf(24);
BigInteger g = a.gcd(b); // 6
~~~

Các ước chung dương của 54 và 24 gồm 1, 2, 3, 6; số lớn nhất là 6.

### Số âm không làm gcd âm

**BigInteger.gcd** luôn trả số **không âm**, về bản chất:

\[
\gcd(a,b)=\gcd(|a|,|b|)
\]

~~~java
BigInteger g = BigInteger.valueOf(-54)
        .gcd(BigInteger.valueOf(24)); // 6
~~~

Nếu cả hai toán hạng khác 0, gcd trả số dương. Với một toán hạng 0, gcd trả độ lớn tuyệt đối của toán hạng còn lại.

### Các trường hợp zero

| a | b | a.gcd(b) |
| ---: | ---: | ---: |
| 54 | 24 | 6 |
| −54 | 24 | 6 |
| 0 | 24 | 24 |
| 0 | −24 | 24 |
| 0 | 0 | **0** |
| 101 | 24 | 1 |

Trong lý thuyết số có thể có những cách nói khác nhau về gcd(0,0), nhưng **API BigInteger.gcd(0,0) định nghĩa trả 0**. Không tự ném exception cho trường hợp này nếu muốn bám sát hành vi API.

**Gcd bằng 1** nghĩa là hai số **nguyên tố cùng nhau** (*coprime*), không nhất thiết từng số đều là số nguyên tố.

### Vì sao gcd phù hợp với số rất lớn?

Thuật toán Euclid dựa trên phép chia lấy dư:

\[
\gcd(a,b)=\gcd(b,a\bmod b)
\]

Lặp cho đến khi số dư bằng 0. Java cung cấp BigInteger.gcd đã triển khai sẵn, nên không cần tự viết phiên bản dựa trên int/long rồi vô tình overflow hoặc làm mất các chữ số lớn.

Ví dụ nếu đầu vào có hàng trăm chữ số, không chuyển về long để tính gcd. Giữ BigInteger và gọi a.gcd(b).

## 8. Ứng dụng kết hợp: rút gọn phân số bằng gcd

Cho phân số a/b với **b ≠ 0**. Nếu g=gcd(a,b) thì:

\[
\frac{a}{b}=\frac{a/g}{b/g}
\]

Ví dụ **84/30** có gcd bằng 6 nên rút gọn thành **14/5**:

~~~java
BigInteger numerator = BigInteger.valueOf(84);
BigInteger denominator = BigInteger.valueOf(30);

if (denominator.signum() == 0) {
    throw new ArithmeticException("Zero denominator");
}
BigInteger g = numerator.gcd(denominator);
BigInteger reducedNumerator = numerator.divide(g);   // 14
BigInteger reducedDenominator = denominator.divide(g); // 5
~~~

Đây là đoạn minh họa bên trong method. BigInteger.divide chia nguyên và ở đây **chia hết** vì g là ước chung.

Để có biểu diễn phân số chuẩn, thường quy định **mẫu số dương**: nếu denominator âm, đổi dấu cả tử và mẫu trước hoặc sau bước rút gọn. Nếu numerator=0, gcd(0,b)=|b|, nên phân số 0/b có thể chuẩn hóa thành 0/1 (khi đã chuẩn hóa dấu mẫu).

**Cảnh báo:** gcd(0,0)=0, nhưng 0/0 vẫn là phân số không hợp lệ; phải kiểm tra mẫu số bằng 0 **trước** khi chia cho gcd.

## 9. Ứng dụng kết hợp: bội chung nhỏ nhất (LCM)

Bội chung nhỏ nhất của hai số nguyên a, b có thể tính từ gcd:

\[
\operatorname{lcm}(a,b)=
\begin{cases}
0,&a=0\text{ hoặc }b=0\\
\left|\frac{a}{\gcd(a,b)}\times b\right|,&\text{ngược lại}
\end{cases}
\]

Để tránh tạo ra trung gian lớn hơn mức cần thiết, **chia cho gcd trước rồi mới nhân**:

~~~java
static BigInteger lcm(BigInteger a, BigInteger b) {
    if (a.signum() == 0 || b.signum() == 0) {
        return BigInteger.ZERO;
    }
    return a.divide(a.gcd(b)).multiply(b).abs();
}
~~~

Đây là phương thức minh họa cần đặt bên trong class. Ví dụ lcm(84,30)=420; với đầu vào âm, **abs()** đảm bảo kết quả không âm. Với BigInteger, nhân không bị overflow kiểu long nhưng vẫn có chi phí bộ nhớ/CPU, nên giảm trung gian có ích.

## 10. pow với số rất lớn: giới hạn CPU và bộ nhớ

Có thể tạo giá trị **10^1.000.000** bằng BigInteger.TEN.pow(1_000_000) nếu môi trường đủ tài nguyên, nhưng kết quả sẽ có hơn một triệu chữ số thập phân. Vấn đề không còn là overflow 64 bit mà là:

- Kích thước đối tượng, nhu cầu heap và bộ nhớ tạm.
- Thời gian tính lũy thừa và thời gian chuyển giá trị sang chuỗi.
- Khả năng bị người dùng lợi dụng số mũ cực lớn làm tiêu hao tài nguyên.
- Giới hạn nội bộ của BigInteger và cấu hình JVM.

Một ước lượng gần đúng cho a khác 0, |a|>1 và số mũ e:

\[
\operatorname{bitLength}(|a^e|)
\approx e\log_2|a|+1
\]

Đây là **ước lượng kích thước**, không phải công thức đếm bit chính xác cho mọi n. Nếu parse một số mũ do người dùng gửi, đặt hạn mức theo ngữ cảnh ứng dụng **trước khi gọi pow**.

Nếu chỉ cần **a^e modulo m**, đừng tạo toàn bộ a^e rồi mới lấy remainder: dùng **BigInteger.modPow(exponent, modulus)** theo hợp đồng của nó để tiết kiệm tài nguyên. modPow là một API khác, đặc biệt hữu ích trong thuật toán modulo và mật mã; nó **không phải** phép lũy thừa nguyên thuần của bài này.

## 11. sqrt của số khổng lồ: không dùng căn double làm đáp án

Với n có hàng trăm chữ số, **sqrt()** cho kết quả nguyên đúng theo giá trị n. Ví dụ:

~~~java
BigInteger n = BigInteger.TEN.pow(40);
BigInteger s = n.sqrt();
// s bằng 10^20 = 100000000000000000000
~~~

Khi n là số chính phương, sqrtAndRemainder trả phần dư 0. Với số không chính phương, phần dư cho biết khoảng cách từ n đến bình phương s², và luôn nhỏ hơn 2s+1.

Một bài toán phổ biến là kiểm tra n có phải số chính phương trước khi phân tích thêm. Dùng **n.signum() < 0** để từ chối miền không hợp lệ, rồi:

~~~java
boolean perfect = n.sqrtAndRemainder()[1].signum() == 0;
~~~

Đoạn này cần n là BigInteger **không âm** và đã được khai báo. Không so sánh kết quả Math.sqrt(n.doubleValue()) với số nguyên lớn, vì doubleValue() có thể mất chính xác hoặc thành Infinity.

## 12. Dấu và exception: bảng kiểm biên

| Tình huống | Kết quả/hành vi |
| --- | --- |
| **BigInteger.valueOf(12).pow(5)** | 248832 |
| **BigInteger.valueOf(-3).pow(4)** | 81 |
| **BigInteger.valueOf(-3).pow(5)** | −243 |
| **BigInteger.ZERO.pow(0)** | 1 |
| **a.pow(-1)** | **ArithmeticException** |
| **BigInteger.ZERO.sqrt()** | 0 |
| **BigInteger.valueOf(200).sqrt()** | 14 |
| **BigInteger.valueOf(200).sqrtAndRemainder()** | [14, 4] |
| **BigInteger.valueOf(-1).sqrt()** | **ArithmeticException** |
| **BigInteger.valueOf(-1).sqrtAndRemainder()** | **ArithmeticException** |
| **BigInteger.valueOf(-54).gcd(BigInteger.valueOf(24))** | 6 |
| **BigInteger.ZERO.gcd(BigInteger.ZERO)** | 0 |

Nếu đối số là tham chiếu **null**, không coi nó là số 0. Tùy phương thức và vị trí null, chương trình có thể ném NullPointerException; khi xử lý dữ liệu bên ngoài, hãy kiểm tra null riêng trước các phép tính.

## 13. Kiểm tra đúng bằng bất biến thay vì chỉ so output

Một số điều kiện QA hữu ích:

- **pow**: với e chẵn, a.pow(e) không âm; với e lẻ, dấu kết quả theo dấu a; a.pow(0)=1.
- **sqrt**: s=n.sqrt() thì s≥0, s²≤n và (s+1)²>n.
- **sqrtAndRemainder**: n=s²+r, r≥0, r<2s+1; n chính phương khi r=0.
- **gcd**: g≥0, nếu g>0 thì a.remainder(g)=0 và b.remainder(g)=0; gcd(a,b)=gcd(b,a)=gcd(|a|,|b|); gcd(a,0)=|a|.
- **Bất biến**: a, b, n vẫn giữ giá trị ban đầu sau khi gọi các phương thức.

Kiểm thử cả **0, 1, số âm (nếu API cho phép), số chính phương và số sát hai phía số chính phương**, cũng như các số có hàng trăm chữ số. Với phép kiểm thử property-based, giới hạn kích thước n/e để bộ kiểm thử không tiêu thụ quá nhiều tài nguyên.

**Đừng chỉ kiểm tra giá trị dạng chuỗi rồi bỏ qua loại exception.** Điều kiện exponent âm của pow và radicand âm của sqrt là một phần hợp đồng API cần test.

## 14. Khác biệt phiên bản và cú pháp API

Tất cả ví dụ chạy độc lập ở đây hướng đến **Java 17+**:

- **BigInteger.pow(int)** nhận số mũ nguyên kiểu int không âm.
- **BigInteger.sqrt()** và **sqrtAndRemainder()** có từ **Java 9**, không có trong các bản Java 8 cũ.
- **BigInteger.gcd(BigInteger)** trả số không âm.
- **BigInteger.TWO** xuất hiện từ Java 9; nếu cần tương thích Java 8, dùng BigInteger.valueOf(2).

BigInteger không hỗ trợ operator overloading của toán tử **^** để tính lũy thừa. Trong Java, **^** trên kiểu nguyên thủy là phép XOR theo bit, **không phải** phép nâng lũy thừa. Để tính 2^10, viết **BigInteger.valueOf(2).pow(10)**, không viết 2 ^ 10.

Tương tự, không có phương thức **BigInteger.sqrt(double precision)** hay **pow(BigInteger exponent)** phù hợp với những ý nghĩa đơn giản trên. Dùng đúng chữ ký trong tài liệu API và thiết kế lại khi bài toán có loại đầu vào khác.

## 15. Chương trình Java 17+ chạy độc lập

Tạo tệp **BigIntegerOperationsDemo.java** với mã sau:

~~~java
import java.math.BigInteger;
import java.util.Arrays;

public class BigIntegerOperationsDemo {
    static void show(String label, BigInteger value) {
        System.out.println(label + ": " + value);
    }

    public static void main(String[] args) {
        BigInteger twelve = BigInteger.valueOf(12);
        BigInteger negativeThree = BigInteger.valueOf(-3);

        show("12^5", twelve.pow(5));
        show("(-3)^5", negativeThree.pow(5));
        show("(-3)^4", negativeThree.pow(4));
        show("10^20", BigInteger.TEN.pow(20));
        show("0^0", BigInteger.ZERO.pow(0));

        BigInteger n = BigInteger.valueOf(200);
        BigInteger[] pair = n.sqrtAndRemainder();

        show("floor sqrt(200)", n.sqrt());
        System.out.println("sqrtAndRemainder(200): "
                + Arrays.toString(pair));
        show("reconstruct(200)", pair[0].pow(2).add(pair[1]));

        BigInteger square = BigInteger.valueOf(123456789).pow(2);
        show("perfect-square root", square.sqrt());
        System.out.println("sqrtAndRemainder(81): "
                + Arrays.toString(
                        BigInteger.valueOf(81).sqrtAndRemainder()));

        show("gcd(-54, 24)",
                BigInteger.valueOf(-54).gcd(BigInteger.valueOf(24)));
        show("gcd(0, -24)",
                BigInteger.ZERO.gcd(BigInteger.valueOf(-24)));
        show("gcd(0, 0)",
                BigInteger.ZERO.gcd(BigInteger.ZERO));
        show("gcd(101, 24)",
                BigInteger.valueOf(101).gcd(BigInteger.valueOf(24)));

        show("sqrt(10^40)", BigInteger.TEN.pow(40).sqrt());

        try {
            negativeThree.pow(-1);
        } catch (ArithmeticException e) {
            System.out.println("negative exponent: ArithmeticException");
        }

        try {
            BigInteger.valueOf(-1).sqrt();
        } catch (ArithmeticException e) {
            System.out.println(
                    "negative square root: ArithmeticException");
        }
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 BigIntegerOperationsDemo.java
java BigIntegerOperationsDemo
~~~

Stdout:

~~~text
12^5: 248832
(-3)^5: -243
(-3)^4: 81
10^20: 100000000000000000000
0^0: 1
floor sqrt(200): 14
sqrtAndRemainder(200): [14, 4]
reconstruct(200): 200
perfect-square root: 123456789
sqrtAndRemainder(81): [9, 0]
gcd(-54, 24): 6
gcd(0, -24): 24
gcd(0, 0): 0
gcd(101, 24): 1
sqrt(10^40): 100000000000000000000
negative exponent: ArithmeticException
negative square root: ArithmeticException
~~~

### Phân tích kết quả chương trình

1. **12^5** được tính là 248832. Trong Java, ký hiệu **^** ở phần tên nhãn chỉ là văn bản; để tính lũy thừa dùng phương thức pow.
2. **(-3)^5** âm, còn **(-3)^4** dương theo quy tắc số mũ lẻ/chẵn.
3. **10^20** là một số nguyên lớn được in đủ chữ số nhờ BigInteger, không có dạng ký pháp khoa học của double.
4. **0^0** trả 1 đúng quy ước API BigInteger.
5. **sqrt(200)** trả 14, còn **sqrtAndRemainder** trả [14,4], vì 14²=196 và 200−196=4.
6. Dòng reconstruct(200) kiểm tra bất biến **n=s²+r**.
7. Bình phương của 123456789 là số chính phương; sqrt lấy lại chính xác 123456789.
8. **sqrtAndRemainder(81)** có r=0 nên 81 là số chính phương.
9. gcd xử lý dấu âm, zero và cặp coprime đúng quy tắc.
10. Với số lớn 10^40, sqrt trả chính xác 10^20.
11. pow với exponent âm và sqrt với n âm đều ném **ArithmeticException**; chương trình bắt để minh họa mà không dừng sớm.

Những giá trị trên được kiểm tra bằng API BigInteger trên JDK. Các method sử dụng đều có trong Java 17.

## 16. Những lỗi phổ biến và cách tránh

| Lỗi | Tại sao sai | Hướng đúng |
| --- | --- | --- |
| Dùng toán tử **^** để tính số mũ | Trong Java ^ là XOR, không phải phép lũy thừa | Dùng **pow(int)** |
| Gọi **pow(-1)** để tính nghịch đảo | Số mũ âm không được chấp nhận | Biểu diễn phân số hoặc số thập phân theo bài toán |
| Chuyển n lớn sang double rồi Math.sqrt | Có thể mất độ chính xác/Infinity | Dùng **BigInteger.sqrt()** |
| Nghĩ sqrt(200) sẽ là 14.142... | API trả căn **nguyên** | Dùng BigDecimal.sqrt nếu thật sự cần căn thập phân và quy tắc precision |
| Nghĩ sqrt sẽ làm tròn gần nhất | sqrt lấy floor, không phải round | Kiểm tra s²≤n<(s+1)² |
| Cho rằng remainder là n % s | sqrtAndRemainder trả **n−s²** | Xác định đúng bất biến |
| Lấy phần tử thứ nhất của mảng làm remainder | Kết quả [0] là căn, [1] là dư | Gán rõ biến root/remainder |
| Kiểm tra n chính phương chỉ bằng Math.sqrt(n.doubleValue()) | Sai ở số lớn gần bình phương | Dùng sqrtAndRemainder()[1] == 0 |
| Gọi sqrt cho số âm mà không có chính sách | ArithmeticException | Kiểm tra signum trước |
| Nghĩ gcd(-54,24) = -6 | API trả số không âm | Kết quả =6 |
| Nghĩ gcd(0,0) ném exception | API định nghĩa kết quả 0 | Test trường hợp zero |
| Dùng gcd trước khi kiểm tra mẫu phân số bằng 0 | 0/0 vẫn không hợp lệ | Validate denominator != 0 |
| Nhân a*b rồi chia gcd để tính lcm với long | Có thể overflow long | BigInteger và chia gcd trước khi nhân |
| Bỏ qua kết quả **x.pow(e)** | BigInteger immutable | Gán đối tượng trả về |
| Chạy pow với exponent cực lớn từ input | Tốn nhiều bộ nhớ/CPU | Giới hạn exponent và kích cỡ output |
| Dùng BigInteger.pow cho a^e mod m | Tạo số trung gian lớn không cần thiết | Cân nhắc **modPow** |
| Dùng hằng BigInteger.TWO trên Java 8 | TWO có từ Java 9 | Java 17 hỗ trợ; Java 8 dùng valueOf(2) |

## 17. Bài tập thực hành

**Bài 1.** Tính 7^4 bằng BigInteger. Phương thức nào cần gọi?

*Đáp án:* **BigInteger.valueOf(7).pow(4)**, kết quả **2401**.

**Bài 2.** Giá trị **BigInteger.ZERO.pow(0)** là bao nhiêu? **BigInteger.TEN.pow(-1)** thì sao?

*Đáp án:* Trường hợp đầu trả **BigInteger.ONE (1)**; trường hợp sau ném **ArithmeticException** vì exponent âm.

**Bài 3.** Cho n=99, sqrtAndRemainder trả gì?

*Đáp án:* √99 nằm giữa 9 và 10, s=9, r=99−81=18, nên trả **[9,18]**. Phần dư này không phải 99%9.

**Bài 4.** Cho n=81, làm thế nào biết n chính phương mà không dùng double?

*Đáp án:* **n.sqrtAndRemainder()[1].signum() == 0** (với n là BigInteger không âm) hoặc so **n.sqrt().pow(2).equals(n)**.

**Bài 5.** gcd(-54, 24) và gcd(0,0) trả bao nhiêu?

*Đáp án:* **6** và **0** theo hợp đồng BigInteger.gcd.

**Bài 6.** Vì sao không lấy BigInteger.valueOf(101).gcd(BigInteger.valueOf(24)) bằng 101? Kết quả là gì?

*Đáp án:* 101 và 24 không có ước chung dương nào ngoài 1, nên gcd bằng **1**.

**Bài 7.** Rút gọn phân số 84/30 bằng gcd được bao nhiêu?

*Đáp án:* gcd(84,30)=6; tử mới 84/6=14, mẫu mới 30/6=5, tức **14/5**.

**Bài 8.** LCM của 84 và 30 bằng bao nhiêu? Nên tính theo thứ tự nào?

*Đáp án:* **420**. Lấy **84/gcd(84,30)=14**, sau đó nhân 30. Trong mã BigInteger nên xử lý đầu vào zero và dấu.

**Bài 9.** Vì sao cần giới hạn pow khi nhận cơ số và số mũ từ request HTTP, dù dùng BigInteger?

*Đáp án:* Kết quả có thể dài hàng triệu hoặc hàng tỷ bit, gây áp lực bộ nhớ/CPU và thậm chí vượt giới hạn triển khai. BigInteger không có nghĩa tài nguyên vô hạn.

**Bài 10.** Với n=200, chứng minh phần dư từ sqrtAndRemainder nằm trong khoảng cho phép.

*Đáp án:* s=14, r=4. **0≤4<2×14+1=29**, và **14²+4=200**.

## 18. Tài liệu tham khảo chính thức

- [BigInteger Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): chữ ký và hợp đồng **pow(int)**, **sqrt()**, **sqrtAndRemainder()**, **gcd(BigInteger)**, cùng những phương thức hỗ trợ.
- [BigDecimal Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): cân nhắc khi cần kết quả căn ở dạng thập phân thay vì căn nguyên; **sqrt(MathContext)** yêu cầu độ chính xác và quy tắc làm tròn.
- [Java SE 17 Math](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): đối chiếu **Math.sqrt(double)** với căn nguyên BigInteger và các giới hạn double.
- [Java Language Specification SE 17 — Integer Types and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.1): các giới hạn int/long và phép tính nguyên thủy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-008">mod, modPow, modInverse và các điều kiện đầu vào</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger: mod, modPow, modInverse và các điều kiện đầu vào

## 1. Học đúng ba phép toán — không chỉ nhớ tên method

Trong Java, `java.math.BigInteger` hỗ trợ số nguyên có độ chính xác tùy ý và ba phép toán modulo rất quan trọng:

```java
BigInteger r = a.mod(m);
BigInteger p = a.modPow(exponent, m);
BigInteger inverse = a.modInverse(m);
```

Chúng phục vụ các bài toán khác nhau:

- **`a.mod(m)`**: trả **phần dư modulo không âm** của `a` theo `m`.
- **`a.modPow(e,m)`**: trả `a^e mod m`, được triển khai cho lũy thừa modulo lớn **mà không cần tạo toàn bộ `a^e`**.
- **`a.modInverse(m)`**: tìm `x` sao cho `a*x ≡ 1 (mod m)`, **nếu nghịch đảo tồn tại**.

Điểm cốt lõi của bài **Java Core — Numbers / BigInteger** là *điều kiện đầu vào*. Cả ba đòi **modulus `m > 0`**. `modInverse` còn đòi `gcd(a,m) = 1`; `modPow` có **số mũ âm được phép** trong Java, nhưng khi ấy cũng đòi cơ số khả nghịch modulo `m`.

Ví dụ:

```java
BigInteger a = BigInteger.valueOf(-13);
BigInteger m = BigInteger.valueOf(5);

System.out.println(a.remainder(m)); // -3
System.out.println(a.mod(m));       // 2
```

`remainder` có thể âm; `mod` với modulus dương luôn trả một giá trị trong `[0,m−1]`. Đây là khác biệt đáng nhớ trong mọi thuật toán chu kỳ, hash, đồng dư hay mật mã mô-đun.

**Mục tiêu:** đọc đúng hợp đồng Java 17, biết tự kiểm tra precondition, dự đoán kết quả/ngoại lệ cho cả giá trị âm/0/1, hiểu nghịch đảo và số mũ âm, dùng được các ví dụ Java 17+ với đầu ra kiểm chứng được.

## 2. Chuẩn bị: tạo và so sánh BigInteger

`BigInteger` là **class immutable**, không phải primitive. Các phép toán trả một giá trị `BigInteger` mới hoặc một đối tượng có thể tái sử dụng; chúng **không thay đổi receiver**.

```java
import java.math.BigInteger;

BigInteger a = new BigInteger("123456789012345678901234567890");
BigInteger b = BigInteger.valueOf(17);
BigInteger c = a.add(b);

System.out.println(a); // a không đổi
```

Mảnh code cần đặt trong class/method nếu muốn chạy. Với số nằm trong miền `long`, `BigInteger.valueOf(long)` thuận tiện; với số rất dài, dùng `new BigInteger(String)` để parse từ decimal chính xác. Không truyền một literal `long` quá lớn vượt `Long.MAX_VALUE` rồi hy vọng `BigInteger.valueOf` sửa được: lỗi đã xảy ra ở **literal hoặc phép tính primitive** trước khi chuyển.

Các hằng số chuẩn:

```java
BigInteger.ZERO
BigInteger.ONE
BigInteger.TWO   // có từ Java 9
BigInteger.TEN
```

Các cách kiểm tra:

- `m.signum() > 0`: `m` là số dương.
- `m.compareTo(BigInteger.ZERO) > 0`: một cách khác kiểm tra `m` dương.
- `a.equals(b)`: so **giá trị** hai `BigInteger`.
- `a.gcd(m).equals(BigInteger.ONE)`: `a` và `m` **nguyên tố cùng nhau**.

**Không dùng `a == b` để so giá trị hai object `BigInteger`.** `==` giữa hai reference kiểm tra chúng có cùng object hay không, và sẽ không đáp ứng yêu cầu equality cho các giá trị được tạo độc lập.

### 2.1. Chữ ký các method

```text
BigInteger mod(BigInteger m)
BigInteger modPow(BigInteger exponent, BigInteger m)
BigInteger modInverse(BigInteger m)

BigInteger remainder(BigInteger val)
BigInteger pow(int exponent)
BigInteger gcd(BigInteger val)
```

Các dòng trên là **chữ ký minh họa**, không phải khai báo source Java độc lập. Lưu ý `modPow` nhận số mũ dưới dạng **`BigInteger`**, còn `pow` nhận **`int`** và tính lũy thừa nguyên *không* lấy modulo. Chọn nhầm API có thể dẫn đến chi phí bộ nhớ rất lớn hoặc ngoại lệ khi số mũ âm.

## 3. Đồng dư và lớp phần dư

Hai số nguyên `a` và `b` được gọi là **đồng dư modulo m** nếu hiệu `a−b` chia hết cho `m`:

```text
a ≡ b (mod m)
⇔ tồn tại k nguyên sao cho a - b = k*m.
```

Ví dụ:

```text
-13 ≡ 2 (mod 5), vì -13 - 2 = -15 = -3*5.
17 ≡ 2 (mod 5), vì 17 - 2 = 15 = 3*5.
```

Với `m > 0`, ta chọn một **đại diện chuẩn** trong khoảng `0≤r<m`. Phương thức `BigInteger.mod(m)` trả đại diện chuẩn đó, cho dù `a` dương, âm hay bằng 0.

| `a` | `m` | `a.mod(m)` | Ý nghĩa |
| ---: | ---: | ---: | --- |
| 13 | 5 | 3 | 13 = 2×5+3 |
| −13 | 5 | 2 | −13 = −3×5+2 |
| 0 | 5 | 0 | Zero thuộc lớp 0 |
| 10 | 5 | 0 | Chia hết |
| 5 | 5 | 0 | Đại diện của m là 0 |
| 5 | 1 | 0 | Chỉ có một lớp modulo 1 |

Hãy phân biệt **`a.mod(m)`** (tính kết quả) với **`a.equals(b)`** (so giá trị object). Một cặp số có thể không bằng nhau theo `equals` nhưng cùng kết quả modulo.

### 3.1. Vì sao modulus phải dương?

`BigInteger.mod(m)` định nghĩa đầu ra trong `0..m−1`. Nếu `m=0` thì khái niệm lớp phần dư theo modulus 0 không phù hợp hợp đồng API. Nếu `m<0`, khoảng đại diện `[0,m−1]` không có ý nghĩa ở dạng đã quy định.

Vì vậy, **`mod` ném `ArithmeticException` khi `m≤0`**. Không được suy rằng `mod(-5)` “tự lấy trị tuyệt đối” thành `mod(5)`. Nếu một domain thực sự có cách hiểu modulus âm, bạn cần tự thiết kế chính sách chuẩn hóa `m.abs()` *trước* khi gọi và giải thích rõ ý nghĩa; đừng âm thầm đổi dấu dữ liệu đầu vào.

## 4. `remainder` và `mod`: nguồn gốc nhiều lỗi âm

`BigInteger.remainder(m)` mô phỏng phép `%` số nguyên Java: thương được cắt về 0 và phần dư có thể **cùng dấu với số bị chia** (hoặc bằng 0). `BigInteger.mod(m)` lại luôn trả kết quả **không âm** khi `m>0`.

Ví dụ:

```java
BigInteger a = BigInteger.valueOf(-13);
BigInteger five = BigInteger.valueOf(5);

System.out.println(a.remainder(five)); // -3
System.out.println(a.mod(five));       // 2
```

Hai kết quả vẫn đại diện cùng lớp đồng dư vì `−3 ≡ 2 (mod 5)`. Nhưng chúng **không cùng giá trị nguyên** và có thể cho hệ quả khác trong lập trình.

| Biểu thức | Kết quả | Ghi chú |
| --- | ---: | --- |
| `BigInteger.valueOf(-13).remainder(BigInteger.valueOf(5))` | −3 | Dấu âm từ số bị chia |
| `BigInteger.valueOf(-13).mod(BigInteger.valueOf(5))` | 2 | Chuẩn hóa không âm |
| `BigInteger.valueOf(13).remainder(BigInteger.valueOf(-5))` | 3 | `remainder` cho phép divisor âm khác 0 |
| `BigInteger.valueOf(13).mod(BigInteger.valueOf(-5))` | Ném `ArithmeticException` | `mod` từ chối modulus âm |
| `BigInteger.valueOf(13).remainder(BigInteger.ZERO)` | Ném `ArithmeticException` | Chia cho zero |

**Điều kiện:** `remainder` chỉ cấm divisor bằng zero; `mod` cấm modulus **không dương**. Không tự suy “`remainder` và `mod` là hai tên gọi cho cùng method” chỉ từ một vài ví dụ có số dương.

### 4.1. Chuẩn hóa phần dư thủ công

Nếu đã có `r = a.remainder(m)` với `m>0`, có thể biến thành đại diện chuẩn:

```java
BigInteger r = a.remainder(m);
if (r.signum() < 0) {
    r = r.add(m);
}
```

Với modulus dương, `remainder` có độ lớn nhỏ hơn modulus, nên chỉ cần cộng `m` **một lần** khi r âm. Nhưng **cách trực tiếp và rõ nhất** vẫn là `a.mod(m)`.

Công thức đôi khi gặp ở các ngôn ngữ khác là `((a % m) + m) % m`. Nó có thể giúp chuẩn hóa phần dư âm, nhưng với `BigInteger` nên dùng `mod` để diễn đạt đúng chủ đích và tránh nhầm với `remainder`.

## 5. Chương trình Java A — phân biệt phần dư và modulo

Tạo file **`ModVsRemainder.java`**:

```java
import java.math.BigInteger;

public class ModVsRemainder {
    public static void main(String[] args) {
        BigInteger minus13 = BigInteger.valueOf(-13);
        BigInteger five = BigInteger.valueOf(5);

        System.out.println(minus13.remainder(five));
        System.out.println(minus13.mod(five));

        System.out.println(BigInteger.valueOf(13).mod(five));
        System.out.println(BigInteger.ZERO.mod(five));
        System.out.println(BigInteger.valueOf(10).mod(five));
        System.out.println(BigInteger.valueOf(99).mod(BigInteger.ONE));

        System.out.println(BigInteger.valueOf(13)
                .remainder(BigInteger.valueOf(-5)));
    }
}
```

Chạy với JDK 17+:

```bash
javac --release 17 ModVsRemainder.java
java ModVsRemainder
```

**Stdout chính xác:**

```text
-3
2
3
0
0
0
3
```

Sau khi chạy, hãy kiểm tra từng kết quả có thỏa `0≤a.mod(m)<m` với `m>0`. `mod(BigInteger.ONE)` luôn trả 0, bất kể receiver là giá trị nguyên nào.

## 6. `modPow(exponent,m)` — lũy thừa modulo

`a.modPow(e,m)` tính phần dư chuẩn của `a^e` theo `m`. Với `e≥0`, ý nghĩa toán học:

```text
result = a^e mod m,  m > 0, e >= 0.
```

Đầu ra nằm trong khoảng `[0,m−1]`. Khác `pow(int)`, phương thức này **không cần tạo toàn bộ số a^e trước rồi mới chia lấy dư**. Đây là yếu tố sống còn khi số mũ lớn.

Ví dụ kinh điển:

```text
4^13 mod 497 = 445.
```

Java:

```java
BigInteger result = BigInteger.valueOf(4)
        .modPow(BigInteger.valueOf(13),
                BigInteger.valueOf(497));

System.out.println(result); // 445
```

Kết quả `445` luôn không âm và nhỏ hơn `497`. Cơ số `a` có thể âm: `(−2)^5 mod 13 = 7`, vì `−32 ≡ 7 (mod 13)`.

### 6.1. Lặp bình phương — trực giác thuật toán

Một phương pháp cơ bản để tính `a^e mod m` với `e≥0` là **binary exponentiation** (lũy thừa nhị phân/lặp bình phương):

1. Đặt `result = 1 mod m` và `base = a mod m`.
2. Với mỗi bit của `e` từ thấp lên cao, nếu bit lẻ đang xét bằng 1 thì `result = (result*base) mod m`.
3. Bình phương `base = base*base mod m` rồi chuyển `e` sang phải một bit.
4. Khi `e` bằng 0, trả `result`.

Phép nhân được giảm modulo sau mỗi bước nên các giá trị trung gian không tăng theo kích thước khổng lồ của `a^e`. Số vòng lặp tỷ lệ với **số bit của số mũ**, không phải giá trị số mũ. Chi phí thực tế còn phụ thuộc số bit của modulus, thuật toán nhân/số học lớn và implementation JDK.

### 6.2. Code minh họa lặp bình phương (không thay API chuẩn)

```java
static BigInteger repeatedSquaring(
        BigInteger a, BigInteger e, BigInteger m) {
    if (m.signum() <= 0 || e.signum() < 0) {
        throw new IllegalArgumentException("Require m>0, e>=0");
    }

    BigInteger result = BigInteger.ONE.mod(m);
    BigInteger base = a.mod(m);
    BigInteger power = e;

    while (power.signum() > 0) {
        if (power.testBit(0)) {
            result = result.multiply(base).mod(m);
        }
        power = power.shiftRight(1);
        if (power.signum() > 0) {
            base = base.multiply(base).mod(m);
        }
    }
    return result;
}
```

Đây là **phương thức minh họa**, cần import `java.math.BigInteger` và đặt trong class; nó chỉ nhận `e≥0`. Điều kiện `m>0` phải được kiểm tra **trước** khi gọi `ONE.mod(m)`. Phép bit `testBit(0)` xác định bit thấp, tương đương kiểm tra số mũ lẻ.

Hàm minh họa giúp hiểu thuật toán; khi viết ứng dụng Java thông thường hãy ưu tiên `BigInteger.modPow` đã được tối ưu và kiểm thử rộng rãi. Không tự dùng bản minh họa cho mật mã nhạy cảm: thời gian thực thi có thể phụ thuộc các bit của số mũ và làm lộ thông tin nhạy cảm qua kênh phụ.

## 7. `modPow` và `pow` không giống nhau

| Tính năng | `a.pow(int e)` | `a.modPow(BigInteger e, BigInteger m)` |
| --- | --- | --- |
| Kết quả | `a^e` nguyên đầy đủ | Đại diện của `a^e` modulo `m` |
| Modulus | Không có | Bắt buộc `m>0` |
| Kiểu exponent | `int` | `BigInteger` |
| Exponent âm | **Không được**, ném `ArithmeticException` | **Được phép nếu `a` khả nghịch modulo `m`** |
| Kích thước kết quả | Có thể khổng lồ | Số bit không vượt quá kích thước đại diện dưới `m` |
| Ứng dụng | Lũy thừa nguyên thông thường | Đồng dư/lũy thừa modulo |

Ví dụ có thể gây nhầm:

```java
BigInteger a = BigInteger.valueOf(2);

System.out.println(a.pow(10)); // 1024
System.out.println(a.modPow(
        BigInteger.TEN, BigInteger.valueOf(17))); // 4
```

Vì `1024 mod 17 = 4`. Nhưng với số mũ dài hàng nghìn bit, tạo `a.pow(...)` trước khi modulo thường không khả thi, trong khi `modPow` xử lý theo kỹ thuật modular exponentiation.

**Lưu ý quan trọng:** `BigInteger.pow(-1)` ném `ArithmeticException`. **Không** suy từ đó rằng `BigInteger.modPow(BigInteger.valueOf(-1),m)` cũng bắt buộc ném; API `modPow` cho phép số mũ âm khi tồn tại nghịch đảo.

## 8. Điều kiện đầu vào của `modPow` — kể cả exponent âm

Hợp đồng Java 17:

- `m` phải **dương**; `m≤0` ⇒ `ArithmeticException`.
- `exponent` có thể **âm, zero hoặc dương**.
- Nếu `exponent<0`, cơ số phải **nguyên tố cùng nhau với modulus**, tương đương `gcd(a,m)=1`. Nếu không, `ArithmeticException`.
- Nếu `exponent≥0`, **không cần** `gcd(a,m)=1`. Chẳng hạn `6.modPow(2,15)` vẫn xác định.
- Các tham số object bằng `null` gây `NullPointerException` theo quy tắc chung của `BigInteger`; không xem null như zero.

| Cơ số | e | m | Kết quả/hành vi |
| ---: | ---: | ---: | --- |
| 4 | 13 | 497 | 445 |
| 2 | 10 | 17 | 4 |
| −2 | 5 | 13 | 7 |
| 3 | −1 | 11 | 4 |
| 3 | −2 | 11 | 5 |
| 6 | 2 | 15 | 6 |
| 6 | −1 | 15 | `ArithmeticException`: không có nghịch đảo |
| 2 | 10 | 0 | `ArithmeticException`: modulus không dương |
| 2 | 10 | −7 | `ArithmeticException`: modulus không dương |
| 2 | 0 | 7 | 1 |
| 0 | 0 | 7 | 1 |
| 0 | 5 | 7 | 0 |
| 99 | 0 | 1 | 0 |

Với hàng `6^2 mod 15`: `36 mod 15=6`, không đòi 6 và 15 nguyên tố cùng nhau vì số mũ không âm.

### 8.1. Số mũ zero và 0^0 trong BigInteger

`a.modPow(BigInteger.ZERO, m)` trả `1 mod m` khi `m>0`. Do đó:

- Nếu `m>1`, kết quả là `1`, kể cả cơ số `a=0`.
- Nếu `m=1`, kết quả là `0` vì mọi giá trị modulo 1 đều đại diện bằng 0.

Java xác định trường hợp `0.modPow(0,m)` bằng ngữ nghĩa API, không yêu cầu người học tự tranh luận định nghĩa `0^0` trong giải tích. Cũng tương tự, `BigInteger.ZERO.pow(0)` trả `BigInteger.ONE`.

### 8.2. Vì sao exponent âm là một trường hợp hợp lệ?

Với `e<0`, diễn giải lũy thừa modulo dựa trên **nghịch đảo nhân**:

```text
a^(-1) mod m = inverse(a) mod m
a^(-k) mod m = (inverse(a))^k mod m, k>0
```

Ví dụ `3 mod 11` có nghịch đảo 4 vì `3×4=12 ≡ 1 (mod 11)`. Vì vậy:

```text
3^(-1) mod 11 = 4
3^(-2) mod 11 = 4^2 mod 11 = 16 mod 11 = 5.
```

Trong Java:

```java
BigInteger a = BigInteger.valueOf(3);
BigInteger m = BigInteger.valueOf(11);

System.out.println(a.modPow(BigInteger.valueOf(-1), m)); // 4
System.out.println(a.modPow(BigInteger.valueOf(-2), m)); // 5
```

Không được hiểu `e<0` là tạo một số hữu tỉ kiểu `BigDecimal` bằng phép `1/a^k`. Đó là **nghịch đảo trong cấu trúc số dư modulo**; nếu không tồn tại nghịch đảo, biểu thức không có giá trị modulo và Java báo lỗi.

## 9. Chương trình Java B — modPow và điều kiện số mũ

Tạo file **`ModularExponentiation.java`**:

```java
import java.math.BigInteger;

public class ModularExponentiation {
    private static BigInteger bi(long n) {
        return BigInteger.valueOf(n);
    }

    public static void main(String[] args) {
        System.out.println(bi(4).modPow(bi(13), bi(497)));
        System.out.println(bi(2).modPow(bi(10), bi(17)));
        System.out.println(bi(-2).modPow(bi(5), bi(13)));

        System.out.println(bi(3).modPow(bi(-1), bi(11)));
        System.out.println(bi(3).modPow(bi(-2), bi(11)));

        System.out.println(bi(2).modPow(bi(0), bi(7)));
        System.out.println(bi(0).modPow(bi(0), bi(7)));
        System.out.println(bi(99).modPow(bi(0), bi(1)));
    }
}
```

Chạy:

```bash
javac --release 17 ModularExponentiation.java
java ModularExponentiation
```

**Stdout chính xác:**

```text
445
4
7
4
5
1
1
0
```

Dòng thứ tư và thứ năm xác nhận **số mũ âm được hỗ trợ** khi `gcd(a,m)=1`. Dòng cuối xác nhận modulus 1 có lớp phần dư duy nhất là 0.

## 10. `modInverse(m)` — nghịch đảo nhân trong modulo

Trong số học thực, người học có thể quen nghịch đảo của 3 là `1/3`. Trong modulo 11, nghịch đảo của 3 là **4**, bởi:

```text
3 × 4 = 12
12 mod 11 = 1
⇒ 3 × 4 ≡ 1 (mod 11).
```

Java hỗ trợ trực tiếp:

```java
BigInteger three = BigInteger.valueOf(3);
BigInteger eleven = BigInteger.valueOf(11);
BigInteger inv = three.modInverse(eleven);

System.out.println(inv); // 4
```

Đây là một **số nguyên đại diện chuẩn** trong khoảng `[0, m−1]`, không phải `double`/`BigDecimal` hoặc phân số thông thường. Nếu kết quả là `x`, bạn có thể kiểm chứng:

```java
System.out.println(
        three.multiply(inv).mod(eleven)); // 1
```

### 10.1. Điều kiện tồn tại nghịch đảo

Với modulus `m>1`, nghịch đảo của `a` tồn tại **khi và chỉ khi**:

```text
gcd(a,m) = 1.
```

Đây là định lý quan trọng nhất của bài. **Modulus không bắt buộc là số nguyên tố**. Chẳng hạn:

- `3` có nghịch đảo modulo `10` vì `gcd(3,10)=1`; kết quả `7` vì `3×7=21 ≡1 (mod 10)`.
- `3` có nghịch đảo modulo `11` vì `gcd(3,11)=1`; kết quả `4`.
- `6` **không** có nghịch đảo modulo `15` vì `gcd(6,15)=3`.
- `2` **không** có nghịch đảo modulo `4` vì `gcd(2,4)=2`.
- `0` **không** có nghịch đảo modulo `m>1` vì `gcd(0,m)=m≠1`.

`BigInteger.modInverse(m)` ném `ArithmeticException` nếu `m≤0` hoặc không tồn tại nghịch đảo. `m=1` là một trường hợp đại số **suy biến**, sẽ được giải thích riêng; tránh xây thuật toán yêu cầu “1 là phần tử khác 0” rồi giả định modulus 1 bình thường như modulus lớn.

### 10.2. Số âm vẫn có thể có nghịch đảo

Cơ số âm **không tự khiến modInverse lỗi**. Ví dụ `−3 ≡ 8 (mod 11)`. Nghịch đảo của `−3` modulo 11 là `7`, vì:

```text
(-3)*7 = -21
-21 ≡ 1 (mod 11).
```

Java:

```java
System.out.println(BigInteger.valueOf(-3)
        .modInverse(BigInteger.valueOf(11))); // 7
```

Do tính chất lớp đồng dư, `a.modInverse(m)` và `a.mod(m).modInverse(m)` cho cùng lớp nghiệm với `m>0` khi nghịch đảo tồn tại.

## 11. gcd và thuật toán Euclid mở rộng

`BigInteger.gcd(BigInteger)` trả **ước chung lớn nhất không âm** của hai số (với `gcd(0,0)=0` theo API). Khi muốn kiểm tra trước `modInverse`, dùng:

```java
BigInteger g = a.gcd(m);
boolean invertible = g.equals(BigInteger.ONE);
```

Ví dụ:

```java
System.out.println(BigInteger.valueOf(6)
        .gcd(BigInteger.valueOf(15))); // 3
```

### 11.1. Vì sao gcd=1 bảo đảm có nghịch đảo?

Định lý Bézout phát biểu: tồn tại hai số nguyên `x,y` sao cho:

```text
a*x + m*y = gcd(a,m).
```

Nếu `gcd(a,m)=1` thì:

```text
a*x + m*y = 1
⇒ a*x ≡ 1 (mod m).
```

Vậy `x` là một nghịch đảo modulo `m`. Thuật toán Euclid mở rộng tìm các hệ số `x,y` đó và là nền tảng toán học quen thuộc để tính nghịch đảo.

Ví dụ `a=3,m=11`:

```text
3*4 + 11*(-1) = 1
⇒ x = 4 là nghịch đảo của 3 modulo 11.
```

Ví dụ `a=6,m=15`: mọi tổ hợp `6x+15y` đều chia hết cho 3, nên không thể bằng 1. Không tồn tại `x` thỏa `6x ≡ 1 (mod 15)`.

### 11.2. Không dùng x/y thông thường thay cho modInverse

Nếu có `a*x ≡ b (mod m)`, đôi khi ta muốn “chia cho a” để tìm `x`. Điều đó chỉ hợp lệ theo cách dùng nghịch đảo khi `a` **khả nghịch modulo m**:

```text
x ≡ b * inverse(a) (mod m), nếu gcd(a,m)=1.
```

Nếu `gcd(a,m)>1`, phép chia modulo không đơn giản như số thực. Đồng dư `a*x ≡ b (mod m)` có thể **không có nghiệm** hoặc **nhiều nghiệm khác nhau**; cần thuật toán giải đồng dư tuyến tính riêng và kiểm tra `gcd(a,m)` có chia `b` hay không. Đừng cứ bắt exception rồi thay bằng phép chia `BigInteger.divide`.

## 12. Chương trình Java C — nghịch đảo, gcd và ngoại lệ

Tạo file **`ModularInverseDemo.java`**:

```java
import java.math.BigInteger;

public class ModularInverseDemo {
    private static BigInteger bi(long value) {
        return BigInteger.valueOf(value);
    }

    public static void main(String[] args) {
        BigInteger three = bi(3);
        BigInteger eleven = bi(11);

        BigInteger inverse = three.modInverse(eleven);
        System.out.println(inverse);
        System.out.println(three.multiply(inverse).mod(eleven));

        System.out.println(bi(-3).modInverse(eleven));
        System.out.println(bi(3).modInverse(bi(10)));

        System.out.println(bi(6).gcd(bi(15)));

        try {
            System.out.println(bi(6).modInverse(bi(15)));
        } catch (ArithmeticException ex) {
            System.out.println("No inverse for 6 mod 15");
        }

        try {
            System.out.println(bi(6).modPow(bi(-1), bi(15)));
        } catch (ArithmeticException ex) {
            System.out.println("Negative exponent needs inverse");
        }
    }
}
```

Chạy:

```bash
javac --release 17 ModularInverseDemo.java
java ModularInverseDemo
```

**Stdout chính xác:**

```text
4
1
7
7
3
No inverse for 6 mod 15
Negative exponent needs inverse
```

Cách xử lý exception ở đây để **biểu diễn điều kiện toán học** cho người học, không có nghĩa ứng dụng nên im lặng bỏ qua lỗi. Trong code nghiệp vụ, nên xác thực đầu vào, phân biệt “modulus sai” và “không có nghịch đảo”, và trả thông báo/trạng thái phù hợp.

## 13. Bảng điều kiện đầu vào và lỗi cho ba phương thức

| Phép toán | Modulus hợp lệ | Điều kiện khác | Lỗi đáng chú ý |
| --- | --- | --- | --- |
| `a.mod(m)` | `m > 0` | `a` bất kỳ số nguyên | `ArithmeticException` nếu `m≤0` |
| `a.modPow(e,m)` | `m > 0` | Với `e<0` phải có `gcd(a,m)=1` | `ArithmeticException` nếu `m≤0` hoặc mũ âm mà không khả nghịch |
| `a.modInverse(m)` | `m > 0` | Phải khả nghịch modulo `m`: `gcd(a,m)=1` | `ArithmeticException` nếu `m≤0` hoặc không có nghịch đảo |
| `a.remainder(m)` | `m ≠ 0` | `m` có thể âm | `ArithmeticException` nếu `m=0` |
| `a.pow(int e)` | Không có modulus | `e ≥ 0` | `ArithmeticException` nếu `e<0` |

**Null:** các method `BigInteger` khi được truyền tham số object null sẽ ném `NullPointerException` theo quy tắc chung; nếu receiver `a` null thì gọi bất kỳ method instance nào cũng ném NPE. Không dùng null làm “giá trị modulo zero”.

**Sức chứa tài nguyên:** dù `BigInteger` cung cấp độ chính xác tùy ý, implementation có giới hạn tài nguyên/bộ nhớ và hỗ trợ số bit tối đa. Các thao tác lớn có thể dùng nhiều CPU/bộ nhớ; `BigInteger` có thể ném `ArithmeticException` khi kết quả nằm ngoài miền được hỗ trợ, bên cạnh những điều kiện lỗi nêu trong bảng.

### 13.1. Không có điều kiện “m phải nguyên tố” cho modPow

`modPow(e,m)` chạy với **modulus hợp số** như `497` hoặc `15` miễn là `m>0` và các điều kiện về số mũ âm được đáp ứng. Nghịch đảo `modInverse` cũng **không yêu cầu modulus nguyên tố**, mà yêu cầu `gcd(a,m)=1`.

Một modulus nguyên tố `p` làm mọi lớp khác zero có nghịch đảo, nhưng đó là tính chất **trường hữu hạn** của `Z/pZ`, không phải điều kiện do Java ép vào API.

## 14. Những trường hợp biên: m=1, a=0, e=0, số âm

### 14.1. Modulus bằng 1

`m=1` **hợp lệ** vì `m>0`. Mọi số nguyên đều đồng dư 0 modulo 1:

```java
System.out.println(BigInteger.valueOf(99)
        .mod(BigInteger.ONE)); // 0

System.out.println(BigInteger.valueOf(5)
        .modPow(BigInteger.ZERO, BigInteger.ONE)); // 0
```

Trên JDK Java 17, `BigInteger.valueOf(2).modInverse(BigInteger.ONE)` trả `0`. Đây là trường hợp suy biến có duy nhất một lớp đồng dư `0=1` modulo 1 theo phép đồng dư, **không phải** nghịch đảo số học thông thường. Cùng logic đó, `a.modPow(e,ONE)` trả 0 cho các số mũ, kể cả những trường hợp mũ âm được JDK chấp nhận.

**Khuyến nghị:** nếu thiết kế nghiệp vụ dùng nghịch đảo, nhóm nhân hay mật mã yêu cầu modulus không suy biến, hãy đặt precondition mạnh hơn: **`m>1`**. Đây là ràng buộc **nghiệp vụ bổ sung**, không phải ràng buộc chung của `BigInteger.mod`.

### 14.2. Cơ số zero

- `0.mod(m)` bằng 0 cho mọi `m>0`.
- `0.modPow(e,m)` bằng 0 với `e>0` và `m>1`.
- `0.modPow(0,m)` bằng `1 mod m`.
- `0.modInverse(m)` **không tồn tại** nếu `m>1` vì `gcd(0,m)=m`.
- `0.modPow(e<0,m)` ném `ArithmeticException` nếu `m>1` vì thiếu nghịch đảo.

Không được suy từ “`0^0` gây tranh cãi trong toán học” rằng Java không có giá trị xác định cho `modPow(0,m)`: API trả kết quả quy định.

### 14.3. Cơ số âm

Cơ số âm vẫn được phép. `mod` chuẩn hóa phần dư thành không âm, `modPow` tính lớp đồng dư chính xác, `modInverse` hoạt động nếu `gcd=1`.

Ví dụ:

```java
System.out.println(BigInteger.valueOf(-13)
        .mod(BigInteger.valueOf(5))); // 2
System.out.println(BigInteger.valueOf(-2)
        .modPow(BigInteger.valueOf(5),
                BigInteger.valueOf(13))); // 7
```

**Không dùng phép `abs()` thay `mod()`**: `abs(-13)=13`, không phải kết quả đồng dư chuẩn của `-13 mod5`. Một phép biến đổi dấu không bảo toàn mọi lớp đồng dư.

### 14.4. Modulus bằng zero hoặc âm

`a.mod(BigInteger.ZERO)`, `a.modPow(e,BigInteger.ZERO)`, `a.modInverse(BigInteger.ZERO)` đều ném `ArithmeticException`. Tương tự với modulus âm. Trong ứng dụng nhập dữ liệu, kiểm tra `m.signum()>0` trước khi tính và cung cấp thông báo rõ.

## 15. Chương trình Java D — kiểm tra input có chủ đích

Tạo file **`CheckedModularArithmetic.java`**:

```java
import java.math.BigInteger;
import java.util.Objects;

public class CheckedModularArithmetic {
    static BigInteger modularPower(
            BigInteger base,
            BigInteger exponent,
            BigInteger modulus) {

        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(exponent, "exponent");
        Objects.requireNonNull(modulus, "modulus");

        if (modulus.signum() <= 0) {
            throw new IllegalArgumentException("modulus must be positive");
        }

        if (exponent.signum() < 0
                && !base.gcd(modulus).equals(BigInteger.ONE)) {
            throw new IllegalArgumentException(
                    "negative exponent requires inverse");
        }

        return base.modPow(exponent, modulus);
    }

    public static void main(String[] args) {
        System.out.println(modularPower(
                BigInteger.valueOf(2),
                BigInteger.TEN,
                BigInteger.valueOf(17)));

        System.out.println(modularPower(
                BigInteger.valueOf(3),
                BigInteger.valueOf(-1),
                BigInteger.valueOf(11)));

        try {
            modularPower(BigInteger.TWO,
                    BigInteger.TEN, BigInteger.ZERO);
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
        }

        try {
            modularPower(BigInteger.valueOf(6),
                    BigInteger.valueOf(-1),
                    BigInteger.valueOf(15));
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
```

Chạy:

```bash
javac --release 17 CheckedModularArithmetic.java
java CheckedModularArithmetic
```

**Stdout chính xác:**

```text
4
4
modulus must be positive
negative exponent requires inverse
```

Ứng dụng có thể chọn để `BigInteger` tự ném `ArithmeticException`, hoặc validate sớm để đưa thông báo nghiệp vụ. Việc validate `gcd` có thể **tăng chi phí** vì `modPow` với exponent âm cũng phải xử lý khả nghịch; không nhất thiết tính gcd trước trong mọi đường chạy. Nhưng validation tường minh hữu ích khi cần phân loại lỗi đầu vào.

Trong công việc liên quan dữ liệu **không đáng tin cậy**, còn phải giới hạn độ dài chuỗi `BigInteger`, bit length của cơ số/modulus và exponent, thời gian tính, bộ nhớ, cũng như chính sách logging. `m>0` không đồng nghĩa mọi phép tính có chi phí chấp nhận được.

## 16. Các định lý giúp kiểm chứng kết quả

Khi làm modulo, có những đẳng thức toán học hữu ích cho `m>0`:

```text
(a + b) mod m = ((a mod m) + (b mod m)) mod m

(a * b) mod m = ((a mod m) * (b mod m)) mod m

a^e mod m = (a mod m)^e mod m, với e >= 0

Nếu gcd(a,m)=1:
a * modInverse(a,m) mod m = 1 mod m.
```

Trong Java `BigInteger`:

```java
BigInteger left = a.multiply(b).mod(m);
BigInteger right = a.mod(m)
        .multiply(b.mod(m)).mod(m);

boolean equal = left.equals(right);
```

Đây là mảnh code cần khai báo `a`, `b`, `m` dương và khác null trước khi chạy. Nó là một **property test** tốt cho các phép modulo, miễn là đầu vào thuộc miền được phép.

### 16.1. Không “rút gọn” một nhân tử khi nó không khả nghịch

Từ:

```text
a*x ≡ a*y (mod m)
```

không phải lúc nào cũng suy ra `x≡y (mod m)`. Phép rút gọn `a` chỉ hợp lệ nếu `gcd(a,m)=1`.

Ví dụ:

```text
2*1 ≡ 2*3 (mod 4)  vì 2 ≡ 6 (mod 4),
nhưng 1 không đồng dư 3 (mod 4).
```

Đây là lý do “chia hai vế” hoặc gọi `modInverse` **không được** nếu `a` thiếu nghịch đảo modulo m.

### 16.2. Không đồng nhất kết quả modulo với bằng nhau số nguyên

`a.mod(m).equals(b.mod(m))` đúng khi `a` và `b` cùng lớp đồng dư modulo `m`; nhưng không có nghĩa `a.equals(b)`. Ví dụ `-13` và `2` khác giá trị số, nhưng đồng dư modulo 5.

Khi làm khóa dữ liệu theo lớp đồng dư, bạn có thể dùng `a.mod(m)` làm **canonical key**, miễn là modulus đã được xác định và nhất quán. Không tự thay đổi modulus cho một khóa đã chèn vào map/set mà không định nghĩa cách phân nhóm lại.

## 17. Chương trình Java E — ví dụ toán học minh họa RSA

Lũy thừa modulo và nghịch đảo modulo được dùng trong nhiều cấu trúc toán học, trong đó có hệ mật mã RSA. Ví dụ nhỏ sau **chỉ để học**, tuyệt đối không dùng cho bảo mật thực tế.

Ta chọn các số nguyên tố rất nhỏ:

```text
p = 61
q = 53
n = p*q = 3233
φ(n) = (p-1)*(q-1) = 3120
e = 17
d = e^(-1) mod 3120 = 2753
message = 65
cipher = message^e mod n = 2790
decoded = cipher^d mod n = 65
```

Tạo file **`ToyRsaMath.java`**:

```java
import java.math.BigInteger;

public class ToyRsaMath {
    public static void main(String[] args) {
        BigInteger p = BigInteger.valueOf(61);
        BigInteger q = BigInteger.valueOf(53);
        BigInteger n = p.multiply(q);

        BigInteger phi = p.subtract(BigInteger.ONE)
                .multiply(q.subtract(BigInteger.ONE));

        BigInteger e = BigInteger.valueOf(17);
        BigInteger d = e.modInverse(phi);

        BigInteger message = BigInteger.valueOf(65);
        BigInteger cipher = message.modPow(e, n);
        BigInteger decoded = cipher.modPow(d, n);

        System.out.println(n);
        System.out.println(phi);
        System.out.println(d);
        System.out.println(cipher);
        System.out.println(decoded);
    }
}
```

Chạy:

```bash
javac --release 17 ToyRsaMath.java
java ToyRsaMath
```

**Stdout chính xác:**

```text
3233
3120
2753
2790
65
```

Ý nghĩa: `modInverse` tìm `d` khi `gcd(e,φ(n))=1`; `modPow` thực hiện lũy thừa modulo mà không tính các số nguyên khổng lồ. Bộ số nhỏ cho phép bạn kiểm tra các công thức bằng tay.

**Cảnh báo về an toàn:** RSA thực tế yêu cầu sinh khóa đúng chuẩn, padding an toàn (ví dụ OAEP cho mã hóa và PSS cho chữ ký), randomness thích hợp, quản lý khóa, thiết kế tránh timing/power side channels và dùng API/provider mật mã đã được kiểm định. `BigInteger.modPow` là một phép toán số học, **không phải** API RSA hoàn chỉnh hay cam kết thời gian hằng. Không dùng ví dụ ở đây để mã hóa/chữ ký dữ liệu thật.

## 18. Sai lầm thường gặp

1. **“`mod` giống hệt `remainder`.”** Sai với số âm. `(-13).remainder(5)=-3`, `(-13).mod(5)=2`.
2. **“modulus âm được tự chuẩn hóa.”** Sai: `mod`, `modPow`, `modInverse` yêu cầu `m>0`, ngược lại ném `ArithmeticException`.
3. **“modulus phải là số nguyên tố.”** Sai: modulus hợp số vẫn được, miễn thỏa điều kiện từng phép.
4. **“`modInverse` luôn tồn tại khi `m>0`.”** Sai: cần `gcd(a,m)=1` (trường hợp m=1 suy biến).
5. **“`modPow` không cho phép exponent âm.”** Sai: đây là một khác biệt lớn với `pow(int)`.
6. **“Exponent âm trong modPow tạo phân số thông thường.”** Sai: đó là nghịch đảo modulo, chỉ có khi khả nghịch.
7. **“`a.pow(e).mod(m)` luôn là cách tốt nhất.”** Sai: `pow` có thể tạo số cực lớn; dùng `modPow`.
8. **“`a.pow(-1)` có nghĩa là nghịch đảo.”** Sai: `pow(int)` từ chối mũ âm.
9. **“`BigInteger.ZERO.modPow(BigInteger.ZERO,m)` phải lỗi.”** Sai: với `m>1`, Java trả 1 theo hợp đồng.
10. **“`m=1` bị cấm vì quá nhỏ.”** Sai theo API; `m=1` là modulus dương, kết quả modulo luôn 0.
11. **“Kết quả `modInverse` là decimal 1/a.”** Sai: nó là đại diện nguyên của nghịch đảo trong lớp đồng dư.
12. **“Nếu `a*x ≡ a*y` thì luôn có thể rút a.”** Sai nếu `gcd(a,m)>1`.
13. **“Dùng `==` để so hai `BigInteger` cùng số.”** Sai: nó so identity tham chiếu; dùng `equals`.
14. **“BigInteger là vô hạn tài nguyên, không cần giới hạn input.”** Sai: tài nguyên và miền implementation hữu hạn.
15. **“`BigInteger.modPow` đủ để viết RSA production.”** Sai: RSA còn yêu cầu padding, randomness, quản lý khóa và chống kênh phụ.
16. **“`BigInteger.mod` làm thay đổi receiver.”** Sai: đối tượng BigInteger bất biến.
17. **“Bắt `ArithmeticException` rồi trả 0 cho mọi trường hợp là an toàn.”** Sai: có thể che giấu modulus sai hoặc nghịch đảo không tồn tại; 0 là một kết quả hợp lệ cho nhiều phép.
18. **“`gcd(0,m)` luôn bằng 0.”** Sai: với `m>0`, `gcd(0,m)=m`.

## 19. Bài tập có đáp án

### Bài 1 — remainder và mod

Tính `BigInteger.valueOf(-13).remainder(BigInteger.valueOf(5))` và `.mod(BigInteger.valueOf(5))`.

**Đáp án:** `-3` và `2`. Cả hai cùng lớp đồng dư nhưng `mod` chọn đại diện không âm.

### Bài 2 — modulus âm

`BigInteger.valueOf(13).mod(BigInteger.valueOf(-5))` có trả 3 hoặc -2 không?

**Đáp án:** không. API ném `ArithmeticException` vì modulus không dương.

### Bài 3 — modulus bằng 1

`BigInteger.valueOf(-123456).mod(BigInteger.ONE)` bằng mấy?

**Đáp án:** `0`. Modulo 1 chỉ có một đại diện là 0.

### Bài 4 — modPow cơ bản

Tính `4^13 mod 497` bằng `BigInteger.modPow`.

**Đáp án:** `445`: `BigInteger.valueOf(4).modPow(BigInteger.valueOf(13), BigInteger.valueOf(497))`.

### Bài 5 — so sánh pow và modPow

`BigInteger.TWO.pow(10)` khác `BigInteger.TWO.modPow(BigInteger.TEN,BigInteger.valueOf(17))` thế nào?

**Đáp án:** `1024` và `4`. `pow` tính lũy thừa đầy đủ; `modPow` cho kết quả modulo.

### Bài 6 — số mũ âm

`BigInteger.valueOf(3).modPow(BigInteger.valueOf(-1), BigInteger.valueOf(11))` trả gì?

**Đáp án:** `4`, vì `3×4≡1 (mod11)`. Đây là nghịch đảo, không phải số thực 1/3.

### Bài 7 — số mũ âm thứ hai

`3^(-2) mod 11` có giá trị gì?

**Đáp án:** `5` vì nghịch đảo của 3 là 4, và `4² mod 11 =16 mod 11=5`.

### Bài 8 — điều kiện nghịch đảo

`6.modInverse(15)` thành công không? Vì sao?

**Đáp án:** không, ném `ArithmeticException` vì `gcd(6,15)=3≠1`.

### Bài 9 — modulus không nguyên tố

`3.modInverse(10)` bằng bao nhiêu?

**Đáp án:** `7` vì `3×7=21≡1 (mod10)`. Modulus hợp số 10 vẫn cho phép nghịch đảo của 3.

### Bài 10 — cơ số âm

`(-2)^5 mod 13` trả giá trị nào qua `modPow`?

**Đáp án:** `7`, vì `-32 ≡7 (mod13)`. Cơ số âm hợp lệ.

### Bài 11 — số mũ bằng zero

`0.modPow(0,7)` và `0.modPow(0,1)` trả gì?

**Đáp án:** `1` và `0`. Quy tắc là `1 mod m` khi exponent bằng zero.

### Bài 12 — điều kiện mũ âm

`6.modPow(-1,15)` có được phép không?

**Đáp án:** cú pháp method nhận exponent âm, nhưng phép tính ném `ArithmeticException` vì cơ số không khả nghịch modulo 15.

### Bài 13 — giải đồng dư

Giải `3*x ≡ 5 (mod 11)` bằng modInverse.

**Đáp án:** `inverse(3)=4`, nên `x≡5×4≡20≡9 (mod11)`. Có thể viết `bi(5).multiply(bi(3).modInverse(bi(11))).mod(bi(11))`, với `bi(long)` là helper tạo BigInteger.

### Bài 14 — kiểm tra đồng dư

`-13` và `2` có bằng nhau qua `BigInteger.equals` không? Có đồng dư mod 5 không?

**Đáp án:** không bằng nhau theo `equals`, nhưng `(-13).mod(5)=2.mod(5)=2` nên đồng dư.

### Bài 15 — so sánh hai giá trị khi làm khóa modulo

Khi cần nhóm số nguyên theo lớp đồng dư modulo `m>0`, có nên dùng `a` trực tiếp hay `a.mod(m)`?

**Đáp án:** dùng `a.mod(m)` làm canonical key nếu yêu cầu nhóm theo lớp dư. Cùng lớp dư có thể chứa nhiều số nguyên khác nhau.

### Bài 16 — kiểm tra modulus

Một hàm nghiệp vụ yêu cầu `m>1` và cơ số khả nghịch nếu e âm. Nên validate theo thứ tự nào?

**Đáp án:** kiểm tra non-null; kiểm tra `m.compareTo(BigInteger.ONE)>0`; nếu `e.signum()<0` thì kiểm tra `a.gcd(m).equals(ONE)`; sau đó gọi `a.modPow(e,m)`. `m>1` là ràng buộc nghiệp vụ mạnh hơn `m>0` của API.

### Bài 17 — khái niệm rút gọn

Tại sao `2*x ≡ 2*y (mod4)` không suy ra `x≡y (mod4)`?

**Đáp án:** `2` không khả nghịch modulo `4` vì `gcd(2,4)=2`. Ví dụ `x=1,y=3` phản chứng.

### Bài 18 — ứng dụng RSA

Trong ví dụ nhỏ, `e=17` và `φ(n)=3120`, tính `d=e.modInverse(phi)` và `65^e mod 3233`.

**Đáp án:** `d=2753` và ciphertext `2790`; `2790^2753 mod 3233=65`. Chỉ là bài toán minh họa, không dùng bảo mật thật.

## 20. Checklist trước khi sử dụng trong dự án

- [ ] Tôi biết `mod` trả đại diện **không âm** với modulus dương, khác `remainder` khi receiver âm.
- [ ] Tôi kiểm tra `m.signum()>0` trước `mod`/`modPow`/`modInverse`.
- [ ] Tôi biết `m=1` hợp lệ theo API nhưng có thể bị nghiệp vụ cấm.
- [ ] Tôi dùng `modPow` cho lũy thừa modulo lớn, thay vì `pow` rồi `mod`.
- [ ] Tôi phân biệt kiểu tham số exponent của `pow(int)` và `modPow(BigInteger,BigInteger)`.
- [ ] Tôi biết `modPow` **cho phép exponent âm** nếu cơ số có nghịch đảo modulo m.
- [ ] Tôi dùng `gcd(a,m)=1` để xác định tồn tại `modInverse`, không đòi modulus nguyên tố một cách không cần thiết.
- [ ] Tôi phân biệt nghịch đảo modulo với phép chia số nguyên hoặc phân số thông thường.
- [ ] Tôi không rút gọn nhân tử không khả nghịch trong đồng dư.
- [ ] Tôi biết cách nhận diện ngoại lệ `ArithmeticException` cho modulus không dương hoặc thiếu nghịch đảo.
- [ ] Tôi biết null gây `NullPointerException` và BigInteger immutable.
- [ ] Tôi xét trường hợp biên: 0, 1, số âm, exponent 0 và exponent âm.
- [ ] Tôi kiểm tra kết quả nghịch đảo bằng `a.multiply(inv).mod(m).equals(BigInteger.ONE.mod(m))`.
- [ ] Tôi tránh nhầm reference `==` với equality theo giá trị `equals`.
- [ ] Tôi giới hạn kích thước input nếu phép toán có thể bị lạm dụng làm tiêu hao CPU/RAM.
- [ ] Tôi không sử dụng các chương trình minh họa modular arithmetic làm hệ thống bảo mật thực tế.
- [ ] Tôi đã biên dịch/chạy năm chương trình mẫu và so sánh stdout.

## 21. Tài liệu chuẩn và nguồn đọc tiếp

- [Java SE 17 — BigInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html) — hợp đồng `mod`, `remainder`, `modPow`, `modInverse`, `pow`, `gcd` và ngoại lệ.
- [Java SE 17 — ArithmeticException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ArithmeticException.html) — ngoại lệ cho các phép toán số học không hợp lệ.
- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html) — lớp khác phục vụ số thập phân, **không phải** công cụ thay thế đồng dư nguyên.
- [JLS 17, `4.2.2 Integer Operations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.2) — phép chia và phần dư của integer primitives để so sánh với `BigInteger.remainder`.
- [Java SE 17 — Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html) — API mật mã mức cao; minh họa toán học `modPow` không thay nó.
- [Java SE 17 — Signature](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html) — API chữ ký số mức cao; không dùng raw modular exponentiation tự chế để ký.

**Kết luận:** ba API có phạm vi riêng rõ ràng: **`mod`** chuẩn hóa phần dư trong `[0,m−1]`, **`modPow`** tính lũy thừa modulo hiệu quả và hỗ trợ exponent âm khi khả nghịch, còn **`modInverse`** tìm nghiệm nghịch đảo chỉ khi `gcd(a,m)=1`. Phần lớn lỗi không đến từ cú pháp Java mà đến từ **quên kiểm tra modulus, tính khả nghịch và trường hợp biên**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-009">Phép bit trên số nguyên có độ dài mở rộng</a>

<details>
<summary>Xem chi tiết</summary>

# Phép bit trên số nguyên có độ dài mở rộng với BigInteger trong Java

`java.math.BigInteger` biểu diễn **số nguyên có độ chính xác tùy ý** (*arbitrary precision*), không bị cố định trong 32 bit của `int` hoặc 64 bit của `long`. Ngoài cộng, trừ, nhân, chia, lớp này cung cấp các phép **AND, OR, XOR, NOT, dịch bit và thao tác từng bit**. Các phép đó hữu ích khi làm việc với cờ trạng thái lớn, bitset, giao thức nhị phân, biểu diễn số nguyên rất lớn và thuật toán số học.

Nhưng có khác biệt quan trọng: phép bit của `BigInteger` hoạt động **như thể giá trị được lưu dưới dạng bù hai (*two's complement*) với các bit dấu ảo kéo dài vô hạn**. Đặc biệt, `not()` của một số dương có thể trả số âm, dịch phải số âm giữ dấu, và `BigInteger` **không có phép dịch phải không dấu `>>>`**. Nếu bài toán thật sự đòi một từ máy có độ rộng 8, 16, 32 hay 256 bit, lập trình viên phải tự áp **mặt nạ giới hạn độ rộng**.

Bài học thuộc mục **BigInteger** trong Java Core. Các ví dụ sử dụng Java 17+ và giải thích cả giá trị thập phân, bit nhị phân, bit dấu, kết quả chính xác và các bẫy thường gặp.

## 1. Khởi tạo BigInteger và tư duy bit không giới hạn độ rộng

`BigInteger` là class bất biến (*immutable*). Sử dụng constructor nhận chuỗi và radix hoặc `valueOf(long)`:

~~~java
import java.math.BigInteger;

BigInteger a = new BigInteger("101101", 2); // nhị phân 101101 = 45
BigInteger b = new BigInteger("011110", 2); // nhị phân 011110 = 30
BigInteger hex = new BigInteger("FF", 16);  // 255
BigInteger small = BigInteger.valueOf(45L);
~~~

Radix hợp lệ từ 2 đến 36, và chuỗi phải tuân thủ cú pháp của constructor. `new BigInteger("101101", 2)` là cú pháp đúng, còn `new BigInteger("0b101101", 2)` không tự chấp nhận tiền tố `0b`. `toString(2)` cho chuỗi biểu diễn theo cơ số 2, ví dụ `"101101"` đối với số dương 45.

Một số `BigInteger` dương có thể có hàng nghìn hoặc hàng triệu bit. Trong biểu thức toán học, phép bit vẫn được định nghĩa trên **hai số nguyên có dấu**, không phải trên hai mảng byte với kích thước cố định. Khi cần lưu thành gói tin hay thao tác cờ trong một độ rộng xác định, phải chọn rõ độ rộng trước.

**Điểm mấu chốt về âm:** số âm được xem như có vô hạn bit 1 ở đầu trong biểu diễn bù hai. Số `-1` được hình dung `...11111111`, còn `0` là `...00000000`. Dấu ba chấm biểu diễn **bit ảo** của mô hình toán học, không có nghĩa JVM thật sự cấp phát vô hạn bộ nhớ.

## 2. AND, OR, XOR và AND-NOT

Với `a = 45` và `b = 30`:

~~~text
a = 101101₂  (45)
b = 011110₂  (30)
--------------
& = 001100₂  (12)
| = 111111₂  (63)
^ = 110011₂  (51)
~~~

Các phương thức tương ứng:

| Thao tác | BigInteger API | Quy tắc từng bit | Kết quả ví dụ |
| --- | --- | --- | --- |
| AND | `a.and(b)` | 1 chỉ khi cả hai bit đều 1 | 12 |
| OR | `a.or(b)` | 1 khi ít nhất một bit là 1 | 63 |
| XOR | `a.xor(b)` | 1 khi hai bit khác nhau | 51 |
| AND NOT | `a.andNot(b)` | `a & ~b` | 33 |
| NOT | `a.not()` | Đảo tất cả bit theo mô hình bù hai | Phụ thuộc a; có thể âm |

~~~java
BigInteger a = new BigInteger("101101", 2);
BigInteger b = new BigInteger("011110", 2);

BigInteger intersection = a.and(b); // 12
BigInteger combined = a.or(b);      // 63
BigInteger differences = a.xor(b);  // 51
BigInteger removed = a.andNot(b);   // 33
~~~

Với các bit cờ, `and(mask)` thường dùng để lấy những bit được chọn; `or(mask)` bật một nhóm bit; `andNot(mask)` tắt một nhóm bit; `xor(mask)` lật trạng thái một nhóm bit. Các phép nhị phân này **không mutate `a` hoặc `b`**; chúng tạo `BigInteger` kết quả mới.

Nếu hai toán hạng khác độ dài hoặc có dấu khác nhau, BigInteger thực hiện phép logic theo **mô hình mở rộng dấu** của hai số. Không cần tự điền 0 hoặc 1 để hai số có cùng độ rộng trước khi gọi `and`, `or` và `xor`, trừ khi bài toán yêu cầu mô phỏng bitvector độ rộng cố định.

## 3. NOT là bẫy lớn nhất: `not()` không phải đảo N bit

Trong một bitvector **8 bit**, đảo bit của `00000101₂` được `11111010₂`, nghĩa là **250 nếu xem như unsigned**. Nhưng `BigInteger.valueOf(5).not()` trả **-6**, không phải 250:

~~~java
BigInteger five = BigInteger.valueOf(5);
System.out.println(five.not()); // -6
~~~

Vì mô hình là bù hai có dấu được mở rộng vô hạn, đẳng thức toán học đúng với mọi số nguyên `x` là:

~~~text
~x = -x - 1
~5 = -6
~-1 = 0
~~~

Nếu muốn **đảo trong 8 bit**, cần tạo mask `0xFF = 255` rồi chỉ giữ tám bit thấp:

~~~java
BigInteger mask8 =
        BigInteger.ONE.shiftLeft(8).subtract(BigInteger.ONE);
BigInteger inverted8 = five.not().and(mask8);
// inverted8 = 250 (nhị phân 11111010 trong 8 bit)
~~~

Tổng quát, với độ rộng `w > 0`, mặt nạ là `2^w - 1`:

~~~java
static BigInteger mask(int width) {
    if (width <= 0) {
        throw new IllegalArgumentException("width must be positive");
    }
    return BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
}
~~~

Phương thức trên **chỉ là đoạn minh họa**, cần đặt trong một class và giới hạn `width` hợp lý theo tài nguyên. Một ví dụ `width` rất lớn có thể tiêu thụ nhiều bộ nhớ; độ dài tùy ý không có nghĩa chi phí thực thi không giới hạn.

Với cùng mặt nạ, biểu thức `value.and(mask)` cũng là cách lấy phần dư **không âm** trong phạm vi `0..2^w-1` của biểu diễn w-bit: `BigInteger.valueOf(-1).and(mask8)` trả 255. Đây là **phép chiếu sang bitvector unsigned w-bit**, không làm số nguyên âm ban đầu biến mất.

## 4. Dịch trái và dịch phải: dấu được bảo toàn theo quy tắc số học

`shiftLeft(n)` tương tự `<<` và `shiftRight(n)` tương tự `>>` của số nguyên có dấu. Dịch trái n bit thường nhân giá trị với `2^n`; dịch phải n bit tương đương **lấy floor khi chia cho `2^n`**, rất quan trọng với số âm.

~~~java
BigInteger a = BigInteger.valueOf(45);
BigInteger left = a.shiftLeft(3);  // 360 = 45 * 8
BigInteger right = a.shiftRight(2); // 11 = floor(45 / 4)

BigInteger negative = BigInteger.valueOf(-3);
BigInteger arithmeticRight = negative.shiftRight(1); // -2
~~~

Với `-3 >> 1`, kết quả là `-2` vì quy tắc làm tròn xuống khi dịch số học, **không phải `-1`** theo phép chia `-3 / 2` của số nguyên Java, vốn chia lấy phần nguyên về phía 0.

So với primitive `int`/`long`, BigInteger có một số khác biệt:

- Khoảng dịch `n` không bị lấy modulo 32/64 như số bit của primitive. Ví dụ `BigInteger.ONE.shiftLeft(32)` là `4294967296`, không quay lại 1.
- BigInteger cho phép **khoảng dịch âm**: `shiftLeft(-2)` tương đương dịch phải 2 bit; `shiftRight(-2)` tương đương dịch trái 2 bit.
- Các bit dấu ảo được kéo dài khi dịch phải; `BigInteger.valueOf(-1).shiftRight(100)` vẫn là `-1`.
- Dịch trái số lớn có thể tạo giá trị/khối dữ liệu rất lớn; nên giới hạn số bit theo nghiệp vụ và tài nguyên.

Ví dụ:

~~~java
BigInteger a = BigInteger.valueOf(45);
a.shiftLeft(-2);  // 11 (cùng hướng với shiftRight(2))
a.shiftRight(-2); // 180 (cùng hướng với shiftLeft(2))
~~~

**Không có `BigInteger.unsignedShiftRight` hay toán tử `>>>` dành cho `BigInteger`.** Với số có bit dấu ảo 1 kéo dài vô hạn, việc “đẩy 0 vào từ phía trái” thiếu định nghĩa nếu không chọn trước một độ rộng từ máy. Để mô phỏng unsigned right shift ở độ rộng `w`, chuẩn hóa bằng mask rồi `shiftRight` trên giá trị không âm trong `0..2^w-1`.

## 5. Thao tác bit thứ n: `testBit`, `setBit`, `clearBit` và `flipBit`

Trong các API thao tác bit riêng lẻ, **bit 0 là bit thấp nhất (LSB)**. Ví dụ `45 = 101101₂` có các bit 0, 2, 3 và 5 bằng 1.

| Phương thức | Ý nghĩa |
| --- | --- |
| `testBit(n)` | Đọc trạng thái bit n (boolean) |
| `setBit(n)` | Trả về số mới với bit n được đặt thành 1 |
| `clearBit(n)` | Trả về số mới với bit n được đặt thành 0 |
| `flipBit(n)` | Trả về số mới với bit n bị đảo |
| `getLowestSetBit()` | Trả về vị trí bit 1 thấp nhất; trả `-1` đối với số 0 |

~~~java
BigInteger a = BigInteger.valueOf(45); // 101101₂
boolean bit3 = a.testBit(3);            // true
BigInteger withBit1 = a.setBit(1);      // 47
BigInteger withoutBit3 = a.clearBit(3); // 37
BigInteger flippedBit2 = a.flipBit(2);  // 41
~~~

`a` vẫn bằng 45 sau các lời gọi vì `BigInteger` bất biến. Nếu muốn lưu kết quả, hãy gán lại: `a = a.setBit(1);`. Chỉ số bit phải là số nguyên **không âm**; `testBit(-1)`, `setBit(-1)`, `clearBit(-1)` hoặc `flipBit(-1)` sẽ ném `ArithmeticException`.

**Với số âm**, test bit ở rất xa phía trái vẫn có thể trả true do sign extension:

~~~java
BigInteger minusOne = BigInteger.valueOf(-1);
boolean highBit = minusOne.testBit(100); // true
~~~

Không suy diễn rằng `minusOne` phải có ít nhất 101 bit vật lý đang được lưu. Các bit cao là một phần **mô hình bù hai vô hạn**.

## 6. `bitLength()`, `bitCount()` và `getLowestSetBit()`

Ba phương thức này **không thay thế nhau**:

- `bitLength()` trả số bit cần cho biểu diễn bù hai **tối thiểu, loại trừ bit dấu**. Với số dương, gần với độ dài biểu diễn nhị phân thông thường; ví dụ 256 cần 9 bit.
- `bitCount()` đếm số bit trong biểu diễn bù hai **khác bit dấu**. Với số không âm, đây là số bit 1; với số âm, đây là số bit 0 hữu hạn khác các bit 1 dấu kéo dài vô hạn.
- `getLowestSetBit()` trả chỉ số của bit 1 thấp nhất, hay `-1` nếu giá trị là 0.

~~~java
BigInteger a = BigInteger.valueOf(45); // 101101₂

a.bitLength(); // 6
a.bitCount();  // 4
BigInteger.valueOf(40).getLowestSetBit(); // 3
BigInteger.ZERO.getLowestSetBit();        // -1
~~~

Các trường hợp dễ nhầm:

| Giá trị | `bitLength()` | `bitCount()` | Vì sao? |
| --- | ---: | ---: | --- |
| `0` | 0 | 0 | Không có bit 1 |
| `45` | 6 | 4 | `101101₂` có 6 bit, bốn bit 1 |
| `256` | 9 | 1 | `1` theo sau bởi 8 bit 0 |
| `-1` | 0 | 0 | Dãy dấu ảo `...111111`; không có bit khác dấu |
| `-8` | 3 | 3 | Dãy bù hai `...11111000`; ba bit 0 thấp khác bit dấu |

Vì vậy **`bitLength(-1) == 0` không có nghĩa -1 là 0 hoặc không có bit 1**. Nếu thuật toán đòi độ dài bit của một số *unsigned* hoặc độ dài bit của một field trong giao thức, hãy chuẩn hóa về số không âm và áp dụng định nghĩa độ rộng của field đó.

## 7. Mô phỏng từ máy w-bit bằng mask: AND, NOT, shift và rotate

Khi đọc một word/bitfield với **độ rộng cố định**, đầu tiên tạo mask và chuẩn hóa:

~~~java
int width = 8;
BigInteger modulus = BigInteger.ONE.shiftLeft(width);
BigInteger mask = modulus.subtract(BigInteger.ONE);

BigInteger unsigned = BigInteger.valueOf(-1).and(mask); // 255
BigInteger inverted = BigInteger.valueOf(5).not().and(mask); // 250
BigInteger unsignedRight = unsigned.shiftRight(1); // 127
~~~

Với một đầu vào không âm đã giới hạn, `shiftRight` dịch 0 vào phần trái vì giá trị không có dấu âm. Việc **AND với mask trước khi dịch** có ý nghĩa quan trọng với các đầu vào âm.

Ví dụ **xoay trái (rotate-left) trong 8 bit** của `0x81`:

~~~java
BigInteger mask8 =
        BigInteger.ONE.shiftLeft(8).subtract(BigInteger.ONE);
BigInteger word = new BigInteger("10000001", 2); // 0x81 = 129

BigInteger rotated = word.shiftLeft(1).and(mask8)
        .or(word.shiftRight(7));
// rotated = 3 (binary 00000011)
~~~

Đây là phép xoay trái một bit **được cài trên bitvector 8-bit**, không phải phương thức rotate trực tiếp của `BigInteger`. Nếu muốn xoay một khoảng `n` bất kỳ, trước tiên chuẩn hóa `n` theo `width`, xử lý riêng độ rộng 0 không hợp lệ và mask lại kết quả. Khi dùng bitvector cho giao thức, cần ghi rõ **endianness khi chuyển sang byte**, bit numbering, signedness và width.

Một trường hợp bit-field khác là trích bốn bit ở vị trí 4–7:

~~~java
BigInteger packetWord = new BigInteger("AB3", 16);
BigInteger fourBits = packetWord.shiftRight(4)
        .and(BigInteger.valueOf(15));
// fourBits = 11 (0xB)
~~~

Nếu word chứa nhiều trường với offset và độ dài cố định, nên viết hàm trích field có kiểm tra offset/width, thay vì rải các con số mask khó hiểu khắp mã nguồn.

## 8. `toByteArray()` và signed/unsigned bytes

`BigInteger.toByteArray()` trả **mảng byte big-endian biểu diễn bù hai có dấu, có đủ bit dấu**. Vì vậy số dương có bit cao nhất của byte đầu bằng 1 có thể cần một byte `0x00` ở đầu để tránh bị giải thích thành số âm.

~~~java
BigInteger value = BigInteger.valueOf(255);
byte[] signedBytes = value.toByteArray();
// Mảng bytes: [0, -1] trong cách in Arrays.toString(...)
~~~

Ở Java, `byte` có dấu nên byte `0xFF` khi in kiểu `byte` trở thành `-1`. Hai cách dựng `BigInteger` từ cùng dãy byte `0xFF` mang ý nghĩa khác nhau:

~~~java
byte[] raw = {(byte) 0xFF};

BigInteger signed = new BigInteger(raw);    // -1
BigInteger unsigned = new BigInteger(1, raw); // 255
~~~

Constructor `new BigInteger(byte[])` nhận **bù hai có dấu**, còn constructor `new BigInteger(signum, magnitude)` nhận **dấu riêng và độ lớn magnitude**, với `signum = 1` cho số dương. Đừng mặc định cả hai cách nhận raw bytes tương đương.

`toByteArray()` không trả một mảng byte **unsigned có độ dài cố định**. Nếu giao thức đòi đúng 16 hoặc 32 byte, cần:

1. Kiểm tra giá trị có vừa miền unsigned/signed tương ứng không.
2. Xác định byte order (API này trả big-endian).
3. Kiểm tra và loại byte 0 dấu bổ sung khi **an toàn** và khi giao thức muốn unsigned magnitude.
4. Đệm thêm `0x00` về đúng chiều để đủ độ dài, hoặc chuyển sang two's complement width-bit theo đặc tả của giao thức.

Đừng tùy tiện loại một byte đầu chỉ vì bằng 0 nếu giao thức cần giữ độ dài hoặc chính sách signedness khác.

## 9. Ví dụ Java 17+ hoàn chỉnh và kết quả thực thi

Lưu file `BigIntegerBitsDemo.java`:

~~~java
import java.math.BigInteger;
import java.util.Arrays;

public class BigIntegerBitsDemo {
    public static void main(String[] args) {
        BigInteger a = new BigInteger("101101", 2);
        BigInteger b = new BigInteger("011110", 2);
        BigInteger mask8 =
                BigInteger.ONE.shiftLeft(8).subtract(BigInteger.ONE);

        System.out.println("a = " + a);
        System.out.println("b = " + b);
        System.out.println("a & b = " + a.and(b));
        System.out.println("a | b = " + a.or(b));
        System.out.println("a ^ b = " + a.xor(b));
        System.out.println("a andNot b = " + a.andNot(b));
        System.out.println("not 5 = "
                + BigInteger.valueOf(5).not());
        System.out.println("not 5 in 8 bits = "
                + BigInteger.valueOf(5).not().and(mask8));

        System.out.println("a << 3 = " + a.shiftLeft(3));
        System.out.println("a >> 2 = " + a.shiftRight(2));
        System.out.println("-3 >> 1 = "
                + BigInteger.valueOf(-3).shiftRight(1));
        System.out.println("-1 testBit(100) = "
                + BigInteger.valueOf(-1).testBit(100));
        System.out.println("-1 & 255 = "
                + BigInteger.valueOf(-1).and(mask8));

        System.out.println("a testBit(3) = " + a.testBit(3));
        System.out.println("a setBit(1) = " + a.setBit(1));
        System.out.println("a clearBit(3) = " + a.clearBit(3));
        System.out.println("a flipBit(2) = " + a.flipBit(2));

        System.out.println("bitLength(256) = "
                + BigInteger.valueOf(256).bitLength());
        System.out.println("bitLength(-1) = "
                + BigInteger.valueOf(-1).bitLength());
        System.out.println("bitCount(a) = " + a.bitCount());
        System.out.println("bitCount(-1) = "
                + BigInteger.valueOf(-1).bitCount());
        System.out.println("lowest set bit(40) = "
                + BigInteger.valueOf(40).getLowestSetBit());
        System.out.println("a binary = " + a.toString(2));

        byte[] one = {(byte) 0xFF};
        System.out.println("from signed byte = "
                + new BigInteger(one));
        System.out.println("from unsigned byte = "
                + new BigInteger(1, one));
        System.out.println("255 bytes = "
                + Arrays.toString(BigInteger.valueOf(255).toByteArray()));

        BigInteger x = new BigInteger("10000001", 2);
        System.out.println("rotate left 8-bit 0x81 = "
                + x.shiftLeft(1).and(mask8).or(x.shiftRight(7)));
        System.out.println("a unchanged = " + a);
    }
}
~~~

Lệnh biên dịch và chạy trên Java 17+:

~~~bash
javac BigIntegerBitsDemo.java
java BigIntegerBitsDemo
~~~

Stdout chính xác:

~~~text
a = 45
b = 30
a & b = 12
a | b = 63
a ^ b = 51
a andNot b = 33
not 5 = -6
not 5 in 8 bits = 250
a << 3 = 360
a >> 2 = 11
-3 >> 1 = -2
-1 testBit(100) = true
-1 & 255 = 255
a testBit(3) = true
a setBit(1) = 47
a clearBit(3) = 37
a flipBit(2) = 41
bitLength(256) = 9
bitLength(-1) = 0
bitCount(a) = 4
bitCount(-1) = 0
lowest set bit(40) = 3
a binary = 101101
from signed byte = -1
from unsigned byte = 255
255 bytes = [0, -1]
rotate left 8-bit 0x81 = 3
a unchanged = 45
~~~

Các dòng đầu kiểm tra chính xác AND/OR/XOR/AND-NOT. Các dòng `not`, `shiftRight`, `testBit(100)` cho thấy **mô hình bù hai vô hạn**, còn các dòng mask/rotate cho thấy cách trở lại thế giới **bitvector 8-bit**. Dòng cuối chứng minh `BigInteger` là immutable: các kết quả bitwise được tạo mới mà không thay đổi `a`.

## 10. So sánh với `int`, `long` và `BitSet`

| Đặc tính | `int`/`long` | `BigInteger` | `java.util.BitSet` |
| --- | --- | --- | --- |
| Độ rộng | Cố định 32/64 bit | Mở rộng theo giá trị | Bitset linh hoạt |
| Ngữ nghĩa dấu | Bù hai có dấu | Bù hai với sign extension ảo | Thường dùng tập bit không âm theo vị trí |
| Cú pháp toán tử | `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>` | Phương thức `and`, `or`, `xor`, `not`, `shiftLeft`, `shiftRight` | API set/clear/flip/get và phép tập hợp |
| Dịch phải không dấu | Có `>>>` | Không có trực tiếp | Không phải số nguyên signed |
| Giá trị sau thao tác | Primitive value | Object bất biến mới | `BitSet` thường sửa trạng thái bên trong |

**Chọn `BigInteger`** khi đối tượng chính là một **số nguyên lớn** và cần cả số học lẫn phép bit. **Chọn `BitSet`** khi đối tượng chính là **tập các vị trí boolean**, cần bật/tắt nhiều bit và muốn API cấu trúc dữ liệu; `BitSet` có ngữ nghĩa và mutability khác `BigInteger`. **Chọn `int`/`long`** khi độ rộng cố định và hành vi overflow/unsigned dịch được quy định rõ.

Không thay thế các loại này theo cảm giác “đều chứa bit”: hãy xem yêu cầu signedness, kích thước, tính bất biến, định dạng xuất và semantics của bit cao.

## 11. Bẫy về hiệu năng, bộ nhớ và an toàn

**Độ dài mở rộng vẫn có giới hạn thực tế.** Dịch `BigInteger.ONE.shiftLeft(1_000_000)` có thể tạo một số rất lớn và phải phân bổ bộ nhớ; việc nhận width/bit index tùy ý từ người dùng mà không kiểm tra có thể dẫn đến tiêu hao tài nguyên. Java có thể ném ngoại lệ cho giá trị/độ dài không được hỗ trợ hoặc không còn tài nguyên.

**Không có nghĩa mọi phép bit là thời gian hằng số.** Những phép trên BigInteger thường phụ thuộc kích thước giá trị/biểu diễn. Khi dùng BigInteger cho mật mã, phải cân nhắc cả yêu cầu **constant-time**, rò rỉ thời gian và thư viện mật mã chuyên dụng; không thể suy ra an toàn về side-channel chỉ từ việc số có độ chính xác tùy ý.

**Chuyển về primitive có thể mất bit.** `intValue()` và `longValue()` có thể làm mất các bit cao; nếu cần chắc chắn không mất thông tin thì dùng `intValueExact()`, `longValueExact()` phù hợp và xử lý `ArithmeticException` khi vượt miền.

**Không dùng `bitLength()` như một giới hạn bảo mật cho raw byte unsigned khi số âm vẫn được chấp nhận.** Hãy kiểm tra `signum() >= 0` và giá trị có nằm trong miền định dạng hay không. Với số unsigned w-bit, miền thường là `0 <= x < 2^w`; áp mask để diễn giải/trích bit khác với **từ chối** đầu vào vượt miền.

**Không tự dùng `xor` làm thuật toán mã hóa bảo mật.** XOR là phép bit cơ bản, không phải tự thân một giao thức mật mã an toàn. Phương pháp mã hóa/hashing/kiểm chữ ký cần thuật toán và thư viện được thiết kế cho mục đích đó.

## 12. Những sai lầm phổ biến

1. **Cho rằng `BigInteger` có đúng 64 bit vì tạo từ `long`.** Nó mang giá trị số và có thể mở rộng qua các phép toán sau đó.
2. **Kỳ vọng `BigInteger.valueOf(5).not()` trả 250 như NOT trên 8 bit.** `not()` có nghĩa `~5 = -6`; muốn 250 phải áp mask 8-bit.
3. **Coi số âm có một số lượng bit dấu hữu hạn do `bitLength()` báo.** Bit dấu ảo trong bù hai được sign-extend vô hạn.
4. **Gọi `shiftRight` là dịch phải không dấu.** Với số âm nó là dịch phải số học, giữ dấu; không có `>>>` trực tiếp trên BigInteger.
5. **Giả định shift distance của BigInteger bị lấy modulo 32/64.** Khác primitive, BigInteger không sử dụng cơ chế masking khoảng dịch ấy.
6. **Cho rằng `-3.shiftRight(1)` là -1 vì phép chia nguyên -3/2 cho -1.** Dịch phải âm dùng làm tròn xuống, ra -2.
7. **Giả sử `bitCount()` luôn là số bit 1 hữu hạn.** Với số âm, nó đếm bit khác bit dấu; `bitCount(-1) == 0`.
8. **Cho rằng `bitLength(-1) == 0` tức số không có bit 1.** -1 có các bit dấu ảo bằng 1; bitLength là định nghĩa khác.
9. **Quên gán kết quả `setBit`, `clearBit`, `flipBit`.** BigInteger bất biến, biến gốc không đổi.
10. **Dùng chỉ số bit âm hoặc độ rộng nhập không kiểm soát.** Chỉ số âm ném `ArithmeticException`; chỉ số cực lớn có thể tạo áp lực bộ nhớ.
11. **Hiểu `toByteArray()` là unsigned bytes cố định.** Nó trả signed two's-complement big-endian, có thể có byte dấu `0x00`.
12. **Nhầm `new BigInteger(bytes)` với `new BigInteger(1, bytes)`.** Với `0xFF`, hai cách cho -1 và 255.
13. **Dùng `toString(2)` để mong thấy N bit bù hai của số âm.** Chuỗi cơ số 2 của số âm có dấu trừ và trị tuyệt đối, không phải dãy bit w-bit cố định.
14. **Lấy mask và tưởng đầu vào vượt miền đã được xác thực.** Mask cắt bit cao; nếu dữ liệu vượt giới hạn phải báo lỗi thì hãy kiểm tra trước, không âm thầm loại thông tin.

## 13. Câu hỏi luyện tập

**1.** Vì sao `BigInteger.valueOf(5).not()` trả -6 thay vì 250? Làm sao lấy NOT của 5 trong 8 bit?

**Đáp án:** Theo bù hai vô hạn `~x = -x-1`. Dùng `BigInteger.valueOf(5).not().and(BigInteger.ONE.shiftLeft(8).subtract(BigInteger.ONE))` để lấy 250.

**2.** Tính `BigInteger.valueOf(45).and(BigInteger.valueOf(30))`, `or` và `xor`.

**Đáp án:** Lần lượt 12, 63 và 51; viết hai số ở nhị phân `101101` và `011110` để kiểm từng cột.

**3.** `BigInteger.valueOf(-3).shiftRight(1)` trả bao nhiêu, và vì sao khác `-3 / 2` trong Java?

**Đáp án:** -2, vì dịch phải số học tương đương lấy floor khi chia cho 2, trong khi chia số nguyên Java hướng về 0.

**4.** `BigInteger.valueOf(-1).testBit(100)` là true hay false? Điều đó có tạo một mảng 101 bit không?

**Đáp án:** True do các bit dấu ảo 1 kéo dài; không ngụ ý object vật lý có đúng 101 bit.

**5.** `BigInteger.valueOf(-1).bitCount()` và `bitLength()` đều cho bao nhiêu?

**Đáp án:** Đều 0 theo định nghĩa khác nhau của hai phương thức. Đây không phải số 0 về mặt giá trị.

**6.** Cùng mảng byte một phần tử `0xFF`, vì sao `new BigInteger(bytes)` và `new BigInteger(1, bytes)` khác nhau?

**Đáp án:** Constructor đầu đọc bù hai có dấu (-1), constructor sau đọc magnitude không âm với signum dương (255).

**7.** BigInteger có phép dịch phải không dấu trực tiếp như `>>>` không? Muốn thực hiện trên word 8-bit âm thì sao?

**Đáp án:** Không. Cần chọn width 8, áp mask `0xFF` để diễn giải giá trị thành 0..255, sau đó gọi `shiftRight` trên số không âm.

**8.** Khi nào phải dùng `intValueExact()` thay vì `intValue()`?

**Đáp án:** Khi việc mất các bit cao là lỗi, cần phát hiện và từ chối số BigInteger vượt miền `int` thay vì âm thầm cắt bớt.

## Tài liệu tham khảo

- [Oracle Java SE 21 — BigInteger API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html): ý nghĩa phép bit, sign extension, shift distance âm, `bitCount`, `bitLength` và `toByteArray`.
- [Java Language Specification `15.19 — Shift Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.19): quy tắc dịch bit primitive để đối chiếu.
- [Java Language Specification `15.22 — Bitwise and Logical Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.22).
- [Oracle Java SE 21 — BitSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/BitSet.html): so sánh ngữ nghĩa tập bit và mutability với số nguyên BigInteger.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-010">bitLength, bitCount, testBit, setBit và shift</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger: bitLength, bitCount, testBit, setBit và shift trong Java

`java.math.BigInteger` biểu diễn **số nguyên có dấu với độ chính xác tùy ý**, thay vì bị giới hạn ở 32 bit (`int`) hoặc 64 bit (`long`). Không chỉ cộng, trừ và nhân số lớn, lớp này còn hỗ trợ **quan sát và chỉnh sửa bit**.

Những phương thức cần nắm:

| Phương thức | Mục đích |
| --- | --- |
| `bitLength()` | Số bit trong biểu diễn bù hai tối thiểu, **không tính bit dấu** |
| `bitCount()` | Số bit **khác bit dấu** trong biểu diễn bù hai |
| `testBit(n)` | Kiểm tra bit ở chỉ số n có bằng 1 không |
| `setBit(n)` | Trả giá trị mới có bit n được đặt thành 1 |
| `clearBit(n)` | Trả giá trị mới có bit n bằng 0 |
| `flipBit(n)` | Trả giá trị mới với bit n bị đảo |
| `shiftLeft(n)` | Dịch trái; với n dương tương đương nhân \(2^n\) |
| `shiftRight(n)` | Dịch phải số học, **mở rộng bit dấu**; với n dương tương đương làm tròn xuống khi chia \(2^n\) |

**Điểm cốt lõi:** các phép toán bit của `BigInteger` sử dụng mô hình **bù hai có mở rộng dấu vô hạn** (*infinite sign extension*). Số âm có các bit cao được xem là 1, không phải 0. Vì thế `BigInteger.bitCount(-1)` bằng **0** chứ không bằng 32 hay 64; và `shiftRight` trên số âm có thể cho kết quả khác `divide`.

## 1. Khởi tạo BigInteger và tính bất biến

```java
import java.math.BigInteger;

BigInteger a = BigInteger.valueOf(45);
BigInteger fromBinary = new BigInteger("101101", 2); // 45
BigInteger huge = new BigInteger("123456789012345678901234567890");
BigInteger zero = BigInteger.ZERO;
BigInteger one = BigInteger.ONE;
```

`BigInteger` là **immutable**: các phép toán không sửa đối tượng đang có; chúng trả về đối tượng/giá trị kết quả.

```java
BigInteger x = BigInteger.valueOf(5); // 101₂
x.setBit(1); // kết quả 7 bị bỏ qua; x vẫn là 5
System.out.println(x); // 5

x = x.setBit(1);
System.out.println(x); // 7
```

Một cách viết như `x.shiftLeft(10);` rồi bỏ qua kết quả thường là bug. Với giá trị cần cập nhật, hãy dùng `x = x.shiftLeft(10);`.

“Độ chính xác tùy ý” không có nghĩa tài nguyên vô hạn: số rất lớn có thể không đủ bộ nhớ hoặc vượt giới hạn kích thước của implementation. Các vị trí bit vẫn dùng tham số `int`.

## 2. Chỉ số bit: đếm từ phải sang trái, bắt đầu ở 0

`45` có biểu diễn nhị phân dương `101101₂`:

```text
Vị trí:  5 4 3 2 1 0
Bit:     1 0 1 1 0 1
         ^       ^ ^
       bit 5   bit 2/0
```

Bit 0 là bit thấp nhất, có trọng số \(2^0=1\). Bit 5 có trọng số \(2^5=32\). Các vị trí bật trong 45 là `0, 2, 3, 5`.

```java
BigInteger x = BigInteger.valueOf(45);
System.out.println(x.testBit(0)); // true
System.out.println(x.testBit(1)); // false
System.out.println(x.testBit(5)); // true
System.out.println(x.testBit(6)); // false
```

`toString(2)` in ký tự theo thứ tự **bit cao trước**, còn `testBit(0)` đọc **bit thấp nhất**; không lấy vị trí ký tự trong chuỗi làm chỉ số bit trực tiếp.

Với số dương, các bit cao ngoài độ dài có nghĩa là 0. Với số âm, các bit cao là **1** theo quy tắc bù hai có mở rộng dấu.

## 3. bitLength(): độ rộng không tính bit dấu

Theo Javadoc Java 17, `bitLength()` trả **số bit của biểu diễn bù hai tối thiểu trừ một bit dấu**. Với số không âm, đó chính là số chữ số nhị phân cần để biểu diễn độ lớn, ngoại trừ zero trả 0.

| Giá trị | Nhị phân | `bitLength()` |
| ---: | --- | ---: |
| 0 | 0 | 0 |
| 1 | 1 | 1 |
| 7 | 111 | 3 |
| 8 | 1000 | 4 |
| 45 | 101101 | 6 |
| 127 | 1111111 | 7 |
| 128 | 10000000 | 8 |
| \(2^{100}\) | 1 theo sau 100 zero | 101 |

```java
System.out.println(BigInteger.ZERO.bitLength()); // 0
System.out.println(BigInteger.valueOf(45).bitLength()); // 6
System.out.println(BigInteger.ONE.shiftLeft(100).bitLength()); // 101
```

Với `x > 0`, có thể hình dung kết quả bằng \(\lfloor\log_2(x)\rfloor+1\). **Không** đổi số cực lớn thành `double` rồi lấy logarit để tính số bit, bởi phép chuyển kiểu có thể mất precision hoặc thành Infinity.

### Số âm khác gì?

Số âm được hiểu theo bù hai, với vô hạn bit 1 ở phía cao. Với `x < 0`, `bitLength(x)` bằng độ dài nhị phân của `-x-1`:

| Giá trị | `bitLength()` | Gợi ý |
| ---: | ---: | --- |
| -1 | 0 | `-(-1)-1 = 0` |
| -2 | 1 | Giá trị bù là 1 |
| -3 | 2 | Giá trị bù là 2 |
| -4 | 2 | Giá trị bù là 3 |
| -8 | 3 | Giá trị bù là 7 |
| -45 | 6 | Giá trị bù là 44 |
| -128 | 7 | Vừa biểu diễn signed 8-bit |
| -129 | 8 | Không vừa biểu diễn signed 8-bit |

```java
System.out.println(BigInteger.valueOf(-1).bitLength()); // 0
System.out.println(BigInteger.valueOf(-8).bitLength()); // 3
System.out.println(BigInteger.valueOf(-45).bitLength()); // 6
```

**Không dùng `bitLength() == 0` để kiểm tra giá trị bằng 0:** `-1` cũng có bitLength bằng 0! Nếu cần kiểm tra zero, dùng `x.signum() == 0` hoặc `x.equals(BigInteger.ZERO)`.

### bitLength không tự cho số byte serializing signed

`BigInteger.valueOf(128).bitLength()` bằng 8, nhưng biểu diễn signed bù hai theo byte phải có **bit dấu 0** tách biệt với bit 7 là 1, nên `toByteArray()` có thể xuất thêm byte 0 ở đầu. Nếu truyền dữ liệu nhị phân, cần xác định signed/unsigned, số byte cố định hay biến đổi, byte order và padding. Không lấy `bitLength()/8` làm độ dài mảng byte signed một cách máy móc.

## 4. bitCount(): đếm bit khác bit dấu

Đối với số **không âm**, bit dấu là 0, nên `bitCount()` bằng số bit 1 của biểu diễn nhị phân:

```java
BigInteger x = BigInteger.valueOf(45); // 101101₂
System.out.println(x.bitCount()); // 4
```

Đối với **số âm**, các bit cao bằng 1 vô hạn. Javadoc không yêu cầu đếm vô hạn bit 1, mà yêu cầu đếm **bit khác với bit dấu** — tức số bit 0 hữu hạn trong biểu diễn bù hai của số âm.

| Giá trị | `bitLength()` | `bitCount()` | Diễn giải |
| ---: | ---: | ---: | --- |
| 0 | 0 | 0 | Không có bit 1 |
| 45 | 6 | 4 | Bốn bit 1 |
| -1 | 0 | 0 | Tất cả bit đều 1 |
| -2 | 1 | 1 | Một bit 0 khác dấu |
| -3 | 2 | 1 | Một bit 0 khác dấu |
| -4 | 2 | 2 | Hai bit 0 khác dấu |
| -8 | 3 | 3 | Ba bit 0 khác dấu |
| -45 | 6 | 3 | `~(-45) = 44 = 101100₂` có ba bit 1 |

Với `x < 0`, có thể hình dung `x.not()` là số không âm `-x-1`. Khi đó `x.bitCount()` bằng số bit 1 của số bổ sung dương này.

```java
BigInteger neg = BigInteger.valueOf(-45);
System.out.println(neg.bitCount()); // 3
System.out.println(neg.not().bitCount()); // 3
```

### Đừng nhầm Integer.bitCount với BigInteger.bitCount

```java
System.out.println(Integer.bitCount(-1)); // 32
System.out.println(BigInteger.valueOf(-1).bitCount()); // 0
```

`Integer.bitCount(int)` đếm các bit 1 trong **một từ máy 32-bit**. `BigInteger.bitCount()` đếm bit **khác sign bit** trong mô hình số nguyên tùy ý. Đây là hai hợp đồng hoàn toàn khác nhau, không phải lỗi Java.

Nếu bài toán của bạn thực sự là đếm 1 bit trong **64 bit thấp**, hãy quy định độ rộng, mask đúng 64 bit rồi dùng thuật toán đếm popcount phù hợp.

## 5. testBit(n): đọc một bit với mở rộng dấu

`testBit(n)` trả `true` nếu bit thứ n bằng 1, `false` nếu bằng 0.

```java
BigInteger x = new BigInteger("101101", 2);
System.out.println(x.testBit(0));   // true
System.out.println(x.testBit(1));   // false
System.out.println(x.testBit(5));   // true
System.out.println(x.testBit(100)); // false
```

Với số âm:

```java
BigInteger minusOne = BigInteger.ONE.negate();
System.out.println(minusOne.testBit(0));   // true
System.out.println(minusOne.testBit(100)); // true
```

**Bit cao của số âm không tự về 0.** Vì thế nếu số âm đang đại diện cho một tập các cờ bit hữu hạn, bạn rất dễ nhận nhầm cờ ở vị trí lớn là bật.

`testBit(-1)` sẽ ném `ArithmeticException` vì **địa chỉ bit âm không hợp lệ**. Quy tắc này cũng áp dụng cho `setBit(-1)`, `clearBit(-1)` và `flipBit(-1)`.

## 6. setBit, clearBit, flipBit: chỉnh sửa giá trị mới

| Phương thức | Quy tắc | Tương đương về bit |
| --- | --- | --- |
| `setBit(n)` | Đặt bit n thành 1 | OR với \(2^n\) |
| `clearBit(n)` | Xóa bit n về 0 | AND với NOT \(2^n\) |
| `flipBit(n)` | Đảo bit n | XOR với \(2^n\) |

Ví dụ `x = 45 = 101101₂`:

```java
BigInteger x = BigInteger.valueOf(45);

BigInteger set = x.setBit(1);     // 47 = 101111₂
BigInteger clear = x.clearBit(2); // 41 = 101001₂
BigInteger flip = x.flipBit(3);   // 37 = 100101₂

System.out.println(x);     // 45 (không thay đổi)
System.out.println(set);   // 47
System.out.println(clear); // 41
System.out.println(flip);  // 37
```

`setBit(n)` với bit đã là 1, hoặc `clearBit(n)` với bit đã là 0, **không đổi giá trị**. `flipBit(n)` đảo trạng thái; flip hai lần ở cùng vị trí khôi phục giá trị cũ.

### Dựng bit cao

```java
BigInteger twoTo100 = BigInteger.ZERO.setBit(100);
System.out.println(twoTo100.bitLength()); // 101
```

Kết quả là \(2^{100}\), khác hẳn mã primitive `1 << 100`: do toán tử shift của `int` dùng 5 bit thấp của số lượng dịch, `1 << 100` tương đương `1 << 4`, kết quả **16**. Với BigInteger dùng `setBit(100)` hoặc `BigInteger.ONE.shiftLeft(100)`.

### Với số âm, set/clear có thể có kết quả không trực giác

```java
BigInteger negOne = BigInteger.ONE.negate();
System.out.println(negOne.setBit(100)); // -1
System.out.println(negOne.clearBit(3)); // -9
```

Ở giá trị -1, mọi bit đều là 1; set thêm bit 100 không đổi gì. Xóa bit 3 loại trọng số 8 khỏi -1 theo hai bù, nên thành -9. Khi dùng BigInteger làm **bitset**, ưu tiên số không âm hoặc mask một độ rộng đã quy định, nếu không việc mở rộng dấu gây nhầm lẫn.

## 7. shiftLeft(n): dịch trái chính xác

Với số lượng dịch `n >= 0`, `shiftLeft(n)` tương ứng nhân với \(2^n\):

```java
BigInteger x = BigInteger.valueOf(45);
System.out.println(x.shiftLeft(3)); // 360 = 45 * 8
System.out.println(BigInteger.valueOf(-13).shiftLeft(2)); // -52
```

```text
45      =    101101₂
45 << 3 = 101101000₂ = 360
```

Không có wrap-around tại 32/64 bit như primitive `int` và `long`. Tuy nhiên kết quả dịch cực lớn có thể tiêu thụ bộ nhớ lớn hoặc vượt giới hạn implementation. `shiftLeft(0)` giữ nguyên giá trị. Đây vẫn là phép trên BigInteger immutable: không sửa object gốc.

## 8. shiftRight(n): dịch phải số học và làm tròn xuống

Với `n >= 0`, `shiftRight(n)` có ngữ nghĩa **arithmetic right shift** với mở rộng bit dấu. Về giá trị, nó bằng:

\[
\operatorname{floor}\left(\frac{x}{2^n}\right).
\]

Với số không âm:

```java
System.out.println(BigInteger.valueOf(45).shiftRight(2)); // 11
```

Nhưng với **số âm**, cần phân biệt phép chia BigInteger:

```java
BigInteger neg = BigInteger.valueOf(-13);

System.out.println(neg.shiftRight(2)); // -4
System.out.println(neg.divide(BigInteger.valueOf(4))); // -3
```

Bởi \(-13/4=-3.25\), `shiftRight(2)` làm tròn **về âm vô cực** ra -4, trong khi `divide(4)` làm tròn thương **về zero** ra -3.

| Số gốc | Phép toán | Kết quả |
| --- | --- | ---: |
| 13 | `shiftRight(2)` | 3 |
| -13 | `shiftRight(2)` | -4 |
| -13 | `divide(4)` | -3 |
| 45 | `shiftRight(2)` | 11 |
| 45 | `shiftRight(100)` | 0 |
| -13 | `shiftRight(100)` | -1 |

```java
System.out.println(BigInteger.valueOf(45).shiftRight(100)); // 0
System.out.println(BigInteger.valueOf(-13).shiftRight(100)); // -1
```

Dịch phải số âm rất xa trở thành -1 do bit dấu tiếp tục là 1. Nếu bạn cần tính **chia lấy phần nguyên về zero** đối với số âm, không thay `divide` bằng `shiftRight` trừ khi bạn đã chứng minh không có phần dư hoặc đáp ứng quy tắc floor.

## 9. Số lượng dịch âm và trường hợp lỗi

Javadoc của `shiftLeft(int)` và `shiftRight(int)` chấp nhận **khoảng dịch âm**, khi đó hướng dịch bị đảo:

| API | Ý nghĩa |
| --- | --- |
| `x.shiftLeft(3)` | Dịch trái 3 |
| `x.shiftRight(3)` | Dịch phải 3 |
| `x.shiftLeft(-3)` | Dịch phải 3 |
| `x.shiftRight(-3)` | Dịch trái 3 |

```java
BigInteger x = BigInteger.valueOf(45);
System.out.println(x.shiftLeft(-2)); // 11, cùng giá trị x.shiftRight(2)
```

**Số lượng dịch âm khác địa chỉ bit âm**:

- `x.shiftLeft(-1)` hợp lệ, đổi thành dịch phải.
- `x.testBit(-1)`, `x.setBit(-1)`, `x.clearBit(-1)` và `x.flipBit(-1)` ném `ArithmeticException`.

Đừng dựa vào `1 << 100` hoặc quy tắc mask shift count của primitive để suy ra `BigInteger.shiftLeft(100)`. Những API này khác nhau về độ rộng và ý nghĩa.

Với các chỉ số cực lớn, dù kiểu tham số là `int`, chương trình vẫn phải hạn chế theo ngân sách bộ nhớ/thời gian; không cấp phát số bit hàng tỷ chỉ vì người dùng cung cấp số n hợp lệ về mặt cú pháp.

## 10. getLowestSetBit và mask độ rộng cố định

Phương thức bổ trợ `getLowestSetBit()` tìm vị trí bit 1 thấp nhất, hoặc -1 khi giá trị bằng zero:

```java
System.out.println(BigInteger.valueOf(40).getLowestSetBit()); // 3 (40 = 101000₂)
System.out.println(BigInteger.ZERO.getLowestSetBit());       // -1
```

Với các thao tác trên **n bit thấp**, có thể tạo mask giá trị \(2^n-1\):

```java
int n = 4;
BigInteger mask = BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE);
// mask = 15 = 1111₂

BigInteger lastFour = BigInteger.valueOf(-1).and(mask);
// 15: chỉ lấy bốn bit thấp của -1
```

Với `n == 0`, mask là zero. Ứng dụng phải bảo đảm `n >= 0` và không quá lớn. Sau khi mask, giá trị được hiểu như một **vector bit không âm**, giúp tránh nhầm lẫn từ bit dấu vô hạn của số nguyên âm.

Cần biết sự khác nhau giữa kiểu mô hình: `BigInteger` dùng khi cần **giá trị số nguyên** có dấu hoặc lũy thừa hai lớn; `java.util.BitSet` có thể phù hợp hơn khi mô hình là tập vị trí bit không âm thay đổi thường xuyên.

## 11. Chương trình Java 17+ độc lập

Tạo file **`BigIntegerBitDemo.java`**:

```java
import java.math.BigInteger;

public class BigIntegerBitDemo {
    public static void main(String[] args) {
        BigInteger x = BigInteger.valueOf(45);
        BigInteger negative = x.negate();

        System.out.println("positiveBinary=" + x.toString(2));
        System.out.println("positiveBitLength=" + x.bitLength());
        System.out.println("positiveBitCount=" + x.bitCount());
        System.out.println("negativeBinary=" + negative.toString(2));
        System.out.println("negativeBitLength=" + negative.bitLength());
        System.out.println("negativeBitCount=" + negative.bitCount());

        BigInteger minusOne = BigInteger.ONE.negate();
        System.out.println("minusOneBitLength=" + minusOne.bitLength());
        System.out.println("minusOneBitCount=" + minusOne.bitCount());
        System.out.println("minusEightBitLength=" +
                BigInteger.valueOf(-8).bitLength());
        System.out.println("minusEightBitCount=" +
                BigInteger.valueOf(-8).bitCount());

        System.out.println("testBit0=" + x.testBit(0));
        System.out.println("testBit1=" + x.testBit(1));
        System.out.println("testBit5=" + x.testBit(5));
        System.out.println("highNegativeBit=" + minusOne.testBit(100));

        System.out.println("setBit1=" + x.setBit(1));
        System.out.println("clearBit2=" + x.clearBit(2));
        System.out.println("flipBit3=" + x.flipBit(3));
        System.out.println("originalUnchanged=" + x);
        System.out.println("highBitLength=" +
                BigInteger.ZERO.setBit(100).bitLength());

        System.out.println("shiftLeftThree=" + x.shiftLeft(3));
        System.out.println("shiftRightTwo=" + x.shiftRight(2));
        BigInteger minusThirteen = BigInteger.valueOf(-13);
        System.out.println("negativeShiftRight=" +
                minusThirteen.shiftRight(2));
        System.out.println("negativeDivide=" +
                minusThirteen.divide(BigInteger.valueOf(4)));
        System.out.println("negativeShiftDistance=" + x.shiftLeft(-2));
        System.out.println("farPositiveShift=" + x.shiftRight(100));
        System.out.println("farNegativeShift=" +
                minusThirteen.shiftRight(100));

        System.out.println("lowestSetBit40=" +
                BigInteger.valueOf(40).getLowestSetBit());
        System.out.println("lowestSetBitZero=" +
                BigInteger.ZERO.getLowestSetBit());
        System.out.println("negativeClearBit3=" + minusOne.clearBit(3));
        System.out.println("negativeBit3AfterClear=" +
                minusOne.clearBit(3).testBit(3));

        try {
            x.testBit(-1);
            System.out.println("negativeBitIndex=missed");
        } catch (ArithmeticException ex) {
            System.out.println("negativeBitIndex=ArithmeticException");
        }

        BigInteger mask = BigInteger.ONE.shiftLeft(4)
                .subtract(BigInteger.ONE);
        System.out.println("lowFourMask=" + mask);
        System.out.println("maskedNegative=" +
                BigInteger.valueOf(-1).and(mask));
        System.out.println("primitiveShift100=" + (1 << 100));
    }
}
```

Biên dịch và chạy:

```bash
javac BigIntegerBitDemo.java
java BigIntegerBitDemo
```

Output:

```text
positiveBinary=101101
positiveBitLength=6
positiveBitCount=4
negativeBinary=-101101
negativeBitLength=6
negativeBitCount=3
minusOneBitLength=0
minusOneBitCount=0
minusEightBitLength=3
minusEightBitCount=3
testBit0=true
testBit1=false
testBit5=true
highNegativeBit=true
setBit1=47
clearBit2=41
flipBit3=37
originalUnchanged=45
highBitLength=101
shiftLeftThree=360
shiftRightTwo=11
negativeShiftRight=-4
negativeDivide=-3
negativeShiftDistance=11
farPositiveShift=0
farNegativeShift=-1
lowestSetBit40=3
lowestSetBitZero=-1
negativeClearBit3=-9
negativeBit3AfterClear=false
negativeBitIndex=ArithmeticException
lowFourMask=15
maskedNegative=15
primitiveShift100=16
```

Các dòng quan trọng:

- `negativeBitCount=3` và `minusOneBitCount=0`: `bitCount` ở số âm đếm bit **khác dấu**, không đếm bit 1 trong 32/64 bit.
- `highNegativeBit=true`: bit 100 của -1 bằng 1 vì **mở rộng dấu**.
- `negativeShiftRight=-4` so với `negativeDivide=-3`: dịch phải dùng **floor**, phép chia nguyên dùng **truncate toward zero**.
- `primitiveShift100=16` khác việc đặt bit 100 trong `BigInteger`.
- `negativeBitIndex=ArithmeticException`: chỉ số bit âm bị từ chối, dù **khoảng dịch âm** được cho phép.

## 12. Lỗi thường gặp và checklist

| Nhận định | Cách hiểu đúng |
| --- | --- |
| “`bitCount(-1)` có vô hạn bit 1 nên trả vô hạn.” | Javadoc đếm số bit **khác sign bit**; kết quả là 0. |
| “`BigInteger.bitCount(-1)` giống `Integer.bitCount(-1)`.” | Sai: kết quả tương ứng là 0 và 32. |
| “`bitLength() == 0` chỉ xảy ra với zero.” | Sai: -1 cũng trả 0. |
| “`toString(2)` của số âm in toàn bộ bù hai.” | Sai: in dấu trừ và độ lớn, không in sign extension vô hạn. |
| “Bit 100 của -1 phải là 0 vì số -1 nhỏ.” | Sai: số âm có bit cao là 1. |
| “`setBit(n)` thay đổi object hiện tại.” | Sai: BigInteger immutable; lưu kết quả trả về. |
| “`1 << 100` tạo bit 100.” | Sai: `int` che bớt số lượng dịch; kết quả 16. |
| “`shiftRight(2)` và `divide(4)` luôn bằng nhau.” | Sai khi số âm và có phần dư. |
| “`shiftLeft(-2)` sẽ ném exception.” | Sai: khoảng dịch âm đảo hướng; **bit index âm** mới bị từ chối. |
| “Dịch phải số âm đủ xa cho 0.” | Sai: có thể cho -1 do sign extension. |
| “BigInteger không bao giờ hết khả năng lưu.” | Vẫn giới hạn bộ nhớ và kích thước đối tượng. |
| “`bitLength()/8` cho số byte signed cần thiết.” | Cần xét sign, padding và encoding. |

Checklist:

1. **Xác định đầu vào có âm hay không:** nếu dùng làm cờ bit, cân nhắc mask độ rộng hoặc quy ước không âm.
2. **Đếm bit đúng nghĩa:** `bitLength` đo độ dài không tính sign; `bitCount` đếm bit khác sign.
3. **Kiểm tra chỉ số:** `testBit`/`setBit`/`clearBit`/`flipBit` cần n không âm.
4. **Nhớ bất biến:** dùng `x = x.setBit(n)` thay vì bỏ kết quả.
5. **Dùng BigInteger để dựng bit lớn:** `BigInteger.ONE.shiftLeft(n)`, không phải `1 << n`.
6. **Với số âm, phân biệt floor và truncate:** `shiftRight` không thay được `divide` trong mọi trường hợp.
7. **Giới hạn tài nguyên:** n lớn có thể tiêu tốn bộ nhớ dù cú pháp hợp lệ.
8. **Serialize theo contract:** chọn signed/unsigned, endian, độ rộng và leading padding minh bạch.

**Ghi nhớ:** Những phương thức bit của `BigInteger` đều dựa trên **số nguyên có dấu bù hai, mở rộng bit dấu vô hạn**. Phần lớn hiểu lầm xuất phát từ việc áp dụng trực giác về một từ máy `int`/`long` có kích thước cố định sang số nguyên tùy ý.

Tài liệu chuẩn: [Oracle Java 17 BigInteger Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-011">compareTo, equals, signum, abs và negate</a>

<details>
<summary>Xem chi tiết</summary>

# `BigInteger.compareTo`, `equals`, `signum`, `abs` và `negate` trong Java

## 1. Mục tiêu và bối cảnh

`java.math.BigInteger` biểu diễn số nguyên có dấu với độ chính xác tùy ý (*arbitrary precision*). Giá trị không bị giới hạn trong 32 bit của `int` hoặc 64 bit của `long`; giới hạn thực tế là bộ nhớ, tài nguyên tính toán và giới hạn triển khai thư viện. Đây là một **kiểu đối tượng bất biến**, nên các phép tính không sửa đối tượng nguồn.

Năm phương thức trong bài trả lời năm câu hỏi cốt lõi:

| Phương thức | Câu hỏi | Kiểu kết quả |
| --- | --- | --- |
| `a.compareTo(b)` | `a` nhỏ hơn, bằng hay lớn hơn `b`? | `int` âm, `0`, hoặc dương |
| `a.equals(b)` | Hai đối tượng có cùng **giá trị `BigInteger`** không? | `boolean` |
| `a.signum()` | Giá trị là âm, bằng 0 hay dương? | `int` thuộc `-1`, `0`, `1` |
| `a.abs()` | Độ lớn không âm của `a` là gì? | `BigInteger` |
| `a.negate()` | Số đối của `a` là gì? | `BigInteger` |

Ví dụ ngắn, đặt trong một phương thức Java với `import java.math.BigInteger`:

```java
BigInteger a = new BigInteger("-123456789012345678901234567890");
BigInteger b = BigInteger.valueOf(42);

System.out.println(a.compareTo(b)); // -1: a nhỏ hơn b
System.out.println(a.signum());      // -1: a âm
System.out.println(a.abs());         // 123456789012345678901234567890
System.out.println(a.negate());      // 123456789012345678901234567890
System.out.println(a);               // Vẫn là số âm ban đầu
```

Cần phân biệt ba khái niệm: **thứ tự số học**, **bằng nhau về giá trị**, và **cùng tham chiếu đối tượng**. Trong phần lớn code nghiệp vụ, `compareTo`, `equals` và `signum` trả lời câu hỏi đúng; `==` không thay chúng được.

## 2. `compareTo(BigInteger)`: so thứ tự chính xác cả khi số rất lớn

Chữ ký API:

```java
public int compareTo(BigInteger val)
```

Kết quả mang **dấu** của quan hệ thứ tự:

\[
a.\mathrm{compareTo}(b)
\begin{cases}
< 0 & \text{khi } a < b,\\
= 0 & \text{khi } a = b,\\
> 0 & \text{khi } a > b.
\end{cases}
\]

Các ví dụ:

```java
BigInteger small = BigInteger.valueOf(-5);
BigInteger zero = BigInteger.ZERO;
BigInteger large = new BigInteger("99999999999999999999999999");

System.out.println(small.compareTo(zero) < 0); // true
System.out.println(large.compareTo(zero) > 0); // true
System.out.println(zero.compareTo(BigInteger.ZERO) == 0); // true
```

**Đừng dựa vào chính xác `-1` hoặc `1` như một hợp đồng tổng quát của `Comparable`.** Hợp đồng yêu cầu kết quả âm/0/dương; dùng `< 0`, `== 0`, `> 0`.

`compareTo` làm việc trực tiếp với `BigInteger`, không phải chuyển giá trị sang `int`, `long` hoặc `double`:

```java
BigInteger left = new BigInteger("9007199254740993");
BigInteger right = new BigInteger("9007199254740992");

System.out.println(left.compareTo(right) > 0); // true
```

Nếu chuyển cả hai sang `double`, các giá trị trên có thể cùng làm tròn thành một số, phá hỏng phép so sánh. `compareTo` giữ nguyên thứ tự toán học trên mọi giá trị `BigInteger` hợp lệ.

### So sánh chéo dấu và hai số âm

- Mọi số âm nhỏ hơn số 0, và số 0 nhỏ hơn mọi số dương.
- Với hai số âm, số có **giá trị tuyệt đối lớn hơn** thì lại **nhỏ hơn** theo thứ tự thông thường.

Ví dụ:

```java
BigInteger a = BigInteger.valueOf(-100);
BigInteger b = BigInteger.valueOf(-2);

System.out.println(a.compareTo(b) < 0); // true
System.out.println(a.abs().compareTo(b.abs()) > 0); // true
```

`-100 < -2` nhưng `|-100| > |-2|`.

### Không dùng phép trừ rồi ép về `int` để làm comparator

Sai lầm thường gặp:

```java
// Không dùng trong Comparator<BigInteger>:
int compareWrong(BigInteger a, BigInteger b) {
    return a.subtract(b).intValue();
}
```

Hiệu có thể lớn hơn miền `int`, khiến `intValue()` bỏ bit cao. Hai số khác nhau có thể tạo kết quả 0 hoặc sai dấu, phá vỡ thứ tự.

Cách đúng:

```java
int compareCorrect(BigInteger a, BigInteger b) {
    return a.compareTo(b);
}
```

Đây là phương thức minh họa trong một class Java; không phải hai chương trình độc lập. Trong thực tế có thể dùng trực tiếp `Comparator.naturalOrder()` hoặc `BigInteger::compareTo`.

### `min` và `max`

Hai tiện ích liên quan:

```java
BigInteger a = BigInteger.valueOf(-3);
BigInteger b = BigInteger.valueOf(2);

System.out.println(a.min(b)); // -3
System.out.println(a.max(b)); // 2
```

`min`/`max` lựa chọn giá trị theo thứ tự mà `compareTo` xác định. Không dùng chúng khi cần so sánh **độ lớn tuyệt đối**; lúc đó phải xét `abs()`.

## 3. `equals(Object)`: so giá trị, không so địa chỉ đối tượng

Chữ ký:

```java
public boolean equals(Object x)
```

`BigInteger.equals` trả `true` khi đối số là một `BigInteger` biểu diễn cùng số nguyên. Nó **không** quan tâm cách giá trị được tạo ra:

```java
BigInteger a = new BigInteger("42");
BigInteger b = BigInteger.valueOf(42);
BigInteger c = new BigInteger("0042");

System.out.println(a.equals(b)); // true
System.out.println(a.equals(c)); // true
```

Các chuỗi `"42"` và `"0042"` là cách viết khác nhau của cùng số nguyên 42, nên các đối tượng có cùng giá trị. `BigInteger` không lưu “số chữ số 0 ở đầu” như một thuộc tính số học có nghĩa.

### `==` chỉ so tham chiếu

```java
BigInteger a = new BigInteger("42");
BigInteger b = new BigInteger("42");

System.out.println(a.equals(b)); // true
System.out.println(a == b);      // false: ở đây tạo hai đối tượng riêng
```

Lời gọi `new BigInteger(String)` tạo đối tượng mới cho hai biến trong ví dụ, nên so tham chiếu nhận `false`. Nhưng **không suy ngược** rằng cùng giá trị luôn có tham chiếu khác nhau: các hằng và `valueOf` có thể tái sử dụng đối tượng.

```java
BigInteger x = BigInteger.valueOf(10);
BigInteger y = BigInteger.TEN;

System.out.println(x.equals(y)); // true
// Không dùng x == y làm quy tắc bằng nhau của số.
```

`BigInteger.valueOf` có thể cache các giá trị thường dùng. Đây là chi tiết hiệu năng/identity, không thay hợp đồng `equals`. Không dùng `System.identityHashCode` làm ID toán học.

### So sánh khác kiểu

```java
System.out.println(BigInteger.valueOf(42).equals(Long.valueOf(42L)));
// false
```

`BigInteger` và `Long` khác lớp, dù cùng biểu diễn số toán học 42. Nếu cần so sánh số nguyên dạng `long` với `BigInteger`, chuyển `long` thành `BigInteger` **mà không mất thông tin**:

```java
long input = 42L;
BigInteger value = new BigInteger("42");

boolean equal = value.equals(BigInteger.valueOf(input)); // true
```

Không ép một `BigInteger` lớn xuống `long` hoặc sang `double` để so nếu chưa xác minh miền; mất chính xác có thể khiến hai số khác nhau thành bằng nhau.

### `equals` và `compareTo` nhất quán với nhau

Đối với hai đối tượng `BigInteger` không `null`:

\[
a.equals(b) \iff a.compareTo(b) == 0.
\]

Đây là hợp đồng quan trọng khi dùng trong collection. Trong khi `BigDecimal` có thể coi `"2.0"` và `"2.00"` bằng nhau về thứ tự nhưng khác nhau theo `equals` do scale, `BigInteger` **không có scale decimal**: thứ tự tự nhiên của nó nhất quán với `equals`.

Nếu cần giữ định dạng nguyên thủy (ví dụ `"0042"` khác `"42"` về mã hồ sơ), hãy giữ chuỗi nguồn hoặc một ID dạng chuỗi phù hợp; `BigInteger.equals` cố ý xem chúng là cùng số.

## 4. `signum()`: lấy dấu mà không cần so với 0 nhiều lần

Chữ ký:

```java
public int signum()
```

Kết quả **chỉ là** `-1`, `0` hoặc `1`:

| Giá trị `BigInteger` | `signum()` | Ý nghĩa |
| --- | ---: | --- |
| Âm, chẳng hạn `-9` | `-1` | Nhỏ hơn 0 |
| Bằng 0 | `0` | Không âm, không dương |
| Dương, chẳng hạn `42` | `1` | Lớn hơn 0 |

Ví dụ:

```java
System.out.println(BigInteger.valueOf(-9).signum()); // -1
System.out.println(BigInteger.ZERO.signum());        // 0
System.out.println(BigInteger.valueOf(42).signum()); // 1
```

Khác với `compareTo`, `signum` không cần một đối tượng thứ hai. Dùng khi bạn chỉ quan tâm dấu:

```java
static String describe(BigInteger value) {
    return switch (value.signum()) {
        case -1 -> "NEGATIVE";
        case  0 -> "ZERO";
        case  1 -> "POSITIVE";
        default -> throw new AssertionError("Unexpected sign");
    };
}
```

Đoạn trên là phương thức trong một class Java 17 và giả sử `value != null`. `switch` này hợp lệ với JDK 17; các nhánh bao hết miền giá trị được `signum()` cam kết.

### `signum` khác kiểm tra parity

`signum()` không cho biết số chẵn/lẻ. Số `-8` và `-7` đều có `signum() == -1`. Muốn kiểm tra bit, dùng các phép như `testBit(0)` sau khi xác nhận cách diễn giải số âm phù hợp, hoặc `remainder(BigInteger.TWO)` theo quy tắc số học cần thiết.

### `BigInteger` không có số âm 0

```java
BigInteger zero = new BigInteger("-0");

System.out.println(zero);          // 0
System.out.println(zero.signum()); // 0
```

Khác `double`, `BigInteger` không có hai giá trị số 0 với bit dấu khác nhau. Chuỗi `"-0"` khi parse vẫn biểu diễn số nguyên 0, `signum()` trả 0. Nếu cần ghi nhận người dùng đã gõ dấu `-`, phải giữ chuỗi ban đầu; đó là thông tin **biểu diễn**, không phải giá trị số nguyên.

## 5. `abs()`: lấy trị tuyệt đối không overflow ở biên `long`

Chữ ký:

```java
public BigInteger abs()
```

Định nghĩa:

\[
|n| =
\begin{cases}
-n & n < 0,\\
n  & n \ge 0.
\end{cases}
\]

Ví dụ:

```java
BigInteger negative = new BigInteger("-12345678901234567890");
BigInteger magnitude = negative.abs();

System.out.println(magnitude); // 12345678901234567890
System.out.println(negative);  // -12345678901234567890
```

`abs()` trả giá trị không âm, **không thay đổi** `negative`. Với 0, kết quả là 0. Với số dương, kết quả có cùng giá trị với đầu vào; thư viện có thể trả lại chính đối tượng hoặc một đối tượng khác tùy tối ưu, nhưng code không được dựa vào identity đó.

### Khác biệt nổi bật với `Math.abs(Long.MIN_VALUE)`

Kiểu `long` có miền từ `-2^63` đến `2^63 - 1`. Giá trị tuyệt đối của `Long.MIN_VALUE` là `2^63`, **không vừa miền số dương `long`**:

```java
System.out.println(Math.abs(Long.MIN_VALUE));
// -9223372036854775808
```

Theo quy tắc số nguyên Java, kết quả vẫn âm ở trường hợp biên này.

Nếu trước tiên chuyển nguyên giá trị `long` sang `BigInteger`, trị tuyệt đối được giữ chính xác:

```java
BigInteger exactMagnitude =
        BigInteger.valueOf(Long.MIN_VALUE).abs();

System.out.println(exactMagnitude); // 9223372036854775808
```

**Đúng thứ tự là chuyển rồi `abs`**, không phải `BigInteger.valueOf(Math.abs(Long.MIN_VALUE))`, bởi phép `Math.abs(long)` có thể đã cho kết quả âm không mong muốn trước khi BigInteger nhận được giá trị.

Tình huống thực tế gồm kiểm tra độ lệch giá trị rất lớn, khoảng cách giữa số nguyên và các thuật toán nhận dữ liệu gần cận 64 bit.

### `abs` khác `signum`

`abs()` trả **toàn bộ độ lớn** như một `BigInteger`, còn `signum()` chỉ trả dấu như một `int`. Đừng dùng `abs().equals(BigInteger.ONE)` để kiểm tra dấu dương vì điều đó chỉ đúng cho số `1`, không đúng cho mọi số dương.

## 6. `negate()`: tạo số đối và giữ nguyên đối tượng nguồn

Chữ ký:

```java
public BigInteger negate()
```

Định nghĩa:

\[
\operatorname{negate}(n)=-n.
\]

Ví dụ:

```java
BigInteger original = BigInteger.valueOf(17);
BigInteger opposite = original.negate();

System.out.println(opposite); // -17
System.out.println(original); // 17
```

`negate()` không sửa `original`. Để đổi trạng thái của biến, phải gán kết quả:

```java
BigInteger balance = BigInteger.valueOf(17);
balance = balance.negate();

System.out.println(balance); // -17
```

Một số đẳng thức đúng về giá trị:

\[
-(-n)=n,\qquad -0=0,\qquad |-n|=|n|.
\]

Kiểm tra:

```java
BigInteger n = new BigInteger("-12345678901234567890");

System.out.println(n.negate().negate().equals(n)); // true
System.out.println(BigInteger.ZERO.negate());      // 0
System.out.println(n.negate().abs().equals(n.abs())); // true
```

Không suy từ `n.negate().negate().equals(n)` rằng `n.negate().negate() == n`; phương thức là phép toán giá trị, không có hợp đồng giữ identity.

### `negate()` của `Long.MIN_VALUE` không bị tràn như `long`

```java
long x = Long.MIN_VALUE;
System.out.println(-x); // -9223372036854775808: overflow của long

BigInteger bi = BigInteger.valueOf(x);
System.out.println(bi.negate()); // 9223372036854775808
```

Khi cần số đối chính xác của một giá trị âm cực tiểu, hãy chuyển sang `BigInteger` **trước** phép đổi dấu.

### `abs()` có phải chỉ là `negate()` không?

Không:

- Với `n > 0`, `n.abs()` vẫn dương, còn `n.negate()` âm.
- Với `n < 0`, hai kết quả có cùng giá trị dương.
- Với `n == 0`, cả hai cùng cho 0.

`abs` là **lấy độ lớn**, còn `negate` là **đổi dấu**. Đây là hai biến đổi khác nhau, không thay thế nhau cho dữ liệu dương.

## 7. Tính bất biến: phải nhận giá trị trả về

`BigInteger` không có toán tử `++`, `--`, `+` hay toán tử một ngôi `-` giống primitive. Các phép toán của nó là phương thức:

```java
BigInteger n = BigInteger.TEN;

// n++;      // Không biên dịch: BigInteger không phải kiểu số primitive
// n = -n;   // Không biên dịch
n = n.add(BigInteger.ONE);
n = n.negate();

System.out.println(n); // -11
```

Nếu bỏ qua kết quả của `add`, `negate` hoặc `abs`, giá trị cũ **không thay đổi**:

```java
BigInteger n = BigInteger.valueOf(-5);
n.abs();
System.out.println(n); // -5

n = n.abs();
System.out.println(n); // 5
```

Đây là lý do các hàm giúp biến đổi dãy `BigInteger` thường trả về một `BigInteger` mới về **mặt giá trị**. “Bất biến” không hứa mọi phép gọi luôn cấp phát một đối tượng mới; nó chỉ bảo đảm không sửa dữ liệu bên trong đối tượng đã có.

## 8. Kết hợp năm phương thức trong các mẫu code thực tế

### 8.1. Kiểm tra miền giá trị

Nếu người dùng nhập một số nguyên lớn `quantity` và chỉ chấp nhận giá trị từ `0` đến `max`:

```java
static boolean inRange(BigInteger quantity, BigInteger max) {
    if (quantity == null || max == null) {
        return false;
    }
    return quantity.signum() >= 0 && quantity.compareTo(max) <= 0;
}
```

Ở đây `signum() >= 0` kiểm tra không âm; `compareTo(max) <= 0` kiểm tra cận trên. **Không** ép `quantity` sang `long` chỉ để kiểm tra, vì phép ép có thể mất dữ liệu.

### 8.2. Khoảng cách tuyệt đối giữa hai số nguyên

Khoảng cách một chiều giữa `a` và `b`:

\[
d = |a-b|.
\]

```java
static BigInteger distance(BigInteger a, BigInteger b) {
    return a.subtract(b).abs();
}
```

Ví dụ:

```java
BigInteger a = new BigInteger("100000000000000000000");
BigInteger b = new BigInteger("99999999999999999995");

System.out.println(distance(a, b)); // 5
```

Lưu ý đây là **giá trị tuyệt đối của hiệu**, không phải hiệu của hai giá trị tuyệt đối:

\[
|a-b|\ \text{thường khác}\ |a|-|b|.
\]

Với `a = -5` và `b = 3`, `|a-b| = 8`, còn `|a|-|b| = 2`.

### 8.3. Chuẩn hóa theo dấu trong thuật toán

Một thuật toán có thể cần tách dấu và độ lớn:

```java
BigInteger input = new BigInteger("-12345678901234567890");

int sign = input.signum();
BigInteger magnitude = input.abs();

System.out.println(sign);      // -1
System.out.println(magnitude); // 12345678901234567890
```

Nếu sau này dựng lại số:

```java
BigInteger restored =
        sign < 0 ? magnitude.negate() : magnitude;

System.out.println(restored.equals(input)); // true
```

Với số 0, `sign == 0` và `magnitude == 0` về **giá trị**, vì `BigInteger` không có âm 0. Mẫu code này hữu ích trong các phép xử lý dấu riêng, nhưng đừng viết lại thuật toán thư viện khi một phương thức sẵn có đã đáp ứng đầy đủ.

### 8.4. Sắp xếp tự nhiên

`BigInteger` triển khai `Comparable<BigInteger>`, nên dùng comparator tự nhiên:

```java
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

List<BigInteger> values = new ArrayList<>(
        List.of(
                BigInteger.TEN,
                BigInteger.valueOf(-3),
                BigInteger.ZERO,
                BigInteger.valueOf(2)
        )
);

values.sort(BigInteger::compareTo);
System.out.println(values); // [-3, 0, 2, 10]
```

Thứ tự trên là **thứ tự số học**: `-3 < 0 < 2 < 10`. Nếu yêu cầu sắp theo độ lớn tuyệt đối, phải chỉ rõ cách giải quyết hòa (chẳng hạn `-3` và `3` có độ lớn bằng nhau).

Ví dụ comparator độ lớn:

```java
values.sort((left, right) -> left.abs().compareTo(right.abs()));
```

Comparator này xem `-3` và `3` như cùng thứ tự (kết quả compare 0) dù `equals` của chúng là `false`. Nếu dùng nó với `TreeSet`/`TreeMap`, điều đó có thể gây **mất sự phân biệt các khóa khác giá trị**. Muốn vừa ưu tiên độ lớn vừa giữ thứ tự tổng quát phù hợp với `equals`, thêm tie-breaker:

```java
values.sort(
        (left, right) -> {
            int order = left.abs().compareTo(right.abs());
            return order != 0 ? order : left.compareTo(right);
        }
);
```

### 8.5. Phân nhánh theo dấu cho bài toán nghiệp vụ

```java
static String status(BigInteger balance) {
    return switch (balance.signum()) {
        case -1 -> "DEBT";
        case 0 -> "SETTLED";
        case 1 -> "CREDIT";
        default -> throw new AssertionError();
    };
}
```

Đây chỉ là ví dụ minh họa thuật toán: cách diễn giải số âm là nợ, số dương là dư có phải khớp hợp đồng dữ liệu thực tế. `signum()` không tự biết ý nghĩa tài chính hay nghiệp vụ.

## 9. `equals`, `hashCode` và collection

`BigInteger.equals` kiểm tra giá trị và `hashCode` tuân thủ hợp đồng tương ứng. Vì thế có thể dùng `BigInteger` làm khóa cho `HashMap`/`HashSet`:

```java
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

Set<BigInteger> identifiers = new HashSet<>();
identifiers.add(new BigInteger("1234567890123456789012345"));

boolean present = identifiers.contains(
        new BigInteger("1234567890123456789012345")
);

System.out.println(present); // true
```

Hai lời gọi constructor có thể tạo hai đối tượng khác nhau về tham chiếu nhưng `HashSet` so khớp **giá trị**.

`TreeSet<BigInteger>` sử dụng thứ tự tự nhiên qua `compareTo`; vì thứ tự đó nhất quán với `equals`, nó có hành vi nhận diện giá trị đúng như mong đợi.

Không dùng `IdentityHashMap<BigInteger, ...>` cho key là số nếu mục tiêu là bằng nhau theo **giá trị**: `IdentityHashMap` cố ý sử dụng tham chiếu, khiến dữ liệu có cùng số có thể bị coi là các khóa khác nhau.

### Dấu âm không ảnh hưởng định danh như chuỗi nguồn

`new BigInteger("-0")`, `BigInteger.ZERO`, `new BigInteger("000")` đều bằng nhau về giá trị. Nếu một mã định danh dạng chuỗi yêu cầu giữ số 0 đứng đầu, không nên chuyển nó thành `BigInteger` rồi kỳ vọng `equals` phân biệt được chuỗi nguồn.

## 10. Null và kiểu dữ liệu: các ngoại lệ cần biết

`BigInteger` là kiểu **tham chiếu**, nên biến có thể nhận `null`:

```java
BigInteger missing = null;
// missing.signum(); // NullPointerException
// missing.abs();    // NullPointerException
// missing.negate(); // NullPointerException
```

`null` không có giá trị số. Phải quyết định cách xử lý:

```java
static boolean isZeroIfPresent(BigInteger value) {
    return value != null && value.signum() == 0;
}
```

`Objects.equals` hỗ trợ so đối tượng nullable:

```java
boolean same = java.util.Objects.equals(
        (BigInteger) null, (BigInteger) null
); // true vì cả hai tham chiếu đều null
```

Nhưng `Objects.equals(null, null)` không chứng minh tồn tại hai số 0. Nếu nghiệp vụ yêu cầu có hai số hợp lệ, hãy kiểm tra không `null` trước.

Các phương thức có đối số bắt buộc là `BigInteger` như `compareTo(null)` không có một giá trị số để so; trong Java 17, việc này gây `NullPointerException`. Nên validate ở ranh giới API:

```java
static int compareRequired(BigInteger a, BigInteger b) {
    java.util.Objects.requireNonNull(a, "a");
    java.util.Objects.requireNonNull(b, "b");
    return a.compareTo(b);
}
```

`equals(null)` thì trả `false` nếu receiver là `BigInteger` không `null`, theo hợp đồng `equals(Object)`. **Đừng đánh đồng** `equals(null)` với `compareTo(null)`.

### BigInteger không phải primitive và không auto-unbox

Không thể viết:

```java
BigInteger n = BigInteger.TEN;
// if (n > 0) {} // Không biên dịch
// int x = n;    // Không biên dịch
```

Muốn so với 0, dùng `n.signum() > 0` hoặc `n.compareTo(BigInteger.ZERO) > 0`. Muốn trích `long`, dùng `longValueExact()` nếu bắt buộc chính xác và vừa miền; không dùng `longValue()` tùy tiện với số cực lớn.

## 11. Ví dụ Java 17 hoàn chỉnh

Lưu dưới tên `BigIntegerComparisonDemo.java`:

```java
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class BigIntegerComparisonDemo {

    static BigInteger distance(BigInteger left, BigInteger right) {
        return left.subtract(right).abs();
    }

    static String describe(BigInteger value) {
        return switch (value.signum()) {
            case -1 -> "NEGATIVE";
            case 0 -> "ZERO";
            case 1 -> "POSITIVE";
            default -> throw new AssertionError();
        };
    }

    public static void main(String[] args) {
        BigInteger negative =
                new BigInteger("-123456789012345678901234567890");
        BigInteger positive = BigInteger.valueOf(42);

        System.out.println("compare negative to 42: "
                + negative.compareTo(positive));
        System.out.println("compare 42 to negative: "
                + positive.compareTo(negative));

        System.out.println("equals same value: "
                + new BigInteger("42").equals(positive));
        System.out.println("equals different type: "
                + positive.equals(Long.valueOf(42L)));

        System.out.println("negative signum: " + negative.signum());
        System.out.println("zero signum: " + BigInteger.ZERO.signum());
        System.out.println("positive signum: " + positive.signum());

        System.out.println("absolute negative: " + negative.abs());
        System.out.println("negated negative: " + negative.negate());
        System.out.println("original unchanged: " + negative);
        System.out.println("double negation equals: "
                + negative.negate().negate().equals(negative));

        System.out.println("abs min long: "
                + BigInteger.valueOf(Long.MIN_VALUE).abs());
        System.out.println("Math.abs min long: "
                + Math.abs(Long.MIN_VALUE));

        System.out.println("negative zero signum: "
                + new BigInteger("-0").signum());
        System.out.println("describe zero: "
                + describe(BigInteger.ZERO));

        System.out.println("distance: " + distance(
                new BigInteger("100000000000000000000"),
                new BigInteger("99999999999999999995")));

        List<BigInteger> values = new ArrayList<>(List.of(
                BigInteger.TEN,
                BigInteger.valueOf(-3),
                BigInteger.ZERO,
                BigInteger.valueOf(2)
        ));
        values.sort(BigInteger::compareTo);
        System.out.println("sorted: " + values);
    }
}
```

Chạy với JDK 17 hoặc mới hơn:

```bash
javac BigIntegerComparisonDemo.java
java BigIntegerComparisonDemo
```

Đầu ra:

```text
compare negative to 42: -1
compare 42 to negative: 1
equals same value: true
equals different type: false
negative signum: -1
zero signum: 0
positive signum: 1
absolute negative: 123456789012345678901234567890
negated negative: 123456789012345678901234567890
original unchanged: -123456789012345678901234567890
double negation equals: true
abs min long: 9223372036854775808
Math.abs min long: -9223372036854775808
negative zero signum: 0
describe zero: ZERO
distance: 5
sorted: [-3, 0, 2, 10]
```

Các giá trị trên làm rõ việc `compareTo` chỉ cần so dấu, `equals` so giá trị cùng kiểu, `signum` phân nhánh theo dấu, `abs` tính độ lớn chính xác vượt giới hạn `long`, còn `negate` trả số đối **mà không sửa đầu vào**.

## 12. Lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| `a == b` cho hai `BigInteger` | So tham chiếu; cùng giá trị vẫn có thể `false` | `a.equals(b)` |
| `compareTo(b) == 1` để kiểm tra lớn hơn | Dựa vào trị số thay vì dấu do hợp đồng `Comparable` | `compareTo(b) > 0` |
| `a.subtract(b).intValue()` làm comparator | Mất bit, sai dấu hoặc trả 0 ngoài ý muốn | `a.compareTo(b)` |
| Ép `BigInteger` sang `double` để so | Có thể mất chữ số của số nguyên lớn | So trực tiếp bằng `compareTo` |
| `Math.abs(Long.MIN_VALUE)` rồi gọi `valueOf` | Trị tuyệt đối kiểu `long` đã overflow | `BigInteger.valueOf(Long.MIN_VALUE).abs()` |
| `-someLong` rồi chuyển BigInteger | Số đối có thể overflow tại `Long.MIN_VALUE` | `BigInteger.valueOf(someLong).negate()` |
| `n.abs();` không gán kết quả | `BigInteger` bất biến; `n` không đổi | `n = n.abs();` |
| `n.negate();` không gán kết quả | Không thay đổi số đang lưu | `n = n.negate();` |
| Dùng `negate()` thay `abs()` cho số dương | Đổi thành số âm thay vì giữ không âm | Dùng đúng hàm theo mục tiêu |
| `value.signum()` khi `value == null` | `NullPointerException` | Kiểm tra `null` |
| Dùng `equals(null)` như `compareTo(null)` | Hai API có hành vi khác | `equals(null)` trả `false`; so thứ tự cần giá trị |
| Coi `BigInteger("-0")` khác `ZERO` | BigInteger không có âm 0 | Cả hai là số nguyên 0 |
| Tạo `TreeSet` với comparator chỉ dựa vào `abs` khi cần phân biệt `-a` và `a` | Comparator có thể trả 0 cho hai giá trị khác nhau | Thêm tie-breaker `compareTo` |
| Chọn `BigInteger` làm ID nhưng cần giữ số 0 ở đầu | Chuỗi `"0042"` và `"42"` thành cùng giá trị số | Lưu ID dưới dạng chuỗi nếu cú pháp có ý nghĩa |
| Coi `abs()` hoặc `negate()` luôn cấp phát instance mới | Identity không phải hợp đồng của phép biến đổi giá trị | Không so tham chiếu; dùng `equals` |
| Gọi `longValue()` để kiểm tra miền `long` | Có thể mất dữ liệu mà không báo lỗi | Dùng `longValueExact()` khi cần |

### Quy tắc chọn phương thức nhanh

- **Xếp thứ tự hai số:** `a.compareTo(b)`.
- **Kiểm tra bằng giá trị cùng kiểu:** `a.equals(b)`.
- **Kiểm tra âm, 0, dương:** `a.signum()`.
- **Lấy độ lớn không âm:** `a.abs()`.
- **Đổi dấu:** `a.negate()`.
- **Lấy độ lớn chênh lệch hai số:** `a.subtract(b).abs()`.

## 13. Bài tập có đáp án

**Bài 1.** Với `a = -5`, `b = -2`, `a.compareTo(b)` có dấu nào? Giá trị `a.abs().compareTo(b.abs())` có dấu nào?

**Đáp án:** `a.compareTo(b) < 0` vì `-5 < -2`. Trái lại `a.abs().compareTo(b.abs()) > 0` vì `5 > 2`. So thứ tự số khác so độ lớn.

**Bài 2.** Hai đối tượng được tạo bằng `new BigInteger("42")` riêng biệt có bằng nhau theo `equals` không? Cần dùng `==` không?

**Đáp án:** Có, `equals` là `true`; `==` giữa hai đối tượng vừa tạo riêng là `false`, và không phù hợp để kiểm tra cùng giá trị.

**Bài 3.** `BigInteger.ZERO.signum()` trả gì? `new BigInteger("-0").signum()` có khác không?

**Đáp án:** Cả hai trả `0`; `BigInteger` không có số 0 âm tách biệt.

**Bài 4.** Tại sao `BigInteger.valueOf(Long.MIN_VALUE).abs()` cho kết quả dương đúng, còn `Math.abs(Long.MIN_VALUE)` vẫn âm?

**Đáp án:** `long` không biểu diễn được số dương `2^63`, nên `Math.abs(long)` gặp cận biểu diễn. `BigInteger` không bị giới hạn ở 64 bit và giữ được `9223372036854775808`.

**Bài 5.** Nếu `n` là `BigInteger` âm, sau lệnh `n.negate();` mà không gán kết quả, `n` có đổi không?

**Đáp án:** Không. `BigInteger` bất biến; cần `n = n.negate()` để biến tham chiếu đến số đối.

**Bài 6.** `BigInteger.valueOf(42).equals(Long.valueOf(42L))` trả gì và vì sao?

**Đáp án:** `false`. `BigInteger.equals` yêu cầu đối tượng thuộc `BigInteger` và có cùng giá trị, không so chéo các lớp `Number` chỉ vì cùng giá trị toán học.

**Bài 7.** Viết biểu thức kiểm tra `BigInteger total` không âm, giả sử `total != null`.

**Đáp án:** `total.signum() >= 0` hoặc `total.compareTo(BigInteger.ZERO) >= 0`. Nếu đầu vào có thể `null`, kiểm tra `total != null` trước.

**Bài 8.** Vì sao không dùng `a.subtract(b).intValue()` làm hàm so thứ tự giữa hai số `BigInteger` rất lớn?

**Đáp án:** Phép trích `intValue()` có thể bỏ bit cao, làm dấu và độ lớn của hiệu bị sai; `compareTo` tính thứ tự chính xác trên toàn miền `BigInteger`.

**Bài 9.** Hai khóa `-7` và `7` sẽ ra sao trong `TreeSet<BigInteger>` nếu comparator chỉ là `(a,b) -> a.abs().compareTo(b.abs())`?

**Đáp án:** Comparator trả `0` cho hai khóa dù `equals` trả `false`. `TreeSet` có thể coi chúng là cùng phần tử theo comparator. Nếu cần giữ cả hai, thêm tie-breaker bằng `a.compareTo(b)` hoặc dùng thứ tự tự nhiên.

**Bài 10.** So sánh `n.abs()` và `n.negate()` với `n = 17`, `n = -17`, `n = 0`.

**Đáp án:** Với `17`, `abs = 17`, `negate = -17`; với `-17`, cả hai là `17`; với `0`, cả hai là `0`. Hai phương thức khác nhau về ý nghĩa dù đôi khi cùng kết quả.

**Ghi nhớ:** `compareTo` là thứ tự, `equals` là bằng nhau về giá trị, `signum` chỉ dấu, `abs` lấy độ lớn không âm, `negate` lấy số đối. Tất cả áp dụng trực tiếp cho `BigInteger` lớn mà không cần thu hẹp về primitive; hãy giữ tính bất biến, kiểm tra `null` và không dùng identity hay phép ép mất chính xác thay cho so sánh toán học.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-012">intValue, longValue khác intValueExact, longValueExact</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger: `intValue`, `longValue` khác `intValueExact`, `longValueExact` như thế nào?

Trong Java Core, `java.math.BigInteger` biểu diễn **số nguyên có dấu với độ chính xác tùy ý**, có thể lớn hơn rất nhiều so với giới hạn 32 bit của `int` và 64 bit của `long`. Khi đưa một giá trị `BigInteger` vào API chỉ chấp nhận kiểu nguyên thủy, người học phải chọn rõ **có cho phép mất dữ liệu hay không**.

Cặp phương thức dễ nhầm:

- **`intValue()` / `longValue()`:** chuyển về kiểu hẹp bằng cách **giữ các bit thấp**. Nếu giá trị quá lớn hoặc quá nhỏ, kết quả có thể bị mất bit cao, đổi dấu, hoặc về 0; phương thức **không thông báo lỗi tràn phạm vi**.
- **`intValueExact()` / `longValueExact()`:** yêu cầu giá trị số nguyên **phải nằm trọn trong phạm vi** của kiểu đích. Nếu không thể biểu diễn chính xác, chúng ném **`ArithmeticException`**, không âm thầm cắt bit.

Ví dụ quan trọng:

```java
BigInteger n = new BigInteger("4294967296"); // 2^32.
int unsafe = n.intValue();           // 0: 32 bit thấp đều bằng 0.
long safeLong = n.longValueExact();  // 4294967296L: vẫn nằm trong long.
// int impossible = n.intValueExact(); // Ném ArithmeticException.
```

Đây là mảnh mã có thể đặt trong phương thức của một lớp đã import `java.math.BigInteger`. Bài học đi sâu cơ chế **thu hẹp bit theo bù hai**, biên `Integer.MIN_VALUE/MAX_VALUE`, `Long.MIN_VALUE/MAX_VALUE`, số âm, `2^32`, `2^64`, cách dùng các phương thức `Exact` và cách thiết kế API không âm thầm mất dữ liệu.

## 1. Vì sao phải chuyển `BigInteger` sang `int` hoặc `long`?

`BigInteger` cung cấp phép tính nguyên có độ chính xác tùy ý như `add`, `subtract`, `multiply`, `pow`, `shiftLeft` và hỗ trợ các số vượt xa `long`. Nhưng nhiều API Java khác nhận `int` hoặc `long`:

- Chỉ số, số lượng, kích thước, tham số của một số thư viện yêu cầu `int`.
- Thời gian, giá trị đếm hoặc số byte với miền đủ rộng thường dùng `long`.
- Các giao thức có trường 32 bit hoặc 64 bit cố định bắt buộc chọn một kiểu, phạm vi và quy tắc overflow.
- Cơ sở dữ liệu có cột `INTEGER`/`BIGINT` có giới hạn mà bạn phải tuân thủ.

**Chuyển kiểu không làm thay đổi giá trị trong đối tượng `BigInteger` gốc.** Nó chỉ tạo một kết quả nguyên thủy. Nếu kết quả bị mất bit, dữ liệu chính xác vẫn còn trong đối tượng `BigInteger`, nhưng biến `int`/`long` nhận kết quả sẽ mang **giá trị khác**.

```java
BigInteger big = BigInteger.ONE.shiftLeft(64);
long narrowed = big.longValue(); // 0L
System.out.println(big);         // 18446744073709551616: đối tượng không đổi.
```

Đừng coi việc “chuyển thành công” là bằng chứng số vừa phạm vi. Hai phương thức `intValue()` và `longValue()` được thiết kế để có thể **cắt ngắn có chủ ý**, không phải parser hoặc validator phạm vi.

## 2. Bốn phương thức quan trọng và hợp đồng

| Phương thức | Kết quả | Ngoài phạm vi kiểu đích | Dùng khi |
| --- | --- | --- | --- |
| `BigInteger.intValue()` | `int` 32 bit | Giữ **32 bit thấp**, có thể đổi giá trị và dấu; không báo tràn | Chủ ý lấy low 32 bits |
| `BigInteger.longValue()` | `long` 64 bit | Giữ **64 bit thấp**, có thể đổi giá trị và dấu; không báo tràn | Chủ ý lấy low 64 bits |
| `BigInteger.intValueExact()` | `int` 32 bit | Ném **`ArithmeticException`** | Cần chuyển đúng từng đơn vị trong phạm vi signed int |
| `BigInteger.longValueExact()` | `long` 64 bit | Ném **`ArithmeticException`** | Cần chuyển đúng từng đơn vị trong phạm vi signed long |

Tất cả là **phương thức thực thể** gọi trên đối tượng `BigInteger`. Hai phương thức có hậu tố `Exact` là API của lớp `BigInteger`, không phải phương thức có sẵn của mọi biến khai báo kiểu `Number`.

```java
BigInteger value = BigInteger.valueOf(42);
int a = value.intValue();          // 42
int b = value.intValueExact();     // 42
long c = value.longValue();        // 42L
long d = value.longValueExact();   // 42L
```

**Trong miền biểu diễn được**, cả hai biến thể của cùng kiểu trả **cùng một kết quả**. Sự khác biệt bộc lộ khi giá trị vượt giới hạn signed 32/64 bit.

## 3. Phạm vi số nguyên có dấu phải thuộc lòng

Các giới hạn cố định trong Java:

| Kiểu | Số bit | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | ---: | ---: | ---: |
| `int` | 32 | `-2^31 = -2147483648` | `2^31 - 1 = 2147483647` |
| `long` | 64 | `-2^63 = -9223372036854775808` | `2^63 - 1 = 9223372036854775807` |
| `BigInteger` | Tùy ý | Không có giới hạn `int`/`long` | Chỉ giới hạn tài nguyên và giới hạn triển khai liên quan |

Với `intValueExact()`, điều kiện hợp lệ là:

```text
Integer.MIN_VALUE <= value <= Integer.MAX_VALUE
```

Với `longValueExact()`:

```text
Long.MIN_VALUE <= value <= Long.MAX_VALUE
```

Nếu một `BigInteger` vượt **chỉ phạm vi int** mà vẫn thuộc phạm vi `long`, thì `intValueExact()` thất bại nhưng **`longValueExact()` vẫn thành công**. Ví dụ `2^31` hoặc `2^32` là các số `long` hợp lệ, nhưng không phải `int` có dấu hợp lệ.

Tránh dùng `Integer.MAX_VALUE` và `Long.MAX_VALUE` như thể đó là giá trị dương có độ lớn lớn nhất mà Java nói chung lưu được. Chúng là giới hạn **của kiểu primitive có dấu tương ứng**.

## 4. `intValue()`: 32 bit thấp, không kiểm tra tràn

Ý nghĩa của `intValue()` có thể hiểu theo **bù hai 32 bit**: lấy phần còn lại của giá trị nguyên theo `2^32`, rồi diễn giải mẫu bit theo kiểu `int` signed.

Đặt:

```text
r = value mod 2^32, với 0 <= r < 2^32
```

Khi đó:

```text
intValue(value) =
    r                 nếu 0 <= r < 2^31
    r - 2^32          nếu 2^31 <= r < 2^32
```

Ví dụ:

```java
BigInteger a = BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE);
// a = 2147483648 = 2^31.
int narrowedA = a.intValue(); // -2147483648 = Integer.MIN_VALUE.

BigInteger b = BigInteger.ONE.shiftLeft(32);
// b = 4294967296 = 2^32.
int narrowedB = b.intValue(); // 0.
```

Tại `2^31`, 32 bit thấp là `0x80000000`, được `int` xem là số âm nhỏ nhất. Tại `2^32`, 32 bit thấp toàn 0, nên kết quả là 0. **Đây không phải phép bão hòa về `Integer.MAX_VALUE` hay một exception**.

Bảng minh họa:

| `BigInteger` gốc | `intValue()` | `intValueExact()` |
| ---: | ---: | --- |
| 123 | 123 | 123 |
| 2147483647 | 2147483647 | 2147483647 |
| 2147483648 | **-2147483648** | `ArithmeticException` |
| -2147483648 | -2147483648 | -2147483648 |
| -2147483649 | **2147483647** | `ArithmeticException` |
| 4294967295 | **-1** | `ArithmeticException` |
| 4294967296 | **0** | `ArithmeticException` |
| -4294967296 | **0** | `ArithmeticException` |

Dòng `-2147483649 → 2147483647` cho thấy **giá trị âm nhỏ hơn `Integer.MIN_VALUE`** cũng có thể đổi thành dương sau khi giữ bit thấp.

## 5. `longValue()`: giữ 64 bit thấp, quy tắc tương tự

Với `long`, thay 32 bằng 64:

```text
r = value mod 2^64, với 0 <= r < 2^64

longValue(value) =
    r                 nếu 0 <= r < 2^63
    r - 2^64          nếu 2^63 <= r < 2^64
```

Đây là mô hình toán học của việc giữ **64 bit thấp**, rồi diễn giải theo bù hai signed `long`.

```java
BigInteger a = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
// a = 9223372036854775808 = 2^63.
long narrowedA = a.longValue(); // Long.MIN_VALUE.

BigInteger b = BigInteger.ONE.shiftLeft(64);
// b = 18446744073709551616 = 2^64.
long narrowedB = b.longValue(); // 0L.
```

Hai biến thể `longValue` và `longValueExact` cũng khác nhau ở biên âm. `Long.MIN_VALUE - 1` trở thành `Long.MAX_VALUE` khi cắt 64 bit thấp, chứ không gây lỗi khi gọi `longValue()`.

| `BigInteger` gốc | `longValue()` | `longValueExact()` |
| ---: | ---: | --- |
| 9223372036854775807 | 9223372036854775807 | 9223372036854775807 |
| 9223372036854775808 | **-9223372036854775808** | `ArithmeticException` |
| -9223372036854775808 | -9223372036854775808 | -9223372036854775808 |
| -9223372036854775809 | **9223372036854775807** | `ArithmeticException` |
| 18446744073709551615 | **-1** | `ArithmeticException` |
| 18446744073709551616 | **0** | `ArithmeticException` |

**Điều cần nhớ:** `longValue()` không phải cách an toàn để chuyển một số nguyên quá lớn thành đại diện gần nhất trong miền `long`. Nó là chuyển đổi **low 64 bits**, có thể làm thay đổi hẳn giá trị.

## 6. Một số vừa `long` nhưng không vừa `int`

Xét `4294967296 = 2^32`:

```java
BigInteger n = new BigInteger("4294967296");

int a = n.intValue();           // 0: mất toàn bộ bit có nghĩa phía trên 32 bit.
long b = n.longValue();         // 4294967296L.
long c = n.longValueExact();    // 4294967296L.
// n.intValueExact();           // ArithmeticException.
```

Vì số này có thể lưu chính xác trong `long`, **không cần chuyển nó thành 0 chỉ để ép vào `int`**. Nếu API nhận `long`, dùng `longValueExact()`. Nếu API bắt buộc `int`, cần một **chính sách rõ**: từ chối, chia nhỏ công việc, hoặc phân trang; không âm thầm truyền 0.

Kết quả âm thầm về 0 đặc biệt nguy hiểm với các trường như **số bản ghi cần xử lý**, **giới hạn bộ nhớ**, **độ dài file**, **số lượng đơn hàng** hoặc **định danh**. Một giá trị cực lớn sau cắt bit có thể trở thành 0, âm hoặc số nhỏ nhưng trông hợp lệ.

## 7. `intValueExact()`: chuyển có kiểm tra signed 32 bit

`BigInteger.intValueExact()` chỉ thành công khi giá trị đã nằm trong **`[-2^31, 2^31-1]`**. Nếu ngoài miền, ném `ArithmeticException`.

```java
BigInteger a = BigInteger.valueOf(123);
int ok = a.intValueExact(); // 123.

BigInteger b = BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE);
// int wrong = b.intValueExact(); // ArithmeticException.
```

Điều này bao gồm **tràn dương lẫn tràn âm**. Không phải chỉ số dương lớn mới gây lỗi: `Integer.MIN_VALUE - 1` cũng vượt phạm vi `int`.

Hãy dùng khi việc có một giá trị sai dù chỉ một đơn vị là không thể chấp nhận, ví dụ nhập `BigInteger` vào trường `int` của một thư viện hoặc chuyển kết quả tính chính xác sang hệ thống giới hạn 32 bit.

Có thể xử lý ngoại lệ:

```java
static int requireInt(BigInteger value) {
    try {
        return value.intValueExact();
    } catch (ArithmeticException ex) {
        throw new IllegalArgumentException(
                "Value outside signed 32-bit range", ex);
    }
}
```

Đây là **phương thức ví dụ** đặt bên trong lớp có import `BigInteger`. Tùy hệ thống, bạn có thể giữ nguyên `ArithmeticException`, chuyển thành exception của nghiệp vụ hoặc trả một kiểu kết quả có mã lỗi. Đừng bắt exception rồi **quay về `intValue()`**, vì như vậy mất toàn bộ lợi ích kiểm tra tràn.

## 8. `longValueExact()`: chuyển có kiểm tra signed 64 bit

`BigInteger.longValueExact()` tương tự nhưng kiểm tra miền lớn hơn: **`[-2^63, 2^63-1]`**.

```java
BigInteger x = BigInteger.valueOf(Long.MAX_VALUE);
long ok = x.longValueExact(); // Long.MAX_VALUE.

BigInteger tooLarge = BigInteger.ONE.shiftLeft(63);
// long broken = tooLarge.longValueExact(); // ArithmeticException.
```

Đối với `2^63`, `longValue()` sẽ trả `Long.MIN_VALUE`; `longValueExact()` ném ngoại lệ. Với `-2^63` (chính `Long.MIN_VALUE`) thì phiên bản exact **vẫn thành công**, vì đó là biên dưới hợp lệ.

Nếu giá trị lớn hơn cả 64 bit và hệ thống thực sự cần giữ **số nguyên chính xác**, hãy tiếp tục giữ kiểu `BigInteger` trong API, dùng thư viện/cột dữ liệu phù hợp hoặc thiết kế cách lưu nhiều phần. Không nên giả định `long` đủ rộng chỉ vì dữ liệu “hiếm khi lớn”.

## 9. Phương thức `Exact` kiểm tra giá trị, không kiểm tra chuỗi biểu diễn

`BigInteger` lưu **giá trị số nguyên**, không giữ lại nguyên văn chuỗi đầu vào. Hai chuỗi như `"+00042"` và `"42"` khi được parse hợp lệ đều tạo giá trị 42; `intValueExact()` trả 42 trong cả hai trường hợp.

```java
BigInteger a = new BigInteger("42");
BigInteger b = new BigInteger("+00042");
System.out.println(a.equals(b)); // true.
System.out.println(b.intValueExact()); // 42.
```

Do đó `intValueExact()` **không phải** validator format đầu vào, và nó cũng không phát hiện được số 0 đầu, dấu cộng dư hoặc vi phạm quy tắc của một mã định danh. Nếu đầu vào phải giữ chính xác những ký tự như số 0 đầu, có thể đó là **mã** chứ không phải số cần tính toán; hãy giữ `String` và kiểm tra schema riêng.

Tương tự, chữ `Exact` không có nghĩa phương thức sẽ tự kiểm tra điều kiện nghiệp vụ “số phải không âm” hoặc “số lượng <= 1000”: một `int` âm vẫn có thể biểu diễn chính xác và được trả về bình thường.

## 10. `intValue()` không giống ép `BigInteger` bằng toán tử cast

Trong Java, `BigInteger` là **đối tượng**, không phải primitive `long` hoặc `int`. Bạn không thể viết:

```java
BigInteger big = BigInteger.ONE.shiftLeft(32);
// int value = (int) big; // Không biên dịch: BigInteger không thể cast trực tiếp sang int.
```

Cần gọi một trong các phương thức chuyển đổi:

```java
int lowBits = big.intValue(); // Chủ ý mất bit cao.
long checked = big.longValueExact(); // Yêu cầu chính xác.
```

Mô hình cắt bit của `BigInteger.intValue()` giống **ép hẹp số nguyên nguyên thủy** theo low-order bits về mặt kết quả, nhưng cú pháp và kiểu nguồn hoàn toàn khác. Đừng coi `BigInteger` là lớp bọc cho `long` có thể ép bằng cast thông thường.

Ngoài ra, phương thức `BigInteger.longValue()` **không tự gọi `intValueExact()` hay `longValueExact()`**. Nếu bạn muốn phát hiện mất dữ liệu, phải chọn **phiên bản Exact** một cách tường minh.

## 11. Số âm và cách hoạt động của các bit thấp

Số âm BigInteger có ngữ nghĩa bù hai khi thực hiện các phép toán bit. Khi ép hẹp về 32/64 bit, **không lấy trị tuyệt đối rồi cắt**, mà lấy mẫu bit thấp của **giá trị signed**.

Ví dụ:

```java
BigInteger a = BigInteger.ONE.shiftLeft(32).negate(); // -4294967296.
int bits32 = a.intValue(); // 0.
long whole64 = a.longValueExact(); // -4294967296.
```

Vì `-2^32` chia hết cho `2^32`, 32 bit thấp đều bằng 0. Điều này không biến giá trị gốc thành zero; chỉ có **phần thông tin được chuyển vào `int`** là zero.

Một ví dụ sát cận âm:

```java
BigInteger x = BigInteger.valueOf(Integer.MIN_VALUE)
        .subtract(BigInteger.ONE); // -2147483649.
int narrowed = x.intValue(); // 2147483647.
```

Bản `int` cho một số dương lớn mặc dù gốc âm. Điều này có thể phá logic nghiệp vụ như:

```java
int amount = someBigInteger.intValue();
if (amount > 0) {
    // Có thể vào nhánh dù giá trị BigInteger gốc âm và quá nhỏ.
}
```

Khi giá trị đóng vai trò **độ dài, số lượng, số tiền hoặc giá trị không âm**, nên dùng `intValueExact()` kết hợp kiểm tra **điều kiện >= 0** trước hoặc sau (vì exact chỉ kiểm tra phạm vi, không kiểm tra domain).

## 12. `2^32`, `2^64` và quy luật “về 0”

Hai mốc biên trực quan nhất cho chuyển đổi low bits:

```java
BigInteger p32 = BigInteger.ONE.shiftLeft(32);
BigInteger p64 = BigInteger.ONE.shiftLeft(64);

System.out.println(p32.intValue());  // 0.
System.out.println(p32.longValue()); // 4294967296.

System.out.println(p64.intValue());  // 0.
System.out.println(p64.longValue()); // 0.
```

Tại `2^32`, **32 bit thấp** đều bằng 0, trong khi 64 bit thấp vẫn mang thông tin của `2^32`. Tại `2^64`, cả hai phần thấp 32/64 bit đều bằng 0.

Ngược lại:

```java
BigInteger p64MinusOne = BigInteger.ONE.shiftLeft(64)
        .subtract(BigInteger.ONE);

System.out.println(p64MinusOne.longValue()); // -1.
```

Mẫu 64 bit thấp toàn 1 được kiểu `long` **có dấu** hiểu là -1. Dù một số nguyên dương có thể in `longValue() == -1`, nó không có nghĩa BigInteger “mất dấu” trong phép tính gốc; đây là **cách diễn giải signed của một mẫu bit**.

## 13. Giá trị cực lớn vẫn có thể tạo một kết quả nhỏ

Một BigInteger có thể cực kỳ lớn nhưng khi cắt lấy low 32/64 bits lại thành số nhỏ. Ví dụ:

```java
BigInteger huge = BigInteger.ONE.shiftLeft(100)
        .add(BigInteger.valueOf(123));

System.out.println(huge.intValue());  // 123.
System.out.println(huge.longValue()); // 123.
```

Lý do: `2^100` chia hết cho `2^64`, nên **64 bit thấp** của `2^100 + 123` chỉ còn 123. Đây là minh họa mạnh cho thấy kết quả `intValue()` hoặc `longValue()` **trông hợp lý** không chứng minh chúng phản ánh giá trị thật.

Nếu sau chuyển đổi chương trình kiểm tra `result <= limit` mà không kiểm tra phạm vi **trước đó**, một đầu vào cực lớn có thể trở thành một số nhỏ nằm dưới hạn mức. Dùng phiên bản `Exact` khi **giá trị cần được bảo toàn**, đặc biệt ở các đường đi kiểm tra giới hạn, cấp phát và định tuyến công việc.

## 14. So với `Math.toIntExact(long)`

JDK có phương thức:

```java
int narrowed = Math.toIntExact(someLong);
```

Nó dùng để kiểm tra một giá trị **đã là `long`** có vừa signed `int` không. Không thể truyền trực tiếp `BigInteger` vào `Math.toIntExact`.

Hai cách sau có ý nghĩa khác nhau:

```java
BigInteger big = new BigInteger("4294967296");

// Cách đúng nếu cần BigInteger -> int exact:
// int safe = big.intValueExact(); // ArithmeticException.

// Cách vòng qua long với kiểm tra:
// long first = big.longValueExact(); // Thành công vì 2^32 vừa long.
// int second = Math.toIntExact(first); // ArithmeticException.
```

Với giá trị ngoài signed 64 bit, **phải gọi `longValueExact()`** ở bước đầu. Nếu thay `longValueExact()` bằng `longValue()`, dữ liệu đã bị cắt, và `Math.toIntExact` chỉ kiểm tra giá trị **sau khi bị hỏng**, không khôi phục được giá trị gốc.

Ví dụ nguy hiểm:

```java
BigInteger p64 = BigInteger.ONE.shiftLeft(64);
long brokenLong = p64.longValue(); // 0.
int seemsFine = Math.toIntExact(brokenLong); // 0, KHÔNG có exception.
```

`Math.toIntExact` đúng với đầu vào `long 0`, nhưng đó đã là dữ liệu sai nếu mục tiêu là biểu diễn chính xác `BigInteger 2^64`. Khi nguồn vốn là `BigInteger`, dùng **`intValueExact()` trực tiếp** vừa rõ vừa an toàn.

## 15. So với `byteValueExact()` và `shortValueExact()`

Bên cạnh signed int/long, `BigInteger` cũng có các phương thức chuyển hẹp:

```java
byte a = BigInteger.valueOf(127).byteValueExact(); // 127.
short b = BigInteger.valueOf(32767).shortValueExact(); // 32767.

// BigInteger.valueOf(128).byteValueExact();   // ArithmeticException.
// BigInteger.valueOf(32768).shortValueExact(); // ArithmeticException.
```

Các giới hạn áp dụng:

| Kiểu | Miền signed |
| --- | --- |
| `byte` | -128..127 |
| `short` | -32768..32767 |
| `int` | -2147483648..2147483647 |
| `long` | -9223372036854775808..9223372036854775807 |

Ngược lại, `byteValue()` và `shortValue()` cũng có thể cắt bit khi thu hẹp. Nếu đang parse một trường giao thức có độ rộng cố định, cần phân biệt:

- **Giá trị có dấu** theo phạm vi của kiểu primitive Java.
- **Mẫu bit unsigned** của trường, ví dụ unsigned byte 0..255 hoặc unsigned int 0..2^32-1.

Một giá trị `BigInteger 255` **không vừa signed byte**, nên `byteValueExact()` phải ném exception. Nếu protocol thật sự muốn ghi byte `FF` với ý nghĩa **unsigned 255**, đó là phép **mã hóa unsigned có kiểm tra dải khác**, không phải chuyển exact sang signed `byte`.

## 16. Trường hợp dữ liệu unsigned: không nhầm với signed exact

Java `int` và `long` là kiểu nguyên **có dấu**. `BigInteger.intValueExact()` không có nhiệm vụ cho phép unsigned 32 bit đến `2^32-1`; nó chỉ cho phép signed int tới `2^31-1`.

Ví dụ:

```java
BigInteger unsigned32Max = new BigInteger("4294967295");
int lowBits = unsigned32Max.intValue(); // -1.
long positive = unsigned32Max.longValueExact(); // 4294967295L.
```

Ở đây:

- Nếu cần **giá trị dương chính xác**, giữ BigInteger hoặc chuyển sang `long` bằng `longValueExact()`.
- Nếu cần **mẫu 32 bit** của một trường unsigned, có thể dùng `intValue()` **sau khi xác thực** rằng giá trị nằm trong `0..2^32-1`; phía nhận phải dùng API unsigned như `Integer.toUnsignedLong` hoặc `Integer.toUnsignedString` để diễn giải.
- **Không** dùng `intValueExact()` làm bộ kiểm tra phạm vi unsigned 32 bit, vì +4294967295 hợp lệ unsigned nhưng không vừa signed int.

Tương tự, BigInteger `2^64-1` khi gọi `longValue()` trả -1, nhưng `longValueExact()` ném lỗi, bởi đó không phải signed `long` hợp lệ. Nếu một protocol lưu unsigned 64 bit, cần kiểm tra riêng `0..2^64-1` và định nghĩa rõ cách xử lý **bit pattern long** hoặc giữ kiểu `BigInteger`.

## 17. Đoạn mã kiểm tra signed/unsigned rõ ràng

Với giá trị signed `BigInteger` cần đưa vào `int`:

```java
static int requireSignedInt(BigInteger value) {
    return value.intValueExact();
}
```

Với một trường **unsigned 32 bit** cần giữ mẫu bit trong `int`:

```java
static int requireUnsignedIntBits(BigInteger value) {
    BigInteger max = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
    if (value.signum() < 0 || value.compareTo(max) > 0) {
        throw new ArithmeticException("outside unsigned 32-bit range");
    }
    return value.intValue(); // Có chủ đích: chỉ lấy 32 bit thấp.
}
```

Hai phương thức này là **mảnh mã trong lớp Java**, không phải API có sẵn. Bản unsigned **kiểm tra trước khi cắt bit**, vì sau khi cắt sẽ không còn đủ thông tin để biết giá trị ban đầu có quá lớn hay quá nhỏ không.

Đây là trường hợp điển hình mà `intValue()` **đúng** nếu và chỉ nếu:

1. Mục tiêu thật sự là biểu diễn **32 bit** chứ không phải giá trị signed int.
2. Miền unsigned đã được kiểm tra rõ ràng.
3. Hệ thống sử dụng kết quả theo **ngữ nghĩa unsigned**, không vô tình so sánh với 0 bằng cách signed.

Không nên áp dụng chiến lược unsigned cho số lượng công việc, chỉ số mảng hoặc số tiền chỉ vì muốn tránh exception — những miền đó thường yêu cầu **giá trị signed không âm và vừa phạm vi cụ thể**.

## 18. Kiểm tra trước bằng `compareTo` khi cần thông báo nghiệp vụ

Nếu muốn phân biệt “quá nhỏ” và “quá lớn”, thay vì bắt `ArithmeticException` có thể kiểm tra trực tiếp:

```java
static int requireIntWithMessage(BigInteger value) {
    BigInteger min = BigInteger.valueOf(Integer.MIN_VALUE);
    BigInteger max = BigInteger.valueOf(Integer.MAX_VALUE);
    if (value.compareTo(min) < 0) {
        throw new IllegalArgumentException("below Integer.MIN_VALUE");
    }
    if (value.compareTo(max) > 0) {
        throw new IllegalArgumentException("above Integer.MAX_VALUE");
    }
    return value.intValueExact();
}
```

Đây là một phương thức minh họa, có thể đặt trong lớp đã import `BigInteger`. Trong ứng dụng thực, có thể lưu `min`/`max` thành hằng số static final để tái sử dụng.

**Vì sao vẫn dùng `intValueExact()` sau kiểm tra?** Nó thể hiện rõ tính bất biến cần giữ: phương thức chỉ trả về một `int` nếu giá trị phù hợp. Sau khi đã kiểm tra bằng `compareTo`, `intValue()` cũng cho cùng kết quả, nhưng dùng `Exact` là cách diễn đạt ngữ nghĩa API ít gây nhầm.

Đừng thay điều kiện range bằng `value.bitLength() <= 31` mà chưa phân tích số âm và biên, bởi `BigInteger.bitLength()` có quy tắc riêng đối với giá trị âm. So sánh trực tiếp với `Integer.MIN_VALUE/MAX_VALUE` rõ ràng và khó sai hơn.

## 19. Exception không phải lúc nào cũng là lỗi cần sửa bằng cách bỏ kiểm tra

Hai phương thức `Exact` **ném `ArithmeticException` một cách có chủ đích**. Khi điều đó xảy ra, bạn nên xem lại:

- **Nguồn dữ liệu:** dữ liệu nhập có đúng miền bài toán không?
- **Kiểu đích:** yêu cầu `int`/ `long` là bắt buộc hay có thể giữ `BigInteger`?
- **Giới hạn nghiệp vụ:** một số nguyên tuy vừa `long` nhưng có thể vẫn vượt quota/tài nguyên.
- **Cách biểu diễn:** dữ liệu là số signed hay unsigned bit pattern?
- **Phản hồi cho caller:** nên trả mã lỗi, exception nghiệp vụ hay yêu cầu cung cấp giá trị nhỏ hơn?

Mã tránh dùng:

```java
static int dangerousFallback(BigInteger x) {
    try {
        return x.intValueExact();
    } catch (ArithmeticException ex) {
        return x.intValue(); // SAI nếu yêu cầu là chuyển chính xác.
    }
}
```

Nếu nghiệp vụ có chủ đích cần **wrap modulo 2^32**, hãy chọn `intValue()` ngay từ đầu và giải thích. Nếu nghiệp vụ muốn **clamp/saturate** vào `Integer.MIN_VALUE/MAX_VALUE`, hãy viết chính sách giới hạn riêng sau so sánh; `intValue()` **không thực hiện saturation**.

## 20. Chương trình Java 17+ chạy độc lập

Tạo tệp **`BigIntegerConversionDemo.java`**:

```java
import java.math.BigInteger;

public class BigIntegerConversionDemo {
    private static String exactInt(BigInteger value) {
        try {
            return Integer.toString(value.intValueExact());
        } catch (ArithmeticException ex) {
            return "ArithmeticException";
        }
    }

    private static String exactLong(BigInteger value) {
        try {
            return Long.toString(value.longValueExact());
        } catch (ArithmeticException ex) {
            return "ArithmeticException";
        }
    }

    private static void show(String label, BigInteger value) {
        System.out.println(label + " value=" + value);
        System.out.println(label + " int=" + value.intValue()
                + ", intExact=" + exactInt(value));
        System.out.println(label + " long=" + value.longValue()
                + ", longExact=" + exactLong(value));
    }

    public static void main(String[] args) {
        show("small", BigInteger.valueOf(123));
        show("intMax", BigInteger.valueOf(Integer.MAX_VALUE));
        show("intOver", BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE));
        show("intUnder", BigInteger.valueOf(Integer.MIN_VALUE).subtract(BigInteger.ONE));
        show("twoPow32", BigInteger.ONE.shiftLeft(32));
        show("minusTwoPow32", BigInteger.ONE.shiftLeft(32).negate());
        show("longMax", BigInteger.valueOf(Long.MAX_VALUE));
        show("longOver", BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        show("longUnder", BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE));
        show("twoPow64", BigInteger.ONE.shiftLeft(64));
        show("twoPow64MinusOne", BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));
        show("twoPow100Plus123", BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(123)));

        BigInteger outsideInt = new BigInteger("4294967295");
        boolean fitsInt = outsideInt.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                && outsideInt.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0;
        System.out.println("fitsInt=" + fitsInt);
        System.out.println("mathToIntExactError="
                + exactMathToInt(BigInteger.ONE.shiftLeft(32).longValueExact()));
        System.out.println("bigIntegerUnchanged="
                + BigInteger.ONE.shiftLeft(64).equals(new BigInteger("18446744073709551616")));
    }

    private static String exactMathToInt(long value) {
        try {
            return Integer.toString(Math.toIntExact(value));
        } catch (ArithmeticException ex) {
            return "ArithmeticException";
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 BigIntegerConversionDemo.java
java BigIntegerConversionDemo
```

Stdout chính xác (đã biên dịch và chạy với Java 17):

```text
small value=123
small int=123, intExact=123
small long=123, longExact=123
intMax value=2147483647
intMax int=2147483647, intExact=2147483647
intMax long=2147483647, longExact=2147483647
intOver value=2147483648
intOver int=-2147483648, intExact=ArithmeticException
intOver long=2147483648, longExact=2147483648
intUnder value=-2147483649
intUnder int=2147483647, intExact=ArithmeticException
intUnder long=-2147483649, longExact=-2147483649
twoPow32 value=4294967296
twoPow32 int=0, intExact=ArithmeticException
twoPow32 long=4294967296, longExact=4294967296
minusTwoPow32 value=-4294967296
minusTwoPow32 int=0, intExact=ArithmeticException
minusTwoPow32 long=-4294967296, longExact=-4294967296
longMax value=9223372036854775807
longMax int=-1, intExact=ArithmeticException
longMax long=9223372036854775807, longExact=9223372036854775807
longOver value=9223372036854775808
longOver int=0, intExact=ArithmeticException
longOver long=-9223372036854775808, longExact=ArithmeticException
longUnder value=-9223372036854775809
longUnder int=-1, intExact=ArithmeticException
longUnder long=9223372036854775807, longExact=ArithmeticException
twoPow64 value=18446744073709551616
twoPow64 int=0, intExact=ArithmeticException
twoPow64 long=0, longExact=ArithmeticException
twoPow64MinusOne value=18446744073709551615
twoPow64MinusOne int=-1, intExact=ArithmeticException
twoPow64MinusOne long=-1, longExact=ArithmeticException
twoPow100Plus123 value=1267650600228229401496703205499
twoPow100Plus123 int=123, intExact=ArithmeticException
twoPow100Plus123 long=123, longExact=ArithmeticException
fitsInt=false
mathToIntExactError=ArithmeticException
bigIntegerUnchanged=true
```

Các dòng đáng chú ý:

1. **`small` và `intMax`**: trong miền signed int, cả bốn phương thức trả giá trị tương thích với kích thước kiểu đích.
2. **`intOver`**: +2147483648 trở thành -2147483648 với `intValue()`, nhưng `intValueExact()` báo lỗi; `longValueExact()` vẫn thành công.
3. **`intUnder`**: -2147483649 trở thành +2147483647 khi bị cắt xuống int; phương thức exact từ chối.
4. **`twoPow32`, `minusTwoPow32`**: giá trị gốc ±4294967296 cho `intValue() == 0`, dù vẫn vừa signed long.
5. **`longOver`, `longUnder`**: một bước ngoài giới hạn 64 bit gây chuyển đổi wrap-around ở `longValue()`, trong khi `longValueExact()` ném exception.
6. **`twoPow64`**: BigInteger (2^{64}) có `intValue() == 0` và `longValue() == 0`, không phải vì số gốc là zero.
7. **`twoPow64MinusOne`**: giá trị unsigned 64 bit tối đa cho `longValue() == -1`; phương thức exact từ chối vì ngoài signed long.
8. **`twoPow100Plus123`**: một BigInteger hơn 30 chữ số có `intValue()` và `longValue()` đều bằng 123, cho thấy kết quả bị cắt có thể trông hợp lệ.
9. **`fitsInt=false`**, **`mathToIntExactError=ArithmeticException`** và **`bigIntegerUnchanged=true`**: minh họa range check, API `Math.toIntExact` và tính bất biến của BigInteger.

Trong ví dụ, hàm `exactInt` và `exactLong` **bắt ArithmeticException để in kết quả kiểm thử**. Ứng dụng thật thường cần truyền lỗi hoặc xử lý nó theo quy tắc nghiệp vụ thay vì biến thành chuỗi. Cả chương trình chỉ dùng thư viện chuẩn JDK 17.

## 21. Phân biệt những phương thức khác có chữ “Exact”

Java có nhiều API gắn hậu tố `Exact`, nhưng không nên gộp chúng thành cùng một hành vi:

| API | Nhiệm vụ |
| --- | --- |
| `BigInteger.intValueExact()` | Chuyển BigInteger sang signed int **có kiểm tra dải** |
| `BigInteger.longValueExact()` | Chuyển BigInteger sang signed long **có kiểm tra dải** |
| `Math.toIntExact(long)` | Chuyển **long** sang int với kiểm tra dải |
| `Math.addExact(int,long...)` | Nhóm phương thức cộng primitive có kiểm tra overflow theo overload cụ thể |
| `Math.multiplyExact(...)` | Nhân primitive có kiểm tra overflow |
| `BigDecimal.intValueExact()` | Chuyển **số thập phân** sang int, kiểm tra cả khả năng biểu diễn nguyên chính xác và dải |
| `BigDecimal.longValueExact()` | Chuyển số thập phân sang long, cũng không cho mất phần lẻ |

Với `BigInteger`, **không tồn tại phần thập phân** để phải làm tròn khi chuyển sang `int` hoặc `long`; chỉ cần kiểm tra miền giá trị. Với `BigDecimal`, `intValueExact` còn có thể lỗi khi giá trị như `1.5` không phải số nguyên, dù trị tuyệt đối rất nhỏ. Đây là một khác biệt quan trọng nếu hệ thống dùng biến kiểu `Number` hoặc hoán đổi giữa BigInteger và BigDecimal.

Ví dụ:

```java
BigInteger a = new BigInteger("1");
int x = a.intValueExact(); // 1, hợp lệ.

java.math.BigDecimal b = new java.math.BigDecimal("1.5");
// b.intValueExact() ném ArithmeticException vì không thể bỏ .5 một cách exact.
```

Hậu tố `Exact` cần được hiểu theo **hợp đồng của từng API**, không phải như lời hứa rằng mọi phép toán phía sau cũng chính xác.

## 22. Kiểm thử biên cần có

Để chắc rằng chương trình không cắt dữ liệu ngoài ý muốn, nên kiểm thử ít nhất:

| Test value | Kỳ vọng `intValueExact` | Kỳ vọng `longValueExact` |
| --- | --- | --- |
| 0, 1, -1 | Thành công | Thành công |
| `Integer.MAX_VALUE` | Thành công | Thành công |
| `Integer.MAX_VALUE + 1` | **Lỗi** | Thành công |
| `Integer.MIN_VALUE` | Thành công | Thành công |
| `Integer.MIN_VALUE - 1` | **Lỗi** | Thành công |
| `Long.MAX_VALUE` | **Lỗi** | Thành công |
| `Long.MAX_VALUE + 1` | **Lỗi** | **Lỗi** |
| `Long.MIN_VALUE` | **Lỗi** | Thành công |
| `Long.MIN_VALUE - 1` | **Lỗi** | **Lỗi** |
| `2^64`, `2^100+123` | **Lỗi** | **Lỗi** |

Sau đó test hành vi **`intValue()`/`longValue()` cắt bit** theo đúng policy. Không cần khẳng định nó “gần” giá trị gốc; phép chuyển là modulo theo số bit chứ không phải phép lấy gần nhất.

Với dữ liệu từ giao thức unsigned, thêm test **trường signed dương/âm** và **trường bit pattern unsigned**, ví dụ `2^32-1` hoặc `2^64-1`. Đảm bảo các tầng nhận và gửi thống nhất về độ rộng bit và cách diễn giải dấu.

## 23. Một số tình huống ứng dụng

### A. Số lượng hoặc kích thước

Một endpoint nhận `BigInteger` cho số bản ghi. Nếu gọi `intValue()` trên `2^32`, kết quả 0 có thể khiến chương trình tưởng không có bản ghi để xử lý. Với yêu cầu một số lượng vừa signed `int` và không âm:

```java
static int validCount(BigInteger number) {
    if (number.signum() < 0) {
        throw new IllegalArgumentException("negative count");
    }
    return number.intValueExact();
}
```

**Hai kiểm tra** có hai trách nhiệm: `signum() >= 0` cho ngữ nghĩa số lượng; `intValueExact()` cho khả năng lưu vào kiểu đích.

### B. ID và số nguyên độ dài lớn

Nếu ID lớn hơn signed 64 bit, ép bằng `longValue()` có thể làm hai ID **khác nhau** có cùng 64 bit thấp, tạo va chạm sau chuyển đổi. Nếu ID phải duy nhất, không được dùng phép cắt bit; giữ **`BigInteger`** hoặc dùng `String`/kiểu cơ sở dữ liệu đủ rộng theo thiết kế. Khi giới hạn API chỉ cho `long`, dùng `longValueExact()` để từ chối giá trị không phù hợp.

### C. Mã hóa trường 32 bit unsigned

Một giao thức mô tả trường 32 bit không dấu. Giá trị `4294967295` hợp lệ, nhưng `intValueExact()` không thể biểu diễn thành signed int dương. Có thể chủ động tạo mẫu 32 bit với `intValue()` **sau khi kiểm tra miền unsigned** và khi ghi/đọc phải dùng API unsigned. Sự chuyển đổi này không giống chuyển kiểu số nguyên có dấu exact.

### D. Tính toán nhiều bước

Nếu đang tính một tích có thể vượt `long`:

```java
BigInteger result = BigInteger.valueOf(Long.MAX_VALUE)
        .multiply(BigInteger.valueOf(2));
// Giữ BigInteger trong toàn bộ chuỗi tính toán.
```

Hãy **trì hoãn việc thu hẹp** cho tới boundary thật sự cần primitive, rồi chọn `longValueExact()` nếu không chấp nhận mất dữ liệu. Chuyển sớm sang `longValue()` có thể hủy kết quả chính xác mà BigInteger đã giúp duy trì.

## 24. Những nhầm lẫn thường gặp

| Nhầm lẫn | Sự thật | Cách làm đúng |
| --- | --- | --- |
| `intValue()` tự kiểm tra quá phạm vi | Nó chỉ lấy **32 bit thấp**, không ném lỗi tràn | Dùng `intValueExact()` nếu cần bảo toàn |
| `longValue()` đủ lớn cho mọi BigInteger | Long chỉ 64 bit có dấu | Dùng `longValueExact()` hoặc giữ BigInteger |
| Cắt số lớn sẽ lấy `Integer.MAX_VALUE` | Không có saturation; có thể trả âm/0 | Dùng chính sách clamp riêng nếu cần |
| `2^32` chuyển thành int vẫn là số lớn dương | 32 bit thấp là 0 | Dùng long/BigInteger |
| `2^64` chuyển thành long được 2^64 | 64 bit thấp bằng 0 | `longValueExact()` ném lỗi |
| `intValueExact()` kiểm tra số không âm | Nó chỉ kiểm tra signed range | Kết hợp `signum()` cho miền không âm |
| Chỉ số dương mới bị wrap | Số âm ngoài miền cũng đổi thành số dương hoặc 0 | Test cả hai biên signed |
| `Math.toIntExact(big.longValue())` luôn đủ an toàn | `longValue()` có thể đã cắt bit | Dùng `big.intValueExact()` trực tiếp |
| `intValue()` trả 123 nghĩa BigInteger bằng 123 | Có thể là `2^100+123` | Phân biệt giá trị gốc với low bits |
| `intValueExact()` bảo đảm số chữ số/format input | Nó chỉ kiểm tra giá trị | Validate `String` đầu vào riêng |
| `longValueExact()` thích hợp cho unsigned `2^64-1` | Signed long không chứa được giá trị này | Xác thực unsigned và dùng quy ước bit-pattern |
| Chuyển sang `double` rồi ép int/long sẽ chính xác hơn | Double còn có sai số làm tròn và giới hạn | Tránh floating-point khi cần số nguyên exact |
| Catch `ArithmeticException` rồi gọi `intValue()` | Âm thầm bỏ qua lỗi quan trọng | Trả lỗi có nghĩa hoặc đổi kiểu đích |

## 25. Tự kiểm tra kiến thức

1. **Sự khác biệt ngắn gọn giữa `intValue()` và `intValueExact()` là gì?** Bản thường giữ 32 bit thấp, có thể mất dữ liệu; bản exact từ chối số ngoài signed int bằng `ArithmeticException`.
2. **`BigInteger("4294967296").intValue()` trả gì?** 0, vì `2^32` có 32 bit thấp bằng 0.
3. **Với cùng BigInteger `2^32`, `longValueExact()` có thành công không?** Có, trả `4294967296L` vì số này vừa signed long.
4. **`BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE).intValue()` trả gì?** `Integer.MIN_VALUE`, tức `-2147483648`.
5. **`BigInteger.valueOf(Integer.MIN_VALUE).subtract(BigInteger.ONE).intValue()` trả gì?** `Integer.MAX_VALUE`, tức `2147483647`.
6. **`BigInteger.ONE.shiftLeft(64).longValue()` trả gì?** 0, vì `2^64` có 64 bit thấp bằng 0.
7. **`BigInteger.ONE.shiftLeft(64).longValueExact()` ra sao?** Ném `ArithmeticException` vì `2^64` vượt signed long.
8. **`BigInteger("18446744073709551615").longValue()` cho gì?** -1, vì 64 bit thấp toàn 1.
9. **`longValueExact()` có ném lỗi với `Long.MIN_VALUE` không?** Không. Đây là giới hạn dưới hợp lệ của signed long.
10. **Có thể dùng `intValueExact()` để kiểm tra giá trị thuộc `0..2^32-1` không?** Không; nó chỉ kiểm tra signed int. Unsigned cần validator miền riêng.
11. **Một số cực lớn có `intValue() == 123` có nghĩa nó bằng 123 không?** Không. `2^100+123` cũng trả 123 khi chỉ lấy 32 bit thấp.
12. **`Math.toIntExact(big.longValue())` có luôn an toàn cho BigInteger không?** Không; bước `longValue()` có thể mất dữ liệu trước khi `Math.toIntExact` nhìn thấy kết quả.
13. **Để giảm nguy cơ mất dữ liệu khi chuyển BigInteger cho API nhận long, nên dùng gì?** `longValueExact()`; nếu lỗi, xử lý hoặc thay đổi thiết kế để giữ BigInteger.
14. **Exact chỉ đảm bảo phạm vi hay cả quy tắc nghiệp vụ?** Chỉ bảo đảm giá trị số nằm trong phạm vi signed của kiểu đích. Số không âm, quota và cú pháp input phải kiểm tra riêng.

**Ghi nhớ:** với `BigInteger`, **`intValue()` và `longValue()` là phép lấy các bit thấp**; nếu số quá phạm vi, kết quả có thể thay đổi mạnh mà **không có ngoại lệ**. Ngược lại, **`intValueExact()` và `longValueExact()` yêu cầu biểu diễn chính xác toàn bộ giá trị trong signed 32/64 bit**, nếu không sẽ **ném `ArithmeticException`**. Khi cần bảo toàn giá trị của số nguyên lớn trong hệ thống thật, ưu tiên API `Exact`, kiểm tra phạm vi nghiệp vụ và chỉ cố ý cắt bit khi chính hợp đồng dữ liệu yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-013">Chuyển sang byte&#91;&#93; và vấn đề byte dấu bổ sung</a>

<details>
<summary>Xem chi tiết</summary>

# BigInteger: Chuyển sang `byte[]` và vấn đề byte dấu bổ sung

`java.math.BigInteger` biểu diễn số nguyên có dấu, độ chính xác tùy ý. Khi cần lưu hoặc truyền số dưới dạng nhị phân, phương thức **`BigInteger.toByteArray()`** trả một mảng byte, nhưng **không phải mảng các chữ số độ lớn không dấu**. Đây là **biểu diễn bù hai (*two's complement*) có dấu, theo thứ tự byte cao trước (*big-endian*), có độ dài tối thiểu đủ biểu diễn cả dấu**.

Hệ quả dễ gây ngạc nhiên nhất: một số nguyên **dương** như `128` có thể cần thêm byte `0x00` ở đầu. Byte này **không phải một chữ số thừa vô nghĩa do lỗi API**: nó giữ cho bit dấu của biểu diễn bù hai bằng 0.

```java
import java.math.BigInteger;
import java.util.Arrays;

BigInteger number = BigInteger.valueOf(128);
byte[] bytes = number.toByteArray();

System.out.println(Arrays.toString(bytes)); // [0, -128]
System.out.println(new BigInteger(bytes));  // 128
```

Mảng trên gồm `0x00 0x80`. Vì `byte` trong Java có dấu từ `-128` đến `127`, byte `0x80` được in thành `-128` khi dùng `Arrays.toString`. Nhưng ở cấp bit nó vẫn là `1000_0000`; đặt byte `0x00` ở đầu giúp số dương 128 **không** bị đọc nhầm thành −128.

Bài này giải thích đầy đủ dấu, byte bổ sung, trường hợp âm, tạo số lại từ bytes, chuyển sang định dạng unsigned hoặc fixed-width, byte order và cách kiểm thử round trip trong Java 17+.

## 1. Hiểu ba khái niệm trước khi viết code

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Giá trị số nguyên** | Giá trị toán học của đối tượng `BigInteger` | `128` |
| **Biểu diễn bù hai có dấu** | Chuỗi bit quy định số âm/dương, với bit cao nhất của toàn biểu diễn là bit dấu | `00 80` cho `+128`; `80` cho `-128` |
| **Định dạng wire/protocol** | Quy ước của bên gửi và nhận: signed hay unsigned, big-endian hay little-endian, độ dài cố định hay tối thiểu | unsigned uint8 `80` cũng đại diện 128 |

Ba cách nhìn có thể dẫn đến **cùng byte vật lý nhưng khác giá trị**. Với đúng một byte `0x80`:
- Nếu giải mã dưới dạng **signed two's complement 8-bit**, giá trị là **−128**.
- Nếu giải mã dưới dạng **unsigned 8-bit**, giá trị là **128**.

Không có API nào tự suy ra ý nghĩa đó chỉ từ một mảng byte. **Signedness, byte order và width phải được nêu trong hợp đồng dữ liệu**.

### `BigInteger` là immutable nhưng byte array thì không

Đối tượng `BigInteger` không có thao tác thay đổi giá trị tại chỗ. Trong khi đó, `byte[]` là một object mutable của Java:

```java
BigInteger value = BigInteger.valueOf(128);
byte[] exported = value.toByteArray();
exported[0] = 99;

// value vẫn là 128: sửa byte[] đã lấy ra không sửa BigInteger.
```

Tương tự, khi muốn dùng một mảng byte làm input, hãy hiểu constructor đọc **nội dung** của nó để tạo giá trị. Một mảng byte bạn giữ trong ứng dụng không trở thành immutable chỉ vì nó từng dùng để tạo `BigInteger`.

## 2. Hợp đồng chính xác của `BigInteger.toByteArray()`

Chữ ký trong `java.math.BigInteger`:

```java
public byte[] toByteArray()
```

Kết quả có các đặc tính:
1. **Signed two's complement:** biểu diễn giá trị âm/dương bằng bù hai, không dùng một byte riêng chỉ chứa dấu.
2. **Big-endian:** byte có trọng số lớn hơn xuất hiện trước.
3. **Độ dài tối thiểu:** cần **ít nhất một bit dấu** cùng những bit cần biểu diễn giá trị. Phương thức không nhận tham số độ rộng.
4. **Không cố định 1/2/4/8 byte:** kết quả dài ngắn tùy giá trị.
5. **Số 0 không tạo mảng rỗng:** `BigInteger.ZERO.toByteArray()` cho mảng một byte `00`.
6. **Có thể có byte đầu `00` với số dương**, khi bit cao của byte độ lớn đầu tiên bằng 1.
7. **Không tự chọn độ dài giao thức:** nếu protocol yêu cầu chính xác N byte, ứng dụng phải encode theo hợp đồng đó.

Dạng diễn giải bằng hexadecimal, mỗi cặp ký tự là một byte:

```text
Value       BigInteger.toByteArray() hex
0            00
127          7f
128          00 80
255          00 ff
256          01 00
-1           ff
-128         80
-129         ff 7f
-255         ff 01
-256         ff 00
```

Đây là các kết quả thực tế đã kiểm chứng với Java. Không nhầm cột hex với đầu ra mặc định của `Arrays.toString(byte[])`: phương thức đó in các **byte signed dạng thập phân**, ví dụ `[0, -128]` thay cho `00 80`.

## 3. Tại sao 128 lại là hai byte `00 80`?

Xét một byte, tức 8 bit:

```text
Hex byte         0x80
Binary bits      1000 0000
Signed 8-bit     -128
Unsigned 8-bit   +128
```

Trong bù hai có dấu, bit cao nhất của toàn bộ vùng biểu diễn là bit dấu. Nếu dùng duy nhất byte `80`, bit cao là 1 ⇒ theo signed two's complement, giá trị là **−128**.

Muốn mô tả **+128**, cần một biểu diễn có bit dấu bằng 0:

```text
00          80
00000000    10000000
^
bit dấu 0 của toàn biểu diễn 16 bit
```

Vì vậy `BigInteger.valueOf(128).toByteArray()` là `00 80`. Byte `00` ở trước giữ dấu dương và không đổi độ lớn.

Tương tự:
- `127` có bit đầu byte `7f` bằng 0 ⇒ **một byte** đủ.
- `128` có byte độ lớn `80`, bit đầu bằng 1 ⇒ **hai byte** `00 80`.
- `255` có byte độ lớn `ff`, bit đầu bằng 1 ⇒ **hai byte** `00 ff`.
- `256` có hex `01 00`, bit đầu `01` bằng 0 ⇒ **hai byte** là đủ, không thêm byte `00` thứ ba.

**Không phải mọi số dương nhiều byte đều được thêm byte 00.** Byte dấu bổ sung chỉ cần khi biểu diễn độ lớn tối thiểu, theo ranh giới byte, có bit cao đầu bằng 1.

### Biên 127 → 128

```java
byte[] a = BigInteger.valueOf(127).toByteArray();
byte[] b = BigInteger.valueOf(128).toByteArray();

System.out.println(a.length); // 1
System.out.println(b.length); // 2
```

Giá trị chỉ tăng 1, nhưng số byte tăng từ 1 lên 2. Đây là lý do **không dùng độ dài của `toByteArray()` làm độ rộng dữ liệu unsigned một cách máy móc**.

### Biên `0x7fff` → `0x8000`

Số dương 32767 có thể biểu diễn là `7f ff`. Nhưng 32768 cần `00 80 00`: byte đầu `80` của magnitude có bit cao bằng 1, nên phải thêm `00`. Đây chính là nguyên tắc xảy ra tại **mọi ranh giới 7-bit đầu của biểu diễn signed**, không phải trường hợp riêng của 128.

## 4. Với số âm, bù hai hoạt động như thế nào?

Số âm được mã hóa bằng **bù hai**. Trong vùng bit có độ rộng cố định, một cách hình dung là đảo bit của trị tuyệt đối rồi cộng 1. Nhưng với `BigInteger`, độ rộng không cố định; `toByteArray()` chọn độ dài **tối thiểu** để mô tả số có dấu.

```text
-1    -> ff       (11111111)
-128  -> 80       (10000000)
-129  -> ff 7f    (11111111 01111111)
-255  -> ff 01
-256  -> ff 00
```

Vì sao `-128` vừa một byte nhưng `-129` cần hai?

- Với signed 8-bit, miền là **−128 đến +127**.
- `-128` vừa miền, có biểu diễn `80`.
- `-129` nằm ngoài miền, cần 16 bit. Bù hai 16 bit của `-129` là `ff 7f`.
- Byte `ff` đầu mang phần bit dấu mở rộng cần thiết. Bỏ nó đi sẽ còn `7f` tức +127, **không** phải −129.

**Không được xóa mọi byte `0xFF` đầu mảng âm**. Có lúc `ff` là bit dấu mở rộng **cần thiết** (như `-129`). Chỉ một codec có hiểu rõ giá trị, width và bù hai mới có thể rút gọn hoặc kéo dài biểu diễn mà vẫn bảo toàn số.

### Sign extension khi cần độ dài signed cố định

Nếu muốn đưa `-1` từ một byte `ff` vào vùng signed 4 byte, cần mở rộng dấu bằng byte `ff`:

```text
Signed minimal -1:  ff
Signed 32-bit -1:   ff ff ff ff
```

Tương tự, `+1` muốn biểu diễn signed 32-bit sẽ được đệm `00`:

```text
Signed minimal +1: 01
Signed 32-bit +1:  00 00 00 01
```

Vì vậy **padding signed âm** dùng `FF` chứ không phải `00`. Padding unsigned thì theo giao thức số không âm, thường là `00` phía đầu khi big-endian.

## 5. Constructor `new BigInteger(byte[])` đọc signed two's complement

Constructor:

```java
BigInteger(byte[] val)
```

diễn giải mảng input thành **số nguyên có dấu** theo bù hai và thứ tự big-endian, tương thích với `toByteArray()`.

```java
byte[] negativeEncoding = {(byte) 0x80};
BigInteger a = new BigInteger(negativeEncoding);
System.out.println(a); // -128.
```

Nếu dùng `00 80` thay vì `80`:

```java
byte[] positiveEncoding = {0, (byte) 0x80};
BigInteger b = new BigInteger(positiveEncoding);
System.out.println(b); // 128.
```

Hai mảng có byte thấp giống nhau; chỉ khác **byte đầu quyết định bit dấu của vùng signed**.

### Round trip signed

Với một giá trị `BigInteger` hợp lệ, có thể serialize và parse lại theo cặp tương ứng:

```java
BigInteger original = new BigInteger("-129");
byte[] bytes = original.toByteArray();
BigInteger restored = new BigInteger(bytes);

System.out.println(original.equals(restored)); // true
```

Đây là round trip **giá trị số**, không phải cam kết giữ nguyên chiều dài byte của một input signed *không tối thiểu* đã có trước đó. Chẳng hạn constructor có thể nhận `00 00 80` như +128, nhưng `toByteArray()` sẽ dùng biểu diễn signed tối thiểu `00 80`.

**Cảnh báo:** mảng rỗng không đại diện số 0 trong constructor signed này; xử lý đầu vào empty theo chính sách codec (trong Java, constructor signed từ mảng byte độ dài 0 ném `NumberFormatException`).

## 6. Constructor `new BigInteger(signum, magnitude)` cho độ lớn không dấu

Một overload khác:

```java
BigInteger(int signum, byte[] magnitude)
```

cho phép truyền **dấu tách riêng** và **mảng độ lớn không dấu**, big-endian. `signum` chỉ có thể là `-1`, `0` hoặc `1`.

```java
byte[] oneByte = {(byte) 0x80};

BigInteger signed = new BigInteger(oneByte);
BigInteger unsignedMagnitude = new BigInteger(1, oneByte);

System.out.println(signed);            // -128
System.out.println(unsignedMagnitude); // 128
```

Đây là cách đọc đúng **một byte `80` như số dương unsigned 128**. Nó không thêm `00` vào mảng input để “chữa” dấu, mà trao dấu +1 cho constructor.

Các tình huống:
- `new BigInteger(1, bytes)`: magnitude không âm; dùng dấu dương (trừ trường hợp magnitude bằng 0 sẽ ra 0).
- `new BigInteger(-1, bytes)`: giá trị âm theo độ lớn bytes.
- `new BigInteger(0, bytes)`: chỉ hợp lệ nếu magnitude thật sự bằng 0; signum 0 với magnitude khác 0 bị từ chối.
- `signum` ngoài −1/0/1: không hợp lệ.

`new BigInteger(1, bytes)` đặc biệt hữu ích khi hệ thống nhận **unsigned big-endian byte string** như một số dương. Nhưng với giao thức có độ dài cố định, vẫn cần kiểm tra số byte và miền dự kiến.

### Một byte `ff` có thể là −1 hoặc +255

```java
byte[] ff = {(byte) 0xff};
System.out.println(new BigInteger(ff));    // -1
System.out.println(new BigInteger(1, ff)); // 255
```

Không thể xác định đáp án đúng từ hex `ff` nếu schema chưa quy định signed/unsigned.

## 7. Tại sao bit cao nhất của một byte lại ảnh hưởng tổng độ dài?

Độ dài `toByteArray()` không chỉ phụ thuộc số bit của **độ lớn tuyệt đối**, mà còn phải dành **ít nhất một bit dấu** theo signed two's complement.

Bảng các mốc dương:

| Giá trị | Magnitude hex tối thiểu | `toByteArray()` hex | Độ dài |
| ---: | --- | --- | ---: |
| 0 | `00` | `00` | 1 |
| 1 | `01` | `01` | 1 |
| 127 | `7f` | `7f` | 1 |
| 128 | `80` | `00 80` | 2 |
| 255 | `ff` | `00 ff` | 2 |
| 256 | `01 00` | `01 00` | 2 |
| 32767 | `7f ff` | `7f ff` | 2 |
| 32768 | `80 00` | `00 80 00` | 3 |
| 65535 | `ff ff` | `00 ff ff` | 3 |
| 65536 | `01 00 00` | `01 00 00` | 3 |

Từ bảng có thể thấy **thêm byte dấu không phải lúc nào cũng xảy ra mỗi khi tăng độ rộng magnitude**. Quan trọng là bit cao của byte đầu trong biểu diễn magnitude có bị nhầm thành bit dấu âm hay không.

### `bitLength()` không phải số byte wire

`BigInteger.bitLength()` mô tả độ dài bit theo định nghĩa của lớp (không tính bit dấu trong biểu diễn tối thiểu). Nó không tự chuyển thành chiều dài signed `toByteArray()` hay chiều dài unsigned của protocol nếu chưa hiểu cách cộng bit dấu và làm tròn theo byte.

Đối với **số không âm**, số byte magnitude tối thiểu thường được tính bằng làm tròn lên `bitLength/8`, với trường hợp zero cần chính sách riêng. Nhưng dùng `bitLength()` làm một codec tổng quát mà quên zero, số âm, sign byte và giới hạn width dễ tạo lỗi biên. Nếu cần xuất bytes thật, lấy `toByteArray()` rồi áp dụng **chính sách signedness** có kiểm tra.

## 8. BigInteger.toByteArray() không phải một bộ mã hóa unsigned cố định

Giả sử một giao thức yêu cầu trường **unsigned 64-bit big-endian**, đúng **8 byte**. Giá trị tối đa là:

\[
2^{64}-1 = 18446744073709551615
\]

`BigInteger` biểu diễn được giá trị này. Nhưng:

```java
BigInteger maxUnsigned64 =
        BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
byte[] signedBytes = maxUnsigned64.toByteArray();
```

`signedBytes` gồm **9 byte**:

```text
00 ff ff ff ff ff ff ff ff
```

Byte `00` là byte dấu bảo đảm số dương khi đọc lại bằng constructor signed. Trong wire format **unsigned 64-bit**, trường hợp này cần đúng **8 byte**:

```text
ff ff ff ff ff ff ff ff
```

Nếu gửi thẳng 9 byte sẽ **vi phạm độ dài giao thức**. Nếu phía nhận đọc 8 byte trên bằng `new BigInteger(bytes)`, nó sẽ thấy −1 thay vì `2^64−1`.

**Cách đúng:** ở bên gửi áp dụng codec **unsigned fixed-width có kiểm tra miền**, ở bên nhận dùng `new BigInteger(1, bytes)` và xác thực độ dài. Không thay đổi signedness chỉ bằng cách đổi tên biến.

### Khi nào được bỏ byte `00` đầu?

Chỉ khi **định dạng đích là unsigned magnitude** và bạn xác định byte `00` thật sự là **byte dấu thêm vào cho biểu diễn dương**, không phải một byte có ý nghĩa của một trường fixed-width dài hơn. Để chuyển **giá trị không âm** từ `toByteArray` sang magnitude không dấu tối thiểu, có thể bỏ **chính xác một** byte `00` đầu nếu mảng có ít nhất hai byte và byte đầu bằng `00`.

```java
byte[] signed = value.toByteArray();
int offset = signed.length > 1 && signed[0] == 0 ? 1 : 0;
```

Điều kiện `signed.length > 1` quan trọng: `BigInteger.ZERO.toByteArray()` là `00`. Nếu xóa byte zero duy nhất, bạn sẽ thu được mảng rỗng; giao thức phải **quy định riêng** cách biểu diễn số 0, không để điều này xảy ra tình cờ.

Không dùng thao tác "bỏ tất cả byte đầu bằng 00" nếu protocol có fixed width và các byte 0 đầu chính là phần padding hợp lệ cần giữ.

## 9. Viết codec unsigned fixed-width an toàn

Phương thức dưới đây nhận:
- `value` phải **không âm**.
- `length` là **số byte của wire field**, lớn hơn 0.
- Giá trị phải vừa miền unsigned `[0, 2^(8*length)-1]`.
- Kết quả là **đúng `length` byte big-endian**.
- Nếu giá trị không vừa, **ném exception thay vì cắt bit cao**.

```java
static byte[] toUnsignedFixed(BigInteger value, int length) {
    if (value.signum() < 0 || length <= 0) {
        throw new IllegalArgumentException(
                "requires nonnegative number and positive length");
    }

    byte[] encoded = value.toByteArray();
    int offset = encoded.length > 1 && encoded[0] == 0 ? 1 : 0;
    int count = encoded.length - offset;

    if (count > length) {
        throw new IllegalArgumentException("does not fit unsigned width");
    }

    byte[] out = new byte[length]; // Tự điền 0 ở các byte cao.
    System.arraycopy(encoded, offset, out, length - count, count);
    return out;
}
```

Đây là **method minh họa**, cần đặt trong class có `import java.math.BigInteger`.

### Vì sao kiểm tra miền rất quan trọng?

Nếu muốn uint8, chỉ có đúng một byte; miền phải là `0..255`.

```java
toUnsignedFixed(BigInteger.valueOf(255), 1); // ff
// toUnsignedFixed(BigInteger.valueOf(256), 1); // IllegalArgumentException.
```

Nếu lặng lẽ lấy byte thấp của 256, bạn sẽ gửi `00` và bên nhận nghĩ số là 0. Lỗi này tương tự cast số nguyên vượt miền nhưng khó phát hiện hơn vì bytes trông vẫn hợp lệ.

### Giải mã unsigned big-endian

```java
byte[] wire = {(byte) 0xff};
BigInteger restored = new BigInteger(1, wire);
System.out.println(restored); // 255.
```

Với **fixed-width**, cần xác thực `wire.length` bằng đúng số byte schema chỉ định trước khi parse; `new BigInteger(1,bytes)` tự nó không bắt buộc một chiều dài field cụ thể.

Có thể so sánh round trip:

```java
BigInteger value = new BigInteger("18446744073709551615");
byte[] wire8 = toUnsignedFixed(value, 8);
BigInteger restored = new BigInteger(1, wire8);
System.out.println(value.equals(restored)); // true.
```

Độ rộng 8 byte ở đây là phần của **schema**, không phải độ dài tự nhiên của `BigInteger.toByteArray()`.

## 10. Codec signed fixed-width: phải sign-extend

Nếu giao thức yêu cầu **signed 16-bit big-endian** (2 byte), miền hợp lệ là `−32768..32767`. Ta không chỉ gọi `toByteArray()` rồi đệm 0 một cách mù quáng, bởi số âm cần **đệm `ff`** để bảo toàn dấu.

Ví dụ:
- `−129` có signed minimal `ff 7f`, vừa đúng 2 byte.
- `−1` có signed minimal `ff`, muốn signed 2 byte phải thành `ff ff`.
- `+128` có signed minimal `00 80`, vừa 2 byte.
- `+32767` có `7f ff`, vừa 2 byte.
- `+32768` không vừa signed 16 bit, cần **báo lỗi**, không lấy hai byte thấp `80 00` rồi biến thành −32768.

Một codec minh họa:

```java
static byte[] toSignedFixed(BigInteger value, int length) {
    // Giới hạn 1000 byte chỉ là quota của ví dụ, không phải luật BigInteger.
    if (length <= 0 || length > 1000) {
        throw new IllegalArgumentException("invalid length");
    }

    int bits = length * 8;
    BigInteger limit = BigInteger.ONE.shiftLeft(bits - 1);
    BigInteger min = limit.negate();
    BigInteger max = limit.subtract(BigInteger.ONE);

    if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
        throw new IllegalArgumentException("does not fit signed width");
    }

    byte[] raw = value.toByteArray();
    byte[] out = new byte[length];
    Arrays.fill(out, value.signum() < 0 ? (byte) 0xff : 0);
    System.arraycopy(raw, 0, out, length - raw.length, raw.length);
    return out;
}
```

Cần `import java.util.Arrays;`. Phương thức này minh họa signed two's complement theo **big-endian**, dùng giới hạn miền signed và đệm dấu thích hợp. Quota `1000 byte` giúp ví dụ không nhận length vô lý hoặc phép nhân độ rộng tràn `int`. Hệ thống thật phải chọn quota theo schema, nhu cầu và tài nguyên thực tế.

Khi giải mã signed:

```java
byte[] signedTwoBytes = {(byte) 0xff, (byte) 0x7f};
BigInteger n = new BigInteger(signedTwoBytes);
System.out.println(n); // -129.
```

Nếu thử dùng `new BigInteger(1,signedTwoBytes)` cho cùng bytes, kết quả sẽ **dương** (653... theo số byte); đó là **đổi ý nghĩa giao thức** chứ không phải một cách parse signed khác.

## 11. Big-endian khác little-endian

`BigInteger.toByteArray()` và các constructor từ byte array liên quan sử dụng **big-endian**. Ví dụ số dương 256:

```text
BigInteger.toByteArray() of +256:
01 00
^ byte cao trước
```

Một protocol little-endian có thể muốn `00 01`. Nếu gửi `01 00` mà phía nhận tưởng little-endian, nó có thể đọc thành 1 thay vì 256 hoặc thành giá trị khác tùy width.

**Đừng chỉ đảo byte khi chưa biết signedness và width.** Nếu dùng độ dài tự nhiên của `toByteArray()`, byte dấu bổ sung ảnh hưởng số byte phải đảo. Đối với giao thức fixed-width, thường nên:
1. Encode theo **signed/unsigned width đúng** ở big-endian.
2. Nếu protocol dùng little-endian, đảo thứ tự **đúng N byte**.
3. Bên nhận đảo lại đúng N byte rồi tạo `BigInteger` bằng constructor phù hợp.

`ByteBuffer` có thể được cấu hình `ByteOrder.LITTLE_ENDIAN` cho một số phép đọc/ghi primitive 16/32/64 bit, nhưng `BigInteger` **không** có constructor đơn giản nhận `ByteOrder`. Với số tùy ý độ lớn, codec cần nêu rõ thứ tự byte.

### Không nhầm với thứ tự bit trong từng byte

Big-endian/little-endian mô tả **thứ tự byte**, không tự đảo thứ tự các bit bên trong từng byte. Byte `80` vẫn có mẫu bit `1000_0000`; không phải `0000_0001`.

## 12. Những trường hợp quan trọng khi xử lý zero

`BigInteger.ZERO.toByteArray()` trả `00`, không phải mảng rỗng.

```java
byte[] zero = BigInteger.ZERO.toByteArray();
System.out.println(zero.length); // 1.
System.out.println(zero[0]);     // 0.
```

Với signed fixed width 4 byte, zero là `00 00 00 00`. Với unsigned fixed width 4 byte cũng vậy. Nhưng nếu một giao thức định nghĩa magnitude zero bằng **mảng trống**, bạn không thể tự suy điều đó từ `toByteArray()`; phải có quy tắc ngoại lệ.

Tương tự, `new BigInteger(new byte[]{0})` là số 0. Đừng lẫn `BigInteger.ZERO` với `null` (không có đối tượng) hoặc với “chưa có giá trị” trong schema.

**Nên có test riêng cho số 0** vì nhiều hàm “bỏ byte dấu đầu” dễ làm zero thành mảng rỗng, từ đó decode sai hoặc gây exception.

## 13. Sử dụng `Arrays.toString`: byte Java là signed

`Arrays.toString(byte[])` in các phần tử theo `byte` **có dấu**, không phải hex unsigned:

```java
byte[] bytes = {0, (byte) 0x80, (byte) 0xff};
System.out.println(Arrays.toString(bytes)); // [0, -128, -1].
```

Để xem bytes dưới dạng hex, hãy tách từng byte và **mask `& 0xff`** trước khi định dạng:

```java
static String hex(byte[] bytes) {
    StringBuilder b = new StringBuilder();
    for (byte x : bytes) {
        if (b.length() > 0) {
            b.append(" ");
        }
        b.append(String.format(
                java.util.Locale.ROOT, "%02x", x & 0xff));
    }
    return b.toString();
}
```

Đây là helper phục vụ debug/bài học, không phải phương thức mã hóa byte tùy ý lớn tối ưu nhất. `x & 0xff` chuyển byte về **giá trị không dấu 0..255** trước khi format. Không dùng `Integer.toHexString((int)x)` trực tiếp với byte âm rồi bất ngờ nhận chuỗi dài `ffffff80` do sign extension khi mở rộng lên `int`.

### Khi in giá trị BigInteger ở dạng hex

`big.toString(16)` biểu diễn **giá trị có dấu** (chẳng hạn `-80` cho −128), còn `hex(big.toByteArray())` biểu diễn **mẫu byte signed** (chẳng hạn `80` cho −128). Đây là **hai câu hỏi khác nhau**.

Ví dụ:

```java
BigInteger n = BigInteger.valueOf(-1);
System.out.println(n.toString(16));   // -1
System.out.println(hex(n.toByteArray())); // ff
```

Không dùng chuỗi hex của giá trị có dấu thay thế cho hex của byte stream trong giao thức.

## 14. Java 17+: Chương trình chạy đầy đủ

Lưu thành `ByteArraySignDemo.java`:

```java
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;

public class ByteArraySignDemo {
    static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for (byte x : bytes) {
            if (b.length() > 0) {
                b.append(" ");
            }
            b.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        }
        return b.toString();
    }

    static byte[] toUnsignedFixed(BigInteger n, int length) {
        if (n.signum() < 0 || length <= 0) {
            throw new IllegalArgumentException(
                    "requires nonnegative number and positive length");
        }
        byte[] encoded = n.toByteArray();
        int offset = encoded.length > 1 && encoded[0] == 0 ? 1 : 0;
        int count = encoded.length - offset;
        if (count > length) {
            throw new IllegalArgumentException("does not fit unsigned width");
        }
        byte[] out = new byte[length];
        System.arraycopy(encoded, offset, out, length - count, count);
        return out;
    }

    static byte[] toSignedFixed(BigInteger n, int length) {
        if (length <= 0 || length > 1000) {
            throw new IllegalArgumentException("invalid length");
        }
        int bits = length * 8;
        BigInteger limit = BigInteger.ONE.shiftLeft(bits - 1);
        if (n.compareTo(limit.negate()) < 0
                || n.compareTo(limit.subtract(BigInteger.ONE)) > 0) {
            throw new IllegalArgumentException("does not fit signed width");
        }
        byte[] raw = n.toByteArray();
        byte[] out = new byte[length];
        Arrays.fill(out, n.signum() < 0 ? (byte) 0xff : 0);
        System.arraycopy(raw, 0, out, length - raw.length, raw.length);
        return out;
    }

    public static void main(String[] args) {
        long[] values = {
                0, 127, 128, 255, 256, -1, -128, -129, -255, -256
        };
        for (long n : values) {
            BigInteger x = BigInteger.valueOf(n);
            byte[] b = x.toByteArray();
            System.out.println(n + " -> " + hex(b)
                    + " -> " + new BigInteger(b));
        }

        byte[] one = {(byte) 0x80};
        System.out.println("80 as signed: " + new BigInteger(one));
        System.out.println("80 as unsigned: " + new BigInteger(1, one));
        System.out.println("0080 as signed: "
                + new BigInteger(new byte[] {0, (byte) 0x80}));

        BigInteger max64 =
                BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        System.out.println("2^64-1 signed bytes: "
                + hex(max64.toByteArray()));
        byte[] wire = toUnsignedFixed(max64, 8);
        System.out.println("2^64-1 unsigned 8 bytes: " + hex(wire));
        System.out.println("2^64-1 unsigned decoded: "
                + new BigInteger(1, wire));

        System.out.println("negative -129 signed 2 bytes: "
                + hex(toSignedFixed(BigInteger.valueOf(-129), 2)));
        System.out.println("signed 2 bytes decoded: "
                + new BigInteger(toSignedFixed(
                        BigInteger.valueOf(-129), 2)));

        try {
            toUnsignedFixed(BigInteger.valueOf(-1), 1);
        } catch (IllegalArgumentException ex) {
            System.out.println("negative unsigned: rejected");
        }
        try {
            toUnsignedFixed(BigInteger.valueOf(256), 1);
        } catch (IllegalArgumentException ex) {
            System.out.println("256 in uint8: rejected");
        }
        try {
            toSignedFixed(BigInteger.valueOf(128), 1);
        } catch (IllegalArgumentException ex) {
            System.out.println("128 in int8: rejected");
        }

        System.out.println("signed zero bytes: "
                + hex(BigInteger.ZERO.toByteArray()));
        System.out.println("byte[] reused immutable? "
                + BigInteger.valueOf(128).equals(
                    new BigInteger(BigInteger.valueOf(128).toByteArray())));
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac --release 17 ByteArraySignDemo.java
java ByteArraySignDemo
```

Stdout đã kiểm chứng:

```text
0 -> 00 -> 0
127 -> 7f -> 127
128 -> 00 80 -> 128
255 -> 00 ff -> 255
256 -> 01 00 -> 256
-1 -> ff -> -1
-128 -> 80 -> -128
-129 -> ff 7f -> -129
-255 -> ff 01 -> -255
-256 -> ff 00 -> -256
80 as signed: -128
80 as unsigned: 128
0080 as signed: 128
2^64-1 signed bytes: 00 ff ff ff ff ff ff ff ff
2^64-1 unsigned 8 bytes: ff ff ff ff ff ff ff ff
2^64-1 unsigned decoded: 18446744073709551615
negative -129 signed 2 bytes: ff 7f
signed 2 bytes decoded: -129
negative unsigned: rejected
256 in uint8: rejected
128 in int8: rejected
signed zero bytes: 00
byte[] reused immutable? true
```

Phần demo xác nhận signed two's complement tối thiểu, byte `00` bổ sung ở số dương, signed vs unsigned constructor, byte `FF` mở rộng dấu âm, codec fixed-width kiểm tra miền, zero và khả năng round trip. Ví dụ chỉ dùng các kiểu/method có sẵn trên Java 17+.

## 15. Bảng quyết định: dùng API nào?

| Hợp đồng đầu vào/đầu ra | Cách xử lý thích hợp | Cảnh báo |
| --- | --- | --- |
| Muốn round trip `BigInteger` có dấu dưới dạng bytes tối thiểu | `toByteArray()` ↔ `new BigInteger(bytes)` | Signed two's complement, big-endian, length biến đổi |
| Nhận magnitude **không dấu**, big-endian | `new BigInteger(1, bytes)` | Kiểm tra schema độ dài và miền |
| Xuất magnitude **không dấu**, length tự do | `toByteArray()`, bỏ byte dấu `00` đúng điều kiện | Zero cần chính sách rõ |
| Xuất **unsigned N byte** | Codec `toUnsignedFixed` với kiểm tra | Không âm, kiểm tra fit N byte, padding bằng 00 |
| Xuất **signed N byte** | Codec `toSignedFixed` với kiểm tra | Signed range, negative sign extension FF |
| Nhận **signed N byte** big-endian | Xác thực đúng N byte rồi `new BigInteger(bytes)` | Không được loại bỏ sign byte tùy tiện |
| Nhận **unsigned N byte** big-endian | Xác thực đúng N byte rồi `new BigInteger(1,bytes)` | Cùng bytes có thể đọc ra số khác nếu signed |
| Giao thức little-endian | Hoán đổi thứ tự byte sau khi encode đúng width | Không đảo bit; không quên sign byte |
| Cần log bytes theo hex | `hex(bytes)` với `b & 0xff` | `Arrays.toString` in byte signed decimal |
| Cần hiển thị giá trị số hệ 16 | `big.toString(16)` | Không đồng nghĩa raw byte hex |
| Cần độ chính xác toán học nhưng dữ liệu là String decimal | `new BigInteger(decimalText)` | Không parse qua `double` |
| Cần gửi JSON / CSV | Schema text hoặc codec dữ liệu có cấu trúc | Không coi `byte[]` là text, thường cần base64/hex nếu schema yêu cầu |

### Bài toán thực tế: ID 128 bit và digest

Giả sử một hệ thống đưa số định danh **không dấu 128 bit** lên mạng theo 16 byte big-endian. Nếu dùng `BigInteger.toByteArray()` trực tiếp, ID có bit cao nhất bằng 1 có thể tạo **17 byte** vì byte 00 bổ sung. Bên nhận dự kiến đúng 16 byte sẽ báo sai chiều dài.

Ngược lại, nếu vô điều kiện lấy 16 byte cuối của bất kỳ BigInteger, bạn có nguy cơ **cắt bỏ các bit quan trọng** của một giá trị vượt 128 bit. Cách đúng là xác thực `0 <= value < 2^128` rồi serialize **đúng 16 byte không dấu**.

**Phân biệt digest và số:** giá trị hash 16/32 byte trong nhiều giao thức là **chuỗi byte** chứ không phải một số nguyên có dấu. Nếu yêu cầu giữ đúng mọi byte, bao gồm zero đầu, đôi khi tốt hơn là **giữ trực tiếp `byte[]`/`ByteBuffer` hoặc một kiểu wrapper dữ liệu byte**, không chuyển qua `BigInteger` khi không cần số học.

## 16. Các lỗi phổ biến và cách sửa

| Sai lầm | Kết quả sai | Cách đúng |
| --- | --- | --- |
| Cho rằng `toByteArray()` trả magnitude không dấu | `128` thành `00 80` thay vì `80` | Hiểu signed two's complement; chọn unsigned codec khi cần |
| Xóa mọi byte `00` ở đầu | Có thể mất padding hợp lệ và xử lý sai zero | Chỉ bỏ byte dấu bổ sung theo schema |
| Xóa mọi byte `FF` ở đầu số âm | `-129` thành `+127` | Sign extension phải dựa trên signed range |
| Dùng `new BigInteger(new byte[]{(byte)0x80})` để lấy 128 | Nhận −128 | `new BigInteger(1, bytes)` |
| Dùng `new BigInteger(1,bytes)` với bytes signed âm | Nhận số dương lớn thay vì số âm | Constructor signed `new BigInteger(bytes)` |
| Gửi `toByteArray()` vào uint64 field | Có thể phát sinh 9 byte | Codec unsigned 8 byte, kiểm tra miền |
| Cắt byte cao để vừa trường | Âm thầm đổi giá trị | Kiểm tra fit trước khi encode |
| Đệm số âm bằng `00` | Có thể làm sai dấu hoặc số | Đệm `FF` cho signed negative |
| Không kiểm tra zero | Có thể biến `00` thành mảng rỗng | Định nghĩa zero trong schema |
| Nhầm `Arrays.toString` với hex dump | Nhìn thấy `-128` thay vì `80` | Format byte bằng `& 0xff` |
| Nhầm `big.toString(16)` với `hex(big.toByteArray())` | Value hex khác raw byte hex | Chọn đúng ý nghĩa |
| Tưởng `BigInteger` có byte order tùy chọn | Constructor/toByteArray dùng big-endian | Codec đảo byte khi protocol little-endian |
| Sử dụng signed integer cho chuỗi byte hash | Mất nghĩa độ dài và zero đầu | Giữ raw bytes nếu không cần số học |
| Tưởng mọi `BigInteger.toByteArray()` có 8 byte với long-size | Độ dài tự thay đổi theo giá trị | Fixed-width codec theo schema |
| Đo độ dài bằng `bitLength()/8` rồi không xử lý dấu | Sai ở biên 127/128, 0 | Test biên và thêm quy tắc sign |
| Dùng `byte` Java và nghĩ nó không dấu | `(byte)0xff == -1` | Mask `& 0xff` khi cần 0..255 |
| Không validate byte array input độ dài | Bên nhận có thể chấp nhận dữ liệu sai schema | Kiểm tra đúng N byte trước khi tạo BigInteger |

## 17. Bộ kiểm thử và câu hỏi tự đánh giá

### Kiểm thử ranh giới tối thiểu

Một codec đúng nên có các nhóm test:

1. **Zero:** `0` dưới dạng signed minimal, signed fixed, unsigned fixed.
2. **Ngay dưới và ngay trên bit dấu byte:** `127`, `128`, `255`, `256`.
3. **Số âm sát biên signed byte:** `-1`, `-128`, `-129`, `-255`, `-256`.
4. **Ranh giới 16 bit:** `32767`, `32768`, `-32768`, `-32769`.
5. **Ranh giới 64-bit unsigned:** `2^64-1` (vừa uint64), `2^64` (không vừa uint64).
6. **Miền unsigned:** từ chối số âm, từ chối value vượt `8N` bit.
7. **Miền signed:** từ chối `128` trong int8, chấp nhận `-128`, từ chối `-129` trong int8.
8. **Width:** `N=1`, `N=2`, `N=8`; từ chối length 0 hoặc âm.
9. **Byte order:** big-endian và little-endian phải round trip nếu codec tương ứng.
10. **Byte count:** output chính xác N byte khi schema yêu cầu; không tự thay N.
11. **Immutable:** sửa mảng `toByteArray()` đã nhận không đổi giá trị `BigInteger` gốc.
12. **Protocol integrity:** test với mảng đầu `00` và byte có bit cao bằng 1, không mất dữ liệu.

### Câu hỏi tự kiểm tra

1. Vì sao `BigInteger.valueOf(128).toByteArray()` trả `00 80` thay vì `80`?
2. Hai byte `00 80` biểu diễn giá trị gì theo signed big-endian? Một byte `80` biểu diễn gì theo signed và unsigned?
3. Vì sao số `127` chỉ cần một byte, còn `128` cần hai byte trong `toByteArray()`?
4. Vì sao `BigInteger.valueOf(-128).toByteArray()` là `80` nhưng `-129` là `ff 7f`?
5. Tại sao không được xóa mọi byte `ff` đầu mảng âm?
6. `new BigInteger(bytes)` khác `new BigInteger(1,bytes)` ở hợp đồng nào?
7. Giá trị `2^64-1` có `toByteArray()` dài bao nhiêu? Khi truyền uint64, cần bao nhiêu byte?
8. Nếu protocol yêu cầu signed 16-bit, bạn phải kiểm tra khoảng giá trị nào trước khi encode?
9. Tại sao padding `ff` với số âm khác padding `00` với số dương?
10. Vì sao `BigInteger.ZERO.toByteArray()` trả `00` thay vì mảng rỗng? Nếu wire schema quy định zero khác thì phải làm gì?
11. Làm sao bảo đảm byte array round trip giữ **giá trị** và đồng thời giữ **đúng chiều dài N byte**?
12. Vì sao output `Arrays.toString(new byte[]{(byte)0xff})` là `[-1]`? Nếu muốn hex `ff` phải làm sao?
13. `big.toString(16)` có giống hex dump của `big.toByteArray()` cho mọi số âm không?
14. Một protocol little-endian có thể dùng trực tiếp `toByteArray()` không? Những bước codec nào còn thiếu?
15. Với 16 byte digest/ID có zero đầu, có nên tự động chuyển qua `BigInteger` không? Trường hợp nào nên giữ byte array nguyên vẹn?

## 18. Tham khảo chính thức

- [Java SE 17 — `BigInteger.toByteArray()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html#toByteArray()): mô tả mảng **big-endian two's complement**, độ dài tối thiểu và bit dấu.
- [Java SE 17 — `BigInteger(byte[])`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html#%3Cinit%3E(byte%5B%5D)): constructor nhận biểu diễn signed two's complement.
- [Java SE 17 — `BigInteger(int,byte[])`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html#%3Cinit%3E(int,byte%5B%5D)): constructor nhận signum và magnitude big-endian.
- [Java SE 17 — `Arrays.toString(byte[])`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#toString(byte%5B%5D)): in phần tử byte theo giá trị signed.
- [Java Language Specification `4.2.1 — Integral Types and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.1): kiểu `byte` có dấu 8 bit và miền của các kiểu số nguyên Java.

**Kết luận:** `BigInteger.toByteArray()` biểu diễn số nguyên **có dấu** bằng **bù hai big-endian, độ dài tối thiểu**, nên có thể thêm `00` khi số dương có bit cao của magnitude bằng 1. Với số âm, byte `ff` có thể là **sign extension cần thiết**. Đừng xóa byte dấu hay cắt/pad ngẫu nhiên: hãy xác định **signedness, byte order và fixed width** của định dạng đích, kiểm tra miền, rồi dùng cặp mã hóa/giải mã tương ứng để bảo toàn giá trị và chiều dài dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-014">Kiểm tra số nguyên tố xác suất và ý nghĩa certainty</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra số nguyên tố xác suất và ý nghĩa certainty với BigInteger

> **Java core – numbers · BigInteger**  
> **Mục tiêu:** Hiểu kết quả của `BigInteger.isProbablePrime(int certainty)`, cách diễn giải cận sai số xác suất, các trường hợp biên (đặc biệt `certainty <= 0`), và sự khác nhau giữa kiểm tra một ứng viên, sinh probable prime và chứng minh nguyên tố. Bài sử dụng Java 17+.

## 1. Số nguyên tố và vì sao cần thuật toán xác suất?

Một số nguyên tố là **số nguyên dương lớn hơn 1** có đúng hai ước dương là 1 và chính nó. Ví dụ 2, 3, 5, 7, 11, 17, 97 là các số nguyên tố; 0, 1, −17, 21 và 561 không phải số nguyên tố theo định nghĩa đó.

Với số nhỏ, có thể thử chia bởi các số nguyên từ 2 đến căn bậc hai của n. Nhưng một `BigInteger` có thể chứa số dài hàng trăm hoặc hàng nghìn bit. Việc thử tất cả các ước nhỏ ở miền đó quá tốn kém. Java có phép thử nhanh theo xác suất:

```java
import java.math.BigInteger;

BigInteger n = new BigInteger("1000000007");
boolean probable = n.isProbablePrime(80);

System.out.println(probable); // true
```

Tên gọi **isProbablePrime** rất có chủ đích: phương thức không trả về chứng minh nguyên tố chính xác vô điều kiện. Nó kiểm tra ứng viên theo một mức tin cậy do caller đưa vào.

Ba thao tác dễ nhầm:

| Câu hỏi | API |
| --- | --- |
| Giá trị n đã có có khả năng là nguyên tố không? | `n.isProbablePrime(certainty)` |
| Sinh một giá trị ngẫu nhiên dương có đúng k bit và probably prime? | `BigInteger.probablePrime(k, rnd)` |
| Tìm probable prime đầu tiên lớn hơn n? | `n.nextProbablePrime()` |

Trong các ứng dụng nghiêm ngặt, cần phân biệt **probable prime** với **proven prime**: phép thử xác suất không tự tạo chứng chỉ để người khác xác minh n là nguyên tố.

## 2. Chữ ký và hai kiểu kết luận không đối xứng

Chữ ký trong `java.math.BigInteger`:

```java
public boolean isProbablePrime(int certainty)
```

Khi `certainty > 0`:

- `false`: số **chắc chắn là hợp số** theo hợp đồng Javadoc; phép thử đã loại ứng viên.
- `true`: số **probably prime** với mức tin cậy liên quan đến tham số `certainty`; chưa phải một chứng minh toán học tuyệt đối.

Ví dụ:

```java
System.out.println(BigInteger.valueOf(17)
        .isProbablePrime(80)); // true
System.out.println(BigInteger.valueOf(21)
        .isProbablePrime(80)); // false
```

Không đảo ý nghĩa thành “`false` là *probably composite* còn `true` là *definitely prime*”. Tư duy đúng là: nếu có bằng chứng hợp số, loại ngay; nếu chưa tìm thấy, coi là ứng viên nguyên tố theo xác suất của thuật toán.

**Điều kiện đầu vào:** vì khái niệm nguyên tố chỉ áp dụng cho n ≥ 2, nên một phương thức nghiệp vụ nên kiểm tra điều kiện này ngay cả khi implementation JDK có quy tắc xử lý các số âm.

## 3. certainty đại diện cho điều gì?

Javadoc Java 17 mô tả `certainty` là **mức bất định mà caller sẵn sàng chấp nhận**. Khi `isProbablePrime(certainty)` trả true, tài liệu nêu xác suất n là nguyên tố vượt:

\[
1 - \frac{1}{2^{\mathrm{certainty}}}.
\]

Theo cách diễn giải cận sai số trong mô hình của API, phần rủi ro còn lại được biểu thị bởi `2^-certainty`. certainty càng lớn thì yêu cầu tin cậy càng cao; kiểm tra thường có chi phí tính toán lớn hơn.

| certainty | `2^-certainty` (xấp xỉ) |
| ---: | ---: |
| 1 | 0,5 |
| 10 | 9,77×10^-4 |
| 20 | 9,54×10^-7 |
| 40 | 9,09×10^-13 |
| 80 | 8,27×10^-25 |
| 100 | 7,89×10^-31 |
| 128 | 2,94×10^-39 |

Ví dụ khi truyền 80, tài liệu diễn đạt một mức tin cậy cao, không phải yêu cầu chính xác 80 vòng kiểm tra.

### Không đồng nhất cận xác suất với xác suất hậu nghiệm của một số cố định

Một số nguyên n xác định **hoặc nguyên tố hoặc hợp số**. Nếu n cố định, thuộc tính đó không tự biến thành xác suất theo cách đếm. Sự bất định xuất phát từ lựa chọn thuật toán kiểm tra, các yếu tố xác suất/giả định và dữ liệu ứng viên. Do đó không nên nói:

> “Xác suất toán học chính xác n=... là nguyên tố bằng 1−2^-80.”

Đó không phải suy luận Bayes tự động hợp lệ cho mọi n do đối thủ cố ý chọn. Cách diễn đạt đúng trong thiết kế phần mềm là **dựa vào mức bảo đảm xác suất của API khi phép thử trả probable prime**, đồng thời xét chính sách sinh ứng viên và mô hình rủi ro hệ thống.

### certainty KHÔNG phải số vòng Miller–Rabin

`certainty=80` không bảo đảm có **đúng 80 vòng** Miller–Rabin. Số vòng thực tế và những phép thử bổ sung phụ thuộc implementation, độ dài bit của số và các tối ưu. Không viết mã dựa trên giả định “certainty chính là số lần lặp”.

Dùng `certainty` như tham số chất lượng, không phải tham số điều khiển vòng lặp hay thời gian chạy chính xác. Với yêu cầu kiểm toán thuật toán, xem đặc tả tiêu chuẩn hoặc implementation cụ thể thay vì đoán từ chữ ký.

## 4. Cạm bẫy lớn nhất: certainty <= 0 luôn trả true

Javadoc quy định rõ ràng: nếu `certainty <= 0`, `isProbablePrime` trả **true**, bất kể giá trị kiểm tra.

```java
BigInteger nine = BigInteger.valueOf(9);

System.out.println(nine.isProbablePrime(0));   // true
System.out.println(nine.isProbablePrime(-5));  // true
System.out.println(nine.isProbablePrime(80));  // false
```

Số 9 vẫn là hợp số `3×3`. Kết quả true của hai lời gọi đầu **không chứng minh** hay thậm chí cho mức tin cậy hữu ích về tính nguyên tố.

```java
System.out.println(BigInteger.ZERO.isProbablePrime(0)); // true
System.out.println(BigInteger.ONE.isProbablePrime(-1)); // true
```

**Không dùng 0 hay số âm để nhờ Java tự chọn mức mặc định.** Nếu certainty đọc từ cấu hình, env var, HTTP parameter hay database, phải validate `certainty > 0` trước khi gọi API.

Một helper rõ ý định:

```java
static boolean probablyPrimePositive(BigInteger n, int certainty) {
    if (certainty <= 0) {
        throw new IllegalArgumentException("certainty must be positive");
    }
    if (n == null || n.compareTo(BigInteger.TWO) < 0) {
        return false;
    }
    return n.isProbablePrime(certainty);
}
```

Đây là **phương thức minh họa** cần đặt trong một lớp. Chính sách null có thể được thiết kế là ném lỗi thay vì trả false; quan trọng là phải nói rõ trong API.

## 5. Số âm, 0, 1, 2 và những trường hợp biên

Theo định nghĩa nguyên tố:

| n | Có phải số nguyên tố? |
| ---: | --- |
| −17 | Không |
| −2 | Không |
| 0 | Không |
| 1 | Không |
| 2 | Có |
| 3 | Có |
| 4 | Không |
| 17 | Có |
| 21 | Không |

Với certainty dương, những tình huống thường dùng:

```java
System.out.println(BigInteger.ZERO.isProbablePrime(80)); // false
System.out.println(BigInteger.ONE.isProbablePrime(80));  // false
System.out.println(BigInteger.TWO.isProbablePrime(80));  // true
System.out.println(BigInteger.valueOf(100).isProbablePrime(80)); // false
```

### Tại sao cần kiểm tra n ≥ 2 trước API?

Trong OpenJDK 17, implementation `isProbablePrime` lấy **giá trị tuyệt đối** khi thử số:

```java
BigInteger negative = BigInteger.valueOf(-17);
System.out.println(negative.isProbablePrime(80));
// true trên OpenJDK 17
```

Độ lớn 17 là nguyên tố, nhưng **−17 không phải số nguyên tố dương**. Không lấy raw boolean từ API này làm quyết định nghiệp vụ trước khi enforce miền hợp lệ.

```java
boolean accepted = negative.compareTo(BigInteger.TWO) >= 0
        && negative.isProbablePrime(80);

System.out.println(accepted); // false
```

Quy tắc giá trị âm có thể là chi tiết triển khai; bằng cách validate n ≥ 2, code không phụ thuộc vào hành vi này để định nghĩa nguyên tố.

## 6. So sánh với phép thử chia xác định cho số nhỏ

Một kiểm tra **deterministic** cho số `long` nhỏ:

```java
static boolean trialPrime(long n) {
    if (n < 2) return false;
    if ((n & 1L) == 0L) return n == 2;
    for (long d = 3; d <= n / d; d += 2) {
        if (n % d == 0L) {
            return false;
        }
    }
    return true;
}
```

Hàm thử các ước lẻ đến √n. Điều kiện `d <= n/d` an toàn hơn kiểm tra `d*d <= n` vì phép nhân có thể overflow ở miền long lớn. Nếu thấy ước số, n là hợp số; nếu không có, n là nguyên tố.

```java
System.out.println(trialPrime(97));  // true
System.out.println(trialPrime(561)); // false
```

Nhược điểm: với số lớn gần giới hạn `long`, thử chia có thể chậm; với một BigInteger hàng trăm bit, thử chia thô không khả thi. Đó là lý do các phép thử nguyên tố hiện đại dùng số học modulo và chiến lược xác suất.

**Mục đích của trial division ở đây:** làm oracle nhỏ dễ tin cậy khi kiểm thử probable-prime API, chứ không phải cách sinh nguyên tố kích thước khóa mật mã.

## 7. Fermat và số Carmichael 561: vì sao một test đơn giản chưa đủ

Định lý nhỏ Fermat nói: nếu p nguyên tố và a không chia hết cho p thì:

\[
a^{p-1} \equiv 1 \pmod p.
\]

Java có phương thức `modPow` để tính lũy thừa modulo hiệu quả, không cần tạo một số `a^(p−1)` khổng lồ rồi mới chia:

```java
BigInteger n = BigInteger.valueOf(561);
BigInteger residue = BigInteger.TWO.modPow(
        n.subtract(BigInteger.ONE), n);

System.out.println(residue); // 1
```

Nếu áp dụng phép kiểm tra Fermat một cách ngây thơ, một người có thể cho 561 là nguyên tố. Nhưng:

\[
561 = 3\times11\times17.
\]

561 là **hợp số Carmichael**. Nó vượt qua các phép kiểm tra Fermat với nhiều cơ số, nên một kết quả `a^(n−1) mod n = 1` **không đủ chứng minh n nguyên tố**.

```java
System.out.println(
        BigInteger.valueOf(561).isProbablePrime(80)); // false
```

Thuật toán probable-prime của Java mạnh hơn kiểm tra Fermat một cơ số đơn lẻ trong ví dụ này. Tuy nhiên **không suy ra** rằng mọi hợp số khác đều bắt buộc bị phát hiện bởi mọi phép thử xác suất với certainty hữu hạn.

## 8. Trực giác về Miller–Rabin: tìm chứng cứ hợp số

Một kỹ thuật kiểm tra nguyên tố xác suất quan trọng là *Miller–Rabin*. Với số lẻ n>2, ta phân tích:

\[
n-1 = 2^s d,\quad d\text{ lẻ}.
\]

Sau đó chọn cơ số a và tính các lũy thừa/bình phương modulo n. Nếu chuỗi giá trị vi phạm những tính chất bắt buộc với số nguyên tố, ta phát hiện **chứng cứ hợp số** (*witness*).

- Có witness hợp số → loại ứng viên.
- Không tìm được witness trong các phép thử → ứng viên là *probable prime* theo phép thử đã dùng.
- Nhiều vòng/chiến lược thử phù hợp làm nguy cơ hợp số lọt qua thấp hơn.
- Kết quả pass **không tự tạo chứng chỉ primality**.

Không cần tự triển khai Miller–Rabin để sử dụng `BigInteger.isProbablePrime`. Tự triển khai dễ mắc lỗi ở các trường hợp n=2, n chẵn, phân tích n−1, chọn cơ số, thao tác modulo và randomness.

JDK có thể phối hợp các bước kiểm tra và sàng số nhỏ, tùy phiên bản. Vì thế tránh diễn đạt “`BigInteger.isProbablePrime` chắc chắn chỉ chạy đúng một thuật toán và đúng N vòng” khi API không cam kết.

## 9. Chọn certainty: tin cậy, chi phí và rủi ro nghiệp vụ

`certainty` càng lớn, mức cam kết kiểm tra càng cao và chi phí thường lớn hơn. Không có một giá trị duy nhất phù hợp mọi hệ thống.

Ví dụ một ứng dụng có thể chọn:

```java
private static final int PRIME_CERTAINTY = 100;
```

Nhưng `100` chỉ là một **giá trị minh họa** có mức tin cậy cao; nó không tự bảo đảm toàn bộ quy trình sinh khóa đáp ứng một tiêu chuẩn mật mã cụ thể.

Hãy quyết định theo:

1. **Hậu quả nếu một hợp số bị chấp nhận.**
2. **Số lượng ứng viên** mà hệ thống chấp nhận trong cả vòng đời.
3. **Độ dài bit** của ứng viên và chi phí CPU/bộ nhớ.
4. **Yêu cầu kiểm toán:** probable prime có đủ, hay cần chứng chỉ nguyên tố?
5. **Nguồn sinh và phân phối ứng viên**, nhất là nếu dữ liệu có thể do đối thủ tạo.
6. **Phiên bản JDK, thư viện hoặc chuẩn kỹ thuật** cần tuân thủ.

Nhiều lời gọi trong một dịch vụ tạo ra một bài toán **rủi ro tổng hợp**. Không được suy ra rằng lặp n lần luôn cho xác suất lỗi bằng đúng `n×2^-certainty` hoặc các lần kiểm tra độc lập; phải xét giả định về các biến cố. Trong các lập luận bảo thủ có thể dùng *union bound* cho cận lỗi khi các tiền đề phù hợp.

**Không dùng certainty rất lớn tùy tiện** với mục tiêu “bảo đảm tuyệt đối”: tính xác suất không trở thành chứng minh chỉ vì tham số lớn; chi phí tài nguyên có thể tăng.

## 10. isProbablePrime kiểm tra; probablePrime sinh ứng viên

Hai lời gọi trông gần giống nhưng phục vụ hai mục tiêu:

```java
// Kiểm tra n đã có:
boolean checked = n.isProbablePrime(80);

// Tạo một ứng viên nguyên tố ngẫu nhiên có 64 bit:
BigInteger generated = BigInteger.probablePrime(
        64, new java.security.SecureRandom());
```

`BigInteger.probablePrime(int bitLength, Random rnd)` trả **số dương có đúng độ dài bit được yêu cầu** và là probable prime. Javadoc Java 17 quy định xác suất kết quả sinh ra là hợp số **không vượt quá `2^-100`**.

- `bitLength` phải ≥2 và nằm trong giới hạn implementation.
- Giá trị cụ thể phụ thuộc nguồn ngẫu nhiên; đừng gán một stdout số cố định cho nó.
- Tham số có kiểu `Random` nhưng nếu dùng để tạo khóa, hãy dùng `SecureRandom`.
- API chỉ sinh *probable prime* chứ không cấp chứng chỉ chứng minh nguyên tố.

Với Java:

```java
import java.math.BigInteger;
import java.security.SecureRandom;

SecureRandom random = new SecureRandom();
BigInteger p = BigInteger.probablePrime(64, random);

System.out.println(p.bitLength()); // 64
System.out.println(p.signum() > 0); // true
System.out.println(p.testBit(0));   // true: prime 64 bit là số lẻ
```

Ba thuộc tính trên ổn định dù giá trị p ngẫu nhiên thay đổi mỗi lần chạy.

## 11. Ba constructor/factory dễ nhầm

| Cách tạo | Ý nghĩa |
| --- | --- |
| `BigInteger.probablePrime(64, rnd)` | Sinh probable prime dương có đúng 64 bit |
| `new BigInteger(64, 100, rnd)` | Sinh probable prime dương có đúng 64 bit, certainty 100 |
| `new BigInteger(64, rnd)` | Sinh một số không âm ngẫu nhiên có tối đa 64 bit; **không hứa prime** |

Constructor ba tham số:

```java
BigInteger p = new BigInteger(
        64, 100, new SecureRandom());
```

cho phép đặt certainty. Javadoc khuyến nghị **dùng `probablePrime`** trừ khi có lý do cụ thể cần tự chỉ định certainty.

Constructor hai tham số:

```java
BigInteger randomBits = new BigInteger(
        64, new SecureRandom());
```

chỉ tạo số ngẫu nhiên không âm; nó có thể chẵn, có thể nhỏ hơn 2 và hoàn toàn có thể là hợp số. **Không** coi nó là probable prime.

Đặc biệt, với constructor random-bits, số bit yêu cầu là **giới hạn tối đa**, không bảo đảm `bitLength() == 64`; bit cao nhất có thể bằng 0.

## 12. nextProbablePrime: tìm ứng viên đầu tiên lớn hơn n

Một phương thức tiện ích khác:

```java
BigInteger start = BigInteger.valueOf(100);
BigInteger next = start.nextProbablePrime();

System.out.println(next); // 101
```

`nextProbablePrime()` tìm probable prime **lớn hơn** n, không bao gồm chính n:

```java
System.out.println(
        BigInteger.valueOf(2).nextProbablePrime()); // 3
```

Javadoc Java 17 mô tả cận xác suất hợp số không vượt `2^-100` với kết quả tìm được; đồng thời phương thức **không bỏ qua một số nguyên tố thực sự** nằm giữa n và kết quả.

Đối với số âm, Java ném `ArithmeticException`:

```java
try {
    BigInteger.valueOf(-5).nextProbablePrime();
} catch (ArithmeticException ex) {
    System.out.println("negative search rejected");
}
```

Không nhầm với `isProbablePrime`: một hàm **phân loại số hiện tại**, hàm kia **tìm ứng viên lớn hơn**.

### Tính ngẫu nhiên không được suy ra từ nextProbablePrime

Nếu đầu vào `start` dự đoán được, `nextProbablePrime()` cũng có kết quả dự đoán được. Đừng dùng hàm tìm số kế tiếp như cách tự động tạo khóa mật mã khó đoán mà không có quy trình chọn ứng viên ngẫu nhiên và kiểm tra khóa đầy đủ.

## 13. SecureRandom quan trọng với sinh nguyên tố cho mật mã

API sinh probable prime nhận một `Random`, nhưng loại Random không phải lúc nào cũng đáp ứng tiêu chuẩn an ninh:

```java
import java.util.Random;
import java.security.SecureRandom;

// Chỉ phục vụ demo không mật mã:
// Random predictable = new Random(123);

// Khi dùng cho ứng dụng nhạy cảm:
SecureRandom secure = new SecureRandom();
BigInteger prime = BigInteger.probablePrime(128, secure);
```

`Random` thông thường không phải bộ sinh số ngẫu nhiên mật mã. Seed cố định khiến kết quả tái lập và dễ dự đoán; không phù hợp khi số sinh ra cần bí mật.

**Một probable prime chưa phải một khóa RSA hoàn chỉnh.** Quy trình RSA còn yêu cầu độ dài khóa, số mũ công khai, quan hệ giữa các nguyên tố p/q, tính toán khóa, bảo vệ bí mật, kiểm tra tiêu chuẩn và chống lỗi triển khai. Trong phần mềm thật nên dùng `KeyPairGenerator` hoặc thư viện mật mã đã được thẩm định.

Không log seed, p, q hoặc vật liệu khóa bí mật. Bài học chỉ nên in ra các **thuộc tính không nhạy cảm** của giá trị sinh ra.

## 14. Nhận dữ liệu đầu vào không tin cậy

Không nên nhận một chuỗi thập phân hàng triệu ký tự, một `certainty` rất lớn và rồi gọi `isProbablePrime` ngay trong HTTP request mà không giới hạn.

Các biện pháp thực tế:

- Giới hạn **độ dài chuỗi số** trước khi parse.
- Xác thực cú pháp với `BigInteger(String)` và xử lý `NumberFormatException`.
- Giới hạn `n.bitLength()` phù hợp bài toán.
- Đảm bảo `n >= 2` trước khi coi giá trị là ứng viên nguyên tố.
- Đảm bảo `certainty > 0` và không lớn hơn giới hạn policy cho phép.
- Giới hạn thời gian xử lý, số lời gọi đồng thời, CPU và bộ nhớ.
- Ghi rõ trạng thái lỗi input, hợp số, probable prime; không gom hết thành một boolean sai lệch.

Ví dụ helper:

```java
static boolean boundedPrimalityTest(
        BigInteger n, int certainty, int maxBits) {
    if (n == null || certainty <= 0 || maxBits < 2) {
        throw new IllegalArgumentException("Invalid input/policy");
    }
    if (n.compareTo(BigInteger.TWO) < 0) {
        return false;
    }
    if (n.bitLength() > maxBits) {
        throw new IllegalArgumentException("Too many bits");
    }
    return n.isProbablePrime(certainty);
}
```

Đây là phương thức minh họa. Giá trị `maxBits` là giới hạn nghiệp vụ, không phải ranh giới nội tại cố định của BigInteger. Production còn cần policy riêng cho chuỗi nhập, logging và giới hạn tài nguyên.

## 15. Chương trình Java 17+ hoàn chỉnh

Tạo tệp **`BigIntegerPrimalityDemo.java`**:

```java
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;

public class BigIntegerPrimalityDemo {
    private static final int CERTAINTY = 80;

    private static boolean acceptable(BigInteger n, int certainty) {
        Objects.requireNonNull(n, "candidate");
        if (certainty <= 0) {
            throw new IllegalArgumentException(
                    "certainty must be positive");
        }
        if (n.compareTo(BigInteger.TWO) < 0) {
            return false;
        }
        return n.isProbablePrime(certainty);
    }

    private static boolean trialPrime(long n) {
        if (n < 2) return false;
        if ((n & 1L) == 0L) return n == 2;

        for (long d = 3; d <= n / d; d += 2) {
            if (n % d == 0) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        for (String text : List.of(
                "0", "1", "2", "17", "21", "561", "1000000007")) {
            BigInteger n = new BigInteger(text);
            boolean result = acceptable(n, CERTAINTY);
            System.out.println(text + " -> "
                    + (result ? "probably prime" : "composite"));
        }

        BigInteger nine = BigInteger.valueOf(9);
        System.out.println("certainty 0 for 9 = "
                + nine.isProbablePrime(0));
        System.out.println("certainty -3 for 0 = "
                + BigInteger.ZERO.isProbablePrime(-3));

        try {
            acceptable(nine, 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("validated certainty = rejected");
        }

        BigInteger negative = BigInteger.valueOf(-17);
        System.out.println("negative -17 raw test = "
                + negative.isProbablePrime(CERTAINTY));
        System.out.println("negative -17 validated = "
                + acceptable(negative, CERTAINTY));

        BigInteger n561 = BigInteger.valueOf(561);
        BigInteger fermat = BigInteger.TWO.modPow(
                n561.subtract(BigInteger.ONE), n561);
        System.out.println("Fermat 2^560 mod 561 = " + fermat);

        System.out.println("trial division 97 = " + trialPrime(97));
        System.out.println("trial division 561 = " + trialPrime(561));
        System.out.println("next probable after 100 = "
                + BigInteger.valueOf(100).nextProbablePrime());

        try {
            BigInteger.valueOf(-5).nextProbablePrime();
        } catch (ArithmeticException ex) {
            System.out.println("negative search = rejected");
        }

        SecureRandom rnd = new SecureRandom();
        BigInteger generated = BigInteger.probablePrime(64, rnd);
        System.out.println("generated bit length = "
                + generated.bitLength());
        System.out.println("generated positive odd = "
                + (generated.signum() > 0 && generated.testBit(0)));
    }
}
```

Chạy trên môi trường có JDK 17 trở lên:

```bash
javac --release 17 BigIntegerPrimalityDemo.java
java BigIntegerPrimalityDemo
```

**Stdout dự kiến trên OpenJDK 17 (phần ngẫu nhiên chỉ in thuộc tính ổn định):**

```text
0 -> composite
1 -> composite
2 -> probably prime
17 -> probably prime
21 -> composite
561 -> composite
1000000007 -> probably prime
certainty 0 for 9 = true
certainty -3 for 0 = true
validated certainty = rejected
negative -17 raw test = true
negative -17 validated = false
Fermat 2^560 mod 561 = 1
trial division 97 = true
trial division 561 = false
next probable after 100 = 101
negative search = rejected
generated bit length = 64
generated positive odd = true
```

### Giải thích đầu ra

1. 0 và 1 không nguyên tố; 2 và 17 nguyên tố; 21 và 561 là hợp số.
2. certainty không dương khiến `isProbablePrime` trả true cho cả hợp số; wrapper có validation từ chối tình huống ấy.
3. OpenJDK có thể thử độ lớn của −17 và trả true; wrapper yêu cầu n≥2 nên trả false.
4. Carmichael 561 cho `2^560 mod 561 = 1` dù là hợp số; kiểm tra probable prime mức cao vẫn nhận diện nó là composite.
5. `nextProbablePrime` trả 101 khi tìm số lớn hơn 100; đối số âm của phương thức này bị từ chối.
6. `SecureRandom` sinh giá trị không cố định, nhưng probablePrime(64,...) bảo đảm đúng 64 bit và một số nguyên tố ứng viên dài 64 bit phải lẻ.

## 16. Những hiểu nhầm thường gặp

| Hiểu nhầm | Điều đúng |
| --- | --- |
| “`isProbablePrime(100) == true` là một chứng minh prime” | Đó là probably prime theo mức tin cậy API |
| “`false` chỉ là probably composite” | Với certainty>0, Javadoc nói false là definitely composite |
| “certainty 0 yêu cầu mặc định” | certainty≤0 trả true ngay |
| “certainty 80 là đúng 80 vòng Miller–Rabin” | API không cam kết số vòng cụ thể |
| “Đọc được 2^-80 là biết chính xác xác suất hậu nghiệm của một n cố định” | Phải xét mô hình xác suất/ứng viên, không diễn giải máy móc |
| “Số âm mặc định không bao giờ được isProbablePrime chấp nhận” | OpenJDK có thể thử theo trị tuyệt đối; tự kiểm tra n≥2 |
| “Số 1 là prime vì không có ước nhỏ” | 1 không nguyên tố theo định nghĩa |
| “Fermat a^(n−1) mod n = 1 chứng minh prime” | Có hợp số Carmichael như 561 |
| “probablePrime và isProbablePrime làm cùng một việc” | Một hàm sinh, một hàm kiểm tra |
| “new BigInteger(64, rnd) sinh probable prime” | Constructor này sinh số ngẫu nhiên không âm, có thể hợp số |
| “nextProbablePrime có thể trả lại chính số đang xét” | Trả số lớn hơn n |
| “Lặp lời gọi isProbablePrime nhiều lần luôn là các phép thử độc lập” | API không đưa ra cam kết như vậy |
| “SecureRandom mặc định đảm bảo mọi thao tác sinh RSA hợp chuẩn” | Cần quy trình khóa và tiêu chuẩn đầy đủ |
| “Tăng certainty vô hạn biến probability thành proof” | Không, nó vẫn là kiểm tra xác suất theo contract |
| “BigInteger loại bỏ mọi nguy cơ DoS” | CPU, RAM và giới hạn implementation vẫn hữu hạn |

## 17. Bài tập tự kiểm tra và lời giải

**Bài 1.** `BigInteger.valueOf(9).isProbablePrime(0)` trả gì? Có khiến 9 thành nguyên tố không?  
**Đáp án:** Trả true vì certainty≤0; 9 vẫn là hợp số. Cần validation.

**Bài 2.** `BigInteger.ONE.isProbablePrime(80)` và `BigInteger.TWO.isProbablePrime(80)` trả gì?  
**Đáp án:** Lần lượt false và true vì 1 không nguyên tố, 2 nguyên tố.

**Bài 3.** Vì sao không suy rằng `certainty=80` có đúng 80 vòng Miller–Rabin?  
**Đáp án:** Certainty biểu thị độ tin cậy yêu cầu; thuật toán, số vòng và các bước bổ sung là chi tiết triển khai.

**Bài 4.** Vì sao `2^560 mod 561=1` không chứng minh 561 nguyên tố?  
**Đáp án:** 561=3×11×17, là số Carmichael vượt qua phép kiểm tra Fermat với cơ số 2.

**Bài 5.** Giải thích khác biệt `BigInteger.probablePrime(64,rnd)` và `new BigInteger(64,rnd)`.  
**Đáp án:** Cái đầu sinh probable prime dương có đúng 64 bit; cái sau sinh số không âm có tối đa 64 bit, có thể hợp số.

**Bài 6.** Nếu cần số nguyên tố cho RSA, tại sao chỉ gọi `isProbablePrime(100)` chưa đủ?  
**Đáp án:** Cần nguồn ngẫu nhiên an toàn, lựa chọn p/q, tham số khóa, kiểm tra tiêu chuẩn và thư viện mật mã bảo mật.

**Bài 7.** `BigInteger.valueOf(100).nextProbablePrime()` trả gì?  
**Đáp án:** 101, vì cần tìm giá trị probable prime **lớn hơn** 100.

**Bài 8.** Một ứng dụng phải kiểm tra số nguyên tố dương nhưng n=−17. Kết quả đúng theo nghiệp vụ?  
**Đáp án:** False; tự kiểm tra n≥2 trước khi thử probable-prime dù implementation có thể thử trị tuyệt đối.

**Bài 9.** Nếu `isProbablePrime(100)` trả false cho n≥2, có nên thử lại 100 lần để chứng minh n prime không?  
**Đáp án:** Không. False nghĩa n đã bị phát hiện là hợp số theo API; lặp lại không đảo ngược bản chất toán học.

**Bài 10.** Khi cần chứng chỉ nguyên tố có thể xác minh độc lập, một giá trị `true` có đủ không?  
**Đáp án:** Không. Cần thuật toán/chứng chỉ primality proof phù hợp.

**Bài 11.** Tại sao cần giới hạn certainty đầu vào, dù tăng certainty có vẻ tốt?  
**Đáp án:** Vì CPU/chi phí có thể tăng và kẻ tấn công có thể yêu cầu mức kiểm tra cực lớn; giá trị phải theo policy.

**Bài 12.** Khi output từ `probablePrime(64,new SecureRandom())` thay đổi mỗi lần chạy, có phải lỗi không?  
**Đáp án:** Không. Đó là hành vi ngẫu nhiên bình thường; kiểm tra `bitLength()==64`, dương và lẻ thay vì so sánh giá trị số cố định.

## 18. Checklist khi áp dụng trong chương trình thật

1. **Xác định yêu cầu**: probable prime có đủ hay bắt buộc primality proof?
2. **Kiểm tra n ≥ 2** để tuân định nghĩa nguyên tố dương.
3. **Kiểm tra certainty > 0**; không dùng 0 hoặc âm làm mặc định.
4. **Đọc đúng true/false**: true là probable; false là composite theo contract.
5. **Không gán certainty bằng số vòng Miller–Rabin**.
6. **Không diễn giải máy móc cận 2^-certainty** như xác suất hậu nghiệm tuyệt đối cho n bất kỳ.
7. **Kiểm thử các giá trị biên**: âm, 0, 1, 2, chẵn, 561, số nguyên tố đã biết.
8. **Phân biệt API kiểm tra, sinh và tìm**: isProbablePrime, probablePrime và nextProbablePrime.
9. **Với dữ liệu mật mã:** dùng SecureRandom và các API/protocol sinh khóa chuẩn.
10. **Giới hạn tài nguyên**: độ dài input, bitLength, certainty và thời gian xử lý.
11. **Tránh in giá trị prime sinh ngẫu nhiên trong stdout tham khảo**; chỉ in đặc tính ổn định.
12. **Khi cần chứng minh:** dùng thuật toán chứng minh nguyên tố và chứng chỉ kiểm tra riêng.

### Tài liệu tham khảo

- [BigInteger — Javadoc Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html)
- [SecureRandom — Javadoc Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html)
- [Random — Javadoc Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html)

**Ghi nhớ:** `BigInteger.isProbablePrime(certainty)` kiểm tra **xác suất**, không cung cấp chứng chỉ nguyên tố. Với certainty dương, `false` là hợp số; `true` là probable prime theo mức tin cậy của API. Với certainty≤0, phương thức **trả true ngay**, nên tuyệt đối phải validate tham số. Sinh ứng viên cho mục đích bảo mật còn cần nguồn ngẫu nhiên và quy trình khóa phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-integer-015">Giới hạn tài nguyên dù không có độ rộng cố định như long</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn tài nguyên của BigInteger dù không có độ rộng cố định như long

## 1. Mục tiêu: số nguyên chính xác tùy ý không có nghĩa là vô hạn tài nguyên

`java.math.BigInteger` là lớp biểu diễn **số nguyên có dấu với độ chính xác tùy ý** (*arbitrary-precision signed integer*). Khác với `long` cố định 64 bit, `BigInteger` có thể chứa số nguyên hàng trăm, hàng nghìn hoặc rất nhiều chữ số mà không bị **tràn số theo modulo 2⁶⁴** như toán tử primitive.

Nhưng “tùy ý” **không phải “vô hạn”**. Mỗi chương trình Java vẫn chạy trong một JVM hữu hạn RAM, heap, CPU, thời gian, tài nguyên thread và giới hạn cài đặt. Một phép toán trên `BigInteger` quá lớn có thể:

- Cần bộ nhớ cho đối tượng, mảng dữ liệu và các kết quả tạm.
- Mất nhiều thời gian CPU để cộng, nhân, chia, nâng lũy thừa hoặc chuyển đổi sang chuỗi.
- Thất bại do giới hạn kích thước dữ liệu của implementation, hoặc gây `OutOfMemoryError`.
- Chiếm tài nguyên chung khiến các yêu cầu khác của dịch vụ chậm hoặc bị từ chối.
- Tạo nguy cơ từ dữ liệu không đáng tin cậy, nhất là một chuỗi đầu vào dài bất thường hoặc một số mũ do người dùng quyết định.

Bài học giúp bạn phân biệt **giới hạn của kiểu dữ liệu** với **ngân sách tài nguyên của ứng dụng**, ước lượng độ lớn bằng `bitLength()`, bảo vệ các thao tác tăng trưởng nhanh và viết ví dụ Java 17+ an toàn.

## 2. long cố định 64 bit, còn BigInteger mở rộng theo giá trị

| Thuộc tính | `long` | `BigInteger` |
| --- | --- | --- |
| Dạng | Primitive 64-bit có dấu | Lớp đối tượng bất biến |
| Miền giá trị | `Long.MIN_VALUE` đến `Long.MAX_VALUE` | Không khóa ở độ rộng 64 bit; có giới hạn implementation và tài nguyên |
| Tràn khi cộng/nhân thông thường | Có thể wrap quanh modulo 2⁶⁴ | Không wrap theo miền `long` |
| Độ chính xác số nguyên | Chính xác trong miền `long` | Chính xác cho số nguyên mà JVM biểu diễn/tính được |
| Bộ nhớ mỗi giá trị | Kích thước primitive cố định | Phụ thuộc độ lớn, cấu trúc đối tượng và cài đặt |
| Độ dài kết quả | Tối đa 64 bit kiểu primitive | Có thể tăng theo phép toán |
| Khi không đủ khả năng xử lý | Overflow số học kiểu primitive | Có thể hết bộ nhớ, quá giới hạn implementation hoặc quá ngân sách ứng dụng |

Ví dụ so sánh:

~~~java
long primitive = Long.MAX_VALUE;
System.out.println(primitive + 1); // -9223372036854775808

BigInteger extended = BigInteger.valueOf(Long.MAX_VALUE)
        .add(BigInteger.ONE);
System.out.println(extended);      // 9223372036854775808
~~~

Đây là đoạn minh họa trong phương thức có import `java.math.BigInteger`, không phải chương trình độc lập.

`BigInteger` không làm mất chữ số trong phép cộng trên. Tuy nhiên, giá trị kết quả cần được biểu diễn và lưu trên heap. Nếu tiếp tục tạo những kết quả quá lớn, ứng dụng vẫn có thể thất bại.

**Không dùng `double` như phương án “tránh tràn long” nếu cần số nguyên chính xác.** `double` có 53 bit precision có nghĩa, nên nhiều số nguyên lớn không thể biểu diễn chính xác; `BigInteger` phục vụ bài toán khác hẳn.

## 3. BigInteger lưu bao nhiêu dữ liệu?

Về mặt toán học, một số nguyên dương `n` cần khoảng:

\[
\text{bits}(n)=\lfloor\log_2(n)\rfloor+1,\qquad n>0
\]

bit để biểu diễn phần độ lớn dưới dạng nhị phân. Với số 0, quy ước độ dài độ lớn bằng 0 bit.

Java có sẵn:

~~~java
BigInteger n = BigInteger.ONE.shiftLeft(1000);

System.out.println(n.bitLength());         // 1001
System.out.println(n.toString().length()); // 302
~~~

`n = 2¹⁰⁰⁰` nên bit cao nhất ở vị trí 1000 (đánh số từ 0), dẫn tới **1001 bit**. Khi in thập phân, `2¹⁰⁰⁰` có **302 chữ số**.

### Ước lượng bộ nhớ thô theo bit

Một số có `b` bit độ lớn cần ít nhất khoảng `ceil(b / 8)` byte cho phần bit thô, trước các chi phí bổ sung:

\[
\text{rawMagnitudeBytes}\approx \left\lceil\frac{b}{8}\right\rceil.
\]

Nhưng **đây không phải kích thước heap thực của BigInteger**. Trên JVM còn có header đối tượng, trường tham chiếu, thông tin dấu, mảng/word, alignment và các đối tượng tạm. Thực tế còn phụ thuộc phiên bản JDK, architecture, GC và cách tính.

`BigInteger.toByteArray()` cũng **không phải công cụ đo heap**. API này tạo một mảng byte **two's-complement có dấu**, nên có thể phải giữ thêm bit/byte dấu và còn phát sinh bản sao.

~~~java
BigInteger value = BigInteger.ONE.shiftLeft(1000);
System.out.println(value.toByteArray().length); // 126
~~~

Ví dụ trên có 126 byte, phù hợp với biểu diễn byte của số dương `2¹⁰⁰⁰`. Nó **không** có nghĩa đối tượng `BigInteger` chỉ chiếm 126 byte heap.

### Với số âm, cẩn trọng về bitLength()

`BigInteger.bitLength()` được mô tả bằng biểu diễn bù hai không kể bit dấu, nên không luôn giống bit length của **độ lớn tuyệt đối** đối với số âm. Nếu mục tiêu là kiểm soát kích thước toán hạng theo độ lớn, hãy dùng:

~~~java
int magnitudeBits = value.abs().bitLength();
~~~

`BigInteger.ZERO.bitLength()` là 0. `abs()` giữ nguyên ngữ nghĩa số nguyên chính xác; đừng ép về `long` để lấy giá trị tuyệt đối, vì như vậy có thể mất dữ liệu hoặc gặp `Long.MIN_VALUE`.

## 4. “Không cố định 64 bit” không đồng nghĩa “không có giới hạn implementation”

Hợp đồng `BigInteger` đảm bảo các phép toán số nguyên có độ chính xác tùy ý **trong miền mà implementation hỗ trợ**. Tài liệu API Java nêu các ràng buộc của kiểu và cảnh báo rằng những thao tác tạo kết quả vượt phạm vi được hỗ trợ có thể phát sinh `ArithmeticException`.

Ngoài phạm vi của implementation, còn có các giới hạn độc lập:

- **Kích thước mảng và đối tượng:** một mảng Java không có chiều dài vô hạn; độ lớn BigInteger phụ thuộc vào các cấu trúc dữ liệu trong bộ nhớ.
- **Giới hạn heap:** dù lý thuyết biểu diễn được, JVM có thể không còn vùng nhớ cấp phát.
- **Tài nguyên tạm thời:** phép nhân/chia/parse có thể cần nhiều bộ đệm hơn kích thước kết quả cuối.
- **CPU và thời gian:** ngay cả khi đủ RAM, tính toán hoặc chuyển đổi kết quả có thể lâu hơn giới hạn xử lý yêu cầu.
- **Giới hạn do ứng dụng đặt:** kích thước input, số chữ số output, số mũ, thời hạn request, quota tenant, ngân sách memory/CPU của container.

Không nên ghi trong tài liệu nghiệp vụ một con số **“BigInteger tối đa luôn là X bit”** như giới hạn thực tế phổ quát. Khả năng cụ thể có thể phụ thuộc phiên bản Java, implementation và môi trường vận hành; ứng dụng nên tự đặt ngân sách nhỏ hơn, có thể kiểm soát và kiểm thử được.

**Giới hạn kiểu dữ liệu** và **giới hạn an toàn của hệ thống** là hai việc khác nhau. Một máy có thể chứa số `BigInteger` với hàng triệu bit, nhưng việc cho phép từng yêu cầu web tạo số như vậy có thể vẫn không phù hợp.

## 5. Kích thước kết quả thay đổi theo phép toán

Điểm nguy hiểm không chỉ ở đầu vào; **kết quả trung gian có thể lớn lên rất nhanh**.

### Phép cộng và phép trừ

Khi cộng hai số có độ lớn tối đa khoảng `b` bit, kết quả có thể cần khoảng `b + 1` bit. Với dữ liệu tùy ý dấu, còn có những trường hợp triệt tiêu, nhưng không nên dựa vào điều đó để bỏ giới hạn.

~~~java
BigInteger a = BigInteger.ONE.shiftLeft(100);
BigInteger b = a;
BigInteger sum = a.add(b);

System.out.println(a.bitLength());   // 101
System.out.println(sum.bitLength()); // 102
~~~

### Phép nhân

Hai số dương có khoảng `m` và `n` bit tạo tích có không quá khoảng `m + n` bit, với độ lớn khác 0. Chẳng hạn bình phương một số có thể **gần gấp đôi số bit** cần thiết.

~~~java
BigInteger a = BigInteger.ONE.shiftLeft(1000);
BigInteger square = a.multiply(a);

System.out.println(a.bitLength());      // 1001
System.out.println(square.bitLength()); // 2001
~~~

Bên cạnh dung lượng kết quả còn có chi phí tính toán và các mảng trung gian. Implementation có thể sử dụng thuật toán nhân khác nhau theo kích thước; không nên khẳng định mọi phép nhân đều có một độ phức tạp cố định duy nhất trong mọi JDK.

### Nâng lũy thừa

`BigInteger.pow(int exponent)` có thể khiến kết quả tăng rất nhanh theo số mũ. Với `base > 1`:

\[
\text{bits}(base^e) \approx e\log_2(base)+1.
\]

~~~java
BigInteger power = BigInteger.TWO.pow(1000);
System.out.println(power.bitLength()); // 1001
~~~

Nếu số mũ do người dùng kiểm soát và bạn không kiểm tra ngân sách, một request nhỏ kiểu `"base=2&exponent=1000000000"` có thể yêu cầu lượng dữ liệu kết quả rất lớn. **Không chạy phép tính chỉ để “xem nó có vừa hay không”**; ước lượng kích thước trước.

### Bình phương lặp gây tăng trưởng đặc biệt nhanh

~~~java
BigInteger n = BigInteger.TWO;
for (int i = 0; i < 10; i++) {
    n = n.multiply(n);
}
System.out.println(n.bitLength()); // 1025
~~~

Sau 10 lần bình phương, `n = 2¹⁰²⁴`, có 1025 bit. Nếu tăng số lần bình phương thêm rất nhiều, số bit tăng theo **hàm mũ của số bước**. Vì vậy một vòng lặp trông ngắn cũng có thể tạo gánh nặng lớn nếu không có điểm dừng theo kích thước.

## 6. Không chỉ phép toán: parse và toString cũng tốn tài nguyên

Lấy BigInteger từ chuỗi:

~~~java
BigInteger n = new BigInteger("123456789012345678901234567890");
~~~

Cú pháp chuỗi nhỏ có vẻ vô hại. Nhưng với chuỗi có hàng triệu chữ số, chương trình cần đọc, kiểm tra định dạng, chuyển sang biểu diễn nhị phân, cấp phát bộ nhớ và xử lý lỗi. Nếu đầu vào không đáng tin, việc parse không giới hạn có thể là vấn đề **availability** của dịch vụ.

Tương tự, `n.toString()` phải sinh biểu diễn thập phân:

~~~java
String decimal = n.toString();
~~~

Một số có hàng triệu bit có thể tạo một chuỗi thập phân rất lớn. Việc ghi chuỗi đó ra log, gửi qua HTTP, chèn vào JSON hoặc lưu vào hệ thống khác tiêu tốn thêm bộ nhớ, CPU, băng thông và dung lượng lưu trữ.

### Ước lượng số chữ số thập phân

Số nguyên dương có `b` bit thường có khoảng:

\[
\text{decimalDigits}\approx b\log_{10}(2)\approx0{,}30103b.
\]

Đây là **ước lượng**, không phải một bộ kiểm tra độ dài chính xác cho mọi giá trị. Ví dụ `2¹⁰⁰⁰` có 1001 bit nhưng 302 chữ số thập phân.

Với dữ liệu cần giữ nguyên, không nên làm `toString()` của một BigInteger rất lớn chỉ để hỏi “nó có lớn không?”. `abs().bitLength()` cung cấp một tín hiệu kích thước rẻ hơn so với tạo toàn bộ chuỗi.

### Input cực dài có thể gây rủi ro trước khi constructor chạy

Nếu hệ thống đã tải toàn bộ body HTTP hoặc file vào một `String` khổng lồ rồi mới kiểm tra độ dài, bạn **đã cấp phát tài nguyên cho đầu vào**. Vì vậy nên giới hạn request body/field ngay tại ranh giới tiếp nhận, rồi mới kiểm tra chuỗi, parse và tính toán. Hàm parse có giới hạn ở tầng nghiệp vụ là lớp bảo vệ thứ hai, không phải thay thế toàn bộ kiểm soát ở gateway/server.

## 7. Một hàm parse có giới hạn số chữ số

Ví dụ yêu cầu hệ thống chỉ nhận số nguyên thập phân ASCII tối đa **40 chữ số**, không tính dấu `+` hoặc `-`. Đoạn code dưới đây không cần thư viện ngoài:

~~~java
static BigInteger parseLimitedDecimal(String text, int maxDigits) {
    if (text == null || maxDigits <= 0) {
        throw new IllegalArgumentException("Invalid input or limit");
    }

    int start = 0;
    if (!text.isEmpty()
            && (text.charAt(0) == '+' || text.charAt(0) == '-')) {
        start = 1;
    }

    int digits = text.length() - start;
    if (digits < 1 || digits > maxDigits) {
        throw new IllegalArgumentException("Too many or no digits");
    }

    for (int i = start; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c < '0' || c > '9') {
            throw new IllegalArgumentException("ASCII decimal digits only");
        }
    }

    return new BigInteger(text);
}
~~~

Đây là **phương thức đặt trong thân lớp**, cần `import java.math.BigInteger;`, không phải một file chạy độc lập.

Lựa chọn kiểm tra ASCII có chủ đích: `BigInteger(String)` có thể nhận nhiều dạng chữ số mà `Character.digit` hỗ trợ, nhưng giao thức ví dụ này quy định **chỉ 0–9 ASCII**. Đừng cho phép một tập ký tự rồi vô tình mô tả nó là “chỉ số ASCII”.

Điểm tốt của hàm:

1. Kiểm tra `null`, số chữ số và độ dài **trước** khi tạo `BigInteger`.
2. Cho phép dấu đầu chuỗi nhưng không tính nó như chữ số.
3. Dùng vòng lặp đơn giản để kiểm tra định dạng; không cần parse rồi mới kiểm tra kích thước.
4. Tạo giá trị chính xác nếu đầu vào hợp lệ.
5. Báo lỗi tường minh khi vượt chính sách.

Nếu chuỗi đến từ người dùng, còn cần giới hạn **độ dài payload trước khi hình thành String** như đã trình bày ở trên. Giới hạn 40 chỉ là ví dụ, không phải ngưỡng bắt buộc của `BigInteger`.

## 8. Kiểm tra số bit trước phép nhân

Với hai số khác 0 có độ dài độ lớn lần lượt là `aBits` và `bBits`, tích có **không quá `aBits + bBits` bit**. Ta có thể dùng cận trên bảo thủ này để **từ chối sớm** phép nhân có nguy cơ vượt ngân sách.

~~~java
static BigInteger multiplyWithinBitBudget(
        BigInteger a, BigInteger b, int maxBits) {
    if (a == null || b == null || maxBits < 1) {
        throw new IllegalArgumentException("Invalid operands or budget");
    }
    if (a.signum() == 0 || b.signum() == 0) {
        return BigInteger.ZERO;
    }

    long upperBound = (long) a.abs().bitLength()
            + b.abs().bitLength();
    if (upperBound > maxBits) {
        throw new IllegalArgumentException("Result may exceed bit budget");
    }

    return a.multiply(b);
}
~~~

Điều này là một **giới hạn bảo thủ**, không phải phép tiên đoán số bit chính xác của tích. Một tích thực tế có thể nằm trong ngân sách ngay cả khi `upperBound` lớn hơn ngưỡng: khi đó hàm **cố ý từ chối** để tránh rủi ro. Nếu nghiệp vụ không chấp nhận false rejection, hãy áp dụng phân tích phù hợp hơn hoặc dùng ngân sách lớn hơn.

Vì `bitLength()` trả `int`, phép cộng hai độ dài được chuyển sang `long` trước để tránh tràn số nguyên ở phép cộng cận. Đừng ước lượng bằng `a.toString().length()` và `b.toString().length()` khi input có thể cực lớn: tạo chuỗi cũng là công việc tốn tài nguyên.

Một cận trên không thay thế memory limit thật. Phép nhân nhỏ hơn ngân sách bit vẫn có thể cần **mảng trung gian**, gặp thiếu heap hoặc ảnh hưởng CPU.

## 9. Kiểm tra số mũ trước khi gọi pow

`BigInteger.pow(int exponent)` yêu cầu số mũ nguyên không âm. Nếu `exponent < 0`, API ném `ArithmeticException`. Nếu số mũ rất lớn, kết quả có thể cần số bit khổng lồ.

Với `base` khác 0 và số mũ dương, cận trên đơn giản là:

\[
\text{resultBits}\le \text{abs(base).bitLength()}\times exponent.
\]

Ta dùng `long` để tính tích của hai tham số kích thước:

~~~java
static BigInteger powWithinBitBudget(
        BigInteger base, int exponent, int maxBits) {
    if (base == null || exponent < 0 || maxBits < 1) {
        throw new IllegalArgumentException("Invalid power parameters");
    }
    if (exponent == 0) {
        return BigInteger.ONE;
    }
    if (base.signum() == 0) {
        return BigInteger.ZERO;
    }

    long upperBound = (long) base.abs().bitLength() * exponent;
    if (upperBound > maxBits) {
        throw new IllegalArgumentException("Power may exceed bit budget");
    }
    return base.pow(exponent);
}
~~~

Đây cũng là một **cận bảo thủ**. Ví dụ `base = 1` hoặc `base = -1` thì kết quả luôn có độ lớn 1 với mọi số mũ, nhưng phép ước lượng trên vẫn có thể từ chối khi exponent quá lớn. Nếu cần xử lý tối ưu, có thể đặc biệt hóa các base `0`, `1` và `-1`.

**Lưu ý `0^0` trong Java API:** `BigInteger.pow(0)` trả `BigInteger.ONE` (kể cả khi base là 0); code trên cũng tuân theo hành vi đó. Nếu một mô hình toán học/ứng dụng muốn coi `0^0` là không hợp lệ, phải áp dụng kiểm tra nghiệp vụ riêng.

Không phải lúc nào cũng phù hợp trả về một BigInteger khổng lồ từ lũy thừa. Nếu bạn chỉ cần `base^exponent mod modulus`, hãy dùng phép toán modulo chuyên dụng, được nói ở phần tiếp theo.

## 10. Ví dụ Java 17+ hoàn chỉnh và kiểm soát được tài nguyên

Lưu thành file `BigIntegerResourceDemo.java`:

~~~java
import java.math.BigInteger;

public class BigIntegerResourceDemo {
    private static final int MAX_DIGITS = 40;
    private static final int MAX_RESULT_BITS = 2048;

    static BigInteger parseLimitedDecimal(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Missing number");
        }
        int start = 0;
        if (!text.isEmpty()
                && (text.charAt(0) == '+' || text.charAt(0) == '-')) {
            start = 1;
        }
        int digits = text.length() - start;
        if (digits < 1 || digits > MAX_DIGITS) {
            throw new IllegalArgumentException("Invalid digit count");
        }
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Not ASCII decimal");
            }
        }
        return new BigInteger(text);
    }

    static BigInteger multiplyBounded(BigInteger a, BigInteger b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Missing operand");
        }
        if (a.signum() == 0 || b.signum() == 0) {
            return BigInteger.ZERO;
        }
        long upperBound = (long) a.abs().bitLength()
                + b.abs().bitLength();
        if (upperBound > MAX_RESULT_BITS) {
            throw new IllegalArgumentException("Product too large");
        }
        return a.multiply(b);
    }

    static BigInteger powBounded(BigInteger base, int exponent) {
        if (base == null || exponent < 0) {
            throw new IllegalArgumentException("Invalid exponent");
        }
        if (exponent == 0) {
            return BigInteger.ONE;
        }
        if (base.signum() == 0) {
            return BigInteger.ZERO;
        }
        long upperBound = (long) base.abs().bitLength() * exponent;
        if (upperBound > MAX_RESULT_BITS) {
            throw new IllegalArgumentException("Power too large");
        }
        return base.pow(exponent);
    }

    public static void main(String[] args) {
        BigInteger powerOfTwo = BigInteger.ONE.shiftLeft(1000);
        System.out.println("bits=" + powerOfTwo.bitLength());
        System.out.println("decimal digits=" + powerOfTwo.toString().length());
        System.out.println("byte array length=" +
                powerOfTwo.toByteArray().length);

        BigInteger parsed = parseLimitedDecimal("12345678901234567890");
        System.out.println("parsed=" + parsed);
        try {
            parsed.longValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("longValueExact=rejected");
        }

        System.out.println("square=" +
                multiplyBounded(BigInteger.valueOf(1234),
                        BigInteger.valueOf(1234)));
        System.out.println("two^10=" + powBounded(BigInteger.TWO, 10));
        System.out.println("zero bits=" + BigInteger.ZERO.bitLength());

        try {
            powBounded(BigInteger.TWO, 2000);
        } catch (IllegalArgumentException ex) {
            System.out.println("oversized power=rejected");
        }
        try {
            parseLimitedDecimal("1".repeat(41));
        } catch (IllegalArgumentException ex) {
            System.out.println("too many digits=rejected");
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 BigIntegerResourceDemo.java
java BigIntegerResourceDemo
~~~

Stdout:

~~~text
bits=1001
decimal digits=302
byte array length=126
parsed=12345678901234567890
longValueExact=rejected
square=1522756
two^10=1024
zero bits=0
oversized power=rejected
too many digits=rejected
~~~

Giải thích:

- Số `2^1000` chỉ lớn hơn một chút so với 1000 bit và vẫn rất an toàn để minh họa; không cần tạo một số có hàng triệu chữ số.
- Chuyển `BigInteger` có giá trị lớn hơn `Long.MAX_VALUE` bằng `longValueExact()` phát sinh `ArithmeticException`. Cách này giúp phát hiện vượt miền thay vì âm thầm mất bit như `longValue()`.
- Phép nhân nhỏ `1234 × 1234` cho kết quả chính xác `1522756`.
- `2^10` trả `1024`, nằm trong ngân sách 2048 bit.
- `2^2000` **không được tính** trong hàm `powBounded`: cận trên bảo thủ đã vượt ngân sách và ném lỗi trước khi tạo số lớn.
- Chuỗi 41 chữ số bị từ chối **trước khi khởi tạo BigInteger**.

Mục đích của ví dụ là thể hiện **kiểm soát tài nguyên bằng chính sách ứng dụng**, không đo benchmark CPU hoặc heap. Các ngân sách 40 chữ số/2048 bit chỉ là cấu hình minh họa; sản phẩm thực phải chọn giới hạn từ yêu cầu nghiệp vụ và thử nghiệm tải.

## 11. Dùng modPow thay vì tạo lũy thừa khổng lồ rồi lấy mod

Nhiều bài toán chỉ cần:

\[
a^e\bmod m
\]

với `m > 0`. Không cần tính giá trị `a^e` đầy đủ rồi mới lấy số dư. `BigInteger.modPow(BigInteger exponent, BigInteger modulus)` thực hiện phép nâng lũy thừa modulo trực tiếp.

~~~java
BigInteger base = BigInteger.valueOf(7);
BigInteger exponent = BigInteger.valueOf(100);
BigInteger modulus = BigInteger.valueOf(13);

BigInteger modular = base.modPow(exponent, modulus);
System.out.println(modular); // 9
~~~

Trong ví dụ trên, `7^100 mod 13 = 9`. Phương thức `modPow` giúp tránh phải giữ **toàn bộ `7^100`** trong kết quả trung gian chỉ để lấy số dư.

Nhưng **modPow không miễn phí**: số mũ lớn, modulus lớn hoặc số lượng phép gọi nhiều vẫn cần CPU và tài nguyên. Với dữ liệu không đáng tin, vẫn phải giới hạn độ dài của base, exponent, modulus, yêu cầu modulus dương và thời hạn xử lý.

`BigInteger.modPow` còn có các quy tắc riêng cho số mũ âm (liên quan nghịch đảo modulo khi tồn tại); nếu nghiệp vụ chỉ cần lũy thừa modulo thông thường, hãy **từ chối số mũ âm từ đầu** để hợp đồng đơn giản và dễ dự đoán.

## 12. Không tạo chuỗi khổng lồ chỉ để kiểm tra hay log

Ví dụ log nguy hiểm:

~~~java
// Không nên làm với BigInteger chưa bị giới hạn kích thước:
logger.info("Computed result: " + hugeBigInteger);
~~~

Việc ghép chuỗi sẽ gọi `toString()` của BigInteger trước khi hoặc trong lúc tạo thông điệp, có thể khiến ứng dụng tạo một chuỗi thập phân khổng lồ. Ngay cả việc dùng logging placeholder cũng cần xem logging framework có thực sự trì hoãn format, và có giới hạn độ lớn trước khi format hay không.

Thay vì log toàn bộ, nếu chính sách cho phép, có thể ghi thông tin **về kích thước**:

~~~java
BigInteger value = BigInteger.ONE.shiftLeft(1000);
System.out.println("sign=" + value.signum()
        + ", bits=" + value.abs().bitLength());
// sign=1, bits=1001
~~~

Khi phục vụ API, cần giới hạn **cả input lẫn output**. Một chuỗi số rất dài được phép tính xong vẫn có thể vượt giới hạn của response, cache, broker, database hoặc giao thức nhận dữ liệu tiếp theo.

Nếu yêu cầu phải xuất đầy đủ một kết quả cực lớn, nên có cơ chế xử lý theo lô hoặc lưu vào tài nguyên phù hợp, và đánh giá cả chi phí tạo biểu diễn decimal. Không giả định rằng vì tính toán hoàn tất thì bước serialize sẽ luôn an toàn.

## 13. Chi phí của các phép toán khác

| Phép toán | Dạng chi phí cần lưu ý |
| --- | --- |
| `add` / `subtract` | Xử lý các phần tử biểu diễn số; tạo kết quả thích hợp |
| `multiply` / `square` | Kích thước kết quả tăng, phép nhân dùng CPU và bộ nhớ tạm |
| `divide` / `remainder` | Thuật toán chia số nhiều bit; chi phí phụ thuộc độ lớn của cả hai toán hạng |
| `pow` | Độ dài kết quả có thể tăng theo số mũ |
| `shiftLeft` | Có thể mở rộng số bit theo số lượng vị trí dịch |
| `toString` | Tạo chuỗi decimal; CPU/bộ nhớ cho chuyển đổi và kết quả |
| `new BigInteger(String)` | Chi phí phân tích chuỗi và chuyển biểu diễn |
| `gcd` | Chi phí tăng với kích thước toán hạng |
| `modPow` | Giảm nhu cầu tạo lũy thừa đầy đủ, nhưng vẫn tốn tài nguyên |
| `isProbablePrime` / `nextProbablePrime` | Các phép kiểm tra/tìm số nguyên tố có thể tốn nhiều CPU với input lớn |

Không nên gán một con số thời gian hoặc độ phức tạp duy nhất cho mọi hoạt động BigInteger trong mọi JVM; JDK có thể lựa chọn thuật toán khác nhau theo độ dài và cấu trúc đầu vào. Điều quan trọng là **đo trên dữ liệu và runtime thực tế** đồng thời đặt giới hạn an toàn từ cấp thiết kế.

Một cảnh báo khác: phép chia cho `BigInteger.ZERO` ném `ArithmeticException` do **điều kiện toán học không hợp lệ**, chứ không phải lỗi thiếu tài nguyên. Hãy phân biệt lỗi dữ liệu, lỗi cận tài nguyên và lỗi hạ tầng khi ghi log hoặc trả phản hồi API.

## 14. Tăng trưởng không thể chặn chỉ bằng điều kiện “đầu vào nhỏ”

Một API nhận hai số nguyên mỗi số tối đa 100 chữ số có vẻ an toàn cho phép cộng. Nhưng nếu người dùng cung cấp thêm:

- `repeatCount` rất lớn để nhân hoặc cộng lặp.
- `exponent` để gọi `pow`.
- `shiftDistance` để gọi `shiftLeft`.
- Số lần thực hiện thuật toán tìm số nguyên tố.
- Số lượng bước thử hoặc số lượng yêu cầu đồng thời.

thì kết quả và CPU có thể vượt ngân sách dù **mỗi toán hạng đầu vào nhỏ**.

Ví dụ vòng lặp:

~~~java
BigInteger value = BigInteger.TWO;
for (int step = 0; step < 10; step++) {
    value = value.multiply(value);
}
System.out.println(value.bitLength()); // 1025
~~~

Tăng số bước lên vài lần nữa sẽ làm kích thước tăng đột ngột. Bảo vệ tốt là định nghĩa **cận tổng chi phí** theo thuật toán, không chỉ giới hạn ký tự của một input.

Nếu hệ thống cho phép biểu thức toán học do người dùng gửi vào, cần hạn chế cả độ sâu biểu thức, số phép toán, số mũ, kích thước kết quả trung gian và tần suất gọi. Nếu chương trình chỉ kiểm tra output sau khi phép tính hoàn tất, việc bảo vệ tài nguyên đã quá muộn.

## 15. Tại sao catch OutOfMemoryError không phải giải pháp?

`OutOfMemoryError` là một `Error`, không phải một `Exception` nghiệp vụ thông thường. Khi heap cạn hoặc JVM không thể cấp phát đối tượng cần thiết, hệ thống có thể đã ở trạng thái rất hạn chế về tài nguyên.

Không nên viết kiểu:

~~~java
// Mẫu không nên dùng để "xử lý" số quá lớn trong nghiệp vụ:
//
// try {
//     return veryLargeComputation();
// } catch (OutOfMemoryError error) {
//     return BigInteger.ZERO;
// }
~~~

Việc trả zero sẽ che giấu lỗi và tạo dữ liệu sai. Hơn nữa, việc bắt `OutOfMemoryError` **không đảm bảo** chương trình còn đủ bộ nhớ để chạy logic phục hồi an toàn; thông thường cần cơ chế quản lý tài nguyên và khôi phục dịch vụ ở tầng cao hơn.

Giải pháp tốt hơn là:

1. Giới hạn kích thước payload **trước khi** tạo chuỗi/đối tượng lớn.
2. Kiểm tra số bit/digit và các tham số tạo tăng trưởng trước phép tính.
3. Dùng phép toán phù hợp, như `modPow` nếu chỉ cần số dư modulo.
4. Đặt timeout, giới hạn song song và quota tại tầng job/request.
5. Dùng bộ đo/giám sát thích hợp cho heap, GC, CPU, latency và mức tải.
6. Thử tải với dữ liệu sát biên ngân sách, kiểm tra hành vi khi vượt ngưỡng.

## 16. Timeouts và heap size: không phải bảo đảm đủ cho mọi BigInteger

Cấu hình JVM như `-Xmx` đặt **giới hạn tối đa của heap**, nhưng không chỉ định một BigInteger được phép chiếm bao nhiêu. Heap còn dùng cho object khác, thread, request, cache và mảng tạm. Tăng `-Xmx` có thể giúp một workload chạy được, nhưng cũng có thể dẫn tới thời gian GC dài, áp lực bộ nhớ của máy chủ hoặc chi phí vận hành lớn.

Ví dụ lệnh:

~~~bash
java -Xmx256m MyBigIntegerApp
~~~

Đây là **lệnh minh họa**; `MyBigIntegerApp` không phải một lớp được tạo trong bài. Nó chỉ giới hạn heap theo cấu hình JVM, không tự động cho phép mọi phép tính BigInteger an toàn trong giới hạn đó.

**Timeout** cũng có nhiều tầng: HTTP timeout, queue deadline, cancellation của job và giới hạn CPU của container. Không mặc định rằng gọi `Thread.interrupt()` sẽ dừng tức thì mọi phương thức BigInteger đang tính. Với workload không tin cậy hoặc tính toán dài, cân nhắc cô lập job trong tiến trình/worker riêng để có cơ chế thực thi, giới hạn và hủy phù hợp với hệ thống.

## 17. Thiết kế ngân sách tài nguyên theo nghiệp vụ

Một phương án thực tế là **xác định ngân sách được phép trước khi xây API**:

| Tham số | Ví dụ giới hạn | Mục đích |
| --- | --- | --- |
| Số chữ số thập phân đầu vào | 1–1000 | Ngăn chuỗi quá dài |
| Số bit đầu vào | ≤ 4096 | Giới hạn độ lớn toán hạng |
| Số bit kết quả ước lượng | ≤ 8192 | Ngăn tăng trưởng ngoài dự kiến |
| Exponent | 0–10000, có thể thấp hơn | Kiểm soát `pow`/lặp |
| Số phép toán trong request | Theo workload | Chặn biểu thức dài bất thường |
| Kích thước response | Theo hợp đồng API | Tránh serialize/log kết quả quá dài |
| Thời hạn/CPU quota | Theo loại job | Bảo vệ thời gian đáp ứng |
| Số job đồng thời | Theo khả năng máy | Tránh cùng lúc cấp phát lớn |

**Các số trong bảng là ví dụ thiết kế, không phải khuyến nghị mặc định hoặc giới hạn của Java.** Không thể chọn riêng một “bit budget” rồi kết luận dịch vụ chắc chắn an toàn: độ phức tạp thuật toán và số request đồng thời quyết định đáng kể chi phí thực tế.

Nếu mỗi phép tính khác nhau có mức rủi ro khác nhau, nên đặt các giới hạn khác nhau cho parse, cộng, nhân, lũy thừa, primality test và serialize. Khi có dữ liệu người dùng, cần kiểm soát **trước** từng bước có thể làm chi phí tăng, kể cả khi những bước trước đã vượt qua validation.

## 18. Khi nào dùng BigInteger, khi nào giữ long?

Dùng `long` nếu:

- Miền dữ liệu được quy định rõ và luôn nằm trong 64 bit.
- Cần số lượng lớn phép toán nguyên primitive với chi phí/biểu diễn đơn giản.
- Có thể kiểm tra overflow tường minh bằng `Math.addExact`, `Math.multiplyExact` và các quy tắc giới hạn số.

Dùng `BigInteger` nếu:

- Giá trị nguyên hợp lệ vượt miền `long`.
- Cần tính toán số nguyên chính xác với số bit biến thiên.
- Bài toán dùng các phép toán số nguyên lớn như số học modulo, tính toán tổ hợp hoặc các thuật toán đặc thù.
- Bạn đã thiết kế chính sách hạn chế tài nguyên và cách báo lỗi cho trường hợp không thể xử lý.

Một lựa chọn cân bằng: dùng `long` với `Math.addExact`/`multiplyExact` để phát hiện overflow, rồi **chuyển sang BigInteger có chủ đích** khi bài toán cho phép số lớn. Đừng làm một phép toán đã overflow bằng `long` rồi mới chuyển **kết quả sai** sang `BigInteger`; phải chuyển toán hạng trước khi tính hoặc xử lý nhánh overflow riêng.

~~~java
long a = Long.MAX_VALUE;
long b = 1;

BigInteger sum = BigInteger.valueOf(a)
        .add(BigInteger.valueOf(b));
System.out.println(sum); // 9223372036854775808
~~~

## 19. Sai lầm thường gặp

| Sai lầm | Tại sao nguy hiểm | Cách làm đúng |
| --- | --- | --- |
| “BigInteger là vô hạn” | Heap, CPU, array và implementation vẫn hữu hạn | Dùng ngân sách tài nguyên |
| “Không overflow long thì không thể có lỗi” | Vẫn có thể `OutOfMemoryError` hoặc giới hạn implementation | Giới hạn input/output và thao tác |
| Đọc String có hàng triệu chữ số rồi mới kiểm tra | Payload đã tiêu tốn RAM trước khi validation | Chặn kích thước ở tầng nhận dữ liệu |
| Chỉ giới hạn bitLength toán hạng ban đầu | `pow`, `multiply` hoặc `shiftLeft` làm kết quả lớn nhanh | Ước lượng kích thước trước từng phép |
| Gọi `pow` với exponent do người dùng nhập trực tiếp | Có thể tạo kết quả rất lớn | Kiểm tra exponent và result bit budget |
| Dùng `base.pow(e).mod(m)` khi chỉ cần modulo | Tạo lũy thừa đầy đủ không cần thiết | `base.modPow(e, m)` khi phù hợp |
| `toString()` để kiểm tra kích thước mọi số | Tạo chuỗi decimal có thể rất lớn | `abs().bitLength()` và cận chi phí |
| Dùng `toByteArray().length` như heap size | Mảng two's-complement không tính object/intermediate | Xem là encoding, không phải đo RAM |
| Ép `BigInteger.longValue()` trước thao tác | Có thể mất bit cao | Giữ BigInteger hoặc `longValueExact()` |
| Bắt `OutOfMemoryError` rồi trả 0 | Che giấu lỗi; không bảo đảm phục hồi | Ngăn lỗi trước; xử lý ở tầng vận hành |
| Tăng `-Xmx` là hết mọi vấn đề | CPU/GC/concurrency và giới hạn output vẫn còn | Đo workload và đặt quota |
| Cho rằng đo `Runtime.freeMemory()` là cận an toàn để tính | Không phản ánh đầy đủ cấp phát trung gian, GC và tải đồng thời | Dùng giới hạn thiết kế và giám sát thực tế |
| Chỉ giới hạn ký tự đầu vào, bỏ số request | Tải đồng thời có thể gây cạn tài nguyên | Giới hạn concurrency/rate |
| Lấy timing từ một lần chạy nhỏ rồi suy cho mọi số | Thuật toán có thể đổi theo kích thước | Benchmark nhiều dải dữ liệu |
| Xem mọi `ArithmeticException` đều là lỗi chia 0 | Có thể có overflow giới hạn implementation, chuyển đổi exact, exponent âm... | Phân biệt theo API và ngữ cảnh |

## 20. Câu hỏi tự kiểm tra

**Câu 1.** Tại sao `BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)` đúng, nhưng vẫn không chứng minh BigInteger “vô hạn”?

**Đáp án:** BigInteger không có độ rộng 64 bit như long, nên biểu diễn được số vượt Long.MAX_VALUE. Tuy nhiên nó vẫn dùng heap và thuật toán trên một JVM hữu hạn, có giới hạn implementation và tài nguyên.

**Câu 2.** Số `2^1000` cần bao nhiêu bit để biểu diễn độ lớn và có bao nhiêu chữ số decimal?

**Đáp án:** 1001 bit và 302 chữ số thập phân. `BigInteger.ONE.shiftLeft(1000)` là ví dụ kiểm tra.

**Câu 3.** Vì sao `toByteArray().length` không phải kích thước heap của một BigInteger?

**Đáp án:** Nó là độ dài **một mảng mới chứa biểu diễn two's-complement có dấu**, có thể thêm bit/byte dấu và không tính header, mảng nội bộ hay các bộ đệm khác.

**Câu 4.** Nếu input chỉ 3 chữ số là `"999"` nhưng exponent bằng 1 tỷ, có thể coi an toàn không?

**Đáp án:** Không. `999^1000000000` có kích thước kết quả khổng lồ. Cần kiểm tra exponent và cận số bit kết quả trước khi tính.

**Câu 5.** Khi chỉ cần `7^100 mod 13`, nên dùng `pow(100).mod(13)` hay `modPow(100,13)`?

**Đáp án:** `modPow` phù hợp hơn vì tính số dư modulo trực tiếp mà không bắt buộc tạo lũy thừa đầy đủ.

**Câu 6.** Vì sao không nên catch `OutOfMemoryError` rồi trả về `BigInteger.ZERO`?

**Đáp án:** Cạn bộ nhớ không phải kết quả số học 0; cách đó che giấu lỗi và có thể không giúp JVM khôi phục. Phải thiết kế giới hạn tài nguyên trước.

**Câu 7.** `BigInteger.bitLength()` và `new BigInteger(text).toString().length()` có cùng chi phí và đơn vị không?

**Đáp án:** Không. `bitLength()` trả số bit theo biểu diễn số, còn `toString()` tạo văn bản thập phân rồi `length()` đếm UTF-16 code units của văn bản. Việc chuyển số rất lớn sang chuỗi có thể tốn tài nguyên đáng kể.

**Câu 8.** Nếu result bit budget là 2048, phép nhân có cận `aBits+bBits=2050` nhưng tích thực tế có thể chỉ 2049 bit, có nên gọi hàm kiểm tra bảo thủ là “sai” không?

**Đáp án:** Không; hàm được thiết kế **bảo thủ** và cố ý có thể từ chối trường hợp thực tế vẫn vừa ngân sách. Nếu cần tối ưu tỷ lệ chấp nhận, phải thiết kế cách ước lượng/kiểm tra chính xác hơn và chấp nhận chi phí tương ứng.

## 21. Ghi nhớ và tài liệu tham khảo

- **`BigInteger` không bị giới hạn ở 64 bit**, nhưng không có tài nguyên vô hạn. Giới hạn thực tế do **implementation, heap, CPU, các đối tượng tạm và chính sách ứng dụng** quyết định.
- Số bit có thể quan sát bằng `abs().bitLength()`; **đừng dùng `toByteArray()` như công cụ đo heap**.
- **Input nhỏ có thể tạo output rất lớn** qua nhân, bình phương lặp, dịch trái và `pow`.
- Đặt giới hạn **trước khi parse, trước phép toán nặng và trước serialize/log**. Cận trên độ lớn có thể bảo thủ, cần tài liệu rõ.
- Khi chỉ cần lũy thừa modulo, ưu tiên `modPow` thay cho tính đầy đủ `pow` rồi lấy `mod`.
- `OutOfMemoryError` không phải cách báo “kết quả bằng 0”; phòng tránh bằng quota, validation, timeout và cô lập tải thích hợp.
- Dùng `long` khi miền cố định đủ; dùng `BigInteger` khi thật sự cần số nguyên lớn chính xác và đã chấp nhận chi phí.

Tham khảo chính thức (Java 17):

- [BigInteger — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): giới hạn miền được hỗ trợ, `bitLength`, `multiply`, `pow`, `modPow`, `toByteArray`, `longValueExact`.
- [Math — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `addExact` và `multiplyExact` khi cần phát hiện overflow primitive.
- [OutOfMemoryError — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/OutOfMemoryError.html): lỗi cấp phát khi JVM không thể cung cấp đối tượng cần thiết.
- [Java launcher options — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): tùy chọn heap `-Xmx` và các cấu hình khởi động JVM.


</details>

- [Quay lại đầu trang](#back-to-top)
