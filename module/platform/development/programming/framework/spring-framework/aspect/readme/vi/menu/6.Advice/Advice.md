<a id="back-to-top"></a>

# Ngữ nghĩa và vòng đời của Advice

## Menu
- [Ngữ nghĩa của từng loại Advice](#advice-semantics)
- [Luồng thành công](#advice-success-demo)
- [Luồng exception](#advice-failure-demo)
- [Binding tham số cho Advice](#advice-parameter-binding)
- [Tổng hợp Ngữ nghĩa và vòng đời của Advice](#advice-conclusion)

Phần này quan sát điều kiện chạy của `@Before`, `@After`, `@AfterReturning` và `@AfterThrowing`.

## <a id="advice-semantics">Ngữ nghĩa của từng loại Advice</a>

<details>
<summary>Xem chi tiết</summary>

Mỗi advice type mô tả **thời điểm** hành vi chạy và mức quyền điều khiển mà nó nhận được.

```text
@Before
→ chạy trước method execution được chọn
→ không có proceed() để tự quyết định target có chạy hay không
→ nếu chính advice ném exception thì chain dừng

@AfterReturning
→ chỉ chạy sau khi return bình thường
→ có thể bind và quan sát return value
→ không thay thế reference mà bên gọi nhận như @Around có thể làm

@AfterThrowing
→ chạy khi method execution được chọn thoát ra bằng exception phù hợp
→ có thể bind exception đó
→ chủ yếu để quan sát lỗi, không phải cơ chế recovery/control-flow tổng quát

@After
→ có semantics kiểu finally
→ chạy cả khi thành công lẫn khi có exception

@Around
→ bao quanh invocation
→ kiểm soát việc chain có tiếp tục và tiếp tục như thế nào
```

Quy tắc thực tế là chọn **advice có quyền hạn thấp nhất nhưng vẫn đủ cho chính sách**. Một bước kiểm tra chỉ cần chạy trước method sẽ dễ suy luận hơn khi dùng `@Before` thay vì `@Around` với `proceed()` phải đặt chính xác.

Cần phân biệt `@After` với `@AfterReturning`:

```text
return bình thường
→ @AfterReturning tham gia
→ @After cũng tham gia

thoát bằng exception
→ @AfterThrowing tham gia nếu exception binding phù hợp
→ @After cũng tham gia
```

Trong cùng một `@Aspect`, các advice khác loại có precedence semantics của framework. Spring gán precedence theo advice type, còn semantics after-finally làm `@After` thực tế chạy sau `@AfterReturning` hoặc `@AfterThrowing` phù hợp trên chiều đi ra.

Nếu hai advice **cùng loại** trong cùng aspect đều match một join point, thứ tự khai báo trong source code không phải ordering contract được đảm bảo. Khi thứ tự cần deterministic, nên tách concern thành các aspect có order explicit hoặc gộp logic phù hợp.

### Tài liệu tham khảo

- Spring Framework Reference — Declaring Advice
- Spring Framework Reference — Advice Ordering

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-success-demo">Luồng thành công</a>

<details>
<summary>Xem chi tiết</summary>

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

Khi target return bình thường, experiment ghi:

```text
@Before:success
target:success
@AfterReturning:success:success-result
@After:success
```

Sequence này cho thấy hai sự kiện khác nhau. `@AfterReturning` tham gia vì join point hoàn thành bình thường, còn `@After` tham gia vì after-finally advice chạy bất kể kết quả.

Method `@AfterReturning` còn bind return value. Binding hữu ích khi chính sách cần quan sát kết quả, ví dụ ghi metrics hoặc audit một thao tác thành công. Nếu chính sách phải thay thế giá trị mà bên gọi nhận được, `@Around` mới là cơ chế điều khiển phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-failure-demo">Luồng exception</a>

<details>
<summary>Xem chi tiết</summary>

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

Controller bắt exception ở bên ngoài advised service call để experiment vẫn trả về bằng chứng quan sát được.

Các event quan trọng:

```text
@Before:failure
target:failure
@AfterThrowing:failure:IllegalStateException
@After:failure
```

`@AfterReturning` không chạy vì target không return bình thường. `@AfterThrowing` nhận exception thoát ra khỏi method execution được chọn, còn `@After` vẫn chạy theo semantics kiểu finally.

Kiểu exception của parameter được bind bằng `throwing` còn giới hạn matching. Một advice bind `IllegalStateException` sẽ không nhận mọi loại `Throwable`.

Đây là cơ chế quan sát lỗi, không phải recovery tự động. `@AfterThrowing` không biến lỗi thành kết quả thành công và cũng không có `proceed()`. Nếu thật sự cần recovery hoặc chuyển đổi exception trong AOP, thiết kế control flow phải tường minh, thường dùng `@Around` khi đó đúng là boundary phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-parameter-binding">Binding tham số cho Advice</a>

<details>
<summary>Xem chi tiết</summary>

Advice có thể nhận context với type rõ ràng thay vì tự giải mã một mảng argument chung.

Các nguồn phổ biến:

```text
JoinPoint
→ signature, arguments, proxy (getThis), target (getTarget)

args(value)
→ bind runtime argument của method

@annotation(annotation)
→ bind annotation instance

returning = "result"
→ bind return value của luồng thành công

throwing = "throwable"
→ bind exception thoát ra khỏi method execution được chọn
```

Ví dụ trong module:

```text
PointcutMatchingAspect#matchArgs(String value)
TrackingAspect#track(..., TrackExecution trackExecution)
AdviceLifecycleAspect#afterReturning(..., Object result)
AdviceLifecycleAspect#afterThrowing(..., Throwable throwable)
```

Binding không chỉ cung cấp value. Kiểu parameter được khai báo còn có thể thu hẹp applicability. Ví dụ, throwing parameter dùng một exception type cụ thể sẽ giới hạn advice vào các lỗi tương thích.

Dùng `JoinPoint` khi concern cần metadata invocation tổng quát; dùng typed binding khi chính sách cần một argument, annotation, result hoặc exception cụ thể. Signature có type rõ giúp contract dễ review và giảm cast thủ công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-conclusion">Tổng hợp Ngữ nghĩa và vòng đời của Advice</a>

<details>
<summary>Xem chi tiết</summary>

Chọn advice theo lifecycle semantics:

```text
chỉ cần chạy trước
→ @Before

hoàn thành thành công
→ @AfterReturning

hoàn thành bằng exception
→ @AfterThrowing

dọn dẹp bất kể kết quả
→ @After

điều khiển chính invocation
→ @Around
```

Chapter tiếp theo tập trung vào `@Around` vì loại advice này có thể thay đổi argument, return value, exception và cả việc invocation có tiếp tục hay không.

</details>

- [Quay lại đầu trang](#back-to-top)
