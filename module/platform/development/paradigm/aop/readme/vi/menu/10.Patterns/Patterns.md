<a id="back-to-top"></a>

# Practical Patterns và Pitfalls

## Menu
- [1. Pattern phù hợp với AOP](#practical-patterns)
- [2. Demo auditing + timing](#practical-demo)
- [3. Pitfalls cần tránh](#aop-pitfalls)
- [4. Kết luận](#practical-conclusion)

Chapter cuối của phần **Fundamentals** ghép mental model AOP vào một pattern gần với code thật.

## <a id="practical-patterns">1. Pattern phù hợp với AOP</a>

<details>
<summary>Click for details</summary>

Các behavior thường phù hợp:

- execution logging;
- timing/metrics;
- auditing;
- tracing hook;
- declarative authorization boundary;
- annotation-driven cross-cutting behavior.

Điểm chung:

```text
behavior xuất hiện ở nhiều target
        +
boundary rõ ràng
        +
không phải business logic cốt lõi
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-demo">2. Demo auditing + timing</a>

<details>
<summary>Click for details</summary>

Controller:

```text
PracticalPatternController#checkout(String)
```

Endpoint:

```text
GET /aop/patterns/checkout?item=book
```

Business method:

```text
PracticalPatternService#checkout(String)
```

Nó chỉ thực hiện behavior chính và khai báo:

```text
@AuditedOperation(action = "checkout")
```

Aspect:

```text
PracticalPatternAspect#audit(...)
```

Response `events` cho thấy:

```text
audit-start:action=checkout
target:checkout:item=book
audit-success:method=checkout
metric:elapsed-nanos=...
```

Business service không phải tự viết audit start/success hoặc timing.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pitfalls">3. Pitfalls cần tránh</a>

<details>
<summary>Click for details</summary>

### Pointcut quá rộng

```text
execution(* com.example..*(..))
```

có thể intercept nhiều method hơn ý định và làm behavior khó dự đoán.

### Aspect chứa business logic

Nếu quyết định giá, trạng thái order hoặc workflow chính nằm trong Aspect, dependency trở nên ẩn và khó test/debug.

### Thay đổi argument/return value âm thầm

`@Around` có thể làm được, nhưng khả năng kỹ thuật không đồng nghĩa đó là design tốt.

### Swallow exception

Aspect bắt exception rồi trả một giá trị giả có thể phá contract của target và che lỗi.

### Quá nhiều ordering dependency

Nếu correctness phụ thuộc một chuỗi `@Order` phức tạp, abstraction thường đã vượt quá mức AOP nên gánh.

### Mutable state trong Aspect

Aspect bean thông thường là singleton trong ApplicationContext. Vì vậy field mutable kiểu:

```java
private int currentRequestCount;
```

có thể bị nhiều request/thread cùng truy cập và tạo race condition hoặc leak state giữa các invocation.

Ưu tiên Aspect stateless. Nếu thật sự cần state, phải thiết kế scope/concurrency semantics rõ ràng thay vì mặc định coi mỗi invocation có một Aspect instance riêng.

`AopTraceLog` trong learning module dùng `ThreadLocal` chỉ để tách event của từng request/thread khi quan sát experiment. Đây là instrumentation phục vụ học tập, không phải lý do để nhét business state vào `ThreadLocal` của Aspect.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-conclusion">4. Kết luận</a>

<details>
<summary>Click for details</summary>

Mental model cuối cùng:

```text
AOP tốt
→ cross-cutting
→ declarative
→ boundary rõ
→ behavior có thể dự đoán

AOP xấu
→ business flow bị giấu
→ pointcut quá rộng
→ control flow khó theo dõi
```

AOP nên làm business code sạch hơn mà không biến runtime behavior thành một hệ thống ngầm khó hiểu.

</details>

- [Quay lại đầu trang](#back-to-top)
