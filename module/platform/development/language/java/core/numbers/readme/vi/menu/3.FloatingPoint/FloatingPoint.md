# Số thực dấu phẩy động

## <a id="binary-floating-point">Biểu diễn binary floating-point</a>
`float` và `double` biểu diễn value bằng sign, significand và exponent theo IEEE 754 binary. Nhiều decimal fraction đơn giản có binary expansion lặp vô hạn nên không thể represent chính xác.

## <a id="precision-rounding-error">Precision và rounding error</a>
Operation round về floating-point value gần nhất có thể represent. Representation error nhỏ có thể tích lũy nên `0.1 + 0.2` không đúng chính xác decimal `0.3`. Đây là behavior expected, không phải JVM random.

## <a id="nan-infinity-negative-zero">NaN, infinity và negative zero</a>
Floating-point có positive/negative infinity, NaN và signed zero. NaN unordered nên `x == Double.NaN` là sai cách kiểm tra; dùng `Double.isNaN`. `0.0 == -0.0` là true nhưng một số operation và wrapper comparison/hash có thể phân biệt bit pattern.

## <a id="floating-point-comparison">Chiến lược so sánh floating-point</a>
`==` phù hợp khi contract cần exact binary identity, không phải universal test cho computed measurement. Domain comparison thường dùng absolute/relative tolerance dựa trên scale thực tế. Không có một epsilon duy nhất đúng cho mọi magnitude/domain.
