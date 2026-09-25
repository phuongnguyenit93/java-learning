# Math API

## <a id="math-core-functions">Core function của Math</a>
`Math` cung cấp absolute value, min/max, power, root, logarithm, trigonometry, rounding helper và conversion. Các method chủ yếu làm việc trên primitive numeric type và kế thừa semantics về overflow/floating-point của chúng.

## <a id="exact-arithmetic-methods">Exact integer arithmetic helper</a>
`addExact`, `subtractExact`, `multiplyExact`, `incrementExact`, `decrementExact`, `negateExact` và exact narrowing conversion biến silent overflow thành `ArithmeticException`. Dùng khi overflow vi phạm domain contract.

## <a id="strictmath-boundary">Boundary Math và StrictMath</a>
Java hiện đại specification chặt kết quả `Math` và nhiều method được intrinsify/native optimize. `StrictMath` tồn tại lịch sử để đảm bảo algorithm reproducible xuyên platform. Xem đây là numerical reproducibility boundary, không phải lý do thay mọi `Math` bằng `StrictMath`.
