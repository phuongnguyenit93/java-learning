<a id="back-to-top"></a>

# Advanced - Spring AOP Runtime Boundary và AspectJ

## Menu
- [1. @AspectJ syntax, Spring AOP runtime](#spring-aop-runtime)
- [2. Boundary với full AspectJ](#spring-aop-vs-aspectj)
- [3. Demo trong module](#runtime-boundary-demo)
- [4. Liên hệ với các Spring feature khác](#framework-connections)
- [5. Kết luận](#runtime-boundary-conclusion)

Chapter cuối đặt đúng boundary giữa **@AspectJ declaration style**, **Spring AOP runtime**, và **full AspectJ weaving**.

## <a id="spring-aop-runtime">1. @AspectJ syntax, Spring AOP runtime</a>

<details>
<summary>Click for details</summary>

Module dùng:

```text
@Aspect
@Pointcut
@Before
@Around
```

nhưng build của module chỉ khai báo Spring Boot AOP starter và không cấu hình AspectJ compiler hoặc load-time weaving agent.

Mental model của module là:

```text
@AspectJ-style declaration
        ↓
Spring ApplicationContext phát hiện Aspect bean
        ↓
Spring AOP auto-proxy infrastructure
        ↓
proxy-based method interception
```

Không nên nhìn package `org.aspectj.lang...` rồi kết luận runtime đang weave bytecode bằng AspectJ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-vs-aspectj">2. Boundary với full AspectJ</a>

<details>
<summary>Click for details</summary>

Spring AOP tập trung vào method execution trên Spring-managed object qua proxy boundary.

Full AspectJ weaving có model rộng hơn và có thể tác động ở những join point không cần đi qua Spring proxy, tùy kiểu weaving/configuration.

Một nuance khác nằm ở around advice argument rewriting. Chapter 6 dùng:

```text
ProceedingJoinPoint#proceed(Object[])
```

theo semantics của Spring AOP: array mới đại diện cho arguments của underlying method invocation. Nếu cùng source Aspect được compile bởi AspectJ compiler thì semantics của arguments truyền vào `proceed(...)` không hoàn toàn giống vậy; chúng gắn với các parameter được bind vào around advice signature. Đây là một lý do không nên coi "@AspectJ syntax giống nhau" là bằng chứng hai runtime hoàn toàn tương đương.

Module này cố ý không chuyển sang compile-time/load-time AspectJ vì mục tiêu chính là hiểu Spring AOP.

Do đó câu hỏi debug chuẩn trong module luôn bắt đầu từ:

```text
1. object có phải Spring-managed/proxied không?
2. invocation có đi qua proxy không?
3. Advisor/pointcut có match không?
4. interceptor/advice chain chạy thế nào?
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-boundary-demo">3. Demo trong module</a>

<details>
<summary>Click for details</summary>

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

Response chứng minh các facts dương:

```text
Aspect tồn tại như Spring bean
Target được inject là AOP proxy
Target class thật vẫn truy xuất được phía sau proxy
```

Events:

```text
runtime-aspect:join-point-kind=method-execution
runtime-aspect:before
target:runtime-boundary
runtime-aspect:after
```

`method-execution` ở đây đến trực tiếp từ `JoinPoint#getKind()`, không phải một string mô tả hard-code từ Controller.

Endpoint không cố "chứng minh bằng reflection" rằng mọi loại AspectJ weaving trên thế giới đều vắng mặt. Boundary đó được xác định bởi chính build/runtime configuration của module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="framework-connections">4. Liên hệ với các Spring feature khác</a>

<details>
<summary>Click for details</summary>

Sau khi hiểu proxy + Advisor + interceptor chain, có thể dùng cùng mental model để đọc nhiều feature declarative trong Spring ecosystem, ví dụ:

```text
transaction management
caching
method security
async method execution
```

Implementation cụ thể của từng feature có thể khác nhau, nhưng câu hỏi về proxy boundary, method interception và self-invocation thường vẫn rất hữu ích.

Một boundary khác cần nhớ với async/reactive API:

```text
@Around đo thời gian method invocation
≠
thời điểm CompletableFuture / Mono / Flux hoàn tất công việc bất đồng bộ
```

Nếu method return một async/reactive container rất nhanh, advice có thể kết thúc trước khi workload thật sự hoàn thành. Muốn đo end-to-end completion cần instrumentation phù hợp với abstraction async/reactive đó, không chỉ đo thời gian quanh `proceed()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-boundary-conclusion">5. Kết luận</a>

<details>
<summary>Click for details</summary>

Advanced Spring AOP kết thúc ở mental model:

```text
Declarative annotation
        ↓
Pointcut / Advisor
        ↓
Auto Proxy Creator
        ↓
Proxy
        ↓
Interceptor chain
        ↓
Target
```

Khi hiểu được chain này, `@Aspect`, `@Around`, `@Order` hay self-invocation không còn là các rule rời rạc mà trở thành hệ quả của cùng một architecture.

</details>

- [Quay lại đầu trang](#back-to-top)
