<a id="back-to-top"></a>

# Thuật ngữ AOP

## Menu
- [1. Thuật ngữ cốt lõi](#terminology-map)
- [2. Demo trong module](#terminology-demo)
- [3. @AspectJ style không đồng nghĩa với AspectJ weaving](#spring-aop-vs-aspectj-style)
- [4. Kết luận](#terminology-conclusion)

Phần này gắn các thuật ngữ AOP vào một method call thật thay vì học thuộc định nghĩa rời rạc.

## <a id="terminology-map">1. Thuật ngữ cốt lõi</a>

<details>
<summary>Xem chi tiết</summary>

Hãy dùng một invocation làm bản đồ chung:

```text
bên gọi
  ↓
AOP proxy
  ↓
advisor / interceptor chain
  ↓
target object
  ↓
method execution
```

Mỗi thuật ngữ mô tả một phần khác nhau của đường đi này.

**Aspect** gom một cross-cutting concern. Một aspect có thể chứa pointcut, advice và trạng thái.

**Advice** là hành vi chạy tại join point đã được chọn. Spring AOP hỗ trợ before, after-returning, after-throwing, after-finally và around advice.

**Join point** là một điểm trong quá trình thực thi chương trình. Spring AOP chủ động thu hẹp khái niệm đó: join point luôn là **method execution** có thể đi qua mô hình interception dựa trên proxy.

**Pointcut** là predicate chọn join point. Nó trả lời advice áp dụng ở đâu; bản thân pointcut không thực hiện hành vi.

**Target object** là object ứng dụng cuối cùng thực thi method.

**AOP proxy** là object mà bên gọi thường nhìn thấy khi Spring áp dụng AOP. Proxy nhận lời gọi, chạy interceptor/advice chain phù hợp rồi delegate về target.

**Advisor** là abstraction cấp thấp của Spring AOP, kết hợp advice với quy tắc xác định nơi advice áp dụng. Khi dùng annotation-style aspect, Spring chuyển metadata đó thành advisor/interceptor infrastructure ở phía dưới; chapter Advisor sẽ đi sâu hơn.

**Weaving** là thuật ngữ AOP tổng quát cho việc liên kết aspect với type/object của ứng dụng để tạo hành vi được advise. Spring AOP thực hiện liên kết đó ở runtime bằng proxy. Full AspectJ có thể weave ở compile time hoặc load time và có join-point model rộng hơn.

Ánh xạ vào module:

```text
TerminologyAspect
→ Aspect

explainTerms(...)
→ Advice

execution(...TerminologyService.execute(..))
→ Pointcut expression

TerminologyService.execute()
→ Join Point dạng method execution được chọn

TerminologyService instance
→ Target Object

object được inject vào Controller
→ AOP Proxy
```

Quan hệ quan trọng nhất là:

```text
proxy boundary
+ pointcut match
→ advice mới có thể tham gia
```

Pointcut match nhưng call không đi qua proxy vẫn chưa đủ.

### Tài liệu tham khảo

- Spring Framework Reference — AOP Concepts
- Spring Framework Reference — Spring AOP Capabilities and Goals

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="terminology-demo">2. Demo trong module</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
TerminologyController#inspectTerminology()
```

Endpoint:

```text
GET /aop/terminology/inspect
```

Target:

```text
TerminologyService#execute()
```

Advice:

```text
TerminologyAspect#explainTerms(...)
```

Response `facts` cho biết object được inject có phải AOP proxy hay không, runtime class của nó và target class thật. Chuỗi `events` ghi lại cùng mental model từ ứng dụng đang chạy:

```text
aspect=TerminologyAspect
advice=@Before
join-point=...
proxy-class=...
target-class=TerminologyService
target:TerminologyService.execute
```

Hãy đọc output như bằng chứng về vai trò của từng thành phần, không xem tên generated proxy class là contract. Tên runtime class là chi tiết triển khai; các identity cần giữ là proxy, target, pointcut, join point và advice.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-vs-aspectj-style">3. @AspectJ style không đồng nghĩa với AspectJ weaving</a>

<details>
<summary>Xem chi tiết</summary>

Spring có thể đọc các AspectJ annotation như:

```text
@Aspect
@Before
@Around
@Pointcut
```

Đây là **@AspectJ declaration style**. Vocabulary của annotation đến từ AspectJ, nhưng Spring vẫn có thể dùng chúng với runtime là pure proxy-based Spring AOP.

Mental model:

```text
@AspectJ metadata
        ↓
Spring đọc aspect + pointcut declaration
        ↓
Spring AOP tạo advisor/interceptor
        ↓
Spring tạo AOP proxy cho object phù hợp
        ↓
method invocation đi qua proxy
```

Chỉ có `@Aspect` không đồng nghĩa ứng dụng đang dùng AspectJ compiler hoặc load-time weaver.

Cũng cần tách declaration khỏi bean registration:

```text
@Aspect
→ đánh dấu class là aspect

@Component / @Bean / XML bean definition
→ đưa aspect instance vào ApplicationContext
```

`@Aspect` tự nó không phải component-scanning stereotype. Khi @AspectJ auto-proxying được bật, Spring phát hiện các aspect **bean** rồi dùng chúng để cấu hình AOP proxy.

Nếu yêu cầu cần intercept constructor, field access hoặc join point khác ngoài proxy method execution, full AspectJ weaving là một lựa chọn runtime khác, không chỉ là cách viết annotation khác của cùng cơ chế Spring AOP.

### Tài liệu tham khảo

- Spring Framework Reference — @AspectJ support
- Spring Framework Reference — Declaring an Aspect
- Spring Framework Reference — Using AspectJ with Spring Applications

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="terminology-conclusion">4. Kết luận</a>

<details>
<summary>Xem chi tiết</summary>

Hãy đọc một Spring AOP call từ ngoài vào trong:

```text
bên gọi
→ proxy
→ advisor/interceptor chain phù hợp
→ advice quanh method-execution join point được chọn
→ target object
```

Các thuật ngữ này liên quan chặt với nhau nhưng không đồng nghĩa. Tách đúng vai trò giúp tránh nhầm lẫn về pointcut matching, giới hạn proxy và ranh giới giữa Spring AOP với AspectJ weaving.

</details>

- [Quay lại đầu trang](#back-to-top)
