# Precision và Scale

## <a id="precision-vs-scale">Precision khác scale</a>
Precision là số significant digit trong `BigDecimal`; scale là số digit bên phải decimal point khi scale không âm. Hai khái niệm trả lời hai câu hỏi khác nhau và không nên dùng thay thế nhau.

```java
BigDecimal x = new BigDecimal("123.4500");
// precision = 7, scale = 4
```

## <a id="scale-transformations">setScale, movePoint và normalization</a>
`setScale` đổi representation và có thể cần rounding khi giảm scale. `movePointLeft`/`movePointRight` đổi numerical value theo power of ten. `stripTrailingZeros` bỏ trailing zero và có thể tạo negative scale, nên không được assume normalized value luôn scale >= 0.

## <a id="math-context">MathContext và precision control</a>
`MathContext` quy định significant-digit precision cộng rounding mode cho operation hỗ trợ nó. Nó khác việc cố định số decimal place. Policy phải xuất phát từ domain requirement, không nên áp một global context cho mọi calculation.
