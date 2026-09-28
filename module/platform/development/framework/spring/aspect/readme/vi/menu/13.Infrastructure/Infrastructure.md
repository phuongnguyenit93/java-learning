<a id="back-to-top"></a>

# Advanced - Auto Proxy Creator và Advised

## Menu
- [1. Auto-proxy creation xảy ra ở đâu?](#auto-proxy-mental-model)
- [2. Ai bật AOP trong Spring Boot?](#spring-boot-aop-auto-config)
- [3. Advised cho biết proxy đang chứa gì](#advised-interface)
- [4. Demo trong module](#infrastructure-demo)
- [5. Kết luận](#infrastructure-conclusion)

Ở application code bình thường ta không tự gọi `new ProxyFactory(...)` cho từng bean. Spring tự động phát hiện candidate và bọc bean bằng proxy.

## <a id="auto-proxy-mental-model">1. Auto-proxy creation xảy ra ở đâu?</a>

<details>
<summary>Click for details</summary>

Mental model đơn giản hóa:

```text
Bean definition
    ↓
bean instance được tạo
    ↓
BeanPostProcessor infrastructure
    ↓
Spring tìm Advisor phù hợp
    ↓
nếu cần
→ trả AOP proxy thay cho raw bean reference
```

Một family infrastructure quan trọng là:

```text
AbstractAutoProxyCreator
```

Với `@AspectJ` support, runtime thường có một auto-proxy creator cụ thể chịu trách nhiệm biến Aspect metadata thành Advisor candidate và quyết định bean nào cần proxy.

Không nên hardcode class name cụ thể của auto-proxy creator vào business logic vì đó là framework infrastructure.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-boot-aop-auto-config">2. Ai bật AOP trong Spring Boot?</a>

<details>
<summary>Click for details</summary>

Module chỉ khai báo:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

và `AspectApplication` không cần tự thêm:

```java
@EnableAspectJAutoProxy
```

Trong Spring Boot, AOP được auto-configure khi dependency và điều kiện phù hợp. Hai setting quan trọng cần biết là:

```text
spring.aop.auto
→ mặc định true

spring.aop.proxy-target-class
→ mặc định true trong Spring Boot
→ ưu tiên class-based/CGLIB proxy
```

Default ở trên là **documented Boot default**, không có nghĩa application bắt buộc phải chứa hai property này trong `application.yml`.

Nếu đổi:

```properties
spring.aop.proxy-target-class=false
```

thì auto-proxy infrastructure có thể dùng JDK dynamic proxy khi target expose interface phù hợp.

Đây là lý do nhiều Spring-managed bean trong module hiện trả:

```text
isCglibProxy = true
```

dù chapter `11.ProxyFactory` vẫn có thể chủ động tạo cả JDK proxy và CGLIB proxy bằng code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advised-interface">3. Advised cho biết proxy đang chứa gì</a>

<details>
<summary>Click for details</summary>

Spring AOP proxy thường expose infrastructure interface:

```text
Advised
```

Qua đó có thể inspect:

```text
Advisor[]
```

Việc này hữu ích khi debug hoặc học architecture, nhưng application code thông thường không nên phụ thuộc sâu vào `Advised` để chạy business flow.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="infrastructure-demo">4. Demo trong module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
InfrastructureController#inspectInfrastructure()
```

Endpoint:

```text
GET /aop/advanced/infrastructure/inspect
```

Target:

```text
InfrastructureTargetService#execute()
```

Aspect:

```text
InfrastructureAspect#observe(...)
```

Response cho thấy:

```text
isAopProxy
isCglibProxy
isJdkDynamicProxy
runtimeClass
targetClass
advisors
autoProxyCreators
autoProxyCreatorPresent
springAopAutoExplicitlyConfigured
springAopAutoConfiguredValue
springAopProxyTargetClassExplicitlyConfigured
springAopProxyTargetClassConfiguredValue
```

Events:

```text
infrastructure-aspect:before
target:infrastructure
infrastructure-aspect:after
```

Điểm quan trọng là Controller không tạo proxy bằng tay. Proxy đã tồn tại khi bean được inject vào Controller.

Endpoint cố ý tách hai loại evidence:

```text
configuration evidence
→ property có được application cấu hình explicit không?
→ nếu có, configured value là gì?

runtime evidence
→ AutoProxyCreator có thật sự tồn tại không?
→ bean có thật sự là AOP proxy không?
→ proxy thực tế là CGLIB hay JDK dynamic proxy?
```

Nhờ vậy response không giả vờ rằng Boot default là một property value do application đã khai báo. Documented default và runtime observation là hai nguồn thông tin khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="infrastructure-conclusion">5. Kết luận</a>

<details>
<summary>Click for details</summary>

Annotation-style Spring AOP nhìn rất declarative ở application layer vì phần tạo proxy, tìm Advisor và xây interceptor chain được framework infrastructure thực hiện tự động.

Trong Spring Boot, starter + auto-configuration là lớp nối từ dependency/configuration tới auto-proxy infrastructure đó.

</details>

- [Quay lại đầu trang](#back-to-top)
