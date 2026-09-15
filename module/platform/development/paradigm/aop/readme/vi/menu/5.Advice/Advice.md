<a id="back-to-top"></a>

# Advice Lifecycle

## Menu
- [1. Semantics của từng Advice](#advice-semantics)
- [2. Success path](#advice-success-demo)
- [3. Exception path](#advice-failure-demo)
- [4. Advice parameter binding](#advice-parameter-binding)
- [5. Kết luận](#advice-conclusion)

Phần này quan sát điều kiện chạy của `@Before`, `@After`, `@AfterReturning` và `@AfterThrowing`.

## <a id="advice-semantics">1. Semantics của từng Advice</a>

<details>
<summary>Click for details</summary>

```text
@Before
→ chạy trước target invocation

@AfterReturning
→ chỉ chạy khi target return bình thường

@AfterThrowing
→ chỉ chạy khi target thoát ra bằng exception phù hợp

@After
→ finally-like semantics, chạy khi invocation kết thúc dù success hay exception
```

Không nên hiểu `@After` là "chỉ chạy sau khi thành công".

Mỗi advice type cũng có mức quyền kiểm soát khác nhau:

```text
@Before
→ chạy trước invocation; không có proceed() để chủ động skip target
→ nhưng nếu chính advice throw exception thì chain dừng

@AfterReturning
→ quan sát return value của success path
→ không thay thế return value như @Around

@AfterThrowing
→ quan sát exception thoát ra
→ không phải recovery/control-flow API mạnh như @Around

@Around
→ có proceed() và có thể kiểm soát invocation chain
```

Nên chọn **advice yếu nhất nhưng đủ đáp ứng requirement**. Dùng `@Around` cho mọi concern sẽ trao nhiều quyền điều khiển hơn mức cần thiết và làm flow khó đọc hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-success-demo">2. Success path</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AdviceLifecycleController#success()
```

Endpoint:

```text
GET /aop/advice/success
```

Target:

```text
AdviceLifecycleService#success()
```

Khi target return bình thường, response sẽ có các event tương ứng với:

```text
@Before
target:success
@AfterReturning
@After
```

Điểm cần quan sát là `@AfterReturning` có thể đọc return value.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-failure-demo">3. Exception path</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AdviceLifecycleController#failure()
```

Endpoint:

```text
GET /aop/advice/failure
```

Target cố ý ném:

```text
IllegalStateException("intentional-advice-demo-error")
```

Controller bắt exception để experiment vẫn trả response quan sát được.

Event quan trọng:

```text
@Before
target:failure
@AfterThrowing
@After
```

`@AfterReturning` không chạy trên exception path.

Trong cùng một `@Aspect`, các advice **khác loại** có precedence semantics của framework. Với experiment này, `@AfterReturning`/`@AfterThrowing` được quan sát trước `@After` vì `@After` có finally-like semantics.

Ngược lại, nếu một Aspect khai báo **nhiều advice cùng loại** cùng match một join point, không nên dựa vào thứ tự source code của các method advice như một contract. Nếu thứ tự thật sự quan trọng, tách concern thành Aspect riêng và đặt precedence rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-parameter-binding">4. Advice parameter binding</a>

<details>
<summary>Click for details</summary>

Các chapter trong module đang dùng nhiều kiểu binding khác nhau. Gom lại thành một mental model:

```text
JoinPoint
→ signature / this / target / arguments

args(value)
→ bind runtime argument vào parameter value

@annotation(annotation)
→ bind annotation instance vào advice parameter

returning = "result"
→ bind return value của invocation thành công

throwing = "throwable"
→ bind exception thoát ra khỏi invocation
```

Ví dụ thực tế trong module:

```text
PointcutMatchingAspect#matchArgs(String value)
TrackingAspect#track(..., TrackExecution trackExecution)
AdviceLifecycleAspect#afterReturning(..., Object result)
AdviceLifecycleAspect#afterThrowing(..., Throwable throwable)
```

Điểm quan trọng là advice có thể nhận đúng dữ liệu mà pointcut/advice declaration đã bind, thay vì mọi advice đều phải tự parse một `Object[]` chung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-conclusion">5. Kết luận</a>

<details>
<summary>Click for details</summary>

Chọn advice dựa trên lifecycle semantics, không dựa trên tên nghe "có vẻ phù hợp".

Nếu cần toàn quyền bao quanh invocation hoặc thay đổi control flow, chuyển sang `@Around` ở chapter tiếp theo.

</details>

- [Quay lại đầu trang](#back-to-top)
