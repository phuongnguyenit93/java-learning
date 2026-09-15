<a id="back-to-top"></a>

# AOP và Cross-Cutting Concern

## Menu
- [1. Business concern và cross-cutting concern](#cross-cutting-concern)
- [2. Demo trong module](#cross-cutting-demo)
- [3. Kết luận](#cross-cutting-conclusion)

Phần này trả lời câu hỏi cơ bản nhất: **vì sao AOP tồn tại?**

## <a id="cross-cutting-concern">1. Business concern và cross-cutting concern</a>

<details>
<summary>Click for details</summary>

Business concern là logic cốt lõi của use case, ví dụ tạo đơn hàng hoặc hủy đơn hàng.

Cross-cutting concern là behavior xuất hiện ở nhiều nơi nhưng không phải business logic chính, ví dụ:

- logging;
- đo thời gian;
- auditing;
- tracing;
- authorization check.

Nếu mỗi business method tự viết logging, code dễ lặp:

```text
createOrder()
→ log before
→ business logic
→ log after

cancelOrder()
→ log before
→ business logic
→ log after
```

AOP cho phép tách phần lặp đó thành một Aspect:

```text
LoggingAspect
        ↓
createOrder()
cancelOrder()
```

Điểm quan trọng: AOP không làm business logic biến mất. Nó chỉ đặt một cross-cutting behavior ở boundary phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cross-cutting-demo">2. Demo trong module</a>

<details>
<summary>Click for details</summary>

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

Cross-cutting behavior:

```text
CrossCuttingLoggingAspect#logAround(...)
```

Response `events` sẽ có dạng:

```text
logging-before:createOrder
target:create-order
logging-after:createOrder
logging-before:cancelOrder
target:cancel-order
logging-after:cancelOrder
```

Hãy mở `CrossCuttingService` và kiểm tra: hai business method không tự viết `logging-before` hay `logging-after`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cross-cutting-conclusion">3. Kết luận</a>

<details>
<summary>Click for details</summary>

Mental model:

```text
Business method
→ chỉ tập trung vào behavior chính

Aspect
→ chứa behavior dùng chung đi ngang qua nhiều method
```

AOP phù hợp khi concern thật sự cross-cutting và boundary có thể mô tả rõ bằng pointcut hoặc annotation.

</details>

- [Quay lại đầu trang](#back-to-top)
