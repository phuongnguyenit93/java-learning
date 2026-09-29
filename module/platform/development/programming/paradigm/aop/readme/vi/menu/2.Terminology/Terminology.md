# Thuật ngữ cốt lõi của AOP

## <a id="aop-aspect">1. Aspect</a>

**Aspect** là đơn vị gom một cross-cutting concern cùng với rule xác định nơi concern đó được áp dụng.

## <a id="aop-join-point">2. Join Point</a>

**Join Point** là một điểm có ý nghĩa trong execution model nơi behavior bổ sung có thể tham gia.

Tùy implementation, join point có thể là method execution, constructor, field access hoặc một loại execution event khác.

## <a id="aop-pointcut">3. Pointcut</a>

**Pointcut** là rule chọn một tập join point.

Nói ngắn gọn:

```text
Join Point = nơi có thể can thiệp
Pointcut   = rule chọn nơi cần can thiệp
```

## <a id="aop-advice">4. Advice</a>

**Advice** là behavior được thực thi tại join point đã được chọn.

Conceptually, advice có thể chạy trước, sau hoặc bao quanh behavior chính tùy execution model.

## <a id="aop-target">5. Target</a>

**Target** là behavior hoặc component gốc mà cross-cutting behavior được áp dụng lên.

## <a id="aop-weaving">6. Weaving</a>

**Weaving** là quá trình kết hợp aspect với target để tạo ra effective behavior.

Weaving không bắt buộc phải dùng cùng một cơ chế ở mọi implementation; nó có thể xảy ra ở build time, load time hoặc runtime.
