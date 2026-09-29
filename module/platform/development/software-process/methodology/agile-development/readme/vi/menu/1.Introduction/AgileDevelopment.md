# Agile Software Development

## <a id="agile-what">1. Agile là gì?</a>

Agile là một **cách tiếp cận phát triển phần mềm dựa trên values và principles**, nhấn mạnh feedback sớm, collaboration, adaptive planning và delivery theo từng phần có giá trị.

Agile không phải một framework duy nhất và cũng không đồng nghĩa với Scrum.

```text
Agile
→ umbrella of values / principles

Scrum
→ framework

Kanban
→ method for managing flow

XP
→ software development method with strong engineering practices
```

## <a id="agile-why">2. Tại sao Agile tồn tại?</a>

Software project thường có uncertainty: requirement thay đổi, user feedback xuất hiện muộn, technical risk chỉ lộ ra khi implementation bắt đầu.

Nếu toàn bộ plan, design và scope bị khóa quá sớm, chi phí sửa sai có thể rất lớn.

Agile giảm feedback delay bằng cách đưa planning, implementation, verification và learning vào các vòng ngắn hơn.

## <a id="agile-without">3. Cách đơn giản hơn trước đó chưa đủ ở đâu?</a>

Sequential lifecycle có thể phù hợp khi requirement ổn định và change cost thấp.

Nhưng trong môi trường có uncertainty cao:

```text
plan rất sớm
→ build rất lâu
→ feedback rất muộn
→ phát hiện sai hướng khi chi phí sửa đã cao
```

Agile không loại bỏ planning; nó chuyển planning thành hoạt động **liên tục và thích nghi**.

## <a id="agile-model">4. Mental model</a>

```text
small plan
→ build
→ verify
→ deliver / inspect
→ feedback
→ adapt
→ repeat
```

Các yếu tố trung tâm gồm iterative delivery, incremental value, short feedback loop, transparency và adaptation.

## <a id="agile-methods">5. Scrum, Kanban, XP và Lean nằm ở đâu?</a>

**Scrum** cung cấp framework với roles/accountabilities, events và artifacts để làm việc theo iteration.

**Kanban** tập trung vào visualization, flow, work-in-progress và continuous improvement.

**Extreme Programming (XP)** nhấn mạnh engineering practices như feedback nhanh, testing, refactoring và continuous integration.

**Lean Software Development** tập trung vào value, waste reduction, flow và learning.

Chúng có quan hệ với Agile nhưng không đồng nghĩa với Agile.

## <a id="agile-boundary">6. Boundary</a>

Module này sở hữu Agile family ở mức methodology.

TDD/BDD/ATDD được học sâu trong `methodology/driven-development`. CI/CD và deployment automation thuộc delivery/tooling concern, không phải canonical owner của Agile module.
