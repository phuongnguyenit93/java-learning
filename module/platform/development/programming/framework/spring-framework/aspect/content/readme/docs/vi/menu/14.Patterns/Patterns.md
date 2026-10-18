<a id="back-to-top"></a>

# Đánh đổi thiết kế, Pattern và Pitfall trong AOP

## Menu
- [Các pattern phù hợp với AOP](#practical-patterns)
- [Khi AOP là abstraction không phù hợp: ưu tiên composition tường minh và workflow dễ thấy](#aop-design-tradeoffs)
- [Demo auditing và timing](#practical-demo)
- [Các pitfall cần tránh](#aop-pitfalls)
- [Tổng hợp Đánh đổi thiết kế, Pattern và Pitfall trong AOP](#practical-conclusion)

Chương này ghép mô hình tư duy AOP vào một pattern gần với mã ứng dụng thực tế.

## <a id="practical-patterns">Các pattern phù hợp với AOP</a>

<details>
<summary>Xem chi tiết</summary>

Các hành vi thường phù hợp với AOP gồm:

- ghi log thực thi;
- đo thời gian hoặc metric;
- audit;
- tracing hook;
- kiểm tra policy theo kiểu khai báo;
- hành vi cross-cutting kích hoạt bằng annotation.

Chúng có hình dạng chung:

```text
cùng một policy áp dụng trên nhiều target
        +
có ranh giới interception ổn định
        +
policy tách khỏi luồng nghiệp vụ chính
```

Một concern AOP tốt thường có thể mô tả như policy trên ranh giới phương thức: "ghi lại mọi operation cần audit", "đo thời gian các lời gọi ở service layer", hoặc "gắn tracing context quanh các entry point này".

Khi đọc riêng service nghiệp vụ, người học vẫn phải hiểu được operation chính mà không cần mở Aspect. Nếu một bước bắt buộc của luồng nghiệp vụ nằm trong Aspect thì AOP đang che quá nhiều logic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-design-tradeoffs">Khi AOP là abstraction không phù hợp: ưu tiên composition tường minh và workflow dễ thấy</a>

<details>
<summary>Xem chi tiết</summary>

AOP không còn phù hợp khi hành vi là một phần của luồng nghiệp vụ thay vì một chính sách cross-cutting ổn định.

Nên dùng cách kết hợp tường minh khi người đọc cần nhìn thấy quan hệ phụ thuộc hoặc thứ tự để hiểu tính đúng đắn:

```text
giữ hàng
→ thanh toán
→ phát order event
```

Đó là các bước nghiệp vụ. Giấu một bước phía sau pointcut làm luồng điều khiển khó tìm, khó kiểm thử và khó suy luận.

Có thể tự hỏi theo chuỗi sau:

```text
Hành vi có áp dụng cho nhiều đối tượng đích vì cùng một lý do chính sách?
        ↓ có
Có ranh giới phương thức ổn định để intercept?
        ↓ có
Đối tượng đích vẫn giữ contract nghiệp vụ rõ khi tách chính sách ra?
        ↓ có
AOP có thể phù hợp
```

Ưu tiên đối tượng cộng tác tường minh, decorator, filter/interceptor ở đúng tầng giao thức hoặc lời gọi phương thức thông thường khi hành vi cần luồng dữ liệu dễ thấy, thứ tự nghiệp vụ rõ ràng hoặc nhánh xử lý theo domain.

AOP cũng có chi phí vận hành: kiểu proxy ảnh hưởng hành vi, self-invocation có thể bypass advice, pointcut có thể lệch khi package hoặc annotation thay đổi, và nhiều Advisor có thể tạo phụ thuộc ẩn về ordering. Chỉ đáng trả các chi phí đó khi việc tập trung hóa chính sách cross-cutting làm thiết kế tổng thể dễ hiểu hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-demo">Demo auditing và timing</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
PracticalPatternController#checkout(String)
```

Endpoint:

```text
GET /aop/patterns/checkout?item=book
```

Phương thức nghiệp vụ:

```text
PracticalPatternService#checkout(String)
```

Nó chỉ thực hiện hành vi chính và khai báo:

```text
@AuditedOperation(action = "checkout")
```

Aspect:

```text
PracticalPatternAspect#audit(...)
```

Chuỗi sự kiện trong phản hồi cho thấy:

```text
audit-start:action=checkout
target:checkout:item=book
audit-success:method=checkout
metric:elapsed-nanos=...
```

Service nghiệp vụ không phải tự viết audit start/success hoặc timing.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pitfalls">Các pitfall cần tránh</a>

<details>
<summary>Xem chi tiết</summary>

### Pointcut quá rộng

```text
execution(* com.example..*(..))
```

có thể intercept nhiều phương thức hơn dự kiến và làm hành vi khó dự đoán.

### Aspect chứa logic nghiệp vụ

Nếu quyết định giá, trạng thái order hoặc luồng nghiệp vụ chính nằm trong Aspect, quan hệ phụ thuộc trở nên ẩn và khó test/debug.

### Thay đổi argument/return value âm thầm

`@Around` có thể làm được, nhưng khả năng kỹ thuật không đồng nghĩa đó là thiết kế tốt.

### Swallow exception

Aspect bắt exception rồi trả một giá trị giả có thể phá contract của target và che lỗi.

### Quá nhiều phụ thuộc vào ordering

Nếu tính đúng đắn phụ thuộc một chuỗi `@Order` phức tạp, cách dùng này thường đã vượt quá mức AOP nên gánh.

### Trạng thái có thể thay đổi trong Aspect

Aspect bean thông thường là singleton trong ApplicationContext. Vì vậy trường có thể thay đổi như:

```java
private int currentRequestCount;
```

có thể bị nhiều request/thread cùng truy cập và tạo race condition hoặc rò rỉ trạng thái giữa các invocation.

Ưu tiên Aspect stateless. Nếu thật sự cần trạng thái, phải thiết kế rõ scope và ngữ nghĩa concurrency thay vì mặc định coi mỗi invocation có một Aspect instance riêng.

`AopTraceLog` trong module dùng `ThreadLocal` chỉ để tách sự kiện của từng request/thread khi quan sát thử nghiệm. Đây là cơ chế ghi nhận phục vụ học tập, không phải lý do để đặt trạng thái nghiệp vụ vào `ThreadLocal` của Aspect.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-conclusion">Tổng hợp Đánh đổi thiết kế, Pattern và Pitfall trong AOP</a>

<details>
<summary>Xem chi tiết</summary>

Mục tiêu không chỉ là xóa mã lặp. AOP nên tập trung hóa một cross-cutting policy ổn định trong khi vẫn giữ luồng nghiệp vụ dễ đọc và ranh giới interception dễ dự đoán.

Nếu muốn hiểu một use case mà phải dựng lại nhiều pointcut ẩn cùng các luật ordering, composition tường minh thường là thiết kế rõ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
