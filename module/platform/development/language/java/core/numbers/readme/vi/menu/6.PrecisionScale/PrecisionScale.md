# Precision và Scale

Hai thuật ngữ này thường bị dùng lẫn nhau nhưng mô tả hai khía cạnh khác nhau của `BigDecimal`.

## <a id="precision-vs-scale">Precision và Scale</a>

Với:

```text
123.45
```

ta có thể hiểu:

```text
precision = 5
→ tổng số chữ số có nghĩa

scale = 2
→ số chữ số bên phải dấu thập phân
```

Scale có thể bằng `0`, dương hoặc thậm chí âm trong một số cách biểu diễn.

Đừng dùng “precision” như từ đồng nghĩa với “số chữ số sau dấu phẩy”; đó là vai trò của scale.

## <a id="scale-transformations">Thay đổi Scale</a>

`setScale(...)` có thể yêu cầu rounding nếu giảm số chữ số thập phân:

```java
value.setScale(2, RoundingMode.HALF_UP);
```

`movePointLeft`/`movePointRight` thay đổi vị trí decimal point theo power of ten.

Các thao tác như `stripTrailingZeros()` có thể thay cách biểu diễn/scale mà vẫn giữ numerical value.

## <a id="math-context">MathContext</a>

`MathContext` cho phép đặt **precision của thao tác** và rounding mode:

```java
MathContext context = new MathContext(4, RoundingMode.HALF_EVEN);
```

Nó khác với `setScale`: một cái kiểm soát tổng precision của arithmetic, cái kia tập trung vào scale của value/result.

chương tiếp theo đi sâu vào rounding: **làm tròn không chỉ là format đầu ra mà thường là một quyết định của domain**.
