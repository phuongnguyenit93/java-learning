# Rounding

Rounding xuất hiện khi kết quả chính xác không thể hoặc không được phép giữ nguyên ở cách biểu diễn mong muốn. Với financial/domain mã, cách làm tròn phải là **chính sách có chủ ý**, không phải chi tiết format cuối cùng.

## <a id="rounding-modes">RoundingMode</a>

`RoundingMode` mô tả cách xử lý phần bị bỏ đi. Một số mode phổ biến:

```text
UP / DOWN
CEILING / FLOOR
HALF_UP / HALF_DOWN / HALF_EVEN
UNNECESSARY
```

`HALF_EVEN` thường được gọi là banker's rounding và có thể giảm bias khi lặp nhiều phép làm tròn, nhưng không có nghĩa nó luôn là chính sách đúng cho mọi domain.

## <a id="rounding-at-boundaries">Khi nào Rounding xảy ra?</a>

Rounding có thể xảy ra khi:

- giảm scale bằng `setScale`;
- dùng `MathContext` với precision giới hạn;
- chia một decimal không có kết quả hữu hạn;
- áp dụng business quy tắc yêu cầu đơn vị nhỏ nhất cụ thể.

Điểm quan trọng là **round ở ranh giới có chủ ý**, không round ngẫu nhiên sau mỗi thao tác nếu domain không yêu cầu.

## <a id="financial-rounding-policy">Chính sách làm tròn</a>

Với money/tax/rate, chính sách thường phải trả lời:

- làm tròn ở từng line item hay tổng cuối;
- dùng scale bao nhiêu;
- dùng `HALF_UP`, `HALF_EVEN` hay quy tắc pháp lý riêng;
- intermediate calculation giữ precision bao nhiêu.

Nếu chính sách không được xác định rõ, hai cách triển khai đều “hợp lý” có thể cho kết quả khác nhau.

chương tiếp theo giải quyết một bẫy khác của BigDecimal: **cùng numerical value nhưng `equals` có thể vẫn trả false vì scale khác nhau**.
