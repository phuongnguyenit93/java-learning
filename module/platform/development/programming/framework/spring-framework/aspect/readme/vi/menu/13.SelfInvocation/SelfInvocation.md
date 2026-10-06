<a id="back-to-top"></a>

# Mô hình lỗi của Proxy và Self-Invocation

## Menu
- [Vì sao this.inner() khác lời gọi từ bên ngoài?](#self-invocation-mental-model)
- [Minh chứng thực thi cho Mô hình lỗi của Proxy và Self-Invocation](#self-invocation-demo)
- [Các giới hạn proxy liên quan](#proxy-limitations)
- [Xử lý self-invocation: refactor, self injection và AopContext](#self-invocation-remediation)
- [Final method trên class-based proxy](#final-method-demo)
- [Checklist debug: tạo proxy → proxy boundary → pointcut → advisor chain](#aop-debugging-checklist)
- [Tổng hợp Mô hình lỗi của Proxy và Self-Invocation](#self-invocation-conclusion)

Đây là một trong những giới hạn quan trọng nhất của Spring AOP dựa trên proxy.

## <a id="self-invocation-mental-model">Vì sao this.inner() khác lời gọi từ bên ngoài?</a>

<details>
<summary>Xem chi tiết</summary>

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

Lời gọi `inner()` diễn ra trực tiếp trên đối tượng target hiện tại và không quay ra proxy lần thứ hai.

Vì vậy **có annotation không đồng nghĩa advice chắc chắn chạy**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="self-invocation-demo">Minh chứng thực thi cho Mô hình lỗi của Proxy và Self-Invocation</a>

<details>
<summary>Xem chi tiết</summary>

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

Phản hồi trả về hai nhóm sự kiện.

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

## <a id="proxy-limitations">Các giới hạn proxy liên quan</a>

<details>
<summary>Xem chi tiết</summary>

Proxy-based interception phụ thuộc cả đường đi của lời gọi lẫn khả năng mà kiểu proxy hiện tại có thể phơi bày hoặc override.

Với JDK dynamic proxy, bên gọi thực thi operation qua các interface của proxy. Với class-based proxy, Spring có thể intercept các phương thức có thể override và nhìn thấy từ subclass sinh ra, nhưng không thể override phương thức `final` hoặc `private`. Một phương thức thực tế không nhìn thấy từ subclass, chẳng hạn package-private method kế thừa từ parent ở package khác, cũng không thể được advice qua ranh giới subclass đó.

Những giới hạn này tách biệt với pointcut matching:

```text
pointcut khớp
+
invocation không đi qua một phương thức proxy có thể intercept
=
advice vẫn không thể chạy
```

Các tính năng như `@Transactional`, `@Async` và `@Cacheable` có thể dùng cùng cơ chế proxy/interceptor, nhưng ngữ nghĩa nghiệp vụ của chúng thuộc các module chuyên trách.

### Tài liệu tham khảo

- Spring Framework Reference — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="self-invocation-remediation">Xử lý self-invocation: refactor, self injection và AopContext</a>

<details>
<summary>Xem chi tiết</summary>

Có ba cách xử lý phổ biến, với mức coupling khác nhau.

**1. Refactor ranh giới cộng tác.** Chuyển operation cần advice sang một bean cộng tác rồi gọi bean đó qua tham chiếu được inject:

```text
Bean A
→ Bean B proxy
→ advice
→ Bean B target
```

Cách này làm ranh giới interception hiện rõ trong thiết kế object và thường dễ test, dễ suy luận nhất.

**2. Self injection.** Bean có thể gọi một tham chiếu được inject tới chính proxy của nó thay vì gọi `this`. Interception được giữ lại, nhưng lớp lúc này phụ thuộc vào wiring của container để tự gọi mình. Cơ chế tự tham chiếu và ngữ nghĩa của circular dependency trở thành một phần thiết kế, nên chỉ dùng khi thực sự có lý do.

**3. `AopContext.currentProxy()`.** Đây là cách coupling mạnh nhất. Current proxy chỉ có sẵn khi bật expose proxy, ví dụ `@EnableAspectJAutoProxy(exposeProxy = true)` hoặc `ProxyFactory#setExposeProxy(true)`, và chỉ trong lúc đang thực thi một invocation mà Spring đã expose proxy đó.

Spring Reference khuyến nghị tránh self-invocation khi có thể và xem `AopContext.currentProxy()` như giải pháp cuối. Sửa ranh giới giữa các bean thường bền vững hơn việc để business code tự tìm proxy.

### Tài liệu tham khảo

- Spring Framework Reference — [Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- Spring Framework 6.1.x API — [`AopContext`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/aop/framework/AopContext.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-method-demo">Final method trên class-based proxy</a>

<details>
<summary>Xem chi tiết</summary>

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

Phản hồi vẫn nhận được:

```text
final-method-result
```

nhưng `events` không có:

```text
track-before:label=final-method
```

dù annotation vẫn hiện diện trên method.

Hai dữ kiện này không được mã hóa cứng. Controller đọc phương thức bằng reflection và kiểm tra trực tiếp:

```text
annotationPresentOnFinalMethod = true
methodIsFinal                  = true
```

Thử nghiệm vì thế tách rõ hai điều độc lập: annotation thật sự tồn tại, phương thức thật sự là `final`, nhưng advice vẫn không intercept được invocation qua class-based proxy.

Đây là một giới hạn **khác self-invocation**:

```text
self-invocation
→ call không quay lại proxy

final method với class-based proxy
→ proxy subclass không override method được
```

### Private method

Phương thức private cũng không thể được subclass proxy override. Ngoài ra, bên gọi bên ngoài target không thể trực tiếp invoke phương thức private như một ranh giới nghiệp vụ công khai.

Vì vậy module không tạo endpoint giả bằng reflection chỉ để "ép" private method thành demo; mô hình tư duy cần nhớ là private method không phải join point phù hợp cho Spring AOP dựa trên proxy.

Hai caveat class-based proxy khác cũng cần nhớ:

```text
final class
→ không thể tạo subclass proxy

package-private method được kế thừa từ parent khác package
→ không thể override/intercept như một method nhìn thấy bình thường từ proxy subclass
```

Demo `finalTrackedMethod()` chỉ trả về một hằng để tập trung vào interception. Không nên suy luận rằng gọi mọi phương thức final trên class-based proxy luôn tương đương invocation đã đi qua target delegation chain; bản chất giới hạn là proxy subclass **không thể override phương thức final**.

---

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-debugging-checklist">Checklist debug: tạo proxy → proxy boundary → pointcut → advisor chain</a>

<details>
<summary>Xem chi tiết</summary>

Khi advice không chạy, hãy debug theo đúng thứ tự của kiến trúc runtime:

```text
1. proxy đã được tạo chưa?
2. invocation có đi qua proxy không?
3. phương thức có thể intercept không?
4. pointcut / Advisor có khớp không?
5. ordering và hành vi của advice có đúng không?
```

**Tạo proxy:** xác nhận tham chiếu mà bên gọi đang giữ thật sự là AOP proxy. `AopUtils.isAopProxy(...)`, runtime class hoặc `Advised` trên proxy non-opaque có thể cung cấp bằng chứng.

**Đường đi của invocation:** xác nhận bên gọi thực sự gọi proxy. Tham chiếu raw target, object tạo trực tiếp bằng `new` hoặc `this.inner()` đều có thể bỏ qua ranh giới proxy mong đợi.

**Khả năng intercept:** với JDK proxy, kiểm tra operation có nằm trên interface mà bên gọi sử dụng hay không. Với class-based proxy, kiểm tra `final`, `private` và visibility.

**Pointcut / Advisor:** xem đúng method, lớp target, annotation, runtime argument khi có, cùng danh sách Advisor áp dụng cho proxy. Không nên sửa pointcut trước khi biết việc tạo proxy và đường đi lời gọi đã đúng.

**Ordering và advice:** nếu nhiều Advisor cùng khớp, kiểm tra thứ tự của chúng và xem around interceptor có gọi `proceed()`, gọi nhiều lần, biến đổi kết quả hoặc thay đổi exception contract hay không.

Trình tự này tránh lỗi debug phổ biến: sửa quy tắc chọn trong khi invocation thực tế chưa bao giờ đi qua proxy cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="self-invocation-conclusion">Tổng hợp Mô hình lỗi của Proxy và Self-Invocation</a>

<details>
<summary>Xem chi tiết</summary>

Khi AOP không chạy, trước hết hãy xác nhận proxy có tồn tại và invocation có đi qua nó hay không. Sau đó mới kiểm tra khả năng intercept của phương thức, pointcut và Advisor chain.

Riêng với self-invocation, refactor ranh giới cộng tác thường rõ ràng hơn việc thêm proxy lookup ẩn. Proxy model trở nên dễ dự đoán khi đường đi của lời gọi được xem là một phần thiết kế, thay vì coi annotation như cơ chế tự động bất kể invocation đi bằng đường nào.

</details>

- [Quay lại đầu trang](#back-to-top)
