<a id="back-to-top"></a>

# Ranh giới Runtime của Spring AOP và AspectJ

## Menu
- [1. @AspectJ syntax và Spring AOP runtime](#spring-aop-runtime)
- [2. Ranh giới với full AspectJ](#spring-aop-vs-aspectj)
- [3. Demo trong module](#runtime-boundary-demo)
- [4. Liên hệ với các Spring feature khác](#framework-connections)
- [5. Tổng hợp end-to-end: concern → proxy creation → advisor chain → target](#aop-end-to-end-synthesis)
- [6. Kết luận](#runtime-boundary-conclusion)

Chương cuối xác lập ranh giới giữa **phong cách khai báo @AspectJ**, **Spring AOP runtime** và **full AspectJ weaving**.

## <a id="spring-aop-runtime">1. @AspectJ syntax và Spring AOP runtime</a>

<details>
<summary>Xem chi tiết</summary>

Module dùng phong cách khai báo `@AspectJ`:

```text
@Aspect
@Pointcut
@Before
@Around
```

nhưng mô hình runtime vẫn là Spring AOP. Module cấu hình hạ tầng AOP dựa trên proxy của Spring và không cấu hình AspectJ compiler hay load-time-weaving agent.

Mô hình là:

```text
khai báo theo @AspectJ
        ↓
Spring chuyển advice phù hợp thành metadata Advisor
        ↓
hạ tầng Spring AOP auto-proxy
        ↓
runtime proxy
        ↓
intercept method execution
```

Các type annotation/runtime của AspectJ xuất hiện trên classpath vì hỗ trợ `@AspectJ` của Spring cần chúng. Chỉ có các type đó không chứng minh application class đang được weave.

Một proxy tạo trực tiếp bằng `ProxyFactory` vẫn thuộc cùng mô hình runtime Spring AOP; nó chỉ bỏ qua bước container tự phát hiện và auto-proxy.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-vs-aspectj">2. Ranh giới với full AspectJ</a>

<details>
<summary>Xem chi tiết</summary>

Spring AOP là hệ thống proxy ở runtime. Trong cách dùng container thông thường, advice chỉ tham gia khi lời gọi thực thi phương thức đi qua proxy do Spring tạo. AspectJ weaving đầy đủ thay đổi mô hình runtime này.

Với AspectJ compile-time weaving (CTW) hoặc load-time weaving (LTW), lớp đã được weave tự chứa cơ chế interception. Advice vì thế không phụ thuộc vào việc lời gọi phải đi qua Spring AOP proxy. AspectJ còn có mô hình join point rộng hơn, gồm method call, constructor và field access mà mô hình method-execution của Spring AOP không cung cấp.

Khác biệt này giải thích self-invocation:

```text
Spring AOP
this.inner()
→ vẫn ở bên trong target
→ không đi qua proxy lần hai

AspectJ weaving
this.inner()
→ mã đã được weave
→ không cần đi qua proxy
```

Chương 7 dùng:

```text
ProceedingJoinPoint#proceed(Object[])
```

theo ngữ nghĩa Spring AOP: mảng truyền vào đại diện cho argument của lời gọi phương thức bên dưới. Nếu cùng mã nguồn Aspect được biên dịch bằng AspectJ compiler, ngữ nghĩa argument truyền cho `proceed(...)` tuân theo các parameter đã bind vào advice và không hoàn toàn giống Spring AOP. Cùng cú pháp `@AspectJ` không có nghĩa hai runtime tương đương.

Module giữ Spring AOP vì đây là mục tiêu học. Nếu yêu cầu cần intercept self-invocation, constructor, field access hoặc join point khác nằm ngoài ranh giới proxy của method execution, đó là dấu hiệu nên đánh giá AspectJ weaving hoặc cơ chế khác.

### Tài liệu tham khảo

- Spring Framework Reference — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- Spring Framework Reference — [Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-boundary-demo">3. Demo trong module</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
RuntimeBoundaryController#inspectRuntimeBoundary()
```

Endpoint:

```text
GET /aop/advanced/runtime-boundary/inspect
```

Target:

```text
RuntimeBoundaryService#execute()
```

Aspect:

```text
RuntimeBoundaryAspect#around(...)
```

Phản hồi chứng minh các dữ kiện dương:

```text
Aspect tồn tại như Spring bean
Target được inject là AOP proxy
Lớp target thật vẫn truy xuất được phía sau proxy
```

Events:

```text
runtime-aspect:join-point-kind=method-execution
runtime-aspect:before
target:runtime-boundary
runtime-aspect:after
```

`method-execution` ở đây đến trực tiếp từ `JoinPoint#getKind()`, không phải chuỗi mô tả được mã hóa cứng trong Controller.

Endpoint không cố "chứng minh bằng reflection" rằng mọi loại AspectJ weaving trên thế giới đều vắng mặt. Boundary đó được xác định bởi chính build/runtime configuration của module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="framework-connections">4. Liên hệ với các Spring feature khác</a>

<details>
<summary>Xem chi tiết</summary>

Sau khi hiểu proxy, Advisor và interceptor chain, có thể dùng cùng mô hình tư duy để đọc nhiều tính năng khai báo trong hệ sinh thái Spring, ví dụ:

```text
transaction management
caching
method security
async method execution
```

Phần triển khai cụ thể của từng tính năng có thể khác nhau, nhưng các câu hỏi về ranh giới proxy, method interception và self-invocation vẫn rất hữu ích. Ngữ nghĩa nghiệp vụ của transaction, cache, async hay security vẫn thuộc module chuyên trách tương ứng.

Một ranh giới khác cần nhớ với API bất đồng bộ hoặc reactive:

```text
@Around đo thời gian của lời gọi phương thức
≠
thời điểm CompletableFuture / Mono / Flux hoàn tất công việc bất đồng bộ
```

Nếu phương thức trả về một container async/reactive rất nhanh, advice có thể kết thúc trước khi công việc thật sự hoàn thành. Muốn đo thời gian end-to-end cần cơ chế đo phù hợp với mô hình bất đồng bộ/reactive đó, không chỉ đo quanh `proceed()`.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-end-to-end-synthesis">5. Tổng hợp end-to-end: concern → proxy creation → advisor chain → target</a>

<details>
<summary>Xem chi tiết</summary>

Có thể suy luận một tính năng Spring AOP theo cùng pipeline:

```text
cross-cutting concern
        ↓
khai báo Aspect / Advice
        ↓
Pointcut xác định phạm vi áp dụng
        ↓
Pointcut + Advice tạo PointcutAdvisor
        ↓
auto-proxy creator đánh giá các bean
        ↓
bên gọi nhận proxy
        ↓
invocation phù hợp đi vào interceptor chain
        ↓
TargetSource cung cấp target
        ↓
phương thức target thực thi
        ↓
giá trị trả về / exception đi ngược qua chain
```

`Advisor` là contract rộng hơn; không phải mọi Advisor đều là `Pointcut + Advice`. `PointcutAdvisor` mới là dạng ghép pointcut với advice, còn Introduction dùng loại Advisor chuyên biệt.

Các nhóm lỗi lớn trong module đều là điểm đứt hoặc giới hạn của pipeline:

```text
không có proxy
→ không có interception tự động qua proxy

lời gọi bỏ qua proxy
→ self-invocation hoặc tham chiếu trực tiếp tới target

phương thức không thể intercept
→ final/private/không nhìn thấy với class-based proxy
  hoặc operation không có trên interface của JDK proxy

pointcut không khớp
→ proxy tồn tại nhưng PointcutAdvisor này không áp dụng

around advice không gọi proceed
→ chain chủ động dừng trước target
```

Mô hình này cũng giải thích vì sao transaction management, caching, async execution và method security có thể dùng hạ tầng AOP mà không sở hữu ngữ nghĩa của AOP. Quy tắc domain của chúng khác nhau; phần dùng chung là cơ chế proxy/advisor/interceptor.

Khi yêu cầu vượt khỏi pipeline này, đặc biệt ra ngoài ranh giới proxy của method execution, thiết kế đã chuyển sang một cơ chế khác như AspectJ weaving.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-boundary-conclusion">6. Kết luận</a>

<details>
<summary>Xem chi tiết</summary>

Module kết thúc với một mô hình runtime:

```text
khai báo / policy
        ↓
Pointcut + Advice
        ↓
PointcutAdvisor
        ↓
auto-proxy creation hoặc ProxyFactory
        ↓
Proxy
        ↓
Interceptor chain
        ↓
TargetSource
        ↓
Target
```

`Advisor` vẫn là contract rộng hơn phía trên các subtype như `PointcutAdvisor` và `IntroductionAdvisor`.

`@Aspect`, `@Around`, ordering, self-invocation, kiểu proxy và cách debug đều là hệ quả của kiến trúc này. AspectJ weaving dùng chung một phần cú pháp khai báo nhưng thay đổi runtime boundary, nên chỉ chọn khi join point cần thiết vượt khỏi mô hình method-execution dựa trên proxy của Spring AOP.
</details>

- [Quay lại đầu trang](#back-to-top)
