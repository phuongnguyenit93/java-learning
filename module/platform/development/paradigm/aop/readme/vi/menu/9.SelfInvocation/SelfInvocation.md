<a id="back-to-top"></a>

# Proxy Limitation và Self-Invocation

## Menu
- [1. Vì sao this.inner() khác external call?](#self-invocation-mental-model)
- [2. Demo trong module](#self-invocation-demo)
- [3. Một số limitation liên quan](#proxy-limitations)
- [4. Final method trên class-based proxy](#final-method-demo)
- [5. Kết luận](#self-invocation-conclusion)

Đây là một trong những limitation quan trọng nhất của proxy-based Spring AOP.

## <a id="self-invocation-mental-model">1. Vì sao this.inner() khác external call?</a>

<details>
<summary>Click for details</summary>

Giả sử target có:

```java
public void outer() {
    inner();
}

@TrackExecution("inner")
public void inner() {
}
```

External call:

```text
Controller
→ Proxy
→ inner()
```

Advice có cơ hội chạy.

Self-invocation:

```text
Controller
→ Proxy
→ outer()
      ↓
   this.inner()
```

Lời gọi `inner()` diễn ra trực tiếp trên target object hiện tại và không quay ra proxy lần thứ hai.

Vì vậy **có annotation không đồng nghĩa advice chắc chắn chạy**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="self-invocation-demo">2. Demo trong module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
SelfInvocationController#compareSelfInvocation()
```

Endpoint:

```text
GET /aop/self-invocation/compare
```

Target methods:

```text
SelfInvocationService#outerCallsInner()
SelfInvocationService#innerTrackedMethod()
```

`innerTrackedMethod()` mang:

```text
@TrackExecution("self-invocation-inner")
```

Response trả hai nhóm event.

### Self invocation

```text
target:outer-before-inner
target:innerTrackedMethod
target:outer-after-inner
```

Không có `track-before` vì `outerCallsInner()` gọi `innerTrackedMethod()` trực tiếp trên cùng object.

### External proxy invocation

Controller gọi trực tiếp bean `selfInvocationService.innerTrackedMethod()`:

```text
track-before:label=self-invocation-inner
target:innerTrackedMethod
track-success:method=innerTrackedMethod
track-finished:elapsed-nanos=...
```

Lần này invocation bắt đầu từ bên ngoài target và đi qua proxy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-limitations">3. Một số limitation liên quan</a>

<details>
<summary>Click for details</summary>

Proxy-based interception còn phụ thuộc khả năng method được proxy strategy hiện tại intercept.

Những case như private method, final method hoặc call nội bộ cần được đánh giá theo proxy mechanism cụ thể. Không nên suy luận đơn giản rằng "thêm annotation là đủ".

Các Spring feature như `@Transactional`, `@Async` hoặc `@Cacheable` cũng thường gặp cùng loại mental model vì chúng có thể dựa trên proxy/interceptor.

### Khi cần tránh self-invocation

Ưu tiên tốt nhất thường là refactor boundary sang một bean khác:

```text
Bean A
→ gọi Bean B proxy
→ advice có cơ hội chạy
```

Spring cũng có các kỹ thuật như self-injection hoặc `AopContext.currentProxy()`, nhưng chúng làm code phụ thuộc rõ hơn vào AOP infrastructure; `AopContext.currentProxy()` còn cần expose proxy. Với learning module này, chúng được xem là escape hatch chứ không phải design mặc định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-method-demo">4. Final method trên class-based proxy</a>

<details>
<summary>Click for details</summary>

Controller:

```text
SelfInvocationController#finalMethodLimitation()
```

Endpoint:

```text
GET /aop/self-invocation/final-method
```

Method:

```java
@TrackExecution("final-method")
public final String finalTrackedMethod() {
    return "final-method-result";
}
```

`SelfInvocationService` cần class-based proxy vì nó không expose interface business cho proxy strategy này.

Class-based proxy hoạt động bằng subclassing. `final` method không thể override, vì vậy interceptor/advice không thể chen vào method đó.

Response vẫn nhận:

```text
final-method-result
```

nhưng `events` không có:

```text
track-before:label=final-method
```

dù annotation vẫn hiện diện trên method.

Hai fact này không được hard-code. Controller đọc method bằng reflection và kiểm tra trực tiếp:

```text
annotationPresentOnFinalMethod = true
methodIsFinal                  = true
```

Tức là experiment tách rõ hai điều độc lập: annotation thật sự tồn tại, method thật sự là `final`, nhưng advice vẫn không intercept được invocation qua class-based proxy.

Đây là một limitation **khác self-invocation**:

```text
self-invocation
→ call không quay lại proxy

final method với class-based proxy
→ proxy subclass không override method được
```

### Private method

Private method cũng không thể được subclass proxy override. Ngoài ra caller bên ngoài target không thể trực tiếp invoke private method như một business boundary công khai.

Vì vậy module không tạo một endpoint giả tạo bằng reflection chỉ để "ép" private method thành demo; mental model cần nhớ là private method không phải join point phù hợp cho proxy-based Spring AOP.

Hai caveat class-based proxy khác cũng cần nhớ:

```text
final class
→ không thể tạo subclass proxy

package-private method được kế thừa từ parent khác package
→ không thể override/intercept như một method nhìn thấy bình thường từ proxy subclass
```

Demo `finalTrackedMethod()` chỉ return constant để tập trung vào interception. Không nên suy luận rằng gọi mọi final method trên một class-based proxy luôn tương đương một invocation đã đi qua target delegation chain; bản chất limitation là proxy subclass **không override được final method**.

---

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="self-invocation-conclusion">5. Kết luận</a>

<details>
<summary>Click for details</summary>

Khi AOP không chạy, câu hỏi đầu tiên nên là:

```text
invocation này có thực sự đi qua proxy không?
```

Thường nên refactor boundary giữa các bean thay vì tìm cách lách proxy model bằng hidden self-reference.

</details>

- [Quay lại đầu trang](#back-to-top)
