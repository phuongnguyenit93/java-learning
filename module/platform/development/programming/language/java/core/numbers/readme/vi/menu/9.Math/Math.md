# Tiện ích toán học và phép toán an toàn

Java đã cung cấp nhiều tiện ích số chuẩn. Dùng API chuẩn thường thể hiện mục đích rõ hơn và tránh tự viết lại các trường hợp biên khó.

## <a id="math-core-functions">Các hàm chính của Math</a>

`Math` có thể nhóm thành:

```text
chọn giá trị cơ bản
→ abs, min, max

lũy thừa/căn
→ pow, sqrt, cbrt

tiện ích làm tròn
→ floor, ceil, round, rint

lượng giác/logarit
→ sin, cos, tan, log, exp

tiện ích số nguyên
→ floorDiv, floorMod, phép toán có kiểm tra tràn

tiện ích số dấu phẩy động
→ ulp, nextUp, nextDown, copySign...
```

### `floor`, `ceil`, `round`

```java
Math.floor(2.7);  // 2.0
Math.ceil(2.1);   // 3.0
Math.round(2.5);  // 3

Math.floor(-2.1); // -3.0
Math.ceil(-2.9);  // -2.0
```

`floor` nghĩa là đi về -∞, không phải “bỏ phần thập phân”.

### `floorDiv` và `floorMod`

Phép chia số nguyên thông thường của Java cắt về phía 0:

```java
System.out.println(-7 / 3); // -2
```

`Math.floorDiv` dùng phép chia lấy sàn:

```java
System.out.println(Math.floorDiv(-7, 3)); // -3
```

Khi thuật toán cần đúng ngữ nghĩa lấy sàn toán học với số âm, điểm khác biệt này rất quan trọng.

## <a id="exact-arithmetic-methods">Phép toán số nguyên có kiểm tra</a>

Các phương thức hỗ trợ:

```java
Math.addExact
Math.subtractExact
Math.multiplyExact
Math.incrementExact
Math.decrementExact
Math.negateExact
Math.absExact
Math.divideExact
Math.floorDivExact
Math.ceilDivExact
Math.toIntExact
```

biến tràn số âm thầm thành `ArithmeticException`.

Ví dụ:

```java
long total =
        Math.multiplyExact(
                (long) quantity,
                unitPrice
        );
```

### Phương thức kiểm tra tràn không tạo ra phạm vi tùy ý

Nếu giá trị hợp lệ có thể vượt `long`, các phương thức `*Exact` chỉ giúp **phát hiện** tràn số. Cách biểu diễn đúng vẫn là `BigInteger`.

Với Java 21, `Math.absExact(Integer.MIN_VALUE)` và `Math.divideExact(Integer.MIN_VALUE, -1)` ném `ArithmeticException` thay vì trả kết quả tràn số âm thầm như các phép tương ứng không có kiểm tra.

## <a id="strictmath-boundary">Math và StrictMath</a>

`Math` là lựa chọn thông thường cho mã ứng dụng.

`StrictMath` tồn tại cho trường hợp cần hành vi của các hàm toán học trên số dấu phẩy động tuân theo yêu cầu chặt hơn về khả năng tái lập kết quả/đặc tả.

```text
Math
→ API tiện ích số mặc định

StrictMath
→ ưu tiên khả năng tái lập đã được đặc tả cho các hàm toán học liên quan
```

Không nên hiểu `StrictMath` là “Math nhưng chính xác tuyệt đối”. Các hàm vẫn hoạt động trên giá trị dấu phẩy động và chịu giới hạn biểu diễn của `double/float`.

Chỉ cần quan tâm ranh giới này khi khả năng tái lập kết quả giữa các nền tảng thực sự là một yêu cầu.

Hai chương tiếp theo chuyển từ cách biểu diễn số sang một nhóm yêu cầu khác: **sinh số ngẫu nhiên**. Sau đó chương tổng hợp sẽ nối các lựa chọn này thành một mô hình ra quyết định thống nhất.
