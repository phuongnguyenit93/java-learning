<a id="back-to-top"></a>

# Bit và số không dấu

## Menu
- [Các toán tử &amp;, |, ^ và ~](#bits-and-unsigned-001)
- [Dịch trái &lt;&lt;, dịch phải &gt;&gt; và dịch phải không dấu &gt;&gt;&gt;](#bits-and-unsigned-002)
- [Khoảng dịch được rút gọn theo kiểu int hoặc long](#bits-and-unsigned-003)
- [Mở rộng dấu khi nâng kiểu byte hoặc short](#bits-and-unsigned-004)
- [Mặt nạ bit và chuyển byte sang giá trị 0 đến 255](#bits-and-unsigned-005)
- [Byte.toUnsignedInt, Short.toUnsignedInt và Integer.toUnsignedLong](#bits-and-unsigned-006)
- [compareUnsigned, divideUnsigned và remainderUnsigned](#bits-and-unsigned-007)
- [parseUnsignedInt, parseUnsignedLong và toUnsignedString](#bits-and-unsigned-008)
- [bitCount, leading zeros, trailing zeros và one-bit helpers](#bits-and-unsigned-009)
- [rotateLeft, rotateRight, reverse và reverseBytes](#bits-and-unsigned-010)
- [Không đồng nhất dịch phải với chia khi xử lý số âm](#bits-and-unsigned-011)
- [Phân biệt giá trị số với thứ tự byte khi trao đổi dữ liệu](#bits-and-unsigned-012)

## <a id="bits-and-unsigned-001">Các toán tử &amp;, |, ^ và ~</a>

<details>
<summary>Xem chi tiết</summary>

# Các toán tử `&`, `|`, `^` và `~` trong Java — Bit và số không dấu

## 1. Mục tiêu của bài học

Trong Java, **toán tử thao tác bit (bitwise operators)** tác động lên từng bit của giá trị số nguyên. Chúng rất hữu ích khi làm việc với cờ trạng thái, quyền được mã hóa thành bit, giao thức nhị phân, dữ liệu thiết bị, trường màu RGB và các phép biến đổi ở mức bit.

Bài này tập trung vào bốn toán tử:

- `&` — AND theo từng bit;
- `|` — OR theo từng bit;
- `^` — XOR theo từng bit;
- `~` — NOT (đảo tất cả các bit).

Sau bài học, bạn cần tự tính được kết quả nhị phân, hiểu vì sao `~12` cho ra `-13`, biết kiểu `byte`/`short`/`char` được nâng lên `int`, sử dụng mặt nạ bit (bitmask), và nhận biết điểm khác biệt giữa thao tác bit với diễn giải số nguyên **có dấu/không dấu**.

**Điều kiện:** đã biết `int`, `long`, literal nhị phân `0b...`, literal hex `0x...` và các toán tử cơ bản. Ví dụ chạy độc lập với JDK 17 trở lên. Java không có kiểu nguyên thủy `unsigned int` hay `unsigned long`; chủ đề “không dấu” ở đây chủ yếu nói về **cách diễn giải bit** và các API hỗ trợ unsigned.

## 2. Bit và biểu diễn số nguyên Java

Mỗi bit chỉ có thể là `0` hoặc `1`. Java sử dụng các kiểu số nguyên có độ rộng xác định:

| Kiểu | Độ rộng | Có dấu? | Lưu ý |
| --- | ---: | --- | --- |
| `byte` | 8 bit | Có dấu | −128 đến 127 |
| `short` | 16 bit | Có dấu | −32.768 đến 32.767 |
| `char` | 16 bit | Không dấu | Đơn vị mã UTF-16, không phải kiểu số lượng thông thường |
| `int` | 32 bit | Có dấu | −2³¹ đến 2³¹ − 1 |
| `long` | 64 bit | Có dấu | −2⁶³ đến 2⁶³ − 1 |

Các giá trị `byte`, `short`, `int` và `long` được diễn giải theo biểu diễn **bù hai (two's complement)**. Bit cao nhất của `int` hoặc `long` có ý nghĩa dấu khi diễn giải như số nguyên có dấu.

Ta có thể dùng tám hoặc bốn bit để **minh họa trên giấy**, nhưng cần nhớ: phép toán Java trên một biến `int` thực tế làm việc với **32 bit**; biến `long` là **64 bit**. Đó là điểm đặc biệt quan trọng với `~`.

Ví dụ lấy bốn bit thấp:

```text
x = 12 = 1100
y = 10 = 1010
```

`12` ở dạng nhị phân là `1100`, `10` là `1010`. Khi biểu diễn hai giá trị `int`, các bit cao hơn trong ví dụ này là `0`.

## 3. Bảng chân trị: cách bốn toán tử xử lý một bit

Ba toán tử `&`, `|`, `^` so sánh **hai bit tương ứng**:

| Bit A | Bit B | A `&` B | A `|` B | A `^` B |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 0 | 0 | 0 | 0 |
| 0 | 1 | 0 | 1 | 1 |
| 1 | 0 | 0 | 1 | 1 |
| 1 | 1 | 1 | 1 | 0 |

Với NOT `~`, chỉ có **một toán hạng**:

| Bit A | `~`A |
| ---: | ---: |
| 0 | 1 |
| 1 | 0 |

Có thể ghi nhớ:

- AND chỉ bật bit khi **cả hai** bit đều bật.
- OR bật bit khi **ít nhất một** bit bật.
- XOR bật bit khi **hai** bit khác nhau.
- NOT đảo từng bit, `0` thành `1` và ngược lại.

Không nên áp dụng bảng chân trị trên một vị trí duy nhất rồi quên các bit còn lại. Toán tử bitwise xử lý toàn bộ các bit của toán hạng sau khi Java thực hiện quy tắc chuyển kiểu.

## 4. Toán tử `&`: AND theo từng bit

Xét `12` và `10`:

```text
     1100    (12)
   & 1010    (10)
   ------
     1000     (8)
```

Kết quả là `8` vì chỉ bit tương ứng ở vị trí `2³` cùng bằng `1`.

Ví dụ Java:

```java
int x = 0b1100;
int y = 0b1010;
int both = x & y;      // 0b1000 = 8
```

### 4.1. Ứng dụng phổ biến: kiểm tra một bit

Mỗi bit có thể đại diện cho một khả năng hoặc cờ. Giả sử bit `2` biểu thị quyền thực thi:

```java
int flags = 0b0101;
int execute = 0b0100;

boolean enabled = (flags & execute) != 0; // true
```

`flags & execute` sẽ giữ lại đúng các bit có `1` ở **cả** `flags` và `execute`. Nếu chỉ cần kiểm tra **một cờ được biểu diễn bằng một bit**, điều kiện `!= 0` là đủ.

Với mặt nạ có **nhiều bit**, hai câu hỏi khác nhau:

```java
int state = 0b0101;
int mask = 0b0110;

boolean any = (state & mask) != 0;    // true: ít nhất một bit trong mask bật
boolean all = (state & mask) == mask; // false: chưa đủ tất cả bit trong mask
```

Đặc biệt, khi `mask == 0`, biểu thức `(state & mask) == mask` luôn đúng. Nếu mã nghiệp vụ yêu cầu mask phải chứa ít nhất một bit, cần xác thực `mask != 0` riêng.

### 4.2. Ứng dụng: giữ lại một phần bit

`value & 0xFF` giữ **8 bit thấp** và xóa tất cả bit cao hơn. Nó thường được dùng khi cần diễn giải một `byte` thành số từ `0` đến `255`:

```java
byte signed = (byte) 0xF0; // bit pattern 11110000, byte có dấu = -16
int unsigned = signed & 0xFF; // 240
```

Đây là phép bitwise AND và quy tắc promotion của Java, không phải việc biến `byte` thành một kiểu primitive unsigned mới. Xem phần về số không dấu bên dưới.

## 5. Toán tử `|`: OR theo từng bit

```text
     1100    (12)
   | 1010    (10)
   ------
     1110    (14)
```

Ví dụ:

```java
int x = 0b1100;
int y = 0b1010;
int either = x | y;    // 0b1110 = 14
```

`|` thường dùng để **bật bit** hoặc **kết hợp các cờ độc lập**:

```java
int flags = 0b0001;
int writeFlag = 0b0010;

flags = flags | writeFlag; // 0b0011
// Cách viết gọn:
flags |= writeFlag;        // vẫn là 0b0011
```

Nếu bit đã bật, OR với `1` vẫn giữ bit đó bằng `1`. Vì vậy thao tác `flags |= mask` có tính **idempotent theo giá trị bit**: lặp nhiều lần sẽ không tự tắt cờ. Nhưng phép OR không bảo đảm việc cập nhật biến dùng chung giữa các luồng là nguyên tử.

**Không cộng số học thay cho OR để bật cờ.** Nếu bit đã bật, `flags + mask` có thể gây nhớ sang bit khác; `flags | mask` diễn đạt rõ thao tác bật bit.

## 6. Toán tử `^`: XOR theo từng bit

```text
     1100    (12)
   ^ 1010    (10)
   ------
     0110     (6)
```

Ví dụ:

```java
int x = 0b1100;
int y = 0b1010;
int different = x ^ y; // 0b0110 = 6
```

XOR hữu ích khi cần **đảo trạng thái các bit được chọn**:

```java
int flags = 0b0101;
int mask = 0b0100;

flags ^= mask; // 0b0001: tắt bit 2
flags ^= mask; // 0b0101: bật lại bit 2
```

Các tính chất quan trọng với bit nguyên:

- `x ^ 0 == x`.
- `x ^ x == 0`.
- `(x ^ mask) ^ mask == x`: áp dụng cùng XOR hai lần sẽ phục hồi bit ban đầu.
- `a ^ b == b ^ a` với toán hạng nguyên cùng kiểu.

**XOR không phải phép lũy thừa.** Trong Java, `2 ^ 3` bằng `1` vì `010 ^ 011 = 001`, **không** phải `8`. Muốn tính lũy thừa, cân nhắc `Math.pow` cho số thực hoặc thuật toán nhân nguyên có kiểm tra tràn tùy yêu cầu.

Một ứng dụng khác là phát hiện bit khác nhau giữa hai trạng thái: `changed = oldState ^ newState` tạo mask với bit `1` đúng tại những vị trí có khác biệt.

## 7. Toán tử `~`: NOT trên *toàn bộ* bit của toán hạng

`~` là toán tử **một ngôi**:

```java
int x = 12;
int flipped = ~x; // -13
```

Tại sao `~12` là `-13` mà không phải `3`?

Nếu chỉ **vẽ bốn bit**, phép NOT cho:

```text
x = 1100
~   ----
    0011   (3, nếu chỉ đảo trong một mặt nạ rộng 4 bit)
```

Nhưng `x` là `int` 32 bit. Java đảo cả **32 bit**:

```text
x:  00000000 00000000 00000000 00001100
~x: 11111111 11111111 11111111 11110011
```

Bit cao nhất trở thành `1`, nên khi diễn giải theo bù hai, giá trị là `-13`. Với `int`/`long`, đồng nhất thức đúng theo số học Java là:

```text
~x == -x - 1
```

Ví dụ `~0 == -1`, `~(-1) == 0` và `~12 == -13`.

### 7.1. Muốn đảo trong phạm vi N bit, phải dùng mặt nạ

Giả sử chỉ quan tâm bốn bit thấp:

```java
int x = 0b1100;            // 12
int lowFourInverted = ~x & 0b1111;
System.out.println(lowFourInverted); // 3
```

Mặt nạ `0b1111` xóa các bit cao sau khi NOT, giữ lại `0011`. Tương tự, `~x & 0xFF` giữ tám bit thấp của giá trị sau đảo.

### 7.2. `~` không giống `!`

- `~` làm việc trên **kiểu nguyên** (sau unary numeric promotion), đảo tất cả bit.
- `!` làm việc trên **`boolean`** (hoặc `Boolean` được unbox), đảo giá trị logic.

`~true` không hợp lệ; `!12` cũng không hợp lệ. Cách dùng đúng:

```java
int invertedBits = ~12;       // -13
boolean invertedFlag = !true; // false
```

## 8. Kiểu dữ liệu, binary numeric promotion và kết quả trung gian

Các toán tử `&`, `|`, `^` với **toán hạng nguyên** dùng quy tắc **binary numeric promotion**. Toán tử `~` dùng **unary numeric promotion**.

- `byte`, `short` và `char` được nâng lên `int` trước phép toán.
- `int` với `int` cho kết quả `int`.
- `long` kết hợp với kiểu nguyên nhỏ hơn dẫn đến kết quả `long`.
- `~byteValue`, `~shortValue` hoặc `~charValue` cho kết quả `int`, không tự trở về kiểu nhỏ.
- Không có bitwise `&`/`|`/`^`/`~` trực tiếp trên `float` hay `double`.

Ví dụ:

```java
byte a = 0b0101;
byte b = 0b0011;
int andResult = a & b;     // 1, kiểu int
int notResult = ~a;        // -6, kiểu int

// byte invalid = a & b;  // lỗi: biểu thức là int, không tự thu hẹp thành byte
byte narrowed = (byte) (a & b); // cast tường minh, giá trị 1 ở ví dụ này
```

**Lưu ý với compound assignment:** `a &= b` có thể biên dịch khi `a` là `byte`, vì phép gán ghép có chuyển kiểu ngầm về kiểu biến đích. Nhưng `a = a & b` thường lỗi kiểu do vế phải là `int`. Chuyển hẹp có thể mất các bit cao nên cần chủ động xác định miền dữ liệu.

### 8.1. Sign extension khi nâng một `byte` âm

Xem giá trị có tám bit `1111_0000`:

```java
byte raw = (byte) 0xF0;  // raw == -16
int promoted = raw;      // 0xFFFFFFF0, sign-extended
int unsigned = raw & 0xFF; // 0x000000F0 = 240
int inverse = ~raw;      // 0x0000000F = 15
```

Bởi `raw` là số có dấu, khi chuyển lên `int`, các bit cao được **mở rộng dấu**. Trong `raw & 0xFF`, mask xóa 24 bit cao và giữ nguyên tám bit thấp.

Java cũng có `Byte.toUnsignedInt(raw)` để nhận `240` rõ ý định hơn. Đối với `short`, có `Short.toUnsignedInt`. Không nên viết `Math.abs(raw)` thay cho việc diễn giải 8 bit không dấu; giá trị âm của kiểu signed và unsigned representation là hai vấn đề khác nhau.

### 8.2. `char` không mở rộng dấu

`char` là giá trị 16 bit không dấu. Khi promoted thành `int`, các bit cao là `0`. Ví dụ `char ch = 0xFF00; int n = ch;` cho `n = 65280`, không phải một số âm. Tuy vậy `~ch` vẫn có kiểu `int` và có thể âm vì đảo đủ 32 bit.

Trong xử lý text, `char` đại diện cho **đơn vị mã UTF-16**, không tương đương một Unicode code point trong mọi trường hợp; không dùng toán tử bit chỉ vì muốn thao tác chuỗi ký tự.

## 9. Phân biệt bitwise integer và các phép boolean `&`, `|`, `^`

Java còn định nghĩa `&`, `|` và `^` cho hai toán hạng **boolean`. Khi đó chúng trả `boolean`:

| Toán tử boolean | Quy tắc |
| --- | --- |
| `a & b` | `true` khi cả `a` và `b` đều `true` |
| `a | b` | `true` khi ít nhất một bên `true` |
| `a ^ b` | `true` khi hai bên khác nhau |

Điểm khác biệt quan trọng so với `&&` và `||` là **`&`/`|` trên boolean đánh giá cả hai toán hạng**, còn `&&`/`||` có **short circuit**:

- `false && method()` không gọi `method()`.
- `true || method()` không gọi `method()`.
- `false & method()` vẫn gọi `method()`.
- `true | method()` vẫn gọi `method()`.
- `a ^ b` cũng đánh giá cả hai bên.

Không thể áp dụng `&` giữa một `int` và một `boolean`; biểu thức `flags & true` lỗi biên dịch. Java không tự coi số nguyên `0` là `false` hoặc số nguyên khác `0` là `true`.

### Ví dụ chạy được: so sánh short circuit

Lưu tệp **`BooleanBitwise.java`**:

```java
public class BooleanBitwise {
    static boolean check(String name) {
        System.out.println(name);
        return true;
    }

    public static void main(String[] args) {
        System.out.println(false & check("AND evaluated"));
        System.out.println(true | check("OR evaluated"));
        System.out.println(false && check("AND skipped"));
        System.out.println(true || check("OR skipped"));
        System.out.println(true ^ true);
    }
}
```

Chạy:

```bash
javac --release 17 BooleanBitwise.java
java BooleanBitwise
```

Stdout:

```text
AND evaluated
false
OR evaluated
true
false
true
false
```

Hai nhãn “AND skipped” và “OR skipped” **không xuất hiện**, vì hai phương thức tương ứng không được gọi. Trong biểu thức boolean thông thường, nên ưu tiên `&&`/`||` khi muốn short circuit; dùng `&`/`|` khi thực sự cần cả hai biểu thức được đánh giá và điều đó rõ ràng trong yêu cầu.

## 10. Độ ưu tiên: `~`, `&`, `^`, `|`

Trong các toán tử đang học, thứ tự ưu tiên là:

1. `~` (unary bitwise complement).
2. `&`.
3. `^`.
4. `|`.

Ví dụ:

```java
int a = 0b1100;
int b = 0b1010;
int c = 0b0011;

int first = a | b & c;       // a | (b & c)
int second = (a | b) & c;    // (a | b) & c
```

Tính thủ công:

- `b & c = 0010`; `a | 0010 = 1110` nên `first == 14`.
- `a | b = 1110`; `1110 & 0011 = 0010` nên `second == 2`.

Với XOR:

```java
int ungrouped = a ^ b & c;   // a ^ (b & c) => 1100 ^ 0010 = 1110
int grouped = (a ^ b) & c;   // 0110 & 0011 = 0010
```

Hai biểu thức lần lượt cho `14` và `2`.

**Bẫy thường gặp với phép so sánh:** Trong Java, `==` có độ ưu tiên **cao hơn** bitwise `&`. Vì thế:

```java
// boolean invalid = flags & MASK == MASK;
// Java nhóm như flags & (MASK == MASK): int & boolean -> lỗi biên dịch.

boolean correct = (flags & MASK) == MASK;
```

Đoạn trên chỉ là **minh họa cú pháp**, cần khai báo `flags` và `MASK` nếu đưa vào class chạy. Luôn đặt ngoặc quanh phần bitwise trong điều kiện, chẳng hạn `(flags & mask) != 0`, để vừa tránh lỗi vừa bộc lộ ý định.

Các toán tử shift `<<`, `>>`, `>>>` có độ ưu tiên cao hơn `&`/`^`/`|`. Chúng dùng để định vị bit, nhưng là một nhóm toán tử khác; bài này chỉ dùng shift khi cần tạo mask và tách dữ liệu.

## 11. Một chương trình đầy đủ cho bốn toán tử

Lưu thành **`BitwiseBasics.java`**:

```java
public class BitwiseBasics {
    public static void main(String[] args) {
        int x = 0b1100;
        int y = 0b1010;

        System.out.println(x & y);
        System.out.println(x | y);
        System.out.println(x ^ y);
        System.out.println(~x);
        System.out.println(~x & 0b1111);

        System.out.println(~0);
        System.out.println(~(-1));
    }
}
```

Chạy:

```bash
javac --release 17 BitwiseBasics.java
java BitwiseBasics
```

**Stdout chính xác:**

```text
8
14
6
-13
3
-1
0
```

Giải thích: ba dòng đầu tương ứng bảng bit `1100`/`1010`; `~x` đảo 32 bit của `int` và thành `-13`; `& 0b1111` giữ bốn bit thấp nên nhận `3`. Đảo toàn bộ bit của `0` cho `-1`, và đảo `-1` cho `0`.

**Đừng dùng `Integer.toBinaryString(~x)` rồi mong chỉ có bốn ký tự**, vì kết quả `int` âm được thể hiện đầy đủ bằng chuỗi 32 bit theo two's complement. Muốn xem phần bit thấp, dùng mask trước khi chuyển sang chuỗi.

## 12. Dùng bitmask để bật, tắt, kiểm tra và đảo cờ

Một cờ một bit có thể tạo bằng `1 << vị_trí_bit`. Từ đây có bốn công thức quan trọng:

| Mục đích | Công thức | Ý nghĩa |
| --- | --- | --- |
| Kiểm tra **một** cờ | `(flags & FLAG) != 0` | Bit đại diện cờ đang bật |
| Bật cờ | `flags |= FLAG` | Đặt bit thành 1 |
| Tắt cờ | `flags &= ~FLAG` | Xóa bit về 0 |
| Đảo cờ | `flags ^= FLAG` | Đổi 0↔1 |
| Kiểm tra đủ **nhiều** cờ | `(flags & MASK) == MASK` | Tất cả bit trong mask đều bật |

### Ví dụ chạy được: trạng thái quyền bằng bit

Lưu thành **`FlagDemo.java`**:

```java
public class FlagDemo {
    private static final int READ = 1 << 0;    // 001
    private static final int WRITE = 1 << 1;   // 010
    private static final int EXECUTE = 1 << 2; // 100

    public static void main(String[] args) {
        int permissions = READ | EXECUTE; // 101
        System.out.println(permissions);
        System.out.println((permissions & WRITE) != 0);

        permissions |= WRITE;   // 111
        System.out.println(permissions);

        permissions ^= EXECUTE; // 011
        System.out.println(permissions);

        permissions &= ~READ;   // 010
        System.out.println(permissions);
    }
}
```

Chạy:

```bash
javac --release 17 FlagDemo.java
java FlagDemo
```

**Stdout:**

```text
5
false
7
3
2
```

Trình tự:

- `READ | EXECUTE` bật bit `0` và bit `2`, giá trị là `101₂ = 5`.
- Chưa có `WRITE` nên kiểm tra trả `false`.
- `|= WRITE` bật thêm bit `1` → `111₂ = 7`.
- `^= EXECUTE` đảo bit `2` từ `1` về `0` → `011₂ = 3`.
- `&= ~READ` xóa bit `0` → `010₂ = 2`.

**Chọn đúng thao tác:** muốn **bảo đảm bật** thì dùng `|`; muốn **bảo đảm tắt** thì dùng `& ~`; chỉ dùng `^` khi thật sự muốn **đảo** trạng thái. Việc dùng mask tự nó không thay thế quy tắc phân quyền của ứng dụng hay kiểm tra quyền theo người dùng/tài nguyên.

### 12.1. Khi bit vượt phạm vi 31

`int` có 32 bit; `1 << 31` vẫn hợp lệ nhưng cho giá trị `Integer.MIN_VALUE` âm vì bật bit dấu. Nếu cần cờ ở vị trí `40`, phải dùng `long`:

```java
long flag40 = 1L << 40;
```

Viết `1 << 40` **không** tạo bit 40 của `int`: Java chỉ dùng 5 bit thấp của shift distance với `int`, nên hiệu ứng tương đương dịch `1 << 8`. Với `long`, số bước dịch chỉ xét 6 bit thấp. Luôn chọn độ rộng trước khi tạo mask và kiểm tra phạm vi chỉ số bit.

## 13. `&` với số không dấu: cùng bit, cách diễn giải khác

Java không có kiểu nguyên thủy `unsigned int` hoặc `unsigned long`. `int` và `long` vẫn dùng các bit tương ứng cho phép AND/OR/XOR/NOT dù chúng được diễn giải như số âm. Sự khác biệt giữa có dấu và không dấu nằm ở **cách diễn giải giá trị và một số phép toán khác** (so sánh, chia, dịch phải), không phải bảng chân trị của bốn toán tử bitwise.

### 13.1. Đọc một `byte` thành 0..255

Lưu thành **`UnsignedBits.java`**:

```java
public class UnsignedBits {
    public static void main(String[] args) {
        byte raw = (byte) 0xF0;

        System.out.println(raw);
        System.out.println(raw & 0xFF);
        System.out.println(Byte.toUnsignedInt(raw));
        System.out.println(~raw);
        System.out.println((byte) ~raw);
        System.out.println(Integer.toUnsignedString(-1));
    }
}
```

Chạy:

```bash
javac --release 17 UnsignedBits.java
java UnsignedBits
```

**Stdout:**

```text
-16
240
240
15
15
4294967295
```

Giải thích:

1. `0xF0` có 8 bit thấp `11110000`; khi ép về `byte` có dấu, đó là `-16`.
2. `raw & 0xFF` xóa bit cao đã được mở rộng dấu → `240`.
3. `Byte.toUnsignedInt(raw)` trả cùng giá trị `240` với ý nghĩa unsigned tường minh.
4. `~raw` được tính ở kiểu `int` sau unary promotion: `~(-16) = 15`.
5. Cast `(byte) ~raw` trong ví dụ vẫn là `15` vì vừa trong miền `byte`; với dữ liệu khác, cast hẹp có thể cắt bit.
6. `Integer.toUnsignedString(-1)` diễn giải 32 bit toàn `1` thành `4294967295`. Biểu diễn bit không đổi; chỉ thay cách đọc giá trị.

Để so sánh/chia số nguyên theo hướng unsigned, Java còn có `Integer.compareUnsigned`, `Integer.divideUnsigned`, `Integer.remainderUnsigned` cùng các API `Long` tương ứng. `char` là trường hợp primitive không dấu 16 bit, nhưng chủ yếu phục vụ UTF-16.

### 13.2. Cách tách nhiều trường từ một số

Một tình huống thực tế là tách ba kênh màu RGB được đóng gói vào 24 bit:

```java
int rgb = 0x12AB34;
int red   = (rgb >>> 16) & 0xFF;
int green = (rgb >>> 8)  & 0xFF;
int blue  = rgb & 0xFF;
```

Trong đó `>>>` là **dịch phải điền 0**, đưa nhóm bit cần đọc xuống vị trí thấp; `& 0xFF` chỉ giữ tám bit tương ứng. Ba giá trị nhận được: `red = 18`, `green = 171`, `blue = 52`. Dùng `&` để mask là điều cốt lõi; cần học riêng `>>`, `>>>`, `<<` để hiểu toàn bộ thuật toán đóng gói bit.

## 14. Các tính chất đại số hữu ích và giới hạn áp dụng

Với các toán hạng nguyên cùng kiểu và phép bitwise thuần, những luật sau đúng trên toàn bộ bit của kiểu:

| Tính chất | Biểu thức |
| --- | --- |
| AND với 0 | `x & 0 == 0` |
| AND với toàn bit 1 | `x & -1 == x` |
| OR với 0 | `x | 0 == x` |
| OR với toàn bit 1 | `x | -1 == -1` |
| XOR với 0 | `x ^ 0 == x` |
| XOR với chính nó | `x ^ x == 0` |
| Đảo hai lần | `~~x == x` (khi `x` đã là `int`/`long`) |
| De Morgan | `~(x & y) == (~x | ~y)` |
| De Morgan | `~(x | y) == (~x & ~y)` |

Các luật này rất hữu ích khi đơn giản hóa mặt nạ. Tuy nhiên cần giữ **cùng độ rộng**, xét promotion, và không tự ý biến phép toán có lời gọi hàm/side effect thành biểu thức khác.

### 14.1. XOR không bảo đảm hoán đổi biến an toàn

Mẹo `a ^= b; b ^= a; a ^= b;` thường được dùng để swap hai số. Nó khó đọc, có thể sai khi hai tham chiếu/vị trí thực chất trỏ vào cùng phần tử, và không mang lợi ích mặc định trong Java hiện đại. Hãy dùng biến tạm tường minh để swap giá trị. Mục tiêu học XOR là hiểu bit, không phải thay thế mọi thao tác dữ liệu bằng “mẹo”.

### 14.2. Thao tác bit không đồng nghĩa phép cập nhật nguyên tử

`flags |= mask` là phép đọc, OR và ghi lại theo ngữ nghĩa bình thường. Nếu nhiều luồng cùng thay đổi một biến, có thể mất cập nhật nếu không đồng bộ. Với yêu cầu đồng thời, dùng cơ chế đồng bộ hoặc các phương thức cập nhật nguyên tử phù hợp; phạm vi của toán tử `|` không tự cung cấp thread safety.

## 15. Lỗi phổ biến và cách phát hiện

1. **Nhầm `^` với mũ:** `2 ^ 3` = `1`, không phải 8.
2. **Nghĩ `~12` là 3:** đó chỉ là kết quả NOT ở **bốn bit thấp**; `~12` với `int` 32 bit bằng `-13`. Muốn 3 dùng `(~12) & 0xF`.
3. **Quên promotion của `byte`:** `~byteValue` cho `int`, không cho `byte`; `byteA & byteB` cũng cho `int`.
4. **Không mask sau sign extension:** `byte raw = (byte) 0xF0` là `-16`; `raw & 0xFF` hoặc `Byte.toUnsignedInt(raw)` trả 240.
5. **Dùng `^` để “bật cờ”:** XOR sẽ tắt một cờ đang bật. Dùng `|` khi yêu cầu chắc chắn bật.
6. **Dùng `|` để “tắt cờ”:** muốn tắt một bit phải dùng `flags &= ~FLAG`.
7. **Sai mức ưu tiên:** `flags & MASK == MASK` không có nhóm mong muốn; đặt `(flags & MASK) == MASK`.
8. **Nhầm `&` với `&&`:** với `boolean`, `&` luôn đánh giá cả hai bên, nên lời gọi hàm thứ hai vẫn chạy.
9. **Quên rằng `~` là NOT cho toàn độ rộng:** mask hẹp chỉ được áp dụng khi ghi rõ `& mask` sau đó.
10. **Tạo mask dùng `int` cho bit cao:** `1 << 40` không phải cờ vị trí 40; dùng `1L << 40`.
11. **Nhầm unsigned bit pattern với giá trị signed:** cùng `0xFFFFFFFF` là bit toàn 1; đọc kiểu `int` là `-1`, đọc unsigned 32 bit là `4294967295`.
12. **Trộn số thực/boolean với integer bitwise:** `1.5 & 2`, `~1.0` hoặc `3 & true` không có kiểu toán hạng hợp lệ trong Java.

## 16. Bài luyện tập kèm giải thích

### Bài 1 — Tính tay bảng bit

Với `a = 0b1100` và `b = 0b1010`, hãy tính `a & b`, `a | b` và `a ^ b`.

**Đáp án:** `1000₂ = 8`, `1110₂ = 14`, `0110₂ = 6`. AND giữ bit cùng 1, OR giữ bit có ít nhất một 1, XOR chỉ giữ bit khác nhau.

### Bài 2 — Vì sao NOT ra số âm?

Tính `~0`, `~12` và `~(-1)` với toán hạng `int`.

**Đáp án:** `-1`, `-13` và `0`. `~` đảo đủ 32 bit; công thức `~x = -x - 1` có thể giúp kiểm tra.

### Bài 3 — Bật, tắt, đảo

Ban đầu `flags = 0b0101`; cần (a) bật bit `1`, (b) tắt bit `2`, (c) đảo bit `0`.

**Đáp án:** (a) `flags |= 0b0010` → `0111`; (b) `flags &= ~0b0100` → `0011`; (c) `flags ^= 0b0001` → `0010`. Ba bước thực hiện **liên tiếp**.

### Bài 4 — Một bit và nhiều bit

`flags = 0b0101`, `mask = 0b0110`. Kết quả `(flags & mask) != 0` và `(flags & mask) == mask` là gì?

**Đáp án:** `true` và `false`. Mask yêu cầu bit 1 và 2, nhưng flags chỉ chứa bit 2 trong hai bit đó.

### Bài 5 — Promotion và unsigned byte

`byte raw = (byte) 0xF0;`. Hãy tính `raw`, `raw & 0xFF` và `~raw`.

**Đáp án:** `-16`, `240`, `15`. `raw` sign-extends lên `int` trong hai phép bitwise, còn mask `0xFF` giữ tám bit thấp.

### Bài 6 — Chọn toán tử theo ý định

Tại sao `flags ^= FLAG` không phải lựa chọn mặc định để đảm bảo một cờ bật?

**Đáp án:** XOR **đảo** bit. Nếu cờ đã bật, XOR tắt nó. OR với `FLAG` bảo đảm bit bật, tức `flags |= FLAG`.

### Bài 7 — Boolean có side effect

Giả sử `probe()` in thông báo rồi trả `true`. So sánh `false & probe()` với `false && probe()`.

**Đáp án:** biểu thức thứ nhất vẫn gọi `probe()` và trả `false`; biểu thức thứ hai bỏ qua `probe()` do short circuit và cũng trả `false`.

### Bài 8 — Mask ở bit 40

Tại sao `1 << 40` sai nếu mục tiêu là tạo một cờ tại vị trí bit 40? Viết lại đúng.

**Đáp án:** `1` là `int` 32 bit; khoảng dịch hiệu dụng chỉ xét năm bit thấp nên `40` được xử lý như `8`. Viết `1L << 40` để dùng `long` và dịch đúng vị trí.

### Bài 9 — Tách màu RGB

Cho `rgb = 0x12AB34`. Dùng `& 0xFF` và dịch phải để lấy red, green, blue.

**Đáp án:**

```java
int red   = (rgb >>> 16) & 0xFF; // 18
int green = (rgb >>> 8)  & 0xFF; // 171
int blue  = rgb & 0xFF;          // 52
```

Đoạn này là **mảnh mã**; đặt trong phương thức với `int rgb = 0x12AB34;` trước khi chạy.

## 17. Checklist thành thạo

- [ ] Tôi tự dựng được bảng chân trị của AND, OR, XOR và NOT.
- [ ] Tôi lý giải được `12 & 10 = 8`, `12 | 10 = 14`, `12 ^ 10 = 6`.
- [ ] Tôi không nhầm `~12` với việc đảo chỉ bốn bit.
- [ ] Tôi biết `&`/`|`/`^` và `~` áp dụng lên kiểu nguyên sau promotion; `~byte` cho `int`.
- [ ] Tôi phân biệt `&`/`|` trên `boolean` với `&&`/`||` và cơ chế short circuit.
- [ ] Tôi chọn được mask để bật, tắt, kiểm tra hoặc đảo cờ.
- [ ] Tôi biết phải đặt ngoặc với biểu thức kiểm tra `(flags & mask) != 0` và `(flags & mask) == mask`.
- [ ] Tôi hiểu ý nghĩa dấu khi đọc `byte`/`int` và cách mask ra phần thấp không dấu.
- [ ] Tôi biết cờ ở bit cao cần `long` và literal `1L` trước phép dịch.
- [ ] Tôi đã chạy ví dụ Java 17+ và kiểm tra các trường hợp số âm, bit cao và mask nhiều bit.

**Tài liệu tham khảo chính thức:**

- [JLS 17 §15.22 — Bitwise and Logical Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.22): quy tắc `&`, `^`, `|` với integer/boolean.
- [JLS 17 §15.15.5 — Bitwise Complement Operator](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.15.5): quy tắc toán tử `~`.
- [JLS 17 §5.6 — Numeric Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.6): unary/binary numeric promotion.
- [JLS 17 §15.19 — Shift Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.19): dịch bit và số bước dịch hiệu dụng.
- [Java SE 17 Integer API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): `toBinaryString`, `toUnsignedString` và các phương thức unsigned.
- [Java SE 17 Byte API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Byte.html): `toUnsignedInt`.

Hãy luôn đọc biểu thức bitwise theo **bit width, kiểu sau promotion, mask và mục đích thao tác**. Bốn toán tử đơn giản về bảng chân trị, nhưng hiệu quả và độ đúng phụ thuộc vào cách bạn diễn giải các bit thành giá trị trong Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-002">Dịch trái &lt;&lt;, dịch phải &gt;&gt; và dịch phải không dấu &gt;&gt;&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Dịch trái `<<`, dịch phải `>>` và dịch phải không dấu `>>>` trong Java

## 1. Ba toán tử dịch bit giải quyết vấn đề gì?

Trong Java, các toán tử dịch bit (*bitwise shift operators*) di chuyển các bit của một giá trị số nguyên theo số vị trí được chỉ định:

- **`x << n` — dịch trái (*left shift*)**: đưa các bit của `x` sang trái `n` vị trí, điền bit `0` vào các vị trí trống bên phải; những bit đi ra khỏi độ rộng kiểu bị loại bỏ.
- **`x >> n` — dịch phải có dấu (*signed/arithmetic right shift*)**: đưa các bit sang phải; các vị trí trống bên trái được điền bằng **bit dấu** của toán hạng (`0` nếu không âm, `1` nếu âm).
- **`x >>> n` — dịch phải không dấu (*unsigned/logical right shift*)**: đưa các bit sang phải; các vị trí trống bên trái **luôn được điền `0`**.

Cả ba thao tác trên **mẫu bit** của số nguyên và tạo ra một kết quả có kiểu số nguyên. `>>>` không tạo ra kiểu `unsigned int` mới: Java không có kiểu nguyên thủy riêng mang tên này.

| Phép dịch | Hướng | Bit điền vào vùng trống | Điểm đáng nhớ |
|---|---|---|---|
| `<<` | Trái | `0` từ bên phải | Có thể mất bit cao hoặc đổi dấu |
| `>>` | Phải | Lặp lại bit dấu từ bên trái | Giữ cách diễn giải dấu với số âm |
| `>>>` | Phải | `0` từ bên trái | Đọc mẫu bit như giá trị không dấu khi dịch |

Ví dụ với một mẫu **8 bit minh họa** là `0010 1100` (44 thập phân):

```text
0010 1100  (44)
0101 1000  (dịch trái 1 lần -> 88)
0001 0110  (dịch phải 1 lần -> 22)
```

Ở số không âm và không có bit cao bị mất, `>>` và `>>>` thường cho cùng kết quả. Sự khác biệt quan trọng xuất hiện với **số âm**.

**Lưu ý:** bảng 8 bit phía trên chỉ minh họa trực quan. Trong Java, toán hạng `byte` và `short` được nâng lên `int` trước khi dịch; để dự đoán đúng kết quả Java, luôn tính trên độ rộng **32 hoặc 64 bit** của biểu thức thực tế.

## 2. Số nguyên có dấu và cách đọc bit cao nhất

Java sử dụng biểu diễn **bù hai** (*two's complement*) cho `int` 32 bit và `long` 64 bit:

- Bit cao nhất của mẫu bit có dấu là **bit dấu**; `0` tương ứng số không âm, `1` tương ứng số âm.
- Ví dụ `int` dương 8 có mẫu bit 32 bit `00000000 00000000 00000000 00001000`.
- `int` âm -8 có mẫu bit 32 bit `11111111 11111111 11111111 11111000`.

Trong nội dung này, thuật ngữ **“dịch phải không dấu”** mô tả **cách chèn bit 0** của toán tử `>>>`, không có nghĩa toán hạng trở thành kiểu dữ liệu unsigned. Kết quả vẫn là `int` hoặc `long` theo kiểu biểu thức.

### Hình dung trên ví dụ -8 dưới dạng `int` 32 bit

```text
-8 (int):
11111111 11111111 11111111 11111000

-8 >> 2  (điền bit dấu 1):
11111111 11111111 11111111 11111110  = -2

-8 >>> 2 (điền bit 0):
00111111 11111111 11111111 11111110  = 1073741822
```

Hai phép dịch đều đưa bit sang **phải 2 vị trí**, nhưng `>>` duy trì phần mở rộng dấu trong khi `>>>` điền 0. Vì bit đầu của kết quả `>>>` trên đây là 0, kết quả được diễn giải là số `int` dương.

## 3. Toán tử `<<`: dịch trái

Cú pháp:

```java
int result = value << distance;
```

Các bit được đẩy sang trái, vùng trống bên phải điền `0`. Đối với các giá trị không âm và khi kết quả vẫn nằm trong miền có dấu, dịch trái `n` vị trí cho cùng kết quả toán học với **nhân \(2^n\)**:

```java
int a = 3 << 1; // 6, tương ứng 3 * 2
int b = 3 << 2; // 12, tương ứng 3 * 4
int c = 5 << 3; // 40, tương ứng 5 * 8
```

Tuy nhiên, toán tử dịch trái **không kiểm tra tràn số**. Các bit vượt ra khỏi biên trái bị loại; dấu của kết quả có thể thay đổi.

```java
int signBit = 1 << 31; // -2147483648
int wrapped = 1 << 32; // 1, vì khoảng dịch int bị giới hạn 5 bit thấp
long wide = 1L << 32;  // 4294967296, toán hạng trái là long
```

Ở `1 << 31`, bit 1 được đưa đến vị trí dấu của `int`, khiến kết quả mang giá trị âm `Integer.MIN_VALUE`. Với `1 << 32`, Java **không dịch đủ 32 vị trí**; quy tắc khoảng dịch sẽ được giải thích riêng trong mục 7.

**Không coi `x << n` là phép nhân an toàn với \(2^n\)** trong trường hợp tổng quát. Dịch bit có thể bỏ bit cao, tràn miền hoặc thay đổi dấu; phép dịch cũng không phát hiện lỗi bằng ngoại lệ. Nếu nghiệp vụ cần phép nhân chính xác và phát hiện tràn, hãy cân nhắc `Math.multiplyExact` hoặc `BigInteger`.

## 4. Toán tử `>>`: dịch phải giữ dấu

Cú pháp:

```java
int result = value >> distance;
```

Khi dịch phải có dấu, vùng trống bên trái được điền bằng bản sao của **bit cao nhất**:

- Số không âm: bit dấu là `0`, do đó chèn `0`.
- Số âm: bit dấu là `1`, do đó chèn `1`.

Ví dụ:

```java
int positive = 40 >> 2; // 10
int negative = -8 >> 2; // -2
int odd = -7 >> 1;      // -4
```

Với số nguyên có dấu, kết quả của `x >> n` tương ứng phép chia lấy phần nguyên xuống dưới (*floor*) của \(x / 2^n\), khi hiểu khoảng dịch theo quy tắc Java. Vì vậy phải cẩn thận với số âm lẻ:

```java
System.out.println(-7 / 2);  // -3: chia nguyên làm tròn về 0
System.out.println(-7 >> 1); // -4: dịch phải làm tròn xuống
```

Phép `/` trên số nguyên Java **làm tròn về 0**, trong khi dịch phải dấu với số âm thường có hiệu ứng làm tròn về âm vô cực. Hai cách không luôn tương đương.

**Khi nào dùng `>>`?** Khi bạn muốn xử lý các bit trong kiểu số nguyên có dấu và cần giữ hiệu ứng mở rộng dấu, hoặc khi một thuật toán bit được định nghĩa rõ bằng dịch phải số học.

## 5. Toán tử `>>>`: dịch phải điền `0`

Cú pháp:

```java
int result = value >>> distance;
```

Toán tử này luôn điền bit `0` vào vùng trống bên trái, bất kể dấu của toán hạng. Ví dụ:

```java
int a = 40 >>> 2;   // 10
int b = -8 >>> 2;   // 1073741822
int c = -1 >>> 1;   // 2147483647
long d = -1L >>> 1; // 9223372036854775807L
```

Với số không âm, `>>` và `>>>` cho cùng kết quả nếu cùng toán hạng và khoảng dịch. Với số âm, `>>>` thường cho kết quả số **dương lớn** vì bit dấu `1` ban đầu được thay bằng `0`.

`>>>` hữu ích khi đọc các trường bit, xử lý mẫu bit như **bit pattern không dấu**, trích xuất byte/nhóm bit từ số nguyên, hoặc triển khai thuật toán băm, checksum, mã hóa nhị phân.

Không nhầm `>>>` với phép chia số có dấu. Ví dụ, `-8 / 4` bằng -2; `-8 >>> 2` bằng 1_073_741_822. Nếu muốn phép chia nguyên theo nghĩa toán học trong Java, hãy dùng `/` hoặc API phù hợp thay vì tự thay thế bằng `>>>`.

## 6. Chương trình Java 17+ đối chiếu cả ba toán tử

Tạo file `ShiftOperatorsDemo.java`:

```java
public class ShiftOperatorsDemo {
    public static void main(String[] args) {
        int positive = 40;
        int negative = -8;

        System.out.println("40 << 2 = " + (positive << 2));
        System.out.println("40 >> 2 = " + (positive >> 2));
        System.out.println("40 >>> 2 = " + (positive >>> 2));

        System.out.println("-8 << 2 = " + (negative << 2));
        System.out.println("-8 >> 2 = " + (negative >> 2));
        System.out.println("-8 >>> 2 = " + (negative >>> 2));

        System.out.println("-1 >> 1 = " + (-1 >> 1));
        System.out.println("-1 >>> 1 = " + (-1 >>> 1));
        System.out.println("-7 / 2 = " + (-7 / 2));
        System.out.println("-7 >> 1 = " + (-7 >> 1));

        System.out.printf("hex(-8 >>> 2) = %08X%n", negative >>> 2);
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac ShiftOperatorsDemo.java
java ShiftOperatorsDemo
```

Kết quả chính xác:

```text
40 << 2 = 160
40 >> 2 = 10
40 >>> 2 = 10
-8 << 2 = -32
-8 >> 2 = -2
-8 >>> 2 = 1073741822
-1 >> 1 = -1
-1 >>> 1 = 2147483647
-7 / 2 = -3
-7 >> 1 = -4
hex(-8 >>> 2) = 3FFFFFFE
```

Quan sát hai điểm:

1. Dịch trái `-8 << 2` cho -32 trong ví dụ này vì các bit còn lại tạo ra giá trị -32, nhưng không thể kết luận mọi phép dịch trái đều tương đương phép nhân số học an toàn.
2. Với `-8 >>> 2`, kết quả hex `3FFFFFFE` cho thấy hai bit `1` đầu ở biểu diễn âm đã được thay bằng hai bit `0`.

## 7. Khoảng dịch và kiểu của vế trái

Java không ném ngoại lệ khi số bước dịch âm hoặc lớn hơn độ rộng kiểu. Khoảng dịch hiệu dụng được rút gọn theo **kiểu vế trái sau nâng kiểu**, còn kiểu vế phải không quyết định độ rộng biểu thức.

| Kiểu vế trái | Số bước dịch hiệu dụng | Ví dụ |
| --- | --- | --- |
| `int`, kể cả `byte`/`short`/`char` đã nâng lên `int` | `distance & 31` | `1 << 32 == 1` |
| `long` | `distance & 63` | `1L << 32 == 4294967296L` |

```java
int lowerWidth = 1 << 40;       // 256: 40 & 31 = 8
long higherWidth = 1L << 40;    // 1099511627776L
long assignedAfterShift = 1 << 40; // 256L: phép dịch đã xong ở int
int negativeDistance = 1 << -1;   // Integer.MIN_VALUE: -1 & 31 = 31
```

Một `byte` âm cũng được mở rộng dấu thành `int` trước khi dịch: với `byte b = -8;`, `b >>> 2` cho `1073741822`, trong khi `Byte.toUnsignedInt(b) >>> 2` cho `62`. Khi `index` đến từ input để tạo mặt nạ, phải kiểm tra rõ `0 <= index && index < 32` hoặc `< 64` theo kiểu trước khi dịch; Java không bác bỏ chỉ số ngoài miền.

Bài tiếp theo **Khoảng dịch được rút gọn theo kiểu int hoặc long** đi sâu vào trường hợp số bước âm, bội 32/64, vế phải kiểu `long` và việc bảo vệ phép truy cập bit theo chỉ số.

## 8. Độ ưu tiên toán tử, ngoặc và biểu thức điều kiện

Các toán tử `<<`, `>>`, `>>>` có **cùng độ ưu tiên** và tính kết hợp trái. Chúng có độ ưu tiên **thấp hơn các toán tử cộng/trừ** nhưng **cao hơn các toán tử so sánh quan hệ** như `<`, `>=`.

Ví dụ:

```java
int a = 1 << 2 + 1; // 8: diễn giải thành 1 << (2 + 1)
int b = (1 << 2) + 1; // 5
int c = 32 >> 1 >> 2; // 4: (32 >> 1) >> 2
boolean d = (1 << 3) > 7; // true
```

Khi viết điều kiện và bitmask, nên đặt ngoặc theo ý định để người đọc không cần nhớ bảng độ ưu tiên. Chẳng hạn:

```java
int mask = 1 << 5;
boolean hasFlag = (flags & mask) != 0;
```

Đoạn trên là **mã minh họa** cần một biến `int flags` đã khai báo trước; `&` là AND theo bit và `!=` là so sánh khác. Nó cho biết bit thứ 5 có đang bật trong mẫu bit của `flags` hay không.

Các toán tử dịch chỉ nhận toán hạng **kiểu tích phân** (sau unboxing/thăng cấp phù hợp), không dịch trực tiếp `float`, `double` hoặc `String`. Nếu dùng wrapper như `Integer` mà giá trị là `null`, việc unboxing cho phép dịch có thể ném `NullPointerException`.

## 9. Ứng dụng thực tế: bitmask và tách byte

### 11.1. Tạo bitmask cho cờ trạng thái

Để bật bit thứ `k` của một biến `int`:

```java
int flags = 0;
int readFlag = 1 << 2;   // 0b00000100
int writeFlag = 1 << 5;  // 0b00100000

flags |= readFlag;
flags |= writeFlag;
boolean canRead = (flags & readFlag) != 0;   // true
boolean canWrite = (flags & writeFlag) != 0; // true
```

Đây là các biểu thức cần đặt trong một chương trình Java có `main` hoặc phương thức để chạy. `|` là OR theo bit, `|=` là phép OR gán; toán tử `<<` chỉ giúp đặt bit 1 ở vị trí mong muốn. Với `int`, vị trí bit có ý nghĩa thông thường từ 0 đến 31; với `long` từ 0 đến 63. Hãy giới hạn `k` theo miền này để tránh quy tắc vòng khoảng dịch tạo nhầm mask.

### 11.2. Trích xuất từng byte từ một số 32 bit

Giả sử `int word = 0x1234ABCD`. Để lấy bốn byte theo thứ tự bit từ cao đến thấp, sử dụng `>>>` và mặt nạ `0xFF`:

```java
int word = 0x1234ABCD;
int first  = (word >>> 24) & 0xFF; // 0x12 = 18
int second = (word >>> 16) & 0xFF; // 0x34 = 52
int third  = (word >>>  8) & 0xFF; // 0xAB = 171
int fourth = word & 0xFF;          // 0xCD = 205
```

Mặt nạ `& 0xFF` chỉ giữ **8 bit thấp**. `>>>` thích hợp khi muốn trích xuất một trường bit mà không kéo bit dấu `1` vào vùng cao của kết quả. Trong biểu thức có mặt nạ đủ chặt, đôi khi `>>` vẫn cho cùng 8 bit thấp; nhưng `>>>` truyền đạt rõ ý định dịch logic.

**Endianness** (big-endian/little-endian) là quy ước sắp thứ tự các byte khi lưu/truyền dữ liệu. Các phép dịch ở trên chỉ tách các nhóm 8 bit theo vị trí trong **giá trị `int`**, không tự xác định thứ tự byte trong một file hoặc byte buffer. Khi ghi ra ngoài, cần chỉ rõ thứ tự theo giao thức.

### 11.3. Chương trình chạy được: tách byte

Tạo file `ExtractBytesDemo.java`:

```java
public class ExtractBytesDemo {
    public static void main(String[] args) {
        int word = 0x1234ABCD;
        System.out.printf("%02X%n", (word >>> 24) & 0xFF);
        System.out.printf("%02X%n", (word >>> 16) & 0xFF);
        System.out.printf("%02X%n", (word >>> 8) & 0xFF);
        System.out.printf("%02X%n", word & 0xFF);
    }
}
```

Chạy:

```bash
javac ExtractBytesDemo.java
java ExtractBytesDemo
```

Kết quả:

```text
12
34
AB
CD
```

`%02X` định dạng số dưới dạng hex chữ hoa và chèn số 0 để có ít nhất 2 chữ số khi cần.

## 10. Dịch bit có thể và không thể thay thế phép nhân/chia

Một số quy tắc gần đúng hữu ích:

- Với `x >= 0`, khi không làm mất bit và kết quả còn vừa kiểu: `x << n` có cùng giá trị với `x * 2^n`.
- Với `x >= 0`: `x >> n` và `x >>> n` có cùng giá trị với phép chia nguyên `x / 2^n` trong giới hạn khoảng dịch phù hợp.
- Với `x < 0`: `x >> n` **không luôn** bằng `x / 2^n` vì hướng làm tròn khác nhau.
- Với `x < 0`: `x >>> n` diễn giải bit như không dấu rồi dịch, nên không tương đương phép chia có dấu.

Dùng `Math.multiplyExact` hoặc tính trên kiểu rộng hơn nếu tính đúng và phát hiện overflow là yêu cầu. Đừng tối ưu mã nghiệp vụ bằng việc đổi `*` hay `/` thành toán tử dịch khi chưa kiểm tra hành vi ở số âm, giá trị biên và khoảng dịch lớn. Trình biên dịch/JIT có thể tự thực hiện các tối ưu phù hợp khi được phép.

## 11. Những lỗi phổ biến

| Biểu hiện | Nguyên nhân | Cách sửa/ghi nhớ |
|---|---|---|
| `-8 >>> 2` ra số dương rất lớn | `>>>` chèn `0` vào bit cao của `int` 32 bit | Chỉ dùng khi muốn dịch logic theo mẫu bit |
| `1 << 32` vẫn bằng 1 | Với `int` chỉ dùng 5 bit thấp của khoảng dịch | Dùng `1L << 32` nếu muốn tạo \(2^{32}\) |
| `1L << 64` vẫn bằng 1 | Với `long` chỉ dùng 6 bit thấp | Kiểm tra khoảng dịch trước |
| `byte b = -8; b >>> 2` không cho 62 | `byte` nâng có dấu thành `int` trước dịch | Dùng `Byte.toUnsignedInt(b) >>> 2` |
| `byte b = 1; b = b << 1;` lỗi biên dịch | Dịch cho kết quả `int` | Nhận bằng `int` hoặc ép kiểu có kiểm tra |
| `-7 >> 1` khác `-7 / 2` | Dịch phải giữ dấu làm tròn xuống; chia nguyên làm tròn về 0 | Dùng `/` khi muốn phép chia Java |
| `1 << 31` âm | Đưa 1 vào bit dấu của `int` | Hiểu biểu diễn bù hai và miền kiểu |
| `x << n` không báo lỗi tràn | Bit cao bị loại bỏ âm thầm | Dùng phép toán có kiểm tra nếu cần |
| `int x = 1; long y = x << 40;` cho kết quả lạ | Phép dịch dùng `int` trước khi mở rộng sang `long` | Dùng `((long) x) << 40` |
| Điều kiện bitmask khó đọc | Nhầm độ ưu tiên `&`, `<<` và so sánh | Thêm ngoặc: `(flags & mask) != 0` |

## 12. Tự kiểm tra

**Câu 1.** Với `int x = 16`, `x << 1`, `x >> 2` và `x >>> 2` bằng bao nhiêu?  
**Đáp án:** lần lượt là 32, 4 và 4.

**Câu 2.** Với `int x = -8`, `x >> 2` và `x >>> 2` bằng bao nhiêu?  
**Đáp án:** -2 và 1_073_741_822. Phép đầu điền bit dấu `1`, phép sau điền `0`.

**Câu 3.** Vì sao `1 << 32` trả 1?  
**Đáp án:** toán hạng trái kiểu `int`, nên chỉ 5 bit thấp của khoảng dịch được sử dụng; 32 & 31 bằng 0.

**Câu 4.** Muốn tạo \(2^{32}\) bằng phép dịch, viết thế nào?  
**Đáp án:** `1L << 32`, kết quả `long` là 4_294_967_296.

**Câu 5.** Vì sao `byte b = -8; b >>> 2` không phải phép dịch unsigned 8 bit?  
**Đáp án:** `byte` được nâng lên `int` 32 bit bằng mở rộng dấu rồi mới dịch. Dùng `Byte.toUnsignedInt(b) >>> 2` nếu muốn lấy giá trị 248 rồi dịch thành 62.

**Câu 6.** Với `int n = -7`, `n / 2` và `n >> 1` khác nhau ra sao?  
**Đáp án:** -3 và -4 vì quy tắc làm tròn của chia số nguyên và dịch phải dấu khác nhau với số âm lẻ.

**Câu 7.** Phép `(word >>> 16) & 0xFF` dùng để làm gì?  
**Đáp án:** dịch mẫu bit `word` qua phải 16 vị trí, rồi chỉ giữ 8 bit thấp của kết quả (nhóm bit 16..23 của giá trị gốc).

**Câu 8.** Kết quả `long x = 1 << 40;` có khác `long y = 1L << 40;` không?  
**Đáp án:** có. `x` nhận 256 vì dịch `int` với khoảng hiệu dụng 8; `y` nhận 1_099_511_627_776 vì phép dịch trên `long` với khoảng 40.

**Câu 9.** Dịch trái có luôn là phép nhân \(2^n\) an toàn không?  
**Đáp án:** không. Bit cao có thể bị loại bỏ hoặc chuyển thành bit dấu; Java không ném ngoại lệ vì tràn trong phép dịch.

**Ghi nhớ:** `<<` dịch trái và điền `0`; `>>` dịch phải và **giữ bit dấu**; `>>>` dịch phải và **điền `0`**. Kiểu kết quả phụ thuộc **toán hạng trái sau thăng cấp**. Với `int` chỉ dùng **5 bit thấp** của khoảng dịch (0..31), với `long` chỉ dùng **6 bit thấp** (0..63). Khi thao tác `byte`/`short` âm, đặc biệt với `>>>`, hãy nhớ rằng Java thực hiện phép dịch trên `int` 32 bit sau khi thăng cấp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-003">Khoảng dịch được rút gọn theo kiểu int hoặc long</a>

<details>
<summary>Xem chi tiết</summary>

# Khoảng dịch được rút gọn theo kiểu `int` hoặc `long` trong Java

Khi viết `1 << 32` trong Java, kết quả là **1**, không phải `0` hoặc `4_294_967_296`. Nhưng `1L << 32` lại bằng **4_294_967_296**. Sự khác nhau bắt nguồn từ quy tắc của các toán tử dịch bit: **Java chỉ dùng 5 bit thấp của khoảng dịch nếu vế trái sau khi nâng kiểu là `int`, và 6 bit thấp nếu vế trái là `long`**.

Quy tắc này áp dụng cho cả ba toán tử `<<`, `>>` và `>>>`. Nó giải thích vì sao những khoảng dịch như `32`, `64`, `40`, hoặc thậm chí `-1` vẫn biên dịch và thực thi, đôi khi cho kết quả rất khác trực giác.

## 1. Toán tử dịch bit làm việc với những gì?

Java có ba toán tử dịch bit trên số nguyên:

| Toán tử | Tên | Cách thao tác các bit |
| --- | --- | --- |
| `a << n` | Dịch trái | Đưa bit sang trái, điền `0` vào các vị trí trống bên phải; các bit vượt khỏi độ rộng bị loại bỏ |
| `a >> n` | Dịch phải giữ dấu | Đưa bit sang phải, lấp các vị trí trống bên trái bằng bit dấu ban đầu |
| `a >>> n` | Dịch phải không giữ dấu | Đưa bit sang phải, lấp các vị trí trống bên trái bằng `0` |

Trong biểu thức `a << n`, `a >> n` hoặc `a >>> n`, **`a` là vế trái chứa mẫu bit cần dịch**, còn **`n` là vế phải chỉ số vị trí muốn dịch**. Cả hai phải có kiểu nguyên phù hợp: `byte`, `short`, `char`, `int` hoặc `long` (hoặc các wrapper được unbox tương ứng). Không thể dịch trực tiếp `float`, `double` hoặc `boolean`.

Java áp dụng **unary numeric promotion riêng rẽ cho từng toán hạng**. Vế trái kiểu `byte`, `short`, `char` được nâng thành `int`; nếu vế trái đã là `int` thì kết quả phép dịch là `int`, nếu là `long` thì kết quả là `long`. Kiểu của vế phải không làm thay đổi kiểu của kết quả.

Đối với số âm, các bit đang biểu diễn theo bù hai. `>>` có thể giữ các bit `1` ở phía trái, còn `>>>` đưa các bit `0` vào; nhưng **quy tắc rút gọn khoảng dịch vẫn giống nhau**.

## 2. Quy tắc chính: chỉ giữ 5 hoặc 6 bit thấp của khoảng dịch

Với một vế trái có kiểu sau nâng kiểu là `int` (32 bit):

```text
effectiveDistance = distance & 0x1F
                  = distance & 31
```

Với vế trái `long` (64 bit):

```text
effectiveDistance = distance & 0x3F
                  = distance & 63
```

`&` ở đây là phép AND từng bit. Mặt nạ `0x1F` có 5 bit thấp bằng `1`, còn `0x3F` có 6 bit thấp bằng `1`. Do đó phạm vi dịch thực sự của mỗi phép toán là:

- **`int`: từ 0 đến 31**, bất kể giá trị ban đầu của vế phải là bao nhiêu.
- **`long`: từ 0 đến 63**, bất kể giá trị ban đầu của vế phải là bao nhiêu.

Ví dụ các khoảng dịch được quy về giá trị sau:

| `n` được viết | `int`: `n & 31` | `long`: `n & 63` |
| ---: | ---: | ---: |
| 0 | 0 | 0 |
| 1 | 1 | 1 |
| 31 | 31 | 31 |
| 32 | 0 | 32 |
| 33 | 1 | 33 |
| 40 | 8 | 40 |
| 63 | 31 | 63 |
| 64 | 0 | 0 |
| 65 | 1 | 1 |
| -1 | 31 | 63 |
| -32 | 0 | 32 |
| -64 | 0 | 0 |

Nói cách khác, đối với **khoảng dịch số nguyên** thì kết quả của phép `&` trên tương đương với lấy phần dư không âm theo 32 hoặc 64. Tuy nhiên, cần nhớ **quy tắc chính xác trong Java là mặt nạ bit**, vì cả giá trị âm cũng được diễn giải tự nhiên: `-1 & 31 == 31`; `-1 & 63 == 63`.

**Không có ngoại lệ tự động vì khoảng dịch lớn hơn độ rộng hoặc vì khoảng dịch âm.** Một phép `1 << -1` là hợp lệ và có khoảng dịch thực tế 31, không có nghĩa là dịch ngược 1 bit.

## 3. Phân biệt `1 << 32` và `1L << 32`

Hai biểu thức nhìn rất gần nhau nhưng có vế trái khác kiểu:

```java
int a = 1 << 32;      // Dịch int: 32 & 31 == 0  => 1
long b = 1L << 32;    // Dịch long: 32 & 63 == 32 => 4294967296
```

Đây là hai câu lệnh minh họa có thể đặt trong một phương thức Java. Literal `1` mặc định là `int`; hậu tố `L` tạo literal `long`. Vậy `1 << 32` thực chất giống `1 << 0`, trong khi `1L << 32` đặt bit `1` ở vị trí 32 của một mẫu 64 bit.

So sánh thêm:

| Biểu thức | Khoảng dịch thực | Kết quả |
| --- | ---: | ---: |
| `1 << 0` | 0 (int) | 1 |
| `1 << 31` | 31 (int) | -2_147_483_648 |
| `1 << 32` | 0 (int) | 1 |
| `1 << 33` | 1 (int) | 2 |
| `1 << 40` | 8 (int) | 256 |
| `1 << 64` | 0 (int) | 1 |
| `1L << 31` | 31 (long) | 2_147_483_648 |
| `1L << 32` | 32 (long) | 4_294_967_296 |
| `1L << 40` | 40 (long) | 1_099_511_627_776 |
| `1L << 63` | 63 (long) | `Long.MIN_VALUE` |
| `1L << 64` | 0 (long) | 1 |
| `1L << 65` | 1 (long) | 2 |

`1 << 31` cho giá trị âm vì bit 31 là **bit dấu** của `int`, không phải vì dịch 31 bit không hợp lệ. Tương tự, `1L << 63` bật bit dấu của `long`. Bit vẫn được đặt đúng vị trí; khi in theo thập phân có dấu, Java hiển thị số âm tương ứng.

## 4. Vế trái quyết định mặt nạ, ngay cả khi vế phải là `long`

Xem đoạn mã:

```java
long distance = 40L;
int a = 1 << distance;  // 256: 40 & 31 == 8
long b = 1L << distance; // 1099511627776: 40 & 63 == 40
```

Hai câu lệnh đặt trong phương thức Java. Vế phải `distance` có kiểu `long` trong **cả hai** biểu thức; điều đó **không khiến** biểu thức `1 << distance` trở thành `long`. Kết quả đầu tiên vẫn là `int`, vì vế trái được xác định từ literal `1` có kiểu `int`.

Cần phân biệt:

- `long result = 1 << 40;`: phép dịch hoàn tất dưới dạng `int`, kết quả 256 mới được chuyển sang `long`; nhận **256L**.
- `long result = 1L << 40;`: phép dịch diễn ra bằng `long`; nhận **1_099_511_627_776L**.
- `long result = ((long) 1) << 40;`: ép vế trái thành `long` trước phép dịch, nên giống trường hợp thứ hai.
- `long result = (long) (1 << 40);`: ép **sau** phép dịch, nên vẫn nhận **256L**.

Đây là cùng nguyên tắc “nâng kiểu phải diễn ra trước phép toán” từng gặp trong tính toán số nguyên. Kiểu của biến đích không thể thay đổi quyết định mặt nạ đã áp dụng lên khoảng dịch.

## 5. `byte`, `short` và `char` cũng dùng quy tắc 5 bit

Các toán hạng `byte`, `short`, `char` được Java nâng lên **`int`** trước phép dịch. Chúng không dùng các mặt nạ riêng kiểu `& 7` cho `byte` hoặc `& 15` cho `short`.

```java
byte b = 1;
short s = 1;
char c = 1;

int a = b << 32; // 1: thao tác bằng int, 32 & 31 == 0.
int d = s << 33; // 2: thao tác bằng int, 33 & 31 == 1.
int e = c << 40; // 256: thao tác bằng int, 40 & 31 == 8.
```

Đây là mảnh mã hợp lệ trong một phương thức. Kiểu kết quả của từng phép dịch là `int`. Nếu muốn gán về biến `byte` hoặc `short`, thường cần ép kiểu hẹp có thể mất bit. Phép gán kết hợp như `b <<= n` có quy tắc chuyển đổi về kiểu biến gốc; vẫn không làm khoảng dịch được rút gọn theo độ rộng 8 bit.

Ví dụ: `byte b = -1; int result = b >>> 1;` thực hiện `>>>` trên **`int` -1 đã nâng kiểu**, nên kết quả là `2_147_483_647`; nó không phải phép dịch không dấu của một mẫu chỉ 8 bit có kết quả 127. Nếu yêu cầu xử lý một byte như giá trị không dấu 8 bit, trước tiên dùng `b & 0xFF` để diễn giải byte thành số `int` từ 0 đến 255, rồi tính trên phạm vi ấy.

## 6. Khoảng dịch âm được xử lý thế nào?

Java không hiểu `<< -1` là “dịch phải một bit”. Nó áp dụng mặt nạ bình thường lên số âm được biểu diễn bù hai:

```java
int intShift = 1 << -1;  // Tương đương 1 << 31 => Integer.MIN_VALUE.
long longShift = 1L << -1; // Tương đương 1L << 63 => Long.MIN_VALUE.
```

Các câu lệnh minh họa đặt trong phương thức Java. Vì `-1` có toàn bit thấp bằng `1`, nên `-1 & 31 == 31` và `-1 & 63 == 63`. Ví dụ khác:

```java
int x = 1 << -32; // -32 & 31 == 0  => 1.
long y = 1L << -32; // -32 & 63 == 32 => 4294967296.
```

Đừng dùng khoảng dịch âm để biểu đạt chiều dịch ngược, và đừng dựa vào thao tác mask ngầm để “sửa” giá trị nhập sai. Nếu yêu cầu chỉ cho phép vị trí bit từ 0 đến 31 (hoặc 63), nên kiểm tra giá trị đầu vào rõ ràng rồi mới dịch.

## 7. Ba toán tử `<<`, `>>`, `>>>` cùng áp dụng mặt nạ khoảng dịch

Mặt nạ khoảng dịch không phụ thuộc hướng dịch hay chính sách điền bit. Với `int`, mọi biểu thức `x << 32`, `x >> 32`, `x >>> 32` đều có khoảng dịch thực **0**, vì `32 & 31 == 0`. Do đó:

```java
int x = -1;
int left = x << 32;       // -1: không dịch.
int arithmetic = x >> 32; // -1: không dịch.
int logical = x >>> 32;   // -1: không dịch.
int logicalOne = x >>> 1; // 2147483647: điền 0 phía trái khi dịch 1.
```

Đây là các câu lệnh minh họa. `>>` bảo toàn dấu trong quá trình dịch phải; `>>>` điền số 0. Khi khoảng dịch thực bằng 0, cả hai đều trả lại cùng mẫu bit của `x`, không làm mất dấu.

Đối với `long`, khoảng dịch `32` **không** bị rút về 0:

```java
long x = -1L;
long a = x >> 32;  // -1L: các bit 1 của dấu được điền thêm.
long b = x >>> 32; // 4294967295L: 32 bit cao được điền 0.
long c = x >>> 64; // -1L: vì 64 & 63 == 0.
```

Với `x = -1L`, mẫu ban đầu có 64 bit đều là 1. Dịch không dấu sang phải 32 bit chỉ còn 32 bit thấp là 1, nên `x >>> 32` là một `long` dương có giá trị `4_294_967_295`. Còn `x >>> 64` **không dịch chút nào** vì khoảng dịch thực bằng 0. Đây là trường hợp quan trọng khi xử lý dữ liệu nhị phân hoặc chia một giá trị 64 bit thành các phần.

## 8. Ví dụ Java 17+ đầy đủ, biên dịch và chạy được

Tạo tệp **`ShiftDistanceDemo.java`**:

```java
public class ShiftDistanceDemo {
    public static void main(String[] args) {
        int count = 40;
        long longCount = 40L;
        byte small = 1;

        System.out.println("intCount32=" + (1 << 32));
        System.out.println("intCount33=" + (1 << 33));
        System.out.println("intCount40=" + (1 << count));
        System.out.println("intCount64=" + (1 << 64));
        System.out.println("intCountMinus1=" + (1 << -1));
        System.out.println("longCount32=" + (1L << 32));
        System.out.println("longCount40=" + (1L << count));
        System.out.println("longCount64=" + (1L << 64));
        System.out.println("longCount65=" + (1L << 65));
        System.out.println("longCountMinus1=" + (1L << -1));
        System.out.println("intLeftLongRight=" + (1 << longCount));
        System.out.println("byteLeft=" + (small << 32));
        System.out.println("intEffective40=" + (count & 0x1F));
        System.out.println("longEffective40=" + (count & 0x3F));
        System.out.println("intSignBit=" + (1 << 31));
        System.out.println("longSignBit=" + (1L << 63));
        System.out.println("unsignedIntShift32=" + (-1 >>> 32));
        System.out.println("unsignedIntShift1=" + (-1 >>> 1));
        System.out.println("unsignedLongShift32=" + (-1L >>> 32));
        System.out.println("signedLongShift32=" + (-1L >> 32));
        System.out.println("intShift40Binary=" + Integer.toBinaryString(1 << count));
    }
}
```

Trong thư mục chứa tệp, chạy:

```bash
javac --release 17 -encoding UTF-8 ShiftDistanceDemo.java
java ShiftDistanceDemo
```

Stdout chính xác:

```text
intCount32=1
intCount33=2
intCount40=256
intCount64=1
intCountMinus1=-2147483648
longCount32=4294967296
longCount40=1099511627776
longCount64=1
longCount65=2
longCountMinus1=-9223372036854775808
intLeftLongRight=256
byteLeft=1
intEffective40=8
longEffective40=40
intSignBit=-2147483648
longSignBit=-9223372036854775808
unsignedIntShift32=-1
unsignedIntShift1=2147483647
unsignedLongShift32=4294967295
signedLongShift32=-1
intShift40Binary=100000000
```

Giải thích những dòng dễ nhầm:

- `intCount32=1`, `intCount64=1`: cả 32 và 64 đều cho khoảng dịch thực 0 trên `int`.
- `intCount40=256` so với `longCount40=1099511627776`: cùng số 40 nhưng một bên mask `&31`, bên kia mask `&63`.
- `intCountMinus1=-2147483648` và `longCountMinus1=-9223372036854775808`: `-1` được mask thành 31 hoặc 63 rồi bật đúng bit dấu.
- `intLeftLongRight=256`: vế phải kiểu `long` **không** làm phép dịch của vế trái `int` thành phép dịch 64 bit.
- `byteLeft=1`: `byte` được nâng thành `int`, nên cũng rút gọn khoảng dịch 32 thành 0.
- `unsignedIntShift32=-1`: `>>>` không thay đổi mẫu bit khi khoảng dịch thực bằng 0; `unsignedLongShift32=4294967295` cho thấy `long` lại dịch thực 32 bit.

## 9. Hệ quả khi tạo mặt nạ bit (bit mask)

Trong các chương trình có cờ bật/tắt, có thể cần tạo một mẫu bit chỉ có vị trí `index` được bật:

```java
int intMask = 1 << index;     // Dành cho index 0..31.
long longMask = 1L << index;  // Dành cho index 0..63.
```

Đoạn này là mã minh họa trong phạm vi có biến `index`. Không nên thay `1L` bằng `1` trong biểu thức thứ hai, vì `long longMask = 1 << 40;` cho **256L**, chỉ bật bit 8 chứ không phải bit 40.

Khi `index` đến từ input hoặc một vòng lặp, **phải chủ động kiểm tra phạm vi** để tránh mask ngầm. Ví dụ hàm truy cập bit của `long`:

```java
static boolean isBitSet(long flags, int index) {
    if (index < 0 || index >= Long.SIZE) {
        throw new IllegalArgumentException("bit index must be 0..63");
    }
    long mask = 1L << index;
    return (flags & mask) != 0L;
}
```

Đây là **phương thức** cần đặt bên trong một lớp Java. `Long.SIZE` bằng 64. Hàm kiểm tra rõ `index` từ 0 đến 63, sau đó tạo mask bằng `long`, nên kể cả vị trí 63 (bit dấu) cũng kiểm tra được đúng bằng AND bit. Nếu thiếu kiểm tra, `index = 64` sẽ bị rút về 0 và vô tình kiểm tra bit 0; `index = -1` bị rút về 63. Cả hai có thể khiến logic quyền truy cập, cờ tính năng hoặc dữ liệu nhị phân sai.

Với bit mask 32 bit, dùng `Integer.SIZE` (32) để kiểm tra `0 <= index && index < 32`. Việc mặt nạ `1 << 31` có giá trị `int` âm **không khiến nó sai**: bitwise AND và OR vẫn thao tác đúng trên mẫu bit.

## 10. Dịch trái lớn không tự báo overflow

Trong `<<`, những bit rời khỏi độ rộng của vế trái bị loại bỏ. Ngoài việc khoảng dịch được mask, còn có khả năng **mất thông tin do dịch trái**, kể cả khi khoảng dịch thực nằm trong phạm vi hợp lệ.

```java
int highBit = 1 << 31;        // Integer.MIN_VALUE.
int lostBit = highBit << 1;   // 0: bit cao nhất bị đẩy ra khỏi 32 bit.
long retained = ((long) highBit) << 1; // -4294967296L: mở rộng dấu trước dịch.
long positive = 1L << 32;     // 4294967296L.
```

Mã trên là các khai báo minh họa. `highBit` vốn đã là số `int` âm, nên mở rộng dấu `highBit` sang `long` rồi dịch sẽ cho số âm; không khôi phục số dương `2^31`. Muốn xây dựng `2^32`, cần bắt đầu từ `1L << 32`.

Dịch trái không ném `ArithmeticException` khi mất bit hoặc khi bit dấu thay đổi. Đừng dùng `x << n` như phép nhân `2^n` vô điều kiện: kết quả chỉ tương ứng với phép nhân toán học khi giá trị cần thiết vẫn biểu diễn chính xác trong kiểu kết quả và không bị tác động bởi bit bị loại bỏ.

## 11. Tình huống thường gặp trong code và cách sửa

| Cách viết | Điều dễ gây sai | Cách hiểu/sửa |
| --- | --- | --- |
| `1 << 32` | Chờ một `int` biểu diễn `2^32` | Khoảng dịch thực 0, kết quả 1; dùng `1L << 32` nếu muốn `2^32` |
| `1L << 64` | Nghĩ phép dịch ra 0 | `64 & 63 == 0`; kết quả 1 |
| `1 << -1` | Nghĩ dịch ngược hoặc ném lỗi | `-1 & 31 == 31`; kết quả `Integer.MIN_VALUE` |
| `long x = 1 << 40;` | Tưởng kiểu biến `long` làm vế trái rộng 64 bit | Biểu thức `int` cho 256 trước khi gán; dùng `1L << 40` |
| `1 << 40L` | Tưởng vế phải `long` khiến toàn biểu thức `long` | Vế trái `int` nên mask `& 31`, kết quả 256 |
| `byte b = 1; b << 32` | Tưởng `byte` dùng khoảng dịch `& 7` | `byte` được nâng kiểu `int`, mask `& 31` |
| `-1 >>> 32` | Tưởng chắc chắn đổi thành số không âm | Dịch thực 0, vẫn `int` -1 |
| `-1L >>> 32` | Tưởng kết quả giống biểu thức `int` | `long` dịch 32 bit, trả `4_294_967_295L` |
| `1L << index` với `index = 64` | Tưởng index sai sẽ bị báo lỗi | Khoảng dịch thực 0; phải kiểm tra index bằng điều kiện rõ ràng |

## 12. Câu hỏi tự kiểm tra

1. **`1 << 32` bằng bao nhiêu?** **1**, vì vế trái là `int` và `32 & 31 == 0`.
2. **`1L << 32` bằng bao nhiêu?** **4_294_967_296**, vì vế trái là `long`, còn 32 là khoảng dịch hợp lệ thực sự.
3. **`1 << 40` bằng bao nhiêu?** **256**, vì `40 & 31 == 8`, tức dịch trái 8 bit.
4. **`1L << 40` bằng bao nhiêu?** **1_099_511_627_776**, do `40 & 63 == 40`.
5. **`1 << 40L` trả kiểu `int` hay `long`?** **`int`**, vì kiểu kết quả phụ thuộc vế trái, không phụ thuộc kiểu `long` của khoảng dịch.
6. **`1L << -1` cho gì?** **`Long.MIN_VALUE`**, vì `-1 & 63 == 63`.
7. **`-1 >>> 32` có khác `-1` không?** **Không**, vì khoảng dịch thực bằng 0. Nhưng `-1L >>> 32` cho **`4_294_967_295L`**.
8. **Dịch `byte` có rút gọn khoảng dịch theo 8 bit không?** **Không**: trước phép dịch, `byte` được nâng thành `int`; quy tắc vẫn là `& 31`.
9. **Tại sao phải kiểm tra `index` khi tạo bit mask?** Vì các chỉ số âm hoặc vượt độ rộng sẽ bị mask ngầm, có thể kiểm tra/bật sai một bit khác mà không báo lỗi.

**Ghi nhớ:** với `<<`, `>>`, `>>>`, hãy **nhìn kiểu vế trái sau nâng kiểu** để biết khoảng dịch thực: **`int` dùng `distance & 31`; `long` dùng `distance & 63`**. Vế phải có kiểu `long` không làm kết quả rộng hơn; các khoảng dịch lớn hoặc âm được mask chứ không bị từ chối. Khi xử lý bit mask hoặc đầu vào có chỉ số bit, hãy xác định độ rộng mong muốn và kiểm tra giới hạn chỉ số một cách tường minh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-004">Mở rộng dấu khi nâng kiểu byte hoặc short</a>

<details>
<summary>Xem chi tiết</summary>

# Mở rộng dấu khi nâng kiểu `byte` hoặc `short` trong Java

Trong Java, `byte` và `short` là **số nguyên có dấu** (*signed integers*). Khi chuyển từ `byte`/`short` sang một kiểu nguyên rộng hơn như `int` hoặc `long`, Java bảo toàn **giá trị số học**. Đối với số âm trong biểu diễn bù hai, điều đó đòi hỏi **mở rộng dấu** (*sign extension*): điền các bit `1` vào phần bit mới ở bên trái. Đối với giá trị không âm, các bit mới là `0`.

Hành vi này rất quan trọng khi xử lý byte nhị phân, mã màu, dữ liệu mạng, file và bitmask. Nếu muốn hiểu một mẫu 8/16 bit theo nghĩa **số không dấu**, cần diễn giải rõ ràng, chẳng hạn `b & 0xFF`, `s & 0xFFFF`, `Byte.toUnsignedInt(b)` hoặc `Short.toUnsignedInt(s)`.

## 1. Kiểu `byte` và `short` biểu diễn những gì?

| Kiểu Java | Số bit | Miền giá trị có dấu | Bit dấu |
| --- | ---: | --- | --- |
| `byte` | 8 | -128 đến 127 | Bit thứ 7 |
| `short` | 16 | -32768 đến 32767 | Bit thứ 15 |
| `int` | 32 | -2147483648 đến 2147483647 | Bit thứ 31 |
| `long` | 64 | -9223372036854775808 đến 9223372036854775807 | Bit thứ 63 |
| `char` | 16 | 0 đến 65535 | Không có bit dấu |

Java sử dụng biểu diễn **bù hai** (*two's complement*) cho kiểu nguyên có dấu. Chẳng hạn một byte gồm tám bit `1111_1111` được hiểu là `-1` nếu nhìn như `byte`, nhưng cũng có thể được hiểu là `255` nếu áp dụng quy ước *unsigned 8-bit*.

Trong Java **không có primitive `unsigned byte` hoặc `unsigned short`**. Dữ liệu thô thường được chứa trong `byte`/`short`; người lập trình cần chủ động chọn cách diễn giải có dấu hay không dấu khi tính toán.

### Minh họa hai cách diễn giải cùng một byte

```text
Bit gốc:            1111 1111
Đọc như byte signed:        -1
Đọc như unsigned 8-bit:    255

Bit gốc:            1000 0000
Đọc như byte signed:      -128
Đọc như unsigned 8-bit:    128
```

Hai cách nhìn không làm thay đổi tám bit gốc; chỉ thay đổi **ý nghĩa số học** của chúng. Ngay trong mã Java, literal `0xFF` có kiểu `int` và giá trị `255`; để giữ tám bit đó vào một `byte`, cần ép kiểu:

```java
byte raw = (byte) 0xFF; // bit 11111111, giá trị byte = -1
```

## 2. Cơ chế sign extension khi nâng lên `int` và `long`

Khi widening một giá trị số nguyên có dấu từ `byte` thành `short`, `int` hoặc `long`, Java **giữ nguyên giá trị có dấu**. Theo biểu diễn nhị phân bù hai, các bit được bổ sung ở bên trái bằng bit dấu của giá trị nguồn:

- Nếu bit dấu của giá trị nguồn là `0` (giá trị không âm), thêm các bit `0`.
- Nếu bit dấu là `1` (giá trị âm), thêm các bit `1`.

Ví dụ `byte b = -128` có bit gốc `1000_0000`. Khi nâng lên `int`:

```text
byte -128 (8 bit):
                            1000 0000
int -128  (32 bit):
1111 1111 1111 1111 1111 1111 1000 0000
          24 bit mới = toàn các bit 1
```

Kết quả được diễn giải thành `-128` trong `int`, **không thành +128**. Việc mở rộng dấu giữ nguyên giá trị âm thay vì coi bit dấu cũ là bit dữ liệu thông thường.

Với `byte b = 5`, bit gốc là `0000_0101`; khi nâng lên `int`, 24 bit thêm vào đều bằng `0` và kết quả vẫn là `5`.

### Bảng kết quả chuẩn

| Biểu thức | Giá trị số thập phân | Mẫu 32 bit dưới dạng hex |
| --- | ---: | --- |
| `(int) (byte) 0x7F` | 127 | `0x0000007F` |
| `(int) (byte) 0x80` | -128 | `0xFFFFFF80` |
| `(int) (byte) 0xFF` | -1 | `0xFFFFFFFF` |
| `(int) (short) 0x7FFF` | 32767 | `0x00007FFF` |
| `(int) (short) 0x8000` | -32768 | `0xFFFF8000` |
| `(int) (short) 0xFFFF` | -1 | `0xFFFFFFFF` |

Các cast từ literal `int` sang `byte`/`short` ở đây **thu hẹp số bit trước**, rồi cast `int` phía ngoài thực hiện sign extension. Không nên đọc chuỗi cast như một phép “bỏ qua dấu”.

## 3. Sign extension khác với zero extension

**Zero extension** là điền các bit `0` vào phần bên trái khi mở rộng. Với `byte`/`short` âm, chuyển kiểu nguyên thông thường sẽ thực hiện **sign extension**. Nếu dữ liệu thực sự là một số không dấu 8/16 bit, phải loại bỏ phần mở rộng dấu để lấy mẫu bit dưới dạng số dương.

Với `byte b = (byte) 0xFF`:

```text
b dưới dạng byte:  1111 1111             (signed -1)

Nâng kiểu bình thường:
int b:              FFFFFFFF             (signed -1)

Giữ tám bit thấp, điền 0 vào các bit còn lại:
b & 0xFF:          000000FF             (unsigned 255)
```

Dùng toán tử AND:

```java
byte b = (byte) 0xFF;
int signed = b;             // -1: mở rộng dấu
int unsigned = b & 0xFF;    // 255: giữ tám bit thấp

short s = (short) 0xFFFF;
int signedShort = s;        // -1
int unsignedShort = s & 0xFFFF; // 65535
```

Điều cốt lõi là `& 0xFF` **không biến kiểu `byte` thành unsigned**; nó tạo một biểu thức kiểu `int` có kết quả trong miền `0..255`. Với `short` và `& 0xFFFF`, kết quả nằm trong `0..65535`.

### Tại sao mặt nạ hoạt động?

Trong biểu thức `b & 0xFF`, `b` được nâng kiểu lên `int` **trước** khi phép AND xảy ra. Nếu `b == -1`, bit sau nâng kiểu là `0xFFFFFFFF`. Mặt nạ `0x000000FF` giữ tám bit thấp và xóa 24 bit cao:

```text
0xFFFFFFFF   // b = -1 sau sign extension
0x000000FF   // mặt nạ
----------
0x000000FF   // 255
```

Với `short`, mặt nạ `0x0000FFFF` giữ 16 bit thấp và xóa 16 bit cao. Mặt nạ cần phù hợp **chiều rộng của dữ liệu gốc**: `0xFF` cho byte, `0xFFFF` cho short.

## 4. API `Byte.toUnsignedInt` và `Short.toUnsignedInt`

Java có sẵn các phương thức biểu diễn giá trị có dấu thành giá trị không dấu tương ứng:

| Phương thức | Giá trị có dấu đầu vào | Giá trị `int` trả về |
| --- | --- | --- |
| `Byte.toUnsignedInt((byte) -1)` | -1 | 255 |
| `Byte.toUnsignedInt((byte) -128)` | -128 | 128 |
| `Byte.toUnsignedInt((byte) 127)` | 127 | 127 |
| `Short.toUnsignedInt((short) -1)` | -1 | 65535 |
| `Short.toUnsignedInt((short) -32768)` | -32768 | 32768 |

`Byte.toUnsignedInt(b)` tương đương về kết quả với `b & 0xFF`; `Short.toUnsignedInt(s)` tương đương `s & 0xFFFF`. Các method này có ý nghĩa rõ ràng khi người đọc gặp mã xử lý dữ liệu nhị phân.

```java
byte byteData = (byte) 0x80;
short shortData = (short) 0x8000;

int unsignedByte = Byte.toUnsignedInt(byteData);     // 128
int unsignedShort = Short.toUnsignedInt(shortData);  // 32768
```

Chú ý: `Byte.toUnsignedInt` nhận **`byte`**, không nhận mọi số nguyên. `Short.toUnsignedInt` nhận **`short`**. Muốn xem tám bit thấp của một giá trị `int`, biểu thức `value & 0xFF` thường trực tiếp và rõ ý hơn việc ép tạm sang `byte` rồi gọi method.

Đừng nhầm `Integer.toUnsignedLong(int)` với API trên: nó diễn giải **toàn bộ 32 bit** của một `int` theo miền `0..4294967295` và trả `long`; không thay thế cho yêu cầu chỉ lấy 8 hoặc 16 bit thấp của dữ liệu.

## 5. `char` được mở rộng bằng bit 0: trường hợp đối chiếu

`char` có 16 bit **không dấu**. Khi `char` được nâng lên `int`, Java điền các bit `0` ở phần cao và giữ giá trị trong khoảng `0..65535`. Ngược lại, `short` là kiểu **có dấu** nên hai giá trị có cùng 16 bit có thể cho kết quả `int` khác nhau.

```java
short signed = (short) 0xFFFF;  // -1
char unsigned = (char) 0xFFFF; // 65535

int fromShort = signed;   // -1, mẫu int là FFFFFFFF
int fromChar = unsigned;  // 65535, mẫu int là 0000FFFF
```

`char` trong Java đại diện một **UTF-16 code unit**; ví dụ trên chỉ dùng khả năng biểu diễn không dấu của nó để đối chiếu cách mở rộng bit. Không nên dùng `char` như một cách thay thế tổng quát cho số nguyên không dấu trong mọi API.

### Ép `byte` âm sang `char`: chi tiết cần cẩn thận

`byte` không thể tự động mở rộng sang `char` vì miền giá trị của hai kiểu không bao chứa nhau theo quy tắc widening primitive conversion. Ép tường minh có thể tạo kết quả bất ngờ:

```java
byte minusOne = -1;
// char invalid = minusOne; // lỗi biên dịch: cần cast

char castDirectly = (char) minusOne;      // 65535 (0xFFFF)
char lowEightBits = (char) (minusOne & 0xFF); // 255 (0x00FF)
```

Khi cast trực tiếp `byte` âm sang `char`, việc chuyển từ giá trị có dấu sang miền 16 bit của `char` cho `0xFFFF` trong trường hợp `-1`. Nếu mục tiêu là chuyển **một byte không dấu** thành code unit mang giá trị `0..255`, hãy che bằng `& 0xFF` trước.

## 6. Quy tắc unary/binary numeric promotion và phép toán bit

Không chỉ phép gán như `int x = b;` mới gây sign extension. Java thực hiện **nâng kiểu số học** (*numeric promotion*) với nhiều toán tử:

- `b + 1`, `b - 1`, `b * 2`: `byte` được nâng lên `int` trước khi tính.
- `~b`: toán tử bù bit làm việc trên giá trị `int` đã nâng kiểu.
- `b << 1`, `b >> 1` và `b >>> 1`: toán hạng trái `byte`/`short` được nâng lên `int` trước khi dịch bit.
- `b & mask`, `b | mask`, `b ^ mask`: thao tác bit trên kết quả sau binary numeric promotion.

Ví dụ `byte b = -1`:

```java
byte b = -1;
int promoted = b;     // -1, bit FFFFFFFF
int complement = ~b;  // 0, vì ~FFFFFFFF = 00000000
int shifted = b >> 1; // -1, dịch phải số học giữ bit dấu
```

Nếu người học chỉ hình dung `b` là tám bit `11111111`, có thể dự đoán sai kết quả của phép dịch hay của `~b`; hầu hết các biểu thức trên **đã làm việc với int 32 bit**.

### `>>` và `>>>` khi nguồn là byte âm

`>>` dịch phải có bảo toàn bit dấu của kiểu đã nâng; `>>>` dịch phải logic và điền `0`. Tuy nhiên **`>>>` không tự giới hạn phép dịch trong tám bit ban đầu**:

```java
byte b = -1;
int arithmetic = b >> 1;          // -1
int logicalOnInt = b >>> 1;       // 2147483647 (0x7FFFFFFF)
int logicalOnByte = (b & 0xFF) >>> 1; // 127 (0x7F)
```

Tại sao `b >>> 1` không ra 127? `b` đã trở thành `0xFFFFFFFF` kiểu `int`, nên phép dịch logic 32 bit ra `0x7FFFFFFF`. Để dịch **giá trị 8 bit không dấu**, áp dụng `& 0xFF` **trước phép dịch**.

Quy tắc tương tự với `short`: `(s & 0xFFFF) >>> 1` dịch giá trị không dấu 16 bit đúng mục đích.

## 7. Kết hợp nhiều byte thành một số nguyên không dấu

Khi ghép hai byte từ file hay giao thức mạng thành một số 16 bit **không dấu**, cần giữ đúng tám bit thấp của **từng byte** trước khi ghép.

Giả sử byte cao có bit `1000_0000` (`0x80`) và byte thấp là `1111_1111` (`0xFF`). Giá trị không dấu 16 bit mong muốn là `0x80FF` = `33023`.

```java
byte high = (byte) 0x80;  // -128
byte low = (byte) 0xFF;   // -1

int combined = ((high & 0xFF) << 8) | (low & 0xFF);
// combined = 0x80FF = 33023
```

Từng bước:

1. `high & 0xFF` biến mẫu `0x80` thành `128` kiểu `int`.
2. Dịch trái tám bit: `128 << 8` thành `0x8000` (32768).
3. `low & 0xFF` biến mẫu `0xFF` thành `255`.
4. Phép OR `0x8000 | 0x00FF` thu được `0x80FF` (33023).

**Lỗi phổ biến:** viết `(high << 8) | low` mà không mask sẽ khiến `low = -1` mở rộng thành `0xFFFFFFFF`, kéo toàn bộ kết quả OR thành `-1`, sai giá trị mong muốn.

Với dữ liệu giao thức, quy ước **byte order** (*big endian/little endian*) vẫn phải được biết từ đặc tả. Ví dụ trên giả định `high` là byte cao; sign extension chỉ là một phần của việc giải mã nhị phân đúng.

## 8. Ví dụ Java 17+ hoàn chỉnh và output

Tạo file **`SignExtensionDemo.java`**:

```java
public class SignExtensionDemo {
    public static void main(String[] args) {
        byte minusOne = (byte) 0xFF;
        byte minus128 = (byte) 0x80;
        short shortMinusOne = (short) 0xFFFF;
        short shortMin = (short) 0x8000;

        System.out.println("byteSigned=" + (int) minusOne);
        System.out.printf("byteBitsInt=%08X%n", (int) minusOne);
        System.out.println("byteUnsigned=" + (minusOne & 0xFF));
        System.out.println("byteUnsignedAPI=" + Byte.toUnsignedInt(minusOne));
        System.out.println("byte128Signed=" + (int) minus128);
        System.out.println("byte128Unsigned=" + Byte.toUnsignedInt(minus128));

        System.out.println("shortSigned=" + (int) shortMinusOne);
        System.out.printf("shortBitsInt=%08X%n", (int) shortMin);
        System.out.println("shortUnsigned=" + (shortMinusOne & 0xFFFF));
        System.out.println("shortUnsignedAPI=" + Short.toUnsignedInt(shortMin));

        char chars = (char) 0xFFFF;
        System.out.printf("charBitsInt=%08X%n", (int) chars);
        System.out.println("castByteToChar=" + (int) (char) minusOne);
        System.out.println("maskedByteToChar=" + (int) (char) (minusOne & 0xFF));

        System.out.println("arithmeticShift=" + (minusOne >> 1));
        System.out.println("logicalShiftInt=" + (minusOne >>> 1));
        System.out.println("logicalShiftByte=" + ((minusOne & 0xFF) >>> 1));
        System.out.println("bitwiseComplement=" + (~minusOne));

        byte high = (byte) 0x80;
        byte low = (byte) 0xFF;
        int combined = ((high & 0xFF) << 8) | (low & 0xFF);
        int wrongCombined = (high << 8) | low;
        System.out.println("combinedUnsigned16=" + combined);
        System.out.println("combinedWithoutMask=" + wrongCombined);

        System.out.println("signedComparison=" + (minusOne == 0xFF));
        System.out.println("unsignedComparison=" + ((minusOne & 0xFF) == 0xFF));
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac SignExtensionDemo.java
java SignExtensionDemo
```

**Output đúng:**

```text
byteSigned=-1
byteBitsInt=FFFFFFFF
byteUnsigned=255
byteUnsignedAPI=255
byte128Signed=-128
byte128Unsigned=128
shortSigned=-1
shortBitsInt=FFFF8000
shortUnsigned=65535
shortUnsignedAPI=32768
charBitsInt=0000FFFF
castByteToChar=65535
maskedByteToChar=255
arithmeticShift=-1
logicalShiftInt=2147483647
logicalShiftByte=127
bitwiseComplement=0
combinedUnsigned16=33023
combinedWithoutMask=-1
signedComparison=false
unsignedComparison=true
```

Khi chạy, `printf("%08X")` in tám chữ số hex của biểu diễn `int`. Vì thế `FFFFFFFF` chứng tỏ dấu âm của byte `-1` đã mở rộng lên 32 bit; `0000FFFF` của `char` cho thấy 16 bit cao được điền bằng `0`.

## 9. Những nhầm lẫn thường gặp

| Hiểu nhầm | Quy tắc đúng |
| --- | --- |
| “`byte b = (byte) 0xFF` thì `int x = b` cho 255.” | `byte` có dấu: `b == -1`; khi nâng lên `int` vẫn `-1` vì sign extension. |
| “Cast `(int) b` loại bỏ phần dấu.” | Cast widening giữ nguyên giá trị signed và mở rộng bit dấu. |
| “`b & 0xFF` làm `b` trở thành unsigned.” | `b` không đổi; biểu thức tạo **`int`** dương từ tám bit thấp. |
| “`Short.toUnsignedInt(s)` mở rộng bằng dấu.” | API diễn giải `short` theo **16 bit không dấu** và trả `int` trong 0..65535. |
| “`byte` và `char` đều 8 bit.” | `byte` 8 bit signed; `char` 16 bit unsigned UTF-16 code unit. |
| “`(char) (byte) 0xFF` có giá trị 255.” | Cast trực tiếp byte `-1` sang char tạo `65535`; cần mask trước nếu muốn 255. |
| “`b >>> 1` dịch đúng tám bit của byte.” | `b` được nâng thành `int` 32 bit trước; để dịch unsigned 8 bit, mask trước. |
| “Ghép hai byte bằng `(high << 8) | low` luôn đúng.” | `low` âm mở rộng bit dấu; mask từng byte để không làm bẩn các bit cao. |
| “`(byte) 0xFF` là cách viết một kiểu unsigned byte mới.” | Cast về `byte` chỉ giữ tám bit thấp và diễn giải signed: kết quả `-1`. |
| “Sign extension làm giá trị âm đổi thành số dương.” | Sign extension **bảo toàn giá trị âm** khi chuyển sang kiểu nguyên rộng hơn. |

## 10. Quy trình kiểm tra mã xử lý bit

1. **Xác định kiểu nguồn:** `byte` 8 bit signed, `short` 16 bit signed, `char` 16 bit unsigned. Kiểu nguồn quyết định việc thêm các bit dấu hay bit `0`.
2. **Xác định ý nghĩa dữ liệu:** dữ liệu thật sự là số signed hay chỉ là 8/16 bit thô cần hiểu theo unsigned?
3. **Kiểm tra điểm nâng kiểu:** `int i = b`, `b + 1`, `b >> 1`, `b & mask` đều có thể làm việc với `int` đã sign-extend.
4. **Chuyển sang diễn giải unsigned trước khi tính nếu cần:** byte dùng `b & 0xFF` hoặc `Byte.toUnsignedInt(b)`; short dùng `s & 0xFFFF` hoặc `Short.toUnsignedInt(s)`.
5. **Khi dịch/ghép byte:** mask mỗi thành phần trước khi shift/OR; chú ý thứ tự byte do định dạng dữ liệu quy định.
6. **Kiểm tra bằng hex có độ rộng cố định:** `%08X` cho `int`, so sánh mẫu bit với kết quả số học; phân biệt giá trị signed và mẫu bit nguyên thủy.

**Ghi nhớ:** nâng `byte`/`short` âm lên `int`/`long` thực hiện **sign extension**, giữ giá trị âm bằng cách bổ sung bit `1`. Khi muốn đọc bit gốc như số **không dấu**, dùng **mặt nạ** hoặc **API unsigned** trước những phép toán mà độ rộng và dấu có thể ảnh hưởng kết quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-005">Mặt nạ bit và chuyển byte sang giá trị 0 đến 255</a>

<details>
<summary>Xem chi tiết</summary>

# Mặt nạ bit và chuyển `byte` sang giá trị 0 đến 255 trong Java

## 1. Vấn đề: cùng tám bit nhưng có hai cách hiểu

Trong Java, kiểu nguyên thủy `byte` chứa **8 bit có dấu** và có miền giá trị từ `-128` đến `127`. Tuy nhiên, khi xử lý file nhị phân, màu sắc RGB, dữ liệu mạng, ảnh hoặc giao thức, một byte thường được hiểu như **8 bit không dấu**, tức giá trị từ `0` đến `255`. Cùng một mẫu bit có thể có hai giá trị tùy cách diễn giải.

| Mẫu 8 bit | Hiểu dưới dạng `byte` Java có dấu | Hiểu không dấu |
| --- | ---: | ---: |
| `0000_0000` | 0 | 0 |
| `0000_0001` | 1 | 1 |
| `0111_1111` | 127 | 127 |
| `1000_0000` | -128 | 128 |
| `1010_0101` | -91 | 165 |
| `1111_1111` | -1 | 255 |

Java **không có kiểu nguyên thủy `unsigned byte`**. Khi cần giá trị không dấu từ 0 đến 255, ta lưu mẫu bit ban đầu trong `byte`, rồi chuyển sang một kiểu có miền rộng hơn, thường là `int`, bằng **mặt nạ bit**:

```java
byte value = (byte) 0xA5; // 0xA5 = 165, ngoài miền byte có dấu.
int unsigned = value & 0xFF;

System.out.println(value);    // -91
System.out.println(unsigned); // 165
```

`value & 0xFF` là cách viết phổ biến để lấy **8 bit thấp** của một giá trị nguyên dưới dạng số không dấu. Java 8+ còn cung cấp `Byte.toUnsignedInt(value)` với mục đích tương đương, rõ nghĩa hơn ở nơi cần chuyển `byte` sang `int` không dấu.

## 2. Mặt nạ bit (*bit mask*) là gì?

*Mặt nạ bit* là một giá trị có các bit được chọn là `1`, các bit không cần giữ là `0`. Khi kết hợp với toán tử AND theo bit `&`:

- Bit ở vị trí mặt nạ là `1`: giữ nguyên bit tương ứng của toán hạng.
- Bit ở vị trí mặt nạ là `0`: kết quả tại vị trí đó thành `0`.

Bảng chân trị của phép AND theo bit:

| Bit A | Bit mask | `A & mask` |
| ---: | ---: | ---: |
| 0 | 0 | 0 |
| 0 | 1 | 0 |
| 1 | 0 | 0 |
| 1 | 1 | 1 |

`0xFF` là số nguyên thập lục phân có giá trị `255`. Ở kiểu `int` 32 bit, nó có dạng:

```text
0x000000FF
00000000 00000000 00000000 11111111
                                ^^^^^^^^
                          giữ tám bit thấp
```

Khi tính `n & 0xFF`, tám bit thấp của `n` được giữ lại; **24 bit cao của kết quả `int` đều bằng 0**. Vì vậy, kết quả luôn nằm trong khoảng `0..255`, bất kể `n` ban đầu dương hay âm.

Ví dụ với một `int` bình thường:

```java
int n = 0x1234;
int lowByte = n & 0xFF; // 0x34 = 52.
```

Nó chỉ giữ tám bit thấp, **không phải kiểm tra xem `n` có thuộc khoảng 0..255 hay không**. Nếu giá trị ban đầu nằm ngoài khoảng đó, dữ liệu bit cao bị bỏ đi. Ta sẽ phân biệt “trích xuất bit” và “kiểm tra miền” ở phần sau.

## 3. Vì sao `byte` âm khi chuyển thẳng sang `int`?

Khi mở rộng một `byte` có dấu sang `int`, Java **giữ nguyên giá trị số có dấu**. Về mặt bit, cách biểu diễn có thể hiểu bằng *sign extension* (mở rộng bit dấu):

```java
byte b = (byte) 0xA5;
int signed = b;
System.out.println(signed); // -91
```

`0xA5` có tám bit `1010_0101`. Bit cao nhất của `byte` là `1`, nên `byte` được hiểu là `-91`. Khi mở rộng sang `int`, phần bit cao được điền bằng `1` để giữ lại giá trị âm:

```text
byte (8 bit):             10100101                  = -91
int sau sign extension:   11111111 11111111 11111111 10100101
                         = 0xFFFFFFA5              = -91

AND 0x000000FF:          00000000 00000000 00000000 10100101
                         = 0x000000A5              = 165
```

Vì sao `& 0xFF` hiệu quả?

1. `byte` được nâng lên `int` khi tham gia phép AND với literal `0xFF` (kiểu `int`).
2. Nếu `byte` âm, quá trình nâng kiểu giữ bit dấu, tạo ra các bit `1` ở phần cao của `int`.
3. AND với `0x000000FF` xóa sạch 24 bit cao; giữ nguyên tám bit dữ liệu gốc.
4. Kết quả là **`int` không âm từ 0 đến 255**, đúng với cách hiểu 8 bit không dấu.

Như vậy `& 0xFF` **không thay đổi bit đang có trong biến `byte`**; nó tạo ra một kết quả `int` mới. Một `byte` có giá trị `-1` vẫn là `-1` sau khi gọi `Byte.toUnsignedInt` hoặc tính mặt nạ; chỉ cách diễn giải tám bit thành `int` mới thay đổi.

## 4. Hai cách chuẩn để chuyển `byte` thành `int` 0..255

### 4.1. Dùng mặt nạ `& 0xFF`

```java
byte b = (byte) 0xFF;
int unsigned = b & 0xFF;
System.out.println(b);        // -1
System.out.println(unsigned); // 255
```

Ưu điểm: ngắn, dễ kết hợp khi phân tích bit, dịch bit và ghép các byte. Nhược điểm: người đọc cần hiểu `0xFF` giữ tám bit thấp.

### 4.2. Dùng `Byte.toUnsignedInt`

```java
byte b = (byte) 0x80;
int unsigned = Byte.toUnsignedInt(b);
System.out.println(b);        // -128
System.out.println(unsigned); // 128
```

`Byte.toUnsignedInt(byte x)` là phương thức tĩnh từ Java 8, trả về `int` chứa giá trị **không dấu của tám bit**. Với mọi giá trị `byte`, nó cho cùng kết quả như `b & 0xFF`.

| Dữ liệu đầu vào `byte` | `b & 0xFF` | `Byte.toUnsignedInt(b)` |
| ---: | ---: | ---: |
| -128 | 128 | 128 |
| -91 | 165 | 165 |
| -1 | 255 | 255 |
| 0 | 0 | 0 |
| 1 | 1 | 1 |
| 127 | 127 | 127 |

**Nên chọn gì?** Dùng `Byte.toUnsignedInt(b)` khi muốn diễn đạt rõ “đọc byte như một số không dấu”. Dùng `b & 0xFF` khi đang thao tác trực tiếp với mặt nạ, trường bit hoặc ghép các byte trong một biểu thức bitwise. Cả hai đều đúng và đều cho ra kiểu `int`.

## 5. Dấu `&` là phép toán bit, khác `&&`

Với hai toán hạng số nguyên, `&` áp dụng AND cho từng bit. Với toán hạng boolean, `&` cũng có dạng AND logic **không đoản mạch**. Trong khi đó `&&` chỉ dùng cho điều kiện logic, có đoản mạch và không thể dùng để tạo mặt nạ số nguyên.

```java
byte b = (byte) 0xF0;
int unsigned = b & 0xFF; // 240.

// int error = b && 0xFF; // Không biên dịch: && yêu cầu boolean.
```

Mặt nạ không phải phép so sánh kiểu `b >= 0` hay phép lấy giá trị tuyệt đối. Với `b = -1`, `Math.abs(b)` cho `1` khi giá trị được nâng lên `int`; nhưng `b & 0xFF` cho `255`. Hai bài toán hoàn toàn khác nhau.

## 6. Dùng mặt nạ để lấy nibble và ghép nhiều byte

Một *nibble* là bốn bit. Từ một `byte`, có thể trích xuất nibble thấp và nibble cao để xử lý mã màu, header nhị phân hoặc giao thức.

```java
byte b = (byte) 0xAB;      // -85 dưới dạng byte có dấu.
int unsigned = b & 0xFF;  // 171.
int lowNibble = unsigned & 0x0F;       // 11 = 0xB.
int highNibble = unsigned >>> 4;       // 10 = 0xA.

System.out.println(lowNibble);  // 11
System.out.println(highNibble); // 10
```

Ở đây `0x0F` chỉ giữ bốn bit thấp; sau khi đã chuyển sang `unsigned` trong miền 0..255, dịch phải logic `>>> 4` đưa bốn bit cao xuống thấp.

**Bẫy:** nếu dịch trực tiếp `b >>> 4` khi `b` âm, `b` được nâng thành `int` có các bit cao bằng `1`. Dù `>>>` chèn `0` từ trái, kết quả có thể vẫn là một số lớn với các bit cao còn lại. Hãy viết `(b & 0xFF) >>> 4` hoặc `(b >>> 4) & 0x0F` nếu chỉ muốn nibble cao.

### Ghép hai byte thành số nguyên không dấu 16 bit

Giả sử `high` và `low` lần lượt là byte cao, byte thấp của một số được lưu theo thứ tự *big-endian*:

```java
byte high = (byte) 0xFF;
byte low = (byte) 0x80;

int value = (Byte.toUnsignedInt(high) << 8)
        | Byte.toUnsignedInt(low);

System.out.println(value); // 65408 = 0xFF80.
```

Ta lấy giá trị không dấu của từng byte trước khi ghép:

- `Byte.toUnsignedInt(high)` trả `255`; dịch trái 8 bit cho `0xFF00`.
- `Byte.toUnsignedInt(low)` trả `128`; phép OR theo bit `|` tạo `0xFF80`.
- Vì kiểu `int` đủ chứa toàn bộ khoảng 0..65535 nên kết quả `65408` đúng với cách hiểu unsigned 16-bit.

Nếu ghép các byte có dấu mà quên mask, *sign extension* của `high` hoặc `low` có thể làm sai kết quả. Khi ghép nhiều byte, cần quyết định **thứ tự byte (endianness)** và **dấu** theo định dạng dữ liệu trước khi viết phép dịch/OR. Ví dụ trên quy ước byte đầu là phần cao; định dạng *little-endian* sẽ có công thức khác.

## 7. Chuyển ngược số 0..255 về mẫu tám bit trong `byte`

Một số trong khoảng `0..255` có thể được mã hóa thành tám bit bằng cách cast xuống `byte`:

```java
int unsigned = 255;
byte packed = (byte) unsigned;

System.out.println(packed);                   // -1
System.out.println(Byte.toUnsignedInt(packed)); // 255
```

Trong biến `packed`, tám bit đều là `1`; giá trị `byte` của Java là `-1`. Điều này **không có nghĩa dữ liệu 255 đã mất**: tám bit ban đầu vẫn được giữ và có thể đọc trở lại bằng `Byte.toUnsignedInt`.

Tuy nhiên, cast hẹp **không kiểm tra miền**. Ví dụ:

```java
int unexpected = 300;
byte truncated = (byte) unexpected; // 44.
```

`300` có biểu diễn nhị phân dài hơn tám bit; giữ tám bit thấp cho `44`. Khi ứng dụng cần đảm bảo dữ liệu đã đúng miền unsigned byte, phải kiểm tra **trước** khi cast:

```java
static byte packUnsignedByte(int value) {
    if (value < 0 || value > 255) {
        throw new IllegalArgumentException("Expected 0..255");
    }
    return (byte) value;
}
```

Đây là ví dụ một phương thức ứng dụng tự định nghĩa, dùng khi **lỗi vượt miền phải bị phát hiện**. Không dùng `value & 0xFF` như phép kiểm tra đầu vào: biểu thức đó sẽ biến `300` thành `44` mà không cảnh báo.

## 8. `byte[]`, `ByteBuffer` và dữ liệu nhị phân từ file/mạng

### Mảng `byte[]`

Dữ liệu file hoặc mạng thường nằm trong mảng `byte[]`:

```java
byte[] payload = {(byte) 0x00, (byte) 0x80, (byte) 0xFF};

for (byte item : payload) {
    int unsigned = Byte.toUnsignedInt(item);
    System.out.println(unsigned);
}
```

Kết quả:

```text
0
128
255
```

Đối với một phần tử `payload[i]`, `payload[i] & 0xFF` là cách thay thế trực tiếp. Đừng kiểm tra `payload[i] > 127` để tìm các byte “trên 127”: một biến `byte` **không bao giờ lớn hơn 127**. Hãy lấy số không dấu bằng `Byte.toUnsignedInt(payload[i])` trước khi so sánh.

### `ByteBuffer.get()` trả về `byte` có dấu

```java
import java.nio.ByteBuffer;

ByteBuffer buffer = ByteBuffer.wrap(new byte[] {(byte) 0xFF});
byte item = buffer.get();
int unsigned = Byte.toUnsignedInt(item); // 255.
```

`ByteBuffer.get()` đọc một byte dưới dạng `byte` nguyên thủy. Nếu muốn một giá trị số nguyên không dấu, phải mask hoặc gọi `Byte.toUnsignedInt`. `ByteBuffer` cũng có các phương thức đọc số nhiều byte (`getShort`, `getInt`, …), chịu ảnh hưởng thứ tự byte; cách xử lý unsigned cho nhiều byte không đồng nhất với mask chỉ một byte.

### Khác biệt rất quan trọng: `InputStream.read()` đã trả về `int`

Với `java.io.InputStream.read()`, khi có byte dữ liệu, phương thức **đã trả về `int` từ 0 đến 255**. Giá trị `-1` được dành riêng làm tín hiệu **đã hết luồng (EOF)**.

```java
int next = input.read(); // input là InputStream đã được tạo.
if (next == -1) {
    System.out.println("EOF");
} else {
    System.out.println("Unsigned byte: " + next);
}
```

Đây là đoạn minh họa trong một ngữ cảnh đã có biến `input` kiểu `InputStream`; **không phải chương trình chạy độc lập**.

**Không viết `int value = input.read() & 0xFF` nếu chưa kiểm tra EOF**: khi `read()` trả `-1`, `-1 & 0xFF` thành `255`, che mất tín hiệu kết thúc luồng.

Sự khác biệt cần phân biệt:

| API | Kiểu nhận được | Ý nghĩa của `-1` |
| --- | --- | --- |
| `byte[][index]` | `byte` (-128..127) | Một mẫu bit dữ liệu `0xFF` hợp lệ |
| `ByteBuffer.get()` | `byte` (-128..127) | Một mẫu bit dữ liệu `0xFF` hợp lệ |
| `InputStream.read()` | `int` (0..255 hoặc -1) | **EOF**, không phải byte dữ liệu |
| `DataInputStream.readUnsignedByte()` | `int` (0..255) | Không dùng -1 làm giá trị kết thúc; hết dữ liệu gây `EOFException` |

Khi dùng `InputStream.read(byte[])`, dữ liệu thực tế được đặt trong `byte[]`, nhưng chính giá trị trả về của phương thức là **số byte đã đọc** (hoặc `-1` khi EOF). Không nhầm số byte đã đọc với một byte dữ liệu cụ thể.

## 9. Ví dụ Java 17+ chạy độc lập

Tạo tệp **`UnsignedByteMaskDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

public class UnsignedByteMaskDemo {
    public static void main(String[] args) throws Exception {
        byte b = (byte) 0xA5;

        int signed = b;
        int unsignedMasked = b & 0xFF;
        int unsignedApi = Byte.toUnsignedInt(b);

        System.out.println("signed=" + signed);
        System.out.println("masked=" + unsignedMasked);
        System.out.println("API=" + unsignedApi);
        System.out.println("signed hex=" + Integer.toHexString(signed));
        System.out.println("unsigned hex=" + Integer.toHexString(unsignedMasked));

        int lowNibble = unsignedMasked & 0x0F;
        int highNibble = unsignedMasked >>> 4;
        System.out.println("nibbles=" + highNibble + "," + lowNibble);

        byte high = (byte) 0xFF;
        byte low = (byte) 0x80;
        int combined = (Byte.toUnsignedInt(high) << 8)
                | Byte.toUnsignedInt(low);
        System.out.println("combined=" + combined);

        int packedValue = 255;
        byte packed = (byte) packedValue;
        System.out.println("packed signed=" + packed);
        System.out.println("packed unsigned=" + Byte.toUnsignedInt(packed));

        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {(byte) 0x80});
        System.out.println("buffer unsigned="
                + Byte.toUnsignedInt(buffer.get()));

        try (ByteArrayInputStream stream =
                new ByteArrayInputStream(new byte[] {(byte) 0xFF})) {
            int first = stream.read();
            int eof = stream.read();
            System.out.println("stream data=" + first);
            System.out.println("stream EOF=" + eof);
        }
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac UnsignedByteMaskDemo.java
java UnsignedByteMaskDemo
```

Kết quả:

```text
signed=-91
masked=165
API=165
signed hex=ffffffa5
unsigned hex=a5
nibbles=10,5
combined=65408
packed signed=-1
packed unsigned=255
buffer unsigned=128
stream data=255
stream EOF=-1
```

Kết quả `signed hex=ffffffa5` thể hiện `byte` âm được mở rộng dấu khi chuyển sang `int`. `unsigned hex=a5` thể hiện các bit cao đã bị xóa bởi mặt nạ `0xFF`. Hai dòng cuối xác nhận `InputStream.read()` phân biệt rõ byte `0xFF` (trả `255`) với EOF (trả `-1`).

## 10. Các lỗi thường gặp

1. **Nhầm `byte` là kiểu 0..255.** Trong Java, `byte` là kiểu **có dấu**, từ -128 đến 127. Cần `int` khi muốn lưu giá trị 0..255.
2. **Cho rằng `int u = b;` đã tạo giá trị unsigned.** Lệnh này bảo toàn giá trị có dấu bằng mở rộng dấu; khi `b < 0`, `u` cũng âm. Phải dùng `b & 0xFF` hoặc `Byte.toUnsignedInt(b)`.
3. **Dùng `Math.abs(b)` để chuyển không dấu.** Trị tuyệt đối không chuyển cách diễn giải tám bit, đặc biệt `-1` phải trở thành `255`, không phải `1`.
4. **Dùng `b >>> 4` trực tiếp để lấy nibble cao.** Với `byte` âm, Java mở rộng dấu thành `int` trước khi dịch. Hãy mask trước rồi dịch.
5. **Ghép nhiều byte mà không mask.** Một byte âm khi nâng kiểu có thể đưa các bit `1` lên những vị trí cao ngoài ý muốn. Dùng `Byte.toUnsignedInt` cho từng byte trước khi dịch và OR.
6. **Nghĩ `b & 0xFF` làm `b` thành kiểu unsigned.** Biến `b` không đổi; kết quả biểu thức là `int`.
7. **Nhầm `&` với `&&`.** `&` trên số nguyên là AND từng bit; `&&` dành cho các điều kiện boolean.
8. **Mask `InputStream.read()` trước khi kiểm tra EOF.** `-1` là EOF nhưng `-1 & 0xFF == 255`; thao tác đó có thể biến tín hiệu EOF thành byte dữ liệu giả.
9. **Dùng `value & 0xFF` như phép kiểm tra dữ liệu nhập từ 0..255.** Mask cắt bỏ bit cao; nếu cần bác bỏ số ngoài miền, hãy kiểm tra điều kiện `value < 0 || value > 255` trước khi chuyển sang `byte`.
10. **Cho rằng các giá trị `byte` âm là dữ liệu bị hỏng.** Chúng chỉ là cách nhìn có dấu của một mẫu tám bit. Với dữ liệu nhị phân, phải xem giao thức định nghĩa trường đó là có dấu hay không dấu.

## 11. Quy tắc chọn cách làm trong thực tế

- Nếu đã có **`byte` dữ liệu** và muốn số nguyên không dấu: dùng `Byte.toUnsignedInt(b)` hoặc `b & 0xFF`.
- Nếu đang **trích xuất bit** trong `int`: dùng mặt nạ thích hợp, như `& 0x0F` cho nibble thấp hoặc `& 0xFF` cho byte thấp.
- Nếu cần **tách/ghép nhiều byte**: quy định thứ tự byte trước; chuyển từng phần tử sang kiểu không dấu rồi dịch/OR.
- Nếu có **số nguyên đầu vào** cần đóng gói thành một byte: kiểm tra 0..255 trước khi cast nếu không cho phép cắt bit.
- Nếu đang đọc bằng **`InputStream.read()`**: xử lý `-1` như EOF trước mọi phép mask; `read()` đã trả `0..255` khi đọc thành công.

**Ghi nhớ:** `byte` của Java luôn có dấu, nhưng **mẫu 8 bit của nó vẫn giữ nguyên**. Mặt nạ `& 0xFF` hoặc `Byte.toUnsignedInt(b)` chuyển cách diễn giải mẫu đó thành một `int` từ 0 đến 255. Khi làm việc với file/mạng, luôn phân biệt **giá trị byte dữ liệu** với **giá trị điều khiển như EOF** và kiểm tra miền nếu cần.

**Tài liệu tham khảo:** [Java Language Specification §4.2.1 — Integral Types and Values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.1); [§5.1.2 — Widening Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.2); [§15.22 — Bitwise and Logical Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.22); [Byte.toUnsignedInt API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Byte.html#toUnsignedInt(byte)); [InputStream.read API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStream.html#read()).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-006">Byte.toUnsignedInt, Short.toUnsignedInt và Integer.toUnsignedLong</a>

<details>
<summary>Xem chi tiết</summary>

# Byte.toUnsignedInt, Short.toUnsignedInt và Integer.toUnsignedLong

Java có các kiểu số nguyên `byte` (8 bit), `short` (16 bit), `int` (32 bit) với biểu diễn **có dấu** (bù hai). Tuy nhiên, nhiều định dạng ảnh, tệp nhị phân, ID và giao thức mạng định nghĩa trường dữ liệu theo số nguyên **không dấu**. Ba phương thức tĩnh sau giúp đọc toàn bộ mẫu bit thành giá trị không âm bằng kiểu rộng hơn, thay vì hiểu nhầm bit dấu:

- `Byte.toUnsignedInt(byte value)`: giữ 8 bit thấp, trả `int` từ **0 đến 255**.
- `Short.toUnsignedInt(short value)`: giữ 16 bit thấp, trả `int` từ **0 đến 65.535**.
- `Integer.toUnsignedLong(int value)`: giữ 32 bit thấp, trả `long` từ **0 đến 4.294.967.295**.

Các API có từ Java 8, dùng được trong Java 17+. Chúng diễn giải lại cùng bit dữ liệu bằng một **giá trị có kiểu rộng hơn**; không thay đổi biến đầu vào, không tạo thêm kiểu nguyên thủy `unsigned byte`, `unsigned short` hoặc `unsigned int` trong Java.

## 1. Vì sao phải chuyển sang giá trị không dấu?

Mẫu `1111_1111` (hex `0xFF`) có hai cách diễn giải:

- `byte` Java: **−1**, vì bit cao nhất là bit dấu trong bù hai.
- Số nguyên không dấu 8 bit: **255**, vì cả tám bit đều mang trọng số dương.

Sự khác nhau tương tự xảy ra với `short` và `int`. Ví dụ `0xFFFF` là `short -1` nhưng là unsigned 16 bit `65535`; `0xFFFF_FFFF` là `int -1` nhưng là unsigned 32 bit `4294967295`.

| Phương thức | Đầu vào | Miền có dấu của đầu vào | Kiểu kết quả | Miền không dấu của kết quả |
| --- | --- | ---: | --- | ---: |
| `Byte.toUnsignedInt(b)` | `byte` (8 bit) | −128…127 | `int` | 0…255 |
| `Short.toUnsignedInt(s)` | `short` (16 bit) | −32.768…32.767 | `int` | 0…65.535 |
| `Integer.toUnsignedLong(i)` | `int` (32 bit) | −2.147.483.648…2.147.483.647 | `long` | 0…4.294.967.295 |

Phải chọn hàm dựa trên **độ rộng của kiểu dữ liệu gốc**. Hàm `Byte.toUnsignedInt` không thay thế cho `Short.toUnsignedInt` khi đang có 16 bit; `Integer.toUnsignedLong` không đơn thuần là cách viết khác của ép `int` sang `long`.

## 2. Cơ chế zero extension và các mặt nạ bit

Việc ép `byte` âm thành `int` hoặc `int` âm thành `long` theo Java thực hiện **sign extension**: những bit mới bên trái sao chép bit dấu (1 đối với số âm). Ba hàm ở trên muốn giữ giá trị **unsigned** của đúng 8/16/32 bit ban đầu, nên loại các bit 1 do mở rộng dấu gây ra.

Có thể hiểu kết quả tương đương với phép AND sau đây:

~~~java
int u8 = Byte.toUnsignedInt(b);        // bằng b & 0xFF
int u16 = Short.toUnsignedInt(s);     // bằng s & 0xFFFF
long u32 = Integer.toUnsignedLong(i); // bằng ((long) i) & 0xFFFF_FFFFL
~~~

Đoạn trên là **mã minh họa**: giả định `b`, `s`, `i` đã được khai báo lần lượt là `byte`, `short`, `int`. Với `b & 0xFF`, phép AND giữ 8 bit thấp sau khi `byte` được nâng lên `int`. Với `s & 0xFFFF`, giữ 16 bit thấp. Với `i`, mặt nạ phải mang kiểu `long` (hậu tố `L`) để cho ra một số dương chứa đủ 32 bit.

Nếu đầu vào vốn không âm, kết quả bằng chính giá trị đầu vào. Nếu đầu vào âm, kết quả số học không dấu bằng giá trị signed cộng `2^8`, `2^16` hoặc `2^32` tương ứng. Đây là quy tắc **diễn giải giá trị**, không phải phép toán trực tiếp sửa biến gốc.

| Biểu thức | Giá trị signed tương ứng | Kết quả unsigned |
| --- | ---: | ---: |
| `Byte.toUnsignedInt((byte) 0x80)` | −128 | 128 |
| `Byte.toUnsignedInt((byte) 0xFF)` | −1 | 255 |
| `Short.toUnsignedInt((short) 0x8000)` | −32768 | 32768 |
| `Short.toUnsignedInt((short) 0xFFFF)` | −1 | 65535 |
| `Integer.toUnsignedLong(0x8000_0000)` | −2147483648 | 2147483648 |
| `Integer.toUnsignedLong(0xFFFF_FFFF)` | −1 | 4294967295 |

Lưu ý: `Byte`, `Short`, `Integer` là các **lớp wrapper**; tên phương thức là `static`, vì vậy gọi trực tiếp như bảng trên, không cần `new Byte(...)`.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Tạo tệp `UnsignedConversionsDemo.java`:

~~~java
public class UnsignedConversionsDemo {
    public static void main(String[] args) {
        byte b = (byte) 0xFF;
        short s = (short) 0x8001;
        int i = 0xFFFF_FFFE;

        System.out.println("byte signed: " + b);
        System.out.println("byte unsigned: " + Byte.toUnsignedInt(b));
        System.out.println("short signed: " + s);
        System.out.println("short unsigned: " + Short.toUnsignedInt(s));
        System.out.println("int signed: " + i);
        System.out.println("int unsigned: " + Integer.toUnsignedLong(i));

        System.out.println("byte mask: " + (b & 0xFF));
        System.out.println("short mask: " + (s & 0xFFFF));
        System.out.println("int mask: " + (i & 0xFFFF_FFFFL));

        byte[] packet = {
            (byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0x98
        };
        int raw32 =
                (Byte.toUnsignedInt(packet[0]) << 24)
              | (Byte.toUnsignedInt(packet[1]) << 16)
              | (Byte.toUnsignedInt(packet[2]) << 8)
              | Byte.toUnsignedInt(packet[3]);
        long packetUnsigned = Integer.toUnsignedLong(raw32);

        System.out.printf("packet=0x%08X, unsigned=%d%n",
                raw32, packetUnsigned);

        System.out.println("signed compare: " + (-1 < 1));
        System.out.println("unsigned compare: "
                + (Integer.compareUnsigned(-1, 1) > 0));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac UnsignedConversionsDemo.java
java UnsignedConversionsDemo
~~~

Đầu ra:

~~~text
byte signed: -1
byte unsigned: 255
short signed: -32767
short unsigned: 32769
int signed: -2
int unsigned: 4294967294
byte mask: 255
short mask: 32769
int mask: 4294967294
packet=0xFEDCBA98, unsigned=4275878552
signed compare: true
unsigned compare: true
~~~

Giải thích:

- `0xFF` có 8 bit đều bằng 1. Với `byte` nó bằng `-1`; qua `Byte.toUnsignedInt` là `255`.
- `0x8001` có bit dấu 16 bit bằng 1. Với `short` nó bằng `-32767`; qua `Short.toUnsignedInt` là `32769`.
- `0xFFFF_FFFE` dưới dạng `int` là `-2`; qua `Integer.toUnsignedLong` là `4294967294L`.
- Các dòng `mask` cho thấy phép AND đúng kiểu cho kết quả bằng các API tường minh. Việc biểu diễn bằng hàm thường dễ đọc và thể hiện rõ ý định hơn.

## 4. Đọc trường nhị phân từ byte array

Giả sử một giao thức mô tả ID **unsigned 32 bit, big-endian** gồm bốn byte `FE DC BA 98`. `byte[]` Java vẫn giữ đủ bit, nhưng những phần tử bắt đầu bằng bit 1 sẽ in ra số âm.

Trong ví dụ:

1. `Byte.toUnsignedInt(packet[k])` biến từng phần tử thành số 0–255 trước khi ghép.
2. Các toán tử `<< 24`, `<< 16`, `<< 8` đặt byte ở đúng vị trí và `|` ghép đủ 32 bit. `raw32` có mẫu bit `0xFEDCBA98` (có thể âm nếu in ra như `int` signed).
3. `Integer.toUnsignedLong(raw32)` đưa mẫu 32 bit này về `long` dương `4275878552L`, phù hợp với định nghĩa ID không dấu của giao thức.

Trường 16 bit big-endian cũng có thể ghép:

~~~java
// byte[] data chứa ít nhất hai phần tử, theo thứ tự big-endian.
int unsigned16 = (Byte.toUnsignedInt(data[0]) << 8)
               | Byte.toUnsignedInt(data[1]);
~~~

Đây là mã minh họa phụ thuộc `data`. Nếu đã đọc nguyên trường bằng `ByteBuffer.getShort()`, hãy dùng `Short.toUnsignedInt(shortValue)`; nếu đọc bằng `ByteBuffer.getInt()`, dùng `Integer.toUnsignedLong(intValue)`. Hướng byte **big-endian/little-endian** do định dạng dữ liệu quy định; các phương thức `toUnsigned...` không tự đảo thứ tự byte.

Trong phép `<< 24` ở ví dụ, kết quả trung gian kiểu `int` có thể âm nhưng vẫn giữ đúng bit để ghép; bước `Integer.toUnsignedLong` sau đó mới diễn giải cả trường thành số không dấu. Không cần cố ép từng byte thành `long` chỉ vì bit cao nhất của `raw32` bằng 1.

## 5. So sánh, tính toán và giới hạn miền unsigned

Với unsigned 8/16 bit, một `int` đủ rộng để chứa trọn kết quả. Với unsigned 32 bit, cần `long` vì `int` signed chỉ tối đa `2147483647`.

Ví dụ `Integer.toUnsignedLong(-1) + 1` cho `4294967296L`, phép cộng hợp lệ trên `long`; tuy nhiên con số ấy đã **vượt miền unsigned 32 bit**. Nếu ghi trả về một trường 32 bit, hãy kiểm tra `0 <= value && value <= 0xFFFF_FFFFL` đối với biến `long value` trước khi ép `(int) value`. Ép xuống `int` lấy 32 bit thấp, kết quả có thể âm khi in ra theo cách signed của Java. Ép giá trị `4294967296L` thành `int` cho `0`: đây là **mất dữ liệu** nếu chưa kiểm tra miền.

So sánh signed và unsigned cũng khác nhau:

~~~java
int a = -1;
int b = 1;
boolean signedLess = a < b;                         // true
boolean unsignedGreater = Integer.compareUnsigned(a, b) > 0; // true
boolean sameViaLong =
        Integer.toUnsignedLong(a) > Integer.toUnsignedLong(b); // true
~~~

Đây là mã minh họa độc lập trong một khối/phương thức hợp lệ. Signed `-1` nhỏ hơn `1`, nhưng bit `0xFFFF_FFFF` tương ứng unsigned 32 bit `4294967295` lớn hơn `1`. Đừng sử dụng toán tử `<` giữa hai `int` signed khi giao thức yêu cầu thứ tự **unsigned**.

Khi dữ liệu thực sự có ý nghĩa signed (chênh lệch âm, nhiệt độ âm, số signed trong đặc tả), giữ nguyên cách diễn giải signed. Chuyển đổi không dấu là lựa chọn dựa trên **ngữ nghĩa dữ liệu nguồn**, chứ không phải mẹo làm mọi số âm biến mất.

## 6. Lỗi phổ biến và cách tránh

### 6.1. Nhầm ép kiểu với unsigned conversion

~~~java
byte b = (byte) 0xFF;
int signedByte = (int) b;                  // -1: sign extension
int unsignedByte = Byte.toUnsignedInt(b); // 255

int i = -1;
long signedInt = (long) i;                   // -1
long unsignedInt = Integer.toUnsignedLong(i); // 4294967295
~~~

Ép `byte` sang `int` và `int` sang `long` giữ **giá trị có dấu**. Các đoạn trong mục lỗi phổ biến là ví dụ nội tuyến, không phải chương trình Java độc lập.

### 6.2. Đặt hậu tố mặt nạ sai chỗ

~~~java
int i = -1;
long wrong = (long) (i & 0xFFFF_FFFF);     // -1
long correct = ((long) i) & 0xFFFF_FFFFL; // 4294967295
long preferred = Integer.toUnsignedLong(i); // 4294967295
~~~

`0xFFFF_FFFF` không có `L` là hằng `int` có toàn bit 1, tức `-1`. Phép `i & -1` vẫn ra `i`; chỉ ép lên `long` sau đó sẽ mở rộng dấu. Dùng mặt nạ `long` `0xFFFF_FFFFL`, hoặc đơn giản dùng `Integer.toUnsignedLong`.

### 6.3. Dịch bit trước khi xử lý sign extension

Với `byte` âm, Java nâng toán hạng sang `int` trước phép dịch. `>>` điền thêm bit dấu 1; `>>>` điền bit 0 nhưng **không tự tạo ra kiểu unsigned nguyên thủy**. Cách dễ hiểu để lấy nibble cao của một byte là:

~~~java
byte encoded = (byte) 0xF0;
int highNibble = Byte.toUnsignedInt(encoded) >>> 4; // 15
~~~

Nếu dịch một `byte` âm rồi dùng kết quả ngay mà không mask đúng số bit, các bit 1 do sign extension có thể ảnh hưởng. Hãy kiểm soát 8 bit trước khi thao tác.

### 6.4. Không kiểm tra dữ liệu khi thu hẹp trở lại

Ba hàm này tạo giá trị chứa được toàn bộ miền unsigned nhờ **mở rộng kiểu**. Không có điều gì ngăn bạn thực hiện cộng, nhân hoặc ép kiểu khiến kết quả vượt phạm vi mong muốn. Khi lưu về 8/16/32 bit, tự kiểm tra giá trị có phù hợp đặc tả hay không; phép cast tự nó có thể cắt các bit cao.

### 6.5. Nhầm unsigned 32 bit với unsigned 64 bit

`Integer.toUnsignedLong` chuyển unsigned **32 bit** sang một `long` dương, còn `long` Java vẫn là kiểu **signed 64 bit**. Nó không tạo giá trị dương để chứa trọn unsigned 64 bit; trường hợp đó có thể cần `Long.compareUnsigned`, `Long.toUnsignedString` hoặc `BigInteger`.

`char` có miền số nguyên 0–65535 nhưng mang ngữ nghĩa **ký tự UTF-16**. Một trường dữ liệu 16 bit không dấu đã nằm trong `short` vẫn nên đọc rõ ý định bằng `Short.toUnsignedInt`.

## 7. Câu hỏi tự luyện

**1.** Nếu `byte b = (byte) 0x80`, `(int) b` và `Byte.toUnsignedInt(b)` là gì?  
**Đáp án:** `-128` và `128`. Cùng 8 bit thấp, khác cách mở rộng và diễn giải.

**2.** Nếu `short s = (short) 0xFFFF`, kết quả `Short.toUnsignedInt(s)`?  
**Đáp án:** `65535`, vì cả 16 bit đều là 1.

**3.** `Integer.toUnsignedLong(Integer.MIN_VALUE)` bằng bao nhiêu?  
**Đáp án:** `2147483648L`, ứng với duy nhất bit 31 bằng 1 trong unsigned 32 bit.

**4.** Vì sao `(long) (i & 0xFFFF_FFFF)` có thể sai khi `i` âm?  
**Đáp án:** Mask là `int -1` nên không xóa bit dấu; ép `long` sau đó vẫn mở rộng dấu. Hãy dùng `0xFFFF_FFFFL` với `long` hoặc `Integer.toUnsignedLong(i)`.

**5.** Dữ liệu byte `FE DC BA 98` big-endian có thể lấy số không dấu bằng API nào?  
**Đáp án:** Dùng `Byte.toUnsignedInt` với từng byte để ghép 32 bit, sau đó `Integer.toUnsignedLong` để diễn giải cả trường thành `4275878552L`.

## 8. Điểm cần nhớ

- Bắt đầu từ **độ rộng dữ liệu gốc**: `byte` → `Byte.toUnsignedInt`; `short` → `Short.toUnsignedInt`; `int` → `Integer.toUnsignedLong`.
- Kết quả là **giá trị không âm trong kiểu rộng hơn**, không đổi biến đầu vào hoặc biểu diễn bit thấp.
- Khi thao tác bit, phân biệt sign extension với mask; với unsigned 32 bit, dùng hằng mặt nạ `long` nếu không dùng API.
- Khi đọc định dạng nhị phân, tuân thủ độ rộng và endian trong đặc tả; khi so sánh hoặc ghi lại, kiểm tra miền unsigned và chọn phép so sánh phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-007">compareUnsigned, divideUnsigned và remainderUnsigned</a>

<details>
<summary>Xem chi tiết</summary>

# `compareUnsigned`, `divideUnsigned` và `remainderUnsigned` trong Java

## 1. Vấn đề: `int`/`long` có dấu nhưng dữ liệu đôi khi phải hiểu là không dấu

Java chỉ có kiểu nguyên thủy `int` 32 bit và `long` 64 bit **có dấu**; không có kiểu nguyên thủy `uint` hay `ulong`. Tuy nhiên, cùng một chuỗi bit có thể được diễn giải thành số có dấu hoặc không dấu:

| Mẫu bit | Diễn giải có dấu | Diễn giải không dấu |
| --- | ---: | ---: |
| `int` toàn bit `0` | 0 | 0 |
| `int` `0x7FFFFFFF` | 2.147.483.647 | 2.147.483.647 |
| `int` `0x80000000` | -2.147.483.648 | 2.147.483.648 |
| `int` `0xFFFFFFFF` | -1 | 4.294.967.295 |
| `long` `0xFFFFFFFFFFFFFFFFL` | -1 | 18.446.744.073.709.551.615 |

Với 32 bit, miền không dấu là `0..2^32-1`; với 64 bit, miền không dấu là `0..2^64-1`. Hai cách hiểu **không thay đổi các bit đang lưu**. Chúng chỉ thay đổi ý nghĩa của việc so sánh, chia, lấy dư hoặc hiển thị các bit đó.

Ví dụ, `int value = -1;` có mẫu bit `0xFFFFFFFF`. Nếu dùng quy tắc số có dấu, `-1 < 1`; nếu diễn giải như số không dấu 32 bit, `4_294_967_295 > 1`. Java cung cấp các phương thức tĩnh giúp thực hiện **phép toán theo cách hiểu không dấu** mà vẫn dùng `int`/`long`.

Ba nhóm API chính (có từ Java 8):

- `Integer.compareUnsigned(int x, int y)` và `Long.compareUnsigned(long x, long y)`.
- `Integer.divideUnsigned(int dividend, int divisor)` và `Long.divideUnsigned(long dividend, long divisor)`.
- `Integer.remainderUnsigned(int dividend, int divisor)` và `Long.remainderUnsigned(long dividend, long divisor)`.

Các phương thức này phù hợp khi một **giao thức, trường bit, định dạng file, bộ đếm hoặc mã định danh** định nghĩa các bit là số nguyên không dấu. Chúng không biến Java thành ngôn ngữ có kiểu nguyên thủy unsigned mới.

## 2. Ngữ nghĩa của `compareUnsigned`

`compareUnsigned(x, y)` so sánh hai mẫu bit sau khi diễn giải chúng là **số không dấu**, rồi trả về một `int`:

- Giá trị **âm**: `x` nhỏ hơn `y` theo thứ tự không dấu.
- `0`: hai mẫu bit bằng nhau.
- Giá trị **dương**: `x` lớn hơn `y` theo thứ tự không dấu.

Không nên phụ thuộc vào giá trị chính xác là `-1` hay `1`; hợp đồng của phương thức là **dấu của kết quả**. Khi dùng Comparator, kiểm tra `< 0`, `== 0` hoặc `> 0`.

```java
int a = -1;  // bit 0xFFFFFFFF => unsigned 4294967295.
int b = 1;

System.out.println(Integer.compare(a, b) < 0);         // true.
System.out.println(Integer.compareUnsigned(a, b) > 0); // true.
```

Với phép so sánh thông thường, `-1 < 1`. Với unsigned, `0xFFFFFFFF` đại diện cho `4_294_967_295` nên lớn hơn `1`.

Một cách đọc nhanh mẫu bit:

- Nếu hai `int` cùng có bit dấu bằng `0`, so sánh có dấu và không dấu có cùng thứ tự.
- Nếu hai `int` cùng có bit dấu bằng `1`, thứ tự so sánh của chúng cũng giống nhau (cả hai cùng nằm ở nửa cao của miền unsigned).
- Nếu một giá trị có bit dấu `1` (hiện ra là số âm) và giá trị kia có bit dấu `0`, **giá trị hiện ra âm sẽ lớn hơn theo unsigned**.

Quy tắc tương tự áp dụng cho `long` với 64 bit.

### Ví dụ sắp xếp theo thứ tự không dấu

```java
import java.util.ArrayList;
import java.util.List;

List<Integer> numbers = new ArrayList<>(List.of(-1, 2, 0, -2));
numbers.sort(Integer::compareUnsigned);
System.out.println(numbers); // [0, 2, -2, -1]
```

Danh sách vẫn in các giá trị theo kiểu `int` **có dấu**, nhưng thứ tự sắp xếp đã dựa trên ý nghĩa unsigned: `0`, `2`, `4_294_967_294`, `4_294_967_295`. Đừng dùng `Comparator.naturalOrder()` hoặc phép so sánh `<` có dấu nếu trường dữ liệu bắt buộc được xếp theo unsigned.

Lưu ý `Arrays.sort(int[])` sắp xếp theo số `int` có dấu. Để có comparator unsigned, ví dụ trên dùng danh sách `Integer` hoặc cần triển khai chiến lược sắp xếp unsigned riêng.

## 3. Ngữ nghĩa của `divideUnsigned` và `remainderUnsigned`

Với hai mẫu bit có cùng độ rộng `w`, đặt:

- `A` là giá trị không dấu của `dividend`, trong miền `0..2^w-1`.
- `B` là giá trị không dấu của `divisor`, **phải khác 0**.
- `Q = floor(A / B)` là thương nguyên không dấu.
- `R = A - B * Q` là số dư, thỏa `0 <= R < B`.

Khi `divisor` có mẫu bit bằng 0, cả hai phương thức chia/lấy dư unsigned đều ném `ArithmeticException`, giống phép chia nguyên thông thường.

### Ví dụ với `int`

```java
int dividend = -1; // 0xFFFFFFFF, nghĩa unsigned = 4294967295.
int divisor = 10;

int signedQuotient = dividend / divisor;
int signedRemainder = dividend % divisor;
int unsignedQuotient = Integer.divideUnsigned(dividend, divisor);
int unsignedRemainder = Integer.remainderUnsigned(dividend, divisor);

System.out.println(signedQuotient);   // 0
System.out.println(signedRemainder);  // -1
System.out.println(unsignedQuotient); // 429496729
System.out.println(unsignedRemainder);// 5
```

Về unsigned, `4_294_967_295 = 10 × 429_496_729 + 5`. Kết quả unsigned khác hoàn toàn so với `-1 / 10 == 0` và `-1 % 10 == -1` theo phép toán có dấu. Đây là **thay đổi quy tắc số học**, không đơn thuần thay đổi cách in số.

### Trường hợp mẫu số có bit dấu bằng 1

```java
int dividend = 10;
int divisor = -1; // Nghĩa unsigned = 4294967295.

System.out.println(Integer.divideUnsigned(dividend, divisor));    // 0
System.out.println(Integer.remainderUnsigned(dividend, divisor)); // 10
```

Dưới cách hiểu unsigned, mẫu số lớn hơn tử số, nên thương bằng 0 và dư bằng chính tử số. Không được coi mẫu số `-1` là số âm khi suy luận về kết quả unsigned.

## 4. Kết quả phép chia unsigned vẫn có kiểu `int`/`long`

Đây là điểm dễ gây hiểu nhầm nhất: các API unsigned **trả về kiểu nguyên thủy có dấu của Java**.

`Integer.divideUnsigned` trả về `int`; `Long.divideUnsigned` trả về `long`. Nếu thương unsigned lớn hơn miền dương của kiểu đó, mẫu bit kết quả khi in trực tiếp có thể xuất hiện như **một số âm**.

```java
int x = -1; // unsigned 4294967295.
int q = Integer.divideUnsigned(x, 1);

System.out.println(q);                         // -1
System.out.println(Integer.toUnsignedString(q)); // 4294967295
System.out.println(Integer.toUnsignedLong(q));   // 4294967295
```

Lý do: thương unsigned thực sự là `4_294_967_295`, có mẫu bit `0xFFFFFFFF`. API trả mẫu bit đó dưới kiểu `int`, nên `System.out.println(q)` in `-1` theo cách hiểu có dấu. Muốn đọc đúng unsigned, dùng `Integer.toUnsignedString(q)` hoặc chuyển thành `long` không dấu với `Integer.toUnsignedLong(q)`.

**Đặc biệt với `long`**:

```java
long allBits = -1L; // unsigned 18446744073709551615.
long quotient = Long.divideUnsigned(allBits, 1L);

System.out.println(quotient);                       // -1
System.out.println(Long.toUnsignedString(quotient)); // 18446744073709551615
```

Không có một kiểu nguyên thủy Java nào đủ lưu toàn bộ miền unsigned 64 bit dưới dạng một **số không âm**. Nếu cần biểu diễn số nguyên toán học từ 0 đến `2^64-1`, có thể dùng `BigInteger` thông qua chuỗi unsigned:

```java
import java.math.BigInteger;

long bits = -1L;
BigInteger asPositive = new BigInteger(Long.toUnsignedString(bits));
System.out.println(asPositive); // 18446744073709551615
```

Đừng dùng `BigInteger.valueOf(bits)` trong trường hợp muốn số **không dấu**: nó bảo toàn giá trị có dấu `-1`.

## 5. So sánh unsigned không đồng nghĩa với ép kiểu mở rộng thông thường

Một `int` âm khi gán thẳng vào `long` được **mở rộng dấu**:

```java
int bits = -1;

long signedWiden = bits;
long unsignedWiden = Integer.toUnsignedLong(bits);

System.out.println(signedWiden);   // -1
System.out.println(unsignedWiden); // 4294967295
```

`Integer.toUnsignedLong(bits)` tương đương với `bits & 0xFFFF_FFFFL`. Mặt nạ giữ đúng 32 bit của `int` và xóa các bit cao trong `long`. Đây là cách có ích khi muốn làm phép tính unsigned 32 bit bằng một kiểu **đủ rộng để giữ toàn bộ giá trị 32 bit ở dạng không âm**.

```java
int a = -1;
int b = 2;

long unsignedA = Integer.toUnsignedLong(a);
long unsignedB = Integer.toUnsignedLong(b);

long quotient = unsignedA / unsignedB; // 2147483647
long remainder = unsignedA % unsignedB; // 1
```

Với `int`, cách mở rộng đúng vào `long` rồi chia hoạt động vì `long` đủ sức chứa `0..2^32-1`. Nhưng với **unsigned `long`**, không thể dùng `long` như một nơi chứa toàn bộ miền 0..2^64-1 dưới dạng số không âm. Đó là lý do `Long.divideUnsigned`/`Long.remainderUnsigned` đặc biệt hữu ích; nếu cần tính toán với số lớn vượt miền, hãy dùng `BigInteger`.

## 6. Ví dụ unsigned 64 bit: so sánh và chia mà không mất bit

```java
long maxUnsignedBits = -1L; // 0xFFFFFFFFFFFFFFFF.
long divisor = 2L;

System.out.println(Long.compare(maxUnsignedBits, 1L) < 0); // true.
System.out.println(Long.compareUnsigned(maxUnsignedBits, 1L) > 0); // true.

long q = Long.divideUnsigned(maxUnsignedBits, divisor);
long r = Long.remainderUnsigned(maxUnsignedBits, divisor);

System.out.println(Long.toUnsignedString(q)); // 9223372036854775807
System.out.println(Long.toUnsignedString(r)); // 1
```

Unsigned `2^64-1` chia `2` cho thương `2^63-1`, dư `1`. Trường hợp này thương vừa đúng miền dương `long`, nhưng các phép chia khác có thể trả về bit mang dấu âm; vì vậy dùng `Long.toUnsignedString` là thói quen tốt khi xuất dữ liệu theo định dạng unsigned.

**Lưu ý:** `Long.divideUnsigned(x, y)` không làm phép chia dấu phẩy động, không làm tròn và không trả `Infinity`. Đây là phép chia **nguyên không dấu** trên 64 bit; mẫu số bằng `0L` ném `ArithmeticException`.

## 7. Liên hệ giữa ba phép toán và quy tắc chia có dư

Với các giá trị unsigned `A`, `B != 0`, phép chia đảm bảo:

```text
A = B * Q + R
0 <= R < B
Q = floor(A / B)
```

Các biểu thức toán học phía trên được hiểu **trên miền số nguyên không dấu**. Nếu kiểm chứng bằng toán tử `*` và `+` của Java ở cùng độ rộng bit, các phép tính trung gian có thể tràn theo phép toán nguyên cố định và kết quả hiển thị dưới dạng số có dấu.

Để kiểm chứng một ví dụ unsigned 32 bit một cách dễ đọc, hãy dùng `long` và `Integer.toUnsignedLong`:

```java
int a = -1;
int b = 10;
int q = Integer.divideUnsigned(a, b);
int r = Integer.remainderUnsigned(a, b);

long A = Integer.toUnsignedLong(a);
long B = Integer.toUnsignedLong(b);
long Q = Integer.toUnsignedLong(q);
long R = Integer.toUnsignedLong(r);

System.out.println(A == B * Q + R); // true.
System.out.println(R < B);          // true.
```

Với phép chia unsigned 64 bit, việc nhân `B * Q` có thể vượt miền `long`, nên muốn kiểm chứng bằng số nguyên toán học không mất dữ liệu có thể dùng `BigInteger` (hoặc so sánh theo quan hệ modulo phù hợp).

## 8. Chuyển dữ liệu unsigned sang/ra khỏi chuỗi

Nếu dữ liệu giao tiếp dùng văn bản thập phân unsigned, đừng lấy `String.valueOf(value)` hoặc `Integer.parseInt` làm giải pháp mặc định cho các giá trị vượt `Integer.MAX_VALUE`.

| Mục đích | API | Kết quả/ghi chú |
| --- | --- | --- |
| In `int` theo unsigned | `Integer.toUnsignedString(value)` | In từ 0 đến 4294967295. |
| In `long` theo unsigned | `Long.toUnsignedString(value)` | In từ 0 đến 18446744073709551615. |
| Đọc chuỗi unsigned 32 bit | `Integer.parseUnsignedInt(text)` | Trả `int` mang đúng mẫu bit; có thể trông âm khi in signed. |
| Đọc chuỗi unsigned 64 bit | `Long.parseUnsignedLong(text)` | Trả `long` mang đúng mẫu bit; có thể trông âm khi in signed. |
| Đọc `int` như unsigned thành `long` dương | `Integer.toUnsignedLong(value)` | Giữ 32 bit và diễn giải thành 0..4294967295. |
| Đọc unsigned `long` thành số toán học dương | `new BigInteger(Long.toUnsignedString(value))` | Có thể biểu diễn đầy đủ miền 64 bit. |

Ví dụ:

```java
int u32 = Integer.parseUnsignedInt("4294967295");
long u64 = Long.parseUnsignedLong("18446744073709551615");

System.out.println(u32);                         // -1.
System.out.println(Integer.toUnsignedString(u32)); // 4294967295.
System.out.println(u64);                         // -1.
System.out.println(Long.toUnsignedString(u64)); // 18446744073709551615.
```

Các phương thức `parseUnsignedInt` và `parseUnsignedLong` kiểm tra miền hợp lệ của kiểu unsigned tương ứng và ném `NumberFormatException` với chuỗi không hợp lệ hoặc số vượt giới hạn. Chúng **không** mở rộng kích thước biến thành một kiểu unsigned mới.

Cùng một dữ liệu có thể có cách hiển thị rất khác nhau. Khi xây dựng API, log hoặc lưu tệp, hãy thống nhất rõ **trường được mô tả là signed hay unsigned**, dùng phương thức chuyển đổi tương ứng.

## 9. So sánh với `byte`/`short` và xử lý bit

Các phương thức `compareUnsigned`, `divideUnsigned` và `remainderUnsigned` trực tiếp được cung cấp bởi `Integer` và `Long`. Với `byte` và `short`, nên chuyển sang giá trị dương theo miền unsigned trước khi so sánh/chia:

```java
byte raw = (byte) 0xFF;
int u8 = Byte.toUnsignedInt(raw); // 255.

short raw16 = (short) 0xFFFF;
int u16 = Short.toUnsignedInt(raw16); // 65535.

System.out.println(u8 / 10);   // 25
System.out.println(u8 % 10);   // 5
System.out.println(u16 > u8);  // true
```

`Byte.toUnsignedInt` tương đương với `raw & 0xFF`; `Short.toUnsignedInt` tương đương với `raw16 & 0xFFFF`. Sau khi chuyển sang `int` dương, các phép so sánh và chia có dấu thông thường có cùng kết quả với unsigned **vì mọi toán hạng đang ở trong miền dương đủ rộng**.

Khi dữ liệu nhị phân được ghép thành `int` hoặc `long`, cần xác định cách sắp xếp byte (*endianness*) và cách diễn giải trường từ giao thức. Một trường 32 bit có bit cao nhất bằng 1 sẽ trông âm trong Java, dù giao thức quy định đó là số unsigned lớn; khi đó ba API trên giữ đúng ngữ nghĩa cần dùng.

## 10. Ví dụ Java 17+ hoàn chỉnh

Tạo tệp **`UnsignedOperationsDemo.java`**:

```java
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class UnsignedOperationsDemo {
    public static void main(String[] args) {
        int x = -1;
        int divisor = 10;

        System.out.println("signed compare -1 < 1 = "
                + (Integer.compare(x, 1) < 0));
        System.out.println("unsigned compare -1 > 1 = "
                + (Integer.compareUnsigned(x, 1) > 0));
        System.out.println("signed quotient = " + (x / divisor));
        System.out.println("signed remainder = " + (x % divisor));
        System.out.println("unsigned quotient = "
                + Integer.divideUnsigned(x, divisor));
        System.out.println("unsigned remainder = "
                + Integer.remainderUnsigned(x, divisor));
        System.out.println("unsigned int text = "
                + Integer.toUnsignedString(x));

        List<Integer> items = new ArrayList<>(List.of(-1, 2, 0, -2));
        items.sort(Integer::compareUnsigned);
        System.out.println("unsigned sorted = " + items);

        long bigBits = -1L;
        long quotient64 = Long.divideUnsigned(bigBits, 2L);
        long remainder64 = Long.remainderUnsigned(bigBits, 2L);
        System.out.println("unsigned long quotient = "
                + Long.toUnsignedString(quotient64));
        System.out.println("unsigned long remainder = "
                + Long.toUnsignedString(remainder64));

        long allBitsQuotient = Long.divideUnsigned(bigBits, 1L);
        System.out.println("quotient signed view = " + allBitsQuotient);
        System.out.println("quotient unsigned view = "
                + Long.toUnsignedString(allBitsQuotient));

        BigInteger fullUnsignedLong =
                new BigInteger(Long.toUnsignedString(bigBits));
        System.out.println("BigInteger view = " + fullUnsignedLong);

        int parsed = Integer.parseUnsignedInt("4294967295");
        System.out.println("parsed unsigned int bits = " + parsed);

        try {
            Integer.divideUnsigned(123, 0);
        } catch (ArithmeticException ex) {
            System.out.println("division by zero detected");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac UnsignedOperationsDemo.java
java UnsignedOperationsDemo
```

Kết quả:

```text
signed compare -1 < 1 = true
unsigned compare -1 > 1 = true
signed quotient = 0
signed remainder = -1
unsigned quotient = 429496729
unsigned remainder = 5
unsigned int text = 4294967295
unsigned sorted = [0, 2, -2, -1]
unsigned long quotient = 9223372036854775807
unsigned long remainder = 1
quotient signed view = -1
quotient unsigned view = 18446744073709551615
BigInteger view = 18446744073709551615
parsed unsigned int bits = -1
division by zero detected
```

Bài toán trong ví dụ dùng đúng một bộ bit nhưng so sánh, chia và in theo hai cách hiểu khác nhau. Chú ý `items` sau sort vẫn hiển thị các giá trị có dấu; điều quan trọng là comparator đã xếp chúng theo **thứ tự không dấu**.

## 11. Lỗi thường gặp

1. **Dùng `x < y` để so sánh trường giao thức unsigned.** Nếu bit dấu khác nhau, thứ tự có dấu có thể trái ngược unsigned. Dùng `compareUnsigned`.
2. **Dùng `x / y` hoặc `x % y` cho hai mẫu bit unsigned.** Phép chia/lấy dư mặc định diễn giải toán hạng theo signed. Dùng `divideUnsigned`/`remainderUnsigned` khi quy tắc dữ liệu là unsigned.
3. **Nghĩ kết quả `divideUnsigned` luôn hiển thị không âm.** Kiểu trả về vẫn là `int` hoặc `long`. Với kết quả vượt miền signed dương, hãy dùng `toUnsignedString` hoặc chuyển sang kiểu đủ rộng thích hợp.
4. **Đổi `int` âm sang `long` bằng gán trực tiếp và tưởng là unsigned.** Chuyển đổi mở rộng thông thường giữ dấu. Dùng `Integer.toUnsignedLong` hoặc `x & 0xFFFF_FFFFL`.
5. **Cho rằng `Long.toUnsignedString(x)` sửa đổi giá trị `x`.** Hàm chỉ tạo cách biểu diễn chuỗi của cùng một mẫu 64 bit.
6. **Gọi `Math.abs(x)` để xử lý số nguyên unsigned.** Trị tuyệt đối không chuyển cách diễn giải bit, và với `MIN_VALUE` còn có bẫy tràn.
7. **Quên kiểm tra mẫu số 0.** Cả phép chia/lấy dư unsigned đều ném `ArithmeticException` khi mẫu số có bit bằng 0.
8. **Đánh đồng `Math.floorDiv`/`Math.floorMod` với phép toán unsigned.** Các hàm `floorDiv`/`floorMod` dùng quy tắc làm tròn/dấu trên toán hạng có dấu, không phải unsigned 32/64 bit.
9. **Đọc `long` unsigned tối đa bằng `Long.parseLong`.** Giá trị `2^64-1` vượt `Long.MAX_VALUE`; dùng `Long.parseUnsignedLong` rồi hiển thị bằng `Long.toUnsignedString`.
10. **Nhầm giữa “bit pattern” và “số nguyên toán học”.** API unsigned định nghĩa cách xử lý mẫu 32/64 bit; khi cần lưu giá trị dương từ 0 đến `2^64-1` như số toán học, chuyển sang `BigInteger` thích hợp.

## 12. Chọn API nào trong thực tế?

| Nhu cầu | Cách dùng |
| --- | --- |
| So sánh thứ tự hai trường unsigned 32 bit | `Integer.compareUnsigned(a, b)` |
| So sánh thứ tự hai trường unsigned 64 bit | `Long.compareUnsigned(a, b)` |
| Chia unsigned 32 bit | `Integer.divideUnsigned(a, b)` |
| Lấy dư unsigned 32 bit | `Integer.remainderUnsigned(a, b)` |
| Chia/lấy dư unsigned 64 bit | `Long.divideUnsigned(a, b)`, `Long.remainderUnsigned(a, b)` |
| In đúng số unsigned | `Integer.toUnsignedString(x)`, `Long.toUnsignedString(x)` |
| Đọc số unsigned trong chuỗi | `Integer.parseUnsignedInt(s)`, `Long.parseUnsignedLong(s)` |
| Mở rộng unsigned 32 bit thành số dương | `Integer.toUnsignedLong(x)` |
| Biểu diễn unsigned 64 bit như số dương tùy ý | `new BigInteger(Long.toUnsignedString(x))` |

**Ghi nhớ:** Java vẫn lưu `int`/`long` như các kiểu số nguyên có dấu, nhưng `compareUnsigned`, `divideUnsigned` và `remainderUnsigned` thực hiện thao tác theo **cách diễn giải không dấu của cùng mẫu bit**. Phân biệt phép toán với cách hiển thị kết quả, xử lý mẫu số 0 và dùng API chuyển đổi/chuỗi unsigned khi cần giao tiếp với bên ngoài.

**Tài liệu tham khảo:** [Java SE 21 Integer API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html), [Java SE 21 Long API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Long.html), [Java Language Specification §4.2.1 — Integral Types and Values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.1).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-008">parseUnsignedInt, parseUnsignedLong và toUnsignedString</a>

<details>
<summary>Xem chi tiết</summary>

# parseUnsignedInt, parseUnsignedLong và toUnsignedString trong Java

## 1. Mục tiêu và ngữ cảnh

Java có kiểu nguyên thủy `byte`, `short`, `int`, `long` mang dấu; không có kiểu nguyên thủy riêng cho số nguyên không dấu 32 bit hoặc 64 bit. Tuy nhiên, 32 hoặc 64 bit của một giá trị vẫn có thể được **diễn giải là không dấu** thông qua các API của `Integer` và `Long`.

Bài học này giải thích ba thao tác thường đi cùng nhau:

- `Integer.parseUnsignedInt(...)`: đọc chuỗi biểu diễn số không dấu 32 bit và trả về **mẫu bit** dưới dạng `int`.
- `Long.parseUnsignedLong(...)`: đọc chuỗi biểu diễn số không dấu 64 bit và trả về **mẫu bit** dưới dạng `long`.
- `Integer.toUnsignedString(...)` và `Long.toUnsignedString(...)`: diễn giải mẫu bit theo nghĩa không dấu rồi xuất chuỗi.

Điểm quan trọng nhất: **giá trị âm khi in theo kiểu có dấu không có nghĩa việc phân tích unsigned bị sai**. Chính cùng một mẫu bit có hai cách diễn giải.

## 2. Cùng bit, hai cách hiểu

| Bề rộng | Cách hiểu có dấu | Cách hiểu không dấu |
| --- | --- | --- |
| 32 bit (`int`) | −2³¹ đến 2³¹ − 1 | 0 đến 2³² − 1 = 4.294.967.295 |
| 64 bit (`long`) | −2⁶³ đến 2⁶³ − 1 | 0 đến 2⁶⁴ − 1 = 18.446.744.073.709.551.615 |

Java biểu diễn `int` và `long` bằng bù hai (two's complement). Tất cả bit bằng 1 có giá trị có dấu là `-1`, nhưng nếu xem là không dấu thì tương ứng với 2³² − 1 hoặc 2⁶⁴ − 1.

~~~java
int bits32 = -1;
long bits64 = -1L;

System.out.println(bits32);                           // -1
System.out.println(Integer.toUnsignedString(bits32)); // 4294967295
System.out.println(bits64);                           // -1
System.out.println(Long.toUnsignedString(bits64));    // 18446744073709551615
~~~

Đây là *đoạn minh họa đặt trong một phương thức*; chương trình Java chạy hoàn chỉnh nằm ở phần 6. `toUnsignedString` không thay đổi bit trong biến. Nó chỉ chọn cách chuyển các bit thành ký tự thập phân.

Với số có bit dấu bằng 0, ví dụ `42`, chuỗi có dấu và không dấu giống nhau; sự khác biệt thường lộ ra khi bit cao nhất bằng 1.

## 3. Integer.parseUnsignedInt: chuỗi → mẫu bit 32 bit

Hai chữ ký thường dùng:

~~~java
static int Integer.parseUnsignedInt(String s)
static int Integer.parseUnsignedInt(String s, int radix)
~~~

- Nếu không truyền `radix`, hệ cơ số mặc định là **10**.
- `radix` từ **2 đến 36**. Chữ cái trong hệ 16 có thể viết hoa hoặc thường.
- Chuỗi phải biểu diễn giá trị thuộc khoảng **0 đến 4294967295**. Ngoài khoảng, sai chữ số theo cơ số, chuỗi rỗng hoặc dấu trừ sẽ gây `NumberFormatException`.
- Hàm trả về `int`, bởi vậy kết quả có thể âm theo cách hiểu có dấu. Để sử dụng giá trị không dấu chính xác dưới dạng kiểu lớn hơn, gọi `Integer.toUnsignedLong(int)`.

Ví dụ theo từng bước:

~~~java
int value = Integer.parseUnsignedInt("4294967295");
System.out.println(value);                           // -1
System.out.println(Integer.toUnsignedString(value)); // 4294967295
System.out.println(Integer.toUnsignedLong(value));   // 4294967295
~~~

`Integer.toUnsignedLong(value)` tạo `long` trong khoảng 0..4294967295; đây là cách dễ nhất để thực hiện phép tính 32 bit không dấu khi cần không gian số rộng hơn.

Với hệ 16:

~~~java
int hexBits = Integer.parseUnsignedInt("FFFFFFFF", 16);
System.out.println(hexBits);                            // -1
System.out.println(Integer.toUnsignedString(hexBits, 16)); // ffffffff
~~~

Chuỗi `"FFFFFFFF"` không có tiền tố `0x`. Khi chỉ định cơ số 16, truyền `"FF"` chứ không truyền `"0xFF"`; `parseUnsignedInt` không tự phát hiện hoặc loại bỏ tiền tố. Tương tự, chuỗi nhập từ người dùng cần được xử lý phần tiền tố theo quy ước của ứng dụng trước khi parse.

## 4. Long.parseUnsignedLong: chuỗi → mẫu bit 64 bit

Hai chữ ký tương ứng:

~~~java
static long Long.parseUnsignedLong(String s)
static long Long.parseUnsignedLong(String s, int radix)
~~~

Hàm chấp nhận **0 đến 18446744073709551615** (2⁶⁴ − 1), vượt giới hạn dương của `Long.parseLong` là 9223372036854775807. Kết quả vẫn là kiểu `long` có dấu: nếu bit cao nhất bằng 1 thì khi in trực tiếp sẽ ra số âm.

~~~java
long bits = Long.parseUnsignedLong("18446744073709551615");

System.out.println(bits);                            // -1
System.out.println(Long.toUnsignedString(bits));      // 18446744073709551615
System.out.println(Long.toUnsignedString(bits, 16));  // ffffffffffffffff
~~~

Không có kiểu nguyên thủy nào của Java có thể chứa **toàn bộ** giá trị unsigned 64 bit theo nghĩa số dương. Nếu cần biểu diễn như số dương để tính toán vượt 64 bit, dùng `java.math.BigInteger` sau khi chuyển mẫu bit thành chuỗi:

~~~java
import java.math.BigInteger;

// Đoạn minh họa trong phương thức:
long raw = Long.parseUnsignedLong("18446744073709551615");
BigInteger positiveValue = new BigInteger(Long.toUnsignedString(raw));
System.out.println(positiveValue.add(BigInteger.ONE)); // 18446744073709551616
~~~

Cũng có thể tính `BigInteger.valueOf(raw).add(BigInteger.ONE.shiftLeft(64))` khi `raw < 0`. Tuyệt đối không dùng `BigInteger.valueOf(raw)` một mình cho trường hợp `raw` âm nếu mục đích là lấy **giá trị unsigned**.

## 5. toUnsignedString: mẫu bit → chuỗi và cơ số

| API | Đối số đầu vào | Ý nghĩa |
| --- | --- | --- |
| `Integer.toUnsignedString(int value)` | Mẫu bit 32 bit | Chuỗi thập phân unsigned 32 bit |
| `Integer.toUnsignedString(int value, int radix)` | Mẫu bit và cơ số | Chuỗi unsigned 32 bit ở cơ số chỉ định |
| `Long.toUnsignedString(long value)` | Mẫu bit 64 bit | Chuỗi thập phân unsigned 64 bit |
| `Long.toUnsignedString(long value, int radix)` | Mẫu bit và cơ số | Chuỗi unsigned 64 bit ở cơ số chỉ định |

Ví dụ cùng một mẫu bit:

~~~java
int n = 0x8000_0000;
System.out.println(Integer.toString(n));             // -2147483648
System.out.println(Integer.toUnsignedString(n));     // 2147483648
System.out.println(Integer.toUnsignedString(n, 16)); // 80000000
System.out.println(Integer.toUnsignedString(n, 2));  // 10000000000000000000000000000000
~~~

Kết quả `toUnsignedString(value, radix)` **không tự thêm tiền tố** như `0x` hay `0b` và dùng chữ cái thường cho các chữ số lớn hơn 9. Những cơ số không hợp lệ được `toUnsignedString` xử lý theo quy ước của `Integer.toString`/`Long.toString`: dùng hệ 10; khác với hàm parse, vốn báo lỗi khi `radix` nằm ngoài 2..36.

Cặp thao tác lý tưởng khi trao đổi dữ liệu là `parseUnsignedInt` ↔ `Integer.toUnsignedString` hoặc `parseUnsignedLong` ↔ `Long.toUnsignedString` với **cùng cơ số**. Mẫu bit có thể âm nếu quan sát bằng `int`/`long` nhưng vẫn giữ được giá trị unsigned gốc trong phạm vi biểu diễn.

## 6. Ví dụ chạy độc lập trên Java 17+

Tạo tệp `UnsignedNumbersDemo.java`:

~~~java
import java.math.BigInteger;

public class UnsignedNumbersDemo {
    public static void main(String[] args) {
        int maxU32 = Integer.parseUnsignedInt("4294967295");
        long maxU64 = Long.parseUnsignedLong("18446744073709551615");

        System.out.println("u32 as int: " + maxU32);
        System.out.println("u32 decimal: " + Integer.toUnsignedString(maxU32));
        System.out.println("u32 as long: " + Integer.toUnsignedLong(maxU32));
        System.out.println("u32 from hex: " +
                Integer.toUnsignedString(Integer.parseUnsignedInt("FFFFFFFF", 16)));

        System.out.println("u64 as long: " + maxU64);
        System.out.println("u64 decimal: " + Long.toUnsignedString(maxU64));
        System.out.println("u64 hex: " + Long.toUnsignedString(maxU64, 16));

        System.out.println("compare u32 > 1: " +
                (Integer.compareUnsigned(maxU32, 1) > 0));
        System.out.println("u32 / 10: " + Integer.divideUnsigned(maxU32, 10));
        System.out.println("u32 % 10: " + Integer.remainderUnsigned(maxU32, 10));

        System.out.println("u64 / 10: " + Long.divideUnsigned(maxU64, 10L));
        System.out.println("u64 % 10: " + Long.remainderUnsigned(maxU64, 10L));

        BigInteger big = new BigInteger(Long.toUnsignedString(maxU64));
        System.out.println("u64 plus one: " + big.add(BigInteger.ONE));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac UnsignedNumbersDemo.java
java UnsignedNumbersDemo
~~~

Kết quả chuẩn:

~~~text
u32 as int: -1
u32 decimal: 4294967295
u32 as long: 4294967295
u32 from hex: 4294967295
u64 as long: -1
u64 decimal: 18446744073709551615
u64 hex: ffffffffffffffff
compare u32 > 1: true
u32 / 10: 429496729
u32 % 10: 5
u64 / 10: 1844674407370955161
u64 % 10: 5
u64 plus one: 18446744073709551616
~~~

Giải thích:

1. Hai lời gọi `parseUnsigned...` trả về mẫu bit toàn 1, được in trực tiếp thành `-1` theo kiểu có dấu.
2. Hai lời gọi `toUnsignedString` giải mã chính xác mẫu bit theo miền không dấu; lời gọi với `radix = 16` trả chữ `f` thường.
3. `Integer.toUnsignedLong` mở rộng mẫu 32 bit ra số dương `long`, tránh *sign extension* của phép chuyển kiểu `(long) maxU32`.
4. `compareUnsigned`, `divideUnsigned` và `remainderUnsigned` dùng cách hiểu unsigned, nên số lớn nhất 32/64 bit lớn hơn 1 và chia 10 có số dư 5.
5. `BigInteger` cho phép cộng vượt giới hạn unsigned 64 bit mà không tràn.

## 7. Kiểm tra biên, dữ liệu lỗi và radix

Thử các tình huống tiêu biểu:

| Lời gọi | Kết quả |
| --- | --- |
| `Integer.parseUnsignedInt("0")` | `0` |
| `Integer.parseUnsignedInt("2147483648")` | `-2147483648` (bit của 2³¹) |
| `Integer.parseUnsignedInt("4294967295")` | `-1` |
| `Integer.parseUnsignedInt("4294967296")` | `NumberFormatException`: vượt 32 bit unsigned |
| `Long.parseUnsignedLong("9223372036854775808")` | `Long.MIN_VALUE` |
| `Long.parseUnsignedLong("18446744073709551615")` | `-1L` |
| `Long.parseUnsignedLong("18446744073709551616")` | `NumberFormatException`: vượt 64 bit unsigned |
| `Integer.parseUnsignedInt("1010", 2)` | `10` |
| `Long.parseUnsignedLong("ff", 16)` | `255L` |
| `Integer.parseUnsignedInt("-1")` | `NumberFormatException`: dấu âm không hợp lệ |
| `Integer.parseUnsignedInt("0xFF", 16)` | `NumberFormatException`: `0x` không phải chữ số của hệ 16 |
| `Integer.parseUnsignedInt("12 3")` | `NumberFormatException`: có khoảng trắng bên trong |

Chuỗi có thể có dấu cộng ở đầu (`"+42"`) và các số 0 dẫn đầu (`"00042"`); chúng không làm thay đổi giá trị. Tuy nhiên, các hàm parse không tự `trim()` khoảng trắng, không nhận dấu phân cách `_` của cú pháp literal Java, và không chấp nhận dấu trừ ngay cả khi số âm đó có mẫu bit hợp lệ. Chỉ chuẩn hóa dữ liệu đầu vào (ví dụ `trim()` hoặc bỏ tiền tố) khi quy định định dạng đầu vào cho phép.

Phân biệt lỗi của parser và giá trị âm hợp lệ trong kết quả:

~~~java
try {
    int bits = Integer.parseUnsignedInt("4294967296");
    System.out.println(Integer.toUnsignedString(bits));
} catch (NumberFormatException ex) {
    System.out.println("Giá trị không thuộc miền unsigned 32 bit");
}
~~~

Đoạn trên in duy nhất:

~~~text
Giá trị không thuộc miền unsigned 32 bit
~~~

Không nên bắt `NumberFormatException` rồi chuyển mặc định về `0` nếu `0` là một giá trị có ý nghĩa nghiệp vụ; hãy thông báo lỗi nhập hoặc trả kết quả xác thực rõ ràng.

## 8. So sánh và tính toán đúng ngữ nghĩa không dấu

Chỉ parse và xuất chuỗi là chưa đủ nếu ứng dụng còn phải so sánh, chia, chia lấy dư.

~~~java
int a = Integer.parseUnsignedInt("4294967295");
int b = 1;

System.out.println(a < b);                           // true: so sánh signed (-1 < 1)
System.out.println(Integer.compareUnsigned(a, b) > 0); // true: so sánh unsigned
System.out.println(Integer.divideUnsigned(a, 2));     // 2147483647
System.out.println(Integer.remainderUnsigned(a, 2));  // 1
~~~

Đây là đoạn minh họa trong phương thức. `Long` có API tương ứng: `Long.compareUnsigned`, `Long.divideUnsigned`, `Long.remainderUnsigned`. Phép `+`, `-`, `*` trên `int`/`long` giữ các bit thấp và có thể tràn (wrap modulo 2³²/2⁶⁴); Java không tự báo tràn cho các phép này. Nếu nghiệp vụ yêu cầu phát hiện tràn **trong miền unsigned**, cần kiểm tra riêng hoặc dùng `BigInteger` để tính rồi kiểm tra giới hạn.

Với **unsigned 32 bit**, cũng có thể mở rộng cả hai toán hạng bằng `Integer.toUnsignedLong` rồi dùng các phép so sánh/chia có dấu trên `long`, vì mọi unsigned 32 bit đều nằm trong miền dương của `long`. Mẹo tương tự không áp dụng trọn vẹn cho unsigned 64 bit: cần API unsigned của `Long` hoặc `BigInteger`.

## 9. Áp dụng: mã nhận diện 32 bit từ giao thức hoặc tệp nhị phân

Một giao thức có thể quy định trường `requestId` là số không dấu 32 bit. Giá trị `4026531841` có mẫu bit `0xF0000001`; nếu lưu bằng `int`, Java nhìn nó thành số âm. Ta vẫn có thể truyền và ghi lại chính xác chuỗi thập phân unsigned:

~~~java
int requestIdBits = Integer.parseUnsignedInt("4026531841");
String wireText = Integer.toUnsignedString(requestIdBits);

System.out.println(requestIdBits); // -268435455
System.out.println(wireText);      // 4026531841
~~~

Đây là đoạn minh họa trong phương thức. Khi nhận dữ liệu từ luồng byte, thứ tự byte (*endianness*) và việc ghép bit là vấn đề riêng; `parseUnsignedInt` nhận **chuỗi chữ số**, không giải mã trực tiếp mảng byte hay xử lý byte order. Nếu cần đọc một byte như 0..255, dùng `Byte.toUnsignedInt(byteValue)` để tránh ảnh hưởng của sign extension.

## 10. Lỗi thường gặp và cách sửa

| Sai lầm | Hệ quả | Cách đúng |
| --- | --- | --- |
| Dùng `Integer.parseInt("4294967295")` | Vượt `Integer.MAX_VALUE` | `Integer.parseUnsignedInt` |
| Dùng `Long.parseLong` cho 2⁶⁴ − 1 | Vượt `Long.MAX_VALUE` | `Long.parseUnsignedLong` |
| In `System.out.println(bits)` rồi cho rằng dữ liệu sai | Thấy số âm | `Integer.toUnsignedString` hoặc `Long.toUnsignedString` |
| Ép `(long) intBits` để lấy unsigned 32 bit | Âm do sign extension | `Integer.toUnsignedLong(intBits)` |
| So sánh `a < b` với các bit unsigned | Dùng thứ tự signed | `compareUnsigned(a, b)` |
| Chia `a / b` khi toán hạng có bit dấu bằng 1 | Dùng phép chia signed | `divideUnsigned` và `remainderUnsigned` |
| Truyền `"0xFF"` cùng radix 16 | Parse thất bại | Bỏ tiền tố, truyền `"FF"` |
| Để số nguyên lớn thành literal `18446744073709551615L` | Không biên dịch | Parse từ chuỗi; dùng `BigInteger` khi cần số dương |
| Mong parse tự loại bỏ khoảng trắng hoặc `_` | `NumberFormatException` | Chuẩn hóa đầu vào có kiểm soát |

## 11. Tự kiểm tra

**Câu 1.** Vì sao `Integer.parseUnsignedInt("4294967295")` trả `-1` nhưng không ném lỗi?

Vì chuỗi hợp lệ với số không dấu 32 bit; hàm trả lại đúng 32 bit toàn 1 dưới kiểu `int` có dấu. `Integer.toUnsignedString` sẽ chuyển chính 32 bit ấy thành `"4294967295"`.

**Câu 2.** Làm cách nào nhận unsigned 32 bit lớn nhất thành số dương để cộng với 1 mà không mất dữ liệu?

~~~java
int raw = Integer.parseUnsignedInt("4294967295");
long sum = Integer.toUnsignedLong(raw) + 1L;
System.out.println(sum); // 4294967296
~~~

Đây là đoạn minh họa trong phương thức; `long` có đủ miền dương cho kết quả này.

**Câu 3.** Có thể làm phép cộng `+1` trên unsigned 64 bit lớn nhất và nhận số dương 18446744073709551616 dưới kiểu `long` không?

Không. Kiểu `long` không chứa được số dương đó. Chuyển sang `BigInteger` theo cách ở phần 4 trước khi cộng.

**Câu 4.** Nếu input là `"ffffffffffffffff"` theo hệ 16, nên dùng API nào để giữ đủ mẫu bit?

`Long.parseUnsignedLong("ffffffffffffffff", 16)`, rồi `Long.toUnsignedString(result, 16)` để xuất lại. Kết quả xuất là `"ffffffffffffffff"`.

## 12. Ghi nhớ

- `parseUnsignedInt` và `parseUnsignedLong` **kiểm tra miền giá trị unsigned**, trả về mẫu bit trong kiểu có dấu tương ứng.
- `toUnsignedString` **xuất các bit theo miền unsigned**, không thay đổi mẫu bit đã lưu.
- Với 32 bit, `Integer.toUnsignedLong` giúp chuyển thành số dương `long`; với 64 bit cần `BigInteger` nếu muốn số dương vượt `Long.MAX_VALUE`.
- Cơ số khi parse và khi xuất phải phù hợp với định dạng dữ liệu. Chú ý các điều kiện lỗi, phép so sánh/chia unsigned và tràn số khi tính toán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-009">bitCount, leading zeros, trailing zeros và one-bit helpers</a>

<details>
<summary>Xem chi tiết</summary>

# `bitCount`, leading zeros, trailing zeros và one-bit helpers trong Java

## 1. Mục tiêu và mô hình bit

Trong Java, `int` luôn có **32 bit** và `long` luôn có **64 bit** (`Integer.SIZE == 32`, `Long.SIZE == 64`). Các phép toán bit xử lý **mẫu bit có độ rộng cố định**, kể cả khi giá trị được diễn giải là số âm theo biểu diễn bù hai (two's complement). Các hàm tiện ích của `Integer` và `Long` giúp đếm bit 1, xác định vị trí bit 1, hoặc cô lập bit 1 ở đầu/cuối mà không cần tự dịch bit qua từng vị trí.

Ví dụ, số `40` có dạng nhị phân `101000`. Khi xem dưới dạng `int`, nó có **26 bit 0 ở bên trái**, sau đó là `101000`; hai bit 1 nằm tại vị trí 5 và 3 (đánh số từ phải sang, bắt đầu từ 0). Vì vậy:

- `Integer.bitCount(40)` trả `2`.
- `Integer.numberOfLeadingZeros(40)` trả `26`.
- `Integer.numberOfTrailingZeros(40)` trả `3`.
- `Integer.highestOneBit(40)` trả `32` (bit 1 tại vị trí 5).
- `Integer.lowestOneBit(40)` trả `8` (bit 1 tại vị trí 3).

Các phương thức dưới đây là `static`, không thay đổi đối số và không phụ thuộc việc bạn in giá trị theo hệ thập phân, thập lục phân hay nhị phân.

## 2. Tổng quan API

| `Integer` (`int`, 32 bit) | `Long` (`long`, 64 bit) | Ý nghĩa |
| --- | --- | --- |
| `Integer.bitCount(x)` | `Long.bitCount(x)` | Số lượng bit bằng 1 (population count, **popcount**) trong toàn bộ mẫu bit. |
| `Integer.numberOfLeadingZeros(x)` | `Long.numberOfLeadingZeros(x)` | Số bit 0 liên tiếp **từ bit cao nhất** đến trước bit 1 đầu tiên. |
| `Integer.numberOfTrailingZeros(x)` | `Long.numberOfTrailingZeros(x)` | Số bit 0 liên tiếp **từ bit thấp nhất** đến trước bit 1 đầu tiên. |
| `Integer.highestOneBit(x)` | `Long.highestOneBit(x)` | Giá trị chỉ giữ lại **bit 1 cao nhất**; các bit khác bằng 0. |
| `Integer.lowestOneBit(x)` | `Long.lowestOneBit(x)` | Giá trị chỉ giữ lại **bit 1 thấp nhất**; các bit khác bằng 0. |

Các kết quả đếm luôn là `int`, kể cả với `Long`. Hai hàm `highestOneBit`/`lowestOneBit` trả lại đúng kiểu đầu vào (`int` hoặc `long`). Không có giá trị trả về `-1` để báo không tìm thấy bit 1: hai hàm *one-bit* trả `0`; hai hàm đếm số bit 0 trả **độ rộng bit** khi đầu vào bằng `0`.

### Các trường hợp biên cần nhớ

| Đầu vào `int` | `bitCount` | Leading zeros | Trailing zeros | `highestOneBit` | `lowestOneBit` |
| --- | ---: | ---: | ---: | ---: | ---: |
| `0` | 0 | 32 | 32 | 0 | 0 |
| `40` (`0b101000`) | 2 | 26 | 3 | 32 | 8 |
| `-1` (32 bit 1) | 32 | 0 | 0 | `Integer.MIN_VALUE` | 1 |
| `Integer.MIN_VALUE` (chỉ bit dấu bằng 1) | 1 | 0 | 31 | `Integer.MIN_VALUE` | `Integer.MIN_VALUE` |

Với `long`, thay độ rộng `32` bằng `64`. Chẳng hạn `Long.numberOfTrailingZeros(0L) == 64`, `Long.bitCount(-1L) == 64`, và `Long.highestOneBit(-1L) == Long.MIN_VALUE`.

## 3. `bitCount`: đếm số bit 1

`Integer.bitCount(x)` và `Long.bitCount(x)` trả số bit 1 trong toàn bộ 32/64 bit. Hàm này **không** đếm chữ số `1` trong chuỗi thập phân; cũng **không** đếm số bit cần dùng để biểu diễn độ lớn của số.

```java
int a = 0b1010_0101; // Bốn bit 1
int count = Integer.bitCount(a); // 4
long b = 0b1_0000_0000L;
int longCount = Long.bitCount(b); // 1
```

Số âm phải được tính theo bù hai: `Integer.bitCount(-1) == 32`, còn `Long.bitCount(-1L) == 64`. Điều này hữu ích khi làm việc với cờ bit (flags), bitmap, bitset nén trong số nguyên hoặc thuật toán Hamming distance: `Integer.bitCount(a ^ b)` là số vị trí bit khác nhau giữa hai mẫu `int`.

Ví dụ, nếu `permissions` là một tập cờ và `mask` chỉ định các quyền cần kiểm tra, `Integer.bitCount(permissions & mask)` đếm số quyền đã bật **trong phạm vi `mask`**. Đối với ứng dụng cần tập bit lớn hơn 64 bit, `java.util.BitSet` phù hợp hơn; `BitSet.cardinality()` đếm số bit 1 của tập đó.

## 4. Leading zeros: đếm bit 0 từ đầu cao

`numberOfLeadingZeros(x)` bắt đầu từ bit cao nhất (bit 31 của `int`, bit 63 của `long`) và đếm đến trước bit 1 đầu tiên. Một số dương có bit 1 cao nhất ở vị trí `p` thì:

- Với `int`: `p = 31 - Integer.numberOfLeadingZeros(x)` khi `x != 0`.
- Với `long`: `p = 63 - Long.numberOfLeadingZeros(x)` khi `x != 0`.

Với `x > 0`, số bit tối thiểu để biểu diễn dạng nhị phân không dấu chính là `Integer.SIZE - Integer.numberOfLeadingZeros(x)`. Ví dụ `40` cần 6 bit (`101000`), nên `32 - 26 = 6`.

Với `x == 0`, Java trả `32` hoặc `64` vì toàn bộ bit đều bằng 0; phải xử lý riêng nếu bài toán cần vị trí một bit 1. Với số âm, bit dấu cao nhất luôn bằng 1, nên số leading zeros bằng **0**, dù giá trị tuyệt đối lớn hay nhỏ.

```java
int x = 40;
int highestSetIndex = x == 0
        ? -1
        : Integer.SIZE - 1 - Integer.numberOfLeadingZeros(x);
// highestSetIndex == 5
```

Dùng `-1` trong đoạn trên là **quy ước do ứng dụng tự chọn** cho trường hợp không có bit 1, không phải giá trị trả trực tiếp của `numberOfLeadingZeros`.

## 5. Trailing zeros: đếm bit 0 từ đầu thấp

`numberOfTrailingZeros(x)` đếm bit 0 liên tiếp từ bit thấp nhất (bit 0) đến trước bit 1 đầu tiên. Khi `x != 0`, kết quả chính là **chỉ số của bit 1 thấp nhất**:

```java
int mask = 0b0010_1000; // 40
int lowestSetIndex = Integer.numberOfTrailingZeros(mask); // 3
```

Một số nguyên khác 0 chia hết cho `2^k` khi và chỉ khi ít nhất `k` bit thấp nhất bằng 0. Vì vậy trailing zeros cho biết số thừa số 2 của một số nguyên khác 0 theo biểu diễn bit (ví dụ `40 = 5 × 2^3`, nên có 3 bit 0 ở cuối). Công thức vẫn hữu ích với số âm khi cần thao tác trên mẫu bit, nhưng không dùng để suy ra dấu.

`Integer.numberOfTrailingZeros(0) == 32`, `Long.numberOfTrailingZeros(0L) == 64`. Đây là sentinel hợp lệ của API: không được đưa trực tiếp `32` vào `1 << index` với kỳ vọng tạo bit ở vị trí 32.

Trong bài toán liệt kê các bit đang bật, trailing zeros tìm ngay chỉ số bit thấp nhất; sau đó có thể xóa bit ấy và tiếp tục đến khi mẫu bit bằng 0.

## 6. `highestOneBit` và `lowestOneBit`: tạo mặt nạ chỉ có một bit 1

Hai hàm này trả về một **mặt nạ one-hot**: chỉ bit 1 cao nhất hoặc thấp nhất còn được giữ lại.

```java
int x = 0b0010_1000;          // 40
int high = Integer.highestOneBit(x); // 0b0010_0000 = 32
int low  = Integer.lowestOneBit(x);  // 0b0000_1000 = 8
```

Nếu `x == 0`, cả hai đều trả `0`. Nếu `x` âm, các hàm vẫn đọc toàn bộ mẫu 32/64 bit, không bỏ bit dấu. Do đó:

```java
int highNegative = Integer.highestOneBit(-1);
// Integer.MIN_VALUE, tức 0x80000000, hiển thị dạng int là -2147483648

long highNegativeLong = Long.highestOneBit(-1L);
// Long.MIN_VALUE, tức 0x8000000000000000
```

**Ý nghĩa kết quả phụ thuộc cách diễn giải signed/unsigned.** Bit dấu ở vị trí 31 biểu diễn mặt nạ `0x80000000`; nếu in như `int`, giá trị âm, nhưng nếu in như số nguyên không dấu thì đó là `2147483648`:

```java
String unsignedText =
        Integer.toUnsignedString(Integer.highestOneBit(-1));
// "2147483648"
```

Với **số dương** `x`, `highestOneBit(x)` là lũy thừa của 2 lớn nhất không vượt quá `x`; còn `lowestOneBit(x)` là lũy thừa của 2 ứng với bit 1 thấp nhất. Không đồng nhất `highestOneBit` với phép làm tròn lên lũy thừa 2: ví dụ `highestOneBit(40) == 32`, trong khi lũy thừa 2 tiếp theo là `64`.

Có thể nhận ra hai phép biến đổi bit tương đương khi thao tác trên mẫu bù hai:

- `x & -x` cô lập bit 1 thấp nhất, tương đương `lowestOneBit(x)`.
- `x & (x - 1)` **xóa** bit 1 thấp nhất, không tương đương `lowestOneBit(x)`.

Phép trừ một và đổi dấu ở đây dùng số nguyên Java có độ rộng cố định. Đối với mã ứng dụng, các phương thức tên rõ ràng của JDK thường dễ đọc và ít gây nhầm hơn.

## 7. Ví dụ Java 17 đầy đủ, chạy được

Lưu thành tệp `BitHelpersDemo.java`:

```java
public class BitHelpersDemo {
    public static void main(String[] args) {
        int x = 0b0010_1000; // 40: các bit 1 ở vị trí 5 và 3

        System.out.println("bitCount(40) = " + Integer.bitCount(x));
        System.out.println("leadingZeros(40) = "
                + Integer.numberOfLeadingZeros(x));
        System.out.println("trailingZeros(40) = "
                + Integer.numberOfTrailingZeros(x));
        System.out.println("highestOneBit(40) = "
                + Integer.highestOneBit(x));
        System.out.println("lowestOneBit(40) = "
                + Integer.lowestOneBit(x));

        System.out.println("leadingZeros(0) = "
                + Integer.numberOfLeadingZeros(0));
        System.out.println("trailingZeros(0) = "
                + Integer.numberOfTrailingZeros(0));
        System.out.println("bitCount(-1) = "
                + Integer.bitCount(-1));
        System.out.println("highestOneBit(-1), signed = "
                + Integer.highestOneBit(-1));
        System.out.println("highestOneBit(-1), unsigned = "
                + Integer.toUnsignedString(Integer.highestOneBit(-1)));

        long large = 1L << 40;
        System.out.println("Long.bitCount(2^40) = "
                + Long.bitCount(large));
        System.out.println("Long.leadingZeros(2^40) = "
                + Long.numberOfLeadingZeros(large));
        System.out.println("Long.trailingZeros(2^40) = "
                + Long.numberOfTrailingZeros(large));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac BitHelpersDemo.java
java BitHelpersDemo
```

Đầu ra chính xác:

```text
bitCount(40) = 2
leadingZeros(40) = 26
trailingZeros(40) = 3
highestOneBit(40) = 32
lowestOneBit(40) = 8
leadingZeros(0) = 32
trailingZeros(0) = 32
bitCount(-1) = 32
highestOneBit(-1), signed = -2147483648
highestOneBit(-1), unsigned = 2147483648
Long.bitCount(2^40) = 1
Long.leadingZeros(2^40) = 23
Long.trailingZeros(2^40) = 40
```

Giải thích phép dịch `1L << 40`: chữ `L` đảm bảo phép dịch thực hiện trên `long` 64 bit. Kết quả có một bit 1 ở vị trí 40, nên có 40 bit 0 bên phải, 23 bit 0 bên trái và tổng cộng một bit 1.

## 8. Ứng dụng: duyệt nhanh từng cờ bit đang bật

Giả sử mỗi bit biểu diễn một tính năng được bật. Mẫu `0b101010` có các bit 1 tại vị trí 1, 3, 5. Có thể dùng `numberOfTrailingZeros` để tìm vị trí rồi xóa chính bit thấp nhất bằng `mask &= mask - 1`.

Lưu thành tệp `ActiveBitsDemo.java`:

```java
public class ActiveBitsDemo {
    public static void main(String[] args) {
        int mask = 0b101010;

        while (mask != 0) {
            int index = Integer.numberOfTrailingZeros(mask);
            System.out.println("Bit bat: " + index);
            mask &= mask - 1;
        }
    }
}
```

```bash
javac ActiveBitsDemo.java
java ActiveBitsDemo
```

```text
Bit bat: 1
Bit bat: 3
Bit bat: 5
```

Vòng lặp thực hiện đúng một lần cho mỗi bit 1, tức `Integer.bitCount(maskBanDau)` lượt. Nếu `mask` có bit dấu đang bật, cách này vẫn xử lý được vị trí 31; điều kiện `mask != 0` kiểm tra **mẫu bit**, không đòi hỏi giá trị phải dương. Để kiểm tra một bit cụ thể:

```java
int flags = 0b101010;
int index = 3;
boolean enabled = (flags & (1 << index)) != 0; // true
```

Ở đây `index` phải thuộc `0..31` với `int`; với `long`, dùng `1L << index` và giới hạn `0..63`.

## 9. Một số mẫu sử dụng hữu ích

### Kiểm tra mẫu bit có đúng một bit 1

```java
static boolean hasExactlyOneBitSet(int x) {
    return Integer.bitCount(x) == 1;
}
```

Hàm nhận cả `Integer.MIN_VALUE` là mẫu có đúng một bit 1. Nếu mục tiêu là kiểm tra **số nguyên dương** có phải lũy thừa của 2 hay không, cần thêm điều kiện `x > 0`:

```java
static boolean isPositivePowerOfTwo(int x) {
    return x > 0 && Integer.bitCount(x) == 1;
}
```

Hoặc dùng `x > 0 && (x & (x - 1)) == 0`. Điều kiện `x > 0` tránh nhầm `0` và số âm có duy nhất bit dấu. Với `long`, thay `Integer.bitCount` bằng `Long.bitCount`.

### Lấy chỉ số bit cao nhất và thấp nhất

```java
static int highestSetBitIndex(int x) {
    return x == 0 ? -1
            : Integer.SIZE - 1 - Integer.numberOfLeadingZeros(x);
}

static int lowestSetBitIndex(int x) {
    return x == 0 ? -1 : Integer.numberOfTrailingZeros(x);
}
```

Các hàm trả `-1` cho `0` theo quy ước riêng. Chúng có thể dùng với cả `int` âm vì chỉ số nói về **vị trí trong mẫu bit**. Ví dụ, `highestSetBitIndex(Integer.MIN_VALUE) == 31`.

### Đếm số bit khác nhau của hai số

```java
int a = 0b1010;
int b = 0b0011;
int hammingDistance = Integer.bitCount(a ^ b); // 2
```

XOR cho bit 1 tại những vị trí khác nhau; `bitCount` đếm chúng. Đây là phép so sánh 32 bit (hoặc 64 bit với `Long.bitCount`), kể cả với số âm.

## 10. Lưu ý và lỗi thường gặp

1. **Quên độ rộng bit cố định.** `bitCount(-1)` là `32`/`64`, không phải `1`, vì `-1` ở dạng bù hai toàn bit 1.
2. **Bỏ qua `0`.** `numberOfLeadingZeros(0)` và `numberOfTrailingZeros(0)` trả `Integer.SIZE`/`Long.SIZE`; còn `highestOneBit(0)` và `lowestOneBit(0)` đều trả `0`. Cần xử lý `0` trước khi tính chỉ số bit hay dịch `1` theo kết quả.
3. **Lẫn giữa vị trí và mặt nạ.** `numberOfTrailingZeros(40)` là **vị trí** `3`; `lowestOneBit(40)` là **giá trị mặt nạ** `8`. Tương tự, `highestOneBit(40)` là `32`, còn chỉ số bit cao nhất là `5`.
4. **Hiểu nhầm dấu của `highestOneBit` ở số âm.** Hàm có thể trả `Integer.MIN_VALUE` hoặc `Long.MIN_VALUE`; bit dấu vẫn là một bit 1 bình thường khi thao tác bit. Dùng `Integer.toUnsignedString`/`Long.toUnsignedString` nếu cần hiển thị không dấu.
5. **Dùng toán tử dịch với sai kiểu hoặc chỉ số ngoài biên.** `1 << 40` thực hiện trên `int` và Java chỉ dùng 5 bit thấp của lượng dịch, nên không tạo bit ở vị trí 40. Dùng `1L << 40` cho `long`; giới hạn chỉ số dịch hợp lệ theo nhu cầu `0..31`/`0..63`.
6. **Quên ưu tiên toán tử.** Nên viết `(x & (x - 1)) == 0` và `(flags & (1 << index)) != 0` với ngoặc rõ ràng.
7. **Dùng bit trick cho số 0 khi kiểm tra lũy thừa 2.** `(0 & (0 - 1)) == 0` đúng, nhưng `0` không phải lũy thừa 2. Bắt buộc kiểm tra `x > 0` nếu đang xét số nguyên dương.
8. **Nhầm leading/trailing zeros với bit đầu/cuối của chuỗi.** `Integer.toBinaryString(40)` trả `"101000"` (không tự đệm 26 số 0 đầu). Kết quả `numberOfLeadingZeros` vẫn tính trên toàn bộ 32 bit.

Các phương thức này diễn đạt rõ thao tác bit và thường được JVM tối ưu tốt, đôi khi bằng chỉ thị máy chuyên dụng; không nên mặc định chúng có tốc độ cố định trên mọi nền tảng hoặc tự thay thế bằng vòng lặp thủ công nếu chưa có phép đo.

## 11. Tự kiểm tra

1. Với `0b1001_0000` (`144`), `bitCount` bằng bao nhiêu? `numberOfTrailingZeros` bằng bao nhiêu? `highestOneBit` và `lowestOneBit` bằng bao nhiêu?
2. Vì sao `Integer.numberOfLeadingZeros(-8)` bằng `0`, dù `Integer.toBinaryString(-8)` dài 32 ký tự?
3. Vì sao `Integer.bitCount(Integer.MIN_VALUE) == 1` nhưng `Integer.MIN_VALUE` không phải lũy thừa 2 **dương**?
4. Điều gì xảy ra nếu lỡ tính `1 << Integer.numberOfTrailingZeros(0)`?

**Đáp án:** (1) Lần lượt `2`, `4`, `128`, `16`. (2) Bit dấu (bit 31) của mọi `int` âm bằng 1 nên không có bit 0 ở đầu. (3) Chỉ có bit dấu bật, giá trị được diễn giải là số âm. (4) Dịch `1 << 32` trên `int` tương đương `1 << 0`, cho `1`, vì Java chỉ xét 5 bit thấp của số lượng dịch; đó là lý do cần xử lý `0` trước.

**Ghi nhớ:** `bitCount` trả **số bit 1**; leading/trailing zeros trả **số bit 0 liên tiếp từ hai đầu mẫu bit**; `highestOneBit`/`lowestOneBit` trả **mặt nạ chứa đúng một bit 1** hoặc `0`. Luôn phân biệt kết quả là *số lượng*, *vị trí*, hay *mặt nạ*, và kiểm tra `0` trước khi suy ra chỉ số.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-010">rotateLeft, rotateRight, reverse và reverseBytes</a>

<details>
<summary>Xem chi tiết</summary>

# rotateLeft, rotateRight, reverse và reverseBytes trong Java

## 1. Bốn phép biến đổi bit giải quyết vấn đề gì?

Khi làm việc với số nguyên ở mức bit, ta thường cần xoay một dãy bit theo vòng tròn, đảo thứ tự từng bit hoặc đổi thứ tự các byte. Java cung cấp sẵn các phương thức tĩnh trên **Integer** (32 bit) và **Long** (64 bit):

| Phương thức | Ý nghĩa với dãy có độ rộng cố định | Có làm mất bit không? |
| --- | --- | --- |
| **Integer.rotateLeft(x, distance)** / **Long.rotateLeft(x, distance)** | Xoay toàn bộ bit sang trái; bit đi ra đầu trái quay về đầu phải | Không |
| **Integer.rotateRight(x, distance)** / **Long.rotateRight(x, distance)** | Xoay toàn bộ bit sang phải; bit đi ra đầu phải quay về đầu trái | Không |
| **Integer.reverse(x)** / **Long.reverse(x)** | Đảo thứ tự **mọi bit** từ đầu đến cuối | Không |
| **Integer.reverseBytes(x)** / **Long.reverseBytes(x)** | Đảo thứ tự **các byte**, giữ nguyên thứ tự 8 bit bên trong mỗi byte | Không |

Đây đều là hàm **trả về giá trị mới**: chúng không sửa biến được truyền vào. Mỗi phương thức bảo toàn số bit 1 và số bit 0 của toán hạng; thay đổi nằm ở **vị trí** các bit.

Trong Java, kiểu **int** luôn có 32 bit, **long** luôn có 64 bit và đều là kiểu **có dấu** theo biểu diễn bù hai (two's complement). Java không có kiểu nguyên thủy **unsigned int** hay **unsigned long**, nhưng các phương thức trên xử lý **mẫu bit** mà không quan tâm cách diễn giải dấu. Ví dụ, mẫu **0x80000000** là một số int âm, song bit cao nhất vẫn tham gia xoay/đảo như mọi bit khác.

## 2. Xoay bit khác dịch bit như thế nào?

Giả sử chỉ có tám bit minh họa:

~~~text
Giá trị gốc:         1000 0001
Dịch trái 1 bit:     0000 0010   (bit bên trái bị loại)
Xoay trái 1 bit:     0000 0011   (bit bên trái quay về bên phải)
Dịch phải logic 1:   0100 0000   (bit bên phải bị loại)
Xoay phải 1 bit:     1100 0000   (bit bên phải quay về bên trái)
~~~

Minh họa 8 bit này **không phải** kết quả trực tiếp của **Integer.rotateLeft** trên giá trị 0x81: **Integer** luôn xoay trên **32 bit**. Muốn xoay đúng một byte, cần mã riêng ở mục 6.

Ba toán tử Java cần phân biệt:

- **<<**: dịch trái, bỏ các bit tràn ra ngoài, thêm bit 0 bên phải.
- **>>**: dịch phải số học, lặp lại bit dấu ở phía trái.
- **>>>**: dịch phải logic, thêm bit 0 ở phía trái.

Phép xoay ghép phần đã dịch ra ngoài trở về đầu đối diện nên không mất thông tin. Với độ rộng 32 bit và khoảng xoay d, có thể hình dung:

~~~text
rotateLeft(x, d)  = (x << d) | (x >>> (32 - d))
rotateRight(x, d) = (x >>> d) | (x << (32 - d))
~~~

Đây là công thức mô tả cho **d đã chuẩn hóa trong khoảng 0..31**; sử dụng trực tiếp **Integer.rotateLeft/rotateRight** rõ ý định hơn và tự xử lý giá trị khoảng xoay bất kỳ. Với **long**, công thức tương tự dùng 64 bit. Trong Java, khoảng dịch của **int** chỉ xét 5 bit thấp, còn **long** chỉ xét 6 bit thấp; công thức minh họa không thay thế việc hiểu đúng độ rộng toán hạng.

## 3. rotateLeft và rotateRight: xoay vòng trên 32/64 bit

Với **int**, khoảng xoay có hiệu lực bằng **distance modulo 32**; với **long** là **distance modulo 64**. Do đó:

- **Integer.rotateLeft(x, 40)** tương đương **Integer.rotateLeft(x, 8)**.
- **Integer.rotateLeft(x, -8)** tương đương **Integer.rotateRight(x, 8)**.
- Xoay 0, 32 hoặc bội của 32 trên int trả về đúng mẫu bit ban đầu.
- Với long, mốc vòng quay là 64, không phải 32.

Khoảng xoay âm là hợp lệ: nó có tác dụng xoay theo chiều ngược lại. Không cần tự lấy trị tuyệt đối trước khi gọi hàm.

Ví dụ với mẫu 32 bit **0x12345678**:

~~~text
Gốc:                12 34 56 78
rotateLeft(x, 8):   34 56 78 12
rotateRight(x, 8):  78 12 34 56
~~~

Xoay một khoảng đúng bội của 8 khiến các nhóm byte trông như chuyển vị trí, nhưng **rotateLeft/rotateRight** vẫn là phép xoay trên cả dãy bit; khoảng xoay không nhất thiết phải chia hết cho 8.

### Ví dụ Java 17+ có thể chạy trực tiếp

Lưu thành **BitTransforms.java**:

~~~java
public class BitTransforms {
    public static void main(String[] args) {
        int x = 0x12345678;

        System.out.printf("int x: %08X%n", x);
        System.out.printf("int left8: %08X%n", Integer.rotateLeft(x, 8));
        System.out.printf("int right8: %08X%n", Integer.rotateRight(x, 8));
        System.out.printf("int reverse: %08X%n", Integer.reverse(x));
        System.out.printf("int reverseBytes: %08X%n", Integer.reverseBytes(x));

        System.out.println("negative distance: "
                + (Integer.rotateLeft(x, -8) == Integer.rotateRight(x, 8)));
        System.out.println("distance modulo 32: "
                + (Integer.rotateLeft(x, 40) == Integer.rotateLeft(x, 8)));
        System.out.println("rotation round trip: "
                + (Integer.rotateRight(Integer.rotateLeft(x, 8), 8) == x));
        System.out.println("reverse round trip: "
                + (Integer.reverse(Integer.reverse(x)) == x));
        System.out.println("reverseBytes round trip: "
                + (Integer.reverseBytes(Integer.reverseBytes(x)) == x));

        System.out.printf("reverse(1) hex: %08X%n", Integer.reverse(1));
        System.out.println("reverse(1) signed: " + Integer.reverse(1));

        long y = 0x0123456789ABCDEFL;
        System.out.printf("long x: %016X%n", y);
        System.out.printf("long left8: %016X%n", Long.rotateLeft(y, 8));
        System.out.printf("long right8: %016X%n", Long.rotateRight(y, 8));
        System.out.printf("long reverse: %016X%n", Long.reverse(y));
        System.out.printf("long reverseBytes: %016X%n", Long.reverseBytes(y));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BitTransforms.java
java BitTransforms
~~~

Kết quả chuẩn (stdout):

~~~text
int x: 12345678
int left8: 34567812
int right8: 78123456
int reverse: 1E6A2C48
int reverseBytes: 78563412
negative distance: true
distance modulo 32: true
rotation round trip: true
reverse round trip: true
reverseBytes round trip: true
reverse(1) hex: 80000000
reverse(1) signed: -2147483648
long x: 0123456789ABCDEF
long left8: 23456789ABCDEF01
long right8: EF0123456789ABCD
long reverse: F7B3D591E6A2C480
long reverseBytes: EFCDAB8967452301
~~~

**%08X** hiển thị int dưới dạng 8 chữ số hexa với số 0 bổ sung; **%016X** tương ứng 16 chữ số cho long. Nhờ vậy ta so sánh **mẫu bit** đầy đủ, kể cả số âm. Kết quả **Integer.reverse(1)** là **0x80000000**, có giá trị thập phân có dấu **-2147483648**; điều này không có nghĩa phép reverse tạo ra hay làm mất bit.

## 4. reverse: đảo thứ tự toàn bộ bit

**Integer.reverse(x)** ánh xạ bit ở vị trí **i** (đếm từ bit thấp nhất, bắt đầu từ 0) tới vị trí **31 - i**. **Long.reverse(x)** ánh xạ **i** tới **63 - i**.

Ví dụ trực quan trên một byte minh họa:

~~~text
Bit gốc:     1101 0010
Đảo bit:     0100 1011
~~~

Trên int, đảo diễn ra với **đúng 32 bit**; trên long là **64 bit**. Một bit thấp nhất của int trở thành bit dấu:

~~~text
Integer.reverse(0x00000001) = 0x80000000
Integer.reverse(0x80000000) = 0x00000001
~~~

**reverse** là phép đối xứng: áp dụng hai lần khôi phục nguyên mẫu bit, tức **reverse(reverse(x)) == x**. Nó cũng bảo toàn số lượng bit 1, nhưng thường **không** bảo toàn giá trị số học hay dấu.

Các trường hợp hữu ích gồm chuẩn hóa thứ tự bit theo một định dạng nhị phân được mô tả rõ, các thuật toán bitboard, thao tác thứ tự bit cho giao thức hoặc bước biến đổi trong thuật toán bit. Cần xác nhận rõ **bit order** của định dạng vì nhiều định dạng lưu **byte order** khác với **bit order**.

## 5. reverseBytes: đảo thứ tự byte, không đảo bit trong byte

Một byte có 8 bit. **Integer.reverseBytes** đổi chỗ bốn byte trên 32 bit theo mẫu:

~~~text
Gốc (32 bit):
00010010 00110100 01010110 01111000
   12       34       56       78

reverseBytes:
01111000 01010110 00110100 00010010
   78       56       34       12
~~~

**Long.reverseBytes** thực hiện tương tự với tám byte của long. Trong mỗi byte, thứ tự các bit vẫn giữ nguyên. Vì vậy:

~~~text
Integer.reverseBytes(0x00000001) = 0x01000000
Integer.reverse(0x00000001)      = 0x80000000
~~~

Hai kết quả khác nhau ở cả vị trí và ý nghĩa. Với **0x12345678**:

| Biến đổi | Kết quả hex | Điều xảy ra |
| --- | --- | --- |
| Gốc | **12345678** | Thứ tự 4 byte: 12 34 56 78 |
| **rotateLeft(x, 8)** | **34567812** | Xoay vòng 8 bit sang trái |
| **rotateRight(x, 8)** | **78123456** | Xoay vòng 8 bit sang phải |
| **reverse(x)** | **1E6A2C48** | Đảo toàn bộ 32 bit |
| **reverseBytes(x)** | **78563412** | Đảo thứ tự 4 byte |

**reverseBytes(reverseBytes(x)) == x** vì đảo thứ tự byte hai lần trở về ban đầu.

### Liên hệ với endian

**Big-endian** lưu byte quan trọng nhất trước; **little-endian** lưu byte ít quan trọng nhất trước. Đảo thứ tự byte có thể hữu ích khi **chuyển đổi biểu diễn** của một giá trị giữa hai quy ước byte order. Tuy nhiên:

- **reverseBytes** chỉ biến đổi **giá trị int/long**, không tự đổi endian của CPU, JVM, mảng byte hay luồng I/O.
- Không nên tự động gọi **reverseBytes** mỗi khi đọc/gửi dữ liệu mạng. Cần biết đặc tả giao thức và API đọc/ghi đã xử lý endian hay chưa; chuyển đổi hai lần sẽ gây sai.
- Khi làm việc với mảng byte hoặc bộ đệm, **ByteBuffer.order(ByteOrder.BIG_ENDIAN / LITTLE_ENDIAN)** giúp đọc/ghi theo endian mong muốn; đó thường là cách biểu đạt trực tiếp hơn.
- **reverseBytes** không thay thế **reverse** khi yêu cầu là đảo thứ tự từng bit; **rotateRight(x, 8)** cũng không phải phép đổi endian vì nó chỉ xoay vòng byte thay vì đảo toàn bộ thứ tự.

## 6. Cẩn thận: xoay một byte khác xoay một int

Đối với các toán tử dịch, Java áp dụng **unary numeric promotion** riêng cho từng toán hạng: **byte**, **short**, **char** ở vế trái được nâng thành **int** trước khi dịch. Truyền một byte âm tới phương thức 32 bit sẽ không tự tạo phép xoay 8 bit:

~~~java
byte b = (byte) 0x81;   // có dấu: -127
int promoted = b;       // 0xFFFFFF81 (sign extension)
int unsigned = b & 0xFF; // 0x00000081
~~~

Ngay cả khi đã che dấu bằng **& 0xFF**, gọi **Integer.rotateLeft(unsigned, 1)** vẫn xoay **32 bit**, không phải 8 bit. Muốn xoay trong miền 8 bit, hãy đưa các bit tràn quay lại trong **8 vị trí** rồi che phần ngoài byte.

Ví dụ độc lập, lưu thành **ByteRotationDemo.java**:

~~~java
public class ByteRotationDemo {
    static int rotateLeft8(int rawByte, int distance) {
        int value = rawByte & 0xFF;
        int d = distance & 7;
        return ((value << d) | (value >>> (8 - d))) & 0xFF;
    }

    static int rotateRight8(int rawByte, int distance) {
        int value = rawByte & 0xFF;
        int d = distance & 7;
        return ((value >>> d) | (value << (8 - d))) & 0xFF;
    }

    public static void main(String[] args) {
        byte b = (byte) 0x81;

        System.out.printf("promoted: %08X%n", (int) b);
        System.out.printf("unsigned: %08X%n", b & 0xFF);
        System.out.printf("left8(81, 1): %02X%n", rotateLeft8(b, 1));
        System.out.printf("right8(81, 1): %02X%n", rotateRight8(b, 1));
        System.out.printf("Integer.rotateLeft(81, 1): %08X%n",
                Integer.rotateLeft(b & 0xFF, 1));
        System.out.println("8-bit round trip: "
                + (rotateRight8(rotateLeft8(b, 3), 3) == (b & 0xFF)));
    }
}
~~~

Chạy **javac ByteRotationDemo.java** rồi **java ByteRotationDemo**, stdout:

~~~text
promoted: FFFFFF81
unsigned: 00000081
left8(81, 1): 03
right8(81, 1): C0
Integer.rotateLeft(81, 1): 00000102
8-bit round trip: true
~~~

Trong hàm phụ, **distance & 7** chuẩn hóa số bước theo modulo 8, kể cả bước âm. **& 0xFF** giới hạn kết quả còn 8 bit, tránh để các bit cao của int lọt vào giá trị byte. Nếu cần biến đổi 16 bit, hãy thiết kế tương tự với mặt nạ **0xFFFF** và modulo 16; đừng nhầm độ rộng dữ liệu với độ rộng của kiểu int.

## 7. Số không dấu: cách đọc kết quả chính xác

Phương thức xoay/đảo bit không phân biệt signed và unsigned, nhưng lúc **hiển thị, so sánh hoặc đổi kiểu** phải chọn cách hiểu phù hợp. Với int:

~~~java
int bits = 0xFFFFFFFF;
System.out.println(bits);                          // -1
System.out.println(Integer.toUnsignedString(bits)); // 4294967295
System.out.printf("%08X%n", bits);                  // FFFFFFFF
~~~

Với long, có **Long.toUnsignedString**, **Long.compareUnsigned**, **Long.divideUnsigned**, ... phù hợp cho thao tác số không dấu. Tuy nhiên **Integer.toUnsignedLong(x)** mở rộng mẫu 32 bit thành một **long** 64 bit có giá trị không âm; nếu sau đó gọi **Long.rotateLeft**, phép xoay sẽ diễn ra trên **64 bit**. Muốn xoay nguyên mẫu 32 bit, hãy dùng **Integer.rotateLeft** trước khi mở rộng.

Để kiểm tra bit cuối hoặc bit dấu, có thể dùng:

~~~java
int x = Integer.rotateLeft(1, 31);
boolean highBitSet = (x & 0x80000000) != 0; // true
boolean lowBitSet = (x & 1) != 0;          // false
~~~

Không suy luận rằng một phép biến đổi bit sai chỉ vì số in ra là âm: **int/long** chỉ là một cách diễn giải signed của mẫu bit. Khi phân tích dữ liệu, hãy in hex đủ chữ số hoặc biểu diễn nhị phân có bổ sung số 0 phía trước.

## 8. Tính chất quan trọng và cách tự kiểm chứng

Gọi **ROL(x, d)** và **ROR(x, d)** là hai phép xoay cùng độ rộng:

1. **ROR(ROL(x, d), d) == x** và **ROL(ROR(x, d), d) == x**.
2. **ROL(x, a + b) == ROL(ROL(x, a), b)**, với cách tính khoảng xoay theo độ rộng tương ứng.
3. **ROL(x, d) == ROR(x, -d)**, kể cả khi d lớn hơn độ rộng.
4. **reverse(reverse(x)) == x**.
5. **reverseBytes(reverseBytes(x)) == x**.

Các đẳng thức trên đúng theo **mẫu bit** kể cả với số âm, số 0, mọi bit đều 1 và các khoảng xoay âm. Trong Java, **==** so sánh được trực tiếp mẫu bit int/long vì hai phía cùng kiểu nguyên thủy.

Một số phép kiểm tra tay rất tiện:

~~~text
int 0x00000000: mọi phép biến đổi đều trả 0x00000000
int 0xFFFFFFFF: mọi phép xoay/đảo đều trả 0xFFFFFFFF
reverse(0x00000001):      0x80000000
reverseBytes(0x00000001): 0x01000000
rotateLeft(0x80000001,1): 0x00000003
rotateRight(0x80000001,1):0xC0000000
~~~

Xoay và đảo chỉ **hoán vị vị trí bit**, nên giữ nguyên số lượng bit 1. Tuy nhiên chúng không cùng một phép hoán vị và không thể thay thế cho nhau. **Integer.bitCount(x)** hoặc **Long.bitCount(x)** có thể được dùng để kiểm chứng tính bảo toàn số bit 1 nếu cần.

## 9. Ứng dụng thực tế và giới hạn

- **Hashing và trộn bit:** phép rotate thường kết hợp với XOR, cộng modulo độ rộng hoặc các phép toán khác để khuếch tán thông tin giữa các vị trí bit. Chỉ xoay bit riêng lẻ **không** tạo hàm băm tốt và **không** có tính bảo mật.
- **Giao thức/định dạng nhị phân:** **reverseBytes** hữu ích khi cần biểu diễn giá trị bằng thứ tự byte đối nghịch; **reverse** phù hợp khi đặc tả yêu cầu đảo thứ tự bit. Cần đọc kỹ đặc tả để xác định đơn vị là **bit** hay **byte**.
- **Thuật toán thao tác bit:** chuyển hướng các mặt nạ bit, xử lý bitmap/bitboard, đảo vị trí bit hoặc kiểm thử cấu trúc dữ liệu nhị phân theo độ rộng cố định.
- **Khôi phục dữ liệu:** các phép đảo và xoay đều có nghịch đảo rõ ràng, thuận tiện cho thuật toán cần biến đổi khả nghịch; chúng **không mã hóa bảo mật** dữ liệu.

Về hiệu năng, hãy ưu tiên các phương thức chuẩn của **Integer/Long**: ngữ nghĩa rõ ràng, ít lỗi dấu/khoảng dịch và cho phép JDK tối ưu theo nền tảng. Không tự khẳng định chúng luôn biên dịch thành một lệnh CPU hoặc luôn nhanh hơn mọi mã thủ công; điều đó phụ thuộc JVM, máy và bối cảnh.

## 10. Lỗi phổ biến cần tránh

| Sai lầm | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Dùng **>>** thay **>>>** khi mô phỏng xoay phải | **>>** kéo dài bit dấu, làm sai phần ghép | Dùng hàm chuẩn hoặc dịch logic **>>>** |
| Nghĩ **Integer.rotateLeft** xoay đúng 8 bit khi đầu vào là byte | Phương thức luôn dùng 32 bit | Viết helper xoay 8 bit và che **& 0xFF** |
| Nhầm **reverse** với **reverseBytes** | Một bên đảo mọi bit; bên kia chỉ đảo byte | Vẽ nhóm 8 bit và đối chiếu đặc tả |
| Coi **rotateRight(x, 8)** là đổi endian | Xoay vòng khác đảo thứ tự các byte | Dùng **reverseBytes** khi thực sự cần byte swap |
| Cho rằng kết quả âm là lỗi | Bit cao nhất của int/long là bit dấu | In hex đủ độ rộng, hoặc hiển thị unsigned |
| Tự áp **% 32** rồi nghĩ mọi trường hợp âm đều dương | Toán tử **%** trong Java có thể trả số âm | Gọi thẳng **rotateLeft/Right**; hoặc chuẩn hóa phù hợp |
| Trộn **Integer** với **Long** | Độ rộng xoay thay đổi từ 32 sang 64 bit | Chọn phương thức khớp độ rộng định dạng |
| Đổi byte order hai lần | Có thể khôi phục về giá trị ban đầu nhưng làm sai dữ liệu cần truyền | Chỉ chuyển đổi ở ranh giới đã xác minh |

### Câu hỏi tự luyện

1. Với **int x = 1**, tại sao **Integer.rotateLeft(x, 1) == 2**, còn **Integer.reverse(x) == 0x80000000**?
2. Với **0x12345678**, giải thích tại sao **rotateRight(x, 8) = 0x78123456** nhưng **reverseBytes(x) = 0x78563412**.
3. Tại sao **Integer.rotateLeft(x, 40) == Integer.rotateLeft(x, 8)**, trong khi không thể lấy kết luận đó cho **Long.rotateLeft**?
4. Với một byte **0x81**, dự đoán xoay trái và xoay phải 1 bit trong **8 bit**, sau đó chạy **ByteRotationDemo** để kiểm chứng.
5. Nếu giao thức yêu cầu little-endian, bước nào trong quá trình ghi bằng **ByteBuffer** cần thể hiện byte order? Việc gọi thêm **reverseBytes** có còn đúng không?

Hiểu rõ ba khái niệm **độ rộng bit**, **thứ tự bit** và **thứ tự byte** sẽ giúp chọn đúng phép biến đổi và tránh những lỗi nhị phân rất khó nhìn thấy khi chỉ in số thập phân.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-011">Không đồng nhất dịch phải với chia khi xử lý số âm</a>

<details>
<summary>Xem chi tiết</summary>

# Không đồng nhất dịch phải với chia khi xử lý số âm trong Java

Trong Java, **dịch phải số nguyên có dấu** (`>>`) và **chia số nguyên** (`/`) có thể cho hai kết quả khác nhau khi số bị chia âm. Sự khác nhau đến từ **quy tắc làm tròn**, không phải từ việc viết hai phép toán theo hai cách khác nhau. Cần phân biệt thêm dịch phải không dấu (`>>>`), vì nó còn thay đổi cách xử lý bit dấu.

Bài học thuộc chủ đề **Bit và số không dấu**. Sau khi học, bạn cần biết khi nào dùng `/`, `>>` và `>>>`; có thể tự giải thích kết quả với số âm; và tránh thay phép chia bằng phép dịch bit một cách máy móc.

## 1. Ba phép toán có ba ý nghĩa

- `x / d` với `x` và `d` là kiểu nguyên (`int` hoặc `long`): Java thực hiện **chia nguyên, bỏ phần thập phân theo hướng 0**. Ví dụ `-3 / 2` được `-1`, vì thương toán học là `-1.5` và được cắt về `-1`. Dấu của số dư (`%`) theo dấu của số bị chia: `-3 % 2 == -1`.
- `x >> k`: **dịch phải số học** (arithmetic right shift), các bit bên trái được điền bằng bit dấu (0 với số không âm, 1 với số âm). Với `int` và `0 <= k <= 30`, kết quả tương đương **làm tròn xuống âm vô cực** của `x / 2^k` theo nghĩa toán học: `floor(x / 2^k)`.
- `x >>> k`: **dịch phải logic** (logical/unsigned right shift), luôn điền bit 0 từ bên trái. Với số âm, kết quả thường là một số nguyên dương rất lớn khi nhìn theo kiểu `int`; phép này không tương đương phép chia có dấu.

Điểm mấu chốt: `/` làm tròn về **0**, còn `>>` khi dịch số âm với phần dư khác 0 thì làm tròn về **âm vô cực**.

| Giá trị `x` | `k` | Thương toán học `x / 2^k` | `x / (1 << k)` | `x >> k` |
| ---: | ---: | ---: | ---: | ---: |
| 7 | 1 | 3.5 | 3 | 3 |
| -8 | 2 | -2 | -2 | -2 |
| -7 | 2 | -1.75 | -1 | -2 |
| -3 | 1 | -1.5 | -1 | -2 |
| -1 | 1 | -0.5 | 0 | -1 |

Bảng dùng kiểu `int`, `k` từ 0 đến 30 và mẫu số dương `1 << k`. Hai phép chỉ luôn giống nhau trong miền này khi `x >= 0` hoặc `x` âm nhưng **chia hết cho `2^k`**. Chúng khác nhau một đơn vị với số âm không chia hết.

## 2. Vì sao `-3 >> 1` bằng `-2`?

`int` có 32 bit và biểu diễn số nguyên âm theo bù hai (two's complement). Mẫu bit của `-3` là:

~~~text
-3 (int)       = 11111111 11111111 11111111 11111101 = 0xFFFFFFFD
-3 >> 1        = 11111111 11111111 11111111 11111110 = 0xFFFFFFFE = -2
-3 >>> 1       = 01111111 11111111 11111111 11111110 = 0x7FFFFFFE = 2147483646
-3 / 2         = -1                                      = 0xFFFFFFFF
~~~

Khi dùng `>>`, bit 1 bên trái được lặp lại, giữ dấu âm. Bit thấp bị loại bỏ, nên số `-3` chuyển thành `-2`. Trong phép chia `-3 / 2`, Java tính thương `-1.5` rồi bỏ phần lẻ về phía 0, được `-1`; phép chia không được định nghĩa là “dịch bit rồi bỏ bit cuối”.

Khi dùng `>>>`, bit 0 được chèn vào trái. Điều này phù hợp với ý nghĩa **dịch một mẫu bit 32 bit như số không dấu**: `0xFFFFFFFD` được hiểu là `4294967293` trong miền unsigned 32 bit, và chia lấy phần nguyên cho 2 được `2147483646`. Tuy nhiên biến `int` trong Java **vẫn là kiểu có dấu**; để xem giá trị unsigned ban đầu, dùng `Integer.toUnsignedLong(-3)`.

## 3. Ví dụ Java 17+ chạy độc lập

Lưu đúng tên tệp `ShiftVsDivision.java`:

~~~java
public class ShiftVsDivision {
    public static void main(String[] args) {
        printCase(-3, 1);
        printCase(-8, 2);
        printCase(-7, 2);
        printCase(7, 1);
        printCase(-1, 1);

        System.out.println("Math.floorDiv(-3, 2) = "
                + Math.floorDiv(-3, 2));
        System.out.println("-3 >>> 1 = " + (-3 >>> 1));
        System.out.println("-3 unsigned = "
                + Integer.toUnsignedLong(-3));
    }

    private static void printCase(int x, int k) {
        if (k < 0 || k > 30) {
            throw new IllegalArgumentException("k must be from 0 to 30");
        }
        int divisor = 1 << k;
        System.out.println("x=" + x + ", k=" + k
                + ": /=" + (x / divisor)
                + ", >>=" + (x >> k));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ShiftVsDivision.java
java ShiftVsDivision
~~~

Đầu ra chính xác:

~~~text
x=-3, k=1: /=-1, >>=-2
x=-8, k=2: /=-2, >>=-2
x=-7, k=2: /=-1, >>=-2
x=7, k=1: /=3, >>=3
x=-1, k=1: /=0, >>=-1
Math.floorDiv(-3, 2) = -2
-3 >>> 1 = 2147483646
-3 unsigned = 4294967293
~~~

Quan sát các dòng `-3`, `-7` và `-1`: chia nguyên và dịch phải khác nhau. Dòng `-8` cho thấy số âm **chia hết** cho lũy thừa của 2 thì kết quả vẫn trùng nhau. Dòng `7` minh họa trường hợp số dương.

## 4. Chọn toán tử theo ý nghĩa cần tính

**Trường hợp A — muốn thương chia nguyên theo quy tắc Java:** dùng `/`.

~~~java
int quantity = -7;
int result = quantity / 4; // -1: làm tròn về 0
~~~

Không thay thành `quantity >> 2`: phép dịch cho `-2`. Đây có thể là lỗi khó thấy nếu thử nghiệm chỉ với số dương hoặc số âm chia hết.

**Trường hợp B — muốn làm tròn xuống khi chia cho lũy thừa của 2:** dùng `Math.floorDiv(x, divisor)` để thể hiện rõ ý định, hoặc `x >> k` nếu thật sự đang làm phép toán bit và hiểu giới hạn của `k`.

~~~java
int x = -7;
int floorByApi = Math.floorDiv(x, 4); // -2
int floorByBits = x >> 2;             // -2
~~~

`Math.floorDiv` còn dùng được với các số chia dương khác 2, 4, 8, ...; `>>` chỉ có quan hệ trên với phép chia cho lũy thừa của 2. Trong công thức `floor(x / 2^k)`, ký hiệu `floor` là phép làm tròn toán học, **không phải** một phép chia nguyên Java.

**Trường hợp C — đang thao tác mẫu bit hoặc muốn dịch không dấu:** dùng `>>>` khi cần điền 0 từ bên trái; nếu muốn xử lý giá trị `int` như unsigned 32 bit, dùng các API thể hiện rõ cách hiểu unsigned.

~~~java
int bits = -3; // 0xFFFFFFFD
int shifted = bits >>> 1;                // 2147483646
long unsigned = Integer.toUnsignedLong(bits); // 4294967293
int quotient = Integer.divideUnsigned(bits, 2); // 2147483646
~~~

`>>>` hữu ích khi tách trường bit, xử lý mặt nạ, dữ liệu nhị phân hoặc một thuật toán chủ động dùng biểu diễn unsigned. Không dùng `>>>` để thay `/` với số âm có dấu.

## 5. Các giới hạn dễ bỏ sót

1. **Số lần dịch bị rút gọn theo độ rộng kiểu dữ liệu.** Với `int`, Java chỉ dùng 5 bit thấp của số đếm dịch (`k & 31`); với `long` là 6 bit thấp (`k & 63`). Vì vậy `x >> 32` thực chất giống `x >> 0` khi `x` là `int`; nó không biểu diễn chia cho `2^32`. Tương tự `1L >> 64` giống `1L >> 0`.
2. **Tránh viết `1 << 31` làm số chia dương kiểu `int`.** Giá trị đó là `Integer.MIN_VALUE` (`-2147483648`), do bit dấu được bật lên. Mẫu `x / (1 << k)` trong bảng chỉ dùng `0 <= k <= 30`. Nếu phải tính lũy thừa của 2 lớn hơn, xét `long` hoặc cách biểu diễn phù hợp trước khi chia.
3. **`byte` và `short` được nâng lên `int` trước khi dịch.** Ví dụ nếu `byte b = -3`, thì `b >>> 1` hoạt động trên `int` 32 bit đã được mở rộng dấu, kết quả là `2147483646`, không phải phép dịch riêng trên mẫu 8 bit `0xFD`. Nếu cần xử lý byte không dấu, chuyển `b & 0xFF` trước rồi mới dịch.
4. **`long` tuân theo cùng khác biệt làm tròn.** `-3L / 2L` cho `-1L`, còn `-3L >> 1` cho `-2L`. Số đếm dịch của `long` dùng giới hạn 64 bit thay vì 32 bit.
5. **Đừng suy ra “dịch luôn nhanh hơn chia” để tự thay toán tử.** Hai biểu thức phải có cùng ngữ nghĩa với *mọi* đầu vào hợp lệ trước khi tối ưu; trình biên dịch/JIT có thể tự chọn cách thực hiện phù hợp nhưng chương trình vẫn phải bảo toàn cách làm tròn của `/`. Việc thay sai phép toán là lỗi đúng đắn, bất kể hiệu năng.
6. **Kiểm tra trường hợp âm sát 0 và số âm lẻ.** `-1 / 2 == 0` nhưng `-1 >> 1 == -1` là phép thử nhỏ nhất làm lộ khác biệt; hãy thêm cả `-3`, `-7` và số âm chia hết như `-8`.

Với số chia là `2^k` dương và biểu diễn được, có thể ghi nhớ công thức:

~~~text
x / (2^k)  = truncateTowardZero(x / 2^k)
x >> k     = floor(x / 2^k)
~~~

Với `x < 0` và có số dư khác 0, kết quả `x >> k` **nhỏ hơn kết quả chia nguyên đúng 1**. Với `x >= 0` hoặc số âm chia hết, chúng trùng nhau. Đây là lý do phép dịch bit không thể mặc định thay thế phép chia trong logic xử lý số nguyên có dấu.

## 6. Tự kiểm tra

Hãy dự đoán trước khi chạy Java:

- `-9 / 4` và `-9 >> 2` lần lượt bằng bao nhiêu?
- `-12 / 4` và `-12 >> 2` có bằng nhau không?
- `-1 >>> 1` là bao nhiêu đối với kiểu `int`, và vì sao nó không phải kết quả của `-1 / 2`?

**Đáp án:** `-9 / 4 == -2`, `-9 >> 2 == -3`; cả hai biểu thức với `-12` đều bằng `-3` vì chia hết cho 4; `-1 >>> 1 == 2147483647` vì `-1` có toàn bit 1 và `>>>` chèn 0 vào đầu, trong khi `-1 / 2 == 0`.

Khi đọc code hiện có, hãy xác định ngay mục đích: **chia số có dấu**, **làm tròn xuống**, hay **dịch mẫu bit không dấu**. Chọn toán tử theo mục đích ấy, đặc biệt khi dữ liệu đầu vào có thể âm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bits-and-unsigned-012">Phân biệt giá trị số với thứ tự byte khi trao đổi dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt giá trị số với thứ tự byte khi trao đổi dữ liệu trong Java

Một số nguyên có **giá trị** riêng, ví dụ `0x1234_5678` (thập phân `305_419_896`). Nhưng khi gửi số đó qua mạng hoặc lưu thành tệp nhị phân, các bên phải thống nhất **cách biến giá trị thành các byte** và **thứ tự sắp xếp các byte**. Nếu bên ghi dùng big-endian nhưng bên đọc tự coi dữ liệu là little-endian, cùng bốn byte có thể được giải mã thành một giá trị hoàn toàn khác.

Bài học thuộc chủ đề **Bit và số không dấu**: cần tách bạch ba khái niệm **giá trị số**, **mẫu bit có độ rộng xác định**, và **thứ tự byte (endianness)**. Sau đó mới xét cách Java diễn giải mẫu bit đó như `int` có dấu, `long` hoặc một giá trị không dấu.

## 1. Một giá trị số, nhiều cách biểu diễn

Trong mã nguồn, cùng một giá trị có thể được viết bằng nhiều cơ số:

```java
int decimal = 305_419_896;
int hexadecimal = 0x1234_5678;
int binary = 0b0001_0010_0011_0100_0101_0110_0111_1000;
// Ba biến có cùng giá trị int.
```

Đây là ba khai báo minh họa có thể đặt trong phương thức Java. Dấu `0x` là **cách viết literal** trong mã nguồn, không phải một byte được tự động gửi ra mạng; dấu `_` chỉ giúp đọc dễ hơn. Con số `0x1234_5678` có mẫu bit 32 bit chia thành bốn nhóm tám bit:

```text
Hex:    12       34       56       78
Bit:  00010010 00110100 01010110 01111000
Byte:     1        2        3        4
```

Vì một chữ số hex tương ứng 4 bit, **hai chữ số hex tương ứng một byte**. Đây mới là những nhóm byte logic của mẫu số; khi trao đổi dữ liệu, ta còn phải quyết định **byte nào xuất hiện trước**.

Không nhầm:

- **Cơ số**: cách con người viết/đọc một giá trị bằng thập phân, nhị phân, hex.
- **Độ rộng**: số bit dùng biểu diễn, chẳng hạn `int` 32 bit, `long` 64 bit.
- **Có dấu/không dấu**: cách diễn giải mẫu bit thành giá trị số, nhất là khi bit cao nhất bật.
- **Thứ tự byte**: trật tự đặt các byte của **một giá trị nhiều byte** vào chuỗi byte trong tệp, gói tin hay bộ đệm.

## 2. Big-endian và little-endian

Với giá trị 32 bit `0x12345678`:

| Thứ tự | Byte tại offset 0 | Offset 1 | Offset 2 | Offset 3 |
| --- | --- | --- | --- | --- |
| **Big-endian** | `12` | `34` | `56` | `78` |
| **Little-endian** | `78` | `56` | `34` | `12` |

**Big-endian** đặt **byte có trọng số lớn nhất (most significant byte, MSB)** trước. **Little-endian** đặt **byte có trọng số nhỏ nhất (least significant byte, LSB)** trước. Trong ví dụ, `0x12` chứa các bit trọng số lớn nhất, `0x78` chứa các bit trọng số nhỏ nhất.

Hình dung hai phía:

```text
Giá trị logic: 0x12345678

Big-endian:
offset       0    1    2    3
bytes       12   34   56   78

Little-endian:
offset       0    1    2    3
bytes       78   56   34   12
```

**Không đảo thứ tự bit bên trong từng byte**. Byte `0x12` vẫn là `00010010` ở cả hai dạng; khác biệt nằm ở vị trí các byte trong chuỗi. Không có “bit dấu di chuyển” theo endian: bit có ý nghĩa dấu thuộc vị trí bit của số 32/64 bit **sau khi đã ghép đúng các byte**.

Đối với **một byte đơn lẻ**, không có sự khác biệt về thứ tự byte. Endianness chỉ có ý nghĩa khi chuyển một giá trị nhiều byte thành chuỗi byte hoặc ngược lại.

## 3. Đọc sai thứ tự làm đổi số, dù các byte không đổi

Giả sử nhận được bốn byte liên tiếp:

```text
12 34 56 78
```

Nếu đặc tả quy định big-endian, giá trị là `0x12345678` = `305419896`. Nếu đọc **chính bốn byte đó** như little-endian, ta nhận `0x78563412` = `2018915346`.

Với byte `b0`–`b3` được hiểu là các giá trị **không dấu 0..255**, công thức giải mã là:

```text
Big-endian:
value = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3

Little-endian:
value = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0
```

Đây là công thức logic trên bốn giá trị byte **đã chuyển thành không dấu**. Trong Java phải cẩn thận nâng kiểu/mask trước khi dịch để không bị mở rộng dấu ngoài ý muốn; phần thực hành bên dưới thể hiện cách làm an toàn.

Nếu hai đầu giao tiếp không thống nhất endian, chương trình có thể vẫn chạy, vẫn nhận đủ bốn byte, nhưng cho giá trị sai **âm thầm**. Đây là lỗi giao thức, không phải lỗi phép cộng hay tình trạng mất gói dữ liệu.

## 4. `int` trong Java có dấu, nhưng dữ liệu trao đổi có thể là unsigned

Java `int` có **32 bit có dấu**, miền `-2_147_483_648` đến `2_147_483_647`. Có những giao thức quy định trường **unsigned 32 bit**, miền `0` đến `4_294_967_295`. Hai miền cùng dùng **bốn byte**; sự khác biệt là cách diễn giải các bit sau khi ghép byte.

Ví dụ mẫu bit `0xFEDCBA98`:

```text
Mẫu 32 bit:                FE DC BA 98
Giá trị int có dấu:        -19088744
Giá trị unsigned 32 bit:   4275878552
```

Nếu bên nhận Java dùng `ByteBuffer.getInt()`, biến nhận `int` là `-19088744`. Điều này **không chứng minh dữ liệu bị sai thứ tự**: mẫu 32 bit vẫn là `FEDCBA98`. Khi giao thức yêu cầu unsigned 32 bit, chuyển cách diễn giải bằng:

```java
int raw = 0xFEDC_BA98;
long unsigned = Integer.toUnsignedLong(raw); // 4275878552L
```

Đây là mảnh mã minh họa trong một phương thức. `Integer.toUnsignedLong` mở rộng bằng số 0 để giữ đúng giá trị **không dấu** 32 bit dưới dạng `long`, thay vì sign-extend. `Integer.toUnsignedString(raw)` có thể biểu diễn kết quả dưới dạng chuỗi.

Cần tránh kết luận “số âm thì endian sai”: **signedness và endianness độc lập**. Luôn kiểm tra đúng thứ tự byte trước, rồi mới diễn giải mẫu bit theo hợp đồng signed/unsigned.

## 5. `byte` Java có dấu: mask `0xFF` trước khi ghép

Một byte trên dây thường được mô tả trong tài liệu giao thức như `0x00`–`0xFF` (0–255). Nhưng kiểu `byte` Java là **8 bit có dấu**, phạm vi -128 đến 127. Ví dụ:

```java
byte b = (byte) 0xFE;
int signed = b;                 // -2: mở rộng dấu sang int.
int unsigned = b & 0xFF;        // 254: giữ tám bit thấp.
int alsoUnsigned = Byte.toUnsignedInt(b); // 254.
```

Khi `byte` được nâng thành `int` để dịch trái, nếu nó âm thì có thể phát sinh bit 1 mở rộng ở phần cao. Vì vậy phải chuẩn hóa mỗi byte bằng `& 0xFF` hoặc `Byte.toUnsignedInt` **trước khi ghép**.

Một hàm đọc unsigned 32-bit big-endian an toàn:

```java
static long readUnsigned32BigEndian(byte[] bytes) {
    if (bytes.length != 4) {
        throw new IllegalArgumentException("expected exactly four bytes");
    }
    return ((long) (bytes[0] & 0xFF) << 24)
            | ((long) (bytes[1] & 0xFF) << 16)
            | ((long) (bytes[2] & 0xFF) << 8)
            | (bytes[3] & 0xFFL);
}
```

Đây là **phương thức trong một lớp Java**, không phải chương trình đầy đủ. Mỗi byte được chuyển thành miền 0–255, ghép theo big-endian, rồi lưu vào `long` để kết quả unsigned 32 bit luôn là số không âm và không bị tràn `int` có dấu. Tránh `return (bytes[0] << 24) | ...` khi không mask byte: Java sign-extend `byte` âm lên `int`.

## 6. Java `ByteBuffer`: ghi và đọc theo thứ tự đã thỏa thuận

Trong `java.nio`, `ByteBuffer` hỗ trợ cấu hình `ByteOrder.BIG_ENDIAN` và `ByteOrder.LITTLE_ENDIAN`:

```java
int value = 0x1234_5678;
byte[] bigEndian = java.nio.ByteBuffer.allocate(4)
        .order(java.nio.ByteOrder.BIG_ENDIAN)
        .putInt(value)
        .array();

byte[] littleEndian = java.nio.ByteBuffer.allocate(4)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        .putInt(value)
        .array();
```

Hai mảng byte lần lượt chứa `12 34 56 78` và `78 56 34 12`. **`value` không thay đổi.** `ByteBuffer.putInt` quyết định cách chia mẫu 32 bit thành 4 byte dựa trên thứ tự byte của buffer.

Khi đọc, phải đặt cùng `ByteOrder` theo **hợp đồng dữ liệu**:

```java
int fromBig = java.nio.ByteBuffer.wrap(bigEndian)
        .order(java.nio.ByteOrder.BIG_ENDIAN)
        .getInt(); // 0x12345678

int fromLittle = java.nio.ByteBuffer.wrap(littleEndian)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        .getInt(); // 0x12345678
```

Một `ByteBuffer` mới tạo có **thứ tự mặc định là `BIG_ENDIAN`**. Tuy nhiên, đừng dựa mù quáng vào mặc định của chương trình khi thiết kế giao thức: gọi `.order(...)` tường minh để thể hiện contract và tránh hiểu sai.

Hàm `putInt`/ `getInt` xử lý **đúng bốn byte**; `putShort`/ `getShort` xử lý hai byte; `putLong`/ `getLong` xử lý tám byte. `put`/ `get` cho **một byte** không bị endian làm thay đổi. Khi dùng `ByteBuffer.allocate(4)`, `.putInt(...)` đẩy position tới 4; `.array()` lấy mảng lưu trữ. Nếu chuyển từ ghi sang đọc trên **cùng buffer**, thường phải gọi `flip()` trước `getInt()`. Ở ví dụ `wrap(byte[])`, buffer mới có position = 0 nên đọc được ngay.

Lưu ý `ByteBuffer` có thể direct hoặc read-only và không phải lúc nào cũng có mảng truy cập được bằng `.array()`; ví dụ này chọn buffer heap bằng `allocate(4)` có backing array. Trong ứng dụng thực, có thể ghi vào `ByteBuffer` rồi truyền vùng byte bằng API I/O thay vì giả định luôn gọi được `.array()`.

## 7. Ví dụ hoàn chỉnh Java 17+: cùng số, hai thứ tự byte

Tạo tệp **`ByteOrderDemo.java`** với mã sau:

```java
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteOrderDemo {
    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(String.format("%02X", Byte.toUnsignedInt(b)));
        }
        return out.toString();
    }

    static long readUnsigned32BigEndian(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("expected exactly four bytes");
        }
        return ((long) (bytes[0] & 0xFF) << 24)
                | ((long) (bytes[1] & 0xFF) << 16)
                | ((long) (bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFFL);
    }

    public static void main(String[] args) {
        int number = 0x1234_5678;
        byte[] big = ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(number)
                .array();
        byte[] little = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(number)
                .array();

        System.out.println("value=" + String.format("0x%08X", number));
        System.out.println("bigEndian=" + hex(big));
        System.out.println("littleEndian=" + hex(little));
        System.out.println("readBE=" + String.format("0x%08X",
                ByteBuffer.wrap(big).order(ByteOrder.BIG_ENDIAN).getInt()));
        System.out.println("readLE=" + String.format("0x%08X",
                ByteBuffer.wrap(little).order(ByteOrder.LITTLE_ENDIAN).getInt()));
        System.out.println("wrongOrder=" + String.format("0x%08X",
                ByteBuffer.wrap(big).order(ByteOrder.LITTLE_ENDIAN).getInt()));

        int rawBits = 0xFEDC_BA98;
        byte[] payload = ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(rawBits)
                .array();
        int decoded = ByteBuffer.wrap(payload)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();
        System.out.println("signedInt=" + decoded);
        System.out.println("unsignedInt=" + Integer.toUnsignedLong(decoded));
        System.out.println("wireBytes=" + hex(payload));
        System.out.println("manualUnsigned=" + readUnsigned32BigEndian(payload));

        byte signedByte = (byte) 0xFE;
        System.out.println("signedByte=" + signedByte);
        System.out.println("unsignedByte=" + Byte.toUnsignedInt(signedByte));
    }
}
```

Chạy tại thư mục chứa tệp:

```bash
javac --release 17 -encoding UTF-8 ByteOrderDemo.java
java ByteOrderDemo
```

Kết quả stdout chính xác:

```text
value=0x12345678
bigEndian=12 34 56 78
littleEndian=78 56 34 12
readBE=0x12345678
readLE=0x12345678
wrongOrder=0x78563412
signedInt=-19088744
unsignedInt=4275878552
wireBytes=FE DC BA 98
manualUnsigned=4275878552
signedByte=-2
unsignedByte=254
```

Ý nghĩa quan trọng:

- Hai dòng `bigEndian` và `littleEndian` khác nhau ở **thứ tự byte**, còn dòng `value` vẫn chỉ một giá trị `0x12345678`.
- `readBE` và `readLE` bằng nhau nếu bên đọc dùng **đúng endian của bên ghi**; `wrongOrder=0x78563412` chứng minh đọc sai endian làm đổi số.
- `signedInt=-19088744` và `unsignedInt=4275878552` có cùng mẫu bit `0xFEDCBA98`. Số âm đến từ cách đọc có dấu, không phải lỗi thứ tự byte.
- `manualUnsigned=4275878552` xác nhận có thể tự ghép dữ liệu theo big-endian với phép dịch/mask đúng.
- `signedByte=-2` và `unsignedByte=254` cho thấy cần xử lý tính có dấu của `byte` trước khi ghép số nguyên nhiều byte.

## 8. Khi giao tiếp qua mạng, điều gì phải thống nhất?

Trong nhiều giao thức mạng truyền thống, các trường số nguyên nhiều byte ở **network byte order (big-endian)**. Ví dụ, nhiều trường của header IPv4/TCP sử dụng big-endian. Nhưng **không phải mọi giao thức trên TCP đều tự động dùng big-endian**: giao thức ứng dụng có thể quy định little-endian, byte-order riêng theo từng trường, hoặc biểu diễn văn bản/biến độ dài.

Một hợp đồng dữ liệu cần nêu rõ:

| Thuộc tính | Ví dụ quy ước |
| --- | --- |
| Kiểu trường | unsigned 32-bit |
| Số byte | Chính xác 4 |
| Byte order | Big-endian |
| Dải hợp lệ | 0 đến 4_294_967_295 |
| Vị trí trong thông điệp | Offset 0–3 sau header |
| Cách xử lý dữ liệu thiếu | Chưa đủ 4 byte thì chưa được giải mã trường |
| Kiểm tra dữ liệu | Từ chối giá trị ngoài phạm vi nghiệp vụ |

Ví dụ field `length` được quy định là **4 byte unsigned big-endian**. Bên gửi phải ghi 4 byte theo đúng thứ tự; bên nhận phải đọc **đủ 4 byte** trước khi tính giá trị `long` không dấu và kiểm tra giới hạn độ dài tối đa chấp nhận được. Không nên cấp phát vùng nhớ theo chiều dài nhận được trước khi xác minh dải, quota và giới hạn dữ liệu.

**TCP chỉ cung cấp luồng byte**, không đánh dấu “đây là một `int`” hoặc bảo đảm mỗi lần gọi `read` trả về trọn một trường. Phần đóng gói thông điệp, xử lý partial read, kiểu số và endian thuộc trách nhiệm thiết kế giao thức.

## 9. `DataInputStream`, `DataOutputStream` và sự khác biệt với `ByteBuffer`

Java `DataOutputStream.writeInt(int)` và `DataInputStream.readInt()` quy định encoding **big-endian** cho giá trị bốn byte. Đây là lựa chọn tiện nếu giao thức của bạn cũng dùng định dạng này.

```java
java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
try (java.io.DataOutputStream out = new java.io.DataOutputStream(buffer)) {
    out.writeInt(0x1234_5678);
}
byte[] bytes = buffer.toByteArray(); // 12 34 56 78
```

Đây là đoạn mã trong một phương thức Java có xử lý ngoại lệ phù hợp; không phải một chương trình độc lập. Nếu giao thức quy định little-endian, **không dùng `DataOutputStream.writeInt` như thể nó tự chuyển endian**; dùng `ByteBuffer.order(LITTLE_ENDIAN)` hoặc viết byte theo thứ tự được yêu cầu.

`ByteBuffer.getInt` và `DataInputStream.readInt` đều trả về **`int` có dấu**. Nếu trường thực tế là unsigned 32 bit, kết quả `int` vẫn chứa đúng 32 bit và có thể dùng `Integer.toUnsignedLong` để đọc giá trị không dấu.

## 10. Nhầm endian với bit order, ký tự, và số được gửi dưới dạng văn bản

Endianness chỉ là **thứ tự byte trong biểu diễn nhiều byte**. Không nên trộn nó với ba khái niệm khác:

**Thứ tự bit:** Có những giao thức mô tả bit cao/thấp được truyền trước trong một byte hoặc một khung; đó là một quy tắc ở mức bit. Thay big-endian thành little-endian **không đảo thứ tự các bit trong từng byte**.

**Mã hóa văn bản:** Khi gửi chuỗi `"305419896"` theo UTF-8, ta gửi **các byte ký tự** (ví dụ byte `0x33` cho ký tự `'3'`), chứ không phải bốn byte nhị phân `12 34 56 78`. Bên nhận phải biết đang nhận chuỗi số hay số nhị phân. Với định dạng JSON, số thường được biểu diễn bằng các ký tự văn bản; byte order của một `int` nhị phân không tự động áp dụng vào đó.

**Thứ tự code unit của UTF-16:** Một số cách mã hóa chuỗi 16-bit có khái niệm endian riêng. Dù có cùng tên big/little endian, quy tắc mã hóa văn bản vẫn phải được xác định thông qua charset và đặc tả văn bản, không suy ngược từ endian của một trường số nhị phân.

**Độ rộng trường:** Gửi số `1` thành một byte `01`, hai byte `00 01` hoặc bốn byte `00 00 00 01` là ba hợp đồng khác nhau. Chọn endian không thay thế việc thống nhất số byte, độ rộng signed/unsigned và cách đánh dấu đầu/cuối trường.

## 11. Lỗi thường gặp khi trao đổi số nhị phân

| Lỗi | Hậu quả | Cách tránh |
| --- | --- | --- |
| Bên gửi ghi big-endian, bên nhận đọc little-endian | `12 34 56 78` thành `0x78563412` | Quy định byte order và cấu hình hai bên tương ứng |
| Cho rằng `int` âm tức dữ liệu bị đảo byte | Nhầm signedness với endian | Kiểm tra mẫu bit và `Integer.toUnsignedLong` |
| Ghép `byte` âm mà không mask | Sign extension làm thay đổi các bit cao | Dùng `& 0xFF` hoặc `Byte.toUnsignedInt` |
| Dùng `byte[]` nhưng không có hợp đồng kiểu | Không xác định được độ dài, signedness và byte order | Định nghĩa schema rõ ràng cho từng trường |
| Gọi `ByteBuffer.getInt()` sau `putInt` mà không `flip()` | Sai position hoặc buffer underflow | `flip()` khi chuyển từ ghi sang đọc cùng buffer |
| Cho rằng `DataOutputStream.writeInt` dùng endian của CPU | Luồng ra không khớp định dạng mong muốn | Biết rõ API này ghi big-endian |
| Dùng `Integer.parseInt` để giải mã bốn byte | Parse nhận chuỗi ký tự số, không phải định dạng nhị phân | Dùng `ByteBuffer`, bit shifts hoặc `DataInputStream` |
| Dùng `0x12345678` trong văn bản như thể đó là byte truyền đi | Nhầm cú pháp literal và payload thực | Chỉ định encoding nhị phân hoặc văn bản |
| Tin rằng TCP `read` luôn nhận đủ bốn byte | Giải mã khi dữ liệu chưa đủ | Đọc theo vòng lặp hoặc API readFully |
| Coi unsigned 32-bit là `int` dương thông thường | Giá trị lớn hơn `Integer.MAX_VALUE` bị hiển thị âm | Dùng `long` và unsigned conversion |

## 12. Checklist khi thiết kế và kiểm thử

Trước khi viết bộ mã hóa/giải mã, xác nhận **mỗi field** có độ rộng 8/16/32/64 bit hoặc kiểu biến độ dài; giá trị signed hay unsigned; big-endian hay little-endian; offset và byte count; dải nghiệp vụ hợp lệ. Với field kiểu unsigned 32-bit, trong Java thường nên dùng `long` làm kiểu API bên ngoài và kiểm tra `0 <= value <= 0xFFFF_FFFFL` trước khi chuyển sang bốn byte.

Một bộ test tối thiểu nên có:

1. **Giá trị dễ nhìn:** `0x12345678` để phát hiện đảo byte. Big-endian `12 34 56 78`; little-endian `78 56 34 12`.
2. **Biên unsigned 32-bit:** `0x00000000`, `0x7FFFFFFF`, `0x80000000`, `0xFFFFFFFF`. Đọc/ghi vẫn phải giữ bit chính xác bất kể `int` tạm thời âm.
3. **Round-trip:** ghi một giá trị rồi đọc lại với byte order tương ứng, so sánh **bit pattern** và giá trị unsigned nếu đặc tả yêu cầu.
4. **Sai endian có chủ ý:** đọc bytes big-endian bằng little-endian để chắc chắn test bắt được thay đổi mong đợi.
5. **Thông điệp thiếu byte:** thử nhận 1–3 byte khi mong đợi 4 byte; bộ giải mã phải chờ đủ hoặc báo thiếu dữ liệu rõ ràng.
6. **Đầu vào ngoài giới hạn:** thử kích thước quá lớn, giá trị vượt hợp đồng và dữ liệu không hợp lệ, bảo đảm không cấp phát/tiến hành xử lý nguy hiểm.

**Câu hỏi tự kiểm tra:**

- Cùng một số `0x12345678`, big-endian và little-endian có giá trị số khác nhau không? **Không**, nếu được giải mã theo đúng quy ước. Chỉ thứ tự bốn byte khi lưu/truyền khác.
- Byte nào xuất hiện trước trong little-endian? **Byte có trọng số thấp nhất**, ở đây là `0x78`.
- Vì sao `ByteBuffer.getInt()` có thể trả số âm cho một field unsigned 32-bit? Vì **Java `int` có dấu**; hãy dùng `Integer.toUnsignedLong` để đọc cách diễn giải unsigned.
- `& 0xFF` dùng để làm gì? Giữ **8 bit thấp** và loại tác động sign extension khi nâng `byte` âm lên kiểu lớn hơn.
- Nếu giao thức dùng little-endian nhưng lập trình viên dùng `DataOutputStream.writeInt`, điều gì xảy ra? Byte trên dây **không đúng quy ước little-endian**, vì API này ghi big-endian.

**Ghi nhớ:** giá trị số không quyết định thứ tự byte của payload. Endianness là một phần của **hợp đồng serialization**, signedness là cách **diễn giải mẫu bit**, còn `byte` Java có dấu nên phải mask khi ghép byte thủ công. Khi hai phía thống nhất độ rộng, endian, signed/unsigned và kiểm thử cùng các chuỗi byte chuẩn, dữ liệu nhị phân sẽ được trao đổi chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)
