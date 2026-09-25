# Làm tròn số

## <a id="rounding-modes">Semantics của RoundingMode</a>
`RoundingMode` định nghĩa cách phần bị discard ảnh hưởng value giữ lại. `UP`/`DOWN` là away/toward zero, `CEILING`/`FLOOR` theo positive/negative infinity, HALF modes quyết định tie. `UNNECESSARY` assert rằng không được cần rounding và throw nếu thực tế phải round.

## <a id="rounding-at-boundaries">Khi nào rounding thực sự xảy ra</a>
Rounding chỉ xảy ra khi operation hoặc representation policy loại bỏ precision/scale. Round lặp ở intermediate step có thể tạo final result khác với round một lần tại business boundary. Giữ full precision nội bộ trừ khi domain quy định khác.

## <a id="financial-rounding-policy">Rounding policy là domain decision</a>
Không có một financial rounding mode đúng cho mọi bài toán. Tax, interest, currency conversion, invoice-line và settlement có thể dùng rule khác. Policy nên explicit, test tie/boundary case và nằm gần domain decision thay vì rải `setScale(2, ...)` khắp code.
