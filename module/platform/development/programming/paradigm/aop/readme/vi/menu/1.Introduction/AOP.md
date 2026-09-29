# Aspect-Oriented Programming (AOP)

## <a id="aop-what">1. AOP là gì?</a>

Aspect-Oriented Programming là một cách tổ chức chương trình nhằm tách những hành vi **cắt ngang nhiều phần của hệ thống** khỏi business logic chính.

Những hành vi kiểu này thường được gọi là **cross-cutting concern**, ví dụ logging, tracing, đo thời gian, audit hoặc kiểm tra policy.

## <a id="aop-why">2. Tại sao AOP tồn tại?</a>

Nếu cùng một concern phải xuất hiện ở rất nhiều nơi, code business dễ bị lặp và bị trộn với logic không thuộc domain chính.

Ví dụ một hệ thống có hàng chục use case đều cần đo thời gian và ghi audit. Nếu mỗi method tự viết lại cùng một đoạn logic, việc thay đổi policy sau này trở nên khó và dễ thiếu nhất quán.

## <a id="aop-without">3. Nếu không có AOP thì giải quyết thế nào?</a>

Cách đơn giản hơn là gọi helper, wrapper hoặc decorator một cách tường minh:

```text
business code
→ gọi logging helper
→ gọi audit helper
→ chạy logic chính
```

Cách này hoàn toàn hợp lệ và thường còn dễ hiểu hơn khi concern chỉ xuất hiện ở ít nơi.

## <a id="aop-limit">4. Tại sao cách đơn giản đôi khi chưa đủ?</a>

Khi cùng một concern trải rộng trên nhiều module hoặc nhiều execution boundary, việc gọi helper thủ công dễ tạo duplicate code và phụ thuộc vào việc developer nhớ áp dụng đúng ở mọi nơi.

AOP đưa concern đó thành một đơn vị riêng và mô tả **nơi nào nó được áp dụng** thay vì chèn logic thủ công vào từng business method.

## <a id="aop-solution">5. AOP giải quyết vấn đề ra sao?</a>

Mental model tổng quát:

```text
business behavior
        +
cross-cutting behavior
        ↓
composition mechanism
        ↓
effective runtime behavior
```

AOP không thay business logic. Nó bổ sung behavior tại những điểm được chọn trong execution model.

## <a id="aop-when">6. Khi nào nên dùng?</a>

AOP phù hợp khi concern thực sự cắt ngang nhiều phần của hệ thống và selection rule có thể mô tả rõ ràng.

Không nên dùng AOP chỉ để tránh viết vài dòng code. Nếu behavior là business flow quan trọng hoặc cần nhìn thấy trực tiếp trong control flow, code tường minh thường dễ đọc và dễ bảo trì hơn.
