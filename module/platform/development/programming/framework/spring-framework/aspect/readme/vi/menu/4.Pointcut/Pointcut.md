<a id="back-to-top"></a>

# Pointcut và Method Matching

## Menu
- [1. Pointcut đang match theo cái gì?](#pointcut-mental-model)
- [2. Demo trong module](#pointcut-demo)
- [3. Spring AOP không hỗ trợ toàn bộ AspectJ join point model](#spring-aop-pointcut-boundary)
- [4. Kết luận](#pointcut-conclusion)

Pointcut quyết định **join point nào được chọn**.

## <a id="pointcut-mental-model">1. Pointcut đang match theo cái gì?</a>

<details>
<summary>Click for details</summary>

Một số designator phổ biến:

```text
execution(...)
→ match method execution/signature

within(...)
→ match theo declaring type/package boundary

args(...)
→ match theo runtime argument shape

@annotation(...)
→ match method mang annotation cụ thể

this(...)
→ match theo proxy type

target(...)
→ match theo target type

bean(...)
→ Spring-specific designator, match theo bean name
```

Pointcut cũng có thể đặt tên bằng `@Pointcut` rồi compose bằng:

```text
&&
||
!
```

Việc đặt tên cho pointcut nhỏ giúp expression phức tạp dễ đọc hơn thay vì nhét toàn bộ rule vào một annotation dài.

Mục tiêu không phải dùng expression càng rộng càng tốt. Pointcut nên đủ hẹp để behavior có thể dự đoán được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-demo">2. Demo trong module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
PointcutController#comparePointcuts()
```

Endpoint:

```text
GET /aop/pointcut/compare
```

Controller lần lượt gọi:

```text
PointcutService#byExecution()
PointcutService#byAnnotation()
PointcutService#byArgs("demo")
PointcutService#byRuntimeArgs("runtime-string")
PointcutService#byWithin()
PointcutService#byThisAndTarget()
PointcutService#byBean()
PointcutService#unmatched()
```

Aspect:

```text
PointcutMatchingAspect
```

Response `events` sẽ cho thấy:

```text
pointcut:execution
target:byExecution

pointcut:@annotation
target:byAnnotation

pointcut:args:demo
target:byArgs:demo

pointcut:args-runtime-type=String
target:byRuntimeArgs:runtime-string:declaredType=Object

pointcut:within+named-composition
target:byWithin

(xuất hiện cả pointcut:this-proxy-type và pointcut:target-type)
target:byThisAndTarget

pointcut:bean-name
target:byBean

target:unmatched
```

`unmatched()` cố ý không match bất kỳ advice nào để làm baseline so sánh.

`byRuntimeArgs(Object)` cố ý khai báo parameter là `Object` nhưng Controller truyền một `String`. Advice dùng:

```text
args(java.lang.String)
```

vẫn match vì `args(...)` xét **runtime argument type**. Đây là distinction quan trọng với `execution(...)`: `execution(...)` chủ yếu nhìn declared method signature, còn `args(...)` nhìn shape/type của argument tại runtime.

`byWithin()` còn chứng minh named pointcut composition:

```text
pointcutServiceType()
&&
byWithinMethod()
```

`this(...)` và `target(...)` có thể cùng match trong experiment hiện tại, nhưng semantics khác nhau:

```text
this(...)
→ nhìn type của proxy object

target(...)
→ nhìn type của target object phía sau proxy
```

Không dùng thứ tự giữa hai event `pointcut:this-proxy-type` và `pointcut:target-type` làm learning contract. Chúng đến từ hai advice cùng precedence và experiment chỉ cần chứng minh **cả hai matching rule đều true** cho invocation hiện tại.

Sự khác nhau trở nên rõ hơn khi học JDK proxy và CGLIB ở Advanced.

`bean(pointcutService)` là một designator đặc trưng của Spring AOP: nó chọn join point dựa trên identity/name của Spring bean, thay vì chỉ nhìn Java type hoặc method signature.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-pointcut-boundary">3. Spring AOP không hỗ trợ toàn bộ AspectJ join point model</a>

<details>
<summary>Click for details</summary>

Spring AOP dùng **AspectJ pointcut expression language**, nhưng runtime model vẫn là proxy-based method interception.

Vì vậy không nên suy luận:

```text
Spring hiểu AspectJ pointcut syntax
→ Spring AOP hỗ trợ mọi AspectJ join point
```

Các designator gắn với join point ngoài proxy method execution, ví dụ:

```text
call(...)
get(...)
set(...)
initialization(...)
cflow(...)
cflowbelow(...)
```

không thuộc capability thông thường của proxy-based Spring AOP.

Mental model đúng:

```text
AspectJ expression syntax
        ≠
full AspectJ weaving model
```

Chapter `15.RuntimeBoundary` sẽ nối distinction này với full AspectJ weaving.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-conclusion">4. Kết luận</a>

<details>
<summary>Click for details</summary>

Khi debug AOP, đừng chỉ hỏi "Aspect có chạy không?". Hãy tách thành hai câu hỏi:

```text
1. invocation có đi qua proxy không?
2. pointcut có match invocation đó không?
```

Hai điều kiện này độc lập và đều cần đúng.

</details>

- [Quay lại đầu trang](#back-to-top)
