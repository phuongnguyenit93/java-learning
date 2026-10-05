<a id="back-to-top"></a>

# Hạ tầng Auto-Proxy, Advised và TargetSource

## Menu
- [1. Auto-proxy được tạo ở đâu?](#auto-proxy-mental-model)
- [2. Bật AOP trong Spring Framework và ranh giới auto-configuration của Spring Boot](#framework-boot-aop-enablement-boundary)
- [3. Advised cho biết proxy chứa gì](#advised-interface)
- [4. TargetSource và chiến lược vòng đời của target](#target-source-strategy)
- [5. Demo trong module](#infrastructure-demo)
- [6. Kết luận](#infrastructure-conclusion)

Trong mã ứng dụng thông thường, ta không tự gọi `new ProxyFactory(...)` cho từng bean. Spring tự động phát hiện bean ứng viên và bọc bean bằng proxy.

## <a id="auto-proxy-mental-model">1. Auto-proxy được tạo ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình đơn giản hóa:

```text
Bean definition
    ↓
bean instance được tạo
    ↓
hạ tầng auto-proxy dạng BeanPostProcessor
    ↓
tìm các Advisor đủ điều kiện
    ↓
nếu có advice áp dụng
→ phơi bày AOP proxy cho các bên dùng bean
```

`AbstractAutoProxyCreator` là họ lớp cốt lõi của hạ tầng auto-proxy Spring AOP và tham gia vòng đời dưới dạng `BeanPostProcessor`. Với hỗ trợ `@AspectJ`, Spring biến các advice method phù hợp thành các Advisor ứng viên, xác định Advisor nào áp dụng cho bean rồi bọc bean khi cần interception.

Sơ đồ trên cố ý đơn giản hóa. Container còn có các đường lifecycle cho early proxy reference, nhưng quy tắc hữu ích ở tầng ứng dụng vẫn là: hãy dùng tham chiếu bean đã được container xử lý, vì tham chiếu đó có thể đã là proxy.

Auto-proxying thuộc vòng đời Spring-managed bean. Một object được tạo trực tiếp bằng `new` không tự được bọc chỉ vì có Aspect khớp; object đó phải đi qua hạ tầng Spring phù hợp hoặc được tạo proxy tường minh.

Không gắn cứng tên một lớp auto-proxy creator cụ thể vào mã nghiệp vụ. Đây là chi tiết hạ tầng của framework, không phải điểm tích hợp ổn định dành cho ứng dụng.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="framework-boot-aop-enablement-boundary">2. Bật AOP trong Spring Framework và ranh giới auto-configuration của Spring Boot</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework thuần và Spring Boot có thể đi tới cùng hạ tầng proxy nhưng qua hai lớp cấu hình khác nhau.

Trong Java configuration của Spring Framework:

```java
@EnableAspectJAutoProxy
```

bật xử lý Aspect theo phong cách `@AspectJ` trong `ApplicationContext` nơi annotation được khai báo. Với Spring Framework 6.1.x, giá trị mặc định là:

```text
proxyTargetClass = false
exposeProxy      = false
```

Đây là mặc định của **Spring Framework**.

Module này chạy trên Spring Boot và khai báo:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

Boot AOP auto-configuration bật hạ tầng khi các điều kiện phù hợp và `spring.aop.auto` không vô hiệu hóa nó. Boot cũng có mặc định riêng cho kiểu proxy:

```text
spring.aop.auto
→ mặc định true trong Spring Boot

spring.aop.proxy-target-class
→ mặc định true trong Spring Boot
→ ưu tiên class-based/CGLIB proxy
```

Khi đặt `spring.aop.proxy-target-class=false`, Boot có thể dùng JDK dynamic proxy nếu target có interface phù hợp.

Điểm cần giữ rõ: mặc định của property Boot không phải quy tắc của Spring Framework AOP. Chương 10 vẫn có thể tự tạo cả hai kiểu proxy qua `ProxyFactory` bất kể mặc định auto-proxy của Boot.

### Tài liệu tham khảo

- Spring Framework 6.1.x API — [`@EnableAspectJAutoProxy`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html)
- Spring Boot 3.3 API — [`AopAutoConfiguration`](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/autoconfigure/aop/AopAutoConfiguration.html)
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advised-interface">3. Advised cho biết proxy chứa gì</a>

<details>
<summary>Xem chi tiết</summary>

Một Spring AOP proxy tiêu chuẩn ở chế độ non-opaque có thể phơi bày interface hạ tầng:

```text
Advised
```

Qua đó, mã hạ tầng hoặc chẩn đoán có thể xem các cấu hình như:

```text
Advisor[]
TargetSource
các interface được proxy
cấu hình proxy
```

Điều này có điều kiện. `ProxyConfig#setOpaque(true)` ngăn bên gọi cast proxy sinh ra sang `Advised`. Mặc định thông thường là non-opaque, nên module có thể dùng `instanceof Advised` để quan sát advisor chain.

`Advised` phù hợp cho chẩn đoán và extension hạ tầng. Mã nghiệp vụ không nên dựa vào nó để điều khiển luồng vì như vậy logic domain bị phụ thuộc vào phần triển khai proxy và cấu hình AOP có thể thay đổi.

### Tài liệu tham khảo

- Spring Framework 6.1.x API — [`Advised`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/aop/framework/Advised.html)
- Spring Framework 6.1.x API — [`ProxyConfig`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/aop/framework/ProxyConfig.html)
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-source-strategy">4. TargetSource và chiến lược vòng đời của target</a>

<details>
<summary>Xem chi tiết</summary>

Proxy và target có hai trách nhiệm khác nhau. `TargetSource` trả lời câu hỏi hạ tầng ngay trước khi một invocation đã được advice đi tới mã ứng dụng:

```text
invocation đã sẵn sàng tới target
        ↓
instance target nào sẽ nhận lời gọi này?
```

`TargetSource#isStatic()` định nghĩa contract vòng đời quan trọng.

Khi `isStatic() == true`:

```text
gọi getTarget() nhiều lần
→ phải trả cùng một đối tượng target

hạ tầng proxy
→ có thể cache target đó

releaseTarget(...)
→ không cần được gọi
```

Đây là mô hình target ổn định thông thường, được biểu diễn bởi các phần triển khai như `SingletonTargetSource`.

Khi `isStatic() == false`, target có thể thay đổi giữa các invocation. Hạ tầng AOP lấy target cho từng invocation rồi giải phóng nó sau đó qua `releaseTarget(...)`. Spring cung cấp các chiến lược nâng cao cho pooling, hot swap, lazy creation hoặc target dựa trên prototype.

Trách nhiệm này tách biệt với pointcut matching:

```text
Pointcut / Advisor
→ hành vi này có áp dụng không?

TargetSource
→ đối tượng target nào cuối cùng nhận invocation?
```

Phần lớn mã ứng dụng không cần tự triển khai `TargetSource`. Đây là điểm mở rộng hạ tầng cho trường hợp hiếm khi danh tính hoặc vòng đời của target thực sự cần thay đổi phía sau một tham chiếu proxy ổn định.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — [`TargetSource`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/TargetSource.html)
- Spring Framework Reference — [Using `TargetSource` Implementations](https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html)
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="infrastructure-demo">5. Demo trong module</a>

<details>
<summary>Xem chi tiết</summary>

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

Phản hồi cho thấy:

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

Các sự kiện:

```text
infrastructure-aspect:before
target:infrastructure
infrastructure-aspect:after
```

Điểm quan trọng là Controller không tạo proxy bằng tay. Proxy đã tồn tại khi bean được inject vào Controller.

Endpoint cố ý tách hai loại bằng chứng:

```text
bằng chứng cấu hình
→ property có được ứng dụng cấu hình tường minh không?
→ nếu có, giá trị cấu hình là gì?

bằng chứng runtime
→ AutoProxyCreator có thật sự tồn tại không?
→ bean có thật sự là AOP proxy không?
→ proxy thực tế là CGLIB hay JDK dynamic proxy?
```

Nhờ vậy phản hồi không đánh đồng mặc định của Boot với property value do ứng dụng tự khai báo. Giá trị mặc định trong tài liệu và quan sát runtime là hai nguồn bằng chứng khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="infrastructure-conclusion">6. Kết luận</a>

<details>
<summary>Xem chi tiết</summary>

Spring AOP theo phong cách annotation trông rất khai báo ở tầng ứng dụng vì việc tạo proxy, tìm Advisor, chọn target và xây interceptor chain đều do hạ tầng đảm nhiệm.

Trong Spring Framework, `@EnableAspectJAutoProxy` là một cách tường minh để đăng ký hỗ trợ này. Trong module dùng Boot, starter và Boot AOP auto-configuration cài hạ tầng tương đương với các mặc định riêng của Boot. Phân biệt hai lớp này giúp tránh nhầm lựa chọn auto-configuration của Boot với ngữ nghĩa cốt lõi của Spring AOP.
</details>

- [Quay lại đầu trang](#back-to-top)
