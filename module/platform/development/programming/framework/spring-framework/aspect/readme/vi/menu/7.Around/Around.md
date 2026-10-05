<a id="back-to-top"></a>

# Around Advice và ProceedingJoinPoint

## Menu
- [1. Mental model cho @Around](#around-mental-model)
- [2. Demo đo thời gian](#around-timing-demo)
- [3. Demo biến đổi giá trị trả về](#around-transform-demo)
- [4. Demo không gọi proceed()](#around-skip-demo)
- [5. proceed(Object[]) và thay đổi argument](#around-arguments-demo)
- [6. Demo lan truyền exception](#around-exception-demo)
- [7. Kết luận](#around-conclusion)

`@Around` là advice có quyền kiểm soát invocation mạnh nhất trong Spring AOP.

## <a id="around-mental-model">1. Mental model cho @Around</a>

<details>
<summary>Xem chi tiết</summary>

Around advice nhận `ProceedingJoinPoint` và có thể quyết định invocation chain tiếp tục như thế nào.

```text
around: trước
    ↓
proceed()
    ↓
interceptor/advice tiếp theo
    ↓
cuối cùng tới target
    ↓
return hoặc exception đi ngược trở ra
```

`ProceedingJoinPoint#proceed()` không có nghĩa "gọi target trực tiếp". Nó có nghĩa **tiếp tục invocation chain hiện tại**. Nếu còn advisor có precedence thấp hơn, các advisor đó có thể chạy trước khi target được gọi.

Quyền kiểm soát này cho phép `@Around`:

- đọc invocation và argument;
- chạy logic trước và sau khi hoàn thành bình thường;
- thay argument bằng `proceed(Object[])`;
- quan sát hoặc thay đổi return value;
- dừng phần chain còn lại bằng cách không gọi `proceed()`;
- gọi `proceed()` theo điều kiện;
- quan sát, rethrow, wrap hoặc chủ động chuyển đổi exception.

Một đoạn lệnh bình thường sau `proceed()` chỉ chạy khi control quay lại theo luồng bình thường. Phần dọn dẹp cần chạy cả khi thành công lẫn lỗi nên đặt trong `finally` bao quanh `proceed()`.

Quyền này hữu ích cho chính sách như đo thời gian vì cần trạng thái trước và sau invocation, nhưng cũng rất dễ tạo control flow ẩn. Nếu không cần điều khiển ở mức `proceed()`, nên chọn advice type hẹp hơn.

### Tài liệu tham khảo

- Spring Framework Reference — Declaring Advice, Around Advice

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-timing-demo">2. Demo đo thời gian</a>

<details>
<summary>Xem chi tiết</summary>

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

Các event:

```text
around:before-proceed
target:timedOperation
around:after-proceed:elapsed-nanos=...
```

Advice lấy mốc thời gian trước `proceed()` rồi tính elapsed time sau khi return bình thường. Sequence cho thấy target chạy trong khi around advice vẫn đang nằm trên call stack.

Với timing trong production cần ghi nhận cả lỗi, phần kết thúc đo nên đặt trong `finally`:

```java
long start = System.nanoTime();
try {
    return joinPoint.proceed();
}
finally {
    record(System.nanoTime() - start);
}
```

Experiment giữ luồng thành công đơn giản để nested control flow dễ quan sát; phần exception phía sau sẽ cho thấy luồng lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-transform-demo">3. Demo biến đổi giá trị trả về</a>

<details>
<summary>Xem chi tiết</summary>

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

nhưng bên gọi nhận:

```text
original-result|transformed-by-around
```

Around advice lấy result từ `proceed()` rồi return một value khác. Điều này chứng minh một boundary quan trọng:

```text
return value của target
≠ luôn luôn là value mà bên gọi quan sát
```

Transformation có thể là chủ đích, nhưng phải là một phần được mô tả rõ trong cross-cutting contract. Aspect thay đổi kết quả nghiệp vụ ngoài dự kiến sẽ làm code khó suy luận và có thể phá giả định của bên gọi.

Nếu chính sách chỉ cần **quan sát** result thành công, `@AfterReturning` cung cấp cơ chế điều khiển nhỏ và an toàn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-skip-demo">4. Demo không gọi proceed()</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
AroundAdviceController#skip()
```

Endpoint:

```text
GET /aop/around/skip
```

Aspect cố ý **không gọi `proceed()`**.

Response có:

```text
result = returned-without-calling-target
events = [around:skip-proceed]
```

và không có:

```text
target:skippedTarget
```

vì phần invocation chain còn lại chưa từng chạy.

Đây là lý do `@Around` không chỉ là "before + after trong cùng method". Nó có thể short-circuit invocation rồi tự cung cấp result hoặc exception. Khả năng này chỉ phù hợp khi short-circuit là một phần chủ đích của chính sách; nếu không, aspect có thể âm thầm bỏ qua hành vi nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-arguments-demo">5. proceed(Object[]) và thay đổi argument</a>

<details>
<summary>Xem chi tiết</summary>

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

Aspect đọc argument hiện tại, chuẩn hóa thành:

```text
book
```

rồi tiếp tục bằng:

```java
joinPoint.proceed(new Object[]{normalized})
```

Các event:

```text
around:argument-before="  Book  "
around:argument-after=book
target:normalizeArgument:item=book
```

Target nhận argument mới.

Trong **Spring AOP**, `Object[]` truyền vào `proceed(Object[])` là argument list thay thế cho underlying method invocation. Số phần tử phải khớp số argument của method và các value phải tương thích với parameter type tương ứng.

Semantics này khác around advice được compile bằng AspectJ compiler. Trong traditional AspectJ semantics, argument truyền cho `proceed(...)` tương ứng với các value đã bind vào around advice signature, nên quy tắc về số lượng/vị trí dựa trên binding model đó thay vì argument list trực tiếp của underlying method như Spring AOP.

Nếu code cần dùng được cả với Spring AOP và AspectJ weaving, nên bind method argument explicit trong pointcut/advice signature và giữ quan hệ giữa value được bind với value thay thế thật rõ.

Việc đổi argument làm thay đổi dữ liệu target nhận. Chỉ nên dùng khi normalization/transformation là một chính sách tường minh, không dùng như cách âm thầm vá target code.

### Tài liệu tham khảo

- Spring Framework Reference — Declaring Advice, Proceeding with Arguments

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-exception-demo">6. Demo lan truyền exception</a>

<details>
<summary>Xem chi tiết</summary>

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

Aspect quan sát exception rồi rethrow:

```text
around:exception-before-proceed
target:throwsFailure
around:exception-observed=IllegalStateException
around:exception-finally
```

Event trong `finally` vẫn chạy vì đó là phần dọn dẹp bao quanh `proceed()`.

Bắt exception trong `@Around` không bắt buộc phải chuyển lỗi thành kết quả thành công. Với logging, metrics hay auditing, giữ nguyên propagation thường là hành vi ít bất ngờ nhất. Có thể wrap hoặc chuyển đổi exception, nhưng việc đó thay đổi contract mà bên gọi nhìn thấy và phải là quyết định chủ đích.

### Còn việc gọi proceed() nhiều lần?

Around advice có thể gọi `proceed()` nhiều hơn một lần. Mỗi lần gọi sẽ tiếp tục chain thêm một lần, vì vậy downstream advice và target có thể được thực thi lặp lại. Với operation làm thay đổi trạng thái, điều này có thể lặp tác dụng phụ.

Hãy xem `proceed()` là quyền điều khiển invocation chain, không phải callback vô hại. Retry chỉ nên đặt ở đây khi việc thực thi lặp lại là chính sách được thiết kế rõ, cùng semantics về idempotency và xử lý lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-conclusion">7. Kết luận</a>

<details>
<summary>Xem chi tiết</summary>

Mental model cốt lõi:

```text
proceed()
→ tiếp tục interceptor/advice chain hiện tại

proceed(newArgs)
→ tiếp tục với method argument đã thay

không proceed()
→ short-circuit phần chain còn lại

catch / return value khác
→ thay đổi kết quả mà bên gọi quan sát
```

Vì `@Around` có thể điều khiển argument, return value, exception và việc target có thực thi hay không, nó nên biểu diễn một chính sách chủ đích thay vì trở thành advice mặc định cho mọi concern.

</details>

- [Quay lại đầu trang](#back-to-top)
