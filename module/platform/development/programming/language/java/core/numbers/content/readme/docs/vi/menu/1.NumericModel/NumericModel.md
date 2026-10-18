# Các cách Java biểu diễn số

Java không có một kiểu số duy nhất phù hợp với mọi bài toán. Mỗi cách biểu diễn đánh đổi giữa **phạm vi giá trị, khả năng biểu diễn chính xác, hiệu năng, bộ nhớ và cách làm tròn**.

Đây là mô hình tư duy quan trọng nhất của mô-đun:

```text
int / long
→ số nguyên nhanh, gọn, chính xác trong phạm vi cố định

float / double
→ phạm vi lớn, phù hợp tính toán khoa học/kỹ thuật,
  nhưng nhiều số thập phân chỉ được biểu diễn gần đúng

BigInteger
→ số nguyên không bị giới hạn bởi độ rộng cố định của kiểu nguyên thủy

BigDecimal
→ số thập phân với giá trị + scale rõ ràng,
  phù hợp khi ngữ nghĩa thập phân và chính sách làm tròn quan trọng
```

Không có cách biểu diễn nào đồng thời cho ta phạm vi vô hạn, số thập phân chính xác tuyệt đối, tốc độ của kiểu nguyên thủy và mức dùng bộ nhớ nhỏ. Vì vậy học Numbers trước hết là học **chọn cách biểu diễn phù hợp với yêu cầu của bài toán**.

Lộ trình:

```text
Vì sao Java cần nhiều cách biểu diễn số?
Các cách Java biểu diễn số
        ↓
Số nguyên độ rộng cố định có thể sai như thế nào?
Số nguyên độ rộng cố định và tràn số
        ↓
Vì sao 0.1 + 0.2 gây bất ngờ?
Số dấu phẩy động và sai số biểu diễn
        ↓
Nếu phạm vi số nguyên không đủ?
Số nguyên phạm vi tùy ý với BigInteger
        ↓
Nếu cần số thập phân chính xác?
Số thập phân chính xác với BigDecimal
        ↓
Độ chính xác (precision), scale và làm tròn trở thành chính sách như thế nào?
Độ chính xác (precision), scale và chính sách làm tròn
        ↓
Vì sao bằng nhau về số học và bằng nhau giữa đối tượng có thể khác nhau?
Quy tắc bằng nhau và thứ tự của giá trị số
        ↓
`Math` cung cấp tiện ích nào?
Tiện ích toán học và phép toán an toàn
        ↓
Sinh số giả ngẫu nhiên và SecureRandom khác nhau ở yêu cầu nào?
Sinh số ngẫu nhiên trong lập trình và yêu cầu bảo mật
        ↓
Cuối cùng chọn cách biểu diễn và chính sách nào?
Chọn mô hình số phù hợp
```

Ba nhóm ví dụ sẽ được dùng xuyên suốt mô-đun:

```text
bộ đếm / số lượng / ID
→ ngữ nghĩa số nguyên và tràn số

cảm biến / đo lường khoa học
→ xấp xỉ dấu phẩy động và sai số chấp nhận

tiền / tỷ lệ / thuế
→ BigDecimal, độ chính xác, scale và chính sách làm tròn
```

## <a id="numeric-type-model">Các nhóm kiểu số</a>

### KHÁI NIỆM — Java có những cách biểu diễn số nào?

Các kiểu số nguyên thủy:

| Kiểu | Số bit | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | ---: | ---: | ---: |
| `byte` | 8 | -128 | 127 |
| `short` | 16 | -32,768 | 32,767 |
| `int` | 32 | -2³¹ | 2³¹ - 1 |
| `long` | 64 | -2⁶³ | 2⁶³ - 1 |

`char` cũng là kiểu nguyên thủy dạng số nguyên nhưng dùng để biểu diễn một đơn vị mã UTF-16 (code unit); trong phạm vi học Numbers, trọng tâm phép toán số học thường nằm ở `byte/short/int/long`.

Các kiểu nguyên thủy dấu phẩy động:

```text
float   → IEEE 754 binary32
double  → IEEE 754 binary64
```

Các kiểu đối tượng quan trọng:

```text
BigInteger
→ số nguyên có độ chính xác tùy ý (arbitrary precision)

BigDecimal
→ số nguyên chưa áp dụng scale (unscaled integer) có độ chính xác tùy ý + scale kiểu int
→ ngữ nghĩa thập phân và chính sách làm tròn rõ ràng
```

### VÌ SAO — Vì sao cần nhiều kiểu?

Hãy bắt đầu từ yêu cầu của bài toán thay vì từ cú pháp Java:

| Bài toán | Cách biểu diễn thường phù hợp | Lý do chính |
| --- | --- | --- |
| số lượng, chỉ số, bộ đếm nhỏ | `int` | nhanh, đơn giản, đủ phạm vi |
| mốc thời gian/số đếm lớn | `long` | phạm vi lớn hơn |
| khoa học/cảm biến/đồ họa | `double` | phạm vi rộng + được phần cứng hỗ trợ |
| số nguyên vượt `long` | `BigInteger` | không bị giới hạn bởi độ rộng cố định |
| tiền, thuế, tỷ lệ thập phân | `BigDecimal` | ngữ nghĩa thập phân + cách làm tròn tường minh |

Một ID trong cơ sở dữ liệu thường nên là `long` không phải vì cần làm toán, mà vì cần **phạm vi đủ rộng**. Một số đo nhiệt độ có thể dùng `double` vì bài toán chấp nhận một mức sai số. Một số tiền lại thường không nên dùng `double` vì yêu cầu cần ngữ nghĩa thập phân rõ ràng.

### CÁCH CHỌN — Chọn cách biểu diễn theo yêu cầu bài toán

Khi gặp một giá trị số, hỏi theo thứ tự:

```text
1. Có cần phần thập phân không?
        ↓
2. Có cần giá trị thập phân chính xác không?
        ↓
3. Phạm vi tối đa là bao nhiêu?
        ↓
4. Có cho phép tràn số/làm tròn không?
        ↓
5. Hiệu năng/mức dùng bộ nhớ có quan trọng không?
```

Không nên chọn `double` chỉ vì “chứa được số lớn”, cũng không nên dùng `BigDecimal` cho mọi phép toán chỉ vì “chính xác hơn”. Mỗi cách biểu diễn giải quyết một nhóm yêu cầu khác nhau.

## <a id="integer-vs-floating">Số nguyên và số dấu phẩy động</a>

Phép toán số nguyên và phép toán dấu phẩy động có **kiểu sai khác nhau**.

```text
số nguyên
→ chính xác trong phạm vi biểu diễn được
→ tràn số/quay vòng khi vượt phạm vi

số dấu phẩy động
→ phạm vi động rộng
→ nhiều giá trị chỉ là xấp xỉ
→ có NaN / vô cực / số 0 có dấu
```

Ví dụ:

```java
int count = Integer.MAX_VALUE;
count++;
System.out.println(count); // Integer.MIN_VALUE

double total = 0.1 + 0.2;
System.out.println(total); // 0.30000000000000004
```

Hai kết quả “lạ” này không cùng nguyên nhân:

- số nguyên sai vì **phạm vi hữu hạn**;
- số dấu phẩy động lệch vì **cách biểu diễn hữu hạn trong hệ nhị phân**.

Điều quan trọng không phải “kiểu nào chính xác hơn” một cách chung chung, mà là **kiểu nào phù hợp với yêu cầu hơn**.

## <a id="numeric-conversions">Chuyển đổi và nâng kiểu số (Numeric Promotion)</a>

### Cơ chế quan trọng: kiểu của phép toán được quyết định trước phép gán

Trước khi phép toán số học chạy, Java áp dụng các quy tắc về chuyển đổi và nâng kiểu số (numeric promotion).

Các kiểu số nguyên nhỏ hơn `int` thường được nâng lên `int`:

```java
byte a = 10;
byte b = 20;

int result = a + b;
// byte invalid = a + b; // compile error
```

Đây là lý do `byte + byte` không tự cho ra `byte`.

Với biểu thức trộn nhiều kiểu:

```java
int i = 10;
long l = 20L;
double d = 1.5;

long x = i + l;
double y = l + d;
```

### Lỗi thường gặp: mở rộng kiểu ở phép gán không cứu được tràn số đã xảy ra

```java
int quantity = 1_000_000;
int price = 10_000;

long wrong = quantity * price;
```

Người mới thường nghĩ `wrong` là `long` nên an toàn. Nhưng luồng thực tế là:

```text
int * int
    ↓
phép toán chạy dưới dạng int
    ↓
có thể tràn số
    ↓
kết quả int đã bị sai
    ↓
chuyển sang long
```

Muốn phép nhân chạy dưới dạng `long`, phải mở rộng kiểu **trước khi thực hiện phép toán**:

```java
long correct = (long) quantity * price;
```

### Ép kiểu không phục hồi dữ liệu đã mất

```java
int overflowed = Integer.MAX_VALUE + 1;
long widened = (long) overflowed;
```

`widened` chỉ chứa phiên bản `long` của giá trị đã bị tràn. Ép kiểu ở bước cuối không thể quay lại phép toán ban đầu.

Tương tự, nếu phép toán `double` đã tạo sai số làm tròn rồi mới đưa vào `BigDecimal`, BigDecimal không thể biết ý nghĩa thập phân ban đầu mà người viết mã mong muốn là gì.

Chương tiếp theo bắt đầu với dạng lỗi dễ bỏ qua nhất của số nguyên: **bị tràn nhưng chương trình vẫn tiếp tục chạy**.
