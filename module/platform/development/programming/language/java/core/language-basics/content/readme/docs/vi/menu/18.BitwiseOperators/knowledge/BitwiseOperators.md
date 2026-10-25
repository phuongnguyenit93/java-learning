<a id="back-to-top"></a>

# Toán tử bit

## Menu
- [Biểu diễn bit của số nguyên có dấu](#bitwise-operators-001)
- [AND, OR, XOR và NOT theo từng bit](#bitwise-operators-002)
- [Dịch trái &lt;&lt;](#bitwise-operators-003)
- [Dịch phải có dấu &gt;&gt; và không dấu &gt;&gt;&gt;](#bitwise-operators-004)
- [Quy tắc giới hạn số bit dịch theo kiểu toán hạng](#bitwise-operators-005)
- [Numeric promotion khi thao tác trên byte, short hoặc char](#bitwise-operators-006)
- [Mặt nạ bit để đọc, bật, tắt và đảo cờ](#bitwise-operators-007)
- [Phân biệt phép tính theo bit với điều kiện boolean](#bitwise-operators-008)

## <a id="bitwise-operators-001">Biểu diễn bit của số nguyên có dấu</a>

<details>
<summary>Xem chi tiết</summary>

# Biểu diễn bit của số nguyên có dấu — Java 17+

Java biểu diễn các kiểu **số nguyên có dấu** `byte`, `short`, `int` và `long` bằng **bù hai** (*two's complement*) với độ rộng cố định. Đọc đúng mẫu bit giúp hiểu số âm, phép toán bit, phép dịch, mở rộng dấu và tràn số.

Ví dụ với **8 bit**:

~~~text
Giá trị     8 bit nhị phân    Hex
     5      0000 0101         0x05
    -5      1111 1011         0xFB
     0      0000 0000         0x00
    -1      1111 1111         0xFF
  -128      1000 0000         0x80
   127      0111 1111         0x7F
~~~

**Điểm cốt lõi:** bit cao nhất là **bit dấu** trong cách diễn giải có dấu: 0 ở đầu chỉ giá trị không âm, 1 ở đầu chỉ giá trị âm. Nhưng **không** phải chỉ cần đổi bit đầu tiên để đổi dấu; toàn bộ các bit được diễn giải theo quy tắc **bù hai**.

## 1. Độ rộng và miền giá trị của từng kiểu

| Kiểu | Số bit | Miền giá trị có dấu |
| --- | ---: | --- |
| `byte` | 8 | −128 đến 127 |
| `short` | 16 | −32.768 đến 32.767 |
| `int` | 32 | −2.147.483.648 đến 2.147.483.647 |
| `long` | 64 | −9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 |

Với độ rộng `w`, miền bù hai là:

\[
-2^{w-1}\ \leq\ x\ \leq\ 2^{w-1}-1
\]

Mẫu bit bằng 0 biểu diễn số 0; mẫu có bit cao nhất bằng 1 biểu diễn số âm. Miền **bất đối xứng**: có một giá trị âm nhiều hơn các giá trị dương; vì vậy, ví dụ, `Integer.MIN_VALUE` không có số đối dương trong `int`.

**Phân biệt:** `char` cũng rộng **16 bit** nhưng là **đơn vị mã UTF-16 không dấu**, giá trị 0 đến 65.535. `boolean` không phải kiểu số nguyên dùng để thao tác các bit dấu.

## 2. Bù hai hoạt động thế nào?

Với mẫu bit độ rộng `w`, có thể diễn giải giá trị theo **trọng số bit**:

- Các bit từ vị trí 0 đến `w-2` mang trọng số dương `2^i`.
- Bit cao nhất, vị trí `w-1`, mang trọng số **`-2^(w-1)`**.

Ví dụ `11111011` (8 bit):

~~~text
Bit:       1   1  1  1  1  0  1  1
Trọng số: -128 64 32 16  8  4  2  1

Giá trị: -128 + 64 + 32 + 16 + 8 + 0 + 2 + 1 = -5
~~~

Một cách dựng mẫu bit âm từ số dương trong độ rộng cố định:

1. Viết giá trị dương thành đúng `w` bit.
2. **Đảo tất cả bit** (0 ↔ 1).
3. **Cộng 1**, giữ đúng `w` bit thấp.

~~~text
 +5:          0000 0101
 Đảo bit:     1111 1010
 Cộng 1:      1111 1011    => -5
~~~

Quy tắc này áp dụng cho mẫu bit ở độ rộng cố định. Không thể tạo `+128` trong kiểu `byte` để đổi dấu `-128` mà vẫn giữ kiểu `byte`: `+128` nằm ngoài miền.

### Quan hệ với diễn giải không dấu

Cùng mẫu bit 8-bit `11111011` có thể được đọc là:

- **Có dấu:** `-5`.
- **Không dấu:** `251`.

Nếu một mẫu `w` bit có giá trị không dấu `u`, giá trị có dấu là:

~~~text
Nếu bit cao nhất = 0:  signed = u
Nếu bit cao nhất = 1:  signed = u - 2^w
~~~

Đây là **hai cách diễn giải cùng một mẫu bit**, không phải hai dãy bit khác nhau.

## 3. Chương trình Java 17+ độc lập và runnable

Tạo tệp UTF-8 tên **`SignedIntegerBitsDemo.java`**:

~~~java
public class SignedIntegerBitsDemo {
    private static String padded(String bits, int width) {
        return "0".repeat(width - bits.length()) + bits;
    }

    private static String bits8(int n) {
        return padded(Integer.toBinaryString(n & 0xFF), 8);
    }

    private static String bits16(int n) {
        return padded(Integer.toBinaryString(n & 0xFFFF), 16);
    }

    private static String bits32(int n) {
        return padded(Integer.toBinaryString(n), 32);
    }

    private static String bits64(long n) {
        return padded(Long.toBinaryString(n), 64);
    }

    public static void main(String[] args) {
        System.out.println("== widths and ranges ==");
        System.out.println("byte=" + Byte.SIZE + ":"
                + Byte.MIN_VALUE + ".." + Byte.MAX_VALUE);
        System.out.println("short=" + Short.SIZE + ":"
                + Short.MIN_VALUE + ".." + Short.MAX_VALUE);
        System.out.println("int=" + Integer.SIZE + ":"
                + Integer.MIN_VALUE + ".." + Integer.MAX_VALUE);
        System.out.println("long=" + Long.SIZE + ":"
                + Long.MIN_VALUE + ".." + Long.MAX_VALUE);

        System.out.println("== two's complement ==");
        System.out.println("byte5=" + bits8(5));
        System.out.println("byteMinus5=" + bits8(-5));
        System.out.println("byteMinus1=" + bits8(-1));
        System.out.println("byteMin=" + bits8(Byte.MIN_VALUE));
        System.out.println("byteMax=" + bits8(Byte.MAX_VALUE));
        System.out.println("intMinus5=" + bits32(-5));
        System.out.println("longMinus1=" + bits64(-1L));
        System.out.println("intMinHex="
                + Integer.toHexString(Integer.MIN_VALUE));
        System.out.println("minusOneHex="
                + Integer.toHexString(-1));
        System.out.println("notFour=" + (~4));
        System.out.println("negateFive=" + (~5 + 1));

        System.out.println("== sign extension ==");
        byte minusOne = -1;
        int widened = minusOne;
        System.out.println("widenedValue=" + widened);
        System.out.println("widenedHex="
                + Integer.toHexString(widened));
        System.out.println("lowByte=" + bits8(widened));
        System.out.println("maskedByte=" + (minusOne & 0xFF));
        System.out.println("unsignedByte="
                + Byte.toUnsignedInt(minusOne));

        short minusTwo = -2;
        System.out.println("shortBits=" + bits16(minusTwo));
        System.out.println("widenedShortHex="
                + Integer.toHexString(minusTwo));
        System.out.println("unsignedShort="
                + Short.toUnsignedInt(minusTwo));

        char maximumChar = (char) 0xFFFF;
        System.out.println("charValue=" + (int) maximumChar);
        System.out.println("charBits=" + bits16(maximumChar));
        System.out.println("castFFToByte=" + ((byte) 0xFF));

        System.out.println("== shifts ==");
        int negative = -8;
        System.out.println("minus8=" + bits32(negative));
        System.out.println("arithmeticRight=" + (negative >> 1));
        System.out.println("arithmeticBits="
                + bits32(negative >> 1));
        System.out.println("logicalRight=" + (negative >>> 1));
        System.out.println("logicalBits="
                + bits32(negative >>> 1));
        System.out.println("longLogicalRight="
                + (-8L >>> 1));
        System.out.println("byteShift=" + ((byte) -8 >> 1));
        System.out.println("oneShift32=" + (1 << 32));

        System.out.println("== limits ==");
        System.out.println("intOverflow="
                + (Integer.MAX_VALUE + 1));
        System.out.println("negatedIntMin="
                + (-Integer.MIN_VALUE));
        System.out.println("leadingZerosOne="
                + Integer.numberOfLeadingZeros(1));
        System.out.println("leadingZerosMinusOne="
                + Integer.numberOfLeadingZeros(-1));
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) trong thư mục chứa file, dùng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 SignedIntegerBitsDemo.java
java SignedIntegerBitsDemo
~~~

**Exact expected stdout** của riêng lệnh `java SignedIntegerBitsDemo`:

~~~text
== widths and ranges ==
byte=8:-128..127
short=16:-32768..32767
int=32:-2147483648..2147483647
long=64:-9223372036854775808..9223372036854775807
== two's complement ==
byte5=00000101
byteMinus5=11111011
byteMinus1=11111111
byteMin=10000000
byteMax=01111111
intMinus5=11111111111111111111111111111011
longMinus1=1111111111111111111111111111111111111111111111111111111111111111
intMinHex=80000000
minusOneHex=ffffffff
notFour=-5
negateFive=-5
== sign extension ==
widenedValue=-1
widenedHex=ffffffff
lowByte=11111111
maskedByte=255
unsignedByte=255
shortBits=1111111111111110
widenedShortHex=fffffffe
unsignedShort=65534
charValue=65535
charBits=1111111111111111
castFFToByte=-1
== shifts ==
minus8=11111111111111111111111111111000
arithmeticRight=-4
arithmeticBits=11111111111111111111111111111100
logicalRight=2147483644
logicalBits=01111111111111111111111111111100
longLogicalRight=9223372036854775804
byteShift=-4
oneShift32=1
== limits ==
intOverflow=-2147483648
negatedIntMin=-2147483648
leadingZerosOne=31
leadingZerosMinusOne=0
~~~

Hai lệnh kiểm tra phiên bản JDK in thông tin tùy máy, không thuộc khối stdout cố định. `bits8` và `bits16` trong demo **che các bit cao** bằng mask trước khi in, nhằm quan sát **đúng 8/16 bit** thay vì vô tình in mẫu `int` đã mở rộng dấu.

## 4. Tại sao số âm in ra nhiều bit 1?

~~~java
System.out.println(Integer.toBinaryString(-1));
~~~

Với `int` 32 bit, `-1` có mẫu bù hai gồm **32 bit 1**. `Integer.toBinaryString` trả **chuỗi bit theo biểu diễn bù hai của `int`**; đối với số âm, chuỗi có độ dài 32.

Tuy nhiên, với một `byte`:

~~~java
byte value = -1;
System.out.println(Integer.toBinaryString(value));
~~~

Kết quả vẫn là **32 bit 1**, vì `byte` được **nâng thành `int` trước khi truyền tham số**. Muốn xem đúng 8 bit thấp:

~~~java
byte value = -1;
String eightBits = Integer.toBinaryString(value & 0xFF);
// eightBits == "11111111".
~~~

Với số dương, `Integer.toBinaryString(5)` chỉ in `101` chứ **không tự đệm đủ 32 bit**. Hàm `padded` trong chương trình giúp tạo chuỗi đúng độ rộng để đối chiếu.

**Chú ý:** hiển thị hex/binary là biểu diễn dữ liệu, không biến một số âm thành số dương chỉ vì không có dấu trừ trong chuỗi. `Integer.toHexString(-1)` là `"ffffffff"`, tương ứng mẫu 32 bit bù hai.

## 5. Mở rộng dấu khi chuyển `byte`/`short` sang `int`

Khi chuyển kiểu số nguyên **có dấu** hẹp sang kiểu rộng, Java giữ nguyên giá trị bằng cách **mở rộng bit dấu** (*sign extension*).

~~~text
byte  -1:           11111111            (8 bit)
int   -1:  11111111111111111111111111111111  (32 bit)
~~~

Vì vậy:

~~~java
byte value = -1;
int wide = value;        // -1, khong phai 255.
int unsigned = value & 0xFF; // 255.
~~~

- `int wide = value` giữ **giá trị có dấu**.
- `value & 0xFF` giữ **8 bit thấp** rồi diễn giải thành số nguyên không âm 0–255.
- `Byte.toUnsignedInt(value)` thể hiện rõ mục đích đọc `byte` như số không dấu.

Với `short` có thể dùng `Short.toUnsignedInt(value)` để đọc 16 bit dưới dạng 0–65.535.

### Vì sao `char` khác `short`?

Cả hai đều có 16 bit, nhưng:

~~~java
short signed = -1;
char unsigned = (char) 0xFFFF;

int fromShort = signed;    // -1.
int fromChar = unsigned;   // 65535.
~~~

`short` **mở rộng dấu** khi chuyển sang `int`; `char` **mở rộng với bit 0** vì là đơn vị mã **không dấu**. Không dùng `char` như một “unsigned short” thay thế tùy tiện trong mã thao tác ký tự vì `char` có ý nghĩa riêng về UTF-16.

## 6. Số âm khi dịch bit

Mẫu bit có dấu cũng giải thích vì sao các phép dịch phải cho kết quả khác nhau. Với `int -8` (`0xFFFFFFF8`), `>>` lấp bit dấu 1, còn `>>>` lấp bit 0:

~~~java
System.out.println(-8 >> 1);  // -4: vẫn âm.
System.out.println(-8 >>> 1); // 2147483644: bit cao mới bằng 0.
~~~

`<<` dịch trái và chèn bit 0 ở bên phải; các bit vượt ngoài độ rộng `int`/`long` bị bỏ. Đây là phép thao tác bit, không phải phép nhân luôn an toàn trước tràn. `byte`, `short`, `char` được nâng thành `int` trước phép dịch; `int` dùng 5 bit thấp của khoảng dịch, `long` dùng 6 bit thấp. Vì thế `1 << 32` là 1. Các bài về dịch bit phân tích riêng từng cơ chế và trường hợp biên.

## 7. Đảo bit, đổi dấu và tràn số

Trong biểu diễn bù hai với độ rộng cố định, `~x` đảo toàn bộ bit và `-x == ~x + 1` theo phép toán số nguyên của Java. Ví dụ `~4` là -5; `~5 + 1` cũng là -5. Tràn không tự gây ngoại lệ: `Integer.MAX_VALUE + 1` và `-Integer.MIN_VALUE` đều cho `Integer.MIN_VALUE`.

~~~java
int min = Integer.MIN_VALUE;
System.out.println(-min);            // -2147483648 (tràn int).
System.out.println(-(long) min);     // 2147483648 (nâng trước).
~~~

Nếu cần phát hiện tràn trong tính toán nghiệp vụ, chọn `Math.addExact`/`Math.negateExact`; nếu cần miền lớn hơn `long`, dùng `BigInteger`. Chính độ rộng và cách diễn giải có dấu của mẫu bit quyết định kết quả.

## 8. Cách đọc literal nhị phân và hex

Java hỗ trợ literal cơ số 2 với tiền tố `0b` và cơ số 16 với tiền tố `0x`:

~~~java
int positive = 0b0000_0101; // 5.
int mask = 0xFF;           // 255.
byte minusOne = (byte) 0xFF; // -1, giu 8 bit thap.
~~~

Các dấu gạch dưới chỉ để dễ đọc literal, không thay đổi giá trị.

**Đừng nhầm literal `0xFF` với `byte -1`**: không có cast, `0xFF` là số nguyên dương 255 kiểu `int`. Khi thu hẹp xuống `byte`, mẫu 8 bit thấp `11111111` được diễn giải thành **-1**.

Các literal hex/binary `int` có thể thể hiện trọn 32 bit, vì thế literal như `0x80000000` tương ứng mẫu bit của `Integer.MIN_VALUE`. Việc cùng một dãy bit mang ý nghĩa âm hoặc dương phụ thuộc **độ rộng và cách diễn giải có dấu/không dấu**.

## 9. Dữ liệu trong bộ nhớ và mẫu bit không phải cùng vấn đề với thứ tự byte

Biểu diễn **bù hai** trả lời câu hỏi: *những bit nào biểu diễn một giá trị số nguyên có dấu?* Còn **endianness** (big-endian/little-endian) trả lời câu hỏi khác: *khi nhiều byte được lưu/truyền, byte nào xuất hiện trước?*

Ví dụ một `int` rộng 32 bit có thể có cùng giá trị và cùng 32 bit về mặt số học, nhưng trật tự bốn byte trong một định dạng nhị phân cần tuân theo quy ước lưu trữ/truyền tải. Khi đọc file hoặc giao thức, phải chọn rõ byte order (ví dụ qua `ByteBuffer.order(...)`); không suy ra endianness từ việc thấy số âm có bit dấu bằng 1.

## 10. Những lỗi thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Số âm chỉ khác số dương ở bit dấu.” | Bù hai liên quan **toàn bộ mẫu bit**, không chỉ đảo bit đầu |
| “`byte -1` khi sang `int` thành 255.” | Chuyển kiểu có dấu **mở rộng dấu**, vẫn là -1 |
| “`Integer.toBinaryString(byte âm)` in 8 bit.” | `byte` bị nâng thành `int`, thường in 32 bit nếu âm |
| “`0xFF` mặc định là `byte -1`.” | `0xFF` là 255 kiểu `int` |
| “`char` 16 bit nên là kiểu số nguyên có dấu.” | `char` là đơn vị mã UTF-16 **không dấu** |
| “`>>` và `>>>` giống nhau với số âm.” | `>>` điền bit dấu; `>>>` điền 0 |
| “`1 << 32` cho 0.” | Với `int`, khoảng dịch được mask; kết quả là 1 |
| “`-Integer.MIN_VALUE` luôn thành số dương.” | Tràn `int` nên vẫn là `MIN_VALUE` |
| “Bù hai cho biết thứ tự các byte trong bộ nhớ.” | Endianness là vấn đề khác |

## 11. Bài tập tự kiểm tra

1. Biểu diễn `-1` ở độ rộng 8 bit?  
   *Đáp án:* `11111111`.
2. Dãy 8 bit `11111011` được hiểu là số có dấu bao nhiêu? Không dấu bao nhiêu?  
   *Đáp án:* **-5** và **251**.
3. Vì sao `byte b = -1; int x = b;` cho `x = -1` chứ không phải 255?  
   *Đáp án:* Mở rộng bit dấu khi chuyển có dấu sang kiểu rộng.
4. Viết cách lấy mẫu byte `-1` dưới dạng `int` không dấu 255.  
   *Đáp án:* `b & 0xFF` hoặc `Byte.toUnsignedInt(b)`.
5. `-8 >> 1` và `-8 >>> 1` lần lượt bằng bao nhiêu?  
   *Đáp án:* **-4** và **2147483644**.
6. `1 << 32` bằng bao nhiêu với toán hạng `int`?  
   *Đáp án:* **1**, vì khoảng dịch dùng 5 bit thấp.
7. `~4` bằng bao nhiêu trong `int`?  
   *Đáp án:* **-5**.
8. `Integer.MAX_VALUE + 1` cho gì?  
   *Đáp án:* **Integer.MIN_VALUE = -2147483648**, do tràn `int`.
9. `short s = -1; char c = (char) 0xFFFF;`; khi chuyển sang `int`, `s` và `c` khác thế nào?  
   *Đáp án:* `s` thành **-1**, `c` thành **65535**.

## Checklist

- [ ] Biết `byte`, `short`, `int` và `long` là các kiểu nguyên **có dấu, bù hai**, rộng 8/16/32/64 bit.
- [ ] Giải thích được vì sao bit cao nhất có trọng số âm và mẫu `111...111` bằng -1.
- [ ] Hiểu cách tạo bù hai: đảo bit rồi cộng 1 trong độ rộng cố định.
- [ ] Phân biệt cách diễn giải **có dấu** và **không dấu** của cùng mẫu bit.
- [ ] Biết chuyển `byte`/`short` âm sang `int` thực hiện **mở rộng dấu**.
- [ ] Dùng mask và `Byte.toUnsignedInt`/`Short.toUnsignedInt` khi cần đọc phần dữ liệu không dấu.
- [ ] Phân biệt `>>` (giữ dấu) và `>>>` (điền 0) và hiểu numeric promotion.
- [ ] Nhận diện overflow, `Integer.MIN_VALUE` và giới hạn khoảng cách dịch.
- [ ] Không nhầm bù hai với endianness.

**Kết luận:** Bù hai quy định cách Java diễn giải mẫu bit của số nguyên có dấu. Muốn dùng toán tử bit đúng, hãy luôn theo dõi **độ rộng kiểu**, **bit dấu**, **mở rộng/thu hẹp kiểu**, và **quy tắc dịch bit**, thay vì suy luận chỉ từ chuỗi 0/1 in ra.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bitwise-operators-002">AND, OR, XOR và NOT theo từng bit</a>

<details>
<summary>Xem chi tiết</summary>

# AND, OR, XOR và NOT theo từng bit trong Java

> **Java core — language-basics / Toán tử bit**  
> **Môi trường:** JDK 17+; chỉ sử dụng thư viện chuẩn Java.  
> **Mục tiêu:** Hiểu `&`, `|`, `^` và `~` trên các bit của số nguyên; đọc bảng chân trị, thao tác bit mask, xử lý đúng số âm và quy tắc nâng kiểu.

## 1. Toán tử bit hoạt động như thế nào?

Mỗi số nguyên được lưu dưới dạng dãy bit 0/1. Các phép toán bit xử lý **từng vị trí bit tương ứng** của toán hạng:

| Toán tử | Tên | Quy tắc cho từng vị trí bit |
| --- | --- | --- |
| `&` | AND | Kết quả là 1 **chỉ khi cả hai bit đều là 1** |
| `\|` | OR | Kết quả là 1 khi **ít nhất một bit là 1** |
| `^` | XOR | Kết quả là 1 khi **hai bit khác nhau** |
| `~` | NOT (bù bit) | **Đảo từng bit:** 0 → 1; 1 → 0 |

Ví dụ với hai số `int`:

~~~java
int a = 0b1100; // 12
int b = 0b1010; // 10

int and = a & b; // 0b1000 = 8
int or  = a | b; // 0b1110 = 14
int xor = a ^ b; // 0b0110 = 6
~~~

`0b` là tiền tố literal nhị phân trong Java. Dấu gạch dưới có thể giúp đọc nhóm bit, chẳng hạn `0b1100_1010`.

## 2. Bảng chân trị AND, OR, XOR, NOT

Xem `A` và `B` là **một bit đơn lẻ**:

| A | B | A AND B | A OR B | A XOR B | NOT A |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 0 | 0 | 0 | 0 | 1 |
| 0 | 1 | 0 | 1 | 1 | 1 |
| 1 | 0 | 0 | 1 | 1 | 0 |
| 1 | 1 | 1 | 1 | 0 | 0 |

`NOT A` chỉ phụ thuộc vào `A`, không phụ thuộc `B`.

Có thể hình dung phép toán theo cột bit:

~~~text
   a  = 1100  (12)
   b  = 1010  (10)
a & b = 1000  ( 8)
a | b = 1110  (14)
a ^ b = 0110  ( 6)
~~~

Từ bảng chân trị suy ra:

- `x & 0` luôn bằng 0; `x | 0` bằng `x`.
- `x ^ 0` bằng `x`; `x ^ x` bằng 0.
- `x & x` và `x | x` đều bằng `x`.

Những tính chất này hữu ích để bật, tắt, đảo và kiểm tra cờ (*flags*).

## 3. `~` đảo **toàn bộ** bit, không chỉ những bit đang nhìn thấy

Với `int`, Java sử dụng **32 bit có dấu**. Vì vậy `~a` đảo **cả 32 bit**, không chỉ bốn bit `1100` được viết trong literal:

~~~java
int a = 0b1100; // 12
System.out.println(~a); // -13, không phải 3.
~~~

Số nguyên có dấu dùng biểu diễn **bù hai** (*two's complement*). Với `int`/`long`, ta có hệ thức:

~~~text
~n == -n - 1
~12 == -13
~~~

Nếu chỉ muốn xét bốn bit thấp, dùng **mask**:

~~~java
int lowFour = (~a) & 0b1111; // 0b0011 = 3.
~~~

Đây là khác biệt giữa `~a` (đảo mọi bit của một giá trị kiểu `int`) và `(~a) & 0b1111` (giữ **4 bit thấp** sau khi đảo).

`int` có độ rộng 32 bit, `long` 64 bit. `~` không áp dụng được lên `boolean`; phủ định logic boolean dùng `!`.

## 4. Ứng dụng ngắn: đọc, bật, tắt và đảo một bit

Một *bit mask* đặt bit 1 tại vị trí cần thao tác. Từ bảng chân trị của AND, OR và XOR, có bốn quy tắc đủ để thử trên số nguyên:

```java
int flags = 0b0101;             // Bit 0 và bit 2 đang bật.
int mask = 0b0010;              // Chọn bit 1.
boolean hasBit = (flags & mask) != 0; // false.
int set = flags | mask;         // 0b0111, bật bit 1.
int cleared = flags & ~0b0100; // 0b0001, tắt bit 2.
int toggled = flags ^ 0b0001;  // 0b0100, đảo bit 0.
```

**AND lọc bit**, **OR bật bit**, **AND với NOT tắt bit**, **XOR đảo bit**. Hai phép `flags & mask` và `flags | mask` đều có kết quả là **số nguyên**, không phải `boolean`: muốn kiểm tra trong `if`, so sánh `(flags & mask) != 0` (ít nhất một bit bật) hoặc `(flags & mask) == mask` (mọi bit trong mask đều bật).

Đây chỉ là **ứng dụng trực tiếp của bảng chân trị**. Khi quản lý nhiều cờ thực tế, còn phải xem mask có bằng 0 không, cờ có nằm trong miền hợp lệ không, cách kiểm tra *any/all* và vấn đề đồng bộ; các thao tác này không được suy ra là một hệ thống phân quyền an toàn chỉ từ phép toán bit.

## 5. Kiểu dữ liệu và nâng kiểu số nguyên

Toán tử bit dành cho các kiểu **nguyên** (`byte`, `short`, `char`, `int`, `long`). Khi dùng `&`, `|`, `^` với số nguyên, Java áp dụng *binary numeric promotion*:

- Các toán hạng `byte`, `short` và `char` được nâng lên ít nhất `int`.
- Nếu một toán hạng có kiểu `long` thì phép toán số nguyên thực hiện ở kiểu `long`.
- `~` với `byte`, `short`, `char` cũng cho kết quả kiểu `int` sau khi nâng kiểu.
- `float` và `double` không được dùng với các phép toán bit này.

~~~java
byte small = 0b0000_1111; // 15
int inverse = ~small;    // -16 (int), không phải byte.
int lowEight = inverse & 0xFF; // 240: giữ 8 bit thấp.

// byte invalid = ~small; // Lỗi biên dịch: int không tự gán sang byte.
~~~

Với byte âm, Java chuyển sang `int` có mở rộng dấu trước khi thực hiện nhiều phép toán. Khi muốn biểu diễn một byte như số **không dấu trong miền 0..255**, dùng `value & 0xFF` hoặc `Byte.toUnsignedInt(value)`.

Ví dụ `long`:

~~~java
long bits = 0x1_0000_0005L;
long mask = 0x1_0000_0000L;
long highFlag = bits & mask; // 4294967296.
~~~

Hằng `L` và kiểu `long` rất quan trọng khi cờ nằm ở vị trí bit cao vượt miền `int`.

## 6. Đừng nhầm toán tử bit với toán tử logic

Trên `boolean`, Java cũng cho phép `&`, `|`, `^` nhưng chúng thực hiện **phép logic boolean**, không phải thao tác bit trên số:

~~~java
boolean a = true;
boolean b = false;
boolean both = a & b;  // false.
boolean either = a | b; // true.
boolean one = a ^ b;    // true.
boolean not = !a;      // false.
~~~

| Toán tử | Với số nguyên | Với boolean |
| --- | --- | --- |
| `&`, `\|`, `^` | AND/OR/XOR theo từng bit | AND/OR/XOR logic, **đánh giá cả hai vế** |
| `~` | Đảo bit của số nguyên | **Không hợp lệ** |
| `!` | **Không hợp lệ** | Phủ định logic |
| `&&`, `\|\|` | **Không hợp lệ** | AND/OR logic **ngắn mạch** |

Khác biệt quan trọng: `false & check()` **vẫn gọi** `check()`; `false && check()` **bỏ qua** lời gọi. Tương tự, `true | check()` vẫn gọi `check()`, còn `true || check()` không gọi.

Độ ưu tiên của các toán tử bit nhị phân là **`&` cao hơn `^`, cao hơn `|`**. Nên viết ngoặc khi trộn nhiều toán tử, đặc biệt các biểu thức mask có phép so sánh.

## 7. Ví dụ Java 17+ chạy độc lập

Tạo file **BitwiseOperatorsDemo.java**:

~~~java
public final class BitwiseOperatorsDemo {
    private static String bits4(int value) {
        String raw = Integer.toBinaryString(value & 0b1111);
        return "0".repeat(4 - raw.length()) + raw;
    }

    public static void main(String[] args) {
        int a = 0b1100; // 12
        int b = 0b1010; // 10

        System.out.println("a=" + bits4(a) + ",b=" + bits4(b));
        System.out.println("and=" + (a & b)
                + ",bits=" + bits4(a & b));
        System.out.println("or=" + (a | b)
                + ",bits=" + bits4(a | b));
        System.out.println("xor=" + (a ^ b)
                + ",bits=" + bits4(a ^ b));
        System.out.println("not=" + (~a)
                + ",low4=" + bits4(~a));
        System.out.println("notMasked=" + ((~a) & 0b1111));

        int flags = 0b0101;
        int mask = 0b0010;
        System.out.println("flags=" + bits4(flags));
        System.out.println("set=" + bits4(flags | mask));
        System.out.println("clear=" + bits4(flags & ~0b0100));
        System.out.println("toggle=" + bits4(flags ^ 0b0001));
        System.out.println("hasBit2=" + ((flags & 0b0100) != 0));
        System.out.println("allSelected=" + ((flags & 0b0101) == 0b0101));

        byte small = 0b0000_1111;
        int inverted = ~small;
        System.out.println("byteNot=" + inverted
                + ",low8=" + (inverted & 0xFF));

        long big = 0x1_0000_0005L;
        long highMask = 0x1_0000_0000L;
        System.out.println("longMask=" + (big & highMask));

        boolean yes = true, no = false;
        System.out.println("booleanAnd=" + (yes & no)
                + ",booleanOr=" + (yes | no)
                + ",booleanXor=" + (yes ^ no));
    }
}
~~~

Chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 BitwiseOperatorsDemo.java
java BitwiseOperatorsDemo
~~~

**Expected stdout chính xác:**

~~~text
a=1100,b=1010
and=8,bits=1000
or=14,bits=1110
xor=6,bits=0110
not=-13,low4=0011
notMasked=3
flags=0101
set=0111
clear=0001
toggle=0100
hasBit2=true
allSelected=true
byteNot=-16,low8=240
longMask=4294967296
booleanAnd=false,booleanOr=true,booleanXor=true
~~~

`not=-13` do `~` đảo đủ 32 bit của `int`; `low4=0011` chỉ hiển thị bốn bit thấp. `byteNot=-16` vì `byte` được nâng thành `int`; `low8=240` là tám bit thấp. Kết quả mask cho thấy cách thao tác từng cờ mà không làm thay đổi những bit không được chọn.

## 8. Lỗi thường gặp và bài tập

| Nhầm lẫn | Kết luận đúng |
| --- | --- |
| Nghĩ `~0b1100` chỉ ra `0b0011` | `~` đảo **toàn bộ 32 bit `int`**; mask mới giữ bốn bit thấp |
| Dùng `if (flags & mask)` | Java yêu cầu boolean; viết `if ((flags & mask) != 0)` |
| Dùng `~true` hoặc `!12` | `~` chỉ dùng với số nguyên, `!` dùng với boolean |
| Cho rằng `&`/`\|` trên boolean short-circuit | Không: cả hai vế đều được đánh giá |
| Gán `~byteValue` thẳng vào `byte` | Kết quả được nâng thành `int` |
| Nghĩ XOR giống OR | XOR bằng 0 khi cả hai bit đều 1 |

**Bài 1.** Tính `0b0110 & 0b1010`, `0b0110 | 0b1010` và `0b0110 ^ 0b1010`. Viết kết quả thành bốn bit.

**Bài 2.** Cho `int flags = 0b0011;`. Viết lệnh bật bit thứ ba (đếm bit thấp nhất là bit 0), tắt bit 0 và đảo bit 1.

**Bài 3.** Giải thích vì sao `~12 == -13` trong Java, còn `(~12) & 0b1111 == 3`.

**Bài 4.** Cho `byte b = 15;`. Dự đoán kiểu và giá trị của `~b`. Muốn xem tám bit thấp dưới dạng số 0..255, nên dùng phép mask nào?

**Bài 5.** So sánh `false & check()` với `false && check()` nếu `check()` tăng bộ đếm hoặc có thể ném ngoại lệ.

### Ghi nhớ

**`&` (AND), `|` (OR), `^` (XOR) kết hợp các bit; `~` (NOT) đảo mọi bit của kiểu số nguyên.** Với `int`/`long` có dấu, `~n = -n - 1`; muốn xét một số bit nhất định thì dùng mask. Với `byte`/`short`/`char`, chú ý phép nâng kiểu lên `int`. Dùng mask và ngoặc rõ ràng, phân biệt toán tử bit với logic boolean ngắn mạch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bitwise-operators-003">Dịch trái &lt;&lt;</a>

<details>
<summary>Xem chi tiết</summary>

# Dịch trái `<<` trong Java 17+

Toán tử **dịch trái** (*left shift*) `a << n` dịch các bit của `a` sang trái một số vị trí, bổ sung **bit 0 ở bên phải** và **loại bỏ các bit tràn ra ngoài độ rộng kiểu**.

~~~java
int value = 3;             // 0000...0011
int shifted = value << 2;  // 0000...1100 = 12
~~~

Với số nguyên nhỏ, không bị tràn, `a << n` thường cho giá trị bằng `a * 2^n`. Nhưng **đây không phải phép nhân có kiểm tra tràn**: khi dịch mất bit cao hoặc làm đổi bit dấu, kết quả có thể âm, bằng 0 hoặc khác kỳ vọng toán học.

**Trọng tâm bài:** hiểu *bit được dịch*, *kiểu dữ liệu của kết quả*, *độ rộng 32/64 bit*, *khoảng dịch bị giới hạn*, và *tràn số*.

## 1. Dịch trái một bit diễn ra thế nào?

Ví dụ minh họa trên **8 bit thấp** (chỉ để dễ nhìn):

~~~text
Trước:  0000 0101   (5)
                << 1
Sau:    0000 1010   (10)
                << 2 từ giá trị 5
Sau:    0001 0100   (20)
~~~

Mỗi bit dịch `n` vị trí sang bên trái, các vị trí thấp mới được điền bằng `0`. Bit vượt qua mép trái **bị mất**.

Trong Java, toán hạng thực sự dùng cho phép dịch là **`int` 32 bit hoặc `long` 64 bit**, sau khi áp dụng *unary numeric promotion*. Không có phép dịch thực thi trực tiếp trên thanh ghi 8 bit `byte` chỉ vì biến nguồn là `byte`.

| Biểu thức | Kết quả `int` | Mẫu bit thấp minh họa |
| --- | ---: | --- |
| `3 << 2` | 12 | `0000 0011 → 0000 1100` |
| `5 << 1` | 10 | `0000 0101 → 0000 1010` |
| `-3 << 1` | −6 | `1111 1101 → 1111 1010` (8 bit thấp) |
| `0 << 12` | 0 | Tất cả bit đều bằng 0 |
| `1 << 0` | 1 | Không dịch |

Với một số âm, giá trị được biểu diễn theo **bù hai**. `-3 << 1` cho `-6` vì mẫu bù hai khi dịch trái một bit còn mang đúng giá trị đó. Đừng hiểu dấu `<<` là so sánh “nhỏ hơn hai lần”: nó là **một toán tử dịch bit**.

## 2. Chương trình Java 17+ chạy độc lập

Tạo file UTF-8 **`LeftShiftDemo.java`**:

~~~java
import java.math.BigInteger;

public class LeftShiftDemo {
    static String typeOf(int unused) {
        return "int";
    }

    static String typeOf(long unused) {
        return "long";
    }

    static String bits8(int value) {
        String s = Integer.toBinaryString(value & 0xFF);
        return "0".repeat(8 - s.length()) + s;
    }

    static String bits32(int value) {
        String s = Integer.toBinaryString(value);
        return "0".repeat(32 - s.length()) + s;
    }

    public static void main(String[] args) {
        System.out.println("== basic shifts ==");
        System.out.println("threeShiftTwo=" + (3 << 2));
        System.out.println("fiveShiftOne=" + (5 << 1));
        System.out.println("negativeShift=" + (-3 << 1));
        System.out.println("zeroShift=" + (0 << 12));
        System.out.println("oneShiftZero=" + (1 << 0));
        System.out.println("fiveBits8=" + bits8(5));
        System.out.println("tenBits8=" + bits8(5 << 1));
        System.out.println("negativeBits8=" + bits8(-3));
        System.out.println("negativeShiftBits8=" + bits8(-3 << 1));

        System.out.println("== promoted types ==");
        byte small = 3;
        short medium = 6;
        char letter = 'A';
        long large = 3L;
        System.out.println("byteResultType=" + typeOf(small << 2));
        System.out.println("byteShiftValue=" + (small << 2));
        System.out.println("shortResultType=" + typeOf(medium << 1));
        System.out.println("charShift=" + (letter << 1));
        System.out.println("longResultType=" + typeOf(large << 2));

        System.out.println("== shift distances ==");
        long distance = 33L;
        System.out.println("intByLongCount=" + (1 << distance));
        System.out.println("intBy32=" + (1 << 32));
        System.out.println("intBy33=" + (1 << 33));
        System.out.println("intByMinusOne=" + (1 << -1));
        System.out.println("longBy64=" + (1L << 64));
        System.out.println("longBy65=" + (1L << 65));

        System.out.println("== sign and overflow ==");
        System.out.println("intSignBit=" + (1 << 31));
        System.out.println("intSignBitPattern=" + bits32(1 << 31));
        System.out.println("longSignBit=" + (1L << 63));
        System.out.println("highBitShiftOne=" + (0x40000000 << 1));
        System.out.println("highBitShiftTwo=" + (0x40000000 << 2));
        System.out.println("maxIntShiftOne=" + (Integer.MAX_VALUE << 1));
        System.out.println("minIntShiftOne=" + (Integer.MIN_VALUE << 1));
        System.out.println("widenBeforeShift="
                + ((long) Integer.MAX_VALUE << 1));
        System.out.println("widenAfterShift="
                + ((long) (Integer.MAX_VALUE << 1)));

        byte narrow = 100;
        narrow <<= 1;
        System.out.println("compoundByte=" + narrow);
        System.out.println("compoundByteBits=" + bits8(narrow));

        System.out.println("== bit masks ==");
        int read = 1 << 0;
        int write = 1 << 1;
        int execute = 1 << 2;
        int permissions = read | execute;
        System.out.println("readAllowed=" + ((permissions & read) != 0));
        System.out.println("writeAllowed=" + ((permissions & write) != 0));
        System.out.println("executeAllowed=" + ((permissions & execute) != 0));
        System.out.println("groupedExpression=" + (1 << (2 + 1)));

        System.out.println("== big integers ==");
        System.out.println("bigShift="
                + BigInteger.ONE.shiftLeft(40));
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) trong thư mục chứa file; dùng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 LeftShiftDemo.java
java LeftShiftDemo
~~~

**Exact expected stdout** của riêng lệnh `java LeftShiftDemo`:

~~~text
== basic shifts ==
threeShiftTwo=12
fiveShiftOne=10
negativeShift=-6
zeroShift=0
oneShiftZero=1
fiveBits8=00000101
tenBits8=00001010
negativeBits8=11111101
negativeShiftBits8=11111010
== promoted types ==
byteResultType=int
byteShiftValue=12
shortResultType=int
charShift=130
longResultType=long
== shift distances ==
intByLongCount=2
intBy32=1
intBy33=2
intByMinusOne=-2147483648
longBy64=1
longBy65=2
== sign and overflow ==
intSignBit=-2147483648
intSignBitPattern=10000000000000000000000000000000
longSignBit=-9223372036854775808
highBitShiftOne=-2147483648
highBitShiftTwo=0
maxIntShiftOne=-2
minIntShiftOne=0
widenBeforeShift=4294967294
widenAfterShift=-2
compoundByte=-56
compoundByteBits=11001000
== bit masks ==
readAllowed=true
writeAllowed=false
executeAllowed=true
groupedExpression=8
== big integers ==
bigShift=1099511627776
~~~

Hai lệnh `-version` không có expected output cố định vì phụ thuộc bản JDK cài đặt. Chương trình không cần thư viện bên thứ ba.

## 3. Kiểu kết quả: `int` hoặc `long`, không nhất thiết là kiểu biến gốc

Java áp dụng **unary numeric promotion** cho từng toán hạng trước khi dịch:

- Toán hạng trái `byte`, `short`, `char` được nâng thành `int`; kết quả `<<` có kiểu **`int`**.
- Toán hạng trái `int` cho kết quả **`int`**; `long` cho kết quả **`long`**.
- Toán hạng phải cung cấp **khoảng cách dịch**, không quyết định kiểu kết quả. Nó cũng có thể là `long`.
- Không có phép `<<` trực tiếp cho `float`, `double`, `boolean` hay `String`.

~~~java
byte b = 3;
int x = b << 2; // 12, kiểu int.
// byte invalid = b << 2; // Lỗi: không tự gán int vào byte.

long y = 3L << 2; // 12L, kiểu long.
int z = 1 << 33L; // 2, không phải long.
~~~

Không áp dụng **binary numeric promotion** giống phép cộng số học để suy ra kiểu của toán tử dịch. Ví dụ `1 << 33L` vẫn trả `int`, vì kiểu toán hạng **trái** là `int`.

### `<<=` và phép chuyển kiểu về biến vế trái

Gán kết hợp `x <<= n` thực hiện dịch trái rồi gán lại kết quả về kiểu của `x`, có thể **thu hẹp** giá trị:

~~~java
byte value = 100;
value <<= 1; // Hợp lệ, kết quả byte là -56.
~~~

Phép dịch tạo kết quả `int 200`, sau đó gán về `byte` theo quy tắc compound assignment. `byte` có miền -128..127, nên mẫu bit thấp `11001000` được diễn giải thành **-56**.

Ngược lại, `value = value << 1;` không biên dịch nếu thiếu cast tường minh, vì biểu thức dịch có kiểu `int`.

## 4. Khoảng cách dịch phụ thuộc kiểu toán hạng trái

Java chỉ lấy **5 bit thấp** (`n & 31`) của khoảng dịch khi bên trái là `int`, và **6 bit thấp** (`n & 63`) khi bên trái là `long`. Các toán hạng hẹp `byte`/`short`/`char` được nâng thành `int` trước khi áp dụng quy tắc này.

~~~java
System.out.println(1 << 32);   // 1, vì khoảng dịch hiệu lực là 0.
System.out.println(1 << 33);   // 2, vì khoảng dịch hiệu lực là 1.
System.out.println(1L << 64);  // 1, vì khoảng dịch hiệu lực là 0.
System.out.println(1 << -1);   // -2147483648, vì -1 & 31 là 31.
~~~

Khoảng dịch âm hoặc vượt độ rộng vẫn được mask, không tự báo lỗi. Nếu `n` đến từ đầu vào và nghiệp vụ chỉ cho phép vị trí bit 0–31 hoặc 0–63, phải kiểm tra miền trước khi dịch. Bài riêng về giới hạn khoảng dịch sẽ đi sâu vào phép mask, kiểu bên trái và việc cast trước hay sau khi dịch.

## 5. Bit dấu và tràn số: `<<` không luôn là nhân đôi an toàn

`int` có 32 bit, `long` có 64 bit và đều là số nguyên **bù hai**. Khi `<<` đẩy bit 1 vào vị trí dấu hoặc làm rơi bit cao, giá trị có dấu có thể thay đổi bất ngờ:

~~~java
System.out.println(1 << 31); // Integer.MIN_VALUE = -2147483648.
System.out.println(1L << 63); // Long.MIN_VALUE.
System.out.println(0x40000000 << 1); // -2147483648.
System.out.println(0x40000000 << 2); // 0: bit cao đã bị loại.
~~~

Giải thích `1 << 31`:

~~~text
int 1:
00000000000000000000000000000001

Dịch trái 31:
10000000000000000000000000000000
^
Bit cao nhất = 1; diễn giải theo int có dấu là -2147483648.
~~~

Khi kết quả toán học vừa trong miền kiểu và không mất bit có nghĩa, dịch trái `n` vị trí phù hợp với nhân `2^n`. Nhưng với `int`, giá trị vượt miền bị **cắt về 32 bit thấp**; với `long` bị cắt về 64 bit thấp. `<<` **không ném `ArithmeticException` khi tràn**.

Ví dụ:

~~~java
int x = Integer.MAX_VALUE;
System.out.println(x << 1); // -2, không phải 4294967294.
~~~

Nếu cần phép nhân có kiểm tra tràn số, sử dụng `Math.multiplyExact` với toán hạng/kiểu phù hợp; nếu cần kết quả lớn hơn `int`, nâng kiểu **trước** khi dịch:

~~~java
int x = Integer.MAX_VALUE;
long correct = ((long) x) << 1; // 4294967294.
long tooLate = (long) (x << 1); // -2: int đã tràn trước cast.
~~~

`BigInteger.ONE.shiftLeft(40)` là một lựa chọn khi cần số nguyên rất lớn, không bị giới hạn trong 32 hoặc 64 bit (nhưng vẫn phụ thuộc tài nguyên máy).

## 6. Ứng dụng: tạo mặt nạ bit (*bit mask*)

`1 << k` đặt bit thứ `k` (đánh số từ 0, phải sang trái) thành 1; hữu ích cho các cờ trạng thái:

~~~java
int READ = 1 << 0;       // 001.
int WRITE = 1 << 1;      // 010.
int EXECUTE = 1 << 2;    // 100.

int flags = READ | EXECUTE; // 101.
boolean canRead = (flags & READ) != 0;   // true.
boolean canWrite = (flags & WRITE) != 0; // false.
~~~

Toán tử `|` kết hợp các cờ, `&` kiểm tra bit. `<<` tạo đúng vị trí bit cần dùng.

**Giới hạn:** với `int`, bit thứ 31 chính là bit dấu (`1 << 31` âm); với `long`, bit thứ 63 là bit dấu. Điều này vẫn hợp lệ với **mẫu bit** và phép `&`, nhưng dễ sai nếu đem mặt nạ đi so bằng `> 0`: giá trị âm có bit được bật vẫn có thể là mặt nạ hợp lệ. Thường hãy kiểm tra `(flags & mask) != 0`.

Nếu `k` đến từ đầu vào, xác minh `0 <= k && k < 32` khi dùng `int` hoặc `0 <= k && k < 64` khi dùng `long`, bởi Java **mask** khoảng cách dịch thay vì tự báo lỗi.

## 7. Độ ưu tiên, dấu ngoặc và so sánh với dịch phải

Toán tử `<<` có độ ưu tiên **thấp hơn `+`, `-`** nhưng **cao hơn các phép so sánh quan hệ** như `<` hoặc `>=`.

~~~java
int a = 1 << 2 + 1;     // 1 << (2 + 1) = 8.
int b = (1 << 2) + 1;   // 4 + 1 = 5.
boolean check = (1 << 3) > 5; // true.
~~~

Dùng dấu ngoặc rõ ràng trong mã cần nhiều phép toán, nhất là với các mặt nạ bit.

| Toán tử | Hướng dịch | Các bit mới được chèn |
| --- | --- | --- |
| `x << n` | Sang trái | Điền 0 ở phía phải |
| `x >> n` | Sang phải, giữ dấu | Điền bit dấu ở phía trái |
| `x >>> n` | Sang phải, không giữ dấu | Điền 0 ở phía trái |

`<<` không có biến thể riêng “dịch trái không dấu” `<<<`. Cả số âm và số dương đều được dịch trái bằng cùng quy tắc bit. Việc đọc kết quả theo dấu là do **kiểu `int` hoặc `long`**, không phải do toán tử tự chọn “dịch trái có dấu” hay “không dấu”.

## 8. Lỗi phổ biến cần tránh

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “`x << 1` lúc nào cũng giống `x * 2` theo giá trị toán học.” | Có thể tràn và mất bit |
| “`byte b; b << 1` cho kết quả `byte`.” | Kết quả có kiểu `int` sau promotion |
| “`1 << 32` cho 0 vì dịch hết 32 bit.” | Kết quả là **1** vì khoảng dịch mask |
| “`1L << 64` cho 0.” | Kết quả là **1** vì khoảng dịch mask theo 64 |
| “Khoảng dịch âm ném exception.” | Các bit thấp của khoảng dịch vẫn được dùng |
| “`1 << 31` là số dương `2147483648`.” | Trả `Integer.MIN_VALUE` (âm) |
| “Cast sang `long` sau khi dịch sẽ sửa tràn `int`.” | Phải cast **trước** khi dịch |
| “`byteValue <<= 1` và `byteValue = byteValue << 1` cùng hợp lệ.” | Dạng thứ hai cần cast |
| “`<<` ném ngoại lệ nếu bit dấu thay đổi.” | Không; tràn bị cắt theo độ rộng |
| “`1 << k` có thể dùng tùy ý với `k > 31` để cấp thêm bit.” | Không; dùng `1L`/`BigInteger` theo miền yêu cầu |

## 9. Bài tập tự kiểm tra

1. `3 << 2` bằng bao nhiêu?  
   *Đáp án:* **12** (`3 × 4`), không bị tràn.
2. `-3 << 1` cho bao nhiêu?  
   *Đáp án:* **-6** theo bù hai `int`.
3. Kiểu của `byte b = 3; var x = b << 2;` là gì?  
   *Đáp án:* `int`.
4. `1 << 32`, `1 << 33` và `1 << -1` lần lượt là gì?  
   *Đáp án:* **1**, **2**, **-2147483648**.
5. `1L << 64` và `1L << 65` bằng bao nhiêu?  
   *Đáp án:* **1L**, **2L**.
6. Tại sao `0x40000000 << 2` bằng 0?  
   *Đáp án:* Bit 1 cao bị dịch vượt khỏi độ rộng 32 bit.
7. `int x = Integer.MAX_VALUE; long y = (long) (x << 1);` — `y` bằng bao nhiêu?  
   *Đáp án:* **-2** vì tràn xảy ra trước cast.
8. Muốn tạo bit thứ 40 của một `long`, viết gì?  
   *Đáp án:* `1L << 40` (không phải `1 << 40`).
9. Vì sao nên kiểm tra mặt nạ bằng `(flags & mask) != 0` thay vì `> 0`?  
   *Đáp án:* Mặt nạ có thể bật bit dấu, khiến kết quả là số âm nhưng bit vẫn tồn tại.

## Checklist

- [ ] Hiểu `<<` dịch bit sang trái, chèn 0 ở bên phải và bỏ bit tràn phía trái.
- [ ] Nhớ kết quả sau promotion có kiểu `int` hoặc `long` dựa vào toán hạng trái.
- [ ] Biết giới hạn khoảng dịch: `int` dùng 5 bit thấp, `long` dùng 6 bit thấp.
- [ ] Nhận diện việc bit dấu thay đổi và tràn số **không tự báo lỗi**.
- [ ] Nâng kiểu lên `long` **trước** khi dịch nếu cần miền rộng hơn `int`.
- [ ] Hiểu gán `<<=` có thể thu hẹp kết quả về kiểu biến gốc.
- [ ] Dùng `1 << k` hoặc `1L << k` làm mặt nạ bit và kiểm tra `k` theo miền yêu cầu.
- [ ] Dùng dấu ngoặc để làm rõ các biểu thức kết hợp dịch, cộng và so sánh.

**Kết luận:** `<<` là **toán tử dịch bit**, không phải phép nhân chính xác vô điều kiện. Hãy luôn kiểm tra **kiểu toán hạng trái**, **khoảng dịch hiệu dụng**, **bit có thể bị mất** và **bit dấu** trước khi suy luận kết quả của phép dịch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bitwise-operators-004">Dịch phải có dấu &gt;&gt; và không dấu &gt;&gt;&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Dịch phải có dấu `>>` và không dấu `>>>` — Java 17+

## 1. Mục tiêu và khác biệt quan trọng nhất

Java có hai toán tử **dịch bit sang phải**:

- **`>>` — dịch phải có dấu** (*signed/arithmetic right shift*): đẩy các bit sang phải, lấp các vị trí mới bên trái bằng **bit dấu** của toán hạng sau khi nâng kiểu. Số âm tiếp tục có bit dấu 1.
- **`>>>` — dịch phải không dấu** (*unsigned/logical right shift*): đẩy các bit sang phải, **luôn lấp bằng bit 0** bên trái. Với số âm, kết quả có thể trở thành số dương rất lớn.

~~~java
int number = -8;
System.out.println(number >> 2);  // -2
System.out.println(number >>> 2); // 1073741822
~~~

Cả hai đều thao tác trên **biểu diễn bit**; chúng không phải toán tử so sánh `>` và cũng không chuyển kiểu `int` thành một kiểu unsigned mới.

**Cần nắm:** cách điền bit, chiều rộng `int`/`long`, quy tắc mask số bit cần dịch, nâng kiểu số nguyên nhỏ, phép gán dịch kết hợp, ứng dụng trích xuất bit và khác biệt so với phép chia số nguyên.

## 2. Quan sát bit qua hệ thập lục phân

Trong Java, `int` dùng **32 bit** và `long` dùng **64 bit**, số âm biểu diễn theo **bù hai** (*two's complement*). Với `int n = -8`:

~~~text
n        = 11111111 11111111 11111111 11111000  (0xFFFFFFF8)
n >> 2   = 11111111 11111111 11111111 11111110  (0xFFFFFFFE = -2)
n >>> 2  = 00111111 11111111 11111111 11111110  (0x3FFFFFFE = 1073741822)
~~~

Bốn điểm dễ kiểm chứng:

1. Bit trái nhất của `-8` là **1**.
2. `-8 >> 2` điền thêm **hai bit 1** bên trái.
3. `-8 >>> 2` điền thêm **hai bit 0** bên trái.
4. Kết quả `>>>` vẫn là **kiểu `int`**, chỉ khác giá trị bit. Không có kiểu nguyên thủy `uint` trong Java.

In dạng hex bằng JDK:

~~~java
System.out.println(Integer.toHexString(-8));        // fffffff8
System.out.println(Integer.toHexString(-8 >> 2));   // fffffffe
System.out.println(Integer.toHexString(-8 >>> 2));  // 3ffffffe
~~~

`Integer.toHexString` biểu diễn bit của `int` ở dạng hex không dấu, rất hữu ích để kiểm tra thao tác bit.

## 3. Với số không âm, `>>` và `>>>` thường giống nhau

~~~java
int number = 24;

System.out.println(number >> 2);  // 6
System.out.println(number >>> 2); // 6
~~~

Vì bit cao nhất của số `int` không âm bằng 0, `>>` điền bit 0 giống `>>>`.

| Giá trị `int` | `>> 2` | `>>> 2` | Nhận xét |
|---:|---:|---:|---|
| `24` | `6` | `6` | Cùng bit điền 0 |
| `8` | `2` | `2` | Cùng kết quả |
| `0` | `0` | `0` | Zero giữ nguyên |
| `-8` | `-2` | `1073741822` | Khác vì bit dấu |
| `-1` | `-1` | `1073741823` | Dịch 2 bit |

**Giới hạn cách hiểu:** “giống nhau” ở đây là với số `int`/`long` **không âm**, khi dùng cùng khoảng dịch. Với số âm và khoảng dịch dương hữu hiệu, hai toán tử có cách điền bit khác nhau.

## 4. `>>`: dịch phải có dấu

Quy tắc `x >> n` điền bit cao mới bằng bit dấu của `x` sau khi áp dụng quy tắc kiểu. Đối với số âm, bit dấu luôn là 1 sau dịch; đối với số không âm, bit dấu là 0.

~~~java
System.out.println(-8 >> 1); // -4
System.out.println(-8 >> 2); // -2
System.out.println(-1 >> 5); // -1
~~~

Tính chất hay dùng: với `int`/`long` và khoảng dịch hữu hiệu `n`, `x >> n` tương ứng với chia cho `2^n` và **lấy phần nguyên xuống** (*floor*) về phía âm vô cực. Với số âm lẻ, điều này **khác phép chia nguyên `/`**, vốn cắt thương về 0:

~~~java
System.out.println(-7 / 2);           // -3
System.out.println(-7 >> 1);          // -4
System.out.println(Math.floorDiv(-7, 2)); // -4
~~~

**Không dùng `x >> 1` thay cho `x / 2` nếu cần thương cắt về 0 với số âm.** Với số nguyên không âm, cả hai có thể cùng kết quả khi số chia là lũy thừa 2.

## 5. `>>>`: dịch phải không dấu (logic)

`x >>> n` luôn điền bit 0 bên trái. Với `int` âm, kết quả thường trở thành một `int` không âm khi dịch ít nhất một bit:

~~~java
int negative = -8;
System.out.println(negative >>> 1); // 2147483644
System.out.println(negative >>> 2); // 1073741822
~~~

Ví dụ `-1` có 32 bit đều bằng 1:

~~~java
System.out.println(-1 >>> 1); // 2147483647
~~~

`>>>` hữu ích khi muốn coi mẫu bit như một **giá trị không dấu** để dịch, ví dụ tách các bit dữ liệu từ một `int`. Nó không đổi loại biến sang một kiểu unsigned và không cho phép biểu diễn toàn bộ miền unsigned 32 bit thành `int` dương **mà không có xử lý bổ sung**.

Java 17 hỗ trợ một số API diễn giải `int` dưới dạng unsigned, như `Integer.toUnsignedLong`, `Integer.compareUnsigned` và `Integer.divideUnsigned`.

~~~java
System.out.println(Integer.toUnsignedLong(-8)); // 4294967288
System.out.println(Integer.divideUnsigned(-8, 4)); // 1073741822
~~~

Trong trường hợp chia unsigned cho `4 = 2^2`, `Integer.divideUnsigned(-8, 4)` bằng `-8 >>> 2`. Khi mẫu không phải lũy thừa hai, không thay phép chia bằng shift một cách máy móc.

## 6. `int` và `long`: chiều rộng khác nhau

| Kiểu toán hạng bên trái sau nâng kiểu | Chiều rộng phép dịch | Số bit dùng xác định khoảng dịch |
|---|---:|---|
| `int` | 32 bit | **5 bit thấp** của khoảng dịch |
| `long` | 64 bit | **6 bit thấp** của khoảng dịch |

~~~java
int a = -8;
long b = -8L;

System.out.println(a >>> 2); // 1073741822
System.out.println(b >>> 2); // 4611686018427387902
~~~

Hai biểu thức khác nhau vì với `long` có **64 bit**, việc điền 0 cho hai bit cao để lại 62 bit biểu diễn kết quả.

~~~java
System.out.println(Long.toHexString(-8L >> 2));  // fffffffffffffffe
System.out.println(Long.toHexString(-8L >>> 2)); // 3ffffffffffffffe
~~~

**Hãy dùng literal `L` khi muốn phép dịch 64 bit.** Không nên tính trên `int` rồi mới gán sang `long` và kỳ vọng kết quả sẽ giống dịch `long` ngay từ đầu.

## 7. Khoảng dịch được mask trước khi tính

Cả `>>` và `>>>` đều dùng **5 bit thấp** của số lượng dịch nếu toán hạng trái là `int`, hoặc **6 bit thấp** nếu là `long`. Ví dụ:

~~~java
System.out.println(-8 >>> 32);   // -8: 32 & 31 = 0.
System.out.println(-8 >>> 34);   // 1073741822: dịch hiệu lực 2 bit.
System.out.println(-8L >>> 66);  // 4611686018427387902: 66 & 63 = 2.
System.out.println(1 >> -1);     // 0: -1 & 31 = 31.
~~~

Các khoảng dịch âm hoặc vượt độ rộng không tự ném ngoại lệ. Nếu số lượng dịch là dữ liệu do người dùng cung cấp, hãy kiểm tra giới hạn trước khi dịch, bởi các bit bị mask có thể khiến đầu vào sai tạo kết quả có vẻ hợp lệ. Bài về giới hạn khoảng dịch phân tích đầy đủ quy tắc này; ở đây điều cần quan sát là **cách `>>` và `>>>` điền bit vẫn khác nhau khi khoảng dịch hiệu lực giống nhau**.

## 8. `byte`, `short`, `char` được nâng thành `int` trước khi dịch

Toán tử dịch áp dụng *unary numeric promotion* cho **từng toán hạng**; kiểu kết quả của biểu thức dịch là kiểu toán hạng **bên trái sau nâng kiểu**. Khác với cộng số, toán hạng phải **không làm toán hạng trái trở thành `long`** chỉ vì số dịch thuộc kiểu `long`.

~~~java
byte small = -8;
int result = small >>> 2;

System.out.println(result); // 1073741822
~~~

`small` được **sign-extend từ byte lên int** trước, thành `0xFFFFFFF8`; sau đó `>>>` dịch trên 32 bit. Không phải Java đang dịch trực tiếp trên 8 bit rồi tạo một số không dấu 8 bit.

Với `char`, vì `char` lưu **UTF-16 code unit không dấu**, chuyển lên `int` có giá trị từ 0 tới 65535, nên bit dấu int vẫn bằng 0:

~~~java
char codeUnit = '\uFF80';
System.out.println(codeUnit >> 4);  // 4088
System.out.println(codeUnit >>> 4); // 4088
~~~

Với `short` âm, kết quả cũng chịu sign-extension sang int trước khi dịch.

### `>>>=`: gán dịch kết hợp có thu hẹp ngầm

~~~java
byte small = -8;
small >>>= 2;
System.out.println(small); // -2
~~~

Mô hình cơ bản của `small >>>= 2` là dịch trên `int`, rồi **thu hẹp** kết quả về `byte` (giữ 8 bit thấp). Vì thế kết quả gán lại là -2, trong khi biểu thức `small >>> 2` gán vào `int` cho 1073741822.

~~~java
// small = small >>> 2; // LỖI COMPILE: biểu thức bên phải là int
small = (byte) (small >>> 2); // Có cast tường minh, nhưng xem lại giá trị hiện tại của small
~~~

Quy tắc `op=` còn có điểm đặc biệt: **vế trái chỉ được đánh giá một lần**. Không nên thay máy móc các compound assignment có chỉ số mảng hoặc method side effect bằng dạng gán dài.

## 9. Dịch bit không phải luôn là phép chia số học

| Biểu thức | Với `x = -7` | Giải thích |
|---|---:|---|
| `x / 2` | `-3` | Chia nguyên, cắt về 0 |
| `x >> 1` | `-4` | Dịch có dấu, tương ứng floor |
| `x >>> 1` | `2147483644` | Dịch 32 bit với điền zero |

Việc dùng `>>>` thay cho phép chia số nguyên **đặc biệt nguy hiểm** nếu dữ liệu có thể âm: nó diễn giải bit như unsigned, cho kết quả rất khác.

Với số nguyên **không âm**, `x >> n` và `x >>> n` thường tương ứng với thương của phép chia cho `2^n` khi `n` nằm trong khoảng hợp lý. Nhưng **ưu tiên `/` khi code cần diễn đạt phép chia**: dễ đọc hơn và không khiến người bảo trì hiểu sai về dấu hay khoảng dịch.

## 10. Dùng `>>>` để trích xuất trường bit

Giả sử một `int` đóng gói 4 byte:

~~~text
0x12 34 AB CD
~~~

Muốn lấy byte thứ hai từ phải (`0xAB`):

~~~java
int packed = 0x1234ABCD;
int extracted = (packed >>> 8) & 0xFF;

System.out.println(extracted); // 171 (0xAB)
~~~

Diễn biến:

1. `packed >>> 8` dịch bit để đưa `0xAB` vào vùng 8 bit thấp.
2. `& 0xFF` giữ **8 bit thấp**, xóa các bit khác.
3. Kết quả là 171, giá trị unsigned của byte `0xAB`.

Tương tự, tách **bit dấu** từ một `int`:

~~~java
int signed = Integer.MIN_VALUE;
int highBit = signed >>> 31;
System.out.println(highBit); // 1
~~~

Nếu dùng `signed >> 31`, kết quả là **-1** vì `>>` điền toàn 1 ở bit trái. Đây là lý do `>>>` thường được chọn khi xử lý cấu trúc bit/flags.

**Chú ý:** trích xuất bit không tự xác nhận dữ liệu đầu vào hợp lệ. Với protocol, hãy xác nhận độ dài, kiểu dữ liệu và vị trí trường bit trước khi dịch/mask.

## 11. Ưu tiên toán tử và dấu ngoặc

`>>` và `>>>` thuộc nhóm **toán tử dịch**; chúng có độ ưu tiên thấp hơn `+`/`-`, nhưng cao hơn nhiều toán tử so sánh/bitwise boolean. Đặt ngoặc để thể hiện rõ ý định:

~~~java
int flags = 0x80000000;
boolean topBitSet = (flags >>> 31) == 1;
System.out.println(topBitSet); // true
~~~

Với mask:

~~~java
int byteValue = (packed >>> 8) & 0xFF;
~~~

Dấu ngoặc quanh phép dịch làm rõ việc **dịch trước, mask sau**, dù phép dịch có ưu tiên cao hơn `&`.

Đừng nhầm `>>`/`>>>` trong **biểu thức số học** với ký tự `>` xuất hiện trong mã generic như `Map<String, List<Integer>>`: đây là hai ngữ cảnh cú pháp khác nhau.

## 12. Lỗi phổ biến và cách xử lý

| Ngộ nhận/lỗi | Vì sao sai | Cách đúng |
|---|---|---|
| `>>` và `>>>` là như nhau | Khác cách điền bit cao khi số âm | Chọn theo mục tiêu signed/bitwise |
| `-8 >>> 2` là `-2` | `>>>` điền 0, ra 1073741822 | `-8 >> 2` nếu muốn giữ dấu |
| `>>> ` làm int thành unsigned primitive | Java không có kiểu `uint` nguyên thủy | Dùng các API unsigned nếu cần |
| `-7 >> 1` bằng `-7 / 2` | Dịch có dấu làm tròn xuống, chia nguyên cắt về 0 | Dùng `/` nếu cần cắt về 0 |
| `int x; x >>> 32` cho 0 | Khoảng dịch int được mask 5 bit | Validate khoảng dịch nếu cần |
| `long x` dùng phép dịch 32 bit | long dùng 64 bit | Kiểm tra kiểu toán hạng trái |
| `byte >>> 2` dịch trên 8 bit | byte được nâng thành int 32 bit | Cân nhắc sign-extension |
| `byte b; b >>>= 2` trả một byte unsigned lớn | Compound assignment thu hẹp về byte | Xem giá trị int trung gian |
| `x >>> 2` có cùng nghĩa với unsigned `x / 4` ở mọi trường hợp | Chỉ đúng khi xử lý bit unsigned 32/64 và mẫu lũy thừa hai | Dùng `divideUnsigned` khi phù hợp |
| Dùng `>>>` để kiểm tra số âm | `>>>` làm đổi bit dấu của kết quả khi dịch | Dùng `x < 0` khi ý định là so dấu |
| Viết `flags >>> 31 == 1` không chú giải | Dễ hiểu sai precedence | Viết `(flags >>> 31) == 1` |
| Giả định shift luôn nhanh hơn phép chia | JVM/JIT có tối ưu riêng | Ưu tiên ngữ nghĩa rõ, benchmark nếu cần |
| Dùng khoảng dịch âm như một tính năng nghiệp vụ | Java mask bit, có thể gây bất ngờ | Validate `n >= 0` và giới hạn phù hợp |

## 13. Chương trình Java 17 chạy độc lập

Tạo đúng file **`SignedUnsignedShiftDemo.java`**:

~~~java
public final class SignedUnsignedShiftDemo {
    private SignedUnsignedShiftDemo() {}

    static void showInt(String label, int value) {
        System.out.println(label + "=" + value
                + ",hex=" + Integer.toHexString(value));
    }

    public static void main(String[] args) {
        showInt("positive >>2", 8 >> 2);
        showInt("positive >>>2", 8 >>> 2);

        showInt("negative original", -8);
        showInt("negative >>2", -8 >> 2);
        showInt("negative >>>2", -8 >>> 2);
        showInt("negative >>>1", -8 >>> 1);
        showInt("all ones >>>1", -1 >>> 1);

        showInt("MIN >>1", Integer.MIN_VALUE >> 1);
        showInt("MIN >>>1", Integer.MIN_VALUE >>> 1);

        long wide = -8L;
        System.out.println("long >>2=" + (wide >> 2));
        System.out.println("long >>>2=" + (wide >>> 2));
        System.out.println("long >>2 hex="
                + Long.toHexString(wide >> 2));
        System.out.println("long >>>2 hex="
                + Long.toHexString(wide >>> 2));

        showInt("int >>>32", -8 >>> 32);
        showInt("int >>>34", -8 >>> 34);
        System.out.println("long >>>66=" + (wide >>> 66));
        System.out.println("negative shift=" + (1 >> -1));

        byte small = -8;
        showInt("byte >>>2 promoted", small >>> 2);
        small >>>= 2;
        System.out.println("byte >>>=2=" + small);

        char codeUnit = '\uFF80';
        System.out.println("char >>4=" + (codeUnit >> 4));
        System.out.println("char >>>4=" + (codeUnit >>> 4));

        System.out.println("negative division=" + (-7 / 2));
        System.out.println("negative >>1=" + (-7 >> 1));
        System.out.println("negative >>>1=" + (-7 >>> 1));
        System.out.println("floorDiv=" + Math.floorDiv(-7, 2));

        System.out.println("unsigned long view="
                + Integer.toUnsignedLong(-8));
        System.out.println("unsigned /4="
                + Integer.divideUnsigned(-8, 4));

        int packed = 0x1234ABCD;
        int extracted = (packed >>> 8) & 0xFF;
        System.out.println("extracted byte=" + extracted);

        int sign = Integer.MIN_VALUE;
        System.out.println("top bit unsigned=" + (sign >>> 31));
        System.out.println("top bit signed=" + (sign >> 31));
        System.out.println("top bit set=" + ((sign >>> 31) == 1));
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal đang mở tại thư mục chứa file; không cần Maven, Gradle hay thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 SignedUnsignedShiftDemo.java
java -cp . SignedUnsignedShiftDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\SignedUnsignedShiftDemo.java
java -cp . SignedUnsignedShiftDemo
~~~

### Exact expected stdout

~~~text
positive >>2=2,hex=2
positive >>>2=2,hex=2
negative original=-8,hex=fffffff8
negative >>2=-2,hex=fffffffe
negative >>>2=1073741822,hex=3ffffffe
negative >>>1=2147483644,hex=7ffffffc
all ones >>>1=2147483647,hex=7fffffff
MIN >>1=-1073741824,hex=c0000000
MIN >>>1=1073741824,hex=40000000
long >>2=-2
long >>>2=4611686018427387902
long >>2 hex=fffffffffffffffe
long >>>2 hex=3ffffffffffffffe
int >>>32=-8,hex=fffffff8
int >>>34=1073741822,hex=3ffffffe
long >>>66=4611686018427387902
negative shift=0
byte >>>2 promoted=1073741822,hex=3ffffffe
byte >>>=2=-2
char >>4=4088
char >>>4=4088
negative division=-3
negative >>1=-4
negative >>>1=2147483644
floorDiv=-4
unsigned long view=4294967288
unsigned /4=1073741822
extracted byte=171
top bit unsigned=1
top bit signed=-1
top bit set=true
~~~

### Cách đối chiếu kết quả

1. **Giá trị không âm:** `8 >> 2` và `8 >>> 2` đều bằng 2.
2. **Giá trị âm:** `-8 >> 2` còn dấu âm (hex `fffffffe`), nhưng `-8 >>> 2` điền 0 (hex `3ffffffe`).
3. **Bit dấu cao:** `MIN_VALUE >> 1` điền bit 1; `MIN_VALUE >>> 1` điền 0.
4. **Chiều rộng:** phép dịch `long` có kết quả khác `int`; `Long.toHexString` cho 16 chữ số hex khi cần.
5. **Mask khoảng dịch:** `int >>> 32` tương ứng dịch 0; `int >>> 34` dịch 2; `long >>> 66` cũng dịch 2.
6. **Nâng kiểu:** `byte >>> 2` được tính trên int; `byte >>>= 2` chuyển kết quả trở lại byte.
7. **Phép chia:** `-7 / 2` là -3, khác `-7 >> 1` là -4.
8. **Unsigned API:** `Integer.toUnsignedLong(-8)` cho 4294967288, còn chia unsigned cho 4 cho 1073741822.
9. **Trích xuất bit:** dịch `packed` rồi `& 0xFF` lấy `0xAB = 171`; bit cao nhất của `MIN_VALUE` được tách thành 1 bằng `>>>`.

## 14. Bài tập có đáp án

### Bài A — âm với hai cách dịch

~~~java
int x = -16;
System.out.println(x >> 2);
System.out.println(x >>> 2);
~~~

**Đáp án:**

~~~text
-4
1073741820
~~~

Vì `-16` có hex `0xfffffff0`; dịch logic hai bit tạo `0x3ffffffc`.

### Bài B — trích xuất một byte

Cho `int packed = 0x12AB34CD;`, lấy byte ở vị trí bit 16–23 (`0xAB`) dưới dạng số từ 0 tới 255.

**Đáp án:**

~~~java
int extracted = (packed >>> 16) & 0xFF;
System.out.println(extracted); // 171
~~~

### Bài C — tác động của mask

~~~java
System.out.println(-1 >>> 32);
System.out.println(-1 >>> 33);
~~~

**Đáp án:**

~~~text
-1
2147483647
~~~

Trên `int`, số dịch 32 trở thành 0; 33 trở thành 1.

### Bài D — byte được nâng kiểu

~~~java
byte b = -1;
int result = b >>> 1;
System.out.println(result);
~~~

**Đáp án:** `2147483647`. Byte `-1` được nâng có dấu thành int `0xffffffff`, rồi `>>> 1` thành `0x7fffffff`.

### Bài E — dịch và chia nguyên

~~~java
int x = -9;
System.out.println(x / 2);
System.out.println(x >> 1);
~~~

**Đáp án:**

~~~text
-4
-5
~~~

Phép chia nguyên cắt về 0, còn dịch phải có dấu với 1 bit tương ứng floor.

### Bài F — chọn toán tử

Muốn lấy bit dấu cao nhất của `int flags` dưới dạng **0 hoặc 1**, dùng biểu thức nào?

**Đáp án:** `(flags >>> 31) & 1` hoặc đơn giản `flags >>> 31`. Với `int`, dịch logic 31 bit chỉ để lại bit cao ban đầu ở vị trí thấp nhất; kết quả là 0 hoặc 1. `flags >> 31` lại có thể trả 0 hoặc -1.

## 15. Checklist và kết luận

- [ ] Hiểu `>>` điền bit dấu, `>>>` điền zero ở bên trái.
- [ ] Biết hai toán tử cho cùng kết quả với số nguyên không âm và cùng khoảng dịch.
- [ ] Tự giải thích được `-8 >> 2 = -2`, `-8 >>> 2 = 1073741822`.
- [ ] Nhớ `int` là 32 bit, `long` là 64 bit.
- [ ] Biết `int` mask 5 bit khoảng dịch, `long` mask 6 bit.
- [ ] Biết `byte`/`short`/`char` được nâng thành int trước khi dịch.
- [ ] Phân biệt biểu thức `byte >>> 2` kiểu int với `byte >>>= 2` gán lại byte.
- [ ] Không nhầm `>>` với phép chia số nguyên cắt về 0 khi số âm.
- [ ] Biết `>>>` có ích khi trích xuất bit và coi mẫu bit như unsigned.
- [ ] Hiểu `>>>` không tạo kiểu primitive unsigned mới.
- [ ] Dùng ngoặc khi kết hợp dịch và mask; xét sign-extension trước khi chuyển kiểu.
- [ ] Validate số bit dịch nếu nghiệp vụ không cho phép giá trị âm hoặc quá chiều rộng.
- [ ] Chạy `SignedUnsignedShiftDemo.java` bằng Java 17+ và đối chiếu exact stdout.

**Kết luận:** `>>` giữ dấu bằng cách điền bit dấu; `>>>` dịch logic bằng cách điền bit 0. Sự khác biệt nổi bật ở **số âm**, ngoài ra còn cần chú ý **32/64 bit, mask khoảng dịch, numeric promotion và compound assignment**. Dùng `>>>` để thao tác mẫu bit/unsigned một cách chủ đích; dùng toán tử chia khi mục tiêu thực sự là số học, đặc biệt với số âm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bitwise-operators-005">Quy tắc giới hạn số bit dịch theo kiểu toán hạng</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc giới hạn số bit dịch theo kiểu toán hạng trong Java

> **Java core — language-basics / Toán tử bit**  
> **Môi trường:** JDK 17+, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Biết Java xác định số bit dịch thực sự theo **kiểu của toán hạng bên trái sau khi nâng kiểu**, giải thích vì sao dịch 32/64 bit hoặc dịch với số âm không báo lỗi, và tránh mất dữ liệu vì chuyển sang `long` quá muộn.

## 1. Toán tử dịch bit và hai toán hạng

Java có ba toán tử dịch bit dành cho các kiểu số nguyên:

| Toán tử | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `x << n` | Dịch trái, điền bit 0 ở bên phải | `1 << 3` → 8 |
| `x >> n` | Dịch phải **có dấu**, giữ bit dấu ở bên trái | `-8 >> 1` → -4 |
| `x >>> n` | Dịch phải **không dấu**, điền bit 0 ở bên trái | `-8 >>> 1` → 2147483644 |

Trong `x << n`, `x` là **giá trị bị dịch**, `n` là **số lượng dịch** (*shift distance*). Cả hai phải là kiểu nguyên (hoặc wrapper được unbox); `float`, `double` và `boolean` không được dùng làm toán hạng dịch.

**Đừng hiểu số lượng dịch là số bit Java sẽ dùng nguyên vẹn.** Java chỉ lấy một số bit thấp của `n`, theo kiểu kết quả của toán hạng **trái**.

## 2. Quy tắc quan trọng nhất: `int` dùng 5 bit thấp, `long` dùng 6 bit thấp

| Kiểu toán hạng trái **sau unary numeric promotion** | Độ rộng | Bit thấp của số lượng dịch được dùng | Số lượng dịch hiệu lực |
| --- | ---: | --- | --- |
| `int` | 32 bit | 5 bit thấp, mask `0x1F` | `n & 0x1F`, từ 0 đến 31 |
| `long` | 64 bit | 6 bit thấp, mask `0x3F` | `n & 0x3F`, từ 0 đến 63 |
| `byte`, `short`, `char` | Nâng thành `int` | **5 bit thấp**, không phải 3/4 bit | `n & 0x1F` |

Ví dụ dễ nhớ:

~~~java
int a = 1;
System.out.println(a << 31); // -2147483648
System.out.println(a << 32); // 1, vì 32 & 31 == 0
System.out.println(a << 33); // 2, vì 33 & 31 == 1

long b = 1L;
System.out.println(b << 32); // 4294967296
System.out.println(b << 64); // 1, vì 64 & 63 == 0
System.out.println(b << 65); // 2, vì 65 & 63 == 1
~~~

**Mấu chốt:** `1 << 32` bằng **1**, không phải 0; còn `1L << 32` bằng **4294967296**. Java không tự động ném ngoại lệ khi số lượng dịch đạt hoặc vượt độ rộng bit.

Đây là phép lấy **các bit thấp của khoảng dịch**, không phải cơ chế kiểm tra `n <= 31` hay `n <= 63` rồi từ chối trường hợp vượt giới hạn.

## 3. Số lượng dịch âm vẫn được mask

Java **không báo lỗi** chỉ vì số lượng dịch âm; số nguyên âm cũng có dạng bit, và Java vẫn lấy các bit thấp:

~~~java
int intEffective = -1 & 0x1F;  // 31
long longEffective = -1 & 0x3F; // 63

int highInt = 1 << -1;   // như 1 << 31: -2147483648
long highLong = 1L << -1; // như 1L << 63: -9223372036854775808
~~~

| Biểu thức | Số lượng dịch hiệu lực | Kết quả |
| --- | ---: | ---: |
| `1 << 0` | 0 | 1 |
| `1 << 31` | 31 | -2147483648 |
| `1 << 32` | 0 | 1 |
| `1 << 33` | 1 | 2 |
| `1 << -1` | 31 | -2147483648 |
| `1L << 32` | 32 | 4294967296 |
| `1L << 63` | 63 | -9223372036854775808 |
| `1L << 64` | 0 | 1 |
| `1L << -1` | 63 | -9223372036854775808 |

Không nên dùng mask tự động của toán tử dịch làm cách “kiểm tra hợp lệ” input. Nếu nghiệp vụ quy định chỉ chấp nhận `0 <= n && n < 32` hoặc `0 <= n && n < 64` thì **kiểm tra trước** rồi mới dịch; nếu không, input bất thường có thể âm thầm biến thành một khoảng dịch khác.

## 4. Kiểu toán hạng **trái** quyết định độ rộng, không phải biến nhận kết quả

Hai toán hạng của toán tử dịch được áp dụng *unary numeric promotion* riêng biệt. Kiểu của **toán hạng trái đã nâng kiểu** là kiểu kết quả; toán hạng phải chỉ cung cấp số lượng dịch.

~~~java
int x = 1;

long tooLate = x << 40;        // int dịch 40 -> 40 & 31 = 8 -> 256
long correct = ((long) x) << 40; // long dịch đúng 40 -> 1099511627776
~~~

`tooLate` có kiểu `long` ở **biến đích**, nhưng phép dịch được thực hiện bằng `int` trước khi gán. Kể cả `(long) (x << 40)` cũng chuyển kiểu **sau phép dịch**, nên vẫn cho 256.

Dùng `1L` hoặc cast bên trái **trước phép dịch** khi cần dãy 64 bit:

~~~java
long bit40 = 1L << 40;    // 1099511627776
long bit40b = (long) 1 << 40; // cùng kết quả
~~~

Kiểu **toán hạng phải** không nâng kiểu kết quả bên trái thành `long`:

~~~java
int value = 1;
long distance = 33L;
int shifted = value << distance; // 2: 33 & 31 = 1
~~~

**Kết luận:** Trong phép `left << distance`, hãy nhìn vào **kiểu left**, không nhìn kiểu của `distance` hay kiểu biến đích.

## 5. `byte`, `short`, `char` được nâng thành `int`

Dù `byte` có 8 bit, `short`/`char` có 16 bit, phép dịch vẫn dùng **quy tắc 32 bit** vì toán hạng trái được nâng thành `int` trước phép toán:

~~~java
byte small = 1;
short brief = 1;
char ch = 1;

int a = small << 8;  // 256, không phải 0
int b = brief << 16; // 65536
int c = ch << 32;    // 1, vì 32 & 31 = 0
~~~

Dòng `byte result = small << 8;` không biên dịch khi `small` là biến: biểu thức có kiểu `int`, không được gán ngầm vào `byte`. Nếu ép về `byte` sau phép dịch, giá trị có thể bị cắt mất các bit cao:

~~~java
byte narrowed = (byte) (small << 8); // 0
~~~

Đừng nghĩ “dịch byte quá 8 bit nên bị mask theo 8” — phép dịch trả `int`; chỉ khi cast về `byte` mới bị thu hẹp về 8 bit.

## 6. Quy tắc mask áp dụng cho cả `<<`, `>>`, `>>>`

Không chỉ phép dịch trái: cả ba toán tử đều dùng quy tắc 5/6 bit thấp của số lượng dịch.

~~~java
int x = -8;
int signed = x >> 33;   // -4, vì 33 & 31 = 1
int unsigned = x >>> 33; // 2147483644, cũng dịch 1 bit
~~~

`>>` giữ bit dấu khi dịch phải; `>>>` điền bit 0 từ bên trái. Chúng có thể cho kết quả rất khác nhau khi `x` âm, nhưng **quy tắc giới hạn số lượng dịch là giống nhau**.

| Biểu thức | Kết quả | Vì sao |
| --- | ---: | --- |
| `-8 >> 1` | -4 | Dịch phải số âm và giữ dấu |
| `-8 >> 33` | -4 | 33 trở thành 1 |
| `-8 >>> 1` | 2147483644 | Điền 0 từ bên trái |
| `-8 >>> 33` | 2147483644 | 33 trở thành 1 |
| `1 >>> 32` | 1 | 32 trở thành 0 |
| `1L >>> 64` | 1 | 64 trở thành 0 |

Điều này không đồng nghĩa với các phép dịch “không thể mất bit”. Phép dịch trái có thể đẩy bit cao ra khỏi miền biểu diễn, dẫn tới kết quả âm hoặc mất dữ liệu; Java không tự ném ngoại lệ tràn số vì phép dịch.

## 7. Chương trình Java 17+ runnable

Tạo file **ShiftDistanceDemo.java**:

~~~java
public final class ShiftDistanceDemo {
    public static void main(String[] args) {
        int one = 1;
        long wide = 1L;

        System.out.println("int31=" + (one << 31));
        System.out.println("int32=" + (one << 32));
        System.out.println("int33=" + (one << 33));
        System.out.println("intMinusOne=" + (one << -1));

        System.out.println("long32=" + (wide << 32));
        System.out.println("long63=" + (wide << 63));
        System.out.println("long64=" + (wide << 64));
        System.out.println("long65=" + (wide << 65));
        System.out.println("longMinusOne=" + (wide << -1));

        long tooLate = one << 40;
        long correct = ((long) one) << 40;
        System.out.println("lateWiden=" + tooLate);
        System.out.println("earlyWiden=" + correct);

        long distance = 33L;
        int intWithLongDistance = one << distance;
        System.out.println("longDistanceOnInt=" + intWithLongDistance);

        byte small = 1;
        short brief = 1;
        char ch = 1;
        System.out.println("byteShift=" + (small << 8));
        System.out.println("shortShift=" + (brief << 16));
        System.out.println("charShift32=" + (ch << 32));
        System.out.println("narrowedByte=" + (byte) (small << 8));

        int negative = -8;
        System.out.println("signedRight33=" + (negative >> 33));
        System.out.println("unsignedRight33=" + (negative >>> 33));
        System.out.println("unsignedNoShift=" + (one >>> 32));
        System.out.println("longUnsignedNoShift=" + (wide >>> 64));

        System.out.println("maskedIntMinusOne=" + (-1 & 0x1F));
        System.out.println("maskedLongMinusOne=" + (-1 & 0x3F));
    }
}
~~~

Mở terminal trong thư mục chứa file và chạy (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ShiftDistanceDemo.java
java ShiftDistanceDemo
~~~

**Expected stdout chính xác:**

~~~text
int31=-2147483648
int32=1
int33=2
intMinusOne=-2147483648
long32=4294967296
long63=-9223372036854775808
long64=1
long65=2
longMinusOne=-9223372036854775808
lateWiden=256
earlyWiden=1099511627776
longDistanceOnInt=2
byteShift=256
shortShift=65536
charShift32=1
narrowedByte=0
signedRight33=-4
unsignedRight33=2147483644
unsignedNoShift=1
longUnsignedNoShift=1
maskedIntMinusOne=31
maskedLongMinusOne=63
~~~

Các dòng `int32=1` và `long64=1` cho thấy Java **mask số lượng dịch**, không dừng ở biên 32/64. `lateWiden=256` và `earlyWiden=1099511627776` chứng minh cần chuyển kiểu bên trái trước phép dịch. Các dòng `byteShift`, `shortShift` và `charShift32` chứng minh `byte`/`short`/`char` sử dụng quy tắc `int`.

## 8. Checklist và bài tập

| Nếu cần... | Hãy nhớ... |
| --- | --- |
| Dịch `int` | Số lượng dịch hiệu lực `n & 0x1F` |
| Dịch `long` | Số lượng dịch hiệu lực `n & 0x3F` |
| Dịch `byte`/`short`/`char` | Trái được nâng thành `int` trước phép dịch |
| Lấy bit thứ 40 | Dùng `1L << 40`, không phải `1 << 40` |
| Kiểm tra input số lượng dịch | Kiểm tra miền cụ thể trước khi thực hiện phép dịch |
| Dịch phải số âm | Phân biệt `>>` giữ dấu và `>>>` điền 0 |

**Bài 1.** Dự đoán `1 << 32`, `1 << 33`, `1L << 32`, `1L << 64`. Giải thích vì sao kết quả khác nhau theo kiểu toán hạng bên trái.

**Bài 2.** So sánh `long x = 1 << 40;` và `long y = 1L << 40;`. Vì sao chỉ một cách thực sự dịch 40 bit của `long`?

**Bài 3.** Cho `byte b = 1;`. Tính `b << 8`, nêu kiểu kết quả, rồi giải thích `(byte)(b << 8)`.

**Bài 4.** Với `int n = -8;`, tính `n >> 33` và `n >>> 33`. Hai phép dùng khoảng dịch hiệu lực nào?

**Bài 5.** Nếu hệ thống yêu cầu số bit dịch từ 0 đến 31, vì sao không nên chỉ dùng `value << userCount` mà không validate `userCount`?

### Ghi nhớ

**Java chỉ dùng 5 bit thấp của số lượng dịch cho `int` và 6 bit thấp cho `long`.** `byte`, `short`, `char` nâng thành `int` trước phép dịch. Quy tắc áp dụng cho cả `<<`, `>>`, `>>>`, kể cả số lượng dịch âm hoặc vượt độ rộng; chúng không tự báo lỗi. Nếu cần 64 bit, hãy làm cho **toán hạng trái** là `long` trước phép dịch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bitwise-operators-006">Numeric promotion khi thao tác trên byte, short hoặc char</a>

<details>
<summary>Xem chi tiết</summary>

# Numeric promotion khi thao tác trên `byte`, `short` hoặc `char` — Java 17+

Trong Java, nhiều toán tử số học và bit **không tính trực tiếp trên độ rộng 8 bit hoặc 16 bit**, dù toán hạng là `byte`, `short` hay `char`. Trước khi tính, Java áp dụng **numeric promotion** (*nâng kiểu số*) và thường biến các toán hạng này thành **`int` 32 bit**.

~~~java
byte b = 5;
int shifted = b << 1; // 10, kiểu int.
int inverted = ~b;    // -6, kiểu int.
~~~

**Điểm cốt lõi:** Phân biệt ba bước:

1. **Nâng kiểu toán hạng** từ `byte`/`short`/`char` lên `int`.
2. **Tính toán** trên kiểu đã nâng, ví dụ AND bit, XOR bit hoặc dịch bit.
3. **Chuyển kiểu khi gán kết quả** (nếu biến đích là kiểu hẹp): có thể cần cast, hoặc `op=` sẽ thu hẹp ngầm.

Không suy luận rằng kết quả `b << 1` hay `~b` có kiểu `byte` chỉ vì biến `b` là `byte`.

## 1. Bảng tổng quan

| Kiểu nguồn | Độ rộng | Miền giá trị | Khi chuyển sang `int` |
| --- | ---: | --- | --- |
| `byte` | 8 bit | −128 đến 127 | **Mở rộng dấu** (*sign extension*) |
| `short` | 16 bit | −32.768 đến 32.767 | **Mở rộng dấu** |
| `char` | 16 bit | 0 đến 65.535 | **Mở rộng với bit 0** (*zero extension*) |

`char` là **đơn vị mã UTF-16 không dấu**, không phải kiểu “ký tự có dấu”. Ví dụ `char c = (char) 0xFFFF;` khi chuyển sang `int` cho **65535**, trong khi `short s = -1;` cho **-1**, mặc dù cả hai đều có 16 bit 1 ở độ rộng ban đầu.

### Những toán tử liên quan

| Toán tử | Quy tắc nâng kiểu | Kiểu kết quả |
| --- | --- | --- |
| `+x`, `-x`, `~x` | **Unary numeric promotion** | `int` nếu `x` là `byte`/`short`/`char` |
| `x << n`, `x >> n`, `x >>> n` | **Unary numeric promotion riêng cho hai toán hạng** | Kiểu của toán hạng **trái sau nâng kiểu** |
| `x & y`, `x \| y`, `x ^ y` khi là phép **bit trên số nguyên** | **Binary numeric promotion** | Thường là `int`; thành `long` nếu có toán hạng `long` |
| `x + y`, `x - y`, `x * y` | **Binary numeric promotion** | Thường là `int` với các kiểu nhỏ |
| `x &= y`, `x <<= n` (gán kết hợp) | Tính theo kiểu được nâng, sau đó **chuyển về kiểu của x** | Kiểu của biến bên trái |

Cùng ký hiệu `&`, `|`, `^` còn có nghĩa logic khi cả hai toán hạng là `boolean`. Bài này xét **trường hợp số nguyên**, không phải phép toán boolean.

## 2. Demo Java 17+ tự chứa, chạy bằng CLI

Tạo file UTF-8 **`BitPromotionDemo.java`**:

~~~java
public class BitPromotionDemo {
    static String kind(int unused) {
        return "int";
    }

    static String kind(long unused) {
        return "long";
    }

    static String bits8(int value) {
        String bits = Integer.toBinaryString(value & 0xFF);
        return "0".repeat(8 - bits.length()) + bits;
    }

    static String bits16(int value) {
        String bits = Integer.toBinaryString(value & 0xFFFF);
        return "0".repeat(16 - bits.length()) + bits;
    }

    public static void main(String[] args) {
        byte b = 5;
        short s = 9;
        char c = 'A';

        System.out.println("== int promotion ==");
        System.out.println("byteShiftType=" + kind(b << 1));
        System.out.println("byteShift=" + (b << 1));
        System.out.println("shortShiftType=" + kind(s << 2));
        System.out.println("shortShift=" + (s << 2));
        System.out.println("charShiftType=" + kind(c << 1));
        System.out.println("charShift=" + (c << 1));
        System.out.println("byteAndShortType=" + kind(b & s));
        System.out.println("byteAndShort=" + (b & s));
        System.out.println("charXorByteType=" + kind(c ^ b));
        System.out.println("charXorByte=" + (c ^ b));
        System.out.println("plusByteType=" + kind(+b));
        System.out.println("minusByte=" + (-b));
        System.out.println("notByte=" + (~b));
        System.out.println("notChar=" + (~c));

        System.out.println("== sign vs zero extension ==");
        byte signedByte = -1;
        short signedShort = -2;
        char unsignedChar = (char) 0xFFFF;
        System.out.println("byteAsInt=" + (int) signedByte);
        System.out.println("byteHex="
                + Integer.toHexString(signedByte));
        System.out.println("byteBits8=" + bits8(signedByte));
        System.out.println("unsignedByte=" + (signedByte & 0xFF));
        System.out.println("shortAsInt=" + (int) signedShort);
        System.out.println("shortBits16=" + bits16(signedShort));
        System.out.println("unsignedShort="
                + Short.toUnsignedInt(signedShort));
        System.out.println("charAsInt=" + (int) unsignedChar);
        System.out.println("charRight15=" + (unsignedChar >> 15));
        System.out.println("shortRight15=" + (signedShort >> 15));
        System.out.println("charShiftLeft=" + (unsignedChar << 1));

        System.out.println("== shift semantics ==");
        System.out.println("byteLogicalRight="
                + (signedByte >>> 1));
        System.out.println("shortArithmeticRight="
                + (signedShort >> 1));
        long distance = 33L;
        System.out.println("byteLongDistanceType="
                + kind(b << distance));
        System.out.println("byteLongDistance="
                + (b << distance));
        System.out.println("intShiftToSignBit="
                + (b << 31));

        System.out.println("== compound assignments ==");
        byte narrow = 100;
        int promoted = narrow << 1;
        narrow <<= 1;
        System.out.println("promotedValue=" + promoted);
        System.out.println("compoundByteValue=" + narrow);
        System.out.println("compoundByteBits=" + bits8(narrow));

        byte masked = -1;
        masked &= 0x0F;
        System.out.println("compoundAndByte=" + masked);

        char movedChar = 'A';
        movedChar <<= 1;
        System.out.println("compoundCharCode=" + (int) movedChar);

        short medium = 30_000;
        int promotedShort = medium << 1;
        medium <<= 1;
        System.out.println("promotedShort=" + promotedShort);
        System.out.println("compoundShort=" + medium);

        System.out.println("== long promotion and wrappers ==");
        System.out.println("byteAndLongType="
                + kind(signedByte & 0xFFL));
        System.out.println("byteAndLongValue="
                + (signedByte & 0xFFL));

        Byte boxed = null;
        try {
            int unused = boxed & 0xFF;
            System.out.println("unexpected=" + unused);
        } catch (NullPointerException ex) {
            System.out.println("boxedNull=NullPointerException");
        }
    }
}
~~~

Dùng **JDK 17+**, mở PowerShell hoặc Bash trong thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 BitPromotionDemo.java
java BitPromotionDemo
~~~

**Expected stdout chính xác** của riêng lệnh `java BitPromotionDemo`:

~~~text
== int promotion ==
byteShiftType=int
byteShift=10
shortShiftType=int
shortShift=36
charShiftType=int
charShift=130
byteAndShortType=int
byteAndShort=1
charXorByteType=int
charXorByte=68
plusByteType=int
minusByte=-5
notByte=-6
notChar=-66
== sign vs zero extension ==
byteAsInt=-1
byteHex=ffffffff
byteBits8=11111111
unsignedByte=255
shortAsInt=-2
shortBits16=1111111111111110
unsignedShort=65534
charAsInt=65535
charRight15=1
shortRight15=-1
charShiftLeft=131070
== shift semantics ==
byteLogicalRight=2147483647
shortArithmeticRight=-1
byteLongDistanceType=int
byteLongDistance=10
intShiftToSignBit=-2147483648
== compound assignments ==
promotedValue=200
compoundByteValue=-56
compoundByteBits=11001000
compoundAndByte=15
compoundCharCode=130
promotedShort=60000
compoundShort=-5536
== long promotion and wrappers ==
byteAndLongType=long
byteAndLongValue=255
boxedNull=NullPointerException
~~~

Đầu ra của `java -version`, `javac -version` phụ thuộc môi trường và không thuộc khối stdout cố định. Chương trình sử dụng API Java chuẩn, không yêu cầu thư viện bên ngoài.

## 3. Unary numeric promotion trong các phép dịch và phép `~`

Với các toán tử dịch `<<`, `>>`, `>>>`, Java nâng **từng toán hạng** theo quy tắc *unary numeric promotion*. Một `byte`, `short` hoặc `char` ở bên trái trở thành `int`, nên kiểu kết quả là **`int`**.

~~~java
byte b = 5;
int result = b << 2; // 20, int.
// byte wrong = b << 2; // Lỗi: không thể gán int vào byte.
~~~

**Khoảng dịch được xác định theo kiểu toán hạng trái đã nâng:**

- Với `int`: chỉ dùng **5 bit thấp** của khoảng dịch (`n & 31`).
- Với `long`: chỉ dùng **6 bit thấp** (`n & 63`).

~~~java
byte b = 5;
long count = 33L;
int shifted = b << count; // 10, vì kiểu trái là int và 33 & 31 = 1.
~~~

`count` là `long` **không làm kết quả thành `long`**. Đây là khác biệt quan trọng so với phép cộng hai ngôi, nơi hai toán hạng có thể cùng được nâng lên kiểu rộng hơn.

**Phép `~` cũng thực hiện unary numeric promotion**:

~~~java
byte b = 5;
int inverted = ~b; // -6 (đảo 32 bit của int 5).
~~~

Phép `~b` không chỉ đảo 8 bit ban đầu. Nếu `b == 5`, sau nâng kiểu `int` ta có 32-bit `...00000101`, đảo toàn bộ 32 bit thành mẫu bù hai của **-6**.

## 4. Binary numeric promotion với `&`, `|`, `^` kiểu số

Với toán hạng số nguyên, `&`, `|`, `^` áp dụng **binary numeric promotion** trước khi tính.

~~~java
byte a = 5;      // 00000101
short b = 9;     // 0000000000001001

int andResult = a & b; // 1.
int orResult = a | b;  // 13.
int xorResult = a ^ b; // 12.
~~~

Kết quả có kiểu **`int`** vì cả hai toán hạng hẹp đều được nâng lên `int`.

| Toán hạng | Kiểu kết quả | Ví dụ |
| --- | --- | --- |
| `byte & short` | `int` | `5 & 9` = 1 |
| `char ^ byte` | `int` | `65 ^ 5` = 68 |
| `short \| int` | `int` | Nâng `short` thành `int` |
| `byte & long` | `long` | Nâng `byte` thành `long` |
| `char & long` | `long` | Nâng `char` thành `long` |

Khi có `long`, phép toán bit chạy trên **64 bit**. Ví dụ `byte b = -1; long x = b & 0xFFL;` cho 255 vì `b` được mở rộng dấu thành `long -1`, rồi AND với mask `255L`.

### Đừng nhầm toán tử bit và toán tử logic

`&`, `|` và `^` cũng áp dụng trên hai toán hạng `boolean`. Khi là số nguyên, chúng xử lý **từng bit**; khi là boolean, kết quả là boolean. `&&` và `||` chỉ dùng cho boolean và có tính **ngắn mạch**, không có tác dụng trên `int`.

## 5. Mở rộng dấu và mở rộng với số 0

### `byte` và `short` âm: sign extension

~~~java
byte b = -1;
int x = b; // -1.
~~~

Minh họa mẫu bit:

~~~text
byte -1:    11111111
int  -1:    11111111111111111111111111111111
~~~

Java mở rộng bằng **bit 1** vì nguồn có dấu âm. Do đó `Integer.toHexString(b)` in `ffffffff` chứ không in `ff`.

Nếu muốn chỉ xem 8 bit thấp không dấu, dùng mask:

~~~java
int unsigned = b & 0xFF; // 255.
int same = Byte.toUnsignedInt(b); // 255.
~~~

Với `short`, dùng `s & 0xFFFF` hoặc `Short.toUnsignedInt(s)` để đọc 16 bit thấp dưới dạng không dấu.

### `char`: zero extension

~~~java
char c = (char) 0xFFFF;
int x = c; // 65535.
~~~

Mẫu bit:

~~~text
char:        1111111111111111
int:         00000000000000001111111111111111
~~~

`char` không phải số nguyên âm, dù bit cao nhất của mẫu 16 bit là 1. Vì thế:

~~~java
char c = (char) 0xFFFF;
short s = -2;

System.out.println(c >> 15); // 1.
System.out.println(s >> 15); // -1.
~~~

`c` nâng lên `int` không âm nên `>>` chèn bit 0 ở đầu. `s` nâng lên `int -2` nên `>>` lấp bit 1 ở đầu để giữ dấu.

### Vì sao `byte >>> 1` đôi khi có giá trị rất lớn?

~~~java
byte b = -1;
int x = b >>> 1; // 2147483647.
~~~

`b` được mở rộng dấu thành **`int -1`**, gồm 32 bit 1. `>>> 1` điền bit 0 phía trái và dịch còn 31 bit 1, tạo **2147483647**; không phải 127.

Nếu muốn xem dịch logic trên **8 bit nguồn**:

~~~java
int unsignedShift = (b & 0xFF) >>> 1; // 127.
~~~

Ở đây mask `0xFF` **xóa các bit mở rộng dấu trước khi dịch**. Đặt mask sau phép dịch có thể cho kết quả khác trong một số biểu thức; hãy chọn thứ tự theo đúng độ rộng dữ liệu mong muốn.

## 6. Gán kết hợp có thể thu hẹp kết quả về kiểu gốc

Phép toán thường có thể cho `int` trong khi phép gán kết hợp `op=` cho phép **thu hẹp về kiểu biến trái**:

~~~java
byte b = 100;
int result = b << 1; // 200.
b <<= 1;             // hợp lệ, b thành -56.

// b = b << 1;       // Lỗi biên dịch nếu không cast.
b = (byte) (b << 1); // Cast tường minh nếu muốn viết dạng mở rộng.
~~~

Vì miền `byte` là -128..127, `int 200` khi thu về 8 bit thấp thành `11001000`, đọc có dấu là **-56**.

Điều tương tự xảy ra với `short` và `char`:

~~~java
short s = 30_000;
int promoted = s << 1; // 60000.
s <<= 1;               // short -5536.

char c = 'A';
c <<= 1; // char có mã đơn vị 130.
~~~

Với phép toán bit, `byte b = -1; b &= 0x0F;` cũng hợp lệ và trả **15**, do kết quả được gán trở về kiểu `byte`.

**Đừng coi việc biên dịch được là bằng chứng không mất dữ liệu.** Toán tử compound assignment như `<<=`, `&=`, `|=`, `^=` có chuyển kiểu ngầm về biến gốc. Nếu cần giữ kết quả đầy đủ, lưu vào `int` hoặc `long` trước khi thu hẹp.

## 7. Wrapper, `null` và kiểu kết quả

Nếu số được đóng gói trong `Byte`, `Short` hoặc `Character`, toán tử số sẽ **unbox** trước khi numeric promotion.

~~~java
Byte boxed = 5;
int shifted = boxed << 1; // 10.
~~~

Wrapper null không chứa giá trị số để unbox:

~~~java
Byte missing = null;
// int value = missing & 0xFF; // Ném NullPointerException.
~~~

Một biến `Byte` không biến `&` thành phép toán trên đối tượng tham chiếu. Nó vẫn là phép toán số nguyên khi được unbox bình thường.

`var` cũng theo **kiểu của biểu thức khởi tạo**:

~~~java
byte b = 5;
var x = b << 1; // x có kiểu int.
var y = ~b;     // y có kiểu int.
~~~

Không thể suy luận kiểu `byte` cho `x` chỉ vì đầu vào là `byte`.

## 8. Bẫy thường gặp

| Nhầm lẫn | Quy tắc đúng |
| --- | --- |
| “`byte << 1` trả kiểu `byte`.” | Toán hạng trái nâng thành `int`; kết quả `int` |
| “`~byte` chỉ đảo 8 bit.” | Nâng lên `int` trước; `~` đảo **32 bit** |
| “`short & char` trả `short`.” | Hai toán hạng nâng lên `int`; kết quả `int` |
| “Khoảng dịch `long` làm kết quả dịch thành `long`.” | Kiểu kết quả phụ thuộc **toán hạng trái** |
| “`char` có bit cao là 1 nghĩa là số âm.” | `char` không dấu, mở rộng với bit 0 |
| “`byte -1 >>> 1` trả 127.” | Trả **2147483647** do mở rộng dấu thành `int` |
| “`byteValue <<= 1` luôn giữ giá trị toán học chính xác.” | Có thể thu hẹp, mất bit và đổi dấu |
| “`Byte null` tự được coi như số 0.” | Unboxing null ném `NullPointerException` |
| “`var x = b << 1` suy ra kiểu của `b`.” | Suy ra kiểu của **biểu thức**, thường là `int` |

## 9. Bài tập tự kiểm tra

1. `byte b = 7; var x = b << 1;` — kiểu và giá trị của `x`?  
   *Đáp án:* `int`, giá trị **14**.
2. `byte b = 5; int n = ~b;` — `n` bằng bao nhiêu?  
   *Đáp án:* **-6**, vì đảo bit của `int 5`.
3. `byte a = 5; short b = 9; var n = a & b;` — kiểu và giá trị?  
   *Đáp án:* `int`, giá trị **1**.
4. `char c = (char) 0xFFFF; int x = c;` — `x` là bao nhiêu?  
   *Đáp án:* **65535**, vì `char` zero-extend.
5. `short s = -2; int x = s >> 15;` — `x` bằng bao nhiêu?  
   *Đáp án:* **-1**, do sign extension.
6. `byte b = -1; int x = b >>> 1;` — `x` bằng bao nhiêu?  
   *Đáp án:* **2147483647**.
7. `byte b = 100; b <<= 1;` — `b` bằng bao nhiêu?  
   *Đáp án:* **-56**, do thu hẹp `int 200` về `byte`.
8. `char c = 'A'; c <<= 1;` — mã số của `c` là gì?  
   *Đáp án:* **130**.
9. `byte b = 5; long n = 33L; var x = b << n;` — kiểu và giá trị `x`?  
   *Đáp án:* `int` và **10**; khoảng dịch `33 & 31 = 1`.

## Checklist

- [ ] Nhớ `byte`/`short`/`char` thường nâng thành `int` trước khi thao tác bit.
- [ ] Phân biệt **unary numeric promotion** của `~` và dịch bit với **binary numeric promotion** của `&`/`|`/`^`.
- [ ] Biết `byte`/`short` **mở rộng dấu**, `char` **mở rộng với 0**.
- [ ] Hiểu kiểu kết quả dịch phụ thuộc kiểu **toán hạng trái**, không do kiểu khoảng dịch.
- [ ] Nhận ra `byte >>> 1` thực sự có thể chạy trên `int` 32 bit sau sign extension.
- [ ] Đánh giá mất dữ liệu khi dùng `<<=`, `&=` và các gán kết hợp về kiểu hẹp.
- [ ] Kiểm tra `null` trước khi unbox wrapper.
- [ ] Dùng mask `0xFF`/`0xFFFF` hoặc API unsigned khi cần xem dữ liệu không dấu.

**Kết luận:** Trong các phép bit Java, `byte`, `short` và `char` thường là **nguồn dữ liệu 8/16 bit**, nhưng **phép toán thực tế thực hiện trên `int` 32 bit**. Theo dõi **promotion**, **sign/zero extension** và **narrowing khi gán lại** sẽ giúp tránh các kết quả tưởng như bất ngờ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bitwise-operators-007">Mặt nạ bit để đọc, bật, tắt và đảo cờ</a>

<details>
<summary>Xem chi tiết</summary>

# Mặt nạ bit để đọc, bật, tắt và đảo cờ — Java 17+

## 1. Mặt nạ bit là gì?

**Mặt nạ bit** (*bit mask*) là một số nguyên có các bit 0/1 được dùng để chọn hoặc thao tác một số vị trí bit trong một số nguyên khác. Mỗi bit có thể biểu diễn một **cờ** (*flag*) độc lập: một tính năng đang bật, một trạng thái, hoặc một thuộc tính.

Ví dụ lưu bốn cờ trong một biến `int`:

| Cờ | Bit số | Giá trị thập phân | Nhị phân 4 bit |
|---|---:|---:|---|
| `READ` | 0 | 1 | `0001` |
| `WRITE` | 1 | 2 | `0010` |
| `EXECUTE` | 2 | 4 | `0100` |
| `ADMIN` | 3 | 8 | `1000` |

~~~java
static final int READ    = 1 << 0;
static final int WRITE   = 1 << 1;
static final int EXECUTE = 1 << 2;
static final int ADMIN   = 1 << 3;

int flags = READ | WRITE; // 0001 | 0010 = 0011 (3)
~~~

Mỗi cờ có **một bit 1 riêng**, nên có thể ghép bằng `|` mà không làm mất thông tin về các cờ khác. `flags` lúc này là 3, nghĩa là READ và WRITE cùng bật.

**Bốn thao tác cốt lõi:**

- **Đọc/kiểm tra:** `flags & mask`.
- **Bật:** `flags | mask`.
- **Tắt:** `flags & ~mask`.
- **Đảo:** `flags ^ mask`.

Các phép toán dùng trên số nguyên `byte`, `short`, `char`, `int`, `long` theo quy tắc nâng kiểu của Java; ví dụ chính của bài dùng `int`.

## 2. Từ phép toán từng bit đến thao tác cờ

Bốn công thức dựa trực tiếp vào cách mỗi toán tử xử lý bit tại cùng vị trí: AND (`&`) chỉ giữ 1 khi cả hai bit là 1; OR (`|`) bật 1 nếu một bit là 1; XOR (`^`) đảo bit khi mask bằng 1; NOT (`~`) đảo mọi bit của toán hạng đã nâng kiểu.

~~~text
  flags      0101
  mask       0011
  &          0001   (lọc các bit chung)
  |          0111   (bật bit đã chọn)
  ^          0110   (đảo bit đã chọn)
~~~

Bảng chân trị đầy đủ thuộc bài về AND/OR/XOR/NOT. Ở đây điều quan trọng là **tác động lên trạng thái cờ**: dùng `&` để kiểm tra, `|` để bật, `& ~mask` để tắt, và `^` để đảo. `~` trên `int` đảo 32 bit, vì vậy thường được kết hợp với `&` hoặc một mask giới hạn vùng cần thao tác.

## 3. Đọc một cờ: AND với mask

Muốn biết cờ READ có bật:

~~~java
boolean canRead = (flags & READ) != 0;
~~~

Nếu `flags = 0b0101` (READ và EXECUTE), ta có:

~~~text
flags = 0101
READ  = 0001
&     = 0001  -> khác 0 -> READ bật
~~~

Với WRITE:

~~~text
flags = 0101
WRITE = 0010
&     = 0000  -> bằng 0 -> WRITE tắt
~~~

**Dấu ngoặc rất quan trọng để rõ ý:** viết `(flags & READ) != 0`, không viết mơ hồ `flags & READ != 0`. Trong Java, `!=` có ưu tiên cao hơn `&`; dạng không ngoặc có thể trở thành phép bitwise AND giữa `int` và `boolean` và bị lỗi biên dịch.

Nếu mask là **một bit**, hai cách sau tương đương để kiểm tra nó:

~~~java
(flags & READ) != 0
(flags & READ) == READ
~~~

Nhưng với **mask nhiều bit**, ý nghĩa của hai cách khác nhau, xem phần sau.

## 4. Đọc nhiều cờ: phân biệt “bất kỳ” và “tất cả”

Giả sử:

~~~java
int flags = READ | WRITE;       // 0011
int required = READ | EXECUTE;  // 0101
~~~

**Có ít nhất một cờ trong mask:** (*any*):

~~~java
boolean any = (flags & required) != 0; // true: READ đang bật
~~~

**Có đầy đủ tất cả cờ trong mask:** (*all*):

~~~java
boolean all = (flags & required) == required; // false: EXECUTE chưa bật
~~~

| Kiểm tra | Công thức | Khi nào true? |
|---|---|---|
| **Một cờ** | `(flags & FLAG) != 0` | Bit của FLAG bật |
| **Bất kỳ cờ nào** | `(flags & mask) != 0` | Ít nhất một bit thuộc mask bật |
| **Tất cả cờ** | `(flags & mask) == mask` | Mọi bit thuộc mask đều bật |
| **Không có cờ nào** | `(flags & mask) == 0` | Không bit nào thuộc mask bật |

**Bẫy với mask bằng 0:** `(flags & 0) == 0` **luôn true**, kể cả `flags == 0`. Vì vậy `hasAll(flags, 0)` có thể trả true theo định nghĩa toán học (không có cờ bắt buộc). Nếu API nghiệp vụ không cho phép mask rỗng, **phải validate `mask != 0`**, đừng coi kết quả true là một trạng thái quyền hợp lệ.

## 5. Bật cờ bằng OR

Bật những bit được chọn nhưng giữ nguyên các bit khác:

~~~java
flags = flags | READ;
flags |= READ; // Viết gọn, cùng mục đích với biến int đơn giản
~~~

Ví dụ:

~~~text
flags  = 0100   (EXECUTE)
mask   = 0011   (READ | WRITE)
OR     = 0111   (cả ba bật)
~~~

~~~java
int flags = EXECUTE;
flags |= READ | WRITE;
System.out.println(flags); // 7
~~~

**Tính chất idempotent:** bật một cờ đã bật **không làm thay đổi giá trị**:

~~~java
flags |= READ;
flags |= READ; // vẫn giữ READ bật, không chuyển nó thành tắt
~~~

Nếu cần **đảo** trạng thái mỗi lần gọi thì dùng XOR `^`, không dùng OR.

## 6. Tắt cờ bằng AND với NOT

Muốn tắt các bit thuộc mask mà **giữ nguyên mọi bit khác**:

~~~java
flags &= ~WRITE;
~~~

Ví dụ:

~~~text
flags        = 0111   (READ | WRITE | EXECUTE)
WRITE        = 0010
~WRITE (4bit)= 1101
AND          = 0101   (READ | EXECUTE)
~~~

Trong thực tế Java, `~WRITE` là toán tử 32 bit với `int`, không chỉ đảo 4 bit. Nhưng thao tác `flags & ~WRITE` vẫn đúng vì bit WRITE bị 0 hóa, còn những vị trí bit khác trong mask `~WRITE` là 1, nên được giữ nguyên.

~~~java
int flags = READ | WRITE | EXECUTE;
flags &= ~WRITE;
System.out.println(flags); // 5
~~~

Tắt nhiều cờ cùng lúc:

~~~java
flags &= ~(READ | EXECUTE);
~~~

**Tính chất idempotent:** tắt cờ đã tắt sẽ không làm thay đổi giá trị.

**Không dùng `flags &= WRITE` khi muốn tắt WRITE:** thao tác đó **chỉ giữ lại** bit WRITE và xóa các bit khác. Muốn xóa WRITE phải dùng `~WRITE`.

## 7. Đảo cờ bằng XOR

XOR làm bit thuộc mask **đổi trạng thái**:

- 0 thành 1.
- 1 thành 0.

~~~java
int flags = READ; // 0001
flags ^= EXECUTE; // 0101: bật EXECUTE
flags ^= EXECUTE; // 0001: tắt EXECUTE
~~~

Bảng ý nghĩa:

| Trạng thái ban đầu của một bit | OR với 1 | AND với 0 | XOR với 1 |
|---|---|---|---|
| 0 (tắt) | Bật (1) | Tắt (0) | Bật (1) |
| 1 (bật) | Giữ bật (1) | Tắt (0) | Tắt (0) |

XOR **không idempotent** đối với bit trong mask: gọi hai lần sẽ quay về trạng thái đầu.

~~~java
int state = 0;
state ^= READ;
state ^= READ;
System.out.println(state); // 0
~~~

**Không dùng XOR để “bật chắc chắn” một cờ**, vì nếu cờ đã bật thì XOR sẽ tắt nó.

## 8. Quy trình quản lý cờ minh họa

~~~mermaid
flowchart TD
    A["Bắt đầu: flags = 0000"] --> B["flags |= READ | WRITE"]
    B --> C["0011: READ và WRITE bật"]
    C --> D["flags &= ~WRITE"]
    D --> E["0001: chỉ READ bật"]
    E --> F["flags ^= EXECUTE"]
    F --> G["0101: READ và EXECUTE bật"]
    G --> H["flags ^= EXECUTE"]
    H --> I["0001: EXECUTE lại tắt"]
~~~

Đây là thao tác **bitmask trên một biến số nguyên**, không tự tạo hệ thống cấp quyền thực tế. Nếu flags biểu diễn quyền truy cập thật, việc xác thực, phân quyền, dữ liệu đầu vào đáng tin cậy và đồng bộ trạng thái vẫn cần được thiết kế riêng.

## 9. Chọn vị trí cờ bằng phép dịch `1 << position`

Thay vì viết các số magic:

~~~java
static final int READ    = 1 << 0;
static final int WRITE   = 1 << 1;
static final int EXECUTE = 1 << 2;
static final int ADMIN   = 1 << 3;
~~~

Với `int`, vị trí bit hợp lệ là **0 đến 31**; với `long`, vị trí là **0 đến 63**:

~~~java
int highestIntBit = 1 << 31;
long highLongBit = 1L << 40;
~~~

**Hai cảnh báo:**

1. `1 << 31` bằng `Integer.MIN_VALUE`, tức một số **âm** do bit dấu được bật. Nó vẫn là một mask bit hợp lệ. Kiểm tra `(flags & highestIntBit) != 0`, **không dùng `> 0`**, vì AND có thể trả giá trị âm.
2. Khoảng dịch của int lấy **5 bit thấp**, của long lấy **6 bit thấp**. `1 << 32` thực chất là `1 << 0` → kết quả 1, dễ khiến **trùng cờ**. `1L << 32` mới tạo bit thứ 32 trên long.

Nếu `position` đến từ dữ liệu động, cần tự kiểm tra `0 <= position && position < 32` (hoặc `< 64` cho long); Java không tự ném lỗi nếu dịch quá phạm vi.

## 10. `~` đảo toàn bộ chiều rộng: nhớ giới hạn vùng bit cần dùng

~~~java
int flag = READ; // 0001
System.out.println(~flag); // -2
~~~

`~1` của int không phải `1110` đơn thuần; nó đảo **32 bit**, trở thành `0xFFFFFFFE` hay -2.

Nếu chỉ quan tâm **4 bit thấp**, mask lại:

~~~java
int FOUR_BITS = 0b1111;
int invertedWithin4Bits = (~READ) & FOUR_BITS;
System.out.println(invertedWithin4Bits); // 14 = 1110
~~~

**Đừng nhầm:** `~mask` đơn lẻ tạo giá trị có các bit cao bật; đó là bình thường trong `flags & ~mask` khi mục tiêu là **giữ nguyên tất cả bit khác**. Chỉ giới hạn lại bằng `& allowedBits` khi muốn một kết quả thực sự bị ràng buộc trong một vùng bit xác định.

## 11. `byte`, bit dấu và cách đọc 8 bit không dấu

Các phép bitwise với `byte`, `short` và `char` thường **nâng toán hạng lên `int`** trước khi tính. Một `byte` âm được sign-extend khi chuyển sang int.

~~~java
byte encoded = (byte) 0b1111_0000;
System.out.println(encoded);         // -16
System.out.println(encoded & 0xFF);  // 240
~~~

- Dưới dạng `byte` có dấu, bit `11110000` là -16.
- `encoded & 0xFF` chỉ giữ 8 bit thấp, trả `int` có giá trị 240 (diễn giải byte theo kiểu unsigned).

Với `byte`, phép gán kết hợp như `byteVar |= mask` có thể **thu hẹp ngầm** từ kết quả int về byte. Nếu cần xử lý flags 8 bit nhưng muốn biểu diễn các giá trị 128–255 không âm, dùng `int` sau khi mask `& 0xFF`, hoặc một mô hình dữ liệu khác có kiểm soát miền.

## 12. Viết helper rõ ý và kiểm tra mask

Một nhóm hàm đơn giản giúp tránh lặp công thức và nhầm **any** với **all**:

~~~java
static boolean hasAny(int flags, int mask) {
    return (flags & mask) != 0;
}
static boolean hasAll(int flags, int mask) {
    return (flags & mask) == mask;
}
static int enable(int flags, int mask) {
    return flags | mask;
}
static int disable(int flags, int mask) {
    return flags & ~mask;
}
static int toggle(int flags, int mask) {
    return flags ^ mask;
}
~~~

Các helper trên **không thay đổi biến đầu vào** (Java truyền `int` theo giá trị); chúng trả trạng thái mới khi bật/tắt/đảo. Người gọi phải gán kết quả trở lại nếu muốn cập nhật:

~~~java
flags = enable(flags, READ);
~~~

Đối với API có tập cờ hợp lệ `allowed`, nên **từ chối mask bằng 0 hoặc chứa bit lạ**:

~~~java
static void requireValidMask(int mask, int allowed) {
    if (mask == 0 || (mask & ~allowed) != 0) {
        throw new IllegalArgumentException("invalid flag mask");
    }
}
~~~

- `mask == 0`: ngăn trường hợp kiểm tra “all” trả true một cách rỗng.
- `(mask & ~allowed) != 0`: phát hiện **ít nhất một bit ngoài tập cho phép**.
- Hàm không tự chứng minh người dùng có quyền bật cờ; nó chỉ **validate hình dạng mask**.

## 13. Lỗi thường gặp

| Nhầm lẫn | Hậu quả | Cách đúng |
|---|---|---|
| Kiểm tra nhiều cờ bằng `(flags & mask) != 0` rồi hiểu là “đủ hết” | Chỉ cần một bit bật đã true | Dùng `(flags & mask) == mask` |
| `hasAll(flags, 0)` có nghĩa đã được cấp quyền | Kết quả true rỗng | Cấm mask 0 nếu không hợp lệ |
| Tắt cờ bằng `flags &= mask` | Xóa các bit khác, giữ bit mask | `flags &= ~mask` |
| Bật cờ bằng `flags ^= mask` | Có thể tắt cờ đang bật | `flags |= mask` |
| Dùng `flags & mask > 0` | Nhầm ưu tiên toán tử và bit dấu | `(flags & mask) != 0` |
| `~READ` chỉ đảo 4 bit | `~` đảo toàn bộ 32 bit của int | `(~READ) & 0b1111` khi cần 4 bit |
| `1 << 31` không hợp lệ vì âm | Bit dấu có thể dùng làm cờ | Kiểm tra `!= 0`, không `> 0` |
| `1 << 32` tạo bit thứ 32 | Shift của int mask 5 bit; thành bit 0 | Dùng `1L << 32` hoặc validate vị trí |
| `byte` lưu `0xF0` là 240 | byte có dấu trả -16 | `byteValue & 0xFF` để đọc unsigned |
| Dùng `&&` giữa hai giá trị int | Không biên dịch: `&&` cần boolean | Dùng `&` bitwise, sau đó so 0 |
| Nghĩ `flags |= mask` tự thread-safe | Read–modify–write không tự nguyên tử | Đồng bộ/`AtomicInteger` nếu chia sẻ đa luồng |
| Xem bitmask là hệ thống phân quyền đủ an toàn | Bitmask chỉ mô tả trạng thái | Kiểm tra quyền phía đáng tin cậy |

## 14. Chương trình Java 17 hoàn chỉnh

Lưu file đúng tên **`BitMaskFlagsDemo.java`**:

~~~java
public final class BitMaskFlagsDemo {
    private BitMaskFlagsDemo() {}

    static final int READ = 1 << 0;
    static final int WRITE = 1 << 1;
    static final int EXECUTE = 1 << 2;
    static final int ADMIN = 1 << 3;
    static final int ALL_ALLOWED = READ | WRITE | EXECUTE | ADMIN;

    static boolean hasAny(int flags, int mask) {
        return (flags & mask) != 0;
    }

    static boolean hasAll(int flags, int mask) {
        return (flags & mask) == mask;
    }

    static int enable(int flags, int mask) {
        return flags | mask;
    }

    static int disable(int flags, int mask) {
        return flags & ~mask;
    }

    static int toggle(int flags, int mask) {
        return flags ^ mask;
    }

    static void requireValidMask(int mask, int allowed) {
        if (mask == 0 || (mask & ~allowed) != 0) {
            throw new IllegalArgumentException("invalid flag mask");
        }
    }

    public static void main(String[] args) {
        int flags = 0;
        System.out.println("initial=" + flags);

        flags = enable(flags, READ | WRITE);
        System.out.println("enabled READ|WRITE=" + flags);
        System.out.println("has READ=" + hasAny(flags, READ));
        System.out.println("has EXECUTE=" + hasAny(flags, EXECUTE));
        System.out.println("has both READ|WRITE="
                + hasAll(flags, READ | WRITE));
        System.out.println("has both READ|EXECUTE="
                + hasAll(flags, READ | EXECUTE));
        System.out.println("has any WRITE|EXECUTE="
                + hasAny(flags, WRITE | EXECUTE));

        flags = disable(flags, WRITE);
        System.out.println("disabled WRITE=" + flags);

        flags = toggle(flags, EXECUTE);
        System.out.println("toggle EXECUTE on=" + flags);
        flags = toggle(flags, EXECUTE);
        System.out.println("toggle EXECUTE off=" + flags);

        flags = enable(flags, ADMIN);
        System.out.println("enabled ADMIN=" + flags);
        flags = disable(flags, READ | ADMIN);
        System.out.println("cleared READ|ADMIN=" + flags);

        System.out.println("all with zero mask=" + hasAll(flags, 0));
        System.out.println("any with zero mask=" + hasAny(flags, 0));

        flags = enable(flags, READ | EXECUTE);
        System.out.println("current binary="
                + Integer.toBinaryString(flags));
        System.out.println("allowed binary="
                + Integer.toBinaryString(ALL_ALLOWED));
        System.out.println("~READ raw=" + (~READ));
        System.out.println("~READ within four bits="
                + ((~READ) & ALL_ALLOWED));

        byte encoded = (byte) 0b1111_0000;
        System.out.println("byte signed=" + encoded);
        System.out.println("byte unsigned=" + (encoded & 0xFF));

        int highBit = 1 << 31;
        int withHigh = highBit;
        System.out.println("high bit flag=" + withHigh);
        System.out.println("high bit detected="
                + hasAny(withHigh, highBit));
        System.out.println("high bit unsigned="
                + Integer.toUnsignedLong(withHigh));

        System.out.println("1 << 32=" + (1 << 32));
        System.out.println("1L << 32=" + (1L << 32));

        requireValidMask(READ | WRITE, ALL_ALLOWED);
        System.out.println("valid mask=accepted");
        try {
            requireValidMask(0, ALL_ALLOWED);
        } catch (IllegalArgumentException ex) {
            System.out.println("zero mask=" + ex.getMessage());
        }
        try {
            requireValidMask(1 << 4, ALL_ALLOWED);
        } catch (IllegalArgumentException ex) {
            System.out.println("unknown mask=" + ex.getMessage());
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; mở terminal tại thư mục chứa `BitMaskFlagsDemo.java`. Chương trình không dùng thư viện ngoài JDK, không cần Maven/Gradle.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 BitMaskFlagsDemo.java
java -cp . BitMaskFlagsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\BitMaskFlagsDemo.java
java -cp . BitMaskFlagsDemo
~~~

### Exact expected stdout

~~~text
initial=0
enabled READ|WRITE=3
has READ=true
has EXECUTE=false
has both READ|WRITE=true
has both READ|EXECUTE=false
has any WRITE|EXECUTE=true
disabled WRITE=1
toggle EXECUTE on=5
toggle EXECUTE off=1
enabled ADMIN=9
cleared READ|ADMIN=0
all with zero mask=true
any with zero mask=false
current binary=101
allowed binary=1111
~READ raw=-2
~READ within four bits=14
byte signed=-16
byte unsigned=240
high bit flag=-2147483648
high bit detected=true
high bit unsigned=2147483648
1 << 32=1
1L << 32=4294967296
valid mask=accepted
zero mask=invalid flag mask
unknown mask=invalid flag mask
~~~

### Giải thích nhóm kết quả

1. **Bật:** `READ|WRITE = 3`; sau khi tắt WRITE còn 1.
2. **Đọc:** `hasAny(3, WRITE|EXECUTE)` đúng vì WRITE bật, nhưng `hasAll(3, READ|EXECUTE)` sai vì EXECUTE tắt.
3. **Đảo:** XOR với EXECUTE hai lần biến `1 → 5 → 1`.
4. **Tắt nhiều:** xóa READ và ADMIN sau khi flags là 9 đưa flags về 0.
5. **Mask rỗng:** `hasAll(0,0)` true theo phép so mask; `hasAny(0,0)` false.
6. **Biểu diễn:** `flags = 5` có nhị phân `101`; `ALL_ALLOWED = 15` là `1111`.
7. **NOT:** `~1` là -2 vì đảo 32 bit; giới hạn lại 4 bit tạo 14.
8. **Byte:** mẫu bit `11110000` có `byte` ký hiệu -16, sau `& 0xFF` là 240.
9. **Bit dấu:** mask `1 << 31` mang giá trị `int` âm; kiểm tra `!= 0` vẫn chính xác.
10. **Khoảng dịch:** `1 << 32` quay lại bit 0 với `int`, còn `1L << 32` tạo bit thứ 32 trên `long`.
11. **Validation:** mask thuộc tập cho phép được chấp nhận, mask bằng 0 hoặc có bit lạ bị từ chối.

## 15. Bài tập có đáp án

### Bài A — bật và kiểm tra hai cờ

~~~java
int flags = 0;
flags |= READ | EXECUTE;
System.out.println((flags & READ) != 0);
System.out.println((flags & WRITE) != 0);
~~~

**Đáp án:**

~~~text
true
false
~~~

### Bài B — any khác all

~~~java
int flags = READ | WRITE;
int mask = READ | EXECUTE;

System.out.println((flags & mask) != 0);
System.out.println((flags & mask) == mask);
~~~

**Đáp án:**

~~~text
true
false
~~~

### Bài C — xóa một cờ, giữ cờ còn lại

~~~java
int flags = READ | WRITE | EXECUTE;
flags &= ~WRITE;
System.out.println(flags);
~~~

**Đáp án:** `5`, gồm READ và EXECUTE.

### Bài D — đảo hai lần

~~~java
int flags = READ;
flags ^= ADMIN;
flags ^= ADMIN;
System.out.println(flags);
~~~

**Đáp án:** `1`. XOR hai lần cùng mask trả về trạng thái đầu.

### Bài E — mask cao nhất của `int`

~~~java
int highest = 1 << 31;
System.out.println(highest);
System.out.println((highest & highest) != 0);
System.out.println((highest & highest) > 0);
~~~

**Đáp án:**

~~~text
-2147483648
true
false
~~~

Cờ bit dấu vẫn bật, nên **không dùng `> 0` để kiểm tra bitmask**.

### Bài F — mask zero và vị trí bit sai

Vì sao `hasAll(flags, 0)` luôn true? Vì sao `1 << 32` không đại diện bit thứ 32 của `int`?

**Đáp án:** `flags & 0` luôn bằng 0, nên so với mask 0 cho true. Với int 32 bit, Java chỉ dùng 5 bit thấp của lượng dịch: `32 & 31 = 0`, nên `1 << 32` bằng `1 << 0`. Cần validate mask/vị trí theo miền hợp lệ.

## 16. Checklist và kết luận

- [ ] Biết tạo cờ riêng bằng `1 << position` hoặc `1L << position`.
- [ ] Biết ghép các cờ bằng OR `|`.
- [ ] Biết đọc một/ít nhất một cờ bằng `(flags & mask) != 0`.
- [ ] Biết kiểm tra đủ mọi cờ bằng `(flags & mask) == mask`.
- [ ] Biết bật bằng `flags |= mask`, tắt bằng `flags &= ~mask`, đảo bằng `flags ^= mask`.
- [ ] Hiểu OR bật và AND-NOT tắt là idempotent; XOR đảo mỗi lần gọi.
- [ ] Không nhầm `~` bitwise với `!` boolean.
- [ ] Hiểu `~` đảo mọi bit của int/long; dùng mask bổ sung khi chỉ cần vùng bit thấp.
- [ ] Biết trường hợp `hasAll(flags, 0)` true và vì sao phải validate mask rỗng.
- [ ] Nhận diện bit dấu `1 << 31` và chỉ kiểm tra `!= 0`, không `> 0`.
- [ ] Hiểu `byte` có dấu và `& 0xFF` để diễn giải 8 bit không dấu.
- [ ] Validate vị trí bit: int 0–31, long 0–63.
- [ ] Không nhầm bitmask với giải pháp bảo mật hoặc đồng bộ đa luồng đầy đủ.
- [ ] Biên dịch và chạy `BitMaskFlagsDemo.java` trên Java 17+; đối chiếu chính xác stdout.

**Kết luận:** Mặt nạ bit giúp lưu nhiều cờ độc lập trong một biến số nguyên. Hãy nhớ bốn quy tắc **đọc bằng `&`**, **bật bằng `|`**, **tắt bằng `& ~`**, **đảo bằng `^`**. Khi triển khai, phân biệt kiểm tra *any/all*, kiểm soát mask 0 và bit không hợp lệ, hiểu bit dấu/độ rộng số nguyên và giữ rõ ý định nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bitwise-operators-008">Phân biệt phép tính theo bit với điều kiện boolean</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt phép tính theo bit với điều kiện boolean trong Java

> **Java core — language-basics / Toán tử bit**  
> **Phiên bản:** Java 17+; ví dụ độc lập, không cần thư viện ngoài.  
> **Mục tiêu:** Phân biệt phép toán trên **từng bit của số nguyên** với phép toán trên **giá trị đúng/sai**; dùng đúng `&`, `|`, `^`, `~`, `!`, `&&`, `||`; hiểu short-circuit và viết điều kiện kiểm tra bit mask hợp lệ.

## 1. Hai miền dữ liệu khác nhau

Trong Java, số nguyên được biểu diễn bằng các bit 0/1; còn kiểu `boolean` chỉ có giá trị `true` hoặc `false`. Một số ký hiệu toán tử được dùng ở **cả hai miền**, nhưng ý nghĩa được xác định bởi **kiểu toán hạng**.

~~~java
int bitsA = 0b1100; // 12
int bitsB = 0b1010; // 10
int bitResult = bitsA & bitsB; // 0b1000 = 8

boolean ready = true;
boolean allowed = false;
boolean logicResult = ready & allowed; // false
~~~

`&` trong dòng đầu tạo **số nguyên** từ từng cặp bit; `&` trong dòng sau tạo **boolean** từ hai điều kiện. Java **không** tự xem `int 0` là false, hoặc số khác 0 là true.

~~~java
// KHÔNG BIÊN DỊCH:
// if (1) { }
// boolean b = 1;
// int n = true;
// int mixed = 7 & true;
~~~

## 2. Phép bit trên số nguyên trả về số nguyên

Với `byte`, `short`, `char`, `int`, `long` (và wrapper có thể unbox), `&`, `|`, `^` xử lý từng vị trí bit; `~` đảo tất cả bit sau nâng kiểu. Ví dụ với `int a = 0b1100` và `b = 0b1010`:

~~~java
int a = 0b1100;
int b = 0b1010;
int and = a & b;       // 0b1000 = 8.
int or = a | b;        // 0b1110 = 14.
int xor = a ^ b;       // 0b0110 = 6.
int not = ~a;          // -13: đảo đủ 32 bit.
int fourBits = ~a & 0b1111; // 3: chỉ giữ 4 bit thấp.
~~~

Các biểu thức này trả **`int`**, không thể dùng trực tiếp làm điều kiện `if`. `byte`/`short`/`char` thường được nâng lên `int`, và có toán hạng `long` thì phép bit hai ngôi dùng `long`. Bài về AND/OR/XOR/NOT trình bày bảng chân trị chi tiết; phần sau tập trung vào sự khác biệt khi **cùng ký hiệu** nhận toán hạng `boolean`.

## 3. Toán tử logic trên boolean

| Toán tử | Ý nghĩa trên `boolean` | Vế phải có được đánh giá? |
| --- | --- | --- |
| `!a` | NOT: đảo true/false | Một toán hạng |
| `a && b` | AND ngắn mạch | **Chỉ khi `a` true** |
| OR ngắn mạch: `a \|\| b` | OR ngắn mạch | **Chỉ khi `a` false** |
| `a & b` | AND không ngắn mạch | **Luôn** |
| OR không ngắn mạch: `a \| b` | OR không ngắn mạch | **Luôn** |
| `a ^ b` | XOR | **Luôn** |

Ví dụ:

~~~java
boolean ready = true;
boolean allowed = false;

System.out.println(ready && allowed); // false
System.out.println(ready || allowed); // true
System.out.println(ready ^ allowed);  // true
System.out.println(!ready);           // false
~~~

Với `boolean`, `&` và `&&` có cùng **bảng chân trị**; `|` và `||` cũng vậy. **Khác biệt là thứ tự có đánh giá vế phải hay không.** `^` trả true khi đúng một trong hai giá trị là true; `~` không hợp lệ với boolean, phải dùng `!` để phủ định.

## 4. Short-circuit quyết định tác dụng phụ và ngoại lệ

Java đánh giá vế trái trước. Nếu vế trái đủ quyết định kết quả, `&&`/`||` bỏ qua vế phải; `&`/`|` trên boolean thì không.

~~~java
int divisor = 0;

boolean safe = divisor != 0 && 10 / divisor > 1;
// safe=false, không thực hiện phép chia cho 0.

// boolean unsafe = divisor != 0 & 10 / divisor > 1;
// Nếu chạy dòng này: ArithmeticException.
~~~

Điều tương tự xảy ra khi kiểm tra null:

~~~java
String name = null;
boolean hasText = name != null && !name.isBlank(); // false
boolean missing = name == null || name.isBlank();  // true
~~~

**Không** chuyển `&&` thành `&` hoặc `||` thành `|` chỉ vì kết quả logic thường giống nhau. Khi vế phải có lời gọi hàm, phép tăng biến, truy cập mảng hoặc unboxing `Boolean` nullable, sự khác biệt có thể làm thay đổi kết quả chương trình.

## 5. Bit mask: biến số nguyên không tự thành điều kiện

Một bit mask thường dùng để lưu các cờ bật/tắt trong **một số nguyên**:

~~~java
final int READ  = 0b0001;
final int WRITE = 0b0010;
final int EXEC  = 0b0100;

int permissions = READ | EXEC; // 0b0101
~~~

Muốn hỏi người dùng có quyền đọc không, phép `permissions & READ` trả về **int** (1 hoặc 0), chứ không trả boolean:

~~~java
boolean canRead = (permissions & READ) != 0;   // true
boolean canWrite = (permissions & WRITE) != 0; // false
boolean hasBoth = (permissions & (READ | EXEC))
        == (READ | EXEC);                      // true
~~~

| Mục đích | Biểu thức đúng | Kiểu kết quả |
| --- | --- | --- |
| Lọc các bit chung | `permissions & mask` | `int` |
| Có **ít nhất một** bit được chọn | `(permissions & mask) != 0` | `boolean` |
| Có **tất cả** bit được chọn | `(permissions & mask) == mask` | `boolean` |
| Bật bit | `permissions \| mask` | `int` |
| Tắt bit | `permissions & ~mask` | `int` |
| Đảo bit | `permissions ^ mask` | `int` |
| Kết hợp hai điều kiện boolean | `canRead && canWrite` | `boolean` |

Không viết `if (permissions & READ)`: `if` yêu cầu boolean nên Java báo lỗi biên dịch. Hãy chuyển **kết quả số nguyên** thành một phép so sánh boolean như `(permissions & READ) != 0`.

**Chú ý dấu ngoặc:** Java ưu tiên `==` và `!=` **cao hơn** `&` bitwise. Luôn viết `(permissions & mask) != 0`, không viết `permissions & mask != 0`.

## 6. Đừng trộn lẫn toán tử và ngữ nghĩa

| Biểu thức | Hợp lệ? | Vì sao? |
| --- | --- | --- |
| `5 & 3` | Có | Kết quả `int` là 1 |
| `true & false` | Có | Kết quả `boolean` false, hai vế đều chạy |
| `true && false` | Có | Kết quả boolean false, có short-circuit |
| `true ^ false` | Có | XOR logic là true |
| `~5` | Có | Bù bit `int`: -6 |
| `!true` | Có | Phủ định boolean: false |
| `5 && 3` | **Không** | `&&` chỉ nhận boolean |
| `~true` | **Không** | `~` chỉ nhận số nguyên |
| `!5` | **Không** | `!` chỉ nhận boolean |
| `5 & true` | **Không** | Không có phép `&` giữa int và boolean |

Khi hai vế của `&`/`|`/`^` đều là boolean thì Java áp dụng logic boolean; khi cả hai là số nguyên thì áp dụng phép toán bit. Không có kiểu kết quả “vừa int vừa boolean”, và không chuyển ngầm hai miền này.

Với `Boolean` wrapper, cần nhớ phép logic có thể **unbox**, gây `NullPointerException` khi tham chiếu là null. Dùng điều kiện bảo vệ như `value != null && value` nếu cần.

## 7. Chương trình Java 17+ runnable

Tạo file **BitwiseVsBooleanDemo.java**:

~~~java
public final class BitwiseVsBooleanDemo {
    private static boolean mark(
            StringBuilder log, String label, boolean result) {
        log.append(label);
        return result;
    }

    public static void main(String[] args) {
        int a = 0b1100;
        int b = 0b1010;
        System.out.println("bitAnd=" + (a & b));
        System.out.println("bitOr=" + (a | b));
        System.out.println("bitXor=" + (a ^ b));
        System.out.println("bitNot=" + (~a));
        System.out.println("bitNotLow4=" + ((~a) & 0b1111));

        final int READ = 0b0001;
        final int WRITE = 0b0010;
        final int EXEC = 0b0100;
        int flags = READ | EXEC;
        System.out.println("readAllowed=" + ((flags & READ) != 0));
        System.out.println("writeAllowed=" + ((flags & WRITE) != 0));
        System.out.println("hasReadExec="
                + ((flags & (READ | EXEC)) == (READ | EXEC)));
        System.out.println("setWrite=" + Integer.toBinaryString(flags | WRITE));

        boolean ready = true;
        boolean allowed = false;
        System.out.println("boolAnd=" + (ready & allowed));
        System.out.println("boolOr=" + (ready | allowed));
        System.out.println("boolXor=" + (ready ^ allowed));
        System.out.println("boolNot=" + (!ready));
        System.out.println("shortAnd=" + (ready && allowed));
        System.out.println("shortOr=" + (ready || allowed));

        StringBuilder trace = new StringBuilder();
        boolean scAnd = mark(trace, "F", false)
                && mark(trace, "X", true);
        boolean scOr = mark(trace, "T", true)
                || mark(trace, "Y", false);
        boolean eagerAnd = mark(trace, "A", false)
                & mark(trace, "B", true);
        boolean eagerOr = mark(trace, "C", true)
                | mark(trace, "D", false);
        boolean eagerXor = mark(trace, "E", true)
                ^ mark(trace, "G", true);
        System.out.println("scAnd=" + scAnd + ",scOr=" + scOr);
        System.out.println("eagerAnd=" + eagerAnd
                + ",eagerOr=" + eagerOr + ",eagerXor=" + eagerXor);
        System.out.println("trace=" + trace);

        int divisor = 0;
        System.out.println("divideGuard="
                + (divisor != 0 && 10 / divisor > 1));
        try {
            boolean error = divisor != 0 & 10 / divisor > 1;
            System.out.println("error=" + error);
        } catch (ArithmeticException e) {
            System.out.println("eagerDivide=ArithmeticException");
        }
    }
}
~~~

Chạy từ thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 BitwiseVsBooleanDemo.java
java BitwiseVsBooleanDemo
~~~

**Expected stdout chính xác:**

~~~text
bitAnd=8
bitOr=14
bitXor=6
bitNot=-13
bitNotLow4=3
readAllowed=true
writeAllowed=false
hasReadExec=true
setWrite=111
boolAnd=false
boolOr=true
boolXor=true
boolNot=false
shortAnd=false
shortOr=true
scAnd=false,scOr=true
eagerAnd=false,eagerOr=true,eagerXor=false
trace=FTABCDEG
divideGuard=false
eagerDivide=ArithmeticException
~~~

Để hiểu `trace=FTABCDEG`: `&&` bỏ qua X, `||` bỏ qua Y; còn `&`, `|`, `^` luôn chạy đủ hai vế. `divideGuard=false` không thực hiện phép chia, nhưng `eagerDivide` ném ngoại lệ vì `&` vẫn đánh giá vế phải.

## 8. Bài tập tự kiểm tra

**Bài 1.** Với `int a = 0b0110, b = 0b1010;`, tính `a & b`, `a | b`, `a ^ b` dưới dạng nhị phân 4 bit.

**Bài 2.** Cho `boolean a = true, b = false;`, dự đoán `a & b`, `a | b`, `a ^ b`, `!a`; phân biệt với các toán tử bit trên số.

**Bài 3.** Vì sao `if (flags & 0b0100)` không biên dịch? Viết lại để kiểm tra bit thứ hai (đếm từ bit 0).

**Bài 4.** Giải thích sự khác nhau giữa `false && check()` và `false & check()` nếu `check()` thay đổi biến hoặc ném ngoại lệ.

**Bài 5.** Viết điều kiện kiểm tra quyền READ **và** WRITE khi quyền lưu trong `int flags`. Phân biệt cách này với kiểm tra **ít nhất một** trong hai quyền.

### Ghi nhớ

**`&`, `|`, `^` có hai cách dùng tùy kiểu:** bitwise với số nguyên, logic không ngắn mạch với boolean. `~` chỉ đảo **bit số nguyên**, còn `!` chỉ phủ định **boolean**. `&&` và `||` chỉ dùng cho boolean và có short-circuit. Muốn biến kết quả bit mask thành điều kiện `if`, so sánh với 0 hoặc với mask, chẳng hạn `(flags & mask) != 0`.


</details>

- [Quay lại đầu trang](#back-to-top)
