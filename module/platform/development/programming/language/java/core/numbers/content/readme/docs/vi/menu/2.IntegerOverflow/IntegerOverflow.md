# Số nguyên độ rộng cố định và tràn số

Số nguyên kiểu nguyên thủy nhìn có vẻ “chính xác”, nhưng chỉ chính xác **trong phạm vi của kiểu có độ rộng cố định**. Khi phép toán vượt `MIN_VALUE` hoặc `MAX_VALUE`, Java không tự đổi sang `BigInteger` và cũng không mặc định ném ngoại lệ.

## <a id="integer-overflow-wraparound">Tràn số và giá trị quay vòng (Wraparound)</a>

### KHÁI NIỆM

`int` là số nguyên có dấu 32 bit. Giá trị lớn nhất và nhỏ nhất là:

```text
Integer.MIN_VALUE = -2^31
Integer.MAX_VALUE =  2^31 - 1
```

Khi phép toán đi ra ngoài phạm vi đó, kết quả quay vòng theo quy tắc của số nguyên độ rộng cố định:

```java
int value = Integer.MAX_VALUE;
value++;

System.out.println(value); // Integer.MIN_VALUE
```

### VÌ SAO nguy hiểm?

Tràn số thường **không tạo ngoại lệ**:

```text
đầu vào hợp lệ
    ↓
phép toán bị tràn số
    ↓
giá trị quay vòng nhưng vẫn là int hợp lệ
    ↓
logic nghiệp vụ tiếp tục chạy với dữ liệu sai
```

Nếu giá trị là số lượng, số xu, bộ đếm, thời gian chờ, độ lệch hoặc dung lượng, lỗi có thể xuất hiện rất xa nơi tràn số thực sự xảy ra.

### CƠ CHẾ — kết quả trung gian mới là nơi cần nhìn

```java
int quantity = 1_000_000;
int unitPrice = 10_000;

long total = quantity * unitPrice;
```

Phép nhân vẫn là `int * int`. Phép gán sang `long` diễn ra **sau phép toán**.

Cách đúng nếu phạm vi của kết quả cần `long`:

```java
long total = (long) quantity * unitPrice;
```

Tương tự:

```java
long a = 3_000_000_000L;
long b = 4_000_000_000L;

long product = a * b; // long cũng có thể overflow
```

Đổi từ `int` sang `long` chỉ tăng phạm vi, không loại bỏ nguy cơ tràn số vĩnh viễn.

## <a id="checked-arithmetic">Phép toán có kiểm tra tràn (Checked Arithmetic)</a>

Khi tràn số phải được coi là **vi phạm yêu cầu của bài toán**, phép toán có kiểm tra giúp biến tràn số thành lỗi rõ ràng thay vì để giá trị âm thầm quay vòng.

Ví dụ:

```java
try {
    int next = Math.addExact(Integer.MAX_VALUE, 1);
} catch (ArithmeticException ex) {
    System.out.println("overflow detected");
}
```

Danh sách đầy đủ các phương thức `*Exact` và cách dùng API chi tiết thuộc chương `Math` phía sau. Ở đây cần giữ mô hình quyết định:

```text
phép toán thông thường
→ tràn số có thể làm giá trị quay vòng

phương thức `*Exact`
→ tràn số được biến thành ArithmeticException
```

Không phải mọi phép toán đều cần phương thức kiểm tra. Nếu việc quay vòng là chủ ý của thuật toán mức thấp thì phép toán có kiểm tra có thể không phù hợp. Nhưng với các bài toán số lượng/tiền/bộ đếm, tràn số âm thầm thường là lỗi.

Với Java 21 là phiên bản nền của dự án, `absExact` và `divideExact` đặc biệt hữu ích cho hai trường hợp biên ở phần sau: `MIN_VALUE` không có giá trị dương tương ứng trong cùng kiểu nguyên thủy, và `MIN_VALUE / -1` bị tràn.

## <a id="boundary-values">Giá trị biên MIN/MAX</a>

### Vì sao MIN_VALUE đặc biệt?

Kiểu số nguyên có dấu dùng biểu diễn bù hai (two's complement) có một giá trị âm nhiều hơn phía dương:

```text
int
min = -2147483648
max =  2147483647
```

Vì vậy:

```java
int abs = Math.abs(Integer.MIN_VALUE);
System.out.println(abs); // vẫn là Integer.MIN_VALUE
```

`Math.abs` không thể trả `2147483648` dưới dạng `int` vì giá trị đó nằm ngoài phạm vi. Với overload nhận `int` này, kết quả bị tràn và vẫn là `Integer.MIN_VALUE`; phương thức không tự ném ngoại lệ chỉ vì trường hợp này.

Tương tự, phép:

```java
int x = Integer.MIN_VALUE / -1;
System.out.println(x); // Integer.MIN_VALUE
```

Về toán học cần `2147483648`, nhưng `int` không biểu diễn được. Java định nghĩa trường hợp tràn đặc biệt này trả lại `Integer.MIN_VALUE` thay vì ném ngoại lệ tràn số; chỉ phép chia cho 0 mới ném `ArithmeticException` trong phép chia số nguyên.

### Kiểm thử giá trị biên

Khi logic có nguy cơ chạm giới hạn phạm vi, nên kiểm thử:

```text
MIN_VALUE
MIN_VALUE + 1
-1
0
1
MAX_VALUE - 1
MAX_VALUE
```

và các biểu thức có **phép nhân/phép cộng trung gian**.

Nếu bài toán thực sự có thể vượt `long`, đừng vá từng trường hợp tràn số. Khi đó cách biểu diễn phù hợp hơn là `BigInteger`.

Trước khi tới `BigInteger`, ta cần hiểu một đánh đổi khác: số dấu phẩy động không quay vòng theo cùng kiểu nhưng lại **không biểu diễn chính xác mọi phân số thập phân**.
