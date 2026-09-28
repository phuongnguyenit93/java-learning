<a id="back-to-top"></a>

# AOP Terminology

## Menu
- [1. Các thuật ngữ chính](#terminology-map)
- [2. Demo trong module](#terminology-demo)
- [3. @AspectJ style không đồng nghĩa AspectJ weaving](#spring-aop-vs-aspectj-style)
- [4. Kết luận](#terminology-conclusion)

Phần này gắn các thuật ngữ AOP vào một method call thật thay vì học thuộc định nghĩa rời rạc.

## <a id="terminology-map">1. Các thuật ngữ chính</a>

<details>
<summary>Click for details</summary>

Trong experiment của module:

```text
TerminologyAspect
→ Aspect

explainTerms(...)
→ Advice

execution(...TerminologyService.execute(..))
→ Pointcut expression

TerminologyService.execute()
→ Join Point (method execution) đang được chọn

TerminologyService instance
→ Target Object

object được inject vào Controller
→ AOP Proxy
```

Trong **Spring AOP**, join point model là **method execution**. Pointcut chọn một subset các method execution đó, còn proxy boundary quyết định invocation có thực sự đi vào AOP chain hay không.

`weaving` là thuật ngữ AOP tổng quát cho quá trình liên kết Aspect với target/advised object. Spring AOP thực hiện việc này ở runtime bằng proxy; full AspectJ còn có compile-time hoặc load-time weaving và hỗ trợ join point model rộng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="terminology-demo">2. Demo trong module</a>

<details>
<summary>Click for details</summary>

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

Response `facts` cho biết:

- object được inject có phải AOP proxy hay không;
- runtime class của object được inject;
- target class thật.

Response `events` còn ghi:

```text
aspect=TerminologyAspect
advice=@Before
join-point=...
proxy-class=...
target-class=TerminologyService
target:TerminologyService.execute
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-vs-aspectj-style">3. @AspectJ style không đồng nghĩa AspectJ weaving</a>

<details>
<summary>Click for details</summary>

Module sử dụng các annotation:

```text
@Aspect
@Before
@Around
```

Các annotation này thuộc **@AspectJ declaration style**, nhưng runtime của module vẫn là **Spring AOP proxy-based**.

Mental model:

```text
AspectJ annotation syntax
        ↓
Spring đọc metadata + pointcut expression
        ↓
Spring tạo AOP proxy
        ↓
method invocation đi qua proxy
```

Module không cấu hình AspectJ compiler hay load-time weaving agent.

Vì vậy cần tách ba khái niệm:

```text
weaving
→ khái niệm AOP tổng quát

Spring AOP runtime weaving
→ proxy-based

AspectJ compile-time / load-time weaving
→ bytecode weaving + join point model rộng hơn
```

Một distinction khác rất quan trọng:

```text
@Aspect
→ khai báo class có semantics của Aspect

@Component hoặc @Bean
→ đưa Aspect instance vào Spring ApplicationContext
```

`@Aspect` tự nó không phải component-scanning annotation. Các Aspect trong module dùng cả `@Aspect` và `@Component` vì Spring cần Aspect đó tồn tại như một bean để auto-proxy infrastructure sử dụng.

Phần `15.RuntimeBoundary` sẽ quay lại distinction này ở mức infrastructure.

---

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="terminology-conclusion">4. Kết luận</a>

<details>
<summary>Click for details</summary>

Một cách đọc call chain:

```text
Controller
→ AOP Proxy
→ Pointcut chọn Join Point
→ Advice chạy
→ Target Object
```

Aspect là nơi gom cross-cutting behavior; Advice là behavior cụ thể chạy tại một join point được pointcut chọn.

</details>

- [Quay lại đầu trang](#back-to-top)
