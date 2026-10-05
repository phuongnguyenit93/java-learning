<a id="back-to-top"></a>

# Mô hình Proxy trong Spring AOP

## Menu
- [1. Bên gọi không nhất thiết gọi trực tiếp target](#proxy-mental-model)
- [2. Demo trong module](#proxy-demo)
- [3. Container auto-proxying và object tạo trực tiếp](#managed-vs-new-demo)
- [4. JDK Dynamic Proxy và CGLIB](#proxy-strategies)
- [5. Kết luận](#proxy-conclusion)

Proxy boundary là nền tảng để hiểu hầu hết hành vi của Spring AOP.

## <a id="proxy-mental-model">1. Bên gọi không nhất thiết gọi trực tiếp target</a>

<details>
<summary>Xem chi tiết</summary>

Khi Spring áp dụng AOP cho một object, bên gọi thường nhận một proxy reference:

```text
Bên gọi
  ↓
Spring AOP Proxy
  ↓
Advisor / interceptor chain
  ↓
Target Object
```

Proxy chính là interception boundary. Nó có thể xem invocation hiện tại, chạy advice phù hợp rồi tiếp tục về target. Vì vậy câu hỏi runtime hữu ích nhất trong Spring AOP là:

```text
Invocation này có đi qua đúng proxy không?
```

Nên trả lời câu hỏi đó trước khi debug pointcut. Nếu code đang giữ reference trực tiếp tới target thì không có pointcut nào có thể tự chen vào lời gọi trực tiếp ấy.

Container auto-proxying là đường đi phổ biến trong ứng dụng: infrastructure của Spring xem xét Spring-managed bean và khi có advisor phù hợp, Spring expose một proxy thay cho plain bean reference. Đây là hành vi trong lifecycle của container, không phải quy tắc rằng mọi Java object trong JVM tự động được proxy.

Ngoài ra còn có boundary cấp thấp. `ProxyFactory` có thể explicit wrap một object và tạo AOP proxy bằng code. Vì vậy khi nói về object được tạo bằng `new` cần diễn đạt chính xác:

```text
new SomeService()
→ object thường, tự nó không có AOP

ProxyFactory(new SomeService()).getProxy()
→ Spring AOP proxy được tạo explicit
```

Do đó điểm phân biệt quan trọng là **call đi qua AOP proxy** hay **call trực tiếp tới unproxied target**, thay vì coi "Spring bean" và "object tạo bằng new" là hai nhóm tuyệt đối.

### Tài liệu tham khảo

- Spring Framework Reference — Proxying Mechanisms
- Spring Framework Reference — Creating AOP Proxies Programmatically with the ProxyFactory

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-demo">2. Demo trong module</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
ProxyMentalModelController#inspectProxy()
```

Endpoint:

```text
GET /aop/proxy/inspect
```

Target:

```text
ProxyMentalModelService#invokeTarget()
```

Aspect:

```text
ProxyMentalModelAspect#observeProxyBoundary(...)
```

Response `facts` gồm:

```text
runtimeClass
targetClass
isAopProxy
isCglibProxy
isJdkDynamicProxy
```

Response `events` cho thấy invocation đi qua advice trước khi tới target:

```text
proxy-boundary:before-target
target:invokeTarget
proxy-boundary:after-target
```

Tên generated proxy class không phải contract ổn định. Khi experiment cần bằng chứng về semantics, dùng các utility của Spring như `AopUtils.isAopProxy(...)`, `isJdkDynamicProxy(...)`, `isCglibProxy(...)` và target-class inspection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="managed-vs-new-demo">3. Container auto-proxying và object tạo trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
ProxyMentalModelController#compareManagedBeanAndPlainObject()
```

Endpoint:

```text
GET /aop/proxy/managed-vs-new
```

Experiment cố ý so sánh đường đi qua container với một object thông thường được gọi trực tiếp:

```text
Spring-managed reference
→ AOP proxy
→ advice
→ target

new ProxyMentalModelService(...)
→ object thông thường
→ target trực tiếp
```

Nhánh managed ghi:

```text
proxy-boundary:before-target
target:invokeTarget
proxy-boundary:after-target
```

Nhánh plain chỉ ghi:

```text
target:invokeTarget
```

và `facts` xác nhận:

```text
managedIsAopProxy = true
plainIsAopProxy   = false
```

Bài học ở đây rất cụ thể: container auto-proxying không tự advise một object bất kỳ chỉ vì class của nó sẽ match cùng pointcut. Object thông thường vẫn có thể được wrap explicit bằng `ProxyFactory`, nhưng experiment này cố ý không làm bước đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-strategies">4. JDK Dynamic Proxy và CGLIB</a>

<details>
<summary>Xem chi tiết</summary>

Spring AOP có hai chiến lược proxy chính.

```text
JDK dynamic proxy
→ proxy dựa trên interface
→ type surface của proxy là các interface được proxy

CGLIB proxy
→ generated subclass của target class
→ proxy assignable tới target class
```

Với mặc định của Spring Framework core, khi target implements ít nhất một interface, Spring có thể dùng JDK dynamic proxy; nếu không có interface phù hợp, Spring dùng CGLIB class proxy. Cấu hình như `proxyTargetClass = true` có thể ép class-based proxying. Spring Boot có thể chọn mặc định ứng dụng khác, vì vậy không nên lấy default của Boot làm contract của Spring Framework AOP.

Strategy ảnh hưởng những call nào có thể đi qua proxy. JDK proxy intercept lời gọi được expose qua proxy interfaces. CGLIB dựa vào subclassing/overriding nên chịu giới hạn từ Java:

- class `final` không thể được subclass-proxy;
- method `final` không thể override nên không thể được advise qua CGLIB;
- method `private` không thể override nên không thể được advise;
- method không visible với subclass cũng không thể được advise qua subclass proxy đó.

Ứng dụng thông thường nên đặt AOP ở public service boundary rõ ràng. Nếu yêu cầu cần intercept internal call bất kỳ, constructor hoặc field access, đó thường là tín hiệu proxy-based Spring AOP không còn là runtime boundary phù hợp.

Experiment so sánh cả hai strategy bằng code nằm ở:

```text
readme/vi/menu/10.ProxyFactory/ProxyFactory.md
ProgrammaticProxyController#compareProxyFactoryStrategies()
```

### Tài liệu tham khảo

- Spring Framework Reference — Proxying Mechanisms

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-conclusion">5. Kết luận</a>

<details>
<summary>Xem chi tiết</summary>

Với mọi câu hỏi kiểu "vì sao advice chạy hoặc không chạy?", nên kiểm tra theo thứ tự:

```text
1. Có AOP proxy không?
2. Invocation có đi qua proxy đó không?
3. Advisor / pointcut có match invocation không?
4. Advice nào nằm trong chain kết quả?
```

Proxy strategy làm thay đổi type surface và một số giới hạn interception, nhưng nguyên tắc về boundary không đổi. Chapter self-invocation phía sau sẽ cho thấy trường hợp điển hình: object đã có proxy nhưng một lời gọi nội bộ vẫn bypass proxy.

</details>

- [Quay lại đầu trang](#back-to-top)
