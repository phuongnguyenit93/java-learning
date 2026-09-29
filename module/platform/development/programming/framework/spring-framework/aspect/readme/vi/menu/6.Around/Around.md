<a id="back-to-top"></a>

# Around Advice và ProceedingJoinPoint

## Menu
- [1. Mental model của @Around](#around-mental-model)
- [2. Demo đo thời gian](#around-timing-demo)
- [3. Demo thay đổi return value](#around-transform-demo)
- [4. Demo không gọi proceed()](#around-skip-demo)
- [5. Demo proceed(Object[]) và thay đổi argument](#around-arguments-demo)
- [6. Demo exception propagation](#around-exception-demo)
- [7. Kết luận](#around-conclusion)

`@Around` là advice có quyền kiểm soát invocation mạnh nhất trong Spring AOP.

## <a id="around-mental-model">1. Mental model của @Around</a>

<details>
<summary>Click for details</summary>

```text
around before
    ↓
proceed()
    ↓
target method
    ↓
return / exception
    ↓
around after
```

`ProceedingJoinPoint#proceed()` là bước chuyển quyền thực thi sang advice tiếp theo hoặc target.

Vì vậy `@Around` có thể:

- đo thời gian;
- đọc argument;
- thay đổi argument trước khi tiếp tục invocation;
- quan sát return value;
- thay đổi return value;
- quyết định không gọi target;
- propagate hoặc transform exception.

Quyền kiểm soát lớn cũng đồng nghĩa nguy cơ tạo behavior khó đoán nếu dùng quá mức.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-timing-demo">2. Demo đo thời gian</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#timing()
```

Endpoint:

```text
GET /aop/around/timing
```

Target:

```text
AroundAdviceService#timedOperation()
```

Event:

```text
around:before-proceed
target:timedOperation
around:after-proceed:elapsed-nanos=...
```

Điểm cần quan sát là code sau `proceed()` chỉ chạy sau khi target đã hoàn thành hoặc control quay trở lại advice.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-transform-demo">3. Demo thay đổi return value</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#transform()
```

Endpoint:

```text
GET /aop/around/transform
```

Target trả:

```text
original-result
```

Nhưng caller nhận:

```text
original-result|transformed-by-around
```

Experiment này chứng minh advice có thể thay đổi contract mà caller quan sát được. Trong code thật, capability này cần dùng rất thận trọng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-skip-demo">4. Demo không gọi proceed()</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#skip()
```

Endpoint:

```text
GET /aop/around/skip
```

Aspect cố ý **không gọi `proceed()`**.

Response sẽ có:

```text
result = returned-without-calling-target
events = [around:skip-proceed]
```

Sẽ không có:

```text
target:skippedTarget
```

vì target method chưa từng được thực thi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-arguments-demo">5. Demo proceed(Object[]) và thay đổi argument</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#arguments()
```

Endpoint:

```text
GET /aop/around/arguments
```

Controller truyền:

```text
"  Book  "
```

Aspect đọc argument từ `joinPoint.getArgs()`, normalize thành:

```text
book
```

rồi tiếp tục bằng:

```java
joinPoint.proceed(new Object[]{normalized})
```

Event:

```text
around:argument-before="  Book  "
around:argument-after=book
target:normalizeArgument:item=book
```

Target thật sự nhận argument mới chứ không phải argument ban đầu.

Capability này mạnh nhưng cũng dễ làm contract trở nên khó đoán, nên chỉ dùng khi transformation là một phần rõ ràng của cross-cutting contract.

### `proceed(Object[])` trong Spring AOP và AspectJ

Experiment này chạy trên **proxy-based Spring AOP**. Ở runtime này, array truyền vào:

```java
joinPoint.proceed(new Object[]{normalized})
```

đại diện cho argument list mới của underlying method invocation và số phần tử phải phù hợp với method arguments.

Không nên suy luận rằng `proceed(Object[])` có semantics giống hệt khi advice được compile bởi AspectJ compiler. Với AspectJ-compiled around advice, arguments truyền vào `proceed(...)` gắn với các parameter đã bind vào around advice signature, nên rule về số lượng/vị trí argument khác Spring AOP.

Nếu một Aspect cần portable giữa Spring AOP và AspectJ, nên bind argument rõ ràng trong pointcut/advice signature thay vì phụ thuộc vào assumption riêng của một runtime.

---

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-exception-demo">6. Demo exception propagation</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#exceptionPropagation()
```

Endpoint:

```text
GET /aop/around/exception
```

Target cố ý ném:

```text
IllegalStateException("intentional-around-demo-error")
```

Aspect quan sát exception nhưng **rethrow** thay vì swallow:

```text
around:exception-before-proceed
target:throwsFailure
around:exception-observed=IllegalStateException
around:exception-finally
```

Điểm cần học:

```text
catch exception trong @Around
≠
bắt buộc phải chuyển exception thành success
```

Giữ nguyên propagation thường là behavior ít bất ngờ hơn nếu Aspect chỉ đang logging/metrics/auditing.

---

### Còn việc gọi proceed() nhiều lần?

Về mặt kỹ thuật `@Around` có thể thay đổi control flow rất sâu, nhưng module không biến việc gọi `proceed()` nhiều lần thành một pattern khuyến nghị. Nếu làm vậy, target có thể thực thi nhiều lần và tạo side effect lặp.

Điều cần nhớ là `proceed()` đại diện cho **tiếp tục invocation chain**, không phải một callback vô hại có thể gọi tùy ý.

---

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-conclusion">7. Kết luận</a>

<details>
<summary>Click for details</summary>

`@Around` không chỉ là "before + after trong một method". Nó là advice có thể điều khiển chính invocation.

Mental model an toàn:

```text
proceed()
→ tiếp tục call chain

không proceed()
→ chain dừng tại advice hiện tại
```

</details>

- [Quay lại đầu trang](#back-to-top)
