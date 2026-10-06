<a id="back-to-top"></a>

# AOP và các mối quan tâm cắt ngang (Cross-Cutting Concern)

## Menu
- [Cross-cutting concern là gì và vì sao AOP tồn tại?](#cross-cutting-concern)
- [Minh chứng thực thi cho AOP và các mối quan tâm cắt ngang (Cross-Cutting Concern)](#cross-cutting-demo)
- [Tổng hợp AOP và các mối quan tâm cắt ngang (Cross-Cutting Concern)](#cross-cutting-conclusion)

Phần này trả lời câu hỏi cơ bản nhất: **vì sao AOP tồn tại?**

## <a id="cross-cutting-concern">Cross-cutting concern là gì và vì sao AOP tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

Business concern là hành vi tạo ra giá trị chính của use case, ví dụ tạo đơn hàng, giữ tồn kho hoặc tính giá. Cross-cutting concern có vai trò khác: đó là một chính sách hoặc hành vi kỹ thuật cần áp dụng cho nhiều thao tác vốn không cùng trách nhiệm nghiệp vụ.

Các ví dụ quen thuộc:

- logging và tracing;
- đo thời gian thực thi, metrics;
- auditing;
- kiểm tra authorization hoặc chính sách.

Nếu không có một cơ chế AOP, từng phương thức nghiệp vụ vẫn có thể tự thực hiện các việc đó:

```text
createOrder()
→ log trước
→ business logic
→ log sau

cancelOrder()
→ log trước
→ business logic
→ log sau
```

Cách này có thể chạy đúng, nhưng cùng một chính sách bị rải qua nhiều class. Khi chính sách thay đổi, ta phải sửa đồng thời ở những nơi mà trách nhiệm chính vốn là nghiệp vụ khác.

AOP bổ sung một trục modularization khác:

```text
business classes
→ giữ hành vi của use case

aspect
→ sở hữu một chính sách cắt ngang

pointcut
→ mô tả chính sách áp dụng ở đâu
```

AOP bổ sung cho thiết kế hướng đối tượng chứ không thay thế class, service hay quan hệ cộng tác tường minh giữa các object. Target method vẫn sở hữu hành vi nghiệp vụ; aspect sở hữu hành vi có thể mô tả độc lập tại một interception boundary ổn định.

Một câu hỏi thiết kế hữu ích là:

```text
Concern này có thể diễn đạt thành một chính sách tái sử dụng,
áp dụng cho một tập method execution có boundary rõ ràng không?
```

Nếu có, AOP có thể phù hợp. Nếu hành vi thực chất là một quy trình nghiệp vụ nhiều bước và thứ tự các bước là một phần của miền nghiệp vụ, biểu diễn bằng service/composition tường minh thường dễ hiểu hơn việc giấu quy trình trong advice.

Spring AOP hiện thực ý tưởng này bằng runtime proxy và method interception. Boundary runtime đó sẽ rất quan trọng ở các chapter sau: pointcut match đúng vẫn chưa đồng nghĩa mọi Java call đều có thể bị intercept.

### Tài liệu tham khảo

- Spring Framework Reference — Aspect Oriented Programming with Spring
- Spring Framework Reference — AOP Concepts

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cross-cutting-demo">Minh chứng thực thi cho AOP và các mối quan tâm cắt ngang (Cross-Cutting Concern)</a>

<details>
<summary>Xem chi tiết</summary>

Experiment của module cố ý tách rõ hành vi nghiệp vụ và hành vi cắt ngang.

Controller:

```text
CrossCuttingController#observeCrossCutting()
```

Endpoint:

```text
GET /aop/cross-cutting/observe
```

Target methods:

```text
CrossCuttingService#createOrder()
CrossCuttingService#cancelOrder()
```

Hành vi cắt ngang:

```text
CrossCuttingLoggingAspect#logAround(...)
```

Response `events` dự kiến cho thấy chính sách bao quanh từng target invocation:

```text
logging-before:createOrder
target:create-order
logging-after:createOrder
logging-before:cancelOrder
target:cancel-order
logging-after:cancelOrder
```

Mở `CrossCuttingService` và kiểm tra hai phương thức nghiệp vụ không tự phát ra `logging-before` hay `logging-after`. Sau đó xem aspect: chính nó sở hữu hành vi dùng chung và áp dụng hành vi đó tại các method-execution boundary được chọn.

Điều cần rút ra không nằm ở ví dụ logging cụ thể, mà ở đường đi:

```text
bên gọi
→ AOP boundary
→ chính sách dùng chung
→ phương thức nghiệp vụ
```

Các chapter sau sẽ làm rõ từng phần: proxy tạo boundary, pointcut chọn method execution, còn advice hiện thực chính sách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cross-cutting-conclusion">Tổng hợp AOP và các mối quan tâm cắt ngang (Cross-Cutting Concern)</a>

<details>
<summary>Xem chi tiết</summary>

Giữ mental model đầu tiên này:

```text
Phương thức nghiệp vụ
→ sở hữu hành vi chính của use case

Aspect
→ gom một cross-cutting concern

Pointcut
→ chỉ ra concern áp dụng ở đâu

Advice
→ thực hiện concern tại các invocation đã chọn
```

Spring AOP phù hợp nhất khi chính sách cắt ngang ổn định, có thể tái sử dụng và vẫn hiểu được mà không phải đọc từng target method. Phần còn lại của module giải thích cách Spring biến thiết kế đó thành một runtime chain dựa trên proxy.

</details>

- [Quay lại đầu trang](#back-to-top)
