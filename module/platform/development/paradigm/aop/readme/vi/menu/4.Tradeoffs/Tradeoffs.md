# Trade-off khi sử dụng AOP

## <a id="aop-benefits">1. Lợi ích</a>

AOP có thể giảm duplicate code, gom policy cross-cutting vào một nơi và giữ business code tập trung hơn vào nhiệm vụ chính.

## <a id="aop-cost">2. Chi phí</a>

Behavior có thể không còn hiện rõ ngay tại source code của target.

Điều này làm debugging, tracing control flow và reasoning về thứ tự execution khó hơn nếu aspect quá nhiều hoặc selection rule quá rộng.

## <a id="aop-good-fit">3. Concern phù hợp</a>

Những concern thường phù hợp có đặc điểm:

- lặp lại ở nhiều execution boundary;
- ít phụ thuộc vào business meaning riêng của từng use case;
- có rule áp dụng rõ ràng;
- có thể test độc lập.

## <a id="aop-bad-fit">4. Concern không phù hợp</a>

Business decision quan trọng, workflow chính hoặc behavior mà người đọc cần nhìn thấy trực tiếp thường không nên bị ẩn sau aspect.

Rule thực tế:

```text
cross-cutting policy  → AOP có thể phù hợp
core business flow    → ưu tiên code tường minh
```

## <a id="aop-boundary">5. Boundary với implementation cụ thể</a>

Module này chỉ sở hữu AOP ở mức paradigm.

Các implementation cụ thể như framework proxy/interceptor hoặc weaving engine nên được học tại module công nghệ tương ứng thay vì đưa toàn bộ chi tiết implementation vào đây.
