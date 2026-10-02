# Numbers

Module này xây mental model cho **numeric representation và arithmetic semantics** trong Java: integer overflow, floating-point approximation, arbitrary precision, decimal scale/rounding và random-number choices.

## Learning flow

1. numeric model của Java;
2. integer overflow;
3. floating-point;
4. `BigInteger`;
5. `BigDecimal`;
6. precision và scale;
7. rounding;
8. `BigDecimal` comparison;
9. `Math`;
10. `Random`;
11. `SecureRandom`;
12. tổng hợp decision model.

## Mental model cần giữ

Không có một numeric type phù hợp cho mọi bài toán. Primitive integer có fixed range; floating point ưu tiên binary approximation; `BigDecimal` phù hợp decimal arithmetic khi scale/rounding là contract; security-sensitive randomness cần `SecureRandom`.

Module tập trung vào semantics và lựa chọn representation, không biến mọi phép tính thành bài toán arbitrary precision.
