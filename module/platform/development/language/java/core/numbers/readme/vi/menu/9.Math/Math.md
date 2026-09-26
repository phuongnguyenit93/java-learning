# Math và StrictMath

Nhiều phép toán số học phổ biến đã có helper chuẩn trong Java. Dùng API chuẩn thường rõ intent hơn và tránh tự viết lại những edge case khó.

## <a id="math-core-functions">Các hàm chính của Math</a>

`Math` cung cấp nhiều nhóm thao tác:

- `abs`, `min`, `max`;
- `sqrt`, `pow`;
- `floor`, `ceil`, `round`;
- lượng giác/logarithm;
- exact integer helpers;
- một số utility liên quan floating-point.

Đừng mặc định `Math` làm mọi thứ “exact”: các method floating-point vẫn tuân cách biểu diễn và rounding của `double`/`float`.

## <a id="exact-arithmetic-methods">Exact Integer Arithmetic</a>

Các helper như:

```java
Math.addExact
Math.subtractExact
Math.multiplyExact
Math.incrementExact
```

giúp biến overflow từ silent wraparound thành `ArithmeticException`.

Chúng phù hợp khi domain coi overflow là invalid trạng thái/lỗi.

## <a id="strictmath-boundary">Math và StrictMath</a>

`StrictMath` ưu tiên kết quả floating-point có tính reproducible theo specification nghiêm ngặt hơn cho các transcendental function.

Trong đa số mã ứng dụng, `Math` là lựa chọn thông thường. Chỉ cần quan tâm ranh giới này khi reproducibility/cách triển khai ngữ nghĩa của mathematical function thật sự là yêu cầu.

Hai chương cuối không còn nói về cách biểu diễn số mà nói về **hợp đồng của tính ngẫu nhiên**.
